# 第三轮用语确认的输入（R0 部分）

- 日期：2026-10-14 · 整理：psa-r0-metric-baseline
- 用户安排：第三轮用语确认在「1a 第二波和 R0 报表分析交了再一次做」。本文件是 R0 的输入，由第三轮会话与 1a 第二波的输入合并，出一张确认页。本会话不单独出确认页。
- 规则：ADR-0018。新词条一律先为「提议」；会计类由财务确认，业务类由业务 Admin 确认；确认前不得进入代码。
- OAT 引用：`docs/glossary/oat/forcitis-operating-accounting-taxonomy.csv` 的 `canonical_symbol`。

## A. 已登记的待确认别名（19 项）

已登记在 `docs/glossary/r0-report-aliases.json`，确认表见 `glossary/TERM_CONFIRMATION.md` A 组。每项要确认的是：这个叫法是方言（禁用），还是可以与正式用语并列。

| 叫法 | 对应正式用语 | 模块 | 建议 |
|---|---|---|---|
| 实际结果 | 经营结果 BusinessResult | `operations.facts` | 方言 |
| 待签合约 | 合同草案 ContractDraft | `contract.baseline` | 方言 |
| 待签项目投入 | 无合同风险敞口 UncontractedExposure | `contract.riskdelivery` | 方言 |
| 收费工时 | 可计费工时 BillableHours | `cost.timesheet` | 方言 |
| 委外费 | 外包成本 OutsourcingCost | `cost.costing` | 方言；按单据区分分包与外包（第二轮 A5 已定） |
| 本年度未开票 | 合同资产 ContractAsset（形成期间切片） | `receivables.wip` | 方言 |
| 已确认未开票 | 合同资产 ContractAsset | `receivables.wip` | 方言 |
| 存量资产 | 合同资产 ContractAsset | `receivables.wip` | 方言 |
| 开票收入 | 开票 InvoiceIssued | `receivables.wip` | 方言（开票不是收入） |
| 已开票未回款 | 应收账款 AccountsReceivable | `receivables.collection` | 方言 |
| 回款收入 | 回款 CashReceived | `receivables.collection` | 方言 |
| 坏账 | 坏账核销 ArWrittenOff | `receivables.collection` | 方言；与「收入冲减」区分（D17-8） |
| 账龄区间 | 账龄桶 AgingBucket | `receivables.collection` | 方言 |
| 预计回款 | 回款计划 CollectionPlan | `receivables.collection` | 方言 |
| 预收 | 合同负债 ContractLiability | `receivables.wip` | 方言（D17-7） |
| 合同额 | 交易价格 TransactionPrice | `contract.baseline` | 方言 |
| 剩余合同额 | 剩余履约义务 RemainingPerformanceObligation | `contract.baseline` | 方言 |
| 合约储备 | 剩余履约义务 RemainingPerformanceObligation | `contract.baseline` | 方言 |
| 产能饱和度 | 利用率 UtilizationRate | `cost.timesheet` | 方言 |

另有两项含「产值」，按已有裁定已自动归为方言：已确认产值、客户产值。

## N. 需新增的词条（起草稿，状态：提议）

「类别」沿用 `terms.yaml` 的 `kind`。「确认」一列：财 = 财务，业 = 业务 Admin。

### N1. 经营口径与指标（`operations.policy`）

| 中文 | 英文 | 类别 | 定义（草稿） | OAT | 标准依据 | 确认 |
|---|---|---|---|---|---|---|
| 贡献毛利 | ContributionMargin | metric | 收入减去可直接归属的成本后的余额；按层级（项目、BU）与变体（报价、预算、预估、实际）声明，扣减项见 FQ-04 | CM | 管理会计 | 财 |
| 贡献毛利率 | ContributionMarginRatio | metric | 贡献毛利 ÷ 对应收入（不含税） | CMR | 管理会计 | 财 |
| 净服务收入 | NetServiceRevenue | metric | 收入确认（不含税）减转付成本（FQ-04） | NR | AEC 行业 | 财 |
| 经营利润 | OperatingProfit | metric | 管理口径，贡献毛利减间接费用与分摊后的余额；法定口径的营业利润由财务报表提供 | OP | us-gaap:OperatingIncomeLoss | 财 |
| 经营利润率 | OperatingProfitMargin | metric | 经营利润 ÷ 收入确认（不含税） | OPM | 管理会计 | 财 |
| 间接费用 | Overhead | metric | 不能直接归属项目的费用，按职能（研发、总部、控股）归集 | OH | 管理会计 | 财 |
| 转付成本 | PassThroughCost | metric | 代客户支付、按原价或加成转嫁的成本，不计入净服务收入 | PTC | 管理会计 | 财 |
| 口径参数 | PolicyParameter | value | 按版本生效的口径参数：税率、日标准工时、账龄分段、分摊费率、激励门槛、色标阈值 | — | | 业 / 财 |
| 人工成本产出倍数 | （英文待定） | metric | 指定分子（收入确认 / 开票 / 回款）÷ 人工成本；用户 B8 已定中文名 | NRPM（参照「净收入薪酬乘数」） | AEC 行业 | 业 |

OAT 中 NRPM 的中文名是「净收入薪酬乘数」，分子是净服务收入、分母是薪酬。用户已定中文名「人工成本产出倍数」，分子有三种，第三轮确认时请一并决定英文名，以及是否与 NRPM 对齐。

### N2. 计划、目标与预算（`operations.planning`、`operations.budgeting`）

`GLOSSARY.md` 附录中两个 BC 的 21 个术语（战略目标、经营计划、计划基线、计划周期、预算目标、经营复盘、经营动作、目标偏差；预算基线、预算编制、预算分解、预算主体、预算占用、预算释放、预算消耗、可用额度、预算调整、超预算审批、预算冻结、预算执行、预算科目）一并转入 `terms.yaml`。另新增：

| 中文 | 英文 | 类别 | 定义（草稿） | OAT | 确认 |
|---|---|---|---|---|---|
| 计划目标值 | PlanTargetValue | metric | 某目标类型、某版本下，组织 × 期间 × 指标的目标值 | PLAN_TARGET_VALUE | 业 |
| 目标类型 | TargetType | enum | 预算、承诺、挑战、预测、减值计划（用户 B7） | — | 业 |
| 目标达成率 | TargetAttainmentRate | metric | 实际 ÷ 指定目标类型的目标值；名称须带目标类型 | — | 业 |
| 时间进度 | ElapsedPeriodRatio | value | 已过期间 ÷ 计划周期；只作参照线 | — | 业 |
| 承诺清单 | CommitmentList | document | 到项目 / 发票粒度的承诺明细，承诺目标的组成部分 | — | 业 |
| 预算消耗率 | BudgetBurnRate | metric | 已消耗 ÷ 预算基线 | BURN | 业 |

### N3. 订单、收入、应收（`contract.*`、`receivables.*`）

| 中文 | 英文 | 类别 | 定义（草稿） | OAT | 确认 |
|---|---|---|---|---|---|
| 新签合同额 | NewContractValue | metric | 合同签署日落在期间内的交易价格之和（不含税为主，含税另列）；是否扣转付见 FQ-04 | SIGNED_NET_BOOKINGS（变体） | 业 / 财 |
| 项目立项金额 | ProjectInitiationValue | metric | 项目立项时登记的预计合同额，订单前置指标（用户 B6） | —（拟登记为经营扩展原语） | 业 |
| 回款方式 | SettlementMethod | enum | 现金、商业承兑汇票、工抵、保理、保理发票核销 | — | 财 |
| 现金回款 | CashCollection | metric | 以现金或银行存款结清的回款 | — | 财 |
| 商业承兑汇票 | CommercialAcceptanceBill | document | 客户以商业承兑汇票支付的票据，按到期日兑付 | — | 财 |
| 账龄超 N 天应收 | AgedReceivable | metric | 按开票日起算账龄超过 N 天的应收余额；N 为口径参数 | — | 财 |
| 应收周转天数 | DaysSalesOutstanding | metric | 算法见 FQ-13 | DSO | 财 |
| 开票转化率 | BillingConversionRate | metric | 开票金额 ÷ 收入确认（同一税口径） | BCR | 财 |
| 回款率 | CollectionRate | metric | 回款 ÷ 开票金额 | COLR | 财 |
| 承诺开票超期 | OverdueBillingCommitment | metric | 承诺开票日已过而未开票的金额 | — | 业 |
| 合同资产风险分层 | ContractAssetRiskTier | enum | 近期可转化、长期挂账、高风险；规则为口径参数 | — | 财 |
| 收入冲减 | RevenueReduction | event | 可变对价导致的收入减少（D17-8），区别于信用损失 | — | 财 |
| 信用损失准备 | CreditLossAllowance | metric | 应收与合同资产的减值准备余额（科目名「坏账准备」）；区别于减值分录（事件） | IMP | 财 |

### N4. 组织与人员（`organization.*`）

| 中文 | 英文 | 类别 | 定义（草稿） | OAT | 确认 |
|---|---|---|---|---|---|
| 平均在册人数 | AverageHeadcount | metric | 期间内在册人数的平均值；口径见 B2；所有「人均」的统一除数 | AVG_HEADCOUNT | 业 |
| 离职率 | AttritionRate | metric | 离职人数 ÷ 平均在册人数，分主动、被动 | EMPLOYEE_SEPARATIONS ÷ AVG_HEADCOUNT | 业 |
| 组织层级 | OrgUnitLevel | enum | 区域、事业部、交付中心、客户服务中心等组织单元的层级与类型（K14）；层级名由组织维度设计确定 | — | 业 |

### N5. 商机与客户（`presales.*`）

| 中文 | 英文 | 类别 | 定义（草稿） | OAT | 确认 |
|---|---|---|---|---|---|
| 客户集团 | CustomerGroup | entity | 同一实际控制方下的客户集合 | — | 业 |
| 客户等级 | CustomerTier | enum | A、B、C、黑名单；按合同额占比、利润率、应收周转天数动态评定 | — | 业 |
| 商机管道金额 | PipelineValue | metric | 未关闭商机的金额（可加权） | PIPE | 业 |
| 赢单率 | WinRate | metric | 赢单 ÷（赢单 + 丢单）（用户 B6） | WR | 业 |
| 线索转化率 | LeadConversionRate | metric | 转为商机的线索 ÷ 线索 | CONV | 业 |
| 业务线 | BusinessLine | enum | 幕墙、照明、品控、海外等 | — | 业 |
| 外部参考数据集 | ExternalReferenceDataset | entity | 土拍、规划许可证、宏观、竞对等外部数据，登记来源与维护人（用户 B10） | — | 业 |

### N6. 会计与交付（`accounting.*`、`delivery.*`）

| 中文 | 英文 | 类别 | 定义（草稿） | OAT | 确认 |
|---|---|---|---|---|---|
| 费用支付 | ExpensePayment | metric | 按支付日计的费用付款（收付口径），与「费用（权责）」对照 | — | 财 |
| 账簿 | Ledger | entity | 收付账簿（法定）与权责账簿（ADR-0022） | — | 财 |
| 启动会完成率 | KickoffCompletionRate | metric | 已开启动会 ÷ 应开启动会 | — | 业 |
| 内审覆盖率 | InternalReviewCoverage | metric | 已完成内审的项目 ÷ 已交付项目（用户 B11：与「内审完成率」合一） | — | 业 |

## D. 已裁定为方言、待目标词条入表后登记

| 方言 | 正式用语 | 依据 |
|---|---|---|
| 毛利、毛利率（含交付毛利、产值毛利、BU 毛利等变体） | 贡献毛利、贡献毛利率（N1） | 用户 Q6-2 |
| 产工比（含产值产工比、开票产工比、回款产工比） | 人工成本产出倍数（N1） | 用户 B8 |
| 逾期（指开票后超 N 天时） | 账龄超 N 天应收（N3）；「逾期」只指过了到期日 | 用户 Q6② |
| 预实比 | 目标达成率（N2）或经营偏差 | 报表盘点 K11 |
