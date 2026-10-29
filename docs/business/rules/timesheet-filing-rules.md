# Timesheet filing duty business rules — RULE-397..RULE-405

Scope: Phase 2 `cost.timesheet` (submission checks, reminders), with leave hours read from the absence system (`organization.attendance` / future leave data). Rule 1 「每人每天最多一张」 is decided and implemented by one-1a-par, not repeated here. These are rules of FORCITIS one, decided by the user on 2026-10-16 on the filing-duty decision page (`docs/decisions/decision-pages/sheets/fd2.json`, answers `docs/decisions/decision-pages/answers/fd2.json`, intent `2026-10-16-054259-one-rules-工时填报义务决策页-FD2-6.md`), plus additions the user gave afterwards, relayed by one-coordinator (RULE-400, RULE-403, and the pending-leave handling in RULE-398, RULE-404, RULE-405; their intents are recorded by one-coordinator). Industry practice (SAP CATS, Oracle, Workday, Deltek) was the basis; production totals of the old system (hours dated from 2026-05-01, `docs/data/evidence/dba-handoff/timesheet-filing-duty.sql`) were only a cross-check. The old system's minimum-hours check is not implemented until Phase 2.

| ID | Domain | Title | Priority | Review |
|---|---|---|---|---|
| RULE-397 | timesheet | A workday must be filed; a non-workday may be filed but never has to be | P0 | No |
| RULE-398 | timesheet | The minimum hours of a workday are the standard day minus the approved and pending leave hours of that day | P0 | No |
| RULE-399 | timesheet | Leave is never a line of the timesheet; it only reduces the hours that must be filed | P0 | No |
| RULE-400 | timesheet | A full-leave day may be filed voluntarily, with no minimum | P1 | No |
| RULE-401 | timesheet | Hours on a non-workday are plain time records, not tied to an overtime approval or comp time | P1 | No |
| RULE-402 | timesheet | A missed workday is blocked at submission when short, and reminded afterwards when not filed | P1 | No |
| RULE-403 | timesheet | Hours filed plus approved leave hours of one day are at most 15 | P0 | No |
| RULE-404 | timesheet | A timesheet filed with pending leave is marked 「含待批请假」 and the approver sees it | P1 | No |
| RULE-405 | timesheet | When pending leave is rejected the day is checked again; a shortfall is reminded to the employee and told to the approver; an approval needs no action | P1 | No |

### RULE-397: A workday must be filed; a non-workday may be filed but never has to be
**Category:** Policy
**Priority:** P0
**Source:** user decision FD2-01, 2026-10-16
**Plain English:** Which days must be filed follows the work calendar of the person's legal entity (`organization.attendance`). A day that is not a workday never counts as missed. The old system also accepted non-workday timesheets (5.0 % of person-days with hours).
**Specification:**
- Given a Saturday that is not a make-up workday, When nobody files, Then nothing is missed.
- Given a make-up workday on a Saturday, When nobody files, Then it is a day that had to be filed.
**Tests:** to be written with the Phase 2 timesheet card.
**Confidence:** High.

### RULE-398: The minimum hours of a workday are the standard day minus the approved and pending leave hours of that day
**Category:** Calculation
**Priority:** P0
**Source:** user decision FD2-02, 2026-10-16 (approved leave); user answer 「采用」 relayed by one-coordinator on 2026-10-16 (pending leave counts too); FD-10 (standard day 7.5 hours, configurable per legal entity)
**Plain English:** On a workday that has to be filed, the minimum is `max(0, standard day − (approved + pending leave hours))`, and a submission is allowed when it meets it. Submitting fewer hours than that fails. The standard day is the per-entity parameter (7.5 by default).
**Specification:**
- Given standard day 7.5, 2 approved and 1 pending leave hours, When 4.5 hours are submitted, Then the submission passes; with 4.0 it fails.
**Tests:** to be written with the Phase 2 timesheet card.
**Dependency:** leave hours and their status (approved, pending, rejected) come from the absence system; no such data source exists yet in the new system, so the rule cannot be implemented before the leave read API is fixed with it.
**Confidence:** High — leave data source is fixed with the absence system (the old leave table has almost no data before 2026-09 and counts nursing leave and the 「晚到规则」 as leave; not carried over by this rule).

### RULE-399: Leave is never a line of the timesheet; it only reduces the hours that must be filed
**Category:** Policy
**Priority:** P0
**Source:** user decision FD2-03, 2026-10-16; RULE-391 (three line kinds)
**Plain English:** There is no leave line kind and no leave line generated from the absence system. Leave lives in the absence system only; the timesheet reads approved leave hours to compute RULE-398 and RULE-403.
**Specification:**
- Given a timesheet line whose kind is leave, When it is submitted, Then it fails (RULE-391).
**Tests:** to be written with the Phase 2 timesheet card.
**Confidence:** High.

### RULE-400: A full-leave day may be filed voluntarily, with no minimum
**Category:** Policy
**Priority:** P1
**Source:** user words relayed by one-coordinator on 2026-10-16 (「请假日也自愿填，补上」); replaces the choice on the decision page for FD2-04, which was 「整天请假的日子不能提交」
**Plain English:** On a day of full approved leave a timesheet may be submitted but never has to be, and no minimum applies (the minimum is 0 by RULE-398). This differs from the old system, which refused submission on full-leave days (450 minutes or more); 9 such days had a timesheet there. RULE-403 still applies.
**Specification:**
- Given a full-leave day, When 2 hours are submitted, Then the submission passes; When nothing is submitted, Then the day is not missed.
**Tests:** to be written with the Phase 2 timesheet card.
**Confidence:** High.

### RULE-401: Hours on a non-workday are plain time records, not tied to an overtime approval or comp time
**Category:** Policy
**Priority:** P1
**Source:** user decision FD2-05, 2026-10-16
**Plain English:** A submission on a non-workday needs no overtime approval and earns no comp time through the timesheet. Overtime approval and comp time belong to the absence system and are connected later. The Labor Law does not make filing hours a duty; it governs arrangement and pay (statement relayed, official text not retrieved).
**Specification:**
- Given 6 hours submitted on a Sunday, When no overtime approval exists, Then the submission passes.
**Tests:** to be written with the Phase 2 timesheet card.
**Confidence:** High.

### RULE-402: A missed workday is blocked at submission when short, and reminded afterwards when not filed
**Category:** Policy
**Priority:** P1
**Source:** user decision FD2-06, 2026-10-16
**Plain English:** Two controls work together. At submission, fewer hours than RULE-398 fails. After the day, for a workday that had to be filed and has no timesheet, the person is reminded, then the supervisor if it stays open. **Open:** who counts as someone who has to file (employment dates, posts exempt from filing; the old system has no such flag) and the reminder timing; to be fixed before the reminder is built.
**Specification:**
- Given a workday that had to be filed with no timesheet, When the reminder job runs, Then the person is reminded; the supervisor is told when it stays open.
**Tests:** to be written with the Phase 2 reminder card.
**Confidence:** Medium — who must file and the timing are open.

### RULE-403: Hours filed plus approved leave hours of one day are at most 15
**Category:** Validation
**Priority:** P0
**Source:** user words relayed by one-coordinator on 2026-10-16 (「每天含请假最高不超过 15 小时」); the old system's daily 15-hour check (`ManHourServiceImpl`, per docs/business/processes/timesheet-approval.md step 2)
**Plain English:** For every day (workday, non-workday and leave day alike), submitted hours plus approved leave hours must not exceed 15; otherwise the submission is refused with an alert. Only approved leave counts here (user answer 「采用」 relayed by one-coordinator on 2026-10-16: the 15-hour cap still counts approved leave only, while the minimum of RULE-398 also counts pending leave). The old system counted any leave record regardless of approval status.
**Specification:**
- Given 4 approved leave hours, When 11.5 hours are submitted, Then it fails; with 11 it passes.
**Tests:** to be written with the Phase 2 timesheet card.
**Dependency:** approved leave hours from the absence system (see RULE-398).
**Confidence:** High.

### RULE-404: A timesheet filed with pending leave is marked 「含待批请假」 and the approver sees it
**Category:** Policy
**Priority:** P1
**Source:** user answer 「采用」 relayed by one-coordinator, 2026-10-16
**Plain English:** When the submission passes the minimum of RULE-398 only because of leave that is still pending, it is submitted and carries the mark 「含待批请假」 (a term still to register with one-glossary). Every approver of the timesheet sees the mark with the pending leave hours of that day, no leave reason or type.
**Specification:**
- Given standard day 7.5 with 3 pending leave hours, When 4.5 hours are submitted, Then the timesheet is accepted, is marked 「含待批请假」 and its approvers see the mark.
- Given the same day with all leave approved, Then no mark is set.
**Tests:** to be written with the Phase 2 timesheet card.
**Dependency:** leave status from the absence system (see RULE-398).
**Confidence:** High.

### RULE-405: When pending leave is rejected the day is checked again; a shortfall is reminded to the employee and told to the approver; an approval needs no action
**Category:** Policy
**Priority:** P1
**Source:** user answer 「采用」 relayed by one-coordinator, 2026-10-16
**Plain English:** When leave that counted toward a submitted day's minimum is rejected, the system checks that day again by RULE-398. If hours are now short, the employee is reminded to file more hours or to request leave again, and the approver of the timesheet is told. When pending leave is approved, nothing happens (it already counted). The timesheet is not reopened or withdrawn by the system.
**Specification:**
- Given a day passed with 3 pending leave hours and 4.5 hours filed, When the leave is rejected, Then the day is short by 3 hours, the employee is reminded and the approver is told.
- Given the same day, When the leave is approved, Then nothing happens.
**Tests:** to be written with the Phase 2 timesheet card.
**Dependency:** an event or poll for leave status changes from the absence system; none exists yet.
**Confidence:** Medium — how the leave status change reaches the timesheet is fixed with the absence system.

## Data and implementation preconditions (one-data pre-review, 2026-10-16)

These are not user decisions; they are what had to be settled before RULE-398..405 can be implemented. Items 1, 2, 3 and 7 were settled by one-architecture on 2026-10-16.

1. **Leave object (settled by one-architecture, 2026-10-16).** Leave is managed by this system, not read from an external attendance source (user Q36/R2, D-5): a separate BC for leave and overtime (B-27) with an anti-corruption layer to attendance sources; object OBJ-68, attached to the person. States: draft → in approval → approved / rejected → leave ended / cancelled. Recorded by start and end moment, may span days, half days or hours, duration stored as whole minutes. For this card only **approved** leave is deducted from the 15-hour cap (RULE-403); for the minimum of RULE-398 approved and pending leave both count (user, RULE-398); ended and cancelled leave counts nowhere. A leave day still takes the day's one timesheet slot. The smallest request unit and which leave types may be taken by the hour live in the leave-type configuration of B-27.
2. **Standard day (settled by one-architecture, 2026-10-16).** A new configuration-type 「标准工时版本」 under the work calendar (OBJ-28), owned by `organization.attendance` (AGG-108): optional legal entity like the calendar year; daily standard minutes per day kind (workday, make-up workday), overridable per date (for half-day swaps); effective date, confirmer, decision reference; append-only, no retroactive change; a lookup that finds nothing fails with an alert and never falls back to a default. **Value (decided by FD-10, 2026-10-15):** 7.5 hours (450 minutes), per-entity configurable; the five legal entities share the value in the first version. The old system's rule (hours plus leave of a workday at least 7.5) was the evidence.
3. **Calculation convention (fixed; defined by the standard-day version, AGG-108, one-architecture 2026-10-16).** Unit minutes (an implementation detail). Expected hours = the day's standard minutes, so a non-workday expects 0; leave spanning days is split onto each day by that day's standard minutes. Minimum = expected − (approved + pending) leave of that day, floored at 0. Pending leave is a calculation input, never stored.
4. **Derived values are not stored.** The minimum hours of a day is derived, not a column of the timesheet. If the basis of a submission decision must be kept, either record it as a rebuildable projection with a reconciliation, or write it into the audit detail; one of the two is chosen in the implementation card (suggested: audit detail).
5. **Versioning.** RULE-397..405 are fixed rules. The values they read (standard day, calendar) are versioned configuration; the timesheet records the version ids of the standard-day and calendar versions it was checked against, per the configuration rules (METHODS M3「规则调整三类」). Not retroactive: a later version never re-judges earlier submissions.
6. **Missing data fails loudly.** An unmaintained calendar year, an unknown standard day or unreadable leave data makes the check fail with an alert; nothing is skipped or defaulted. A reminder job raises an alert to a named recipient and de-duplicates (recipient and timing are open, see RULE-402).
7. **Module dependency.** `cost.timesheet` gains two dependencies through the cross-module read path: `organization.attendance` (calendar, standard day) and B-27 (leave hours and status). Checked for cycles by one-architecture: none.
8. **Read models.** 「谁没填」 and filing timeliness are read models built from events plus the calendar, not totals kept on the timesheet table.
9. **One timesheet per person per day** (rule 1, one-1a-par) still holds on a full-leave day: filing voluntarily takes the day's one slot (RULE-400).
