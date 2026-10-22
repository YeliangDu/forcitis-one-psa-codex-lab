# 收入线组 NOTES（阅读提示：各节按时间追加，同一问题以后出现的小节为准——「评审改稿 · FIX04 / FIX123」优先于「统一改写」，后者优先于「铺开」与初稿各节；one-arch-revenue；小节按 closeout/CONVENTIONS.md 第 4 节；各子批原样收录，主会话条目在每节末尾）

来源批次：S1 售前合同主数据流程、S2 交付质量结项流程、S3 检查收入回款流程、D1 / D2 表级模型由 sonnet 子代理起草；D3（B-08、B-09）、Q-04、PRC-140/141 由主会话起草。


## 接缝问题

### 接缝问题·S1（流程·售前合同主数据）

1. 审批结果回调的方向（PRC-07、16、47、48、13 等）：F-2 要求聚合经 platform.approval，下游 → platform.approval 的 api 边已有（B-02、B-03、B-05 均在 §4.1）；但审批结果回调命令（如 PRC-07 的「通过 / 驳回报价评审」）由平台调用聚合所在 BC 的钩子，写成 `mode: api, direction: in, bc: platform.approval` 会违反规则 2（api 只能下游调上游）。本片段只在发起审批处写 `api out platform.approval`，回调不写 cross_bc。依据：CONVENTIONS §2 规则 2；聚合稿 §4.1 B-29 行「审批钩子由本模块实现」。需 one-architecture 裁定回调的表示法。
2. PRC-78：v1 human_note 有「延续审批」，AGG-02 引用列无 platform.approval，B-01 的上游只有 B-13、platform.audit、platform.integration（§4.1），故 PRC-78 不写 `api out platform.approval`，approval 节点只作骨架占位。选项见「待用户定」D8。
3. PRC-08（P-13 提醒）、PRC-49（P-08 提醒）、PRC-50（孤儿占用定时释放）、PRC-78（到期标记）靠定时：聚合稿 §2.2 没有任何聚合引用 platform.scheduling（STEP6A G-04，状态「未处理」），本片段不写 scheduling 的 cross_bc。
4. PRC-08 v1 notes 写「OpportunityAccepted 的订阅方 B-04、B-05、B-07 ……事件策略「转入售前投入」」，聚合稿 §2.2 AGG-10 现为 B-04、B-05，赢单不触发售前投入转入（intent 120544 P-2）。cross_bc 按聚合稿只写 B-04、B-05；v1 notes 未改（保留原样），建议 one-architecture 在合并时改正。
5. 「仅分析」事件（线索已确认、报价版本已生效、投标已递交、ClaimAsserted/ClaimSettled、Regularized/Halted、角色已合格等）订阅方只有 reporting，不写 cross_bc（reporting 无 B- 编号，且是遵奉者读模型）。若要写，需定表示法，见「格式问题」5。
6. PRC-16：STEP6A G-13（建立项目与风险交付授权之间无校验路径，状态「未处理」）影响本流程：项目可先于授权存在，靠 P-09 度量预警承担，已在 v1 notes 之外未改骨架；无须改边，仅记录。
7. G-02 已处理（F-2）：PRC-07、13、16、47、48 的 v1 步骤仍带 `gap: G-02`，按「v1 字段原样」保留，建议合并时统一去掉或标已处理。
8. G-06（REF-01 登记命令落点）：PRC-08 只读 REF-01（api out B-01），不依赖登记命令，不受影响，仅提示该读取的命令落点仍在 PRC-14。
9. G-19：PRC-47「合同—项目关联已登记 → B-07」按 §2.2 写 `event out B-07`，但聚合稿无 B-07 的反应命令（G-19 第 6 项），事件有订阅方、反应未定义。

### 接缝问题·S2（流程·交付质量结项）

1. PRC-18：AGG-21 事件 `MilestoneAccepted` 订阅方列 B-08（随 Q21），但 AGG-21 命令列只有「批准交付计划 → 交付计划已批准」，没有命令发出该事件，且 §4.1 无 B-08 → B-04 边（STEP6A G-19 表第 2 行，不加边）。依据：聚合稿 §2.2 AGG-21 行。PRC-18 不写该 cross_bc。
2. PRC-28 步 7：`AcceptanceReturned` 订阅方 B-08，订阅后「取消视为同意待办」在 AGG-44 无命令（G-19 表第 3 行）。步 7 的 cross_bc 只登记事件发出，反应命令缺在 B-08 组。请 PRC-54 所在的 B-08 组对接。
3. PRC-28 步 5、6、8：B-08 订阅的是「交付物已送达、已认可、无异议已声明」，对应 PRC-53、PRC-54、PRC-56（本组之外，B-05/B-08）；PRC-56 订阅「交付物已认可」的主语见 G-15 ②（AGG-13 内含文字写成 AGG-27 订阅），按聚合稿 §2.2 AGG-27 行「交付物已认可（B-08、B-05）」写成 B-28 向 B-05、B-08 发出的 out 事件。
4. PRC-23：聚合稿 AGG-39 事件列写「已有 TimesheetSubmitted/Approved/Returned/Rejected/Cancelled（订阅方以代码为准）」，未登记 B-xx 订阅方，只有同 BC「认定工时有效性」（PRC-91）。PRC-23 因此不写事件 cross_bc；需按已建代码核对实际订阅方后回填。
5. PRC-23 步 2：v1 的 reads 未列 AGG-19，但 Q-05（intent 134134）已把「已暂停」加进 INV-39、拒记工时；B-06 → B-04 为规划边（§4.1「已建模块的新增规划边」，到阶段再登记）。已在步 2 写 `api out B-04`，v1 reads 建议补 AGG-19。
6. PRC-38 / PRC-107：AGG-103 事件「项目复盘已发布」订阅方写「同 BC：B-04 经复盘编制人决定提炼，PRC-107」，但 PRC-107 属 B-30，B-30 不依赖 B-04，提炼是 B-04 → B-30 的 api 调用（创建知识条目，在 PRC-107 步 2），不是事件。订阅方列应改为「不订阅，api 调知识」。PRC-38 只写读 B-30 的 api，未写事件。
7. PRC-36 步 4：「项目已结项」由 AGG-19（B-04）发出，B-29 不发领域事件（§4.6）；cross_bc 的事件 out B-07 挂在该步，发布方实为 B-04，B-29 → B-07 无边，请合并时按「发布方 B-04」记，不要在 B-29 下生成事件边。
8. PRC-17：先行投入路径（风险交付授权已批准）建项目时无法校验授权（G-13，B-04 不得读 B-05），P-09 度量预警承担。触发已写为 `event in B-03 OpportunityAccepted`，先行投入的人工路径不画 cross_bc。
9. PRC-24：「项目已暂停、已恢复」订阅方「仅分析」，不写；终止、取消订阅方 B-07 已写（对应 PRC-35）。

### 接缝问题·S3（流程·检查收入回款）

1. PRC-45、44（B-26 → B-28）：B-26、B-28 无到 `platform.approval` 的边（§4.1），AGG-28 状态无审批。PRC-45 的审批节点（整改复查结论）因此按「检查负责人单级复查结论」写，由 AGG-28 的「闭合不符合项」「退回整改」命令承载，不走 platform.approval。依据：§4.1 B-26、B-28 上游列表；F-2 只补了 11 个聚合。
2. PRC-61（B-09）：同理 B-09 无到 `platform.approval` 的边，「核销坏账」是财务主管单命令，未写审批节点（该流程非人工编排，可不写）。见待用户定 3。
3. PRC-54 / PRC-53（B-08）：Q-04 倾向「不可改确认记录 + 审批中占用额度」时，收入确认审批要调 `platform.approval`。AGG-44 引用列没有它，但 B-08 → platform.approval 的边已因 AGG-45 存在，不新增边，只需补 AGG-44 引用列。PRC-54 步 3、PRC-53 步已按此写 cross_bc，随 Q-04。
4. PRC-58 / 发票已红冲 → B-09：订阅无命令（G-08 第 2 点、G-19）。cross_bc 写了 out 事件，B-09 侧反应未写；本组不补。
5. PRC-66 / 协议已结算 → B-08：订阅无命令（G-16、G-19）。cross_bc 写了 out 事件（B-05 发布、B-08 订阅，B-08 → B-05 事件边成立），B-08 侧无收入冲减命令。
6. 其余 cross_bc 方向均核对 §4.1：B-26 → B-01、B-04、B-05（api、事件）、B-13、B-28；B-08 → B-01、B-05、B-13、B-16、B-28、platform.approval、platform.integration；B-09 → B-08（事件）、B-10（事件）、B-12（事件）、B-13、B-16、B-22、B-01，均成立，无反向。
7. PRC-54 步 1 订阅「交付物已送达」（B-28 → B-08 事件）成立。读「无异议声明」按 D-8 走 api 读取，不订阅 B-08 之外的事件。

### 接缝问题·D1（表·B-01/02/03/05）

1. AGG-12 引用 AGG-103（B-04，不校验）、AGG-103 在下游；表引用只存 ID，不成边，与 §4.1 一致，无需改。
2. AGG-17 依据证据引用 AGG-38 与 AGG-19、AGG-13 引用 AGG-19/20/30/31/38 均走 B-05 → B-04/B-28 既有边，一致。
3. AGG-01 银行账户变更事件的订阅方是 B-10（P-01）；B-01 表内无 B-10 引用，一致。
4. AGG-33 的「我方参与记录」存 AGG-19 项目 ID（B-01 → B-04 方向为上游记下游，按「不校验」只存 ID），已在表里标 cross_bc；若 §4.1 后续把该处改为 api，会反向成环，须保持不校验。
5. 词表已有 `standard_cost_rate_card`、`rate_card_version` 属 bc `cost.costing`，而聚合稿把 AGG-05 放 B-02 `masterdata.catalog`；两处 bc 不一致，须 one-glossary 对齐归属。

### 接缝问题·D2（表·B-04/06/26/28/29）

1. B-06 已建只有 `cost_timesheet.timesheets` 与 `consumed_events`（V20261014140000，无后续迁移）。AGG-39 内含的工时条目、认定记录未建，片段里的 `time_entries`、`time_entry_validations` 是待建；建条目时须决定单头 `submitted_hours` 去留（有条目后它是合计，派生值不存，D1）。
2. 已建 `status` 取值为 DRAFT / SUBMITTED / RETURNED / APPROVED / REJECTED，而聚合稿 AGG-39 有「提交过账候选 → 工时已过账」「已有 TimesheetCancelled」，状态里没有已撤销、已过账；命令列也没有撤销命令。要补哪个，以 AGG-39 命令冻结（第④步）为准。
3. 已建 `project_manager_id` 注明「Phase 2 前由请求给出」；将来来源是项目负责人（AGG-19 属性版本）还是项目团队（AGG-24，Q13），随 Q13。
4. 已建 `legal_entity_id` 取提交人所属法人，Q-06 项目只有一个承担法人，两者可不同，见结构问题 S-4。
5. 聚合稿 §2.2 引用列缺项（B-04→B-13、B-28→B-13 的边已有，不新增边）：AGG-19 缺 AGG-60（承担法人）；AGG-27 缺 AGG-62（评审人、封存人）；AGG-28 缺 AGG-62（事件操作人）。
6. 「不校验」类型化 ID 三处方向与边相反，按聚合稿允许（对方在下游、只作回溯线索）：AGG-104 → AGG-105（B-04 → B-28）、AGG-28 → AGG-35（B-28 → B-26）；片段里 `cross_bc: true`，不成依赖边。
7. 视为同意期满判定在 B-08（ADR-0049）。intent 141725 要求期满记录可作诉讼证据，B-08 的片段（不在本批）须含只追加的期满判定表（期限计算快照、判定时间、哈希、人工确认人与复核人）；本批 `no_objection_declarations` 只承担项目经理的无异议声明。
8. 其余引用方向均与 §4.1 一致，未发现不一致的边。

### 接缝问题·D3（表·B-08/09，主会话）

1. `revenue_recognition_reservations`、`revenue_recognitions` 引用审批实例写成 `to: platform.approval`；CONVENTIONS 的 `references.to` 只列 AGG-NN，平台引用的写法请裁定（与 S1、S2 同类问题合并处理）。
2. 确认事实（AGG-44）与过账事实（AGG-77，finance 组 B-16）不同事务：B-08 → B-16 经事件发布登记表投递过账候选，日对账兜底；finance 组的 AGG-77 片段须能带「确认事实 ID」作来源引用（Q04_REVIEW 第 8 条）。
3. `receipt_bank_matches` 引用 AGG-88 银行流水行、`cash_receipts` 引用 AGG-87 资金账户（finance 组 B-22），已有边 B-09 → B-22（api）。
4. 收入三口径对照读模型（产值估计、权责收入、开票收入）在 `reporting`，读 B-04、B-08 与 B-16 过账事实，见 Q04_STORAGE §3。


## 结构问题

### 结构问题·S1（流程·售前合同主数据）

1. 缺命令（对照 AGG-12 已有「提交报价评审 / 通过报价评审 / 驳回报价评审」）：AGG-13（合同评审，状态「审批中」）、AGG-15（协议变更审批）、AGG-17（授权审批）、AGG-05（批准费率版本）没有「提交审批」与「审批结果回调」命令，PRC-47、48、16、13 的审批只能挂在 platform 任务步或「批准 / 生效」命令上。涉及 PRC-47、48、16、13；依据：聚合稿 §2.2 命令列；STEP6A G-02 处理意见 F-2。不自造命令。
2. PRC-50：INV-10 写「任务订单提交审批时占用」，v1 却先占用、后「提交任务订单」，且无 platform 任务步；审批挂在「提交任务订单」步上，on_reject 指向「释放框架限额」（步 3）。AGG-13 的审批本身只有 PRC-47 的合同评审一个任务步，任务订单走不走同一审批需确认。
3. PRC-16：步 3 platform 任务与步 4「批准风险交付授权」（actor 管理层）重复，PRC-07 的同类回调用「系统策略」。本片段保持 v1 的 actor（SH-M1），审批挂在步 3。
4. PRC-08 与 PRC-49 `human_orchestration: true` 但 v1 无审批任务（有外部等待、撤回、时限分支，满足 ⑥a M1）；AGG-11、AGG-16 没有审批状态，也没引用 platform.approval，按 F-2 不应自行加审批。本片段不写 approval 节点，是否补审批见 D9。
5. PRC-69：v1 无「资质核验未通过 / 准入驳回」分支，也无触发「暂停 / 停用往来单位角色」的事件（资质撤销、到期不会自动触发暂停，PRC-78 只发 `资质已撤销` / `资质已到期`，订阅方仅分析）。步 3 的 decision 用业务条件占位，见 D10。
6. 干系人表无「商务」「项目总监」「法务」：已映射到最近的 SH-E5、SH-M1、SH-E6，见 D1。
7. PRC-13 的 v1 没有「驳回 / 退回」分支，批准是聚合命令；approval 挂在「批准费率版本」步，on_reject 取 end。

### 结构问题·S2（流程·交付质量结项）

1. PRC-28：v1 的「登记提交轮次送达」「登记客户认可」两步没有任何指向证据的字段；送达回执与阶段签认作证据（用户 R-5：缺客户签认或签收）的链路在 PRC-33（采集、关联并封存证据，B-28，关联经 AGG-110）。PRC-28 与 PRC-33 的衔接未写，证据缺口恰是用户点名的主要缺口。建议在步 5（送达）、步 6（认可）、步 8（无异议声明）加 v1 已有字段 `calls: PRC-33`（回执或签认的采集、关联），收入侧（AGG-44 api 读 AGG-38，B-08 → B-28 边已有）经证据引用。本片段未改 v1 步骤，等 one-architecture 定。
2. PRC-36：步 1「封存交付物」是 AGG-27（B-28）的命令，v1 放在结项流程第 1 步；结项编排按 §4.6 只读「已封存」。建议封存归 PRC-28/PRC-33 的收尾，结项编排只读；若保留，B-29 → B-28 api 成立（方向正确），不改归属。
3. PRC-36 / REF-02：编排方不持有状态，结项检查单只经 `platform.audit` 留痕；检查拒绝项 1（收入覆盖结论载体）随 Q20，未落定前读收入确认行（影响步 2，G-14）。
4. PRC-17 / AGG-24：项目团队命令「维护项目团队成员」的位置、事件与是否决定工时范围与审批人随 Q13（G-14）；PRC-23 工时审批人暂取项目经理。若 Q13 把团队名册定为审批依据，PRC-23 的 approver 与 PRC-17 步 5 要改。
5. PRC-24：Q09 项目状态机取值仍待定（暂停期间拒记工时已答），暂停、恢复、终止、取消的入口并非总是「暂停」之后（执行中可直接终止）。decision 只按 v1 步骤顺序写了一条保守路径，状态机定后重画。

### 结构问题·S3（流程·检查收入回款）

1. PRC-53 / PRC-54 / Q-04：Q-04 倾向方案是「申请占用额度 → 审批 → 通过时转实并写不可改确认记录」，多步且有人工审批，已不是单步事件策略。建议 PRC-53 升级为人工编排流程（审批节点：收入确认审批，approver 财务负责人，单级，驳回释放占用），PRC-54 步 3 改为 `calls: PRC-53`，使「认可 / 无异议声明 / 视为同意期满」三条触发路径汇到同一审批。本稿未改 PRC-53 步结构（任务限定只增 cross_bc），PRC-54 步 3 暂内含审批节点，随 Q-04 取舍。归属不变（B-08），不改 BC 边界。
2. 聚合稿 AGG-44 仍有命令「确认视为同意期满」，与 Q-04 确认页第 2 条「聚合稿要订正」一致，随 Q-04 订正；订正后 PRC-54 步 2 的命令名要改，本稿保留 v1 原名。
3. PRC-54 步 2 与 PRC-53 路径重叠：项目经理在 B-28 登记「无异议声明」触发 PRC-53，PRC-54 步 2 又由项目经理在 AGG-44 确认期满。两条路径都以人工声明为前提（ADR-0047 第 5 条），建议核对是否同一动作的两个入口。
4. 收入口径体现：PRC-54 / PRC-53 的确认点是客户确认（认可、无异议声明、视为同意期满，证据触发）；PRC-57 开票只改写开票额度与应收，不触发收入，红冲（PRC-58）只回冲开票额度；进度估计（管理产值）没有任何流程触发收入，本组无需步骤，管理产值读模型不在本组。
5. G-16 影响 PRC-66：「编制结算书」「确认最终价款」「收入冲减」「记结算折让」均为反推，本稿 PRC-66 步 4 的差额处理未写命令。按「收入在客户确认时点」口径，结算后差额冲减的触发证据（客户签认的结算书）也应走证据触发，G-16 补命令时一并定。
6. G-17（发票入账占用）已处理，不影响本组（只涉及 PRC-87）。G-02 已处理，PRC-57 审批节点因此成立。G-08 影响 PRC-58、PRC-51、PRC-59（订阅反应），G-19 影响 PRC-54、PRC-66，已在对应步写 cross_bc，不新增命令。

### 结构问题·D1（表·B-01/02/03/05）

1. 客户分级（词表 `customer_tier`，bc masterdata.counterparty）：Q-07 计费系数由业务线 x 客户分级决定，但 §2.2 AGG-01 内含未列分级评定。暂在 AGG-01 加子表 `counterparty_customer_tier_assessments`，请 one-architecture 确认是否归 AGG-01。
2. 线索（AGG-09）可无往来单位，必须自存潜在客户名称与联系人，§2.2 内含未列。暂加子表 `lead_contacts`（SENSITIVE）。
3. 合同归档（C-4）：§2.2 AGG-13 无「归档协议」命令也无归档对象。暂在 AGG-13 内加子表 `agreement_archive_records`（原件类型、归档位置、归档日期）；借阅走 platform.approval，不建借阅表。需补命令（新用语：归档协议）并确认归 AGG-13，而非 AGG-110 证据关联或 B-29。
4. INV-16a「协议信息更正只留痕」：无命令也无聚合，按审计留痕处理，不建表；若须业务可见的更正单，须另立聚合。
5. 持证主体（AGG-02）是往来单位或我方法人二选一（引用 AGG-01 或 AGG-60）；两个可空引用加互斥约束，不拆表。
6. AGG-13 内表较多（13 张）：基线、相对方、范围、付款条件、履约义务各自追加并带取代标记。属「次级在内」，未改聚合边界；写入时事务较大，建议后续看是否拆「协议结算」子聚合。

### 结构问题·D2（表·B-04/06/26/28/29）

- S-1 每期估进度（管理产值）没有聚合承载：AGG-23 只有 ETC、EAC。用户 intent 144613/144943 已定产值与收入分开登记、不进 AGG-44。建议放 AGG-23 内含只追加子表 `progress_estimates`（已写入片段并标待裁定）；备选新增技术聚合。AGG-110 的挂接目标类型须相应加「进度估计」。
- S-2 不符合项的等级体系版本（按服务类型的版本化参考数据，INSPECTION_EVIDENCE §2.6）在 §2.2 没有聚合。建议 B-26 或 B-28 增一个「发现等级体系版本」配置聚合（每版本一聚合，D15），AGG-28 只存其版本 ID。归属由 one-architecture 定。
- S-3 词表 `effectiveness_assessment`（有效性认定）是「项目×期间批量认定」的聚合，且有 `validation_evidence`；聚合稿把「认定工时有效性」放在 AGG-39 内。片段按聚合稿处理（`time_entry_validations` 逐条目追加）；若批量认定为独立聚合，须在 B-06 另立并改 §2.2。
- S-4 Q-06 一项目一承担法人，工时按提交人所属法人记账：人员法人与项目法人不同时，成本归哪个法人、是否内部结算未定。涉及 AGG-39、AGG-41 人工成本分配、员工法人归属（Q-09 三口径）。
- S-5 证据要求 E5 须同时关联合同与提交轮次，AGG-110 内含只有证据、目标类型与目标 ID。片段 `evidence_links` 已加「提交轮次引用、合同引用」列族，超出 §2.2「内含」，请确认扩写。
- S-6 AGG-27 在片段里有 9 张子表（文件版本、内部评审、签发、提交轮次、送达、认可、声明、封存），均为同一交付物下串行追加，不改变聚合边界；仅提示体量。

### 结构问题·Q-04 与新增流程（主会话）

1. **管理产值的承载**：用户事实（144613、144943 R-9）要求每期估进度作内部产值，聚合稿无对象。D2 暂作 AGG-23 完工估算内只追加子表 `progress_estimates`，主会话采纳（不进 AGG-44），请 one-architecture 判定放 AGG-23 内还是独立聚合。
2. **AGG-44 订正**（Q04_STORAGE §4）：视为同意判定改为 AGG-44 内单独只追加的认定事实；「确认视为同意期满」命令随之改写；AGG-47 注释、N-12、Q20 行「选有状态对象须新开 ADR」改为「选 C 为细化」；补命令「占用收入额度」「释放收入额度占用」（S3 同报）。
3. **PRC-53 升级**：选 C 后确认收入需「占用 → 审批 → 转实」，单步事件策略放不下；S3 建议升为人工编排流程，PRC-54 第 3 步改为调用它，主会话同意，请定号或改类。
4. **PRC-140 合同借阅**：借阅记录暂挂 AGG-13（协议）的归档记录上；借阅频繁时会与协议本身的变更争同一乐观锁，备选为 B-05 内独立「档案借阅」聚合。请裁定。
5. **PRC-141 归档**：C-4「归档并入合同模块的档案功能」，D1 已在 AGG-13 加 `agreement_archive_records`；结项封存（PRC-36）只封存交付成果，不含合同原件。


## 新用语

### 新用语·S1（流程·售前合同主数据）

- 审批单据类型暂名（doc_type），terms.yaml 全文检索均无：报价评审单、费率版本批准单、风险交付授权审批单、合同评审单、协议变更审批单、任务订单审批单、资质延续审批单。建议统一「…审批单」，报价与合同沿用业务惯称「评审单」。使用范围：领域（platform.approval 任务类型），bc：B-02、B-03、B-05、B-01。
- 缺命令的候选名（只提议，未写进 yaml）：提交合同评审、通过合同评审、驳回合同评审（AGG-13）；提交协议变更审批（AGG-15）；提交风险交付授权审批（AGG-17）；批准费率版本的审批回调（AGG-05）。
- cross_bc 的 `bc` 对 platform 写 `platform.approval`、`platform.scheduling`（无 B- 编号），见「格式问题」2。

### 新用语·S2（流程·交付质量结项）

交 one-glossary 的单据类型暂名（`approval.doc_type`）：项目预算审批（PRC-19）、工时审批（PRC-23）、发包人资料确认（PRC-26）、交付物内部评审（PRC-28）、交付物客户认可（PRC-28）、延期申请答复（PRC-32）、结项申请（PRC-36；信息图已有「结项申请表」，请核对是否同名）。
角色用语：「项目总监」「部门负责人」在 `value-streams.v2.yaml` 的 stakeholders 无独立 SH 编号，本片段映射为 SH-M1（管理层：职能高管、区域和事业部负责人）；请 one-glossary 登记两词与 SH-M1 的对应。
命令、事件：全部取自聚合稿 §2.2，无自造。

### 新用语·S3（流程·检查收入回款）

按 terms.yaml 检索，下列在词表无命中（命中 0）。建议使用范围：领域，bc 如括号。
- 收入确认审批（单据类型，B-08）、开票申请审批（单据类型，B-08）、整改复查结论（B-26）。
- 命令缺口（不自造进 yaml）：占用收入额度、释放收入额度占用（Q-04 方案 C 需要；B-08）；编制结算书类（G-16）。
- 干系人映射用语：检查负责人、检查人员、业主代表、商务（词表已有「商务」1 处，使用范围待查）、红字信息确认。
- 管理产值、视为同意：词表无，属 ADR-0049 / 用户口径，交 one-glossary 登记。
- 「核销」与「已核销（坏账）」重名（PRC-61）v1 已交，不重复。

### 新用语·D1（表·B-01/02/03/05）

词表查不到的表名与列族（中文 / 拟英文名 / bc / 范围）：
| 中文 | 拟英文名 | bc | 使用范围 |
|---|---|---|---|
| 往来单位 | counterparty（snake 复数 counterparties） | masterdata.counterparty | 领域 |
| 往来单位版本 | counterparty_version | masterdata.counterparty | 领域 |
| 上级单位关系 | counterparty_parent_link | masterdata.counterparty | 领域 |
| 往来单位角色 | counterparty_role（词表 customer_role 是其客户特例） | masterdata.counterparty | 领域 |
| 角色准入变更 | counterparty_role_admission_change | masterdata.counterparty | 领域 |
| 客户分级评定 | customer_tier_assessment | masterdata.counterparty | 领域 |
| 联系人 | counterparty_contact | masterdata.counterparty | 领域 |
| 银行账户 | counterparty_bank_account | masterdata.counterparty | 领域 |
| 资质证书（及版本、核查） | qualification_certificate / _version / qualification_verification | masterdata.counterparty | 领域 |
| 建设工程（及版本、部位、当事方角色、我方参与记录） | construction_project / _version / _part / _party_role / _participation | masterdata.counterparty | 领域 |
| 招标项目登记（及版本）、标段 | tender_registration / _version、tender_lot | masterdata.counterparty | 领域 |
| 服务产品版本 | service_product_version（及 _phase） | masterdata.catalog | 领域 |
| 阶段类型版本、默认交付物、评审要求 | phase_type_version / _deliverable / _review_requirement | masterdata.catalog | 领域（phase_type 已有） |
| 标准成本费率表版本及条目 | standard_cost_rate_card_version / _entry（词表 bc 为 cost.costing，见接缝问题5） | masterdata.catalog | 领域 |
| 计费系数版本及条目 | billing_coefficient_version / _entry | masterdata.catalog | 领域 |
| 协议价目版本及条目 | agreement_price_list_version / _item | masterdata.catalog | 领域 |
| 市场活动、线索、线索联系人、线索状态变更 | marketing_campaign、lead、lead_contact、lead_status_change | presales.opportunity | 领域 |
| 商机团队成员、委托证据、丢单记录 | opportunity_team_member、opportunity_entrustment_evidence、opportunity_loss_record | presales.opportunity | 领域 |
| 投标、联合体协议（表）、联合体成员、资格预审结果、中标通知书、承诺快照 | bid、bid_consortium_agreement、bid_consortium_member、bid_prequalification_result、bid_award_notice、bid_commitment_snapshot（词表 proposal_commitment_snapshot 已有） | presales.opportunity | 领域 |
| 报价版本、估算条目、所用版本关联、参考关联 | quote_version、quote_estimate_item、quote_applied_version、quote_reference_link（词表 quote 已有） | presales.opportunity | 领域 |
| 独立性检查、书面同意 | independence_check、independence_check_consent | presales.opportunity | 领域 |
| 客户活动及变更 | customer_activity、customer_activity_change | presales.opportunity | 领域 |
| 协议基线 | agreement_baseline | contract.agreement | 领域 |
| 协议相对方、协议—项目关联、承诺比对、视为同意条款、发包人代表、结算书、状态变更、归档记录 | agreement_party、agreement_project_link、agreement_commitment_comparison、agreement_deemed_consent_term、agreement_owner_representative、agreement_settlement_statement、agreement_status_change、agreement_archive_record | contract.agreement | 领域 |
| 框架限额（根）、占用行 | framework_capacity（词表 framework_capacity_reservation 已有）、framework_capacity_reservation | contract.agreement | 领域 |
| 协议变更主张项、索赔认定、风险交付授权依据证据关联 | agreement_change_claimed_item、claim_determination、risk_delivery_authorization_evidence_link | contract.agreement | 领域 |
| 归档协议（命令） | archive_agreement | contract.agreement | 领域 |
| 角色准入状态、档案状态、承包链位置、我方份额 | admission_status、profile_status、contracting_chain_position、our_share_ratio | 对应 bc | 领域 |
同名异义提醒：词表 `customer`（presales.opportunity，客户档案）与 `customer_role`（masterdata.counterparty）并存，按 H1 客户是往来单位角色，presales 下 customer 应废止，请 one-glossary 登记。

### 新用语·D2（表·B-04/06/26/28/29）

使用范围均为领域；表名按 NAMING_STANDARD 为复数，下列写单数用语。词表已有可直接用：project、wbs_node、milestone、deliverable、nonconformity、timesheet、time_entry、delivery_record、rework_recognition。

| 中文 | 拟英文 | bc |
|---|---|---|
| 项目属性版本、项目来源商机、项目状态事件 | project_attribute_version、project_source_opportunity、project_status_event | delivery.execution |
| WBS 树、WBS 节点版本 | wbs_tree、wbs_node_version | delivery.execution |
| 交付计划版本、项目预算版本、项目预算明细 | delivery_plan_version、project_budget_version、project_budget_line | delivery.execution |
| 完工估算记录、进度估计（管理产值） | completion_estimate、progress_estimate | delivery.execution |
| 项目团队、项目团队成员、资源需求、资源分配 | project_team、project_team_member、resource_request、resource_assignment | delivery.execution |
| 延期申请、延期申请事件 | extension_request、extension_request_event | delivery.execution |
| 项目复盘、复盘评审、项目风险与问题、应对记录 | project_retrospective、retrospective_review、project_risk_issue、risk_issue_response | delivery.execution |
| 工时认定记录 | time_entry_validation | cost.timesheet |
| 交付物文件版本、交付物内部评审、交付物签发记录、提交轮次、客户认可记录、无异议声明、交付物封存记录 | deliverable_file_version、deliverable_internal_review、deliverable_issuance、submission_round、customer_acceptance_record、no_objection_declaration、deliverable_seal | delivery.acceptance |
| 不符合项事件、不符合项证据引用、险情告知记录 | nonconformity_event、nonconformity_evidence_reference、hazard_notification | delivery.acceptance |
| 设计变更、设计变更交付物、发包人提供资料 | design_change、design_change_deliverable、owner_provided_material | delivery.acceptance |
| 证据、证据文件、证据封存、证据完整性复核、证据保全标记、证据作废、证据关联 | evidence_item、evidence_file、evidence_seal、evidence_integrity_check、evidence_preservation_hold、evidence_voiding、evidence_link | delivery.acceptance |
| 客户满意度调查、满意度评分 | satisfaction_survey、satisfaction_score | delivery.acceptance |
| 检查计划、检查、检查项结果、检查项更正、检查人员指派、检查报告关联 | inspection_schedule、inspection、inspection_item、inspection_item_correction、inspection_assignment、inspection_report_link | delivery.inspection |
| 检查表模板版本、检查表项、外部检测、外部检测报告 | inspection_checklist_template_version、inspection_checklist_item、external_testing、external_testing_report | delivery.inspection |

另：「客户认可记录」不用词表 `acceptance_record`（验收单，kind 为 aggregate、含里程碑与最终验收），避免同名异义；词表该条与 AGG-27 内含的关系请 one-glossary 核（用 new_term_issue.py 登记）。列族英文名：内容哈希 content_hash、服务端时间 recorded_at、关联键 correlation_key、迁入来源与原始时间 migration_origin / source_recorded_at。

### 新用语·D3、Q-04、PRC-140/141（主会话）

| 中文 | 拟英文 | bc | 范围 | 说明 |
|---|---|---|---|---|
| 开票与收入额度 | BillingRevenueCapacity（表 `billing_revenue_capacities`） | receivables.wip | 领域 | AGG-44 根；避开 allowance（坏账准备）与 ledger（总账） |
| 付款条件项额度 / 履约义务额度 | PaymentTermCapacity / PerformanceObligationCapacity | receivables.wip | 领域 | |
| 开票占用 / 收入确认占用 | BillingReservation / RevenueRecognitionReservation | receivables.wip | 领域 | 与 `framework_capacity_reservation` 同族 |
| 视为同意认定 | DeemedAcceptanceDetermination | receivables.wip | 领域 | ADR-0049 |
| 收入确认事实 | RevenueRecognition（表 `revenue_recognitions`） | receivables.wip | 领域 | 词表 `revenue_recognition` 现为 business_rule，选 C 后多一个不可改记录，kind 须重审 |
| 可触发确认的证据类型版本 | RecognitionEvidenceTypeVersion | receivables.wip | 领域 | AGG-92 |
| 销项发票 / 红字信息确认 | SalesInvoice / RedLetterConfirmation | receivables.wip | 领域 | |
| 保证金与押金 / 变动 | SecurityDeposit / SecurityDepositMovement | receivables.collection | 领域 | |
| 收款银行流水匹配 | ReceiptBankMatch | receivables.collection | 领域 | |
| 产值估计 | OutputValueEstimate（D2 表名 `progress_estimates`，须统一） | delivery.execution | 领域 | 与「收入」区分使用范围 |
| 收入三口径对照 | RevenueMeasureReconciliation | reporting | 领域 | 读模型 |
| 申请借阅协议 / 登记协议借出 / 登记协议归还 / 归档协议 | RequestAgreementLoan / RecordAgreementLoan / RecordAgreementReturn / ArchiveAgreement | contract.agreement | 领域 | 命令；另有单据类型「合同借阅审批」 |

词表冲突（用 `new_term_issue.py` 登记给 one-glossary）：`progress_assessment` 已批准为 `receivables.wip` 的聚合（计量认定单），与用户事实「进度只作管理产值」不符；`tm_period_bill`、`final_account`、`interim_payment_certificate` 在词表为 `receivables.wip` 的聚合，聚合稿 B-08 无对应聚合。


## 格式问题

### 格式问题·S1（流程·售前合同主数据）

1. actor 字段：v1 的 actor 是中文角色名，CONVENTIONS §2 要求 SH- / system / platform，故直接替换；原中文名不保留（映射表见本文件开头）。如需保留追溯请增 `actor_v1` 字段。
2. cross_bc 的 `bc` 对平台模块写 `platform.approval`（取自 §4.1），CONVENTIONS 只规定 B-NN；请确认平台写法。
3. approval 位置约定：本片段把 approval 挂在「通过后才执行本步」的步骤上；有 platform 任务步的挂在任务步（PRC-07、16、47、48），无任务步的挂在被门控的命令步（PRC-13 步 1、PRC-50 步 2、PRC-78 步 3）。on_reject 为驳回后去向。需确认是否统一为「审批节点必须有独立步」。
4. `mode: api, direction: in` 的用法：下游读取上游，对上游那一步而言是「被读取」，本片段写 `direction: in`（PRC-13 生效各版本被 B-03、B-04 读取；PRC-69 角色状态被 B-10、B-11 读取）。CONVENTIONS 对 `in` 的 api 含义是「对方读取」，据此使用，请确认。
5. 「仅分析」事件的表示法见接缝 5。
6. decision.goto 可指向流程号，PRC-08 步 15 用 `goto: PRC-08` 表示「重新招标，另建投标」重入自身，请确认允许自指。
7. `otherwise → end` 在等待外部结果的步（PRC-08 步 4）表示「本次骨架到此，外部结果到达后由事件重入」，不是业务终止。
8. 决策条件里的「合同已签回」等是业务状态描述，未出现字段名（规则 1）。

### 格式问题·S2（流程·交付质量结项）

1. `actor`：v1 已有 `actor`（中文角色名），约定要求写 SH- 编号。本片段把编号写入 `actor`，v1 原文保留为 `actor_v1`。请 one-architecture 确认合并时 `actor_v1` 是丢弃还是保留。
2. `cross_bc.bc` 对平台模块写 `platform.approval`、`platform.audit`、`platform.numbering`、`platform.identity`（约定只列 B-NN）。
3. `approval` 用于非 `platform.approval` 的节点：PRC-26（资料确认）、PRC-28（内部评审、客户认可）、PRC-32（发包人答复延期），它们是聚合内裁定或外部方答复，不走审批流（G-02 已明确 AGG-21、AGG-26 之类不走）。为满足「人工编排必有 approval 节点」写入，请约定是否加 `engine: platform.approval | aggregate | external` 标识。PRC-19、23、36 为 `platform.approval`，其步 `actor` 写 `platform`。
4. `approver` 取外部方 SH-C6a（PRC-28、32）：约定写「审批人角色」，未说明可否为外部。
5. 读类步骤（`read: true`）也写了 `actor`；其跨 BC 读写 `mode: api, direction: out`。
6. 事件驱动的交接（如步 8 之后触发 PRC-53）不能用 `goto`（goto 只指流程内跳转或调用），本片段一律 `goto: end`，交接靠 `cross_bc` 事件表达。

### 格式问题·S3（流程·检查收入回款）

1. `actor` 已由 v1 中文角色名替换为 SH- 编号 / system / platform，原名映射：系统策略、系统 → system；财务、出纳、财务主管 → SH-E6；项目经理 → SH-E2；商务 → SH-E5；业主代表 → SH-C3。检查负责人 → SH-E4，检查人员 → SH-E3。`value-streams.v2.yaml` 的 stakeholders 没有「检查负责人」「检查人员」「商务」「财务主管」「出纳」，上述均为最接近的类别，需 one-architecture 确认。
2. `cross_bc.direction` 对 api：本稿统一写 `out`（本步调用上游 api，含只读）；对 event：发布写 `out`、订阅写 `in`。约定里「in 含读取」易误解，建议明确。
3. `cross_bc.bc` 遇平台模块写 `platform.approval`、`platform.integration`（约定只写 B-NN）。
4. 同一 SH 在 roles 里可有两个 as（如 SH-E6 既是 performer 又是 approver），本稿分条写，请确认允许。
5. 事件策略的 cross_bc 放在 `step.cross_bc` 下（策略只有单步）。
6. 读步骤（read: true）上也写了 cross_bc（如 PRC-41 步 1）。

### 格式问题·D1（表·B-01/02/03/05）

1. versioned 固定取值不够表达配置型版本：配置版本表（服务产品、阶段类型、报价版本）要「每版本一行、已发布不可改、只一个生效、状态可由已发布转已停用」。本稿取 append_only，费率、系数、协议价目因有「生效期不重叠」取 effective_dated。建议增 `config_version` 取值（对应 D15）。
2. sensitivity 是表级单值，而同一表常见混合敏感度（如往来单位根表含证件号）。本稿取表内最高级并在 reason 说明，列级以 data-classification 登记为准；建议约定即此。
3. 「状态可变的次级表」（付款条件项的条件状态、框架占用）用 append_only 加 reason 说明审计可还原，建议在 versioned 写法上明确。
4. references 的 `to` 对平台模块写 `platform.approval`（非 AGG-xx），请确认可接受。
5. 迁入暂存表（role: staging）本稿未建：生产自成体系、历史迁入走 platform.integration 的迁入通道，业务 BC 无暂存表。

### 格式问题·D2（表·B-04/06/26/28/29）

1. 聚合本身即一个版本（交付计划版本、项目预算版本、检查表模板版本）时，根表既是 root 又带版本语义；片段写 `role: root`、`versioned: effective_dated`。请确认是否要 `role: version` 的新取值。
2. REF-02 无聚合：写 `tables: []` 加 `no_table_reason`，约定第 3 节未覆盖。
3. 已建技术表 `consumed_events` 不属任何聚合，未入片段；约定需说明框架或技术表如何登记。
4. `versioned: append_only` 用于「草稿可改、发布后只追加」的 `project_retrospectives`，和纯追加表同值；可考虑单列 `version_chain`。
5. 约定无「不校验引用」标记，片段靠 reason 说明。
6. 敏感级别按表给初判，实际落到列时须登记 `data-classification.txt`（送达人、签认人身份快照、评语、采集人与地点等）。

### 格式问题·主会话汇总

1. **actor 与 v1 原文**：S2 把 v1 中文 actor 存为 `actor_v1`，合并时主会话对全部流程统一补了 `actor_v1`；请定合并进 v2 时是否保留。
2. **配置型版本**：`versioned` 缺「配置版本」（D15：每版本一行、只一个生效、不追溯），D1、D3 分别用了 append_only 与 effective_dated；建议加 `config_version`。
3. **平台引用与回调**：`cross_bc.bc` 与 `references.to` 都需要能写 `platform.approval` 等平台模块；审批回调方向（api in）与规则 2 冲突，请定表示法（S1、S2、D3 同报）。
4. **非平台审批节点**：PRC-26、28、32 的「审批」是聚合内裁定或外部方答复，建议 approval 加 `engine: platform.approval | aggregate | external`。
5. **表级敏感**：sensitivity 是表级单值，混合表取最高级，列级以 data-classification 为准（D1、D2 同做法）。
6. **新增流程的命令**：PRC-140、141 的命令聚合稿没有，步骤上用 `gap` 标「待补命令」，未进命令列。


## 待用户定

### 待用户定·S1（流程·售前合同主数据）

D1 角色映射：商务 → SH-E5（市场和客户经理）、项目总监 → SH-M1（管理层）、法务 → SH-E6（职能人员）。建议先用此映射；理由：干系人表不含这三者，AEC 通行做法商务属经营条线，项目总监与高管同属管理决策层，法务属职能。若要区分请新增干系人。涉及 PRC-07、16、47、48、50。
D2 报价评审（PRC-07）审批人 SH-M1、单级。建议按报价金额分级后再定多级；理由：骨架先单级，v1 与规则卡均未定。
D3 合同评审（PRC-47）审批人 SH-E6（法务）、多级（法务 → 财务 → 分管高管）。建议多级；理由：AEC 项目合同通行为法务、商务、财务会签后经分管领导批准，且 INV-06 直建例外也须经审批，可并入同一评审。
D4 协议变更审批（PRC-48）审批人 SH-M1、多级（按变更金额与工期影响分级）。建议多级；理由：GB/T 50500 工程变更与签证通行按金额分级，影响价款与基线。
D5 风险交付授权审批（PRC-16）审批人 SH-M1、单级。建议单级；理由：授权额度有限、期限短，口径与转正时点另待流程梳理（C-56）。
D6 费率版本批准（PRC-13）审批人 SH-M1（财务负责人级）、单级。建议不用 SH-E6：理由：发起人是财务经办，批准人与发起人应分离；v1 只写「须财务审定」，未定层级。
D7 任务订单审批（PRC-50）审批人 SH-M1、单级、驳回释放框架限额。建议单级；理由：框架协议已定价与限额，订单仅核对占用与范围。
D8 资质延续审批（PRC-78）：骨架占位 approver SH-M1。选项 ① 在 AGG-02 加 platform.approval（api），B-01 增加到 platform.approval 的边，层位会变，要重跑 aggregates_dep_check.py；② 延续作 B-01 内单级命令，不走 platform.approval。建议 ②；理由：AGG-02 无「审批中」状态，F-2 的适用条件是「含审批状态的聚合」，B-01 又是最上游薄主数据，不宜增边。
D9 PRC-08 与 PRC-49 是否补审批：PRC-08 建议不补（报价评审已在 PRC-07，保证金、盖章走线下，AGG-11 无审批状态）；PRC-49 建议补「索赔提交审批」（对外有法律后果与时限，AGG-16 加审批状态，B-05 → platform.approval 边已有，不增边）。理由：通行做法索赔提交前须内部审批，投标决策多在立项阶段完成。
D10 PRC-69 补「准入未通过」分支与暂停、停用的触发条件：建议暂停由 PRC-78 的「资质已撤销 / 已到期」人工触发，停用由合作终止触发，仍为人工命令；理由：两事件订阅方仅分析，无事件策略可用，自动化需新增订阅与边。

### 待用户定·S2（流程·交付质量结项）

建议项均不自行定稿，写出建议与理由。

1. 角色映射：项目总监、部门负责人是否都用 SH-M1。建议是，理由：stakeholders 无独立编号，VS-02.0 参与者只含 SH-M1 一类管理角色；否则需新增 SH 编号（回到 value-streams 改动）。涉及 PRC-19、20、24、30、36。
2. PRC-19 项目预算审批：建议项目总监（SH-M1）单级，驳回回到步 2 重新编制。理由：v1 actor 为项目总监；规则卡与 ADR 未定金额分级。是否按预算金额或项目级别分级（intent C-6 有项目级别走审批）待定。
3. PRC-23 工时审批：建议项目经理（SH-E2）单级；退回→员工修改后重提（goto 2），驳回→终结（end），可撤回。理由：v1 actor 项目经理，退回/驳回语义取自 v1 human_note。Q13 若定团队名册决定审批人需改。
4. PRC-28 客户认可：建议审批方取 SH-C6a（发包人代表，AGG-13 记发包人代表与通知联系人），而非决策人 SH-C6b。理由：书面签认通常由发包人代表出具，与证据口径（R-5）一致；客户退回后回到步 2 整改再送达。
5. PRC-28 内部评审：建议技术专家（SH-E4）单级，不通过回步 2 整改复审；签发人 SH-E7（注册执业人员，INV-47）。理由：v1 actor 与 human_note。
6. PRC-32 延期：建议发包人答复作 approval 外部节点（SH-C6a），5 天未答复由系统登记「延期视为批准」。确认此类「视为批准」仍留在 B-04（AGG-31 自身），与交付物视为同意归 B-08（ADR-0049）是两件事，不合并。
7. PRC-36 结项审批：建议项目总监（SH-M1）单级，审批通过前拒绝项不满足退回步 2。是否追加财务（SH-E6）复核（多级）待定：结项涉及收入覆盖、转费用清算（B-07），多级更稳，但 ADR-0050/0051 与 §4.6 未定审批层级。另：步 2 检查未通过，本片段写 `goto end`（条件满足后重新发起），备选是回步 1 补封存；建议前者，理由是拒绝项 2、3 的补救不一定在本流程内。
8. PRC-37 低分阈值（P-45「阈值待定」）：仍待用户定，decision 条件写了「评分低于阈值（阈值待定）」。
9. PRC-28 步 5：是否增加 `calls: PRC-33`（见结构问题 1）。建议增加，理由：用户已定缺口主要是缺客户签认或签收，优先补交付回执与阶段签认，不在流程里显式挂证据就无法度量缺口。
10. PRC-24：终止与取消是否走审批（现按非人工编排）、暂停中的项目能否结项，随 Q09。建议 Q09 定后一并决定，理由：状态机决定入口。

### 待用户定·S3（流程·检查收入回款）

1. 随 Q-04：PRC-54 / PRC-53 的收入确认审批人。建议财务负责人（SH-E6）单级审批；理由：用户口径为无法定审计、内部审计加财务负责人把关，且确认记录不可改、审批中占额度，驳回释放占用即可。
2. 视为同意期满路径的收入业务日期。建议取期满日（客户未异议的最早可确认时点）；理由：与「认可日」口径一致，期间闸门（INV-92）按此路由；PRC-53 现写业务日期 = 认可日，视为同意路径无认可日，需明确。
3. PRC-61 坏账核销是否走多级审批。建议财务主管核销后经财务负责人（或管理层）审批，金额大则多级；理由：坏账直接影响损益，需要权限分离。若采纳须新增 B-09 → platform.approval 边，这是依赖边变更，须架构裁定。
4. PRC-45 整改复查结论是否单级。建议单级（检查负责人），复查未通过自动退回整改；理由：不涉资金，且 B-26 / B-28 无 platform.approval 边（见接缝 1）。
5. PRC-57 开票申请审批：建议财务主管单级、金额超限时多级（授权额度由规则卡定）；本稿 multi_level 取 false，规则卡未定多级。理由：一般 AEC 企业按开票额度分级授权。
6. PRC-44 复核不符合项后若不成立，没有「作废」命令（AGG-28 只有关闭、让步接受）。建议登记后复核不成立走「关闭不符合项」；本稿未写该分支，待确认。
7. PRC-58 红冲时蓝字发票未入账时是否也要求红字信息确认：本稿按「已用途确认或入账才须对方确认」写（v1 desc）。

### 待用户定·D1（表·B-01/02/03/05）

1. 报价成本、毛利、费率表、计费系数、协议价目的敏感级别与可见角色（本稿 sensitivity: tbd；建议 SENSITIVE，仅报价、财务及授权人员可见，敏感权限授给具体的人，依 SESSION_PLAYBOOK §13 横切第7条）。
2. 承诺快照（含报价承诺）敏感级别：建议 INTERNAL。
3. 历史迁入：全表默认 full，无例外；请确认「线索、市场活动」等低价值历史也全迁（U-3、U-4 已定，无需再问，除非要豁免）。
4. 履约义务（Q21）与阶段类型取值（Q06）仍待既有确认页，结构不受影响。
5. 资质证书核查是否要审批（本稿不设审批）；建议不设，核查结果登记即可。

### 待用户定·D2（表·B-04/06/26/28/29）

以下建议由我按行业做法给出，未定稿。

1. 进度估计归属与流程（S-1）：建议放 AGG-23 内含，项目经理估、部门负责人复核，按月，只追加，可挂客户确认，无客户确认的标「仅内部估计」。理由：与 AGG-44 分开，满足诉讼证据要求（intent 141725）。
2. 已删工时单（老系统 57,154 张）：建议不进 `timesheets`，进迁入暂存并保留可查。理由：已建唯一约束覆盖全状态（人员，工作日），已删单会和重填单冲突，且已删不是业务事实。
3. 迁入的证据与附件：建议登记迁入批次、迁入时重算的内容哈希，原始时间由来源声明并标「迁入」，不冒充服务端采集时间。理由：老系统与 A8 无哈希与原始服务端时间，诉讼证明力靠原件保全。
4. 证据保存期（Q34）：建议合同终结后至少 10 年或应收清偿后若干年取较长者，期限配置化，诉讼与催收项目设保全标记。
5. 第三方可信时间戳：建议只对高金额项目取，金额门槛由财务与法务定。理由：法律无硬性要求，是加分项。
6. 发包人提供资料（AGG-32）：建议按证据处理，改 `append_only`。理由：它是延期与免责依据。
7. 跨法人工时成本归属（S-4）：建议成本归项目承担法人，人员法人按内部结算。理由：Q-06 把项目收入与成本固定在一个法人。
8. WBS 节点是否版本化：建议版本化（现写 `wbs_node_versions`）。理由：被工时按 ID 引用，属 D17 被引用主数据；备选是登记豁免。
9. B-29 结项检查单：建议先不建表，经 `platform.audit` 留痕，试运行后若快照超出审计条目承载再建。

### 待用户定·主会话（Q-04 与新增流程）

1. **Q-04 选 C**（不可改确认事实 + 审批中占用行，ADR-0016 决定 4 的细化），见 `Q04_STORAGE.md`；须同时让用户知情 ADR-0047 U-1 (a)「认可前登记证据」字面细化为先关联交付物提交轮次、确认时再引用（用户按预选提交的条目）。
2. **审批履历放审批平台是否可接受**：无法定审计，问内部审计与财务负责人（替代原 W-6，并入 A1-02）。
3. **合同借阅审批人**（PRC-140）：建议借阅人所在部门负责人单级（SH-M1）；原件外借用于诉讼时知会法务（SH-E6）。

## 聚合稿回补

对应 `closeout/COVERAGE_REPORT.md` 本组缺口（第 75–78、84、113 行）。只写草案，由 one-architecture 定号并回写聚合稿；流程骨架 PRC-140、141 按此改稿，等流程设计方法批准后与其余流程一并改，现不动。

### 1. AGG-13 协议：归档与借阅命令（PRC-140、141，C-4，intent 144943）

| 命令（拟） | → 事件 | 前置不变量（拟，待定号） | 说明 |
|---|---|---|---|
| 归档协议 | → 协议已归档（仅分析） | INV-新A（→ INV-124）：协议须已签署生效；原件份数与签章齐全，不齐拒绝并告警（退回补齐） | 记原件类型、份数、存放位置、归档日期、扫描件引用；表 `agreement_archive_records`（D1） |
| 登记协议借出 | → 协议原件已借出（仅分析） | INV-新B（→ INV-125）：协议已归档；同一份原件同时只能有一笔未归还的借出（复印件不受限）；须引用已通过的合同借阅审批实例 | 由审批结果触发（见第 3 节裁定） |
| 登记协议归还 | → 协议原件已归还（仅分析） | INV-新C（→ INV-126）：只对未归还的借出登记，每笔借出归还一次 | 记归还日与接收人 |
| 标记协议借阅逾期 | → 协议借阅已逾期（订阅：催还事件策略 PRC-146） | 无（预计归还日届满且未归还即标，已归还的不标） | 定时策略，执行方 `platform.scheduling`（G-04 同类） |

- 删去「申请借阅协议」作为聚合命令：借阅申请的内容（目的、形式、预计归还日）作为审批载荷留在 `platform.approval`；PRC-140 第 1 步改为平台任务「发起合同借阅审批」。
- 催还提醒（覆盖报告第 84 行）：改为事件策略，订阅「协议借阅已逾期」，经企业微信提醒借阅人与档案管理人。
- 借阅放 AGG-13 还是独立聚合：借阅与协议变更、回签争同一乐观锁；建议先放 AGG-13，取证借阅频次后再定（one-ops 只出计数）。

### 2. AGG-14 框架协议限额：补对象对应

- 对应：**OBJ-05 协议（框架协议类型）的组成部分「框架限额占用」**。信息图 §3 非对象出口表把它降为「框架协议的组成部分，依附框架协议」（`ENTERPRISE_INFO_MAP_DRAFT.md:416`），无单独 OBJ 编号；与聚合稿「次级在内（组成部分自成聚合）」一致。
- 建议 AGG-14 名称列改为「框架协议限额（OBJ-05 框架协议的组成部分：框架限额占用；信息图 §3 :416）」，与 INV-10 出处「OBJ-05；I-10」一致。

### 3. one-architecture 裁定（S-22，2026-10-16）

- 加边 B-05 → `platform.integration`（api），催还走事件策略；不采用平台订阅业务事件（会让平台依赖业务）。
- 删「申请借阅协议」：借阅申请作 `platform.approval` 载荷；审批结果由 B-05 订阅后执行「登记协议借出」（同 `cost.timesheet` 接审批的写法，边 B-05 → `platform.approval`）。
- INV-新A–C 的正式编号由 one-architecture 合并聚合稿时统一给。PRC-140 骨架随流程设计方法批准后一并改稿。

### 4. one-architecture 裁定（S-23，2026-10-16）

- 归档、借出、归还三条命令的事件定为「仅分析」（按第 1 节原拟）。
- 保留定时命令「标记协议借阅逾期」→「协议借阅已逾期」→ 催还事件策略，策略编号 PRC-146（PRC-145 为 people 组三口径预警）。
- INV-新A–C 合入聚合稿时定为 INV-124–126。

### 5. 新归本组（S-16、S-17、S-19）

- PRC-147「费用报销核定后冲抵员工借款并确认应付」（事件策略，owner B-09）：铺开时由本组写骨架；样板阶段不画。

# 铺开 · VS-03 获得风险结论（2026-10-16，子代理 VS03 起草，主会话合并）


### VS-03·接缝问题

1. PRC-44、45（B-26 → B-28）：B-26、B-28 到 `platform.approval` 仍无边（§4.1），AGG-28、AGG-35 状态无审批平台节点（原 S3 接缝 1，未解决，沿用）。本批的三个审批节点（检查记录审核、不符合项复核、整改复查结论）全部写 `approval.engine: aggregate`，由 AGG-35 的「审核检查记录」「复查检查」和 AGG-28 的「复核不符合项」「闭合不符合项」「退回整改」命令承载，均为检查负责人（SH-E4）单级。F-2（intent 114200）要求含审批状态的聚合走 platform.approval，AGG-28、AGG-35 的状态是否算「审批状态」请 one-architecture 判；若算，需新增 B-26、B-28 → platform.approval 两条边。
2. 其余 cross_bc 方向逐条核对 §4.1：B-26 → B-01、B-02、B-04、B-05（api、事件）、B-13、B-28 均成立；PRC-41 触发事件 ContractSigned 由 B-05 发布、B-26 订阅（聚合稿 AGG-13 订阅方列有 B-26），成立；PRC-42 读 AGG-03（B-02）成立。无反向。
3. AGG-28 → AGG-35「来源检查」是 B-28 指向下游 B-26 的「不校验」类型化 ID，聚合稿允许（只作回溯线索），不成依赖边；本批 PRC-44 步 1 以来源检查 ID 记回溯线索，沿用。

### VS-03·结构问题

1. 检查与不符合项的诉讼证据关联键缺口：AGG-110 证据关联的挂接目标只有「交付物 / WBS 阶段节点」（含提交轮次、合同引用，S-13 / intent 141725），检查（AGG-35）与不符合项（AGG-28）引用证据只按 ID（AGG-35 → AGG-38、AGG-28 → AGG-38），没有合同引用与时间线关联。现场检查证据是检查服务最重要的诉讼证据。建议：AGG-110 的挂接目标类型增「检查」「不符合项」（目标 ID 在下游，只作回溯线索不校验，不增边）；或 AGG-35、AGG-28 引用证据时带合同引用。结构变化，交 one-architecture 判。
2. AGG-35 状态机与命令不全：状态「现场进行中」没有进入命令（「采集证据」「提交检查记录」都可能是触发点，未定）；OBJ-49 状态机提议的「取消（原因必填）」没有命令；检查因天气、通行条件无法进行时无处登记。本批骨架未写这两处，见新用语。
3. AGG-28 的等级体系版本（原 D2 结构问题 S-2，未解决）：IDI 五级、尽调红黄绿、既有幕墙地方标准，按服务类型版本化，聚合稿 AGG-28 只有「等级体系版本与等级值」，没有等级体系版本的聚合。PRC-44 步 1 登记时选择等级体系版本，该参考数据暂无发布流程。建议归 B-26 或 B-28 增配置聚合（每版本一聚合，D15），归属由 one-architecture 定。
4. 检查服务收入证据落点（ADR-0047 第 5 类）：「检查完成并经客户签认」目前只能以证据（类型：签认）挂检查，「登记客户认可」只在 AGG-27 交付物上。报告签发这一路走 PRC-28 无问题；若某次检查（如品控按次月结）不出报告，收入证据的认可日无处登记。见待用户定 6。
5. 证据封存时点：INV-58（封存后不可修改）的保存期与封存规则 Q29、Q34 仍待定；本批 PRC-43 步 8 在检查锁定后自动调用「封存证据」，是对封存时点的一个提议（见待用户定 2）。
6. 重大险情须记录已报告（INV-52）在 AGG-28 无对应命令，见新用语。

### VS-03·新用语

按 terms.yaml 检索，下列在词表无命中（命中 0）。建议使用范围：领域，bc 括号内。
- 概念与角色词：检查计划（B-26）、检查表模板版本（B-26）、现场检查（B-26）、检查记录（B-26）、复查（B-26）、外部检测（B-26）、采信（B-26）、封存（B-28）、险情（B-28）、风险保留（B-28）、整改申报（B-28）。干系人映射用语：检查负责人、检查人员（沿用原 S3 映射）。
- 单据类型暂名：检查记录审核（B-26）、不符合项复核（B-28 载荷，B-26 发起）、整改复查结论（原 S3 已列）。
- 命令缺口（不自造进 yaml，步骤上已 gap 标注或写在 desc）：
  - 登记不符合项送达（AGG-28；回执证据落点，PRC-44 步 4 已 gap 标注）；
  - 记录重大险情已报告（AGG-28，INV-52 前置所需）；
  - 停用检查表模板版本（AGG-36；OBJ-50 有「停用」，AGG-03 有对应的停用命令，AGG-36 缺）；
  - 取消检查、开始现场检查（AGG-35，见结构问题 2）。
- 权限码暂名（交 one-glossary 与权限登记汇总）：检查计划.登记 / 生效 / 标记不再受托；检查.安排 / 指派 / 锁定 / 关联报告 / 复查；检查记录.提交 / 审核；证据.采集；不符合项.登记 / 复核 / 发布 / 关闭 / 登记送达 / 开始整改 / 登记整改申报 / 闭合 / 退回整改 / 登记客户接受风险 / 停止跟踪；外部检测.建议 / 登记委托 / 登记报告 / 采信 / 不采信；检查表模板.发布 / 查看。
- 页面暂名：检查计划编辑页、检查计划页、检查安排页、检查指派页、现场证据采集页、证据上传页、检查记录填报页、检查记录审核页、检查锁定页、检查报告关联页、不符合项登记页、不符合项复核页、不符合项发布页、不符合项送达登记页、外部检测建议页、外部检测登记页、外部检测采信页、整改跟踪页、整改申报登记页、整改复查页、客户接受风险登记页、检查表模板版本发布页 / 列表页 / 详情页。

### VS-03·格式问题

1. PRC-45 步 2「登记整改申报」的 actor 由 SH-C3（业主代表，v1 映射）改为 SH-E4，where: offline，SH-C3 留在 roles（external）。理由：整改由业主完成，业主不是本系统用户（与 PRC-28「登记客户认可」同写法）；actor_v1 仍为「业主代表」。若将来开放客户端登记，再改回。
2. PRC-43 步序调整：v1 把「采集证据」放在最后一步，现移到「提交检查记录」之前（步 4）。理由：证据先采集、检查项结果才能引用，INV-55 / INV-50 的先后依赖；v1 字段值未改，只调序。请确认可调序。
3. 新增步骤（added_in: closeout）：PRC-43 步 8（封存证据）；PRC-44 步 4（送达，无命令，gap）、步 6（关闭不符合项，复核不成立）；PRC-45 步 3（采集证据，复查证据）；PRC-46 步 2（采集证据，外部检测报告原件）。命令均来自聚合稿 §2.2 原文。
4. 无命令的步骤：PRC-44 步 4 因命令缺口无 command / aggregate，仅 gap 标注与 evidence；约定未写此情形，请确认写法。
5. PRC-41 步 1 是 system 步骤却带 decision 与 outcome（判断协议是否含 IDI 检查服务）；步 4 同为 system 读步骤带 timeout。均沿用约定的字段，请确认读步骤上可带。
6. PRC-42（单聚合维护）约定未规定字段，本批写了 `states`、`transitions`（from / to / command / by / emits / permission / page / rule）、`pages`、`permissions`、`scenarios`、`migration`，并保留 `steps` 一步以便统一检查；`from: ~` 表示新建。请 one-architecture 裁定标准写法。
7. 同一 SH 在 roles 里出现两个 as（PRC-43 的 SH-E4 initiator / reviewer，PRC-45 的 SH-E4 performer / approver），分条写，沿用原 S3 格式问题 4。
8. 线下步骤同时带 permission / page（登记录入部分），沿用 PRC-28 样板写法。
9. 检查与不符合项的审批 `approver` 与执行人同为 SH-E4，已用 `segregation` 约束登记人与复核人、提交人与审核人，不是同一人。

### VS-03·待用户定

1. 现场采集证据的离线暂存：建议要求移动端离线暂存，联网后补传，同时记设备采集时间与服务端接收时间（两个时间并存），上传即计内容哈希。理由：幕墙、地下室等场地常无网络；诉讼证据要求不可改、时间戳、哈希、关联（intent 141725），两个时间并存可证明补传未改动采集时间。
2. 检查锁定后是否自动封存本次检查引用的证据（PRC-43 步 8）。建议是。理由：锁定即检查结论定稿，证据随之不可改，避免锁定后证据仍可被删改；若封存时点另定（Q29、Q34），仅改该步触发条件。
3. 期限默认值（合同约定优先）：检查记录提交期限 P3D（PRC-43 步 5）、整改申报后复查期限 P10D（PRC-45 步 4）、外部检测报告出具期限 P30D（PRC-46 步 1）；整改期限按等级与合同约定，无默认值。理由：AEC 检查合同通常按等级约定整改期，其余为行业惯例的量级；规则卡与合同模板未定，请业务给出或确认。
4. 提交人与审核人、登记人与复核人必须不同（segregation）。建议是。理由：检查结论是对外责任文件，自查自审无独立性；〔沪TIS〕强调检查独立性。
5. PRC-44 复核认定发现不成立时如何处置（原 S3 待用户定 6，仍待答）：本批按建议写成「关闭不符合项」（步 6）。备选是在 AGG-28 增「作废不符合项」命令。理由：关闭命令已有，增量最小；作废语义更准但属聚合稿增命令。
6. 检查服务收入证据（ADR-0047 第 5 类）：无报告的按次检查（如品控按次月结）是否仍建交付物（类型「检查记录单」）并走 PRC-28 登记客户认可。建议是；认可日对客户签认取签认日、对报告签发取送达客户日（ADR-0047 F-6 建议）。理由：「登记客户认可」命令只在交付物上，不建交付物则没有收入认可落点，且送达证据链断开。
7. PRC-45 整改复查结论是否单级（原 S3 待用户定 4，仍待答）：建议检查负责人单级，复查未通过自动退回整改；理由：不涉资金，且无 platform.approval 边（见接缝 1）。
8. 非 IDI 的周期检查（既有幕墙 3 至 5 年一次）是否也建检查计划：AGG-34 目前仅 IDI。建议暂不建，由检查负责人直接「安排检查」，到期提醒走 P-28 派生。理由：U-3 已定到期只作提醒，不是对象状态；非 IDI 无状态机需求。
9. 不符合项发布后向委托方送达的回执落点（PRC-44 步 4）：建议在 AGG-28 增命令「登记不符合项送达」，证据同交付物送达（签收单、妥投、邮件原始报文）。理由：IDI 失信情形含未按要求报告和上传（〔沪TIS〕第 12 条），送达是责任证据；与 PRC-28 的送达回执口径一致。
10. 「标记不再受托」是否要求填原因（PRC-41 步 5）：建议必填。理由：与协议终止原因必填一致，便于周期检查的客户流失分析。
11. 检查表模板发布是否需技术委员会复核（PRC-42）：建议暂不需要，由技术专家直接发布。理由：版本化保证可追溯，旧版本不受影响；AGG-36 无审批状态，F-2 不适用；若业务要求技术委员会把关，再加审批节点。
12. 我方法律角色（Q30）：INV-56 兼项限制只在我方为 TIS 时生效，我方是 TIS 专项分包或保险公司直接委托的专项顾问时不适用（PRC-43 步 3 已按此写）。请确认这一口径，并确认这一角色在「安排检查」时由检查负责人选定。
13. 外部检测报告采信由谁最终决定（PRC-46）：建议检查负责人单级。理由：采信是技术判断，不涉资金；〔沪IDI〕未要求多级。
14. 整改责任方（业主）是否开放客户端登记整改申报：建议暂不开放，由检查负责人收函后登记（本批 PRC-45 步 2 如此写）。理由：U-1 未定客户门户，当前范围内客户不是本系统用户。
15. IDI 委托（保险公司，SH-C5）与 TIS（SH-X5）的角色：本批 SH-C5 作 informed（接收报告和发现），SH-X5 作 PRC-43 的 external（我方为 TIS 专项分包时的上游）。若保险公司需要系统内签认检查记录，应另提需求。请确认。

# 铺开 · VS-04 约定并支付服务价款（2026-10-16，子代理 VS04 起草，主会话合并）

主会话按语：PRC-52 与 INV-23 中「审计师书面认可」按 A1 后续页（intent 2026-10-16-162827）改读为「财务负责人书面确认并留档，每年复核」；INV-23 字面由 one-architecture 随 ADR-0047 部分取代一并改。


范围：PRC-47、48、49、50、54、57、58、60、61、63、66、86、140、141（业务流程 14）；PRC-51、53、56、59、146、147、148（事件策略 7）；PRC-52、68（单聚合维护 2）；PRC-62（读模型 1）。验收场景 96 条。已在上一版 NOTES 中解决的问题不再列：PRC-53 升级与号（已给 PRC-148）、PRC-54 步 3、4 外移、PRC-140 / 141 改稿、PRC-146 / 147 编号。

### VS-04·接缝问题

1. 审批结果回调的表示法（沿 S1 接缝 1，仍待裁定）：PRC-47、48、50、57、140、148 的回调事件（合同评审、协议变更审批、任务订单审批、开票申请审批、合同借阅审批已通过、收入确认审批已通过 / 已驳回）由 platform.approval 发出、本 BC 订阅。本批写作 `event in platform.approval`；依赖边方向是本 BC → platform.approval，规则 2 不能直接对照，请 one-architecture 裁定统一写法。S-22 对 PRC-140 已定「B-05 订阅审批结果」，本批照此。
2. PRC-148 提交过账候选：Q04_STORAGE 写「经事件发布登记表投递，日对账兜底」，骨架按既有边 B-08 → B-16 写 `api out B-16`（与上一版一致）。实现是 api 同步还是登记表异步投递不影响边，仅提示。
3. PRC-56 与视为同意路径：「付款条件项已可结算」只由 B-28 的「交付物已认可」触发；客户未认可而「视为同意已成立」（B-08 认定）时，B-05 不依赖 B-08（边为 B-08 → B-05），不能订阅认定事件。本批不加边，视为同意后是否可开票见待用户定 3。
4. PRC-147：B-09 → B-10（api 确认应付）、B-09 → B-12（api 标记已付）是 S-17、S-19 在既有边上加的 api 方式，聚合稿 §4.1 须补；PRC-86 第 3 步订阅 B-10「借款付款已成功」沿 Q-10 的 B-09 → B-10 事件边。均无新边。
5. PRC-58 发出的「发票已红冲」订阅方 B-09 无反应命令（G-08 第 2 点，仍未解决）；PRC-66 的「协议已结算」→ B-08 无收入冲减命令（G-16、G-19，仍未解决）。
6. PRC-54 读 B-28 的无异议声明与送达日（api），订阅「交付物已送达」，方向均与 §4.1 一致；「AcceptanceReturned」的取消待办是读检不是命令（G-19）。
7. PRC-140 第 4 步、PRC-146、PRC-62 的定时执行方为 platform.scheduling，聚合稿 §2.2 仍无任何聚合引用它（G-04 未处理），骨架不写 scheduling 的 cross_bc。

### VS-04·结构问题

1. AGG-44 订正（Q04_STORAGE §4，随 Q-04）：命令「占用收入额度」「释放收入额度占用」「追加收入确认事实」「占用转实」要进聚合稿；「确认视为同意期满」改为「认定视为同意期满」，视为同意改为单独只追加的认定事实；AGG-47 注释、N-12、Q20 行改写。PRC-53、148 已按 C 写并标 `pending: Q-04`。
2. PRC-148 二次校验失败（RULE-222）后占用的去向，聚合稿与 Q04_STORAGE 未写；骨架暂写「不转实、告警、占用保持待处置」。
3. PRC-61 坏账核销审批：B-09 无到 platform.approval 的边（仍未解决，沿 S3 结构问题 2）；本批不写审批节点。
4. PRC-49 / PRC-68：AGG-16 无审批状态；AGG-13 有「审批中」状态但终止不在其中，F-2 对「终止协议」「提交索赔」是否要走 platform.approval 未裁（沿 S1 D9）。本批不加审批节点。
5. PRC-50：INV-10 写「提交审批时占用」，骨架仍先占用后提交（沿 S1 结构问题 2，未解决）。
6. PRC-47 第 6 步新增出口「我方为风险管理机构且独立性检查有冲突、无书面同意 → 签约被拒」，依据 INV-33 的「签约前再读一次」，v1 无此出口；已写在决策上，请核。
7. PRC-66 的差额冲减（G-16）：按「收入在客户确认时点」口径，结算差额冲减也应有证据触发；命令缺，未写。
8. 开票与收入两条独立路径：PRC-57 开票只改写开票额度，触发应收（PRC-59）；PRC-53 / 148 确认收入只由客户确认触发；两者由同一「交付物已认可」分别经 PRC-56 与 PRC-53 展开，互不触发，PRC-58 红冲只回冲开票额度。进度估计（管理产值）不触发任何本批流程。无结构变更。
9. PRC-52：INV-23 与 ADR-0047 第 4 条第 4 点以「审计师书面认可」为开放前提，用户已定无法定审计，条文须订正，见待用户定 8。
10. PRC-86 手工冲抵步与 PRC-147 自动冲抵重叠，见待用户定 6。

### VS-04·新用语

命令、事件（聚合稿已列的不再重复，已列者：归档协议、登记协议借出、登记协议归还、标记协议借阅逾期、协议已归档 / 原件已借出 / 已归还、协议借阅已逾期）：

1. 命令：占用收入额度、释放收入额度占用、追加收入确认事实、占用转实（或「收入额度占用转实」）、认定视为同意期满（替代「确认视为同意期满」）。
2. 事件：收入确认审批已通过、收入确认审批已驳回、合同借阅审批已通过（平台审批结果事件，名称由 one-architecture 与 one-glossary 定）。
3. 审批单据类型：合同评审单、协议变更审批单、任务订单审批单、开票申请审批、合同借阅审批、收入确认审批。
4. 概念：缺陷责任期届满日（PRC-62 维度）、红字信息确认（税务）、借款冲抵（结清方式，S-19 已交）。
5. 结局词（待与阶段出口用语对齐）：已回签生效、签约被拒、新基线已生效、变更已驳回、已提交待答复、索赔已认定、索赔已拒绝或撤回、占用被拒、任务订单已签署生效、限额已释放、视为同意已成立、视为同意未成立、待办已取消、已开具、已驳回、已作废、因期间已关账未开具、已红冲、红字确认已作废、因期间已关账未红冲、已核销、待认领、无逾期、已登记处置、坏账已核销、因期间已关账未核销、已兑付、商票已逾期、因期间已关账未登记兑付、履约未完成、协议已结算、借款已发放、借款已结清、因期间已关账被拒、借阅申请被驳回、借出被拒、已归还、已归档。
6. 权限码（暂名「对象.动作」）与页面（暂名「…页」）：均写在骨架 permission、page 字段，待汇总交 one-glossary 与页面清单；同名跨流程复用的有「应收.核销」「收款.认领」。

### VS-04·格式问题

1. 一步有多个结束出口时，结局词写在 decision 项（`{when, goto: end, outcome}`），步级 `outcome` 只用于单一出口；CONVENTIONS 未定，请裁定。
2. 事件策略多步（PRC-147、148）用 `steps` 列表，单步仍用 `step`；事件策略增补 `triggers`（保留 v1 `trigger`）、`migration`、`scenarios`、`notes`。
3. 单聚合维护新增 `states`、`transitions`（from、to、command、actor、emits）、`pages`、`permissions`、`scope`；读模型新增 `query`、`dimensions`、`refresh`、`permission`、`page`、`scope`；方法稿只描述了内容，字段名请裁定。
4. 自动（`actor: system`）触账步骤 `period_gate: true` 但不写拒绝分支，而是路由到开放期间并标原期间（INV-92）；人工触账步骤写「期间已关账」拒绝出口（引 ADR-0045）。
5. 新字段 `pending: Q-04`（PRC-53、148），Q-04 不选 C 时撤改。
6. `timeout.after` 非 ISO 时长处写中文口径（合同约定、配置值），已在待用户定 12 汇总默认值。
7. 平台任务步 `where: in_system`（审批人在审批平台操作），permission 写「…审批」，page 写「审批待办页」。
8. 步骤 `bc: B-05`、`gap` 等 v1 字段原样保留；PRC-86 外部银行步骤 `actor: system, where: external:银行`。

### VS-04·待用户定

沿用上一版 NOTES 且未变的不重复：PRC-47 合同评审多级（D3）、PRC-48 协议变更多级（D4）、PRC-50 单级（D7）、PRC-49 索赔提交审批（D9）、PRC-57 开票审批分级、PRC-58 蓝字未入账是否要红字确认、PRC-61 坏账核销审批。本批新增或变化：

1. Q-04 选 C（PRC-53、148 依赖）；收入确认审批人建议财务负责人单级（SH-E6），驳回释放占用。理由：无法定审计，内部审计与财务负责人把关。
2. 视为同意路径的收入业务日期，建议取期满日（沿上一版 S3 待定 2，PRC-148 第 1 步已按此写）。理由：与认可日口径一致，期间闸门按此路由。
3. 视为同意已成立后，付款条件项是否可结算、可开票：建议可（视为同意等同认可），实现需 B-05 经 B-08 → B-05 既有边读取认定（api），而不是订阅事件。理由：否则「视为同意」只能确认收入、不能开票，两条路径不对等。
4. PRC-148 二次校验失败后占用的处置：建议保持占用并告警，由财务负责人人工释放或重新发起审批。理由：自动释放会丢失原审批实例的追溯。
5. PRC-146 催还节奏：建议首次逾期提醒后每 7 天复提醒，逾期满 30 天升级 SH-M1；现骨架只提醒一次。理由：单次提醒易被忽略，需新增定时复发事件。
6. PRC-86 手工冲抵例外：建议删除，只留 PRC-147 自动冲抵；骨架暂保留第 8 步。理由：S-16 / S-24 已把冲抵放到报销核定的自动反应，两条路径并存易双重冲抵。
7. PRC-140 借阅审批人：沿上一版，建议借阅人所在部门负责人单级（SH-M1），原件用于诉讼时知会法务（SH-E6）。
8. PRC-52 开放证据类型的认可依据：INV-23 写「审计师书面认可」，用户已定无法定审计。建议改为「财务负责人与内部审计书面认可」，依据文件仍作命令输入。理由：保持审批履历与依据可追溯。
9. PRC-68 终止协议是否审批：建议单级（SH-M1）并知会法务，经 platform.approval，AGG-13 终止前加「终止审批中」状态。理由：终止对价款与项目成本清算影响大，通行须内部审批。
10. 自动触账步骤遇期间已关账：建议路由开放期间并标原期间（INV-92），不拒绝；人工触账步骤拒绝并告警（已按此写）。理由：自动反应拒绝会让报销、开票等停在中间态。
11. PRC-60、PRC-86 的职务分离（认领与银行流水登记、借款发放与付款执行）的具体岗位：建议认领出纳与流水导入、付款执行岗位分开。理由：资金线通行不相容职务分离。
12. 期限默认值汇总（均为建议）：PRC-47 评审 P5D；PRC-50 占用有效期（配置）；PRC-54 期满待办 P7D；PRC-57 开票审批 P3D、税务平台应答 PT30M；PRC-61 处置登记 P30D；PRC-66 结算确认 P30D；PRC-140 借阅审批 P3D；PRC-141 回签后移交 P30D、退回补齐 P10D；PRC-49 未约定索赔时限 P28D。理由：规则卡与 ADR 未定，按 AEC 常见做法填。

# 铺开 · VS-01 选定服务方（含 PRC-69、78；2026-10-16，子代理 VS01 起草，主会话合并）


范围：业务流程 PRC-03、07、08、13、16、69、78；单聚合维护 PRC-01、02、05、06、10、12、14。底稿为 WT 的 `processes.revenue.yaml` 与 `NOTES.md`（S1 / D1 子批）；已在样板之后解决的不再列。依据 `SCR/agg_new.md`（聚合稿新版）、S-11–S-24、样板 PRC-28。小节按 `CONVENTIONS.md` 第 4 节。

### VS-01·接缝问题

1. **审批结果回调的表示法（PRC-07、PRC-13，仍未解）**：B-03、B-02 只有到 `platform.approval` 的 api 边（§4.1），审批通过 / 驳回的结果回调写成 `direction: in` 的 api 会违反规则 2。PRC-16（B-05）已由 S-22 解决：B-05 → `platform.approval` 含事件边，第 4 步写 `event in platform.approval 审批结果（暂名）`。B-03、B-02 的回调仍未写 `cross_bc`，只在 desc 说明为「审批通过结果回调」。建议 one-architecture 对 B-03、B-02 同样给「审批结果事件」边（同 `cost.timesheet` 做法），或明确审批钩子由业务模块实现、回调不入依赖图（§4.1 B-29 行有此写法）。
2. **定时命令无执行方（G-04 仍未处理）**：PRC-08 第 4 步（P-13 截止提醒）、PRC-16 第 4 步（授权期限届满）、PRC-78 第 4、6 步（到期提醒与到期标记）、PRC-10 与 PRC-08 第 15、17、19 步（P-10 退还时限提醒）都靠定时；聚合稿没有任何聚合引用 `platform.scheduling`，本批不写其 `cross_bc`。
3. **同一事件两端分写**：PRC-08 第 7、8、9 步写 `event out B-09`（发布端），第 15、17、19 步按 v1 写 `event in B-03`（订阅端，step 带 `bc: B-09`）。若合并时只保留一端，请统一删订阅端，v1 的写法是订阅端。
4. 其余 `cross_bc` 逐条对 §4.1：B-03 → B-01 / B-02 / B-13 / `platform.approval`（api）；B-03 → B-09 / B-04 / B-05 / B-07 的事件（对方订阅 B-03，边为 B-09 / B-04 / B-05 / B-07 → B-03 事件，成立）；B-09 → B-16（api）；B-02 → B-01 / B-13 / `platform.approval`（api）；B-03 / B-04 → B-02 的 api 读取（`direction: in` 沿用 S1 写法）；B-05 → B-03 / B-04 / B-28（api）；B-01 → B-13（api）；B-11 / B-10 → B-01（api 读取角色状态）。均成立，无反向。
5. 「仅分析」事件（线索已确认、报价版本已生效、投标已递交、投标已放弃、Regularized、Halted、风险交付授权已批准、资质已撤销、资质已到期、角色已合格 / 已暂停 / 已停用、各配置版本已生效等）订阅方只有 `reporting`，不写 `cross_bc`（沿用 S1 做法，表示法见格式问题）。
6. PRC-16：STEP6A G-13（建立项目与风险交付授权之间无校验路径）影响仍在，靠 P-09 度量预警承担，不改边。
7. PRC-08 v1 `notes` 曾写 OpportunityAccepted 订阅方含 B-07 与「转入售前投入」策略；与聚合稿 AGG-10（B-04、B-05）和 P-2 不一致。本批已把 `notes` 改为 B-04、B-05 并注明赢单不触发售前投入转入，请合并时认可此改动。

### VS-01·结构问题

1. **PRC-13 缺命令（聚合稿回补）**：AGG-05 只有「批准费率版本」「生效费率版本」，没有编制（草拟）与「提交审批」命令；AGG-06、AGG-07 只有「生效…版本」，没有编制命令。草拟状态因此没有创建命令，第 2 步无 `command`，标 `gap`。另 INV-36a 写「已批准的计费系数版本」，但 AGG-06 既无批准命令、引用列也无 `platform.approval`，系数是否经审批不明（见待用户定 5）。
2. **PRC-16 审批驳回后聚合没有对应命令**：AGG-17 只有申请、批准、转正、叫停；审批驳回时申请停在「申请」状态。需补「驳回风险交付授权」（或「撤销风险交付授权申请」）之类的命令，或明确驳回只在审批平台留痕、聚合不变。本批第 3 步驳回直接结束，结局「授权未获批准」。
3. **PRC-78 延续审批（D8，已按指示处理）**：B-01 无到 `platform.approval` 的边，AGG-02 无「审批中」状态，F-2 不适用；按指示写成 B-01 内单级命令：第 5 步「延续资质」由 SH-M1 执行，`approval.engine: aggregate`，批准即登记新有效期。同时发现：信息图 OBJ-65 有「有效 → 延续中」，但 AGG-02 的命令中没有进入「延续中」的命令（只有「发起资质核查」→ 核查中、「延续资质」）。线下向发证机关办理延续（第 4 步）因此没有对应命令，只作线下步骤并登记证据。请补「登记资质延续申请」（进入延续中）或确认「延续中」不入聚合。
4. **PRC-10 缺命令**：保证金「待缴」状态没有创建命令（登记保证金应缴 / 付款申请的来源），聚合稿命令从「登记保证金缴纳」起；投标保证金缴纳前的付款申请经 B-10，来源关联未写。
5. **PRC-06 缺创建命令**：「待查」状态没有命令创建（商机进入跟进且我方为 IDI 风险管理机构时自动待查），聚合稿只有「登记独立性检查结论」。
6. **PRC-08 保证金退还的实质改动（请确认）**：v1 三个退还步是「系统策略」自动执行「退还保证金」；退款发生在系统外（招标人退回到我方账户），系统不知道钱是否到账，本批改为出纳（SH-E6）按银行到账记录登记（第 15、17、19 步），过账提交拆为独立服务任务（第 16、18、20 步，S-24）。v1 的 `actor_v1: 系统策略` 保留。
7. **PRC-69 暂停、停用无自动触发（D10 沿用）**：资质撤销、到期两个事件订阅方仅分析，暂停、停用仍由财务人工命令触发；自动化需新增订阅与边，不在本批。
8. **PRC-05 合并后在途引用**：聚合稿只有「已合并的单位不得新建协议与付款」（INV-03a/b/c），没有说明合并时既有商机、协议、资质的引用是否重指向存续档案。
9. **干系人缺项（D1 沿用并扩充）**：干系人表无「商务」「项目总监」「法务」「出纳」「目录管理员」；本批映射为 SH-E5、SH-M1、SH-E6、SH-E6、SH-E4（PRC-12 的技术委员会目录管理员）。
10. **PRC-12 阶段类型取值**：Q06 阶段类型取值待用户提供，迁入框架按其到位后补。
11. **对 v1 字段值的改动清单（请合并时确认）**：① 各流程 `actor` 由中文角色改为 SH- / system / platform，原中文保留在 `actor_v1`；② PRC-03 第 7 步的两条 api 读取移到新增第 6 步（系统读取），第 7 步 `cross_bc` 清空；③ PRC-13 `approval.on_reject` 由 end 改为退回编制（第 2 步）；④ PRC-16 第 4 步（批准风险交付授权）由管理层执行改为审批结果回调，actor 改 system，第 3 步 `approval.on_reject` 仍为 end；⑤ PRC-08 见上第 6 条，且 v1 第 12 步（未中标丢单）在废标后不再重入，废标后丢单另设第 21 步（人工）；⑥ PRC-08 `notes` 订阅方见接缝 7。步骤序号因新增读取、过账与通知步骤重排，所有 `goto` 已按新序号重写。

### VS-01·新用语

- **缺命令的候选名（只提议，未写进 yaml 的 `command`）**：编制费率版本、提交费率版本审批（AGG-05）；编制计费系数版本（AGG-06）；编制协议价目版本（AGG-07）；驳回风险交付授权或撤销风险交付授权申请（AGG-17）；登记资质延续申请（AGG-02，进入延续中）；登记保证金应缴（AGG-51）；发起独立性检查（AGG-18，创建待查）。
- **审批单据类型暂名**（沿用 S1，terms.yaml 无）：报价评审单（B-03）、费率版本批准单（B-02）、风险交付授权审批单（B-05）。「资质延续审批单」（B-01）因走 `engine: aggregate`，不是审批平台单据类型，正式名随 D8 定。使用范围均为领域（`platform.approval` 任务类型）。
- **平台任务名暂名**（platform 步骤，非聚合命令，不计入命令）：报价评审、费率版本批准、授权审批。
- **事件名暂名**：审批结果（平台发布，B-05 订阅；通过 / 驳回两种结果，PRC-16 第 4 步）。
- **结局词**（取自阶段出口用语）：PRC-03 已转商机、线索已关闭、已挂起、商机已新建、商机已维护；PRC-07 报价已生效、报价已作废、报价未编制；PRC-08 已递交待评标、商机已赢单、商机已丢单、商机已放弃、保证金已退、不得投标、因期间已关账被拒绝；PRC-13 费率版本已生效、计费系数版本已生效、协议价目版本已生效、生效被拒（区间重叠）；PRC-16 已批准、已转正、已叫停、授权未获批准；PRC-69 角色已合格、准入未通过、角色已暂停、角色已停用、无需准入；PRC-78 核查通过、资质已降级、资质已撤销、资质已延续、资质已到期。
- **权限码暂名**（对象.动作，bc 随步骤所属聚合）：线索（登记、认领、挂起、恢复、确认、关闭）、商机（新建、变更负责人、维护参与团队、赢单、放弃、登记丢单）、报价（编制、提交评审、生效、作废）、报价评审.审批、投标（登记资格预审、递交、登记结果、撤回、放弃）、保证金（登记退还、登记缴纳、登记保函、扣减、没收、登记保函到期或索赔）、费率版本（编制、生效）、费率版本批准.审批、计费系数版本.生效、协议价目版本.生效、风险交付授权（申请、审批、转正、叫停）、往来单位角色（申请、核准合格、暂停、停用）、资质证书（登记、发起核查、登记核查结果、登记延续申请、延续）、往来单位（登记、核实、变更银行账户、合并、停用）、市场活动（计划、开展、结束）、客户活动（计划、变更、完成、取消）、独立性检查.登记结论、服务产品版本与阶段类型版本（发布、停用）、建设工程（登记、更新外部状态）、招标项目.登记。
- **页面暂名**：线索登记页、线索跟进页、商机新建页、商机维护页、商机赢单页、商机放弃页、商机丢单登记页、报价编制页、报价生效页、报价评审审批页、资格预审登记页、投标递交登记页、投标结果登记页、投标撤回登记页、保证金退还登记页、保证金缴纳登记页、保函开立登记页、保证金扣减页、保证金没收页、保函到期或索赔登记页、费率版本编制页、费率版本批准审批页、费率版本生效页、计费系数版本生效页、协议价目版本生效页、风险交付授权申请页、风险交付授权审批页、风险交付授权转正页、风险交付授权叫停页、往来单位角色申请页、往来单位角色核准页、资质证书登记页、资质核查页、资质延续申请登记页、资质延续批准页、往来单位登记页、往来单位档案核实页、往来单位银行账户变更页、往来单位合并页、往来单位停用页、市场活动登记页、市场活动跟进页、客户活动计划页、客户活动反馈页、独立性检查登记页、服务产品版本发布页、阶段类型版本发布页、建设工程登记页、招标项目登记页、外部状态更新页。
- 「正式往来单位」= 档案已核实（P-3）：本批只引用，不新建词。

### VS-01·格式问题

1. **decision 条目可带 `outcome`**：同一步有多个 `goto: end` 且结局不同（期间已关账 vs 正常、撤销 vs 降级 vs 通过、客户角色无需准入等）时，每条结束出口各带 `outcome`；仅有一个结束出口的步仍写 step 级 `outcome`。请确认并入 CONVENTIONS，BPMN 生成器按出口生成结束事件。
2. **step 的 `bc`**：下游 BC 订阅事件后的反应步（PRC-08 第 15–20 步）带 `bc: B-09`，沿用 v1 写法。
3. **单聚合维护的 schema**：`states`、`transitions`（`from`、`to`、`command`、`by`、`emits`、`rule`、`period_gate`、`aggregate`）、`permissions`（`code`、`command`、`roles`）、`pages`、`reminders`（`policy`、`when`、`to`、`how`）、`posting`（过账候选及期间闸门）、`aggregates`（一个流程含多个聚合时，PRC-12、PRC-14）、`scope`、`audit`、`legal_entity`、`migration`、`scenarios`。CONVENTIONS 未给出该类型的字段表，请裁定。
4. **线下步骤无命令**：PRC-78 第 4 步（向发证机关办理延续）没有可对应的命令，仍按线下步写 `evidence`、`permission`、`page`（登记入口），以便生成「登记…」用户任务。
5. **`timeout.after` 非 ISO**：投标截止提醒的提前量（P-13）、延续办理的提醒提前量（P-02）规则卡未定，写为文字注明「以规则卡为准」；确定的写 ISO 8601（保证金退还 P5D）。
6. **`goto: PRC-NN` 自指**：PRC-08 第 18 步「重新招标，另建投标」写 `goto: PRC-08`，沿用 v1，请确认允许。
7. **`otherwise → end` 表示外部等待**：PRC-08 第 5 步（已递交待评标）、PRC-16 第 4 步（已批准，等转正或叫停）的 `end` 表示本次骨架到此、由事件或人工重入，不是业务终止，结局词按状态命名。
8. **`trigger.type`** 取值：本批用 `human`、`time`、`message`，约定未列举取值。
9. 平台模块写法 `platform.approval` 沿用 S1（取自 §4.1）。

### VS-01·待用户定

编号沿用 S1（D1–D10），仍未决的照列并新增 D11 起。所有项均为建议，yaml 已按建议填写。

- **D1 角色映射（沿用并扩充）**：商务 → SH-E5、项目总监 → SH-M1、法务 → SH-E6、出纳 → SH-E6、目录管理员 → SH-E4。建议先用此映射；理由：干系人表不含这些角色，AEC 通行做法商务属经营条线、项目总监属管理决策层、出纳与法务属职能。涉及 PRC-07、08、10、12、16。
- **D2 报价评审（PRC-07）**：审批人 SH-M1、单级；并建议报价编制人与评审人不得为同一人。建议按报价金额分级后再定多级；理由：规则卡未定，先单级。另需定评审时限（建议 3 个工作日，超期升级提醒），本批未写 `timeout`。
- **D5 风险交付授权（PRC-16）**：审批人 SH-M1、单级；转正保持人工（SH-E5），不由合同回签事件自动转正。建议如此；理由：转正口径与时点待流程梳理（C-56），授权额度有限，人工确认可控。驳回后聚合不变（见结构问题 2）。
- **D6 费率版本（PRC-13）**：批准人 SH-M1 单级，发起人（SH-E6）与批准人分离。**D13 新增**：计费系数版本是否与费率版本一并批准（建议一并：INV-36a 提到「已批准的系数版本」，Q-07 系数由业务线 × 客户分级决定、影响计价，应受控）；协议价目版本是否单独审批（建议不单独审批，由报价评审与合同评审覆盖，因为协议价目按单个客户议定）。
- **D8 资质延续审批（PRC-78）**：已按指示写成 B-01 内单级命令（SH-M1 执行，批准即登记新有效期），向发证机关办理延续为线下步骤，到期未延续由定时标记。仍建议 ②，如要走 `platform.approval` 需 B-01 增边并重跑 `aggregates_dep_check.py`。
- **D9 PRC-08 是否补审批**：建议不补（报价评审已在 PRC-07，保证金、盖章走线下，AGG-11 无审批状态）；PRC-49 的索赔审批不在本批。
- **D10 PRC-69（沿用）**：暂停由 PRC-78 的资质已撤销 / 已到期人工触发，停用由合作终止触发，仍为人工命令；**D14 新增**：供应商准入是否要求往来单位档案先核实（建议要求：核准合格前档案须为「有效」，与 P-3 的正式往来单位口径一致）；资质核验证据要求（建议查验发证机关公开查询结果并留存截图，不凭复印件）。
- **D11 PRC-08 保证金退还**：由「系统策略」改为出纳按银行到账登记（结构问题 6）。建议如此；理由：退款在系统外发生，系统无法确认到账。同时请确认退还提醒时限：未中标 / 废标按书面合同签订后 5 日（条例第 57 条），截止前撤回按收到撤回通知后 5 日（条例第 35 条，v1 要求另行核对），规则卡定。
- **D12 银行账户变更复核**：PRC-05 的「变更银行账户」建议加双人复核（变更人与复核人不同）。理由：银行账户变更是付款欺诈高风险点，P-01 只是付款前预警。v1 无复核，本批未写进 yaml，仅 `audit`。
- **D15 保证金扣减与没收复核**：PRC-10 的扣减、没收建议由财务负责人复核。理由：金额影响大且不可逆（退还与没收互斥各只一次，INV-20）。本批未写进 yaml。
- **D16 独立性检查结论更正方式**：PRC-06 建议更正以追加新结论、保留历史的方式处理，不覆盖旧结论；理由：检查结论是售前拦截与诉讼可引用的依据。
- **D17 线索挂起超期提醒**：PRC-03 建议挂起超过 90 天未恢复提醒认领人（可关闭）；理由：挂起线索无期限会堆积。本批未写进 yaml。
- **D18 往来单位合并后的在途引用**：建议合并时既有商机、协议、资质引用重指向存续档案并留痕，由财务执行（结构问题 8）。
- **D19 在途单据迁入口径（跨批）**：本批所有流程的 `migration` 统一按「审批中的单据由发起人在新系统重新提交，其余按当前状态迁入」写，原则来自 U-1（不接老系统）与 ADR-0036；请确认该统一口径，或由迁入框架另定。
- **D20 PRC-02 客户活动**：P-47 的提醒周期（计划日期过后几日提醒）规则卡未定，建议 1 个工作日后首次提醒。
- 与随 Q-04 的事项无交集，本批无需标「随 Q-04」。

# 铺开 · VS-02 获得交付成果（2026-10-16，子代理 VS02 起草，主会话合并）


范围：业务流程 PRC-17、18、19、20、23、24、26、30、31、32、34、36、37、38（14 条），单聚合维护 PRC-21、27、33，读模型 PRC-22，事件策略 PRC-91；PRC-28 为用户确认的范本，未改。已在 WT `NOTES.md` 解决的问题不再列，仍未解决的照列并注明「沿用」。

### VS-02·接缝问题

1. **审批结果的事件方式（PRC-19、23、36）**：B-04、B-06、B-29 回写审批结果都靠订阅 platform.approval 的「审批已完成」（词表已有，bc 为 platform.approval），B-06 已建代码即如此（提交时发 DocumentSubmittedForApproval，订阅审批已完成）；聚合稿 §4.1 这三条边只登记了 api 方式。建议同 S-22 做法：给既有边补「事件」方式，不新增边。骨架已按此写 `cross_bc {mode: event, direction: in, bc: platform.approval}`。
2. **PRC-23 事件订阅方（WT 接缝 4 可销）**：已按代码核对，TimesheetApproved 等事件除 `reporting` 外只有同 BC 的 PRC-91 订阅，无跨 BC 订阅方；PRC-23 不写事件 cross_bc。聚合稿 AGG-39 事件列建议补写「订阅方：同 BC PRC-91；其余仅分析」。
3. **PRC-24 取消项目「无任何投入」**：v1 写「无任何投入时」才取消，但 B-04 位于 B-06、B-07 上游，不得读工时与成本，AGG-19 的「取消项目」不能把它当前置。骨架已改为：由 B-07 订阅「项目已取消」后核对，发现已有投入则预警并转费用。请确认。
4. **PRC-91 期间闸门（B-19）**：v1 的 gate 写 AGG-81（B-19）开放，但 §4.1 B-06 的上游没有 B-19。骨架改为经 B-16 读期间状态（B-06 → B-16 已有 api 边），B-16 是否提供期间状态读取、还是只靠过账时的期间闸门（INV-67、INV-92）拦截，请 one-architecture 判。
5. **PRC-32 合同期限参数**：通知 5 天、说明 10 天、发包人答复 5 天来自合同，B-04 不得读 B-05（B-05 在下游），故骨架让项目经理录入申请时填合同期限（缺省取示范文本）；若要自动取合同参数需反向边，不可。请确认保持录入。
6. **PRC-18 `MilestoneAccepted`（沿用 WT 接缝 S2-1）**：AGG-21 事件列写订阅方 B-08（随 Q21），但无命令发出该事件、§4.1 无 B-08 → B-04 边，PRC-18 不写。
7. **PRC-38 / PRC-107（沿用 WT 接缝 S2-6）**：AGG-103 的「项目复盘已发布」订阅方应改为「不订阅，B-04 经 api 调 B-30 提炼」，PRC-38 只写读 B-30 的 api。
8. **PRC-17 先行投入（沿用 WT 接缝 S2-8，G-13）**：B-04 不得读 B-05，先行投入建项时无法校验风险交付授权，靠 P-09 度量预警承担；骨架第 2 步不校验授权。
9. **PRC-36 项目已结项事件（WT 接缝 S2-7 已处理）**：骨架在「结项」步只写 `emits`，事件由 AGG-19（B-04）发出，不在 B-29 步下写事件 cross_bc。

### VS-02·结构问题

1. **管理产值（产值估计）无聚合命令**：用户答复「每期估进度只作内部管理产值」，`Q04_STORAGE.md` 暂作 AGG-23 内只追加子表 `progress_estimates`，每期一行，项目经理填、部门负责人复核。聚合稿没有「提出产值估计」「复核产值估计」命令，也没有流程号。**PRC-30 涉及**：PRC-30 只含完工估算（ETC、EAC）的提出与批准，不含产值估计；两者同在 AGG-23 所在 BC，但频率（每期 vs 阶段门）、填报人与复核人不同，不进收入确认（G-1 守护）。建议另立流程号（向 one-architecture 要号），归属（AGG-23 内子表还是独立聚合）由 one-architecture 判。
2. **AGG-32 缺「登记资料需求」**：P-26「发包人资料逾期未提供提醒」需要先有所需资料与计划提供日，AGG-32 内含「—」，命令只有登记已提供、确认、退回。骨架 PRC-26 第 1 步标 gap。建议给 AGG-32 加命令与计划提供日，属薄聚合内扩充，不改 BC 与归属。
3. **AGG-39 命令缺撤回与修改**：已建代码有「修改（revise）」「撤回本轮（Withdraw）」「取消整个审批（Cancel）」，事件 TimesheetApprovalWithdrawn；聚合稿命令列只有提交、通过、退回、驳回、过账候选、认定。PRC-23 第 7 步标 gap。需回补聚合稿。
4. **PRC-91 与词表 `effectiveness_assessment`（沿用 WT 结构 S-3）**：词表把有效性认定定为「项目×期间批量认定」聚合，聚合稿放在 AGG-39 内。骨架按聚合稿（AGG-39 命令「认定工时有效性」）；若批量认定独立成聚合，须在 B-06 另立并改 §2.2。
5. **PRC-24 / AGG-19 状态机 Q09 仍未定**：是否允许执行中不经暂停直接终止或取消，影响入口。骨架用两个带 `starts_at` 的触发表达，Q09 定后改。
6. **定时命令无执行方（沿用 G-04）**：PRC-32「登记延期视为批准」、PRC-26 与 PRC-37 的到期提醒、PRC-91 封期前提醒都靠定时，聚合稿没有任何聚合引用 platform.scheduling。
7. **PRC-36 封存步的归属（沿用 WT 结构 2）**：骨架保留「封存交付物」「封存证据」两步，由 B-29 经 api 调 B-28 的命令（方向正确）；备选是封存归 PRC-28、PRC-33 收尾、结项编排只读。不改归属，请 one-architecture 择一。

### VS-02·新用语

交 one-glossary；命令缺的不进 yaml 的命令名以外：

| 用语 | 位置 | 说明 |
|---|---|---|
| 撤回工时单 | AGG-39 命令 | 已建有「撤回本轮」「取消整个审批」两个动作，是否合并为一条命令或拆两条，请定 |
| 修改工时单 | AGG-39 命令 | 已建 revise；退回或草稿状态下改内容 |
| 登记资料需求 | AGG-32 命令 | 含所需资料清单与计划提供日 |
| 标记不主张 | AGG-30 命令 | 现「标记待主张」同一命令按参数记待主张或不主张及理由；建议保持一条命令，用语改为「标记主张状态」或另立，请定 |
| 提出产值估计、复核产值估计 | 管理产值 | 见结构问题 1；词表另需 `OutputValueEstimate` 与「收入三口径对照」（Q04_STORAGE） |
| 工作包「锁定」与「已发布」 | AGG-20 | 聚合稿命令叫锁定、不变量叫已发布（INV-42），状态词需统一 |
| 审批单据类型暂名 | `approval.doc_type` | 项目预算审批、工时审批、结项申请（走审批平台）；聚合内裁定的：资源分配确认、发包人资料确认、完工估算批准、设计变更内审、不符合项关闭、项目复盘评审；外部答复：延期申请答复 |
| 权限码暂名 | `permission` | 项目.建立 / 开始执行 / 暂停 / 恢复 / 终止 / 取消 / 归档，项目WBS.建立 / 推进阶段 / 管理工作包，项目团队.维护，交付计划.编制 / 登记批准 / 锁定，项目预算.编制 / 审批 / 锁定，资源需求.提出，资源分配.确认，工时单.提交 / 审批，工时.认定有效性，发包人资料.登记需求 / 登记提供 / 确认 / 退回，完工估算.提出 / 批准，设计变更.提出 / 认定归因 / 内审 / 发出 / 标记待主张 / 关闭，延期申请.通知 / 提交说明 / 登记批准 / 登记拒绝，不符合项.登记 / 确认 / 让步接受 / 关闭，返工认定.认定 / 撤销，风险与问题.登记 / 处置 / 接受，证据.采集 / 关联 / 封存，交付物.封存，结项检查.发起，结项申请.审批，满意度调查.发出 / 登记回收 / 评分 / 登记跟进，项目复盘.编制 / 评审 / 发布，阶段门预警.查看 |
| 页面暂名 | `page` | 项目建立页、项目概览页、项目状态处置页、项目归档页、项目WBS编制页、项目WBS进度页、工作包管理页、项目团队页、交付计划编制页 / 批准登记页 / 锁定页、项目预算编制页 / 锁定页、审批待办页（平台）、资源需求编辑页、资源分配确认页、工时填报页、工时认定页、发包人资料需求页 / 登记页 / 确认页、完工估算编制页 / 批准页、设计变更编辑页 / 归因页 / 内审页 / 发出登记页 / 主张标记页 / 关闭页、延期通知登记页 / 延期说明登记页 / 延期答复登记页、不符合项登记页 / 确认页 / 让步登记页 / 关闭页、返工认定页、项目风险与问题登记页 / 处置页 / 接受页、证据采集页 / 关联页 / 封存页、交付物封存页、结项检查页、满意度调查发出页 / 回收登记页 / 评分页 / 跟进页、项目复盘编辑页 / 评审页 / 发布页、项目阶段门预警页 |
| 事件「审批已完成」 | platform.approval | 词表已有，PRC-19、23、36 订阅 |

### VS-02·格式问题

1. **多触发进入不同步骤**：CONVENTIONS 的 `triggers` 没有指向步骤的字段。PRC-24（直接终止、直接取消）、PRC-26（发包人主动提供）、PRC-32（定时视为批准）用 `starts_at: <步序号>` 表达。建议采纳。
2. **并行会签的分支审批人**：PRC-23 的审批平台按项目工时、售前工时、管理间接工时并行路由到不同审批人（已建流程），`approval.approver` 只有单值。骨架 approver 写主分支 SH-E2，其余写在 desc。建议 `approval` 加 `branches: [{when, approver}]`。
3. **单聚合维护字段**：骨架使用 `states`（可分多个对象的状态机）、`transitions {from, to, command, by, emits, rule, kind, note, aggregate}`、`pages {page, permission, commands}`、`also_aggregates`，并保留 v1 的 `commands`。建议写入 CONVENTIONS。
4. **读模型字段**：`events`（含发布 BC）、`reads`、`query {grain, dimensions, gate_rule, stage_exits, warnings, refresh, freshness}`、`permission`、`page`、`scope`、`migration`、`scenarios`。建议写入 CONVENTIONS。
5. **事件策略多步**：PRC-91 用 `steps` 列表（自动生成待认定清单，再由项目经理认定），v1 为单个 `step`。建议事件策略统一为 `steps`。
6. **`timeout.after` 写合同参数或建议值**：PRC-18（P5D）、PRC-32（合同约定期限，缺省 P5D）、PRC-37、PRC-91 的值是合同参数或待定建议值，非纯 ISO 8601；建议允许写参数名，并以 `suggested: true` 标出待用户定的值。
7. **外部中断的表达**：员工撤回审批（PRC-23 第 3 步）没有对应的 BPMN 元素字段，骨架用 decision 分支近似；建议约定为边界消息事件。
8. **同一步多个拒绝出口共用一个结局**：PRC-23 第 2 步四条拒绝分支都指向结局「未提交」，PRC-36 第 4 步同理。建议 CONVENTIONS 明确可行。
9. **segregation 同角色**：PRC-30 的提出人与批准人都是 SH-M1，写成 `roles: [SH-M1, SH-M1]` 表示「须不同人」。建议约定。
10. **平台审批步的 actor**：写 `actor: platform`、`where: in_system`，并补 `permission`、`page`（审批待办页）。建议确认这样画成用户任务。

### VS-02·待用户定

每条：建议，理由。（审批人、条件规则卡与 ADR 未定的，已按 AEC 通行做法写入骨架并在此列出。）

1. **PRC-23 工时单的法人口径**：骨架写「员工所属法人」。建议取核算法人（员工三口径之一，Q-09）。理由：工时按核算法人归集成本；已建代码按员工所属法人取，请核对是否同一口径。
2. **PRC-23 不相容职务**：员工不得审批自己的工时单；项目经理本人的项目工时由部门负责人审批。建议采纳。理由：通行的不相容职务隔离；已建审批流程是否已拦截未核实。
3. **PRC-23 在途迁入**：审批中的工时单以新审批实例重新提交（轮次从 1 起）。建议采纳。理由：旧审批实例不迁入（U-1 不接老系统）。
4. **PRC-24 终止、取消是否审批**：建议终止项目经部门负责人或项目总监审批（单据暂名「项目终止审批」，走 platform.approval），取消项目由项目总监单级；暂停超过 N 日（建议 30 日）未处置提醒项目总监；执行中允许不经暂停直接终止。理由：终止触发 B-07 清算，影响成本；但这需给 AGG-19 加审批中状态，属结构变化，故骨架未加。
5. **PRC-19 预算审批**：沿用 WT 待用户定 2：项目总监（SH-M1）单级，驳回回编制；金额或项目级别分级留待规则卡。另建议编制人与审批人不得同一人。
6. **PRC-18 内部锁定的执行人**：骨架为项目经理自锁；建议维持。理由：内部基线不涉及客户，v1 命令执行人即项目经理；若要部门负责人确认，则内部锁定也须审批。
7. **PRC-20 资源分配确认人**：建议被分配人所属部门负责人（SH-M1）单级，聚合内确认。理由：资源归部门管理；G-02 已定不走审批流。
8. **PRC-26 资料需求与逾期提醒**：建议项目经理登记所需资料与计划提供日，逾期提醒项目经理、再升级项目总监。理由：P-26 需要计划日；升级对象为推荐。
9. **PRC-30 提出与批准分离，及管理产值**：建议项目总监提出、部门负责人批准（同为 SH-M1 角色，须不同人）；每期管理产值另立流程（见结构问题 1），由项目经理填、部门负责人复核。理由：自提自批无制衡；v1 两步都是项目总监。
10. **PRC-31 内审与待主张时限**：建议内审人不得为提出人；「待主张」超过 N 日未被协议变更或索赔引用预警（N 建议 30 日，或取合同索赔时限）。理由：校审分离；P-24 需有时限。
11. **PRC-32 期限缺省与「视为批准」**：5、10、5 天取示范文本缺省，项目经理按合同录入；视为批准仅限合同约定的延期答复，不借用于交付物（ADR-0049）。理由：合同约定因项目而异。
12. **PRC-34 处置期限**：按等级体系版本配置，建议严重 3 日、一般 15 日起步，由质量负责人调。理由：P-23 需要期限，聚合稿无；该数值无依据，仅为建议。
13. **PRC-34 登记人与关闭人分离**：建议采纳，理由同 PRC-31。
14. **PRC-36 结项审批层级**（沿用 WT 待用户定 7）：建议项目总监单级，财务（SH-E6）知会；检查未通过时本骨架结束并要求重新发起（结局「检查未通过」），备选是回第 1 步补封存。理由：结项触发 B-07 清算，多级更稳，但 ADR-0050/0051 与 §4.6 未定层级。
15. **PRC-37 低分阈值与回收提醒期**：阈值沿用 P-45 待定，建议评分尺度的 70% 以下（五分制低于 3.5）；回收提醒期建议 14 日，只预警。理由：行业问卷通行做法；评分尺度尚未定。
16. **PRC-38 评审人不得为编制人**：建议采纳；复盘建议在项目结项前发布（门只预警，不拦截）。理由：复盘评审的独立性。
17. **PRC-91 认定方式**：骨架按 v1，由项目经理批量认定，系统只预置返工并发待办。建议改为系统按规则默认认定（返工由返工认定归类，其余默认有效，按合同口径标可计费），项目经理只处理例外；封期前提醒提前量建议 3 个工作日。理由：逐条人工认定在工时量大时负担重。
18. **PRC-22 是否推送企业微信预警**：现仅在页面展示，推送需 reporting 调 platform.integration，该边未登记。建议先页面展示，推送随需求另议。
19. **PRC-17 迁入多法人项目**：老项目若挂多个法人，按 Q-06 拆为一项目一承担法人；拆分数量与口径需 one-ops 取证（只出计数，不进会话与仓库明细），请确认由谁做。
20. **角色映射**（沿用 WT 待用户定 1）：项目总监、部门负责人均用 SH-M1，涉及 PRC-19、20、24、30、36。建议维持，理由：stakeholders 无独立编号。

## 方法验证 / 推翻

| 做法 | 结论 | 证据 |
|---|---|---|
| 先做一个用户确认的样板（PRC-28）再铺开，样板作子代理范本 | 验证 | 样板 5c94de01→a44f48a9；四批子代理按范本补齐新字段，bpmn_gen 41/41「检查：通过」 |
| 按价值流分批、每批一个 sonnet 子代理并行起草 | 验证 | VS-01–04 四批同时完成，合并无冲突（704e6d31、cc988945、a85a5325、a50ff861） |
| 子代理共用一个输出目录 | 推翻 | VS-02 批中间文件被别批覆盖一次；改为每批私有子目录（out3/<批>/） |
| 图由骨架生成、不手画 | 验证 | 手画 PRC-28.bpmn 与生成器结果都过 bpmn_check；铺开 41 个流程未手画一张 |
| 子代理字段约定靠派工单转述 | 推翻（部分） | 子代理自拟 decision.outcome、states/transitions、pending 等字段，事后才收进 CONVENTIONS 第 5 节；应先把约定文件作为必读 |
| 用旧版聚合稿跑 closeout_check | 推翻 | #384 分支聚合稿落后于 governance 分支，AGG-13 新命令被误判缺失；须用最新聚合稿跑 |
| 跨会话报完成点前核对文件内容而非只核提交号 | 验证（教训） | 8946b0db 被推送钩子拦截的追加未落盘，报告失实；334115cc 补推 |
| 通知写成步骤 | 推翻 | 生成器把通知步骤判为缺命令；改为 notify 属性（CONVENTIONS 第 5 节） |
| 子代理先自查（otherwise 收尾、goto 范围、outcome、permission/page）再交稿 | 验证 | 四批交稿后生成器零错误 |
| 审批节点先问「能否规则化」再保留 | 待验证 | 统一改写批进行中 |

# 统一改写 · VS-03（CONVENTIONS 第 5 节；2026-10-16，子代理 VS03 起草，主会话合并）


### VS-03·接缝问题

1. **S-25 ② 三个检查审批的复核结论**：PRC-43 第 7 步（检查记录审核）、PRC-44 第 3 步（不符合项复核）、PRC-45 第 5 步（整改复查结论）都保留人工，但性质是专业复核（对照 PRC-28 校审，用户已确认的范本），不涉资金，也不是「谁负责谁批」的责任审批，故写 `engine: aggregate`、approver SH-E4（专业角色）、`why_human`，不写 `escalation_rules`，**不走 platform.approval，不需要加边 B-26 / B-28 → platform.approval**。若 one-architecture 认为 AGG-35「已审核」、AGG-28「复核 / 闭合」属 F-2 所说的审批状态，则**需加边 B-26 / B-28 → platform.approval**；届时三处改 `engine: platform.approval`、approver 改 `cost_object_owner`、`escalation_rules: []`，下一步各加 `cross_bc: {mode: event, direction: in, bc: platform.approval, name: 审批已通过 / 审批已驳回}`，驳回去向不变。请 one-architecture 在合并前裁定。
2. **AGG-110 挂接目标加「检查」「不符合项」后的依赖方向**：AGG-110 在 B-28，检查（AGG-35）在 B-26，B-26 是 B-28 的下游，所以检查 ID 在 AGG-110 里只能是「不校验」的类型化 ID（与 AGG-28 的来源检查同类，不成依赖边）；不符合项（AGG-28）与 AGG-110 同在 B-28，可校验。三条「关联证据」的 cross_bc 均为 B-26 → B-28 api，与 §4.1 一致。
3. **S-25 ① 编号疑为笔误**：STRUCTURAL_ITEMS 写「不符合项（AGG-29）」，聚合稿中 AGG-28 是不符合项、AGG-29 是返工认定。本稿按 AGG-28 写，请回写聚合稿时核对。
4. **外部检测（AGG-37）是否也加挂目标**：PRC-46 的外部检测报告证据由 AGG-37 直接引用 AGG-38（INV-57 只引用），本稿未经 AGG-110 关联；诉讼关联键要不要覆盖外部检测，请 one-architecture 裁定（建议不加：采信结论已在 PRC-45 复查证据中引用，经不符合项目标可回溯）。
5. 其余 cross_bc 方向逐条对过 §4.1（B-26 → B-01、B-04、B-05、B-13、B-28 api；ContractSigned B-05 → B-26 事件；B-26 → B-02 读服务产品），无反向。

### VS-03·结构问题

- 无新增。沿用 D2 / S-2：等级体系版本无发布聚合（PRC-44 第 1 步仍选版本）。

### VS-03·新用语

- 命令缺口（沿用，仍未解决）：「登记不符合项送达」「记录重大险情已报告」（AGG-28，PRC-44 第 5 步，gap 标注）。
- 撤回类命令缺口（brief 第 6 项）：本批没有写 `withdraw`，因为聚合稿没有对应命令。提议新增：「取消检查」（AGG-35，已计划 / 已安排阶段，客户取消或改期）、「撤销检查计划登记」（AGG-34，已登记未生效阶段）、「撤销整改申报登记」（AGG-28，申报登记有误）。证据误传已有「作废证据」（AGG-38），PRC-43 第 4 步、PRC-45 第 3 步可用，未单列为步骤。若这些命令采纳，再补 `withdraw: {to, outcome: 已撤回}` 与场景。
- 页面与权限暂名（无命令，仅 PRC-44 第 2 步人工关联证据）：权限 `证据.关联`，页面 `证据关联页`。
- 拟规则（交 one-rules 出规则卡，见「规则化」）：`拟规则-VS03-01` … `拟规则-VS03-04`。

### VS-03·格式问题

1. **生成器未读 `decision[].outcome`**：CONVENTIONS 第 5 节已采纳该字段，但 `closeout/bpmn_gen.py` 的 `outcome_for` 只读 `step.outcome`，所以用现版本生成会出现「outcomes 有多个而步骤未写 outcome，结束事件名按位置猜测」警告（PRC-41、44、45、46）。本批用本地补丁副本（SCR/gen3/bpmn_gen.py，只多读 decision.outcome）验证，结束事件名与结局一一对应。请 one-architecture 合入生成器。
2. 去掉了 aggregate 审批上的 `multi_level` 字段（约定里没有）；`on_reject` 保留。
3. PRC-41 第 4 步、PRC-45 第 2 步 `timeout.after` 原为中文描述，改为 ISO 8601（P30D、P14D、P10D、P3D、P30D），配置参数性质写进 desc，具体值见待用户定。
4. PRC-46 的超时从第 1 步（登记委托）移到第 2 步（采集证据，即等待报告到达），第 1 步只是几分钟的登记，超时挂在它上面语义不对；时限值不变。
5. 单聚合 PRC-42 补了 `outcomes`、`frequency_ref`、`org_needs` 与步骤 `effort`、出口 `p`，沿用 `states` / `transitions` 写法。
6. 同一 SH 在 roles 出现两个 as（PRC-43、PRC-45 的 SH-E4）时，`org_needs` 各写一份，审批权一栏写得不同。
7. 检查锁定、证据封存等「系统调用 api」的自动步骤不写 `effort`；读步骤（`read: true`）生成器仍报「缺 command，用缺省」，属已知，不影响检查通过。

### VS-03·待用户定

1. 三个检查审批（检查记录审核、不符合项复核、整改复查结论）保留人工专业复核、不走审批平台。建议：保留，aggregate 内单级，approver 检查负责人。理由：判断对象是证据是否足以支撑结论、发现是否成立、整改是否达标，属专业判断且不涉资金；机械项已拆成规则（见规则化）。回避由 segregation 与「同一人拒绝」守住。
2. 四条拟规则（VS03-01…04）是否按本稿口径采纳（见规则化）。
3. 期限默认值（经验估计）：检查记录提交 P3D（原有）、整改申报期限占位 P14D（实际按等级与合同约定）、复查期限 P10D（原有）、外部报告出具期限 P30D（原有）、计划到期提醒提前量 P30D。请业务确认或给出合同模板口径。
4. 证据关联由系统在采集成功后自动做（PRC-43 第 5 步、PRC-45 第 4 步，检查人员不重复录入）；PRC-44 第 2 步由检查人员选择哪些证据支撑该不符合项，仍人工。建议如此。理由：检查与复查场景下证据本来就归属该次检查或不符合项，无需选择；不符合项场景同一次检查的证据未必都支撑同一条发现。
5. 复核不成立（PRC-44 第 7 步）用「关闭不符合项」承接、「标记不再受托」是否必填原因、复核后送达回执落点、整改责任方是否开放客户端登记等，沿用 S3 / VS03 原待用户定，不重列。
6. 负担参数（effort、wait、p）均为经验估计，取证（E-B1、E-B2、E-B3、E-B5）后校准；其中现场采集 180 / 420 分钟、整改等待 240 / 720 小时、报告等待 360 / 720 小时变动最大。

### VS-03·规则化

本批三个审批没有整体规则化，但从人工审批里拆出了可由系统守的条件，交 one-rules 出规则卡：

| 拟规则 | 流程与步骤 | 改成什么 | 规则文字（拟） |
|---|---|---|---|
| 拟规则-VS03-01 检查项证据 | PRC-43 第 6 步「提交检查记录」，前移自第 7 步审核 | 提交时拒绝，不进入人工审核 | 判定结果为通过或不符合的检查项须引用至少一条本检查已关联的证据；未检项须选原因类型并写说明；否则拒绝提交并提示缺项。审核人只判断「够不够」，不再核对「有没有」 |
| 拟规则-VS03-02 等级默认值 | PRC-44 第 1 步「登记不符合项」 | 默认值与偏离标示 | 不符合项等级值默认取检查表模板（AGG-36）的等级映射结果；登记人改动须写理由，复核页标示偏离项。复核人只重点判断偏离是否有理，不逐项重算 |
| 拟规则-VS03-03 复查未通过自动流转 | PRC-45 第 5 步「复查检查」 | 自动流转 | 复查结论为未通过时，系统自动生成「退回整改」待办，带出轮次加 1 与新整改期限输入，不由人另选去向；复查通过才进入闭合 |
| 拟规则-VS03-04 闭合证据时序 | PRC-45 第 6 步「闭合不符合项」 | 拒绝闭合 | 闭合须引用复查或整改证据（INV-50），且所引复查证据的采集时间不早于整改申报日；否则拒绝并告警 |

此外：检查记录的提交人与审核人、不符合项的登记人与复核人不得为同一人，已由 `segregation` 与「同一人拒绝」守住，不留给审批人。PRC-46 采信外部检测报告本来就是检查负责人本人的技术判断（无第二人审批），未写 `approval`，也未规则化；机构一致已是 P-30 预警。

# 统一改写 · VS-02（CONVENTIONS 第 5 节；2026-10-16，子代理 VS02 起草，主会话合并）


范围：业务流程 PRC-17、18、19、20、23、24、26、28、30、31、32、34、36、37、38；单聚合维护 PRC-21、27、33；读模型 PRC-22；事件策略 PRC-91。依据 `CONVENTIONS.md` 第 5 节、`APPROVAL_RULES.yaml`、`PROCESS_QUALITY_CHECKLIST.md`、`BURDEN_ANALYSIS_PLAN.md`。PRC-28 是用户确认的样板，只补字段，业务内容未改，唯一例外见「格式问题」第 4 条。

### VS-02·接缝问题

1. **审批结果回调事件与 §4.1 边方式（PRC-19 步 2、4；PRC-23 步 4–7；PRC-36 步 3、6）**：回调已按第 5 节改写为下一步的 `cross_bc: event in platform.approval 审批已通过 / 审批已驳回`。聚合稿 §4.1 中 B-04、B-06、B-29 → `platform.approval` 只登记了 api 方式，没有事件方式（自查脚本报 8 处）。沿用 S-22 的做法：给这三条既有边补「事件」方式，不加新边。
2. **INV-67 与 INV-92 的措辞（PRC-23 步 2、步 8；PRC-91 步 2）**：INV-67 写「期间关账后工时只能冲销或修正」，INV-92 写「业务日期在已关账期间的，路由到当前开放期间并标原期间」。按第 5 节：系统自动触账（PRC-23 步 8 过账候选）遇已关账期间路由开放期间并标原期间，不拒绝；人工发起（PRC-23 步 2 提交工时单、PRC-91 步 2 认定工时有效性）只提示并改走冲销或修正，不设审批。建议在聚合稿 INV-67 注明「人工提交拒绝并提示；系统自动过账路由开放期间（INV-92）」，以免两条不变量被读成冲突。
3. **PRC-28 对 B-08、B-05 的事件边**：自查脚本核对 B-08 → B-28、B-05 → B-28 的事件边存在，方向无误；`AcceptanceReturned` 订阅后在 AGG-44 无反应命令，沿用 WT 接缝 2，未变。

### VS-02·结构问题

1. **管理产值无聚合命令（PRC-30）**：沿用前稿，未变。
2. **AGG-32 缺「登记资料需求」（PRC-26 步 1）、AGG-39 缺「撤回工时单」（PRC-23 步 7）**：沿用前稿，步骤仍标 `gap`。本次逐条核对了命令缺口（见「新用语」第 3 条），其余步骤的命令均取自聚合稿 §2.2 原文。
3. **设计变更与延期通知没有撤销命令（PRC-31、PRC-32）**：第 6 项要求可撤回或撤销的步骤写 `withdraw`。AGG-30、AGG-31 的命令列没有「撤销设计变更」「撤回延期通知」，不自造，故这两条流程未写 `withdraw`；是否需要见「待用户定」第 10 条。
4. **PRC-24 取消项目的「无任何投入」前置**：沿用前稿（B-04 读不到投入，由 B-07 订阅后核对）。

### VS-02·新用语

1. **`审批已通过`、`审批已驳回`**（`platform.approval` 领域事件，kind: domain_event）：第 5 节要求用这两个名字，词表目前只有 `审批已完成`（approval_completed）。请 one-glossary 登记，写明使用范围（领域事件，供订阅方 B-04、B-06、B-29 等）和 bc `platform.approval`。退回、驳回、撤回、取消四种非通过结果都用「审批已驳回」，用结果类型区分（见格式问题第 6 条）。
2. **结局词**（阶段出口用语，非词表条目，请 one-glossary 核对）：`未建立`（PRC-17）、`已撤回`（PRC-19、PRC-23、PRC-36）、`已认定`、`未认定`（PRC-91）、`检查未通过`（PRC-36，前稿已有）。
3. **命令缺口清单（第 8 项）**：
   - 只读步（`read: true`，不算缺口）：PRC-17 步 1、PRC-19 步 1、PRC-20 步 2、PRC-23 步 1、PRC-28 步 1、PRC-36 步 4、PRC-91 步 1。
   - 平台步（`platform_task: true`，不算缺口）：PRC-19 步 3、PRC-23 步 3、PRC-36 步 5。
   - 编排命令（B-29 无聚合，命令取自 §4.6 的 REF-02）：PRC-36 步 3「发起结项检查」；步 5「提交结项申请」是平台步。
   - 真缺口（标 `gap`，沿用）：PRC-26 步 1「登记资料需求」（AGG-32）；PRC-23 步 7「撤回工时单」（AGG-39）。
   - 命令在聚合稿但聚合归属需说明：PRC-36 步 1、步 2 是 B-29 经 api 调 B-28 的命令，沿用。
4. **`审批已完成`**在本批 yaml 与场景中已无残留；「视为同意期满」类命令名在本批没有出现（本批只有 PRC-28 步 8、PRC-22 读模型提到期满判定在 B-08），无须改为「认定视为同意成立」。

### VS-02·格式问题

1. **`decision[].outcome` 生成器不读**：`bpmn_gen.py` 的 `outcome_for` 只读步骤级 `outcome`，否则按 `when` 文字匹配 `outcomes` 词。我在每个结束出口同时写了 `decision[].outcome`，并把结局词写进 `when` 文字（如「提交人已撤回审批」「目标期间已关账（路由…）」），使生成器不再「按位置猜测」。建议生成器改读 `decision[].outcome`。
2. **只读步与平台步被报「缺 command」**：生成器对 `read: true`、`platform_task: true` 的步骤也警告（本批 6 个流程各 1 个）。建议这两类步骤不计该警告。
3. **超时升级与原任务同泳道**：PRC-18 步 2（P-17 提醒项目经理）、PRC-28 步 5（催签）、PRC-32 步 2、PRC-37 步 1（P-45 提醒）的升级对象就是执行人本人，业务上是提醒而非升级，生成器警告同泳道。不改业务口径，保留警告，写入 QC。
4. **PRC-28 步 5 的 otherwise 由 `end` 改为 `goto 5`（唯一一处触及样板的业务面）**：该出口原指向 end 且无结局词，但业务含义是「客户尚未答复，继续催签」（场景 S06），并非结束；按第 7 项要求每个结束出口须有结局，故改为回到本步等待，不新增结局词。请 one-architecture 确认；否决则需在 `outcomes` 增加「已送达待答复」。
5. **`starts_at` 生成器不读**：PRC-17、19、24、26、32 的多触发用 `starts_at` 指向入口步，生成器把所有开始事件都连到第 1 步，图上入口不准。建议生成器读 `starts_at`。PRC-26 的 time 触发「计划提供日已过」与步 1 的 `timeout` 重复，已删（提醒由 `timeout` 承担）。
6. **审批结果类型**：回调事件只有「通过 / 驳回」两个名字，退回（PRC-23 步 5）、撤回或取消（步 7）用同一事件名加结果类型区分；请审批平台确认事件载荷带结果类型。
7. **引擎为 aggregate 的非专业确认**：PRC-20 步 3（资源分配确认）、PRC-30 步 2（批准完工估算）、PRC-27「接受风险与问题」是管理确认，不是专业复核。我写成 `approver: cost_object_owner`，PRC-20 `escalation_rules: []`，PRC-30 引 AR-11；专业校审（PRC-26 步 3、PRC-28 步 3、PRC-31 步 3、PRC-34 步 6、PRC-38 步 2）写专业角色、不写 `escalation_rules`。请 one-architecture 确认这一分法。
8. **单聚合维护的 `effort`**：写在 `commands[]` 每条命令上（单聚合维护没有 `steps`）；读模型 `frequency_ref` 写「不适用」。
9. **`approval.approver` 写 `cost_object_owner` 时的解析说明**写在 `desc` 与 `why_human`，没有新增字段（前稿提议的 `branches` 仍未采纳，PRC-23 并行会签分支写在 `desc`）。

### VS-02·待用户定

沿用前稿的待用户定（WT 与 S2 小节）未变；以下是本次新增或口径变化的，建议值都是经验估计，用户定后改。

1. **项目级审批的「归属对象」（PRC-19 预算、PRC-30 完工估算、PRC-36 结项）**：建议归属对象取项目承担部门，责任人是承担部门负责人（SH-M1），项目经理只提交；加审规则引 AR-11（部门负责人本人提交时上提一级）。理由：若归属对象取项目，则责任人就是项目经理，也就是提交人，每次都命中 AR-11，「正常一级」落空。
2. **PRC-23 管理间接工时的审批人**：已建流程路由到员工的一级汇报人，`cost_object_owner` 解析的是员工所在部门的责任人。建议保持已建口径（一级汇报人通常就是部门责任人），在权责对照表里按此映射；项目工时到项目经理、售前工时到商机承担部门负责人，与已建口径和「谁负责谁批」一致，无冲突。请确认。
3. **PRC-36 结项审批可否有条件免审**：三项拒绝检查已是 INV-40 的守卫，审批人真正判断的只有两项预警和「项目是否实质完成」。建议改为：三项拒绝检查通过且无预警项时项目经理提交即结项，有预警项才进一级审批。拟规则文字见「规则化」。因 D-9 与 ADR-0050/0051 已定结项审批走 `platform.approval`，本批未改，用户同意后再改 ADR。
4. **PRC-19 预算审批可否有条件免审**：建议预算毛利不低于赢单报价毛利且总工时不超报价估算时自动锁定，否则进一级审批。理由同上；本批未改。
5. **PRC-30 完工估算批准是否保留**：估算不入收入确认，超预算已预警（P-19）。建议保留部门负责人一级批准、不加审；或改成「提出即生效，超预算预警」。请用户选。
6. **加审规则 AR-11、AR-12 的处置方式**【已定：用户答「上级代批」（intent 2026-10-16-164324），APPROVAL_RULES AR-11/12 已改，本条关闭】：PRC-19、30、36、27 引 AR-11，PRC-23 引 AR-11、AR-12；`APPROVAL_RULES.yaml` 这两条的处置方式（上级代批 / 按异常加审 / 抽查，指定代理人 / 退回补登责任人）仍待用户定。
7. **PRC-27 接受风险与问题是否分金额或严重度加审**：现单级（归属对象责任人）。建议严重度为高的加审一级，严重度分级聚合稿没有，需先定。
8. **预算与结项被撤回后的再发起**：PRC-19 加了「驳回或撤回后重新提交」的人工触发（`starts_at: 2`）；PRC-36 撤回后项目经理重新发起检查。请确认可以。
9. **负担参数全部是经验估计**：`effort`、`wait`、`p`、`frequency_ref` 均按经验先估，取证（E-B1 单据月度件数、E-B2 办结时长、E-B4 岗位待办、E-B5 客户签认等待）后校准。优先取 E-B5（交付物送达到签认）和 E-B2（工时审批时长），它们决定权责发生制的跨期风险。
10. **是否补「撤销设计变更」「撤回延期通知」命令**：见结构问题 3。建议补，理由：这两件事在线下常发生，不补则无法追溯撤销，也无法写 `withdraw`。需 one-architecture 给 AGG-30、AGG-31 加命令。
11. **`org_needs` 里的 `department_hint`、`position_hint`**：按角色的通行称呼写，未对照岗位体系；岗位体系重梳定后统一改一次。

### VS-02·规则化

本批**没有审批被整个删去**。逐条复核了保留的审批节点，结论如下（保留的都写了 `why_human`）：

| 流程 步 | 审批 | 结论与理由 |
|---|---|---|
| PRC-19 步 3 | 项目预算审批（platform.approval） | 保留。毛利比较已是预警（P-18），预算工时与非人工成本是否合理要人判断。条件免审方案见待用户定 4 |
| PRC-20 步 3 | 资源分配确认（aggregate） | 保留。产能已由系统算并预警（P-27），派谁是部门负责人的取舍 |
| PRC-23 步 3 | 工时审批（platform.approval，已建） | 保留。系统已守卫工作包授权、请假、标准工时、一人一日一单，剩下「是否确在该项目投入」只有责任人知道；同一审批人重复轮次自动通过已是规则 |
| PRC-26 步 3 | 发包人资料确认（aggregate，专业核对） | 保留。完整性与可用性是专业判断 |
| PRC-28 步 3 | 交付物校审（aggregate，专业复核） | 保留。样板 |
| PRC-30 步 2 | 完工估算批准（aggregate） | 保留，见待用户定 5 |
| PRC-31 步 3 | 设计变更内审（aggregate，专业复核） | 保留 |
| PRC-34 步 6 | 不符合项关闭（aggregate，专业复核） | 保留；本次补了「复核未通过退回」出口 |
| PRC-36 步 5 | 结项申请（platform.approval） | 保留，条件免审方案见待用户定 3 |
| PRC-38 步 2 | 项目复盘评审（aggregate，专业复核） | 保留 |
| PRC-27「接受风险与问题」 | 风险接受（aggregate） | 保留；接受人与依据由 INV-113 守卫，是否接受要责任人判断 |

本批**从拒绝改为自动分流或提示**的规则（交 one-rules 出规则卡，与 `APPROVAL_RULES.yaml` 的 AR-05 / RULE-408 同族）：

1. **PRC-23 步 8，系统自动过账遇已关账期间**：原为转冲销或修正并告警，现为路由开放期间。拟规则文字：「系统自动触发的工时过账候选，目标期间已关账时，路由到该法人当前开放期间并标原期间（INV-92、ADR-0045），通知财务核对；不拒绝、不设审批。缺锁定费率版本仍失败并告警（INV-37）。」
2. **PRC-23 步 2、PRC-91 步 2，人工发起遇已关账期间**：拟规则文字：「人工提交工时单或认定工时有效性，所属期间已关账时，提示并改走冲销或修正，不设审批（INV-67）。」

条件免审的两个候选（PRC-36、PRC-19）的拟规则文字：

3. （建议，未改，待用户定 3）「项目结项：三项拒绝检查（INV-40）均满足且预警项为零时，项目经理提交结项申请即调用『结项』，不经审批；存在预警项时进承担部门负责人一级审批，审批人只判断预警项是否可接受。」
4. （建议，未改，待用户定 4）「项目预算：预算毛利不低于赢单报价毛利，且各 WBS 计划工时合计不超过报价估算工时时，项目经理锁定即生效；否则进承担部门负责人一级审批。」

# 统一改写 · VS-04（CONVENTIONS 第 5 节；2026-10-16，子代理 VS04 起草，主会话合并）


范围：业务流程 PRC-47、48、49、50、54、57、58、60、61、63、66、86、140、141；事件策略 PRC-51、53、56、59、146、147、148；单聚合维护 PRC-52、68；读模型 PRC-62。验收场景 111 条（上一版 96 条）。依据：CONVENTIONS 第 5 节、APPROVAL_RULES.yaml、PROCESS_QUALITY_CHECKLIST、BURDEN_ANALYSIS_PLAN 第 1 节。上一版 VS-04 NOTES 中未被下列条目触及的问题仍然有效。

### VS-04·接缝问题

1. 审批回调（CONVENTIONS 第 5 节已定写法，仅提示）：本批全部写作 `cross_bc: {mode: event, direction: in, bc: platform.approval, name: 审批已通过 / 审批已驳回}`，落在审批结果之后的下一步（PRC-47 第 2、6 步，PRC-48 第 5 步，PRC-50 第 3 步，PRC-57 第 5、6 步，PRC-140 第 2 步，PRC-148 第 1、3 步）。事件由 platform.approval 发出、本 BC 订阅，与依赖边方向（本 BC → platform.approval）相反，规则 2 不能直接对照；沿上一版接缝 1，仍请 one-architecture 在 §4.1 注明「审批结果事件」例外。
2. 审批结果之后没有下一步时（PRC-48 驳回即结束、PRC-50 通过即结束、PRC-140 驳回即结束），回调事件写在审批所在步本身。
3. 撤回结果同样来自 platform.approval：撤回审批实例后本 BC 需要一个事件才能把聚合退回草案或释放占用。本批只写 `withdraw` 与结局，事件名「审批已撤回」未进 cross_bc（CONVENTIONS 只定了通过与驳回两个名字），请定后补；影响 PRC-47、48、50、57、140。
4. PRC-53 第 2 步 `goto: PRC-148`：表示 PRC-148 由 platform.approval 的结果事件触发，不是同步调用；事件策略之间的接续没有调用活动可画。
5. PRC-58 撤销红字信息确认发生在税务平台一侧（platform.integration），AGG-46 无对应命令，见「新用语·命令缺口」。
6. 其余接缝（PRC-56 与视为同意、PRC-148 过账方式、B-09 → B-10 / B-12 的 api、PRC-140 / 146 / 62 的 platform.scheduling）沿上一版 VS-04 接缝 2、3、4、7，无变化。

### VS-04·结构问题

1. PRC-57 开票申请审批由「一律审批」改为「系统自动准入 + 命中条件才人工审批」：协议依据（AR-04、AR-10，规则卡 RULE-407、RULE-411）与可开票额度（INV-21）、购买方已核实（P-3）都是系统可算的条件，由准入规则守卫；人工审批只留金额超阈值与关联方交易（AR-01、AR-02、AR-07）。原由财务经办的「开票申请准入」「驳回开票申请」两步改为系统自动执行；财务只保留开具与作废。见「规则化」第 1 条。
2. PRC-53 拆为两步：第 1 步系统占用收入额度（失败即「占用被拒」并告警），第 2 步平台任务「收入确认审批」（财务复核，ADR-0047 U-2 (a)）；审批结果由 PRC-148 接续。上一版把占用与发起审批放在同一步。
3. PRC-61 去掉独立的「催收提醒」步，并入第 1 步 notify；PRC-141 去掉独立的「退回补齐」步，并入第 2 步的退回出口与 notify。退回补齐原有 P10D 期限随之取消，补齐等待并入第 1 步 P30D，见待用户定 4。
4. PRC-52 的年度复核（财务负责人书面确认并留档、每年复核）在聚合稿 AGG-92 只有「开放证据类型」一个命令：复核维持写作再次开放（追加新生效版本，依据为复核确认文件）；复核后要关闭某类证据类型无命令可用，需 one-architecture 补。
5. 内控缺口（沿上一版，未自作主张加审批）：PRC-58 红冲已过账的销项发票命中 AR-06（guard: approval，异常加一级财务负责人）而本流程没有审批；PRC-61 核销坏账、PRC-68 终止协议、PRC-49 提交索赔均无审批。B-09 无到 platform.approval 的边（沿 S3 结构问题 2）、AGG-16 无审批状态、终止不在 AGG-13「审批中」之内，加审批需先改边或状态，见待用户定 3。
6. PRC-47 / 48 / 50 的审批由 `multi_level: true / false` 改为按 APPROVAL_RULES 的「一级责任人审批 + 命中加审规则最多再加两级」，`multi_level` 字段已删除；上一版待用户定 D3、D4、D7 由阈值待定（AR-01、AR-02，用户页 T-1）取代。

### VS-04·新用语

**命令缺口**（命令只取聚合稿 §2.2 原文；以下步骤的命令不在 SCR/agg_new.md §2.2 的对应聚合命令列，已在骨架步骤写 `gap`）：

| 流程步骤 | 聚合 | 现用命令 | 情况 |
|---|---|---|---|
| PRC-54 第 2 步 | AGG-44 | 认定视为同意成立 | 用户口径统一定名；§2.2 现为「确认视为同意期满」，随 Q-04 订正（上一版结构问题 1） |
| PRC-53 第 1 步 | AGG-44 | 占用收入额度 | 随 Q-04，§2.2 待补 |
| PRC-148 第 1 步 | AGG-44 | 追加收入确认事实并占用转实 | 随 Q-04，§2.2 待补 |
| PRC-148 第 3 步 | AGG-44 | 释放收入额度占用 | 随 Q-04，§2.2 待补 |
| PRC-140 第 4 步 | AGG-13 | 标记协议借阅逾期（事件：协议借阅已逾期） | SCR 聚合稿 AGG-13 命令列与事件列都没有；回补 NOTES 称已列，请核对 |
| PRC-86 第 3 步 | AGG-49 | （无） | B-09 订阅 B-10「借款付款已成功」后无记录命令 |
| PRC-58 第 1 步撤回 | AGG-46 | （无） | 撤销红字信息确认，税务平台一侧可撤销 |

其他新用语：

1. 事件：审批已通过、审批已驳回（platform.approval 通用事件，载荷带单据类型与实例号；替换 PRC-140「合同借阅审批已通过」、PRC-148「收入确认审批已通过 / 已驳回」的专名；触发列表 triggers 里保留单据专名以便阅读）；审批已撤回（见接缝 3，待定）。
2. 平台任务名：收入确认审批（PRC-53 第 2 步）。
3. 结局词新增：已撤回（PRC-47、48、57、58、140）、审批已发起、占用被拒（PRC-53）、收入已确认、转实失败待处置、占用已释放（PRC-148）、应付已确认、报销已置已付（PRC-147）。结局词删除：因期间已关账未开具、因期间已关账未红冲、因期间已关账未核销、因期间已关账未登记兑付、因期间已关账被拒（均随期间闸门改为不拒绝）。PRC-60 的「待认领」只剩「流水无法对应」一个来源。
4. 审批归属对象的写法（字段 `approval.owner_object`，见格式问题 2）：协议承担部门、协议所属项目、借阅人所在部门、承担法人（财务负责人复核）。
5. 命令名统一：「认定视为同意期满」「确认视为同意期满」→「认定视为同意成立」（PRC-54 及说明文字）。

### VS-04·格式问题

1. 决策出口 `decision[].outcome` 与步骤 `withdraw`：本批全部多结局流程已在结束的出口写 outcome。`withdraw.to` 为 `end` 时，`outcome` 必在流程 outcomes 内，并同时在 decision 里写一个对应出口（便于画图与测场景）；`to` 为步骤号时（PRC-49 第 2 步、PRC-50 第 2 步），`outcome: 已撤回` 只是路径标签，不进 outcomes，最终结局取后续步骤（索赔已拒绝或撤回、限额已释放）。
2. 新增字段 `approval.owner_object`：说明 `approver: cost_object_owner` 解析到哪个对象的责任人（CONVENTIONS 第 2 节没有写这一层）；请 one-architecture 裁定是否入约定，或改由 APPROVAL_RULES 的解析规则统一给出。
3. 审批嵌在提交步上时（PRC-50 第 2 步），提交人的耗时写步骤 `effort`，审批人的耗时写 `approval.effort`；审批是独立平台步时（PRC-47、48、57、140），步骤 `effort` 是审批人的耗时（PRC-140 合并了申请填写）。
4. 事件策略 PRC-53、147、148 增写 `outcomes`、`decision`（含 outcome）、`roles`；PRC-146 的催还步改为平台步骤（`actor: platform`、`where: automatic`、`platform_task: true`），不计命令缺口。
5. 单聚合维护 PRC-52 增 `periodic_review`（`cycle: P1Y`、`by`、`action`、`notify`）与 `roles`；转移上增 `evidence`、`effort`。PRC-68 转移上增 `effort`。
6. 单出口 decision（只有 otherwise）不写 p；多出口一律写 p，同一判断各出口之和为 1。
7. 生成器 `bpmn_gen.py`（read-only）目前只认步骤级 `outcome`，不读 `decision[].outcome`：用原版生成 14 张图全部检查通过，但有 13 条「结束事件名按位置猜测」警告，且同一步两个不同结局会被合并成一个结束事件（只在走向同一结束节点时发生）；也不画 `withdraw`。已在本目录附最小补丁 `bpmn_gen.suggested.patch`（两处共 8 行），应用后 `bpmn/` 下的图用正确结局名生成，警告清零；补丁未改仓库，请 one-architecture 决定是否并入。撤回的画法靠 decision 里对应的撤回出口体现。
8. `frequency_ref` 统一写成「待取证（E-B1）；经验估计：…」，E-B1 是单据月度件数；个别流程（PRC-60 收款、PRC-63 商票、PRC-86 借款）取证口径不属 E-B1 的单据类型，取证时请 one-ops 补列。

### VS-04·待用户定

沿上一版 VS-04 待用户定且未变的不重复。本批新增或变化：

1. **常规开票是否免人工审批**（PRC-57）：建议免，即协议依据与可开票额度由准入规则守卫，只有金额超阈值或购买方为关联方才转责任人审批（加审规则 AR-01、AR-02、AR-07、AR-11）。理由：AR-04、AR-10 已是 guard: rule，可系统算的条件不让人守；开票经办与提交人分离（不相容职务）保留。备选：仍对所有开票做一级责任人审批（上一版做法）。
2. **人工触账单据遇期间已关账**（PRC-57 开具、58 红冲、60 核销、61 坏账、63 兑付、86 借款）：按 CONVENTIONS 第 5 节改为「只提示、落开放期间并标原期间（INV-92），不拒绝、不审批」，取代上一版待用户定 10 中「人工触账拒绝」。跨年关账的处理仍按 ADR-0045 U-2 未定（APPROVAL_RULES AR-05 的 review），在 U-2 定之前跨年单据的行为待定。
3. **补不补审批**：PRC-58 红冲已过账发票（命中 AR-06，建议加一级财务负责人）、PRC-61 核销坏账（建议责任人单级，金额超阈值加审）、PRC-68 终止协议（建议责任人单级并知会法务）、PRC-49 提交索赔（沿上一版 D9）。理由：这些都是需要人判断的事项且金额或影响大，通行做法有审批；但各需先补依赖边或状态（结构问题 5），所以本批未加。
4. **PRC-141 退回补齐期限**：原 P10D 随「退回补齐」步并入而取消，补齐等待并入第 1 步 P30D。建议保留 P10D，作为退回后第 1 步的重新计时；如要保留须在第 1 步 timeout 写两种期限，格式待定。
5. **PRC-140 责任人就是借阅人本人**（AR-11）【已定：用户答「上级代批」（intent 2026-10-16-164324），本条关闭】：APPROVAL_RULES 的 AR-11 处理方式本身待用户定（上级代批、按异常加审或只事后抽查），建议上级代批。
6. **审批加审阈值**：PRC-47、48、50、53、57 的 escalation_rules 引用 AR-01、AR-02、AR-07（及 AR-11），阈值与级别仍是草拟值，待用户页 T-1 确认；合同评审是否保留「法务专业意见」作为非审批的会签，建议保留，记在审批实例备注而不增节点。
7. **负担参数全部为经验估计**：各步 effort、wait 与判断出口 p 均按经验先估，取证（E-B1 至 E-B8）后校准；出口概率之和已校验为 1。
8. **PRC-52 年度复核**：建议每年复核由财务负责人书面确认并留档，维持则再次开放追加新版本；不再适用时需要「关闭证据类型」命令（结构问题 4）。

### VS-04·规则化

被规则化的审批或人工核对（交 one-rules 出规则卡；已有规则卡者注明）：

| 流程、步骤 | 原来 | 改成 | 拟规则文字 |
|---|---|---|---|
| PRC-57 第 3、4 步 | 财务主管对每份开票申请做一级审批，再由财务做「开票申请准入」 | 系统自动准入；命中条件才转人工审批（第 4 步）；原准入与驳回两步改为系统步骤 | 1) 协议未生效且无预收例外依据，拒绝开票申请（RULE-407、RULE-411 已有，INV-71）。2) 开票金额不得超过付款条件项可开票额度，超过拒绝并告警（INV-21，拟新规则卡）。3) 购买方须为档案已核实的往来单位，否则拒绝（P-3，拟新规则卡）。4) 开票金额超过单据类型阈值，或购买方为关联方，转责任人审批；其余自动准入（分流规则，拟新规则卡，引用 AR-01、AR-02、AR-07） |
| PRC-140 第 1 步（申请时预检） | 审批通过后才在第 2 步发现协议未归档或原件已外借 | 建议在发起借阅审批时由系统预检，不满足不得发起 | 协议未归档，或同一份原件已有未归还的借出，不得发起原件借阅申请；复印件不受限（INV-125 前移到申请时，拟规则卡；第 2 步仍保留 INV-125 作最终守卫） |
| PRC-141 第 2 步（退回） | 档案管理员人工退回补齐 | 份数或签章不全由系统拒绝归档并通知项目经理，不再单设人工退回步 | 原件份数或签章不全拒绝归档（INV-124），通知项目经理补齐 |
| PRC-53 第 1 步（对照，不删审批） | 占用时已有校验 | 保留收入确认审批（财务复核证据实质与签认人身份），但证据类型已开放、证据齐全、签认人登记匹配、额度充足都由规则守卫 | INV-23、INV-22、INV-86、RULE-222 已有；审批理由见 why_human |
| PRC-57、58、60、61、63、86 期间闸门 | 人工触账遇已关账拒绝（结局「因期间已关账……」） | 只提示，落开放期间并标原期间，不审批 | 业务日期所在期间已关账时，人工发起的触账单据提示后落最近开放期间并标原期间（RULE-408、INV-92 已有；跨年待 ADR-0045 U-2） |

# 统一改写 · VS-01（CONVENTIONS 第 5 节；2026-10-16，子代理 VS01 起草，主会话合并）


范围：业务流程 PRC-03、07、08、13、16、69、78；单聚合维护 PRC-01、02、05、06、10、12、14。依据 CONVENTIONS 第 5 节、APPROVAL_RULES.yaml、PROCESS_QUALITY_CHECKLIST.md、BURDEN_ANALYSIS_PLAN.md 第 1 节。只写本次新增或变化的；底稿 NOTES 中未变的条目不重复。

### VS-01·接缝问题

1. **审批结果回调按第 5 节写成 `event in platform.approval`（PRC-07 第 5、6 步，PRC-13 第 2、4 步，PRC-16 第 4、5 步）**：聚合稿 §2.2 中 AGG-05、AGG-12、AGG-17 的引用列只写「platform.approval 审批（api）」，没有「事件」。依赖边 B-02 / B-03 / B-05 → `platform.approval` 已存在，加事件不增边也不改方向，但引用列应补成（api、事件），请 one-architecture 修聚合稿并重跑 `aggregates_dep_check.py`。
2. **事件名**：`审批已通过`、`审批已驳回` 由 `platform.approval` 发布（取自第 5 节），聚合稿 §2.2 的 platform.approval 行尚无这两个事件，请补。
3. PRC-08 第 15、17、19 步（B-09 订阅 B-03 事件）的 `event in` 与第 7、8、9 步的 `event out` 两端分写，沿用底稿做法，未变。
4. 第 16、18、20 步（提交过账候选）`api out B-16` 未变；期间已关账按 INV-92 路由，不再拒绝（见结构问题 5）。

### VS-01·结构问题

1. **命令缺口（聚合稿 §2.2 没有、步骤标 `gap` 或 `withdraw.gap`，只提议不进 `command`）**：
   - AGG-05：编制费率版本、提交费率版本审批、驳回费率版本批准、撤回费率版本审批（PRC-13 第 2 步）；
   - AGG-06：编制计费系数版本；AGG-07：编制协议价目版本（PRC-13，底稿已列）；
   - AGG-12：撤回报价评审（PRC-07 第 3 步 `withdraw`）；
   - AGG-17：驳回风险交付授权（PRC-16 新增第 4 步 `gap`）、撤回风险交付授权申请（PRC-16 第 2 步 `withdraw`）；
   - AGG-02：登记资质延续申请（PRC-78 第 4 步 `gap`，线下办理、进入延续中，底稿已列）。
   - 撤回与驳回是审批平台单据的结果，聚合侧要么补命令，要么约定「审批已驳回、撤回」到达时聚合回到草拟（建议补命令，状态可追溯）。
2. **撤回（item 6）**：新增 `withdraw: {to: end, outcome: 已撤回, ...}` 于 PRC-07 第 3 步、PRC-13 第 2 步、PRC-16 第 2 步（均为提交审批的步骤）与 PRC-08 第 5 步（递交投标 → 第 9 步撤回或撤销投标）；审批步 decision 同步新增「申请人已撤回…」出口并带 `outcome: 已撤回`，场景已补（PRC-07-S07、PRC-13-S07、PRC-16-S07、PRC-08-S19）。生成器不画 `withdraw` 字段，靠出口表达。
3. **PRC-08 截止后撤销**：第 9 步新增出口「撤回发生在截止后，商机继续跟进」直接以 `已撤回` 结束（底稿截止后撤销一律去放弃商机）。依据：撤销投标不等于商机丢失，客户经理可能转其他投标或另行跟进；放弃商机仍保留为 otherwise 出口（p 0.3）。属业务口径微调，见待用户定。
4. **PRC-69 无需资质出口前移**：底稿「该供方类型无需资质」出口在第 2 步（已在登记资质证书之后，实际是死分支，且与 otherwise 同去向），改到第 1 步（申请时判断，直接去核准）。
5. **期间闸门（item 5）**：PRC-08 第 16、18、20 步与 PRC-10 过账候选由「期间已关账 → 拒绝（因期间已关账被拒绝）」改为系统自动按 INV-92 / ADR-0045 落开放期间并标原期间，不拒绝；人工登记（缴纳、扣减、退还、没收）遇已关账期间仅提示、不设审批（规则卡 RULE-408 / AR-05）。已删结局「因期间已关账被拒绝」，场景 PRC-08-S07、PRC-10-S06 改为不拒绝，PRC-10 新增 S07。
6. **非中断超时改为 notify（生成器与新质检的冲突）**：PRC-08 第 15、17、19 步，PRC-16 第 5 步，PRC-78 第 4 步原有 `timeout{interrupting:false}` 仅为提醒。新版 `bpmn_check` R14 对这类图报「会同时收到两个令牌」（生成器把升级任务接回主线后继，非中断时主线与升级各带一个令牌），全部属生成器画法问题，不是业务错误。本批把仅提醒的超时改为步骤 `notify`（写明时限与「再次提醒」，P-10、P-02、授权期限届满），保留的 `timeout` 为零。请 one-architecture 改生成器：非中断超时的升级任务做成独立分支并在其后结束，改后可把这几处还原为 `timeout`。
7. **PRC-08 元素 52 个（> 50，R15 警告）**：投标（第 1–14 步）与三种退还保证金反应（第 15–20 步，B-09 的编舞）合在一张图。建议拆出子流程「退还投标保证金」（事件触发：投标未中标、投标已废标、投标已撤销，B-09 持有），需向 one-architecture 要流程号。本批未拆（不改流程划分）。
8. **PRC-13 缺命令与批准人**：第 2 步仍无 `command`（`gap`）；批准走审批平台，基础审批人解析为经营管理职能负责人（费率版本无费用归属对象，见待用户定 1）。
9. **PRC-78 延续审批（D8，engine: aggregate）**：保持 B-01 内单级命令，按专业复核写法：`approver: SH-M1`、`why_human` 写经营决策内容、不写 `escalation_rules`；仍无 B-01 → `platform.approval` 边。

### VS-01·新用语

- **事件名（领域，`platform.approval` 发布，订阅方 B-02 / B-03 / B-05）**：审批已通过、审批已驳回。
- **命令候选名（只提议，未写进 yaml 的 `command`）**：驳回费率版本批准、撤回费率版本审批（AGG-05）；撤回报价评审（AGG-12）；驳回风险交付授权、撤回风险交付授权申请（AGG-17）。其余候选名见结构问题 1 与底稿。
- **结局词**：已撤回（PRC-07、08、13、16 共用，建议登记为通用流程结局词）。已删：因期间已关账被拒绝（PRC-08）。其余结局词沿用底稿。
- **单据类型暂名**：沿用底稿（报价评审单、费率版本批准单、风险交付授权审批单、资质延续审批单）。
- 「认定视为同意成立」：本批流程不含相关命令，无需改动。

### VS-01·格式问题

1. **生成器不读 `decision[].outcome`（也不画 `withdraw`）**：它只用步骤级 `outcome`，或在 `when` 文字里找结局词，找不到才「按位置猜测」。本批做法：同一步有两个以上不同结局的结束出口时，不写步骤级 `outcome`，每个结束出口的 `when` 含结局词并带 `decision[].outcome`，`otherwise` 出口不直接结束（接下一步或唯一结束出口）；只有一个结束出口时两者都写。已核对所有结束事件名与意图一致。建议生成器直接读 `decision[].outcome` 与 `withdraw`。
2. 生成器把 `read: true` 步也计入「缺 command」警告（不影响检查通过）。
3. 平台定时提醒步写法（PRC-08 第 4 步）：`actor: platform`、`where: automatic`、`platform_task: true`、`on_time: <提前量>`，不再用 `timeout`。
4. 单聚合维护：`roles`（含 `org_needs`，`as: performer`）、`frequency_ref`、`commands[].effort`、`posting[].on_closed_period`（取代 `reject`）为本批新增写法；`approval.escalation_rules` 仅 `engine: platform.approval` 写，`engine: aggregate` 不写。
5. `withdraw.gap`：撤回命令缺口的说明字段，随命令补齐后删除。
6. `segregation` 由字符串改为列表 `{roles, rule}`（PRC-07、13、16、69、78）。

### VS-01·待用户定

均已按建议填写，用户确认前不定稿。（只列本批新增或变化的）

1. **基础审批人的归属对象（PRC-07、13、16）**：已统一为 `cost_object_owner`。报价评审归属对象建议取商机承担部门，即承担部门负责人批；商机负责人往往就是编制人，命中 AR-11（责任人即提交人）的概率高，建议评审责任落承担部门负责人而不是商机负责人。费率版本无费用归属，建议取经营管理职能负责人。风险交付授权取商机或项目的责任人。（取代底稿 D2、D5、D6 的 SH-M1 写法）
2. **加审规则适用（PRC-07、16）**：写入 AR-01、AR-02（金额阈值，阈值待用户定）、AR-07（关联方）、AR-11；PRC-13 不写（费率版本非金额单据）；PRC-78 aggregate 引擎不写。
3. **撤回（item 6）**：建议审批中允许申请人撤回，并补聚合命令（结构问题 1）；也可只在审批平台留痕、聚合不变。
4. **PRC-08 截止后撤销**：商机是否必然放弃？建议不必然，由客户经理判断（结构问题 3）。
5. **规则化候选**：见下节，两条拟规则请用户确认参数。
6. **P-10 / P-02 / 授权期限届满的提醒**：由 `timeout` 改为 `notify`（结构问题 6），业务提醒口径不变，待生成器修复后可还原。
7. **效率参数为经验估计**：`effort`、`wait`、`p`、`frequency_ref` 均按经验先估，取证（E-B1–E-B8）后校准。
8. 早前待用户定中 D1–D4、D7、D9–D20 不变；D8（PRC-78）按本批 engine: aggregate 写法保持。

### VS-01·规则化

用户原则「能通过规则和不变量守卫的就不要人来守」。逐个审批节点结论：

| 流程 | 步骤 | 判断 | 结论 |
|---|---|---|---|
| PRC-07 | 4 报价评审 | 价格策略、毛利、范围与风险承担需商业判断 | 保留人工，加审引 AR-01、02、07、11 |
| PRC-13 | 3 费率版本批准 | 全公司报价与预算口径的水平判断 | 保留人工，不写加审 |
| PRC-16 | 3 授权审批 | 签约可能性、客户信用与先行投入风险 | 保留人工，加审引 AR-01、02、07、11 |
| PRC-78 | 5 延续资质（aggregate） | 是否仍具备延续条件并续办，经营决策 | 保留人工，专业复核写法 |

本批没有审批整体改为规则；以下是从审批前后拆出的、系统可算的条件，改成拒绝、预警或自动分流（交 one-rules 出规则卡）：

1. **PRC-08 期间闸门**：保证金退还提交过账候选遇已关账期间，由拒绝改自动落开放期间并标原期间（INV-92、ADR-0045；AR-05 的 RULE-408 已覆盖，本批只改流程）。
2. **拟规则 A（PRC-07 提交评审前预警）**：提交报价评审时系统计算毛利率与授权额度，低于毛利下限标红并要求填写理由；不阻断。参数（毛利下限、授权额度）待用户定。
3. **拟规则 B（PRC-16 申请时拒绝）**：风险交付授权的额度不得超过商机预计合同额的某比例、期限不得超过某月数，超限拒绝而非转加审。参数待用户定；与 INV-18（同一对象同类型同时段唯一）并列。

## 新用语 · 汇总交 one-glossary（聚合稿命令名与审批单据类型名，closeout_check --qc 第 6 项，2026-10-16）

以下 125 个任务名取自聚合稿 §2.2 命令列原文，`terms.yaml` 无词条、各批 NOTES 也未逐条登记；请 one-glossary 按 command 类登记（bc 取所在聚合的 BC），定英文名：

不采信外部检测报告、作废报价版本、停用往来单位角色、关联检查报告、关闭线索、关闭设计变更、内审设计变更、冲抵员工借款、创建应收、占用框架限额、发出满意度调查、发出设计变更、发布检查表模板版本、发布项目复盘、发起红字信息确认、发起资质核查、取消项目、变更商机负责人、回冲开票额度、在应收上核销占用、复查检查、安排检查、完成协议、审核检查记录、封存证据、应用变更、延续资质、建立项目、建议外部检测、开具销项发票并改写 WIP、归档项目、恢复线索、恢复项目、拒绝或撤回索赔、挂起线索、指派检查人员、提交延期详细说明、提交报价评审、提交检查记录、提出协议变更、提出索赔、提出设计变更、提出资源需求、撤回或撤销投标、收款认领、放弃商机、放弃投标、新建商机、暂停往来单位角色、暂停项目、更新开票与收入额度、标记商票已逾期、标记费用报销已付、标记资质已到期、核准往来单位角色合格、核销坏账、检测应收逾期、生效协议价目版本、生效协议变更、生效报价版本、生效检查计划、生效计费系数版本、申请往来单位角色、登记业主委托外部检测、登记中标、登记争议与处置分类、登记内部评审结论、登记商机丢单、登记商票兑付、登记外部检测报告、登记客户认可、登记客户退回、登记废标、登记延期批准、登记延期拒绝、登记延期视为批准、登记我方参与记录、登记提交轮次送达、登记未中标、登记检查计划、登记满意度跟进、登记线索、登记视为同意参数与发包人代表、登记调查回收、登记资料已提供、登记资格预审结果、登记资质证书、登记风险与问题、确认发包人资料、确认应付、确认最终价款、确认线索、确认资源分配、红冲发票、终止项目、结算协议、维护商机参与团队、维护项目团队成员、编制报价版本、编制项目复盘、编制项目预算版本、计算期满日并生成待办、认定变更归因与影响、认定工时有效性、认定索赔、认定返工、认领线索、记分配行与流水行、评估变更影响、评分满意度调查、评审项目复盘、起草协议、退回发包人资料、退还保证金、递交投标、通知延期、通过报价评审、采信外部检测报告、采集证据、释放占用、恢复应收、释放框架限额、锁定检查、锁定项目预算、驳回报价评审

# 评审改稿 · FIX123（REVIEW.md M8、M10、M15、低 1/4/6；PRC-151、153 衔接、154；2026-10-16）


生成检查：`python3 -I bpmn_gen.py processes.yaml` 全部「检查：通过」，R15 无警告（PRC-08 由 52 个元素降为 38 个）。残余警告均为既有（只读或平台步骤缺 command 用缺省、PRC-28 超时升级同泳道）。PRC-151 不在 process-catalog.yaml，只读副本中临时补一行验证通过，正式合并时需 one-architecture 在目录加 PRC-151、PRC-153、PRC-154 三行。

### FIX123·改动清单

- **M15 / R15　PRC-08**：
  - 原第 15–20 步（三条退还分支及各自过账提交）整体移出，改由 PRC-153 退还投标保证金（B-09，S-27 给号；由另一批写）订阅 PRC-08 发出的「投标未中标」「投标已废标」「投标已撤销」事件接续。这是信号事件衔接，不用调用活动；这三个 `event out B-09` 在第 7、8、9 步（现序号 9、10、11）原本就有。
  - 废标、撤回出口在 PRC-08 内收口：废标后重新招标、丢单或放弃；截止前撤回且继续跟进以「已撤回」结束，不再跟进则放弃商机；未中标的商机丢单策略直接以「商机已丢单」结束。
  - 流程结局去掉「保证金已退」（归 PRC-153），场景 S06–S14 按新路径重写，场景总数仍为 19 条。
  - 新增第 4 步「发起付款」（B-10，AGG-53，approval：engine platform.approval，approver cost_object_owner，escalation AR-01、AR-02，why_human 写 go/no-go 与资金占用的商业判断，驳回去向为放弃投标）和第 5 步「登记保证金缴纳」（出纳，AGG-51，订阅 B-10「付款已成功」）。招标文件不要求保证金的从第 3 步直接跳到定时提醒。加 segregation：申请人与出纳不同人（INV-81）。加 SH-M1 为 approver 角色。
  - 流程名由「…并处理保证金」改为「参加投标，缴纳保证金并登记结果」（process-catalog 的名称需随改）。
- **低 1　PRC-78**：segregation 改为 `[{roles: [SH-E6, SH-M1], rule: 核查结果登记人与延续批准人分离}]`。
- **低 4　PRC-28**：
  - 第 4 步（签发）：证书不符时可「改派持证签发人」回第 4 步（p 0.02），无人可派才以「未签发」结束。
  - 第 6 步（登记客户认可）：新增 INV-48 被拒出口，结局「认可登记被拒」；不符合项按 PRC-34 处置，关闭后再登记认可。
  - 结局词加「认可登记被拒」；场景加 S10（改派签发人）、S11（认可登记被拒）。
- **低 6　PRC-20、PRC-30**：engine 为 aggregate 的审批，approver 由 cost_object_owner 改类型级 SH-M1（依 CONVENTIONS 第 5 节第 2 条第二层，专业复核/职能确认不写 escalation_rules）。PRC-30 原 AR-11 删去，其「提出人与批准人不得同一人」由流程级 segregation 与聚合内校验承担；PRC-20 原 escalation_rules 本为空。
- **M8　PRC-43**：第 4 步 desc 与 notes 写明待用户定，步骤标 `pending: M8`；流程图结构未变。
- **M10**：data-model 全部 158 张表补 `pi`：none 127、pi 29、spi 2（counterparties 证件号、counterparty_bank_accounts 银行账号）。pi 的表在 reason 末尾追加「pi=…：依据」。员工借款类 receivables 为 pi。
- **低 2**：只查其余流程，没有发现完全重复的场景（PRC-53、54 按分工不动）。唯一相近的 PRC-86-S04/S05 的 expect 文字相同但路径、覆盖步骤和结局都不同（发放期间闸门与结清期间闸门），不是重复，保留不动。
- **M7（协调会话指示）新增**：
  - PRC-151 填报经营产值（业务流程，B-04，每期时间触发）：项目经理填报，抽中则部门负责人抽查，不设审批、不过账、不占额度。场景 4 条。
  - PRC-154 收入三口径对照（读模型，reporting）：按协议 × 期间并排经营产值、权责收入、开票收入，算差额与时滞；写 query、dimensions、permission、page、scope；场景 3 条。

### FIX123·接缝问题

1. PRC-08 第 4 步 `api out platform.approval` 挂在 bc: B-10 的步骤上（B-10 → platform.approval 的 api 边已有）；PRC-08 属 B-03，本流程自身不依赖 B-10，第 4 步是 B-10 页面上的人工动作，沿用本组「下游 BC 反应步带 bc」写法。第 5 步订阅 B-10「付款已成功」，B-09 ← B-10 事件边已有。审批通过/驳回的回调发生在 B-10 付款流程内，未在 PRC-08 另写 event in platform.approval（B-03 无该边的事件方式，同评审 M11）。
2. PRC-154 读 AGG-23 子表 progress_estimates 按 api（reporting → B-04 的读取边请 one-architecture 按 §4.1 核对），读 AGG-44、AGG-46 按订阅事件；B-08、B-09 的事件在 reporting 内都是既有订阅方式。

### FIX123·结构问题

1. PRC-08 以信号事件接 PRC-153，PRC-153 由另一批写；三个事件（投标未中标、投标已废标、投标已撤销）名与聚合稿 AGG-11 保持一致。请 PRC-153 作者核对：未中标、废标按合同签订后 5 日、截止前撤回按收到撤回通知后 5 日的提醒时限，以及「保证金已退」结局由 PRC-153 承担，PRC-08 不再有该结局。
2. 保证金缴纳走 B-10 付款后，PRC-10（保证金台账）的「登记保证金缴纳」与本流程第 5 步是同一命令，PRC-10 单聚合维护条目请指向 PRC-08 第 5 步为入口。

### FIX123·新用语

- 命令「填报产值估计」（AGG-23，写 progress_estimates；PRC-151 第 1 步，标 gap）。
- 事件名未新增。

### FIX123·格式问题

1. process-catalog.yaml 需新增 PRC-151（type: business）、PRC-153、PRC-154（type: read_model），并更新 PRC-08 名称。
2. 读模型 PRC-154 的 `query.measures`、`query.derived`、`query.rule` 三个键为读模型 query 下的自由键，请确认可用；PRC-151 步骤的 `read: true` 抽查步沿用 PRC-28 样板写法。
3. PRC-28 步 4 的 `goto` 指向自身（改派后重入）。
4. pi 判定口径：业务内容里出现的人员（评审人、签发人、负责人、认领人、联系人、签收人）判 pi；通用审计列的创建人/操作人标识不单列，由框架审计列统一登记（ADR-0043 第二维的列级登记另行处理）。

### FIX123·待用户定

1. **PRC-08 go/no-go（评审 M15）**〔已定：用户 VS01-10，独立流程 PRC-183「作出参投决策」，商机负责人决定、异常加一级；PRC-08 去掉参投判断〕：保证金付款审批同时承担是否参投的判断。AEC 通行做法：部门负责人（商机负责人的上级）审批投标立项，保证金金额超额按 AR-01、AR-02 加审。建议如此，不另设独立 go/no-go 流程；如要独立立项评审，则在第 3 步前加一步审批。
2. **PRC-43 检查现场客户签认如何到收入侧（评审 M8）**，二选一：
   - ① 现场签认单作为一份交付物（建议）：经 PRC-28 第 6 步「登记客户认可」在 B-28 登记认可，B-08 据「交付物已认可」按 ADR-0047 第 5 类确认收入，不增加新路径。
   - ② 第 5 类 (a)「检查完成经客户现场签认」不单独触发，收入证据以报告签发后送达（第 5 类 (b)）为准。
3. **PRC-151 抽查比例与逾期**：默认抽查 20%、期末后 3 日未填报则提醒部门负责人，均为经验估计，请业务确认或给取证值。
4. **PRC-28 改派签发人**：改派须持有效证书且专业、等级相符；改派是否需要留痕审批，建议不需要（INV-47 已守卫）。

# 评审改稿 · FIX04（REVIEW.md H1–H7、M1–M6、M9、M11、M12、M14、低 2/5/8；新增 PRC-149、150、152、153；2026-10-16）


范围：修改 PRC-47、48、49、50、54、57、58、60、61、63、66、86、140、141，事件策略 PRC-51、53、56、59、146、147、148，维护 PRC-10、52、68，读模型 PRC-62；新增 PRC-149（冲销收入确认）、PRC-150（发票红冲后冲减应收）、PRC-152（登记付款条件达成）、PRC-153（退还投标保证金）。PRC-151、154 由另一批写。验收场景 241 条（29 个单元）。bpmn_gen 17 个业务流程全部「检查：通过」（含新增 3 个；检查时在副本目录给 process-catalog 临时加了 PRC-149、152、153 三条，类型 business；事件策略 PRC-150 不画图）。closeout_check --qc：why_human 缺 0、加审引用无效 0、用户任务缺 permission/page 0、出口 / 超时 / 结局无场景 0。

### FIX04·处理对照

| 编号 | 状态 | 落点 |
|---|---|---|
| H1 | 已改 | PRC-54 重写：订阅「无异议已声明」与「交付物已送达」，系统自动认定；期满定时与退回二选一；期满无声明催办（P3D 升级 SH-M1、阶段门预警）；新送达作废旧待办；场景 9 条 |
| H2 | 已改（新号 PRC-149） | 冲销收入确认：占用反向额度 → 审批（AR-06）→ 追加冲销行 → 过账；事件触发（协议已结算差额为负、协议已终止）与人工发起；差额为正转 PRC-53 补充确认 |
| H3 | 已改 | PRC-66 改人工发起、累计确认只作提示；最终价款低于协议价走 platform.approval（AR-01、AR-02）；未能确认转索赔（PRC-49 增 call 触发）、转争议处理或修订再谈 |
| H4 | 已改 | PRC-48 插入「登记发包人书面同意」external 步（approver SH-C6a）与「变更未获发包人同意」出口；现场签证拒签出口「签证被拒签」；内部审批改为「是否向发包人提出」 |
| H5 | 已改（新号 PRC-152） | 人工登记 + 时间型定时 + 质保金到期返还；PRC-56 只处理成果认可型；视为同意路径由 PRC-54 第 8 步经 B-08 → B-05 api 标记 |
| H6 | 已改 | PRC-58 红冲审批（AR-06）、PRC-61 坏账核销审批、PRC-10 扣减与没收审批、PRC-86 员工借款审批、PRC-68 终止审批；均 engine platform.approval、approver cost_object_owner、写 why_human 与 segregation；借款有未结不得再借与单笔限额、保证金退还只付往原缴纳方账户写成 guard rule；保证金退还经 B-10 付款 |
| H7 | 已改（新号 PRC-150） | 订阅「发票已红冲」冲减应收，已收款部分转预收或待退并通知财务 |
| H8 | 部分 | PRC-08 拆分由另一批做（本批提供 PRC-153）；QC 重写与 A8 比对不在本批 |
| M1 | 已改 | PRC-53 approver 写类型级 SH-E6（ADR-0047 U-2 (a) 财务复核，已获 one-architecture 同意）；加审 AR-02、AR-07；超时升级给 SH-M1（财务负责人的上级） |
| M2 | 已改 | 占用与审批实例不迁；由迁入编排模块的一次性离线命令对在途项重新占用并发起审批：PRC-47、48、50、53、57、58、61、66、86、148、10、68；PRC-147 迁入后一次性对账去重；PRC-54、141、146、140、51、152、153 的待办、事件由迁入编排离线命令生成或补发 |
| M3 | 已改 | 触账步骤加「原期间所在年度已年结 → 暂挂并告警（待 ADR-0045 U-2）」出口与结局「暂挂待跨年处理」：PRC-57、58、60、61、63、86、147、148、10 的过账说明，新增流程同 |
| M4 | 已改 | PRC-147 冲抵限同一法人，不同法人冲抵额为零并告警（拟规则 FIN-R1） |
| M5 | 已改 | PRC-60 补第三方代付（委托付款函，AR-15 加审）、超收转预收或待退、待认领超期跟进（P30D）、收到商票出口（已收票待兑付，到期见 PRC-63） |
| M6 | 已改 | PRC-57 第 6 步分可重试（自动重试并告警）、业务拒绝（退回申请人修改）、主动作废三出口；平台故障不作废 |
| M9 | 已改 | PRC-53 第 1 步写占用金额取值，拟规则 REV-R1（见规则化） |
| M11 | 已改 | PRC-61 催收改 notify，去掉 api B-09 → platform.integration；审批回调边 B-09 → platform.approval 已获同意 |
| M12 | 已改 | PRC-86 发放、归还、手工冲抵、到期催还四种触发在第 1 步分流；第 3 步向 B-10 api「发起借款付款」；补到期催还 |
| M14 | 已改 | PRC-148 二次校验失败转第 4 步，财务负责人人工「释放占用」或「补证据后重新发起审批」，P5D 期限与场景 |
| 低 2 | 已改 | 重写的场景全部重新编号去重，去掉生成器产物「期限届满」重复场景（PRC-53、54 及本批各条） |
| 低 5 | 已改 | PRC-63 超时升级改给 SH-M1，补贴现与背书转让出口（会计处理见待用户定） |
| 低 8 | 已改 | PRC-148 triggers 与 cross_bc 统一为「审批已通过 / 审批已驳回」，过账说明统一「B-08 → B-16 api，实现为事件发布登记表异步投递」 |
| 低 1、3、4、6、7 | 不在本批 | 低 7 的数据模型部分见 data-model.patch.md |

### FIX04·接缝问题

1. 审批回调 event in platform.approval 方向问题沿旧稿（仍待 §4.1 注明例外）；本批新增的审批步骤同样写法。
2. 「投标保证金待退还」信号事件由 PRC-08（B-03）发出、PRC-153（B-09）订阅：B-03 → B-09 的事件方向须在 §4.1 核对；PRC-153 读投标结果为 B-09 → B-03 api（AGG-51 依赖 AGG-11，边已有）。
3. PRC-54 第 8 步 B-08 → B-05 api「标记付款条件项可结算」、PRC-149 读 B-05（协议状态与最终价款）均为下游调上游，边已有。
4. PRC-149 订阅 B-05 的「协议已结算」「协议已终止」，PRC-150 订阅 B-08 的「发票已红冲」，均与既有边方向一致。
5. PRC-60 第 7 步、PRC-86 第 3 步 B-09 → B-10 api（发起退款付款、借款付款）沿 Q-10 的边；PRC-10 退还与缴纳经 B-10 付款同此。
6. PRC-86 第 1 步按触发类型分流，仍是同一流程画四个开始事件，不是四个实例的拆分；若要拆成独立流程，需新号（待 one-architecture 定）。

### FIX04·结构问题

1. PRC-66 的 owner_bc 仍为 B-08（命令在 B-05，编排下游调上游）；流程名已由「判定履约完成，确认最终价款并结算协议」改为「发起结算，确认最终价款并结算协议」，PRC-86 改名「发放、归还与催还员工借款」，请 one-architecture 同步 process-catalog。
2. AGG-13 终止：PRC-68 终止经审批平台，是否在 AGG-13 状态加「终止审批中」（F-2）未裁；本稿按「审批通过后执行终止协议」，不加状态。
3. 单聚合维护的审批：PRC-10、52、68 的 transition 上新增 `approval` 字段（形同步骤审批）；见格式问题 1。
4. PRC-66 结算后差额为正的补充确认：结算书签认作客户确认证据，需 B-28 登记认可（复用 PRC-28 第 6 步）才能触发 PRC-53，PRC-149 第 1 步只路由到 PRC-53，不自行触发；见待用户定 6。

### FIX04·新用语

**命令缺口**（命令只取聚合稿 §2.2；以下不在对应聚合命令列，步骤已写 `gap`）：

| 流程步骤 | 聚合 | 命令（事件） |
|---|---|---|
| PRC-48 第 5 步 | AGG-15 | 登记发包人书面同意 |
| PRC-54 第 7 步 | AGG-44 | 认定视为同意成立（沿旧稿） |
| PRC-53 第 1 步；PRC-148 第 1、3、4 步 | AGG-44 | 占用收入额度；追加收入确认事实并占用转实；释放收入额度占用；重新发起收入确认审批（随 Q-04，沿旧稿；第 4 步 M14 新增） |
| PRC-149 第 2、3、5 步 | AGG-44 | 冲销收入确认；占用冲销额度；追加冲销行并占用转实（事件：收入确认已冲销，仅分析） |
| PRC-150 第 1、2 步 | AGG-49 | 冲减应收；转预收或待退 |
| PRC-152 第 2 步 | AGG-13 | 登记付款条件达成（标记付款条件项可结算沿用 PRC-56 的命令） |
| PRC-60 第 7 步 | AGG-50 | 处置待认领流水 |
| PRC-86 第 4 步 | AGG-49 | （无）订阅 B-10「借款付款已成功」后无记录命令（沿旧稿） |
| PRC-140 第 4 步 | AGG-13 | 标记协议借阅逾期（沿旧稿） |

**事件**：投标保证金待退还（PRC-08 发出，B-03 → PRC-153）、收入确认已冲销（仅分析）、保证金退还付款已成功 / 保证金缴纳付款已成功（B-10 → B-09，PRC-10 退还与缴纳的付款结果，PRC-10 desc 已用「经 B-10 付款」，事件名待定）。

**平台任务与审批单据类型**：红冲审批 / 红冲申请审批单、坏账核销审批 / 坏账核销审批单、最终价款让价审批 / 最终价款让价审批单、员工借款审批 / 员工借款审批单、第三方代付认领审批 / 第三方代付认领审批单、收入冲销审批 / 收入冲销审批单、保证金扣减审批单、保证金没收审批单、协议终止审批单、发包人书面同意（external）、证据类型开放书面确认（external）。

**结局词**：变更未获发包人同意、签证被拒签、暂挂待跨年处理、红冲被驳回、认领被拒、已收票待兑付、待退回、坏账核销被驳回、商票已贴现或背书转让、转索赔、转争议处理、借款被拒、已催还、收入已冲销、冲销已驳回、无需冲销、转补充确认、冲销被拒、应收已冲减、已转预收或待退、应收未找到、条件已达成并可结算、登记被拒、条件已是可结算、已重新发起审批、退还被拒、转没收登记。

**概念**：预收与待退（分配行类型）、条件类型（付款条件项：成果认可、预付、审图通过、竣工验收、按期、结算审定、质保期满）、冲销原因、反向额度占用。权限码与页面：写在步骤 permission、page 字段，新增的有「收入冲销.提交 / 审批」「付款条件.登记达成」「应收.坏账核销审批」「员工借款.审批」「销项发票.红冲审批」「收款.第三方代付审批」「收款.处置待认领」「协议结算.发起 / 让价审批」「协议变更.登记发包人同意」「收入确认.处置占用」「保证金.登记退还」。

### FIX04·格式问题

1. 单聚合维护的 transition 上增 `approval`（字段同步骤审批：engine、doc_type、approver、owner_object、why_human、escalation_rules、on_reject）与流程级 `segregation`、`roles`、`cross_bc`；CONVENTIONS 只定了步骤上的 approval，请裁定是否入约定（closeout_check --qc 不扫 transition，故这些审批未被脚本核对）。
2. PRC-53 approver 写 `SH-E6`（类型级，已获 one-architecture 同意）：closeout_check --qc 第 2 项仍报 1 处（权责对照表无「收入确认审批」对象×命令），请在权责对照表登 `resolve: type`、A 为 SH-E6；`owner_object` 写「承担法人财务负责人（类型级复核，ADR-0047 U-2 (a)）」。
3. 触发类型 call 用于 PRC-49 被 PRC-66 调用；goto 到另一流程（`goto: PRC-49`、`goto: PRC-53`）带 `outcome`，生成器画「已转 PRC-xx」结束事件。
4. 事件网关二选一（期满定时 / 客户退回）在生成器里只能画成「定时等待 + 判断」，PRC-54 第 3 步用 `on_time` 加 cross_bc in AcceptanceReturned 表示，图上不是真正的事件网关。
5. 同一流程多个开始事件进入同一第 1 步时（PRC-86、PRC-149、PRC-152），第 1 步用 decision 按触发分流。
6. PRC-60、86、152、153 的外部银行步骤 `where: external:银行`，PRC-153 第 2 步为等待到账的只读步骤。

### FIX04·待用户定

沿旧稿不变的不重复。新增或变化（均为建议）：

1. **PRC-54 视为同意使付款条件项可结算**（旧待用户定 3，现已按建议落在第 8 步）：建议可。理由：视为同意等同认可，否则只能确认收入、不能开票。
2. **催办期**：PRC-54 期满无声明的催办期建议 P3D，之后本轮不成立（再声明会重新触发）。理由：不让待办无限挂着。
3. **红冲审批阈值**（PRC-58）：命中 AR-06 必加财务负责人一级，金额超第二阈值再加总经理。理由：红冲冲销已过账事实。APPROVAL_RULES 建议补两条：「坏账核销」（异常加一级，金额超阈值加二级）与「员工借款」（单笔限额与未结余额为规则，超限额拒绝而非加审）。
4. **借款规则**（PRC-86）：有未结借款不得再借、单笔限额（建议值待 one-ops 取数）；逾期按催收与争议处置，不从工资代扣（Q24 已定为固定规则，审题 VS04-22 撤题）。
5. **保证金方向与付款**（PRC-10）：我方缴纳经 B-10 付款审批（审批人商机负责人或部门负责人），投标 go/no-go 是否加审批（M15）：通行做法为部门负责人审批、超额加审，建议在 PRC-08 立项时设，本批未加。
6. **结算差额为正时的补充确认**（PRC-66 → PRC-149 → PRC-53）：建议把双方签认的结算书作为一份交付物经 B-28 登记认可，复用 PRC-28 第 6 步，再由 PRC-53 占用；备选：不补确认，差额为正只在下一次确认时体现。
7. **PRC-60 第三方代付**：建议委托付款函为必备证据，AR-15 加审（财务负责人、总经理）；待认领流水挂账超 P30D 升级，建议转其他应付或退回付款方。
8. **商票贴现与背书转让**（PRC-63）：建议登记贴现、背书转让为终局，会计处理另议（命令缺口）。
9. **联合体代收代付、预收款合同中途终止的退款**（M13）：本批只补质保金返还（并入 PRC-152）；联合体我方牵头代收后向成员转付、联合体开票口径，建议新开流程，待用户定是否需要；预收款中途终止退款建议走 PRC-68 终止 → PRC-149 冲销 → 经 B-10 退款，退款流程待定。
10. **检查服务现场签认**（M8，不在本批）仍待定。
11. **PRC-152 无审批**：条件达成登记由外部证据与类型对照规则守卫，不设审批；若用户要求对大额预付、结算审定加审，建议引用 AR-01、AR-02。
12. **PRC-10 保证金没收、扣减的审批人**：建议保证金所属协议或商机的责任人单级，金额超阈值加审（AR-01、AR-02）。

### FIX04·规则化

| 流程、步骤 | 原来 | 改成 | 拟规则文字 |
|---|---|---|---|
| PRC-53 第 1 步（M9，REV-R1） | 占用金额怎么来未写 | 规则派生 | 占用金额 = 履约义务分摊价格 × 交付物对应的阶段比例；交付物与付款条件项或履约义务的映射取协议登记；部分认可按认可占比单列；一份成果对应多个履约义务按分摊比例拆行分别占用 |
| PRC-149（REV-R2） | 无冲销路径 | 不变量守卫 | 冲销额不得超过原确认事实的未冲销余额（含在途冲销）；超过拒绝并告警 |
| PRC-147 第 1 步（M4，FIN-R1） | 借款不限法人 | 规则 | 冲抵员工借款限同一法人（借款所属法人 = 报销承担法人），不同则冲抵额为零并告警 |
| PRC-86 第 1 步 | 无借款规则 | 规则拒绝 | 借款人有未结借款，或申请额超过单笔限额，拒绝借款申请（限额值待取证） |
| PRC-152 第 2 步（PAY-R1） | 无 | 规则校验 | 登记付款条件达成须附与条件类型对应的外部证据：预付 ← 协议已生效（回签）即达成、约定预付款保函的附保函，施工图审查通过 ← 审查合格书，竣工验收 ← 验收证书，结算审定 ← 审定书，质保期满 ← 缺陷责任期终止证书；证据缺失或类型不符拒绝 |
| PRC-60 第 2 步 | 无 | 规则拒绝 | 付款方不是合同相对方且无委托付款函，拒绝认领（RULE-410）；有函的转 AR-15 审批 |
| PRC-10 退还 | 出纳登记 | 规则 | 保证金退还只能付往原缴纳方登记账户（AR-09，RULE-410）；退还与没收互斥各一次（INV-20） |
| 各触账步骤（M3） | 无年结处理 | 规则暂挂 | 业务日期所在期间已关账路由开放期间并标原期间（INV-92）；原期间所在年度已年结的暂挂并告警，待 ADR-0045 U-2 |

## 新用语 · 评审改稿补登（2026-10-16）

交 one-glossary：发起付款（命令，PRC-86、153 调 B-10，payables.payment）、投标保证金付款审批（审批单据类型，B-10）、登记保证金缴纳（命令，receivables.collection，AGG-51 已有「登记保证金缴纳」则为同名确认）。

# 聚合收敛 · VS-03/04 的 19 个聚合（commands.yaml、state-machines.yaml；2026-10-16，子代理起草，主会话合并）

主会话按语：converge_check 报「AGG-38 关联证据 / 解除证据关联未入目录」是脚本只取单维护单元级 aggregate、不读条目级 aggregate 所致；骨架 PRC-33 已逐条标 AGG-110，目录按聚合稿放 AGG-110，不改。已报 one-architecture。


范围：AGG-34–37、28、38、110、13–17、44–46、92、49–51（19 个聚合，124 条命令，其中 26 条拟命令 `proposed: true`；19 台状态机，212 条转换）。脚本结果见 `check.txt`：本批无孤儿命令（20 条靠 `unused_ok`，其中 16 条是骨架未写 command 的拟命令）、状态全可达、非终态均能到终态、骨架 199 个改状态步骤涉及的本批 (聚合, 命令) 全部对上转换；唯一报错是骨架把两条命令挂错聚合（接缝 1）。

### 收敛·接缝

1. 骨架 PRC-33（单聚合维护，AGG-38）的 commands 列了「关联证据」「解除证据关联」，聚合稿属 AGG-110（intent 114200 F-1），PRC-43、44、45 的步骤也正确写 AGG-110。converge_check 因此报 `AGG-38 关联证据`、`AGG-38 解除证据关联` 未入目录。目录按聚合稿放 AGG-110，AGG-110「解除证据关联」靠 `unused_ok`。建议改骨架 PRC-33：这两条命令的 aggregate 改 AGG-110，AGG-38 的 `states.link` 子表挪出。
2. 单聚合维护（PRC-52、PRC-68、PRC-10、PRC-33 等）只写 `states`/`transitions`，commands 里没有 `aggregate` 字段或步骤没有 aggregate，脚本按「无人用」处理，本批用 `unused_ok` 兜住：AGG-13 终止协议（PRC-68）、AGG-92 开放证据类型（PRC-52）。建议骨架约定：单聚合维护单元的转换自动计入「使用」，或 commands 补 aggregate。
3. 骨架 PRC-10（AGG-51）单聚合维护的状态机（待缴、已缴、已退、被没收、已开立、到期、被索赔）与 PRC-153、PRC-08 步骤一致，本批 AGG-51 状态机沿用并补「登记保证金应缴」。PRC-33 的 AGG-38 状态沿用，其余本批聚合由聚合稿与骨架步骤合成，未与单聚合维护单元冲突。
4. 权责表只有 AGG-44「占用收入额度」一条（类型级，A=SH-E6）。其余 123 条命令 authority 写「待定」，不是缺漏而是表未登；骨架上带审批的步骤（如 AGG-28 关闭不符合项、AGG-35 审核检查记录、AGG-45 开票申请审批）其 approver 可作权责表登记依据，未在本批代填。
5. AGG-28、AGG-35 的审批仍按 `approval.engine: aggregate`（B-26、B-28 无到 platform.approval 的边，沿 VS-03 结构问题 1），状态机因此把「复核不符合项」「审核检查记录」「闭合」「退回整改」写成聚合内命令，没有审批中状态。

### 收敛·结构

1. 聚合稿缺而骨架已用的命令（26 条拟命令里骨架已用 10 条）：AGG-13 登记付款条件达成；AGG-15 登记发包人书面同意；AGG-44 占用收入额度、释放收入额度占用（Q-04）、冲销收入确认、占用冲销额度、追加冲销行并占用转实；AGG-49 冲减应收、转预收或待退；AGG-50 处置待认领流水。其余 16 条骨架未写 command、仅在 NOTES 或 gap 提到：AGG-35 开始现场检查、取消检查；AGG-36 停用检查表模板版本；AGG-28 登记不符合项送达、记录重大险情已报告；AGG-13 提交/通过/驳回合同评审；AGG-15 提交协议变更审批及两个回调（通过/驳回协议变更审批，本批按 AGG-12 模式补）；AGG-17 提交风险交付授权审批、驳回风险交付授权、撤回风险交付授权申请；AGG-46 撤销红字信息确认；AGG-51 登记保证金应缴。
2. AGG-44 与 AGG-46 同名命令「开具销项发票并改写 WIP」：聚合稿两处都列（INV-73，C 类同事务例外）。目录两处各一条，AGG-44 侧靠 `unused_ok`，状态机里它对应「开票占用已转实」。建议 AGG-44 侧改名「开票占用转实」，与收入侧的「占用转实」同语，由 one-architecture 订正聚合稿。
3. AGG-13「履行中」没有进入命令：聚合稿只写状态，B-08 的「完成协议」才离开它。本批写成事件触发转换（已签署生效 → 履行中，command: null，触发条件待定，候选为首个交付物建立或项目开工）。
4. AGG-44 一台机承载多类行（额度变动、开票占用、收入确认占用、冲销占用、视为同意认定、确认事实）。脚本每聚合只认一台机，故用带前缀的状态名区分。若将来要按子表各出一台机，需 converge_check 支持 `entity` 键。
5. AGG-49 的逾期、争议与结清程度本是正交的，AGG-50 的商票兑付与分配同理；脚本一聚合一台机，本批用互斥状态或前缀状态并列近似。逾期后部分核销会回到「部分结清」，由每日检测再标逾期。
6. AGG-37 外部检测：业主不委托时「已建议」没有终止命令；AGG-92 已开放类型无关闭命令（骨架行 12300 已提）。均为状态机上的缺口，非脚本错误。
7. 「提交过账候选」不改业务状态，只追加过账候选提交事实，状态机里写成在相关状态上的自环转换，仅为满足「改状态步骤对得上转换」。

### 收敛·新用语

沿用 NOTES 已交的命令名，本批没有再造词。需要登记的是状态名（均为领域用语，bc 为所属聚合的 bc）：
- B-26：检查计划「已登记、生效、不再受托」；检查「已计划、已安排、现场进行中、记录已提交、已审核、已锁定、已取消」；外部检测「已建议、已委托、报告已登记、报告已采信、报告未采信」；检查表模板版本「已停用」。
- B-28：不符合项「让步已接受、已复核、整改中、整改已申报、已闭合、风险已保留、已停止跟踪、已关闭」；证据「已采集、已封存、已作废」；证据关联「已关联、已解除」。
- B-05：协议「草案、审批中、已签署生效、履行中、已完成、已结算、已终止」；协议变更「变更审批中、内部审批已通过、已获发包人同意、未获同意、现场签证已确认、已生效」；索赔「已认定、已拒绝或撤回」；风险交付授权「已申请、审批中、已批准、已转正、已叫停、已驳回、已撤回」；框架限额占用行「占用中、已释放」。
- B-08：开票申请「已提交、已准入、已开具、已驳回、已作废」；销项发票「红冲确认中、已红冲」；AGG-44 占用行「开票占用已转实、开票占用已回冲、收入占用中、收入占用已转实、收入占用已释放、冲销申请已登记、冲销占用中、冲销已转实、冲销已释放、视为同意已认定、收入确认事实已追加、额度变动已追加」。
- B-09：应收「未结、部分结清、逾期、争议中、已结清、坏账已核销、已冲减、已转预收或待退」（注意「坏账已核销」与「核销」重名问题 PRC-61 已交 one-glossary）；收款「待认领、已认领、已分配、已处置、商票已收到、商票已逾期、商票已兑付」。
- 术语冲突提示：AGG-28 的「已关闭」「已闭合」两状态并存（我方成果走关闭，客户资产走闭合），聚合稿命令即分「关闭不符合项」「闭合不符合项」，沿用，请 one-glossary 判是否同义异形。

### 收敛·格式

1. `from: ~` 表示新建；`command: null` 表示事件触发（converge_check 允许），本批有两处：AGG-13 已签署生效 → 履行中，AGG-45 已准入 → 已开具。
2. 同一命令导致不同终点时，写多条转换，用 `guard` 区分（如 AGG-49 核销占用、AGG-15 登记发包人书面同意）。guard 里同时放了不变量号与普通条件描述（如「核销合计<未结余额」），因为脚本不解析；建议约定规则卡号放前、文字条件放后。
3. 命令目录增加两个非约定字段：`proposed`/`proposed_basis`（拟命令及依据）。转换加 `note`（含「拟命令」标记）。机级加 `note` 说明建模约定。
4. authority 写成 `{A: ...}`，权责表未登的写「待定（权责表未登）」，未用 `resolve` 结构，等权责表补全后由脚本生成。
5. 目录里 `merged_from`：AGG-44 认定视为同意成立（并入「确认视为同意期满」），追加收入确认事实并占用转实（并入「确认收入」）。

### 收敛·待用户定

1. AGG-35「现场进行中」：建议加「开始现场检查」（拟命令，已进目录）并允许「提交检查记录」从「已安排」直达，以免现场补录被卡；同时加「取消检查」（原因必填）。理由：现场因天气、通行条件无法进行是检查服务的常态，无处登记会逼检查人员绕过系统。
2. AGG-13 履行启动触发：建议以「首个交付物建立」或「项目开工」二选一触发，不设人工命令。理由：履行中对阶段门不引用（R-01），人工命令只会增加漏点。
3. AGG-37 外部检测业主不委托：建议加终止命令「放弃外部检测」或超期自动关闭。理由：目前「已建议」永不终结，影响不符合项闭合的统计口径。
4. AGG-92 已开放证据类型的关闭命令：建议加「关闭证据类型」（年度复核发现不再适用时用）。理由：ADR-0047 的年度复核无落点。

# 聚合收敛 · VS-01/02 的本组聚合（2026-10-16，子代理起草，主会话合并）

主会话按语：converge_check 修复后（条目级 aggregate 优先），删去为绕脚本而在 REF-01 下重复登记的「登记建设工程」「更新外部状态」，REF-01 状态机只留「登记招标项目」；全组复跑仅余财务组 AGG-52、53、59（由 PRC-86、147、153 跨 BC 调用，命令与状态机归 finance 组）。


范围：AGG-01–12、18、102（B-01/02/03）；AGG-19–27、29–33、103–105（B-04/B-28）；AGG-39（B-06）；另含骨架挂的 REF-01，共 33 台状态机（187 条转换）、131 条命令（拟命令 9 条）。VS-03/VS-04 批 19 台、124 条未改。脚本结果见 `check.txt`：孤儿命令 0（8 条靠 `unused_ok`），状态全可达、非终态均能到终态，骨架 199 个改状态步骤全部对上转换。剩余 7 条「骨架命令未入目录」与 3 个「无状态机」：AGG-52/53/59 属财务组不在本批；AGG-38 两条是上一批已记的接缝；AGG-03 两条见接缝 1。

### 收敛二·接缝

1. 骨架 PRC-12（单聚合维护）单元级 `aggregate: AGG-03`，四条命令里两条阶段类型命令命令级写 AGG-04，转换也写 AGG-04，聚合稿同。converge_check 只认单元级聚合，所以报 `AGG-03 发布阶段类型版本`、`AGG-03 停用阶段类型版本` 未入目录，同时本批 AGG-04 两条命令靠 `unused_ok` 兜底。目录按聚合稿放 AGG-04。建议改脚本为命令级 aggregate 优先，或骨架把 PRC-12 拆成两个单元。
2. 骨架 PRC-14（单聚合维护）把「登记建设工程」「更新外部状态」「登记招标项目」「登记我方参与记录」挂在 REF-01，聚合稿 §2.2 的 AGG-33 才有前两条与后一条，REF-01 只有「登记招标项目」且「无聚合」。本批 REF-01 目录与状态机照骨架写（前两条标拟命令），AGG-33 按聚合稿写（前两条靠 `unused_ok`）。建议骨架把 PRC-14 拆为 AGG-33 与登记招标项目两个单元，REF-01 不再当聚合。
3. AGG-38 关联证据、解除证据关联仍由骨架 PRC-33 挂 AGG-38（上一批接缝 1，未改）。
4. 骨架 PRC-13 步 3（费率版本批准）无 aggregate、command 是审批名；步 2 编制费率、系数版本为人工步骤未写 command，故 AGG-05/06/07 无创建动作。本批补三条拟命令。
5. 权责表仍只有 AGG-44 一条，本批 131 条 authority 全写「待定」。

### 收敛二·结构

1. 聚合稿缺而骨架或已建代码已用的命令（拟命令）：AGG-23 填报产值估计（PRC-151）；AGG-32 登记资料需求（PRC-26 步 1）；AGG-39 撤回工时单（PRC-23 步 7，已建 `cancelSubmission` 与 `TimesheetApprovalWithdrawn`）。另有骨架与聚合稿都没有、为状态机创建动作补的拟命令：AGG-05 编制费率版本、AGG-06 编制计费系数版本、AGG-07 编制协议价目版本；AGG-39 起草工时单（已建 `Timesheet.draft()`）。
2. AGG-39 状态以已建代码为准：草稿、已提交、已退回、已通过、已驳回（`TimesheetStatus` 的中文对应），转换与 `Timesheet.MACHINE` 一致（草稿或已退回 → 已提交；已提交 → 已通过、已驳回、已退回、草稿）。「认定工时有效性」「提交过账候选」不改工时单状态，写成已通过上的自环。聚合稿事件表另有 `TimesheetCancelled`，代码事件为 `TimesheetApprovalWithdrawn`，名称不一致，请 one-architecture 订正。
3. AGG-18 独立性检查没有创建命令：骨架只有登记结论，「待查」状态从何而来无命令，本批写成事件触发创建（触发条件待定，候选为商机建立后）。
4. AGG-11 投标没有创建命令，本批以「登记资格预审结果」作创建（骨架 PRC-08 步 3 是首个命令）。招标文件无需资格预审的投标如何建立，聚合稿未说明。
5. AGG-25 资源需求、AGG-26 资源分配各只有一条命令，需求被满足、分配调整与释放都无命令，故状态机只有单一终态。AGG-24 项目团队同（Q13 待定）。
6. AGG-20 WBS：一棵树含阶段与工作包两类节点，一台机用前缀状态近似；工作包「已锁定」可作废（骨架 PRC-21 如此），与「锁定」的含义有张力，请 one-rules 确认。
7. AGG-105 调查已发出但永不回收的，无命令关闭；AGG-09 等同类情形无。AGG-27 的「已认可」之后客户再退回、已声明后客户异议的处理，聚合稿未定，本批只允许已声明 → 已退回。
8. 版本化主数据：AGG-01 档案核实后变更银行账户只追加账户版本（生效 → 被取代）；AGG-02 延续与降级产生新版本，旧版本被取代；AGG-03/04 各版本并行，「启用」= 已发布（聚合稿 RD-10），没有「被取代」；AGG-05/06/07 为 审批中或待生效 → 已生效 → 被取代，新版本生效时旧版本同事务被取代（INV-30、35）。

### 收敛二·新用语

状态名均为领域用语，bc 为所属聚合的 bc，需登记：
- B-01：往来单位角色「角色申请中、角色合格、角色暂停、角色停用」；银行账户「银行账户版本生效、银行账户版本被取代」；资质证书「核查中、已到期、已撤销、被取代」。
- B-02：费率版本「审批中、已批准、已生效、被取代」；计费系数与协议价目版本「待生效、已生效、被取代」。
- B-03：线索「待认领、跟进中、挂起、已确认、已关闭」；商机「推进中、已赢单、已丢单、已放弃」（避免与线索同名「跟进中」）；投标「资格预审已登记、已递交、已中标、未中标、已废标、已撤销、已放弃」；报价版本「草拟、审批中、已通过、驳回、已生效、被取代、已作废」；独立性检查「待查、无冲突、有冲突已获书面同意、有冲突不得承接」。
- B-04：项目「已立项、执行中、暂停、已结项、已终止、已取消、已归档」；WBS「阶段未开始、阶段进行中、阶段已完成、工作包未发布、工作包已锁定、工作包已作废」；完工估算「产值估计已追加」；交付物「已内审、已签发、已送达、已退回、已声明、已认可、已封存」；设计变更「已认定归因、已内审、已发出、待主张、不主张、已关闭」；延期「已通知、已提交说明、已批准、已拒绝、视为批准」；发包人资料「待提供、已提供、已确认、已退回」。
- 术语冲突提示：「已批准」「视为批准」（延期）与 AGG-27 不用「视为同意」，请 one-glossary 确认两者不会与 ADR-0049 的「视为同意」混用。「被取代」在多聚合出现，含义一致（版本被新版本取代）。

### 收敛二·格式

1. 沿用上一批格式：`from: ~` 新建，`command: null` 事件触发（本批一处：AGG-18 创建），`proposed`/`proposed_basis`，转换 `note` 标「拟命令」。
2. 新版本取代旧版本写作旧版本实例上的转换：同一命令（如生效费率版本）既有 待批准 → 已生效，又有 已生效 → 被取代，后者 note 注明「同事务」。创建新版本实例用 `from: ~`（AGG-02 延续、降级）。
3. 同一命令因结果不同到不同终点的，写多条转换用 guard 区分（AGG-02 登记核查结果、AGG-09 关闭线索、AGG-30 标记待主张）。
4. 一聚合多类实例（AGG-01、20、23）用前缀状态名，机级 `note` 说明，同上一批 AGG-44 的做法。

### 收敛二·待用户定

1. AGG-18 独立性检查的创建时点：建议商机赢单前由「独立性检查要求」事件创建（IDI 风险管理机构才需要），否则检查记录无来由。
2. AGG-05/06/07 是否补「编制」命令进聚合稿：建议补，否则版本对象无创建与修订的落点。
3. AGG-25 资源需求加「已满足 / 已关闭」状态：建议加，否则无法统计未满足需求。
4. AGG-20 已锁定工作包可作废：建议保留并要求原因，现场变更常见。
5. AGG-11 无资格预审的投标如何创建：建议「登记资格预审结果」允许结论为「无需预审」。

## C 类自定后的待补（C_DEFAULTS.md，2026-10-16）

- 新用语 / 命令缺口（聚合稿回补）：「撤销设计变更」（AGG-30）、「撤回延期通知」（AGG-31），只追加撤销记录（VS02-11）；PRC-31、PRC-32 骨架随回补加 withdraw。
- 规则化（交 one-rules）：VS02-14 三处自提自审拒绝；VS03-02/03/04/05 已在 VS-03 规则化小节（VS03-01–04）；VS04-19 即 PAY-R1。

# 权责片段 raci.yaml（S-36；2026-10-16，子代理起草，主会话合并）


产物：raci.yaml，256 条（commands.yaml 全部命令；含 REF-01）。A：实例级 187、类型级 69；sensitive 50 条。自查：每条恰一个 A；类型级 role 均为 SH- 编号并带 function；骨架 cost_object_owner 的审批命令全为实例级；engine aggregate 的类型级 role 与骨架 approver 一致；platform.approval 类型级例外仅 AGG-44 占用收入额度（带 basis）。与 rm.yaml 现有 revenue 条目比对：A 种类、role、R、I 无冲突。raci_build.py 只读骨架，不读权责片段，缺 A 172 为合并前口径。AGG-52 / 53 / 59 三条在骨架里有而 commands.yaml 无，属财务组，未入本片段。

### 权责·拿不准的 A（请 one-architecture 或用户定）

1. 实例字段名多为拟定，数据模型里不一定有同名列：市场活动.负责人、客户活动.负责人、标准成本费率表 / 计费系数 / 协议价目.维护部门负责人、保证金.承担部门负责人、借阅人.所属部门负责人、协议.承担部门负责人（协议表未见该列，项目侧为 project_attribute_versions 的承担部门）。需 one-data 确认或补列。
2. 系统自动或事件触发且骨架无审批的命令（如检测应收逾期、释放框架限额、封存证据），A 取实例级责任人而非类型级角色（责任人更精确）；额度与过账候选类已按指示取 SH-M1 财务负责人。若要统一类型级，需另定各聚合的类型级角色。
3. 开票与红冲链（AGG-44 / 45 / 46）取 协议.承担部门负责人；PRC-58 红冲审批、PRC-50 任务订单审批的 why_human 写「项目责任人」，两者可能不是同一人。待定归属项目还是协议。
4. 风险交付授权（AGG-17）：骨架只写 cost_object_owner，取 商机.负责人（签约前先行投入的风险承担者）；若对象是项目则应为项目.项目经理，且申请人（项目经理）与审批人可能同人，靠 AR-11 上级代批。叫停取 SH-M1 分管副总。
5. 签发交付物（AGG-27）：A 取 SH-E7 注册执业人员（专业责任人自己签发），骨架无审批；是否改为交付物.项目经理待定。
6. 不符合项（AGG-28）：登记、让步接受取 项目.项目经理（让步接受的人与提出人同为项目经理，偏弱）；检查线命令取 检查.检查负责人；确认、关闭取 SH-E4 质量审核人，复核取 SH-E4 检查审核人。让步接受是否应改为类型级专业角色待定。
7. 返工认定（AGG-29）取 SH-E4 质量审核人（专业判断），返工成本由项目承担，是否应由项目经理批待定。
8. 证据（AGG-38 / 110）：采集、上传、作废、关联取 检查.检查负责人；证据同时服务项目结项（封存取 项目.项目经理），非检查来源的证据无检查负责人，作废证据宜改类型级。
9. 往来单位（AGG-01）：多数取 SH-E6 往来单位管理；变更银行账户取 SH-M1 财务负责人（欺诈风险，骨架无步骤）；合并、停用是否也提到财务负责人待定。
10. 收款（AGG-50）整体取 SH-M1 财务负责人，R 为 SH-E6，未用往来款项.承担部门负责人（收款认领可能无应收可指）。
11. 变更商机负责人（AGG-10）取现负责人，交接场景是否应改上级代批待定。
12. 无骨架步骤的命令（53 条）的 R 为推定（按聚合主责角色），列于 gen.py 的 RDEF / RAGG；通过 / 驳回类审批命令 R 写 system。

### 权责·待用户定的权责问题

- 独立性检查（AGG-18）结论归谁：暂取 SH-M1 独立性复核，岗位待定。
- 服务产品版本、阶段类型版本（AGG-03 / 04）的发布权：暂取 SH-M1 分管副总。
- 费率表批准人（AGG-05）：骨架为 cost_object_owner，费率是全公司口径，「责任人」取维护部门负责人是否合适。

### 权责·sensitive 口径

true：往来单位证件与银行账户、费率与价目（tbd 分级）、协议价款与结算 / 变更生效、报价通过与生效、收入确认与冲销、红冲、坏账核销、员工借款冲抵、保证金扣减 / 没收 / 退还、待认领流水处置、证据、客户签认人身份、满意度回收与评分、线索联系人。其余 false 并写分级与个人信息理由。

### 新用语·PRC-183（参投决策，用户 VS01-10）

| 中文 | 拟英文 | bc | 范围 | 说明 |
|---|---|---|---|---|
| 作出参投决策 | DecideToBid（命令） | B-03 | 领域 | 适用招标、议标、直接委托 |
| 已决定参投 | BidDecided（商机状态） | B-03 | 领域 | 信息图 OBJ-18 已登状态 |
| 登记参投判断 | RecordBidAssessment（命令） | B-03 | 领域 | 判断清单评分与理由，状态不变 |
| 参投例外审批 | BidExceptionApproval（单据类型） | B-03 | 领域 | 低于毛利底线、超产能、独立性冲突时一级审批 |
| 商机已决定参投 | OpportunityBidDecided（事件） | B-03 | 领域 | |
| 参投决策记录 | OpportunityBidDecision（表 `opportunity_bid_decisions`） | B-03 | 领域 | |

### 新用语·报价发出（B31-16）

| 中文 | bc | 范围 | 说明 |
|---|---|---|---|
| 报价发出编排 | presales | 领域 | REF-04（ADR-0057），名称待 one-glossary 定；无聚合，向 B-31 核验实际用印后调用 AGG-12 或 AGG-11 命令 |
| 登记报价发出 | presales | 领域 | AGG-12 命令；只记录发出方式、日期、客户联系人、用印登记 ID，不改状态、不校验真伪 |
| 用印登记 ID | presales | 领域 | 发出命令引用 AGG-113 实际用印登记的凭据标识；报价版本、法人、拟发文件须对应 |
| 报价已发出 | presales | 领域 | PRC-07 非投标发出成功结局，不是领域事件 |
| 报价发出被拒（未用印） | presales | 领域 | PRC-07 拒绝结局；含未用印、版本或法人或拟发文件不符、读不到与调用失败，均失败并告警 |
| 递交被拒（未用印） | presales | 领域 | PRC-08 递交前核验拒绝结局，同上 |

### 新用语·N7 改名回写（NM6 用户已认可，2026-10-17）

骨架按 `N7_DOWNSTREAM_RENAMES.md` revenue 节 130 处精确槽位回写（只改中文名，不改结构与英文标识）。下列命令名 terms.yaml 暂无命中，属 NM6 已认可定名，待 one-glossary 写入词表：开始执行项目（AGG-19）、标记检查计划不再受托（AGG-34）、登记商机赢单（AGG-10）。

### 命令卡待定·终止 / 取消项目复核（用户 GL3 提醒，one-coordinator 2026-10-21 转）

终止项目、取消项目（AGG-19，PRC-24）现由项目经理本人发起并决定，是否需上级复核待定；用户原话：「终止/取消项目的批准人就是项目经理本人，要不要上级复核应在 revenue 命令卡单独定，别靠改名字解决。」出命令卡时给推荐，需用户定的随确认页改版。commands.yaml 两条命令已加 pending_decision。

### AGG-21 待定·对客基线与内部基线（用户追问，one-coordinator 2026-10-21 转）

用户原话：「请 revenue 再定一条：对客基线是否必须与某一内部基线内容一致？不要求 → 两条链并行即可；要求 → 需加约束（对客基线须引用其来源内部基线），否则进度考核与结算依据会各说各话。」

现状：AGG-21 交付计划版本分对客、内部两条链，各只有一个生效版本，互不引用（state-machines AGG-21 note；PRC-18）。

推荐（介于两项之间，偏「要求」）：**不要求内容一致，但要求可追溯**——
1. 对客基线版本须引用其编制所据的内部基线版本（拒：缺引用不得提交发包人批准）；
2. 内部基线须包含对客基线的全部合同里程碑，且同名里程碑的内部日期不晚于对客日期，不满足只警告、不拒绝（内部计划通常比对客计划更紧，留缓冲是通行做法）；
3. 进度考核按内部基线，结算、延期与索赔依据按对客基线（合同进度计划）。

依据（AEC 通行做法）：合同范本把经发包人认可的进度计划作为合同文件，进度延误、工期顺延与索赔以它为准（FIDIC 1999/2017 第 8.3 条「进度计划」、NEC4 的 Accepted Programme）；承包人 / 咨询方内部另设目标进度并与合同进度计划对账，而不是两份同内容（PMI《进度管理实践标准》的基准与目标进度区分）。两条链完全并行会让考核与结算对不上，内容强制一致又会取消内部缓冲。**核对状态：未核实**——上述条款出自模型知识，FIDIC、NEC、PMI 原文不在桌面公开资料、公开资料研究目录与仓库中，出页前需取得原文核对条号与措辞（或改为不引条号的通行做法表述）。

需用户定：是否采纳上述「引用 + 里程碑对齐警告」；若采纳，命令前置与不变量（新 INV）随命令卡落地。随确认页改版交用户。

### GL5-01 已签署 / 已生效分两状态（用户 2026-10-22，intent 2026-10-22-061733，one-architecture 转）

落地（本组骨架）：AGG-13 状态链改为 草案 → 审批中 → 已签署 → 已生效 → 履行中 → 已完成 → 已结算 / 已终止。
- 回签：无附生效条件的，签署与生效同一事务，只落「已生效」，同时记生效依据（条件类型「签署即生效」、证据单据号为回签合同文件、生效日为回签日），发 ContractSigned 与协议已生效；附生效条件的落「已签署」，只发 ContractSigned。
- 新拟命令「确认生效条件满足」（PRC-47 第 7 步）：已签署 → 已生效，生效依据三项必填、证据须已登记，是唯一途径，不得手工改状态（ADR-0058）。
- 已签署期间只允许关联项目、归档、借阅与终止；变更、任务订单、付款条件、开始履行都要求「已生效」。PRC-68 加已签署 → 已终止。
- PRC-51 额度改订阅「协议已生效」（附条件协议签署后不建额度）。原「已签署生效」各处改「已生效」（任务订单结局改「任务订单已生效」）。

### GL5-01 待用户定：生效条件确认人与「已签署」停留时限（第 ③ 点，随确认页交）

1. 谁有权确认生效条件满足——推荐：合同负责人（协议上登记的负责人，商务岗执行）凭证据登记确认，不另设审批；我方须履行的条件（如履约保函提交）由财务资金岗登记证据后合同负责人确认。理由：条件成就是事实认定、凭证据可核，审批只会增加等待；生效后的变更与价款事项另有审批。出处：经验判断；法律依据为附生效条件的合同自条件成就时生效（《民法典》第 158 条，**未核实**：原文不在本地资料，出页前核对）。
2. 条件长期不满足时最多停多久——推荐：合同约定了条件成就期限的按约定；未约定的，签署后 30 天提醒合同负责人，90 天升级部门负责人决定继续等待或经 PRC-68 终止；不自动终止（终止涉及对外通知与保证金处理，须人决定）。出处：经验判断。

### 用户 LEB 答复落地（LEB-20261022-082103-ioy，2026-10-22，one-architecture 转，九题全选推荐）

- LEB-01 支持合同主体变更：AGG-15「提出协议变更」变更类型加「合同主体变更」，前置须三方协议证据、新法人在五家之内；PRC-48 第 2 步说明与场景 S13。
- LEB-02：主体变更时原法人开完已确认部分（LE_RECEIVABLES_WIP 第 3 条）。
- LEB-03：项目不必转，跨法人人工按成本组「待内部结算」记（LE_DELIVERY_EXECUTION 第 3 条，与成本组 LEB-04 互引）。
- LEB-05：先行投入按申请授权时承担部门法人立项（PRC-16 第 2 步）。
- LEB-07（本节上方「AGG-21 待定」已定）：INV-140、INV-141 已由 one-architecture 在聚合稿正式登记（a915e3a23）；命令前置、数据模型列族、PRC-18 场景 S07、S08 已加。
- LEB-08（上方「GL5-01 待用户定」第 1 点已定）：确认生效条件满足的 A 为合同负责人，凭证据、不设审批；我方须履行的条件由财务资金岗先登记证据。
- LEB-09（同上第 2 点已定）：PRC-47 第 7 步 30 天提醒、90 天（timeout P90D）升级部门负责人，不自动终止；场景 S13。

### 新用语·应收默认账期（KPI-Q04，ARC-1022-ARE-01）

| 中文 | 拟英文 | bc | 范围 | 说明 |
|---|---|---|---|---|
| 应收默认账期规则版本 | ReceivableTermRuleVersion（表 `receivable_term_rule_versions`） | receivables.collection | 领域 | AGG-136 |
| 编制账期规则版本 | DraftReceivableTermRule（命令） | receivables.collection | 领域 | PRC-202 |
| 生效账期规则版本 | ActivateReceivableTermRule（命令） | receivables.collection | 领域 | PRC-202 |
| 账期规则审批 | ReceivableTermRuleApproval（单据类型） | receivables.collection | 领域 | 财务负责人批准 |
| 账期来源 | PaymentTermSource（应收列族） | receivables.collection | 领域 | 付款条件项 / 规则版本与覆盖行 / 全局默认 |
