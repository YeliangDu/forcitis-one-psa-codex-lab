# 全企业聚合草案 §8 用语预判（one-glossary 查重稿）

依据：origin/main 的 terms.yaml（正式 + 提议共 921 条）、bcs.yaml、term_rules.py；草案取自 origin/claude/one-architecture-stage-bc-2。登记稿 `proposed.yaml` 共 43 条，已用 term_rules.check 对全部现有词条校验：相对 main 新增违规 0 条（前提：先并入 `bcs-additions.yaml`）。scope 除另注外均为 domain。「查重」均已查中英文、aliases、forbidden。

## 一、BC 与命名空间（用户已定划分，名称由我核定）

| 草案词 | bc id | 中文名 | 英文名 | 子域 | 与 bcs.yaml 关系 |
|---|---|---|---|---|---|
| 主数据命名空间（D-2） | masterdata | 主数据 | MasterData | 新子域 | 新增；须新 ADR 取代 ADR-0017 决定 1 |
| 往来单位（含外部工程登记） | masterdata.counterparty | 往来单位与外部工程 | Counterparty | masterdata | 新增；取代 presales.customer 的客户主数据部分。名称加「外部工程」是因其统一语言含建设工程、招标项目 |
| 服务与定价目录 | masterdata.catalog | 服务与定价目录 | ServiceCatalog | masterdata | 新增；费率类词条要从 cost.costing（标准费率、费率版本）、contract.baseline（费率卡、计费费率卡、费率表）迁入 |
| 质量与认可 | delivery.acceptance | 质量与认可 | QualityAndAcceptance | delivery | 沿用 id，中文名由「质量返工与验收」改；认可 vs 验收见 X08 |
| 检查 | delivery.inspection | 检查 | Inspection | delivery | 新增（D-3） |
| 请假与加班 | organization.leave | 请假与加班 | LeaveAndOvertime | organization | 新增（D-5）；organization.attendance 只留工作日历，其词条「考勤与休假」(attendance) 已名不副实 |
| 合并后的售前 | 待 X01 | 推荐 presales.opportunity「商机与投标报价」 | | presales | 合并 presales.customer + presales.quoting |
| 合并后的合同 | 待 X01 | 推荐 contract.agreement「协议与变更索赔」 | | contract | 合并 baseline + changeorder + riskdelivery |

附带发现：草案与 ADR-0022 用到的 accounting.cashbank「银行与现金」、accounting.tax「税费」、accounting.fixedasset「固定资产」不在 bcs.yaml，词条无处可挂（进项发票即用到 accounting.tax），已写入 bcs-additions.yaml；operations.budgeting、organization.incentive、km.knowledge 按草案将退役，词条迁完后再删。

## 二、聚合与技术聚合（逐条查重）

| 草案词 | 查重 | 结论 | id / 中文 / 英文 | bc · kind |
|---|---|---|---|---|
| 框架协议限额 | 有 | 沿用 | framework_capacity_reservation 框架限额占用 | contract.baseline · aggregate（草案聚合还含限额本身，定义要补） |
| 开票与收入额度 | 无 | 新 | billing_revenue_limit / BillingRevenueLimit | receivables.wip · aggregate |
| 收入确认行 | 无；revenue_recognition 收入确认单为聚合 | 新（旧词条见 X06） | revenue_recognition_line / RevenueRecognitionLine | receivables.wip · entity |
| 供方协议 | 无；agreement 为客户侧实体 | 新 | supplier_agreement / SupplierAgreement（「供方」取 GB/T 19000） | procurement.subcontracting · aggregate |
| 供方协议履约额度 | 无 | 新 | supplier_agreement_performance_limit | 同上 · aggregate |
| 假期余额账户 | 无 | 新，改名 | leave_balance 假期余额（「账户」与会计账户混） | organization.leave · aggregate |
| 人工成本分配运行 | 无 | 新，改名 | labor_cost_allocation_result 人工成本分配结果（与同族「批次」「结果」一致，不称运行单据） | cost.costing · aggregate |
| 分摊运行 | allocation_result 分摊结果已含「运行分试算与全量」 | 沿用，不新登记 | allocation_result | cost.costing |
| 成本结转批次 | 有 | 沿用 | cost_transfer_batch | cost.costing |
| 过账 | 无；business_fact 经营事实（用户已确认）内容相同 | 待裁 X03 | 推荐沿用 business_fact | operations.facts |
| 收付↔权责科目映射表 | 无；accounting_mapping_rule_set 核算映射规则（事件到凭证模板）不同义 | 新 | cash_accrual_account_mapping 收付权责科目映射表 | accounting.ledger · aggregate |
| 控制科目授权表 | 无 | 新 | control_account_authorization | accounting.ledger · aggregate |
| 证据类型开放配置 | 无 | 新，补全名 | acceptance_evidence_type_admission 客户认可证据类型开放配置 | receivables.wip · aggregate |
| 人员薪酬档案 | 无；「档案」是 A8 表单叫法，「人员」是待裁用语 | 新，改名 | employee_compensation 员工薪酬（CAS 9 职工薪酬）；原名列方言别名 | organization.payroll · aggregate |
| 招标项目登记 | 无 | 新，改名 | tender_project 招标项目 | masterdata.counterparty · aggregate（若架构定为无聚合则 entity） |
| 我方参与记录 | 无 | 新 | own_participation_record | masterdata.counterparty · entity |
| 建设工程（附带） | 无 | 新 | construction_project / ConstructionProject | masterdata.counterparty · aggregate |
| 项目 WBS | wbs_node（中文名「WBS」，实体）是其内部分 | 新聚合根 | project_wbs 项目 WBS / ProjectWbs | delivery.execution · aggregate |
| 销项发票 | 无；invoice_issued 开票是事件 | 新 | output_invoice / OutputInvoice | receivables.wip · aggregate |
| 进项发票 | 无 | 新 | input_invoice / InputInvoice | accounting.tax · aggregate |
| 收款 / 付款 | cash_receipt 回款单、cash_received 回款已有 | 待裁 X05 | 推荐沿用回款单，新增付款单 | receivables.collection / payables.payment |
| 应收 / 应付往来款项 | ar_item 应收单范围窄且与 retention_money 有重叠 | 待裁 X04 | 推荐应收款项、应付款项 | |
| 发票入账占用 | 无 | 新 | invoice_posting_reservation | accounting.tax · entity |
| 核销占用 | 无；收付两侧同名 | 新，两条互列 homonyms | receivables_collection__ / payables_payment__cash_application_reservation | entity |
| 无异议声明 | 无 | 新（FIDIC no-objection） | no_objection_declaration | delivery.acceptance · entity |
| 主张标记 | 无；「标记」是实现用语 | 新，改名 | design_change_claim_status 设计变更主张状态 | delivery.acceptance · state |

## 三、方法用语：均不登记为词条

集合约束、跨聚合前置检查、同事务读锁、下游编排、技术聚合、运行单据、额度占用、证据规则、依赖方式（api / 事件 / 不校验）都是架构方法词，不是业务统一语言，不进 terms。归 common_words 与否：只有在代码标识里出现对应英文单词（orchestration、precondition、lock、reservation 等）才按 GlossaryTest 要求放进所在文件的 common_words，现在不需要。三点说明：「运行单据」不要进代码，沿用既有「批次」「结果」；「额度占用」的业务含义已由占用实体（reservation，如框架限额占用、预算占用）承载；「证据规则」每条是规则卡，不是词。

## 四、有订阅方的事件（17 条）

| 草案 | id / 英文 | bc |
|---|---|---|
| 银行账户已变更 | counterparty_bank_account_changed 往来单位银行账户已变更 CounterpartyBankAccountChanged（加前缀，免与我方资金账户混） | masterdata.counterparty |
| 商机已丢单 | opportunity_lost 商机已输单 OpportunityLost（与词表「赢单」相对，丢单列别名） | presales.quoting（待 X01） |
| 商机已放弃 | opportunity_abandoned OpportunityAbandoned | 同上 |
| 投标已中标 / 未中标 | bid_won BidWon / bid_lost BidLost；聚合 bid 投标 Bid 一并登记 | 同上 |
| 协议已终止 | agreement_terminated AgreementTerminated | contract.baseline（待 X01） |
| 协议已结算 | 待裁 X11，推荐 AgreementFinalAccountConfirmed 协议竣工结算已确认 | |
| 新基线已生效 | agreement_baseline_activated AgreementBaselineActivated | 同上 |
| 合同—项目关联已登记 | agreement_project_linked 协议项目关联已登记 AgreementProjectLinked（合同是协议的一种类型） | 同上 |
| 项目已建立 / 已结项 / 已终止 / 已取消 | project_established ProjectEstablished、project_closed ProjectClosed、project_terminated ProjectTerminated、project_cancelled ProjectCancelled | delivery.execution |
| 交付物已送达 / 已认可 | 待裁 X07、X08，推荐 DeliverableDelivered、DeliverableAccepted | delivery.acceptance |
| 无异议已声明 | no_objection_declared NoObjectionDeclared | delivery.acceptance |
| 分包变更已生效 | subcontract_change_activated SubcontractChangeActivated；聚合 subcontract_change 分包变更一并登记 | procurement.subcontracting |

## 五、ADR-0047 六个词（均无同名，已入 proposed.yaml，bc delivery.acceptance）

视为同意 deemed_acceptance（state）；认可日 acceptance_date（value_object）；审查期 review_period（value_object）；期满记录 expiry_record（entity）；客户认可证据类型 acceptance_evidence_type（state，bc 随 Q29）；送达记录 待裁 X07（service_record 易与「服务」混，推荐 delivery_record）。X08 定后补各词条定义里的「认可 ≠ 验收」措辞。

## 六、STAGE_TYPES_DRAFT §7 的同名异义

- 初设 / 扩初 / 初步设计（X10）：正式名「初步设计」，「初设」作缩写别名，「扩初设计」不并入（景观产品线深化阶段内的子阶段名）。bc masterdata.catalog。
- 结算三处（X11）：「结算」只留给 final_account 竣工结算（receivables.wip）；阶段 7 正式名「收尾」，结算作别名；造价线四个子阶段称投资估算、设计概算、施工图预算、竣工结算审核（造价线的估算与成本口径的估算不同义）；协议事件改称协议竣工结算已确认。另：AGG-73「月度薪酬结算」是第四处，建议改月度薪酬核算。
- 附带（X09）：「阶段」英文取 Phase 还是 Stage。

## 七、冲突与 B1 会报的问题

1. 新 bc 不在 bcs.yaml：规则 bc 报错。先并入 bcs-additions.yaml。
2. 合同三 BC 合并后 ResponsibleParty（责任方 / 责任承担方）同 bc 同英文名：规则 unique_en 报错，须先改一个（X01）。
3. 草案 AGG-27「交付物」为 delivery.acceptance 的聚合，而 deliverable 交付物是 delivery.execution 的实体：跨 bc 同名须 homonyms 互列，且 kind 与归属要对齐。
4. 草案 AGG-38「证据」若英文名取 Evidence，与 platform.integration 的 evidence「取证」同名：须互列 homonyms，建议另取 AcceptanceEvidence。
5. agreement 协议现为实体，草案 AGG-13 为聚合：kind 要改。供方侧五个协议类型值（租赁合同、职业责任保险单、居间协议、劳务派遣协议等）现登记在 contract.baseline，应随供方协议迁到采购子域。
6. 草案 AGG-03–07 与现有词条重叠：标准成本费率表 ≈ standard_cost_rate_card 标准费率，协议价目 ≈ rate_card 费率卡 / bill_rate_card 计费费率卡，须并名或区分（不在 §8，另提醒 one-architecture）。
7. 事件只有中文名的草案，英文须为聚合名加过去分词；见第四部分。
8. 词表已有 customer 聚合与往来单位同源：X02。

## 八、需要用户裁定的（11 项，见 decide-items.json）

| 号 | 题 | 推荐 | 备选 |
|---|---|---|---|
| X01 | 合并后售前、合同 BC 标识 | 新标识 presales.opportunity、contract.agreement | 沿用 presales.quoting、contract.baseline 改中文名 |
| X02 | customer 客户的去向 | 弃用，客户降为往来单位角色 | 保留客户聚合并存 |
| X03 | AGG-77 叫过账还是经营事实 | 沿用经营事实，过账只作动作 | 新登记过账，经营事实降为实体 |
| X04 | 应收、应付往来款项 vs 应收单 | 应收款项、应付款项，应收单并入 | 字面沿用往来款项；或保留应收单扩义 |
| X05 | 收款/付款 vs 回款单 | 沿用回款单，新增付款单 | 改称收款、付款 |
| X06 | 收入确认是单还是规则 | 降为业务规则，加收入确认行 | 保留收入确认单为聚合 |
| X07 | 送达记录英文名 | DeliveryRecord | ServiceRecord；SubmissionDeliveryRecord |
| X08 | 认可与验收是否同义 | 并存分工 | 统一用验收；统一用认可 |
| X09 | 阶段 Phase 还是 Stage | Phase | Stage |
| X10 | 初设 / 扩初 / 初步设计 | 初步设计为正式名，扩初不并入 | 三者合一；扩初另立阶段 |
| X11 | 结算三处 | 阶段 7 称收尾，事件称竣工结算已确认，造价子阶段加限定词 | 沿用结算，靠定义区分 |

拿不准：X11 里「协议已结算」的含义是我据 AGG-13 的「最终价款」属性推断的，请财务确认；AGG-37/38 证据归属随 Q29；招标项目是否建聚合；英文 OutputInvoice 与 SalesInvoice 二选一，中文不变。
