-- M-29 贡献毛利率分母改净服务收入前后值取证（one-rules 提供，来源 origin/claude/one-rules-context-2@3653a70e8 r0/m29_baseline_readonly_queries.sql）：Q20
-- ops 改动：每条外包 IF DB_NAME()=ruoyi-vue-pro；加 SET LOCK_TIMEOUT 30000；Q01 补一个缺失的右括号。one_evidence_ro（ADR-0037 第 7 项），默认 READ COMMITTED，不用 NOLOCK。
SET LOCK_TIMEOUT 30000;
-- Q20 项目成本·进损益口径（与收入配比的主营成本）：（窗口 × 成本类 × 上级部门 × 交付部门）及合计
-- 依赖：psa_pl_ledger（deleted, pl_type, source_type, notax_amount, period_id, project_id）；mdm_accounting_period.period_year；
--       psa_pm_project.dept_id；system_dept。
-- 口径：pl_type IN (0,9)（主营业务成本与红冲冲减，红冲为负），与老系统三级利润读侧 sumMainCostByProjectTerm 一致，
--       但窗口按「期间所属年」而非「截止期末累计」。期间取 period_id → mdm_accounting_period.period_year（2025 含年末调整期）。
-- 成本类（按 source_type，枚举 CarryForwardDataTypeEnum）：
--   DL工时     = 0 工时、12 人月尾差调整          → M-24 直接人工
--   DL奖金待定 = 8 奖金计提、9 年度奖金          → 是否计入直接人工未决，单列
--   ODC费用    = 1 日常、2 差旅、3 付款申请、4 特殊付款、7 招待 → M-25 直接费用候选；其中 3/4 可能含分包付款，见 Q40
--   SUB委外    = 5 委外                           → M-26 分包成本/外包成本（转付成本之一）
--   INFO居间   = 6 信息服务                       → 居间费，词表定为非转付成本，在 CM 中扣减
--   OTHER      = 其余（10 OH 手工导入、11 零工时薪资等；理论上不进本表，出现即报告）
-- 不确定度：中偏低。(1) 3/4 的分包与普通付款无法在本表区分；(2) pl_type 1/2（资产减值、营业外）被排除，坏 WIP 不在 CM 内是否合理待定；
--       (3) 项目→部门取项目当前交付部门，不是成本发生时的部门；(4) psa_pl_ledger 无部门列（cost_dept_id 已于 V20260730144333 删除），故经项目取部门。
IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
WITH d AS (
    SELECT p.id AS project_id,
           ISNULL(sp.name, N'(无上级部门)') AS parent_name,
           ISNULL(sd.name, N'(项目无交付部门)') AS dept_name
    FROM dbo.psa_pm_project p
    LEFT JOIN dbo.system_dept sd ON sd.id = p.dept_id
    LEFT JOIN dbo.system_dept sp ON sp.id = sd.parent_id
)
SELECT CASE ap.period_year WHEN 2025 THEN N'a_2025整年' WHEN 2026 THEN N'b_2026至今' END AS win_label,
       cc.cost_class,
       GROUPING_ID(d.parent_name, d.dept_name) AS lvl,
       d.parent_name, d.dept_name,
       COUNT_BIG(*) AS row_cnt,
       COUNT(DISTINCT l.project_id) AS project_cnt,
       SUM(l.notax_amount) AS cost_notax
FROM dbo.psa_pl_ledger l
JOIN dbo.mdm_accounting_period ap ON ap.id = l.period_id AND ap.period_year IN (2025, 2026)
LEFT JOIN d ON d.project_id = l.project_id
CROSS APPLY (SELECT CASE WHEN l.source_type IN (0, 12) THEN N'DL工时'
                         WHEN l.source_type IN (8, 9) THEN N'DL奖金待定'
                         WHEN l.source_type IN (1, 2, 3, 4, 7) THEN N'ODC费用'
                         WHEN l.source_type = 5 THEN N'SUB委外'
                         WHEN l.source_type = 6 THEN N'INFO居间'
                         ELSE N'OTHER' END AS cost_class) cc
WHERE l.deleted = 0 AND l.pl_type IN (0, 9)
GROUP BY ap.period_year, cc.cost_class, ROLLUP(d.parent_name, d.dept_name)
ORDER BY ap.period_year, cc.cost_class, d.parent_name, d.dept_name;
END
