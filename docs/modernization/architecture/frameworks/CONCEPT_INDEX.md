<!-- 由 frameworks/notes_build.py 生成，勿手改 -->

# 资料概念索引

同一概念在各资料中的叫法与位置。收录跨两份及以上资料出现、或在 terms.yaml 有词条的概念；只在一份资料出现的概念见各笔记。差异说明手写在 `concept_notes.yaml`。

收录 27 个概念（全部锚点 419 个）。

## WBS

词条：`wbs_node`（WbsNode，delivery.execution）——工作分解结构，节点必须挂接合同范围条目或已批准变更

| 锚点 | 资料 | 原文叫法 | 位置 | 页码 |
|---|---|---|---|---|
| WBS2#WBS | [Secrets to Mastering the WBS in Real-World Projects: The Most Practical Approach to Work Breakdown Structures (WBS)!](notes/buchtik-wbs2.md) | 定义、范围与交付物导向 | 第 1 章 What Is the WBS? | 1–3 / 23–25 |

## 一致性维度

词条：无（terms.yaml 未收）

差异：KIM2 把总线矩阵与一致性维度合为一条；KIM3 分开，并把矩阵行称 business process，本系统称度量事件（FRAMEWORKS §5）。

| 锚点 | 资料 | 原文叫法 | 位置 | 页码 |
|---|---|---|---|---|
| KIM2#总线矩阵与一致性维度 | [The Data Warehouse Toolkit: The Complete Guide to Dimensional Modeling](notes/kimball-dwt2.md) | 总线架构、矩阵与一致性维度 | K2，第 3 章 Data Warehouse Bus Matrix / Conformed Dimensions | 79-83 / 105-109 |
| KIM3#一致性维度 | [The Data Warehouse Toolkit: The Definitive Guide to Dimensional Modeling](notes/kimball-dwt3.md) | Conformed Dimensions / Drilling Across | 第 2 章 | 51 / 87 |

## 上下文映射

词条：无（terms.yaml 未收）

| 锚点 | 资料 | 原文叫法 | 位置 | 页码 |
|---|---|---|---|---|
| DDDR#上下文映射 | [Domain-Driven Design Reference: Definitions and Pattern Summaries](notes/ddd-reference.md) | 上下文关系和显式翻译 | DDD，Context Map | 29 / 36 |
| VER#上下文映射 | [Implementing Domain-Driven Design](notes/vernon-iddd.md) | 第 3 章 Context Maps | 第 3 章 Context Maps | 87 / 130 |

## 业务对象与信息概念

词条：无（terms.yaml 未收）

| 锚点 | 资料 | 原文叫法 | 位置 | 页码 |
|---|---|---|---|---|
| BZ11#业务对象与信息概念 | [A Guide to the Business Architecture Body of Knowledge®（BIZBOK® Guide）](notes/bizbok-v11.md) | 业务对象、信息概念与数据库边界 | BZ，Part 2 §2.5 | 186 / 190 |
| BZ9#业务对象与信息概念 | [A Guide to the Business Architecture Body of Knowledge（BIZBOK Guide）](notes/bizbok-v9-diff.md) | 业务对象、信息概念与数据库边界 | V9：§2.5，spine 11 `BB90F_split_009.htm`，L185–210；V11：§2.5，186 / 190 | 186 / 190 |

## 事实与维度

词条：无（terms.yaml 未收）

| 锚点 | 资料 | 原文叫法 | 位置 | 页码 |
|---|---|---|---|---|
| KIM2#事实与维度 | [The Data Warehouse Toolkit: The Complete Guide to Dimensional Modeling](notes/kimball-dwt2.md) | 事实、粒度、可加性与维度 | K2，第 1 章 Fact Table / Dimension Tables | 17-19 / 43-45 |
| KIM3#事实与维度 | [The Data Warehouse Toolkit: The Definitive Guide to Dimensional Modeling](notes/kimball-dwt3.md) | Fact Tables / Dimension Tables | 第 1 章 | 10-14 / 46-50 |

## 交易价格

词条：`transaction_price`（TransactionPrice，contract.agreement）——企业因向客户转让商品或服务而预期有权收取的对价金额，含可变对价估计

| 锚点 | 资料 | 原文叫法 | 位置 | 页码 |
|---|---|---|---|---|
| CAS14#交易价格 | [企业会计准则第 14 号——收入](notes/cas14-2017.md) | 交易价格 | 第十四条、第十五条 | 7 / 7 |

## 仓储

词条：无（terms.yaml 未收）

| 锚点 | 资料 | 原文叫法 | 位置 | 页码 |
|---|---|---|---|---|
| DDDR#仓储 | [Domain-Driven Design Reference: Definitions and Pattern Summaries](notes/ddd-reference.md) | 聚合根的仓储访问 | DDD，Repositories | 17 / 24 |
| PEAA#仓储 | [Patterns of Enterprise Application Architecture](notes/fowler-poeaa.md) | 领域对象访问抽象 | 第 13 章 Repository（Edward Hieatt / Rob Mee）；`EPUB/Text/0321127420_ch13lev1sec3.html` | — |

## 值对象

词条：无（terms.yaml 未收）

| 锚点 | 资料 | 原文叫法 | 位置 | 页码 |
|---|---|---|---|---|
| DDDR#值对象 | [Domain-Driven Design Reference: Definitions and Pattern Summaries](notes/ddd-reference.md) | 值对象、特征与不可变性 | DDD，Value Objects | 12 / 19 |
| VER#值对象 | [Implementing Domain-Driven Design](notes/vernon-iddd.md) | 第 6 章 Value Characteristics | 第 6 章 Value Characteristics | 221 / 264 |

## 关联号

词条：`correlation_id`（CorrelationId，kernel）——一次请求及其引发的事件与投递共用的追踪编号（X-Correlation-Id）；出错时界面显示可复制的关联号，便于对日志（NAMING_STANDARD §4a U5、GUIDING_PRINCIPLES §11 O-1）

| 锚点 | 资料 | 原文叫法 | 位置 | 页码 |
|---|---|---|---|---|
| EIP#关联号 | [Enterprise Integration Patterns: Designing, Building, and Deploying Messaging Solutions](notes/hohpe-eip.md) | 请求与回复关联 | EIP1，第 5 章 Correlation Identifier | 163–164 / 219–220 |

## 可变对价

词条：`variable_consideration`（VariableConsideration，contract.agreement）——交易价格中随折让、违约金、奖励、索赔结果等变动的部分

| 锚点 | 资料 | 原文叫法 | 位置 | 页码 |
|---|---|---|---|---|
| CAS14#可变对价 | [企业会计准则第 14 号——收入](notes/cas14-2017.md) | 可变对价及限制 | 第十六条 | 7-8 / 7-8 |

## 合同取得成本

词条：`costs_to_obtain_contract`（CostsToObtainContract，cost.costing）——为取得合同发生的增量成本（如居间佣金），预期可收回时确认为资产

| 锚点 | 资料 | 原文叫法 | 位置 | 页码 |
|---|---|---|---|---|
| CAS14#合同取得成本 | [企业会计准则第 14 号——收入](notes/cas14-2017.md) | 合同取得成本 | 第二十八条、第二十九条 | 12 / 12 |

## 合同履约成本

词条：`contract_fulfilment_cost`（ContractFulfilmentCost，cost.costing）——已发生、待与收入确认匹配的可资本化履约成本（成本/存货计量，收入确认时转主营业务成本）；全库唯一 WIP，与回款侧合同资产计量基准不同（IFRS 15 §95-98 / CAS 14）

| 锚点 | 资料 | 原文叫法 | 位置 | 页码 |
|---|---|---|---|---|
| CAS14#合同履约成本 | [企业会计准则第 14 号——收入](notes/cas14-2017.md) | 合同履约成本 | 第二十六条、第二十七条 | 11-12 / 11-12 |

## 员工

词条：`employee`（Employee，organization.workforce）——员工主数据与任职记录（EmployeeId 来源）

| 锚点 | 资料 | 原文叫法 | 位置 | 页码 |
|---|---|---|---|---|
| UAE#员工 | [Unanet AE Participant Guide（八份手册）](notes/unanet-ae.md) | Employee | AD 21、33、49–50、67 | — |

## 命令查询职责分离

词条：无（terms.yaml 未收）

| 锚点 | 资料 | 原文叫法 | 位置 | 页码 |
|---|---|---|---|---|
| KHO#命令查询职责分离 | [Learning Domain-Driven Design: Aligning Software Architecture and Business Strategy](notes/khononov-lddd.md) | 命令查询职责分离 | 第 8 章 Command-Query Responsibility Segregation；17，`ch08.html`；L180 起 | — |
| VER#命令查询职责分离 | [Implementing Domain-Driven Design](notes/vernon-iddd.md) | 第 4 章 Command-Query Responsibility Segregation | 第 4 章 Command-Query Responsibility Segregation | 138–140 / 181–183 |

## 商机

词条：`opportunity`（Opportunity，presales.opportunity）——立项跟进的销售机会，评估→报价→承诺→转化的全生命周期对象

| 锚点 | 资料 | 原文叫法 | 位置 | 页码 |
|---|---|---|---|---|
| UAE#商机 | [Unanet AE Participant Guide（八份手册）](notes/unanet-ae.md) | Opportunity 主档 | AD 67、98 | — |

## 对象分类与汇聚例外

词条：无（terms.yaml 未收）

| 锚点 | 资料 | 原文叫法 | 位置 | 页码 |
|---|---|---|---|---|
| BZ11#对象分类与汇聚例外 | [A Guide to the Business Architecture Body of Knowledge®（BIZBOK® Guide）](notes/bizbok-v11.md) | 一级、次级、类型与汇聚例外 | BZ，Part 2 §2.5 | 194-195 / 198-199 |
| BZ9#对象分类与汇聚例外 | [A Guide to the Business Architecture Body of Knowledge（BIZBOK Guide）](notes/bizbok-v9-diff.md) | primary/secondary 与汇聚例外均已存在 | V9：同上，L565 起、L878 附近；V11：§2.5，194–195、199 / 198–199、203 | 194–195、199 / 198–199、203 |

## 履约进度

词条：`measure_of_progress`（MeasureOfProgress，receivables.wip）——履约义务完成程度的计量值，取值 [0,1]（F041），按投入法或产出法计量

| 锚点 | 资料 | 原文叫法 | 位置 | 页码 |
|---|---|---|---|---|
| CAS14#履约进度 | [企业会计准则第 14 号——收入](notes/cas14-2017.md) | 履约进度、产出法、投入法 | 第十二条 | 6 / 6 |

## 工作包

词条：`work_package`（WorkPackage，delivery.execution）——WBS 下的工作单元分组

| 锚点 | 资料 | 原文叫法 | 位置 | 页码 |
|---|---|---|---|---|
| WBS2#工作包 | [Secrets to Mastering the WBS in Real-World Projects: The Most Practical Approach to Work Breakdown Structures (WBS)!](notes/buchtik-wbs2.md) | 工作包、细度和分支深度 | 第 5 章 What Is a Work Package? / Decomposition | 52–58 / 74–80 |

## 工作日历

词条：`work_calendar`（WorkCalendar，organization.attendance）——按年维护的节假日与调休上班日；默认日历适用全部法人，预留法人专属日历（用户 2026-10-14）

| 锚点 | 资料 | 原文叫法 | 位置 | 页码 |
|---|---|---|---|---|
| UAE#工作日历 | [Unanet AE Participant Guide（八份手册）](notes/unanet-ae.md) | Holiday Calendar | AD 22–23 | — |

## 挣值

词条：`earned_value`（EarnedValue，delivery.execution）——截至某时点已完成工作的预算价值（EV；GB/T 50358 称赢得值）

| 锚点 | 资料 | 原文叫法 | 位置 | 页码 |
|---|---|---|---|---|
| PMB6#挣值 | [项目管理知识体系指南（PMBOK® 指南）第六版](notes/pmbok6-zh.md) | §7.4.2.2 数据分析中的挣值分析、偏差与预测 | §7.4.2.2 数据分析中的挣值分析、偏差与预测；spine / 文档：3，同上；位置：L253 起 | — |

## 数据质量

词条：无（terms.yaml 未收）

| 锚点 | 资料 | 原文叫法 | 位置 | 页码 |
|---|---|---|---|---|
| DAMA2#数据质量 | [The DAMA Guide to the Data Management Body of Knowledge](notes/dama-dmbok2.md) | 数据质量标准、源头预防与根因改进 | D2，第 13 章 §1.2 / §1.3.10.2 前文 | 未核 / 555-556,580 |
| HAY#数据质量 | [Data Model Patterns: A Metadata Map](notes/hay-dmp.md) | 数据质量 | 第 7 章数据质量特征；ROW SIX: MEASURING DATA QUALITY；16，同上；L249；L340 起 | — |

## 流程与功能

词条：无（terms.yaml 未收）

| 锚点 | 资料 | 原文叫法 | 位置 | 页码 |
|---|---|---|---|---|
| AM32#流程与功能 | [ArchiMate® 3.2 Specification](notes/archimate32.md) | 流程、功能与业务行为 | AM，§8.3 / §8.3.1 | 62-63 / 86-87 |
| AM4#流程与功能 | [ArchiMate 4 Specification](notes/archimate4.md) | Process / Function，§4.2.2–4.2.3 | 14，`chapter04.xhtml`；L37 起 | — |

## 粒度

词条：无（terms.yaml 未收）

| 锚点 | 资料 | 原文叫法 | 位置 | 页码 |
|---|---|---|---|---|
| KIM2#粒度 | [The Data Warehouse Toolkit: The Complete Guide to Dimensional Modeling](notes/kimball-dwt2.md) | 按测量活动设计、先明确粒度 | K2，第 2 章 Four-Step Dimensional Design Process | 30-31 / 56-57 |
| KIM3#粒度 | [The Data Warehouse Toolkit: The Definitive Guide to Dimensional Modeling](notes/kimball-dwt3.md) | Grain | 第 2 章 Business Processes / Grain | 39-40 / 75-76 |

## 缓慢变化维

词条：无（terms.yaml 未收）

| 锚点 | 资料 | 原文叫法 | 位置 | 页码 |
|---|---|---|---|---|
| KIM2#缓慢变化维 | [The Data Warehouse Toolkit: The Complete Guide to Dimensional Modeling](notes/kimball-dwt2.md) | Type 2、版本键与有效期说明 | K2，第 4 章 Type 2: Add a Dimension Row | 97-98 / 123-124 |
| KIM3#缓慢变化维 | [The Data Warehouse Toolkit: The Definitive Guide to Dimensional Modeling](notes/kimball-dwt3.md) | Slowly Changing Dimension Type 0/1/2 | 第 2 章 | 54 / 90 |

## 能力与价值流

词条：无（terms.yaml 未收）

| 锚点 | 资料 | 原文叫法 | 位置 | 页码 |
|---|---|---|---|---|
| AM32#能力与价值流 | [ArchiMate® 3.2 Specification](notes/archimate32.md) | 能力与价值流的抽象层次 | AM，§7.3.1 / §7.3.2 | 52-53 / 76-77 |
| BAH#能力与价值流 | [Practical Model-Driven Enterprise Architecture: Design a mature enterprise architecture repository using Sparx Systems Enterprise Architect and ArchiMate® 3.1](notes/bahri-togaf.md) | 第 9 章 Defining capabilities / Value streams | 第 9 章 Defining capabilities / Value streams | 289、292 / 308、311 |

## 领域事件

词条：无（terms.yaml 未收）

| 锚点 | 资料 | 原文叫法 | 位置 | 页码 |
|---|---|---|---|---|
| DDDR#领域事件 | [Domain-Driven Design Reference: Definitions and Pattern Summaries](notes/ddd-reference.md) | 领域事件、状态原因与两种时刻 | DDD，Domain Events | 13 / 20 |
| VER#领域事件 | [Implementing Domain-Driven Design](notes/vernon-iddd.md) | 第 8 章 Modeling Events | 第 8 章 Modeling Events | 288–289 / 331–332 |

## 领域服务

词条：无（terms.yaml 未收）

| 锚点 | 资料 | 原文叫法 | 位置 | 页码 |
|---|---|---|---|---|
| DDDR#领域服务 | [Domain-Driven Design Reference: Definitions and Pattern Summaries](notes/ddd-reference.md) | 不自然归属对象的领域操作 | DDD，Services | 14 / 21 |
| EVA#领域服务 | [Domain-Driven Design: Tackling Complexity in the Heart of Software](notes/evans-ddd2003.md) | 领域服务及其边界 | 第 5 章 Services；L873 起 | — |
| VER#领域服务 | [Implementing Domain-Driven Design](notes/vernon-iddd.md) | 第 7 章 Make Sure You Need a Service | 第 7 章 Make Sure You Need a Service | 268 / 311 |
