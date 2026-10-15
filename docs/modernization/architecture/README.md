# 架构 SSOT（施工图）

按框架分文件的机器可读架构，稳定 ID 互相引用；4 张图由它生成，CI 校验代码与它一致。设计见 `DESIGN.md`，现状速查见 `ARCH_CONTEXT.md`，维护会话 one-architecture（`sessions/one-architecture.md`）。

| 目录 | 内容 | 状态 |
|---|---|---|
| `ddd/` | `subdomains.yaml` 子域、`contexts.yaml` 限界上下文、`context-map.yaml` 上下文映射（只登记已建与本阶段在建的边，ADR-0041） | 初稿（PR-A）；未定项标 `undecided` |
| `ddd/aggregates.yaml` | 聚合、命令、事件、表（15 个，已建模块） | 初稿（PR-C） |
| `togaf/` | `transitions.yaml` 过渡架构（阶段 → BC）；`capabilities.yaml` 一级能力（范围查漏）；`vision.yaml`、`value-streams.yaml` 参考草稿 | 过渡架构已登记（PR-B）；能力与价值流不驱动 BC 推导（ADR-0041） |
| `bpmn/processes.yaml`、`dmn/decisions.yaml` | 流程清单（1 个可执行、6 个仅文档；改为由 `.bpmn20.xml` 生成前暂手写）、决策点清单（15 处，均在代码中；DMN 推迟，FRAMEWORKS §7） | 初稿（PR-C） |
| （OLAP） | 不另建文件：指标口径以 OAT 与 METRIC_BASELINE 为准，KPI → M-xx 只在 `togaf/vision.yaml` 登记（ADR-0041 决定 4） | — |
| `generated/` | ①a 上下文全景、①b 已建上下文映射、② 过渡架构、③ 跨 BC 事件链路、④ 战术聚合与契约；mermaid 源、SVG、`index.html`（生成，勿手改）。`build_architecture.py --svg` 本机渲染（mermaid-cli，SUPPLY_CHAIN S1），`--check` 校验 | PR-B |

规则：新 BC、聚合、事件、命令、流程、决策先在这里登记再写代码；老仓库与老代码只作 `evidence`；结构性选择交用户定（架构决策页）。

**规划项不是目标**（用户要求「避免按照旧系统架构直接现代化」，intent 2026-10-15-160111）：`status: planned` 的 BC（`basis: legacy-evidence` 来自老系统切法，`basis: adr` 来自已有 ADR 的新建决定）只是起点。进入对应阶段前，按 `METHOD.md` 从证据与不变量重新推导边界、名称与关系（ADR-0041），经评审（必要时交用户定）后改为目标条目；上下文映射的规划边不预登记。已建（`status: built`）条目以代码为准。
