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
| `ddd/aggregates.yaml` | 退役；已建聚合 `ssot/i03-aggregates/built.yaml`（由代码生成），不变量对照 `ssot/i03-aggregates/mapping.yaml`。文件暂留、冻结不改：两个 R-1b P5 改名验收测试仍读它，验收测试须由 test-engineer 提交改路径，改完后删除 |
| `togaf/value-streams.yaml` | 退役；价值流以 `proposals/value-streams.v2.yaml` 为准，升正本后进 ssot 节点 05 |
| `togaf/capabilities.yaml` | 退役；能力由信息图重建时直接进 ssot（原 ADR-0050 决定 22 拟放 `business/`，未建） |
| `bpmn/processes.yaml` | 暂留，第②步与 `process/`、`proposals/closeout/process-catalog.yaml` 合一 |

## 2. 构建脚本读 proposals/ 的限期例外

TEAM-108 定「proposals/ 不算权威，脚本不读」。下列来源在升正本前暂准读，到期未升的由第②步加的检查拦下。升正本的次序由用户定：先价值流 v2 与信息图，后聚合草稿。

| 来源（`architecture/proposals/` 下） | 读取脚本 | 批次 | 期限 |
|---|---|---|---|
| `value-streams.v2.yaml` | panorama_build | 一 | 2026-11-06 |
| `ENTERPRISE_INFO_MAP_DRAFT.md` | panorama_build、ssot/10-objects/convert_objects | 一 | 2026-11-06 |
| `closeout/process-catalog.yaml` | panorama_build、object_freeze_check | 第②步流程合一 | 2026-11-06 |
| `ENTERPRISE_AGGREGATES_DRAFT.md` | panorama_build、scan_aggregates、ssot/c1-rules/convert_inv | 二 | 2026-11-20 |
| `closeout/*/commands.yaml`、`closeout/*/state-machines*`、`closeout/RESPONSIBILITY_MATRIX.yaml` 及骨架模块 | panorama_build、scan_commands、scan_org_roles、convert_objects、object_freeze_check | 二 | 2026-11-20 |

`proposals/STAKEHOLDERS.md` 由 panorama_build 写入、不读，不在例外内；它的去向随第③步定。
