-- M-29 基线补充（第二批）B10（one-rules 提供，来源 origin/claude/one-rules-context-2@943876a76 r0/m29b_baseline_supplement_queries.sql）。ops 仅加库名守卫与 SET LOCK_TIMEOUT 30000。one_evidence_ro（ADR-0037 第 7 项），默认 READ COMMITTED，不用 NOLOCK。
SET LOCK_TIMEOUT 30000;
-- B10 OH 池里的奖金（计提 8 / 年终奖 9 的非项目部分）：business_date 月份 × 池期间（公司合计）
-- 依赖：psa_oh_cost_pool（source_type, business_date, period_id, notax_amount, alloc_status, pool_line_type, deleted）。
-- 用途：奖金计提 8 正常走 OH 池；看它的 business_date 是否按月分布（若按月，说明计提侧本有月份信息，只是不挂项目）。
--       pool_line_type：0 正常、1 冲销镜像（V20260818104603）。
-- 不确定度：中。pool_line_type 列若 B01 显示不存在，把它从 SELECT 和 GROUP BY 去掉。
IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
WITH w AS (SELECT 2025 AS yr)
SELECT p.source_type, p.pool_line_type, p.alloc_status, ap.period_year, ap.period_type,
       YEAR(p.business_date) AS biz_year, MONTH(p.business_date) AS biz_month,
       COUNT_BIG(*) AS row_cnt, COUNT(DISTINCT p.business_date) AS distinct_days,
       SUM(p.notax_amount) AS notax_sum
FROM dbo.psa_oh_cost_pool p
LEFT JOIN dbo.mdm_accounting_period ap ON ap.id = p.period_id
CROSS JOIN w
WHERE p.deleted = 0 AND p.source_type IN (8, 9)
  AND (ap.period_year = w.yr OR YEAR(p.business_date) = w.yr)
GROUP BY p.source_type, p.pool_line_type, p.alloc_status, ap.period_year, ap.period_type, YEAR(p.business_date), MONTH(p.business_date)
ORDER BY p.source_type, p.pool_line_type, p.alloc_status, ap.period_year, YEAR(p.business_date), MONTH(p.business_date);
END
