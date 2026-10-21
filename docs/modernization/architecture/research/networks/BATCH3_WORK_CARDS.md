# 第三批派工卡：业务事实、维度、指标、计算绑定（度量链，ARC-1021-COO-10 批次 3）

状态：草稿｜2026-10-21｜负责：one-architecture

依据：`proposals/DECISION_IMPACT_NETWORK_PROPOSAL.md` §1.1、§3.2（用户 U1–U11 已定）；批次表 `research/networks/ADR_NODE_FREQUENCY.md`（度量链全缺，W5 前须补）；one-coordinator 2026-10-21 要求现在出卡，并与 W1 业财主干、BI 迁入口径对齐。

## 0. 本批共同约定

- **编号对照**：全景（`panorama_build.py`，ADR-0059 v5 的 15 节点）是 11 业务事实、12 维度、13 指标与评价、14 计算绑定，检查编号用 N11-*、N12-*、N13-*、N14-*，不得占用现有编号（N11-1、N13-1～3 已在用）。`ssot/` 目录沿用提案编号：`ssot/12-facts`、`ssot/13-dimensions`、`ssot/14-bindings`、`ssot/15-kpis`（与已建的 `ssot/10-objects`、`ssot/11-commands` 一致）。
- **W1 对齐**（ADR-0054 决定 1）：W1「主数据与业财主干」含 B-19 `accounting.period`、B-16 `operations.facts`、B-20 `accounting.ledger` 与迁入编排（`proposals/construction-plan.v1.yaml`）。`operations.facts` 是单一过账管道，只追加、更正靠冲销，业务 BC 不认识会计科目（ADR-0016，ADR-0031、0045 补充）。本批每条事实标 `wave`（按产生它的 BC 在 construction-plan 里的批次），W1 的事实优先登记、优先连通。
- **用语**（one-glossary 已结 NAM-1021-GLO-07）：「经营事实」只指 `operations.facts` 中业务认定的事实；BI 的 OLAP 度量在架构文档里叫「分析事实」，不进代码词表。「经营事实」已不是业务对象（`objects.yaml` OBJ-75 已去掉），事实只能由命令或事件产生（`produced_by`→CMD/EVT），不挂对象。
- **BI 迁入口径**（ADR-0054 决定 3 第 7 条）：撤 BI 镜像，投产日 BI 读 `reporting`，R0–R6 到 W5 完成；指标定义在 `operations.policy`（ADR-0023 决定 2–4）；财务事实直读过账明细（ADR-0025 决定 3）。`r0/BI_SCOPE.md` §4 的过渡镜像做法已被取代，卡里不得引用。
- **新前缀先过用语**（U9）：`FACT-`、`DIM-`、`BIND-`、`KPI-`（`M-` 沿用 `r0/METRIC_BASELINE_DRAFT.md`，`RM-` 读模型留第四批），开工前由本会话登记交 one-glossary（架构登记编号、非代码）。**同名异义先开题**：分析「维度」与数据访问注册表 role `DIMENSION_VERSIONED`（版本化主数据）——one-glossary 尚无结论，卡 13 开工前须有。
- **人工层与用户口径**：指标口径归业务（用户是 BI 负责人）；凡合并三套指标时需要裁定的，一律列「待用户定」攒确认页，本会话不自行裁定口径。
- 只登记、不改设计文件（SSOT 暂停令）；生成与手写分文件；每卡报条目数、自动生成数、自动化率、缺口数（U2）。基线：`claude/one-architecture-network-batch2` 合入 main 之后的 main。

## 卡 12　ARC-1021-ARC-12　业务事实登记（全景节点 11）

- **类别**：功能补做（抽取＋对照）。**执行者**：Codex（抽取，gpt-6.1-sol，medium）＋ one-architecture（经营事实 / 分析事实判定与 `produced_by`，sonnet 提议、本会话定稿）。
- **做什么**：
  1. Codex 写 `ssot/12-facts/extract_facts.py`，原样抽三处候选到 `facts.raw.yaml`：`objects.yaml` 的「产生的事实」列（约 81 条真正声明；「—（注释）」单列为未声明）；事件目录 59 条 EVT（标记是否带过账含义，以 `x-event` 实际字段为准，不猜）；`r0/BI_SCOPE.md` §3.2 总线矩阵的 16 行事实。
  2. one-architecture 写 `facts.yaml`：`id`（FACT-）、`name`、`kind`（经营事实 / 分析事实）、`produced_by`（CMD / EVT，可多）、`business_time` 与 `record_time` 的取法、`owner_bc`、`wave`、`sources`（三处原文引用）。对不上的列 `pending`。
  3. `panorama_build.py`：节点 11 读 `facts.yaml`；N11-1 改为「— （注释）」也计未声明（报告档，先报差异数再改）；新增 N11-2「每条经营事实有产生它的命令或事件」、N11-3「每条分析事实能追到经营事实」。
- **验收**：三处候选条数核对；sonnet 抽 15 条判定；W1 的事实单列清单。

## 卡 13　ARC-1021-ARC-13　维度登记（全景节点 12）

- **类别**：功能补做（抽取＋对照）。**执行者**：Codex（抽取，medium）＋ one-architecture。**前置**：卡 12；one-glossary 对「维度 / DIMENSION_VERSIONED」的结论。
- **做什么**：
  1. Codex 抽候选到 `ssot/13-dimensions/dimensions.raw.yaml`：`BI_SCOPE.md` §3.2 总线矩阵的列（维度）；`METRIC_BASELINE_DRAFT.md` §3 的 OAT 与「事实 BC」列；数据访问注册表中 role 为 `DIMENSION_VERSIONED` 的 7 张表（只列表名与生效区间列，不读数据）。
  2. one-architecture 写 `dimensions.yaml`：`id`（DIM-）、`name`、`slices`（FACT，可多）、`scd`（版本方式）、`backed_by`（主数据对象 OBJ 或表，可空）、`sources`。
  3. `panorama_build.py`：节点 12 读入；N12-1「每个维度至少切一条事实」、N12-2「总线矩阵的每个格子都对上 DIM×FACT」（报告档）。
- **验收**：总线矩阵 16 行逐格核对；DIMENSION_VERSIONED 7 张表各有归属或写明不是分析维度。

## 卡 14　ARC-1021-ARC-14　指标三套口径合一（全景节点 13）

- **类别**：功能补做（对照）。**执行者**：Codex（三套原样并列，medium）＋ one-architecture（对照提议，sonnet 起草、本会话定稿）；口径裁定交用户。**前置**：卡 12、卡 13。
- **做什么**：
  1. Codex 把三套原样并列到 `ssot/15-kpis/metrics.raw.yaml`：`METRIC_BASELINE_DRAFT.md` §3 的 48 个 M-xx（全部列）、`terms.yaml` kind=metric 79 条、`togaf/vision.yaml` 16 个 KPI；附条数核对。
  2. one-architecture 写 `kpis.yaml`：每个指标 `id`（KPI-，沿用 M-xx 作别名）、`name`、`term`（terms.yaml id）、`baseline`（M-xx）、`vision_kpi`（KPI-nn）、`facts`、`dimensions`、`layer`（L1/L2/L3）、`answers`（关注点 CON，可空，人工层）；三套对不上或同名不同口径的列 `needs_user`，不裁定。
  3. `panorama_build.py`：节点 13 改读 `kpis.yaml`（保留 terms 79 条为别名来源）；现有 N13-1～3 重算，报改源前后差异。
- **验收**：48＋79＋16 每条都落到一个 KPI 或列入 `needs_user` / `retired`；`needs_user` 清单交 one-coordinator 攒确认页。

## 卡 15　ARC-1021-ARC-15　计算绑定（全景节点 14）

- **类别**：功能补做（登记）。**执行者**：Codex（格式与校验，medium）＋ one-architecture。**前置**：卡 14。
- **做什么**：
  1. 以 `r0/tool-sample/metrics.sample.yaml` 的字段为格式参照，写 `ssot/14-bindings/bindings.yaml`：`id`（BIND-）、`kpi`、`fact`、`formula`（文字口径）、`formula_version`、`time_basis`（业务时间 / 记录时间 / 期间）、`grain`、`source_table`（`reporting` 中的读模型或过账明细，按 ADR-0025、ADR-0054 决定 3 第 7 条）。先只登记 W1 可算的指标与 `terms.yaml` 已带 formula 的 4 条，其余标 `pending`，不编造公式。
  2. `panorama_build.py`：节点 14 读入；新增 N14-1「每个绑定有事实、公式版本与时间基准」；N13-3（度量链）改为按绑定判定。
- **验收**：每个已登记绑定的公式能在 ADR 或 r0 口径原文找到出处；`pending` 清单与卡 14 的 `needs_user` 合并报调度。

## 依赖与顺序

前缀登记与「维度」同名异义开题 → 卡 12 → 卡 13 → 卡 14 → 卡 15。卡 12、13 的 Codex 抽取可并行起步，对照按序。卡 14 的口径裁定最慢（等用户），卡 15 先登记 W1 可算部分，不等全部口径。

## 本批交付后报告的数

| 层（全景编号） | 条目数 | 自动生成 | 自动化率 | 缺口 |
|---|---|---|---|---|
| 11 业务事实 | 待抽 | 候选全部 | 待交付 | 无产生命令或事件的经营事实数 |
| 12 维度 | 待抽 | 候选全部 | 待交付 | 未切任何事实的维度数 |
| 13 指标 | 48＋79＋16 合并后 | 原样并列 | 待交付 | needs_user 数 |
| 14 计算绑定 | W1 可算部分 | 0（人工） | 0%（人工维护） | pending 数 |
