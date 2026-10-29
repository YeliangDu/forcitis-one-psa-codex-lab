# NAM-1017-GLO-10：交 N7 的 C 类 48 项问题、倾向与依据

日期：2026-10-17。Codex 起草；本卡首轮，承接 NAM-1017-GLO-07 第2轮的 C 类清单。**这是待审命名与处置建议，不是业务、会计或法律批准；不写回词表、不改变运行行为。**

原编号、原 id、原 bc 全部保留，用于对应原 C3、B 改归 C44、宽口径“证据”英文待确认1。建议的新名字和退役用途不改概念 id。逐项对账、原提议文件、出处能证明什么及剩余缺口见 [C_CLASS_BASIS.md](c-class-basis.md)。

“有倾向”指本卡推荐某个名字或处理；“无倾向”不设推荐或预选。类型是 N7 审阅标签，不替代词表 kind／bc；技术含义标签只指本题的旧用途处置或英文名，不把人事生效、在职或安全规则改由技术方批准。会计命名按用户已定 NM2-20 由用户直接确定，不再转财务定名（[原话](../../decisions/2026-10-17-105923-one-glossary-NM2-决策页提交-NM2-20.md)）；收入政策书面确认等业务职责仍沿既有规则，不能随命名免除。

题面只补问尚缺的名称与处理；已定中文、业务范围及后续取代关系均作为前提，不重问。特别是已采纳 ADR-0055 已撤日常外部组织同步，第4、41–46项按用途退出或历史追溯处理，不恢复旧设计。依据中的“聚合草稿”“N4候选”“老行为证据”均未冒称用户批准；原始答复中的 defaulted:true 只表示按推荐预选提交，不能推定逐题主动点选。

## 逐项题面

### 1. 招标项目

- **原 id / bc**：`tender_project` / `masterdata.counterparty`。
- **类型**：业务含义。
- **白话问题**：外部招标的一项采购是否叫“招标项目”，单独记招标编号和标段（分别报价、评标的采购部分），并与我方承接的项目分开？
- **倾向：有倾向**。推荐保留“招标项目 / TenderProject”，作为外部引用登记。理由：同一招标及标段需要被多次业务引用，不能拿我方项目代替。
- **依据**：[2026-10-15-205406-one-architecture-售前信息图确认页保存-合同与项目.md](../../decisions/2026-10-15-205406-one-architecture-售前信息图确认页保存-合同与项目.md) 原话“所有竞争性采购建投标”；[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §1.2 INV-28；§2.2 REF-01、AGG-11。完整边界见 [C01](c-class-basis.md#c01)。
- **重名／近义处理**：与已登记 `bid`（投标）、`project`（项目）、`construction_project`（建设工程）分立：分别是参投过程、我方交付、外部工程，均不合并。

### 2. 销项发票

- **原 id / bc**：`output_invoice` / `receivables.wip`。
- **类型**：会计用语。
- **白话问题**：我方向客户开出的增值税发票是否叫“销项发票”，保留正常开具和红字更正（另开红字发票纠正原票）的记录？
- **倾向：有倾向**。推荐“销项发票 / OutputInvoice”独立保存发票记录。理由：发票有票号、金额和更正关系，“开票”只描述一次已发生的动作。
- **依据**：[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §1.2 INV-72、73；§2.2 AGG-46；[NM2 已提交答复](../../decisions/decision-pages/answers/nm2.json) NM2-17；[NM2 题面](../../decisions/decision-pages/sheets/nm2.json) NM2-17 完整动作名。完整边界见 [C02](c-class-basis.md#c02)。
- **重名／近义处理**：与已登记 `invoice_issued`（开票事件）、`billing_request`（开票申请）分立；与 `input_invoice`（我方收到的进项发票）按方向分立。

### 3. 供方协议

- **原 id / bc**：`supplier_agreement` / `procurement.subcontracting`。
- **类型**：业务含义。
- **白话问题**：我方与分包（把部分服务交给别的单位做）、采购、租赁等服务或货物提供方签的协议，是否统称“供方协议”，再按交易类型区分条款和变更？
- **倾向：有倾向**。推荐共用“供方协议 / SupplierAgreement”总称，保留各协议类型。理由：共用总称便于查找，按类型命名可避免把采购和租赁误叫分包。
- **依据**：[2026-10-16-061232-one-architecture-阶段类型与-BC-划分确认页提交.md](../../decisions/2026-10-16-061232-one-architecture-阶段类型与-BC-划分确认页提交.md) D-1：收入侧、供方侧、劳动合同分开；[NM3 已提交答复](../../decisions/decision-pages/answers/nm3.json) NM3-11；[NM3 题面](../../decisions/decision-pages/sheets/nm3.json) NM3-11：采购、租赁、分包分别称变更；[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §2.2 AGG-55。完整边界见 [C03](c-class-basis.md#c03)。
- **重名／近义处理**：与已登记 `agreement`（协议总称）作上下位关系；与 `subcontract_change`、`purchase_modification`、`lease_modification` 分立，不合并成一种分包变更。

### 4. 应用组织变更

- **原 id / bc**：`apply_org_change_set` / `organization.workforce`。
- **类型**：技术含义。
- **白话问题**：投产后既由本系统直接登记人事变更，“应用组织变更”这个接收外部同步结果的入口是否退出使用？
- **倾向：有倾向**。推荐按原同步用途退役“应用组织变更 / ApplyOrgChangeSet”，人事操作使用各自明确的命令。理由：已采纳路线撤销外部组织同步，继续保留该入口会混淆数据来源。
- **依据**：[ADR-0055](../../adr/ADR-0055.md) 决定2、3.3、6；[ADR-0039](../../adr/ADR-0039.md) 决定2.1–3（原方案，已被部分取代）。完整边界见 [C04](c-class-basis.md#c04)。
- **重名／近义处理**：与已登记 `org_change_set`（组织变更集）、`sync_run`（同步记录）分立：一个是入口、另两个是输入及运行记录；一起核查用途退出，不合并成人事业务命令。

### 5. 待放行离职

- **原 id / bc**：`separation_hold` / `organization.workforce`。
- **类型**：业务含义。
- **白话问题**：管理员离职可能让系统无人管理时，是否只暂停这一个人的离职，核对放行后再收回权限，其余人事变更继续办理？
- **倾向：有倾向**。推荐保留“待放行离职 / SeparationHold”，承接本地离职命令的逐人保护。理由：安全保护针对一个人的离职，不应停住其他人的人事办理。
- **依据**：[ADR-0055](../../adr/ADR-0055.md) 决定3.2–4、6；[ADR-0028](../../adr/ADR-0028.md) 决定4、6（保留的放行与留任约束）。完整边界见 [C05](c-class-basis.md#c05)。
- **重名／近义处理**：与已登记 `separation`（离职）、`separated`（已离职）分立；与 `workforce__held_run`（被中止的同步）分立并按旧用途退出，不能把等待放行视为离职已经生效。

### 6. 个人信息

- **原 id / bc**：`personal_information` / `platform.audit`。
- **类型**：业务含义。
- **白话问题**：“个人信息”是否继续指能识别某个人或与其有关的资料，彻底匿名、不能再认出个人的资料才不算？
- **倾向：有倾向**。推荐保留“个人信息 / PersonalInformation”及 PI 标记。理由：仓库已采纳定义区分匿名化与仅去掉姓名，避免误把还能关联个人的资料当成无关资料。
- **依据**：[ADR-0043](../../adr/ADR-0043.md) 决定1.1–3、4.2：本地转述的个人信息范围。完整边界见 [C06](c-class-basis.md#c06)。
- **重名／近义处理**：与已登记 `employee_profile`（员工概况）分立：一个是资料分类，另一个是资料载体；也与访问级别 SENSITIVE 分立。

### 7. 敏感个人信息

- **原 id / bc**：`sensitive_personal_information` / `platform.audit`。
- **类型**：业务含义。
- **白话问题**：“敏感个人信息”是否指泄露或被乱用后容易伤害个人尊严、人身或财产安全的资料，并与一般个人信息分开标记？
- **倾向：有倾向**。推荐保留“敏感个人信息 / SensitivePersonalInformation”，标记采用 SENSITIVE_PI。理由：既保留仓库所列的法律分类含义，也避免 SPI 与进度绩效指数重名。
- **依据**：[ADR-0043](../../adr/ADR-0043.md) 决定1.2–3、2（旧标记 SPI）；[已登记词表（按 id 核对）](../terms.yaml) id=schedule_performance_index。完整边界见 [C07](c-class-basis.md#c07)。
- **重名／近义处理**：是第6项个人信息的子类，分立登记；与已登记 `schedule_performance_index` 的 SPI 仅缩写撞名，分立并取消隐私标记的 SPI 候选。

### 8. 迁入占用

- **原 id / bc**：`migration_period_reservation` / `accounting.period`。
- **类型**：会计用语。
- **白话问题**：“迁入占用”是否只在批次正式写入期间内阻止关账（结束该月记账），暂存和核对阶段仍可照常关账？
- **倾向：有倾向**。推荐保留“迁入占用 / MigrationPeriodReservation”，只有“提交中”挡关账。理由：这是已采纳 ADR 的明确条件，可避免暂存中的批次长期占住会计月份。
- **依据**：[ADR-0036](../../adr/ADR-0036.md) 决定6“已登记的占用不挡关账”及关账前置检查；[ADR-0054](../../adr/ADR-0054.md) 决定3.2：保留暂存、试提交、核对等机制。完整边界见 [C08](c-class-basis.md#c08)。
- **重名／近义处理**：与已登记 `migration_batch`（迁入批次）分立；与额度占用也分立，此处占的是期间的处理许可，不是金额。

### 9. 迁入标记

- **原 id / bc**：`migration_marker` / `operations.facts`。
- **类型**：会计用语。
- **白话问题**：“迁入标记”是否只说明这笔经营事实来自历史导入，并让总账不再为它重复生成会计分录（记账所用的借贷明细）？
- **倾向：有倾向**。推荐保留“迁入标记 / MigrationMarker”，只放在事实行，迁入事实不补生成分录。理由：迁入历史事实与总账期初分别承接，重复补分录会改变账面金额。
- **依据**：[ADR-0036](../../adr/ADR-0036.md) 决定4、5（总账不为迁入事实生成分录）、6；[ADR-0054](../../adr/ADR-0054.md) 决定3.2：历史事实入交易库，总账只迁期初。完整边界见 [C09](c-class-basis.md#c09)。
- **重名／近义处理**：与已登记 `business_fact`（经营事实）、`source_reference`（来源对照）、`migration_batch` 分立：标记只分原生／迁入，详细追溯用后两者。

### 10. 迁入过账通道

- **原 id / bc**：`migration_posting_channel` / `operations.facts`。
- **类型**：会计用语。
- **白话问题**：“迁入过账通道”是否只让迁入批次把历史事实正式入账，接受历史或开放月份，但不发送业务事件来重复启动其他处理？
- **倾向：有倾向**。推荐保留专用“迁入过账通道 / MigrationPostingChannel”。理由：专用入口保留平衡和重复校验，同时容纳历史月份和无副作用要求。
- **依据**：[ADR-0036](../../adr/ADR-0036.md) 决定1（迁入入口只能由编排调用）、5、6；[NM2 已提交答复](../../decisions/decision-pages/answers/nm2.json) NM2-19：区分记账与过账。完整边界见 [C10](c-class-basis.md#c10)。
- **重名／近义处理**：与已登记 `business_fact` 的正常过账入口分立用途，与第9项迁入标记分立；不另建一套经营事实。

### 11. 我方参与记录

- **原 id / bc**：`own_participation_record` / `masterdata.counterparty`。
- **类型**：业务含义。
- **白话问题**：同一个建设工程中，我方先后做过什么、对应哪些我方项目，是否留作“我方参与记录”，允许关联多个项目供回查？
- **倾向：有倾向**。推荐保留“我方参与记录 / OwnParticipationRecord”，允许多条且只作回查关联。理由：建设工程可跨业主和多年延续，一个我方项目不能代替工程的全部参与历史。
- **依据**：[2026-10-15-234418-one-architecture-答复复核-R1–R5-提交-已提.md](../../decisions/2026-10-15-234418-one-architecture-答复复核-R1–R5-提交-已提.md) R5：建设工程为一个对象，跨业主延续；[已登记词表（按 id 核对）](../terms.yaml) id=construction_project：内含我方参与记录；[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §2.2 AGG-33：项目ID由交付登记、我方项目“不校验”。完整边界见 [C11](c-class-basis.md#c11)。
- **重名／近义处理**：与已登记 `construction_project`、`project` 分立，作为两者之间的参与记录；与 `project_team_member`（项目团队成员）分立，后者记录人员。

### 12. 项目 WBS

- **原 id / bc**：`project_wbs` / `delivery.execution`。
- **类型**：业务含义。
- **白话问题**：是否把“项目 WBS”完整叫“项目工作分解结构”，即把项目按阶段、子项逐层拆到可分派的工作包，并让每个项目维护一棵树？
- **倾向：有倾向**。推荐中文“项目工作分解结构”，WBS 保留为缩写；一项目一棵树。理由：用户已定项目层次和合同范围挂接，完整中文能让人读懂树在管理什么。
- **依据**：[2026-10-15-215632-one-architecture-WBS-从商机报价出生-到项目完.md](../../decisions/2026-10-15-215632-one-architecture-WBS-从商机报价出生-到项目完.md) 落点：项目WBS依附项目，以稳定追溯编号串联；[2026-10-16-061232-one-architecture-阶段类型与-BC-划分确认页提交.md](../../decisions/2026-10-16-061232-one-architecture-阶段类型与-BC-划分确认页提交.md) S1b、S3、S5、D-6；[已登记词表（按 id 核对）](../terms.yaml) id=wbs_node、work_package；[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §1.2 INV-41、42；§2.2 AGG-20。完整边界见 [C12](c-class-basis.md#c12)。
- **重名／近义处理**：与已登记 `wbs_node`（工作分解结构节点）、`work_package`（工作包）分立为整棵树与其中节点；不合并进 `delivery_plan`（交付计划）。

### 13. 开票与收入额度

- **原 id / bc**：`billing_revenue_limit` / `receivables.wip`。
- **类型**：会计用语。
- **白话问题**：一个协议下，最多能开多少发票、最多能确认多少收入（把金额计作本期收入），是否分别记录和检查这两条上限？
- **倾向：有倾向**。推荐保留“开票与收入额度 / BillingRevenueLimit”一个承载记录，内部两条额度分开计算。理由：按合同付款条件可开票的金额与按已承诺服务分到的收入金额，来源和使用时点不同。
- **依据**：[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §1.2 INV-21、22；§2.2 AGG-44；[BUSINESS_RULES（老行为证据）](../../business/business-rules.md) RULE-184（老收入条款上限及待批占额证据）；[GL2 已提交答复](../../decisions/decision-pages/answers/gl2.json) B2-X06：收入确认保留规则层；[2026-10-15-233152-one-architecture-全企业业务对象确认页-11-组全.md](../../decisions/2026-10-15-233152-one-architecture-全企业业务对象确认页-11-组全.md) Q20、Q21 暂不定。完整边界见 [C13](c-class-basis.md#c13)。
- **重名／近义处理**：与已登记 `payment_term_item`（付款条件项）、`performance_obligation`（履约义务，即承诺给客户的服务）、`revenue_recognition_line`（收入确认行）分立；不把额度合并成收入确认规则。

### 14. 客户认可证据类型开放配置

- **原 id / bc**：`acceptance_evidence_type_admission` / `receivables.wip`。
- **类型**：会计用语。
- **白话问题**：哪些材料能让一笔金额正式计作收入，是否用“客户认可证据类型开放配置”按类别和生效日期逐项开放，历史迁入类别永不用于新确认？
- **倾向：有倾向**。推荐名称缩为“客户认可证据类型开放配置”，按版本逐类开放。理由：证据可留存和证据可触发收入是两件事，版本能说明某天采用了哪套许可。
- **依据**：[ADR-0047](../../adr/ADR-0047.md) 决定2、4.4–5；[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §2.2 AGG-92；[A1 已提交答复](../../decisions/decision-pages/answers/a1.json) A1-01、A1-02。完整边界见 [C14](c-class-basis.md#c14)。
- **重名／近义处理**：与第28项证据类型目录分立；与已登记 `revenue_recognition`（收入确认规则）分立，配置只决定准入。

### 15. 发票入账占用

- **原 id / bc**：`invoice_posting_reservation` / `accounting.tax`。
- **类型**：会计用语。
- **白话问题**：同一张进项发票（我方收到的发票）用于报销或记下应付给对方的款项时，是否先记“发票入账占用”，防止同一金额被重复记账？
- **倾向：有倾向**。推荐“发票入账占用 / InvoicePostingReservation”，在进项发票上统一限制累计使用金额。理由：一张发票可能分次使用，统一余额才能拦住不同来源重复用票。
- **依据**：[2026-10-16-072440-one-architecture-对象完整性与方法第-4-稿确认页.md](../../decisions/2026-10-16-072440-one-architecture-对象完整性与方法第-4-稿确认页.md) D-7：进项发票与入账占用进入独立税务BC；[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §1.2 INV-74；§2.2 AGG-89、AGG-52（报销来源应付不再占用）。完整边界见 [C15](c-class-basis.md#c15)。
- **重名／近义处理**：作为已登记 `input_invoice` 的内部占用记录分立；与第16、17项往来余额核销占用分立。

### 16. 核销占用

- **原 id / bc**：`receivables_collection__cash_application_reservation` / `receivables.collection`。
- **类型**：会计用语。
- **白话问题**：把已到账的钱分给某笔应收款时，是否先留“应收核销占用”，正式抵掉客户欠款后再形成核销记录？
- **倾向：有倾向**。推荐加方向限定为“应收核销占用 / ReceivableCashApplicationReservation”。理由：先占后转记录可避免两笔收款同时抵同一份欠款，方向前缀也更清楚。
- **依据**：[GL2 已提交答复](../../decisions/decision-pages/answers/gl2.json) B2-X04、05：应收款项及回款单；[NM2 已提交答复](../../decisions/decision-pages/answers/nm2.json) NM2-12：回款单只记已到账款项；[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §1.2 INV-77、78；§2.2 AGG-49、50。完整边界见 [C16](c-class-basis.md#c16)。
- **重名／近义处理**：与第17项同名提议分立为应收／应付；与已登记 `cash_application`（收款核销）、`receipt_allocation`（回款分配）分立为占用、动作、分配明细；不并入 `ar_written_off`（无法收回的坏账核销）。

### 17. 核销占用

- **原 id / bc**：`payables_payment__cash_application_reservation` / `payables.payment`。
- **类型**：会计用语。
- **白话问题**：发起付款时，是否先留“应付核销占用”，银行付款成功后再正式抵掉这笔欠款并形成核销记录？
- **倾向：有倾向**。推荐加方向限定为“应付核销占用 / PayableCashApplicationReservation”。理由：付款发起和成功不是同一时点，先占应付余额可避免并发超付。
- **依据**：[GL2 已提交答复](../../decisions/decision-pages/answers/gl2.json) B2-X04、05；[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §1.2 INV-77a、79；§2.2 AGG-52、53：付款成功后占用转核销。完整边界见 [C17](c-class-basis.md#c17)。
- **重名／近义处理**：与第16项分立，各在所属业务范围使用；与已登记 `payable`（应付款项）、`cash_payment`（付款单）分立为欠款、付款和预占记录，不复用应收侧核销命令。

### 18. 供方协议履约额度

- **原 id / bc**：`supplier_agreement_performance_limit` / `procurement.subcontracting`。
- **类型**：会计用语。
- **白话问题**：与供货或服务单位签的有总金额协议，累计确认对方已完成工作的金额是否不得超过含已生效变更的协议总额，并用“供方协议履约额度”记录？
- **倾向：有倾向**。推荐保留“供方协议履约额度 / SupplierAgreementPerformanceLimit”，只用于有金额上限的协议。理由：对方完成工作金额与付款金额应分别控制，已生效变更才调整前者上限。
- **依据**：[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §1.2 INV-82；§2.2 AGG-57、58；[NM3 已提交答复](../../decisions/decision-pages/answers/nm3.json) NM3-11：变更按协议类型命名。完整边界见 [C18](c-class-basis.md#c18)。
- **重名／近义处理**：与已登记 `supplier_performance_confirmation_statement`（供方履约确认单）、第3项供方协议分立：分别是一次确认、原协议、累计额度；不合并为付款额度。

### 19. 分包变更已生效

- **原 id / bc**：`subcontract_change_activated` / `procurement.subcontracting`。
- **类型**：业务含义。
- **白话问题**：分包协议（约定由别的单位完成部分工作）的正式变更，是否在批准且到达约定生效时点后，才记录“分包变更已生效”并调整可确认工作的金额上限？
- **倾向：有倾向**。推荐保留中文“分包变更已生效”，英文候选 SubcontractChangeBecameEffective。理由：批准和生效可能有时间差，事件应表达已经生效的事实。
- **依据**：[NM3 已提交答复](../../decisions/decision-pages/answers/nm3.json) NM3-11；[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §1.2 INV-16b；§2.2 AGG-56；[N4 动词与状态候选及 NM2 定稿](n4-verbs-states.md) §2.2 V008、V014；§4.2 不及物生效事件模板。完整边界见 [C19](c-class-basis.md#c19)。
- **重名／近义处理**：与已登记 `subcontract_change`（分包变更对象）、`change_order_approved`（客户侧变更批准事件）分立；采购、租赁变更按类型分立。

### 20. 人工成本分配结果

- **原 id / bc**：`labor_cost_allocation_result` / `cost.costing`。
- **类型**：会计用语。
- **白话问题**：每月按已确定的工时和人工成本（工资等用工费用）把金额分给项目后，是否把各项目分得的金额叫“人工成本分配结果”，并只保留一份当前有效结果？
- **倾向：有倾向**。推荐保留“人工成本分配结果 / LaborCostAllocationResult”，与分配运行记录分开。理由：一次运行可有试算、更正和失效，结果是最终分到各项目的金额。
- **依据**：[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §1.2 INV-89；§2.2 AGG-41：每期间一次有效，冲销后失效计；[已登记词表（按 id 核对）](../terms.yaml) id=allocation_result；[NM2 题面](../../decisions/decision-pages/sheets/nm2.json) NM2-04：方法与一次结果分开。完整边界见 [C20](c-class-basis.md#c20)。
- **重名／近义处理**：与已登记 `allocation_result`（间接费用分摊结果）分立，避免把直接人工与间接费用混在一起；与 AGG-41“人工成本分配运行”分立为结果和处理过程。

### 21. 收付权责科目映射表

- **原 id / bc**：`cash_accrual_account_mapping` / `accounting.ledger`。
- **类型**：会计用语。
- **白话问题**：两套账使用的会计科目（把资金和收支分类记账的栏目）如何对应，是否叫“收付权责科目映射表”，并与业务事项生成凭证的规则分开？
- **倾向：有倾向**。推荐保留“收付权责科目映射表 / CashAccrualAccountMapping”，两种映射分立。理由：科目对科目回答两个账簿如何对应，事项对凭证回答一笔业务如何记账。
- **依据**：[ADR-0022](../../adr/ADR-0022.md) 决定2：两张科目表、版本化收付科目↔权责科目映射；[已登记词表（按 id 核对）](../terms.yaml) id=accounting_mapping_rule_set、chart_of_accounts_version；[NM2 已提交答复](../../decisions/decision-pages/answers/nm2.json) NM2-22。完整边界见 [C21](c-class-basis.md#c21)。
- **重名／近义处理**：与已登记 `accounting_mapping_rule_set`（核算映射规则）分立；与 `chart_of_accounts_version`（科目表版本）分立为对应关系和科目目录。

### 22. 控制科目授权表

- **原 id / bc**：`control_account_authorization` / `accounting.ledger`。
- **类型**：会计用语。
- **白话问题**：应收、应付等重点科目（限制可由哪些业务记账的栏目），是否用“控制科目授权表”列明可用的业务种类及系统、导入或人工记账入口？
- **倾向：有倾向**。推荐保留“控制科目授权表 / ControlAccountAuthorization”，按科目、事实类型、入口共同授权。理由：只说明能用哪个科目还不够，还须限制什么业务经什么入口写入。
- **依据**：[ADR-0022](../../adr/ADR-0022.md) 决定3“控制科目”及来源通道授权；[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §2.2 AGG-94；[NM2 已提交答复](../../decisions/decision-pages/answers/nm2.json) NM2-22。完整边界见 [C22](c-class-basis.md#c22)。
- **重名／近义处理**：与已登记 `account`（会计科目）、`chart_of_accounts_version` 分立为授权配置与目录；与 `controlled_manual_voucher`（受控手工凭证）分立，后者是受授权约束的一张凭证。

### 23. 无异议已声明

- **原 id / bc**：`no_objection_declared` / `delivery.acceptance`。
- **类型**：业务含义。
- **白话问题**：项目经理声明“截至某日没收到客户的书面异议”时，是否只记录“无异议已声明”，之后再按合同约定判断是否可当作客户已同意？
- **倾向：有倾向**。推荐保留“无异议已声明 / NoObjectionDeclared”，仅表示项目经理已作声明。理由：项目经理的陈述与客户同意是不同事实，合同条件尚需单独核对。
- **依据**：[ADR-0049](../../adr/ADR-0049.md) 决定1、2、5；[已登记词表（按 id 核对）](../terms.yaml) id=no_objection_declaration、deemed_acceptance。完整边界见 [C23](c-class-basis.md#c23)。
- **重名／近义处理**：与已登记 `no_objection_declaration`（无异议声明材料）分立为事件和材料；与 `deemed_acceptance`（合同条件满足后视为客户同意）分立。

### 24. 设计变更主张状态

- **原 id / bc**：`design_change_claim_status` / `delivery.acceptance`。
- **类型**：业务含义。
- **白话问题**：设计内容改变后，是否只在交付侧记录“待向客户提出”或“不向客户提出及原因”，真正提出加价或延期要求则记在合同侧？
- **倾向：有倾向**。推荐保留“设计变更主张状态 / DesignChangeClaimStatus”，限定为是否准备向客户提出要求。理由：交付侧是否准备提出，与合同侧已经提出了什么要求，是不同阶段的信息。
- **依据**：[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §2.2 AGG-30；[NM3 已提交答复](../../decisions/decision-pages/answers/nm3.json) NM3-01：设计变更名称已提交认可；[N4 动词与状态候选及 NM2 定稿](n4-verbs-states.md) §4.3“标记待主张”不表示已经主张。完整边界见 [C24](c-class-basis.md#c24)。
- **重名／近义处理**：与已登记 `design_change`（设计变更）作状态关系，分立于 `change_order`（合同变更）、`claim`（索赔，即要求对方承担价款或责任）、`claim_asserted`（索赔通知）。

### 25. 认可日

- **原 id / bc**：`acceptance_date` / `delivery.acceptance`。
- **类型**：会计用语。
- **白话问题**：客户认可成果的日期是否叫“认可日”，按签字、邮件或合同约定期满等材料各自取日，用来确定收入应归哪个月？
- **倾向：有倾向**。推荐保留“认可日 / AcceptanceDate”，按证据类型取日，不取系统录入日。理由：收入属于哪个月要有客户认可的依据，录入时间不能替代该业务日期。
- **依据**：[ADR-0047](../../adr/ADR-0047.md) 决定2表、3.1–4；[ADR-0049](../../adr/ADR-0049.md) 决定2、5：视为同意取期满日；[GL2 已提交答复](../../decisions/decision-pages/answers/gl2.json) B2-X08：认可与验收分工；[A1 已提交答复](../../decisions/decision-pages/answers/a1.json) A1-01。完整边界见 [C25](c-class-basis.md#c25)。
- **重名／近义处理**：与已登记 `delivery_record`（送达记录）中的送达日、`milestone_accepted`（验收通过）及第41项快照日分立；认可事实与质量验收已由 GL2 区分。

### 26. 审查期

- **原 id / bc**：`review_period` / `delivery.acceptance`。
- **类型**：业务含义。
- **白话问题**：“审查期”是否只指合同给客户审阅本轮成果的期限，修改后重新送达就重新起算，没约定期限时只催签和预警？
- **倾向：有倾向**。推荐保留“审查期 / ReviewPeriod”，逐合同登记并按每轮送达起算。理由：合同是否约定期限和届满后视为同意，不能用全公司统一天数替代。
- **依据**：[ADR-0047](../../adr/ADR-0047.md) 决定5.1–3、5.5；待核 F-3、F-4、F-8；[2026-10-16-162821-one-architecture-流程样板页提交-三组整体均-符合.md](../../decisions/2026-10-16-162821-one-architecture-流程样板页提交-三组整体均-符合.md) 未约定审查期时催签与预警、不自动认可。完整边界见 [C26](c-class-basis.md#c26)。
- **重名／近义处理**：与已登记 `defects_liability_period`（缺陷责任期，即交付后承担缺陷责任的期间）分立；与第27项期满记录分立为参数与计算留痕。

### 27. 期满记录

- **原 id / bc**：`expiry_record` / `delivery.acceptance`。
- **类型**：业务含义。
- **白话问题**：是否为每轮合同审查期届满留下“期满记录”，写清从哪天算、怎么算到这一天及用了什么合同约定？
- **倾向：有倾向**。推荐保留“期满记录 / ExpiryRecord”，由收入侧保存计算依据。理由：只存一个到期日不能解释为什么当天届满，也不能证明客户已经同意。
- **依据**：[ADR-0047](../../adr/ADR-0047.md) 决定5.3：合同条款引用、起算日、期限单位、末日顺延、无异议检索；[ADR-0049](../../adr/ADR-0049.md) 决定2、4、5。完整边界见 [C27](c-class-basis.md#c27)。
- **重名／近义处理**：与已登记 `deemed_acceptance`（视为同意规则）和第23项无异议声明事件分立；与 `evidence_link_record` 分立为材料与挂接关系。

### 28. 客户认可证据类型

- **原 id / bc**：`acceptance_evidence_type` / `delivery.acceptance`。
- **类型**：业务含义。
- **白话问题**：“客户认可证据类型”是否继续列八种证明客户接受成果的材料，并把“历史迁入”单独标成仅供导入、不能触发新收入的类别？
- **倾向：有倾向**。推荐保留“客户认可证据类型 / AcceptanceEvidenceType”，目录内区分八类证据与历史迁入用途。理由：既能读懂旧数据，也避免把迁入标签当成客户认可材料。
- **依据**：[ADR-0047](../../adr/ADR-0047.md) 决定2：八类及非证据枚举“历史迁入”；[GL2 已提交答复](../../decisions/decision-pages/answers/gl2.json) B2-X08。完整边界见 [C28](c-class-basis.md#c28)。
- **重名／近义处理**：与第14项开放配置分立；与已登记 `acceptance_record`（验收单）、`deemed_acceptance` 分立；宽口径第48项“证据”是上位概念。

### 29. 往来单位银行账户已变更

- **原 id / bc**：`counterparty_bank_account_changed` / `masterdata.counterparty`。
- **类型**：业务含义。
- **白话问题**：往来单位变更收款银行账户时，是否叫“往来单位银行账户已变更”，付款前重新查询最新账户并提示风险？
- **倾向：有倾向**。推荐保留“往来单位银行账户已变更 / CounterpartyBankAccountChanged”，付款前查账户、只预警。理由：较新草稿已取消事件订阅拦截，不能沿用旧定义把变更直接等同拒付。
- **依据**：[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) 修订记录“跨组接缝检查”S-42①；§2.2 AGG-01、AGG-53；§1.2 INV-80；[NM2 已提交答复](../../decisions/decision-pages/answers/nm2.json) NM2-21；[NM2 题面](../../decisions/decision-pages/sheets/nm2.json) NM2-21：资金账户为现金或银行资金账户统称。完整边界见 [C29](c-class-basis.md#c29)。
- **重名／近义处理**：与已登记 `fund_account`（资金账户，我方持有资金的账户）分立；与 `counterparty` 分立为单位资料和变更事件，不另建同义银行变更事件。

### 30. 投标已中标

- **原 id / bc**：`bid_won` / `presales.opportunity`。
- **类型**：业务含义。
- **白话问题**：收到并登记本次投标的中标通知书后，是否记录“投标已中标”，并据此记为商机赢单（已经赢得这次业务），同时另记合同是否已签回？
- **倾向：有倾向**。推荐保留“投标已中标 / BidWon”，以登记中标通知为依据。理由：中标是投标结果，签回合同是另一事实，二者不能靠一个事件名混同。
- **依据**：[2026-10-15-205406-one-architecture-售前信息图确认页保存-合同与项目.md](../../decisions/2026-10-15-205406-one-architecture-售前信息图确认页保存-合同与项目.md) 回签即生效；所有竞争性采购建投标；[2026-10-16-120544-one-architecture-售前回溯确认页提交-P-1-P.md](../../decisions/2026-10-16-120544-one-architecture-售前回溯确认页提交-P-1-P.md) P-1 中标通知书为投标附件；P-2 赢单不转投入；[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §1.2 INV-27；§2.2 AGG-10、11。完整边界见 [C30](c-class-basis.md#c30)。
- **重名／近义处理**：与已登记 `bid`（投标）分立为过程对象和结果事件；与 `contract_signed`（合同签署事实）及商机赢单分立，一商机可多次投标。

### 31. 投标未中标

- **原 id / bc**：`bid_lost` / `presales.opportunity`。
- **类型**：业务含义。
- **白话问题**：本次投标已有明确落选结果时，是否记录“投标未中标”，保证金（为参投先交的担保款）另按招标文件约定办理退还？
- **倾向：有倾向**。推荐保留“投标未中标 / BidLost”，只表示已确认落选，退还保证金另核条件。理由：未出结果不等于落选，落选也不能单独证明钱已经可以退还。
- **依据**：[2026-10-15-210513-one-architecture-售前聚合与-BC-确认页保存-一.md](../../decisions/2026-10-15-210513-one-architecture-售前聚合与-BC-确认页保存-一.md) 保证金在应收里管，先做登记；[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §2.2 AGG-11：登记未中标及 P-10 保证金退还；[N4 动词与状态候选及 NM2 定稿](n4-verbs-states.md) §3.2：未中标作为落选结果区别于尚未中标。完整边界见 [C31](c-class-basis.md#c31)。
- **重名／近义处理**：与第30项中标事件分立；与已登记 `bid`、`bond_and_deposit`（保证金与押金）、`retention_money_release_due`（质量保证金到期返还）分立，不能混用保证金类型。

### 32. 商机已输单

- **原 id / bc**：`opportunity_lost` / `presales.opportunity`。
- **类型**：业务含义。
- **白话问题**：商机因为没有赢得业务而结束时，是否叫“商机已输单”，记录原因、竞争对手和中标信息，并与我方主动不做分开？
- **倾向：有倾向**。推荐“商机已输单 / OpportunityLost”，“丢单”作为旧口语检索写法。理由：用户要求保留丢单原因及竞争结果，正式名应与主动放弃清楚区分。
- **依据**：[2026-10-15-202939-one-architecture-售前业务对象问题页保存-线索单独.md](../../decisions/2026-10-15-202939-one-architecture-售前业务对象问题页保存-线索单独.md) 原话“丢单要记原因”；[2026-10-15-205406-one-architecture-售前信息图确认页保存-合同与项目.md](../../decisions/2026-10-15-205406-one-architecture-售前信息图确认页保存-合同与项目.md) 原话“丢单记竞争对手、中标单位、中标价”；[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §2.2 AGG-10；[N4 动词与状态候选及 NM2 定稿](n4-verbs-states.md) §3.2：投标结果与商机输单对象不同。完整边界见 [C32](c-class-basis.md#c32)。
- **重名／近义处理**：与第33项商机已放弃分立；与已登记 `win_rate`（赢单率）、`opportunity`（商机）衔接，不因有统计指标就自动批准两类如何计入分母。

### 33. 商机已放弃

- **原 id / bc**：`opportunity_abandoned` / `presales.opportunity`。
- **类型**：业务含义。
- **白话问题**：我方决定不参与或中途退出一个商机时，是否记录“商机已放弃”并留下理由，区别于参与后输给别人？
- **倾向：有倾向**。推荐保留“商机已放弃 / OpportunityAbandoned”。理由：主动不做的决定有独立责任人与理由，不能从对手胜出结果推出来。
- **依据**：[参投决策规则卡](../../business/rules/bid-decision-rules.md) RULE-414：商机负责人作参投／放弃决定，理由及评分留痕；[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §2.2 AGG-10。完整边界见 [C33](c-class-basis.md#c33)。
- **重名／近义处理**：与第32项输单分立；与已登记 `migration_batch_status__abandoned`（迁入批次已放弃）是不同对象、分立，不因同名 Abandoned 合并。

### 34. 协议已终止

- **原 id / bc**：`agreement_terminated` / `contract.agreement`。
- **类型**：业务含义。
- **白话问题**：协议约定的工作提前不再继续时，是否记录“协议已终止”，与自然到期或全部履行完成分开，并另处理此前已完成工作仍应收取的款项？
- **倾向：有倾向**。推荐保留“协议已终止 / AgreementTerminated”，与到期、完成分立。理由：提前结束不表示此前债权和结算义务消失，不能从名称推成所有开票都禁止。
- **依据**：[2026-10-15-205406-one-architecture-售前信息图确认页保存-合同与项目.md](../../decisions/2026-10-15-205406-one-architecture-售前信息图确认页保存-合同与项目.md) 合同与项目分开；[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §2.2 AGG-13：已完成、已结算、已终止分列且终止原因必填；[N4 动词与状态候选及 NM2 定稿](n4-verbs-states.md) §2.3：关闭／终止／完成分立。完整边界见 [C34](c-class-basis.md#c34)。
- **重名／近义处理**：与已登记 `agreement`、`contract_signed`、`agreement_final_account_confirmed`（协议竣工结算已确认）分立；与第39项项目终止分立，协议与项目是不同对象。

### 35. 协议新基线已生效

- **原 id / bc**：`agreement_baseline_activated` / `contract.agreement`。
- **类型**：业务含义。
- **白话问题**：协议变更形成的新版本内容正式生效时，是否叫“协议新基线已生效”，其中基线就是后续按其执行的合同内容版本？
- **倾向：有倾向**。推荐保留中文，英文候选 AgreementBaselineBecameEffective，并保留新旧版本追溯。理由：事件应指已经生效的版本，不能把批准变更或启用配置当成同一件事。
- **依据**：[2026-10-15-205406-one-architecture-售前信息图确认页保存-合同与项目.md](../../decisions/2026-10-15-205406-one-architecture-售前信息图确认页保存-合同与项目.md) 回签即生效；[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §1.2 INV-07、08；§2.2 AGG-13 应用变更→新基线已生效；[N4 动词与状态候选及 NM2 定稿](n4-verbs-states.md) §2.2 V008、V014；§4.2 生效模板。完整边界见 [C35](c-class-basis.md#c35)。
- **重名／近义处理**：与已登记 `contract_baseline`（合同基线）、`baseline_version`（基线版本）、`contract_modification`（合同已变更）分立：版本对象、版本号关系与生效事件各有用途。

### 36. 协议项目关联已登记

- **原 id / bc**：`agreement_project_linked` / `contract.agreement`。
- **类型**：业务含义。
- **白话问题**：协议与我方项目的对应关系登记完成时，是否叫“协议项目关联已登记”，允许多对多并保留各段有效日期？
- **倾向：有倾向**。推荐保留中文，英文候选 AgreementProjectLinkRegistered，登记多对多及有效期。理由：用户已定关联由合同侧登记，事件名也应表达记录已登记，不能误称合同生效。
- **依据**：[2026-10-15-210513-one-architecture-售前聚合与-BC-确认页保存-一.md](../../decisions/2026-10-15-210513-one-architecture-售前聚合与-BC-确认页保存-一.md) 合同—项目关联由合同登记、多对多；[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §1.2 INV-09；§2.2 AGG-13；[N4 动词与状态候选及 NM2 定稿](n4-verbs-states.md) §4.2 recorded_fact、§4.3 合同—项目关联。完整边界见 [C36](c-class-basis.md#c36)。
- **重名／近义处理**：与已登记 `agreement`、`project` 分立；与 `evidence_link_record`（证据关联记录）对象不同、分立，不合并为无对象的通用关联。

### 37. 项目已建立

- **原 id / bc**：`project_established` / `delivery.execution`。
- **类型**：业务含义。
- **白话问题**：我方项目登记建立时是否叫“项目已建立”，而签约前花的工时和费用转入项目的处理仍须等合同签回和项目建立两个条件都满足？
- **倾向：有倾向**。推荐保留“项目已建立 / ProjectEstablished”，成本转入按双条件处理。理由：用户后续已明确赢单不转、仅建立项目也不足以转入，不能以旧表单名决定时点。
- **依据**：[2026-10-15-210513-one-architecture-售前聚合与-BC-确认页保存-一.md](../../decisions/2026-10-15-210513-one-architecture-售前聚合与-BC-确认页保存-一.md) 项目可先于合同建立；[2026-10-16-120544-one-architecture-售前回溯确认页提交-P-1-P.md](../../decisions/2026-10-16-120544-one-architecture-售前回溯确认页提交-P-1-P.md) P-2：合同签回且项目已建立后转入，赢单不转；[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §2.2 AGG-19。完整边界见 [C37](c-class-basis.md#c37)。
- **重名／近义处理**：与已登记 `project`、`precontract_investment`（签约前投入）分立；与 `contract_signed` 分立，建立不等于签约或开始执行。

### 38. 项目已结项

- **原 id / bc**：`project_closed` / `delivery.execution`。
- **类型**：业务含义。
- **白话问题**：项目完成内部收尾检查后是否记录“项目已结项”，把与客户核对最终价款的竣工结算作为另一件事？
- **倾向：有倾向**。推荐保留“项目已结项 / ProjectClosed”，结项与竣工结算分立。理由：一个是我方管理收尾，另一个是与客户确定价款，不能用一个状态混记。
- **依据**：[2026-10-15-233152-one-architecture-全企业业务对象确认页-11-组全.md](../../decisions/2026-10-15-233152-one-architecture-全企业业务对象确认页-11-组全.md) Q10：收入、分包、归档三项拒绝，工时只预警；[2026-10-16-072440-one-architecture-对象完整性与方法第-4-稿确认页.md](../../decisions/2026-10-16-072440-one-architecture-对象完整性与方法第-4-稿确认页.md) D-9：独立项目结项流程模块；[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §1.2 INV-40；§2.2 AGG-19；[GL2 已提交答复](../../decisions/decision-pages/answers/gl2.json) B2-X11：收尾与竣工结算分工。完整边界见 [C38](c-class-basis.md#c38)。
- **重名／近义处理**：与已登记 `final_account`（竣工结算）、`agreement_final_account_confirmed` 分立；与第34项协议终止、第39项项目终止分立。

### 39. 项目已终止

- **原 id / bc**：`project_terminated` / `delivery.execution`。
- **类型**：业务含义。
- **白话问题**：项目在完成之前决定不再做，您希望按“是否已开始执行”还是“是否已经发生成本”来区分终止与取消？
- **倾向：无倾向**。无倾向。理由：无依据，请凭业务判断。
- **依据**：[2026-10-15-233152-one-architecture-全企业业务对象确认页-11-组全.md](../../decisions/2026-10-15-233152-one-architecture-全企业业务对象确认页-11-组全.md) Q09 项目状态机暂不定；[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §1.1 Q09；§2.2 AGG-19：终止、取消均为草稿命令。完整边界见 [C39](c-class-basis.md#c39)。
- **重名／近义处理**：与第40项项目已取消近义，分立还是合并无倾向；与已登记 `project` 是对象与事件关系，与 `terminate`（终止审批流程）对象不同，不据同形词合并。

### 40. 项目已取消

- **原 id / bc**：`project_cancelled` / `delivery.execution`。
- **类型**：业务含义。
- **白话问题**：项目还没开始执行、但已经花过前期费用时，您希望记为“项目已取消”还是“项目已终止”？
- **倾向：无倾向**。无倾向。理由：无依据，请凭业务判断。
- **依据**：[2026-10-15-233152-one-architecture-全企业业务对象确认页-11-组全.md](../../decisions/2026-10-15-233152-one-architecture-全企业业务对象确认页-11-组全.md) Q09 暂不定；Q19 以合同回签为管理口径分界；[2026-10-15-234418-one-architecture-答复复核-R1–R5-提交-已提.md](../../decisions/2026-10-15-234418-one-architecture-答复复核-R1–R5-提交-已提.md) R3：立项后回签前履约投入先挂合同履约成本；[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §1.1 Q09；§2.2 AGG-19。完整边界见 [C40](c-class-basis.md#c40)。
- **重名／近义处理**：与第39项近义，合并／分立无倾向；与已登记 `workforce__revoked`（已撤销）及审批取消的对象、行为不同，不直接复用。

### 41. 快照日

- **原 id / bc**：`snapshot_date` / `organization.workforce`。
- **类型**：技术含义。
- **白话问题**：既然人事版本已按实际入职、调岗等生效日记录，旧“快照日”是否只保留为读取旧资料的日期，不再当作人事生效日？
- **倾向：有倾向**。推荐旧“快照日 / SnapshotDate”退出人事版本生效用途，观察日与业务生效日分开。理由：后续用户决定已明确取人事业务生效日，早期同步开始日口径已被取代。
- **依据**：[2026-10-16-134434-one-architecture-U-12-白话重问页提交-用人事.md](../../decisions/2026-10-16-134434-one-architecture-U-12-白话重问页提交-用人事.md) 原话“用人事业务生效日（入职日、调岗生效日，人事登记时填写）”；[ADR-0055](../../adr/ADR-0055.md) 决定2、6；[2026-10-16-085331-one-data-员工与岗位怎样版本化-都做维度型.md](../../decisions/2026-10-16-085331-one-data-员工与岗位怎样版本化-都做维度型.md) 被取代的“以同步观察日为生效依据”。完整边界见 [C41](c-class-basis.md#c41)。
- **重名／近义处理**：与已登记 `org_snapshot`（组织快照）、`org_snapshot_changed`（组织快照已变化事件）分立为读取日期及数据／事件；与第46项离职观察时间分立。

### 42. 在职判定

- **原 id / bc**：`employment_verdict` / `organization.workforce`。
- **类型**：技术含义。
- **白话问题**：投产后的“在职判定”是否只依据本系统有效的人事记录，缺记录时拒绝并告警，不再保留“外部快照不可用也放行”的分支？
- **倾向：有倾向**。推荐按本地人事状态作判定，旧四分支仅留历史解释。理由：已采纳路线撤销外部故障前提，缺记录不能冒充在职或离职。
- **依据**：[ADR-0055](../../adr/ADR-0055.md) 决定3.1；[ADR-0033](../../adr/ADR-0033.md) 决定3.7（旧快照可用／不可用规则，已被部分取代）。完整边界见 [C42](c-class-basis.md#c42)。
- **重名／近义处理**：与已登记 `employment_status`（在职状态）、`employed`／`separated` 分立为判定结果及业务状态；`source_employment_state`（来源在职状态）只作迁入翻译，不合并成本地权威状态。

### 43. 登录名

- **原 id / bc**：`workforce__login_name` / `platform.integration`。
- **类型**：技术含义。
- **白话问题**：组织源里的“登录名”是否只作旧资料的外部登录标识，投产后不再用“缺登录名”判断某人离职？
- **倾向：有倾向**。推荐与已登记“外部登录名 / ExternalLoginName”复用同一标识含义，撤离职判定用途。理由：旧规则的登录名缺失判离职已被本地人事状态替代，不能继续藏在词条定义里。
- **依据**：[2026-10-15-153841-one-coordinator-员工在职判定保持现状-登录名为空.md](../../decisions/2026-10-15-153841-one-coordinator-员工在职判定保持现状-登录名为空.md) 旧决定：保持 RULE-363 口径；[身份组织规则卡（老行为证据）](../../business/rules/identity-org-rules.md) RULE-363（老代码证据）；[ADR-0055](../../adr/ADR-0055.md) 决定2：登录名判离职改本地状态；决定5.1、6；[已登记词表（按 id 核对）](../terms.yaml) id=external_login_name。完整边界见 [C43](c-class-basis.md#c43)。
- **重名／近义处理**：与已登记 `external_login_name` 近义，倾向合并标识概念、保留来源限定用途；不把员工、人事状态或本系统账号合并。

### 44. 员工任职快照

- **原 id / bc**：`workforce__staff_employee` / `organization.workforce`。
- **类型**：技术含义。
- **白话问题**：旧同步中的“员工任职快照”是否退出日常人事维护，迁入时分别识别员工和任职（担任什么岗位），保留一人多项任职的情况？
- **倾向：有倾向**。推荐退役日常同步用途，迁入分别复用员工与任职概念。理由：员工和任职的数量不同，把二者当同一条永久记录会遮住兼职或任职变更。
- **依据**：[GL 已提交答复](../../decisions/decision-pages/answers/gl.json) B2-E02：人员上位对象、原提议改称员工任职快照；[2026-10-16-072440-one-architecture-对象完整性与方法第-4-稿确认页.md](../../decisions/2026-10-16-072440-one-architecture-对象完整性与方法第-4-稿确认页.md) INV-62：主任职不重叠、兼职不设上限；[ADR-0055](../../adr/ADR-0055.md) 决定2、5.1–2、6。完整边界见 [C44](c-class-basis.md#c44)。
- **重名／近义处理**：与已登记 `employee`（员工）、`workforce__assignment`（任职）、`org_snapshot` 分立；倾向复用员工与任职而不再新增一个日常业务实体；历史快照作为组合结构留档。

### 45. 关系

- **原 id / bc**：`workforce__relationship` / `platform.integration`。
- **类型**：技术含义。
- **白话问题**：旧组织源里的“关系”是否只用于迁入时识别部门负责人、区域应收负责人（负责区域客户欠款事项的人），投产后改由本系统分别维护这两种职责？
- **倾向：有倾向**。推荐按已登记职责分别复用“部门负责人”和“区域应收负责人”，泛称“关系”仅留来源映射。理由：两种职责含义不同，来源里的数字类型只是迁入编码。
- **依据**：[身份组织规则卡（老行为证据）](../../business/rules/identity-org-rules.md) RULE-356：两类来源关系及多个结果任取其一的旧缺陷；[已登记词表（按 id 核对）](../terms.yaml) id=org_unit_head、regional_receivables_manager；[ADR-0055](../../adr/ADR-0055.md) 决定2、5.1、6。完整边界见 [C45](c-class-basis.md#c45)。
- **重名／近义处理**：与已登记 `org_unit_head`、`regional_receivables_manager` 分立职责并复用目标；原“关系”如仅为二者来源汇称，不另建同义业务概念。

### 46. 离职观察时间

- **原 id / bc**：`separation_observed_at` / `platform.integration`。
- **类型**：技术含义。
- **白话问题**：“离职观察时间”是否只表示旧同步首次把人判为离职并写入的时间，与人事填写的离职生效日、系统实际收回权限的时间分别记录？
- **倾向：有倾向**。推荐保留三种时间的区别，旧“离职观察时间 / SeparationObservedAt”限历史追溯。理由：观察、业务生效和权限实际收回可能在不同时间，不能相互回填。
- **依据**：[组织与任职历史规则卡（候选）](../../business/rules/workforce-history-rules.md) “离职申请的预计离职日期与离职观察时间是两件事”候选规则；[2026-10-16-134434-one-architecture-U-12-白话重问页提交-用人事.md](../../decisions/2026-10-16-134434-one-architecture-U-12-白话重问页提交-用人事.md) 人事业务生效日；修改时间由审计留痕；[ADR-0055](../../adr/ADR-0055.md) 决定2、3.5–6、6。完整边界见 [C46](c-class-basis.md#c46)。
- **重名／近义处理**：与已登记 `expected_separation_date`（预计离职日期）分立；与人事生效日、授予关闭时刻分立，单说“离职日期 / separation_date”仍按原提议禁用歧义写法。

### 47. 法人归属对象

- **原 id / bc**：`attribution_subject` / `organization.workforce`。
- **类型**：业务含义。
- **白话问题**：“法人归属对象”是否只表示正在指定归属的部门或员工，而员工的劳动合同用人单位、数据范围法人（决定可看哪些公司的资料）、个税扣缴义务人（替员工扣缴税款的单位）分别记录，彼此不同只预警？
- **倾向：有倾向**。推荐保留“法人归属对象 / AttributionSubject”，同一归属用途内限制同一时点一个法人。理由：用户已要求三口径分开，不能再把“单一归属”扩成一个员工所有用途只能有一个法人。
- **依据**：[ADR-0033](../../adr/ADR-0033.md) 决定7.4（DA-5：部门或员工恰一、分别类型化ID）；[已提交Q-09的归档题面](../../decisions/decision-pages/archive/archive/PENDING.json) Q-09：劳动合同用人单位、数据范围法人、个税扣缴义务人；[2026-10-16-134134-one-architecture-ADR-待决-14-题页提交-四.md](../../decisions/2026-10-16-134134-one-architecture-ADR-待决-14-题页提交-四.md) Q-09：员工法人归属三口径分别记录、不一致时预警；[法人归属规则卡](../../business/rules/legal-entity-attribution-rules.md) RULE-385：同对象期间不重叠、更正留痕。完整边界见 [C47](c-class-basis.md#c47)。
- **重名／近义处理**：与已登记 `legal_entity_attribution`（法人归属记录）分立为被归属对象与关系；与 `employee`、`org_unit` 分立，复用它们的类型化标识。

### 48. 证据

- **原 id / bc**：`acceptance_evidence` / `delivery.acceptance`。
- **类型**：技术含义。
- **白话问题**：中文“证据”及其宽范围已定，英文代码名是否改为 Evidence，使它也能保存过程和其他证明材料，而不只表示客户验收材料？
- **倾向：有倾向**。推荐英文候选“Evidence”，保留已定中文“证据”和宽口径定义。理由：AcceptanceEvidence 容易把概念收窄为认可或验收材料，与已定范围不符。
- **依据**：[2026-10-16-072440-one-architecture-对象完整性与方法第-4-稿确认页.md](../../decisions/2026-10-16-072440-one-architecture-对象完整性与方法第-4-稿确认页.md) Q29：证据取宽口径；[2026-10-16-114200-one-architecture-符合性审查确认页提交-F-1-至.md](../../decisions/2026-10-16-114200-one-architecture-符合性审查确认页提交-F-1-至.md) F-1：证据留内容及采集人ID，证据关联另记挂接；[ADR-0053（本卡输入）](../../../../.codex-inputs/ADR-0053.md) 决定1.1、2.1–2、5.3：概念ID稳定、改名只改指称。完整边界见 [C48](c-class-basis.md#c48)。
- **重名／近义处理**：与已登记 `evidence_link_record`（证据关联记录）分立为内容和挂接关系；与 `fact_evidence`（事实证据）、`signature_evidence`（签署证据）、`validation_evidence`（认定证据）按各自用途分立，不凭宽口径全合并；与提议 `evidence`（取证动作）分立。

## 倾向分布与无依据项

- 有倾向46项：第1–38、41–48项；每项给出具体名称或处理及理由。
- 无倾向2项：第39、40项。两项均为“无依据，请凭业务判断”，取消／终止的合并或分立也无倾向，不预选。
- 类型分布：会计用语14项、业务含义26项、技术含义8项。
- 有依据只支持每项明确推荐的部分，不表示原定义全部获证。只有草稿支持的内容明确标为建议，具体业务参数仍见依据明细的缺口。

## 按主题分批出页建议

- **N7-A 会计命名与金额、期间**：14题；原编号 2、8、9、10、13、14、15、16、17、18、20、21、22、25。
- **N7-B 售前、协议与外部工程**：11题；原编号 1、3、11、29、30、31、32、33、34、35、36。
- **N7-C 交付、认可材料与项目收尾**：11题；原编号 12、19、23、24、26、27、28、37、38、39、40。
- **N7-D 人事业务与个人资料分类**：4题；原编号 5、6、7、47。
- **N7-E 技术处置与宽口径证据英文**：8题；原编号 4、41、42、43、44、45、46、48。

五批合计48题、互不重叠，每批均≤25题。第39、40项相邻、一并判断两词分界；第14／28项、第23／25／26／27项跨页互链，不重复提问。N7-E 是技术建议审阅页，已定人事边界不再要求用户重批；第48项只审英文候选。

## 仍不确定、不能借命名补定的事项

- 第1、2、3、11、12、19、29–36项：建议有出处，但名称、字段或事件效果没有全部逐项批准；特别是第29项仅有较新结构草稿支持查询＋预警，不能宣称用户已定立即拦截。
- 第7项：SENSITIVE_PI 的碰名理由已核实；P-029 原始答复未定位，不能把原提议的“已选改名”自述冒充原答复。
- 第13–18、20–22项：履约义务怎么划分、额度金额和含税口径、核销及释放时点、结果按法人还是全局唯一、映射与授权版本的细节均未由本卡补定。
- 第14、25–28项：证据可登记不等于已开放；ADR 旧审计师闸门与后续财务负责人文字仍有接缝；期满记录原 bc 与收入侧计算主权待核，不擅自搬迁。
- 第24、34–40项：待主张状态闭环、终止后的剩余应收与开票、结项“有结论”的条件及取消／终止分界仍待规则。第37项成本转入按后续已定双条件，不重问。
- 第4、41–47项：同步用途退出已定，代码调用方、迁入日期映射、本地枚举、归属三口径的字段及区间细节尚未在本卡验证；第43项近义合并不授权把来源标识扩散到业务模块。
- 第48项：中文和宽范围已定，Evidence 只是英文候选；提议中的“取证”动作与证据材料分立，概念 id 保持不变。

技术类另有维持提议12项仍按 [B_CLASS_DECISIONS.md](b-class-decisions.md) 处理，不计入本48项。
