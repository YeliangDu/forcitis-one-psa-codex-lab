-- M-29 基线补充（第二批）B04（one-rules 提供，来源 origin/claude/one-rules-context-2@943876a76 r0/m29b_baseline_supplement_queries.sql）。ops 仅加库名守卫与 SET LOCK_TIMEOUT 30000。one_evidence_ro（ADR-0037 第 5 项第 4 条），默认 READ COMMITTED，不用 NOLOCK。
SET LOCK_TIMEOUT 30000;
-- B04 2025 年奖金行按 business_date 的月份分布（公司合计）：psa_pl_ledger
-- 依赖：同 B03。
-- 口径：取「期间年 = 2025」或「business_date 年 = 2025」的奖金行（两者可能不同），分组带出期间年，月份取 MONTH(business_date)。
-- 判读：若 distinct_days 恒为 1 且 biz_month 恒为 12，则 business_date 无月份信息，按月归期不可能。
-- 不确定度：低。金额含红冲负数（pl_type 9）；pl_type 分列。
IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
WITH w AS (SELECT 2025 AS yr)
SELECT l.source_type, l.pl_type, ap.period_year,
       YEAR(l.business_date) AS biz_year, MONTH(l.business_date) AS biz_month,
       COUNT_BIG(*) AS row_cnt, COUNT(DISTINCT l.business_date) AS distinct_days,
       MIN(l.business_date) AS min_biz_date, MAX(l.business_date) AS max_biz_date,
       SUM(l.notax_amount) AS notax_sum, SUM(l.amount) AS gross_sum
FROM dbo.psa_pl_ledger l
LEFT JOIN dbo.mdm_accounting_period ap ON ap.id = l.period_id
CROSS JOIN w
WHERE l.deleted = 0 AND l.source_type IN (8, 9)
  AND (ap.period_year = w.yr OR YEAR(l.business_date) = w.yr)
GROUP BY l.source_type, l.pl_type, ap.period_year, YEAR(l.business_date), MONTH(l.business_date)
ORDER BY l.source_type, l.pl_type, ap.period_year, YEAR(l.business_date), MONTH(l.business_date);
END
