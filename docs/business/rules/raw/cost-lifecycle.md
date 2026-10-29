### RULE-01: Month-end cost processing only runs while the period is pre-closed or reopened
**Category:** Policy
**Priority:** P0
**Source:** `F/service/accounting/AccountingPeriodServiceImpl.java:43-56`
**Plain English:** Collection, collection confirmation, allocation, and every carry draft or confirm are only allowed when the accounting period is "pre-closed" (预关账) or "reopened" (重开). New cost facts can still be booked into a period that is open, pre-closed or reopened.
**Specification:**
  Given period 2026-07 for company C01 with status 1 (打开 / open)
  When finance starts cost collection, full allocation, or a carry draft for 2026-07
  Then the request is rejected (COST_COLLECT_PERIOD_NOT_ALLOWED, COST_ALLOCATION_PERIOD_NOT_ALLOWED or COST_CARRY_POSTING_NOT_ALLOWED). After the period moves to status 2 (预关账) the same request is accepted.
**Parameters:** status codes 0 = 关闭, 1 = 打开, 2 = 预关账, 3 = 已关账, 4 = 重开 (`FSTConstant.AccountingPeriodStatus`). Cost processing is allowed for {2, 4}. Cost entry is allowed for {1, 2, 4}.
**Edge cases handled:** A null period ID or an unknown period is treated as not allowed. Carry confirm checks the gate again, because the period may have left 2/4 after the draft was created.
**Suspected defect:** The status checks use `FSTConstant` integer constants, not the `AccountingPeriodStatusEnum` single source of truth (CLAUDE.md forbids new FSTConstant status usage). Seed spec cost-revenue-red-reversal says red reversal needs the period to be "开账中" (open), but the code requires pre-closed or reopened.
**Confidence:** High. The logic is explicit, and the callers are ExecuteCollectionUseCase:84, ConfirmCollectionUseCase:60, ExecuteFullAllocationUseCase:72 and CarryBatchService:306-310.

### RULE-02: Accounting period status transition table
**Category:** Lifecycle
**Priority:** P1
**Source:** `FA/enums/registry/accounting/AccountingPeriodStatusEnum.java:22-72`
**Plain English:** The allowed period moves are: closed → open, open → pre-closed, pre-closed → back to open or closed-books, and reopened → pre-closed or closed-books. Closed-books is terminal.
**Specification:**
  Given a period in PRE_CLOSED (2)
  When action CLOSE is applied
  Then the status becomes CLOSED_BOOKS (3), and every later action returns null (not allowed)
**Parameters:** CLOSED(0)→[OPEN]; OPENED(1)→[PRE_CLOSE]; PRE_CLOSED(2)→[RETURN_TO_OPEN, CLOSE]; CLOSED_BOOKS(3)→[]; REOPENED(4)→[PRE_CLOSE, CLOSE]
**Edge cases handled:** A null or unlisted action returns null.
**Suspected defect:** No production code calls `transition()`; only `AccountingPeriodStatusEnumTest` uses it, so nothing actually enforces this table. REOPENED (4) cannot be reached: no state lists the REOPEN action, and CLOSED_BOOKS has no actions at all. Yet cost processing depends on status 4.
**Confidence:** Medium. SME question: "How does a closed-books period get to 重开 (4) in production, and which code or screen writes the period status?"

### RULE-03: Period month-end step gate: collection lock, confirm, and re-collection rollback
**Category:** Lifecycle
**Priority:** P0
**Source:** `F/domain/cost/projectcost/period/PeriodProgress.java:71-117`
**Plain English:** Each period×company row has a fixed step sequence: 0 collect-confirm → 1 allocation → 2 WIP enter → 3 stage advance → 4 write-off → 99 done. Running collection locks step 0. If collection was already confirmed (step 1), running it again rolls the period back to step 0, so it must be confirmed again. Once allocation is effective (step ≥ 2), collection can never be re-run.
**Specification:**
  Given progress (next_step=1, step_status=0), meaning collection is confirmed and allocation has not run
  When finance runs collection again
  Then next_step becomes 0 and step_status becomes 1 (in progress). Confirmation is required again. A later confirm sets next_step=1, step_status=0, last_posted_step=0 and stamps confirmed_by/at.
  Given next_step=2
  When collection is run
  Then it is rejected with COST_COLLECT_PERIOD_ALREADY_ADVANCED
**Parameters:** steps 0/1/2/3/4/99; step_status 0 = idle, 1 = running
**Edge cases handled:** Re-running while step 0 is already running is idempotent and makes no state change. Confirm requires next_step=0 and step_status=1. Full allocation requires next_step=1 and step_status=0, then sets next_step=2 and last_posted_step=1. Concurrency is serialised by a version compare-and-set (CAS) in the repository.
**Suspected defect:** Nothing reverses an effective full allocation, so a collection error found after step 2 cannot be fixed through re-collection.
**Confidence:** High

### RULE-04: Carry steps are strictly serial: occupy on draft, advance on POSTED, release on cancel
**Category:** Lifecycle
**Priority:** P0
**Source:** `F/domain/cost/projectcost/period/PeriodProgress.java:126-171`
**Plain English:** A WIP carry of type 0, 1 or 2 can only start when the period's next step is exactly its step (2, 3 or 4) and that step is idle. Starting the draft occupies the step. Full posting moves the period to the next step (step 4 moves to 99 = done). Cancelling frees the step without advancing it.
**Specification:**
  Given progress (next_step=3, step_status=0)
  When a stage-advance carry (carry_type=1 → step 3) draft is created
  Then step_status becomes 1. Creating a write-off draft (step 4) at this point is rejected with COST_CARRY_STEP_NOT_READY. When the stage-advance batch reaches POSTED, next_step becomes 4, last_posted_step becomes 3, and step_status becomes 0.
**Parameters:** carry_type→step: 0→2, 1→3, 2→4. Red reversal (carry_type=3) has no step. Final step 4 moves to 99.
**Edge cases handled:** A second draft while the step is occupied is rejected with COST_CARRY_STEP_BUSY. A step outside 2–4 raises IllegalArgumentException. Release on cancel is idempotent: if the gate is not occupied for that step, it does nothing (#983).
**Confidence:** High

### RULE-05: Collection batch status and phase lifecycle
**Category:** Lifecycle
**Priority:** P0
**Source:** `F/domain/cost/projectcost/batch/CostCollectionBatch.java:122-191`
**Plain English:** A collection batch moves QUEUED → RUNNING and then through fixed phases INIT → BASELINE_COUNTING → COLLECTING → ROUNDING_NORMALIZING → SOURCE_RECONCILING → COLLECT_RECONCILING → DONE, ending as COMPLETED. Only QUEUED or INTERRUPTED batches may be picked up by the worker.
**Specification:**
  Given a batch in status INTERRUPTED with persisted phase ROUNDING_NORMALIZING
  When the MQ job for it is consumed
  Then status becomes RUNNING, and execution resumes at rounding normalisation, then source recon, then collect recon, then COMPLETED/DONE with finishedAt set. Earlier phases are not repeated.
**Parameters:** statuses INIT/QUEUED/RUNNING/COMPLETED/FAILED/INTERRUPTED; Redisson consumer lock `lock:cost-collection:execute:{batchId}`
**Edge cases handled:** A phase skip throws IllegalStateException. Thread interruption, including Druid "interrupt" SQLExceptions, RejectedExecution and ClosedByInterrupt, is classified as INTERRUPTED, which can be re-run. Other errors become FAILED with last_error truncated to 2000 characters. A CAS loss means another executor owns the batch, so this run yields without marking FAILED (CostCollectionBatchOrchestratorImpl:82-101).
**Suspected defect:** `fail()` has no guard. Its comment says "non-terminal only", but it would overwrite a COMPLETED batch if called.
**Confidence:** High

### RULE-06: Re-running collection resets the batch in place; overwrite mode purges first
**Category:** Lifecycle
**Priority:** P1
**Source:** `F/domain/cost/projectcost/batch/CostCollectionBatch.java:211-264`
**Plain English:** One collection batch exists per period×company. Re-running a FAILED, COMPLETED or INTERRUPTED batch resets it to QUEUED/INIT and zeroes every counter, recon result, error and timestamp. A batch that is QUEUED or RUNNING is reused unchanged. Overwrite mode, which physically purges the period's collection output first, is refused while the batch is in flight.
**Specification:**
  Given a COMPLETED collection batch with baseline 1,200 rows / ¥3,450,000.00 and collect_recon=BALANCED
  When finance runs collection again in overwrite mode
  Then the period's collection artifacts are purged and the batch returns to QUEUED·INIT with all counts at 0 and recon NOT_RECONCILED. The id, period, company, progress ID and startedBy are kept.
**Parameters:** a unique key (period, company, deleted) allows only one row
**Edge cases handled:** Overwrite on a QUEUED or RUNNING batch is rejected with COST_COLLECT_OVERWRITE_IN_FLIGHT (ExecuteCollectionUseCase:150-156). Incremental mode keeps existing output and claims, and only picks up unclaimed source rows.
**Confidence:** High

### RULE-07: Collection confirmation gate
**Category:** Validation
**Priority:** P0
**Source:** `F/domain/cost/projectcost/batch/CostCollectionBatch.java:367-388`
**Plain English:** Collection can only be confirmed when the batch is COMPLETED, the destination reconciliation balances, no technical error is still open, every business error has been exempted, and (when the source gate is on) no line-level source difference is left unresolved.
**Specification:**
  Given a COMPLETED batch with collect_recon=BALANCED, 0 open technical errors, 2 unexempted business errors and 0 unresolved source differences
  When finance confirms collection
  Then confirmation is rejected with COST_COLLECT_GATE_BLOCKED and the reason "存在未豁免业务异常 2 笔". next_step stays 0.
**Parameters:** `fst.cost.collection.source-recon-gate-enabled` (default true). Error counts are read live from the error repository, not from the batch's frozen counters.
**Edge cases handled:** With no batch the message is "无归集批次，请先执行归集". All failing reasons are reported together. A wrong progress state gives COST_COLLECT_NOT_IN_PROGRESS. A CAS loss gives ALREADY_ADVANCED (ConfirmCollectionUseCase:56-98).
**Confidence:** High

### RULE-08: Collection start preconditions and order of side effects
**Category:** Validation
**Priority:** P0
**Source:** `F/application/cost/projectcost/ingest/ExecuteCollectionUseCase.java:82-128`
**Plain English:** Before any batch is created, the system checks the period window (RULE-01) and that payroll for that company and period has been calculated. It then takes a 3-second lock for the period×company and locks the period progress row in its own committed transaction. Only after that does it create or reset the batch and send the MQ job.
**Specification:**
  Given period 2026-07 is 预关账 but salary calculation for C01 is not finished
  When collection is started
  Then it is rejected with COST_COLLECT_SALARY_NOT_READY and the readiness reason. No lock is taken, no batch is created, and no MQ message is sent.
**Parameters:** lock `lock:cost-collection:create:{period}:{company}`, wait 3 s, lease 30 s
**Edge cases handled:** A busy lock gives COST_COLLECT_INITIATE_BUSY. The period is locked before the batch is touched, so a confirmed period cannot leave a QUEUED orphan batch. An MQ send failure leaves the batch QUEUED (logged, not thrown). Year-end adjustment periods and missing periods pass the salary gate (per port Javadoc, not verified).
**Confidence:** Medium. The salary-readiness rules live in SalaryCalculationReadinessPort, which I did not read. SME question: "Which payroll state counts as 'calculated' per company, and should period 13 really bypass it?"

### RULE-09: Allocation batch lifecycle
**Category:** Lifecycle
**Priority:** P0
**Source:** `F/domain/cost/projectcost/allocation/CostAllocationBatch.java:69-134`
**Plain English:** An overhead allocation batch is created QUEUED and moves to RUNNING. A trial run ends as DRAFT and a full run ends as EFFECTIVE, both with progress at 100. Any error ends it as FAILED. Only QUEUED or RUNNING batches can be cancelled, and progress never goes backwards.
**Specification:**
  Given a full allocation batch in RUNNING at progress 55
  When the user cancels it
  Then the status becomes CANCELLED(5) with finishedAt set. If the worker's final CAS then loses, the whole full-commit transaction rolls back and the batch is not made EFFECTIVE.
**Parameters:** statuses DRAFT 0, EFFECTIVE 1, QUEUED 2, RUNNING 3, FAILED 4, CANCELLED 5; progress checkpoints 10/30/55/75/90/100
**Edge cases handled:** A non-dispatchable batch is skipped without changing status (orchestrator:92-97). A failed invariant check marks the batch FAILED so no misleading draft is left. An MQ send failure leaves the batch QUEUED with no automatic recovery; the only fix is a manual cancel and resend.
**Suspected defect:** `DeleteTrialAllocationUseCase:23-30` deletes a trial batch without checking its status, so a QUEUED or RUNNING trial can be deleted while the worker is still writing it.
**Confidence:** High

### RULE-10: Full allocation takes effect atomically with pool write-back and step advance
**Category:** Lifecycle
**Priority:** P0
**Source:** `F/application/cost/projectcost/allocation/CostAllocationBatchOrchestratorImpl.java:152-208`
**Plain English:** A full allocation becomes EFFECTIVE in one transaction that also: re-checks that the period is still at step 1 and has no other effective full allocation, saves the allocation rows, marks the picked pool lines as allocated (including accrual-reversal mirror lines), and moves the period from step 1 to 2. If any part fails, nothing is committed.
**Specification:**
  Given progress next_step=1 and no effective FULL batch for 2026-07/C01
  When full allocation batch OH-{period}-F3 finishes computing
  Then the rows are saved, the picked pool lines become allocated for 2026-07, next_step becomes 2, and the batch becomes EFFECTIVE. A second concurrent full batch hits the filtered unique index and fails with COST_ALLOCATION_EFFECTIVE_FULL_EXISTS.
**Parameters:** batch number `OH-{periodId}-F{seq}` (sequence counts deleted and failed rows); start lock `lock:cost-allocation:create:{p}:{c}` 3 s / 30 s
**Edge cases handled:** At start: next_step must be 1, no effective full allocation may exist, and no batch may be in progress (ExecuteFullAllocationUseCase:70-132). At commit: a stale progress row gives NOT_IN_ALLOCATION_STEP and a CAS loss gives PROGRESS_ADVANCED.
**Confidence:** High

### RULE-11: Creating a carry draft (asynchronous)
**Category:** Lifecycle
**Priority:** P0
**Source:** `F/application/cost/projectcost/carry/CarryBatchService.java:102-122`
**Plain English:** Creating a carry draft checks the period window and the reconciliation gate (no pending unreconciled expense rows above the threshold). Normal carry types also occupy their step (RULE-04); red reversal skips the step gate. It then creates an empty DRAFT·QUEUED batch and sends the line-selection job only after the transaction commits.
**Specification:**
  Given period 2026-07/C01 is 预关账, next_step=2 (idle), and 0 pending reconciliation rows
  When a WIP-enter draft (carry_type=0) is created
  Then batch `CF0-{periodId}-C01-{epochMillis}` is created as DRAFT·QUEUED, step 2 becomes running, and the draft job is sent after commit
  Given 3 pending unreconciled rows and threshold 0
  Then the draft is rejected with COST_CARRY_HAS_UNRECONCILED (3)
**Parameters:** `fst.cost.recon.unreconciled-threshold` (default 0); red-reversal batch number `HC-{period}-{company}-R{n}`, where n = existing red batches + 1
**Edge cases handled:** A filtered unique index allows one live forward batch per period×company×type with status 0–3 (POSTED still counts). Red reversal allows only one batch in flight with status 0–2, and POSTED frees the slot. A duplicate gives COST_CARRY_ALIVE_BATCH_EXISTS. An MQ send failure leaves the batch QUEUED for the startup reconciler.
**Confidence:** High

### RULE-12: Carry batch status and phase state machine
**Category:** Lifecycle
**Priority:** P0
**Source:** `F/domain/cost/projectcost/carry/CarryBatch.java:98-283`
**Plain English:** A carry batch's status moves DRAFT → CONFIRMED → POSTING → POSTED, and a draft can branch to CANCELLED. A separate phase field (QUEUED, RUNNING, COMPLETED, FAILED, INTERRUPTED) tracks the asynchronous draft generation and the asynchronous posting. Exclude, confirm and opening the detail are only allowed on DRAFT·COMPLETED (draft ready).
**Specification:**
  Given a batch in DRAFT·RUNNING (lines still being generated)
  When finance clicks confirm
  Then it is rejected with COST_CARRY_DRAFT_NOT_READY
  Given POSTING·FAILED
  When it is re-run
  Then the phase goes back to RUNNING, keeping the processed cursor and startedAt
**Parameters:** status DRAFT 0, CONFIRMED 1, POSTING 2, POSTED 3, REVERSED 4, CANCELLED 5; last_error is truncated to 2000 characters
**Edge cases handled:** Generation may be dispatched in DRAFT·{QUEUED, RUNNING (orphan), FAILED, INTERRUPTED}. Posting may be dispatched in CONFIRMED or POSTING·{FAILED, INTERRUPTED}. POSTED is only allowed from POSTING·COMPLETED. Confirm freezes the totals and baseline.
**Suspected defect:** REVERSED (4) is never assigned by any code; only OpeningEventCarryBridge:275 reads it. Batch-level reversal is not implemented, and red reversal works line by line through carry_type=3.
**Confidence:** High

### RULE-13: Carry confirm re-checks the gates and dry-runs the vouchers
**Category:** Validation
**Priority:** P0
**Source:** `F/application/cost/projectcost/carry/CarryBatchService.java:196-235`
**Plain English:** Confirming a carry draft re-checks that the draft is ready, the period window, and the reconciliation gate. It then dry-renders all vouchers the way they will be stored (strict debit/credit balance, required department and cost-type mappings) and refuses to confirm if any voucher would fail.
**Specification:**
  Given a DRAFT·COMPLETED write-off batch where department D017 has no U8C mapping on 42 lines
  When finance confirms
  Then it is rejected with COST_CARRY_CONFIRM_VOUCHER_INVALID and a message naming D017 and the 42 lines. The batch stays DRAFT and no MQ message is sent.
**Parameters:** none hard-coded
**Edge cases handled:** A batch with every line excluded renders no vouchers and passes. A CAS loss on confirm throws IllegalStateException. The confirm is sent to MQ after the status is persisted; if the send fails, the batch is stuck in CONFIRMED until `rerun` or the startup reconciler.
**Confidence:** High

### RULE-14: Cancelling a carry draft
**Category:** Lifecycle
**Priority:** P1
**Source:** `F/application/cost/projectcost/carry/CarryBatchService.java:266-301`
**Plain English:** Only a DRAFT batch (generating or ready) can be cancelled. Cancelling sets CANCELLED, frees the step gate (normal types only), and deletes the selected lines, either directly or by the generation worker when it notices the cancel signal. No accounting trace is left.
**Specification:**
  Given a WIP-enter batch in DRAFT·RUNNING whose generation lock is held by the worker
  When finance cancels
  Then the status becomes CANCELLED and step 2 becomes idle. Because the lock is held, line deletion is left to the worker.
**Parameters:** CANCEL_CAS_RETRY = 3; generation lock `lock:cost-carry:gen:{batchId}`
**Edge cases handled:** An already CANCELLED batch is an idempotent success. Three CAS losses in a row give COST_CARRY_STEP_OCCUPY_CONFLICT. CONFIRMED or later batches cannot be cancelled (the aggregate throws).
**Confidence:** High

### RULE-15: Posting phases and the completion condition
**Category:** Lifecycle
**Priority:** P0
**Source:** `F/application/cost/projectcost/carry/CarryConfirmOrchestratorImpl.java:36-78`
**Plain English:** Posting a confirmed batch has two phases. Phase 1 posts the WIP effect of each line, claiming lines by flipping post_status 0→1. Phase 2 generates the batch's summary vouchers and their outbox entries in one transaction. The batch only reaches POSTING·COMPLETED when the count of non-excluded unposted lines is 0.
**Specification:**
  Given a CONFIRMED batch with 1,500 lines
  When the confirm job runs
  Then status becomes POSTING·RUNNING and processed counters reset to 0. Lines are posted in 500-line chunks in parallel (≤ 200 lines would use a single transaction). One voucher per company×voucher word×department is generated, then the batch becomes POSTING·COMPLETED.
**Parameters:** `fst.cost.carry.posting.single-tx-threshold` = 200; `fst.cost.carry.posting.chunk-size` = 500; consumer lock `lock:cost-carry:post:{batchId}`
**Edge cases handled:** A failed chunk rolls back only that chunk; the batch becomes POSTING·FAILED and resumes from the cursor. Interruption gives INTERRUPTED. Missing mappings in phase 2 roll back every voucher, so none are stored (fail-closed). On startup, an orphan POSTING·RUNNING batch with no lock holder is set to INTERRUPTED and resent (CarryPostingReconciler:75-91). FAILED batches are not retried automatically.
**Confidence:** High

### RULE-16: A batch becomes POSTED only when U8C has confirmed every voucher; then the step advances
**Category:** Lifecycle
**Priority:** P0
**Source:** `F/application/cost/projectcost/carry/CarryBatchFinalizer.java:75-135`
**Plain English:** After each voucher is confirmed by U8C, the system checks whether the batch is POSTING·COMPLETED and has no unconfirmed vouchers. If so, the batch becomes POSTED and the period step advances. For write-off batches it then also writes back the manual write-off list and runs the WIP aging-impairment sweep.
**Specification:**
  Given write-off batch B in POSTING·COMPLETED with 12 vouchers, 11 of them CONFIRMED
  When U8C confirms the 12th voucher
  Then B becomes POSTED, period next_step goes from 4 to 99, the manual write-off list is written back, and the impairment sweep runs
**Parameters:** none
**Edge cases handled:** A batch with no vouchers is treated as fully confirmed, and the confirm consumer calls tryFinalize directly (CarryConfirmJobConsumer:79-90). Concurrent finalization flips POSTED only once (CAS). Red reversal does not advance the step. Failures in the write-back or the sweep are logged and do not undo POSTED.
**Confidence:** High

### RULE-17: U8C voucher delivery and the opening-year local bypass
**Category:** Policy
**Priority:** P0
**Source:** `F/application/cost/projectcost/carry/U8cVoucherPostHandler.java:88-140`
**Plain English:** Each voucher is delivered to U8C over HTTP outside any transaction. On success the voucher becomes CONFIRMED with the U8C receipt number. Vouchers for periods whose year is before the configured threshold are never sent to U8C; they are confirmed locally with receipt "OPENING-LOCAL".
**Specification:**
  Given a voucher belonging to a 2025 period and threshold 2026
  When the delivery task runs
  Then no HTTP call is made, the voucher becomes CONFIRMED with u8c_voucher_no="OPENING-LOCAL", and batch finalization is attempted
  Given a 2026 period
  Then the payload is posted to U8C with idempotency key = external_no
**Parameters:** `fst.cost.u8c.voucher.opening-year-max-exclusive` (default 2026; any value ≤ 0 falls back to 2026)
**Edge cases handled:** A missing voucher gives the failure VOUCHER_NOT_FOUND. If the period cannot be resolved, delivery goes to U8C (conservative). A failure during finalization does not fail the delivery.
**Suspected defect:** The voucher status only ever moves DRAFT(0) → CONFIRMED(2). SENT(1) and FAILED(3) are never written, even though VoucherWriterImpl:69 says "回写 SENT/CONFIRMED", so delivery state only exists on the delivery task.
**Confidence:** High

### RULE-18: Voucher delivery retry, suspension and manual re-delivery
**Category:** Policy
**Priority:** P1
**Source:** `F/application/cost/projectcost/voucher/VoucherDeliveryManageService.java:38-82`
**Plain English:** Each voucher gets one outbox task, created as PENDING with a maximum of 5 retries. A failure moves it to RETRYING with backoff. A terminal failure or reaching the retry limit moves it to DEAD (suspended). Manual retry is allowed only for unconfirmed vouchers whose task is PENDING or RETRYING. Force-retry is allowed only for DEAD tasks and resets them to PENDING.
**Specification:**
  Given a voucher whose task has failed 5 times (retry_count=5, max=5, status DEAD)
  When a user clicks Retry
  Then it is rejected with COST_VOUCHER_RETRY_NOT_ALLOWED
  When the user clicks Force retry
  Then the task becomes PENDING and is dispatched immediately
**Parameters:** MAX_RETRY = 5 (`F/infrastructure/cost/projectcost/carry/OutboxWriterImpl.java:29`); DEAD when newRetry ≥ max or the failure is terminal (ReliableDeliveryDispatcher:221)
**Edge cases handled:** Outbox and voucher writes are idempotent on external_no. A concurrent status change gives COST_VOUCHER_RETRY_CONFLICT. In a multi-voucher retry, each voucher is checked separately and the first invalid one throws.
**Suspected defect:** The seed spec says external_no is `CF-{batchNo}-{companyCode}-{seq}`, but the code (VoucherWriterImpl:115-120) uses `CF-{batchNo}-{voucherWord}-{deptCode}`, and `CF-{batchNo}-{returnSlipId}-{deptCode}` for red reversal.
**Confidence:** High

### RULE-19: WIP write-off flips the entry from in-WIP to relieved and books a P&L line
**Category:** Lifecycle
**Priority:** P0
**Source:** `F/infrastructure/cost/projectcost/carry/WipLedgerPosterImpl.java:272-304`
**Plain English:** A write-off (carry_type=2) can only move a WIP entry from status 0 (in WIP) to 1 (relieved). It stamps written_off_at, adds a movement of type 2, adds a positive P&L line on the main-cost account, and marks the paired revenue (output value) document as written off.
**Specification:**
  Given WIP entry E (status 0, version 4, ¥80,000.00 excluding tax) paired with output value CV-1001
  When the write-off line is posted
  Then E becomes status 1 at version 5, a type-2 movement 0→1 is added, a positive P&L line (trigger_type 0) is inserted, and CV-1001 gets write_off_flag=1
**Parameters:** CAS batch size 500; P&L insert batch size 80
**Edge cases handled:** An entry that is already relieved or has a version mismatch fails the CAS, and the whole chunk rolls back. A COST_ONLY line has no paired document, so nothing is written back. A missing cost-ledger row throws.
**Confidence:** High. This file is in infrastructure, outside the listed paths, but it is the only place where WIP status changes.

### RULE-20: Red reversal returns a relieved WIP entry to in-WIP and books one negative P&L line per positive line
**Category:** Lifecycle
**Priority:** P0
**Source:** `F/infrastructure/cost/projectcost/carry/WipLedgerPosterImpl.java:435-502`
**Plain English:** Red reversal (carry_type=3) moves a WIP entry from relieved (1) back to in-WIP (0) and clears written_off_at. It adds a reverse movement of type 3 and a negated copy of the original positive P&L line, then marks the return document as written off. Each positive line can only be reversed once.
**Specification:**
  Given entry E relieved by batch line L (positive P&L line P = ¥80,000.00)
  When red-reversal line R (reversal_of_line_id=L) is posted
  Then E goes back to status 0 (stage unchanged), a type-3 movement 1→0 is added, a negative line (trigger_type 2, reversal_of_id=P, −¥80,000.00) is inserted, and the return document gets write_off_flag=1
**Parameters:** none
**Edge cases handled:** Zero or more than one type-2 movement for L throws. Zero or more than one positive line throws. If P has already been reversed, it throws "不可重复红冲". An entry that is not relieved fails the CAS.
**Suspected defect:** Seed doc 09 rule PLT-R02 says "红冲无人工入口" (no manual entry for red reversal). In code, red reversal is a manual draft-and-confirm flow (RULE-21).
**Confidence:** High

### RULE-21: Red reversal confirm requires a valid reason and a successful red-voucher preview
**Category:** Validation
**Priority:** P0
**Source:** `F/application/cost/projectcost/carry/RevenueRedReversalCommandUseCase.java:42-55`
**Plain English:** Confirming a red reversal batch needs a reason code from the allowed list. The red-voucher preview must succeed (original write-off lines, department mappings and cost-type accounts all present). Then the reason is recorded and the standard carry confirm gates (RULE-13) apply.
**Specification:**
  Given a ready red-reversal draft whose original write-off line has no department mapping
  When finance confirms with reason "<valid code>"
  Then the preview throws COST_CARRY_VOUCHER_PREVIEW_FAILED and the batch stays DRAFT, editable and cancellable
**Parameters:** allowed reasons come from `RedReversalReasonEnum`
**Edge cases handled:** An invalid reason gives COST_CARRY_RED_REVERSAL_REASON_INVALID before any gate runs. The reason is saved before confirm, so a failed confirm leaves the batch DRAFT with the reason kept; it can be overwritten on retry.
**Suspected defect:** The seed spec requires the "财务主管" (finance supervisor) role. The controller only checks the generic permission `cost:carry-batch:confirm` (RevenueRedReversalController:149), which is the same one used for normal carry confirms.
**Confidence:** Medium. SME question: "Must red-reversal confirm be limited to finance supervisors, separately from normal carry confirm?"

### RULE-22: WIP stage assignment and advancement
**Category:** Lifecycle
**Priority:** P0
**Source:** `F/domain/cost/projectcost/carry/CarrySelectorImpl.java:54-124`
**Plain English:** On entry to WIP, cost goes to stage 1 (pre-contract) if the contract has not been returned, stage 3 (completed) if the contract is back and percentage of completion (POC) is exactly 100, and stage 2 (in progress) otherwise. Advancement goes 1→2 or 1→3 only after the contract is back, and 2→3 only at POC = 100. Without a returned contract, cost never leaves stage 1, even at POC = 100.
**Specification:**
  Given a WIP entry at stage 1, contract not returned, POC 100
  When stage-advance selection runs
  Then it is not selected (stays at stage 1)
  Given the contract is returned and POC is 100
  Then it is selected for 1→3
**Parameters:** POC_COMPLETED = 100 (strict equality); stages 1 = PRE_CONTRACT, 2 = IN_PROGRESS, 3 = COMPLETED, 4 = IMPAIRMENT
**Edge cases handled:** A null POC is treated as not complete. A null from-stage is skipped. POC is taken from the snapshot at period month-end.
**Confidence:** High

### RULE-23: Aged in-WIP cost is frozen into impairment (stage 4) after write-off posting
**Category:** Policy
**Priority:** P1
**Source:** `F/application/cost/projectcost/carry/WipImpairmentSweepService.java:69-175`
**Plain English:** After a write-off batch is POSTED, any in-WIP entry at stage 1, 2 or 3 whose WIP-entry period ended at least N days before the current period end is moved to stage 4 (impairment reserve) and gets a type-7 movement with no voucher. Stage 4 entries are excluded from all write-off channels.
**Specification:**
  Given entry E entered in a period ending 2024-06-30, stage 2, in WIP
  When the 2026-07 write-off batch (period end 2026-07-31) becomes POSTED
  Then the gap is 761 days, which is ≥ 720, so E moves to stage 4 with movement 2→4
**Parameters:** `fst.cost.wip.carry.impairment-aging-days` = 720 (refreshable; values ≤ 0 fall back to 720); page size 2000; update chunk 1000
**Edge cases handled:** A period with no end date (for example period 13) skips the sweep. An entry with no end date for its entry period is never treated as aged. Concurrent losers get no movement.
**Suspected defect:** Candidates are filtered with `entered_period_id <= periodId`, which assumes period IDs increase in date order; that is not guaranteed if IDs are snowflake or loaded out of order.
**Confidence:** Medium. SME question: "Is 720 days the approved impairment threshold, and should it apply to stage 1 (pre-contract) cost?"

### RULE-24: Opening-period collection confirm can bulk-exempt all open errors
**Category:** Policy
**Priority:** P0
**Source:** `F/application/cost/projectcost/opening/OpeningMonthExecuteUseCase.java:115-129`
**Plain English:** In the opening-balance (期初) flow, confirming collection while technical or unexempted business errors exist is blocked unless `forceConfirm=true`. With that flag, every OPEN error, technical ones included, is marked RESOLVED+EXEMPTED (left out of the ledger) before the normal confirmation gate runs.
**Specification:**
  Given opening period 2025-03/C01 with 4 open technical and 10 unexempted business errors
  When confirm is called with forceConfirm=true
  Then all 14 errors become EXEMPTED, and the standard gate (RULE-07) then passes on the error criteria
**Parameters:** forceConfirm (request flag)
**Edge cases handled:** forceConfirm=false gives HTTP 400 with the error counts. WIP entry from the opening flow requires next_step = 2 exactly (lines 253-264).
**Suspected defect:** This bypasses the normal-flow rule that technical errors "must be resolved by IT" (CostCollectionBatch:381-383). The exemptions are permanent.
**Confidence:** Medium. SME question: "Is bulk exemption of technical errors acceptable for opening periods, and who may set forceConfirm?"

### RULE-25: Legacy carry-forward lifecycle (待结转 → 结转中 → 已结转), still exposed by controllers
**Category:** Lifecycle
**Priority:** P0
**Source:** `F/service/accounting/CostCarryForwardServiceImpl.java:109-157`
**Plain English:** The legacy monthly cost carry forward refuses the current or a future month. It sets the master record to "结转中" (in progress) and pushes vouchers company by company. The master record becomes "已结转" (done) only when no detail row is left unwritten-off. While a record is in progress, only its creator can resume it; anyone else gets "正在结转中！".
**Specification:**
  Given today is 2026-10-14
  When cost carry forward is requested for 2026-10
  Then it is rejected with "不能结转当月或未来的工时！"
  Given 2026-09 has 3 companies and 1 fails
  Then the master record stays 结转中, the 2 successful companies' rows get cost_write_off_flag=1, and an HTML error listing the failed company is returned
**Parameters:** CarryForwardStatus 待结转 / 结转中 / 已结转; CarryForwardType 成本 / WIP / WIP二次
**Edge cases handled:** For a record that is already 已结转, getTobeCarryForward refuses with "本月已结转！". A stale 待结转 record is deleted and rebuilt (lines 214-249).
**Suspected defect:**
1. In `F/service/wip/CarryForwardServiceImpl.java:219-222` the U8C call is commented out. updateResult is hard-coded SUCCESS, and the 2nd company processed always gets the fake error "U8C错误信息:XXXXXXXXXX", so no real voucher is ever posted.
2. In `F/service/wip/WipServiceImpl.java:445-447` the "WIP结转还未完成" guard checks the 成本 record's status instead of the WIP record's status (copy-paste error).
3. `carryForward` does not check the current status before setting 结转中, and the master record is looked up by year and month only, with no company filter.

**Confidence:** Medium. SME question: "Are the legacy CostController and WipController carry-forward endpoints still reachable in prod, or fully replaced by the psa_carry_batch flow?"

---
**Coverage:** I read in full: domain/cost batch, period, carry (CarryBatch, CarrySelectorImpl) and CostAllocationBatch; application/cost period, ingest/ExecuteCollectionUseCase, batch orchestrator, allocation full/cancel/delete/orchestrator, carry (Service, Finalizer, U8cVoucherPostHandler, ConfirmOrchestrator, ChunkProcessor, PostingPhaseRunner, VoucherAggregator, PostingReconciler, RedReversal command, WipImpairmentSweep), voucher/VoucherDeliveryManageService, and opening/OpeningMonthExecuteUseCase; all of mq/consumer/cost; service/accounting AccountingPeriodServiceImpl. Outside the listed paths I also read the related infrastructure (WipLedgerPosterImpl, VoucherWriterImpl, OutboxWriterImpl, AccountingPeriodPortAdapter).
**Skimmed only:** legacy service/wip and service/accounting/CostCarryForwardServiceImpl (state parts only); mq/producer/cost (thin senders); the openspec seed specs and the seed rules doc 09.
**Possibly missed:** the trial-allocation start, CarryDraftOrchestratorImpl/CarryLineSelectorImpl generation details, the labor-reclass run state machine (LaborReclassRunState), opening auto-carry/event bridge and range-run steps, manual write-off list states, WIP snapshot status, and the legacy WipServiceImpl second-pass voucher flow. No instruction-like text aimed at automated tools was found in the files read.