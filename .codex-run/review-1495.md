复审结论：必改
commit：a6fe8b496cd00dee597748ba2a70b2cae0acfb88
复审者：opus-首轮-opus-first-1495

patch-id：e8887b4535931c94dea394a156b5cdf942837f9f

Impact: one new read endpoint GET /api/v1/organization/primary-assignments?employeeId= (organization.workforce web -> application GetPrimaryAssignmentHistory -> GetEmployee scope gate + denied audit -> JdbcEmployeeRosterQueries) and its one consumer, the employee detail page section in frontend workforce; 3 caller layers proved (controller -> use case -> GetEmployee/queries), no other callers (new code).

## Must-fix

1. backend/architecture/data-scope-read-paths.txt (missing line) — correctness / line of defence (#730 I-10 register). The new GET handler is not in the data-scope read-path register, so `DataScopeReadPathsIT` is red on this head. The light PR CI does not run it; the merge queue will.
   Evidence (temp checkout of a6fe8b496, `./mvnw -B -o verify -Dit.test=DataScopeReadPathsIT,...`):
   `GET /api/v1/organization/primary-assignments is a GET handler that is not in the register. Fix: add `GET /api/v1/organization/primary-assignments | SCOPED, SELF or NOT_SCOPE_BOUND | reason` to backend/architecture/data-scope-read-paths.txt`
   Fix: add one SCOPED line next to the two employee lines (18-19), e.g. `GET /api/v1/organization/primary-assignments | SCOPED | one employee's primary-post history; readable exactly when the employee detail is (scope from current posts), out of scope answers 404 and writes organization.workforce.employee.denied (#1495)`; rerun DataScopeReadPathsIT.

## Suggestions

- JdbcEmployeeRosterQueries.primaryAssignmentHistory lists every primary *version*, not every *post*: a command that only changes reports-to (CommandEffects.REPORTS_TO) splits the primary version, so the section will show the same unit + position twice in a row with adjacent ranges. Consider merging adjacent versions with equal org_unit_id/position_id (or say in the card/PR that a row is a version). Not blocking: each row is a true range.
- EmployeeRosterQueries.java:21 doc says "Every primary assignment version"; the query drops voided versions and empty same-day ranges. Say "every version in force" so the comment matches the SQL.
- PrimaryAssignmentView.of is a 1:1 copy of PrimaryAssignmentVersion (rule B.13 single-caller pass-through). It exists to keep web off the domain type (commit a6fe8b496 message); acceptable, but returning the view straight from the query layer would remove one mapping.
- primary-assignment-history-section.tsx uses a raw <table> because DataTable (@forcitis/ui) cannot take an accessible name. Fine under CS-4.7 (no antd primitive), but a `label`/`aria-labelledby` prop on DataTable would keep tables consistent; worth a follow-up, not this card.
- Data scope semantic worth a line in the PR: a reader in scope of the person's current unit also sees the names of old units outside their scope (IT asserts this deliberately). It matches the employee-detail precedent; lead/user may want to confirm.

## Checked and fine

- Head: `git cat-file -e` ok; `gh pr view 1500` headRefOid = a6fe8b496cd00dee597748ba2a70b2cae0acfb88; diff taken with `git diff origin/main...a6fe8b496`.
- Scope gate: GetPrimaryAssignmentHistory.java:21 reads history only inside `employee.execute(id).map(...)`; GetEmployee.java:27-34 applies visibility.forDetail() and writes the denied audit; out of scope and unknown id both 404 with the same errorCode (IT scopeFollowsTheCurrentPostNotTheOldOnes). Permission: @PreAuthorize EMPLOYEE_READ, same as EmployeeController.java:48.
- SQL: named params only, own schema (organization_workforce), filters PRIMARY, superseded_at is null, non-empty ranges; stable order effective_from desc, revision desc; nullable columns mapped to @Nullable (B.6, B.9).
- G4 (api methods <= 12): no change to any `api` package; new types are in application/domain/web.
- Tests run on the temp checkout: PrimaryAssignmentHistoryHttpIT 4/4, NamingSurfaceTest 13/13, GlossaryTest 5/5, ArchitectureRulesTest 38/38, OpenApiExportTest 4/4, DataScopeReadPathsGuardTest 9/9, ControllerResponseGuardTest 3/3, ErrorCodeGlossaryTest 2/2; DataScopeReadPathsIT 0/1 (must-fix 1). Frontend after generate:api: `vitest run src/modules/workforce` 24/24, `tsc --noEmit` clean, eslint clean, `pnpm check:copy` pass.
- OpenApiExportTest.java:111 change adds two fields to OWN_ID_FIELDS (registration of uuid id fields), not a loosened assertion. No acceptance test touched; no migration; no deletions.
- Frontend: errors via describeFailure; loading / error / empty states explicit; section failure does not hide the page (vitest 403 case); heading + table named by aria-labelledby (found by role+name in vitest); no `!`; dates are LocalDate strings, not instants.
- No secrets, no row-level data; fixtures use synthetic codes (PH-A...). All files well under 1000 lines.

Break verification (temp copy, PrimaryAssignmentHistoryHttpIT):
| defence | how broken | result |
|---|---|---|
| scope gate via GetEmployee | GetPrimaryAssignmentHistory returns the query result directly, no GetEmployee call | red (scopeFollowsTheCurrentPostNotTheOldOnes) |
| exclude voided versions and empty same-day ranges | removed `superseded_at is null` and the `effective_to_date > effective_from_date` clause | red (theHistoryListsPrimaryPostsNewestFirst, scopeFollowsTheCurrentPostNotTheOldOnes) |
| data-scope read-path register | not added by the PR | already red on head (must-fix 1) |
