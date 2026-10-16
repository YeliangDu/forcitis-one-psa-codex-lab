# 架构收尾统一字段约定（三组共用，one-architecture 定，2026-10-16；修订：approval.engine，收入线样板提议）

本文件是唯一字段约定；`closeout/README.md` 第 3、4 节以本文件为准，各组 `closeout/<组>/` 下自写的约定一律以本文件替换。要加字段或取值，写进本组 `NOTES.md`「格式问题」，one-architecture 裁定后改本文件并通知三组。

## 1. 标识

| 对象 | 写法 | 来源 |
|---|---|---|
| 流程 | `PRC-NN` | `processes.v1.yaml`；新增流程先向 one-architecture 要号 |
| 价值流阶段 | `VS-NN.N` | `value-streams.v2.yaml` |
| 角色 | `SH-XN`（如 `SH-C1`、`SH-E2`、`SH-M1`） | `value-streams.v2.yaml` 顶部 `stakeholders:`；系统自动步骤写 `system`；平台人工任务写 `platform` |
| BC | `B-NN` | `ENTERPRISE_AGGREGATES_DRAFT.md` §3.2；不写模块名（模块名由 B-NN 唯一映射） |
| 聚合 | `AGG-NN` | 聚合稿 §2.2 |
| 命令、事件 | 聚合稿 §2.2 命令列原文（中文） | 不自造；缺的写 NOTES「新用语」 |
| 不变量 | `INV-NN` | 聚合稿 §1.2 |

## 2. 流程骨架（`processes.<组>.yaml`）

每个流程一条，保留 v1 全部字段（`id`、`name`、`trigger`、`stages`、`owner_bc`、`human_orchestration`、`steps` 等），**只增补**下列字段：

```yaml
- id: PRC-03
  roles:                              # 必填；本流程全部参与角色
    - {id: SH-E2, as: initiator}      # as 取值：initiator 发起 / performer 执行 / approver 审批 / reviewer 复核 / informed 知会 / external 外部方
  steps:
    - command: 提交报价版本            # v1 原字段不动
      aggregate: AGG-12
      actor: SH-E2                    # 必填；SH- 编号 / system / platform
      decision:                       # 可选；本步之后的判断点，按顺序求值
        - {when: "报价金额超过授权额度", goto: 3}       # goto：步骤序号（从 1 起）、end、或另一流程 PRC-NN
        - {when: otherwise, goto: 4}
      approval:                       # 可选；人工编排流程中审批节点必填
        engine: platform.approval     # platform.approval 审批平台 / aggregate 聚合内命令（如交付物内部校审） / external 系统外（客户书面认可等，只登记证据）
        doc_type: 报价审批             # 单据类型中文暂名，汇总交 one-glossary
        approver: SH-M1               # 审批人角色
        multi_level: false            # 是否多级
        on_reject: 2                  # 驳回后去向：步骤序号 / end
      cross_bc:                       # 可选；本步跨 BC 的交互，可多条
        - {mode: event, direction: out, bc: B-05, name: 报价已通过}   # mode：event / api；direction：out 本步发出（事件）或调用（api），in 本步由对方触发或读取
```

规则：
1. `decision.when` 只写业务条件，不写字段名；最后一条用 `otherwise`。
2. `cross_bc` 的方向必须与聚合稿 §4.1 依赖边一致（`mode: api` 只能由下游调上游；`mode: event` 由发布方 → 订阅方）；不一致写 NOTES「接缝问题」，不改边。
3. 同 BC 内的交互不写 `cross_bc`。

## 3. 表级数据模型（`data-model.<组>.yaml`）

每个聚合一条：

```yaml
- aggregate: AGG-39
  bc: B-06
  tables:
    - name: timesheets                # snake_case 复数，按 NAMING_STANDARD；不写 schema（由 BC 定）
      role: root                      # root 根 / child 次级 / version 版本 / link 关联 / staging 迁入暂存
      key: uuid_v7                    # 自有表固定 uuid_v7（§14.1）；框架表写 framework 并注明
      references:
        - {to: AGG-19, by: project_id, cross_bc: true}     # 跨聚合只按 ID；同聚合内外键 cross_bc 省略
      versioned: none                 # none / effective_dated（生效区间，D6）/ snapshot（期间快照）/ append_only（只追加）
      history_migration: full         # full 全量历史 / open_items 只迁未结 / none 不迁；非 full 写 reason
      sensitivity: internal           # 取 backend/architecture/data-classification.txt 的级别名；不确定写 tbd 并写 reason
      column_families: [条目, 状态, 费率版本引用, 审计列]    # 列族，不列字段
      reason: ""                      # history_migration、sensitivity 非默认时必填
```

规则：
1. 一张表只属一个聚合、一个 BC。
2. 每个聚合至少一张 `root`。
3. 不写字段、类型、索引、约束（全貌级）。

## 4. NOTES.md 固定小节

`接缝问题`、`结构问题`、`新用语`、`格式问题`、`待用户定`（交 one-architecture 合成确认页，不自行出页）。
