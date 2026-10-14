# TIMESHEET 审批人序列回放：导出文件格式与命令行

日期：2026-10-14 · 模块：platform.approval · 依据：REIMAGINED_ARCHITECTURE.md §3.5（两步法）、检查点 2 Q-4

## 做什么

回放工具读入「当时」老系统的组织快照、每个节点的指派规则、流程变量和**实际办理的审批人**，用新系统的 `AssigneeResolver`
（与线上待办分配同一份代码）逐节点算出候选集，按「**老审批人 ∈ 新候选集**」比对（RULE-249 把随机选人改成了候选组，所以不再比较「是同一个人」）。

这是**步骤 (a) 引擎等价**：输入全部来自老系统同一时刻的快照，所以差异只可能来自新旧规则的差别，不含数据源漂移。
**步骤 (b) 数据源差异**（A8 与老表之间的漂移，RULE-365）单独出报告，不进本工具。

## 差异分类（输出里的 verdict）

| verdict | 含义 | 处理 |
|---|---|---|
| `MATCH` | 实际办理的每个人都在新候选集内 | 通过 |
| `REVIEWED_DISABLED_APPROVER` | 老审批人当时已停用 / 离职；新候选集排除这类人（RULE-253） | 按类别由用户接受 |
| `REVIEWED_HARDCODED_FALLBACK` | 老审批人是写死的兜底个人（`legacyFallbackUserIds`）；新规则转流程管理员（RULE-254） | 按类别由用户接受 |
| `REVIEWED_REPORTING_LINE` | 老汇报线不完整；新规则上推到部门负责人（RULE-250） | 按类别由用户接受 |
| `REVIEWED_SELF_APPROVAL` | 发起人自己审批自己；新规则防自审（RULE-307） | 按类别由用户接受 |
| `REVIEWED_ADMINISTRATOR_FALLBACK` | 新规则取不到人，转流程管理员并告警（RULE-250/254） | 按类别由用户接受 |
| `DEFECT` | 以上都不是：真缺陷 | **逐条修复**；出现任何 `DEFECT`，命令行以状态码 1 退出 |

## 命令行入口

不需要 Spring，不需要数据库，不联网。先编译，再运行：

```bash
cd backend
./mvnw -q compile
./mvnw -q dependency:build-classpath -Dmdep.outputFile=target/replay-cp.txt
java -cp "target/classes:$(cat target/replay-cp.txt)" \
  com.forcitis.one.platform.approval.infrastructure.replay.TimesheetApproverReplayCommand \
  ~/.modernize/psa/timesheet-export.json ~/.modernize/psa/replay-out
```

输出目录里生成 `replay-report.json`（机器可读）和 `replay-report.md`（汇总表加差异清单）。报告只含人员 ID，不含姓名。
有 `DEFECT` 时命令以退出状态 1 结束（异常信息指向报告）。

验收测试：`TimesheetApproverReplayIT`（合成数据 `src/test/resources/replay/synthetic-timesheet-export.json`，其中不含任何真实人员或组织；
测试里也运行了上面这条命令行）。

## 导出文件（JSON，`formatVersion` = 1）

**导出文件含真实人员，只存本机 `~/.modernize/psa/`，不进仓库。** 生产只读导出需用户授权（Q-4）。

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
| 规则类型 `INITIATOR`（脚本 10，发起人重做节点） | （不回放） | 该节点整个跳过，不计入节点数 |

### TIMESHEET 当前生产版本与新流程（核对）

生产当前定义 TIMESHEET:15（`runtime/prod_bpm.tsv` ##13）有 4 个节点；新流程 `cost_timesheet_approval` 的对应：

| 老节点 | 老规则 | 新节点 | 新规则 |
|---|---|---|---|
| `Activity_19wn7op` | 脚本 10（发起人重做） | 无 | 退回即结束本轮，重提开新一轮 |
| `Activity_0g7oxfy` | 类型 60，变量 `manager_id` | `projectManagerReview` | `FORM_FIELD_USERS`，字段 `projectManagerId` |
| `Activity_01vbv92` | 类型 60，变量 `area_leader`（售前工时分支） | `regionalHeadReview` | `FORM_FIELD_USERS`，字段 `regionalHeadId`，仅当路由标记 `preSalesHours` 为真 |
| `Activity_0hwqdkm` | 脚本 20（发起人的一级上级） | `supervisorReview` | `INITIATOR_SUPERVISOR`，`level=1` |

老分支条件 `containsAny('manHourTypeList.type', 0)`（含售前类工时）在新 BPMN 中不照搬：由业务模块预先算好路由标记
`preSalesHours`（`TimesheetRoutingFlag.PRE_SALES_HOURS`），BPMN 只读它。当前 BPMN 里的 `DEPARTMENT_HEAD` 节点与生产不符，已移除。
节点顺序（项目经理 → 售前分支的区域负责人 → 一级上级）取自生产定义里节点的出现顺序，等用户确认。

## 执行步骤（本会话，Q-4 授权后）

1. 经用户授权，在生产做只读导出：近期、**当前定义版本**的 TIMESHEET 实例样本；同时导出当时的 `system_users` / `system_dept` 快照、
   `bpm_task_assign_rule`、流程变量快照与实际审批人序列，按上面的格式写成一个 JSON，存 `~/.modernize/psa/`。
2. 运行命令行，读 `replay-report.md`。
3. 各 `REVIEWED_*` 类别请用户逐类接受；每条 `DEFECT` 修复并重跑，直到没有 `DEFECT`。
4. 另出步骤 (b) 的数据源差异报告（A8 与老表）。
