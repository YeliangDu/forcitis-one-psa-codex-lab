# 架构收尾统一字段约定（三组共用，one-architecture 定，2026-10-16；修订：approval.engine（收入线样板）；segregation、pi、added_in（财务线样板）；第 5 节补充（铺开 VS-03、VS-04 与用户新原则））

本文件是唯一字段约定；`closeout/README.md` 第 3、4 节以本文件为准，各组 `closeout/<组>/` 下自写的约定一律以本文件替换。要加字段或取值，写进本组 `NOTES.md`「格式问题」，one-architecture 裁定后改本文件并通知三组。

## 1. 标识

| 对象 | 写法 | 来源 |
|---|---|---|
| 流程 | `PRC-NN` | `architecture/ssot/09-processes/catalog.yaml`；新增流程先向 one-architecture 要号 |
| 价值流阶段 | `VS-NN.N` | `value-streams.v2.yaml` |
| 角色 | `SH-XN`（如 `SH-C1`、`SH-E2`、`SH-M1`） | `value-streams.v2.yaml` 顶部 `stakeholders:`；系统自动步骤写 `system`；平台人工任务写 `platform` |
| BC | `B-NN` | `ENTERPRISE_AGGREGATES_DRAFT.md` §3.2；不写模块名（模块名由 B-NN 唯一映射）。平台模块编排的流程（如账号开通）`owner_bc` 写平台模块名 `platform.xxx` |
| 聚合 | `AGG-NN` | 聚合稿 §2.2 |
| 命令、事件 | 聚合稿 §2.2 命令列原文（中文） | 不自造；缺的写 NOTES「新用语」 |
| 不变量 | `INV-NN` | 聚合稿 §1.2 |

## 2. 流程骨架（`processes.<组>.yaml`）

每个流程一条，保留 v1 全部字段（`id`、`name`、`trigger`、`stages`、`owner_bc`、`human_orchestration`、`steps` 等），**只增补**下列字段：

```yaml
- id: PRC-03
  migration: "在途实例按当前状态迁入；已结实例只迁结局与证据"   # 必填（提议中）：投产时在途与历史实例的迁入口径
  scope: "按项目承担部门的数据范围"   # 可选（提议中）：谁能看到、经办
  segregation:                        # 可选；不相容职务约束：同一人不得同时担任所列角色（流程级）
    - {roles: [SH-E5, SH-M2], rule: "制单与审批不得为同一人"}
  triggers:                           # 必填（提议中）：触发，可多个
    - {type: human, name: "人工发起"}            # type：human / event（写 signal 事件名与发布 BC）/ time / call
    - {type: human, name: "从既有状态继续办理", entry: 3}   # 可选 entry：直接进入的步骤序号，从 1 起；省略为第 1 步
  outcomes: [已认可, 已退回]            # 必填（提议中）：本流程的结局词（结束事件名），取自阶段出口用语
  scenarios: [PRC-03-S01]             # 铺开时填：验收场景编号 PRC-NN-Sxx
  roles:                              # 必填；本流程全部参与角色
    - {id: SH-E2, as: initiator}      # as 取值：initiator 发起 / performer 执行 / approver 审批 / reviewer 复核 / informed 知会 / external 外部方
  steps:
    - command: 提交报价版本            # v1 原字段不动；收尾新增的步骤加 added_in: closeout
      aggregate: AGG-12
      actor: SH-E2                    # 必填；SH- 编号 / system / platform
      where: in_system                # 必填（提议中，随样板页请用户确认）：in_system 用户在本系统操作 / automatic 本系统自动 / external:<系统名> 经集成由外部系统完成（如 external:企业微信、external:U8C、external:银行、external:税务）/ offline 线下完成、本系统只登记结果
      evidence: []                    # where 为 offline 或 external 时必填：本系统登记的结果与证据（如「客户签认单扫描件＋签认日」「银行回单」）
      decision:                       # 可选；本步之后的判断点，按顺序求值
        - {when: "报价金额超过授权额度", goto: 3, rule: RC-NN}   # goto：步骤序号（从 1 起）、end、或另一流程 PRC-NN；rule：引用的规则卡编号（提议中）
        - {when: otherwise, goto: 4}
      approval:                       # 可选；人工编排流程中审批节点必填。口径（用户 intent 163556）：正常一级＝归属对象责任人；按 APPROVAL_RULES.yaml 条件加审，最多两级；不相容职务另计
        engine: platform.approval     # platform.approval 审批平台 / aggregate 聚合内命令（如交付物内部校审） / external 系统外（客户书面认可等，只登记证据）
        doc_type: 报价审批             # 单据类型中文暂名，汇总交 one-glossary
        approver: cost_object_owner   # 基础审批人：一律「费用或业务归属对象的责任人」（项目经理、部门负责人或成本对象责任人），不写固定角色
        escalation_rules: [AR-01, AR-04]   # 可能命中的加审规则编号（APPROVAL_RULES.yaml）；图上画「一级审批 → 条件加审（≤2）」
        on_reject: 2                  # 驳回后去向：步骤序号 / end
      permission: 报价.提交            # 用户任务必填（提议中）：权限码暂名
      page: 报价编辑页                 # 用户任务必填（提议中）：页面或页面操作暂名
      notify: [{to: SH-M1, how: 企业微信待办}]   # 可选：谁在何时收到通知
      period_gate: false              # 触账步骤写 true，并在 decision 写期间已关账的拒绝分支
      legal_entity: 承担法人           # 可选：本步按哪个法人口径执行
      audit: true                     # 可选：必须留操作审计
      metric_event: false             # 可选：产生分析事实（总线矩阵的行）
      timeout: {after: "P5D", escalate_to: SH-M1, interrupting: false}   # 可选（提议中）：期限（ISO 8601 时长）、升级给谁、是否中断
      outcome: 已认可                  # 本步结束流程时填结局词（须在 outcomes 中）
      cross_bc:                       # 可选；本步跨 BC 的交互，可多条
        - {mode: event, direction: out, bc: B-05, name: 报价已通过}   # mode：event / api；direction：out 本步发出（事件）或调用（api），in 本步由对方触发或读取
```

规则：
1. `decision.when` 只写业务条件，不写字段名；最后一条用 `otherwise`。
2. `cross_bc` 的方向必须与聚合稿 §4.1 依赖边一致（`mode: api` 只能由下游调上游；`mode: event` 由发布方 → 订阅方）；不一致写 NOTES「接缝问题」，不改边。
3. 同 BC 内的交互不写 `cross_bc`。
4. `triggers[].entry` 适用于全部触发类型，指向本流程 `steps` 的步骤序号（正整数，也接受数字字符串），不写状态名、`end` 或其他流程编号；越界、空值、布尔值等非法值须报错，不回退到第 1 步。开始事件放在目标步骤的泳道，连该步骤的首个节点（含定时等待、线下办理等步骤内部前置节点），不执行前面的步骤；多个触发可指向同一步。状态前置条件仍由目标命令的守卫校验，`entry` 只表达描述图入口，不证明运行时状态守卫已实现。例如 PRC-144 的取消触发写 `entry: 3`，从已批准或已完成直达取消登记步骤。

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
      pi: none                        # 个人信息（ADR-0043 第二维）：none 无 / pi 个人信息 / spi 敏感个人信息 / tbd
      column_families: [条目, 状态, 费率版本引用, 审计列]    # 列族，不列字段
      reason: ""                      # history_migration、sensitivity 非默认时必填
```

规则：
1. 一张表只属一个聚合、一个 BC。
2. 每个聚合至少一张 `root`。
3. 不写字段、类型、索引、约束（全貌级）。

## 4. NOTES.md 固定小节

`接缝问题`、`结构问题`、`新用语`、`格式问题`、`待用户定`（交 one-architecture 合成确认页，不自行出页）。

## 5. 补充（2026-10-16 铺开中裁定）

- **审批回调**：走审批平台的审批，审批结果统一写成下一步的 `cross_bc: {mode: event, direction: in, bc: platform.approval, name: 审批已通过 / 审批已驳回}`（同 cost.timesheet 接审批的做法）。
- **人工审批须说明理由**：`approval.why_human` 必填（为何不能由规则或不变量守卫，用户原则）；`approval.approver` 一律 `cost_object_owner`（谁负责谁批），加审只引用 `APPROVAL_RULES.yaml` 中 `guard: approval` 的规则。
- **通知不是步骤**：通知写在步骤的 `notify` 属性；确需单列的平台通知步骤写 `actor: platform`、`where: automatic`、`platform_task: true`，不计命令缺口。
- **期间闸门**：系统自动触账的步骤遇期间已关账，按 ADR-0045 与 INV-92 路由到开放期间并标原期间，不拒绝；人工发起的触账单据按规则卡提示（不设审批）。
- **可撤回**：步骤 `withdraw: {to: 步骤序号 | end, outcome: 已撤回}`。
- **已采纳的扩展字段**：`decision[].outcome`（出口直接结束时的结局词）；事件策略可写多步 `steps`；单聚合维护写 `states`、`transitions`；读模型写 `query`、`dimensions`、`permission`、`page`；`pending: <题号>` 标注待用户定事项影响的步骤。
- **负担与时间分析参数**（`BURDEN_ANALYSIS_PLAN.md`）：步骤 `effort: {typical_min, p90_min}`、`wait: {typical_h, p90_h}`（可空），判断出口 `p`（出口概率估计）；流程级 `frequency_ref`（取证编号）。铺开时按经验先估，取证后校准。
- **组织信息收集**（用户「我们现在就来确定流程需要的信息，最终统一修改一次」）：泳道先按角色；每个角色在流程级 `roles` 里补 `org_needs: {responsibility, approval_right, department_hint, position_hint}`，汇总作岗位体系重梳的输入，岗位体系定后一次性改泳道与映射。
- **审批人口径按 engine 区分**：`platform.approval`（费用与业务审批）→ `approver: cost_object_owner`（实例级 A）；`aggregate`（专业复核：交付物校审、检查记录审核、设计变更内审、不符合项关闭、复盘评审等）→ `approver` 写类型级专业角色（SH- 编号），权责对照表登 `resolve: type`；`external`（客户认可、发包人批准、延期答复等系统外意见）→ 不登 A，脚本跳过，但步骤须写 `evidence`。
- **类型级例外**：engine 为 platform.approval 的审批，若已采纳 ADR 或用户决定指定由某职能复核（如收入确认由财务复核，ADR-0047 U-2 (a)），基础审批人写该类型级角色，权责表登 `resolve: type`，并在 `why_human` 写出处；其余一律 cost_object_owner。
- **单聚合维护的审批**：`commands[]`（命令清单，可逐条写 `aggregate` 覆盖单元级）或 `transitions[]`（状态迁移）上需要人工审批的条目（没收、扣减、终止、开户确认等）写 `approval:`，字段与步骤审批相同（`engine`、`approver`、`doc_type`、`why_human`、`escalation_rules`、`on_reject` 写驳回后状态不变的说明）；审批对象×命令 = 条目的 `aggregate`（没写取单元级）× 条目的 `command`；两处写了同一命令只算一次。`closeout_check.py --qc` 与 `raci_build.py` 一并扫描（one-architecture 2026-10-16 裁定，应 one-arch-revenue PRC-10、52、68）。
- **类型级 A 手登**：platform.approval 的类型级例外在 `RESPONSIBILITY_MATRIX.yaml` 手登 `A: {resolve: type, role: SH-…, basis: <ADR 或 intent>}`，`raci_build.py --write` 重新生成时保留；engine 为 aggregate 的类型级 A 由脚本自动生成，不必手登。
- **定时触发**（2026-10-20 统一写法，one-architecture 按 one-coordinator 派工；依据 ADR-0002 定时任务行、ADR-0044 决定 3、`platform/scheduling/package-info.java` 与守护 G46）：time 触发由所属 BC 在本模块内声明 `@Scheduled` + `@SchedulerLock(name = "{子域}.{bc}.{名}")` 的作业，到点调本 BC 聚合命令；不向 `platform.scheduling` 登记，也不依赖它（它只提供锁表与总开关）。按记录到期的（提醒、超时、周年）由本 BC 的周期作业扫描聚合上的到期时点列，幂等键 = 聚合 ID + 到期时点，到期判定用应用注入的时钟（ADR-0034 第 1 项第 6 条），执行时复核聚合当前状态。原写法「向 platform.scheduling 登记、边 BC → platform.scheduling」作废（S-31 SQ-18 的该句不再适用）；聚合稿与 ARCH_VS14 中现存的此类边待 SSOT 重建导入时去掉。
- **超时的系统代办（ARCH-VS14 A8）**：骨架保持只读时，在 `architecture/ssot/09-processes/catalog.yaml` 对应流程登记 `timeout_actions`，以 `step_command` + `after` 精确匹配原步骤；`command`、`aggregate`、`outcome` 指明代办命令、同一聚合和已登记结局，`guard: {condition: <业务守卫>, goto: <失败回到的步骤序号>}` 明确失败路径。仅适用于 `interrupting: true`、`escalate_to: system`。图画成中断边界 → 守卫网关 → 系统服务任务 → 指定结局，守卫失败转纠正步骤，不再经过人工确认网关。重复、未匹配或无效配置须失败；未登记配置的超时保留原升级语义，非中断提醒仍只结束提醒分支。本配置只修正描述图，不代表定时或领域命令已实现；PRC-142 的期限仍待 B31-2。
- **B-31 超时登记（ARCH-VS14 A7）**：受理提醒、解决后代关登记在 AGG-114，离职后归还提醒登记在 AGG-115，作业按上条在 B-31 内自声明，不依赖 `platform.scheduling`。期限、业务日历、起算点及混合入口范围分别待 B31-2、B31-7，未确认前不把 P2D 当两个工作日、也不把人工归还统一套离职 P7D。执行时复核聚合当前状态，重开、办结时取消旧期限，重试使用上述幂等键；已批准的数值回写原骨架须另卡。
- **支撑流程**：不在客户价值流上的业务流程（如 B-31 行政事务）写 `stages: []` 与 `support: <理由>`（S-32）。
- **跨段说明**：流程在同一价值流内所标阶段不连续时，须写 `stages_note: <理由>`（阶段对齐，2026-10-16）；没有理由的跨段按跳段报。
- **状态机豁免**：运行单据写 `stateless: true`，长期配置写 `perpetual: true`，两者都须写 `reason`（S-30、S-32）。
- **平台依赖**：业务模块依赖平台模块（platform.approval、platform.integration 等）为标准方向；用到审批或 `notify` 的 BC 在依赖边回补中登记。
