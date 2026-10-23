-- M-29 基线补充（第二批）B27（one-rules 提供，来源 origin/claude/one-rules-context-2@943876a76 r0/m29b_baseline_supplement_queries.sql）。ops 仅加库名守卫与 SET LOCK_TIMEOUT 30000。one_evidence_ro（ADR-0037 第 5 项第 4 条），默认 READ COMMITTED，不用 NOLOCK。
SET LOCK_TIMEOUT 30000;
-- B27 跨年项目三分类（B26 的归并，层级固定先后）
-- 依赖与口径：同 B26。归并顺序：先按立项年，再按完工代理：
--   1_2025年以前立项（不论何时完工） ；
--   2_2025年及以后立项且2025年内完工（立项年 2025 或更晚，且完工代理为 a） ；
--   3_完工晚于2025或未完工（立项年 2025 或更晚，其余全部：未完成、终止、2026 年起仍有收入）；
--   9_立项日期空。
-- 不确定度：同 B26。调度方的三分类有交叉（早立项也可能晚完工），本条取先按立项年的互斥分法；要换法，用 B26 网格重算。
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
           CASE WHEN p.initiation_date IS NULL THEN 9
                WHEN p.initiation_date < w.y_from THEN 1
                WHEN p.status IN (-3117158260873541206, -5057727321925005625) AND (r.last_rev_date IS NULL OR r.last_rev_date < w.y_to) THEN 2
                ELSE 3 END AS cls,
           ISNULL(r.rev_y, 0) AS rev_y, ISNULL(d.dl_st0, 0) AS dl_st0, ISNULL(d.dl_st12, 0) AS dl_st12, ISNULL(d.bonus_st9, 0) AS bonus_st9
    FROM dbo.psa_pm_project p
    CROSS JOIN w
    LEFT JOIN rev r ON r.project_id = p.id
    LEFT JOIN dl d ON d.project_id = p.id
)
SELECT CASE cls WHEN 1 THEN N'1_2025年以前立项'
                WHEN 2 THEN N'2_2025年及以后立项且2025年内完工'
                WHEN 3 THEN N'3_完工晚于2025或未完工'
                ELSE N'9_立项日期空' END AS class_label,
       COUNT_BIG(*) AS project_cnt,
       SUM(CASE WHEN rev_y <> 0 THEN 1 ELSE 0 END) AS project_with_rev_cnt,
       SUM(CASE WHEN dl_st0 <> 0 OR dl_st12 <> 0 THEN 1 ELSE 0 END) AS project_with_dl_cnt,
       SUM(rev_y) AS rev_notax, SUM(dl_st0) AS dl_st0, SUM(dl_st12) AS dl_st12, SUM(bonus_st9) AS bonus_st9
FROM pj
WHERE rev_y <> 0 OR dl_st0 <> 0 OR dl_st12 <> 0 OR bonus_st9 <> 0
GROUP BY cls
ORDER BY cls;
END
