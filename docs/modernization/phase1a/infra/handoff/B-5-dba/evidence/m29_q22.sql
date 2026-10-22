-- M-29 贡献毛利率分母改净服务收入前后值取证（one-rules 提供，来源 origin/claude/one-rules-context-2@3653a70e8 r0/m29_baseline_readonly_queries.sql）：Q22
-- ops 改动：每条外包 IF DB_NAME()=ruoyi-vue-pro；加 SET LOCK_TIMEOUT 30000；Q01 补一个缺失的右括号。one_evidence_ro（ADR-0037 第 7 项），默认 READ COMMITTED，不用 NOLOCK。
SET LOCK_TIMEOUT 30000;
-- Q22 项目成本·发生口径（成本台账）：（窗口 × 成本类 × 上级部门 × 交付部门）及合计
-- 依赖：psa_cost_ledger（deleted, entry_status, source_type, notax_amount, business_date, project_id）；其余同 Q20。
-- 口径：entry_status = 0（有效）；窗口按 business_date（工时=工时日、付款=付款日、委外=委外确认日）。成本类划分同 Q20。
-- 与 Q20 的关系：Q22 是已发生、未必已结转（可能仍在 WIP，成本台账不拥有 WIP 状态）；Q20 是已转进损益。
--       哪个更接近新系统 M-27「项目成本（合同履约成本结转）」「权责」需口径负责人定（见 M29_BASELINE_MAPPING.md 未决点）。
-- 不确定度：中偏低。entry_status=0 是否把红冲行（entry_type=1）正确计入未知——先看 Q23；本条只含 entry_status=0 的行。
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
SELECT x.win_label, cc.cost_class,
       GROUPING_ID(d.parent_name, d.dept_name) AS lvl,
       d.parent_name, d.dept_name,
       COUNT_BIG(*) AS row_cnt,
       COUNT(DISTINCT c.project_id) AS project_cnt,
       SUM(c.notax_amount) AS cost_notax
FROM dbo.psa_cost_ledger c
CROSS JOIN w
LEFT JOIN d ON d.project_id = c.project_id
CROSS APPLY (SELECT CASE WHEN c.business_date >= w.a_from AND c.business_date < w.a_to THEN N'a_2025整年'
                         WHEN c.business_date >= w.b_from AND c.business_date < w.b_to THEN N'b_2026至今' END AS win_label) x
CROSS APPLY (SELECT CASE WHEN c.source_type IN (0, 12) THEN N'DL工时'
                         WHEN c.source_type IN (8, 9) THEN N'DL奖金待定'
                         WHEN c.source_type IN (1, 2, 3, 4, 7) THEN N'ODC费用'
                         WHEN c.source_type = 5 THEN N'SUB委外'
                         WHEN c.source_type = 6 THEN N'INFO居间'
                         ELSE N'OTHER' END AS cost_class) cc
WHERE c.deleted = 0 AND c.entry_status = 0
  AND c.business_date >= w.a_from AND c.business_date < w.b_to
GROUP BY x.win_label, cc.cost_class, ROLLUP(d.parent_name, d.dept_name)
ORDER BY x.win_label, cc.cost_class, d.parent_name, d.dept_name;
END
