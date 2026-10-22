-- M-29 基线补充（第二批）B28（one-rules 提供，来源 origin/claude/one-rules-context-2@943876a76 r0/m29b_baseline_supplement_queries.sql）。ops 仅加库名守卫与 SET LOCK_TIMEOUT 30000。one_evidence_ro（ADR-0037 第 7 项），默认 READ COMMITTED，不用 NOLOCK。
SET LOCK_TIMEOUT 30000;
-- B28 跨年项目：以条款 POC 快照代替项目状态判完工（B26 的对照）
-- 依赖：psa_contract_clause（project_id, term_code, deleted）；psa_contract_term_poc_snapshot（term_code, period 'YYYY-MM', poc, deleted）；其余同 B26。
-- 口径：每个项目的每个条款取「POC 首次 ≥ 100 的月份」；项目所有条款都达 100 且最晚月份 ≤ '2025-12' → a；全部达 100 但最晚月份晚于 2025-12 → b；
--       有条款从未达 100 → c；项目无条款或条款无 POC 快照 → z。立项分类同 B26。
-- 不确定度：高。(1) psa_contract_clause.term_code 是否覆盖所有项目未核实（预算导入落定，可能只有已编预算的项目），z 类个数反映覆盖率；
--       (2) 快照 term_code 与 clause.term_code 是否同口径（源码注释称都是「结转口径 term_number」，另有注释称 clause.term_code = 合同条款 code，二者说法不一致）；
--       (3) 快照月度（YYYY-MM）。
IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
WITH w AS (SELECT 2025 AS yr, CAST('20250101' AS date) AS y_from, CAST('20260101' AS date) AS y_to),
rev AS (
    SELECT cu.project_id,
           SUM(CASE WHEN cu.complete_date >= w.y_from AND cu.complete_date < w.y_to THEN ISNULL(cu.no_tax_amount, 0) ELSE 0 END) AS rev_y
    FROM dbo.psa_pm_cuvalue cu CROSS JOIN w
    WHERE cu.[type] IN (0, 1)
    GROUP BY cu.project_id
),
dl AS (
    SELECT l.project_id,
           SUM(CASE WHEN l.source_type IN (0, 12) THEN l.notax_amount ELSE 0 END) AS dl_hours
    FROM dbo.psa_pl_ledger l
    JOIN dbo.mdm_accounting_period ap ON ap.id = l.period_id
    CROSS JOIN w
    WHERE l.deleted = 0 AND l.pl_type IN (0, 9) AND ap.period_year = w.yr AND l.source_type IN (0, 12)
    GROUP BY l.project_id
),
tc AS (
    SELECT cl.project_id, cl.term_code,
           MIN(CASE WHEN s.poc >= 100 THEN s.period END) AS first100
    FROM dbo.psa_contract_clause cl
    LEFT JOIN dbo.psa_contract_term_poc_snapshot s ON s.term_code = cl.term_code AND s.deleted = 0
    WHERE cl.deleted = 0 AND cl.term_code IS NOT NULL
    GROUP BY cl.project_id, cl.term_code
),
pc AS (
    SELECT project_id, COUNT_BIG(*) AS term_cnt,
           SUM(CASE WHEN first100 IS NULL THEN 1 ELSE 0 END) AS open_cnt,
           MAX(first100) AS last100
    FROM tc GROUP BY project_id
),
pj AS (
    SELECT p.id AS project_id,
           CASE WHEN p.initiation_date IS NULL THEN N'x_立项日期空'
                WHEN p.initiation_date < w.y_from THEN N'a_2025年以前立项'
                WHEN p.initiation_date < w.y_to THEN N'b_2025年立项'
                ELSE N'c_2026年及以后立项' END AS init_class,
           CASE WHEN pc.project_id IS NULL THEN N'z_无条款POC数据'
                WHEN pc.open_cnt = 0 AND pc.last100 <= N'2025-12' THEN N'a_全部条款POC达100且止于2025'
                WHEN pc.open_cnt = 0 THEN N'b_全部达100但最晚月份在2026起'
                ELSE N'c_有条款未达100' END AS done_poc,
           ISNULL(r.rev_y, 0) AS rev_y, ISNULL(d.dl_hours, 0) AS dl_hours
    FROM dbo.psa_pm_project p
    CROSS JOIN w
    LEFT JOIN rev r ON r.project_id = p.id
    LEFT JOIN dl d ON d.project_id = p.id
    LEFT JOIN pc ON pc.project_id = p.id
)
SELECT init_class, done_poc,
       COUNT_BIG(*) AS project_cnt,
       SUM(CASE WHEN rev_y <> 0 THEN 1 ELSE 0 END) AS project_with_rev_cnt,
       SUM(rev_y) AS rev_notax, SUM(dl_hours) AS dl_hours
FROM pj
WHERE rev_y <> 0 OR dl_hours <> 0
GROUP BY init_class, done_poc
ORDER BY init_class, done_poc;
END
