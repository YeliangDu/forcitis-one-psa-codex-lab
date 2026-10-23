-- M-29 贡献毛利率分母改净服务收入前后值取证（one-rules 提供，来源 origin/claude/one-rules-context-2@3653a70e8 r0/m29_baseline_readonly_queries.sql）：Q41
-- ops 改动：每条外包 IF DB_NAME()=ruoyi-vue-pro；加 SET LOCK_TIMEOUT 30000；Q01 补一个缺失的右括号。one_evidence_ro（ADR-0037 第 5 项第 4 条），默认 READ COMMITTED，不用 NOLOCK。
SET LOCK_TIMEOUT 30000;
-- Q41 「代垫/可转收」关键词探测（只出个数与金额）：成本台账费用类别名里有无 代垫、垫付、垫资、代付、代收、转收、实报实销、分包、外包、委外、劳务
-- 依赖：psa_cost_ledger.cost_type_name / notax_amount / business_date。
-- 用途：判断代垫费用能否在老系统里识别。老系统源码与文档里没有「可向客户转收」标识（见 M29_BASELINE_MAPPING.md），本条是唯一的名称探测。
-- 不确定度：高（关键词法：命中少不代表不存在，命中多也不代表可转收）。关键词表是提议，运维可补充。
IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
WITH w AS (
    SELECT CAST('20250101' AS date) AS a_from, DATEADD(DAY, 1, CAST(GETDATE() AS date)) AS b_to
)
SELECT k.kw, COUNT_BIG(*) AS row_cnt, COUNT(DISTINCT c.project_id) AS project_cnt,
       SUM(c.notax_amount) AS notax_sum, MIN(c.business_date) AS first_date, MAX(c.business_date) AS last_date
FROM dbo.psa_cost_ledger c
CROSS JOIN w
JOIN (VALUES (N'代垫'), (N'垫付'), (N'垫资'), (N'代付'), (N'代收'), (N'转收'), (N'实报实销'),
             (N'分包'), (N'外包'), (N'委外'), (N'劳务')) AS k(kw)
  ON c.cost_type_name LIKE N'%' + k.kw + N'%'
WHERE c.deleted = 0 AND c.entry_status = 0
  AND c.business_date >= w.a_from AND c.business_date < w.b_to
GROUP BY k.kw
ORDER BY k.kw;
END
