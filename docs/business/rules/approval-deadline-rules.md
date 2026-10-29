# Approval deadline business rules — RULE-377..RULE-384, RULE-386

Scope: Phase 1a `platform.approval`, the generic node deadline (T+1 in the first use: timesheets). These are rules of FORCITIS one itself, decided by the user on 2026-10-14; they have no counterpart in the old system, which only had a manual reminder. The numbers are fixed by the user decisions D-1a-110..117 and live as attributes of the node in the process file (`deadlineWorkdays`, `escalationWorkdays`, always together; D-1a-100: no decision table in 1a), not in code; a new version of the process file applies to the approvals started after it is deployed. D-1a-111/112/113 supersede the wording of RULE-377..381 where they differ: T is the workday the task is created in, the deadline is 23:59, the reminder is 09:00 of the next workday, the escalation 09:00 of the m-th workday after the reminder, a claimed task goes to the claimant's superior and an unclaimed one to the process administrator.

User decisions (REIMAGINED_ARCHITECTURE.md §2.1, last row): "工时要求 T+1 审批完毕……" — T is the workday the task is created in (D-1a-111), +1 counts in workdays (needs the work calendar); remind when late, escalate to the superior, count toward timeliness statistics, never approve automatically.

| ID | Domain | Title | Priority | Review |
|---|---|---|---|---|
| RULE-377 | approval | A task's deadline is 23:59 of the n-th workday after the workday the task is created in, in the legal entity's calendar | P0 | No |
| RULE-378 | approval | The number of workdays and the escalation interval of a node are attributes of the node in the process file | P0 | No |
| RULE-379 | approval | A task that is still unhandled at its deadline is recorded as overdue once and its holders are reminded | P0 | No |
| RULE-380 | approval | Still unhandled after the escalation point, the task goes to the holders' superiors | P0 | No |
| RULE-381 | approval | With no superior known, an overdue task goes to the process administrator and raises an alert | P0 | No |
| RULE-382 | approval | A deadline never approves, rejects or skips anything | P0 | No |
| RULE-383 | approval | A calendar that cannot answer fails the start of the approval, never the submission | P0 | No |
| RULE-384 | approval | Overdue and escalation are events with the task's own event number, counted for timeliness statistics | P1 | No |
| RULE-386 | approval | Every approval moment reads the one injected Clock, including the process engine | P0 | No |

### RULE-377: A task's deadline is 23:59 of the n-th workday after the workday the task is created in, in the legal entity's calendar
**Category:** Calculation
**Priority:** P0
**Source:** user decisions D-1a-110/111 (REIMAGINED_ARCHITECTURE.md §2.1); `organization.attendance.api.WorkCalendar`
**Plain English:** T is the workday the task is created in (when that day is not a workday, the next workday); it is neither the work date nor the day the document was submitted. The deadline of the task is 23:59 (business zone Asia/Shanghai) of the n-th workday after T, counted with the work calendar of the document's legal entity: weekends and public holidays do not count, make-up workdays do. Every task has its own T: a task of a later node, created days later, gets a later deadline.
**Specification:**
  Given a task created on Friday 2026-10-16 and n = 1
  Then T is Friday and the deadline is 23:59 on Monday 2026-10-19
  Given Monday 2026-10-19 is a public holiday
  Then the deadline is 23:59 on Tuesday 2026-10-20
  Given a task created on Saturday 2026-10-17 that is a rest day
  Then T is the next workday, Monday 2026-10-19, and the deadline 23:59 on Tuesday 2026-10-20
  Given the document's work date is two weeks before the task is created
  Then the work date plays no part in the deadline
**Parameters:** n from RULE-378; the calendar is maintained by an administrator (default calendar for every legal entity, a legal-entity calendar is reserved).
**Edge cases handled:** A task created on a rest day starts counting from the next workday.
**Tests:** `ApprovalDeadlineIT` (weekend, holiday, make-up workday, creation day instead of work date).
**Confidence:** High.

### RULE-378: The number of workdays and the escalation interval of a node are attributes of the node in the process file
**Category:** Policy
**Priority:** P0
**Source:** user decision D-1a-100 (no decision table in 1a); the node attributes `deadlineWorkdays` and `escalationWorkdays`
**Plain English:** The deadline in workdays (n) and the interval in workdays from the reminder day to the escalation (m) are two attributes of the node's rule element in the process file, always given together and both at least 1. A node without them has no deadline. The process file version deployed when the approval starts applies to that approval.
**Specification:**
  Given a node carries deadlineWorkdays = 1 and escalationWorkdays = 1
  When a task of the node is created on Friday
  Then due = 23:59 of the next workday, remind = 09:00 of the workday after that, escalate = 09:00 one workday after the reminder day
  Given a newer process file version sets 3 and 2
  Then approvals started afterwards use 3 and 2, approvals already running keep 1 and 1
**Parameters:** `deadlineWorkdays`, `escalationWorkdays`.
**Edge cases handled:** One of the two alone, or a value below 1, makes the node configuration invalid and the start fails with an error, nothing is guessed.
**Tests:** `ApprovalDeadlineIT`, `NodeRoutingIT`.
**Confidence:** High.

### RULE-379: A task that is still unhandled at its deadline is recorded as overdue once and its holders are reminded
**Category:** Lifecycle
**Priority:** P0
**Source:** user decision 2026-10-14
**Plain English:** A scheduled job (ShedLock, every 5 minutes) finds open tasks past their deadline. For each, in its own transaction, the task records "overdue" once (event `ApprovalTaskBecameOverdue`) and the holders (the assignee, or all candidates before a claim) get a WeCom text card. Repeating the run changes nothing.
**Specification:**
  Given an open task whose deadline passed
  When the job runs twice
  Then one ApprovalTaskBecameOverdue exists and one card was sent per holder
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

### RULE-383: A calendar that cannot answer fails the start of the approval, never the submission
**Category:** Validation
**Priority:** P0
**Source:** G-1 (missing or invalid input fails); `WorkCalendar` contract
**Plain English:** When the calendar has no maintained data for a year the answer depends on, the task cannot get a deadline; the start rolls back (no request, no instance, no task) and the error is raised. The document's own submission has succeeded by then (the approval starts from the event it published): the start fails, is alerted and is retried once the calendar is maintained; the document stays submitted. "Every day is a workday" is never assumed.
**Tests:** `ApprovalDeadlineIT.calendarFailureFailsTheStart`.
**Confidence:** High.

### RULE-384: Overdue and escalation are events with the task's own event number, counted for timeliness statistics
**Category:** Policy
**Priority:** P1
**Source:** docs/architecture/events/event-analytics-fields.md §6 (item 3: timer-triggered events take the owning aggregate's sequence)
**Plain English:** `ApprovalTaskBecameOverdue` and `ApprovalTaskEscalated` are published through the event publication registry like every domain event; their envelope's aggregateVersion is the task's event number (assigned = 1, then claimed, overdue, escalated … in order). They are process facts, not posting facts.
**Tests:** `ApprovalDeadlineIT`, `ApprovalTaskStateMachineTest.eventsCarryTheTaskSequence`; catalogue entries in `docs/architecture/events/approval.yaml`.
**Confidence:** High.

### RULE-386: Every approval moment reads the one injected Clock, including the process engine
**Category:** Policy
**Priority:** P0
**Source:** FLW-1 (one-coordinator 2026-10-15)
**Plain English:** Every business moment of an approval (task creation, assignment, claim, completion, deadline, reminder, escalation, reassignment, the decision time on a task record, the archive cut-off date and the moment the scheduled job judges) comes from the single injected Clock (UTC) and is cut into workdays in the business zone. The process engine is wired to that same Clock when it is built; it never reads the machine or the database clock, and its clock cannot be set or reset. Deadlines are recorded on `approval_tasks` and judged by the scheduled job, not by engine timers or engine task due dates. The F-14 reassignment on a leaver and the ADR-0032 escalation of a pending to-do after 5 workdays obey the same rule. Row-level technical columns (`created_at` / `updated_at` defaulting to the database clock) and the engine's technical lock and logging times are outside this rule; R-4 removes those defaults.
**Specification:**
- Given the injected Clock fixed at X, When an approval starts, Then the engine's current time, the task's creation time and the history start time are X, and a task due of one hour from start is X+1h.
- Given the Clock then moved to X+30m, When the task is completed, Then the history end time is X+30m.
- When anything tries to set or reset the engine's clock, Then it fails.
**Tests:** `EngineClockIT`.
**Confidence:** High.
