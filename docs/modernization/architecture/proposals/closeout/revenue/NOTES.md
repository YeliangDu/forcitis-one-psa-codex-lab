# 收入线组 NOTES（one-arch-revenue；小节按 closeout/CONVENTIONS.md 第 4 节；各子批原样收录，主会话条目在每节末尾）

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
5. 结局词（待与阶段出口用语对齐）：已回签生效、签约被拒、新基线已生效、变更已驳回、已提交待答复、索赔已认定、索赔已拒绝或撤回、占用被拒、订单已提交并通过审批、限额已释放、视为同意已成立、视为同意未成立、待办已取消、已开具、已驳回、已作废、因期间已关账未开具、已红冲、红字确认已作废、因期间已关账未红冲、已核销、待认领、无逾期、已登记处置、坏账已核销、因期间已关账未核销、已兑付、商票已逾期、因期间已关账未登记兑付、履约未完成、协议已结算、借款已发放、借款已结清、因期间已关账被拒、借阅申请被驳回、借出被拒、已归还、已归档。
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
