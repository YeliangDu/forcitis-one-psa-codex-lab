-- M-29 基线补充（第二批）B26（one-rules 提供，来源 origin/claude/one-rules-context-2@943876a76 r0/m29b_baseline_supplement_queries.sql）。ops 仅加库名守卫与 SET LOCK_TIMEOUT 30000。one_evidence_ro（ADR-0037 第 7 项），默认 READ COMMITTED，不用 NOLOCK。
SET LOCK_TIMEOUT 30000;
-- B26 跨年项目：项目 × 立项年 × 完工代理，2025 年收入与进损益直接人工（网格，只出各格合计）
-- 依赖：psa_pm_project（id, initiation_date, status）；psa_pm_cuvalue（project_id, complete_date, no_tax_amount, type）；
--       psa_pl_ledger（project_id, source_type, pl_type, notax_amount, period_id, deleted）；mdm_accounting_period.period_year。
-- 项目日期依据：立项 = psa_pm_project.initiation_date（ProjectDO，DELIVERY_RESOURCES_EVIDENCE.md 亦称老系统 initiation_date）；
--       项目无完工日期列，完工用代理：status 为「已完成」或「已关闭」（FSTConstant.ProjectStatus：已完成 -3117158260873541206、已关闭 -5057727321925005625）
--       且最后一条确认收入日期（type 0/1 的 complete_date 最大值）早于 2026-01-01；终止（-1296737353346166958）单列。
-- 口径：只统计 2025 年有收入或有进损益成本的项目。直接人工分三列：工时 st0、尾差 st12、年度奖金 st9（奖金是否算直接人工待定）。成本取 pl_type IN (0,9)、期间年 2025。
-- 不确定度：中偏高。(1) status 值是 bigint 哈希，源码常量未对生产核对，以 B29 校验；(2) 完工用「最后收入日期 + 状态」代理，状态可能与实际完工不同步；
--       (3) 立项日期为 NULL 的项目单列 x；(4) 网格格内项目数可能很小，仅限内部。
IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
WITH w AS (SELECT 2025 AS yr, CAST('20250101' AS date) AS y_from, CAST('20260101' AS date) AS y_to),
rev AS (
    SELECT cu.project_id,
           SUM(CASE WHEN cu.complete_date >= w.y_from AND cu.complete_date < w.y_to THEN ISNULL(cu.no_tax_amount, 0) ELSE 0 END) AS rev_y,
           MAX(cu.complete_date) AS last_rev_date
    FROM dbo.psa_pm_cuvalue cu CROSS JOIN w
    WHERE cu.[type] IN (0, 1)
    GROUP BY cu.project_id
),
dl AS (
    SELECT l.project_id,
           SUM(CASE WHEN l.source_type = 0 THEN l.notax_amount ELSE 0 END) AS dl_st0,
           SUM(CASE WHEN l.source_type = 12 THEN l.notax_amount ELSE 0 END) AS dl_st12,
           SUM(CASE WHEN l.source_type = 9 THEN l.notax_amount ELSE 0 END) AS bonus_st9
    FROM dbo.psa_pl_ledger l
    JOIN dbo.mdm_accounting_period ap ON ap.id = l.period_id
    CROSS JOIN w
    WHERE l.deleted = 0 AND l.pl_type IN (0, 9) AND ap.period_year = w.yr AND l.source_type IN (0, 12, 9)
    GROUP BY l.project_id
),
pj AS (
    SELECT p.id AS project_id,
           CASE WHEN p.initiation_date IS NULL THEN N'x_立项日期空'
                WHEN p.initiation_date < w.y_from THEN N'a_2025年以前立项'
                WHEN p.initiation_date < w.y_to THEN N'b_2025年立项'
                ELSE N'c_2026年及以后立项' END AS init_class,
           CASE WHEN p.status IN (-3117158260873541206, -5057727321925005625) AND (r.last_rev_date IS NULL OR r.last_rev_date < w.y_to) THEN N'a_已完成或已关闭且确认收入止于2025'
                WHEN p.status IN (-3117158260873541206, -5057727321925005625) THEN N'b_已完成或已关闭但2026起仍有确认收入'
                WHEN p.status = -1296737353346166958 THEN N'd_终止'
                ELSE N'c_未完成（执行中、规划中、暂停及其他）' END AS done_class,
           ISNULL(r.rev_y, 0) AS rev_y, ISNULL(d.dl_st0, 0) AS dl_st0, ISNULL(d.dl_st12, 0) AS dl_st12, ISNULL(d.bonus_st9, 0) AS bonus_st9
    FROM dbo.psa_pm_project p
    CROSS JOIN w
    LEFT JOIN rev r ON r.project_id = p.id
    LEFT JOIN dl d ON d.project_id = p.id
)
SELECT init_class, done_class,
       COUNT_BIG(*) AS project_cnt,
       SUM(CASE WHEN rev_y <> 0 THEN 1 ELSE 0 END) AS project_with_rev_cnt,
       SUM(CASE WHEN dl_st0 <> 0 OR dl_st12 <> 0 THEN 1 ELSE 0 END) AS project_with_dl_cnt,
       SUM(rev_y) AS rev_notax, SUM(dl_st0) AS dl_st0, SUM(dl_st12) AS dl_st12, SUM(bonus_st9) AS bonus_st9
FROM pj
WHERE rev_y <> 0 OR dl_st0 <> 0 OR dl_st12 <> 0 OR bonus_st9 <> 0
GROUP BY init_class, done_class
ORDER BY init_class, done_class;
END
