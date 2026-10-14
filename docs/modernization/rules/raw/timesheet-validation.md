### RULE-01: Timesheets can only be booked to a work package the employee is allowed to use right now
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourServiceImpl.java:1434-1469`
**Plain English:** When a timesheet is saved, the server rebuilds the list of work packages the logged-in user may book against on that work date, and rejects any line whose (work-package type, id) is not on that list.
**Specification:**
  Given employee E whose selectable list for 2026-08-05 has project-task packages {101, 102} and OH item {7}
  When  E saves a timesheet for 2026-08-05 with a line on project-task package 205
  Then  the save is rejected with "项目任务 <code（name）> 已关闭、超出可填报窗口或无权限，不能录入工时。" and nothing is written
**Parameters:** Matching key = (workPackageType, workPackageId as a string). The list is built by `getWorksSpace(worksDate)` (RULE-02 to RULE-05).
**Edge cases handled:** A line with no type or no id is rejected ("工作包类型或ID缺失"). The check also runs on resubmits, direct API calls and late submissions, which is how a package that closed after the form was opened gets caught. It runs for drafts (opType=保存) as well as sends.
**Suspected defect:** Only type and id are compared. The three org fields (costOrgId/projectOrgId/profitCenterId), termNumber, stageId and managerId sent by the client are not checked against the server's list, so a crafted payload can book against a valid package with a different cost organisation (see RULE-12).
**Confidence:** High — explicit code.

### RULE-02: Which project-task packages are bookable
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourServiceImpl.java:2295-2411`
**Plain English:** A project-task package can be booked only if all of the following hold: it is assigned to the user, it still has remaining hours, it has not passed its planned end date (measured on the work date), it is DOING or REWORK, nothing in its chain is obsolete, its contract clause has a code, and its project is "执行中" (executing) with a project manager.
**Specification:**
  Given package P assigned to E, status DOING, etc_hours null, planned 40h, actual 36h, plan_end_date 2026-08-10, under an executing project that has a PM and a clause with a term_code
  When  E opens or saves a timesheet for 2026-08-05
  Then  P is bookable (remaining = 4h); for 2026-08-11, or if the status were DONE/LOCKED, or if the project were paused, P is excluded
**Parameters:** Status in {DOING, REWORK}; remaining = etc_hours if not null, otherwise planned − actual, and must be > 0; the window checks only plan_end_date ≥ work date (a future start date is allowed); project status must be `ProjectStatus.执行中`.
**Edge cases handled:** A missing deliverable, clause, project or PM causes the package to be silently dropped (warning in the log). An obsolete deliverable or clause is dropped. A clause with no term_code is dropped. Data permission is switched off for this lookup so that tasks taken across departments still appear.
**Confidence:** High.

### RULE-03: Opportunity visibility for booking depends on post channel and a 90-day window after a win
**Category:** Validation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/framework/opportunity/OpportunityFilterUtils.java:66-90`
**Plain English:** Unclosed opportunities can be booked by everyone. A won-and-closed opportunity can be booked only by S (sales) or M (management) channel staff, and only up to 90 days after the win date, measured against the work date. Lost or otherwise closed opportunities cannot be booked.
**Specification:**
  Given opportunity O was won and closed on 2026-05-01, and user U has post code "S2" (channel S)
  When  U books time for 2026-07-30 (90 days later) or 2026-07-31 (91 days later)
  Then  2026-07-30 is allowed; 2026-07-31 is rejected; a "T3" (technical) user is rejected on both dates
**Parameters:** `OPP_WON_WINDOW_DAYS = 90` (hardcoded; a TODO says it should move to config). The channel is the first letter of the post code, one of T/S/M/A.
**Edge cases handled:** A work date before the win date is allowed (pre-win presales effort). A won opportunity with no won date is not visible. An unknown or empty post code is treated as not S/M. Opportunity tasks additionally need: task owner = user, type = opportunity, manager-accepted, progress < 100, a plan window covering the work date, a stageId, and an opportunity with a pdcDeptId (`ManHourServiceImpl.java:2354-2382, 2414-2424`).
**Suspected defect:** The doc (09 COST-R01) says a project or opportunity that is not "执行中" (executing) cannot be booked. Opportunities are actually filtered by close status and channel, not by an executing status.
**Confidence:** High.

### RULE-04: Role-gated "indirect" project and opportunity work packages
**Category:** Validation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourServiceImpl.java:2705-2756`
**Plain English:** Users with role 项目辅助 or 交付辅助 can book directly against any executing project in their data-permission scope. Users with role 项目辅助, 营销 or 商机辅助 can book directly against any visible opportunity (RULE-03).
**Specification:**
  Given user U holds only role 交付辅助 and project X is 执行中 and within U's data scope
  When  U opens the work-package list
  Then  X appears as an indirect project package; no indirect opportunity packages appear
**Parameters:** Role codes `RoleCode.项目辅助`, `交付辅助`, `营销`, `商机辅助`. Project status set = {执行中}.
**Edge cases handled:** A user with no roles gets no indirect packages.
**Confidence:** Medium — the role codes are constants whose stored values I did not inspect. SME: confirm which business roles map to 项目辅助/交付辅助/商机辅助, and whether indirect project booking should really cover every executing project in the data scope.

### RULE-05: OH, visit, lead and customer work packages
**Category:** Validation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourServiceImpl.java:2429-2620`
**Plain English:** Everyone can book Overhead (OH) items configured for their own department. Only users with the 营销 (marketing) role also get three more sources: customer visits they created on that work date, leads, and customers whose sales team includes them.
**Specification:**
  Given marketing user M created a customer visit dated 2026-08-05, and customer C's sales team includes M
  When  M opens the work-package list for 2026-08-05
  Then  "拜访-…" and customer C are offered; for 2026-08-06 the visit is not offered; a non-marketing user sees only their department's OH items
**Parameters:** Role code `RoleCode.营销`. OH source = `psa_ohproject_detail` filtered by the user's dept.
**Edge cases handled:** A user with no dept gets no OH items. A visit with no opportunity, lead or customer is skipped. A customer with an empty sales team is skipped.
**Confidence:** Medium — the lead filter (`selectListForWorkSpace`, said to be "not closed + data permission") is in a mapper I did not read. SME: confirm which leads should be bookable.

### RULE-06: Today's timesheet cannot be submitted before 09:00 (Shanghai time)
**Category:** Validation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourServiceImpl.java:1184-1191`
**Plain English:** A timesheet for the current day opens at 09:00 Asia/Shanghai. Before that it is rejected.
**Specification:**
  Given now is 2026-08-05 08:59 Asia/Shanghai
  When  an employee saves a timesheet dated 2026-08-05
  Then  it is rejected with "2026-08-05 日的工时还未开放填报！"; at 09:00 it is accepted
**Parameters:** Opening time 09:00; zone Asia/Shanghai.
**Edge cases handled:** Applies only when the work date equals today. Past dates are unaffected.
**Suspected defect:** There is no rule against future dates; the only guards are the accounting period and the 7.5h minimum. The doc (09 COST-R02) describes a deadline ("finish by 09:00 the next day"), which is not implemented. The code has an opening gate instead.
**Confidence:** Medium. SME: should future-dated timesheets be allowed, and is there a submission deadline?

### RULE-07: The accounting period for the work date must be open
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/accounting/AccountingPeriodServiceImpl.java:38-69`
**Plain English:** Time can be entered only if the work date falls in an accounting period whose status is 打开 (open) or 重开 (reopened). It is called from `ManHourServiceImpl.check` at lines 1192-1195.
**Specification:**
  Given the period covering 2026-07-15 has status 预关账 (pre-closed)
  When  an employee submits time for 2026-07-15
  Then  it is rejected with "你所填报的工时期间非"打开"状态，禁止填报工时！"
**Parameters:** Allowed statuses = {打开, 重开}.
**Edge cases handled:** If no period covers the date, it is rejected ("未找到…对应的会计期间").
**Suspected defect:** The caller passes `companyCode = null`, so the period is resolved without the employee's company even though the sheet records a company_code.
**Confidence:** Medium. SME: are accounting periods per company? If so, the timesheet check should use the employee's company.

### RULE-08: Every line must have hours greater than zero
**Category:** Validation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourServiceImpl.java:1196-1204`
**Plain English:** Each timesheet line must declare a positive number of hours.
**Specification:**
  Given a timesheet with lines of 0.5h and 0h
  When  it is saved
  Then  it is rejected with "填报工时不能小于0！"
**Parameters:** Threshold: hours must be > 0.
**Edge cases handled:** None beyond the threshold.
**Suspected defect:** The message says "cannot be less than 0", but 0 is also rejected. A null hours value causes a NullPointerException that surfaces as a generic "工时保存/发起流程异常" error. This does not match doc TS-R-020, which allows a zero-hour submit with a warning when auto-approve is on.
**Confidence:** High.

### RULE-09: No more than 15 hours per day
**Category:** Validation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourServiceImpl.java:1205-1211`
**Plain English:** The declared hours on one day's timesheet may not add up to more than 15.
**Specification:**
  Given lines of 8h + 7h + 0.5h on 2026-08-05
  When  the timesheet is saved
  Then  it is rejected with "2026-08-05 填报工时大于15小时！"; exactly 15h is accepted
**Parameters:** Daily maximum 15h (hardcoded).
**Edge cases handled:** Leave hours are not counted towards the cap.
**Suspected defect:** The cap is per submitted sheet. It does not add up other sheets for the same person and day, but RULE-11 normally prevents a second sheet anyway.
**Confidence:** High.

### RULE-10: On a workday, booked hours plus leave must be at least 7.5h
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourServiceImpl.java:1215-1226`
**Plain English:** On a working day, declared hours plus approved leave hours must reach 7.5 hours.
**Specification:**
  Given 2026-08-05 is a workday, the employee declares 6.0h and has 90 minutes of leave
  When  the timesheet is saved
  Then  it is accepted (6.0 + 1.5 = 7.5); with 89 minutes of leave (7.4833h) it is rejected with "工时合计（工时+请假）不得低于7.5小时！"
**Parameters:** Minimum 7.5h. Leave hours = minutes ÷ 60, rounded HALF_UP to 4 decimal places. Workday flag comes from `psa_calendar.workday` (1 = workday).
**Edge cases handled:** Non-workdays are exempt. If a date is missing from the calendar it is treated as a workday (`isWorkday`, lines 1548-1558). The check runs for drafts too, so a partial draft cannot be saved on a workday.
**Confidence:** High.

### RULE-11: One timesheet per person per day; duplicate and concurrent submissions blocked
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourServiceImpl.java:1227-1246`
**Plain English:** A new timesheet is rejected if the same person already has a non-deleted sheet for that date, or if another submission for the same person and date is in flight. Changes must go through "resubmit".
**Specification:**
  Given employee E already has a sheet for 2026-08-05 (any approval status, including cancelled)
  When  E creates another sheet for 2026-08-05 without an originId
  Then  it is rejected with "2026-08-05 已存在工时单，请勿重复填报；如需修改请在「我的流程」中重新发起"
**Parameters:** Redis lock key `psa:man-hour:submitted:{personId}:{yyyy-MM-dd}`, set during submission (lines 792-797) and released in `finally`. Backed by the DB filtered unique index `idx_psa_time_sheet_unique_valid`.
**Edge cases handled:** The date match covers the whole day (00:00 to 00:00 the next day). Resubmits that carry an originId skip this check.
**Suspected defect:** A resubmit may change worksDate (`resetOriginManHourForResubmit` writes the new date), and the duplicate check is skipped whenever originId is set. Moving a sheet onto a date that already has one therefore hits the unique index and returns a raw SQL error.
**Confidence:** High.

### RULE-12: The three org fields are mandatory on every line
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourServiceImpl.java:1327-1351`
**Plain English:** Every line must carry a cost-bearing org, a project-owning org and a profit centre. Otherwise the whole save is rejected and every offending line is listed.
**Specification:**
  Given a line for project "PRJ-001" on 2026-08-05 with profitCenterId missing
  When  the timesheet is saved
  Then  it is rejected listing "[2026-08-05 PRJ-001] 缺少：利润中心编码(profitCenterId)"
**Parameters:** Fields: costOrgId, projectOrgId, profitCenterId.
**Edge cases handled:** Lines with no work date are skipped. Errors from all lines are reported together.
**Suspected defect:** The values come from the client and are not compared with the server's work-package list. The intended source rules are: project or project task → the project's delivery dept; opportunity or opportunity task → the opportunity's dept; OH, visit, lead and customer → the submitter's dept (`applyWorkSpaceOrgFields` call sites). Those rules are not enforced at save time.
**Confidence:** High.

### RULE-13: Every line must match exactly one standard-cost rate and one billing rate
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourServiceImpl.java:1479-1507`
**Plain English:** Submission is blocked unless each line resolves both rates. The lookup takes the employee's transfer record valid on the work date, builds a key from job level + salary grade + city category, and needs exactly one standard-cost detail and one billing detail whose period covers that date.
**Specification:**
  Given employee E's record on 2026-08-05 has no cityCategoryCode
  When  E submits a timesheet for 2026-08-05
  Then  it is rejected with "存在以下工时行的费率未命中… 缺失：cityCategoryCode（城市分类编码）"
**Parameters:** Match key = jobLevelCode + salaryGradeNo + cityCategoryCode (`TimeSheetRateServiceImpl.java:62-104`).
**Edge cases handled:** These cases all return 503: no employee record, more than one record, a blank key field, no rate detail, more than one detail, or a rate period that does not cover the date. Errors from all lines are reported together. Rates are locked at submit time (lines 1670-1696). At approval, amounts are calculated from the locked rate, not looked up again.
**Confidence:** High.

### RULE-14: Fields required to send, by work-package type (drafts exempt)
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourServiceImpl.java:1360-1393`
**Plain English:** Sending for approval requires a line type and a work-package type on every line, plus extra fields that depend on the work-package type.
**Specification:**
  Given a line with workPackageType = 2 (opportunity task), taskId 88 and no stageId
  When  the timesheet is sent (opType = 发送)
  Then  it is rejected with "缺少：阶段ID(stageId)"; saving it as a draft is not blocked by this rule
**Parameters:** Type 0 (project task) → taskId + termNumber. Type 1 (project) → termNumber. Type 2 (opportunity task) → stageId + taskId. Type 3 (opportunity) → stageId.
**Edge cases handled:** For project tasks, termNumber is filled on the server from task package → deliverable → clause (lines 678-684), so the client value is ignored.
**Confidence:** High.

### RULE-15: Every line must have an approver before it can be sent
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourServiceImpl.java:1400-1418`
**Plain English:** Sending is blocked if any line has no approver. Approvers are resolved by line type (lines 1702-1765):
- OH lines: the submitter's direct leader.
- Project lines: tried in order — the project's manager, then the task's manager, then the line's managerId, then the manager of the project looked up by code.
- Presales or mixed lines: the leader of the PDC dept of the opportunity, lead, customer or visit.
**Specification:**
  Given an OH line, and submitter E has no leaderUserId
  When  E sends the timesheet
  Then  it is rejected with "以下工时行未能匹配到审批人…[2026-08-05 <name>]"
**Parameters:** Line type codes 0 = project, 1 = presales, 2 = OH; `ManHourType.混合` (mixed) uses the presales rule.
**Edge cases handled:** For drafts the approver may be null. A project code of "--" (non-chargeable placeholder) cannot be resolved.
**Confidence:** High.

### RULE-16: A whole day of leave blocks sending a timesheet for that day
**Category:** Validation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourServiceImpl.java:858-868`
**Plain English:** If the employee has at least 7.5h (450 minutes) of leave on the work date, a timesheet cannot be sent for that date, because they owe no time.
**Specification:**
  Given employee E has 450 minutes of leave on 2026-08-05
  When  E sends a timesheet for 2026-08-05
  Then  it is rejected with "2026-08-05 为整天请假，无需填报工时" and the transaction is rolled back
**Parameters:** Full-day threshold = 450 minutes.
**Edge cases handled:** The rule is skipped for drafts.
**Suspected defect:** The draft branch is technically exempt, but the same transaction applies RULE-10, which passes, and the draft is persisted. A full-leave day can therefore still end up with a draft sheet, which conflicts with the comment's goal of "no sheet on full-leave days".
**Confidence:** Medium. SME: should drafts also be blocked on full-leave days?

### RULE-17: Only the owner can edit or resubmit, and only when rejected, cancelled or returned
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourServiceImpl.java:800-818`
**Plain English:** A sheet can be resubmitted only by the person it belongs to, and only if its status is 驳回 (rejected) or 取消 (cancelled/withdrawn), or its live workflow instance is currently returned to the initiator. The same guard protects edit-detail (lines 2847-2863) and process-edit-submit (lines 334-360).
**Specification:**
  Given sheet S belongs to E and is 审批中 (in approval), not returned
  When  E resubmits with originId = S
  Then  it is rejected with "仅审批驳回、取消或退回发起人的工时允许重新发起"; if another user tries, the result is 403 "只能重新发起本人的工时单"
**Parameters:** Allowed: approveStatus ∈ {驳回, 取消} or `isReturnedToInitiator = true`.
**Edge cases handled:** If BPM is unreachable or has no instance, the sheet is treated as not returned, so access is denied. Approved (通过) sheets can never be edited or resubmitted.
**Suspected defect:** Doc TS-R-003/008 says approved sheets can be reopened by configuration. No reopen path exists in this code.
**Confidence:** High.

### RULE-18: On resubmit, unchanged approved lines are kept, and a resubmit with nothing to approve is refused
**Category:** Validation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourServiceImpl.java:953-1003`
**Plain English:** A line that was already approved keeps its approval and is not routed again, provided it comes back with the same row id and the same type, work-package type, task, work package, date, hours and remark. If every line qualifies, the resubmit is rejected (lines 885-891).
**Specification:**
  Given a rejected sheet with line A (approved, 4h, remark "x") and line B (rejected)
  When  E resubmits with A unchanged (4.00h, remark " x ") and B edited
  Then  A keeps status 通过 and only B goes for approval; if B were also an unchanged approved line, the result is "所有明细行均已审批通过且未修改，无需重新提交"
**Parameters:** Hours compared numerically (4 = 4.00), using approved hours if present, otherwise declared hours. Remarks compared after trimming.
**Edge cases handled:** A missing or mismatched row id sends the line for approval again. A changed work date sends every line again. The "nothing left" rejection applies only through the nested `/add` entry point; the flat `/update` path returns −1 and never rejects.
**Confidence:** High.

### RULE-19: The remaining-hours (ETC) gate on project-task packages
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourServiceImpl.java:1258-1310`
**Plain English:** Total hours booked on one task package in this sheet may not exceed its remaining hours (ETC, estimate to complete). Going over requires an approved ETC increase first.
**Specification:**
  Given package P has etc_hours null, planned 40h, actual 36h (remaining 4h)
  When  E submits lines of 3h + 2h on P
  Then  it is rejected with "任务包「P」剩余工时 4 小时，本次填报合计 5 小时已超出，请先申请 ETC 调增！"
**Parameters:** Remaining = max(etc_hours, 0) if etc_hours is set, otherwise max(planned − actual, 0).
**Edge cases handled:** On resubmit, lines of the original sheet that are already approved on the same package are subtracted, because they are already counted in actual_hours. Example: an approved 2h line plus a new 3h line gives 3h compared with 4h, which passes. Legacy tasks missing from the new package table are not checked.
**Confidence:** High.

### RULE-20: Withdrawn timesheets can be deleted only on non-workdays or full-leave days
**Category:** Validation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourServiceImpl.java:2940-2957`
**Plain English:** Only the owner can delete, only a cancelled (withdrawn) sheet can be deleted, and only if its date is a non-workday or has at least 450 minutes of leave. This stops people withdrawing and deleting a sheet to escape the daily booking duty.
**Specification:**
  Given E's cancelled sheet for workday 2026-08-05 with no leave
  When  E deletes it
  Then  it is rejected with "工作日且无整天请假的工时，撤回后不允许删除"; the same sheet dated Saturday 2026-08-08 is deleted, and an audit row (operatorType = DELETE) is written first
**Parameters:** Full-leave threshold 450 minutes. Workday comes from `psa_calendar` (`isManHourDeletable`, lines 1561-1569).
**Edge cases handled:** Another user gets 403. A status other than 取消 gets 400. The to-do list's "deletable" flag uses the same function.
**Confidence:** High.

### RULE-21: Who can see whose timesheets
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourServiceImpl.java:216-222`
**Plain English:** In the timesheet list, anyone who is not a super-admin sees only their own timesheets.
**Specification:**
  Given user U without the super-admin role filters by personName "Li"
  When  U opens the timesheet page
  Then  only U's own sheets come back (personId is forced to U)
**Parameters:** `FSTConstant.SUPER_ADMIN_ROLE`.
**Edge cases handled:** Only sheets with dataOrigin = PSA are listed.
**Suspected defect:** Several read endpoints have no ownership or permission check:
- `/get?id=` (`getById`), `/item/get?id=`, `/item/get-by-instance-id`, `/process-edit-detail` return any sheet.
- `/preview-rates?personId=` returns any employee's cost and billing rates.
- `/labor-cost/page` and `/closed/list` have no `@DataPermission`, unlike `/closed/page`.

None of the controller methods carry `@PreAuthorize` (`ManHourController.java:124-207`). This conflicts with doc 09 OPS-R15, which says a PM sees only their own project's cost.
**Confidence:** High.

### RULE-22: Approval status transition rules; approvers act only on their own lines
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourApproveStatusService.java:41-183`
**Plain English:** Status changes are checked against an allow-list:
- none → 待发起 (pending send)
- 待发起 → 审批中 (in approval)
- 审批中 → 通过 (approved) / 驳回 (rejected) / 取消 (cancelled) / 待发起
- 驳回 → 待发起
- 取消 → 待发起
- 通过 is final: there is no transition out of it.

When an individual approver approves or rejects (not the BPM engine), only lines whose approval field equals that approver are updated.
**Specification:**
  Given sheet S is 通过 (approved)
  When  anyone requests 通过 → 取消
  Then  it is rejected with "状态流转不合法：通过 → 取消"; and when approver A approves a 审批中 sheet, only lines with approval = A become 通过 and are priced
**Parameters:** Allow-list as above. Amount = locked rate × approved hours, rounded HALF_UP to 3 decimal places.
**Edge cases handled:** Repeating the current status does nothing (BPM path). Setting 审批中 never overwrites lines already 通过, 驳回 or 取消. A BPM final status sweeps any lines still 审批中.
**Confidence:** High.

### RULE-23: Conditions for a timesheet line to be postable to cost
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourPostingCheckServiceImpl.java:117-216`
**Plain English:** A line can be posted only if all five hold:
1. It is approved.
2. All nine rate fields are filled.
3. Its type and work-package type fields are complete.
4. company_code, cost_center_id and cost_org_id are filled.
5. For project and project-task lines, the term number really belongs to that project's contract clauses.
**Specification:**
  Given an approved project-task line with project_code PRJ-001 and term_number "T-09", where PRJ-001's clauses are {T-01, T-02}
  When  the posting check runs
  Then  it fails with "合同条款编码不在该项目档案中（term_number=T-09 不属于 project_code=PRJ-001 …）"
**Parameters:** Approved status value = 3. Term comparison ignores surrounding spaces and letter case.
**Edge cases handled:** The check fails closed: a project with no clauses, a blank project_code, or a failure to load the clause archive all fail rather than pass. Missing ids get an explicit reason. `fix()` refuses to repair lines that are not approved.
**Confidence:** High.

### RULE-24: Integrity rules applied before any task is created
**Category:** Validation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/resource/TaskValidator.java:96-143`
**Plain English:** Every task needs a project or opportunity id. A project task also needs a clause number and a work package that belongs to the same project. An opportunity task needs a stage.
**Specification:**
  Given a project task for project 10 whose planDetailId belongs to a plan for project 11
  When  it is inserted via `TaskService.insertTask`
  Then  it is rejected with "存在以下任务校验不通过，不能新增：[name] 所选工作包不属于该项目"
**Parameters:** Type 0 = project and type 1 = opportunity (`PlanTaskType`).
**Edge cases handled:** Failures for all rows are collected and the whole batch is rejected. A missing projectId suppresses the ownership message. Types with no defined rule are skipped.
**Confidence:** High.

### RULE-25: Tasks can be accepted or refused only while pending
**Category:** Validation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/resource/ResourceServiceImpl.java:210-294`
**Plain English:** For a task the member raised themselves, the manager's flag must still be 待接受 (pending). For a task the manager assigned, the member's flag must still be 待接受. Otherwise the action is refused. On accept the task becomes 进行中 (in progress).
**Specification:**
  Given a member-raised task whose flagAcceptManager is already 接受 (accepted)
  When  the manager clicks Accept again
  Then  it is rejected with "存在已审批的任务，请刷新页面后再试！" and the batch is rolled back
**Parameters:** Flag values 待接受 / 接受 / 拒绝.
**Edge cases handled:** The guard stops double-clicks and stale lists from changing status twice and sending duplicate notifications.
**Suspected defect:** There is no check that the caller is the task's manager (member-raised tasks) or its assignee (manager-assigned tasks), so any authenticated user can accept or refuse any task id. The related deletion guard (`TaskServiceImpl.del`, `PlanWorksServiceImpl.delete`) also checks only the accept flag, not who is deleting.
**Confidence:** High for the status guard. SME: who is allowed to accept or refuse a given task?

---
**Coverage:**
- Read fully: `ManHourServiceImpl` validation, work-package, edit and delete paths (lines 216-1870, 2131-2990); `ManHourApproveStatusService`; `ManHourBpmApplyService`; `TimeSheetRateServiceImpl`; `ManHourPostingCheckServiceImpl`; `TaskValidator`; both timesheet MQ handlers; both controllers; `OpportunityFilterUtils`; `AccountingPeriodServiceImpl.assertEntryAllowed`; seed docs 04 and 09 §5.
- Skimmed: `ManHourItemServiceImpl`, `ManHourRevisionService`, `ManHourEndServiceImpl` (mostly calculation), `CalendarServiceImpl`. In `TaskServiceImpl`, `ResourceServiceImpl` and `PlanWorksServiceImpl` I read only the guard sites: plan-date edit by the responsible manager only, `saveByPerson` group-leader/member gate, weekly-meeting once-sent and per-week uniqueness, and plan-template name uniqueness. These plus the employee-archive company_code requirement (`ManHourServiceImpl.java:1611-1616`) were left out to stay within 25 cards.
- Possibly missed: guards inside the BPM module (for example, whether rejecting requires a comment, doc TS-R-038, which is not enforced anywhere in these paths); mapper-level filters (`selectListForWorkSpace` for leads and projects, data-permission SQL); `ManHourDirtyDetector` rule details. No credentials and no instruction-shaped text were found in the files read.