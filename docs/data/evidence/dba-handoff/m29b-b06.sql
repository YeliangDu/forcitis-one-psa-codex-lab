-- M-29 基线补充（第二批）B06（one-rules 提供，来源 origin/claude/one-rules-context-2@943876a76 r0/m29b_baseline_supplement_queries.sql）。ops 仅加库名守卫与 SET LOCK_TIMEOUT 30000。one_evidence_ro（ADR-0037 第 5 项第 4 条），默认 READ COMMITTED，不用 NOLOCK。
SET LOCK_TIMEOUT 30000;
-- B06 落在 2025 期间、business_date 早于 2025-01-01 的奖金行（psa_pl_ledger）
-- 依赖：同 B03。
-- 判读：返回 0 行表示不存在。有行则看 biz_year（上一年的奖金在 2025 期间结转进损益，即跨年错配）。
-- 不确定度：低。
IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
WITH w AS (SELECT 2025 AS yr, CAST('20250101' AS date) AS y_from)
SELECT l.source_type, l.pl_type, YEAR(l.business_date) AS biz_year,
       COUNT_BIG(*) AS row_cnt, COUNT(DISTINCT l.project_id) AS project_cnt,
       COUNT(DISTINCT l.business_date) AS distinct_days,
       MIN(l.business_date) AS min_biz_date, MAX(l.business_date) AS max_biz_date,
       SUM(l.notax_amount) AS notax_sum
FROM dbo.psa_pl_ledger l
JOIN dbo.mdm_accounting_period ap ON ap.id = l.period_id
CROSS JOIN w
WHERE l.deleted = 0 AND l.source_type IN (8, 9)
  AND ap.period_year = w.yr AND l.business_date < w.y_from
GROUP BY l.source_type, l.pl_type, YEAR(l.business_date)
ORDER BY l.source_type, l.pl_type, YEAR(l.business_date);
END
