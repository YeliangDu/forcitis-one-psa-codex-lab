---
type: kpi-system-design
area: operating-metrics
status: active
methodology: ../../../methodology/kpi-system-design-method.md
source_assets:
  - ../../../cases/forcitis-aec-ops/FORCITIS_KPI公式体系_V5.0.md
  - ../../../cases/forcitis-aec-ops/aec-kpi-bc-catalog.csv
  - ../../../cases/forcitis-aec-ops/kpi_fact_source_contracts.csv
  - ../../../cases/forcitis-aec-ops/bc_kpi_observability_matrix.md
  - ../../../cases/forcitis-aec-ops/kpi_validation_report.md
  - ../../../cases/forcitis-aec-ops/forcitis_one_kpi_hierarchy_map.csv
---

# FORCITIS one KPI 体系设计

本文是 FORCITIS one 的经营度量体系 active 标准。它按 [KPI 体系设计方法](../../../methodology/kpi-system-design-method.md) 重构现有 KPI 资产，不再把原 `FORCITIS_KPI公式体系_V5.0.md` 当作顶层方案；原公式库、目录 CSV、事实源契约和验证报告作为语料、公式 baseline 和迁移证据使用。

核心结论：

> FORCITIS one KPI 体系 = 经营体检架构 + 23BC 主权映射 + 权责制口径政策 + 指标观测契约 + 诊断规则 + 可回放证据。

## 1. 顶层设计

### 1.1 设计目标

KPI 体系只回答经营管理问题，不承担业务事实裁定、不替代法定核算、不替代流程审批。

| 目标    | FORCITIS one 标准                                                                        |
| ----- | -------------------------------------------------------------------------------------- |
| 一号位体检 | 能从 L0 经营健康看出盈利、现金、增长、效率、风险哪一类异常                                                        |
| 管理层诊断 | 能把异常下钻到七步价值链和主责 BC                                                                     |
| 执行层动作 | 能把异常转成工单、复盘、准入、冻结、纠偏或激励动作                                                              |
| 口径主权  | 指标口径统一归经营口径与勾稽 BC，事实裁定仍归事实 owner BC                                                    |
| 权责制严谨 | 收入、WIP、AR、Backlog、Book-to-Burn、OP、M* 必须声明 accounting / revenue / input basis           |
| 工程可验  | 每个生产指标都能追溯到 MetricDefinition、PolicyDefinition、Fact Source Contract 和 MetricObservation |

### 1.2 当前资产定位

| 资产 | 新定位 | 使用方式 |
| --- | --- | --- |
| `FORCITIS_KPI公式体系_V5.0.md` | 公式语料和旧 baseline | 保留公式编号作为迁移索引，逐步迁入 active MetricDefinition |
| `aec-kpi-bc-catalog.csv` | 现有机器目录 baseline | 作为迁移来源，旧 accepted KPI 已进入 active 目录种子 |
| `kpi_fact_source_contracts.csv` | 事实源契约种子 | 补齐双时态、会计期间、勾稽目标和证据字段后进入生产级 |
| `bc_kpi_observability_matrix.md` | BC 观察覆盖证据 | 用于检查 23BC 是否被指标观察到，不改变事实主权 |
| `kpi_validation_report.md` | synthetic 级验证证据 | 证明旧目录设计级和 synthetic 回放通过，不证明生产级完成 |
| `forcitis_one_active_kpi_catalog.csv` | active MetricDefinition 种子 | 303 个 accepted、0 个 candidate、2 个 rejected；其中 16 个 active canonical、12 个子域 rollup、275 个 legacy migrated |
| `forcitis_one_active_policy_registry.csv` | PolicyDefinition 种子 | 15 个生产级口径政策、阈值政策、关闭/重述政策和激励冻结政策 |
| `forcitis_one_metric_observation_contracts.csv` | MetricObservation 契约种子 | 303 个 accepted KPI 的双时态观测契约 |
| `forcitis_one_kpi_hierarchy_map.csv` | KPI 责任与下钻层级图 | 119 个层级节点：L0 1、L1 12、L2 23、L3 83 |

### 1.3 完成状态

本设计当前是 active 设计标准，并已有机器可读 active 目录种子。生产级完成必须另行证明：

- `M_star_oper` 已进入 active MetricDefinition 种子，并已具备门序政策和观测契约；生产级仍需接入真实事实源和值回放。
- 漏损瀑布已进入 active MetricDefinition 种子；first-cause 归属、收入当量折算、总额勾稽和去重约束仍需工程实现。
- MetricObservation 契约种子已定义 `as_of`、`knowledge_time`、关闭快照和重述链；生产级仍需生成真实观测值。
- `_oper` / `_stat` 差异项已进入政策和目录字段；生产级仍需按期间、维度、政策版本对账。
- Operational Facts → Project Subledger → GL → Management KPI 的三段勾稽通过。
- `Unexplained_OP_Variance` 在阈值内，或已触发公式模型复核。

## 2. 经营体检架构

### 2.1 四层责任与下钻

| 层级 | 受众 | FORCITIS one 内容 | 主要产物 |
| --- | --- | --- | --- |
| L0 域层经营健康 | 一号位、经营会 | FORCITIS one 经营整体健康：M*、OP、OPM、总漏损率、现金转化、Backlog、关键风险红线 | T1 经营健康页 |
| L1 子域健康 | 管理层、域负责人 | 12 个子域健康指数和子域指标族 | T2 子域健康页 |
| L2 BC 健康 | BC owner、流程 owner | 23 个 BC 的事实、口径、过程和风险指标族 | BC 责任页 |
| L3 原子经营事实 | 产品、工程、数据治理 | 领域事件、事实源、证据字段、观测值快照 | 契约和回放样本 |

责任与下钻方向固定为：

```text
L0 域层经营健康
→ L1 子域健康
→ L2 BC 健康
→ L3 原子经营事实 / 事实源 / 事件
```

五大经营系统、七步价值链和 T1/T2/T3/T4 是横向诊断或展示维度，不占用 L0-L3 的层级语义。也就是说，`diagnostic_system=盈利系统` 可以同时切到 L0、L1、L2 或 L3；它不是 L1 层。

这条四层链路不是强制计算链。L0 指标必须能沿 L1、L2、L3 下钻定位责任和证据，但公式可以直接使用 L3 事实源计算，也可以显式引用 L1 / L2 子指标，具体由 MetricDefinition 的 `formula`、`source_metric_codes`、`fact_sources` 和 policy 版本决定。不能默认把 L0 设计成 12 个 L1 的加权和。

12 个 L1 子域的正式英文名如下。英文名用于 `domain_node_code`、子域健康 KPI、数据目录和系统集成标识。

| 中文子域 | 正式英文名 | 机器标识词根 |
| --- | --- | --- |
| 经营子域 | operations | OPERATIONS |
| 商机子域 | presales | PRESALES |
| 合约子域 | contract | CONTRACT |
| 交付子域 | delivery | DELIVERY |
| 成本子域 | cost | COST |
| 回款子域 | receivables | RECEIVABLES |
| 费用子域 | expense | EXPENSE |
| 采购子域 | procurement | PROCUREMENT |
| 知识子域 | knowledge_management | KNOWLEDGE_MANAGEMENT |
| 组织子域 | organization | ORGANIZATION |
| 核算子域 | accounting | ACCOUNTING |
| 平台子域 | platform | PLATFORM |

### 2.2 五大经营系统

| 系统 | 经营问题 | T1 指标 | 诊断下钻 |
| --- | --- | --- | --- |
| 盈利系统 | 专业服务投入是否转成高质量经营成果 | `M_star_oper`、`OP_oper`、`OPM_oper`、总漏损率、项目毛利率 | 价格、合同边界、交付、成本、减值、OH |
| 现金流系统 | 权责成果是否转成低风险现金 | `Cash_Conversion_Ratio_oper`、`DSO_oper`、`Contract_Asset_Days_oper`、AR90+、质保金逾期率 | 履约确认、开票准入、发票、争议、回款、保证金 |
| 增长系统 | 未来订单是否充足且质量可靠 | `Book_to_Burn_NSR_oper`、Backlog 月数、Pipeline 健康度、复购收入占比 | 商机质量、报价承诺、合同储备、客户结构 |
| 运营效率系统 | 人、分包、知识和管理费用是否有效 | 工时利用率、有效工时率、人均 NSR、直接成本乘数、OH Rate | 人工、分包、项目成本、知识复用、组织杠杆 |
| 风险防控系统 | 是否有拖垮经营的敞口 | 无合同交付敞口、WIP 减值预测、ECL 率、预计亏损项目占比、职业责任成本率 | 合同缺口、质量责任、AR 风险、保险/资质、合规 |

### 2.3 七步价值链

| 价值链步骤 | 管理问题 | 主责 BC | 关键指标族 |
| --- | --- | --- | --- |
| 1. 选对机会 | 哪些客户和商机值得投入 | 客户经营 BC、商机评估与报价承诺 BC | Pipeline 健康度、BD ROI、低胜率商机占比、复购收入占比 |
| 2. 做对承诺 | 报价与承诺是否覆盖交付复杂度 | 商机评估与报价承诺 BC、经营口径与勾稽 BC | 报价毛利、价格实现率、风险调整计划贡献、报价低线触发率 |
| 3. 锁住边界 | 合同、履约义务、变更和例外是否可控 | 合约基线 BC、变更签证索赔 BC、风险交付 BC | 合同完备度、无合同收入占比、变更落地率、范围蔓延率 |
| 4. 生产价值 | 交付是否形成客户认可和可确认价值 | 项目交付执行 BC、质量返工与验收 BC | 产出法进度、EAC 偏差、FPY、返工工时率、验收通过率 |
| 5. 控制漏损 | 价格、范围、质量、效率是否侵蚀利润 | 经营口径与勾稽 BC 牵头，多 BC 供事实 | W1-W7 漏损瀑布、利润侵蚀闭合、执行偏差拆解 |
| 6. 完成变现 | 已履约价值是否可开票、可收回 | WIP 与开票准入 BC、AR 与现金转化 BC | 开票转化率、合同资产天数、DSO、争议 AR、质保金回收率 |
| 7. 归属反馈 | 结果是否回到责任、激励和下一轮计划 | 经营事实与归属 BC、绩效激励 BC、战略与经营计划 BC | 归属完整率、风险调整利润、奖金递延/追索、计划达成差异 |

## 3. T1 经营健康指标

T1 只放经营会必须稳定观察的少数指标。T1 指标可以引用多个 F/X 公式，但对外必须形成一个清晰口径。

| T1 指标 | 类型 | basis | 口径来源 | 配对护栏 | 主要下钻 |
| --- | --- | --- | --- | --- | --- |
| `M_star_oper` | 北极星 / 质量乘数 | performed_value + billable_direct_labor_cost_oper | 新增 active 定义 | 资格率矩阵、低样本量、现金风险门 | 五性门、价值链、BU、服务线 |
| `OP_oper` | 结果 | recognized_accrual / management_forecast | F002/F008 迁移 | OCF、WIP/AR 减值、勾稽差异 | OP bridge、OH、减值、直接成本 |
| `OPM_oper` | 结果 | nsr_accrual | F009/F013 迁移 | `NSR_accrual`、`Unexplained_OP_Variance` | BU、项目、服务线、客户 |
| `Total_Loss_Rate_oper` | 诊断 | potential_revenue_equivalent | F030F/F099/F102 迁移 + 新 first-cause 规则 | 漏损去重率、归属完整率 | W1-W7 漏损序列 |
| `Cash_Conversion_Ratio_oper` | 结果 | collected / recognized_accrual | F127/X029 迁移 | 争议 AR、客户满意、催收合规 | 现金漏斗 |
| `DSO_oper` | 结果 | receivable | F119 迁移 | 争议率、ECL、客户风险 | AR Aging、客户、合同 |
| `Contract_Asset_Days_oper` | 领先 | contract_asset | F118/F163-F168 迁移 | 开票准入质量、风险交付敞口 | WIP aging、项目、合同 |
| `Book_to_Burn_NSR_oper` | 领先 | booked_nsr / recognized_nsr | X020 迁移 | 新签质量、合同完备度、低毛利准入 | Backlog 分层、Pipeline |
| `Backlog_Months_NSR_oper` | 领先 | contracted / funded backlog | X003/X004 迁移 | 未批准索赔不并入正式 backlog | 合同状态、服务线、BU |
| `Effective_Hours_Rate_oper` | 领先 / 护栏 | billable_direct_labor_hours | F030L/F174 迁移 | 返工率、项目毛利、过载预警 | 员工、项目、角色 |
| `At_Risk_Delivery_Exposure_oper` | 风险 | at-risk performed value | F127A/F127B/X026 迁移 | 风险交付审批率、关闭 SLA | 合同缺口、无授权交付 |
| `Reconciliation_Exception_Rate_oper` | 数据质量 / 护栏 | management vs statutory | F151-F159A 迁移 | T1 降级展示、close 状态 | 口径差异、事实源、期间 |

### 3.1 `DSO_oper` 字段级血缘样例

`DSO_oper` 是首个同时显式登记 `formula_value_objects`、`formula_source_fields` 和 `formula_evidence_fields` 的 T1 指标，用于说明生产级指标不得只停留在 fact source 级血缘。

```text
DSO_oper = Average_AR_oper / Daily_Billed_NSR_oper
```

| 公式分量 | fact source | 参与计算的聚合 / 实体 / 值对象 | 计算字段 | 证据字段 |
| --- | --- | --- | --- | --- |
| `Average_AR_oper` | `ar_fact` | `ArItem`、`ReceivableType`、`PaymentTerm`、`DueDate`、`AgingBucket`、`DisputeFlag`、`CashChainStatus`、`ArItemStatus` | `ar_item_id`、`invoice_id`、`customer_id`、`contract_id`、`period`、`open_ar_balance_amount`、`ar_begin_amount`、`ar_end_amount`、`receivable_type`、`due_date`、`aging_bucket`、`dispute_flag`、`currency_code`、`receiving_legal_entity_id`、`ar_item_status` | `source_event_id`、`evidence_ref`、`invoice_id`、`recorded_at`、`recorded_by`、`source_system`、`policy_version_ref` |
| `Daily_Billed_NSR_oper` | `billing_fact` | `BillingRequest`、`BillingBasisType`、`EligibilityResult`、`BillingRequestStatus` | `billing_request_id`、`invoice_id`、`customer_id`、`contract_id`、`period`、`invoice_issue_date`、`billed_nsr_amount`、`billing_basis_type`、`eligibility_result`、`currency_code`、`contracting_legal_entity_id`、`billing_request_status` | `source_event_id`、`evidence_ref`、`billing_request_id`、`invoice_id`、`recorded_at`、`recorded_by`、`source_system`、`policy_version_ref` |
| AR 余额滚动校验 | `cash_receipt_fact` | `CashReceipt`、`AllocationEntry`、`CashReceiptStatus`、`ReceivingLegalEntity`、`Currency` | `cash_receipt_id`、`receipt_id`、`invoice_id`、`customer_id`、`contract_id`、`period`、`receipt_date`、`receipt_amount`、`applied_amount`、`unapplied_amount`、`currency_code`、`receiving_legal_entity_id`、`cash_receipt_status` | `source_event_id`、`evidence_ref`、`cash_receipt_id`、`receipt_id`、`invoice_id`、`recorded_at`、`recorded_by`、`source_system`、`policy_version_ref` |

`cash_receipt_fact` 不直接构成 DSO 分母，但通过 `AllocationEntry` 核销应收、更新 AR 余额，是 `Average_AR_oper` 可信计算的必要输入。

字段级仍不是物理数据库实现的唯一形态。这里的字段名代表可审计的源字段，可以落在领域事件 payload、读模型列、事实表字段或外部系统接口字段中；生产级验证以 `formula_source_fields` 和 `formula_evidence_fields` 是否可回放为准。

完整实时计算说明见 [DSO_oper字段级实时计算说明](DSO_oper字段级实时计算说明.md)。

## 4. M* 北极星规格

### 4.1 目标

`M_star_oper` 衡量“高质量、可归属、可变现的专业服务产出”相对于直接投入的转化效率。它不是法定收入指标，不替代 `REV_stat`、`NR_stat`、`OP_stat`。

### 4.2 最小粒度

`M_star_oper` 的最小粒度：

```text
contract_id
+ performance_obligation_id
+ project_id
+ service_line_id
+ operating_period
+ as_of
```

简单项目可以退化为 `project_id + period`，但系统模型必须保留履约义务粒度。

### 4.3 公式

```text
M_star_oper = Qualified_Performed_Value_oper / Direct_Input_oper
```

其中：

```text
Qualified_Performed_Value_oper
= sum(Performed_Value_unit where first_failed_gate is null)
```

`Direct_Input_oper` 默认使用 `billable_direct_labor_cost_oper`，用于与专业服务行业净人工乘数桥接。若管理层要看全投入口径，必须另设展示指标：

```text
M_star_full_input_oper
= Qualified_Performed_Value_oper / Direct_Input_full_oper
```

两者不得混名。

### 4.4 五性门序

五性门固定按顺序判定。每个单位价值只在第一个未通过门被扣减，禁止重复扣减。

| 门序 | 五性 | 通过条件 | 主权 BC | 事实来源 |
| --- | --- | --- | --- | --- |
| G1 | 客户认可 | 已验收、阶段确认或客户认可证据满足政策 | 质量返工与验收 BC | `quality_fact`、验收事件、客户反馈 |
| G2 | 合同支持 | 合约基线、变更或风险交付授权覆盖 | 合约基线 BC、变更签证索赔 BC、风险交付 BC | `contract_baseline_fact`、`change_order_fact`、`risk_delivery_fact` |
| G3 | 财务可确认 | 满足履约义务收入确认政策，或标记为经营预测 | 财务总账与法定核算 BC | `revenue_fact`、`gl_transaction_fact`、PolicyDefinition |
| G4 | 现金可回收 | 点时负向筛查通过：无超阈逾期、无重大争议、客户风险不高于阈值 | AR 与现金转化 BC | `ar_fact`、`cash_receipt_fact`、客户风险状态 |
| G5 | 责任可归属 | 项目、合同、客户、组织、责任人归属完整 | 经营事实与归属 BC | `business_fact_ledger`、`dimension_tables` |

G4 采用点时负向筛查，不等待实际回款成熟。若需观察真实现金成熟结果，另设：

```text
M_star_matured_oper = Matured_Qualified_Performed_Value / Direct_Input_oper
```

`M_star_matured_oper` 必须声明 `maturation_window`，例如 180 天，不得覆盖当期 `M_star_oper`。

### 4.5 与利润乘式的桥接

经典利润乘式使用：

```text
Π = L(U * M - 1 - b)
```

在 FORCITIS one 中：

| 符号 | 经营含义 | KPI 映射 |
| --- | --- | --- |
| `L` | 总人工成本或直接人工基数 | `labor_fact` / `payroll_fact` |
| `U` | 计费利用率 | F022/F023A/F070 |
| `M` | 净收入相对计费直接人工的乘数 | F019/F026/F027 |
| `b` | OH 和减值等负担率 | F011/F012/F012A/F012B/F028 |
| `M*` | 质量过滤后的 performed value 乘数 | `M_star_oper` |

`M*` 用于解释收入质量，不替代 `M`。两者差异进入：

```text
M_Quality_Difference_oper
= M_raw_oper - M_star_oper
```

该差异按五性门和价值链归因。

## 5. 漏损瀑布

### 5.1 设计原则

漏损瀑布只计经济损失，不计时间性延迟。开票慢、回款慢、质保金占用进入现金流漏斗；只有预计减值、折扣、核销、不可回收、不可主张等经济损失进入漏损总额。

总漏损必须满足：

```text
Total_Loss_Amount_oper = sum(Loss_Amount_W1 ... Loss_Amount_W7)
Total_Loss_Rate_oper = Total_Loss_Amount_oper / Potential_Revenue_Equivalent_oper
```

同一损失事实只按 first-cause 归入一个 W 序列，禁止在 W1-W7 之间重复扣减。

### 5.2 分母

```text
Potential_Revenue_Equivalent_oper
= sum(Standard_Rate_Card_Value for eligible performed or planned value units)
```

分母必须引用 `rate_card_policy_id`，并声明服务线、角色、职级、地区、币种和生效期间。

### 5.3 七段漏损

| 序列 | 漏损类型 | 经济折算 | first-cause 主责 BC | 候选旧公式 |
| --- | --- | --- | --- | --- |
| W1 | 选错机会 / 定价漏损 | 标准价值与可支持报价价值差额 | 商机评估与报价承诺 BC | F030A/F030B/F108/F184-F186 |
| W2 | 合同边界漏损 | 未被合同、变更或授权覆盖的价值不可主张部分 | 合约基线 BC | F127A/F127B/F169/X026 |
| W3 | 变更签证索赔漏损 | 可主张变更与批准变更之间的不可回收差额 | 变更签证索赔 BC | F100/F170-F172 |
| W4 | 质量返工漏损 | 返工、质量责任、职业责任导致的成本或赔付 | 质量返工与验收 BC | F030Q/F060A/F102B/X023 |
| W5 | 投入效率漏损 | 无效工时、浪费性直接费用、效率偏差折算 | 工时与投入有效性 BC、项目成本归集 BC | F053B/F060B/F174/F178 |
| W6 | WIP / 合同资产经济漏损 | 不可开票、不可确认或预计减值金额 | WIP 与开票准入 BC | F116B/F163-F168A/F179 |
| W7 | AR / 现金回收经济漏损 | ECL、争议折扣、坏账、质保金不可释放金额 | AR 与现金转化 BC | F116C/F123-F125/F181/F182 |

总漏损率必须与项目毛利率、`OP_oper`、`IMP_WIP_oper`、`IMP_AR_oper` 和 `Unexplained_OP_Variance` 做交叉勾稽。

## 6. 现金流漏斗

现金流系统按状态漏斗设计，避免把权责利润和现金约束混在一个公式里。

```text
Performed Value
→ Recognized Revenue
→ Billable Amount
→ Invoiced AR
→ Collectible Cash
→ Collected Cash
```

| 阶段 | 经营问题 | KPI | 主责 BC |
| --- | --- | --- | --- |
| Performed → Recognized | 已履约价值能否确认收入 | Revenue Recognition Lag、G3 通过率 | 财务总账与法定核算 BC |
| Recognized → Billable | 已确认收入能否开票 | Contract Asset Days、WIP Aging、开票准入通过率 | WIP 与开票准入 BC |
| Billable → Invoiced | 可开票金额是否及时开票 | Billing Cycle Days、开票转化率 | WIP 与开票准入 BC |
| Invoiced → Collectible | 发票是否存在争议或信用风险 | Disputed AR、ECL 率、客户风险等级 | AR 与现金转化 BC |
| Collectible → Collected | 可回收金额是否到账 | DSO、回款预测偏差、现金转化率 | AR 与现金转化 BC |
| Guarantee / Bond Lockup | 保证金、履约保函是否占用现金 | 保证金占用天数、释放率 | 合约基线 BC、AR 与现金转化 BC |

## 7. 23BC 指标主权映射

本表定义 L2 指标族的主责边界。`经营口径与勾稽 BC` 负责指标口径和跨域解释，表内 BC 负责事实是否成立。

| 子域 | BC | 核心经营问题 | L2 指标族 | 关键事实源 |
| --- | --- | --- | --- | --- |
| 经营 | 战略与经营计划 BC | 目标、计划与经营现实是否闭环 | 计划达成、目标差异、滚动预测偏差 | `strategy_plan_fact`、`budget_fact` |
| 经营 | 预算管控 BC | 预算是否约束投入和费用 | 直接成本预算偏差、OH 预算偏差、预算消耗率 | `budget_fact` |
| 经营 | 经营事实与归属 BC | 事实是否归属到责任对象 | 归属完整率、经营事实台账覆盖率、责任归属差异 | `business_fact_ledger`、`dimension_tables` |
| 经营 | 经营口径与勾稽 BC | 指标口径和跨域差异是否可解释 | 勾稽差异、双口径差异、公式残差、口径覆盖率 | `metric_catalog`、`policy_param` |
| 商机 | 客户经营 BC | 客户质量能否支撑增长和回款 | 复购收入、新客户收入、客户集中度、客户风险 | `customer_fact`、`client_feedback_fact` |
| 商机 | 商机评估与报价承诺 BC | 前端是否选对机会、做对承诺 | BD ROI、赢单率、Pipeline 健康度、报价毛利、低胜率商机占比 | `opportunity_fact`、`pricing_fact` |
| 合约 | 合约基线 BC | 合同边界和履约义务是否清楚 | 合同完备度、Backlog、合同签订及时率、例外关闭率 | `contract_baseline_fact`、`backlog_fact` |
| 合约 | 变更签证索赔 BC | 变更和索赔能否转成可收回价值 | 变更落地率、范围蔓延率、索赔发生率、变更回收率 | `change_order_fact`、`claim_fact` |
| 合约 | 风险交付 BC | 无合同或未授权交付是否受控 | 风险交付敞口、风险交付关闭率、无合同收入占比 | `risk_delivery_fact` |
| 交付 | 项目交付执行 BC | 进度、EAC 和执行偏差是否预警亏损 | SPI、EAC 上调、预计亏损项目占比、执行偏差 | `project_progress_fact`、`project_eac_fact` |
| 交付 | 质量返工与验收 BC | 质量、返工和验收是否支撑收入质量 | 一次验收通过率、返工工时率、质量损失、职业责任成本率 | `quality_fact`、`liability_claim_fact` |
| 成本 | 工时与投入有效性 BC | 工时是否有效形成产出 | 工时利用率、有效工时率、无效工时、影子加班成本 | `timesheet_fact`、`labor_fact` |
| 成本 | 项目成本归集 BC | 直接成本是否真实、完整、可解释 | 直接成本、直接人工偏差、直接费用偏差、成本归集完整率 | `direct_expense_fact`、`project_cost_fact` |
| 回款 | WIP 与开票准入 BC | 已履约价值能否开票和低风险确认 | WIP Aging、合同资产天数、开票准入通过率、WIP 减值预测 | `wip_fact`、`billing_fact` |
| 回款 | AR 与现金转化 BC | 发票和应收能否变成现金 | DSO、AR Aging、争议 AR、ECL、质保金回收率 | `ar_fact`、`cash_receipt_fact` |
| 费用 | 费用报销与支出管控 BC | 非项目费用和 OH 是否受控 | OH Rate、费用池偏差、间接费用偏差、费用合规率 | `expense_fact`、`overhead_fact` |
| 采购 | 分包与采购履约 BC | 分包和采购是否支撑交付并控制成本 | 分包按期交付率、采购周期、分包成本偏差 | `procurement_fact` |
| 知识 | 知识资产与 AI 赋能 BC | 知识和 AI 是否提高报价与交付质量 | 知识复用率、AI 建议准确率、AI 回测通过率 | `knowledge_asset_fact`、`ai_recommendation_fact` |
| 组织 | 组织人事 BC | 组织、岗位、资质是否支撑交付能力 | 人员杠杆、资质覆盖、离职率、保险/证照到期风险 | `staff_snapshot_fact`、`certification_fact` |
| 组织 | 假勤薪酬 BC | 薪酬和假勤是否影响产能 | 薪酬负担率、缺勤产能影响、人工成本闭合 | `payroll_fact`、`absence_fact` |
| 组织 | 绩效激励 BC | 激励是否反映收益、风险和漏损归属 | 风险调整利润、奖金池、递延奖金、追索金额、绩效分数 | `performance_fact`、`bonus_fact` |
| 核算 | 财务总账与法定核算 BC | 法定收入、成本、减值和期间关闭是否可信 | 权责收入、合同资产/负债、亏损合同拨备、close cycle | `gl_transaction_fact`、`revenue_fact` |
| 平台 | 权限流程与集成平台 BC | 数据、流程、权限和事件是否支撑可信事实 | 数据完整率、按时工时率、系统采用率、合规问题关闭率 | `audit_action_fact`、`workflow_fact` |

## 8. 核心对象契约

### 8.1 MetricDefinition

FORCITIS one 每个 accepted KPI 至少登记：

| 字段组 | 必填内容 |
| --- | --- |
| 身份 | `metric_code`、`semantic_metric_code`、中英文名、状态 |
| 问题来源 | `problem_id`、`domain_layer`、`domain_node_code`、`domain_node_name`、`diagnostic_system`、价值链步骤、目标受众 |
| 公式 | 正式表达式、分子、分母、单位、最小样本量、平滑策略 |
| 计算血缘 | `source_metric_codes`、`fact_sources`、`formula_value_objects`、`formula_source_fields`、`formula_evidence_fields`、公式变量、policy 版本；声明直接事实计算、子指标组合或混合模式 |
| 主权 | metric owner、fact owner、dimension owner、policy owner、RACI |
| basis | `accounting_basis`、`revenue_basis`、`input_basis`、`period_grain` |
| 政策 | recognition、pass-through、contract asset、allocation、threshold、gate order、incentive freeze |
| 观测 | fact sources、calculation view、MetricObservation 契约 |
| 行为 | paired guardrail、gaming risk、incentive safe、exclusion policy |
| 展示 | T1/T2/T3/T4、维度、阈值、target plan version、质量采信规则 |

### 8.2 PolicyDefinition

所有 `*_policy_id` 都必须有唯一 owner、版本、生效期和审批链。

| policy_type | owner BC | 适用对象 |
| --- | --- | --- |
| recognition | 财务总账与法定核算 BC | REV、NR、合同资产、合同负债、履约义务 |
| pass_through / principal_agent | 财务总账与法定核算 BC | NSR、PTC、分包、代收代付 |
| gate_order | 经营口径与勾稽 BC | M* 五性门、资格瀑布 |
| rate_card | 经营口径与勾稽 BC | Potential_Revenue_Equivalent、价格实现 |
| allocation | 预算管控 BC、费用报销与支出管控 BC、财务总账与法定核算 BC | OH、内部结算、跨法人分摊 |
| threshold / guardrail | 经营口径与勾稽 BC | 诊断、预警、T1 展示 |
| incentive | 绩效激励 BC | 奖金、递延、追索、冻结窗口 |
| restatement | 财务总账与法定核算 BC、经营口径与勾稽 BC | close 后调整、经营 / 法定差异 |

### 8.3 Fact Source Contract

生产级 fact source 必须补齐：

| 字段 | 作用 |
| --- | --- |
| `event_time` / `posting_time` / `approval_time` | 区分业务发生、入账和审批时间 |
| `operating_period` / `accounting_period` | 区分经营期间和会计期间 |
| `close_state` | 控制 open、soft closed、hard closed、restated |
| `correction_policy` / `late_arrival_policy` | 定义冲销、补录、迟到事件处理 |
| `idempotency_key` / `source_hash` / `control_total` | 防重复和总量校验 |
| `reconciliation_target` | 指向项目子账、总账或管理 KPI |
| `legal_entity_id` / `project_accounting_id` / `gl_account_code` | 支撑账实勾稽 |
| `currency_code` / `fx_rate_source` / `tax_policy_id` | 支撑多币种和含税口径 |
| `evidence_document_id` | 合同、验收、开票、回款、凭证等证据 |

### 8.4 MetricObservation

MetricObservation 是指标值的唯一生产快照对象。

| 字段 | 说明 |
| --- | --- |
| `metric_code` / `semantic_metric_code` | 指标身份 |
| `metric_definition_version` | 口径版本 |
| `period` / `dimension_tuple` | 观测期间和维度组合 |
| `value` / `unit` | 指标值 |
| `as_of` | 查询或报表的点时视图 |
| `knowledge_time` | 系统知道该事实的时间 |
| `source_watermark` | 输入事实源水位 |
| `close_snapshot_id` | 期间关闭快照 |
| `formula_version` / `policy_versions` | 公式和政策版本 |
| `quality_state` | pass / warning / blocked |
| `close_state` | open / soft_closed / hard_closed / restated |
| `restatement_chain` | 原值、调整值、原因、审批人、事件引用 |

hard closed 期间不得被普通迟到事件覆盖。确需调整时，只能通过 restatement_event 生成新观测版本，并保留旧值可复现。

## 9. 仪表盘与诊断

### 9.1 仪表盘分层

| 层级 | 受众 | 内容 |
| --- | --- | --- |
| T1 经营健康 | 一号位、经营会 | 12 个 T1 指标、五大系统红黄绿、关键风险敞口、勾稽状态 |
| T2 中心 / 子域 | 中心负责人、域 owner | 子域指标族、价值链漏损、系统专项诊断 |
| T3 项目 / 合同 / 客户 | PM、BD、法务、回款负责人 | 项目 EAC、合约边界、WIP、AR、客户风险、回款动作 |
| T4 个人 / 团队 | 员工、团队负责人 | 工时及时率、有效工时、个人可控动作；返工归因只作辅导输入 |

### 9.2 诊断规则

诊断规则必须是可审计资产，不是看板文案。

| 字段 | 说明 |
| --- | --- |
| `rule_id` | 稳定编号 |
| `trigger_expression` | 触发表达式 |
| `severity` | critical / major / minor |
| `required_metrics` | 必备指标 |
| `leading_window` | 观察窗口 |
| `missing_data_behavior` | 缺数时跳过、降级、阻断或人工复核 |
| `owner_bc` | 规则 owner |
| `action_sla` | 管理动作时限 |
| `evidence_required` | 复盘证据 |
| `false_positive_check` | 误报复核机制 |

### 9.3 首批诊断组合

| 异常 | 组合信号 | 根因路径 | 动作 |
| --- | --- | --- | --- |
| M* 下降但 OP 暂未下降 | G1/G2/G4 资格率下降、WIP Aging 上升、At-risk exposure 上升 | 交付认可、合同覆盖、现金风险 | 项目清单复核、开票准入冻结、合同缺口关闭 |
| OPM 下降 | OP bridge 恶化、直接成本乘数下降、OH Rate 上升、总漏损率上升 | 成本、OH、价格、减值 | 经营会专题复盘，触发责任 BC 工单 |
| 现金转化恶化 | Contract Asset Days 上升、DSO 上升、争议 AR 上升、回款预测偏差上升 | WIP、开票、争议、客户信用 | 回款战情会、开票卡点清单、客户风险重评 |
| 增长质量恶化 | Book-to-Burn 上升但报价毛利下降、合同完备度下降 | 低价抢单或低质量新签 | 低毛利准入复核，合同基线前置检查 |
| 工时利用率改善但有效工时下降 | 利用率上升、返工率上升、项目毛利下降 | 堆工时或交付质量问题 | 禁止单独激励利用率，启用护栏 |
| 勾稽差异扩大 | F151-F159A 异常、close 后重述增加 | 事实源、政策、分摊、期间 | 降级展示，差异案进入经营口径与勾稽 BC |

## 10. 与旧 KPI baseline 的迁移规则

### 10.1 保留

保留旧目录中已验证的 275 个 accepted KPI，并作为 `active_migrated_from_legacy` 纳入 active 目录种子，尤其是：

- F001-F018：OP 和利润乘式骨架。
- F019-F030Q：A&E benchmark、利用率、价值变现漏斗和有效工时。
- F031-F050：权责收入、履约义务、NSR 和 pass-through。
- F051-F087：直接成本、三桶工时、OH 和预算偏差。
- F088-F102B：项目贡献、EAC、利润侵蚀。
- F103-F112、F184-F186：商机、Pipeline 和 BD 投入。
- F113-F127G、F163-F183：WIP、合同资产、AR、现金和风险传导。
- F128-F159A：切片、激励、勾稽。
- X 系列：外部 benchmark、AI、职业责任、平台和条件性 AEC 服务指标。

### 10.2 调整

| 调整项 | 原 baseline 问题 | 新处理 |
| --- | --- | --- |
| 顶层结构 | 公式编号主导叙事 | 经营系统、价值链、BC 主权主导叙事 |
| M* | 旧目录缺少北极星质量乘数 | 新增 `M_star_oper`、五性门、first-fail、matured 视图 |
| 漏损瀑布 | F030/F099/F102 分散，未强制可加 | 新增 first-cause 和收入当量折算 |
| basis | 部分公式有财务语义但未形成统一字段 | 强制 accounting / revenue / input basis |
| WIP | 收入口径和成本口径容易混用 | 强制拆分 `contract_asset` 与 `contract_cost_asset` |
| Backlog | 合同额、NSR、预测可能混用 | 拆成 contracted、funded、approved change、weighted forecast |
| Observations | 旧目录重定义，轻观测契约 | 新增 MetricObservation 双时态和 close 快照 |
| 政策 | `*_policy_id` 缺注册表 | 新增 PolicyDefinition |
| 总账勾稽 | synthetic 回放已过，生产账实未证明 | 增加 project subledger / GL / management KPI 三段门 |

### 10.3 新增 canonical 指标

首批新增指标必须进入 active MetricDefinition：

| semantic_metric_code | 中文名 | 原因 |
| --- | --- | --- |
| `M_star_oper` | 五性合格经营价值乘数 | L0 北极星 |
| `M_star_matured_oper` | 成熟期五性合格经营价值乘数 | 观察现金成熟结果 |
| `Total_Loss_Rate_oper` | 总漏损率 | L0 漏损诊断 |
| `Potential_Revenue_Equivalent_oper` | 潜在收入当量 | 漏损瀑布分母 |
| `M_Quality_Difference_oper` | M 与 M* 质量差异 | 连接利润乘式和北极星 |
| `Unexplained_OP_Variance_oper` | 未解释 OP 差异 | 验证利润驱动模型 |
| `Reconciliation_Exception_Rate_oper` | 勾稽异常率 | T1 数据可信度护栏 |

## 11. 准入与退役

### 11.1 准入

候选 KPI 必须先回答问题卡：

| 字段 | 要求 |
| --- | --- |
| 经营问题 | 用业务语言描述管理判断 |
| 价值链步骤 | 必须落到七步价值链，或说明其为结构 / 背景指标 |
| 主责 BC | 明确事实 owner，不允许共管 |
| 指标类型 | 结果、领先、护栏、诊断、数据质量或计划 |
| 事实测点 | 事件、事实源、状态快照或外部系统契约 |
| basis | accounting、revenue、input、period 和 close 口径 |
| 管理动作 | 预警、准入、冻结、复盘、激励或退役 |
| 验证证据 | seed、synthetic、production、subledger、GL |

### 11.2 采信

| quality_state | 使用规则 |
| --- | --- |
| pass | 可进入经营会、诊断规则和已授权激励 |
| warning | 可展示但必须标注，不得触发自动强管理动作 |
| blocked | 不得进入 T1、不得用于激励、不得作为正式诊断触发条件 |

### 11.3 激励安全

进入激励的指标必须满足：

- `incentive_safe=true`。
- 激励周期内公式、basis、阈值和排除政策冻结。
- 有 required guardrails。
- 可控性达到个人、项目或部门可控级别。
- 数据质量达到 `data_quality_minimum`。
- 异常项目、不可抗力、组织调整有 exclusion policy。

### 11.4 退役

连续 3 个月无管理动作、无仪表盘访问、无诊断规则引用、无业务 owner 认领的指标进入退役确认。退役只改变 `MetricDefinition.status=deprecated`，不得删除历史观测值。

## 12. 验证与落地顺序

### 12.1 落地顺序

| 阶段 | 工作 | 输出 |
| --- | --- | --- |
| S1 active 设计 | 本文件定为 FORCITIS one KPI 体系标准 | active 设计文档 |
| S2 目录迁移 | 旧 275 KPI 映射到 L0/L1/L2/L3 纵向节点、五大系统、七步链路、basis 和政策 | 已生成 active MetricDefinition 种子；旧 KPI 已迁为 accepted |
| S3 新增指标 | 补 `M_star_oper`、漏损瀑布、公式残差、双口径差异和 12 个 L1 子域健康 rollup | 已生成 16 个 active canonical KPI、12 个子域 rollup，active accepted 合计 303 个 |
| S4 契约补强 | 补 PolicyDefinition、MetricObservation、层级依赖图、生产级 Fact Source Contract | 已生成 policy registry、303 条 observation contract 种子和 119 个层级节点；生产级 Fact Source Contract 待补 |
| S5 synthetic 回放 | 运行现有校验脚本并更新报告 | design/synthetic PASS |
| S6 生产证据 | 接入生产事实样本和 KPI 输出回放 | production fact/value PASS |
| S7 账实勾稽 | 接入项目子账、总账、期间关闭、重述链 | accrual production PASS |

### 12.2 当前验证命令

设计级和 synthetic 回放继续使用：

```bash
python3 scripts/validate_kpi_all.py
```

active 目录也可单独生成和校验：

```bash
python3 scripts/build_forcitis_active_kpi_catalog.py
python3 scripts/validate_forcitis_active_kpi_catalog.py
```

生产证据门继续使用：

```bash
python3 scripts/validate_kpi_all.py \
  --production-evidence-package <production_evidence_package_dir> \
  --require-production-evidence
```

权责制生产级还需要扩展验证参数：

```bash
python3 scripts/validate_kpi_all.py \
  --production-evidence-package <production_evidence_package_dir> \
  --require-project-subledger-reconciliation \
  --require-gl-tieout \
  --require-period-close-state
```

### 12.3 完成判定

只有同时满足以下证据，才可声明 FORCITIS one KPI 体系生产级完成：

- 新 MetricDefinition 目录包含 active T1 指标、M*、漏损瀑布、双口径差异和观测契约。
- 所有 accepted KPI 有唯一 metric owner、fact owner、dimension owner、policy owner。
- 所有 fact source 有生产样本，并通过 owner、粒度、证据、hash、control total 校验。
- 所有生产 KPI 输出值能按目录公式和输入变量重算。
- 项目子账、总账、管理 KPI 三段勾稽通过。
- hard closed 期间的调整均通过 restatement_event 留痕。
- T1 仪表盘展示质量状态和勾稽状态。

## 13. 反模式

| 反模式 | 风险 | 处理 |
| --- | --- | --- |
| 继续把原公式库当顶层设计 | 公式编号替代经营问题，经营会难以下钻 | 公式库降级为语料和公式 baseline |
| 直接扩到 500 个 KPI | 指标膨胀，管理动作稀释 | 只在新经营问题、新事实源、新责任或新风险出现时新增 |
| M* 直接用已确认 NSR | 财务可确认门恒等为 1，五性失真 | M* 使用 Performed Value，并按 first-fail 门归因 |
| 把 WIP 统一看成一个概念 | 合同资产与合同履约成本混用 | 强制拆分收入侧和成本侧 WIP |
| 把 DSO 当现金系统唯一指标 | 看不出 WIP、争议、质保金、未批变更 | 使用现金流漏斗 |
| 用利用率单独激励个人 | 堆工时、牺牲质量、毛利下降 | 配有效工时、返工、项目毛利护栏 |
| 只做 synthetic 回放就宣告生产级 | 缺生产事实和账实勾稽 | 生产证据包、项目子账和 GL tieout 必须通过 |
