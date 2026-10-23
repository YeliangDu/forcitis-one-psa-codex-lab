-- M-29 基线补充（第二批）B24（one-rules 提供，来源 origin/claude/one-rules-context-2@943876a76 r0/m29b_baseline_supplement_queries.sql）。ops 仅加库名守卫与 SET LOCK_TIMEOUT 30000。one_evidence_ro（ADR-0037 第 5 项第 4 条），默认 READ COMMITTED，不用 NOLOCK。
SET LOCK_TIMEOUT 30000;
-- B24 WIP 明细当前状态反推 2025 年末在制（与 B21 独立的交叉验证）：区域/BU 及公司合计
-- 依赖：psa_wip_entry（cost_ledger_id, project_id, wip_stage, wip_status, notax_amount, entered_period_id, written_off_at, terminated_at, deleted）；
--       psa_cost_ledger（id, source_type, deleted）；mdm_accounting_period.period_year（入 WIP 期间）；psa_pm_project / system_dept。
-- 口径：入 WIP 期间年 ≤ 2025 的工时 WIP 明细，按现状与转出/终止时间归类：
--       a_至今仍在制 + b_2026起转出 + c_2026起终止 = 2025-12-31 仍在制（推算）；d_2025及以前已转出或终止 = 年末已不在制；
--       e_豁免 f_红冲或被替代 另列；g_时间缺失 = 状态为已转出/终止但没有对应时间，无法判断。入 WIP 期间在 2026 起的单列 x，不计。
--       wip_stage 带出：1 合同前投入、2 未完工、3 已完工、4 减值准备。
-- 不确定度：中偏高。(1) 现状不是年末状态：2025 年末已转出但随后被红冲回在制的明细，现状 0 会算成「至今仍在制」，结果对红冲后再转出的明细算作 2026 起转出，方向保持一致；
--       (2) 红冲后再转出会产生新 movement，但 wip_entry 只存最近状态；(3) 同 B21 的历史期初问题。
IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
WITH w AS (SELECT 2025 AS yr, CAST('20260101' AS datetime2) AS cut),
d AS (
    SELECT p.id AS project_id,
           ISNULL(sp.name, N'(无上级部门)') AS parent_name,
           ISNULL(sd.name, N'(项目无交付部门)') AS dept_name
    FROM dbo.psa_pm_project p
    LEFT JOIN dbo.system_dept sd ON sd.id = p.dept_id
    LEFT JOIN dbo.system_dept sp ON sp.id = sd.parent_id
)
SELECT cls.state_class, e.wip_stage,
       GROUPING_ID(d.parent_name, d.dept_name) AS lvl,
       d.parent_name, d.dept_name,
       COUNT_BIG(*) AS entry_cnt,
       SUM(e.notax_amount) AS entry_notax
FROM dbo.psa_wip_entry e
JOIN dbo.psa_cost_ledger l ON l.id = e.cost_ledger_id AND l.deleted = 0
JOIN dbo.mdm_accounting_period ap ON ap.id = e.entered_period_id
CROSS JOIN w
LEFT JOIN d ON d.project_id = e.project_id
CROSS APPLY (SELECT CASE
        WHEN ap.period_year > w.yr THEN N'x_2026起入WIP（不计）'
        WHEN e.wip_status = 0 THEN N'a_至今仍在制'
        WHEN e.wip_status = 1 AND e.written_off_at >= w.cut THEN N'b_2026起转出'
        WHEN e.wip_status = 2 AND e.terminated_at >= w.cut THEN N'c_2026起终止'
        WHEN e.wip_status = 1 AND e.written_off_at < w.cut THEN N'd_2025及以前已转出'
        WHEN e.wip_status = 2 AND e.terminated_at < w.cut THEN N'd_2025及以前已终止'
        WHEN e.wip_status = 3 THEN N'e_豁免不结转'
        WHEN e.wip_status = 9 THEN N'f_红冲或被替代'
        ELSE N'g_状态或时间缺失' END AS state_class) cls
WHERE e.deleted = 0 AND l.source_type = 0
GROUP BY cls.state_class, e.wip_stage, ROLLUP(d.parent_name, d.dept_name)
ORDER BY cls.state_class, e.wip_stage, d.parent_name, d.dept_name;
END
