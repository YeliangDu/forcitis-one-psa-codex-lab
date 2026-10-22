-- M-29 基线补充（第二批）B07（one-rules 提供，来源 origin/claude/one-rules-context-2@943876a76 r0/m29b_baseline_supplement_queries.sql）。ops 仅加库名守卫与 SET LOCK_TIMEOUT 30000。one_evidence_ro（ADR-0037 第 7 项），默认 READ COMMITTED，不用 NOLOCK。
SET LOCK_TIMEOUT 30000;
-- B07 成本台账里的奖金行：business_date 月份 × 台账归属期间（psa_cost_ledger，公司合计）
-- 依赖：psa_cost_ledger（source_type, entry_type, entry_status, business_date, period_id, notax_amount, deleted）；mdm_accounting_period。
-- 口径：期间年 = 2025 或 business_date 年 = 2025；不限 entry_status（带出以便看被替代/红冲行）。
-- 用途：和 B04 对照，看成本发生侧（入池时点）是否同样只有 12-31 一个日期。
-- 不确定度：低。period_type=2 为 13 期（年末调整期，2026 起承载年终奖）。
IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
WITH w AS (SELECT 2025 AS yr)
SELECT l.source_type, l.entry_type, l.entry_status, cap.period_year, cap.period_type,
       YEAR(l.business_date) AS biz_year, MONTH(l.business_date) AS biz_month,
       COUNT_BIG(*) AS row_cnt, COUNT(DISTINCT l.business_date) AS distinct_days,
       SUM(l.notax_amount) AS notax_sum
FROM dbo.psa_cost_ledger l
LEFT JOIN dbo.mdm_accounting_period cap ON cap.id = l.period_id
CROSS JOIN w
WHERE l.deleted = 0 AND l.source_type IN (8, 9)
  AND (cap.period_year = w.yr OR YEAR(l.business_date) = w.yr)
GROUP BY l.source_type, l.entry_type, l.entry_status, cap.period_year, cap.period_type, YEAR(l.business_date), MONTH(l.business_date)
ORDER BY l.source_type, l.entry_type, l.entry_status, cap.period_year, YEAR(l.business_date), MONTH(l.business_date);
END
