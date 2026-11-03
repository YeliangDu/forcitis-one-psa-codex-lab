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
