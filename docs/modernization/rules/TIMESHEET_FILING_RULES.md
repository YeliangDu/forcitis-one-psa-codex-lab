# Timesheet filing duty business rules — RULE-397..RULE-403

Scope: Phase 2 `cost.timesheet` (submission checks, reminders), with leave hours read from the absence system (`organization.attendance` / future leave data). Rule 1 「每人每天最多一张」 is decided and implemented by one-1a-par, not repeated here. These are rules of FORCITIS one, decided by the user on 2026-10-16 on the filing-duty decision page (`rules/decide/sheets/FD2.json`, answers `rules/decide/answers/FD2.json`, intent `2026-10-16-054259-one-rules-工时填报义务决策页-FD2-6.md`), plus two additions the user gave afterwards, relayed by one-coordinator (RULE-400 and RULE-403; their intents are recorded by one-coordinator). Industry practice (SAP CATS, Oracle, Workday, Deltek) was the basis; production totals of the old system (hours dated from 2026-05-01, `phase1a/infra/handoff/B-5-dba/evidence/timesheet_filing_duty.sql`) were only a cross-check. The old system's minimum-hours check is not implemented until Phase 2.

| ID | Domain | Title | Priority | Review |
|---|---|---|---|---|
| RULE-397 | timesheet | A workday must be filed; a non-workday may be filed but never has to be | P0 | No |
| RULE-398 | timesheet | The minimum hours of a workday are the standard day minus the approved leave hours of that day | P0 | No |
| RULE-399 | timesheet | Leave is never a line of the timesheet; it only reduces the hours that must be filed | P0 | No |
| RULE-400 | timesheet | A full-leave day may be filed voluntarily, with no minimum | P1 | No |
| RULE-401 | timesheet | Hours on a non-workday are plain time records, not tied to an overtime approval or comp time | P1 | No |
| RULE-402 | timesheet | A missed workday is blocked at submission when short, and reminded afterwards when not filed | P1 | No |
| RULE-403 | timesheet | Hours filed plus approved leave hours of one day are at most 15 | P0 | No |

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

### RULE-398: The minimum hours of a workday are the standard day minus the approved leave hours of that day
**Category:** Calculation
**Priority:** P0
**Source:** user decision FD2-02, 2026-10-16; FD-10 (standard day 7.5 hours, configurable per legal entity)
**Plain English:** On a workday that has to be filed, the minimum is `max(0, standard day − approved leave hours)`. Submitting fewer hours than that fails. The standard day is the per-entity parameter (7.5 by default).
**Specification:**
- Given standard day 7.5 and 3 approved leave hours, When 4.5 hours are submitted, Then the submission passes; with 4.0 it fails.
**Tests:** to be written with the Phase 2 timesheet card.
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
**Source:** user words relayed by one-coordinator on 2026-10-16 (「每天含请假最高不超过 15 小时」); the old system's daily 15-hour check (`ManHourServiceImpl`, per process/1a/TIMESHEET审批.md step 2)
**Plain English:** For every day (workday, non-workday and leave day alike), submitted hours plus approved leave hours must not exceed 15; otherwise the submission is refused with an alert. **Open point for the user:** one-coordinator reads leave as approved leave; the old system counted any leave record regardless of approval status, and whether its 15-hour check included leave could not be confirmed from the summary. If leave still in approval should count, the rule must say so.
**Specification:**
- Given 4 approved leave hours, When 11.5 hours are submitted, Then it fails; with 11 it passes.
**Tests:** to be written with the Phase 2 timesheet card.
**Confidence:** Medium — approved vs requested leave is open.
