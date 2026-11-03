# F-1 第 3 块（#730）：验收测试（进行中）

独立测试代理所写；实现只让它们转绿、不改它们。依据：#730 评论 6599023412 与其更正 6599341585（one-design，撤销误离职的更正生成敏感角色复核）。

## 已写（草稿 PR 第一批）

| 测试 | 对应 |
|---|---|
| `PermissionAdjustmentShapeIT.aNullBeforeUnitIsOnlyForAReinstatementOfASensitiveRoleReview` | DDL 一行：`before_org_unit_id` 为空只对「敏感角色复核、任职变化、触发字段为部门」合法；范围复核、岗位触发、触发前岗位非空、触发后部门为空、非任职变化来源仍拒绝 |

红：当前 main 上第一条「应接受」的插入即被 `ck_permission_adjustments_assignment_change` 拒绝。绿：用 one-design 裁定的放宽后 CHECK 作为临时迁移跑，`PermissionAdjustment*IT` 30 条全绿（临时迁移不提交）。

## 待写（等 #1089 的 `EmployeeCorrected` 新载荷合入）

- A-U1：撤销误离职的更正，对持有至少一个敏感角色的人生成恰好一条 SENSITIVE_ROLE_REVIEW（人级，`role_id` 为空），重投递不再生成。
- A-U2：无敏感角色的人不生成；任何情况下都没有 SCOPE_REVIEW 条目。
- A-U3：更正后不在职不生成条目，监听器不重试。
- 调岗事件前部门为空时被拒并告警（监听器侧）。
