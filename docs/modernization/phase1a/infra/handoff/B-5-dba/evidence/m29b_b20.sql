-- M-29 基线补充（第二批）B20（one-rules 提供，来源 origin/claude/one-rules-context-2@943876a76 r0/m29b_baseline_supplement_queries.sql）。ops 仅加库名守卫与 SET LOCK_TIMEOUT 30000。one_evidence_ro（ADR-0037 第 5 项第 4 条），默认 READ COMMITTED，不用 NOLOCK。
SET LOCK_TIMEOUT 30000;
-- B20 WIP 流水原始分布：截至 2025 年末（期间年 ≤ 2025），按（期间年 × 来源类型 × 流水类型）（公司合计）
-- 依赖：psa_wip_movement（movement_type, notax_amount, period_id, create_time, cost_ledger_id, deleted）；psa_cost_ledger（id, source_type, deleted）；mdm_accounting_period（period_year, closed_time）。
-- 口径：来源类型限 0 工时与 12 人月尾差（直接人工）。raw_sum 带符号原值；abs_sum 绝对值；neg_cnt 负额行数（判断红冲行金额符号）；
--       after_close_cnt = 创建时间晚于期间关账时间的行数（WIP 报表口径按关账冻结截止，晚于关账的不计；本条不剔除，只报告）。
-- 不确定度：中。movement 金额符号约定在 DO 注释里写「红冲用负额或反向 movement」，不明确，故同时给原值与绝对值。
IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
WITH w AS (SELECT 2025 AS yr)
SELECT ap.period_year, l.source_type, m.movement_type,
       COUNT_BIG(*) AS row_cnt,
       SUM(CASE WHEN m.notax_amount < 0 THEN 1 ELSE 0 END) AS neg_cnt,
       SUM(CASE WHEN ap.closed_time IS NOT NULL AND m.create_time > ap.closed_time THEN 1 ELSE 0 END) AS after_close_cnt,
       SUM(m.notax_amount) AS raw_sum, SUM(ABS(m.notax_amount)) AS abs_sum
FROM dbo.psa_wip_movement m
JOIN dbo.psa_cost_ledger l ON l.id = m.cost_ledger_id AND l.deleted = 0
JOIN dbo.mdm_accounting_period ap ON ap.id = m.period_id
CROSS JOIN w
WHERE m.deleted = 0 AND ap.period_year <= w.yr AND l.source_type IN (0, 12)
GROUP BY ap.period_year, l.source_type, m.movement_type
ORDER BY ap.period_year, l.source_type, m.movement_type;
END
