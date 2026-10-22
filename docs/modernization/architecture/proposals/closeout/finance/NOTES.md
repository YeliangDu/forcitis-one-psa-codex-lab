# finance 组 NOTES

本文件合并了初稿阶段的 `NOTES.processes.md`、`NOTES.data-model.md`，铺开阶段的 `notes/frag_P1_notes.md`、`frag_P2_notes.md`、`frag_P3_notes.md`（原始片段保留不动），以及 `EXPENSE_COVERAGE.md` 第 3 节的结构问题，并按用户新原则改稿（intent 163556 与 one-coordinator 2026-10-16：能由规则和不变量守的不设人工审批；正常单人、异常加一级、特殊加两级，审批人一律费用或业务归属对象的责任人；期间闸门按 ADR-0045 与 INV-92 落开放期间；审批结果走事件回调；可撤回；通知只写 `notify`）。样板见 `SAMPLE.md`，首轮评审见 `REVIEW.md`，格式依据 `closeout/CONVENTIONS.md`，结构裁定台账 `closeout/STRUCTURAL_ITEMS.md`（S-01–S-26）。

范围：54 条（业务流程 29、事件策略 12、单聚合维护 11、读模型 2；S-29 新增 PRC-158、159，S-31 新增 PRC-163–176 共 14 条），验收场景 662 条（见 `scenarios.finance.yaml`）。BC：B-07、B-10、B-11、B-12、B-16、B-17、B-19–B-24。S-31（33 条结构问题）、S-34（四件）、S-36（权责表合并）已落地；本稿依据聚合稿 cb39fc65 与 afc068bb 之后的 GOV 分支。

## 聚合稿回补

S-16 至 S-19 由 one-architecture 裁定（台账 `closeout/STRUCTURAL_ITEMS.md`）。以下是聚合稿 `ENTERPRISE_AGGREGATES_DRAFT.md` 需要回补的行，由 one-architecture 合入，本组不改聚合稿。基线为 Q-10 新增两条事件边之后的版本（origin/claude/one-architecture-adr-governance :728，intent 134134）。

| # | 位置 | 现状 | 回补为 | 依据 |
|---|---|---|---|---|
| 1 | AGG-59 费用报销「对外事件」列 | 费用报销已核定（仅分析） | 费用报销已核定（B-09：冲抵员工借款，并按净额确认应付或置报销已付） | S-16、S-17、S-19；Q-10 |
| 2 | AGG-59「命令」列 | 标记费用报销已付（→ （内部）） | 标记费用报销已付（→ （内部）；参数：结清方式——付款结清 / 借款冲抵） | S-19 |
| 3 | AGG-59「引用与依赖方式」列 | AGG-52 提交应付（api）；AGG-52 应付已结清（事件） | 删去「AGG-52 提交应付（api）」（B-12 不再直接确认应付）；保留「AGG-52 应付已结清（事件）」 | S-17 |
| 4 | AGG-49 应收往来款项「引用与依赖方式」列 | 无报销与应付引用 | 加「AGG-59 费用报销已核定（事件）；AGG-52 确认应付（api）；AGG-59 标记费用报销已付（api）」 | S-16、S-17、S-19 |
| 5 | §4.1 依赖骨架 | B-09 → B-10、B-09 → B-12 两条为事件边（Q-10） | 两条边的方式都加「api」（B-09 → B-10：确认应付；B-09 → B-12：标记费用报销已付），不新增边 | S-17、S-19 |
| 6 | AGG-52 应付往来款项「聚合（根）」列的列举 | 分包 / 采购 / 报销 / 其他应付、预付、员工借款、…… | 删去「员工借款」（员工借款以 AGG-49 其他应收为准） | S-18；⑥a 评审 H5 |
| 7 | AGG-52「对外事件」列 | 应付已确认（同 BC：B-10 付款流程） | 不变；注明报销来源的应付由 B-09 按净额调用确认 | S-17 |
| 8 | `processes.v1.yaml` PRC-87 | 第 8 步 B-12 确认应付（AGG-52） | B-12 确认应付一步删去；B-09 的反应为事件策略 PRC-147（订阅「费用报销已核定」后冲抵借款、按净额确认应付，净额为零时调 B-12 置已付） | S-17、S-19、S-24 |
| 9 | §1.2 INV-102「预算占用不超过可用额度」 | **待定**（拦截还是预警未定） | 改为「策略（警）」：预算不足只预警、不拦截，移到 §1.3 策略；AGG-80 预算占用不再是额度占用的前置 | 用户 F-Q2（intent 2026-10-16-162821） |
| 10 | §4.1 依赖骨架 | 无 B-31 → B-24 | 加边 B-31 → B-24（api：按卡片 ID 读资产卡片、报废时处置资产）；B-24 无上游到 B-31，不成环 | 用户已定（intent 2026-10-16-152926）；PRC-99 |
| 11 | §3.2 B-24 行及 :561、:680 处「G1 期间为 ledger 内导入通道」 | 默认 G1/G2 两步走 | 改为「形态待过渡 ADR」 | R-28、S-05 |
| 12 | `processes` 目录 PRC-101 | `human_orchestration: true` | 改 false（确认关账是聚合命令，没有 platform.approval 人工任务） | R-15、S-12 |
| 13 | 目录与聚合稿引用列中 PRC-70、75、94、97 的审批人 | 固定角色（财务负责人等） | 审批人按费用或业务归属对象的责任人解析（`cost_object_owner`），加审引用 APPROVAL_RULES 中 `guard: approval` 的规则；关账确认、对账签字、股东决议是聚合命令，approver 取职责角色 | 用户原则（intent 163556） |
| 14 | AGG-53 引用列 | 无 AGG-62 | 加 AGG-62 人员（api：付款对象为员工时读收款账户）；INV-80 员工侧并入 | QC-VS05-13 |
| 15 | AGG-58 命令列 | 无释放履约额度 | 加 释放履约额度（→（内部）） | QC-VS05-03；SQ-43 |
| 16 | AGG-57 命令列 | 无冲销 | 加 冲销供方履约确认（→（内部），加审 AR-06） | QC-VS05-03；SQ-43 |
| 17 | AGG-59 命令列 | 无冲销 | 加 冲销费用报销（→（内部），加审 AR-06） | QC-VS08-07；SQ-45 |
| 18 | AGG-98 命令列 | 无作废 | 加 作废手工凭证草稿（→（内部）） | QC-VS10-07；SQ-46 |
| 19 | AGG-99、AGG-41、AGG-42 命令列 | 无更正 | 加 冲销期末结转运行、作废人工成本分配运行、冲销分摊运行 | QC-VS10-07；SQ-17 |
| 20 | AGG-54 不变量与命令 | INV 列为空 | 加 INV：累计实缴不超过认缴、退回不超过已实缴；分期实缴与更正命令（SQ-05②） | QC-VS12-04；SQ-47 |
| 21 | AGG-109 命令列 | 无撤销决议 | 加 撤销利润分配决议（→（内部）） | QC-VS12-01；SQ-48 |
| 22 | AGG-90「对外事件」与 §4.1 | 申报已提交、税款已缴纳仅分析 | 申报已提交的订阅方加 B-10（税费应付确认，方向 B-10 → B-23 边已有）；税费应付由 B-10 事件策略确认 | QC-VS06-01；SQ-44 |
| 23 | AGG-84 推送状态 | 待推送、已推送、已确认记账、死信 | 加 挂起（主数据未同步）；再入由定时或同步事件触发 | QC-VS10-04；SQ-50 |
| 24 | AGG-87 命令列 | 开立、冻结、销户 | 开立与销户加确认命令（财务负责人）；解冻命令同 SQ-07 | QC-VS05-09；SQ-42 |
| 25 | AGG-89 不变量 | 无登记唯一性 | 加 INV：同一发票代码与号码只登记一次 | QC-VS05-10；SQ-53 |
| 26 | AGG-77 命令列与 §1.3 | 无拒收记录处置 | 加 重提过账候选、登记拒收处置结论（拒收记录是 AGG-77 内部技术表 posting_rejections，处置另表只追加）；目录加 PRC-158 处置过账拒收记录（B-16 单聚合维护） | S-29 SQ-41 |
| 27 | AGG-87 命令列与事件 | 开立、冻结、销户 | 加 确认开立资金账户、确认销户资金账户、解冻资金账户（engine aggregate，类型级 SH-M1）；事件加 资金账户已解冻；状态加 待确认开立、待确认销户 | S-29 SQ-42（替换第 24 行） |
| 28 | AGG-52 命令列 | 无人工处置、无冲回 | 加 人工处置核销差异（人工核销或转冲销付款，二选一，留痕）、冲回应付核销（订阅付款已退票，恢复未结余额，同 BC） | S-29 SQ-49；S-30 ③ |
| 29 | AGG-53 命令列与事件 | 状态已成功 → 已失败（退票） | 加 登记退票（已核销 → 已退票）、事件 付款已退票（B-10 内 AGG-52 订阅）；退票冲销过账经冲销并重过账 | S-30 ③ |
| 30 | AGG-54 INV 与命令 | 空 | INV-127 累计实缴不超过认缴、INV-128 退回不超过已实缴；命令加 更正出资实缴，登记出资实缴支持分期，状态加 部分实缴（替换第 20 行） | S-29 SQ-47 |
| 31 | AGG-57、AGG-59、AGG-98、AGG-109 命令列 | 无冲销、作废、撤销 | 冲销供方履约确认（加审 AR-06）、冲销费用报销（加审 AR-06）、作废手工凭证草稿、撤销利润分配决议（替换第 16–18、21 行）；AGG-58 加 释放履约额度（替换第 15 行） | S-29 SQ-43、45、46、48 |
| 32 | AGG-89 INV 与 AGG-52 应付类型 | 无登记唯一性、无税费应付 | INV-129 同一发票代码与号码只登记一次；登记人与确认用途人分离写流程 segregation，不作不变量；应付类型加税费（PRC-159） | S-29 SQ-53、SQ-44 |
| 33 | INV-100 与 INV-130 | 前置检查项未列全；争议拒付无编号 | INV-100 扩展检查项（受控手工凭证在途、成本分配与分摊运行办结、拒收记录已处置、银行余额调节），不另编号；INV-130 应付争议状态拒绝付款；付款条件未到期只作提前付款预警（规则提示，不拦） | S-29 SQ-54 |
| 34 | AGG-84 命令与推送状态 | 无挂起、无登记外部记账确认 | 推送状态加 挂起（每日定时重投，不新增主数据已同步事件）；命令加 登记外部记账确认（已推送 → 已确认记账，system，platform.integration 回执触发）（替换第 23 行） | S-29 SQ-50；S-30 ② |
| 35 | AGG-81 命令列 | 无年度开账 | 加 开立会计年度（财务负责人，按法人一次生成第 1–12 期，状态开放）；下一年度未开立的年末预警入 §1.3 策略 | S-30 ② |
| 36 | AGG-43 命令列 | 结转命令（→ 成本结转已过账）与提交过账候选（→（内部））并存 | 四个结转命令改为（→（内部））；提交过账候选 → B-16；订阅 BusinessFactAttributed 后「标记已过账」发成本结转已过账；运行单据被更正后原单置「已冲销」，「已被取代」只用于版本化配置（AGG-109 的已取代同改） | S-30 ①③ |
| 37 | 目录与事件目录 | 申报已提交仅分析 | PRC-158、PRC-159 入目录；申报已提交订阅方 B-10（PRC-159）；沿既有边 B-10 → B-23，无新边 | S-29 SQ-44 |

### S-31–S-36 回补续行（基线：GOV 分支 afc068bb，聚合稿 cb39fc65 已回补 AGG-120–133、INV-131–137、INV-92 写法）

聚合稿已按 S-31 回补的不再重列。下面是本稿落地时与聚合稿不一致、或聚合稿未覆盖的逐条，交 one-architecture；AGG-122–133 的命令名以聚合稿草案名为准，本稿已逐条对齐，不一致处列在第 43 行。

| # | 位置 | 现状（聚合稿） | 回补为 | 依据 |
|---|---|---|---|---|
| 38 | AGG-78、AGG-124 命令列 | 只有「发布」 | 加「编制口径版本」「编制资产类别与折旧政策版本」（照 AGG-123、133、86 先例；骨架 PRC-168、166 步 1，目录 proposed） | S-31 SQ-10、19、22 |
| 39 | AGG-52 命令与状态 | 已有登记其他应付、登记预付款、预付款冲抵应付 | 加「冲销应付利润」（撤销利润分配决议后注销未付应付，无核销方可注销；PRC-112 步 7，proposed）；状态加预付款已登记、部分冲抵、已冲抵，冲抵同时核销被冲抵的应付；登记预付款的事件、预付款进 PRC-75 待付清单的触发未列；INV：冲抵双方往来方一致（〔缺〕） | S-31 SQ-29；S-34 ② |
| 40 | AGG-109 状态与命令 | 撤销利润分配决议（→（内部）） | 状态「已撤销」，不用「已冲销」（决议不是运行单据）；后继决议取代原决议亦置已撤销，留原决议；已过账的应付股利经冲销过账更正；「登记利润分配支付」由 PRC-163 单一触发 | S-34 ②③ |
| 41 | AGG-130 命令列 | 提交、批准、驳回、撤回、关闭 | 加「标记费用事前申请已用尽」（报销累计达额度时置位，PRC-87 回补；目录 proposed，标 unused_ok） | S-31 SQ-26 |
| 42 | AGG-131 命令列与 INV | 已有登记差旅订单与月账单、归属、核对、确认差旅账单应付、提交过账候选、登记超标订单待审批、批准/驳回差旅超标订单 | 加「处理账单差异」（PRC-171 步 5，proposed）；INV：超标订单须对应已批出差申请（现只写规则出口，〔缺〕INV） | S-31 SQ-27、28 |
| 43 | 命令名对齐 | 骨架初稿用自拟名 | 已改用聚合稿名：生效/编制…版本 → 发布…版本（成本核算参数、资产类别与折旧政策、口径、出差补贴标准）；登记差旅订单、登记差旅月账单 → 登记差旅订单与月账单；同步出差申请 → 登记出差申请已同步；回写超标订单审批结果 → 批准/驳回差旅超标订单；更正出资实缴 → 更正出资登记（聚合稿草案名） | 协调会话追加 |
| 44 | AGG-80（他组，B-18） | 已有调整预算占用 | PRC-71 步 4 使用；本组 commands.yaml 不收 AGG-80，不改 | S-31 SQ-08 |
| 45 | AGG-77 命令列 | 无重提过账候选、登记拒收处置结论 | S-29 SQ-41 已裁定而聚合稿未列；PRC-158 仍标〔缺〕，目录 proposed | S-29 |
| 46 | 事件目录 | 期初余额已生效订阅方 | B-20（余额起点，同 BC）；已登记 events.finance.yaml | 协调会话追加 ③ |
| 47 | 事件目录 | 应付已结清、应付已核销订阅方 | 应付已结清加 B-10 PRC-163（来源为利润分配，单一触发）；应付已核销去掉 PRC-112 | S-34 ③ |
| 48 | 骨架 B-21 | 订阅关账事件 | PRC-101、102 已删向 B-21 的 event out；PRC-106 读模型按需查询，订阅栏为空 | S-31 SQ-03 |
| 49 | §4.1 新边 | B-07 → platform.scheduling；B-19 → platform.audit；B-12 → platform.integration；B-12 → B-10 加 api | 已核对骨架 cross_bc 方向一致：PRC-15、96 登记定时；PRC-101、102 写关账审计记录；PRC-171–173 订阅集成事件；PRC-171 经 AGG-131 调确认应付 | S-31 SQ-18、20、12、27、28 |
| 50 | INV-92 写法 | 关账中不接受业务日期在本期的过账 | PRC-92 步 1、3 与 28 个触账步骤已加分支「期间关账中、可重试」（结局「期间关账中待重试」，不进拒收记录）；显式指定期间的通道（PRC-94 手工凭证、PRC-95 期末结转、PRC-100、105 配置生效日）所指期间未开放（含关账中）即拒绝 | S-31 SQ-12、32 |
| 51 | AGG-125 | 占位号（原「新-F1」） | 已全部改 AGG-125（data-model、commands、state-machines、responsibility、processes、NOTES）；命令用聚合稿名「编制期初余额」「签字确认期初余额」；旧「复核期初余额」随 A1-02 把关方式再定 | S-31 SQ-21 |
| 52 | 权责表 A 的写法 | role 写「财务负责人」等职能名 | role 只写 SH- 编号，职能写 function（SH-M1 + 财务负责人 / 采购与分包负责人，共 131 处）；AGG-40 生效分摊规则版本改实例级；付款审批挂 AGG-53 发起付款（PRC-75 审批步已改到发起付款之前的位置，使 gated_target 指向发起付款） | S-36 |
| 53 | 状态机 AGG-109 / AGG-52 / AGG-41、42、99 | 无作废、冲销、预付 | 已在 state-machines.yaml 加：AGG-41 已作废、已冲销；AGG-42、99 已冲销；AGG-52 已冲销与预付三态；AGG-43 冲回成本结转 | S-31 SQ-04、17、29 |

## 接缝问题

已按聚合稿 §4.1 核对所有 `cross_bc` 方向；审批回调 `platform.approval → 业务 BC` 的事件边与 `提交审批` 的 api 边，在聚合稿引用列含 platform.approval 的 B-07、B-10、B-11、B-12、B-20 上已有。以下为与 §4.1 不一致或需确认的接缝。

| # | 位置 | 问题 | 处理 |
|---|---|---|---|
| 1 | PRC-92 api in（B-03） | B-03 → B-16 在 §4.1 无边（revenue 组 PRC-08 调过账）；建议 PRC-08 的保证金过账其实在 B-09（AGG-51），去掉 PRC-08 的 calls，或登记该边 | revenue 组与 one-architecture 裁；本组不改边 |
| 2 | PRC-87 → B-09（Q-10） | 已裁定（S-16、S-17、S-19、S-24；intent 134134）：B-09 → B-12 事件边与 B-09 → B-10 边已存在，**只需给两条边加 api 方式**（B-09 → B-10 确认应付；B-09 → B-12 标记费用报销已付），不是新边；AGG-59「标记费用报销已付」要带结清方式（付款结清 / 借款冲抵），见聚合稿回补第 2、5 行 | 已裁定，不再提问 |
| 3 | PRC-74 内的占用进项发票入账 | 实际是 B-10 命令内部调 B-23（边存在），流程 owner 是 B-11，`cross_bc` 只记 B-11 → B-10；B-11 → B-23 无边。沿用 v1 的 inner 写法 | 请 one-architecture 确认记法；不改边 |
| 4 | PRC-75 的 B-10 → platform.identity | 边存在；INV-81 校验在「审批付款」与「登记银行结果」两处，yaml 在审批步写 `cross_bc`，登记步在判断点里写；AGG-53 引用列已含 platform.identity | 不新增边 |
| 5 | PRC-77 缴款的资金支出 | 修后按「税费应付 → PRC-75 付款 → 完税凭证登记」写：B-10 订阅 AGG-90「申报已提交」确认税费应付（事件方向 B-23 → B-10 与 §4.1 边 B-10 → B-23 一致，事件目录要把仅分析改为 B-10 订阅）；B-23 不得读 B-10（无此边），完税凭证与付款只按 ID 引用；税费过账时点待总账确认（SQ-44、U-41） | 请 one-architecture 判断 |
| 6 | PRC-64 触发「银行文件已到达」 | 由 platform.integration 发布，不在聚合稿事件目录；是否进事件目录请定 | 请 one-architecture 定 |
| 7 | PRC-76 的 B-29 读 | `direction: in`（B-29 → B-11 api），边存在 | 无 |
| 8 | PRC-101、102 的 event out → reporting | 聚合稿 §4.1 骨架没有 reporting 节点（分析库按 ADR-0023 决定 2 订阅全部事件，「仅分析」约定见 F-4）；B-21 订阅 AccountingPeriodClosed 由读模型 PRC-106 承接 | 请确认是否把 reporting 记入骨架 |
| 9 | 关账事件发布方是 B-19，编排方是 B-17 | PRC-101、102 属 B-17，命令在 AGG-81（B-19），事件由 B-19 发布；event out 写在触发它的步骤上；B-20 → B-19、B-21 → B-19 事件边已有，方向一致 | 无 |
| 10 | B-17 订阅 BusinessFactAttributed / Restated 无反应命令（G-19 ②） | PRC-92 沿 v1 保留向 B-17 的 event out；B-17 实际只在月结检查时读，建议聚合稿订阅方列改为「不订阅，api 读取」 | 已裁定（S-31 SQ-04）：B-17 不订阅，api 读取；PRC-92 已删向 B-17 的 event out |
| 11 | 发起关账 ≠ 确认关账没有 INV 编号 | 只写在 AGG-81 内含列与 ADR-0045 决定 1，聚合稿 §1.2 无条目；判断点只能引 ADR；建议补一条（ADR-0046 §8.5 要求发起人、确认人入审计记录） | 请补 INV |
| 12 | PRC-99 步 2、4 的 B-31 → B-24 | 用户已定的新增边（intent 2026-10-16-152926），方向 B-31 调 B-24（direction in）与新边一致，见聚合稿回补第 10 行 | 已裁定 |
| 13 | PRC-99 步 4 经 api 调「处置资产」 | 权限与审计以谁的身份记（B-31 经办人代入还是服务身份）；处置金额由 B-24 按卡片算，B-31 只传卡片 ID 与报废原因 | 请 one-architecture 定 api 调用的身份承载约定；本稿按「B-31 经办人代入、B-24 记审计」写 |
| 14 | PRC-25 | B-07 对 B-04、B-05 只有事件边没有 api 边。「合同已签回且项目已建立两者齐备」「一商机多合同按 WBS 挂接归属（Q12）」要在 B-07 内自存已收事件与归属，事件载荷须带商机、合同、项目、WBS 挂接 | 记入 AGG-43 内含补列（SQ-18）；不新增边 |
| 15 | PRC-90 | reporting 订阅 AccountingPeriodClosed（既有边）；经营计划（AGG-79）、预算（AGG-80）数据走 reporting 的事件投影（L1），不 api 读 B-18、B-13 之外的 BC | 无新边 |
| 16 | PRC-104 角色 | v1 把 SH-X7（资金监管主办银行）列为外部方，实际对方是 U8C（外部系统）；已去掉，U8C 写在 `where: external:U8C` | 见格式问题 F-10 |
| 17 | PRC-87、PRC-73 与 B-23 | B-12 → B-23 与 B-10 → B-23 均有边；PRC-74 由 B-10 命令内部调 B-23 | 无 |
| 19 | PRC-77 提交申报 → B-10 事件「申报已提交」 | 方向符合 §4.1（B-10 → B-23 为下游到上游，事件由 B-23 发出）；订阅方登记待回补（聚合稿回补第 22 行） | 请 one-architecture 确认 |
| 18 | PRC-72 与 PRC-70 等的 platform.approval 事件回调 | 审批结果统一写 `cross_bc: {mode: event, direction: in, bc: platform.approval, name: 审批已通过 / 审批已驳回}`；本稿写在审批步骤自身（照样板 PRC-87），CONVENTIONS 第 5 节写「下一步」 | 见格式问题 F-16 |

### S-31 后（PRC-163–176 与改动）

| # | 位置 | 问题 | 处理 |
|---|---|---|---|
| 54 | PRC-163 | 部分支付怎么置（协调会话追加 ②）：只订阅 AGG-52「应付已结清」，不再订阅「应付已核销」。利润分配每位股东一张应付，某股东的应付结清即置该股东的分配额已支付；仍有其他股东未结清时利润分配为「部分支付」，全部结清为「已支付」；单张应付只部分付款只发「应付已核销」，不触发本策略，利润分配状态不变；PRC-112 已删该入口 | 已裁定（S-34 ③） |
| 55 | PRC-175 与 AR-04 / RULE-407 | 直接付款无来源单据与「付款须引用生效合同或协议」冲突 | 已裁定（S-34 ④）：只受理 RULE-407 豁免清单内的付款类别，清单外照 AR-04 拒绝；归属对象只取组织单元或法人，经 B-13 解析，删去读项目的 B-04 调用，不加 B-10 → B-04 边；与项目有关的付款从项目侧单据发起（步 1 已加拒收分支）；清单由用户定（U-51） |
| 56 | PRC-87 步 1 | 原写「事前申请暂不引用」 | 已回补：报销读 AGG-130 额度并在超额时预警，读 AGG-132、133 生成补贴行；已用尽由报销累计置位（命令 proposed，待回补） |
| 57 | PRC-75 触发 | 触发只有「应付已确认」，登记预付款（PRC-175 步 5）不发该事件，预付款如何进待付清单未定 | 待补事件或触发，见结构问题 S31-结构-7 |
| 58 | PRC-171 | B-12 提交费用事实过账、B-10 确认应付，AGG-52 不得对来源 B-12 的应付重复提交过账（聚合稿 §7 第 13 条同理） | 请 one-architecture 在 AGG-52 提交过账候选的前置里写明；骨架 PRC-171 步 6 经 AGG-131 调确认应付，步 7 由 AGG-131 提交过账候选 |
| 59 | PRC-101 | 前置检查须读 AGG-126 未关闭案（INV-135 扩 INV-100 检查项） | 已回补：步 1 reads 加 AGG-126，checks 加一条，PRC-169 由此检查触发 |
| 60 | PRC-93 ↔ PRC-104 | 登记外部记账确认的触发方 | 已裁定（S-31 SQ-11）：由对账读到 U8C 记账结果后调用；原 PRC-93 步 3 与「外部法定账记账回执已到达」事件已删，移入 PRC-104 步 2 |
| 61 | PRC-75 步序 | 付款审批挂「发起付款」（S-36 ②） | 已改骨架：审批步移到发起付款之前的位置（list 顺序），流向仍是发起 → 审批 → 登记银行结果，使 gated_target 指向 AGG-53 发起付款；登记银行结果不是审批对象 |
| 62 | PRC-112 步 7、8 | 撤销决议后的应付与过账 | 增「冲销应付利润」（〔缺〕proposed）与「冲销并重过账」（B-16），已过账应付股利经冲销过账更正（S-34 ②） |
| 63 | 跨 BC 方向 | 新流程逐条对过聚合稿 §4.1 | B-12 → B-04/B-03/B-13/B-10(api)/platform.approval/platform.integration；B-10 → B-01/B-13/platform.approval；B-07 → B-16/platform.scheduling；B-17 → B-16/B-19/B-20；B-20 → platform.integration/B-16；B-10 → B-15 的事件（B-15 订阅）；B-19 → platform.audit；无不一致 |

## 结构问题

状态「已裁定」的不再提问，只记依据；「待裁定」的交 one-architecture（或注明 one-data、one-glossary），不进用户确认页（R-18）。

| # | 问题 | 建议 / 依据 | 状态 |
|---|---|---|---|
| SQ-01 | B-03 → B-16 无边（见接缝 1） | 去掉 PRC-08 的 calls 或登记该边 | 已裁定（S-31）：已覆盖。退还投标保证金已拆为 PRC-153（B-09），B-03 不过账、不加边；接缝 1 关闭 |
| SQ-02 | 利润分配：PRC-112 步 5「登记利润分配支付」无触发事件（G-10）；v1 的提交过账候选在支付登记之后，会计上决议时点就确认应付股利，更早，AGG-109 命令列无决议后过账 | 新增同 BC 事件策略（订阅「应付已结清」，来源为利润分配的应付，置部分支付 / 已支付并发「利润分配已支付」），不新增边；过账时点请与总账确认；不新编 PRC 号，等定号 | 已裁定（S-31；S-34 ③）：PRC-163 只订阅「应付已结清」，PRC-112 删支付登记与「支付后过账」，过账在决议时点由应付侧提交 |
| SQ-03 | B-21 无流程承接：AccountingPeriodClosed 订阅方 B-21（出具报表）与对账状态检查没有流程（本组只有 PRC-105 报表行定义）；PRC-106 读模型已承接出表查询 | 补「出具法定报表」流程（B-21，事件触发，api 读 B-20 快照与 AGG-101 状态，INV-107 前置），或按读模型处理 | 已裁定（S-31；协调会话追加 ①）：改写，PRC-106 按需查询，不订阅关账事件，INV-107 为查询前置 |
| SQ-04 | B-17 订阅 BusinessFactAttributed / Restated 无反应命令（G-19 ②）；B-07 对 BusinessFactRestated 也无反应命令（PRC-55、96 只订阅 Attributed） | 聚合稿 B-17 订阅方列改「不订阅，api 读取」；B-07 的反应命令请定 | 已裁定（S-31）：B-17 不订阅，api 读取；B-07 新增 PRC-164 反应 BusinessFactRestated |
| SQ-05 | 出资 AGG-54：①出资退回的支出须落 AGG-52，类型表无「出资退回」（G-11）；②状态为「认缴 → 实缴 → 部分退回 / 已退回」，没有「部分实缴」，若有分期实缴状态机要加 | ①按其他应付承载，待用户确认（待用户定 U-14）；②请定 | ① 交用户（U-14、U-15）；② 已覆盖（S-29 SQ-47） |
| SQ-06 | 预提与冲回无来源聚合（G-12）；暂估应付：PRC-73 写「无票先确认应付」，但 AGG-52「确认应付」的合约是「先在进项发票上占用再写应付」，无票时占用能否跳过、取得发票后「重入占用」是哪个命令，聚合稿没有（PRC-74 步 1 标 pending P1-Q2） | 指定 AGG-57 或 AGG-98 候选类型承载预提；定暂估应付的命令形态 | 已裁定（S-31）：② 改写——暂估在履约确认过账时处理，AGG-52 占用前置不变（PRC-72、73、74），是否允许无票暂估 U-25、形态 U-07 待用户定；① 推迟（预提与冲回已交用户，U-46） |
| SQ-07 | 资金账户没有「解冻资金账户」命令（AGG-87 只有开立、冻结、销户，冻结后只能销户，PRC-65） | 〔缺〕解冻资金账户，是否需要请定 | 已裁定（S-31）：已覆盖（S-29 SQ-42） |
| SQ-08 | 分包变更价款变化后，AGG-80 只有「预算占用 / 释放预算占用 / 调整预算」，没有按增减额重占用的命令（INV-10 只对框架协议写了重占）；PRC-71 未写预算调整 | 需要的话补命令 | 已裁定（S-31）：AGG-80 调整预算占用（按增减额，一次调用），PRC-71 加一步 |
| SQ-09 | PRC-75 的发起与出纳都是 SH-E6（职能人员），靠 `segregation` 按人员判定；出纳是否拆成独立角色 | 请在角色清单层面定 | 交用户（S-31）：出纳是否单列角色，J 系列；PRC-75 标 pending SQ-09 |
| SQ-10 | 配置版本缺「起草 / 编制」命令：AGG-82、85、94（PRC-100：〔缺〕起草科目表版本、起草映射表版本、起草授权表版本）与 AGG-40（PRC-97：〔缺〕编制分摊规则版本）只有「发布 / 批准 / 生效」，「起草人 ≠ 批准人」无处落；草稿另存、版本行确认时才写（R-31） | 回聚合稿补命令或草稿状态；对照 AGG-86 有「编制报表行定义版本」 | 已裁定（S-31）：起草命令入目录，INV-131 起草人不得为批准人（PRC-97、100） |
| SQ-11 | AGG-84 推送状态「已推送 → 已确认记账」没有对应命令（PRC-135 状态机） | 〔缺〕登记外部记账确认，建议并入「推送与重投」或补命令，由对账读取 U8C 记账结果后回写 | 已裁定（S-31）：登记外部记账确认由对账（PRC-104）读到 U8C 记账结果后调用，移出 PRC-93 |
| SQ-12 | 月结检查无聚合（B-17 零聚合，检查结果不落状态）；「关账中」是否仍接受过账未定（聚合稿 §7 第 15 条，R-18 判属架构问题）；若仍接受，发起与确认之间检查结论会失效 | 请定「关账中」口径，并定检查结论是否需要落一个技术记录 | 已裁定（S-31）：INV-92 新写法，关账中不接受本期过账，返回「期间关账中、可重试」；检查结论带摘要写审计记录，边 B-19 → platform.audit |
| SQ-13 | 待处理队列清空路径与「配置新版本只在期间首日生效」冲突：事实因授权表或映射表缺项进队列，补配置后新版本按 ADR-0022 决定 2 只在期间首日生效，本期队列清不空、关不了账。备选：(a) 生效日可定为当前开放期间首日；(b) 缺项事实改经受控手工凭证；(c) 冲销该事实后下期重提 | 财务取舍见待用户定 U-27 | 交用户（S-31；U-27，推荐 (a) 只增不改） |
| SQ-14 | 第 13 期：INV-108 只允许「年度审计调整类」受控手工凭证显式指定第 13 期，AGG-99 期末结转运行没有显式落账期间；ADR-0046 移交 P-3 推荐第 13 期允许模板期末凭证，若采纳须扩展 AGG-99 与 INV-92。另 AGG-98 内含的凭证类别「年度审计调整类」在无法定审计下是否保留（S-06 已裁定去向过渡 ADR，把关方式待 A1-02） | 随 ADR46-P3 与 A1-02 回写 | 已裁定（S-31）：推迟，随 ADR-0046 P-3、A1-02 |
| SQ-15 | 新旧科目对照缺聚合与表：PRC-104 对账要经它把 U8C 余额对到新科目（ADR-0022 决定 8，R-26）；归属 B-20 还是 platform.integration 防腐层 | 请定归属 | 已裁定（S-31）：新旧科目对照在 platform.integration 的 U8C 适配器内翻译，本组撤表 |
| SQ-16 | 「推送与重投」命令在两处使用：首次推送在 PRC-93 自动完成，死信重投在 PRC-135；命令粒度（逐凭证还是批次）聚合稿未写 | 随 AGG-84 细化 | 已裁定（S-31）：按凭证一次，幂等外部键按凭证；批量是调度细节 |
| SQ-17 | 更正命令缺：AGG-42 每期间一次有效全量运行（INV-90）、AGG-41 每期间一次有效，v1 写「冲销按原比例」（P-42），但两个聚合都没有冲销或作废命令 | 补〔缺〕冲销分摊运行、〔缺〕作废人工成本分配运行（只在提议里，骨架未用） | 已裁定（S-31）：作废、冲销命令落 PRC-96、98、95 |
| SQ-18 | AGG-43 内含只列「结转行」，没有已收事件登记与 WBS 归属（PRC-25 判齐备、Q12）；定时分支「超 N 个月转费用」没有定时命令或触发约定（G-04）；PRC-15 暂以同一命令「售前投入转费用」由定时触发，N 默认 6 个月、可设，按版本生效（D15） | 补内含与定时触发约定；N 的配置载体交 one-data | 已裁定（S-31）：AGG-122、AGG-123、INV-132、定时约定（B-07 → platform.scheduling）、PRC-165 |
| SQ-19 | AGG-91 内含列为「—」：资产类别（含 IT 资产）、卡片状态、折旧政策（方法、年限、残值率，D15 版本化）、卡片 ID 供 B-31 引用都没有地方登记；资产原值入账（取得借记固定资产）由谁过账未写；R-06 要求 AGG-91 根表不存可版本化的列 | 补内含列；定取得过账是 AGG-91 的提交过账候选，还是来自采购应付（B-10、B-11） | 已裁定（S-31）：卡片根表只存不变身份，AGG-124 与 PRC-166；U-29、U-31 待用户定 |
| SQ-20 | PRC-96 触发语义：AGG-41 对 AGG-77 是 api 读，分配月末一次；v1 触发是逐笔事实的 BusinessFactAttributed，事件每条到一次，「月末才触发」无承载 | 触发改为月末定时、事件仅用于预警；定前保留 v1 | 已裁定（S-31）：PRC-96 改人工发起或月结日程定时，不订阅逐笔事实 |
| SQ-21 | B-20 期初余额没有聚合（R-05）：ADR-0022 决定 9、11 要求期初表经财务签字，§14.2 M7 又规定迁入事实不生成分录，G-2、G-3 准入靠它。本稿以「AGG-125 期初余额」占位出 4 张表 | 请给聚合号，并裁定命令与事件（含签字前置） | 已裁定（S-31）：AGG-125 与 INV-133、134，PRC-167；占位号 AGG-125 全部改 AGG-125 |
| SQ-22 | B-17 口径版本、勾稽差异案（R-13）：AGG-78 聚合稿「不计」，本稿撤了表；词表已批准 `policy_version`、`reconciliation_deviation_case` 为 aggregate，ADR-0016、0023 的版本化口径与差异案生命周期必须有表 | 请定聚合；定号后表名即 `policy_versions`、`reconciliation_deviation_cases` | 已裁定（S-31）：AGG-78 口径版本计入、AGG-126 勾稽差异案、INV-135、PRC-168、169 |
| SQ-23 | 待处理队列（INV-98）放 AGG-84 下作无父行的技术表（同 AGG-77 拒收记录写法），`journal_entries` 因此改为只追加 | 请确认放 AGG-84 还是独立技术聚合 | 已裁定（S-31）：队列放 AGG-84 下作无父行技术表，分录只追加（INV-95a） |
| SQ-24 | 缺表清单余项：口径转换规则版本、期末结转模板版本、现金流量项目、辅助核算维度定义、推送死信与外部凭证对照、关账前置检查结果、应付争议分类取值表（G1 导入通道要保留的原始文件随 S-05 过渡 ADR 定形态） | 给归属；未定前不出表 | 已裁定（S-31）：AGG-127–129、INV-136；其余不建表 |
| SQ-25 | 来源不在老系统与 A8 的历史（法定凭证、银行流水、申报与缴款、合伙人出资、利润分配、公司资产卡片）标 none 后，U-3、U-4「全部历史明细迁入」对这些对象无承载 | 投产日余额由 SQ-21 承载；历史是否从 U8C、银行、税务端取回见待用户定 U-33；若取回须有 ACL 导入用例，与过渡 ADR 一并定 | 交用户（S-31；U-33，推荐不取回历史明细，投产日在用对象须初始化） |
| SQ-26 | 费用事前申请没有聚合与流程（G-F1）：业务招待事前申请（额度）；AGG-59 已写「超事前预批额度 P-37 预警」但额度无落点 | 新聚合「费用事前申请」（B-12）与新流程「事前申请费用额度」（走审批平台）；是否扩到所有费用类别交用户（U-34） | 已裁定（S-31）：AGG-130 与 PRC-170；类别范围 U-34 待用户定 |
| SQ-27 | 分贝通公司直付订单的月账单对账、应付、付款、成本归属都没有（G-F2）；老系统只做到拉取月账单、整月核对 | 新流程「核对分贝通月账单并确认应付」：订单（external:分贝通）→ 按出差申请归属 → 月账单核对 → 确认供方应付（AGG-52，往来方分贝通）→ PRC-75 → 费用事实过账；编排 BC（B-12 或 B-10）与订单、账单是否成聚合交 one-architecture | 已裁定（S-31，把握低）：AGG-131、PRC-171、INV-137；付款方式 U-39 待用户定 |
| SQ-28 | 出差申请与分贝通超标审批没有落点（G-F3）；出差补贴标准（按差旅类型与天数）要做成按版本生效的配置（G-F7，D15） | 新聚合「出差申请」（B-12）与新流程「申请出差并同步分贝通」，超标审批为该流程的审批节点并回写分贝通；补贴标准配置由财务编制、财务负责人批 | 已裁定（S-31）：AGG-132、133，PRC-172–174；取值 U-35 待用户定 |
| SQ-29 | 直接付款申请（无来源单据）、预付核销、外部代发：PRC-75 由「应付已确认」触发，没有「先申请付款、后形成应付」的入口（G-F4） | 确认 AGG-52 是否提供「登记其他应付 / 预付」命令与对应流程 | 已裁定（S-31；S-34 ④）：AGG-52 登记其他应付、登记预付款、预付款冲抵应付，PRC-175、176；豁免清单交用户（U-51） |
| SQ-30 | 信息服务费备案（G-F5） | 判断是否为供方协议（AGG-55）的一种类型，属则并入 PRC-70，否则新增 | 已裁定（S-31，把握低）：并入 PRC-70 作协议类型，字段待取证 |
| SQ-31 | 分贝通作为外部系统的集成登记（订单、发票、账单、审批回写、员工与组织单向同步）（G-F6） | 按 §14.3 登记集成册；同步归 B-13 或 platform.integration | 已裁定（S-31）：集成册登记；员工与组织同步 PRC-177 归 people 组 |
| SQ-32 | PRC-95 期末结转遇目标期间已关账：本稿保留拒绝（规则提示，不路由到其他期间），因期末凭证结转的是该期间余额，落其他期间无意义；这是 INV-92 / ADR-0045 决定 2「落开放期间」的例外 | 请确认例外成立，并写入 INV-92 或 ADR-0045 | 已裁定（S-31）：不是 INV-92 / ADR-0045 的例外（显式指定期间的通道所指期间未开放即拒绝） |
| SQ-33 | 审批归属对象解析：「费用或业务归属对象的责任人」对非费用类单据（供方应付付款、手工凭证、分摊规则、利润分配、税费、薪酬）如何取；本稿暂按协议所属项目或部门、法人的财务部门解析 | 建议 platform.approval 载荷由业务 BC 带归属对象 ID，由组织与责任人登记解析；用户部分见 U-19 | 已裁定（S-31）：载荷带归属对象与已解析的责任人；余项无单一归属对象见 U-19 待用户定 |
| SQ-34 | PRC-101 目录里 `human_orchestration` 应为 false（R-15） | 目录改 false，见聚合稿回补第 12 行 | 已裁定（R-15、S-12） |
| SQ-35 | Q-10 员工借款边方向与调用方式：B-09 订阅「费用报销已核定」，先冲抵再按净额调 B-10 确认应付，净额为零调 B-12 置已付；B-12 不再直接调 B-10；PRC-87 步 8–10 独立为事件策略 PRC-147 | 聚合稿回补第 1–8 行 | 已裁定（S-16、S-17、S-19、S-24；intent 134134） |
| SQ-36 | AGG-52 内含列仍有「员工借款」 | 以 AGG-49 为准，删去 | 已裁定（S-18） |
| SQ-37 | 关账确认、对账签字、股东决议是聚合命令，不经 platform.approval；只有 PRC-70、75、87、94、97 走审批平台 | `approval.engine` 取 aggregate / platform.approval / external | 已裁定（R-15、S-12） |
| SQ-38 | ADR-0022 的 G1/G2 两步走、「U8C 权责账套只读」、导入通道形态，与一次投产、对外按开票冲突 | 过渡 ADR 取代；流程里一律写「形态待过渡 ADR」，`pending: [S-05]` | 已裁定（S-05） |
| SQ-39 | ADR-0022 决定 9、ADR-0047「审计师书面认可」因无法定审计不成立 | 进过渡 ADR；把关方式待 A1-02 | 已裁定（S-06） |
| SQ-40 | B-31 → B-24 新边（IT 资产卡片的账务归 AGG-91，B-31 只按卡片 ID 引用） | 聚合稿回补第 10 行 | 已定（用户 intent 2026-10-16-152926） |
| SQ-41 | 过账被拒收的处置路径：PRC-92 只写拒收记录并告警，没有谁、何时、经哪个入口重提；PRC-101 前置检查含「B-16 队列为空」却无清空流程。本稿已在各触账步骤的拒收出口标 `pending: SQ-41`，PRC-92 步 1 把幂等判断提前，使同一幂等键的重提不被期间闸门挡 | B-16 新增一个处理过账拒收记录的维护（事件策略或单聚合维护，需新流程号）：财务查看原因、补主数据或开放期间后以同一幂等键重提（INV-94），超期告警，处置结论留痕；PRC-101 前置检查加「拒收记录已处置」 | 已裁定（S-29）：PRC-158；PRC-101、102 检查加「拒收记录已处置」 |
| SQ-42 | AGG-87 资金账户的开立与销户无审批命令，PRC-65 已写 aggregate 审批（SH-M1）与分离，命令缺 | 聚合稿回补第 24 行 | 已裁定（S-29）：确认开立、确认销户、解冻，engine aggregate，类型级 SH-M1 |
| SQ-43 | AGG-58 缺「释放履约额度」（PRC-72 步 3 因 INV-83 拒收后须释放步 2 的占用）；AGG-57 缺「冲销供方履约确认」（确认后更正） | 聚合稿回补第 15、16 行；步骤标〔缺〕释放履约额度 | 已裁定（S-29） |
| SQ-44 | 税款缴纳的资金控制：PRC-77 改为税费应付经 PRC-75（发起≠审批≠出纳）。需 B-10 事件策略订阅 AGG-90「申报已提交」确认税费应付（新流程号、新订阅关系），税费过账时点（申报时或缴款时）与总账确认 | 聚合稿回补第 22 行；本稿步 3 标 pending SQ-44，步 6 过账暂留在缴款登记之后 | 已裁定（S-29）：PRC-159；过账时点待 U-41 |
| SQ-45 | AGG-59 缺冲销已核定报销命令（PRC-87 步 7 标 pending，AR-06 加审无落点） | 聚合稿回补第 17 行 | 已裁定（S-29） |
| SQ-46 | AGG-98 缺作废手工凭证草稿命令（PRC-94 步 1 增「已作废」出口） | 聚合稿回补第 18 行 | 已裁定（S-29） |
| SQ-47 | AGG-54：缺不变量（累计实缴不超过认缴、退回不超过已实缴）与分期实缴、更正命令；PRC-111、114 的拒收分支引用占位 | 聚合稿回补第 20 行（含 SQ-05②） | 已裁定（S-29）：INV-127、128 |
| SQ-48 | AGG-109 缺撤销或修改利润分配决议命令（PRC-112 步 3） | 聚合稿回补第 21 行 | 已裁定（S-29） |
| SQ-49 | PRC-131 核销占用与分配行不一致时付款已成功而应付未核销，缺人工处置命令（人工核销或冲销付款） | 聚合稿回补；PRC-131 拒收分支标 pending | 已裁定（S-29）：人工处置核销差异 |
| SQ-50 | AGG-84 推送状态缺「挂起」；主数据已同步到 U8C 的事件不存在，PRC-93 现以每日定时重投兜底 | 聚合稿回补第 23 行；是否新增集成事件 | 已裁定（S-29）：挂起状态，保留每日定时重投 |
| SQ-51 | 手工凭证（PRC-94）与分摊规则（PRC-97）没有费用归属对象，`cost_object_owner` 如何解析（SQ-33 的补充）：本稿权责表 A 写实例级，解析规则取「凭证所属法人的财务部门」「间接成本池归属的财务部门」的责任人（财务负责人）；AR-01 的加审对象即其本人时按 AR-11 上提 | 由 one-architecture 与 one-data 定归属对象字段；若不接受，改为类型级角色并说明 platform.approval 例外（CONVENTIONS 第 5 节「类型级例外」需 ADR 或用户决定出处） | 已裁定（S-29）：手工凭证取凭证所属法人的财务负责人，分摊规则取间接成本池归属财务部门的负责人；字段落点交 one-data |
| SQ-52 | 缺流程：银行流水匹配与银行余额调节（未达账项）、预提与冲回（E12、G-12、SQ-06）、期末汇兑重估（有外币时）、固定资产盘点与减值；PRC-101 前置检查已为银行余额调节留检查项 | 需新流程号与聚合归属；向财务确认是否需要 | 待用户定（范围题，见待用户定 U-46） |
| SQ-53 | AGG-89 缺发票登记唯一性不变量（同一发票代码与号码只登记一次）；登记人与确认用途人是否分离写入不变量 | 聚合稿回补第 25 行 | 已裁定（S-29）：INV-129；登记人≠确认用途人写 segregation |
| SQ-54 | 规则编号缺口：①INV-100 前置检查扩展（受控手工凭证在途、人工成本分配与分摊运行办结、拒收记录已处置、银行余额调节）是否扩展 INV-100；②付款前置：协议付款条件项到期（提前付款预警）与应付争议状态（拒绝）补不变量或策略号（PRC-75 步 1 标 pending） | one-architecture 确认；对照 ADR-0022 决定 10 与财会〔2024〕12 号第 24 条 | 已裁定（S-29）：INV-100 扩展检查项、INV-130；提前付款只预警 |

SQ-01–SQ-33 已由 S-31 裁定：采纳或改写 23 条，已覆盖 3 条（SQ-01、07、32），推迟 1 条（SQ-14），混合 3 条（SQ-05、06、33），整条交用户 3 条（SQ-09、13、25）；另 SQ-34–SQ-40 已裁定或已定 7 条；质检修后新增 14 条（SQ-41–SQ-54）中 13 条已由 S-29 裁定、1 条（SQ-52）待用户定；S-30 另裁定 3 项（AGG-43 拆骨架、年度开立与外部记账确认、退票冲回），均已落骨架。

### S-31 后新增（PRC-163–176）

聚合稿 cb39fc65 已给 AGG-122–133 命令名，原 S31-结构-1–5 的〔缺〕命令均已改用聚合稿名；剩余：

| # | 问题 | 建议 / 依据 | 状态 |
|---|---|---|---|
| S31-结构-1 | 费用事前申请「已用尽」的置位命令聚合稿未列（AGG-130 状态有已用尽） | 加「标记费用事前申请已用尽」（目录 proposed，unused_ok） | 待回补 |
| S31-结构-2 | AGG-131 缺「处理账单差异」命令（PRC-171 步 5） | 加命令（目录 proposed） | 待回补 |
| S31-结构-3 | 「超标订单须对应已批出差申请」目前只写规则出口 | 补 INV（AGG-131、132） | 待裁定 |
| S31-结构-4 | PRC-175 在审批通过前没有聚合承载申请态：申请单由审批平台保管，AGG-52 命令批准后才执行 | 沿 PRC-87 第 5 步写法；是否需要申请态聚合请裁定 | 待裁定 |
| S31-结构-5 | AGG-78、124 缺「编制」命令；INV-131 适用清单含 AGG-128、129，不含 AGG-78、123、124、133 | 加命令并扩 INV-131 适用范围（现仅写 segregation 与命令守卫） | 待裁定 |
| S31-结构-6 | 数据模型：超标订单审批表 business_trip_over_standard_reviews 原挂 AGG-132，而批准、驳回差旅超标订单是 AGG-131 的命令 | 本稿已改挂 AGG-131（references 指向订单表与 AGG-132）；请 one-data 复核 | 已改，待复核 |
| S31-结构-7 | 「登记预付款」产生的事件、预付状态、预付款进 PRC-75 待付清单的触发聚合稿未列；PRC-176 状态为暂拟，预付款冲抵同时核销被冲抵应付 | 回补事件与状态；INV：冲抵双方往来方一致（〔缺〕） | 待回补 |
| S31-结构-8 | AGG-52 缺「冲销应付利润」（S-34 ②） | 加命令；撤销决议时应付注销的前置 | 待回补 |
| S31-结构-9 | 引用口径版本的方向：business_facts（B-16）、AGG-41（B-07）的「口径版本引用」列指向 AGG-78（B-17），而 B-17 只读 B-16、B-19、B-20 | 按 ID 引用不建外键；请确认 §4.1 是否需要 B-16 → B-17 / B-07 → B-17 边，或引用留在 B-17 一侧 | 待裁定 |
| S31-结构-10 | AGG-122 引用 AGG-10（商机）、AGG-13（协议）、AGG-19（项目）：B-07 对 B-03、B-05、B-04 以事件边为准，PRC-25 经事件进来 | 沿用既有事件边，不加 api | 待确认 |
| S31-结构-11 | AGG-126 与勾稽族的关系（案件按勾稽族 × 期间 × 法人一案）未定 | one-data | 待裁定 |
| S31-结构-12 | PRC-96 作废、冲销（S-31 SQ-17）：冲销经 AR-06 加审，审批人按间接成本池归属财务部门的责任人（SQ-51 解析）；AGG-42、99 同 | 已按 PRC-87 的冲销分支写法落骨架 | 已落骨架 |
| S-4（BONUS_OVERTIME_COSTING） | PRC-96 读已核定奖金、日期类型、补休安排的来源与「成本核算输入取自 operations.facts」不一致 | 建议日期类型与补休随工时过账事实携带；已核定奖金加 B-07 → B-15 api 边（详见该稿 §4） | 待裁定 |

## 新用语

先 grep `terms.yaml`（中文名与 snake 都查，同 bc 查重，跨 bc 同名互列）。命令、事件名沿用聚合稿，没有新编。以下交 one-glossary，使用范围与 bc 一并登记；同名异义用 `new_term_issue.py` 登记，不自行裁定。

**流程与命令用语**

| 用语 | 用在 | 说明 |
|---|---|---|
| 命令暂名（聚合稿已有、词表未登记）：月结检查、发起关账、确认关账、退回开放、发起年末关账、确认年末关账、登记第 13 期启用、开放第 13 期、登记对账差异、签字接受差异、处理待处理队列、推送分录 | PRC-101、102、104、135 | 领域，B-17 / B-19 / B-20 |
| 〔缺〕命令：起草科目表版本、起草映射表版本、起草授权表版本、登记外部记账确认、编制分摊规则版本、解冻资金账户；提议：冲销分摊运行、作废人工成本分配运行 | PRC-100、135、97、65 | 见 SQ-07、10、11、17；同 BC 查重通过 |
| 单据类型暂名：供方协议审批、付款审批、费用报销审批、分摊规则审批、手工凭证审批、月结关账确认、年末关账确认、对账差异签字、利润分配决议 | 审批 | 0 条命中；聚合内确认的三项不走审批平台 |
| 审批已通过、审批已驳回 | 审批回调事件 | 平台事件，bc 为 platform.approval；同 cost.timesheet 接审批的做法 |
| 银行文件已到达 | PRC-64 触发 | 平台事件，bc 为 platform.integration |
| 公司资产、固定资产、IT 资产、折旧与摊销、处置资产 | B-24 聚合与命令 | 领域，B-24，词表查无；「IT 资产」是资产类别取值，B-31 的「IT 资产使用记录」是 AGG-115，两词交判同名异义 |
| 经营月报、售前投入 | 读模型名、事件策略名 | 词表查无（经营月报 bc 为 reporting；售前投入 bc 为 cost.costing，与「合同履约成本」的关系见 AEC 对象稿 §2.5） |
| 概念：拒收记录（技术记录，非聚合根）、待处理队列、新旧科目对照、关账提醒日、归属对象责任人（cost_object_owner）、规则提示 | 多处 | 领域 / 共享内核待定 |
| 结局词：已拒收、过账被拒收、已撤回、已放弃、查验未完成、幂等已返回原结果、重复事件已忽略、已有有效运行、检查未通过保持开放、已挂起待重投、已记账无需推送、对账未完成、年末已关账、快照已生成、已发布、已生效、已签字接受、差异已清零、应付部分核销、等待齐备、本期已分配、本期已分摊、无需转费用 | 流程结局 | 流程用语，非聚合状态，非领域标识符；统一与否交 one-glossary；「应付部分核销」与词表 `cash_application` 的关系待判 |
| 已付（报销） | PRC-133 | 是否改称「已结清」交 one-glossary（S-19 已提） |
| 付款.发起 等权限码、各页面名 | 全部 | 暂名，汇总交 one-glossary |

**表级与数据模型用语**（词表已批准、本稿已改用的 snake：`payable`、`cash_payment`、`ledger`、`account`、`cost_transfer_batch`、`allocation_result`、`business_fact`、`accounting_period`、`expense_claim`；`policy_version`、`reconciliation_deviation_case` 用于 SQ-22）。下列 snake 与中文名在 `terms.yaml` 查无同名，建议登记，使用范围「领域」：

| 中文 | 表名（单数 snake） | bc |
|---|---|---|
| 分摊规则版本、分摊规则池 | allocation_rule_version、allocation_rule_pool（与 `indirect_cost_pool` 间接成本池不同义） | cost.costing |
| 人工成本分配运行 | labor_cost_allocation_run | cost.costing |
| 分摊结果行、成本结转批次行 | allocation_result_line、cost_transfer_batch_line | cost.costing |
| 应付来源、核销占用、应付核销、应付争议记录 | payable_source、payable_application_hold、payable_application、payable_dispute_record；「核销」与 receivables.collection 的 `cash_application`（核销）及 `ar_written_off`（坏账核销）同名异义，须互列；不用 write_off | payables.payment |
| 付款分配、付款银行匹配 | cash_payment_allocation、cash_payment_bank_match | payables.payment |
| 合伙人出资、出资记录 | partner_contribution、partner_contribution_entry | payables.payment |
| 利润分配、利润分配行、决议、支付 | profit_distribution(_line / _resolution / _payment) | payables.payment |
| 供方协议、协议条款、协议证据 | supplier_agreement(_term / _evidence) | procurement.subcontracting |
| 供方协议履约额度、额度占用 | supplier_agreement_quota(_hold) | procurement.subcontracting |
| 供方履约确认（行） | supplier_performance_confirmation(_line) | procurement.subcontracting |
| 分包变更、变更影响 | subcontract_change(_impact) | procurement.subcontracting |
| 报销发票引用 | expense_claim_invoice_ref | expense.spend |
| 过账拒收记录、过账序号计数 | posting_rejection、post_sequence_counter | operations.facts |
| 迁入占用、第 13 期启用登记 | period_migration_hold、period_adjustment_enablement | accounting.period |
| 科目表版本 | chart_of_account_version | accounting.ledger |
| 会计分录、分录行、待处理分录项、分录推送记录 | journal_entry(_line / _pending_item / _push_record)；与词表 `journal_voucher`（记账凭证，accounting.ledger aggregate，「分录载体」）是否同一概念待判，若同一则 `journal_entries` 改 `journal_vouchers`（ST-7） | accounting.ledger |
| 账簿科目余额 | ledger_account_balance | accounting.ledger |
| 收付权责科目映射表版本（行） | account_mapping_version(_line)；与词表 `accounting_mapping_rule_set`（核算映射规则）不同义 | accounting.ledger |
| 控制科目授权表版本（行） | control_account_authorization_version(_line) | accounting.ledger |
| 手工凭证（行） | manual_voucher(_line) | accounting.ledger |
| 期末结转运行（行） | period_end_carryover_run(_line)；与词表 `period_end_closing`（期末结转，command）关联 | accounting.ledger |
| 期间余额快照（行） | period_balance_snapshot(_line) | accounting.ledger |
| 外部法定账对账（差异、已推送未记账） | statutory_reconciliation(_difference / _unbooked_push) | accounting.ledger |
| 期初余额表（行、口径差异、签字） | opening_balance_set(_line)、opening_balance_basis_difference、opening_balance_sign_off | accounting.ledger（待 SQ-21 定聚合） |
| 报表行定义版本（映射） | report_line_definition_version、report_line_mapping | accounting.reporting |
| 资金账户（版本） | fund_account(_version) | accounting.cashbank |
| 银行流水导入、银行流水行 | bank_statement_import、bank_statement_line | accounting.cashbank |
| 进项发票、占用、转出 | input_invoice(_hold / _transfer_out) | accounting.tax |
| 申报、缴款、更正 | tax_return、tax_payment、tax_return_correction | accounting.tax |
| 公司资产（版本）、折旧与摊销记录、资产处置 | company_asset(_version)、asset_depreciation_record、asset_disposal | accounting.fixedasset |

跨 bc 同名：「核销」（见上）；「账户」在 accounting.cashbank（资金账户）与词表 `account`（科目，accounting.ledger）用字相近，中文名分别写「资金账户」「科目」，不会同名。AGG-59 词表中文名是「报销单」，流程与聚合稿写「费用报销」；AGG-42 名称「分摊运行」与词表 `allocation_result`（分摊结果）基本重合，表名取 `allocation_results`。以上同名异义与近义交 one-glossary 用 `new_term_issue.py` 登记。

### 修后补记（用语无着落，closeout_check --qc 报）

范围：命令名、审批单据类型名、读取步骤暂名。使用范围均为领域；中文名同 bc 内已查重（词表无）；跨 bc 同名的另行互列。

| 用语 | 所属 BC | 来源 | 处理 |
|---|---|---|---|
| 供方协议终止审批 | procurement.subcontracting | 审批单据类型暂名 | 交 one-glossary |
| 供方协议结算审批 | procurement.subcontracting | 审批单据类型暂名 | 交 one-glossary |
| 供方额度更新 | procurement.subcontracting | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 冲销并重过账 | operations.facts | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 决议利润分配 | payables.payment | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 减资或退伙决议 | payables.payment | 审批单据类型暂名 | 交 one-glossary |
| 分包变更审批 | procurement.subcontracting | 审批单据类型暂名 | 交 one-glossary |
| 制单手工凭证 | accounting.ledger | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 占用履约额度 | procurement.subcontracting | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 占用进项发票入账 | accounting.tax | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 发布授权表版本 | accounting.ledger | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 发布映射表版本 | accounting.ledger | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 发布科目表版本 | accounting.ledger | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 发起付款 | payables.payment | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 售前投入转费用 | cost.costing | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 复核手工凭证 | accounting.ledger | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 完成供方协议 | procurement.subcontracting | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 审批付款 | payables.payment | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 导入银行流水 | accounting.cashbank | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 建立履约额度 | procurement.subcontracting | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 批准分摊规则版本 | cost.costing | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 报表行定义评审生效 | accounting.reporting | 审批单据类型暂名 | 交 one-glossary |
| 授权表版本发布批准 | accounting.ledger | 审批单据类型暂名 | 交 one-glossary |
| 提交审批 | accounting.fixedasset、procurement.subcontracting | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 提交应付利润 | payables.payment | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 提交申报 | accounting.tax | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 提交费用报销 | expense.spend | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 提出分包变更 | procurement.subcontracting | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 映射表版本发布批准 | accounting.ledger | 审批单据类型暂名 | 交 one-glossary |
| 更正申报 | accounting.tax | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 查验进项发票 | accounting.tax | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 标记费用报销已付 | expense.spend | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 校验目标期间 | operations.facts | 读取步骤暂名 | 交 one-glossary |
| 核定费用报销 | expense.spend | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 核销应付 | payables.payment | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 生成分录 | accounting.ledger | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 生成快照 | accounting.ledger | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 生成申报 | accounting.tax | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 生效供方协议 | procurement.subcontracting | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 生效分包变更 | procurement.subcontracting | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 生效分摊规则版本 | cost.costing | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 生效分摊运行 | cost.costing | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 生效报表行定义版本 | accounting.reporting | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 登记付款失败 | payables.payment | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 登记公司资产 | accounting.fixedasset | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 登记出资退回 | payables.payment | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 登记利润分配支付 | payables.payment | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 登记缴款 | accounting.tax | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 登记进项发票 | accounting.tax | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 登记银行结果 | payables.payment | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 确认供方履约 | procurement.subcontracting | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 确认应付 | payables.payment | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 确认进项发票用途 | accounting.tax | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 科目表版本发布批准 | accounting.ledger | 审批单据类型暂名 | 交 one-glossary |
| 第 13 期启用批准 | operations.policy | 审批单据类型暂名 | 交 one-glossary |
| 纳税申报复核 | accounting.tax | 审批单据类型暂名 | 交 one-glossary |
| 终止供方协议 | procurement.subcontracting | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 终止清算成本 | cost.costing | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 结算供方协议 | procurement.subcontracting | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 标记付款已核销 | payables.payment | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 草拟利润分配 | payables.payment | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 计提折旧与摊销 | accounting.fixedasset | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 试算人工成本分配 | cost.costing | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 试算分摊运行 | cost.costing | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 读取供方准入资质与项目 | procurement.subcontracting | 读取步骤暂名 | 交 one-glossary |
| 读取供方协议 | accounting.fixedasset | 读取步骤暂名 | 交 one-glossary |
| 读取外部法定账余额 | accounting.ledger | 读取步骤暂名 | 交 one-glossary |
| 读取已关账年度与可分配利润 | payables.payment | 读取步骤暂名 | 交 one-glossary |
| 读取已过账事实 | cost.costing | 读取步骤暂名 | 交 one-glossary |
| 读取收款账户与资金账户 | payables.payment | 读取步骤暂名 | 交 one-glossary |
| 读取本期分录与余额 | accounting.ledger | 读取步骤暂名 | 交 one-glossary |
| 读取第 13 期启用登记 | operations.policy | 读取步骤暂名 | 交 one-glossary |
| 读取销项发票与分录余额 | accounting.tax | 读取步骤暂名 | 交 one-glossary |
| 读取项目与人员 | expense.spend | 读取步骤暂名 | 交 one-glossary |
| 读取项目状态 | procurement.subcontracting | 读取步骤暂名 | 交 one-glossary |
| 资产处置审批 | accounting.fixedasset | 审批单据类型暂名 | 交 one-glossary |
| 起草供方协议 | procurement.subcontracting | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 转入售前投入 | cost.costing | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 进项转出 | accounting.tax | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 释放履约额度 | procurement.subcontracting | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 释放核销占用 | payables.payment | 聚合稿命令名（词表未登记） | 交 one-glossary |
| 释放进项发票占用 | accounting.tax | 聚合稿命令名（词表未登记） | 交 one-glossary |

另：「已作废」（PRC-94 结局）、「暂估应付已确认」「已申报无需缴款」「已放弃申报」「已决议待支付」「申请未通过」「出资已认缴」「已放弃」「已驳回」属结局词，bc 随各流程；「应付股利」「完税凭证」「税费应付」为概念用语，交 one-glossary 判是否入词表。

### S-29、S-30 补记（新增命令与单据类型）

使用范围均为领域；中文名同 bc 内已查重（词表无）；交 one-glossary。聚合稿命令列待回补（见聚合稿回补第 26–37 行），骨架里以〔缺〕标注。

| 用语 | 所属 BC | 来源 | 处理 |
|---|---|---|---|
| 确认开立资金账户、确认销户资金账户、解冻资金账户 | accounting.cashbank | S-29 SQ-42 命令 | 交 one-glossary |
| 资金账户开立确认、资金账户销户确认、资金账户解冻确认 | accounting.cashbank | 审批单据类型（aggregate 引擎） | 交 one-glossary |
| 冲销供方履约确认、供方履约冲销审批 | procurement.subcontracting | S-29 SQ-43 命令与审批单据类型 | 交 one-glossary |
| 冲销费用报销、费用报销冲销审批、报销冲销审批 | expense.spend | S-29 SQ-45 命令与审批单据类型（后两者为同一单据的两个暂名，取前者） | 交 one-glossary |
| 作废手工凭证草稿 | accounting.ledger | S-29 SQ-46 命令 | 交 one-glossary |
| 撤销利润分配决议、利润分配决议撤销 | payables.payment | S-29 SQ-48 命令与审批单据类型 | 交 one-glossary |
| 人工处置核销差异、冲回应付核销、登记退票 | payables.payment | S-29 SQ-49、S-30 ③ 命令 | 交 one-glossary |
| 更正出资实缴 | payables.payment | S-29 SQ-47 命令 | 交 one-glossary |
| 开立会计年度 | accounting.period | S-30 ② 命令 | 交 one-glossary |
| 登记外部记账确认 | accounting.ledger | S-30 ② 命令（原已在上表，现入骨架 PRC-93 步 3） | 交 one-glossary |
| 标记成本结转已过账 | cost.costing | S-30 ① 命令（N7 定稿后与薪酬「标记薪酬已过账」分名） | 交 one-glossary |
| 占用预算 | operations.planning（B-18，他组 AGG-80） | N7 定稿（NM6-07#7，原「预算占用」），本组 PRC-70、87 经 api 调用 | 交 one-glossary |
| 待申报、已申报 | accounting.tax | AGG-90 法定申报（OBJ-92）状态，取代旧称「已生成」「已提交」（transition-review CF-033；one-glossary 暂定通过，待用户认可） | 交 one-glossary |
| 登记进项发票入账（RegisterInputInvoicePosting） | accounting.tax | AGG-89 提议命令：占用方过账后登记进项发票在税务平台的入账状态（CF-032；one-glossary 暂定通过，动词「登记」按 V001） | 交 one-glossary |
| 登记协议已生效（RegisterAgreementEffective） | cost.costing | AGG-122 命令，取代「登记合同已签回」（旧称列方言）；PRC-25 改订阅协议已生效（GL5-01）；one-glossary 暂定通过 | 交 one-glossary |
| 跨法人人工 | cost.costing | one-glossary PR #572 已登记提议；员工当日成本法人与项目承担法人不一致的人工与项目费用（LEB-04；LEGAL_ENTITY_COSTING C-2、C-3）；记员工所属法人并标「待内部结算」（词表已有） | 交 one-glossary |
| 跨法人清单 | cost.costing | one-glossary PR #572 已登记提议；财务每月收到的跨法人人工、项目费用与未转入售前投入清单（员工、项目、两边法人、金额、期间）（LEB-04；C-2、C-3、C-5） | 交 one-glossary |
| 登记代发退回（RegisterDisbursementReturn）、付款已部分退回（PaymentPartiallyReturned） | payables.payment | AGG-53 提议命令与事件：代发总额已付后部分收款人失败退回（PAYROLL_REISSUE_SEAM J-2） | 交 one-glossary |
| 登记薪酬补发明细（RegisterPayrollRedisbursementDetail）、补发明细（PayrollRedisbursementDetail） | payables.payment | AGG-52 提议命令：薪酬补发不新建应付，在原应付上登记补发明细（J-1，one-architecture 2026-10-22 裁定） | 交 one-glossary |
| 重提过账候选、登记拒收处置结论 | operations.facts | S-29 SQ-41 命令（PRC-158） | 交 one-glossary |
| 付款已退票、资金账户已解冻、外部法定账记账回执已到达 | 各 BC | 事件名；回执为 platform.integration 事件 | 交 one-glossary |
| 已冲销、已退票、已撤销、已作废、挂起、部分实缴、待确认开立、待确认销户、已生成 | 各 BC | 状态值；「已冲销」用于运行单据，「已取代」只用于版本化配置（S-30 ③） | 交 one-glossary |

### S-31 补记（命令、单据类型、聚合、事件、状态）

词表无，登记待 one-glossary；使用范围均为领域，bc 按聚合所在；领域内不出现「分贝通」，一律「差旅服务商」，分贝通只出现在集成适配器（ADR-0039）。

| 类别 | 用语 | bc | 处理 |
|---|---|---|---|
| 命令（聚合稿草案名） | 登记其他应付、登记预付款、预付款冲抵应付、冲回成本结转、登记协议已生效、登记项目已建立、登记售前投入挂接归属、标记售前投入已转入、标记售前投入已转费用、发布成本核算参数版本、发布资产类别与折旧政策版本、发布口径版本、签字确认期初余额、发布现金流量项目表版本、起草现金流量项目表版本、提交费用事前申请、批准费用事前申请、驳回费用事前申请、撤回费用事前申请、关闭费用事前申请、登记差旅订单与月账单、归属差旅订单、核对差旅月账单、确认差旅账单应付、登记超标订单待审批、批准差旅超标订单、驳回差旅超标订单、提交出差申请、批准出差申请、驳回出差申请、撤回出差申请、登记出差申请已同步、发布出差补贴标准版本 | cost.costing、payables.payment、accounting.ledger、accounting.fixedasset、operations.policy、expense.spend | 交 one-glossary |
| 命令（本稿提议） | 冲销应付利润、处理账单差异、登记进项发票入账、编制口径版本、编制资产类别与折旧政策版本、标记费用事前申请已用尽 | payables.payment、expense.spend、operations.policy、accounting.fixedasset | 交 one-glossary；聚合稿待回补 |
| 命令（平台与读取步骤用名） | 读取归属对象与人员、读取往来方与归属对象、提交直接付款申请 | expense.spend、payables.payment | 交 one-glossary |
| 命令（作废与冲销） | 冲销人工成本分配运行、冲销分摊运行、冲销期末结转运行、作废人工成本分配运行 | cost.costing、accounting.ledger | 交 one-glossary |
| 审批单据类型 | 费用事前申请审批、出差申请审批、差旅超标订单审批、直接付款审批、成本核算参数版本发布确认、资产类别与折旧政策版本发布确认、出差补贴标准版本发布确认、现金流量项目表发布、期初余额签字确认、人工成本分配运行冲销审批、分摊运行冲销审批 | 各 BC | aggregate 引擎的发布确认与签字三项不走审批平台；其余走 platform.approval |
| 聚合与表 | 售前投入归属登记、成本核算参数版本、资产类别与折旧政策版本、期初余额、勾稽差异案、口径转换规则版本、期末结转模板版本、现金流量项目表版本、费用事前申请、差旅订单月账单、出差申请、出差补贴标准版本；口径版本（词表已批准） | 见聚合稿 | 表名见 data-model；词表已有：勾稽差异案、口径版本、其他应付 |
| 事件 | 口径版本已生效、成本核算参数版本已生效、资产类别与折旧政策版本已生效、期初余额已生效、勾稽差异案已关闭、期末结转模板版本已生效、现金流量项目表版本已生效、费用事前申请已批准、差旅月账单已核对、出差申请已批准、出差补贴标准版本已生效；集成事件：差旅订单已推送、差旅月账单已推送、超标订单待审批 | 各 BC | 交 one-glossary |
| 状态值与结局词 | 已撤销、已用尽、已冲抵、部分冲抵、预付款已登记、已解释、已修正、已豁免、未关闭、编制中、期间关账中待重试、等待发票、差额进下期运行 | 各 BC | 交 one-glossary；「口径转换」与「口径版本」同用「口径」，请判是否同名异义 |

（用语检查清单，逐字：人工成本分配运行冲销审批、关闭费用事前申请、冲回成本结转、冲销人工成本分配运行、冲销应付利润、出差申请审批、出差补贴标准版本发布确认、分摊运行冲销审批、发布出差补贴标准版本、发布成本核算参数版本、发布现金流量项目表版本、发布资产类别与折旧政策版本、处理账单差异、差旅超标订单审批、归属差旅订单、成本核算参数版本发布确认、批准出差申请、批准差旅超标订单、批准费用事前申请、提交出差申请、提交直接付款申请、提交费用事前申请、撤回出差申请、撤回费用事前申请、期初余额签字确认、标记售前投入已转入、标记售前投入已转费用、核对差旅月账单、现金流量项目表发布、登记其他应付、登记出差申请已同步、登记协议已生效、登记售前投入挂接归属、登记差旅订单与月账单、登记超标订单待审批、登记项目已建立、登记预付款、直接付款审批、确认差旅账单应付、签字确认期初余额、读取归属对象与人员、读取往来方与归属对象、费用事前申请审批、资产类别与折旧政策版本发布确认、起草现金流量项目表版本、驳回出差申请、驳回差旅超标订单、驳回费用事前申请）

## 格式问题

「已裁定」表示 one-architecture 已采纳（CONVENTIONS 第 2、5 节，S-12、S-15），不再提问；其余请裁定后写进 CONVENTIONS。

| # | 提案或做法 | 状态 |
|---|---|---|
| F-01 | `decision[].outcome`：出口直接结束时的结局词（一个步骤可有多个结局，步骤级 `outcome` 只用于无 decision 的最后一步） | 已裁定（第 5 节） |
| F-02 | 事件策略可写多步 `steps`；单聚合维护写 `states`、`transitions`；读模型写 `query`、`dimensions`、`permission`、`page`；`pending: <题号>` 标注待用户定事项影响的步骤 | 已裁定（第 5 节） |
| F-03 | 流程级 `segregation`、表级 `pi`、步骤 `added_in: closeout`；`approval.engine`（platform.approval / aggregate / external）；`approval.why_human`；步骤 `withdraw: {to, outcome}` | 已裁定（S-12、S-15、第 5 节） |
| F-04 | 事件策略 `triggers` 条目写法：`{type, name, bc}`（PRC-15 等）与 `{type, event, publisher_bc}`（PRC-93、103）并存；字段按任务单自拟，CONVENTIONS 未规定 | 待裁定：请统一 |
| F-05 | 单聚合维护 `commands` 里加 `where`、`evidence`、`from`、`to`（外部与线下证据、状态转换可核对）；状态机可多个，每个有 `values` 与 `transitions`，转换带 `by`、`guard`；读模型加 `subscribes`、`notes` | 待裁定（`states`、`transitions` 已裁定，其余字段请裁） |
| F-06 | `decision` 增加 `alert: true`：标记「拒收并告警」类分支（CLAUDE.md 第 6 条）。原期间闸门分支在 `when` 里写「拒收并告警」造成重复，本稿期间闸门路由分支已不再写拒收，`alert` 只留在真正拒收的分支 | 待裁定：保留 `alert` 字段 |
| F-07 | `decision.rule` 允许多个引用，用「、」或「；」分隔（如 `INV-04、AR-08`；`ADR-0045 决定 2；INV-92；AR-05`）；`guard: rule` 的 AR 规则写 `rule: AR-xx` | 待裁定 |
| F-08 | `timeout` 增加 `relative_to`（法定截止日这类相对期限，PRC-77）并允许 `pending` 引用 T-01；`timeout.after` 可取「配置：财务设定的关账提醒日」（非 ISO 时长，ADR-0046 第 5 项第 5 条只定提醒、不设强制期限）。取值 `n 个工作日（n 待定，按审批节点属性）` 同 PRC-87 | 待裁定 |
| F-09 | offline 步的操作人与登记人不同时（PRC-112 步 3 股东会决议由 SH-O1 形成、SH-E6 登记）：增加 `recorded_by`，`actor` 写决议人 | 待裁定 |
| F-10 | `roles` 的 `as: external` 只能写 SH- 编号，U8C、银行等外部系统没有位置；本稿用 `where: external:U8C` 表达。是否允许 `{id: external:U8C, as: external}` | 待裁定 |
| F-11 | `segregation.roles` 的几种写法：同一角色重复加 `as` 对位（`[SH-E6, SH-E6]` + `[performer, reviewer]`，表示同角色异人）；`[system]`（说明记账由系统完成）；多角色多 `as`（PRC-70、75 审批层级）。CONVENTIONS 对同一角色两个人的情形没写 | 待裁定 |
| F-12 | 独立入口：步骤加 `entry`（入口名），`triggers` 每条加 `entry`（入口步序号），对应 BPMN 多个开始事件（PRC-92 入口 1、3；PRC-99 四个入口；PRC-100 入口 1、3、5）；也可拆成多个流程 | 待裁定 |
| F-13 | 新增 `checks`：列月结前置检查清单（ADR-0022 决定 10），PRC-101 步 1、PRC-102 步 4 | 待裁定 |
| F-14 | `approval.on_reject_outcome`：驳回直接结束时的结局词（PRC-97）；`approval.approver_basis`：approver 不取 `cost_object_owner`（关账确认、对账签字、股东决议，聚合命令）时说明为什么不是责任人 | 待裁定 |
| F-15 | `approval.escalation` 与 `escalation_rules` 写法不一 | **已按 CONVENTIONS 第 2 节统一为 `escalation_rules`**（不再提问） |
| F-16 | 审批回调写在审批步骤还是下一步 | **已按 CONVENTIONS 第 5 节改到下一步**（通过 → 审批后一步，驳回 → on_reject 去向步；PRC-97 驳回即结束，无驳回回调步）（不再提问） |
| F-17 | 期间闸门写法（ADR-0045 决定 2、INV-92、AR-05）：系统自动触账的步骤写路由分支（`when` 为所属期间已关账，`rule: ADR-0045 决定 2；INV-92；AR-05`，结局与 otherwise 相同，照样板）；路由会遮蔽后续分支时（PRC-93、98）写进 `desc`；人工发起的写「规则提示…不设审批」；过账管道拒收单列分支（无开放期间、借贷不平、法人不一致），结局「过账被拒收」。CONVENTIONS 第 2 节「拒绝分支」措辞对新单据不适用 | 待裁定：请改第 2 节措辞 |
| F-18 | `goto` 指向另一流程（PRC-93 → PRC-135，PRC-102 → PRC-101）时不写结局词，也不进本流程 `outcomes` | 待裁定 |
| F-19 | 空角色与同角色：无人工角色的流程（PRC-92）`roles` 写 SH-E6 informed（接收拒收告警），是否允许 `roles: []`；步骤有 `command` 无 `aggregate`（月结检查，B-17 无聚合），〔缺〕前缀写在 `command` 里、`aggregate` 照填所属聚合 | 待裁定 |
| F-20 | 角色映射：员工 = SH-E3（SH-E3 是设计师和顾问，借用，没有「全体员工」类别）；财务 / 出纳 / 财务主管 = SH-E6（细分只能丢掉，靠 `platform.identity` 按人员校验）；项目经理 / 验收人 = SH-E2；部门负责人 = SH-E1；财务负责人 = SH-M1；股东 = SH-O1。同一编号靠 `as` 区分职务 | 待裁定：是否需要 SH- 下加子类 |
| F-21 | `pending` 不在约定里，保留，取值为「待用户定」的编号；新增编号 P2-01 至 P2-03，合并确认页时统一编号；原 ADR22-G1G2 一律换成 S-05 | 待裁定（pending 字段已在第 5 节采纳，编号体系待统一） |
| F-22 | v1 的 `trigger`、`gate`、`notes`、`calls`、`platform_task`、`post` 等字段原样保留；新增 `type`（business / event_policy / single_aggregate / read_model）便于合并；`decision.goto` 数字为本流程步骤序号（1 起），含系统补偿步 | 待裁定 |
| F-23 | 数据模型：新增 `source`（每张 `history_migration: full` 的表写迁入来源系统，R-09）；`notes`、`reason` 沿用，`data_category` 已删（R-23）；`history_migration` 只用 full / open_items / none，`sensitivity` 不再用 tbd，`pi` 有 tbd 三处（`labor_cost_allocation_lines`、`business_facts`、`supplier_agreements`，均写了理由）；锚点已全部展开 | 待裁定：请写进 CONVENTIONS 第 3 节 |
| F-24 | 个人信息判定口径：业务对象的人员引用（报销人、付款对象、责任人、辅助维度人员、签字人）计 `pi`；操作类人员（作者、确认人、制单、复核、批准、执行）作审计列，不单独计；对方账号为自然人账号的计 `spi` | 待 one-data 复核 |
| F-25 | 数据模型引用写法：同聚合内引用写表名，跨聚合一律 `to: AGG-xx`（R-24）；同 BC 跨聚合是否建外键（§14.1 D2）与 CONVENTIONS 写法不一致；聚合内无父行的技术表（`posting_rejections`、`post_sequence_counters`、`journal_entry_pending_items`）写 `role: child` 且不写本聚合内引用，沿用 AGG-77 样板；聚合稿「不计」的 AGG-78 撤出，聚合总数计 32（含「不计」的 AGG-83、占位的 AGG-125） | 待裁定 |
| F-26 | 配置类版本草稿存放位置（R-31）：版本行在确认时才写入，草稿另存（审批载荷或同聚合技术表） | 待 one-data 定 |
| F-27 | 迁入口径 `migration`、权限码 `permission`、页面 `page` 均为暂名，汇总交 one-glossary；`decision.when` 里有少量带规则引用的业务条件长句，若要求短句请定拆法 | 待裁定 |
| F-28 | 生成器 `bpmn_gen.py` 不读 `decision[].outcome`：结束事件名「按位置猜测」警告（PRC-76、99） | 暂不能查（生成器，交 one-architecture） |
| F-29 | 生成器不画 `approval.on_reject`、`withdraw`：驳回与撤回出口在图上缺，R13 不会报；骨架已写全 | 暂不能查（生成器） |
| F-30 | 生成器多入口只接第一个：PRC-92（入口 3）、PRC-99（入口 3、4、5）报 R13；本轮修后新增的多入口流程 PRC-76（人工终止入口 4）、PRC-112（支付再入入口 6）同样受影响，不拆流程（用户已要求不拆 PRC-64、92、99）；共 29 个 R13，均来自多入口 | 暂不能查（生成器），等 one-architecture |
| F-31 | `closeout_check --qc` 只扫 `steps`，单聚合维护（PRC-65、111、135）的 `commands` 里的 `approval`、`decision` 不被检查；本稿已在 `commands` 里写了 aggregate 审批（PRC-65）与出口（PRC-65、111），请脚本扩展到 `commands` | 待裁定 |
| F-32 | 读模型 PRC-90、106 与单聚合维护 PRC-65、111 的场景为手写（按状态转换与查询分支），不是出口覆盖；PRC-135 沿用原场景 | 记录 |
| F-33 | `approval.engine: external` 的审批写 `approver: SH-O1` 与 `approver_basis`（PRC-114 减资或退伙决议），不登权责表 A，步骤 `evidence` 必填 | 记录（按 CONVENTIONS 第 5 节） |
| F-34 | `timeout` 保持 `interrupting: false`（S-28）；审批节点超时 P3D 暂用 T-01 占位并引用 RULE-377 至 382，取值待 U-23 | 记录 |

### S-29、S-30 后补记

| # | 问题 | 处理 |
|---|---|---|
| 1 | 单聚合维护的 `commands` 字段按 CONVENTIONS 第 5 节改名 `transitions`（from 可为列表，含 approval 的照写）；converge_check、object_check 仍读 `commands` 统计命令使用 | 目录对相关命令登记 `unused_ok`；S-34 ① 脚本已改为同时读 `commands` 与 `transitions`，11 条 `unused_ok` 已撤回 |
| 2 | 单聚合维护的 `transitions[].to` 写单一状态，多落点（如实缴分期落部分实缴或实缴（到账））在状态机文件里表达 | 状态机文件为准 |
| 3 | `cross_bc` 项加了 `subscriber_policy`（PRC-77 申报已提交 → PRC-159） | 提议；未采纳则删，对象仍可由事件目录推出 |
| 4 | 单聚合维护 PRC-158 的顶层 `timeout`（超期告警）：脚本只扫步骤超时，不扫此处 | 提议；场景已覆盖 |
| 5 | 已裁定但聚合稿未回补的命令仍写〔缺〕前缀并在目录标 `proposed: true`，回补后统一去掉 | 聚合稿回补第 26–37 行 |

### S-31 后补记

| # | 提案或做法 | 状态 |
|---|---|---|
| 6 | 单聚合维护 PRC-165–169、174、176 与既有 PRC-111 一样写 `transitions`（from 为列表，创建为 `[]`；多落点用 `also_to`），states 写名称列表；converge_check 已读 commands[] 与 transitions[]（S-34 ①），目录里 11 条 `unused_ok` 已撤回 | 提议；沿 CONVENTIONS 第 5 节 |
| 7 | 事件策略 `triggers` 单一触发时只留一条（PRC-163，S-34 ③）；入口多个的业务流程在 `triggers[].entry` 写入口步骤（PRC-170、171 沿 PRC-112） | 沿用 |
| 8 | 申请类流程的驳回、撤回各为独立命令步骤：审批步 `decision` 的「审批已驳回」转驳回步，`withdraw.to` 写撤回步序号（PRC-170、172） | 提议 |
| 9 | 新增结局词「期间关账中待重试」：触账步骤的分支，返回「期间关账中、可重试」，来源 BC 投递重试，不进拒收记录（INV-92 新写法）；显式指定期间的通道不用此结局，直接拒绝 | 提议 |
| 10 | 冲销分支模板（PRC-96、98、95）：冲销审批（platform.approval，AR-06）→ 冲销命令 → 冲销并重过账（PRC-92 更正入口）；所属期间已关账的不再冲原运行，结局「差额进下期运行」 | 沿 PRC-87 冲销分支 |
| 11 | 权责表 A 的 role 只写 SH- 编号，职能写 `function`（S-36 ①）；A 为实例级写 `field: 费用归属对象.责任人` | 已裁定（S-36） |
| 12 | `pending` 编号：S31-结构-N 对应上面结构问题；SQ-29-豁免清单、SQ-29-外部代发对应 U-51、U-52 | 提议 |
| 13 | 命令前缀「〔缺〕」直接写进 `command` 字段，便于脚本检出；聚合稿回补后统一去掉 | 沿用 |
| 14 | 条目级 `added_in: closeout` 与 `basis: S-31 SQ-NN` 供追溯 | 沿用 |

## 待用户定

交 one-architecture 或 one-data 的事项不在此表（见结构问题与「交 one-data 复核」）。新原则落地后，F-01（审批人与分级）缩窄，见 U-17。

| 编号 | 问题 | 影响 | 出处 |
|---|---|---|---|
| U-01 | P-001 第 13 期与年度结转能否由系统按模板生成 | PRC-94 步 4、PRC-95 步 2、PRC-102 步 4、6 | 今日口径 |
| U-02 | P-002 12 月与第 13 期结转怎么出分录、是否设「以前年度损益调整」科目 | PRC-95 步 2、PRC-102 步 4、6 | 今日口径 |
| U-03 | P-003 第 13 期启用年度；登记人与财务批准人是谁 | PRC-94 步 1、PRC-102 步 1、2 | 今日口径 |
| U-04 | P-004 / P-005 切换当年上年年审调整如何入账、U8C 只读时点 | PRC-94 步 4、PRC-102 步 4、6、PRC-104 | 今日口径 |
| U-05 | ADR46-P3 第 13 期接收范围：是否允许模板期末凭证 | PRC-94 步 1、PRC-95 步 2、PRC-102 步 3 | ADR-0046 移交 P-3 |
| U-06 | A1 / Q-04 收入确认时点（按时点还是时段）；影响 PRC-55 转出金额配对（pending [A1, Q-04, Q20]） | PRC-55 | 聚合稿 §0.3 |
| U-07 | Q20 供方履约确认形态与成本结转（AGG-57、INV-88；DM-3） | PRC-72 步 3、4、PRC-55 | 聚合稿 |
| U-08 | R-9-差异 对外按开票与权责按客户确认两账差异怎么处理（DM-10） | PRC-105 步 1、PRC-106 | 今日口径 |
| U-09 | ADR22-差异类 应收与销项在收付账簿是否按纳税义务时点记账（差异类名单；DM-6） | PRC-77 步 1、PRC-100 步 4 | ADR-0022 |
| U-10 | ADR22-签字容差 对账签字人与容差 | PRC-104 步 3 | ADR-0022 决定 8 |
| U-11 | A1-02 审计把关方式改写（内部审计与财务负责人）；年度审计调整类凭证是否还存在，谁提出、谁把关 | PRC-94 步 1 | 今日口径、S-06 |
| U-12 | FD-09 人工成本分配口径（DM-4） | PRC-96 步 2、3 | 聚合稿 AGG-41 |
| U-13 | Q10 未结算即结项的结算结论 | PRC-76 步 3 | 聚合稿 |
| U-14 | G-11-出资退回 出资退回的应付类型承载 | PRC-114 | G-11 |
| U-15 | R-27 退回出资要不要决议或审批环节（减资、退伙通常需合伙人决议）；本稿没有加节点（流程与步骤标 `pending: [R-27]`） | PRC-114 | REVIEW R-27 |
| U-16 | 往来款「争议」处置分类取值（只确认诉讼；DM-5） | AGG-52 状态，无流程步 | 聚合稿 AGG-52 |
| U-17 | F-01（缩窄）关账确认、年末关账确认、对账签字、报表行定义评审、手工凭证审批、分摊规则批准的人员级别：本稿按职责角色财务负责人（SH-M1 占位）写，是否分级 | PRC-94 步 3、PRC-97 步 2、PRC-101 步 3、PRC-102 步 6、PRC-104 步 3、PRC-105 步 2 | v1、评审 R-01 |
| U-18 | F-02 可分配利润取哪个账簿的快照（收付或权责） | PRC-112 步 1 | INV-117 |
| U-19 | F-03（新）无单一归属对象的付款（利润分配、税费、薪酬来源的应付）审批人如何取：本稿暂取承担法人的财务部门责任人 | PRC-75 步 4 | 新原则落地 |
| U-20 | F-Q1-阈值（新汇总）AR-01、AR-02 的金额阈值：供方协议、付款、手工凭证分别给（报销阈值同 PRC-87） | PRC-70 步 4、PRC-75 步 4、PRC-94 步 3 | APPROVAL_RULES、intent 163556 |
| U-21 | AR-11 责任人就是提交人本人时的处理（上级代批 / 按异常加审 / 只事后抽查）；PRC-70 的项目经理起草自己项目的协议是典型 | PRC-70 步 4、PRC-75 步 4、PRC-94 步 3、PRC-97 步 2 | APPROVAL_RULES |
| U-22 | AR-12 责任人缺位的处理（上级代批 / 指定代理人 / 退回补登责任人） | 同上 | APPROVAL_RULES |
| U-23 | T-01 各超时期限取值与升级对象：银行文件每日应到未到、付款已审批未登记银行结果（暂 3 日）、申报距截止日提醒（暂前 3 日） | PRC-64、75、77 | 本批提议 |
| U-24 | P1-Q1 履约验收人由哪类角色担任（项目经理 SH-E2 还是设计师和顾问 SH-E3 参与），「专人」是否要求不得是协议经办人（本稿按不得同人写） | PRC-72 | INV-83 |
| U-25 | P1-Q2 无票暂估应付：是否允许、取得发票后如何重入占用 | PRC-74 步 1 | PRC-73 notes |
| U-26 | P2-01 月度关账有无提醒日、提醒谁（年度关账只定了提醒、不设强制期限） | PRC-101 步 3 | ADR-0046 第 5 项第 5 条 |
| U-27 | P2-02 补授权表或映射表后，新版本能否以当前开放期间首日生效（否则待处理队列本期清不空；见 SQ-13） | PRC-93 步 1、PRC-100 步 4、6、PRC-135 | ADR-0022 决定 2、10 |
| U-28 | P2-03 对账与签字有无期限（收付口径报表须等对账，INV-107 已定） | PRC-104 步 3 | INV-107 |
| U-29 | T-4 IT 资产采购是否属非项目采购、是否经供方协议入账 | PRC-99 步 1、2 | 财务 |
| U-30 | P3-Q1 资产处置（出售、报废、转让）是否需审批、金额门槛；按新原则建议资产归属部门责任人一级、超阈值按 AR-01、AR-02 加审；建卡与处置经办人是否分离（骨架暂按分离写） | PRC-99 步 4 与 segregation | 本批提议 |
| U-31 | P3-Q2 资本化门槛（单价低于多少不建卡、直接费用化）与折旧政策（含 IT 资产年限、残值率） | PRC-99 步 2、3 | 本批提议 |
| U-32 | DM-9 保留期（凭证、账簿 30 年之外的各类：凭证、分录、快照、事实、银行流水、申报） | 数据模型 | D12 |
| U-33 | DM-11 来源不在老系统与 A8 的历史（法定凭证明细、银行流水、申报与缴款、合伙人出资、利润分配）是否从 U8C、银行、税务端取回迁入；不取回则以期初余额表承载余额，历史查阅回老库只读归档（U-9）或 U8C | journal_*、bank_statement_*、tax_*、partner_contribution*、profit_distribution* | U-3、U-4、R-09 |
| U-34 | G-F1 费用事前申请是否扩到所有费用类别（现只覆盖业务招待额度） | SQ-26 | EXPENSE_COVERAGE |
| U-35 | G-F7 出差补贴标准（按差旅类型与天数）取值与由谁定 | PRC-87（补贴行）、SQ-28 | EXPENSE_COVERAGE |
| U-36 | FX-1 费用类别清单与「费用类别 → 科目」对照表首版由谁编、谁批（建议财务编制、财务负责人批，编制 ≠ 批准） | PRC-87 → 总账映射 | ANSWERS F-09 |
| U-37 | FX-2 销售费用、管理费用、研发支出按什么分：报销人所在部门性质，还是报销单上选的归属 | 映射表维度 | ANSWERS F-09 |
| U-38 | FX-3 员工报销应付逐人逐单挂账，还是沿用 U8C 月末汇总支付（GL-018）；建议逐人逐单 | PRC-87、PRC-75 | ANSWERS F-09 |
| U-39 | 分贝通现行做法三题（事实题，问财务）：怎么付分贝通账单（预充值 / 月结，走 A8 哪个表单）；员工会不会把分贝通订单再报进差旅报销单（防重复）；「待打款」之后线下付款记账走哪里 | SQ-27 | EXPENSE_COVERAGE 第 4 节 |
| U-40 | 项目采购的协议起草人与审批责任人同为项目经理（PRC-70、71、76）：拆角色（需求由项目经理提、协议由采购经办或职能起草，项目经理只批），还是把「上级代批」定为常态规则（关联 U-21） | PRC-70 步 3、4；PRC-71；PRC-76 | QC-VS05-05 |
| U-41 | 税款缴纳是否经 PRC-75（本稿已按「税费应付 → PRC-75」写，SQ-44）；如否，改为本流程内加责任人审批并分离制表、提交、缴款登记 | PRC-77 步 3、4 | QC-VS06-01 |
| U-42 | 核定费用报销是否保留财务人工核定（权责表说明三.4）；本稿已为核定加退回出口与超时，若改系统校验则步 6 改自动 | PRC-87 步 6 | QC-VS08-02 |
| U-43 | 向财务确认（行业通行）：报销时限、差旅与招待标准超标检查、外币与海外差旅汇率、代提交、员工收款账户来源 | PRC-87 | QC-VS08-11 |
| U-44 | 向财务与法务确认：提取法定盈余公积的分录落点（P-001、P-002）、减资公告程序、利润分配决议撤销或修改、认缴期限与逾期；登记出资认缴与实缴是否须财务负责人复核 | PRC-111、112、114 | QC-VS12-05、06 |
| U-45 | 无票凭证的类型与规则：补贴行、行程单、收据等哪些可不占用进项发票（VS-08.2 退出条件「有票时」） | PRC-87 步 2 | QC-VS08-04 |
| U-46 | SQ-52 范围题：银行流水匹配与银行余额调节（未达账项）、预提与冲回进本期（推荐进）；期末汇兑重估、固定资产盘点与减值是否进本期，按是否有外币业务与固定资产规模定。骨架不改，前两项进则新给流程号与聚合归属，PRC-101、102 前置检查已留「银行余额调节已完成」项 | PRC-101 步 1、PRC-102 步 4 | S-29 SQ-52 |
| U-47 | 冲销已核定的费用报销与供方履约确认后的联动：预算占用、借款冲抵、应付、发票占用、履约额度是否同时冲回；已付或应付已有核销的是否一律拒绝（本稿按拒绝写，须先走付款退票） | PRC-87 步 10–12、PRC-72 步 6–9 | S-29 SQ-43、45 |
| U-48 | 撤销利润分配决议后已有支付的如何处理（本稿按「已有支付不可撤销」拒收；已确认应付股利与已过账事实的冲销已定，S-34 ②） | PRC-112 步 7 | S-29 SQ-48 |
| U-49 | 付款已退票后的下游回退：应付由已结清回到未结，报销「已付」、薪酬已发放是否随之回退（本稿只冲回应付核销与过账） | PRC-75 步 7–9 | S-30 ③ |
| U-50 | 过账拒收记录超期阈值（暂 P3D，T-01）；拒收记录作 AGG-77 内部技术表、不另立聚合是否接受 | PRC-158 | S-29 SQ-41 |

共 44 条（U-46–U-50 为 S-29、S-30 后新增）。已定、不再问：S-29（SQ-41–51、53、54）与 S-30 三项已裁定，见结构问题；INV-102 只预警（F-Q2）；Q-10 方向（S-16、S-17、S-19、S-24）；ADR22-G1G2（S-05，交过渡 ADR）；AGG§7-15（改交架构，SQ-12）；G-10 利润分配支付触发（改交架构，SQ-02）；DM-2（按 R-08 处理）；DM-8（事实是否逐人存放，改交 one-data）；N 默认 6 个月可设（A7，GL-026）；一次投产 2028-01-01；全部历史迁入；报销审批分级见 PRC-87。

### S-31、S-34 后新增

| 编号 | 问题 | 影响 | 出处 |
|---|---|---|---|
| U-51 | 哪些付款类别可以不引用合同或来源单据直接申请付款（豁免清单），清单外一律拒绝（候选：E8 公共、E10 财务、E11 特殊款项；金额门槛随阈值取证） | PRC-175 步 1；one-rules 写回 RULE-407 | S-31 SQ-29；S-34 ④ |
| U-52 | 「外部代发」是否为付给个人的劳务报酬（事实题）：是则须经 B-15 代扣个税，不能走其他应付 | PRC-175 步 1 | S-31 SQ-29 |
| U-53 | 出纳是否单列角色（会计法：出纳不得兼管稽核、档案和收支、债权债务账目登记）；推荐单列并在 platform.identity 设不相容权限组，属 J 系列岗位体系 | PRC-75、175、176 泳道与 segregation | S-31 SQ-09 |
| U-54 | 取证项（把握低）：分贝通付款方式（U-39）、A8「信息服务费备案申请」字段清单（SQ-30）、超标审批回写与同步时限（占位 PT12H、PT1H） | PRC-70、171–173 | S-31 SQ-27、28、30 |
| U-55 | 进项发票入账状态由谁向税务平台报送（FORCITIS one 直连、U8C 记账后回报或发票平台），时点是否为占用方过账后 | PRC-74、87（AGG-89 登记进项发票入账） | transition-review CF-032 |
| — | 投产日在用对象初始化（资产卡片含累计折旧、资金账户、合伙人认缴与实缴、未付利润分配）来源：财务清单或 U8C 导出；碰 U8C 生产数据先问用户（U-33 的前提） | PRC-166、167 migration | S-31 SQ-25 |

U-48 缩窄（S-34 ②）：已过账的应付股利经冲销过账更正，已定；剩余待问「已有支付的如何处理」（本稿按「已有支付不可撤销」拒收）。已写进骨架并标 `pending`、不替用户定：U-14、U-15（SQ-05 ①）、U-17、U-19、U-25、U-27、U-29、U-31、U-33、U-34、U-35、U-39、A1-02、S-05、Q20、T-01、AR-01、AR-02 阈值。

共 49 条（U-01–U-50 扣去已合并，另 U-51–U-55 为本轮新增）。

## 交 one-data 复核

1. 所有 `source` 取值按 v1 与聚合稿推断，须经 ADR-0033 来源对照与取证核实；尤其是资金账户（A8 付款账户加财务清单补录）、供方协议（老系统分包与 A8 采购、租赁等表单）、进项发票（A8 报销所附与老系统分包发票）、供方履约确认。
2. 公司资产卡片、资金账户、合伙人出资台账的投产日初始化来源（初始化建档不等于历史迁入），与 SQ-21、SQ-25 合并定。
3. DM-8：`business_facts`、`labor_cost_allocation_lines` 的 pi 判定。
4. 配置版本（科目表、映射表、授权表、报表行定义、分摊规则、第 13 期启用登记）仍暂记 `append_only`，草稿存放见格式问题 F-26。
5. `ledger_account_balances` 是否独立表；`post_sequence_counters` 高频更新行方案（D18 第 10 条）；`business_facts`、`journal_entries` 分区键（§2.4a 第 8 条）。
6. `accounting_periods`、`ledgers` 的 D17 豁免判定；`payable_application_holds`、`manual_vouchers` 等状态可变的 `versioned: none` 表是否都符合 D17。
7. 个人信息口径（格式问题 F-24）与 `cash_payments` 的 sensitive 判定。
8. 待裁定的结构问题中涉及表归属的：SQ-19（AGG-91 折旧政策表归属）、SQ-18（N 的配置载体）、SQ-22–SQ-24。

9. S-31 表级片段 13 个聚合、27 张表已并入（AGG-125 的四张表以片段为准，新增 opening_balance_project_splits；原 sets 状态改「草稿 / 已生效」）：AGG-130、132、131、122 的 history_migration（业务招待申请近 180 天 504 张、出差申请、未结月账单、未转出售前投入）与 pi、sensitivity 取值；AGG-122 receipts 是否逐人。
10. AGG-125 的 sets 用 versioned: none（每法人 × 账簿一个生效，部分唯一索引）而非 D15 配置型，请复核是否要 effective_dated；AGG-126 与勾稽族的案件粒度；配置型草稿存放位置（INV-131，沿 AGG-40）。
11. 投产日在用对象初始化（SQ-25）走 platform.migration 导入，来源财务清单或 U8C 导出；company_assets 等表 history_migration 现为 none，应标「初始化」而非「不迁」。
12. 引用列族已补：AGG-84 journal_entries → AGG-127、journal_entry_lines → AGG-129；AGG-99 → AGG-128；AGG-86 report_line_mappings → AGG-129；AGG-91 company_asset_versions → AGG-124；AGG-59 expense_claims → AGG-130、expense_claim_lines → AGG-132。
13. 表名用语：presales_investment_attribution、cost_accounting_parameter_version、asset_category_policy_version、opening_balance_set、basis_conversion_rule_version、period_end_carryover_template_version、cash_flow_item_table_version、expense_pre_application、travel_provider_monthly_statement、business_trip_application、travel_allowance_standard_version 待词表登记。
14. AGG-41 奖金与加班（ARC-1022-AFI-01，BONUS_OVERTIME_COSTING）：`labor_cost_allocation_lines` 加行类型与加班计价、奖金计提、奖金调整列族（按项目、不到人）；新增 `labor_cost_allocation_variances`（每运行每员工一行，pi、sensitive）；`labor_cost_allocation_runs` 加费率卡版本引用（AGG-137 表级模型尚未出）。one-data 2026-10-22 设计层通过附 F1–F6（claude/one-data-handoff-1 @00ab9f42 `data/reviews/ARC-1022-AFI-01-AGG41.md`），已写入表注；待办：差异表保留期须用户认可（D12，随下一张确认页交 one-architecture）；F5 外键形式随 S-5；DM-8 仍开；DDL 草案出来再送 one-data。

## 验收场景清单

场景已移到 `scenarios.finance.yaml`（按流程号分组，每条 id、path、outcome、covers、expect），共 662 条（S-31 后新增：PRC-163–176 共 125 条；既有流程加「期间关账中待重试」分支 28 条、冲销与作废分支、归属登记、记账确认等）。

| 流程 | 名称 | 场景数 |
|---|---|---|
| PRC-15 | 未签约的售前投入转费用 | 13 |
| PRC-25 | 转入售前投入 | 14 |
| PRC-35 | 项目或协议终止、结项后清算成本 | 10 |
| PRC-55 | 结转合同履约成本 | 11 |
| PRC-64 | 导入银行流水 | 10 |
| PRC-65 | 维护资金账户 | 17 |
| PRC-70 | 起草并生效供方协议 | 16 |
| PRC-71 | 生效分包变更 | 15 |
| PRC-72 | 确认供方履约 | 15 |
| PRC-73 | 登记并查验进项发票 | 13 |
| PRC-74 | 提交供方应付 | 11 |
| PRC-75 | 发起、审批并执行付款 | 32 |
| PRC-76 | 完成并结算供方协议 | 9 |
| PRC-77 | 申报并缴纳增值税、附加税与企业所得税 | 15 |
| PRC-87 | 提交、核定并确认报销应付 | 27 |
| PRC-90 | 出具经营月报并复盘 | 3 |
| PRC-92 | 过账业务事实 | 18 |
| PRC-93 | 生成会计分录 | 12 |
| PRC-94 | 制单并过账受控手工凭证 | 24 |
| PRC-95 | 期末结转 | 16 |
| PRC-96 | 分配人工成本 | 21 |
| PRC-97 | 批准并生效分摊规则版本 | 10 |
| PRC-98 | 分摊间接成本 | 16 |
| PRC-99 | 计提折旧并处置公司资产 | 18 |
| PRC-100 | 发布总账配置版本 | 18 |
| PRC-101 | 月结检查并关账 | 15 |
| PRC-102 | 启用第 13 期并年末关账 | 20 |
| PRC-103 | 生成期间余额快照 | 4 |
| PRC-104 | 外部法定账对账与签字 | 10 |
| PRC-105 | 评审并生效报表行定义版本 | 7 |
| PRC-106 | 出具三大报表 | 5 |
| PRC-111 | 认缴并实缴出资 | 10 |
| PRC-112 | 决议并支付利润分配 | 15 |
| PRC-114 | 退回出资 | 10 |
| PRC-131 | 付款成功后核销应付 | 8 |
| PRC-132 | 应付核销后置付款已核销并提交过账候选 | 5 |
| PRC-133 | 应付结清后报销置已付 | 6 |
| PRC-135 | 处理会计分录待处理队列与推送重投 | 2 |
| PRC-158 | 处置过账拒收记录 | 6 |
| PRC-159 | 申报已提交后确认税费应付 | 9 |
| PRC-163 | 应付结清后登记利润分配支付 | 8 |
| PRC-164 | 收入确认冲销后冲回已配对成本结转 | 9 |
| PRC-165 | 发布成本核算参数版本 | 6 |
| PRC-166 | 发布资产类别与折旧政策版本 | 6 |
| PRC-167 | 编制并签字确认期初余额 | 8 |
| PRC-168 | 发布口径版本 | 6 |
| PRC-169 | 处置勾稽差异案 | 10 |
| PRC-170 | 申请费用事前额度 | 13 |
| PRC-171 | 核对差旅服务商月账单并确认应付 | 19 |
| PRC-172 | 申请出差并同步差旅服务商 | 13 |
| PRC-173 | 审批差旅超标订单 | 13 |
| PRC-174 | 发布出差补贴标准版本 | 7 |
| PRC-175 | 直接付款申请 | 20 |
| PRC-176 | 预付款核销 | 8 |

## 评审意见处理

`REVIEW.md` 32 条（H 5、M 14、L 13）的最终状态。

| 编号 | 最终状态 | 说明 |
|---|---|---|
| R-01 | 已改 | 不相容职务落到全组：PRC-75（发起 ≠ 审批 ≠ 出纳，记账由系统自动）、94（制单 ≠ 复核 ≠ 审批）、97（编制 ≠ 批准）、101、102（发起关账 ≠ 确认关账）、104（登记差异 ≠ 签字）、105、100（起草 ≠ 批准）、PRC-87 样板；审批人不与执行人同为 SH-E6，按新原则改为责任人，加审为 SH-M1；职务分离判断点保留 |
| R-02 | 已改 | `journal_entries` 去掉「状态」列，待处理队列另建技术表；候选表样板已撤；归属见 SQ-23 |
| R-03 | 已改 | `business_facts` 为根表（只追加），拒收记录与序号计数为技术表 |
| R-04 | 已改 | 表名按词表（`payables`、`cash_payments`、`ledgers`、`cost_transfer_batches`、`allocation_results`、`accounts`）；核销类表不用 write_off；同名异义与 `journal_entries` 对 `journal_voucher` 交 one-glossary（新用语） |
| R-05 | 已改（占位） | AGG-125 期初余额 4 张表，聚合号待给（SQ-21） |
| R-06 | 已改 | `fund_accounts`、`company_assets` 根表 `versioned: none`，状态与可变属性在 `*_versions`；AGG-91 内含列另见 SQ-19 |
| R-07 | 已改 | 落账期间、原期间统一「（会计年度、期间序号）」，逐表补齐法人、业务日期、单据编号 |
| R-08 | 已改 | 无 tbd；运行单据写 none 并指向迁入的权责事实，投产前两期按 U-10 重算；分摊规则版本至少覆盖 U-10 两期 |
| R-09 | 已改 | 每张 full 表写 `source`；来源不在老系统与 A8 的改 none 加理由，是否取回见 U-33、SQ-25 |
| R-10 | 已改 | 新建根表 `bank_statement_imports`（校验和唯一、原始文件引用） |
| R-11 | 已改 | 发票占用只记在 AGG-89 占用子表，AGG-59 只存发票引用 |
| R-12 | 已改 | AGG-52 理由不含员工借款；聚合稿回补第 6 行（S-18） |
| R-13 | 已改 | 撤 AGG-78 两张表，聚合问题见 SQ-22 |
| R-14 | 已改 | 每表加 `pi`（none / pi / spi / tbd），口径见 F-24 |
| R-15 | 已改 | 关账确认、年末关账确认、对账签字、股东决议 `approval.engine: aggregate`；PRC-70、75、87、94、97 保留 `platform.approval`；目录 PRC-101 `human_orchestration` 改 false（聚合稿回补第 12 行） |
| R-16 | 已改 | PRC-70 同意证据判断移到提交审批前；新增步 7「释放预算占用」，无同意证据、驳回后放弃、本轮新增的审批撤回都走它 |
| R-17 | 已改 | PRC-92 冲销并重过账为独立入口（入口 3）；拒收分支写告警；幂等重复单列返回原 PostSeq |
| R-18 | 已改 | 「待用户定」与交架构、交 one-data 的事项分开（本文件结构问题、待用户定、交 one-data 复核三节） |
| R-19 | 已改 | 汇总表按根表取值；PRC-105 不加审批节点，评审生效人执行聚合命令，另写 `segregation` |
| R-20 | 已改 | PRC-102 步 1 未启用第 13 期转 PRC-101，补「已有生效登记」分支 |
| R-21 | 已改 | PRC-87、94、105 的判断点不再有两个同终点无区别的分支；期间闸门路由分支与 otherwise 同结局是照样板的有意写法（F-17） |
| R-22 | 已改 | PRC-87 步 4 改为「原始凭证要素不全」；员工角色映射见 F-20 |
| R-23 | 已改 | 锚点已展开；新字段汇总于格式问题 |
| R-24 | 已改 | 跨聚合引用一律 `to: AGG-xx`；同 BC 外键问题见 F-25 |
| R-25 | 已改 | 「上年调整栏」「以前期间更正栏」在 `period_balance_snapshot_lines` |
| R-26 | 已改 | 新旧科目对照、导入通道原始文件等已进 SQ-15、SQ-24 |
| R-27 | 不改，待用户定 | PRC-114 不自行加决议或审批节点，列 U-15 |
| R-28 | 已改 | PRC-93、99 备注与 PRC-100 步 6 写「形态待过渡 ADR（S-05）」，`pending` 保留；聚合稿 B-24 行另见回补第 11 行 |
| R-29 | 已改 | PRC-70 删无对应步骤的 SH-E6 复核角色；PRC-71 合并重复的 SH-E2；PRC-97 角色表改 SH-E6 initiator、SH-M1 approver；PRC-77 的 reviewer 因无步骤删去 |
| R-30 | 已改 | 关账确认与年末关账确认补 `event out` 到 B-20、B-21、reporting；B-21 承接见 PRC-106 与 SQ-03 |
| R-31 | 已采纳 | 草稿另存、确认时才写版本行，见 F-26 与 SQ-10 |
| R-32 | 已改 | `supplier_agreement_evidences` 改 append_only；争议状态只在 `payable_dispute_records`；补齐缺失的父引用 |

## 被规则替代的审批

用户原则：能由规则和不变量守的不设人工审批。判断结果：本组 24 个业务流程里带 `approval` 的节点共 8 个（platform.approval 4 个：PRC-70 步 4、PRC-75 步 4、PRC-94 步 3、PRC-97 步 2；聚合命令确认 4 个：PRC-101 步 3、PRC-102 步 6、PRC-104 步 3、PRC-112 步 3），每个节点要判断的东西仍含人才能做的判断，故**整节点被规则替代的为 0 个**；它们原先顺带守的校验、可能加审的条件，已全部移出审批，改由规则与不变量守，审批只留下残余的判断（写进各节点的 `approval.why_human`）。

下表是被规则替代的审批判断与加审（共 13 项）：

| # | 流程 步 | 原先由人（审批、加审或复核）守的 | 现在由什么守 | 规则 |
|---|---|---|---|---|
| 1 | PRC-70 步 1 | 供方是否已准入、资质是否合格 | 拒绝并告警，放弃 | INV-03、INV-04；AR-08（未准入即拒绝） |
| 2 | PRC-70 步 1、3、5 | 再分包与分包资质、发包方同意证据 | 拒绝并告警，退回补正 | INV-11、12、13 |
| 3 | PRC-70 步 2 | 预算超额 | 只预警，审批待办标「超预算」，不审批 | INV-102；AR-03 |
| 4 | PRC-73 步 1 | 开票方是否已登记往来单位 | 拒收并告警 | AR-08 |
| 5 | PRC-75 步 1 | 付款有无协议依据（AR-04 无合同依据加审） | 拒收并告警 | AR-04 |
| 6 | PRC-75 步 1 | 收款方是否为协议相对方或其登记账户（AR-09 特殊加两级） | 拒收并告警 | AR-09；INV-80 |
| 7 | PRC-75 步 2 | 付款是否超过应付未付余额 | 拒收并告警 | INV-79 |
| 8 | PRC-75 步 4、5 | 发起、审批、出纳是否同一人 | 驳回或拒收，改由他人 | INV-81（segregation，不算审批层级） |
| 9 | PRC-94 步 1 | 手工凭证的理由与附件、控制科目授权、差异类别 | 拒收 | INV-108、INV-98、INV-97 |
| 10 | 17 个流程共 19 个触账步骤（PRC-15、25、35、55、64、72、73、74、77、93、94、96、98、99、112、114、132） | 业务日期落在已关账期间（AR-05 特殊加两级审批） | 系统触账自动落当前开放期间并标原期间，不拒绝；人工发起的（PRC-94、98）按规则提示，不设审批；管道无开放期间可落才拒收 | AR-05；ADR-0045 决定 2；INV-92 |
| 11 | PRC-97 步 1、3 | 规则引用的组织单元是否有效、生效日是否期间首日且不追溯 | 拒收并告警 | INV-91 |
| 12 | PRC-101 步 2、3；PRC-102 步 3、5 | 重复关账、关账前置检查（待处理队列、死信、期末结转） | 拒绝；确认时重跑前置检查 | INV-99、INV-100、INV-104 |
| 13 | PRC-112 步 1、3、5 | 可分配利润上限、决议额与支付合计 | 拒收并告警 | INV-117 |

涉及的 AR 规则共 5 条（AR-03、04、05、08、09，均为 `guard: rule`），AR-10 在本组无对应步骤。

**保留的人工审批（8 个）**

| 流程 步 | engine | 审批人 | 加审 | 为何非人判断不可（`why_human` 概述） |
|---|---|---|---|---|
| PRC-70 步 4 供方协议审批 | platform.approval | cost_object_owner（项目采购取项目经理、公司级取部门负责人） | AR-01、02、07、11、12 | 价款、付款节点、范围与违约责任是否合理 |
| PRC-75 步 4 付款审批 | platform.approval | cost_object_owner（应付归属的项目或部门；无单一归属见 U-19） | AR-01、02、07、11、12 | 付款时点与资金安排，合同付款节点是否实际满足、有无争议或抵扣 |
| PRC-94 步 3 手工凭证审批 | platform.approval | cost_object_owner（凭证所属法人的财务部门，财务负责人 SH-M1 占位） | AR-01、02、06、11、12 | 调整理由是否成立、科目与金额是否恰当 |
| PRC-97 步 2 分摊规则审批 | platform.approval | cost_object_owner（间接成本池归属财务部门，SH-M1 占位） | AR-11、12 | 分摊基数、档位、费率是管理口径的政策判断 |
| PRC-101 步 3 月结关账确认 | aggregate | SH-M1（职责角色，`approver_basis` 说明为何不是 cost_object_owner） | 无 | 账务是否可以定稿，期间关后不可重开 |
| PRC-102 步 6 年末关账确认 | aggregate | SH-M1（同上） | 无 | 年度两期同时定稿 |
| PRC-104 步 3 对账差异签字 | aggregate | SH-M1（同上，签字人与容差待 U-10） | 无 | 未清零差异是否可接受 |
| PRC-112 步 3 利润分配决议 | aggregate（线下决议、登记证据） | SH-O1（股东会，法定职权） | 无 | 利润分配是公司治理事项，依法须股东会或合伙人决议 |

另有两处只做不相容职务分离、不设审批节点：PRC-100（起草 ≠ 批准，ADR-0022 决定 2）、PRC-105（编制 ≠ 评审生效，R-19）。PRC-87（样板）不动。

## 方法验证 / 推翻

| 做法 | 结论 | 证据 |
|---|---|---|
| 先出一个样板流程给用户看，再铺开 | 验证 | 样板 PRC-87 经 S-15–S-19、S-24 多轮裁定后被用户确认（intent 162820、162821），铺开没有返工字段约定；提交 d727121d → 1a27243d |
| 初稿 + opus 首轮对抗评审 + 按评审逐条改 | 验证 | REVIEW.md 32 条（H5），抓出只追加表含可变状态、表名未用已批准用语、缺期初余额表等结构性问题；56bb1e45 |
| 流程初稿里把对方 BC 的反应写进本流程（编舞步骤） | 推翻 | 被裁定为方向错误或应独立为事件策略（S-16、S-24），最后拆成 PRC-147；2d26005f |
| 先选定冲抵方向，再逐步发现多付、停在「已核定」的问题 | 验证（逐步走查有效） | 每改一次都沿资金走一遍，发现 S-17、S-19 两个漏洞；b7af0b3d、56775e02 |
| 主会话写简报，sonnet 子代理按价值流分批并行写骨架，主会话合并 | 验证 | 三批 38 条合并后，closeout_check 命令可查 92/92、聚合缺失 0；c8c0af33 |
| 子代理只写临时片段、主会话合并进正式文件（不让多个子代理改同一文件） | 验证 | 合并无冲突、条目不重不漏（38 条与目录一致） |
| 审批节点按单据类型多级预设 | 推翻 | 用户原则改为责任人一级、异常按规则表加审、能规则守的不设审批（intent 163556）；PRC-87 改于 99d49a58 |
| 「在哪」逐步标注（本系统 / 自动 / 外部 / 线下）+ 证据 | 验证 | 用户追问「哪些节点在本系统」后补上，样板一次通过；cb9a69f8 |
| 只看代码和已有盘点，不取生产数据，判断报销情形覆盖 | 待验证 | 找到三种结算方式与 7 个缺口（EXPENSE_COVERAGE.md，7e32c4ca），但分贝通付款方式、A8 字段等仍需取证或问财务 |
| 期间闸门写成「已关账即拒收」 | 推翻 | 与 ADR-0045、INV-92 冲突，改为自动落开放期间并标原期间（CONVENTIONS 第 5 节） |
| 生成器修好后（bpmn_gen 支持多入口 triggers[].entry、驳回与撤回出口），多入口流程不拆成多个流程即可通过 | 验证 | S-29、S-30 后 PRC-72、75、87、93、102、112 加分支入口（冲销、退票、撤销、开立会计年度），24 张图全部生成，bpmn_check 通过，生成警告 0（此前 PRC-101、102 两条同泳道升级警告已改升级对象消除） |
| 先写新流程与新聚合的片段、后统一合并（S-31：14 条流程片段与 13 个聚合片段由子代理按 SQ 并行写，主会话合并进正式文件，再改 38 条既有骨架） | 验证 | 合并后 closeout_check --qc 本组出口、超时、结局无场景均为 0，命令可查 100%，bpmn 29 张 0 错 0 警；冲突集中在合并时才暴露的 7 处：插入步骤使 goto 整体后移（PRC-71、104）、单聚合维护的 `commands` 与 `transitions` 键（object_check 判终点冲突）、命令名与聚合稿草案名不一致、双触发（PRC-163）、读项目的 B-04 调用（PRC-175）、权责表职能名；片段阶段看不见这些，说明合并后的全量检查不可省 |
| 用通配符读本机私有目录 | 推翻 | 误把凭据输出进会话记录；改为只按文件名读 |

## 质检修后（QC-VS-05、06、08、10、12 复查）

按五份质检的需改清单改骨架；需新流程号、新命令、新聚合或用户决定的，步骤标 `pending` 并进上面的结构问题（SQ-41–SQ-54）与待用户定（U-40–U-45）。权责表同步：aggregate 引擎的审批 A 改为类型级 SH-M1（PRC-65、77、100、101、102、104、105），platform.approval 的手工凭证、分摊规则、资产处置 A 改实例级 `费用归属对象.责任人`（解析见 SQ-51），新增 释放履约额度（draft_gap）。

## 收敛（命令目录与状态机）

产物：`commands.yaml`（168 条，其中 `proposed` 7 条）、`state-machines.yaml`（44 台，其中 `stateless` 7 台，均写 reason）、`events.finance.yaml`（60 个事件，4 个带 `mismatch`）。名称取聚合稿 §2.2（cb39fc65）；发令权取权责表 A；前置条件取聚合稿「前置 INV」加骨架判断点引用的 INV；转换的守卫是提议，交 one-rules。未做同义合并（`merged_from` 无）。

### 脚本剩余缺口（S-31、S-34、S-36 后；`--local`，revenue、people 骨架按各自分支原样放进副本一起跑）

| 脚本 | 本组剩余 | 说明 |
|---|---|---|
| closeout_check --qc --all --matrix | 出口、超时、结局无场景 0；approver 不符 0；缺 permission/page 0；命令可查 161/161（本组）| 提议新增命令 2 处（冲销应付利润、处理账单差异），另 PRC-158、166、168、52 等处的〔缺〕见目录 proposed 7 条；用语无着落 0（新用语补入 NOTES）；revenue PRC-53 一条 approver 不符是他组 |
| closeout_check | 命令不在聚合命令列 0（本组）；聚合缺失 0 | 他组条目不计 |
| converge_check --all | 孤儿命令 0；本组骨架命令未入目录 0；本组改状态步骤无对应转换 0；本组聚合无状态机 0 | 脚本已读 commands[] 与 transitions[]（S-34 ①），原 11 条绕脚本的 `unused_ok` 已撤回；仍保留 `unused_ok` 的：AGG-109 提交过账候选（只留给不经应付的项）、AGG-127 登记口径转换规则版本（随代码发布）、AGG-130 标记费用事前申请已用尽（待回补）、AGG-52 提交应付薪酬（people PRC-126 用） |
| converge_check：他组聚合 | AGG-80 预算占用、释放预算占用、调整预算占用等 | 他组聚合（预算账户，B-18），骨架 PRC-70、71、87 在用；由所属组收入，本组不替收（不改）；脚本的「骨架命令未入目录」「聚合无状态机」列表里其余条目均为 revenue、people 的聚合 |
| object_check | 本组新增聚合状态机冲突 0（PRC-165、166、168、174 补「已生效 → 已取代」转换；单聚合维护改 `transitions`）；既有遗留：AGG-54 在 PRC-111 的终点名（部分退回、已退回）冲突；AGG-84 状态集合分作 3 组（外部法定账推送、记账、待处理队列项）无转换相连；AGG-77 多个 BC 写（PRC-92 的设计）；AGG-96 写入方 B-17（聚合稿 B-19，请裁） | 其余「权责表无此对象×命令」是 GOV 合并矩阵尚未含本组新条目（raci_build 重建后消失），「对象无人修改 / 无人结束」多为配置型版本聚合（只追加）与资金账户、第 13 期登记等，沿旧口径 |
| bpmn_gen | 29 张，0 错 0 警 | 新增 PRC-170–173、175；PRC-71、104 修正步骤入口后通过 |

脚本误报说明：原 `converge_check` 只读 `commands`、不读 `transitions`，会把单聚合维护（PRC-65、111、135、158 等）的命令报成孤儿；S-34 ① 已改脚本，上表已无此项。AGG-80 为他组聚合，条目出现在本组骨架的未入目录清单里是预期。

### 交 one-architecture 的问题

| # | 问题 | 建议 / 依据 |
|---|---|---|
| C-1 | AGG-43 聚合稿有独立命令「提交过账候选」，骨架（PRC-15、25、35、55）四个结转命令各自直接发「成本结转已过账」，无单独提交步 | 二选一：聚合稿删该命令并说明过账内含于四个结转命令；或骨架四个事件策略各拆出提交过账步。状态机已按前者写（单一终态「已过账」）；FD 口径与 Q20 待定不动；已裁定（S-30 ①）：保留提交过账候选并拆骨架 |
| C-2 | AGG-84 的 `StatutoryRevenueBooked`、`ImpairmentBooked`、AGG-43 的 `CostAccumulated` 在聚合稿事件列，但命令列没有任何命令发出它们 | 补发出命令，或从事件列删（F-4 要求事件须有发布命令） |
| C-3 | 已裁定（S-31 SQ-11：由对账 PRC-104 登记）；原问题：AGG-84 推送状态「已推送 → 已确认记账」骨架 PRC-135 标〔缺〕登记外部记账确认，而 PRC-135 命令列只有两条；PRC-104 对账读取 U8C 后由谁、经哪个命令回写 | 聚合稿加该命令并在 PRC-135 或 PRC-104 列为步骤；目录里已按 proposed 收入并标 `unused_ok`；已裁定（S-30 ②）：PRC-93 步 3、PRC-135 状态转换 |
| C-4 | AGG-81 没有「把第 1–12 期置为开放」的命令：期间由谁、何时创建并开放（年度开账）聚合稿无命令；状态机以「未开放」为初态，仅第 13 期有「开放第 13 期」转换 | 补「开放会计期间」命令与流程（或年度开账事件策略）；涉及 P-3 第 13 期接收范围，不由本组定；已裁定（S-30 ②）：开立会计年度，PRC-102 步 8 |
| C-5 | 付款退票与应付核销：AGG-53 状态「已成功 → 已失败（退票）」已写，但应付已按「付款已成功」核销、付款已置「已核销」之后退票，需要逆向冲回核销占用与余额，聚合稿无冲回命令（AGG-52 只有核销、释放占用） | 补「冲回核销」或明确退票只发生在核销之前（银行结果在核销事件前确认）；骨架 PRC-131、132 未覆盖；已裁定（S-30 ③）：登记退票、冲回应付核销，PRC-75 步 7–9 |
| C-6 | 运行单据（AGG-41、99、43）已过账后被更正，是否需要「已更正」状态：按 ADR-0045 更正以冲销并重过账在开放期间追加新行，本稿写 `reversal_note`，不改原行状态；但「每期间一次有效」唯一索引与重做运行如何共存未定 | 请定：更正产生新运行单据并标旧单据「已被取代」，或部分唯一索引放宽到按有效标记；已裁定（S-30 ③）：运行单据更正后原单置已冲销，不用已被取代（AGG-109 已改） |
| C-7 | AGG-52 争议状态与处置分类（诉讼）聚合稿内含列有，命令列无「标记争议」「解除争议」；PRC-75 判断点已拒绝争议应付 | 补命令；状态机暂未收争议状态 |
| C-8 | AGG-87 无解冻命令（PRC-65 已记）：冻结后只能销户，无逆向转换 | 补「解冻资金账户」或确认冻结不可逆；已裁定（S-29 SQ-42）：解冻资金账户 |
| C-9 | AGG-90 更正申报产生新申报并把原申报标「已更正」，已缴款后的更正对应的补缴、退税未建命令 | 提议稿按「已提交、已缴款 → 已更正」写，请确认是否另建更正申报聚合 |
| C-10 | AGG-98 手工凭证驳回回到「草稿」（骨架 on_reject 1），而聚合稿状态列无「已撤回」「已驳回」 | 本稿增「已撤回」终态（骨架撤回 → end）；请确认 |
| C-11 | 版本配置类（AGG-40、82、85、94、86）带「草稿」状态，而数据模型写「版本行确认时才写入，草稿另存」 | 草稿存放位置待 one-data；状态机把「草稿」当聚合状态，如草稿另存则初态改为「已生效」前置的聚合外概念 |
| C-12 | 聚合稿 AGG-52 的 `提交应付薪酬` 本组骨架无步骤，由 people 组 PRC-126 使用；AGG-52 `确认应付` 另被 revenue 组 PRC-147 使用，AGG-53 `发起付款` 另被 revenue 组 PRC-08、AGG-59 `标记费用报销已付` 另被 PRC-147 使用 | 目录以 `unused_ok` 与 `also_used_by` 记录，无需动作，仅供三组汇总时核对 |
| C-13 | 已裁定（S-31 SQ-21）：AGG-125；原问题：期初余额表聚合号待定（占位号），命令（建立、签字、复核期初余额）与权责均为占位；投产准入流程未建骨架 | 给号并起骨架；权责待定，签字人为财务负责人、复核为内部审计（G-2、G-3，A1-02 待定） |

### 事件目录对不上的地方

| 事件 | 对不上 | 建议 |
|---|---|---|
| 申报已提交 | 聚合稿写「仅分析」，骨架 PRC-77 向 B-10 发出（接缝 5、19） | 已裁定（S-29 SQ-44）：事件目录改为 B-10 订阅（PRC-159），不再列为对不上 |
| 进项发票已用途确认 | 聚合稿写「仅分析」，PRC-74 触发（B-11） | 订阅方改为 B-11 |
| 付款已成功 | 骨架多出 revenue 组 PRC-08（B-03，保证金付款）订阅，聚合稿订阅方只有 B-10 | 已关闭（S-31 SQ-01）：revenue PRC-08 不再订阅，订阅方只有 B-10；另 revenue PRC-86 订阅「借款付款已成功」，本组目录无此事件名，是 AGG-53 的同名事件还是独立事件请定 |
| BusinessFactAttributed、BusinessFactRestated | 聚合稿订阅方含 B-17（Attributed、Restated）、B-07（Restated）；骨架只有 cross_bc 向其发出，无订阅流程（接缝 10 已提） | 已裁定（S-31 SQ-04）：B-17 不订阅，api 读取；B-07 由 PRC-164 订阅 Restated，目录已登记 |
| CostAccumulated、StatutoryRevenueBooked、ImpairmentBooked | 见 C-2 | 同 C-2 |

另：骨架 PRC-93、103、104 的事件触发用 `event`/`publisher_bc` 键，其余流程用 `name`/`publisher`，建议统一（格式问题，不影响收敛检查）。

## S-39 落实（2026-10-16）

- ① 「登记预付款」发「预付款已登记」，PRC-75 待付清单订阅（同 BC，与「应付已确认」相同）；命令目录与事件目录已登记。聚合稿回补：AGG-52 对外事件加「预付款已登记（B-10 内 PRC-75）」。
- ② INV-131 适用 AGG-78、123、124、133：命令目录的发布命令已引用 INV-131；非会计配置的出处为《企业内部控制基本规范》第二十九条（不相容职务分离）。
- ③ 不加 B-16 → B-17 边：过账事实（AGG-77 business_facts）与人工成本分配运行（AGG-41）的「口径版本引用」列族已删；口径版本只在 B-17 勾稽侧记录。

## 接缝检查（SEAM_REPORT，S-42）的处理

| 聚合稿声明的订阅 | 处理 | 理由 |
|---|---|---|
| AGG-101「对账已完成」→ B-21 | 建议删订阅方 | 报表出具时经 api 读对账状态即可（聚合稿 AGG-101 原写「B-21 经 api 读状态」），无需事件反应 |
| AGG-52「应付已确认」→ B-09 | 建议删订阅方（与 revenue 核对） | S-17 后由 B-09 调 B-10 确认应付，B-09 是调用方，不需要再订阅结果 |
| AGG-117「法人归属已变更」→ B-16 | 建议删订阅方 | 过账管道在过账时按候选携带的法人校验（INV-93）；已过账事实只追加，法人归属变更不回改事实 |
| AGG-01「银行账户已变更」→ B-10 | 改为 api 读取，删订阅方 | P-01「变更后首笔付款预警」在 PRC-75 第 1 步读收款账户及其变更时间实现（B-10 → B-01 既有 api 边），不需要本地订阅缓存 |
| AGG-13「合同—项目关联已登记」→ B-07 | 已承接 | PRC-25 加入口 3：订阅该事件登记售前投入挂接归属 |
| AGG-77 BusinessFactAttributed → B-18 | 归 people 组承接 | 订阅方 B-18 计划与预算属 people 组（预算消耗），finance 只是发布方 |
