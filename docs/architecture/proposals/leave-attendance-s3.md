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

| | A. `RECALLED` terminal plus `recalled_from_date` on the request (chosen) | B. Request stays `APPROVED`, a separate recall table | C. Recall only the whole request |
|---|---|---|---|
| Node 020 (900 approved, 450 actual) | yes: untaken days returned | yes | no: would return 900 |
| Original request kept | yes (row and days unchanged) | yes | yes |
| New schema | one nullable column | one table | none |
| One reversal per request per account (fact 7) | holds | holds | holds |

Chosen A. A recall with `recalled_from_date` equal to the first leave day is the whole-request recall of the spec, so one mechanism covers both. `RECALLED` then means "ended before its planned last day"; a leave fully taken stays `APPROVED` (spec 4.3).

**4.4 What attendance days are confirmed against (node 013).** The spec says a missing import day is a failed precondition, never full attendance (4.6); the pack gives only the calendar and the first-day boundary [P], and the attendance source is undecided (R2). Options: (a) require punches: node 013 cannot run; (b) derive the month's attendance days from the work calendar of the cost entity, the relationship's service days and the approved leave, and record that basis; punches validate later. Chosen (b) as the first basis `CALENDAR`, a `basis_kind` column on the confirmation (`CALENDAR` or `PUNCHES`); a month with basis `PUNCHES` keeps the spec's failed precondition. Whether the business confirms against the calendar or the punches is Q-S3-1. The confirmation stores no proration factor: node 013 says the first-month proration follows an explicit input [P], and proration is payroll's (section 7).

**4.5 Which entity's calendar.** The cost (attribution) entity of the person on the day, as the user ruled for standard hours (Q-11 a, spec section 2). The first-day boundary is the relationship's start date and the end boundary its end date from `EmploymentRelationships`; unpaid-leave days from the relationship are not attendance days [U: the api field names are checked by the slice].

## 5. Changes to the spec and the schema (the record governs, section 8 of it)

1. New ledger source `POLICY_GRANT` (only with line kind GRANT) and new table `leave_policy_grants` (4.1).
2. `leave_requests.recalled_from_date` (4.3).
3. `monthly_confirmations.basis_kind` (4.4).
4. Spec 4.2 names the ledger kinds `CARRY_IN`, `CARRY_OUT`; the schema has `ROLLOVER_IN`, `ROLLOVER_OUT`. The schema governs; the spec text is read that way (no document edit of the spec in this PR).
5. Spec 4.2 says an account is never opened implicitly by a hold. `GrantPolicyCredit` may open the year account (rule version in force on the day, else it fails), because a policy credit has no relationship-start event to open it.
6. No new module edge: leave already depends on workforce, attendance and platform.approval (spec 4).

## 6. Events

Payload tables are in the record. This PR does not write them into `docs/architecture/events/leave.yaml`: that file does not exist and `EventCatalogueTest` fails when the catalogue names an event class absent from the code (same conflict as `workforce-acting-duty.md`; the implementing slice adds the class and the yaml entry together).

| Event | Raised by | Consumers now | Notes |
|---|---|---|---|
| `LeaveApproved` | node 019 | none registered; `cost.timesheet` after the seam card (spec 4.3) | `TimesheetOvertimeSeamGuardTest` keeps that edge closed |
| `LeaveRecalled` | node 020 | same | carries `recalledFromDate` and returned minutes |
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
| A | Migration: `POLICY_GRANT`, `leave_policy_grants`, `recalled_from_date`, `basis_kind`; registry and classification entries | all | this PR merged |
| B | Leave types read, rule version publish and read (a version with no entitlement rows is allowed for a policy type), the module's api package | 010 | A |
| C | `GrantPolicyCredit`, the year account open, `LeaveBalances.of` | 010, 021 | B |
| D | `SubmitLeave` (day split, HOLD, relationship-in-service check, approval start) and the approval process definition and numbering type | 018, 024 | C |
| E | `ApproveLeave` (USE of the hold), `RejectLeave` and `WithdrawLeave` (RELEASE), event `LeaveApproved` | 019, 024 | D |
| F | `RecallLeave` with `recalled_from_date`, event `LeaveRecalled` | 020 | E |
| G | Monthly confirmation generate, confirm, lock with basis `CALENDAR`, the read `MonthlyAttendance.of`, events | 013 | D (leave lines), E |
| H | Acceptance tests for the seven nodes (one-test, not design) | all | C-G |

C to F run in sequence (same aggregate); G can start after E.

## 9. Done-when (for the test engineer; one checkable line each)

1. A grant of 4500 minutes to a minute-unit type for a relationship in service on 2026-01-05 reads credit 4500, held 0, used 0, available 4500 through `LeaveBalances.of`; the ledger line has source `POLICY_GRANT`, the grant row names its basis, and an audit entry exists.
2. Re-delivering the same grant changes nothing; a grant with quantity 0 or a negative one is refused; a grant for a relationship not in service on the day is refused.
3. The grant does not touch any statutory annual-leave account: no `leave_grant_basis` row is written and the person's annual-leave balance is unchanged.
4. Generating the confirmation of 2026-01 for a relationship that started 2026-01-05 gives basis `CALENDAR`; attendance days equal the workdays from 2026-01-05 to 2026-01-31 of the cost entity's calendar (the test counts them from the fixture calendar; the pack states no count [U]), no day before 2026-01-05 is counted, and no proration factor is stored.
5. `ConfirmByEmployee` by the person on 2026-01-31 moves the month to `CONFIRMED`; another person's call returns 404; regenerate after confirm is refused; after lock nothing changes and a later change is a correction row of the next month (INV-105).
6. A request for two workdays of 450 standard minutes each against available 4500 leaves held 900, available 3600, used 0 and the request `IN_APPROVAL`; a request above the available balance is refused (T-4).
7. Approval completion turns the hold into use: held 0, used 900, available 3600, request `APPROVED`; delivering the same `ApprovalCompleted` again, or the same consume command, writes no second USE.
8. A recall whose first untaken day is the second day returns 450: used 450, available 4050, request `RECALLED` with `recalled_from_date` set; the request, its days, the HOLD and the USE lines are unchanged and one REVERSE_USE of 450 exists; `LeaveRecalled` is published once.
9. A second grant of 450 reads credit 4950, available 4500, used 450.
10. A second request of 900 approved reads used 1350, held 0, available 3600.
11. A rejected or withdrawn request releases its hold: held 0 and available as before the request.
12. A confirmation generated after node 020 lists approved leave by type with `is_paid_leave` copied, and counts neither an `IN_APPROVAL` request nor a recalled day.
13. Each new database rule is violated once in a test and refused: a `POLICY_GRANT` line that is not a GRANT; `recalled_from_date` set on a request that is not `RECALLED`; a `basis_kind` outside the two values.
14. A foreign person's request, balance or confirmation returns 404; every command is reachable only through its permission.

## 10. Questions only the user can answer (none decided here)

| # | Question | Options | Recommendation |
|---|---|---|---|
| Q-S3-1 | Is the monthly attendance confirmed against the work calendar and service days, or against punches? | calendar now, punches when the attendance source (R2) is decided; punches only | calendar now, punches later: node 013 runs without a source system, and the basis is recorded |
| Q-S3-2 | Does a policy-granted credit expire or roll over at year end? (the pack carries an `expired` counter) | never expires within its year only; expires at a date; rolls over with a cap | the grant's rule version states it; until the user names a policy, no expiry inside the year and nothing carried |
| Q-S3-3 | Which leave types exist in production, with unit and paid flag? The type table is migration-only; the pack's type is fictional and lives in test fixtures | the user lists them | the user lists them before the first production seed; not needed for S3 tests |
| Q-S3-4 | Who may grant a policy credit, and does a grant need approval? | HR permission with audit; HR permission plus approval | HR permission with audit, as for other HR adjustments |
| Q-S3-5 | Who verifies the actual leave and registers the recall (node 020 says the actual days are verified)? | the applicant; the direct manager; HR | HR or the direct manager registers it after verifying; the data shape is the same for any of them |

## 11. Unverified (the implementing slice checks; none changes a column)

1. The pack names no leave dates for node 018, so the test picks two workdays after 2026-05-04 and one day before the recall on 2026-05-08 [P].
2. The guard triggers accept a rule version with zero entitlement rows (spec 4.1 allows the structure; the trigger bodies were read only for the ledger and the account).
3. Field names of `EmploymentRelationships` for the service boundary and unpaid periods.
4. The approval process type and the numbering document type for a leave request follow the #1436 template; the events documents list the new document type (as `58535e073` did for `BONUS_INPUT`).
