-- M-29 基线补充（第二批）B21（one-rules 提供，来源 origin/claude/one-rules-context-2@943876a76 r0/m29b_baseline_supplement_queries.sql）。ops 仅加库名守卫与 SET LOCK_TIMEOUT 30000。one_evidence_ro（ADR-0037 第 7 项），默认 READ COMMITTED，不用 NOLOCK。
SET LOCK_TIMEOUT 30000;
-- B21 截至 2025 年末已发生未结转的直接人工余额（movement 重放口径）：区域/BU 及公司合计
-- 依赖：同 B20；psa_pm_project.dept_id；system_dept。
-- 口径：balance_abs = Σ(type 0、3 的 ABS) − Σ(type 2、4、6 的 ABS)（与 WIP 期间快照同口径，source_type 限 0 与 12，两类分列）。
--       另给各类型原值合计，若原值带符号，可在本地用 enter_raw − writeoff_raw … 交叉验证；部门取项目当前交付部门。
-- 验证：调度方说该余额约 2,060 万元——比较公司合计行（lvl=3）两个 source_type 的 balance_abs（只取 0 时看 st=0 行；0 与 12 一并看合计）。
-- 不确定度：中偏高。(1) 历史年（2020～2025）台账残缺（AnnualBonusBasis 注释），WIP 可能缺历史期初；(2) 上线前的期初余额若走期初批次，可能不在本表；
--       (3) 项目换部门时按当前部门；(4) 2025 期间的 movement 若在 2026 年才创建（关账后补录）也被计入，见 B20 after_close_cnt。
IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
WITH w AS (SELECT 2025 AS yr),
d AS (
    SELECT p.id AS project_id,
           ISNULL(sp.name, N'(无上级部门)') AS parent_name,
           ISNULL(sd.name, N'(项目无交付部门)') AS dept_name
    FROM dbo.psa_pm_project p
    LEFT JOIN dbo.system_dept sd ON sd.id = p.dept_id
    LEFT JOIN dbo.system_dept sp ON sp.id = sd.parent_id
)
SELECT l.source_type,
       GROUPING_ID(d.parent_name, d.dept_name) AS lvl,
       d.parent_name, d.dept_name,
       COUNT(DISTINCT m.wip_entry_id) AS entry_cnt,
       SUM(CASE WHEN m.movement_type IN (0, 3) THEN ABS(m.notax_amount)
                WHEN m.movement_type IN (2, 4, 6) THEN -ABS(m.notax_amount) ELSE 0 END) AS balance_abs,
       SUM(CASE WHEN m.movement_type = 0 THEN m.notax_amount ELSE 0 END) AS enter_raw,
       SUM(CASE WHEN m.movement_type = 2 THEN m.notax_amount ELSE 0 END) AS writeoff_raw,
       SUM(CASE WHEN m.movement_type = 3 THEN m.notax_amount ELSE 0 END) AS writeoff_reversal_raw,
       SUM(CASE WHEN m.movement_type = 4 THEN m.notax_amount ELSE 0 END) AS terminate_raw,
       SUM(CASE WHEN m.movement_type = 6 THEN m.notax_amount ELSE 0 END) AS exempt_raw
FROM dbo.psa_wip_movement m
JOIN dbo.psa_cost_ledger l ON l.id = m.cost_ledger_id AND l.deleted = 0
JOIN dbo.mdm_accounting_period ap ON ap.id = m.period_id
CROSS JOIN w
LEFT JOIN d ON d.project_id = m.project_id
WHERE m.deleted = 0 AND ap.period_year <= w.yr AND l.source_type IN (0, 12)
GROUP BY l.source_type, ROLLUP(d.parent_name, d.dept_name)
ORDER BY l.source_type, d.parent_name, d.dept_name;
END
