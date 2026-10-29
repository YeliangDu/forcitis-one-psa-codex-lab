### RULE-01: Budget can only be edited while it is unlocked and not awaiting approval
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/ProjectBudget.java:438-446`
**Plain English:** All budget changes are blocked once the budget is locked (approved) or while it is in PM_BUDGET approval. The one exception is a budget whose approval was sent back to the person who submitted it. This covers importing clauses, adding or renaming deliverables, changing task packages, editing normal cost items and submitting.
**Specification:**
  Given project P's budget has `locked=false` and `approvalStatus=PENDING`, and the workflow is not parked at the initiator node
  When the PM adds a task package "Structural calc" (role R1, 40h)
  Then the request is rejected with APPROVAL_PENDING, "项目预算审批中，不允许该操作"
  Given the same budget whose workflow was returned to the initiator
  When the PM edits the task package
  Then the edit is allowed. If `locked=true`, the edit is rejected with LOCKED whether or not it was returned.
**Parameters:** `APPROVAL_PENDING="PENDING"`, `APPROVAL_REJECTED="REJECTED"`. The returned-to-initiator flag is held in memory only and resets on every load.
**Edge cases handled:** A rejected budget (REJECTED) can be edited again. The returned flag never overrides a lock. Sensitive cost items use a looser check (locked only; see RULE-07).
**Suspected defect:** The budget write endpoints (`PmProjectBudgetController` `/import`, `/task/*`, `/lock`, `/deliverable*`, `/cost-items`) check only the permission `fst:pm-project:budget` and turn off `@DataPermission`. No use case checks that the caller is that project's PM. Anyone with the permission can edit or submit any project's budget. The progress-side writes do check this (see RULE-13).
**Confidence:** High for the lock check. Medium for the ownership gap. SME question: "Should budget editing be limited to the project's own PM or delivery manager, or can anyone holding `fst:pm-project:budget` edit any project?"

### RULE-02: Every task package must be complete before a budget can be submitted
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/ProjectBudget.java:326-377`
**Plain English:** Saving the budget means submitting it for approval. That is allowed only when every task package has a role, every non-obsolete task package has a name, and every non-obsolete task package with planned hours above 0 has a matching role cost rate.
**Specification:**
  Given an editable budget with task package TP1 (role "ENG_SR", 80h, no rate found for that role and city)
  When the PM submits the budget
  Then it is rejected with TASK_ROLE_RATE_MISSING. A missing rate would count as cost 0 and inflate the margin.
  Given TP1 has 0h and no rate
  Then it passes (a missing rate does not change the amount).
**Parameters:** Hours threshold `> 0`. A role is required (blank or whitespace counts as missing).
**Edge cases handled:** Obsolete task packages skip the name and rate checks, but the role check still applies to them. Assignees are not required (assignment happens at execution time).
**Suspected defect:** The class comment says the role "must be in the static rate-table domain", but `validateTask` (lines 802-808) only checks that hours are not negative. The role value domain is never checked on the server. The `TASK_ROLE_INVALID` reason is unused in this aggregate.
**Confidence:** High.

### RULE-03: A budget change can only start from a locked budget
**Category:** Validation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/ProjectBudget.java:454-464`
**Plain English:** A budget change (revision) can be started only after the budget has been approved and locked. Starting one unlocks the budget and clears its approval state.
**Specification:**
  Given budget `locked=false`, `approvalStatus=PENDING`
  When the PM starts a revision
  Then it is rejected with REVISION_NOT_ALLOWED, "请等待审批终态后再发起变更"
  Given `locked=false` and not pending
  Then it is rejected with "尚未锁定，可直接编辑无需发起变更"
  Given `locked=true`
  Then the budget is unlocked, `approvalStatus=null`, `processInstanceId=null`
**Parameters:** None.
**Edge cases handled:** The use case also blocks the revision when the project archive has no contract terms (`BUDGET_ARCHIVE_TERMS_MISSING`, StartBudgetRevisionUseCase:61-64).
**Confidence:** High.

### RULE-04: A revised budget is rejected if the contract-term archive changed during editing
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/delivery/project/LockProjectBudgetUseCase.java:120-147`
**Plain English:** When a revised budget is submitted, its non-obsolete clauses must still match the project's archived contract terms exactly: the same term codes, and for each code the same title, clause type and amount. Otherwise the PM must start a new revision.
**Specification:**
  Given revision IN_PROGRESS, budget clause T-01 with amount ¥300,000.00, and archive term T-01 now ¥320,000
  When the PM submits
  Then it is rejected with BUDGET_REVISION_ARCHIVE_DRIFTED
  Given archive amount 300000 and budget amount 300000.00
  Then this passes (amounts are compared numerically, so scale does not matter)
**Parameters:** Compared fields: term code set, name/title, clauseType, amount.
**Edge cases handled:** Applies when the revision is IN_PROGRESS or REJECTED (resubmitting after rejection). Obsolete clauses and clauses with no term code are excluded. null equals null.
**Suspected defect:** This check does not run on the first (non-revision) submission. Drift between initialisation and the first lock is not detected.
**Confidence:** High.

### RULE-05: Obsolete nodes must be acknowledged before a revision can be submitted
**Category:** Validation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/BudgetRevision.java:96-117`
**Plain English:** If a revision made any clause, deliverable or task package obsolete, the submitter must explicitly confirm the obsolete list. Their identity is recorded. A revision can be submitted only from IN_PROGRESS or REJECTED.
**Specification:**
  Given revision #2 IN_PROGRESS with `obsoleteNodeCount=5` (actual cost ¥12,400)
  When it is submitted with `obsoleteConfirmed=false`
  Then it is rejected with REVISION_OBSOLETE_UNCONFIRMED
  When it is submitted with `true` by user 1001
  Then status becomes PENDING and `obsoleteConfirmedBy="1001"`
**Parameters:** The change reason must be in `BudgetRevisionReasonEnum`. "OTHER" requires a note (lines 54-66).
**Edge cases handled:** With 0 obsolete nodes no confirmation is needed. A PENDING or EFFECTIVE revision cannot be resubmitted.
**Confidence:** High.

### RULE-06: Archive reconciliation keeps clauses that have data and removes those that do not
**Category:** Policy
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/ProjectBudget.java:477-538`
**Plain English:** During a revision, clauses are matched to the archive by term code. New terms create clauses. Matched clauses update title, type and amount only. Clauses gone from the archive (including legacy clauses with no term code) are marked obsolete if they have incurred data (hours, deliverables, revenue), otherwise removed. Obsolete nodes become read-only everywhere.
**Specification:**
  Given clauses C1 (term T1, has timesheet hours) and C2 (term T2, no data), and an archive that now contains only T1 and T3
  When the revision starts
  Then C1 is kept with updated attributes, C2 is soft-deleted, and a new clause is created for T3 with code `{projectCode}-C{max used+1}`
  Given T1 is also removed from the archive
  Then C1 is marked obsolete and any later edit is rejected with NODE_OBSOLETE
**Parameters:** The code sequence continues from all used codes, including soft-deleted ones (never reused).
**Edge cases handled:** The PM's existing deliverables and task packages under matched clauses are never touched. Obsolete status cannot be undone.
**Confidence:** High.

### RULE-07: Normal cost items are edited during budgeting; sensitive items need a permission
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/ProjectBudget.java:579-626`
**Plain English:** Travel and printing can be entered only while the budget is editable. The six sensitive items (5 outsourcing types plus info-service fee) can be entered whenever the budget is not locked, including during approval, but only by users with `fst:pm-project:budget-cost-view`. Approval-node adoption accepts only sensitive items and only while PENDING. Negative amounts are rejected and null counts as 0.
**Specification:**
  Given budget PENDING, and user U holds budget-cost-view
  When U sets `outsourceSafety=¥50,000`
  Then it is saved
  When U sets `travel=¥8,000` in the same state
  Then it is rejected with APPROVAL_PENDING
  When a user without the permission submits `outsourceLight`
  Then it is rejected with BUDGET_SENSITIVE_COST_PERMISSION_DENIED (UpdateBudgetCostItemsUseCase:56-58)
**Parameters:** EDITABLE = {travel, print}. SENSITIVE = {outsourceSafety, outsourceLight, outsourceWindow, outsourceSign, outsourceOther, infoService}. Permission `fst:pm-project:budget-cost-view`.
**Edge cases handled:** Unknown categories are rejected. If the permission RPC fails, the user is treated as having no permission. Updates only change the categories sent (upsert subset).
**Confidence:** High.

### RULE-08: Rules for an approver changing the budget tree during approval
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/ProjectBudget.java:639-723`
**Plain English:** An approver may add, rename or delete deliverables and task packages only while the budget is PENDING. The submitted list is the complete list, so anything missing from it is deleted. Obsolete task packages cannot be deleted or edited. Every new or updated task package needs a name. After the changes, every task package must still have a role.
**Specification:**
  Given budget PENDING and obsolete task TP9 (with logged hours)
  When the approver submits a change list without TP9
  Then it is rejected with NODE_OBSOLETE
  Given a new task package with role "ENG" and a blank name
  Then it is rejected with TASK_NAME_REQUIRED, "不可通过"
**Parameters:** Negative `deliverableId` refers to a temporary deliverable created in the same batch.
**Edge cases handled:** Hours must not be negative. A target deliverable that is not in the tree is rejected.
**Suspected defect:** This path does not run the RULE-02 rate-missing check. An approver can add a task package with hours but no matching rate, and it is locked into the baseline at cost 0.
**Confidence:** High.

### RULE-09: Budget margin vs quotation margin decides whether CEO approval is needed
**Category:** Policy
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/delivery/project/ApplyBudgetApprovalUseCase.java:100-113`
**Plain English:** The budget gross margin is compared with the source quotation's gross margin. If the budget margin is at least as high, `budgetGmNotBelowQuote="true"` and the CEO step is skipped. If it is lower, or either margin is missing, the budget goes to CEO final approval. The comparison runs at submission (BudgetApprovalStarterImpl:58-60) and again after every approver change.
**Specification:**
  Given contract ¥1,060,000, quote tax rate 6%, budget cost ¥700,000, so budget GM = (1,000,000.00 − 700,000)/1,000,000.00 = 0.3000, and quote GM 0.3200
  When submitted or adopted
  Then `notBelow=false` and the budget is routed to the CEO
  Given quote GM 0.2800
  Then `notBelow=true` and the CEO is skipped
**Parameters:** Comparison `>=`. Process key `PM_BUDGET`.
**Edge cases handled:** No source quotation, contract amount ≤ 0 or no clauses all make the margin null, which routes to the CEO.
**Confidence:** High.

### RULE-10: Which quotation counts as the budget baseline
**Category:** Policy
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/delivery/project/BudgetSourceQuotationFinder.java:26-44`
**Plain English:** The baseline quotation (the source for the deliverable and task template, margin and tax rate) is the newest quotation on the opportunity that has a contract code (the winning bid). If none has one, it is the newest APPROVED quotation. Otherwise there is none.
**Specification:**
  Given opportunity O with Q3 (newest, APPROVED, no contract code) and Q2 (older, contract code "HT-001")
  When the baseline is resolved
  Then Q2 is used
**Parameters:** Status `QuotationStatus.APPROVED`.
**Edge cases handled:** A null opportunity returns null. Quotations whose clause code does not match any archive term are reported as unmatched and not imported (InitializeProjectBudgetUseCase:73-77).
**Confidence:** High.

### RULE-11: Budget gross margin uses net-of-tax revenue and a valid tax rate
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/ProjectBudget.java:775-792`
**Plain English:** Budget GM = (contract amount ÷ (1 + tax rate), rounded half-up to 2 decimals − budget cost) ÷ that net amount, rounded half-up to 4 decimals. Budget cost is labour plus cost items. The tax rate must be in [0, 1), otherwise submission is blocked.
**Specification:**
  Given contract ¥1,060,000, tax 0.06, labour ¥600,000, cost items ¥100,000
  Then GM = (1,000,000.00 − 700,000)/1,000,000.00 = 0.3000
  Given a quote tax rate of 1.06
  Then SOURCE_TAX_RATE_INVALID is raised and submission is blocked before the PENDING state is claimed (LockProjectBudgetUseCase:82-87)
**Parameters:** `DEFAULT_TAX_RATE=0.06` when there is no source quotation. HALF_UP rounding at scale 2 and 4.
**Edge cases handled:** No clauses, contract amount ≤ 0 or net amount ≤ 0 all return null.
**Confidence:** High.

### RULE-12: Execution-side structure is frozen during budget approval, and planned hours are frozen after lock
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/delivery/project/OperateProgressTaskUseCase.java:244-295`
**Plain English:** On the progress page, task packages and deliverables cannot be created, edited or deleted while the budget is PENDING. Once the budget is locked, a task package's planned hours cannot change and new task packages must have 0 planned hours. Name, role, assignee and dates can still change.
**Specification:**
  Given budget locked and TP1 with plannedHours 40
  When the PM updates TP1 to 48h
  Then it is rejected with PROGRESS_PLANNED_HOURS_LOCKED
  When the PM creates a task with 10h
  Then it is rejected
  When the PM creates a task with 0h
  Then it is allowed
**Parameters:** null hours count as 0 for comparison.
**Edge cases handled:** Status changes (done, reopen, rework, ETC) are not subject to these checks. A deliverable can still be added during execution after lock, with no amount (lines 304-320).
**Confidence:** High.

### RULE-13: Only the project's PM can change execution-side structure
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/delivery/project/OperateProgressTaskUseCase.java:504-510`
**Plain English:** Updating or creating task packages, creating or deleting deliverables, and rework by a new deliverable require the logged-in user to be the project's manager. A missing PM or missing project counts as a failure (fail-closed).
**Specification:**
  Given project P with managerId 2001
  When user 3005 calls `/progress/task/update` for P
  Then it is rejected with PROGRESS_TASK_NOT_PROJECT_MANAGER
**Parameters:** None.
**Edge cases handled:** Self-create (member) and self-done (assignee) use different checks.
**Suspected defect:** `delete()` (lines 440-453) does not call `requireProjectManager`. Any holder of `fst:pm-project:progress` can delete another project's task packages, as long as no hours are logged. Also, `/task/update`, `/task/create` and `/deliverable/create` have no `@PreAuthorize` at all (PmProjectProgressController:288-315), so this ownership check is their only guard.
**Confidence:** High. SME question: "Should task deletion require the project PM like the other structural writes?"

### RULE-14: Team members can mark only their own task packages done
**Category:** Validation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/delivery/project/OperateProgressTaskUseCase.java:63-77`
**Plain English:** The self-service "done" endpoint needs no progress permission, but the task package must be assigned to the logged-in user.
**Specification:**
  Given TP1 assigned to user 4001
  When user 4002 calls `/task/done/my`
  Then it is rejected with PROGRESS_TASK_DONE_NOT_ASSIGNEE
**Parameters:** None.
**Edge cases handled:** The same state-machine checks as RULE-15 apply.
**Confidence:** High.

### RULE-15: Task package state transitions and their guards
**Category:** Lifecycle
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/DeliverableProgress.java:47-84`
**Plain English:** A task can be marked done only from DOING or REWORK, and not while an ETC change is in approval. Reopening is allowed only from DONE (back to DOING). Rework is allowed only from LOCKED (to REWORK). Obsolete nodes reject all of these.
**Specification:**
  Given TP1 DOING with `etcApprovalStatus=PENDING`
  When it is marked done
  Then it is rejected with TASK_DONE_LOCKED_BY_ETC_APPROVAL
  Given TP1 LOCKED
  When it is reopened
  Then it is rejected with TASK_TRANSITION_INVALID
**Parameters:** States DOING, DONE, LOCKED, REWORK.
**Edge cases handled:** DONE and LOCKED tasks cannot have fields or ETC edited (`requireEditableTask`).
**Confidence:** High.

### RULE-16: Once hours are logged, the assignee is fixed and the task cannot be deleted
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/DeliverableProgress.java:292-315`
**Plain English:** A task package counts as having hours once it has actual hours above 0 or any timesheet line that is not rejected or cancelled (including pending). After that its assignee cannot change and it cannot be deleted (`removeTask`, lines 386-395). Planned and actual date ranges must have start ≤ end.
**Specification:**
  Given TP1 assigned to 4001, actual 0h, and one timesheet line pending approval
  When the PM reassigns it to 4002
  Then it is rejected with TASK_ASSIGNEE_LOCKED
  Given planStart 2026-11-10 and planEnd 2026-11-01
  Then it is rejected with TASK_SCHEDULE_INVALID
**Parameters:** Hours threshold `> 0`.
**Edge cases handled:** The Excel import path keeps the old assignee silently instead of failing (lines 216-224). A single-sided date is allowed.
**Confidence:** High.

### RULE-17: An ETC increase needs approval only when it pushes projected labour cost over budget
**Category:** Policy
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/DeliverableProgress.java:110-136`
**Plain English:** Lowering or keeping the ETC (estimate to complete) applies immediately. An increase also applies immediately if the projected labour cost stays at or below the budget labour cost. Otherwise it needs PM_ETC approval with a mandatory reason, and the ETC stays frozen until a final decision.
**Specification:**
  Given budget labour ¥100,000, current projected ¥98,000, task rate ¥200/h, current ETC 5h
  When ETC is set to 12h (+7h × 200 = +¥1,400 → ¥99,400)
  Then it applies directly
  When ETC is set to 20h (+¥3,000 → ¥101,000 > ¥100,000)
  Then a reason is required and the change goes to approval
**Parameters:** "Over" means strictly greater than. The delta is rounded half-up to 2 decimals. A missing rate counts as 0.
**Edge cases handled:** A negative ETC is rejected. While approval is PENDING a second request is rejected. If no headroom figure is available, every increase needs approval. Self-created task packages always need approval. Excel import applies ETC without approval.
**Confidence:** High.

### RULE-18: Submitting a deliverable requires all its tasks to be finished, then locks them
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/DeliverableProgress.java:403-423`
**Plain English:** A deliverable can be submitted only if it is not obsolete, has at least one task package, and every task package is DONE or LOCKED. DONE packages then become LOCKED, so no more hours can be logged on them.
**Specification:**
  Given deliverable D with TP1 DONE and TP2 DOING
  When the PM submits D
  Then it is rejected with TASK_NOT_ALL_DONE
  Given TP1 and TP2 are DONE
  Then both become LOCKED
**Parameters:** None.
**Edge cases handled:** A deliverable with no task packages is rejected with NO_TASK_PACKAGE. An obsolete deliverable is rejected with NODE_OBSOLETE.
**Confidence:** High.

### RULE-19: Only one active submission per deliverable
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/delivery/project/SubmitDeliverableUseCase.java:54-84`
**Plain English:** Only one active submission is allowed per deliverable. A submission is active while it is in internal review, PMO review, awaiting acceptance, accepted or converted. A rejected (DRAFT) one is not active, so a new submission is allowed. If the active one was returned to the initiator, only the original submitter can resubmit it.
**Specification:**
  Given D has submission S1 in DLV_ACCEPTING
  When the PM submits again
  Then it is rejected with DELIVERABLE_SUBMISSION_EXISTS
  Given S1 is in DLV_INTERNAL and returned to initiator 2001
  When user 2002 submits
  Then it is rejected
**Parameters:** Active statuses: DLV_INTERNAL, DLV_EVAL, DLV_ACCEPTING, DLV_ACCEPTED, DLV_CONVERTED.
**Edge cases handled:** A conditional insert guards against concurrent duplicates. While a submission is active, task packages under D cannot be created or deleted (OperateProgressTaskUseCase:528-532).
**Confidence:** High.

### RULE-20: Required submission inputs depend on the phase
**Category:** Validation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/DeliverableSubmission.java:220-253`
**Plain English:** Every submission needs a title and a phase of INITIAL or DEEPEN. INITIAL (stage review) needs a meeting time and attachments are optional. DEEPEN (internal review) needs at least one review file, the requirement-review date, the drawing category and whether construction-drawing review is included. Fields that belong to the other phase are cleared.
**Specification:**
  Given phase DEEPEN with 1 file, date 2026-10-01, category "A", and constructionDrawingReview not set
  When the PM submits
  Then it is rejected with CONSTRUCTION_DRAWING_REVIEW_REQUIRED
  Given phase INITIAL with no meeting time
  Then it is rejected with MEETING_TIME_REQUIRED
**Parameters:** Review scores elsewhere in the class must be integers in 0–100 (`REVIEW_SCORE_MIN=0`, `REVIEW_SCORE_MAX=100`).
**Edge cases handled:** Attachments missing a name or URL are dropped. Resubmission reruns the same checks and clears all earlier review outcomes.
**Confidence:** High.

### RULE-21: Rules for registering customer acceptance
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/DeliverableSubmission.java:619-655`
**Plain English:** Acceptance can be registered only on an approved submission (DLV_ACCEPTING). It needs a customer sign date no later than today, plus both an acceptance document and an email confirmation. After that it is fixed (DLV_ACCEPTED).
**Specification:**
  Given S1 DLV_ACCEPTING, today 2026-10-14, sign date 2026-10-15
  When acceptance is registered
  Then it is rejected with ACCEPTANCE_SIGN_DATE_INVALID
  Given sign date 2026-10-12, 1 document and 0 emails
  Then it is rejected with ACCEPTANCE_ATTACHMENT_REQUIRED
**Parameters:** None.
**Edge cases handled:** The use case checks that the submission belongs to the project (RegisterAcceptanceUseCase:32-36).
**Confidence:** High.

### RULE-22: Rework path depends on the deliverable's submission status
**Category:** Validation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/delivery/project/OperateProgressTaskUseCase.java:85-109`
**Plain English:** In-place rework of a LOCKED task is allowed only if the deliverable has no active submission. If the submission is in review, rework is refused until the workflow ends. If it is already approved, rework must go through a new rework deliverable. That path (lines 198-242) requires the PM, no pending budget approval, a reason, at least one selected task, and all earlier rework deliverables approved. Copied tasks start at 0 planned hours.
**Specification:**
  Given D's submission is DLV_ACCEPTED
  When the PM reworks TP1 in place
  Then it is rejected with DELIVERABLE_REWORK_APPROVED_USE_NEW_DELIVERABLE
  Given D's submission is DLV_EVAL
  Then it is rejected with DELIVERABLE_REWORK_LOCKED_BY_SUBMISSION
**Parameters:** Approved statuses: DLV_ACCEPTING, DLV_ACCEPTED, DLV_CONVERTED.
**Edge cases handled:** A rejected (DRAFT) submission allows in-place rework.
**Confidence:** High.

### RULE-23: Revenue confirmation needs a valid clause and a returned contract
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/receivables/revenue/SubmitRevenueConfirmationUseCase.java:44-66`
**Plain English:** A revenue confirmation can be raised only on an existing clause that is not obsolete, on a project whose contract status is "contract returned/signed back". A missing contract status counts as not returned. The state of the deliverables is no longer checked. A resubmission must come from the original submitter, and the workflow must be at the initiator node (lines 96-107).
**Specification:**
  Given clause C1 (non-obsolete) and project contract_status not "合约已回"
  When the PM submits a revenue confirmation
  Then it is rejected with REVENUE_CONTRACT_NOT_BACKED
  Given the contract is returned and no deliverable is accepted
  Then the submission is allowed (subject to RULE-24)
**Parameters:** Permission `fst:pm-project:revenue`.
**Edge cases handled:** Only new submissions and resubmissions are blocked. Confirmations already in flight are not affected.
**Suspected defect:** Code, docs and comments disagree. Seed doc DLV-R18 requires POC = 100%, internal review passed and customer confirmation. `ClauseRevenueContext` comments (lines 21-29) say PREPAY clauses are exempt and other clauses need all deliverables accepted. The executable code applies neither: step 4 says the gate was removed. Also, there is no project-ownership check beyond the permission point.
**Confidence:** Medium. SME question: "Has the business formally dropped the POC = 100% / internal-review precondition for revenue confirmation (refs #1524), and should doc DLV-R18 and the context comments be retired?"

### RULE-24: Total confirmed revenue cannot exceed the clause amount
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/receivables/revenue/RevenueConfirmation.java:64-87`
**Plain English:** The amount must be above 0. The customer evidence set must be complete: sign date, at least one acceptance document and at least one confirmation email. The amount must be no more than the clause amount minus confirmed amounts minus pending amounts. The revenue period is the month of the customer sign date, and any period the client sends is ignored.
**Specification:**
  Given clause ¥300,000, confirmed ¥100,000, pending ¥150,000, so remaining ¥50,000
  When ¥60,000 is submitted
  Then it is rejected with QUOTA_EXCEEDED, "剩余 50000"
  When ¥50,000 is submitted with sign date 2026-09-28
  Then it is accepted as PENDING with period "2026-09"
**Parameters:** Remaining = max(0, clauseAmount − confirmed − pending). A null clause amount counts as 0.
**Edge cases handled:** On resubmission, the item's own pending amount is excluded from the quota.
**Suspected defect:** Unlike deliverable acceptance (RULE-21), the sign date is not checked against today. A future date is accepted and gives a future revenue period.
**Confidence:** High.

### RULE-25: The quota is checked again at final approval, and an over-quota approval is turned into a rejection
**Category:** Policy
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/receivables/revenue/DecideRevenueConfirmationUseCase.java:44-61`
**Plain English:** When a confirmation is finally approved, the quota is recalculated without this confirmation. If its amount no longer fits (because of concurrent approvals or a reduced clause amount), the system rejects it instead and no revenue fact is written. Only a real approval writes the append-only revenue fact, in the same transaction.
**Specification:**
  Given RC-2 for ¥50,000 pending, while the clause was cut to ¥120,000 and ¥100,000 is already confirmed (remaining excluding self is ¥20,000)
  When BPM approves RC-2
  Then status becomes REJECTED with note "审批通过时累计确认金额已超条款金额，系统自动驳回…" and no psa_revenue_fact row is written
**Parameters:** None.
**Edge cases handled:** A message for a confirmation not in PENDING does nothing (redelivery safe). A missing confirmation is only logged.
**Confidence:** High.

---
**Coverage:** I read in full ProjectBudget, BudgetRevision, BudgetRuleViolation, BudgetCostCategory, DeliverableSubmission(+Status/SubmissionInputs), DeliverableProgress, TaskPackage, LaborCostHeadroom, RevenueConfirmation, ClauseRevenueQuota/Context, AcceptanceEvidence, and the Lock/StartRevision/Initialize/ApplyBudgetApproval/DecideBudgetApproval/UpdateCostItems/OperateProgressTask/SubmitDeliverable/RegisterAcceptance/SubmitRevenue/DecideRevenue use cases. I also read BudgetSourceQuotationFinder and parts of BudgetApprovalStarterImpl.
I only skimmed or grepped the pmproject controllers (permissions only), ProjectServiceImpl (just `modificationRights`: BU project director role, delivery manager or PM gets the edit flag on the project list), ContractTermServiceImpl, ProjectTermArchive and the service/budget legacy code. I did not read ImportProgressWorkbookUseCase, RecordStageReview/RecordInternalReview, DecideEtcApproval or Rename use cases, or the domain/receivables billing view.
Possibly missed: the review-node permission gates in RecordStageReview/RecordInternalReview, Excel import validations, legacy InvestForecast/HourlyRate validations (duplicate year / duplicate rate rows), the contract-term stage logic in ContractTermServiceImpl:66-87, and the seed docs under `docs/domain/03_项目交付域_delivery/` (only the summary rule list was cross-checked). No instruction-like text aimed at automated tools turned up in the files I read.