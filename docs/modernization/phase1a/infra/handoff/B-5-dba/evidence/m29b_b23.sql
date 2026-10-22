-- M-29 基线补充（第二批）B23（one-rules 提供，来源 origin/claude/one-rules-context-2@943876a76 r0/m29b_baseline_supplement_queries.sql）。ops 仅加库名守卫与 SET LOCK_TIMEOUT 30000。one_evidence_ro（ADR-0037 第 7 项），默认 READ COMMITTED，不用 NOLOCK。
SET LOCK_TIMEOUT 30000;
-- B23 截至 2025 年末累计结转进损益（损益台账）：按（期间年 × 来源类型 × 损益类型 × 触发类型）及区域/BU
-- 依赖：psa_pl_ledger（source_type, pl_type, trigger_type, period_id, notax_amount, project_id, deleted）；mdm_accounting_period.period_year；psa_pm_project / system_dept。
-- 口径：期间年 ≤ 2025、来源类型 0 与 12、pl_type 全带出（0 主营、1 减值、2 营业外、9 红冲负行）；累计结转（净）= 各行合计在本地相加（红冲行本身为负）。
--       trigger_type：0 正常转出 / 1 项目终止 / 2 红冲回退。
-- 不确定度：中。同 B22。
IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
WITH w AS (SELECT 2025 AS yr),
d AS (
    SELECT p.id AS project_id,
           ISNULL(sp.name, N'(无上级部门)') AS parent_name,
           ISNULL(sd.name, N'(项目无交付部门)') AS dept_name
    FROM dbo.psa_pm_project p
    LEFT JOIN dbo.system_dept sd ON sd.id = p.dept_id
    LEFT JOIN dbo.system_dept sp ON sp.id = sd.parent_id
)
SELECT ap.period_year, l.source_type, l.pl_type, l.trigger_type,
       GROUPING_ID(d.parent_name, d.dept_name) AS lvl,
       d.parent_name, d.dept_name,
       COUNT_BIG(*) AS row_cnt,
       SUM(l.notax_amount) AS carried_notax
FROM dbo.psa_pl_ledger l
JOIN dbo.mdm_accounting_period ap ON ap.id = l.period_id
CROSS JOIN w
LEFT JOIN d ON d.project_id = l.project_id
WHERE l.deleted = 0 AND ap.period_year <= w.yr AND l.source_type IN (0, 12)
GROUP BY ap.period_year, l.source_type, l.pl_type, l.trigger_type, ROLLUP(d.parent_name, d.dept_name)
ORDER BY ap.period_year, l.source_type, l.pl_type, l.trigger_type, d.parent_name, d.dept_name;
END
