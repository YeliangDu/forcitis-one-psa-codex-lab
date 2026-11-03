# #730 S4 acceptance: fail-closed read paths (A1 to A5)

Register: `backend/architecture/data-scope-read-paths.txt`. Both classes take their adapters per register line and fail when a SCOPED or SELF line has none (or an adapter has no line).

| Done-when (issue #730 comment 6597359528) | Test |
|---|---|
| A1 every SCOPED list gives unit B's rows only, through every filter and cursor page | `DataScopeReadBehaviourIT.listsGiveTheRowsOfUnitBOnly` |
| A2 every SCOPED detail of unit A is 404 with the body of a missing id | `DataScopeReadBehaviourIT.detailsOutOfScopeAreNotFound` |
| A3 empty scope: zero rows, 404 | `DataScopeReadBehaviourIT.anEmptyScopeSeesNothing` |
| A4 unresolvable scope: 403 on lists, 404 on details | `DataScopeReadBehaviourIT.anUnresolvableScopeIsDenied` |
| A5 every SELF path: a second account's data never returned, its id is 404 | `acceptance/platform/approval/SelfReadBehaviourIT` (4 behaviours + register check) |
| register completeness | `everyScopedLineHasAnAdapter`, `everySelfLineHasAnAdapter` |

Not covered, and why:
- A4 "a role version whose scope kind the calculator does not know": `ck_role_versions_access_scope_kind` forbids storing one, so it cannot be seeded; the calculator's switch is the implementer's guard (G1-G3).
- A4 "custom org units that cannot be read": the seam that reads them does not exist yet; only the unreadable own unit (OrgDirectory fails for the caller) is driven.
- `GET /api/v1/platform/files/{fileId}` (SELF): the file service is off in every test context (needs scanner and bucket), so no upload over HTTP; `GetUploadStatusTest` proves another uploader's file is not found.
- G2 (a seeded test-only SCOPED endpoint that ignores scope): the harness takes plain adapters, but no such endpoint is registered here; the implementer adds it.
- `GET /api/v1/platform/permission-adjustments` and its detail (SCOPED by relation, not by unit): the harness maps "B" to the items addressed to scopeB and "A" to every other item; scopeB holds no code, `everything` holds org_data_admin. Seeded straight into the table. A1-A3 are green on main; A4 is red (200 with an empty list where the ruling says 403) until the S4 implementation.

Rulings on the doubts (one-design, #730 comment 6606893624):
- No employee or no assignment on an own-unit scope is an empty scope (A3): lists 200 with zero rows, details 404. The resolver answered "nothing" is A3; the resolver failed or met an unknown kind is A4. Today's 403 is an accidental NPE in `AccessScopeCalculator.orgUnitReach`.
- A4 details are 404 by intent; lists stay 403 `PLATFORM_IDENTITY_ACCESS_SCOPE_UNAVAILABLE`.
- `unknownDepartmentSeesNothing` and `scopeFailureRefuses` change in the implementer's PR, not in this one.
