# Timesheet approval routing business rules — RULE-388..RULE-392

Scope: Phase 2 `cost.timesheet` (computing the routing flags `projectHours` / `preSalesHours` / `overheadHours` before an approval starts) and `platform.approval` (the three nodes of `cost_timesheet_approval`). These are rules of FORCITIS one, decided by the user on 2026-10-15 on the ARCH-D5 decision page (`rules/decide/sheets/D5.json`, answers `rules/decide/answers/D5.json`, intent `2026-10-15-221431-one-rules-工时审批路径决策页-ARCH-D.md`). They follow common practice of AEC and professional-services products (Deltek Vision / Vantagepoint, Upland, Oracle, Workday); production totals of the old system were used only as a cross-check (`phase1a/infra/handoff/B-5-dba/evidence/arch_d5_timesheet_routing.sql`). D5-05 and D5-06 were submitted later the same day (all recommended options): no supervisor sign-off of the whole timesheet (RULE-392), and the department head is the head registered on the department in the organization structure (RULE-389).

Already decided elsewhere and not repeated: three parallel nodes read precomputed flags (process/1a/TIMESHEET审批.md T1); return ends the round (M3); no candidate → process administrator plus alert (RULE-250 / RULE-262 reviews); anti-self-approval escalates only when no other candidate exists (RULE-307 review); deadlines RULE-377..384.

| ID | Domain | Title | Priority | Review |
|---|---|---|---|---|
| RULE-388 | timesheet | A project line is approved by the project manager of that line's project; each project manager sees and decides only the lines of their own projects | P0 | No |
| RULE-389 | timesheet | A pre-sales line is approved by the head of the department that owns the pre-sales object | P0 | No |
| RULE-390 | timesheet | An indirect line is approved by the submitter's supervisor | P0 | No |
| RULE-391 | timesheet | A timesheet line is project, pre-sales or indirect; any other kind, or a submission that sets no routing flag, fails with an alert | P0 | No |
| RULE-392 | timesheet | The supervisor does not sign off the whole timesheet; each kind of line is decided only by its own approver | P1 | No |

### RULE-388: A project line is approved by the project manager of that line's project; each project manager sees and decides only the lines of their own projects
**Category:** Policy
**Priority:** P0
**Source:** user decision D5-01, 2026-10-15
**Plain English:** For every project line the candidate is the project manager recorded on the project the line is booked to; a timesheet with lines on several projects gives each of those project managers a task that shows only their own project's lines. The project manager is read from the project only: there is no fallback to task owner, a manager carried on the line or a lookup by project code (the old system's four-step fallback is not kept). No project manager on the project → process administrator plus alert, as already decided.
**Specification:**
- Given a timesheet with lines on projects P1 (manager M1) and P2 (manager M2), When it is submitted, Then M1's task lists only the P1 lines and M2's task only the P2 lines, and the project node completes when both are approved.
- Given P1 has no project manager, When the timesheet is submitted, Then the P1 task goes to the process administrator and an alert is raised; no other person is guessed.
**Tests:** to be written with the Phase 2 timesheet routing card.
**Confidence:** High.

### RULE-389: A pre-sales line is approved by the head of the department that owns the pre-sales object
**Category:** Policy
**Priority:** P0
**Source:** user decisions D5-02 and D5-06, 2026-10-15
**Plain English:** A pre-sales line is booked to an opportunity, lead, customer or visit plan. The candidate is the head of the department that owns that object, not the submitter's own department head and not the opportunity owner. Several owning departments give one task per head, each showing its own lines. The head is the one registered on that department in the organization structure (kept with the org sync, no separate list), read when the task is created. The 1a node `regionalHeadReview` and the term 「区域负责人」 describe this approver wrongly (the old system also took the owning department's head, not a regional unit's); the node and term are renamed to department-head wording through one-glossary and a renaming card.
**Specification:**
- Given a pre-sales line on an opportunity owned by department D, When the timesheet is submitted, Then the head of D is the candidate for that line, whatever department the submitter belongs to.
- Given D has no head, When the timesheet is submitted, Then the task goes to the process administrator and an alert is raised.
**Tests:** to be written with the Phase 2 timesheet routing card.
**Confidence:** High.

### RULE-390: An indirect line is approved by the submitter's supervisor
**Category:** Policy
**Priority:** P0
**Source:** user decision D5-03, 2026-10-15; supervisor as defined by the RULE-372 review (person-level reporting line, not the department head)
**Plain English:** Indirect lines (management, training, internal work, booked to neither a project nor a pre-sales object) go to the submitter's supervisor (`INITIATOR_SUPERVISOR`, level 1), as the 1a node `supervisorReview` already does. An incomplete reporting line moves up as already decided (RULE-250 review).
**Specification:**
- Given a timesheet with indirect lines, When it is submitted, Then the submitter's supervisor gets one task with those lines.
**Tests:** to be written with the Phase 2 timesheet routing card.
**Confidence:** High.

### RULE-391: A timesheet line is project, pre-sales or indirect; any other kind, or a submission that sets no routing flag, fails with an alert
**Category:** Validation
**Priority:** P0
**Source:** user decision D5-04, 2026-10-15; NEXT_BATCH_PLAN Phase 2 前置项 (flag check added with the mapping card); CLAUDE.md rule 6
**Plain English:** The old system's 「混合」 kind is not carried over (no such line in the last 12 months). Each kind maps to exactly one flag: project → `projectHours`, pre-sales → `preSalesHours`, indirect → `overheadHours`. A line of any other kind, or a submission that ends up with no flag set, is refused with an alert, never routed by default. Before migrating old timesheets, the full history is checked once for 「混合」 lines; any found are classed as project or pre-sales by what they are booked to and counted in the migration check report.
**Specification:**
- Given a timesheet whose lines are all project lines, When it is submitted, Then only `projectHours` is set.
- Given a line whose kind is not one of the three, When the timesheet is submitted, Then the submission fails, nothing is started and an alert is raised.
- Given a timesheet with no lines that set a flag, When it is submitted, Then the submission fails with an alert.
**Tests:** to be written with the Phase 2 timesheet routing card.
**Confidence:** High.

### RULE-392: The supervisor does not sign off the whole timesheet; each kind of line is decided only by its own approver
**Category:** Policy
**Priority:** P1
**Source:** user decision D5-05, 2026-10-15
**Plain English:** There is no extra whole-timesheet node for the submitter's supervisor. Completeness (working-day hours plus leave at least 7.5, at most 15 a day, no submission on a full leave day) is enforced by submission checks, not by a reviewer. AEC products offer whole-sheet approval as an option; the old system did not have it and rejected fewer than 0.1 % of lines.
**Specification:**
- Given a timesheet with only project lines, When it is submitted, Then only project-manager tasks are created; the supervisor gets none.
**Tests:** to be written with the Phase 2 timesheet routing card.
**Confidence:** High.
