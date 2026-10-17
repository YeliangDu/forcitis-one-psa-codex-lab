# Fowler《Patterns of Enterprise Application Architecture》要点笔记

状态：已审定（one-architecture 2026-10-18，页码待 sonnet 抽查）｜派工：ARC-1018-ARC-02｜2026-10-17

本笔记保存与 FORCITIS one 相关的阅读要点，不构成新增架构决定。正文为自行概括；英文模式名是来源术语，中文说明不据此确立正式术语。项目取舍均为草拟，现行决定优先。

## 1. 书目与阅读范围

- 书名：Patterns of Enterprise Application Architecture。
- 主作者：Martin Fowler；本地书目信息列合作者 David Rice、Matthew Foemmel、Edward Hieatt、Robert Mee、Randy Stafford；各模式保留其具体署名，例如 Repository 署 Edward Hieatt、Rob Mee。
- 出版者：Addison-Wesley；ISBN：0-321-12742-0。版权页为 © 2003 Pearson Education, Inc.，首次印刷 October 2002；书目入口另载出版日期 November 05, 2002，二者分别记录，不混作同一日期。未见编号版次声明。
- 本地源 P1：用户提供的本地 EPUB，不进仓库。书目与版权位置见 P-B1；重排电子书没有可核实的固定物理页，本笔记只用 ZIP 成员路径、章与节标题定位。
- 按问题选读领域逻辑、映射、事务与并发，并对照相关模式的意图、机制和适用讨论；未通读所有模式或运行代码示例。源包夹带的第三方推广页不作正文依据，未打开其链接。短引：0 处。

## 2. 与本项目相关的核心思想

### 领域逻辑的组织取决于行为复杂度

Transaction Script 把一次请求所需逻辑组织成过程，简单场景下理解成本低，但复杂规则容易在不同过程间重复。Domain Model 把数据和行为放在有业务含义的对象中，适合复杂且变化的规则，也增加映射和学习成本。本书没有可直接计算的复杂度分界；不能因使用面向对象语言就要求所有模块采用同一种模型深度。（P-01、P-02）

### 应用操作与领域规则有分工

Service Layer 表达应用可用操作并协调响应。书中区分领域自身的计算、校验等逻辑与应用协调责任；服务层可以薄到只协调底层对象，也可能承担更多逻辑。这些都是书中备选，不是允许本项目把聚合不变量移到服务层的规定。（P-03）

### 对象与关系结构不必相同

Data Mapper 把对象与数据库之间的搬运、映射放在独立层，使领域对象不用知道 SQL 或表结构。Repository 在领域与映射层之间提供访问领域对象的抽象。二者的目的与职责可用于检查边界，但并不要求反射 ORM、通用查询语言，或一张表对应一个聚合。（P-04、P-05）

### 变更跟踪、对象同一性与加载策略各解一个问题

Unit of Work 跟踪一次业务处理中的变更并协调写入；Identity Map 避免同一记录加载成多个可独立修改的对象；Lazy Load 推迟不一定需要的数据加载。它们是不同问题，不能因采用仓储就默认全部存在。延迟加载还改变查询发生的时点与次数，必须结合实际访问路径判断。（P-06、P-07、P-08）

### 业务交互跨度不等于数据库事务跨度

本书区分用户理解的一次业务处理与事务系统提供的事务。前者可能跨多次请求，不能自然继承一个短数据库事务的并发保证；长期持有数据库事务又可能降低并发。应先指出哪些数据在何时被读、哪些前提在提交时仍必须成立，再选择并发控制，不能把一次审批流等同于一个数据库事务。（P-09）

Optimistic Offline Lock 通过提交前验证发现冲突，验证与更新须在同一系统事务内完成；常见做法是比较版本。Pessimistic Offline Lock 提前排斥冲突操作，代价是降低并发、增加锁管理责任。乐观方式也有用户最后才发现工作不能提交的体验成本；使用版本号并不自动解决全部一致性问题。（P-10、P-11）

### 值语义和金额约束应显式表达

Value Object 以值而不是身份判断相等，不可变性可避免共享引用造成意外修改。Money 把数值和币种放在一起，并集中处理精度、运算和舍入问题。书中实现有具体类型选择，不等于本项目可以绕开既有金额内核。（P-12）

## 3. 概念章节索引

下列均为 P1 中的精确成员路径；路径相对于 EPUB ZIP 根目录，不是仓库文件或网页。成员内的括号数字是原书交叉引用，未把它们当成本地 EPUB 页码。

| 索引 | 概念 | 章与节标题 | EPUB 成员路径 |
|---|---|---|---|
| P-B1 | 作者、出版信息 / 版权与印次 | 书目入口 / Copyright | `EPUB/Text/0321127420_main.html`；`EPUB/Text/0321127420_copyrightpg.html` |
| P-01 | 逻辑复杂度与组织选择 | 第 2 章 Making a Choice | `EPUB/Text/0321127420_ch02lev1sec1.html` |
| P-02 | 请求过程与领域对象 | 第 9 章 Transaction Script / Domain Model | `EPUB/Text/0321127420_ch09lev1sec1.html`；`EPUB/Text/0321127420_ch09lev1sec2.html` |
| P-03 | 应用边界与协调 | 第 2 章 Service Layer；第 9 章 Service Layer（Randy Stafford） | `EPUB/Text/0321127420_ch02lev1sec2.html`；`EPUB/Text/0321127420_ch09lev1sec4.html` |
| P-04 | 领域与关系映射分离 | 第 10 章 Data Mapper | `EPUB/Text/0321127420_ch10lev1sec4.html` |
| P-05 | 领域对象访问抽象 | 第 13 章 Repository（Edward Hieatt / Rob Mee） | `EPUB/Text/0321127420_ch13lev1sec3.html` |
| P-06 | 变更集合与写入协调 | 第 11 章 Unit of Work | `EPUB/Text/0321127420_ch11lev1sec1.html` |
| P-07 | 一次处理中的对象同一性 | 第 11 章 Identity Map | `EPUB/Text/0321127420_ch11lev1sec2.html` |
| P-08 | 推迟加载及查询代价 | 第 11 章 Lazy Load | `EPUB/Text/0321127420_ch11lev1sec3.html` |
| P-09 | 并发异常、事务与业务跨度 | 第 5 章 Concurrency Problems / Transactions / Business and System Transactions | `EPUB/Text/0321127420_ch05lev1sec1.html`；`EPUB/Text/0321127420_ch05lev1sec5.html`（后一标题在该成员内） |
| P-10 | 提交时检查冲突 | 第 16 章 Optimistic Offline Lock（David Rice） | `EPUB/Text/0321127420_ch16lev1sec1.html` |
| P-11 | 提前排斥冲突与代价 | 第 16 章 Pessimistic Offline Lock（David Rice） | `EPUB/Text/0321127420_ch16lev1sec2.html` |
| P-12 | 值相等、不可变性与币种金额 | 第 18 章 Value Object / Money | `EPUB/Text/0321127420_ch18lev1sec6.html`；`EPUB/Text/0321127420_ch18lev1sec7.html` |

## 4. 对本项目的取舍

以下均为**已审定（one-architecture 2026-10-18，页码待 sonnet 抽查）**；现行约束见 [FRAMEWORKS](../../FRAMEWORKS.md) §2、CODING_STANDARD 与 NAMING_STANDARD。

- **建议借鉴：把逻辑组织方式的理由写到 BC 的建模选择中。** 可用规则复杂度、变化与行为重复解释深建模或薄 CRUD；具体标记继续按 ADR-0041 与现行方法由用户决定，本书不替代该决定。
- **建议借鉴：以应用协调和领域行为的分工检查用例。** 本项目一类一用例、事务只在 application、聚合维护不变量是 CS-2.2–2.5 的现行规则；书中 Service Layer 的多种实现不能据此放宽。
- **建议借鉴：用 Data Mapper 的分离目的检查手写映射。** 实现继续采用 CS-3.6 的 `JdbcClient`、类型化手写映射与 `Jdbc*Repository`，只访问本模块 schema。Repository 的集合比喻不能授权跨 BC 加载对象图，也不替代读模型经 `*Queries` 查询的 CS-2.8。
- **建议借鉴：把版本读取、条件更新与冲突返回视为一个可验证机制。** 比较版本和写入须处于相应事务保护内；本项目可变聚合按 CS-2.6，HTTP 按 NAMING_STANDARD §2.6 A-8 的 `If-Match`、412 / 428，不能只在界面检查版本。
- **不建议：另造通用 Unit of Work、全局 Identity Map、自动脏检查或隐式 Lazy Load。** 本卡未证明这些构件必要；先按本项目显式仓储写入、查询路径和性能预算实现。书中 Unit of Work 涉及的对象集合不能放宽一用例一聚合及已登记例外的要求。
- **不建议：把跨请求业务处理实现成长期数据库事务，或照搬本书的跨资源协调示例。** 本项目短事务、不在事务中调用外部系统，外部调用走发件箱和可靠投递；不借模式讨论引入新的事务管理框架。
- **建议借鉴：保留值相等与金额的类型约束。** 继续使用既有值对象与金额内核；不照搬示例中的整数存储、舍入算法或货币处理策略作为新标准。

## 5. 使用边界

本书是企业应用模式集，不是本企业的业务规格、DDD 聚合目录或数据库设计标准。它不证明某个聚合应包含哪些对象，不给出 FORCITIS 的收入、成本和审批规则，也不证明现有代码已满足事务或并发要求。本笔记只提出复审问题与模式对照，未新增框架、依赖、实现或验收结论。
