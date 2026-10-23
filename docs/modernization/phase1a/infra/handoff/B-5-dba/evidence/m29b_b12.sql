-- M-29 基线补充（第二批）B12（one-rules 提供，来源 origin/claude/one-rules-context-2@943876a76 r0/m29b_baseline_supplement_queries.sql）。ops 仅加库名守卫与 SET LOCK_TIMEOUT 30000。one_evidence_ro（ADR-0037 第 5 项第 4 条），默认 READ COMMITTED，不用 NOLOCK。
SET LOCK_TIMEOUT 30000;
-- B12 【仅当 B02 显示 dbo.monthly_bonus_accrual 存在才跑】奖金计提源视图：business_date 年月分布与 fee_type（公司合计）
-- 依赖：monthly_bonus_accrual（business_date, process_type, fee_type, notax_amount）——列名取自 MonthlyBonusAccrualViewDO；
--       DO 注释称视图另有 pay_date，B02 若列出 pay_date，可另加一条按 pay_date 月份分布（本条不用，因未映射、未核实）。
-- 判读：fee_type=0 的行数（应为异常）；计提的 business_date 是否逐月。
-- 不确定度：中。同 B11。
IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
SELECT YEAR(b.business_date) AS biz_year, MONTH(b.business_date) AS biz_month, b.fee_type,
       COUNT_BIG(*) AS row_cnt, COUNT(DISTINCT b.business_date) AS distinct_days,
       SUM(b.notax_amount) AS notax_sum
FROM dbo.monthly_bonus_accrual b
WHERE b.process_type = '7'
GROUP BY YEAR(b.business_date), MONTH(b.business_date), b.fee_type
ORDER BY YEAR(b.business_date), MONTH(b.business_date), b.fee_type;
END
