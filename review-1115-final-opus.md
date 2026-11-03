复审结论：必改
commit：59a6a2a728235a4ee02f2c2e94d0b747946d0882
复审者：opus-终审-1115

patch-id：ba5fc94becbb7bd7cf98330e6176f0be82782af2

Impact: platform.identity gains two listeners (EmployeeCorrected, EmployeeTransferred) that write permission_adjustments and queue a WeCom card through platform.integration; organization.attendance WorkCalendar now extends kernel WorkdayDeadlines (nullable entity). Callers proven over 2 layers: workforce event publication -> HandleEmployeeCorrected/HandleEmployeeTransferred -> OpenAssignmentReviews -> repository / DomainEvents / WecomGateway.

## Must-fix

1. Correctness (wrong behaviour, a defence line missing): a correction that changes the department or the position of an employed sensitive-role holder raises no SENSITIVE_ROLE_REVIEW.
   - Where: `backend/src/main/java/com/forcitis/one/platform/identity/application/HandleEmployeeCorrected.java:32-49`. Line 32 returns for every correction without ORG_UNIT, so a position-only correction raises nothing. Lines 37-48 open only `openScopeReviewFor` for a department move, never `openFor`. The javadoc at line 15 says this on purpose ("raises the scope review of a transfer, nothing else"), and the unit tests `HandleEmployeeCorrectedEscalationTest.aMoveBetweenDepartmentsRaisesNothing` (:175, a sensitive holder moved by a correction gets no item) and `aPositionOnlyCorrectionRaisesNothing` (:184) lock it in.
   - Evidence: in ADR-0032 item 4.3, a change of `org_unit_id` or `position_id` triggers the review for a holder of a two-person role. ADR-0081 decision 3 keeps `EmployeeCorrected` as a source-1 trigger. It gives the event the transfer's payload so that items can be generated from it, and it rules out "敏感角色复核与范围复核都不生成" only when one side has no department. The dedupe key stays (kind, person, command, field). ADR-0082 supersedes only the undo-separation sentence and says "其余照 ADR-0081 不变". f-1.md line 158 says items are generated after "调岗、更正". Ruling 6602688198 case 9 says a correction "follows the same rule". No ruling or user decision on #730 exempts corrections between two employed states from the sensitive-role review. The acceptance does not cover this case: case 9's moved person holds only a custom-scope role, so it neither proves nor forbids the behaviour. The implementer's own tests fix a narrower rule than the ADR.
   - Fix: when both sides are employed (`previousOrgUnitId != null && newOrgUnitId != null`), handle the correction like `HandleEmployeeTransferred`. Call `openFor` for each field in `triggerFieldKinds` (ORG_UNIT and POSITION), and call `openScopeReviewFor` when the field is ORG_UNIT and the departments differ. Keep the undo path (previous department null) and the employment-ending path (new department null) as they are. Flip the two unit tests to assert one SENSITIVE_ROLE_REVIEW per changed field, and fix the javadoc. If the author believes corrections should be exempt, that needs a one-design ruling, not an implementation choice (baseline rule 3).

2. Correctness (fails closed in the wrong place, the review is lost and the listener loops): when the new department's head has no WeCom user id, the scope review item is never stored.
   - Where: `backend/src/main/java/com/forcitis/one/platform/identity/application/OpenAssignmentReviews.java:116`, `new WecomCard(List.of(profile.wecomUserId()), ...)`. `EmployeeProfile.wecomUserId` is nullable: the record has no null check, `TaskNotifier.java:144` and `AdministratorFloorNotice.java:54` both handle null, and `SnapshotReconciler.java:199` counts employed people without one. `List.of(null)` throws NPE. This runs inside the listener transaction after `adjustments.insert` and `events.publish`, so the insert rolls back, the publication fails and is retried, and every retry fails the same way. A lost review is exactly what ruling 6600968506 item 4 says is worse than an approximate answer. The javadoc at :108 ("none when ... the head has no WeCom user id") contradicts the code, which also counts as correctness (agents follow comments).
   - Reproduction (temporary copy of the head, deleted afterwards): a unit test cloned from `HandleEmployeeCorrectedEscalationTest.aMoveGivesTheNewHeadAScopeReview` with `new EmployeeProfile(head, "Head", unit, null, EMPLOYED, false)` -> `java.lang.NullPointerException at handle:123`; no item is inserted.
   - Fix: when the profile is absent or has no WeCom user id, keep the item (recipient = head), send no card and log one `ALERT P3 platform.identity` line with the event id and item id, the same as the no-profile branch. Add the unit case. Consider applying the same branch to a head who is not EMPLOYED, as `TaskNotifier` does.

## Suggestions (do not block)

- `OpenAssignmentReviews.escalationDueAt(trigger, now)` ignores `now` and reads `clock.instant()` again (:128); use `zone.dateOf(now)` so the creation instant and the start day cannot fall on two different days around midnight.
- ADR-0081 decision 3 requires an `EmployeeCorrected` that lacks `triggerFieldKinds` to be refused with an ALERT, not retried. Today a publication serialized before #1089 that is resubmitted fails in `List.copyOf(null)` while it is deserialized, and it keeps retrying. The ADR's go-live condition ("上线前须确认无此类积压") covers this. A note on #730 would keep it visible.
- The acceptance A-6(c) filters `ALERT P3` lines by text only, not by level. A downgrade to INFO stays green there and is caught only by the unit test (see the break table). This is for one-test, not the author.
- `HandleEmployeeCorrectedEscalationTest` uses broad `verify(adjustments, never()).insert(any())` (coding-core B15 "no broad never()"); assert against a positive control instead (as the acceptance does).
- The PR body is stale: it says the transfer path is "only the refusal path", "stacked on #1110", "other corrections do nothing". Update it before merging, because reviewers and the queue read it.
- The card's target URL is hard-coded in a fourth place (`"https://one.forcitis.com/"` at :117; three existing constants elsewhere). Use a constant or the existing property style.

## Items checked and fine

- PR head equals the sha: `gh pr view 1115 --json headRefOid` -> 59a6a2a72...; `git cat-file -e` ok; diff taken with `git diff origin/main...59a6a2a72` (merge-base 88e34b1d5), 40 files.
- Acceptance unchanged: `git diff origin/claude/one-test-730-acceptance 59a6a2a72 -- <5 acceptance files>` gives 0 lines for each of CorrectionUndoesSeparationReviewIT, PersonnelCommandSupport, F1_730_ACCEPTANCE.md, PermissionAdjustmentRows, PermissionAdjustmentShapeIT. #1103 head = 0859b499d = the merged commit.
- No assertion was loosened: every test file in the diff is additions only (`--numstat`); PersonnelCommandSupport `private`->`protected` comes from #1103.
- Migrations: V20261103130000 and V20261103140000 are new files, above the main maximum 20261103120200; no merged migration is edited; the marker `migration-baseline-open` is present (warn only).
- Schema matches the card and the rulings: `ck_permission_adjustments_assignment_change` equals the f-1.md DDL in the same diff and ruling 6599023412 plus correction 6599341585 item 3 (after not null iff ASSIGNMENT_CHANGE; null before only for SENSITIVE_ROLE_REVIEW + ORG_UNIT; before position null when before department null; `IS DISTINCT FROM`; non-assignment rows all four null). The outbound CHECKs keep every value of the latest main definition and add `PERMISSION_ADJUSTMENT` / `PERMISSION_ADJUSTMENT_ASSIGNED` (f-1.md:656).
- Undo separation (ADR-0082 decisions 1, 2, 4): one person item, role-free, ORG_UNIT, before department and position null, no scope review, no recipient; an employment-ending correction (new department null) returns. `HandleEmployeeCorrected.java:32-44`.
- Transfer: refusal with one `ALERT P2 platform.identity` ERROR line and a normal return when there is no department before (ruling 6600900803); a sensitive review per field; a scope review only on ORG_UNIT; CUSTOM_ORG_UNITS only (ruling 6602940790); one item per (kind, person, command, field) under `findForUpdate`; recipient = new head or null with a P3 line; the card is queued in the caller's transaction (`WecomGateway.send` javadoc "inside the caller's transaction").
- A-6 (ruling 6600968506): the envelope entity reaches the port; null -> default calendar plus one P3 line without personal data; no legal-entity column; the `WorkdayDeadlines` kernel port takes `@Nullable LegalEntityId`; `isWorkday` keeps its non-null check; f-1.md A-6 is marked ruled.
- Audit action name follows ruling 6602101721: `platform.identity.PermissionAdjustmentOpened` in must-audit-actions.yaml; the event holds ids and enums only (no names or permission codes); `auditDetail` follows row 33's format.
- Module boundaries: identity uses only `organization.workforce.api` (OrgDirectory, ids), `platform.integration.api` and the kernel port; no foreign table or internal class; registers updated (data-access-registry writers, system-actors, signatures, audit-detail implementations, asyncapi/identity.yaml, built.yaml, gaps.md, glossary).
- Secrets and personal data: none in the diff; log lines carry event and item ids only.
- No code file over 1000 lines (largest new main file 152 lines; the acceptance IT has 677).
- Tests run at head (temporary worktree): unit HandleEmployeeCorrectedEscalationTest 6, PermissionAdjustmentTest 1, WorkCalendarServiceTest 10, EventSequenceContinuityTest 16, OpenApiExportTest 4, all green; IT PermissionAdjustmentShapeIT + CorrectionUndoesSeparationReviewIT 29 run, 0 failed (`./mvnw -o verify -Dit.test=...`).

Break verification (temporary worktree under the scratchpad, deleted afterwards):

| Defence | How broken | Result |
|---|---|---|
| Relaxed CHECK: null before only for SENSITIVE_ROLE_REVIEW | dropped `kind = 'SENSITIVE_ROLE_REVIEW' AND` from V20261103130000 | red (PermissionAdjustmentShapeIT.aNullBeforeUnitIsOnlyForAReinstatementOfASensitiveRoleReview:120) |
| Transfer without a before department: ALERT P2 ERROR | logged at INFO instead | red (aTransferWithoutItsBeforeDepartmentIsAlertedNotRetriedAndGivesNoItem:253) |
| Scope-review dedupe (existsForTrigger) | removed the check | red (aReplayGivesOneScopeReviewAndOneCard, publication FAILED) |
| Sensitive-review dedupe (existsForTrigger) | removed the check | red (anUndoneSeparationGivesOnePersonItemForAHolderOfSensitiveRoles) |
| Scope review only for CUSTOM_ORG_UNITS | any scope kind accepted | red (aHolderOfOnlyNonScopedGrantsGetsNoScopeReview and others) |
| A-6 missing entity: one ALERT P3 ERROR | logged at INFO instead | red in the unit test (HandleEmployeeCorrectedEscalationTest.noEntityStillRaisesTheItem:159); the acceptance A-6(c) stays green (see suggestions) |
| Head without a WeCom id still yields the item (claimed in the javadoc at :108) | no break needed: a reproduction at head throws NPE | must-fix 2 |
