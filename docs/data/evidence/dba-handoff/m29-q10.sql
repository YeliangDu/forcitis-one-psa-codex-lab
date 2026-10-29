-- M-29 贡献毛利率分母改净服务收入前后值取证（one-rules 提供，来源 origin/claude/one-rules-context-2@3653a70e8 r0/m29_baseline_readonly_queries.sql）：Q10
-- ops 改动：每条外包 IF DB_NAME()=ruoyi-vue-pro；加 SET LOCK_TIMEOUT 30000；Q01 补一个缺失的右括号。one_evidence_ro（ADR-0037 第 5 项第 4 条），默认 READ COMMITTED，不用 NOLOCK。
SET LOCK_TIMEOUT 30000;
-- Q10 收入（不含税，权责）：（窗口 × 上级部门 × 交付部门）及合计。新旧分母的共同起点（老分母 = 本条；新分母 = 本条减转付成本）
-- 依赖：psa_pm_cuvalue（project_id, complete_date, no_tax_amount, type）；psa_pm_project.dept_id；system_dept。
-- 口径：type IN (0,1)（0 正常确认、1 红冲退回，退回为负额，直接相加即净额），与老系统三级利润读侧
--       ProfitReportMapper.sumRevenueByProjectTerm 一致；含已核销行；不按 company_code 过滤（该列存量可能为 NULL，老读侧严格等值会漏）。
-- 不确定度：中。no_tax_amount 存量行可能为 NULL（V20260728 加列时「存量无从回填」），本条把 NULL 当 0，可能低估；NULL 行数与含税额见 Q11。
--       「产值」即新系统「收入（不含税）」按 METRIC_BASELINE M-08 视为同一事实，未另核。
IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
WITH w AS (
    SELECT CAST('20250101' AS date) AS a_from, CAST('20260101' AS date) AS a_to,
           CAST('20260101' AS date) AS b_from, DATEADD(DAY, 1, CAST(GETDATE() AS date)) AS b_to
),
d AS (
    SELECT p.id AS project_id,
           ISNULL(sp.name, N'(无上级部门)') AS parent_name,
           ISNULL(sd.name, N'(项目无交付部门)') AS dept_name
    FROM dbo.psa_pm_project p
    LEFT JOIN dbo.system_dept sd ON sd.id = p.dept_id
    LEFT JOIN dbo.system_dept sp ON sp.id = sd.parent_id
)
SELECT x.win_label,
       GROUPING_ID(d.parent_name, d.dept_name) AS lvl,
       d.parent_name, d.dept_name,
       COUNT_BIG(*) AS row_cnt,
       COUNT(DISTINCT c.project_id) AS project_cnt,
       SUM(ISNULL(c.no_tax_amount, 0)) AS rev_notax
FROM dbo.psa_pm_cuvalue c
CROSS JOIN w
LEFT JOIN d ON d.project_id = c.project_id
CROSS APPLY (SELECT CASE WHEN c.complete_date >= w.a_from AND c.complete_date < w.a_to THEN N'a_2025整年'
                         WHEN c.complete_date >= w.b_from AND c.complete_date < w.b_to THEN N'b_2026至今' END AS win_label) x
WHERE c.[type] IN (0, 1)
  AND c.complete_date >= w.a_from AND c.complete_date < w.b_to
GROUP BY x.win_label, ROLLUP(d.parent_name, d.dept_name)
ORDER BY x.win_label, d.parent_name, d.dept_name;
END
