# Bahri、Williams 企业架构建模书要点笔记

状态：已审定（one-architecture 2026-10-18，页码待 sonnet 抽查）｜派工：ARC-1018-ARC-01｜2026-10-17

本文按实际输入题名阅读，自行概括；短引：0 处。页码待 sonnet 独立抽查。

## 1. 书目与阅读范围

- **书内实际题名**：Practical Model-Driven Enterprise Architecture: Design a mature enterprise architecture repository using Sparx Systems Enterprise Architect and ArchiMate® 3.1。
- 作者：Mudar Bahri、Joe Williams；版本：首版（First published: April 2022）；出版者：Packt Publishing Ltd.；年份：2022。
- ISBN：978-1-80107-616-6（版权 PDF 3）。
- 本地源 B：桌面文件名含 `Modeling Enterprise Architecture with TOGAF_ A Practical` 的 PDF，412 个物理页；书内题名 PDF 2、版权 PDF 3 与文件名题名及另一 ISBN 不同。
- **范围边界**：保留卡片简称 `bahri-togaf`，本笔记只描述这份已核读输入；没有证据可把它登记成题为 Modeling Enterprise Architecture with TOGAF 的另一本书。重点读模型仓库质量、视图、抽象层次与治理，未操作书中的工具或链接。

## 2. 与本项目相关的核心思想

### 业务对象

模型仓库中的元素应被多幅图复用，不能将同一事物在不同图上复制成相互漂移的副本。元素、关系和图的观察范围各有含义，模型仓库对象也不等于企业经营对象。（B-01、B-05）

### 价值流

书中以 ArchiMate 3.1 的策略元素解释价值流及其与能力的联系；该图示可帮助展示关系，不构成本项目 BIZBOK 的阶段、价值项或入口/退出条件判据。（B-04）

### 能力

能力是战略意图与实现之间的观察角度，可和资源、行动方案及结果相联系。本项目具体能力命名和分解仍需 BIZBOK 规则及业务证据，不能照图生成模块。（B-04）

### 流程

本书不涉及 BPMN 可执行流程或本项目办理规则的完整设计。仓库中的业务行为、架构维护和审定流程不能相互替代。（B-06）

### 数据建模

聚焦元模型只为当前主题选择所需元素和合法关系；一图围绕一个主要问题有助沟通。对大型系统保留稳定抽象元素，可以减少细部重构对外部模型引用的连锁影响；这种稳定性来自正确抽象，不是冻结全部设计。（B-01、B-02、B-03、B-05）

### 度量与 BI

本书不涉及经营指标、事实粒度及 BI 口径。仓库查询和文档发布服务于架构知识的使用，不能把生成报告当作业务数据集已验证。（B-06）

### 主数据

共享仓库需要版本、并行编辑、维护和发布责任。架构治理决定架构取舍，仓库治理决定怎样维护知识，两者不等同；统一架构元素的管理方法也不能直接替代业务主数据记录系统。（B-05、B-06）

## 3. 概念页码索引

源 B；PDF 从 1 开始。所列正文印刷页加 19 为物理页，按页眉核读。

| 索引 | 概念 / 章节小节 | 印刷页 | PDF 物理页 |
|---|---|---|---|
| B-B1 | 实际题名、版次、ISBN | 无页码 | 2–3 |
| B-01 | 第 1 章 Use smaller metamodels；第 4 章 focused metamodel | 19、96 | 38、115 |
| B-02 | 第 4 章 Modeling best practices | 115 | 134 |
| B-03 | 第 4 章 Keeping your diagram focused | 116 | 135 |
| B-04 | 第 9 章 Defining capabilities / Value streams | 289、292 | 308、311 |
| B-05 | 第 10 章 Model abstraction / Model replication / Version management | 328–329 | 347–348 |
| B-06 | 第 10 章 Architecture governance / Repository governance | 343–344 | 362–363 |

## 4. 对本项目的取舍

**已审定（one-architecture 2026-10-18，页码待 sonnet 抽查）。**

- **采纳：模型元素复用、聚焦视图及明确维护责任。** 理由：适用于现有文件化架构资料，能够减少同物多名和关系漂移。
- **采纳：用稳定抽象隔离不必要的实现细节。** 理由：企业视图不宜被某个内部组件或临时端点绑住。
- **不采纳：引入 Sparx 工具、ArchiMate 3.1 或重建全套 TOGAF 仓库。** 理由：书中工具步骤不构成项目授权，既有技术和框架边界保持有效。
- **不采纳：把本书的关系示例当成 ArchiMate 4 的规范依据。** 理由：本地书明确基于 3.1，版本 4 已调整通用行为元素及关系表示，须分别查原文。

## 5. 使用边界与短引

短引：0 处。文件名对应的目标书目尚未核到；本卡交付的是实际提供文件的局部阅读笔记，不能宣称另一本题名已读完。
