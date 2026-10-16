# 提议术语清单（第三轮用语确认用）

<!-- 本文件由 backend/glossary/build_proposed_index.py 从同目录 *.yaml 生成，不要手改；改 yaml 后运行 python3 docs/modernization/build_all.py 并提交结果。 -->

`terms` 只放业务术语，纯技术词（动词、框架词、值对象方法名等）放同文件的 `common_words`。每条必填使用范围 `scope`（domain 领域 / kernel 共享内核 / acl 防腐层）与 `bc`（取 `docs/modernization/glossary/bcs.yaml` 中的 id）：kernel 另填 `used_by`（使用它的 bc），acl 另填 `acl_class`（过渡期 / 长期），指标另填 `formula`、`grain`、`as_of`，下游 bc 自用词可填 `upstream_term`；同一 bc 内中英文名唯一，跨 bc 同名在 `homonyms` 互列；`kind` 只取 DDD 分类（aggregate 聚合 / entity 实体 / value_object 值对象 / domain_event 领域事件 / command 命令 / business_rule 业务规则 / read_model 读模型 / metric 指标 / role 角色 / state 状态，错误码另列 error_code），事件名为聚合名加过去式业务动词、命令用祈使动词，Create / Update / Delete / Data / Info / Manager 等技术泛词不作领域名（规则与校验见 `glossary/term_rules.py`）。本表按文件列出除错误码以外的全部提议术语；「是否疑似方言」由条目中类型为方言或待定的别名与禁用写法生成，空表示未登记问题；「备注」为条目 note 原文。

修改任何一个 yaml 后运行 `./mvnw -B verify`（GlossaryTest 会校验字段、重名和未登记词）与 `python3 docs/modernization/build_all.py`（重新生成本文件）。

## organization.workforce（`architecture-acl.yaml`）

ADR-0039（防腐层边界）引入的术语（proposed，待业务评审，ADR-0018）。登记会话：one-architecture，2026-10-15。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `org_change_set` | 组织变更集 | OrgChangeSet | 领域 · organization.workforce | 集成层翻译成新系统用语后交给组织人事的一批组织单元、员工与任职变更；以同步运行 ID 作幂等键，整批在一个事务内应用 |  |  |
| `apply_org_change_set` | 应用组织变更 | ApplyOrgChangeSet | 领域 · organization.workforce | 组织人事 api 中唯一的同步入口：应用一份组织变更集，管理员离职挂为待放行离职，其余变更照常生效；同一变更集重复调用返回上次结果 |  |  |
| `separation_hold` | 待放行离职 | SeparationHold | 领域 · organization.workforce | 会使可用超级管理员少于 2 名、或涉及超级管理员与安全审计员持有人的离职，被单独挂起等待另一名持有人放行的记录（ADR-0027 第 1 项第 7 条、ADR-0028） |  | 权限码 organization.workforce:separation-hold:approve 已用此词（ADR-0028 第 2 条） |

## `code-aggregates-events.yaml`

涉及模块：`cost.timesheet`、`platform.approval`、`platform.identity`、`platform.integration`。

B2 登记：代码里的聚合根与领域事件各自要有词条（G40、G41）；

登记会话 one-glossary，2026-10-15，中文名待用户确认。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `timesheet_approval_withdrawn` | 工时单已撤回审批 | TimesheetApprovalWithdrawn | 领域 · cost.timesheet | 填报人撤销审批，工时单回到草稿；不记为驳回；以工作日期记业务日 |  |  |
| `approval_task_became_overdue` | 任务已超时 | ApprovalTaskBecameOverdue | 领域 · platform.approval | 任务超过审批时限仍未处理；只提醒，不自动通过，计入时效统计 |  |  |
| `role_deleted` | 角色已废除 | RoleDeleted | 领域 · platform.identity | 一个角色被废除，不再可用 |  | 事件目录称「角色删除」；「删除」属技术泛词，业务说法（废除/撤销）待用户确认 |
| `source_reference_refused` | 来源对照登记已被拒绝 | SourceReferenceRefused | 领域 · platform.integration | 有人试图人工登记只能由同步或导入写入的来源对照，被拒绝，未存储任何对照 |  | 聚合 SourceReferenceRefusal |

## `contract-evolution.yaml`

契约演进与部署排空用语（proposed，ADR-0035，待用语确认，ADR-0018）。

无错误码以外的术语。

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
| `migration_batch` | 迁入批次 | MigrationBatch | ACL（过渡期） · platform.integration | 一次历史数据迁入运行；登记来源系统、对象类型、范围、状态、行数与来源 ID 摘要；批次挂在来源对照与原编号对照上，业务表不带批次列 | 「导入批次」已禁用；「import_batch」已禁用；「ImportBatch」已禁用 | 用户 2026-10-15 定批次术语唯一写法；platform.numbering 的 import_batch 已在 R-1b P3a（#139）改为 migration_batch_id，同一提交加入 forbidden 并删去 platform-integration.yaml 中的重复条目 |
| `migration_reject` | 迁入拒收 | MigrationReject | ACL（过渡期） · platform.integration | 迁入时被拒绝的一条来源行，带批次、来源对象、来源 ID 与原因代码；处置经应用内带权限的操作录入，批次提交前须逐条处置（ADR-0036 第 7 条） |  |  |
| `migration_reject_reason__unmappable_value` | 取值无法映射 | UnmappableValue | ACL（过渡期） · platform.integration | 迁入拒收原因代码：来源取值在映射表中没有对应的自有取值（ADR-0036 第 7 条） |  |  |
| `migration_reject_reason__required_missing` | 必填缺失 | RequiredMissing | ACL（过渡期） · platform.integration | 迁入拒收原因代码：目标必填项在来源中为空（ADR-0036 第 7 条） |  |  |
| `migration_reject_reason__number_conflict` | 编号冲突 | NumberConflict | ACL（过渡期） · platform.integration | 迁入拒收原因代码：原编号与已有编号在（单据类型, 编号）上冲突（ADR-0036 第 7 条） |  |  |
| `migration_reject_reason__original_missing` | 原行缺失 | OriginalMissing | ACL（过渡期） · platform.integration | 迁入拒收原因代码：冲销行的原行不在本批次也不在目标库中（ADR-0036 第 7 条） |  |  |
| `migration_reject_reason__legal_entity_out_of_scope` | 法人不在范围 | LegalEntityOutOfScope | ACL（过渡期） · platform.integration | 迁入拒收原因代码：来源行的法人不在新系统的法人范围内（ADR-0036 第 7 条） |  |  |
| `migration_reject_reason__trial_commit_failed` | 试提交失败 | TrialCommitFailed | ACL（过渡期） · platform.integration | 迁入拒收原因代码：试提交中领域校验或数据库约束不通过（ADR-0036 第 7 条） |  |  |
| `migration_reject_disposition__rerun_after_fix` | 修正后重跑 | RerunAfterFix | ACL（过渡期） · platform.integration | 迁入拒收处置取值：修正映射或来源后由新的暂存重跑（ADR-0036 第 7 条） |  |  |
| `migration_reject_disposition__excluded` | 确认不迁 | Excluded | ACL（过渡期） · platform.integration | 迁入拒收处置取值：确认该行不迁入；权责事实须财务负责人确认，其他由业务管理员确认（ADR-0036 第 7 条） |  |  |
| `migration_tie_out` | 迁入核对 | MigrationTieOut | ACL（过渡期） · platform.integration | 一个迁入批次按期间 × 法人 × 对象类型比对来源侧聚合与暂存的行数、金额合计、来源 ID 摘要与按来源单号的行数金额的结果；零差异或经财务签字后批次才能提交（ADR-0036 第 8 条） |  | 不用 Reconciliation：已批准术语「经营勾稽」Reconciliation 指跨口径对账 |
| `migration_period_reservation` | 迁入占用 | MigrationPeriodReservation | ACL（过渡期） · accounting.period | 迁入批次对 (期间, 法人) 的占用登记，由 accounting.period 自持；有未解除占用的期间不得关账，批次转为提交中时加锁确认期间状态（ADR-0036 第 6 条） |  |  |
| `migration_marker` | 迁入标记 | MigrationMarker | 领域 · operations.facts | 事实行上区分原生与迁入的标记；只在事实行，单据表不带；accounting.ledger 不为迁入标记的事实生成分录（ADR-0036 第 5、6 条） |  | 列名在实现 PR 中按 NAMING_STANDARD 定 |
| `migration_posting_channel` | 迁入过账通道 | MigrationPostingChannel | ACL（过渡期） · operations.facts | operations.facts 中只供迁入用例使用的过账入口：期间可为历史期间或开放期间，不发布事件（ADR-0036 第 6 条） |  |  |
| `migration_staging` | 迁入暂存 | MigrationStaging | ACL（过渡期） · platform.integration | 来源行转换为目标形状后存放的集成层暂存表（Flyway 按对象类型建固定表，带批次列、来源键、摘要与核对所需来源取值）；试提交与核对都针对它，核对通过后才提交目标库（ADR-0036 第 7 条） |  | 暂存只有一份，不另存来源快照 |
| `trial_commit` | 试提交 | TrialCommit | ACL（过渡期） · platform.migration | 用与正式提交同一套代码把整批写入目标库后回滚事务，以跑全部领域校验与数据库约束（ADR-0036 第 7 条） |  |  |
| `migration_batch_status__staging` | 暂存中 | Staging | ACL（过渡期） · platform.integration | 迁入批次状态取值：正在抽取、转换与暂存，可试提交（ADR-0036 第 3 条） |  |  |
| `migration_batch_status__awaiting_tie_out` | 待核对 | AwaitingTieOut | ACL（过渡期） · platform.integration | 迁入批次状态取值：暂存与试提交完成，等待核对通过或签字（ADR-0036 第 3 条） |  |  |
| `migration_batch_status__committing` | 提交中 | Committing | ACL（过渡期） · platform.integration | 迁入批次状态取值：核对已通过，正在分块提交目标库（ADR-0036 第 3 条） |  |  |
| `migration_batch_status__effective` | 已生效 | Effective | 领域 · platform.integration | 迁入批次状态取值：提交后复核一致；可转为已冲销（ADR-0036 第 3 条） |  |  |
| `migration_batch_status__reversed` | 已冲销 | Reversed | ACL（过渡期） · platform.integration | 迁入批次状态取值：提交阶段失败或生效后发现错误，事实行已整批冲销，终态；重跑为新批次（ADR-0036 第 3 条） |  |  |
| `migration_batch_status__abandoned` | 已放弃 | Abandoned | ACL（过渡期） · platform.integration | 迁入批次状态取值：提交前放弃，目标库未受影响，终态（ADR-0036 第 3 条） |  |  |
| `source_reference_status` | 对照状态 | SourceReferenceStatus | ACL（过渡期） · platform.integration | 来源对照行的状态：当前 CURRENT / 已冲销 REVERSED；只有事实类对照可为已冲销，冲销与关闭旧对照同一事务（ADR-0036 第 3、9 条） |  | 与确认状态 confirmation_status 是两列；自然键只约束已确认且当前的行；取值「已冲销」REVERSED 与迁入批次状态的「已冲销」同词同义，不另登记 |
| `source_reference_status__current` | 当前 | Current | ACL（过渡期） · platform.integration | 对照状态取值：该对照仍指向有效的自有行（ADR-0036 第 3 条） |  |  |
| `source_row_digest` | 来源行摘要 | SourceRowDigest | ACL（过渡期） · platform.integration | 来源行规范化后的 SHA-256，存于来源对照与暂存行，用于重跑时判断同一来源行是否变化（ADR-0036 第 3、7 条） |  |  |

另有技术词 2 个（`common_words`），不在表中。

## `enterprise-aggregates.yaml`

涉及模块：`accounting.ledger`、`accounting.tax`、`contract.agreement`、`cost.costing`、`delivery.acceptance`、`delivery.execution`、`masterdata.counterparty`、`organization.leave`、`organization.payroll`、`payables.payment`、`presales.opportunity`、`procurement.subcontracting`、`receivables.collection`、`receivables.wip`。

全企业聚合草案 §8 交 one-glossary 的新用语（预判登记稿，proposed，待业务评审，ADR-0018）。

前提：bcs.yaml 先并入 bcs-additions.yaml 中的 masterdata.*、organization.leave、accounting.tax 等；需要用户裁定的 11 项不在此文件，见 decide-items.json。

聚合所在 bc 暂取现有 bc（presales.quoting、contract.baseline 等），B2-X01 定合并后的 BC 标识后整体迁移。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `counterparty` | 往来单位 | Counterparty | 领域 · masterdata.counterparty | 与我方发生业务往来的法律主体（企业、事业单位或自然人）的主数据：唯一法定标识，合并保留历史；客户、供方、联合体成员、建设单位等是它带准入状态的角色，不是不同的主体。不含我方法人（法人主体，组织人事）和员工。 |  | 与 presales.customer 的 customer（客户）定义同源，处置见确认单 B2-X02；英文 Counterparty 取 IFRS 9 通行写法 |
| `construction_project` | 建设工程 | ConstructionProject | 领域 · masterdata.counterparty | 建设单位的工程项目的外部引用登记（含竣工在用后成为受检对象的工程）：被售前、交付执行、合同、检查共同引用，位于最上游；内含我方参与记录和往来单位角色（建设单位、业主、物业，带起止）。不是我方交付的项目（项目 project）。 |  | 本条不在草案 §8 清单内，因我方参与记录需要其聚合名而一并提议 |
| `own_participation_record` | 我方参与记录 | OwnParticipationRecord | 领域 · masterdata.counterparty | 建设工程内记录我方在该工程中承担的角色及对应的我方项目（项目 ID 由交付登记）；一个工程可有多条，只作回溯线索，不触发校验。 |  |  |
| `tender_project` | 招标项目 | TenderProject | 领域 · masterdata.counterparty | 依《招标投标法》公开或邀请招标的项目登记（招标标识、采购方式、标段），外部引用登记，被投标、合同和建设工程共同引用。草案称「招标项目登记」，登记是动作，名词取「招标项目」。与我方项目（project）不是一回事。 |  | 若架构定为不建聚合（REF，无聚合），kind 改 entity |
| `project_wbs` | 项目 WBS | ProjectWbs | 领域 · delivery.execution | 一个项目一棵的工作分解结构树：节点（wbs_node）分阶段、子项、工作包三类，工作包为末级；节点挂接合同范围条目或已批准变更。wbs_node 是其内的实体，本词是聚合根。 |  | 词表已有 wbs_node（中文名「WBS」，实体）；本条为聚合根，两者是整体与部分，不是同义 |
| `billing_revenue_limit` | 开票与收入额度 | BillingRevenueLimit | 领域 · receivables.wip | 每个协议一个，守住两条累计上限：累计开票不超过付款条件项的可开票额度，累计确认收入不超过履约义务的分摊价格；内含额度行、开票占用和收入确认行。是额度计数的载体，收入确认本身的规则不在这里。 |  | 草案称「履约义务收入台账并入开票与收入额度」；台账是方言，不用 |
| `revenue_recognition_line` | 收入确认行 | RevenueRecognitionLine | 领域 · receivables.wip | 开票与收入额度内的一次收入确认记录：引用证据、证据类型、认可日和视为同意判定；收入期间由认可日派生，请求携带期间的一律拒绝。 |  | 与词表 revenue_recognition（收入确认单，聚合）的关系见确认单 B2-X06 |
| `acceptance_evidence_type_admission` | 客户认可证据类型开放配置 | AcceptanceEvidenceTypeAdmission | 领域 · receivables.wip | 哪些客户认可证据类型已开放为可触发收入确认的版本化配置：每版本一个聚合，带生效日、批准人和依据文件，只追加；「历史迁入」永不开放。 |  | 开放（admit）取 ADR-0047 的说法；配置类聚合，每版本一个、已批准版本不可改 |
| `output_invoice` | 销项发票 | OutputInvoice | 领域 · receivables.wip | 我方向客户开具的增值税发票（蓝字、红字），含平台开具结果与红冲确认；与开票事件（invoice_issued，开票）的关系是：销项发票是对象，开票是它被开具这件事。 |  | 英文取 output tax 的通行写法；若偏好 SalesInvoice 可改，中文名不变 |
| `input_invoice` | 进项发票 | InputInvoice | 领域 · accounting.tax | 我方取得的增值税发票：查验、用途确认、入账占用和进项转出的载体；发票总额由入账占用分次消耗。 |  | bc accounting.tax 须先登记入 bcs.yaml（ADR-0022 第 7 条） |
| `invoice_posting_reservation` | 发票入账占用 | InvoicePostingReservation | 领域 · accounting.tax | 进项发票金额被某笔入账（费用或应付）占用的记录；各笔占用合计不超过发票可入账金额。 |  |  |
| `receivables_collection__cash_application_reservation` | 核销占用 | CashApplicationReservation | 领域 · receivables.collection | 回款分配行对应收款项余额的占用记录：分配行引用它，核销时转为核销记录，各笔占用合计不超过应收余额。 |  | 与应付侧同名，见 homonyms |
| `payables_payment__cash_application_reservation` | 核销占用 | CashApplicationReservation | 领域 · payables.payment | 付款分配行对应付款项余额的占用记录：分配行引用它，核销时转为核销记录，各笔占用合计不超过应付余额。 |  | 与应收侧同名，见 homonyms |
| `supplier_agreement` | 供方协议 | SupplierAgreement | 领域 · procurement.subcontracting | 我方与供方（分包方、采购供应商、出租方、保险人、居间人、劳务派遣单位）签订的协议，含基线、付款条件、质保金条款以及资质与同意证据；与客户侧收入协议（agreement，协议）相对。「供方」取 GB/T 19000 用语。 |  | 词表 agreement_type__lease_contract 等五个供方侧协议类型现登记在 contract.baseline，随本条迁入采购子域，见 PREJUDGE |
| `supplier_agreement_performance_limit` | 供方协议履约额度 | SupplierAgreementPerformanceLimit | 领域 · procurement.subcontracting | 每个供方协议一个，守住「累计已确认履约金额不超过协议金额（含已生效分包变更）」；供方履约确认占用它。是额度计数的载体。 |  |  |
| `subcontract_change` | 分包变更 | SubcontractChange | 领域 · procurement.subcontracting | 对分包协议金额、范围或工期的正式修改（含影响评估）；生效后调整供方协议履约额度。与客户侧变更（change_order）相对，二者不互相取代。 |  |  |
| `subcontract_change_activated` | 分包变更已生效 | SubcontractChangeActivated | 领域 · procurement.subcontracting | 分包变更审批通过并生效；同 BC 内订阅，用于更新供方协议履约额度。 |  |  |
| `leave_balance` | 假期余额 | LeaveBalance | 领域 · organization.leave | 一名员工在一个假期类型、一个年度内的应享、已用和占用（待批请假）三项数量，守住「已用加占用不超过应享」。草案称「假期余额账户」，「账户」与会计账户易混，不用。 |  | bc organization.leave 须先登记入 bcs.yaml |
| `employee_compensation` | 员工薪酬 | EmployeeCompensation | 领域 · organization.payroll | 一名员工的薪酬属性版本（职级、薪档等，维度型版本）与专项附加扣除年度属性，月度薪酬核算以它为输入；含敏感个人信息，按 ADR-0043 登记访问级别。名称取企业会计准则第 9 号「职工薪酬」，员工与词表一致。 | 「人员薪酬档案」方言·已禁用 | 草案称「人员薪酬档案」：「人员」是已知待裁用语，「档案」是老系统表单叫法 |
| `labor_cost_allocation_result` | 人工成本分配结果 | LaborCostAllocationResult | 领域 · cost.costing | 一个会计期间内，把已过账的工时与薪酬事实分配到项目的结果（分配行）；每期间只能有一份有效结果。人工成本称分配，间接费用称分摊（allocation_result）。 |  | 草案称「人工成本分配运行」；与既有 cost_transfer_batch（批次）、allocation_result（结果）同族，故称「结果」，不称「运行单据」 |
| `cash_accrual_account_mapping` | 收付权责科目映射表 | CashAccrualAccountMapping | 领域 · accounting.ledger | 收付实现制科目与权责发生制科目的对应关系（映射行、对账类别）；每版本一个聚合，已生效版本不可改。与核算映射规则（accounting_mapping_rule_set，业务事件到凭证模板）不同：它对应的是科目，不是事件。 |  |  |
| `control_account_authorization` | 控制科目授权表 | ControlAccountAuthorization | 领域 · accounting.ledger | 控制科目允许由哪些事实类型经哪些通道入账的版本化授权（科目 × 事实类型 × 通道）；每版本一个聚合，已生效版本不可改。 |  |  |
| `no_objection_declaration` | 无异议声明 | NoObjectionDeclaration | 领域 · delivery.acceptance | 交付物内由项目经理作出的声明：截至某日未收到发包人的书面异议，附函件检索记录；是视为同意成立的须附证据之一。 |  |  |
| `no_objection_declared` | 无异议已声明 | NoObjectionDeclared | 领域 · delivery.acceptance | 项目经理作出无异议声明；开票与收入侧订阅，用于视为同意的判定。 |  |  |
| `design_change_claim_status` | 设计变更主张状态 | DesignChangeClaimStatus | 领域 · delivery.acceptance | 设计变更是否向对方主张：待主张，或不主张（附理由）；是否已主张由合同侧的变更、索赔记录，本 BC 不引用它们。草案称「主张标记」，标记是实现用语，业务上是状态。 |  |  |
| `deemed_acceptance` | 视为同意 | DeemedAcceptance | 领域 · delivery.acceptance | 提交轮次送达后审查期满而发包人无结论也无异议，按合同约定视为发包人同意的状态；以送达记录、合同条款引用和期满记录成立，认可日取期满日。不同于发包人明确出具的书面认可。 |  |  |
| `acceptance_date` | 认可日 | AcceptanceDate | 领域 · delivery.acceptance | 客户认可成果的日期，按证据类型取不同的日（签字日、签收日、邮件进入我方邮箱之日、纪要发出日、期满日等），决定收入期间。；认可是客户方接受成果这一事实，由八类证据证明（含邮件、纪要、视为同意），区别于验收（验收单上的裁定行为：通过、退回、有条件通过）；「交付物已认可」是收入证据，「验收通过」是质量裁定（GL2 B2-X08） |  | 「认可」与词表「验收」的关系见确认单 B2-X08 |
| `review_period` | 审查期 | ReviewPeriod | 领域 · delivery.acceptance | 合同约定发包人审查设计文件的期限（天），期满无结论也无异议视为同意；不同意须书面说明，修改后重新报送、审查期重新起算。 |  |  |
| `expiry_record` | 期满记录 | ExpiryRecord | 领域 · delivery.acceptance | 审查期届满的记录（期满日、计算所依参数），是视为同意的须附证据之一；期满日的判定在开票与收入侧（用户已定 D-8）。 |  |  |
| `acceptance_evidence_type` | 客户认可证据类型 | AcceptanceEvidenceType | 领域 · delivery.acceptance | 客户认可成果的八类证据（书面认可文件、两类签收单、邮件确认、两类会议纪要或签认单、检查服务签认或报告签发、视为同意）加「历史迁入」；逐类开放，缺须附证据即拒绝。 |  | bc 随证据（OBJ-53）归属的 Q29 定；暂放 delivery.acceptance |
| `counterparty_bank_account_changed` | 往来单位银行账户已变更 | CounterpartyBankAccountChanged | 领域 · masterdata.counterparty | 往来单位的收款银行账户变更；应付与付款侧订阅，用于拦截向旧账户付款。草案称「银行账户已变更」，与我方资金账户（银行与现金）相混，故加「往来单位」。 |  |  |
| `bid` | 投标 | Bid | 领域 · presales.opportunity | 一次投标的全过程：招标项目与标段、参与方式、联合体协议、资格预审、投标报价、承诺快照、撤回与撤销。不是商机（opportunity）：一个商机可对应一次或多次投标。 |  |  |
| `bid_won` | 投标已中标 | BidWon | 领域 · presales.opportunity | 投标收到中标通知；同 BC 内转为商机赢单。 |  |  |
| `bid_lost` | 投标未中标 | BidLost | 领域 · presales.opportunity | 投标确认未中标；应收侧订阅，用于退还投标保证金。 |  |  |
| `opportunity_lost` | 商机已输单 | OpportunityLost | 领域 · presales.opportunity | 商机以未赢结束，记录原因、竞争对手、中标单位和中标价；成本核算侧订阅，用于售前投入转费用。与词表「赢单」相对；「丢单」是口语，列为别名。 |  |  |
| `opportunity_abandoned` | 商机已放弃 | OpportunityAbandoned | 领域 · presales.opportunity | 我方主动放弃商机（不参与或中途退出）；成本核算侧订阅，用于售前投入转费用。与输单不同：输单是对手赢，放弃是我方不做。 |  |  |
| `agreement_terminated` | 协议已终止 | AgreementTerminated | 领域 · contract.agreement | 协议提前终止；成本核算与开票收入侧订阅，用于终止清算和停止开票。与「到期」不同，到期不是终止。 |  |  |
| `agreement_baseline_activated` | 协议新基线已生效 | AgreementBaselineActivated | 领域 · contract.agreement | 协议的新基线版本生效（变更批准后）；开票收入侧订阅，用于更新付款条件项与履约义务。 |  |  |
| `agreement_project_linked` | 协议项目关联已登记 | AgreementProjectLinked | 领域 · contract.agreement | 协议与项目的关联（只带生效期）已登记；成本核算侧订阅，用于确定项目归属的协议。 |  |  |
| `project_established` | 项目已建立 | ProjectEstablished | 领域 · delivery.execution | 项目建立（立项）。「立项」是老系统表单叫法，事件取「建立」；成本核算侧订阅，用于售前投入转入。 |  |  |
| `project_closed` | 项目已结项 | ProjectClosed | 领域 · delivery.execution | 项目完成收尾检查后结项；成本核算侧订阅，用于结项清算。与竣工结算（final_account）不同：结算是与客户对账，结项是我方内部收尾。 |  |  |
| `project_terminated` | 项目已终止 | ProjectTerminated | 领域 · delivery.execution | 项目在完成前被终止（有成本发生）；成本核算侧订阅，用于终止清算。 |  |  |
| `project_cancelled` | 项目已取消 | ProjectCancelled | 领域 · delivery.execution | 项目在开工前被取消（无成本发生）；与终止的区别在是否已有成本。 |  |  |

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

另有错误码 28 个（kind 为 `error_code`）、技术词 18 个（`common_words`），不在表中。

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

## kernel（`kernel.yaml`）

`tail_adjustment`（尾调）、`rounding_residual`（舍入差）、`prorate`（按权重拆分）由用户 2026-10-15 XD-07「同意 (Recommended)」确认登记（`docs/modernization/CROSS_CUTTING_DECISIONS.md`）。`reversal_document`、`correction_document`、`document_nature`、`reversal_category`、`reversible_balance`、`derived_reversal`、`original_period`、`change_order_document`、`reverse_action`、`correct_action` 十条是 ADR-0031 第 8 项的提议，待用语确认（U-4）。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `system_actor` | 系统操作主体 | SystemActor | 共享内核 · kernel | 以 SYSTEM 身份发出事件的作业、监听器或运维入口；名字人为声明在 backend/architecture/system-actors.txt，事件信封 actorId 为该名字在固定命名空间下的 UUIDv5，同一名字恒定、不同于任何用户 ID，任何实例都可由目录反查名字（RULING-P2-C5） |  | RULING-P2-C5 已裁定：声明完整的静态目录，改名新增一行、旧行不删 |
| `messaging_account` | 即时通讯账号 | MessagingAccount | 共享内核 · kernel | 员工在企业即时通讯平台上的账号，作为外部主体的一种；kernel 端口 ExternalSubjectEmployees 只以中性文本接收它的标识，平台取值只在集成层翻译（RULING-P3B-9） |  | one-glossary 2026-10-15 定名 withMessagingAccountId / withExternalLoginName；临时端口，C1 落地后按身份模型删除或改形 |
| `external_login_name` | 外部登录名 | ExternalLoginName | 共享内核 · kernel | 员工在外部身份提供方的登录名，作为外部主体的一种；与新系统自身账号的登录无关 |  | one-glossary 2026-10-15 定名；临时端口 ExternalSubjectEmployees 的查询值，C1 落地后按身份模型删除或改形 |

另有技术词 38 个（`common_words`），不在表中。

## `list-paging.yaml`

K-3a 列表分页公共契约新增的纯技术词（proposed，NAMING_STANDARD A-3、A-4）。

无错误码以外的术语。

另有技术词 6 个（`common_words`），不在表中。

## organization.attendance（`organization_attendance.yaml`）

organization.attendance 提议术语（proposed，待业务评审，ADR-0018）。

无错误码以外的术语。

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
| `workforce__shrink_guard` | 缩水保护 | ShrinkGuard | ACL（过渡期） · organization.workforce | A8 返回的有效部门数或在职人数降幅超过 20 % 时中止同步，等管理员人工放行 |  |  |
| `workforce__held_run` | 被中止的同步 | HeldRun | ACL（过渡期） · organization.workforce | 被缩水保护中止、等待管理员放行的同步运行 |  |  |
| `workforce__isolated_row` | 被隔离的行 | IsolatedRow | ACL（过渡期） · organization.workforce | 缺部门或缺岗位的人员行：不写入、告警，不中断整批同步 |  |  |
| `workforce__broken_org_tree` | 断裂的组织树 | BrokenOrgTree | ACL（过渡期） · organization.workforce | A8 返回的部门缺少上级路径；整次同步中止，什么都不写 |  |  |
| `workforce__login_name` | 登录名 | LoginName | ACL（过渡期） · platform.integration | 员工在组织源的登录名（手机号）；没有即离职（RULE-363） |  |  |
| `workforce__staff_employee` | 员工任职快照 | StaffEmployee | ACL（过渡期） · organization.workforce | 一名员工连同其任职，是同步比较与写入的单位 |  |  |
| `workforce__relationship` | 关系 | Relationship | ACL（过渡期） · platform.integration | A8 中人员与组织单元之间的关系（部门负责人、区域应收负责人），类型 ID 为配置项 |  |  |
| `separation_observed_at` | 离职观察时间 | SeparationObservedAt | ACL（过渡期） · platform.integration | 组织同步第一次把员工判为离职、并写入快照的那一轮同步的提交时刻；只表示新系统这样判定了，不是人事生效日 | 「离职日期」已禁用；「separation_date」已禁用 | 单说离职日期有歧义，须写明预计离职日期或离职观察时间 |
| `attribution_subject` | 法人归属对象 | AttributionSubject | 领域 · organization.workforce | 法人归属所指的对象，是一个组织单元或一名员工，由对象类别与对象 ID 共同确定；同一对象在同一时点只归属一个法人 |  | 类型 AttributionSubjectId 为它的标识（kind + subjectId，events/workforce.yaml x-type）；落库按 DA-5 拆为 org_unit_id / employee_id 两列 |

另有错误码 10 个（kind 为 `error_code`）、技术词 86 个（`common_words`），不在表中。

## platform.approval（`platform-approval.yaml`）

platform.approval 提议术语（proposed，待业务评审，ADR-0018）。单词级术语：服务于代码标识、URL、事件键、错误码的命名守护。

无错误码以外的术语。

另有错误码 29 个（kind 为 `error_code`）、技术词 111 个（`common_words`），不在表中。

## platform.identity（`platform-identity.yaml`）

platform.identity 新增术语（proposed，待业务评审，ADR-0018）。登录、会话、票据、角色与数据范围用到的词。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `signing_key_code` | 签名密钥编码 | SigningKeyCode | ACL（长期） · platform.identity | 票据签发方签名密钥的编码（票据头部的 kid），一方一把；原列名 key_id 按 ADR-0033 影响面改为以 _code 结尾（R-1b P2） |  |  |
| `ticket` | 票据 | Ticket | ACL（长期） · platform.identity | 一次性登录凭据：致远 OA 票据，或外部系统的签名票据 |  |  |
| `ticket_issuer` | 票据签发方 | TicketIssuer | ACL（长期） · platform.identity | 被允许签发签名票据的外部系统（KM、组织门户、YD 门户、BI），每方一把密钥，默认关闭 |  |  |
| `external_identity` | 外部身份 | ExternalIdentity | ACL（长期） · platform.identity | 用户账号在某个身份提供方（企业微信、致远登录）中的标识与该账号的绑定，带绑定区间；登录只经此绑定解析用户 |  | ADR-0033（提议）登记，待用语确认 |
| `provider_kind` | 身份提供方类别 | ProviderKind | ACL（长期） · platform.identity | 外部身份所属的身份提供方类别，如企业微信用户 ID、致远登录名 |  | ADR-0033（提议）登记，待用语确认 |
| `external_subject` | 外部主体标识 | ExternalSubject | ACL（长期） · platform.identity | 身份提供方给出的用户标识文本（企业微信 userid、致远登录名），只存于外部身份绑定 |  | ADR-0033（提议）登记，待用语确认 |
| `single_operator_mode` | 单人运维模式 | SingleOperatorMode | 领域 · platform.identity | 仅 dev 环境、仅在进程启动时按配置读定的运行方式：运维命令可省略见证人，应急账号与超级管理员下限按 1 计，并可经运维命令起草无作者的编号模板版本；放宽项封闭为单人运维放宽项的取值，每次放宽都写标注审计（ADR-0030） |  | 登记会话 one-1a-par（派工卡 SINGLE-OP）；配置键 forcitis.platform.identity.single-operator-mode；查重：terms.yaml 与 proposed 中同一 bc 无同名、同中文名 |
| `single_operator_relaxation` | 单人运维放宽项 | SingleOperatorRelaxation | 领域 · platform.identity | 单人运维模式下被放宽的规则，封闭为四个取值：见证人省略、应急账号下限一个、超级管理员下限一个（只改同步暂停原因文本）、模板版本无作者；写入标注审计的 detail；新增取值须另立 ADR（ADR-0030 第 2 项） |  | 登记会话 one-1a-par（派工卡 SINGLE-OP）；四个取值的标识符与中文名由派工卡第 11 节列出，实现 PR 前由 one-glossary 确认 |
| `super_admin` | 超级管理员 | SuperAdmin | 领域 · platform.identity | 持有全部平台管理权限的内置角色的角色码；单人运维放宽项「超级管理员下限一个」的取值名由它构成（ADR-0027、ADR-0030） |  | 登记会话 one-1a-par（派工卡 SINGLE-OP P2）；G37 要求枚举取值 SUPER_ADMIN_MINIMUM_ONE 的词；查重：terms.yaml 与 proposed 中同一 bc 无同名、同中文名 |

另有错误码 3 个（kind 为 `error_code`）、技术词 108 个（`common_words`），不在表中。

## platform.integration（`platform-integration.yaml`）

platform.integration 提议术语（proposed，待业务评审，ADR-0018）。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `outbound_delivery` | 外发投递 | OutboundDelivery | ACL（长期） · platform.integration | 一条待发往外部系统（企业微信）的消息及其发送结果；事务提交后由投递作业发送，失败退避重试，次数用尽进死信 |  |  |
| `recipient` | 收件人 | Recipient | ACL（长期） · platform.integration | 消息的接收人，用企业微信用户 ID 精确指定；不允许按部门、标签或全员投递 |  |  |
| `source_reference` | 来源对照 | SourceReference | ACL（过渡期） · platform.integration | 外部系统或老系统中一个对象的标识与 FORCITIS one 自有 ID 的对应；只在集成层，领域只用自有 ID；同一来源系统、同一类别下来源 ID 与自有 ID 在任一时点各自唯一 |  | ADR-0033 登记；权限码 platform.integration:source-reference:update（授予组织数据管理员）、platform.integration:source-reference:approve（核算组织编码四眼确认，授予业务管理员），授予方式均为 INDIVIDUAL |
| `source_system` | 来源系统 | SourceSystem | ACL（过渡期） · platform.integration | 来源对照所属的外部系统，取值为组织源（ORG_SOURCE）、老系统（LEGACY_SYSTEM）、核算系统（ACCOUNTING_SYSTEM），只在集成层与迁入登记中出现 |  | 数据库列叫 source_system，Java 枚举叫 ExternalSource（platform.integration.api），因为 kernel.event.SourceSystem（ONE / MIGRATION）表示事件出自新系统还是历史迁入，是另一回事，不改名 |
| `source_object_kind` | 来源对象类别 | SourceObjectKind | ACL（过渡期） · platform.integration | 来源对照所指的自有对象类别（员工、组织单元、岗位、法人主体等），决定自有 ID 指向哪类对象 |  | ADR-0033（提议）登记，待用语确认 |
| `rejected_state` | 已拒绝 | RejectedState | ACL（长期） · platform.integration | 来源对照的确认状态之一：登记的对照被另一个人拒绝，不占自然键约束，不用于出站 |  | 展示用语；对应确认状态 REJECTED。与审批的「驳回」（Reject，platform.approval，审批人对任务给出不同意的结论）不是一回事：这里是另一个人不接受一条登记的来源对照，中文写「已拒绝」，英文命令写 Reject 时不得显示成「驳回」。确认状态的写法：编号规则版本为 PENDING_CONFIRMATION / CONFIRMED，来源对照为 PENDING / CONFIRMED / REJECTED，中文都是待确认 / 已确认 / 已拒绝（ADR-0033 第 2 项第 1 条） |
| `registration` | 登记 | Registration | ACL（长期） · platform.integration | 由人把一条来源对照录入系统，先处于待确认，等另一个人确认或拒绝；现在只有核算组织编码可以人工登记 |  |  |
| `supersede` | 取代 | Supersede | ACL（长期） · platform.integration | 已确认的后继对照从生效日起取代生效中的对照：旧对照在后继的生效日结束，两行的来源标识与自有 ID 都不改写 |  |  |
| `evidence` | 取证 | Evidence | 领域 · platform.integration | 只读核对老系统与 A8 生产数据，用于复刻老行为、迁入对账与新旧等价核对；用专用取证账号，只出计数或聚合，或落 ECS 数据盘，每次登记一行 |  | ADR-0037 登记（数据库登录名 one_evidence_ro），待用语确认 |
| `read_only_verification` | 只读核实 | ReadOnlyVerification | ACL（过渡期） · platform.integration | 运维命令：对一条只读外部连接的登录跑与启动校验同一套判定，逐项（ADR-0037 第 4 项 (a)–(g)）输出核实通过、确认违规或判定不了，判定不了时只带 SQL 错误号与 SQLState，按结果给退出码；不输出数据、消息原文与凭据，不监听端口、不迁移、不起作业，口令只从环境读 |  | PAR-2 验收测试登记（档案名 read-only-verification），待用语确认 |
| `read_only_verification_result` | 只读核实结果 | ReadOnlyVerificationResult | ACL（过渡期） · platform.integration | 只读核实对每一项与对整条连接给出的结论，取核实通过、确认违规、判定不了之一；整条连接有任一项确认违规即为确认违规，否则有判定不了即为判定不了 |  | PAR-2 验收测试登记，待用语确认 |
| `read_only_verification_result__passed` | 核实通过 | Passed | ACL（过渡期） · platform.integration | 只读核实结果的取值：该项（或全部各项）与 RDS 平台基线一致 |  | 不写「通过」：已是审批结论 approve 的中文名 |
| `read_only_verification_result__violated` | 确认违规 | Violated | ACL（过渡期） · platform.integration | 只读核实结果的取值：数据库目录确实显示该项超出或缺少基线（与「判定不了」相对） |  | PAR-2 验收测试登记，待用语确认 |
| `read_only_verification_result__undetermined` | 判定不了 | Undetermined | ACL（过渡期） · platform.integration | 只读核实结果的取值：查询出错、超时、连不上或返回为空，该项无法判定；启动时同样拒绝启动（ADR-0037 第 4 项第 2 条） |  | PAR-2 验收测试登记，待用语确认 |
| `overridden_grant` | 被拒绝覆盖的授权 | OverriddenGrant | ACL（过渡期） · platform.integration | 已授予、但被同级或更高层级的 DENY 覆盖、实际不生效的权限；只读核实按有效权限判定时不算违规，只作提示。 |  | one-glossary 术语门禁结论（one-coordinator 2026-10-15 转达）；只读核实命令定位行种类 overridden，by 写被哪条 DENY 覆盖（DATABASE_DENY / SCHEMA_DENY） |

另有错误码 7 个（kind 为 `error_code`）、技术词 25 个（`common_words`），不在表中。

## platform.numbering（`platform-numbering.yaml`）

platform.numbering 提议术语（proposed，待业务评审，ADR-0018）。服务于原编号、确认状态、单据类型代码的命名守护。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `qt` | 报价单据类型代码 | Qt | 领域 · platform.numbering | 报价（DocumentType.QUOTE）的单据类型代码 QT，1b 首个使用方 |  | 例 QT-FST-202610-0001 |
| `ts` | 工时单据类型代码 | Ts | 领域 · platform.numbering | 工时（DocumentType.TIMESHEET）的单据类型代码 TS，沿用种子模板中的前缀字母，正式词表尚无对应词 |  | 例 TS-FST-202610-0001；模板待管理员确认 |
| `jv` | 记账凭证单据类型代码 | Jv | 领域 · platform.numbering | 记账凭证（DocumentType.JOURNAL_VOUCHER）的单据类型代码 JV，沿用种子模板中的前缀字母，正式词表尚无对应词 |  | 例 JV-FST-202610-0001；模板待管理员确认 |

另有错误码 5 个（kind 为 `error_code`）、技术词 3 个（`common_words`），不在表中。

## platform.web（`platform-web.yaml`）

无错误码以外的术语。

另有错误码 5 个（kind 为 `error_code`）、技术词 13 个（`common_words`），不在表中。

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

## `step3-objects.yaml`

涉及模块：`delivery.acceptance`、`organization.workforce`、`receivables.collection`。

信息图 ADR-0048 确认页（用户 2026-10-16 选推荐）带来的新用语：one-architecture 交，one-glossary 2026-10-16 查重后登记；争议状态与争议应收是否同义待用户裁定（待决定收件箱）。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `bill_redemption_status` | 兑付状态 | BillRedemptionStatus | 领域 · receivables.collection | 商业承兑汇票从收到到兑付的状态：收到 → 已兑付 / 逾期；到期日到而未兑付为逾期 |  | OBJ-35 之外：商票作回款方式、加到期日与兑付状态，不单独成对象 |
| `bill_redemption_status__received` | 收到 | Received | 领域 · receivables.collection | 商业承兑汇票已收到，尚未到期兑付 |  |  |
| `bill_redemption_status__redeemed` | 已兑付 | Redeemed | 领域 · receivables.collection | 商业承兑汇票到期已兑付，计现金流入（FD-40） |  |  |
| `bill_redemption_status__overdue` | 已逾期 | BillOverdue | 领域 · receivables.collection | 商业承兑汇票到期日已过而未兑付；与应收账款的逾期（逾期应收）、审批任务的已超时不同 |  |  |
| `receivable_disposition_category` | 往来款项处置分类 | ReceivableDispositionCategory | 领域 · receivables.collection | 应收、应付往来款项出现争议或无法正常收付时的处置分类，诉讼是其中一类；其余取值未定（信息图候选：复工、结算、诉讼、核销，待用户确认页定） |  | 信息图候选取值：复工、结算、诉讼、核销，待用户确认页定，目前只登记诉讼 |
| `litigation` | 诉讼 | Litigation | 领域 · receivables.collection | 往来款项处置分类之一：已进入诉讼或仲裁程序 |  |  |
| `continuing_education_hours` | 继续教育学时 | ContinuingEducationHours | 领域 · organization.workforce | 注册执业证书的属性：持证人在注册有效期内累计完成的继续教育学时，分必修与选修；达标是延续注册的条件 |  | 依据已由 one-architecture 核对原文（2026-10-16） |
| `drawing_review_opinion` | 施工图审查意见 | DrawingReviewOpinion | 领域 · delivery.acceptance | 施工图审查机构对施工图出具的审查意见，作证据类型 |  | 随证据（Q29）暂不定的部分一并确认 |
| `drawing_review_certificate` | 施工图审查合格书 | DrawingReviewCertificate | 领域 · delivery.acceptance | 施工图审查通过后出具的合格书，作证据类型 |  |  |
