### RULE-01: Timesheet header approval-status state machine (allowed transitions)
**Category:** Lifecycle
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourApproveStatusService.java:163-183`
**Plain English:** A timesheet's approval status (`psa_time_sheet.approve_status`) can only move along a fixed set of transitions, and an Approved timesheet can never move again.
**Specification:**
  Given a timesheet with approve_status = 3 (Approved / 通过)
  When any caller asks to transition it to 4 (Cancelled / 取消) or 0 (Pending / 待发起)
  Then the request is rejected with error 400 "状态流转不合法：通过 → 取消"
  (Allowed: none → 0; 0 → 1; 1 → 3, 2, 4 or 0; 2 → 0; 4 → 0; 3 → nothing)
**Parameters:** ApproveStatus: 待发起=0, 审批中=1, 驳回=2, 通过=3, 取消=4 (CommonConstants.ApproveStatus)
**Edge cases handled:** A null current status may only go to 0. A request for the same status as the current one returns early as a no-op (lines 74-76), except for approver row actions. The 1 → 0 edge exists only for "returned to initiator" resubmits, and the entry guard enforces that, not the state machine.
**Suspected defect:** The state-machine doc (05-工时域-状态机.md §5.3, §3.8) lists a `通过 → 取消` edge and an "admin revoke to DRAFT". The global rule list COST-R09 says approved sheets can be reopened back to 审批中. In code, Approved has no outgoing edge at all. Also, the 1 → 0 edge is open to any caller of `transition()`, so the only protection against resetting an in-flight sheet is the caller-side guard.
**Confidence:** High. The whitelist is explicit.

### RULE-02: Secondary header "status" field follows the approval status
**Category:** Lifecycle
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourApproveStatusService.java:185-190`
**Plain English:** Every time the header approval status changes, the header `status` is set to Ended (2) if the new status is Approved, and to In-progress (1) otherwise.
**Specification:**
  Given a timesheet with approve_status 1, status 1
  When BPM finishes with Approved
  Then approve_status = 3 and status = 2 (结束); on Rejected, Cancelled or reset to Pending, status = 1 (进行中)
**Parameters:** ManHourStatus: 待发起=0, 进行中=1, 结束=2 (CommonConstants.java:294-299)
**Edge cases handled:** status = 0 is only written when the sheet is first created (ManHourServiceImpl.java:1601). After a resubmit resets the sheet to approve_status 0, status becomes 1, not 0.
**Suspected defect:** The doc (§2.3, §3.7) says `status = 2` marks POSTED and is written by the posting engine. In code it is written when the sheet is approved, so APPROVED and POSTED cannot be told apart by this field. A Pending (0) sheet carries status 0 or 1 depending on its history.
**Confidence:** High for the code. SME question: "Is psa_time_sheet.status = 2 meant to mean 'approved' or 'posted'? Downstream readers of status = 2 depend on the answer."

### RULE-03: New timesheet starts in Pending with a TS-prefixed number and a locked company code
**Category:** Lifecycle
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourServiceImpl.java:1595-1621`
**Plain English:** A first-time timesheet is created with approve_status 0, status 0, data origin PSA, and the employee's accounting company taken from their transfer record for the work date. If that company code is missing, the timesheet is not created.
**Specification:**
  Given employee 1001 with a transfer record valid on 2026-10-13 whose company_code is blank
  When the employee saves or submits a timesheet for 2026-10-13
  Then the whole request fails with error 503 ("…异动档案缺少核算组织编码(company_code)…")
**Parameters:** code prefix "TS" (line 827); WorkHoursDataOrigin.PSA = 0; dept/post copied from the user cache
**Edge cases handled:** The company code is resolved as of the work date, not as of today.
**Suspected defect:** The insert already sets approve_status 0. The follow-up `transition(… 待发起, "创建工时单")` at line 830 therefore hits the same-status no-op (ManHourApproveStatusService.java:74-76), so the "creation" audit row in `psa_time_sheet_approve_status_revision` is never written.
**Confidence:** High.

### RULE-04: One timesheet per employee per calendar day
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourServiceImpl.java:1235-1246`
**Plain English:** You cannot create a second timesheet for a day that already has one, in any status. Changes must go through "re-initiate" on the existing sheet.
**Specification:**
  Given employee 1001 already has a non-deleted timesheet for 2026-10-13 (even one in Cancelled, 4)
  When the employee creates a new timesheet (no originId) for 2026-10-13
  Then the request is rejected with 503 "2026-10-13 已存在工时单，请勿重复填报；如需修改请在「我的流程」中重新发起"
**Parameters:** Day window [00:00, next day 00:00). It mirrors the DB filtered unique index `idx_psa_time_sheet_unique_valid WHERE deleted=0`.
**Edge cases handled:** Resubmits (originId set) skip this check. A Redis key `psa:man-hour:submitted:{person}:{date}` also blocks two concurrent submits for the same person and date (lines 792-797, 1227-1230).
**Confidence:** High.

### RULE-05: "Save" keeps a draft; "Send" queues the approval process
**Category:** Lifecycle
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourServiceImpl.java:858-923`
**Plain English:** Only a Send (opType 1) runs the submit checks and writes an outbox record that later starts the BPM approval. A Save (opType 0) only stores the data in Pending.
**Specification:**
  Given employee 1001 has 480 minutes of approved leave on 2026-10-13
  When the employee sends a timesheet for 2026-10-13
  Then the request is rejected with 503 "2026-10-13 为整天请假，无需填报工时" and nothing is persisted
  Given no full-day leave and every row has an approver
  When the employee sends
  Then an outbox row is written with action CREATE_OA (or CONTINUE_AFTER_RETURN for a returned resubmit), the header stays at 0, and the process is started asynchronously after commit
**Parameters:** OpType: 保存=0, 发送=1. Full-day leave threshold = 450 minutes (7.5 h). Process business key = 工时审批 / "TIMESHEET".
**Edge cases handled:** Send also requires every row to have an approver (`assertApproverResolvedForSubmit`, lines 1400-1418) and every type-dependent required field to be filled. Save skips all of these. Any failure rolls back the whole transaction. The flow title date is the work date, not the send date.
**Confidence:** High.

### RULE-06: Pending becomes In-approval only after BPM has actually started or resumed the process
**Category:** Lifecycle
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/bpm/ProcessToBeSentServiceImpl.java:163-189`
**Plain English:** The outbox sender calls BPM (create a new instance, or continue the returned one). Only when BPM returns success does it move the timesheet from Pending (0) to In-approval (1), recording operator type SYSTEM.
**Specification:**
  Given timesheet 555 in approve_status 0 with a CREATE_OA outbox row
  When the sender calls BPM createOA and gets "success"
  Then timesheet 555 moves to approve_status 1 and status 1 with remark "发起工时审批流程"
  When BPM returns anything other than success
  Then the outbox row is marked failed (retried up to MAX_RETRY) and the timesheet stays at 0
**Parameters:** Actions ACTION_CREATE_OA and ACTION_CONTINUE_AFTER_RETURN; MAX_RETRY (value not read)
**Edge cases handled:** If an instant self-approval races ahead of this step, the resulting 0 → 3 is illegal. A compensation replay later retries it as 1 → 3 (comment #460; the replay code is not in scope).
**Confidence:** High for the transition. Medium for the race handling, because the compensation code was not read.

### RULE-07: How row statuses follow a header change
**Category:** Lifecycle
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourApproveStatusService.java:82-112`
**Plain English:** When the header moves to In-approval, only rows that are not already decided become In-approval. When the header moves to Pending or Cancelled, every row is overwritten, including approved rows. When BPM ends with Approved or Rejected, rows still left In-approval are swept to that final status and stamped with an approval time.
**Specification:**
  Given a timesheet with rows A = 3 (kept approved row) and B = 0
  When the header moves 0 → 1
  Then A stays 3 and B becomes 1
  Given rows A = 3 and B = 1, header 1
  When the process is cancelled (target 4)
  Then A and B both become 4
  Given rows A = 3 and B = 1 (approver was null), header 1
  When BPM ends with Rejected
  Then B becomes 2 with approved_time = now (or the supplied backfill time), and the header becomes 2
**Parameters:** Statuses kept on →1: {3, 2, 4} (plus null rows are updated)
**Edge cases handled:** Rows with a null approver can never be stuck in 1 after BPM ends (refs #100).
**Confidence:** High.

### RULE-08: Row approver is assigned at submit by work-category type
**Category:** Lifecycle
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourServiceImpl.java:1702-1763`
**Plain English:** Each row is routed to one approver. Overhead (OH) rows go to the submitter's direct leader. Project rows go to the project manager. Pre-sales, opportunity and mixed rows go to the leader of the responsible department.
**Specification:**
  Given a project-task row whose task has project_id P and P.manager_id = 2002
  When the timesheet is built at save or send
  Then row.approval = 2002
  Given the task has no project, task.manager_id = 2003
  Then approval = 2003. If that is missing, the row's own managerId is used, then project.manager_id looked up by project code. If all fail, approval = null.
**Parameters:** ManHourType OH / 2, 项目 / 0, 售前 / 1, 混合. The placeholder project code "--" means no project.
**Edge cases handled:** A null approver is allowed on Save. On Send it is rejected with 503 by `assertApproverResolvedForSubmit`.
**Confidence:** High. SME question: "Should BPM routing be authoritative instead? The BPM node rowIds (RULE-12) can assign rows to approvers other than `approval`."

### RULE-09: Cost and bill rates are locked at submit, not at approval
**Category:** Policy
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourServiceImpl.java:1670-1696`
**Plain English:** When a row is created, the system finds and stores the standard-cost and billing rate scheme, detail, rate, unit and billable flag. It also stores the company code and cost center (the employee's department). Later master-data changes do not change these values.
**Specification:**
  Given employee 1001 on 2026-10-13 with job level, salary grade and city category matching a standard-cost detail of rate 120.500/h and a bill detail of 200.000/h
  When the row is created
  Then standard_cost_rate = 120.500 and bill_rate = 200.000 are stored. The amounts are left empty until approval.
**Parameters:** Match key = jobLevelCode + salaryGradeNo + cityCategoryCode (TimeSheetRateServiceImpl.java:84-90)
**Edge cases handled:** A missing employee archive field, no matching rate detail, more than one match, or a period that does not cover the date all throw 503 and block the submit.
**Confidence:** High.

### RULE-10: Approval writes the cost and bill amounts (approved hours × locked rate)
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourApproveStatusService.java:241-271`
**Plain English:** When rows become Approved, standard_cost_amount and bill_rate_amount are set to rate × approved hours, rounded half-up to 3 decimals, using the rates locked at submit. Missing approval times are filled in.
**Specification:**
  Given an approved row with approval_hours 7.5, standard_cost_rate 120.500, bill_rate 200.000, approved_time null
  When the approver approves it, or the header reaches 3
  Then standard_cost_amount = 903.750, bill_rate_amount = 1500.000, and approved_time = now (or the backfill approvedTime)
**Parameters:** Scale 3, RoundingMode.HALF_UP
**Edge cases handled:** Rows with a null work date or null approval hours are skipped. A null rate gives a null amount. An existing approved_time is not overwritten. Rerunning gives the same result.
**Confidence:** High.

### RULE-11: An approver's personal decision only touches that approver's own rows
**Category:** Lifecycle
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourApproveStatusService.java:56-72`
**Plain English:** A non-BPM Approve or Reject action sets only the rows whose `approval` is the acting user, writes any row opinions, and calculates amounts for that user's approved rows. The header status is not changed.
**Specification:**
  Given a header at 1 with rows R1 (approval 2002) and R2 (approval 3003)
  When user 2002 approves
  Then R1 = 3 with approved_time = now and amounts calculated, R2 is unchanged, and the header stays at 1
**Parameters:** Operator type is anything other than "BPM". The operator comes from the request or from the login user.
**Edge cases handled:** The header must be in a state where → target is legal (i.e. header = 1), otherwise 400. A missing operator gives 400. A Reject in this path also stamps approved_time (ManHourItemMapper.java:112-119).
**Confidence:** High.

### RULE-12: Row decisions from BPM task-completion messages
**Category:** Lifecycle
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/mq/consumer/approval/timesheet/TimesheetApprovalResultHandler.java:54-139`
**Plain English:** Each BPM task result updates rows only. A per-row task decides its own row. A node-level approval approves all rows the node covers. A node-level rejection with JSON opinions rejects only the rows that have a non-blank opinion and approves the rest. A plain-text rejection rejects the whole node. The header is not changed here.
**Specification:**
  Given a node covering rows {11, 12, 13}
  When the approver rejects with reason `[{"id":11,"remarks":"wrong project"},{"id":12,"remarks":""}]`
  Then row 11 = 2 with opinion "wrong project", and rows 12 and 13 = 3
  When the approver rejects with reason "redo"
  Then rows 11, 12 and 13 = 2, each with opinion "redo"
**Parameters:** BPM result APPROVE → 3, REJECT → 2; other results are ignored
**Edge cases handled:** Opinion rows outside the node's rows are ignored. Invalid row ids are skipped. When BPM "returns to initiator" without ending the process, the header stays at 1.
**Suspected defect:** The legacy fallback path (no rowIds, lines 122-135) rejects every id in the opinion JSON, even ids with a blank opinion and ids belonging to other approvers. That contradicts the "blank means agree" rule of the node path.
**Confidence:** High.

### RULE-13: End-of-process result decides the header outcome
**Category:** Lifecycle
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/mq/consumer/approval/timesheet/TimesheetProcessResultHandler.java:46-92`
**Plain English:** When the BPM process ends, the header is set from the BPM result. Approved also refreshes task progress. Rejected and Cancelled also write a revision snapshot.
**Specification:**
  Given timesheet 555 at 1
  When the process-end message has result 2 (BPM 通过)
  Then the header becomes 3 (通过), no revision is written, and task progress is refreshed for the distinct task ids
  When the result is 3 (BPM 不通过), the header becomes 2 (驳回) and a revision is written
  When the result is 4 (已取消, user withdrew), the header becomes 4 (取消) and a revision is written
**Parameters:** BPM codes 通过=2, 不通过=3, 已取消=4 map to fst 3 / 2 / 4. Note that the numbers are swapped between the two code sets.
**Edge cases handled:** Other results are ignored. Exceptions are re-thrown so RocketMQ redelivers the message. The task-progress refresh and the origin deletion are best-effort and never roll back the status change.
**Confidence:** High.

### RULE-14: Applying an end result is idempotent and attributed to the submitter
**Category:** Policy
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourBpmApplyService.java:54-113`
**Plain English:** If the header is already in the target status, nothing is written (no duplicate revision). Otherwise rows, header and revision are written in one transaction. The operator is the timesheet creator when the message names no approver.
**Specification:**
  Given timesheet 555 already at 3
  When a duplicate "approved" message arrives
  Then apply returns false, nothing is written, and no side effects run
**Parameters:** operatorType = "BPM"
**Edge cases handled:** A missing timesheet gives 404. A creator that is not numeric gives operator null. The before-snapshot is taken only when a revision is required.
**Confidence:** High.

### RULE-15: Withdrawing means Cancelled (4), not back to draft, and has no "untouched" guard
**Category:** Lifecycle
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourApproveStatusService.java:172-180`
**Plain English:** A user withdrawal arrives as a BPM cancel. The sheet goes 1 → 4, and every row (including already-approved rows) becomes 4. From 4 the only way forward is re-initiation (4 → 0) or deletion.
**Specification:**
  Given header 1, row A already approved (3) by approver X, row B at 1
  When the employee withdraws
  Then header = 4 and A = B = 4. X's approval is lost and must be given again after resubmit.
**Parameters:** Edges 1 → 4 and 4 → 0
**Edge cases handled:** Cancelled sheets appear in the employee's "editable" to-do list (ManHourServiceImpl.java:2830-2844). Rejected sheets do not, because BPM returns them to the initiator as a real task.
**Suspected defect:** The doc (§1, §3.6) says a withdrawal returns the sheet to DRAFT (0) and is allowed only when no approver has acted yet. The code goes to 4 and has no such guard. The doc's own §7 admits the guard is missing.
**Confidence:** Medium. The withdraw trigger lives in BPM and was not read. SME question: "May an employee withdraw after some rows are already approved, and should those approvals survive?"

### RULE-16: Who may re-initiate (edit and resubmit) a timesheet
**Category:** Lifecycle
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourServiceImpl.java:800-831`
**Plain English:** Only the owner can re-initiate, and only if the sheet is Rejected (2), Cancelled (4), or currently returned to the initiator in BPM. Re-initiation keeps the same header id, deletes and re-inserts the rows, and resets the header to Pending (0).
**Specification:**
  Given timesheet 555 owned by 1001 at status 1 and not returned to the initiator
  When 1001 resubmits it with originId 555
  Then the request is rejected with 400 "仅审批驳回、取消或退回发起人的工时允许重新发起"
  Given 555 is at 2
  When 1001 resubmits
  Then the rows of 555 are deleted, header 555 → 0 with remark "重新发起工时单，重置为待提交", and new rows are inserted
**Parameters:** —
**Edge cases handled:** A non-owner gets 403 and a missing origin gets 404. The same guard applies to the edit-detail screen (lines 2855-2863) and to process-edit submit (lines 352-360).
**Confidence:** High.

### RULE-17: A returned sheet resumes the same BPM instance; a cancelled or ended one starts a new instance
**Category:** Lifecycle
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourServiceImpl.java:2815-2827`
**Plain English:** "Returned to initiator" is decided by asking BPM about the latest instance. If the instance is still alive at the initiator node, the resubmit continues that instance (CONTINUE_AFTER_RETURN). Otherwise a new instance is created (CREATE_OA, line 908-910).
**Specification:**
  Given timesheet 555's latest instance is alive and parked at the initiator after a return
  When the employee resubmits
  Then the outbox action is CONTINUE_AFTER_RETURN and the same instance resumes
  Given BPM is unreachable
  Then the sheet is treated as not returned: editing is refused unless the header is 2 or 4, and any resubmit creates a new instance
**Parameters:** —
**Edge cases handled:** No instance id gives false (fail-safe).
**Confidence:** High.

### RULE-18: Unchanged, already-approved rows keep their approval on resubmit
**Category:** Lifecycle
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourServiceImpl.java:953-1003`
**Plain English:** On resubmit, a row that was Approved and is unchanged keeps its old record (status, opinion, rates, amounts, approver) and is left out of the new approval route. Unchanged means same id, type, work-package type, task, work package, date, hours and trimmed remarks.
**Specification:**
  Given the prior round had row 11 approved at 4.0 h with remarks "dev" and row 12 rejected
  When the employee resubmits row 11 unchanged and row 12 edited
  Then row 11 is re-inserted as its old approved record and gets no approval task. Row 12 goes through approval.
  When every submitted row is a kept approved row
  Then the request is rejected with 400 "所有明细行均已审批通过且未修改，无需重新提交" (lines 885-891)
**Parameters:** Hours are compared numerically (0.5 = 0.50). The baseline is approval hours, falling back to declared hours.
**Edge cases handled:** A changed date makes every row re-enter approval. A missing row id means the row is re-approved ("better to over-approve than miss").
**Confidence:** High.

### RULE-19: Revision snapshots and their event types
**Category:** Lifecycle
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourRevisionService.java:118-175`
**Plain English:** A revision (before/after row snapshot plus per-row detail) is written on BPM reject, BPM cancel and every resubmit. Its event type comes from the combined row status before and after.
**Specification:**
  Given prior rows {3, 2} (combined = 2 Rejected) and new rows all 0
  When the employee resubmits
  Then a revision with event_type 3 (RESUBMIT_AFTER_REJECT) is written
  (combined: one distinct status → that status; mixed with 2 → 2; mixed with 4 → 4; otherwise → 1. Event: new 2 → 1, new 4 → 2, new 0 or 1 with old 2 → 3, with old 4 → 4)
**Parameters:** Event types: 1 PROCESS_REJECT, 2 PROCESS_CANCEL, 3 RESUBMIT_AFTER_REJECT, 4 RESUBMIT_AFTER_CANCEL, 5 ADMIN_INVALIDATE (unused), 6 DATA_REPAIR (unused)
**Edge cases handled:** When the old status was 1, the latest process id is stored as "old" and the new process id is null.
**Suspected defect:** The doc (§5.2) lists snapshot events SUBMITTED, APPROVED, WITHDRAWN and POSTED. The code writes no revision on first submit or on approval (createRevision=false for 通过), and it records event types as numbers, not those names. A resubmit where every row is kept as approved could produce a null event_type.
**Confidence:** High.

### RULE-20: Every header status change is written to an append-only audit log
**Category:** Policy
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourApproveStatusService.java:119-132`
**Plain English:** Each header transition writes from, to, operator, operator type (USER / SYSTEM / BPM) and a remark to `psa_time_sheet_approve_status_revision`. For backfilled historical approvals, the log time is the real approval time.
**Specification:**
  Given a backfill request with approvedTime 2026-03-01 18:00 for a sheet at 1
  When it transitions to 3
  Then the audit row has from 1, to 3, operatorType BPM, and create_time = 2026-03-01 18:00
**Parameters:** Default remark "<from> → <to>"
**Edge cases handled:** Approver row actions and same-status no-ops are not logged. A deletion is logged with operatorType DELETE (lines 151-161).
**Confidence:** High.

### RULE-21: Approval side effects: refresh task progress and retire the replaced original sheet
**Category:** Lifecycle
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/mq/consumer/approval/timesheet/TimesheetProcessResultHandler.java:72-91`
**Plain English:** After a sheet is approved, progress is recalculated for every task its rows touch. If the sheet replaced an earlier sheet (originId > 0 and different from its own id), the earlier sheet is soft-deleted and its posting-ledger (`psa_time_sheet_end`) rows are removed.
**Specification:**
  Given approved sheet 777 with originId 555 and rows on tasks {T1, T2}
  When the approval is applied
  Then progress for T1 and T2 is refreshed, sheet 555 is marked deleted = 1, and its end rows are deleted. A failure in either step is logged and does not undo the approval.
**Parameters:** —
**Edge cases handled:** Runs only when apply really changed the status.
**Suspected defect:** No code in fst sets `originId` on a ManHourDO (a resubmit reuses the same id), so the origin deletion looks dead for new data. Also, removing `psa_time_sheet_end` rows by the work-row ids assumes end.id == works.id.
**Confidence:** Medium. SME question: "Does any path (A8 sync or legacy) still create replacement sheets with originId set?"

### RULE-22: Only a withdrawn sheet on a non-work day or a full-leave day may be deleted
**Category:** Lifecycle
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourServiceImpl.java:2940-2985`
**Plain English:** The owner may permanently delete their own Cancelled (4) sheet only if its date is not a work day or they had at least 7.5 h of leave that day. The deletion is audited first, then revisions, rows, end rows and the header are physically deleted.
**Specification:**
  Given a cancelled sheet for workday 2026-10-13 with 120 minutes of leave
  When the owner deletes it
  Then the request is rejected with 400 "工作日且无整天请假的工时，撤回后不允许删除"
  Given the same sheet with 450 minutes of leave
  Then the audit row is written ("用户删除已撤回工时单", title "工时填报_<nick>_2026-10-13") and all data is physically removed
**Parameters:** Full-day leave ≥ 450 min. Calendar workday = 1, rest day = 2. A missing calendar entry counts as a workday.
**Edge cases handled:** A non-owner gets 403 and a status other than 4 gets 400. The same check drives the "deletable" flag in the to-do list.
**Confidence:** High.

### RULE-23: A row may be posted only if it is approved and has complete cost data
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourPostingCheckServiceImpl.java:117-152`
**Plain English:** A row passes the posting check only if all of these hold:
1. Row approve_status is 3.
2. All nine rate fields are present.
3. The type-dependent required fields are present.
4. company_code, cost_center_id and cost_org_id are present.
5. For project or project-task rows, the term number belongs to that project's contract terms.
**Specification:**
  Given row 11 with approve_status 3 and term_number "QU-01" whose project has terms {QU-02}
  When the posting check runs
  Then it fails with "合同条款编码不在该项目档案中…"
  Given row 12 with approve_status 1
  Then it fails with "未审批通过…需=通过(3)", and the auto-fix refuses to touch it
**Parameters:** STATUS_PASSED = 3. The term comparison trims and ignores case.
**Edge cases handled:** If the project archive cannot be loaded, every term check fails (fail-closed). A project with no terms also fails.
**Suspected defect:** The check uses the row status, not the header status. A row approved by its approver while the header is still 1, or later Rejected or Cancelled at header level, is overwritten by the cascade (RULE-07) only on cancel. A row approved within a sheet that ends Rejected keeps status 3 and would pass this check.
**Confidence:** Medium. SME question: "Must posting require header approve_status = 3, or is row-level approval enough?"

### RULE-24: Logged hours freeze task staffing from the moment they are filed
**Category:** Policy
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/dal/mysql/manhour/ManHourItemMapper.java:50-84`
**Plain English:** A task package counts as "has logged hours", which locks its staffing and blocks its deletion, as soon as any non-deleted timesheet row on it is in Pending, In-approval or Approved. Approval is not needed. Rejected or Cancelled rows do not count.
**Specification:**
  Given task package T1 with one row in status 1 and T2 with only a row in status 4
  When staffing changes are attempted
  Then T1 is locked and T2 is not
**Parameters:** Locking statuses {0, 1, 3}. Batches of 1000 ids (SQL Server parameter limit).
**Edge cases handled:** For legacy rows with a null task_id, work_package_id is used instead.
**Confidence:** High.

### RULE-25: Today's timesheet opens at 09:00 Beijing time
**Category:** Policy
**Priority:** P2
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourServiceImpl.java:1184-1195`
**Plain English:** A timesheet for today cannot be saved or submitted before 09:00 Asia/Shanghai. Any date in a closed accounting period cannot be saved or submitted at all.
**Specification:**
  Given now = 2026-10-14 08:30 Asia/Shanghai
  When an employee files for 2026-10-14
  Then the request is rejected with 503 "2026-10-14 日的工时还未开放填报！"
**Parameters:** Opening time 09:00. Zone Asia/Shanghai. Accounting period must be open or reopened (`assertEntryAllowed`).
**Edge cases handled:** Past dates are not blocked by the 09:00 rule.
**Suspected defect:** The global rule list COST-R02 says a timesheet must be filed by 09:00 the next day (a deadline). The code enforces an opening time for the same day, and no filing deadline was found.
**Confidence:** Medium. SME question: "Is 09:00 meant as when today's sheet opens, or as the deadline for yesterday's sheet?"

---
**Coverage:** Read fully: ManHourApproveStatusService, ManHourBpmApplyService, ManHourRevisionService, ManHourPostingCheckServiceImpl, ManHourEndServiceImpl, TimeSheetRateServiceImpl, both timesheet MQ handlers, both jobs, and the ManHourStatus / ApproveStatus / BpmProcessInstanceResult enums. From ManHourServiceImpl I read 330-512, 756-1066, 1184-1290, 1400-1418 and 1540-1765, plus selected parts of 2810-3085 and the status methods of ManHourItemMapper.
Skimmed or not read: ManHourItemServiceImpl (only its status-related lines), ManHourAPPController (route list only), getPIVariables / workspace-visibility / rate-hit validators, ManHourEndService posting-ledger population, SyncService.syncWorkHours (A8 import), the human-days calculation (calculation lens), and BPM-side code that emits withdraw and "return to initiator".
Probably missed: how `psa_time_sheet_end` rows are created (the actual "posted" transition), any A8-sourced sheet lifecycle (dataOrigin = 1), admin-side controllers (ManHourWCheckController, ManHourDirtyDetector), and the compensation replay for the self-approval race (#460).