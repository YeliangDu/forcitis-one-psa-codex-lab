### RULE-01: Only the assigned approver may act on a task
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmTaskServiceImpl.java:1303-1312`
**Plain English:** A user can approve, reject, return, transfer or list return targets for a task only if that task is currently assigned to them. Being a candidate or holding a menu permission is not enough.
**Specification:**
  Given task T-100 is active and assigned to user 2048
  When user 3001 calls /bpm/task/decide (or /return, /return-to-activity, /update-assignee, /return-activity-list) on T-100
  Then the request is rejected with 1009005001 "该任务的审批人不是你". If T-100 is already completed or deleted, the request is rejected with TASK_COMPLETE_FAIL_NOT_EXISTS.
**Parameters:** The check is an exact match between the task assignee and the logged-in user. `/decide`, `/return-activity-list` and `/return-to-activity` have no `@PreAuthorize`, and `/return` only requires `isAuthenticated()` (BpmTaskController.java:77-115). Authorization depends entirely on this service-layer check (refs #1034).
**Edge cases handled:** The task must still be in the runtime table, so finished tasks fail. Each task in a multi-decision request is checked separately (lines 867-870).
**Suspected defect:** The fst unified-approve gate accepts unclaimed candidates (`taskCandidateOrAssigned`), but this check and `validateApprovalTaskFacts` (BpmProcessTaskApiImpl.java:494-497) require the exact assignee. See RULE-25.
**Confidence:** High. The logic is explicit.

### RULE-02: The initiator of a customized OA process is the caller-supplied `creator`, otherwise the logged-in user
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/oa/CustomizedBpmOAServiceImpl.java:105-110`
**Plain English:** When a business document is sent for approval, the process starter is whoever the request names in `creator`. Only when `creator` is empty does the system use the logged-in user.
**Specification:**
  Given a request {paramsId: 1789…, processKey: "OPP_QUOTATION", creator: 2048}, sent by any caller
  When /bpm/oa/customized/create is invoked
  Then the process instance is started with startUserId = 2048, and the initiator variables (creUserId, roles, leaderUserId, depts, postName, billing) are derived from user 2048
**Parameters:** `creator` is optional (#356, meant for scheduled jobs with no login). The initiator variable `rejected` is always initialised to false (lines 206-225).
**Edge cases handled:** If there is no login and no creator, `userId` is null. `userMap.get(null)` then leads to an NPE in `buildProcessInstanceVariables`.
**Suspected defect:** The HTTP endpoint is `@PermitAll` with `@DataPermission(enable=false)` (CustomizedBpmController.java:29-38) and accepts `creator` from the request body. An unauthenticated caller could therefore start, or resume (see RULE-03), an approval for any document while posing as any user, with arbitrary process variables that drive gateways and assignee selection.
**Confidence:** High on the code. SME/security question: "Is /bpm/oa/customized/create reachable from outside the internal service mesh (gateway rules), and should `creator` be accepted only from trusted internal RPC?"

### RULE-03: Duplicate-start guard: one live process per business document, reused on resubmission
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/oa/CustomizedBpmOAServiceImpl.java:135-198`
**Plain English:** If the document already has a live approval, a new submission either resumes that approval (when it is sitting at the initiator node) or is refused (when it is still with approvers). A new instance is created only when the previous one has ended.
**Specification:**
  Given document paramsId 1001 is mapped to live instance PI-A, and PI-A's active task is at node "Activity_approve_1"
  When createOA is called again for paramsId 1001
  Then it fails with code 503 "原流程尚在审批中，不能重复发起"
  Given PI-A is instead parked at the initiator node (the node with extension attribute "跳过发起人")
  When createOA is called
  Then the variables are overwritten, `rejected` is reset to false, data_stamp is refreshed, the initiator task is completed, and PI-A is returned (no new instance)
  Given PI-A has ended (approved, rejected or cancelled)
  When createOA is called
  Then a new instance is created and the mapping row's process_instance_id and data_stamp are overwritten
**Parameters:** The initiator node is identified by the extension attribute `跳过发起人`. Error code 503.
**Edge cases handled:** A `process_to_be_sent` row for the document is deleted after a successful start. The definition is looked up by the active version of processKey.
**Suspected defect:** The lookup is by `params_id` alone, not (params_id, processKey). The comment in WorkflowReturnStateQuery.java:17 says paramsIds are 16-digit snowflakes that are unique across tables, but no code enforces that. Two process types sharing an id would cross-wire.
**Confidence:** High. SME question: "Can one business document legitimately go through two different process keys (e.g. quotation and its revision), and should the guard be per processKey?"

### RULE-04: Batch submission returns the existing instance for any document already mapped
**Category:** Validation
**Priority:** P1
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/oa/CustomizedBpmOAServiceImpl.java:89-103`
**Plain English:** In a batch submission, a document that already has any recorded process instance is skipped and its old instance id is returned. Only unmapped documents start new instances.
**Specification:**
  Given paramsId 1001 is mapped to PI-A, which ended as rejected, and paramsId 1002 has no mapping
  When submitApprovalBatch([1001, 1002]) runs
  Then the result is {1001: PI-A (not restarted), 1002: newly created PI-B}, all in one transaction
**Parameters:** None.
**Edge cases handled:** The whole batch rolls back if any single start fails.
**Suspected defect:** This disagrees with the single-submit path (RULE-03), which starts a new instance once the old one has ended and refuses a live one. In batch mode a re-submission after a final reject silently returns a dead instance, and the duplicate-live case is accepted rather than refused.
**Confidence:** Medium. SME question: "Is batch submission only ever used for first-time submissions? If re-submission is possible, should it follow single-submit semantics?"

### RULE-05: Idempotent process start by idempotency key
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/oa/BpmOAIdempotentStartService.java:25-53`
**Plain English:** A start request carrying an idempotency key creates at most one process instance. A retry with the same key returns the original instance id.
**Specification:**
  Given key "OPP_QUOTATION:1001:2" was already recorded with instance PI-B
  When the start is retried with the same key
  Then PI-B is returned and no new instance is created
  Given the key is new and two requests race
  When both arrive
  Then a transaction-scoped DB app lock "bpm:oa:start:<key>" serialises them, the loser re-reads after the lock, and it returns the winner's PI
**Parameters:** Lock prefix `bpm:oa:start:`. A lock result below 0 fails the start.
**Edge cases handled:** A blank returned PI raises "流程已发起但未返回流程实例号". The request row and the Flowable start commit or roll back together.
**Confidence:** High.

### RULE-06: Approval start rounds: a monotonic round number per (processKey, businessId) defines the idempotency key
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/platform/workflow/WorkflowStartRoundService.java:20-55`
**Plain English:** Each time a business document is sent for approval, the system records the next round number. The key "processKey:businessId:round" ensures a retry of the same round never starts a second process.
**Specification:**
  Given ETC task 5001 has previous rounds 1 and 2
  When it is submitted again inside the business transaction
  Then round 3 is inserted with key "ETC…:5001:3", and after the BPM start the instance id must bind to exactly 1 row
**Parameters:** First round = 1. `Propagation.MANDATORY`, so the call must be inside the business transaction.
**Edge cases handled:**
- If the local transaction rolls back after a remote success, the round row disappears and a retry reuses the same key, which together with RULE-05 returns the same PI.
- A blank PI or a bind affecting a row count other than 1 throws an error.
- `isCommittedInstance` lets result handlers tell "message arrived too early" (redeliver) from "already processed" (ignore).
**Suspected defect:** `selectMaxRound` followed by insert is not locked. Two concurrent submissions could compute the same round. Whether a unique index exists is not visible here.
**Confidence:** Medium. SME/DBA question: "Is there a unique constraint on (process_key, business_id, start_round) or on idempotency_key?"

### RULE-07: A resubmission after a return must belong to the current return round
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/oa/CustomizedBpmOAServiceImpl.java:229-283`
**Plain English:** "Continue after return" only advances the process if it is still parked at the initiator node and, when a returnTaskId is supplied, that it is the same initiator task. If the process has already moved on, the call succeeds as a no-op only when the resubmitted snapshot is identical to the one stored.
**Specification:**
  Given instance PI-A is parked at initiator task T-9 and the request has returnTaskId T-7
  When continueProcessAfterReturn runs
  Then it fails with "流程退回修改轮次已变化，无法续跑"
  Given PI-A has already left the initiator node and the request data_stamp equals the stored data_stamp
  When it runs
  Then it is an idempotent no-op; if the stamps differ or either is blank it fails with "流程已离开本次退回修改节点，且重提快照不一致"
**Parameters:** None.
**Edge cases handled:**
- No mapping row returns 404 "未找到对应的流程实例，无法续跑".
- An ended instance follows the same snapshot-equality rule.
- `rejected` is reset to false before completing the initiator task.
**Suspected defect:** RULE-03's resume branch inside createOA performs the same resume without this round check, and it accepts any caller (see RULE-02).
**Confidence:** High.

### RULE-08: End-of-process write-back of approve_status to the business table
**Category:** Policy
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/oa/CustomizedBpmOAServiceImpl.java:303-337`
**Plain English:** When a process ends, BPM writes the outcome into the `approve_status` column of the business table configured on the process definition. Timesheets are excluded.
**Specification:**
  Given process key "OPP_X" has tableName "fst_x" and document 1001 is mapped to the ended instance
  When the result event arrives with result 2 (通过) / 3 (不通过) / 4 (已取消)
  Then `UPDATE fst_x SET approve_status = 3 / 2 / 4, update_time = getDate() WHERE id = 1001`. Result 1 (处理中) writes nothing.
**Parameters:**
- Mapping from process result to ApproveStatus: 2→通过(3), 3→驳回(2), 4→取消(4).
- Excluded key: "TIMESHEET", whose write-back moved to fst consumers (#564).
**Edge cases handled:** A blank tableName or a missing mapping row means no write.
**Suspected defect:**
- The table name is interpolated as `${tableName}` (SqlActionMapper.java:15), so it relies on trusted definition configuration.
- The definition is resolved by the currently active version of the key. A suspended or missing definition, or a missing ext row, causes an NPE.
- There is no status precondition (it is not a CAS), so a redelivered or late event can overwrite a newer status.
**Confidence:** Medium. SME question: "Which process keys still rely on this generic write-back rather than an fst ProcessResultHandler, and is overwriting without a state check acceptable?"

### RULE-09: Unified decide: same instance, result must be pass/fail, a reject needs a reason, one decision per non-row node
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmTaskServiceImpl.java:865-975`
**Plain English:**
- One decide request may cover several of the approver's tasks, but they must all belong to one process instance.
- Each result must be pass (2) or fail (3), and a fail needs a reason unless the node has a BEFORE hook.
- On ordinary nodes each node accepts exactly one decision.
- All hooks are checked before any task is completed.
**Specification:**
  Given approver 2048 holds T-1 (node A) and T-2 (node B) in instance PI-A
  When they decide {T-1: 3 with reason ""} on node A, which has no beforeHookKey
  Then the request is rejected with 1009005008 "不通过必须填写审批意见"
  When the decisions span PI-A and PI-B
  Then the request is rejected with 1009005010
  When a result is 4
  Then the request is rejected with 1009005007
  When a non-row node receives 2 decisions
  Then the request is rejected with 1009005009
**Parameters:** Accepted results: APPROVE = 2, REJECT = 3.
**Edge cases handled:**
- Node groups containing a reject are processed last, so a subprocess cancellation does not orphan the other groups' tasks.
- If any group's BEFORE hook returns an interaction, nothing is committed.
- Countersign rejects are advisory (RULE-11).
**Confidence:** High.

### RULE-10: Row-approval nodes must be decided all at once, covering every pending row
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmTaskServiceImpl.java:1042-1098`
**Plain English:** On a row-by-row approval node, the approver must submit a result for exactly every row task they hold on that node. If any row fails, the whole document is returned to the initiator once the node completes.
**Specification:**
  Given approver 2048 has 3 active row tasks on node "PM_ROW" in PI-A
  When they submit 2 decisions
  Then the request is rejected with 1009005006 "应提交 3 行，实际 2 行"
  When they submit 3 decisions and one is reject
  Then `rejected=true` is set before completing, each row publishes its own result MQ and records its own result, and the instance is not deleted
**Parameters:** A node is a row node only if exactly 1 assign rule exists with `row_approval=true` (lines 1376-1383).
**Edge cases handled:**
- Submitting the same row twice is rejected because the set size differs from the decision count.
- Row nodes also refuse single approveTask/rejectTask (lines 774-777, 805-807) and are skipped by batch approve.
- The system fast-forward `autoApproveRowTask` (lines 1010-1031) bypasses the coverage check by design.
**Confidence:** High.

### RULE-11: A reject from an added countersigner is advisory only
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmTaskServiceImpl.java:982-1040`
**Plain English:** If a person added by countersign votes "fail", their opinion is recorded but the process is not terminated and no result message is sent. Only originally assigned approvers can truly reject.
**Specification:**
  Given task T-5 has `bpm_task_ext.countersign=1`
  When its assignee decides 3 with reason "价格偏高"
  Then T-5 is completed with ext result 3 and the reason, the instance continues, and node exit follows the MI completion condition
**Parameters:** Flag `countersign=true` (set in RULE-12).
**Edge cases handled:** "Pass" by a countersigner follows the normal approveTask path, including the result MQ.
**Confidence:** High.

### RULE-12: Countersign can only target a running parallel multi-instance node
**Category:** Validation
**Priority:** P1
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmTaskServiceImpl.java:1242-1295`
**Plain English:** Extra approvers can be added only to a node that currently has a live parallel multi-instance execution. Duplicates and nulls are dropped, and each added task is flagged as a countersign.
**Specification:**
  Given PI-A's node "BU_REVIEW" is a running parallel MI node
  When /add-countersign is called with users [2048, 2048, null, 3001]
  Then 2 MI executions are added (2048 and 3001), both tasks are flagged countersign=true, and their ids are returned
  When the node is not MI or not running
  Then the request is rejected with 1009005011
**Parameters:** Empty after de-duplication: returns [] with no error.
**Edge cases handled:** A dead instance raises PROCESS_INSTANCE_NOT_EXISTS.
**Suspected defect:** The service does not check that the caller is the initiator or a current approver of the instance; only `bpm:task:update` gates the HTTP endpoint (BpmTaskController.java:125-130). There is also no check that the added users are active.
**Confidence:** Medium. SME question: "Who is allowed to add countersigners: the current approver, the initiator, or admins only?"

### RULE-13: A reject inside a subprocess returns to the initiator; a top-level reject terminates the process
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmTaskServiceImpl.java:802-861`
**Plain English:** A "fail" on a node nested in a subprocess must never end the main process. It is converted into a return to the initiator. A fail on a top-level node deletes the instance and the whole document is rejected.
**Specification:**
  Given T-3 is on a userTask whose parent is a subProcess
  When it is rejected with "工时有误"
  Then returnTaskToInitiator semantics apply and the instance stays alive
  Given T-4 is on a top-level node
  When it is rejected
  Then a REJECT task MQ is sent, the instance is deleted (ext status FINISH, result 3), and the task ext result is 3
**Parameters:** None.
**Edge cases handled:** A missing model or blank node key is treated as top-level. For timesheets, the reason is formatted from the JSON row opinions.
**Confidence:** High.

### RULE-14: Return to initiator requires a reason and passes the node's BEFORE hook
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmTaskServiceImpl.java:1109-1152`
**Plain English:** An approver can send the document back to its initiator only with a non-blank reason. A configured BEFORE hook may block the return or ask for confirmation, and the process then routes back via BPMN without ending.
**Specification:**
  Given the assignee returns T-6 with reason "" and hookInteraction=false
  Then it fails with 400 "审批意见不能为空"; with hookInteraction=true it returns the RETURN_REASON dialog instead
  Given the reason is "缺附件" and the hook returns warn
  Then it is not committed (409, or the hook's code)
  Given the hook allows
  Then a REJECT task MQ is sent, `rejected=true`, the task is completed, and the ext result is 3
**Parameters:** Default block code 409.
**Confidence:** High.

### RULE-15: Return to a specific earlier node: single-instance source and target only
**Category:** Validation
**Priority:** P1
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmTaskServiceImpl.java:1155-1225`
**Plain English:** An approver can move the token back to an upstream node that already completed in this instance. Neither the current node nor the target may be a multi-instance node, and for timesheets the initiator node is not a valid target.
**Specification:**
  Given PI-A has completed nodes [start(initiator), DeptLead, BU(MI)] and the current node is Finance
  When the returnable list is requested
  Then it is [start, DeptLead], in completion order and de-duplicated; for process "TIMESHEET" it is [DeptLead]
  When returning to "BU"
  Then the request is rejected with 1009005003
  When the current node is MI
  Then the request is rejected with 1009005002
**Parameters:** Exclusions are the current node, nodes not in the model, nodes with loopCharacteristics, and the TIMESHEET initiator node.
**Edge cases handled:** The ext result is BACK (5). No business MQ is sent and no business status changes.
**Suspected defect:** The reason is optional here (BpmTaskReturnToActivityReqVO has no @NotEmpty), while return-to-initiator requires one.
**Confidence:** High. SME question: "Should return-to-node also require a reason?"

### RULE-16: Transfer (change assignee) allowed only by the current assignee, to any user
**Category:** Validation
**Priority:** P1
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmTaskServiceImpl.java:1228-1238`
**Plain English:** A task can be reassigned to someone else only by the person currently holding it, and the caller also needs `bpm:task:update`.
**Specification:**
  Given T-7 is assigned to 2048
  When 2048 transfers it to 3001
  Then the assignee becomes 3001
  When 4000 tries the same transfer
  Then the request is rejected with 1009005001
**Parameters:** Only the new assignee id is required (@NotNull).
**Edge cases handled:** None.
**Suspected defect:**
- The target user is not validated (existence, active status, not the initiator).
- No reason or audit row is recorded.
- Row-approval and countersign tasks can be transferred, which may break the coverage rule in RULE-10.
**Confidence:** Medium. SME question: "Are transfers to the initiator or to inactive users allowed, and must a reason be recorded?"

### RULE-17: Batch approve acts on every user's pending tasks in each assignee's name
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmTaskServiceImpl.java:430-466`
**Plain English:** A holder of `bpm:task:update` can approve every matching pending task of all users. Each approval is recorded as if the task's own assignee approved it, with reason "通过". Row-approval and notify tasks are skipped.
**Specification:**
  Given filter {name: "报价"} matches 5 tasks of 3 users, one of them a row node
  When PUT /bpm/task/batch/approve runs
  Then 4 tasks are approved with userId = each task's assignee and reason "通过", with a 1-second sleep between tasks
**Parameters:** Reason "通过". Sleep of 1000 ms (the comment says 3 seconds).
**Edge cases handled:** Row nodes and `知会节点` nodes are skipped.
**Suspected defect:**
- This bypasses RULE-01. The audit trail shows the assignee, not the operator.
- BEFORE hooks are not run, because approveTask is called directly rather than through decide.
- One failure rolls back nothing already committed per task (there is no outer transaction), while the loop aborts.
**Confidence:** High on the behaviour. SME question: "Is this an admin back-door that must survive the rewrite, and if so, must it record the real operator and honour BEFORE hooks?"

### RULE-18: Only the initiator may cancel, before any cutoff node, with a reason
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmProcessInstanceServiceImpl.java:239-278`
**Plain English:** Cancelling a running approval is allowed only to the user who started it and only before any node marked "撤回截止" has completed. A reason is required unless a process-level BEFORE hook decides. After a cancel, the Flowable history is deleted unless the model says to keep it.
**Specification:**
  Given PI-A was started by 2048 and its cutoff node "财务确认" has not completed
  When 3001 cancels it
  Then the request is rejected with PROCESS_INSTANCE_CANCEL_FAIL_NOT_SELF
  When 2048 cancels it with reason "" and no beforeHookKey
  Then it fails with 400 "取消原因不能为空"
  When "财务确认" has completed
  Then it fails with 1009004011 "已越过「财务确认」节点"
  When 2048 cancels with reason "金额错误"
  Then active tasks are set to result 4, the instance is deleted, and history is deleted unless `retainHistoryOnCancel=true`
**Parameters:** Process attributes `beforeHookKey` and `retainHistoryOnCancel`. The reason may come from inputs["reason"].
**Edge cases handled:**
- With a hook, the hook result is gated by RULE-22. A confirmed `cancelAction` persists a CANCEL result message reliably.
- Batch cancel (lines 282-304) treats an already-cancelled instance of the same initiator as success. Any uncommitted hook result aborts the batch.
**Confidence:** High.

### RULE-19: Withdraw to initiator: initiator only, before cutoff, single-instance current node, not already at initiator
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmProcessInstanceServiceImpl.java:360-420`
**Plain English:** The initiator can pull a running approval back to themselves for editing. This requires that:
- the process model has an initiator node;
- an approver task is active;
- the process is not already at the initiator node;
- the current node is not multi-instance;
- no cutoff node has been passed.
**Specification:**
  Given PI-A was started by 2048, is at single-instance node "DeptLead", and has no cutoff passed
  When 2048 withdraws with reason "金额填错了"
  Then the active task's ext result becomes BACK (5) with reason "发起人撤回：金额填错了", and the token moves to the initiator node
  When the current node is MI
  Then the request is rejected with PROCESS_INSTANCE_WITHDRAW_FAIL_SOURCE_MULTI_INSTANCE
  When the process is already at the initiator node
  Then the request is rejected with 1009004007
**Parameters:** Initiator node attribute `跳过发起人`. The reason is @NotEmpty.
**Edge cases handled:**
- If there is no initiator node in the model, the instance can only be cancelled.
- If there is no active task, the withdraw is rejected.
- `getWithdrawable` (lines 423-439) mirrors the initiator and cutoff checks for showing the button, but not the MI or at-initiator checks.
**Suspected defect:** `getWithdrawable` can show "allow" when the actual withdraw will fail (MI node or already at initiator).
**Confidence:** High.

### RULE-20: Withdraw/cancel cutoff = any marked node with a completed task
**Category:** Policy
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmProcessInstanceServiceImpl.java:454-477`
**Plain English:** Once any node flagged "撤回截止" has a finished task in this instance, the business effect is treated as having happened, and the initiator can no longer withdraw or cancel.
**Specification:**
  Given nodes "BU确认" and "财务确认" are both flagged and "BU确认" has 1 finished task (including auto-completed or notify tasks)
  When a cutoff check runs
  Then it returns "BU确认" and the withdraw or cancel is blocked
  Given the token is sitting on "BU确认" and its task is not yet finished
  Then the cutoff has not been passed
**Parameters:** Extension attribute `撤回截止`. Any parallel branch passing the cutoff blocks.
**Edge cases handled:** If no node is flagged, the check never blocks. The node name falls back to the node key.
**Confidence:** High.

### RULE-21: BEFORE-hook commit gate
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/task/WorkflowHookBeforeGate.java:16-38`
**Plain English:** A configured business hook can stop an approval action. Only "allow" or "not_applicable" lets it proceed. For cancel/withdraw the action also needs a reason or an explicit action value; task actions keep BPM's own reason rules.
**Specification:**
  Given a cancel with hook outcome "allow", reason "", and actionValue ""
  Then it is not committed
  Given outcome "warn", "reject" or "supplement"
  Then it is never committed
  Given no hook result (null) and reason "改期"
  Then the cancel is committed; for a task action, a null result means not committed
**Parameters:** Committing outcomes: ALLOW, NOT_APPLICABLE.
**Edge cases handled:** Variable updates returned by an allowing task hook are written to the process before completion (BpmApprovalTaskHookService.java:125-130).
**Confidence:** High.

### RULE-22: Task BEFORE hooks require a bound business document and a registered hook key
**Category:** Validation
**Priority:** P1
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmApprovalTaskHookService.java:75-133`
**Plain English:**
- If a node has a BEFORE hook but the instance has no mapped business document, the action fails.
- The hook receives a server-rebuilt context: processKey, paramsId, the row ids of all grouped tasks, the rejected row ids, and the payout variables.
- The hook key must be registered in fst. Unknown keys fail, and duplicate registrations stop startup (WorkflowHookRegistry.java:27-58).
**Specification:**
  Given node "FIN" has beforeHookKey "TRAVEL_PAYOUT" and PI-A has no `bpm_oa_customized` row
  When the task is decided
  Then it fails with "审批任务未绑定业务单据"
  Given the key is not registered
  Then it fails with WORKFLOW_HOOK_NOT_REGISTERED
**Parameters:** Snapshot variables: payableAmount, needPayout, expenseAllocationRevisionNo.
**Edge cases handled:** A blank hook key means no hook (null result).
**Confidence:** High.

### RULE-23: Node field permissions: unlisted fields are read-only; configuration is a full overwrite per process key
**Category:** Validation
**Priority:** P1
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/definition/BpmNodeFieldPermissionServiceImpl.java:51-95`
**Plain English:** For a task, the field permissions are the exception rows configured for (process definition key, node key). Any field not listed is read-only. Saving replaces the whole configuration for that process key, across all versions.
**Specification:**
  Given OPP_QUOTATION node "PM" has rows {taxRate: 1 (编辑), remark: 3 (必填)}
  When permissions are requested for task T-8 on node PM, whether active or finished
  Then the result is {taxRate: 1, remark: 3}, and every other field is read-only
**Parameters:** Values are 1 = 编辑, 2 = 隐藏, 3 = 必填. Read-only is the default and is not stored. The key is the processDefinitionId prefix before ":".
**Edge cases handled:** An unknown or blank task returns {}, which means everything is read-only.
**Suspected defect:** The `/by-task` endpoint only checks `bpm:task:query`, not task ownership, so any approver can read another task's field map. Enforcement is in fst (RULE-24); the service itself does no enforcement.
**Confidence:** High.

### RULE-24: Unified approve: ownership gate before any field write, fail-closed field permission, default reasons
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/workflowform/WorkflowApproveServiceImpl.java:52-174`
**Plain English:** Before an approver's edited fields are applied, the system checks four things:
- the task is still running;
- the user is its assignee or a candidate;
- the task's document matches paramsId;
- the task's process key matches businessKey.

If permissions cannot be loaded, approval is refused. A pass defaults its reason to "同意", and a fail requires a reason.
**Specification:**
  Given user 2048 holds T-9 of quotation 1001
  When they submit businessKey OPP_QUOTATION, paramsId 1002, taskId T-9
  Then it fails with WORKFLOW_APPROVE_TASK_BUSINESS_MISMATCH and nothing is written
  When a pass is submitted with reason ""
  Then the reason becomes "同意"
  When a fail is submitted with reason ""
  Then it fails with WORKFLOW_APPROVE_REJECT_REASON_REQUIRED
  When BPM is unreachable for permissions
  Then it fails with WORKFLOW_APPROVE_PERMISSION_UNAVAILABLE
**Parameters:** Result codes come from the RESULT_APPROVE/RESULT_REJECT constants. The default pass reason is "同意".
**Edge cases handled:** Ownership-check failures are fail-closed.
**Suspected defect:**
- `handler.applyApproved` commits on its own before `completeApprovalTask`. The fst gate accepts candidates, but BPM then requires the exact assignee and taskDefinitionKey (BpmProcessTaskApiImpl.java:494-506). An unclaimed candidate's field edits can therefore be committed and then fail completion, leaving changes without an approval record.
- The fst gate does not compare taskDefinitionKey, so permissions for node X could be applied while the request names node Y.
**Confidence:** High.

### RULE-25: Process-result handling is idempotent per instance and status (reference: quotation)
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/presales/quote/DecideQuotationUseCase.java:32-75`
**Plain English:** A process-end result is applied to the document only once, and only if all of these hold:
- the document is still "pending";
- the message belongs to the current round's instance;
- the message is not older than the latest submission.

A duplicate or late message is ignored silently, and a real failure is rethrown so it is redelivered.
**Specification:**
  Given quotation 1001 is PENDING, bound to PI-B, with submitTime 2026-10-01 10:00
  When a message arrives for PI-A
  Then it is ignored
  When a message for PI-B has createTime 09:59
  Then it is ignored
  When a reject message for PI-B has an empty reason
  Then the quotation is rejected with reason "审批未通过" via a compare-and-set from PENDING; 0 rows updated means an idempotent ignore
**Parameters:**
- Default reject reason "审批未通过".
- Result mapping in QuotationProcessResultHandler.java:39-48: 2 means approve, 3 or 4 means reject, others are ignored.
**Edge cases handled:**
- A missing document is ignored.
- ETC (DecideEtcApprovalUseCase.java:59-84) adds a "message arrived before the start transaction committed" check through `isCommittedInstance` and `latestProcessInstanceId`. It throws in order to be redelivered.
- Consumers rethrow every failure (ProcessResultConsumer.java:58-74; ApprovalTaskResultConsumer.java:58-74).
**Suspected defect:**
- Cancelled (4) is mapped to a quotation reject, which conflates initiator withdrawal with an approver rejection.
- When `afterHookKey` is present, ApprovalTaskResultConsumer runs only the hook and skips the businessKey handler (lines 46-63). ProcessResultConsumer runs both. The asymmetry is undocumented.
- Idempotency is per handler and by convention only. The interface comment requires it, but nothing enforces it.
**Confidence:** Medium. SME questions: "Should a cancelled approval leave the quotation in REJECTED or return it to DRAFT?" and "Do all other ProcessResultHandlers (budget, revenue, deliverable, travel, timesheet) implement the same status, instance and time checks?"

---
**Coverage:**
- **Read fully:** BpmTaskServiceImpl (approve, reject, decide, return, transfer, countersign, batch and notify sections), BpmProcessInstanceServiceImpl lines 236-702, CustomizedBpmOAServiceImpl, BpmOAIdempotentStartService, WorkflowHookBeforeGate, BpmApprovalTaskHookService, BpmNodeFieldPermissionServiceImpl, BpmWithdrawnHistoryCleanupRunner, WorkflowHook/WorkflowHookRegistry, ApprovalResultHandler/ProcessResultHandler and both consumers, WorkflowStartRoundService, ApprovalVisibilityGuard, WorkflowReturnStateQuery.
- **Skimmed or partly read:** the parts of BpmProcessTaskApiImpl and WorkflowApproveServiceImpl outside my listed paths, plus DecideQuotationUseCase and DecideEtcApprovalUseCase as idempotency references, and seed doc 09. Not read: the task-assign-rule engine (BpmTaskAssignRuleServiceImpl), BpmModel/ProcessDefinition services, the todo/done list visibility filters, task-created listeners (auto-skip of the initiator and duplicate approvers), the other business result handlers, and the 审批流通用化 design docs.
- **Probably missed:** assignee-resolution and auto-approve rules in the Flowable listeners and assign-rule service, delegate semantics beyond setAssignee, and per-domain idempotency gaps in the non-quotation handlers. I did not confirm whether /bpm/oa/customized/create is exposed through the gateway (RULE-02). The code contains no instruction-shaped text aimed at automated analysis.