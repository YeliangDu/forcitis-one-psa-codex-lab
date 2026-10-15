# 架构 SSOT（施工图）

按框架分文件的机器可读架构，稳定 ID 互相引用；4 张图由它生成，CI 校验代码与它一致。设计见 `DESIGN.md`，现状速查见 `ARCH_CONTEXT.md`，维护会话 one-architecture（`sessions/one-architecture.md`）。

| 目录 | 内容 | 状态 |
|---|---|---|
| `ddd/` | `subdomains.yaml` 子域、`contexts.yaml` 限界上下文、`context-map.yaml` 上下文映射 | 初稿（PR-A）；未定项标 `undecided`，等架构决策页 |
| `ddd/aggregates.yaml` | 聚合、命令、事件、表 | PR-C |
| `togaf/` | 能力（只引 forcitis-architecture 编号）、价值流、过渡架构与批次 | PR-B / PR-C |
| `bpmn/`、`dmn/` | 流程清单、决策清单 | PR-C |
| `dama/` | 数据对象与类别 | PR-C |
| `olap/` | 指标（沿用 M-xx） | PR-C |
| `generated/` | mermaid 源、SVG、追溯表（生成，勿手改） | PR-B 起 |

规则：新 BC、聚合、事件、命令、流程、决策先在这里登记再写代码；老仓库与老代码只作 `evidence`；结构性选择交用户定（架构决策页）。
