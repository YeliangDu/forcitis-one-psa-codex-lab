复审结论：必改
commit：2f4000420457274a6dc00062fa6b0cfd56db0794
复审者：opus-首轮-729db

patch-id：5c9630f013a17ad1b44c200d89c4d4d16337ae68

## Impact

Database only. A new table `platform_identity.permission_adjustments`, one new `platform_audit.audit_subject_kinds` row and the matching `AuditSubjectKind.PERMISSION_ADJUSTMENT` constant. No main code reads or writes the table yet (registry has no `writers`; `DataArchitectureTest` agrees). The only runtime consumer is the start-up check `AuditSubjectKindCheck`, which compares the enum with the registered rows. One caller layer proven: removing the row makes the application context fail to start (see the break table).

## Must-fix

1. **The runtime role keeps DELETE on `permission_adjustments`. The card's privilege defence is missing** (lost line of defence).
   - Where: `docs/ops/one-db-roles-schemas.sql` §4b (around lines 143-145) and `docs/ops/runbooks/sql/dev-db-after-migrations.sql` (around lines 31-33). Neither file is in this diff, but the table that needs the REVOKE is.
   - Card (f-1.md on main): registration list item 6 says "`one_db_roles_schemas.sql` §4b 撤销清单加三张新表的 `DELETE / TRUNCATE`（与 ADR-0034 卡同法）". The retention clause says "到期在线删除只走受控特权路径（同 0034-Q2，运行角色无 `DELETE`）". The registry comment this PR adds also says the table is "purged by chain", which means deletion through a controlled path.
   - Evidence:
     - `one-db-roles-schemas.sql:90` grants `SELECT, INSERT, UPDATE, DELETE ON TABLES TO one_dev_app` by default privileges in every schema.
     - `grep -n permission_adjustments docs/ops/one-db-roles-schemas.sql docs/ops/runbooks/sql/*.sql` returns nothing.
     - #728 added the other two tables of the card (`role_version_custom_org_units`, `authorization_request_custom_org_units`). The third table, created here, was left out.
     - So once this table is on main and the after-migration script runs, the application role can delete review items and their chain directly, without the aggregate or audit.
   - Fix:
     - Add `REVOKE DELETE, TRUNCATE ON platform_identity.permission_adjustments FROM one_dev_app;` next to the #728 lines in `one-db-roles-schemas.sql` §4b and in `dev-db-after-migrations.sql`.
     - Regenerate `prod-db-after-migrations.sql` with `render-prod-sql.sh` (it is not edited by hand).
     - Keep `scripts/guards/db_scripts_aligned.py` green.
   - If lead decides this belongs to #730, record it on the card. Do not drop it silently.

## Suggestions

1. **Registry retention.** `data-access-registry.yaml`, new `permission_adjustments` entry: `retention: {policy: PERMANENT}` with a comment that cites N1-10 (3 years online, 10 years in total). ADR-0043 R-2 (`docs/adr/ADR-0043.md:116`) says a table with PI columns may not be PERMANENT without a reason and the user's decision. This entry now has three PI rows. R-2 is not enforced by a test yet (no hit in `DataArchitectureTest`). Follow the `role_versions` entry: say that the purge job is pending, what the policy becomes (`{policy: 3y, job: …}`), and the owner and due date. As written, a reader may think a purge already exists.
2. **Classification differs from the card.** `data-classification.txt`: the card's 分级表 (C4) registers only `uphold_reason`. It treats `recipient_employee_id` and `business_owner_employee_id` as surrogate keys ("不登记，出口按 NONE"). This PR marks both as PI. The D5 guard needs the `recipient` name registered, and marking it PI is stricter, so no harm is done. Get one-data to confirm, or note the deviation on the card, so the two stay consistent.
3. **Comment wording differs from the card on main.**
   - The `trigger_personnel_command_id` comment says "调动命令"; the updated card (main `e47e5a5b8`) says "调岗命令".
   - The `user_id` and `org_unit_id` comments differ from the card. They are reworded to start with the glossary name (G39 / `GlossaryTest`), which is justified.
   - The inline SQL comment above `ck_permission_adjustments_recipient` is dropped.
   - Card section 7 counts comment differences in the expected diff. Align the wording with the card, or have one-design write the G39 rewording into the card.
4. **Column comments name events that do not exist yet.** The `trigger_org_structure_command_id` comment names `OrgUnitDissolved` and `OrgUnitMerged`. Neither exists on head or on main (`git grep` in `backend/src/main` and `docs/architecture/events` finds only this comment). They come from #907. This is not stale, but a short "(#907)" would stop an agent from looking for them.
5. **Glossary.** The proposed file adds only `origin` and `regeneration`. The card's 本卡新词 table also lists `trigger_field_kind`, `escalation_due_at`, `uphold_expires_at` and the other terms. The ruling also renamed the columns to `*_personnel_command_id`, `trigger_org_structure_command_id` and `is_held_at_expiry`. `GlossaryTest` is green. Check with one-glossary whether the rest should be proposed now or with #730.
6. **Card section 7.2 is out of date.** It still says to add a value to `ck_audit_entries_subject_kind`. On head this is the `audit_subject_kinds` table (ADR-0069 §14), and the implementation correctly adds a row. A one-line card fix by one-design would help.

## Checked and fine

- **Commit and PR.** `git cat-file -e 2f40004…^{commit}` passes. `gh pr view 1087` reports `headRefOid` = 2f4000420457274a6dc00062fa6b0cfd56db0794 and base `main`. Diff taken with `git diff origin/main...2f4000420`: 17 files.
- **Acceptance tests not changed by the implementer.** Every test file commit carries `Acceptance-Test-Author: test-engineer`: 799073d42, 43d5a6b5e, c056dcf40, 618ad5149, de0e515e8, b2530936d, 777ddabf7. The `P5Names` change has `Acceptance-Ruling: RULING-LEAD-20261103-13`, and the ruling exists in `docs/rules/rulings/acceptance-rulings.yaml:457` on main. The `RoleScopeGrantIT` and `F1_728_ACCEPTANCE.md` changes only edit labels and Disabled reasons (#729 → #730); no assertion changed. The implementer commits (32590fa00, bc320eb5c, 9f7f8927d) touch no test file.
- **DDL against the card.**
  - The migration was compared line by line with the 7.1 block of f-1.md (head) and of f-1.md (main, after `e47e5a5b8`/`7a209f5d0`).
  - All column, constraint, index and FK differences against the head card are exactly the ruling's items 1-4 (#729 comment by one-design, 2026-11-03 07:10). Those items are:
    - `trigger_personnel_command_id` and `trigger_org_structure_command_id`;
    - `ck_permission_adjustments_trigger_command`, as written in the ruling;
    - `uk_permission_adjustments_assignment_trigger` on `(kind, user_id, trigger_personnel_command_id, trigger_field_kind)`;
    - `is_held_at_expiry boolean` and the ruled `ck_permission_adjustments_expiry`;
    - `closure_personnel_command_id` and the ruled `ck_permission_adjustments_closed`;
    - `CLOSED_BY_REACTIVATION` removed.
  - Against the main card, only comment wording differs (suggestion 3).
- **7.2.** The row in `V20261103120100__platform_audit_permission_adjustment_kind.sql` has id `…000000000621`, which is unique in `db/migration`, with `platform.identity`, has_object true and ACTIVE. The enum is placed after `AUTHORIZATION_REQUEST`. A precedent for a separate audit kind migration exists (`V20261102171100__platform_audit_retire_sync_run_kind.sql`).
- **Migrations.**
  - The new versions 20261103120000 and 20261103120100 are above the highest on main (20261102180000).
  - `migration-baseline-open` exists, and `python3 scripts/guards/migrations_immutable.py <merge-base>` reports "baseline open, 0 violation(s), 0 warning(s)".
  - No merged migration was edited.
- **Registry.**
  - `fk_exceptions` covers every non-FK `_id` column: org_unit, two trigger commands, before/after unit and position, recipient, business owner, audit entry, closure command.
  - `references` covers `upheld_by` and `adjusted_by` (K6).
  - There are no `writers`, which is correct with no SQL (`DataArchitectureTest` 13/13 green).
- **Unbuilt roots and aggregate registry.** `python3 scripts/codegen/scan_aggregates.py --check` reports "check 通过". `unbuilt-roots.yaml` matches the `built.yaml` meta (owner one-dev-identity, due 2026-11-20).
- **Comment facts checked.**
  - `OrgDirectory.availabilityOn` is at `organization/workforce/api/OrgDirectory.java:18`, and `separationHeld` at `Availability.java:8`.
  - `EmployeeTransferred` and `EmployeeSeparated` exist under `organization/workforce/events/`.
- **Tests run in a temporary checkout of 2f4000420, offline Maven, all green.**
  - `PermissionAdjustmentSchemaIT` 5, `PermissionAdjustmentShapeIT` 11, `PermissionAdjustmentLifecycleIT` 11, `PermissionAdjustmentSubjectKindIT` 1.
  - `RoleScopeGrantIT` 7 (2 skipped, by design).
  - `AuditSubjectColumnsIT` 4, `EventEnvelopeContractTest` 6, `AuditIdContractTest` 5 (the generated `schema.ts` was copied in because the fresh checkout lacks the codegen output).
  - `DataArchitectureTest` 13, `DdlNamingTest` 27, `ClassificationRegisterTest` 7, `GlossaryTest` 5, `AuditSubjectKindGuardTest` 4, `EventCatalogueTest` 10.
  - `db_scripts_aligned.py` passes.
- **Other checks.** No secrets and no row data. No code file is near 1000 lines (the migration has 161). Coding-core §B: the only Java change is one enum constant. There are no deletions, so no orphaned readers.
- **Break verification.** Each defence was broken in a temporary copy of the migration and the PermissionAdjustment*IT suite was run:

| Defence | How broken | Result |
|---|---|---|
| `ck_permission_adjustments_trigger_command` (org-structure half) | dropped the `ORG_UNIT_INACTIVE` = `trigger_org_structure_command_id` clause | red (ShapeIT 1 failure) |
| `ck_permission_adjustments_expiry`: expired needs `is_held_at_expiry` | dropped `(expired_at IS NULL OR is_held_at_expiry IS NOT NULL)` | red (LifecycleIT 1) |
| `ck_permission_adjustments_expiry`: an unheld expiry must be decided | dropped `(expired_at IS NULL OR is_held_at_expiry OR regeneration_decided_at IS NOT NULL)` | red (LifecycleIT 1) |
| `ck_permission_adjustments_closed`: closure command paired with `closed_at` | dropped the pairing clause | red (LifecycleIT 1) |
| `ck_permission_adjustments_not_own` | replaced the `upheld_by <> user_id` test with true | red (LifecycleIT 1) |
| `uk_permission_adjustments_assignment_trigger` | added `before_org_unit_id` to the key | red (SchemaIT 1) |
| `ck_permission_adjustments_status` without reactivation | re-added `CLOSED_BY_REACTIVATION` | red (ShapeIT 1) |
| `ck_permission_adjustments_assignment_change` last clause (invariant 35) | dropped `before_org_unit_id <> after_org_unit_id` | red (ShapeIT 1) |
| audit subject kind row | replaced the INSERT with `SELECT 1` | red: context fails to start, "PERMISSION_ADJUSTMENT is a code constant but not registered" (`AuditSubjectKindCheck`) |
| `AuditSubjectKind.PERMISSION_ADJUSTMENT` | removed the constant | red (`AuditSubjectKindGuardTest` 1, `EventCatalogueTest` 1) |
| runtime-role DELETE revoke | not present (must-fix 1) | n/a |

The temporary checkout was restored after every break and removed at the end.
