---
编号: "PEAA"
题名: "Patterns of Enterprise Application Architecture"
作者: ["Martin Fowler", "David Rice", "Matthew Foemmel", "Edward Hieatt", "Robert Mee", "Randy Stafford"]
年份: "2003（版权年；出版年未核）"
版本: "未核（新版版权页未见编号版次与首次印刷日期）"
出版信息: "Addison-Wesley；版权 © 2003 Pearson Education, Inc.；ISBN-13 978-0-321-12742-6；ISBN-10 0-321-12742-0"
资料类型: "书籍"
主题标签: ["领域建模", "数据建模"]
状态: "已审定"
派工: "ARC-1018-ARC-02"
---

# Fowler《Patterns of Enterprise Application Architecture》要点笔记

本笔记保存与 FORCITIS one 相关的阅读要点，不构成新增架构决定。正文为自行概括；英文模式名是来源术语，中文说明不据此确立正式术语。项目取舍均为草拟，现行决定优先。

原笔记审定记录：已审定（one-architecture 2026-10-18，已抽查）；记录日期：2026-10-17。

## 核心思想

- **论断：请求过程适合简单逻辑，复杂且变化的规则更适合把行为与数据放进领域对象，但会增加映射和学习成本。** 依据：PEAA#逻辑组织选择、PEAA#请求过程与领域模型。含义：按 ADR-0041 以复杂度、变化和行为重复说明 BC 建模选择，不因语言而统一模型深度，具体选择仍由用户决定。
- **论断：Service Layer 定义应用可用操作并协调响应，领域计算和校验与应用协调具有不同责任。** 依据：PEAA#应用协调。含义：按 CS-2.2–2.5 检查一类一用例、application 事务边界及聚合不变量，不以书中的服务层备选实现放宽现行规则。
- **论断：Data Mapper 使领域对象不必知道 SQL 或表结构，Repository 在领域与映射层之间提供对象访问抽象。** 依据：PEAA#领域与关系映射、PEAA#仓储。含义：按 CS-3.6 继续使用 JdbcClient、类型化手写映射和 Jdbc*Repository，只访问本模块 schema；按 CS-2.8 经 *Queries 查询读模型，不由模式推导反射 ORM 或跨 BC 对象图。
- **论断：Unit of Work 跟踪变更，Identity Map 保持一次处理中的对象同一性，Lazy Load 则推迟加载并改变查询时点与次数。** 依据：PEAA#变更跟踪、PEAA#对象同一性、PEAA#延迟加载。含义：按 CS-3.6 检查显式写入和查询路径，不因已有仓储就假定这些机制齐备，也不据此另造通用工作单元或隐式加载机制。
- **论断：跨请求的业务处理不自动获得短事务的并发保证，乐观离线锁的版本验证与更新须在同一系统事务内完成。** 依据：PEAA#业务与系统事务、PEAA#乐观离线锁。含义：按 CS-2.6 和 NAMING_STANDARD §2.6 A-8 验证版本读取、条件更新及 If-Match、412 / 428，不只在界面检查版本，也不把审批流实现成长数据库事务。
- **论断：值对象以值判断相等，不可变性避免共享引用的意外修改，Money 将数值、币种与运算约束放在一起。** 依据：PEAA#值对象与金额。含义：按 FRAMEWORKS §2、CODING_STANDARD 和 NAMING_STANDARD 保留既有值对象与金额内核，不照搬示例的整数存储、舍入或货币处理策略。

## 概念及页码

PDF 物理页从 1 开始；印刷页按新版页面实见页眉或页脚记录，「—」表示未标页码或未核，原因见附节。分号分隔的位置与两列页码依次对应。

| 锚点 | 概念 | 原文位置 | 印刷页 | PDF 页 | 要点 |
|---|---|---|---|---|---|
| PEAA#书目 | 作者、出版信息 / 版权与印次 | 书名页 / 版权页（未见首次印刷或编号版次声明） | — | 4–5 | 作者、出版、版权与印次分别记录 |
| PEAA#逻辑组织选择 | 逻辑复杂度与组织选择 | 第 2 章 Organizing Domain Logic，Making a Choice | 29–30 | 54–55 | 按行为复杂度解释逻辑组织的选择 |
| PEAA#请求过程与领域模型 | 请求过程与领域对象 | 第 9 章 Domain Logic Patterns，Transaction Script / Domain Model | 110–111；116–119 | 135–136；141–144 | 过程逻辑与领域对象各有适用条件 |
| PEAA#应用协调 | 应用边界与协调 | 第 2 章 Organizing Domain Logic，Service Layer；第 9 章 Domain Logic Patterns，Service Layer（Randy Stafford） | 30–31；133–135 | 55–56；158–160 | 应用可用操作与协调责任须明确 |
| PEAA#领域与关系映射 | 领域与关系映射分离 | 第 10 章 Data Source Architectural Patterns，Data Mapper | 165–166 | 190–191 | 对象与数据库映射由独立层承担 |
| PEAA#仓储 | 领域对象访问抽象 | 第 13 章 Object-Relational Metadata Mapping Patterns，Repository（Edward Hieatt / Rob Mee） | 322–323 | 347–348 | 在领域与映射层之间提供访问抽象 |
| PEAA#变更跟踪 | 变更集合与写入协调 | 第 11 章 Object-Relational Behavioral Patterns，Unit of Work | 184–185 | 209–210 | 跟踪一次业务处理的变更并协调写入 |
| PEAA#对象同一性 | 一次处理中的对象同一性 | 第 11 章 Object-Relational Behavioral Patterns，Identity Map | 195–196 | 220–221 | 避免同一记录被加载为多个可独立修改的对象 |
| PEAA#延迟加载 | 推迟加载及查询代价 | 第 11 章 Object-Relational Behavioral Patterns，Lazy Load | 200–201 | 225–226 | 推迟加载会改变查询时点与次数 |
| PEAA#业务与系统事务 | 并发异常、事务与业务跨度 | 第 5 章 Concurrency，Concurrency Problems / Transactions / Business and System Transactions | 64；71；74–76 | 89；96；99–101 | 跨请求业务处理不自动获得短事务的并发保证 |
| PEAA#乐观离线锁 | 提交时检查冲突 | 第 16 章 Offline Concurrency Patterns，Optimistic Offline Lock（David Rice） | 416–417 | 441–442 | 版本验证与更新在同一系统事务内完成 |
| PEAA#悲观离线锁 | 提前排斥冲突与代价 | 第 16 章 Offline Concurrency Patterns，Pessimistic Offline Lock（David Rice） | 426–427 | 451–452 | 提前排斥冲突会增加锁管理与并发成本 |
| PEAA#值对象与金额 | 值相等、不可变性与币种金额 | 第 18 章 Base Patterns，Value Object / Money | 486–489 | 511–514 | 值相等、不可变性及金额币种约束显式表达 |

## 对本系统的启示

### 领域逻辑的组织取决于行为复杂度

Transaction Script 把一次请求所需逻辑组织成过程，简单场景下理解成本低，但复杂规则容易在不同过程间重复。Domain Model 把数据和行为放在有业务含义的对象中，适合复杂且变化的规则，也增加映射和学习成本。本书没有可直接计算的复杂度分界；不能因使用面向对象语言就要求所有模块采用同一种模型深度。（PEAA#逻辑组织选择、PEAA#请求过程与领域模型） 本系统对照：ADR-0041。

### 应用操作与领域规则有分工

Service Layer 表达应用可用操作并协调响应。书中区分领域自身的计算、校验等逻辑与应用协调责任；服务层可以薄到只协调底层对象，也可能承担更多逻辑。这些都是书中备选，不是允许本项目把聚合不变量移到服务层的规定。（PEAA#应用协调） 本系统对照：CS-2.2–2.5。

### 对象与关系结构不必相同

Data Mapper 把对象与数据库之间的搬运、映射放在独立层，使领域对象不用知道 SQL 或表结构。Repository 在领域与映射层之间提供访问领域对象的抽象。二者的目的与职责可用于检查边界，但并不要求反射 ORM、通用查询语言，或一张表对应一个聚合。（PEAA#领域与关系映射、PEAA#仓储） 本系统对照：CS-3.6、CS-2.8。

### 变更跟踪、对象同一性与加载策略各解一个问题

Unit of Work 跟踪一次业务处理中的变更并协调写入；Identity Map 避免同一记录加载成多个可独立修改的对象；Lazy Load 推迟不一定需要的数据加载。它们是不同问题，不能因采用仓储就默认全部存在。延迟加载还改变查询发生的时点与次数，必须结合实际访问路径判断。（PEAA#变更跟踪、PEAA#对象同一性、PEAA#延迟加载） 本系统对照：CS-3.6。

### 业务交互跨度不等于数据库事务跨度

本书区分用户理解的一次业务处理与事务系统提供的事务。前者可能跨多次请求，不能自然继承一个短数据库事务的并发保证；长期持有数据库事务又可能降低并发。应先指出哪些数据在何时被读、哪些前提在提交时仍必须成立，再选择并发控制，不能把一次审批流等同于一个数据库事务。（PEAA#业务与系统事务）

Optimistic Offline Lock 通过提交前验证发现冲突，验证与更新须在同一系统事务内完成；常见做法是比较版本。Pessimistic Offline Lock 提前排斥冲突操作，代价是降低并发、增加锁管理责任。乐观方式也有用户最后才发现工作不能提交的体验成本；使用版本号并不自动解决全部一致性问题。（PEAA#乐观离线锁、PEAA#悲观离线锁） 本系统对照：CS-2.6、NAMING_STANDARD §2.6 A-8。

### 值语义和金额约束应显式表达

Value Object 以值而不是身份判断相等，不可变性可避免共享引用造成意外修改。Money 把数值和币种放在一起，并集中处理精度、运算和舍入问题。书中实现有具体类型选择，不等于本项目可以绕开既有金额内核。（PEAA#值对象与金额） 本系统对照：FRAMEWORKS §2、CODING_STANDARD、NAMING_STANDARD。

## 我们的取舍

以下均为**已审定（one-architecture 2026-10-18，已抽查）**；现行约束见 [FRAMEWORKS](../../frameworks.md) §2、CODING_STANDARD 与 NAMING_STANDARD。

- **建议借鉴：把逻辑组织方式的理由写到 BC 的建模选择中。** 可用规则复杂度、变化与行为重复解释深建模或薄 CRUD；具体标记继续按 ADR-0041 与现行方法由用户决定，本书不替代该决定。
- **建议借鉴：以应用协调和领域行为的分工检查用例。** 本项目一类一用例、事务只在 application、聚合维护不变量是 CS-2.2–2.5 的现行规则；书中 Service Layer 的多种实现不能据此放宽。
- **建议借鉴：用 Data Mapper 的分离目的检查手写映射。** 实现继续采用 CS-3.6 的 `JdbcClient`、类型化手写映射与 `Jdbc*Repository`，只访问本模块 schema。Repository 的集合比喻不能授权跨 BC 加载对象图，也不替代读模型经 `*Queries` 查询的 CS-2.8。
- **建议借鉴：把版本读取、条件更新与冲突返回视为一个可验证机制。** 比较版本和写入须处于相应事务保护内；本项目可变聚合按 CS-2.6，HTTP 按 NAMING_STANDARD §2.6 A-8 的 `If-Match`、412 / 428，不能只在界面检查版本。
- **不建议：另造通用 Unit of Work、全局 Identity Map、自动脏检查或隐式 Lazy Load。** 本卡未证明这些构件必要；先按本项目显式仓储写入、查询路径和性能预算实现。书中 Unit of Work 涉及的对象集合不能放宽一用例一聚合及已登记例外的要求。
- **不建议：把跨请求业务处理实现成长期数据库事务，或照搬本书的跨资源协调示例。** 本项目短事务、不在事务中调用外部系统，外部调用走发件箱和可靠投递；不借模式讨论引入新的事务管理框架。
- **建议借鉴：保留值相等与金额的类型约束。** 继续使用既有值对象与金额内核；不照搬示例中的整数存储、舍入算法或货币处理策略作为新标准。

**使用边界：** 本书是企业应用模式集，不是本企业的业务规格、DDD 聚合目录或数据库设计标准。它不证明某个聚合应包含哪些对象，不给出 FORCITIS 的收入、成本和审批规则，也不证明现有代码已满足事务或并发要求。本笔记只提出复审问题与模式对照，未新增框架、依赖、实现或验收结论。

## 附：书目与阅读范围

- 书名：Patterns of Enterprise Application Architecture。
- 主作者：Martin Fowler；新版书名页列合作者 David Rice、Matthew Foemmel、Edward Hieatt、Robert Mee、Randy Stafford；Repository 正文署 Edward Hieatt、Rob Mee。
- 新版版权页（PDF 5）：出版者 Addison-Wesley；版权 © 2003 Pearson Education, Inc.；ISBN-13：978-0-321-12742-6；ISBN-10：0-321-12742-0。出版年、首次印刷日期与编号版次未核，原因见旧版本页码对照附节。
- 本地源 P1：本机清单 PEAA 当前路径所列 PDF，共 558 页；书名页与版权页为 PDF 4–5，无印刷页码；正文以实见印刷页与 PDF 页定位。
- 按问题选读领域逻辑、映射、事务与并发，并对照相关模式的意图、机制和适用讨论；未通读所有模式或运行代码示例。只读取 PDF 文字层，未打开链接。短引：0 处。

## 附：核对记录

原记录：页码抽查（ARC-1018-ARC-04）。

本笔记以 EPUB 成员路径定位，无印刷页；逐一核成员存在及主题。

| 抽查项 | 笔记所记位置 | 实际位置 | 结果 |
|---|---|---|---|
| PEAA#书目 作者与出版信息 | main.html；copyrightpg.html | main 列作者 Fowler 等六人、出版社、日期、ISBN 0-321-12742-0；copyrightpg 为版权页，含首印 2002 年 10 月 | 一致 |
| PEAA#逻辑组织选择 逻辑复杂度与组织选择 | 第 2 章 Making a Choice，ch02lev1sec1 | 成员存在，标题为 Making a Choice，讨论三种领域逻辑模式选择 | 一致 |
| PEAA#应用协调 应用边界 | ch02lev1sec2；ch09lev1sec4 | 前者为 Service Layer 简介，后者为 Service Layer（Randy Stafford）定义应用边界 | 一致 |
| PEAA#领域与关系映射 领域与映射分离 | 第 10 章 Data Mapper，ch10lev1sec4 | 成员为 Data Mapper，主题为对象与库互相独立 | 一致 |
| PEAA#业务与系统事务 并发与业务事务 | ch05lev1sec1；ch05lev1sec5 | sec1 为 Concurrency Problems，sec5 为 Transactions 并在其内含 Business and System Transactions 小标题 | 一致 |
| PEAA#乐观离线锁 提交时检查冲突 | ch16lev1sec1 | Optimistic Offline Lock（David Rice），检测冲突并回滚 | 一致 |
| PEAA#值对象与金额 值相等与币种金额 | ch18lev1sec6；ch18lev1sec7 | 分别为 Value Object 与 Money | 一致 |
| PEAA#延迟加载 推迟加载 | ch11lev1sec3 | Lazy Load，主题为加载关联对象的代价 | 一致 |

抽查 8 条，偏差 0 条。

本次重核：2026-10-19；版本：未核（新版版权页未见编号版次与首次印刷日期）；锚点 13 个（已定位 13 个）；旧版抽查记录保留作历史记录，新版定位见概念及页码及旧版本页码对照。

## 附：旧索引对照

- P-B1 → PEAA#书目
- P-01 → PEAA#逻辑组织选择
- P-02 → PEAA#请求过程与领域模型
- P-03 → PEAA#应用协调
- P-04 → PEAA#领域与关系映射
- P-05 → PEAA#仓储
- P-06 → PEAA#变更跟踪
- P-07 → PEAA#对象同一性
- P-08 → PEAA#延迟加载
- P-09 → PEAA#业务与系统事务
- P-10 → PEAA#乐观离线锁
- P-11 → PEAA#悲观离线锁
- P-12 → PEAA#值对象与金额

## 附：旧版本页码对照

2026-10-19 按 ARC-1019-ARC-01 重核；旧值逐格保留自本次修改前的概念及页码表；旧版本是 epub 或按章节、行号定位，原表无印刷页与 PDF 页，故旧页码两列为「—」，旧定位见「原文位置」历史记录。旧文件已不存在，不据新版推算旧页码。下表「—」的原因见后文。

| 锚点 | 旧印刷页 | 旧 PDF 页 | 新印刷页 | 新 PDF 页 |
|---|---|---|---|---|
| PEAA#书目 | — | — | — | 4–5 |
| PEAA#逻辑组织选择 | — | — | 29–30 | 54–55 |
| PEAA#请求过程与领域模型 | — | — | 110–111；116–119 | 135–136；141–144 |
| PEAA#应用协调 | — | — | 30–31；133–135 | 55–56；158–160 |
| PEAA#领域与关系映射 | — | — | 165–166 | 190–191 |
| PEAA#仓储 | — | — | 322–323 | 347–348 |
| PEAA#变更跟踪 | — | — | 184–185 | 209–210 |
| PEAA#对象同一性 | — | — | 195–196 | 220–221 |
| PEAA#延迟加载 | — | — | 200–201 | 225–226 |
| PEAA#业务与系统事务 | — | — | 64；71；74–76 | 89；96；99–101 |
| PEAA#乐观离线锁 | — | — | 416–417 | 441–442 |
| PEAA#悲观离线锁 | — | — | 426–427 | 451–452 |
| PEAA#值对象与金额 | — | — | 486–489 | 511–514 |

新版来源：本机 `~/.modernize/psa/library.yaml` 的 PEAA「路径」所列 `Patterns of Enterprise Application Architecture (Martin Fowler) .pdf`，共 558 个 PDF 物理页。逐页以 `pdftotext -layout -f N -l N` 读取原生文字层；未用旧路径、旧文本或 OCR 目录补证。

未核说明：新版版权页（PDF 5）确认版权、出版者及两种 ISBN，但未见旧 EPUB 的 October 2002 首印与 November 05, 2002 出版日期，未见编号版次声明；页末仅有「24 17」，不足以据此确定版次或印刷年份，故不沿用旧日期。书名页与版权页无印刷页码，故 PEAA#书目 的印刷页记「—」。
