# Approval deadline business rules — RULE-377..RULE-384

Scope: Phase 1a `platform.approval`, the generic node deadline (T+1 in the first use: timesheets). These are rules of FORCITIS one itself, decided by the user on 2026-10-14; they have no counterpart in the old system, which only had a manual reminder. The numbers (how many workdays, how long until the escalation) are **placeholders** until the old-system practice is checked: they live in the decision table `cost_timesheet_approval_nodes` (`backend/src/main/resources/dmn/`), not in code, and a new version of the table applies to the approvals started after it is deployed.

User decisions (REIMAGINED_ARCHITECTURE.md §2.1, last row): "工时要求 T+1 审批完毕……" — T counts from the submission day, +1 counts in workdays (needs the work calendar); remind when late, escalate to the superior, count toward timeliness statistics, never approve automatically.

| ID | Domain | Title | Priority | Review |
|---|---|---|---|---|
| RULE-377 | approval | A task's deadline is the end of the n-th workday after the submission day, in the legal entity's calendar | P0 | No |
| RULE-378 | approval | The number of workdays and the escalation interval of a node come from the node's decision table | P0 | No |
| RULE-379 | approval | A task that is still unhandled at its deadline is recorded as overdue once and its holders are reminded | P0 | No |
| RULE-380 | approval | Still unhandled after the escalation point, the task goes to the holders' superiors | P0 | No |
| RULE-381 | approval | With no superior known, an overdue task goes to the process administrator and raises an alert | P0 | No |
| RULE-382 | approval | A deadline never approves, rejects or skips anything | P0 | No |
| RULE-383 | approval | A calendar that cannot answer fails the submission | P0 | No |
| RULE-384 | approval | Overdue and escalation are events with the task's own event number, counted for timeliness statistics | P1 | No |

### RULE-377: A task's deadline is the end of the n-th workday after the submission day, in the legal entity's calendar
**Category:** Calculation
**Priority:** P0
**Source:** user decision 2026-10-14 (REIMAGINED_ARCHITECTURE.md §2.1); `organization.attendance.api.WorkCalendar.endOfWorkdayAfter`
**Plain English:** The submission day is the business date (Asia/Shanghai) of the round start; it never counts. The deadline of every task of that round is the end (24:00 business zone) of the n-th workday after it, counted with the work calendar of the document's legal entity: weekends and public holidays do not count, make-up workdays do. Tasks created later in the same round (a later node) keep the deadline of the submission day.
**Specification:**
  Given a timesheet submitted on Friday 2026-10-16 and n = 1
  When its first task is created
  Then the deadline is the end of Monday 2026-10-19 (Tuesday 00:00)
  Given Monday 2026-10-19 is a public holiday
  Then the deadline is the end of Tuesday 2026-10-20
  Given Saturday 2026-10-17 is a make-up workday
  Then the deadline is the end of Saturday 2026-10-17
**Parameters:** n from RULE-378; the calendar is maintained by an administrator (default calendar for every legal entity, a legal-entity calendar is reserved).
**Edge cases handled:** Submission on a holiday or weekend counts from that date as well: the first workday after it is day 1.
**Tests:** `ApprovalDeadlineIT` (weekend, holiday, make-up workday).
**Confidence:** High.

### RULE-378: The number of workdays and the escalation interval of a node come from the node's decision table
**Category:** Policy
**Priority:** P0
**Source:** user decision 2026-10-14 ("B 决策表管业务判断")
**Plain English:** The decision table `{process key}_nodes` answers, per node and per routing facts, the deadline in workdays and the interval (also in workdays, counted from the deadline's day) until the escalation. A node whose answer has no deadline has none. The shipped values (1 and 1) are placeholders for TIMESHEET until the old-system practice is checked. The table version deployed when the approval starts applies to that approval.
**Specification:**
  Given the shipped table gives deadline 1 and escalation 1 for the node
  When a task of the node is created on Friday
  Then due = end of the next workday and escalate-at = end of the workday after that
  Given a newer table version gives 3 and 2
  Then approvals started afterwards use 3 and 2, approvals already running keep 1 and 1
**Parameters:** `deadlineWorkdays`, `escalationWorkdays` outputs of the table.
**Edge cases handled:** A node the table has no rule for fails the start (G-1); a deadline without an interval, or the reverse, is refused.
**Tests:** `NodeDecisionIT`, `ApprovalDeadlineIT.numbersComeFromTheDecisionTable`.
**Confidence:** High.

### RULE-379: A task that is still unhandled at its deadline is recorded as overdue once and its holders are reminded
**Category:** Lifecycle
**Priority:** P0
**Source:** user decision 2026-10-14
**Plain English:** A scheduled job (ShedLock, every 5 minutes) finds open tasks past their deadline. For each, in its own transaction, the task records "overdue" once (event `ApprovalTaskOverdue`) and the holders (the assignee, or all candidates before a claim) get a WeCom text card. Repeating the run changes nothing.
**Specification:**
  Given an open task whose deadline passed
  When the job runs twice
  Then one ApprovalTaskOverdue exists and one card was sent per holder
**Parameters:** job `platform.approval.deadline.enforce`, cron `forcitis.platform.approval.deadline.cron`.
**Edge cases handled:** A task whose engine task is gone is closed without a reminder; a task that fails is counted (`platform.approval.deadline.failed`) and logged while the others still run.
**Tests:** `ApprovalDeadlineIT.reminderOnceAndNeverAnApproval`, `ApprovalTaskStateMachineTest`.
**Confidence:** High.

### RULE-380: Still unhandled after the escalation point, the task goes to the holders' superiors
**Category:** Lifecycle
**Priority:** P0
**Source:** user decision 2026-10-14
**Plain English:** Once the escalation point has passed and the overdue was recorded, the superiors of the holders (employed direct superiors, not the holders themselves and not the initiator) become candidates of the task as well and get a card; event `ApprovalTaskEscalated`. A claimed task is released to the pool so a superior can take it. It happens once per task.
**Specification:**
  Given an overdue task claimed by P whose superior is H, and the escalation point passed
  When the job runs
  Then the task is unclaimed, its candidates are P and H, H gets a card, and H may decide it
**Edge cases handled:** A late job run records the reminder and the escalation in order within one transaction.
**Tests:** `ApprovalDeadlineIT.escalationToTheSuperior`, `claimedTaskEscalates`, `lateRunCatchesUp`.
**Confidence:** High.

### RULE-381: With no superior known, an overdue task goes to the process administrator and raises an alert
**Category:** Policy
**Priority:** P0
**Source:** user decision 2026-10-14; same fallback as RULE-250/254
**Plain English:** When none of the holders has a usable superior, the process administrator becomes a candidate, the event says `toProcessAdministrator`, the counter `platform.approval.task.escalation.administrator` rises and an error is logged. With no administrator configured either, the escalation fails loudly (counter `platform.approval.task.escalation.unresolved`) and is tried again on the next run; nothing is dropped.
**Tests:** `ApprovalDeadlineIT.noSuperiorGoesToTheAdministrator`.
**Confidence:** High.

### RULE-382: A deadline never approves, rejects or skips anything
**Category:** Policy
**Priority:** P0
**Source:** user decision 2026-10-14 ("不自动通过")
**Plain English:** Overdue and escalation only remind and widen who may act. The approval request stays RUNNING and the task open until a person decides, the initiator withdraws or cancels, or a rejection ends the round.
**Tests:** `ApprovalDeadlineIT.reminderOnceAndNeverAnApproval`, `decidedInTimeIsNotReminded`.
**Confidence:** High.

### RULE-383: A calendar that cannot answer fails the submission
**Category:** Validation
**Priority:** P0
**Source:** G-1 (missing or invalid input fails); `WorkCalendar` contract
**Plain English:** When the calendar has no maintained data for a year the answer depends on, the task cannot get a deadline; the whole start rolls back (no request, no instance, no task) and the error is raised. "Every day is a workday" is never assumed.
**Tests:** `ApprovalDeadlineIT.calendarFailureFailsTheStart`.
**Confidence:** High.

### RULE-384: Overdue and escalation are events with the task's own event number, counted for timeliness statistics
**Category:** Policy
**Priority:** P1
**Source:** proposals/EVENT_ANALYTICS_FIELDS.md §6 (item 3: timer-triggered events take the owning aggregate's sequence)
**Plain English:** `ApprovalTaskOverdue` and `ApprovalTaskEscalated` are published through the event publication registry like every domain event; their envelope's aggregateVersion is the task's event number (assigned = 1, then claimed, overdue, escalated … in order). They are process facts, not posting facts.
**Tests:** `ApprovalDeadlineIT`, `ApprovalTaskStateMachineTest.eventsCarryTheTaskSequence`; catalogue entries in `docs/modernization/events/approval.yaml`.
**Confidence:** High.
