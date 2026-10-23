-- M-29 贡献毛利率分母改净服务收入前后值取证（one-rules 提供，来源 origin/claude/one-rules-context-2@3653a70e8 r0/m29_baseline_readonly_queries.sql）：Q40
-- ops 改动：每条外包 IF DB_NAME()=ruoyi-vue-pro；加 SET LOCK_TIMEOUT 30000；Q01 补一个缺失的右括号。one_evidence_ro（ADR-0037 第 5 项第 4 条），默认 READ COMMITTED，不用 NOLOCK。
SET LOCK_TIMEOUT 30000;
-- Q40 费用类别分布（探测哪些类别可能是分包/外包/代垫）：（窗口 × 来源类型 × 费用类别），公司合计
-- 依赖：psa_cost_ledger.source_type / cost_type_code / cost_type_name（V20260720193300 加列）/ notax_amount / business_date。
-- 用途：Q20/Q22 里 ODC费用 的 3 付款申请、4 特殊付款能否拆出分包；1/2/7 里有没有「可向客户转收」的类别。
-- 不确定度：中。cost_type_name 是 OA 费用类别名，老快照可能为 NULL；类别名不含个人信息；不选 supplier_code、person_name。
IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
WITH w AS (
    SELECT CAST('20250101' AS date) AS a_from, CAST('20260101' AS date) AS a_to,
           CAST('20260101' AS date) AS b_from, DATEADD(DAY, 1, CAST(GETDATE() AS date)) AS b_to
)
SELECT x.win_label, c.source_type, c.cost_type_code, c.cost_type_name,
       COUNT_BIG(*) AS row_cnt, COUNT(DISTINCT c.project_id) AS project_cnt, SUM(c.notax_amount) AS notax_sum
FROM dbo.psa_cost_ledger c
CROSS JOIN w
CROSS APPLY (SELECT CASE WHEN c.business_date >= w.a_from AND c.business_date < w.a_to THEN N'a_2025整年'
                         WHEN c.business_date >= w.b_from AND c.business_date < w.b_to THEN N'b_2026至今' END AS win_label) x
WHERE c.deleted = 0 AND c.entry_status = 0
  AND c.business_date >= w.a_from AND c.business_date < w.b_to
GROUP BY x.win_label, c.source_type, c.cost_type_code, c.cost_type_name
ORDER BY x.win_label, c.source_type, SUM(c.notax_amount) DESC;
END
