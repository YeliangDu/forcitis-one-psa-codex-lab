复审结论：必改
commit：4bda252051c7d8b21a141cb9f7db26b47c7f17c7
复审者：opus-首轮-730s3

patch-id：a9a5049ae77bcd4d1b5c6e2596a7c11a7d295f59

## Impact
This affects three groups. The first is callers of the 4 new endpoints under /api/v1/platform/permission-adjustments. The second is every module that returns a 404 through ProblemDetails.of(ErrorCode, ErrorKind, …): this covers GlobalExceptionHandler:45 for refusals and IdempotencyFilter:196, and the `instance` field of those 404s changes. The third is the org_data_admin and security_auditor declarations: the revisions go up and new codes are added. I proved 3 caller layers: controller -> use case -> PermissionAdjustmentAccess/Reader -> repository SQL. I also checked ProblemDetails for readers of `instance` in backend tests, the frontend and docs, and found none.

## Must-fix

1. **The uphold reason never reaches the audit trail (it should go in `change_reason`). This contradicts the card's audit content table.**
   - Where: backend/src/main/java/com/forcitis/one/platform/identity/events/PermissionAdjustmentUpheld.java:14-15. The record implements `DomainEvent, AuditDetail` only.
   - Evidence:
     - `docs/architecture/implementation-cards/f-1.md:270`, the audit row for `upheld`: "条目 ID、业务负责人员工 ID、有效至、实际钟点；理由进 `change_reason` 列，不进 detail".
     - `AuditDomainEvents` fills `change_reason` only for an event that implements `kernel.event.Reasoned`. It reads `String reason = event instanceof Reasoned reasoned && ... reasoned.reason().value()`.
     - `PermissionAdjustmentUpheld` does not implement `Reasoned` and carries no reason. Nothing else in the diff writes an audit entry for an uphold, so the change_reason of every `platform.identity.PermissionAdjustmentUpheld` entry is null.
     - The acceptance test `upholdingIsAudited` does not assert changeReason, so the gap is not caught.
   - Why it matters: the reason for keeping a sensitive authorization is exactly what an auditor reviews. After this change it lives only in the mutable transaction row. Rulings 6602101721 and ADR-0044:99 changed only the action names and did not lift this row.
   - Fix:
     - Give `PermissionAdjustmentUpheld` a `Reason reason` component and implement `Reasoned`. The existing pattern is `organization/workforce/events/LegalEntityAttributionCorrected.java:15`.
     - Fill it from `UpholdReason` in `PermissionAdjustment.uphold`.
     - Add the payload field to identity.yaml and asyncapi.yaml.
     - Add a unit or IT assertion that the audit entry's changeReason equals the reason given.

2. **The use-case check "only a sensitive-role review takes a business owner" has no test (break verification is not red).**
   - Where: backend/src/main/java/com/forcitis/one/platform/identity/application/UpholdPermissionAdjustment.java:58-61.
   - How it was broken:
     - I replaced `if (owner != null)` with `if (false)`. All 18 cases of `PermissionAdjustmentActionApiIT` stayed green.
     - Without the check, a scope review upheld with a `businessOwnerEmployeeId` reaches `PermissionAdjustment.uphold` (PermissionAdjustment.java, `sensitive != (owner != null)`). That throws an `IllegalArgumentException`.
     - GlobalExceptionHandler has no mapping for `IllegalArgumentException` (I grepped it: no match). The caller would get a 500 instead of the intended 400.
     - Nothing is written, so this is not fail-open. But it is a claimed line of defence with no test. Under PROMPT.md, not red means must-fix.
   - Fix: add a test that upholds a SCOPE_REVIEW with a businessOwnerEmployeeId, expects 400, and checks the item is untouched. It can be a unit test of the use case or a non-acceptance IT; the implementer must not change the acceptance directory. If the domain-level throw should be the only guard, remove the duplication instead.

## Suggestions (do not block)
- **Can the two copies of the read rule drift?**
  - Today they are equivalent. `canRead` is `employee.equals(recipient) || (holdsRead && !isSubject)`. The SQL in JdbcPermissionAdjustmentRepository is `recipient_employee_id = :employee or (:holdsRead and user_id is distinct from :user)`.
  - NULL handling matches. A null user_id gives "distinct = true", which matches Java's `equals(null) = false`. A null recipient gives NULL OR x, which matches false || x.
  - Nothing ties the two together except the acceptance cases: break verification M2/M3 against M4 each went red on a different test. A future edit to one side could still slip through.
  - Suggestion: add a parameterised IT. Over a matrix of items and readers, assert `findFor(reader, null, null, null, N)` equals `findAll().filter(reader::canRead)`.
- **`PermissionAdjustment.restore` loads only part of the row.**
  - It leaves out uphold_reason, adjusted_by, the adjusted reference, expired_at and closed_at.
  - Meanwhile `JdbcPermissionAdjustmentRepository.update` overwrites all stage columns. It also sets `upheld_at = now` whenever upheldBy is non-null.
  - This is correct today, because only OPEN items reach update, and the CHECK guarantees OPEN rows have none of these columns set. But if the S2 expiry job calls `update` on a restored UPHELD item, it would null uphold_reason and reset upheld_at.
  - Suggestion: restore every column, or write only the columns of the transition. Also make the docstring "Writes the state a command changed" match that.
- **The fixed `instance` urn applies to every 404 in the system.**
  - The value is `urn:forcitis:error:not-found`. It is safe: I grepped backend/src/test, frontend/packages and frontend/apps and found no reader of `instance`.
  - But the code comment says the reason is to "not echo it". The real reason is that two different ids must give byte-identical bodies. Suggestion: reword the comment to say so.
- `ListPermissionAdjustments.LIST` and the controller's `"permission-adjustments"` literal are the same list name written twice. Use the constant in both places.
- `FindAuditEntry` is a single-caller pass-through (coding-core B13). It is acceptable as the api-to-domain adapter of the audit module, but it is worth a look.
- An `auditEntryId` that has since been archived out of `platform_audit.audit_entries` would be refused with 400. This is unlikely within retention; note it for the archive card.

## Checked and fine (with evidence)
- **Commit and diff:**
  - `git cat-file -e 4bda2520…^{commit}` succeeded.
  - The diff was taken with `git diff origin/main...4bda25205`. The merge-base is 8b3b3f508.
- **Acceptance directory:**
  - Feature commit 3bfd87b8c touches no file under `acceptance/`. I checked with `git show --name-only`.
  - 5c8d134bf (SystemRoleDeclarationIT) carries `Acceptance-Ruling: RULING-LEAD-20261103-16`.
  - 6b861819b and 3de3cf88b carry `Acceptance-Test-Author: test-engineer`.
- **Guard count:** AuditWritePointsGuardTest goes from 51 to 53, with two register lines added. That is a count update, not a loosened assertion.
- **Rulings 6602368110 and invariants 19, 22 and 33, checked item by item:**
  - List is 200 and empty for an empty scope. `ListPermissionAdjustments` and `PermissionAdjustmentEmptyScopeIT` are green.
  - A detail out of scope gives 404 plus `platform.identity.access.denied` (PermissionAdjustmentAccess.readable). A missing id gives 404 without the audit.
  - A command gives 403 OWN on the caller's own item, then 404 out of scope, then 403 HANDLER_NOT_ALLOWED. This is PermissionAdjustmentAccess.handleable.
  - An addressed SCOPE_REVIEW/ASSIGNMENT_CHANGE item is handled only by its recipient; every other item needs `:update`. This is PermissionAdjustmentReader.canHandle, and it matches f-1.md:136 and :343-345.
- **Owner rules and quarter expiry:**
  - The business owner must be employed (OrgDirectory) and must not be the subject (the account is mapped via AccountEmployees). The owner may be the handler. This matches f-1.md:31, ADR-0044:54 and the column comment.
  - UpholdExpiry computes the first month of the next quarter as `(m-1)/3*3+4`, with a December wrap. The four boundaries are green in UpholdExpiryTest and in theExpiryIsTheNextQuarterStartInShanghai.
- **Mark-adjusted takes exactly one reference:**
  - The AdjustmentReference constructor rejects both and neither with 400.
  - The DB has `num_nonnulls(...) <= 1`.
  - An unknown reference gives 400 before any write. MarkPermissionAdjustmentAdjusted checks both kinds of reference.
- **Reason length:** it is counted in code points, up to 500. CJK 500 is accepted and 501 is refused (green).
- **Audit action names:** they follow the event type, plus `permission-adjustment.viewed`, as ruling 6602101721 requires. Registered in must-audit-actions.yaml; MustAuditActionsTest is green.
- **Module boundaries:** identity uses only `platform.audit.api` (AuditEntryLookup, AuditLog) and `organization.workforce.api` (OrgDirectory). It does not touch audit tables directly. `audit_entries` is read only inside platform.audit's JdbcAuditEntryQueries.
- **Schema:** there are no migration changes in this diff. The columns used match V20261103120000, and the CHECK constraints are respected.
- **File size:** no file is over 1000 lines. The largest changed main file is PermissionAdjustment.java at 281 lines.
- **Secrets and personal data:** none. The fixtures use synthetic names, and the audit details carry only surrogate ids.
- **Tests run in a temporary worktree under scratchpad (since removed):**
  - Unit tests, all green: UpholdExpiryTest, PermissionAdjustmentTest, SystemRoleDeclarationTest, AuditWritePointsGuardTest, GlossaryTest, MustAuditActionsTest, AuditDetailGuardTest.
  - ITs, all green: PermissionAdjustmentReadApiIT (8), PermissionAdjustmentActionApiIT (18), PermissionAdjustmentEmptyScopeIT (1), SystemRoleDeclarationIT (7).

### Break verification (temporary worktree, one mutation at a time)
| Defence | How broken | Red / not red |
|---|---|---|
| 404 `instance` not echoed (ProblemDetails) | NOT_FOUND branch made `if (false)` | red: callersOutOfScopeSeeAnEmptyListAndNotFoundAlike |
| SQL leaves out own items | `(:holdsRead or user_id = :user)` | red: aListLeavesOutTheCallersOwnItems, aCodeHolderWhoseOnlyItemIsItsOwnSeesAnEmptyList |
| SQL recipient branch | `where (false or …` | red: theNewHeadSeesOnlyTheItemsAddressedToThem |
| canRead leaves out subject | `holdsRead` only | red: aListLeavesOutTheCallersOwnItems |
| canHandle addressed to recipient only | `holdsUpdate \|\| recipient` | red: onlyTheRecipientUpholdsATransferScopeReview |
| canHandle needs :update | `: true` | red: 3 cases (auditor 403, inactive-unit) |
| handleable own item gives 403 | isSubject check made `if (false)` | red: onlyAnotherAdministratorMayUpholdASensitiveReview, markAdjustedIsRefusedOutOfScope… |
| access.denied audit | recordIndependently removed | red: callersOutOfScopeSeeAnEmptyListAndNotFoundAlike |
| viewed audit | recordIndependently removed | red: readingAnItemIsAuditedAsViewed |
| reason counted in characters | UTF-8 byte length | red: theReasonLimitCountsCharactersNotBytes |
| owner must be employed | check made `false` | red: invalidUpholdRequestsAreRefusedAndChangeNothing |
| owner is not the subject | check made `false` | red: invalidUpholdRequestsAreRefusedAndChangeNothing |
| only sensitive review takes an owner (use case) | `if (false)` | **not red** (see must-fix 2) |
| exactly one reference | only "neither" refused | red: markAdjustedNeedsExactlyOneReference |
| audit entry exists | check made `false` | red: anUnknownReferenceIsABadRequest |
| authorization request exists | check made `false` | red: anUnknownReferenceIsABadRequest |
| quarter expiry | `+4` changed to `+3` | red: UpholdExpiryTest (2), theExpiryIsTheNextQuarterStartInShanghai |
| domain not-about-handler | `if (false)` | red: PermissionAdjustmentTest.anItemTakesOneCommandAndNeverOneAboutTheHandler |
| domain OPEN only | `if (false)` | red: anUpheldItemTakesNoFurtherCommand, and the unit test |
