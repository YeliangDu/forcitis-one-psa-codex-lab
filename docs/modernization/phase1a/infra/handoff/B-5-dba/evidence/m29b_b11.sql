-- M-29 基线补充（第二批）B11（one-rules 提供，来源 origin/claude/one-rules-context-2@943876a76 r0/m29b_baseline_supplement_queries.sql）。ops 仅加库名守卫与 SET LOCK_TIMEOUT 30000。one_evidence_ro（ADR-0037 第 7 项），默认 READ COMMITTED，不用 NOLOCK。
SET LOCK_TIMEOUT 30000;
-- B11 【仅当 B02 显示 dbo.annual_bonus_detail 存在才跑】年终奖源视图：business_date 年月分布（公司合计）
-- 依赖：annual_bonus_detail（business_date, process_type, fee_type, notax_amount, pay_amount, id）——列名取自 AnnualBonusDetailViewDO。
-- 判读：源视图 business_date 是否恒为 12-31；若有别的日期，说明源单有月份信息可用。fee_type：0 项目直接成本 / 1 销售 OH / 2 管理 OH。
-- 不确定度：中。视图在 OA 侧建，列名以 B02 为准；视图可能有软删或状态过滤条件，此处按源码读端的唯一谓词 process_type = '6' 过滤。
IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
SELECT YEAR(b.business_date) AS biz_year, MONTH(b.business_date) AS biz_month, b.fee_type,
       COUNT_BIG(*) AS row_cnt, COUNT(DISTINCT b.business_date) AS distinct_days,
       MIN(b.business_date) AS min_biz_date, MAX(b.business_date) AS max_biz_date,
       SUM(b.notax_amount) AS notax_sum, SUM(b.pay_amount) AS gross_sum
FROM dbo.annual_bonus_detail b
WHERE b.process_type = '6'
GROUP BY YEAR(b.business_date), MONTH(b.business_date), b.fee_type
ORDER BY YEAR(b.business_date), MONTH(b.business_date), b.fee_type;
END
