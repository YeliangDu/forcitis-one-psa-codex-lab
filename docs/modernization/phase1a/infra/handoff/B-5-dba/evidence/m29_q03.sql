-- M-29 贡献毛利率分母改净服务收入前后值取证（one-rules 提供，来源 origin/claude/one-rules-context-2@3653a70e8 r0/m29_baseline_readonly_queries.sql）：Q03
-- ops 改动：每条外包 IF DB_NAME()=ruoyi-vue-pro；加 SET LOCK_TIMEOUT 30000；Q01 补一个缺失的右括号。one_evidence_ro（ADR-0037 第 5 项第 4 条），默认 READ COMMITTED，不用 NOLOCK。
SET LOCK_TIMEOUT 30000;
-- Q03 会计期间表：两个窗口覆盖的期间（只取期间元数据）
-- 依赖：mdm_accounting_period（AccountingPeriodDO：period_year、period_type、start_date、end_date、status、company_code）。
-- 不确定度：中。ProfitReportMapper 注释称会计期间「为年度而非自然月」（#1712），与 DO 的 1-12 月加年末调整期不一致；以本条结果为准。
IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
SELECT company_code, period_year, period_type, COUNT_BIG(*) AS period_cnt,
       MIN(start_date) AS min_start, MAX(end_date) AS max_end,
       SUM(CASE WHEN status = 3 THEN 1 ELSE 0 END) AS closed_cnt
FROM dbo.mdm_accounting_period
WHERE period_year IN (2025, 2026)
GROUP BY company_code, period_year, period_type
ORDER BY company_code, period_year, period_type;
END
