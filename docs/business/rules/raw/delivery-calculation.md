### RULE-01: WBS budget cost is task hours × role cost rate, summed up the tree, plus non-labour cost categories
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/ProjectBudget.java:733-767`
**Plain English:** A project's budget cost is its labour cost plus its other cost categories. Labour cost is each task's planned hours times its role cost rate (the rate frozen at lock, once locked), added up through deliverable, clause and project. Obsolete nodes are left out.
**Specification:**
  Given task A has 40 h at ¥300/h, task B has 20 h at ¥450/h, an obsolete task has 10 h at ¥300/h, a task with no matched rate has 8 h, and the cost items are travel ¥2,000 and info-service ¥5,000
  When the budget is loaded or locked
  Then labour cost = ¥21,000, cost items = ¥7,000, and budget cost = ¥28,000
**Parameters:** 8 cost categories: 5 outsourcing categories and info-service are sensitive; travel and print are editable. Item amounts must be ≥ 0 (null counts as 0).
**Edge cases handled:** A task with no rate costs 0. Obsolete clauses, deliverables and tasks contribute 0 (`BudgetClause.java:67-72`, `BudgetDeliverable.java:54-60`). Null hours count as 0.
**Suspected defect:** The task-level product (`TaskPackage.budgetCost`) is never rounded to cents, but the progress page rounds each task to 2 dp (`ExecutionCostBook.money`). So the budget page total and the sum of progress-tree rows can differ by a few cents.
**Confidence:** High — the arithmetic is explicit.

### RULE-02: Budget gross margin uses ex-tax contract amount at the source quotation's tax rate (default 6%)
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/ProjectBudget.java:769-792`
**Plain English:** Budget margin = (contract amount ÷ (1 + tax rate) − budget cost) ÷ that ex-tax amount. The tax rate comes from the winning quotation; if there is none, 6% is used.
**Specification:**
  Given the contract amount is ¥106,000, budget cost is ¥28,000 and the source quotation tax rate is 6%
  When the budget detail is shown
  Then ex-tax = ¥100,000.00 and margin = 0.7200. At a 13% rate: ex-tax = ¥93,805.31 and margin = 0.7015.
**Parameters:** DEFAULT_TAX_RATE = 0.06. Ex-tax is rounded to 2 dp HALF_UP; margin to 4 dp HALF_UP.
**Edge cases handled:** No clauses gives null. Contract amount null or ≤ 0 gives null. Ex-tax ≤ 0 gives null. A tax rate outside [0, 1) is rejected with SOURCE_TAX_RATE_INVALID.
**Suspected defect:** The legacy performance margins (RULE-20) hard-code 1.06 no matter what the quotation's tax rate is, so the two screens can disagree for 13%-tax contracts.
**Confidence:** High.

### RULE-03: On budget approval, role rates are frozen and cost snapshots are written
**Category:** Lifecycle
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/delivery/project/DecideBudgetApprovalUseCase.java:73-80`
**Plain English:** When a budget is approved, each task's current role rate is copied into its frozen rate. The project then stores two snapshots: budget cost and labour cost. Later changes to the rate master data do not change the baseline.
**Specification:**
  Given a pending budget where task A (40 h) currently resolves to ¥300/h
  When the approval-passed result arrives for the current process instance
  Then locked_cost_rate is ¥300, wbs_budget_cost = ¥28,000 and wbs_budget_labor_cost = ¥21,000. If the master rate later becomes ¥320, task A still costs ¥12,000.
**Parameters:** None.
**Edge cases handled:** Only a PENDING budget can be approved (a repeat message is a no-op). A message from an old process instance is ignored. A task with no rate freezes to null and falls back to the live rate. A revision re-lock overwrites the frozen rates. `WbsBudgetCostBackfillJob` fills a missing wbs_budget_cost for budgets locked before the snapshot existed.
**Confidence:** High.

### RULE-04: Which role cost rate applies: project city, then quotation city, then unfiltered; latest active scheme wins
**Category:** Policy
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/infrastructure/delivery/project/ProjectBudgetRepositoryImpl.java:150-222`
**Plain English:** The rate city is chosen in this order:
1. The project's "budget city".
2. Otherwise the source quotation's city. The source quotation is the newest one with a contract code, or failing that the newest APPROVED one.
3. Otherwise no city filter.

Within that city, the role's standard cost rate comes from today's active scheme with the latest effective start date.
**Specification:**
  Given a project with no budget city whose winning quotation has city SH
  When the budget is loaded
  Then role T2 is priced at the SH row of the latest scheme active today
**Parameters:** Salary-grade-0 (role-level) rows. "Today" = the server date.
**Edge cases handled:** With no city, the row with the smallest city code is used for each role (`PositionRateQueryImpl.java:50-83`).
**Suspected defect:** `PositionRateQueryImpl.build` turns a missing cost rate into ¥0 (not null) when the role has only a bill rate. Such tasks pass the "rate missing" gate (RULE-05) and silently cost ¥0.
**Confidence:** Medium — ask the SME: should a role with a bill rate but no cost rate block budget submission?

### RULE-05: A budget cannot be submitted while a task with hours lacks a role, a name or a cost rate
**Category:** Validation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/ProjectBudget.java:326-377`
**Plain English:** Submitting the budget requires every task to have a role. Every non-obsolete task also needs a name, and any task with more than 0 hours needs a matched cost rate. Otherwise its labour cost would be understated.
**Specification:**
  Given a task with 8 h and role X, where X has no rate in the master data
  When the PM saves (submits) the budget
  Then submission is rejected with TASK_ROLE_RATE_MISSING. A task with 0 h and no rate is allowed.
**Parameters:** Hours threshold: > 0.
**Edge cases handled:** Obsolete tasks are skipped for the name and rate checks. Submission is refused if the budget is locked or pending (unless it was returned to the initiator).
**Suspected defect:** The role check runs on obsolete tasks too, while the name and rate checks skip them (inconsistent). A ¥0 rate passes (see RULE-04).
**Confidence:** High.

### RULE-06: A clause's revenue is capped at the clause amount; pending requests reserve quota
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/receivables/revenue/ClauseRevenueQuota.java:32-49`
**Plain English:** Remaining confirmable = max(0, clause amount − confirmed − pending). A new confirmation must be greater than 0 and no more than the remaining amount.
**Specification:**
  Given a clause of ¥500,000, ¥300,000 already confirmed and ¥150,000 pending
  When a confirmation for ¥60,000 is submitted
  Then it is rejected with "剩余 50000". ¥50,000 is accepted.
**Parameters:** "Confirmed" = sum of output-value fact rows (psa_pm_cuvalue, type 0 or null) where term_number = the clause key (`RevenueConfirmationRepositoryImpl.java:80-131`). "Pending" = sum of REV_PENDING requests.
**Edge cases handled:** A null clause amount counts as 0, so nothing can be confirmed. If the clause was reduced below what is already confirmed, remaining shows 0, not a negative. A resubmission excludes its own pending amount.
**Confidence:** High.

### RULE-07: Revenue confirmation needs a positive amount and complete evidence; the period is the sign-date month
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/receivables/revenue/RevenueConfirmation.java:64-125`
**Plain English:** A revenue request needs:
- an amount greater than 0,
- the customer acceptance document, an email confirmation and the sign date,
- room within the clause quota.

The revenue period is always the month of the customer's sign date. Any period the client sends is ignored.
**Specification:**
  Given a ¥50,000 request with sign date 2026-09-28 and a declared period of 2026-10
  When it is submitted
  Then it is saved as PENDING with revenue period "2026-09"
**Parameters:** Period format YYYY-MM.
**Edge cases handled:** A resubmission after return-to-initiator re-derives the period and re-checks the quota excluding itself. Only PENDING requests can be resubmitted.
**Suspected defect:** The docs disagree with the code on three points:
- DLV-R18 (09-业务规则清单) and income2.0 POP-D03 require POC = 100% and internal review before revenue confirmation. The code removed the deliverable gate (`SubmitRevenueConfirmationUseCase.java:60-61`, refs #1524).
- income2.0 F3 says revenue is fixed automatically without approval. The code always starts a BPM approval.
- Q-203 (a sign month whose accounting period is already closed) is unhandled.
**Confidence:** High for the code; ask the SME which gate set is authoritative.

### RULE-08: At final approval the quota is re-checked; an over-cap request is auto-rejected
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/receivables/revenue/DecideRevenueConfirmationUseCase.java:44-62`
**Plain English:** When an approval passes, the system re-checks that the amount still fits the clause quota (excluding this request). If it does not, the request is rejected with a system reason and no revenue fact is written.
**Specification:**
  Given a ¥500,000 clause where ¥480,000 is now confirmed and this request is for ¥50,000
  When the BPM approval passes
  Then the request becomes REJECTED with reason "审批通过时累计确认金额已超条款金额，系统自动驳回…" and no fact row is inserted
**Parameters:** None.
**Edge cases handled:** A non-pending request is a no-op (idempotent against MQ redelivery). When approved, a fact row is inserted in the same transaction.
**Confidence:** High.

### RULE-09: Project "confirmed revenue" counts only output rows that match one of the project's clauses
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/infrastructure/delivery/project/ProjectQueryImpl.java:272-300`
**Plain English:** Confirmed revenue = sum of output-value rows (type 0 or null) whose term number equals one of the project's clause keys. The clause key is the OA term_code, falling back to the PSA clause code.
**Specification:**
  Given clause term_code "HT-01-03" and rows of ¥200,000 (HT-01-03), ¥50,000 (no term number) and ¥30,000 ("HT-OLD")
  When the project list loads
  Then confirmed revenue = ¥200,000
**Parameters:** Type filter = 0 or NULL. Term numbers are trimmed.
**Edge cases handled:** Orphan OA rows are excluded so the list matches the dashboard. List stats compute pending = max(0, Σ project amount − confirmed) (`ProjectQueryImpl.java:221-223`).
**Confidence:** High.

### RULE-10: Task ETC, EAC and POC
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/TaskPackage.java:264-311`
**Plain English:**
- ETC = 0 if the task is DONE or LOCKED; otherwise the manual ETC (floored at 0); otherwise max(0, planned − actual).
- EAC = actual + ETC.
- POC = actual ÷ EAC, rounded to a whole percent.
**Specification:**
  Given a DOING task with 40 h planned, 25 h actual and no manual ETC
  When progress is computed
  Then ETC = 15 h, EAC = 40 h and POC = 63% (62.5 rounded HALF_UP)
**Parameters:** POC is an integer percent, rounded HALF_UP.
**Edge cases handled:** DONE or LOCKED gives 100%. EAC = 0 gives 0%. Actual > 0 with ETC = 0 gives 100%. A negative manual ETC becomes 0.
**Suspected defect:** Rule DLV-R08 (09-业务规则清单) says "ETC empty ⇒ EAC = AC (treated as complete)". The code instead defaults an empty ETC to remaining budget hours.
**Confidence:** High for the code; ask the SME which ETC default is intended.

### RULE-11: Hours roll-up and POC per deliverable, clause and project; overrun flag
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/infrastructure/delivery/project/ProgressTreeQueryImpl.java:586-714`
**Plain English:** Parent nodes add up children's planned, actual and ETC hours (obsolete children skipped), then recompute EAC and POC with the same formula. Deleted tasks keep their planned and actual hours but have ETC 0. A deliverable is "overrun" when EAC > planned > 0.
**Specification:**
  Given a deliverable with task A (40 h planned / 30 h actual / ETC 10), DONE task B (20 h / 0 h) and deleted task C (16 h / 4 h)
  When the progress tree is built
  Then planned = 76, actual = 34, ETC = 10, EAC = 44, POC = 77% and overrun = false
**Parameters:** Rounding HALF_UP to an integer.
**Edge cases handled:**
- EAC = 0: 100% if all valid children are done, else 0%.
- ETC = 0 with EAC > 0: 100%.
- Execution status: NOT_STARTED, then SUBMITTED or READY_TO_SUBMIT, then DOING.
**Suspected defect:** PREPAY clauses are now loaded (line 73), contrary to the `ProgressTree` docstring "已排除 PREPAY". An empty PREPAY clause counts as "not done", so a project whose tasks are all done but have 0 approved hours gets POC 0% instead of 100%.
**Confidence:** High.

### RULE-12: Execution cost uses the assignee's personal rate; budget cost uses the role rate (frozen first)
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/infrastructure/delivery/project/ExecutionCostBook.java:180-194`
**Plain English:** Budget cost uses the role rate: the frozen rate if there is one, otherwise the live rate. Actual, ETC and forecast costs use the assignee's personal standard cost rate. If the task is unassigned or the person has no rate, they fall back to the budget rate; if neither exists the cost is 0.
**Specification:**
  Given task A with a frozen role rate of ¥300/h, assigned to someone whose personal rate is ¥350/h
  When costs are computed
  Then actual and ETC cost use ¥350; an unassigned task uses ¥300
**Parameters:** Personal rate = `TimeSheetRateService.resolveRates(user, LocalDate.now()).standardCostRate`.
**Edge cases handled:** A person with no rate scheme degrades to null (logged warning).
**Suspected defect:** Two issues:
- Past actual hours are re-priced at today's personal rate, not the rate when they were worked.
- Budget POC compares a personal-rate numerator against a role-rate denominator, which conflicts with DLV-R09 ("分子分母口径一致").

**Confidence:** Medium — ask the SME: should actual cost use the rate in force on the work date?

### RULE-13: Cost view: forecast cost, savings, cost POC and budget POC
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/infrastructure/delivery/project/ExecutionCostBook.java:77-136`
**Plain English:**
- Cost = hours × rate, rounded to cents.
- Forecast (EAC) cost = actual + ETC cost.
- Savings = budget cost − EAC cost.
- Cost POC = actual ÷ EAC cost.
- Budget POC = actual ÷ budget cost; it is not capped at 100.
- Labour savings % = (budget − EAC) ÷ budget.
**Specification:**
  Given 40 h planned at a role rate of ¥300, and 30 h actual plus 10 h ETC at ¥350
  When costs are computed
  Then budget = ¥12,000, actual = ¥10,500, ETC = ¥3,500, EAC = ¥14,000, savings = −¥2,000, cost POC = 75% and budget POC = 88%
**Parameters:** Money is rounded to 2 dp HALF_UP; percentages are integers rounded HALF_UP.
**Edge cases handled:**
- Cost POC: EAC 0 gives 100% if done, else 0%. ETC cost 0 gives 100%.
- Budget POC: budget ≤ 0 gives null.
- Savings %: budget null or ≤ 0 gives null.
- A deleted task's ETC counts as 0.
**Confidence:** High.

### RULE-14: The progress-page header's "budget cost" is the approved baseline labour cost, not the execution sum
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/infrastructure/delivery/project/ProgressTreeQueryImpl.java:164-180`
**Plain English:** The header uses the locked labour-cost snapshot. If it is unlocked or no snapshot exists, it computes the budget's labour cost. Savings and budget POC are then recomputed against that number.
**Specification:**
  Given a locked project with wbs_budget_labor_cost = ¥21,000, tree EAC cost = ¥24,000 and actual cost = ¥10,500
  When the progress page loads
  Then header budget = ¥21,000, savings = −¥3,000 and budget POC = 50%
**Parameters:** None.
**Edge cases handled:** With no budget aggregate, the execution-sum values are kept. Row-level (clause, deliverable, task) budgets still use execution tasks, so the header can differ from the sum of its rows.
**Confidence:** High.

### RULE-15: Raising ETC needs PM_ETC approval only if it pushes project forecast labour cost above budget labour cost
**Category:** Policy
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/LaborCostHeadroom.java:59-75`
**Plain English:** Lowering or keeping ETC takes effect at once (status APPROVED). Raising it takes effect at once if the new project forecast labour cost is still within budget. The new forecast is current forecast + (new ETC − current ETC) × task rate. Otherwise approval is required and a reason is mandatory (`DeliverableProgress.java:110-136`).
**Specification:**
  Given budget labour ¥100,000, current EAC ¥98,000, a task rate of ¥350 and current ETC 10 h
  When the PM sets ETC to 15 h (+¥1,750 → ¥99,750) or to 20 h (+¥3,500 → ¥101,500)
  Then 15 h applies immediately; 20 h needs approval with a reason
**Parameters:** "Over" means strictly greater than. Delta rounded to 2 dp HALF_UP.
**Edge cases handled:**
- No headroom available (self-created task) means any increase needs approval.
- An already over-budget project means any increase needs approval.
- Negative ETC is rejected.
- An ETC already pending blocks a new request; a resubmission must still be an increase.
**Confidence:** High.

### RULE-16: Project list labour savings % and overrun flag
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/infrastructure/delivery/project/ProjectQueryImpl.java:401-421`
**Plain English:** The list uses the locked labour snapshot as the labour budget; if it is unlocked, it uses the sum of execution tasks. Savings % = (budget − EAC) ÷ budget, and "overrun" = EAC > budget.
**Specification:**
  Given a labour budget of ¥100,000 and an EAC of ¥112,000
  When the list renders
  Then savings % = −12 and overrun = true
**Parameters:** Integer percent, rounded HALF_UP.
**Edge cases handled:** A project with no execution tasks gets no cost fields. Obsolete nodes are excluded in SQL. Deleted tasks keep their budget. The list "budget cost" uses the snapshot, falling back to legacy psa_pm_budget.amount (`ProjectQueryImpl.java:308-313`).
**Confidence:** High.

### RULE-17: A daily job writes the progress-tree POC into project progress for locked projects
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/trigger/task/ProjectPocRecalcJob.java:35-64`
**Plain English:** For every project whose WBS budget is locked, the job recomputes the overall POC and writes it to progress_pro (0–100) only if the value changed.
**Specification:**
  Given a locked project whose tree POC is 63 and whose stored progress_pro is 50
  When `projectPocRecalcJob` runs
  Then progress_pro becomes 63. If they are equal, nothing is written.
**Parameters:** XXL-JOB handler `projectPocRecalcJob`.
**Edge cases handled:** A failure on one project is skipped. A null tree is skipped.
**Suspected defect:** The job's doc claims "POC null ⇒ do not write". But `Sum.fill` always sets a POC, so a locked project with no clauses or tasks gets 0, not "—".
**Confidence:** High.

### RULE-18: Budget-risk traffic light from the cost-waste rate
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/project/ProjectServiceImpl.java:1665-1705`
**Plain English:** For projects that have a contract, compute the waste rate = (budget × progress − incurred) ÷ budget × 100.
- Red if the rate ÷ 100, rounded to 2 dp, is ≤ −0.20.
- Yellow if the rate is ≤ 0.
- Green otherwise.

The incremental job only covers projects with timesheets or output-value records since yesterday 00:00; the full job covers all.
**Specification:**
  Given a budget of ¥100,000 and incurred cost of ¥60,000
  When the risk job runs
  Then the light is:
  - EAC ¥120,000 → −10% → yellow
  - EAC ¥200,000 → −30% → red
  - incurred ¥50,000, EAC ¥50,000 → +50% → green
**Parameters:** Red threshold −0.2. Yellow threshold 0. Colours 45b97c / ffd400 / F56C6C. Batch size 1900. Job handlers `calculateBudgetRiskJob` and `calculateAllBudgetRiskJob`.
**Edge cases handled:** A project with no performance data is green. Because of 2 dp rounding, −19.5% already turns red.
**Suspected defect:**
- A project with no activity, or a budget of 0, gets rate 0 and shows yellow.
- The code comment at line 1391 gives the waste formula with the opposite sign.
- The comment "排除已结项和终止" (exclude closed and terminated) at line 1634 is not implemented.
- If a project has a contract but no priced terms, the waste rate is null, causing a NullPointerException that aborts the whole batch update.
- `BudgetWorksServiceImpl.buildProjectBudgetRisk` (a different 10%-overspend rule) is dead code.

**Confidence:** Medium — ask the SME: should a project with no costs and no progress be green, and should closed or terminated projects be scored?

### RULE-19: Legacy project progress, cost waste and cost performance index
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/project/ProjectServiceImpl.java:1386-1400`
**Plain English:**
- Progress = incurred cost ÷ EAC.
- Waste = legacy budget × progress − incurred.
- Waste rate = waste ÷ budget × 100.
- Cost performance index (CPI) = earned value ÷ incurred × 100.
- WIP rate = WIP ÷ contract × 100.
**Specification:**
  Given a legacy budget of ¥100,000, incurred ¥60,000, EAC ¥120,000 and earned value ¥50,000
  When performance is built
  Then progress = 50.00, waste = −¥10,000.00, waste rate = −10.0000 and CPI = 83.3300
**Parameters:** Ratios to 4 dp, then × 100. Budget = psa_pm_budget.amount.
**Edge cases handled:** Divide-by-zero returns 0 (`BigDecimalUtil`).
**Suspected defect:** A single `contractTermPerformanceList` is shared by every project in a batch (lines 1150-1153), so `calculateProjectPerformance` adds up other projects' terms. A project with two contracts keeps only the last contract's totals.
**Confidence:** Medium — this is legacy code that the WBS model is replacing. Ask the SME: are these figures still consumed?

### RULE-20: Legacy margins divide by a fixed 1.06 for tax
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/project/ProjectServiceImpl.java:1410-1425`
**Plain English:**
- Current margin = (confirmed output ÷ 1.06 − incurred) ÷ (confirmed ÷ 1.06).
- Forecast margin = (contract ÷ 1.06 − EAC) ÷ (contract ÷ 1.06).
- Gross profit = confirmed ÷ 1.06 − carried-over cost.
**Specification:**
  Given contract ¥1,060,000, EAC ¥700,000, confirmed ¥530,000, incurred ¥400,000 and carried-over cost ¥350,000
  When performance is built
  Then forecast margin = 30.00%, estimated gross profit = ¥300,000.00, current margin = 20.00% and gross profit = ¥150,000.00
**Parameters:** `FSTConstant.税率` = 1.06 (FA/enums/fst/FSTConstant.java:278). Rates rounded to 2 dp.
**Edge cases handled:** Divide-by-zero gives 0.
**Suspected defect:** This disagrees with RULE-02, which uses the quotation's tax rate.
**Confidence:** High.

### RULE-21: Legacy per-term earned value: progress, accrued revenue, EV, CV and VAC
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/project/ProjectServiceImpl.java:1258-1306`
**Plain English:** For each term:
- Budget at completion (BAC) = labour + outsourcing + commission + other.
- EAC = forecast labour + budgeted other costs. Once the term is fully confirmed, EAC uses actual labour and max(actual, budget) for each other category.
- Progress = actual labour ÷ EAC labour.
- Accrued revenue = final term amount × progress.
- Earned value (EV) = BAC × progress.
- Cost variance (CV) = EV − incurred.
- Variance at completion (VAC) = BAC − EAC.
**Specification:**
  Given BAC = ¥95,000 (labour 80k, outsourcing 10k, other 5k), actual labour ¥40,000, EAC labour ¥100,000, other paid ¥3,000 and term final amount ¥200,000
  When performance is built
  Then progress = 40.00%, accrued revenue = ¥80,000, EV = ¥38,000, CV = −¥5,000 and VAC = −¥20,000
**Parameters:** Progress to 4 dp. Money to 2 dp HALF_UP.
**Edge cases handled:** A term with no payments defaults all payment categories to 0.
**Suspected defect:** "Fully confirmed" uses an exact equality, `amount.compareTo(completeAmount)==0`; any over-confirmation never switches to actuals.
**Confidence:** High.

### RULE-22: Legacy term labour EAC = incurred at forecast + remaining forecast days × budget daily wage, floored at actual
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/project/InvestForecastServiceImpl.java:612-680`
**Plain English:** For each post (T1, T2, T3, M3, M4), EAC labour = cost incurred when the forecast was made + forecast remaining days × the budget daily wage. If there is no forecast, budget labour is used. If actual labour is higher, actual is used.
**Specification:**
  Given T1 budget 10 days × ¥2,000, incurred at forecast ¥15,000 and forecast remaining 4 days
  When the term EAC is built
  Then EAC = ¥23,000; if actual labour is ¥25,000, EAC = ¥25,000; with no forecast, EAC = ¥20,000
**Parameters:** Posts T1/T2/T3/M3/M4. Rest-day hours add days but no cost (lines 583-585).
**Edge cases handled:** No budget rows gives an empty map. An EAC ≤ 0 falls back to budget.
**Suspected defect:** Two issues:
- Forecast days are clamped to max(budget, incurred) before RULE-21 adds incurred days, which inflates the EAC person-days figure.
- `incurredCostsAmount` sums the never-populated `laborCostAmount`, so it is always 0.

**Confidence:** Medium — ask the SME: is the legacy forecast still used by the risk light?

### RULE-23: Project WIP = sum of carried-forward rows posted to cost but not yet written off
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/sync/SyncServiceImpl.java:741-750`
**Plain English:** The job sets each project's inventory value to the sum of carry-forward amounts where cost_write_off_flag = 1 and write_off_flag = 0.
**Specification:**
  Given rows of ¥30,000 and ¥20,000 (flags 1/0) and ¥10,000 (write_off_flag = 1)
  When `calculateProjectWipJob` runs
  Then inventory_value = ¥50,000
**Parameters:** Table psa_wip_carry_forward_data_works.
**Edge cases handled:** None.
**Suspected defect:** A project whose rows are all written off disappears from the result, so its old inventory_value is never reset to 0.
**Confidence:** High.

### RULE-24: Works stage: Design until the 深化 (deepening) stage output is fully confirmed, then Closing (one-way)
**Category:** Lifecycle
**Priority:** P2
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/project/ProjectServiceImpl.java:1707-1750`
**Plain English:** For projects not already in Closing: if the confirmed amount on deepening-stage terms equals their final amount, the stage becomes Closing (2); otherwise Design (1).
**Specification:**
  Given deepening-stage terms with a final amount of ¥300,000
  When `calculateWorksStageJob` runs
  Then confirmed ¥300,000 → Closing; ¥200,000 → Design
**Parameters:** Stage id 1373982752841728 (深化阶段). WorksStage 1 = Design, 2 = Closing.
**Edge cases handled:** Projects with no deepening-stage terms are untouched. Closing is never reverted.
**Suspected defect:** Over-confirmation (¥310,000) stays in Design because the check is exact equality. By contrast, `ContractTermServiceImpl.getContractTermStage` uses ≥.
**Confidence:** High.

### RULE-25: Invoiceable amount per term = max(0, confirmed output − invoiced)
**Category:** Calculation
**Priority:** P2
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/receivables/billing/ProjectBillingView.java:93-98`
**Plain English:** Each OA term row shows invoiceable = confirmed − invoiced, never below zero. The project total adds up these clipped values.
**Specification:**
  Given a term with ¥300,000 confirmed and ¥120,000 invoiced
  When the billing tab loads
  Then invoiceable = ¥180,000; with ¥350,000 invoiced, it shows ¥0
**Parameters:** None.
**Edge cases handled:** Null values count as 0. Totals skip nulls.
**Confidence:** High.

---
**Coverage:** I read fully: ProjectBudget, TaskPackage, LaborCostHeadroom, BudgetCostCategory, ProgressTree, ProgressTreeQueryImpl, ExecutionCostBook, InvestForecastServiceImpl, the receivables revenue domain and use cases, ProjectBillingView, all 5 trigger jobs, `ProjectServiceImpl` lines 955-1750, and ContractTermServiceImpl. I skimmed: ProjectBudgetRepositoryImpl, ProjectQueryImpl, DeliverableProgress, BudgetWorksServiceImpl, HourlyRateServiceImpl, and the seed docs (rules 09, income2.0/04).
**Missed or not carded:**
- Deliverable submission and rework rules, and StartBudgetRevision diffing, are outside this calculations lens.
- `HourlyRateServiceImpl.java:133-135` stores hourly wage as daily wage **×** 7.5 instead of ÷ 7.5 (likely a defect; no Java reader found).
- Legacy budget balance (`ProjectServiceImpl.java:997-1007`) is only computed when output = contract amount.
- `getRemainderContractTerm` and ProjectPayServiceImpl expense bucketing were not examined in depth.
**Other checks:** No instruction-shaped text or credentials were found in the files read.