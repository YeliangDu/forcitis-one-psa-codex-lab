复审结论：通过
commit：a908c2cde3d2f44170be5acd7da4af8cd1c34810
复审者：opus-终审-opus-final-1495

patch-id：3760181eca4fdec74bfec689cd6d59f52c57120d

Adversarial final round (data-scope card #1495, PR #1500, base origin/main merge-base 2da74367b). PR head checked equal to the sha (`gh pr view 1500` headRefOid). Every attack below was run in a throwaway detached worktree under the scratchpad with an extra IT class (deleted afterwards). Test data used made-up "Synthetic" names only.

## Impact

Callers of the new `GET /api/v1/organization/primary-assignments?employeeId=` (holders of organization.workforce:employee:read) and the web employee detail page. Proved 3 layers: HTTP (MockMvc, real Postgres Testcontainer) -> GetPrimaryAssignmentHistory -> GetEmployee scope gate + JdbcEmployeeRosterQueries.primaryAssignmentHistory; frontend section via vitest.

## Must-fix

None. No attack broke the scope gate, the permission check or the denied-audit behaviour.

## Suggestions (do not block)

1. A future-dated open version is shown with 失效日「至今」. Proved on the backend: employee with PH-A 2023-01-01..2099-01-01 and PH-B from 2099-01-01 (open) -> aReader gets `{"orgUnitName":"Synthetic unit PH-B","effectiveFrom":"2099-01-01","effectiveTo":null,"current":false}` as the first item. `primary-assignment-history-section.tsx:40` renders `item.effectiveTo ?? '至今'`, so a scheduled post that has not started reads "until today". Consider a different label when `effectiveFrom` is after today, or a 「待生效」 badge. (Glossary term says the history includes posts "将要担任的", so listing them is intended; only the wording is off.)
2. Employees still read from the legacy snapshot (no versions) get `200 {"items":[]}` while the detail above shows a PRIMARY assignment (proved: allReader on `admin1` -> history `{"items":[]}`, detail has one PRIMARY in PH-X). The page then shows a current primary post and 「暂无主岗历史」 together. Transitional (migration only, per the CURRENT comment in JdbcEmployeeRosterQueries), but worth a note or a fallback row.
3. The card asks for explicit loading and empty states; vitest covers empty, error and data, not loading. A one-line test for `LoadingState` while the history query is pending would close it.
4. The section uses a raw `<table>` (the only one under `frontend/apps/*/src`), not `DataTable`; not a CS-4.7 breach (no antd primitive), but it will look different from the assignments table above it. Optional.
5. For the user/lead to be aware of, not a defect: the history shows names and ids of the units of old posts even when those units are outside the reader's scope (proved: aReader, scoped to PH-A only, sees PH-C and PH-B rows for `mover`; xReader, in scope only via mover's CONCURRENT post in PH-X, sees the full primary history PH-A/PH-C/PH-B). This is what the registered read-path line says ("readable exactly when that employee's detail is") and matches the detail endpoint, which already lists all current assignments regardless of scope; `GET /org-units/{id}` itself is SCOPED, so this is a deliberate widening of what unit names a reader sees.

## Checked and fine (with evidence)

Attack runs (extra IT `PrimaryAssignmentHistoryAttackIT`, `./mvnw verify -Dit.test=...`, exit 0, output lines prefixed ATTACK):
- Reader whose scope covers only the unit the person left (bReader on mover): 404, errorCode ORGANIZATION_WORKFORCE_EMPLOYEE_NOT_FOUND, denied audit written (PR IT asserts +2 audits).
- Empty-scope reader (role with an unknown unit): 404, same errorCode as an unknown id (PR IT).
- No employee:read: 403; anonymous: 401.
- Separated employee (EMPLOYED 2023-01-01..2025-09-01, SEPARATED from 2025-09-01, posts PH-C then PH-B ending on the separation day): bReader (last unit) gets 200 with both rows, none current, matching detail 200 with PH-B; aReader 404; reader of the snapshot unit PH-X 404. Same rule as detail (posts_day = separation day - 1).
- Future-dated version: bReader (unit of the future post only) gets history 404 and detail 404; the future unit does not put the person in scope.
- Malformed input with an empty-scope reader: missing param, `employeeId=`, `employeeId=abc`, two employeeId values, nil UUID -> all 400 PLATFORM_WEB_REQUEST_INVALID; denied audits added = 0.
- Unknown id: 404 and no audit (PR IT `withoutThePermissionOrWithoutThePerson`).
- Duplicate rows from the name joins: impossible, `ex_org_unit_versions_overlap` / `ex_position_versions_overlap` are exclusion constraints on non-superseded versions (V20261102180000 lines 32, 179).
- Unit/position name is as of the version's start day (docstring on PrimaryAssignmentVersion matches the SQL).

Break verification (each in the scratch copy, PR IT `PrimaryAssignmentHistoryHttpIT` re-run, then reverted):

| Defence | How broken | Result |
|---|---|---|
| Scope gate via GetEmployee | `GetPrimaryAssignmentHistory.execute` calls `employee.execute(id)` but returns history unconditionally | red (scopeFollows...:185, withoutThePermission...:198) |
| `@PreAuthorize(EMPLOYEE_READ)` | annotation removed | red (withoutThePermission...:196) |
| Voided versions excluded | dropped `and av.superseded_at is null` | red (2 tests) |
| Primary only | dropped `av.assignment_kind = 'PRIMARY'` | red (2 tests) |
| Empty same-day ranges excluded | range predicate replaced by `true` | red (2 tests) |
| Newest first | `order by ... asc` | red (theHistoryLists...:168) |
| UI current marker | badge replaced by '—' | red (vitest) |
| UI empty state | empty branch removed | red (vitest) |
| UI error stays in the section | error branch throws instead | red (vitest) |
| UI accessible name | `aria-labelledby` removed from table | red (vitest) |

Other checks:
- PR IT: 4/4 pass at the sha; workforce vitest: 3 files, 24 tests pass (after `pnpm install --offline` and `scripts/codegen/generate_api.sh -o` in the scratch copy).
- No secrets, no row-level data; fixtures are synthetic. No acceptance test or assertion changed; OpenApiExportTest only gains two id fields in its allow list.
- data-scope-read-paths.txt entry added as SCOPED, wording matches behaviour.
- coding-core §B: JdbcClient with named params, explicit columns; no swallowed exceptions; files far under 400 lines. coding-core-frontend: failure shown through `describeFailure`; no `!`; no instants formatted (LocalDate strings only).
- No deletions in the diff.
