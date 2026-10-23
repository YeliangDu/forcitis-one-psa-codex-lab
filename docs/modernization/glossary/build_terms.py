#!/usr/bin/env python3
"""Build glossary/terms.yaml — machine-readable unified language (初版 SSOT) for the FORCITIS one modernization.

Inputs : glossary/source_terms.json (parsed 03-通用语言.md, 385 terms)
         + the 38 core terms of GLOSSARY.md §2 (CORE below, legacy names copied from build_glossary.py)
         + the standards audit in this file (AUDIT / NEW) — see STANDARDS_AUDIT.md for the G-8.. proposals.
         + 用户确认记录 glossary/decisions-*.json（按文件名排序全部读入并合并；各轮键前缀不同：第一轮 A- / B- / C-，第二轮 R2-，第三轮 R3- 与 ACL 归类 ACL-，
           新一轮用新前缀并在下方加该轮的应用段），glossary/oa_aliases.json、r0_report_aliases.json、round2-proposals.yaml、round2-source/*.yaml
Output : glossary/terms.yaml（不手改；build_all.py --check 重新生成并要求与已提交的一致）
Run    : cd docs/modernization && python3 glossary/build_terms.py   （或 python3 docs/modernization/build_all.py）
缺任何输入文件、或两份确认记录出现同一键时失败，不静默跳过。依赖 PyYAML。
"""
import json, re, collections, yaml, glob, os, sys

SRC = json.load(open("glossary/source_terms.json", encoding="utf-8"))

BC = {
    "经营事实与归属 BC": "operations.facts", "经营口径与勾稽 BC": "operations.policy",
    "合约基线 BC": "contract.baseline", "变更签证索赔 BC": "contract.changeorder", "风险交付 BC": "contract.riskdelivery",
    "项目交付执行 BC": "delivery.execution", "质量返工与验收 BC": "delivery.acceptance",
    "工时与投入有效性 BC": "cost.timesheet", "项目成本归集 BC": "cost.costing",
    "WIP 与开票准入 BC": "receivables.wip", "AR 与现金转化 BC": "receivables.collection",
    "财务总账与法定核算 BC": "accounting.ledger",
    # 非财务关键 BC：只收 38 个核心术语
    "商机评估与报价承诺 BC": "presales.quoting", "客户经营 BC": "presales.customer",
    "费用报销与支出管控 BC": "expense.spend", "组织人事 BC": "organization.workforce",
    "权限流程与集成平台 BC": "platform",
}
# KEY_BCS 与下方 A 表的键「bc:原代码」是旧资料（源术语表、审计覆盖）时代的模块标识，只用来取覆盖项，有意不跟着 ADR-0051 的退役改名；词条的最终 bc 由 rulings.yaml 的 move_bc 写定（P-059）
KEY_BCS = {"operations.facts", "operations.policy", "contract.baseline", "contract.changeorder", "contract.riskdelivery",
           "delivery.execution", "delivery.acceptance", "cost.timesheet", "cost.costing",
           "receivables.wip", "receivables.collection", "accounting.ledger", "accounting.period"}

# ---------- standard references (写到准则 / 标准名一级；条文号只在把握高时写) ----------
R = dict(
    BASIC="企业会计准则——基本准则（第九条 权责发生制）",
    CAS14="企业会计准则第 14 号——收入（财会〔2017〕22 号）",
    IFRS15="IFRS 15 Revenue from Contracts with Customers",
    CAS22="企业会计准则第 22 号——金融工具确认和计量（预期信用损失）",
    IFRS9="IFRS 9 Financial Instruments（expected credit losses）",
    CAS8="企业会计准则第 8 号——资产减值",
    CAS13="企业会计准则第 13 号——或有事项（亏损合同）",
    CAS19="企业会计准则第 19 号——外币折算", IAS21="IAS 21 The Effects of Changes in Foreign Exchange Rates",
    CAS28="企业会计准则第 28 号——会计政策、会计估计变更和差错更正", IAS8="IAS 8 Accounting Policies, Changes in Accounting Estimates and Errors",
    CAS30="企业会计准则第 30 号——财务报表列报", IAS1="IAS 1 Presentation of Financial Statements",
    CAS33="企业会计准则第 33 号——合并财务报表", IFRS10="IFRS 10 Consolidated Financial Statements",
    CAS36="企业会计准则第 36 号——关联方披露", IAS24="IAS 24 Related Party Disclosures",
    KJGF="会计基础工作规范（财政部）", CBHS="企业产品成本核算制度（试行）（财会〔2013〕17 号）",
    CIVIL="中华人民共和国民法典·合同编",
    XBRL="IFRS Accounting Taxonomy (ifrs-full)",
    PMBOK="PMI PMBOK Guide", EVM="PMI Practice Standard for Earned Value Management", WBSPS="PMI Practice Standard for Work Breakdown Structures",
    AACE="AACE International RP 10S-90 Cost Engineering Terminology",
    FIDIC="FIDIC Conditions of Contract for Construction 2017（Red Book）", FIDICW="FIDIC Client/Consultant Model Services Agreement 2017（White Book）",
    GB50500="GB/T 50500《建设工程工程量清单计价标准》（原 GB 50500-2013 规范）",
    GB50326="GB/T 50326-2017 建设工程项目管理规范", GB50358="GB/T 50358-2017 建设项目工程总承包管理规范",
    ZBJ="建设工程质量保证金管理办法（建质〔2017〕138 号）",
    ISO9000="GB/T 19000-2016 / ISO 9000:2015 质量管理体系 基础和术语",
    USCAS418="US Cost Accounting Standards 418（Allocation of direct and indirect costs）",
    PSA="PSA 行业通行写法（Kantata / Certinia / Deltek 等）",
    ADR16="ADR-0016 权责发生制业财一体主干",
    INTERNAL="FORCITIS 03-通用语言（内部概念，准则 / 国标无对应条目）",
)

def snake(s):
    s = re.sub(r"[^A-Za-z0-9]", "", s)
    return re.sub(r"(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])", "_", s).lower()

# ---------- audit overrides, keyed "bc:OldCode" ----------
# keys: en(new), zh(new), kind, refs(list of R keys or literal strings), conf, status, g(change id), aliases, forbidden, definition, note
A = {}
def a(key, **kw): A[key] = kw

# operations.facts / policy（权责发生制主干）
a("operations.facts:BusinessFact", kind="entity", refs=["BASIC", "ADR16"], conf="medium", note="权责发生制事实只追加（ADR-0016）")
a("operations.facts:FactEvidence", kind="value", refs=["KJGF"], conf="low")
a("operations.facts:BusinessFactRestated", kind="event", refs=["CAS28", "IAS8"], conf="low", note="准则「差错更正」是会计口径；此处为经营事实更正，只借用概念")
a("operations.facts:BusinessPeriod", kind="value", refs=["INTERNAL", "ADR16"], conf="medium", note="与 AccountingPeriod 的对齐关系见 G-16")
a("operations.policy:Reconciliation", kind="command", refs=["KJGF"], conf="medium")
a("operations.policy:OperatingAccountingTaxonomy", kind="entity", refs=["XBRL"], conf="medium")
a("operations.policy:BasisDifferenceList", kind="document", refs=["INTERNAL"], conf="high")

# contract.baseline（合同）
a("contract.baseline:Contract", kind="entity", refs=["CAS14", "IFRS15", "CIVIL"], conf="high", note="CAS 14 / IFRS 15 第一步「识别与客户订立的合同」；收入合同")
a("contract.baseline:ContractType", kind="value")
a("contract.baseline:ContractPeriod", kind="value")
a("contract.baseline:PaymentTermItem", kind="entity", refs=["CIVIL", "IFRS15"], conf="medium", note="IFRS 15 重大融资成分判断需读付款条件")
a("contract.baseline:ContractClause", kind="entity", refs=["CAS14", "IFRS15", "INTERNAL"], conf="low", g="G-21",
  note="条款是收入 / 成本 / 开票的挂接锚点，语义接近准则「履约义务」，又兼作付款节点；需财务确认二者关系，见 G-21",
  aliases=[("合同条款", "zh-internal", False), ("ContractTerm / ContractTermDO / termNumber", "legacy-code", True), ("psa_pm_contract_term / psa_contract_clause", "legacy-table", True), ("开票计划 / 开票计划节点", "zh-internal", False), ("BillingScheduleItem", "old-code", True)],
  forbidden=["ContractTerm", "Term", "BillingScheduleItem"])
a("contract.baseline:BillingScheduleItem", en="ContractClause", status="deprecated-alias",
  note="已并入 ContractClause（FIXED_LUMP_SUM 形态），见 03 词表 2026-07-07")
a("contract.baseline:PricingModel", kind="value", refs=["PSA", "AACE"], conf="medium")
a("contract.baseline:FixedPrice", kind="value", refs=["PSA", "AACE"], conf="high")
a("contract.baseline:TimeAndMaterials", kind="value", refs=["PSA", "IFRS15"], conf="high", note="IFRS 15 B16 「有权开票金额」实务简化常用于 T&M")
a("contract.baseline:CostPlus", kind="value", refs=["AACE"], conf="high")
a("contract.baseline:TmCap", kind="value", refs=["PSA"], conf="medium", aliases=[("Not-to-exceed（NTE）", "industry", False)])
a("contract.baseline:FrameworkAgreement", kind="entity", refs=["PSA"], conf="high")
a("contract.baseline:TaskOrderRegistered", en="TaskOrder", kind="entity", refs=["PSA"], conf="high", status="proposed", g="G-29",
  note="词表以事件名充当实体名；实体 TaskOrder，事件 TaskOrderRegistered 保留")
a("contract.baseline:ContractBaselined", en="ContractBaseline", kind="entity", conf="high", status="proposed", g="G-29",
  note="实体 ContractBaseline，事件 ContractBaselined 保留")
a("contract.baseline:RateCardItem", kind="entity", refs=["PSA"], conf="high")
a("contract.baseline:PerformanceCondition", kind="value", refs=["CIVIL"], conf="low")
a("contract.baseline:ContractScope", kind="entity", refs=["PMBOK"], conf="medium")
a("contract.baseline:ScopeItem", kind="entity", refs=["PMBOK"], conf="medium")
a("contract.baseline:ContractingLegalEntity", kind="role")
a("contract.baseline:CustomerParty", kind="role")
a("contract.baseline:ProjectOwnerParty", kind="role")

# contract.changeorder（变更签证索赔）
a("contract.changeorder:ChangeOrder", kind="document", refs=["GB50500", "FIDIC", "CAS14", "IFRS15"], conf="high",
  note="国标「工程变更」，FIDIC Cl.13 Variation；进入核算时对应 CAS 14 / IFRS 15「合同变更」(contract modification)",
  aliases=[("工程变更", "standard-zh", False), ("Variation（FIDIC）", "standard-en", False), ("ContractModification（IFRS 15 会计口径）", "standard-en", False)])
a("contract.changeorder:SupplementalAgreement", kind="document", refs=["CIVIL"], conf="medium")
a("contract.changeorder:SiteVisa", zh="现场签证", kind="document", refs=["GB50500"], conf="low", status="proposed", g="G-26",
  note="中文改用国标原文「现场签证」；英文 SiteVisa 为业主确认的中国 AEC 方言（03 词表），FIDIC 无对等术语，暂保留",
  aliases=[("签证", "zh-internal", False)])
a("contract.changeorder:SiteVisaConfirmed", zh="现场签证签认", kind="event", refs=["GB50500"], conf="low")
a("contract.changeorder:SiteVisaTimeWindow", zh="现场签证时效", kind="value", refs=["GB50500"], conf="low")
a("contract.changeorder:Claim", kind="document", refs=["GB50500", "FIDIC"], conf="high", note="FIDIC 2017 Cl.20 Employer's and Contractor's Claims")
a("contract.changeorder:ClaimTimeBar", kind="value", refs=["FIDIC"], conf="high")
a("contract.changeorder:ClaimAsserted", kind="event", refs=["FIDIC"], conf="medium")
a("contract.changeorder:ClaimSettled", kind="event", refs=["FIDIC"], conf="medium")
a("contract.changeorder:ImpactAssessment", kind="document", refs=["PMBOK"], conf="medium")
a("contract.changeorder:ScopeCreepIdentified", kind="event", refs=["PMBOK"], conf="high")
a("contract.changeorder:Penalty", en="LiquidatedDamages", zh="违约金", kind="value", refs=["CIVIL", "FIDIC", "CAS14", "IFRS15"], conf="medium",
  status="proposed", g="G-20", note="「罚款」在中文法律语境指行政处罚；合同扣罚为违约金（民法典第五百八十五条），FIDIC Cl.8.8 Delay Damages；核算上属可变对价",
  aliases=[("罚款", "zh-internal", True), ("Penalty", "old-code", True), ("PEN（KPI 符号）", "kpi-symbol", False)], forbidden=["Penalty", "Fine"])

# contract.riskdelivery
a("contract.riskdelivery:RiskExposure", en="UncontractedExposure", zh="无合同风险敞口", kind="metric", refs=["INTERNAL"], conf="medium",
  status="proposed", g="G-27", note="定义专指无合同敞口（NR+CA+AR，F127A），通用名 RiskExposure 与其它风险敞口歧义；且无合同时不存在准则口径合同资产，CA 段应为未开票在制",
  aliases=[("风险敞口", "zh-internal", False), ("RiskExposure", "old-code", True)])
a("contract.riskdelivery:UncontractedDelivery", kind="state", refs=["CAS14"], conf="medium", note="CAS 14 合同成立五条件未满足时不得确认收入")
a("contract.riskdelivery:EvidenceLevel", kind="value")
a("contract.riskdelivery:RiskType", kind="value")
a("contract.riskdelivery:RiskLevel", kind="value")
a("contract.riskdelivery:ResponsibleParty", kind="role")

# delivery.execution
a("delivery.execution:WbsNode", kind="entity", refs=["PMBOK", "WBSPS", "GB50326"], conf="high", aliases=[("BudgetClause / BudgetDeliverable", "legacy-code", True), ("psa_budget_*", "legacy-table", True)])
a("delivery.execution:WorkPackage", zh="工作包", kind="entity", refs=["PMBOK", "WBSPS"], conf="high", status="proposed", g="G-22",
  note="PMBOK 中文版用「工作包」（WBS 最底层）；「任务包」降为别名",
  aliases=[("任务包", "zh-internal", False), ("TaskPackage / task_package", "legacy-code", True), ("psa_task_package", "legacy-table", True)], forbidden=["TaskPackage"])
a("delivery.execution:DeliveryPackage", kind="entity", refs=["INTERNAL"], conf="low", g="G-22", note="与 WorkPackage 是否同一概念待业务确认（验收对象类型含交付包）")
a("delivery.execution:Deliverable", kind="entity", refs=["PMBOK", "FIDICW"], conf="high")
a("delivery.execution:PlanBaseline", kind="entity", refs=["PMBOK"], conf="high", note="PMBOK performance measurement baseline")
a("delivery.execution:Milestone", kind="entity", refs=["PMBOK"], conf="high")
a("delivery.execution:EacAssessment", kind="document", refs=["PMBOK", "AACE"], conf="high", note="EAC = AC + ETC 与 PMBOK 一致；值对象另立 EstimateAtCompletion（G-23）")
a("delivery.execution:EstimateToComplete", kind="metric", refs=["PMBOK", "AACE"], conf="high", aliases=[("etcHours", "legacy-code", True), ("psa_task_package.etc_hours", "legacy-table", True)])
a("delivery.execution:ProgressDeviationIdentified", en="ScheduleVarianceIdentified", zh="进度偏差识别", kind="event", refs=["PMBOK", "EVM"], conf="high",
  status="proposed", g="G-23", note="定义即 EVM 的 SV / SPI，用 PMBOK 原词", aliases=[("ProgressDeviationIdentified", "old-code", True)])
a("delivery.execution:DeliveryVariance", kind="metric")
a("delivery.execution:PostCompletionServiceOrder", kind="document", refs=["ZBJ"], conf="low", note="质保期服务；国标称缺陷责任期 / 保修期，见 G-17")
a("delivery.execution:DeliveryConfirmation", kind="document")
a("delivery.execution:MilestoneCompletionSubmitted", kind="event")

# delivery.acceptance
a("delivery.acceptance:AcceptanceRecord", kind="document", refs=["GB50326", "FIDIC"], conf="medium",
  note="最终验收对应国标「竣工验收」、FIDIC Cl.10 Taking-Over；收入确认触发点（ACC-R01 客户价值确认单）",
  aliases=[("DeliverableSubmission(DLV_ACCEPTED)", "legacy-code", True), ("psa_deliverable_submission", "legacy-table", True)])
a("delivery.acceptance:ConcessionAcceptance", kind="value", refs=["ISO9000"], conf="high")
a("delivery.acceptance:ConditionalPass", kind="value")
a("delivery.acceptance:QualityIssue", kind="entity", refs=["ISO9000"], conf="medium", note="ISO 9000 nonconformity")
a("delivery.acceptance:Rework", kind="value", refs=["ISO9000"], conf="high")
a("delivery.acceptance:ReworkRate", kind="metric")
a("delivery.acceptance:DefectRate", kind="metric")
a("delivery.acceptance:QualityLoss", kind="metric", refs=["INTERNAL"], conf="medium")
a("delivery.acceptance:AcceptanceStatus", kind="state")
a("delivery.acceptance:AcceptanceTargetType", kind="value")

# cost.timesheet
a("cost.timesheet:TimeEntry", kind="entity", refs=["PSA"], conf="high",
  aliases=[("ManHourItemDO / works", "legacy-code", True), ("psa_time_sheet_works", "legacy-table", True), ("TimesheetLine", "old-code", True)],
  forbidden=["ManHour", "ManHourItem", "Works", "WorkHour", "TimesheetLine"])
a("cost.timesheet:TimesheetLine", en="TimeEntry", status="deprecated-alias", g="G-2")
a("cost.timesheet:Timesheet", kind="document", refs=["PSA"], conf="high",
  aliases=[("ManHourDO / TimeSheet", "legacy-code", True), ("psa_time_sheet", "legacy-table", True)], forbidden=["ManHour", "TimeSheet", "WorkHour"])
a("cost.timesheet:BillableHours", kind="metric", refs=["PSA"], conf="high")
a("cost.timesheet:EffectiveHours", kind="metric")
a("cost.timesheet:IneffectiveHours", kind="metric")
a("cost.timesheet:ReworkHours", kind="metric")
a("cost.timesheet:CapacityRealization", kind="metric", refs=["INTERNAL", "PSA"], conf="low", note="行业近义指标为 Utilization（利用率），口径不同不合并")
a("cost.timesheet:IdleCapacity", kind="metric", refs=["CBHS"], conf="low")
a("cost.timesheet:SubmissionDeadline", kind="value")

# cost.costing（成本）
a("cost.costing:ProjectCost", kind="metric", refs=["CBHS"], conf="medium",
  aliases=[("CostLedgerDO / cost_summary", "legacy-code", True), ("psa_cost_ledger / psa_cost_summary", "legacy-table", True)])
a("cost.costing:CostEntry", kind="entity", refs=["CBHS"], conf="medium")
a("cost.costing:DirectLabor", kind="metric", refs=["CBHS", "AACE"], conf="high")
a("cost.costing:DirectExpense", kind="metric", refs=["CBHS"], conf="medium")
a("cost.costing:SubcontractCost", kind="metric", refs=["CBHS", "FIDIC"], conf="medium")
a("cost.costing:OutsourcingCost", kind="metric", refs=["INTERNAL"], conf="medium")
a("cost.costing:CostAllocated", en="CostAccumulated", zh="成本归集", kind="event", refs=["CBHS", "AACE"], conf="high", status="proposed", g="G-18",
  note="「归集」= accumulation；allocation 是「分配 / 分摊」，与 AllocateOverhead 冲突", aliases=[("CostAllocated", "old-code", True)], forbidden=["CostAllocated"])
a("cost.costing:CostAdjustment", kind="entity", refs=["CAS28"], conf="low")
a("cost.costing:StandardCostRateCard", kind="entity", refs=["CBHS", "PSA"], conf="high", g="G-3")
a("cost.costing:RateVersion", en="RateCardVersion", kind="entity", refs=["PSA"], conf="high", status="proposed", g="G-3", aliases=[("RateVersion", "old-code", True)])
a("cost.costing:CostBudget", kind="metric", refs=["PMBOK"], conf="high")
a("cost.costing:CostVariance", en="CostBudgetVariance", zh="成本预算偏差", kind="metric", refs=["PMBOK", "EVM"], conf="high", status="proposed", g="G-24",
  note="PMBOK 的 CostVariance 专指 EVM 的 CV = EV − AC；本词定义是预算 vs 实际（BURN_STD），换名避免与 EVM 指标同名异义",
  aliases=[("CostVariance", "old-code", True)])
a("cost.costing:PeriodClose", en="PeriodCloseReadiness", zh="成本关账前置检查", kind="document", refs=["ADR16", "KJGF"], conf="medium", status="proposed", g="G-16",
  note="期间状态只归 accounting.period；成本侧只提供关账前置检查结论", aliases=[("期间封闭 / 封期", "zh-internal", True), ("PeriodProgress / next_step", "legacy-code", True), ("psa_period_progress / psa_carry_period_progress", "legacy-table", True)])
a("cost.costing:CostWip", en="ContractFulfilmentCost", zh="合同履约成本", kind="entity", refs=["CAS14", "IFRS15", "ifrs-full:AssetsRecognisedFromCostsToObtainOrFulfilContractsWithCustomers"],
  conf="medium", status="proposed", g="G-10",
  note="准则科目名「合同履约成本」（IFRS 15 §95-98 costs to fulfil a contract；ACC-R12 要求 1/2 级科目用准则原词）；与回款侧未开票在制同名异物（09-业务规则 §7 警示），代码禁用裸 Wip",
  aliases=[("成本 WIP / 在制成本", "zh-internal", True), ("CostWip", "old-code", True), ("WipEntry / WipCostAccount", "legacy-code", True), ("psa_wip_entry / psa_wip_movement / psa_wip_period_balance / psa_wip_cost_account", "legacy-table", True)],
  forbidden=["CostWip", "Wip", "WipEntry"])
a("cost.costing:IndirectCostPool", kind="entity", refs=["CBHS", "USCAS418"], conf="high", g="G-28",
  aliases=[("OH 池 / 被分摊池", "zh-internal", True), ("OhCostPool", "legacy-code", True), ("psa_oh_cost_pool", "legacy-table", True)], forbidden=["OhPool", "OhCostPool"])
a("cost.costing:AllocateOverhead", en="AllocateIndirectCost", zh="间接成本分摊", kind="command", refs=["CBHS", "USCAS418"], conf="medium", status="proposed", g="G-28",
  note="与 IndirectCostPool 同一词族；OH 只作别名。分摊仅管理口径、不过账（COST-R42）",
  aliases=[("OH 分摊", "zh-internal", True), ("AllocateOverhead", "old-code", True), ("OhAllocationCalculator", "legacy-code", True), ("psa_cost_allocation* / psa_oh_allocation_*", "legacy-table", True)], forbidden=["Oh", "OhAllocation"])
a("cost.costing:AllocationResult", kind="entity", refs=["CBHS"], conf="medium")
a("cost.costing:CarryBatch", en="CostTransferBatch", zh="成本结转批次", kind="entity", refs=["CBHS"], conf="medium", status="proposed", g="G-14",
  note="「结转」= transfer；carry / carry forward 在会计英语中指余额结转下期，语义不符",
  aliases=[("结转批次", "zh-internal", False), ("CarryBatch / CarryForwardData", "old-code", True), ("psa_carry_batch* / psa_wip_carry_forward_*", "legacy-table", True)], forbidden=["Carry", "CarryForward", "CarryBatch"])
a("cost.costing:PlLedger", en="ProfitOrLossLedger", zh="损益台账", kind="entity", refs=["IAS1", "CAS30"], conf="medium", status="proposed", g="G-15",
  note="Pl 是自造缩写；IAS 1 原文 profit or loss", aliases=[("PlLedger / PlLedgerDO", "old-code", True), ("psa_pl_ledger*", "legacy-table", True)], forbidden=["Pl", "PnL"])
a("cost.costing:PendingIntercompanySettlement", kind="state")
a("cost.costing:ExpenseRootCategory", kind="value")

# receivables.wip（收入 / 合同资产）
a("receivables.wip:Wip", en="UnbilledWip", zh="未开票在制（WIP）", kind="metric", refs=["PSA", "INTERNAL"], conf="medium", status="proposed", g="G-9",
  definition="管理口径：累计履约进度收入 − 累计已开票，按项目 × 合同条款计量，含无合同部分；不是准则科目",
  note="WIP 是 PSA 行业管理口径（未开票价值），合同资产是准则口径；两者差异 = 无合同 WIP + 合同负债抵消 + 减值，见 G-8/G-9",
  aliases=[("WIP", "zh-internal", False), ("Wip", "old-code", True)], forbidden=["Wip"])
a("receivables.wip:WipAccount", en="ContractAsset", zh="合同资产", kind="entity", refs=["CAS14", "IFRS15", "ifrs-full:ContractAssets"], conf="high", status="proposed", g="G-8",
  definition="准则口径：企业已向客户转让商品或服务而有权收取对价、且该权利取决于时间流逝之外因素的权利；按合同与合同负债净额列示",
  note="IFRS 15 §105-109；与应收账款（无条件收款权）区分；预期信用损失按 CAS 22 / IFRS 9 计提",
  aliases=[("CA（KPI 符号，F113）", "kpi-symbol", False), ("WipAccount", "old-code", True), ("psa_pm_contract_asset_impairment", "legacy-table", True)], forbidden=["WipAccount"])
a("receivables.wip:Poc", en="MeasureOfProgress", zh="履约进度", kind="metric", definition="履约义务完成程度的计量值，取值 [0,1]（F041），按投入法或产出法计量", refs=["CAS14", "IFRS15"], conf="medium", status="proposed", g="G-13",
  note="「完工百分比」是已废止的 IAS 11 / CAS 15 建造合同用语；CAS 14 / IFRS 15 §39-45 称履约进度。POC 保留为 KPI 符号别名",
  aliases=[("完工百分比 / POC", "zh-internal", True), ("Poc / PercentageOfCompletion / progress", "old-code", True), ("psa_pm_project.progress_pro", "legacy-table", True)], forbidden=["Poc", "PercentageOfCompletion"])
a("receivables.wip:PercentageOfCompletion", en="MeasureOfProgress", status="deprecated-alias", g="G-13")
a("receivables.wip:ProgressAssessment", kind="document", refs=["CAS14", "IFRS15"], conf="medium", note="履约进度认定单")
a("receivables.wip:InputMethod", kind="value", refs=["CAS14", "IFRS15"], conf="high", note="IFRS 15 B18-B19")
a("receivables.wip:OutputMethod", kind="value", refs=["CAS14", "IFRS15"], conf="high", note="IFRS 15 B15-B17；ACC-R01 定稿收入确认统一为产出法·客户价值确认，与词表「T&M 用投入法」冲突待财务确认")
a("receivables.wip:ProgressRecognized", en="RevenueRecognition", zh="收入确认单", kind="document", refs=["CAS14", "IFRS15", "ifrs-full:RevenueFromContractsWithCustomers"], conf="medium",
  status="approved", g="G-12",
  definition="凭客户确认（验收单、邮件确认、签字日期）按合同条款确认收入的单据；审批通过即确认收入，收入期间取客户签字日所在月；法定入账另为 StatutoryRevenueBooked",
  note="用户 2026-10-14 裁定：「产值确认单就是收入确认单，系统用收入确认这个语言，现在的叫法可以归为方言」。ACC-R01：客户价值确认是确认收入的唯一触发点。事件名 RevenueRecognized",
  aliases=[("产值确认单 / 产值", "dialect", True), ("收入确认准备", "zh-internal", True), ("ProgressRecognized", "old-code", True), ("CuValueDO / RevenueConfirmation / RevenueFact", "legacy-code", True), ("psa_pm_cuvalue / psa_revenue_confirmation / psa_revenue_fact", "legacy-table", True)],
  forbidden=["CuValue", "Cuvalue", "OutputValue", "ProgressRecognized", "产值", "产值确认单"])
a("receivables.wip:InvoiceIssued", kind="event", refs=["CAS14"], conf="medium", note="开票 ≠ 收入；开票时合同资产转应收账款（或冲合同负债）")
a("receivables.wip:BillingRequest", kind="document", refs=["PSA"], conf="high", aliases=[("ProjectBillingView", "legacy-code", True)])
a("receivables.wip:InvoiceEligibilityConfirmed", kind="event")
a("receivables.wip:TmPeriodBill", kind="document", refs=["PSA"], conf="medium")
a("receivables.wip:UncontractedWip", kind="metric", refs=["CAS14", "INTERNAL"], conf="medium", g="G-9",
  note="无合同即不满足 CAS 14 合同成立条件：永不进入 ContractAsset，只存在于管理口径未开票在制")
a("receivables.wip:PeriodClose", en="PeriodCloseReadiness", status="deprecated-alias", g="G-16")
a("receivables.wip:PeriodAdjustment", kind="entity", refs=["BASIC", "ADR16"], conf="medium")
a("receivables.wip:TimeCaptureRate", kind="metric", refs=["PSA"], conf="medium")
a("receivables.wip:WipImpairmentRiskRaised", en="ContractAssetImpairmentRiskRaised", kind="event", refs=["CAS22", "IFRS9", "CAS8"], conf="medium", status="proposed", g="G-8",
  aliases=[("WipImpairmentRiskRaised", "old-code", True)])
a("receivables.wip:ArImpairmentRiskRaised", kind="event", refs=["CAS22", "IFRS9"], conf="medium")
a("receivables.wip:SettlementRecord", en="FinalAccount", zh="竣工结算", kind="document", refs=["GB50500", "FIDIC"], conf="medium", status="proposed", g="G-19",
  note="国标「竣工结算」，FIDIC Cl.14.11 Final Statement / Final Payment Certificate；Settlement 在英语里多指款项结清，且与 ClaimSettled / IntercompanySettlement 冲突",
  aliases=[("结算单", "zh-internal", False), ("SettlementRecord", "old-code", True)], forbidden=["SettlementRecord"])
a("receivables.wip:SettlementDiscount", en="FinalAccountDiscount", zh="结算折让", kind="value", refs=["CAS14"], conf="low", status="proposed", g="G-19",
  note="核算上属可变对价 / 交易价格调整", aliases=[("SettlementDiscount", "old-code", True)])

# receivables.collection（应收）
a("receivables.collection:Ar", en="AccountsReceivable", zh="应收账款", kind="metric", refs=["CAS14", "CAS22", "ifrs-full:TradeReceivables"], conf="medium",
  note="准则：无条件收款权（仅取决于时间流逝）；代码保留 Ar 前缀写法 ArItem", aliases=[("AR", "abbr", False), ("Ar", "old-code", False)])
a("receivables.collection:ArItem", kind="entity", refs=["CAS14"], conf="high")
a("receivables.collection:CashReceived", kind="event")
a("receivables.collection:CashReceipt", kind="document", refs=["PSA"], conf="high")
a("receivables.collection:CashApplication", kind="command", refs=["PSA"], conf="high")
a("receivables.collection:ArWrittenOff", kind="event", refs=["CAS22", "IFRS9"], conf="high")
a("receivables.collection:Aging", kind="metric", refs=["PSA"], conf="high")
a("receivables.collection:AgingBucket", kind="value", refs=["PSA"], conf="high")
a("receivables.collection:DisputedReceivable", kind="state")
a("receivables.collection:Retention", en="RetentionMoney", zh="质量保证金", kind="entity", refs=["ZBJ", "GB50500", "FIDIC", "CAS14"], conf="high", status="proposed", g="G-17",
  note="国标全称「质量保证金」，FIDIC Cl.14 Retention Money；裸 Retention 与 java.lang.annotation.Retention、日志保留等冲突。未到期质保金在准则上通常属合同资产而非应收账款，待财务确认",
  aliases=[("质保金", "zh-internal", False), ("Retention / RET", "old-code", True)], forbidden=["Retention"])
a("receivables.collection:RetentionDue", en="RetentionMoneyReleaseDue", zh="质量保证金到期返还", kind="event", refs=["ZBJ", "FIDIC"], conf="medium", status="proposed", g="G-17",
  note="期间称「缺陷责任期」（建质〔2017〕138 号），「质保期」为别名", aliases=[("质保金到期", "zh-internal", True), ("RetentionDue", "old-code", True)])
a("receivables.collection:ArOverdueDetected", kind="event")
a("receivables.collection:EclAlert", kind="event", refs=["CAS22", "IFRS9"], conf="high")
a("receivables.collection:BadDebtRisk", kind="metric", refs=["CAS22"], conf="medium")
a("receivables.collection:ImpairmentRisk", kind="metric", refs=["CAS22", "CAS8"], conf="medium")
a("receivables.collection:Collection", kind="command", refs=["PSA"], conf="high")
a("receivables.collection:CollectionPlan", kind="document", refs=["PSA"], conf="high")
a("receivables.collection:CashConversion", kind="metric")
a("receivables.collection:ReceiptClaiming", kind="command")
a("receivables.collection:ReceiptAllocation", kind="entity")

# accounting.ledger
a("accounting.ledger:JournalVoucher", kind="document", refs=["KJGF"], conf="high", aliases=[("VoucherDO / U8cVoucherDO", "legacy-code", True), ("psa_voucher* / psa_u8c_voucher", "legacy-table", True)])
a("accounting.ledger:GeneralLedger", kind="entity", refs=["KJGF"], conf="high")
a("accounting.ledger:Account", kind="entity", refs=["KJGF"], conf="high", note="会计科目；1/2 级科目用准则原词（ACC-R12）")
a("accounting.ledger:BalanceSheet", kind="document", refs=["CAS30", "IAS1"], conf="high", note="IAS 1 称 statement of financial position")
a("accounting.ledger:IncomeStatement", kind="document", refs=["CAS30", "IAS1"], conf="high", note="CAS 30 利润表；IAS 1 statement of profit or loss")
a("accounting.ledger:FinancialStatement", kind="document", refs=["CAS30", "IAS1"], conf="high")
a("accounting.ledger:AccountingPeriodClosed", kind="event", refs=["KJGF", "ADR16"], conf="high", note="实体 AccountingPeriod 移入 accounting.period（G-4、G-16）")
a("accounting.ledger:StatutoryRevenueBooked", kind="event", refs=["CAS14", "IFRS15"], conf="high")
a("accounting.ledger:StatutoryCost", kind="metric", refs=["CAS30", "ifrs-full:CostOfSales"], conf="medium")
a("accounting.ledger:PeriodEndClosing", kind="command", refs=["KJGF"], conf="medium")
a("accounting.ledger:ImpairmentBooked", kind="event", refs=["CAS8", "CAS22"], conf="high")
a("accounting.ledger:BasisDifferenceList", kind="document")
a("accounting.ledger:FunctionalCurrency", kind="value", refs=["CAS19", "IAS21"], conf="high")
a("accounting.ledger:CurrencyTranslation", kind="command", refs=["CAS19", "IAS21"], conf="high")
a("accounting.ledger:RelatedPartyTransaction", kind="entity", refs=["CAS36", "IAS24"], conf="high")
a("accounting.ledger:IntercompanyBalance", kind="metric", refs=["CAS33", "IFRS10"], conf="high")
a("accounting.ledger:IntercompanyElimination", zh="内部往来抵销", kind="command", refs=["CAS33", "IFRS10"], conf="high", status="proposed", g="G-25",
  note="CAS 33 原文用「抵销」，不用「对销」", aliases=[("内部往来对销", "zh-internal", True)])
a("accounting.ledger:IntercompanySettlement", kind="command")
a("accounting.ledger:TransferPricing", kind="value", refs=["IAS24"], conf="medium")
a("accounting.ledger:AccountingMappingRuleSet", kind="entity")
a("accounting.ledger:PeriodCloseSequence", kind="value", refs=["ADR16"], conf="medium")

# ---------- 新增（proposed）术语：准则 / 国标 / EVM 中缺位的标准概念 ----------
NEW = [
 # id-en, zh, kind, bc, definition, refs, conf, g, aliases, forbidden
 ("ContractLiability", "合同负债", "entity", "receivables.wip", "企业已收或应收客户对价而应向客户转让商品或服务的义务（预收款、超额开票），按合同与合同资产净额列示",
  ["CAS14", "IFRS15", "ifrs-full:ContractLiabilities"], "high", "G-11", [("预收款 / 递延收入", "zh-internal", True), ("AdvanceReceipt / DeferredRevenue", "old-code", True)], ["DeferredRevenue", "AdvanceReceipt"]),
 ("PerformanceObligation", "履约义务", "entity", "contract.baseline", "合同中向客户转让可明确区分商品或服务的承诺，收入确认的计量单元",
  ["CAS14", "IFRS15"], "high", "G-21", [("合同条款（若财务确认等同）", "zh-internal", False)], []),
 ("TransactionPrice", "交易价格", "value", "contract.baseline", "企业因向客户转让商品或服务而预期有权收取的对价金额，含可变对价估计",
  ["CAS14", "IFRS15"], "high", "G-21", [("合同金额 / 最终交易价格", "zh-internal", False)], []),
 ("VariableConsideration", "可变对价", "value", "contract.baseline", "交易价格中随折让、违约金、奖励、索赔结果等变动的部分",
  ["CAS14", "IFRS15"], "high", "G-20", [], []),
 ("ContractModification", "合同变更（会计口径）", "event", "contract.changeorder", "经批准的合同范围或价格变更在收入核算上的处理事实（单独合同 / 原合同终止新立 / 累积追加调整）",
  ["CAS14", "IFRS15"], "high", "G-21", [], []),
 ("CostsToObtainContract", "合同取得成本", "metric", "cost.costing", "为取得合同发生的增量成本（如居间佣金），预期可收回时确认为资产",
  ["CAS14", "IFRS15", "ifrs-full:AssetsRecognisedFromCostsToObtainOrFulfilContractsWithCustomers"], "medium", "G-10", [], []),
 ("SatisfiedOverTime", "时段履约", "value", "receivables.wip", "在某一时段内履行的履约义务，按履约进度确认收入",
  ["CAS14", "IFRS15"], "high", "G-13", [], []),
 ("SatisfiedAtPointInTime", "时点履约", "value", "receivables.wip", "在某一时点履行的履约义务，控制权转移时确认收入",
  ["CAS14", "IFRS15"], "high", "G-13", [], []),
 ("RevenueRecognized", "收入已确认", "event", "receivables.wip", "收入确认单生效的事实事件（替代 ProgressRecognized 事件名）",
  ["CAS14", "IFRS15"], "medium", "G-12", [("ProgressRecognized", "old-code", True)], ["ProgressRecognized"]),
 ("CostOfSales", "营业成本", "metric", "cost.costing", "随收入确认由合同履约成本转出的成本（原「主营业务成本」）",
  ["CAS30", "ifrs-full:CostOfSales"], "medium", "G-10", [("主营业务成本 / 主营成本", "zh-internal", False)], []),
 ("RemainingPerformanceObligation", "剩余履约义务", "metric", "contract.baseline", "已签约未履约部分分摊的交易价格（行业称在手合同额 / backlog）",
  ["CAS14", "IFRS15", "ifrs-full:TransactionPriceAllocatedToRemainingPerformanceObligations"], "medium", "G-21", [("在手合同额 / Backlog", "industry", False)], []),
 ("OnerousContract", "亏损合同", "state", "delivery.execution", "履行合同义务不可避免的成本超过预期经济利益的合同",
  ["CAS13", "IAS 37 Provisions, Contingent Liabilities and Contingent Assets"], "high", "G-23", [], []),
 ("EstimateAtCompletion", "完工估算", "metric", "delivery.execution", "EAC = 实际成本 AC + 完工尚需估算 ETC", ["PMBOK", "AACE"], "high", "G-23", [("EAC", "abbr", False)], []),
 ("PlannedValue", "计划价值", "metric", "delivery.execution", "按基线截至某时点计划完成工作的预算价值（PV）", ["PMBOK", "EVM", "GB50358"], "high", "G-23", [("PV", "abbr", False)], []),
 ("EarnedValue", "挣值", "metric", "delivery.execution", "截至某时点已完成工作的预算价值（EV；GB/T 50358 称赢得值）", ["PMBOK", "EVM", "GB50358"], "high", "G-23", [("赢得值 / EV", "standard-zh", False)], []),
 ("ActualCost", "实际成本", "metric", "delivery.execution", "截至某时点已完成工作的实际发生成本（AC）", ["PMBOK", "EVM"], "high", "G-23", [("AC", "abbr", False)], []),
 ("CostPerformanceIndex", "成本绩效指数", "metric", "delivery.execution", "CPI = EV / AC", ["PMBOK", "EVM"], "high", "G-23", [("CPI", "abbr", False)], []),
 ("SchedulePerformanceIndex", "进度绩效指数", "metric", "delivery.execution", "SPI = EV / PV", ["PMBOK", "EVM"], "high", "G-23", [("SPI", "abbr", False)], []),
 ("ScheduleVariance", "进度偏差", "metric", "delivery.execution", "SV = EV − PV", ["PMBOK", "EVM"], "high", "G-23", [("SV", "abbr", False)], []),
 ("EarnedValueCostVariance", "挣值成本偏差", "metric", "delivery.execution", "EVM 的 CV = EV − AC（与成本预算偏差 CostBudgetVariance 区分）", ["PMBOK", "EVM"], "high", "G-24", [("CV", "abbr", False)], ["CostVariance"]),
 ("DefectsLiabilityPeriod", "缺陷责任期", "value", "receivables.collection", "质量保证金扣留与返还所对应的期间（区别于法定保修期）",
  ["ZBJ", "FIDIC"], "high", "G-17", [("质保期", "zh-internal", True), ("Defects Notification Period（FIDIC）", "standard-en", False)], ["WarrantyPeriod"]),
 ("InterimPaymentCertificate", "期中支付证书", "document", "receivables.wip", "按合同对阶段完成工作的进度款确认（国标称进度款支付）",
  ["FIDIC", "GB50500"], "medium", "G-19", [("进度款", "standard-zh", False), ("IPC", "abbr", False)], []),
 ("AccountingPeriod", "会计期间", "entity", "accounting.period", "按（期间, 法人）粒度的期间状态载体，只有会计期间 BC 能写", ["KJGF", "ADR16"], "high", "G-4",
  [("psa_accounting_period", "legacy-table", True)], []),
 ("PeriodStatus", "期间状态", "state", "accounting.period", "OPEN / CLOSING / CLOSED（重开政策待财务确认）", ["ADR16"], "medium", "G-16", [], []),
 ("PeriodClose", "期末关账", "command", "accounting.period", "一组前置检查（各 BC 关账前置检查结论）加一次期间状态转换", ["KJGF", "ADR16"], "medium", "G-16",
  [("期间封闭 / 封期 / 结账", "zh-internal", False)], []),
 ("ReopenAccountingPeriod", "期间重开", "command", "accounting.period", "已关账期间的受控重开（政策待财务确认）", ["ADR16"], "low", "G-16", [], []),
 ("PostingCandidate", "过账候选", "value", "operations.facts", "触账 BC 向单一过账管道提交的类型化候选分录", ["ADR16"], "high", "G-16", [], []),
 ("ReversingEntry", "冲销", "entity", "operations.facts", "生成反向事实与新序号的更正方式，原行保留并双向关联", ["KJGF", "ADR16"], "medium", "G-16",
  [("红冲 / 红字冲销", "zh-internal", False), ("RedReversal / revenueRedReversal", "legacy-code", True)], ["RedReversal", "Hongchong"]),
 ("RateCard", "费率卡", "entity", "contract.baseline", "合同约定的计费费率集合（RateCardItem 的聚合）", ["PSA"], "high", "G-3",
  [("BillRateDO / HourlyRateDO", "legacy-code", True), ("psa_bill_rate* / psa_pm_hourlyrate*", "legacy-table", True)], ["HourlyRate", "BillRateTable"]),
 ("BillRateCard", "计费费率卡", "entity", "contract.baseline", "对客计费费率卡（与成本费率卡 StandardCostRateCard 对称）", ["PSA"], "high", "G-3", [], []),
 ("UtilizationRate", "利用率", "metric", "cost.timesheet", "可计费工时 / 可用工时（PSA 通行指标）", ["PSA"], "high", "G-2", [("Utilization", "industry", False)], []),
]

# ---------- 38 核心术语（GLOSSARY.md §2） ----------
# (中文, 代码名, BC path, legacy code, legacy tables)
CORE = [
 ("报价单", "Quote", "presales.quoting", "Quotation / QuotationDO", "psa_quotation, psa_quotation_line, psa_quotation_seal"),
 ("成本估算树", "CostEstimateNode", "presales.quoting", "CostEstimateNode", "psa_cost_estimate_*"),
 ("工时估算明细", "LaborEstimateLine", "presales.quoting", "ManhourEstimateItem", ""),
 ("商机", "Opportunity", "presales.quoting", "OpportunityDO, OppCloseStatus", "psa_crm_opportunity"),
 ("客户", "Customer", "presales.customer", "CustomerDO", "psa_bd_customer"),
 ("费率卡", "RateCard / RateCardItem", "contract.baseline", "BillRateDO, HourlyRateDO", "psa_bill_rate*, psa_pm_hourlyrate*"),
 ("合同条款", "ContractClause", "contract.baseline", "ContractTermDO, termNumber, clause", "psa_pm_contract_term, psa_contract_clause"),
 ("项目", "Project", "delivery.execution", "PmProjectDO, ProjectDO", "psa_pm_project"),
 ("WBS 节点", "WbsNode", "delivery.execution", "BudgetClause/BudgetDeliverable", "psa_budget_*"),
 ("任务包", "WorkPackage", "delivery.execution", "TaskPackage, task_package, workPackage", "psa_task_package"),
 ("交付物", "Deliverable", "delivery.execution", "DeliverableDO, DeliverableSubmission", "psa_deliverable*"),
 ("ETC / EAC", "EstimateToComplete / EacAssessment", "delivery.execution", "etcHours, eac", "psa_task_package.etc_hours"),
 ("项目预算（WBS 预算）", "ProjectBudget / PlanBaseline", "delivery.execution", "ProjectBudget, wbs_budget_*", "psa_pm_project.wbs_budget_*"),
 ("验收单", "AcceptanceRecord", "delivery.acceptance", "DeliverableSubmission(DLV_ACCEPTED)", "psa_deliverable_submission"),
 ("工时单", "Timesheet", "cost.timesheet", "ManHourDO, TimeSheet", "psa_time_sheet"),
 ("工时（条目）", "TimeEntry", "cost.timesheet", "ManHourItemDO, works", "psa_time_sheet_works"),
 ("可计费工时", "BillableHours", "cost.timesheet", "billable, BillingUser", "psa_time_sheet_works.billable"),
 ("填报 / 审核", "SubmitTimesheet / ApproveTimesheet", "cost.timesheet", "saveManHour, approve", ""),
 ("项目成本 / 成本明细", "ProjectCost / CostEntry", "cost.costing", "CostLedgerDO, cost_summary", "psa_cost_ledger, psa_cost_summary"),
 ("间接成本池 / 分摊", "IndirectCostPool / AllocateOverhead", "cost.costing", "OH pool, OhAllocationCalculator", "psa_oh_cost_pool, psa_cost_allocation*"),
 ("结转批次", "CarryBatch", "cost.costing", "CarryBatch, CarryForwardData", "psa_carry_batch*, psa_wip_carry_forward_*"),
 ("合同履约成本 / 成本 WIP", "CostWip", "cost.costing", "WipEntry", "psa_wip_entry, psa_wip_movement"),
 ("损益台账", "PlLedger", "cost.costing", "PlLedgerDO", "psa_pl_ledger"),
 ("期间封闭", "PeriodClose", "cost.costing", "PeriodProgress, next_step", "psa_period_progress"),
 ("履约进度 / 完工百分比", "Poc / PercentageOfCompletion", "receivables.wip", "progress, POC", "psa_pm_project.progress_pro"),
 ("收入确认准备（产值）", "ProgressRecognized", "receivables.wip", "CuValueDO, RevenueConfirmation, 产值", "psa_pm_cuvalue, psa_revenue_confirmation, psa_revenue_fact"),
 ("开票申请", "BillingRequest", "receivables.wip", "ProjectBillingView", ""),
 ("报销单 / 差旅", "ExpenseClaim / TravelExpense", "expense.spend", "TravelApplication, Invoice", "psa_travel_application*"),
 ("法人主体", "LegalEntity", "organization.workforce", "companyCode, company_code", "system_dept / mdm"),
 ("员工 / 组织单元", "Employee / OrgUnit", "organization.workforce", "AdminUserDO, DeptDO", "system_users, system_dept"),
 ("职级 / 岗位", "JobGrade / Position", "organization.workforce", "jobLevelCode, postCode", "system_post"),
 ("会计凭证", "JournalVoucher", "accounting.ledger", "VoucherDO, U8cVoucherDO", "psa_voucher*, psa_u8c_voucher"),
 ("会计期间", "AccountingPeriod", "accounting.period", "AccountingPeriodDO", "mdm_accounting_period"),
 ("用户 / 角色 / 权限", "User / Role / Permission", "platform.identity", "AdminUserDO, RoleDO, MenuDO", "system_users, system_role, system_menu"),
 ("流程 / 流程实例 / 审批", "Workflow / WorkflowInstance / Approval", "platform.approval", "Bpm*, CustomizedBpmOA", "bpm_*, ACT_*"),
 ("规则引擎 / 规则执行", "RuleEngine / RuleExecution", "platform.approval", "", ""),
 ("异常队列 / 投递保证", "DeadLetterQueue / DeliveryGuarantee", "platform.integration", "ReliableDeliveryTask, psa_bpm_mq_outbox", "psa_reliable_delivery_task, psa_bpm_mq_outbox"),
 ("审计日志", "AuditLog", "platform.audit", "OperateLog, ApiAccessLog", "system_operate_log, infra_api_access_log"),
]
assert len(CORE) == 38
# 非财务 BC 的核心术语审计
CORE_AUDIT = {
 "Quote": dict(kind="document", refs=["PSA"], conf="high", forbidden=["Quotation", "BaoJia"]),
 "CostEstimateNode": dict(kind="entity", refs=["AACE"], conf="medium", status="proposed", g="G-5", definition="报价成本估算树的节点（分量 × 层级）"),
 "LaborEstimateLine": dict(kind="entity", refs=["AACE", "PSA"], conf="high", status="proposed", g="G-1", definition="报价内按职级的预估工时明细行（职级 × 工时 × 费率 = 人工小计）", forbidden=["ManhourEstimateItem", "ManHour"]),
 "Opportunity": dict(kind="entity", refs=["PSA"], conf="high"),
 "Customer": dict(kind="entity", refs=["PSA", "CAS14"], conf="high"),
 "Project": dict(kind="entity", refs=["PMBOK", "GB50326", "PSA"], conf="high", status="proposed", g="G-29", definition="交付执行的项目聚合（词表未收录，补入）",
                 note="PSA 行业中与客户的整体合作也称 Engagement；本系统 Engagement 仅用于客户侧 EngagementHistory，不作项目别名", forbidden=["PmProject"]),
 "ProjectBudget": dict(kind="entity", refs=["PMBOK"], conf="high", status="proposed", g="G-29", definition="WBS 层级的项目成本预算；锁定即计划基线生效（词表未收录，补入）"),
 "SubmitTimesheet": dict(kind="command", refs=["PSA"], conf="high", forbidden=["saveManHour"]),
 "ApproveTimesheet": dict(kind="command", refs=["PSA"], conf="high"),
 "ExpenseClaim": dict(kind="document", refs=["PSA"], conf="high"),
 "TravelExpense": dict(kind="value", refs=["PSA"], conf="high"),
 "LegalEntity": dict(kind="entity", refs=["CAS33"], conf="medium", forbidden=["CompanyCode", "companyCode"]),
 "Employee": dict(kind="entity", conf="high"), "OrgUnit": dict(kind="entity", conf="high"),
 "JobGrade": dict(kind="value", conf="high"), "Position": dict(kind="value", conf="high"),
 "User": dict(kind="entity", conf="high"), "Role": dict(kind="role", conf="high"), "Permission": dict(kind="value", conf="high"),
 "Workflow": dict(kind="entity", conf="high"), "WorkflowInstance": dict(kind="entity", conf="high"), "Approval": dict(kind="command", conf="high"),
 "RuleEngine": dict(kind="entity", conf="high"), "RuleExecution": dict(kind="event", conf="high"),
 "DeadLetterQueue": dict(kind="entity", conf="high"), "DeliveryGuarantee": dict(kind="value", conf="high"), "AuditLog": dict(kind="entity", conf="high"),
}

EVENT_SUFFIX = re.compile(r"(ed|Raised|Booked|Halted|Lost|Paid|WrittenOff|Detected|Identified)$")
VERBS = ("Submit", "Approve", "Allocate", "Merge", "Qualify", "Import", "Waive", "Correct", "Record", "Complete", "Invalidate", "Convert", "Nurture", "Close", "Reopen")
def infer_kind(code, zh):
    if code.startswith(VERBS): return "command"
    if EVENT_SUFFIX.search(code) and not code.endswith(("Need", "Speed")): return "event"
    if code.endswith(("Status", "State")): return "state"
    if code.endswith(("Rate", "Hours", "Variance", "Ratio", "Index", "Exposure", "Health", "Capacity")): return "metric"
    if code.endswith(("Ref", "Snapshot", "Level", "Type", "Category", "Model", "No", "Period", "Spec", "Time")): return "value"
    if code.endswith("Party"): return "role"
    if zh.endswith(("单", "申请", "凭证", "书", "协议")): return "document"
    return "entity"

def norm_alias(lst):
    return [{"name": n, "type": t, "deprecated": d} for n, t, d in (lst or [])]

entries = collections.OrderedDict()   # (bc, en) -> entry
def put(e):
    k = (e["bc"], e["en"])
    if k in entries:   # merge synonyms (e.g. 竣工结算 / 结算单 → FinalAccount)
        old = entries[k]
        if e["zh"] != old["zh"]:
            old["aliases"].append({"name": e["zh"], "type": "zh-internal", "deprecated": False})
        for f in ("old_codes",):
            old[f] = sorted(set(old.get(f, [])) | set(e.get(f, [])))
        old["core"] = old.get("core") or e.get("core")
        return
    entries[k] = e

def mk(zh, code, bc, definition, src_status="approved", core=False, legacy=None):
    ov = A.get(f"{bc}:{code}", {}) if bc in KEY_BCS else {}
    ov = {**CORE_AUDIT.get(code, {}), **ov} if (core or bc not in KEY_BCS) else ov
    if ov.get("status") == "deprecated-alias":
        return None  # folded into its target as alias
    en = ov.get("en", code)
    status = ov.get("status", src_status)
    aliases = norm_alias(ov.get("aliases"))
    if en != code and not any(code in x["name"] for x in aliases):
        aliases.append({"name": code, "type": "old-code", "deprecated": True})
    if ov.get("zh") and ov["zh"] != zh and not any(zh in x["name"] for x in aliases):
        aliases.append({"name": zh, "type": "zh-internal", "deprecated": False})
    if legacy:
        lc, lt = legacy
        if lc and not any(lc.split(",")[0].strip() in x["name"] for x in aliases):
            aliases.append({"name": lc, "type": "legacy-code", "deprecated": True})
        if lt and not any(lt.split(",")[0].strip() in x["name"] for x in aliases):
            aliases.append({"name": lt, "type": "legacy-table", "deprecated": True})
    refs = [R.get(r, r) for r in ov.get("refs", ["INTERNAL"])]
    e = collections.OrderedDict(
        id=None, zh=ov.get("zh", zh), en=en, snake=snake(en), kind=ov.get("kind") or infer_kind(en, zh), bc=bc,
        definition=ov.get("definition", definition), standard_ref=refs, confidence=ov.get("conf", "high" if refs == [R["INTERNAL"]] else "medium"),
        aliases=aliases, forbidden=sorted(set(ov.get("forbidden", [])) | ({code} if en != code else set())), status=status)
    if ov.get("g"): e["change_ref"] = ov["g"]
    if ov.get("note"): e["note"] = ov["note"]
    if core: e["core"] = True
    return e

def split(zh, code):
    codes = [c.strip() for c in code.split(" / ")]
    zhs = [z.strip() for z in zh.split(" / ")]
    if len(zhs) != len(codes): zhs = [zh] * len(codes)
    return list(zip(zhs, codes))

# 1) appendix terms of key BCs
for r in SRC:
    bc = BC.get(r["bc"])
    if bc not in KEY_BCS: continue
    for zh, code in split(r["term"], r["code"]):
        code = re.sub(r"（.*?）", "", code).strip()
        e = mk(zh, code, bc, r["definition"])
        if e: put(e)
# 2) core terms
for zh, code, bc, lc, lt in CORE:
    for z, c in split(zh, code):
        e = mk(z, c, bc, next((r["definition"] for r in SRC if c in [s.strip() for s in r["code"].split(" / ")]), ""), core=True, legacy=(lc, lt))
        if e is None: continue
        k = (e["bc"], e["en"])
        if k in entries:
            entries[k]["core"] = True
            names = {x["name"] for x in entries[k]["aliases"]}
            for al in e["aliases"]:
                if al["name"] not in names: entries[k]["aliases"].append(al)
        else:
            put(e)
# 3) new proposed terms
for en, zh, kind, bc, d, refs, conf, g, als, forb in NEW:
    k = (bc, en)
    if k in entries:   # core term already created (AccountingPeriod, RateCard…) → enrich
        e = entries[k]
        e.update(kind=kind, definition=d or e["definition"], standard_ref=[R.get(x, x) for x in refs], confidence=conf, status="proposed", change_ref=g)
        names = {x["name"] for x in e["aliases"]}
        e["aliases"] += [x for x in norm_alias(als) if x["name"] not in names]
        e["forbidden"] = sorted(set(e["forbidden"]) | set(forb))
        continue
    put(collections.OrderedDict(id=None, zh=zh, en=en, snake=snake(en), kind=kind, bc=bc, definition=d,
        standard_ref=[R.get(x, x) for x in refs], confidence=conf, aliases=norm_alias(als), forbidden=forb, status="proposed", change_ref=g))

# ids: snake, qualified with bc when the same en exists in several BCs (同名异义按 BC 隔离)
cnt = collections.Counter(e["snake"] for e in entries.values())
for e in entries.values():
    e["id"] = e["snake"] if cnt[e["snake"]] == 1 else f'{e["bc"].replace(".", "_")}__{e["snake"]}'
ids = [e["id"] for e in entries.values()]
assert len(ids) == len(set(ids)), [i for i in ids if ids.count(i) > 1]

COMMON = ["Id", "Uuid", "Key", "Code", "No", "Name", "Title", "Description", "Remark", "Note", "Type", "Kind", "Category", "Status", "State",
          "Level", "Version", "Seq", "Sequence", "Index", "Order", "Sort", "Line", "Item", "Detail", "Entry", "Record", "List", "Page",
          "Amount", "Price", "Quantity", "Qty", "Rate", "Ratio", "Percent", "Total", "Sum", "Count", "Balance", "Currency", "Money", "Hours",
          "Date", "Time", "Timestamp", "Period", "Start", "End", "From", "To", "Year", "Month", "Day", "Created", "Updated", "Deleted", "By", "At",
          "Request", "Response", "Command", "Query", "Queries", "Commands", "Result", "Summary", "View", "Dashboard", "Queue", "Ledger", "Snapshot",
          "Ref", "Reference", "Source", "Target", "Owner", "Approver", "Reason", "Comment", "Attachment", "File", "Url", "Is", "Has",
          "Repository", "Controller", "Client", "Gateway", "Config", "Policy", "Spec", "Rule", "Event", "Handler", "Mapper",
          "Create", "Update", "Delete", "Get", "Find", "Submit", "Approve", "Reject", "Cancel", "Withdraw", "Close", "Open", "Reopen",
          "Draft", "Pending", "Active", "Inactive", "Approved", "Rejected", "Cancelled", "Closed", "Valid", "Invalid", "Enabled",
          "Min", "Max", "Default", "Limit", "Threshold", "Tax", "Net", "Gross", "Debit", "Credit", "Api", "Http", "Json", "Dto"]

meta = collections.OrderedDict(
    version="0.1.0-draft", date="2026-10-14",
    source=["psa/docs/domain/00_战略设计/03-通用语言.md（经 glossary/source_terms.json）", "GLOSSARY.md §2 核心术语 38 条", "STANDARDS_AUDIT.md（G-8 起改名建议）"],
    naming_priority=[
        "1 法规与会计准则：中文用《企业会计准则》原文用语（CAS 14 收入：合同、履约义务、交易价格、合同资产、合同负债、应收账款、合同履约成本、合同取得成本、时段履约 / 时点履约、履约进度、投入法 / 产出法）；英文用 IFRS 15 / IAS 与 IFRS XBRL taxonomy（ifrs-full）标准用语",
        "2 行业与工程标准：中文用国标（GB/T 50326、GB/T 50358、GB/T 50500 的工程变更、现场签证、索赔、质量保证金、竣工结算等）；英文用 PMI PMBOK / AACE（WBS、WorkPackage、EstimateAtCompletion、EstimateToComplete、EarnedValue、PlannedValue、ActualCost、Baseline）与 FIDIC（Variation、Claim、RetentionMoney、InterimPaymentCertificate）",
        "3 专业服务 / PSA 行业通行写法（Timesheet、TimeEntry、Utilization、Billable、RateCard、Engagement、WIP），在准则与国标未覆盖时采用",
        "4 所有跟正式用语不符合的叫法都归为公司内部方言（dialect），新系统一律采用正式用语，方言不进入代码、界面文案、接口文档、列注释；判定不了的标为 undetermined，登记在 TERM_CONFIRMATION.md 由业务 Admin 确认（用户 2026-10-14 裁定）；禁用拼音、自造缩写",
    ],
    field_notes=dict(
        status="approved = 原词表已有且符合标准；proposed = 本次建议改名或新增，待评审（见 change_ref 指向的 G 编号）",
        confidence="standard_ref 对应关系的把握程度；条文号不确定的只写到准则 / 标准名一级",
        id="稳定 slug；同一英文名在多个 BC 同名异义时以 <bc>__ 前缀隔离",
        aliases="type: dialect（方言：业务口头或旧系统叫法，只用于检索和沟通，禁止进入代码、界面文案、接口文档、列注释）| undetermined（公司叫法，是正式用语还是方言待确认）| old-code（03 词表旧代码名）| legacy-code / legacy-table（老系统）| kpi-symbol | abbr | industry | standard-zh / standard-en；deprecated=true 表示新代码禁用",
        coverage="38 个核心术语全部 + 财务关键 BC（operations.facts / operations.policy / contract.* / delivery.* / cost.* / receivables.* / accounting.*）的附录术语 + 准则 / EVM 缺位的新增术语",
    ),
)
terms = list(entries.values())

# 用户 2026-10-14 裁定：所有跟正式用语不符合的都归为公司内部方言，新系统一律采用正式用语，不把方言带入新系统；
# 不确定的登记用语确认表，由用户确认。规则：
#   已判定非正式（zh-internal 且 deprecated）→ dialect，并把叫法加入 forbidden；
#   未判定（zh-internal 且未 deprecated）→ undetermined，进入 TERM_CONFIRMATION.md 等待确认。
def _split(n): return [x.strip() for x in re.split(r"\s*/\s*", n) if x.strip()]
# 用户已逐项裁定为方言的叫法（含这些词的别名一律为方言）
DECIDED_DIALECT = ["产值"]  # 2026-10-14：「产值确认单就是收入确认单……现在的叫法可以归为方言」
# 用户在「用语确认」页上的逐项确认（2026-10-14，197 项，见 glossary/decisions-2026-10-14.json）
DECISIONS = {}
for _f in sorted(glob.glob("glossary/decisions-*.json")):
    for _k, _v in json.load(open(_f, encoding="utf-8")).items():
        if _k in DECISIONS:
            raise SystemExit(f"{_f}: 确认记录键 {_k} 与前面的文件重复")
        DECISIONS[_k] = _v
for _need in ("A-", "R2-", "R3-", "ACL-"):
    if not any(k.startswith(_need) for k in DECISIONS):
        raise SystemExit(f"缺少键前缀为 {_need} 的确认记录（glossary/decisions-*.json）")
DEC = DECISIONS
for t in terms:
    for k, al in enumerate(t.get("aliases") or []):
        dk = DEC.get(f"A-{t['id']}-{k}")
        if dk and dk["choice"] == "dialect":
            al["type"], al["deprecated"] = "zh-internal", True   # 下方统一转为 dialect 并列入禁用
    dk = DEC.get(f"B-{t['id']}")
    if dk and dk["choice"] == "approve" and t["status"] == "proposed":
        t["status"] = "approved"
        for al in t.get("aliases") or []:
            if al.get("type") in ("zh-internal", "undetermined"):
                al["type"], al["deprecated"] = "zh-internal", True
    if DEC.get(f"B-{t['id']}") or DEC.get(f"C-{t['id']}") or any(k.startswith(f"A-{t['id']}-") for k in DEC):
        t["confirmed"] = "2026-10-14（业务 Admin，用语确认页）"
# OA 流程盘点（2026-10-14，放在已有确认之后，不受已确认改名的连带）：A8 表单与流程中的叫法登记为待确认别名，不定为正式用语（ADR-0018）
# R0 在用报表盘点（2026-10-14）：管理报表叫法同样只登记为待确认别名（glossary/r0_report_aliases.json）
_byid = {t["id"]: t for t in terms}
for _src in ("glossary/oa_aliases.json", "glossary/r0_report_aliases.json"):
    for _a in json.load(open(_src, encoding="utf-8"))["aliases"]:
        _t = _byid[_a["term"]]
        if all(al["name"] != _a["name"] for al in _t.get("aliases") or []):
            _t.setdefault("aliases", []).append({"name": _a["name"], "type": "zh-internal", "deprecated": False, "source": _a["source"]})
# 用语确认第二轮（2026-10-14，用语确认页，见 glossary/decisions-round2-2026-10-14.json）：A 组 = OA 盘点登记的 A8 叫法
DEC2 = DECISIONS
for t in terms:
    for k, al in enumerate(t.get("aliases") or []):
        dk = DEC2.get(f"R2-A-{t['id']}-{k}")
        if not dk:
            continue
        if dk["choice"] == "dialect":
            al["type"], al["deprecated"] = "zh-internal", True   # 下方统一转为 dialect 并列入禁用
        elif dk["choice"] == "formal":
            al["type"], al["deprecated"] = "standard-zh", False
        if dk.get("note") or dk.get("renameTo"):
            al["decision_note"] = " ".join(x for x in (dk.get("renameTo"), dk.get("note")) if x)
        t["confirmed"] = "2026-10-14（业务 Admin，用语确认页第二轮）"

for t in terms:
    for al in t.get("aliases") or []:
        if al.get("type") == "zh-internal" and any(w in al["name"] for w in DECIDED_DIALECT):
            al["deprecated"] = True
        if al.get("type") == "zh-internal":
            if al.get("deprecated"):
                al["type"] = "dialect"
                fb = t.setdefault("forbidden", [])
                for n in _split(al["name"]):
                    if n not in fb and n != t["zh"] and not re.fullmatch(r"[A-Za-z]+", n):
                        fb.append(n)
            elif al["name"] == t["zh"]:
                al["type"] = "standard-zh"   # 叫法与正式用语相同
            else:
                al["type"] = "undetermined"
# 用语确认第二轮 P 组：backend/glossary/proposed/*.yaml 的模块提议术语，按 glossary/round2-proposals.yaml 的建议与用户确认转为正式。
# 需改代码的旧写法（code 非「无」）在 1a 会话改名前仍由 backend/glossary/proposed/pending-renames.yaml 登记；改完后删除该文件，并把 forbid_en 写入禁用。
PROP2 = yaml.safe_load(open("glossary/round2-proposals.yaml", encoding="utf-8"))
CODE_RENAMED = set(PROP2.get("code_renamed") or [])   # 1a 改名完成后在 round2-proposals.yaml 中登记 id
_byid = {t["id"]: t for t in terms}
_cjk = lambda s: re.fullmatch(r"[一-鿿（）]+", s) is not None   # 只把纯中文叫法列入禁用：含字母的（H5、区域 AR）拆词后会误伤 ar、h 等
_round2_sources = sorted(glob.glob("glossary/round2-source/*.yaml"), reverse=True)
if not _round2_sources:
    raise SystemExit("缺少 glossary/round2-source/*.yaml")
for _f in _round2_sources:
    for _t in yaml.safe_load(open(_f, encoding="utf-8"))["terms"]:
        _p, _d = PROP2["P"].get(_t["id"]), DEC2.get(f"R2-P-{_t['id']}")
        if not _p or not _d:
            continue
        _conf = "2026-10-14（业务 Admin，用语确认页第二轮）"
        if _d["choice"] == "rename":
            print("P rename needs manual handling:", _t["id"], _d.get("renameTo"))
            continue
        _sug = "approve" if _d["choice"] == "keep" else _p["sug"]
        _forbid = [x for x in _p.get("forbid_en") or [] if _t["id"] in CODE_RENAMED or not _p.get("code") or _p["code"].startswith("无")]
        if _sug == "merge":
            _tgt = _byid[_p["into"]]
            _al = _tgt.setdefault("aliases", [])
            if _t["en"] not in [a["name"] for a in _al]:
                _al.append({"name": _t["en"], "type": "old-code", "deprecated": True})
            for _z in _p.get("dialect") or []:
                if _z not in [a["name"] for a in _al]:
                    _al.append({"name": _z, "type": "dialect", "deprecated": True})
            _fb = _tgt.setdefault("forbidden", [])
            _fb += [x for x in _forbid + [z for z in _p.get("dialect") or [] if _cjk(z)] if x not in _fb]
            continue
        _en = _p.get("en", _t["en"]) if _sug == "change" else _t["en"]
        _sn = "_".join(w.lower() for w in re.findall(r"[A-Z][a-z0-9]*", _en))
        _e = collections.OrderedDict(
            id=_sn, zh=_p.get("zh", _t["zh"]) if _sug == "change" else _t["zh"], en=_en, snake=_sn, kind=_t["kind"], bc=_t["bc"],
            definition=_p.get("definition", _t["definition"]) if _sug == "change" else _t["definition"],
            standard_ref=list(_t.get("standard_ref") or []), confidence=_t.get("confidence", "high"), aliases=[], forbidden=list(_t.get("forbidden") or []),
            status="approved", note=f"用语确认第二轮转正式（原提议 {os.path.basename(_f)}#{_t['id']}）", confirmed=_conf)
        for _a in _t.get("aliases") or []:
            _n = _a["name"] if isinstance(_a, dict) else _a
            if _n != _e["zh"] and _n not in (_p.get("dialect") or []):
                _e["aliases"].append({"name": _n, "type": "standard-zh", "deprecated": False})
        if _e["en"] != _t["en"]:
            _e["aliases"].append({"name": _t["en"], "type": "old-code", "deprecated": True})
        if _e["zh"] != _t["zh"] and _t["zh"] not in [x["zh"] for x in terms]:
            _e["aliases"].append({"name": _t["zh"], "type": "dialect", "deprecated": True})
            if _cjk(_t["zh"]):
                _e["forbidden"].append(_t["zh"])
        for _z in _p.get("dialect") or []:
            if _z not in [a["name"] for a in _e["aliases"]]:
                _e["aliases"].append({"name": _z, "type": "dialect", "deprecated": True})
            if _cjk(_z) and _z not in _e["forbidden"]:
                _e["forbidden"].append(_z)
        _e["forbidden"] += [x for x in _forbid if x not in _e["forbidden"]]
        terms.append(_e)

# 开工前术语门禁（SESSION_PLAYBOOK 附 M6）：每张卡的小决策页答复 glossary/decisions-gate-<卡号>.json，规则在 glossary/gate/apply_gate.py
sys.path.insert(0, "glossary/gate")
import apply_gate
print("gate: 新增正式词条", apply_gate.apply(terms))

# 用语确认第三轮（重做，2026-10-15，固定确认页逐组提交，glossary/decisions-round3-2026-10-15.json）：R3- 换算规则在 glossary/round3/apply_round3.py，
# ACL- 归类（使用范围 scope / acl_class、组织同步归集成层、定义去外部名）在 glossary/round3/apply_acl.py
sys.path.insert(0, "glossary/round3")
import apply_round3, apply_acl
print("round3: 新增正式词条", apply_round3.apply(terms))
_scoped, _unscoped = apply_acl.apply(terms, apply_round3.plan()["id_map"])
print("acl: 写使用范围", _scoped, "条；尚无归类", len(_unscoped), "条（B1 补齐后必填）")

# 用语登记规则（B1，intent 2026-10-15-171649、171959-one-coordinator）：bc 为 kernel 的领域词条转为 scope kernel，
# 并入 glossary/term-fields.yaml 的补充字段，再连同 backend/glossary/proposed/*.yaml 逐条校验（glossary/term_rules.py，存量问题见 term-rules-baseline.json）
# B1 决策页答复（glossary/decisions-b1-2026-10-15.json）：使用范围、同 bc 重名改名，规则在 glossary/b1/apply_b1.py
sys.path.insert(0, "glossary/b1")
import apply_b1
print("b1: 写使用范围 %d 条、改名 %d 条" % apply_b1.apply(terms))
# B2：类别统一为 DDD 战术分类（glossary/b2/apply_b2.py，判定表 b2/kind-judgement.json）
sys.path.insert(0, "glossary/b2")
import apply_b2
print("b2: 改类别 %d 条、移出词条 %d 条、挂属性 %d 条" % apply_b2.apply(terms))
# 用户裁定（glossary/rulings.yaml）：在类别统一之后改名、改定义、提议转正，规则在 glossary/apply_rulings.py（判定表按原 id，所以排在 B2 之后）
sys.path.insert(0, "glossary")
import apply_rulings
print("rulings: 应用用户裁定", apply_rulings.apply(terms), "条")
import term_rules
_bcs = {b["id"]: b["kind"] for b in yaml.safe_load(open("glossary/bcs.yaml", encoding="utf-8"))["bcs"]}
_extra = yaml.safe_load(open("glossary/term-fields.yaml", encoding="utf-8"))["terms"] or {}
_ids = {t["id"] for t in terms}
if set(_extra) - _ids:
    raise SystemExit("glossary/term-fields.yaml 有不存在的词条 id：" + "、".join(sorted(set(_extra) - _ids)))
_EXTRA_FIELDS = {"scope", "used_by", "upstream_term", "homonyms", "formula", "grain", "as_of"}
_to_kernel = []
for t in terms:
    _x = _extra.get(t["id"]) or {}
    if set(_x) - _EXTRA_FIELDS:
        raise SystemExit(f"glossary/term-fields.yaml {t['id']}：只能写 {'、'.join(sorted(_EXTRA_FIELDS))}，不能写 " + "、".join(sorted(set(_x) - _EXTRA_FIELDS)))
    if "scope" in _x and t.get("scope"):
        raise SystemExit(f"glossary/term-fields.yaml {t['id']}：使用范围已由 ACL 归类答复写定（{t['scope']}），不得覆盖")
    if t["bc"] == "kernel" and t.get("scope") in (None, "domain") and "scope" not in _x:
        t["scope"] = "kernel"
        _to_kernel.append(t["id"])
    t.update(_x)
print("term_rules: bc 为 kernel 的领域词条转为共享内核", len(_to_kernel), "条：", "、".join(_to_kernel))
import apply_nm
print("nm: 应用命名第 2–4 批答复", apply_nm.apply(terms))
import apply_recon
print("recon: 已裁定漏登与提议转正", apply_recon.apply(terms))
print("rulings: recon 之后应用的裁定（late: true）", apply_rulings.apply(terms, late=True), "条")
_entries = [("terms.yaml", t) for t in terms]
_proposed = sorted(glob.glob("../../backend/glossary/proposed/*.yaml"))
if not _proposed:
    raise SystemExit("找不到 backend/glossary/proposed/*.yaml（须在 docs/modernization 下运行）")
for _f in _proposed:
    for _e in (yaml.safe_load(open(_f, encoding="utf-8")) or {}).get("terms") or []:
        _entries.append((f"proposed/{os.path.basename(_f)}", _e))
_left, _by_rule = term_rules.enforce(_entries, _bcs)
print("term_rules: 存量问题", _left, "条（只减不增）", dict(sorted(_by_rule.items())))

out = collections.OrderedDict(meta=meta, terms=[dict(t) for t in terms], common_words=COMMON)

class D(yaml.SafeDumper): pass
D.add_representer(collections.OrderedDict, lambda d, v: d.represent_dict(v.items()))
D.ignore_aliases = lambda *a: True
with open("glossary/terms.yaml", "w", encoding="utf-8") as f:
    f.write("# terms.yaml — FORCITIS one 现代化统一语言（单一来源初版，机器可读）\n")
    f.write("# 生成：python3 glossary/build_terms.py ；人工修改请改生成器，避免与 03-通用语言.md 漂移\n")
    yaml.dump(out, f, Dumper=D, allow_unicode=True, sort_keys=False, width=200)

st = collections.Counter(t["status"] for t in terms)
print("terms:", len(terms), dict(st), "core-flagged:", sum(1 for t in terms if t.get("core")),
      "by-bc:", dict(collections.Counter(t["bc"] for t in terms)))
print("G refs:", sorted({t.get("change_ref") for t in terms if t.get("change_ref")}))
