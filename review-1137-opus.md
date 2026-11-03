复审结论：必改
commit：1b632a60046b0d8cb70c8f1eeb6c90f2ba57e14a
复审者：opus-首轮-1137

patch-id：a9664131ca03d0ef0be98f29088e3c4348bb5777

Impact: platform.identity permission-adjustment listeners. Scope reviews raised by EmployeeTransferred and EmployeeCorrected (department changed, CUSTOM_ORG_UNITS grant) no longer address the moved person when that person heads the new department, and three ALERT P3 lines now carry the item id. Two caller layers checked: listener -> OpenAssignmentReviews.openScopeReviewFor -> assign/escalationDueAt. openScopeReviewFor has no other callers (git grep).

## Must-fix

1. Correctness / a test cannot prove the claim: the correction path of the self-head rule has no test that goes red.
   - Where: backend/src/main/java/com/forcitis/one/platform/identity/application/HandleEmployeeCorrected.java:53 (`review.openScopeReviewFor(user, event.employeeId(), ...)`).
   - Ruling 6604945364 says the rule is the "same for a correction treated as a transfer". The PR changes this call site for that reason, but no test covers it. Case 7b only drives EmployeeTransferred. Case 9 (`aCorrectionOfTheDepartmentFollowsTheSameRule`) uses a new department headed by someone else.
   - Evidence (break verification): in a temporary checkout at the head, I replaced `event.employeeId()` on that line with `new EmployeeId(UUID.randomUUID())`. Under that change, a correction into a department the corrected person heads makes that person their own recipient. `CorrectionUndoesSeparationReviewIT` still passed 26/26 (not red). `HandleEmployeeCorrectedEscalationTest` does not reach this argument.
   - Fix: add a test the implementer owns. It can be an identity-module IT or unit test outside the acceptance files, which must not be edited. In the test, a correction with ORG_UNIT into a department the corrected person heads, with a CUSTOM_ORG_UNITS grant, must give a SCOPE_REVIEW with a NULL recipient, no ASSIGNED delivery and one `ALERT P3 platform.identity ` line. Show that it goes red under the break above. Asking one-test for a case "9b" in the acceptance class also works.

## Suggestions

- OpenAssignmentReviews.java:118: for the self-head case the alert text says "has no head of the new department", but the department does have a head (the moved person). The operator is misled, though the ids are correct. Consider wording that covers both cases, e.g. "has no head of the new department other than the moved person". Case 7b only checks the prefix and the ids, so this is safe.
- OpenAssignmentReviews.java class Javadoc (lines 39-44): "addressed to the head of the new department" could add "other than the moved person". It is incomplete, not contradictory, because the `assign` Javadoc was updated.
- When an event has no legal entity and also no eligible head, two ALERT P3 lines are written for one item. Both carry the event id and the item id, which matches ruling 6605327331. Noted only.
- Ruling 6604945364 also mentions invariant 33 (an administrator who is the moved person does not see the item). I found no administrator listing for recipient-less items in main code at this head (git grep). It is presumably a later slice and outside this diff. Unverified.

## Checked and fine

- PR head equals the sha: `gh pr view 1137 --json headRefOid` = 1b632a60046b0d8cb70c8f1eeb6c90f2ba57e14a; `git cat-file -e` ok; diff taken with `git diff origin/main...1b632a600`.
- Acceptance files were not edited by the implementer: `git diff --quiet c588351b2 1b632a600 -- CorrectionUndoesSeparationReviewIT.java F1_730_ACCEPTANCE.md` shows them identical to the one-test branch head c588351b2. The implementer commit 1b632a600 touches only the 3 main files (`git show --stat`). No assertion was loosened: the acceptance diff only adds `.contains(itemId)` and the new case 7b.
- Ruling 6604945364, checked item by item against OpenAssignmentReviews.java:102-109. The head is filtered by `!candidate.equals(mover)`, and EmployeeId is a record (kernel/EmployeeId.java:7), so equals is by value. The recipient is NULL, `assign` returns before `wecom.send`, so there is no ASSIGNED delivery, and one ALERT P3 line is written. SENSITIVE_ROLE_REVIEW (`openFor`) is unchanged. Both listeners pass `event.employeeId()`, the same id used for `accountOf`.
- Ruling 6605327331, checked line by line (`git grep "ALERT P" platform/identity` at the head). no-head/self-head line: event id plus item id. no-WeCom line: event id plus item id. no-legal-entity line: event id plus item id (the id is now generated before `escalationDueAt`; argument order was already id first in openScopeReviewFor, so ids from the fixed IdGenerator are unchanged). refused-transfer P2 line: event id plus event type, no item exists, nothing personal. There are no names, employee numbers or role names in any line.
- Coding rules §B: no SQL, no outside calls added; the logs hold no RESTRICTED data (§B10); no swallowed exceptions or new null checks; the `@Nullable` head is pre-existing and needed.
- Module boundaries: only `OrgDirectory.findOrgUnitHead` (organization.workforce.api), as before.
- No migration or table change; no deletions; file_lines guard: `python3 scripts/guards/file_lines.py` -> 0 problems.
- Tests at the head in a temporary checkout: `CorrectionUndoesSeparationReviewIT` 26/26, `HandleEmployeeCorrectedEscalationTest` 8/8.

Break verification (each in a temporary checkout of the head, then `CorrectionUndoesSeparationReviewIT`):

| Defence | How broken | Result |
|---|---|---|
| mover is not their own recipient (transfer) | removed `.filter(candidate -> !candidate.equals(mover))` | red: 7b `aMovedPersonWhoHeadsTheNewDepartmentIsTreatedLikeNoHead` (recipient was set) |
| no-head / self-head ALERT carries the item id | dropped the item id argument and placeholder | red: case 7 and 7b |
| no-legal-entity ALERT carries the item id | dropped the item id argument and placeholder | red: A-6(c) `anUnresolvableEntityStillGivesTheItemWithTheDefaultCalendarAndOneP3Alert` |
| mover is not their own recipient (correction) | passed a random EmployeeId instead of `event.employeeId()` in HandleEmployeeCorrected | **not red** (26/26 green), must-fix 1 |
