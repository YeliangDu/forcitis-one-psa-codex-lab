复审结论：必改
commit：577059965e512d16b8696e4da27f09f6a5858be4
复审者：sonnet-首轮-sonnet-first-1438

patch-id：28ce11b5663155bceabdc30d216ea3ce3174fd62

## Impact
Schema and skeleton for organization.workforce only (3 new tables, 3 triggers, 1 new unique constraint on personnel_commands, repositories insert/read only); no application caller exists yet (WC2/WC3 will be the first). Proved 1 layer (tests -> repositories -> tables).

## Must-fix
All four are lines of defence claimed in the migration (and in the registry `condition` text) whose break-verification stayed green, so no test proves them. Fix = add one test each in `ContractSeparationTablesIntegrationTest`; no migration change needed.

1. Migration line 350-354 (`SEPARATING -> COMPLETED` only when the linked SEPARATE command is EFFECTIVE): the check replaced by `IF false` -> all 11 tests still pass. No test drives SEPARATING -> COMPLETED at all. Add: complete with the command PENDING is refused (message "completes only when"), with it EFFECTIVE it succeeds.
2. Migration line 204-205 (`uk_separation_requests_open_per_relationship`, open = REQUESTED, APPROVED, SEPARATING): predicate reduced to `status IN ('REQUESTED')` -> green. The only test checks REQUESTED vs REQUESTED. Add: a second request is refused while the first is APPROVED and while SEPARATING (and COMPLETED/REJECTED lets a new one in).
3. Migration line 316-318 (separation requests) and 258-260 (contracts), "version must advance by one": check replaced by `IF false` -> green. Every test passes version+1, none passes a wrong version. Add: an update with an unchanged version, and one with +2, is refused on both tables.
4. Migration line 252-257 (contracts are inserted as DRAFT only): replaced by `IF false` -> green. The test helper `contract("SIGNED", ...)` appears to go through the state machine, nothing inserts a non-DRAFT row directly. Add: direct insert with status APPROVED/SIGNED is refused.

## Suggestions
- `tg_separation_bases_guard` fires on UPDATE/DELETE only; INSERT is open to any role holding INSERT, while the table comment, registry (`MIGRATION_ONLY`) and the function message say rows change by migration only. Consider adding INSERT to the trigger (owner-only) or soften the wording. Not a correctness break while the app role has no INSERT grant.
- Contracts trigger on INSERT does not check `version = 0` (DEFAULT 0 only); a row inserted at version 5 would be accepted.
- Test message matchers "dismissal", "system", "last_working_date" are loose; the three linking cases (wrong day, not SEPARATE, withdrawn) share one message and cannot tell which clause fired. Split the clauses or assert separately-worded messages.
- Migration columns `contract_start_date`/`contract_end_date` alignment is off by a few spaces in the DDL (cosmetic).

## Checked and fine
- Head/PR: `gh pr view 1490` headRefOid = 577059965e51...; commit exists; base origin/main.
- Tests (temporary copy via git archive, scratchpad rv1438): ContractSeparationTablesIntegrationTest 11/11, ContractSeparationRepositoriesIntegrationTest 3/3, ContractStatusMachineTest 1/1, SeparationRequestStatusMachineTest 1/1 pass.
- Migration is a new file (V20261105200000), nothing merged edited or deleted; tables UUID primary keys; separation_bases keyed by named unique constraint; FKs ON DELETE RESTRICT; COMMENTs on every table and column present; registry and classification entries cover every new column (read diff).
- No secrets, no row-level data, no production write, no assertion loosened in the diff; test data uses made-up ids.
- Comments vs code: javadoc of `ContractStatus`, `SeparationRequestStatus`, records matches trigger edges (DRAFT<->IN_APPROVAL, SEPARATING->APPROVED, etc.); no stale paths found.
- Repositories use JdbcClient with named params, explicit columns, injected Clock, OffsetDateTime; domain has no Spring.

### Break verification (mutation in temporary copy, ContractSeparationTablesIntegrationTest, 11 tests)
| Defence | How broken | Result |
|---|---|---|
| A contract in-force period exclusion | WHERE predicate made never true | red (2 fail) |
| B four dismissal checks before approval | `<> 4` -> `<> 99` | red |
| C system-only basis vs SYSTEM source | check -> `IF false` | red |
| D linked command effective = last working day + 1 | clause removed | red |
| E complete only when command EFFECTIVE | check -> `IF false` | NOT red (must-fix 1) |
| F one open request per relationship | index predicate REQUESTED only | NOT red (must-fix 2) |
| G version advances by one | check -> `IF false` | NOT red (must-fix 3) |
| H one successor per contract | index predicate inverted | red |
| I column whitelist per edge | jsonb diff check -> `IF false` | red |
| J delete owner-only (contracts) | check -> `IF false` | red |
| K approver is not submitter | CHECK (true) | red |
| L insert as DRAFT only | check -> `IF false` | NOT red (must-fix 4) |
