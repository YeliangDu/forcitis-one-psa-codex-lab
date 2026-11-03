复审结论：必改
commit：1b632a60046b0d8cb70c8f1eeb6c90f2ba57e14a
复审者：opus-终审-1137adv

patch-id：a9664131ca03d0ef0be98f29088e3c4348bb5777

Impact: platform.identity only. `OpenAssignmentReviews.openScopeReviewFor` gains a mover argument and drops a head equal to the mover; its two callers (HandleEmployeeTransferred, HandleEmployeeCorrected) pass `event.employeeId()`; two ALERT P3 lines gain the item id. Caller layers proved: listener -> OpenAssignmentReviews -> OrgDirectory.findOrgUnitHead (1); no other caller of `openScopeReviewFor` (`git grep openScopeReviewFor 1b632a600`).

Input checks: `git cat-file -e 1b632a600^{commit}` ok; PR #1137 head = 1b632a60046b0d8cb70c8f1eeb6c90f2ba57e14a (`gh pr view 1137 --json headRefOid`); diff from `git diff origin/main...1b632a600`. The implementer commit 1b632a600 touches only the three main files (`git show --stat 1b632a600`); the test and F1 changes come from the test-engineer branch (commits 7423a20c5..c588351b2, `Acceptance-Test-Author: test-engineer`) merged in 74d54d763. The implementer did not change acceptance tests.

## 必改

1. **The correction path of the new rule (the mover heading the new department counts as no head) has no test that goes red when it is broken.**
   - Where: `backend/src/main/java/com/forcitis/one/platform/identity/application/HandleEmployeeCorrected.java:53`, the new argument `event.employeeId()`. The class Javadoc (lines 14-15) and the card both say a department correction is handled like a transfer, so the 7b rule is claimed for this path too.
   - Break (temporary worktree of the head, under the scratchpad): replaced `event.employeeId()` in that call with `new com.forcitis.one.kernel.EmployeeId(java.util.UUID.randomUUID())`, ran `CorrectionUndoesSeparationReviewIT` (26 cases): **all green, exit 0.** Case 7b only publishes `EmployeeTransferred`; case 9 (`aCorrectionOfTheDepartmentFollowsTheSameRule`) uses a head who is a different person.
   - Behaviour at the head is right: a temporary probe case (same setup as 7b, but `corrected(employee, cmd, UNIT, unit, headedUnit(employee), null, null)`, asserting one SCOPE_REVIEW, NULL recipient, no ASSIGNED card) passes at the head and goes red with the break above (`probeCorrectionSelfHead:742`). So this is a missing line of defence, not a wrong behaviour today.
   - Fix: add a test the implementer owns that a department correction of a scoped holder who heads the new department gives a NULL-recipient scope review with no card (for example a unit test of `HandleEmployeeCorrected` with a fake `OpenAssignmentReviews` collaborator checking the mover passed is the event's employee, or an implementer IT next to the acceptance class); or ask test-engineer for an acceptance case 9b. Do not edit the acceptance class yourself.

## Suggestions

- `OpenAssignmentReviews.java:39-41` class Javadoc still says the scope review is "addressed to the head of the new department" without the mover exception; the `assign` Javadoc (lines 112-115) has it. Adding "(none when the mover heads it)" keeps the class summary true.
- `HandleEmployeeCorrected.java:53` and `HandleEmployeeTransferred.java:46` call lines are 170 characters; no guard enforces a width (`git grep -i LineLength` finds none), cosmetic only.
- Two scope reviews for one event would each log their own ALERT line when the envelope has no legal entity and the head is missing or is the mover (two lines, both with event id and item id). This matches "exactly one" only in the tested setups that carry a legal entity; not required by the card.

## Checked and fine

- Head of the IT class: `./mvnw failsafe:integration-test -Dit.test=CorrectionUndoesSeparationReviewIT` at 1b632a600: Tests run 26, failures 0.
- `EmployeeId` is a record (`backend/src/main/java/com/forcitis/one/kernel/EmployeeId.java:7`), so `candidate.equals(mover)` compares by value.
- ALERT lines of the two listeners (`git grep -n ALERT 1b632a600 -- backend/src/main/java/com/forcitis/one/platform/identity`): OpenAssignmentReviews.java:118 (event id + item id), :124 (event id + item id, unchanged), :135 (event id + item id); HandleEmployeeTransferred.java:36 (event id + event type, no item exists). None carries an employee code, name or employee id; the item id is a UUID.
- Item id in the ALERT line equals the stored row: the id is created once (`OpenAssignmentReviews.java:84,103`) and used both for the row and the log line; the tests compare with `permission_adjustments.permission_adjustment_id`.
- No secrets, no migration, no schema change, no file over 1000 lines (OpenAssignmentReviews.java 140 lines).
- No defensive fallback added: the head filter maps the mover to the same `null` branch that already alerts.

Break verification (temporary worktree of the head, removed after; each break restored before the next):

| defence | how broken | result |
|---|---|---|
| mover heading the new dept counts as no head (transfer) | removed `.filter(candidate -> !candidate.equals(mover))` | red: 7b `:660` "the mover is not their own reviewer" |
| no-head ALERT carries item id | dropped `item.id().value()` from the :118 line | red: 7 `:633`, 7b `:668` |
| no-legal-entity ALERT carries item id | dropped `item.value()` from the :135 line | red: A-6(c) `:386` |
| mover heading the new dept counts as no head (correction) | passed a random EmployeeId instead of `event.employeeId()` in HandleEmployeeCorrected | **not red** (26/26 green) -> 必改 1 |
