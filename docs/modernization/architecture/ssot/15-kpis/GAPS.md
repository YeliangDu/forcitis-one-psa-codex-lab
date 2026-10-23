# 指标候选字面差异

由 extract_metrics.py 生成，勿手改。

仅按完整名称字面相同比对，不拆分组合名称，不判同义。交 one-architecture 判定。

词表复制全部原始键；愿景 KPI 的 source 保留原有依据文字，不覆盖为文件定位。

基线没有独立 definition 列，以下以完整表行 cells 展示口径原文；与 definition 文本结构不同也列为候选，不能据此认定语义冲突。

## 条数与预期

- by_metric_baseline：实际 48，预期 48，一致。
- by_glossary_metric：实际 79，预期 79，一致。
- by_vision_kpi：实际 16，预期 16，一致。

输入缺失、结构或条数不符时脚本报错退出，不覆盖生成物；错误包含实际条数与预期。

## 同名而定义原文不同的候选（12 组）

### 净服务收入

- by_metric_baseline / M-28；出处：docs/modernization/r0/METRIC_BASELINE_DRAFT.md:102

```yaml
definition:
  编号: M-28
  建议名称: 净服务收入
  替代的叫法: （老报表没有）
  OAT: NR
  期间: D
  加总: A
  事实 BC: 派生：M-08 − 转付成本
  基础: 权
  含税: 不含税
  层: L3
  处置: 新增
  待决: NR 是否等于 REV − PTC（F2）
```

- by_glossary_metric / net_service_revenue；出处：docs/modernization/glossary/terms.yaml:9599

```yaml
definition: 管理口径的净额，不用于法定报表：不含税收入减委外与分包、再减可向客户转收的代垫费用后的余额；信息服务费不在此扣除，计入直接项目费用并在贡献毛利中扣除。法定收入按总额含代垫确认，二者可互相推导：净服务收入 ＝ 法定收入（不含税）− 委外与分包 − 可向客户转收的代垫费用，报表脚注写出推导关系（用户 KPI-Q02）；原「净收入」并入本词（KPI-Q02、NU-13）
```

### 利用率

- by_metric_baseline / M-42；出处：docs/modernization/r0/METRIC_BASELINE_DRAFT.md:126

```yaml
definition:
  编号: M-42
  建议名称: 利用率
  替代的叫法: 产能饱和度、工时有效率
  OAT: UR
  期间: D
  加总: N
  事实 BC: '`cost.timesheet` + 日历参数'
  基础: —
  含税: —
  层: L3
  处置: 合并
  待决: 日标准工时参数（B1，Q6③）；分子是可计费工时，还是可计费加商机工时
```

- by_glossary_metric / utilization_rate；出处：docs/modernization/glossary/terms.yaml:5757

```yaml
definition: 可计费工时 / 可用工时（PSA 通行指标）
```

### 剩余履约义务

- by_metric_baseline / M-06；出处：docs/modernization/r0/METRIC_BASELINE_DRAFT.md:70

```yaml
definition:
  编号: M-06
  建议名称: 剩余履约义务
  替代的叫法: 剩余合同额、在手合同、合约储备、backlog
  OAT: RPO
  期间: I
  加总: S
  事实 BC: '`contract.baseline` + `receivables.wip`'
  基础: 权
  含税: 不含税
  层: L2
  处置: 保留
  待决: 按月末期间快照
```

- by_glossary_metric / remaining_performance_obligation；出处：docs/modernization/glossary/terms.yaml:5382

```yaml
definition: 已签约未履约部分分摊的交易价格（行业称在手合同额 / backlog）
```

### 回款率

- by_metric_baseline / M-19；出处：docs/modernization/r0/METRIC_BASELINE_DRAFT.md:88

```yaml
definition:
  编号: M-19
  建议名称: 回款率
  替代的叫法: 回款 / 开票、回款质量、开回率、当年开回率
  OAT: COLR
  期间: D
  加总: N
  事实 BC: 派生
  基础: —
  含税: 含税
  层: L3
  处置: 合并
  待决: 两种开回率合一（K7）
```

- by_glossary_metric / collection_rate；出处：docs/modernization/glossary/terms.yaml:10295

```yaml
definition: 回款 ÷ 开票金额（同一税口径、同一期间）
```

### 平均在册人数

- by_metric_baseline / M-38；出处：docs/modernization/r0/METRIC_BASELINE_DRAFT.md:122

```yaml
definition:
  编号: M-38
  建议名称: 平均在册人数
  替代的叫法: 平均人数、交付人数、当前在职（作分母时）
  OAT: AVG_HEADCOUNT
  期间: D
  加总: N
  事实 BC: '`organization.workforce`'
  基础: —
  含税: —
  层: L2
  处置: 合并
  待决: 口径（B2）；Q6④ 统一除数
```

- by_glossary_metric / average_headcount；出处：docs/modernization/glossary/terms.yaml:10397

```yaml
definition: 期间内在册人数的平均值；计算口径（月初月末平均或日均、是否含实习与外包）待 B2；所有「人均」的统一除数
```

### 应收周转天数

- by_metric_baseline / M-16；出处：docs/modernization/r0/METRIC_BASELINE_DRAFT.md:85

```yaml
definition:
  编号: M-16
  建议名称: 应收周转天数
  替代的叫法: DSO、滚动 DSO、当年 DSO
  OAT: DSO
  期间: I（区间）
  加总: N
  事实 BC: 派生
  基础: 待定
  含税: —
  层: L3
  处置: 合并
  待决: 单一算法：分子、天数基准、是否含商业承兑汇票与坏账（F6，K7）
```

- by_glossary_metric / days_sales_outstanding；出处：docs/modernization/glossary/terms.yaml:10263

```yaml
definition: 应收余额（合同资产、应收账款、应收票据分别及合计）÷ 对应额 × 天数；分子分母与天数基准按 FD-39
```

### 开票转化率

- by_metric_baseline / M-18；出处：docs/modernization/r0/METRIC_BASELINE_DRAFT.md:87

```yaml
definition:
  编号: M-18
  建议名称: 开票转化率
  替代的叫法: 开票 / 产值、开票转化
  OAT: BCR
  期间: D
  加总: N
  事实 BC: 派生
  基础: —
  含税: 同口径
  层: L3
  处置: 保留
  待决: 分子分母税口径一致
```

- by_glossary_metric / billing_conversion_rate；出处：docs/modernization/glossary/terms.yaml:10279

```yaml
definition: 开票金额 ÷ 收入确认金额（同一税口径、同一期间）
```

### 直接人工

- by_metric_baseline / M-24；出处：docs/modernization/r0/METRIC_BASELINE_DRAFT.md:98

```yaml
definition:
  编号: M-24
  建议名称: 直接人工
  替代的叫法: 人工费用（项目部分）、人力成本费用
  OAT: DL
  期间: D
  加总: A
  事实 BC: '`cost.costing` ← `cost.timesheet`'
  基础: 权
  含税: 不含税
  层: L1
  处置: 保留
  待决: 有效工时 × 标准费率（词表定义）
```

- by_glossary_metric / direct_labor；出处：docs/modernization/glossary/terms.yaml:2699

```yaml
definition: 有效工时乘该变体所用的成本费率，可复算；按变体分费率：预算、预估变体用标准成本费率卡的版本，实际变体用实际成本费率（含加班计价）；挂项目的奖金按服务期间计提计入直接人工：预算、预估变体按费率卡版本的奖金计提率，实际变体按计提并在奖金核定后单列调整行（FCQ-05、FCQ-01）
```

### 直接费用

- by_metric_baseline / M-25；出处：docs/modernization/r0/METRIC_BASELINE_DRAFT.md:99

```yaml
definition:
  编号: M-25
  建议名称: 直接费用
  替代的叫法: 项目费用、ODC
  OAT: DE
  期间: D
  加总: A
  事实 BC: '`cost.costing` ← `expense.spend`'
  基础: 权
  含税: 不含税
  层: L1
  处置: 保留
  待决: —
```

- by_glossary_metric / direct_expense；出处：docs/modernization/glossary/terms.yaml:2715

```yaml
definition: 判定为项目直接的费用（经费用 BC 防腐层）
```

### 线索转化率

- by_metric_baseline / M-04；出处：docs/modernization/r0/METRIC_BASELINE_DRAFT.md:68

```yaml
definition:
  编号: M-04
  建议名称: 线索转化率
  替代的叫法: 线索转化率、已转为商机
  OAT: CONV
  期间: D
  加总: N
  事实 BC: '`presales.customer`'
  基础: 经
  含税: —
  层: L3
  处置: 保留
  待决: —
```

- by_glossary_metric / lead_conversion_rate；出处：docs/modernization/glossary/terms.yaml:10511

```yaml
definition: 期间内转为商机的线索数 ÷ 期间线索数
```

### 贡献毛利率

- by_glossary_metric / contribution_margin_ratio；出处：docs/modernization/glossary/terms.yaml:9578

```yaml
definition: 贡献毛利 ÷ 对应层级的净服务收入（不含税）；分母须与贡献毛利同层级、同变体
```

- by_vision_kpi / KPI-13；出处：BI_SCOPE §6.1 Q6-2；METRIC_BASELINE_DRAFT §3.4（M-29）

```yaml
definition: 贡献毛利 ÷ 同层级、同变体的净服务收入（用户 KPI-Q03，2026-10-22，原为 ÷ 不含税收入确认；口径变更与前后值见 ssot/15-kpis/kpis.yaml KPI-13），按 OAT 的 CMR 变体（报价、预算、预估、实际）声明；本 KPI 是经营结果度量，不是目标值。
```

### 账龄超 N 天应收

- by_metric_baseline / M-14；出处：docs/modernization/r0/METRIC_BASELINE_DRAFT.md:83

```yaml
definition:
  编号: M-14
  建议名称: 账龄超 N 天应收
  替代的叫法: 超期应收、超期（> 90 天）、逾期金额 > 90 天
  OAT: —
  期间: I
  加总: S
  事实 BC: 同上
  基础: —
  含税: 含税
  层: L2
  处置: 合并
  待决: 分段方案（F3）；按开票日起算
```

- by_glossary_metric / aged_receivable；出处：docs/modernization/glossary/terms.yaml:10242

```yaml
definition: 账龄超过 N 天的应收账款余额；N 与起算日为口径参数（管理账龄与减值账龄分段见 FD-37）
```
