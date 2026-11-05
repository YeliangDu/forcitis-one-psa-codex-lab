# Data design review: organization.leave slice L1b (six tables)

Card: to be named by lead (the sign-off for the tables that L1a held back; the schema card follows it). Basis: `docs/architecture/proposals/organization-leave.md` sections 3, 4.1, 4.2, 8, 9 at origin/main `ab1e6dc2a`, the legacy values in the #1435 comments (aggregates only, verified there unless marked), user answers of 2026-11-05 (T-3 a, T-10 a; T-1 verified), gate `docs/data/data-design-gate.md` appendix A. Signed by one-design (form as `organization-leave-l1a.md`). Where this record differs from the section 8 sketch, the record governs (list in "Differences").

## Scope

Schema `organization_leave` (created by L1a), six tables: `leave_types`, `leave_rule_versions`, `leave_entitlement_rules`, `leave_accounts`, `leave_ledger_lines`, `leave_grant_basis`. This closes the three registered deferrals of L1a: the foreign keys `fk_leave_requests_leave_type_id`, `fk_monthly_confirmation_lines_leave_type_id` (each with an index starting with the column, G-DDL2) and `fk_leave_request_holds_ledger_line_id`, all `ON DELETE RESTRICT`, added in the same migration (the three tables are still empty, so no `NOT VALID`); the registry `references` entries are added with them.

**Values are not signed here.** The tables carry structure; the numbers (entitlement bands, carry-over cap and expiry month, lot validity days) enter later through `PublishLeaveRuleVersion` / the L2 seed. Rule rows enter only through `PublishLeaveRuleVersion` (the publisher triple needs a real audit entry, so a Flyway seed cannot write them), as for standard hours; the "L2 seed" below means L2's use of that command.

Items the sign-off does not settle, for lead (none blocks the structure):

1. T-7 values: legacy has carry-over by application with 0 to 3 days carried in; cap and expiry date are not in the A8 form (#1435, unverified). The statute note allows a leave to cross one year (`LEAV#年休假跨年`). The structure holds none / capped / unlimited and an expiry month, so any answer fits; the L2 seed value is a user item.
2. T-8 value: no comp-time type exists in A8 and the repo law note gives no validity period; `grant_valid_days_count` null means "never expires". Which N the seed uses is a user item.
3. T-5 statutory cut: the repo law note does not contain the thresholds, so they are **unverified here** and no column for them is signed. `leave_grant_basis.unpaid_leave_days_count` is the recorded input. If the thresholds turn out to be rule data, L2 or L3 adds nullable columns to `leave_entitlement_rules` in a new sign-off (an additive change); lead should have the statute text checked before the L3 entitlement command is built.

## Table to object source (B1, B2)

| Table | Source | Aggregate | Registry role |
|---|---|---|---|
| `leave_types` | controlled code table (spec 4.1, 8) | none (reference data) | `CONFIG`, `MIGRATION_ONLY` |
| `leave_rule_versions` | AGG-118 (4.1) | LeaveRuleVersion | `DIMENSION_VERSIONED`, `APPEND_ONLY` with a stated update condition (as `standard_hours_versions`) |
| `leave_entitlement_rules` | AGG-118 rows (4.1) | LeaveRuleVersion | `AGGREGATE_CHILD`, `APPEND_ONLY` |
| `leave_accounts` | AGG-71 (4.2) | LeaveBalanceAccount | `AGGREGATE_ROOT`, `CONDITIONAL_UPDATE` |
| `leave_ledger_lines` | AGG-71 ledger (3.1, 4.2) | LeaveBalanceAccount | `AGGREGATE_CHILD`, `APPEND_ONLY` |
| `leave_grant_basis` | AGG-71 inputs (3.5) | LeaveBalanceAccount | `AGGREGATE_CHILD`, `APPEND_ONLY` |

Conventions as L1a: UUIDv7 `id`, `pk_<table>`, enums are `text` with named CHECKs, free text has `char_length` and `btrim` CHECKs, nullable columns say 「为空表示」, audit `created_at`, `updated_at` on every table (`DdlNamingTest`). Ids of other contexts (`employment_relationship_id`, `employee_id`, `employer_entity_id`, user ids) are `uuid`, no foreign key (B4, ADR-0086).

## Columns and constraints

### leave_types

`id`; `code text NOT NULL` (`ck_leave_types_code` format `~ '^[A-Z][A-Z0-9_]{1,31}$'`, `uk_leave_types_code`); `display_name text NOT NULL` (1 to 60, `btrim`); `ledger_unit_kind text NOT NULL` (`MINUTE`, `DAY`; a `DAY` quantity is stored in hundredths of a day, 3.2); `is_paid boolean NOT NULL`; `is_quota_counted boolean NOT NULL` (false: sick, personal, marriage and the like have no account, only requests). Rows are inserted by migration only (L2 seeds the legacy types: annual, personal, sick, marriage, childcare, nursing, maternity, prenatal check, funeral as seen in #1435; the list is L2's, not signed here). Trigger refuses update and delete of a row after insert except `display_name` (a label fix; `code` and the unit never change because accounts and lines depend on them).

### leave_rule_versions (AGG-118)

| Column | Type | Rule |
|---|---|---|
| `id` | uuid | pk |
| `leave_type_id` | uuid NOT NULL | `fk_leave_rule_versions_leave_type_id` RESTRICT |
| `scope_kind` | text NOT NULL | `ALL`, `EMPLOYER_ENTITY` (spec 4.1: the relationship's employer, city dropped until the user names a city rule) |
| `employer_entity_id` | uuid null | 为空表示 scope `ALL`; `ck_leave_rule_versions_scope_matches_entity` `(scope_kind = 'ALL') = (employer_entity_id IS NULL)`; no FK |
| `version_number` | integer NOT NULL | `>= 1`, per (type, scope): `uk_leave_rule_versions_entity_version_number` (`leave_type_id`, `employer_entity_id`, `version_number`) where entity not null; `uk_leave_rule_versions_all_version_number` (`leave_type_id`, `version_number`) where entity null |
| `status` | text NOT NULL | `IN_EFFECT`, `VOIDED` |
| `effective_from_date` | date NOT NULL | first day; leave years are calendar years (Q-2) but a version may start on any day from today on (no backdating) |
| `effective_to_date` | date null | 为空表示 open-ended; half-open `[from, to)`; `ck_leave_rule_versions_effective_range` `to IS NULL OR to > from` |
| `request_unit_kind` | text NOT NULL | `MINUTE`, `HALF_DAY`, `DAY` (smallest request step) |
| `is_negative_allowed` | boolean NOT NULL | T-4: never for annual leave; a per-type exception is a published rule, through approval and an ADR note |
| `exempt_threshold_minutes_count` | integer null | 为空表示 no exemption from approval; `> 0` |
| `is_proof_required` | boolean NOT NULL | |
| `carry_over_kind` | text NOT NULL | `NONE`, `CAPPED`, `UNLIMITED` (T-7) |
| `carry_over_cap_qty` | integer null | 为空表示 no cap (kind is not `CAPPED`); `ck_leave_rule_versions_carry_over_columns` `(carry_over_kind = 'CAPPED') = (carry_over_cap_qty IS NOT NULL)`, `carry_over_cap_qty > 0` and `carry_over_kind <> 'NONE' OR carry_over_expiry_month_number IS NULL`; in the type's ledger unit |
| `carry_over_expiry_month_number` | integer null | 为空表示 carried balance does not expire; `BETWEEN 1 AND 12`: the carry-in lot expires on the last day of that month of the new year; must be null when kind is `NONE` |
| `grant_valid_days_count` | integer null | 为空表示 lots (comp-time grants) never expire (T-8); `>= 1`; the grant line's `expiry_date` = grant date plus this count, stored on the line (spec 4.2) |
| `basis_ref_text` | text NOT NULL | where the values come from (statute article, legacy evidence reference, user decision file); 1 to 200, `btrim`; citations only |
| `published_by`, `published_at`, `decision_audit_entry_id` | uuid, timestamptz, uuid, all NOT NULL | the publisher and the audit entry of the decision, the same triple as `standard_hours_versions`; no FK to audit (other context) |
| `voided_by`, `voided_at`, `void_reason_text` | null together | `ck_leave_rule_versions_void_columns` as `standard_hours_versions` (`status = 'VOIDED'` exactly when set; reason 1 to 500) |
| `created_at`, `updated_at` | | audit |

Constraints: `uk_leave_rule_versions_id_leave_type_id` `(id, leave_type_id)` (target of the composite keys of `leave_accounts` and `leave_grant_basis`, so an account or a basis row cannot name a version of another type); `ex_leave_rule_versions_overlap_entity` `EXCLUDE USING gist (leave_type_id WITH =, employer_entity_id WITH =, daterange(effective_from_date, effective_to_date, '[)') WITH &&) WHERE (status = 'IN_EFFECT' AND employer_entity_id IS NOT NULL)` and `ex_leave_rule_versions_overlap_all` the same without the entity column `WHERE (status = 'IN_EFFECT' AND employer_entity_id IS NULL)` (INV-123, needs `btree_gist`, which other baselines create). Index `ix_leave_rule_versions_type_from` `(leave_type_id, effective_from_date)` for the lookup "version in force on a date" (the exclusions' gist index does not serve the equality plus date probe in the right order for the entity-less scope; hundreds of rows at most).

Trigger `tg_leave_rule_versions_change` and `tg_leave_rule_versions_truncate`: the complete list of legal writes, as the #1310 record and trigger define for standard hours (precedent, not an invented path): (1) insert `IN_EFFECT`, open, `version_number` = latest of the scope plus one, `effective_from_date` later than the scope's latest non-voided version **and not earlier than the database date** (Asia/Shanghai, `transaction_timestamp()`): no version acts on the past, the first version of a scope and a new version after all were voided included, exactly the rule the #1310 card added in `V20261104210200`. Reason: an account stores the `rule_version_id` in force on its open date, and a backdated version would close its predecessor retroactively so the stored id and the lookup for the same date would disagree (D15); (2) the only updates are closing the previous version (`effective_to_date` null to a date, done by the insert of the next one in the same transaction), reopening it when the successor is voided, and voiding the latest version once, only while its `effective_from_date` is after the database date; nothing else changes and nothing is deleted. Corrections are new versions and never re-apply to a sealed year (D15): an account keeps its `rule_version_id`.

### leave_entitlement_rules

| Column | Type | Rule |
|---|---|---|
| `id` | uuid | pk |
| `rule_version_id` | uuid NOT NULL | `fk_leave_entitlement_rules_rule_version_id` RESTRICT; index is the prefix of the exclusion below |
| `service_years_from_count` | integer NOT NULL | `>= 0`; cumulative service in whole years at 1 January of the leave year (T-2; confirmed on legacy aggregates, #1435) |
| `service_years_to_count` | integer null | 为空表示 no upper bound; half-open `[from, to)`; `ck_leave_entitlement_rules_service_range` `to IS NULL OR to > from` |
| `granted_qty` | integer NOT NULL | `>= 0`; full-year entitlement in the type's ledger unit (`DAY`: hundredths, so 5 days = 500) |
| `proration_kind` | text NOT NULL | `NONE`, `CALENDAR_DAYS_FLOOR_DAY` (T-3 a, statute: remaining calendar days / 365 x entitlement, the fraction under one day is dropped and the result floored to whole days, never rounded to nearest, so under one day grants nothing; `LEAV#新进年假折算`). A further kind is a lossless widening later. `CALENDAR_DAYS_FLOOR_DAY` only for a `DAY` type: cross-table, checked by the publish command |
| `created_at`, `updated_at` | | audit |

Constraints: `ex_leave_entitlement_rules_service_overlap` `EXCLUDE USING gist (rule_version_id WITH =, int4range(service_years_from_count, service_years_to_count, '[)') WITH &&)` (bands of one version never overlap; a gap means no entitlement for that service); `uk_leave_entitlement_rules_version_from` (`rule_version_id`, `service_years_from_count`), kept as the foreign key's index and the business key although the exclusion implies it. Trigger `tg_leave_entitlement_rules_change`: insert only in the transaction that inserted its version (so a published version's rows are complete and frozen), update and delete refused, truncate refused.

The legacy and statute check of T-1 is done (#1435, bands 5/10/15 days at 1 / 10 / 20 cumulative work years, evaluated at 1 January): the values fit `granted_qty` with `service_years_from_count` 1, 10, 20 and `to` 10, 20, null.

### leave_accounts

| Column | Type | Rule |
|---|---|---|
| `id` | uuid | pk |
| `employment_relationship_id` | uuid NOT NULL | the relationship (ADR-0086); no FK |
| `employee_id` | uuid NOT NULL | denormalised for the data-scope check and "my balance" only, as in L1a; no FK |
| `leave_type_id` | uuid NOT NULL | `fk_leave_accounts_leave_type_id` RESTRICT |
| `leave_year_number` | integer null | 为空表示 rolling account (comp-time lots, carry-in lots with an expiry); `BETWEEN 2000 AND 2999` |
| `rule_version_id` | uuid NOT NULL | the version in force on the open date; composite `fk_leave_accounts_rule_version_type` `(rule_version_id, leave_type_id)` to `uk_leave_rule_versions_id_leave_type_id` RESTRICT; a sealed year keeps it |
| `status` | text NOT NULL | `OPEN`, `SEALED`; `ck_leave_accounts_sealed_columns` `(status = 'SEALED') = (sealed_at IS NOT NULL)` and `status = 'OPEN' OR leave_year_number IS NOT NULL` (a rolling account is never sealed) |
| `sealed_at` | timestamptz null | 为空表示 not sealed |
| `created_at`, `updated_at` | | audit |

Constraints: `uk_leave_accounts_relationship_type_year` `UNIQUE NULLS NOT DISTINCT (employment_relationship_id, leave_type_id, leave_year_number)` (one year account per relationship, type, year; one rolling account per relationship and type); `uk_leave_accounts_id_relationship_type` `(id, employment_relationship_id, leave_type_id)` (the target of the composite key of the ledger, below); indexes `ix_leave_accounts_leave_type_id`, `ix_leave_accounts_rule_version_id` (also the prefix for the composite key) (G-DDL2) and `ix_leave_accounts_employee_id` (my balance). Trigger `tg_leave_accounts_change`: insert `OPEN`; the only update is `OPEN` to `SEALED` with `sealed_at` and `updated_at` (a sealed row never changes); delete and truncate refused. **Concurrency (O5):** every ledger-writing command takes `SELECT … FOR UPDATE` on the account row first, and `CloseLeaveYear` does the same and checks that no hold is active; a hold racing the seal therefore sees `SEALED` and fails. No version column is needed (`CONDITIONAL_UPDATE`).

### leave_ledger_lines

| Column | Type | Rule |
|---|---|---|
| `id` | uuid | pk |
| `leave_account_id` | uuid NOT NULL | with the two columns below, composite `fk_leave_ledger_lines_account_relationship_type` to `uk_leave_accounts_id_relationship_type` RESTRICT |
| `employment_relationship_id`, `leave_type_id` | uuid NOT NULL | copies of the account's, held equal by the composite key; they exist so the event idempotency key below can span accounts (G-6) |
| `line_kind` | text NOT NULL | `GRANT`, `CARRY_IN`, `HOLD`, `RELEASE`, `USE`, `REVERSE_USE`, `ADJUST`, `EXPIRE`, `CARRY_OUT` (4.2) |
| `line_qty` | integer NOT NULL | in the type's ledger unit; `ck_leave_ledger_lines_sign` `>= 0` for every kind except `ADJUST` (signed, non-zero) |
| `source_kind` | text NOT NULL | format `~ '^[A-Z][A-Z0-9_]{1,31}$'`; the closed value list is added by the L3 slice before its first write (the writers are not known until then) |
| `source_id` | uuid NOT NULL | the request, event, approval or command that caused the line |
| `original_leave_year_number` | integer null | 为空表示 the line is not driven by a relationship event; set to the year the event is about, so a redelivery after the seal redirected to the next year hits the same key (RULE-514, G-6) |
| `effective_date` | date NOT NULL | |
| `expiry_date` | date null | 为空表示 no expiry (not a lot, or a lot that never expires); `ck_leave_ledger_lines_expiry_kind` only on `GRANT`, `CARRY_IN`; `> effective_date` |
| `consumes_line_id` | uuid null | 为空表示 not drawn from a lot; the `GRANT` or `CARRY_IN` line drawn from; only on `HOLD`, `RELEASE`, `USE`, `REVERSE_USE`, `EXPIRE`, and on an `ADJUST` of a rolling account (names the lot adjusted; `ck_leave_ledger_lines_consumes_kind`, the rolling-account part in the trigger); self reference `fk_leave_ledger_lines_consumes_line_id` RESTRICT; index starts with it |
| `corrects_line_id` | uuid null | 为空表示 not a correction; the original line an `ADJUST` of a sealed year corrects (RULE-514); only on `ADJUST`; self reference RESTRICT; index starts with it |
| `adjusted_by` | uuid null | 为空表示 not a manual HR adjustment; the HR operator, only on `ADJUST` |
| `reason_text` | text null | 为空表示 not a manual adjustment; 1 to 500, `btrim`; set exactly when `adjusted_by` is set |
| `created_at`, `updated_at` | | audit; equal on insert, never changed |

Constraints: `ck_leave_ledger_lines_adjust_columns` (`adjusted_by`, `reason_text`, `corrects_line_id` only on `ADJUST`; an `ADJUST` has at least one of: `adjusted_by` (manual HR, with `reason_text`; `corrects_line_id` may also be set when HR corrects a sealed-year line), `corrects_line_id` (an event-driven correction of a sealed-year line, no operator) or `original_leave_year_number` (the event-driven recompute of an open year, spec 3.5 and 4.2: `UnpaidLeaveChanged` and the like); the three are not exclusive; `(adjusted_by IS NULL) = (reason_text IS NULL)`); `uk_leave_ledger_lines_id_leave_type_id` `(id, leave_type_id)` (target of the basis key); `uk_leave_ledger_lines_source_key` `UNIQUE NULLS NOT DISTINCT (leave_account_id, source_kind, source_id, line_kind, consumes_line_id)` (a replayed hold or use cannot insert twice, also on a year account whose lot is null); `uk_leave_ledger_lines_event_key` unique on (`employment_relationship_id`, `leave_type_id`, `source_id`, `original_leave_year_number`, `line_kind`) where `original_leave_year_number IS NOT NULL` (one entitlement line of a kind per event and original year whatever account it lands on; it covers same-kind redelivery; a redirect that would turn a `GRANT` into an `ADJUST` after the seal is held by the recompute rule that writes a line only for a non-zero delta, a command rule, not a database guarantee); indexes `ix_leave_ledger_lines_account_effective` `(leave_account_id, effective_date, id)` (the balance sum and the earliest-expiry draw read one account), `ix_leave_ledger_lines_consumes_line_id`, `ix_leave_ledger_lines_corrects_line_id`.

Trigger `tg_leave_ledger_lines_guard` (and truncate): update and delete refused (D8); on insert it reads the account row `FOR SHARE` (so the seal's update and a concurrent insert serialise in the database, not only by convention) and refuses (1) a line on a `SEALED` account, so a sealed year takes no line; (2) `consumes_line_id` that does not point at a `GRANT` or `CARRY_IN` line of the same account; (3) a rolling account line of kind `HOLD`, `RELEASE`, `USE`, `REVERSE_USE`, `EXPIRE` or `ADJUST` without `consumes_line_id` (the balance of a rolling account is per lot, so an adjustment names its lot), and a year-account line of any kind with one (lot types only, spec 4.2). The derived balance is never stored (O9): sum per account for year accounts, per lot for rolling ones, read through the index above.

### leave_grant_basis

| Column | Type | Rule |
|---|---|---|
| `id` | uuid | pk |
| `leave_ledger_line_id` | uuid NOT NULL | composite `fk_leave_grant_basis_line_type` on (`leave_ledger_line_id`, `leave_type_id`) to `uk_leave_ledger_lines_id_leave_type_id` RESTRICT, so the basis `leave_type_id` is the type of its line and, through the rule-version key below, so is its version; `uk_leave_grant_basis_leave_ledger_line_id` unique (one basis per line); only a `GRANT` or `ADJUST` line (trigger, cross-table) |
| `relationship_start_date` | date NOT NULL | start of the relationship as read from the workforce api on the computation |
| `prior_service_months_count` | integer NOT NULL | `>= 0`; cumulative service before this relationship (T-6, ADR-0086); the band at 1 January is this plus the months from `relationship_start_date` |
| `relationship_end_date` | date null | 为空表示 still in service |
| `unpaid_leave_days_count` | integer NOT NULL | `>= 0`; calendar days of unpaid leave in the year as read (3.5; the sketch's `unpaid_days`) |
| `employer_entity_id` | uuid NOT NULL | the employer on the computation (`employerOn`); no FK. NOT NULL: a computation that cannot name an employer fails with an alert, no default |
| `rule_version_id` | uuid NOT NULL | the version used for this line; composite `fk_leave_grant_basis_rule_version_type` on (`rule_version_id`, `leave_type_id`) RESTRICT with a `leave_type_id uuid NOT NULL` copy of the account's type, index starts with `rule_version_id` |
| `created_at`, `updated_at` | | audit |

Trigger: insert in the transaction of its line only, update, delete and truncate refused. The row exists so a sealed year can be reproduced without a copy of workforce data (3.5).

## Differences from the section 8 sketch

1. `label` is `display_name`; `state` is `status`; `quantity` is `line_qty` and `granted_qty`; `account_id` is `leave_account_id`; `unpaid_days` is `unpaid_leave_days_count`; `*_months`, `*_years`, `*_days` get `_count`; `effective_from`, `*_date` columns get `_date`; `expires_on` is `expiry_date`; `line_id` is `leave_ledger_line_id`; `basis_ref` is `basis_ref_text`; `carry_over_cap_quantity` is `carry_over_cap_qty`; `exempt_threshold_minutes` is `exempt_threshold_minutes_count`; `leave_year` is `leave_year_number` and `original_leave_year` (new) is `original_leave_year_number` (`_year` is not a class word, `DdlNamingTest`); `ledger_unit` is `ledger_unit_kind`; `request_unit` is `request_unit_kind`; `proration_mode` is `proration_kind`; the sketch's `IN_FORCE` is `IN_EFFECT` (the repo's word for versions, #1310). Naming standard 2.4.
2. `leave_rule_versions`: added `scope_kind`, `version_number`, the publisher triple, the void triple and `grant_valid_days_count` (T-8 needs a place for N); `superseded_by` is dropped (the closed `effective_to_date` and the version numbers carry it); `carry_over_expiry_rule` becomes `carry_over_kind` plus `carry_over_expiry_month_number`.
3. `leave_accounts`: added `employee_id` (scope check, as in L1a), `status` constraint pairing.
4. `leave_ledger_lines`: added `employment_relationship_id` and `leave_type_id` (with a composite key) and `original_leave_year_number` with `uk_leave_ledger_lines_event_key`, which is how model gap G-6 is closed in the database; `corrects_line_id` and `reason_text` for RULE-514 and the manual adjustment reason the spec text asks for.
5. `leave_grant_basis.employer_entity_id` is NOT NULL (sketch: null); `leave_type_id` added to it for the composite rule-version key.
6. `leave_rule_versions` and the two accounts keys: composite keys with `leave_type_id`; no backdating (rule of #1310).

None changes a command or an event of the spec.

## Invariants to constraints (B5, O4)

| Invariant | Carrier | Reason when no constraint |
|---|---|---|
| One version in force per type and scope on a date (INV-123) | two exclusions on `leave_rule_versions` | |
| A published version and its rows never change; corrections are new versions | triggers on both tables | |
| Entitlement bands of a version do not overlap | `ex_leave_entitlement_rules_service_overlap` | |
| One account per relationship, type, year; one rolling account per relationship and type | `uk_leave_accounts_relationship_type_year` | |
| A sealed year takes no line; a sealed account never changes | `tg_leave_ledger_lines_guard`, `tg_leave_accounts_change` | |
| The ledger is append-only | `tg_leave_ledger_lines_guard` | |
| Sign per line kind; consume and expiry columns only on the kinds that use them | CHECKs above | |
| A replayed hold, use or entitlement event writes once | `uk_leave_ledger_lines_source_key`, `uk_leave_ledger_lines_event_key` | |
| One basis row per grant or adjust line | `uk_leave_grant_basis_leave_ledger_line_id` and the trigger | |
| Hold does not exceed the available balance unless the rule allows (INV-68) | | depends on the rule version and a sum over rows; checked by `HoldLeaveBalance` under the account row lock |
| Earliest-expiry draw (RULE-516), expired lots take no return | | ordering and clock logic of the commands |
| `leave_request_holds.ledger_line_id` names a `HOLD` line of the request's relationship | | cross-table; the hold command (the L1a key only guarantees the line exists) |
| `CALENDAR_DAYS_FLOOR_DAY` only for a `DAY` type; the type matches the account's lot logic | | cross-table; the publish command and the open-account command |
| Balance below zero only where T-4 allows | | same as the hold check |

## Business key, class, time (O3, O6)

| Table | Business key | Class | Versioning |
|---|---|---|---|
| `leave_types` | `code` | reference data (declared) | migration only; the unit and the code never change |
| `leave_rule_versions` | (`leave_type_id`, scope, `version_number`) | configuration | effective-interval versions, append only (D15, D17) |
| `leave_entitlement_rules` | (`rule_version_id`, `service_years_from_count`) | configuration | versioned by the parent |
| `leave_accounts` | (`employment_relationship_id`, `leave_type_id`, `leave_year_number`) | aggregate root | not versioned: a year account is one fact per year, sealed once; the history is the ledger |
| `leave_ledger_lines` | the source key | event fact | append-only; corrections are lines |
| `leave_grant_basis` | `leave_ledger_line_id` | event fact detail | append-only |

## Query paths (O10)

| Path | Filter | Sort | Rows | Paging | Index |
|---|---|---|---|---|---|
| Rule version in force for a type, employer and date | `leave_type_id`, `employer_entity_id` (or null), date inside the interval, `status = 'IN_EFFECT'` | none | 1 | none | `ix_leave_rule_versions_type_from` |
| Bands of a version | `rule_version_id` | `service_years_from_count` | 3 to 5 | none | the exclusion's gist index and the unique key |
| Accounts of a person (my balance) | `employee_id`, optional `leave_year_number` | `leave_year_number` desc, `id` | tens | cursor | `ix_leave_accounts_employee_id` |
| Account of a relationship, type, year (open, hold, grant) | the three columns | none | 1 | none | `uk_leave_accounts_relationship_type_year` |
| Balance of an account, earliest-expiry draw | `leave_account_id` | `effective_date`, `id` | tens to low hundreds | none (capped by the account) | `ix_leave_ledger_lines_account_effective` |
| Lines drawn from a lot | `consumes_line_id` | none | few | none | `ix_leave_ledger_lines_consumes_line_id` |
| Replay of an event | event key columns | none | 1 | none | `uk_leave_ledger_lines_event_key` |
| Basis of a line | `leave_ledger_line_id` | none | 1 | none | its unique key |
| Open accounts to close at year end | `leave_year_number`, `status = 'OPEN'` | `id` | hundreds | batch | `ix_leave_accounts_employee_id` does not serve it; a once-a-year scan of a few thousand rows (at most a thousand accounts a year (the legacy annual-leave archive holds 3 154 rows over seven years, about 450 a year)), not a frequent path, so no extra index |

## Cross-cutting, ADR clauses, value objects, large tables (O11, O13 to O16)

- Large table (O11): the ledger is append-only with `PERMANENT` retention (Q-3), so the gate asks for a partition key and archive route. Volume, from #1435: about 160 + 100 + 20 annual-leave entitlements a year and 4 282 annual-leave records in 2025; at three lines per record plus grants and year-end lines the ledger grows by roughly 15 000 to 20 000 rows a year, so about 150 000 rows in ten years, a factor of about 67 below the 10 million line. Decision: no partition, no archive route; revisit if headcount or volume changes by a factor of 50. Counts are aggregates from the #1435 comments (verified there), the extrapolation is mine.
- Legal entity: the account is per relationship, not per entity (leave follows the relationship, ADR-0086 decision 5; the employer is read from the relationship and recorded in the basis row; payroll's entity split comes from requests, section 9a), so the account and ledger carry no `legal_entity_id`. The ledger has no accounting period; the document date is `effective_date`; the business number of a line is its source request (`source_id`). A6: not applicable to a balance ledger of this kind (not a business document or fact row of a legal entity), stated rather than skipped.
- Money: none. Time zone: dates only except `sealed_at`, `published_at`. No `jsonb`; free text only `reason_text`, `void_reason_text`, `basis_ref_text`, `display_name`, with the content limits above.
- ADR clauses: ADR-0079 (leave context, sealed year :123, approval :125), ADR-0086 (per relationship; no cross-BC key), ADR-0043 (below), ADR-0026 item 5 (registry), D15/D17 of the gate (versions). No other ADR column rule applies.
- Value objects: `LeaveType` code, ledger quantity (unit-carrying), `LeaveYear`, service months; the CHECKs mirror them.

## Registry drafts and classification (C1 to C5)

```yaml
organization_leave.leave_types:
  role: CONFIG
  mutability: MIGRATION_ONLY
  retention: {policy: PERMANENT}
  lineage: {source: NEW}
  writers: [organization.leave]
organization_leave.leave_rule_versions:
  role: DIMENSION_VERSIONED
  aggregate: LeaveRuleVersion
  mutability: APPEND_ONLY
  condition: "closing the previous version (effective_to_date null to a date by the publish of the next), its reopening and the voiding of a future latest version are the only updates; nothing is deleted (tg_leave_rule_versions_change)"
  retention: {policy: PERMANENT}
  lineage: {source: NEW}
  writers: [organization.leave]
organization_leave.leave_entitlement_rules:
  role: AGGREGATE_CHILD
  aggregate: LeaveRuleVersion
  mutability: APPEND_ONLY
  retention: {policy: PERMANENT}
  lineage: {source: NEW}
  writers: [organization.leave]
organization_leave.leave_accounts:
  role: AGGREGATE_ROOT
  aggregate: LeaveBalanceAccount
  mutability: CONDITIONAL_UPDATE
  condition: "OPEN to SEALED once, with sealed_at (tg_leave_accounts_change); nothing else changes"
  retention: {policy: PERMANENT}      # Q-3, see retention note
  lineage: {source: NEW, migrated_from: [old annual leave archive 年假档案], via: ONE_OFF_IMPORT}
  writers: [organization.leave]
organization_leave.leave_ledger_lines:
  role: AGGREGATE_CHILD
  aggregate: LeaveBalanceAccount
  mutability: APPEND_ONLY
  retention: {policy: PERMANENT}
  lineage: {source: NEW, migrated_from: [old annual leave archive 年假档案], via: ONE_OFF_IMPORT}
  writers: [organization.leave]
organization_leave.leave_grant_basis:
  role: AGGREGATE_CHILD
  aggregate: LeaveBalanceAccount
  mutability: APPEND_ONLY
  retention: {policy: PERMANENT}
  lineage: {source: NEW}
  writers: [organization.leave]
```

`references` entries: `leave_requests.leave_type_id`, `monthly_confirmation_lines.leave_type_id` to `leave_types`, `leave_request_holds.ledger_line_id` to `leave_ledger_lines`, plus the in-schema keys of this slice. The card adapts the drafts to the registry header; the registry test is the check. Reading by other contexts: payroll reads through the leave api (section 9a), not these tables, so `readers` stays empty.

**Retention (C3).** `PERMANENT` on the strength of Q-3, answered 2026-11-05 (`docs/decisions/2026-11-05-120403-one-lead-均采纳-监管部门有明确法规且和企.md` (Q-3 adopted as recommended: ledger kept with the employee record period)): ledger and records are kept with the employee record period, an expiry job is a later decision. `leave_accounts` and `leave_grant_basis` and the ledger carry `PI` columns, so ADR-0043 R-2 applies and the registry entry must carry a reason and the decision source (a comment until the DG-0 field exists, as `emergency_admins`): 「a leave account and its ledger are the employee's leave record, kept with the employee record period; user decision Q-3, 2026-11-05」. The user can set a limit later.

| Column | Access level | PI | Reason |
|---|---|---|---|
| `leave_accounts.employee_id` | `INTERNAL` | `PI` | 用途：额度账户所属员工，数据范围判断与本人查询 |
| `leave_ledger_lines.effective_date` | `INTERNAL` | `PI` | 用途：额度流水发生日，额度核对与审计（用掉、占用的日子即个人的请假日期） |
| `leave_grant_basis.relationship_start_date`, `.relationship_end_date`, `.prior_service_months_count`, `.unpaid_leave_days_count` | `INTERNAL` | `PI` | 用途：额度折算与档位的依据，个人的入离职日期、累计工龄与停薪留职天数 |
| `leave_ledger_lines.adjusted_by`, `.reason_text` | `INTERNAL` | `PI` | 用途：人事手工调整的操作人与原因，审计；原因文字不得写病情或个人隐私 |
| all other columns | `INTERNAL` (default, not registered) | `NONE` | quantities, kinds, ids, rule values |

`reason_text` must not carry health information (sick leave): its content is limited to the reason of a balance correction; the leave form's reason and proof columns stay carved out in L1a. No `SENSITIVE`, `RESTRICTED` or `SPI` column; no event payload or audit `detail` carries `reason_text` or the dates above (ADR-0043 §4 item 1).

## Comments (C1)

| Table | Comment draft |
|---|---|
| `leave_types` | 假期类型。粒度：每种假期一行，只由迁移写入 |
| `leave_rule_versions` | 假期规则版本。粒度：每个假期类型每个适用范围每个生效区间一行，只追加 |
| `leave_entitlement_rules` | 假期规则的额度档位。粒度：每个规则版本每个工龄档位一行 |
| `leave_accounts` | 假期额度账户。粒度：每个雇佣关系每个假期类型每个假期年度一行；无年度的为滚动账户 |
| `leave_ledger_lines` | 额度流水，只追加，余额由它推出。粒度：每个账户每次额度变动一行 |
| `leave_grant_basis` | 额度发放或调整所依据的输入。粒度：每条发放或调整流水一行 |

| Nullable column | 「为空表示」 draft |
|---|---|
| `leave_rule_versions.employer_entity_id` | 为空表示规则适用于所有用人单位 |
| `.effective_to_date` | 为空表示没有后继版本，一直有效 |
| `.exempt_threshold_minutes_count` | 为空表示没有免审批时长 |
| `.carry_over_cap_qty` | 为空表示不设结转上限（结转方式不是限额） |
| `.carry_over_expiry_month_number` | 为空表示结转的额度不过期 |
| `.grant_valid_days_count` | 为空表示发放的额度（调休）不过期 |
| `.voided_by`, `.voided_at`, `.void_reason_text` | 为空表示版本未作废 |
| `leave_entitlement_rules.service_years_to_count` | 为空表示工龄没有上限 |
| `leave_accounts.leave_year_number` | 为空表示滚动账户，无年度 |
| `.sealed_at` | 为空表示该年度尚未封存 |
| `leave_ledger_lines.original_leave_year_number` | 为空表示该行不是由关系事件按年度触发 |
| `.expiry_date` | 为空表示不过期（不是批次，或批次不过期） |
| `.consumes_line_id` | 为空表示不是从某个批次扣减 |
| `.corrects_line_id` | 为空表示不是对已封存年度某行的更正 |
| `.adjusted_by`, `.reason_text` | 为空表示不是人事手工调整 |
| `leave_grant_basis.relationship_end_date` | 为空表示仍在职 |

Non-null columns: meaning and unit, one line each, written by the schema card; glossary words go to `docs/glossary/terms.yaml` in the same card (`GlossaryTest`).

## Analytical facts (A1 to A8)

No analytical fact and no read model in L1b. The ledger is the transaction history of balances; the lot-expiry event `LeaveCompTimeExpired` is written with its publisher in L5 (events catalogue, ids and minutes only). Metrics on leave balance are not introduced (A7: none).

## Baseline, target, transition, rollback (T1 to T4)

Baseline: schema `organization_leave` with the seven L1a tables (merged or in the L1 card), none of these six; the legacy annual-leave archive is evidence only (`LegacyShapeTest`: no legacy name in the design). Target: the six tables, the three foreign keys of L1a, the triggers and the registry entries. Transition: structure only, no data; leave types and rule rows are L2's seed, the per-person balances come from a later one-off import with its own mapping card (E3; the registry `lineage` names the source). Rollback: while empty, drop the six tables and the three keys; afterwards a new migration.

## Expected schema diff (for the schema card)

Form (O12): `backend/architecture/migration-baseline-open` exists, so the schema card edits the L1a baseline migration `V20261105160000__organization_leave_baseline.sql` in place (no appended migration; the dev database is rebuilt, `docs/ops/runbooks/dev-db-rebuild.md`), and this is the expected diff.

In schema `organization_leave`: the six tables, their constraints and indexes above (all names at most 63 bytes; the longest is `fk_leave_ledger_lines_account_relationship_type`, 47 bytes, and `ck_leave_rule_versions_scope_matches_entity`, 43 bytes), the triggers `tg_leave_types_change`, `tg_leave_rule_versions_change`, `tg_leave_entitlement_rules_change`, `tg_leave_accounts_change`, `tg_leave_ledger_lines_guard`, `tg_leave_grant_basis_change` and one truncate trigger each with their functions (every function pins `search_path`, `function-search-path-pinning.md`), the three foreign keys of L1a with their indexes, and an existence check for `btree_gist`. Nothing else. The schema PR's diff is compared with this record table by table, column by column.

## Result

数据设计审查
- 审查：one-design，2026-11-05，依据 origin/main `ab1e6dc2a`
- 判定：B1 至 B7、O1 至 O16、C1 至 C5、T1 至 T4 通过（六张表；O11 已按预计行量写明不分区）；A1 至 A5、A7、A8、E1、E2 不适用（无分析事实、读模型）；E3 迁入映射延后到导入卡（注册表 lineage 先登记）；A6 按「均衡账本非法人单据」说明，不适用。
- 豁免与待用户决定：规则取值（T-7 的上限与到期月、T-8 的有效天数）在 L2 种子里由用户定，不在本次签字；T-5 的法定扣减数值本仓库法条笔记未载，未核实，未签相关列，如需列则新签字（加可空列）；保留期 `PERMANENT` 依据 Q-3 并登记 R-2 理由。
- 结论：通过（六张表）
- 数据设计：通过（one-design，2026-11-05，依据 ab1e6dc2a；范围：`leave_types`、`leave_rule_versions`、`leave_entitlement_rules`、`leave_accounts`、`leave_ledger_lines`、`leave_grant_basis`，列名按本记录「与第 8 节草案的差异」）
