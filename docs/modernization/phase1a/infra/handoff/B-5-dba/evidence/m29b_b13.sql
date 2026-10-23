-- M-29 基线补充（第二批）B13（one-rules 提供，来源 origin/claude/one-rules-context-2@943876a76 r0/m29b_baseline_supplement_queries.sql）。ops 仅加库名守卫与 SET LOCK_TIMEOUT 30000。one_evidence_ro（ADR-0037 第 5 项第 4 条），默认 READ COMMITTED，不用 NOLOCK。
SET LOCK_TIMEOUT 30000;
-- B13 条款名关键词命中：条款数、合同数、项目数（含软删与否分列）
-- 依赖：psa_pm_contract_term（name, contract_id, code, amount, final_amount, deleted）；psa_pm_contract（id, project_id, deleted）。
-- 用途：核对此前「4 条」的数；看放宽关键词（据实、实报实销…）是否还有；看有无合同缺失或项目缺失。条款名原文、合同号不出。
-- 不确定度：中。amount / final_amount 的含义取自 DO 注释（条款金额、最终金额），金额只作聚合参考。
IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
SELECT k.kw, t.deleted,
       COUNT_BIG(*) AS term_cnt,
       COUNT(DISTINCT t.contract_id) AS contract_cnt,
       COUNT(DISTINCT c.project_id) AS project_cnt,
       SUM(CASE WHEN c.id IS NULL THEN 1 ELSE 0 END) AS contract_missing_cnt,
       SUM(CASE WHEN c.id IS NOT NULL AND c.deleted <> 0 THEN 1 ELSE 0 END) AS contract_deleted_cnt,
       SUM(CASE WHEN c.id IS NOT NULL AND c.project_id IS NULL THEN 1 ELSE 0 END) AS project_null_cnt,
       SUM(ISNULL(t.amount, 0)) AS term_amount_sum, SUM(ISNULL(t.final_amount, 0)) AS term_final_amount_sum
FROM dbo.psa_pm_contract_term t
JOIN (VALUES (N'据实结算'), (N'据实'), (N'实报实销'), (N'据实报销'), (N'实际发生'), (N'代垫')) AS k(kw)
  ON t.name LIKE N'%' + k.kw + N'%'
LEFT JOIN dbo.psa_pm_contract c ON c.id = t.contract_id
GROUP BY k.kw, t.deleted
ORDER BY k.kw, t.deleted;
END
