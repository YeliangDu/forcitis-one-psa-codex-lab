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

```json
{
  "formatVersion": 1,
  "processKey": "TIMESHEET",
  "processAdministratorUserId": 900,
  "legacyFallbackUserIds": [500],
  "snapshot": {
    "employees": [{"userId": 111, "orgUnitId": 10, "leaderUserId": 110, "employed": true}],
    "orgUnits":  [{"orgUnitId": 10, "parentOrgUnitId": 1, "headUserId": 110,
                   "regionalReceivablesManagerId": null, "active": true}],
    "roleHolders": {"BU-002": [110, 112]}
  },
  "instances": [{
    "instanceId": "…",
    "initiatorId": 201,
    "formFields": {"manager_id": ["111"]},
    "variables":  {"deliveryDeptId": ["11"]},
    "nodes": [{
      "nodeKey": "managerReview",
      "rule": {"type": "FORM_FIELD_USERS", "field": "manager_id"},
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
| `snapshot.employees` | **老 `system_users` 当时的快照**：`employed=false` 表示停用 / 已删 / 离职；`leaderUserId` 是人员级直属领导（RULE-372） |
| `snapshot.orgUnits` | **老 `system_dept` 当时的快照**：负责人、区域应收负责人（`ar_user_id`）、上级、是否有效 |
| `snapshot.roleHolders` | 角色编码到持有人（`BU-002` 等；导出时把老 role id 换成角色编码） |
| `instances[].formFields` | 业务表单字段（指派规则的 `field` 从这里取，如 `manager_id`） |
| `instances[].variables` | 其余流程变量（指派规则的 `variable` / `department` 从这里取，如 `deliveryDeptId`） |
| `nodes[].rule` | 该节点的**唯一**指派规则（RULE-262）：`type` 加各类型的属性，词汇见下表 |
| `nodes[].selfApprovalAllowed` | 老节点标了「员工活动」的写 `true`（RULE-307 豁免） |
| `nodes[].actualApprovers` | 实际办理该节点的人（会签多人则都列出）；取自 `bpm_task_ext` 的办理人 |

缺省的布尔值按 `false` 处理，缺省的列表按空处理。

### 规则类型词汇（与 BPMN `forcitis:assignmentRule` 完全相同）

| `type` | 属性 | 老系统对应 |
|---|---|---|
| `ROLE` | `role`（角色编码） | 规则类型 10 |
| `NAMED_USERS` | `users`（逗号分隔 ID） | 规则类型 30；类型 70（空节点）导出为老管理员 ID |
| `FORM_FIELD_USERS` | `field` | 类型 60（`options` 的第一个字段）；脚本 9（`scriptField`） |
| `FORM_FIELD_USER_LEADERS` | `field` | 脚本 33 |
| `INITIATOR` | — | 脚本 10 |
| `INITIATOR_LEADER` | `level`（1 或 2） | 脚本 20 / 21 |
| `REGIONAL_RECEIVABLES_MANAGER` | `department`（变量名，缺省 `deliveryDeptId`） | 脚本 30 |
| `DEPARTMENT_HEAD` | `department`（变量名；缺省为发起人所在部门） | 脚本 31 |
| `DEPARTMENT_DIRECTOR` | `department` | 脚本 32 |
| `PROCESS_VARIABLE_USERS` | `variable` | 引擎或钩子写入的变量 |

老类型 20（部门成员）、22（岗位）、40（用户组）在生产 TIMESHEET 里没有使用（`runtime/prod_bpm.tsv` ##13），回放工具不支持；
类型 21 按固定部门指派在 TIMESHEET 里也没有使用，出现时需要先扩充规则词汇。

## 执行步骤（本会话，Q-4 授权后）

1. 经用户授权，在生产做只读导出：近期、**当前定义版本**的 TIMESHEET 实例样本；同时导出当时的 `system_users` / `system_dept` 快照、
   `bpm_task_assign_rule`、流程变量快照与实际审批人序列，按上面的格式写成一个 JSON，存 `~/.modernize/psa/`。
2. 运行命令行，读 `replay-report.md`。
3. 各 `REVIEWED_*` 类别请用户逐类接受；每条 `DEFECT` 修复并重跑，直到没有 `DEFECT`。
4. 另出步骤 (b) 的数据源差异报告（A8 与老表）。
