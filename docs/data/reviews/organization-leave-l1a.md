# Data design review: organization.leave slice L1a (seven tables)

Card: #1400 (impl, one-dev-identity); design card #1325. Basis: `docs/architecture/proposals/organization-leave.md` sections 4.3 to 4.6, 6, 8 at commit `bb2e2ce1d` (PR #1398 merged), gate `docs/data/data-design-gate.md` appendix A. Signed by one-design (the leave design says the data design is signed by design for this repo; `data-design-gate.md` names one-data, so this record states the form used). Naming follows `docs/rules/naming-standard.md` 2.4 and 2.4a, which is why three design column names change (listed in "Differences from the section 8 sketch").

## Scope

Schema `organization_leave`, seven tables: `leave_requests`, `leave_request_days`, `leave_request_holds`, `overtime_requests`, `attendance_records`, `monthly_confirmations`, `monthly_confirmation_lines`. Not signed here and not to be built by #1400: `leave_types`, `leave_rule_versions`, `leave_entitlement_rules`, `leave_accounts`, `leave_ledger_lines`, `leave_grant_basis` (slice L1b, after the legacy values of T-1, T-3, T-5, T-7, T-8, T-10).

**Carved out of this signature:** `leave_requests.reason_text` and `leave_requests.proof_file_id`. They hold free text and a file reference that can carry health information (sick leave). Marking them SPI needs a legal confirmation (U-14) and an impact-assessment file under the privacy folder named by the classification header, which does not exist yet; the gate allows no conditional pass (`data-design-gate.md` 1.3) and a decision of that kind is not mine. They are signed later, in their own record, after the user decides; until then #1400 builds `leave_requests` without them and the leave form carries no reason or proof field.

Two columns point at L1b tables of the same schema: `leave_requests.leave_type_id`, `monthly_confirmation_lines.leave_type_id` (to `leave_types`) and `leave_request_holds.ledger_line_id` (to `leave_ledger_lines`). They are plain `uuid NOT NULL` columns in L1a, without a foreign key, because the target tables do not exist. The L1b migration adds each foreign key (and, for the two `leave_type_id` columns, an index starting with that column, rule G-DDL2) (`fk_leave_requests_leave_type_id`, `fk_monthly_confirmation_lines_leave_type_id`, `fk_leave_request_holds_ledger_line_id`, `ON DELETE RESTRICT`) in the same change that creates the target table; the three tables are empty until a later slice writes them, so the key can be added without `NOT VALID`. This is a registered deferral, not a gap: the registry carries a `references` entry for each (K6).

Relationship ids (`employment_relationship_id`) and person ids (`employee_id`), `legal_entity_id`, `approval_instance_id` belong to other contexts: stored as `uuid`, no foreign key (cross-BC rule B4, ADR-0086).

## Table to object source (B1, B2)

| Table | Source | Aggregate | Registry role |
|---|---|---|---|
| `leave_requests` | AGG-70 request data (4.3) | LeaveRequest | `AGGREGATE_ROOT`, `MUTABLE` |
| `leave_request_days` | AGG-70 "minutes per day" (3.2, 4.3) | LeaveRequest | `AGGREGATE_CHILD`, `REPLACE_SET` (written at submit; a `DRAFT` request may be re-split) |
| `leave_request_holds` | AGG-70 "balance hold id", one row per HOLD line (4.3, section 8) | LeaveRequest | `AGGREGATE_CHILD`, `APPEND_ONLY` |
| `overtime_requests` | AGG-72 (4.4) | OvertimeRequest | `AGGREGATE_ROOT`, `MUTABLE` |
| `attendance_records` | AGG-69 (4.5) | AttendanceRecord | `AGGREGATE_ROOT`, `APPEND_ONLY` |
| `monthly_confirmations` | AGG-97 (4.6) | MonthlyConfirmation | `AGGREGATE_ROOT`, `MUTABLE` until `LOCKED` |
| `monthly_confirmation_lines` | AGG-97 lines (4.6) | MonthlyConfirmation | `AGGREGATE_CHILD`, `REPLACE_SET` while `GENERATED` |

Audit columns: `created_at`, `updated_at` on every mutable or replaceable table; `version bigint NOT NULL DEFAULT 0` on the three roots that carry optimistic locking (`leave_requests`, `overtime_requests`, `monthly_confirmations`); append-only tables (`leave_request_holds`, `attendance_records`) carry `created_at` and `updated_at` too, because `DdlNamingTest` requires both on every table; on these two `updated_at` is set equal to `created_at` on insert and never changed (their update-blocking trigger already forbids any update). Amended 2026-11-05 on a question from the L1a card (#1412); the first version of this record wrongly gave `created_at` only. All keys are UUIDv7 `id`, primary key `pk_<table>`.

## Columns and constraints

Every enum is `text` with a named `ck_<table>_<column>` CHECK; every free-text column has `char_length` and `btrim` CHECKs; every nullable column's comment states 「为空表示」.

### leave_requests

| Column | Type | Rule |
|---|---|---|
| `id` | uuid | pk |
| `employment_relationship_id` | uuid NOT NULL | the relationship the leave is taken from [R]; no foreign key |
| `employee_id` | uuid NOT NULL | denormalised, data-scope check only; no foreign key |
| `leave_type_id` | uuid NOT NULL | foreign key added in L1b |
| `start_at`, `end_at` | timestamptz NOT NULL | `ck_leave_requests_period` `end_at > start_at` |
| `status` | text NOT NULL | `DRAFT`, `SUBMITTED`, `IN_APPROVAL`, `APPROVED`, `REJECTED`, `WITHDRAWN`, `RECALLED`, `CANCELLED` (4.3) |
| `approval_instance_id` | uuid null | 为空表示 no approval process started (draft, auto-approved, or rejected before routing) |
| `legal_entity_id` | uuid NOT NULL | cost (attribution) entity of the person on the start day (RULE-398 scope), not the employer |
| `created_at`, `updated_at`, `version` | | audit |

Constraints: `ex_leave_requests_live_period_overlap` `EXCLUDE USING gist (employment_relationship_id WITH =, tstzrange(start_at, end_at) WITH &&) WHERE (status IN ('SUBMITTED','IN_APPROVAL','APPROVED'))` (needs the `btree_gist` extension, which the workforce, administration and attendance baselines already create); `ck_leave_requests_in_approval_has_instance` `status <> 'IN_APPROVAL' OR approval_instance_id IS NOT NULL`; `uk_leave_requests_approval_instance_id` unique where `approval_instance_id IS NOT NULL` (one request per approval process, so a replayed `ApprovalCompleted` finds one row).

### leave_request_days

`id`; `leave_request_id uuid NOT NULL` (`fk_leave_request_days_leave_request_id`, RESTRICT); `work_date date NOT NULL`; `minutes_count integer NOT NULL` (`ck_leave_request_days_minutes_count` 0 to 1440; 0 for a non-workday, 3.2); `day_hundredths_count integer NOT NULL` (`ck_leave_request_days_day_hundredths_count` 0 to 100; hundredths of a day for `DAY`-unit types); `created_at`, `updated_at`. `uk_leave_request_days_request_work_date` (`leave_request_id`, `work_date`).

### leave_request_holds

`id`; `leave_request_id uuid NOT NULL` (`fk_leave_request_holds_leave_request_id`, RESTRICT); `ledger_line_id uuid NOT NULL` (foreign key added in L1b); `created_at`, `updated_at` (equal, line 27). `uk_leave_request_holds_request_line` (`leave_request_id`, `ledger_line_id`); `uk_leave_request_holds_ledger_line_id` (`ledger_line_id`): one HOLD line belongs to one request. A request spanning two years or several lots keeps one row per line. Append-only trigger rejects update and delete.

### overtime_requests

| Column | Type | Rule |
|---|---|---|
| `id` | uuid | pk |
| `employment_relationship_id` | uuid NOT NULL | the relationship the overtime is worked for [R], so `GrantCompTime` has one target; no foreign key |
| `employee_id` | uuid NOT NULL | scope check; no foreign key |
| `work_date` | date NOT NULL | |
| `minutes_count` | integer NOT NULL | `ck_overtime_requests_minutes_count` 1 to 1440 |
| `day_kind` | text NOT NULL | `WORKDAY`, `REST_DAY`, `STATUTORY_HOLIDAY` (the day type the overtime falls on, 4.4 "limited by day type") |
| `compensation_kind` | text NOT NULL | `PAY`, `COMP_TIME` |
| `status` | text NOT NULL | `REQUESTED`, `IN_APPROVAL`, `APPROVED`, `COMPENSATED_PAY`, `CREDITED_AS_COMP_TIME`, `REJECTED`, `WITHDRAWN` (4.4) |
| `approval_instance_id` | uuid null | 为空表示 no approval process started |
| `legal_entity_id` | uuid NOT NULL | cost entity on the day |
| `created_at`, `updated_at`, `version` | | audit |

Constraints: `ck_overtime_requests_holiday_pay_only` `day_kind <> 'STATUTORY_HOLIDAY' OR compensation_kind = 'PAY'` (RULE-517); `ck_overtime_requests_status_matches_compensation` `(status = 'COMPENSATED_PAY') <= (compensation_kind = 'PAY') AND (status = 'CREDITED_AS_COMP_TIME') <= (compensation_kind = 'COMP_TIME')`; `ck_overtime_requests_in_approval_has_instance` as above; `uk_overtime_requests_approval_instance_id` partial unique.

### attendance_records

`id`; `employee_id uuid NOT NULL`; `record_kind text NOT NULL` (`PUNCH`, `OUTFIELD`, `MAKE_UP`; a make-up punch is a separate record, RULE-519); `occurred_at timestamptz NOT NULL`; `source_system text NOT NULL` (format CHECK `~ '^[A-Z][A-Z0-9_]{1,31}$'`; the value list is added by the import slice once the source system is decided, R2); `external_record_key text NOT NULL` (length 1 to 100); `created_at`, `updated_at` (equal, line 27). `uk_attendance_records_source_external_record_key` (`source_system`, `external_record_key`) makes the import idempotent. Append-only trigger rejects update and delete (RULE-519). The single state `RECORDED` of 4.5 is not a column. **No biometric or location column** (ADR-0080 decision 2); a later slice adds one only with a new data design.

### monthly_confirmations

| Column | Type | Rule |
|---|---|---|
| `id` | uuid | pk |
| `employee_id` | uuid NOT NULL | one confirmation per person and month |
| `month_start_date` | date NOT NULL | `ck_monthly_confirmations_month_start_date` `EXTRACT(DAY FROM month_start_date) = 1` |
| `status` | text NOT NULL | `GENERATED`, `CONFIRMED`, `LOCKED` |
| `confirmed_by_kind` | text null | `EMPLOYEE`, `DEFAULT`; 为空表示 not yet confirmed |
| `confirmed_at` | timestamptz null | 为空表示 not yet confirmed |
| `locked_at` | timestamptz null | 为空表示 not locked |
| `created_at`, `updated_at`, `version` | | audit |

Constraints: `uk_monthly_confirmations_employee_month` (`employee_id`, `month_start_date`); `ck_monthly_confirmations_confirmation_columns` `(status = 'GENERATED') = (confirmed_by_kind IS NULL) AND (confirmed_by_kind IS NULL) = (confirmed_at IS NULL)`; `ck_monthly_confirmations_lock_columns` `(status = 'LOCKED') = (locked_at IS NOT NULL)`. Trigger `guard_locked_monthly_confirmation` (INV-105, RULE-518): rejects any update or delete of a row whose stored `status` is `LOCKED`, and any insert, update or delete of a `monthly_confirmation_lines` row whose parent is `LOCKED`.

### monthly_confirmation_lines

| Column | Type | Rule |
|---|---|---|
| `id` | uuid | pk |
| `monthly_confirmation_id` | uuid NOT NULL | `fk_monthly_confirmation_lines_monthly_confirmation_id`, RESTRICT |
| `employment_relationship_id` | uuid NOT NULL | [R]; no foreign key |
| `line_kind` | text NOT NULL | `ATTENDANCE_DAYS`, `LEAVE`, `OVERTIME` |
| `leave_type_id` | uuid null | 为空表示 not a leave line; foreign key added in L1b |
| `is_paid_leave` | boolean null | 为空表示 not a leave line; copied at generate time |
| `compensation_kind` | text null | 为空表示 not an overtime line; `PAY`, `COMP_TIME` |
| `day_kind` | text null | 为空表示 not an overtime line; `WORKDAY`, `REST_DAY`, `STATUTORY_HOLIDAY`; payroll reads overtime by day kind (section 9a) and cannot read `overtime_requests` (RULE-412) |
| `minutes_count` | integer null | 为空表示 an attendance-days line; `>= 0` |
| `day_hundredths_count` | integer null | 为空表示 a minutes line; `>= 0` |
| `original_month_start_date` | date null | 为空表示 not a correction row; first day of the month the correction belongs to |
| `created_at`, `updated_at` | | audit |

Constraints: `ck_monthly_confirmation_lines_leave_columns` `(line_kind = 'LEAVE') = (leave_type_id IS NOT NULL) AND (leave_type_id IS NULL) = (is_paid_leave IS NULL)`; `ck_monthly_confirmation_lines_overtime_columns` `(line_kind = 'OVERTIME') = (compensation_kind IS NOT NULL) AND (line_kind = 'OVERTIME') = (day_kind IS NOT NULL)`; `ck_monthly_confirmation_lines_day_kind` enum CHECK; `ck_monthly_confirmation_lines_quantity_columns` `(line_kind = 'ATTENDANCE_DAYS') = (day_hundredths_count IS NOT NULL) AND (line_kind = 'ATTENDANCE_DAYS') = (minutes_count IS NULL)`; `ck_monthly_confirmation_lines_original_month_start_date` day-of-month 1; `uk_monthly_confirmation_lines_identity` unique `NULLS NOT DISTINCT` (`monthly_confirmation_id`, `employment_relationship_id`, `line_kind`, `leave_type_id`, `compensation_kind`, `day_kind`, `original_month_start_date`): one line per fact, so a regenerate that replaces the set cannot double-count. That a correction row's original month is earlier than the confirmation's month crosses two rows and is checked by the generate command, not by a constraint (no cross-row CHECK).

## Differences from the section 8 sketch

1. `external_record_id` becomes `external_record_key` (`_id` is `uuid` only; an external system's text identifier is `_key`, naming standard 2.4).
2. `month` becomes `month_start_date` and `original_month` becomes `original_month_start_date` (`date`, day 1; `month` is not a class word).
3. `minutes`, `day_fraction_hundredths` become `minutes_count`, `day_hundredths_count` (`_count` is the integer class word). `compensation_mode` becomes `compensation_kind`, `state` becomes `status` (the repo's enum word, as in `standard_hours_versions`), `reason` and `proof_ref` are carved out (see Scope).
4. `monthly_confirmation_lines` gets `compensation_kind`, `day_kind`, `minutes_count` and `day_hundredths_count` instead of one `minutes` column, because attendance days and minutes are different units and one column must not hold both.
5. The `attendance_records.state` column of 4.5 (`RECORDED`, the only value) is dropped.

None of these changes a command or an event.

## Invariants to constraints (B5, O4)

| Invariant | Carrier | Reason when no constraint |
|---|---|---|
| Idempotent import (4.5) | `uk_attendance_records_source_external_record_key` | |
| Attendance records append-only (RULE-519) | trigger on `attendance_records` | |
| After lock nothing changes (INV-105, RULE-518) | `guard_locked_monthly_confirmation` trigger | |
| One confirmation per person and month | `uk_monthly_confirmations_employee_month` | |
| Statutory-holiday overtime is paid only (RULE-517) | `ck_overtime_requests_holiday_pay_only` | |
| One request per approval process | `uk_*_approval_instance_id` | |
| An `IN_APPROVAL` request has its process | `ck_*_in_approval_has_instance` | |
| A hold line belongs to one request | `uk_leave_request_holds_ledger_line_id` | |
| One split row per request and day | `uk_leave_request_days_request_work_date` | |
| Month columns hold the first day | `ck_*_month_start_date` | |
| State machines of `leave_requests`, `overtime_requests`, `monthly_confirmations` (legal transitions) | | the enum CHECK limits the values; transitions are the commands' job and are recorded in `state_transitions` (no transition trigger, as for the other aggregates) |
| Request days lie inside `[start_at, end_at]` and sum to the request | | cross-row; checked by `SubmitLeave` |
| Leave or overtime only on days the relationship is in service; overlap with approved leave (RULE-517) | | spans another context's data; checked by the commands |
| Correction row's original month is earlier than the confirmation month | | cross-row; checked by the generate command |
| Two live leave requests of one relationship do not overlap | `ex_leave_requests_live_period_overlap` | the design does not state this rule in words; it is the table's business key (gate O3) and I added it as a design proposal, **lead may veto before merge** (then the key is dropped and O3 for this table is recorded as not passed) |
| Several overtime requests on one relationship and day | | allowed on purpose: one day may hold separate overtime periods (no rule in 4.4 or RULE-517 forbids it); the sum against the daily limit is checked by `SubmitOvertime`. Source of the exemption: section 4.4 |

## Use case to aggregate to table (O1)

| Use case | Tables |
|---|---|
| `SubmitLeave`, `Approve/Reject/Withdraw/RecallLeave`, cancel on `Ended`/`Corrected` | `leave_requests`, `leave_request_days`, `leave_request_holds` |
| `SubmitOvertime`, approve, reject, `MarkOvertimeCompensated`, `MarkOvertimeCreditedAsCompTime` | `overtime_requests` |
| `ImportAttendanceRecords`, `MakeUpPunch` | `attendance_records` |
| `GenerateMonthlyConfirmation`, regenerate, `ConfirmByEmployee`, `ConfirmByDefault`, `LockMonthlyConfirmation` | `monthly_confirmations`, `monthly_confirmation_lines` |
| `LeaveHours.forDay(employee, date)` (timesheet seam) | `leave_requests`, `leave_request_days` |

## Business keys, data classes, versioning (O3, O6)

| Table | Business key | Class and temporality | Versioning |
|---|---|---|---|
| `leave_requests` | business key: one relationship has at most one live request at any instant (`ex_leave_requests_live_period_overlap`); `approval_instance_id` | transaction; current state plus `state_transitions` | optimistic `version` |
| `leave_request_days`, `leave_request_holds` | (request, day), (request, line) | transaction detail | none (children) |
| `overtime_requests` | `approval_instance_id` | transaction | optimistic `version` |
| `attendance_records` | (`source_system`, `external_record_key`) | event fact, append-only | none |
| `monthly_confirmations` | (`employee_id`, `month_start_date`) | transaction, locked at end | optimistic `version` |
| `monthly_confirmation_lines` | the identity unique key | transaction detail | none (replaced while `GENERATED`) |

## Query paths (O10)

| Path | Filter | Sort | Rows | Paging | Index |
|---|---|---|---|---|---|
| My leave requests | `employee_id` | `start_at` desc, `id` | tens per person | cursor | `ix_leave_requests_employee_id_start_at` (`employee_id`, `start_at`, `id`) |
| Scoped HR/manager list | `legal_entity_id`, `status` | `start_at`, `id` | hundreds | cursor | `ix_leave_requests_legal_entity_id_start_at` (`legal_entity_id`, `start_at`, `id`) |
| Cancel on relationship `Ended`/`Corrected` | `employment_relationship_id`, `status IN (IN_APPROVAL, APPROVED)` | none | few | none | `ix_leave_requests_relationship_id_status` (`employment_relationship_id`, `status`) |
| Approval result | `approval_instance_id` | none | 1 | none | `uk_leave_requests_approval_instance_id` |
| `LeaveHours.forDay` | `work_date` joined to requests of one employee | none | few | none | `ix_leave_request_days_work_date_leave_request_id` (`work_date`, `leave_request_id`); the request-id lookup uses the unique key's prefix |
| Holds of a request | `leave_request_id` | none | few | none | `uk_leave_request_holds_request_line` prefix |
| My overtime | `employee_id` | `work_date` desc, `id` | tens | cursor | `ix_overtime_requests_employee_id_work_date` |
| Overtime by relationship and day (comp-time target, overlap check) | `employment_relationship_id`, `work_date` | none | few | none | `ix_overtime_requests_relationship_id_work_date` |
| Punches of a person in a month (generate, T-9 validation) | `employee_id`, `occurred_at` range | `occurred_at` | up to hundreds | none | `ix_attendance_records_employee_id_occurred_at` (`employee_id`, `occurred_at`, `id`) |
| Confirmation of a person | `employee_id`, `month_start_date` | none | 1 | none | `uk_monthly_confirmations_employee_month` |
| Overdue unconfirmed (default confirm) | `status = 'GENERATED'`, `month_start_date` | `month_start_date` | hundreds | batch | `ix_monthly_confirmations_generated_month` partial on `month_start_date` where `status = 'GENERATED'` |
| Lines of a confirmation | `monthly_confirmation_id` | none | tens | none | `uk_monthly_confirmation_lines_identity` prefix |

Foreign-key columns `leave_request_id` (days, holds) and `monthly_confirmation_id` are the prefix of a unique key, so no extra index. Large-table planning (2.4a item 8): `attendance_records` is the only table that can grow; the number of punches in the 2-year minimum retention window is expected below ten million rows for this company (unverified: the head count and the punch source are open, R2), so no partition now; the import slice revisits it before the first real import.

## Cross-cutting, legal entity and period (O13, O14)

Each request carries the cost `legal_entity_id` on the start day or work day (RULE-398 scope); the employer is read from the relationship. `attendance_records` and `monthly_confirmations` carry no legal entity: a punch belongs to the person, and the confirmation is per person and month; payroll's per-entity split (section 9a) comes from the request lines' relationship, resolved by payroll through the relationship api. Periods are calendar months (`month_start_date`) and calendar years of the leave year (Q-2). Retention (Q-3 answered): attendance records at least 2 years (ADR-0079:123); requests, overtime and confirmations kept with the employee record period; no purge job in L1a.

## ADR clauses (O15)

| ADR clause | Provision | Where |
|---|---|---|
| ADR-0086 decision 5 (per-relationship counting), B4 | `employment_relationship_id` on requests, overtime and confirmation lines, stored without a foreign key | `leave_requests`, `overtime_requests`, `monthly_confirmation_lines` |
| ADR-0080 decision 2 | no biometric or location column; sick leave stores proof reference and period only | `attendance_records`; the free-text and proof columns of `leave_requests` are carved out until the user decides |
| ADR-0079:123 | attendance written records kept at least 2 years | retention in the registry |
| ADR-0043 | classification of every personal column | below |

## Registry drafts (C2, C3, C5)

```yaml
organization_leave.leave_requests:
  role: AGGREGATE_ROOT
  aggregate: LeaveRequest
  mutability: MUTABLE
  retention: {policy: PERMANENT}      # Q-3: kept with the employee record period; an expiry job is a later decision
  lineage: {source: NEW, migrated_from: [old leave records], via: ONE_OFF_IMPORT}
  writers: [organization.leave]
organization_leave.leave_request_days:
  role: AGGREGATE_CHILD
  aggregate: LeaveRequest
  mutability: REPLACE_SET
  retention: {policy: PERMANENT}
  lineage: {source: NEW}
  writers: [organization.leave]
organization_leave.leave_request_holds:
  role: AGGREGATE_CHILD
  aggregate: LeaveRequest
  mutability: APPEND_ONLY
  retention: {policy: PERMANENT}
  lineage: {source: NEW}
  writers: [organization.leave]
organization_leave.overtime_requests:
  role: AGGREGATE_ROOT
  aggregate: OvertimeRequest
  mutability: MUTABLE
  retention: {policy: PERMANENT}
  lineage: {source: NEW, migrated_from: [old overtime records], via: ONE_OFF_IMPORT}
  writers: [organization.leave]
organization_leave.attendance_records:
  role: AGGREGATE_ROOT
  aggregate: AttendanceRecord
  mutability: APPEND_ONLY
  retention: {policy: BUSINESS_RECORD}      # ADR-0079:123 sets a 2-year minimum, not a purge; no purge job in L1a (a time-bound policy needs a ShedLock job)
  lineage: {source: NEW}
  writers: [organization.leave]
organization_leave.monthly_confirmations:
  role: AGGREGATE_ROOT
  aggregate: MonthlyConfirmation
  mutability: MUTABLE
  retention: {policy: PERMANENT}
  lineage: {source: NEW}
  writers: [organization.leave]
organization_leave.monthly_confirmation_lines:
  role: AGGREGATE_CHILD
  aggregate: MonthlyConfirmation
  mutability: REPLACE_SET
  retention: {policy: PERMANENT}
  lineage: {source: NEW}
  writers: [organization.leave]
```

No `references` entry is written for the three deferred keys in L1a (the target tables do not exist and the registry test would fail); L1b adds the entry with each foreign key. Retention of the personal-data tables is `PERMANENT` on the strength of Q-3 (kept with the employee record period); ADR-0043 R-2 asks PI tables for an expiry, which Q-3 answers as none for now, and the user can change it. The card adapts the drafts to the registry header; the registry test is the check.

## Classification drafts (C4)

```
organization_leave.leave_requests.employee_id | INTERNAL | PI | 用途：请假人，数据范围判断与本人查询
organization_leave.leave_requests.start_at | INTERNAL | PI | 用途：请假起始时刻，排班与工时扣减
organization_leave.leave_requests.end_at | INTERNAL | PI | 用途：请假结束时刻，排班与工时扣减
organization_leave.overtime_requests.employee_id | INTERNAL | PI | 用途：加班人，数据范围判断与本人查询
organization_leave.overtime_requests.work_date | INTERNAL | PI | 用途：加班日期，工时与调休
organization_leave.attendance_records.employee_id | INTERNAL | PI | 用途：打卡人，考勤月确认
organization_leave.attendance_records.occurred_at | INTERNAL | PI | 用途：打卡时刻，考勤月确认与加班校验
organization_leave.attendance_records.external_record_key | INTERNAL | NONE | 豁免：来源系统的记录标识，不指向个人
organization_leave.monthly_confirmations.employee_id | INTERNAL | PI | 用途：月确认的所属员工
```

Every column above is personal information (PI) at the INTERNAL level, never SPI: attendance times and leave periods do not name a health condition. A leave type such as sick leave is a reference to `leave_types` (L1b) and is classified there. The columns that could carry health text are carved out (Scope).

## Comments (C1)

Chinese comments, written into the migration by #1400 exactly as drafted here. Table comments carry the grain (`粒度：每 … 一行`, checked by `DataArchitectureTest` D3); every nullable column carries 「为空表示」.

| Table | Comment draft |
|---|---|
| `leave_requests` | 请假申请。粒度：每个雇佣关系每次请假申请一行。 |
| `leave_request_days` | 请假申请按日拆分的时长。粒度：每个请假申请的每个工作日一行。 |
| `leave_request_holds` | 请假申请占用的额度流水。粒度：每个请假申请占用的每条额度流水一行。 |
| `overtime_requests` | 加班申请。粒度：每个雇佣关系每次加班申请一行。 |
| `attendance_records` | 外部打卡记录，只追加。粒度：每个来源系统的每条打卡记录一行。 |
| `monthly_confirmations` | 月度考勤确认。粒度：每个员工每个自然月一行。 |
| `monthly_confirmation_lines` | 月度考勤确认的明细。粒度：每个确认单每个关系每个出勤、请假或加班事实一行。 |

| Nullable column | 「为空表示」 draft |
|---|---|
| `leave_requests.approval_instance_id`, `overtime_requests.approval_instance_id` | 为空表示尚未发起审批（草稿、自动通过或在进入审批前被拒） |
| `monthly_confirmations.confirmed_by_kind`, `.confirmed_at` | 为空表示尚未确认 |
| `monthly_confirmations.locked_at` | 为空表示尚未锁定 |
| `monthly_confirmation_lines.leave_type_id`, `.is_paid_leave` | 为空表示不是请假行 |
| `monthly_confirmation_lines.compensation_kind`, `.day_kind` | 为空表示不是加班行 |
| `monthly_confirmation_lines.minutes_count` | 为空表示这是按天计的出勤行 |
| `monthly_confirmation_lines.day_hundredths_count` | 为空表示这是按分钟计的行 |
| `monthly_confirmation_lines.original_month_start_date` | 为空表示不是跨月更正行 |

Non-null columns: meaning and unit, one line each, written by #1400 (the glossary words for the table, column and enum names go to `docs/glossary/terms.yaml` in the same card: not checked here, `GlossaryTest` is the card's check).

## Analytical facts (A1 to A8)

No analytical fact: the seven tables are transaction and event tables of the leave context; no metric or read model is introduced in L1a. Monthly confirmation lines are the payroll seam's input (section 9a), not an analytical fact table.

## Baseline, target, transition, rollback (T1 to T4)

Baseline: no `organization_leave` schema. Target: the schema with these seven tables. Transition: one migration set (schema, tables, triggers, indexes), no data. Rollback: drop the schema while it is empty; after the first slice writes into it, rollback is a new migration.

## Specials (E1 to E3)

Event payloads: none in L1a (events land with the commands, 4.3 to 4.6, `docs/architecture/events/`). Read models: none. Migration mapping: none in L1a (the old leave and overtime records are imported by a later slice; the registry `lineage` names them).

## Expected schema diff (for #1400)

New schema `organization_leave`; new tables, columns, constraints, indexes and the two trigger functions listed above, nothing else. Constraint and index names are those given in the sections above. The schema PR's actual diff is compared with this record table by table, column by column.

## Verdict

All applicable items of the gate are met for the seven tables as signed here, except the carved-out columns `leave_requests.reason_text` and `leave_requests.proof_file_id`, which are **not passed (pending the user's decision)** and are not part of this signature. Three named deferrals, none a condition: foreign keys to the L1b tables (added there), the `source_system` value list (import slice), the glossary entries (the #1400 card). The one design proposal that lead may veto is `ex_leave_requests_live_period_overlap`. None of the seven tables depends on a pending value (T-1, T-3, T-5, T-7, T-8, T-10), and Q-3 is answered.

数据设计：通过（one-design，2026-11-05，依据 bb2e2ce1d；范围：`leave_requests`（不含 `reason_text`、`proof_file_id`）、`leave_request_days`、`leave_request_holds`、`overtime_requests`、`attendance_records`、`monthly_confirmations`、`monthly_confirmation_lines`，列名按本记录「与第 8 节草案的差异」）。
