# 架构 SSOT（施工图）

按框架分文件的机器可读架构，稳定 ID 互相引用；4 张图由它生成，CI 校验代码与它一致。设计见 `DESIGN.md`，现状速查见 `ARCH_CONTEXT.md`，维护会话 one-architecture（`sessions/one-architecture.md`）。

| 目录 | 内容 | 状态 |
|---|---|---|
| `ssot/` | **架构唯一来源**（GOV-1023-ARC-01，intent 2026-10-23-154833）：按登记网络 v5 节点分目录（ADR-0059）；`d-domain/` 子域、限界上下文、上下文映射（节点 09、10 的领域映射层）；`t-transitions/transitions.yaml` 过渡架构（阶段 → BC）；`04-goals/vision.yaml` 目标与愿景 KPI（草稿）；`i03-aggregates/` 聚合 | 各文件头标明手写或生成 |
| 已退役 | `ddd/aggregates.yaml`、`bpmn/processes.yaml`（已删，分别由 `ssot/i03-aggregates/`、`ssot/09-processes/` 取代）、`togaf/value-streams.yaml`（由价值流 v2 取代，待升正本）、`togaf/capabilities.yaml`（由信息图重建时直接进 ssot）；旧路径对照见 `ssot/PATHS.md` | — |
| `ssot/09-processes/catalog.yaml`、`dmn/decisions.yaml` | 业务流程唯一登记（PRC 编号，含可执行 BPMN 引用 `runtime_bpmn` 与流程梳理文档 `flow_docs`；`CATALOG.md` 由同目录 `build_catalog.py` 生成并校验）、决策点清单（15 处，均在代码中；DMN 推迟，FRAMEWORKS §7） | 目录条目多为 draft；决策点初稿（PR-C） |
| （OLAP） | 不另建文件：指标口径以 OAT 与 METRIC_BASELINE 为准，KPI → M-xx 在 `ssot/15-kpis/kpis.yaml` 登记（愿景 KPI 原文在 `ssot/04-goals/vision.yaml`）（ADR-0041 决定 4） | — |
| `generated/` | ①a 上下文全景、①b 已建上下文映射、② 过渡架构、③ 跨 BC 事件链路、④ 战术聚合与契约；mermaid 源、SVG、`index.html`（生成，勿手改）。`build_architecture.py --svg` 本机渲染（mermaid-cli，SUPPLY_CHAIN S1），`--check` 校验 | PR-B |

规则：新 BC、聚合、事件、命令、流程、决策先在这里登记再写代码；老仓库与老代码只作 `evidence`；结构性选择交用户定（架构决策页）。

**规划项不是目标**（用户要求「避免按照旧系统架构直接现代化」，intent 2026-10-15-160111）：`status: planned` 的 BC（`basis: legacy-evidence` 来自老系统切法，`basis: adr` 来自已有 ADR 的新建决定）只是起点。进入对应阶段前，按 `METHOD.md` 从证据与不变量重新推导边界、名称与关系（ADR-0041），经评审（必要时交用户定）后改为目标条目；上下文映射的规划边不预登记。已建（`status: built`）条目以代码为准。
