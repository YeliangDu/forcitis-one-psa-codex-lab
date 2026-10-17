# 架构知识框架第 4 稿（草案，已按 opus 评审修改；待用户确认；随最终一次性 ADR 落地）

状态：评审中｜2026-10-16｜负责：one-architecture

状态：草案 · 日期：2026-10-16 · 作者：one-architecture 子代理 · 取代：`FRAMEWORKS_V3_ADDITIONS_DRAFT.md`；落地后取代现行 `architecture/FRAMEWORKS.md`（ADR-0041）。方法见同目录 `METHOD_V4_DRAFT.md`，最终一次性 ADR 的条目清单见该文件第 8 节。

依据：用户 2026-10-15「你需要有一个稳定的知识框架，不能漂移」（intent `2026-10-15-163920`）；ADR-0041 适配评审收窄；框架纳入页「BIZBOK 五项（干系人与价值流、业务对象判据、能力命名、流程映射、参考模型查漏）、TOGAF G178、G190、G211 全部纳入（按所列范围）」（intent `2026-10-15-222319`）；「G178 与 BIZBOK 冲突时：以 BIZBOK 为准；TOGAF 许可：已有许可」（intent `2026-10-15-222350`）；方法评审决定（intent `2026-10-15-221201`）；本地原文可用（intent `2026-10-15-220121`）。适配评审（opus，逐页核对原文）见 `decision-page/frameworks-fit.html`。

本文件是 one-architecture 做架构判断时唯一的理论参照：只收经过适配评审、对本系统确有用处的框架与概念，每项锁定权威版本，写明本项目怎么用；概念用官方英文名加中文，释义用我们自己的话写，出处可查。`forcitis-architecture` 目录不是信源（干系人部分经用户指定除外，intent `2026-10-15-213742`）。

标记沿用 METHOD 第 4 稿：【已定】【推出】【裁量】【提议】；本稿【推出】项用户已过目认可（intent `2026-10-16-112800` L-11）；提议项汇总见第 13 节。第 4 稿评审的处理见 `METHOD_V4_DRAFT.md` 第 10 节。

**防漂移规则**
1. 改动本文件（换版本、增删框架或概念、改释义）须引 ADR 或 intent，提交带 `Architecture-Ref:`。
2. 增加框架、标准或选型前，先做对照本系统的适配评审：要求、代价、收益、把握；决策页写明代价，不预选推荐项（ADR-0041 决定 6）。
3. 本文件与 `METHOD.md` 列入 one-architecture 画像的「上下文文档」，压缩或清空后自动注入；做判断前以本文件为准，不凭记忆。
4. 标「未核实」的条目不作硬依据，用到时先核实并更新本文件；标「待独立复核」的条目可用，但交用户前须另派代理复核出处（METHOD 第 4 稿第 4 节）。
5. 框架之间冲突时按第 0 节，不临场裁定。【已定 / 推出，见第 0 节】
6. 许可受限的文本（BIZBOK、TOGAF 系列指南）只作概括与出处指引（Part、Section、Page），不转录原文、不转写步骤、清单与定义（第 11 节）。【推出】（intent `2026-10-15-220121`）
7. 核实记录：2026-10-15，sonnet 子代理查一手来源（版本与出处）；同日 opus 对抗式适配评审与联网核实（`proposals/FIT_REVIEW_FRAMEWORKS.md`、`FIT_REVIEW_ENGINEERING.md`、`FIT_REVIEW_VERIFY.md`），opus 逐页核对 BIZBOK 与三份指南（改正页码 4 处，`decision-page/method-review.html`）；2026-10-16 本稿起草时对照本地原文核对版本行与章节起始页（第 12 节）。

## 0. 分工与冲突规则

| 层 | 以谁为准 | 说明 |
|---|---|---|
| 业务内容（阶段叫什么、有几段、门的业务含义、对象的业务定义） | 公司手册、行业通行做法、用户业务事实 | METHOD 第 4 稿优先级规则 2、3 |
| 业务架构结构（干系人、价值流、业务对象、能力、流程映射） | BIZBOK v11；G178 只用价值流四要素与阶段六要素 | G178 与 BIZBOK 冲突以 BIZBOK 为准、G178 只用上述两项【已定】（intent `2026-10-15-222350`）；BIZBOK 作结构依据的其余部分【推出】（intent `2026-10-15-222319` 按所列范围纳入） |
| 领域模型结构（聚合、BC、上下文映射、事件） | DDD | 对象 ≠ 聚合：BIZBOK 的业务对象经 METHOD 第 ④ 步才对应到聚合（五种对应）【推出】 |
| 执行流程 | BPMN 2.0.2（只用可执行流程） | 与价值流阶段多对多（BIZBOK §3.4） |
| 分析 | 维度建模 | — |
| 迁移规划 | TOGAF C220 过渡架构 | — |
| 已采纳、有证据的不变量 ADR | 优先于任何框架推导 | ADR-0041 决定 3 |

**G178 与 BIZBOK 冲突，按 BIZBOK**【已定】（intent `2026-10-15-222350`）。原文有据的冲突只有一处：
1. G178「上一阶段的退出条件即下一阶段的进入条件」→ 按 BIZBOK：阶段可回退、跳过、重入。

**G178 未作规定、按 BIZBOK 的两点**【推出】（intent `2026-10-15-222350` 的冲突规则；`2026-10-15-222319` 纳入 BIZBOK 价值流）：
2. 不拆子价值流，跨价值流只经对象状态衔接（BIZBOK 允许把**阶段**再分子阶段，第 153–154 页，条件见 METHOD 第 4 稿第 ① 步）。
3. 进出条件写成业务对象的状态（BIZBOK §2.4）。

**G190、G211 与 BIZBOK 的不一致**：G190 附录对「业务对象」的定义与 BIZBOK 不同，且主张由能力确立信息概念（与本方法先对象后能力的顺序相反）；G211 的分层与从组织结构推导能力。intent `2026-10-15-222319` 按所列范围纳入，所列范围之外不用，故 G190 只用「从业务用语的名词里找信息概念」「信息图不等于数据模型」，G211 只用命名规则【推出】。

**已由用户决定的偏离**（对框架原文的有意偏离，用户已定，独立符合性审查 2026-10-16 必-8 要求登记）：
1. **先对象后能力**：BIZBOK §2.5 第 197–198 页建议从能力图提取信息概念、信息图与能力图同时或在其后做；G190 附录同样主张由能力确立信息概念。本方法反过来，能力由信息图对象生成，只作视图与查漏（intent `2026-10-15-222319`）。
2. **门只预警**：BIZBOK §2.4 第 161 页的门语义是退出条件满足前工作不离开阶段；本方法的阶段门只预警，不拦截，对象自身的不变量照常拒绝（intent `2026-10-16-061232` S2）。
3. **证据升为一级对象**：BIZBOK §2.5 第 195、198 页示例中证据是次级；本方法按 Q29 宽口径升为一级（intent `2026-10-16-072440`）。
4. **法律事实例外**：外部权威登记的法律事实算对象（intent `2026-10-15-221201`）。

**公司手册与框架冲突**：业务叫法与内容保留，建模概念按框架落（例：手册的「门事件完成才能进入下一阶段」落为只预警的前置检查，intent `2026-10-16-061232` S2）。只适用于建模概念的落点，不改手册的业务叫法与内容【已定】（intent `2026-10-16-072440` M-2；METHOD 第 4 稿第 7.1 节题②）。

## 1. Domain-Driven Design（领域模型主干）

出处：Eric Evans，*Domain-Driven Design*（Addison-Wesley，2003）；**DDD Reference（Evans，2015，CC BY 4.0，以它的模式名为准）** https://www.domainlanguage.com/ddd/reference/ 。

| 概念 | 中文 | 本项目怎么用 |
|---|---|---|
| Ubiquitous Language | 统一语言 | `glossary/terms.yaml`（ADR-0018） |
| Bounded Context | 限界上下文 | 一个 BC = 一个模块 = 一个 schema（ADR-0003）。**边界由统一语言与模型的一致范围决定**，同名异义处必须切开；不变量与事务一致性决定的是聚合，不是 BC（DDD Reference 2015「Bounded Context」「Aggregates」两节）【已定】（intent `2026-10-16-112800` L-7）（改正现行「边界由统一语言的一致范围、不变量与事务一致性决定」一句）。不由能力或老系统模块决定。技术模块（`platform.web`、`idempotency`、`eventing`、`scheduling` 等）在 `ddd/contexts.yaml` 标 `kind: technical`，不称 BC |
| Aggregate、Entity、Value Object、Domain Event、Service、Repository | 聚合、实体、值对象、领域事件、领域服务、仓储 | 一个事务只改一个聚合（SESSION_PLAYBOOK §14 第 1 项）；不变量分 A / B / C 三类（METHOD 第 4 稿第 ④ 步）；聚合与表的对应以数据访问注册表 `aggregate` 字段为准 |
| Context Map：只用有代码后果的四种关系——Anticorruption Layer、Open-host Service + Published Language、Shared Kernel、Conformist | 上下文映射 | `ddd/context-map.yaml` 只登记已建或本阶段在建的边，记方向、经由（`api` 或 `events` 契约）与上述关系；防腐层 = `platform.integration`（ADR-0039）。只用四种关系【已定】（L-10，intent `2026-10-16-112800`）。Partnership、Customer/Supplier、Separate Ways 是团队间关系，单团队单体中不用。引用的依赖方式（不校验 / api / 事件）与查环见 METHOD 第 4 稿第 ⑤ 步 |
| Subdomain | 子域 | **只作命名空间分组**：Java 包、schema、URL、权限码的前缀，与 BC 一起定；已建模块以代码为准，改名另开 ADR。不按 DDD 原意作问题空间分区、不先于 BC【已定】（intent `2026-10-15-221201`）；不做核心 / 支撑 / 通用分类【推出】（ADR-0041 决定 1：三分类已改为「深建模 / 薄 CRUD」一个标记，标在 BC 上） |
| 深建模 / 薄 CRUD | — | 标在 BC 上：规则密集或触账的 BC 深建模（聚合、不变量、事件），其余薄 CRUD。标记由用户定（ADR-0041 决定 1） |
| 事件清单推导 | — | 取代事件风暴工作坊：以信息图规则、规则卡与生产取证为输入推导领域事件、命令与聚合，用户裁定（METHOD 第 4 稿第 ④ 步） |

复审问题（来自 REA 会计本体，McCarthy 1982，不作框架采用）：每个经济事件的对偶事件在哪里；合同承诺如何成为事实。用于触账 BC 的过账设计复审。

## 2. BIZBOK v11（业务架构主干，新增）

出处：Business Architecture Guild，*A Guide to the Business Architecture Body of Knowledge®（BIZBOK® Guide）*，Version 11.0，Copyright 2022。本地原文：`~/Projects/YeliangDu/standards/bizbok-v11/BIZBOK-Guide-v11.0-2022.pdf`（用户提供，intent `2026-10-15-220121`）。页码按原文页脚。纳入范围：用户选定的五项【已定】（intent `2026-10-15-222319`），其余部分不用。

| # | 纳入项 | 出处（Part / Section / Page） | 本项目怎么用（METHOD 第 4 稿） |
|---|---|---|---|
| B1 | 干系人与价值流 | Part 2 §2.8（第 266 页起）；Part 2 §2.4（第 140 页起；子阶段第 153–154 页；绑定对象第 155 页；绑定对象常为协议第 155 页；状态互斥第 156 页；触发干系人第 158 页；进出条件引用对象状态第 161 页） | 第 ① 步：干系人 类型 → 类别 → 具体干系人；价值流以触发干系人视角。第 ③ 步：阶段进出条件写对象状态；可回退、跳过、重入；不拆子价值流（第 0 节冲突规则） |
| B2 | 业务对象判据 | Part 2 §2.5（第 181 页起；业务对象释义第 186 页；一级 / 次级第 194–195、198 页；同物异型不拆第 195 页；不是信息概念的东西第 197–199 页；汇聚对象第 199 页；类型、状态、关系第 200–206 页；状态作阶段门第 205–206 页） | 第 ② 步：对象须有定义、类型、状态、关系；一级 / 次级按依附；单据、估值、评级、属性不升为对象；一级对象检验（全部一级对象，intent `2026-10-15-215658`）。**本地化例外**：外部权威登记的法律事实（增值税发票、登记证书）算对象，偏离 BIZBOK「发票是单据」的点名（intent `2026-10-15-221201`） |
| B3 | 能力命名 | Part 2 §2.2（第 57 页起）；§2.5 第 197、208 页（能力与对象）；§2.4 第 159 页（价值流用于检验并精化能力） | 第 ② 步：由信息图生成能力图（一级对象一个一级能力，次级对象一个二级能力，约 90 项），命名「对象 + 管理」，按对象拆分、不越出父对象、不按类型拆。只作视图、第 ③ 步能力—阶段对账与查漏，**不检验信息图、不驱动 BC**；能力文件禁 `bc`、`module`、`subdomain` 字段（intent `2026-10-15-221201`、`2026-10-15-222319`） |
| B4 | 业务流程与价值流映射 | Part 3 §3.4（第 327 页起；事件触发第 330 页；与价值流 / 阶段多对多第 332 页） | 第 ⑥a 步（全企业清单）：流程由事件触发，与阶段多对多；一个阶段不等于一个 BPMN 流程；第 ⑥b 步才画 BPMN |
| B5 | 参考模型查漏 | Part 8 §8.6 Common Reference Model（第 650 页起，§8.7 始于第 673 页） | 第 ② 步：用通用模型一级能力的**名称**逐项核对是否漏了对象，出漏项清单逐项处置；**不抄定义**（Guild 条款）；没有工程或专业服务行业模型，Part 8 其他行业模型不用 |

**明文不用**【推出】（intent `2026-10-15-222319` 纳入时所列代价）：Part 6 §6.5 *Business Architecture and Service-Oriented Architecture Alignment*（第 529 页起；从能力推导软件服务）；Part 6 其余各节（业务架构与 IT 对齐）；由能力图决定 BC、模块或子域的任何做法。

**页码核实状态**：章节起始页（§2.2 第 57、§2.4 第 140、§2.5 第 181、§2.8 第 266、§3.4 第 327、§6.5 第 529、§8.6 第 650 页）2026-10-16 对照原文目录核对；节内页码由 2026-10-15 opus 适配评审逐页核对（已改正 4 处），**待独立复核**。

## 3. TOGAF Standard 与系列指南

出处：The Open Group。本地原文：`~/Projects/YeliangDu/standards/togaf10-2025-05/`（用户提供，intent `2026-10-15-220121`）。许可：用户登记为「已有许可」（intent `2026-10-15-222350`）。

### 3.1 TOGAF Standard, 10th Edition（只用迁移规划部分）

版本：C220（含 Technical Corrigendum 1，2025）https://publications.opengroup.org/standards/togaf/specifications/c220 。

| 概念 | 中文 | 本项目怎么用 |
|---|---|---|
| Baseline / Target / Transition Architecture | 基线 / 目标 / 过渡架构 | 老系统现状 / 目标架构 / 每个阶段结束时可运行、可回滚的中间状态（`togaf/transitions.yaml`） |
| Architecture Roadmap | 架构路线图 | 过渡架构的排序与依赖（图②） |

不用：ADM 全套阶段与治理机制（Architecture Board、Contract、Enterprise Continuum 等）；ABB / SBB 与 BC 不作对应。

### 3.2 G178 *Value Streams*（新增）

版本：Document Number G178，April 2022 出版（取代 2017-10 版），2025-05 更新纳入 Technical Corrigendum 1（2026-10-16 对照本地原文版权页核对）。

| 概念 | 本项目怎么用 |
|---|---|
| 价值流四要素（名称、描述、触发干系人、价值）§2.1 | METHOD 第 ① 步 |
| 阶段六要素（名称、描述、参与干系人、进入条件、退出条件、价值项）§2.2 | METHOD 第 ③ 步；进出条件按 BIZBOK 写对象状态 |
| 价值流原则：必须有明确触发者、先外部后内部、价值流不是能力、保持简洁 §3.1 | METHOD 第 ① 步校验 |

不用：「退出即进入」（与 BIZBOK 冲突，按第 0 节）【已定】（intent `2026-10-15-222350`）；G178 未作规定处按 BIZBOK：不拆子价值流、跨价值流只经对象状态衔接、进出条件写对象状态【推出】。

### 3.3 G190 *Information Mapping*（新增）

版本：Document Number G190，April 2022 出版，2025-05 按 Technical Corrigendum 1 作编辑性更新（2026-10-16 核对本地原文版权页）。

| 概念 | 本项目怎么用 |
|---|---|
| 从业务用语的名词里识别信息概念（§2） | METHOD 第 ② 步的识别方法之一 |
| 信息图不等于数据模型；与组织图分开（§4、§5） | 信息图不进数据访问注册表；表由第 ⑥b 步按聚合登记 |

不用：附录中与 BIZBOK 不同的「业务对象」定义；由能力确立信息概念的顺序【推出】（intent `2026-10-15-222319` 按所列范围）。节号沿用第 3 稿，**待独立复核**。

### 3.4 G211 *Business Capabilities, Version 2*（扩大使用）

版本：Document Number G211，April 2022 出版，2025-05 按 Technical Corrigendum 1 作编辑性更新（2026-10-16 核对本地原文版权页；现行 FRAMEWORKS 只写「2022」）。

| 概念 | 本项目怎么用 |
|---|---|
| 能力命名：名词—动词，名词是唯一的业务对象，不照搬部门名（§2.1.1） | METHOD 第 ② 步能力图命名，与 BIZBOK B3 一致 |

不用：多级拆分方法、从组织结构推导能力、能力热图驱动投资或 BC【推出】（intent `2026-10-15-222319` 按所列范围）。现行 FRAMEWORKS 的「一级 12 项只作范围漏项核对」**作废**，`business/capabilities.yaml`（原 `togaf/capabilities.yaml`，L-3）按 METHOD 第 ② 步重建【已定】（intent `2026-10-15-221201`）。

## 4. BPMN 2.0.2（OMG，formal/2013-12-09）

出处：https://www.omg.org/spec/BPMN/2.0.2/ 。

| 概念 | 本项目怎么用 |
|---|---|
| Process（Flow Objects：Event、Activity、Gateway；Sequence Flow；Lane） | 只用可执行的 Process（Flowable，ADR-0007），只编排人工步骤、超时与退回，不做业务计算（GUIDING_PRINCIPLES §4）；流程登记由 `.bpmn20.xml` 生成（加所属阶段字段，可多个），不手写【已定】（L-10，intent `2026-10-16-112800`）。流程与价值流阶段多对多（BIZBOK B4）。BPMN 2.0.2 没有里程碑元素，阶段不画成 BPMN 中间事件（`STAGE_TYPES_DRAFT.md` §3） |

## 5. 维度建模与 OLTP / OLAP

出处：Kimball & Ross，*The Data Warehouse Toolkit*，3rd Ed.（Wiley，2013）；技术目录 https://www.kimballgroup.com/data-warehouse-business-intelligence-resources/kimball-techniques/dimensional-modeling-techniques/ 。OLTP / OLAP 的表述以 GUIDING_PRINCIPLES §7、§8 为准。

| 概念 | 中文 | 本项目怎么用 |
|---|---|---|
| Grain | 粒度 | 取一条领域事件或过账明细（ADR-0025） |
| Fact table：Transaction、Periodic snapshot、Accumulating snapshot | 事实表类型 | 期间快照对应 Periodic snapshot（ADR-0023） |
| Conformed dimension | 一致性维度 | 各 BC 负责的主数据；阶段维度取全局阶段类型目录（intent `2026-10-16-061232` S1） |
| Enterprise Data Warehouse Bus Matrix | 总线矩阵 | 行 = 度量事件（过账、开票、工时等过账事实类型或领域事件类型；Kimball 原文称 business process，本项目不译作「业务过程」以免与 BIZBOK 业务流程混淆），列 = 一致性维度；由 SSOT 生成【已定】（L-10，intent `2026-10-16-112800`；行改称度量事件另见 METHOD 条目 12，L-6） |
| Slowly Changing Dimension Type 2 | 缓慢变化维第 2 类 | 主数据版本化（D6、D17） |

## 6. 数据管理（以本仓库规则为准）

DAMA-DMBOK 第 2 版修订版（2024，https://dama.org/dama-dmbok-revision/ ）只作概念参照：System of Record（记录系统）、Reference and Master Data（主数据）、敏感分级、保留期、血缘、数据质量规则落为约束。本项目的规则与登记以 SESSION_PLAYBOOK §14.1 D1–D19、`backend/architecture/data-access-registry.yaml`（DataArchitectureTest 双向校验）、`backend/architecture/data-classification.txt`（访问级别 + 个人信息标记两维度，ADR-0043）为准；不另建 `dama/data-objects.yaml`。业务对象（BIZBOK）不等于数据对象：信息图不进注册表（第 3.3 节）。

## 7. C4 model（只用 L1、L2）

出处：Simon Brown，https://c4model.com/ （CC BY 4.0，无版本号）。只用 System Context 与 Container 两层；组件层不用，聚合图不按 C4 画。

## 8. 推迟

| 框架 | 何时再议 | 已知事实 |
|---|---|---|
| DMN | 出现第一张真实决策表时（如 ADR-0016 决定 3 的科目映射），在 DMN 与 D15 版本化配置表 + 表驱动测试之间选，另开 ADR | Flowable 8.0.0（2026-02-27）DMN 解析器只支持 DMN 1.1、1.2、1.3（FIT_REVIEW_VERIFY 5.1） |

## 9. 不采用

- ADR-0041 已定：ArchiMate、ISO/IEC/IEEE 42010、Wardley 地图、Team Topologies、商业模式画布；REA 只保留第 1 节末尾的两个复审问题。
- 本稿新增【推出】：BIZBOK Part 6（含 §6.5）、Part 8 除 §8.6 名称查漏以外的内容、BIZBOK 其余未纳入章节；G178「退出即进入」的写法；G190 附录的业务对象定义与「由能力确立信息概念」；G211 的多级拆分与从组织推导能力（后两项按 intent `2026-10-15-222319` 所列范围）。
- CMMN 里程碑：只作旁注，不据此建模（`STAGE_TYPES_DRAFT.md` §3）。

## 10. 工程层标准（ADR-0041 决定 5）

照现行 `FRAMEWORKS.md` §9（9.1 合规义务、9.2 工程实践及其「不采用」一段）原样保留，本稿不改；落地时整节原文搬入，避免两处手写漂移。唯一同步：9.1「个人信息保护法」行的落点补「访问级别 + 个人信息标记两维度（ADR-0043）」。

## 11. 许可与引用方式

| 文本 | 条件 | 本项目做法 |
|---|---|---|
| BIZBOK v11 | Guild 条款允许授权用户为学习与 informative material 用途使用并注明出处（格式 Part #, Section #.#, Page #）；未经同意不得制作派生的参考资料、指南 | 只写概括与出处指引；不转录原文、不转写步骤、清单与定义；§8.6 只用能力名称核对、不抄定义。使用经用户决定（intent `2026-10-15-220121`） |
| TOGAF C220 与 G178、G190、G211 | 本地文本页脚标有评估版字样，版权页另有商业用途许可与生成式 AI 使用限制（适配评审页已提示用户） | 用户登记为「已有许可」（intent `2026-10-15-222350`）；此为用户与 The Open Group 之间的许可事项，本仓库只登记、不转录 |
| DDD Reference、C4 | CC BY 4.0 | 注明出处 |

## 12. 核实状态

| 项 | 状态 |
|---|---|
| BIZBOK v11 版本（Version 11.0，Copyright 2022） | 已核（2026-10-16，本地原文首页） |
| BIZBOK 章节起始页（§2.2、§2.4、§2.5、§2.8、§3.4、§6.5、§8.6） | 已核（2026-10-16，原文目录） |
| BIZBOK 节内页码（第 2 节表中其余页码） | opus 逐页核对（2026-10-15）；**待独立复核** |
| G178、G190、G211 版本行 | 已核（2026-10-16，本地原文版权页） |
| G178 §2.1、§2.2、§3.1；G190 §2、§4、§5；G211 §2.1.1 节号 | 沿用第 3 稿与适配评审；**待独立复核** |
| 其余条目 | 同现行 FRAMEWORKS 核实记录 |

## 13. 需用户确认的提议项

本稿已无待确认项。原唯一一项并入 METHOD 第 4 稿第 7.1 节用户题②，用户已答复 a（intent `2026-10-16-072440` M-2）：

| 项 | 提议 | 代价 |
|---|---|---|
| 公司手册与框架冲突的通则（原 P-F2）【已定】 | 业务内容按手册、建模概念按框架，只限建模概念的落点 | 手册字面（如「门完成才能进入下一阶段」）与系统行为可能不同，须在用户文档中说明 |

原 P-F1（G190、G211 与 BIZBOK 不一致时以 BIZBOK 为准）已删除，改为【推出】：intent `2026-10-15-222319` 按所列范围纳入，所列范围之外不用（第 0 节）。放弃的内容：G190 从能力出发的信息概念识别与 G211 的分层方法；若将来扩为多系统、需能力热图，须重新评审。

## 14. 与现行 FRAMEWORKS.md（ADR-0041）的差异

| 项 | 现行 | 第 4 稿 | 依据 |
|---|---|---|---|
| 业务架构参照 | 无（TOGAF 只用过渡架构与一级能力） | 新增 BIZBOK v11 五项（第 2 节） | intent `2026-10-15-222319` |
| TOGAF 系列指南 | 只列 G211（2022），一级能力只作范围核对 | 新增 G178、G190；G211 改为命名规则并注明 2025-05 编辑更新；一级 12 项作废、能力图重建 | intent `2026-10-15-222319`、`2026-10-15-221201` |
| 冲突规则 | 无 | 第 0 节：G178 与 BIZBOK 冲突按 BIZBOK（原文有据一处，G178 未作规定的两点按 BIZBOK）；G190、G211 按所列范围使用 | intent `2026-10-15-222350`、`2026-10-15-222319` |
| Bounded Context 释义 | 边界由语言、不变量与事务一致性共同决定 | 边界由语言与模型一致范围决定，不变量决定聚合【已定】（L-7） | DDD Reference；第 3 稿改正；intent `2026-10-16-112800` |
| 子域 | 未单列（`subdomains.yaml` 按 DDD 子域） | 只作命名空间分组 | intent `2026-10-15-221201` |
| 许可 | 未登记 | 第 11 节 | intent `2026-10-15-220121`、`2026-10-15-222350` |
| 不采用 | ADR-0041 五项 | 加 BIZBOK §6.5 与 Part 6、G178 冲突写法、G190 / G211 部分做法 | intent `2026-10-15-222319` 所列代价 |
| 删去未核实参照 | Vernon 2013 第 10 章聚合经验规则、PMBOK WBS 定义（凭记忆引用，未核实） | 删去，不作依据；WBS 三类节点的依据仍取 BIZBOK §2.5 与用户决定（intent `2026-10-15-215632`） | intent `2026-10-16-112800` L-9 |
| 防漂移规则 | 5 条 | 7 条（加冲突规则、许可受限文本的引用方式；区分「未核实」与「待独立复核」） | 本稿 |
| 工程层标准 | §9 | 原样保留（第 10 节），只同步 ADR-0043 | ADR-0043 |
