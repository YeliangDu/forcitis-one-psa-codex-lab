# ArchiMate 4 规范要点笔记

状态：已审定（one-architecture 2026-10-18，已抽查）｜派工：ARC-1018-ARC-01｜2026-10-17

本文自行概括，只作语言与概念对照，不改变项目尚不采用 ArchiMate 的决定。短引：0 处。章节位置已抽查（ARC-1018-ARC-04）。

## 1. 书目与阅读范围

- 书名：ArchiMate 4 Specification；责任机构：The Open Group；文档号：C260。
- 版本：Eighth edition, first impression, April 2026（这是出版版次，语言版本为 4）；出版者：Van Haren Publishing；年份：2026。
- ISBN：纸本 978-94-018-1474-4；PDF 978-94-018-1475-1；EPUB 978-94-018-1476-8。版权页逐项列出。
- 本地源 A：桌面题名为 ArchiMate 4 Specification 的 EPUB，31 个 spine 文档，版权 spine 5。
- 对照源 A-PDF：桌面文件名含 `ArchiMate 4 Specification_ A Pocket Guide` 的 PDF，207 页；**书内题名、版权、文档号均为 C260 规范，与 A 对应，不是口袋指南**。文件名所称作者及另一 ISBN 未当作书内证据。
- 以下以 EPUB 的章节、小节及去标签文本行号定位；A-PDF 的版权 PDF 5、版本变化 PDF 199–200 可交叉核读。

## 2. 与本项目相关的核心思想

### 业务对象

语言中的业务对象是业务相关的概念性被动元素，通常表示类型；数据对象是适合自动处理的数据结构，两者可通过实现关系联系。它们不是同一种建模层次，也不能据图中一个框推出一张物理表或一个 DDD 聚合。（A-04、A-05）

### 价值流

价值从接受者角度判断，阶段逐步产生价值。价值流与具体个例中的办理流程有不同抽象层次和目的；能力可支持价值流，但支持关系不等于两者同名同结构。（A-03）

### 能力

能力表达组织、人员或系统具备的本领，可由人员、流程、信息及技术的组合实现。它是战略层面的观察角度，不是已经实现的应用功能清单，也不自动决定服务部署边界。（A-02；部署边界为项目应用）

### 流程

版本 4 将服务、流程、功能、事件等行为元素归到 Common Domain。流程关注产生特定结果的行为顺序，功能按选定标准归集行为；同一语言元素能用于多种层次，但细致的活动流仍需 BPMN 等过程设计语言。（A-01、A-06、A-08）

### 数据建模

业务、应用、技术等域之间的关系用于表达实现、访问或支持等不同含义。关系推导从细节到摘要，遵守规范规则；它不会凭空补出数据库事实或实际运行依赖。版本 4 改动了元素和组织方式，不能将 3.2 图例原样当作 4 的语法。（A-01、A-07、A-08）

### 度量与 BI

本规范不涉及 Kimball 的事实粒度、可加性或指标算法。模型可表达价值、目标和评估，但画出目标或影响关系不能证明经营效果，也不定义 BI 数据集。（A-09）

### 主数据

规范提供业务对象、数据对象的跨层次联系，不提供实体匹配合并、主数据记录系统优先级或本项目的版本化规则。不能从某应用访问数据对象的连线推定其拥有写入权。（A-04、A-05；写入权为项目应用）

## 3. 概念章节索引

源 A；文档位于 `OEBPS/xhtml/`。`L` 为本卡对应 spine 去标签文本行号；完整小节号优先用于复查。

| 索引 | 概念 / 原文小节 | spine / 文档 | 位置 |
|---|---|---|---|
| A-B1 | 题名、版次、ISBN、C260 | 5，`copyright.xhtml` | 开头；A-PDF PDF 5 |
| A-01 | 域、方面、跨域信息建模，§3.2.1 | 13，`chapter03.xhtml` | L50 起 |
| A-02 | Capability，§7.3.1 | 17，`chapter07.xhtml` | L15 起 |
| A-03 | Value Stream，§7.3.2 | 17，同上 | L24 起 |
| A-04 | Business Object，§8.3.1 | 18，`chapter08.xhtml` | L28 起 |
| A-05 | Data Object，§9.3.1 | 19，`chapter09.xhtml` | L27 起 |
| A-06 | Process / Function，§4.2.2–4.2.3 | 14，`chapter04.xhtml` | L37 起 |
| A-07 | Derivation of Relationships，§5.8 | 15，`chapter05.xhtml` | L241 起；规则另见附录 B |
| A-08 | Changes from Version 3.2，附录 E.4 | 29，`bm05.xhtml` | L54 起；A-PDF PDF 199–200 |
| A-09 | Motivation / Views and Viewpoints | 16，`chapter06.xhtml`；23，`chapter13.xhtml` | 章节开头；§13.1 L3 起 |

## 4. 对本项目的取舍

**已审定（one-architecture 2026-10-18，已抽查）。** 现行 [FRAMEWORKS](../../FRAMEWORKS.md) §9 的不采用项不因本笔记改变。

- **采纳：保存对象、实现数据、能力、价值流与流程的语义差异供比较。** 理由：能发现不同框架下的同名异义，不要求更换项目符号体系。
- **采纳：模型视图必须说明干系人、关注问题和抽象范围。** 理由：便于核验一幅图能支持什么决定，也避免一图包办所有细节。
- **不采纳：将 ArchiMate 4 升为本项目架构主语言或替换 BPMN/C4。** 理由：采用范围由 one-architecture 和既有决定约束，本卡只起草笔记。
- **不采纳：按图形删除合同、差距或约束等业务概念。** 理由：4 移除 Contract、Gap、Constraint 等语言元素并给出替代表示，不代表这些业务事实不复存在。

## 5. 使用边界与短引

短引：0 处。未执行书内链接、工具要求或其他指示。规范版本与本项目是否采用分别登记；任何 3.2 与 4 的迁移讨论都须核规范附录，不能用其他书的旧例替代。

## 页码抽查（ARC-1018-ARC-04）

| 抽查项 | 笔记所记位置 | 实际位置 | 结果 |
|---|---|---|---|
| A-B1 题名、版次、ISBN、C260 | spine 5 copyright；PDF 5 | EPUB copyright 与 PDF 5 内容一致 | 一致 |
| A-01 域与跨域 | §3.2.1，chapter03 | §3.2.1 Domains of the ArchiMate Language，位于 chapter03 | 一致 |
| A-02 Capability | §7.3.1，chapter07 | §7.3.1 Capability，位于 chapter07 | 一致 |
| A-03 Value Stream | §7.3.2，chapter07 | §7.3.2 Value Stream，位于 chapter07 | 一致 |
| A-04 Business Object | §8.3.1，chapter08 | 同 | 一致 |
| A-05 Data Object | §9.3.1，chapter09 | 同 | 一致 |
| A-06 Process / Function | §4.2.2–4.2.3，chapter04 | 同 | 一致 |
| A-07 派生关系 | §5.8，chapter05 | §5.8 Derivation of Relationships，位于 chapter05 | 一致 |
| A-08 版本变化 | 附录 E.4；PDF 199–200 | E.4 起于 PDF 199，续至 200；确列 Contract、Gap、Constraint 等被移除 | 一致 |

L 行号因去标签方式不同无法逐行复现，仅以小节号核对，不计偏差。

抽查 9 条，偏差 0 条
