-- M-29 贡献毛利率分母改净服务收入：前后值取证——只读聚合查询（草稿，未执行）
-- 起草：2026-10-22。依据：用户 2026-10-22 决定（KPI-Q03）；METRIC_BASELINE_DRAFT.md M-24~M-29；词表 net_service_revenue / pass_through_cost（FD-41）。
-- 口径映射、已知差异、未决点见同目录 M29_BASELINE_MAPPING.md。
--
-- 【访问纪律】（ADR-0037 第 7、8 项；登记格式见 LEGACY_READ_REGISTER.md）
--   1. 账号：取证专用只读账号 one_evidence_ro；库：ruoyi-vue-pro（老系统业务库）。不用运行时账号、高权限账号或应用数据源。
--   2. 只含 SELECT（可带 WITH 公共表表达式）。不含 DECLARE / SET / 临时表 / 写语句，以便通过「拒绝非 SELECT」的单文件程序。
--      因此窗口日期写在每条查询开头的 WITH w 里；改窗口只改 w。
--   3. 不用 NOLOCK / READ UNCOMMITTED / 任何表提示（ADR-0037 第 8 项第 1 条）。保持默认 READ COMMITTED。
--      执行前由运维先只读核对 sys.databases.is_read_committed_snapshot_on（Q00 可查）并记录；不对外部库做 ALTER DATABASE。
--   4. 超时：本文件不能写 SET LOCK_TIMEOUT / SET QUERY_GOVERNOR（SET 会被程序拒绝）。请运维在连接工厂/程序参数里设：
--      锁等待超时 30 秒、语句超时 150 秒、套接字超时（与 bi_audit 样板同为单条 150 秒）。任一超时即停止并报告，不重试加压。
--   5. 大表分批：Q20/Q22/Q40 扫 psa_pl_ledger / psa_cost_ledger（行数未知）。每条只扫窗口内行，两个窗口合并为一次扫描；
--      一次只跑一条，低峰执行；若 Q00 显示单表过大（建议 > 2000 万行）或任一条超时，
--      把 w 里的窗口拆成按季度的小窗口分次运行，本地相加（成本类查询把 period_year 条件改为 ap.start_date 区间）。
--   6. 输出：只出聚合金额与计数。分组粒度不细于「区域/BU」（交付部门及其上级部门）。
--      不选择：项目编码/名称、客户、合同号/条款号、单据号、人员、供应商、备注。部门名是组织名，非个人信息。
--      结果落运维约定的数据盘目录，不带回本机明细；会话里只留结论。每次取证登记一行（账号、库表、目的、输出形式与去向、删除时点）。
--
-- 【与样板 docs/data/metric-baseline/bi-audit-readonly-queries.sql 的差异】
--   样板（R0·Q11，2026-10-14 起草）：读 BI 库审计表；沿用「READ UNCOMMITTED + WITH (NOLOCK)」；需用户确认访问方式；输出限于计数；
--          列名用 <占位> 待元数据确认。
--   本文件：读业务库 ruoyi-vue-pro；不用 NOLOCK / READ UNCOMMITTED（ADR-0037 第 8 项取代了样板时期的做法，脏读会让对账结论失效）；
--          取证不逐次请示但要登记一行（ADR-0037 第 7 项）；列名已按老系统源码填好，确认不了的写在每条前的「不确定度」，
--          并由 Q01/Q43 先查元数据；输出含金额聚合（任务明确要求），仍不含明细。
--
-- 【窗口】a = 2025 整年；b = 2026-01-01 至今（含今天）。
--   收入按确认日 psa_pm_cuvalue.complete_date；成本按会计期间所属年（mdm_accounting_period.period_year，含年末调整期）或发生日，见各条。
-- 【单位】金额为元。币种：老系统未见多币种字段，按单币种处理（见 M29_BASELINE_MAPPING.md 未决点）。
-- 【区域/BU】项目交付部门 psa_pm_project.dept_id → system_dept（本级 name，上级 parent_id 对应的 name）。
--   「哪一级算区域、哪一级算 BU」未核实：先跑 Q02 看部门树，再决定结果取 parent_name 还是 dept_name 汇总。
-- 【结果行层级】lvl = GROUPING_ID(parent_name, dept_name)：0 交付部门行；1 上级部门小计；3 公司合计。

-- =====================================================================================
-- Q00 环境核对：隔离级别与目标表行数量级（不取业务数据）
-- 依赖：sys.databases；sys.dm_db_partition_stats（需 VIEW DATABASE STATE，无权限则本条报错，忽略）。
-- 不确定度：低。行数只用于判断是否要拆窗口。
SELECT DB_NAME() AS db_name,
       (SELECT is_read_committed_snapshot_on FROM sys.databases WHERE name = DB_NAME()) AS rcsi_on,
       t.name AS table_name, SUM(ps.row_count) AS approx_rows
FROM sys.tables t
JOIN sys.dm_db_partition_stats ps ON ps.object_id = t.object_id AND ps.index_id IN (0, 1)
WHERE t.name IN (N'psa_pm_cuvalue', N'psa_pl_ledger', N'psa_cost_ledger', N'psa_wip_outsourcing',
                 N'psa_wip_commission', N'psa_pm_project', N'system_dept', N'mdm_accounting_period')
GROUP BY t.name
ORDER BY t.name;

-- =====================================================================================
-- Q01 列名核对：下面各条用到的表与列是否存在（只取元数据）
-- 依据：Flyway 迁移脚本、DO 类、数据字典（路径见 M29_BASELINE_MAPPING.md）。psa_pm_cuvalue 基础列、psa_pm_project、system_dept 不在仓库 DDL 内，
--       列名来自 DO 类与字典，未对生产核对（这正是本条的用途）。
-- 用法：逐表比对期望列；缺列的查询先不跑，回报。
SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = N'dbo'
  AND (   (TABLE_NAME = N'psa_pm_cuvalue'        AND COLUMN_NAME IN (N'project_id', N'dept_id', N'complete_date', N'complete_amount', N'no_tax_amount', N'tax', N'rate', N'type', N'company_code', N'write_off_flag'))
       OR (TABLE_NAME = N'psa_pl_ledger'         AND COLUMN_NAME IN (N'project_id', N'period_id', N'pl_type', N'source_type', N'notax_amount', N'business_date', N'company_code', N'deleted', N'cost_org_id'))
       OR (TABLE_NAME = N'psa_cost_ledger'       AND COLUMN_NAME IN (N'project_id', N'period_id', N'entry_type', N'entry_status', N'source_type', N'cost_type_code', N'cost_type_name', N'fee_type', N'business_date', N'notax_amount', N'company_code', N'deleted'))
       OR (TABLE_NAME = N'psa_wip_outsourcing'   AND COLUMN_NAME IN (N'project_id', N'dept_id', N'complete_date', N'amount', N'notax_amount', N'write_off_flag', N'project_write_off_flag', N'cost_type_code'))
       OR (TABLE_NAME = N'psa_wip_commission'    AND COLUMN_NAME IN (N'project_id', N'dept_id', N'record_date', N'amount', N'notax_amount', N'write_off_flag', N'project_write_off_flag', N'cost_type_code'))
       OR (TABLE_NAME = N'psa_pm_project'        AND COLUMN_NAME IN (N'id', N'dept_id'))
       OR (TABLE_NAME = N'system_dept'           AND COLUMN_NAME IN (N'id', N'name', N'parent_id', N'deleted'))
       OR (TABLE_NAME = N'mdm_accounting_period' AND COLUMN_NAME IN (N'id', N'company_code', N'period_code', N'period_type', N'period_year', N'period_month', N'start_date', N'end_date', N'status'))
       OR (TABLE_NAME = N'psa_pm_contract_term'  AND COLUMN_NAME IN (N'name', N'code', N'deleted')))
ORDER BY TABLE_NAME, COLUMN_NAME;

-- =====================================================================================
-- Q02 部门树：拥有项目的交付部门及其上级（用于确定「区域/BU」取哪一级）
-- 依赖：psa_pm_project.dept_id；system_dept.id / name / parent_id。
-- 不确定度：中。parent_id 来自 A8 PATH 去掉最后 4 位（docs/data/column-ownership.md system_dept 行），层级深度未核实。
SELECT sd.id AS dept_id, sd.name AS dept_name, sd.parent_id, sp.name AS parent_name,
       COUNT_BIG(p.id) AS project_cnt
FROM dbo.psa_pm_project p
LEFT JOIN dbo.system_dept sd ON sd.id = p.dept_id
LEFT JOIN dbo.system_dept sp ON sp.id = sd.parent_id
GROUP BY sd.id, sd.name, sd.parent_id, sp.name
ORDER BY sp.name, sd.name;

-- =====================================================================================
-- Q03 会计期间表：两个窗口覆盖的期间（只取期间元数据）
-- 依赖：mdm_accounting_period（AccountingPeriodDO：period_year、period_type、start_date、end_date、status、company_code）。
-- 不确定度：中。ProfitReportMapper 注释称会计期间「为年度而非自然月」（#1712），与 DO 的 1-12 月加年末调整期不一致；以本条结果为准。
SELECT company_code, period_year, period_type, COUNT_BIG(*) AS period_cnt,
       MIN(start_date) AS min_start, MAX(end_date) AS max_end,
       SUM(CASE WHEN status = 3 THEN 1 ELSE 0 END) AS closed_cnt
FROM dbo.mdm_accounting_period
WHERE period_year IN (2025, 2026)
GROUP BY company_code, period_year, period_type
ORDER BY company_code, period_year, period_type;

-- =====================================================================================
-- Q10 收入（不含税，权责）：（窗口 × 上级部门 × 交付部门）及合计。新旧分母的共同起点（老分母 = 本条；新分母 = 本条减转付成本）
-- 依赖：psa_pm_cuvalue（project_id, complete_date, no_tax_amount, type）；psa_pm_project.dept_id；system_dept。
-- 口径：type IN (0,1)（0 正常确认、1 红冲退回，退回为负额，直接相加即净额），与老系统三级利润读侧
--       ProfitReportMapper.sumRevenueByProjectTerm 一致；含已核销行；不按 company_code 过滤（该列存量可能为 NULL，老读侧严格等值会漏）。
-- 不确定度：中。no_tax_amount 存量行可能为 NULL（V20260728 加列时「存量无从回填」），本条把 NULL 当 0，可能低估；NULL 行数与含税额见 Q11。
--       「产值」即新系统「收入（不含税）」按 METRIC_BASELINE M-08 视为同一事实，未另核。
WITH w AS (
    SELECT CAST('20250101' AS date) AS a_from, CAST('20260101' AS date) AS a_to,
           CAST('20260101' AS date) AS b_from, DATEADD(DAY, 1, CAST(GETDATE() AS date)) AS b_to
),
d AS (
    SELECT p.id AS project_id,
           ISNULL(sp.name, N'(无上级部门)') AS parent_name,
           ISNULL(sd.name, N'(项目无交付部门)') AS dept_name
    FROM dbo.psa_pm_project p
    LEFT JOIN dbo.system_dept sd ON sd.id = p.dept_id
    LEFT JOIN dbo.system_dept sp ON sp.id = sd.parent_id
)
SELECT x.win_label,
       GROUPING_ID(d.parent_name, d.dept_name) AS lvl,
       d.parent_name, d.dept_name,
       COUNT_BIG(*) AS row_cnt,
       COUNT(DISTINCT c.project_id) AS project_cnt,
       SUM(ISNULL(c.no_tax_amount, 0)) AS rev_notax
FROM dbo.psa_pm_cuvalue c
CROSS JOIN w
LEFT JOIN d ON d.project_id = c.project_id
CROSS APPLY (SELECT CASE WHEN c.complete_date >= w.a_from AND c.complete_date < w.a_to THEN N'a_2025整年'
                         WHEN c.complete_date >= w.b_from AND c.complete_date < w.b_to THEN N'b_2026至今' END AS win_label) x
WHERE c.[type] IN (0, 1)
  AND c.complete_date >= w.a_from AND c.complete_date < w.b_to
GROUP BY x.win_label, ROLLUP(d.parent_name, d.dept_name)
ORDER BY x.win_label, d.parent_name, d.dept_name;

-- =====================================================================================
-- Q11 收入取数质量：no_tax_amount 为 NULL 的行数与含税额；产值部门与项目交付部门不一致的行数
-- 依赖：psa_pm_cuvalue.no_tax_amount / complete_amount / dept_id / rate / company_code；psa_pm_project.dept_id。
-- 不确定度：中。用于判断 Q10 低估程度，以及「部门取项目交付部门」是否合理（cuvalue.dept_id 为交付部门，字典与 DO 注释一致）。
--       不做 /1.06 回算：新口径用单据实际税率（METRIC_BASELINE M-08），回算只是估计，需要时本地用 complete_amount 与 rate 另算。
WITH w AS (
    SELECT CAST('20250101' AS date) AS a_from, CAST('20260101' AS date) AS a_to,
           CAST('20260101' AS date) AS b_from, DATEADD(DAY, 1, CAST(GETDATE() AS date)) AS b_to
)
SELECT x.win_label,
       COUNT_BIG(*) AS row_cnt,
       SUM(CASE WHEN c.no_tax_amount IS NULL THEN 1 ELSE 0 END) AS notax_null_cnt,
       SUM(CASE WHEN c.no_tax_amount IS NULL THEN ISNULL(c.complete_amount, 0) ELSE 0 END) AS notax_null_gross_amount,
       SUM(CASE WHEN c.no_tax_amount IS NULL AND c.rate IS NOT NULL THEN 1 ELSE 0 END) AS notax_null_but_rate_cnt,
       SUM(CASE WHEN c.dept_id IS NOT NULL AND p.dept_id IS NOT NULL AND c.dept_id <> p.dept_id THEN 1 ELSE 0 END) AS dept_mismatch_cnt,
       SUM(CASE WHEN p.id IS NULL THEN 1 ELSE 0 END) AS project_missing_cnt,
       SUM(CASE WHEN c.company_code IS NULL THEN 1 ELSE 0 END) AS company_code_null_cnt
FROM dbo.psa_pm_cuvalue c
CROSS JOIN w
LEFT JOIN dbo.psa_pm_project p ON p.id = c.project_id
CROSS APPLY (SELECT CASE WHEN c.complete_date >= w.a_from AND c.complete_date < w.a_to THEN N'a_2025整年'
                         WHEN c.complete_date >= w.b_from AND c.complete_date < w.b_to THEN N'b_2026至今' END AS win_label) x
WHERE c.[type] IN (0, 1)
  AND c.complete_date >= w.a_from AND c.complete_date < w.b_to
GROUP BY x.win_label
ORDER BY x.win_label;

-- =====================================================================================
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

-- =====================================================================================
-- Q21 进损益表的损益类型与来源类型分布（公司合计；核对 Q20 的过滤是否合理）
-- 依赖：psa_pl_ledger.pl_type / source_type / notax_amount / period_id。
-- 不确定度：低。pl_type：0 主营成本、1 资产减值、2 营业外支出、9 红冲冲减（DDL 注释）。
SELECT ap.period_year, l.pl_type, l.source_type,
       COUNT_BIG(*) AS row_cnt, SUM(l.notax_amount) AS cost_notax
FROM dbo.psa_pl_ledger l
JOIN dbo.mdm_accounting_period ap ON ap.id = l.period_id AND ap.period_year IN (2025, 2026)
WHERE l.deleted = 0
GROUP BY ap.period_year, l.pl_type, l.source_type
ORDER BY ap.period_year, l.pl_type, l.source_type;

-- =====================================================================================
-- Q22 项目成本·发生口径（成本台账）：（窗口 × 成本类 × 上级部门 × 交付部门）及合计
-- 依赖：psa_cost_ledger（deleted, entry_status, source_type, notax_amount, business_date, project_id）；其余同 Q20。
-- 口径：entry_status = 0（有效）；窗口按 business_date（工时=工时日、付款=付款日、委外=委外确认日）。成本类划分同 Q20。
-- 与 Q20 的关系：Q22 是已发生、未必已结转（可能仍在 WIP，成本台账不拥有 WIP 状态）；Q20 是已转进损益。
--       哪个更接近新系统 M-27「项目成本（合同履约成本结转）」「权责」需口径负责人定（见 M29_BASELINE_MAPPING.md 未决点）。
-- 不确定度：中偏低。entry_status=0 是否把红冲行（entry_type=1）正确计入未知——先看 Q23；本条只含 entry_status=0 的行。
WITH w AS (
    SELECT CAST('20250101' AS date) AS a_from, CAST('20260101' AS date) AS a_to,
           CAST('20260101' AS date) AS b_from, DATEADD(DAY, 1, CAST(GETDATE() AS date)) AS b_to
),
d AS (
    SELECT p.id AS project_id,
           ISNULL(sp.name, N'(无上级部门)') AS parent_name,
           ISNULL(sd.name, N'(项目无交付部门)') AS dept_name
    FROM dbo.psa_pm_project p
    LEFT JOIN dbo.system_dept sd ON sd.id = p.dept_id
    LEFT JOIN dbo.system_dept sp ON sp.id = sd.parent_id
)
SELECT x.win_label, cc.cost_class,
       GROUPING_ID(d.parent_name, d.dept_name) AS lvl,
       d.parent_name, d.dept_name,
       COUNT_BIG(*) AS row_cnt,
       COUNT(DISTINCT c.project_id) AS project_cnt,
       SUM(c.notax_amount) AS cost_notax
FROM dbo.psa_cost_ledger c
CROSS JOIN w
LEFT JOIN d ON d.project_id = c.project_id
CROSS APPLY (SELECT CASE WHEN c.business_date >= w.a_from AND c.business_date < w.a_to THEN N'a_2025整年'
                         WHEN c.business_date >= w.b_from AND c.business_date < w.b_to THEN N'b_2026至今' END AS win_label) x
CROSS APPLY (SELECT CASE WHEN c.source_type IN (0, 12) THEN N'DL工时'
                         WHEN c.source_type IN (8, 9) THEN N'DL奖金待定'
                         WHEN c.source_type IN (1, 2, 3, 4, 7) THEN N'ODC费用'
                         WHEN c.source_type = 5 THEN N'SUB委外'
                         WHEN c.source_type = 6 THEN N'INFO居间'
                         ELSE N'OTHER' END AS cost_class) cc
WHERE c.deleted = 0 AND c.entry_status = 0
  AND c.business_date >= w.a_from AND c.business_date < w.b_to
GROUP BY x.win_label, cc.cost_class, ROLLUP(d.parent_name, d.dept_name)
ORDER BY x.win_label, cc.cost_class, d.parent_name, d.dept_name;

-- =====================================================================================
-- Q23 成本台账的分录类型与有效性分布（公司合计）：判断 Q22 的过滤是否漏掉红冲/重分类
-- 依赖：psa_cost_ledger.entry_type（0 正常、1 金额红冲、3 条款重分类）、entry_status（0 有效、1 被重分类替代、2 被金额红冲）。
-- 不确定度：低（枚举来自 DDL 注释）。看 entry_type=1 行的 entry_status 与金额符号，决定 Q22 是否要把红冲行另加。
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

-- =====================================================================================
-- Q30 委外费预提明细（老系统委外单据表，与成本台账 source_type=5 对账）：（窗口 × 上级部门 × 交付部门）及合计
-- 依赖：psa_wip_outsourcing（WIPOutsourcingDO：dept_id, complete_date, amount, notax_amount, project_write_off_flag）；system_dept。
-- 口径：窗口按 complete_date（委外交付确认日）；dept_id 为交付部门，直接取，不经项目。amount 含税、notax_amount 不含税。
-- 不确定度：中。notax_amount 在 DO 里默认 0，存量行可能未回填（生产形态未核实）：notax_zero_cnt 报告其个数。
--       project_write_off_flag（0 未结转、1 已结转）把「已确认未结转」与「已结转」分开。
WITH w AS (
    SELECT CAST('20250101' AS date) AS a_from, CAST('20260101' AS date) AS a_to,
           CAST('20260101' AS date) AS b_from, DATEADD(DAY, 1, CAST(GETDATE() AS date)) AS b_to
),
dd AS (
    SELECT sd.id AS dept_id,
           ISNULL(sp.name, N'(无上级部门)') AS parent_name,
           ISNULL(sd.name, N'(无部门)') AS dept_name
    FROM dbo.system_dept sd
    LEFT JOIN dbo.system_dept sp ON sp.id = sd.parent_id
)
SELECT x.win_label,
       GROUPING_ID(dd.parent_name, dd.dept_name) AS lvl,
       dd.parent_name, dd.dept_name,
       COUNT_BIG(*) AS row_cnt,
       COUNT(DISTINCT o.project_id) AS project_cnt,
       SUM(ISNULL(o.amount, 0)) AS amount_gross,
       SUM(ISNULL(o.notax_amount, 0)) AS amount_notax,
       SUM(CASE WHEN ISNULL(o.notax_amount, 0) = 0 THEN 1 ELSE 0 END) AS notax_zero_cnt,
       SUM(CASE WHEN o.project_write_off_flag = 1 THEN ISNULL(o.notax_amount, 0) ELSE 0 END) AS notax_carried
FROM dbo.psa_wip_outsourcing o
CROSS JOIN w
LEFT JOIN dd ON dd.dept_id = o.dept_id
CROSS APPLY (SELECT CASE WHEN o.complete_date >= w.a_from AND o.complete_date < w.a_to THEN N'a_2025整年'
                         WHEN o.complete_date >= w.b_from AND o.complete_date < w.b_to THEN N'b_2026至今' END AS win_label) x
WHERE o.complete_date >= w.a_from AND o.complete_date < w.b_to
GROUP BY x.win_label, ROLLUP(dd.parent_name, dd.dept_name)
ORDER BY x.win_label, dd.parent_name, dd.dept_name;

-- =====================================================================================
-- Q31 居间费预提明细（对照：词表定为非转付成本，仅确认 Q20 INFO居间 类量级）：公司合计，按窗口
-- 依赖：psa_wip_commission（WIPCommissionDO：record_date, amount, notax_amount）。
-- 不确定度：中。record_date 为居间记录日期；是否与 source_type=6（信息服务）同一事实未核。
WITH w AS (
    SELECT CAST('20250101' AS date) AS a_from, CAST('20260101' AS date) AS a_to,
           CAST('20260101' AS date) AS b_from, DATEADD(DAY, 1, CAST(GETDATE() AS date)) AS b_to
)
SELECT x.win_label, COUNT_BIG(*) AS row_cnt,
       SUM(ISNULL(m.amount, 0)) AS amount_gross, SUM(ISNULL(m.notax_amount, 0)) AS amount_notax
FROM dbo.psa_wip_commission m
CROSS JOIN w
CROSS APPLY (SELECT CASE WHEN m.record_date >= w.a_from AND m.record_date < w.a_to THEN N'a_2025整年'
                         WHEN m.record_date >= w.b_from AND m.record_date < w.b_to THEN N'b_2026至今' END AS win_label) x
WHERE m.record_date >= w.a_from AND m.record_date < w.b_to
GROUP BY x.win_label
ORDER BY x.win_label;

-- =====================================================================================
-- Q40 费用类别分布（探测哪些类别可能是分包/外包/代垫）：（窗口 × 来源类型 × 费用类别），公司合计
-- 依赖：psa_cost_ledger.source_type / cost_type_code / cost_type_name（V20260720193300 加列）/ notax_amount / business_date。
-- 用途：Q20/Q22 里 ODC费用 的 3 付款申请、4 特殊付款能否拆出分包；1/2/7 里有没有「可向客户转收」的类别。
-- 不确定度：中。cost_type_name 是 OA 费用类别名，老快照可能为 NULL；类别名不含个人信息；不选 supplier_code、person_name。
WITH w AS (
    SELECT CAST('20250101' AS date) AS a_from, CAST('20260101' AS date) AS a_to,
           CAST('20260101' AS date) AS b_from, DATEADD(DAY, 1, CAST(GETDATE() AS date)) AS b_to
)
SELECT x.win_label, c.source_type, c.cost_type_code, c.cost_type_name,
       COUNT_BIG(*) AS row_cnt, COUNT(DISTINCT c.project_id) AS project_cnt, SUM(c.notax_amount) AS notax_sum
FROM dbo.psa_cost_ledger c
CROSS JOIN w
CROSS APPLY (SELECT CASE WHEN c.business_date >= w.a_from AND c.business_date < w.a_to THEN N'a_2025整年'
                         WHEN c.business_date >= w.b_from AND c.business_date < w.b_to THEN N'b_2026至今' END AS win_label) x
WHERE c.deleted = 0 AND c.entry_status = 0
  AND c.business_date >= w.a_from AND c.business_date < w.b_to
GROUP BY x.win_label, c.source_type, c.cost_type_code, c.cost_type_name
ORDER BY x.win_label, c.source_type, SUM(c.notax_amount) DESC;

-- =====================================================================================
-- Q41 「代垫/可转收」关键词探测（只出个数与金额）：成本台账费用类别名里有无 代垫、垫付、垫资、代付、代收、转收、实报实销、分包、外包、委外、劳务
-- 依赖：psa_cost_ledger.cost_type_name / notax_amount / business_date。
-- 用途：判断代垫费用能否在老系统里识别。老系统源码与文档里没有「可向客户转收」标识（见 M29_BASELINE_MAPPING.md），本条是唯一的名称探测。
-- 不确定度：高（关键词法：命中少不代表不存在，命中多也不代表可转收）。关键词表是提议，运维可补充。
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

-- =====================================================================================
-- Q42 合同条款名称里的「代垫/实报实销」关键词（合同层可转收线索）：只出个数
-- 依赖：psa_pm_contract_term.name / deleted（ProfitReportMapper.listClauseTitles 用到 t.name、t.deleted）。
-- 用途：若合同条款有「代垫费」「实报实销」类条款，则可转收费用至少能在合同层识别；不取条款名原文，只出命中个数。
-- 不确定度：高（关键词法）。
SELECT k.kw, COUNT_BIG(*) AS term_cnt
FROM dbo.psa_pm_contract_term t
JOIN (VALUES (N'代垫'), (N'垫付'), (N'实报实销'), (N'差旅'), (N'据实结算'), (N'据实报销')) AS k(kw)
  ON t.name LIKE N'%' + k.kw + N'%'
WHERE t.deleted = 0
GROUP BY k.kw
ORDER BY k.kw;

-- =====================================================================================
-- Q43 列名探测：业务库里与「可转收/代垫/计费」相关的列（只取元数据，不取数据）
-- 依赖：INFORMATION_SCHEMA.COLUMNS。
-- 用途：Q41/Q42 没命中时，看有没有专门的标识列（如 reimbursable、billable、advance、rebill）。
-- 不确定度：高（列名猜测）。
SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = N'dbo'
  AND (TABLE_NAME LIKE N'psa[_]cost[_]%' OR TABLE_NAME LIKE N'psa[_]pm[_]%' OR TABLE_NAME LIKE N'psa[_]wip[_]%'
       OR TABLE_NAME = N'psa_opportunity_expense' OR TABLE_NAME LIKE N'psa[_]term[_]%')
  AND (COLUMN_NAME LIKE N'%reimb%' OR COLUMN_NAME LIKE N'%advance%' OR COLUMN_NAME LIKE N'%rebill%'
       OR COLUMN_NAME LIKE N'%billable%' OR COLUMN_NAME LIKE N'%pass%' OR COLUMN_NAME LIKE N'%charge%'
       OR COLUMN_NAME LIKE N'%customer_bear%' OR COLUMN_NAME LIKE N'%client%')
ORDER BY TABLE_NAME, COLUMN_NAME;
