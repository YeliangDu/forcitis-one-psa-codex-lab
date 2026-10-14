### RULE-01: A budget can only be locked through an approved PM_BUDGET approval, and only when it is complete
**Category:** Lifecycle
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/ProjectBudget.java:326-377`
**Plain English:** "Save budget" does not lock it. It submits the budget for approval and sets it to PENDING, and only if every task package has a role and a name, and every package with hours has a cost rate.
**Specification:**
  Given an unlocked budget with no approval status, and 3 live task packages that all have a role and a name; one package has 40 h and a rate of ¥300/h
  When the PM saves (submits) the budget
  Then wbs_budget_approval_status becomes `PENDING` and the PM_BUDGET process starts. If any package has an empty role → TASK_ROLE_REQUIRED. If a non-obsolete package has an empty name → TASK_NAME_REQUIRED. If a package with 40 h has no rate → TASK_ROLE_RATE_MISSING, and nothing changes
**Parameters:** status values `PENDING`, `REJECTED`, null (never submitted or already approved). Packages with 0 h may have no rate.
**Edge cases handled:** Obsolete packages are skipped by the name/rate check. The DB claims PENDING with a conditional update, allowed only when locked is null or false AND status is null or REJECTED (`ProjectBudgetRepositoryImpl.java:751-762`). If 0 rows are updated, a concurrent submit already happened → BUDGET_APPROVAL_PENDING. A budget "returned to initiator" skips the claim and resubmits on the same process instance. The tax rate and gross margin are validated before the PENDING claim.
**Suspected defect:** `requireAllRolesSelected` does NOT skip obsolete packages, unlike `requireAllTasksComplete`. An obsolete package with no role is read-only, so it would block every future submit. Seed doc OPS-R01 says "预算保存即锁定" (saving the budget locks it), but the code says save = submit for approval and the lock happens only on approval.
**Confidence:** High. The logic is explicit.

### RULE-02: Budget editing is blocked while the budget is locked or awaiting approval
**Category:** Lifecycle
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/ProjectBudget.java:438-446`
**Plain English:** No budget compilation action is allowed once the budget is locked or pending approval (import clauses, add or rename deliverables, add, edit or delete task packages, edit ordinary cost items, submit). The only exception is when the workflow has returned the budget to the initiator.
**Specification:**
  Given a budget with locked=false and approvalStatus=`PENDING`, and the process is not parked at the initiator node
  When the PM adds a task package
  Then the action is rejected with APPROVAL_PENDING. If locked=true it is rejected with LOCKED, even when the process is returned to the initiator
**Parameters:** `returnedToInitiator` is an in-memory flag. The application layer sets it after checking that the instance is alive and parked at the initiator node. It is not persisted.
**Edge cases handled:** Sensitive cost items (5 outsourcing categories + information service fee) are only blocked by `locked` and stay editable while PENDING (lines 596-608). In the execution phase, a deliverable can be added after lock (`addDeliverableInProgress`), with no amount.
**Confidence:** High.

### RULE-03: The PM_BUDGET outcome is a three-way split, guarded by idempotency and the process instance; approval locks the budget, freezes rates and snapshots cost
**Category:** Lifecycle
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/delivery/project/DecideBudgetApprovalUseCase.java:46-86`
**Plain English:** When the approval finishes:
- Approved → the budget is locked, each task package's role rate is frozen, the baseline tree is synced to the execution tables, and budget cost and labour cost snapshots are written.
- Rejected → status REJECTED (the PM can edit and resubmit).
- Cancelled by the initiator → status cleared to "never submitted".
**Specification:**
  Given a project with approvalStatus=`PENDING` and processInstanceId=`P1`
  When a BPM result message arrives with result=2 (通过 / approved) for `P1`
  Then wbs_budget_locked=1 and approvalStatus=null. Each package's lockedCostRate is set to its current roleCostRate. wbs_budget_cost = labour + cost items, and wbs_budget_labor_cost = labour. The active revision (if PENDING) becomes EFFECTIVE.
  With result=3 → `REJECTED`, and the revision becomes REJECTED. With result=4 (已取消 / cancelled) → approvalStatus=null, and the revision becomes REJECTED.
  A message for instance `P0` (not P1), or for a budget that is not PENDING → ignored silently
**Parameters:** BPM results: 2 = 通过 (approved), 3 = 不通过 (rejected), 4 = 已取消 (cancelled). Other results are not written back (`BudgetProcessResultHandler.java:40-44`).
**Edge cases handled:** A missing project → warning, no exception. Duplicate or late messages are a no-op. Real DB errors are rethrown so the message is redelivered.
**Suspected defect:** On cancel, the budget goes back to the clean state (null), but the revision is set to `REJECTED`. The two records disagree, so the UI may show "rejected" for a withdrawn revision. The instance check is skipped when the stored processInstanceId is null (legacy rows).
**Confidence:** High.

### RULE-04: Budget approval routing — the CEO step is skipped only if budget gross margin ≥ quote gross margin
**Category:** Policy
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/infrastructure/delivery/project/BudgetApprovalStarterImpl.java:58-75`
**Plain English:** The gateway variable `budgetGmNotBelowQuote` is "true" only when both margins exist and the budget margin is not lower than the source quote's margin. Otherwise the budget goes to the CEO for final approval.
**Specification:**
  Given contract amount ¥1,060,000, quote tax rate 6%, budget cost ¥700,000, and source quote gross margin 0.2800
  When the budget approval starts
  Then ex-tax revenue = 1,060,000 ÷ 1.06 = ¥1,000,000.00 and budget GM = (1,000,000 − 700,000) ÷ 1,000,000 = 0.3000, so the variable is "true" (CEO skipped). If the quote GM is null, or the contract amount is ≤ 0 → "false" (route to CEO)
**Parameters:** default tax rate 0.06 when the quote has none. GM is rounded HALF_UP to 4 decimals, ex-tax to 2 decimals. The tax rate must be in [0,1). Process key `PM_BUDGET`.
**Edge cases handled:** Each approver adoption recomputes the variable after reloading the aggregate (`ApplyBudgetApprovalUseCase.java:96-113`).
**Confidence:** High.

### RULE-05: Approvers may change the cost tree only while the budget is PENDING; the adopted list is the full set
**Category:** Lifecycle
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/ProjectBudget.java:639-723`
**Plain English:** At an approval node, the approver submits the full list of task packages:
- Packages missing from the list are deleted.
- Packages with an id are updated, and packages without an id are inserted.
- Deliverables can be renamed or added.
- Every touched package needs a name and a role. Obsolete nodes cannot be deleted or edited.
**Specification:**
  Given a PENDING budget with packages T1 and T2, where T2 is obsolete
  When the approver submits a list containing only T1
  Then the request is rejected with NODE_OBSOLETE (T2 cannot be removed). If T2 were not obsolete, it would be deleted, and the diff is persisted package by package (no whole-tree rewrite)
**Parameters:** Negative tempIds reference deliverables added in the same batch.
**Edge cases handled:** Any status other than PENDING → COST_ITEM_NOT_EDITABLE. Hours < 0 are rejected, and null hours become 0.
**Suspected defect:** Deletion through adoption (and `removeTaskPackage` during compilation) does not check `hasHoursInvested`. Whether this is safe depends on the budget task table being separate from the execution table (baseline sync on lock).
**Confidence:** Medium. SME question: "During a budget revision, can a budget task package whose execution twin already has timesheet hours be deleted by the PM or an approver? If so, what should happen to the execution package?"

### RULE-06: A budget revision can only start from a locked budget; it snapshots, unlocks and re-baselines the clause layer
**Category:** Lifecycle
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/delivery/project/StartBudgetRevisionUseCase.java:55-110`
**Plain English:** Starting a revision does the following, in one transaction:
- Requires a locked budget and at least one archive clause.
- Saves a snapshot of the previous version.
- Unlocks the budget and clears approval status and process id.
- Creates revision N (IN_PROGRESS), recording how many nodes became obsolete and their actual cost.
**Specification:**
  Given a locked project with 2 prior revisions
  When the PM starts a revision with reason `OTHER` and note "scope change"
  Then snapshot #2 is saved, revision #3 is created with status `IN_PROGRESS`, wbs_budget_locked=0 and approvalStatus=null. An unlocked budget → REVISION_NOT_ALLOWED ("edit directly"). A PENDING budget → "wait for the outcome"
**Parameters:** revision numbers increase within a project (the first compilation has no revision record; the first revision is 1). Reason must be in `BudgetRevisionReasonEnum`; a note is required when the enum says so (`BudgetRevision.java:54-66`).
**Edge cases handled:** No archive terms → BUDGET_ARCHIVE_TERMS_MISSING. New clauses take their deliverable/workpackage skeleton from the winning quote by clause_code. Unmatched quote clauses are returned to the caller.
**Confidence:** High.

### RULE-07: On revision, clauses are re-matched to the archive; vanished clauses with history become obsolete, others are removed
**Category:** Lifecycle
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/ProjectBudget.java:477-538`
**Plain English:** Clauses are matched to the project archive by term_code:
- Matched clauses sync title, type and amount, and keep their deliverables and tasks untouched.
- New archive terms create clauses with a fresh, never-reused code.
- Clauses that left the archive (including legacy clauses with no term_code) are marked obsolete (read-only forever) if they have incurred data, and soft-deleted otherwise.
**Specification:**
  Given clauses C1 (term A, has timesheet hours) and C2 (term B, no data). The archive now holds terms B and D, and codes PRJ-C1..C3 were used historically
  When the revision starts
  Then C1 becomes obsolete (kept). C2 syncs its amount. A new clause PRJ-C4 is created for D
**Parameters:** code format `{projectCode}-C{n}`, where n = highest used number (including soft-deleted) + 1. "Has data" means timesheet hours, deliverables, or revenue (injected predicate).
**Edge cases handled:** Already-obsolete clauses are excluded from matching. Obsolete status cannot be reversed.
**Confidence:** High.

### RULE-08: Revision submit requires the obsolete list to be acknowledged; the revision status follows the budget outcome
**Category:** Lifecycle
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/BudgetRevision.java:96-123`
**Plain English:**
- Only an IN_PROGRESS or REJECTED revision can be submitted.
- If any nodes became obsolete, the submitter must confirm the obsolete list, and the confirmer is recorded.
- Approval makes the revision EFFECTIVE. Rejection makes it REJECTED, and the edits made so far are kept.
**Specification:**
  Given revision #3 IN_PROGRESS with obsoleteNodeCount=5
  When it is submitted with obsoleteConfirmed=false
  Then REVISION_OBSOLETE_UNCONFIRMED. With true → PENDING and obsoleteConfirmedBy=operator. Later approval → EFFECTIVE
**Parameters:** statuses `IN_PROGRESS`, `PENDING`, `EFFECTIVE`, `REJECTED`.
**Edge cases handled:** The check runs before the budget's own status changes, so there is no half-updated state (`LockProjectBudgetUseCase.java:68-77`). Resubmitting after REJECTED still counts as a revision.
**Confidence:** High.

### RULE-09: A revision cannot be submitted if the archive clauses changed after the revision started
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/delivery/project/LockProjectBudgetUseCase.java:120-147`
**Plain English:** When a revision is submitted, every live (non-obsolete) budget clause must match the archive exactly on term_code set, name, type and amount. Otherwise the PM must start a new revision.
**Specification:**
  Given a revision started when clause A was ¥500,000, and the archive now says ¥520,000
  When the PM submits
  Then BUDGET_REVISION_ARCHIVE_DRIFTED, and nothing changes. ¥500,000 vs ¥500,000.00 counts as equal (compareTo)
**Parameters:** none.
**Edge cases handled:** Obsolete clauses and clauses with no term_code are excluded. This matters because a locked clause amount becomes the revenue quota ceiling.
**Confidence:** High.

### RULE-10: Deliverable submission states, and which states hold the deliverable's single "submission slot"
**Category:** Lifecycle
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/DeliverableSubmissionStatus.java:43-64`
**Plain English:**
- A deliverable can have at most one submission in INTERNAL, EVAL, ACCEPTING, ACCEPTED or CONVERTED.
- A rejected (DRAFT) submission frees the slot, and the next submit creates a new record.
- "Approved" means ACCEPTING, ACCEPTED or CONVERTED.
**Specification:**
  Given deliverable D has submission S1 in `DLV_DRAFT`
  When the PM submits again
  Then a new submission S2 is created in `DLV_INTERNAL`. If S1 were `DLV_ACCEPTING` → DELIVERABLE_SUBMISSION_EXISTS
**Parameters:** `DLV_DRAFT`, `DLV_INTERNAL`, `DLV_EVAL`, `DLV_ACCEPTING`, `DLV_ACCEPTED`, `DLV_CONVERTED`. Phases `INITIAL` and `DEEPEN`.
**Edge cases handled:** Concurrency uses query-then-insert inside a transaction (`DeliverableSubmissionRepositoryImpl.java:104-110`). Every status write also syncs `psa_deliverable.dlv_status`.
**Suspected defect:** `DLV_CONVERTED` has no writer anywhere in the codebase, although it is listed as a state.
**Confidence:** High.

### RULE-11: Submitting a deliverable requires all its task packages to be done, and locks them
**Category:** Lifecycle
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/DeliverableProgress.java:403-423`
**Plain English:** A deliverable can be submitted only if it is not obsolete, has at least one task package, and every package is DONE or LOCKED. Submitting moves every DONE package to LOCKED, which stops further timesheet entry.
**Specification:**
  Given a deliverable with packages T1=DONE and T2=LOCKED
  When the PM submits the deliverable
  Then T1 becomes LOCKED and the submission is created. If T2 were DOING → TASK_NOT_ALL_DONE. With no packages → NO_TASK_PACKAGE
**Parameters:** package statuses `DOING`, `DONE`, `LOCKED`, `REWORK`.
**Edge cases handled:** Submission, task lock and BPM start happen in one transaction (`SubmitDeliverableUseCase.java:47-99`). A failed process start rolls everything back. The lock uses CAS, so a concurrent change → PROGRESS_TASK_CHANGED.
**Suspected defect:** If the submission is rejected (DRAFT), the packages stay LOCKED. Nothing unlocks them. Resubmission still works because LOCKED is accepted.
**Confidence:** High.

### RULE-12: First-level review pass moves deepen-phase submissions to PMO review; initial-phase submissions only record the trace
**Category:** Lifecycle
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/DeliverableSubmission.java:357-368`
**Plain English:** When the first approval node is approved, the reviewer is recorded. A DEEPEN submission moves INTERNAL → EVAL. An INITIAL submission stays INTERNAL.
**Specification:**
  Given a DEEPEN submission in `DLV_INTERNAL`
  When a task-approved message arrives for node `Activity_internal` or `Activity_director` (PM_DELIVERABLE), or for `Activity_tech_review` (PM_STAGE_REVIEW)
  Then status becomes `DLV_EVAL`. When it is not INTERNAL → returns false, and nothing is persisted
**Parameters:** task keys `Activity_internal`, `Activity_director`, `Activity_tech_review`.
**Edge cases handled:** Returning false prevents a late message from overwriting the derived dlv_status (fix #1378 M20). Rejections are ignored here and handled by the process-end handler.
**Confidence:** High.

### RULE-13: Deepen-phase PMO review — a one-time pass ends the review; otherwise a director revision and second PMO round follow
**Category:** Lifecycle
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/DeliverableSubmission.java:465-540`
**Plain English:**
- In EVAL, PMO records an opinion attachment, three 0–100 scores and a "one-time pass" flag. This flag drives the BPM gateway.
- If the flag is false, the director uploads final deliverables, and then PMO gives a second score and a management evaluation.
- The first-round PMO conclusion is frozen once the final deliverables are submitted.
**Specification:**
  Given a DEEPEN submission in `DLV_EVAL`, with no final deliverables yet
  When PMO records firstScore 85, structureScore 90, safetyScore 95, firstPass=false
  Then the second round opens. A director final upload with no FINAL attachment → FINAL_ATTACHMENT_REQUIRED. A second round when firstPass≠false → STATUS_NOT_ALLOWED
**Parameters:** score range 0–100 inclusive (integer).
**Edge cases handled:** INITIAL-phase submissions are rejected on these nodes. The status stays EVAL until the process ends.
**Confidence:** High.

### RULE-14: Deliverable approval outcome — approved → awaiting customer acceptance; rejected or cancelled → DRAFT
**Category:** Lifecycle
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/DeliverableSubmission.java:588-613`
**Plain English:** When PM_DELIVERABLE or PM_STAGE_REVIEW ends:
- Approved → `DLV_ACCEPTING`.
- Rejected or cancelled → `DLV_DRAFT`.
- Only submissions in INTERNAL or EVAL are affected.
**Specification:**
  Given a submission in `DLV_EVAL`
  When the process ends with result=2
  Then status becomes `DLV_ACCEPTING`, and the PMO trace is filled in only if empty. With result=3 or 4 → `DLV_DRAFT`. A submission already in `DLV_ACCEPTED` → no-op
**Parameters:** BPM results 2, 3, 4.
**Edge cases handled:** For a legacy single-level INITIAL flow, the internal-review trace is backfilled from the final event unless the chief review is present.
**Suspected defect:** The `StageReviewProcessResultHandler.java:16-17` comment says that when a meeting fails, the director-node handler already set the submission to DRAFT, so a later "approved" end is a no-op. But `recordDirectorReview` (lines 377-400) never changes status, and the form handler only records `meetingResult`. If the BPM model ends the flow on "meeting not passed" with result=通过 (approved), the submission would move to ACCEPTING. Only the comment supports the claim.
**Confidence:** Medium. SME question: "In PM_STAGE_REVIEW, does 'meeting not passed' end the process as approved, rejected, or neither? Should the submission go back to DRAFT?"

### RULE-15: Customer acceptance can be registered once, only from ACCEPTING, with a sign date and two kinds of proof
**Category:** Lifecycle
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/DeliverableSubmission.java:619-655`
**Plain English:** Registering acceptance needs status ACCEPTING, a sign date no later than today, at least one acceptance document AND one acceptance email. The submission then becomes ACCEPTED, and the sign date can never be changed.
**Specification:**
  Given a submission in `DLV_ACCEPTING`, today 2026-10-14
  When the PM registers sign date 2026-10-10 with 1 document and 1 email
  Then status becomes `DLV_ACCEPTED`. A sign date of 2026-10-15 → ACCEPTANCE_SIGN_DATE_INVALID. A document without an email → ACCEPTANCE_ATTACHMENT_REQUIRED
**Parameters:** none.
**Edge cases handled:** The project-ownership check is in the use case (`RegisterAcceptanceUseCase.java:32-36`).
**Suspected defect:** Revenue confirmation no longer requires accepted deliverables (see RULE-18), so ACCEPTED no longer gates anything downstream.
**Confidence:** High.

### RULE-16: Resubmitting after a return to the initiator resets the review and wipes earlier conclusions
**Category:** Lifecycle
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/DeliverableSubmission.java:168-209`
**Plain English:** When the workflow returns a submission (INTERNAL or EVAL) to its original submitter:
- The submitter can change the title, the deliverable attachments and the phase inputs. The phase itself is fixed.
- All director, PMO and chief conclusions and scores are cleared.
- Status returns to INTERNAL and the process continues on the same instance.
**Specification:**
  Given submission S in `DLV_EVAL` with firstScore 70, parked at the initiator node
  When the original submitter resubmits
  Then firstScore etc. become null, status becomes `DLV_INTERNAL` and submittedTime=now. A different user → DELIVERABLE_SUBMISSION_EXISTS (`SubmitDeliverableUseCase.java:107-111`)
**Parameters:** none.
**Edge cases handled:** Meeting and final attachments are kept as history. The phase input gate is re-applied.
**Confidence:** High.

### RULE-17: Revenue confirmation submit gates — clause exists, clause not obsolete, contract signed back
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/receivables/revenue/SubmitRevenueConfirmationUseCase.java:44-90`
**Plain English:** Revenue confirmation can be started for a clause only if the clause is not obsolete and the project contract status is "合约已回" (contract signed back). Missing contract status counts as not signed back. The confirmation is created and PM_REVENUE starts in one transaction.
**Specification:**
  Given clause C on a project whose contract_status ≠ 合约已回
  When the PM submits a ¥50,000 confirmation
  Then REVENUE_CONTRACT_NOT_BACKED. An obsolete clause → REVENUE_CLAUSE_OBSOLETE
**Parameters:** `FSTConstant.ContractStatusId.合约已回` (dictionary id).
**Edge cases handled:** A resubmit after return requires the same submitter AND a process parked at the initiator node. The quota then excludes the confirmation's own amount.
**Suspected defect:** Seed doc DLV-R18 lists three preconditions: work package POC 100%, internal review passed, and customer confirmation. The code dropped the deliverable gate (#1524), and `allDeliverablesAccepted` / `allDeliverablesInternalApproved` are only for display. Seed doc ACC-R08 requires the project to be 执行中/暂停/已完成 (in progress / paused / completed). No project-status check exists in this path.
**Confidence:** High for the code. SME question: "Should revenue confirmation be refused for projects in 规划中/已关闭/终止 (planning / closed / terminated)?"

### RULE-18: A revenue confirmation needs a positive amount, a full evidence set and quota headroom; the revenue period comes from the sign date
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/receivables/revenue/RevenueConfirmation.java:64-125`
**Plain English:**
- The amount must be > 0.
- The evidence set (sign date + acceptance document + email) must be complete.
- The amount must fit within the clause quota: remaining = max(0, clause amount − (confirmed + pending)).
- Revenue period = the month of the customer sign date. The client-supplied period is ignored.
**Specification:**
  Given clause amount ¥100,000, confirmed ¥60,000, pending ¥30,000
  When a confirmation for ¥10,000 is submitted with sign date 2026-09-28
  Then the status is `REV_PENDING` and revenuePeriod is "2026-09". ¥10,000.01 → QUOTA_EXCEEDED ("剩余 10000")
**Parameters:** statuses `REV_PENDING`, `REV_CONFIRMED`, `REV_REJECTED`. PENDING and CONFIRMED use up quota, REJECTED frees it (`ClauseRevenueQuota.java:33-49`). A null clause amount counts as 0.
**Edge cases handled:** A resubmit keeps the same status and instance and overwrites the amount, evidence and period.
**Confidence:** High.

### RULE-19: Approved revenue writes an append-only revenue fact, after a second quota check
**Category:** Lifecycle
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/receivables/revenue/DecideRevenueConfirmationUseCase.java:36-68`
**Plain English:** When PM_REVENUE ends:
- Approved → the quota is checked again, excluding this confirmation. If it still fits, the status becomes CONFIRMED and one row is inserted into `psa_revenue_fact` in the same transaction. If it no longer fits, the system rejects it automatically.
- Rejected or cancelled → REJECTED, which frees the quota.
**Specification:**
  Given a ¥10,000 confirmation in PENDING, and the clause amount was cut, so the remaining quota excluding this confirmation is ¥5,000
  When the BPM approved message arrives
  Then the status is `REV_REJECTED` with note "审批通过时累计确认金额已超条款金额，系统自动驳回…" ("cumulative confirmed amount exceeds the clause amount at approval time; rejected automatically by the system…"), and no fact row is written
**Parameters:** BPM results 2 (approved), 3/4 (rejected/cancelled) (`RevenueProcessResultHandler.java:37-43`).
**Edge cases handled:** A confirmation that is not PENDING is a no-op, so redelivered messages cannot create a second fact. A missing confirmation → warning only.
**Suspected defect:** When the auto-reject happens, BPM shows the instance as approved while PSA shows it rejected. No notification or BPM correction is visible in the code.
**Confidence:** High.

### RULE-20: Task package execution states — DOING/REWORK → DONE, DONE → DOING (reopen), LOCKED → REWORK
**Category:** Lifecycle
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/DeliverableProgress.java:47-84`
**Plain English:**
- A package can be completed only from DOING or REWORK, and not while an ETC approval is pending.
- Only DONE can be reopened.
- Only LOCKED (locked by a deliverable submission) can go to rework.
- Obsolete packages and deliverables reject every transition.
**Specification:**
  Given task T in DOING with etcApprovalStatus=PENDING
  When the PM marks it done
  Then TASK_DONE_LOCKED_BY_ETC_APPROVAL. With no pending ETC → DONE. Then ETC=0 and POC=100
**Parameters:** `DOING`, `DONE`, `LOCKED`, `REWORK`. New packages start as DOING with actual 0 and ETC status NONE.
**Edge cases handled:** The self-complete entry point lets only the assignee complete their own package (`OperateProgressTaskUseCase.java:63-77`). DONE and LOCKED packages cannot have hours, ETC or other fields edited.
**Suspected defect:** `FSTConstant.TaskStatus` (0 进行中 / in progress, 1 已保存 / saved, 2 已完成 / completed, 3 已逾期 / overdue, with the comment "TODO 其他两个状态需要完善" / "the other two states need completing") is a separate legacy integer vocabulary. It is not used by this state machine, which uses strings.
**Confidence:** High.

### RULE-21: An ETC increase needs PM_ETC approval only if it pushes forecast labour cost over budget labour cost
**Category:** Policy
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/DeliverableProgress.java:110-136`
**Plain English:**
- Lowering or keeping ETC takes effect immediately, with status APPROVED.
- A raise takes effect immediately if the project's forecast labour cost after the change is still ≤ the budget labour cost.
- Otherwise a reason is required, the package's ETC is frozen (PENDING), and PM_ETC starts while the old ETC stays in effect.
**Specification:**
  Given budget labour ¥100,000, forecast (EAC) ¥95,000, package rate ¥200/h, current effective ETC 10 h
  When the PM sets ETC to 30 h
  Then the new EAC = 95,000 + 20 × 200 = ¥99,000 ≤ 100,000 → effective immediately. Setting 40 h → ¥101,000 > budget → a reason is required, then PENDING
**Parameters:** delta rounded HALF_UP to 2 decimals. Overrun means strictly greater. A missing headroom (tree not found) or the self-created-package path means any raise needs approval (`LaborCostHeadroom.java:64-75`).
**Edge cases handled:** ETC < 0 → rejected. Already PENDING → TASK_ETC_APPROVAL_PENDING. A self-created package always gets 0 planned hours and starts PM_ETC automatically with reason "组员自发建包申请工时" ("hours requested for a self-created package"). Excel import changes ETC without approval, in either direction (lines 197-229).
**Suspected defect:** Seed DLV-R08 says "ETC 为空时 EAC = AC → 视同完工" (empty ETC means EAC = actual, treated as complete). The code treats empty ETC as max(0, planned − actual) (`TaskPackage.java:276-286`).
**Confidence:** High.

### RULE-22: ETC approval outcome — approved promotes the pending value; rejected or cancelled unfreezes and keeps the old value
**Category:** Lifecycle
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/delivery/project/DecideEtcApprovalUseCase.java:46-96`
**Plain English:**
- Approved → etcHours = pending value and status APPROVED.
- Rejected or cancelled → status REJECTED and the original ETC stays.
- Only a PENDING package matching the bound instance is updated, using a CAS on PENDING.
- A message that arrives before the start transaction commits is retried.
**Specification:**
  Given task T PENDING with pending 40 h, current ETC 10 h, etcProcessId=`E1`
  When PM_ETC ends with result=2 for `E1`
  Then etcHours=40, status `APPROVED`, pending and process id cleared. With result=3 or 4 → `REJECTED`, ETC stays 10
**Parameters:** ETC statuses `NONE`, `PENDING`, `APPROVED`, `REJECTED`.
**Edge cases handled:** T not PENDING and instance already recorded → ignored. T not PENDING and instance not seen yet → exception so the message is redelivered. Instance not bound yet → checked against the BPM mapping. The obsolete gate is deliberately skipped, so approvals started before the node went obsolete can still finish.
**Confidence:** High.

### RULE-23: Reconciliation job replays lost ETC outcomes and releases orphaned freezes
**Category:** Policy
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/trigger/task/EtcApprovalReconJob.java:61-155`
**Plain English:** A scheduled job finds PENDING packages and replays their outcome through RULE-22:
- Bound instance, last updated > 2 min ago, and the BPM instance has finished → replay its result.
- Unbound for > 10 min:
  - no BPM instance → treated as rejected (unfrozen);
  - BPM instance finished → replay its result;
  - instance still running → left alone.
**Specification:**
  Given T PENDING, unbound, updated 15 min ago, and no BPM instance
  When the job runs
  Then T is replayed as 不通过 (rejected), so the status becomes REJECTED and the ETC is unchanged
**Parameters:** `GRACE_MINUTES`=2, `ORPHAN_GRACE_MINUTES`=10, batch 500, XXL-JOB handler `etcApprovalReconJob` (every 5 min suggested). A null updated_time counts as overdue.
**Edge cases handled:** Missing instances or results are skipped and logged.
**Confidence:** High.

### RULE-24: Execution-phase structure changes freeze during budget approval; planned hours freeze after lock; a submission locks the package set
**Category:** Lifecycle
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/delivery/project/OperateProgressTaskUseCase.java:244-295`
**Plain English:** On the progress page:
- Adding, editing or deleting packages is refused while the budget is PENDING.
- After lock, planned hours cannot change, and new packages must have 0 planned hours.
- Packages cannot be added or deleted while the deliverable has a slot-holding submission.
- Only the project's PM may add or edit packages.
**Specification:**
  Given a locked budget and task T with 40 planned hours
  When the PM edits T to 50 h
  Then PROGRESS_PLANNED_HOURS_LOCKED. Creating a new package with 8 h → the same error. During PENDING, any create or update → BUDGET_APPROVAL_PENDING
**Parameters:** none.
**Edge cases handled:**
- The assignee cannot change once hours are invested, including timesheets still awaiting approval (`DeliverableProgress.java:304-310`).
- A package with invested hours cannot be deleted.
- A deliverable can be deleted only if it is empty, has no submission history, and did not come from the baseline (lines 456-479).
**Confidence:** High.

### RULE-25: How rework is handled depends on the submission state
**Category:** Lifecycle
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/delivery/project/OperateProgressTaskUseCase.java:84-242`
**Plain English:**
- No submission, or a rejected one → rework in place (LOCKED → REWORK).
- Submission in approval → refused.
- Submission already approved (ACCEPTING, ACCEPTED or CONVERTED) → in-place rework is refused. A new rework deliverable is created under the same clause instead, copying the selected packages with 0 planned hours. A new round needs the previous round's rework to be approved.
**Specification:**
  Given deliverable D with submission in `DLV_ACCEPTED`, and no earlier rework deliverables
  When the PM reworks tasks T1 and T2 with reason "客户变更" (customer change)
  Then a rework deliverable is created with copies of T1 and T2 (DOING, 0 planned, actual 0, same assignee and role). D and its packages are unchanged. If an earlier rework deliverable is not approved yet → DELIVERABLE_REWORK_PREVIOUS_UNFINISHED
**Parameters:** reason comes from the dictionary `rework_reason` (required).
**Edge cases handled:** PM only. Budget PENDING → refused. Selected ids outside D or obsolete → REWORK_NO_TASK_SELECTED. Hours for the copies can only come through PM_ETC, which will require approval because the forecast already exceeds budget.
**Confidence:** High.

---
**Coverage:** I read fully: ProjectBudget, BudgetRevision, DeliverableSubmission(+Status), DeliverableProgress, TaskPackage, LaborCostHeadroom, RevenueConfirmation(+Status, Quota, ClauseRevenueContext), the Submit/Decide use cases for revenue and deliverables, Lock/Decide/Apply/StartRevision budget use cases, OperateProgressTaskUseCase, RecordStageReview, RegisterAcceptance, all budget/deliverable/progress/revenue MQ handlers, EtcApprovalReconJob, and the FSTConstant ProjectStatus/TaskStatus parts.
I only skimmed, by grep: ProjectBudgetRepositoryImpl (lock/unlock/markApprovalPending), DeliverableSubmissionRepositoryImpl, ClauseRevenueQueryImpl, ExportProgressWorkbookUseCase, and the seed doc 09 rule list.
I did not read: the ETC/Deliverable/Revenue starter adapters, the query or read-model impls (ProgressTree, ProjectQuery), ImportProgressWorkbook, GetBudgetRevision, the receivables billing package, or the `docs/domain/03_项目交付域_delivery/` subfolders. Things I may have missed:
- How project status (FSTConstant.ProjectStatus: 规划中/执行中/已完成/已关闭/暂停/终止) changes. No transition is coded in these paths. Its only consumer here is the export/import filter (`ExportProgressWorkbookUseCase.java:33-35`, 规划中 and 执行中 only), so the lifecycle is probably in legacy `service/` code.
- What the BPM model actually does for "meeting not passed" in PM_STAGE_REVIEW (RULE-14).