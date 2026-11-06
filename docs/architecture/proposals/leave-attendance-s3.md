# Leave and attendance, slice S3: running the pack's seven nodes on organization.leave (#1581)

- Card: #1581 (design). Author: one-design, 2026-11-07. Base: origin/main `6a05eaf4a` plus later merges (fetched 2026-11-07).
- Companions: `organization-leave.md` (the sign-off spec of the context, #1279, called "the spec" below), `leave-quota-golden-sample.md`, data records `docs/data/reviews/organization-leave-l1a.md` and `-l1b.md` (the merged schema), and this card's record `docs/data/reviews/leave-attendance-s3.md`.
- Evidence tags: [V] = checked in this repository on 2026-11-07; [U] = unverified, the implementing card checks it; [P] = sample pack R002 (outside the repo, evidence only; its ids, words and structure are not adopted, only its expected numbers).
- Out of scope: code, payroll design (#1582), the attendance source system (R2) and punch import, overtime and comp time, carry-over and year close, statutory entitlement rules (T-1..T-8), UI.

## 1. The one thing to decide

How the pack's leave and attendance nodes 010, 013, 018, 019, 020, 021, 024 run on the leave context that the spec already designed and the merged schema already holds: what is reused, what is missing, what is added, in which slices, and what the test engineer checks. No quota policy and no business treatment is decided here.

## 2. Facts

| # | Fact | Source |
|---|---|---|
| 1 | The schema `organization_leave` exists: 13 tables in one baseline migration (729 lines), merged by #1400 and #1470 | [V] `db/migration/organization_leave/V20261105160000__organization_leave_baseline.sql` |
| 2 | The Java module is one file, `package-info.java`: no aggregate, command, controller, event or api type | [V] `organization/leave/` |
| 3 | `organization.attendance` (64 files) holds only the work calendar and standard-hours versions; its api is `WorkCalendar.isWorkday(date, legalEntityId)` and `StandardHours.minutesOn(legalEntityId, date)`. It has no attendance record, no monthly confirmation and no leave concept | [V] `organization/attendance/api/` |
| 4 | The spec's AGG-97 (monthly confirmation) and AGG-69 (attendance record) are tables in `organization_leave`, not in `organization.attendance` | [V] baseline lines 83-130 |
| 5 | A year account takes lines with no lot: the ledger trigger refuses `consumes_line_id` on a year account and requires it on a rolling account for HOLD, RELEASE, USE, REVERSE_USE, EXPIRE, ADJUST | [V] baseline `guard_leave_ledger_line` |
| 6 | `leave_ledger_lines.source_kind` allows `LEAVE_REQUEST, OVERTIME_REQUEST, HR_ADJUSTMENT, SEALED_YEAR_CORRECTION, RELATIONSHIP_EVENT, YEAR_OPEN, YEAR_CLOSE, COMP_TIME_EXPIRY, MIGRATION`: no value fits "a grant by a confirmed policy decision"; `reason_text` is allowed only on `ADJUST` | [V] baseline CHECKs |
| 7 | The unique source key is `(account, source_kind, source_id, line_kind, consumes_line_id)` with NULLS NOT DISTINCT, so one REVERSE_USE and one USE per request per year account | [V] `uk_leave_ledger_lines_source_key` |
| 8 | `monthly_confirmations` has no column for what the month was confirmed against (calendar or punches) | [V] baseline lines 99-117 |
| 9 | Approval is started with `ApprovalProcesses.start` and ends in `ApprovalCompleted`; a new document type needs its own BPMN file and numbering type (template: `organization_payroll_bonus_input.bpmn20.xml`, #1436) | [V] `resources/processes/`, `BonusInputApprovalProcess` |
| 10 | Payroll imports nothing from attendance or leave today | [V] grep of `organization/payroll` |
| 11 | The pack's nodes carry only expected numbers: 4500 credit, two days of 450 minutes, 900 reserved, used 900, restored 450, extra credit 450, second leave 900. It names no leave dates and no attendance day count for node 013 | [P] `cases/mainline/HCM-LIFE-010..024.yaml` |
| 12 | The pack's balance counters are credit, used, reserved, expired, available | [P] `fixtures/anchors.json` |
| 13 | A new rule version is refused when its `effective_from_date` is before today (Asia/Shanghai, the database clock) or not later than the latest version: no backdating | [V] baseline `guard_leave_rule_version_insert` |
| 14 | The migration baseline is open: `backend/architecture/migration-baseline-open` exists, so the merged baseline file may be edited and no migration is appended (data gate O12, open period) | [V] |
| 15 | The request overlap guard is `ex_leave_requests_live_period_overlap`: one relationship, `tstzrange(start_at, end_at)`, only for status `SUBMITTED`, `IN_APPROVAL`, `APPROVED`; a `RECALLED` request is outside it | [V] baseline line 24 |

## 3. Node map

"Reuse" = designed in the spec and its table exists; "new" = not in the spec or the schema (section 5).

| Node | Pack expectation [P] | Command / read / event | Reuse or new |
|---|---|---|---|
| 010 | credit 4500, available 4500, used 0, held 0; not a statutory annual-leave standard | `GrantPolicyCredit` (opens the year account if absent), read `LeaveBalances.of`; audit entry | account, ledger GRANT: reuse. Policy grant source and table, balance read: **new** |
| 013 | first month confirmed from the given calendar and first-day boundary; no fixed statutory proration stated | `GenerateMonthlyConfirmation`, `ConfirmByEmployee`, `LockMonthlyConfirmation`; event `MonthlyConfirmationConfirmed`; read `MonthlyAttendance.of` | AGG-97 tables, commands, states: reuse (spec 4.6). Calendar basis column, the read, the generate rule: **new** |
| 018 | two days of 450 minutes: available 3600, held 900, used 0 | `SubmitLeave` writes HOLD per year account, splits days by `StandardHours.minutesOn`, starts approval | reuse (spec 4.3, 4.2). Approval process definition: **new** (fact 9) |
| 019 | held 0, used 900, available 3600; no second deduction | `ApproveLeave` from `ApprovalCompleted` writes USE of the hold; event `LeaveApproved` | reuse (`ConsumeLeaveHold`) |
| 020 | actual 450 of 900: net used 450, available 4050; request and correction both kept | `RecallLeave` with a first day not taken writes REVERSE_USE for the untaken days; event `LeaveRecalled` | spec has `RecallLeave` but only as a whole-request reversal: partial recall **new** (4.3) |
| 021 | extra credit 450 from a confirmed rule: credit 4950, available 4500, used 450 | `GrantPolicyCredit` again, another grant id | as 010 |
| 024 | second leave 900: used 1350, held 0, available 3600 | `SubmitLeave` then `ApproveLeave` | as 018, 019 |

Balance formulas, checked against every pack number [P]: credit = GRANT + ROLLOVER_IN + positive ADJUST; held = HOLD - RELEASE - USE; used = USE - REVERSE_USE; available = credit - held - used - EXPIRE - ROLLOVER_OUT. 010: 4500. 018: held 900, available 3600. 019: held 0, used 900, available 3600. 020: used 450, available 4050. 021: credit 4950, available 4500. 024: held 900 then used 1350, available 3600.

## 4. Options and choices (technical; mine to choose)

**4.1 How a policy grant is recorded (nodes 010, 021).**

| | A. `ADJUST` line (HR adjustment) | B. New source `POLICY_GRANT` plus an append-only `leave_policy_grants` row (chosen) | C. A rule-computed grant (`leave_grant_basis`) |
|---|---|---|---|
| Fits "grant by a confirmed decision, with its source" | no: `ADJUST` is a correction and a signed quantity; credit would mix grants and corrections | yes | no: the quantity is a decision, not a formula of service years |
| Schema change | none | one CHECK value, one table | none |
| Credit stays a clean sum of grants | no | yes | yes |
| Source and operator recorded | `adjusted_by`, `reason_text` | `granted_by`, `basis_text` on the grant row | basis row of the computation |

Chosen B. Cost: one new table and one widened CHECK; benefit: credit equals the sum of grants, and every grant names who and on what basis.

**4.2 Account kind of the pack's leave type (nodes 010-024).** Year account or rolling account. Fact 5 decides the cost: a rolling account needs a lot on every HOLD and USE, so two grants (010, 021) make the 900-minute hold pick lots; the pack states no expiry (its `expired` counter is 0). Chosen: a **year account** for 2026 (calendar leave year, spec Q-2). The pack never crosses a year; whether a policy credit expires or rolls over is a business question (Q-S3-2) and, if yes, a later slice moves that type to rolling lots. No schema change either way.

**4.3 Partial recall (node 020).**

| | A. `RECALLED` terminal plus `taken_until_at` on the request and the overlap guard cut at it (chosen) | B. Request stays `APPROVED`, a separate recall table | C. Recall only the whole request |
|---|---|---|---|
| Node 020 (900 approved, 450 actual) | yes: untaken days returned | yes | no: would return 900 |
| Original request kept | yes (row and days unchanged) | yes | yes |
| The taken day stays blocked for a second request (fact 15) | yes: the guard covers `RECALLED` with its range cut at `taken_until_at` | yes (stays `APPROVED`) | yes |
| New schema | one nullable column and a redefined exclusion | one table | none |
| One reversal per request per account (fact 7) | holds | holds | holds |

Chosen A. `taken_until_at` is the start instant (business zone) of the first untaken day; the command writes it and publishes its business date as `recalledFromDate`. A recall whose `taken_until_at` equals `start_at` is the whole-request recall of the spec (an empty range), so one mechanism covers both. Without the cut a `RECALLED` request would drop its whole period, taken day included, out of the guard (reviewer counter-example, fact 15). `RECALLED` then means "ended before its planned last day"; a leave fully taken stays `APPROVED` (spec 4.3).

**4.4 What attendance days are confirmed against (node 013).** The spec says a missing import day is a failed precondition, never full attendance (4.6); the pack gives only the calendar and the first-day boundary [P], and the attendance source is undecided (R2). Options: (a) require punches: node 013 cannot run; (b) derive the month's attendance days from the work calendar of the cost entity, the relationship's service days and the approved leave, and record that basis; punches validate later. Chosen (b) as the first basis `CALENDAR`, a `basis_kind` column on the confirmation (`CALENDAR` or `PUNCHES`); a month with basis `PUNCHES` keeps the spec's failed precondition. Whether the business confirms against the calendar or the punches is Q-S3-1. The confirmation stores no proration factor: node 013 says the first-month proration follows an explicit input [P], and proration is payroll's (section 7).

**4.5 Which entity's calendar.** The cost (attribution) entity of the person on the day, as the user ruled for standard hours (Q-11 a, spec section 2). The first-day boundary is the relationship's start date and the end boundary its end date from `EmploymentRelationships`; unpaid-leave days from the relationship are not attendance days [U: the api field names are checked by the slice].

## 5. Changes to the spec and the schema (the record governs, section 8 of it)

1. New ledger source `POLICY_GRANT` (only with line kind GRANT) and new table `leave_policy_grants` (4.1).
2. `leave_requests.taken_until_at`, and the overlap exclusion redefined to cover `RECALLED` with the range cut there (4.3).
3. `monthly_confirmations.basis_kind` (4.4).
4. Spec 4.2 names the ledger kinds `CARRY_IN`, `CARRY_OUT`; the schema has `ROLLOVER_IN`, `ROLLOVER_OUT`. The schema governs; the spec text is read that way (no document edit of the spec in this PR).
5. Spec 4.2 says an account is never opened implicitly by a hold. `GrantPolicyCredit` may open the year account (rule version in force on the business day of the command, else it fails; the grant's `effective_date` may not be earlier than that day, see Q-S3-6), because a policy credit has no relationship-start event to open it.
6. No new module edge: leave already depends on workforce, attendance and platform.approval (spec 4).

## 6. Events

Payload tables are in the record. This PR does not write them into the leave event catalogue file, which does not exist yet; the catalogue test fails when the catalogue names an event class absent from the code (same conflict as `workforce-acting-duty.md`; the implementing slice adds the class and the yaml entry together).

| Event | Raised by | Consumers now | Notes |
|---|---|---|---|
| `LeaveApproved` | node 019 | none registered; `cost.timesheet` after the seam card (spec 4.3) | `TimesheetOvertimeSeamGuardTest` keeps that edge closed |
| `LeaveRecalled` | node 020 | same | carries `recalledFromDate` (the business date of `taken_until_at`) and returned minutes |
| `MonthlyConfirmationConfirmed` | node 013 | `platform.audit` | |
| `MonthlyConfirmationLocked` | lock | payroll (#1582, assumed) | |

## 7. Seam to payroll (what node 014 will read; payroll is not designed here)

- Payroll reads the locked monthly confirmation only, through a new read `MonthlyAttendance.of(employeeId, month)` that returns, per relationship, attendance days (hundredths), leave minutes by leave type with the copied `is_paid_leave`, and the basis kind; it never reads requests or ledger lines (spec 9a).
- A month is readable only when `LOCKED`; locking is a command (`LockMonthlyConfirmation`) that runs before payroll computes; node 013 asserts `CONFIRMED` on 2026-01-31 and the lock is asserted separately.
- The pack's first-month gross, deductions and net are approved inputs, not a formula of attendance [P: `payroll_input_status SYNTHETIC_CONFIRMED_INPUT_NOT_STATUTORY_FORMULA`]. So S4 needs from this seam only "the month is confirmed and locked". Whether payroll later derives a proration from attendance days (this needs the month's scheduled days as a denominator, which the confirmation does not hold) is left to #1582.
- Assumed by #1582 until this design is signed: the read name and its three fields above; the lock-before-payroll order; the event `MonthlyConfirmationLocked`.

## 8. Slicing

Each slice is one behaviour with its own tests. Owner: lead assigns. Slice A is a schema PR and needs the record's signature (merged with this PR) before it starts.

| Slice | Behaviour | Nodes | Needs |
|---|---|---|---|
| A | Schema PR: edit the open baseline file (fact 14), no appended migration: `POLICY_GRANT`, `leave_policy_grants`, `taken_until_at` and the redefined overlap guard, `basis_kind`; registry and classification entries; dev database rebuilt | all | this PR merged |
| B | Leave types read, rule version publish and read (a version with no entitlement rows is allowed for a policy type), the module's api package | 010 | A |
| C | `GrantPolicyCredit`, the year account open, `LeaveBalances.of` | 010, 021 | B |
| D | `SubmitLeave` (day split, HOLD, relationship-in-service check, approval start) and the approval process definition and numbering type | 018, 024 | C |
| E | `ApproveLeave` (USE of the hold), `RejectLeave` and `WithdrawLeave` (RELEASE), event `LeaveApproved` | 019, 024 | D |
| F | `RecallLeave` with `taken_until_at`, event `LeaveRecalled` | 020 | E |
| G | Monthly confirmation generate, confirm, lock with basis `CALENDAR`, the read `MonthlyAttendance.of`, events | 013 | D (leave lines), E |
| I | Web pages for the role walks of section 9.2 (frontend session; page list waits for the role-task mapping, B11.5) | all | C-G |
| H | Acceptance tests for the seven nodes, API side and seam tests (one-test, not design) | all | C-G |

C to F run in sequence (same aggregate); G can start after E; I follows each of C to G page by page. A slice is done only when its role walk (T3) and its seam tests (T4) of section 9 pass.

## 9. Done-when (for the test engineer; one checkable line each)

Dates and numbers. The pack's amounts are asserted as they are (4500, 900, 450, 3600, 4050, 4950, 1350). The pack's calendar dates are labels, not fixture dates: a rule version cannot be backdated (fact 13), so every fixture date is today-relative. D0 = today of the database clock (Asia/Shanghai); the rule version of the fixture type takes effect on D0; the application `Clock` is fixed at D0 and moved forward within the test (CS-5.8); the relationship starts on D0; the leave days are two workdays after D0; the confirmation month is the month containing D0.

### 9.1 Behaviour (each line is an API-level test)

1. A grant of 4500 minutes to a minute-unit type for a relationship in service on D0 reads credit 4500, held 0, used 0, available 4500 through `LeaveBalances.of`; the ledger line has source `POLICY_GRANT`, the grant row names its basis, and an audit entry exists.
2. A second call with the same `request_key` returns the same grant and writes no second row or line; a different key with the same quantity writes a second grant; quantity 0 or negative is refused; a grant for a relationship not in service on its `effective_date` is refused; a grant dated before the business day is refused (Q-S3-6 pending, then as answered).
3. The grant touches no statutory annual-leave account: no `leave_grant_basis` row is written and the person's annual-leave balance is unchanged.
4. Generating the confirmation of D0's month for a relationship that started on D0 gives basis `CALENDAR`; attendance days equal the workdays from D0 to the month's last day of the cost entity's calendar (the test counts them from the fixture calendar; the pack states no count [U]), no day before D0 is counted, no proration factor is stored.
5. `ConfirmByEmployee` by the person on the month's last day moves the month to `CONFIRMED`; another person's call returns 404; regenerate after confirm is refused; after lock nothing changes and a later change is a correction row of the next month (INV-105).
6. A request for two workdays of 450 standard minutes each against available 4500 leaves held 900, available 3600, used 0 and the request `IN_APPROVAL`; a request above the available balance is refused (T-4); a second request overlapping the first is refused.
7. Approval completion turns the hold into use: held 0, used 900, available 3600, request `APPROVED`; delivering the same `ApprovalCompleted` again, or the same consume command, writes no second USE.
8. A recall whose first untaken day is the second day returns 450: used 450, available 4050, request `RECALLED` with `taken_until_at` at the start of that day; its days, its HOLD and its USE lines are unchanged and one REVERSE_USE of 450 exists; `LeaveRecalled` is published once; a new request on the taken first day is still refused, a new request on the returned second day is accepted.
9. A second grant of 450 reads credit 4950, available 4500, used 450.
10. A second request of 900 approved reads used 1350, held 0, available 3600.
11. A rejected or withdrawn request releases its hold: held 0 and available as before the request.
12. A confirmation generated after node 020 lists approved leave by type with `is_paid_leave` copied, counts the taken part of the `RECALLED` request (450 minutes, the days before `taken_until_at`), and counts neither an `IN_APPROVAL` request nor the returned day.
13. Each new database rule is violated once in a test and refused: a `POLICY_GRANT` line that is not a GRANT; `taken_until_at` set on a request that is not `RECALLED`, or outside `[start_at, end_at]`; a `basis_kind` outside the two values; a second grant with the same `(account, request_key)`.
14. A foreign person's request, balance or confirmation returns 404; every command is reachable only through its permission.

### 9.2 Role walks in the web UI (T3: each activity walked by the real role, no admin shortcut)

Roles (user decisions 2026-11-06): employee; direct manager; HR lead (a separate approval layer); HR specialist (also holds the attendance-specialist role); permission administrator. A separated employee cannot log in; the HR specialist acts for them.

15. HR specialist grants a policy credit (nodes 010, 021) on the HR page and sees credit and available change; an employee account sees the same balance on its own page and cannot see another person's.
16. Employee submits the two-day request (018, 024) on the own-leave page; the direct manager finds it in the to-do list and approves; the HR-lead layer approves it when the route includes that layer (Q-S3-7 pending: layer condition); the employee sees held, then used; the applicant's "my requests" list shows each state.
17. HR specialist registers the recall (020) for the applicant, or for a separated applicant (login impossible); the employee's page shows used 450 and the request `RECALLED`; the manager's to-do list shows nothing new.
18. Employee confirms the month on the attendance page (013); for a separated employee the HR specialist confirms by default (`ConfirmByDefault`); the HR specialist locks the month; a non-HR role sees no lock action.
19. Permission administrator sees the leave permissions granted automatically by source (employee, manager, HR specialist, HR lead) in the grant review list and can revoke one; the revoked role's page action disappears. [U: the review list is the identity module's existing mechanism]

### 9.3 Seam tests (T4: both sides of each seam, numbers as in section 12.1)

20. Seams 1, 2, 3, 4: leave's test calls the real attendance and workforce read ports (no stubs); each port's own contract test covers its failure answers (no standard-hours version, calendar year not maintained, relationship not in service).
21. Seam 5: a leave request starts a real approval process and the result returns by `ApprovalCompleted`; the approval-side test of the new process definition checks its route (manager, then the HR-lead layer by Q-S3-7).
22. Seam 6: the provider test asserts the `LeaveApproved` and `LeaveRecalled` payloads field by field against the record; the consumer side waits for the timesheet seam card (listed, not skipped): until then a recorded-payload contract test in leave stands in for it.
23. Seam 7: the provider test asserts `MonthlyAttendance.of` and `MonthlyConfirmationLocked` against the record, including `is_paid_leave` and basis; `MonthlyAttendance.of` on an unlocked month fails; the consumer side is #1582's.
24. Seam 8: `LeaveBalances.of` is asserted for credit, held, used, expired, available after every pack step (1 to 10 above).

## 10. Questions only the user can answer (none decided here)

| # | Question | Options | Recommendation |
|---|---|---|---|
| Q-S3-1 | Is the monthly attendance confirmed against the work calendar and service days, or against punches? | calendar now, punches when the attendance source (R2) is decided; punches only | calendar now, punches later: node 013 runs without a source system, and the basis is recorded |
| Q-S3-2 | Does a policy-granted credit expire or roll over at year end? (the pack carries an `expired` counter) | never expires within its year only; expires at a date; rolls over with a cap | the grant's rule version states it; until the user names a policy, no expiry inside the year and nothing carried |
| Q-S3-3 | Which leave types exist in production, with unit and paid flag? The type table is migration-only; the pack's type is fictional and lives in test fixtures | the user lists them | the user lists them before the first production seed; not needed for S3 tests |
| Q-S3-4 | Who may grant a policy credit, and does a grant need approval? | HR permission with audit; HR permission plus approval | HR permission with audit, as for other HR adjustments |
| Q-S3-5 | Who verifies the actual leave and registers the recall (node 020 says the actual days are verified)? | the applicant; the direct manager; HR specialist | HR specialist registers it after verifying (also the only path for a separated employee); the data shape is the same for any of them |
| Q-S3-6 | May a policy credit be dated before the day it is entered (backdated)? The rule-version guard already refuses a backdated version, so a backdated grant would have no version in force | no, effective date is the entry day or later; yes with HR-lead approval | no, as the guard does |
| Q-S3-7 | When does the HR-lead approval layer apply to a leave request (user decision 2026-11-06: the layer exists)? | every request; above a number of days; for given leave types | above a number of days the user names; until then the process definition has the layer behind a threshold of the rule version |

## 11. Unverified (the implementing slice checks; none changes a column)

1. The pack names no leave dates for node 018, so the test picks two workdays after 2026-05-04 and one day before the recall on 2026-05-08 [P].
2. A rule version with zero entitlement rows is accepted: the entitlement trigger fires only on entitlement inserts (reviewer read, 2026-11-07); the backdating guard is a fact (13), handled by today-relative fixtures in section 9.
3. Field names of `EmploymentRelationships` for the service boundary and unpaid periods.
4. The approval process type and the numbering document type for a leave request follow the #1436 template; the events documents list the new document type (as `58535e073` did for `BONUS_INPUT`).

## 12. Seams this slice defines or reuses

Rule (the user's HR development chart, `04-P03-HR_dev-strategy-v0.svg`, read-only): the first slice that crosses a seam fixes its contract; later slices only add fields or commands, never change one. The chart is a sketch and its seam names are drafts (lead, 2026-11-07): the seams below are **derived** from the signed spec, the sample pack and the code on main, and a chart name is used only where it matches. Every mismatch is in the table of 12.2 for the user to review. "Defined here" = this slice fixes the contract; "reused" = fixed by an earlier slice or by code already on main; "crossed here" = both sides are built in this slice (so T4 can test both); "provider side only" = the consumer is built by a later slice.

### 12.1 The seams (provider -> consumer)

| # | Seam (chart name where it matches) | Provider -> consumer | Contract | Status in S3 |
|---|---|---|---|---|
| 1 | 标准工时 | `organization.attendance` -> `organization.leave` | read port `StandardHours.minutesOn(legalEntityId, date)` -> minutes; fails when no version covers the date | reused (#1310) [V]; crossed here by `SubmitLeave` (day split) |
| 2 | (none in the chart) work calendar | `organization.attendance` -> `organization.leave` | read port `WorkCalendar.isWorkday(date, legalEntityId)`; fails when the year is not maintained | reused [V]; crossed here by `SubmitLeave` and `GenerateMonthlyConfirmation` |
| 3 | 承担成本 | `organization.workforce` -> `organization.leave` | read port `EmployeeLegalEntities.on(employee, date)` -> the cost (attribution) entity; it is the entity whose calendar and standard hours apply (Q-11 a) and the `legal_entity_id` of a request | reused [V: has outside callers on main]; crossed here |
| 4 | (the chart's 任职生效 / 任职结束 are events; the service boundary is a read) | `organization.workforce` -> `organization.leave` | read port `EmploymentRelationships` (service period and unpaid-leave periods of a relationship) [U: field names]; the events `EmploymentRelationshipStarted/Ended` are **not** consumed in S3 | read reused (ADR-0086, on main); the two events are not crossed in S3 (Ended: the S5 separation slice cancels open leave, spec 4.3; Started: the entitlement slice opens the annual account) |
| 5 | (none in the chart) approval | `organization.leave` -> `platform.approval` -> `organization.leave` | `ApprovalProcesses.start` per (process type, document, round); result `ApprovalCompleted`; withdraw | reused (platform pattern, template #1436 [V]); crossed here; the leave process definition is new (slice D) |
| 6 | 请假事实 | `organization.leave` -> `cost.timesheet` | events `LeaveApproved`, `LeaveRecalled` (payloads in the data record) and later the read `LeaveHours.forDay` (spec 4.3) | **defined here** (payloads); provider side only: `TimesheetOvertimeSeamGuardTest` keeps the edge closed until the seam card opens it |
| 7 | 考勤结果 | `organization.leave` -> `organization.payroll` | read `MonthlyAttendance.of(employeeId, month)` (per relationship: attendance days, leave minutes by type with `is_paid_leave`, basis kind) readable only when `LOCKED`; event `MonthlyConfirmationLocked` | **defined here**; provider side only until #1582 builds the consumer |
| 8 | 关系额度 | `organization.leave` -> `organization.payroll` (and own UI) | S3 defines the read `LeaveBalances.of(relationship, type, year)` (credit, held, used, expired, available). The spec's `LeaveBalances.unused(...)` for separation settlement is **added** by the S5 slice | `of` defined here, consumed by leave's own pages and tests only; `unused` not crossed |
| 9 | (none in the chart) audit | `organization.leave` -> `platform.audit` | audit entry per balance- or state-changing command; events audited | reused (platform) |
| 10 | (none in the chart) permissions | `platform.identity` -> `organization.leave` | permission codes registered per role; no leave table reads identity | reused; new codes per slice |

Seams of the chart that S3 does not touch: 岗位 (leave uses no position), 奖金输入 (payroll side, #1436), 支付结果, 未结事项, 扣缴义务, 个人义务 (payroll and settlement slices).

### 12.2 Where the derived seams differ from the chart (for the user to review)

| Chart seam | Derived here | Kind of mismatch |
|---|---|---|
| 标准工时 | seam 1, same direction; the work calendar (seam 2) is a second read of the same provider that the chart does not list | chart seam merged: the calendar is missing or folded into 标准工时 |
| 承担成本 | seam 3, matches in meaning (cost entity of a person on a day) | none, name kept |
| 任职生效, 任职结束 | seam 4: for leave the relationship matters as a read (service boundary) in S3; the two events are only consumed from S5 (ended) and from the entitlement slice (started) | split: one chart seam is a read and two events, scheduled in different slices |
| 请假事实 | seam 6, direction leave -> timesheet; also leave -> payroll through the monthly confirmation, which the chart may count under 考勤结果 | same name, two consumers: needs the user to say whether payroll reads leave minutes as 请假事实 or only inside 考勤结果 |
| 考勤结果 | seam 7, provider is `organization.leave` (the spec's AGG-97), not `organization.attendance`; the chart names the BC 工作日历 for attendance | wrong owner suspected: the monthly confirmation lives in leave by the signed spec (4.6) |
| 关系额度 | seam 8, defined here only as the balance read; its payroll use (unused leave at separation) is S5 | meaning unclear: the chart does not say whether it is the balance or the entitlement basis (service years of a relationship); derived as the balance |
| (missing) approval, audit, permissions | seams 5, 9, 10 | chart seam missing: platform seams, perhaps meant to be the 地基 (platform cross-cutting) row |
| 岗位, 奖金输入, 支付结果, 未结事项, 扣缴义务, 个人义务 | not touched by S3 | none for this slice |

#1582 reuses seams 1, 2, 3, 7 and 8 as fixed here and only adds (for example `LeaveBalances.unused`, payroll-side fields of `MonthlyAttendance`).
