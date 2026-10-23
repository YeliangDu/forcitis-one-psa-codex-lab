-- M-29 贡献毛利率分母改净服务收入前后值取证（one-rules 提供，来源 origin/claude/one-rules-context-2@3653a70e8 r0/m29_baseline_readonly_queries.sql）：Q11
-- ops 改动：每条外包 IF DB_NAME()=ruoyi-vue-pro；加 SET LOCK_TIMEOUT 30000；Q01 补一个缺失的右括号。one_evidence_ro（ADR-0037 第 7 项），默认 READ COMMITTED，不用 NOLOCK。
SET LOCK_TIMEOUT 30000;
-- Q11 收入取数质量：no_tax_amount 为 NULL 的行数与含税额；产值部门与项目交付部门不一致的行数
-- 依赖：psa_pm_cuvalue.no_tax_amount / complete_amount / dept_id / rate / company_code；psa_pm_project.dept_id。
-- 不确定度：中。用于判断 Q10 低估程度，以及「部门取项目交付部门」是否合理（cuvalue.dept_id 为交付部门，字典与 DO 注释一致）。
--       不做 /1.06 回算：新口径用单据实际税率（METRIC_BASELINE M-08），回算只是估计，需要时本地用 complete_amount 与 rate 另算。
IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
WITH w AS (
    SELECT CAST('20250101' AS date) AS a_from, CAST('20260101' AS date) AS a_to,
           CAST('20260101' AS date) AS b_from, DATEADD(DAY, 1, CAST(GETDATE() AS date)) AS b_to
)
SELECT x.win_label,
       COUNT_BIG(*) AS row_cnt,
       SUM(CASE WHEN c.no_tax_amount IS NULL THEN 1 ELSE 0 END) AS notax_null_cnt,
       SUM(CASE WHEN c.no_tax_amount IS NULL THEN ISNULL(c.complete_amount, 0) ELSE 0 END) AS notax_null_gross_amount,
       SUM(CASE WHEN c.no_tax_amount IS NULL AND c.rate IS NOT NULL THEN 1 ELSE 0 END) AS notax_null_but_rate_cnt,
       SUM(CASE WHEN c.dept_id IS NOT NULL AND p.dept_id IS NOT NULL AND c.dept_id <> p.dept_id THEN 1 ELSE 0 END) AS dept_mismatch_cnt,
       SUM(CASE WHEN p.id IS NULL THEN 1 ELSE 0 END) AS project_missing_cnt,
       SUM(CASE WHEN c.company_code IS NULL THEN 1 ELSE 0 END) AS company_code_null_cnt
FROM dbo.psa_pm_cuvalue c
CROSS JOIN w
LEFT JOIN dbo.psa_pm_project p ON p.id = c.project_id
CROSS APPLY (SELECT CASE WHEN c.complete_date >= w.a_from AND c.complete_date < w.a_to THEN N'a_2025整年'
                         WHEN c.complete_date >= w.b_from AND c.complete_date < w.b_to THEN N'b_2026至今' END AS win_label) x
WHERE c.[type] IN (0, 1)
  AND c.complete_date >= w.a_from AND c.complete_date < w.b_to
GROUP BY x.win_label
ORDER BY x.win_label;
END
