# GLOSSARY — FORCITIS one 现代化统一领域用语

日期：2026-10-14

**唯一来源**：`psa/docs/domain/00_战略设计/03-通用语言.md`（385 个术语，23 个 BC）。本文不另立术语，只做三件事：
1. 给每个 BC 指定代码位置：子域 slug 沿用已定稿的 `02-子域划分.md`，BC slug 是本次提出的方案；
2. 列出老系统的代码名、表名、页面接口与新名的对照；
3. 提出词表改词建议，需同批修改 `03-通用语言.md`（按 `00-命名一致性规则.md` 的同步清单）。

所有新代码、表、事件、API、文档都用这里的代码名，**不得自创**（ArchUnit G14 和词表校验守护）。

## 1. 模块路径：子域 → BC → 包

Spring Modulith 的顶层模块对应**子域 slug**，嵌套模块对应 **BC slug**，与老 fst 的 `domain.<subdomainSlug>.<bcSlug>.<concept>` 约定一致。
Java 包为 `com.forcitis.psa.<subdomain>.<bc>.{api,events,domain,application,infrastructure,web}`，PG schema 为 `<subdomain>_<bc>`。

| 子域 | BC | 模块路径 | schema | 本次范围 |
|---|---|---|---|---|
| operations | 战略与经营计划 BC | `operations.planning` | `operations_planning` | — |
| operations | 预算管控 BC | `operations.budgeting` | `operations_budgeting` | — |
| operations | 经营事实与归属 BC | `operations.facts` | `operations_facts` | ✔ |
| operations | 经营口径与勾稽 BC | `operations.policy` | `operations_policy` | ✔ |
| presales | 客户经营 BC | `presales.customer` | `presales_customer` | ✔ |
| presales | 商机评估与报价承诺 BC | `presales.quoting` | `presales_quoting` | ✔ |
| contract | 合约基线 BC | `contract.baseline` | `contract_baseline` | ✔ |
| contract | 变更签证索赔 BC | `contract.changeorder` | `contract_changeorder` | — |
| contract | 风险交付 BC | `contract.riskdelivery` | `contract_riskdelivery` | — |
| delivery | 项目交付执行 BC | `delivery.execution` | `delivery_execution` | ✔ |
| delivery | 质量返工与验收 BC | `delivery.acceptance` | `delivery_acceptance` | ✔ |
| cost | 工时与投入有效性 BC | `cost.timesheet` | `cost_timesheet` | ✔ |
| cost | 项目成本归集 BC | `cost.costing` | `cost_costing` | ✔ |
| receivables | WIP 与开票准入 BC | `receivables.wip` | `receivables_wip` | ✔ |
| receivables | AR 与现金转化 BC | `receivables.collection` | `receivables_collection` | — |
| expense | 费用报销与支出管控 BC | `expense.spend` | `expense_spend` | ✔ |
| procurement | 分包与采购履约 BC | `procurement.subcontracting` | `procurement_subcontracting` | — |
| km | 知识资产与 AI 赋能 BC | `km.knowledge` | `km_knowledge` | — |
| organization | 组织人事 BC | `organization.workforce` | `organization_workforce` | ✔ |
| organization | 假勤薪酬 BC | `organization.payroll` | `organization_payroll` | — |
| organization | 绩效激励 BC | `organization.incentive` | `organization_incentive` | — |
| accounting | 财务总账与法定核算 BC | `accounting.ledger` | `accounting_ledger` | ✔ |
| accounting | 会计期间 BC（**新增提议**，ADR-0016：(期间, 法人) 期间状态与关账闸门；词表需补入） | `accounting.period` | `accounting_period` | ✔ |
| platform | 权限流程与集成平台 BC（用户 / 角色 / 权限、登录与会话、数据范围） | `platform.identity` | `platform_identity` | ✔ |
| platform | 权限流程与集成平台 BC（流程 / 流程实例 / 审批（Flowable BPMN + DMN）、待办、催办） | `platform.approval` | `platform_approval` | ✔ |
| platform | 权限流程与集成平台 BC（事件总线、投递保证、异常队列、数据集成通道、外部系统防腐层） | `platform.integration` | `platform_integration` | ✔ |
| platform | 权限流程与集成平台 BC（审计日志、平台配置） | `platform.audit` | `platform_audit` | ✔ |

BC slug 的命名规则（沿用 fst `CLAUDE.md` §2）：纯英文小写；不用 Manager / Helper / Util；不用拼音；不用自造缩写；不用子域旧称（operating、opportunity、cash、knowledge）。
平台子域只有一个 BC（权限流程与集成平台 BC）。按 ADR-007，它是技术底座，所以拆成 identity / approval / integration / audit 四个技术模块，但它们同属一个 BC。

## 2. 核心术语对照（老系统 → 统一用语）

| 中文 | 代码名（词表） | 模块 | 老代码名 | 老表 | 老页面 / 接口 | 说明 |
|---|---|---|---|---|---|---|
| 报价单 | `Quote` | `presales.quoting` | Quotation / QuotationDO | psa_quotation, psa_quotation_line, psa_quotation_seal | views/project/quotation, /fst/quotation | 不用 Quotation |
| 成本估算树 | `CostEstimateNode` | `presales.quoting` | CostEstimateNode | psa_cost_estimate_* | CostEstimateTree.vue | 词表无此条：建议补入 |
| 工时估算明细 | `LaborEstimateLine` | `presales.quoting` | — | — | — | 改词建议：词表现为 ManhourEstimateItem |
| 商机 | `Opportunity` | `presales.quoting` | OpportunityDO, OppCloseStatus | psa_crm_opportunity | /fst/opportunity | 关闭状态改为枚举 OPEN/WON/LOST |
| 客户 | `Customer` | `presales.customer` | CustomerDO | psa_bd_customer | /fst/customer | CRM 为上游，老系统存只读快照 |
| 费率卡 | `RateCard / RateCardItem` | `contract.baseline` | BillRateDO, HourlyRateDO | psa_bill_rate*, psa_pm_hourlyrate* | views/project/price | 改词建议：统一 RateCard 词族（含 StandardCostRateCard、RateVersion、CustomerRateCard） |
| 合同条款 | `ContractClause` | `contract.baseline` | ContractTermDO, termNumber, clause | psa_pm_contract_term, psa_contract_clause | — | 老代码 term/clause 混用；Term 一律不用 |
| 项目 | `Project` | `delivery.execution` | PmProjectDO, ProjectDO | psa_pm_project | views/project/pmList | 项目状态改为枚举 |
| WBS 节点 | `WbsNode` | `delivery.execution` | BudgetClause/BudgetDeliverable | psa_budget_* | pmList/budget.vue |  |
| 任务包 | `WorkPackage` | `delivery.execution` | TaskPackage, task_package, workPackage | psa_task_package | taskLists | 不用 TaskPackage；交付包 = DeliveryPackage |
| 交付物 | `Deliverable` | `delivery.execution` | DeliverableDO, DeliverableSubmission | psa_deliverable* | views/project/deliverable |  |
| ETC / EAC | `EstimateToComplete / EacAssessment` | `delivery.execution` | etcHours, eac | psa_task_package.etc_hours | progress.vue |  |
| 项目预算（WBS 预算） | `ProjectBudget / PlanBaseline` | `delivery.execution` | ProjectBudget, wbs_budget_* | psa_pm_project.wbs_budget_* | pmList/budget.vue | 预算锁定 = 计划基线生效 |
| 验收单 | `AcceptanceRecord` | `delivery.acceptance` | DeliverableSubmission(DLV_ACCEPTED) | psa_deliverable_submission | deliverable |  |
| 工时单 | `Timesheet` | `cost.timesheet` | ManHourDO, TimeSheet | psa_time_sheet | views/project/manhour, timesheet | 不用 ManHour / Works |
| 工时（条目） | `TimeEntry` | `cost.timesheet` | ManHourItemDO, works | psa_time_sheet_works | manhour/fill | 改词建议：TimesheetLine 并入 TimeEntry |
| 可计费工时 | `BillableHours` | `cost.timesheet` | billable, BillingUser | psa_time_sheet_works.billable | — |  |
| 填报 / 审核 | `SubmitTimesheet / ApproveTimesheet` | `cost.timesheet` | saveManHour, approve | — | /fst/man-hour/* | 用例名 |
| 项目成本 / 成本明细 | `ProjectCost / CostEntry` | `cost.costing` | CostLedgerDO, cost_summary | psa_cost_ledger, psa_cost_summary | costCollection |  |
| 间接成本池 / 分摊 | `IndirectCostPool / AllocateOverhead` | `cost.costing` | OH pool, OhAllocationCalculator | psa_oh_cost_pool, psa_cost_allocation* | costAllocation | OH = overhead |
| 结转批次 | `CarryBatch` | `cost.costing` | CarryBatch, CarryForwardData | psa_carry_batch*, psa_wip_carry_forward_* | costCarry, carryForward | 老结转路径与新引擎并存，需下线老路径 |
| 合同履约成本 / 成本 WIP | `CostWip` | `cost.costing` | WipEntry | psa_wip_entry, psa_wip_movement | projectWipSummary |  |
| 损益台账 | `PlLedger` | `cost.costing` | PlLedgerDO | psa_pl_ledger | projectProfitL3 |  |
| 期间封闭 | `PeriodClose` | `cost.costing` | PeriodProgress, next_step | psa_period_progress | accountingPeriod |  |
| 履约进度 / 完工百分比 | `Poc / PercentageOfCompletion` | `receivables.wip` | progress, POC | psa_pm_project.progress_pro | — |  |
| 收入确认准备（产值） | `ProgressRecognized` | `receivables.wip` | CuValueDO, RevenueConfirmation, 产值 | psa_pm_cuvalue, psa_revenue_confirmation, psa_revenue_fact | views/project/revenue | 不用 cuvalue（拼音）；法定收入入账 = StatutoryRevenueBooked（核算 BC） |
| 开票申请 | `BillingRequest` | `receivables.wip` | ProjectBillingView | — | — |  |
| 报销单 / 差旅 | `ExpenseClaim / TravelExpense` | `expense.spend` | TravelApplication, Invoice | psa_travel_application* | views/accounting/travel | 差旅申请 = ExpenseRequest（费用申请） |
| 法人主体 | `LegalEntity` | `organization.workforce` | companyCode, company_code | system_dept / mdm | — | ADR-0001：legal_entity_id |
| 员工 / 组织单元 | `Employee / OrgUnit` | `organization.workforce` | AdminUserDO, DeptDO | system_users, system_dept | views/system/user, dept | A8 为主数据源 |
| 职级 / 岗位 | `JobGrade / Position` | `organization.workforce` | jobLevelCode, postCode | system_post | — |  |
| 会计凭证 | `JournalVoucher` | `accounting.ledger` | VoucherDO, U8cVoucherDO | psa_voucher*, psa_u8c_voucher | voucherManage | U8C 为外部总账 |
| 会计期间 | `AccountingPeriod（事件 AccountingPeriodClosed）` | `accounting.ledger` | AccountingPeriodDO | mdm_accounting_period | accountingPeriod | 词表只有事件名：建议补实体名 |
| 用户 / 角色 / 权限 | `User / Role / Permission` | `platform.*` | AdminUserDO, RoleDO, MenuDO | system_users, system_role, system_menu | views/system/* | 模块 platform.identity |
| 流程 / 流程实例 / 审批 | `Workflow / WorkflowInstance / Approval` | `platform.*` | Bpm*, CustomizedBpmOA | bpm_*, ACT_* | views/bpm/* | 模块 platform.approval（Flowable 8） |
| 规则引擎 / 规则执行 | `RuleEngine / RuleExecution` | `platform.*` | — | — | — | DMN 决策表即规则执行 |
| 异常队列 / 投递保证 | `DeadLetterQueue / DeliveryGuarantee` | `platform.*` | ReliableDeliveryTask, psa_bpm_mq_outbox | psa_reliable_delivery_task, psa_bpm_mq_outbox | — | 模块 platform.integration |
| 审计日志 | `AuditLog` | `platform.*` | OperateLog, ApiAccessLog | system_operate_log, infra_api_access_log | infra/logger | 模块 platform.audit |

## 3. 词表改词建议（需同批修改 03-通用语言.md）

| # | 现状 | 建议 | 理由 |
|---|---|---|---|
| G-1 | 工时估算明细 = `ManhourEstimateItem` | `LaborEstimateLine` | manhour 是旧称，行业用 labor / effort；Item 改为 Line，与 QuoteLine 一致 |
| G-2 | 工时 = `TimeEntry`，工时明细 = `TimesheetLine`（同义重复） | 只保留 `TimeEntry` | 一个概念一个名字；Timesheet（单）+ TimeEntry（条目）是 Harvest、Kantata、Jira 等的通行写法 |
| G-3 | 标准费率 `StandardCostRateCard`、费率版本 `RateVersion`、客户费率卡 `CustomerRateCard`、费率表 `RateCardItem` | 统一为 `RateCard` 词族：`StandardCostRateCard`、`BillRateCard`、`RateCardVersion`、`RateCardItem` | 词族一致，便于 agent 推断 |
| G-4 | 会计期间只有事件名 `AccountingPeriodClosed` | 补实体 `AccountingPeriod` | 实体与事件同主语 |
| G-5 | 无「成本估算树 / 节点」 | 补 `CostEstimateNode`（商机评估与报价承诺 BC） | 报价的核心结构，代码已在用 |
| G-6 | 无「法人主体」以外的核算主体别名 | 明确 `companyCode` 等老字段一律映射为 `LegalEntity` / `legal_entity_id` | ADR-0001 硬约束 2 |
| G-7 | 本次方案文档中曾使用「审批与待办 BC」 | 废止，改称「权限流程与集成平台 BC」的 `platform.approval` 模块 | 老系统领域设计的 23 个 BC 中没有「审批与待办 BC」 |

## 附录：全部术语（按模块）

### `operations.planning` — 战略与经营计划 BC

| 术语 | 代码名 | 定义 |
|---|---|---|
| 战略目标 | `StrategicGoal` | 公司层面的长期经营意图，经营计划的输入 |
| 经营计划 | `BusinessPlan` | 计划周期内目标与动作的版本化基线 |
| 计划基线 | `PlanBaseline` | 经营计划审批生效后的不可变版本 |
| 计划周期 | `PlanningPeriod` | 经营计划覆盖的时间段（年度/季度，多级待确认） |
| 预算目标 | `BudgetTarget` | 从生效计划基线发布、供预算编制使用的目标值 |
| 经营复盘 | `ManagementReview` | 面向计划周期的行动复盘（区别于口径侧的经营结果解释） |
| 经营动作 | `ActionItem` | 复盘产生的有责任对象与期限的改进行动 |
| 目标偏差 | `TargetVariance` | 计划目标 vs 实际事实的差（三偏差术语之一，见下注） |

### `operations.budgeting` — 预算管控 BC

| 术语 | 代码名 | 定义 |
|---|---|---|
| 预算基线 | `BudgetBaseline` | 预算年度×预算主体的版本化编制结果 |
| 预算编制 | `BudgetPreparation` | 依据预算目标形成预算基线的过程 |
| 预算分解 | `BudgetBreakdown` | 基线按维度树逐级生成预算账户 |
| 预算主体 | `BudgetSubject` | 预算账户的归属维度（法人/BU/项目/部门） |
| 预算占用 | `BudgetReserved` | 业务单据驱动的额度预留，可用额度即时扣减 |
| 预算释放 | `BudgetReleased` | 引用占用记录的额度归还 |
| 预算消耗 | `BudgetConsumed` | 实际发生事实对占用的冲减 |
| 可用额度 | `AvailableBudget` | 分解额 − 生效占用 − 已消耗 + 已释放，恒可复算 |
| 预算调整 | `BudgetAdjusted` | 产生基线新版本的受控变更 |
| 超预算审批 | `BudgetOverrunApproval` | 额度+期限+责任方三要素的例外授权 |
| 预算冻结 | `BudgetFreeze` | 账户级禁止新增占用的管控状态 |
| 预算执行 | `BudgetExecution` | 占用/消耗/可用的执行事实与查询视图 |
| 预算科目 | `BudgetCategory` | 预算账户的科目维度 |

### `operations.facts` — 经营事实与归属 BC

| 术语 | 代码名 | 定义 |
|---|---|---|
| 经营事实 | `BusinessFact` | 由发布事件认定、证据化、可归属的业务事实 |
| 事实证据 | `FactEvidence` | 事实的来源事件全局 ID 与原始单据引用 |
| 事实归属 | `BusinessFactAttributed` | 把事实归到九维归属维度的判定结果 |
| 主归属 | `PrimaryAttribution` | 同一口径下事实唯一的主归属结果（辅助归属需比例守恒） |
| 归属规则 | `AttributionRuleSet` | 九维归属的版本化判定规则 |
| 事实重述 | `BusinessFactRestated` | 引用原事实、落当期的受控更正（事实不可删改） |
| 责任对象 | `ResponsibleParty` | 事实归属的责任承载（责任人/BU/法人） |
| 经营期间 | `BusinessPeriod` | 经营口径下的期间，封闭后归属不可变 |
| 经营台账 | `BusinessFactLedger` | 按维度聚合事实的读模型（非聚合） |
| 经营偏差 | `BusinessVariance` | 经营事实层面的异常与漏损（三偏差术语之一） |
| 经营结果 | `BusinessResult` | 归属后事实在口径下的聚合呈现 |
| 归属规则版本 | `AttributionRuleVersion` | 归属规则集的生效版本（生效不重叠、被引用不可改） |

### `operations.policy` — 经营口径与勾稽 BC

| 术语 | 代码名 | 定义 |
|---|---|---|
| 经营口径 | `Policy` | 指标的公式、来源映射与维度适用的定义 |
| 口径版本 | `PolicyVersion` | 按期版本化、审批留痕的口径集（KPI 体系的口径主权载体） |
| 指标定义 | `MetricDefinition` | 口径版本内单个指标的四层映射（公式→语义字段→事实表→维度） |
| 经营勾稽 | `Reconciliation` | 按 KPI §15 勾稽族执行的跨域对账 |
| 勾稽差异 | `ReconciliationDeviation` | 不同口径间对账的差额（三偏差术语之一） |
| 勾稽差异案 | `ReconciliationDeviationCase` | 超容差差异的生命周期载体（解释/修正/豁免三选一关闭） |
| 公式 / 数据来源映射 | `Formula / SourceMapping` | 指标定义的构成要素（四层映射的前两层） |
| 偏差解释 / 经营结果解释 | `DeviationExplanation / ResultExplanation` | 面向口径与数据的差异解释（区别于经营复盘） |
| 口径差异清单 | `BasisDifferenceList` | 业务口径 vs 法定口径的差异及解释（与核算共维护） |
| 经营会计分类法 | `OperatingAccountingTaxonomy` | 基于 XBRL、US GAAP、ASC 606、管理会计与 FORCITIS AEC 扩展的企业级财务/经营原语语义坐标系 |
| 标准财务原语 | `StandardAccountingPrimitive` | 可映射外部会计/报告标准的财务概念，优先复用而非自创 |
| 经营扩展原语 | `OperatingAccountingExtension` | 标准财务原语不足以表达 AEC 项目制经营语义时登记的 FORCITIS 扩展概念 |
| 评估模板 | `GoNoGoTemplate` | 追/不追评估的维度权重、阈值区间、否决项、评审角色权重、决策-赢率映射的版本化配置（按业务线差异化，policy_param） |

### `presales.customer` — 客户经营 BC

| 术语 | 代码名 | 定义 |
|---|---|---|
| 客户 | `Customer` | 具备独立签约与付款主体资格的法律责任实体的主数据档案（判定标准=能否独立签合同、独立承担付款义务，业主报告 2026-07-06）；唯一法定标识，合并保留历史 |
| 客户分级 | `CustomerTier / CustomerTierSnapshot` | 五级客户分层评定：S 战略 / A 重点 / B 机会 / C 交易 / D 风险（引用分级规则版本、季度集中评定、须引用经营数据——业主报告 2026-07-06 落库，原 CustomerTiering 代码名弃用；商机 BC 评估单侧固化副本 = CustomerTierSnapshot，原 CustomerValueTier 漂移名清理 2026-07-06） |
| 客户价值 | `CustomerValue` | 分级评分的量化维度（历史贡献、潜力；评分规则 policy_param） |
| 客户风险画像 | `CustomerRiskProfile` | 有来源事实引用的客户风险聚合；AR/争议风险事实回流（Q24 已裁决，2026-07-04） |
| 黑名单 | `Blacklist` | 阻断新商机追决策的客户管控状态 |
| 客户关系状态 | `RelationshipStatus` | 潜在/合作中/休眠/黑名单的经营状态 + 已合并/已失效两个治理终态的状态机；解除黑名单返回转入前原状态（记录 previousStatus） |
| 历史合作 | `EngagementHistory` | 合同/项目引用的合作记录（读模型承载——过程数据不入写模型；聚合内仅存最近活跃合作时间供休眠自证） |
| 客户合并 | `MergeCustomer / CustomerMerged` | 治理动作，仅限三类：同一法定标识重复建档 / 法律主体承继（须证据+强审批）/ 虚拟转正合并——不同法定实体禁止合并（外部定稿审查 P0-1 修回 2026-07-06）；被并客户入已合并终态不可逆；下游按 canonical mapping 消费，历史事实原客户 ID 不改写 |
| 最近活跃合作 | `LastActiveEngagement` | 客户最近一次合作事实（合同基线生效/交付完结/赢单）的时间锚点，休眠判定的聚合内自证依据 |
| 虚拟客户 | `VirtualCustomer` | 不具备独立签约付款资格、为线索/治理目的临时建档的客户记录（两型：线索临时/治理过渡——内部核算型 Q56 裁决取消 2026-07-06）；不参与客户分级、不计入正常客户名录、命名必须加规范前缀（业主报告 2026-07-06） |
| 客户归并映射 | `CustomerCanonicalMapping` | 合并后下游各 BC 维护的旧客户→主客户映射（当前引用与读模型归并用；历史事实原 ID 永不改写——P0-3 修回 2026-07-06） |
| 失效清查快照 | `InvalidationEligibilitySnapshot` | 失效客户前六项在途业务清查（商机/合同/项目/WIP/AR/争议）结论的本地快照（P1-4 修回 2026-07-06） |
| 客户分类 | `CustomerCategory` | 正式法人 / 线索临时 / 治理过渡 三分类（FORMAL_LEGAL / LEAD_TEMPORARY / GOVERNANCE_TEMP；内部核算型 Q56 业主裁决取消 2026-07-06——内部结算走 ADR-011 关联法人，不伪装成客户） |
| 客户转正 | `ConvertCustomer / CustomerConverted` | 虚拟客户经查重校验、数据迁移升级为正式法人客户的治理动作 |
| 客户失效 | `InvalidateCustomer / CustomerInvalidated` | 客户记录的治理终态：工商注销或虚拟客户到期清理归档（主数据留存、仅可查询，不可物理删除） |
| 客户集团 / 集团归属 | `CustomerGroup / GroupAffiliation` | 集团为参考主数据轻聚合（GroupId 标识，**非 Customer**——集团经常不是签约付款实体，P0-2 修回 2026-07-06）；客户以 GroupAffiliation 单归属引用；集团仅作统计与规则继承维度，不承载业务单据；集团母公司若自身签约则另建正常客户 |
| 付款主体 / 开票主体 | `PayerParty / InvoicedParty` | 实际承担付款义务、发票抬头对应的客户主体（可与签约客户不同——多法人跨境形态；必须为有效正式客户） |
| 多主体关联 | `MultiPartyRelation` | 签约法人、付款主体、开票主体、项目业主的客户侧关联映射 |
| 联系人 | `Contact` | 客户组织内的对接人（客户聚合内实体，按对接板块分类；跨客户流动以新联系人记录承载） |
| 决策关系地图 | `RelationshipMapItem` | 客户组织内决策人/影响人/使用人/反对者的结构标注（引用联系人，含对接层级与关系强度） |
| 客户专属费率 | `CustomerRate` | 与客户单独议价的工时/服务费率，时间分片多版本、只追加不删除（与职级计费费率体系的衔接待 Q54） |
| 客户费率卡 | `CustomerRateCard` | 承载单客户全部专属费率时间分片的聚合（每客户至多一张；同维度同时段唯一生效在卡内自证） |
| 客户拜访记录 | `CustomerVisit` | 日常客情维护的事实档案（一事一单、只追加、作废留痕；关联联系人） |
| 客户需求项 | `CustomerDemand` | 全链路采集的客户需求与战略洞察的结构化沉淀（必须关联可追溯来源事实；组织级客户需求资产池的最小单元） |
| 账户计划 | `AccountPlanSnapshot` | S/A 级客户的年度经营计划（编制审批流程载体待 Q55；本 BC 以快照存档，S 级无有效计划触发预警） |
| 客户负责人 | `AccountOwner / AccountOwnerSnapshot` | 客户经营的第一责任人（Account Owner，业主报告 2026-07-06；任职主数据归组织人事 BC，客户聚合持 AccountOwnerSnapshot 本地快照——P1-7 落模型） |
| 数据质量标签 | `DataQualityTag` | 客户主数据的异常类型、治理状态与豁免到期标注（存量治理配套） |

### `presales.quoting` — 商机评估与报价承诺 BC

| 术语 | 代码名 | 定义 |
|---|---|---|
| 线索 | `Lead` | 尚未立项为商机的潜在机会信息，生成→培育→验证→转化/关闭的独立生命周期（业主语言定稿，ADR-012） |
| 线索培育 | `NurtureLead` | 线索的持续跟进与信息补全活动 |
| 线索来源 | `LeadSource` | 五渠道：老客户/互联网/市场活动/转介绍/内部员工推荐（业主口述 2026-07-05；内部推荐的奖励核算归绩效激励 BC，本 BC 仅留推荐人事实） |
| 线索验证三要素 | `LeadQualificationElements` | 验证通过的必要条件：客户预算、关键联系人、预计启动时间（业主口述 2026-07-05 固化为守卫） |
| 线索验证 | `QualifyLead / LeadQualified` | 线索能否转化为商机的决策闸口：通过即转化、不通过即关闭（追/不追前的粗筛） |
| 商机 | `Opportunity` | 立项跟进的销售机会，评估→报价→承诺→转化的全生命周期对象 |
| 机会评估 | `OpportunityEvaluated` | 商机价值、赢率与投入回报的评估过程 |
| 标前引导 | `PreBidEngagement` | 评估中阶段的客户影响与追/不追信息获取活动（业主用词 2026-07-05；capture/pre-bid engagement 行业语义） |
| 获取方式 | `TenderMethod` | 商机的竞争获取形式：投标 / 比价 / 直接委托（业主口述：大部分投标、少量比价、直接委托很少） |
| 追/不追评估要素 | `GoNoGoAssessment` | 四维评估框架：A 客户/B 项目/C 竞争/D 资源（22 因子明细为模板配置数据，非领域模型——GnG 规格书 v1.0 落库 2026-07-05，取代口述七维简化版） |
| 追/不追评估单 | `GoNoGoEvaluation` | 四维积分卡评估的过程载体：多评审者角色加权（BD 30/技术 25/PM 25/管理层 20 默认）、加权百分制、四档决策（GO≥75/GO_WITH_CONDITIONS 60-74 限期复评/ESCALATE 45-59 管理层研判/NO_GO<45）、否决项强制不追、版本化 |
| 评审者打分 | `ReviewerScore` | 评审者 × 因子的独立打分记录（互不可见，永久留存供复盘校准） |
| 维度汇总结果 | `DimensionResult` | 评估单内按维度（A/B/C/D）的加权得分率汇总 |
| 评估模板快照 | `EvaluationTemplateSnapshot` | 发起评估时从 GoNoGoTemplate 固化入评估单的本地快照（审计可追溯） |
| 评估结果快照 | `GoNoGoDecisionResult` | 商机聚合持有的评估结果本地快照（得分/档位/版本，供不变式 3 自证） |
| 报价结果快照 | `QuoteResultSnapshot` | 商机聚合持有的生效报价结果本地快照（版本/毛利/审批标记/折扣/分量摘要，供不变式 20 自证） |
| 客户画像快照 | `CustomerProfileSnapshot` | 商机/合约聚合持有的客户画像本地快照（分级/关系状态/分类/冻结/画像版本——CustomerProfileUpdated 同步，评估 E2-1 补登记 2026-07-07） |
| 售前投入快照 | `PresalesInvestmentSnapshot` | 商机聚合持有的归集台账累计快照（含 ledger_version/entry_count，承诺守卫自证） |
| 售前预算额度 | `PresalesBudgetLimit` | 商机级预算包额度的占用回执同步值（商机与台账双聚合各持快照） |
| 归集条目 | `PresalesInvestmentEntry` | 售前投入台账的只追加入账行（来源事实引用+金额） |
| 预计收入 | `EstimatedRevenue` | 报价的折前标准价（毛利率口径的分子基数） |
| 全成本估算 | `EstimatedCost` | 报价五分量成本容器（direct_labor 自动计算+四项外部/其他） |
| 毛利率 | `GrossMarginRate` | (折后收入−五分量合计)/折后收入（阈值与口径版本归经营口径与勾稽 BC） |
| 询价结果引用 | `QuotationResultRef` | 报价委外/分包分量的询价结果版本化引用（Q49-③，过期须重询） |
| 外部成本审批结论 | `ExternalCostApprovalDecision` | 报价外部分量的承诺前审批留痕（Q51 层级 policy_param） |
| 前端风险等级 | `RiskLevel（商机侧）` | 商机侧前端亏损风险分档（YELLOW/RED，FrontRiskRaised 载荷值）——与风险交付 BC 的 RiskLevel（授权审批层级分档）同名异义，分行注权（评估 E2-2，2026-07-07） |
| 否决项 | `VetoRule` | 触发即强制不追的红线规则（初值三条：付款信用 >180 天逾期 / 产能与关键人员双枯竭 / 高合规风险×低毛利，模板配置可扩） |
| 投标组 | `BidTeam` | 追决策通过后组建的商机跟进团队（本 BC 仅登记成员身份；派工归工时与投入有效性 BC） |
| 追/不追 / Go-NoGo | `GoNoGoDecision` | 是否投入跟进的正式决策（业主用词「追/不追」2026-07-05；行业术语 Go/No-Go bid decision，代码名保持 GoNoGo 词族；追结论前禁止正式报价） |
| 报价方案 / 报价单 | `Quote` | 报价编制、审批与定价的独立聚合（2026-07-05 聚合根严格评估拆出）：工时估算×职级费率、五分量全成本、折扣管控、版本链（**每版本一个聚合实例**：调整产生新实例携前版引用，SUPERSEDED/VOIDED 为实例终态——冷启动提问 5 显式化 2026-07-07）；草拟→提交→BU 审定→生效流转 |
| 报价审定 | `ApproveQuote / QuoteApproved` | BU 负责人对报价的审批环节：录入委外/分包/居间三项外部费用、确认全成本口径（业主口述 2026-07-05） |
| 折扣 | `DiscountRate` | 报价相对标准价的减让，毛利阈值检查对象 |
| 计费费率 / Bill Rate | `BillRate` | 报价费率 = 职级标准费率 × 费率乘数（乘数通常 ≥3，policy_param）；销售不得修改费率与工时、仅可在折扣上操作（业主口述 2026-07-05，管控重点） |
| 职级计费费率 | `GradeBillRate` | 职级（T1-T7）→ 标准费率 → ×乘数 → 最终报价费率的内部定价基准（区别于合约基线 BC 的合同约定费率表）；标准费率与乘数范围归经营口径与勾稽 BC policy_param |
| 工时估算明细 | `ManhourEstimateItem` | 报价内按职级的预估工时明细行（职级×工时×费率=人工小计）；仅交付角色（项目经理/总监）可修改，销售只读——估算准确性与责任挂钩 |
| 折扣权限 | `DiscountAuthority` | 销售职级 → 折扣上限的分级映射（超限须升级审批；映射表归经营口径与勾稽 BC policy_param）；折扣分布为全司管控观察指标 |
| 外部成本审批 | `ApproveQuoteExternalCost / QuoteExternalCostApproved` | 报价内委外/分包/居间分量的承诺前审批（结论留痕；层级与阈值 policy_param 归经营口径与勾稽 BC 维护，Q51 业主裁决 2026-07-05） |
| 风险评审 | `CompleteFrontRiskReview / FrontRiskReview` | 低毛利/售前超额/超范围触发的承诺前评审（结论留痕，2026-07-05 战略审查评审对象化） |
| 赢率 | `WinProbability` | 商机赢单概率（PWIN，F185 低胜率口径） |
| 售前投入 | `PresalesInvestment / PresalesInvestmentAttributed` | 商机跟进消耗的资源（商机级预算包受控，ROI_BD 分母；归属事实入发布语言，未转化归 BU） |
| 顾问建议书 / Proposal | `Proposal` | 向客户提交的正式建议书——范围/价格/条件的载体（业主用词「顾问建议书」2026-07-05；consulting proposal 行业词，代码名 Proposal 词族不动） |
| 顾问建议书承诺 | `ProposalCommitted` | 对顾问建议书内容的正式承诺（生成快照传合约，同一时间仅一个生效版本） |
| 商机转化 | `OpportunityAccepted` | 赢单并输出承诺快照给合约基线 BC |
| 失单 | `RecordOpportunityLoss / OpportunityLost` | 参与竞争后败北的商机终态（进赢单率 WR 分母，失单复盘对象；区别于不追与已关闭） |
| Pipeline 健康度 | `PipelineHealth` | 阶段≥50% 商机期望贡献占比（F186） |

### `contract.baseline` — 合约基线 BC

| 术语 | 代码名 | 定义 |
|---|---|---|
| 合同 | `Contract` | 与客户的收入合同（采购/分包合同另属采购子域，独立语义）；四形态：独立合同/框架协议/任务订单/内部结算协议 |
| 合同形态 | `ContractType` | 独立合同 / 框架协议 / 任务订单 / 内部结算协议 四形态枚举（决定必填要件与主体规则） |
| 合同草案 | `ContractDraft` | 未审批签署的合同工作态（可修改；基线化后转不可变演进） |
| 合同来源 | `ContractSourceRef` | 草案来源：赢单转化 / 直建（例外原因+审批必填）/ 历史导入（LEGACY_MIGRATION 标记，豁免承诺比对）/ 框架下任务订单 |
| 客户方主体 | `CustomerParty` | 合同甲方的客户引用与签约瞬间冻结快照（customerId 稳定引用 + 名称/法定标识快照——历史基线不随客户主数据演变漂移；内部结算协议形态为空、改用关联法人对）；付款/开票/项目业主主体同构引用（缺省=客户方主体） |
| 签署证据 | `SignatureEvidence` | 合同签署的证据留存（签署文件、时间、主体、方式——经电子合同防腐层接入） |
| 审批证据 | `ApprovalEvidence` | OA 审批结果事实（流程 ID、结论、审批人——经防腐层接入） |
| 当前有效基线 | `ActiveBaselineVersion` | 同一合同同一时间唯一生效的基线版本（新版本生效旧版本原子转被取代态） |
| 承诺差异处理结论 | `DeviationDisposition` | 承诺差异的处理终态：已接受/已拒绝/已豁免（不利差异处于待处理时禁止基线化） |
| 合同缺口项 | `ContractGapItem` | 单条缺口的生命周期实体（识别→处理中→已解除 / 已豁免；豁免=风险被授权接受≠缺口不存在） |
| 缺口豁免 | `WaiveContractGap / ContractGapWaived` | 管理层授权接受缺口风险的显式动作（审批留痕，缺口事实仍可审计） |
| T&M 封顶 | `TmCap` | T&M 合同的可选计费上限（有 cap 时开票准入执行硬检查，Q5 业主确认） |
| 框架限额占用 | `FrameworkCapacityReservation` | 任务订单登记对框架协议金额限额的占用（占用→创建→失败释放的编排补偿；Q4 限额可选） |
| 付款条件项 | `PaymentTermItem` | 付款周期、账期、付款前置条件、逾期条款的合约侧定义 |
| 历史合同导入 | `ImportLegacyContractBaseline` | 存量合同的受控迁移入口（LEGACY_MIGRATION 来源标记、豁免承诺比对、审计可辨） |
| 合同签署事实 | `ContractSigned` | 签署完成的经营事实事件（签约额指标的事实来源；语义护栏：仅签署事实、不作交付/WIP/开票充分依据） |
| 合同范围条目 | `ScopeItem` | 工作条款内的范围行（服务阶段/交付成果/排除项/客户输入责任） |
| 框架挂靠信息 | `FrameworkLink` | 任务订单对框架协议的挂靠引用（框架 ID+订单编号+占用金额） |
| 内部结算条款 | `InternalSettlementTerm` | 内部结算协议的关联法人对与结算口径引用（ADR-011，只登记不计算） |
| 合同期间 | `ContractPeriod` | 合同起止日（任务订单期间须落框架期间内） |
| 基线版本号 | `BaselineVersionNo` | 单调递增的基线版本标识 |
| 合同完备度结论 | `ContractCompletenessResult` | 六项判定（签署/范围/金额/期限/计价要件/主体清晰）的结论与缺口清单 |
| 我方法人引用 | `LegalEntityRef` | 签约法人+交付法人的结构载体（词表概念 ContractingLegalEntity 的实现形态，共享内核 LegalEntityId） |
| 变更引用 | `ChangeOrderRef` | 基线版本 2+ 的变更批准事实引用（变更编号+event_id） |
| 赢单预填快照 | `OpportunityAcceptedSnapshot` | 赢单来源草案绑定的 OpportunityAccepted 本地快照 |
| 承诺比对基准快照 | `ProposalCommitmentSnapshot` | ProposalCommitted 的本地快照（承诺差异比对基准） |
| 项目业主主体 | `ProjectOwnerParty` | 合同上的项目业主客户引用（客户 BC 多主体之一的合约侧登记；开票主体合约侧统一沿用客户 BC 码名 InvoicedParty、不另造 InvoiceTitleParty——评估 D-06 双码名裁决 2026-07-07） |
| 合同工作条款 | `ContractWorkingTerms` | 当前可编辑的合同条款容器（范围/开票计划/费率/付款条件）；与基线快照分离——基线生效时复制生成不可变快照（定稿审查 P0-5） |
| 签署例外 | `ContractSigningException` | 签署前客户转黑名单的唯一放行通道（例外类型+管理层审批+证据留痕；特批通道开放性待 Q59） |
| 历史导入质量等级 | `LegacyBaselineQuality` | LEGACY_MIGRATION 数据质量四级：COMPLETE/PARTIAL/MINIMAL/UNVERIFIED——UNVERIFIED 不得作 WIP 与开票依据（定稿审查 P1-4） |
| 修正合同主体 | `CorrectContractParty` | DRAFT 限定的主体修正命令（留痕；合并重绑建议的人工确认入口——合并不静默切换合同主体） |
| 合同基线 | `ContractBaselined` | 签署审批后生效的合同事实真相版本 |
| 基线版本 | `BaselineVersion` | 单调递增、历史不可变的基线快照（版本2+仅由已批准变更产生） |
| 合同范围 | `ContractScope` | 基线内的范围条目，WBS 挂接与超范围判断的基准 |
| 履约条件 | `PerformanceCondition` | 合同约定的交付、质量、验收条件 |
| 计价模式 | `PricingModel` | 现役三种：固定总价 / T&M / 成本加酬金（**形态与计价正交** P0-1；Q6 三种履约计量方式佐证）；**封闭枚举+开放规格**：新计价类型经扩展协议注册（词表定名→规格注册→WIP 计量确认→schema 扩值→ADR），即插即用（业主需求 2026-07-06） |
| 计价模式规格 | `PricingModelSpec` | 每种计价模式的行为契约：必备商务条件集、履约计量方法、开票准入档案、基线快照字段集、数理守恒规则——不变式通用化校验的策略载体；新模式必须 ADR+策略实现、不做运行时配置 |
| 固定总价 | `FixedPrice` | 按里程碑/节点开票计划计价的模式 |
| T&M 工时计费 | `TimeAndMaterials` | 可计费工时×费率计价的模式（须有费率表） |
| 框架协议 | `FrameworkAgreement` | 挂靠任务订单的框架合同（限额按合同约定，Q4 已确认） |
| 任务订单 | `TaskOrderRegistered` | 挂靠已生效框架协议的独立聚合实例 |
| 成本加酬金 | `CostPlus` | 成本实报实销+约定酬金的计价模式 |
| 费率表 | `RateCardItem` | T&M 计费的职级/角色费率清单（含币种与有效期） |
| 合同条款 | `ContractClause` | 合同的结算条款实体（固定总价阶段款 FIXED_LUMP_SUM——98% 主形态 / 按量结算款 UNIT_RATE——品控按次月结；业主口述 2026-07-07）：clauseId 为**经济事实挂接锚点**（收入/成本/开票/回款按条款挂载，业主裁决）；聚合内实体非独立聚合根（生命周期依附+总额守恒聚合内自证）；轻状态机 PENDING→ACTIVE→SETTLEABLE→SUPERSEDED/VOIDED（开票/结清为读模型投影态）；原 BillingScheduleItem 升级并入 |
| 开票计划 | `ContractClause（FIXED_LUMP_SUM）` | 固定总价合同的节点/比例开票安排（已升级为合同条款 ContractClause 的固定总价阶段款形态——历史码名 BillingScheduleItem 弃用，2026-07-07） |
| 签约法人 | `ContractingLegalEntity` | 合同的我方法人主体（与交付法人可不同，均必填） |
| 承诺差异 | `CommitmentDeviation` | 顾问建议书承诺快照与合同基线的差异记录（比对为基线生效前置、不利差异须有处理结论；记录事实发布 CommitmentDeviationRecorded） |
| 合同完备度 | `ContractCompleteness` | 签署/范围/金额/期限/计价要件的完备结论 |
| 合同缺口 | `ContractGapIdentified` | 完备度缺陷的显式风险事实（承载于合同缺口项实体，非主状态；是否允许缺口下交付归风险交付 BC） |
| 开票计划节点 | `BillingScheduleItem` | 合同开票计划的节点（须挂接交付里程碑，交付执行 BC 不变式 3a） |

### `contract.changeorder` — 变更签证索赔 BC

| 术语 | 代码名 | 定义 |
|---|---|---|
| 变更 | `ChangeOrder` | 合同范围/价格/工期的正式修改（增补协议是落地形式之一） |
| 变更意向 | `ChangeOrderDraft` | 变更的识别登记态（影响评估的前置） |
| 影响评估 | `ImpactAssessment` | 变更的范围/成本/工期/价格影响（ΔCO，F038 分量） |
| 未批先干 | `UnapprovedWorkFlag` | 已执行未获批的变更风险标记（F170 落地率分母差，必须可视） |
| 增补 | `SupplementalAgreement` | 以增补协议落地的重大变更形式 |
| 签证 | `SiteVisa` | 现场事实的客户签认（证据，不必然改基线） |
| 签证时效 | `SiteVisaTimeWindow` | 签认的时间窗口（按项目类型配置、默认 14 天，超窗标记取证风险，Q18 已确认） |
| 索赔 | `Claim` | 一方对另一方的补偿主张（双向：我方主张/客户主张） |
| 索赔通知 | `ClaimAsserted` | 合同时限内的正式主张通知（超时限记时限风险） |
| 索赔时限 | `ClaimTimeBar` | 合同约定的索赔通知期限 |
| 无签证范围蔓延 | `ScopeCreepIdentified` | 未正式化的隐性范围扩张（F171，交付侧闸口为 OutOfScopeWorkDetected） |
| 范围变化 | `ScopeChanged` | 变更认定的范围影响事实 |
| 变更批准 | `ChangeOrderApproved` | 客户批准变更（携批准证据与 ΔCO） |
| 签认 | `SiteVisaConfirmed` | 客户代表对签证的确认动作 |
| 责任方 / 原因分类 | `ResponsibleParty / ReasonCategory` | 变更/签证/索赔的归因必填维度（对齐 dim_scope） |
| 达成 | `ClaimSettled` | 索赔谈判的结案（携 SETTLE_AMT） |
| 罚款 | `Penalty` | 客户主张的扣罚（PEN，认定在本 BC、贷项执行在 AR，Q19 已确认） |

### `contract.riskdelivery` — 风险交付 BC

| 术语 | 代码名 | 定义 |
|---|---|---|
| 证据效力等级 | `EvidenceLevel` | 预开工/风险交付证据的司法效力四级分级（一级盖章文件/二级官方邮箱确认+交付回执/三级沟通与履约痕迹/四级口头——系统判定不可人工改；项目部章须授权佐证方判一级） |
| 风险交付 | `RiskDelivery` | 合同不完备或越界状态下的受控例外交付 |
| 风险交付授权 | `RiskDeliveryAuthorization` | 额度+期限+责任方三要素的例外授权（同项目同类型同时段唯一） |
| 无合同交付 | `UncontractedDelivery` | 交付启动时无生效合同基线（起算锚点，F169 证据，Q1 已确认从严口径） |
| 超范围交付 | `OutOfScopeDelivery` | 超出基线范围+已批准变更的先行投入 |
| 合约风险 | `ContractRisk` | 合约子域风险事实的统称（敞口对齐 F127A） |
| 风险类型 | `RiskType` | 无合同/合同缺口/超范围/前端高风险承接四类 |
| 风险等级 | `RiskLevel` | 授权审批层级路由的风险分档 |
| 授权额度 / 授权期限 | `AuthorizedLimit / AuthorizationPeriod` | 风险交付授权的三要素之二（成本口径上限与起止日） |
| 责任承担方 | `ResponsibleParty` | 授权三要素之三（BU/法人/责任人） |
| 风险敞口 | `RiskExposure` | NR+CA+AR 三段合计的无合同敞口（F127A） |
| 转正 | `Regularized` | 合同生效/变更批准后的授权复核关闭 |
| 叫停 | `Halted` | 风险交付的主动终止（通知交付侧） |

### `delivery.execution` — 项目交付执行 BC

| 术语 | 代码名 | 定义 |
|---|---|---|
| WBS | `WbsNode` | 工作分解结构，节点必须挂接合同范围条目或已批准变更 |
| 交付包 / 任务包 | `DeliveryPackage / WorkPackage` | WBS 下的工作单元分组 |
| 交付物 | `Deliverable` | 向客户交付的成果物（验收对象类型之一） |
| 交付计划 | `DeliveryPlan` | WBS/里程碑/计划价值的版本化计划 |
| 计划基线 | `PlanBaseline` | 交付计划的不可变版本（修订须依据；与经营子域计划基线同名不同义） |
| 里程碑 | `Milestone` | 计划内的关键节点（执行状态挂交付项目聚合） |
| 完成申报 | `MilestoneCompletionSubmitted` | 交付侧的里程碑完成声明（验收裁定权归质量侧，ADR-005） |
| EAC | `EacAssessment` | 完工估算 = 已发生成本（成本归集提供）+ ETC（本 BC 估算），投入法 POC 分母 |
| ETC | `EstimateToComplete` | 剩余完工估算 |
| 进度偏差 | `ProgressDeviationIdentified` | EVM 口径的 SPI 偏差（F175） |
| 阶段性交付 / 交付周期 / 交付偏差 | `PhasedDelivery / DeliveryCycleTime / DeliveryVariance` | 交付执行的过程度量 |
| 后期服务单 / 后期服务工时 | `PostCompletionServiceOrder / PostCompletionServiceHours` | 质保期服务的登记与工时（归属待 Q21） |
| 交付确认单 | `DeliveryConfirmation` | 完成申报的证据载体 |

### `delivery.acceptance` — 质量返工与验收 BC

| 术语 | 代码名 | 定义 |
|---|---|---|
| 质量标准 | `QualityStandard` | 验收检查项的依据（源自合同履约条件与公司制度） |
| 验收单 | `AcceptanceRecord` | 统一验收裁定载体（对象类型化：里程碑/交付包/交付物/最终验收） |
| 验收对象类型 | `AcceptanceTargetType` | 验收单的类型维度（ADR-005 统一模型） |
| 有条件通过 | `ConditionalPass` | 附条件项与整改期限的验收结论（销项前不发 MilestoneAccepted，Q22） |
| 验收退回 | `AcceptanceReturned` | 验收不通过，里程碑回进行中 |
| 交付验收状态 | `AcceptanceStatus` | 验收单状态机的对外呈现 |
| 质量问题 | `QualityIssue` | 识别→确认→处置→关闭的质量缺陷生命周期 |
| 阻断性质量问题 | `BlockingQualityIssue` | 未关闭则阻断验收通过的质量问题 |
| 让步接受 | `ConcessionAcceptance` | 不返工、降标准接受的处置方式（客户书面确认为硬性守卫，Q38 业主裁决） |
| 返工认定 | `ReworkRecognition` | 原因分类+责任归因双要素的返工事实认定 |
| 返工原因分类 | `ReworkReasonCategory` | 六类编码：客户原因/内部质量/范围不清/人员能力/管理失控/交付理解偏差（Q8 定稿 2026-07-04） |
| 责任归因 | `ResponsibilityAttribution` | 责任方+责任比例的认定 |
| 质量损失 | `QualityLoss` | 挂质量问题与责任方的损失金额（QL） |
| 返工率 / 质量缺陷率 | `ReworkRate / DefectRate` | 质量侧过程度量（F030Q 等） |
| 验收通过 | `MilestoneAccepted` | 检查项全通过且无阻断问题的验收结论（发布 MilestoneAccepted） |
| 返工 | `Rework` | 质量问题的处置方式之一（须开返工认定单） |

### `cost.timesheet` — 工时与投入有效性 BC

| 术语 | 代码名 | 定义 |
|---|---|---|
| 工时 | `TimeEntry` | 员工填报的时间投入记录 |
| 工时单 | `Timesheet` | 员工×期间的填报与审核载体 |
| 有效工时 | `EffectiveHours` | 经认定形成有效产出的工时（EFF_HRS 分子） |
| 无效工时 | `IneffectiveHours` | 认定为未形成有效产出的工时 |
| 返工工时 | `ReworkHours` | 只能由 ReworkRecognized 引用产生的工时分类 |
| 可计费工时 | `BillableHours` | 有效 且 关联 T&M 合同 且 在费率表范围内的工时（可计费 ⊆ 有效） |
| 有效性认定 | `EffectivenessAssessment` | 项目×期间的批量认定（数量守恒：四类之和=填报总数） |
| 产能变现 | `CapacityRealization` | 可用产能转化为有效产出的分析（F022/F187） |
| 闲置产能 | `IdleCapacity` | 闲置+待派+无效内部事务（F187 口径） |
| 工时明细 | `TimesheetLine` | 工时单内的日期×项目×小时记录行 |
| 填报 / 审核 | `SubmitTimesheet / ApproveTimesheet` | 工时单的员工提交与 PM 审核动作 |
| 认定证据 | `ValidationEvidence` | 有效性认定必备的交付事实/产出引用 |
| 填报截止 | `SubmissionDeadline` | 期间填报的截止时点（policy_param） |

### `cost.costing` — 项目成本归集 BC

| 术语 | 代码名 | 定义 |
|---|---|---|
| 项目成本 | `ProjectCost` | 项目直接投入的归集结果（DC_BIL = DL + DE + 分包） |
| 成本账户 | `ProjectCostAccount` | 项目×承担法人×币种的归集与期间封闭载体 |
| 成本明细 | `CostEntry` | 只追加、必带来源事实引用与归因维度的成本记录 |
| 直接人工 | `DirectLabor` | 有效工时×标准费率版本（可复算） |
| 直接费用 | `DirectExpense` | 判定为项目直接的费用（经费用 BC 防腐层） |
| 外包成本 | `OutsourcingCost` | 人力劳务外包投入：按工时/人月计费、人员受我方管理（Q31 业主定稿 2026-07-04） |
| 分包成本 | `SubcontractCost` | 成果性专业分包投入：按验收计价、责任随成果转移（Q31 业主定稿 2026-07-04，核算科目与成本路径同步区分） |
| 成本归集 | `CostAllocated` | 权责发生制下按来源分类入账成本账户 |
| 成本归因 | `CostAttribution` | 六分量归因维度（返工/效率/范围/结构/浪费/其他，F102A） |
| 成本调整 | `CostAdjustment` | 封期后引用原明细的当期更正 |
| 标准费率 | `StandardCostRateCard` | 薪酬脱敏后的版本化政策费率（财务维护） |
| 费率版本 | `RateVersion` | 生效期间不重叠、被引用不可改的费率快照 |
| 待内部结算 | `PendingIntercompanySettlement` | 跨法人投入的挂起状态，按 ADR-011 内部结算单确认后解除 |
| 成本预算 / 成本偏差 | `CostBudget / CostVariance` | 预算对照与偏差度量（BURN_STD F058A） |
| 期间封闭 | `PeriodClose` | 成本期间关闭（与回款子域「封期」同义，各 BC 内部用词一致） |
| 合同履约成本 / 成本 WIP | `CostWip` | 已发生、待与收入确认匹配的可资本化履约成本（成本/存货计量，收入确认时转主营业务成本）；全库唯一 WIP，与回款侧合同资产计量基准不同（IFRS 15 §95-98 / CAS 14） |
| 间接成本池 / 被分摊池 | `IndirectCostPool` | 汇集待两档分摊的间接（OH）成本的池（丢单商机费用、期间费用等入池；即「分摊」三义中的分摊源；物理表 psa_oh_cost_pool） |
| 结转批次 | `CarryBatch` | 一次成本结转的批量执行载体（台账行 → 成本 WIP → 凭证请求同批确认，支撑四方对账；carry_type 0/1/2/3/4，DRAFT→POSTED） |
| 费用类型一级分类 | `ExpenseRootCategory` | 成本行的根费用类别（root_category → 费用性质，由费用分类视图产出，A11） |
| 损益台账 | `PlLedger` | 成本转损益 / 红冲的损益侧只追加台账（psa_pl_ledger） |
| 分摊 | `AllocateOverhead` | 按分档判据把间接成本池摊到项目的执行动作（两档 OH 分摊；不过账、仅管理口径，B1） |
| 分摊结果 | `AllocationResult` | 分摊主行＋分摊行的生效结果（batch_type 试算/全量、status 草稿/生效，B4） |

### `receivables.wip` — WIP 与开票准入 BC

| 术语 | 代码名 | 定义 |
|---|---|---|
| WIP | `Wip` | 合同资产语义：累计履约进度收入 − 累计已开票（ADR-003） |
| 合同资产 | `WipAccount` | WIP 的准则名（CA，F113 滚动恒等式约束） |
| 履约进度 | `Poc` | 完工百分比（POC），封顶 [0,1]（F041） |
| 履约进度认定 | `ProgressAssessment` | 项目×合同×期间的计量认定单（证据必备、封期锁定） |
| 完工百分比 | `PercentageOfCompletion` | 履约进度的计量值 |
| 投入法 | `InputMethod` | 按累计投入占预计总投入的计量法（T&M 用工时、成本加酬金用成本，F040） |
| 产出法 | `OutputMethod` | 按里程碑/交付物完成的计量法（固定总价，F039） |
| 开票 | `InvoiceIssued` | 发票开具动作（开票 ≠ 收入，收入以 ProgressRecognized 为准） |
| 开票申请 | `BillingRequest` | 准入检查→开票→移交 AR 的流程载体 |
| 开票准入 | `InvoiceEligibilityConfirmed` | 按计价模式矩阵的硬性检查（通过才可开票） |
| T&M 期间账单 | `TmPeriodBill` | 期间可计费工时×费率的开票依据汇总（截止规则 Q20） |
| 无合同 WIP | `UncontractedWip` | NOCTR 状态的 WIP：永不可开票，待风险交付转正（F164） |
| 收入确认准备 | `ProgressRecognized` | 向法定核算提供的 REV_earned 事实 |
| 封期 / 期间调整 | `PeriodClose / PeriodAdjustment` | 期间封闭后更正走当期 ADJ 的权责发生制约束 |
| 工时捕获率 | `TimeCaptureRate` | T&M 可计费工时 vs 项目工时（HCR F030H） |
| 减值预警 | `WipImpairmentRiskRaised / ArImpairmentRiskRaised` | WIP/AR 侧的前置预警信号统称（区别于法定计提；两侧事件已拆分） |
| 竣工结算 | `SettlementRecord` | 完工后对最终交易价格的对账确认流程（ADR-010，F127E/F179 周期埋点） |
| 结算单 | `SettlementRecord` | 结算对账、金额与折扣确认的聚合载体 |
| 结算折扣 | `SettlementDiscount` | 结算确认中的减让（SETTLE_DISC，留痕不可改） |

### `receivables.collection` — AR 与现金转化 BC

| 术语 | 代码名 | 定义 |
|---|---|---|
| AR | `Ar` | 应收账款（发票之后的收款权） |
| 应收单 | `ArItem` | 一张发票一个应收载体（只能由 InvoiceIssued 产生） |
| 回款 | `CashReceived` | 客户付款到账的确认（CYCLE_B2C 终点） |
| 回款单 | `CashReceipt` | 一笔到账的认领与分配载体（分配合计=到账金额） |
| 到账认领 / 回款分配 | `ReceiptClaiming / ReceiptAllocation` | 到账与客户/应收的匹配与核销分配 |
| 核销 | `CashApplication` | 回款对应收余额的冲减（≤余额，必引用分配明细） |
| 坏账核销 | `ArWrittenOff` | 引用减值依据的应收终态处理（进 IMP_AR） |
| 账龄 / 账龄桶 | `Aging / AgingBucket` | 应收与 WIP 共用的分桶口径（0-30/30-90/90-180/180+） |
| 争议应收 | `DisputedReceivable` | 挂索赔案引用的争议标记（争议不停账龄） |
| 质保金 | `Retention` | 按合同质保条款拆分的独立应收类型（RET，F127C/D、F181） |
| 催收 / 回款计划 | `Collection / CollectionPlan` | 收款行动与计划管理 |
| 逾期应收 | `ArOverdueDetected` | 到期未收的账龄升级状态（ODAR F123） |
| ECL 预警 | `EclAlert` | 预期信用损失的前置信号（预警在本 BC、法定计提在核算） |
| 坏账风险 / 减值风险 | `BadDebtRisk / ImpairmentRisk` | 减值前置信号的统称（WIP/AR 两侧事件已拆分，主权唯一） |
| 现金转化 | `CashConversion` | 履约到现金的端到端转化（CCR F127） |
| 质保金到期 | `RetentionDue` | 质保期届满、质保金应收进入可回收状态 |

### `expense.spend` — 费用报销与支出管控 BC

| 术语 | 代码名 | 定义 |
|---|---|---|
| 费用申请 | `ExpenseRequest` | 预算校验与占用前置的支出申请（含借款、差旅形态） |
| 报销单 | `ExpenseClaim` | 引用已批申请（或政策豁免）的费用确认载体 |
| 借款 | `CashAdvance` | 单列借款单据：申请→放款→冲抵报销/还款→结清，未冲抵余额守卫（Q44 业主裁决） |
| 差旅 / 零星支出 | `TravelExpense / IncidentalExpense` | 费用类别 |
| 期间费用 | `PeriodExpense` | 判定为非项目直接的费用（进 OH） |
| 项目直接费用初判 | `ExpenseClassification` | 项目直接 vs 期间的归类判定（规则清单 Q26） |
| 费用标准 | `ExpensePolicy` | 类别/职级/城市档位的标准表（policy_param） |
| 费用合规 | `ExpenseCompliance` | 标准校验+发票凭证必备的合规约束 |
| 费用审批 | `ExpenseApproval` | 申请与报销的审批流转（守卫规则在本 BC，流转在平台） |
| 付款申请 | `PaymentRequest` | 引用已确认报销或已认应付的付款流程（ADR-002 暂留） |
| 支出记录 | `PaymentCompleted` | 付款完成的现金流出事实 |

### `procurement.subcontracting` — 分包与采购履约 BC

| 术语 | 代码名 | 定义 |
|---|---|---|
| 供应商 / 分包商 | `Supplier` | 外部资源主数据，评级达准入门槛才可下单 |
| 居间人 | `Intermediary` | 居间协议相对方（供应商主数据的一类，准入与合规管控更严） |
| 供应商评级 | `SupplierRating` | 准入与降级的评定（降级阻断新订单不影响存量） |
| 采购申请 | `PurchaseRequest` | 预算受控的采购需求（订单前置） |
| 询价 | `RequestForQuotation` | 投标期向候选分包/委外供应商的正式询价（报价外部成本分量的依据，Q49-③ 业主裁决） |
| 采购订单 | `PurchaseOrder` | 下单→履约→验收→应付→付款的载体（含分包合同形态） |
| 采购合同 | `ProcurementContract` | 与供应商的合同（与收入合同独立语义） |
| 采购价格 | `PurchasePrice` | 订单行计价 |
| 入库验收 / 服务验收 | `ProcurementAccepted` | 商务侧验收（应付成立的前置；与项目侧质量验收的先后关系待确认） |
| 应付账款 | `AccountsPayable` | 以验收定应付（不以发票定应付，权责发生制） |
| 分包进度 | `SubcontractProgress` | 分包履约进度事实（供交付侧消费） |
| 采购付款 | `ProcurementPaid` | 引用已登记应付的付款（ADR-002 暂留本 BC） |
| 委外 | `OrderType.SERVICE` | 服务类工作量的外部采购（设计/人力外包等，与工程分包并列的外部成本形态；Q49-① 业主裁决两术语同主权） |
| 居间协议 | `IntermediaryAgreement` | 为获取业务向居间人支付佣金的协议（合规敏感，独立聚合；签订须合规审查留痕，Q49-② 业主裁决） |
| 居间费用 | `IntermediaryFee` | 居间协议约定的佣金支出（报价全成本分量之一；应付登记须挂触发事实） |
| 分包合同 | `Subcontract` | 采购订单的分包形态（成果性专业分包） |

### `km.knowledge` — 知识资产与 AI 赋能 BC

| 术语 | 代码名 | 定义 |
|---|---|---|
| 知识资产 | `KnowledgeAsset` | 版本化、可检索、可评价的沉淀知识 |
| 资产版本 | `AssetVersion` | 只追加的修订历史（下架不删除） |
| 专业模板 / 标准方案 / 最佳实践 / 复盘经验 | `Template / StandardSolution / BestPractice / LessonLearned` | 知识资产的形态分类 |
| 知识标签 | `KnowledgeTag` | 发布前置的归类与适用范围 |
| 复用记录 | `ReuseRecord` | 引用资产版本与使用方的复用事实 |
| 知识有效性 | `EffectivenessRating` | 复用效果的评价等级 |
| AI 知识库 | `AiKnowledgeBase` | AI 建议生成的知识底座（敏感信息脱敏边界待确认） |
| AI 建议 | `AiSuggestion` | 必须留来源追溯才能采纳的 AI 输出（治理红线） |
| 知识贡献 / 知识沉淀 | `KnowledgeContribution / KnowledgeCapture` | 知识供给侧行为（是否进绩效记分卡待确认） |

### `organization.workforce` — 组织人事 BC

| 术语 | 代码名 | 定义 |
|---|---|---|
| 业务线 | `BusinessLine` | 五线：幕墙设计 CW / 照明设计 LT / 景观设计 LA / 第三方评估 AS / 项目管理 PM（GnG 规格书披露 2026-07-05；组织结构主数据） |
| 职级 | `JobGrade` | 员工职级 T1-T7 七级（组织主数据，计费费率与折扣权限的档位依据） |
| 法人主体 | `LegalEntity` | 法律实体主数据（主权归组织人事 BC，Q11 已确认 2026-07-04；核算持属性视图） |
| 员工 | `Employee` | 员工主数据与任职记录（EmployeeId 来源） |
| 组织架构 / 组织单元 | `OrgUnit` | 法人→BU→部门层级（组织单元必挂法人主体） |
| 岗位 / 职级 | `Position / JobGrade` | 任职与费率适用的岗级体系 |
| 任职 | `Assignment` | 员工与组织单元/岗位的关联记录（只追加） |
| 主任职 | `PrimaryAssignment` | 任一时点有且仅有一个的主要任职 |
| 兼任 | `SecondaryAssignment` | 显式标记、有起止的附加任职 |
| 人员状态 / 入转调离 | `EmploymentStatus / EmployeeLifecycle` | 员工生命周期（在职/停薪留职/离职） |
| 可用产能 | `EmployeeCapacityChanged` | 基础产能事实（本 BC 计算，产能变现分析归工时 BC） |

### `organization.payroll` — 假勤薪酬 BC

| 术语 | 代码名 | 定义 |
|---|---|---|
| 假勤 | `AttendanceConfirmed` | 出勤/休假/外勤/加班事实（外购承载，防腐层出 AttendanceConfirmed） |
| 薪酬 | `Payroll` | 薪酬核发（外购承载；个人明细不出防腐层） |
| 薪酬成本汇总 | `PayrollCostRecognized` | 法人×组织×期间×类别的脱敏汇总 |
| 出勤 / 休假 / 外勤 / 加班 | `Attendance / Leave / FieldWork / Overtime` | 假勤事实的类型（加班是否进有效工时口径待确认） |
| 薪酬期间 | `PayrollPeriod` | 薪酬核发的期间口径（与业务期间对齐） |
| 脱敏边界 | `DataMaskingBoundary` | 个人薪酬明细不出防腐层的合规红线 |

### `organization.incentive` — 绩效激励 BC

| 术语 | 代码名 | 定义 |
|---|---|---|
| 绩效 | `PerformanceAssessment` | 消费 BusinessFactAttributed 的评定结果（不重新认定事实） |
| 角色记分卡 | `Scorecard` | PM/BD/BU 的版本化权重卡（F147-149） |
| 闸门因子 | `GateFactor` | 一票否决项（GATE：红线类归零、黄线类封顶，因子分档 policy_param，Q43 业主裁决） |
| 配平系数 | `BalancingFactorK` | K = min(K_CAP, POOL/ΣRAW)，ΣBONUS ≤ 池（F141） |
| 递延奖金 | `DeferredBonus` | BONUS = DEF + PAID 的递延部分（F143/144） |
| 追索 | `Clawback` | CLAW ≤ 递延余额的责任回收（F145/146，劳动法边界待确认） |
| 奖惩 / 责任评价 | `RewardAndPenalty / AccountabilityReview` | 绩效结果的应用形态 |
| 绩效分数 | `PerformanceScore` | 记分卡逐分量加权的 SCORE（每分量须有事实引用） |
| 目标奖金 / 原始奖金 | `TargetBonus / RawBonus` | TARGET 与 RAW = TARGET × SCORE × GATE（F140） |
| 奖金池 | `BonusRun` | 配平约束的总额（POOL） |
| 当期发放 | `CurrentPayout` | PAID = BONUS − DEF（F144） |
| 递延余额 | `DeferredBalance` | 追索上限：DEF_NET = DEF − CLAW（F146） |

### `accounting.ledger` — 财务总账与法定核算 BC

| 术语 | 代码名 | 定义 |
|---|---|---|
| 会计凭证 | `JournalVoucher` | 可追溯来源业务事件的分录载体（外购系统承载，主权在本 BC） |
| 总账 / 科目 / 资产负债 / 利润表 / 财务报表 | `GeneralLedger / Account / BalanceSheet / IncomeStatement / FinancialStatement` | 法定账簿与报表对象（外购系统承载） |
| 会计期间 | `AccountingPeriodClosed` | 核算期间，关闭顺序强制业务期间先封 |
| 法定收入 | `StatutoryRevenueBooked` | 以 ProgressRecognized 为依据的准则收入（开票≠收入） |
| 法定成本 / 期末结转 | `StatutoryCost / PeriodEndClosing` | 准则口径的成本入账与结转 |
| 减值分录 | `ImpairmentBooked` | 实际计提口径（F116B，区别于业务侧预警口径） |
| 合规口径 | `BasisDifferenceList` | 法定口径与经营口径的差异清单（与经营口径与勾稽 BC 共维护） |
| 记账本位币 / 汇率折算 | `FunctionalCurrency / CurrencyTranslation` | 法人独立成账的币种基础与期末折算（汇率来源留痕） |
| 关联交易 / 内部往来 | `RelatedPartyTransaction / IntercompanyBalance` | 跨法人业务的核算侧表达（成对入账） |
| 内部往来对销 | `IntercompanyElimination` | 对销恒等：A 内部应收 = B 内部应付，差额不清零期间不关 |
| 内部结算 / 转移定价 | `IntercompanySettlement / TransferPricing` | 跨法人结算：成本加成模型（ADR-011），加成率为财务政策参数、跨境合规由税务把关 |
| 核算映射规则 | `AccountingMappingRuleSet` | 业务事件→凭证模板的版本化防腐层配置 |
| 期间关闭顺序 | `PeriodCloseSequence` | 工时→成本→WIP→经营→核算的强制封闭次序 |

### `platform.*` — 权限流程与集成平台 BC

| 术语 | 代码名 | 定义 |
|---|---|---|
| 用户 / 角色 / 权限 | `User / Role / Permission` | 技术权限（RBAC；业务授权语义归业务 BC） |
| 流程 / 流程实例 / 审批 | `Workflow / WorkflowInstance / Approval` | 审批流转承载（能不能批由业务 BC 判定） |
| 事件总线 | `EventBus` | 发布语言事件的投递承载 |
| 全局事件 ID | `GlobalEventId` | 跨 BC 事件必携标识（消费幂等的技术支撑） |
| 投递保证 | `DeliveryGuarantee` | 至少一次投递 + 事件不可篡改 + 可重放 |
| 数据集成 / 集成通道 | `DataIntegration / IntegrationChannel` | 外部系统对接的技术通道（映射规则归各业务 BC 防腐层） |
| 异常队列 | `DeadLetterQueue` | 映射失败消息的显式滞留处（不得静默丢弃） |
| 规则引擎 / 规则执行 | `RuleEngine / RuleExecution` | 只承载执行；规则定义与版本主权归业务 BC（红线） |
| 审计日志 | `AuditLog` | 只追加、不可篡改、可回放的变更轨迹 |
| 平台配置 | `PlatformConfig` | 平台自身的技术配置 |

