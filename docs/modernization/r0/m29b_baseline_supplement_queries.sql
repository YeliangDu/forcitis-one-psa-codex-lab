-- M-29 基线补充（第二批）：奖金归期、据实结算合同抽样、年末未结转与跨年项目——只读聚合查询（草稿，未执行）
-- 起草：2026-10-22。接续 r0/m29_baseline_readonly_queries.sql（Q00～Q43）；本批编号 B01 起，不与 Q 号冲突。
-- 口径说明与不确定处见同目录 m29b_notes.md。老系统源码依据：psa-backend/yudao-module-fst（PlLedgerDO、CostLedgerDO、WipEntryDO、WipMovementDO、
--   AnnualPeriodResolver、AnnualBonusDetailViewDO、MonthlyBonusAccrualViewDO、WipPeriodSnapshotMapper、ContractTermMapper、ProjectDO、CuValueDO）。
--
-- 【访问纪律】沿用 Q 批文件头第 1～6 条，原样适用：
--   账号 one_evidence_ro、库 ruoyi-vue-pro；只含 SELECT 与 WITH；不写 SET/DECLARE/临时表；不用 NOLOCK 或任何表提示；
--   窗口日期写在每条查询开头的 WITH w 里；超时由运维在连接参数设（锁等待 30 秒、语句 150 秒），超时即停止并报告；
--   一次只跑一条，低峰执行；只出聚合；分组不细于「区域/BU」（交付部门及其上级部门）；
--   不选项目编码/名称、客户、合同号/条款号/条款名原文、单据号、人员、供应商、备注。每次取证登记一行。
--   人员个数、源单个数只以 COUNT(DISTINCT) 出数，不出明细。
-- 【执行顺序】B01、B02 先跑（只查元数据）；缺列的查询先不跑，回报缺哪列。B11、B12 只在 B02 显示视图存在时才跑。
-- 【单位】金额为元。默认窗口 2025 整年；期间年取 mdm_accounting_period.period_year（已知该表是年度期间，不是月）。
-- 【区域/BU】沿 Q 批：psa_pm_project.dept_id → system_dept（本级 name，上级 parent_id 对应 name）。lvl = GROUPING_ID(parent_name, dept_name)：
--   0 交付部门行；1 上级部门小计；3 公司合计。
-- 【source_type】0 工时、1 日常、2 差旅、3 付款申请、4 特殊付款、5 委外、6 信息服务、7 招待、8 奖金计提、9 年度奖金、12 人月尾差调整
--   （CarryForwardDataTypeEnum）。pl_type：0 主营成本、1 资产减值、2 营业外、9 红冲冲减。

-- #####################################################################################
-- 组 0 元数据核对
-- #####################################################################################

-- =====================================================================================
-- B01 列名核对：本批用到而 Q01 未核对的表与列（只取元数据）
-- 依据：Flyway 迁移与 DO 类；psa_project / system_dept 等基础表不在仓库 DDL 内，列名来自 DO。
-- 不确定度：低。缺列的查询先不跑。
SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = N'dbo'
  AND (   (TABLE_NAME = N'psa_pl_ledger'     AND COLUMN_NAME IN (N'source_cost_ledger_id', N'source_id', N'source_code', N'trigger_type', N'pl_type', N'source_type', N'business_date', N'period_id', N'notax_amount', N'amount', N'project_id', N'deleted', N'reversal_of_id'))
       OR (TABLE_NAME = N'psa_cost_ledger'   AND COLUMN_NAME IN (N'id', N'source_id', N'source_code', N'source_type', N'entry_type', N'entry_status', N'fee_type', N'person_id', N'term_number', N'term_name_snap', N'approval_hours', N'business_date', N'period_id', N'notax_amount', N'project_id', N'deleted'))
       OR (TABLE_NAME = N'psa_oh_cost_pool'  AND COLUMN_NAME IN (N'source_type', N'business_date', N'period_id', N'notax_amount', N'alloc_status', N'pool_line_type', N'deleted'))
       OR (TABLE_NAME = N'psa_wip_entry'     AND COLUMN_NAME IN (N'cost_ledger_id', N'project_id', N'wip_stage', N'wip_status', N'notax_amount', N'entered_period_id', N'written_off_at', N'terminated_at', N'deleted'))
       OR (TABLE_NAME = N'psa_wip_movement'  AND COLUMN_NAME IN (N'wip_entry_id', N'cost_ledger_id', N'project_id', N'movement_type', N'notax_amount', N'period_id', N'create_time', N'deleted'))
       OR (TABLE_NAME = N'psa_wip_period_balance' AND COLUMN_NAME IN (N'period_id', N'company_code', N'wip_stage', N'wip_status', N'balance', N'snapshot_at', N'deleted'))
       OR (TABLE_NAME = N'psa_pm_project'    AND COLUMN_NAME IN (N'initiation_date', N'status', N'region'))
       OR (TABLE_NAME = N'psa_pm_contract'   AND COLUMN_NAME IN (N'id', N'project_id', N'back_date', N'deleted'))
       OR (TABLE_NAME = N'psa_pm_contract_term' AND COLUMN_NAME IN (N'contract_id', N'code', N'name', N'amount', N'final_amount', N'deleted'))
       OR (TABLE_NAME = N'psa_pm_cuvalue'    AND COLUMN_NAME IN (N'term_number', N'term_depict'))
       OR (TABLE_NAME = N'psa_contract_clause' AND COLUMN_NAME IN (N'project_id', N'term_code', N'deleted'))
       OR (TABLE_NAME = N'psa_contract_term_poc_snapshot' AND COLUMN_NAME IN (N'term_code', N'period', N'poc', N'deleted'))
       OR (TABLE_NAME = N'mdm_accounting_period' AND COLUMN_NAME IN (N'period_sequence', N'closed_time', N'company_code')))
ORDER BY TABLE_NAME, COLUMN_NAME;

-- =====================================================================================
-- B02 奖金源视图是否在本库可见：annual_bonus_detail、monthly_bonus_accrual（只取对象名与列名）
-- 依据：两个视图是 OA 侧建的、非 Flyway（AnnualBonusDetailViewDO / MonthlyBonusAccrualViewDO 注释）；是否在 ruoyi-vue-pro 库内可见未核实。
-- 用途：决定 B11、B12 能不能跑。不含 level 列（物理列名带不可见字符），其余列照出。
-- 不确定度：中。可能在别的库或别的 schema（本条不限 schema，把 schema 带出来）。
SELECT c.TABLE_SCHEMA, c.TABLE_NAME, c.COLUMN_NAME, c.DATA_TYPE
FROM INFORMATION_SCHEMA.COLUMNS c
WHERE c.TABLE_NAME IN (N'annual_bonus_detail', N'monthly_bonus_accrual')
ORDER BY c.TABLE_NAME, c.ORDINAL_POSITION;

-- #####################################################################################
-- 组 A 奖金归期：能否把挂项目的奖金按期归到所属月份
-- 源码结论（写在查询前，供对照）：
--   · 年度奖金（source_type 9）：来自 OA 视图 annual_bonus_detail，business_date 恒为该年 12-31（AnnualPeriodResolver 注释、
--     AnnualBonusDetailViewDO 注释）；视图里没有「所属月份」列，只有 business_date；fee_type=0 的行按「人 × 当年全年批准工时」在
--     项目×条款间拆条（AnnualBonusBasis，基数按年聚合，不分月）→ 写 psa_cost_ledger。2026 年前的年终奖随该年正常期间归集，2026 起走 13 期（period_type=2）。
--   · 奖金计提（source_type 8）：来自 OA 视图 monthly_bonus_accrual，business_date 定归属期间；DO 注释写明 fee_type 应为 1/2（销售/管理 OH），
--     fee_type=0 视为异常（CostRoutingService）；故计提正常走 psa_oh_cost_pool，不进挂项目的 psa_cost_ledger / psa_pl_ledger。已知 psa_pl_ledger 无 source_type 8，与此一致。
--   · psa_pl_ledger.business_date = 承接成本台账行的 business_date（PlLedgerDO.forWriteOff）；period_id = 转出（结转）所在期间，
--     不是成本发生期间；红冲行 period_id = 红冲所在期间。
--   · psa_cost_ledger、psa_pl_ledger 均没有「所属月份」「所属年度」列；psa_cost_ledger 有 source_code（奖金单号）、source_id、person_id，但查询只出计数。
-- #####################################################################################

-- =====================================================================================
-- B03 奖金行总览：psa_pl_ledger 里 source_type 8、9 的行，按（期间年 × 期间类型 × 来源类型 × 损益类型 × 触发类型）
-- 依赖：psa_pl_ledger（source_type, pl_type, trigger_type, period_id, business_date, notax_amount, project_id, deleted）；mdm_accounting_period（period_year, period_type）。
-- 用途：全年份看奖金行进损益的期间分布；确认 8 是否真无行；红冲行（pl_type 9）落在哪个期间。
-- 不确定度：低。LEFT JOIN 期间表，period_year 为 NULL 表示 period_id 对不上期间。
SELECT ap.period_year, ap.period_type, l.source_type, l.pl_type, l.trigger_type,
       COUNT_BIG(*) AS row_cnt, COUNT(DISTINCT l.project_id) AS project_cnt,
       MIN(l.business_date) AS min_biz_date, MAX(l.business_date) AS max_biz_date,
       SUM(l.notax_amount) AS notax_sum
FROM dbo.psa_pl_ledger l
LEFT JOIN dbo.mdm_accounting_period ap ON ap.id = l.period_id
WHERE l.deleted = 0 AND l.source_type IN (8, 9)
GROUP BY ap.period_year, ap.period_type, l.source_type, l.pl_type, l.trigger_type
ORDER BY ap.period_year, ap.period_type, l.source_type, l.pl_type, l.trigger_type;

-- =====================================================================================
-- B04 2025 年奖金行按 business_date 的月份分布（公司合计）：psa_pl_ledger
-- 依赖：同 B03。
-- 口径：取「期间年 = 2025」或「business_date 年 = 2025」的奖金行（两者可能不同），分组带出期间年，月份取 MONTH(business_date)。
-- 判读：若 distinct_days 恒为 1 且 biz_month 恒为 12，则 business_date 无月份信息，按月归期不可能。
-- 不确定度：低。金额含红冲负数（pl_type 9）；pl_type 分列。
WITH w AS (SELECT 2025 AS yr)
SELECT l.source_type, l.pl_type, ap.period_year,
       YEAR(l.business_date) AS biz_year, MONTH(l.business_date) AS biz_month,
       COUNT_BIG(*) AS row_cnt, COUNT(DISTINCT l.business_date) AS distinct_days,
       MIN(l.business_date) AS min_biz_date, MAX(l.business_date) AS max_biz_date,
       SUM(l.notax_amount) AS notax_sum, SUM(l.amount) AS gross_sum
FROM dbo.psa_pl_ledger l
LEFT JOIN dbo.mdm_accounting_period ap ON ap.id = l.period_id
CROSS JOIN w
WHERE l.deleted = 0 AND l.source_type IN (8, 9)
  AND (ap.period_year = w.yr OR YEAR(l.business_date) = w.yr)
GROUP BY l.source_type, l.pl_type, ap.period_year, YEAR(l.business_date), MONTH(l.business_date)
ORDER BY l.source_type, l.pl_type, ap.period_year, YEAR(l.business_date), MONTH(l.business_date);

-- =====================================================================================
-- B05 business_date 年份与期间年份是否一致：交叉表（psa_pl_ledger，全年份）
-- 依赖：同 B03。
-- 口径：year_gap = 期间年 − business_date 年；0 一致，>0 表示进损益期间晚于成本发生年（跨年结转），<0 表示早于（不应出现）。
-- 不确定度：低。
SELECT l.source_type, l.pl_type,
       YEAR(l.business_date) AS biz_year, ap.period_year, ap.period_type,
       ap.period_year - YEAR(l.business_date) AS year_gap,
       COUNT_BIG(*) AS row_cnt, SUM(l.notax_amount) AS notax_sum
FROM dbo.psa_pl_ledger l
LEFT JOIN dbo.mdm_accounting_period ap ON ap.id = l.period_id
WHERE l.deleted = 0 AND l.source_type IN (8, 9)
GROUP BY l.source_type, l.pl_type, YEAR(l.business_date), ap.period_year, ap.period_type
ORDER BY l.source_type, l.pl_type, YEAR(l.business_date), ap.period_year, ap.period_type;

-- =====================================================================================
-- B06 落在 2025 期间、business_date 早于 2025-01-01 的奖金行（psa_pl_ledger）
-- 依赖：同 B03。
-- 判读：返回 0 行表示不存在。有行则看 biz_year（上一年的奖金在 2025 期间结转进损益，即跨年错配）。
-- 不确定度：低。
WITH w AS (SELECT 2025 AS yr, CAST('20250101' AS date) AS y_from)
SELECT l.source_type, l.pl_type, YEAR(l.business_date) AS biz_year,
       COUNT_BIG(*) AS row_cnt, COUNT(DISTINCT l.project_id) AS project_cnt,
       COUNT(DISTINCT l.business_date) AS distinct_days,
       MIN(l.business_date) AS min_biz_date, MAX(l.business_date) AS max_biz_date,
       SUM(l.notax_amount) AS notax_sum
FROM dbo.psa_pl_ledger l
JOIN dbo.mdm_accounting_period ap ON ap.id = l.period_id
CROSS JOIN w
WHERE l.deleted = 0 AND l.source_type IN (8, 9)
  AND ap.period_year = w.yr AND l.business_date < w.y_from
GROUP BY l.source_type, l.pl_type, YEAR(l.business_date)
ORDER BY l.source_type, l.pl_type, YEAR(l.business_date);

-- =====================================================================================
-- B07 成本台账里的奖金行：business_date 月份 × 台账归属期间（psa_cost_ledger，公司合计）
-- 依赖：psa_cost_ledger（source_type, entry_type, entry_status, business_date, period_id, notax_amount, deleted）；mdm_accounting_period。
-- 口径：期间年 = 2025 或 business_date 年 = 2025；不限 entry_status（带出以便看被替代/红冲行）。
-- 用途：和 B04 对照，看成本发生侧（入池时点）是否同样只有 12-31 一个日期。
-- 不确定度：低。period_type=2 为 13 期（年末调整期，2026 起承载年终奖）。
WITH w AS (SELECT 2025 AS yr)
SELECT l.source_type, l.entry_type, l.entry_status, cap.period_year, cap.period_type,
       YEAR(l.business_date) AS biz_year, MONTH(l.business_date) AS biz_month,
       COUNT_BIG(*) AS row_cnt, COUNT(DISTINCT l.business_date) AS distinct_days,
       SUM(l.notax_amount) AS notax_sum
FROM dbo.psa_cost_ledger l
LEFT JOIN dbo.mdm_accounting_period cap ON cap.id = l.period_id
CROSS JOIN w
WHERE l.deleted = 0 AND l.source_type IN (8, 9)
  AND (cap.period_year = w.yr OR YEAR(l.business_date) = w.yr)
GROUP BY l.source_type, l.entry_type, l.entry_status, cap.period_year, cap.period_type, YEAR(l.business_date), MONTH(l.business_date)
ORDER BY l.source_type, l.entry_type, l.entry_status, cap.period_year, YEAR(l.business_date), MONTH(l.business_date);

-- =====================================================================================
-- B08 成本台账期间 → 损益期间的结转滞后矩阵（奖金行）
-- 依赖：psa_pl_ledger.source_cost_ledger_id → psa_cost_ledger.id；两端 period_id → mdm_accounting_period.period_year。
-- 口径：pl 行（含红冲）关联其来源成本行；pl 期间年或成本期间年为 2025 的行。
-- 用途：回答「期间错配在哪」：成本在哪个期间入池（cost_period_year）、在哪个期间进损益（pl_period_year）、business_date 年份是多少；
--       biz_date_diff_cnt 校验 pl.business_date 是否等于成本行 business_date（源码承接，应为 0）。
-- 不确定度：中。source_cost_ledger_id 可空（期间费用直进损益时），cost_link_missing_cnt 报告个数；红冲行的来源成本行与正向行同一条。
WITH w AS (SELECT 2025 AS yr)
SELECT l.source_type, l.pl_type,
       cap.period_year AS cost_period_year, pap.period_year AS pl_period_year,
       YEAR(l.business_date) AS pl_biz_year,
       COUNT_BIG(*) AS row_cnt,
       SUM(CASE WHEN c.id IS NULL THEN 1 ELSE 0 END) AS cost_link_missing_cnt,
       SUM(CASE WHEN c.id IS NOT NULL AND ISNULL(c.business_date, '19000101') <> ISNULL(l.business_date, '19000101') THEN 1 ELSE 0 END) AS biz_date_diff_cnt,
       SUM(l.notax_amount) AS pl_notax_sum
FROM dbo.psa_pl_ledger l
LEFT JOIN dbo.psa_cost_ledger c ON c.id = l.source_cost_ledger_id
LEFT JOIN dbo.mdm_accounting_period cap ON cap.id = c.period_id
LEFT JOIN dbo.mdm_accounting_period pap ON pap.id = l.period_id
CROSS JOIN w
WHERE l.deleted = 0 AND l.source_type IN (8, 9)
  AND (pap.period_year = w.yr OR cap.period_year = w.yr)
GROUP BY l.source_type, l.pl_type, cap.period_year, pap.period_year, YEAR(l.business_date)
ORDER BY l.source_type, l.pl_type, cap.period_year, pap.period_year, YEAR(l.business_date);

-- =====================================================================================
-- B09 奖金成本台账行的结构与源单列覆盖（只出计数）
-- 依赖：psa_cost_ledger（source_type, fee_type, entry_type, entry_status, source_id, source_code, person_id, term_number, approval_hours, project_id, business_date）。
-- 用途：(1) rows 对 src_cnt 的倍数 = 一张奖金单被拆成几条项目×条款行；(2) source_code / person_id 非空率 = 能否回到奖金源单、再到源单上的月份信息
--       （月份信息只可能在 OA 源视图，不在本表）；(3) fee_type 分布确认挂项目行只有 0。
-- 不确定度：低。人员只出个数（COUNT DISTINCT person_id），不出 id。
SELECT YEAR(l.business_date) AS biz_year, l.source_type, l.fee_type, l.entry_type, l.entry_status,
       COUNT_BIG(*) AS row_cnt,
       COUNT(DISTINCT l.source_id) AS source_cnt,
       COUNT(DISTINCT l.person_id) AS person_cnt,
       COUNT(DISTINCT l.project_id) AS project_cnt,
       SUM(CASE WHEN l.source_code IS NULL THEN 1 ELSE 0 END) AS source_code_null_cnt,
       SUM(CASE WHEN l.person_id IS NULL THEN 1 ELSE 0 END) AS person_null_cnt,
       SUM(CASE WHEN l.term_number IS NULL THEN 1 ELSE 0 END) AS term_null_cnt,
       SUM(CASE WHEN l.approval_hours IS NULL THEN 1 ELSE 0 END) AS hours_null_cnt,
       SUM(l.notax_amount) AS notax_sum
FROM dbo.psa_cost_ledger l
WHERE l.deleted = 0 AND l.source_type IN (8, 9)
GROUP BY YEAR(l.business_date), l.source_type, l.fee_type, l.entry_type, l.entry_status
ORDER BY YEAR(l.business_date), l.source_type, l.fee_type, l.entry_type, l.entry_status;

-- =====================================================================================
-- B10 OH 池里的奖金（计提 8 / 年终奖 9 的非项目部分）：business_date 月份 × 池期间（公司合计）
-- 依赖：psa_oh_cost_pool（source_type, business_date, period_id, notax_amount, alloc_status, pool_line_type, deleted）。
-- 用途：奖金计提 8 正常走 OH 池；看它的 business_date 是否按月分布（若按月，说明计提侧本有月份信息，只是不挂项目）。
--       pool_line_type：0 正常、1 冲销镜像（V20260818104603）。
-- 不确定度：中。pool_line_type 列若 B01 显示不存在，把它从 SELECT 和 GROUP BY 去掉。
WITH w AS (SELECT 2025 AS yr)
SELECT p.source_type, p.pool_line_type, p.alloc_status, ap.period_year, ap.period_type,
       YEAR(p.business_date) AS biz_year, MONTH(p.business_date) AS biz_month,
       COUNT_BIG(*) AS row_cnt, COUNT(DISTINCT p.business_date) AS distinct_days,
       SUM(p.notax_amount) AS notax_sum
FROM dbo.psa_oh_cost_pool p
LEFT JOIN dbo.mdm_accounting_period ap ON ap.id = p.period_id
CROSS JOIN w
WHERE p.deleted = 0 AND p.source_type IN (8, 9)
  AND (ap.period_year = w.yr OR YEAR(p.business_date) = w.yr)
GROUP BY p.source_type, p.pool_line_type, p.alloc_status, ap.period_year, ap.period_type, YEAR(p.business_date), MONTH(p.business_date)
ORDER BY p.source_type, p.pool_line_type, p.alloc_status, ap.period_year, YEAR(p.business_date), MONTH(p.business_date);

-- =====================================================================================
-- B11 【仅当 B02 显示 dbo.annual_bonus_detail 存在才跑】年终奖源视图：business_date 年月分布（公司合计）
-- 依赖：annual_bonus_detail（business_date, process_type, fee_type, notax_amount, pay_amount, id）——列名取自 AnnualBonusDetailViewDO。
-- 判读：源视图 business_date 是否恒为 12-31；若有别的日期，说明源单有月份信息可用。fee_type：0 项目直接成本 / 1 销售 OH / 2 管理 OH。
-- 不确定度：中。视图在 OA 侧建，列名以 B02 为准；视图可能有软删或状态过滤条件，此处按源码读端的唯一谓词 process_type = '6' 过滤。
SELECT YEAR(b.business_date) AS biz_year, MONTH(b.business_date) AS biz_month, b.fee_type,
       COUNT_BIG(*) AS row_cnt, COUNT(DISTINCT b.business_date) AS distinct_days,
       MIN(b.business_date) AS min_biz_date, MAX(b.business_date) AS max_biz_date,
       SUM(b.notax_amount) AS notax_sum, SUM(b.pay_amount) AS gross_sum
FROM dbo.annual_bonus_detail b
WHERE b.process_type = '6'
GROUP BY YEAR(b.business_date), MONTH(b.business_date), b.fee_type
ORDER BY YEAR(b.business_date), MONTH(b.business_date), b.fee_type;

-- =====================================================================================
-- B12 【仅当 B02 显示 dbo.monthly_bonus_accrual 存在才跑】奖金计提源视图：business_date 年月分布与 fee_type（公司合计）
-- 依赖：monthly_bonus_accrual（business_date, process_type, fee_type, notax_amount）——列名取自 MonthlyBonusAccrualViewDO；
--       DO 注释称视图另有 pay_date，B02 若列出 pay_date，可另加一条按 pay_date 月份分布（本条不用，因未映射、未核实）。
-- 判读：fee_type=0 的行数（应为异常）；计提的 business_date 是否逐月。
-- 不确定度：中。同 B11。
SELECT YEAR(b.business_date) AS biz_year, MONTH(b.business_date) AS biz_month, b.fee_type,
       COUNT_BIG(*) AS row_cnt, COUNT(DISTINCT b.business_date) AS distinct_days,
       SUM(b.notax_amount) AS notax_sum
FROM dbo.monthly_bonus_accrual b
WHERE b.process_type = '7'
GROUP BY YEAR(b.business_date), MONTH(b.business_date), b.fee_type
ORDER BY YEAR(b.business_date), MONTH(b.business_date), b.fee_type;

-- #####################################################################################
-- 组 B 据实结算合同抽样：合同条款 → 合同 → 项目
-- 关联路径（源码 ContractTermMapper.selectAllProjectContractTerms）：psa_pm_contract_term.contract_id → psa_pm_contract.id；
--   psa_pm_contract.project_id → psa_pm_project.id。条款名在 psa_pm_contract_term.name。
-- 项目级口径：整个项目的 2025 年收入与费用，不只是该条款的（一个项目可有多条款）；条款级备选见 B16、B17。
-- #####################################################################################

-- =====================================================================================
-- B13 条款名关键词命中：条款数、合同数、项目数（含软删与否分列）
-- 依赖：psa_pm_contract_term（name, contract_id, code, amount, final_amount, deleted）；psa_pm_contract（id, project_id, deleted）。
-- 用途：核对此前「4 条」的数；看放宽关键词（据实、实报实销…）是否还有；看有无合同缺失或项目缺失。条款名原文、合同号不出。
-- 不确定度：中。amount / final_amount 的含义取自 DO 注释（条款金额、最终金额），金额只作聚合参考。
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

-- =====================================================================================
-- B14 据实结算项目 2025 年收入与直接费用（进损益口径）vs 公司全部项目
-- 依赖：psa_pm_contract_term.name / contract_id / deleted；psa_pm_contract.project_id / deleted；
--       psa_pm_cuvalue（project_id, complete_date, no_tax_amount, type）；psa_pl_ledger（project_id, source_type, pl_type, notax_amount, period_id, deleted）；mdm_accounting_period.period_year。
-- 口径：据实结算项目集合 = 有未删条款名含「据实结算」的未删合同所属项目（去重）。
--       收入 = type IN (0,1)、确认日在 2025（同 Q10）；成本 = 期间年 2025、pl_type IN (0,9)（同 Q20），按 source_type 分列：
--       ODC 日常 1、差旅 2、付款申请 3、特殊付款 4、招待 7；委外 5；居间 6；工时 0、12；奖金 9。比例列便于与公司全部项目对比。
--       a 行只含 2025 年有收入或有进损益成本的据实结算项目（project_cnt）；sel_project_cnt_all 为据实结算项目总数（含 2025 年无活动者）。
-- 不确定度：中。(1) 项目内非据实结算条款的收入与费用也在内，会稀释比例；(2) 项目数可能很小，结果可能能反推到具体项目，仅限内部使用，不外传；
--       (3) no_tax_amount 为空按 0（同 Q10）；(4) 公司全部项目行的项目含 project_id 对不上项目档案的行。
WITH w AS (SELECT 2025 AS yr, CAST('20250101' AS date) AS y_from, CAST('20260101' AS date) AS y_to),
sel AS (
    SELECT DISTINCT c.project_id
    FROM dbo.psa_pm_contract_term t
    JOIN dbo.psa_pm_contract c ON c.id = t.contract_id AND c.deleted = 0
    WHERE t.deleted = 0 AND t.name LIKE N'%据实结算%' AND c.project_id IS NOT NULL
),
rev AS (
    SELECT cu.project_id, SUM(ISNULL(cu.no_tax_amount, 0)) AS rev_notax
    FROM dbo.psa_pm_cuvalue cu CROSS JOIN w
    WHERE cu.[type] IN (0, 1) AND cu.complete_date >= w.y_from AND cu.complete_date < w.y_to
    GROUP BY cu.project_id
),
pl AS (
    SELECT l.project_id,
           SUM(CASE WHEN l.source_type = 1 THEN l.notax_amount ELSE 0 END) AS odc_daily,
           SUM(CASE WHEN l.source_type = 2 THEN l.notax_amount ELSE 0 END) AS odc_travel,
           SUM(CASE WHEN l.source_type = 3 THEN l.notax_amount ELSE 0 END) AS odc_payreq,
           SUM(CASE WHEN l.source_type = 4 THEN l.notax_amount ELSE 0 END) AS odc_special,
           SUM(CASE WHEN l.source_type = 7 THEN l.notax_amount ELSE 0 END) AS odc_hospitality,
           SUM(CASE WHEN l.source_type = 5 THEN l.notax_amount ELSE 0 END) AS outsourcing,
           SUM(CASE WHEN l.source_type = 6 THEN l.notax_amount ELSE 0 END) AS info_service,
           SUM(CASE WHEN l.source_type IN (0, 12) THEN l.notax_amount ELSE 0 END) AS dl_hours,
           SUM(CASE WHEN l.source_type = 9 THEN l.notax_amount ELSE 0 END) AS dl_bonus
    FROM dbo.psa_pl_ledger l
    JOIN dbo.mdm_accounting_period ap ON ap.id = l.period_id
    CROSS JOIN w
    WHERE l.deleted = 0 AND l.pl_type IN (0, 9) AND ap.period_year = w.yr
    GROUP BY l.project_id
),
allp AS (SELECT project_id FROM rev UNION SELECT project_id FROM pl),
grp AS (
    SELECT N'a_据实结算项目' AS scope, a.project_id FROM allp a JOIN sel ON sel.project_id = a.project_id
    UNION ALL
    SELECT N'b_公司全部项目', a.project_id FROM allp a
)
SELECT g.scope,
       (SELECT COUNT_BIG(*) FROM sel) AS sel_project_cnt_all,
       COUNT_BIG(*) AS project_cnt,
       SUM(CASE WHEN r.project_id IS NOT NULL THEN 1 ELSE 0 END) AS project_with_rev_cnt,
       SUM(ISNULL(r.rev_notax, 0)) AS rev_notax,
       SUM(ISNULL(p.odc_daily, 0)) AS odc_daily, SUM(ISNULL(p.odc_travel, 0)) AS odc_travel,
       SUM(ISNULL(p.odc_payreq, 0)) AS odc_payreq, SUM(ISNULL(p.odc_special, 0)) AS odc_special,
       SUM(ISNULL(p.odc_hospitality, 0)) AS odc_hospitality,
       SUM(ISNULL(p.odc_daily, 0) + ISNULL(p.odc_travel, 0) + ISNULL(p.odc_payreq, 0) + ISNULL(p.odc_special, 0) + ISNULL(p.odc_hospitality, 0)) AS odc_total,
       SUM(ISNULL(p.outsourcing, 0)) AS outsourcing, SUM(ISNULL(p.info_service, 0)) AS info_service,
       SUM(ISNULL(p.dl_hours, 0)) AS dl_hours, SUM(ISNULL(p.dl_bonus, 0)) AS dl_bonus,
       SUM(ISNULL(p.odc_daily, 0) + ISNULL(p.odc_travel, 0) + ISNULL(p.odc_payreq, 0) + ISNULL(p.odc_special, 0) + ISNULL(p.odc_hospitality, 0))
         / NULLIF(SUM(ISNULL(r.rev_notax, 0)), 0) AS odc_to_rev_ratio,
       SUM(ISNULL(p.outsourcing, 0)) / NULLIF(SUM(ISNULL(r.rev_notax, 0)), 0) AS outsourcing_to_rev_ratio
FROM grp g
LEFT JOIN rev r ON r.project_id = g.project_id
LEFT JOIN pl p ON p.project_id = g.project_id
GROUP BY g.scope
ORDER BY g.scope;

-- =====================================================================================
-- B15 同 B14，费用改取发生口径（成本台账）
-- 依赖：同 B14，但费用取 psa_cost_ledger（project_id, source_type, notax_amount, business_date, entry_status, deleted）。
-- 口径：entry_status = 0、business_date 在 2025；收入同 B14。
-- 不确定度：中。成本台账发生口径与进损益口径差异大（见 M29_BASELINE_RESULT.md），两条并看。
WITH w AS (SELECT CAST('20250101' AS date) AS y_from, CAST('20260101' AS date) AS y_to),
sel AS (
    SELECT DISTINCT c.project_id
    FROM dbo.psa_pm_contract_term t
    JOIN dbo.psa_pm_contract c ON c.id = t.contract_id AND c.deleted = 0
    WHERE t.deleted = 0 AND t.name LIKE N'%据实结算%' AND c.project_id IS NOT NULL
),
rev AS (
    SELECT cu.project_id, SUM(ISNULL(cu.no_tax_amount, 0)) AS rev_notax
    FROM dbo.psa_pm_cuvalue cu CROSS JOIN w
    WHERE cu.[type] IN (0, 1) AND cu.complete_date >= w.y_from AND cu.complete_date < w.y_to
    GROUP BY cu.project_id
),
cl AS (
    SELECT l.project_id,
           SUM(CASE WHEN l.source_type = 1 THEN l.notax_amount ELSE 0 END) AS odc_daily,
           SUM(CASE WHEN l.source_type = 2 THEN l.notax_amount ELSE 0 END) AS odc_travel,
           SUM(CASE WHEN l.source_type = 3 THEN l.notax_amount ELSE 0 END) AS odc_payreq,
           SUM(CASE WHEN l.source_type = 4 THEN l.notax_amount ELSE 0 END) AS odc_special,
           SUM(CASE WHEN l.source_type = 7 THEN l.notax_amount ELSE 0 END) AS odc_hospitality,
           SUM(CASE WHEN l.source_type = 5 THEN l.notax_amount ELSE 0 END) AS outsourcing,
           SUM(CASE WHEN l.source_type = 6 THEN l.notax_amount ELSE 0 END) AS info_service,
           SUM(CASE WHEN l.source_type IN (0, 12) THEN l.notax_amount ELSE 0 END) AS dl_hours,
           SUM(CASE WHEN l.source_type = 9 THEN l.notax_amount ELSE 0 END) AS dl_bonus
    FROM dbo.psa_cost_ledger l CROSS JOIN w
    WHERE l.deleted = 0 AND l.entry_status = 0 AND l.business_date >= w.y_from AND l.business_date < w.y_to
    GROUP BY l.project_id
),
allp AS (SELECT project_id FROM rev UNION SELECT project_id FROM cl),
grp AS (
    SELECT N'a_据实结算项目' AS scope, a.project_id FROM allp a JOIN sel ON sel.project_id = a.project_id
    UNION ALL
    SELECT N'b_公司全部项目', a.project_id FROM allp a
)
SELECT g.scope,
       (SELECT COUNT_BIG(*) FROM sel) AS sel_project_cnt_all,
       COUNT_BIG(*) AS project_cnt,
       SUM(CASE WHEN r.project_id IS NOT NULL THEN 1 ELSE 0 END) AS project_with_rev_cnt,
       SUM(ISNULL(r.rev_notax, 0)) AS rev_notax,
       SUM(ISNULL(p.odc_daily, 0)) AS odc_daily, SUM(ISNULL(p.odc_travel, 0)) AS odc_travel,
       SUM(ISNULL(p.odc_payreq, 0)) AS odc_payreq, SUM(ISNULL(p.odc_special, 0)) AS odc_special,
       SUM(ISNULL(p.odc_hospitality, 0)) AS odc_hospitality,
       SUM(ISNULL(p.odc_daily, 0) + ISNULL(p.odc_travel, 0) + ISNULL(p.odc_payreq, 0) + ISNULL(p.odc_special, 0) + ISNULL(p.odc_hospitality, 0)) AS odc_total,
       SUM(ISNULL(p.outsourcing, 0)) AS outsourcing, SUM(ISNULL(p.info_service, 0)) AS info_service,
       SUM(ISNULL(p.dl_hours, 0)) AS dl_hours, SUM(ISNULL(p.dl_bonus, 0)) AS dl_bonus,
       SUM(ISNULL(p.odc_daily, 0) + ISNULL(p.odc_travel, 0) + ISNULL(p.odc_payreq, 0) + ISNULL(p.odc_special, 0) + ISNULL(p.odc_hospitality, 0))
         / NULLIF(SUM(ISNULL(r.rev_notax, 0)), 0) AS odc_to_rev_ratio,
       SUM(ISNULL(p.outsourcing, 0)) / NULLIF(SUM(ISNULL(r.rev_notax, 0)), 0) AS outsourcing_to_rev_ratio
FROM grp g
LEFT JOIN rev r ON r.project_id = g.project_id
LEFT JOIN cl p ON p.project_id = g.project_id
GROUP BY g.scope
ORDER BY g.scope;

-- =====================================================================================
-- B16 条款级备选（收入）：产值行上的条款名称含「据实结算」的 2025 年收入 vs 其他条款
-- 依赖：psa_pm_cuvalue.term_depict（合同条款名称，CuValueDO 注释）、no_tax_amount、type、complete_date、project_id。
-- 用途：不经合同表，直接看收入确认行上带条款名的部分。与 B14 的项目级结果对照（条款级更窄）。
-- 不确定度：中偏高。term_depict 存量可能为空或不是合同条款名原文；两条路径命中的条款集合可能不同。
WITH w AS (SELECT CAST('20250101' AS date) AS y_from, CAST('20260101' AS date) AS y_to)
SELECT CASE WHEN cu.term_depict LIKE N'%据实结算%' THEN N'a_条款名含据实结算' ELSE N'b_其他' END AS term_class,
       COUNT_BIG(*) AS row_cnt, COUNT(DISTINCT cu.project_id) AS project_cnt,
       SUM(CASE WHEN cu.term_depict IS NULL THEN 1 ELSE 0 END) AS term_depict_null_cnt,
       SUM(ISNULL(cu.no_tax_amount, 0)) AS rev_notax
FROM dbo.psa_pm_cuvalue cu CROSS JOIN w
WHERE cu.[type] IN (0, 1) AND cu.complete_date >= w.y_from AND cu.complete_date < w.y_to
GROUP BY CASE WHEN cu.term_depict LIKE N'%据实结算%' THEN N'a_条款名含据实结算' ELSE N'b_其他' END
ORDER BY term_class;

-- =====================================================================================
-- B17 条款级备选（费用，发生口径）：成本台账的条款名称快照含「据实结算」的 2025 年费用 vs 其他条款
-- 依赖：psa_cost_ledger.term_name_snap（入池时冻结的条款名，CostLedgerDO 注释）、source_type、notax_amount、business_date、entry_status、deleted。
-- 不确定度：中偏高。term_name_snap 老数据可能为 NULL（term_name_null_cnt 报告）；其标题取自 psa_contract_clause.title，与合同条款名 psa_pm_contract_term.name 是否同文未核。
WITH w AS (SELECT CAST('20250101' AS date) AS y_from, CAST('20260101' AS date) AS y_to)
SELECT CASE WHEN l.term_name_snap LIKE N'%据实结算%' THEN N'a_条款名含据实结算' ELSE N'b_其他' END AS term_class,
       COUNT_BIG(*) AS row_cnt, COUNT(DISTINCT l.project_id) AS project_cnt,
       SUM(CASE WHEN l.term_name_snap IS NULL THEN 1 ELSE 0 END) AS term_name_null_cnt,
       SUM(CASE WHEN l.source_type = 1 THEN l.notax_amount ELSE 0 END) AS odc_daily,
       SUM(CASE WHEN l.source_type = 2 THEN l.notax_amount ELSE 0 END) AS odc_travel,
       SUM(CASE WHEN l.source_type = 3 THEN l.notax_amount ELSE 0 END) AS odc_payreq,
       SUM(CASE WHEN l.source_type = 4 THEN l.notax_amount ELSE 0 END) AS odc_special,
       SUM(CASE WHEN l.source_type = 7 THEN l.notax_amount ELSE 0 END) AS odc_hospitality,
       SUM(CASE WHEN l.source_type = 5 THEN l.notax_amount ELSE 0 END) AS outsourcing,
       SUM(CASE WHEN l.source_type IN (0, 12) THEN l.notax_amount ELSE 0 END) AS dl_hours
FROM dbo.psa_cost_ledger l CROSS JOIN w
WHERE l.deleted = 0 AND l.entry_status = 0 AND l.business_date >= w.y_from AND l.business_date < w.y_to
GROUP BY CASE WHEN l.term_name_snap LIKE N'%据实结算%' THEN N'a_条款名含据实结算' ELSE N'b_其他' END
ORDER BY term_class;

-- #####################################################################################
-- 组 C 年末未结转与跨年项目
-- 源码结论（写在查询前）：
--   · 结转流程：成本事实 psa_cost_ledger（entry_status 0 有效）→ 入 WIP：每条有效成本一条 psa_wip_entry（1:1，cost_ledger_id）+ 一条 psa_wip_movement（type 0 入WIP）
--     → 转出：movement type 2 并写一条 psa_pl_ledger（trigger_type 0、pl_type 0），wip_entry.wip_status 0→1；终止项目转损益 movement 4（wip_status 2）；
--     豁免 movement 6（wip_status 3）；转出红冲 movement 3（回在制）写 pl_type 9 负行。成本台账不含 WIP 状态，WIP 状态在 psa_wip_entry.wip_status
--     （0 在制 / 1 已转出 / 2 已终止转损益 / 3 豁免 / 9 红冲或被替代）与 wip_stage（1 合同前投入 / 2 未完工 / 3 已完工 / 4 减值准备）。
--   · 转出条件（CarryCandidateReaderImpl）：stage 3（合同已回且条款 POC=100）、或项目终止、或手工名单；stage 1/2 的在制成本不转出，所以「年末未结转」＝
--     合同前投入 + 未完工 + 已完工待转出三块。
--   · 截止某期末余额：psa_wip_period_balance 是期间快照（不含 source_type，无法只取工时），期末余额口径见 WipPeriodSnapshotMapper：
--     按 entry 累计截至该期的 movement，增加类 type 0、3 取 +ABS(notax_amount)，减少类 type 2、4、6 取 −ABS，其余（1、5）为 0。B20/B21 按此口径，并给出各类型原始合计便于另行推算。
--   · 项目日期列：psa_pm_project 只有 initiation_date（立项时间）；没有完工/结束日期列（ProjectDO 无）。完工判定只有代理：项目状态 status（已完成/已关闭/终止，值为 FSTConstant 里的 bigint）、
--     最后一条确认收入日期、条款 POC 快照（psa_contract_term_poc_snapshot）。
-- #####################################################################################

-- =====================================================================================
-- B20 WIP 流水原始分布：截至 2025 年末（期间年 ≤ 2025），按（期间年 × 来源类型 × 流水类型）（公司合计）
-- 依赖：psa_wip_movement（movement_type, notax_amount, period_id, create_time, cost_ledger_id, deleted）；psa_cost_ledger（id, source_type, deleted）；mdm_accounting_period（period_year, closed_time）。
-- 口径：来源类型限 0 工时与 12 人月尾差（直接人工）。raw_sum 带符号原值；abs_sum 绝对值；neg_cnt 负额行数（判断红冲行金额符号）；
--       after_close_cnt = 创建时间晚于期间关账时间的行数（WIP 报表口径按关账冻结截止，晚于关账的不计；本条不剔除，只报告）。
-- 不确定度：中。movement 金额符号约定在 DO 注释里写「红冲用负额或反向 movement」，不明确，故同时给原值与绝对值。
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

-- =====================================================================================
-- B21 截至 2025 年末已发生未结转的直接人工余额（movement 重放口径）：区域/BU 及公司合计
-- 依赖：同 B20；psa_pm_project.dept_id；system_dept。
-- 口径：balance_abs = Σ(type 0、3 的 ABS) − Σ(type 2、4、6 的 ABS)（与 WIP 期间快照同口径，source_type 限 0 与 12，两类分列）。
--       另给各类型原值合计，若原值带符号，可在本地用 enter_raw − writeoff_raw … 交叉验证；部门取项目当前交付部门。
-- 验证：调度方说该余额约 2,060 万元——比较公司合计行（lvl=3）两个 source_type 的 balance_abs（只取 0 时看 st=0 行；0 与 12 一并看合计）。
-- 不确定度：中偏高。(1) 历史年（2020～2025）台账残缺（AnnualBonusBasis 注释），WIP 可能缺历史期初；(2) 上线前的期初余额若走期初批次，可能不在本表；
--       (3) 项目换部门时按当前部门；(4) 2025 期间的 movement 若在 2026 年才创建（关账后补录）也被计入，见 B20 after_close_cnt。
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

-- =====================================================================================
-- B22 截至 2025 年末累计已发生（成本台账）：按（期间年 × 来源类型）及区域/BU
-- 依赖：psa_cost_ledger（source_type, entry_type, entry_status, period_id, business_date, notax_amount, project_id, deleted）；mdm_accounting_period.period_year；psa_pm_project / system_dept。
-- 口径：entry_status = 0、期间年 ≤ 2025、来源类型 0 与 12；累计 = 各期间年合计在本地相加。after_cutoff_biz_amount = 业务日期晚于 2025-12-31 的部分（入池期间 ≤ 2025 但业务日在后，应为 0）。
-- 用途：与 B23（累计结转）、B21（余额）三者核对：累计发生 − 累计结转 ≈ 未结转余额（差异来自豁免、终止、红冲、未入 WIP 的成本等）。
-- 不确定度：中。历史年台账残缺；金额为日薪×人天（见 M29_BASELINE_MAPPING.md U4）。
WITH w AS (SELECT 2025 AS yr, CAST('20251231' AS date) AS cutoff),
d AS (
    SELECT p.id AS project_id,
           ISNULL(sp.name, N'(无上级部门)') AS parent_name,
           ISNULL(sd.name, N'(项目无交付部门)') AS dept_name
    FROM dbo.psa_pm_project p
    LEFT JOIN dbo.system_dept sd ON sd.id = p.dept_id
    LEFT JOIN dbo.system_dept sp ON sp.id = sd.parent_id
)
SELECT ap.period_year, l.source_type,
       GROUPING_ID(d.parent_name, d.dept_name) AS lvl,
       d.parent_name, d.dept_name,
       COUNT_BIG(*) AS row_cnt,
       SUM(l.notax_amount) AS incurred_notax,
       SUM(CASE WHEN l.business_date > w.cutoff THEN l.notax_amount ELSE 0 END) AS after_cutoff_biz_amount
FROM dbo.psa_cost_ledger l
JOIN dbo.mdm_accounting_period ap ON ap.id = l.period_id
CROSS JOIN w
LEFT JOIN d ON d.project_id = l.project_id
WHERE l.deleted = 0 AND l.entry_status = 0 AND ap.period_year <= w.yr AND l.source_type IN (0, 12)
GROUP BY ap.period_year, l.source_type, ROLLUP(d.parent_name, d.dept_name)
ORDER BY ap.period_year, l.source_type, d.parent_name, d.dept_name;

-- =====================================================================================
-- B23 截至 2025 年末累计结转进损益（损益台账）：按（期间年 × 来源类型 × 损益类型 × 触发类型）及区域/BU
-- 依赖：psa_pl_ledger（source_type, pl_type, trigger_type, period_id, notax_amount, project_id, deleted）；mdm_accounting_period.period_year；psa_pm_project / system_dept。
-- 口径：期间年 ≤ 2025、来源类型 0 与 12、pl_type 全带出（0 主营、1 减值、2 营业外、9 红冲负行）；累计结转（净）= 各行合计在本地相加（红冲行本身为负）。
--       trigger_type：0 正常转出 / 1 项目终止 / 2 红冲回退。
-- 不确定度：中。同 B22。
WITH w AS (SELECT 2025 AS yr),
d AS (
    SELECT p.id AS project_id,
           ISNULL(sp.name, N'(无上级部门)') AS parent_name,
           ISNULL(sd.name, N'(项目无交付部门)') AS dept_name
    FROM dbo.psa_pm_project p
    LEFT JOIN dbo.system_dept sd ON sd.id = p.dept_id
    LEFT JOIN dbo.system_dept sp ON sp.id = sd.parent_id
)
SELECT ap.period_year, l.source_type, l.pl_type, l.trigger_type,
       GROUPING_ID(d.parent_name, d.dept_name) AS lvl,
       d.parent_name, d.dept_name,
       COUNT_BIG(*) AS row_cnt,
       SUM(l.notax_amount) AS carried_notax
FROM dbo.psa_pl_ledger l
JOIN dbo.mdm_accounting_period ap ON ap.id = l.period_id
CROSS JOIN w
LEFT JOIN d ON d.project_id = l.project_id
WHERE l.deleted = 0 AND ap.period_year <= w.yr AND l.source_type IN (0, 12)
GROUP BY ap.period_year, l.source_type, l.pl_type, l.trigger_type, ROLLUP(d.parent_name, d.dept_name)
ORDER BY ap.period_year, l.source_type, l.pl_type, l.trigger_type, d.parent_name, d.dept_name;

-- =====================================================================================
-- B24 WIP 明细当前状态反推 2025 年末在制（与 B21 独立的交叉验证）：区域/BU 及公司合计
-- 依赖：psa_wip_entry（cost_ledger_id, project_id, wip_stage, wip_status, notax_amount, entered_period_id, written_off_at, terminated_at, deleted）；
--       psa_cost_ledger（id, source_type, deleted）；mdm_accounting_period.period_year（入 WIP 期间）；psa_pm_project / system_dept。
-- 口径：入 WIP 期间年 ≤ 2025 的工时 WIP 明细，按现状与转出/终止时间归类：
--       a_至今仍在制 + b_2026起转出 + c_2026起终止 = 2025-12-31 仍在制（推算）；d_2025及以前已转出或终止 = 年末已不在制；
--       e_豁免 f_红冲或被替代 另列；g_时间缺失 = 状态为已转出/终止但没有对应时间，无法判断。入 WIP 期间在 2026 起的单列 x，不计。
--       wip_stage 带出：1 合同前投入、2 未完工、3 已完工、4 减值准备。
-- 不确定度：中偏高。(1) 现状不是年末状态：2025 年末已转出但随后被红冲回在制的明细，现状 0 会算成「至今仍在制」，结果对红冲后再转出的明细算作 2026 起转出，方向保持一致；
--       (2) 红冲后再转出会产生新 movement，但 wip_entry 只存最近状态；(3) 同 B21 的历史期初问题。
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

-- =====================================================================================
-- B25 WIP 期间快照交叉验证：2025 各期末在制余额（所有来源类型，不分工时）
-- 依赖：psa_wip_period_balance（period_id, company_code, wip_stage, wip_status, balance, snapshot_at, deleted）；mdm_accounting_period（period_year, period_sequence, company_code）。
-- 用途：快照不含 source_type，只能得全部成本的在制总额，作为 B21（只工时）的上界；取最大 period_sequence 的期间为 2025 年末。
-- 不确定度：中。快照是否对 2025 末期已生成未核实（无行则本条无输出）；快照 wip_status 通常只存 0。
SELECT ap.company_code, ap.period_year, ap.period_sequence, b.wip_stage, b.wip_status,
       COUNT_BIG(*) AS row_cnt, SUM(b.balance) AS balance_sum, MAX(b.snapshot_at) AS snapshot_at
FROM dbo.psa_wip_period_balance b
JOIN dbo.mdm_accounting_period ap ON ap.id = b.period_id
WHERE b.deleted = 0 AND ap.period_year = 2025
GROUP BY ap.company_code, ap.period_year, ap.period_sequence, b.wip_stage, b.wip_status
ORDER BY ap.company_code, ap.period_sequence, b.wip_stage, b.wip_status;

-- =====================================================================================
-- B26 跨年项目：项目 × 立项年 × 完工代理，2025 年收入与进损益直接人工（网格，只出各格合计）
-- 依赖：psa_pm_project（id, initiation_date, status）；psa_pm_cuvalue（project_id, complete_date, no_tax_amount, type）；
--       psa_pl_ledger（project_id, source_type, pl_type, notax_amount, period_id, deleted）；mdm_accounting_period.period_year。
-- 项目日期依据：立项 = psa_pm_project.initiation_date（ProjectDO，DELIVERY_RESOURCES_EVIDENCE.md 亦称老系统 initiation_date）；
--       项目无完工日期列，完工用代理：status 为「已完成」或「已关闭」（FSTConstant.ProjectStatus：已完成 -3117158260873541206、已关闭 -5057727321925005625）
--       且最后一条确认收入日期（type 0/1 的 complete_date 最大值）早于 2026-01-01；终止（-1296737353346166958）单列。
-- 口径：只统计 2025 年有收入或有进损益成本的项目。直接人工分三列：工时 st0、尾差 st12、年度奖金 st9（奖金是否算直接人工待定）。成本取 pl_type IN (0,9)、期间年 2025。
-- 不确定度：中偏高。(1) status 值是 bigint 哈希，源码常量未对生产核对，以 B29 校验；(2) 完工用「最后收入日期 + 状态」代理，状态可能与实际完工不同步；
--       (3) 立项日期为 NULL 的项目单列 x；(4) 网格格内项目数可能很小，仅限内部。
WITH w AS (SELECT 2025 AS yr, CAST('20250101' AS date) AS y_from, CAST('20260101' AS date) AS y_to),
rev AS (
    SELECT cu.project_id,
           SUM(CASE WHEN cu.complete_date >= w.y_from AND cu.complete_date < w.y_to THEN ISNULL(cu.no_tax_amount, 0) ELSE 0 END) AS rev_y,
           MAX(cu.complete_date) AS last_rev_date
    FROM dbo.psa_pm_cuvalue cu CROSS JOIN w
    WHERE cu.[type] IN (0, 1)
    GROUP BY cu.project_id
),
dl AS (
    SELECT l.project_id,
           SUM(CASE WHEN l.source_type = 0 THEN l.notax_amount ELSE 0 END) AS dl_st0,
           SUM(CASE WHEN l.source_type = 12 THEN l.notax_amount ELSE 0 END) AS dl_st12,
           SUM(CASE WHEN l.source_type = 9 THEN l.notax_amount ELSE 0 END) AS bonus_st9
    FROM dbo.psa_pl_ledger l
    JOIN dbo.mdm_accounting_period ap ON ap.id = l.period_id
    CROSS JOIN w
    WHERE l.deleted = 0 AND l.pl_type IN (0, 9) AND ap.period_year = w.yr AND l.source_type IN (0, 12, 9)
    GROUP BY l.project_id
),
pj AS (
    SELECT p.id AS project_id,
           CASE WHEN p.initiation_date IS NULL THEN N'x_立项日期空'
                WHEN p.initiation_date < w.y_from THEN N'a_2025年以前立项'
                WHEN p.initiation_date < w.y_to THEN N'b_2025年立项'
                ELSE N'c_2026年及以后立项' END AS init_class,
           CASE WHEN p.status IN (-3117158260873541206, -5057727321925005625) AND (r.last_rev_date IS NULL OR r.last_rev_date < w.y_to) THEN N'a_已完成或已关闭且确认收入止于2025'
                WHEN p.status IN (-3117158260873541206, -5057727321925005625) THEN N'b_已完成或已关闭但2026起仍有确认收入'
                WHEN p.status = -1296737353346166958 THEN N'd_终止'
                ELSE N'c_未完成（执行中、规划中、暂停及其他）' END AS done_class,
           ISNULL(r.rev_y, 0) AS rev_y, ISNULL(d.dl_st0, 0) AS dl_st0, ISNULL(d.dl_st12, 0) AS dl_st12, ISNULL(d.bonus_st9, 0) AS bonus_st9
    FROM dbo.psa_pm_project p
    CROSS JOIN w
    LEFT JOIN rev r ON r.project_id = p.id
    LEFT JOIN dl d ON d.project_id = p.id
)
SELECT init_class, done_class,
       COUNT_BIG(*) AS project_cnt,
       SUM(CASE WHEN rev_y <> 0 THEN 1 ELSE 0 END) AS project_with_rev_cnt,
       SUM(CASE WHEN dl_st0 <> 0 OR dl_st12 <> 0 THEN 1 ELSE 0 END) AS project_with_dl_cnt,
       SUM(rev_y) AS rev_notax, SUM(dl_st0) AS dl_st0, SUM(dl_st12) AS dl_st12, SUM(bonus_st9) AS bonus_st9
FROM pj
WHERE rev_y <> 0 OR dl_st0 <> 0 OR dl_st12 <> 0 OR bonus_st9 <> 0
GROUP BY init_class, done_class
ORDER BY init_class, done_class;

-- =====================================================================================
-- B27 跨年项目三分类（B26 的归并，层级固定先后）
-- 依赖与口径：同 B26。归并顺序：先按立项年，再按完工代理：
--   1_2025年以前立项（不论何时完工） ；
--   2_2025年及以后立项且2025年内完工（立项年 2025 或更晚，且完工代理为 a） ；
--   3_完工晚于2025或未完工（立项年 2025 或更晚，其余全部：未完成、终止、2026 年起仍有收入）；
--   9_立项日期空。
-- 不确定度：同 B26。调度方的三分类有交叉（早立项也可能晚完工），本条取先按立项年的互斥分法；要换法，用 B26 网格重算。
WITH w AS (SELECT 2025 AS yr, CAST('20250101' AS date) AS y_from, CAST('20260101' AS date) AS y_to),
rev AS (
    SELECT cu.project_id,
           SUM(CASE WHEN cu.complete_date >= w.y_from AND cu.complete_date < w.y_to THEN ISNULL(cu.no_tax_amount, 0) ELSE 0 END) AS rev_y,
           MAX(cu.complete_date) AS last_rev_date
    FROM dbo.psa_pm_cuvalue cu CROSS JOIN w
    WHERE cu.[type] IN (0, 1)
    GROUP BY cu.project_id
),
dl AS (
    SELECT l.project_id,
           SUM(CASE WHEN l.source_type = 0 THEN l.notax_amount ELSE 0 END) AS dl_st0,
           SUM(CASE WHEN l.source_type = 12 THEN l.notax_amount ELSE 0 END) AS dl_st12,
           SUM(CASE WHEN l.source_type = 9 THEN l.notax_amount ELSE 0 END) AS bonus_st9
    FROM dbo.psa_pl_ledger l
    JOIN dbo.mdm_accounting_period ap ON ap.id = l.period_id
    CROSS JOIN w
    WHERE l.deleted = 0 AND l.pl_type IN (0, 9) AND ap.period_year = w.yr AND l.source_type IN (0, 12, 9)
    GROUP BY l.project_id
),
pj AS (
    SELECT p.id AS project_id,
           CASE WHEN p.initiation_date IS NULL THEN 9
                WHEN p.initiation_date < w.y_from THEN 1
                WHEN p.status IN (-3117158260873541206, -5057727321925005625) AND (r.last_rev_date IS NULL OR r.last_rev_date < w.y_to) THEN 2
                ELSE 3 END AS cls,
           ISNULL(r.rev_y, 0) AS rev_y, ISNULL(d.dl_st0, 0) AS dl_st0, ISNULL(d.dl_st12, 0) AS dl_st12, ISNULL(d.bonus_st9, 0) AS bonus_st9
    FROM dbo.psa_pm_project p
    CROSS JOIN w
    LEFT JOIN rev r ON r.project_id = p.id
    LEFT JOIN dl d ON d.project_id = p.id
)
SELECT CASE cls WHEN 1 THEN N'1_2025年以前立项'
                WHEN 2 THEN N'2_2025年及以后立项且2025年内完工'
                WHEN 3 THEN N'3_完工晚于2025或未完工'
                ELSE N'9_立项日期空' END AS class_label,
       COUNT_BIG(*) AS project_cnt,
       SUM(CASE WHEN rev_y <> 0 THEN 1 ELSE 0 END) AS project_with_rev_cnt,
       SUM(CASE WHEN dl_st0 <> 0 OR dl_st12 <> 0 THEN 1 ELSE 0 END) AS project_with_dl_cnt,
       SUM(rev_y) AS rev_notax, SUM(dl_st0) AS dl_st0, SUM(dl_st12) AS dl_st12, SUM(bonus_st9) AS bonus_st9
FROM pj
WHERE rev_y <> 0 OR dl_st0 <> 0 OR dl_st12 <> 0 OR bonus_st9 <> 0
GROUP BY cls
ORDER BY cls;

-- =====================================================================================
-- B28 跨年项目：以条款 POC 快照代替项目状态判完工（B26 的对照）
-- 依赖：psa_contract_clause（project_id, term_code, deleted）；psa_contract_term_poc_snapshot（term_code, period 'YYYY-MM', poc, deleted）；其余同 B26。
-- 口径：每个项目的每个条款取「POC 首次 ≥ 100 的月份」；项目所有条款都达 100 且最晚月份 ≤ '2025-12' → a；全部达 100 但最晚月份晚于 2025-12 → b；
--       有条款从未达 100 → c；项目无条款或条款无 POC 快照 → z。立项分类同 B26。
-- 不确定度：高。(1) psa_contract_clause.term_code 是否覆盖所有项目未核实（预算导入落定，可能只有已编预算的项目），z 类个数反映覆盖率；
--       (2) 快照 term_code 与 clause.term_code 是否同口径（源码注释称都是「结转口径 term_number」，另有注释称 clause.term_code = 合同条款 code，二者说法不一致）；
--       (3) 快照月度（YYYY-MM）。
WITH w AS (SELECT 2025 AS yr, CAST('20250101' AS date) AS y_from, CAST('20260101' AS date) AS y_to),
rev AS (
    SELECT cu.project_id,
           SUM(CASE WHEN cu.complete_date >= w.y_from AND cu.complete_date < w.y_to THEN ISNULL(cu.no_tax_amount, 0) ELSE 0 END) AS rev_y
    FROM dbo.psa_pm_cuvalue cu CROSS JOIN w
    WHERE cu.[type] IN (0, 1)
    GROUP BY cu.project_id
),
dl AS (
    SELECT l.project_id,
           SUM(CASE WHEN l.source_type IN (0, 12) THEN l.notax_amount ELSE 0 END) AS dl_hours
    FROM dbo.psa_pl_ledger l
    JOIN dbo.mdm_accounting_period ap ON ap.id = l.period_id
    CROSS JOIN w
    WHERE l.deleted = 0 AND l.pl_type IN (0, 9) AND ap.period_year = w.yr AND l.source_type IN (0, 12)
    GROUP BY l.project_id
),
tc AS (
    SELECT cl.project_id, cl.term_code,
           MIN(CASE WHEN s.poc >= 100 THEN s.period END) AS first100
    FROM dbo.psa_contract_clause cl
    LEFT JOIN dbo.psa_contract_term_poc_snapshot s ON s.term_code = cl.term_code AND s.deleted = 0
    WHERE cl.deleted = 0 AND cl.term_code IS NOT NULL
    GROUP BY cl.project_id, cl.term_code
),
pc AS (
    SELECT project_id, COUNT_BIG(*) AS term_cnt,
           SUM(CASE WHEN first100 IS NULL THEN 1 ELSE 0 END) AS open_cnt,
           MAX(first100) AS last100
    FROM tc GROUP BY project_id
),
pj AS (
    SELECT p.id AS project_id,
           CASE WHEN p.initiation_date IS NULL THEN N'x_立项日期空'
                WHEN p.initiation_date < w.y_from THEN N'a_2025年以前立项'
                WHEN p.initiation_date < w.y_to THEN N'b_2025年立项'
                ELSE N'c_2026年及以后立项' END AS init_class,
           CASE WHEN pc.project_id IS NULL THEN N'z_无条款POC数据'
                WHEN pc.open_cnt = 0 AND pc.last100 <= N'2025-12' THEN N'a_全部条款POC达100且止于2025'
                WHEN pc.open_cnt = 0 THEN N'b_全部达100但最晚月份在2026起'
                ELSE N'c_有条款未达100' END AS done_poc,
           ISNULL(r.rev_y, 0) AS rev_y, ISNULL(d.dl_hours, 0) AS dl_hours
    FROM dbo.psa_pm_project p
    CROSS JOIN w
    LEFT JOIN rev r ON r.project_id = p.id
    LEFT JOIN dl d ON d.project_id = p.id
    LEFT JOIN pc ON pc.project_id = p.id
)
SELECT init_class, done_poc,
       COUNT_BIG(*) AS project_cnt,
       SUM(CASE WHEN rev_y <> 0 THEN 1 ELSE 0 END) AS project_with_rev_cnt,
       SUM(rev_y) AS rev_notax, SUM(dl_hours) AS dl_hours
FROM pj
WHERE rev_y <> 0 OR dl_hours <> 0
GROUP BY init_class, done_poc
ORDER BY init_class, done_poc;

-- =====================================================================================
-- B29 项目状态值分布（校验 B26～B27 里写死的三个状态值）：状态值 × 项目数 × 2025 年有收入的项目数
-- 依赖：psa_pm_project.status；psa_pm_cuvalue（project_id, complete_date, type）。
-- 用途：状态值是 bigint 哈希（来自 OA 枚举），本条只出值与计数；运维核对「值 -3117158260873541206 / -5057727321925005625 / -1296737353346166958」是否存在，
--       并凭业务常识对应到已完成 / 已关闭 / 终止（标签不在本库，本条不取标签）。
-- 不确定度：低。
WITH w AS (SELECT CAST('20250101' AS date) AS y_from, CAST('20260101' AS date) AS y_to),
rev AS (
    SELECT cu.project_id
    FROM dbo.psa_pm_cuvalue cu CROSS JOIN w
    WHERE cu.[type] IN (0, 1) AND cu.complete_date >= w.y_from AND cu.complete_date < w.y_to
    GROUP BY cu.project_id
)
SELECT p.status,
       COUNT_BIG(*) AS project_cnt,
       SUM(CASE WHEN r.project_id IS NOT NULL THEN 1 ELSE 0 END) AS project_with_rev_2025_cnt
FROM dbo.psa_pm_project p
LEFT JOIN rev r ON r.project_id = p.id
GROUP BY p.status
ORDER BY COUNT_BIG(*) DESC;
