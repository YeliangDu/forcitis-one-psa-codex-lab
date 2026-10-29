-- M-29 基线补充（第二批）B08（one-rules 提供，来源 origin/claude/one-rules-context-2@943876a76 r0/m29b_baseline_supplement_queries.sql）。ops 仅加库名守卫与 SET LOCK_TIMEOUT 30000。one_evidence_ro（ADR-0037 第 5 项第 4 条），默认 READ COMMITTED，不用 NOLOCK。
SET LOCK_TIMEOUT 30000;
-- B08 成本台账期间 → 损益期间的结转滞后矩阵（奖金行）
-- 依赖：psa_pl_ledger.source_cost_ledger_id → psa_cost_ledger.id；两端 period_id → mdm_accounting_period.period_year。
-- 口径：pl 行（含红冲）关联其来源成本行；pl 期间年或成本期间年为 2025 的行。
-- 用途：回答「期间错配在哪」：成本在哪个期间入池（cost_period_year）、在哪个期间进损益（pl_period_year）、business_date 年份是多少；
--       biz_date_diff_cnt 校验 pl.business_date 是否等于成本行 business_date（源码承接，应为 0）。
-- 不确定度：中。source_cost_ledger_id 可空（期间费用直进损益时），cost_link_missing_cnt 报告个数；红冲行的来源成本行与正向行同一条。
IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
WITH w AS (SELECT 2025 AS yr)
SELECT l.source_type, l.pl_type,
       cap.period_year AS cost_period_year, pap.period_year AS pl_period_year,
       YEAR(l.business_date) AS pl_biz_year,
       COUNT_BIG(*) AS row_cnt,
       SUM(CASE WHEN c.id IS NULL THEN 1 ELSE 0 END) AS cost_link_missing_cnt,
       SUM(CASE WHEN c.id IS NOT NULL AND ISNULL(c.business_date, '19000101') <> ISNULL(l.business_date, '19000101') THEN 1 ELSE 0 END) AS biz_date_diff_cnt,
       SUM(l.notax_amount) AS pl_notax_sum
FROM dbo.psa_pl_ledger l
LEFT JOIN dbo.psa_cost_ledger c ON c.id = l.source_cost_ledger_id
LEFT JOIN dbo.mdm_accounting_period cap ON cap.id = c.period_id
LEFT JOIN dbo.mdm_accounting_period pap ON pap.id = l.period_id
CROSS JOIN w
WHERE l.deleted = 0 AND l.source_type IN (8, 9)
  AND (pap.period_year = w.yr OR cap.period_year = w.yr)
GROUP BY l.source_type, l.pl_type, cap.period_year, pap.period_year, YEAR(l.business_date)
ORDER BY l.source_type, l.pl_type, cap.period_year, pap.period_year, YEAR(l.business_date);
END
