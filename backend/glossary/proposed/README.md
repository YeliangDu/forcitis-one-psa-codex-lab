# 提议术语清单（第三轮用语确认用）

<!-- 本文件由 backend/glossary/build_proposed_index.py 从同目录 *.yaml 生成，不要手改；改 yaml 后运行 python3 docs/modernization/build_all.py 并提交结果。 -->

`terms` 只放业务术语，纯技术词（动词、框架词、值对象方法名等）放同文件的 `common_words`。每条必填使用范围 `scope`（domain 领域 / kernel 共享内核 / acl 防腐层）与 `bc`（取 `docs/modernization/glossary/bcs.yaml` 中的 id）：kernel 另填 `used_by`（使用它的 bc），acl 另填 `acl_class`（过渡期 / 长期），指标另填 `formula`、`grain`、`as_of`，下游 bc 自用词可填 `upstream_term`；同一 bc 内中英文名唯一，跨 bc 同名在 `homonyms` 互列；`kind` 只取 DDD 分类（aggregate 聚合 / entity 实体 / value_object 值对象 / domain_event 领域事件 / command 命令 / business_rule 业务规则 / read_model 读模型 / metric 指标 / role 角色 / state 状态，错误码另列 error_code），事件名为聚合名加过去式业务动词、命令用祈使动词，Create / Update / Delete / Data / Info / Manager 等技术泛词不作领域名（规则与校验见 `glossary/term_rules.py`）。本表按文件列出除错误码以外的全部提议术语；「是否疑似方言」由条目中类型为方言或待定的别名与禁用写法生成，空表示未登记问题；「备注」为条目 note 原文。

修改任何一个 yaml 后运行 `./mvnw -B verify`（GlossaryTest 会校验字段、重名和未登记词）与 `python3 docs/modernization/build_all.py`（重新生成本文件）。

## organization.workforce（`architecture-acl.yaml`）

ADR-0039（防腐层边界）引入的术语（proposed，待业务评审，ADR-0018）。登记会话：one-architecture，2026-10-15。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `apply_org_change_set` | 应用组织变更 | ApplyOrgChangeSet | 领域 · organization.workforce | 组织人事 api 中唯一的同步入口：应用一份组织变更集，管理员离职挂为待放行离职，其余变更照常生效；同一变更集重复调用返回上次结果 |  |  |
| `separation_hold` | 待放行离职 | SeparationHold | 领域 · organization.workforce | 会使可用超级管理员少于 2 名、或涉及超级管理员与安全审计员持有人的离职，被单独挂起等待另一名持有人放行的记录（ADR-0027 第 1 项第 7 条、ADR-0028） |  | 权限码 organization.workforce:separation-hold:approve 已用此词（ADR-0028 第 2 条） |

## `audit-partition.yaml`

审计条目按月分区（ADR-0048、DATA-REPAIR-timesheet-audit TB-07）用到的技术词：分区只是存储与删除的单位，不是业务术语，只登记为技术词（proposed，ADR-0018）。

登记会话：one-1a-next-5 数据修复 PR-1。使用范围：platform.audit 内部的作业、配置键、指标名与锁名。

无错误码以外的术语。

另有技术词 2 个（`common_words`），不在表中。

## `code-aggregates-events.yaml`

B2 登记：代码里的聚合根与领域事件各自要有词条（G40、G41）；

登记会话 one-glossary，2026-10-15，中文名待用户确认。

无错误码以外的术语。

## `contract-evolution.yaml`

契约演进与部署排空用语（proposed，ADR-0035，待用语确认，ADR-0018）。

无错误码以外的术语。

## organization.administration（`contract-original-loan.yaml`）

合同原件借阅用语（one-glossary 2026-10-21，NAM-1021-GLO-14；ADR-0076 提议，proposed）。bc 按 one-architecture 裁定：先行期 organization.administration，W3 移交 contract.agreement。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `contract_original` | 合同原件 | ContractOriginal | 领域 · organization.administration | 合同原件的归档记录：法人、合同编号、原件类型、存放位置、归档状态；先行期在行政事务，W3 移交合同模块的协议归档记录（ADR-0076） |  |  |
| `loan_record` | 借阅记录 | LoanRecord | 领域 · organization.administration | 一份合同原件的一次借出：借用人、借出日、应还日、实际归还日，审批实例必填；借出须原件已归档且无未归还借出（ADR-0076，INV-125） |  |  |
| `renewal_record` | 续借记录 | RenewalRecord | 领域 · organization.administration | 一笔借阅的续借，另起一条并引用原借阅记录，带新应还日与续借审批实例，每笔借阅最多续借一次（ADR-0076） |  |  |

## cost.timesheet（`cost-timesheet.yaml`）

cost.timesheet 提议术语（proposed，待第三轮用语确认，ADR-0018）。

无错误码以外的术语。

另有错误码 13 个（kind 为 `error_code`）、技术词 23 个（`common_words`），不在表中。

## platform.audit（`data-classification.yaml`）

数据分级的个人信息标记（ADR-0043）：个人信息 PI、敏感个人信息 SENSITIVE_PI；盘点 P-029 改名，one-glossary 2026-10-16 登记提议。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `personal_information` | 个人信息 | PersonalInformation | 领域 · platform.audit | 以电子或者其他方式记录的与已识别或者可识别的自然人有关的各种信息，不包括匿名化处理后的信息；数据分级的个人信息标记取值 PI |  |  |
| `sensitive_personal_information` | 敏感个人信息 | SensitivePersonalInformation | 领域 · platform.audit | 一旦泄露或者非法使用，容易导致自然人的人格尊严受到侵害或者人身、财产安全受到危害的个人信息；数据分级的个人信息标记取值 SENSITIVE_PI（原 SPI，与进度绩效指数 SPI 缩写同名，用户已选改名，P-029） |  | P-029：个人信息标记取值 SPI 改为 SENSITIVE_PI，PI 与 NONE 不变；缩写 SPI 只留给进度绩效指数 |

## `data-migration.yaml`

涉及模块：`accounting.period`、`operations.facts`、`platform.integration`、`platform.migration`。

迁入的其余用语（拒收、核对、历史期间、迁入标记等）随 ADR-0036（另行 PR）登记。

通用词另增 `batch`、`count`（批量命令后缀与计数端点，NAMING_STANDARD §2.6 A-3、A-6）。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `migration_period_reservation` | 迁入占用 | MigrationPeriodReservation | ACL（过渡期） · accounting.period | 迁入批次对 (期间, 法人) 的占用登记，由 accounting.period 自持；有未解除占用的期间不得关账，批次转为提交中时加锁确认期间状态（ADR-0036 第 6 条） |  |  |
| `migration_marker` | 迁入标记 | MigrationMarker | 领域 · operations.facts | 事实行上区分原生与迁入的标记；只在事实行，单据表不带；accounting.ledger 不为迁入标记的事实生成分录（ADR-0036 第 5、6 条） |  | 列名在实现 PR 中按 NAMING_STANDARD 定 |
| `migration_posting_channel` | 迁入过账通道 | MigrationPostingChannel | ACL（过渡期） · operations.facts | operations.facts 中只供迁入用例使用的过账入口：期间可为历史期间或开放期间，不发布事件（ADR-0036 第 6 条） |  |  |
| `trial_commit` | 试提交 | TrialCommit | ACL（过渡期） · platform.migration | 用与正式提交同一套代码把整批写入目标库后回滚事务，以跑全部领域校验与数据库约束（ADR-0036 第 7 条） |  |  |
| `migration_batch_status__effective` | 已生效 | Effective | 领域 · platform.integration | 迁入批次状态取值：提交后复核一致；可转为已冲销（ADR-0036 第 3 条） |  |  |

另有技术词 2 个（`common_words`），不在表中。

## `enterprise-aggregates.yaml`

涉及模块：`accounting.ledger`、`accounting.tax`、`contract.agreement`、`cost.costing`、`delivery.acceptance`、`delivery.execution`、`masterdata.counterparty`、`payables.payment`、`presales.opportunity`、`procurement.subcontracting`、`receivables.collection`、`receivables.wip`。

全企业聚合草案 §8 交 one-glossary 的新用语（预判登记稿，proposed，待业务评审，ADR-0018）。

前提：bcs.yaml 先并入 bcs-additions.yaml 中的 masterdata.*、organization.leave、accounting.tax 等；需要用户裁定的 11 项不在此文件，见 decide-items.json。

聚合所在 bc 暂取现有 bc（presales.quoting、contract.baseline 等），B2-X01 定合并后的 BC 标识后整体迁移。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `own_participation_record` | 我方参与记录 | OwnParticipationRecord | 领域 · masterdata.counterparty | 建设工程内记录我方在该工程中承担的角色及对应的我方项目（项目 ID 由交付登记）；一个工程可有多条，只作回溯线索，不触发校验。 |  |  |
| `tender_project` | 招标项目 | TenderProject | 领域 · masterdata.counterparty | 依《招标投标法》公开或邀请招标的项目登记（招标标识、采购方式、标段），外部引用登记，被投标、合同和建设工程共同引用。草案称「招标项目登记」，登记是动作，名词取「招标项目」。与我方项目（project）不是一回事。 |  | 若架构定为不建聚合（REF，无聚合），kind 改 entity |
| `project_wbs` | 项目 WBS | ProjectWbs | 领域 · delivery.execution | 一个项目一棵的工作分解结构树：节点（wbs_node）分阶段、子项、工作包三类，工作包为末级；节点挂接合同范围条目或已批准变更。wbs_node 是其内的实体，本词是聚合根。 |  | 词表已有 wbs_node（中文名「WBS」，实体）；本条为聚合根，两者是整体与部分，不是同义 |
| `billing_revenue_limit` | 开票与收入额度 | BillingRevenueLimit | 领域 · receivables.wip | 每个协议一个，守住两条累计上限：累计开票不超过付款条件项的可开票额度，累计确认收入不超过履约义务的分摊价格；内含额度行、开票占用和收入确认行。是额度计数的载体，收入确认本身的规则不在这里。 |  | 草案称「履约义务收入台账并入开票与收入额度」；台账是方言，不用 |
| `acceptance_evidence_type_admission` | 客户认可证据类型开放配置 | AcceptanceEvidenceTypeAdmission | 领域 · receivables.wip | 哪些客户认可证据类型已开放为可触发收入确认的版本化配置：每版本一个聚合，带生效日、批准人和依据文件，只追加；「历史迁入」永不开放。 |  | 开放（admit）取 ADR-0047 的说法；配置类聚合，每版本一个、已批准版本不可改 |
| `output_invoice` | 销项发票 | OutputInvoice | 领域 · receivables.wip | 我方向客户开具的增值税发票（蓝字、红字），含平台开具结果与红冲确认；与开票事件（invoice_issued，开票）的关系是：销项发票是对象，开票是它被开具这件事。 |  | 英文取 output tax 的通行写法；若偏好 SalesInvoice 可改，中文名不变 |
| `invoice_posting_reservation` | 发票入账占用 | InvoicePostingReservation | 领域 · accounting.tax | 进项发票金额被某笔入账（费用或应付）占用的记录；各笔占用合计不超过发票可入账金额。 |  |  |
| `receivables_collection__cash_application_reservation` | 核销占用 | CashApplicationReservation | 领域 · receivables.collection | 回款分配行对应收款项余额的占用记录：分配行引用它，核销时转为核销记录，各笔占用合计不超过应收余额。 |  | 与应付侧同名，见 homonyms |
| `payables_payment__cash_application_reservation` | 核销占用 | CashApplicationReservation | 领域 · payables.payment | 付款分配行对应付款项余额的占用记录：分配行引用它，核销时转为核销记录，各笔占用合计不超过应付余额。 |  | 与应收侧同名，见 homonyms |
| `supplier_agreement` | 供方协议 | SupplierAgreement | 领域 · procurement.subcontracting | 我方与供方（分包方、采购供应商、出租方、保险人、居间人、劳务派遣单位）签订的协议，含基线、付款条件、质保金条款以及资质与同意证据；与客户侧收入协议（agreement，协议）相对。「供方」取 GB/T 19000 用语。 |  | 词表 agreement_type__lease_contract 等五个供方侧协议类型现登记在 contract.baseline，随本条迁入采购子域，见 PREJUDGE |
| `supplier_agreement_performance_limit` | 供方协议履约额度 | SupplierAgreementPerformanceLimit | 领域 · procurement.subcontracting | 每个供方协议一个，守住「累计已确认履约金额不超过协议金额（含已生效分包变更）」；供方履约确认占用它。是额度计数的载体。 |  |  |
| `subcontract_change_activated` | 分包变更已生效 | SubcontractChangeActivated | 领域 · procurement.subcontracting | 分包变更审批通过并生效；同 BC 内订阅，用于更新供方协议履约额度。 |  |  |
| `labor_cost_allocation_result` | 人工成本分配结果 | LaborCostAllocationResult | 领域 · cost.costing | 一个会计期间内，把已过账的工时与薪酬事实分配到项目的结果（分配行）；每期间只能有一份有效结果。人工成本称分配，间接费用称分摊（allocation_result）。 |  | 草案称「人工成本分配运行」；与既有 cost_transfer_batch（批次）、allocation_result（结果）同族，故称「结果」，不称「运行单据」 |
| `cash_accrual_account_mapping` | 收付权责科目映射表 | CashAccrualAccountMapping | 领域 · accounting.ledger | 收付实现制科目与权责发生制科目的对应关系（映射行、对账类别）；每版本一个聚合，已生效版本不可改。与核算映射规则（accounting_mapping_rule_set，业务事件到凭证模板）不同：它对应的是科目，不是事件。 |  |  |
| `control_account_authorization` | 控制科目授权表 | ControlAccountAuthorization | 领域 · accounting.ledger | 控制科目允许由哪些事实类型经哪些通道入账的版本化授权（科目 × 事实类型 × 通道）；每版本一个聚合，已生效版本不可改。 |  |  |
| `no_objection_declared` | 无异议已声明 | NoObjectionDeclared | 领域 · delivery.acceptance | 项目经理作出无异议声明；开票与收入侧订阅，用于视为同意的判定。 |  |  |
| `design_change_claim_status` | 设计变更主张状态 | DesignChangeClaimStatus | 领域 · delivery.acceptance | 设计变更是否向对方主张：待主张，或不主张（附理由）；是否已主张由合同侧的变更、索赔记录，本 BC 不引用它们。草案称「主张标记」，标记是实现用语，业务上是状态。 |  |  |
| `acceptance_date` | 认可日 | AcceptanceDate | 领域 · delivery.acceptance | 客户认可成果的日期，按证据类型取不同的日（签字日、签收日、邮件进入我方邮箱之日、纪要发出日、期满日等），决定收入期间。；认可是客户方接受成果这一事实，由八类证据证明（含邮件、纪要、视为同意），区别于验收（验收单上的裁定行为：通过、退回、有条件通过）；「交付物已认可」是收入证据，「验收通过」是质量裁定（GL2 B2-X08） |  | 「认可」与词表「验收」的关系见确认单 B2-X08 |
| `review_period` | 审查期 | ReviewPeriod | 领域 · delivery.acceptance | 合同约定发包人审查设计文件的期限（天），期满无结论也无异议视为同意；不同意须书面说明，修改后重新报送、审查期重新起算。 |  |  |
| `expiry_record` | 期满记录 | ExpiryRecord | 领域 · delivery.acceptance | 审查期届满的记录（期满日、计算所依参数），是视为同意的须附证据之一；期满日的判定在开票与收入侧（用户已定 D-8）。 |  |  |
| `acceptance_evidence_type` | 客户认可证据类型 | AcceptanceEvidenceType | 领域 · delivery.acceptance | 客户认可成果的八类证据（书面认可文件、两类签收单、邮件确认、两类会议纪要或签认单、检查服务签认或报告签发、视为同意）加「历史迁入」；逐类开放，缺须附证据即拒绝。 |  | bc 随证据（OBJ-53）归属的 Q29 定；暂放 delivery.acceptance |
| `counterparty_bank_account_changed` | 往来单位银行账户已变更 | CounterpartyBankAccountChanged | 领域 · masterdata.counterparty | 往来单位的收款银行账户变更；应付与付款侧订阅，用于拦截向旧账户付款。草案称「银行账户已变更」，与我方资金账户（银行与现金）相混，故加「往来单位」。 |  |  |
| `bid_won` | 投标已中标 | BidWon | 领域 · presales.opportunity | 投标收到中标通知；同 BC 内转为商机赢单。 |  |  |
| `bid_lost` | 投标未中标 | BidLost | 领域 · presales.opportunity | 投标确认未中标；应收侧订阅，用于退还投标保证金。 |  |  |
| `opportunity_lost` | 商机已输单 | OpportunityLost | 领域 · presales.opportunity | 商机以未赢结束，记录原因、竞争对手、中标单位和中标价；成本核算侧订阅，用于售前投入转费用。与词表「赢单」相对；「丢单」是口语，列为别名。 |  |  |
| `opportunity_abandoned` | 商机已放弃 | OpportunityAbandoned | 领域 · presales.opportunity | 我方主动放弃商机（不参与或中途退出）；成本核算侧订阅，用于售前投入转费用。与输单不同：输单是对手赢，放弃是我方不做。 |  |  |
| `agreement_terminated` | 协议已终止 | AgreementTerminated | 领域 · contract.agreement | 协议提前终止；成本核算与开票收入侧订阅，用于终止清算和停止开票。与「到期」不同，到期不是终止。 |  |  |
| `agreement_baseline_activated` | 协议新基线已生效 | AgreementBaselineActivated | 领域 · contract.agreement | 协议的新基线版本生效（变更批准后）；开票收入侧订阅，用于更新付款条件项与履约义务。 |  |  |
| `agreement_project_linked` | 协议项目关联已登记 | AgreementProjectLinked | 领域 · contract.agreement | 协议与项目的关联（只带生效期）已登记；成本核算侧订阅，用于确定项目归属的协议。 |  |  |
| `project_established` | 项目已建立 | ProjectEstablished | 领域 · delivery.execution | 项目建立（立项）；成本核算侧订阅，用于售前投入转入。 |  |  |
| `project_closed` | 项目已结项 | ProjectClosed | 领域 · delivery.execution | 项目完成收尾检查后结项；成本核算侧订阅，用于结项清算。与竣工结算（final_account）不同：结算是与客户对账，结项是我方内部收尾。 |  |  |
| `project_terminated` | 项目已终止 | ProjectTerminated | 领域 · delivery.execution | 项目在完成前被终止（有成本发生）；成本核算侧订阅，用于终止清算。 |  |  |
| `project_cancelled` | 项目已取消 | ProjectCancelled | 领域 · delivery.execution | 项目在开工前被取消（无成本发生）；与终止的区别在是否已有成本。 |  |  |
| `acceptance_evidence` | 证据 | AcceptanceEvidence | 领域 · delivery.acceptance | 用于证明交付、认可等事实的证据内容；证据本身保存内容与采集人 ID，证据关联记录单独记录其挂在哪个交付物或阶段，归质量与认可 BC。 |  | Q29 按用户裁定保留宽口径“证据”及原定义；AcceptanceEvidence 只是候选英文，尚待确认，词条保持 proposed。 |

## `finance-closeout-terms.yaml`

涉及模块：`accounting.tax`、`cost.costing`。

财务线用语（one-glossary 2026-10-22，NAM-1021-GLO-22；one-arch-finance 来单，proposed）。待申报 / 已申报、跨法人、代发退回、补发明细已在先前批次登记。出处 finance/NOTES.md 新用语表与 BONUS_OVERTIME_COSTING.md §5。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `register_input_invoice_posting` | 登记进项发票入账 | RegisterInputInvoicePosting | 领域 · accounting.tax | 占用方过账后，登记进项发票在税务平台的入账状态；动词登记＝只记录事实；报送方待定（U-55） |  |  |
| `register_agreement_effective` | 登记协议已生效 | RegisterAgreementEffective | 领域 · cost.costing | 在协议已生效后登记售前投入的归属（AGG-122）；取代原「登记合同已签回」，订阅「协议已生效」 |  |  |
| `bonus_accrual` | 奖金计提 | BonusAccrual | 领域 · cost.costing | 按服务期间每月为每个项目计提的奖金，计入该项目的直接人工（实际变体），不等奖金核定或发放；计提额 ＝ 当月计提基数 × 奖金计提率 |  |  |
| `bonus_accrual_rate` | 奖金计提率 | BonusAccrualRate | 领域 · cost.costing | 挂在标准成本费率卡版本上的奖金计提比率；规则类型分合同额百分比与历史比率（默认历史比率），预算、预估、实际三个变体用同一版本 |  |  |
| `bonus_adjustment_line` | 奖金调整行 | BonusAdjustmentLine | 领域 · cost.costing | 奖金核定后，把「已核定额 − 该项目累计计提额」作为调整行计入当期人工成本；不回改已关账期间，单列供分析计提率准不准 |  |  |
| `overtime_multiplier` | 加班倍数 | OvertimeMultiplier | 领域 · cost.costing | 加班工时计成本的倍数：工作日 8 小时后 1.5 倍、休息日未补休 2 倍、法定节假日 3 倍（《劳动法》第四十四条）；休息日已补休按 1 倍 |  |  |
| `labor_cost_allocation_variance` | 人工成本分配差异 | LaborCostAllocationVariance | 领域 · cost.costing | 每月按员工把「按实际成本费率计价的合计 − 当月实际薪酬（含加班费）」记为差异，挂员工所在部门，计期间损益，不进项目；如 7.5 到 8 小时段按 1 倍计价超出日薪的部分（INV-89 改写后项目分配额加差异等于实际人工成本）；这个差异科目是设计（BONUS_OVERTIME_COSTING §2.4，one-architecture 2026-10-22 认可），不是用户原话 |  |  |

## `finance-decisions.yaml`

财务口径决策单（docs/modernization/r0/FINANCE_DECISIONS.md，2026-10-15）中出现、词表未收的会计与经营用语。

会计类提议术语须经财务口径确认（ADR-0018）；「产值收入」是方言，登记在正式用语「权责收入」下。

无错误码以外的术语。

## `fix-b.yaml`

涉及模块：`organization.workforce`、`platform.identity`、`platform.integration`、`platform.numbering`、`platform.web`。

Phase 1a 修复批 B 新增术语（proposed，待业务评审，ADR-0018）。

无错误码以外的术语。

另有错误码 47 个（kind 为 `error_code`）、技术词 49 个（`common_words`），不在表中。

## `fix-b1-identity.yaml`

涉及模块：`organization.workforce`、`platform.audit`、`platform.identity`、`platform.numbering`。

Phase 1a 修复批 B1（身份与授权）新增术语（proposed，待业务评审，ADR-0018）。ADR-0027 的八项与运维命令用到的词；每个新错误码一条 error__ 条目。

无错误码以外的术语。

另有错误码 27 个（kind 为 `error_code`）、技术词 18 个（`common_words`），不在表中。

## organization.attendance（`fix-b2-data.yaml`）

Phase 1a 修复批 B2（数据架构）新增术语（proposed，待业务评审，ADR-0018）。

无错误码以外的术语。

另有错误码 1 个（kind 为 `error_code`），不在表中。

## organization.workforce（`fix-b2b-events.yaml`）

Phase 1a 修复批 B2b（事件业务日期、指标）新增术语（proposed，待业务评审，ADR-0018）。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `snapshot_date` | 快照日 | SnapshotDate | ACL（过渡期） · organization.workforce | 读取组织源快照的那个业务日，即同步运行开始当天（Asia/Shanghai）。它是 OrgSnapshotChanged 信封的 businessDate，也是同一次同步写入的版本行的生效日 |  |  |

## `fix-c.yaml`

涉及模块：`organization.workforce`、`platform.integration`。

前端修复批新增的界面用语（proposed）：状态、列名与提示语中的业务词，守护要求它们作为完整的词登记，不得由单字拼成。

状态、列名与提示语中的业务词。前端用语守护要求单字只作连接词，状态词（已通过、待认领等）必须作为完整的词登记。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `ui_released_state` | 已放行 | ReleasedState | ACL（过渡期） · organization.workforce | 被中止的同步已由管理员放行并生效 |  | 展示用语；同步记录状态 RELEASED |
| `ui_external_system` | 外部系统 | ExternalSystem | ACL（长期） · platform.integration | 本系统之外、通过防腐层接入的系统，如企业微信、致远、组织来源 |  | 错误提示用语 |

## `fix-time-zone.yaml`

Phase 1a 时间与时区修复新增用语（proposed，待业务评审，ADR-0018）。纯技术词放 common_words。

无错误码以外的术语。

另有技术词 2 个（`common_words`），不在表中。

## `frontend-ui.yaml`

涉及模块：`organization.workforce`、`platform.integration`。

前端界面用语中尚不在正式词表里的业务术语（proposed，待第三轮用语确认，ADR-0018）。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `trigger_mode` | 触发方式 | TriggerMode | ACL（过渡期） · organization.workforce | 一次同步是定时触发还是手动触发 |  | 用户 2026-10-14 指定提议；同步记录列名 |
| `held` | 被中止 | Held | ACL（过渡期） · platform.integration | 同步记录的状态：被缩水保护中止，等待管理员放行；与「被中止的同步」指同一状态 |  | 用户 2026-10-14 指定提议；同步记录状态值，第三轮用语确认时与 workforce__held_run 合并 |
| `release` | 放行 | Release | ACL（过渡期） · platform.integration | 管理员核对同步差异后，让被中止的同步生效的动作 |  | 扫描前端界面文案发现；同步记录页按钮与结果用语 |
| `org_sync` | 同步 | OrgSync | ACL（过渡期） · platform.integration | 把组织来源的组织单元与员工变化应用到本系统的一次运行 |  | 扫描前端界面文案发现；与「同步记录」「同步差异」配合使用 |

## `gl5-direct.yaml`

涉及模块：`accounting.tax`、`organization.administration`。

GL5 页首「本页不请你选」各项的直接登记（one-glossary 2026-10-21，NAM-1021-GLO-14，proposed）。已被取代、草拟两个状态名是各对象状态链里的取值名，随各对象登记，决定记在 glossary/arch-naming/STATE_NAME_RULINGS.md。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `statutory_filing` | 法定申报 | StatutoryFiling | 领域 · accounting.tax | 法人对税务、社保等机关的周期申报与缴款（增值税及附加、企业所得税、个税扣缴、社保公积金、统计）；其中税务类的申报记录即已登记的「税务申报记录」 |  |  |
| `statutory_filing_status__pending_filing` | 待申报 | PendingFiling | 领域 · accounting.tax | 法定申报的状态：已生成申报，尚未向机关申报 |  |  |
| `statutory_filing_status__filed` | 已申报 | Filed | 领域 · accounting.tax | 法定申报的状态：已向机关申报，尚未缴款 |  |  |
| `statutory_filing_status__remitted` | 已缴款 | Remitted | 领域 · accounting.tax | 法定申报的状态：税费或社保公积金已缴纳 |  |  |
| `correct_filing` | 更正申报 | CorrectFiling | 领域 · accounting.tax | 对已申报的法定申报作更正，另起更正申报 |  |  |
| `asset_card_list` | 资产卡片清单 | AssetCardList | 领域 · organization.administration | 先行期财务提供的资产卡片清单，每行带资产卡片号、所属法人、资产名称，行政事务据此引用信息技术资产；资产卡片一词待 GL5-02 定 |  |  |
| `card_number_status__pending_check` | 待核 | PendingCheck | 领域 · organization.administration | 资产卡片号的状态：清单没到时手工录入的卡片号，等财务清单到后核对，核对前不当作已确认 |  |  |

## `kernel.yaml`

`tail_adjustment`（尾调）、`rounding_residual`（舍入差）、`prorate`（按权重拆分）由用户 2026-10-15 XD-07「同意 (Recommended)」确认登记（`docs/modernization/CROSS_CUTTING_DECISIONS.md`）。`reversal_document`、`correction_document`、`document_nature`、`reversal_category`、`reversible_balance`、`derived_reversal`、`original_period`、`change_order_document`、`reverse_action`、`correct_action` 十条是 ADR-0031 第 8 项的提议，待用语确认（U-4）。

无错误码以外的术语。

另有技术词 38 个（`common_words`），不在表中。

## `kpi-page-terms.yaml`

涉及模块：`accounting.cashbank`、`contract.agreement`、`cost.timesheet`。

KPI 页用到的七个未登记词（one-glossary 2026-10-22，NAM-1021-GLO-18；one-coordinator 来单，proposed）。出处：r0/METRIC_BASELINE_DRAFT M-37、M-43、M-46，R1-B-06。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `opportunity_hours` | 商机工时 | OpportunityHours | 领域 · cost.timesheet | 员工填报在商机（尚未签约的业务机会）上的工时；与收费工时、非计费工时并列，用于工时结构（M-43） |  |  |
| `available_hours` | 可用工时 | AvailableHours | 领域 · cost.timesheet | 员工在期间内可供使用的工时，来自工作日历扣除休假后的小时数；利用率的分母（可计费工时 / 可用工时） |  |  |
| `default_payment_term` | 默认账期 | DefaultPaymentTerm | 领域 · contract.agreement | 合同或客户未另行约定付款周期时采用的账期天数，配置项；到期日由账期与开票日推算（R1-B-06） |  |  |
| `excess_authorized_limit_input` | 超授权额度投入 | ExcessAuthorizedLimitInput | 领域 · contract.agreement | 无合同风险敞口超出授权额度的投入金额（M-46）；授权额度按客户等级配置 |  |  |
| `hours_structure` | 工时结构 | HoursStructure | 领域 · cost.timesheet | 期间内工时按收费、商机、非计费划分的占比（M-43），与有效 / 无效工时划分对齐 |  |  |
| `effective_hours_ratio` | 有效工时占比 | EffectiveHoursRatio | 领域 · cost.timesheet | 期间内有效工时占全部填报工时的比例；有效工时指经有效性认定的工时 |  |  |
| `cash_inflow_outflow_ratio` | 现金流入流出比 | CashInflowOutflowRatio | 领域 · accounting.cashbank | 期间现金流入合计除以现金流出合计（M-37）；是否含筹资活动待确认 |  |  |

## `list-paging.yaml`

K-3a 列表分页公共契约新增的纯技术词（proposed，NAMING_STANDARD A-3、A-4）。

无错误码以外的术语。

另有技术词 6 个（`common_words`），不在表中。

## organization.workforce（`nits1.yaml`）

NITS-1 项5 新增的标识符（proposed）。登记会话：one-1a-next-5，2026-10-17。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `employee_existence` | 员工存在判定 | EmployeeExistence | 领域 · organization.workforce | 员工存在 = 组织快照的员工表里有该 ID 的行；不要求有任职，也不要求在职（one-data 裁定 DR-TB01-FU1-1）；与需要任职的员工查询分开，只答有或无 |  | NITS-1 项5，运维命令的操作人与见证人检查用 |

## organization.administration（`organization-administration.yaml`）

organization.administration（行政事务，B-31）提议术语（proposed，ADR-0018；起草：用语差距清单，待 one-glossary 评审与 one-architecture 对照）。

来源：B31_CONSTRUCTION_DESIGN.md（§2.3 事件、§3 聚合与表、§4 审批、§5 定时、§6 权限、§10–§11）与 B31_RULES.yaml；只含词表与其他提议文件查不到的词，已登记的五个聚合不重复。

错误码中文名全局唯一；同 bc 内中英文名唯一；与其他 bc 同名的审批中、已驳回、已退回、已通过、已撤销，以及被他词条禁用或同名的参与人、处理人，本文件不以这些词登记（用户 GL4 答复已定：备用审批人、员工活动终态已办结），也不自行裁定，交 one-glossary 判定。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `seal_usage_document` | 用印文件 | SealUsageDocument | 领域 · organization.administration | 用印申请中逐份列明的拟用印文件：名称、份数、原件或复印件，以及不可变文件版本、内容哈希与报价版本追溯引用。 |  | 设计草案表 seal_application_documents 按词表应为 seal_usage_documents；备选英文 SealUsageRequestDocument。 |
| `seal_usage_registration` | 用印登记 | SealUsageRegistration | 领域 · organization.administration | 经办人对一次实际用印的只追加登记：用印时点、本次份数、经办人、所用印章台账条目及版本、拟发文件与实际盖章凭据的文件标识和内容哈希，以申请加登记请求键去重。 |  | 设计草案名为 seal_usage_records（b31.notes 沿原稿提议英文 SealUsageRecord）；备选 用印记录 / SealUsageRecord；对应命令「登记用印」，数据访问注册表登记为只追加（APPEND_ONLY）。 |
| `seal_usage_purpose` | 用印用途 | SealUsagePurpose | 领域 · organization.administration | 用印申请所属的用途分类（如报价用印、投标用印）；审批人表的行、加审条件与预盖空白页的资格都按用途确定，取值为配置数据而非代码枚举。 |  | 用途清单由行政定版，取值不进代码；若 one-architecture 认为应并入审批人表行，可删本条。 |
| `seal_usage_approver_table` | 用印审批人表 | SealUsageApproverTable | 领域 · organization.administration | 无来源单据的用印按用途确定第一审批人岗位与备用审批人岗位的版本化配置：作为配置集「用印审批人」的条目，随配置集版本只追加，带版本号、生效日、定版人与决定依据；上线前须定版，是上线出口条件。 |  | 设计原文「用印审批人表」；同类先例 control_account_authorization（控制科目授权表）。one-data 裁定（B31_PHYSICAL_MODEL_RULINGS §2）：配置不挂聚合，共用配置集根加版本头，各配置一张条目表；物理表 seal_approver_rule_items。 |
| `seal_usage_approver_line` | 用印审批人表行 | SealUsageApproverLine | 领域 · organization.administration | 用印审批人表中一个用途对应的一行：用途、第一审批人岗位、备用审批人岗位；备用审批人存岗位引用，不存个人。 |  |  |
| `seal_usage_special_approval` | 用印特批 | SealUsageSpecialApproval | 领域 · organization.administration | 用途未列入用印审批人表的用印不直接拒绝，改由行政负责人特批；特批有单号、可审计，并留审批实例号。 |  | 是「用印审批」审批人解析的一个分支，不是独立审批类型。 |
| `high_risk_purpose` | 高风险用途 | HighRiskPurpose | 领域 · organization.administration | 担保、外借与预盖章属高风险用途：审批人解析结果不含备用审批人，缺人即挂起并告警，不进备用改派。 |  | 同时约束用印与合同原件借阅两条线。 |
| `blank_page_pre_stamp` | 预盖空白页 | BlankPagePreStamp | 领域 · organization.administration | 仅投标用印可预先盖章的空白页或待填栏的登记：张数、用途、领用人、日期、编号；用完登记收回，未用当场记作废，不删除。 |  | 是否独立聚合待 one-architecture 定；对应设计「预盖空白页登记」。 |
| `pre_stamp_page_limit` | 预盖张数上限 | PreStampPageLimit | 领域 · organization.administration | 投标用印可预盖空白页的张数上限，为行政填写的数字并记录定版人；未设定时不允许预盖。 |  |  |
| `seal_usage_approval` | 用印审批 | SealUsageApproval | 领域 · organization.administration | 用印申请经通用审批办理时使用的审批单据类型，只适用于无来源单据的人工审批路径。 |  | 设计中的 doc_type「用印审批」；同类先例 timesheet_approval 登记在 platform.approval，本条按所有者放 organization.administration；英文枚举尚未在审批模块注册。 |
| `config_set` | 配置集 | ConfigSet | 领域 · organization.administration | 一类版本化配置的根：以配置集代码区分用印审批人、信息技术服务类别、岗位资产配置标准、员工活动类型、提醒规则，并记负责该配置的角色；各配置的内容行落在各自的条目表，不挂在任何聚合下。 |  | 物理表 config_sets（B31 裁定 §2）；通用配置版本结构，是否上移 kernel 由 one-architecture/one-data 另定。 |
| `config_set_version` | 配置集版本 | ConfigSetVersion | 领域 · organization.administration | 配置集的一个版本头：版本号、生效日区间、待确认或已确认状态、作者与确认人（四眼）、决定依据；按业务日期取已确认且生效区间包含该日的版本，找不到失败告警。 |  | 通用配置版本结构，是否上移 kernel 由 one-architecture/one-data 另定。物理表 config_set_versions。 |
| `reminder_rule_item` | 提醒规则项 | ReminderRuleItem | 领域 · organization.administration | 提醒规则配置集中的一条条目：提醒代码与提前天数（七日提醒、归还提醒等参数），类型化列、不用自由文本。 |  | 物理表 reminder_rule_items（B31 裁定 §2）。 |
| `asset_card_number` | 资产卡片号 | AssetCardNumber | 领域 · organization.administration | 公司资产卡片的业务编号，信息技术资产使用记录以它为业务键；先行期无固定资产模块，根由迁入或「登记资产卡片」命令建立；W5 固定资产落地后追加可空的公司资产标识并按卡片号回填。 |  | 列名 asset_card_number（one-data 更正）；是公司资产卡片号，不是老系统内部 ID；one-glossary 已判定资产卡片统称公司资产（M7），本词是卡片号这一业务编号，待确认。 |
| `seal_usage_transition_channel` | 用印过渡通道 | SealUsageTransitionChannel | 领域 · organization.administration | 既有审批系统已审批的用印单据在合同、报价、投标模块上线前，经行政核对批准页后直接办理的过渡规则，相关模块上线即废止。 |  | 业务上指老系统已审批单据；迁入对照走 platform.integration；领域内不出现老系统名称（CLAUDE.md 第 1 条）；用语待 one-glossary Q10 |
| `prior_approval_source` | 既有审批来源 | PriorApprovalSource | 领域 · organization.administration | 用印申请来源为既有审批系统已审批单据时保存的凭据：既有审批单号、批准页文件、批准份数，以及行政核对的核对人与核对时间；同一既有审批单号的累计用印份数在本系统内计数。 |  | 业务上指老系统已审批单据；迁入对照走 platform.integration；领域内不出现老系统名称（CLAUDE.md 第 1 条）；用语待 one-glossary Q10 |
| `prior_approval_number` | 既有审批单号 | PriorApprovalNumber | 领域 · organization.administration | 既有审批系统中已审批用印单据的单号，过渡通道据此累计用印份数；不是本系统的单据编号。 |  | 业务上指老系统已审批单据；迁入对照走 platform.integration；领域内不出现老系统名称（CLAUDE.md 第 1 条）；用语待 one-glossary Q10 |
| `transition_channel_review` | 过渡通道季度复核 | TransitionChannelReview | 领域 · organization.administration | 过渡通道存续期间行政负责人每季度对通道使用情况的复核记录，进入审计。 |  | 业务上指老系统已审批单据；迁入对照走 platform.integration；领域内不出现老系统名称（CLAUDE.md 第 1 条）；用语待 one-glossary Q10 |
| `transition_channel_spot_check` | 过渡通道月度抽检 | TransitionChannelSpotCheck | 领域 · organization.administration | 过渡通道存续期间每月抽取 5 单回既有审批系统核对批准页的抽检记录，结果留痕。 |  | 业务上指老系统已审批单据；迁入对照走 platform.integration；领域内不出现老系统名称（CLAUDE.md 第 1 条）；用语待 one-glossary Q10 |
| `primary_approver` | 第一审批人 | PrimaryApprover | 领域 · organization.administration | 审批人表中按用途或类别指定的首选审批岗位；不可用时才改派备用审批人。 |  | 设计原文「第一审批人」；备选英文 FirstApprover。 |
| `backup_approver` | 备用审批人 | BackupApprover | 领域 · organization.administration | 第一审批人不可用时改派的同职能第二岗位，存岗位引用而非个人；改派加事后知会，不并行双批，不兜到总经理，高风险用途不进备用。预先指定的接手人；将来若做运行时委派（临时转派给他人代办）是另一个机制，须另立词条，不用本词吸收。 | 「兜底」方言·已禁用；「替补审批人」方言·已禁用；「缺位委派」方言·已禁用 | 用户 GL4 答复（5 题全选推荐）定名；用户原话为「兜底」，已列方言。 |
| `it_service_ticket_handling` | 信息技术服务工单处理记录 | ItServiceTicketHandling | 领域 · organization.administration | 信息技术服务工单受理后每次处理的只追加记录：处理时点、处理人、处理说明、处理结论与受理序号。 |  | 设计草案表 it_request_handlings 按词表应为 it_service_ticket_handlings。 |
| `it_service_category_table` | 信息技术服务类别表 | ItServiceCategoryTable | 领域 · organization.administration | 信息技术服务工单类别的版本化配置：作为配置集「信息技术服务类别」的条目，随配置集版本只追加；类别可增删改并留版本与操作人，不写死在代码或迁移，上线前由信息技术负责人定版。 |  | 设计原文「IT 类别表」。one-data 裁定（B31_PHYSICAL_MODEL_RULINGS §2）：配置不挂聚合，共用配置集根加版本头，各配置一张条目表；物理表 it_service_category_items。 |
| `it_service_category` | 信息技术服务类别 | ItServiceCategory | 领域 · organization.administration | 类别表中的一行：类别名称、是否需审批及理由、审批人与备用审批人岗位、受理人与替补、提供方、维护方与采购交接。 |  | 设计列名 category_code；类别取值为配置数据，需求类 / 故障类的细分清单待确认。 |
| `it_service_priority` | 信息技术服务优先级 | ItServicePriority | 领域 · organization.administration | 提出人填写的信息技术服务工单紧急程度。 |  | 设计列 priority_code 出现，但文档未给取值，值域待 one-architecture 或行政补；word priority 在词表中尚无登记。 |
| `access_validity` | 权限有效期 | AccessValidity | 领域 · organization.administration | 开通新权限的信息技术服务工单须写明的有效期或复核日，到期自动收回或提醒。 |  | 设计未明确「收回」由谁执行、是否调用身份模块；有效期与复核日是一个字段还是两个也待定，见报告问题清单。 |
| `it_service_approval` | 信息技术服务审批 | ItServiceApproval | 领域 · organization.administration | 信息技术服务工单经通用审批办理时使用的审批单据类型，只适用于类别表要求人工审批的类别。 |  | 设计中的 doc_type「IT需求审批」；其中「IT需求」是 it_service_ticket 的已废弃旧称，故按词表改称。同类先例 timesheet_approval 登记在 platform.approval。 |
| `it_asset_usage_request` | 信息技术资产使用申请 | ItAssetUsageRequest | 领域 · organization.administration | 信息技术资产使用记录内的领用申请或调拨申请，以申请类型区分；领用只登记、不审批。 |  | 设计草案表 it_asset_use_requests 按词表应为 it_asset_usage_requests；备选 资产领用调拨申请。 |
| `it_asset_usage_period` | 信息技术资产使用期间 | ItAssetUsagePeriod | 领域 · organization.administration | 一件信息技术资产被一名使用人持有的起止区间，左闭右开，同一资产至多一个未结束期间；结束原因区分归还与调拨。 |  | 设计草案表 it_asset_use_periods 按词表应为 it_asset_usage_periods。 |
| `asset_handover` | 资产交接 | AssetHandover | 领域 · organization.administration | 调拨时调出使用人与调入使用人的交接确认、交接日期与证据。 |  |  |
| `return_acceptance` | 归还验收 | ReturnAcceptance | 领域 · organization.administration | 资产管理员对归还设备的验收：验收人、归还日期与归还时设备状态；管理员不得验收自己名下资产的归还。 |  |  |
| `asset_condition` | 设备实物状态 | AssetCondition | 领域 · organization.administration | 领用交付或归还时登记的设备实物状况。 |  | 设计原文「设备实物状态」；英文取 AssetCondition 以与 ItAsset 系列词一致，备选 DeviceCondition。 |
| `position_asset_standard` | 岗位资产配置标准 | PositionAssetStandard | 领域 · organization.administration | 按岗位核对可领用资产类别、规格与数量的版本化配置：作为配置集「岗位资产配置标准」的条目，随配置集版本只追加；规则缺失或领用超出标准时拒绝领用。 |  | 设计原文「岗位资产配置标准」，b31.notes 沿原稿提议 AssetStandardIssuance；岗位指 position（岗位），不是 job_position（职位）。one-data 裁定（B31_PHYSICAL_MODEL_RULINGS §2）：配置不挂聚合，共用配置集根加版本头，各配置一张条目表；物理表 position_asset_standard_items。 |
| `position_asset_standard_line` | 岗位资产配置标准行 | PositionAssetStandardLine | 领域 · organization.administration | 岗位资产配置标准中一个岗位对应的一行：资产类别、规格、数量与计量单位。 |  |  |
| `over_entitlement` | 超配 | OverEntitlement | 领域 · organization.administration | 领用超出岗位资产配置标准；允许条件确认前一律拒绝领用，不新增领用审批，也不转人审。 |  | 备选英文 OverAllocation，但 Allocation 在词表中已指成本分摊。 |
| `return_reminder` | 离职归还提醒 | ReturnReminder | 领域 · organization.administration | 员工离职生效满七个日历日仍有在用资产未归还时，对原组织单元负责人发出的非中断提醒；起算点固定，晚到与重试不重置，已归还不重建，不阻塞离职。 |  | 七个日历日已定；幂等键为聚合标识加固定到期时点。 |
| `fault_description` | 故障描述 | FaultDescription | 领域 · organization.administration | 报修人对资产故障的文字描述。 |  |  |
| `repair_result` | 维修结果 | RepairResult | 领域 · organization.administration | 登记维修结果时填写的结论：已修复或无法修复；是工单属性，不另设状态，无法修复不自动报废也不自动归还。 |  |  |
| `employee_activity_participant` | 员工活动参与人 | EmployeeActivityParticipant | 领域 · organization.administration | 员工活动申请登记时列明的参与员工，登记后不可改；同一申请内参与人唯一。 |  | 词表中 task_participant（任务参与人，platform.approval）禁用了「参与人」，故本条全称登记，界面不得单写「参与人」。 |
| `employee_activity_type` | 员工活动类型 | EmployeeActivityType | 领域 · organization.administration | 员工活动的类型清单（周年贺卡、节日活动、创新项目发布），独立版本化维护、只追加；新增类型须先确认规则。 |  | 设计把它列为 AGG-116 子表，但清单独立版本维护的写边界待 one-data 定稿，故暂登记为 entity；备选 aggregate。one-data 裁定（B31_PHYSICAL_MODEL_RULINGS §2）：配置不挂聚合，共用配置集根加版本头，各配置一张条目表；物理表 employee_activity_type_items。 |
| `anniversary_card` | 周年贺卡 | AnniversaryCard | 领域 · organization.administration | 员工活动类型之一：全体正式员工满周年时由系统自动生成的贺卡活动。 |  | 自动生成作业需要按类型引用它，故需常量；其余两类（节日活动、创新项目发布）只是配置数据，不进代码。 |
| `anniversary_date` | 周年日期 | AnniversaryDate | 领域 · organization.administration | 员工本次入职日期在某一年的对应日期；闰日入职在平年取 02-28，重新入职从本次入职重算。 |  | 依赖入职日期（hire_date），该词属 organization.workforce，词表尚未登记，见报告。 |
| `activity_cost_estimate` | 活动费用预估 | ActivityCostEstimate | 领域 · organization.administration | 登记员工活动时必填并留底的预估金额，只留底、不核对预算；预算上线后另加检查。 |  | 金额按 Money 承载，列为金额加币种。 |
| `contract_original_loan_record` | 合同原件借阅记录 | ContractOriginalLoanRecord | 领域 · organization.administration | 合同原件借出、归还与续借的记录：同一份原件同时只能有一笔未归还的借出，借出时填归还日；先行期记在行政事务，合同模块建成后按 INV-125 移交。 |  | 放行政事务，按合同档案目标形状建；W3 移交 contract.agreement（B-31 裁定第 5 项，ADR-0074）。聚合稿称「协议原件借出」，本条按设计称「合同原件」。 |
| `contract_original_loan_approval` | 合同原件借阅审批 | ContractOriginalLoanApproval | 领域 · organization.administration | 合同原件借出与续借经通用审批办理时使用的审批单据类型：合同经办部门负责人批，外借再加该公司负责人。 |  | 设计 §4.1 只列两个审批 doc_type，Q9、Q10 实际新增此类，需补入映射表。 |
| `offsite_loan` | 外借 | OffsiteLoan | 领域 · organization.administration | 合同原件离开办公场所或交给外部人员的借阅，须写明用途、地点与携带人，另经该公司负责人批准。 |  |  |
| `loan_renewal` | 续借 | LoanRenewal | 领域 · organization.administration | 借阅期满前经原批准人同意的一次续借，续借为新审批，每笔借阅至多一次。 |  |  |
| `loan_overdue_reminder` | 借阅逾期提醒 | LoanOverdueReminder | 领域 · organization.administration | 借阅逾期时提醒借阅人及其部门负责人，逾期满 7 天升级到行政负责人。 |  |  |
| `seal_usage_request_approved` | 用印申请已批准 | SealUsageRequestApproved | 领域 · organization.administration | 用印申请经人工审批通过而置为已批准（先行期的既有审批凭证通道经行政核对后同样置为已批准）。 |  |  |
| `seal_usage_registered` | 用印已登记 | SealUsageRegistered | 领域 · organization.administration | 经办人登记了一次实际用印，每次登记发布，关闭不删除凭据。 |  | 用户 GL4 答复定名「用印已登记」；「用印记录」「用印已办理」不用。 |
| `it_service_ticket_resolved` | 信息技术服务工单已解决 | ItServiceTicketResolved | 领域 · organization.administration | 信息技术服务工单经受理后判定需求成立且可实现并已解决；需求不成立或无法实现直接退回，不发布本事件。 |  | 设计与事件目录称「IT 需求已解决」，其中「IT 需求」是旧称。 |
| `it_asset_issued` | 信息技术资产已领用 | ItAssetIssued | 领域 · organization.administration | 一件信息技术资产登记领用，开启了使用人的使用期间。 |  | 设计与事件目录称「资产已领用」；加「信息技术」以区别于公司资产；英文 Issued 备选 Assigned。 |
| `it_asset_transferred` | 信息技术资产已调拨 | ItAssetTransferred | 领域 · organization.administration | 一件信息技术资产在同一事务内结束调出方使用期间并开启调入方使用期间。 |  | 设计与事件目录称「资产已调拨」。 |
| `it_asset_returned` | 信息技术资产已归还 | ItAssetReturned | 领域 · organization.administration | 一件信息技术资产经归还命令结束了使用期间；调拨结束旧期间不算归还。 |  | 设计与事件目录称「资产已归还」。 |
| `employee_activity_request_approved` | 员工活动申请已批准 | EmployeeActivityRequestApproved | 领域 · organization.administration | 员工活动登记经规则校验通过，不是人工批准，周年自动生成同样发布。 |  | 界面须说明「已批准」仅表达规则通过。 |
| `submit_seal_usage_request` | 提交用印申请 | SubmitSealUsageRequest | 领域 · organization.administration | 申请人提交用印申请：无既有审批凭证的进入审批，使用既有审批凭证的经行政核对后办理；原单重提保留编号并递增轮次。 |  |  |
| `approve_seal_usage_request` | 批准用印申请 | ApproveSealUsageRequest | 领域 · organization.administration | 审批通过回调：把审批中的用印申请置为已批准，须匹配实例、轮次与当前状态。 |  |  |
| `reject_seal_usage_request` | 驳回用印申请 | RejectSealUsageRequest | 领域 · organization.administration | 审批驳回回调：把审批中的用印申请置为已驳回，保留旧轮次与意见。 |  |  |
| `withdraw_seal_usage_request` | 撤回用印申请 | WithdrawSealUsageRequest | 领域 · organization.administration | 申请人在审批期撤回用印申请。 |  | 命令目录标注为缺失命令，沿用分片提议名 WithdrawSealApplication 的词形，改用 SealUsageRequest。 |
| `register_seal_usage` | 登记用印 | RegisterSealUsage | 领域 · organization.administration | 经办人登记一次实际用印，累计份数不得超过批准份数，也不得超过同一来源累计批准份数。 |  |  |
| `close_seal_usage_request` | 关闭用印申请 | CloseSealUsageRequest | 领域 · organization.administration | 用印份数到齐或经确认放弃剩余份数后，规则关闭用印申请；关闭不删除实际登记与凭据。 |  | 命令目录定为系统自动、不设人工关闭，§9 第 2 条却写「行政可关闭」，见报告问题清单。 |
| `submit_it_service_ticket` | 提交信息技术服务工单 | SubmitItServiceTicket | 领域 · organization.administration | 提出人提交工单：类别要求人审的进入审批，常规类别直接待受理；未列入类别表的拒绝。 |  | 设计与命令目录称「提交 IT 需求」。 |
| `approve_it_service_ticket` | 批准信息技术服务工单 | ApproveItServiceTicket | 领域 · organization.administration | 审批通过回调：把审批中的工单置为已提交。 |  | 设计称「批准 IT 需求」。 |
| `reject_it_service_ticket` | 驳回信息技术服务工单 | RejectItServiceTicket | 领域 · organization.administration | 审批驳回回调：把审批中的工单置为已退回。 |  | 设计称「驳回 IT 需求」，命令目录标注为缺失命令。 |
| `withdraw_it_service_ticket` | 撤回信息技术服务工单 | WithdrawItServiceTicket | 领域 · organization.administration | 提出人在审批期撤回工单。 |  | 设计称「撤回 IT 需求」，命令目录标注为缺失命令。 |
| `accept_it_service_ticket` | 受理信息技术服务工单 | AcceptItServiceTicket | 领域 · organization.administration | 处理人受理已提交的工单，或在提出人反馈未解决时重开回处理中；不设受理超时提醒。 |  | 设计称「受理 IT 需求」。 |
| `resolve_it_service_ticket` | 解决信息技术服务工单 | ResolveItServiceTicket | 领域 · organization.administration | 处理人写出处理结论并把处理中的工单置为已解决，同时发布工单已解决事件。 |  | 设计称「解决 IT 需求」。 |
| `close_it_service_ticket` | 关闭信息技术服务工单 | CloseItServiceTicket | 领域 · organization.administration | 仅提出人人工确认并已有处理结论时关闭已解决的工单，系统无代关权。 |  | 设计称「关闭 IT 需求」。 |
| `return_it_service_ticket` | 退回信息技术服务工单 | ReturnItServiceTicket | 领域 · organization.administration | 处理人因需求不成立或无法实现，把处理中的工单直接退回，不发布解决事件。 |  | 设计称「退回 IT 需求」。 |
| `reopen_it_service_ticket` | 重开信息技术服务工单 | ReopenItServiceTicket | 领域 · organization.administration | 提出人反馈未解决时把已解决的工单重开回处理中。 |  | 设计 §1.1 把「重开」列为须交付能力，命令目录却并入「受理 IT 需求」，是否独立命令待 one-architecture 定。 |
| `request_it_asset_issue` | 申请领用资产 | RequestItAssetIssue | 领域 · organization.administration | 使用人申请领用资产：岗位标准规则通过则直达已通过，资产已有使用人则提前拒绝，不转人审。 |  |  |
| `register_it_asset_card` | 登记资产卡片 | RegisterItAssetCard | 领域 · organization.administration | 行政登记一张公司资产卡片号，建立信息技术资产使用记录的根；先行期根只来自迁入与本命令，领用申请的卡片号必须已有根。 |  | one-data 更正第 2 项；权限码由本卡定。 |
| `register_it_asset_issue` | 登记领用 | RegisterItAssetIssue | 领域 · organization.administration | 资产管理员在领用申请已通过后登记交付，开启使用期间；使用人不得登记自己的领用。 |  |  |
| `request_it_asset_transfer` | 申请调拨资产 | RequestItAssetTransfer | 领域 · organization.administration | 在用资产形成调拨申请；资产无当前使用人则转为领用，跨法人转让不在此办理。 |  | 命令目录标注为缺失命令，沿用分片提议名 RequestAssetTransfer 的词形。 |
| `revoke_it_asset_transfer_request` | 撤销调拨申请 | RevokeItAssetTransferRequest | 领域 · organization.administration | 调出使用人或资产管理员撤销尚未登记交接的调拨申请。 |  | 命令目录标注为缺失命令；撤销与取消（Cancel）为两个中文词，沿用「撤销」。 |
| `transfer_it_asset` | 调拨资产 | TransferItAsset | 领域 · organization.administration | 登记调出方与调入方交接，同一事务结束旧使用期间并开启新使用期间。 |  |  |
| `return_it_asset` | 归还资产 | ReturnItAsset | 领域 · organization.administration | 资产管理员验收归还并结束使用期间；离职提醒不改变在用状态。 |  |  |
| `report_it_asset_fault` | 报修资产 | ReportItAssetFault | 领域 · organization.administration | 当前使用人或资产管理员代报，登记资产故障报修。 |  |  |
| `register_it_asset_repair_result` | 登记维修结果 | RegisterItAssetRepairResult | 领域 · organization.administration | 资产管理员登记已修复或无法修复、完成日期与维修单或发票引用。 |  |  |
| `submit_employee_activity_request` | 提交员工活动申请 | SubmitEmployeeActivityRequest | 领域 · organization.administration | 登记员工活动并由规则校验直达已批准；周年自动生成与人工登记共用本入口，重复提交返回原记录。 |  |  |
| `complete_employee_activity` | 完成员工活动 | CompleteEmployeeActivity | 领域 · organization.administration | 行政把已批准的员工活动登记置为已办结，已取消的拒绝办结。 |  |  |
| `cancel_employee_activity_registration` | 取消员工活动登记 | CancelEmployeeActivityRegistration | 领域 · organization.administration | 行政把已批准或已办结的活动登记取消，原因必填并记取消人与时间，保留原记录，不设人审。 |  | 来自 ARC-1017-APE-02 提议。 |
| `waive_remaining_seal_copies` | 放弃剩余用印份数 | WaiveRemainingSealCopies | 领域 · organization.administration | 申请人放弃批准后未用的份数，放弃即释放来源份数占用并可触发规则关闭。 |  | 设计只在 §9 第 2 条写「申请人可放弃未用份数」，命令目录把放弃作为「登记用印」的参数；是否独立命令待定。 |
| `register_blank_page_pre_stamp` | 登记预盖空白页 | RegisterBlankPagePreStamp | 领域 · organization.administration | 投标用印经批准后登记预盖的空白页张数、用途、领用人、日期与编号。 |  |  |
| `reclaim_blank_page_pre_stamp` | 收回预盖空白页 | ReclaimBlankPagePreStamp | 领域 · organization.administration | 预盖空白页用完后登记收回。 |  |  |
| `void_blank_page_pre_stamp` | 作废预盖空白页 | VoidBlankPagePreStamp | 领域 · organization.administration | 预盖空白页未使用时当场记作废，不删除原登记。 |  |  |
| `verify_prior_approval_page` | 核对既有审批页 | VerifyPriorApprovalPage | 领域 · organization.administration | 行政核对既有审批页与所填单号、份数是否一致并留痕（谁核、何时核）。 |  | 业务上指老系统已审批单据；迁入对照走 platform.integration；领域内不出现老系统名称（CLAUDE.md 第 1 条）；用语待 one-glossary Q10 |
| `record_transition_channel_review` | 登记过渡通道季度复核 | RecordTransitionChannelReview | 领域 · organization.administration | 行政负责人完成季度复核待办并留下复核记录。 |  | 业务上指老系统已审批单据；迁入对照走 platform.integration；领域内不出现老系统名称（CLAUDE.md 第 1 条）；用语待 one-glossary Q10 |
| `record_transition_channel_spot_check` | 登记过渡通道月度抽检 | RecordTransitionChannelSpotCheck | 领域 · organization.administration | 行政完成月度抽检待办并留下抽检结果。 |  | 业务上指老系统已审批单据；迁入对照走 platform.integration；领域内不出现老系统名称（CLAUDE.md 第 1 条）；用语待 one-glossary Q10 |
| `administration_status__draft` | 草稿 | Draft | 领域 · organization.administration | 单据已起草、尚未提交的状态。 |  | 使用：用印申请、信息技术服务工单、资产领用与调拨申请、维修单、员工活动申请。 |
| `administration_status__approved` | 已批准 | Approved | 领域 · organization.administration | 用印申请经审批而获批准，或员工活动登记经规则校验通过。 |  | 使用：用印申请、员工活动申请。员工活动的「已批准」仅表达规则通过，不是人工批准，界面须说明。 |
| `administration_status__withdrawn` | 已撤回 | Withdrawn | 领域 · organization.administration | 申请人或提出人在审批期撤回，本轮提前结束，可经同一提交命令重提。 |  | 使用：用印申请、信息技术服务工单。 |
| `administration_status__stamped` | 已用印 | Stamped | 领域 · organization.administration | 用印申请已有实际用印登记，分次登记保持本状态。 |  | 使用：用印申请。备选英文 SealUsed。 |
| `administration_status__closed` | 已关闭 | Closed | 领域 · organization.administration | 用印份数到齐或放弃剩余份数后规则关闭，或工单经提出人确认关闭。 |  | 使用：用印申请、信息技术服务工单。 |
| `administration_status__submitted` | 已提交 | Submitted | 领域 · organization.administration | 信息技术服务工单已提交、待受理。 |  | 使用：信息技术服务工单。常规类别提交后或需审批类别批准后进入。 |
| `administration_status__in_progress` | 处理中 | InProgress | 领域 · organization.administration | 信息技术服务工单已受理或重开，处理人正在处理。 |  | 使用：信息技术服务工单。 |
| `administration_status__resolved` | 已解决 | Resolved | 领域 · organization.administration | 处理人写出处理结论并解决工单，待提出人确认关闭。 |  | 使用：信息技术服务工单。 |
| `administration_status__issued` | 已领用 | Issued | 领域 · organization.administration | 领用申请已登记交付，使用人已持有资产。 |  | 使用：资产领用申请。 |
| `administration_status__requested` | 已申请 | Requested | 领域 · organization.administration | 调拨申请已提交，待登记交接。 |  | 使用：资产调拨申请。 |
| `administration_status__transferred` | 已调拨 | Transferred | 领域 · organization.administration | 调拨申请已登记交接，旧使用期间结束且新期间开启。 |  | 使用：资产调拨申请。 |
| `administration_status__converted_to_issue` | 已转领用 | ConvertedToIssue | 领域 · organization.administration | 调拨申请发现资产无当前使用人，改走领用。 |  | 使用：资产调拨申请。 |
| `administration_status__in_use` | 在用 | InUse | 领域 · organization.administration | 使用期间尚未结束，资产仍由该使用人持有。 |  | 使用：资产使用期间。 |
| `administration_status__ended` | 已结束 | Ended | 领域 · organization.administration | 使用期间因归还或调拨而结束。 |  | 使用：资产使用期间。 |
| `administration_status__ended_by_return` | 因归还结束 | EndedByReturn | 领域 · organization.administration | 使用期间的结束原因：资产被归还。 |  | 使用：资产使用期间。风格同 permission_adjustment_status__closed_by_separation（因离职关闭）。 |
| `administration_status__ended_by_transfer` | 因调拨结束 | EndedByTransfer | 领域 · organization.administration | 使用期间的结束原因：资产被调拨给他人。 |  | 使用：资产使用期间。 |
| `administration_status__fault_reported` | 已报修 | FaultReported | 领域 · organization.administration | 维修单已登记故障，待登记维修结果。 |  | 使用：维修单。 |
| `administration_status__repair_registered` | 已维修登记 | RepairRegistered | 领域 · organization.administration | 维修单已登记维修结果，终态。 |  | 使用：维修单。 |
| `repair_result__repaired` | 已修复 | Repaired | 领域 · organization.administration | 维修结果取值：故障已修复。 |  |  |
| `repair_result__unrepairable` | 无法修复 | Unrepairable | 领域 · organization.administration | 维修结果取值：无法修复，不自动报废也不自动归还，报废归公司资产所在模块。 |  |  |
| `administration_status__cancelled` | 已取消 | Cancelled | 领域 · organization.administration | 员工活动登记因记错或活动取消而作废，终态，不可修改、恢复或完成，重登另建记录并引用被取消记录。 |  | 使用：员工活动申请。 |
| `administration_status__concluded` | 已办结 | Concluded | 领域 · organization.administration | 员工活动登记的活动已办理完毕，终态之一；已取消的不可办结。 |  | 用户 GL4 答复定名「已办结」，不用已完成；使用：员工活动申请。 |
| `pre_stamp_status__registered` | 已登记 | Registered | 领域 · organization.administration | 预盖空白页已登记张数与领用人，尚未收回或作废。 |  |  |
| `pre_stamp_status__reclaimed` | 已收回 | Reclaimed | 领域 · organization.administration | 预盖空白页用完后已登记收回。 |  |  |
| `pre_stamp_status__voided` | 已作废 | Voided | 领域 · organization.administration | 预盖空白页未使用而作废，不删除。 |  |  |

另有错误码 44 个（kind 为 `error_code`）、技术词 1 个（`common_words`），不在表中。

## organization.attendance（`organization_attendance.yaml`）

organization.attendance 提议术语（proposed，待业务评审，ADR-0018）。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `official_notice` | 官方通知 | OfficialNotice | 领域 · organization.attendance | 国家或地方政府发布的放假与调休安排通知，以文号和链接标识；工作日历版本确认时必须挂上（IMP-1021-PAR-02，one-data 审查 WORK-CALENDAR-DDL-2026-10-21 更正三、五） |  | 列名 official_notice_number、official_notice_url 用此词，暂名，待 one-glossary 定名 |
| `make_up_workday_cancelled` | 调休上班日取消 | MakeUpWorkdayCancelled | 领域 · organization.attendance | 官方安排的调休上班日被公司取消、按普通周末休息计的例外日取值；必须是周六或周日，依据为公司加休或手工更正并写明取消的是哪份官方通知里的哪个调休（IMP-1021-PAR-02，更正六） |  | 暂名，待 one-glossary 定名 |

另有错误码 8 个（kind 为 `error_code`）、技术词 8 个（`common_words`），不在表中。

## `organization_workforce.yaml`

涉及模块：`organization.workforce`、`platform.integration`。

Phase 1a organization.workforce 新增术语（proposed，待业务评审，ADR-0018）

新词一律 status: proposed；通用技术词放 common_words，业务概念放 terms。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `employment_verdict` | 在职判定 | EmploymentVerdict | 领域 · organization.workforce | 组织快照对某员工是否在职的判定：在职 EMPLOYED、离职 SEPARATED、快照可用而未列出 NOT_LISTED、快照不可用或过期 SNAPSHOT_UNAVAILABLE（ADR-0033 第 3 项第 7 条，应急登录在职校验用） |  | R-1b P2-H2 |
| `login_lookup` | 登录查人 | LoginLookup | ACL（过渡期） · organization.workforce | 按外部登录标识（企业微信 userid 或外部登录名）在组织快照中找员工；随 DA-3 外部身份移到 identity 后删除 |  | R-1b P2-H2，两个按登录标识查人的方法合一以守住 api 方法上限 |
| `workforce__sync_diff` | 同步差异 | SyncDiff | ACL（过渡期） · platform.integration | 一次同步运行改动的对象与字段，以及发现的数据例外（供数据管理员处理） |  |  |
| `workforce__login_name` | 登录名 | LoginName | ACL（过渡期） · platform.integration | 员工在组织源的登录名（手机号）；没有即离职（RULE-363） |  |  |
| `workforce__staff_employee` | 员工任职快照 | StaffEmployee | ACL（过渡期） · organization.workforce | 一名员工连同其任职，是同步比较与写入的单位 |  |  |
| `workforce__relationship` | 关系 | Relationship | ACL（过渡期） · platform.integration | A8 中人员与组织单元之间的关系（部门负责人、区域应收负责人），类型 ID 为配置项 |  |  |
| `separation_observed_at` | 离职观察时间 | SeparationObservedAt | ACL（过渡期） · platform.integration | 组织同步第一次把员工判为离职、并写入快照的那一轮同步的提交时刻；只表示新系统这样判定了，不是人事生效日 | 「离职日期」已禁用；「separation_date」已禁用 | 单说离职日期有歧义，须写明预计离职日期或离职观察时间 |
| `attribution_subject` | 法人归属对象 | AttributionSubject | 领域 · organization.workforce | 法人归属所指的对象，是一个组织单元或一名员工，由对象类别与对象 ID 共同确定；同一对象在同一时点只归属一个法人 |  | 类型 AttributionSubjectId 为它的标识（kind + subjectId，events/workforce.yaml x-type）；落库按 DA-5 拆为 org_unit_id / employee_id 两列 |

另有错误码 10 个（kind 为 `error_code`）、技术词 86 个（`common_words`），不在表中。

## `payroll-payment-and-people-commands.yaml`

涉及模块：`cost.costing`、`organization.payroll`、`organization.workforce`、`payables.payment`。

薪酬行支付子状态机、人事命令、跨法人用语（one-glossary 2026-10-22，NAM-1021-GLO-19；one-arch-people、one-arch-finance 来单，proposed）。状态名暂定，随用户认可。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `payroll_settlement_line` | 薪酬结算行 | PayrollSettlementLine | 领域 · organization.payroll | 结算单上一名员工的一行，带支付状态；OBJ-73 原文称「薪酬行」，与薪酬结算单对应写全称；也是状态转换记录里该子实体的聚合类型码值（ADR-0075） |  |  |
| `payroll_disbursement` | 薪酬代发 | PayrollDisbursement | 领域 · organization.payroll | 按薪酬结算行经银行代发渠道向员工账户发放薪酬的动作及其结果 |  |  |
| `payroll_line_status__pending_payment` | 待支付 | PendingPayment | 领域 · organization.payroll | 薪酬结算行的支付状态：已确认应发，尚未代发或补发后等待再次代发 |  |  |
| `payroll_line_status__paid` | 已实付 | Paid | 领域 · organization.payroll | 薪酬结算行的支付状态：代发结果登记为成功，实际已付到员工账户（OBJ-73 原文） |  |  |
| `payroll_line_status__payment_failed` | 支付失败 | PaymentFailed | 领域 · organization.payroll | 薪酬结算行的支付状态：代发结果登记为失败，待更正工资卡后补发 |  |  |
| `register_payroll_disbursement_result` | 登记代发结果 | RegisterPayrollDisbursementResult | 领域 · organization.payroll | 登记薪酬代发的结果（成功或失败），薪酬结算行由待支付转已实付或支付失败；动词登记＝只记录事实 |  |  |
| `initiate_payroll_redisbursement` | 发起薪酬补发 | InitiatePayrollRedisbursement | 领域 · organization.payroll | 对支付失败的薪酬结算行发起补发，转回待支付；发出「薪酬补发已发起」 |  |  |
| `payroll_disbursement_result_registered` | 薪酬代发结果已登记 | PayrollDisbursementResultRegistered | 领域 · organization.payroll | 薪酬代发的结果已登记 |  |  |
| `payroll_redisbursement_initiated` | 薪酬补发已发起 | PayrollRedisbursementInitiated | 领域 · organization.payroll | 薪酬补发已发起；B-10 订阅 |  |  |
| `end_legal_entity_attribution` | 结束法人归属 | EndLegalEntityAttribution | 领域 · organization.workforce | 结束一段法人归属并写结束日；与已登记事件「法人归属已结束」对应（one-arch-people 来单称「终止法人归属」，改用「结束」与事件一致） |  |  |
| `correct_legal_entity_code` | 更正法人主体代码 | CorrectLegalEntityCode | 领域 · organization.workforce | 在未发号前更正法人主体代码（INV-59）；与已登记事件「法人主体代码已更正」对应（来单称「更正法人代码」，补上「主体」与词条一致） |  |  |
| `oversight_scope` | 分管范围 | OversightScope | 领域 · organization.workforce | 一名分管领导分管的组织单元或事务范围（B-13） |  |  |
| `oversight_scope_version` | 分管范围版本 | OversightScopeVersion | 领域 · organization.workforce | 分管范围的版本化配置（AGG-135，表 oversight_scope_versions）：生效区间不重叠、只追加 |  |  |
| `cross_entity_labor` | 跨法人人工 | CrossEntityLabor | 领域 · cost.costing | 员工当日成本法人与项目承担法人不一致的人工与项目费用（用户 LEB-04） |  |  |
| `cross_entity_list` | 跨法人清单 | CrossEntityList | 领域 · cost.costing | 财务每月收到的清单：跨法人人工、项目费用与未转入售前投入的部分（用户 LEB-05） |  |  |
| `register_disbursement_return` | 登记代发退回 | RegisterDisbursementReturn | 领域 · payables.payment | 出纳凭银行退回回单登记一笔代发的部分退回，付款仍为已核销，累计退回额增加；幂等键为付款标识加银行回单号；发「付款已部分退回」；全额退回仍走登记退票 |  |  |
| `payment_partially_returned` | 付款已部分退回 | PaymentPartiallyReturned | 领域 · payables.payment | 一笔付款登记了部分退回；应付侧订阅后冲回等额应付核销 |  |  |
| `register_payroll_redisbursement_detail` | 登记薪酬补发明细 | RegisterPayrollRedisbursementDetail | 领域 · payables.payment | 在原应付薪酬上登记补发明细，不新建应付、不过账；幂等键为薪酬行标识加补发轮次；付款流程按明细付清原应付的未结余额 |  |  |
| `payroll_redisbursement_detail` | 补发明细 | PayrollRedisbursementDetail | 领域 · payables.payment | 原应付薪酬上的补发明细：薪酬行、补发轮次、收款人与账户、金额（等于原实发）；与人事侧「薪酬补发」是同一事的应付侧记录 |  |  |

## platform.approval（`platform-approval.yaml`）

platform.approval 提议术语（proposed，待业务评审，ADR-0018）。单词级术语：服务于代码标识、URL、事件键、错误码的命名守护。

无错误码以外的术语。

另有错误码 31 个（kind 为 `error_code`）、技术词 111 个（`common_words`），不在表中。

## platform.audit（`platform-audit-paging.yaml`）

K-3b-4 审计列表、导出、本人登录记录接入分页公共契约新增的标识符（proposed）。登记会话：one-1a-next-5，2026-10-16。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `audit_filter` | 审计筛选条件 | AuditFilter | 领域 · platform.audit | 审计条目列表与导出共用的十个筛选条件（操作人、代办人、动作、动作前缀、对象种类、对象、来源地址、结果、起止时间）；构造时校验对象与种类一致、起早于止 |  | K-3b-4，取代原 AuditQuery 中的筛选部分 |
| `audit_page_mark` | 审计页标记 | AuditPageMark | 领域 · platform.audit | 审计条目键集分页的位置：上一页最后一条的发生时间与条目标识；只供生成下一页游标 | 「AuditPosition」已禁用 | K-3b-4 复审 M1；原名 AuditPosition 与岗位 Position（organization.workforce）同名异义，故改名 |
| `own_login_record_page_view` | 本人登录记录分页视图 | OwnLoginRecordPageView | 领域 · platform.audit | 本人登录记录一页的应用层读模型：记录列表加下一页游标；不含条目标识 |  | K-3b-4，构词沿用审计条目分页视图 |

## platform.file（`platform-file-areas.yaml`）

文件服务存放位置用语（one-glossary 2026-10-21，NAM-1021-GLO-04；技术设施词，proposed）。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `staging_area` | 临时区 | StagingArea | 领域 · platform.file | 对象存储里放已通过检测、尚未关联到业务单据的文件内容的前缀（键 <环境前缀>/staging/<来源>/<文件 ID>），时效按来源由生命周期规则决定；关联时复制到正式键 |  | 口语「临时上传区」不入词表；与迁入暂存、检测暂存目录不同层，见 glossary/arch-naming/NAM-1021-GLO-04.md |
| `inspection_staging_directory` | 检测暂存目录 | InspectionStagingDirectory | 领域 · platform.file | 后端实例本地磁盘上的专用目录，上传流先落盘于此、算哈希并交病毒检测，通过后才写入临时区；不对外签发链接，由年龄清扫作业清理 |  | ADR-0070 暂名「本地检测暂存目录」，去掉「本地」（目录本就在实例本地，不是区别项） |
| `quarantine_prefix` | 隔离前缀 | QuarantinePrefix | 领域 · platform.file | 对象存储里放检测未通过文件内容的键前缀（<环境前缀>/quarantine/<文件 ID>），不可外读，只授安全角色经审批读取，保存期满清除内容 |  | 与文件状态「已隔离」配套：前缀是存放位置，状态是文件的判定结果 |

## platform.file（`platform-file.yaml`）

platform.file 提议术语（proposed，ADR-0018；one-platform-file 2026-10-21 登记，待 one-glossary 评审）。

来源：ADR-0060「须交其他会话」用语、ADR-0061、ADR-0070；错误码中文名全局唯一。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `stored_file` | 存储文件 | StoredFile | 领域 · platform.file | 文件服务的根：一份不可变内容及其哈希、大小、媒体类型、上传主体、检测状态与版本 / 派生引用 |  |  |
| `content_hash` | 内容哈希 | ContentHash | 领域 · platform.file | SHA-256，小写十六进制 64 位，本系统唯一的内容哈希，比对文件以它为准；代码里 platform.approval.api 另有同名类型 ContentHash（待审内容指纹，未登记术语），不是同一概念 |  |  |
| `file_subject` | 文件主体 | FileSubject | 领域 · platform.file | 写入或关联文件的主体（类型 + ID），类型取值用户、一次性链接、平台模块；作上传者时称上传主体，作关联者时称关联主体 |  |  |
| `uploader` | 上传主体 | Uploader | 领域 · platform.file | 写入文件的文件主体 |  |  |
| `file_lineage` | 版本与派生 | FileLineage | 领域 · platform.file | 写入时声明、此后不可改的文件引用：首版、上一版本（同一逻辑文件的线性修订）或派生自（经处理得到的新文件）；词 Lineage 作类型名 |  |  |
| `derivation_kind` | 派生方式 | DerivationKind | 领域 · platform.file | 派生件与源文件的关系，本期取值签署、格式转换 |  |  |
| `format_conversion` | 格式转换 | FormatConversion | 领域 · platform.file | 派生方式取值：由另一文件转换格式得到（如出件生成的 PDF） |  |  |
| `bind_file` | 关联文件 | BindFile | 领域 · platform.file | 所属模块把文件挂到单据，同时给出访问级别、个人信息标记、保存类别与起算单据，缺一即拒；关联前任何人不可下载 |  |  |
| `file_binding` | 文件关联 | FileBinding | 领域 · platform.file | 文件与单据的关联记录，只追加；一个文件可关联多张单据 |  |  |
| `file_hold` | 封存登记 | FileHold | 领域 · platform.file | 业务聚合封存时对文件的登记，只追加、不可撤销，保存期内拒绝处置；避开 seal（签章） |  |  |
| `download_link` | 临时下载链接 | DownloadLink | 领域 · platform.file | 预签名 GET 链接，默认 5 分钟、上限 15 分钟，强制附件下载；地址不进日志与审计 |  |  |
| `link_validity` | 链接时效 | LinkValidity | 领域 · platform.file | 临时下载链接的有效时长 |  |  |
| `media_type` | 媒体类型 | MediaType | 领域 · platform.file | 按内容判定并登记的文件类型，须在白名单内 |  |  |
| `contains_macros` | 含宏 | ContainsMacros | 领域 · platform.file | 文件属性：按扩展名或内容判定文件含宏（VBA、XLM 宏表、远程模板等），判定失败按含宏；含宏文件不预览、下载时提示来源 |  |  |
| `quarantined` | 已隔离 | Quarantined | 领域 · platform.file | 文件状态：检测未通过，内容在隔离前缀，不可关联、下载、预览 |  |  |
| `expired_unbound` | 未关联已过期 | ExpiredUnbound | 领域 · platform.file | 文件状态：在临时区到期仍未关联，对象由生命周期规则过期 |  |  |
| `disposed` | 已处置 | Disposed | 领域 · platform.file | 文件状态：保存期满经两人确认删除或去标识，行与哈希保留 |  |  |
| `access_level` | 访问级别 | AccessLevel | 领域 · platform.file | PUBLIC / INTERNAL / SENSITIVE / RESTRICTED，对应 L1–L4；关联时由所属模块给出 |  |  |
| `classification` | 分级 | Classification | 领域 · platform.file | 一次文件关联给出的访问级别与个人信息标记两项；同一文件同一单据重复关联须分级相同，否则拒绝 |  | one-platform-file 2026-10-21 单元 6 登记；保存类别不在分级内 |
| `personal_information_mark` | 个人信息标记 | PersonalInformationMark | 领域 · platform.file | NONE / PI / SPI；关联时由所属模块给出，SPI 在 U-14 定前拒收 |  |  |
| `pi` | 含个人信息 | Pi | 领域 · platform.file | 个人信息标记取值 PI |  |  |
| `spi` | 含敏感个人信息 | Spi | 领域 · platform.file | 个人信息标记取值 SPI |  |  |
| `preservation_category` | 保存类别 | PreservationCategory | 领域 · platform.file | 文件所属的保存期限表类别，关联时由所属模块给出；文件保存期限与数据保留期限（data_retention_period）不同：后者管在线库与归档库的留存 |  |  |
| `preservation_anchor` | 起算单据 | PreservationAnchor | 领域 · platform.file | 其业务事件（合同履行完毕、项目结项、用印完成等）起算文件保存期的单据；关联时给出，无另外起算单据的即本单据 |  |  |
| `agreed_preservation` | 约定保存年限 | AgreedPreservation | 领域 · platform.file | 合同约定的保存年限，由合同模块写入；到期日取类别默认与约定的较长者 |  |  |
| `import_rejection_reason` | 迁入文件拒收原因 | ImportRejectionReason | 领域 · platform.file | 迁入文件被拒的原因代码（超上限、类型不允许、加密、已隔离、不可用、敏感个人信息、保存类别不存在），计入核对差异清单；与 platform.integration「迁入拒收」互列 |  |  |
| `imported` | 已迁入 | Imported | 领域 · platform.file | 迁入文件写入并关联成功的结果 |  |  |
| `false_positive_judgment` | 误报判定 | FalsePositiveJudgment | 领域 · platform.file | 安全角色把已隔离文件判为误报，第二人复核；确认后其内容哈希进误报例外清单（ADR-0070 决定 6 第 3 条） |  |  |
| `judged_by` | 判定人 | JudgedBy | 领域 · platform.file | 作出误报判定的安全角色用户；不得兼复核人 |  |  |
| `reviewed_by` | 复核人 | ReviewedBy | 领域 · platform.file | 复核误报判定的第二人；不得是判定人 |  |  |
| `scan_exception` | 误报例外 | ScanException | 领域 · platform.file | 误报例外清单中的一项：按内容哈希豁免，只追加，可经两人撤销 |  |  |
| `added_at` | 加入时间 | AddedAt | 领域 · platform.file | 内容哈希加入误报例外清单的时间 |  |  |
| `deidentified` | 已去标识 | Deidentified | 领域 · platform.file | 原文件名到期后置为固定占位值「[已去标识]」的状态 |  |  |
| `platform_file__threat_name` | 病毒名 | ThreatName | 领域 · platform.file | 检测引擎检出时报告的特征名；与 platform.integration 同名词条互列 |  |  |
| `preservation_sequence_number` | 保存期序号 | PreservationSequenceNumber | 领域 · platform.file | 每个文件的保存期记录从 1 递增的序号，最大者为当前 |  |  |
| `transition_sequence_number` | 转换序号 | TransitionSequenceNumber | 领域 · platform.file | 状态转换表中每个聚合的转换顺序号，取本次变更之后聚合行的 version（ADR-0058）；与「流水号」不同 |  | one-glossary 2026-10-21 定名，避开「流水号」同名 |

另有错误码 20 个（kind 为 `error_code`），不在表中。

## platform.identity（`platform-identity-b31-roles.yaml`）

B-31 配置集的三个负责角色（one-glossary 2026-10-22，NAM-1021-GLO-17；one-data 来单，proposed）。角色代码格式按 one-data：小写 snake，^[a-z][a-z0-9_]{1,59}$。后缀沿用词表惯例用 head（部门负责人 OrgUnitHead、法人财务负责人 LegalEntityFinanceHead），不用 manager。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `administration_head` | 行政负责人 | AdministrationHead | 领域 · platform.identity | 负责行政事务（用印、合同原件借阅、员工活动等）的负责人；B-31 配置集的负责角色之一 |  |  |
| `it_head` | 信息技术负责人 | ItHead | 领域 · platform.identity | 负责信息技术服务与信息技术资产的负责人；B-31 配置集的负责角色之一；中文名以「信息技术」为准，「IT 负责人」不用 |  |  |
| `employee_relations_head` | 员工关系负责人 | EmployeeRelationsHead | 领域 · platform.identity | 负责员工关系事务（如周年通知接收）的负责人；B-31 配置集的负责角色之一 |  |  |

## platform.identity（`platform-identity.yaml`）

platform.identity 新增术语（proposed，待业务评审，ADR-0018）。登录、会话、票据、角色与数据范围用到的词。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `role_version` | 角色版本 | RoleVersion | 领域 · platform.identity | 角色定义（名称、状态、数据范围类型、完整权限集合）的每次变化一行，只追加；当前版本是没有失效时刻的那一行，每个角色至多一个；删除角色是关闭最后一个版本（ADR-0034 第 1 项） |  | 登记会话 one-1a-par（IMP-1017-PAR-02，派工卡 ADR-0034-IMPL 第 11 节）；查重：terms.yaml 与 proposed 中同一 bc 无同名、同中文名 |
| `account_role_grant` | 角色授予 | AccountRoleGrant | 领域 · platform.identity | 某账号在一段时间内持有某角色，只追加；结束一律称收回，收回是关闭开放行，再次获得新增一行（ADR-0034 第 2 项） |  | 登记会话 one-1a-par（IMP-1017-PAR-02，派工卡 ADR-0034-IMPL 第 11 节）；查重：terms.yaml 与 proposed 中同一 bc 无同名、同中文名 |
| `revocation_cause` | 收回原因 | RevocationCause | 领域 · platform.identity | 角色授予被收回的原因：账号授权、离职、运维命令、迁入批次回退（ADR-0034 第 2 项）；列名 revocation_cause_kind |  | 登记会话 one-1a-par（IMP-1017-PAR-02，派工卡 ADR-0034-IMPL 第 11 节）；查重：terms.yaml 与 proposed 中同一 bc 无同名、同中文名 |
| `role_version_closure` | 角色版本关闭 | RoleVersionClosure | 领域 · platform.identity | 删除角色时给最后一个角色版本写下失效时刻、关闭人、关闭理由与审计条目，不再开新版本；被新版本接替的关闭不记这三项（ADR-0034 第 1 项） |  | 登记会话 one-1a-par（IMP-1017-PAR-02）；查重：terms.yaml 与 proposed 中同一 bc 无同名、同中文名 |

另有错误码 3 个（kind 为 `error_code`）、技术词 110 个（`common_words`），不在表中。

## platform.integration（`platform-integration.yaml`）

platform.integration 提议术语（proposed，待业务评审，ADR-0018）。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `registration` | 登记 | Registration | ACL（长期） · platform.integration | 由人把一条来源对照录入系统，先处于待确认，等另一个人确认或拒绝；现在只有核算组织编码可以人工登记 |  |  |
| `evidence` | 取证 | Evidence | 领域 · platform.integration | 只读核对老系统与 A8 生产数据，用于复刻老行为、迁入对账与新旧等价核对；用专用取证账号，只出计数或聚合，或落 ECS 数据盘，每次登记一行 |  | ADR-0037 登记（数据库登录名 one_evidence_ro），待用语确认 |
| `malware_scanner` | 恶意文件检测端口 | MalwareScanner | ACL（长期） · platform.integration | 集成层对文件服务提供的检测端口：按本地检测暂存路径交 clamd SCAN，判定之外一律不可用 |  | one-platform-file 2026-10-21 登记（单元 4） |
| `scan_outcome` | 检测结论 | ScanOutcome | ACL（长期） · platform.integration | CLEAN 通过 / INFECTED 未通过 / ENCRYPTED 加密不可检测；不可用不是结论，抛错 |  | one-platform-file 2026-10-21 登记（单元 4） |
| `clean` | 检测通过 | Clean | ACL（长期） · platform.integration | 检测结论取值：引擎未检出 |  | one-platform-file 2026-10-21 登记（单元 4） |
| `infected` | 检测未通过 | Infected | ACL（长期） · platform.integration | 检测结论取值：引擎检出，文件服务隔离 |  | one-platform-file 2026-10-21 登记（单元 4） |
| `platform_integration__threat_name` | 病毒名 | ThreatName | ACL（长期） · platform.integration | 引擎检出时报告的特征名 |  | one-platform-file 2026-10-21 登记（单元 4） |
| `presign_download` | 签发下载链接 | PresignDownload | ACL（长期） · platform.integration | 以签发身份为正式键生成预签名 GET 链接，强制附件下载；临时区与隔离键拒绝 |  | one-platform-file 2026-10-21 登记（单元 4） |
| `promote_file_object` | 转入正式区 | PromoteFileObject | ACL（长期） · platform.integration | 把临时区对象服务端复制到同一文件的正式键，禁止覆盖并读回核对 |  | one-platform-file 2026-10-21 登记（单元 4） |

另有错误码 7 个（kind 为 `error_code`）、技术词 25 个（`common_words`），不在表中。

## platform.numbering（`platform-numbering.yaml`）

platform.numbering 提议术语（proposed，待业务评审，ADR-0018）。服务于原编号、确认状态、单据类型代码的命名守护。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `qt` | 报价单据类型代码 | Qt | 领域 · platform.numbering | 报价（DocumentType.QUOTE）的单据类型代码 QT，1b 首个使用方 |  | 例 QT-FST-202610-0001 |
| `ts` | 工时单据类型代码 | Ts | 领域 · platform.numbering | 工时（DocumentType.TIMESHEET）的单据类型代码 TS，沿用种子模板中的前缀字母，正式词表尚无对应词 |  | 例 TS-FST-202610-0001；模板待管理员确认 |
| `jv` | 记账凭证单据类型代码 | Jv | 领域 · platform.numbering | 记账凭证（DocumentType.JOURNAL_VOUCHER）的单据类型代码 JV，沿用种子模板中的前缀字母，正式词表尚无对应词 |  | 例 JV-FST-202610-0001；模板待管理员确认 |
| `document_type_registry` | 单据类型登记表 | DocumentTypeRegistry | 领域 · platform.numbering | 编号模块持有的单据类型登记：类型码唯一、登记属主、编号缩写码与生效期；新增一种单据只插一行 |  | REG-1（IMP-1021-NX5-01）新增 |
| `registry` | 登记表 | Registry | 领域 · platform.numbering | 集中登记某类可扩展事项并供写入方校验的表 |  | REG-1（IMP-1021-NX5-01）新增 |
| `unregistered` | 未登记 | Unregistered | 领域 · platform.numbering | 不在登记表中 |  | REG-1（IMP-1021-NX5-01）新增 |
| `bc` | 限界上下文 | Bc | 领域 · platform.numbering | glossary/bcs.yaml 中登记的业务模块标识，如 cost.timesheet；登记属主列用它 |  | REG-1（IMP-1021-NX5-01）新增 |
| `number_code` | 编号缩写码 | NumberCode | 领域 · platform.numbering | 单据类型在统一编号格式中的缩写，大写字母 2 至 4 位，对应模板占位符 {TYPE}；与 terms.yaml 的 document_type_code 同义，后者与 kernel.DocumentTypeCode 同名异义，已登记给 one-glossary 裁定 |  | REG-1（IMP-1021-NX5-01）新增 |

另有错误码 6 个（kind 为 `error_code`）、技术词 3 个（`common_words`），不在表中。

## platform.signing（`platform-signing.yaml`）

签章服务（platform.signing）提议术语（one-glossary 2026-10-22，NAM-1021-GLO-16；准入第 6 条，proposed）。模块标识 platform.signing、schema platform_signing、权限码前缀 platform.signing（由 bcs.yaml 的 platform.signing 派生，英文名 SigningService，用户 2026-10-21 同意）。英文 seal 只指印章；文件封存用 hold，不用 seal。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `seal_ledger` | 印章台账 | SealLedger | 领域 · platform.signing | 公司各法人各类印章的台账，管印章有哪些、什么载体、是否启用、谁保管；用印登记引用其条目（ADR-0064 决定 3） |  |  |
| `seal_ledger_entry` | 印章台账条目 | SealLedgerEntry | 领域 · platform.signing | 台账中一枚印章的一条记录：粒度为法人 × 印章类型，自有主键 UUIDv7，带载体（实体或电子）与启停状态，同一法人同一印章类型任一时点至多一条启用（ADR-0064 决定 3） |  |  |
| `seal_ledger_entry_version` | 印章台账条目版本 | SealLedgerEntryVersion | 领域 · platform.signing | 印章台账条目的版本行：生效日、失效日左闭右开，只追加、只许关闭不许改写；载体、启停、保管部门进版本表，用印登记按用印时刻取当时有效版本（ADR-0064 决定 3，D17） |  |  |
| `seal_type` | 印章类型 | SealType | 领域 · platform.signing | 印章的种类，如公章、合同章、出图专用章；台账条目由法人加印章类型确定；取值清单随先行上线批登记（ADR-0064 决定 3） |  |  |
| `signing_record` | 签章记录 | SigningRecord | 领域 · platform.signing | 签章服务的只追加记录：请求方与申请 ID、台账条目版本或执业印章、经办人或签章人、签前件与签后件的文件标识和内容哈希、服务商交易号、验章结果、时刻；不改不删，更正用新记录关联；只指本模块的记录（ADR-0064 决定 7） |  |  |
| `seal_carrier` | 印章载体 | SealCarrier | 领域 · platform.signing | 印章的载体，取值实体章、电子章；台账条目版本上记载体，先行期全部按实体章办理（ADR-0064 决定 3、5） |  |  |
| `custody_department` | 保管部门 | CustodyDepartment | 领域 · platform.signing | 印章台账条目版本上记的保管该印章的部门（ADR-0064 决定 3） |  |  |
| `seal_type_list` | 印章类型清单 | SealTypeList | 领域 · platform.signing | 行政提供的印章类型取值清单，做成版本化配置表（根表加版本表），不写死枚举；作者与确认人分离，决定出处必填（ADR-0064 决定 3，D15） |  |  |
| `seal_type_list_version` | 印章类型清单版本 | SealTypeListVersion | 领域 · platform.signing | 印章类型清单的一个版本：生效日、决定出处、作者；确认人不得是作者（ADR-0064 决定 3，D15） |  |  |
| `physical` | 实体章 | Physical | 领域 · platform.signing | 印章载体的取值之一：实体印章；先行期全部台账条目按实体章办理，电子章随电子印章批追加（ADR-0064 决定 3、5） |  | 一个 bc 内中英文名查重已做；随签章 api PR 登记，请 one-glossary 复核 |

另有错误码 6 个（kind 为 `error_code`），不在表中。

## platform.web（`platform-web.yaml`）

无错误码以外的术语。

另有错误码 5 个（kind 为 `error_code`）、技术词 13 个（`common_words`），不在表中。

## platform.identity（`recon-pending-renames.yaml`）

已裁定漏登补登（NAM-1017-GLO-06）里，新名已入正式词表而代码仍用旧名的待改名登记。

负责会话改名并合入后，one-glossary 删除对应条目，旧名禁用随之生效。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `recon_pending__role_deleted` | 角色已删除 | RoleDeleted | 领域 · platform.identity | 用户裁定（GL 确认单 B2-B06）已定为「角色已废除 / RoleAbolished」；代码事件类改名合入前保留旧写法 RoleDeleted。 |  | 代码仍用 RoleDeleted |

## `round3-pending-renames.yaml`

涉及模块：`accounting.ledger`、`cost.timesheet`、`organization.workforce`、`platform.approval`、`platform.identity`、`platform.integration`、`platform.numbering`。

第三轮用语确认的待改名登记（one-glossary 生成，round3/writeback.py）：用户已定新名，旧名仍在代码或界面中使用。

负责会话按 glossary/round3/RENAME_CARDS.md 改名并合入后，one-glossary 删除对应条目，方言禁用随之生效。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `round3_pending__act` | 办理 | Act | 领域 · platform.approval | 第三轮用语确认（R3-AP-04）已定为：办理 / Handle（handle）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Act（claude/one-1a-par-f1-acceptance） |
| `round3_pending__assigned` | 已分配 | Assigned | 领域 · platform.approval | 第三轮用语确认（R3-AP-05）已定为：任务已分配 / TaskAssigned（task_assigned）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Assigned（claude/one-1a-next-3-r1b-p2） |
| `round3_pending__completed` | 已完成 | Completed | 领域 · platform.approval | 第三轮用语确认（R3-AP-07）已定为：审批已完成 / ApprovalCompleted（approval_completed）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Completed（main） |
| `round3_pending__escalated` | 已升级 | Escalated | 领域 · platform.approval | 第三轮用语确认（R3-AP-10）已定为：已上推 / ReroutedToSuperior（rerouted_to_superior）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Escalated（claude/one-1a-next-3-r1b-p2） |
| `round3_pending__fallback` | 兜底 | Fallback | 领域 · platform.approval | 第三轮用语确认（R3-AP-11）已定为：转流程管理员 / AdministratorFallback（administrator_fallback）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Fallback（claude/one-1a-next-3-r1b-p2） |
| `round3_pending__form` | 表单 | Form | 领域 · platform.approval | 第三轮用语确认（R3-AP-12）已定为：审批表单字段 / ApprovalField（approval_field）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Form（claude/one-1a-next-3-r1b-p2、claude/one-1a-par-f1-acceptance、claude/one-1a-par-k3a） |
| `round3_pending__round` | 轮次 | Round | 领域 · platform.approval | 第三轮用语确认（R3-AP-15）已定为：审批轮次 / ApprovalRound（approval_round）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Round（claude/one-1a-next-3-r1b-p2、claude/one-1a-par-k3a） |
| `round3_pending__row` | 行 | Row | 领域 · platform.approval | 第三轮用语确认（R3-AP-16）已定为：行级任务 / LineTask（line_task）。代码与界面改名合入前保留旧写法。 |  | 界面文字仍用「行」；代码仍用 Row（claude/one-1a-next-3-r1b-p2、claude/one-1a-par-f1-acceptance、claude/one-1a-par-k3a） |
| `round3_pending__deadline` | 审批时限 | Deadline | 领域 · platform.approval | 第三轮用语确认（R3-AP-21）已定为：审批时限 / ApprovalTimeLimit（approval_time_limit）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Deadline（claude/one-1a-par-k3a） |
| `round3_pending__effect` | 效力 | Effect | 领域 · platform.approval | 第三轮用语确认（R3-AP-22）已定为：办理效力 / TaskEffect（task_effect）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Effect（claude/one-1a-par-f1-acceptance、claude/one-1a-par-k3a） |
| `round3_pending__release_of_task` | 释放 | ReleaseOfTask | 领域 · platform.approval | 第三轮用语确认（R3-AP-25）已定为：取消认领 / Unclaim（unclaim）。代码与界面改名合入前保留旧写法。 |  | 界面文字仍用「释放」 |
| `round3_pending__ui_business_summary` | 业务摘要 | BusinessSummary | 领域 · platform.approval | 第三轮用语确认（R3-AP-41）已定为：审批摘要 / ApprovalSummary（approval_summary）。代码与界面改名合入前保留旧写法。 |  | 界面文字仍用「业务摘要」 |
| `round3_pending__ui_proxy_handling` | 代理 | ProxyHandling | 领域 · platform.approval | 第三轮用语确认（R3-AP-42）已定为：代理办理 / ProxyHandling（ui_proxy_handling）。代码与界面改名合入前保留旧写法。 |  | 界面文字仍用「代理」 |
| `round3_pending__task_participant` | 参与人 | TaskParticipant | 领域 · platform.approval | 第三轮用语确认（R3-AP-53）已定为：任务参与人 / TaskParticipant（task_participant）。代码与界面改名合入前保留旧写法。 |  | 界面文字仍用「参与人」 |
| `round3_pending__decision` | 决定 | Decision | 领域 · platform.approval | 第三轮用语确认（R3-AP-57）已定为：审批决定 / TaskDecision（task_decision）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Decision（main） |
| `round3_pending__resulted` | 已出结果 | Resulted | 领域 · platform.approval | 第三轮用语确认（R3-AP-60）已定为：任务已办理 / TaskHandled（task_handled）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Resulted（main） |
| `round3_pending__skipped` | 已跳过 | Skipped | 领域 · platform.approval | 第三轮用语确认（R3-AP-65）已定为：通知已跳过 / NotificationSkipped（notification_skipped）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Skipped（main） |
| `round3_pending__project_manager_review` | 项目经理审批 | ProjectManagerReview | 领域 · platform.approval | 第三轮用语确认（R3-AP-68）已定为：项目经理审批 / ProjectManagerApproval（project_manager_approval）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 ProjectManagerReview（main） |
| `round3_pending__regional_head_review` | 区域负责人审批 | RegionalHeadReview | 领域 · platform.approval | 第三轮用语确认（R3-AP-69）曾定为区域负责人审批 / RegionalHeadApproval，后由 D5-06 取代，改为：部门负责人审批 / OrgUnitHeadApproval（org_unit_head_approval）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 RegionalHeadReview（main）；D5-06 把区域负责人审批改为部门负责人审批 |
| `round3_pending__supervisor_review` | 一级汇报人审批 | SupervisorReview | 领域 · platform.approval | 第三轮用语确认（R3-AP-70）已定为：直属上级审批 / SupervisorApproval（supervisor_approval）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 SupervisorReview（main） |
| `round3_pending__cutoff` | 撤回截止点 | Cutoff | 领域 · platform.approval | 第三轮用语确认（R3-AP-74）已定为：撤回截止点 / WithdrawalCutoff（withdrawal_cutoff）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Cutoff（main） |
| `round3_pending__administrator_mode` | 管理模式 | AdministratorMode | 领域 · platform.identity | 第三轮用语确认（R3-ID-04）已定为：管理员模式 / AdministratorMode（administrator_mode）。代码与界面改名合入前保留旧写法。 |  | 界面文字仍用「管理模式」 |
| `round3_pending__authorization` | 授权 | Authorization | 领域 · platform.identity | 第三轮用语确认（R3-ID-10）已定为：授予权限 / GrantPermission（grant_permission）。代码与界面改名合入前保留旧写法。 |  | 界面文字仍用「授权」；代码仍用 Authorization（claude/one-1a-next-3-r1b-p2、claude/one-1a-par-f1-acceptance） |
| `round3_pending__account_disabled` | 停用 | AccountDisabled | 领域 · platform.identity | 第三轮用语确认（R3-ID-11）已定为：账号停用 / AccountDisabled（account_disabled）。代码与界面改名合入前保留旧写法。 |  | 界面文字仍用「停用」 |
| `round3_pending__permission_adjustment_kind__sensitive_role_holder` | 敏感角色持有人 | SensitiveRoleHolder | 领域 · platform.identity | 第三轮用语确认（R3-ID-22）已定为：敏感角色复核 / SensitiveRoleReview（permission_adjustment_kind__sensitive_role_review）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 SensitiveRoleHolder（claude/one-1a-par-f1-acceptance） |
| `round3_pending__size` | 大小 | Size | 领域 · platform.integration | 第三轮用语确认（R3-IN-07）已定为：字节数 / ByteCount（byte_count）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Size（claude/one-1a-next-3-r1b-p2、claude/one-1a-par-f1-acceptance、claude/one-1a-par-k3a） |
| `round3_pending__keep` | 保留 | Keep | 领域 · platform.integration | 第三轮用语确认（R3-IN-08）已定为：数据保留期限 / DataRetentionPeriod（data_retention_period）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Keep（claude/one-1a-par-f1-acceptance） |
| `round3_pending__worm` | 一次写入多次读取 | Worm | 领域 · platform.integration | 第三轮用语确认（R3-IN-36）已定为：一次写入多次读取 / WriteOnceReadMany（write_once_read_many）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Worm（main） |
| `round3_pending__voucher_numbering_rule` | 凭证号规则 | VoucherNumberingRule | 领域 · platform.numbering | 第三轮用语确认（R3-NU-08）已定为：记账凭证编号规则 / JournalVoucherNumberingRule（journal_voucher_numbering_rule）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 VoucherNumberingRule（main） |
| `round3_pending__rule_version` | 规则版本 | RuleVersion | 领域 · platform.numbering | 第三轮用语确认（R3-NU-09）已定为：编号规则版本 / NumberingRuleVersion（numbering_rule_version）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 RuleVersion（main） |
| `round3_pending__overhead_hours` | OH 工时 | OverheadHours | 领域 · cost.timesheet | 第三轮用语确认（R3-TS-04）已定为：间接工时 / IndirectHours（indirect_hours）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 OverheadHours（main） |
| `round3_pending__workforce__email` | 企业邮箱 | Email | 领域 · organization.workforce | 第三轮用语确认（R3-WF-06）已定为：企业邮箱 / EmailAddress（workforce__email_address）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Email（claude/one-1a-par-f1-acceptance） |
| `round3_pending__department` | 部门 | Department | 领域 · platform.approval | 第三轮用语确认（R3-AP-09）已定为：并入 org_unit。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Department（claude/one-1a-next-3-r1b-p2、claude/one-1a-par-f1-acceptance） |
| `round3_pending__ui_task_handler` | 办理人 | TaskHandler | 领域 · platform.approval | 第三轮用语确认（R3-AP-32）已定为：并入 actor。代码与界面改名合入前保留旧写法。 |  | 界面文字仍用「办理人」 |
| `round3_pending__running` | 进行中 | Running | 领域 · platform.approval | 第三轮用语确认（R3-AP-61）已定为：并入 ui_approval_running。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Running（main） |
| `round3_pending__ts` | 工时单据类型代码 | Ts | 领域 · platform.numbering | 第三轮用语确认（R3-NU-06）已定为：并入 document_type_code。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Ts（claude/one-design-sync-staging） |
| `round3_pending__qt` | 报价单据类型代码 | Qt | 领域 · platform.numbering | 第三轮用语确认（R3-NU-17）已定为：并入 document_type_code。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Qt（main） |
| `round3_pending__jv` | 记账凭证单据类型代码 | Jv | 领域 · platform.numbering | 第三轮用语确认（R3-NU-18）已定为：并入 document_type_code。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Jv（main） |
| `round3_pending__journal_voucher` | 会计凭证 | JournalVoucher | 领域 · accounting.ledger | 第三轮用语确认（R3-AP-26）已定为：记账凭证 / JournalVoucher（journal_voucher）。代码与界面改名合入前保留旧写法。 |  | 界面文字仍用「会计凭证」 |

## `rules-batch-1021.yaml`

涉及模块：`cost.timesheet`、`kernel`、`platform.approval`、`platform.audit`、`platform.identity`、`platform.integration`。

one-rules 规则卡 RULE-418～504 提到的待登记词（one-glossary 2026-10-21，NAM-1021-GLO-05；proposed，ADR-0018）。

已有词条沿用、不另登记：票据 subject→外部主体键的一种取值（one-architecture 判，platform.integration），待办卡片→通知，管理员离职暂停→待放行离职（separation_hold），核算系统（bcs.yaml system），确认人 / 待确认 / 已确认 / 编号规则版本确认（platform.numbering 已有），本人登录记录（own_login_record_page_view），四眼（只在 ADR 文字里，不登记）。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `content_digest` | 内容摘要 | ContentDigest | 领域 · platform.approval | 待审内容在提交审批时的指纹（列 approved_hash 的取值），用于核对审批的内容未被改动；与文件服务的内容哈希（platform.file）是两个概念，不同 bc |  | platform.approval.api 现有类型 ContentHash 与 platform.file 的 ContentHash 同名，改名交 platform.approval 会话 |
| `process_administrator` | 流程管理员 | ProcessAdministrator | 领域 · platform.approval | 负责审批流程兜底、取不到办理人时接手待办的管理角色 |  |  |
| `operations_command` | 运维命令 | OperationsCommand | 领域 · platform.identity | 命令行的运维操作族（授予角色、撤销角色、登记应急管理员等），须有见证人，区别于界面发起的业务命令 |  |  |
| `usable_emergency_admin` | 可用应急管理员 | UsableEmergencyAdmin | 领域 · platform.identity | 应急管理员中本身启用、所绑定账号启用且仍持有超级管理员的人 |  | 规则文件称 emergency account，沿用词表的应急管理员 |
| `relaxation_mark` | 放宽标注 | RelaxationMark | 领域 · platform.identity | 单人运维放宽项生效时写入的审计记录（single-operator-mode.applied） |  |  |
| `canonical_employee_id` | 规范员工标识 | CanonicalEmployeeId | 共享内核 · kernel | 员工标识的唯一文本形式（UUID 字符串），运维命令的 --employee-id 只接受它 |  |  |
| `browser_identifier` | 浏览器标识 | BrowserIdentifier | 领域 · platform.identity | 身份确认后下发给浏览器的标识 cookie，用于识别是否新浏览器 |  |  |
| `login_method` | 登录方式 | LoginMethod | 领域 · platform.audit | 本人登录记录里这次登录所用的方式 |  |  |
| `login_outcome` | 登录结果 | LoginOutcome | 领域 · platform.audit | 本人登录记录里这次登录成功或失败的结果 |  |  |
| `submitted_date` | 提交日 | SubmittedDate | 领域 · cost.timesheet | 员工提交工时单审批的业务日期（列 submitted_date，与工作日期是两件事） |  |  |
| `accounting_organization_code` | 核算组织编码 | AccountingOrganizationCode | ACL（长期） · platform.integration | 法人在核算系统里的数字编码，登记在来源对照里，只在防腐层使用 |  |  |

## `state-transitions-decisions.yaml`

涉及模块：`platform.audit`、`platform.eventing`。

状态转换记录与决定出处列名的提议术语（one-glossary 2026-10-21，E-009 与 #515 列名；ADR-0018）。

注意：state_transitions.sequence_number 与已有词条 sequence_number（流水号，platform.numbering）同名异义，GlossaryTest G39 会要求该列注释以「流水号」开头，待裁定，见任务 NAM-1021-GLO-04。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `state_transition` | 状态转换记录 | StateTransition | 领域 · platform.eventing | 聚合每次状态变化的一行追加记录，与状态变更同一事务写入；各有状态聚合所在 schema 各建一张同形表 state_transitions |  | bc 暂取 platform.eventing，待 one-architecture 确认归属 |
| `aggregate_type_code` | 聚合类型代码 | AggregateTypeCode | 领域 · platform.eventing | 状态转换记录里标明所属聚合类型的代码（列 aggregate_type_code） |  |  |
| `occurred_on_date` | 发生日期 | OccurredOnDate | 领域 · platform.eventing | 状态转换记录里状态变化发生的日期（列 occurred_on_date） |  |  |
| `decision_ref_key` | 决定出处键 | DecisionRefKey | 领域 · platform.audit | 配置型版本表里指向用户决定出处的键，填 intent 或 ADR 编号（列 decision_ref_key；裸 decision_ref 为命名守护所禁） |  |  |
| `decision_audit_entry` | 决定审计记录 | DecisionAuditEntry | 领域 · platform.audit | 确认操作同一事务写入的审计记录，作为决定出处，版本行只存其标识、不建跨 schema 外键（列 decision_audit_entry_id） |  | ADR-0072 在提议阶段，随其采纳转正 |

## `step3-objects.yaml`

信息图 ADR-0048 确认页（用户 2026-10-16 选推荐）带来的新用语：one-architecture 交，one-glossary 2026-10-16 查重后登记；争议状态与争议应收是否同义待用户裁定（待决定收件箱）。

无错误码以外的术语。
