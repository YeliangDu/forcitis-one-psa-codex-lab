# Approval integrity and legal-entity attribution business rules — RULE-492..RULE-504, RULE-506..RULE-508

Scope: `platform.approval` (assignment, reasons, task and request state, start rounds, routing flags, task cards) and `organization.workforce` (legal-entity attribution of employees and departments). These are rules of FORCITIS one. Basis: the tests listed on each card, read together with the decisions and ADRs cited. Placeholder numbers; the coordinator renumbers. Where the cited basis does not decide a value or behaviour, the card says so in **Needs decision** and its Review column is Yes.

| ID | Domain | Title | Priority | Review |
|---|---|---|---|---|
| RULE-492 | approval | An approver chosen on the form who has no user account makes the start fail with an alert; nothing is skipped and no approval is left behind | P0 | No |
| RULE-493 | approval | A rejection, return, cancellation, transfer or reassignment must carry a reason; a request has a reason exactly when it ended in one of the first three | P0 | No |
| RULE-494 | approval | A holder can give a task back to the candidates, who can claim it again; the process administrator can hand any open task to a named person | P1 | No |
| RULE-495 | approval | A task's outcome and effect are recorded together; an automatic pass is always an approval; a content digest is kept only for a binding approval | P0 | No |
| RULE-496 | approval | Start rounds are append-only: a recorded round is never changed, deleted or truncated | P0 | No |
| RULE-497 | approval | A start refused because the caller does not own the document is audited under the signed-in caller, never under the initiator the request names | P0 | No |
| RULE-498 | approval | Each routing flag turns on exactly the nodes that declare it; a flag no node declares is refused, not ignored | P0 | No |
| RULE-499 | approval | Every candidate gets the task card; a claim, or a decision on an unclaimed task, marks the same card handled and sends no further reminder | P1 | No |
| RULE-500 | approval | An approved request is final and cannot be started again | P0 | No |
| RULE-501 | approval | The department of a department-head node is optional; when not given, the initiator's own organization unit is used | P1 | Yes |
| RULE-502 | workforce | Nobody changes their own legal-entity attribution, nor that of their own department or any department above it | P0 | No |
| RULE-503 | workforce | An attribution request that changes nothing records nothing: the same legal entity again, or a correction with the same end date | P1 | Yes |
| RULE-504 | workforce | The legal entity of an employee on a date is the employee's own attribution, else the nearest department up the chain; nothing found, or a broken tree, resolves to none | P0 | No |
| RULE-506 | workforce | A countersign is visible only to those who decide it; anyone else is told it does not exist and the query is audited | P0 | No |
| RULE-507 | workforce | While a department merge is pending, nothing may put a person back into the department being merged, and chained merges must keep their dates in order | P1 | No |
| RULE-508 | workforce | A department's legal-entity attribution cannot be back-dated; a wrong one waits for void-and-append correction | P1 | No |

### RULE-492: An approver chosen on the form who has no user account makes the start fail with an alert; nothing is skipped and no approval is left behind
**Category:** Validation
**Priority:** P0
**Source:** ADR-0033 decision 3 item 3, approval bullet (「员工没有账号时指派失败并告警，不静默跳过」); CODING rule 6 (missing input fails and alerts). Tests: ApprovalAccountsIT#anEmployeeWithoutAnAccountCannotBeAssigned
**Plain English:** Approvers picked on the form are employees; the system turns them into user accounts only when it assigns the task. An employee with no account cannot be assigned. The start fails, an alert is raised, and no approval request is created. An employee who has an account is assigned as usual.
**Specification:**
- Given the form names one employee who has no user account as approver, When the approval is started, Then the start fails, at least one alert is raised, and no approval exists for the document.
- Given the form names the project manager who has an account, When the approval is started, Then one open task is created and its only candidate is that manager's account.
**Confidence:** High

### RULE-493: A rejection, return, cancellation, transfer or reassignment must carry a reason; a request has a reason exactly when it ended in one of the first three
**Category:** Validation
**Priority:** P0
**Source:** user decision 2026-10-16 (intent `2026-10-16-085331-one-data-审批被驳回-被撤销后能不能再发起.md`: rejection, cancellation and the rejection of a co-signer need a reason; the exemption for a hook is removed); intent `2026-10-14-128-1a流程-审批-办理动作.md` (D-1a-75: a transfer needs a reason and is audited); intent `2026-10-14-124-1a流程-审批-流程管理员.md` (D-1a-61: the administrator reassigns with a reason and audit); RULE-285 is superseded on this point (it required no reason). Tests: ApprovalRowRulesIT#aRunningRequestWithAReasonAndAnEmptyReasonAreRefusedByTheDatabase; ApprovalRowRulesIT#transferWithoutAReasonIsRefused; ApprovalRowRulesIT#reassignmentWithoutAReasonIsRefused
**Plain English:** Ending a request as rejected, returned or cancelled, and transferring or reassigning a task, each need a non-blank reason. A request that is running or approved carries no reason. A refused action leaves no record behind.
**Specification:**
- Given a transfer whose reason is missing, empty or only a blank, When it is submitted, Then it is refused with 422 for each of the three forms and the number of records on the request does not change; with the reason "synthetic: on leave" it is accepted (204) and one TRANSFERRED record holds that reason.
- Given an administrator reassignment with the same three bad reasons, Then each is refused with 422 and no record is added; with a reason it is accepted (204) and one REASSIGNED record holds it.
- Given a running request, When a result reason is written to it, or it is set to approved with a reason, Then it is refused; Given cancelled with an empty reason, Then it is refused; Given cancelled with a non-empty reason, Then it is stored.
**Confidence:** High

### RULE-494: A holder can give a task back to the candidates, who can claim it again; the process administrator can hand any open task to a named person
**Category:** Lifecycle
**Priority:** P1
**Source:** intent `2026-10-14-124-1a流程-审批-流程管理员.md` (D-1a-61: the administrator may reassign, with reason and audit); `docs/business/processes/merged-todo-and-notification.md` (claimed and unclaimed task states); glossary terms TaskClaim, Unclaim, Reassignment; reason requirement per RULE-493. Tests: ApprovalTaskStateMachineTest#releaseAndReassign
**Plain English:** A claimed task released by its holder has no holder again and can be claimed by someone else. The administrator can hand an open task, claimed or not, to a named person with a reason; the task then belongs to that person as claimed.
**Specification:**
- Given a claimed task, When its holder releases it, Then it has no assignee; When another person claims it, Then that person is the assignee.
- Given a pending task, When the administrator reassigns it without a reason, Then it is refused with KERNEL_REASON_REQUIRED; with the reason "covering" the task becomes claimed and the named person is its assignee.
**Confidence:** High

### RULE-495: A task's outcome and effect are recorded together; an automatic pass is always an approval; a content digest is kept only for a binding approval
**Category:** Lifecycle
**Priority:** P0
**Source:** RULE-302 and `REIMAGINED_ARCHITECTURE.md` line 265 (a repeat approver passes automatically when the content digest is unchanged; a changed content must be approved again); RULE-291 (a co-signer's decision is advisory); glossary terms TaskOutcome, TaskEffect, AutoApproved, Advisory; `docs/data/repair-cards/data-repair-approval.md` AP-02. Tests: ApprovalRowRulesIT#outcomeAndEffectComeInPairs; ApprovalRowRulesIT#anAutomaticPassIsAnApproval; ApprovalRowRulesIT#anApprovedHashBelongsToABindingApproval
**Plain English:** A completed task has either both an outcome and an effect or neither. An automatic pass (effect AUTOMATIC) can only be an approval. The content digest is written only when the outcome is approved and the effect is binding; a rejection, or an advisory approval, never carries one.
**Specification:**
- Given a pending task, When it is completed with only the outcome APPROVED, or only the effect BINDING, Then it is refused.
- Given a task completed with effect AUTOMATIC, When the outcome is REJECTED, Then it is refused.
- Given a task with a digest, When the outcome is REJECTED with effect BINDING, or APPROVED with effect ADVISORY, Then it is refused.
**Confidence:** Medium (the digest rule follows RULE-302 and the glossary; no card states the advisory case in these words)

### RULE-496: Start rounds are append-only: a recorded round is never changed, deleted or truncated
**Category:** Lifecycle
**Priority:** P0
**Source:** intent `2026-10-14-138` and RULE_REVIEWS RULE-287 (every start is a new round on the same document; history is kept per round and can be read round by round); ADR-0033 decision 7 item 2 (`start_rounds` with its own key); `docs/data/repair-cards/data-repair-approval.md` AP-05 (registered APPEND_ONLY). Tests: ApprovalRowRulesIT#startRoundsCannotBeChanged; ApprovalRowRulesIT#startRoundsCannotBeTruncated
**Plain English:** Once a start round is written it cannot be edited, even to change the initiator or touch its timestamp, nor deleted, nor can the table be emptied. A new start still writes a new row.
**Specification:**
- Given one recorded round, When its initiator is updated, or its updated-at is set, or it is deleted, Then each attempt is refused (SQLSTATE 23001) and the row is unchanged.
- Given another document is started, Then it writes its own single round.
- Given the table is truncated, Then it is refused (0A000) and the round is still there; a before-truncate trigger named `start_rounds_reject_truncate` exists.
**Confidence:** High

### RULE-497: A start refused because the caller does not own the document is audited under the signed-in caller, never under the initiator the request names
**Category:** Validation
**Priority:** P0
**Source:** `docs/ops/security-invariants.md` I-5 (the initiator is the signed-in user only; the caller must own the document; RULE-265, RULE-280 ownership gate); `REIMAGINED_ARCHITECTURE.md` SEC-117 row. Tests: ApprovalStartOwnershipIT#theRefusalNamesTheCallerNotTheInitiatorInTheRequest
**Plain English:** When a caller asks to start someone else's document and is refused with access denied, the audit entry `platform.approval.start.denied` names the person who is actually signed in. The initiator written in the request is untrusted and is never the actor of the denial.
**Specification:**
- Given document D belongs to user B and user C is signed in, When C starts D naming user A as the initiator, Then access is denied.
- Then the denial is audited at least once with C's account as actor, and never with A's account.
**Confidence:** High

### RULE-498: Each routing flag turns on exactly the nodes that declare it; a flag no node declares is refused, not ignored
**Category:** Policy
**Priority:** P0
**Source:** intent `2026-10-14-131-1a流程-审批-事件法人DMN.md` (D-1a-100: node attributes and routing flags, no decision table in 1a); RULE-388, RULE-389, RULE-390 (project hours to the project manager, pre-sales to the owning department head, overhead to the supervisor); RULE-378 (the deadline and escalation interval are node attributes); RULE-391 (a submission with no flag fails); CODING rule 6. Tests: NodeRoutingIT#shippedProcessRoutesByItsAttributes; NodeRoutingIT#anUnknownFlagIsRefused
**Plain English:** The timesheet approval is routed by the flags the timesheet sets. A flag switches on the node that declares it and leaves the others off. Every task created records its deadline, reminder and escalation times. A flag that no node declares is a start error, and nothing is stored.
**Specification:**
- Given the flags projectHours and overheadHours, When the approval starts, Then the project manager node and the supervisor node are routed, the department head node is not, and exactly two tasks are open (projectManagerReview and supervisorReview), each with due, remind and escalate times set.
- Given the flags projectHours and "projectHoursTypo", When the approval starts, Then it is refused with PLATFORM_APPROVAL_ROUTING_FLAG_UNKNOWN, no approval request exists and no process instance exists for the document.
- The flags the shipped process reacts to are projectHours, preSalesHours and overheadHours.
**Confidence:** High

### RULE-499: Every candidate gets the task card; a claim, or a decision on an unclaimed task, marks the same card handled and sends no further reminder
**Category:** Policy
**Priority:** P1
**Source:** intent `2026-10-14-138-合并待办卡片与展示-逐项勾选-办.md` (D-1a-133: the card shows initiator, department, start time and deadline, and a business summary given at start; D-1a-135); `docs/business/processes/merged-todo-and-notification.md` item 8 (a claim or a direct decision marks the same card handled); `REIMAGINED_ARCHITECTURE.md` Q-3 (every candidate gets the card; after a claim only the claimer acts); RULE-374 (recipients are resolved by the messaging id, exact id, no broadcast). Tests: TaskNotificationIT#everyCandidateGetsTheCard; TaskNotificationIT#claimMarksTheCardHandled; TaskNotificationIT#decidingAnUnclaimedTaskMarksTheCardHandled
**Plain English:** When a task is created, one card goes to all its candidates, keyed by the task, linking to it and addressed to exact accounts only. When someone claims the task, or decides it without claiming first (which claims it implicitly), the same card is updated as handled by that person; no second card or reminder is sent.
**Specification:**
- Given a task with two candidates, When it is created, Then exactly one card is sent for that task, addressed to both candidates by exact messaging id and no one else.
- Then the card title contains the node name 项目经理审批, its link contains the task id, and its description contains the process name 工时审批, the business summary, the initiator, the start time and the deadline, and neither the title nor the description shows the internal process key.
- Given the second candidate claims the task, Then the same card is marked handled with a note naming the claimer, and still only one send exists for the task.
- Given the first candidate decides the unclaimed task with approve, Then the card is marked handled with a note naming the first candidate.
**Confidence:** Medium (the exact wording of the card fields follows the D-1a-133 field list; not decided word by word)

### RULE-500: An approved request is final and cannot be started again
**Category:** Lifecycle
**Priority:** P0
**Source:** intent `2026-10-14-126-1a流程-审批-重新发起.md` (D-1a-78: returned, rejected and cancelled may open a new round; approved may not be started again). Tests: ApprovalRequestStateMachineTest#approvedIsFinal
**Plain English:** From the approved status the request has no further status: no new round can be started on it. Rejected and cancelled requests can still be started again on the same document (RULE-493 keeps their reasons).
**Specification:**
- Given a request in status APPROVED, When the successors of that status are asked, Then there are none.
**Confidence:** High

### RULE-501: The department of a department-head node is optional; when not given, the initiator's own organization unit is used
**Category:** Policy
**Priority:** P1
**Source:** `docs/data/timesheet-replay.md` line 121 (the department variable of a DEPARTMENT_HEAD node is optional; not written means the initiator's own unit, listed as kept); `REIMAGINED_ARCHITECTURE.md` line 81 (DEPARTMENT_HEAD is one of the four assignment types kept). No user decision found for the default. Tests: AssigneeResolverTest#departmentIsOptionalForTheDepartmentHead
**Plain English:** A department-head node may name a department through a process variable. If it names none, the head of the department the initiator belongs to is the candidate; if it names one, the head of that department is.
**Specification:**
- Given a DEPARTMENT_HEAD node with no department written, Then the rule is the department head with no variable.
- Given the node names the variable deliveryUnitId and the initiator belongs to another unit, When resolved with deliveryUnitId set to unit 12, Then the candidate is employee 110, the head of unit 12.
**Needs decision:** When a department-head node names no department, is the initiator's own department the right default, or must every such node name its department?
**Confidence:** Medium

### RULE-502: Nobody changes their own legal-entity attribution, nor that of their own department or any department above it
**Category:** Validation
**Priority:** P0
**Source:** ADR-0027 decision 2 item 2 (the data administrator may not add or change the employee-to-legal-entity period of themselves, nor the department-to-legal-entity period of their own department or one above it; 403 and audited); ADR-0033 decision 3 item 3 (the comparison is by employee id through the department chain). Tests: OwnAttributionIT#nobodyChangesTheirOwnAttribution
**Plain English:** A person with the organization data role cannot attribute themselves, their own department or any department above it to a legal entity. Every refusal is audited. The same person can still attribute someone else.
**Specification:**
- Given a maintainer in department "own", which is under "top", When the maintainer attributes their own employee entry, or "own", or "top", Then each is refused with 403 and error code ORGANIZATION_WORKFORCE_OWN_ATTRIBUTION_FORBIDDEN, and at least three audit entries `organization.workforce.legal-entity-attribution.refused` exist for the maintainer.
- Given a colleague with the same role, When the colleague attributes the maintainer's entry, Then it succeeds (200).
**Confidence:** High

### RULE-503: An attribution request that changes nothing records nothing: the same legal entity again, or a correction with the same end date
**Category:** Policy
**Priority:** P1
**Source:** ADR-0026 decision 2 item 5 (attribution periods with effective dates, not overlapping); RULE-385 (a correction adds a record of the corrected period, the new end date, reason and operator); `docs/data/repair-cards/data-repair-workforce.md` WF-12 (a correction needs the new end date to differ from the old). No decision found for either case. Tests: LegalEntityAttributionTest#sameEntityIsANoOp; WorkforceRowConstraintsIT#wf12EmptyCorrectionIsRefused
**Plain English:** Attributing a department or employee to the legal entity it already has adds no period and raises no event. A correction record whose new end date equals the old end date is refused.
**Specification:**
- Given an attribution that already has legal entity LE1 from January, When LE1 is attributed again from July, Then no event is pending and there is still one period.
- Given an attribution, When a correction is stored with new end date equal to the previous one, Then it is refused (check violation); with an end date one day later it is stored.
**Needs decision:** (1) Is attributing the same legal entity again silently ignored, or refused as an error? (2) Is a correction with an unchanged end date refused as the database does, or accepted without effect?
**Confidence:** Low

### RULE-504: The legal entity of an employee on a date is the employee's own attribution, else the nearest department up the chain; nothing found, or a broken tree, resolves to none
**Category:** Calculation
**Priority:** P0
**Source:** ADR-0026 decision 2 item 5 (the department's legal entity is inherited by sub-departments and can be overridden on a subtree; an employee's own attribution overrides the department's; fail closed when no legal entity resolves); user decision D-1 (intent in ADR-0026 header). Tests: LegalEntityAttributionTest#resolution
**Plain English:** Start with the employee's own attribution on the date. If there is none, walk up from the employee's assigned department and take the first department that has an attribution. If nothing is found, the answer is empty. A department chain that loops, and an unknown employee, also give empty rather than a guess.
**Specification:**
- Given an employee in leaf, under middle, under root and no attribution anywhere, Then the result is empty.
- Given root has LE1, Then the result is LE1; given middle also has LE2, Then the result is LE2 (a subtree overrides its ancestor); given the employee has LE1 of their own, Then the result is LE1.
- Given the parent chain leaf, middle, leaf (a cycle), Then the result is empty and the walk ends; given an employee that is unknown, Then the result is empty.
**Confidence:** High

### RULE-506: A countersign is visible only to those who decide it; anyone else is told it does not exist and the query is audited
**Category:** Policy
**Priority:** P0
**Source:** user decision 2026-11-04 (intent `2026-11-04-083849-one-lead-会签可见-对-照现在做-建议.md`), confirming the behaviour built in #910 (PR #1154)
**Plain English:** A countersign request is visible to the finance owner of its legal entity, and also to business administrators once it is escalated to the second level. The employees affected by an organization-command countersign are visible to the organization data administrator and to the finance owners of the legal entities the change touches. Anyone else gets "not found", and the attempt is audited. The person who submitted the command does not see it through this rule.
**Specification:**
- Given a countersign for LE1, When the finance owner of LE2 queries it, Then the answer is "not found" and an audit record is written.
- Given the same countersign escalated to level 2, Then a business administrator sees it.
**Tests:** with #910.
**Confidence:** High.

### RULE-507: While a department merge is pending, nothing may put a person back into the department being merged, and chained merges must keep their dates in order
**Category:** Policy
**Priority:** P1
**Source:** user decision 2026-11-04 (intent `2026-11-04-083849-one-lead-合并限制-对-照现在做-建议.md`), confirming the behaviour built in #1098 (PR #1176)
**Plain English:** While a merge of X into Y is pending, withdrawing a pending personnel command that would return someone to X is refused. A chained merge (X into Y, then Y into Z) is accepted only when the second merge takes effect after the first; otherwise HR does Y into Z first.
**Specification:**
- Given a pending merge of X into Y, When HR withdraws a pending command that would move someone back to X, Then the withdrawal is refused.
- Given X into Y effective on day 10, When Y into Z is requested for day 5, Then it is refused; for day 15, Then it is accepted.
**Tests:** with #1098.
**Confidence:** High.

### RULE-508: A department's legal-entity attribution cannot be back-dated; a wrong one waits for void-and-append correction
**Category:** Policy
**Priority:** P1
**Source:** user decision 2026-11-04 (intent `2026-11-04-083850-one-lead-回溯更正-可以-先这样-按计划做.md`), confirming the behaviour built in #910 S1 (PR #1141) and the correction planned in ADR-0073
**Plain English:** A department's legal entity can only be set from today onwards. A wrong attribution is corrected by voiding it and recording the right one, which comes with the later card planned in ADR-0073; until then it cannot be corrected backwards.
**Specification:**
- Given a department, When an attribution with a start date before today is submitted, Then it is refused.
**Tests:** with #910 S1.
**Confidence:** High.

**New terms:** none required for the rules above except the following, to be looked up or registered by one-glossary: task card (待办卡片, platform.approval; the existing term Notification may already cover it); content digest (内容摘要, platform.approval, the `approved_hash` value); binding effect (registered as 约束性, platform.approval, counterpart of 建议性 under 效力); process administrator (流程管理员, platform.approval).
