-- M-29 贡献毛利率分母改净服务收入前后值取证（one-rules 提供，来源 origin/claude/one-rules-context-2@3653a70e8 r0/m29_baseline_readonly_queries.sql）：Q42
-- ops 改动：每条外包 IF DB_NAME()=ruoyi-vue-pro；加 SET LOCK_TIMEOUT 30000；Q01 补一个缺失的右括号。one_evidence_ro（ADR-0037 第 7 项），默认 READ COMMITTED，不用 NOLOCK。
SET LOCK_TIMEOUT 30000;
-- Q42 合同条款名称里的「代垫/实报实销」关键词（合同层可转收线索）：只出个数
-- 依赖：psa_pm_contract_term.name / deleted（ProfitReportMapper.listClauseTitles 用到 t.name、t.deleted）。
-- 用途：若合同条款有「代垫费」「实报实销」类条款，则可转收费用至少能在合同层识别；不取条款名原文，只出命中个数。
-- 不确定度：高（关键词法）。
IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
SELECT k.kw, COUNT_BIG(*) AS term_cnt
FROM dbo.psa_pm_contract_term t
JOIN (VALUES (N'代垫'), (N'垫付'), (N'实报实销'), (N'差旅'), (N'据实结算'), (N'据实报销')) AS k(kw)
  ON t.name LIKE N'%' + k.kw + N'%'
WHERE t.deleted = 0
GROUP BY k.kw
ORDER BY k.kw;
END
