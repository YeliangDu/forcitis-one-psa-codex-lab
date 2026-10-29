### RULE-01: Only DRAFT or REJECTED quotations can be edited, submitted or deleted
**Category:** Lifecycle
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/presales/quote/quotation/Quotation.java:532-540`
**Plain English:** A quotation can be changed, sent for approval or deleted only while it is a draft or after it was rejected. Once it is in approval (PENDING) or approved (APPROVED), it is read-only.
**Specification:**
  Given a quotation with stored status `PENDING` (not returned to the initiator) or `APPROVED`
  When the owner saves, submits or deletes it
  Then the action is refused with error 1002031001 "报价单已提交或已通过（仅草稿/驳回可编辑），不允许该操作"; with status `DRAFT` or `REJECTED` the action is allowed
**Parameters:** Stored status values (`QuotationStatus`, dict `quotation_status`): `DRAFT`=草稿, `PENDING`=OA 审批中, `APPROVED`=已通过, `REJECTED`=OA 驳回. Editable set = {DRAFT, REJECTED}.
**Edge cases handled:** A PENDING quotation whose workflow was sent back to the initiator can be edited and resubmitted (RULE-12), but not deleted. Save, submit and delete all use the same check.
**Suspected defect:** The save path writes to the database with no status condition (`QuotationRepositoryImpl.saveEditorContent`, lines 89-150, plain `updateById` plus delete-and-reinsert of child rows). Submit (`updateStatusIf`), delete and approval adoption all re-check status in the database (compare-and-set), but save does not. If a save races with a submit or an approval decision, it can overwrite the lines, cost tree and seals of a quotation that is already PENDING or APPROVED.
**Confidence:** High — the check is explicit. The race is inferred from the absence of a status condition.

### RULE-02: Conditions for submitting a quotation for approval
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/presales/quote/quotation/Quotation.java:265-279`
**Plain English:** A quotation can be submitted only if it has a name and a final price above zero. Submitting sets it to PENDING, records the submission time and clears the previous rejection reason.
**Specification:**
  Given a DRAFT quotation named "幕墙顾问报价" with final amount ¥106,000.00 and an old reject reason "价格偏高"
  When the owner submits it
  Then status = `PENDING`, submitTime = now, rejectReason = null, and the old process instance id is cleared (unless the workflow was returned to the initiator)
  Given the same quotation with a blank name, or a final amount of ¥0.00
  When submitted
  Then it is refused (SUBMIT_NAME_REQUIRED / SUBMIT_AMOUNT_INVALID "最终报价必须大于 0")
**Parameters:** Final amount must be > 0. A name made only of spaces counts as blank.
**Edge cases handled:** A null final amount is treated as invalid. On a resubmit after return-to-initiator, the existing process instance id is kept.
**Suspected defect:** The aggregate's comment says the new instance id is "由终态消息补写" (written back by the final-result message). The code actually binds it straight after the workflow starts (see RULE-03), so the comment is out of date.
**Confidence:** High

### RULE-03: Submission order: refresh costs, check all gates, compare-and-set, then start OPP_QUOTATION as one transaction
**Category:** Lifecycle
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/presales/quote/SubmitQuotationUseCase.java:36-89`
**Plain English:** On submit, the system recalculates costs from current master-data rates. It then checks, in this order: editable state, name/amount, seals, clause split, and (for BU users) the low-margin reason. Only one concurrent submit can win, and the approval workflow must start successfully or the whole submit is undone.
**Specification:**
  Given the owner double-clicks "提交" on a DRAFT quotation
  When two requests arrive at the same time
  Then only the first database update (`WHERE status = 'DRAFT'`) succeeds. The second gets QUOTATION_STATUS_CHANGED. The winner stores the refreshed cost summary and cost tree, starts BPM process `OPP_QUOTATION` (title "报价审批-{code}（{quoteName}）"), and binds the returned process instance id.
  Given BPM returns a failure
  When submitting
  Then QUOTATION_WORKFLOW_START_FAILED is raised and the status change is rolled back (the quotation stays DRAFT/REJECTED)
**Parameters:** Process key `OPP_QUOTATION`. Workflow variables `deliveryDeptId` (scalar, drives the candidate scripts DELIVERY_DEPT_AR/DELIVERY_DEPT_LEADER) and `needSeal` (Boolean; null is treated as false). Idempotency key per start round.
**Edge cases handled:** Users with cost-edit permission are rate-checked strictly. Users without it get the lenient draft check (RULE-17). The low-margin check runs only for users with cost-edit permission. Existing drafts get their rates refreshed at submit time.
**Confidence:** High

### RULE-04: Clause amounts must add up to the final quoted price
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/presales/quote/quotation/Quotation.java:334-356`
**Plain English:** The contract clauses in the cost estimate are a complete split of the price. Every clause needs a positive amount, the clause amounts must total exactly the final price, and a quotation cannot be submitted without at least one clause.
**Specification:**
  Given final amount ¥106,000.00 and clauses 预付款 ¥30,000.00 + 方案阶段 ¥76,000.00
  When submitted
  Then it passes
  Given clauses ¥30,000.00 + ¥70,000.00 (total ¥100,000.00)
  When submitted
  Then it is refused with SUBMIT_CLAUSE_TOTAL_MISMATCH "所有条款金额合计必须等于最终报价"
  Given a clause with amount null or ¥0
  Then it is refused with SUBMIT_CLAUSE_AMOUNT_REQUIRED, naming that clause's node number
  Given no clause nodes at all
  Then the submit path refuses with SUBMIT_CLAUSE_REQUIRED; the approval-adoption path lets it through
**Parameters:** The match must be exact (`compareTo == 0`, no tolerance). Each clause amount must be > 0. `requireClause` = true on submit, false on approval adoption.
**Edge cases handled:** On approval adoption the check runs again because a change to the tail adjustment changes the final amount (refs #1335).
**Suspected defect:** A comment in `SubmitQuotationUseCase.java:60` says "合计 ≤ 最终报价" (total ≤ final price), but the code requires the two to be equal.
**Confidence:** High

### RULE-05: A low-margin reason is required when estimated gross margin is below 50%
**Category:** Policy
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/presales/quote/quotation/Quotation.java:282-298`
**Plain English:** If the margin, calculated before tax, is below 50%, the user must explain why before the quotation can go forward.
**Specification:**
  Given final amount ¥106,000.00, tax rate 6% (amount before tax = ¥100,000.00) and BU cost ¥60,000.00, so gross margin = 0.4000
  When a user with cost-edit permission submits, or an approver adopts cost or fee changes, and lowGmReason is blank
  Then it is refused with error 1002031017 "预估毛利率 40.0% 低于 50%，请填写低利润率原因阐述"
**Parameters:** `LOW_GROSS_MARGIN_THRESHOLD = 0.50`, which must match the front-end constant `LOW_GM_THRESHOLD`. Margin = (amount before tax − BU cost) ÷ amount before tax, to 4 decimals, rounded half-up. The percentage in the message is shown to 1 decimal.
**Edge cases handled:** No check when the margin is null (final amount ≤ 0). Not checked when sales (no cost-edit permission) submit, or when an approval node did not adopt cost or fees.
**Confidence:** High

### RULE-06: Seal registration must be complete when "需要用印" is ticked
**Category:** Validation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/presales/quote/quotation/Quotation.java:305-319`
**Plain English:** If the quotation needs a company seal, at least one seal request must be registered. Each request needs a document name, the company that owns the seal, the seal type, original vs. copy, and the number of copies.
**Specification:**
  Given needSeal = true and no seal rows
  When submitted
  Then it is refused with SUBMIT_SEAL_REQUIRED
  Given needSeal = true and row 2 has copies = 0
  Then it is refused with SUBMIT_SEAL_INCOMPLETE, reporting row "2"
**Parameters:** Row is complete only if (see `QuotationSeal.isComplete`, lines 35-41): docName is not blank; the seal company code or name, the seal type code or name, and the copy type code or name are each present; copies ≥ 1.
**Edge cases handled:** When needSeal is false or null, leftover seal rows are ignored.
**Confidence:** High

### RULE-07: Server-side checks on monetary inputs
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/presales/quote/quotation/Quotation.java:470-524`
**Plain English:** Tax rates must be at least 0% and below 100%. Quantities, prices, hours, rates, line amounts and the 8 other-cost categories cannot be negative. The cost tree can be at most 3 levels deep.
**Specification:**
  Given tax rate 1.00, a line unit price of −¥500, a work package with −8 hours, or travel cost −¥200
  When saved, refreshed at submit time, or adopted in approval
  Then it is refused with INPUT_INVALID, naming the field (e.g. "差旅费不能为负数")
  Given tailAdjust = −¥1,000.00
  Then it is accepted (discounts go through the tail adjustment)
**Parameters:** Tax rate allowed range [0, 1). Maximum tree depth 3 (clause → deliverable → work package). The 8 cost categories: 安评, 光评, 擦窗机, 施工图签, 其他委外, 信息服务费, 差旅费, 打印费.
**Edge cases handled:** Null values are skipped. A per-line tax rate is checked against the same range. If no tax rate is given, the current tax rate is used, then the default 0.06.
**Confidence:** High

### RULE-08: How BPM final results map to quotation outcomes
**Category:** Lifecycle
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/mq/consumer/approval/quotation/QuotationProcessResultHandler.java:34-52`
**Plain English:** When the OPP_QUOTATION workflow finishes, "approved" marks the quotation APPROVED. Both "not approved" and "cancelled" mark it REJECTED. Any other result changes nothing.
**Specification:**
  Given a PENDING quotation and a BPM message with result = 2 (通过)
  Then the quotation becomes APPROVED
  Given result = 3 (不通过) or 4 (已取消)
  Then the quotation becomes REJECTED
  Given result = 1 (in progress)
  Then nothing is written back
**Parameters:** businessKey `OPP_QUOTATION`. BPM result codes 通过=2, 不通过=3, 已取消=4 (`CommonConstants.BpmProcessInstanceResult`).
**Edge cases handled:** A message with a null paramsId is ignored.
**Suspected defect:** A cancellation (e.g. the initiator withdraws) is stored as REJECTED, labelled "OA 驳回", and, if no comment was given, with reason "审批未通过". Users and reports cannot tell a withdrawal from a real rejection.
**Confidence:** Medium — mapping is explicit. SME question: "When an approval is cancelled or withdrawn, should the quotation show as 'OA 驳回 / 审批未通过', or go back to DRAFT or a separate CANCELLED state?"

### RULE-09: Approve/reject decisions are idempotent and ignore stale messages
**Category:** Lifecycle
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/presales/quote/DecideQuotationUseCase.java:32-75`
**Plain English:** A final-result message changes a quotation only if it is still PENDING, the message belongs to the current workflow instance, and the message is not older than the latest submission. A rejection always carries a reason.
**Specification:**
  Given a quotation PENDING since 2026-10-10 09:00 with instance "PI-B"
  When a rejection message arrives for instance "PI-A", or with messageTime 2026-10-09 18:00
  Then it is silently ignored (no error, no retry)
  When a rejection for "PI-B" arrives with an empty comment
  Then status = REJECTED, rejectReason = "审批未通过", processInstanceId = "PI-B"
  When an approval for "PI-B" arrives
  Then status = APPROVED and rejectReason is cleared (`Quotation.approve`, lines 445-449)
**Parameters:** Default reject reason `"审批未通过"`. Database update condition `WHERE status='PENDING'`.
**Edge cases handled:** Quotation missing, already decided, or updated concurrently: no-op (avoids messages being retried forever). The instance check is skipped when the stored instance id is null. Rejection reasons are trimmed.
**Confidence:** High

### RULE-10: Approvers can adopt header fields only while PENDING, with required-field and cost checks
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/presales/quote/ApplyQuotationApprovalUseCase.java:34-110`
**Plain English:** During approval, a node can overwrite the name, seal flag, tax rate, tail adjustment, note, low-margin reason, cost tree and other costs. Fields the node marks as required must end up non-empty. If the node requires cost, the BU cost must be above zero.
**Specification:**
  Given a PENDING quotation with buCost ¥0 and a node configured with costRequired = true
  When the regional head adopts a cost tree whose hours are all 0
  Then it is refused with QUOTATION_APPROVAL_COST_REQUIRED
  Given required key "quoteNote" with no adopted value and no stored value
  Then it is refused with WORKFLOW_APPROVE_REQUIRED_FIELD_EMPTY("quoteNote")
  Given the quotation was approved while the approver was editing
  Then the database update matches 0 rows and QUOTATION_STATUS_CHANGED is raised
**Parameters:** Header fields that can be adopted: quoteName, needSeal, taxRate, tailAdjust, quoteNote, lowGmReason. Cost required means buCost > 0.
**Edge cases handled:** An empty `costNodes` array means "no cost adopted" (protects against a front-end load failure wiping the tree, refs #1378). A stored value satisfies a required field. Required keys outside the header fields are not checked.
**Suspected defect:** This use case does not check that the caller is the current approver of an active task. That check may be done upstream in the workflow layer; I did not verify it.
**Confidence:** Medium — logic is explicit. SME question: "Is node-level authorization (caller is the current task assignee) enforced before this adoption endpoint is reached?"

### RULE-11: Contract clauses are locked during approval
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/presales/quote/ApplyQuotationApprovalUseCase.java:116-142`
**Plain English:** Once submitted, the clause split is part of what is being approved. Approvers may change deliverables and work packages, but cannot add, remove or change clauses.
**Specification:**
  Given stored clauses {Q-1 "预付款" ¥30,000, Q-2 "方案" ¥76,000}
  When an approver submits a tree with Q-2 changed to ¥70,000, a third clause, or a clause with no code
  Then it is refused with error 1002035023 "审批中不可增删条款或修改条款标题/金额…"
**Parameters:** Clauses are matched by clauseCode. Name, clauseType and amount must be equal (amounts compared numerically).
**Edge cases handled:** Order does not matter. Null amounts are equal only if both sides are null.
**Confidence:** High

### RULE-12: A quotation returned to the initiator stays PENDING but can be edited and resubmitted
**Category:** Lifecycle
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/infrastructure/presales/quote/QuotationApprovalStarterImpl.java:93-108`
**Plain English:** If an approver sends the workflow back to the initiator, the business status stays PENDING. The initiator may still save and resubmit, and the same workflow instance continues (same process number, history kept). The quotation cannot be deleted in this state.
**Specification:**
  Given a PENDING quotation whose latest BPM instance is sitting at the initiator node
  When the owner saves or submits
  Then the edit is allowed, and on resubmit the existing processInstanceId is kept
  Given BPM cannot be reached or there is no instance record
  Then the system treats it as not returned, and editing is refused
**Parameters:** Uses the latest `CustomizedBpmOA` record for paramsId.
**Edge cases handled:** Fails closed on errors. The flag lives only in memory for one request (`Quotation.markReturnedToInitiator`, lines 543-545). The delete path never sets it.
**Confidence:** High

### RULE-13: Only the applicant can save, submit or delete a quotation
**Category:** Policy
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/presales/quote/QuotationAccessGuard.java:66-71`
**Plain English:** Being on the visible sales team lets a person see a quotation but not change it. Only the person who created it can save, submit or delete it.
**Specification:**
  Given a quotation with applyUserId = 1001 and visibleTeam ["1002"]
  When user 1002 saves, submits or deletes it
  Then it is refused with QUOTATION_NOT_OWNER
**Parameters:** None.
**Edge cases handled:** Super-admins get no exemption.
**Suspected defect:** The delete endpoint requires only the `fst:quotation:query` permission (`QuotationController.java:212-222`, by design per refs #1509), so any query-role user who owns a draft can delete it. Note this when mapping roles.
**Confidence:** High

### RULE-14: Who sees which quotations in lists and stats
**Category:** Policy
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/infrastructure/presales/quote/QuotationQueryImpl.java:285-291`
**Plain English:** Users with "view all" permission see every quotation. Everyone else sees only quotations they created or where they are on the visible sales team.
**Specification:**
  Given user 12 without `fst:quotation:view-all`
  When listing quotations or viewing the stat cards
  Then only rows with applyUserId = 12 OR visible_team containing `"12"` are included (the quotes stop 12 from matching 123)
**Parameters:** Permission `fst:quotation:view-all`.
**Edge cases handled:** If the permission check fails, the user is treated as restricted (`QuotationAccessGuard.resolveViewer`). The detail view is not covered by this rule: it requires query permission or being a participant in this quotation's approval.
**Confidence:** High

### RULE-15: Sales users without cost-edit permission cannot change BU cost fields
**Category:** Policy
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/presales/quote/SaveQuotationUseCase.java:51-164`
**Plain English:** Sales users can add, remove and rename clauses, deliverables and work packages, and change clause amounts. For existing work packages, the role, hours and rates stay as stored. New work packages are saved with no cost data, and the 8 other-cost fields keep their stored values.
**Specification:**
  Given a user without `fst:quotation:cost-edit`, and stored work package id 77 with role T2 and 40 h
  When they save a tree where work package 77 shows 10 h and a new package shows 100 h
  Then work package 77 keeps T2 and 40 h, the new package has no role, hours or rates, and otherCosts is not changed
  Given a payload that is not empty but has no clause nodes
  Then the whole stored tree is kept
**Parameters:** Permission `fst:quotation:cost-edit` (business rule: BU project directors have it, marketing roles do not).
**Edge cases handled:** If the permission check fails, the user is treated as having no permission. An empty array is a legitimate "clear all". A new clause (no code, e.g. created from a template) keeps its whole submitted branch, including cost fields. Top-level nodes that are not clauses are dropped. The city category is accepted from sales.
**Suspected defect:** New clauses from sales keep the submitted cost fields (hours) in full, so a sales user can still set hours by creating a new clause instead of editing an existing one. This is allowed on purpose per refs #1515, but confirm.
**Confidence:** Medium — SME question: "Should hours on work packages under a sales-created new clause be kept, or cleared for BU to fill in?"

### RULE-16: Server master data is the only source of position rates; strict check at submit
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/presales/quote/QuotationCostRateResolver.java:38-68`
**Plain English:** Bill and cost rates sent by the client are always replaced by current master-data rates for the quotation's city. On submit by BU, or on approval adoption, every work package must have a role, and every role must have exactly one current, positive bill rate and cost rate.
**Specification:**
  Given city category "C1" and a work package with role "T3" that has no current cost rate in C1
  When a BU user submits
  Then it is refused with QUOTATION_POSITION_RATE_UNAVAILABLE "岗位（T3）缺少当前城市的计费费率或成本费率"
  Given a work package with no role on submit
  Then it is refused ("存在未选择岗位的工作包"); on draft save it is kept with rates cleared
  Given two current rates for the same role
  Then it is refused ("存在重复生效费率")
**Parameters:** Both rates must be > 0.
**Edge cases handled:** A null city means rates are not filtered by city (legacy quotations).
**Confidence:** High

### RULE-17: Starting a quotation from an opportunity
**Category:** Validation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/presales/quote/CreateQuotationDraftUseCase.java:36-63`
**Plain English:** A quotation can be started only for an opportunity that exists and is not closed (won or lost). Each person has at most one draft per opportunity. Clicking "报价" again reopens their existing draft. Different people can each have their own draft.
**Specification:**
  Given an opportunity with closeStatus = 赢单关闭 or 丢单关闭
  When "报价" is clicked
  Then it is refused with OPPORTUNITY_NOT_OPEN
  Given user 1001 already has a DRAFT on opportunity 500
  When user 1001 clicks "报价" again
  Then the existing draft is returned (reused = true) and no new number is issued
**Parameters:** Open means closeStatus = `FSTConstant.OppCloseStatus.未关闭` (dict id 5665184657220652440; 赢单关闭 = 4613462792083817532, 丢单关闭 = −7570853163447858779; see `OpportunityQueryImpl.java:166`).
**Edge cases handled:** A database lock serializes the check-then-create; if the lock is not acquired, QUOTATION_DRAFT_CREATE_BUSY is raised. The opportunity being closed later does not block editing or submitting an existing draft (no re-check outside creation).
**Suspected defect:** Draft, submit and approval never re-check that the opportunity is still open. A quotation can be approved for an opportunity that was already lost.
**Confidence:** Medium — SME question: "Should submit or approval be blocked when the opportunity has been closed after the draft was created?"

### RULE-18: Values copied onto the draft at creation and frozen
**Category:** Policy
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/presales/quote/quotation/Quotation.java:138-148`
**Plain English:** When a draft is created, it copies the opportunity's customer, delivery department, regional department and sales manager, plus the applicant and the applicant's department. Later changes to the opportunity or the org chart do not change the quotation. The draft starts with a 6% tax rate.
**Specification:**
  Given opportunity 500 with customer C9, delivery dept D3, regional dept R1, manager U7, and applicant 1001 in dept D5
  When a draft is created
  Then the quotation stores those values, with status DRAFT, taxRate 0.06 and version 0
**Parameters:** `DEFAULT_TAX_RATE = 0.06`.
**Edge cases handled:** For legacy quotations with a null pdcDeptId or salesUserId, the current value is read from the opportunity.
**Confidence:** High

### RULE-19: Approval-time adoption recalculates final amount and margin; quotation must stay PENDING
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/presales/quote/quotation/Quotation.java:367-410`
**Plain English:** When an approver changes the tail adjustment, tax rate or costs, the final price is recalculated from the stored line total. The name must still be filled in, and the price must still be above zero.
**Specification:**
  Given PENDING, finalAmount ¥106,000, tailAdjust ¥0
  When an approver adopts tailAdjust −¥106,000
  Then the new final amount is ¥0 and it is refused with SUBMIT_AMOUNT_INVALID
  Given a status other than PENDING
  Then it is refused with NOT_PENDING
**Parameters:** Line total = stored finalAmount − stored tailAdjust. Child tables are not reloaded.
**Edge cases handled:** costNodes null means labor cost is kept. otherCosts null means fees are kept. BU cost is recalculated only when either one was adopted.
**Confidence:** High

### RULE-20: Deletion is a guarded soft delete that cascades to child tables
**Category:** Lifecycle
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/infrastructure/presales/quote/QuotationRepositoryImpl.java:285-303`
**Plain English:** Deleting marks the quotation as deleted only if it is still DRAFT or REJECTED at that moment. Its lines, cost tree and seals are then marked deleted too.
**Specification:**
  Given a DRAFT quotation that another session submits during the delete
  When the delete runs
  Then 0 rows match (`status IN ('DRAFT','REJECTED')`), QUOTATION_STATUS_CHANGED is raised, and no child rows are touched
**Parameters:** Editable statuses {DRAFT, REJECTED}.
**Edge cases handled:** Child tables are deleted only after the main row was deleted successfully.
**Confidence:** High

### RULE-21: Clause codes are permanent and never reused
**Category:** Policy
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/infrastructure/presales/quote/QuotationRepositoryImpl.java:374-416`
**Plain English:** Each clause gets a code "{quotation code}-{n}" that never changes. New clauses take the highest number ever used on that quotation (including deleted clauses) plus 1, because those codes may already have become contract clause numbers.
**Specification:**
  Given quotation PSAQU202600103 whose clause history (including deleted ones) has -1, -2, -5
  When a new clause is saved
  Then it gets code PSAQU202600103-6; existing clauses keep their codes even if reordered
**Parameters:** Separator "-"; the sequence starts at 1.
**Edge cases handled:** Codes with a non-numeric suffix are ignored when finding the maximum. A unique index on (quotation_id, clause_code) blocks concurrent duplicates.
**Confidence:** High

### RULE-22: Quotation numbering
**Category:** Policy
**Priority:** P2
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/infrastructure/presales/quote/QuotationCodeGeneratorImpl.java:20-33`
**Plain English:** Quotation numbers are "PSAQU" + year + a 5-digit counter that restarts at 00103 each year.
**Specification:**
  Given no PSAQU2026 numbers exist yet
  When the first 2026 draft is created
  Then its code is PSAQU202600103; the next is PSAQU202600104
**Parameters:** Prefix `PSAQU`, 5 digits, yearly start value 103.
**Edge cases handled:** Old "QUO-" numbers and suffixes that are not numeric are ignored. The maximum is compared numerically. The unique constraint on code is the final safeguard.
**Suspected defect:** The aggregate's comment still says codes use the "QUO- 前缀" (`Quotation.java:32`); that comment is stale.
**Confidence:** High

### RULE-23: Approved quotations count toward the "approved amount" stat
**Category:** Policy
**Priority:** P2
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/infrastructure/presales/quote/QuotationQueryImpl.java:130-143`
**Plain English:** The stat cards show counts of DRAFT, PENDING and APPROVED quotations and the total final amount of APPROVED quotations, limited to what the viewer can see. REJECTED quotations are not counted.
**Specification:**
  Given the viewer sees 2 APPROVED quotations at ¥106,000 and ¥50,000 (and one with a null amount)
  When the stats load
  Then approvedAmount = ¥156,000.00
**Parameters:** None.
**Edge cases handled:** Null amounts are skipped.
**Confidence:** High

### RULE-24: Cost template name is required; templates are numbered and active by default
**Category:** Validation
**Priority:** P2
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/presales/quote/costestimate/CostTemplate.java:57-62`
**Plain English:** A new cost-estimate template needs a non-blank name (stored trimmed). It gets the code "T-" plus a timestamp to the millisecond and starts out active. Deleting a template does not affect quotations that already used it.
**Specification:**
  Given name "  照明标准模板 " at 2026-10-14 10:15:30.123
  When it is created
  Then name = "照明标准模板", code = T-20261014101530123, active = true
  Given name "   "
  Then it is refused ("模板名称不能为空")
**Parameters:** Code format `T-yyyyMMddHHmmssSSS`. Category LIGHTING or FACADE (null for legacy templates).
**Edge cases handled:** Same-millisecond collisions are caught by the unique constraint. Inactive templates are hidden from the editor dropdown (`CostTemplateRepositoryImpl.listActive`).
**Confidence:** High

### RULE-25: The opportunity-expense CRM status sync needs explicit period and company targets
**Category:** Policy
**Priority:** P2
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/trigger/task/OpportunityExpenseCrmStatusSyncJob.java:32-65`
**Plain English:** The scheduled job syncs CRM opportunity status into the opportunity-expense ledger. It runs only for the (period, company) targets set in the job parameter, and each target must name both.
**Specification:**
  Given job parameter `[{"periodId":202610,"companyCode":"FC01"}]`
  When the job runs
  Then syncCrmStatus runs for period 202610 and company FC01
  Given an empty parameter
  Then the run is skipped
  Given a target with no companyCode
  Then the run fails
**Parameters:** XXL job `opportunityExpenseCrmStatusSyncJob`.
**Edge cases handled:** A parameter that is not a JSON array fails the run.
**Suspected defect:** Nothing is synced unless an operator keeps the parameter up to date for each new period. There is no automatic "current period".
**Confidence:** Medium — the actual status mapping lives in `IOpportunityExpenseLedgerUseCase.syncCrmStatus`, outside the paths I was given. SME question: "Who updates the period/company targets each month, and should the job default to the current open period?"

---
**Coverage:** I read fully: `Quotation.java`, `QuotationRuleViolation`, `QuotationErrors`, the Create/Submit/Save/Delete/Decide/ApplyApproval use cases, `QuotationAccessGuard`, `QuotationCostRateResolver`, `QuotationProcessResultHandler`, `QuotationApprovalStarterImpl`, `QuotationCodeGeneratorImpl` (+ `SerialCodeUtil.next`), `OpportunitySnapshot`, `OpportunityExpenseCrmStatusSyncJob`, and the `QuotationStatus` enum. I also read the seed docs (quotation feature list §1/Q8, PRE-R01..R11).
I skimmed or grepped: `QuotationRepositoryImpl` (save/CAS/delete/clause-code sections), `QuotationQueryImpl` (visibility/stats), `OpportunityQueryImpl`, `QuotationController`, `CostTemplateController`, `CostTemplate`/`CostTemplateRepositoryImpl`, and the entity helpers (`QuotationLine`, `CostEstimateNode`, `QuotationSeal`, `OtherCosts`). I did not read the VOs or assemblers. The source files contained no instruction-shaped text aimed at automated analysis.
Likely gaps: the opportunity win gate that accepts only APPROVED quotations and the contractCode write-back are in contract/CRM code outside these paths. Approver task-level authorization for approval adoption lives in the workflow layer, which I did not read. The CRM status mapping is in `OpportunityExpenseLedgerUseCase`. Detailed calculations (line tax split, margin formula) are only touched here and belong to the Calculation lens.