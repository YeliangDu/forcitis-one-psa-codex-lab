# 架构 SSOT（施工图）

按框架分文件的机器可读架构，稳定 ID 互相引用；4 张图由它生成，CI 校验代码与它一致。设计见 `DESIGN.md`，现状速查见 `ARCH_CONTEXT.md`，维护会话 one-architecture（`sessions/one-architecture.md`）。

| 目录 | 内容 | 状态 |
|---|---|---|
| `ddd/` | `subdomains.yaml` 子域、`contexts.yaml` 限界上下文、`context-map.yaml` 上下文映射 | 初稿（PR-A）；未定项标 `undecided`，等架构决策页 |
| `ddd/aggregates.yaml` | 聚合、命令、事件、表 | PR-C |
| `togaf/` | 能力（只引 forcitis-architecture 编号）、价值流、过渡架构与批次 | PR-B / PR-C |
| `bpmn/`、`dmn/` | 流程清单、决策清单 | PR-C |
| `dama/` | 数据对象与类别 | PR-C |
| （OLAP） | 不另建文件：指标口径以 OAT 与 METRIC_BASELINE 为准，KPI → M-xx 只在 `togaf/vision.yaml` 登记（ADR-0040 决定 4） | — |
| `generated/` | mermaid 源、SVG、追溯表（生成，勿手改） | PR-B 起 |

规则：新 BC、聚合、事件、命令、流程、决策先在这里登记再写代码；老仓库与老代码只作 `evidence`；结构性选择交用户定（架构决策页）。

**规划项不是目标**（用户要求「避免按照旧系统架构直接现代化」，intent 2026-10-15-160111）：`status: planned` 的 BC 与上下文关系中，`basis: legacy-evidence` 来自老系统切法（老 04、05），`basis: adr` 来自已有 ADR 的新建决定；两者都只是起点。进入对应阶段前，必须按能力地图与价值流重新推导边界、名称与关系，经架构评审（必要时交用户定）后改为目标条目，不能直接照搬为目标。已建（`status: built`）条目以代码为准。
