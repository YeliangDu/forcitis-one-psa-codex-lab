### RULE-01: Approvers are resolved from the node's assignment-rule type
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/definition/BpmTaskAssignRuleServiceImpl.java:476-616`
**Plain English:** Each approval node gets its candidate approvers from its configured rule type. The type can be a role, department members, department leader, post, named users, user group, script, form data field, or "empty node". The anti-self-approval policy (RULE-06) is applied to the result.
**Specification:**
  Given a node rule of type ROLE (10) with options [roleId 7], and role 7 is held by users {101, 102}
  When the node is entered
  Then the candidates are {101, 102}.
  Likewise: DEPT_MEMBER (20) gives all users in the listed departments. DEPT_LEADER (21) gives each listed department's `leaderUserId`. POST (22) gives all users holding the listed posts. USER (30) gives the listed user ids as-is. USER_GROUP (40) gives the union of group members. SCRIPT (50) gives the script output (RULE-08). PARAMS (60) gives values taken from form fields (RULE-07). EMPTY_NODE (70) gives {admin id 1}.
**Parameters:** Rule type codes 10/20/21/22/30/40/50/60/70; `CommonConstants.ADMIN_ID = 1`
**Edge cases handled:** An unknown type returns null, which is treated as "no candidate". Data permission is disabled for this calculation (`@DataPermission(enable=false)`).
**Suspected defect:** `removeDisableUsers` (lines 692-701) exists but nothing calls it, so disabled or deleted users can still be made approvers. For DEPT_LEADER, a department with no leader adds `null` to the candidate set. If the random pick (RULE-03) lands on that `null`, task creation fails with "任务处理人不能为空" (assignee must not be empty).
**Confidence:** High — explicit dispatch. SME: should disabled users be removed from candidates? The helper exists but is never wired in.

### RULE-02: Each node must have exactly one assignment rule
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/definition/BpmTaskAssignRuleServiceImpl.java:292-304`
**Plain English:** Assignment rules are looked up per (process definition, node). Finding no rule or more than one rule is a hard engine error.
**Specification:**
  Given process definition `quote:3:abc` whose node `deptApprove` has 0 rules (or 2 rules)
  When the token reaches `deptApprove`
  Then a FlowableException is raised ("找不到符合的任务规则" = no matching rule / "找到过多任务规则" = too many rules) and no task is created.
**Parameters:** Cardinality exactly 1
**Edge cases handled:** The row-approval, skip-when-empty and reapprove checks swallow this exception and treat the node as "not configured" (lines 283-288 and 309-315).
**Confidence:** High.

### RULE-03: A single-approver node picks one candidate at random
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/framework/flowable/core/behavior/BpmUserTaskActivityBehavior.java:73-100`
**Plain English:** On a non-multi-instance node, every task has exactly one owner. If the rule yields several candidates, one is chosen uniformly at random.
**Specification:**
  Given a single-instance node whose ROLE rule resolves to {101, 102, 103}
  When the task is created
  Then exactly one of 101, 102 or 103, chosen at random, becomes the assignee. The others get nothing.
**Parameters:** `RandomUtil.randomInt(size)`
**Edge cases handled:** On a multi-instance node, the assignee comes from the per-instance element variable instead (rowId for row approval, userId otherwise). After a restart the element-variable name is rebuilt from the same formula.
**Suspected defect:** Routing is non-deterministic. If a role or department is configured on a single-instance node, an arbitrary member becomes the sole approver.
**Confidence:** High. SME: is a random single approver acceptable, or should these nodes be required to resolve to exactly one person?

### RULE-04: Countersign/or-sign nodes create one task per distinct approver
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/framework/flowable/core/behavior/BpmParallelMultiInstanceBehavior.java:45-93`
**Plain English:** On a parallel multi-instance (MI) node, the deduplicated candidate set becomes the instance collection, so each person gets one task. Whether the node completes on "all", "any", or "rejected" is decided by the BPMN completion condition, except on row-approval nodes, which never short-circuit.
**Specification:**
  Given an MI node whose rule resolves to {201, 202, 203}
  When the node is entered
  Then 3 tasks are created (one each). The node leaves when the model's completion condition is met (for example all done, or `${rejected}`).
  Given the same node with skip-when-empty on and 0 candidates
  Then 0 instances are created and the node is passed through.
**Parameters:** Collection variable name from `FlowableUtils.formatCollectionVariable(activityId)`
**Edge cases handled:** Without skip-when-empty, an empty result throws `TASK_CREATE_FAIL_NO_CANDIDATE_USER`. On row-approval nodes the collection is row ids, not people (RULE-15).
**Confidence:** Medium — the completion conditions live in the BPMN XML stored in the database, not in code. SME/DBA: list the `completionCondition` of each deployed MI node (all-sign vs or-sign vs `||rejected`).

### RULE-05: "Skip when empty" bypasses a node that has no approver
**Category:** Policy
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/framework/flowable/core/behavior/BpmUserTaskActivityBehavior.java:40-60`
**Plain English:** If a node is flagged skip-when-empty and its approver cannot be resolved, the node is skipped silently. Without the flag, the process stops with an error.
**Specification:**
  Given a single-instance node with `skipWhenEmpty=true` whose DELIVERY_DEPT_AR script returns {} (the department has no AR)
  When the token arrives
  Then no task is created and the token leaves the node.
  Given the same node with `skipWhenEmpty=false`
  Then task creation fails with `TASK_CREATE_FAIL_NO_CANDIDATE_USER` (BpmTaskAssignRuleServiceImpl.java:446-455).
**Parameters:** Per-node flag `skip_when_empty`
**Edge cases handled:** Any exception during calculation (no rule, script NPE, RPC failure) counts as empty for the skip check (`calculateTaskCandidateUsersOrEmpty`, lines 459-469). The flag is read first, and candidates are only calculated if it is set.
**Suspected defect:** Swallowing exceptions means a misconfiguration (missing rule, or a leader-script NPE per RULE-09) is skipped silently. The approval is effectively waived without any error.
**Confidence:** High. SME: should technical errors on a skip-when-empty node really skip the approval?

### RULE-06: Anti-self-approval escalates to the initiator's direct leader
**Category:** Policy
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/definition/BpmTaskAssignRuleServiceImpl.java:504-550`
**Plain English:** For processes that opt in, if the initiator is among the resolved approvers, the initiator is replaced by their first-level leader. If there is no leader, or the leader is the initiator, the initiator stays. Nodes flagged as employee activities are exempt.
**Specification:**
  Given variable `approvalTaskSelfEscalation=true`, `creUserId=301`, `leaderUserId=401`, and node candidates {301, 302}
  When candidates are resolved
  Then the candidates become {302, 401}.
  Given `leaderUserId` is null
  Then the candidates stay {301, 302}.
**Parameters:** Variables `approvalTaskSelfEscalation`, `creUserId`, `leaderUserId`; node attribute `employeeActivity=true` exempts the node
**Edge cases handled:** Separate set and single-value (per-row) variants. Non-numeric ids are ignored. Only the travel starters call `applySelfEscalation` (TravelWorkflowHook, TravelApplicationApprovalStarterImpl, ExternalApprovalWorkflowPolicy, TravelApplicationRevisionApprovalStarterImpl). Every other process keeps the legacy behaviour, where the initiator can approve their own request.
**Suspected defect:** When other non-initiator candidates exist, the leader is still added. Combined with the random pick (RULE-03), the leader may become the approver even though a peer was available.
**Confidence:** High. SME: should escalation only happen when the initiator is the *sole* candidate?

### RULE-07: "Custom data" approvers come from form fields, with a hard-coded fallback person
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/definition/BpmTaskAssignRuleServiceImpl.java:665-689`
**Plain English:** For PARAMS (60) rules, the approvers are the user ids found in the configured form/variable fields. If none of those fields exist, one specific hard-coded user becomes the approver.
**Specification:**
  Given a PARAMS rule with options ["projectManagerId"] and process variables {projectManagerId: ["501","502"]}
  When resolved
  Then the candidates are {501, 502}.
  Given the variables contain no such field
  Then the candidates are {-3924403404092652719} (per the code comment, the IT admin 江勇超).
**Parameters:** Fallback user id `-3924403404092652719` (hard-coded; a person id, not a credential)
**Edge cases handled:** Values are deduplicated. Variables are read from the parent execution.
**Suspected defect:** The hard-coded personal fallback silently routes business approvals to one IT person. There is also a leftover `System.out.println` that dumps all process variables to stdout (line 673).
**Confidence:** High. SME: should a missing approver field fail, skip, or go to a configurable role?

### RULE-08: Script approvers can be driven by a configurable field, resolved per value and unioned
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/definition/BpmTaskAssignRuleServiceImpl.java:618-663`
**Plain English:** A SCRIPT rule may name a "script field". If that variable holds several values (for example several department ids collected from rows), each value is resolved separately and the approvers are unioned.
**Specification:**
  Given a SCRIPT rule with options [31] and `scriptField="deliveryDeptId"`, variable `deliveryDeptId=[11, 12]`, dept 11 leader 601, dept 12 leader 602
  When resolved
  Then the candidates are {601, 602}.
**Parameters:** Script ids 9/10/20/21/30/31/32/33
**Edge cases handled:** An unknown script id throws `TASK_ASSIGN_SCRIPT_NOT_EXISTS`. Scripts that cannot resolve by value (initiator or leader scripts return null) fall back to the execution-context version. Values are deduplicated in order.
**Confidence:** High.

### RULE-09: Initiator-leader scripts pick the 1st or 2nd reporting-line leader
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/framework/flowable/core/behavior/script/impl/BpmTaskAssignLeaderAbstractScript.java:39-73`
**Plain English:** Script 20 returns the initiator's `leaderUserId`. Script 21 returns that leader's leader. Script 10 (StartUserScript) returns the initiator.
**Specification:**
  Given initiator 701 whose leader is 702, and 702's leader is 703
  When script 20 runs, Then the result is {702}
  When script 21 runs, Then the result is {703}.
**Parameters:** level = 1 or 2; the user map comes from the cached all-users list
**Edge cases handled:** None. The department-hierarchy logic is commented out (lines 53-70).
**Suspected defect:** If the initiator has no leader, there is a NullPointerException at line 50, even for level 1. If the leader has no leader, level 2 throws an NPE at line 72. Combined with RULE-05, a skip-when-empty node is then silently skipped. Otherwise task creation crashes.
**Confidence:** High. SME: what should happen when the reporting line is incomplete: skip, escalate, or go to an admin?

### RULE-10: Department-leader script (31) resolves dept id to `dept.leader_user_id`
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/framework/flowable/core/behavior/script/impl/BpmTaskAssignDeliveryDeptLeaderScript.java:38-67`
**Plain English:** The department id is read from the configured field (default `deliveryDeptId`), and that department's leader is the approver.
**Specification:**
  Given `deliveryDeptId="15"` and dept 15 leader 801
  When resolved
  Then the result is {801}. A missing or unparsable id, a missing department, or a department with no leader gives {}.
**Parameters:** Default variable key `deliveryDeptId`
**Edge cases handled:** Numeric or string ids are accepted. The same resolver is used per row (RULE-17).
**Confidence:** High.

### RULE-11: Regional AR script (30) resolves dept id to `dept.ar_user_id`
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/framework/flowable/core/behavior/script/impl/BpmTaskAssignDeliveryDeptArScript.java:43-72`
**Plain English:** The regional AR approver is the user recorded on the department's AR relation (synced from A8). It is not role-based.
**Specification:**
  Given `deliveryDeptId=15` and dept 15 with `arUserId=802`
  When resolved, Then the result is {802}. If `arUserId` is null, the result is {} (and the node is skipped if skip-when-empty is on).
**Parameters:** Default variable key `deliveryDeptId`; source field `system_dept.ar_user_id`
**Edge cases handled:** A missing or invalid dept id gives {}.
**Confidence:** High.

### RULE-12: Department-director script (32) means holders of role BU-002 who sit directly in the department
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/framework/flowable/core/behavior/script/impl/BpmTaskAssignDeliveryDeptDirectorScript.java:74-96`
**Plain English:** Candidates are the intersection of all users holding role code `BU-002` ("BU项目总监", BU project director) and the users directly assigned to the department. Sub-departments and parent departments are not included.
**Specification:**
  Given role BU-002 users {901, 902} and dept 15 users {902, 903}
  When resolved, Then the result is {902}.
**Parameters:** `ROLE_CODE_DIRECTOR = "BU-002"`; default variable key `deliveryDeptId`
**Edge cases handled:** No role holders gives {}. A director outside the department gives {}.
**Confidence:** High. SME: should a director attached to a parent department count?

### RULE-13: Form-project-manager script (9): the field value is the approver
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/framework/flowable/core/behavior/script/impl/BpmTaskAssignFormProjectManagerScript.java:35-63`
**Plain English:** The configured field holds user ids directly. Each distinct value is an approver, so several values produce a countersign.
**Specification:**
  Given `scriptField="managerId"` and variable `managerId=[1001, 1002]`
  When resolved, Then the result is {1001, 1002}.
**Parameters:** —
**Edge cases handled:** No script field configured gives {} (with a warning). Negative snowflake ids are allowed.
**Confidence:** High.

### RULE-14: Form-user-leader script (33): the field value's first-level leader is the approver
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/framework/flowable/core/behavior/script/impl/BpmTaskAssignFormUserLeaderX1Script.java:40-84`
**Plain English:** The configured field holds a user id (for example the project manager). That user's `leader_user_id` is the approver.
**Specification:**
  Given `scriptField="managerId"`, `managerId=1001`, and user 1001's leader is 1101
  When resolved, Then the result is {1101}. If 1001 has no leader, or does not exist, the result is {}.
**Parameters:** —
**Edge cases handled:** Collection values are unioned. A blank field gives {}.
**Confidence:** High.

### RULE-15: A row-approval node requires row units, and the row set is narrowed by routing
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/definition/BpmTaskAssignRuleServiceImpl.java:319-340`
**Plain English:** A row-approval node creates one task per business row that can reach this node. If the submission carries no row units at all, the system fails loudly rather than approving with no reviewer.
**Specification:**
  Given a node with `rowApproval=true` and `rowUnits` absent
  When the node is entered
  Then `PROCESS_INSTANCE_ROW_APPROVAL_NO_ROW_UNITS` is raised.
  Given rowUnits for rows r1, r2, r3, where only r1 and r3 route to this node
  Then 2 tasks are created (r1, r3).
  Given 0 rows route to the node
  Then 0 tasks are created and the node is legitimately skipped.
**Parameters:** Variable `rowUnits`; key `BpmRowApprovalConstants.ROW_ID_KEY`
**Edge cases handled:** Rows with a null id are dropped.
**Confidence:** High.

### RULE-16: Row reachability: a row reaches a node if its routing value activates a path to it
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/framework/flowable/core/util/BpmProcessGraphUtils.java:118-153`
**Plain English:** For each row, a breadth-first search runs from the start event. A flow is active when it has no condition, when the row's value matches `containsAny('key', values...)`, or when the condition cannot be parsed (counted as active to be safe). Rows reaching the node are kept, deduplicated by row id.
**Specification:**
  Given a gateway flow `containsAny('manHourType', '2')` leading to node `areaLeader`, row r1 with manHourType=[2] and row r2 with manHourType=[1]
  When rows(areaLeader) is computed
  Then only r1 is included.
**Parameters:** Condition regex `containsAny\(\s*'([^']+)'\s*,\s*([^)]*)\)`; activation semantics at lines 291-312
**Edge cases handled:** A null model leaves the rows unnarrowed. Rows with the same routing signature are memoized. A routing key with no value means the flow is inactive. Sub-processes are treated as reachable unconditionally (over-approximation).
**Confidence:** High.

### RULE-17: Per-row approver resolution
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/definition/BpmTaskAssignRuleServiceImpl.java:343-426`
**Plain English:** For each row task, the approver is found as follows. PARAMS rules use the row's own field value. SCRIPT rules with a script field run the script on the row's value (for example the row's department leads to that department's leader). All other types use one node-level approver for every row. Self-escalation (RULE-06) is then applied.
**Specification:**
  Given a SCRIPT 31 rule with `scriptField=deptId`, row r1 {deptId:15 → leader 801} and row r2 {deptId:16 → leader 805}
  When the row tasks are created
  Then r1 is assigned to 801 and r2 to 805.
  Given a ROLE rule
  Then all rows go to the first candidate of the node-level set.
**Parameters:** —
**Edge cases handled:** A blank row id, a missing rule, a missing row, or a missing value gives a null assignee. A configured script field is authoritative, with no fallback to node level. Null then makes task creation fail (`Assert.notNull`).
**Suspected defect:** For non-row-specific types the "first" element of a HashSet is taken, so the choice is arbitrary but deterministic per JVM. This differs from the single-instance random pick (RULE-03).
**Confidence:** High.

### RULE-18: Row approval: any rejected row rejects the whole submission, but all rows still finish
**Category:** Lifecycle
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmTaskServiceImpl.java:1068-1098`
**Plain English:** A row-approval node never completes early. It waits until every row task is done (BpmParallelMultiInstanceBehavior.java:87-93). If an approver rejects any row, the process-level `rejected` flag is set before the tasks are completed, so the downstream gateway sends the document back to the initiator. Each row's own result is still published individually.
**Specification:**
  Given approver 801 holds rows r1 and r2 on node N and decides r1=APPROVE, r2=REJECT
  When submitted
  Then `rejected=true` is set, both tasks complete, result MQ messages go out per row (r1 approved, r2 rejected), and once all of N's rows are done the gateway routes to the reject/return path.
**Parameters:** Variable `rejected`
**Edge cases handled:** Other approvers' rows on the same node remain open until they act.
**Suspected defect:** Unlike the per-person path (`dispatchSingleDecision`, lines 982-1006), this path does not check whether a row task was added by countersign. A countersign participant's rejection on a row node would set `rejected=true` and carry real veto power, which contradicts the advisory-reject design.
**Confidence:** Medium — depends on how the BPMN gateway is drawn. SME: confirm that every row-approval node is followed by a `${rejected}` gateway.

### RULE-19: Row tasks must be decided all at once by their approver
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmTaskServiceImpl.java:1042-1053`
**Plain English:** The set of row tasks an approver submits for a node must exactly equal all of their pending tasks on that node. Partial or duplicate submissions are refused. Single-task approve or reject on a row node is also refused (lines 775-777 and 805-807).
**Specification:**
  Given approver 801 has 3 pending row tasks on node N
  When they submit decisions for 2 of them
  Then `TASK_DECIDE_FAIL_INCOMPLETE(3, 2)` is raised and nothing is committed.
**Parameters:** —
**Edge cases handled:** Duplicate task ids are caught by the size check. Batch "approve all" skips row nodes (lines 443-448).
**Confidence:** High.

### RULE-20: The to-do list merges row tasks into one item per (instance, node)
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmTaskServiceImpl.java:189-209`
**Plain English:** An approver with several row tasks for the same document and node sees a single to-do, represented by the first task (newest first). The to-do count is the number of approval actions, not the number of tasks. Unread notify items are added, sorted by create time descending, and paginated in memory.
**Specification:**
  Given user 801 has row tasks t1, t2, t3 (instance P1, node N) and task t4 (instance P2, non-row)
  When the to-do page is fetched
  Then 2 items are returned (t1 for P1/N, t4) plus any unread notify items, and total = 2 + notify count.
**Parameters:** —
**Edge cases handled:** The row-approval lookup is memoized per (definition, node). The admin all-users to-do page (lines 403-427) does not merge.
**Confidence:** High.

### RULE-21: Unified decide: same instance only, reject reason required, rejecting nodes processed last
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmTaskServiceImpl.java:865-975`
**Plain English:** An approver can decide several of their tasks in one submission. The rules are:
- All tasks must belong to the same instance.
- Each result must be APPROVE or REJECT.
- A rejection needs a reason unless the node has a BEFORE hook.
- Non-row nodes accept exactly one decision.
- Every node's BEFORE hook must pass before anything is committed.
- Node groups containing a reject are executed last.
**Specification:**
  Given decisions for node A (approve) and node B (reject, reason "price wrong") in instance P1
  When submitted
  Then the hooks for A and B are evaluated first. If both pass (outcome allow or not_applicable), A is completed, then B.
  Given a reject with a blank reason on a node without a hook
  Then `TASK_DECIDE_FAIL_REASON_REQUIRED` is raised.
**Parameters:** Hook outcomes ALLOW / NOT_APPLICABLE commit; warn, reject, supplement or a null result block (WorkflowHookBeforeGate.java:31-38)
**Edge cases handled:** A cross-instance submission gives `TASK_DECIDE_FAIL_CROSS_INSTANCE`. A hook can write process variables on allow (BpmApprovalTaskHookService.java:125-129). Hook keys must be registered, otherwise `WORKFLOW_HOOK_NOT_REGISTERED` (WorkflowHookRegistry.java:52-58).
**Confidence:** High.

### RULE-22: A reject at top level ends the process; a reject inside a sub-process returns to the initiator
**Category:** Lifecycle
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmTaskServiceImpl.java:802-844`
**Plain English:**
- On a node at the top level, a reject deletes the process instance (final rejection).
- On a node inside a sub-process, a reject is turned into "return to initiator": `rejected=true` is set and the task is completed, so BPMN error boundaries send the document back while the main process stays alive.
**Specification:**
  Given task T on a top-level node and a reject with reason "no budget"
  When rejected
  Then a REJECT result MQ message is published, the instance is deleted with result rejected, and the task ext row records REJECT.
  Given T sits in a sub-process (for example timesheet approval)
  Then `rejected=true` is set, T is completed, and the initiator's task is recreated.
**Parameters:** Variable `rejected`
**Edge cases handled:** Return-to-initiator requires a non-blank reason (lines 1117-1123). Return-to-specific-node is only allowed from single-instance nodes, to previously completed single-instance nodes, excluding the timesheet initiator node (lines 1155-1192).
**Confidence:** High.

### RULE-23: Added countersign participants only give advisory rejections
**Category:** Policy
**Priority:** P1
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmTaskServiceImpl.java:1242-1295`
**Plain English:** Extra approvers can only be added to a running parallel multi-instance node, one instance per distinct user, and each added task is flagged as countersign. A rejection from a flagged task only records an opinion. It does not end the process or emit a result MQ message (lines 982-1006 and 1037-1040). The node still waits for all instances.
**Specification:**
  Given instance P1 with a running MI node `review` and add-sign users [1201, 1201, null]
  When added
  Then 1 task is created for 1201 with `countersign=1`. If 1201 rejects, the task completes with REJECT recorded and the process continues.
**Parameters:** —
**Edge cases handled:** Duplicate and null users are removed. A non-MI node gives `COUNTERSIGN_ADD_FAIL_NODE_NOT_MI`. Under the duplicate rule (RULE-25a), a user who already approved on that node is auto-approved.
**Suspected defect:** There is no guard against adding countersign to a row-approval node. The element variable would be set to a user id but read as a row id (BpmUserTaskActivityBehavior.java:84-87). The added task then gets a null assignee, or the node-level approver instead of the requested person (RULE-17).
**Confidence:** Medium. SME: should add-sign be blocked on row-approval nodes?

### RULE-24: Auto-complete cascade order after each task creation
**Category:** Policy
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/framework/flowable/core/listener/BpmTaskEventListener.java:287-331`
**Plain English:** After commit, the system repeatedly scans the instance's active tasks and auto-completes them, checked in this order:
1. Notify nodes: closed as "notified".
2. Initiator nodes: skipped on first submission. On a rework round (`rejected=true`, or the node already has completed history) the task stops for the initiator.
3. Duplicate approver on the same node (RULE-25).
4. "Skip initiator approval" nodes where the approver equals the initiator: approved.
5. Nodes named "空节点" (empty node): approved as admin.
**Specification:**
  Given a node with attribute 跳过发起人审批 (skip-initiator-approval) whose assignee 301 is the initiator
  When the task is created
  Then it is auto-approved by 301 with the reason "系统自动通过" (system auto-approved).
**Parameters:** `CASCADE_MAX_ROUNDS = 50`; reason prefix "系统自动"; `ADMIN_ID = 1`
**Edge cases handled:** A task that fails is not retried in the same cascade. Row tasks use the row-level auto-approve path. Notifications for tasks created during the cascade are re-sent once it settles.
**Confidence:** High.

### RULE-25: Repeat approver on the same node is auto-approved, except on rework or "reapprove on return"
**Category:** Policy
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/framework/flowable/core/listener/BpmTaskEventListener.java:334-409`
**Plain English:**
- **(a)** If the same person already approved this node in this instance, a new task for them on that node is auto-approved. This does not apply to rework-entry nodes or nodes flagged `reapprove_on_return`.
- **(b)** If they hold several open tasks on the same node, only the earliest is kept and the rest are auto-approved.
- **Row nodes:** only the same row id previously approved by the same person fast-forwards.
- Approvals on *other* nodes never count. Nodes are matched by node key, or by name for legacy rows.
**Specification:**
  Given approver 801 approved node `deptApprove` in P1, and after a return the node is re-entered with a new task for 801
  When created
  Then it is auto-approved ("系统自动同意：同一实例重复审批人", system auto-approved: repeat approver in same instance), unless the node rule has `reapproveOnReturn=true`.
  Given 801 approved node `pmApprove` and now gets a task on `leaderApprove`
  Then 801 must approve it manually.
**Parameters:** Rule flag `reapprove_on_return`
**Edge cases handled:** If the rule lookup fails, the node is treated as not configured. If parsing the model fails, it is treated as a non-rework node. A changed row id requires manual approval.
**Suspected defect:** Row data changes are not detected. A row that was edited but kept its row id is auto-approved after resubmission.
**Confidence:** High. SME: is fast-forwarding an edited row with an unchanged id acceptable?

---
**Coverage:** Read fully: BpmTaskAssignRuleServiceImpl, BpmUserTaskActivityBehavior, BpmParallelMultiInstanceBehavior, all `script/impl/*`, BpmTaskEventListener, BpmProcessGraphUtils, BpmApprovalTaskHookService, WorkflowHookBeforeGate, F/framework/workflowhook/WorkflowHookRegistry + WorkflowHook, F/api/workflowhook/WorkflowHookApiImpl. Read in part: BpmTaskServiceImpl (lines 140-470 and 686-1538), BpmModelServiceImpl deploy checks (165-224; rules must be configured on every non-empty node; row-approval topology must use a single routing key in standard `containsAny` form; notify and row approval are mutually exclusive — no card written for these, by budget), and seed docs by grep only. Not read: BpmProcessInstanceServiceImpl, BpmProcessDefinitionServiceImpl, BpmUserGroupServiceImpl, form and field-permission services, the codegen hook dict runners, BpmWithdrawnHistoryCleanupRunner.
**Gaps:** I found no amount or approval-level thresholds, and no timeouts or reminders, anywhere in these paths. Amount-based routes and MI completion conditions (all-sign vs or-sign, `||rejected`) live in BPMN gateway and loop expressions stored in the database plus fst-side routing variables (for example WorkflowRowVariableBuilder and the travel/quote starters). Extracting them needs a DB dump of the deployed models. No instruction-shaped or manipulative text was found in the analysed sources.