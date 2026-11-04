# Seam between timesheets and overtime / comp time — RULE-412..RULE-413

Scope: Phase 2 `cost.timesheet` and the leave and overtime context (`organization.leave`, B-27 in the people design). Basis: user decision FD2-05, 2026-10-16 (`docs/decisions/decision-pages/answers/fd2.json`, submitted on the filing-duty page; 「不关联：非工作日工时只作为工时记录，加班审批与调休交假勤，之后再对接」) and RULE-401, RULE-398. The comp-time (补休) design is the people group's proposal in `docs/architecture/proposals/closeout/people/notes.md` (removed 2026-11-04; read it at commit 1f6f97d1b) (seam question 8) and is not yet adopted; nothing here extends a user answer.

| ID | Domain | Title | Priority | Review |
|---|---|---|---|---|
| RULE-412 | timesheet | The timesheet context never reads an overtime approval or a comp-time balance; the leave context owns both | P0 | No |
| RULE-413 | timesheet | A comp-time (补休) leave reduces the minimum hours of that day like any approved or pending leave, once comp time is modelled as a leave type | P1 | No |

### RULE-412: The timesheet context never reads an overtime approval or a comp-time balance; the leave context owns both
**Category:** Policy
**Priority:** P0
**Source:** user decision FD2-05, 2026-10-16 (intent `2026-10-16-054259-one-rules-工时填报义务决策页-FD2-6.md`); RULE-401
**Plain English:** Hours filed on a non-workday are plain time records (RULE-401). Whether the person worked approved overtime, and how it is compensated (pay or comp time), is decided and recorded in the leave and overtime context. The timesheet context neither requires an overtime approval to file, nor creates or consumes a comp-time balance. The two contexts meet only at the one point of RULE-413.
**Who connects (not decided by the user, proposed):** the leave and overtime context owns approval of overtime and the comp-time balance; the timesheet context owns nothing about them. If a later connection is wanted (for example, checking filed hours against approved overtime for payroll), it is a new decision and a new rule, because the user said 「之后再对接」.
**Specification:**
- Given a Saturday timesheet with no overtime approval, When it is submitted, Then it is accepted (RULE-401).
- Given an approved overtime on a Saturday and no timesheet, Then nothing is missed (RULE-397).
- Given a comp-time balance changes, Then no timesheet changes.
**Tests:** to be written with the Phase 2 timesheet card.
**Confidence:** High.

### RULE-413: A comp-time (补休) leave reduces the minimum hours of that day like any approved or pending leave, once comp time is modelled as a leave type
**Category:** Calculation
**Priority:** P1
**Source:** RULE-398 (user decision FD2-02, 2026-10-16); user decision N1-02, 2026-10-17; people design (seam question 8: comp time as leave type 「补休」, taking from a comp-time balance line)
**Plain English:** If comp time is booked as a leave type, the day taken as comp time is a day not worked, so RULE-398 applies unchanged: minimum hours = standard day minus approved and pending leave hours, now including comp-time hours. The timesheet context reads only the leave hours of the day, never the balance or the overtime that produced it.
**Specification:**
- Given a 8-hour day with an approved 8-hour comp-time leave, Then the minimum is 0 and the day may be filed voluntarily (RULE-400).
- Given a 4-hour comp-time leave pending, Then the minimum is the standard day minus 4 and the timesheet is marked 「含待批请假」 (RULE-404).
**Decided:** user decision N1-02 (intent `2026-10-17-085044-one-rules-N1-页提交-N1-202610.md`, preselected recommendation unchanged): comp time counts as leave for the minimum hours. It still depends on the people group modelling comp time as a leave type; if their design differs, this rule is revisited with them.
**Tests:** to be written with the Phase 2 timesheet card.
**Confidence:** Medium.
