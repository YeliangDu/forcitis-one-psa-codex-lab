# organization.leave (请假与加班) — sign-off spec (#1279)

- Card: #1279. Author: one-design, 2026-11-04. Base: origin/main.
- Basis: ADR-0051 B-27 (split out of `organization.attendance`); ADR-0054 decision 1 (W2); ADR-0079 decision 2, §5 invariant 9 (:125) and the leave line (:99); `docs/architecture/ssot/aggregates/planned.yaml` AGG-69..72, 97, 118; closeout `people/commands.yaml` and `people/state-machines.yaml`; RULE-398..405, 412, 413; INV-68, INV-69, INV-105, INV-123.
- Status: proposal. Business treatments T-1..T-9 (section 9) are **not decided here**; they go to the user through lead. The golden sample is `leave-quota-golden-sample.md` (same folder).
- Out of scope: implementation, migrations, payroll pay computation of expired comp time (W4, B-15), the attendance source system (R2), any UI wording.

## 1. The one thing to decide

The shape of the new context `organization.leave`: its aggregates, states, commands, events, data and its edges, precise enough that W2 slices can be opened and tested, without deciding any quota policy.

## 2. Facts (verified on origin/main 2026-11-04 unless marked)

| Fact | Source |
|---|---|
| `organization.leave` is planned, schema `organization_leave`, no tables, no code, no INV execution point | `ssot/domain/contexts.yaml:41-54`; `inv-execution-points.yaml` (INV-68/69/105/123 empty) |
| AGG-108 standard hours is planned, not built: `organization.attendance.api` holds only `WorkCalendar`, `DayKind`, `DayException`, `LockedDayChange`, `AttendanceError`; no standard-hours code exists | `git grep -i 'standard.\?hours'` on `backend/src/main`, `planned.yaml:1049` |
| Allowed module edges: `organization.workforce`, `organization.attendance`, `platform.approval` | ADR-0079:89; contexts.yaml note |
| `organization.attendance` keeps the work calendar (AGG-68) and standard-hours version (AGG-108); `platform.approval -> organization.attendance` is a built edge | ADR-0051:96 |
| Approval starts with `ApprovalProcesses.start`, idempotent per (process type, document, round); result arrives as `ApprovalCompleted`; withdraw / cancel exist | `platform/approval/api/ApprovalProcesses.java`; template `cost/timesheet/application/ApplyApprovalResult.java` |
| Workforce publishes `EmployeeHired`, `EmployeeRegularized`, `EmployeeSeparated`, `EmployeeReinstated`, `EmployeeLegalEntityChanged`, `EmployeeTransferred` | `organization/workforce/events/` |
| Unpaid-leave-start event name `EmployeeUnpaidLeaveStarted` is approved (PR #1260); no end event has a name (no consumer yet) | glossary PR #1260 |
| `TimesheetOvertimeSeamGuardTest` forbids any `cost.timesheet` class to depend on `organization.leave..` (api included) and the module graph to have the edge; RULE-398/404/405 need leave minutes and the rejection of a leave from the leave side; ADR-0079:111 names `cost.timesheet` as a subscriber of leave approval and cancellation events | `TimesheetOvertimeSeamGuardTest`; RULE-398, 405, 412; ADR-0079:111 |
| The repo holds no description of the legacy entitlement computation | search 2026-11-04; golden sample section 0 |
| Statutory annual-leave rules exist as a law note only | `frameworks/notes/leave-wage.md` |

## 3. Options and choices (technical; mine to choose)

**3.1 How a balance is stored.**

| | A. Account row with counters (entitled, used, held) | B. Account plus append-only ledger, balance derived (chosen) |
|---|---|---|
| Audit of every change | needs a second history table | the ledger is the history |
| Idempotent re-delivery of an event or approval result | per-counter guards | unique (source kind, source id, line kind) per account |
| Sealed year never rewritten (ADR-0079:123) | counters can drift unnoticed | sealed year refuses new lines; corrections are new lines in the open year |
| Cost | cheap read | one sum per read; fine at this volume (one row per person x type x year) |

Chosen B. Reason: INV-68 and the sealed-year rule both want "append, never rewrite", and the ledger gives idempotence for free.

**3.2 Unit of quantity.** Each leave type declares a ledger unit: `MINUTE` (hour-granular types, comp time) or `DAY` (stored as hundredths of a day, integer). A `DAY` type is never converted to minutes inside the ledger, so a standard-hours change (entity A 450 minutes, entity B 480, a new AGG-108 version) cannot change the number of days a person is entitled to. Conversion happens once, per day of leave taken: the request's day row stores both the minutes (from that day's standard hours, INV-116, no default fallback; this is what the timesheet and payroll read) and the day fraction that is charged to the ledger (day minutes / that day's standard minutes). The conversion date is the day of leave taken. Rounding is T-3, applied when the day fraction is formed, never inside the ledger. Whether a type is `DAY` or `MINUTE` is part of the rule version (T-3).

**3.3 Who is the approver path of overtime.** ADR-0079 §5 invariant 9 (:125) puts leave and overtime through `platform.approval`; the closeout commands replace overtime approval by a punch check. See T-9. Technical choice made here: overtime goes through `platform.approval` as ADR-0079 says; the punch check is a submit-time validation (refuse, or flag, per T-9), not an approver.

**3.4 Rule version (AGG-118) belongs here.** The card lists five aggregates; the planned rows and the closeout list a sixth, AGG-118 (leave types, entitlement rules, carry-over rules, exempt threshold), assigned to B-27 (`planned.yaml:1121-1130`). Leave quota cannot exist without it, so this spec includes it as a version-table aggregate and **asks lead to confirm** (question Q-1).

**3.5 Time-dependent quota inputs (service years, employer entity, unpaid periods).** Entitlement is computed by the account command from an as-of read of the workforce api, and the inputs used (hire date, prior service, separation date, unpaid periods, employer entity) are recorded in a child row of the GRANT or ADJUST line (`leave_grant_basis`), so a sealed year stays reproducible and no replica of workforce data is kept. Events only trigger a recompute: `EmployeeHired`, `EmployeeSeparated`, `EmployeeReinstated`, `EmployeeCorrected` (history-only corrections are not published, so the next recompute reads the corrected history), `EmployeeUnpaidLeaveStarted`. Gaps in today's workforce (verified on origin/main; each needs a card from lead): (a) `EmployeeHired` carries no prior service, so G3 and L3's cumulative basis need the service-start basis of `docs/data/employment-start-date-opinion.md` exposed on a workforce read; (b) there is no unpaid-leave end event or read, G5 needs the period end; (c) `EmployeeLegalEntityChanged` is the **cost** legal entity, ADR-0079 invariant 4 keeps cost entity, employer (用人单位, a labour-contract fact not built, ADR-0079:108) and registered entity apart. Until an employer-entity fact exists, the rule scope is `all entities` and a cost-entity change does not touch entitlement; T-6 is then answered only for the employer-entity case and asked again when that fact exists. Alternative rejected: a local projection fed by events, which cannot see (a)-(c) and misses history-only corrections.

## 4. Aggregates

Module: `com.forcitis.one.organization.leave` with `api`, `events`, `application`, `domain`, `infra`, package rules as other modules. Edges only to `organization.workforce` (api, events), `organization.attendance` (api: work calendar, standard hours) and `platform.approval` (api, events). Audit goes through the existing platform audit port used by other modules (`platform.audit` api, as every module does; it is the audit mechanism, not a domain edge); the module-dependency file lists it like the others.

### 4.1 AGG-118 Leave rule version (configuration)

- Data: leave type code, applicability scope (employer entity or all; the source sets it by city and service years, city is dropped here until the user names a city rule, because INV-123's exclusion constraint needs one scope key), effective-from date, entitlement rule rows (service-year band, days or minutes, proration mode), carry-over rule (cap, expiry), request unit (minute / half-day / day), allow-negative flag, exempt-from-approval threshold (minutes) and proof-required flag per type, basis reference, publisher.
- State: one, `IN_FORCE`; a new version supersedes; never edited, corrections are new versions that do not re-apply to sealed years (D15).
- Command: `PublishLeaveRuleVersion` (permission `leave:rule:publish`). Event: `LeaveRuleVersionPublished`.
- Invariant INV-123: one version in force per (leave type, scope) on a date; enforced by exclusion constraint on non-voided rows, as W2 versioned tables.
- **The values inside the rule rows are business treatments (T-1..T-8).** The structure above is mine; the numbers are the user's.

### 4.2 AGG-71 Leave balance account

- Two account kinds. Year accounts: one per (employee, leave type, leave year), for `DAY` types such as annual leave. Rolling accounts: one per (employee, leave type) with no year and no seal, for comp time (and for carry-in if T-7 gives it an expiry): their grants are lots, each with its own expiry, so a 2026 grant can expire in 2027 without writing to a sealed year (closeout `commands.yaml:1701`, 补休额度行独立于年度). Created by `OpenLeaveYearAccount` (automatic on `EmployeeHired` for the hire year; on year start for the rest; never implicitly by a hold). Requires the rule version in force on the open date, else it fails with an alert (no default).
- Ledger line kinds: `GRANT` (entitlement, proration, comp-time grant), `CARRY_IN`, `HOLD`, `RELEASE`, `USE`, `REVERSE_USE`, `ADJUST` (HR manual, with reason), `EXPIRE`, `CARRY_OUT`. Each line: quantity in the type's ledger unit (non-negative for every kind except `ADJUST`, which is signed; a CHECK per kind enforces the sign), source kind and id, effective date, expiry date (lots only), `consumes_line_id` (the grant line it draws from; mandatory on `USE`, `HOLD`, `RELEASE`, `EXPIRE`, `REVERSE_USE` of lot types), actor.
- Derived balance, per account for year accounts and per lot for rolling accounts: sum(GRANT, CARRY_IN, REVERSE_USE, ADJUST) - sum(HOLD net of RELEASE and USE, USE, EXPIRE, CARRY_OUT). A request that draws from several lots (earliest expiry first, RULE-516) writes one HOLD and one USE line per lot. Whether the balance may go below zero is T-4.
- States (year accounts only): `OPEN` -> `SEALED` (terminal; ADR-0079:123, after `CloseLeaveYear`, no active holds). Rolling accounts have no seal.
- Commands: `HoldLeaveBalance` (INV-68; refuses when it would exceed available unless T-4 allows), `ReleaseLeaveHold`, `ConsumeLeaveHold` (on approval), `ReverseLeaveUse` (on 销假/ cancel after start), `GrantCompTime`, `ExpireCompTime`, `AdjustLeaveBalance`, `CloseLeaveYear`. Retry-safe: unique (account, source kind, source id, line kind, consumes_line_id).
- Consumes: `EmployeeHired`, `EmployeeSeparated`, `EmployeeReinstated`, `EmployeeLegalEntityChanged`, `EmployeeUnpaidLeaveStarted` (each triggers a recompute of entitlement as a new GRANT/ADJUST line with its basis row, 3.5; formulas per T-1..T-6).
- Publishes: `LeaveCompTimeExpired` (minutes, account, date) for the payroll seam; nothing else.

### 4.3 AGG-70 Leave request

- Data: request id, employee, leave type, start and end instants, reason, proof attachment reference, minutes per day (split by each day's standard hours; non-workdays 0), balance hold id.
- States: `DRAFT`, `SUBMITTED` (auto-approve path only), `IN_APPROVAL`, `APPROVED`, `REJECTED` (terminal), `WITHDRAWN` (terminal, before start), `RECALLED` (销假, terminal), where "`APPROVED` stays `APPROVED` after it is fully taken".
- Commands: `SubmitLeave` (holds balance in the same transaction; routing by rule version: inside quota, rule-allowed, under the exempt threshold and no proof needed -> auto-approve, else `ApprovalProcesses.start`), `ApproveLeave` / `RejectLeave` (from `ApprovalCompleted`, like `ApplyApprovalResult`), `WithdrawLeave` (applicant only; in approval via `ApprovalProcesses.withdraw`; approved before start), `RecallLeave` (销假: fact registration, not re-approved; reverses used minutes).
- Events published: `LeaveApproved`, `LeaveRejected`, `LeaveWithdrawn`, `LeaveRecalled` (consumers: timesheet, payroll seam, reminders; names registered by the slice that implements them). Subscribed: `ApprovalCompleted`.
- Timesheet seam (RULE-398/404/405). Direction: `cost.timesheet -> organization.leave`, api and published events only, never tables. Surface: read API `LeaveHours.forDay(employee, date)` (approved and pending minutes) and the events `LeaveApproved`, `LeaveRejected`, `LeaveWithdrawn`, `LeaveRecalled` (RULE-405 needs the rejection). This edge does not exist today and `TimesheetOvertimeSeamGuardTest` forbids it, so **a separate card (lead opens it, design rules the guard text) must add the edge to `module-dependencies.txt` and narrow the guard to allow exactly the `organization.leave.api` leave-hours type and the leave events**; until then no slice may call the API from timesheet. RULE-412 stands for overtime approvals and comp-time balances, which stay unreadable.

### 4.4 AGG-72 Overtime request

- States: `REQUESTED`, `IN_APPROVAL`, `APPROVED`, then `COMPENSATED_PAY` or `CREDITED_AS_COMP_TIME` (terminal), `REJECTED` (terminal), `WITHDRAWN` (terminal; the applicant withdraws only while `IN_APPROVAL`, via `WithdrawOvertime` and `ApprovalProcesses.withdraw`).
- Commands: `SubmitOvertime` (validates against punches, T-9; refuses overlap with approved leave; compensation mode limited by day type: statutory holiday pay only), `ApproveOvertime` / `RejectOvertime` (from `ApprovalCompleted`), `MarkOvertimeCompensated`, `MarkOvertimeCreditedAsCompTime` (calls `GrantCompTime`).
- Events: `OvertimeApproved` (consumed by AGG-97 inside the module), `OvertimeCompensated`.
- RULE-412 stands: nothing outside this module reads overtime approvals or comp-time balance except through the read API.

### 4.5 AGG-69 Attendance record

- Append-only fact, state `RECORDED` (record kind punch / outfield / make-up is an attribute). Idempotent import on (source system, external record id).
- Commands: `ImportAttendanceRecords` (whole batch fails and alerts if a person cannot be resolved), `MakeUpPunch` (counts and window: business treatment U4, not decided).
- The attendance source system is undetermined (R2); this aggregate is the last slice and its import adapter is behind a port. Biometric and location columns follow ADR-0080 decision 2 (supersedes the ADR-0079 U-14 sentence): biometric punch needs separate consent and an alternative; field location needs a regulation and is limited to working time; sick leave stores only the proof and the period.

### 4.6 AGG-97 Monthly attendance confirmation

- Per employee x month: attendance days, leave by type, overtime by compensation mode, correction rows tagged with the original month.
- States: `GENERATED` -> `CONFIRMED` -> `LOCKED` (terminal). Missing import days are a failed precondition, never full attendance.
- Commands: `GenerateMonthlyConfirmation`, `RegenerateMonthlyConfirmation` (only `GENERATED`), `ConfirmByEmployee`, `ConfirmByDefault` (overdue or resigned), `LockMonthlyConfirmation`. Events: `MonthlyConfirmationConfirmed`, `MonthlyConfirmationLocked`.
- INV-105: after lock nothing changes; later changes land as correction rows of the next month. A leave `IN_APPROVAL` is not counted.

### 4.7 Inconsistencies in the sources, and what this spec does with them

1. The closeout `锁定` command carries the idempotency key and `used_by` of the year-end carry-over command (copy-paste). Spec uses the right keys: lock = (employee, month), carry-over = (employee, leave type, year).
2. `SUBMITTED` exists in the state machine but not in the aggregate draft; kept, as the auto-approve path needs it.
3. planned.yaml lists fewer commands than the closeout; the closeout set is used (reject, reverse used, comp-time grant and expiry, regenerate, default-confirm).
4. INV-116 (standard-hours version) is owned by attendance but cited by leave commands; leave only reads it through attendance's api.
5. ADR-0051:96 says attendance starts no approval; this context starts them, which is why leave left attendance. No cycle.

## 5. Permissions and data scope

Pattern `leave:<resource>:<action>`: `leave:request:submit` (own), `leave:request:approve` is not a permission (approval platform decides), `leave:balance:read` (own; scoped read for managers and HR via `OrgUnitAccess`), `leave:balance:adjust` (HR), `leave:rule:publish`, `leave:overtime:submit`, `leave:attendance:import`, `leave:monthly:lock`. Personal records are owned: `CurrentUser` plus explicit service check, anyone else gets 404. Sensitive columns follow ADR-0080 decision 2; the first slices carry the proof as an attachment reference only (period and proof, no medical content). Whether these codes need new rows in the permission register is checked by the slice that adds them.

## 6. RULE ids

New file `docs/business/rules/leave-rules.md`, ids RULE-509.. (bc tag `organization.leave`). Existing RULE-398..405, 412, 413 stay in the timesheet files; RULE-412/413 get the bc tag by cross-reference only.

| Id | Rule (summary) |
|---|---|
| RULE-509 | A leave request holds balance in the same transaction it is submitted; it is refused when the balance is not enough, unless the rule version allows (INV-68, T-4) |
| RULE-510 | Leave minutes are split by the standard hours of each day; a non-workday counts zero; a missing standard-hours version refuses the request |
| RULE-511 | A request inside quota, rule-allowed, under the exempt threshold and with no proof needed is approved without a person; otherwise it goes to approval |
| RULE-512 | A rejected or withdrawn request releases its hold; a recalled leave gives back used minutes; nothing is deleted |
| RULE-513 | A balance account is opened by the year and the rule version in force, never implicitly; a missing version fails and alerts |
| RULE-514 | A sealed leave year is never rewritten; corrections and carry-over are new lines in the open year |
| RULE-515 | Entitlement changes caused by hiring, separation, unpaid leave or legal-entity change are new ledger lines computed from the rule version; the formulas are the business treatments |
| RULE-516 | Comp time is granted per overtime record with its own expiry and used earliest-expiry first; expiry reports the minutes to payroll and pays nothing here |
| RULE-517 | Overtime is approved through the approval platform; statutory-holiday overtime can only be paid; overlap with approved leave is refused |
| RULE-518 | Monthly confirmation is generated only when every day of the month has an import batch; after lock nothing changes and later changes go to the next month marked with the original month |
| RULE-519 | Attendance records are append-only; a make-up punch is a separate record |
| RULE-520 | The leave read API is the only way other modules see leave hours, balances, overtime approvals or comp time |

Each rule gets the full block (Category, Priority, Source, Plain English, Specification, Tests, Confidence) in the rules file; sources: the cited ADR line and the closeout command. Ids may shift if another PR takes RULE-509.. first; the rules file is the register.

## 7. Glossary

Command, event, error-code and state names in this spec are the approved internal names (one-design, #1279). They are entered in `terms.yaml` / `backend/glossary` by the slice that implements each one, so no entry exists without code (the same practice as #918). Chinese display words are the user's to confirm and go to lead as one UI-wording list: 请假, 销假, 补休, 年假, 额度, 占用, 结转, 假期规则版本, 加班, 月度考勤确认.

## 8. Data design (signed by design for this repo; the gate in `docs/data/data-design-gate.md` applies per schema PR)

Schema `organization_leave`. All tables: surrogate `id uuid`, `created_at`, `created_by`, no `jsonb` for business content (D18), no mutable history (W2 rule: versions append and void).

| Table | Key columns | Notes |
|---|---|---|
| `leave_types` | code PK, label, unit_kind, is_paid, counts_against_quota | controlled code table, migration only |
| `leave_rule_versions` | id, leave_type, legal_entity_id null, effective_from, voided_at, superseded_by, request_unit, allow_negative, exempt_threshold_minutes, proof_required, carry_over_cap_minutes null, carry_over_expiry_rule, basis_ref | EXCLUDE no overlap per (type, entity) among non-voided |
| `leave_entitlement_rules` | rule_version_id, service_years_from, service_years_to, minutes, proration_mode | child rows; values are T-1..T-3 |
| `leave_accounts` | id, employee_id, leave_type, leave_year null (null = rolling account), rule_version_id, state, sealed_at | unique (employee, type, year) for year accounts; unique (employee, type) where year is null |
| `leave_ledger_lines` | id, account_id, line_kind, quantity, source_kind, source_id, effective_date, expires_on null, consumes_line_id null, actor | unique (account, source_kind, source_id, line_kind, consumes_line_id); CHECK sign per kind; append-only trigger |
| `leave_grant_basis` | line_id, hire_date, prior_service_months, separation_date null, unpaid_days, employer_entity_id null, rule_version_id | inputs used by a GRANT/ADJUST line (3.5) |
| `leave_requests` | id, employee_id, leave_type, starts_at, ends_at, state, reason, proof_ref null, approval_instance_id null, legal_entity_id | cost legal entity of the person on the start day (RULE-398 scope) |
| `leave_request_holds` | request_id, line_id | one row per HOLD line, so a request spanning two years or several lots keeps all its holds |
| `leave_request_days` | request_id, work_date, minutes, day_fraction_hundredths | split per day (3.2) |
| `overtime_requests` | id, employee_id, work_date, minutes, day_kind, compensation_mode, state, approval_instance_id null, legal_entity_id | cost legal entity on the day |
| `attendance_records` | id, employee_id, record_kind, occurred_at, source_system, external_record_id | unique (source_system, external_record_id); append-only |
| `monthly_confirmations` | id, employee_id, month, state, confirmed_by_kind, locked_at | unique (employee, month); lines in child table |
| `monthly_confirmation_lines` | confirmation_id, line_kind, leave_type null, minutes, original_month | correction rows carry original_month |

Audit entries: written by each balance- or state-changing command, event-type name where written from an event (E-011). Register rows and exact `auditDetail` text are fixed in the first slice that writes them, not here (no behaviour to sign yet). Data-access registry and `data-classification.txt` entries for `leave_grant_basis` (personal) and `attendance_records` (personal; biometric and location columns only on the ADR-0080 decision 2 conditions) are part of the schema slice. Retention: attendance written records at least 2 years (ADR-0079:123); other retention is a business question (Q-3).

**Sign-off.** Not signed here. The pending columns (entitlement rule values T-1..T-8, retention Q-3) cannot be signed before the user answers (`data-design-gate.md` §1.3: no conditional pass). The L1 schema card carries the Appendix A material (table-to-object source, invariant-to-constraint table, use-case-to-table map, query paths, data classes, version/concurrency, registry and classification drafts, expected diff) and is signed there in the form `数据设计：通过（one-design，<date>，依据 <card commit sha>）`; tables that do not depend on the pending items (`attendance_records`, `overtime_requests`, `leave_requests`, `leave_request_days`, `leave_request_holds`, the monthly confirmation tables) may be signed first. This section is the design the L1 card starts from.

## 9. Business treatments for the user (via lead; not decided here)

| # | Question | Options | Recommendation |
|---|---|---|---|
| T-1 | Annual-leave entitlement by service years | (a) statutory 5/10/15 days at 1/10/20 years; (b) company-specific table (legacy table via L2/L3) | (b) if legacy shows a richer table; else (a) |
| T-2 | Which date moves a person to the next band | (a) 1 Jan of the year; (b) the anniversary date, prorated; (c) the first day of the next month | (a): simplest, no mid-year recalculation |
| T-3 | Proration for mid-year hires and separations, and rounding | proration by whole months or by days; round down / half / up to 0.5 or 1 day | follow the legacy result (L4, L5) so migrated people do not change |
| T-4 | May the balance go negative | never (ADR-0079:99 says quota is held first and never deducted into negative, for leave in general); or exceptions where policy allows (INV-68), which depart from ADR-0079:99 and need an ADR note; the closeout sends such exceptions to human approval | never for annual leave; any per-type exception goes through approval and an ADR note |
| T-5 | Effect of unpaid leave and long sick leave on entitlement | none; proportional cut; whole year cut above a threshold | follow L8; statutory note says cut applies above set thresholds |
| T-6 | Legal-entity move mid-year | balance follows the person; balance resets; split by entity | follows the person (one balance per person x type x year) |
| T-7 | Carry-over | none; capped carry-over expiring on a date; unlimited | follow L6; capped with expiry |
| T-8 | Comp time validity (how long) | N days, or never expires; at expiry the unused balance is reported to payroll and is not forfeited (Labour Law art. 44, PRC-178, RULE-516) | follow L10 for N |
| T-9 | Overtime approval: the approval platform (ADR-0079:125) or the punch check (closeout) | approver through the platform plus punch validation at submit; punch check only | platform plus punch validation |
| T-10 | Unused annual leave at separation: paid, lost, or paid only the statutory part | paid for the statutory part; company part per A8 | follow A8 (payroll form definitions in L0) and the law note |
| Q-1 | Include AGG-118 (rule version) in this context although the card names five aggregates | include / split to its own card | include |
| Q-2 | Leave year | calendar year / hire anniversary | calendar year (matches the golden sample) |
| Q-3 | Retention of leave and ledger records beyond the 2-year attendance minimum | per ADR default / longer | ledger kept with the employee record period |

## 9a. Payroll seam (what payroll reads from this context)

Full payroll design stays the W4 card (B-15, AGG-73, 93, 119). This section fixes only what payroll reads from `organization.leave`, because it decides the shape of AGG-97 and of the ledger. Sources: `planned.yaml` AGG-73, closeout `commands.yaml` (计算月度薪酬, PRC-178), RULE-516; the legacy payroll forms (`formmain_0800/0858/0864/1318`, unpaid-leave form `0863`) are read in L0 as definitions only. A line marked **open** needs the legacy definition or the user.

| Fact payroll reads | Grain | Source in this context | Frozen when | Status |
|---|---|---|---|---|
| Attendance days, leave minutes by type, overtime minutes by compensation mode | employee x month | AGG-97 `monthly_confirmation_lines` | `LOCKED` (INV-105); payroll computes only when every in-service person of the legal entity is locked, otherwise "待锁定" | settled by the sources |
| Unpaid-leave days and sick-leave days in the month | employee x month x leave type, minutes | AGG-97 lines by leave type (`is_paid` of the leave type decides whether payroll deducts) | same lock | which types deduct and at what rate is payroll's rule (W4), **open** |
| Overtime hours by kind (workday, rest day, statutory holiday) paid as pay | employee x month x day kind | AGG-97 overtime lines with `compensation_mode = PAY` | same lock | settled; day kind is carried on the overtime request |
| Comp time converted to pay | account line id, minutes, source (expired / separation), payroll month | event `LeaveCompTimeExpired` (expiry) and the read API of unused comp-time minutes (separation); payroll registers the conversion (PRC-178) | the registration, not this context | settled by the sources; one conversion per ledger line (idempotent) |
| Unused annual-leave days at separation (settlement) | employee, minutes | read API `LeaveBalances.unused(employee, leave type, as of date)` on `EmployeeSeparated` | at the separation date, per RULE-514 | whether unused annual leave is paid at separation is a business treatment (T-10, **open**) |
| Corrections after lock | employee x original month | AGG-97 correction rows of the next month | n/a | settled (INV-105) |
| Standard hours for the hourly rate | day | `organization.attendance` (AGG-108), not this context | n/a | out of this seam |

What this forces on the shapes: (1) AGG-97 lines are minutes by leave type and by overtime compensation mode, with the leave type's `is_paid` copied onto the line at generate time so a later rule change does not alter a locked month; (2) payroll never reads ledger lines or requests, only AGG-97 snapshots, the expiry event and two read-API calls; (3) comp-time conversion identity is the ledger line id; (4) the unpaid-leave start in workforce (`EmployeeUnpaidLeaveStarted`) is an input to entitlement (RULE-515), while the unpaid days of a month reach payroll through AGG-97, never through the workforce event.

## 10. Slice plan (implementation cards to open after sign-off)

| Slice | Content | Needs |
|---|---|---|
| L1 | Schema `organization_leave` (all tables of section 8), architecture guard for the module edges | this spec merged |
| L2 | AGG-118 rule version publish + read; leave types seed | L1, T-1..T-8 values as seed data |
| L0' | Prerequisite card (lead opens, design specifies): AGG-108 standard-hours version and its api in `organization.attendance` | none; blocks L3, L4 |
| L3 | AGG-71 account open, ledger, hold/release/use, year close; entitlement basis (3.5) | L0', L2; T-1..T-7 |
| L4 | AGG-70 leave request with approval, read API and events for the timesheet | L3, L0'; the timesheet seam card (4.3) follows |
| L5 | AGG-72 overtime and comp-time grant/expiry | L3; T-8, T-9 |
| L6 | AGG-97 monthly confirmation | L4, L5, attendance import (L7) |
| L7 | AGG-69 attendance records and import port | R2 decided; ADR-0080 decision 2 conditions for biometric and location columns |
| L8 | Golden-sample acceptance tests G1..G8 | legacy results; independent test engineer |

L1 can start as soon as this spec merges; L2 onward wait for the user's treatments. The golden sample is the acceptance input of L3..L5.

## 11. Done-when (for the test engineer)

1. Module guard: `organization.leave` depends only on workforce, attendance, platform.approval (and the audit port); the only module that may depend on it before the payroll context exists is `cost.timesheet`, through the narrowed edge of section 4.3, and only after that card is merged.
2. Each command above is reachable only through its permission; a foreign person's request, balance or confirmation returns 404.
3. Hold, release, use and reverse leave the derived balance as the formulas say; replaying the same source id changes nothing.
4. A sealed year refuses new lines; a correction is a line in the open year.
5. A leave over balance is refused or accepted exactly as the rule version's allow-negative flag says.
6. Approval result and withdraw follow `ApplyApprovalResult` behaviour: idempotent, race-safe with withdraw.
7. A locked month changes nothing; a later change appears as a correction row with the original month.
8. Golden cases G1..G8 pass with expected values from the user's legacy results.

## 12. Counter-examples invited

(For the reviewer.) A year-boundary leave request spanning two leave years; a hire event arriving after the first leave request; a legal-entity change whose rule version differs in carry-over; an approval completing after the request was withdrawn; comp time expiring on the same day it is used.
