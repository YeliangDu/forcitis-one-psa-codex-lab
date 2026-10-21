---
编号: BAH
题名: "Practical Model-Driven Enterprise Architecture: Design a mature enterprise architecture repository using Sparx Systems Enterprise Architect and ArchiMate® 3.1"
作者: [Mudar Bahri, Joe Williams]
年份: 2022
版本: "首版（First published: April 2022）"
资料类型: 书籍
主题标签: [企业架构方法, 业务架构, 能力, 价值流]
状态: 已审定
派工: ARC-1018-ARC-01
---

# Bahri、Williams 企业架构建模书要点笔记

本文按实际输入题名阅读，自行概括；短引：0 处。已抽查。

原笔记日期：2026-10-17；审定记录：one-architecture 2026-10-18。

书目与阅读范围：

- **书内实际题名**：Practical Model-Driven Enterprise Architecture: Design a mature enterprise architecture repository using Sparx Systems Enterprise Architect and ArchiMate® 3.1。
- 作者：Mudar Bahri、Joe Williams；版本：首版（First published: April 2022）；出版者：Packt Publishing Ltd.；年份：2022。
- ISBN：978-1-80107-616-6（版权 PDF 3）。
- 本地源 B：桌面文件名含 `Modeling Enterprise Architecture with TOGAF_ A Practical` 的 PDF，412 个物理页；书内题名 PDF 2、版权 PDF 3 与文件名题名及另一 ISBN 不同。
- **范围边界**：保留卡片简称 `bahri-togaf`，本笔记只描述这份已核读输入；没有证据可把它登记成题为 Modeling Enterprise Architecture with TOGAF 的另一本书。重点读模型仓库质量、视图、抽象层次与治理，未操作书中的工具或链接。

## 核心思想

- **论断：同一模型元素应由多幅图复用，复制同一事物会造成副本漂移。** 依据：BAH#聚焦元模型、BAH#模型抽象与版本管理。含义：现有文件化架构资料复用元素并明确维护责任，减少同物多名与关系漂移；模型仓库对象不据此等同企业经营对象。
- **论断：聚焦元模型只选择当前主题所需的元素和合法关系，每幅图围绕一个主要问题。** 依据：BAH#聚焦元模型、BAH#建模实践、BAH#聚焦视图。含义：架构视图按受众选择元素与观察范围，不为套用书中步骤引入 Sparx 工具、ArchiMate 3.1 或全套 TOGAF 仓库。
- **论断：稳定抽象能减少内部细节重构对外部模型引用的连锁影响。** 依据：BAH#模型抽象与版本管理。含义：企业视图以稳定抽象隔离内部组件和临时端点的变化，不把稳定性解释为冻结全部设计。
- **论断：书中用 ArchiMate 3.1 的策略元素表达能力与价值流的联系，能力可与资源、行动方案和结果关联。** 依据：BAH#能力与价值流。含义：本系统能力命名、分解及价值流判据仍回到 BIZBOK 和业务证据，不照图生成软件模块，也不把 3.1 示例当作 ArchiMate 4 规范依据。
- **论断：架构治理负责架构取舍，仓库治理负责版本、并行编辑、维护与发布。** 依据：BAH#架构与仓库治理。含义：文件化架构资料明确知识维护责任，仓库报告发布不能替代业务数据集验证，统一架构元素也不能直接替代业务主数据记录系统。

## 概念及页码

源 B；PDF 从 1 开始。所列正文印刷页加 19 为物理页，按页眉核读。

| 锚点 | 概念 | 原文位置 | 印刷页 | PDF 页 | 要点 |
|---|---|---|---|---|---|
| BAH#书目 | 实际题名、版次、ISBN | 实际题名、版次、ISBN | 无页码 | 2–3 | 实际题名、版次、ISBN |
| BAH#聚焦元模型 | 第 1 章 Use smaller metamodels；第 4 章 focused metamodel | 第 1 章 Use smaller metamodels；第 4 章 focused metamodel | 19、96 | 38、115 | 按当前主题选择所需元素和合法关系 |
| BAH#建模实践 | 第 4 章 Modeling best practices | 第 4 章 Modeling best practices | 115 | 134 | 每幅图选择适合受众的元素 |
| BAH#聚焦视图 | 第 4 章 Keeping your diagram focused | 第 4 章 Keeping your diagram focused | 116 | 135 | 一图围绕一个主要问题 |
| BAH#能力与价值流 | 第 9 章 Defining capabilities / Value streams | 第 9 章 Defining capabilities / Value streams | 289、292 | 308、311 | 策略元素解释能力与价值流的联系 |
| BAH#模型抽象与版本管理 | 第 10 章 Model abstraction / Model replication / Version management | 第 10 章 Model abstraction / Model replication / Version management | 328–329 | 347–348 | 复用元素，以稳定抽象减少细部变化的连锁影响 |
| BAH#架构与仓库治理 | 第 10 章 Architecture governance / Repository governance | 第 10 章 Architecture governance / Repository governance | 343–344 | 362–363 | 架构取舍与知识维护分属不同治理责任 |

## 对本系统的启示

原笔记没有提供 OBJ、PRC、VS、ADR 或框架子节编号；以下保留原应用判断并逐条标明编号缺口，不另造系统对照。

- **业务对象：** 模型仓库中的元素应被多幅图复用，不能将同一事物在不同图上复制成相互漂移的副本。元素、关系和图的观察范围各有含义，模型仓库对象也不等于企业经营对象。（BAH#聚焦元模型、BAH#模型抽象与版本管理；对照：本系统编号：原笔记未提供）
- **价值流：** 书中以 ArchiMate 3.1 的策略元素解释价值流及其与能力的联系；该图示可帮助展示关系，不构成本项目 BIZBOK 的阶段、价值项或入口/退出条件判据。（BAH#能力与价值流；对照：本系统编号：原笔记未提供）
- **能力：** 能力是战略意图与实现之间的观察角度，可和资源、行动方案及结果相联系。本项目具体能力命名和分解仍需 BIZBOK 规则及业务证据，不能照图生成模块。（BAH#能力与价值流；对照：本系统编号：原笔记未提供）
- **流程：** 本书不涉及 BPMN 可执行流程或本项目办理规则的完整设计。仓库中的业务行为、架构维护和审定流程不能相互替代。（BAH#架构与仓库治理；对照：本系统编号：原笔记未提供）
- **数据建模：** 聚焦元模型只为当前主题选择所需元素和合法关系；一图围绕一个主要问题有助沟通。对大型系统保留稳定抽象元素，可以减少细部重构对外部模型引用的连锁影响；这种稳定性来自正确抽象，不是冻结全部设计。（BAH#聚焦元模型、BAH#建模实践、BAH#聚焦视图、BAH#模型抽象与版本管理；对照：本系统编号：原笔记未提供）
- **度量与 BI：** 本书不涉及经营指标、事实粒度及 BI 口径。仓库查询和文档发布服务于架构知识的使用，不能把生成报告当作业务数据集已验证。（BAH#架构与仓库治理；对照：本系统编号：原笔记未提供）
- **主数据：** 共享仓库需要版本、并行编辑、维护和发布责任。架构治理决定架构取舍，仓库治理决定怎样维护知识，两者不等同；统一架构元素的管理方法也不能直接替代业务主数据记录系统。（BAH#模型抽象与版本管理、BAH#架构与仓库治理；对照：本系统编号：原笔记未提供）

## 我们的取舍

**已审定（one-architecture 2026-10-18，已抽查）。**

- **采纳：模型元素复用、聚焦视图及明确维护责任。** 理由：适用于现有文件化架构资料，能够减少同物多名和关系漂移。
- **采纳：用稳定抽象隔离不必要的实现细节。** 理由：企业视图不宜被某个内部组件或临时端点绑住。
- **不采纳：引入 Sparx 工具、ArchiMate 3.1 或重建全套 TOGAF 仓库。** 理由：书中工具步骤不构成项目授权，既有技术和框架边界保持有效。
- **不采纳：把本书的关系示例当成 ArchiMate 4 的规范依据。** 理由：本地书明确基于 3.1，版本 4 已调整通用行为元素及关系表示，须分别查原文。

**使用边界：** 短引：0 处。文件名对应的目标书目尚未核到；本卡交付的是实际提供文件的局部阅读笔记，不能宣称另一本题名已读完。

## 附：旧索引对照

- B-B1 → BAH#书目
- B-01 → BAH#聚焦元模型
- B-02 → BAH#建模实践
- B-03 → BAH#聚焦视图
- B-04 → BAH#能力与价值流
- B-05 → BAH#模型抽象与版本管理
- B-06 → BAH#架构与仓库治理

## 附：核对记录

原页码抽查派工：ARC-1018-ARC-04。以下为原笔记已有抽查记录；本次格式迁移未读取资料原文，也未重做页码抽查。

| 抽查项 | 笔记所记位置 | 实际位置 | 结果 |
|---|---|---|---|
| BAH#书目 题名与版权 | PDF 2–3 | PDF 2 为 Practical Model-Driven EA 题名，PDF 3 为版权页 | 一致 |
| BAH#聚焦元模型 Use smaller metamodels / focused metamodel | 印刷 19、96；PDF 38、115 | 页眉印刷页与 PDF 均吻合，内容为缩小元模型与聚焦元模型 | 一致 |
| BAH#建模实践 Modeling best practices | 印刷 115；PDF 134 | 页眉 115，讲每图只用适合受众的元素 | 一致 |
| BAH#聚焦视图 Keeping your diagram focused | 印刷 116；PDF 135 | 小节标题在此页 | 一致 |
| BAH#能力与价值流 Defining capabilities / Value streams | 印刷 289、292；PDF 308、311 | 289 页末为 Defining capabilities，292 页有 Value streams 标题 | 一致 |
| BAH#模型抽象与版本管理 Model abstraction / replication / Version management | 印刷 328–329；PDF 347–348 | 抽象在 328 起，replication 与 Version management 在 329 | 一致 |
| BAH#架构与仓库治理 Repository governance | 印刷 343–344；PDF 362–363 | Repository governance 标题在 343，治理内容续至 344 | 一致 |

抽查 7 条，偏差 0 条。
