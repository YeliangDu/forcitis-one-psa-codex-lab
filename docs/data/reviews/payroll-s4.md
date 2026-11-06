# Data design review: organization.payroll slice S4 (seven tables for the pay file, the allowance, the enrolment, the confirmed input, the settlement and the payment, two events)

Card: #1582 (design). Basis: `docs/architecture/proposals/payroll-s4.md` (the proposal, added by the PR that carries this record), `docs/architecture/proposals/organization-payroll.md` (the spec, sections 4 and 8), the merged schema `V20261105173000__organization_payroll_baseline.sql` and its records `organization-payroll-py0.md`, `organization-payroll-py3.md`, gate `docs/data/data-design-gate.md` appendix A. Signed by one-design in the form of `workforce-acting-duty.md`. Where this record differs from the proposal's section 5 the record governs.

Evidence marks: **verified** = read on origin/main on 2026-11-07 (migration, registry, classification file, `light.sh` run with these files); **unverified** = named in the last section.

## Scope

Signed: schema `organization_payroll`; seven new tables `compensation_profile_versions`, `compensation_allowance_versions`, `insurance_enrolments`, `payroll_confirmed_inputs`, `payroll_settlements`, `payroll_settlement_lines`, `payroll_disbursements`; the payloads of `PayrollApproved` and `PayrollDisbursementResultRegistered`; terms (`backend/glossary/proposed/payroll-s4.yaml`).

Not signed (separate gate runs): posting columns and tables (`posting_status`, posting and payables submissions), contribution bases, special deductions, withholding filings, the history import tables, rule rows (no values), pay changes after the first version. The statutory amounts are external confirmed input (proposal 4.2): no rate, base or limit is stored. The user's open questions of proposal section 10 change no column except where stated there (Q-S4-7: a deduction split would add a table later).

## Tables (grain, columns, constraints)

Common to all seven: `id uuid` primary key (UUIDv7, `pk_<table>`); `created_at`, `updated_at` `timestamptz NOT NULL` (on an append-only table the comment of `updated_at` reads 「更新时刻（只追加表，触发器拒绝更新，列只为满足通用审计列约定）」); amounts `numeric(19,2)` paired with a `currency` column `CHECK (currency = 'CNY')` (rounding is not done by the database); dates are business dates (`BusinessZone`); every enum is `text` with a named CHECK; every free text has `char_length` and `btrim` CHECKs; every nullable column's comment states 「为空表示」; cross-context ids (`employment_relationship_id`, `paying_legal_entity_id`, `employee_id`, user ids) carry no foreign key.

### compensation_profile_versions (grain: one agreed base pay of one relationship from one date)

| Column | Type | Constraints and notes |
|---|---|---|
| `employment_relationship_id` | uuid NOT NULL | the pay belongs to the relationship (ADR-0086 decision 5) |
| `effective_from_date` | date NOT NULL | the version in force on a day is the one with the latest `effective_from_date` not after it; no end column (a successor supersedes, so no update is needed); `uk_compensation_profile_versions_relationship_from` UNIQUE `(employment_relationship_id, effective_from_date)` |
| `base_monthly_amount` | numeric(19,2) NOT NULL | `ck_..._base_monthly_amount` `> 0` |
| `base_monthly_currency` | text NOT NULL | `= 'CNY'` |
| `paying_legal_entity_id` | uuid NOT NULL | resolved at creation: the relationship's employer on `effective_from_date`, or the override |
| `paying_entity_reason_key` | text | 为空表示 the paying entity is the relationship's employer; `ck_..._paying_entity_reason_key` pattern `^[A-Z][A-Z0-9_]{1,39}$` |
| `decision_reference_text` | text NOT NULL | the number of the approved pay decision, cited by the payroll specialist (Q-S4-6); length 1 to 100, btrim |
| `created_by_user_id` | uuid NOT NULL | who entered it |

Append-only by a guard trigger (`BEFORE UPDATE OR DELETE` and `BEFORE TRUNCATE`, the pattern of `guard_append_only` in `organization_leave`). `ix_compensation_profile_versions_entity` on `(paying_legal_entity_id)` for the scope filter.

### compensation_allowance_versions (grain: one allowance of one relationship for one period)

| Column | Type | Constraints and notes |
|---|---|---|
| `employment_relationship_id` | uuid NOT NULL | |
| `paying_legal_entity_id` | uuid NOT NULL | resolved as above |
| `allowance_key` | text NOT NULL | the kind, data not code (C-5): pattern `^[A-Z][A-Z0-9_]{1,39}$` |
| `allowance_amount`, `allowance_currency` | numeric(19,2), text NOT NULL | `> 0`, `= 'CNY'` |
| `effective_from_date` | date NOT NULL | |
| `effective_to_date` | date | 为空表示 no end date set yet; `ck_..._period` `effective_to_date IS NULL OR effective_to_date > effective_from_date` |
| `decision_reference_text` | text NOT NULL | the allowance decision number (the pack: decided elsewhere); there is no column for qualification evidence on purpose (proposal 4.1) |
| `created_by_user_id` | uuid NOT NULL | |

`ex_compensation_allowance_versions_period_overlap` `EXCLUDE USING gist (employment_relationship_id WITH =, allowance_key WITH =, daterange(effective_from_date, effective_to_date) WITH &&)` (a null end is an open range; every range is non-empty by the period CHECK, so two versions of one kind with the same start already overlap: no separate unique key). The extension `btree_gist` is already guaranteed by the existence check at the top of the payroll baseline (its `DO` block, used by the rule-version exclusion) [V]; no extension change is needed. Guard trigger: the only legal update sets `effective_to_date` from null to a date and `updated_at`, once; every other update, any delete and truncate are refused.

### insurance_enrolments (grain: one enrolment status of one kind of one relationship from one date)

| Column | Type | Constraints and notes |
|---|---|---|
| `employment_relationship_id` | uuid NOT NULL | per relationship (ADR-0086 decision 5; a re-hire is a new relationship) |
| `employer_legal_entity_id` | uuid NOT NULL | the relationship's employer on the effective date (insurance follows the employer, spec 3) |
| `insurance_kind` | text NOT NULL | data not code: pattern `^[A-Z][A-Z0-9_]{1,39}$` (the kinds come with the statute and the user's list) |
| `enrolment_status` | text NOT NULL | `IN ('ENROLLED', 'NOT_ENROLLED_HERE', 'STOPPED')`; "not enrolled here" is a status, absence of a row means unknown (spec 4.3) |
| `effective_from_date` | date NOT NULL | the status in force on a day is the latest row not after it; `uk_insurance_enrolments_relationship_kind_from` UNIQUE `(employment_relationship_id, insurance_kind, effective_from_date)` |
| `decision_reference_text` | text NOT NULL | the decision number of the plan |
| `created_by_user_id` | uuid NOT NULL | |

Append-only by the guard trigger. No contribution base column in S4 (proposal section 5, item 4).

### payroll_confirmed_inputs (grain: the confirmed gross and deduction of one relationship for one month)

| Column | Type | Constraints and notes |
|---|---|---|
| `employment_relationship_id`, `paying_legal_entity_id` | uuid NOT NULL | |
| `payroll_month_start_date` | date NOT NULL | `EXTRACT(DAY FROM ...) = 1` |
| `gross_amount`, `deduction_amount`, `amount_currency` | numeric(19,2), numeric(19,2), text NOT NULL | `gross_amount >= 0`, `deduction_amount >= 0`, `deduction_amount <= gross_amount`, currency `'CNY'`; net is not stored (derivable) |
| `source_kind` | text NOT NULL | `IN ('EXTERNAL_CONFIRMED')` |
| `decision_reference_text` | text NOT NULL | the number of the confirmation (Q-S4-2) |
| `entered_by_employee_id` | uuid NOT NULL | the person who entered it (for the audit trail); the separation of duties compares the settlement's approver with `calculated_by_user_id`, not with this column |
| `status` | text NOT NULL | `IN ('ACTIVE', 'VOIDED')` |
| `voided_by_user_id`, `voided_at` | uuid, timestamptz | 为空表示 not voided; `ck_..._void_columns` `(status = 'VOIDED') = (voided_at IS NOT NULL) AND (voided_at IS NULL) = (voided_by_user_id IS NULL)` |

`uk_payroll_confirmed_inputs_relationship_month` UNIQUE partial `(employment_relationship_id, payroll_month_start_date) WHERE status = 'ACTIVE'` (a void frees the key). Guard trigger: insert as `ACTIVE`; the only update is `ACTIVE` to `VOIDED` changing status, void columns and `updated_at` (the pattern of `guard_bonus_input_change` [V]); no delete, no truncate.

### payroll_settlements (grain: one payroll of one paying entity for one month)

| Column | Type | Constraints and notes |
|---|---|---|
| `paying_legal_entity_id` | uuid NOT NULL | |
| `payroll_month_start_date` | date NOT NULL | day 1 |
| `status` | text NOT NULL | `IN ('CALCULATED', 'IN_REVIEW', 'APPROVED', 'CANCELLED')`; the spec's `DRAFT` is not written by any S4 command (the calculation inserts `CALCULATED`), so it is not in the CHECK; `CANCELLED` is reachable from `CALCULATED` only |
| `approval_instance_id` | uuid | 为空表示 not yet submitted for review; `ck_..._in_review_has_instance` `status NOT IN ('IN_REVIEW', 'APPROVED') OR approval_instance_id IS NOT NULL`; `uk_payroll_settlements_approval_instance_id` UNIQUE |
| `calculated_by_user_id` | uuid NOT NULL | the user who ran the calculation (the row is inserted by it); the separation-of-duties input |
| `total_gross_amount`, `total_deduction_amount`, `total_net_amount`, `total_currency` | numeric(19,2) x3, text, all NOT NULL | set by the calculation that inserts the row, with the reason in O9; currency `'CNY'` |
| (constraint) | | `ck_payroll_settlements_total_net` `total_net_amount = total_gross_amount - total_deduction_amount` |
| `payment_status` | text NOT NULL | `IN ('NOT_REQUESTED', 'REQUESTED', 'PAID', 'PARTLY_FAILED')`; `ck_..._payment_after_approval` `payment_status = 'NOT_REQUESTED' OR status = 'APPROVED'` |
| `version` | bigint NOT NULL DEFAULT 0 | optimistic lock (`If-Match`) |

`uk_payroll_settlements_entity_month` UNIQUE partial `(paying_legal_entity_id, payroll_month_start_date) WHERE status <> 'CANCELLED'` (the business key: a settlement is unique within a paying entity and month). Guard trigger: once `APPROVED`, only `payment_status`, `updated_at` and `version` may change: `status` is terminal (an approved settlement cannot become `CANCELLED` or return to `CALCULATED`, so its (entity, month) key is never freed), and the amount columns, `payroll_month_start_date`, `paying_legal_entity_id`, `calculated_by_user_id` and `approval_instance_id` never change; `payroll_settlements` rows are never deleted. The legal moves of `status` are `CALCULATED` to `IN_REVIEW` or `CANCELLED`, `IN_REVIEW` to `CALCULATED` (rejected or withdrawn) or `APPROVED`; `CANCELLED` is terminal. `payment_status`: `NOT_REQUESTED` to `REQUESTED` at approval's payment request; `REQUESTED` to `PAID` or `PARTLY_FAILED` when no attempt of the settlement is open (any `FAILED` line gives `PARTLY_FAILED`, including all lines failed); `PARTLY_FAILED` to `REQUESTED` by a re-disbursement (a re-disbursement while other attempts are still open leaves `REQUESTED` unchanged).

### payroll_settlement_lines (grain: the pay of one relationship in one settlement)

| Column | Type | Constraints and notes |
|---|---|---|
| `payroll_settlement_id` | uuid NOT NULL | `fk_payroll_settlement_lines_payroll_settlement_id` to `payroll_settlements`, RESTRICT; no separate index (the unique key below leads with it) |
| `employment_relationship_id`, `employee_id` | uuid NOT NULL | the person is carried for the own-payslip read |
| `compensation_profile_version_id` | uuid NOT NULL | FK to `compensation_profile_versions`, RESTRICT: the evidence of the base pay in force on the month's last day; `ix_...` on it |
| `payroll_confirmed_input_id` | uuid NOT NULL | FK to `payroll_confirmed_inputs`, RESTRICT; `ix_...` on it |
| `gross_amount`, `deduction_amount`, `net_amount`, `amount_currency` | numeric(19,2) x3, text | copied at calculation; `ck_..._net_equals_gross_less_deduction`; currency `'CNY'` |
| `adjustment_of_month_start_date` | date | 为空表示 not an adjustment of an earlier month; day 1 when set (the correction route after approval, spec C-8; not written in S4) |

`uk_payroll_settlement_lines_settlement_relationship_adjustment` UNIQUE `(payroll_settlement_id, employment_relationship_id, adjustment_of_month_start_date)` NULLS NOT DISTINCT. A line trigger refuses INSERT, UPDATE and DELETE unless the parent settlement is `CALCULATED` (so a line cannot change under `IN_REVIEW`, `APPROVED` or `CANCELLED`); lines of a `CALCULATED` settlement are replaced as a set by a recalculation.

### payroll_disbursements (grain: one payment attempt of one settlement line)

| Column | Type | Constraints and notes |
|---|---|---|
| `payroll_settlement_line_id` | uuid NOT NULL | FK RESTRICT; `ix_...` on it |
| `disbursement_status` | text NOT NULL | `IN ('REQUESTED', 'PAID', 'FAILED')` |
| `payment_reference_text` | text | 为空表示 no reference reported yet (the stub or the later payables module reports it) |
| `paid_on_date` | date | 为空表示 not paid; `ck_..._paid_columns` `(disbursement_status = 'PAID') = (paid_on_date IS NOT NULL)` |
| `failure_reason_key` | text | 为空表示 not failed; pattern as the other keys; `(disbursement_status = 'FAILED') = (failure_reason_key IS NOT NULL)` |
| `redisbursement_of_id` | uuid | 为空表示 not a re-disbursement; written by `InitiatePayrollRedisbursement`; self-FK `fk_payroll_disbursements_redisbursement_of_id`, RESTRICT; `ix_...` on it |
| `result_idempotency_key` | text | 为空表示 no result registered yet; `uk_payroll_disbursements_result_idempotency_key` UNIQUE (a replayed result is a no-op) |
| `version` | bigint NOT NULL DEFAULT 0 | |

Rows are inserted `REQUESTED` by the payment request (at approval) and by `InitiatePayrollRedisbursement` (which links `redisbursement_of_id` to a `FAILED` row); a row moves `REQUESTED` to `PAID` or `FAILED` once (guard trigger: terminal states never change, no delete). `uk_payroll_disbursements_line_open` UNIQUE partial `(payroll_settlement_line_id) WHERE disbursement_status IN ('REQUESTED', 'PAID')`: a line has at most one live or paid attempt; a failed one frees the key for the re-disbursement.

## Event payloads (E1)

Both events: publisher `organization.payroll`, audited, version 1; payload fields are ids, dates, enums and the three entity totals; **no per-person amount, no name** (spec P-R5). Envelope `legalEntityId` = the paying entity; `businessDate` = the last day of the payroll month.

| Field | `PayrollApproved` | `PayrollDisbursementResultRegistered` |
|---|---|---|
| aggregate / `aggregateId` | `PayrollSettlement` / settlement id | same |
| `payingLegalEntityId` | required | required |
| `payrollMonth` (first day of the month; the envelope `businessDate` is the last day) | required | required |
| `lineCount` | required | required (lines of the settlement) |
| `totalGrossAmount`, `totalDeductionAmount`, `totalNetAmount` (decimal string, currency CNY) | required | n/a |
| `paymentStatus` | n/a | required: `PAID` / `PARTLY_FAILED` (the settlement's `payment_status` after the change; never `REQUESTED`) |
| `paidLineCount`, `failedLineCount` | n/a | required (`paidLineCount + failedLineCount = lineCount`, each line counted by its latest attempt) |

**Firing rule of `PayrollDisbursementResultRegistered`:** settlement scope, once each time a registered result leaves the settlement with no open (`REQUESTED`) attempt, i.e. when `payment_status` becomes `PAID` or `PARTLY_FAILED`; a result that leaves attempts open fires nothing; a replayed result (same idempotency key) is a no-op and fires nothing; a re-disbursement that settles fires again with the new counts. Consumers de-duplicate on the envelope event id. Consumers now: `platform.audit` for both. Posting, filing and payables consumers are later slices (proposal 6). **Why the catalogue file is not in this PR:** `EventCatalogueTest` fails when the catalogue names an event class absent from the code (the rule of #1525); the slice that adds each class adds its entry from this table in the same PR.

## Invariants to constraints (B5, O4)

| Invariant | Carrier | Reason when no constraint |
|---|---|---|
| A settlement is unique per paying entity and month; a cancelled one frees the key | `uk_payroll_settlements_entity_month` (partial) | |
| Net equals gross less deduction, per line and per settlement | `ck_payroll_settlement_lines_net_equals_gross_less_deduction`, `ck_payroll_settlements_total_net` | |
| An approved settlement's amounts and lines never change | guard triggers on both tables | |
| A line has the profile version and the confirmed input it was calculated from | FKs, RESTRICT | |
| Totals of a settlement equal the sum of its lines | command, integration test | spans two tables |
| A line belongs to the paying entity of its settlement | command, integration test | spans two tables (the entity is on the settlement only) |
| A profile, allowance, enrolment, input is for a relationship in service on its date | command | needs the workforce read |
| The paying entity default is the relationship's employer on the date | command | needs the workforce read (`employerOn`) |
| One allowance of a kind per period, no overlap | `ex_compensation_allowance_versions_period_overlap` | |
| One active confirmed input per relationship and month | partial unique key | |
| The approver is not the calculator | command, integration test | the approver's user id arrives with `ApprovalCompleted` and is compared with `calculated_by_user_id`; no column needed |
| An approved settlement keeps its status and its lines | settlement guard (status terminal) and the line trigger (parent must be `CALCULATED`) | |
| A calculation needs every relationship of the paying entity's gate set locked | command | the gate set is the in-service relationships (workforce listing) whose profile in force on the month's last day names the entity, plus employer-owned ones with no profile (they fail the calculation); needs the S3 lock read |
| A payment result applies once | `uk_payroll_disbursements_result_idempotency_key` and the terminal-state guard | |
| One live or paid payment attempt per line | partial unique key | |

## Use cases to tables (O1, O10, O10a)

| Use case | Writes | Reads by | Volume |
|---|---|---|---|
| `CreateCompensationProfile`, `EnrolInsurance`, `CreateAllowanceVersion`, `EndAllowanceVersion` | one row (or one update of the end date) in its table, one transaction each | `(employment_relationship_id, effective_from_date)` keys | a few per person per year |
| `RecordConfirmedPayrollInput`, `VoidConfirmedPayrollInput` | one row or one void | the partial unique key; `ix_payroll_confirmed_inputs_entity_month` on `(paying_legal_entity_id, payroll_month_start_date)` | one per person per month |
| `CalculateMonthlyPayroll` | settlement and its lines in one transaction (one aggregate) | the profile, confirmed-input, workforce and S3 reads | one settlement per entity per month, one line per relationship |
| `SubmitPayrollForReview`, `ApproveMonthlyPayroll` | settlement status (and `approval_instance_id`) | the settlement key | |
| `CancelPayrollSettlement` | settlement `status` to `CANCELLED` (from `CALCULATED`) | the settlement key | rare |
| `RegisterPayrollDisbursementResult`, `InitiatePayrollRedisbursement` | disbursement rows (a new row for a re-disbursement), settlement `payment_status` | the idempotency key; `ix_` on the line id | one per line |
| Own payslip read | none | line by `employee_id`: `ix_payroll_settlement_lines_employee_id` and the settlement id | a few thousand lines per month for the firm |

## Checks

| # | Result | Evidence |
|---|---|---|
| B1, B2 | pass | tables above; the registry rows (below) name the aggregates `CompensationProfile` (AGG-93) for `compensation_profile_versions`, `CompensationAllowance` for `compensation_allowance_versions`, `InsuranceEnrolment` (AGG-74) for `insurance_enrolments`, `PayrollConfirmedInput` for `payroll_confirmed_inputs`, `PayrollSettlement` (AGG-73) for the settlement and its two child tables |
| B3, B3a | pass | each status CHECK = the Java enum of the slice that writes it (G-D12); the settlement states are the spec's (4.6) minus `DRAFT` and the posting columns; every event maps to one transition: approve, register result |
| B4 | pass | foreign keys only inside `organization_payroll` (lines to settlements, profile versions, confirmed inputs; disbursements to lines and themselves); cross-context ids stored without FK |
| B5, O4 | pass | invariants table |
| B6 | pass | produced facts: the seven tables' rows, the two events |
| B7 | pass | see scope |
| B8 | pass | terms in `backend/glossary/proposed/payroll-s4.yaml`; registered terms used as registered; UI wording stays with the user |
| O1 | pass | every use case writes one aggregate in one transaction (a settlement with its lines; a disbursement result with its settlement marker is one aggregate, `PayrollSettlement`, the result command loads the settlement) |
| O2 | pass | UUIDv7 pks, UUID references |
| O3 | pass | business unique keys: profile `(relationship, effective date)`; allowance: the period exclusion (it also refuses the same start); enrolment `(relationship, kind, effective date)`; confirmed input `(relationship, month)` while active; settlement `(paying entity, month)` while not cancelled; line `(settlement, relationship, adjustment month)`; disbursement idempotency key and one live attempt per line |
| O5 | pass | append-only versions need no `version`; the settlement and the disbursement keep theirs |
| O6 | pass | categories: facts (versions, enrolments, inputs, lines, disbursements) with temporal basis `DECLARED` (the effective date is entered); settlement is a process row |
| O7 | pass | append-only or guarded transitions; FK RESTRICT; no `is_deleted` |
| O8 | pass | `text + CHECK` named; money `numeric(19,2)` with a currency column; no float; status and key patterns as the merged tables |
| O9 | pass | stored derived values and the reason: the line's `net_amount` and the settlement totals are frozen facts of an approved payroll (the approved amount must not move when a source row is later voided), guarded by CHECKs and the settlement trigger |
| O10, O10a | pass | index list above; every foreign-key column set has an index or a leading unique key (`payroll_settlement_id` by the line unique key, `compensation_profile_version_id`, `payroll_confirmed_input_id`, `payroll_settlement_line_id`, `redisbursement_of_id`); no redundant index (the settlement key covers `paying_legal_entity_id` plus month; the line unique key leads with `payroll_settlement_id`, so no separate index on it; the entity index on the profile table serves the scope filter) |
| O11 | pass | small tables; volumes above |
| O12, T4 | pass | open period: the baseline file is edited and the expected diff is attached below; additive: seven new tables, no existing object changes; the three merged tables are untouched; rollback = previous code ignores the new tables |
| O13 | pass | every table carries the paying (or employer) legal entity or inherits it from its parent (lines from the settlement, disbursements from the line); no accounting period: no posting fact in S4 |
| O14 | pass | business dates in `BusinessZone`; the result command dedupes by its idempotency key; lists by cursor; retention below |
| O15 | pass | ADR-0033 items 1, 2: included; ADR-0035 (event version 1): included; ADR-0043: classification below; ADR-0079 payroll lines: unchanged; ADR-0086: relationship id keys and the paying entity: included; ADR-0087 decisions 1 to 5: unchanged |
| O16 | pass | money as `numeric(19,2)` and the API money shape `{amount, currency}`; rounding is the money kernel's; no float |
| C1 | pass | the schema PR writes a table comment 「<名>：<含义>；粒度：<每…一行>；只追加/受守卫」 and a column comment for every column from the tables above |
| C2 | pass | one owner BC `organization.payroll`; registry rows list `writers: []` in slice A (no Java SQL touches the tables yet; `DataArchitectureTest` fails otherwise) and each command slice registers `[organization.payroll]` for its tables; `fk_exceptions` are not needed because no `*_id` column other than the listed in-schema FKs points at a table of this schema (`DataArchitectureTest.d6` asks only for same-schema targets; the merged `bonus_inputs` row has none); new module edges are added by the slices that use them (proposal 5, item 6) |
| C3 | pass | retention follows the signed pay rule: ten years after the person's last separation, removed by the purge route (user 2026-11-05 P-9 a); the purge function does not cover these tables yet, as for `bonus_inputs` [V: registry comment] |
| C4 | pass | classification below |
| C5 | pass | lineage `NEW` for all seven; the legacy payroll is imported by the history slice, not by these tables |
| T1 to T3 | pass | baseline: the schema without these tables; target: the tables above; transition: none |
| E1 | pass (payload signed; catalogue entry waits for the classes) | immutable records of ids, dates, enums and totals; no names; version 1 |
| E2, E3 | n/a | no read model; no migration-in |
| A1 to A8 | n/a | no analytical fact; A6 (judged for each fact row): profile version, allowance version, enrolment and confirmed input carry the relationship, the entity and the decision number; the line carries the relationship, the person, the settlement and the source rows; the disbursement carries the line and the payment reference; no row carries a document number, and none is required because no posting document is produced in S4 |

### Classification (C4) to add to `backend/architecture/data-classification.txt`

One line per non-surrogate column, in the form of the merged `bonus_inputs` lines (pay amounts `SENSITIVE | PI` as the bonus amount is [V]; ids, dates, status and keys `INTERNAL`; decision numbers and reason keys that identify a person's pay decision `SENSITIVE | PI` as `bonus_inputs.reason_key` is [V]; the paying and employer entity ids `INTERNAL | NONE`; currency `INTERNAL | NONE`). The schema PR writes the Chinese 用途 text per column; the table of classes:

| Table | SENSITIVE and PI columns | INTERNAL and PI columns | INTERNAL and NONE columns |
|---|---|---|---|
| `compensation_profile_versions` | `base_monthly_amount`, `decision_reference_text`, `paying_entity_reason_key` | `employment_relationship_id`, `effective_from_date`, `created_by_user_id` | `base_monthly_currency`, `paying_legal_entity_id` |
| `compensation_allowance_versions` | `allowance_amount`, `allowance_key`, `decision_reference_text` | `employment_relationship_id`, `effective_from_date`, `effective_to_date`, `created_by_user_id` | `allowance_currency`, `paying_legal_entity_id` |
| `insurance_enrolments` | `enrolment_status`, `decision_reference_text` | `employment_relationship_id`, `insurance_kind`, `effective_from_date`, `created_by_user_id` | `employer_legal_entity_id` |
| `payroll_confirmed_inputs` | `gross_amount`, `deduction_amount`, `decision_reference_text` | `employment_relationship_id`, `entered_by_employee_id`, `voided_by_user_id` | `payroll_month_start_date`, `amount_currency`, `source_kind`, `status`, `voided_at`, `paying_legal_entity_id` |
| `payroll_settlements` | `total_gross_amount`, `total_deduction_amount`, `total_net_amount` | `calculated_by_user_id`, `approval_instance_id` | `paying_legal_entity_id`, `payroll_month_start_date`, `status`, `payment_status`, `total_currency` |
| `payroll_settlement_lines` | `gross_amount`, `deduction_amount`, `net_amount` | `employment_relationship_id`, `employee_id`, `adjustment_of_month_start_date` | `amount_currency` and the three line-level ids |
| `payroll_disbursements` | none | `payment_reference_text`, `paid_on_date` | `disbursement_status`, `failure_reason_key`, `result_idempotency_key` and the line ids |

Registry (`data-access-registry.yaml`): a row per table with role `AGGREGATE_ROOT` (`payroll_settlements`, and the three version tables each as the root of its append-only versions) or `AGGREGATE_CHILD` (`payroll_settlement_lines`, `payroll_disbursements`), mutability `APPEND_ONLY` for `compensation_profile_versions` and `insurance_enrolments`, `CONDITIONAL_UPDATE` for `payroll_confirmed_inputs` (void only), `compensation_allowance_versions` (first end date only), `payroll_settlements` (status moves and `payment_status`) and `payroll_disbursements` (`REQUESTED` to a terminal state once), each with a `condition` text as the `bonus_inputs` row has, retention and lineage as stated, writers `[]` in slice A.

## Expected diff for the schema PR (DG-3, section 1.4)

Table set: seven tables added with the columns, constraints, indexes and guard triggers above; no existing object changes; the baseline file `V20261105173000__organization_payroll_baseline.sql` is edited in place (open period).

## Unverified items (the dev card checks them; none changes a column)

1. The role code and permission names of the settlement approver (Q-S4-5) and of the payroll pages.
2. That `employerOn` answers for a relationship starting in the pay month (proposal 11, item 1).
3. The approval process definition and numbering document type of a settlement; whether `PayrollApprovers` serves.
4. The guard triggers' exact bodies are written by the schema slice from the patterns named here (`guard_bonus_input_change`, `guard_append_only`); they were read for the pattern, not copied.
5. The event-catalogue, audit and consumer guards run against the real classes in each slice; here they were not run.

## 数据设计审查

- 审查：one-design，2026-11-07，依据卡版本 origin/main（已合并的 organization_payroll 基线）加本记录
- 判定：B1 B2 B3 B3a B4 B5 B6 B7 B8 O1 O2 O3 O4 O5 O6 O7 O8 O9 O10 O10a O11 O12 O13 O14 O15 O16 C1 C2 C3 C4 C5 T1 T2 T3 T4 E1 通过；E2 E3 A1–A8 不适用（理由见上表）
- 豁免与待用户决定：无；Q-S4-1 至 Q-S4-9 不改变本记录的表与列（Q-S4-7 若选「拆分扣除项」，由后续公式切片另走数据设计）
- 结论：通过（仅限切片 A：七张新表、两个事件载荷）
- 数据设计：通过（one-design，2026-11-07，依据已合并的 organization_payroll 基线加本记录）
