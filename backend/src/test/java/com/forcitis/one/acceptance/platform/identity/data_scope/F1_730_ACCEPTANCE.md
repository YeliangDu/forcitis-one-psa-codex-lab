# F-1 第 3 块（#730）：验收测试（进行中）

独立测试代理所写；实现只让它们转绿、不改它们。依据：#730 评论 6599023412 与其更正 6599341585（one-design，撤销误离职的更正生成敏感角色复核）。

## 已写（草稿 PR 第一批）

| 测试 | 对应 |
|---|---|
| `PermissionAdjustmentShapeIT.aNullBeforeUnitIsOnlyForAReinstatementOfASensitiveRoleReview` | DDL 一行：`before_org_unit_id` 为空只对「敏感角色复核、任职变化、触发字段为部门」合法；范围复核、岗位触发、触发前岗位非空、触发后部门为空、非任职变化来源仍拒绝 |

红：当前 main 上第一条「应接受」的插入即被 `ck_permission_adjustments_assignment_change` 拒绝。绿：用 one-design 裁定的放宽后 CHECK 作为临时迁移跑，`PermissionAdjustment*IT` 30 条全绿（临时迁移不提交）。

## 事件驱动（`organization/workforce/CorrectionUndoesSeparationReviewIT`，26 条，按 #1089 的 `EmployeeCorrected` 最终载荷）

| 测试 | 对应 |
|---|---|
| A-U1 `anUndoneSeparationGivesOnePersonItemForAHolderOfSensitiveRoles` | 持有两个敏感角色的人得恰好一条人级敏感角色复核（`role_id` 空、无收件人），触发字段部门、触发前部门岗位空、触发后为更正后的、触发命令为该更正命令；重投递与同命令另一事件不再生成 |
| A-U2 `aPersonWithoutASensitiveRoleGetsNothingAndNobodyGetsAScopeReview` | 只有 employee 的人不生成；任何人都没有范围复核条目（对照：持有者生成，证明监听器跑过） |
| A-U3 `aCorrectionThatEndsEmploymentGivesNothingAndIsNotRetried` | 更正后不在职（触发后部门岗位空）持有敏感角色也不生成；事件的每条发布状态为 COMPLETED，无失败重试，重投递仍不生成 |
| 告警 `aTransferWithoutItsBeforeDepartmentIsAlertedNotRetriedAndGivesNoItem` | 转岗事件缺触发前部门（空）：恰好一条以 `ALERT P2 platform.identity ` 开头的 ERROR 日志（自由文本只断言含事件 id、不含此人姓名与工号；员工 id 允许出现）；不生成条目；事件发布已 COMPLETED（one-design 裁定，#730 评论 6600900803） |
| 真实转岗 `aRealTransferOfAHolderGivesOneItemDueByTheEntitysOwnCalendar` | 人事专员提交转岗命令：持有敏感角色者得一条敏感角色复核（触发字段部门、前后部门、触发命令）；A-6(a)：法人自有日历（11-04 假日）时 escalation_due_at 为第 5 个工作日结束 11-10 |
| 真实转岗 `aRealTransferOfAPersonWithoutASensitiveRoleGivesNothing` | 只有 employee 角色者转岗无条目，持有者对照有 |
| A-6(a) 对照 `anEntityWithoutItsOwnCalendarUsesTheDefaultOne` | 信封法人无自有日历：默认日历，11-09 |
| A-6(c) `anUnresolvableEntityStillGivesTheItemWithTheDefaultCalendarAndOneP3Alert` | 信封无法人：仍生成条目、默认日历 11-09、恰好一条 `ALERT P3 platform.identity ` 含事件 id 不含工号姓名、事件 COMPLETED 一次 |
| A-6(d) `theTableHasNoLegalEntityColumn` | 表无法人列（现已绿，守住不加） |

A-6 不覆盖/存疑：(b) 到期再生成用 `EmployeeLegalEntities.on` 属 S2，待 S2 作业用例；法人自有日历夹具直接写 `organization_attendance.work_calendar_years/days`（旧表，现行读取路径；日历维护 API 目前只管默认日历）——若实现改读版本表（`work_calendar_versions`），夹具要跟着改，请通知我；`escalation_due_at` 只断言落在第 5 个工作日当天（本地 00:00 之后、次日 00:00 及之前），不钉「结束」是 24:00 还是 23:59:59；范围复核（SCOPE_REVIEW）的真实转岗路径见下一节。

红：main 上 identity 没有处理 `EmployeeCorrected` 的监听器，四条都在等待 identity 发布而超时（失败信息列出该事件仅有 audit 的发布）。绿：临时的最小监听器（只对 security_auditor、business_admin 持有者按裁定插入一行）加放宽后的 CHECK，前 3 条全绿（告警条随实现落地后转绿）；临时文件不提交。

后加的 5 条（A-6）：两条真实调动的用例在 awaitItemsAbout 超时；默认日历与无法解析主体的两条在 identityHandled 超时（只有 audit 的发布）；`theTableHasNoLegalEntityColumn` 在基线上按设计就是绿的。

## 未覆盖与存疑

- 告警条的日志取自根 logger 的 ListAppender；事件类型是否出现在行内裁定写了但不好定格式，未断言（只断言事件 id）。
- 「此刻持有的敏感角色」用 security_auditor、business_admin 两个角色代表；哪些角色算敏感由 `SensitivePermissions` 定，用例不自带第三方角色。
- 撤销误离职后通常只剩 employee 一个角色，条目通常不生成：用户已于 2026-11-03 答 A（复核对象取更正时仍持有的敏感角色；决策片段 `docs/decisions/2026-11-03-095141-one-lead-复核对象取更正时仍持有的敏感角色.md`，PR #1101，ADR-0082），本批照此。


## SCOPE_REVIEW from a transfer (ruling #730 comment 6602688198; 9 more cases in the same class)
| Ruling case | Test |
|---|---|
| 1 custom-org-unit holder, real transfer: one row, recipient new head, trigger command/field, before/after, one ASSIGNED card, one Opened audit | `aRealTransferOfACustomOrgUnitHolderGivesOneScopeReviewForTheNewHead` |
| 3 two scoped grants: one row | `twoScopedGrantsStillGiveOneScopeReview` |
| 4 only ALL / OWN_ORG_UNIT grants: none (control: sensitive holder gets its one item) | `aHolderOfOnlyNonScopedGrantsGetsNoScopeReview` |
| 5 POSITION-only transfer: none | `aPositionOnlyTransferGivesNoScopeReview` |
| 6 replay and second event of one command: one row, one card | `aReplayGivesOneScopeReviewAndOneCard` |
| 7 no head: NULL recipient, no card, one `ALERT P3 platform.identity ` line | `aNewDepartmentWithoutAHeadGivesAnItemWithoutRecipientCardOrNothingButOneP3Alert` |
| 7b the moved person heads the new department (ruling #730 comment 6604945364): same as no head: NULL recipient, no card, one `ALERT P3 platform.identity ` line | `aMovedPersonWhoHeadsTheNewDepartmentIsTreatedLikeNoHead` |
| 8 sensitive + scoped: two items, scope one has the recipient | `aSensitiveAndScopedHolderGetsOneItemOfEachKind` |
| 9 EmployeeCorrected with ORG_UNIT and a before department: same rule; empty before: nothing | `aCorrectionOfTheDepartmentFollowsTheSameRule` (uses the #1089 payload, which the class already compiles against) |

Red on baseline: all 8 fail (timeout waiting for the item or for identity to handle the event); the fixtures (roles through the role api by a super administrator, heads written in org_units) work.

Not covered / doubts:
- Case 2 (role with LISTED legal entities): the role schema on main has no such scope (`access_scope_kind` is ALL, CUSTOM_ORG_UNITS, OWN_ORG_UNIT, OWN_ORG_UNIT_SUBTREE, SELF, SELF_AND_DIRECT_REPORTS), so no fixture can be built; to be added when the scope exists. Question for one-design.
- The ASSIGNED card of case 1 is asserted to go to exactly the new head's WeCom user id (through the `WecomGateway` mock: one `WecomCard` with purpose PERMISSION_ADJUSTMENT_ASSIGNED and the item id, one WeCom user id; acceptance tests mock the gateway, so nothing reaches the delivery table), per ruling 6602940790.
- Source 2 (ORG_UNIT_INACTIVE generation) has no acceptance, by ruling.

## EmployeeCorrected behaves like a transfer (ruling #730 comment 6604000297)

`EmployeeCorrected` on main already carries `triggerFieldKinds` and the before/after department and position, so none of the cases waits for #1089.

| Ruling case | Case |
|---|---|
| 1 department correction, sensitive holder: one SENSITIVE_ROLE_REVIEW (field ORG_UNIT, trigger command, before/after unit, no recipient) | `aDepartmentCorrectionOfASensitiveHolderGivesOneSensitiveItem` |
| 2 also a CUSTOM_ORG_UNITS grant: plus one SCOPE_REVIEW, recipient head of Y, one assigned card to that head | `aDepartmentCorrectionOfASensitiveAndScopedHolderAlsoGivesAScopeReviewForTheNewHead` |
| 3 position-only: one SENSITIVE_ROLE_REVIEW (field POSITION, before/after position), no scope review although the person holds a scoped grant | `aPositionCorrectionGivesOnePositionItemAndNoScopeReview` |
| 4 both fields: two sensitive items (one per field) plus the scope item | `aCorrectionOfBothFieldsGivesOneSensitiveItemPerFieldAndTheScopeReview` |
| 5 neither a sensitive role nor a scoped grant: no row (control: a holder's correction raises one) | `aCorrectionOfAPersonWithNeitherGrantGivesNothing` |
| 6 replay and a second event of the same command: no new rows, no new card | `aReplayOfACorrectionAddsNothing` |
| 7 empty `triggerFieldKinds`: no row, completed once (control: a department correction raises one) | `aCorrectionWithoutTriggerFieldsGivesNothing` |
| 8 correction that separates: no row, completed once, no log line starting with `ALERT` | `aCorrectionThatSeparatesGivesNothingAndRaisesNoAlert` |
| 9 undone separation as ADR-0082 | already `anUndoneSeparationGivesOnePersonItemForAHolderOfSensitiveRoles` (A-U1) and the undone half of `aCorrectionOfTheDepartmentFollowsTheSameRule` |

Not covered: the dedupe of a transfer against a correction of the same person (different commands, so no dedupe; only stated by the ruling).
