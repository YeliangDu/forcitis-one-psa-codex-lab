> **状态（2026-10-14）**：G-8 至 G-29 全部经业务 Admin 在用语确认页上同意，原叫法归为方言。

# 统一语言「中英文 + 行业 + 规范标准」对齐审计

日期：2026-10-14 · 对象：`GLOSSARY.md` 的 38 个核心术语，以及财务关键 BC 的附录术语 · 机器可读结果：`docs/glossary/terms.yaml`（由 `glossary/build_terms.py` 生成）

## 命名标准的优先级

1. **法规与会计准则**。中文用《企业会计准则》原文用语，例如 CAS 14 收入中的合同、履约义务、交易价格、合同资产、合同负债、应收账款、合同履约成本、合同取得成本、时段履约 / 时点履约、履约进度、投入法 / 产出法。英文用 IFRS 15 / IAS 以及 IFRS XBRL taxonomy（`ifrs-full`）的标准用语，例如 `ContractAsset`、`ContractLiability`、`PerformanceObligation`、`TransactionPrice`、costs to fulfil a contract。
2. **行业与工程标准**。中文用国标：GB/T 50326 建设工程项目管理规范、GB/T 50358 建设项目工程总承包管理规范、GB/T 50500 工程量清单计价标准（工程变更、现场签证、索赔、质量保证金、竣工结算），以及《建设工程质量保证金管理办法》。英文用 PMI PMBOK / AACE（WBS、WorkPackage、EstimateAtCompletion、EstimateToComplete、EarnedValue、PlannedValue、ActualCost、Baseline）和 FIDIC（Variation、Claim、Retention Money、Interim Payment Certificate）。
3. **专业服务 / PSA 行业通行写法**（Timesheet、TimeEntry、Utilization、Billable、RateCard、Engagement、WIP）。只在准则和国标都没有覆盖时采用。
4. **公司内部用语只作别名**（`aliases`），不作代码名。禁止使用拼音和自造缩写。

条文号只在把握高时写出；其余只写到准则或标准名一级，并在 `terms.yaml` 中用 `confidence` 标注把握程度。

## 1. 审计范围与结论

| 项目 | 数量 |
|---|---|
| 审计的术语行 | **255**：财务关键 BC 的附录术语 217 行（`operations.facts`、`operations.policy`、`contract.*`、`delivery.*`、`cost.*`、`receivables.*`、`accounting.ledger`），加上 38 个核心术语（其中 24 个落在上述 BC，与附录有重叠）；38 个核心术语全部覆盖 |
| `terms.yaml` 条目 | **293**（拆开「A / B」写法、合并同义行后的结果，另新增 31 条准则或 EVM 中缺位的术语，其中 `AccountingPeriod`、`RateCard` 与核心术语合并） |
| 状态 | approved 233 · proposed 60 |
| 新改名建议 | **G-8 至 G-29，共 22 条**（下表）。G-1 至 G-7 已在 `GLOSSARY.md` 中，本次没有重复 |
| 需要财务确认 | G-8、G-9、G-10、G-11、G-12、G-13、G-16、G-17、G-20、G-21、G-27 |
| 需要业务确认 | G-19、G-20、G-22、G-26 |

**审计后保留原名的术语（符合标准，不为改而改）**：
- 合同与变更：`Contract`、`ChangeOrder`（同时标注别名 FIDIC Variation 和 IFRS 15 contract modification）、`Claim`、`ClaimTimeBar`、`FixedPrice` / `TimeAndMaterials` / `CostPlus`、`RateCardItem`、`FrameworkAgreement`。
- 交付：`WbsNode`、`Deliverable`、`Milestone`、`PlanBaseline`、`EstimateToComplete`、`EacAssessment`（定义 EAC = AC + ETC，与 PMBOK 一致）、`AcceptanceRecord`、`ConcessionAcceptance`（ISO 9000 让步）。
- 工时与成本：`Timesheet` / `TimeEntry` / `BillableHours`、`DirectLabor`、`IndirectCostPool`、`StandardCostRateCard`。
- 收入与应收：`InputMethod` / `OutputMethod`、`InvoiceIssued`、`ArItem`、`CashApplication`、`Aging`、`EclAlert`、`ArWrittenOff`。
- 核算：`JournalVoucher`、`FunctionalCurrency`、`CurrencyTranslation`、`RelatedPartyTransaction`、`StatutoryRevenueBooked`、`ImpairmentBooked`。

### 1.1 WIP 与合同资产的区别（G-8、G-9、G-10 的依据）

老词表把一个英文词 WIP 用在三个不同的概念上：

| 概念 | 口径 | 计量 | 是否可能为负 / 无合同 | 建议代码名 |
|---|---|---|---|---|
| 未开票在制（回款侧 WIP） | **管理口径**（PSA 行业的 unbilled WIP） | 累计履约进度收入 − 累计已开票，按项目 × 条款计量 | 包含无合同部分（`UncontractedWip`），不计提减值 | `UnbilledWip` |
| 合同资产 | **准则口径**（CAS 14、IFRS 15 §105-109，`ifrs-full:ContractAssets`） | 按合同列示的净额：有权收取对价、且该权利取决于时间流逝以外的条件；同一合同内与合同负债抵销后列报；按预期信用损失（CAS 22 / IFRS 9）计提减值 | 必须先有满足 CAS 14 合同成立条件的合同；净额为负时列为**合同负债** | `ContractAsset` / `ContractLiability` |
| 成本侧 WIP | **准则口径**的资产（CAS 14「合同履约成本」，IFRS 15 §95-98） | 已发生、尚未随收入转出的履约成本，收入确认时转入营业成本 | 与收入方向无关 | `ContractFulfilmentCost` |

管理口径与准则口径的勾稽关系：未开票在制 − 无合同部分 − 被合同负债抵销的部分 − 减值准备 ≈ 报表中的合同资产（另需加上长期应收部分，见 REC-R02）。这一关系应作为 `operations.policy` 的一条勾稽等式落地。

## 2. 改名与补词建议（接着 G-7 编号）

「影响」一列指新仓库中需要改名的模块，以及迁移时要映射的老系统表。

| # | 现名（中 / 英） | 建议名（中 / 英） | 依据标准 | 影响的模块与老表 | 确认 |
|---|---|---|---|---|---|
| **G-8** | 合同资产 / `WipAccount`；减值预警 `WipImpairmentRiskRaised` | 合同资产 / `ContractAsset`；`ContractAssetImpairmentRiskRaised` | CAS 14；IFRS 15 §105-109；`ifrs-full:ContractAssets`；CAS 22 / IFRS 9（ECL） | `receivables.wip`、`operations.policy`（F113 滚动恒等式）、`accounting.ledger` 科目映射；`psa_pm_contract_asset_impairment`、`psa_wip_period_balance` | **财务**：合同资产按合同净额列示，并与合同负债抵销 |
| **G-9** | WIP / `Wip`（「合同资产语义」） | 未开票在制（WIP）/ `UnbilledWip`；`UncontractedWip` 保留原名，并明确它永远不进入 `ContractAsset` | 管理口径，依据 PSA 行业写法；与 CAS 14 合同成立条件的边界 | `receivables.wip`、报表读模型、KPI F113 / F164；`psa_wip_*`（回款侧视图） | **财务**：§1.1 的勾稽等式；REC-R01 中期初合同资产的口径 |
| **G-10** | 合同履约成本 / 成本 WIP / `CostWip` | 合同履约成本 / `ContractFulfilmentCost`；补入 `CostOfSales`（营业成本，原「主营业务成本」）、`CostsToObtainContract`（合同取得成本） | CAS 14（合同履约成本、合同取得成本）；IFRS 15 §91-98；`ifrs-full:CostOfSales` | `cost.costing`、`accounting.ledger`；`psa_wip_entry`、`psa_wip_movement`、`psa_wip_period_balance`、`psa_wip_cost_account`、`psa_wip_commission` | **财务**：①「合同前投入」（WIP 阶段 1）是否满足「与预期取得的合同直接相关」的资本化条件；② 居间佣金是否作为合同取得成本资本化（`psa_wip_commission` 现在混在 WIP 中） |
| **G-11** | （缺）预收款 / 递延收入 | 合同负债 / `ContractLiability` | CAS 14；IFRS 15 §106；`ifrs-full:ContractLiabilities` | `receivables.wip`、`receivables.collection`（预收款）、`accounting.ledger`；老系统未单列（REC-R03） | **财务**：预收款和超额开票按合同与合同资产抵销后的列报规则 |
| **G-12** ✔ 用户已裁定 | 收入确认准备（产值）/ `ProgressRecognized` | **收入确认单** / `RevenueRecognition`（单据），事件 `RevenueRecognized`；`StatutoryRevenueBooked` 不变；「产值确认单」「产值」归为**方言**（用户 2026-10-14：「产值确认单就是收入确认单，系统用收入确认这个语言，现在的叫法可以归为方言」） | CAS 14「确认收入」；`ifrs-full:RevenueFromContractsWithCustomers` | `receivables.wip`、`accounting.ledger`、`operations.facts`；`psa_pm_cuvalue`、`psa_revenue_confirmation`、`psa_revenue_fact`、`psa_opening_revenue_event` | **财务**：ACC-R01 已把客户价值确认单定为收入确认的唯一触发点，所以「准备」二字不再成立；需确认业务侧收入事实与法定入账两个名字的分工 |
| **G-13** | 履约进度 / 完工百分比 / `Poc`、`PercentageOfCompletion` | 履约进度 / `MeasureOfProgress`；补入 `SatisfiedOverTime`（时段履约）、`SatisfiedAtPointInTime`（时点履约）；`POC` 只保留为 KPI 符号 | CAS 14、IFRS 15 §35-45（「完工百分比」是已被取代的 IAS 11 / CAS 15 建造合同用语） | `receivables.wip`、`delivery.execution`（DLV-R09 的三个 POC 口径）、KPI F039 至 F041；`psa_pm_project.progress_pro` | **财务**：词表写「T&M 用投入法、固定总价用产出法」，而 ACC-R01 / R02 定稿为「统一产出法，投入法只作过程参考」，两者冲突，需要裁定 |
| **G-14** | 结转批次 / `CarryBatch`（含 `CarryForwardData`） | 成本结转批次 / `CostTransferBatch` | 企业产品成本核算制度（结转 = transfer）；英语中的 carry forward 指余额结转到下期，语义不符 | `cost.costing`；`psa_carry_batch`、`psa_carry_batch_line`、`psa_carry_batch_alive*`、`psa_wip_carry_forward_data*`，以及 `carry_type` 枚举 | 不需要（只是改名） |
| **G-15** | 损益台账 / `PlLedger` | 损益台账 / `ProfitOrLossLedger` | IAS 1「profit or loss」；CAS 30；Pl 属于自造缩写 | `cost.costing`；`psa_pl_ledger`、`psa_pl_ledger_reversal_once`、`psa_pl_ledger_wip_*` | 不需要 |
| **G-16** | 期间封闭 / 封期 / `PeriodClose`（同时出现在 `cost.costing` 和 `receivables.wip`）；`BusinessPeriod` 与 `AccountingPeriod` 并存 | 期末关账 / `PeriodClose` 只归 `accounting.period`，配套 `PeriodStatus`、`ReopenAccountingPeriod`；成本和 WIP 侧改为关账前置检查 `PeriodCloseReadiness`；红冲统一为冲销 / `ReversingEntry`；补入 `PostingCandidate` | 会计基础工作规范（结账）；ADR-0016（期间状态只由一个 BC 写） | `cost.costing`、`receivables.wip`、`accounting.period`、`operations.facts`；`psa_period_progress`、`psa_carry_period_progress`、`mdm_accounting_period`、`psa_accounting_period` | **财务**：期间重开政策；`BusinessPeriod`（经营期间）是否与（期间, 法人）同粒度 |
| **G-17** | 质保金 / `Retention`；质保金到期 / `RetentionDue`；「质保期」 | 质量保证金（别名质保金）/ `RetentionMoney`；`RetentionMoneyReleaseDue`；缺陷责任期 / `DefectsLiabilityPeriod` | 建设工程质量保证金管理办法（建质〔2017〕138 号）；GB/T 50500；FIDIC 2017 Cl.14 Retention Money；裸 `Retention` 与 `java.lang.annotation.Retention`、日志保留等冲突 | `receivables.collection`、`contract.baseline`（质保条款）、`delivery.execution`（后期服务）；老系统没有专表 | **财务**：未到期质保金应列为合同资产还是应收账款（取决于收款权是否只取决于时间流逝） |
| **G-18** | 成本归集 / `CostAllocated` | 成本归集 / `CostAccumulated` | 成本核算用语：归集 = accumulation，分配 / 分摊 = allocation；AACE 10S-90 | `cost.costing` 事件及下游消费方 | 不需要 |
| **G-19** | 竣工结算、结算单 / `SettlementRecord`（两个中文名对应一个代码名）；结算折扣 `SettlementDiscount` | 竣工结算 / `FinalAccount`；结算折让 / `FinalAccountDiscount`；补入期中支付证书（进度款）/ `InterimPaymentCertificate` | GB/T 50500（竣工结算、进度款）；FIDIC 2017 Cl.14 Final Statement / IPC；英语中 Settlement 与 `ClaimSettled`、`IntercompanySettlement` 冲突 | `receivables.wip`（ADR-010） | **业务**：咨询合同是否采用「竣工结算」口径；结算折让在核算上作为交易价格调整（**财务**） |
| **G-20** | 罚款 / `Penalty` | 违约金 / `LiquidatedDamages`；补入可变对价 / `VariableConsideration` | 民法典第五百八十五条（违约金）；FIDIC Cl.8.8 Delay Damages；CAS 14 / IFRS 15（可变对价）；「罚款」在中文法律语境中指行政处罚 | `contract.changeorder`、`receivables.collection`（贷项）；KPI 符号 PEN | **业务 + 财务**：扣罚是否包括质量扣款；是否从交易价格中扣减 |
| **G-21** | 合同条款 / `ContractClause`（同时充当收入、成本、开票、回款的挂接锚点和付款节点） | 保留 `ContractClause`，并补入履约义务 / `PerformanceObligation`、交易价格 / `TransactionPrice`、合同变更（会计口径）/ `ContractModification`、剩余履约义务 / `RemainingPerformanceObligation`，建立「条款 → 履约义务」映射 | CAS 14 五步法；IFRS 15；`ifrs-full:TransactionPriceAllocatedToRemainingPerformanceObligations` | `contract.baseline`、`receivables.wip`、`cost.costing`；`psa_contract_clause`、`psa_pm_contract_term` | **财务**：一个条款是否就是一个履约义务（ACC-R08 规定收入挂在付款节点上，而准则规定收入挂在履约义务上） |
| **G-22** | 任务包 / 交付包 / `WorkPackage` / `DeliveryPackage`（在 03 词表中是同一行的两个名字） | 工作包 / `WorkPackage`，「任务包」只作别名；`DeliveryPackage` 待确认后保留或并入 | PMBOK、PMI WBS 实践标准（工作包 = WBS 最底层） | `delivery.execution`、`delivery.acceptance`（验收对象类型）；`psa_task_package`、`psa_budget_task_package` | **业务**：交付包与工作包是否是同一个概念 |
| **G-23** | 进度偏差 / `ProgressDeviationIdentified`；EVM 指标缺位 | `ScheduleVarianceIdentified`；补入 `EstimateAtCompletion`、`PlannedValue`、`EarnedValue`（挣值，GB/T 50358 称赢得值）、`ActualCost`、`CostPerformanceIndex`、`SchedulePerformanceIndex`、`ScheduleVariance`、亏损合同 / `OnerousContract` | PMBOK、PMI EVM 实践标准、GB/T 50358；CAS 13 / IAS 37（亏损合同） | `delivery.execution`、`operations.policy`（KPI F175） | 不需要（亏损合同的计提口径留给财务另议） |
| **G-24** | 成本预算 / 成本偏差 / `CostVariance`（定义为预算对实际） | 成本预算偏差 / `CostBudgetVariance`；EVM 的 CV 另立为 `EarnedValueCostVariance` | PMBOK：CostVariance 专指 CV = EV − AC | `cost.costing`、KPI BURN_STD | 不需要 |
| **G-25** | 内部往来对销 / `IntercompanyElimination` | 内部往来**抵销**（只改中文） | CAS 33 原文用「抵销」 | `accounting.ledger` | 不需要 |
| **G-26** | 签证 / `SiteVisa` | 现场签证（中文改为国标原文）；英文 `SiteVisa` 暂时保留（这是业主确认的中国 AEC 方言，FIDIC 中没有对等术语，`confidence: low`） | GB/T 50500「现场签证」 | `contract.changeorder` | **业务**：英文是否保留 SiteVisa，或改为 SiteInstruction 之类的写法 |
| **G-27** | 风险敞口 / `RiskExposure`（定义为 NR + CA + AR 的**无合同**敞口） | 无合同风险敞口 / `UncontractedExposure` | 名字应与定义一致；按 CAS 14，无合同时不存在准则口径的合同资产，所以 CA 段应改为未开票在制 | `contract.riskdelivery`、KPI F127A | **财务**：F127A 中 CA 段的口径 |
| **G-28** | 间接成本池 `IndirectCostPool` 与分摊 `AllocateOverhead` 用了两个词根（OH） | `IndirectCostPool` 与分摊命令 `AllocateIndirectCost` 统一为同一词族；OH 只作别名 | 企业产品成本核算制度（间接费用）；US CAS 418 | `cost.costing`；`psa_oh_cost_pool*`、`psa_oh_allocation_*`、`psa_cost_allocation*` | 不需要（分摊只是管理口径、不过账，COST-R42 不变） |
| **G-29** | 实体只有事件名或缺位：任务订单 `TaskOrderRegistered`、合同基线 `ContractBaselined`；核心术语「项目」「项目预算」不在 03 词表中 | 补实体 `TaskOrder`、`ContractBaseline`（事件名保留）；补入 `Project`、`ProjectBudget` | 03 词表的命名规则（事件主语与聚合名一致）；PMBOK | `contract.baseline`、`delivery.execution`；`psa_pm_project` | 不需要 |

## 3. 需要财务确认的问题清单（按影响排序）

1. 收入确认方法：词表写的是投入法和产出法并存，ACC-R01 / R02 写的是统一产出法。需要裁定以哪一个为准（G-13）。
2. 合同条款与履约义务是否一一对应；收入挂在付款节点上是否合规（G-21）。
3. 合同资产与合同负债按合同抵销后列报；未开票在制与合同资产的勾稽等式（G-8、G-9、G-11）。
4. 合同前投入能否作为合同履约成本资本化；居间佣金是否作为合同取得成本（G-10）。
5. 未到期质保金的列报科目（G-17）。
6. 违约金、扣款、结算折让是否作为可变对价冲减交易价格（G-19、G-20）。
7. 期间重开政策；经营期间与会计期间是否同粒度（G-16）。
8. 无合同敞口中 CA 段的口径（G-27）。

## 4. 发现的不一致与缺口（不在改名范围内）

- `08-FORCITIS Operating Accounting Taxonomy.md` 的正本是 `08-财务原语.md`，但这个文件以及 `forcitis_operating_accounting_taxonomy.csv`、`standard_concept_mapping.csv` 都不在本机仓库中。所以本次审计无法对照 OAT 已有的原语映射，`terms.yaml` 中的 `standard_ref` 是直接依据准则写的。拿到正本后需要做一轮比对。
- 03 词表说「全库唯一 WIP」，但回款子域规则（09 §7）明确写着回款侧 WIP 与成本侧 WIP「同名异物」。G-9 和 G-10 拆分后，这个矛盾消除。
- `OperatingAccountingTaxonomy` 的定义提到 US GAAP / ASC 606，而 ACC-R12 要求科目用中国准则原词。建议 OAT 以 CAS / IFRS 为主，把 ASC 606 作为映射参考。
- KPI 符号（POC、CA、PEN、RET 等）是公式符号，不是代码名。`terms.yaml` 把它们列为 `kpi-symbol` 别名，KPI 口径层可以继续使用。
