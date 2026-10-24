# 架构收尾：分组工作说明（开工必读）

计划：`../ARCH_CLOSEOUT_PLAN.md`（用户 2026-10-16「确认」）。画像草稿：计划 §4.1。把关：one-architecture。

## 1. 分组与实数

- 流程数一律以唯一目录 `architecture/ssot/09-processes/catalog.yaml`（生成页同目录 `CATALOG.md`）为准；2026-10-16 为 146 条，其中业务流程 91 个（⑥a 的 82 个加 PRC-136–144）。下表是开工时按 v1 的分组，仅作历史。
- 按流程的 `owner_bc` 归组，清单见 `assignments.yaml`：

| 组 | 会话 | BC | 流程 | 人工编排 |
|---|---|---|---|---|
| revenue | one-arch-revenue | B-01–B-06、B-08、B-09、B-26、B-28、B-29 | 39 | 17 |
| finance | one-arch-finance | B-07、B-10–B-12、B-16、B-17、B-19–B-24 | 24 | 5 |
| people | one-arch-people | B-13–B-15、B-18、B-27、B-30 | 19 | 8 |

- 聚合按 BC 归组（聚合稿 §3.2 的 AGG 区间）；事件策略与单聚合维护随其 BC 归组，第 ① 项只补跨 BC 的部分。

## 2. 目录与文件

- 每组只写 `closeout/<组>/`：
  - `processes.<组>.yaml`：本组流程的 ⑥b 骨架片段（格式见第 3 节）；
  - `data-model.<组>.yaml`：本组聚合的表级片段（格式见第 4 节）；
  - `NOTES.md`：说明、待定问题（交 one-architecture 合成确认页）、新用语（交 one-glossary）、接缝问题。
- 不改 `processes.v1.yaml`、聚合稿与任何 SSOT；合并成 `processes.v2.yaml`、`data-model.v1.yaml` 由 one-architecture 做。
- 分支：每组在自己的 worktree 分支上开 PR，标题前缀 `docs(closeout-<组>)`，报 one-architecture 审后入 one-coordinator 队列。

## 3–4. 字段约定

统一以 `CONVENTIONS.md` 为准（流程骨架片段、表级片段、标识、NOTES 小节）。

## 5. 接缝规则

1. 跨组事件或调用：发布方组写在自己的流程里，订阅方组在 `NOTES.md` 的「接缝」表登记对方编号与事件名；两边编号对不上由 one-architecture 裁。
2. 一个表只属一个聚合、一个 BC；跨 BC 只按 ID 引用（METHOD 接缝 4）。
3. 不改 BC 边界与聚合归属；认为要改的写进 `NOTES.md` 的「结构问题」，one-architecture 判后交用户。
4. 用户待定事项不自行出页，交 one-architecture 合成。
5. 用语先查 `glossary/terms.yaml`；查不到写进 `NOTES.md` 的「新用语」，按 CLAUDE.md 第 2 条交 one-glossary。

## 6. 完成标准

- 本组每个流程：角色、每步 actor、判断点（如有）、审批节点（人工编排流程必有）、跨 BC 写全；`processes_check.py` 仍通过（one-architecture 合并后跑）。
- 本组每个聚合：至少一张根表，版本化、历史迁入、敏感初判三项都有值或 `tbd` 加理由。
- opus 首轮评审一次（只用于首轮），改后 sonnet 复核；评审记录放 `closeout/<组>/REVIEW.md`。
