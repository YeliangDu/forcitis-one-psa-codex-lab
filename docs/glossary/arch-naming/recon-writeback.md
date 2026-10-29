# NAM-1017-GLO-06：返工第 1 轮写回报告

日期：2026-10-17。HEAD：`47ecece952d60c835005a20a039817e5ea4eb966`。当前独立 worktree；未提交、未推送、未起容器。

**状态：已实施卡内写回，验收未通过，任务不算完成。** 默认 `scripts/precheck.sh` 为 **7 PASS、2 FAIL、10 SKIP**，退出 1。失败分别是 G41 仍发现现有代码 `RoleDeleted`、前端依赖未安装。未修改任何 Java、守护、验收测试或断言；没有新增基线。上一轮未实施的 Java/supersedes 方案由本轮扩卡处理方式取代，不再提出该方案。

## 写回数量与边界

- 正式词条新增 **56 条**，更新既有词条 **7 条**；总数 **729 → 785**。漏登 60 项中 56 项登记，4 项不另登记，逐项见下表。
- A 类 **66 项已逐项核对落点**：24 条 `proposed` 中 **22 条新建转正、2 条复用已有正式目标**；22 条属于上述新增 56 条，不重复计数。另 **42 条 `deprecated` 已有正式目标**，不是 42 个新概念，保留原兼容条目，不能把它们报为本轮新增转正或已删除。
- 从提议源删除 **24 条**，涉及 **4 个 YAML 文件**；原文件头逐字节保留，剩余条目及其他顶层数据逐项不变。`code-aggregates-events.yaml`、`step3-objects.yaml` 删空后保留原文件头及 `terms: []`。
- 基线仅删除已修好的 **6 个键**，同步把 `event_name.count` 从 5 减为 0、`command_name.count` 从 1 减为 0；其余键、说明、负责人和上限不变。
- B105、C3 以及现有 156 条错误码提议不变；C3 未写 N7。

## 决策源与重放

新增 `glossary/decisions-recon.yaml`：含 60 项漏登对照、63 项词条操作、A66 原提议快照及正式目标、97 个来源定位与 SHA-256。裁定来源回读了本地 JSON 答复／题面、intent、NM 决策和 ADR-0049；补丁准备阶段逐项比对 JSON 原答复及 intent 原文，运行期验证来源哈希、确认来源及登记依据。未把题面预选或草稿当作用户提交。

`apply_recon.py` 复用 `apply_nm.validate_term`、旧值追加与通用短语禁用保护；原 `apply_nm.py` 未改。新增须具备所有必填字段，id/snake 与正式词表及提议目录比对，snake 从英文机械派生；更新用 sparse patch，只允许明确列出的答复字段，原 confirmed 追加、无关字段保留。`build_terms.py` 只增加两行调用，生成后仍走原有 term_rules，无过滤或豁免。

新词的英文、kind、scope、bc 是本卡授权的**设计定稿**，均在 note 明标，不称这些字段逐一获得用户批准。三种法定申报登记为同一申报记录的种类用语，采购／租赁变更不另拆聚合；三项新指标补齐 formula、grain、as_of，不增加指标基线。

协议价格与协议价目按原话区分为价格值（`value_object`）与管理条目、生效期、版本的价格表（`aggregate`）；N5 旧草稿把两者都候选为同一 AGG 的聚合，本轮没有照抄成两个同义聚合。这里只登记已经使用的两种业务含义，设计边界在 note 标识。

`role_deleted` 沿用永久概念 id，首选英文按 GL B2-B06 改为 `RoleAbolished`，原英文留 old-code 别名；未另造 `role_abolished` 概念。`deemed_acceptance` 按较新裁定与 ADR-0049 定为 `receivables.wip` 业务规则，不转正过时的交付物状态。`migration_tie_out` 保留原 ACL／过渡期范围；`source_reference_refused` 按 GL B2-B05 定为 ACL／过渡期。

## 漏登 60 项对照

下表序号对应上一卡 60 项；“登记”是实际正式词条，未把定义中包含某个字样视为登记。所有来源均在 decisions-recon 的 sources 保存哈希与原答复。

| 序 | 中文 | 结果／词条 | 英文／kind／范围与 BC | 裁定出处 |
| --- | --- | --- | --- | --- |
| 1 | 个人所得税申报 | 登记 `personal_income_tax_filing` | PersonalIncomeTaxFiling / value_object / domain · organization.payroll | `docs/decisions/decision-pages/answers/nm4.json#NM4~NM4-09` |
| 2 | 住房公积金申报 | 登记 `housing_provident_fund_filing` | HousingProvidentFundFiling / value_object / domain · organization.payroll | `docs/decisions/decision-pages/answers/nm4.json#NM4~NM4-09` |
| 3 | 余额调节表 | 登记 `bank_reconciliation_statement` | BankReconciliationStatement / read_model / domain · accounting.cashbank | `docs/decisions/decision-pages/answers/fd.json#FD~FD-31` |
| 4 | 净收入 | 登记 `net_revenue` | NetRevenue / metric / domain · operations.policy | `docs/decisions/decision-pages/answers/fd.json#FD~FD-41` |
| 5 | 减值计划 | 登记 `impairment_plan` | ImpairmentPlan / read_model / domain · operations.planning | `docs/decisions/decision-pages/answers/fd.json#FD~FD-36` |
| 6 | 减值账龄 | 登记 `impairment_aging` | ImpairmentAging / value_object / domain · receivables.collection | `docs/decisions/decision-pages/answers/fd.json#FD~FD-37` |
| 7 | 区域 | 登记 `region` | Region / entity / domain · organization.workforce | `docs/decisions/decision-pages/answers/f1.json#F1~F1-05` |
| 8 | 受限 | 登记 `fund_account__restricted` | Restricted / value_object / domain · accounting.cashbank | `docs/decisions/decision-pages/answers/fd.json#FD~FD-33` |
| 9 | 可随时支用 | 登记 `fund_account__available_on_demand` | AvailableOnDemand / value_object / domain · accounting.cashbank | `docs/decisions/decision-pages/answers/fd.json#FD~FD-33` |
| 10 | 含税收入 | 登记 `revenue_including_tax` | RevenueIncludingTax / metric / domain · operations.policy | `docs/decisions/decision-pages/answers/fd.json#FD~FD-14` |
| 11 | 实际成本费率 | 登记 `actual_cost_rate` | ActualCostRate / value_object / domain · cost.costing | `docs/decisions/2026-10-15-211139-one-architecture-我们存在三套费率-标准成本费率用.md` |
| 12 | 对以前期间的更正 | 登记 `prior_period_correction` | PriorPeriodCorrection / value_object / domain · accounting.ledger | `docs/decisions/decision-pages/answers/fd.json#FD~FD-23` |
| 13 | 已计提 | 登记 `impairment__recognized` | ImpairmentRecognized / value_object / domain · accounting.ledger | `docs/decisions/decision-pages/answers/fd.json#FD~FD-36` |
| 14 | 度量事件 | 登记 `measurement_event` | MeasurementEvent / value_object / domain · operations.policy | `docs/decisions/2026-10-16-112800-one-architecture-锁定对照标准与-ADR-0047.md` |
| 15 | 扩初设计 | 登记 `expanded_preliminary_design` | ExpandedPreliminaryDesign / value_object / domain · masterdata.catalog | `docs/decisions/decision-pages/answers/gl2.json#GL2~B2-X10` |
| 16 | 新签净额 | 登记 `net_new_contract_value` | NetNewContractValue / metric / domain · operations.policy | `docs/decisions/decision-pages/answers/fd.json#FD~FD-42` |
| 17 | 权责账簿 | 登记 `accrual_ledger` | AccrualLedger / entity / domain · accounting.ledger | `docs/decisions/decision-pages/answers/fd.json#FD~FD-43` |
| 18 | 标准成本费率 | 登记 `standard_cost_rate` | StandardCostRate / value_object / domain · cost.costing | `docs/decisions/2026-10-15-211139-one-architecture-我们存在三套费率-标准成本费率用.md` |
| 19 | 现金保证金 | 不另登记：NM2 已并入保证金与押金；receivable 的说明保留现金保证金单独列示，不再建同义概念。 目标 `bond_and_deposit`。 | — | `docs/decisions/decision-pages/answers/nm2.json#NM2~NM2-11` |
| 20 | 社会保险申报 | 登记 `social_insurance_filing` | SocialInsuranceFiling / value_object / domain · organization.payroll | `docs/decisions/decision-pages/answers/nm4.json#NM4~NM4-09` |
| 21 | 租赁变更 | 登记 `lease_modification` | LeaseModification / entity / domain · procurement.subcontracting | `docs/decisions/decision-pages/answers/nm3.json#NM3~NM3-11` |
| 22 | 第 13 期 | 登记 `thirteenth_period` | ThirteenthPeriod / value_object / domain · accounting.period | `docs/decisions/2026-10-15-124316-one-coordinator-财务确认单第-2-版答复的-13.md` |
| 23 | 管理账龄 | 登记 `management_aging` | ManagementAging / value_object / domain · receivables.collection | `docs/decisions/decision-pages/answers/fd.json#FD~FD-37` |
| 24 | 经营产值 | 不另登记：业务上与收入分开，但用语问题尚未裁定，按工作卡明确要求不登记；不存在已核实的正式替代词条。 | — | `docs/decisions/2026-10-16-144613-one-architecture-收入确认事实页第-1-组提交-8.md`；`docs/decisions/decision-pages/answers/a1.json#A1~A1-01` |
| 25 | 职位 | 登记 `job_position` | JobPosition / value_object / domain · organization.workforce | `docs/decisions/decision-pages/answers/nm4.json#NM4~NM4-07` |
| 26 | 计费费率 | 登记 `billing_rate` | BillingRate / value_object / domain · masterdata.catalog | `docs/decisions/2026-10-15-211139-one-architecture-我们存在三套费率-标准成本费率用.md` |
| 27 | 财务负责人 | 不另登记：后续 NM5 已明确为各法人的财务职责并登记法人财务负责人，旧泛称不另立词条。 目标 `legal_entity_finance_head`。 | — | `docs/decisions/decision-pages/answers/nm5.json#NM5~NM5-01`；`docs/decisions/decision-pages/answers/a1.json#A1~A1-02` |
| 28 | 费用所属期间 | 登记 `expense_attribution_period` | ExpenseAttributionPeriod / value_object / domain · expense.spend | `docs/decisions/decision-pages/answers/fd.json#FD~FD-16` |
| 29 | 迁入事实差异说明 | 登记 `migrated_fact_difference_statement` | MigratedFactDifferenceStatement / read_model / domain · accounting.ledger | `docs/decisions/decision-pages/answers/fd.json#FD~FD-19` |
| 30 | 采购变更 | 登记 `purchase_modification` | PurchaseModification / entity / domain · procurement.subcontracting | `docs/decisions/decision-pages/answers/nm3.json#NM3~NM3-11` |
| 31 | 证据 | 登记 `acceptance_evidence` | AcceptanceEvidence / aggregate / domain · delivery.acceptance | `docs/decisions/2026-10-16-114200-one-architecture-符合性审查确认页提交-F-1-至.md` |
| 32 | 协议价格 | 登记 `agreement_price` | AgreementPrice / value_object / domain · masterdata.catalog | `docs/decisions/2026-10-15-202939-one-architecture-售前业务对象问题页保存-线索单独.md` |
| 33 | 协议价目 | 登记 `agreement_price_list` | AgreementPriceList / aggregate / domain · masterdata.catalog | `docs/decisions/2026-10-15-210513-one-architecture-售前聚合与-BC-确认页保存-一.md` |
| 34 | 任务已超时 | 登记 `approval_task_became_overdue` | ApprovalTaskBecameOverdue / domain_event / domain · platform.approval | `docs/decisions/decision-pages/answers/gl.json#GL~B2-A27`；`docs/decisions/decision-pages/answers/gl.json#GL~B2-B02` |
| 35 | 投标 | 登记 `bid` | Bid / aggregate / domain · presales.opportunity | `docs/decisions/2026-10-15-202939-one-architecture-售前业务对象问题页保存-线索单独.md` |
| 36 | 兑付状态 | 登记 `bill_redemption_status` | BillRedemptionStatus / state / domain · receivables.collection | `docs/decisions/2026-10-16-090004-one-architecture-阶段写实-对象回补与-ADR-0.md` |
| 37 | 已逾期 | 登记 `bill_redemption_status__overdue` | BillOverdue / value_object / domain · receivables.collection | `docs/decisions/2026-10-16-090004-one-architecture-阶段写实-对象回补与-ADR-0.md` |
| 38 | 收到 | 登记 `bill_redemption_status__received` | Received / value_object / domain · receivables.collection | `docs/decisions/2026-10-16-090004-one-architecture-阶段写实-对象回补与-ADR-0.md` |
| 39 | 已兑付 | 登记 `bill_redemption_status__redeemed` | Redeemed / value_object / domain · receivables.collection | `docs/decisions/2026-10-16-090004-one-architecture-阶段写实-对象回补与-ADR-0.md` |
| 40 | 建设工程 | 登记 `construction_project` | ConstructionProject / aggregate / domain · masterdata.counterparty | `docs/decisions/2026-10-15-234418-one-architecture-答复复核-R1–R5-提交-已提.md` |
| 41 | 继续教育学时 | 登记 `continuing_education_hours` | ContinuingEducationHours / value_object / domain · organization.workforce | `docs/decisions/2026-10-16-090004-one-architecture-阶段写实-对象回补与-ADR-0.md` |
| 42 | 往来单位 | 登记 `counterparty` | Counterparty / aggregate / domain · masterdata.counterparty | `docs/decisions/2026-10-15-210513-one-architecture-售前聚合与-BC-确认页保存-一.md` |
| 43 | 视为同意 | 登记 `deemed_acceptance` | DeemedAcceptance / business_rule / domain · receivables.wip | `docs/decisions/2026-10-16-061232-one-architecture-阶段类型与-BC-划分确认页提交.md` |
| 44 | 施工图审查合格书 | 登记 `drawing_review_certificate` | DrawingReviewCertificate / value_object / domain · delivery.acceptance | `docs/decisions/2026-10-16-090004-one-architecture-阶段写实-对象回补与-ADR-0.md` |
| 45 | 施工图审查意见 | 登记 `drawing_review_opinion` | DrawingReviewOpinion / value_object / domain · delivery.acceptance | `docs/decisions/2026-10-16-090004-one-architecture-阶段写实-对象回补与-ADR-0.md` |
| 46 | 劳动合同 | 登记 `employment_contract` | EmploymentContract / aggregate / domain · organization.workforce | `docs/decisions/2026-10-16-114200-one-architecture-符合性审查确认页提交-F-1-至.md` |
| 47 | 证据关联记录 | 登记 `evidence_link_record` | EvidenceLinkRecord / aggregate / domain · delivery.acceptance | `docs/decisions/2026-10-16-114200-one-architecture-符合性审查确认页提交-F-1-至.md` |
| 48 | 进项发票 | 登记 `input_invoice` | InputInvoice / aggregate / domain · accounting.tax | `docs/decisions/2026-10-16-072440-one-architecture-对象完整性与方法第-4-稿确认页.md` |
| 49 | 线索 | 登记 `lead` | Lead / aggregate / domain · presales.opportunity | `docs/decisions/2026-10-15-202939-one-architecture-售前业务对象问题页保存-线索单独.md`；`docs/decisions/2026-10-15-205406-one-architecture-售前信息图确认页保存-合同与项目.md` |
| 50 | 诉讼 | 登记 `litigation` | Litigation / value_object / domain · receivables.collection | `docs/decisions/2026-10-16-090004-one-architecture-阶段写实-对象回补与-ADR-0.md` |
| 51 | 迁入核对 | 登记 `migration_tie_out` | MigrationTieOut / entity / acl · platform.integration | `docs/glossary/decisions-gate-r-1b-0.json#GATE-R-1b-0-G03` |
| 52 | 无异议声明 | 登记 `no_objection_declaration` | NoObjectionDeclaration / entity / domain · delivery.acceptance | `docs/decisions/2026-10-16-061232-one-architecture-阶段类型与-BC-划分确认页提交.md` |
| 53 | 签约前投入 | 登记 `precontract_investment` | PrecontractInvestment / aggregate / domain · cost.costing | `docs/decisions/2026-10-15-124316-one-coordinator-财务确认单第-2-版答复的-13.md` |
| 54 | 往来款项处置分类 | 登记 `receivable_disposition_category` | ReceivableDispositionCategory / state / domain · receivables.collection | `docs/decisions/2026-10-16-090004-one-architecture-阶段写实-对象回补与-ADR-0.md` |
| 55 | 收入确认行 | 登记 `revenue_recognition_line` | RevenueRecognitionLine / entity / domain · receivables.wip | `docs/decisions/decision-pages/answers/gl2.json#GL2~B2-X06` |
| 56 | 角色已废除 | 登记 `role_deleted` | RoleAbolished / domain_event / domain · platform.identity | `docs/decisions/decision-pages/answers/gl.json#GL~B2-B06` |
| 57 | 来源对照登记已被拒绝 | 登记 `source_reference_refused` | SourceReferenceRefused / domain_event / acl · platform.integration | `docs/decisions/decision-pages/answers/gl.json#GL~B2-A54`；`docs/decisions/decision-pages/answers/gl.json#GL~B2-B05` |
| 58 | 分包变更 | 登记 `subcontract_change` | SubcontractChange / aggregate / domain · procurement.subcontracting | `docs/decisions/decision-pages/answers/nm3.json#NM3~NM3-11` |
| 59 | 工时单已撤回审批 | 登记 `timesheet_approval_withdrawn` | TimesheetApprovalWithdrawn / domain_event / domain · cost.timesheet | `docs/decisions/decision-pages/answers/gl.json#GL~B2-B07` |
| 60 | 工时单审批已撤销 | 不另登记：GL B2-B07 对同一 TimesheetCancelled 作了专项改名裁定；B2-A02 的概括同意由专项裁定取代，不登记第二个同义事件。 目标 `timesheet_approval_withdrawn`。 | — | `docs/decisions/decision-pages/answers/gl.json#GL~B2-A02` |

## 不一致 7 项修正

没有批量覆盖原词条。仅下列指称、机械 snake、对应旧名别名、出处与 note 发生变化；confirmed 追加，原确认保留。旧英文未进入 forbidden，通用旧中文按 standard-zh 别名保留。

| id | 原名 → 裁定后 | 裁定出处 |
| --- | --- | --- |
| `contract_modification` | zh: 合同变更（会计口径） → 合同已变更；en: ContractModification → ContractModified；snake: contract_modification → contract_modified | `docs/decisions/decision-pages/answers/gl.json#GL~B2-B09` |
| `ecl_alert` | en: EclAlert → EclAlerted；snake: ecl_alert → ecl_alerted | `docs/decisions/decision-pages/answers/gl.json#GL~B2-B09` |
| `retention_money_release_due` | en: RetentionMoneyReleaseDue → RetentionMoneyReleaseDueReached；snake: retention_money_release_due → retention_money_release_due_reached | `docs/decisions/decision-pages/answers/gl.json#GL~B2-B09` |
| `revenue_reduction` | en: RevenueReduction → RevenueReduced；snake: revenue_reduction → revenue_reduced | `docs/decisions/decision-pages/answers/gl.json#GL~B2-B09` |
| `rule_execution` | en: RuleExecution → RuleExecuted；snake: rule_execution → rule_executed | `docs/decisions/decision-pages/answers/gl.json#GL~B2-B09` |
| `forced_logout` | en: ForcedLogout → ForceLogout；snake: forced_logout → force_logout | `docs/decisions/decision-pages/answers/gl.json#GL~B2-B10` |
| `standard_cost_rate_card` | zh: 标准费率 → 标准成本费率表 | `docs/decisions/decision-pages/answers/nm3.json#NM3~NM3-08` |

## 提议源清理：24 条、4 个文件

| 文件 | 精确删除的 id | 条数 |
| --- | --- | --- |
| `backend/glossary/proposed/code-aggregates-events.yaml` | `approval_task_became_overdue`、`role_deleted`、`source_reference_refused`、`timesheet_approval_withdrawn` | 4 |
| `backend/glossary/proposed/data-migration.yaml` | `migration_tie_out` | 1 |
| `backend/glossary/proposed/enterprise-aggregates.yaml` | `bid`、`construction_project`、`counterparty`、`deemed_acceptance`、`employee_compensation`、`input_invoice`、`leave_balance`、`no_objection_declaration`、`revenue_recognition_line`、`subcontract_change` | 10 |
| `backend/glossary/proposed/step3-objects.yaml` | `bill_redemption_status`、`bill_redemption_status__overdue`、`bill_redemption_status__received`、`bill_redemption_status__redeemed`、`continuing_education_hours`、`drawing_review_certificate`、`drawing_review_opinion`、`litigation`、`receivable_disposition_category` | 9 |

`backend/glossary/proposed/README.md` 由 `python3 backend/glossary/build_proposed_index.py` 重新生成，26 个文件、150 条非错误码条目（原为 174 条）。两个空文件仍列出原导语，没有手改 README。

### A66 逐项正式落点与删除状态

42 条 deprecated 旧名的保留依据已回读：`GlossaryTest` 当前仍提示 act、department、keep、overhead、size 由旧名兼容条目覆盖；`DocumentTypeCodeTest.everyCodeIsAGlossaryWord` 仍直接要求 qt、ts、jv 的 snake。当前 Java 读取器的别名不能替代这些登记。扩卡只准删“已转正条目”，未授权 Java 改名，因此不把“有 replaced_by”当成“旧名代码已迁移”的证据。其余 deprecated 也未证明全部前后端使用已迁完，统一保留原快照，交原改名卡处理。没有新建这 42 个旧名概念、也没有改它们的状态或内容。

| 序 | 原文件 | 原 id | 正式目标 | 实际处理 |
| --- | --- | --- | --- | --- |
| 1 | `backend/glossary/proposed/code-aggregates-events.yaml` | `timesheet_approval_withdrawn` | `timesheet_approval_withdrawn` | 已新建转正，源条目已删 |
| 2 | `backend/glossary/proposed/code-aggregates-events.yaml` | `approval_task_became_overdue` | `approval_task_became_overdue` | 已新建转正，源条目已删 |
| 3 | `backend/glossary/proposed/code-aggregates-events.yaml` | `role_deleted` | `role_deleted` | 已新建转正，源条目已删 |
| 4 | `backend/glossary/proposed/code-aggregates-events.yaml` | `source_reference_refused` | `source_reference_refused` | 已新建转正，源条目已删 |
| 5 | `backend/glossary/proposed/data-migration.yaml` | `migration_tie_out` | `migration_tie_out` | 已新建转正，源条目已删 |
| 6 | `backend/glossary/proposed/enterprise-aggregates.yaml` | `counterparty` | `counterparty` | 已新建转正，源条目已删 |
| 7 | `backend/glossary/proposed/enterprise-aggregates.yaml` | `construction_project` | `construction_project` | 已新建转正，源条目已删 |
| 8 | `backend/glossary/proposed/enterprise-aggregates.yaml` | `revenue_recognition_line` | `revenue_recognition_line` | 已新建转正，源条目已删 |
| 9 | `backend/glossary/proposed/enterprise-aggregates.yaml` | `input_invoice` | `input_invoice` | 已新建转正，源条目已删 |
| 10 | `backend/glossary/proposed/enterprise-aggregates.yaml` | `subcontract_change` | `subcontract_change` | 已新建转正，源条目已删 |
| 11 | `backend/glossary/proposed/enterprise-aggregates.yaml` | `leave_balance` | `leave_balance_account` | 已复用，源条目已删 |
| 12 | `backend/glossary/proposed/enterprise-aggregates.yaml` | `employee_compensation` | `employee_compensation_profile` | 已复用，源条目已删 |
| 13 | `backend/glossary/proposed/enterprise-aggregates.yaml` | `no_objection_declaration` | `no_objection_declaration` | 已新建转正，源条目已删 |
| 14 | `backend/glossary/proposed/enterprise-aggregates.yaml` | `deemed_acceptance` | `deemed_acceptance` | 已新建转正，源条目已删 |
| 15 | `backend/glossary/proposed/enterprise-aggregates.yaml` | `bid` | `bid` | 已新建转正，源条目已删 |
| 16 | `backend/glossary/proposed/platform-numbering.yaml` | `qt` | `document_type_code` | 目标已正式；原 deprecated 兼容条目保留 |
| 17 | `backend/glossary/proposed/platform-numbering.yaml` | `ts` | `document_type_code` | 目标已正式；原 deprecated 兼容条目保留 |
| 18 | `backend/glossary/proposed/platform-numbering.yaml` | `jv` | `document_type_code` | 目标已正式；原 deprecated 兼容条目保留 |
| 19 | `backend/glossary/proposed/round3-pending-renames.yaml` | `round3_pending__act` | `handle` | 目标已正式；原 deprecated 兼容条目保留 |
| 20 | `backend/glossary/proposed/round3-pending-renames.yaml` | `round3_pending__assigned` | `task_assigned` | 目标已正式；原 deprecated 兼容条目保留 |
| 21 | `backend/glossary/proposed/round3-pending-renames.yaml` | `round3_pending__completed` | `approval_completed` | 目标已正式；原 deprecated 兼容条目保留 |
| 22 | `backend/glossary/proposed/round3-pending-renames.yaml` | `round3_pending__escalated` | `rerouted_to_superior` | 目标已正式；原 deprecated 兼容条目保留 |
| 23 | `backend/glossary/proposed/round3-pending-renames.yaml` | `round3_pending__fallback` | `administrator_fallback` | 目标已正式；原 deprecated 兼容条目保留 |
| 24 | `backend/glossary/proposed/round3-pending-renames.yaml` | `round3_pending__form` | `approval_field` | 目标已正式；原 deprecated 兼容条目保留 |
| 25 | `backend/glossary/proposed/round3-pending-renames.yaml` | `round3_pending__round` | `approval_round` | 目标已正式；原 deprecated 兼容条目保留 |
| 26 | `backend/glossary/proposed/round3-pending-renames.yaml` | `round3_pending__row` | `line_task` | 目标已正式；原 deprecated 兼容条目保留 |
| 27 | `backend/glossary/proposed/round3-pending-renames.yaml` | `round3_pending__deadline` | `approval_time_limit` | 目标已正式；原 deprecated 兼容条目保留 |
| 28 | `backend/glossary/proposed/round3-pending-renames.yaml` | `round3_pending__effect` | `task_effect` | 目标已正式；原 deprecated 兼容条目保留 |
| 29 | `backend/glossary/proposed/round3-pending-renames.yaml` | `round3_pending__release_of_task` | `unclaim` | 目标已正式；原 deprecated 兼容条目保留 |
| 30 | `backend/glossary/proposed/round3-pending-renames.yaml` | `round3_pending__ui_business_summary` | `approval_summary` | 目标已正式；原 deprecated 兼容条目保留 |
| 31 | `backend/glossary/proposed/round3-pending-renames.yaml` | `round3_pending__ui_proxy_handling` | `ui_proxy_handling` | 目标已正式；原 deprecated 兼容条目保留 |
| 32 | `backend/glossary/proposed/round3-pending-renames.yaml` | `round3_pending__task_participant` | `task_participant` | 目标已正式；原 deprecated 兼容条目保留 |
| 33 | `backend/glossary/proposed/round3-pending-renames.yaml` | `round3_pending__decision` | `task_decision` | 目标已正式；原 deprecated 兼容条目保留 |
| 34 | `backend/glossary/proposed/round3-pending-renames.yaml` | `round3_pending__resulted` | `task_handled` | 目标已正式；原 deprecated 兼容条目保留 |
| 35 | `backend/glossary/proposed/round3-pending-renames.yaml` | `round3_pending__skipped` | `notification_skipped` | 目标已正式；原 deprecated 兼容条目保留 |
| 36 | `backend/glossary/proposed/round3-pending-renames.yaml` | `round3_pending__project_manager_review` | `project_manager_approval` | 目标已正式；原 deprecated 兼容条目保留 |
| 37 | `backend/glossary/proposed/round3-pending-renames.yaml` | `round3_pending__regional_head_review` | `org_unit_head_approval` | 目标已正式；原 deprecated 兼容条目保留 |
| 38 | `backend/glossary/proposed/round3-pending-renames.yaml` | `round3_pending__supervisor_review` | `supervisor_approval` | 目标已正式；原 deprecated 兼容条目保留 |
| 39 | `backend/glossary/proposed/round3-pending-renames.yaml` | `round3_pending__cutoff` | `withdrawal_cutoff` | 目标已正式；原 deprecated 兼容条目保留 |
| 40 | `backend/glossary/proposed/round3-pending-renames.yaml` | `round3_pending__administrator_mode` | `administrator_mode` | 目标已正式；原 deprecated 兼容条目保留 |
| 41 | `backend/glossary/proposed/round3-pending-renames.yaml` | `round3_pending__authorization` | `grant_permission` | 目标已正式；原 deprecated 兼容条目保留 |
| 42 | `backend/glossary/proposed/round3-pending-renames.yaml` | `round3_pending__account_disabled` | `account_disabled` | 目标已正式；原 deprecated 兼容条目保留 |
| 43 | `backend/glossary/proposed/round3-pending-renames.yaml` | `round3_pending__permission_adjustment_kind__sensitive_role_holder` | `permission_adjustment_kind__sensitive_role_review` | 目标已正式；原 deprecated 兼容条目保留 |
| 44 | `backend/glossary/proposed/round3-pending-renames.yaml` | `round3_pending__size` | `byte_count` | 目标已正式；原 deprecated 兼容条目保留 |
| 45 | `backend/glossary/proposed/round3-pending-renames.yaml` | `round3_pending__keep` | `data_retention_period` | 目标已正式；原 deprecated 兼容条目保留 |
| 46 | `backend/glossary/proposed/round3-pending-renames.yaml` | `round3_pending__worm` | `write_once_read_many` | 目标已正式；原 deprecated 兼容条目保留 |
| 47 | `backend/glossary/proposed/round3-pending-renames.yaml` | `round3_pending__voucher_numbering_rule` | `journal_voucher_numbering_rule` | 目标已正式；原 deprecated 兼容条目保留 |
| 48 | `backend/glossary/proposed/round3-pending-renames.yaml` | `round3_pending__rule_version` | `numbering_rule_version` | 目标已正式；原 deprecated 兼容条目保留 |
| 49 | `backend/glossary/proposed/round3-pending-renames.yaml` | `round3_pending__overhead_hours` | `indirect_hours` | 目标已正式；原 deprecated 兼容条目保留 |
| 50 | `backend/glossary/proposed/round3-pending-renames.yaml` | `round3_pending__workforce__email` | `workforce__email_address` | 目标已正式；原 deprecated 兼容条目保留 |
| 51 | `backend/glossary/proposed/round3-pending-renames.yaml` | `round3_pending__department` | `org_unit` | 目标已正式；原 deprecated 兼容条目保留 |
| 52 | `backend/glossary/proposed/round3-pending-renames.yaml` | `round3_pending__ui_task_handler` | `actor` | 目标已正式；原 deprecated 兼容条目保留 |
| 53 | `backend/glossary/proposed/round3-pending-renames.yaml` | `round3_pending__running` | `ui_approval_running` | 目标已正式；原 deprecated 兼容条目保留 |
| 54 | `backend/glossary/proposed/round3-pending-renames.yaml` | `round3_pending__ts` | `document_type_code` | 目标已正式；原 deprecated 兼容条目保留 |
| 55 | `backend/glossary/proposed/round3-pending-renames.yaml` | `round3_pending__qt` | `document_type_code` | 目标已正式；原 deprecated 兼容条目保留 |
| 56 | `backend/glossary/proposed/round3-pending-renames.yaml` | `round3_pending__jv` | `document_type_code` | 目标已正式；原 deprecated 兼容条目保留 |
| 57 | `backend/glossary/proposed/round3-pending-renames.yaml` | `round3_pending__journal_voucher` | `journal_voucher` | 目标已正式；原 deprecated 兼容条目保留 |
| 58 | `backend/glossary/proposed/step3-objects.yaml` | `bill_redemption_status` | `bill_redemption_status` | 已新建转正，源条目已删 |
| 59 | `backend/glossary/proposed/step3-objects.yaml` | `bill_redemption_status__received` | `bill_redemption_status__received` | 已新建转正，源条目已删 |
| 60 | `backend/glossary/proposed/step3-objects.yaml` | `bill_redemption_status__redeemed` | `bill_redemption_status__redeemed` | 已新建转正，源条目已删 |
| 61 | `backend/glossary/proposed/step3-objects.yaml` | `bill_redemption_status__overdue` | `bill_redemption_status__overdue` | 已新建转正，源条目已删 |
| 62 | `backend/glossary/proposed/step3-objects.yaml` | `receivable_disposition_category` | `receivable_disposition_category` | 已新建转正，源条目已删 |
| 63 | `backend/glossary/proposed/step3-objects.yaml` | `litigation` | `litigation` | 已新建转正，源条目已删 |
| 64 | `backend/glossary/proposed/step3-objects.yaml` | `continuing_education_hours` | `continuing_education_hours` | 已新建转正，源条目已删 |
| 65 | `backend/glossary/proposed/step3-objects.yaml` | `drawing_review_opinion` | `drawing_review_opinion` | 已新建转正，源条目已删 |
| 66 | `backend/glossary/proposed/step3-objects.yaml` | `drawing_review_certificate` | `drawing_review_certificate` | 已新建转正，源条目已删 |

## 基线删除项与删除前核对

修改前实际 `term_rules.check` 问题 **109 条**，与原基线逐键一致，judge 无失败：metric 75、used_by 18、unique_zh_error_code 10、event_name 5、command_name 1。先在新词条与清理后的提议集合上重算，证实问题差集恰为以下六键、没有新问题，再删除基线；修改后问题 **103 条**，剩余分布 75／18／10 不变。

| 删除键 | 删除理由 |
| --- | --- |
| `terms.yaml#contract_modification:event_name` | 首选事件英文已改为 ContractModified，通过现有过去式检查，event_name 实际问题消失。 |
| `terms.yaml#ecl_alert:event_name` | 首选事件英文已改为 EclAlerted，通过现有过去式检查，event_name 实际问题消失。 |
| `terms.yaml#forced_logout:command_name` | 祈使命令 ForceLogout 已取代 ForcedLogout，command_name 实际问题消失。 |
| `terms.yaml#retention_money_release_due:event_name` | 首选事件英文已改为 RetentionMoneyReleaseDueReached，通过现有过去式检查，event_name 实际问题消失。 |
| `terms.yaml#revenue_reduction:event_name` | 首选事件英文已改为 RevenueReduced，通过现有过去式检查，event_name 实际问题消失。 |
| `terms.yaml#rule_execution:event_name` | 首选事件英文已改为 RuleExecuted，通过现有过去式检查，event_name 实际问题消失。 |

## 未登记的 4 项及原因

- **现金保证金**：NM2 已并入保证金与押金；receivable 的说明保留现金保证金单独列示，不再建同义概念。 正式落点 `bond_and_deposit`。
- **经营产值**：业务上与收入分开，但用语问题尚未裁定，按工作卡明确要求不登记；不存在已核实的正式替代词条。
- **财务负责人**：后续 NM5 已明确为各法人的财务职责并登记法人财务负责人，旧泛称不另立词条。 正式落点 `legal_entity_finance_head`。
- **工时单审批已撤销**：GL B2-B07 对同一 TimesheetCancelled 作了专项改名裁定；B2-A02 的概括同意由专项裁定取代，不登记第二个同义事件。 正式落点 `timesheet_approval_withdrawn`。

## 已验证

- `build_terms.py` 连续两次退出 0，`cmp` 逐字节一致；SHA-256：`9a145ee292239009883b02fa6b70ba25243bca040e2ee370e25ed15c7cecfb62`。术语规则校验通过，103 条存量、基线外新问题 0。
- `apply_recon.py --self-test` 退出 0：72 个负向用例；覆盖缺必填／空值、id/snake 冲突、非答复字段与无效 fields、通用词误禁用、指标／BC 缺口、裁定哈希漂移、确认来源漂移、对照不完整和提议源未删；正向覆盖精确转正、复用目标、定向更新、确认与说明追加、更新幂等。
- `apply_nm.py --self-test` 退出 0：原有 59 个负向用例及正向断言保留执行，原脚本未改。`build_proposed_index.py --self-test` 退出 0。
- 结构化对比：56 条新增恰好对应登记结果；既有 729 条中仅 7 条修改，所有非答复字段与原 confirmed 保留；meta、common_words 不变。26 个提议 YAML 的剩余条目、其他顶层字段及文件头逐项一致，B/C/错误码没有变化。
- 后端以仓库无容器路径执行 `JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home MAVEN_ARGS=-o ./mvnw -o test-compile surefire:test -Dtest=GlossaryTest`：实际 5 项，**4 通过、1 失败**，0 error／skip。
- precheck 后端专项：GlossaryTest 5（1 fail）、ErrorCodeGlossaryTest 2（全通过）、NamingSurfaceTest 11（全通过）、DocumentTypeCodeTest 3（全通过）；合计 21 项、20 通过、1 失败，0 error／skip。
- `git diff --check` 通过；gitleaks 8.30.1 全历史 2926 个提交扫描通过。全历史扫描不涵盖未提交文件。没有修改 Java、验收测试、断言、代码基线、依赖或锁文件。

### 后端失败的确切边界

`backend/src/main/java/com/forcitis/one/platform/identity/events/RoleDeleted.java` 仍声明 `RoleDeleted`。现有 G41 按 bc + 首选 snake 匹配，不读取 old-code 别名。用户已经要求改名 RoleAbolished，故正式词条 `role_deleted` 的 en/snake 为 RoleAbolished/role_abolished；删除旧提议后，现有代码与新首选名不一致。这是实际执行失败，不能靠 id 永久不变或旧英文别名声称已通过。

所需后续动作是负责 identity 的改名卡将现有事件及引用迁到已裁定英文；本卡不得改 Java，故没有实施、没有加回重复提议、没有新增基线，也没有扩展 Java 读取机制。

## 未运行与前端执行问题

- 未运行字面 `./mvnw -o test -Dtest=GlossaryTest`：pom.xml 在 process-test-classes 绑定 OpenApiExportTest，会起 PostgreSQL 容器并重写卡外 OpenAPI。已用上面的 test-compile + surefire:test 实际执行同一 GlossaryTest。
- 前端 check:copy 与 test:scripts **未完成运行**。`frontend/node_modules` 不存在；显式 `pnpm install --frozen-lockfile --offline` 退出 1，报 `ERR_PNPM_NO_OFFLINE_TARBALL`，本轮实际首个缺包是 **jsonfile@6.2.1**（不是据上一轮 dingtalk-jsapi 日志推断本轮结果）。没有改依赖或锁文件。
- **执行失误如实记录**：最初在缺 node_modules 时运行 `pnpm run check:copy`，虽设置 COREPACK_ENABLE_NETWORK=0 与 npm_config_offline=true，pnpm 仍自动进入安装并尝试访问 registry.npmjs.org；日志显示请求因 DNS 失败并重试，已用中断结束，退出 130。没有成功下载的证据，不能将“未成功连接”写成“从未尝试联网”。随后只执行显式 --offline；本轮自动创建的 worktree 根 `.pnpm-store` 已清除。未再运行会触发自动安装的前端命令，test:scripts 未执行。
- 未运行容器、数据库／外网集成验收、业务端到端验收、完整前端 verify 或卡外 build_all 生成。本卡没有收到指定的独立业务验收测试；只报告上述实际检查。

## precheck 原始输出

命令（默认 origin/main 基线，没有用 HEAD 缩小范围）：

```sh
PATH=/Users/yeliang/.pyenv/versions/3.12.13/bin:/Users/yeliang/.nvm/versions/node/v24.18.0/bin:/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin \
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
MAVEN_ARGS=-o COREPACK_ENABLE_NETWORK=0 npm_config_offline=true CI=true scripts/precheck.sh
```

未改门禁、未跳过失败项。每个 SKIP 的理由保留在原文中；其中前端缺依赖在 glossary 专项按 FAIL 记账，不能靠下面的 frontend verify SKIP 把它当作通过。

```text
precheck: base origin/main (3ae3d466), HEAD 47ecece9, 82 changed files (23 uncommitted)

SKIP  generated docs (build_all --check)     docs or backend/glossary/proposed has uncommitted changes: commit first, then run again (请先提交再运行)
PASS  legacy system name                     check_legacy_name: 无违规
PASS  decision pages: question rules (self-test) check_pages self-test: ok
PASS  decision pages: question rules         check_pages: 5 张页，0 处问题
SKIP  migrations immutable                   no migration, marker or Flyway setting changed
SKIP  system actors append-only              system-actors.txt and its script did not change
SKIP  acceptance guard                       no acceptance test changed
SKIP  database scripts aligned               no database script or migration changed
PASS  docs read by builds are registered     ok
SKIP  assertion drift (warning only)         no test file changed
FAIL  glossary: frontend check:copy, test:scripts 前端依赖未安装：先在 frontend/ 运行 pnpm install --frozen-lockfile --offline（只用本机缓存、不下载）；缓存缺包而失败时，再问用户是否联网安装
FAIL  glossary: backend term tests           exit 1 — last lines:
      | java.lang.AssertionError: 
      | G40-G42 aggregate / domain event classes vs glossary (terms.yaml, proposed/*.yaml):
      |   new problem G41 platform.identity RoleDeleted no domain_event term: no domain_event term with snake 'role_deleted' in bc platform.identity: add a proposed term (the class name is the English name)
      | 	at com.forcitis.one.GlossaryTest.aggregatesAndEventsAreGlossaryTerms(GlossaryTest.java:213)
      | 
      | [ERROR] Failures: 
      | [ERROR]   GlossaryTest.aggregatesAndEventsAreGlossaryTerms:213 G40-G42 aggregate / domain event classes vs glossary (terms.yaml, proposed/*.yaml):
      |   new problem G41 platform.identity RoleDeleted no domain_event term: no domain_event term with snake 'role_deleted' in bc platform.identity: add a proposed term (the class name is the English name)
      | [ERROR] Tests run: 21, Failures: 1, Errors: 0, Skipped: 0
      | [ERROR] Failed to execute goal org.apache.maven.plugins:maven-surefire-plugin:3.5.6:test (default-cli) on project forcitis-one: There are test failures.
      | [ERROR] 
      | [ERROR] See /Users/yeliang/Projects/FORCITIS/forcitis-one-psa/.claude/worktrees/codex-n11/backend/target/surefire-reports for the individual test results.
      | [ERROR] See dump files (if any exist) [date].dump, [date]-jvmRun[N].dump and [date].dumpstream.
      | [ERROR] -> [Help 1]
      | [ERROR] 
      | [ERROR] To see the full stack trace of the errors, re-run Maven with the -e switch.
      | [ERROR] Re-run Maven using the -X switch to enable full debug logging.
      | [ERROR] 
      | [ERROR] For more information about the errors and possible solutions, please read the following articles:
      | [ERROR] [Help 1] http://cwiki.apache.org/confluence/display/MAVEN/MojoFailureException
SKIP  guards: backend guard tests            no guard test or architecture list changed
PASS  release scripts self-test              deploy_gate.py self-test passed
PASS  shellcheck scripts/release             ok
PASS  gitleaks (full history)                2926 commits scanned; no leaks
      note: uncommitted files are not in history; the pre-commit hook scans them on commit
SKIP  backend tests                          not run by default; no module code changed (shared inputs, resources, build or architecture lists)
      run: (cd backend && ./mvnw -q test-compile surefire:test -Dtest='ArchitectureRulesTest,ModularityTest,ModuleDependencyTest,NamingSurfaceTest,GlossaryTest,DdlNamingTest,LegacyShapeTest,TimeSourceGuardTest,TestIsolationGuardTest' -Dsurefire.failIfNoSpecifiedTests=false)
      before delivery run everything: scripts/ci/backend_verify.sh (Testcontainers: ask the user before starting containers)
SKIP  frontend verify                        前端依赖未安装：先在 frontend/ 运行 pnpm install --frozen-lockfile --offline（只用本机缓存、不下载）；缓存缺包而失败时，再问用户是否联网安装（本次未改 frontend/，CI 仍会跑）
SKIP  SQL Server integration tests           relevant_change.py: nothing the *MssqlIT tests exercise changed

SUMMARY: 7 PASS, 2 FAIL, 10 SKIP (base origin/main) — not ready
```

## 交付与续接

本轮交付限 decisions-recon.yaml、apply_recon.py、build_terms.py 两行接入、重生成 terms.yaml、本报告、四个精确清理的 proposed YAML、脚本生成 README、仅收缩的 term-rules-baseline.json。所有对照与删除源快照保存在仓库内批准文件。

本机验证留档：`/tmp/nam-1017-glo-06-r1/`（开工词表、基线与提议快照，删除前后实际问题集合，两次构建日志及逐字节比对副本，backend-glossary.log，frontend-copy.log，frontend-install-offline.log，precheck.log，verification.json）。临时目录只供本机复核；关键数量、逐项落点、失败信息、删除理由与完整 precheck 已写入本报告。

交负责会话的未完成项：① identity 事件代码仍为 RoleDeleted，按已有用户裁定完成代码改名后重跑术语测试；② 在已有授权环境补齐前端依赖后重跑 check:copy／test:scripts；③ 42 条 deprecated 兼容旧名仍由原改名卡处理。未请求新的 Java 机制扩展，未擅自增加任何基线。


## NAM-1017-GLO-14：NM6 四条命令登记（2026-10-17）

状态：**本地登记已写入，验收未完成**。Codex 实现；本轮依据负责会话最新裁定继续登记四条，未修改 N4 动词表。开工 HEAD 与本地 origin/main 均为 `a70d8c5b421084b69025d5c3b84d0ef0c23405a1`，按禁网要求未 fetch。本轮仅改 `decisions-recon.yaml`、由 `build_terms.py` 生成的 `terms.yaml`，以及追加本小节；没有提交、推送、改动提议目录或验收测试。

### 四条登记结果

共同字段：`kind: command`、`scope: domain`、`status: approved`、`confidence: medium`。所有旧名均为 `type: standard-zh`、`deprecated: true` 的别名，带 bc 与定稿出处；`forbidden: []`。NM6 认可中文完整名称；前三条英文按 N4 受控动词加宾语组成，第四条按本卡负责会话裁定使用 Calculate。

- **开始执行项目**：`id: start_project_execution`；`en: StartProjectExecution`；`snake: start_project_execution`；`bc: delivery.execution`；别名「开始执行」。定义：使项目从已立项进入执行中的命令。定稿为 `N7_RENAME_FINAL.json` 的 `NM6-07 / row_no=2`，聚合为 AGG-19，动词为 N4 V046 Start。
- **标记检查计划不再受托**：`id: mark_inspection_plan_as_no_longer_entrusted`；`en: MarkInspectionPlanAsNoLongerEntrusted`；`snake: mark_inspection_plan_as_no_longer_entrusted`；`bc: delivery.inspection`；别名「标记不再受托」。定义：将检查计划标记为不再受托状态的命令。定稿为 `NM6-07 / row_no=3`，聚合为 AGG-34，状态为「不再受托」，动词为 N4 V004 Mark。
- **登记商机赢单**：`id: register_opportunity_win`；`en: RegisterOpportunityWin`；`snake: register_opportunity_win`；`bc: presales.opportunity`；别名「商机赢单」。定义：登记商机已赢单事实的命令。定稿为 `NM6-07 / row_no=1`，聚合为 AGG-10，动词为 N4 V001 Register。
- **计算月度薪酬**：`id: calculate_monthly_payroll`；`en: CalculateMonthlyPayroll`；`snake: calculate_monthly_payroll`；`bc: organization.payroll`；别名「计算」。定义：计算某法人某月人员薪酬并形成月度薪酬结算结果的命令。定稿为 `NM6-08 / row_no=2`，聚合为 AGG-73。

### 出处与来源校验

`decisions-recon.yaml` 精确追加 S098（NM6-07）、S099（NM6-08）及 S100（intent）。原有 97 个来源、所有既有操作与 A/B 提议对照不变。现有 JSON 来源类型直接支持「以上叫法全部认可」，无须扩展 `apply_recon.py`。

- 答复：`docs/decisions/decision-pages/answers/nm6.json` 中 `NM6~NM6-07`、`NM6~NM6-08`，均为 choice 0、「以上叫法全部认可」，提交 **NM6-20261017-171527-lg9**，提交时间 `2026-10-17T15:15:27.566Z`。
- 原话：`docs/decisions/2026-10-17-171704-one-glossary-NM6-决策页提交-NM6-20.md` 的「原话」段。每条 `confirmed` 同时含 NM6 提交编号与该 intent 路径；来源记录带整文件 SHA-256，intent 另带原话段 SHA-256。
- 题面与定稿：`docs/decisions/decision-pages/sheets/nm6.json`；`docs/glossary/arch-naming/n7-rename-final.json` 的上述精确 question / row_no。二者未改。
- 骨架：`docs/architecture/proposals/enterprise-aggregates-draft.md` 的 AGG-19、AGG-34、AGG-10、AGG-73；移交依据为 `.codex-inputs/revenue-NOTES.md:1326` 与 `.codex-inputs/people-NOTES.md:439`。所有引用文件在当前 worktree 中实际存在。
- 动词：`docs/glossary/arch-naming/n4-verbs-states.md` 的 V046、V004、V001；这些标号只作表内定位，未创建新动词条目。

**动词表增补项：计算**。本卡负责会话明确授权本名称使用 `Calculate / Calculated / calculate`；N4 当前尚无该动词，本卡没有把尚未存在的 V158 写成已存在的出处。V158 由负责会话另行增补，随下一页向用户说明。本轮不把此裁定扩大到其他名称。

### 已验证

- `build_terms.py` 连续两次退出 0，`cmp` 逐字节一致。SHA-256：`5b1fae7552cf932de43d2b91319a10433cdce2c38ae2f8f5839f1d0c58359377`。词表 **833 → 837**；原有 833 条逐字段相同，meta、common_words 相同，只增加本卡四条。术语规则存量问题为 102 条，基线外新问题 0。
- `apply_recon.py --verify` 退出 0，原文 evidence、日期、批次、提交、intent 段落哈希及整文件审计哈希全部通过。
- 本卡核验器 `/tmp/nam-1017-glo-14/verify-card.py` 退出 0，35 项负向检查均按预期失败：四条整组空输入、逐条缺失、逐条空来源、每条 zh/en/snake/bc/confirmed 空字段、四条各造一个不同 id/en/snake 但同 bc 同中文名的临时词条，以及 confirmed 出处篡改与来源文件不存在。
- 上述四条数量完整性是本卡外部核验器的断言；现有生成器没有本卡专用「必须恰好四条」守护，不能把本次检查写成已新增永久门禁。空字段、空来源、重名与不存在来源均调用原有校验函数验证。
- 故意破坏在 `/tmp/nam-1017-glo-14/mutation-repo/` 数据副本执行：先通过干净对照；将 `calculate_monthly_payroll.confirmed` 的 intent 出处改成不存在路径，报「确认来源不一致」；再把 S100 的 file 也改成该路径，报「裁定来源不存在或越界」；恢复后再次通过。未修改真实来源或脚本。
- 后端命令 `cd backend && ./mvnw -o test-compile surefire:test -Dtest=GlossaryTest`（`MAVEN_ARGS=-o`、本机 Java 25）退出 0：**5 tests，0 failures，0 errors，0 skipped**。precheck 中 GlossaryTest 5、ErrorCodeGlossaryTest 2、NamingSurfaceTest 11、DocumentTypeCodeTest 3，共 21 项全通过，无容器。
- `scripts/precheck.sh` 已实际执行，退出 **1**，汇总为 **8 PASS、1 FAIL、10 SKIP**。`build_all --check` 通过，所有 SKIP 原因见下方原始输出。gitleaks 8.30.1 全历史 2980 个提交扫描通过；该扫描不覆盖未提交文件。
- `git diff --check` 通过。precheck 前后受控文件逐一哈希对照无额外变化。

### 已运行但失败

1. `apply_recon.py --self-test` 退出 **1**：第一段 72 个负向用例及正向检查通过；复审自测在 `apply_recon_selftest.py:241` 的 `assert len(user["sources"]) == 97` 失败，本卡追加来源后为 **100**。这是实际未通过的自测，不以第一段通过替代全命令结果。修正该硬编码断言需要改卡外自测文件，按工作卡边界停止于报告；没有放宽或删除断言，没有修改 apply_recon 行为。
2. `cd frontend && pnpm install --frozen-lockfile --offline` 退出 **1**，`ERR_PNPM_NO_OFFLINE_TARBALL`：本次实际首个缺包为 **aria-query@5.3.0**，本地 store 无 tarball。使用 `COREPACK_ENABLE_NETWORK=0`、`npm_config_offline=true`，未补装或下载。
3. 随后以 `pnpm --offline --config.verify-deps-before-run=false run check:copy` 执行，退出 **1**：`ERR_MODULE_NOT_FOUND`，缺 `typescript`。显式关闭运行前依赖自动安装，未改锁文件或依赖声明。本次离线安装生成的临时 `.pnpm-store` 已清理。
4. precheck 唯一 FAIL 为前端术语检查所需依赖未安装；并非后端术语失败。未达到工作卡要求的 0 FAIL，故本卡不报完成。

### 未运行与待负责会话处理

- 因离线依赖不足，前端 `test:scripts`、完整 `pnpm verify` 未运行；`check:copy` 仅执行到缺模块错误，没有完成文案扫描。
- 未运行容器、数据库集成测试、业务端到端验收；本卡没有指定独立业务验收测试。后端全量测试未运行，precheck 原文说明未改模块代码；已运行上述无容器术语专项。
- 负责会话需处理自测来源数硬编码、在已有授权环境备齐离线依赖缓存，并补 N4 V158 后重跑。无需重新裁定 NM6 中文名称。
- 输入 NM6 答复、题面、intent、N7 定稿表和 `.codex-inputs/` 开工时即为未跟踪移交文件，本卡未改其内容；后续交付须由负责会话保留这些依据，避免只拿本卡三个文件而丢失引用源。

### 本轮 precheck.sh 原始输出

命令（默认 origin/main，追加本报告小节之前执行，未缩小基线）：

```sh
PATH=/Users/yeliang/.pyenv/versions/3.12.13/bin:/Users/yeliang/.nvm/versions/node/v24.18.0/bin:/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin \
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
MAVEN_ARGS=-o COREPACK_ENABLE_NETWORK=0 npm_config_offline=true npm_config_verify_deps_before_run=false CI=true scripts/precheck.sh
```

```text
precheck: base origin/main (a70d8c5b), HEAD a70d8c5b, 15 changed files (15 uncommitted)

PASS  generated docs (build_all --check)     review_revisions: 0 个问题
PASS  legacy system name                     check_legacy_name: 无违规
PASS  decision pages: question rules (self-test) check_pages self-test: ok
PASS  decision pages: question rules         check_pages: 2 张页，0 处问题
SKIP  migrations immutable                   no migration, marker or Flyway setting changed
SKIP  system actors append-only              system-actors.txt and its script did not change
SKIP  acceptance guard                       no acceptance test changed
SKIP  database scripts aligned               no database script or migration changed
SKIP  docs read by builds are registered     no backend, frontend or script changed
SKIP  assertion drift (warning only)         no test file changed
FAIL  glossary: frontend check:copy, test:scripts 前端依赖未安装：先在 frontend/ 运行 pnpm install --frozen-lockfile --offline（只用本机缓存、不下载）；缓存缺包而失败时，再问用户是否联网安装
PASS  glossary: backend term tests             size (term round3_pending__size) -> com.forcitis.one.platform.integration.api.ArchivedOb
SKIP  guards: backend guard tests            no guard test or architecture list changed
PASS  release scripts self-test              deploy_gate.py self-test passed
PASS  shellcheck scripts/release             ok
PASS  gitleaks (full history)                2980 commits scanned; no leaks
      note: uncommitted files are not in history; the pre-commit hook scans them on commit
SKIP  backend tests                          not run by default; no module code changed (shared inputs, resources, build or architecture lists)
      run: (cd backend && ./mvnw -q test-compile surefire:test -Dtest='ArchitectureRulesTest,ModularityTest,ModuleDependencyTest,NamingSurfaceTest,GlossaryTest,DdlNamingTest,LegacyShapeTest,TimeSourceGuardTest,TestIsolationGuardTest' -Dsurefire.failIfNoSpecifiedTests=false)
      before delivery run everything: scripts/ci/backend_verify.sh (Testcontainers: ask the user before starting containers)
SKIP  frontend verify                        前端依赖未安装：先在 frontend/ 运行 pnpm install --frozen-lockfile --offline（只用本机缓存、不下载）；缓存缺包而失败时，再问用户是否联网安装（本次未改 frontend/，CI 仍会跑）
SKIP  SQL Server integration tests           relevant_change.py: nothing the *MssqlIT tests exercise changed

SUMMARY: 8 PASS, 1 FAIL, 10 SKIP (base origin/main) — not ready
```

本轮构建、后端、前端、自测、破坏验证及 precheck 原始日志位于 `/tmp/nam-1017-glo-14/`；关键结果与完整 precheck 已附于本节。

## NAM-1017-GLO-14 收口更正（负责会话，2026-10-17）
本节取代上文 GLO-14 小节里“自测退出 1”“precheck 8 PASS、1 FAIL”“本卡不报完成”“未提交”等过程性表述（那些是 Codex 沙箱内的中间状态）：
- 自测：`apply_recon_selftest.py` 把“来源总数 == 97”改为“原 97 个来源的内容摘要不变（sha256 锁定）”，并在 `apply_recon.py` 新增来源键校验（S 编号须连续，且每个来源至少被引用一次），补孤立来源、跳号两个负向用例；全部自测通过（72 + 84）。
- precheck：在已安装前端依赖的工作区 11 PASS、0 FAIL。
- 引用：新增词条不再引用未入库的 `.codex-inputs/*`；“计算月度薪酬”引用动词表 V158（随本 PR 一并入库，N4_VERBS_STATES.md）。
- 别名“计算”（bare verb）只作 standard-zh / deprecated，不得升为 dialect 或进 forbidden（否则会被前端文案检查当全局子串）。
- opus 复审（#485）遗留的低风险项：closeout/stage_align_check.py 对 3 字以上别名做全局子串替换（不在 CI 门禁），“结果词不受控”（不再受托无状态词条）待补用语页。
