# F-1 第 3 块（#730）：验收测试（进行中）

独立测试代理所写；实现只让它们转绿、不改它们。依据：#730 评论 6599023412 与其更正 6599341585（one-design，撤销误离职的更正生成敏感角色复核）。

## 已写（草稿 PR 第一批）

| 测试 | 对应 |
|---|---|
| `PermissionAdjustmentShapeIT.aNullBeforeUnitIsOnlyForAReinstatementOfASensitiveRoleReview` | DDL 一行：`before_org_unit_id` 为空只对「敏感角色复核、任职变化、触发字段为部门」合法；范围复核、岗位触发、触发前岗位非空、触发后部门为空、非任职变化来源仍拒绝 |

红：当前 main 上第一条「应接受」的插入即被 `ck_permission_adjustments_assignment_change` 拒绝。绿：用 one-design 裁定的放宽后 CHECK 作为临时迁移跑，`PermissionAdjustment*IT` 30 条全绿（临时迁移不提交）。

## 事件驱动（`organization/workforce/CorrectionUndoesSeparationReviewIT`，4 条，按 #1089 的 `EmployeeCorrected` 最终载荷）

| 测试 | 对应 |
|---|---|
| A-U1 `anUndoneSeparationGivesOnePersonItemForAHolderOfSensitiveRoles` | 持有两个敏感角色的人得恰好一条人级敏感角色复核（`role_id` 空、无收件人），触发字段部门、触发前部门岗位空、触发后为更正后的、触发命令为该更正命令；重投递与同命令另一事件不再生成 |
| A-U2 `aPersonWithoutASensitiveRoleGetsNothingAndNobodyGetsAScopeReview` | 只有 employee 的人不生成；任何人都没有范围复核条目（对照：持有者生成，证明监听器跑过） |
| A-U3 `aCorrectionThatEndsEmploymentGivesNothingAndIsNotRetried` | 更正后不在职（触发后部门岗位空）持有敏感角色也不生成；事件的每条发布状态为 COMPLETED，无失败重试，重投递仍不生成 |
| 告警 `aTransferWithoutItsBeforeDepartmentIsAlertedNotRetriedAndGivesNoItem` | 转岗事件缺触发前部门（空）：恰好一条以 `ALERT P2 platform.identity ` 开头的 ERROR 日志（自由文本只断言含事件 id、不含此人姓名与工号；员工 id 允许出现）；不生成条目；事件发布已 COMPLETED（one-design 裁定，#730 评论 6600900803） |

红：main 上 identity 没有处理 `EmployeeCorrected` 的监听器，四条都在等待 identity 发布而超时（失败信息列出该事件仅有 audit 的发布）。绿：临时的最小监听器（只对 security_auditor、business_admin 持有者按裁定插入一行）加放宽后的 CHECK，前 3 条全绿（告警条随实现落地后转绿）；临时文件不提交。

## 未覆盖与存疑

- 告警条的日志取自根 logger 的 ListAppender；事件类型是否出现在行内裁定写了但不好定格式，未断言（只断言事件 id）。
- 「此刻持有的敏感角色」用 security_auditor、business_admin 两个角色代表；哪些角色算敏感由 `SensitivePermissions` 定，用例不自带第三方角色。
- 撤销误离职后通常只剩 employee 一个角色，条目通常不生成：用户已于 2026-11-03 答 A（复核对象取更正时仍持有的敏感角色；决策片段 `docs/decisions/2026-11-03-095141-one-lead-复核对象取更正时仍持有的敏感角色.md`，PR #1101，ADR-0082），本批照此。
