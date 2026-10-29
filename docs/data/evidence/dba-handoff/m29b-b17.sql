-- M-29 基线补充（第二批）B17（one-rules 提供，来源 origin/claude/one-rules-context-2@943876a76 r0/m29b_baseline_supplement_queries.sql）。ops 仅加库名守卫与 SET LOCK_TIMEOUT 30000。one_evidence_ro（ADR-0037 第 5 项第 4 条），默认 READ COMMITTED，不用 NOLOCK。
SET LOCK_TIMEOUT 30000;
-- B17 条款级备选（费用，发生口径）：成本台账的条款名称快照含「据实结算」的 2025 年费用 vs 其他条款
-- 依赖：psa_cost_ledger.term_name_snap（入池时冻结的条款名，CostLedgerDO 注释）、source_type、notax_amount、business_date、entry_status、deleted。
-- 不确定度：中偏高。term_name_snap 老数据可能为 NULL（term_name_null_cnt 报告）；其标题取自 psa_contract_clause.title，与合同条款名 psa_pm_contract_term.name 是否同文未核。
IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
WITH w AS (SELECT CAST('20250101' AS date) AS y_from, CAST('20260101' AS date) AS y_to)
SELECT CASE WHEN l.term_name_snap LIKE N'%据实结算%' THEN N'a_条款名含据实结算' ELSE N'b_其他' END AS term_class,
       COUNT_BIG(*) AS row_cnt, COUNT(DISTINCT l.project_id) AS project_cnt,
       SUM(CASE WHEN l.term_name_snap IS NULL THEN 1 ELSE 0 END) AS term_name_null_cnt,
       SUM(CASE WHEN l.source_type = 1 THEN l.notax_amount ELSE 0 END) AS odc_daily,
       SUM(CASE WHEN l.source_type = 2 THEN l.notax_amount ELSE 0 END) AS odc_travel,
       SUM(CASE WHEN l.source_type = 3 THEN l.notax_amount ELSE 0 END) AS odc_payreq,
       SUM(CASE WHEN l.source_type = 4 THEN l.notax_amount ELSE 0 END) AS odc_special,
       SUM(CASE WHEN l.source_type = 7 THEN l.notax_amount ELSE 0 END) AS odc_hospitality,
       SUM(CASE WHEN l.source_type = 5 THEN l.notax_amount ELSE 0 END) AS outsourcing,
       SUM(CASE WHEN l.source_type IN (0, 12) THEN l.notax_amount ELSE 0 END) AS dl_hours
FROM dbo.psa_cost_ledger l CROSS JOIN w
WHERE l.deleted = 0 AND l.entry_status = 0 AND l.business_date >= w.y_from AND l.business_date < w.y_to
GROUP BY CASE WHEN l.term_name_snap LIKE N'%据实结算%' THEN N'a_条款名含据实结算' ELSE N'b_其他' END
ORDER BY term_class;
END
