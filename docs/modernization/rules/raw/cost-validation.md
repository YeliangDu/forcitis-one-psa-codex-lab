### RULE-01: Month-end cost processing only runs while the period is Pre-closed or Reopened
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/accounting/AccountingPeriodServiceImpl.java:43-48`
**Plain English:** Collection, collection confirmation, trial and full OH allocation, carry draft and carry confirm, labor reclass and opening-balance runs can only start when the accounting period is in "Pre-closed" (预关账) or "Reopened" (重开) status.
**Specification:**
  Given company 1001, period 2026-08 with status 打开 (1)
  When finance clicks "Execute collection" (or allocation, or carry draft)
  Then the request is rejected (COST_COLLECT_PERIOD_NOT_ALLOWED / COST_ALLOCATION_PERIOD_NOT_ALLOWED / COST_CARRY_POSTING_NOT_ALLOWED). No lock is taken, no batch is created and no MQ message is sent. After the period moves to 预关账 (2), the same request goes through.
**Parameters:** Status codes 关闭=0, 打开=1, 预关账=2, 已关账=3, 重开=4 (FSTConstant.AccountingPeriodStatus). Allowed set = {2, 4}.
**Edge cases handled:** A null periodId or a missing period returns false (fail-closed, via AccountingPeriodPortAdapter.isCostProcessingAllowed). Carry confirm re-checks the gate because the period may have left 预关账 after the draft was made. Red-reversal carry is not exempt.
**Suspected defect:** The 重开 branch looks unreachable through the normal UI. `AccountingPeriodManagementService.transition` rejects the REOPEN action outright, and 已关账 has no available actions (`AccountingPeriodStatusEnum` CLOSED_BOOKS = empty list). A closed period therefore cannot get back into a cost-processable state.
**Confidence:** High. The status set is explicit and every entry point calls the same SSOT.

### RULE-02: A cost dated in a closed period lands in the earliest later open period
**Category:** Policy
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/infrastructure/cost/projectcost/period/AccountingPeriodPortAdapter.java:75-99`
**Plain English:** The business date decides the period a cost belongs to. If that period is already closed, the cost goes to the first open, pre-closed or reopened period after it, and closed books are never changed.
**Specification:**
  Given company 1001: 2026-06 is 已关账, 2026-07 is 已关账, 2026-08 is 预关账
  When collection picks up an expense with business date 2026-06-15
  Then the expense is stamped with the 2026-08 period id (lowest periodSequence ≥ June's sequence whose status is in {1, 2, 4})
**Parameters:** Accepting statuses = {打开 1, 预关账 2, 重开 4}. Rolling only happens from {关闭 0, 已关账 3}.
**Edge cases handled:** No period matches the date → COST_INGEST_PERIOD_NOT_FOUND. Nothing open at or after the matched period → COST_INGEST_NO_OPEN_PERIOD. The in-memory batch path only matches 正常期间 (the year-end period 13 is excluded) and has no lower bound when the sequence is null.
**Confidence:** High.

### RULE-03: Unreconciled expense lines block carry and period close
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/cost/projectcost/recon/ReconciledClosingGate.java:20-32`
**Plain English:** If the period/company has more PENDING row-level recon differences than the threshold (default 0), carry draft, carry confirm and period close are all refused. Collection itself is not blocked.
**Specification:**
  Given period 2026-08 / company 1001 has 3 rows in psa_cost_recon_disposition with disposition_status = PENDING
  When finance starts a WIP-enter carry draft, or closes the period
  Then the action is rejected with COST_CARRY_HAS_UNRECONCILED, carrying the count 3. Once all 3 are ACCEPTED or EXEMPTED (count 0), it goes through.
**Parameters:** `fst.cost.recon.unreconciled-threshold` (Nacos, default 0, rule is "> threshold blocks").
**Edge cases handled:** Soft-deleted rows are excluded. STALE rows do not count. Collection is deliberately not gated, because rows must be collected before they can be reconciled.
**Suspected defect:** Period close reuses the carry-specific error code COST_CARRY_HAS_UNRECONCILED, so the close screen will show a carry message. A non-zero threshold would let close/carry proceed with known unreconciled rows; the spec has no threshold.
**Confidence:** High.

### RULE-04: Period open/close preconditions (sequential opening, month-end done, reconciled)
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/accounting/period/AccountingPeriodManagementService.java:98-150`
**Plain English:** A period can only be opened if the previous period is closed. A period can only be closed once every month-end posting step has finished (next_step = 99, step idle) and no unreconciled rows remain.
**Specification:**
  Given company 1001: 2026-07 is 预关账 and 2026-08 is 关闭
  When finance tries to OPEN 2026-08
  Then the request is rejected with ACCOUNTING_PERIOD_PREV_NOT_CLOSED (2026-07)
  Given 2026-08 has a progress row with next_step = 3 (stage-advance not done)
  When finance tries to CLOSE 2026-08
  Then the request is rejected with ACCOUNTING_PERIOD_POSTING_INCOMPLETE(3). With next_step = 99, step_status = 0 and zero PENDING recon rows, the close succeeds.
**Parameters:** Completion marker next_step = 99, step_status = 0. The previous period must be CLOSED_BOOKS (3).
**Edge cases handled:** The first period, or a period with no previous one, skips the open-sequence check. A missing progress row on close fails closed ("未开始"). The status change is CAS-guarded on the expected status, and the close writes the WIP-snapshot task in the same transaction.
**Confidence:** High.

### RULE-05: Collection cannot start until payroll is complete for everyone who filed timesheets
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/infrastructure/cost/projectcost/salary/SalaryCalculationReadinessAdapter.java:38-68`
**Plain English:** Before a collection run, everyone who filed hours for the company in that calendar month must have an OA payroll row with total salary > 0 or a manual payroll entry. Otherwise collection is refused.
**Specification:**
  Given period 2026-08, company 1001, with 120 people who filed hours, 2 of whom have only OA payroll rows totalling 0 and no manual entry
  When finance executes collection
  Then the run is rejected with COST_COLLECT_SALARY_NOT_READY: "期间 2026-08 薪资计算未完成（本公司 2 / 120 名填报工时人员未完成算薪）…"
**Parameters:** Completion = calcPositiveRows > 0 OR manualRows > 0 (SalaryMemberFact.isCompleted). Window = 1st of the month up to (not including) the 1st of the next month.
**Edge cases handled:** It passes without checking when periodId is null, the period is missing, the year-end period has no month (periodMonth null), or nobody filed hours. Payroll rows whose OA code does not match a person record are ignored. The "业务数据检测" salary item uses the same policy.
**Confidence:** High.

### RULE-06: Collection confirmation gate
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/batch/CostCollectionBatch.java:367-388`
**Plain English:** Collection can be confirmed (so allocation can start) only when all five conditions hold: the batch completed, source-side row diffs are all resolved, landing reconciliation balances, no technical errors are open and no business errors are left unexempted.
**Specification:**
  Given the 2026-08 / 1001 batch is COMPLETED and collect_recon = BALANCED, with 0 open technical errors, 4 open business errors and 2 unresolved source diffs
  When finance clicks "Confirm collection"
  Then the request is rejected with COST_COLLECT_GATE_BLOCKED and the reasons "源侧存在 2 笔未处置差异…；存在未豁免业务异常 4 笔". next_step stays 0.
**Parameters:** `fst.cost.collection.source-recon-gate-enabled` (default true; false only skips the source-diff condition). All counts are live, not frozen batch fields.
**Edge cases handled:** No batch → "无归集批次，请先执行归集". The period gate (RULE-01) runs first. Not in collecting state → COST_COLLECT_NOT_IN_PROGRESS. A lost CAS → COST_COLLECT_ALREADY_ADVANCED.
**Suspected defect:** Because the source-recon gate can be switched off by config, prod safety depends on the Nacos value.
**Confidence:** High.

### RULE-07: Landing conservation must balance exactly (zero tolerance)
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/batch/CostCollectionBatch.java:304-310`
**Plain English:** The rows and amounts the batch should process (plus prior-period lost-deal transfers and rounding adjustments) must exactly equal what landed in the ledger, pool, opportunity-in-transit, error and exempt buckets. Otherwise reconciliation is UNBALANCED and blocks confirmation.
**Specification:**
  Given baseline 1,000 rows / ¥500,000.00, transfer-in 0, rounding-adj 1 row / ¥0.03
  When the actuals are ledger 600 / ¥300,000.00, pool 300 / ¥150,000.00, oppexp 50 / ¥25,000.00, error 40 / ¥20,000.03, exempt 11 / ¥5,000.00
  Then the counts match (1,001 = 1,001) and the amounts match (¥500,000.03), so collect_recon = BALANCED. Any difference, even ¥0.01, makes it UNBALANCED.
**Parameters:** Tolerance = 0. Amounts are compared with compareTo, so scale is ignored.
**Edge cases handled:** Error rows count as element_count, not 1 per source, so multi-element timesheet errors still balance. Exempted errors sit in their own bucket.
**Confidence:** High.

### RULE-08: Exempting a collection error needs a reason and only works on OPEN errors
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/cost/projectcost/ingest/ExemptEntryErrorUseCase.java:28-40`
**Plain English:** A user with permission `cost:entry-error:exempt` can exempt an OPEN collection error by giving a non-blank reason. The error becomes RESOLVED/EXEMPTED, never enters the ledger, and stops blocking confirmation.
**Specification:**
  Given entry error #9001 is OPEN with issue MISSING_FEE_CATEGORY, ¥1,200.00
  When finance exempts it with reason "OA 作废单，无需入账"
  Then resolution = EXEMPTED, status = RESOLVED, and resolved_by/at and exempt_reason are stored. A blank reason → COST_ENTRY_ERROR_EXEMPT_REASON_REQUIRED. A second exempt → COST_ENTRY_ERROR_NOT_OPEN.
**Parameters:** Permission code `cost:entry-error:exempt` (CostEntryErrorController.java:133, :155).
**Edge cases handled:** Batch exempt runs per item with its own transaction. The reason is trimmed.
**Suspected defect:** There is no check on issue type, so a technical SYSTEM_PROCESS_FAILED error can be exempted too, and that unblocks the confirmation gate. The spec (`cost-exception-workbench` "技术异常必阻断确认、业务异常…可豁免") says only business errors are exemptable, and the gate message says "必阻断，先由 IT 处置". The spec also limits exemption to 财务主管 (finance supervisor), but the code checks only the permission code.
**Confidence:** Medium. SME: may a SYSTEM_PROCESS_FAILED error be exempted, and if so by whom?

### RULE-09: Row-level recon disposition: which actions are allowed and when
**Category:** Validation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/cost/projectcost/recon/ReconDispositionUseCase.java:29-67`
**Plain English:** "Accept OA value" is only allowed for MISSING and AMOUNT_DIFF differences. "Exempt" is allowed for any type. "Exempt all" flips every PENDING row in the period. All of these are refused while a collection batch for that period/company is RUNNING.
**Specification:**
  Given disposition #77 of type OVER, PENDING
  When finance clicks "以OA为准" (accept)
  Then the request is rejected with COST_RECON_DISPOSITION_ACCEPT_NOT_APPLICABLE. Exempt sets it to EXEMPTED.
  Given the 2026-08 / 1001 collection batch is RUNNING
  When finance clicks exempt or "exempt all"
  Then the request is rejected with COST_COLLECTION_IN_FLIGHT_DISPOSITION_BLOCKED
**Parameters:** Permission `cost:recon-disposition:dispose`. Soft-prompt amount: AMOUNT_DIFF → |diff|, MISSING → |source|, OVER → |view|, DATA_ANOMALY → 0.
**Edge cases handled:** "Exempt all" skips non-PENDING rows. A STALE diff that reappears is reopened to PENDING. There is no sign-off freeze, so a disposition can be changed at any time.
**Suspected defect:** The spec `cost-recon-disposition` requires a materiality threshold with batch accept for AMOUNT_DIFF. The code removed the threshold, and only per-row accept or blanket exempt exists. Unlike entry-error exemption, there is no reason field or audit reason.
**Confidence:** High for the behaviour. Medium on intent. SME: should exempt need a reason, and is the materiality-threshold batch accept still required?

### RULE-10: Re-running collection reverses a prior confirmation; it is blocked once allocation is confirmed
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/period/PeriodProgress.java:71-86`
**Plain English:** Collection can be re-run while the period is at the collection or allocation step. If collection was already confirmed, re-running reverts it to unconfirmed. Once full allocation has taken effect (next_step ≥ 2), collection can no longer run.
**Specification:**
  Given progress for 2026-08 / 1001 with next_step = 1 (collection confirmed)
  When finance executes collection again
  Then next_step resets to 0 with step_status = 1, and collection must be confirmed again
  Given next_step = 2
  Then the request is rejected with COST_COLLECT_PERIOD_ALREADY_ADVANCED
**Parameters:** Step codes 0 = collection, 1 = allocation, 2/3/4 = carry steps, 99 = done.
**Edge cases handled:** A re-run while already in progress is idempotent (no state change). A lost CAS on rollback → ALREADY_ADVANCED. Overwrite mode is refused while the batch is QUEUED or RUNNING (COST_COLLECT_OVERWRITE_IN_FLIGHT, ExecuteCollectionUseCase.java:150-156). A Redisson create lock per period×company gives COST_COLLECT_INITIATE_BUSY.
**Confidence:** High.

### RULE-11: Carry draft gate order, and the red-reversal exception
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/cost/projectcost/carry/CarryBatchService.java:102-122`
**Plain English:** A carry draft is checked in this order: valid carry type, period in 预关账/重开, zero unreconciled rows, then the step gate. The step must be the current next_step and free. Only one live batch is allowed per period×company×type. Revenue red-reversal (type 3) skips the step gate but not the period or recon gates.
**Specification:**
  Given progress next_step = 2 and step_status = 0
  When finance drafts carry type 1 (stage advance → step 3)
  Then the request is rejected with COST_CARRY_STEP_NOT_READY
  When finance drafts type 0 (WIP enter → step 2)
  Then the step is occupied (step_status = 1) and batch CF0-… is created
  When a second type-0 draft is attempted
  Then it is rejected with COST_CARRY_STEP_BUSY or COST_CARRY_ALIVE_BATCH_EXISTS
**Parameters:** carry_type → step: 0 → 2, 1 → 3, 2 → 4. Type 3 = red reversal, no step. Unique index: non-reversal batches in status 0-3 are live; a reversal holds the slot only in status 0-2.
**Edge cases handled:** A missing progress row → COST_CARRY_PROGRESS_NOT_FOUND. Cancel releases the step idempotently. Red-reversal batch numbers are HC-{period}-{company}-R{n}.
**Confidence:** High.

### RULE-12: Carry confirm requires vouchers that render balanced and fully mapped
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/cost/projectcost/carry/CarryBatchService.java:196-235`
**Plain English:** On confirm, the system re-checks the period and recon gates, then does a full trial render of the batch's vouchers. If any voucher is unbalanced, a department or cost type has no U8C mapping, or a posting rule is missing, confirmation is refused and the batch stays a draft.
**Specification:**
  Given draft batch CF0-… (DRAFT·COMPLETED) with a line whose cost-bearing dept 3021 has no psa_dept_u8c mapping
  When finance clicks Confirm
  Then the request is rejected with COST_CARRY_CONFIRM_VOUCHER_INVALID naming dept 3021 and its line count. The batch is not frozen and no MQ message is sent.
**Parameters:** None hardcoded. It uses strict draftAggregated rendering.
**Edge cases handled:** Drafts still generating → COST_CARRY_DRAFT_NOT_READY. A batch with every line excluded renders an empty set and passes. A lost CAS (double confirm) → IllegalStateException. Rerun is allowed only for CONFIRMED or POSTING·FAILED/INTERRUPTED (COST_CARRY_RERUN_NOT_ALLOWED otherwise).
**Confidence:** High.

### RULE-13: Full OH allocation: one effective full run per period, never concurrent
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/cost/projectcost/allocation/ExecuteFullAllocationUseCase.java:70-100`
**Plain English:** Full allocation needs the period gate, progress at the allocation step (next_step = 1), no effective full batch already in place, and no trial or full batch in progress for the same period/company.
**Specification:**
  Given 2026-08 / 1001 is at next_step = 1 with effective full batch OH-…-F1
  When finance runs full allocation
  Then the request is rejected with COST_ALLOCATION_EFFECTIVE_FULL_EXISTS
  Given a trial batch is RUNNING
  Then the request is rejected with COST_ALLOCATION_IN_PROGRESS
**Parameters:** Batch number OH-{periodId}-F{seq}, where seq is strictly increasing and includes soft-deleted and failed rows.
**Edge cases handled:** A missing progress row → COST_ALLOCATION_PROGRESS_NOT_FOUND. A concurrent double submit that hits the unique index → IN_PROGRESS.
**Confidence:** High.

### RULE-14: Trial allocation needs a completed collection batch, but not a confirmed collection
**Category:** Validation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/cost/projectcost/allocation/ExecuteTrialAllocationUseCase.java:74-94`
**Plain English:** Trial allocation is allowed at next_step 0 or 1 if the period's collection batch is COMPLETED. Once full allocation is confirmed (next_step ≥ 2), it is refused.
**Specification:**
  Given next_step = 0 and a collection batch in FAILED status
  When finance runs trial allocation
  Then the request is rejected with COST_ALLOCATION_COLLECTION_NOT_COMPLETED
  Given next_step = 2
  Then the request is rejected with COST_ALLOCATION_PERIOD_CONFIRMED
**Parameters:** Allowed next_step ∈ {0, 1}. A missing progress row is treated as 0.
**Edge cases handled:** No batch, or a batch that is QUEUED, RUNNING, FAILED or INTERRUPTED → all rejected. The in-progress mutual exclusion is shared with full allocation.
**Confidence:** High.

### RULE-15: Ingest pre-routing guards: duplicates, exempt flag, department bonus
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/ingest/CostRoutingService.java:101-125`
**Plain English:** A source row already collected is silently skipped. A row flagged flag_exempt = 1 goes straight to the exempt bucket with no validation at all. A monthly department-bonus accrual carrying fee_type = 0 (project fee) is rejected as an error.
**Specification:**
  Given expense source (type 3, id 556) already claimed in a sink
  When collection re-runs
  Then the decision is SKIP
  Given a row with flag_exempt = 1 and missing cost centre and term
  Then the decision is EXEMPT, with no issues and no ledger or pool entry
  Given a 奖金计提 row with fee_type = 0
  Then the decision is ERROR BONUS_DEPT_HAS_PROJECT_FEE
**Parameters:** Source-type codes are injected at wiring (bonus, annual bonus, zero-hour salary). Annual-bonus ledger levels default to T1/T2/T3/M3/M4.
**Edge cases handled:** Zero-hour salary is always forced to the OH pool. Annual bonus with fee_type = 0 goes to LEDGER only for whitelisted levels with a personId; otherwise it goes to the pool.
**Suspected defect:** Finance can set flag_exempt in bulk through the cost-correction Excel import ("是 / 否 / 豁免", CostCorrectionImportService.java:425-441). That bypasses every validation and exception-workbench control with no reason and no 财务主管 restriction.
**Confidence:** High for the code. Medium for the policy. SME: is a correction-import exempt flag meant to be as strong as a workbench exemption?

### RULE-16: Project fees without a contract term never go to the OH pool
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/ingest/CostRoutingService.java:178-189`
**Plain English:** A non-opportunity project-nature cost (fee_type = 0) with a blank term is held as an error (POP-E06) rather than defaulting into overhead. With a term, it must pass ledger validation before landing.
**Specification:**
  Given expense ¥8,000.00, fee_type = 0, project P-2026-031, term_number blank
  When collection routes it
  Then the decision is ERROR INVALID_TERM "项目费用缺条款（POP-E06），退回补条款，绝不进池"
**Parameters:** fee_type: 0 = project, 1 = sales, 2 = management. Anything else → "字典外，无法路由" error.
**Edge cases handled:** Opportunity rows are short-circuited earlier (lost deal → pool; won with a project → oppexp-won; otherwise in transit).
**Confidence:** High.

### RULE-17: Mandatory dimensions depend on where the cost will land
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/ingest/CostValidationService.java:76-146`
**Plain English:** Each landing target has its own required fields. Ledger rows need company, project, term, cost type, cost-bearing org and cost centre, plus a U8C dept mapping and a unique U8C account per cost type. Pool rows need company, cost org, business date, period, amount, cost centre, fee type and cost type, and both the cost org and the cost centre must have an effective OH allocation mapping. Opportunity-in-transit rows need source, opportunity, company, cost dept, date, amount and cost type.
**Specification:**
  Given a pool-bound management expense with cost_org 3021 (mapped) and cost_center 4410 with no OH mapping effective on 2026-08-12
  When validated
  Then issue DEPT_NOT_MAPPED "成本中心 4410 在分摊对照表无归属" and the row goes to the error bucket
**Parameters:** Tables: psa_dept_u8c, psa_oa_account_u8c, OH allocation dept mapping, all date-effective.
**Edge cases handled:** The term is checked only for presence, not against master data. Timesheet rows check the U8C account per element cost type.
**Suspected defect:** The read-only posting pre-check (RULE-20) checks the OH mapping only for cost_org_id, not for the cost centre. A row can pass the pre-check and still fail ingest with DEPT_NOT_MAPPED on its cost centre.
**Confidence:** High.

### RULE-18: Expense amounts can be negative or zero; timesheet element amounts cannot be negative
**Category:** Validation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/ingest/CostValidationService.java:154-191`
**Plain English:** Expense rows only fail when the amount is null, so red-reversal and negative corrections can post. Timesheet element amounts fail when null or negative; zero is allowed.
**Specification:**
  Given an expense reversal with notax_amount −¥2,580.00
  Then there is no amount issue
  Given a timesheet element with notax_amount −¥10.00
  Then issue INVALID_AMOUNT "要素金额 amount/notax_amount 不得为空或为负数"
**Parameters:** Threshold 0 for the timesheet stream.
**Edge cases handled:** The header-level V02 check skips the timesheet stream. The element check stops at the first bad element.
**Confidence:** High.

### RULE-19: Posting pre-check: an expense's links must match its fee type
**Category:** Validation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/cost/ExpensePostingCheckServiceImpl.java:385-400`
**Plain English:** An expense cannot be linked to both a project and an opportunity. A project link requires fee type 0 (project), and an opportunity link requires fee type 1 (sales).
**Specification:**
  Given an expense with project_id 1001, opportunity_id 2002, fee_type 2
  When the posting check runs
  Then three reasons are recorded (both links set; project but not project fee; opportunity but not sales fee) and the result is fail
**Parameters:** FEE_TYPE_PROJECT = 0, FEE_TYPE_SALES = 1.
**Edge cases handled:** Every rule runs and every reason accumulates (no short-circuit). The check is read-only. Editing a row resets its result to "待检测" (CostCorrectionWriteService.java:373-377).
**Suspected defect:** The design doc (费用过账检测规则说明.md §4 rule 2, "会计期间可定位") lists a period-resolvability check, but checkRowWithCtx (lines 130-148) has none, only a null business-date check. The code also has an extra opportunity-status rule (checkOpportunity) that the doc does not list.
**Confidence:** High.

### RULE-20: Posting pre-check: pool-bound expenses need an allocation mapping effective on the business date
**Category:** Validation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/cost/ExpensePostingCheckServiceImpl.java:321-364`
**Plain English:** Only rows that will reach the OH pool are checked: management fees, sales fees with no opportunity, and sales fees on lost-closed opportunities. For these, the cost-bearing org must have an OH mapping effective on the business date. Non-public tiers must also name a belonging delivery department.
**Specification:**
  Given a management expense (fee_type 2) for cost_org 3021 dated 2021-05-10, where the mapping is effective from 2026-01-01
  Then the reason is "成本承担组织在该业务日期无生效的分摊对照登记…"
  Given a mapping with dept_role = 1 (服务档, service tier) and no belong_delivery_id
  Then the reason is "…缺归属交付部门"
**Parameters:** DEPT_ROLE_PUBLIC = 2 (exempt from the delivery-dept requirement). Lost-deal status = OppCloseStatus.丢单关闭.
**Edge cases handled:** Sales rows on won or open opportunities are skipped (in transit). A null cost org is reported by the required-dims rule instead.
**Suspected defect:** See RULE-17: the cost-centre mapping is not checked here, but ingest checks it.
**Confidence:** High.

### RULE-21: A project fee must be borne by a delivery department, unless no delivery departments are flagged yet
**Category:** Validation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/cost/ExpensePostingCheckServiceImpl.java:534-546`
**Plain English:** For fee_type = 0, the cost-bearing org must have delivery_flag = 1. If no department in the org tree has the flag, the whole rule is skipped.
**Specification:**
  Given 120 departments have delivery_flag = 1, and a project expense has cost_org 5100 with no flag
  Then the reason is "项目费用的成本承担组织不是交付部门"
  Given 0 departments are flagged
  Then the rule is skipped for every row
**Parameters:** delivery_flag = 1 (system_dept). The auto-enable condition is "≥ 1 flagged department" (ExpensePostingLookupCtx.java:83-85).
**Edge cases handled:** A null cost org is not reported twice.
**Suspected defect:** If someone clears every flag, the check switches off silently with no alert.
**Confidence:** High.

### RULE-22: Pre-posting "BLOCK" checks are advisory; only the salary check is enforced
**Category:** Policy
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/cost/projectcost/prepostingcheck/PrePostingCheckUseCase.java:26-35`
**Plain English:** The "业务数据检测" page runs every check and returns results, but no collection, allocation or carry entry point reads them. Items labelled BLOCK do not stop anything. The exception is salary completion, which is enforced separately (RULE-05).
**Specification:**
  Given TimesheetApprovalCheck (level BLOCK) reports 35 timesheet rows not approved, ¥42,000.00 labor cost affected
  When finance executes collection
  Then collection is not blocked, and those hours' cost simply does not enter PSA
**Parameters:** BLOCK checks: TimesheetApproval, TimesheetDeptMapping, TimesheetPostingIssue, TimesheetZeroCost, ExpensePostingIssue, ExpenseNotRatified, ExpenseSnapshotViewDiff, OutsourcingAccrualBlock, PocSnapshot, ConfirmedTermPoc, SalaryCalculation. WARN checks: TimesheetApprovalHours, TimesheetOpportunityStatus, TimesheetProjectStatus, ExpenseOutsourcingNotRatified, OutsourcingAccrualWarn.
**Edge cases handled:** The year-end period returns "not applicable" for monthly checks.
**Suspected defect:** Users will read "阻断" (block) as a hard gate. Several BLOCK items (unapproved hours, zero-cost hours, outsourcing accrual gap, which can drive U8C negative balances at WIP carry) have no enforcement anywhere.
**Confidence:** High that nothing enforces them (no caller outside the controller). SME: which BLOCK items should become hard gates on collection or carry?

### RULE-23: Labor reclass differences over ¥1.00 block the voucher
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/laborreclass/LaborReclassDiffCalculator.java:180-209`
**Plain English:** For each labor element and U8C department, a U8C-minus-PSA difference blocks the voucher if it exceeds the tolerance, the department is unmapped, or there is no department. One exception: when U8C is higher, both sides have amounts, and there is no PSA-side shortfall, the residual stays in U8C and does not block.
**Specification:**
  Given salary element, U8C dept D01: U8C ¥100,000.80, PSA ¥100,000.00
  Then diff ¥0.80 ≤ ¥1.00 → ROUNDING, not blocking
  Given U8C ¥105,000.00, PSA ¥100,000.00, no unapproved or other gap
  Then RESIDUAL_IN_U8C, not blocking
  Given U8C ¥95,000.00, PSA ¥100,000.00
  Then blocking (diff −5,000 exceeds the ¥1.00 tolerance in absolute value; PSA cannot reverse more than U8C holds)
**Parameters:** `fst.cost.labor-reclass.diff-tolerance` default 1.00 (a negative value falls back to 1). MATCH_EPS = 0.01 for archive-mismatch hints.
**Edge cases handled:** A person-level shortfall within tolerance is treated as rounding. A diff explained by unapproved timesheets is classified TIMESHEET_UNAPPROVED but still blocks.
**Confidence:** High.

### RULE-24: Labor reclass confirm and deliver must be fresh and follow period order
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/cost/projectcost/laborreclass/LaborReclassRunUseCase.java:154-202`
**Plain English:** Confirm needs a DRAFT run with zero blocking rows and a voucher. Deliver needs a CONFIRMED run and confirmed collection. Both recompute the preview: if the fingerprint changed (source data, mappings, tolerance or config), the action is refused. Deliver is also refused while any earlier period's run is delivering, failed or rejected.
**Specification:**
  Given the 2027-03 salary run is CONFIRMED v2 and 2027-02's social-insurance run is DELIVERY_FAILED
  When finance delivers 2027-03
  Then the request is rejected with LABOR_RECLASS_EARLIER_UNRESOLVED "期间 id=… 社保"
  Given the OH mapping was edited after generation
  Then the request is rejected with LABOR_RECLASS_STALE
**Parameters:** Preview gates (LaborReclassPreviewService.java:103-128): period year ≥ `fst.cost.u8c.voucher.opening-year-max-exclusive` (default 2026); period in 预关账/重开; posting period ≥ business period; collection batch COMPLETED; U8C and OA reachable. Permissions: generate = `cost:collection:execute`, confirm/deliver = `cost:collection:confirm`.
**Edge cases handled:** Optimistic version mismatch → STALE. A per-company×period Redisson lock with 3 s wait → IN_PROGRESS. A voucher can only be registered if it exists in the period and is currently excluded, and only while the run is still regeneratable.
**Confidence:** High.

### RULE-25: Legacy WIP second carry checks the wrong prerequisite
**Category:** Validation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/wip/WipServiceImpl.java:442-447`
**Plain English:** The legacy second WIP carry is supposed to need both the month's cost carry and its WIP carry to be finished ("已结转"). The code re-checks the cost-carry status where it should check the WIP-carry status.
**Specification:**
  Given 2026-05: cost carry 已结转, WIP carry record present but status 结转中
  When a user requests the WIP second carry
  Then legacy code lets it through, because it tests forwardDataDOMap.get(成本) twice instead of get(WIP). The intent was to reject with "WIP结转还未完成！"
**Parameters:** CarryForwardStatus 已结转/结转中; CarryForwardType 成本/WIP/WIP二次.
**Edge cases handled:** The legacy paths also refuse to carry the current or a future month (CostCarryForwardServiceImpl.java:115-118). A batch "结转中" can only be resumed by its creator (others get "正在结转中！"). Legacy lookups filter by year and month only, with no company.
**Suspected defect:** This is the defect: a copy-paste error on line 446. Also, the legacy carry lookups ignore company_code, so multiple companies would collide. WipController still exposes these endpoints alongside the new CarryBatchService.
**Confidence:** High for the bug. SME: are the legacy `/wip` carry endpoints still used in prod, or can they be retired?

---
**Coverage:**
- **Read fully:** CarryBatchService, CarryBatch, ConfirmCollectionUseCase, ExecuteCollectionUseCase (gates), CostValidationService, CostRoutingService, CostEntryError, ExemptEntryErrorUseCase, ReconDisposition, ReconDispositionUseCase, CollectionInFlightGuard, ReconciledClosingGate, UnreconciledProbeImpl, AccountingPeriodPort and its adapter, AccountingPeriodServiceImpl, PeriodProgress, SalaryCompletionPolicy, SalaryCalculationReadinessAdapter, ExpensePostingCheckServiceImpl, LaborReclassRunUseCase, LaborReclassDiffCalculator, PrePostingCheckUseCase. Also read three files outside the listed paths, needed for the closing and permission gates: AccountingPeriodManagementService, and the CostEntryErrorController / CostReconDispositionController permission annotations.
- **Skimmed:** the full allocation and trial allocation use cases, LaborReclassPreviewService, OpeningHealthCheckUseCase, OpeningPeriodSupport (opening years 2020-2025), ManualWriteOffImportUseCase, the CostCorrection import/write services, the other prepostingcheck items (via ruleDesc and level only), LedgerReconService and LedgerReconUseCase, and legacy WipServiceImpl / CostCarryForwardServiceImpl (guards only).
- **Likely missed:** WipImpairmentSweepService, the carry posting finalizer and reconciler, voucher outbox and red-reversal eligibility, OpportunityExpenseLedgerUseCase re-judgement, RepointEquivalenceChecker, OH import (OhExpenseImportService) validations, the manual write-off import's row checks (5,000-row cap, 100-char reason, no duplicate pending term), whether RETURN_TO_OPEN is blocked while a batch is in flight, and the detailed SQL criteria behind each pre-posting check reader. No instruction-shaped text aimed at automated analysis showed up in the files I read.