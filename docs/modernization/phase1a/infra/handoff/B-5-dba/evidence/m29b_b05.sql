-- M-29 基线补充（第二批）B05（one-rules 提供，来源 origin/claude/one-rules-context-2@943876a76 r0/m29b_baseline_supplement_queries.sql）。ops 仅加库名守卫与 SET LOCK_TIMEOUT 30000。one_evidence_ro（ADR-0037 第 7 项），默认 READ COMMITTED，不用 NOLOCK。
SET LOCK_TIMEOUT 30000;
-- B05 business_date 年份与期间年份是否一致：交叉表（psa_pl_ledger，全年份）
-- 依赖：同 B03。
-- 口径：year_gap = 期间年 − business_date 年；0 一致，>0 表示进损益期间晚于成本发生年（跨年结转），<0 表示早于（不应出现）。
-- 不确定度：低。
IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
SELECT l.source_type, l.pl_type,
       YEAR(l.business_date) AS biz_year, ap.period_year, ap.period_type,
       ap.period_year - YEAR(l.business_date) AS year_gap,
       COUNT_BIG(*) AS row_cnt, SUM(l.notax_amount) AS notax_sum
FROM dbo.psa_pl_ledger l
LEFT JOIN dbo.mdm_accounting_period ap ON ap.id = l.period_id
WHERE l.deleted = 0 AND l.source_type IN (8, 9)
GROUP BY l.source_type, l.pl_type, YEAR(l.business_date), ap.period_year, ap.period_type
ORDER BY l.source_type, l.pl_type, YEAR(l.business_date), ap.period_year, ap.period_type;
END
