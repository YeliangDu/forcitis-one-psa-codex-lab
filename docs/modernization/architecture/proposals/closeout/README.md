# 架构收尾：分组工作说明（开工必读）

计划：`../ARCH_CLOSEOUT_PLAN.md`（用户 2026-10-16「确认」）。画像草稿：计划 §4.1。把关：one-architecture。

## 1. 分组与实数

- 业务流程 **82 个**（`../processes.v1.yaml` 的 `processes`；另有事件策略 20、单聚合维护 19、读模型 4、合并 10，不计流程数）；人工编排 30 个。调度口径「85」与此不符，以 82 为准（`processes_check.py` 结果：通过）。
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

## 3. 流程骨架片段格式（在 v1 字段上增补，不删 v1 字段）

```yaml
- id: PRC-03                 # 与 v1 同号；不新编号，新增流程先交 one-architecture 定号
  roles:                     # 参与角色，取 value-streams.v2.yaml 的干系人 SH-
    - {id: SH-C1, as: 发起}
  steps:                     # 沿用 v1 steps，每步补：
    - command: 提交报价版本
      aggregate: AGG-12
      actor: SH-E2           # 人工步骤填干系人；系统策略填 system
      decision:              # 判断点（可空）：条件与各分支去向
        - {when: 金额超授权, goto: 审批}
      approval:              # 人工审批节点（可空）：走 platform.approval，写单据类型（暂名）与审批人角色
        doc_type: 报价审批
        approver: SH-M1
      cross_bc:              # 跨 BC（可空）：方式 event / api，对方 BC，事件或接口名
        - {mode: event, to: B-05, name: 报价已通过}
```

规则：判断点只写业务条件，不写字段；审批单据类型名先用中文暂名，汇总交 one-glossary；`cross_bc` 的方向必须与 `ENTERPRISE_AGGREGATES_DRAFT.md` §4.1 依赖边一致，不一致写进 `NOTES.md` 的接缝问题，不自行改边。

## 4. 表级片段格式（全貌级，不到字段）

```yaml
- aggregate: AGG-39
  bc: B-06
  tables:
    - name: timesheets              # 表名按 NAMING_STANDARD（snake_case 复数），schema 前缀由 BC 定
      role: root                    # root / child / version / link / staging
      key: uuid_v7                  # 自有表 UUIDv7（§14.1）
      references:                   # 同聚合内外键；跨聚合只记 ID 引用
        - {to: AGG-19, by: project_id, cross_bc: true}
      versioned: false              # D6 / D17：主数据或配置要版本化写 true 并注明区间口径
      history_migration: full       # full / open_items / none（U-3、U-4：默认 full；例外写理由）
      sensitivity: internal         # 初判，取 data-classification.txt 级别名；不确定写 tbd
      column_families: [条目, 状态, 费率版本引用, 审计列]   # 列族，不列字段
```

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
