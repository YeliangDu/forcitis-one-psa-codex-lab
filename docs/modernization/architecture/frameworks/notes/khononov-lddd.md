# Khononov《Learning Domain-Driven Design》要点笔记

状态：已审定（one-architecture 2026-10-18，页码待 sonnet 抽查）｜派工：ARC-1018-ARC-01｜2026-10-17

本文自行概括，不引入书中整套技术方案。短引：0 处。章节位置待 sonnet 独立抽查。

## 1. 书目与阅读范围

- 书名：Learning Domain-Driven Design: Aligning Software Architecture and Business Strategy；作者：Vlad Khononov（版权名 Vladislav Khononov）。
- 版本：First Edition，October 2021；版权年：2022；本地版权页还列 2021-10-08 First Release、2024-03-22 Second Release。
- 出版者：O’Reilly Media, Inc.；ISBN：978-1-098-10013-1。
- 本地源 K：桌面同名 EPUB，34 个 spine 文档；书目 spine 3–4。本次按本地第二次发布文本阅读，不声称它仍是 2021 初始文件。
- 无稳定页码，索引为章节、小节与 spine/去标签文本行号。聚焦战略设计与实现选择、通信和分析数据责任。

## 2. 与本项目相关的核心思想

### 业务对象

复杂业务逻辑涉及相互约束的状态变化，不能退化为 CRUD 界面。实体和值对象应配合聚合保护业务不变量；较简单的业务可采用更简单的逻辑组织方式，战术模式不应脱离业务复杂性独立推广。（K-03、K-06）

### 价值流

本书不涉及 BIZBOK 价值流图。业务战略与竞争差异帮助判断设计投入重点，不直接提供价值阶段或退出判据。（K-01）

### 能力

核心、通用与支撑子域描述业务差异及解决难度；这种分类服务于设计和投入选择。子域属于问题空间，限界上下文属于模型的解决方案边界，不要求机械一一对应，也不能将上下文都拆成最小服务。（K-01、K-02、K-06）

### 流程

保存聚合后再随意发消息会留下失败窗口。发件箱模式将状态和待发事件一起提交，再由转发过程发布；跨步骤还须明确重复处理与状态。作者区分线性事件—命令配对与持有流程状态、选择下一步的过程管理器，不能仅凭异步就认作同一种流程模式。（K-05）

### 数据建模

事件溯源让事件成为重建聚合状态的依据，与保存当前状态、附加审计日志不同。CQRS 则分开写入和读取职责，是另一项选择；这些模式可以组合，但不互相蕴含。（K-04、K-07）

### 度量与 BI

分析数据的提供方、质量与消费契约值得明确。数据网格章节提出领域对分析模型负责、将分析数据作为产品；这是可比较的治理观点，不能据此否定所有集中仓库或取代一致性维度及指标定义。（K-08；后半句为项目比较）

### 主数据

统一语言在限界上下文内保持一致，跨域应公开契约并明确翻译责任。业务主数据身份与某个上下文里的完整模型不能直接画等号；本书不提供本项目的匹配合并、权威来源优先级或版本表规则。（K-02）

## 3. 概念章节索引

源 K；文档位于 EPUB `OEBPS/`；`L` 为对应 spine 抽取文本行号或小节定位。

| 索引 | 概念 / 章节小节 | spine / 文件 | 位置 |
|---|---|---|---|
| K-B1 | 书目与发布历史 | 3，`titlepage01.html`；4，`copyright-page01.html` | 开头 |
| K-01 | 第 1 章 Core / Generic / Supporting subdomains | 9，`ch01.html` | Core L17、Supporting L34 起 |
| K-02 | 第 3 章 Bounded Contexts / Bounded Contexts Versus Subdomains | 11，`ch03.html` | 后者 L45 起 |
| K-03 | 第 6 章 Domain Model / Value Objects / Aggregates | 15，`ch06.html` | 开头与同名小节 |
| K-04 | 第 7 章 Event-Sourced Domain Model / Event Sourcing | 16，`ch07.html` | L3 起 |
| K-05 | 第 9 章 Outbox / Saga / Process Manager | 18，`ch09.html` | L100、L138、L213 起 |
| K-06 | 第 10 章 Heuristic / Bounded Contexts | 20，`ch10.html` | 开头 |
| K-07 | 第 8 章 Command-Query Responsibility Segregation | 17，`ch08.html` | L180 起 |
| K-08 | 第 16 章 Decompose Data Around Domains / Data as a Product | 27，`ch16.html` | L63、L68 起 |

## 4. 对本项目的取舍

**已审定（one-architecture 2026-10-18，页码待 sonnet 抽查）。**

- **采纳：以业务复杂性和模型含义选择设计深度。** 理由：当前阶段不应让模式的统一外观取代需求和不变量判断；现行代码规范仍须遵守。
- **采纳：明确状态、事件投递和分析数据的责任。** 理由：能分别核验交易成功、消息发布及分析可用，避免一处成功代表全链路成功。
- **不采纳：把只追加经营事实认定为全系统事件溯源。** 理由：后者要求从事件重建状态的完整契约，现有事实与审计记录不自动满足。
- **不采纳：因数据网格章节而重建数据平台或取消 Kimball 总线。** 理由：分析归属的观点可供比较，具体平台和指标治理仍按已采纳 ADR，未在本卡裁定。

## 5. 使用边界与短引

短引：0 处。书中启发式是条件化设计建议，不是能自动推出 FORCITIS one 子域、BC、核心业务或数据产品归属的事实。
