# 架构正本路径对照与 proposals/ 读取例外

`ssot/` 是架构唯一来源（GOV-1023-ARC-01；用户 2026-10-23 选定，intent 2026-10-23-154833）。维护：one-architecture。

## 1. 旧路径 → 新路径

已采纳 ADR、intent、会话记录、任务卡里出现的旧路径都不改，按下表读。

| 旧路径（`architecture/` 下） | 现在 |
|---|---|
| `ddd/subdomains.yaml` | `ssot/d-domain/subdomains.yaml` |
| `ddd/contexts.yaml` | `ssot/d-domain/contexts.yaml` |
| `ddd/context-map.yaml` | `ssot/d-domain/context-map.yaml` |
| `togaf/transitions.yaml` | `ssot/t-transitions/transitions.yaml` |
| `togaf/vision.yaml` | `ssot/04-goals/vision.yaml`（节点 04 目标；KPI 口径以 `ssot/15-kpis/kpis.yaml` 为准） |
| `ddd/aggregates.yaml` | 已删（第②步）；已建聚合 `ssot/i03-aggregates/built.yaml`（由代码生成），不变量对照 `ssot/i03-aggregates/mapping.yaml`，原文见 git 历史 |
| `togaf/value-streams.yaml` | 退役；价值流以 `ssot/05-value-streams/value-streams.yaml` 为准（用户 2026-10-26 U08=A 升正本，原 `proposals/value-streams.v2.yaml`） |
| `proposals/value-streams.v2.yaml` | `ssot/05-value-streams/value-streams.yaml`（用户 2026-10-26 U08=A 升正本） |
| `proposals/ENTERPRISE_INFO_MAP_DRAFT.md` | `ssot/10-objects/ENTERPRISE_INFO_MAP.md`（用户 2026-10-26 U08=A 升正本；`ssot/10-objects/objects.yaml` 由它生成） |
| `togaf/capabilities.yaml` | 退役；能力由信息图重建时直接进 ssot（原 ADR-0050 决定 22 拟放 `business/`，未建） |
| `bpmn/processes.yaml` | 已删（第②步）；可执行 BPMN 登记为流程目录条目的 `runtime_bpmn`（工时审批 → PRC-23），用户任务与指派以 `.bpmn20.xml` 为准；documented 6 条只是 `process/1a/` 文档清单，见 `process/README.md` |
| `proposals/closeout/process-catalog.yaml` | `ssot/09-processes/catalog.yaml`（业务流程唯一登记；`CATALOG.md` 与生成校验脚本 `build_catalog.py` 同目录） |
| `process/<阶段>/*.md` | 不搬：阶段流程梳理文档（附 M4），是目录条目 `flow_docs` 指向的细节，不另成流程清单；平台机制（审批引擎、编号、审计、登录）的梳理只在这里，不进目录 |

## 2. 代码读 proposals/ 的限期例外

TEAM-108 定「proposals/ 不算权威，脚本不读」。例外登记在 `architecture/proposal_reads.yaml`（来源、到期日、kind），由 `architecture/proposal_reads.py` 在 `build_all.py` 里检查：未登记、到期仍读、登记过期都失败。升正本的次序由用户定：先价值流 v2 与信息图（已于 2026-10-26 升正本，U08=A，11-06 的登记已删），后聚合草稿与三组骨架（原 2026-11-20，按用户「B 到期日顺延到冻结后」顺延到 2026-12-01，intent 2026-10-26-122240）。

`proposals/STAKEHOLDERS.md` 由 panorama_build 写入、不读，不在例外内；它的去向随第③步定。
