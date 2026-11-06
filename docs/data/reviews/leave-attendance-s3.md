# Data design review: organization.leave slice S3 (one table with the replay key, one request column and a redefined overlap exclusion, two CHECKs, four events)

Card: #1581 (design). Basis: `docs/architecture/proposals/leave-attendance-s3.md` (the proposal, added by the PR that carries this record), the merged schema `V20261105160000__organization_leave_baseline.sql` (#1400, #1470) and its records `organization-leave-l1a.md`, `organization-leave-l1b.md`, gate `docs/data/data-design-gate.md` appendix A. Signed by one-design in the form of `workforce-acting-duty.md`. Where this record differs from the proposal's section 5 the record governs.

Evidence marks: **verified** = read on origin/main on 2026-11-07 (migration, registry, classification file, `light.sh` run with these files); **unverified** = named in the last section.

## Scope

Signed: schema `organization_leave`; new table `leave_policy_grants`; the ledger `source_kind` CHECK widened by `POLICY_GRANT` and one new CHECK tying it to the GRANT kind; new column `leave_requests.taken_until_at` with the overlap exclusion redefined; new column `leave_policy_grants.request_key` with its unique key; the payloads of `LeaveApproved`, `LeaveRecalled`, `MonthlyConfirmationConfirmed`, `MonthlyConfirmationLocked`; terms (`backend/glossary/proposed/leave-s3.yaml`).

Not signed (separate gate runs): any table of overtime, comp time, carry-over, year close and attendance import; the `cost.timesheet` consumer edge; payroll tables (#1582). `attendance_records` is reused unchanged (the table exists; the import command that writes it is code). The user answers of 2026-11-06 change no column: Q-S3-1 (punches only) is why the confirmation needs no basis column, Q-S3-2 and Q-S3-3 are rule-version and seed data, Q-S3-4 and Q-S3-5 are command permissions.

## Table to object source (B1, B2, B7)

| Table | Source | Aggregate | Registry role |
|---|---|---|---|
| `leave_policy_grants` (new) | proposal 4.1: one confirmed grant decision of a quantity to one relationship's account | `LeaveBalanceAccount` (child of the year account, the ledger's source of a `POLICY_GRANT` line) | `AGGREGATE_CHILD`, `APPEND_ONLY` |
| `leave_requests` (changed) | spec 4.3, proposal 4.3 | `LeaveRequest` | unchanged (`AGGREGATE_ROOT`, `MUTABLE`) |
| `monthly_confirmations` (changed) | spec 4.6, proposal 4.4 | `MonthlyConfirmation` | unchanged |
| `leave_ledger_lines` (changed CHECK) | spec 4.2 | `LeaveBalanceAccount` | unchanged |

## Columns and constraints

Everything below is an edit of the open baseline file `V20261105160000__organization_leave_baseline.sql` (the marker `backend/architecture/migration-baseline-open` exists; data gate O12, open period); no migration file is appended, and the schema PR carries the expected diff given in this record. Every enum is `text` with a named CHECK; every free text has `char_length` and `btrim` CHECKs; every nullable column's comment states 「为空表示」.

### leave_policy_grants (grain: one row per grant decision)

| Column | Type | Constraints and notes |
|---|---|---|
| `id` | uuid | `pk_leave_policy_grants`; UUIDv7; this id is the `source_id` of the ledger GRANT line it produces |
| `request_key` | text NOT NULL | client-supplied replay key of one grant command: `ck_leave_policy_grants_request_key` `request_key = btrim(request_key) AND char_length(request_key) BETWEEN 1 AND 100`; `uk_leave_policy_grants_account_request_key` UNIQUE `(leave_account_id, request_key)` |
| `leave_account_id` | uuid NOT NULL | with `employment_relationship_id` and `leave_type_id`: `fk_leave_policy_grants_account_relationship_type` to `leave_accounts (id, employment_relationship_id, leave_type_id)` via the existing `uk_leave_accounts_id_relationship_type`, RESTRICT |
| `employment_relationship_id` | uuid NOT NULL | another context's id: stored, covered by the composite FK to the account only (B4) |
| `leave_type_id` | uuid NOT NULL | same composite FK |
| `grant_qty` | integer NOT NULL | `ck_leave_policy_grants_grant_qty` `grant_qty > 0`; in the type's ledger unit |
| `effective_date` | date NOT NULL | business date of the grant; `ck_leave_policy_grants_effective_date` `isfinite(effective_date)` |
| `basis_text` | text NOT NULL | the confirmed decision in the operator's words: `ck_leave_policy_grants_basis_text` `basis_text = btrim(basis_text) AND char_length(basis_text) BETWEEN 1 AND 500` |
| `granted_by` | uuid NOT NULL | the operator (a column with business meaning: who decided the credit); a user id of `platform.identity`, no FK |
| `created_at` | timestamptz NOT NULL | append-only table: no `version` |
| `updated_at` | timestamptz NOT NULL | comment 「更新时刻（只追加表，触发器拒绝更新，列只为满足通用审计列约定）」, as on `attendance_records` and `leave_request_holds`; `DdlNamingTest` requires the column on every table |

Append-only by `organization_leave.guard_append_only()` (`BEFORE UPDATE OR DELETE` and `BEFORE TRUNCATE`, same pattern as `leave_request_holds`).

### leave_ledger_lines (changed)

- `ck_leave_ledger_lines_source_kind` redefined with the existing nine values plus `POLICY_GRANT`.
- New `ck_leave_ledger_lines_policy_grant_kind` `source_kind <> 'POLICY_GRANT' OR line_kind = 'GRANT'`.
- The unique source key, the sign CHECK, the lot rule and the append-only trigger stay.

### leave_requests (changed)

New `taken_until_at timestamptz`, comment 「为空表示未被提前终止；销假后为第一天未休的起点（业务时区当天零点）」. `ck_leave_requests_taken_until_at` `(status = 'RECALLED') = (taken_until_at IS NOT NULL)` and `taken_until_at IS NULL OR taken_until_at BETWEEN start_at AND end_at`. `ex_leave_requests_live_period_overlap` is redefined: `EXCLUDE USING gist (employment_relationship_id WITH =, tstzrange(start_at, CASE WHEN status = 'RECALLED' THEN taken_until_at ELSE end_at END) WITH &&) WHERE (status IN ('SUBMITTED', 'IN_APPROVAL', 'APPROVED', 'RECALLED'))`. A whole-request recall sets `taken_until_at = start_at`, an empty range that blocks nothing; a partial recall keeps the taken days blocked, so a second request cannot double-book them. That `taken_until_at` is a business-day start is a command check (business zone).

### monthly_confirmations, attendance_records (unchanged)

User-decided 2026-11-06: the month is confirmed against punches only, so no basis column is added. `attendance_records` (baseline lines 83-97) already holds the punch facts with a unique `(source_system, external_record_key)`: it is the replay key of the import command and the row grain is one punch. Its writer, the import command, is code; nothing in the table changes.

## Differences from the proposal's section 5 (the record governs)

None. The proposal's items 1 to 3 are the three changes above; its items 4 to 6 are notes on the spec.

## Event payloads (E1)

All four events: publisher `organization.leave`, audited, version 1, delivery as the other events; payload fields are ids, dates, counts and enums; no names, no reason text, no proof reference. Envelope `legalEntityId` = the cost (attribution) entity of the person on the stated date, as `leave_requests.legal_entity_id` (the spec's RULE-398 scope).

| Field | `LeaveApproved` | `LeaveRecalled` |
|---|---|---|
| aggregate / `aggregateId` | `LeaveRequest` / request id | same |
| `employmentRelationshipId` | required | required |
| `employeeId` | required | required |
| `leaveTypeId` | required | required |
| `startsOn`, `endsOn` (business dates of the first and last leave day) | required; `businessDate` = `startsOn` | required |
| `minutes` | required: total minutes approved | n/a |
| `recalledFromDate` | n/a | required, `businessDate` (business date of `taken_until_at`) |
| `returnedMinutes` | n/a | required: minutes written back by the REVERSE_USE (0 when the recall returns nothing, e.g. only non-workdays remain) |

| Field | `MonthlyConfirmationConfirmed` | `MonthlyConfirmationLocked` |
|---|---|---|
| aggregate / `aggregateId` | `MonthlyConfirmation` / confirmation id | same |
| `employeeId` | required | required |
| `month` (first day of the month) | required, `businessDate` | required, `businessDate` |
| `confirmedByKind` | required, `EMPLOYEE` / `DEFAULT` | n/a |

Consumers now: `platform.audit` for all four; `cost.timesheet` for the first two only after the seam card (the guard keeps the edge closed); payroll for `MonthlyConfirmationLocked` is assumed by #1582, not registered.

**Why the catalogue file is not in this PR.** The leave event catalogue file does not exist; `EventCatalogueTest.catalogueMatchesTheCode` fails when the catalogue names an event class that is not in the code (verified in the acting-duty design, #1525, by `scripts/ci/check_all.sh`), and the pending-fragment exemption is per module, not per event. The slice that adds each event class adds its catalogue entry from this table in the same PR.

## Invariants to constraints (B5, O4)

| Invariant | Carrier | Reason when no constraint |
|---|---|---|
| A `POLICY_GRANT` ledger line is a GRANT | `ck_leave_ledger_lines_policy_grant_kind` | |
| A grant quantity is positive | `ck_leave_policy_grants_grant_qty` | |
| Every `POLICY_GRANT` ledger line has its `leave_policy_grants` row | command, one transaction | `source_id` is generic across source kinds, so no foreign key can name this table |
| The grant row and the ledger line carry the same quantity, date and account | command, integration test | spans two tables |
| `taken_until_at` is set exactly when the request is `RECALLED`, and lies within the request's period | `ck_leave_requests_taken_until_at` | |
| A `RECALLED` request still blocks its taken range for a second request | `ex_leave_requests_live_period_overlap` (redefined) | |
| `taken_until_at` is a business-day start | command | needs the business zone |
| A grant replay with the same key writes nothing | `uk_leave_policy_grants_account_request_key` | |
| One REVERSE_USE per request per year account | existing `uk_leave_ledger_lines_source_key` | |
| A replayed import row writes nothing | `uk_attendance_records_source_external_record_key` (existing) | |
| A month with a workday that has neither a punch nor covering approved leave fails to generate | command | spans attendance records |
| A grant into a sealed year account is refused | existing ledger trigger | |

## Use cases to tables (O1, O10, O10a)

| Use case | Writes (one aggregate) | Path and index | Rows / budget |
|---|---|---|---|
| `GrantPolicyCredit` | `leave_accounts` (open if absent), `leave_policy_grants`, one GRANT line | account by `uk_leave_accounts_relationship_type_year` | one grant, one line |
| Read `LeaveBalances.of(relationship, type, year)` | none | `ix_leave_ledger_lines_account_effective` | tens of lines per account (one-year ledger of one person) |
| `RecallLeave` | request row (`status`, `taken_until_at`), one REVERSE_USE line per year account | request by pk | one to two lines |
| Month confirmation generate | `monthly_confirmations`, lines; reads `attendance_records` by person and month | `uk_monthly_confirmations_employee_month` | one per person and month; a few lines each |

Expected volume: a few thousand confirmations per month for the firm; grants and recalls are rare. All reads are by key.

## Checks

| # | Result | Evidence |
|---|---|---|
| B1, B2 | pass | table-to-object table above |
| B3, B3a | pass | `source_kind` CHECK = enum `LeaveLedgerSourceKind` (the slice makes them agree, G-D12); request state unchanged (`RECALLED` exists); every event maps to one transition: approve, recall, confirm, lock |
| B4 | pass | the grant row's relationship id sits behind the account's composite FK only; `granted_by` is another context's id, no FK |
| B5, O4 | pass | invariants table |
| B6 | pass | produced facts: grant row, ledger lines, the four events |
| B7 | pass | see scope |
| B8 | pass | terms in `backend/glossary/proposed/leave-s3.yaml`; `light.sh` rule check green with the file; UI wording stays with the user |
| O1 | pass | use cases write one aggregate each (the account opens inside the grant command, the same aggregate) |
| O2 | pass | UUIDv7 pk, UUID references |
| O3 | pass | business unique key: a grant is unique within its leave account by the client-supplied `request_key` (`uk_leave_policy_grants_account_request_key`); a retry with the same key returns the first grant, so the ledger's source key (`source_id` = grant id) cannot be reached twice by one command |
| O5 | pass | append-only grant row needs no `version`; `leave_requests` and `monthly_confirmations` keep theirs |
| O6 | pass | category: fact (grant decision), temporal basis `DECLARED` (`effective_date` entered by the operator) |
| O7 | pass | append-only trigger; FK RESTRICT; no `is_deleted` |
| O8 | pass | `text + CHECK` named; quantity is an integer in the type's unit (no money); 500-char limit on `basis_text` |
| O9 | pass | no derived values stored: credit, held, used, available are computed from lines |
| O10, O10a | pass | index list above; the composite FK column set is covered by the leading column of `uk_leave_policy_grants_account_request_key`, so no separate index (no redundant index) |
| O11 | pass | small tables |
| O12, T4 | pass | open period: the baseline file is edited and the expected diff is attached below; additive: one table, one nullable column, one redefined CHECK, one new CHECK, one redefined exclusion; no rows exist in `leave_requests` (no code writes it: verified), and the new table is empty, so every addition is safe in every environment; rollback = previous code ignores the new objects |
| O13 | pass | the fact rows carry no legal entity of their own: the entity is derived from the relationship (ADR-0086) and the events carry the cost entity on the date; an accounting period does not apply (no posting fact); the one entity-dependent number is the calendar of seam 3, resolved at read time |
| O14 | pass | business dates in `BusinessZone`; events dedupe by event id; lists by cursor; retention below |
| O15 | pass | ADR-0033 items 1, 2 (UUIDv7, no composite pk): included; ADR-0035 (event version 1): included; ADR-0043: classification below; ADR-0079 leave lines: unchanged; ADR-0086: relationship id keys: included |
| O16 | pass | quantities are `integer` in the type's declared unit (spec 3.2); API types carry the unit; no float |
| C1 | pass | the schema PR writes the table comment 「政策授予：一次经确认的额度授予决定，写一条授予流水，只追加……粒度：每次授予一行」 and column comments from the tables above |
| C2 | pass | one owner BC `organization.leave`; the registry row lists `writers: []` in slice A (no Java SQL touches the table yet; `DataArchitectureTest` fails otherwise) and the command slice C registers `[organization.leave]`; no new `module-dependencies.txt` line |
| C3 | pass | retention follows the ledger: kept with the employee record period (spec Q-3, decided) |
| C4 | pass | classification below; `basis_text` allowed content: the business basis of the grant; it must not state health, pregnancy or other special-category facts (a note rule) |
| C5 | pass | lineage `NEW`; no legacy source |
| T1 to T3 | pass | baseline: the schema without these objects; target: the objects above; transition: none |
| E1 | pass (payload signed; catalogue entry waits for the classes) | immutable records of ids, dates, counts, enums; no names; version 1 |
| E2, E3 | n/a | no read model; no migration-in |
| A1 to A8 | n/a | no analytical fact; A6 (judged for each fact row): `leave_policy_grants` carries the relationship, the leave type, the date and the operator; the ledger line carries the same via its account; the leave request and the confirmation line carry the relationship and period; no row carries a document number, and none is required because no posting document is produced |

### Classification (C4) to add to `backend/architecture/data-classification.txt`

```
organization_leave.leave_policy_grants.employment_relationship_id | INTERNAL | PI | 用途：额度所属的雇佣关系；个人信息 PI
organization_leave.leave_policy_grants.basis_text | INTERNAL | PI | 用途：授予依据，由人填写；个人信息 PI；不得写健康、孕产等信息
organization_leave.leave_policy_grants.granted_by | INTERNAL | PI | 用途：做出授予决定的操作人；个人信息 PI
organization_leave.leave_policy_grants.effective_date | INTERNAL | PI | 用途：授予生效日，随雇佣关系的假期额度；个人信息 PI
organization_leave.leave_requests.taken_until_at | INTERNAL | PI | 用途：销假后第一天未休的起点，随请假期间；个人信息 PI
```

`leave_requests.taken_until_at` is an instant of the leave period (sibling of `start_at` and `end_at`, registered PI): two of the five lines below (it and the grant's `effective_date`). Registry (`data-access-registry.yaml`): a row for `organization_leave.leave_policy_grants` with role `AGGREGATE_CHILD`, aggregate `LeaveBalanceAccount`, mutability `APPEND_ONLY`, retention as the ledger, lineage `NEW`, writers `[]` in slice A (the slice that writes the table, C, registers `[organization.leave]`), `fk_exceptions: {leave_type_id: carried by the composite foreign key fk_leave_policy_grants_account_relationship_type, which holds it equal to the type of its account}`.

## Expected diff for the schema PR (DG-3, section 1.4)

Table set: `leave_policy_grants` added (columns, FK, index, trigger above). `leave_ledger_lines`: `ck_leave_ledger_lines_source_kind` redefined (ten values), `ck_leave_ledger_lines_policy_grant_kind` added. `leave_requests`: column `taken_until_at`, `ck_leave_requests_taken_until_at`, `ex_leave_requests_live_period_overlap` redefined. `leave_policy_grants` also has `request_key` and its key. No other object changes.

## Unverified items (the dev card checks them; none changes a column)

1. The ledger guard trigger and the account guard accept the grant path as read (read in full on 2026-11-07: sealed accounts refuse lines, year accounts take no lot); the rule-version triggers (`guard_leave_rule_version_insert`, `guard_leave_entitlement_change`) were not read and must accept a version with no entitlement rows (proposal 11, item 2).
2. The permission code of `GrantPolicyCredit` and `RecallLeave` (Q-S3-4, Q-S3-5).
3. The approval process definition and numbering document type of a leave request.
4. The event-catalogue, audit and consumer guards run against the real classes in each slice; here they were not run.

## 数据设计审查

- 审查：one-design，2026-11-07，依据卡版本 origin/main（已合并的 organization_leave 基线）加本记录
- 判定：B1 B2 B3 B3a B4 B5 B6 B7 B8 O1 O2 O3 O4 O5 O6 O7 O8 O9 O10 O10a O11 O12 O13 O14 O15 O16 C1 C2 C3 C4 C5 T1 T2 T3 T4 E1 通过；E2 E3 A1–A8 不适用（理由见上表）
- 豁免与待用户决定：无；Q-S3-1 至 Q-S3-5 不影响本记录的表与列
- 结论：通过（仅限切片 A：一张表、两列、一条放宽的约束、一条新约束、四个事件载荷）
- 数据设计：通过（one-design，2026-11-07，依据已合并的 organization_leave 基线加本记录）
