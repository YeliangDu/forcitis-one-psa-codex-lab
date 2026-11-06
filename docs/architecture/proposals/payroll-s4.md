# Payroll, slice S4: running the pack's pay nodes 008, 014 and 029 on organization.payroll (#1582)

- Card: #1582 (design). Author: one-design, 2026-11-07. Base: origin/main `a68241f24`.
- Companions: `organization-payroll.md` (the sign-off spec of the context, #1403, called "the spec" below; sections 4.2, 4.3, 4.6, 8, 10), the signed records `docs/data/reviews/organization-payroll-py0.md` and `docs/data/reviews/organization-payroll-py3.md` (the merged schema: rule versions, rule rows, bonus inputs), and this card's record `docs/data/reviews/payroll-s4.md`.
- The S3 design (PR #1583, the proposal leave-attendance-s3.md under docs/architecture/proposals once merged) defines the leave and attendance seams this slice consumes; its section 12 is the source of seams 1 and 2 below. Until that PR is signed every input from S3 is **assumed** and listed in section 7.
- Evidence tags: [V] = checked in this repository on 2026-11-07; [U] = unverified, the implementing card checks it; [P] = sample pack R002 (outside the repo, evidence only; its ids, words and structure are not adopted, only its expected numbers).
- Out of scope: code; the statutory formulas (IIT, social insurance, housing fund), pay raises and pay changes, bonus, separation settlement, posting to the ledger, filing, special deductions, the history import, UI.

## 1. The one thing to decide

How the pack's pay nodes 008 (first pay and benefits plan), 014 (first month payroll and payment) and 029 (allowance handled apart from the certificate) run on the payroll context that the spec already designed and the merged schema partly holds: what is reused, what is missing, what is added, in which slices, and what the test engineer checks. The statutory amounts are not computed here; the pack says so itself. No business treatment is decided.

## 2. Facts

| # | Fact | Evidence |
|---|---|---|
| 1 | `organization.payroll` has 40 java files and one controller (the bonus input approval read); the api holds `BonusInputs`, `PayrollRuleVersions`, `BonusInputApprovalProcess` (process key, approver role `payroll_specialist`) | [V] `organization/payroll/` |
| 2 | The merged schema holds three tables: `payroll_rule_versions`, `payroll_rule_rows` (no rows), `bonus_inputs`; no profile, enrolment, allowance, input, settlement or disbursement table exists | [V] baseline `V20261105173000__organization_payroll_baseline.sql` |
| 3 | The module edges on main are `organization.payroll -> platform.audit, platform.approval, platform.identity, organization.workforce`; no edge to `organization.leave`, `platform.integration` or any payables or facts module; no `payables` package exists | [V] `module-dependencies.txt:22`; `ls backend/src/main/java/com/forcitis/one` |
| 4 | `EmploymentRelationships` offers `get`, `employerOn(id, date)` and `on(employee, date)`; it has no listing of the relationships in service in a month | [V] `organization/workforce/api/EmploymentRelationships.java` |
| 5 | The migration baseline is open: `backend/architecture/migration-baseline-open` exists, so the merged baseline is edited and no migration is appended (data gate O12, open period) | [V] |
| 6 | The spec already designs the settlement (AGG-73: states `DRAFT`, `CALCULATED`, `IN_REVIEW`, `APPROVED`, `CANCELLED`; unique per paying entity and month; calculation only when every in-service person is `LOCKED`), the compensation profile (AGG-93, component structure marked "waits for #1373"), the enrolment (AGG-74) and the disbursement; slice names PY1, PY2, PY4, PY6, PY9 | [V] spec 4.2, 4.3, 4.6, 8, 10 |
| 7 | The statutory formulas and the legacy manual steps are not known (#1373 open); the spec lists every value that depends on them as open | [V] spec header, section 9 |
| 8 | Registered glossary terms exist for `employee_compensation_profile`, `monthly_payroll_settlement_statement`, `payroll_settlement_line`, `payroll_disbursement`, `register_payroll_disbursement_result`, `payroll_disbursement_result_registered` | [V] `docs/glossary/terms.yaml` |
| 9 | The sample pack's monthly rows carry "confirmed" gross, deductions and net with the status "synthetic confirmed input, not statutory formula" and name the source of each month as an approved input | [P] `cases/monthly_cycles.json` |
| 10 | `bonus_inputs` shows the approved-input pattern on main: a row is written when its approval completes (approval instance id unique), `ACTIVE`/`VOIDED`, partial unique business key | [V] baseline lines 206-238; `docs/data/reviews/organization-payroll-py3.md` |
| 11 | The payroll rule version trigger sets no clock rule on `effective_from_date`; the leave rule version trigger does (S3 fact 13) | [V] baseline `guard_rule_version_change` |

## 3. Node map (pack expected values [P])

| Node | Pack statement | Commands, reads, events | Reuse or new |
|---|---|---|---|
| 008 first pay and benefits plan | monthly pay 20000 approved; insurance, housing fund and withholding plan given; the pay file belongs to the relationship (ER-001-A, employer LE-A); the statutory amounts come from external confirmed input, no formula | `CreateCompensationProfile` (base monthly amount, paying entity, decision number); `EnrolInsurance` (kinds, status, effective date); reads `CompensationProfiles.inForce(relationship, date)` | spec 4.2 and 4.3: aggregates reused as named; the component structure narrowed (4.1); both tables **new** (record) |
| 014 first month payroll and payment | confirmed gross 18000, confirmed deductions 3000, net 15000; paid on the 10th of the next month, successfully; the further monthly rows are in the monthly cycle table and are not booked again here | `RecordConfirmedPayrollInput`, `VoidConfirmedPayrollInput`, `CalculateMonthlyPayroll`, `SubmitPayrollForReview`, `ApproveMonthlyPayroll`, `CancelPayrollSettlement`, `RegisterPayrollDisbursementResult`, `InitiatePayrollRedisbursement`; event `PayrollApproved` | spec 4.6 states and commands reused; the input port, the narrowed calculation and the stub payment port **new** (4.2, 4.4) |
| 029 certificate and allowance apart | the allowance decision is approved elsewhere; the allowance version ALLOW-A-V1 has its own period and source; qualification evidence is not equal to a wage amount | `CreateAllowanceVersion`, `EndAllowanceVersion`; read `CompensationProfiles.allowancesOn(relationship, date)` | **new**: the spec put allowances inside the profile components (4.1) |

Numbers the tests assert [P, verified by running the pack file]: month 2026-01 for ER-001-A: gross 18000.00, deductions 3000.00, net 15000.00 (= gross - deductions), payment on 2026-02-10; all 24 monthly rows satisfy net = gross - deductions and sum to gross 549000.00, deductions 90000.00, net 459000.00 (the sums are the pack's regular totals); a person with two relationships has one monthly row series per relationship (ER-001-A with employer LE-A until 2027-01-01, ER-001-B from 2027-03-01). S4 asserts the first month only and the per-relationship ownership; the other rows are other nodes' (pay changes, final settlement).

## 4. Options

**4.1 Where the allowance lives (node 029).** (a) Components as lines of the profile version (spec 4.2: base, allowances, subsidies as `component_key` plus amount); (b) base pay in the profile version and each allowance as its own versioned row with its own period and source. The pack says an allowance has its own period and source and that qualification evidence is not a wage amount [P]. With (a) one allowance change writes a new profile version, so the allowance's period is the profile's; two allowances with different periods interleave into a version for every change, and the source of each line is lost. Chosen (b): `compensation_allowance_versions`; the profile version holds the base pay only in S4. The spec's component structure stays open for the formula slice, which can add component rows without touching either table. No column points at qualification evidence: the allowance decision number is the only source, which is the pack's separation.

**4.2 Statutory amounts (node 014).** (a) Compute IIT, insurance and fund from rule rows: the formulas and values are open (#1373, P-1, P-2) and the rule rows are empty [V]; (b) take the confirmed gross and the confirmed total deduction as an approved input per relationship and month and compute only net = gross - deductions. The pack's first month does not follow a formula (18000 against a 20000 pay) and calls its inputs confirmed [P]. Chosen (b) as the first basis: a `payroll_confirmed_inputs` row; it is the same port the formula slice later feeds with computed values, so no payroll code changes then. Consequence: the calculation of S4 reads no rule version, so the spec's "no effective rule version fails" check (spec done-when 3) is not exercised in S4 and is owed to the formula slice. Whether the business accepts (b) is Q-S4-1.

**4.3 What the settlement line stores.** (a) Only the totals per settlement; (b) one line per relationship with gross, deduction and net (spec 4.6, 8) and the profile version in force as evidence. Chosen (b): the disbursement needs a per-person amount (15000.00 in the pack) and the approved settlement must freeze it. The line stores net as a column with a CHECK that it equals gross minus deduction (the money rule: computed once, here by `Money`, never by the database).

**4.4 Payment (node 014).** No payables module exists [V]. The spec (4.6, 6) already places an outbound port in payroll with a stub adapter. Chosen: the port `PayrollPayments` (request per approved settlement) and the inbound command `RegisterPayrollDisbursementResult` (registered term) fill a `payroll_disbursements` row per line; the stub returns success or failure on demand, so the 02-10 payment is a result the test feeds. The pay date rule (the 10th) is Q-S4-4; S4 stores the result's `paid_on_date` and decides nothing about the date.

**4.5 Which entity runs the payroll.** The paying entity, by ADR-0086 decision 5: the profile version stores the resolved paying entity (default the relationship's employer on the effective date through `employerOn`, or an override with a reason key). This is not the cost (attribution) entity of S3 seam 3: that entity governs the calendar and standard hours, payroll runs by the paying entity (section 12.2).

## 5. Changes to the spec and the schema (the record governs)

1. Seven new tables in `organization_payroll`: `compensation_profile_versions`, `compensation_allowance_versions`, `insurance_enrolments`, `payroll_confirmed_inputs`, `payroll_settlements`, `payroll_settlement_lines`, `payroll_disbursements` (record, "Tables").
2. Spec 4.2: the profile version holds the base pay only; allowances are separate versions (4.1). The spec's `pending adjustments` field of the profile is not built.
3. Spec 4.6: `CalculateMonthlyPayroll` in S4 takes gross and deduction from the confirmed input (4.2); the posting columns (`posting_status` and its reason) and the payables submission are not in the S4 tables; the slice that first posts adds them to the baseline while it is open.
4. Spec 4.3: an enrolment version holds the kind, the status and the effective date; no contribution base in S4 (the statutory amounts are external).
5. Spec done-when 3 (rule version missing fails) moves to the formula slice (4.2).
   Spec 4.6, state `DRAFT`: no S4 command writes it (the calculation writes `CALCULATED` directly), so the S4 status CHECK lists `CALCULATED`, `IN_REVIEW`, `APPROVED`, `CANCELLED`; the slice that adds a draft step adds the value to the baseline while it is open. `CANCELLED` is reachable from `CALCULATED` only; once `APPROVED` the status never changes.
6. Module edges: `organization.payroll -> organization.leave` (seam 7 read and event) and `organization.payroll -> platform.approval` (already present, new process) are added by the slices that use them; no other new edge.

## 6. Events

Two payloads are signed in the record: `PayrollApproved` (ids and the three totals, no per-person amount) and `PayrollDisbursementResultRegistered` (settlement scope: ids, the settlement's payment status and line counts, no amount; it fires once each time the last open payment attempt of the settlement is answered, see the record). This PR does not write them into the payroll event catalogue file: `EventCatalogueTest` fails when the catalogue names a class absent from the code, so the slice that adds each class adds its catalogue entry from the record in the same PR (the rule of #1525 and S3). Consumers now: `platform.audit`; posting and filing consume them in later slices.

## 7. Inputs from S3 (assumed until the S3 design is signed)

| Input | Assumed contract | Used by |
|---|---|---|
| The locked monthly confirmation per relationship and month (seam 7): the spec's rule P-R2 needs to know whether every in-service person of the paying entity has a `LOCKED` confirmation | S3 defines `MonthlyAttendance.of(employeeId, month)` readable only when `LOCKED` and the event `MonthlyConfirmationLocked`. `MonthlyAttendance.of` fails on an unlocked month, so it cannot list who is unlocked: S4 **adds** to seam 7 a read `MonthlyConfirmationLocks.unlockedAmong(relationshipIds, month)` (the relationships without a locked confirmation) on the leave side | `CalculateMonthlyPayroll` precondition |
| Attendance days and leave minutes of the month | the two fields of `MonthlyAttendance.of` (attendance days, leave minutes by type with `is_paid_leave`) | not used by S4 (the gross is the confirmed input); named so the formula slice finds them |
| The leave balance (seam 8, `LeaveBalances.of`) | as S3 defines it | not used by S4; separation settlement is S5 |

If the S3 design changes seam 7 before it is signed, only the first row changes.

## 8. Slicing (cards for lead to open; S3 slices are named by the S3 design)

| Slice | Content | Nodes | Prerequisite |
|---|---|---|---|
| A | Schema PR: the seven tables with their guards, registry and classification entries, edit of the open baseline (fact 5), dev database rebuilt | all | this PR merged |
| B | `CreateCompensationProfile` and `CompensationProfiles.inForce` (resolves the paying entity through `employerOn`) | 008 | A |
| C | `EnrolInsurance` and its read | 008 | A |
| D | `CreateAllowanceVersion`, `EndAllowanceVersion`, `CompensationProfiles.allowancesOn` | 029 | A |
| E | `RecordConfirmedPayrollInput` and `VoidConfirmedPayrollInput` | 014 | A |
| F | `CalculateMonthlyPayroll` (with the workforce in-service listing and the S3 lock read), `CancelPayrollSettlement` (from `CALCULATED` only), `SubmitPayrollForReview` (new approval process), `ApproveMonthlyPayroll`, event `PayrollApproved` | 014 | B, E, S3 slice G (confirmation lock) |
| G | The payment port with its stub adapter, `RegisterPayrollDisbursementResult`, `InitiatePayrollRedisbursement` (for a `FAILED` line of an approved settlement), event `PayrollDisbursementResultRegistered` | 014 | F |
| H | Web pages for the role walks of section 9.2 (frontend session; the page list waits for the role-task mapping) and the employee's own payslip view | all | B-G |
| I | Acceptance tests for the three nodes (one-test, not design) and the seam tests of 9.3 | all | B-G |

B, C, D, E can run in parallel after A (disjoint tables). F needs the S3 confirmation slice; until it is merged F cannot be started and the row stays marked assumed.

## 9. Done-when (for the test engineer; one checkable line each)

Dates. The month and pay dates are today-relative: the pack's dates are labels (S3 section 9 explains why dates derive from the database clock). M0 = the month containing the database's today; the relationship starts in M0 and its confirmation is locked for M0 by the S3 test helper; the pay date is the 10th of the next month, fed to the stub as the result date. Amounts are asserted as they are (20000.00, 18000.00, 3000.00, 15000.00).

### 9.1 Behaviour (each line is an API-level test)

1. A profile of 20000.00 CNY effective at the relationship's start belongs to that relationship: `inForce` on the start day returns it, on the day before returns nothing; a second relationship of the same person has no profile until one is created for it; the paying entity is the relationship's employer on the effective date unless an override with a reason key is given.
2. A second profile version with a later effective date is read from that date on and the first stays readable before it; a second version with the same effective date is refused; an amount of 0 or negative, a currency other than CNY and a missing decision number are refused.
3. An enrolment of a kind is read as the latest version with an effective date not after the day; `NOT_ENROLLED_HERE` is a status like another (no row is the same as unknown); two versions of one kind with the same effective date are refused.
4. An allowance version has its own period and decision number: two allowance kinds of one relationship with different periods read independently through `allowancesOn`; two versions of one kind with overlapping periods are refused; ending an allowance sets its end date once and a second end is refused; the allowance read and the profile read never add one into the other.
5. A confirmed input of gross 18000.00 and deduction 3000.00 is stored with no net; a second active input for the same relationship and month is refused; a voided one frees the key and cannot be revoided; deduction above gross, a negative amount and a missing decision number are refused.
6. `CalculateMonthlyPayroll` for the paying entity and month M0 returns "pending lock" with the relationships not locked while any relationship of the gate set lacks a locked confirmation, writes nothing, and computes once all are locked. The gate set is the relationships in service in M0 (workforce listing) whose profile in force on the month's last day names this paying entity, plus those whose employer is this entity and that have no profile in force (they then make the calculation fail, line 7).
7. With every relationship locked, the calculation writes one settlement `CALCULATED` for (entity, M0) with one line: gross 18000.00, deduction 3000.00, net 15000.00, the profile version in force on the month's last day as evidence; a second calculation for the same pair while the first is not cancelled is refused; a relationship with no profile in force or no active confirmed input makes the calculation fail with a named error and write nothing.
8. A relationship paid by another entity does not appear in this entity's settlement; a person with two relationships paid by two entities appears once in each, with only that relationship's line. A relationship employed by entity A and paid by entity B (override) with an unlocked confirmation blocks only B's calculation (pending lock) and not A's; once locked, B's settlement holds its line.
9. `SubmitPayrollForReview` starts the approval and moves the settlement to `IN_REVIEW`; a rejected or withdrawn approval returns it to `CALCULATED`; the approval completing approves it once: delivering the same `ApprovalCompleted` again changes nothing and publishes `PayrollApproved` once.
10. An approved settlement's lines and totals cannot be updated or deleted at database level; the approver cannot be the person who calculated it (separation of duties).
11. After approval the stub payment port is asked once per settlement; `RegisterPayrollDisbursementResult` with success sets the line `PAID` with the result's date (the pay date of the test) and, when no attempt is open, the settlement `payment_status` `PAID`; the same result twice changes nothing; a failure result marks the line `FAILED`, leaves the approved amounts unchanged, and with no attempt open the settlement is `PARTLY_FAILED`.
11a. `InitiatePayrollRedisbursement` on a `FAILED` line writes a new attempt that links to the failed one (`redisbursement_of_id`), asks the port once and sets the settlement `REQUESTED`; it is refused for a line that is `PAID`, `REQUESTED` or of a settlement not `APPROVED`; a second one while the first is open is refused.
12. `PayrollApproved` carries ids and entity totals, `PayrollDisbursementResultRegistered` carries ids, the payment status and counts, and neither carries a per-person field: an architecture or contract test over the event records finds no per-person amount field (spec P-R5).
13. `CancelPayrollSettlement` on a `CALCULATED` settlement sets `CANCELLED`, frees the (entity, month) key (a new calculation for the pair is accepted) and a cancelled one cannot be submitted or approved; cancelling an `IN_REVIEW` or `APPROVED` settlement is refused by the command and, for `APPROVED`, by the database.
14. Each new database rule is violated once in a test and refused: an update of a profile, allowance (except its first end), enrolment or confirmed input (except its void) row; the (relationship, effective date) keys; the allowance overlap exclusion; net not equal to gross minus deduction; an update of an approved settlement's amounts, a change of its `status` (to `CANCELLED` or back to `CALCULATED`), and an insert, update or delete of a line while its settlement is not `CALCULATED`.
15. Reads are scoped fail-closed by the paying entity: a caller outside the entity's scope gets 404 for another entity's settlement, line, profile or input; an employee reads only the own payslip line.

### 9.2 Role walks in the web UI (T3: each activity walked by the real role, no admin shortcut)

Roles. Payroll specialist (enters and runs; the role code of main is `payroll_specialist` [V]); settlement approver (role named by the permission catalogue [U], Q-S4-5); employee (own payslip); permission administrator. Finance does not read payroll (ADR-0079 section 2). A separated employee cannot log in; this slice has no activity that needs one.

16. The payroll specialist creates the profile and the enrolments for a new relationship (node 008) and the allowance (node 029) on the payroll pages and reads them back; the employee's account sees none of the pay pages; an HR specialist without the payroll role cannot open them.
17. The payroll specialist records the confirmed input of the month (node 014) citing the decision number, and runs the calculation after the attendance specialist has locked the confirmation (S3 role walk 18); the pending-lock answer shows the unlocked count and is not an error page.
18. The payroll specialist submits the settlement for review; the settlement approver finds it in the to-do list and approves; the calculating specialist cannot approve it.
19. The payroll specialist registers the payment result (stub) and sees the settlement paid; the employee opens the own payslip and sees gross, deduction and net of the month and no other person's.
20. The permission administrator sees the payroll permissions granted automatically by source in the grant review list and can revoke one, after which the page action of that role disappears. [U: the review list is the identity module's existing mechanism]

### 9.3 Seam tests (T4: both sides of each seam; numbers as in section 12.1)

21. Seams 1 and 2: the calculation's test uses the real leave read for the lock state (not a stub); the leave side's contract test covers its answers (locked, not locked, unknown relationship), and the new `unlockedAmong` read is asserted in leave's own test with the S3 helper that locks a month.
22. Seam 2: the workforce listing read `EmploymentRelationships.inServiceIn(month)` (each relationship in service in the month with its employer) is asserted on the workforce side and used unstubbed by the calculation, which derives the paying-entity gate set from it (line 6); `employerOn` is asserted for the paying-entity default.
23. Seam 4: a settlement approval starts a real process and the result returns by `ApprovalCompleted`; the approval-side test of the new process definition checks its route and the approver role.
24. Seam 5: the stub adapter test and the command test share one result contract (status, reference, paid date); a replayed result is a no-op on both sides.
25. Seam 6: `PayrollApproved` is asserted field by field against the record by the provider test; the audit consumer receives it; the posting consumer is not built, listed as waiting for the posting slice.

## 10. Questions only the user can answer (none decided here)

| # | Question | Options | Recommendation |
|---|---|---|---|
| Q-S4-1 | May the first release of payroll run on externally confirmed gross and deductions (the pack's own basis) and add the statutory formulas later through the same input? | confirmed input now, formulas later; wait for the formulas (#1373) | confirmed input now: the formulas are not known and the pack states the amounts are inputs |
| Q-S4-2 | Who supplies the confirmed gross and deductions and on what authority? | the payroll specialist enters them citing the finance decision number; an import file; an approval process in the platform | the payroll specialist enters them citing the decision number (the pack says the amounts are already approved elsewhere) |
| Q-S4-3 | Does the confirmed gross already include approved bonus inputs (#1436)? | included, so the bonus inputs are not added again; added on top | included in S4 (the settlement does not read bonus inputs until the formula slice) |
| Q-S4-4 | The pay date: the pack pays on the 10th of the next month except the final rows; which rule does the business want, and is it per entity? | day 10 per entity as a rule value; a date entered per run | day 10 per entity, a rule-version value in the formula slice; S4 stores the date the payment result reports |
| Q-S4-5 | Which role approves a settlement, and may the approver be the calculating specialist? | a finance-HR approver role; the HR lead | a role the permission catalogue names, never the calculator (the spec's separation of duties, "confirm with finance") |
| Q-S4-6 | Does creating a profile, an allowance or an enrolment need its own approval process, or is the decision number enough? | decision number only; a platform approval each | decision number only (the pack says the decisions are approved elsewhere) |
| Q-S4-7 | Is the confirmed deduction a single total, or components that INV-70 (statutory deductions only) can check? | single total now; components | single total now; components when the formula slice has the kinds |
| Q-S4-8 | Which allowance kinds exist in production and which amounts and periods? The pack gives no amount for ALLOW-A-V1 | the user lists them | the user lists them before the first production entry; not needed for S4 tests |
| Q-S4-9 | Is the withholding plan ("扣缴方案") of node 008 a fact S4 must record, or only the statutory amount that arrives as input? | record a plan kind per relationship; not recorded | not recorded in S4: the deduction input carries the result; add when the filing slice needs it |

## 11. Unverified (the implementing slice checks; none changes a column)

1. Whether `EmploymentRelationships.employerOn` answers for a relationship that starts in the month of the pay (the paying entity of the first profile).
2. The role code of the settlement approver and the permission names; the slice follows the permission catalogue.
3. The shape of the stub adapter's result for a partly failed settlement (one line fails): the contract is per line; the settlement's payment status and the event are derived by the command from the lines.
4. Whether the existing `PayrollApprovers` class serves the settlement's second stage or a new approver mapping is needed.

## 12. Seams this slice defines or reuses

Rule (the user's HR development chart `04-P03-HR_dev-strategy-v0.svg`, read-only): the first slice that crosses a seam fixes its contract; later slices only add fields or commands, never change one. The chart is a sketch and its seam names are drafts (lead, 2026-11-07): the seams below are **derived** from the signed spec, the sample pack and the code on main; a chart name is used only where it matches; every mismatch is in 12.2 for the user to review. "Defined here" = this slice fixes the contract; "reused" = fixed earlier or on main; "crossed here" = both sides are built in this slice (T4 can test both); "assumed" = fixed by the S3 design, not yet signed.

### 12.1 The seams (provider -> consumer)

| # | Seam (chart name where it matches) | Provider -> consumer | Contract | Status in S4 |
|---|---|---|---|---|
| 1 | 考勤结果 | `organization.leave` -> `organization.payroll` | the lock state of a relationship's month: S3's read `MonthlyAttendance.of` (locked only) and event `MonthlyConfirmationLocked`, plus the S4 addition `MonthlyConfirmationLocks.unlockedAmong(relationshipIds, month)` | assumed (S3) + **added here** (the unlocked listing); crossed in slice F |
| 2 | (none in the chart) service listing | `organization.workforce` -> `organization.payroll` | read `inServiceIn(month)`: the relationships in service in the month, each with its employer; payroll intersects it with its own profile reads to get the paying-entity gate set, with `employerOn(id, date)` for the paying entity default | `employerOn` reused [V]; the listing is a **gap added by slice F** (fact 4) |
| 3 | (none in the chart) approval | `organization.payroll` -> `platform.approval` -> `organization.payroll` | `ApprovalProcesses.start` per (process, document); result `ApprovalCompleted`; withdraw; a new process definition for the settlement | reused (platform pattern, template `organization_payroll_bonus_input` #1436 [V]); crossed in slice F |
| 4 | 支付结果 | `organization.payroll` -> payment (stub adapter) -> `organization.payroll` | outbound port `PayrollPayments.request(settlement)` and inbound command `RegisterPayrollDisbursementResult` (per line: status, reference, paid date) | **defined here**; both sides crossed in slice G; the adapter is a stub until a payables module exists (spec 4.6) |
| 5 | (none in the chart) payroll facts | `organization.payroll` -> audit and later posting and filing | events `PayrollApproved`, `PayrollDisbursementResultRegistered` (ids and totals) | **defined here** (payloads); provider side only: posting and filing consumers are later slices |
| 6 | (none in the chart) audit | `organization.payroll` -> `platform.audit` | audit entry per state- or amount-changing command | reused (platform) |
| 7 | (none in the chart) permissions | `platform.identity` -> `organization.payroll` | permission codes per role; no payroll table reads identity | reused; new codes per slice |

Seams of the chart that S4 does not touch: 标准工时 (needed by the formula slice for an hourly rate), 承担成本 (payroll runs by the paying entity, see 12.2), 关系额度 (separation settlement, S5), 奖金输入 (exists, #1436; not read in S4, Q-S4-3), 请假事实, 岗位, 未结事项, 扣缴义务, 个人义务.

### 12.2 Where the derived seams differ from the chart (for the user to review)

| Chart seam | Derived here | Kind of mismatch |
|---|---|---|
| 考勤结果 | seam 1: what payroll needs in S4 is the lock state, not the attendance days; the provider is `organization.leave` (S3 section 12.2 already flags the chart's owner) and the S4 listing is added to the S3 contract | chart seam narrower than the need: a read of "who is not locked" is missing |
| 承担成本 | not used: payroll runs and withholds by the paying entity (ADR-0086 decision 5), which equals the employer unless an override is recorded; the chart's cost entity governs the calendar and standard hours | chart seam could be confused with the paying entity: different concept, no seam here |
| 支付结果 | seam 4: the chart implies a provider on the payment side; no payables module exists, so the port and the result command live in payroll with a stub adapter | provider missing on main: the seam is defined from the payroll side |
| 奖金输入 | not read in S4 (the gross is the confirmed input); exists from #1436 | chart seam present, not crossed by this slice; Q-S4-3 decides whether it is ever added on top |
| (missing) workforce service listing, approval, audit, permissions | seams 2, 3, 6, 7 | chart seam missing: platform or workforce reads, perhaps meant to be the 地基 row |
| 标准工时, 关系额度, 请假事实, 岗位, 未结事项, 扣缴义务, 个人义务 | not touched by S4 | none for this slice |

S3's seams 1, 2, 3 (standard hours, calendar, cost entity) are not reused by S4. S3 seam 7 is reused as fixed there and only added to (seam 1 above); S3 seam 8 (`LeaveBalances.of`) is not consumed by S4.
