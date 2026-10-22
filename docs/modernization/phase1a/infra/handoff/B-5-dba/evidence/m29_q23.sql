-- M-29 贡献毛利率分母改净服务收入前后值取证（one-rules 提供，来源 origin/claude/one-rules-context-2@3653a70e8 r0/m29_baseline_readonly_queries.sql）：Q23
-- ops 改动：每条外包 IF DB_NAME()=ruoyi-vue-pro；加 SET LOCK_TIMEOUT 30000；Q01 补一个缺失的右括号。one_evidence_ro（ADR-0037 第 7 项），默认 READ COMMITTED，不用 NOLOCK。
SET LOCK_TIMEOUT 30000;
-- Q23 成本台账的分录类型与有效性分布（公司合计）：判断 Q22 的过滤是否漏掉红冲/重分类
-- 依赖：psa_cost_ledger.entry_type（0 正常、1 金额红冲、3 条款重分类）、entry_status（0 有效、1 被重分类替代、2 被金额红冲）。
-- 不确定度：低（枚举来自 DDL 注释）。看 entry_type=1 行的 entry_status 与金额符号，决定 Q22 是否要把红冲行另加。
IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
WITH w AS (
    SELECT CAST('20250101' AS date) AS a_from, DATEADD(DAY, 1, CAST(GETDATE() AS date)) AS b_to
)
SELECT c.entry_type, c.entry_status,
       COUNT_BIG(*) AS row_cnt,
       SUM(c.notax_amount) AS notax_sum,
       SUM(CASE WHEN c.notax_amount < 0 THEN 1 ELSE 0 END) AS negative_cnt
FROM dbo.psa_cost_ledger c
CROSS JOIN w
WHERE c.deleted = 0 AND c.business_date >= w.a_from AND c.business_date < w.b_to
GROUP BY c.entry_type, c.entry_status
ORDER BY c.entry_type, c.entry_status;
END
