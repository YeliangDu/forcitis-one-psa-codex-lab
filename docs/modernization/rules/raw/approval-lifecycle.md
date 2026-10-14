### RULE-01: Process-instance status and result lifecycle
**Category:** Lifecycle
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmProcessInstanceServiceImpl.java:555-650`
**Plain English:** A new approval is stored as "Running / In progress". It ends in one of three ways: approved (it reached the end event), rejected (a top-level approver said no) or cancelled (it was deleted for any other reason). Each ending sets the status to "Finished", records an end time and raises a result event.
**Specification:**
  Given a quotation approval is started by user 1001
  When  the instance is created
  Then  bpm_process_instance_ext gets status=1 (RUNNING), result=1 (PROCESS) and the title "<definition name>_<initiator nickname>_<yyyy-MM-dd start date>"
  When  the last node completes normally
  Then  status=2 (FINISH), result=2 (APPROVE), end_time=now, and an "approved" message plus a result event are emitted
  When  an approver rejects at a top-level node
  Then  the instance is deleted with reason "不通过任务，原因：<reason>" and the record becomes status=2, result=3 (REJECT), end_time=now
  When  the instance is deleted for any reason that does not start with "不通过任务，原因："
  Then  the record becomes status=2, result=4 (CANCEL), end_time=now
**Parameters:** result codes PROCESS=1, APPROVE=2, REJECT=3, CANCEL=4, BACK=5 (task only), NOTIFY=6 (task only); status codes RUNNING=1, FINISH=2; reject-reason prefix "不通过任务，原因：". A caller-supplied `processTitle` variable overrides the default title (lines 694-697).
**Edge cases handled:** deleting the instance on reject fires the cancel listener first. That listener skips its update when the reason has the reject prefix, so the record is not left as CANCEL. For timesheets, the event keeps the raw reject JSON while notifications show a formatted text version.
**Suspected defect:** Telling a rejection apart from a cancellation depends only on the prefix of a Chinese reason string (`BpmProcessInstanceDeleteReasonEnum.isRejectReason`). If that wording changes, rejected instances would be recorded and published as cancellations.
**Confidence:** High — the transitions are explicit.

### RULE-02: Task result lifecycle (bpm_task_ext)
**Category:** Lifecycle
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmTaskServiceImpl.java:1349-1424`
**Plain English:** Every approval task starts as "In progress". Completing it marks it "Approved" by default, and the specific action then overwrites that with the real outcome. A task that is removed without an outcome becomes "Cancelled", but a task that already has a final outcome is never changed.
**Specification:**
  Given a task is created on node "PM approval"
  When  it is persisted
  Then  bpm_task_ext.result=1; on a row-approval node the row id (the multi-instance element variable) is stored in row_id
  When  the engine completes the task
  Then  result=2 and end_time=now; the action that caused it then overwrites this with REJECT(3), BACK(5) or NOTIFY(6) where applicable
  When  the engine deletes the task (instance cancelled or rejected, multi-instance condition met, token moved)
  Then  after commit, if result is not already final (2/3/4/5/6), set result=4 with the delete reason ("MI_END" is translated to "系统自动取消，原因：多任务审批已经满足条件…")
**Parameters:** final results {2,3,4,5,6}. A node counts as a row-approval node only if it has exactly one assignment rule and that rule has `row_approval=true` (lines 1375-1383).
**Edge cases handled:** activities that are not tasks (no historic task) are skipped; a missing ext row is logged and skipped.
**Confidence:** High.

### RULE-03: A top-level rejection ends the whole process; a rejection inside a sub-process sends it back to the initiator
**Category:** Lifecycle
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmTaskServiceImpl.java:800-844`
**Plain English:** "Reject" on a main-flow node ends the approval for good. The same button on a node inside a sub-process only returns the document to the initiator, and the main flow stays alive.
**Specification:**
  Given approver 2002 holds task T on a top-level node of a quotation approval
  When  2002 rejects with reason "价格过低"
  Then  a REJECT task-result message is queued, the instance is deleted, the process record becomes FINISH/REJECT and task T becomes result=3 with end_time and reason
  Given task T is on a node inside a sub-process (for example a timesheet)
  When  2002 rejects
  Then  the call is delegated to return-to-initiator (RULE-07): the instance is not deleted and no process-end message is sent
**Parameters:** none
**Edge cases handled:** only the assignee can act (`checkTask`: task must exist and be assigned to the caller). Row-approval nodes refuse single rejects (TASK_APPROVE_FAIL_ROW_APPROVAL_NODE). A missing instance gives PROCESS_INSTANCE_NOT_EXISTS.
**Confidence:** High.

### RULE-04: Validation for the unified "decide" action
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmTaskServiceImpl.java:863-975`
**Plain English:** One submission can decide several of the approver's own tasks, but only within a single process instance. Each decision must be approve (2) or reject (3). A rejection needs a reason unless the node delegates that check to a business pre-check hook. Every business pre-check must pass before any task is completed.
**Specification:**
  Given approver 2002 submits decisions {T1 on node A: approve, T2 on node B: reject with an empty reason}, both in instance P, and node B has no BEFORE hook
  When  decide is called
  Then  it fails with TASK_DECIDE_FAIL_REASON_REQUIRED and nothing changes
  Given the same submission with reason "数量错误"
  Then  the node-A group is completed first and the node-B (reject) group last; for a non-row node exactly 1 decision per node is allowed
**Parameters:** allowed results {2,3}
**Edge cases handled:** tasks from different instances are refused (TASK_DECIDE_FAIL_CROSS_INSTANCE). More than one decision for a non-row node is refused (TASK_DECIDE_FAIL_SINGLE_ONLY). If a BEFORE hook asks for interaction, nothing is committed and the hook's dialog is returned. Groups that contain a rejection run last, so the sub-process cancellation cannot delete tasks that are still being approved.
**Confidence:** High.

### RULE-05: Row-by-row approval must cover every pending row, and any rejected row rejects the node
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmTaskServiceImpl.java:1042-1098`
**Plain English:** On a row-approval node, the approver must decide all of their pending rows in one submission. If any row is rejected, the whole node is treated as rejected.
**Specification:**
  Given approver 2002 has 3 pending row tasks (rows R1, R2, R3) on node "PM approval"
  When  2002 submits decisions for only R1 and R2
  Then  it fails with TASK_DECIDE_FAIL_INCOMPLETE(3, 2)
  When  2002 submits R1 approve, R2 reject, R3 approve
  Then  the process variable rejected=true is set before any completion; each row gets its own task-result message with its rowId and its own task result (2/3/2); once the multi-instance ends, BPMN routes to the reject error event and the sub-process returns to the initiator
**Parameters:** process variable `rejected`
**Edge cases handled:** duplicate task ids in a submission are refused (the set size must equal the decision count). Single-task approve/reject on a row node is refused. Bulk approval skips row nodes (lines 443-448).
**Confidence:** High.

### RULE-06: A rejection by an added co-signer is advisory only
**Category:** Lifecycle
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmTaskServiceImpl.java:982-1006`
**Plain English:** When an approver who was added later as a co-signer (bpm_task_ext.countersign=1) rejects, the opinion is recorded but the process continues. Only the originally assigned approvers can really reject.
**Specification:**
  Given task T was added as a co-signer task (countersign=true) on node N
  When  its assignee decides reject with reason "建议复核"
  Then  T is completed and recorded with result=3 and the reason; the instance is not terminated, no task-result message is published, and whether N completes is still decided by the multi-instance completion condition
**Parameters:** bpm_task_ext.countersign
**Edge cases handled:** co-signer tasks are created only on a running parallel multi-instance node (COUNTERSIGN_ADD_FAIL_NODE_NOT_MI). Co-signer user ids are de-duplicated and nulls ignored (lines 1240-1295).
**Suspected defect:** The task-level reconcile job (RULE-20) re-sends every bpm_task_ext row with result in {2,3} that has no sent marker. So this "advisory" REJECT would be published to the business module 2 minutes to 24 hours later. That directly contradicts the "no result message" intent.
**Confidence:** High for the rule. Medium for the defect — SME/tech lead: should reconcile exclude rows with countersign=1?

### RULE-07: Return to initiator using the `rejected` flag
**Category:** Lifecycle
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmTaskServiceImpl.java:1107-1152`
**Plain English:** An approver can send the document back to the initiator for correction. A reason is required, the business pre-check runs first, and the process stays alive and waits at the initiator.
**Specification:**
  Given approver 2002 holds task T in a running timesheet instance
  When  2002 returns T with reason "工时超标"
  Then  a REJECT task-result message is queued, process variable rejected=true, T is completed (BPMN short-circuits the co-signers, raises the reject error, cancels the sub-process and routes back to the initiator node), and T's ext record is result=3 with the formatted reason
  When  the reason is blank
  Then  it fails with "审批意见不能为空", or in hook-interaction mode the RETURN_REASON dialog is returned
**Parameters:** variable `rejected`
**Edge cases handled:** if the BEFORE hook blocks in non-interactive mode, a ServiceException is thrown (code from the hook, default 409). No process-end message is sent, so the business header stays "in approval".
**Suspected defect:** A comment at lines 1134-1135 says the remaining rows staying "in approval" after a return is still waiting for business confirmation (都业梁) on whether they should revert to "rejected".
**Confidence:** Medium — the mechanics are explicit, but the business row state after a return is pending confirmation. SME: after a return to the initiator, should rows that were not rejected stay "审批中" or become "驳回"?

### RULE-08: Return to an earlier node (single-instance only)
**Category:** Lifecycle
**Priority:** P1
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmTaskServiceImpl.java:1155-1225`
**Plain English:** An approver can move the process back to any earlier node already completed in this instance, provided neither the current node nor the target is a multi-instance (co-sign) node. This changes nothing on the business side.
**Specification:**
  Given the instance completed nodes Initiator → Dept head → Finance, and approver 2002 now holds the GM task
  When  2002 asks for returnable targets
  Then  the list is [Initiator, Dept head, Finance] in completion order, excluding the current node and duplicates (for TIMESHEET the initiator node is excluded)
  When  2002 returns to "Dept head" with a reason
  Then  the GM task ext becomes result=5 (BACK) with end_time and reason, the token moves to "Dept head" and the task is reassigned by the assignment rules; no business message is sent
**Parameters:** process key `工时审批` excluded from initiator targets
**Edge cases handled:** a multi-instance source node is refused (TASK_RETURN_FAIL_SOURCE_MULTI_INSTANCE). A target outside the list is refused (TASK_RETURN_FAIL_TARGET_ILLEGAL). Multi-instance or non-user-task nodes are never listed as targets.
**Confidence:** High.

### RULE-09: Initiator "withdraw to edit" (process stays alive)
**Category:** Lifecycle
**Priority:** P1
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmProcessInstanceServiceImpl.java:358-420`
**Plain English:** The initiator can pull a running approval back to their own node to edit it. This is only allowed when the process has a marked initiator node, the process is not already there, the current node is not a co-sign node, and no withdraw-cutoff node has been passed.
**Specification:**
  Given user 1001 started a quotation approval that now sits at "Finance review" (single instance)
  When  1001 withdraws with reason "补充附件"
  Then  each active task gets ext result=5 (BACK) with reason "发起人撤回：补充附件" and all tokens move to the initiator node; no business message is sent and `rejected` is not set
**Parameters:** initiator node = the user task with extension attribute `跳过发起人`
**Edge cases handled:** the instance has ended → WITHDRAW_FAIL_NOT_EXISTS; caller is not the initiator → NOT_SELF; no initiator node → NO_INITIATOR_NODE; no active task → NO_ACTIVE_TASK; already at the initiator → ALREADY_AT_INITIATOR; any active node is multi-instance → SOURCE_MULTI_INSTANCE; a cutoff node has been passed → PASSED_CUTOFF.
**Confidence:** High.

### RULE-10: Withdraw cutoff ("撤回截止")
**Category:** Policy
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmProcessInstanceServiceImpl.java:449-477`
**Plain English:** Once any node marked "withdraw cutoff" has a completed task in this instance, the business effect counts as having happened. After that, the initiator can neither withdraw nor cancel.
**Specification:**
  Given node "Seal" is marked `撤回截止` and has one finished task (including auto-completed or notify tasks)
  When  the initiator tries to cancel, withdraw, or checks whether withdrawal is possible
  Then  the action is blocked with "流程已越过「Seal」节点，不可撤回"
  Given the token is sitting on "Seal" and its task is not finished yet
  Then  withdrawal is still allowed
**Parameters:** node extension attribute `撤回截止`. If no node is marked, nothing is blocked.
**Edge cases handled:** with parallel branches, finishing a marked node on any branch counts (conservative). The node name falls back to the node key.
**Suspected defect:** The check covers every finished task in the instance's history, not just the current round. After a return to the initiator and a resubmission, a cutoff passed in an earlier round permanently blocks withdrawal.
**Confidence:** Medium — SME: after a return and resubmission, should passing the cutoff in an earlier round still block withdrawal?

### RULE-11: Initiator cancels the whole process
**Category:** Lifecycle
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmProcessInstanceServiceImpl.java:237-356`
**Plain English:** Only the initiator can cancel, and only before the withdraw cutoff. Without a business pre-check a reason is mandatory. With a pre-check, the hook must allow it (or not apply) and a reason or a confirmed action value is needed. Cancelling marks open tasks as cancelled, ends the instance, and deletes its engine history unless the model says to keep it.
**Specification:**
  Given user 1001 started instance P with no BEFORE hook, and P is before any cutoff
  When  1001 cancels with reason "重复提交"
  Then  active tasks get result=4 with reason "主动取消任务，原因：重复提交", the instance is deleted (the record becomes FINISH/CANCEL via RULE-01), and the Flowable history is deleted unless the process attribute retain-history-on-cancel is "true"
  Given P has a BEFORE hook (for example travel) that returns ALLOW and the user picked a cancelAction
  Then  a process-result outbox row (result=4, cancelAction, reason, afterHookKey) is written in the same transaction before the deletion
**Parameters:** process attributes BEFORE_HOOK_KEY, AFTER_HOOK_KEY, RETAIN_HISTORY_ON_CANCEL. The reason falls back to `inputs["reason"]`.
**Edge cases handled:** in a batch cancel (lines 280-304), an instance that has already ended, was started by the same user and has ext result=4 is skipped (idempotent); any batch item not committed aborts the whole batch. When the hook does not commit, the dialog comes back with committed=false. A startup job (`BpmWithdrawnHistoryCleanupRunner`) removes leftover history of cancelled instances.
**Suspected defect:** Deleting history removes approval participation, so former approvers lose their participant visibility of the document (see RULE-12).
**Confidence:** High.

### RULE-12: Document detail is visible to holders of the function permission or to process participants (fail-closed)
**Category:** Policy
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/platform/workflow/ApprovalVisibilityGuard.java:51-77`
**Plain English:** A user may open a business document if they hold its function permission, or if they are a participant (initiator, past or current assignee, or candidate) in its approval process. Anyone else is told the document does not exist.
**Specification:**
  Given user 3003 lacks the permission "opp:quotation:query" but is the current approver of process P bound to quotation Q
  When  3003 opens Q
  Then  access is granted
  Given bpm is unreachable or the document has no process instance id
  Then  access is denied with the caller's "not exists" error code, not a "forbidden" error
**Parameters:** the permission point is supplied by each caller
**Edge cases handled:** no logged-in user → deny. Cancelled instances have had their history deleted, so no one counts as a participant and only the permission applies.
**Confidence:** High.

### RULE-13: Resubmission chooses between continuing the same instance, blocking, and starting a new instance
**Category:** Lifecycle
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/oa/CustomizedBpmOAServiceImpl.java:105-198`
**Plain English:** Each business document has exactly one bpm_oa_customized row. If its latest instance is alive and waiting at the initiator, resubmission continues that same instance. If the instance is alive elsewhere, resubmission is refused. If the instance has ended (approved, rejected or cancelled), a new instance starts and the row is repointed to it.
**Specification:**
  Given quotation Q (paramsId 900) has a live instance P parked at the initiator node
  When  the initiator resubmits
  Then  the recalculated variables are written to P, rejected=false, data_stamp is replaced, the initiator task is completed, and P's id is returned
  Given P is alive but at "Finance review"
  Then  it fails with error 503 "原流程尚在审批中，不能重复发起"
  Given P has ended
  Then  a new instance P2 is created (variables creUserId, roles, leaderUserId, depts, postName, billing "是"/"否", rejected=false), the row gets process_instance_id=P2 and data_stamp=new, and any psa_process_to_be_sent row for businessId 900 is deleted
**Parameters:** initiator node = attribute `跳过发起人`; `rejected` always initialised to false; billing → "是" when the user's billing flag equals FSTConstant.BillingUser.是
**Edge cases handled:** if no process variables are supplied, they are derived from the dataStamp JSON (an object, or an array merged into sets per key). Detail view: `lastProcess` = the instance equals the row's current pointer (lines 226-231).
**Confidence:** High.

### RULE-14: "Continue after return" is idempotent and bound to the current round
**Category:** Validation
**Priority:** P1
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/oa/CustomizedBpmOAServiceImpl.java:227-283`
**Plain English:** A resubmission after a return completes only the initiator task of the round being edited. If the process has already moved on (or ended), the call quietly succeeds only when the submitted snapshot equals the stored one; otherwise it fails.
**Specification:**
  Given paramsId 900's instance P is at initiator task T7 and the request says returnTaskId=T7
  When  continue is called
  Then  the variables are rebuilt (rejected=false), data_stamp is updated and T7 is completed
  Given the request says returnTaskId=T5 (an earlier round)
  Then  it fails with "流程退回修改轮次已变化，无法续跑"
  Given P has already left the initiator or ended, and the request data_stamp equals the stored data_stamp
  Then  no-op success; if the stamps differ or are blank, it fails with "流程已离开本次退回修改节点，且重提快照不一致"
**Parameters:** none
**Edge cases handled:** no row or no instance id → 404 "未找到对应的流程实例，无法续跑".
**Confidence:** High.

### RULE-15: Batch submission returns the existing instance instead of creating one
**Category:** Lifecycle
**Priority:** P1
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/oa/CustomizedBpmOAServiceImpl.java:89-103`
**Plain English:** In a batch submission, a document that already has a process instance id gets that id back and no new approval starts.
**Specification:**
  Given paramsId 900 already has process_instance_id P (ended by REJECT)
  When  it is included in submitApprovalBatch
  Then  the result maps 900 → P and no instance is created
**Parameters:** none
**Edge cases handled:** documents without a row or instance go through the normal creation path (RULE-13).
**Suspected defect:** The check does not ask whether P is still alive. Unlike single submission (RULE-13), a rejected or cancelled document resubmitted through the batch path gets its dead instance id back and is never re-approved.
**Confidence:** Medium — SME: are batch submissions only ever first-time submissions?

### RULE-16: Approval-start rounds and idempotency key
**Category:** Policy
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/platform/workflow/WorkflowStartRoundService.java:20-55`
**Plain English:** Each time a document is sent for approval, the business side records the next round number (1, 2, 3…) inside its own transaction. It uses "processKey:businessId:round" as the idempotency key, so a retry never starts a second approval. A process-end message is accepted only after the round has been bound to that instance.
**Specification:**
  Given quotation 900 under OPP_QUOTATION already has rounds 1 and 2
  When  it is submitted again
  Then  round 3 is inserted with key "OPP_QUOTATION:900:3"; on bpm's side `BpmOAIdempotentStartService` returns the existing instance for that key, or takes an application lock (prefix "bpm:oa:start:"), re-checks, then creates the instance and stores it
  When  a process-end message for instance P arrives before the round has been bound to P
  Then  isCommittedInstance returns false, so the caller can redeliver instead of ignoring it
**Parameters:** round starts at 1, +1 each time
**Edge cases handled:** binding a blank instance id, or binding a round that already has an instance, fails. If the local transaction rolls back, the round row disappears, so a retry reuses the same key.
**Confidence:** High.

### RULE-17: Which process endings reach business modules
**Category:** Policy
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/mq/producer/BpmProcessResultProducer.java:47-88`
**Plain English:** After commit, only approved (2), rejected (3) and genuinely cancelled (4) endings are published to topic-bpm-process-result. They go only to processes linked to a business document (paramsId), and only one message is sent per rejection.
**Specification:**
  Given instance P for paramsId 900 is rejected (deletion fires CANCEL first, then REJECT)
  When  the CANCEL event arrives after commit
  Then  it is skipped because the ext result has become 3; the REJECT event publishes {businessKey, paramsId=900, result=3, reason, afterHookKey} with dedup key "PE:P"
  Given the initiator cancelled with a hook confirmation
  Then  the payload already persisted in the outbox (with cancelAction) is sent instead of a newly built one
**Parameters:** dedup key prefix "PE:"
**Edge cases handled:** processes with no bpm_oa_customized/paramsId are not published.
**Confidence:** High.

### RULE-18: Task-result messages (topic-bpm-approval-task-result)
**Category:** Policy
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/mq/producer/BpmApprovalTaskResultProducer.java:71-108`
**Plain English:** Each approve, reject or return on a document-linked process sends a task-result message after commit, carrying the rows it covers. When the node has an AFTER hook, the message and its approval snapshot are first saved to the outbox in the same transaction.
**Specification:**
  Given approver 2002 approves task T (node "PM", row-approval row R7) for paramsId 900
  Then  the message {businessKey = process key before ":", paramsId 900, taskId T, result 2, rowId "R7"} is sent after commit, with dedup key = taskId
  Given T is on a single-instance node and the process has a rowUnits variable
  Then  rowIds = the rows whose route reaches node "PM"; if those cannot be resolved, it is left empty and downstream falls back to "approval = assignee"
**Parameters:** none
**Edge cases handled:** non-document processes are skipped. Notify completion, return-to-node, withdraw and advisory co-sign rejections do not call this producer.
**Confidence:** High.

### RULE-19: Outbox redelivery, backoff and dead-letter
**Category:** Policy
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/mq/producer/BpmApprovalMqReliableSender.java:355-398`
**Plain English:** Failed workflow messages wait in psa_bpm_mq_outbox and are re-sent on a growing delay. After 10 failures a message becomes dead and is only logged.
**Specification:**
  Given an outbox row whose sends keep failing
  When  the drainer runs (every 30 s, up to 50 due rows, one node at a time under a lock)
  Then  attempts 1 to 9 reschedule after 30 s, 60 s, 120 s, 300 s, 600 s, then 1800 s each time; the 10th failure sets status=DEAD (about 2 h 18 min in total). A successful send marks "sent" (48 h TTL) and deletes the row
**Parameters:** BACKOFF_SECONDS={30,60,120,300,600,1800} (line 97), MAX_RETRY=10, drain-delay 30000 ms, sweep-delay 60000 ms, staging stale threshold 120 s, batch 50 (all hard-coded or yaml-defaulted)
**Edge cases handled:** a row already marked sent is deleted without resending. Messages stuck in Redis staging for more than 120 s are moved to the outbox. If Redis is down, the message is sent directly and goes to the outbox on failure.
**Suspected defect:** Rows written by persistTaskResult or persistProcessResult also go through asyncSend after commit. The drainer can pick a row up before markSent, so duplicate delivery is possible. Consumers must be idempotent.
**Confidence:** High.

### RULE-20: Reconcile job re-sends missed results from the bpm records
**Category:** Policy
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/mq/producer/BpmApprovalMqReliableSender.java:402-485`
**Plain English:** Every 10 minutes, any task approved or rejected, and any process approved, rejected or cancelled, between 24 hours and 2 minutes ago that was never sent, staged or queued is rebuilt from the bpm tables and queued.
**Specification:**
  Given bpm_task_ext row T has result=3 and end_time 30 minutes ago, with no sent marker or outbox row, and its process is linked to paramsId 900
  When  reconcile runs
  Then  a task-result message (result 3, reason, rowId from bpm_task_ext) is queued with error "reconcile: missing task send"
  Given bpm_process_instance_ext P has result=4 and end_time 1 hour ago, and was never sent
  Then  a process-result message "PE:P" (result 4, afterHookKey) is queued
**Parameters:** lookback 24 h, grace 120 s, interval 600000 ms, `psa.bpm.mq.reconcile-enabled` default true
**Edge cases handled:** non-document processes are skipped. The sent-marker TTL (48 h) is longer than the lookback.
**Suspected defect:** (a) Any task completed by the engine gets result=2 from RULE-02, even when nothing was published: initiator auto-skip, resubmit completion, same-approver auto-approve. The same applies to advisory co-sign rejections (RULE-06). Reconcile would emit these as "missed". (b) A rebuilt cancel message lacks cancelAction and reason if the original outbox payload is gone.
**Confidence:** Medium — SME/tech lead: should reconcile limit itself to tasks completed by a human decision?

### RULE-21: The task-result consumer routes by business key, and a hook replaces the handler
**Category:** Policy
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/mq/consumer/approval/ApprovalTaskResultConsumer.java:41-75`
**Plain English:** The business module applies each task result through the one handler registered for that process key, acting as the approver. If the message has an AFTER hook, only the hook runs. A failure is rethrown so that RocketMQ redelivers the message.
**Specification:**
  Given a message with businessKey "OPP_QUOTATION", no afterHookKey and assigneeId 2002
  Then  the OPP_QUOTATION handler runs with the operator set to 2002, and an exception causes redelivery
  Given the same message with afterHookKey "travelX"
  Then  only the workflow hook registry runs and the business handler is not called
**Parameters:** none
**Edge cases handled:** no handler and no hook → skip. Two handlers for the same key → startup failure.
**Suspected defect:** This is inconsistent with the process consumer (RULE-22), which runs both the hook and the handler.
**Confidence:** Medium — SME: is it intended that a node AFTER hook fully replaces the business handler?

### RULE-22: The process-end consumer runs the hook and then the handler, and must be idempotent
**Category:** Policy
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/mq/consumer/approval/ProcessResultConsumer.java:43-75`
**Plain English:** When a process ends, the configured AFTER hook (if any) runs first and then the business handler for that process key. Any failure triggers redelivery, so handlers must be idempotent per process instance.
**Specification:**
  Given the message {businessKey "OPP_QUOTATION", paramsId 900, result 2, afterHookKey blank}
  Then  the quotation handler writes the final state; on an exception the message is redelivered
**Parameters:** none
**Edge cases handled:** there is no default operator (the handler may override it). No handler → only the hook runs. Activity events are separately de-duplicated by messageId through an insert-if-absent log in the same transaction (`ActivityEventConsumer.java:50-60`).
**Confidence:** High.

### RULE-23: Asynchronous timesheet process creation (psa_process_to_be_sent)
**Category:** Policy
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/bpm/ProcessToBeSentServiceImpl.java:134-204`
**Plain English:** A submitted timesheet is queued and then starts its approval either immediately or through a job every 30 s. Once bpm accepts it, the timesheet moves to "审批中". After 5 failed attempts the row is marked failed and is no longer retried automatically.
**Specification:**
  Given row {businessId 77, actionType CREATE_OA, status 0, retry 0}
  When  the job picks it up (up to 100 per run, FIFO by id) and acquires the Redis lock "ptbs:send:77"
  Then  createOA is called as the row's creator; on "success", bpm deletes the row and timesheet 77 moves to ApproveStatus 1 (审批中) with operator type SYSTEM
  Given actionType CONTINUE_AFTER_RETURN
  Then  continueProcessAfterReturn is called and fst deletes the row itself
  When  a call fails
  Then  retry_count+1; status becomes 2 (failed) when the new count reaches 5, otherwise it stays 0
**Parameters:** MAX_RETRY=5, batch 100, lock prefix "ptbs:send:", status 0 = pending / 2 = failed, error text truncated to 500 characters
**Edge cases handled:** if the lock is not acquired, the row is skipped. The immediate attempt ignores errors and leaves them to the job.
**Suspected defect:** Comment lines 178-181 rely on a "compensation replay" in `CustomizedBpmOAServiceImpl.updateApproveStatus`, but that code was removed (#564), so the instant self-approval race described there is no longer compensated by that path.
**Confidence:** High for the mechanics; Medium for the race.

### RULE-24: Reliable delivery task lifecycle (psa_reliable_delivery_task)
**Category:** Lifecycle
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/platform/integration/reliabledelivery/ReliableDeliveryDispatcher.java:127-251`
**Plain English:** External deliveries (for example U8C voucher posting) go Pending → Sending (claimed with a 5-minute lease) → Success, or → Retrying with a growing delay, or → Dead once the retry budget is used up or the failure is marked non-retryable.
**Specification:**
  Given task 55 {status PENDING(0), maxRetryCount 5}
  When  the dispatcher claims it (CAS on id + version + status; up to 200 per run, by priority then id)
  Then  status=SENDING(1) and the lease ends at now+5 min
  When  the handler fails (retryable)
  Then  retries 1 to 4 → RETRYING(3) with next_retry_at = now + 1 m, 5 m, 30 m, 2 h; the 5th failure → DEAD(4)
  When  the handler returns terminalFailure
  Then  DEAD immediately
  When  it succeeds
  Then  SUCCESS(2) with the external receipt number
**Parameters:** CLAIM_LEASE 5 min; backoff 1 m, 5 m, 30 m, 2 h, then 6 h; batch 200 (`fst.platform.reliable-delivery.dispatch-batch-size`); statuses 0 to 5 (5 = CANCELLED)
**Edge cases handled:** SENDING rows with an expired lease are reclaimed. A late finish by a worker that lost its lease writes nothing. No registered handler → failure "NO_HANDLER". Manual retry and forced retry (forced retry is only allowed from DEAD and resets the count). A pending task can be cancelled only if it was never attempted. Travel versions are serialised behind non-terminal predecessors (`ReliableDeliveryTaskMapper.java:142-147`).
**Suspected defect:** A null maxRetryCount is treated as 0, so the first failure goes straight to DEAD.
**Confidence:** High.

### RULE-25: Notify tasks become unread to-do items until the detail page is opened
**Category:** Lifecycle
**Priority:** P2
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmTaskServiceImpl.java:1314-1347`
**Plain English:** A task on a notify node completes automatically with result "知会" (6) and sends no approval result. It appears in the user's to-do list as unread until they open the process detail.
**Specification:**
  Given a notify node assigns task T to user 4004
  When  the cascade completes T
  Then  ext result=6, no task-result message, and a notify websocket is sent after commit; T appears in 4004's to-do list (result=6 and read_time null, merged with live tasks, newest first)
  When  4004 opens the process detail
  Then  read_time is filled in and T disappears from the to-do list
**Parameters:** node attribute `知会节点`
**Edge cases handled:** a task that is already gone is skipped (idempotent). Bulk approval and task-assigned notifications skip notify nodes.
**Confidence:** High.

---
**Coverage:** I read in full BpmProcessInstanceServiceImpl, CustomizedBpmOAServiceImpl, BpmOAIdempotentStartService, WorkflowHookBeforeGate, BpmWithdrawnHistoryCleanupRunner, BpmApprovalTaskHookService, all three mq/producer classes, the top-level fst approval consumers and handler interfaces, both jobs, ProcessToBeSentServiceImpl and its mapper, the reliable-delivery dispatcher, listener, backoff, outcome and mapper, and the fst platform workflow classes. In BpmTaskServiceImpl I read 120-231 and 425-1539 and skimmed the rest; BpmOALeaveServiceImpl and the seed design docs were skimmed only.
**Possibly missed:** the Flowable event listeners outside the listed paths (initiator auto-skip with round awareness, same-approver auto-approve, task-created and activity-event outbox writers, `isReturnedToInitiator`), the per-business handlers under approval/budget|quotation|timesheet…, and the WorkflowHookRegistry.
**Other findings:** `CustomizedBpmOAServiceImpl.bpmProcessOver` (303-337) is only called by a listener scoped to the timesheet process key, and its timesheet branch writes nothing, so the generic approve_status write-back by table name (通过→3 / 驳回→2 / 取消→4) is dead code. `approveTaskByBatch` (429-466) approves every user's pending tasks as their assignees, a privileged path worth an authorization review. No instruction-shaped text or credentials were found in the code I read.