-- M-29 基线补充（第二批）B22（one-rules 提供，来源 origin/claude/one-rules-context-2@943876a76 r0/m29b_baseline_supplement_queries.sql）。ops 仅加库名守卫与 SET LOCK_TIMEOUT 30000。one_evidence_ro（ADR-0037 第 7 项），默认 READ COMMITTED，不用 NOLOCK。
SET LOCK_TIMEOUT 30000;
-- B22 截至 2025 年末累计已发生（成本台账）：按（期间年 × 来源类型）及区域/BU
-- 依赖：psa_cost_ledger（source_type, entry_type, entry_status, period_id, business_date, notax_amount, project_id, deleted）；mdm_accounting_period.period_year；psa_pm_project / system_dept。
-- 口径：entry_status = 0、期间年 ≤ 2025、来源类型 0 与 12；累计 = 各期间年合计在本地相加。after_cutoff_biz_amount = 业务日期晚于 2025-12-31 的部分（入池期间 ≤ 2025 但业务日在后，应为 0）。
-- 用途：与 B23（累计结转）、B21（余额）三者核对：累计发生 − 累计结转 ≈ 未结转余额（差异来自豁免、终止、红冲、未入 WIP 的成本等）。
-- 不确定度：中。历史年台账残缺；金额为日薪×人天（见 M29_BASELINE_MAPPING.md U4）。
IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
WITH w AS (SELECT 2025 AS yr, CAST('20251231' AS date) AS cutoff),
d AS (
    SELECT p.id AS project_id,
           ISNULL(sp.name, N'(无上级部门)') AS parent_name,
           ISNULL(sd.name, N'(项目无交付部门)') AS dept_name
    FROM dbo.psa_pm_project p
    LEFT JOIN dbo.system_dept sd ON sd.id = p.dept_id
    LEFT JOIN dbo.system_dept sp ON sp.id = sd.parent_id
)
SELECT ap.period_year, l.source_type,
       GROUPING_ID(d.parent_name, d.dept_name) AS lvl,
       d.parent_name, d.dept_name,
       COUNT_BIG(*) AS row_cnt,
       SUM(l.notax_amount) AS incurred_notax,
       SUM(CASE WHEN l.business_date > w.cutoff THEN l.notax_amount ELSE 0 END) AS after_cutoff_biz_amount
FROM dbo.psa_cost_ledger l
JOIN dbo.mdm_accounting_period ap ON ap.id = l.period_id
CROSS JOIN w
LEFT JOIN d ON d.project_id = l.project_id
WHERE l.deleted = 0 AND l.entry_status = 0 AND ap.period_year <= w.yr AND l.source_type IN (0, 12)
GROUP BY ap.period_year, l.source_type, ROLLUP(d.parent_name, d.dept_name)
ORDER BY ap.period_year, l.source_type, d.parent_name, d.dept_name;
END
