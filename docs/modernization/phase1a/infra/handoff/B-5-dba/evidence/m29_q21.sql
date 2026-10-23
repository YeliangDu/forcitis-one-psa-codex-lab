-- M-29 贡献毛利率分母改净服务收入前后值取证（one-rules 提供，来源 origin/claude/one-rules-context-2@3653a70e8 r0/m29_baseline_readonly_queries.sql）：Q21
-- ops 改动：每条外包 IF DB_NAME()=ruoyi-vue-pro；加 SET LOCK_TIMEOUT 30000；Q01 补一个缺失的右括号。one_evidence_ro（ADR-0037 第 7 项），默认 READ COMMITTED，不用 NOLOCK。
SET LOCK_TIMEOUT 30000;
-- Q21 进损益表的损益类型与来源类型分布（公司合计；核对 Q20 的过滤是否合理）
-- 依赖：psa_pl_ledger.pl_type / source_type / notax_amount / period_id。
-- 不确定度：低。pl_type：0 主营成本、1 资产减值、2 营业外支出、9 红冲冲减（DDL 注释）。
IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
SELECT ap.period_year, l.pl_type, l.source_type,
       COUNT_BIG(*) AS row_cnt, SUM(l.notax_amount) AS cost_notax
FROM dbo.psa_pl_ledger l
JOIN dbo.mdm_accounting_period ap ON ap.id = l.period_id AND ap.period_year IN (2025, 2026)
WHERE l.deleted = 0
GROUP BY ap.period_year, l.pl_type, l.source_type
ORDER BY ap.period_year, l.pl_type, l.source_type;
END
