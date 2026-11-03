复审结论：必改
commit：f02960e9a1019a9eadcb7c83e8d30b6164b6f7b0
复审者：opus-首轮-r1115a

patch-id：5d709b1fb592bd633c15cc651b884c4081a074ca

Review of PR #1115 (#730 S1), diff `git diff c737cbaa4...f02960e9a` (2 commits: d777d7600, f02960e9a). PR head confirmed equal to f02960e9a at start and end of the review.

## Impact

platform.identity (new listeners HandleEmployeeCorrected / HandleEmployeeTransferred, new aggregate PermissionAdjustment, migration relaxing `ck_permission_adjustments_assignment_change`), organization.attendance (WorkCalendar now extends kernel `WorkdayDeadlines`; `endOfWorkdayAfter` accepts a null legal entity), platform.audit (new event audited by `AuditDomainEvents`). Callers proven 2 layers deep: `endOfWorkdayAfter` is called by `DeadlinePlanner.workdayAfter` (<- `plan` <- `TaskRegistrar`) and by `HandleEmployeeCorrected`. DeadlinePlanner still fails fast on a null entity, because `plan` calls `isWorkday(created, legalEntityId)` first and that still runs `requireNonNull` (WorkCalendarService.java:46). So the approval deadlines behave as before.

## Must-fix

### 1. The card is not brought in line with ADR-0082, and a DB column comment now contradicts the code (correctness, hard constraint)
- ADR-0082 "影响面" (origin/main docs/adr/ADR-0082.md) says: "`docs/architecture/implementation-cards/f-1.md`：表检查、两条 COMMENT、监听器与不变量随本 ADR 对齐（卡文改动在实现 #730 / 迁移的 PR 里一并做）". This is that PR. Only A-6 was changed in f-1.md. At f02960e9a the card still says:
  - f-1.md:510 (7.1 DDL) `(origin_kind = 'ASSIGNMENT_CHANGE') = (before_org_unit_id IS NOT NULL AND after_org_unit_id IS NOT NULL)` and f-1.md:513 `before_org_unit_id <> after_org_unit_id`. The new migration replaced both.
  - f-1.md:581 COMMENT on `before_org_unit_id`: "为空表示不是任职变化产生的条目". The migration changed this comment, the card did not.
  - f-1.md invariant 11: "任职变化触发的条目有触发字段、触发前后部门；其他来源没有". This is no longer true.
  The card's 7.1 is the "expected schema diff" that reviewers check migrations against (scripts/review/PROMPT.md). As it stands, the next reviewer of this table would flag a correct migration as a mismatch.
- The DB comment on `trigger_personnel_command_id` (V20261103120000...permission_adjustments.sql:133) still reads "触发人事命令 ID：调动命令（EmployeeTransferred）". This PR now writes `EmployeeCorrected.personnelCommandId` into that column (HandleEmployeeCorrected.java:246-247). The card on main already says "调岗或更正命令（EmployeeTransferred、EmployeeCorrected）" (f-1.md:578).
- Fix: in V20261103130000 add `COMMENT ON COLUMN ...trigger_personnel_command_id` with the card's text. In f-1.md, update the 7.1 CHECK, the `before_org_unit_id` COMMENT draft and invariant 11 to match the migration (rows 9-17 if the wording there also names "前后部门").

### 2. The audit of an opened item does not match ADR-0032 or the card (lost line of defence / hard constraint)
- ADR-0032.md:142 lists the new audit action as `platform.identity.permission-adjustment.opened`. f-1.md:97 (the state machine table) says the same. The PR registers `platform.identity.PermissionAdjustmentOpened` instead (must-audit-actions.yaml), because the action comes from the event type via `AuditDomainEvents` (`new AuditEntry(envelope.eventType(), ...)`). No deviation is recorded in docs/adr/readme-deviations.md, unlike the `kept -> upheld` case (f-1.md:26).
- Card C4 (f-1.md:268) sets the detail of the `opened` audit to "条目 ID、类型、来源、当事人账号 ID 或角色 ID + 部门 ID、触发人事命令 ID 或触发组织结构命令 ID、触发字段". Card E1 (f-1.md:370) sets the event payload to "条目 ID、类型、来源、账号 ID、角色 ID、部门 ID、原条目 ID、触发命令 ID、有效至". `PermissionAdjustmentOpened` carries only `permissionAdjustmentId` and `userId` (PermissionAdjustmentOpened.java:9) and does not implement `AuditDetail`. The audit entry's detail is therefore only "by platform.identity.permission-adjustment" (AuditDomainEvents: `detail = ... "by " + systemActorName`). Kind, origin, trigger command and trigger field are not in the audit trail.
- This is v1 of a new event contract, published now. Adding these fields later means a contract change, and the later slices (S2 regenerated/upheld/expired) will copy this pattern.
- Fix: add the card's ID/enum fields to `PermissionAdjustmentOpened`: kind, originKind, triggerPersonnelCommandId, triggerFieldKind, after/before org unit ids, escalationDueAt (and the original item id for S2). Implement `AuditDetail` with those IDs and enums. For the action name, either write the ADR's action, or get one-design / one-architecture to rule on using the event type and record it in readme-deviations.md. Do not decide the name alone.

### 3. The escalation deadline (A-6 ruling) has no test; two breaks did not go red (break verification)
- The PR body claims: "`@Nullable LegalEntityId` (null = default calendar) as ruled in A-6; event with no legal entity -> `ALERT P3` and default calendar". The A-6 ruling (#730 comment 6600968506) has these done-when items: "(a) ... `escalation_due_at` equal to the 5th workday end per X's calendar, with X ≠ the default entity and a differing holiday set ..." and "(c) an unresolvable entity still creates the item, due date per the default calendar, one P3 ALERT line".
- No test in this diff or in #1103 (f359cf7db) checks `escalation_due_at`, calls `endOfWorkdayAfter` with null, or looks for the P3 line (`git grep endOfWorkdayAfter` in tests: only non-null `FST`/`fst` calls; `git grep "ALERT P3"` in tests on the acceptance branch: none).
- Breaks (temporary worktree, see table below):
  - Putting `Objects.requireNonNull(legalEntityId)` back in `WorkCalendarService.endOfWorkdayAfter` left CorrectionUndoesSeparationReviewIT, WorkCalendarIT and WorkCalendar*IT green. With that break, an event without a legal entity makes the listener throw and retry forever, and the item is never created. That is exactly what the ruling forbids ("a lost review is worse than an approximate date").
  - Passing `null` instead of the envelope's entity in HandleEmployeeCorrected.java:245 (every item gets the default calendar) left CorrectionUndoesSeparationReviewIT green.
- Fix: add tests in the PR's own files (do not change #1103):
  - a WorkCalendarServiceTest case with `endOfWorkdayAfter(..., null)` answering from the default calendar;
  - an identity-level IT, or a unit test with a fake `WorkdayDeadlines`, showing that the envelope's entity reaches the port, that a null entity still creates the item and logs exactly one `ALERT P3 platform.identity ` line, and that `escalation_due_at` is stored.
  - Alternatively, get one-test to add (a) and (c) to #1103, and say so on the PR before it is released.

## Suggestions (do not block)
- HandleEmployeeCorrected.java:173-175: the imports are out of order (`java.time.Clock`, `com.forcitis.one.kernel.LegalEntityId`, `java.time.Instant`).
- EventSequenceContinuityTest.permissionAdjustment uses fully qualified names inline. Imports would read better.
- system-actors.txt and asyncapi `x-system-actors` describe `platform.identity.permission-adjustment` as "监听器 HandleEmployeeCorrected ...". HandleEmployeeTransferred carries the same `@SystemActorName`, so name it too.
- asyncapi/identity.yaml `businessDate: 无：不是带日期的业务里程碑（运维或配置变更）`: the item is neither an operations nor a configuration change. Drop the parenthesis.
- WorkCalendarService.isWorkday with a null entity looks up `WorkCalendarKey(null, year)` and then `defaultFor(year)`, which is the same key twice. Skip the first lookup when the entity is null.
- If the work calendar of the handling year (or of the next year, near year end) is not maintained, the listener throws and Modulith retries until an admin saves the calendar. That is consistent with "fail loudly", but the item is delayed. Mention it in the PR so S2/S3 deal with it consistently.
- glossary `permission_adjustment` changes `kind` from entity to aggregate on a user-confirmed term. The wording is unchanged, so this is fine, but tell one-glossary.
- Outside this diff: the `ALERT platform.audit ...` lines in AuditDomainEvents have no P1/P2/P3 level (O-2).

## Checked and fine
- PR head = f02960e9a1019a9eadcb7c83e8d30b6164b6f7b0 (`gh pr view 1115 --json headRefOid`, at start and end); `git cat-file -e f02960e9a^{commit}` ok.
- No secrets, no row-level data. Test fixtures use random UUIDs only.
- No acceptance test is changed in the diff: `git diff --stat c737cbaa4...f02960e9a` touches only PermissionAdjustmentTest (new) and EventSequenceContinuityTest (one case added). No assertion was loosened.
- Migration vs ADR-0082 decision 3 and ruling 6599023412/6599341585, line by line: `(AC) = (trigger_field_kind NOT NULL)` kept; `(AC) = (after NOT NULL)`; `before NOT NULL OR (SENSITIVE_ROLE_REVIEW AND ORG_UNIT)` for AC; `before NOT NULL OR before_position NULL`; non-AC rows have all four columns NULL; `IS DISTINCT FROM`. The ruling's separate line `before IS NULL OR AC` is implied by the non-AC clause. No new column or enum value. The new version V20261103130000 is above the highest on main (V20261103120200). The merged V20261103120000 is untouched.
- ADR-0082 decision 1/2/4 vs HandleEmployeeCorrected:
  - Trigger: ORG_UNIT in kinds, previous dept null, new dept set.
  - One item per person, no role_id, no recipient, no SCOPE_REVIEW.
  - Sensitive roles judged by grants open when the event is handled (`openGrants` + `SensitivePermissions.roleIsSensitive`).
  - Dedupe key (kind, user, command, field) checked under the `findForUpdate` lock, with `uk_permission_adjustments_assignment_trigger` as a backstop.
  - A correction that ends employment returns without retry.
- Ruling 6600900803 vs HandleEmployeeTransferred: one ERROR line starting `ALERT P2 platform.identity `, containing the event id and type and no employee id. No row is written. The listener returns normally.
- Module boundaries: identity uses the kernel port `WorkdayDeadlines` and `AccountEmployees`, not attendance internals. workforce types come only from `organization.workforce.api` and `.events`, which the existing listeners (HandleEmployeeReinstated) already use.
- Audit plumbing works: `AuditSubjectKind.PERMISSION_ADJUSTMENT(true)` maps aggregate type `PermissionAdjustment`, and AuditSubjectKindRegistryIT passed.
- Deletion: the unbuilt-roots entry for `platform_identity.permission_adjustments` is removed now that the root class exists. The generated built.yaml and gaps.md were regenerated to match.
- coding-core §B:
  - B6/B7: no swallowed exceptions; the null checks follow `@Nullable` event fields.
  - B8: listeners are idempotent through the business dedupe key.
  - B9: `JdbcClient` with named params.
  - B12: largest new file 124 lines.
  - B14: the docstrings in HandleEmployeeCorrected, PermissionAdjustment and WorkdayDeadlines match the code (except the stale DB/card text in must-fix 1).
- Tests run in a temporary worktree at f02960e9a, with #1103's three test files (f359cf7db) laid over it:
  - surefire PermissionAdjustmentTest, EventSequenceContinuityTest, WorkCalendarServiceTest: 26 run, 0 failed.
  - failsafe CorrectionUndoesSeparationReviewIT (4), PermissionAdjustmentShapeIT (12), AuditSubjectKindRegistryIT (4): 20 run, 0 failed.

### Break verification (temporary worktree, removed afterwards)
| Defence | How broken | Result |
|---|---|---|
| Only holders of a sensitive role get an item | `if (!holdsSensitiveRole \|\| ...)` -> `if (...)` | red: CorrectionUndoesSeparationReviewIT A-U2 ("no sensitive role, no item") |
| Dedupe per (kind, user, command, field) | `existsForTrigger` short-circuited to false | red: A-U1 (unique index refuses; listener FAILED, not finished) |
| Only an undone separation is handled | removed `previousOrgUnitId()==null && newOrgUnitId()!=null` | red: A-U3 (listener FAILED / retried) |
| Transfer without dept before -> one ALERT P2 | `LOG.error` -> `LOG.warn` | red: transfer case ("exactly one alert line") |
| CHECK: no dept before means no position before | dropped `before_org_unit_id IS NOT NULL OR before_position_id IS NULL` | red: PermissionAdjustmentShapeIT:124 |
| CHECK: null dept before only for SENSITIVE_ROLE_REVIEW | dropped the `kind = 'SENSITIVE_ROLE_REVIEW'` term | red: PermissionAdjustmentShapeIT:120 |
| Null legal entity -> default calendar, item still created | re-added `requireNonNull(legalEntityId)` in `endOfWorkdayAfter` | **not red** (CorrectionUndoesSeparationReviewIT, WorkCalendarIT, WorkCalendar*IT all green) -> must-fix 3 |
| Due date uses the envelope's legal entity | passed `null` instead of `legalEntity` | **not red** -> must-fix 3 |
| Account row lock against two events of one command | not broken: the race cannot be reproduced deterministically; the unique index is the backstop | not verified |
