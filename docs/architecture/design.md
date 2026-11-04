> 冻结（2026-11-04）：2026-10-15 的架构设计稿，不再更新；现行决定见 `docs/adr/current.md`，结构化事实见 `ssot/` 与 `events/`。

# 架构 SSOT 设计说明（one-coordinator 2026-10-15 审过，按 PR-A→D 推进）

依据：intent `2026-10-15-160111-one-coordinator-本仓库是从旧系统现代化到新系统.md`（「像施工图一样」，4 张图）、`2026-10-15-151621`（地基架构守护三层方案）。会话：one-architecture。

## 1. 原则

- **先登记、后写代码**：新 BC、聚合、事件、命令、流程、决策先在 SSOT 登记，再写代码；CI 校验代码与 SSOT 一致。
- **只登记新系统的目标架构**：老仓库 `00_战略设计` 与老代码只作证据，在条目的 `evidence` 字段里引用，不照搬切分方式。
- **已有 SSOT 只引用不复制**：下表「引用」一栏里的文件仍是唯一来源，本目录只存它们没有的那一层，用 ID 指过去。
- **结构性选择交用户定**：本会话只给推荐。未定的条目标 `status: undecided` 并写 `decision_ref`。

## 2. 文件与 ID（`docs/architecture/`，YAML）

| 框架 | 文件 | 稳定 ID | 主要字段 | 引用（不复制） |
|---|---|---|---|---|
| TOGAF | `togaf/capabilities.yaml`（已退役，GOV-1023-ARC-01） | `CAP-xx[.y]` | 名称、层级、上级、实现它的 BC | 能力来源待定（见 §6 Q1） |
| TOGAF | `togaf/value-streams.yaml`（已退役，GOV-1023-ARC-01） | `VS-xx`，阶段 `VS-xx.n` | 阶段 → 能力、BC、触发事件 | 同上 |
| TOGAF | `ssot/t-transitions/transitions.yaml` | `TA-<阶段>`，批次 `TA-1a.W1` 等 | 交付的 BC / 聚合 / 流程、入口与出口条件、状态（已建 / 在建 / 规划） | brief 各 Phase、`phase1a/NEXT_BATCH_PLAN.md` |
| DDD | `ssot/d-domain/subdomains.yaml` | 子域 slug（`cost`） | 中英文名、类型（核心 / 支撑 / 通用，用户定） | 老 `02-子域划分.md` 作证据 |
| DDD | `ssot/d-domain/contexts.yaml` | 模块名（`cost.timesheet`） | 子域、中文名、蓝图 BC 编号（ADR-0017）、schema、状态（已建 / 规划 / 退役）、负责会话 | `terms.yaml` 的 `bc`、`retired-module-schemas.txt` |
| DDD | `ssot/d-domain/context-map.yaml` | `<上游>><下游>` | 只登记已建与本阶段在建的边：经由（api 或 events）、有代码后果的关系（防腐层、开放主机 / 发布语言、共享内核、遵奉者，普通依赖不写类型）（ADR-0041） | `module-dependencies.txt`：已建边集合相等（A1，`build_architecture.py` 实现） |
| DDD | `ssot/i03-aggregates/built.yaml`（由代码生成；规划中的见同目录 `planned.yaml`） | `<模块>/<聚合>` | 根类、命令、产生的事件、表；不变量对照 `mapping.yaml` | 事件目录 `events/*.yaml` 的 `eventType`；数据访问注册表的表键 |
| BPMN | `ssot/09-processes/catalog.yaml` | PRC 编号 | 名称、类型、阶段、编排 BC、主对象；可执行 BPMN `runtime_bpmn`（用户任务与指派以文件为准）、流程梳理文档 `flow_docs` | `processes/*.bpmn20.xml`（每个恰被一条引用，`build_catalog.py` 校验）、`process/<阶段>/*.md`；原 `bpmn/processes.yaml` 已并入（GOV-1023-ARC-01）；ADR-0041 决定 4 要生成的指派细节不再另行登记，直接以 `.bpmn20.xml` 为准 |
| DMN | `dmn/decisions.yaml` | `DEC-xx` | 输入、输出、所属 BC、规则卡、执行方式 | 决策点清单（均在代码中）；DMN 推迟到第一张真实决策表，届时另开 ADR 在 DMN 与版本化配置表之间选（FRAMEWORKS §7） |
| DAMA | 不建文件（ADR-0041 决定 4） | — | 数据类别、记录系统、负责 BC、敏感分级以数据访问注册表与 `data-classification.txt` 为准 | 数据访问注册表、`data-classification.txt` |
| OLTP | 不新建文件 | — | BC → 模块 → schema → 表由生成器从 `contexts.yaml`、迁移目录与注册表推导 | 迁移目录、数据访问注册表 |
| OLAP | 不另建文件 | `M-xx` | 口径以 OAT 与 `docs/data/metric-baseline/baseline-draft.md`（确认后的正式版）为准；KPI → M-xx 只在 `ssot/04-goals/vision.yaml` 登记（ADR-0041 决定 4） | OAT、METRIC_BASELINE |

**追溯链**（ADR-0041）：规则卡 / 不变量 → 聚合 → 事件 → BC → 流程 → 表（注册表）→ 分析事实 → 指标；一级能力只作范围查漏。追溯表为生成的报告，不作合并门禁。

## 3. 生成器与 4 张图

- 生成器：`architecture/build_architecture.py`，接入 `build_all.py --check`，保证产物可重现。
- 产物：`architecture/generated/*.mmd`（mermaid 源）和追溯表；在线页集中展示 4 张图，私有，手机可看，不引用境外资源。
  1. **上下文映射**：子域分组，BC 按已建 / 规划着色，边上标关系类型。
  2. **过渡架构与建设批次**：阶段 × BC 甘特 / 泳道。
  3. **价值流与事件链路**：只画已有代码的 BC 和 1a 价值流（登录、组织同步、工时审批），其余标「规划」。
  4. **战术聚合与契约**：每个已建 BC 的聚合、命令、事件、api 端口。
- 先出 ①②，再出 ③④。

## 4. CI 校验

- 脚本：`scripts/ci/architecture_check.py`，加 `--self-test`，在 gitleaks job 里跑。守护文件改动单列，交 opus 复审。

| # | 校验 |
|---|---|
| A1 | `context-map.yaml` 的边集合等于 `module-dependencies.txt` 的边集合（「已声明但代码不再使用的边」登记为后续项，one-coordinator 2026-10-15） |
| A2 | BC、Java 模块包、schema 目录一一对应（退役与无 schema 的技术模块需显式登记） |
| A3 | 聚合的事件都在事件目录里，事件目录的 `aggregate` 都在 `aggregates.yaml`；聚合根类存在 |
| A4 | 每个已建模块在 `contexts.yaml` 登记；`terms.yaml` 的 `bc` 取值都是已登记的 BC 或子域（规划 BC 也要登记） |
| A5 | kernel 白名单：现有类先登记为「待用户决定去向」，只拦新增 |
| A6 | `architecture_change_guard`（仿 `acceptance_guard.py`）：改 `architecture/` 或 `module-dependencies.txt` 的提交须带 trailer `Architecture-Ref: ADR-xxxx` 或 `intent <文件名>` |
| A7 | ADR-0003 偏差（例如 BC 编号的载体）登记到 `docs/adr/readme-deviations.md`；CODEOWNERS 按用户既定「生产前再定」，本期不加 |

## 5. 交付顺序（每步一个 PR，PR 由 one-coordinator 入队）

1. **PR-A**：本说明、SSOT 骨架、`subdomains.yaml` / `contexts.yaml` / `context-map.yaml` 初稿（从老 `02`、`04`、`05` 抽取并按目标架构修正）、`ARCH_CONTEXT.md`、画像；同时发布架构决策页（§6）。
2. **PR-B**：生成器与图 ①②，在线图页。
3. **PR-C**：`aggregates.yaml`、BPMN / DMN / DAMA / OLAP 清单，图 ③④。
4. **PR-D**：`architecture_check.py` 与 change guard（守护，opus 复审）；DATA_DESIGN_CHECKLIST §14 增加「架构施工图」一条。

## 6. 待决

**交用户（架构决策页，推荐预选）**
1. **Q1 能力与价值流的来源**（2026-10-15 用户：forcitis-architecture「是我学习的副产物，不是信源」，intent 163920；改为在本仓库按 FRAMEWORKS / METHOD 第 1 步推导，本条不再上决策页）：本机 `forcitis-architecture` 仓库已有经批准的 TOGAF 产物（S01 能力推导、价值流），但它的 GOV-0006 规定文件只留在本地、不提交、不推送。可选：只引用其编号（推荐）、摘录进本仓库，或本仓库另起。
2. **Q2 平台**：算一个 BC 还是多个（ADR-0003 现为一个子域多个技术模块）。
3. **Q3 考勤与工资**：`organization.attendance` 与规划中的 `organization.payroll` 的边界。
4. **Q4 kernel 的去向**：6 个跨模块端口（SeparationReview、KnownLegalEntities、AccountEmployees、OrgUnitAccess、CallerPermissions、IdempotentResponses）、DocumentType、WecomUserId、ProjectId / WbsNodeId / ProjectCoordinate。
5. **Q5 蓝图 BC 编号的载体**：ADR-0017 第 1 条要求在 package-info 登记，与 ADR-0003「BC 层不用 `@ApplicationModule`」字面冲突。推荐改登记在 `contexts.yaml`，作为偏差记录。
6. **Q6 子域分类**：核心 / 支撑 / 通用。
7. **Q7 术语的 bc 取值**：`terms.yaml` 中约 270 条术语的 `bc` 是规划 BC（老系统切法），另有 31 条指向已退役的 `delivery.execution`。怎么对应到目标 BC，可以作为规划 BC 登记，或交 one-glossary 改。

**one-coordinator 已确认（2026-10-15）**
- mermaid-cli：用户批准本机安装。锁定版本，装在仓库外 `~/.modernize/psa/tools/mermaid-cli`，登记 SUPPLY_CHAIN.md（只在本机生成图，不进发布包与 SBOM）与 RESOURCES.md；SVG 入库，在线页只嵌静态 SVG，不加载外部脚本或字体。
- PR-A 与决策页同时提交，未定条目标 `undecided`。
- Q1 推荐「只引用 forcitis-architecture 编号」认可，遵守其 GOV-0006，不复制内容；Q7 由 one-glossary 在用语侧执行。
