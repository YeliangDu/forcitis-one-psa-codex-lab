# 用语确认表

规则（用户 2026-10-14）：「所有跟正式用语不符合的，都归为我们公司内部的方言，新系统一律采用正式用语，不把方言带入新系统，不确定哪些是正式用语，哪些是方言可以登记用语表，我来确认。」

**怎么确认**：在「确认」列填 `正式`、`方言` 或 `改为：<用语>`，也可以直接回复编号（例如「A3 方言，B12 同意，C 组全部同意」）。确认后由 `glossary/build_terms.py` 更新 `terms.yaml`，状态改为已批准。

已确定为方言、无需再确认的（如「产值确认单」「产值」= 收入确认单）不在本表，见 `terms.yaml` 中 `type: dialect` 的别名。

## A. 公司叫法：正式用语还是方言？（19 项）

这些是公司在用的中文叫法，审计没有把握判定。选「正式」则保留并与标准用语并列为同义正式名（新系统只用一个，取右列）；选「方言」则禁止进入新系统。

| 编号 | 公司叫法 | 对应正式用语（中 / 英） | 所属模块 | 依据 | 建议 | 确认 |
|---|---|---|---|---|---|---|
| A1 | 实际结果（来源：在用报表：运营看板） | 经营结果 / `BusinessResult` | `operations.facts` | FORCITIS 03-通用语言（内部概念，准则 / 国标无对应条目） | 方言 |  |
| A2 | 待签合约（来源：在用报表：经营分析报告、交付中心汇报） | 合同草案 / `ContractDraft` | `contract.baseline` | FORCITIS 03-通用语言（内部概念，准则 / 国标无对应条目） | 方言 |  |
| A3 | 待签项目投入（来源：在用报表：交付中心汇报 5.1–5.2） | 无合同风险敞口 / `UncontractedExposure` | `contract.riskdelivery` | FORCITIS 03-通用语言（内部概念，准则 / 国标无对应条目） | 方言 |  |
| A4 | 收费工时（来源：在用报表：运营看板人效情况） | 可计费工时 / `BillableHours` | `cost.timesheet` | PSA 行业通行写法（Kantata / Certinia / Deltek 等） | 方言 |  |
| A5 | 委外费（来源：在用报表：损益表、现金流量表（外包或分包待确认）） | 外包成本 / `OutsourcingCost` | `cost.costing` | FORCITIS 03-通用语言（内部概念，准则 / 国标无对应条目） | 方言 |  |
| A6 | 本年度未开票（来源：在用报表：交付中心汇报 3.1（本年口径，不得与全量合同资产相加）） | 未开票在制（WIP） / `UnbilledWip` | `receivables.wip` | PSA 行业通行写法（Kantata / Certinia / Deltek 等）；FORCITIS 03-通用语言（内部概念，准则 / 国标无对应条目） | 方言 |  |
| A7 | 已确认未开票（来源：在用报表：交付中心汇报、运营看板立项明细） | 合同资产 / `ContractAsset` | `receivables.wip` | 企业会计准则第 14 号——收入（财会〔2017〕22 号）；IFRS 15 Revenue from Contracts with Customers | 方言 |  |
| A8 | 存量资产（来源：在用报表：交付中心汇报区域健康度） | 合同资产 / `ContractAsset` | `receivables.wip` | 企业会计准则第 14 号——收入（财会〔2017〕22 号）；IFRS 15 Revenue from Contracts with Customers | 方言 |  |
| A9 | 开票收入（来源：在用报表：损益表（开票收入（含税））） | 开票 / `InvoiceIssued` | `receivables.wip` | 企业会计准则第 14 号——收入（财会〔2017〕22 号） | 方言 |  |
| A10 | 已开票未回款（来源：在用报表：应收账款分析底表） | 应收账款 / `AccountsReceivable` | `receivables.collection` | 企业会计准则第 14 号——收入（财会〔2017〕22 号）；企业会计准则第 22 号——金融工具确认和计量（预期信用损失） | 方言 |  |
| A11 | 回款收入（来源：在用报表：损益表区域表（含现金、保理、商票、工抵）） | 回款 / `CashReceived` | `receivables.collection` | FORCITIS 03-通用语言（内部概念，准则 / 国标无对应条目） | 方言 |  |
| A12 | 坏账（来源：在用报表：应收账款分析底表（OA 坏账申请导出）） | 坏账核销 / `ArWrittenOff` | `receivables.collection` | 企业会计准则第 22 号——金融工具确认和计量（预期信用损失）；IFRS 9 Financial Instruments（expected credit losses） | 方言 |  |
| A13 | 账龄区间（来源：在用报表：应收账款分析底表） | 账龄桶 / `AgingBucket` | `receivables.collection` | PSA 行业通行写法（Kantata / Certinia / Deltek 等） | 方言 |  |
| A14 | 预计回款（来源：在用报表：应收账款分析底表、运营看板开票明细） | 回款计划 / `CollectionPlan` | `receivables.collection` | PSA 行业通行写法（Kantata / Certinia / Deltek 等） | 方言 |  |
| A15 | 预收（来源：在用报表：运营看板回款明细「是否预收」） | 合同负债 / `ContractLiability` | `receivables.wip` | 企业会计准则第 14 号——收入（财会〔2017〕22 号）；IFRS 15 Revenue from Contracts with Customers | 方言 |  |
| A16 | 合同额（来源：在用报表：经营分析报告（合同额、合同额占比）） | 交易价格 / `TransactionPrice` | `contract.baseline` | 企业会计准则第 14 号——收入（财会〔2017〕22 号）；IFRS 15 Revenue from Contracts with Customers | 方言 |  |
| A17 | 剩余合同额（来源：在用报表：运营看板人效情况） | 剩余履约义务 / `RemainingPerformanceObligation` | `contract.baseline` | 企业会计准则第 14 号——收入（财会〔2017〕22 号）；IFRS 15 Revenue from Contracts with Customers | 方言 |  |
| A18 | 合约储备（来源：在用报表：经营分析报告业绩摘要） | 剩余履约义务 / `RemainingPerformanceObligation` | `contract.baseline` | 企业会计准则第 14 号——收入（财会〔2017〕22 号）；IFRS 15 Revenue from Contracts with Customers | 方言 |  |
| A19 | 产能饱和度（来源：在用报表：运营看板人效情况、交付中心汇报） | 利用率 / `UtilizationRate` | `cost.timesheet` | PSA 行业通行写法（Kantata / Certinia / Deltek 等） | 方言 |  |

## B. 改为标准用语的建议（0 项）

原词表用语不符合准则或行业标准，建议改名；原叫法确认后归为方言。标 💰 的涉及会计口径，建议同时请财务确认。

| 编号 | 原用语（将归为方言） | 建议正式用语（中 / 英） | 所属模块 | 依据 | 改名编号 | 确认 |
|---|---|---|---|---|---|---|

## C. 公司自有概念，没有外部标准用语（0 项，可整组确认）

这些概念在准则、国标、PMBOK、FIDIC 中都没有对应条目，是公司自己的经营管理概念。建议以词表的定义作为正式用语；如果其中有你认为是口头叫法、应另取正式名的，单独标出。

| 编号 | 模块 | 用语（中 / 英） | 确认 |
|---|---|---|---|

## P. 模块提议术语转正式（58 项，第二轮）

架构守护模板与 Phase 1a 平台契约带来的术语（`backend/glossary/proposed/`）。回复「P 组全部同意建议」即可，有异议的单独标出。

| 编号 | 提议（中 / 英） | 模块 | 建议 | 理由 | 需改代码 | 确认 |
|---|---|---|---|---|---|---|
| P1 | 工时 / `Hours` | `kernel` | 修改后转正式：小时数 | 正式用语「工时」已是 TimeEntry（工时条目）的中文名；值对象 Hours 只是小时数量，再叫「工时」会同名异义。英文不变。 |  | ✅ 同意建议 |
| P2 | 填报工时 / `DeclaredHours` | `cost.timesheet` | 修改后转正式：填报工时 / `SubmittedHours` | 「填报」的正式英文已定为 Submit（SubmitTimesheet，第一轮 C66），同一动作不另用 Declared。 | cost.timesheet.domain.Timesheet 的组件 declaredHours → submittedHours（及其调用、JSON 字段、列名 declared_hours 如有） | ✅ 同意建议 |
| P3 | 工作日期 / `WorkDate` | `cost.timesheet` | 原样转正式 |  |  | ✅ 同意建议 |
| P4 | 计划工时 / `PlannedHours` | `delivery.execution` | 修改后转正式：只改定义 | 原定义用了方言「任务包」（第一轮已定为工作包 WorkPackage），只改定义。 |  | ✅ 同意建议 |
| P5 | 实际工时 / `ActualHours` | `delivery.execution` | 修改后转正式：只改定义 | 同上，定义中的「任务包」改为「工作包」。 |  | ✅ 同意建议 |
| P6 | 剩余工时 / `RemainingHours` | `delivery.execution` | 原样转正式 | 计划工时减实际工时的机械差额；与 EVM 的完工尚需估算 ETC（estimate_to_complete，人工估计）不同，两者并存。 |  | ✅ 同意建议 |
| P7 | 预算 / `Budget` | `delivery.execution` | 并入已有正式用语：项目预算（WBS 预算） / `ProjectBudget` | 已有正式用语「项目预算 ProjectBudget」（WBS 预算）与「成本预算 CostBudget」；单独的「预算 Budget」会与二者重叠。 | 权限码 delivery.execution:budget:update → delivery.execution:project-budget:update（Permission.DELIVERY_BUDGET_UPDATE） | ✅ 同意建议 |
| P8 | 项目交付 / `Delivery` | `delivery` | 原样转正式 |  |  | ✅ 同意建议 |
| P9 | 单据 / `Document` | `platform.numbering` | 原样转正式 |  |  | ✅ 同意建议 |
| P10 | 单据类型 / `DocumentType` | `platform.numbering` | 原样转正式 |  |  | ✅ 同意建议 |
| P11 | 单据编号 / `DocumentNumber` | `platform.numbering` | 原样转正式；方言：业务编号 | 同一概念只留一个正式名「单据编号」，「业务编号」归方言。 | platform_numbering schema 注释「业务编号（…）」改为「单据编号（…）」；文档中的「业务编号」随后替换 | ✅ 同意建议 |
| P12 | 编号规则 / `NumberingRule` | `platform.numbering` | 原样转正式 |  |  | ✅ 同意建议 |
| P13 | 编号模板 / `NumberingPattern` | `platform.numbering` | 原样转正式 |  |  | ✅ 同意建议 |
| P14 | 编号计数器 / `NumberingCounter` | `platform.numbering` | 原样转正式 |  |  | ✅ 同意建议 |
| P15 | 流水号重置周期 / `ResetPolicy` | `platform.numbering` | 原样转正式 | ResetPolicy 已用于枚举、接口字段与列名；Policy 在经营口径 BC 另有含义，但按 BC 隔离可接受，不值得改名。 |  | ✅ 同意建议 |
| P16 | 周期键 / `PeriodKey` | `platform.numbering` | 原样转正式 |  |  | ✅ 同意建议 |
| P17 | 审批实例 / `ApprovalInstance` | `platform.approval` | 原样转正式；方言：流程实例 | 界面与文档统一称「审批实例」；Flowable 的 ProcessInstance 是第三方类型，不受影响。 |  | ✅ 同意建议 |
| P18 | 审批流程 / `ApprovalProcess` | `platform.approval` | 原样转正式 |  |  | ✅ 同意建议 |
| P19 | 流程标识 / `ProcessKey` | `platform.approval` | 原样转正式 |  |  | ✅ 同意建议 |
| P20 | 业务键 / `BusinessKey` | `platform.approval` | 原样转正式 |  |  | ✅ 同意建议 |
| P21 | 发起人 / `Initiator` | `platform.approval` | 原样转正式 |  |  | ✅ 同意建议 |
| P22 | 审批人 / `Approver` | `platform.approval` | 原样转正式 |  |  | ✅ 同意建议 |
| P23 | 审批任务 / `Task` | `platform.approval` | 修改后转正式：`ApprovalTask` | 单独的 Task 会与「工作包」（原「任务包」）、「任务订单 TaskOrder」同名异义；用 ApprovalTask 限定为审批任务。 |  | ✅ 同意建议 |
| P24 | 路由标记 / `RoutingFlag` | `platform.approval` | 原样转正式 |  |  | ✅ 同意建议 |
| P25 | 金额 / `Money` | `kernel` | 原样转正式 |  |  | ✅ 同意建议 |
| P26 | 数额 / `Amount` | `kernel` | 原样转正式 |  |  | ✅ 同意建议 |
| P27 | 币种 / `Currency` | `kernel` | 原样转正式 |  |  | ✅ 同意建议 |
| P28 | 业务日期 / `BusinessDate` | `kernel` | 原样转正式 |  |  | ✅ 同意建议 |
| P29 | 收入确认准备 / `ProgressRecognition` | `receivables.wip` | 并入已有正式用语：收入确认单 / `RevenueRecognition` | 第一轮已裁定「收入确认准备」「产值」是「收入确认单 RevenueRecognition」的方言；不再单立。 |  | ✅ 同意建议 |
| P30 | 成本 / `Cost` | `cost` | 原样转正式 |  |  | ✅ 同意建议 |
| P31 | 错误码 / `ErrorCode` | `kernel` | 原样转正式 |  |  | ✅ 同意建议 |
| P32 | 编号容量 / `Capacity` | `platform.numbering` | 修改后转正式：`NumberingCapacity` | Capacity 在工时 BC 已是「产能」（闲置产能 IdleCapacity、产能变现）；编号模块的容量用 NumberingCapacity 限定。 |  | ✅ 同意建议 |
| P33 | 调度锁 / `SchedulerLock` | `platform.scheduling` | 原样转正式；方言：作业锁 |  |  | ✅ 同意建议 |
| P34 | 企业微信 / `Wecom` | `platform.integration` | 原样转正式 | 腾讯官方英文名 WeCom，按「缩写按单词处理」写作 Wecom。 |  | ✅ 同意建议 |
| P35 | 企业微信登录入口 / `WecomEntry` | `platform.integration` | 原样转正式 |  |  | ✅ 同意建议 |
| P36 | 企业微信工作台 / `Workbench` | `platform.integration` | 原样转正式；方言：H5 | 老系统把工作台网页叫 H5，归方言。 |  | ✅ 同意建议 |
| P37 | 小程序 / `MiniProgram` | `platform.integration` | 原样转正式 |  |  | ✅ 同意建议 |
| P38 | 扫码登录 / `WebScan` | `platform.integration` | 修改后转正式：`QrCodeLogin` | WebScan 易读成「网页扫描」；企业微信文档的说法是扫码登录（QR code login）。 | platform.integration.api.WecomEntry 枚举常量 WEB_SCAN → QR_CODE_LOGIN（及调用、测试） | ✅ 同意建议 |
| P39 | 身份 / `Identity` | `platform.identity` | 原样转正式 |  |  | ✅ 同意建议 |
| P40 | 组织 / `Organization` | `organization` | 原样转正式 |  |  | ✅ 同意建议 |
| P41 | 组织人事 / `Workforce` | `organization.workforce` | 原样转正式 |  |  | ✅ 同意建议 |
| P42 | 在职状态 / `EmploymentStatus` | `organization.workforce` | 原样转正式 |  |  | ✅ 同意建议 |
| P43 | 在职 / `Employed` | `organization.workforce` | 原样转正式 |  |  | ✅ 同意建议 |
| P44 | 离职 / `Separated` | `organization.workforce` | 原样转正式 |  |  | ✅ 同意建议 |
| P45 | 收费员工标识 / `FeeEarner` | `organization.workforce` | 原样转正式 | Fee earner 是专业服务业通行写法；定义已写明它不是可计费性判定。 |  | ✅ 同意建议 |
| P46 | 直属领导 / `DirectLeader` | `organization.workforce` | 修改后转正式：直属上级 / `Supervisor`；方言：直属领导 | DirectLeader 是中式英文；HR 系统通行 Supervisor（直属上级）。不用 LineManager，避免与禁用后缀 Manager 混淆。 | organization.workforce.api.OrgDirectory#findDirectLeader → findSupervisor（及实现、测试） | ✅ 同意建议 |
| P47 | 部门负责人 / `OrgUnitHead` | `organization.workforce` | 原样转正式 |  |  | ✅ 同意建议 |
| P48 | 区域应收负责人 / `RegionalReceivablesManager` | `organization.workforce` | 原样转正式；方言：区域 AR |  |  | ✅ 同意建议 |
| P49 | 上级组织链 / `Ancestor` | `organization.workforce` | 原样转正式 |  |  | ✅ 同意建议 |
| P50 | 员工快照 / `EmployeeProfile` | `organization.workforce` | 修改后转正式：员工概况 | Profile 不是「快照」；快照是 OrgSnapshot。英文不变。 |  | ✅ 同意建议 |
| P51 | 组织单元快照 / `OrgUnitProfile` | `organization.workforce` | 修改后转正式：组织单元概况 | 同上。 |  | ✅ 同意建议 |
| P52 | 组织快照 / `OrgSnapshot` | `organization.workforce` | 原样转正式 |  |  | ✅ 同意建议 |
| P53 | 同步运行 / `SyncRun` | `organization.workforce` | 修改后转正式：同步记录 | 「同步运行」不是通顺的中文；英文 SyncRun 不变。 |  | ✅ 同意建议 |
| P54 | 角色持有人 / `RoleHolder` | `platform.identity` | 原样转正式 |  |  | ✅ 同意建议 |
| P55 | 审计轨迹 / `AuditTrail` | `platform.audit` | 并入已有正式用语：审计日志 / `AuditLog`；方言：审计轨迹 | 与已批准的「审计日志 AuditLog」（只追加、不可篡改、可回放）是同一概念，两个名字会漂移。 | platform.audit.api.AuditTrail 接口改名 AuditLog（及实现、注入点、测试） | ✅ 同意建议 |
| P56 | 操作人 / `Actor` | `platform.audit` | 原样转正式 |  |  | ✅ 同意建议 |
| P57 | 被代理人 / `OnBehalfOf` | `platform.audit` | 原样转正式 |  |  | ✅ 同意建议 |
| P58 | 审计对象 / `Subject` | `platform.audit` | 修改后转正式：`AuditSubject` | Subject 在身份认证里指登录主体（JWT sub），用 AuditSubject 限定为审计对象。 |  | ✅ 同意建议 |

## N. 新 BC 命名（2 项，第二轮）

模块路径与蓝图编号由 psa-gl-scope 统一，这里只确认中英文名。

| 编号 | 暂定名 | 建议（中 / 英） | 理由 | 确认 |
|---|---|---|---|---|
| N1 | 假勤（暂定 organization.attendance，6e） | 考勤与休假 / `organization.attendance`；方言：假勤 | Time and Attendance 是 SAP、Oracle 等 HR 系统的通行模块名，含休假（absence）；「假勤」是企业微信、钉钉的产品叫法，归方言。 | ✅ 同意建议 |
| N2 | 应付与付款（新建 BC，6c） | 应付与付款 / `payables.payment` | 与 receivables.collection 对称；中文「应付」对应《企业会计准则第 30 号》报表项目应付账款。不用 disbursement：在专业服务业它指代垫费用（可向客户转收的支出），会同名异义。子域 payables 是否单设由 psa-gl-scope 定。 | ✅ 同意建议 |

## 第二轮状态：全部已确认

2026-10-14 业务 Admin 在用语确认页上确认了第二轮全部 80 项：P 组 58 项、N 组 2 项同意建议；A 组 20 个 A8 叫法归为方言（已写入 terms.yaml 的 dialect 别名与禁用写法）。另答复：项目委外「两种都有，按单据区分」；应收坏账申请「计提」。确认记录：`glossary/decisions-round2-2026-10-14.json`。
