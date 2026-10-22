-- M-29 基线补充（第二批）B03（one-rules 提供，来源 origin/claude/one-rules-context-2@943876a76 r0/m29b_baseline_supplement_queries.sql）。ops 仅加库名守卫与 SET LOCK_TIMEOUT 30000。one_evidence_ro（ADR-0037 第 7 项），默认 READ COMMITTED，不用 NOLOCK。
SET LOCK_TIMEOUT 30000;
-- B03 奖金行总览：psa_pl_ledger 里 source_type 8、9 的行，按（期间年 × 期间类型 × 来源类型 × 损益类型 × 触发类型）
-- 依赖：psa_pl_ledger（source_type, pl_type, trigger_type, period_id, business_date, notax_amount, project_id, deleted）；mdm_accounting_period（period_year, period_type）。
-- 用途：全年份看奖金行进损益的期间分布；确认 8 是否真无行；红冲行（pl_type 9）落在哪个期间。
-- 不确定度：低。LEFT JOIN 期间表，period_year 为 NULL 表示 period_id 对不上期间。
IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
SELECT ap.period_year, ap.period_type, l.source_type, l.pl_type, l.trigger_type,
       COUNT_BIG(*) AS row_cnt, COUNT(DISTINCT l.project_id) AS project_cnt,
       MIN(l.business_date) AS min_biz_date, MAX(l.business_date) AS max_biz_date,
       SUM(l.notax_amount) AS notax_sum
FROM dbo.psa_pl_ledger l
LEFT JOIN dbo.mdm_accounting_period ap ON ap.id = l.period_id
WHERE l.deleted = 0 AND l.source_type IN (8, 9)
GROUP BY ap.period_year, ap.period_type, l.source_type, l.pl_type, l.trigger_type
ORDER BY ap.period_year, ap.period_type, l.source_type, l.pl_type, l.trigger_type;
END
