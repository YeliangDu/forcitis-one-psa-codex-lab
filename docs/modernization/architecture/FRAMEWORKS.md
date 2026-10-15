# 架构知识框架（锁定版本）

依据：用户 2026-10-15「你需要有一个稳定的知识框架，不能漂移」（intent `2026-10-15-163920`）。本文件是 one-architecture 做架构判断时唯一的理论参照：每个框架锁定一个权威版本，概念用官方英文名加中文，释义用我们自己的话写，出处可查。`forcitis-architecture` 目录不是信源（同一 intent）。

**防漂移规则**
1. 改动本文件（换版本、增删概念、改释义）须引 ADR 或 intent，提交带 `Architecture-Ref:`。
2. 本文件与 `METHOD.md` 列入 one-architecture 画像的「上下文文档」，压缩或清空后自动注入；做判断前以本文件为准，不凭记忆。
3. 标「未核实」的条目不作硬依据，用到时先核实并更新本文件。
4. 核实记录：2026-10-15，sonnet 子代理只查一手来源（标准组织、作者、出版社页面）；Open Group 正文需登录，TOGAF 与 ArchiMate 部分来自官方页面摘要。

## 1. TOGAF Standard, 10th Edition（The Open Group，C220，已含 Technical Corrigendum 1，2025）

出处：https://publications.opengroup.org/standards/togaf/specifications/c220 。能力与价值流不在基础内容里，在业务架构系列指南：Business Capabilities v2（G211，2022）、Value Streams（G178，2022）。

| 概念 | 中文 | 本项目怎么用 |
|---|---|---|
| ADM：Preliminary、A Architecture Vision、B Business、C Information Systems（数据 + 应用）、D Technology、E Opportunities and Solutions、F Migration Planning、G Implementation Governance、H Architecture Change Management；贯穿的 Requirements Management | 架构开发方法 | 施工图方法的骨架（METHOD 第 0–7 步） |
| Business Capability | 业务能力 | 企业「能做什么」，不含怎么做、谁做、在哪做；按「名词 + 动词」命名（G211）；稳定，是连接战略与边界的锚 |
| Value Stream / Value Stage | 价值流 / 价值流阶段 | 为某个干系人产出价值的端到端阶段序列；每个阶段由若干能力支撑（G178） |
| Architecture Building Block (ABB) / Solution Building Block (SBB) | 架构构件 / 解决方案构件 | ABB = 与实现无关的能力单元（本项目即 BC）；SBB = 具体实现（Flowable、PostgreSQL 等），可替换 |
| Baseline / Target / Transition Architecture | 基线 / 目标 / 过渡架构 | 老系统现状 / 目标架构 / 每个阶段结束时可运行、可回滚的中间状态（`togaf/transitions.yaml`） |
| Architecture Roadmap | 架构路线图 | 过渡架构的排序与依赖（图②） |
| Architecture Repository / Landscape | 架构库 / 架构全景 | 本目录 `architecture/` 即架构库；图①a 即全景 |

## 2. Domain-Driven Design

出处：Eric Evans，*Domain-Driven Design*（Addison-Wesley，2003）；**DDD Reference（Evans，2015，CC BY 4.0，以它的模式名为准）** https://www.domainlanguage.com/ddd/reference/ ；Vaughn Vernon，*Implementing Domain-Driven Design*（2013）。事件风暴：Alberto Brandolini，*Introducing EventStorming*（Leanpub，未完稿）https://www.eventstorming.com/ 。

| 概念 | 中文 | 本项目怎么用 |
|---|---|---|
| Ubiquitous Language | 统一语言 | `glossary/terms.yaml`（ADR-0018） |
| Bounded Context | 限界上下文 | 一个 BC = 一个模块 = 一个 schema（ADR-0003） |
| Core Domain / Generic Subdomain（Evans）；Supporting Subdomain（Vernon，Reference 中没有） | 核心域 / 通用子域 / 支撑子域 | 子域分类（决策页 Q6）；引用「支撑」须注明出自 Vernon |
| Context Map；关系：Partnership、Shared Kernel、Customer/Supplier Development、Conformist、Anticorruption Layer、Open-host Service、Published Language、Separate Ways、Big Ball of Mud | 上下文映射与九种关系 | `ddd/context-map.yaml` 的 `type`；防腐层 = `platform.integration`（ADR-0039） |
| Aggregate、Entity、Value Object、Domain Event、Service、Repository、Factory、Module | 聚合、实体、值对象、领域事件、领域服务、仓储、工厂、模块 | `ddd/aggregates.yaml`；一个事务只改一个聚合（§14 第 1 项） |
| Domain Vision Statement、Highlighted Core | 领域愿景声明、突出核心 | 核心子域的一页说明（METHOD 第 2 步） |
| Event Storming：Big Picture → Process Level → Design Level | 事件风暴三个层级 | 价值流阶段 → 关键事件 → 命令、策略、读模型、聚合（METHOD 第 3 步）。便签颜色与元素清单的官方定义**未核实** |

## 3. BPMN 2.0.2（OMG，formal/2013-12-09）

出处：https://www.omg.org/spec/BPMN/2.0.2/ 。ISO/IEC 19510:2013 对应 2.0.1；未找到对应国标。

| 概念 | 本项目怎么用 |
|---|---|
| Flow Objects（Event、Activity、Gateway）、Data、Connecting Objects（Sequence / Message Flow）、Swimlanes（Pool、Lane）、Artifacts | 流程只编排人工步骤、超时与退回，不做业务计算（GUIDING_PRINCIPLES §4） |
| 三类图：Process、Collaboration、Choreography | 本项目只用可执行的 Process（Flowable，ADR-0007） |

## 4. DMN 1.6（OMG，formal/25-12-02；OMG 页面与 PDF 的日期不一致，不写年份）

出处：https://www.omg.org/spec/DMN/1.6/ 。

| 概念 | 本项目怎么用 |
|---|---|
| Decision Requirements Graph / Diagram；Decision、Business Knowledge Model、Input Data、Knowledge Source（元素名未逐字核对） | 决策点清单 `dmn/decisions.yaml` |
| Decision Table；命中策略：Unique（默认）、Any、Priority、First；Output order、Rule order、Collect（可带 + < > #） | 路由、资格、级别类规则；决策与流程、代码分离 |
| FEEL | 决策表表达式语言 |

## 5. DAMA-DMBOK 第 2 版修订版（DMBOK2R，2024）

出处：https://dama.org/dama-dmbok-revision/ 。DMBOK 3.0 在编，目标 2027。

| 概念 | 本项目怎么用 |
|---|---|
| 知识领域（以数据治理为中心）：Data Governance、Data Architecture、Data Modeling and Design、Data Storage and Operations、Data Security、Data Integration and Interoperability、Document and Content Management、Reference and Master Data、Data Warehousing and Business Intelligence、Metadata Management、Data Quality Management | 11 项按通行结构整理，**须对照车轮图原图核实**；本项目落点见 SESSION_PLAYBOOK §14.1 D1–D19 |
| Reference and Master Data；System of Record | 主数据由拥有该聚合的 BC 负责（D11、D17） |
| Data Owner（2024 修订：业务侧负责人） | 每个数据对象登记负责 BC 与业务负责人 |
| 中国对应：GB/T 36073-2025《数据管理能力成熟度评估模型》（2026-07-01 实施，代替 2018 版） | 2025 版的能力域**未核实** |

## 6. 维度建模与 OLTP / OLAP

出处：Kimball & Ross，*The Data Warehouse Toolkit*，3rd Ed.（Wiley，2013）；技术目录 https://www.kimballgroup.com/data-warehouse-business-intelligence-resources/kimball-techniques/dimensional-modeling-techniques/ ；Inmon 等，*DW 2.0*（2008）。OLTP / OLAP 的一手定义**未核实**，本项目以 GUIDING_PRINCIPLES §7、§8 的表述为准。

| 概念 | 中文 | 本项目怎么用 |
|---|---|---|
| 四步设计：选业务过程 → 声明粒度 → 定维度 → 定事实 | 维度建模四步 | METHOD 第 5 步 |
| Grain；Fact table（Transaction、Periodic snapshot、Accumulating snapshot、Factless） | 粒度；事实表四类 | 粒度取一条领域事件或过账明细（ADR-0025） |
| Conformed dimension；Enterprise Data Warehouse Bus Matrix | 一致性维度；总线矩阵 | 一致性维度 = BC 负责的主数据；总线矩阵 = 价值流阶段 × 主数据 |
| Slowly Changing Dimension Type 0–7 | 缓慢变化维 | 主数据版本化（D6、D17）对应 Type 2 |

## 7. ArchiMate（只作建模语言参照）

ArchiMate 4（C260，2026-04-27）已取代 3.2，元素大幅精简；Capability、Value Stream 在 4 中的定义**未核实**。本项目不以 ArchiMate 作硬依据；需要时以 3.2 的 Strategy 层（Capability、Value Stream、Resource、Course of Action）为参照，并在本文件登记。

## 8. 补充框架（用户 2026-10-15「补充理论框架全部采纳」，intent `2026-10-15-182238`）

核实：2026-10-15，sonnet 子代理只查一手来源；ISO 页面返回 403 的内容来自官方页面摘要（标「间接」）。

| 框架 | 锁定出处 | 本项目怎么用（补强 METHOD 哪一步） | 未核实 |
|---|---|---|---|
| **ISO/IEC/IEEE 42010:2022** Architecture description（第 2 版，ISO/IEC JTC 1/SC 7 与 IEEE） | https://standards.ieee.org/standard/42010-2022.html | 架构描述（AD）、架构描述框架（ADF）、视点（viewpoint）、模型类型（model kind）：4 张图与各 SSOT 是同一架构的不同视图，接缝规则就是视图间的对应规则（第 7 步治理、六条接缝的一致性校验） | 对应国标；stakeholder、concern、view、correspondence 的条款原文 |
| **Wardley Maps**（Simon Wardley） | https://learnwardleymapping.com/book/ （该页写 CC BY-SA 4.0） | 价值链与演化（从新生到商品化）给能力定位，作为核心 / 支撑 / 通用分类的依据；分类仍由用户定（第 2 步） | 版次年份、本人页面的许可、演化阶段的官方名称 |
| **REA 会计本体**（McCarthy 1982，*The Accounting Review* 57(3)；ISO/IEC 15944-4:2015） | https://webstore.iec.ch/publication/22042 | 经济资源、经济事件、经济主体与对偶：业务事件即会计事实，连接领域事件、`operations.facts` 过账与总账（ADR-0016；第 3、4、5 步） | 论文页码；15944-4:2015 是否仍有效；commitment 的标准表述 |
| **C4 model**（Simon Brown，CC BY 4.0） | https://c4model.com/ | 系统上下文、容器、组件、代码四层图：图④与代码视图的画法约定（第 7 步） | 无版本号 |
| **Team Topologies**（Skelton & Pais，第 2 版 2025，IT Revolution） | https://teamtopologies.com/book ；概念 https://teamtopologies.com/key-concepts | 流对齐 / 赋能 / 复杂子系统 / 平台四类团队、三种交互模式、团队认知负荷：会话分工与 BC 对齐（会话画像的独占与不碰） | 康威定律的表述 |
| **商业模式画布**（Osterwalder & Pigneur，*Business Model Generation*，Wiley 2010） | https://www.strategyzer.com/library/the-business-model-canvas | 九个构造块作为愿景、目标与 KPI 的来源（第 0 步） | 许可（CC BY-SA）与九块官方英文名只有二手来源 |
