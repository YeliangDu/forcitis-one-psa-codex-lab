-- M-29 基线补充（第二批）B25（one-rules 提供，来源 origin/claude/one-rules-context-2@943876a76 r0/m29b_baseline_supplement_queries.sql）。ops 仅加库名守卫与 SET LOCK_TIMEOUT 30000。one_evidence_ro（ADR-0037 第 5 项第 4 条），默认 READ COMMITTED，不用 NOLOCK。
SET LOCK_TIMEOUT 30000;
-- B25 WIP 期间快照交叉验证：2025 各期末在制余额（所有来源类型，不分工时）
-- 依赖：psa_wip_period_balance（period_id, company_code, wip_stage, wip_status, balance, snapshot_at, deleted）；mdm_accounting_period（period_year, period_sequence, company_code）。
-- 用途：快照不含 source_type，只能得全部成本的在制总额，作为 B21（只工时）的上界；取最大 period_sequence 的期间为 2025 年末。
-- 不确定度：中。快照是否对 2025 末期已生成未核实（无行则本条无输出）；快照 wip_status 通常只存 0。
IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
SELECT ap.company_code, ap.period_year, ap.period_sequence, b.wip_stage, b.wip_status,
       COUNT_BIG(*) AS row_cnt, SUM(b.balance) AS balance_sum, MAX(b.snapshot_at) AS snapshot_at
FROM dbo.psa_wip_period_balance b
JOIN dbo.mdm_accounting_period ap ON ap.id = b.period_id
WHERE b.deleted = 0 AND ap.period_year = 2025
GROUP BY ap.company_code, ap.period_year, ap.period_sequence, b.wip_stage, b.wip_status
ORDER BY ap.company_code, ap.period_sequence, b.wip_stage, b.wip_status;
END
