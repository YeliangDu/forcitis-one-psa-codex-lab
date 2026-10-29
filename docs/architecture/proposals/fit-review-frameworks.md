# 架构框架适配评审（对抗式，2026-10-15）

状态：历史记录｜2026-10-15｜负责：one-architecture

起因：用户 2026-10-15「关于架构的任何选择都要非常谨慎，必须深入研究后才能决策」（intent `2026-10-15-193135`）。评审：opus 子代理（architecture-critic，只读、未联网），one-architecture 抽查核实（见文末）。结论待用户在确认页重新定；在用户决定前，FRAMEWORKS.md 不改。

评审对象：`docs/architecture/frameworks.md` §1–§8、`METHOD.md`、`DESIGN.md`，ADR-0040、0016、0003、0017。

系统实况（从文件核实）：单家 AEC 专业服务公司内部业财一体系统，335 名活跃用户（MODERNIZATION_BRIEF §1）；Spring Modulith 单体；`ddd/contexts.yaml` 已建 13、规划 28，已建业务模块只有 `cost.timesheet`、`organization.*`；生产中 1 个 BPMN 流程，无 DMN；多个 Claude 会话开发，一人拍板；FRAMEWORKS、METHOD、ADR-0040、6 项补充框架、§9 工程标准均于 2026-10-15 经勾选页采纳。

## 汇总

| 框架 | 结论 | 把握 | 一句理由 |
|---|---|---|---|
| TOGAF 10 | 收窄：只留基线 / 目标 / 过渡架构与路线图；能力地图降为一级覆盖核对 | 高 | ADM 治理机制一项未用；单系统真正在用的只有过渡架构 |
| DDD | 保留；上下文映射关系类型收窄、事件风暴改为「事件清单推导」、子域分类降级 | 高 | 唯一既有代码守护又产出硬决策的框架 |
| BPMN 2.0.2 | 保留，只用可执行 Process；登记表由 `.bpmn20.xml` 生成 | 高 | Flowable 实际在执行 |
| DMN 1.6 | 推迟到第一张真实决策表 | 中 | 目前一张没有；锁的是 OMG 版本而非引擎支持版本 |
| DAMA-DMBOK2R | 收窄：以 §14.1 D1–D19 与数据访问注册表为准；不建 `dama/data-objects.yaml`；去 GB/T 36073 | 高 | 数据治理已落在注册表与守护，DMBOK 表格是挂名 |
| Kimball / OLTP / OLAP | 保留粒度、事实表类型、一致性维度、SCD2；纠正总线矩阵定义 | 高 | 直击「多套算法、无期间快照」 |
| ArchiMate | 去掉 | 高 | 已声明不作硬依据，无模型无图 |
| ISO/IEC/IEEE 42010 | 去掉 | 高 | 视图由同一份 YAML 生成，一致性由生成保证 |
| Wardley Maps | 去掉 | 中 | 自建已定；其「商品化即外购」与总账自建冲突 |
| REA | 去掉框架身份，留两个复审问题 | 中 | ADR-0016 已体现要义；正式采纳会带入与 terms.yaml 并行的词汇 |
| C4 | 收窄：只用 L1、L2 | 高 | brief 与 REIMAGINED 已在用；组件层不等于聚合图 |
| Team Topologies | 去掉 | 高 | 「团队」是可随时重开的会话，§2.1 会话画像已覆盖 |
| 商业模式画布 | 去掉 | 高 | 商业模式是给定输入，不是本系统设计变量 |

## 发现（按严重度）

**Blocker**
- **B1 第 2 步输入不独立。** `togaf/capabilities.yaml` 的 77 个 L2 能力逐项带 `current_bc`（指向 36 个现有或规划 BC，含 `platform.scheduling`、`platform.migration`），证据来自老系统 OA 模板与规则卡。按它「按能力重推 BC」要么是给现有切法盖章，要么无新证据地来回改。建议：第 0、1 步确认页暂缓；能力地图若保留，删 `current_bc` 与 `strategic_hint`，只留一级 12 项作范围覆盖核对。

**High**
- **H1 优先级倒置。** ADR-0040 决定 3 允许重推结果「新开 ADR 取代」ADR-0016 决定 1、5、9；但单一过账管道、期间闸门、WIP 与开票同一事务边界来自会计规则与生产取证，能力地图是一天起草的草稿。建议明确「有证据的不变量 ADR 约束能力地图，反之不成立」。
- **H2 6 项补充框架只挂名。** `togaf/`、`ddd/` 中 Wardley、画布、REA、42010、Team Topologies、ArchiMate 出现 0 次；勾选页把 C4、Team Topologies、画布标「可选」，未写代价，结果全选（intent 182238）。FRAMEWORKS 每次清空后都注入，无用框架是持续噪声，也给误用留口子。建议删除或移入「不采用」附录。
- **H3 上下文映射基本是老切法。** `ddd/context-map.yaml` 1,134 行、129 条边，109 条 `planned` 且 `basis: legacy-evidence`；97 条 customer-supplier 几乎不携带信息；Partnership / Customer-Supplier / Conformist 是团队间关系，单团队单体中无约束力；`separate-ways` 登记成边（:1115、:1125）自相矛盾。建议删规划边，到阶段再登记；关系类型只留有代码后果的：ACL、OHS/PL（api 或 events 契约）、共享内核、遵奉者。
- **H4 同一事实多处手写，已漂移。** 上下文映射 4 处（`module-dependencies.txt`、`context-map.yaml`、REIMAGINED §2.2、GUIDING_PRINCIPLES §3）；聚合与表 3 处（注册表 `aggregate` 字段、规划的 `aggregates.yaml`、`data-objects.yaml`）。已漂移：GUIDING_PRINCIPLES:38、:68、:188 写 PG17（brief 定 18）；:53 写「`03-通用语言.md` 是唯一词源」（应为 terms.yaml）；:54「各外部系统各有独立 integration 模块」与 ADR-0039 不一致。建议每个事实一个手写源，其余生成；GUIDING_PRINCIPLES 各框架章节改为指针。

**Medium**
- M1 能力地图混入技术与一次性能力（CAP-12.04 定时作业、12.07 审计留痕、12.08 历史数据迁入），不符合 G211 业务能力定义。
- M2 「ABB = BC」是误用（FRAMEWORKS §1、GUIDING_PRINCIPLES:38）：ABB 是与实现无关的构件规格，BC 是语言与模型边界。
- M3 总线矩阵定义错：FRAMEWORKS:77、METHOD:22/:34 把行定为价值流阶段，Kimball 的行是业务过程（度量事件：过账、开票、工时）。建议行改为过账事实类型或领域事件类型。
- M4 DMN 锁定与预登记不当：锁 OMG 1.6，但执行的是 Flowable 8 DMN 引擎（支持版本未核实）；`dmn/decisions.yaml` 会提前承诺 DMN；科目映射也可用 D15 版本化配置表。到第一张表时再二选一。
- M5 追溯完整性 CI 守的是登记是否齐全，拦不住行为错误；建议降为生成报告，不做门禁。
- M6 vision.yaml 的 KPI 实为项目出口条件（KPI-01 OA 模板停用率、KPI-16 安全不变量等），OQ-01 也承认无业务目标；可保留作阶段出口度量，不驱动 BC 推导。

**Nit**
- FRAMEWORKS §5 DMBOK 11 个知识领域标「须核实」却以表格出现；GB/T 36073 是组织成熟度评估，对单系统建设无用。
- FRAMEWORKS §8 把 C4 派给图④战术聚合，C4 组件层不是聚合模型。

## 逐项要点

1. **TOGAF 10**：在用的是过渡架构（`togaf/transitions.yaml` 与图②）；能力与价值流已起草未确认；Architecture Board、Contract、合规评审、Enterprise Continuum、Requirements Repository 未用；METHOD 与 GUIDING_PRINCIPLES §2 重复对应 ADM 阶段。范围漏项已由 OA 模板清单 80 个、规则卡 315 条、brief §4 业务走查覆盖。代价：4 份 YAML 共 1,756 行、用户逐项确认 77 项。→ 收窄。
2. **DDD**：有守护的部分（BC = 模块 = schema、terms.yaml、聚合 / 事件 / 值对象 §14、ACL）收益高，ADR-0016 主干即来源于此。挂名或误用：团队关系类型、子域三分类（自建已定，唯一有用后果是「哪里值得深建模」）、事件风暴（无领域专家工作坊）、技术模块登记成 BC。→ 保留；技术模块标 `kind: technical`；子域分类改为单一标记「深建模（规则密集或触账）/ 薄 CRUD」；事件风暴改名「事件清单推导」（输入规则卡 + 生产取证，用户裁定）。
3. **BPMN**：只编排人工步骤，用法克制；GUIDING_PRINCIPLES §4「4 条人物流程协作图」仓库中未找到（只按 `.bpmn20.xml` 搜过）。→ 保留，登记表由 xml 生成。
4. **DMN**：「业务方可读可改」的卖点在一人决策、改动走 PR、手机看不了 XML 的情况下不成立；D15 配置表守护更完备。→ 推迟。
5. **DAMA**：真用的是 §14.1 D 清单与注册表（DataArchitectureTest 双向校验）。→ 收窄。
6. **维度建模**：reporting 只读、期间快照（ADR-0023）、OAT 口径、粒度取过账明细（ADR-0025）均有落点。→ 保留四概念并纠正总线矩阵。
7. **ArchiMate**：无模型、无工具、无图。→ 去掉。
8. **42010**：解决多方各维护视图的一致性问题；这里视图同源生成，接缝已由 CI A1–A7 校验。→ 去掉。
9. **Wardley**：`strategic_hint` 未引用；`capabilities.yaml:664`、`:729` 自承「分类与是否自建是两回事」。→ 去掉（若以后重议外购总账可再用）。
10. **REA**：思想与 ADR-0016 相近，但会引入并行词汇，违反 CLAUDE.md 第 2 条；ISO/IEC 15944-4 是电子商务标准，过重。→ 去掉框架身份，把「每个经济事件的对偶事件在哪里」「合同承诺如何成为事实」加入 ADR-0016 相关阶段的复审清单。
11. **C4**：brief §2、REIMAGINED §1 已有 `C4Container`。→ 只用 L1、L2，作第 5 张生成图或留在 brief。
12. **Team Topologies**：前提是长期人类团队与认知负荷；会话可重开、交接规则已处理上下文上限。→ 去掉。
13. **商业模式画布**：vision.yaml 未用；会诱导无证据目标。→ 去掉。

## 方法链评估（第 0 步愿景 → 第 1 步能力 / 价值流 → 第 2 步按能力推 BC）

问题：
1. **循环**：见 B1。
2. **判据错位**：DDD 中 BC 边界由统一语言一致范围、不变量与事务一致性决定，不由能力决定。本项目最关键的边界（单一过账管道、期间闸门、WIP 与开票同一事务、同步运行与快照同事务）都来自会计不变量与生产取证。METHOD 接缝 1「每个叶子能力只归一个 BC」会逼着把能力切碎，收入确认本就横跨验收、WIP、过账、总账。
3. **优先级倒置**：见 H1。
4. **第 0 步价值有限**：目标汇编有用，但 KPI 是迁移出口条件，适合作阶段出口度量，不适合作边界推导起点。

更简单、同样可靠的路径（METHOD 第 3–7 步加现有清单，第 0–2 步降级）：
1. 范围 = OA 模板清单 + 规则卡 + 生产取证 + 业务走查；能力地图只留一级作漏项核对。
2. 不变量先行：从规则卡与会计规则找不变量与一致性需求 → 聚合 → 按统一语言聚成 BC。
3. 用价值流（brief §4 业务走查）端到端走事件链，查断点与孤儿事件。
4. 数据与分析：§14.1 + 注册表；分析侧声明粒度、做期间快照。
5. 过渡架构与守护：同 METHOD 第 6、7 步。

优先级规则：已采纳、有证据的不变量 ADR 高于任何由能力地图推出的边界。

## 漏掉但更关键的方法（均未核实，需联网查一手来源）

1. 会计软件与会计信息化合规：财政部《会计软件基本功能和服务规范》《会计信息化工作规范》（2024）。GOAL-03 与 ADR-0022 要年度切换法定账，届时是硬约束。
2. 内部控制：《企业内部控制基本规范》及应用指引（职责分离、关账控制、凭证审核）；`SeparationReview` 已做一部分，缺成体系的控制清单。
3. 双时态建模（Snodgrass 1999；Fowler「Bitemporal History」）：对应关账后更正标注原期间（ADR-0016）、D6/D17 版本化、OBSERVED 与 DECLARED。
4. 适应度函数（Ford、Parsons、Kua，*Building Evolutionary Architectures*）：现有 ArchUnit、Modulith、DataArchitectureTest 守护即是，用它描述「架构 ↔ 代码」比 42010 贴切。
5. 特征化测试与影子比对（Feathers 2004）：对应 CLAUDE.md 第 4 条与 ADR-0016 影子比对基线，建议写进 METHOD 迁移步骤。

## 未核实

- 本次评审未联网，FRAMEWORKS 所列 URL 未重新访问。
- Flowable 8 DMN 引擎支持的 DMN 版本与子集。
- REA 出处细节（McCarthy 1982 页码、ISO/IEC 15944-4:2015 现行状态）与「核心模型不存借贷」的原文表述。
- 「漏掉的方法」各条的文号、版次、URL。
- ArchiMate 4 中 Capability、Value Stream 的定义。
- GUIDING_PRINCIPLES §4 的 4 条人物流程协作图（可能以其他格式存放）。

## one-architecture 抽查（2026-10-15）

- `capabilities.yaml` 中 `current_bc` 出现 78 次（含 1 行注释），B1 属实。
- `context-map.yaml` 中 `legacy-evidence` 出现 110 次，H3 属实。
- `togaf/`、`ddd/` 中 Wardley、商业模式画布、42010、Team Topologies、ArchiMate、REA 出现 0 次，H2 属实。
- GUIDING_PRINCIPLES:38、:68、:188 写 PostgreSQL 17 / PG17，H4 漂移属实。
- ADR-0040 决定 3 原文允许「重推结果与这些 ADR 不同时，新开 ADR 取代」，H1 前提属实。
