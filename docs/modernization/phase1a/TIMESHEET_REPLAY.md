# TIMESHEET 审批人序列回放：导出文件格式与命令行

日期：2026-10-14 · 模块：platform.approval · 依据：REIMAGINED_ARCHITECTURE.md §3.5（两步法）、检查点 2 Q-4

## 做什么

回放工具读入「当时」老系统的组织快照、每个节点的指派规则、流程变量和**实际办理的审批人**，用新系统的 `AssigneeResolver`
（与线上待办分配同一份代码）逐节点算出候选集，按「**老审批人 ∈ 新候选集**」比对（RULE-249 把随机选人改成了候选组，所以不再比较「是同一个人」）。

比对口径有两层：

1. **节点集合**：老工时行类型 0 / 1 / 2 按转换表映射为路由标记 `projectHours` / `preSalesHours` / `overheadHours`，每个标记对应新流程的一个分支节点；
   新流程应当启动的节点集合必须等于老系统实际出现任务的节点集合。老系统因「取不到人时跳过」而没有任务的节点，新规则也必须取不到人（`SKIPPED`）。
2. **每个节点的候选集**：老审批人 ∈ 新候选集。三路分支是并行的，**不比较先后顺序**。

节点集合不一致、老规则与新节点规则不一致，都记为 `DEFECT`（报告里的 `note` 列说明原因）。

这是**步骤 (a) 引擎等价**：输入全部来自老系统同一时刻的快照，所以差异只可能来自新旧规则的差别，不含数据源漂移。
**步骤 (b) 数据源差异**（A8 与老表之间的漂移，RULE-365）单独出报告，不进本工具。

## 差异分类（输出里的 verdict）

| verdict | 含义 | 处理 |
|---|---|---|
| `MATCH` | 节点集合一致，且实际办理的每个人都在新候选集内（老系统跳过的节点，新规则也跳过） | 通过 |
| `REVIEWED_DISABLED_APPROVER` | 老审批人当时已停用 / 离职；新候选集排除这类人（RULE-253） | 按类别由用户接受 |
| `REVIEWED_HARDCODED_FALLBACK` | 老审批人是写死的兜底个人（`legacyFallbackUserIds`）；新规则转流程管理员（RULE-254） | 按类别由用户接受 |
| `REVIEWED_REPORTING_LINE` | 老汇报线不完整；新规则上推到部门负责人（RULE-250） | 按类别由用户接受 |
| `REVIEWED_SELF_APPROVAL` | 发起人自己审批自己；新规则防自审（RULE-307） | 按类别由用户接受 |
| `REVIEWED_ADMINISTRATOR_FALLBACK` | 新规则取不到人，转流程管理员并告警（RULE-250/254） | 按类别由用户接受 |
| `DEFECT` | 以上都不是：真缺陷 | **逐条修复**；出现任何 `DEFECT`，命令行以状态码 1 退出 |

## 命令行入口

不需要 Spring，不需要数据库，不联网。回放命令保留独立的 `main`，与应用分开启动；发布包（Spring Boot 可执行 jar，`spring-boot-maven-plugin` 显式指定 `mainClass` 为 `OneApplication`，layout 为 `ZIP` 以启用 `PropertiesLauncher`）里同时带着它，在 ECS 上离线运行：

```bash
D=/data/forcitis-one/import/c6/<日期>
java -Dloader.main=com.forcitis.one.platform.approval.infrastructure.replay.TimesheetApproverReplayCommand \
  -cp /data/forcitis-one/app/<版本>/forcitis-one.jar org.springframework.boot.loader.launch.PropertiesLauncher \
  "$D/timesheet-export.json" "$D/replay-out"
```

运行时需要 ECS 上的 Java 25。开发机上也可以直接用编译结果运行：

```bash
cd backend
./mvnw -q compile
./mvnw -q dependency:build-classpath -Dmdep.outputFile=target/replay-cp.txt
java -cp "target/classes:$(cat target/replay-cp.txt)" \
  com.forcitis.one.platform.approval.infrastructure.replay.TimesheetApproverReplayCommand <导出文件> <输出目录>
```

输出目录里生成 `replay-report.json`（机器可读）和 `replay-report.md`（汇总表加差异清单）。报告只含人员 ID，不含姓名。
有 `DEFECT` 时命令以退出状态 1 结束（异常信息指向报告）。

验收测试：`TimesheetApproverReplayIT`（合成数据 `src/test/resources/replay/synthetic-timesheet-export.json`，其中不含任何真实人员或组织，覆盖「只有项目工时」「项目加售前」「三类都有」三种情形；
测试里也运行了上面这条命令行）。

## 导出文件（JSON，`formatVersion` = 1）

**导出文件含真实人员，只存新 ECS 的 `/data/forcitis-one/import/c6/<日期>/`（目录 root 0700、文件 root 0600，附 manifest），回放在 ECS 上运行，不进仓库，不下载到个人电脑；保留到 1a 生产切换后 30 天删除（C-6 执行方案，one-ops PR #65）。** 生产只读导出需用户授权（Q-4）。

导出文件说**新词汇**（词表术语）；老系统的名字只出现在下面的「老名 → 新名」转换表和回放代码里的同一张表
（`platform/approval/infrastructure/replay/LegacyVocabulary`，LegacyShapeTest 的例外清单只登记这一个文件）。
回放读入时按转换表翻译：表里有的老名被换成新名，不在表里的原样通过，所以直接按新名导出也能回放。

```json
{
  "formatVersion": 1,
  "processKey": "TIMESHEET",
  "processAdministratorUserId": 900,
  "legacyFallbackUserIds": [500],
  "snapshot": {
    "employees": [{"userId": 111, "orgUnitId": 10, "supervisorUserId": 110, "employed": true}],
    "orgUnits":  [{"orgUnitId": 10, "parentOrgUnitId": 1, "headUserId": 110,
                   "regionalReceivablesManagerId": null, "active": true}],
    "roleHolders": {"reviewer": [110, 112]}
  },
  "instances": [{
    "instanceId": "…",
    "initiatorId": 201,
    "legacyHourTypes": [0, 1],
    "formFields": {"projectManagerId": ["111"]},
    "variables":  {},
    "nodes": [{
      "nodeKey": "projectManagerReview",
      "rule": {"type": "FORM_FIELD_USERS", "field": "projectManagerId"},
      "selfApprovalAllowed": false,
      "actualApprovers": [111]
    }]
  }]
}
```

| 字段 | 含义 |
|---|---|
| `processAdministratorUserId` | 当时（或将来）的流程管理员；不给则取不到人时结果为 `UNRESOLVED`，该节点落入 `DEFECT` |
| `legacyFallbackUserIds` | 老代码里写死的兜底个人（RULE-254），用于识别 `REVIEWED_HARDCODED_FALLBACK` |
| `snapshot.employees` | **老用户表当时的快照**：`employed=false` 表示停用 / 已删 / 离职；`supervisorUserId` 是人员级直属上级（RULE-372） |
| `snapshot.orgUnits` | **老部门表当时的快照**：负责人、区域应收负责人、上级、是否有效 |
| `snapshot.roleHolders` | 角色编码到持有人（导出时把老角色 id 换成新系统的角色编码；回放不写死任何老角色码） |
| `instances[].legacyHourTypes` | 该单据工时行的老类型集合（0 项目、1 售前、2 OH）；按转换表映射为路由标记，决定新流程应启动哪些分支 |
| `instances[].formFields` | 业务表单字段（指派规则的 `field` 从这里取，如 `projectManagerId`） |
| `instances[].variables` | 其余流程变量（指派规则的 `department` 可选地从这里取） |
| `nodes[].rule` | 该节点的**唯一**指派规则（RULE-262）：`type` 加各类型的属性，词汇见下表 |
| `nodes[].selfApprovalAllowed` | 老节点标了「员工活动」的写 `true`（RULE-307 豁免） |
| `nodes[].actualApprovers` | 实际办理该节点的人（会签多人则都列出）；取自老系统任务扩展表的办理人 |

缺省的布尔值按 `false` 处理，缺省的列表按空处理。

### 规则类型词汇（与 BPMN `forcitis:assignmentRule` 完全相同）

1a 的目录只有四种，用户 2026-10-14 决定「保留 5 种、推迟 3 种、不建 2 种」（保留的第 5 种发起人节点随退回机制取消，见下）：

| `type` | 属性 | 老系统对应 | 状态 |
|---|---|---|---|
| `ROLE` | `role`（角色编码） | 规则类型 10 | 保留 |
| `FORM_FIELD_USERS` | `field`（词表字段名） | 类型 60（`options` 的第一个字段）；脚本 9 | 保留 |
| `INITIATOR_SUPERVISOR` | `level`（1 或 2） | 脚本 20 / 21 | 保留 |
| `DEPARTMENT_HEAD` | `department`（变量名，**可选**；不写 = 发起人所在组织单元） | 脚本 31 | 保留 |
| `INITIATOR` | — | 脚本 10（发起人重做节点） | **已删**：「退回即结束本轮，重提开新一轮」，新系统没有发起人节点（M3） |

**推迟**（实现与解析已删，规则卡保留；码表拒绝并报「deferred」，启动或部署失败）。时点依据 `runtime/prod_bpm.tsv` ##13 中生产当前版本的使用情况：

| `type` | 规则卡 | 为什么推迟 / 何时建 |
|---|---|---|
| `REGIONAL_RECEIVABLES_MANAGER` | RULE-255 | 1a 不建。1b 报价（OPP_QUOTATION）与 Phase 2（PM_BUDGET）按规格建 |
| `FORM_FIELD_USER_SUPERVISORS` | RULE-259/260 | 1a 不建。随 PM_ETC 所在的 Phase 2、PM_REVENUE 所在的 Phase 2/4（收入确认部分在 Phase 4）建 |
| `DEPARTMENT_DIRECTOR` | RULE-256 | 生产中没有流程使用。需要时按新角色体系重新定义，不写死任何老角色码 |

**不建**（码表拒绝并报「not offered」）：

| `type` | 为什么不建 |
|---|---|
| `NAMED_USERS` | 生产当前版本无使用；用角色代替，符合 RULE-249「候选组」结论 |
| `PROCESS_VARIABLE_USERS` | 老流程变量不得成为模块契约（H2）；生产中类型 60 的取人统一由 `FORM_FIELD_USERS` 加词表字段名承担 |

决策表与回放分支表的一致性：`TimesheetApproverReplayIT.branchTableEqualsTheProcess`（路由标记 ↔ 节点 ↔ 指派规则）。

BPMN 守护：`BpmnRuleConventionsTest` 检查每个流程文件（含测试变体）不出现这五种类型，也不出现发起人节点（`initiatorNode`、`skipExpression`）。

老类型 20（部门成员）、22（岗位）、40（用户组）在生产 TIMESHEET 里没有使用（`runtime/prod_bpm.tsv` ##13），回放工具不支持；
类型 21 按固定部门指派在 TIMESHEET 里也没有使用，出现时需要先扩充规则词汇。

### 老名 → 新名转换表（回放读入时套用）

| 老名 | 新名 | 位置 |
|---|---|---|
| `manager_id` | `projectManagerId` | 表单字段 / 规则的 `field`（项目经理；常量在 `cost.timesheet` 的 api：`TimesheetApprovalField.PROJECT_MANAGER`） |
| `area_leader` | `regionalHeadId` | 同上（区域负责人；`TimesheetApprovalField.REGIONAL_HEAD`） |
| `deliveryDeptId` | `deliveryUnitId` | 流程变量 / 规则的 `department` |
| `INITIATOR_LEADER` | `INITIATOR_SUPERVISOR` | 规则类型 |
| `leaderUserId` | `supervisorUserId` | 快照里员工的人员级直属上级 |
| 工时行类型 `0` / `1` / `2` | `projectHours` / `preSalesHours` / `overheadHours` | 路由标记（`cost.timesheet` 的 api：`TimesheetRoutingFlag`） |
| 任务名 `项目经理审批` / `区域负责人审批` / `一级汇报人审批` | `projectManagerReview` / `regionalHeadReview` / `supervisorReview` | 节点键 |
| 规则类型 `INITIATOR`（脚本 10，发起人重做节点） | （不回放） | 该节点整个跳过，不计入节点数 |

## 生产核实结果（2026-10-14，v15）

生产 TIMESHEET 当前版本是 v15。2026-10-14 经用户授权只读核实流程定义，结构如下，以此为准（取代此前依据 `runtime/prod_bpm.tsv` ##13 的推定）。

**核实方式**：经云助手只读通道，只读流程定义（流程定义表、定义文件字节、指派规则表），不读业务数据和个人信息；凭据不出服务器进程；临时目录已删除。

### 新旧对照

| | 生产 v15 | 新流程 `cost_timesheet_approval` |
|---|---|---|
| 顶层 | 开始 → 发起人任务 → 子流程 → 结束 | 开始 → 子流程 → 结束；无发起人节点（M3：退回即结束本轮，重提开新一轮） |
| 子流程 | 开始 → 包容网关分三路 → 三路汇合到第二个包容网关 | 包容网关分三路，汇合到第二个包容网关，然后结束 |
| 分支 1 | 工时行有类型 0（项目工时）→「项目经理审批」，规则类型 60，取变量 `manager_id` | 路由标记 `projectHours` → `projectManagerReview`，`FORM_FIELD_USERS` 字段 `projectManagerId` |
| 分支 2 | 有类型 1（售前工时）→「区域负责人审批」，规则类型 60，取变量 `area_leader` | 路由标记 `preSalesHours` → `regionalHeadReview`，`FORM_FIELD_USERS` 字段 `regionalHeadId` |
| 分支 3 | 有类型 2（OH 工时）→「一级汇报人审批」，脚本 20（发起人的一级上级） | 路由标记 `overheadHours` → `supervisorReview`，`INITIATOR_SUPERVISOR` `level=1` |
| 分支条件 | 老表达式按工时行类型判断（只作证据，不照搬） | 业务模块预先算好的路由标记交给决策表，决策表答出每个节点「走不走」，BPMN 的网关只读答案（`routed_<节点键>`）（RULE-262） |
| 任务形态 | 并行多实例会签：按审批人列表展开，每个不同的审批人一个任务；完成条件是全部完成或有人驳回 | 同为并行多实例会签（集合表达式 `approverResolution`，每个不同的审批人一个任务） |
| 驳回 | 汇合处只要有驳回，错误结束事件 `Error_REJECT` 被子流程边界事件捕获，回到发起人 | 子流程内驳回即退回：任务全部撤销，以 `RETURNED` 结束本轮，重新提交时开新一轮 |
| 取不到人 | 三个节点 `skip_when_empty=1`、`row_approval=0` | 三个节点显式配置 `skipWhenEmpty="true"`；只有显式允许、字段齐全且值有效、确实没有人时才跳过；三路全部跳过也报错（RULE-300 评审修正） |

### 与原推定不一致之处（已改正）

| 原推定 | 实际（v15） | 改正 |
|---|---|---|
| 三个审批节点串行：项目经理 → 售前分支的区域负责人 → 一级上级 | 子流程内的**包容网关**并行三路，不比较先后 | BPMN 改为包容网关分叉 / 汇合；回放不比较顺序 |
| 一级上级在顶层 | 一级汇报人审批在子流程内，驳回即退回 | 三个节点都在子流程内 |
| 缺少 OH 工时分支 | 有类型 2（OH 工时）分支 | 新增路由标记 `overheadHours` 与对应分支 |
| 售前分支是项目经理之后的可选一步 | 三路各由工时行类型独立触发 | 三路条件互相独立，由三个路由标记各自控制 |

### 核实所用 SQL（只读）

```
SELECT TOP 3 p.KEY_, p.VERSION_, p.ID_, p.DEPLOYMENT_ID_, p.RESOURCE_NAME_, p.SUSPENSION_STATE_ FROM ACT_RE_PROCDEF p WHERE p.KEY_ = 'TIMESHEET' ORDER BY p.VERSION_ DESC
SELECT b.NAME_, DATALENGTH(b.BYTES_) AS len, b.BYTES_ FROM ACT_GE_BYTEARRAY b JOIN ACT_RE_PROCDEF p ON b.DEPLOYMENT_ID_ = p.DEPLOYMENT_ID_ AND b.NAME_ = p.RESOURCE_NAME_ WHERE p.KEY_ = 'TIMESHEET' AND p.VERSION_ = (SELECT MAX(VERSION_) FROM ACT_RE_PROCDEF WHERE KEY_ = 'TIMESHEET')
SELECT r.task_definition_key, r.type, r.options, r.row_approval, r.skip_when_empty, r.reapprove_on_return, r.script_field FROM bpm_task_assign_rule r WHERE r.process_definition_id COLLATE DATABASE_DEFAULT = (SELECT TOP 1 p.ID_ COLLATE DATABASE_DEFAULT FROM ACT_RE_PROCDEF p WHERE p.KEY_ = 'TIMESHEET' ORDER BY p.VERSION_ DESC) AND r.deleted = '0'
```

### 与生产仍存在的差异

| 差异 | 影响 | 处理 |
|---|---|---|
| **会签已实现**：生产是并行多实例，每个不同的审批人一个任务，全部通过才算通过 | 新流程的三路节点同为多实例并行会签（`approverResolution`），与生产一致；一路里任一人正式驳回即结束本轮（退回），加签人的驳回只作建议 | 无；回放只比对审批人候选集，不受影响 |
| 驳回的目的地：生产回到发起人任务，新系统结束本轮并以 `RETURNED` 通知业务模块 | 发起人重新提交时开新一轮（`start_rounds`） | 用户决定 M3 |
| 取不到人时：生产 `skip_when_empty=1` 直接跳过，包括配置错误造成的「空」 | 新系统只在字段齐全、值有效但确实没有人时跳过；字段缺失或值无效则失败并告警（G-1 已修复）。回放中老系统「因字段缺失而跳过」的节点会记为 `DEFECT`，需要先查数据 | 无 |
| 节点内 `row_approval=0`（不按行审批）与 `reapprove_on_return` | 1a 不做行级审批；退回后整轮重来，不存在「退回后是否重审」的开关 | Phase 2 工时审批再议 |

## 执行步骤（本会话，Q-4 授权后）

1. 经用户授权，在生产做只读导出：近期、**当前定义版本**的 TIMESHEET 实例样本；同时导出当时的 `system_users` / `system_dept` 快照、
   `bpm_task_assign_rule`、流程变量快照、各单据的工时行类型集合与实际审批人序列，按上面的格式写成一个 JSON，存 ECS 的 `/data/forcitis-one/import/c6/<日期>/`（权限与保留期见上）。
2. 在 ECS 上用发布包运行命令行（见「命令行入口」），读 `replay-report.md`；报告同样只留在该目录。
3. 各 `REVIEWED_*` 类别请用户逐类接受；每条 `DEFECT` 修复并重跑，直到没有 `DEFECT`。
4. 另出步骤 (b) 的数据源差异报告（A8 与老表）。

## 已知缺口

| # | 缺口 | 应有行为 | 期限 |
|---|---|---|---|
| G-1 | **已修复**（提交 3f3a12b）：只有字段齐全、值有效但确实没有人时，节点才可跳过；字段缺失、值无效（指向的人不存在、不是 ID）或解析异常，一律失败并告警（`platform.approval.assignee.invalid.input`、`platform.approval.assignee.resolution.failed`）。三路全部被跳过也失败并告警（`platform.approval.all.nodes.skipped`），三个路由标记全为 false 在发起时以业务错误 `PLATFORM_APPROVAL_NO_ROUTE` 拒绝 | 测试：`AssigneeResolverTest`、`TimesheetBranchesIT` | 已完成 |
| G-2 | **已实现**（RULE-248/273）：三路都是并行多实例会签，每位不同的审批人各生成一个任务，全部通过才算通过，任一人正式驳回即结束本轮，加签人的驳回只记意见 | 同上 | 测试：`CountersignIT`、`BpmnRuleConventionsTest` | 已完成 |

## 与无效交付工时（A8_ACCESS_REGISTER #31）的关系

生产 SQL Agent 作业会经 BI 视图 `T_FST_INVALID_DELIVERY` 回写「无效交付工时」，这一步发生在审批结束之后，是对工时行的事后处理，不影响审批路由与审批人解析。因此 1a 的审批人回放不需要覆盖它。Phase 2 迁入工时时，这条规则须先抽取为规则卡并评审（brief Phase 2 入口 Proposed revision 草稿），届时工时回放夹具要覆盖无效交付工时的情形。
