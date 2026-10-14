#!/usr/bin/env python3
"""Build GLOSSARY.md: the unified domain vocabulary for the FORCITIS one modernization.
Source of truth = psa/docs/domain/00_战略设计/03-通用语言.md (parsed into glossary/source_terms.json).
This script adds: module path (subdomain.bc), legacy names, and amendment proposals."""
import json, collections
rows = json.load(open("glossary/source_terms.json", encoding="utf-8"))

# BC -> (subdomain slug [SSOT, 02-子域划分], proposed bc slug)
BC = {
    "战略与经营计划 BC": ("operations", "planning"), "预算管控 BC": ("operations", "budgeting"),
    "经营事实与归属 BC": ("operations", "facts"), "经营口径与勾稽 BC": ("operations", "policy"),
    "客户经营 BC": ("presales", "customer"), "商机评估与报价承诺 BC": ("presales", "quoting"),
    "合约基线 BC": ("contract", "baseline"), "变更签证索赔 BC": ("contract", "changeorder"), "风险交付 BC": ("contract", "riskdelivery"),
    "项目交付执行 BC": ("delivery", "execution"), "质量返工与验收 BC": ("delivery", "acceptance"),
    "工时与投入有效性 BC": ("cost", "timesheet"), "项目成本归集 BC": ("cost", "costing"),
    "WIP 与开票准入 BC": ("receivables", "wip"), "AR 与现金转化 BC": ("receivables", "collection"),
    "费用报销与支出管控 BC": ("expense", "spend"), "分包与采购履约 BC": ("procurement", "subcontracting"),
    "知识资产与 AI 赋能 BC": ("km", "knowledge"), "组织人事 BC": ("organization", "workforce"),
    "假勤薪酬 BC": ("organization", "payroll"), "绩效激励 BC": ("organization", "incentive"),
    "财务总账与法定核算 BC": ("accounting", "ledger"), "权限流程与集成平台 BC": ("platform", "*"),
}
PLATFORM_PARTS = [("platform.identity", "用户 / 角色 / 权限、登录与会话、数据范围"),
                  ("platform.approval", "流程 / 流程实例 / 审批（Flowable BPMN + DMN）、待办、催办"),
                  ("platform.integration", "事件总线、投递保证、异常队列、数据集成通道、外部系统防腐层"),
                  ("platform.audit", "审计日志、平台配置")]
IN_SCOPE = {"商机评估与报价承诺 BC", "客户经营 BC", "合约基线 BC", "项目交付执行 BC", "质量返工与验收 BC", "工时与投入有效性 BC",
            "项目成本归集 BC", "WIP 与开票准入 BC", "费用报销与支出管控 BC", "组织人事 BC", "财务总账与法定核算 BC", "权限流程与集成平台 BC",
            "经营事实与归属 BC", "经营口径与勾稽 BC"}  # 后两者：权责发生制主干（ADR-0016）

# core terms: (中文, 代码名, BC, legacy code, legacy tables, legacy UI/API, note)
CORE = [
 ("报价单", "Quote", "商机评估与报价承诺 BC", "Quotation / QuotationDO", "psa_quotation, psa_quotation_line, psa_quotation_seal", "views/project/quotation, /fst/quotation", "不用 Quotation"),
 ("成本估算树", "CostEstimateNode", "商机评估与报价承诺 BC", "CostEstimateNode", "psa_cost_estimate_*", "CostEstimateTree.vue", "词表无此条：建议补入"),
 ("工时估算明细", "LaborEstimateLine", "商机评估与报价承诺 BC", "—", "—", "—", "改词建议：词表现为 ManhourEstimateItem"),
 ("商机", "Opportunity", "商机评估与报价承诺 BC", "OpportunityDO, OppCloseStatus", "psa_crm_opportunity", "/fst/opportunity", "关闭状态改为枚举 OPEN/WON/LOST"),
 ("客户", "Customer", "客户经营 BC", "CustomerDO", "psa_bd_customer", "/fst/customer", "CRM 为上游，老系统存只读快照"),
 ("费率卡", "RateCard / RateCardItem", "合约基线 BC", "BillRateDO, HourlyRateDO", "psa_bill_rate*, psa_pm_hourlyrate*", "views/project/price", "改词建议：统一 RateCard 词族（含 StandardCostRateCard、RateVersion、CustomerRateCard）"),
 ("合同条款", "ContractClause", "合约基线 BC", "ContractTermDO, termNumber, clause", "psa_pm_contract_term, psa_contract_clause", "—", "老代码 term/clause 混用；Term 一律不用"),
 ("项目", "Project", "项目交付执行 BC", "PmProjectDO, ProjectDO", "psa_pm_project", "views/project/pmList", "项目状态改为枚举"),
 ("WBS 节点", "WbsNode", "项目交付执行 BC", "BudgetClause/BudgetDeliverable", "psa_budget_*", "pmList/budget.vue", ""),
 ("任务包", "WorkPackage", "项目交付执行 BC", "TaskPackage, task_package, workPackage", "psa_task_package", "taskLists", "不用 TaskPackage；交付包 = DeliveryPackage"),
 ("交付物", "Deliverable", "项目交付执行 BC", "DeliverableDO, DeliverableSubmission", "psa_deliverable*", "views/project/deliverable", ""),
 ("ETC / EAC", "EstimateToComplete / EacAssessment", "项目交付执行 BC", "etcHours, eac", "psa_task_package.etc_hours", "progress.vue", ""),
 ("项目预算（WBS 预算）", "ProjectBudget / PlanBaseline", "项目交付执行 BC", "ProjectBudget, wbs_budget_*", "psa_pm_project.wbs_budget_*", "pmList/budget.vue", "预算锁定 = 计划基线生效"),
 ("验收单", "AcceptanceRecord", "质量返工与验收 BC", "DeliverableSubmission(DLV_ACCEPTED)", "psa_deliverable_submission", "deliverable", ""),
 ("工时单", "Timesheet", "工时与投入有效性 BC", "ManHourDO, TimeSheet", "psa_time_sheet", "views/project/manhour, timesheet", "不用 ManHour / Works"),
 ("工时（条目）", "TimeEntry", "工时与投入有效性 BC", "ManHourItemDO, works", "psa_time_sheet_works", "manhour/fill", "改词建议：TimesheetLine 并入 TimeEntry"),
 ("可计费工时", "BillableHours", "工时与投入有效性 BC", "billable, BillingUser", "psa_time_sheet_works.billable", "—", ""),
 ("填报 / 审核", "SubmitTimesheet / ApproveTimesheet", "工时与投入有效性 BC", "saveManHour, approve", "—", "/fst/man-hour/*", "用例名"),
 ("项目成本 / 成本明细", "ProjectCost / CostEntry", "项目成本归集 BC", "CostLedgerDO, cost_summary", "psa_cost_ledger, psa_cost_summary", "costCollection", ""),
 ("间接成本池 / 分摊", "IndirectCostPool / AllocateOverhead", "项目成本归集 BC", "OH pool, OhAllocationCalculator", "psa_oh_cost_pool, psa_cost_allocation*", "costAllocation", "OH = overhead"),
 ("结转批次", "CarryBatch", "项目成本归集 BC", "CarryBatch, CarryForwardData", "psa_carry_batch*, psa_wip_carry_forward_*", "costCarry, carryForward", "老结转路径与新引擎并存，需下线老路径"),
 ("合同履约成本 / 成本 WIP", "CostWip", "项目成本归集 BC", "WipEntry", "psa_wip_entry, psa_wip_movement", "projectWipSummary", ""),
 ("损益台账", "PlLedger", "项目成本归集 BC", "PlLedgerDO", "psa_pl_ledger", "projectProfitL3", ""),
 ("期间封闭", "PeriodClose", "项目成本归集 BC", "PeriodProgress, next_step", "psa_period_progress", "accountingPeriod", ""),
 ("履约进度 / 完工百分比", "Poc / PercentageOfCompletion", "WIP 与开票准入 BC", "progress, POC", "psa_pm_project.progress_pro", "—", ""),
 ("收入确认准备（产值）", "ProgressRecognized", "WIP 与开票准入 BC", "CuValueDO, RevenueConfirmation, 产值", "psa_pm_cuvalue, psa_revenue_confirmation, psa_revenue_fact", "views/project/revenue", "不用 cuvalue（拼音）；法定收入入账 = StatutoryRevenueBooked（核算 BC）"),
 ("开票申请", "BillingRequest", "WIP 与开票准入 BC", "ProjectBillingView", "—", "—", ""),
 ("报销单 / 差旅", "ExpenseClaim / TravelExpense", "费用报销与支出管控 BC", "TravelApplication, Invoice", "psa_travel_application*", "views/accounting/travel", "差旅申请 = ExpenseRequest（费用申请）"),
 ("法人主体", "LegalEntity", "组织人事 BC", "companyCode, company_code", "system_dept / mdm", "—", "ADR-0001：legal_entity_id"),
 ("员工 / 组织单元", "Employee / OrgUnit", "组织人事 BC", "AdminUserDO, DeptDO", "system_users, system_dept", "views/system/user, dept", "A8 为主数据源"),
 ("职级 / 岗位", "JobGrade / Position", "组织人事 BC", "jobLevelCode, postCode", "system_post", "—", ""),
 ("会计凭证", "JournalVoucher", "财务总账与法定核算 BC", "VoucherDO, U8cVoucherDO", "psa_voucher*, psa_u8c_voucher", "voucherManage", "U8C 为外部总账"),
 ("会计期间", "AccountingPeriod（事件 AccountingPeriodClosed）", "财务总账与法定核算 BC", "AccountingPeriodDO", "mdm_accounting_period", "accountingPeriod", "词表只有事件名：建议补实体名"),
 ("用户 / 角色 / 权限", "User / Role / Permission", "权限流程与集成平台 BC", "AdminUserDO, RoleDO, MenuDO", "system_users, system_role, system_menu", "views/system/*", "模块 platform.identity"),
 ("流程 / 流程实例 / 审批", "Workflow / WorkflowInstance / Approval", "权限流程与集成平台 BC", "Bpm*, CustomizedBpmOA", "bpm_*, ACT_*", "views/bpm/*", "模块 platform.approval（Flowable 8）"),
 ("规则引擎 / 规则执行", "RuleEngine / RuleExecution", "权限流程与集成平台 BC", "—", "—", "—", "DMN 决策表即规则执行"),
 ("异常队列 / 投递保证", "DeadLetterQueue / DeliveryGuarantee", "权限流程与集成平台 BC", "ReliableDeliveryTask, psa_bpm_mq_outbox", "psa_reliable_delivery_task, psa_bpm_mq_outbox", "—", "模块 platform.integration"),
 ("审计日志", "AuditLog", "权限流程与集成平台 BC", "OperateLog, ApiAccessLog", "system_operate_log, infra_api_access_log", "infra/logger", "模块 platform.audit"),
]

def path(bc):
    sd, slug = BC[bc]
    return f"{sd}.{slug}" if slug != "*" else "platform.*"

L = ["# GLOSSARY — FORCITIS one 现代化统一领域用语", "",
     "日期：2026-10-14",
     "",
     "**唯一来源**：`psa/docs/domain/00_战略设计/03-通用语言.md`（385 个术语，23 个 BC）。本文不另立术语，只做三件事：",
     "1. 给每个 BC 指定代码位置：子域 slug 沿用已定稿的 `02-子域划分.md`，BC slug 是本次提出的方案；",
     "2. 列出老系统的代码名、表名、页面接口与新名的对照；",
     "3. 提出词表改词建议，需同批修改 `03-通用语言.md`（按 `00-命名一致性规则.md` 的同步清单）。", "",
     "所有新代码、表、事件、API、文档都用这里的代码名，**不得自创**（ArchUnit G14 和词表校验守护）。", "",
     "## 1. 模块路径：子域 → BC → 包", "",
     "Spring Modulith 的顶层模块对应**子域 slug**，嵌套模块对应 **BC slug**，与老 fst 的 `domain.<subdomainSlug>.<bcSlug>.<concept>` 约定一致。",
     "Java 包为 `com.forcitis.psa.<subdomain>.<bc>.{api,events,domain,application,infrastructure,web}`，PG schema 为 `<subdomain>_<bc>`。", "",
     "| 子域 | BC | 模块路径 | schema | 本次范围 |", "|---|---|---|---|---|"]
for bc, (sd, slug) in BC.items():
    if slug == "*":
        for p, desc in PLATFORM_PARTS:
            L.append(f"| {sd} | {bc}（{desc}） | `{p}` | `{p.replace('.', '_')}` | ✔ |")
    else:
        L.append(f"| {sd} | {bc} | `{sd}.{slug}` | `{sd}_{slug}` | {'✔' if bc in IN_SCOPE else '—'} |")
        if bc == "财务总账与法定核算 BC":
            L.append("| accounting | 会计期间 BC（**新增提议**，ADR-0016：(期间, 法人) 期间状态与关账闸门；词表需补入） | `accounting.period` | `accounting_period` | ✔ |")
L += ["", "BC slug 的命名规则（沿用 fst `CLAUDE.md` §2）：纯英文小写；不用 Manager / Helper / Util；不用拼音；不用自造缩写；不用子域旧称（operating、opportunity、cash、knowledge）。",
      "平台子域只有一个 BC（权限流程与集成平台 BC）。按 ADR-007，它是技术底座，所以拆成 identity / approval / integration / audit 四个技术模块，但它们同属一个 BC。",
      "", "## 2. 核心术语对照（老系统 → 统一用语）", "",
      "| 中文 | 代码名（词表） | 模块 | 老代码名 | 老表 | 老页面 / 接口 | 说明 |", "|---|---|---|---|---|---|---|"]
for zh, code, bc, lc, lt, lu, note in CORE:
    L.append(f"| {zh} | `{code}` | `{path(bc)}` | {lc} | {lt} | {lu} | {note} |")
L += ["", "## 3. 词表改词建议（需同批修改 03-通用语言.md）", "",
      "| # | 现状 | 建议 | 理由 |", "|---|---|---|---|",
      "| G-1 | 工时估算明细 = `ManhourEstimateItem` | `LaborEstimateLine` | manhour 是旧称，行业用 labor / effort；Item 改为 Line，与 QuoteLine 一致 |",
      "| G-2 | 工时 = `TimeEntry`，工时明细 = `TimesheetLine`（同义重复） | 只保留 `TimeEntry` | 一个概念一个名字；Timesheet（单）+ TimeEntry（条目）是 Harvest、Kantata、Jira 等的通行写法 |",
      "| G-3 | 标准费率 `StandardCostRateCard`、费率版本 `RateVersion`、客户费率卡 `CustomerRateCard`、费率表 `RateCardItem` | 统一为 `RateCard` 词族：`StandardCostRateCard`、`BillRateCard`、`RateCardVersion`、`RateCardItem` | 词族一致，便于 agent 推断 |",
      "| G-4 | 会计期间只有事件名 `AccountingPeriodClosed` | 补实体 `AccountingPeriod` | 实体与事件同主语 |",
      "| G-5 | 无「成本估算树 / 节点」 | 补 `CostEstimateNode`（商机评估与报价承诺 BC） | 报价的核心结构，代码已在用 |",
      "| G-6 | 无「法人主体」以外的核算主体别名 | 明确 `companyCode` 等老字段一律映射为 `LegalEntity` / `legal_entity_id` | ADR-0001 硬约束 2 |",
      "| G-7 | 本次方案文档中曾使用「审批与待办 BC」 | 废止，改称「权限流程与集成平台 BC」的 `platform.approval` 模块 | 老系统领域设计的 23 个 BC 中没有「审批与待办 BC」 |",
      "", "## 附录：全部术语（按模块）", ""]
by = collections.defaultdict(list)
for r in rows:
    by[r["bc"]].append(r)
for bc in BC:
    if bc not in by:
        continue
    L += [f"### `{path(bc)}` — {bc}", "", "| 术语 | 代码名 | 定义 |", "|---|---|---|"]
    L += [f"| {r['term']} | `{r['code']}` | {r['definition'].replace('|', '/')} |" for r in by[bc]]
    L.append("")
open("GLOSSARY.md", "w", encoding="utf-8").write("\n".join(L) + "\n")
print("GLOSSARY.md:", len(CORE), "core terms,", len(rows), "appendix terms")
