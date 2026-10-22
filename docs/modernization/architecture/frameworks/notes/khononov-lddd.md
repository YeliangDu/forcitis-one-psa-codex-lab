---
编号: "KHO"
题名: "Learning Domain-Driven Design: Aligning Software Architecture and Business Strategy"
作者: ["Vlad Khononov（版权名 Vladislav Khononov）"]
年份: "2021（首版；版权年 2022）"
版本: "First Edition（October 2021）；2021-10-08 First Release"
出版信息: "O’Reilly Media, Inc.；版权 © 2022 Vladislav Khononov；ISBN 978-1-098-10013-1"
资料类型: "书籍"
主题标签: ["领域建模", "集成", "度量与BI"]
状态: "已审定"
派工: "ARC-1018-ARC-01"
---

# Khononov《Learning Domain-Driven Design》要点笔记

本文自行概括，不引入书中整套技术方案。短引：0 处。章节位置已抽查。

原笔记审定记录：已审定（one-architecture 2026-10-18，已抽查）；记录日期：2026-10-17。

## 核心思想

- **论断：复杂状态变化需要领域模型与聚合维护不变量，简单业务可以采用更简单的逻辑组织方式。** 依据：KHO#领域模型与聚合、KHO#设计启发式。含义：按业务复杂性选择设计深度，复杂业务不退化为 CRUD，也不脱离需求统一推广战术模式；现行代码规范仍须遵守。
- **论断：子域描述问题空间，限界上下文限定模型的解决方案边界，两者不必一一对应。** 依据：KHO#子域分类、KHO#限界上下文与子域、KHO#设计启发式。含义：以竞争差异与解决难度判断设计投入，不把上下文全部拆成最小服务，也不由某个上下文的完整模型直接推定主数据身份与治理规则；跨域公开契约并明确翻译责任。
- **论断：发件箱将聚合状态与待发事件一并提交，转发和跨步骤流程仍须处理重复及流程状态。** 依据：KHO#发件箱与过程管理器。含义：分别核验交易保存与事件发布，跨步骤明确重复处理和状态责任，不在保存聚合后随意发消息，也不把所有异步协作都认作同一种流程模式。
- **论断：事件溯源以事件重建聚合状态，CQRS 分开读写职责，两种模式可以组合但不互相蕴含。** 依据：KHO#事件溯源、KHO#命令查询职责分离。含义：不把只追加经营事实或附加审计日志认定为全系统事件溯源，采用后者须有从事件重建状态的完整契约。
- **论断：数据网格将分析模型责任分配给领域，并把分析数据作为有质量与消费契约的产品。** 依据：KHO#领域分析数据责任。含义：明确分析数据提供方、质量和消费契约，但不据此重建平台或取消 Kimball 总线，一致性维度和指标治理仍按已采纳 ADR。

## 概念及页码

PDF 物理页从 1 开始；印刷页按新版页面实见页眉或页脚记录，「—」表示未标页码或未核，原因见附节。分号分隔的位置与两列页码依次对应。

| 锚点 | 概念 | 原文位置 | 印刷页 | PDF 页 | 要点 |
|---|---|---|---|---|---|
| KHO#书目 | 书目 | 书名页 / 版权页，Revision History for the First Edition | — | 5–6 | 书目与发布历史分别记录 |
| KHO#子域分类 | 子域分类 | 第 1 章 Analyzing Business Domains，Core subdomains / Generic subdomains / Supporting subdomains | 4–7 | 30–33 | 核心、通用和支撑子域帮助判断设计投入 |
| KHO#限界上下文与子域 | 限界上下文与子域 | 第 3 章 Managing Domain Complexity，Bounded Contexts Versus Subdomains（含 Bounded Contexts / Subdomains） | 38–40 | 64–66 | 问题空间和模型解决方案边界不必一一对应 |
| KHO#领域模型与聚合 | 领域模型与聚合 | 第 6 章 Tackling Complex Business Logic，Domain Model / Value object / Aggregates | 76–78；84–86 | 102–104；110–112 | 复杂业务由实体、值对象与聚合维护不变量 |
| KHO#事件溯源 | 事件溯源 | 第 7 章 Modeling the Dimension of Time，Event Sourcing / Event-Sourced Domain Model | 99–101；108–109 | 125–127；134–135 | 以事件重建状态不同于当前状态加审计 |
| KHO#发件箱与过程管理器 | 发件箱与过程管理器 | 第 9 章 Communication Patterns，Outbox / Saga / Process Manager | 145–147；150–152 | 171–173；176–178 | 状态与待发事件一起提交，流程状态与重复另行明确 |
| KHO#设计启发式 | 设计启发式 | 第 10 章 Design Heuristics，Heuristic / Bounded Contexts / Business Logic Implementation Patterns | 159–162 | 185–188 | 设计深度须适配业务复杂性 |
| KHO#命令查询职责分离 | 命令查询职责分离 | 第 8 章 Architectural Patterns，Command-Query Responsibility Segregation | 128–130 | 154–156 | 写入与读取职责分开是独立选择 |
| KHO#领域分析数据责任 | 领域分析数据责任 | 第 16 章 Data Mesh，Decompose Data Around Domains / Data as a Product | 259–261 | 285–287 | 分析提供方、质量和消费契约须明确 |

## 对本系统的启示

### 业务对象

复杂业务逻辑涉及相互约束的状态变化，不能退化为 CRUD 界面。实体和值对象应配合聚合保护业务不变量；较简单的业务可采用更简单的逻辑组织方式，战术模式不应脱离业务复杂性独立推广。（KHO#领域模型与聚合、KHO#设计启发式） 本系统对照：原笔记未列本系统编号。

### 价值流

本书不涉及 BIZBOK 价值流图。业务战略与竞争差异帮助判断设计投入重点，不直接提供价值阶段或退出判据。（KHO#子域分类） 本系统对照：原笔记未列本系统编号。

### 能力

核心、通用与支撑子域描述业务差异及解决难度；这种分类服务于设计和投入选择。子域属于问题空间，限界上下文属于模型的解决方案边界，不要求机械一一对应，也不能将上下文都拆成最小服务。（KHO#子域分类、KHO#限界上下文与子域、KHO#设计启发式） 本系统对照：原笔记未列本系统编号。

### 流程

保存聚合后再随意发消息会留下失败窗口。发件箱模式将状态和待发事件一起提交，再由转发过程发布；跨步骤还须明确重复处理与状态。作者区分线性事件—命令配对与持有流程状态、选择下一步的过程管理器，不能仅凭异步就认作同一种流程模式。（KHO#发件箱与过程管理器） 本系统对照：原笔记未列本系统编号。

### 数据建模

事件溯源让事件成为重建聚合状态的依据，与保存当前状态、附加审计日志不同。CQRS 则分开写入和读取职责，是另一项选择；这些模式可以组合，但不互相蕴含。（KHO#事件溯源、KHO#命令查询职责分离） 本系统对照：原笔记未列本系统编号。

### 度量与 BI

分析数据的提供方、质量与消费契约值得明确。数据网格章节提出领域对分析模型负责、将分析数据作为产品；这是可比较的治理观点，不能据此否定所有集中仓库或取代一致性维度及指标定义。（KHO#领域分析数据责任；后半句为项目比较） 本系统对照：原笔记未列本系统编号。

### 主数据

统一语言在限界上下文内保持一致，跨域应公开契约并明确翻译责任。业务主数据身份与某个上下文里的完整模型不能直接画等号；本书不提供本项目的匹配合并、权威来源优先级或版本表规则。（KHO#限界上下文与子域） 本系统对照：原笔记未列本系统编号。

## 我们的取舍

**已审定（one-architecture 2026-10-18，已抽查）。**

- **采纳：以业务复杂性和模型含义选择设计深度。** 理由：当前阶段不应让模式的统一外观取代需求和不变量判断；现行代码规范仍须遵守。
- **采纳：明确状态、事件投递和分析数据的责任。** 理由：能分别核验交易成功、消息发布及分析可用，避免一处成功代表全链路成功。
- **不采纳：把只追加经营事实认定为全系统事件溯源。** 理由：后者要求从事件重建状态的完整契约，现有事实与审计记录不自动满足。
- **不采纳：因数据网格章节而重建数据平台或取消 Kimball 总线。** 理由：分析归属的观点可供比较，具体平台和指标治理仍按已采纳 ADR，未在本卡裁定。

**使用边界：** 短引：0 处。书中启发式是条件化设计建议，不是能自动推出 FORCITIS one 子域、BC、核心业务或数据产品归属的事实。

## 附：书目与阅读范围

- 书名：Learning Domain-Driven Design: Aligning Software Architecture and Business Strategy；作者：Vlad Khononov（版权名 Vladislav Khononov）。
- 新版版权页（PDF 6）：First Edition，October 2021；版权年：2022；发布历史仅列 2021-10-08 First Release。
- 出版者：O’Reilly Media, Inc.；ISBN：978-1-098-10013-1。
- 本地源 K：本机清单 KHO 当前路径所列 PDF，共 340 页；书名页与版权页为 PDF 5–6，无印刷页码。
- 本次逐页读取原生文字层，以新版章节、小节与页码定位；聚焦战略设计与实现选择、通信和分析数据责任，不沿用旧 EPUB 的第二次发布声明。

## 附：核对记录

原记录：页码抽查（ARC-1018-ARC-04）。

核对源：本地 EPUB 各 spine 文件去标签后的非空行（行号以独立重新抽取为准）。

| 抽查项 | 笔记所记位置 | 实际位置 | 结果 |
|---|---|---|---|
| KHO#子域分类 核心 / 支撑子域 | ch01；Core L17、Supporting L34 | ch01 Core subdomains L17、Supporting subdomains L34，章名 Analyzing Business Domains | 一致 |
| KHO#限界上下文与子域 限界上下文与子域 | ch03；L45 起 | ch03 小节 Bounded Contexts Versus Subdomains L45 | 一致 |
| KHO#领域模型与聚合 领域模型 / 值对象 / 聚合 | ch06 | ch06 章名 Tackling Complex Business Logic，含 Domain Model、Value object、Aggregates 各小节 | 一致 |
| KHO#事件溯源 事件溯源领域模型 | ch07；L3 起 | ch07 章名 Modeling the Dimension of Time，Event Sourcing 小节 L6，Event-Sourced Domain Model L393 | 一致 |
| KHO#发件箱与过程管理器 Outbox / Saga / Process Manager | ch09；L100、L138、L213 | ch09 对应小节恰在 L100、L138、L213 | 一致 |
| KHO#设计启发式 设计启发式 / 限界上下文 | ch10；开头 | ch10 章名 Design Heuristics，Heuristic 与 Bounded Contexts 小节在开头 | 一致 |
| KHO#命令查询职责分离 CQRS | ch08；L180 起 | ch08 Command-Query Responsibility Segregation 小节 L180 | 一致 |
| KHO#领域分析数据责任 按领域拆分数据 / 数据即产品 | ch16；L63、L68 | ch16 Decompose Data Around Domains L63、Data as a Product L68 | 一致 |

抽查 8 条，偏差 0 条。

本次重核：2026-10-19；版本：First Edition（October 2021）；2021-10-08 First Release；锚点 9 个（已定位 9 个）；旧版抽查记录保留作历史记录，新版定位见概念及页码及旧版本页码对照。

## 附：旧索引对照

- K-B1 → KHO#书目
- K-01 → KHO#子域分类
- K-02 → KHO#限界上下文与子域
- K-03 → KHO#领域模型与聚合
- K-04 → KHO#事件溯源
- K-05 → KHO#发件箱与过程管理器
- K-06 → KHO#设计启发式
- K-07 → KHO#命令查询职责分离
- K-08 → KHO#领域分析数据责任

## 附：旧版本页码对照

2026-10-19 按 ARC-1019-ARC-01 重核；旧值逐格保留自本次修改前的概念及页码表；旧版本是 epub 或按章节、行号定位，原表无印刷页与 PDF 页，故旧页码两列为「—」，旧定位见「原文位置」历史记录。旧文件已不存在，不据新版推算旧页码。下表「—」的原因见后文。

| 锚点 | 旧印刷页 | 旧 PDF 页 | 新印刷页 | 新 PDF 页 |
|---|---|---|---|---|
| KHO#书目 | — | — | — | 5–6 |
| KHO#子域分类 | — | — | 4–7 | 30–33 |
| KHO#限界上下文与子域 | — | — | 38–40 | 64–66 |
| KHO#领域模型与聚合 | — | — | 76–78；84–86 | 102–104；110–112 |
| KHO#事件溯源 | — | — | 99–101；108–109 | 125–127；134–135 |
| KHO#发件箱与过程管理器 | — | — | 145–147；150–152 | 171–173；176–178 |
| KHO#设计启发式 | — | — | 159–162 | 185–188 |
| KHO#命令查询职责分离 | — | — | 128–130 | 154–156 |
| KHO#领域分析数据责任 | — | — | 259–261 | 285–287 |

新版来源：本机 `~/.modernize/psa/library.yaml` 的 KHO「路径」所列 `Learning Domain-Driven Design (Vlad Khononov) .pdf`，共 340 个 PDF 物理页。逐页以 `pdftotext -layout -f N -l N` 读取原生文字层；未用旧路径、旧文本或 OCR 目录补证。

版本差异：新版版权页（PDF 6）仅列 2021-10-08 First Release，未列旧 EPUB 的 2024-03-22 Second Release；不沿用旧发布历史。书名页与版权页无印刷页码，故 KHO#书目 的印刷页记「—」。
