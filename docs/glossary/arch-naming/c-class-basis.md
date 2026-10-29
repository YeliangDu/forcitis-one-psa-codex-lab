# NAM-1017-GLO-10：C 类 48 项依据与对账明细

日期：2026-10-17。Codex 起草。本文件支持 [C_CLASS_FOR_N7.md](c-class-for-n7.md)，只记录本地已读材料与本卡建议；不改原始分类、已定答复、提议术语或正式词表。

## 证据使用边界

- 用户原话／已提交答复只支持其明确决定的内容；题面与答复一起读。原始 defaulted:true 如实保留“按预选提交”的事实，不补写主动选择。
- 已采纳 ADR 按后续取代范围读。ADR-0055 优先于旧同步方案；ADR-0054 保留迁入机制并收紧迁入核对。NM2-20 按本卡用户指令适用于会计命名，不取消会计政策审批职责。
- 聚合草稿和 N4 候选只支持有出处的设计推荐；原提议只用于追溯问题，不能自证已获业务批准。BUSINESS_RULES、RULE-356／363 等老代码抽取只证明旧行为，不能直接充当新系统规则。
- 本卡未读到会计准则、合同范本或法律的完整原文；只在本地材料明确涉及处写准则名与主题，不编条款号。行业惯例只描述仓库已有的具体做法，不声称联网核验。第39、40项无可支持分界的业务依据，明确无倾向。
- 已登记近义项均以当前 terms.yaml 的 id 核对；不存在的候选只称“提议”或“草稿”。合并、分立、改名和用途退出全部是后续写回卡的建议，不在本卡执行。

## 48 项逐一对应

JSON 下标以0起算；表内 `id` 是稳定引用键。B 表编号是原 B105 编号，不能与本页 C 编号混用。JSON 中 B 的 category 未改；后续“改归C”的依据是 B_CLASS_DECISIONS 的逐项结论。

| 本页 | 原 id | reconcile-decided.json 定位 | B_CLASS_DECISIONS / 原清单 | 类型 | 倾向 |
| --- | --- | --- | --- | --- | --- |
| C01 | `tender_project` | `proposed[id=tender_project]`，C | 原C1 | 业务含义 | 有倾向 |
| C02 | `output_invoice` | `proposed[id=output_invoice]`，C | 原C2 | 会计用语 | 有倾向 |
| C03 | `supplier_agreement` | `proposed[id=supplier_agreement]`，C | 原C3 | 业务含义 | 有倾向 |
| C04 | `apply_org_change_set` | `proposed[id=apply_org_change_set]`，原B | B002：改归C | 技术含义 | 有倾向 |
| C05 | `separation_hold` | `proposed[id=separation_hold]`，原B | B003：改归C | 业务含义 | 有倾向 |
| C06 | `personal_information` | `proposed[id=personal_information]`，原B | B004：改归C | 业务含义 | 有倾向 |
| C07 | `sensitive_personal_information` | `proposed[id=sensitive_personal_information]`，原B | B005：改归C | 业务含义 | 有倾向 |
| C08 | `migration_period_reservation` | `proposed[id=migration_period_reservation]`，原B | B016：改归C | 会计用语 | 有倾向 |
| C09 | `migration_marker` | `proposed[id=migration_marker]`，原B | B017：改归C | 会计用语 | 有倾向 |
| C10 | `migration_posting_channel` | `proposed[id=migration_posting_channel]`，原B | B018：改归C | 会计用语 | 有倾向 |
| C11 | `own_participation_record` | `proposed[id=own_participation_record]`，原B | B030：改归C | 业务含义 | 有倾向 |
| C12 | `project_wbs` | `proposed[id=project_wbs]`，原B | B031：改归C | 业务含义 | 有倾向 |
| C13 | `billing_revenue_limit` | `proposed[id=billing_revenue_limit]`，原B | B032：改归C | 会计用语 | 有倾向 |
| C14 | `acceptance_evidence_type_admission` | `proposed[id=acceptance_evidence_type_admission]`，原B | B033：改归C | 会计用语 | 有倾向 |
| C15 | `invoice_posting_reservation` | `proposed[id=invoice_posting_reservation]`，原B | B034：改归C | 会计用语 | 有倾向 |
| C16 | `receivables_collection__cash_application_reservation` | `proposed[id=receivables_collection__cash_application_reservation]`，原B | B035：改归C | 会计用语 | 有倾向 |
| C17 | `payables_payment__cash_application_reservation` | `proposed[id=payables_payment__cash_application_reservation]`，原B | B036：改归C | 会计用语 | 有倾向 |
| C18 | `supplier_agreement_performance_limit` | `proposed[id=supplier_agreement_performance_limit]`，原B | B037：改归C | 会计用语 | 有倾向 |
| C19 | `subcontract_change_activated` | `proposed[id=subcontract_change_activated]`，原B | B038：改归C | 业务含义 | 有倾向 |
| C20 | `labor_cost_allocation_result` | `proposed[id=labor_cost_allocation_result]`，原B | B039：改归C | 会计用语 | 有倾向 |
| C21 | `cash_accrual_account_mapping` | `proposed[id=cash_accrual_account_mapping]`，原B | B040：改归C | 会计用语 | 有倾向 |
| C22 | `control_account_authorization` | `proposed[id=control_account_authorization]`，原B | B041：改归C | 会计用语 | 有倾向 |
| C23 | `no_objection_declared` | `proposed[id=no_objection_declared]`，原B | B042：改归C | 业务含义 | 有倾向 |
| C24 | `design_change_claim_status` | `proposed[id=design_change_claim_status]`，原B | B043：改归C | 业务含义 | 有倾向 |
| C25 | `acceptance_date` | `proposed[id=acceptance_date]`，原B | B044：改归C | 会计用语 | 有倾向 |
| C26 | `review_period` | `proposed[id=review_period]`，原B | B045：改归C | 业务含义 | 有倾向 |
| C27 | `expiry_record` | `proposed[id=expiry_record]`，原B | B046：改归C | 业务含义 | 有倾向 |
| C28 | `acceptance_evidence_type` | `proposed[id=acceptance_evidence_type]`，原B | B047：改归C | 业务含义 | 有倾向 |
| C29 | `counterparty_bank_account_changed` | `proposed[id=counterparty_bank_account_changed]`，原B | B048：改归C | 业务含义 | 有倾向 |
| C30 | `bid_won` | `proposed[id=bid_won]`，原B | B049：改归C | 业务含义 | 有倾向 |
| C31 | `bid_lost` | `proposed[id=bid_lost]`，原B | B050：改归C | 业务含义 | 有倾向 |
| C32 | `opportunity_lost` | `proposed[id=opportunity_lost]`，原B | B051：改归C | 业务含义 | 有倾向 |
| C33 | `opportunity_abandoned` | `proposed[id=opportunity_abandoned]`，原B | B052：改归C | 业务含义 | 有倾向 |
| C34 | `agreement_terminated` | `proposed[id=agreement_terminated]`，原B | B053：改归C | 业务含义 | 有倾向 |
| C35 | `agreement_baseline_activated` | `proposed[id=agreement_baseline_activated]`，原B | B054：改归C | 业务含义 | 有倾向 |
| C36 | `agreement_project_linked` | `proposed[id=agreement_project_linked]`，原B | B055：改归C | 业务含义 | 有倾向 |
| C37 | `project_established` | `proposed[id=project_established]`，原B | B056：改归C | 业务含义 | 有倾向 |
| C38 | `project_closed` | `proposed[id=project_closed]`，原B | B057：改归C | 业务含义 | 有倾向 |
| C39 | `project_terminated` | `proposed[id=project_terminated]`，原B | B058：改归C | 业务含义 | 无倾向 |
| C40 | `project_cancelled` | `proposed[id=project_cancelled]`，原B | B059：改归C | 业务含义 | 无倾向 |
| C41 | `snapshot_date` | `proposed[id=snapshot_date]`，原B | B060：改归C | 技术含义 | 有倾向 |
| C42 | `employment_verdict` | `proposed[id=employment_verdict]`，原B | B070：改归C | 技术含义 | 有倾向 |
| C43 | `workforce__login_name` | `proposed[id=workforce__login_name]`，原B | B077：改归C | 技术含义 | 有倾向 |
| C44 | `workforce__staff_employee` | `proposed[id=workforce__staff_employee]`，原B | B078：改归C | 技术含义 | 有倾向 |
| C45 | `workforce__relationship` | `proposed[id=workforce__relationship]`，原B | B079：改归C | 技术含义 | 有倾向 |
| C46 | `separation_observed_at` | `proposed[id=separation_observed_at]`，原B | B080：改归C | 技术含义 | 有倾向 |
| C47 | `attribution_subject` | `proposed[id=attribution_subject]`，原B | B081：改归C | 业务含义 | 有倾向 |
| C48 | `acceptance_evidence` | `missing_terms[30]`；`records[record=1273]` | 漏登31，B表“本轮结果”列英文待确认 | 技术含义 | 有倾向 |

核对结果：原C=3、B改归C=44、漏登英文=1，总计48；本页编号1–48无缺号、无重复，B改归C的44个id与原表集合相等。原 C3 亦见 RECONCILE_DECIDED.md §4 C 表；“证据”见该文漏登清单与 JSON 第31个缺口，英文仅草稿建议。

## 逐项依据明细

<a id="c01"></a>

### C01 招标项目

- **对账**：原 C 类；JSON `proposed[id=tender_project].category=C`。
- **原提议出处**：[backend/glossary/proposed/enterprise-aggregates.yaml](../../../backend/glossary/proposed/enterprise-aggregates.yaml)，`id=tender_project`。原始快照仍在 JSON，现文件可能已按后续决定更正。
- **可复查依据**：[2026-10-15-205406-one-architecture-售前信息图确认页保存-合同与项目.md](../../decisions/2026-10-15-205406-one-architecture-售前信息图确认页保存-合同与项目.md) 原话“所有竞争性采购建投标”；[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §1.2 INV-28；§2.2 REF-01、AGG-11。
- **实质支持及限度**：REF-01 已逐项列招标标识、采购方式、标段；INV-28 用招标登记 ID＋标段约束参投身份。这支持单独引用登记的建议，不支持把它升级为一套招标管理流程。
- **本卡倾向（有倾向）**：推荐保留“招标项目 / TenderProject”，作为外部引用登记。理由：同一招标及标段需要被多次业务引用，不能拿我方项目代替。
- **已登记重名／近义核对**：与已登记 `bid`（投标）、`project`（项目）、`construction_project`（建设工程）分立：分别是参投过程、我方交付、外部工程，均不合并。
- **尚缺什么**：名称及登记字段尚无用户逐项批准；不声称仓库所引《招标投标法》原文已核实。

<a id="c02"></a>

### C02 销项发票

- **对账**：原 C 类；JSON `proposed[id=output_invoice].category=C`。
- **原提议出处**：[backend/glossary/proposed/enterprise-aggregates.yaml](../../../backend/glossary/proposed/enterprise-aggregates.yaml)，`id=output_invoice`。原始快照仍在 JSON，现文件可能已按后续决定更正。
- **可复查依据**：[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §1.2 INV-72、73；§2.2 AGG-46；[NM2 已提交答复](../../decisions/decision-pages/answers/nm2.json) NM2-17；[NM2 题面](../../decisions/decision-pages/sheets/nm2.json) NM2-17 完整动作名。
- **实质支持及限度**：AGG-46 明列蓝字／红字、税务平台结果及确认过程；NM2-17 已提交采用“开具红字发票”。正常票与更正票的对象关系有具体草稿依据，但尚未变成销项发票名称的用户批准。
- **本卡倾向（有倾向）**：推荐“销项发票 / OutputInvoice”独立保存发票记录。理由：发票有票号、金额和更正关系，“开票”只描述一次已发生的动作。
- **已登记重名／近义核对**：与已登记 `invoice_issued`（开票事件）、`billing_request`（开票申请）分立；与 `input_invoice`（我方收到的进项发票）按方向分立。
- **尚缺什么**：税务平台与本系统分别维护哪些状态仍待接口规格；不据名称决定税额或收入时点，也不把旧法规名称当现行法核验。

<a id="c03"></a>

### C03 供方协议

- **对账**：原 C 类；JSON `proposed[id=supplier_agreement].category=C`。
- **原提议出处**：[backend/glossary/proposed/enterprise-aggregates.yaml](../../../backend/glossary/proposed/enterprise-aggregates.yaml)，`id=supplier_agreement`。原始快照仍在 JSON，现文件可能已按后续决定更正。
- **可复查依据**：[2026-10-16-061232-one-architecture-阶段类型与-BC-划分确认页提交.md](../../decisions/2026-10-16-061232-one-architecture-阶段类型与-BC-划分确认页提交.md) D-1：收入侧、供方侧、劳动合同分开；[NM3 已提交答复](../../decisions/decision-pages/answers/nm3.json) NM3-11；[NM3 题面](../../decisions/decision-pages/sheets/nm3.json) NM3-11：采购、租赁、分包分别称变更；[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §2.2 AGG-55。
- **实质支持及限度**：用户已定供方侧的独立业务范围；NM3-11 已提交按协议类型分别称变更，题面明确是为避免用“分包”概括全部供方协议。总称建议有以上边界支持。
- **本卡倾向（有倾向）**：推荐共用“供方协议 / SupplierAgreement”总称，保留各协议类型。理由：共用总称便于查找，按类型命名可避免把采购和租赁误叫分包。
- **已登记重名／近义核对**：与已登记 `agreement`（协议总称）作上下位关系；与 `subcontract_change`、`purchase_modification`、`lease_modification` 分立，不合并成一种分包变更。
- **尚缺什么**：共同字段不代表六类协议条款、审批和会计处理必须完全相同；GB/T 19000 仅见草稿引用，不冒充已读标准原文。

<a id="c04"></a>

### C04 应用组织变更

- **对账**：B002 改归 C；JSON `proposed[id=apply_org_change_set].category=B`（原分类保留）。
- **原提议出处**：[backend/glossary/proposed/architecture-acl.yaml](../../../backend/glossary/proposed/architecture-acl.yaml)，`id=apply_org_change_set`。原始快照仍在 JSON，现文件可能已按后续决定更正。
- **可复查依据**：[ADR-0055](../../adr/ADR-0055.md) 决定2、3.3、6；[ADR-0039](../../adr/ADR-0039.md) 决定2.1–3（原方案，已被部分取代）。
- **实质支持及限度**：ADR-0039 原来规定唯一 api 入口及变更集幂等；ADR-0055 决定6明确撤销同步拆分，决定2改由人事命令维护。这里只建议处理这个旧入口，不设计新的安全或人事行为。
- **本卡倾向（有倾向）**：推荐按原同步用途退役“应用组织变更 / ApplyOrgChangeSet”，人事操作使用各自明确的命令。理由：已采纳路线撤销外部组织同步，继续保留该入口会混淆数据来源。
- **已登记重名／近义核对**：与已登记 `org_change_set`（组织变更集）、`sync_run`（同步记录）分立：一个是入口、另两个是输入及运行记录；一起核查用途退出，不合并成人事业务命令。
- **尚缺什么**：仅在迁入适配器确有调用方时保留相应用途；本卡未调查代码调用方，不执行退役。

<a id="c05"></a>

### C05 待放行离职

- **对账**：B003 改归 C；JSON `proposed[id=separation_hold].category=B`（原分类保留）。
- **原提议出处**：[backend/glossary/proposed/architecture-acl.yaml](../../../backend/glossary/proposed/architecture-acl.yaml)，`id=separation_hold`。原始快照仍在 JSON，现文件可能已按后续决定更正。
- **可复查依据**：[ADR-0055](../../adr/ADR-0055.md) 决定3.2–4、6；[ADR-0028](../../adr/ADR-0028.md) 决定4、6（保留的放行与留任约束）。
- **实质支持及限度**：ADR-0055 保留管理员离职暂停、逐条放行及可用超级管理员下限，将载体改成本地离职命令；不是原稿暗示的整次同步等待。
- **本卡倾向（有倾向）**：推荐保留“待放行离职 / SeparationHold”，承接本地离职命令的逐人保护。理由：安全保护针对一个人的离职，不应停住其他人的人事办理。
- **已登记重名／近义核对**：与已登记 `separation`（离职）、`separated`（已离职）分立；与 `workforce__held_run`（被中止的同步）分立并按旧用途退出，不能把等待放行视为离职已经生效。
- **尚缺什么**：具体放行人的排除条件及零名／一名可用管理员的处置沿安全规则；本卡不调整权限。

<a id="c06"></a>

### C06 个人信息

- **对账**：B004 改归 C；JSON `proposed[id=personal_information].category=B`（原分类保留）。
- **原提议出处**：[backend/glossary/proposed/data-classification.yaml](../../../backend/glossary/proposed/data-classification.yaml)，`id=personal_information`。原始快照仍在 JSON，现文件可能已按后续决定更正。
- **可复查依据**：[ADR-0043](../../adr/ADR-0043.md) 决定1.1–3、4.2：本地转述的个人信息范围。
- **实质支持及限度**：ADR-0043 直接写出个人信息标记与访问级别是两个维度，引用《个人信息保护法》的个人信息定义，并明确去标识化仍属个人信息；本卡只据该已采纳文件，不宣称重新核验法律原文。
- **本卡倾向（有倾向）**：推荐保留“个人信息 / PersonalInformation”及 PI 标记。理由：仓库已采纳定义区分匿名化与仅去掉姓名，避免误把还能关联个人的资料当成无关资料。
- **已登记重名／近义核对**：与已登记 `employee_profile`（员工概况）分立：一个是资料分类，另一个是资料载体；也与访问级别 SENSITIVE 分立。
- **尚缺什么**：工资等具体列是否敏感个人信息在该 ADR 仍有待核项；名称认可不能代替逐列判定。

<a id="c07"></a>

### C07 敏感个人信息

- **对账**：B005 改归 C；JSON `proposed[id=sensitive_personal_information].category=B`（原分类保留）。
- **原提议出处**：[backend/glossary/proposed/data-classification.yaml](../../../backend/glossary/proposed/data-classification.yaml)，`id=sensitive_personal_information`。原始快照仍在 JSON，现文件可能已按后续决定更正。
- **可复查依据**：[ADR-0043](../../adr/ADR-0043.md) 决定1.2–3、2（旧标记 SPI）；[已登记词表（按 id 核对）](../terms.yaml) id=schedule_performance_index。
- **实质支持及限度**：原提议 sensitive_personal_information.definition 明记 P-029 已选改名，定义给出 SENSITIVE_PI；ADR-0043 的 SPI 是较早标记。词表 schedule_performance_index 可直接核对另一含义。
- **本卡倾向（有倾向）**：推荐保留“敏感个人信息 / SensitivePersonalInformation”，标记采用 SENSITIVE_PI。理由：既保留仓库所列的法律分类含义，也避免 SPI 与进度绩效指数重名。
- **已登记重名／近义核对**：是第6项个人信息的子类，分立登记；与已登记 `schedule_performance_index` 的 SPI 仅缩写撞名，分立并取消隐私标记的 SPI 候选。
- **尚缺什么**：未找到 P-029 原始答复的精确对应，故 SENSITIVE_PI 只作有碰名理由的推荐，不把原提议自述当本轮已批准；具体列的分类仍须按已定合规流程。

<a id="c08"></a>

### C08 迁入占用

- **对账**：B016 改归 C；JSON `proposed[id=migration_period_reservation].category=B`（原分类保留）。
- **原提议出处**：[backend/glossary/proposed/data-migration.yaml](../../../backend/glossary/proposed/data-migration.yaml)，`id=migration_period_reservation`。原始快照仍在 JSON，现文件可能已按后续决定更正。
- **可复查依据**：[ADR-0036](../../adr/ADR-0036.md) 决定6“已登记的占用不挡关账”及关账前置检查；[ADR-0054](../../adr/ADR-0054.md) 决定3.2：保留暂存、试提交、核对等机制。
- **实质支持及限度**：原提议定义“有未解除占用的期间不得关账”范围过宽；ADR-0036 决定6逐状态写明：已登记不挡，转提交中复查期间，关账只查提交中。
- **本卡倾向（有倾向）**：推荐保留“迁入占用 / MigrationPeriodReservation”，只有“提交中”挡关账。理由：这是已采纳 ADR 的明确条件，可避免暂存中的批次长期占住会计月份。
- **已登记重名／近义核对**：与已登记 `migration_batch`（迁入批次）分立；与额度占用也分立，此处占的是期间的处理许可，不是金额。
- **尚缺什么**：原提议与 ADR 冲突应由后续写回卡更正；本卡未改提议文件。

<a id="c09"></a>

### C09 迁入标记

- **对账**：B017 改归 C；JSON `proposed[id=migration_marker].category=B`（原分类保留）。
- **原提议出处**：[backend/glossary/proposed/data-migration.yaml](../../../backend/glossary/proposed/data-migration.yaml)，`id=migration_marker`。原始快照仍在 JSON，现文件可能已按后续决定更正。
- **可复查依据**：[ADR-0036](../../adr/ADR-0036.md) 决定4、5（总账不为迁入事实生成分录）、6；[ADR-0054](../../adr/ADR-0054.md) 决定3.2：历史事实入交易库，总账只迁期初。
- **实质支持及限度**：ADR-0036 把标记限制在事实行，不在单据表复制；其无副作用条款明确 ledger 不补分录。ADR-0054 保留这一迁入机制。
- **本卡倾向（有倾向）**：推荐保留“迁入标记 / MigrationMarker”，只放在事实行，迁入事实不补生成分录。理由：迁入历史事实与总账期初分别承接，重复补分录会改变账面金额。
- **已登记重名／近义核对**：与已登记 `business_fact`（经营事实）、`source_reference`（来源对照）、`migration_batch` 分立：标记只分原生／迁入，详细追溯用后两者。
- **尚缺什么**：此标记不能成为任意新事实免记账的开关；适用范围限已核对的迁入路径。

<a id="c10"></a>

### C10 迁入过账通道

- **对账**：B018 改归 C；JSON `proposed[id=migration_posting_channel].category=B`（原分类保留）。
- **原提议出处**：[backend/glossary/proposed/data-migration.yaml](../../../backend/glossary/proposed/data-migration.yaml)，`id=migration_posting_channel`。原始快照仍在 JSON，现文件可能已按后续决定更正。
- **可复查依据**：[ADR-0036](../../adr/ADR-0036.md) 决定1（迁入入口只能由编排调用）、5、6；[NM2 已提交答复](../../decisions/decision-pages/answers/nm2.json) NM2-19：区分记账与过账。
- **实质支持及限度**：ADR-0036 决定6指定 operations.facts、历史／开放期间、提交中占用及不发事件；决定1限定迁入编排调用。这里的“过账”是正式校验后的处理，不是泛指录入。
- **本卡倾向（有倾向）**：推荐保留专用“迁入过账通道 / MigrationPostingChannel”。理由：专用入口保留平衡和重复校验，同时容纳历史月份和无副作用要求。
- **已登记重名／近义核对**：与已登记 `business_fact` 的正常过账入口分立用途，与第9项迁入标记分立；不另建一套经营事实。
- **尚缺什么**：迁入金额核对按 ADR-0054 决定4 G-3：金额差0、行数例外逐签，不沿旧方案允许金额差签字。

<a id="c11"></a>

### C11 我方参与记录

- **对账**：B030 改归 C；JSON `proposed[id=own_participation_record].category=B`（原分类保留）。
- **原提议出处**：[backend/glossary/proposed/enterprise-aggregates.yaml](../../../backend/glossary/proposed/enterprise-aggregates.yaml)，`id=own_participation_record`。原始快照仍在 JSON，现文件可能已按后续决定更正。
- **可复查依据**：[2026-10-15-234418-one-architecture-答复复核-R1–R5-提交-已提.md](../../decisions/2026-10-15-234418-one-architecture-答复复核-R1–R5-提交-已提.md) R5：建设工程为一个对象，跨业主延续；[已登记词表（按 id 核对）](../terms.yaml) id=construction_project：内含我方参与记录；[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §2.2 AGG-33：项目ID由交付登记、我方项目“不校验”。
- **实质支持及限度**：R5 支持工程持续存在；已登记建设工程定义已含参与记录；AGG-33 给出具体项目 ID 和由交付登记的候选。多条回查关联是基于这些材料的建议，不宣称用户已定全部字段。
- **本卡倾向（有倾向）**：推荐保留“我方参与记录 / OwnParticipationRecord”，允许多条且只作回查关联。理由：建设工程可跨业主和多年延续，一个我方项目不能代替工程的全部参与历史。
- **已登记重名／近义核对**：与已登记 `construction_project`、`project` 分立，作为两者之间的参与记录；与 `project_team_member`（项目团队成员）分立，后者记录人员。
- **尚缺什么**：参与角色的具体枚举、是否记录各段起止日仍缺直接裁定；不借回查关联新增业务拦截。

<a id="c12"></a>

### C12 项目 WBS

- **对账**：B031 改归 C；JSON `proposed[id=project_wbs].category=B`（原分类保留）。
- **原提议出处**：[backend/glossary/proposed/enterprise-aggregates.yaml](../../../backend/glossary/proposed/enterprise-aggregates.yaml)，`id=project_wbs`。原始快照仍在 JSON，现文件可能已按后续决定更正。
- **可复查依据**：[2026-10-15-215632-one-architecture-WBS-从商机报价出生-到项目完.md](../../decisions/2026-10-15-215632-one-architecture-WBS-从商机报价出生-到项目完.md) 落点：项目WBS依附项目，以稳定追溯编号串联；[2026-10-16-061232-one-architecture-阶段类型与-BC-划分确认页提交.md](../../decisions/2026-10-16-061232-one-architecture-阶段类型与-BC-划分确认页提交.md) S1b、S3、S5、D-6；[已登记词表（按 id 核对）](../terms.yaml) id=wbs_node、work_package；[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §1.2 INV-41、42；§2.2 AGG-20。
- **实质支持及限度**：用户原话支持 WBS 各自依附报价／合同／项目；S5 指定合同范围挂阶段或子项。已登记 wbs_node 明确阶段、子项、工作包，AGG-20 明列每项目一棵。行业惯例是逐层分解工作；不据此认定阶段完成就是客户认可。
- **本卡倾向（有倾向）**：推荐中文“项目工作分解结构”，WBS 保留为缩写；一项目一棵树。理由：用户已定项目层次和合同范围挂接，完整中文能让人读懂树在管理什么。
- **已登记重名／近义核对**：与已登记 `wbs_node`（工作分解结构节点）、`work_package`（工作包）分立为整棵树与其中节点；不合并进 `delivery_plan`（交付计划）。
- **尚缺什么**：ProjectWbs 作为英文代码名仍是候选；节点状态及发布／作废规则沿既有决定，不由改名新增。

<a id="c13"></a>

### C13 开票与收入额度

- **对账**：B032 改归 C；JSON `proposed[id=billing_revenue_limit].category=B`（原分类保留）。
- **原提议出处**：[backend/glossary/proposed/enterprise-aggregates.yaml](../../../backend/glossary/proposed/enterprise-aggregates.yaml)，`id=billing_revenue_limit`。原始快照仍在 JSON，现文件可能已按后续决定更正。
- **可复查依据**：[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §1.2 INV-21、22；§2.2 AGG-44；[BUSINESS_RULES（老行为证据）](../../business/business-rules.md) RULE-184（老收入条款上限及待批占额证据）；[GL2 已提交答复](../../decisions/decision-pages/answers/gl2.json) B2-X06：收入确认保留规则层；[2026-10-15-233152-one-architecture-全企业业务对象确认页-11-组全.md](../../decisions/2026-10-15-233152-one-architecture-全企业业务对象确认页-11-组全.md) Q20、Q21 暂不定。
- **实质支持及限度**：AGG-44 的两条上限分别对应付款条件项和分配给承诺服务的价款；其合用载体是结构建议。仓库引用《企业会计准则第14号——收入》的主题为履约义务与交易价格分摊，只支持区分金额来源，不证明必须共用一个技术对象。
- **本卡倾向（有倾向）**：推荐保留“开票与收入额度 / BillingRevenueLimit”一个承载记录，内部两条额度分开计算。理由：按合同付款条件可开票的金额与按已承诺服务分到的收入金额，来源和使用时点不同。
- **已登记重名／近义核对**：与已登记 `payment_term_item`（付款条件项）、`performance_obligation`（履约义务，即承诺给客户的服务）、`revenue_recognition_line`（收入确认行）分立；不把额度合并成收入确认规则。
- **尚缺什么**：共用载体有草稿支持，尚非业务批准；履约义务如何划分、收入确认是否另作单据仍待定，不由本题补定。

<a id="c14"></a>

### C14 客户认可证据类型开放配置

- **对账**：B033 改归 C；JSON `proposed[id=acceptance_evidence_type_admission].category=B`（原分类保留）。
- **原提议出处**：[backend/glossary/proposed/enterprise-aggregates.yaml](../../../backend/glossary/proposed/enterprise-aggregates.yaml)，`id=acceptance_evidence_type_admission`。原始快照仍在 JSON，现文件可能已按后续决定更正。
- **可复查依据**：[ADR-0047](../../adr/ADR-0047.md) 决定2、4.4–5；[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §2.2 AGG-92；[A1 已提交答复](../../decisions/decision-pages/answers/a1.json) A1-01、A1-02。
- **实质支持及限度**：ADR-0047 明定逐类开放及非证据枚举“历史迁入”永不开放；AGG-92 给出生效日、批准人与依据文件。A1-02 只证明收入政策书面确认改由财务负责人，不自动证明各证据类型已开放。
- **本卡倾向（有倾向）**：推荐名称缩为“客户认可证据类型开放配置”，按版本逐类开放。理由：证据可留存和证据可触发收入是两件事，版本能说明某天采用了哪套许可。
- **已登记重名／近义核对**：与第28项证据类型目录分立；与已登记 `revenue_recognition`（收入确认规则）分立，配置只决定准入。
- **尚缺什么**：ADR-0047／0049 的旧审计师闸门与后续 A1、聚合稿的财务负责人表述尚有文字接缝；只推荐配置结构及命名，不宣告闸门已解除。

<a id="c15"></a>

### C15 发票入账占用

- **对账**：B034 改归 C；JSON `proposed[id=invoice_posting_reservation].category=B`（原分类保留）。
- **原提议出处**：[backend/glossary/proposed/enterprise-aggregates.yaml](../../../backend/glossary/proposed/enterprise-aggregates.yaml)，`id=invoice_posting_reservation`。原始快照仍在 JSON，现文件可能已按后续决定更正。
- **可复查依据**：[2026-10-16-072440-one-architecture-对象完整性与方法第-4-稿确认页.md](../../decisions/2026-10-16-072440-one-architecture-对象完整性与方法第-4-稿确认页.md) D-7：进项发票与入账占用进入独立税务BC；[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §1.2 INV-74；§2.2 AGG-89、AGG-52（报销来源应付不再占用）。
- **实质支持及限度**：用户 D-7 已明确载体；INV-74 及 AGG-89 区分占用方为报销或供方来源应付，报销生成的应付只引用报销，避免再占一次。
- **本卡倾向（有倾向）**：推荐“发票入账占用 / InvoicePostingReservation”，在进项发票上统一限制累计使用金额。理由：一张发票可能分次使用，统一余额才能拦住不同来源重复用票。
- **已登记重名／近义核对**：作为已登记 `input_invoice` 的内部占用记录分立；与第16、17项往来余额核销占用分立。
- **尚缺什么**：可入账金额的含税／不含税口径与冲销释放时点仍待规格，不把防重原则扩写成税务政策。

<a id="c16"></a>

### C16 核销占用

- **对账**：B035 改归 C；JSON `proposed[id=receivables_collection__cash_application_reservation].category=B`（原分类保留）。
- **原提议出处**：[backend/glossary/proposed/enterprise-aggregates.yaml](../../../backend/glossary/proposed/enterprise-aggregates.yaml)，`id=receivables_collection__cash_application_reservation`。原始快照仍在 JSON，现文件可能已按后续决定更正。
- **可复查依据**：[GL2 已提交答复](../../decisions/decision-pages/answers/gl2.json) B2-X04、05：应收款项及回款单；[NM2 已提交答复](../../decisions/decision-pages/answers/nm2.json) NM2-12：回款单只记已到账款项；[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §1.2 INV-77、78；§2.2 AGG-49、50。
- **实质支持及限度**：草稿在应收款项上保存核销占用，由回款分配行引用，金额合计不超未结余额。NM2-12 排除了把尚未兑付票据无条件当到账款。
- **本卡倾向（有倾向）**：推荐加方向限定为“应收核销占用 / ReceivableCashApplicationReservation”。理由：先占后转记录可避免两笔收款同时抵同一份欠款，方向前缀也更清楚。
- **已登记重名／近义核对**：与第17项同名提议分立为应收／应付；与已登记 `cash_application`（收款核销）、`receipt_allocation`（回款分配）分立为占用、动作、分配明细；不并入 `ar_written_off`（无法收回的坏账核销）。
- **尚缺什么**：占用失败的回滚、票据兑付后核销、退款更正另走规格；本题不改既有坏账处理。

<a id="c17"></a>

### C17 核销占用

- **对账**：B036 改归 C；JSON `proposed[id=payables_payment__cash_application_reservation].category=B`（原分类保留）。
- **原提议出处**：[backend/glossary/proposed/enterprise-aggregates.yaml](../../../backend/glossary/proposed/enterprise-aggregates.yaml)，`id=payables_payment__cash_application_reservation`。原始快照仍在 JSON，现文件可能已按后续决定更正。
- **可复查依据**：[GL2 已提交答复](../../decisions/decision-pages/answers/gl2.json) B2-X04、05；[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §1.2 INV-77a、79；§2.2 AGG-52、53：付款成功后占用转核销。
- **实质支持及限度**：AGG-53 明确付款发起时占用应付余额，付款成功由应付侧把占用转为核销、减少未结余额；不是发起即核销。
- **本卡倾向（有倾向）**：推荐加方向限定为“应付核销占用 / PayableCashApplicationReservation”。理由：付款发起和成功不是同一时点，先占应付余额可避免并发超付。
- **已登记重名／近义核对**：与第16项分立，各在所属业务范围使用；与已登记 `payable`（应付款项）、`cash_payment`（付款单）分立为欠款、付款和预占记录，不复用应收侧核销命令。
- **尚缺什么**：失败释放、成功后退票的更正及事件顺序沿 AGG-52／53 后续规格，不以名称决定事务边界。

<a id="c18"></a>

### C18 供方协议履约额度

- **对账**：B037 改归 C；JSON `proposed[id=supplier_agreement_performance_limit].category=B`（原分类保留）。
- **原提议出处**：[backend/glossary/proposed/enterprise-aggregates.yaml](../../../backend/glossary/proposed/enterprise-aggregates.yaml)，`id=supplier_agreement_performance_limit`。原始快照仍在 JSON，现文件可能已按后续决定更正。
- **可复查依据**：[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §1.2 INV-82；§2.2 AGG-57、58；[NM3 已提交答复](../../decisions/decision-pages/answers/nm3.json) NM3-11：变更按协议类型命名。
- **实质支持及限度**：INV-82 明确协议金额含已生效变更；AGG-58 明列协议金额和已确认累计。这是有草稿依据的累计限制，不是会计准则规定系统必须建此对象。
- **本卡倾向（有倾向）**：推荐保留“供方协议履约额度 / SupplierAgreementPerformanceLimit”，只用于有金额上限的协议。理由：对方完成工作金额与付款金额应分别控制，已生效变更才调整前者上限。
- **已登记重名／近义核对**：与已登记 `supplier_performance_confirmation_statement`（供方履约确认单）、第3项供方协议分立：分别是一次确认、原协议、累计额度；不合并为付款额度。
- **尚缺什么**：仅单价、无总额上限的供方协议如何限制尚无直接证据；不得凭空补总额。

<a id="c19"></a>

### C19 分包变更已生效

- **对账**：B038 改归 C；JSON `proposed[id=subcontract_change_activated].category=B`（原分类保留）。
- **原提议出处**：[backend/glossary/proposed/enterprise-aggregates.yaml](../../../backend/glossary/proposed/enterprise-aggregates.yaml)，`id=subcontract_change_activated`。原始快照仍在 JSON，现文件可能已按后续决定更正。
- **可复查依据**：[NM3 已提交答复](../../decisions/decision-pages/answers/nm3.json) NM3-11；[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §1.2 INV-16b；§2.2 AGG-56；[N4 动词与状态候选及 NM2 定稿](n4-verbs-states.md) §2.2 V008、V014；§4.2 不及物生效事件模板。
- **实质支持及限度**：AGG-56 以生效变更更新 AGG-58；N4 说明 Activated 偏启用，生效用 BecameEffective 是待审词形。新英文是设计建议，不是用户已确认。
- **本卡倾向（有倾向）**：推荐保留中文“分包变更已生效”，英文候选 SubcontractChangeBecameEffective。理由：批准和生效可能有时间差，事件应表达已经生效的事实。
- **已登记重名／近义核对**：与已登记 `subcontract_change`（分包变更对象）、`change_order_approved`（客户侧变更批准事件）分立；采购、租赁变更按类型分立。
- **尚缺什么**：具体生效时刻是否即批准时刻须依据协议和流程；本题不默认两者永远相同。

<a id="c20"></a>

### C20 人工成本分配结果

- **对账**：B039 改归 C；JSON `proposed[id=labor_cost_allocation_result].category=B`（原分类保留）。
- **原提议出处**：[backend/glossary/proposed/enterprise-aggregates.yaml](../../../backend/glossary/proposed/enterprise-aggregates.yaml)，`id=labor_cost_allocation_result`。原始快照仍在 JSON，现文件可能已按后续决定更正。
- **可复查依据**：[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §1.2 INV-89；§2.2 AGG-41：每期间一次有效，冲销后失效计；[已登记词表（按 id 核对）](../terms.yaml) id=allocation_result；[NM2 题面](../../decisions/decision-pages/sheets/nm2.json) NM2-04：方法与一次结果分开。
- **实质支持及限度**：AGG-41 已从早期“结果”调整为运行单据，列试算、确认、作废及冲销；提议 id 仍叫 result，需把过程与结果说清。“每期间一次有效”有草稿具体位置。
- **本卡倾向（有倾向）**：推荐保留“人工成本分配结果 / LaborCostAllocationResult”，与分配运行记录分开。理由：一次运行可有试算、更正和失效，结果是最终分到各项目的金额。
- **已登记重名／近义核对**：与已登记 `allocation_result`（间接费用分摊结果）分立，避免把直接人工与间接费用混在一起；与 AGG-41“人工成本分配运行”分立为结果和处理过程。
- **尚缺什么**：按法人是否各一份、跨法人如何分配仍待范围设计；不拿《企业会计准则第1号——存货》作为“一月一份”或所有人工成本均可资本化的依据。

<a id="c21"></a>

### C21 收付权责科目映射表

- **对账**：B040 改归 C；JSON `proposed[id=cash_accrual_account_mapping].category=B`（原分类保留）。
- **原提议出处**：[backend/glossary/proposed/enterprise-aggregates.yaml](../../../backend/glossary/proposed/enterprise-aggregates.yaml)，`id=cash_accrual_account_mapping`。原始快照仍在 JSON，现文件可能已按后续决定更正。
- **可复查依据**：[ADR-0022](../../adr/ADR-0022.md) 决定2：两张科目表、版本化收付科目↔权责科目映射；[已登记词表（按 id 核对）](../terms.yaml) id=accounting_mapping_rule_set、chart_of_accounts_version；[NM2 已提交答复](../../decisions/decision-pages/answers/nm2.json) NM2-22。
- **实质支持及限度**：ADR-0022 具体规定两账各一张科目表及映射版本；现词表核算映射规则定义是“业务事件→凭证模板”。收付口径是本企业已定的开票确认等口径，不在本题偷换为纯粹现金到账记账。
- **本卡倾向（有倾向）**：推荐保留“收付权责科目映射表 / CashAccrualAccountMapping”，两种映射分立。理由：科目对科目回答两个账簿如何对应，事项对凭证回答一笔业务如何记账。
- **已登记重名／近义核对**：与已登记 `accounting_mapping_rule_set`（核算映射规则）分立；与 `chart_of_accounts_version`（科目表版本）分立为对应关系和科目目录。
- **尚缺什么**：具体一对多映射、对账类别及版本补缺仍由核算设计落实；中文叫法由用户直接确定，不转财务定名。

<a id="c22"></a>

### C22 控制科目授权表

- **对账**：B041 改归 C；JSON `proposed[id=control_account_authorization].category=B`（原分类保留）。
- **原提议出处**：[backend/glossary/proposed/enterprise-aggregates.yaml](../../../backend/glossary/proposed/enterprise-aggregates.yaml)，`id=control_account_authorization`。原始快照仍在 JSON，现文件可能已按后续决定更正。
- **可复查依据**：[ADR-0022](../../adr/ADR-0022.md) 决定3“控制科目”及来源通道授权；[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §2.2 AGG-94；[NM2 已提交答复](../../decisions/decision-pages/answers/nm2.json) NM2-22。
- **实质支持及限度**：ADR-0022 明列应收、应付、预付、合同资产等控制科目及事实类型＋来源通道授权；不按业务模块直接授权。AGG-94 补充版本化配置载体。
- **本卡倾向（有倾向）**：推荐保留“控制科目授权表 / ControlAccountAuthorization”，按科目、事实类型、入口共同授权。理由：只说明能用哪个科目还不够，还须限制什么业务经什么入口写入。
- **已登记重名／近义核对**：与已登记 `account`（会计科目）、`chart_of_accounts_version` 分立为授权配置与目录；与 `controlled_manual_voucher`（受控手工凭证）分立，后者是受授权约束的一张凭证。
- **尚缺什么**：“控制科目”是该已采纳 ADR 使用的内部核算称呼，本卡不编造准则定义或条款号；具体授权行、批准与版本生效仍属配置治理。

<a id="c23"></a>

### C23 无异议已声明

- **对账**：B042 改归 C；JSON `proposed[id=no_objection_declared].category=B`（原分类保留）。
- **原提议出处**：[backend/glossary/proposed/enterprise-aggregates.yaml](../../../backend/glossary/proposed/enterprise-aggregates.yaml)，`id=no_objection_declared`。原始快照仍在 JSON，现文件可能已按后续决定更正。
- **可复查依据**：[ADR-0049](../../adr/ADR-0049.md) 决定1、2、5；[已登记词表（按 id 核对）](../terms.yaml) id=no_objection_declaration、deemed_acceptance。
- **实质支持及限度**：ADR-0049 已明确交付侧记声明、送达和退回；收入侧结合期满及未退回条件认定，不在交付物上设视为同意状态。
- **本卡倾向（有倾向）**：推荐保留“无异议已声明 / NoObjectionDeclared”，仅表示项目经理已作声明。理由：项目经理的陈述与客户同意是不同事实，合同条件尚需单独核对。
- **已登记重名／近义核对**：与已登记 `no_objection_declaration`（无异议声明材料）分立为事件和材料；与 `deemed_acceptance`（合同条件满足后视为客户同意）分立。
- **尚缺什么**：本题不代替收入确认审批或证明合同条款可执行；事件含义已有 ADR 支持，只需确认命名处理。

<a id="c24"></a>

### C24 设计变更主张状态

- **对账**：B043 改归 C；JSON `proposed[id=design_change_claim_status].category=B`（原分类保留）。
- **原提议出处**：[backend/glossary/proposed/enterprise-aggregates.yaml](../../../backend/glossary/proposed/enterprise-aggregates.yaml)，`id=design_change_claim_status`。原始快照仍在 JSON，现文件可能已按后续决定更正。
- **可复查依据**：[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §2.2 AGG-30；[NM3 已提交答复](../../decisions/decision-pages/answers/nm3.json) NM3-01：设计变更名称已提交认可；[N4 动词与状态候选及 NM2 定稿](n4-verbs-states.md) §4.3“标记待主张”不表示已经主张。
- **实质支持及限度**：AGG-30 明列主张标记及合同侧变更、索赔的分工；NM3-01 的设计变更认可未批准所有状态取值。N4 仅支持动词边界。
- **本卡倾向（有倾向）**：推荐保留“设计变更主张状态 / DesignChangeClaimStatus”，限定为是否准备向客户提出要求。理由：交付侧是否准备提出，与合同侧已经提出了什么要求，是不同阶段的信息。
- **已登记重名／近义核对**：与已登记 `design_change`（设计变更）作状态关系，分立于 `change_order`（合同变更）、`claim`（索赔，即要求对方承担价款或责任）、`claim_asserted`（索赔通知）。
- **尚缺什么**：只有待主张／不主张会不会缺少转交完成标识，仍须流程验证；不据现有字段暗加“已主张”或反向读取合同。

<a id="c25"></a>

### C25 认可日

- **对账**：B044 改归 C；JSON `proposed[id=acceptance_date].category=B`（原分类保留）。
- **原提议出处**：[backend/glossary/proposed/enterprise-aggregates.yaml](../../../backend/glossary/proposed/enterprise-aggregates.yaml)，`id=acceptance_date`。原始快照仍在 JSON，现文件可能已按后续决定更正。
- **可复查依据**：[ADR-0047](../../adr/ADR-0047.md) 决定2表、3.1–4；[ADR-0049](../../adr/ADR-0049.md) 决定2、5：视为同意取期满日；[GL2 已提交答复](../../decisions/decision-pages/answers/gl2.json) B2-X08：认可与验收分工；[A1 已提交答复](../../decisions/decision-pages/answers/a1.json) A1-01。
- **实质支持及限度**：ADR-0047 逐类列签字日、签收日、客户签认日，邮件进入我方邮箱日和未签认纪要发出日等格子仍带建议；视为同意的期满日已定。命名建议不把这些“建议”一并转正。
- **本卡倾向（有倾向）**：推荐保留“认可日 / AcceptanceDate”，按证据类型取日，不取系统录入日。理由：收入属于哪个月要有客户认可的依据，录入时间不能替代该业务日期。
- **已登记重名／近义核对**：与已登记 `delivery_record`（送达记录）中的送达日、`milestone_accepted`（验收通过）及第41项快照日分立；认可事实与质量验收已由 GL2 区分。
- **尚缺什么**：邮件、未签认纪要、检查报告的日期依据及开放条件仍有待核；多证据取最终触发证据，迟到落账按既有期间更正规则。

<a id="c26"></a>

### C26 审查期

- **对账**：B045 改归 C；JSON `proposed[id=review_period].category=B`（原分类保留）。
- **原提议出处**：[backend/glossary/proposed/enterprise-aggregates.yaml](../../../backend/glossary/proposed/enterprise-aggregates.yaml)，`id=review_period`。原始快照仍在 JSON，现文件可能已按后续决定更正。
- **可复查依据**：[ADR-0047](../../adr/ADR-0047.md) 决定5.1–3、5.5；待核 F-3、F-4、F-8；[2026-10-16-162821-one-architecture-流程样板页提交-三组整体均-符合.md](../../decisions/2026-10-16-162821-one-architecture-流程样板页提交-三组整体均-符合.md) 未约定审查期时催签与预警、不自动认可。
- **实质支持及限度**：ADR-0047 要求合同已有约定及期限参数，退回修改后从新送达日重算；用户后续明确没约定时不自动认可。仓库引用 GF-2015-0209 的合同审查主题，但 ADR 自述未逐字核对范本，不在本卡补造条款号。
- **本卡倾向（有倾向）**：推荐保留“审查期 / ReviewPeriod”，逐合同登记并按每轮送达起算。理由：合同是否约定期限和届满后视为同意，不能用全公司统一天数替代。
- **已登记重名／近义核对**：与已登记 `defects_liability_period`（缺陷责任期，即交付后承担缺陷责任的期间）分立；与第27项期满记录分立为参数与计算留痕。
- **尚缺什么**：期限单位、起算规则、末日是否顺延、送达如何证明尚须按合同核对；名称题不代替合同效力判断。

<a id="c27"></a>

### C27 期满记录

- **对账**：B046 改归 C；JSON `proposed[id=expiry_record].category=B`（原分类保留）。
- **原提议出处**：[backend/glossary/proposed/enterprise-aggregates.yaml](../../../backend/glossary/proposed/enterprise-aggregates.yaml)，`id=expiry_record`。原始快照仍在 JSON，现文件可能已按后续决定更正。
- **可复查依据**：[ADR-0047](../../adr/ADR-0047.md) 决定5.3：合同条款引用、起算日、期限单位、末日顺延、无异议检索；[ADR-0049](../../adr/ADR-0049.md) 决定2、4、5。
- **实质支持及限度**：ADR-0047 具体列期满证据内容，ADR-0049 指定期满计算与判定归收入侧；由此推荐可解释的记录。
- **本卡倾向（有倾向）**：推荐保留“期满记录 / ExpiryRecord”，由收入侧保存计算依据。理由：只存一个到期日不能解释为什么当天届满，也不能证明客户已经同意。
- **已登记重名／近义核对**：与已登记 `deemed_acceptance`（视为同意规则）和第23项无异议声明事件分立；与 `evidence_link_record` 分立为材料与挂接关系。
- **尚缺什么**：原提议 bc 为 delivery.acceptance，计算归 receivables.wip：记录所有权及期满待办载体仍待 Q20，不以本卡直接搬迁；期满本身不等于视为同意。

<a id="c28"></a>

### C28 客户认可证据类型

- **对账**：B047 改归 C；JSON `proposed[id=acceptance_evidence_type].category=B`（原分类保留）。
- **原提议出处**：[backend/glossary/proposed/enterprise-aggregates.yaml](../../../backend/glossary/proposed/enterprise-aggregates.yaml)，`id=acceptance_evidence_type`。原始快照仍在 JSON，现文件可能已按后续决定更正。
- **可复查依据**：[ADR-0047](../../adr/ADR-0047.md) 决定2：八类及非证据枚举“历史迁入”；[GL2 已提交答复](../../decisions/decision-pages/answers/gl2.json) B2-X08。
- **实质支持及限度**：ADR-0047 的 1、2a、2b、3、4a、4b、5、6 合计八类，另有历史迁入枚举；缺须附材料即拒绝。认可不局限于验收单。
- **本卡倾向（有倾向）**：推荐保留“客户认可证据类型 / AcceptanceEvidenceType”，目录内区分八类证据与历史迁入用途。理由：既能读懂旧数据，也避免把迁入标签当成客户认可材料。
- **已登记重名／近义核对**：与第14项开放配置分立；与已登记 `acceptance_record`（验收单）、`deemed_acceptance` 分立；宽口径第48项“证据”是上位概念。
- **尚缺什么**：八类可登记不等于八类已开放；未签认纪要、报告等是否可触发及日期仍按第14、25项边界处理。

<a id="c29"></a>

### C29 往来单位银行账户已变更

- **对账**：B048 改归 C；JSON `proposed[id=counterparty_bank_account_changed].category=B`（原分类保留）。
- **原提议出处**：[backend/glossary/proposed/enterprise-aggregates.yaml](../../../backend/glossary/proposed/enterprise-aggregates.yaml)，`id=counterparty_bank_account_changed`。原始快照仍在 JSON，现文件可能已按后续决定更正。
- **可复查依据**：[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) 修订记录“跨组接缝检查”S-42①；§2.2 AGG-01、AGG-53；§1.2 INV-80；[NM2 已提交答复](../../decisions/decision-pages/answers/nm2.json) NM2-21；[NM2 题面](../../decisions/decision-pages/sheets/nm2.json) NM2-21：资金账户为现金或银行资金账户统称。
- **实质支持及限度**：AGG-01 明写“银行账户已变更（仅分析；B-10 改 api 读收款账户、只预警，不再订阅）”；原提议仍写订阅后拦截旧账户，是版本冲突。INV-80 要求收款方为已登记账户，不能据此推导账户变更后一律拦截。
- **本卡倾向（有倾向）**：推荐保留“往来单位银行账户已变更 / CounterpartyBankAccountChanged”，付款前查账户、只预警。理由：较新草稿已取消事件订阅拦截，不能沿用旧定义把变更直接等同拒付。
- **已登记重名／近义核对**：与已登记 `fund_account`（资金账户，我方持有资金的账户）分立；与 `counterparty` 分立为单位资料和变更事件，不另建同义银行变更事件。
- **尚缺什么**：只预警见结构草稿，尚未找到专门账户变更的用户原话；历史付款如何保留原账户及未登记账户的拒绝条件仍需规格。

<a id="c30"></a>

### C30 投标已中标

- **对账**：B049 改归 C；JSON `proposed[id=bid_won].category=B`（原分类保留）。
- **原提议出处**：[backend/glossary/proposed/enterprise-aggregates.yaml](../../../backend/glossary/proposed/enterprise-aggregates.yaml)，`id=bid_won`。原始快照仍在 JSON，现文件可能已按后续决定更正。
- **可复查依据**：[2026-10-15-205406-one-architecture-售前信息图确认页保存-合同与项目.md](../../decisions/2026-10-15-205406-one-architecture-售前信息图确认页保存-合同与项目.md) 回签即生效；所有竞争性采购建投标；[2026-10-16-120544-one-architecture-售前回溯确认页提交-P-1-P.md](../../decisions/2026-10-16-120544-one-architecture-售前回溯确认页提交-P-1-P.md) P-1 中标通知书为投标附件；P-2 赢单不转投入；[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §1.2 INV-27；§2.2 AGG-10、11。
- **实质支持及限度**：INV-27 明确赢单需要中标或直接委托证据；AGG-11 登记中标时必填已上传的通知书，事件供同业务范围办理赢单。
- **本卡倾向（有倾向）**：推荐保留“投标已中标 / BidWon”，以登记中标通知为依据。理由：中标是投标结果，签回合同是另一事实，二者不能靠一个事件名混同。
- **已登记重名／近义核对**：与已登记 `bid`（投标）分立为过程对象和结果事件；与 `contract_signed`（合同签署事实）及商机赢单分立，一商机可多次投标。
- **尚缺什么**：中标事件不授权自动签约、建应收或转成本；需要哪些业务动作仍沿流程规格。

<a id="c31"></a>

### C31 投标未中标

- **对账**：B050 改归 C；JSON `proposed[id=bid_lost].category=B`（原分类保留）。
- **原提议出处**：[backend/glossary/proposed/enterprise-aggregates.yaml](../../../backend/glossary/proposed/enterprise-aggregates.yaml)，`id=bid_lost`。原始快照仍在 JSON，现文件可能已按后续决定更正。
- **可复查依据**：[2026-10-15-210513-one-architecture-售前聚合与-BC-确认页保存-一.md](../../decisions/2026-10-15-210513-one-architecture-售前聚合与-BC-确认页保存-一.md) 保证金在应收里管，先做登记；[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §2.2 AGG-11：登记未中标及 P-10 保证金退还；[N4 动词与状态候选及 NM2 定稿](n4-verbs-states.md) §3.2：未中标作为落选结果区别于尚未中标。
- **实质支持及限度**：草稿 AGG-11 给出结果事件与保证金退还处理的连接；N4 明确“未中标”不能包含等待评标。是否可退仍取具体招标文件，未找到本企业统一退款时点裁定。
- **本卡倾向（有倾向）**：推荐保留“投标未中标 / BidLost”，只表示已确认落选，退还保证金另核条件。理由：未出结果不等于落选，落选也不能单独证明钱已经可以退还。
- **已登记重名／近义核对**：与第30项中标事件分立；与已登记 `bid`、`bond_and_deposit`（保证金与押金）、`retention_money_release_due`（质量保证金到期返还）分立，不能混用保证金类型。
- **尚缺什么**：未中标结果所需文件、通知来源和退还期限尚待业务定义；不把等待结果、撤回、废标和放弃合并。

<a id="c32"></a>

### C32 商机已输单

- **对账**：B051 改归 C；JSON `proposed[id=opportunity_lost].category=B`（原分类保留）。
- **原提议出处**：[backend/glossary/proposed/enterprise-aggregates.yaml](../../../backend/glossary/proposed/enterprise-aggregates.yaml)，`id=opportunity_lost`。原始快照仍在 JSON，现文件可能已按后续决定更正。
- **可复查依据**：[2026-10-15-202939-one-architecture-售前业务对象问题页保存-线索单独.md](../../decisions/2026-10-15-202939-one-architecture-售前业务对象问题页保存-线索单独.md) 原话“丢单要记原因”；[2026-10-15-205406-one-architecture-售前信息图确认页保存-合同与项目.md](../../decisions/2026-10-15-205406-one-architecture-售前信息图确认页保存-合同与项目.md) 原话“丢单记竞争对手、中标单位、中标价”；[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §2.2 AGG-10；[N4 动词与状态候选及 NM2 定稿](n4-verbs-states.md) §3.2：投标结果与商机输单对象不同。
- **实质支持及限度**：用户直接要求失败信息的记录，AGG-10 分别有登记丢单和放弃商机的命令；“输单”为与主动退出分开的命名建议，“丢单”尚非本卡已批准禁用名。
- **本卡倾向（有倾向）**：推荐“商机已输单 / OpportunityLost”，“丢单”作为旧口语检索写法。理由：用户要求保留丢单原因及竞争结果，正式名应与主动放弃清楚区分。
- **已登记重名／近义核对**：与第33项商机已放弃分立；与已登记 `win_rate`（赢单率）、`opportunity`（商机）衔接，不因有统计指标就自动批准两类如何计入分母。
- **尚缺什么**：竞争取消、客户撤回采购是否归输单或单列仍缺裁定；签约前投入转费用不能仅靠改事件名确定。

<a id="c33"></a>

### C33 商机已放弃

- **对账**：B052 改归 C；JSON `proposed[id=opportunity_abandoned].category=B`（原分类保留）。
- **原提议出处**：[backend/glossary/proposed/enterprise-aggregates.yaml](../../../backend/glossary/proposed/enterprise-aggregates.yaml)，`id=opportunity_abandoned`。原始快照仍在 JSON，现文件可能已按后续决定更正。
- **可复查依据**：[参投决策规则卡](../../business/rules/bid-decision-rules.md) RULE-414：商机负责人作参投／放弃决定，理由及评分留痕；[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §2.2 AGG-10。
- **实质支持及限度**：RULE-414 给出决定主体、必填理由、记录不可改及商机结束，是比“原草案候选”更具体的规则依据；中途退出的细节仍只见 AGG-10 和原提议。
- **本卡倾向（有倾向）**：推荐保留“商机已放弃 / OpportunityAbandoned”。理由：主动不做的决定有独立责任人与理由，不能从对手胜出结果推出来。
- **已登记重名／近义核对**：与第32项输单分立；与已登记 `migration_batch_status__abandoned`（迁入批次已放弃）是不同对象、分立，不因同名 Abandoned 合并。
- **尚缺什么**：是否任一阶段都可放弃、已有合同后如何处理仍待流程；放弃不自动成为财务费用化时点。

<a id="c34"></a>

### C34 协议已终止

- **对账**：B053 改归 C；JSON `proposed[id=agreement_terminated].category=B`（原分类保留）。
- **原提议出处**：[backend/glossary/proposed/enterprise-aggregates.yaml](../../../backend/glossary/proposed/enterprise-aggregates.yaml)，`id=agreement_terminated`。原始快照仍在 JSON，现文件可能已按后续决定更正。
- **可复查依据**：[2026-10-15-205406-one-architecture-售前信息图确认页保存-合同与项目.md](../../decisions/2026-10-15-205406-one-architecture-售前信息图确认页保存-合同与项目.md) 合同与项目分开；[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §2.2 AGG-13：已完成、已结算、已终止分列且终止原因必填；[N4 动词与状态候选及 NM2 定稿](n4-verbs-states.md) §2.3：关闭／终止／完成分立。
- **实质支持及限度**：AGG-13 的状态和终止命令支持提前结束与正常完成分开；原提议“用于停止开票”没有逐类处理已履行未开票金额的依据，因此推荐不把它固化成一刀切禁开票。
- **本卡倾向（有倾向）**：推荐保留“协议已终止 / AgreementTerminated”，与到期、完成分立。理由：提前结束不表示此前债权和结算义务消失，不能从名称推成所有开票都禁止。
- **已登记重名／近义核对**：与已登记 `agreement`、`contract_signed`、`agreement_final_account_confirmed`（协议竣工结算已确认）分立；与第39项项目终止分立，协议与项目是不同对象。
- **尚缺什么**：依法何时构成解除、终止及剩余价款怎么结算仍缺本企业规则；不凭草稿宣布法律效果或统一停票时点。

<a id="c35"></a>

### C35 协议新基线已生效

- **对账**：B054 改归 C；JSON `proposed[id=agreement_baseline_activated].category=B`（原分类保留）。
- **原提议出处**：[backend/glossary/proposed/enterprise-aggregates.yaml](../../../backend/glossary/proposed/enterprise-aggregates.yaml)，`id=agreement_baseline_activated`。原始快照仍在 JSON，现文件可能已按后续决定更正。
- **可复查依据**：[2026-10-15-205406-one-architecture-售前信息图确认页保存-合同与项目.md](../../decisions/2026-10-15-205406-one-architecture-售前信息图确认页保存-合同与项目.md) 回签即生效；[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §1.2 INV-07、08；§2.2 AGG-13 应用变更→新基线已生效；[N4 动词与状态候选及 NM2 定稿](n4-verbs-states.md) §2.2 V008、V014；§4.2 生效模板。
- **实质支持及限度**：INV-07／08 限定生效后经变更产生新基线；现有正式词条已记录当前有效版本及历史版本。Activated 改为 BecameEffective 是 N4 模板候选，不是已批准改名。
- **本卡倾向（有倾向）**：推荐保留中文，英文候选 AgreementBaselineBecameEffective，并保留新旧版本追溯。理由：事件应指已经生效的版本，不能把批准变更或启用配置当成同一件事。
- **已登记重名／近义核对**：与已登记 `contract_baseline`（合同基线）、`baseline_version`（基线版本）、`contract_modification`（合同已变更）分立：版本对象、版本号关系与生效事件各有用途。
- **尚缺什么**：何种版本变更影响哪条付款条件或承诺服务金额仍需差异规格，不能默认所有条目一起重算。

<a id="c36"></a>

### C36 协议项目关联已登记

- **对账**：B055 改归 C；JSON `proposed[id=agreement_project_linked].category=B`（原分类保留）。
- **原提议出处**：[backend/glossary/proposed/enterprise-aggregates.yaml](../../../backend/glossary/proposed/enterprise-aggregates.yaml)，`id=agreement_project_linked`。原始快照仍在 JSON，现文件可能已按后续决定更正。
- **可复查依据**：[2026-10-15-210513-one-architecture-售前聚合与-BC-确认页保存-一.md](../../decisions/2026-10-15-210513-one-architecture-售前聚合与-BC-确认页保存-一.md) 合同—项目关联由合同登记、多对多；[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §1.2 INV-09；§2.2 AGG-13；[N4 动词与状态候选及 NM2 定稿](n4-verbs-states.md) §4.2 recorded_fact、§4.3 合同—项目关联。
- **实质支持及限度**：用户原话已定主权和基数；INV-09 约束同一协议—项目有效期不重叠。原英文 Linked 没表达中文“已登记”，因此给出结构上更明确的英文候选。
- **本卡倾向（有倾向）**：推荐保留中文，英文候选 AgreementProjectLinkRegistered，登记多对多及有效期。理由：用户已定关联由合同侧登记，事件名也应表达记录已登记，不能误称合同生效。
- **已登记重名／近义核对**：与已登记 `agreement`、`project` 分立；与 `evidence_link_record`（证据关联记录）对象不同、分立，不合并为无对象的通用关联。
- **尚缺什么**：关联用于哪些成本和收入归属、历史变更如何影响已过账记录，仍须规则，不把“只带生效期”理解成只能供成本使用。

<a id="c37"></a>

### C37 项目已建立

- **对账**：B056 改归 C；JSON `proposed[id=project_established].category=B`（原分类保留）。
- **原提议出处**：[backend/glossary/proposed/enterprise-aggregates.yaml](../../../backend/glossary/proposed/enterprise-aggregates.yaml)，`id=project_established`。原始快照仍在 JSON，现文件可能已按后续决定更正。
- **可复查依据**：[2026-10-15-210513-one-architecture-售前聚合与-BC-确认页保存-一.md](../../decisions/2026-10-15-210513-one-architecture-售前聚合与-BC-确认页保存-一.md) 项目可先于合同建立；[2026-10-16-120544-one-architecture-售前回溯确认页提交-P-1-P.md](../../decisions/2026-10-16-120544-one-architecture-售前回溯确认页提交-P-1-P.md) P-2：合同签回且项目已建立后转入，赢单不转；[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §2.2 AGG-19。
- **实质支持及限度**：较早 i_sales 写赢单后转入，后续 P-2 已改为两个条件同时满足；此题只复述后续边界并确认事件命名。
- **本卡倾向（有倾向）**：推荐保留“项目已建立 / ProjectEstablished”，成本转入按双条件处理。理由：用户后续已明确赢单不转、仅建立项目也不足以转入，不能以旧表单名决定时点。
- **已登记重名／近义核对**：与已登记 `project`、`precontract_investment`（签约前投入）分立；与 `contract_signed` 分立，建立不等于签约或开始执行。
- **尚缺什么**：何种签约前投入可挂为合同履约成本仍沿 A7 与已有核算口径；本卡不把所有投标费用自动资本化。

<a id="c38"></a>

### C38 项目已结项

- **对账**：B057 改归 C；JSON `proposed[id=project_closed].category=B`（原分类保留）。
- **原提议出处**：[backend/glossary/proposed/enterprise-aggregates.yaml](../../../backend/glossary/proposed/enterprise-aggregates.yaml)，`id=project_closed`。原始快照仍在 JSON，现文件可能已按后续决定更正。
- **可复查依据**：[2026-10-15-233152-one-architecture-全企业业务对象确认页-11-组全.md](../../decisions/2026-10-15-233152-one-architecture-全企业业务对象确认页-11-组全.md) Q10：收入、分包、归档三项拒绝，工时只预警；[2026-10-16-072440-one-architecture-对象完整性与方法第-4-稿确认页.md](../../decisions/2026-10-16-072440-one-architecture-对象完整性与方法第-4-稿确认页.md) D-9：独立项目结项流程模块；[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §1.2 INV-40；§2.2 AGG-19；[GL2 已提交答复](../../decisions/decision-pages/answers/gl2.json) B2-X11：收尾与竣工结算分工。
- **实质支持及限度**：Q10 明确结项所需检查的处理强度，D-9 明确独立结项流程；INV-40 写收入覆盖或有结论、分包结算或有结论、资料归档。
- **本卡倾向（有倾向）**：推荐保留“项目已结项 / ProjectClosed”，结项与竣工结算分立。理由：一个是我方管理收尾，另一个是与客户确定价款，不能用一个状态混记。
- **已登记重名／近义核对**：与已登记 `final_account`（竣工结算）、`agreement_final_account_confirmed` 分立；与第34项协议终止、第39项项目终止分立。
- **尚缺什么**：具体“有结论”的可接受情形及财务清算单据仍待流程卡，不把命名当结项业务验收。

<a id="c39"></a>

### C39 项目已终止

- **对账**：B058 改归 C；JSON `proposed[id=project_terminated].category=B`（原分类保留）。
- **原提议出处**：[backend/glossary/proposed/enterprise-aggregates.yaml](../../../backend/glossary/proposed/enterprise-aggregates.yaml)，`id=project_terminated`。原始快照仍在 JSON，现文件可能已按后续决定更正。
- **可复查依据**：[2026-10-15-233152-one-architecture-全企业业务对象确认页-11-组全.md](../../decisions/2026-10-15-233152-one-architecture-全企业业务对象确认页-11-组全.md) Q09 项目状态机暂不定；[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §1.1 Q09；§2.2 AGG-19：终止、取消均为草稿命令。
- **实质支持及限度**：原提议用“有成本”解释终止，AGG-19 并没有给出经用户决定的取消／终止分界；N4 的动词分立也不能证明项目状态机应这样划分。
- **本卡倾向（无倾向）**：无倾向。理由：无依据，请凭业务判断。
- **已登记重名／近义核对**：与第40项项目已取消近义，分立还是合并无倾向；与已登记 `project` 是对象与事件关系，与 `terminate`（终止审批流程）对象不同，不据同形词合并。
- **尚缺什么**：缺少项目生命周期及异常结束规则；名称、分界及清算触发均待用户业务判断。本卡不以一般英语词义编出公司规则。

<a id="c40"></a>

### C40 项目已取消

- **对账**：B059 改归 C；JSON `proposed[id=project_cancelled].category=B`（原分类保留）。
- **原提议出处**：[backend/glossary/proposed/enterprise-aggregates.yaml](../../../backend/glossary/proposed/enterprise-aggregates.yaml)，`id=project_cancelled`。原始快照仍在 JSON，现文件可能已按后续决定更正。
- **可复查依据**：[2026-10-15-233152-one-architecture-全企业业务对象确认页-11-组全.md](../../decisions/2026-10-15-233152-one-architecture-全企业业务对象确认页-11-组全.md) Q09 暂不定；Q19 以合同回签为管理口径分界；[2026-10-15-234418-one-architecture-答复复核-R1–R5-提交-已提.md](../../decisions/2026-10-15-234418-one-architecture-答复复核-R1–R5-提交-已提.md) R3：立项后回签前履约投入先挂合同履约成本；[聚合草稿（仅支持建议，不是业务批准）](../../architecture/proposals/enterprise-aggregates-draft.md) §1.1 Q09；§2.2 AGG-19。
- **实质支持及限度**：原提议同时写“开工前、无成本”，但仓库允许项目建立后签回前发生履约投入；成本是否为零不足以从现材料推出取消／终止的唯一界线。
- **本卡倾向（无倾向）**：无倾向。理由：无依据，请凭业务判断。
- **已登记重名／近义核对**：与第39项近义，合并／分立无倾向；与已登记 `workforce__revoked`（已撤销）及审批取消的对象、行为不同，不直接复用。
- **尚缺什么**：与第39项在同一页相邻展示，一次判定两词关系；不提供默认推荐或预选。

<a id="c41"></a>

### C41 快照日

- **对账**：B060 改归 C；JSON `proposed[id=snapshot_date].category=B`（原分类保留）。
- **原提议出处**：[backend/glossary/proposed/fix-b2b-events.yaml](../../../backend/glossary/proposed/fix-b2b-events.yaml)，`id=snapshot_date`。原始快照仍在 JSON，现文件可能已按后续决定更正。
- **可复查依据**：[2026-10-16-134434-one-architecture-U-12-白话重问页提交-用人事.md](../../decisions/2026-10-16-134434-one-architecture-U-12-白话重问页提交-用人事.md) 原话“用人事业务生效日（入职日、调岗生效日，人事登记时填写）”；[ADR-0055](../../adr/ADR-0055.md) 决定2、6；[2026-10-16-085331-one-data-员工与岗位怎样版本化-都做维度型.md](../../decisions/2026-10-16-085331-one-data-员工与岗位怎样版本化-都做维度型.md) 被取代的“以同步观察日为生效依据”。
- **实质支持及限度**：i_dates 直接取代 i_obs；ADR-0055 决定2再次明确提前登记、到日生效、补登落真实日期。原提议把快照日同时作版本生效日已不适用。
- **本卡倾向（有倾向）**：推荐旧“快照日 / SnapshotDate”退出人事版本生效用途，观察日与业务生效日分开。理由：后续用户决定已明确取人事业务生效日，早期同步开始日口径已被取代。
- **已登记重名／近义核对**：与已登记 `org_snapshot`（组织快照）、`org_snapshot_changed`（组织快照已变化事件）分立为读取日期及数据／事件；与第46项离职观察时间分立。
- **尚缺什么**：旧快照、事件及迁入数据怎样映射日期仍需迁入规格；本卡不改事件载荷或历史记录。

<a id="c42"></a>

### C42 在职判定

- **对账**：B070 改归 C；JSON `proposed[id=employment_verdict].category=B`（原分类保留）。
- **原提议出处**：[backend/glossary/proposed/organization_workforce.yaml](../../../backend/glossary/proposed/organization_workforce.yaml)，`id=employment_verdict`。原始快照仍在 JSON，现文件可能已按后续决定更正。
- **可复查依据**：[ADR-0055](../../adr/ADR-0055.md) 决定3.1；[ADR-0033](../../adr/ADR-0033.md) 决定3.7（旧快照可用／不可用规则，已被部分取代）。
- **实质支持及限度**：ADR-0033 原来要求区分快照整体不可用和正常但未列人；ADR-0055 直接撤外部不可用分支，改查同事务本地人事记录，缺失或无效拒绝告警。
- **本卡倾向（有倾向）**：推荐按本地人事状态作判定，旧四分支仅留历史解释。理由：已采纳路线撤销外部故障前提，缺记录不能冒充在职或离职。
- **已登记重名／近义核对**：与已登记 `employment_status`（在职状态）、`employed`／`separated` 分立为判定结果及业务状态；`source_employment_state`（来源在职状态）只作迁入翻译，不合并成本地权威状态。
- **尚缺什么**：本地判定枚举的精确名称尚无专门用语裁定；本卡不把旧 EMPLOYED／SEPARATED／NOT_LISTED／SNAPSHOT_UNAVAILABLE 四值当未来必需。

<a id="c43"></a>

### C43 登录名

- **对账**：B077 改归 C；JSON `proposed[id=workforce__login_name].category=B`（原分类保留）。
- **原提议出处**：[backend/glossary/proposed/organization_workforce.yaml](../../../backend/glossary/proposed/organization_workforce.yaml)，`id=workforce__login_name`。原始快照仍在 JSON，现文件可能已按后续决定更正。
- **可复查依据**：[2026-10-15-153841-one-coordinator-员工在职判定保持现状-登录名为空.md](../../decisions/2026-10-15-153841-one-coordinator-员工在职判定保持现状-登录名为空.md) 旧决定：保持 RULE-363 口径；[身份组织规则卡（老行为证据）](../../business/rules/identity-org-rules.md) RULE-363（老代码证据）；[ADR-0055](../../adr/ADR-0055.md) 决定2：登录名判离职改本地状态；决定5.1、6；[已登记词表（按 id 核对）](../terms.yaml) id=external_login_name。
- **实质支持及限度**：先列旧用户决定，避免把上一版推荐当授权；随后已采纳 ADR-0055 明确取代运行时登录名判离职。只在迁入确需时读取旧标识。
- **本卡倾向（有倾向）**：推荐与已登记“外部登录名 / ExternalLoginName”复用同一标识含义，撤离职判定用途。理由：旧规则的登录名缺失判离职已被本地人事状态替代，不能继续藏在词条定义里。
- **已登记重名／近义核对**：与已登记 `external_login_name` 近义，倾向合并标识概念、保留来源限定用途；不把员工、人事状态或本系统账号合并。
- **尚缺什么**：external_login_name 当前登记在 kernel，原提议为 platform.integration；概念复用不授权跨边界暴露，归属修正及调用方另卡核实。

<a id="c44"></a>

### C44 员工任职快照

- **对账**：B078 改归 C；JSON `proposed[id=workforce__staff_employee].category=B`（原分类保留）。
- **原提议出处**：[backend/glossary/proposed/organization_workforce.yaml](../../../backend/glossary/proposed/organization_workforce.yaml)，`id=workforce__staff_employee`。原始快照仍在 JSON，现文件可能已按后续决定更正。
- **可复查依据**：[GL 已提交答复](../../decisions/decision-pages/answers/gl.json) B2-E02：人员上位对象、原提议改称员工任职快照；[2026-10-16-072440-one-architecture-对象完整性与方法第-4-稿确认页.md](../../decisions/2026-10-16-072440-one-architecture-对象完整性与方法第-4-稿确认页.md) INV-62：主任职不重叠、兼职不设上限；[ADR-0055](../../adr/ADR-0055.md) 决定2、5.1–2、6。
- **实质支持及限度**：GL 确认过“员工任职快照”这个旧提议叫法；后续本地人事为记录系统、撤同步。用户 INV-62 明确允许多个兼职，不能用一人一条来源记录替代任职集合。
- **本卡倾向（有倾向）**：推荐退役日常同步用途，迁入分别复用员工与任职概念。理由：员工和任职的数量不同，把二者当同一条永久记录会遮住兼职或任职变更。
- **已登记重名／近义核对**：与已登记 `employee`（员工）、`workforce__assignment`（任职）、`org_snapshot` 分立；倾向复用员工与任职而不再新增一个日常业务实体；历史快照作为组合结构留档。
- **尚缺什么**：迁入文件实际粒度及历史快照留存方式未核验；不因此修改已定员工／账号标识关系。

<a id="c45"></a>

### C45 关系

- **对账**：B079 改归 C；JSON `proposed[id=workforce__relationship].category=B`（原分类保留）。
- **原提议出处**：[backend/glossary/proposed/organization_workforce.yaml](../../../backend/glossary/proposed/organization_workforce.yaml)，`id=workforce__relationship`。原始快照仍在 JSON，现文件可能已按后续决定更正。
- **可复查依据**：[身份组织规则卡（老行为证据）](../../business/rules/identity-org-rules.md) RULE-356：两类来源关系及多个结果任取其一的旧缺陷；[已登记词表（按 id 核对）](../terms.yaml) id=org_unit_head、regional_receivables_manager；[ADR-0055](../../adr/ADR-0055.md) 决定2、5.1、6。
- **实质支持及限度**：RULE-356 逐项描述来源的部门负责人及区域应收负责人关系；ADR-0055 撤运行时同步匹配并要求本地维护。现词表已有两种职责，不需要再用裸“关系”代替。
- **本卡倾向（有倾向）**：推荐按已登记职责分别复用“部门负责人”和“区域应收负责人”，泛称“关系”仅留来源映射。理由：两种职责含义不同，来源里的数字类型只是迁入编码。
- **已登记重名／近义核对**：与已登记 `org_unit_head`、`regional_receivables_manager` 分立职责并复用目标；原“关系”如仅为二者来源汇称，不另建同义业务概念。
- **尚缺什么**：多负责人如何取舍、本地职责维护入口及授权不在本题设计；老代码按最低 ID 取一人只是问题证据，不沿用。

<a id="c46"></a>

### C46 离职观察时间

- **对账**：B080 改归 C；JSON `proposed[id=separation_observed_at].category=B`（原分类保留）。
- **原提议出处**：[backend/glossary/proposed/organization_workforce.yaml](../../../backend/glossary/proposed/organization_workforce.yaml)，`id=separation_observed_at`。原始快照仍在 JSON，现文件可能已按后续决定更正。
- **可复查依据**：[组织与任职历史规则卡（候选）](../../business/rules/workforce-history-rules.md) “离职申请的预计离职日期与离职观察时间是两件事”候选规则；[2026-10-16-134434-one-architecture-U-12-白话重问页提交-用人事.md](../../decisions/2026-10-16-134434-one-architecture-U-12-白话重问页提交-用人事.md) 人事业务生效日；修改时间由审计留痕；[ADR-0055](../../adr/ADR-0055.md) 决定2、3.5–6、6。
- **实质支持及限度**：历史卡写观察时间取变化提交时刻、挂起期间不记、首轮已离职者为未知；ADR-0055 决定3.6 要求授予关闭仍记实际收回时刻，不按补登人事日期回填。
- **本卡倾向（有倾向）**：推荐保留三种时间的区别，旧“离职观察时间 / SeparationObservedAt”限历史追溯。理由：观察、业务生效和权限实际收回可能在不同时间，不能相互回填。
- **已登记重名／近义核对**：与已登记 `expected_separation_date`（预计离职日期）分立；与人事生效日、授予关闭时刻分立，单说“离职日期 / separation_date”仍按原提议禁用歧义写法。
- **尚缺什么**：历史观察值可能由审计推出，无具体消费方前不新增列；未来人事离职生效日须有业务依据，不能从观察时间推算。

<a id="c47"></a>

### C47 法人归属对象

- **对账**：B081 改归 C；JSON `proposed[id=attribution_subject].category=B`（原分类保留）。
- **原提议出处**：[backend/glossary/proposed/organization_workforce.yaml](../../../backend/glossary/proposed/organization_workforce.yaml)，`id=attribution_subject`。原始快照仍在 JSON，现文件可能已按后续决定更正。
- **可复查依据**：[ADR-0033](../../adr/ADR-0033.md) 决定7.4（DA-5：部门或员工恰一、分别类型化ID）；[已提交Q-09的归档题面](../../decisions/decision-pages/archive/archive/PENDING.json) Q-09：劳动合同用人单位、数据范围法人、个税扣缴义务人；[2026-10-16-134134-one-architecture-ADR-待决-14-题页提交-四.md](../../decisions/2026-10-16-134134-one-architecture-ADR-待决-14-题页提交-四.md) Q-09：员工法人归属三口径分别记录、不一致时预警；[法人归属规则卡](../../business/rules/legal-entity-attribution-rules.md) RULE-385：同对象期间不重叠、更正留痕。
- **实质支持及限度**：ADR-0033 只明确技术引用形状，并不批准所有归属口径合一；Q-09 的后续原话要求三口径分开。单一归属建议只施于同一用途，具体用途对应从后续规格核对。
- **本卡倾向（有倾向）**：推荐保留“法人归属对象 / AttributionSubject”，同一归属用途内限制同一时点一个法人。理由：用户已要求三口径分开，不能再把“单一归属”扩成一个员工所有用途只能有一个法人。
- **已登记重名／近义核对**：与已登记 `legal_entity_attribution`（法人归属记录）分立为被归属对象与关系；与 `employee`、`org_unit` 分立，复用它们的类型化标识。
- **尚缺什么**：题面已核清三个口径的名称，各自字段与期间算法仍需既有业务口径卡落实；本题不把一个项目一个承担法人的 Q-06 套到员工所有用途上。

<a id="c48"></a>

### C48 证据

- **对账**：漏登31；JSON `missing_terms[30]`、`records[record=1273]`（中文及范围已定，英文待审）。
- **原提议出处**：`backend/glossary/proposed/recon-pending-renames.yaml` 中同 id；保留中文与宽范围、英文待审。
- **可复查依据**：[2026-10-16-072440-one-architecture-对象完整性与方法第-4-稿确认页.md](../../decisions/2026-10-16-072440-one-architecture-对象完整性与方法第-4-稿确认页.md) Q29：证据取宽口径；[2026-10-16-114200-one-architecture-符合性审查确认页提交-F-1-至.md](../../decisions/2026-10-16-114200-one-architecture-符合性审查确认页提交-F-1-至.md) F-1：证据留内容及采集人ID，证据关联另记挂接；[ADR-0053（本卡输入）](../../../../.codex-inputs/ADR-0053.md) 决定1.1、2.1–2、5.3：概念ID稳定、改名只改指称。
- **实质支持及限度**：reconcile missing_terms 第31项、record=1273 只证实中文及业务范围认可，英文来自草稿；因此 Evidence 是待审技术代码名，不能把旧 AcceptanceEvidence 当成已批准英文。
- **本卡倾向（有倾向）**：推荐英文候选“Evidence”，保留已定中文“证据”和宽口径定义。理由：AcceptanceEvidence 容易把概念收窄为认可或验收材料，与已定范围不符。
- **已登记重名／近义核对**：与已登记 `evidence_link_record`（证据关联记录）分立为内容和挂接关系；与 `fact_evidence`（事实证据）、`signature_evidence`（签署证据）、`validation_evidence`（认定证据）按各自用途分立，不凭宽口径全合并；与提议 `evidence`（取证动作）分立。
- **尚缺什么**：id 仍为 acceptance_evidence，不因改名改 id；不重问中文和范围、不在本卡转正或合并其他证据条目。

## 分布、分页及检查记录

46项有倾向；第39、40项无倾向且无分界依据。会计用语14、业务含义26、技术含义8。五批主题与完整原编号见主文末尾，14＋11＋11＋4＋8=48，每批≤25。

### 已验证

- 48项主文编号、原id、原bc与开工清单逐项相同；原C3、B改归C44、漏登31英文1项全部对应。上面的48行同时给出JSON定位和B105原编号。
- 48项各有一句白话问题、类型、倾向及理由、真实存在的本地出处、已登记近义处理；46项有倾向，39／40无倾向。365个文档本地链接存在、48个逐项依据锚点完整。此为结构与引用核对，不是业务批准。
- 五批题数14、11、11、4、8，48项恰好各出现一次，均≤25。
- 3869个开工时已跟踪文件的SHA-256逐一比对，排除外部提交合入的三份文件后，仅C_CLASS_FOR_N7.md内容改变；唯一新增的任务文件是C_CLASS_BASIS.md。开工已有的.codex-inputs/保留，未修改其他源文件、断言、验收测试、生成器或配置。`git diff --check`通过。
- 两份未提交交付文档另经gitleaks定向扫描，无发现；全历史扫描不替代这项检查。
- 默认预检中的生成可复现、旧系统命名、文案、脚本测试、后端词表测试、发布脚本、shellcheck及全历史gitleaks通过。后端词表测试21项通过；前端脚本测试37项通过。以上不代表业务场景验收。

### 运行期间外部更新

本会话开工及两次预检启动时HEAD为`3d8bb376`，收尾时发现HEAD已由外部更新为`37a8342a`（`merge: origin/main`）。`git diff 3d8bb376 HEAD`只有`docs/rules/methods.md`、`docs/templates/WORK_CARD.md`、`scripts/hooks/codex_stdin_gate.py`三份文件；逐字节与当前HEAD一致，不是本会话的未提交修改。本会话未执行merge、commit或push，也未回退这些更新。已读新§5的离线约束，所用pnpm离线及禁自动安装参数与之相符。

下面预检是启动时快照的实际结果，不能当作最终HEAD的完整验证；最终工作区`git status --short`只有允许的两份任务文档及开工已有的.codex-inputs/。本次与外部已提交更新分开核对，未把后者误算为本卡越界修改。

### 未运行、未完成与门禁结果

**未达到0 FAIL，不算交付门禁完成。** 默认`origin/main`基线含本分支继承的词表改动；另按`HEAD`检查本次文档差异，仍被现行relevant_change规则判为需完整前端验证，不能把它称为文档独立通过。

两次运行的完整前端验证均未自然结束，后由本会话主动中止，前端步骤与工具进程返回130；默认日志保留`9 PASS、1 FAIL、8 SKIP`，HEAD日志保留`5 PASS、1 FAIL、10 SKIP`。默认运行的生成代码、类型、lint、结构、文案、脚本测试、前端测试八个子项已返回通过，但build子项没有返回结果。HEAD运行中止时的子项返回程度更少，以原日志为准。本轮未取得build最终错误输出，**不能据旧报告断言这次是Taro、原生依赖或环境错误**。未改构建脚本绕过，也未以SKIP代替该FAIL。

未运行完整后端套件、容器集成、生产或业务端到端验收；本卡没有另列可执行验收测试。没有联网、下载包、起容器、提交或推送。所有核对及预检日志在`/tmp/nam-1017-glo-10/`，不纳入仓库。

每项SKIP理由：迁移及Flyway、系统作业主体目录、验收目录、数据库脚本均未改；没有测试改动需断言漂移检查；没有后端守护或架构清单改动；SQL Server集成所测对象未改。默认后端全套按脚本默认不运行，未改模块代码且本卡禁止容器。HEAD额外跳过构建输入登记自测和词表专门测试，因为本次未改backend/frontend/scripts及terms.yaml、bcs.yaml、backend/glossary；完整前端验证仍被执行且记FAIL，不隐去。脚本原文的容器提示不是本卡的执行授权。

### 预检命令与离线限制

两次分别运行`scripts/precheck.sh`和`scripts/precheck.sh HEAD`。只在进程环境使用已安装Python3.12、Node24、Java25；`MAVEN_ARGS=-o`、`COREPACK_ENABLE_NETWORK=0`、`npm_config_offline=true`，临时pnpm入口为真实已安装pnpm追加`--offline --config.verify-deps-before-run=false --config.manage-package-manager-versions=false`。没有安装或替代测试命令，没有改仓库配置。

### 默认origin/main预检输出

```text
precheck: base origin/main (a6621afa), HEAD 3d8bb376, 48 changed files (16 uncommitted)

PASS  generated docs (build_all --check)     review_revisions: 0 个问题
PASS  legacy system name                     check_legacy_name: 无违规
SKIP  migrations immutable                   no migration, marker or Flyway setting changed
SKIP  system actors append-only              system-actors.txt and its script did not change
SKIP  acceptance guard                       no acceptance test changed
SKIP  database scripts aligned               no database script or migration changed
PASS  docs read by builds are registered     ok
SKIP  assertion drift (warning only)         no test file changed
PASS  glossary: frontend check:copy          copy glossary: 95 files, every Chinese text is built from registered terms and the allow l
PASS  glossary: frontend test:scripts        ℹ duration_ms 2872.046
PASS  glossary: backend term tests             size (term round3_pending__size) -> com.forcitis.one.platform.integration.api.ArchivedOb
SKIP  guards: backend guard tests            no guard test or architecture list changed
PASS  release scripts self-test              deploy_gate.py self-test passed
PASS  shellcheck scripts/release             ok
PASS  gitleaks (full history)                2948 commits scanned; no leaks
      note: uncommitted files are not in history; the pre-commit hook scans them on commit
SKIP  backend tests                          not run by default; no module code changed (shared inputs, resources, build or architecture lists)
      run: (cd backend && ./mvnw -q test-compile surefire:test -Dtest='ArchitectureRulesTest,ModularityTest,ModuleDependencyTest,NamingSurfaceTest,GlossaryTest,DdlNamingTest,LegacyShapeTest,TimeSourceGuardTest,TestIsolationGuardTest' -Dsurefire.failIfNoSpecifiedTests=false)
      before delivery run everything: scripts/ci/backend_verify.sh (Testcontainers: ask the user before starting containers)
FAIL  frontend verify (pnpm verify)          exit 130 — last lines:
      | 
      | 
      |  Test Files  6 passed (6)
      |       Tests  48 passed (48)
      |    Start at  16:24:24
      |    Duration  1.11s (environment 46%, tests 19%, setup 15%, transform 14%, import 6%)
      | 
      | $ vitest run
      | 
      |  RUN  v5.0.4 /Users/yeliang/Projects/FORCITIS/forcitis-one-psa/.claude/worktrees/codex-n12/frontend/apps/web
      | 
      | 
      |  Test Files  13 passed (13)
      |       Tests  93 passed (93)
      |    Start at  16:24:26
      |    Duration  6.56s (tests 39%, import 31%, environment 18%, setup 6%, transform 5%)
      | 
      | 
      | ::endgroup::
      | [ELIFECYCLE] Command failed.
SKIP  SQL Server integration tests           relevant_change.py: nothing the *MssqlIT tests exercise changed

SUMMARY: 9 PASS, 1 FAIL, 8 SKIP (base origin/main) — not ready
```

### HEAD文档差异预检输出

```text
precheck: base HEAD (3d8bb376), HEAD 3d8bb376, 16 changed files (16 uncommitted)

PASS  generated docs (build_all --check)     review_revisions: 0 个问题
PASS  legacy system name                     check_legacy_name: 无违规
SKIP  migrations immutable                   no migration, marker or Flyway setting changed
SKIP  system actors append-only              system-actors.txt and its script did not change
SKIP  acceptance guard                       no acceptance test changed
SKIP  database scripts aligned               no database script or migration changed
SKIP  docs read by builds are registered     no backend, frontend or script changed
SKIP  assertion drift (warning only)         no test file changed
SKIP  glossary: check:copy, test:scripts, term tests terms.yaml, bcs.yaml and backend/glossary unchanged
SKIP  guards: backend guard tests            no guard test or architecture list changed
PASS  release scripts self-test              deploy_gate.py self-test passed
PASS  shellcheck scripts/release             ok
PASS  gitleaks (full history)                2949 commits scanned; no leaks
      note: uncommitted files are not in history; the pre-commit hook scans them on commit
SKIP  backend tests                          not run by default; no module code changed (shared inputs, resources, build or architecture lists)
      run: (cd backend && ./mvnw -q test-compile surefire:test -Dtest='ArchitectureRulesTest,ModularityTest,ModuleDependencyTest,NamingSurfaceTest,GlossaryTest,DdlNamingTest,LegacyShapeTest,TimeSourceGuardTest,TestIsolationGuardTest' -Dsurefire.failIfNoSpecifiedTests=false)
      before delivery run everything: scripts/ci/backend_verify.sh (Testcontainers: ask the user before starting containers)
FAIL  frontend verify (pnpm verify)          exit 130 — last lines:
      | ℹ fail 0
      | ℹ cancelled 0
      | ℹ skipped 0
      | ℹ todo 0
      | ℹ duration_ms 4296.975708
      | 
      | ::endgroup::
      | 
      | ::group::ok   typecheck (7.4s)
      | $ pnpm -r --workspace-concurrency=1 run typecheck
      | Scope: 6 of 7 workspace projects
      | $ tsc --noEmit -p tsconfig.json
      | $ tsc --noEmit -p tsconfig.json
      | $ tsc --noEmit -p tsconfig.json
      | $ tsc --noEmit -p tsconfig.json
      | $ tsc --noEmit -p tsconfig.json
      | $ tsc --noEmit -p tsconfig.json
      | 
      | ::endgroup::
      | [ELIFECYCLE] Command failed.
SKIP  SQL Server integration tests           relevant_change.py: nothing the *MssqlIT tests exercise changed

SUMMARY: 5 PASS, 1 FAIL, 10 SKIP (base HEAD) — not ready
```
