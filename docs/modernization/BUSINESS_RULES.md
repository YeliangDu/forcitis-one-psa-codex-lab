# BUSINESS_RULES — psa

日期：2026-10-14 · 方式：轻量 Method B。5 个核心域（工时与资源、项目成本与核算、售前与报价、交付预算与收入、审批 platform.approval），每个域按计算 / 校验 / 状态三个视角各派一个 business-rules-extractor（售前把校验和状态合为一个视角，共 14 个）。以 `docs/domain/00_战略设计/09-业务规则清单-全域汇总.md` 及各域文档为种子，**每条规则都落到代码引用上**。合并脚本 `rules/merge_rules.py` 逐条校验引用的文件存在、行号在范围内，并把同一处代码的重复陈述合并。差旅报销域未纳入本轮（已有 `docs/domain/02_项目核算域_accounting/travel/04-差旅报销域-业务规则.md`），按 brief 在对应阶段开工前补抽。

**合计 315 条**（抽取 346 张卡；引用校验未通过而丢弃 0 张；重复陈述合并 31 张）。按类别：Validation 103，Calculation 93，Lifecycle 70，Policy 49。按优先级：P0 189，P1 107，P2 19。需要 SME 确认的有 153 条，其中 P0 92 条（置信度不足 High，或带有疑似缺陷）。

## 汇总表

| ID | 域 | 名称 | 类别 | 优先级 | 来源 | 置信度 |
|---|---|---|---|---|---|---|
| RULE-001 | 工时与资源 | Task package actual hours = total approved hours (recalculated in full) | Calculation | P0 | `ProjectTaskApiImpl.java:73-105` | High |
| RULE-002 | 工时与资源 | Which daily wage is used (before 2020 vs from 2020 on) | Calculation | P0 | `HourlyRateServiceImpl.java:329-348` | Medium |
| RULE-003 | 工时与资源 | Approval writes the cost and bill amounts (approved hours × locked rate) | Calculation | P0 | `ManHourApproveStatusService.java:241-271` | High |
| RULE-004 | 工时与资源 | Human-days split of a person's approved day across projects | Calculation | P0 | `ManHourEndServiceImpl.java:57-109` | Medium |
| RULE-005 | 工时与资源 | Rates, company code and cost centre are fixed at submission | Calculation | P0 | `ManHourServiceImpl.java:1670-1696` | High |
| RULE-006 | 工时与资源 | Project labour cost = daily wage × human days, excluding admin/support posts | Calculation | P0 | `ManHourServiceImpl.java:2090-2128` | Medium |
| RULE-007 | 工时与资源 | Rate lookup by employee grade, salary band and city on the work date | Calculation | P0 | `TimeSheetRateServiceImpl.java:62-104` | High |
| RULE-008 | 工时与资源 | Mobile (H5) submission records approved hours = declared hours | Calculation | P1 | `ManHourServiceImpl.java:463-474` | Medium |
| RULE-009 | 工时与资源 | Remaining hours (ETC) of a project task package | Calculation | P1 | `ManHourServiceImpl.java:1258-1266` | High |
| RULE-010 | 工时与资源 | Converting plan person-days to hours (and back) | Calculation | P1 | `PlanWorksServiceImpl.java:488-517` | Medium |
| RULE-011 | 工时与资源 | Budget cost already used, by budget code | Calculation | P1 | `TaskServiceImpl.java:1527-1535` | Medium |
| RULE-012 | 工时与资源 | Nightly check of task-package actual hours | Calculation | P1 | `TaskActualHoursReconJob.java:46-82` | High |
| RULE-013 | 工时与资源 | Average daily hours of a plan line | Calculation | P2 | `PlanServiceImpl.java:63-87` | High |
| RULE-014 | 工时与资源 | Resource-list busyness = assigned task hours ÷ (working days × 7.5) | Calculation | P2 | `ResourceServiceImpl.java:134-204` | Medium |
| RULE-015 | 工时与资源 | Gantt expected hours over the forecast window | Calculation | P2 | `ResourceServiceImpl.java:373-409` | Medium |
| RULE-016 | 工时与资源 | Task hours spread evenly over working days and clipped to the window | Calculation | P2 | `ResourceServiceImpl.java:424-447` | High |
| RULE-017 | 工时与资源 | Remaining-hours estimate for overdue tasks | Calculation | P2 | `ResourceServiceImpl.java:456-488` | Medium |
| RULE-018 | 工时与资源 | Busyness colour bands | Calculation | P2 | `ResourceServiceImpl.java:876-886` | High |
| RULE-019 | 工时与资源 | Default task duration from assigned hours | Calculation | P2 | `TaskServiceImpl.java:489-508` | High |
| RULE-020 | 工时与资源 | Approval status transition rules; approvers act only on their own lines | Validation | P0 | `ManHourApproveStatusService.java:41-183` | High |
| RULE-021 | 工时与资源 | Conditions for a timesheet line to be postable to cost | Validation | P0 | `ManHourPostingCheckServiceImpl.java:117-216` | High |
| RULE-022 | 工时与资源 | Who can see whose timesheets | Validation | P0 | `ManHourServiceImpl.java:216-222` | High |
| RULE-023 | 工时与资源 | Only the owner can edit or resubmit, and only when rejected, cancelled or returned | Validation | P0 | `ManHourServiceImpl.java:800-818` | High |
| RULE-024 | 工时与资源 | On a workday, booked hours plus leave must be at least 7.5h | Validation | P0 | `ManHourServiceImpl.java:1215-1226` | High |
| RULE-025 | 工时与资源 | One timesheet per employee per calendar day | Validation | P0 | `ManHourServiceImpl.java:1235-1246` | High |
| RULE-026 | 工时与资源 | The remaining-hours (ETC) gate on project-task packages | Validation | P0 | `ManHourServiceImpl.java:1258-1310` | High |
| RULE-027 | 工时与资源 | The three org fields are mandatory on every line | Validation | P0 | `ManHourServiceImpl.java:1327-1351` | High |
| RULE-028 | 工时与资源 | Fields required to send, by work-package type (drafts exempt) | Validation | P0 | `ManHourServiceImpl.java:1360-1393` | High |
| RULE-029 | 工时与资源 | Every line must have an approver before it can be sent | Validation | P0 | `ManHourServiceImpl.java:1400-1418` | High |
| RULE-030 | 工时与资源 | Timesheets can only be booked to a work package the employee is allowed to use right now | Validation | P0 | `ManHourServiceImpl.java:1434-1469` | High |
| RULE-031 | 工时与资源 | Every line must match exactly one standard-cost rate and one billing rate | Validation | P0 | `ManHourServiceImpl.java:1479-1507` | High |
| RULE-032 | 工时与资源 | Which project-task packages are bookable | Validation | P0 | `ManHourServiceImpl.java:2295-2411` | High |
| RULE-033 | 工时与资源 | Opportunity visibility for booking depends on post channel and a 90-day window after a win | Validation | P1 | `OpportunityFilterUtils.java:66-90` | High |
| RULE-034 | 工时与资源 | A whole day of leave blocks sending a timesheet for that day | Validation | P1 | `ManHourServiceImpl.java:858-868` | Medium |
| RULE-035 | 工时与资源 | On resubmit, unchanged approved lines are kept, and a resubmit with nothing to approve is refused | Validation | P1 | `ManHourServiceImpl.java:953-1003` | High |
| RULE-036 | 工时与资源 | Today's timesheet cannot be submitted before 09:00 (Shanghai time) | Validation | P1 | `ManHourServiceImpl.java:1184-1191` | Medium |
| RULE-037 | 工时与资源 | Daily declared-hours cap of 15 and positive hours per line | Validation | P1 | `ManHourServiceImpl.java:1196-1211` | High |
| RULE-038 | 工时与资源 | OH, visit, lead and customer work packages | Validation | P1 | `ManHourServiceImpl.java:2429-2620` | Medium |
| RULE-039 | 工时与资源 | Role-gated "indirect" project and opportunity work packages | Validation | P1 | `ManHourServiceImpl.java:2705-2756` | Medium |
| RULE-040 | 工时与资源 | Withdrawn timesheets can be deleted only on non-workdays or full-leave days | Validation | P1 | `ManHourServiceImpl.java:2940-2957` | High |
| RULE-041 | 工时与资源 | Tasks can be accepted or refused only while pending | Validation | P1 | `ResourceServiceImpl.java:210-294` | High |
| RULE-042 | 工时与资源 | Integrity rules applied before any task is created | Validation | P1 | `TaskValidator.java:96-143` | High |
| RULE-043 | 工时与资源 | Row decisions from BPM task-completion messages | Lifecycle | P0 | `TimesheetApprovalResultHandler.java:54-139` | High |
| RULE-044 | 工时与资源 | End-of-process result decides the header outcome | Lifecycle | P0 | `TimesheetProcessResultHandler.java:46-92` | High |
| RULE-045 | 工时与资源 | Pending becomes In-approval only after BPM has actually started or resumed the process | Lifecycle | P0 | `ProcessToBeSentServiceImpl.java:163-189` | High |
| RULE-046 | 工时与资源 | An approver's personal decision only touches that approver's own rows | Lifecycle | P0 | `ManHourApproveStatusService.java:56-72` | High |
| RULE-047 | 工时与资源 | How row statuses follow a header change | Lifecycle | P0 | `ManHourApproveStatusService.java:82-112` | High |
| RULE-048 | 工时与资源 | Timesheet header approval-status state machine (allowed transitions) | Lifecycle | P0 | `ManHourApproveStatusService.java:163-183` | High |
| RULE-049 | 工时与资源 | Who may re-initiate (edit and resubmit) a timesheet | Lifecycle | P0 | `ManHourServiceImpl.java:800-831` | High |
| RULE-050 | 工时与资源 | "Save" keeps a draft; "Send" queues the approval process | Lifecycle | P0 | `ManHourServiceImpl.java:858-923` | High |
| RULE-051 | 工时与资源 | Row approver is assigned at submit by work-category type | Lifecycle | P0 | `ManHourServiceImpl.java:1702-1763` | High |
| RULE-052 | 工时与资源 | Secondary header "status" field follows the approval status | Lifecycle | P1 | `ManHourApproveStatusService.java:185-190` | High |
| RULE-053 | 工时与资源 | Revision snapshots and their event types | Lifecycle | P1 | `ManHourRevisionService.java:118-175` | High |
| RULE-054 | 工时与资源 | Unchanged, already-approved rows keep their approval on resubmit | Lifecycle | P1 | `ManHourServiceImpl.java:953-1003` | High |
| RULE-055 | 工时与资源 | New timesheet starts in Pending with a TS-prefixed number and a locked company code | Lifecycle | P1 | `ManHourServiceImpl.java:1595-1621` | High |
| RULE-056 | 工时与资源 | A returned sheet resumes the same BPM instance; a cancelled or ended one starts a new instance | Lifecycle | P1 | `ManHourServiceImpl.java:2815-2827` | High |
| RULE-057 | 工时与资源 | Only a withdrawn sheet on a non-work day or a full-leave day may be deleted | Lifecycle | P1 | `ManHourServiceImpl.java:2940-2985` | High |
| RULE-058 | 工时与资源 | Cost and bill rates are locked at submit, not at approval | Policy | P0 | `ManHourServiceImpl.java:1670-1696` | High |
| RULE-059 | 工时与资源 | Logged hours freeze task staffing from the moment they are filed | Policy | P1 | `ManHourItemMapper.java:50-84` | High |
| RULE-060 | 工时与资源 | Every header status change is written to an append-only audit log | Policy | P1 | `ManHourApproveStatusService.java:119-132` | High |
| RULE-061 | 工时与资源 | Applying an end result is idempotent and attributed to the submitter | Policy | P1 | `ManHourBpmApplyService.java:54-113` | High |
| RULE-062 | 工时与资源 | Full-day leave threshold (450 minutes) | Policy | P1 | `ManHourServiceImpl.java:858-868` | High |
| RULE-063 | 工时与资源 | Which project task packages a person can log time to | Policy | P1 | `ManHourServiceImpl.java:2402-2411` | High |
| RULE-064 | 工时与资源 | Working-day calendar with fallback when a date is missing | Policy | P1 | `ResourceServiceImpl.java:1099-1106` | Medium |
| RULE-065 | 工时与资源 | Today's timesheet opens at 09:00 Beijing time | Policy | P2 | `ManHourServiceImpl.java:1184-1195` | Medium |
| RULE-066 | 项目成本与核算 | OH basis is timesheet (source_type 0) not-tax cost; period 13 uses the full-year base | Calculation | P0 | `AllocationOrchestrator.java:108-146` | High |
| RULE-067 | 项目成本与核算 | Three-tier project profit and rates | Calculation | P0 | `QueryProjectProfitUseCase.java:551-618` | High |
| RULE-068 | 项目成本与核算 | OH tier routing and carry-forward when the labour base is zero | Calculation | P0 | `OhAllocationCalculator.java:44-110` | High |
| RULE-069 | 项目成本与核算 | OH allocation – share each overhead pot by direct-labour cost share, with rounding remainder on the largest line | Calculation | P0 | `OhAllocationCalculator.java:196-352` | High |
| RULE-070 | 项目成本与核算 | Reversing an OH accrual mirrors the original allocation in proportion | Calculation | P0 | `ReversalMirrorCalculator.java:31-90` | High |
| RULE-071 | 项目成本与核算 | WIP stage on entry and stage advance (POC exactly 100 and contract returned) | Calculation | P0 | `CarrySelectorImpl.java:53-124` | High |
| RULE-072 | 项目成本与核算 | WIP write-off selection – one revenue confirmation releases one whole WIP entry; leftovers go cost-only or wait | Calculation | P0 | `CarrySelectorImpl.java:126-237` | Medium |
| RULE-073 | 项目成本与核算 | Year-end bonus is split to project terms by the person's annual hours (cent-exact largest remainder) | Calculation | P0 | `AnnualBonusSplitCalculator.java:66-123` | High |
| RULE-074 | 项目成本与核算 | Cost routing chain and how expense nature is set | Calculation | P0 | `CostRoutingService.java:101-235` | High |
| RULE-075 | 项目成本与核算 | Timesheet three-element (salary/social/fund) cent rounding anchored to the rounded total | Calculation | P0 | `TimesheetElementRounding.java:24-53` | High |
| RULE-076 | 项目成本与核算 | Labour reclass – U8C credit vs legacy-system landed cost, by department, with tolerance | Calculation | P0 | `LaborReclassDiffCalculator.java:49-278` | High |
| RULE-077 | 项目成本与核算 | Labour reclass voucher – reverse U8C credit in full, debit legacy-system buckets, absorb small differences | Calculation | P0 | `LaborReclassVoucherAssembler.java:27-131` | High |
| RULE-078 | 项目成本与核算 | Vouchers are aggregated per company × voucher word × department | Calculation | P0 | `PostingRuleEngine.java:84-181` | High |
| RULE-079 | 项目成本与核算 | Posting rule matching and voucher-line rendering | Calculation | P0 | `PostingRuleEngine.java:185-286` | High |
| RULE-080 | 项目成本与核算 | Revenue-return red reversal – reverse the whole original cost, re-render the voucher and negate it | Calculation | P0 | `PostingVoucherDrafterImpl.java:125-209` | Medium |
| RULE-081 | 项目成本与核算 | Write-off voucher amounts and credit account derivation | Calculation | P0 | `PostingVoucherDrafterImpl.java:507-568` | High |
| RULE-082 | 项目成本与核算 | WIP period balance and report increase/decrease use the absolute movement amount | Calculation | P0 | `WipPeriodSnapshotMapper.java:53-113` | Medium |
| RULE-083 | 项目成本与核算 | WIP aging impairment flag after 720 days | Calculation | P1 | `WipImpairmentSweepService.java:69-175` | High |
| RULE-084 | 项目成本与核算 | Splitting opportunity cost across terms – drawer and export use different formulas | Calculation | P1 | `QueryProjectProfitUseCase.java:151-295` | Medium |
| RULE-085 | 项目成本与核算 | Contract asset and receivable composition; zeroed for closed or terminated projects | Calculation | P1 | `QueryProjectProfitUseCase.java:661-721` | High |
| RULE-086 | 项目成本与核算 | OA→U8C credit matching for labour reclass | Calculation | P1 | `OaCreditResolver.java:42-166` | High |
| RULE-087 | 项目成本与核算 | Person-month rounding true-up to the payroll accrual | Calculation | P1 | `PersonMonthRoundingCalculator.java:16-74` | High |
| RULE-088 | 项目成本与核算 | Zero-value cost never enters WIP; negative cost does | Calculation | P1 | `CarryCandidateReaderImpl.java:42-58` | Medium |
| RULE-089 | 项目成本与核算 | Legacy carry voucher – debit = Σ credits per company × U8C dept × subject | Calculation | P1 | `CarryForwardServiceImpl.java:134-197` | High |
| RULE-090 | 项目成本与核算 | Legacy WIP/cost carry-forward – labour = person-days × daily wage; project bonus 5% | Calculation | P1 | `WipServiceImpl.java:735-766` | Medium |
| RULE-091 | 项目成本与核算 | Period open/close preconditions (sequential opening, month-end done, reconciled) | Validation | P0 | `AccountingPeriodManagementService.java:98-150` | High |
| RULE-092 | 项目成本与核算 | Full OH allocation: one effective full run per period, never concurrent | Validation | P0 | `ExecuteFullAllocationUseCase.java:70-100` | High |
| RULE-093 | 项目成本与核算 | Carry draft gate order, and the red-reversal exception | Validation | P0 | `CarryBatchService.java:102-122` | High |
| RULE-094 | 项目成本与核算 | Carry confirm re-checks the gates and dry-runs the vouchers | Validation | P0 | `CarryBatchService.java:196-235` | High |
| RULE-095 | 项目成本与核算 | Red reversal confirm requires a valid reason and a successful red-voucher preview | Validation | P0 | `RevenueRedReversalCommandUseCase.java:42-55` | Medium |
| RULE-096 | 项目成本与核算 | Collection start preconditions and order of side effects | Validation | P0 | `ExecuteCollectionUseCase.java:82-128` | Medium |
| RULE-097 | 项目成本与核算 | Exempting a collection error needs a reason and only works on OPEN errors | Validation | P0 | `ExemptEntryErrorUseCase.java:28-40` | Medium |
| RULE-098 | 项目成本与核算 | Labor reclass confirm and deliver must be fresh and follow period order | Validation | P0 | `LaborReclassRunUseCase.java:154-202` | High |
| RULE-099 | 项目成本与核算 | Unreconciled expense lines block carry and period close | Validation | P0 | `ReconciledClosingGate.java:20-32` | High |
| RULE-100 | 项目成本与核算 | Landing conservation must balance exactly (zero tolerance) | Validation | P0 | `CostCollectionBatch.java:304-310` | High |
| RULE-101 | 项目成本与核算 | Collection confirmation gate | Validation | P0 | `CostCollectionBatch.java:367-388` | High |
| RULE-102 | 项目成本与核算 | Ingest pre-routing guards: duplicates, exempt flag, department bonus | Validation | P0 | `CostRoutingService.java:101-125` | High |
| RULE-103 | 项目成本与核算 | Project fees without a contract term never go to the OH pool | Validation | P0 | `CostRoutingService.java:178-189` | High |
| RULE-104 | 项目成本与核算 | Mandatory dimensions depend on where the cost will land | Validation | P0 | `CostValidationService.java:76-146` | High |
| RULE-105 | 项目成本与核算 | Labor reclass differences over ¥1.00 block the voucher | Validation | P0 | `LaborReclassDiffCalculator.java:180-209` | High |
| RULE-106 | 项目成本与核算 | Re-running collection reverses a prior confirmation; it is blocked once allocation is confirmed | Validation | P0 | `PeriodProgress.java:71-86` | High |
| RULE-107 | 项目成本与核算 | Collection cannot start until payroll is complete for everyone who filed timesheets | Validation | P0 | `SalaryCalculationReadinessAdapter.java:38-68` | High |
| RULE-108 | 项目成本与核算 | Month-end cost processing only runs while the period is Pre-closed or Reopened | Validation | P0 | `AccountingPeriodServiceImpl.java:43-48` | High |
| RULE-109 | 项目成本与核算 | Trial allocation needs a completed collection batch, but not a confirmed collection | Validation | P1 | `ExecuteTrialAllocationUseCase.java:74-94` | High |
| RULE-110 | 项目成本与核算 | Row-level recon disposition: which actions are allowed and when | Validation | P1 | `ReconDispositionUseCase.java:29-67` | High |
| RULE-111 | 项目成本与核算 | Expense amounts can be negative or zero; timesheet element amounts cannot be negative | Validation | P1 | `CostValidationService.java:154-191` | High |
| RULE-112 | 项目成本与核算 | Posting pre-check: pool-bound expenses need an allocation mapping effective on the business date | Validation | P1 | `ExpensePostingCheckServiceImpl.java:321-364` | High |
| RULE-113 | 项目成本与核算 | Posting pre-check: an expense's links must match its fee type | Validation | P1 | `ExpensePostingCheckServiceImpl.java:385-400` | High |
| RULE-114 | 项目成本与核算 | A project fee must be borne by a delivery department, unless no delivery departments are flagged yet | Validation | P1 | `ExpensePostingCheckServiceImpl.java:534-546` | High |
| RULE-115 | 项目成本与核算 | Legacy WIP second carry checks the wrong prerequisite | Validation | P1 | `WipServiceImpl.java:442-447` | High |
| RULE-116 | 项目成本与核算 | Full allocation takes effect atomically with pool write-back and step advance | Lifecycle | P0 | `CostAllocationBatchOrchestratorImpl.java:152-208` | High |
| RULE-117 | 项目成本与核算 | A batch becomes POSTED only when U8C has confirmed every voucher; then the step advances | Lifecycle | P0 | `CarryBatchFinalizer.java:75-135` | High |
| RULE-118 | 项目成本与核算 | Creating a carry draft (asynchronous) | Lifecycle | P0 | `CarryBatchService.java:102-122` | High |
| RULE-119 | 项目成本与核算 | Posting phases and the completion condition | Lifecycle | P0 | `CarryConfirmOrchestratorImpl.java:36-78` | High |
| RULE-120 | 项目成本与核算 | Allocation batch lifecycle | Lifecycle | P0 | `CostAllocationBatch.java:69-134` | High |
| RULE-121 | 项目成本与核算 | Collection batch status and phase lifecycle | Lifecycle | P0 | `CostCollectionBatch.java:122-191` | High |
| RULE-122 | 项目成本与核算 | Carry batch status and phase state machine | Lifecycle | P0 | `CarryBatch.java:98-283` | High |
| RULE-123 | 项目成本与核算 | WIP stage assignment and advancement | Lifecycle | P0 | `CarrySelectorImpl.java:54-124` | High |
| RULE-124 | 项目成本与核算 | Period month-end step gate: collection lock, confirm, and re-collection rollback | Lifecycle | P0 | `PeriodProgress.java:71-117` | High |
| RULE-125 | 项目成本与核算 | Carry steps are strictly serial: occupy on draft, advance on POSTED, release on cancel | Lifecycle | P0 | `PeriodProgress.java:126-171` | High |
| RULE-126 | 项目成本与核算 | WIP write-off flips the entry from in-WIP to relieved and books a P&L line | Lifecycle | P0 | `WipLedgerPosterImpl.java:272-304` | High |
| RULE-127 | 项目成本与核算 | Red reversal returns a relieved WIP entry to in-WIP and books one negative P&L line per positive line | Lifecycle | P0 | `WipLedgerPosterImpl.java:435-502` | High |
| RULE-128 | 项目成本与核算 | Legacy carry-forward lifecycle (待结转 → 结转中 → 已结转), still exposed by controllers | Lifecycle | P0 | `CostCarryForwardServiceImpl.java:109-157` | Medium |
| RULE-129 | 项目成本与核算 | Accounting period status transition table | Lifecycle | P1 | `AccountingPeriodStatusEnum.java:22-72` | Medium |
| RULE-130 | 项目成本与核算 | Cancelling a carry draft | Lifecycle | P1 | `CarryBatchService.java:266-301` | High |
| RULE-131 | 项目成本与核算 | Re-running collection resets the batch in place; overwrite mode purges first | Lifecycle | P1 | `CostCollectionBatch.java:211-264` | High |
| RULE-132 | 项目成本与核算 | U8C voucher delivery and the opening-year local bypass | Policy | P0 | `U8cVoucherPostHandler.java:88-140` | High |
| RULE-133 | 项目成本与核算 | Opening-period collection confirm can bulk-exempt all open errors | Policy | P0 | `OpeningMonthExecuteUseCase.java:115-129` | Medium |
| RULE-134 | 项目成本与核算 | A cost dated in a closed period lands in the earliest later open period | Policy | P0 | `AccountingPeriodPortAdapter.java:75-99` | High |
| RULE-135 | 项目成本与核算 | Month-end cost processing only runs while the period is pre-closed or reopened | Policy | P0 | `AccountingPeriodServiceImpl.java:43-56` | High |
| RULE-136 | 项目成本与核算 | Aged in-WIP cost is frozen into impairment (stage 4) after write-off posting | Policy | P1 | `WipImpairmentSweepService.java:69-175` | Medium |
| RULE-137 | 项目成本与核算 | Pre-posting "BLOCK" checks are advisory; only the salary check is enforced | Policy | P1 | `PrePostingCheckUseCase.java:26-35` | High |
| RULE-138 | 项目成本与核算 | Voucher delivery retry, suspension and manual re-delivery | Policy | P1 | `VoucherDeliveryManageService.java:38-82` | High |
| RULE-139 | 售前与报价 | Server rates always overwrite client rates; at submission every work package must have a fully priced role | Calculation | P0 | `QuotationCostRateResolver.java:38-103` | High |
| RULE-140 | 售前与报价 | At submit, cost and margin are recalculated from the current rates before approval starts | Calculation | P0 | `SubmitQuotationUseCase.java:51-81` | High |
| RULE-141 | 售前与报价 | Cost-tree work package value = hours × billing rate; labour cost = hours × cost rate | Calculation | P0 | `CostEstimateNode.java:74-102` | Medium |
| RULE-142 | 售前与报价 | Final quote equals the sum of line amounts plus the tail adjustment | Calculation | P0 | `Quotation.java:237-246` | High |
| RULE-143 | 售前与报价 | BU estimated cost = labour cost (whole tree) + the 8 fixed other-cost categories | Calculation | P0 | `Quotation.java:247-251` | High |
| RULE-144 | 售前与报价 | Gross margin is calculated on the ex-tax final quote using the header tax rate, to 4 decimals | Calculation | P0 | `Quotation.java:252-259` | High |
| RULE-145 | 售前与报价 | Approval-time edits recalculate the final quote from the stored line total plus the new tail adjustment | Calculation | P0 | `Quotation.java:367-410` | High |
| RULE-146 | 售前与报价 | Line amount: a hand-entered discounted total overrides quantity × unit price | Calculation | P0 | `QuotationLine.java:86-93` | High |
| RULE-147 | 售前与报价 | Role rates come from the master-data schemes in effect today, by role and city, newest scheme first | Calculation | P0 | `PositionRateQueryImpl.java:50-92` | High |
| RULE-148 | 售前与报价 | Header tax rate defaults to 6% and must be between 0 (inclusive) and 1 (exclusive) | Calculation | P1 | `Quotation.java:232-240` | High |
| RULE-149 | 售前与报价 | Line tax split, ex-tax unit price and implied discount are derived on the server | Calculation | P1 | `QuotationLine.java:107-126` | High |
| RULE-150 | 售前与报价 | A clause percentage is a UI convenience; only the amount is stored, rounded to cents | Calculation | P1 | `CostEstimateTree.vue:486-538` | Medium |
| RULE-151 | 售前与报价 | Cost template summary shows clause, deliverable and work-package counts and total hours; templates store no rates | Calculation | P2 | `CostTemplate.java:106-126` | High |
| RULE-152 | 售前与报价 | Opportunity stats card: total, won, lost, sum of amounts, and number of quoted opportunities | Calculation | P2 | `OpportunityQueryImpl.java:116-140` | High |
| RULE-153 | 售前与报价 | Quotation list "total hours" = sum of work-package hours | Calculation | P2 | `QuotationQueryImpl.java:94-118` | High |
| RULE-154 | 售前与报价 | Quotation stats card: counts by status and approved amount, limited to what the viewer can see | Calculation | P2 | `QuotationQueryImpl.java:130-144` | High |
| RULE-155 | 售前与报价 | During approval clauses are locked, so the tail adjustment is effectively frozen, and BU cost must be > 0 when required | Validation | P0 | `ApplyQuotationApprovalUseCase.java:65-101` | High |
| RULE-156 | 售前与报价 | Contract clauses are locked during approval | Validation | P0 | `ApplyQuotationApprovalUseCase.java:116-142` | High |
| RULE-157 | 售前与报价 | Server master data is the only source of position rates; strict check at submit | Validation | P0 | `QuotationCostRateResolver.java:38-68` | High |
| RULE-158 | 售前与报价 | Conditions for submitting a quotation for approval | Validation | P0 | `Quotation.java:265-279` | High |
| RULE-159 | 售前与报价 | Clause amounts must each be greater than 0 and add up exactly to the final quote | Validation | P0 | `Quotation.java:334-356` | High |
| RULE-160 | 售前与报价 | Approval-time adoption recalculates final amount and margin; quotation must stay PENDING | Validation | P0 | `Quotation.java:367-410` | High |
| RULE-161 | 售前与报价 | Server-side checks on monetary inputs | Validation | P0 | `Quotation.java:470-524` | High |
| RULE-162 | 售前与报价 | Starting a quotation from an opportunity | Validation | P1 | `CreateQuotationDraftUseCase.java:36-63` | Medium |
| RULE-163 | 售前与报价 | A margin below 50% requires a written low-margin reason (BU submitter or cost-adopting approver only) | Validation | P1 | `Quotation.java:281-298` | High |
| RULE-164 | 售前与报价 | Seal registration must be complete when "需要用印" is ticked | Validation | P1 | `Quotation.java:305-319` | High |
| RULE-165 | 售前与报价 | Cost template name is required; templates are numbered and active by default | Validation | P2 | `CostTemplate.java:57-62` | High |
| RULE-166 | 售前与报价 | Approve/reject decisions are idempotent and ignore stale messages | Lifecycle | P0 | `DecideQuotationUseCase.java:32-75` | High |
| RULE-167 | 售前与报价 | Submission order: refresh costs, check all gates, compare-and-set, then start OPP_QUOTATION as one transaction | Lifecycle | P0 | `SubmitQuotationUseCase.java:36-89` | High |
| RULE-168 | 售前与报价 | Only DRAFT or REJECTED quotations can be edited, submitted or deleted | Lifecycle | P0 | `Quotation.java:532-540` | High |
| RULE-169 | 售前与报价 | How BPM final results map to quotation outcomes | Lifecycle | P0 | `QuotationProcessResultHandler.java:34-52` | Medium |
| RULE-170 | 售前与报价 | A quotation returned to the initiator stays PENDING but can be edited and resubmitted | Lifecycle | P1 | `QuotationApprovalStarterImpl.java:93-108` | High |
| RULE-171 | 售前与报价 | Deletion is a guarded soft delete that cascades to child tables | Lifecycle | P1 | `QuotationRepositoryImpl.java:285-303` | High |
| RULE-172 | 售前与报价 | Only the applicant can save, submit or delete a quotation | Policy | P0 | `QuotationAccessGuard.java:66-71` | High |
| RULE-173 | 售前与报价 | Users without cost-edit permission cannot change cost inputs; work-package cost fields are kept from the database | Policy | P0 | `SaveQuotationUseCase.java:51-164` | Medium |
| RULE-174 | 售前与报价 | Values copied onto the draft at creation and frozen | Policy | P1 | `Quotation.java:138-148` | High |
| RULE-175 | 售前与报价 | A low-margin reason is required when estimated gross margin is below 50% | Policy | P1 | `Quotation.java:282-298` | High |
| RULE-176 | 售前与报价 | Who sees which quotations in lists and stats | Policy | P1 | `QuotationQueryImpl.java:285-291` | High |
| RULE-177 | 售前与报价 | Clause codes are permanent and never reused | Policy | P1 | `QuotationRepositoryImpl.java:374-416` | High |
| RULE-178 | 售前与报价 | Quotation numbering | Policy | P2 | `QuotationCodeGeneratorImpl.java:20-33` | High |
| RULE-179 | 售前与报价 | Approved quotations count toward the "approved amount" stat | Policy | P2 | `QuotationQueryImpl.java:130-143` | High |
| RULE-180 | 售前与报价 | The opportunity-expense CRM status sync needs explicit period and company targets | Policy | P2 | `OpportunityExpenseCrmStatusSyncJob.java:32-65` | Medium |
| RULE-181 | 交付、预算与收入 | WBS budget cost is task hours × role cost rate, summed up the tree, plus non-labour cost categories | Calculation | P0 | `ProjectBudget.java:733-767` | High |
| RULE-182 | 交付、预算与收入 | Budget gross margin uses ex-tax contract amount at the source quotation's tax rate (default 6%) | Calculation | P0 | `ProjectBudget.java:769-792` | High |
| RULE-183 | 交付、预算与收入 | Task ETC, EAC and POC | Calculation | P0 | `TaskPackage.java:264-311` | High |
| RULE-184 | 交付、预算与收入 | A clause's revenue is capped at the clause amount; pending requests reserve quota | Calculation | P0 | `ClauseRevenueQuota.java:32-49` | High |
| RULE-185 | 交付、预算与收入 | Revenue confirmation needs a positive amount and complete evidence; the period is the sign-date month | Calculation | P0 | `RevenueConfirmation.java:64-125` | High |
| RULE-186 | 交付、预算与收入 | Cost view: forecast cost, savings, cost POC and budget POC | Calculation | P1 | `ExecutionCostBook.java:77-136` | High |
| RULE-187 | 交付、预算与收入 | Execution cost uses the assignee's personal rate; budget cost uses the role rate (frozen first) | Calculation | P1 | `ExecutionCostBook.java:180-194` | Medium |
| RULE-188 | 交付、预算与收入 | The progress-page header's "budget cost" is the approved baseline labour cost, not the execution sum | Calculation | P1 | `ProgressTreeQueryImpl.java:164-180` | High |
| RULE-189 | 交付、预算与收入 | Hours roll-up and POC per deliverable, clause and project; overrun flag | Calculation | P1 | `ProgressTreeQueryImpl.java:586-714` | High |
| RULE-190 | 交付、预算与收入 | Project "confirmed revenue" counts only output rows that match one of the project's clauses | Calculation | P1 | `ProjectQueryImpl.java:272-300` | High |
| RULE-191 | 交付、预算与收入 | Project list labour savings % and overrun flag | Calculation | P1 | `ProjectQueryImpl.java:401-421` | High |
| RULE-192 | 交付、预算与收入 | Legacy term labour EAC = incurred at forecast + remaining forecast days × budget daily wage, floored at actual | Calculation | P1 | `InvestForecastServiceImpl.java:612-680` | Medium |
| RULE-193 | 交付、预算与收入 | Legacy per-term earned value: progress, accrued revenue, EV, CV and VAC | Calculation | P1 | `ProjectServiceImpl.java:1258-1306` | High |
| RULE-194 | 交付、预算与收入 | Legacy project progress, cost waste and cost performance index | Calculation | P1 | `ProjectServiceImpl.java:1386-1400` | Medium |
| RULE-195 | 交付、预算与收入 | Legacy margins divide by a fixed 1.06 for tax | Calculation | P1 | `ProjectServiceImpl.java:1410-1425` | High |
| RULE-196 | 交付、预算与收入 | Budget-risk traffic light from the cost-waste rate | Calculation | P1 | `ProjectServiceImpl.java:1665-1705` | Medium |
| RULE-197 | 交付、预算与收入 | Project WIP = sum of carried-forward rows posted to cost but not yet written off | Calculation | P1 | `SyncServiceImpl.java:741-750` | High |
| RULE-198 | 交付、预算与收入 | A daily job writes the progress-tree POC into project progress for locked projects | Calculation | P1 | `ProjectPocRecalcJob.java:35-64` | High |
| RULE-199 | 交付、预算与收入 | Invoiceable amount per term = max(0, confirmed output − invoiced) | Calculation | P2 | `ProjectBillingView.java:93-98` | High |
| RULE-200 | 交付、预算与收入 | A revision cannot be submitted if the archive clauses changed after the revision started | Validation | P0 | `LockProjectBudgetUseCase.java:120-147` | High |
| RULE-201 | 交付、预算与收入 | Execution-side structure is frozen during budget approval, and planned hours are frozen after lock | Validation | P0 | `OperateProgressTaskUseCase.java:244-295` | High |
| RULE-202 | 交付、预算与收入 | Only the project's PM can change execution-side structure | Validation | P0 | `OperateProgressTaskUseCase.java:504-510` | High |
| RULE-203 | 交付、预算与收入 | Only one active submission per deliverable | Validation | P0 | `SubmitDeliverableUseCase.java:54-84` | High |
| RULE-204 | 交付、预算与收入 | At final approval the quota is re-checked; an over-cap request is auto-rejected | Validation | P0 | `DecideRevenueConfirmationUseCase.java:44-62` | High |
| RULE-205 | 交付、预算与收入 | Revenue confirmation submit gates — clause exists, clause not obsolete, contract signed back | Validation | P0 | `SubmitRevenueConfirmationUseCase.java:44-90` | High |
| RULE-206 | 交付、预算与收入 | Once hours are logged, the assignee is fixed and the task cannot be deleted | Validation | P0 | `DeliverableProgress.java:292-315` | High |
| RULE-207 | 交付、预算与收入 | Submitting a deliverable requires all its tasks to be finished, then locks them | Validation | P0 | `DeliverableProgress.java:403-423` | High |
| RULE-208 | 交付、预算与收入 | Rules for registering customer acceptance | Validation | P0 | `DeliverableSubmission.java:619-655` | High |
| RULE-209 | 交付、预算与收入 | Every task package must be complete before a budget can be submitted | Validation | P0 | `ProjectBudget.java:326-377` | High |
| RULE-210 | 交付、预算与收入 | Budget can only be edited while it is unlocked and not awaiting approval | Validation | P0 | `ProjectBudget.java:438-446` | High |
| RULE-211 | 交付、预算与收入 | Normal cost items are edited during budgeting; sensitive items need a permission | Validation | P0 | `ProjectBudget.java:579-626` | High |
| RULE-212 | 交付、预算与收入 | Rules for an approver changing the budget tree during approval | Validation | P0 | `ProjectBudget.java:639-723` | High |
| RULE-213 | 交付、预算与收入 | Total confirmed revenue cannot exceed the clause amount | Validation | P0 | `RevenueConfirmation.java:64-87` | High |
| RULE-214 | 交付、预算与收入 | Team members can mark only their own task packages done | Validation | P1 | `OperateProgressTaskUseCase.java:63-77` | High |
| RULE-215 | 交付、预算与收入 | Rework path depends on the deliverable's submission status | Validation | P1 | `OperateProgressTaskUseCase.java:85-109` | High |
| RULE-216 | 交付、预算与收入 | Obsolete nodes must be acknowledged before a revision can be submitted | Validation | P1 | `BudgetRevision.java:96-117` | High |
| RULE-217 | 交付、预算与收入 | Required submission inputs depend on the phase | Validation | P1 | `DeliverableSubmission.java:220-253` | High |
| RULE-218 | 交付、预算与收入 | A budget change can only start from a locked budget | Validation | P1 | `ProjectBudget.java:454-464` | High |
| RULE-219 | 交付、预算与收入 | On budget approval, role rates are frozen and cost snapshots are written | Lifecycle | P0 | `DecideBudgetApprovalUseCase.java:73-80` | High |
| RULE-220 | 交付、预算与收入 | ETC approval outcome — approved promotes the pending value; rejected or cancelled unfreezes and keeps the old value | Lifecycle | P0 | `DecideEtcApprovalUseCase.java:46-96` | High |
| RULE-221 | 交付、预算与收入 | A budget revision can only start from a locked budget; it snapshots, unlocks and re-baselines the clause layer | Lifecycle | P0 | `StartBudgetRevisionUseCase.java:55-110` | High |
| RULE-222 | 交付、预算与收入 | Approved revenue writes an append-only revenue fact, after a second quota check | Lifecycle | P0 | `DecideRevenueConfirmationUseCase.java:36-68` | High |
| RULE-223 | 交付、预算与收入 | Task package execution states — DOING/REWORK → DONE, DONE → DOING (reopen), LOCKED → REWORK | Lifecycle | P0 | `DeliverableProgress.java:47-84` | High |
| RULE-224 | 交付、预算与收入 | Submitting a deliverable requires all its task packages to be done, and locks them | Lifecycle | P0 | `DeliverableProgress.java:403-423` | High |
| RULE-225 | 交付、预算与收入 | Deliverable approval outcome — approved → awaiting customer acceptance; rejected or cancelled → DRAFT | Lifecycle | P0 | `DeliverableSubmission.java:588-613` | Medium |
| RULE-226 | 交付、预算与收入 | Deliverable submission states, and which states hold the deliverable's single "submission slot" | Lifecycle | P0 | `DeliverableSubmissionStatus.java:43-64` | High |
| RULE-227 | 交付、预算与收入 | A budget can only be locked through an approved PM_BUDGET approval, and only when it is complete | Lifecycle | P0 | `ProjectBudget.java:326-377` | High |
| RULE-228 | 交付、预算与收入 | Budget editing is blocked while the budget is locked or awaiting approval | Lifecycle | P0 | `ProjectBudget.java:438-446` | High |
| RULE-229 | 交付、预算与收入 | On revision, clauses are re-matched to the archive; vanished clauses with history become obsolete, others are removed | Lifecycle | P0 | `ProjectBudget.java:477-538` | High |
| RULE-230 | 交付、预算与收入 | How rework is handled depends on the submission state | Lifecycle | P1 | `OperateProgressTaskUseCase.java:84-242` | High |
| RULE-231 | 交付、预算与收入 | Execution-phase structure changes freeze during budget approval; planned hours freeze after lock; a submission locks the package set | Lifecycle | P1 | `OperateProgressTaskUseCase.java:244-295` | High |
| RULE-232 | 交付、预算与收入 | Revision submit requires the obsolete list to be acknowledged; the revision status follows the budget outcome | Lifecycle | P1 | `BudgetRevision.java:96-123` | High |
| RULE-233 | 交付、预算与收入 | Resubmitting after a return to the initiator resets the review and wipes earlier conclusions | Lifecycle | P1 | `DeliverableSubmission.java:168-209` | High |
| RULE-234 | 交付、预算与收入 | First-level review pass moves deepen-phase submissions to PMO review; initial-phase submissions only record the trace | Lifecycle | P1 | `DeliverableSubmission.java:357-368` | High |
| RULE-235 | 交付、预算与收入 | Deepen-phase PMO review — a one-time pass ends the review; otherwise a director revision and second PMO round follow | Lifecycle | P1 | `DeliverableSubmission.java:465-540` | High |
| RULE-236 | 交付、预算与收入 | Customer acceptance can be registered once, only from ACCEPTING, with a sign date and two kinds of proof | Lifecycle | P1 | `DeliverableSubmission.java:619-655` | High |
| RULE-237 | 交付、预算与收入 | Approvers may change the cost tree only while the budget is PENDING; the adopted list is the full set | Lifecycle | P1 | `ProjectBudget.java:639-723` | Medium |
| RULE-238 | 交付、预算与收入 | Works stage: Design until the 深化 (deepening) stage output is fully confirmed, then Closing (one-way) | Lifecycle | P2 | `ProjectServiceImpl.java:1707-1750` | High |
| RULE-239 | 交付、预算与收入 | Budget margin vs quotation margin decides whether CEO approval is needed | Policy | P0 | `ApplyBudgetApprovalUseCase.java:100-113` | High |
| RULE-240 | 交付、预算与收入 | The quota is checked again at final approval, and an over-quota approval is turned into a rejection | Policy | P0 | `DecideRevenueConfirmationUseCase.java:44-61` | High |
| RULE-241 | 交付、预算与收入 | An ETC increase needs PM_ETC approval only if it pushes forecast labour cost over budget labour cost | Policy | P0 | `DeliverableProgress.java:110-136` | High |
| RULE-242 | 交付、预算与收入 | Raising ETC needs PM_ETC approval only if it pushes project forecast labour cost above budget labour cost | Policy | P0 | `LaborCostHeadroom.java:59-75` | High |
| RULE-243 | 交付、预算与收入 | Archive reconciliation keeps clauses that have data and removes those that do not | Policy | P0 | `ProjectBudget.java:477-538` | High |
| RULE-244 | 交付、预算与收入 | Budget approval routing — the CEO step is skipped only if budget gross margin ≥ quote gross margin | Policy | P0 | `BudgetApprovalStarterImpl.java:58-75` | High |
| RULE-245 | 交付、预算与收入 | Which role cost rate applies: project city, then quotation city, then unfiltered; latest active scheme wins | Policy | P0 | `ProjectBudgetRepositoryImpl.java:150-222` | Medium |
| RULE-246 | 交付、预算与收入 | Which quotation counts as the budget baseline | Policy | P1 | `BudgetSourceQuotationFinder.java:26-44` | High |
| RULE-247 | 交付、预算与收入 | Reconciliation job replays lost ETC outcomes and releases orphaned freezes | Policy | P1 | `EtcApprovalReconJob.java:61-155` | High |
| RULE-248 | 审批（platform.approval） | Countersign/or-sign nodes create one task per distinct approver | Calculation | P0 | `BpmParallelMultiInstanceBehavior.java:45-93` | Medium |
| RULE-249 | 审批（platform.approval） | A single-approver node picks one candidate at random | Calculation | P0 | `BpmUserTaskActivityBehavior.java:73-100` | High |
| RULE-250 | 审批（platform.approval） | Initiator-leader scripts pick the 1st or 2nd reporting-line leader | Calculation | P0 | `BpmTaskAssignLeaderAbstractScript.java:39-73` | High |
| RULE-251 | 审批（platform.approval） | Row reachability: a row reaches a node if its routing value activates a path to it | Calculation | P0 | `BpmProcessGraphUtils.java:118-153` | High |
| RULE-252 | 审批（platform.approval） | Per-row approver resolution | Calculation | P0 | `BpmTaskAssignRuleServiceImpl.java:343-426` | High |
| RULE-253 | 审批（platform.approval） | Approvers are resolved from the node's assignment-rule type | Calculation | P0 | `BpmTaskAssignRuleServiceImpl.java:476-616` | High |
| RULE-254 | 审批（platform.approval） | "Custom data" approvers come from form fields, with a hard-coded fallback person | Calculation | P0 | `BpmTaskAssignRuleServiceImpl.java:665-689` | High |
| RULE-255 | 审批（platform.approval） | Regional AR script (30) resolves dept id to `dept.ar_user_id` | Calculation | P1 | `BpmTaskAssignDeliveryDeptArScript.java:43-72` | High |
| RULE-256 | 审批（platform.approval） | Department-director script (32) means holders of role BU-002 who sit directly in the department | Calculation | P1 | `BpmTaskAssignDeliveryDeptDirectorScript.java:74-96` | High |
| RULE-257 | 审批（platform.approval） | Department-leader script (31) resolves dept id to `dept.leader_user_id` | Calculation | P1 | `BpmTaskAssignDeliveryDeptLeaderScript.java:38-67` | High |
| RULE-258 | 审批（platform.approval） | Form-project-manager script (9): the field value is the approver | Calculation | P1 | `BpmTaskAssignFormProjectManagerScript.java:35-63` | High |
| RULE-259 | 审批（platform.approval） | Form-user-leader script (33): the field value's first-level leader is the approver | Calculation | P1 | `BpmTaskAssignFormUserLeaderX1Script.java:40-84` | High |
| RULE-260 | 审批（platform.approval） | Script approvers can be driven by a configurable field, resolved per value and unioned | Calculation | P1 | `BpmTaskAssignRuleServiceImpl.java:618-663` | High |
| RULE-261 | 审批（platform.approval） | The to-do list merges row tasks into one item per (instance, node) | Calculation | P1 | `BpmTaskServiceImpl.java:189-209` | High |
| RULE-262 | 审批（platform.approval） | Each node must have exactly one assignment rule | Validation | P0 | `BpmTaskAssignRuleServiceImpl.java:292-304` | High |
| RULE-263 | 审批（platform.approval） | A row-approval node requires row units, and the row set is narrowed by routing | Validation | P0 | `BpmTaskAssignRuleServiceImpl.java:319-340` | High |
| RULE-264 | 审批（platform.approval） | Idempotent process start by idempotency key | Validation | P0 | `BpmOAIdempotentStartService.java:25-53` | High |
| RULE-265 | 审批（platform.approval） | The initiator of a customized OA process is the caller-supplied `creator`, otherwise the logged-in user | Validation | P0 | `CustomizedBpmOAServiceImpl.java:105-110` | High |
| RULE-266 | 审批（platform.approval） | Duplicate-start guard: one live process per business document, reused on resubmission | Validation | P0 | `CustomizedBpmOAServiceImpl.java:135-198` | High |
| RULE-267 | 审批（platform.approval） | A resubmission after a return must belong to the current return round | Validation | P0 | `CustomizedBpmOAServiceImpl.java:229-283` | High |
| RULE-268 | 审批（platform.approval） | Only the initiator may cancel, before any cutoff node, with a reason | Validation | P0 | `BpmProcessInstanceServiceImpl.java:239-278` | High |
| RULE-269 | 审批（platform.approval） | Withdraw to initiator: initiator only, before cutoff, single-instance current node, not already at initiator | Validation | P0 | `BpmProcessInstanceServiceImpl.java:360-420` | High |
| RULE-270 | 审批（platform.approval） | Batch approve acts on every user's pending tasks in each assignee's name | Validation | P0 | `BpmTaskServiceImpl.java:430-466` | High |
| RULE-271 | 审批（platform.approval） | A reject inside a subprocess returns to the initiator; a top-level reject terminates the process | Validation | P0 | `BpmTaskServiceImpl.java:802-861` | High |
| RULE-272 | 审批（platform.approval） | Validation for the unified "decide" action | Validation | P0 | `BpmTaskServiceImpl.java:863-975` | High |
| RULE-273 | 审批（platform.approval） | A reject from an added countersigner is advisory only | Validation | P0 | `BpmTaskServiceImpl.java:982-1040` | High |
| RULE-274 | 审批（platform.approval） | Row-by-row approval must cover every pending row, and any rejected row rejects the node | Validation | P0 | `BpmTaskServiceImpl.java:1042-1098` | High |
| RULE-275 | 审批（platform.approval） | Return to initiator requires a reason and passes the node's BEFORE hook | Validation | P0 | `BpmTaskServiceImpl.java:1109-1152` | High |
| RULE-276 | 审批（platform.approval） | Only the assigned approver may act on a task | Validation | P0 | `BpmTaskServiceImpl.java:1303-1312` | High |
| RULE-277 | 审批（platform.approval） | BEFORE-hook commit gate | Validation | P0 | `WorkflowHookBeforeGate.java:16-38` | High |
| RULE-278 | 审批（platform.approval） | Approval start rounds: a monotonic round number per (processKey, businessId) defines the idempotency key | Validation | P0 | `WorkflowStartRoundService.java:20-55` | Medium |
| RULE-279 | 审批（platform.approval） | Process-result handling is idempotent per instance and status (reference: quotation) | Validation | P0 | `DecideQuotationUseCase.java:32-75` | Medium |
| RULE-280 | 审批（platform.approval） | Unified approve: ownership gate before any field write, fail-closed field permission, default reasons | Validation | P0 | `WorkflowApproveServiceImpl.java:52-174` | High |
| RULE-281 | 审批（platform.approval） | Node field permissions: unlisted fields are read-only; configuration is a full overwrite per process key | Validation | P1 | `BpmNodeFieldPermissionServiceImpl.java:51-95` | High |
| RULE-282 | 审批（platform.approval） | Batch submission returns the existing instance for any document already mapped | Validation | P1 | `CustomizedBpmOAServiceImpl.java:89-103` | Medium |
| RULE-283 | 审批（platform.approval） | Task BEFORE hooks require a bound business document and a registered hook key | Validation | P1 | `BpmApprovalTaskHookService.java:75-133` | High |
| RULE-284 | 审批（platform.approval） | Return to a specific earlier node: single-instance source and target only | Validation | P1 | `BpmTaskServiceImpl.java:1155-1225` | High |
| RULE-285 | 审批（platform.approval） | Transfer (change assignee) allowed only by the current assignee, to any user | Validation | P1 | `BpmTaskServiceImpl.java:1228-1238` | Medium |
| RULE-286 | 审批（platform.approval） | Countersign can only target a running parallel multi-instance node | Validation | P1 | `BpmTaskServiceImpl.java:1242-1295` | Medium |
| RULE-287 | 审批（platform.approval） | Resubmission chooses between continuing the same instance, blocking, and starting a new instance | Lifecycle | P0 | `CustomizedBpmOAServiceImpl.java:105-198` | High |
| RULE-288 | 审批（platform.approval） | Initiator cancels the whole process | Lifecycle | P0 | `BpmProcessInstanceServiceImpl.java:237-356` | High |
| RULE-289 | 审批（platform.approval） | Process-instance status and result lifecycle | Lifecycle | P0 | `BpmProcessInstanceServiceImpl.java:555-650` | High |
| RULE-290 | 审批（platform.approval） | A top-level rejection ends the whole process; a rejection inside a sub-process sends it back to the initiator | Lifecycle | P0 | `BpmTaskServiceImpl.java:800-844` | High |
| RULE-291 | 审批（platform.approval） | A rejection by an added co-signer is advisory only | Lifecycle | P0 | `BpmTaskServiceImpl.java:982-1006` | High |
| RULE-292 | 审批（platform.approval） | Row approval: any rejected row rejects the whole submission, but all rows still finish | Lifecycle | P0 | `BpmTaskServiceImpl.java:1068-1098` | Medium |
| RULE-293 | 审批（platform.approval） | Return to initiator using the `rejected` flag | Lifecycle | P0 | `BpmTaskServiceImpl.java:1107-1152` | Medium |
| RULE-294 | 审批（platform.approval） | Task result lifecycle (bpm_task_ext) | Lifecycle | P0 | `BpmTaskServiceImpl.java:1349-1424` | High |
| RULE-295 | 审批（platform.approval） | Reliable delivery task lifecycle (psa_reliable_delivery_task) | Lifecycle | P0 | `ReliableDeliveryDispatcher.java:127-251` | High |
| RULE-296 | 审批（platform.approval） | Batch submission returns the existing instance instead of creating one | Lifecycle | P1 | `CustomizedBpmOAServiceImpl.java:89-103` | Medium |
| RULE-297 | 审批（platform.approval） | Initiator "withdraw to edit" (process stays alive) | Lifecycle | P1 | `BpmProcessInstanceServiceImpl.java:358-420` | High |
| RULE-298 | 审批（platform.approval） | Return to an earlier node (single-instance only) | Lifecycle | P1 | `BpmTaskServiceImpl.java:1155-1225` | High |
| RULE-299 | 审批（platform.approval） | Notify tasks become unread to-do items until the detail page is opened | Lifecycle | P2 | `BpmTaskServiceImpl.java:1314-1347` | High |
| RULE-300 | 审批（platform.approval） | "Skip when empty" bypasses a node that has no approver | Policy | P0 | `BpmUserTaskActivityBehavior.java:40-60` | High |
| RULE-301 | 审批（platform.approval） | Auto-complete cascade order after each task creation | Policy | P0 | `BpmTaskEventListener.java:287-331` | High |
| RULE-302 | 审批（platform.approval） | Repeat approver on the same node is auto-approved, except on rework or "reapprove on return" | Policy | P0 | `BpmTaskEventListener.java:334-409` | High |
| RULE-303 | 审批（platform.approval） | Outbox redelivery, backoff and dead-letter | Policy | P0 | `BpmApprovalMqReliableSender.java:355-398` | High |
| RULE-304 | 审批（platform.approval） | Reconcile job re-sends missed results from the bpm records | Policy | P0 | `BpmApprovalMqReliableSender.java:402-485` | Medium |
| RULE-305 | 审批（platform.approval） | Task-result messages (topic-bpm-approval-task-result) | Policy | P0 | `BpmApprovalTaskResultProducer.java:71-108` | High |
| RULE-306 | 审批（platform.approval） | Which process endings reach business modules | Policy | P0 | `BpmProcessResultProducer.java:47-88` | High |
| RULE-307 | 审批（platform.approval） | Anti-self-approval escalates to the initiator's direct leader | Policy | P0 | `BpmTaskAssignRuleServiceImpl.java:504-550` | High |
| RULE-308 | 审批（platform.approval） | End-of-process write-back of approve_status to the business table | Policy | P0 | `CustomizedBpmOAServiceImpl.java:303-337` | Medium |
| RULE-309 | 审批（platform.approval） | Withdraw/cancel cutoff = any marked node with a completed task | Policy | P0 | `BpmProcessInstanceServiceImpl.java:454-477` | High |
| RULE-310 | 审批（platform.approval） | Document detail is visible to holders of the function permission or to process participants (fail-closed) | Policy | P0 | `ApprovalVisibilityGuard.java:51-77` | High |
| RULE-311 | 审批（platform.approval） | The task-result consumer routes by business key, and a hook replaces the handler | Policy | P0 | `ApprovalTaskResultConsumer.java:41-75` | Medium |
| RULE-312 | 审批（platform.approval） | The process-end consumer runs the hook and then the handler, and must be idempotent | Policy | P0 | `ProcessResultConsumer.java:43-75` | High |
| RULE-313 | 审批（platform.approval） | Added countersign participants only give advisory rejections | Policy | P1 | `BpmTaskServiceImpl.java:1242-1295` | Medium |
| RULE-314 | 审批（platform.approval） | Approval-start rounds and idempotency key | Policy | P1 | `WorkflowStartRoundService.java:20-55` | High |
| RULE-315 | 审批（platform.approval） | Asynchronous timesheet process creation (psa_process_to_be_sent) | Policy | P1 | `ProcessToBeSentServiceImpl.java:134-204` | High |

## 工时与资源

### RULE-001: Task package actual hours = total approved hours (recalculated in full)
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/api/project/task/ProjectTaskApiImpl.java:73-105`
**Plain English:** After a timesheet is approved, each project task package's actual hours are recalculated as the sum of all approved, non-deleted lines on it. Actual start and end dates become the earliest and latest work dates.
**Specification:**
  Given package P has approved, non-deleted lines of 4.0h (2026-03-02) and 3.5h (2026-03-05), plus a soft-deleted approved line of 2.0h
  When the timesheet approval result is processed
  Then actual_hours = 7.5, true_start_date = 2026-03-02, true_end_date = 2026-03-05
**Parameters:** line approve_status = 通过 (3); deleted = 0
**Edge cases handled:** lines with null task_id are matched by work_package_id; this is a full recalculation, not an increment, so it is safe to repeat; opportunity tasks follow the old progress path (`TaskServiceImpl.updateProgress`: progress 100 means completed, keeping the later end date)
**Confidence:** High

### RULE-002: Which daily wage is used (before 2020 vs from 2020 on)
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/budget/HourlyRateServiceImpl.java:329-348`
**Plain English:** For work dated before 2020-01-01, the daily wage comes from the yearly post × department rate card. Posts above level 4 cost 0. From 2020, it comes from the person's monthly daily-wage record.
**Specification:**
  Given a line dated 2019-06-10, post T3, dept D-17
  Then wage = hourly-rate table entry for key "2019-T3-D-17"
  Given a line dated 2019-06-10, post M5
  Then wage = 0 (numeric suffix 5 > 4)
  Given a line dated 2026-03-02 for person 1001
  Then wage = psa daily_wage record "2026-03-1001", rounded HALF_UP to 2 dp
**Parameters:** cutover date 2020-01-01; post level threshold > 4 → 0; wage scale 2 dp
**Edge cases handled:** rest day → wage 0 and no lookup (`ManHourEndServiceImpl.java:203-208`); a missing record gives wage 0 (`ManHourEndServiceImpl.java:206-207`); a missing calendar entry or post fails the whole calculation with a RuntimeException (`ManHourEndServiceImpl.java:191-202`)
**Suspected defect:** `Integer.parseInt(postCode.substring(1))` throws if the post code has a non-numeric suffix.
**Confidence:** Medium — SME: "Is the pre-2020 rule 'posts with level greater than 4 carry no cost' still valid, and should a missing post-2020 wage show as 0 instead of blocking?"

### RULE-003: Approval writes the cost and bill amounts (approved hours × locked rate)
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
**Also stated by:** timesheet-calculation — “Approved-hours cost and billing amounts (rate × approved hours)” (`psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourApproveStatusService.java:241-271`)

### RULE-004: Human-days split of a person's approved day across projects
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourEndServiceImpl.java:57-109`
**Plain English:** Each approved line becomes a fraction of a person-day: its hours divided by the person's total approved hours that day. The divisor is never less than 7.5, so a short day does not count as a full person-day.
**Specification:**
  Given employee 1001 on 2026-03-02 with approved hours Project A 6.0 and Project B 3.0 (total 9.0)
  When the `calculateHumanDaysJob` batch runs
  Then human_days = 6/9 = 0.6667 for A and 3/9 = 0.3333 for B
  And if the employee had only Project A 4.0 that day, human_days = 4/7.5 = 0.5333
**Parameters:** daily standard hours `CommonConstants.日小时` = "7.5"; scale 4, HALF_UP
**Edge cases handled:** only person-days with at least one line still missing human_days are processed, but then every line of that day is recalculated; a divide error gives 0 (BigDecimalUtil swallows it); rest-day hours are included (they become non-chargeable later, RULE-05)
**Suspected defect:** (1) If a null `approval_hours` row follows a non-null row on the same day, `hoursValue.add(null)` throws an NPE. That aborts the run for every remaining user, because the job catches the exception and only logs it. (2) The input table `psa_time_sheet_works_end` is filled once by `syncWorkHours` (approved, not deleted at sync time). Lines soft-deleted later stay in it, can inflate the daily divisor and are still costed by RULE-05.
**Confidence:** Medium — the formula is explicit. SME: "Is a day with more than 7.5 approved hours meant to stay at 1.0 person-day in total (divisor = actual hours) rather than going above 1.0?"

### RULE-005: Rates, company code and cost centre are fixed at submission
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourServiceImpl.java:1670-1696`
**Plain English:** When a timesheet is saved or submitted, each line records the cost and bill rates, the accounting company (from the HR transfer record) and the cost centre (the employee's department). Amounts are left for approval.
**Specification:**
  Given employee 1001 in dept D-17 whose transfer record on 2026-03-02 has company_code C01
  When the timesheet line for 2026-03-02 is saved
  Then the line stores company_code = C01, cost_center_id = D-17, plus the 9 rate fields (2 scheme IDs, 2 detail IDs, 2 rates, 2 units, billable flag); standard_cost_amount and bill_rate_amount stay empty until approval (RULE-01)
**Parameters:** hours passed to rate lookup = approvalHours if present, else declareHours, else 0 (only a placeholder)
**Edge cases handled:** lines with no works date are skipped; a missing company_code stops creation of the timesheet header (`insertManHour`, 1611-1616)
**Confidence:** High

### RULE-006: Project labour cost = daily wage × human days, excluding admin/support posts
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourServiceImpl.java:2090-2128`
**Plain English:** Direct labour cost adds up daily wage × human days over working-day lines. Rest-day human days are counted separately as non-chargeable and carry no cost. Lines whose post code contains "A" or "S" are left out.
**Specification:**
  Given 3 closed lines: (workday, post T3, wage 1,200.00, 0.6667 hd), (rest day, post T3, 0.5 hd), (workday, post A01, 1.0 hd)
  When project labour cost is computed
  Then amount = 800.0400, humanDays = 0.6667, nonChargeableHumanDays = 0.5, manHours = approved hours of line 1 only
**Parameters:** excluded post marker: post code contains "A" or "S"; rest-day flag `FSTConstant.WORKDAY.休息日`
**Edge cases handled:** amount is not rounded; the reported dailyWage is the first non-zero wage seen; a null wage or human_days throws a RuntimeException
**Suspected defect:** `contains("A")`/`contains("S")` matches the letter anywhere in the post code, not just as a prefix. Rest-day human days for A/S posts are still added to non-chargeable days, because the rest-day check runs first. RULE-24 (budget used cost) applies neither exclusion.
**Confidence:** Medium — SME: "Should the A/S exclusion test the first letter of the post code only (post channel), and should admin rest days count as non-chargeable?"

### RULE-007: Rate lookup by employee grade, salary band and city on the work date
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/TimeSheetRateServiceImpl.java:62-104`
**Plain English:** A person's cost rate and bill rate for a day come from their HR transfer record valid on that day: job level, salary grade and city category together pick exactly one standard-cost rate and one bill rate.
**Specification:**
  Given employee 1001 whose transfer record on 2026-03-02 has jobLevel T3, salaryGrade 05, cityCategory SH, and a standard-cost detail "T3|05|SH" effective 2026-01-01 to (open-ended)
  When rates are resolved for works date 2026-03-02
  Then that detail's rate, unit and scheme/detail IDs are returned, the bill rate (plus its billable flag) is resolved the same way, and each amount = rate × hours (HALF_UP, 3 dp)
**Parameters:** match key = `jobLevelCode|salaryGradeNo|cityCategoryCode`; effective window is inclusive at both ends and a null end means open-ended (`TimeSheetRateCacheService.java:375-380`); AMOUNT_SCALE = 3
**Edge cases handled:** submission is blocked (error 503) if the transfer record is missing or ambiguous, any of the 3 key fields is blank, a rate has no match, more than one detail matches, or no detail's window covers the date. Both the cost rate and the bill rate must match. On a miss the cache is force-reloaded once, throttled.
**Confidence:** High

### RULE-008: Mobile (H5) submission records approved hours = declared hours
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourServiceImpl.java:463-474`
**Plain English:** On the mobile submission path, the single hours value the employee enters is stored as both declared and approved hours. Approvers accept or reject lines but do not change hours.
**Specification:**
  Given an H5 submission with workPackage.approvalHours = 6.5
  When it is saved
  Then declare_hours = 6.5 and approval_hours = 6.5 on the line
**Parameters:** none
**Edge cases handled:** none
**Suspected defect:** The seed glossary distinguishes declared from approved hours, but no code in scope lets an approver change approved hours.
**Confidence:** Medium — SME: "Should approvers ever be able to reduce approved hours below declared hours? If so, where in the business process does that happen?"

### RULE-009: Remaining hours (ETC) of a project task package
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourServiceImpl.java:1258-1266`
**Plain English:** A task package's remaining hours are its explicit ETC hours if set; otherwise planned hours minus actual hours. The result is never negative.
**Specification:**
  Given etc_hours null, planned 40.0, actual 32.5
  Then remaining = 7.5
  Given etc_hours = −3
  Then remaining = 0
**Parameters:** floor 0; null planned/actual treated as 0
**Edge cases handled:** an explicit ETC overrides the planned-actual formula even when it is lower
**Confidence:** High

### RULE-010: Converting plan person-days to hours (and back)
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/resource/PlanWorksServiceImpl.java:488-517`
**Plain English:** When a plan line is created or updated, assigned hours = person-days × 7.5. When displayed, person-days = assigned hours ÷ 7.5, rounded to 2 dp.
**Specification:**
  Given a plan line of 3.5 person-days
  When it is saved
  Then assign_hours = 26.25
  And the plan page shows humanDays = 3.50 (26.25 ÷ 7.5, at line 319)
**Parameters:** `FSTConstant.HUMAN_DAYS` = "7.5" (a second constant alongside `CommonConstants.日小时` = "7.5"); display scale 2 HALF_UP
**Edge cases handled:** budget roll-up (line 400) converts budget person-days the same way
**Suspected defect:** The code's own TODO at line 399 asks whether different posts work the same hours per day. The same 7.5 constant is defined in two places.
**Confidence:** Medium — SME: "Is 7.5 hours per person-day uniform for every post and region?"

### RULE-011: Budget cost already used, by budget code
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/resource/TaskServiceImpl.java:1527-1535`
**Plain English:** A task's used cost is the sum of daily wage × human days of closed timesheet lines carrying the same budget code, counting working days only.
**Specification:**
  Given budget code B-01 with closed workday lines (wage 1,200.00 × 0.6667) and (wage 1,000.00 × 1.0)
  Then incurredCostsAmount = 1,800.04
**Parameters:** working days only; no rounding
**Edge cases handled:** rest days are skipped
**Suspected defect:** This is not the same as project labour cost (RULE-05): A/S posts are not excluded. It uses the resource calendar fallback (Mon-Fri) instead of the stored workday flag. A null daily wage throws an NPE.
**Confidence:** Medium — SME: "Should used budget cost exclude administrative/support posts the same way project labour cost does?"

### RULE-012: Nightly check of task-package actual hours
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/trigger/task/TaskActualHoursReconJob.java:46-82`
**Plain English:** A job re-adds approved hours per task package. Where the total differs from the stored actual hours, it re-runs the recalculation from RULE-13 in batches of 200.
**Specification:**
  Given package P stores actual_hours 5.0 but approved lines total 7.5
  When `taskActualHoursReconJob` runs
  Then P is flagged as drifted and recalculated to 7.5
**Parameters:** batch size 200; null actual treated as 0
**Edge cases handled:** lines with null task_id use work_package_id; lines with null approved hours are skipped
**Suspected defect:** Unlike RULE-13, this job does not filter `deleted = 0` (lines 53-56). Soft-deleted approved lines raise its expected total, so packages with superseded versions are flagged on every run. The recalculation then writes the lower, correct value, so the same packages are "repaired" every night and the fix count in the log is misleading.
**Confidence:** High

### RULE-013: Average daily hours of a plan line
**Category:** Calculation
**Priority:** P2
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/resource/PlanServiceImpl.java:63-87`
**Plain English:** A plan line's average daily load = assigned hours ÷ working days between planned start and end (both included), rounded to 2 dp. Team busyness charts add these up per day.
**Specification:**
  Given 45h planned from 2026-03-02 to 2026-03-10 (7 working days)
  Then avgWorkingHours = 6.43
**Parameters:** scale 2 HALF_UP
**Edge cases handled:** lines without start or end dates are skipped; 0 working days gives 0
**Confidence:** High

### RULE-014: Resource-list busyness = assigned task hours ÷ (working days × 7.5)
**Category:** Calculation
**Priority:** P2
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/resource/ResourceServiceImpl.java:134-204`
**Plain English:** On the resource list, a person's busyness is the full assigned hours of their matching tasks divided by expected hours (working days in the selected period × 7.5), rounded to 2 decimals.
**Specification:**
  Given 22 working days from 2026-03-01 to 2026-03-31 (expected 165h) and tasks totalling 180h
  Then busyness = 1.09
  And with filter "0.8,1.2" the person is included (0.8 ≤ 1.09 < 1.2)
**Parameters:** 7.5 h/day; ratio scale 2 HALF_UP; filter is lower bound inclusive, upper bound exclusive
**Edge cases handled:** begin date defaults to today; zero working days gives busyness 0
**Suspected defect:** Task hours are not prorated to the period here (RULE-19 prorates). The Gantt filter (`ganttChart` 509-516) includes the upper bound, this one excludes it.
**Confidence:** Medium — SME: "Should list busyness prorate long tasks to the selected window, as the Gantt does?"

### RULE-015: Gantt expected hours over the forecast window
**Category:** Calculation
**Priority:** P2
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/resource/ResourceServiceImpl.java:373-409`
**Plain English:** A person's expected (theoretical) hours in the Gantt = 7.5 × working days in the window. The window is the next 30 days, or from the 1st of next month through 6 or 12 months ahead.
**Specification:**
  Given window type "30天" and 21 working days in it
  Then expected hours = 157.5
  For a team (`ganttGroupChart` 597-613) with 5 members: 157.5 × 5 = 787.5
**Parameters:** 7.5 h/day; windows 30 days / 6 months / 12 months; explicit begin/end dates override
**Edge cases handled:** both window ends are counted
**Suspected defect:** "今天0点" (today 00:00) is built as `now().plusDays(1)`, i.e. tomorrow, so the window starts a day late (also at 912 and 1117). The "6个月" (6-month) branch uses `plusMonths(6)` but the comment says 5 months later.
**Confidence:** Medium — SME: "Should forecast windows start today or tomorrow?"

### RULE-016: Task hours spread evenly over working days and clipped to the window
**Category:** Calculation
**Priority:** P2
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/resource/ResourceServiceImpl.java:424-447`
**Plain English:** A task's assigned hours are spread evenly over its planned working days. Only the share that falls inside the reporting window counts toward a person's load.
**Specification:**
  Given a task with 60h planned 2026-03-02 to 2026-03-13 (10 working days), of which 4 working days fall in the window
  Then hours/day = 6.0000 and window load = 24.00
**Parameters:** per-day scale 4 HALF_UP; window total scale 2 HALF_UP
**Edge cases handled:** includes in-progress, overdue and (optionally) sent-but-not-accepted tasks; tasks starting after the window end are excluded
**Suspected defect:** A task with zero planned working days gives per-day 0 (division error swallowed), so its hours disappear from the load.
**Confidence:** High

### RULE-017: Remaining-hours estimate for overdue tasks
**Category:** Calculation
**Priority:** P2
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/resource/ResourceServiceImpl.java:456-488`
**Plain English:** For an overdue task, remaining hours are estimated from progress: approved ÷ (progress%) − approved. The Gantt bar is then pushed out by that many whole 7.5-hour working days from today.
**Specification:**
  Given approved 30h and progress 60%
  Then needed = 30 / 0.60 − 30 = 20.00h, neededDays = floor(20 / 7.5) = 2, and the bar ends 2 working days after today
**Parameters:** progress ÷ 100 at 2 dp HALF_UP; needed hours 2 dp HALF_UP; days rounded down by 7.5; safety loop limit 1,000
**Edge cases handled:** progress 0 or null makes the formula throw, so the full assigned hours are used instead
**Suspected defect:** Rounding days down makes any remaining work under 7.5h end today (0 days).
**Confidence:** Medium — SME: "Should remaining days round up, so that 3h of remaining work still takes one day?"

### RULE-018: Busyness colour bands
**Category:** Calculation
**Priority:** P2
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/resource/ResourceServiceImpl.java:876-886`
**Plain English:** Busyness % = assigned ÷ expected × 100 (2 dp). Under 100% is green, 100% up to (not including) 120% is yellow, and 120% or more is red.
**Specification:**
  Given assigned 180h and expected 165h
  Then busyness = 109.09% → yellow
  And 198h → 120.00% → red; 160h → 96.97% → green
**Parameters:** thresholds 100 and 120 (%); ratio at scale 4, then ×100 at scale 2 HALF_UP
**Edge cases handled:** expected = 0 gives 0% → green (division error swallowed)
**Confidence:** High

### RULE-019: Default task duration from assigned hours
**Category:** Calculation
**Priority:** P2
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/resource/TaskServiceImpl.java:489-508`
**Plain English:** When tasks are re-sequenced, a task's duration in working days = assigned hours ÷ 7.5, rounded up, minimum 1. Without hours, it is the number of working days in its planned range.
**Specification:**
  Given a task with 20h assigned
  Then duration = ceil(20 / 7.5) = 3 working days
  And with 0 or null hours and plan range 2026-03-02 to 2026-03-04, duration = 3
**Parameters:** 7.5 h/day (literal); CEILING; minimum 1
**Edge cases handled:** an end date before the start date gives 1; the calendar fallback is Mon-Fri
**Confidence:** High

### RULE-020: Approval status transition rules; approvers act only on their own lines
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

### RULE-021: Conditions for a timesheet line to be postable to cost
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
**Also stated by:** timesheet-lifecycle — “A row may be posted only if it is approved and has complete cost data” (`psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourPostingCheckServiceImpl.java:117-152`)

### RULE-022: Who can see whose timesheets
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

### RULE-023: Only the owner can edit or resubmit, and only when rejected, cancelled or returned
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

### RULE-024: On a workday, booked hours plus leave must be at least 7.5h
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
**Also stated by:** timesheet-calculation — “On working days, hours plus leave must reach 7.5” (`psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourServiceImpl.java:1215-1226`)

### RULE-025: One timesheet per employee per calendar day
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
**Also stated by:** timesheet-validation — “One timesheet per person per day; duplicate and concurrent submissions blocked” (`psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourServiceImpl.java:1227-1246`)

### RULE-026: The remaining-hours (ETC) gate on project-task packages
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
**Also stated by:** timesheet-calculation — “Submission cannot exceed a task package's remaining hours” (`psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourServiceImpl.java:1275-1310`)

### RULE-027: The three org fields are mandatory on every line
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

### RULE-028: Fields required to send, by work-package type (drafts exempt)
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

### RULE-029: Every line must have an approver before it can be sent
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

### RULE-030: Timesheets can only be booked to a work package the employee is allowed to use right now
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

### RULE-031: Every line must match exactly one standard-cost rate and one billing rate
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

### RULE-032: Which project-task packages are bookable
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

### RULE-033: Opportunity visibility for booking depends on post channel and a 90-day window after a win
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

### RULE-034: A whole day of leave blocks sending a timesheet for that day
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

### RULE-035: On resubmit, unchanged approved lines are kept, and a resubmit with nothing to approve is refused
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

### RULE-036: Today's timesheet cannot be submitted before 09:00 (Shanghai time)
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

### RULE-037: Daily declared-hours cap of 15 and positive hours per line
**Category:** Validation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourServiceImpl.java:1196-1211`
**Plain English:** Every line must have more than 0 declared hours, and the lines for one date may not add up to more than 15 hours.
**Specification:**
  Given lines for 2026-03-02 of 8.0h and 7.5h (total 15.5)
  When the timesheet is saved or submitted
  Then it is rejected with "2026-03-02 填报工时大于15小时！"; a total of exactly 15.0 is accepted
**Parameters:** max daily hours = 15; minimum per line > 0
**Edge cases handled:** totals are grouped by each line's works date
**Suspected defect:** The message says "不能小于0" (cannot be less than 0), but `compareTo(ZERO) < 1` also rejects exactly 0. A null declareHours throws an NPE in `merge` before the check.
**Confidence:** High
**Also stated by:** timesheet-validation — “Every line must have hours greater than zero” (`psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourServiceImpl.java:1196-1204`); timesheet-validation — “No more than 15 hours per day” (`psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourServiceImpl.java:1205-1211`)

### RULE-038: OH, visit, lead and customer work packages
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

### RULE-039: Role-gated "indirect" project and opportunity work packages
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

### RULE-040: Withdrawn timesheets can be deleted only on non-workdays or full-leave days
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

### RULE-041: Tasks can be accepted or refused only while pending
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

### RULE-042: Integrity rules applied before any task is created
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

### RULE-043: Row decisions from BPM task-completion messages
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

### RULE-044: End-of-process result decides the header outcome
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
**Also stated by:** timesheet-lifecycle — “Approval side effects: refresh task progress and retire the replaced original sheet” (`psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/mq/consumer/approval/timesheet/TimesheetProcessResultHandler.java:72-91`)

### RULE-045: Pending becomes In-approval only after BPM has actually started or resumed the process
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

### RULE-046: An approver's personal decision only touches that approver's own rows
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

### RULE-047: How row statuses follow a header change
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

### RULE-048: Timesheet header approval-status state machine (allowed transitions)
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
**Also stated by:** timesheet-lifecycle — “Withdrawing means Cancelled (4), not back to draft, and has no "untouched" guard” (`psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourApproveStatusService.java:172-180`)

### RULE-049: Who may re-initiate (edit and resubmit) a timesheet
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

### RULE-050: "Save" keeps a draft; "Send" queues the approval process
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

### RULE-051: Row approver is assigned at submit by work-category type
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

### RULE-052: Secondary header "status" field follows the approval status
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

### RULE-053: Revision snapshots and their event types
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

### RULE-054: Unchanged, already-approved rows keep their approval on resubmit
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

### RULE-055: New timesheet starts in Pending with a TS-prefixed number and a locked company code
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

### RULE-056: A returned sheet resumes the same BPM instance; a cancelled or ended one starts a new instance
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

### RULE-057: Only a withdrawn sheet on a non-work day or a full-leave day may be deleted
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

### RULE-058: Cost and bill rates are locked at submit, not at approval
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

### RULE-059: Logged hours freeze task staffing from the moment they are filed
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

### RULE-060: Every header status change is written to an append-only audit log
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

### RULE-061: Applying an end result is idempotent and attributed to the submitter
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

### RULE-062: Full-day leave threshold (450 minutes)
**Category:** Policy
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourServiceImpl.java:858-868`
**Plain English:** A day with 450 or more minutes of leave counts as a full leave day. A timesheet for that day cannot be submitted, though a draft can still be saved.
**Specification:**
  Given employee 1001 has 450 min of leave on 2026-03-02
  When they submit (发送) a timesheet for that date
  Then it is rejected "2026-03-02 为整天请假，无需填报工时"
**Parameters:** full-day leave = 450 min (= 7.5 h × 60)
**Edge cases handled:** draft save (暂存) is not blocked; the same threshold makes a withdrawn timesheet deletable (non-working day OR leave ≥ 450 min, `isManHourDeletable` 1561-1569)
**Confidence:** High

### RULE-063: Which project task packages a person can log time to
**Category:** Policy
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourServiceImpl.java:2402-2411`
**Plain English:** A person can only log to task packages assigned to them that still have remaining hours, are DOING or REWORK, are not obsolete, and have not passed their planned end date as of the work date.
**Specification:**
  Given package P assigned to 1001, status DOING, etc_hours null, planned 40, actual 40, plan_end 2026-03-31
  When 1001 opens the timesheet for 2026-03-02
  Then P is not offered (no remaining hours); with actual 32 it is offered with remainingHours 8
**Parameters:** statuses {DOING, REWORK}; remaining check `etc_hours > 0 OR (etc_hours IS NULL AND planned_hours > COALESCE(actual_hours,0))`; date check `plan_end_date IS NULL OR plan_end_date >= worksDate`
**Edge cases handled:** a planned start in the future does not hide the package; the reference date is the work date, not today, so back-filling works; the same list is re-checked on save (`assertWorkSpacesAccessible`)
**Confidence:** High

### RULE-064: Working-day calendar with fallback when a date is missing
**Category:** Policy
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/resource/ResourceServiceImpl.java:1099-1106`
**Plain English:** A date is a working day if the company calendar marks it workday = 1. If the date is not in the calendar, resource planning falls back to Monday-Friday.
**Specification:**
  Given 2026-10-01 (national holiday) is in the calendar with workday = 2
  Then it is not a working day
  Given 2027-03-06 (Saturday) is not in the loaded calendar
  Then it is not a working day; 2027-03-08 (Monday) is
**Parameters:** workday code 1 = working, 2 = rest; fallback is day-of-week < 6
**Edge cases handled:** the timesheet path (`ManHourServiceImpl.isWorkday` 1548-1558) treats any missing date, including weekends, as a working day; labour costing (`ManHourEndServiceImpl` 197-202) fails on a missing date
**Suspected defect:** There are three different fallbacks for the same question. Also, `CalendarServiceImpl.getCalendarMap(DateTime)` (51-63) loads only the current and previous year, so 6- or 12-month forecasts that run into next year ignore next year's holidays.
**Confidence:** Medium — SME: "Which fallback is the business rule when the calendar is not maintained, and is next year's calendar supposed to be loaded in advance?"

### RULE-065: Today's timesheet opens at 09:00 Beijing time
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


## 项目成本与核算

### RULE-066: OH basis is timesheet (source_type 0) not-tax cost; period 13 uses the full-year base
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/cost/projectcost/allocation/AllocationOrchestrator.java:108-146`
**Plain English:** The allocation denominator is the period's direct-labour ledger cost. For the 13th (annual adjustment) period it is the sum of periods 1–12. Each pool line's tier is re-resolved from its cost centre's mapping as of the period-end date.
**Specification:**
  Given period 2026-13 for company C and timesheet ledger rows across 2026-01..12 totalling 12,000,000.00
  When the allocation is built
  Then the basis = 12,000,000.00 (readByYear), not just the 13th-period rows
  Given a pool line whose cost centre has no mapping as of the period-end
  Then the build fails with IllegalStateException
**Parameters:** basis filter `source_type == 0` (LedgerBasisRow.java:37-39); as-of date = period end
**Edge cases handled:** REVERSAL pool lines are taken out of tiering and handled by RULE-04.
**Suspected defect:** Docs COST-R39/R40 still say "wpt0/1 work-package hours". Code uses source_type = 0 because work_package_type is never populated (#875).
**Confidence:** High.

### RULE-067: Three-tier project profit and rates
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/cost/projectcost/profitreport/QueryProjectProfitUseCase.java:551-618`
**Plain English:**
- Gross = revenue − main cost (P&L ledger, written-off basis) − opportunity selling cost.
- BU profit = gross − dept mgmt − dept sales OH.
- Full profit = BU − HQ mgmt − HQ sales OH.
- Each rate = profit × 100 ÷ revenue (2 dp); a project's contribution rate = its full profit ÷ the filtered total full profit.
**Specification:**
  Given revenue 1,000,000.00, main cost 600,000.00, opp 20,000.00, dept OH 50,000.00 + 10,000.00 and HQ OH 30,000.00 + 5,000.00
  Then gross 380,000.00 (38.00%), BU 320,000.00 (32.00%), full 285,000.00 (28.50%)
**Parameters:** rate scale 2, HALF_UP; revenue 0 gives rate 0.00
**Edge cases handled:** Term rows carry opp = 0. A full profit < 0 is flagged as a loss. Results are not cached while allocation is incomplete.
**Confidence:** High.

### RULE-068: OH tier routing and carry-forward when the labour base is zero
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/allocation/OhAllocationCalculator.java:44-110`
**Plain English:** Each OH pool line is filed by tier (delivery-own / service / public) and by nature (management / sales):
- Public OH goes to the company pot.
- Service OH goes to its parent delivery department.
- Own OH goes to its own department, or to the successor department if one is configured.

If the target labour base is ≤ 0, the line is not allocated and waits for a later period.
**Specification:**
  Given OH line {tier = own, costOrg = 10, belongDelivery = 20, mgmt, 5,000.00}, dept 20 basis 80,000.00 and dept 10 basis 0
  When aggregated
  Then 5,000.00 lands in dept 20's own-mgmt bucket and is picked
  Given OH line {tier = service, belongDelivery = 30} and dept 30 has no timesheet basis
  Then it goes to pendingReasons (costOrg, amount), is not picked, and stays in the pool
**Parameters:** tier codes 0 own / 1 service / 2 public; nature 0 = management, otherwise sales
**Edge cases handled:** Public OH is deferred when the company basis is ≤ 0. A null target dept means deferral. A successor is used only if belongDeliveryId ≠ costOrgId (#1548).
**Confidence:** High.

### RULE-069: OH allocation – share each overhead pot by direct-labour cost share, with rounding remainder on the largest line
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/allocation/OhAllocationCalculator.java:196-352`
**Plain English:** Each timesheet ledger line gets four OH amounts: department management, department sales, company management and company sales. Each one is the OH pot times the line's share of labour cost. Any rounding remainder in a pot goes to that pot's largest line, so every pot adds back exactly.
**Specification:**
  Given delivery dept D has timesheet basis lines of 600.00, 300.00 and 100.00 (dept base 1,000.00), the company basis is 2,000.00, D's department management OH (own + service) is 100.00 and the company-tier management OH is 300.00
  When allocation is derived
  Then deptRatio = 0.6000000000 / 0.3000000000 / 0.1000000000, and deptMgmt = 60.00 / 30.00 / 10.00
   And companyRatio for the 600 line = 0.3, so companyMgmt = 300.00 × 0.3 = 90.00
  Given instead a 100.00 pot split over 3 equal lines (ratio 0.3333333333 → 33.33 each, sum 99.99)
  Then the 0.01 remainder goes to the first line with the largest value: 33.34 / 33.33 / 33.33. That line gets tail flag bit0 (dept tier); bit1 is the company tier
**Parameters:** RATIO_SCALE = 10, HALF_UP; AMOUNT_SCALE = 2, HALF_UP; tail bitmask 1 = region, 2 = HQ
**Edge cases handled:** Non-timesheet ledger lines get 0-ratio, 0-amount placeholder lines. Ties for largest line go to the earliest line. No remainder means no tail flag. If company basis is 0, companyRatio = 0. The invariant check (lines 266-304) throws if Σ lines ≠ pot per tier and nature, or if Σ allocated ≠ Σ picked pool lines. The parallel path produces identical results.
**Suspected defect:** Docs (09 rules list COST-R45) call this the "largest remainder method". The code does not do that: it puts the whole remainder on the largest-amount line.
**Confidence:** High — the formula is explicit.

### RULE-070: Reversing an OH accrual mirrors the original allocation in proportion
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/allocation/ReversalMirrorCalculator.java:31-90`
**Plain English:** A negative (reversal) OH pool line is spread back over the lines of the original period's effective allocation, for the same nature. Each line's dept and company parts are scaled by reversal ÷ original nature total. The remainder goes to the largest line's company column.
**Specification:**
  Given the original-period management allocation is line A (dept 300.00, company 200.00) and line B (dept 400.00, company 100.00), total 1,000.00
  When reversal pool line −100.00 (mgmt) is mirrored
  Then A = (−30.00, −20.00) and B = (−40.00, −10.00), Σ = −100.00
**Parameters:** SCALE = 2, HALF_UP
**Edge cases handled:**
- Original nature total = 0 throws.
- Lines with 0 for that nature are skipped.
- All lines skipped throws.
- Σ ≠ reversal throws.
- A missing original pool line or effective batch throws (orchestrator 165-176).

**Suspected defect:** Docs COST-R44 say "分摊无独立红冲入口" (allocation has no separate reversal). The code does have this reversal-mirror path.
**Confidence:** High.

### RULE-071: WIP stage on entry and stage advance (POC exactly 100 and contract returned)
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/carry/CarrySelectorImpl.java:53-124`
**Plain English:** On entry:
- Contract not returned → stage 1 (pre-contract).
- Returned and term POC = 100 at the period's month-end snapshot → stage 3 (completed).
- Otherwise → stage 2 (in progress).

Advances: 1→3 or 1→2 only once the contract is back; 2→3 when POC = 100.
**Specification:**
  Given a term with contract returned and month-end POC 99
  When entering WIP for 2026-07
  Then stage 2
  Given a stage-1 entry with POC 100 but contract not returned
  When advancing
  Then not selected (stays at 1)
**Parameters:** POC_COMPLETED = 100 (integer, strict equality); snapshot key `YYYY-MM`
**Edge cases handled:** Null POC is treated as not complete. For the 13th or adjustment period the month is null, so POC is null.
**Confidence:** High.

### RULE-072: WIP write-off selection – one revenue confirmation releases one whole WIP entry; leftovers go cost-only or wait
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/carry/CarrySelectorImpl.java:126-237`
**Plain English:** Write-off works per project term, considering only in-WIP entries. Stage 4 (impairment) is excluded. Eligible terms are those that are completed (stage 3), belong to a terminated project, or are on the manual list.

Pairing: the i-th unpaired confirmed revenue (dated ≤ period end) is paired with the i-th WIP entry, and the entry's full amount moves (FULL). Leftover entries:
- Terminated project or manual list → cost-only.
- Normal terms → cost-only if any pairing happened or the term is fully confirmed; cost-only with reason 2 if the term amount is 0; otherwise parked.
**Specification:**
  Given a stage-3 term with WIP entries 1,000.00 / 200.00 / 50.00 and one unpaired revenue CV-1
  When write-off is selected
  Then 1,000.00 is FULL paired with CV-1; 200.00 and 50.00 are COST_ONLY tagged "CV-1"; all three go 3·0 → 3·1
  Given the same term with 0 revenues and not fully confirmed
  Then all three are parked
**Parameters:** reason codes 1 = terminated, 2 = zero-amount term, 3 = manual; channel priority terminated > manual > normal
**Edge cases handled:**
- Terminated and manual lines keep their original stage and snapshot the real backed/POC values.
- Revenue left over after the WIP entries run out is not posted alone.
- An empty manual-directive date returns no manual matches.

**Suspected defect:** Docs COST-R60 (write-off cutoff by business_date ≤ latest confirmation date) are not implemented. Pairing is by count, so one confirmation of any size releases a whole entry and pulls every other entry on the term into cost-only.
**Confidence:** Medium. Question for the SME: should pairing be by amount or by business-date cutoff, rather than one revenue confirmation per WIP entry?

### RULE-073: Year-end bonus is split to project terms by the person's annual hours (cent-exact largest remainder)
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/ingest/AnnualBonusSplitCalculator.java:66-123`
**Plain English:** Each term's share = bonus × (person's hours on that term ÷ person's total project hours for the year). Gross and not-tax amounts are split separately. Each share is floored to the cent, then leftover cents go one at a time to the largest fractional remainders.
**Specification:**
  Given a bonus of 1,000.00 (gross = not-tax) and hours 1 / 1 / 1 on three terms
  When split
  Then floor = 333.33 × 3 (99,999 cents), and the 1 leftover cent goes to index 0, giving 333.34 / 333.33 / 333.33
**Parameters:** intermediate scale 10, HALF_UP; cents via FLOOR; tie order = lower index
**Edge cases handled:**
- Empty basis or total hours ≤ 0 throws. The caller routes these to the OH pool (CostRoutingService.java:209-211).
- Source amounts are pre-rounded to cents.
- A conservation assertion is enforced.

**Confidence:** High.

### RULE-074: Cost routing chain and how expense nature is set
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/ingest/CostRoutingService.java:101-235`
**Plain English:** Every cost source line is routed in a fixed order to exactly one destination (sink). OH nature is SALES when fee_type = 1, otherwise MANAGEMENT. The order is:
1. Already ingested → SKIP.
2. Exempt flag → EXEMPT.
3. Zero-hour salary → OH pool.
4. Monthly bonus with fee_type 0 → error.
5. Year-end bonus with fee_type 0: whitelisted level with a person → LEDGER (split); otherwise → OH pool.
6. Opportunity: lost → pool; won and project exists → attach to the project; otherwise → in transit.
7. fee_type 0: with a term → LEDGER; without a term → error.
8. fee_type 1/2 → pool.
9. Any other fee_type → error.
**Specification:**
  Given a year-end bonus line with fee_type 0 and level "m5"
  When routed
  Then OH pool, nature MANAGEMENT
  Given level " t2 " with personId set
  Then LEDGER (split by RULE-07)
**Parameters:** annual-bonus ledger levels default `T1,T2,T3,M3,M4` (CostProjectCostBeanConfig.java:75, config `fst.cost.collection.annual-bonus.ledger-levels`); fee_type 0 project / 1 sales / 2 management
**Edge cases handled:** Levels are trimmed and upper-cased before comparison. The pool tier is resolved from the cost centre, not the cost org (#1203).
**Confidence:** High.

### RULE-075: Timesheet three-element (salary/social/fund) cent rounding anchored to the rounded total
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/ingest/TimesheetElementRounding.java:24-53`
**Plain English:** The three labour elements are each rounded to cents. Any cent difference from round(total) goes to the element with the largest raw absolute value, so the elements always add up to the rounded total.
**Specification:**
  Given salary 100.004, social 20.004 and fund 10.004
  When normalised
  Then total = 130.01 and the elements are 100.01 / 20.00 / 10.00 (the 0.01 remainder goes to salary)
**Parameters:** scale 2, HALF_UP; tie priority salary > social > fund
**Edge cases handled:** null is treated as 0. Negatives are compared by absolute value. Elements that net to 0 are later dropped (CostSummaryViewReader.java:433-441). Timesheet amount equals not-tax amount (no input VAT).
**Confidence:** High.

### RULE-076: Labour reclass – U8C credit vs legacy-system landed cost, by department, with tolerance
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/laborreclass/LaborReclassDiffCalculator.java:49-278`
**Plain English:** For each labour element, legacy-system landed cost per person is split across that person's salary-archive departments in proportion to the archive amounts (remainder on the last row). The difference = U8C credit − legacy system, per U8C department. The line blocks posting unless |diff| ≤ tolerance or cause ⑧ applies (U8C higher, both sides non-zero, no legacy-system gap); ⑧ is non-blocking and the excess stays in U8C.
**Specification:**
  Given dept D with U8C 50,000.00, legacy system 49,999.40 and tolerance 1.00
  Then diff 0.60, cause ROUNDING, non-blocking
  Given U8C 52,000.00 and legacy system 50,000.00 with no unapproved-hours gap
  Then diff 2,000.00, cause RESIDUAL_IN_U8C, non-blocking
**Parameters:** `fst.cost.labor-reclass.diff-tolerance` default 1.00 (LaborReclassPreviewService.java:86-97); MATCH_EPS 0.01; scale 2, HALF_UP
**Edge cases handled:** Causes for no department, unmapped OA department, not collected, unapproved timesheet ⑦, archive mismatch and people with no archive (blocking row).
**Confidence:** High.

### RULE-077: Labour reclass voucher – reverse U8C credit in full, debit legacy-system buckets, absorb small differences
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/laborreclass/LaborReclassVoucherAssembler.java:27-131`
**Plain English:**
- Credits (account × dept) are reversed in full. For ⑧ rows, the excess is first cut from the largest credit rows.
- Debits = legacy-system cells by bucket (project / sales / management) × account × dept.
- Non-blocking, non-⑧ differences are debited to the element's difference account.
- A negative net amount moves to the other side, and 0 amounts are dropped.
**Specification:**
  Given credit 6602/D 52,000.00 and a ⑧ row of 2,000.00
  Then the reversal line = Dr 6602/D 50,000.00 (52,000 − 2,000)
**Parameters:** bucket summaries "按工时重分类-项目 / 销售 / 管理"; scale 2, HALF_UP
**Edge cases handled:** Cells with no account or department mapping are skipped (the problem is already recorded).
**Confidence:** High.

### RULE-078: Vouchers are aggregated per company × voucher word × department
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/voucher/PostingRuleEngine.java:84-181`
**Plain English:** Per-line vouchers are merged into one voucher per (company, voucher word, department aux). Within it, lines with the same subject, debit/credit side and aux signature are summed across terms. Each merged voucher must balance when posting; preview may show unbalanced vouchers.
**Specification:**
  Given 3 write-off lines for dept D (Dr 6401 100.00 / 200.00 / 300.00, each with matching Cr)
  When aggregated
  Then one voucher for D with Dr 6401 600.00 and the summed credits, with batchLineId = null
**Parameters:** strictBalance = true for posting, false for preview
**Edge cases handled:** Lines with no department aux group under the "" key. The preview and posting paths share the same grouping key.
**Confidence:** High.

### RULE-079: Posting rule matching and voucher-line rendering
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/voucher/PostingRuleEngine.java:185-286`
**Plain English:** A rule is picked in this order: same carry_type → non-default rules whose `token==N && …` trigger matches → default rule. Within each level, an exact company match beats a company-null rule, and more than one candidate is an error. Each rule line takes its amount from the fact's amount_formula token, and lines with a null or 0 amount are dropped. Every voucher must balance.
**Specification:**
  Given carry_type 2, a fact with to_wip_stage = 3 and not-tax 800.00 for company 01, and two default rules (company 01 and null)
  When rendered
  Then the company-01 rule is used, Dr main cost 800.00 / Cr WIP 800.00, and the voucher balances
  Given a fact with no revenue tokens
  Then the revenue lines are omitted (cost-only voucher)
**Parameters:** dynamic subject prefix `DYN-`; supplier-required subjects default {A123} (config `fst.cost.carry.supplier-required-subjects`)
**Edge cases handled:**
- An unresolved DYN subject throws.
- A133-style supplier subject without a supplier aux throws.
- A missing required aux throws (null_mode 0), or is skipped / defaulted.
- Summary placeholders {periodName}, {deliveryCenter}, {term} are filled, with missing values blanked.

**Suspected defect:** Docs COST-R54 describe 3-level matching on (carry_type, source_type, company). The code matches on carry_type → trigger condition → company. Docs also say "同一 carry_type 应唯一" (one rule per carry_type, design doc line 205).
**Confidence:** High.

### RULE-080: Revenue-return red reversal – reverse the whole original cost, re-render the voucher and negate it
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/infrastructure/cost/projectcost/carry/PostingVoucherDrafterImpl.java:125-209`
**Plain English:** For each revenue return (cuvalue type 1), the original write-off lines with `paired_cu_value_code` = the return's origin_code (same company) are reversed at their full original not-tax amount. The red voucher is re-derived from the current forward rules, aggregated per return slip × department, and every amount is negated. WIP goes 3·1 → 3·0, and the P&L ledger gets a negated copy of the forward line.
**Specification:**
  Given original FULL line 800.00 and COST_ONLY line 200.00, both tagged CV-1, and return R1 with origin CV-1 for −300.00 not-tax
  When red reversal is posted
  Then both lines are reversed (−800.00, −200.00), giving one red voucher per dept with all amounts negated; the summary is "红冲·{period}·{dept}·产值退回冲销（产值单号 CV-1）"
**Parameters:** carry_type REVENUE_RED_REVERSAL; P&L reversal trigger_type 2 / pl_type 9 (PlLedgerDO.java:129-157)
**Edge cases handled:** No origin_code, no match, or cross-company only → not reversible. A missing origin line throws.
**Suspected defect:**
- The return's own amount is never used: a partial return reverses the entire cost.
- ReversalLinkResolverImpl.java:45/112 comments say only FULL lines carry the code. In fact COST_ONLY lines also carry it (WriteOffLine.java:78-83), so they are reversed too.
- The red voucher uses the current rules and mappings, while the P&L ledger copies the original account (PlLedgerDO.java:140). The two can diverge if the mapping changed.

**Confidence:** Medium. Question for the SME: should a partial revenue return reverse cost in proportion to the returned amount, or always reverse all linked cost?

### RULE-081: Write-off voucher amounts and credit account derivation
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/infrastructure/cost/projectcost/carry/PostingVoucherDrafterImpl.java:507-568`
**Plain English:** The cost leg uses the batch line's not-tax amount. The credit account comes from the cost-type → U8C account mapping in the fee_type '0' (project cost) tier only, and must be unambiguous. The not-tax amount goes to the prepay token if the resolved account is a supplier-required subject, otherwise to the expense token. A FULL write-off also brings the paired confirmation's tax-inclusive amount (contract asset), not-tax amount (revenue) and tax.
**Specification:**
  Given a FULL line with not-tax 800.00 and paired cuValue {complete 1,060.00, no_tax 1,000.00, tax 60.00}
  When the fact is built
  Then tokens = notax_amount 800.00, expense_notax_amount 800.00, complete_amount 1,060.00, TBD_REVENUE_NOTAX 1,000.00, TBD_TAX 60.00
  Given cost type X maps to two different accounts under fee_type 0
  Then X is treated as unmapped and the batch fails with a readable error
**Parameters:** `FEE_TYPE_PROJECT_COST = "0"` (lines 652-672); tokens as listed
**Edge cases handled:** Department falls back to the ledger's name snapshot for the summary only. A missing paired cuValue throws.
**Confidence:** High.

### RULE-082: WIP period balance and report increase/decrease use the absolute movement amount
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/infrastructure/cost/projectcost/wipsnapshot/WipPeriodSnapshotMapper.java:53-113`
**Plain English:** An entry's balance at a period's close = Σ |not-tax| of increase movements (enter WIP, write-off reversal) − Σ |not-tax| of decrease movements (write-off, termination, exemption). Aging = period end − earliest business date of entries with a positive balance.
**Specification:**
  Given entry E entered with +500.00 and later written off
  Then balance = 500 − 500 = 0
  Given entry F entered with −120.00 (negative cost is allowed per RULE-09)
  Then balance = +120.00
**Parameters:** direction map in WipMovementDirection.java:15-28; cutoff = `create_time ≤ period closed_time`
**Edge cases handled:** Neutral movements (advance, reclass, impairment) only set stage and status.
**Suspected defect:** ABS() turns negative WIP entries into positive balances. WipReportMapper.java:52-61 (increase/decrease) does the same, so WIP balance and the report overstate WIP by twice any negative cost entered.
**Confidence:** Medium. Question for the SME: should negative cost entries reduce WIP? If so, ABS should only normalise the sign of red-reversal and decrease movements.

### RULE-083: WIP aging impairment flag after 720 days
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/cost/projectcost/carry/WipImpairmentSweepService.java:69-175`
**Plain English:** After a write-off batch posts, every in-WIP entry at stage 1, 2 or 3 is checked. Days are counted from the end of the period it entered WIP to the end of the current period; at ≥ 720 days it moves to stage 4 (impairment). This is frozen: it is excluded from every write-off channel and writes movement type 7 with no voucher.
**Specification:**
  Given an entry that entered in a period ending 2024-06-30, with the current period ending 2026-06-30 (730 days)
  When the 2026-06 write-off batch posts
  Then stage → 4 and a movement is recorded with the entry's amount
**Parameters:** `fst.cost.wip.carry.impairment-aging-days`, default 720 (values ≤ 0 fall back to 720)
**Edge cases handled:** A missing period end means no sweep. A missing entered period end is never treated as aged. A CAS guard prevents double flips.
**Confidence:** High.

### RULE-084: Splitting opportunity cost across terms – drawer and export use different formulas
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/cost/projectcost/profitreport/QueryProjectProfitUseCase.java:151-295`
**Plain English:**
- The detail drawer splits project opportunity cost by term contract amount, with the remainder on the last term. If all contract amounts are missing or the total is ≤ 0, it splits evenly.
- The export Sheet2 always splits evenly across rows, and those rows include the "(未指定条款)" (unassigned-term) bucket.
**Specification:**
  Given opp 10,000.00 and terms T1 (contract 300,000) and T2 (contract 100,000)
  Then the drawer shows T1 7,500.00 / T2 2,500.00, while the export shows 5,000.00 / 5,000.00
**Parameters:** scale 2, HALF_UP; remainder goes to the last row
**Edge cases handled:** With no terms, the drawer keeps the opportunity "carry" row.
**Suspected defect:** The drawer comment (line 153) claims it matches Sheet2's fallback, but Sheet2 never weights by contract amount. The two outputs give different gross profit for the same term.
**Confidence:** Medium. Question for the SME: which basis should the export use?

### RULE-085: Contract asset and receivable composition; zeroed for closed or terminated projects
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/cost/projectcost/profitreport/QueryProjectProfitUseCase.java:661-721`
**Plain English:**
- Net invoiced = invoiced − credit notes.
- Contract asset = confirmed value incl. tax − net invoiced; net = asset − impairment.
- AR = net invoiced − collected (can be negative, meaning advance receipt); net = AR − bad debt.
- All six figures show 0 for projects that are 已关闭 (closed) or 终止 (terminated).
**Specification:**
  Given confirmed value 1,060,000.00, invoiced 800,000.00, flushed 50,000.00, collected 600,000.00 and impairment 10,000.00
  Then net invoiced 750,000.00, contract asset 310,000.00 (net 300,000.00), AR 150,000.00
**Parameters:** suppressed statuses: FSTConstant.ProjectStatus.已关闭 and 终止
**Edge cases handled:** Missing extras count as 0.
**Confidence:** High.

### RULE-086: OA→U8C credit matching for labour reclass
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/laborreclass/OaCreditResolver.java:42-166`
**Plain English:** Each OA accrual row is matched to a line of its referenced U8C voucher in three rounds:
1. Same amount and account, preferring the interface-mapped department and then the psa_dept_u8c department.
2. Interface-merged groups whose sum equals one line; that line is split back per OA row, with the remainder on the last row.
3. A unique remaining candidate.

Unclaimed lines on whitelisted vouchers are kept; lines on other vouchers are excluded.
**Specification:**
  Given OA rows 3,000.00 + 2,000.00 for interface dept A03 and U8C line A03 for 5,000.10
  Then the credits are 3,000.00 and 2,000.10
**Parameters:** amount compare at scale 2, HALF_UP
**Edge cases handled:**
- Rows in the wrong period are flagged as a problem.
- A payment-only voucher with no labour lines is skipped.
- Forms on external payroll accounts with no lines are skipped.
- Ambiguous matches are flagged.

**Confidence:** High.

### RULE-087: Person-month rounding true-up to the payroll accrual
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/rounding/PersonMonthRoundingCalculator.java:16-74`
**Plain English:** For each person, salary month and labour element, payroll accrual minus landed cost is posted to the largest landed row. This only happens when the gap is small enough to be explained by per-row rounding.
**Specification:**
  Given person P in 2026-07 with 3 salary rows landed totalling 2,999.99 and accrual 3,000.00
  When the true-up runs
  Then the bound = 3 × 0.005 + 0.005 = 0.02, and +0.01 goes to the largest row (ties go to the lowest rowId)
  Given the gap is 0.03 instead
  Then it is skipped as "business gap"
**Parameters:** PER_ROW_TOLERANCE 0.005, BASE_TOLERANCE 0.005; adjustment rounded to 2 dp HALF_UP
**Edge cases handled:** Skip when:
- the person-month has open exceptions or exemptions;
- there is no accrual row;
- approved hours ≤ 0;
- diff = 0;
- the line is not a labour cost type.

**Confidence:** High.

### RULE-088: Zero-value cost never enters WIP; negative cost does
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/infrastructure/cost/projectcost/carry/CarryCandidateReaderImpl.java:42-58`
**Plain English:** Candidates to enter WIP are pending ledger rows of the period and company with not-tax ≠ 0 (this also excludes NULL). Negative amounts, such as reversals, are carried into WIP.
**Specification:**
  Given ledger rows with not-tax 0.00, −120.00 and 500.00 in period P
  When WIP-enter candidates are read
  Then −120.00 and 500.00 are selected; 0.00 is not
**Parameters:** `entry_status = 0`, `notax_amount <> 0`
**Edge cases handled:** Zero-value timesheet elements are dropped at ingest (CostSummaryViewReader.java:433-441). The expense flow allows negative and zero values, while the timesheet flow rejects negatives (CostValidationService.java:170-191).
**Suspected defect:** Docs COST-R52 select candidates by `period_id ≤ current`. The code uses `period_id = current` (`.eq`).
**Confidence:** Medium. Question for the SME: should late ledger rows from earlier open periods be picked up by the current WIP-enter batch?

### RULE-089: Legacy carry voucher – debit = Σ credits per company × U8C dept × subject
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/wip/CarryForwardServiceImpl.java:134-197`
**Plain English:** For each U8C department in a company, the legacy voucher has one credit line per subject (sum of amounts, 0 skipped) and one debit line on the fixed WIP or COST debit subject equal to the sum of the credits. Each line is rounded to 2 dp independently.
**Specification:**
  Given dept D with subjects 5001 (100.004) and 5002 (200.004)
  Then credits are 100.00 and 200.00, and the debit is round(300.008) = 300.01, which is unbalanced by 0.01
**Parameters:** debit subjects U8cSubjectEnum.WIP_借 / COST_借; HALF_UP 2 dp
**Edge cases handled:** Subjects with a zero total are skipped.
**Suspected defect:** The debit is rounded from the unrounded sum while each credit is rounded separately, so the voucher can be off by a few cents.
**Confidence:** High.

### RULE-090: Legacy WIP/cost carry-forward – labour = person-days × daily wage; project bonus 5%
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/wip/WipServiceImpl.java:735-766`
**Plain English:** On the old WIP carry screens (`/fst/accounting/wip`, still exposed):
- WIP amount for a term = sum of 7 cost categories (labour, daily, travel, payment request, special payment, outsourcing, info service).
- Project bonus = confirmed amount × 5%.
- Labour cost per row = person-days × daily wage, rounded to 2 dp (CarryForwardServiceImpl.java:535-548).
**Specification:**
  Given confirmed amount 200,000.00 and term costs labour 80,000.004 + daily 5,000.00
  Then project bonus 10,000.00 and WIP 85,000.00
**Parameters:** `FSTConstant.项目奖金比例 = 0.05` (yudao-module-fst-api FSTConstant.java:282); HALF_UP 2 dp; currency hardcoded "CNY"
**Edge cases handled:** A null daily wage is treated as 0. Rows with WIP amount 0 are filtered out.
**Suspected defect:** These parallel legacy carry-forward endpoints coexist with the new carry engine (RULE-11 to RULE-15), so the two can produce conflicting postings.
**Confidence:** Medium. Question for the SME: are the legacy `/fst/accounting/wip` and `/cost` carry-forward endpoints retired, and is the 5% bonus still policy?

### RULE-091: Period open/close preconditions (sequential opening, month-end done, reconciled)
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

### RULE-092: Full OH allocation: one effective full run per period, never concurrent
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

### RULE-093: Carry draft gate order, and the red-reversal exception
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

### RULE-094: Carry confirm re-checks the gates and dry-runs the vouchers
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
**Also stated by:** cost-validation — “Carry confirm requires vouchers that render balanced and fully mapped” (`psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/cost/projectcost/carry/CarryBatchService.java:196-235`)

### RULE-095: Red reversal confirm requires a valid reason and a successful red-voucher preview
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

### RULE-096: Collection start preconditions and order of side effects
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

### RULE-097: Exempting a collection error needs a reason and only works on OPEN errors
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

### RULE-098: Labor reclass confirm and deliver must be fresh and follow period order
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

### RULE-099: Unreconciled expense lines block carry and period close
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

### RULE-100: Landing conservation must balance exactly (zero tolerance)
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

### RULE-101: Collection confirmation gate
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
**Also stated by:** cost-validation — “Collection confirmation gate” (`psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/batch/CostCollectionBatch.java:367-388`)

### RULE-102: Ingest pre-routing guards: duplicates, exempt flag, department bonus
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

### RULE-103: Project fees without a contract term never go to the OH pool
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

### RULE-104: Mandatory dimensions depend on where the cost will land
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

### RULE-105: Labor reclass differences over ¥1.00 block the voucher
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/laborreclass/LaborReclassDiffCalculator.java:180-209`
**Plain English:** For each labor element and U8C department, a U8C-minus-legacy-system difference blocks the voucher if it exceeds the tolerance, the department is unmapped, or there is no department. One exception: when U8C is higher, both sides have amounts, and there is no legacy-system-side shortfall, the residual stays in U8C and does not block.
**Specification:**
  Given salary element, U8C dept D01: U8C ¥100,000.80, legacy system ¥100,000.00
  Then diff ¥0.80 ≤ ¥1.00 → ROUNDING, not blocking
  Given U8C ¥105,000.00, legacy system ¥100,000.00, no unapproved or other gap
  Then RESIDUAL_IN_U8C, not blocking
  Given U8C ¥95,000.00, legacy system ¥100,000.00
  Then blocking (diff −5,000 exceeds the ¥1.00 tolerance in absolute value; the legacy system cannot reverse more than U8C holds)
**Parameters:** `fst.cost.labor-reclass.diff-tolerance` default 1.00 (a negative value falls back to 1). MATCH_EPS = 0.01 for archive-mismatch hints.
**Edge cases handled:** A person-level shortfall within tolerance is treated as rounding. A diff explained by unapproved timesheets is classified TIMESHEET_UNAPPROVED but still blocks.
**Confidence:** High.

### RULE-106: Re-running collection reverses a prior confirmation; it is blocked once allocation is confirmed
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

### RULE-107: Collection cannot start until payroll is complete for everyone who filed timesheets
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

### RULE-108: Month-end cost processing only runs while the period is Pre-closed or Reopened
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
**Also stated by:** timesheet-validation — “The accounting period for the work date must be open” (`psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/accounting/AccountingPeriodServiceImpl.java:38-69`)

### RULE-109: Trial allocation needs a completed collection batch, but not a confirmed collection
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

### RULE-110: Row-level recon disposition: which actions are allowed and when
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

### RULE-111: Expense amounts can be negative or zero; timesheet element amounts cannot be negative
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

### RULE-112: Posting pre-check: pool-bound expenses need an allocation mapping effective on the business date
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

### RULE-113: Posting pre-check: an expense's links must match its fee type
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

### RULE-114: A project fee must be borne by a delivery department, unless no delivery departments are flagged yet
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

### RULE-115: Legacy WIP second carry checks the wrong prerequisite
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

### RULE-116: Full allocation takes effect atomically with pool write-back and step advance
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

### RULE-117: A batch becomes POSTED only when U8C has confirmed every voucher; then the step advances
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

### RULE-118: Creating a carry draft (asynchronous)
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

### RULE-119: Posting phases and the completion condition
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

### RULE-120: Allocation batch lifecycle
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

### RULE-121: Collection batch status and phase lifecycle
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

### RULE-122: Carry batch status and phase state machine
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

### RULE-123: WIP stage assignment and advancement
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

### RULE-124: Period month-end step gate: collection lock, confirm, and re-collection rollback
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

### RULE-125: Carry steps are strictly serial: occupy on draft, advance on POSTED, release on cancel
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

### RULE-126: WIP write-off flips the entry from in-WIP to relieved and books a P&L line
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

### RULE-127: Red reversal returns a relieved WIP entry to in-WIP and books one negative P&L line per positive line
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

### RULE-128: Legacy carry-forward lifecycle (待结转 → 结转中 → 已结转), still exposed by controllers
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

### RULE-129: Accounting period status transition table
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

### RULE-130: Cancelling a carry draft
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

### RULE-131: Re-running collection resets the batch in place; overwrite mode purges first
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

### RULE-132: U8C voucher delivery and the opening-year local bypass
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

### RULE-133: Opening-period collection confirm can bulk-exempt all open errors
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

### RULE-134: A cost dated in a closed period lands in the earliest later open period
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

### RULE-135: Month-end cost processing only runs while the period is pre-closed or reopened
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

### RULE-136: Aged in-WIP cost is frozen into impairment (stage 4) after write-off posting
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

### RULE-137: Pre-posting "BLOCK" checks are advisory; only the salary check is enforced
**Category:** Policy
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/cost/projectcost/prepostingcheck/PrePostingCheckUseCase.java:26-35`
**Plain English:** The "业务数据检测" page runs every check and returns results, but no collection, allocation or carry entry point reads them. Items labelled BLOCK do not stop anything. The exception is salary completion, which is enforced separately (RULE-05).
**Specification:**
  Given TimesheetApprovalCheck (level BLOCK) reports 35 timesheet rows not approved, ¥42,000.00 labor cost affected
  When finance executes collection
  Then collection is not blocked, and those hours' cost simply does not enter the legacy system
**Parameters:** BLOCK checks: TimesheetApproval, TimesheetDeptMapping, TimesheetPostingIssue, TimesheetZeroCost, ExpensePostingIssue, ExpenseNotRatified, ExpenseSnapshotViewDiff, OutsourcingAccrualBlock, PocSnapshot, ConfirmedTermPoc, SalaryCalculation. WARN checks: TimesheetApprovalHours, TimesheetOpportunityStatus, TimesheetProjectStatus, ExpenseOutsourcingNotRatified, OutsourcingAccrualWarn.
**Edge cases handled:** The year-end period returns "not applicable" for monthly checks.
**Suspected defect:** Users will read "阻断" (block) as a hard gate. Several BLOCK items (unapproved hours, zero-cost hours, outsourcing accrual gap, which can drive U8C negative balances at WIP carry) have no enforcement anywhere.
**Confidence:** High that nothing enforces them (no caller outside the controller). SME: which BLOCK items should become hard gates on collection or carry?

### RULE-138: Voucher delivery retry, suspension and manual re-delivery
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


## 售前与报价

### RULE-139: Server rates always overwrite client rates; at submission every work package must have a fully priced role
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/presales/quote/QuotationCostRateResolver.java:38-103`
**Plain English:** Every work package's billing and cost rates are replaced with the master-data rates for its role (RULE-11). In draft mode a work package with no role keeps its hours but its rates are cleared. In submission/approval mode any work package with no role, or a role without both a positive billing rate and a positive cost rate, is rejected.
**Specification:**
  Given a work package with role pd, 40 h, and a client-sent billing rate of ¥1,200/h, where master data gives pd = ¥950 / ¥400
  When  saved or submitted
  Then  the node is stored with billRate ¥950 and costRate ¥400 (value ¥38,000, labour ¥16,000)
  And   a work package with role dev1 (bill ¥0) is rejected at submit with QUOTATION_POSITION_RATE_UNAVAILABLE
**Parameters:** rate must be > 0 on both sides
**Edge cases handled:** The role dropdown shows only fully priced roles (`ListPositionRatesUseCase.java:34-36`, `PositionRate.java:50-56`).
**Suspected defect:** (1) PRE-R02 ("billing rates may be discounted, hours may not be changed") is not implemented: there is no rate discount, any client rate is overwritten, and hours are freely editable by cost editors. (2) The duplicate-rate check (lines 54-57) can never fire, because the query already removes duplicates with putIfAbsent.
**Confidence:** High for the code. SME question: "Is a billing-rate discount still required (PRE-R02), and at which level: line, work package, or whole quotation?"

### RULE-140: At submit, cost and margin are recalculated from the current rates before approval starts
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/presales/quote/SubmitQuotationUseCase.java:51-81`
**Plain English:** On submit, the stored cost tree is re-priced at today's master rates, then labour cost, BU cost and gross margin are recalculated (`Quotation.applyAuthoritativeCostNodes`) and saved, so the approval is based on server-calculated figures.
**Specification:**
  Given a draft saved last month with pd at ¥900/h × 100 h (labour ¥90,000), where pd is now ¥950/h
  When  the BU user submits
  Then  laborCost becomes ¥95,000 and buCost and grossMargin are recalculated before the approval process is started
**Parameters:** none
**Edge cases handled:** A submitter with cost-edit permission uses strict resolution. A sales submitter (no cost-edit permission) uses draft resolution: work packages without a role are allowed and their rates cleared.
**Suspected defect:** A sales submission can therefore go to approval with labour cost understated (unpriced work packages count as 0) and margin overstated. The safeguards are the approval cost-required check (RULE-15) and the BU approver's adoption.
**Confidence:** High.

### RULE-141: Cost-tree work package value = hours × billing rate; labour cost = hours × cost rate
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/presales/quote/quotation/CostEstimateNode.java:74-102`
**Plain English:** For each work package the server stores a billing value of hours × billing rate and contributes hours × cost rate to labour cost. Clause and deliverable nodes simply store the amount the user typed.
**Specification:**
  Given a work package of 40 h with billing rate ¥800/h and cost rate ¥350/h
  When  the quotation is saved
  Then  the stored node amount = ¥32,000 and its labour-cost contribution = ¥14,000
  And   a clause node with amount ¥31,800 is stored as ¥31,800 (passed through unchanged)
**Parameters:** none
**Edge cases handled:** Null hours or rate counts as 0. Any amount sent by the client for a work package is ignored. Legacy "othercost" nodes contribute their amount (null = 0).
**Suspected defect:** (1) The multiplication is not rounded; for example 12.5 h × ¥833.33 = ¥10,416.625, so the value saved depends on the DB column scale. (2) The seed docs (Q5 "逐层向上汇总", PRE-R04 "自底向上汇总标价") say the tree rolls up into the price. In code, deliverable and clause amounts are never derived from their work packages, and the tree's billing total (Σ hours × billing rate) is never compared with finalAmount on the server. The roll-up exists only for display (front-end `clauseBill`).
**Confidence:** Medium — the logic is explicit, but SME question: "Should the bottom-up billing value constrain or check the final quote, or is it informational only?"

### RULE-142: Final quote equals the sum of line amounts plus the tail adjustment
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/presales/quote/quotation/Quotation.java:237-246`
**Plain English:** The server recalculates the final quoted price on every save as the sum of all product/service line amounts plus a manual tail adjustment, which can be negative. It never accepts a total sent by the client.
**Specification:**
  Given a draft quotation with line A amount ¥10,600.00 and line B amount ¥5,000.00, and tail adjustment −¥600.00
  When  the editor saves
  Then  finalAmount = 10,600 + 5,000 − 600 = ¥15,000.00
**Parameters:** none (tail adjust defaults to 0 when null)
**Edge cases handled:** Null tail adjust is treated as 0. A negative tail adjust is explicitly allowed (this is how a "negative discount" is applied). Lines are re-derived first (RULE-03). No rounding is applied to the sum.
**Suspected defect:** Nothing stops the tail adjustment from wiping out most of the line total. The only limits are finalAmount > 0 at submit and clause-sum equality (RULE-10).
**Confidence:** High — explicit arithmetic.

### RULE-143: BU estimated cost = labour cost (whole tree) + the 8 fixed other-cost categories
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/presales/quote/quotation/Quotation.java:247-251`
**Plain English:** BU cost is labour cost (summed over every work package at every level of the tree) plus eight subcontract/other cost categories: safety assessment, lighting assessment, window-cleaning machine, construction-drawing sign-off, other subcontract, information services, travel, and printing.
**Specification:**
  Given work packages totalling 120 h × ¥350/h = ¥42,000 labour, travel ¥3,000, printing ¥500, and the other categories empty
  When  saved
  Then  laborCost = ¥42,000 and buCost = ¥45,500
**Parameters:** 8 categories (`OtherCosts.java:47-58`); an empty category counts as 0
**Edge cases handled:** "othercost" nodes are removed from the tree before summing so legacy clients cannot double-count (`Quotation.java:225-228`). If the request has no other-costs object (no permission or old client), the stored values are kept.
**Confidence:** High.

### RULE-144: Gross margin is calculated on the ex-tax final quote using the header tax rate, to 4 decimals
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/presales/quote/quotation/Quotation.java:252-259`
**Plain English:** Gross margin = (ex-tax final quote − BU cost) ÷ ex-tax final quote. The ex-tax final quote is the sum of the lines' ex-tax amounts plus the tail adjustment divided by (1 + header tax rate), rounded to cents; the margin is then rounded to 4 decimals. A fixed 1.06 is never used, and the header rate is not applied to the whole quote.
**Specification:**
  Given one line of ¥113,000 at 13% (line ex-tax ¥100,000.00), tail adjustment ¥0, header tax rate 0.06, buCost ¥45,500.00
  When  saved
  Then  exTax = ¥100,000.00 and grossMargin = (100,000 − 45,500) ÷ 100,000 = 0.5450
  Given final quote ≤ 0 or exTax ≤ 0
  Then  the margin is null and null is written explicitly
**Parameters:** exTax scale 2 HALF_UP; margin scale 4 HALF_UP. The same formula applies on save, on submit and in approval (RULE-145). Phase 2 budget gross margin (RULE-182, RULE-239) uses the same basis.
**Legacy behaviour:** exTax = final ÷ (1 + header rate), which overstates margin when lines carry different rates (¥113,000 at 13% gave ¥106,603.77).
**Corrected:** 2026-10-21 per the verdict "wrong" of 2026-10-14 (RULE_REVIEWS.json); the legacy behaviour is kept only as evidence.
**Confidence:** High.

### RULE-145: Approval-time edits recalculate the final quote from the stored line total plus the new tail adjustment
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/presales/quote/quotation/Quotation.java:367-410`
**Plain English:** Recalculation during approval uses the same calculation as save: the lines are reloaded and their tax splits are re-derived from the header tax rate, then ex-tax amount and margin are recalculated. The final quote is not recalculated or changed in approval (clauses are locked, RULE-155). If labour cost is empty on an old record, it is recalculated from the cost tree before BU cost and margin.
**Specification:**
  Given a pending quotation with one line of ¥106,000 at 6%, final ¥106,000, tail ¥0, buCost ¥45,500
  When  the approver adopts tax rate 0.09 and no tree
  Then  the line is re-split at 9% (ex-tax ¥97,247.71, tax ¥8,752.29), final stays ¥106,000, grossMargin = (97,247.71 − 45,500) ÷ 97,247.71 = 0.5321
  Given an old record with labour cost null and the approver adopts only other costs
  Then  labour cost is first recalculated from the cost tree, so BU cost keeps its labour part
**Parameters:** same rounding as RULE-144. An empty adopted tree counts as "not adopted".
**Legacy behaviour:** the line total was worked back as (old final − old tail) without reloading lines, so a tax change left line splits stale; a null labour cost dropped out of BU cost and inflated margin.
**Corrected:** 2026-10-21 per the verdict "wrong" of 2026-10-14 (RULE_REVIEWS.json); the legacy behaviour is kept only as evidence.
**Confidence:** High.

### RULE-146: Line amount: a hand-entered discounted total overrides quantity × unit price
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/presales/quote/quotation/QuotationLine.java:86-93`
**Plain English:** Each line's tax-inclusive amount is the hand-entered "discounted tax-inclusive total" if one is filled in. Otherwise it is quantity × tax-inclusive unit price.
**Specification:**
  Given a line with quantity 2 and tax-inclusive unit price ¥6,200.00
  When  amountTaxDisc is empty
  Then  the line amount is ¥12,400.00
  And   when amountTaxDisc = ¥10,600.00 the line amount is ¥10,600.00 (the hand-entered value wins)
**Parameters:** none
**Edge cases handled:** Null quantity or null unit price counts as 0. A hand-entered value of 0 counts as "filled" (it is not null), so the line amount is 0.
**Confidence:** High.

### RULE-147: Role rates come from the master-data schemes in effect today, by role and city, newest scheme first
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/infrastructure/presales/quote/PositionRateQueryImpl.java:50-92`
**Plain English:** A role's billing rate and cost rate are read from separate rate schemes. Only schemes that are published (status = 2), approved (approval status = 3) and in effect today are used. Within them only role-level rows are read (salary_grade_no = '0'), filtered to the quotation's city when one is set. The scheme with the latest effective start date wins. A role that exists on only one side gets 0 on the other.
**Specification:**
  Given billing schemes A (start 2026-01-01, pd ¥900/h) and B (start 2026-07-01, pd ¥950/h), both in effect on 2026-10-14
  And   a cost scheme with pd ¥400/h, and the role dev1 present only in the cost scheme at ¥300/h
  When  rates are listed for the quotation's city
  Then  pd = {bill ¥950, cost ¥400} and dev1 = {bill ¥0, cost ¥300}
**Parameters:** status = 2, approval_status = 3, salary_grade_no = '0', effective_end_date null = open-ended (`BillRateMapper.java:19-26`, `BillRateDetailMapper.java:24-30`)
**Edge cases handled:** Quotations without a city (legacy) take, for each role, the row with the smallest city code. Rows with a null rate are skipped. With no scheme in effect the list is empty.
**Suspected defect:** Rates are taken as of the server's current date (`LocalDate.now()`), not the quotation's creation or submit date. Every save, submit or approval adoption re-prices the tree at that day's rates. Two schemes with the same start date are resolved by DB row order, which is not deterministic.
**Confidence:** High for the mechanics. SME question: "Should a quotation lock rates at creation or submission, or always use the rates in effect today?"

### RULE-148: Header tax rate defaults to 6% and must be between 0 (inclusive) and 1 (exclusive)
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/presales/quote/quotation/Quotation.java:232-240`
**Plain English:** The quotation tax rate is taken from the request if supplied, otherwise kept from the stored value, otherwise defaults to 6%. Header and line rates outside [0, 1) are rejected (range check at lines 472-475 and 483-487).
**Specification:**
  Given a new draft (tax rate set to 0.06 at creation) and a save request with no taxRate
  When  saved
  Then  taxRate stays 0.06
  And   a save with taxRate = 1.00 or −0.01 is rejected with "税率须在 0（含）到 1（不含）之间"
**Parameters:** DEFAULT_TAX_RATE = 0.06 (`Quotation.java:28`)
**Edge cases handled:** The range check prevents division by zero in amount ÷ (1 + rate). A line rate of null falls back to the header rate.
**Suspected defect:** The seed doc limits rates to the set 0/1/3/5/6/9/13%. The server accepts any value in [0, 1), for example 0.4.
**Confidence:** High.

### RULE-149: Line tax split, ex-tax unit price and implied discount are derived on the server
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/presales/quote/quotation/QuotationLine.java:107-126`
**Plain English:** On save the server recomputes each line's ex-tax amount, tax amount, ex-tax unit price and unit discount. If the line has no tax rate of its own, it uses the quotation's header tax rate.
**Specification:**
  Given a line with quantity 2, unit price ¥6,200.00, hand-entered amount ¥10,600.00, no line tax rate, and header tax rate 6%
  When  the quotation is saved
  Then  amountNotax = 10,600 ÷ 1.06 = ¥10,000.00 (rounded half-up to 2 decimals)
  And   taxAmount = 10,600 − 10,000.00 = ¥600.00 (found by subtraction, so amountNotax + taxAmount always equals the amount exactly)
  And   unitPriceNotax = 6,200 ÷ 1.06 = ¥5,849.06
  And   unitDiscount = 10,600 ÷ 12,400 = 0.8548 (rounded half-up to 4 decimals)
  And   the line's tax rate is stored as 0.06 (the inherited header rate)
**Parameters:** 2-decimal HALF_UP for amounts and unit price; 4-decimal HALF_UP for the discount ratio
**Edge cases handled:** A completely blank line (no amount, quantity or price) is left untouched. When quantity × price ≤ 0 the discount is null. When no discount total is entered, the discount is always 1. Values sent by the client for these derived columns are ignored.
**Confidence:** High.

### RULE-150: A clause percentage is a UI convenience; only the amount is stored, rounded to cents
**Category:** Calculation
**Priority:** P1
**Source:** `psa-front-desk-pc/src/views/project/quotation/components/CostEstimateTree.vue:486-538`
**Plain English:** In the editor a clause can be entered as a percentage of the final quote. The amount becomes round(final × pct ÷ 100, 2 decimals). The percentage itself is not saved; it is shown back as round(amount ÷ final × 100, 2 decimals).
**Specification:**
  Given finalAmount ¥100,000.01 and three clauses entered as 33.33%, 33.33% and 33.34%
  When  the percentages are committed
  Then  the amounts become ¥33,330.00, ¥33,330.00 and ¥33,340.00 (sum ¥100,000.00)
  And   the server rejects submit because the sum ≠ ¥100,000.01 (RULE-09); no front-end warning appears because the total is under, not over
**Parameters:** amount precision 2 decimals (Math.round); display percentage precision 2 decimals
**Edge cases handled:** Input is disabled while finalAmount ≤ 0. Invalid or blank input leaves the amount unchanged. Hand-typed amounts are normalised to cents.
**Suspected defect:** Rounding each percentage to cents can leave a 1-cent remainder that the exact server check rejects, and there is no "put the remainder in the last clause" rule. Note that this rule's source is outside the listed back-end paths.
**Confidence:** Medium — this is front-end-only behaviour. SME question: "When a percentage split doesn't divide evenly, which clause should absorb the remainder?"

### RULE-151: Cost template summary shows clause, deliverable and work-package counts and total hours; templates store no rates
**Category:** Calculation
**Priority:** P2
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/presales/quote/costestimate/CostTemplate.java:106-126`
**Plain English:** Template list rows show the number of clauses, deliverables and work packages and the sum of work-package hours. Templates keep only role and hours; rates are applied when the template is used in a quotation (RULE-12).
**Specification:**
  Given a template with 2 clauses, 3 deliverables, and work packages of 40 h, 24 h and 16 h
  When  the template list is shown
  Then  the counts are 2 / 3 / 3 and total hours = 80
**Parameters:** none
**Edge cases handled:** Null hours count as 0. A PREPAY clause in a template can carry an amount (`CostTemplateNode.java:25-26`).
**Suspected defect:** The seed doc says template rates come from a front-end static table; in code, rates come from master data at use time.
**Confidence:** High.

### RULE-152: Opportunity stats card: total, won, lost, sum of amounts, and number of quoted opportunities
**Category:** Calculation
**Priority:** P2
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/infrastructure/presales/quote/OpportunityQueryImpl.java:116-140`
**Plain English:** Within the viewer's data scope and the active filters, the card shows the number of opportunities, the number closed as won and as lost, the sum of opportunity amounts, and the number of opportunities with at least one quotation.
**Specification:**
  Given 10 opportunities in scope (3 won, 2 lost, amounts summing to ¥5,000,000, one with a null amount), 4 of which have quotations
  When  the stats load
  Then  total = 10, won = 3, lost = 2, totalAmount = ¥5,000,000, quoted = 4
**Parameters:** close status constants `FSTConstant.OppCloseStatus.赢单关闭 / 丢单关闭`
**Edge cases handled:** All figures come from one data-scoped query, so counts cannot leak company-wide numbers. Null amounts are skipped.
**Suspected defect:** "Quoted" counts quotations in any status, including drafts and rejected ones.
**Confidence:** High for the code. SME question: "Should 'quoted opportunities' count only submitted or approved quotations?"

Two things worth flagging:
- **Clause percentage is front-end only.** RULE-10's source is a Vue file, outside the listed back-end paths. I included it because it is the only implementation of the percentage-of-final-quote split.
- **No opportunity expense accounting in these paths.** I found no rule for it. `service/customer` contains only contact management, and PRE-R07/R08 (intermediary fees, cancellation on lost opportunity) are not implemented in any listed path.

### RULE-153: Quotation list "total hours" = sum of work-package hours
**Category:** Calculation
**Priority:** P2
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/infrastructure/presales/quote/QuotationQueryImpl.java:94-118`
**Plain English:** Each row in the quotation list shows the total hours across all of that quotation's work packages.
**Specification:**
  Given a quotation with work packages of 40 h, 24 h and a null-hours package
  When  the list page loads
  Then  totalHours = 64
**Parameters:** none
**Edge cases handled:** Null hours count as 0. A quotation with no work packages shows 0.
**Confidence:** High.

### RULE-154: Quotation stats card: counts by status and approved amount, limited to what the viewer can see
**Category:** Calculation
**Priority:** P2
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/infrastructure/presales/quote/QuotationQueryImpl.java:130-144`
**Plain English:** The stats card shows the number of draft, pending and approved quotations, plus the sum of final amounts of approved quotations. It only covers quotations where the viewer is the applicant or is listed in the visible team.
**Specification:**
  Given the viewer can see approved quotations of ¥106,000 and ¥50,000 and one approved quotation with a NULL amount
  When  the stats load
  Then  approvedAmount = ¥156,000
**Parameters:** none
**Edge cases handled:** Null amounts are skipped. The visible-team match uses the quoted id, so 1 does not match 12.
**Confidence:** High.

### RULE-155: During approval clauses are locked, so the tail adjustment is effectively frozen, and BU cost must be > 0 when required
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/presales/quote/ApplyQuotationApprovalUseCase.java:65-101`
**Plain English:** An approver may change deliverables and work packages but not clauses: same count, same codes, same titles, types and amounts. After adoption the clause sum must still equal the final quote. At a node marked cost-required, BU cost must be > 0.
**Specification:**
  Given a pending quotation with final ¥106,000 split into clauses ¥31,800 + ¥74,200
  When  the approver adopts tail adjustment −¥1,000 (final becomes ¥105,000)
  Then  it is rejected as SUBMIT_CLAUSE_TOTAL_MISMATCH, because the clauses cannot be changed to match
  And   at a cost-required node with a tree whose labour is ¥0 and no other costs, it is rejected as QUOTATION_APPROVAL_COST_REQUIRED
**Parameters:** costRequired flag is set by the approval form handler (outside the listed paths)
**Edge cases handled:** Clauses are matched by code, so their order does not matter. An incoming clause without a code counts as a new clause and is rejected.
**Suspected defect:** Tail adjustment and tax/price changes are offered to approvers as adoptable fields, but whenever clauses exist any change to the final quote is guaranteed to fail.
**Confidence:** High for the code. SME question: "Should approvers be able to adjust the final price during approval, and if so, how should the clause split be rebalanced?"
**Also stated by:** presales-validation-lifecycle — “Approvers can adopt header fields only while PENDING, with required-field and cost checks” (`psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/presales/quote/ApplyQuotationApprovalUseCase.java:34-110`)

### RULE-156: Contract clauses are locked during approval
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

### RULE-157: Server master data is the only source of position rates; strict check at submit
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

### RULE-158: Conditions for submitting a quotation for approval
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

### RULE-159: Clause amounts must each be greater than 0 and add up exactly to the final quote
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/presales/quote/quotation/Quotation.java:334-356`
**Plain English:** The payment clauses (progress and prepayment) are a complete split of the final quote. Every clause amount must be positive, the sum must equal finalAmount to the cent, and at submit at least one clause must exist.
**Specification:**
  Given finalAmount ¥106,000.00 and clauses ¥31,800.00 (PREPAY) + ¥74,200.00 (PROGRESS)
  When  submitted
  Then  it passes
  And   with clauses ¥31,800 + ¥74,000 (sum ¥105,800) it is rejected as SUBMIT_CLAUSE_TOTAL_MISMATCH
  And   with no clauses at all, submit is rejected as SUBMIT_CLAUSE_REQUIRED
**Parameters:** exact BigDecimal equality via compareTo (no tolerance)
**Edge cases handled:** The approval-adoption path passes requireClause=false, so legacy in-flight quotations with no clauses get through. The equality check runs only when at least one clause exists and finalAmount is not null.
**Suspected defect:** The comment at `SubmitQuotationUseCase.java:60` says "合计 ≤ 最终报价", but the code requires equality. The front end warns only when the total exceeds the final quote (+0.005 tolerance), so an under-allocated split gives no warning before the hard rejection at submit.
**Confidence:** High.
**Also stated by:** presales-validation-lifecycle — “Clause amounts must add up to the final quoted price” (`psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/presales/quote/quotation/Quotation.java:334-356`)

### RULE-160: Approval-time adoption recalculates final amount and margin; quotation must stay PENDING
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

### RULE-161: Server-side checks on monetary inputs
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
**Also stated by:** presales-calculation — “All cost and price inputs must be non-negative and the cost tree has at most 3 levels” (`psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/presales/quote/quotation/Quotation.java:470-518`)

### RULE-162: Starting a quotation from an opportunity
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

### RULE-163: A margin below 50% requires a written low-margin reason (BU submitter or cost-adopting approver only)
**Category:** Validation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/presales/quote/quotation/Quotation.java:281-298`
**Plain English:** If the calculated gross margin is below 50%, a low-margin reason must be filled in before submission or approval adoption. The error message reports the margin as a percentage with 1 decimal.
**Specification:**
  Given grossMargin 0.4000 (exTax ¥100,000, buCost ¥60,000) and an empty lowGmReason
  When  a user with cost-edit permission submits
  Then  submission is rejected with the parameter "40.0%"
  And   at grossMargin 0.5000 no reason is required
**Parameters:** LOW_GROSS_MARGIN_THRESHOLD = 0.50 (front-end constant `LOW_GM_THRESHOLD` mirrors it)
**Edge cases handled:** A null margin is skipped. A whitespace-only reason counts as empty. The check applies only when the submitter can edit cost (`SubmitQuotationUseCase.java:69-71`) or when the approval step adopted the cost tree or other costs (`ApplyQuotationApprovalUseCase.java:89-91`). A sales submission skips it.
**Suspected defect:** The reason is never cleared automatically when the margin recovers. The repository comment says it "must be cleared together" (`QuotationRepositoryImpl.java:156`), but the code just saves whatever the client sends.
**Confidence:** High for the logic. SME question: "Is 50% the intended threshold, and should it be configurable or vary by business category?"

### RULE-164: Seal registration must be complete when "需要用印" is ticked
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

### RULE-165: Cost template name is required; templates are numbered and active by default
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

### RULE-166: Approve/reject decisions are idempotent and ignore stale messages
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

### RULE-167: Submission order: refresh costs, check all gates, compare-and-set, then start OPP_QUOTATION as one transaction
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

### RULE-168: Only DRAFT or REJECTED quotations can be edited, submitted or deleted
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

### RULE-169: How BPM final results map to quotation outcomes
**Category:** Lifecycle
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/mq/consumer/approval/quotation/QuotationProcessResultHandler.java:34-52`
**Plain English:** When the quotation workflow ends, "approved" marks the quotation APPROVED and "not approved" marks it REJECTED with the approver's reason. A cancellation or withdrawal by the initiator sends the quotation back to DRAFT and records no rejection reason. The result is carried as an enumeration, not a number. Any other result changes nothing. Consistent with RULE-279 and RULE-288.
**Specification:**
  Given a PENDING quotation and the result APPROVED
  Then the quotation becomes APPROVED
  Given the result REJECTED
  Then the quotation becomes REJECTED and the reason is stored
  Given the result CANCELLED (initiator withdraws)
  Then the quotation returns to DRAFT and no rejection reason is written
  Given the result IN_PROGRESS
  Then nothing is written back
**Parameters:** business key `OPP_QUOTATION`. A message with a null document id is ignored.
**Legacy behaviour:** CANCELLED (4) was stored as REJECTED with the label "OA 驳回" and reason "审批未通过", so a withdrawal looked like a rejection.
**Corrected:** 2026-10-21 per the verdict "wrong" of 2026-10-14 (RULE_REVIEWS.json); the legacy behaviour is kept only as evidence.
**Confidence:** High.

### RULE-170: A quotation returned to the initiator stays PENDING but can be edited and resubmitted
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

### RULE-171: Deletion is a guarded soft delete that cascades to child tables
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

### RULE-172: Only the applicant can save, submit or delete a quotation
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

### RULE-173: Users without cost-edit permission cannot change cost inputs; work-package cost fields are kept from the database
**Category:** Policy
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/presales/quote/SaveQuotationUseCase.java:51-164`
**Plain English:** Sales users may add or remove clauses, change clause amounts, and restructure deliverables and work packages. Existing work packages keep their stored role, hours and rates, matched by id. New work packages under existing clauses get no cost fields. Other costs are ignored. Brand-new clauses, including those created from a template, are taken as sent.
**Specification:**
  Given a stored work package id 77 with role pd and 40 h
  When  a sales user saves it with 10 h
  Then  it is saved with 40 h and is then re-priced at current rates
  And   the 8 other-cost categories keep their stored values
**Parameters:** permission `fst:quotation:cost-edit`
**Edge cases handled:** A non-empty payload with no clauses falls back to the whole stored tree. An empty array means "delete all clauses". City changes by sales are accepted.
**Suspected defect:** New clauses (those without a code) carry the hours and roles sent by the client, so sales can set labour hours by deleting a clause and re-adding it.
**Confidence:** Medium — this is inferred from the merge structure. SME question: "Should sales-created clause branches (template or manual) be allowed to carry hours, or should hours always be left blank for BU?"
**Also stated by:** presales-validation-lifecycle — “Sales users without cost-edit permission cannot change BU cost fields” (`psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/presales/quote/SaveQuotationUseCase.java:51-164`)

### RULE-174: Values copied onto the draft at creation and frozen
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

### RULE-175: A low-margin reason is required when estimated gross margin is below 50%
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

### RULE-176: Who sees which quotations in lists and stats
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

### RULE-177: Clause codes are permanent and never reused
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

### RULE-178: Quotation numbering
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

### RULE-179: Approved quotations count toward the "approved amount" stat
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

### RULE-180: The opportunity-expense CRM status sync needs explicit period and company targets
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


## 交付、预算与收入

### RULE-181: WBS budget cost is task hours × role cost rate, summed up the tree, plus non-labour cost categories
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

### RULE-182: Budget gross margin uses ex-tax contract amount at the source quotation's tax rate (default 6%)
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
**Also stated by:** delivery-validation — “Budget gross margin uses net-of-tax revenue and a valid tax rate” (`psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/ProjectBudget.java:775-792`)

### RULE-183: Task ETC, EAC and POC
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

### RULE-184: A clause's revenue is capped at the clause amount; pending requests reserve quota
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

### RULE-185: Revenue confirmation needs a positive amount and complete evidence; the period is the sign-date month
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
**Also stated by:** delivery-lifecycle — “A revenue confirmation needs a positive amount, a full evidence set and quota headroom; the revenue period comes from the sign date” (`psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/receivables/revenue/RevenueConfirmation.java:64-125`)

### RULE-186: Cost view: forecast cost, savings, cost POC and budget POC
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

### RULE-187: Execution cost uses the assignee's personal rate; budget cost uses the role rate (frozen first)
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

### RULE-188: The progress-page header's "budget cost" is the approved baseline labour cost, not the execution sum
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

### RULE-189: Hours roll-up and POC per deliverable, clause and project; overrun flag
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

### RULE-190: Project "confirmed revenue" counts only output rows that match one of the project's clauses
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/infrastructure/delivery/project/ProjectQueryImpl.java:272-300`
**Plain English:** Confirmed revenue = sum of output-value rows (type 0 or null) whose term number equals one of the project's clause keys. The clause key is the OA term_code, falling back to the legacy-system clause code.
**Specification:**
  Given clause term_code "HT-01-03" and rows of ¥200,000 (HT-01-03), ¥50,000 (no term number) and ¥30,000 ("HT-OLD")
  When the project list loads
  Then confirmed revenue = ¥200,000
**Parameters:** Type filter = 0 or NULL. Term numbers are trimmed.
**Edge cases handled:** Orphan OA rows are excluded so the list matches the dashboard. List stats compute pending = max(0, Σ project amount − confirmed) (`ProjectQueryImpl.java:221-223`).
**Confidence:** High.

### RULE-191: Project list labour savings % and overrun flag
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

### RULE-192: Legacy term labour EAC = incurred at forecast + remaining forecast days × budget daily wage, floored at actual
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

### RULE-193: Legacy per-term earned value: progress, accrued revenue, EV, CV and VAC
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

### RULE-194: Legacy project progress, cost waste and cost performance index
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

### RULE-195: Legacy margins divide by a fixed 1.06 for tax
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

### RULE-196: Budget-risk traffic light from the cost-waste rate
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

### RULE-197: Project WIP = sum of carried-forward rows posted to cost but not yet written off
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

### RULE-198: A daily job writes the progress-tree POC into project progress for locked projects
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

### RULE-199: Invoiceable amount per term = max(0, confirmed output − invoiced)
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

### RULE-200: A revision cannot be submitted if the archive clauses changed after the revision started
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
**Also stated by:** delivery-validation — “A revised budget is rejected if the contract-term archive changed during editing” (`psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/delivery/project/LockProjectBudgetUseCase.java:120-147`)

### RULE-201: Execution-side structure is frozen during budget approval, and planned hours are frozen after lock
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/delivery/project/OperateProgressTaskUseCase.java:244-295`
**Plain English:** On the progress page, task packages and deliverables cannot be created, edited or deleted while the budget is PENDING. Once the budget is locked, a task package's planned hours cannot change and new task packages must have 0 planned hours. Name, role, assignee and dates can still change.
**Specification:**
  Given budget locked and TP1 with plannedHours 40
  When the PM updates TP1 to 48h
  Then it is rejected with PROGRESS_PLANNED_HOURS_LOCKED
  When the PM creates a task with 10h
  Then it is rejected
  When the PM creates a task with 0h
  Then it is allowed
**Parameters:** null hours count as 0 for comparison.
**Edge cases handled:** Status changes (done, reopen, rework, ETC) are not subject to these checks. A deliverable can still be added during execution after lock, with no amount (lines 304-320).
**Confidence:** High.

### RULE-202: Only the project's PM can change execution-side structure
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/delivery/project/OperateProgressTaskUseCase.java:504-510`
**Plain English:** Updating or creating task packages, creating or deleting deliverables, and rework by a new deliverable require the logged-in user to be the project's manager. A missing PM or missing project counts as a failure (fail-closed).
**Specification:**
  Given project P with managerId 2001
  When user 3005 calls `/progress/task/update` for P
  Then it is rejected with PROGRESS_TASK_NOT_PROJECT_MANAGER
**Parameters:** None.
**Edge cases handled:** Self-create (member) and self-done (assignee) use different checks.
**Suspected defect:** `delete()` (lines 440-453) does not call `requireProjectManager`. Any holder of `fst:pm-project:progress` can delete another project's task packages, as long as no hours are logged. Also, `/task/update`, `/task/create` and `/deliverable/create` have no `@PreAuthorize` at all (PmProjectProgressController:288-315), so this ownership check is their only guard.
**Confidence:** High. SME question: "Should task deletion require the project PM like the other structural writes?"

### RULE-203: Only one active submission per deliverable
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/delivery/project/SubmitDeliverableUseCase.java:54-84`
**Plain English:** Only one active submission is allowed per deliverable. A submission is active while it is in internal review, PMO review, awaiting acceptance, accepted or converted. A rejected (DRAFT) one is not active, so a new submission is allowed. If the active one was returned to the initiator, only the original submitter can resubmit it.
**Specification:**
  Given D has submission S1 in DLV_ACCEPTING
  When the PM submits again
  Then it is rejected with DELIVERABLE_SUBMISSION_EXISTS
  Given S1 is in DLV_INTERNAL and returned to initiator 2001
  When user 2002 submits
  Then it is rejected
**Parameters:** Active statuses: DLV_INTERNAL, DLV_EVAL, DLV_ACCEPTING, DLV_ACCEPTED, DLV_CONVERTED.
**Edge cases handled:** A conditional insert guards against concurrent duplicates. While a submission is active, task packages under D cannot be created or deleted (OperateProgressTaskUseCase:528-532).
**Confidence:** High.

### RULE-204: At final approval the quota is re-checked; an over-cap request is auto-rejected
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

### RULE-205: Revenue confirmation submit gates — clause exists, clause not obsolete, contract signed back
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
**Also stated by:** delivery-validation — “Revenue confirmation needs a valid clause and a returned contract” (`psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/receivables/revenue/SubmitRevenueConfirmationUseCase.java:44-66`)

### RULE-206: Once hours are logged, the assignee is fixed and the task cannot be deleted
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/DeliverableProgress.java:292-315`
**Plain English:** A task package counts as having hours once it has actual hours above 0 or any timesheet line that is not rejected or cancelled (including pending). After that its assignee cannot change and it cannot be deleted (`removeTask`, lines 386-395). Planned and actual date ranges must have start ≤ end.
**Specification:**
  Given TP1 assigned to 4001, actual 0h, and one timesheet line pending approval
  When the PM reassigns it to 4002
  Then it is rejected with TASK_ASSIGNEE_LOCKED
  Given planStart 2026-11-10 and planEnd 2026-11-01
  Then it is rejected with TASK_SCHEDULE_INVALID
**Parameters:** Hours threshold `> 0`.
**Edge cases handled:** The Excel import path keeps the old assignee silently instead of failing (lines 216-224). A single-sided date is allowed.
**Confidence:** High.

### RULE-207: Submitting a deliverable requires all its tasks to be finished, then locks them
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/DeliverableProgress.java:403-423`
**Plain English:** A deliverable can be submitted only if it is not obsolete, has at least one task package, and every task package is DONE or LOCKED. DONE packages then become LOCKED, so no more hours can be logged on them.
**Specification:**
  Given deliverable D with TP1 DONE and TP2 DOING
  When the PM submits D
  Then it is rejected with TASK_NOT_ALL_DONE
  Given TP1 and TP2 are DONE
  Then both become LOCKED
**Parameters:** None.
**Edge cases handled:** A deliverable with no task packages is rejected with NO_TASK_PACKAGE. An obsolete deliverable is rejected with NODE_OBSOLETE.
**Confidence:** High.

### RULE-208: Rules for registering customer acceptance
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/DeliverableSubmission.java:619-655`
**Plain English:** Acceptance can be registered only on an approved submission (DLV_ACCEPTING). It needs a customer sign date no later than today, plus both an acceptance document and an email confirmation. After that it is fixed (DLV_ACCEPTED).
**Specification:**
  Given S1 DLV_ACCEPTING, today 2026-10-14, sign date 2026-10-15
  When acceptance is registered
  Then it is rejected with ACCEPTANCE_SIGN_DATE_INVALID
  Given sign date 2026-10-12, 1 document and 0 emails
  Then it is rejected with ACCEPTANCE_ATTACHMENT_REQUIRED
**Parameters:** None.
**Edge cases handled:** The use case checks that the submission belongs to the project (RegisterAcceptanceUseCase:32-36).
**Confidence:** High.

### RULE-209: Every task package must be complete before a budget can be submitted
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/ProjectBudget.java:326-377`
**Plain English:** Saving the budget means submitting it for approval. That is allowed only when every task package has a role, every non-obsolete task package has a name, and every non-obsolete task package with planned hours above 0 has a matching role cost rate.
**Specification:**
  Given an editable budget with task package TP1 (role "ENG_SR", 80h, no rate found for that role and city)
  When the PM submits the budget
  Then it is rejected with TASK_ROLE_RATE_MISSING. A missing rate would count as cost 0 and inflate the margin.
  Given TP1 has 0h and no rate
  Then it passes (a missing rate does not change the amount).
**Parameters:** Hours threshold `> 0`. A role is required (blank or whitespace counts as missing).
**Edge cases handled:** Obsolete task packages skip the name and rate checks, but the role check still applies to them. Assignees are not required (assignment happens at execution time).
**Suspected defect:** The class comment says the role "must be in the static rate-table domain", but `validateTask` (lines 802-808) only checks that hours are not negative. The role value domain is never checked on the server. The `TASK_ROLE_INVALID` reason is unused in this aggregate.
**Confidence:** High.
**Also stated by:** delivery-calculation — “A budget cannot be submitted while a task with hours lacks a role, a name or a cost rate” (`psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/ProjectBudget.java:326-377`)

### RULE-210: Budget can only be edited while it is unlocked and not awaiting approval
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/ProjectBudget.java:438-446`
**Plain English:** All budget changes are blocked once the budget is locked (approved) or while it is in PM_BUDGET approval. The one exception is a budget whose approval was sent back to the person who submitted it. This covers importing clauses, adding or renaming deliverables, changing task packages, editing normal cost items and submitting.
**Specification:**
  Given project P's budget has `locked=false` and `approvalStatus=PENDING`, and the workflow is not parked at the initiator node
  When the PM adds a task package "Structural calc" (role R1, 40h)
  Then the request is rejected with APPROVAL_PENDING, "项目预算审批中，不允许该操作"
  Given the same budget whose workflow was returned to the initiator
  When the PM edits the task package
  Then the edit is allowed. If `locked=true`, the edit is rejected with LOCKED whether or not it was returned.
**Parameters:** `APPROVAL_PENDING="PENDING"`, `APPROVAL_REJECTED="REJECTED"`. The returned-to-initiator flag is held in memory only and resets on every load.
**Edge cases handled:** A rejected budget (REJECTED) can be edited again. The returned flag never overrides a lock. Sensitive cost items use a looser check (locked only; see RULE-07).
**Suspected defect:** The budget write endpoints (`PmProjectBudgetController` `/import`, `/task/*`, `/lock`, `/deliverable*`, `/cost-items`) check only the permission `fst:pm-project:budget` and turn off `@DataPermission`. No use case checks that the caller is that project's PM. Anyone with the permission can edit or submit any project's budget. The progress-side writes do check this (see RULE-13).
**Confidence:** High for the lock check. Medium for the ownership gap. SME question: "Should budget editing be limited to the project's own PM or delivery manager, or can anyone holding `fst:pm-project:budget` edit any project?"

### RULE-211: Normal cost items are edited during budgeting; sensitive items need a permission
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/ProjectBudget.java:579-626`
**Plain English:** Travel and printing can be entered only while the budget is editable. The six sensitive items (5 outsourcing types plus info-service fee) can be entered whenever the budget is not locked, including during approval, but only by users with `fst:pm-project:budget-cost-view`. Approval-node adoption accepts only sensitive items and only while PENDING. Negative amounts are rejected and null counts as 0.
**Specification:**
  Given budget PENDING, and user U holds budget-cost-view
  When U sets `outsourceSafety=¥50,000`
  Then it is saved
  When U sets `travel=¥8,000` in the same state
  Then it is rejected with APPROVAL_PENDING
  When a user without the permission submits `outsourceLight`
  Then it is rejected with BUDGET_SENSITIVE_COST_PERMISSION_DENIED (UpdateBudgetCostItemsUseCase:56-58)
**Parameters:** EDITABLE = {travel, print}. SENSITIVE = {outsourceSafety, outsourceLight, outsourceWindow, outsourceSign, outsourceOther, infoService}. Permission `fst:pm-project:budget-cost-view`.
**Edge cases handled:** Unknown categories are rejected. If the permission RPC fails, the user is treated as having no permission. Updates only change the categories sent (upsert subset).
**Confidence:** High.

### RULE-212: Rules for an approver changing the budget tree during approval
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/ProjectBudget.java:639-723`
**Plain English:** An approver may add, rename or delete deliverables and task packages only while the budget is PENDING. The submitted list is the complete list, so anything missing from it is deleted. Obsolete task packages cannot be deleted or edited. Every new or updated task package needs a name. After the changes, every task package must still have a role.
**Specification:**
  Given budget PENDING and obsolete task TP9 (with logged hours)
  When the approver submits a change list without TP9
  Then it is rejected with NODE_OBSOLETE
  Given a new task package with role "ENG" and a blank name
  Then it is rejected with TASK_NAME_REQUIRED, "不可通过"
**Parameters:** Negative `deliverableId` refers to a temporary deliverable created in the same batch.
**Edge cases handled:** Hours must not be negative. A target deliverable that is not in the tree is rejected.
**Suspected defect:** This path does not run the RULE-02 rate-missing check. An approver can add a task package with hours but no matching rate, and it is locked into the baseline at cost 0.
**Confidence:** High.

### RULE-213: Total confirmed revenue cannot exceed the clause amount
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/receivables/revenue/RevenueConfirmation.java:64-87`
**Plain English:** The amount must be above 0. The customer evidence set must be complete: sign date, at least one acceptance document and at least one confirmation email. The amount must be no more than the clause amount minus confirmed amounts minus pending amounts. The revenue period is the month of the customer sign date, and any period the client sends is ignored.
**Specification:**
  Given clause ¥300,000, confirmed ¥100,000, pending ¥150,000, so remaining ¥50,000
  When ¥60,000 is submitted
  Then it is rejected with QUOTA_EXCEEDED, "剩余 50000"
  When ¥50,000 is submitted with sign date 2026-09-28
  Then it is accepted as PENDING with period "2026-09"
**Parameters:** Remaining = max(0, clauseAmount − confirmed − pending). A null clause amount counts as 0.
**Edge cases handled:** On resubmission, the item's own pending amount is excluded from the quota.
**Suspected defect:** Unlike deliverable acceptance (RULE-21), the sign date is not checked against today. A future date is accepted and gives a future revenue period.
**Confidence:** High.

### RULE-214: Team members can mark only their own task packages done
**Category:** Validation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/delivery/project/OperateProgressTaskUseCase.java:63-77`
**Plain English:** The self-service "done" endpoint needs no progress permission, but the task package must be assigned to the logged-in user.
**Specification:**
  Given TP1 assigned to user 4001
  When user 4002 calls `/task/done/my`
  Then it is rejected with PROGRESS_TASK_DONE_NOT_ASSIGNEE
**Parameters:** None.
**Edge cases handled:** The same state-machine checks as RULE-15 apply.
**Confidence:** High.

### RULE-215: Rework path depends on the deliverable's submission status
**Category:** Validation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/delivery/project/OperateProgressTaskUseCase.java:85-109`
**Plain English:** In-place rework of a LOCKED task is allowed only if the deliverable has no active submission. If the submission is in review, rework is refused until the workflow ends. If it is already approved, rework must go through a new rework deliverable. That path (lines 198-242) requires the PM, no pending budget approval, a reason, at least one selected task, and all earlier rework deliverables approved. Copied tasks start at 0 planned hours.
**Specification:**
  Given D's submission is DLV_ACCEPTED
  When the PM reworks TP1 in place
  Then it is rejected with DELIVERABLE_REWORK_APPROVED_USE_NEW_DELIVERABLE
  Given D's submission is DLV_EVAL
  Then it is rejected with DELIVERABLE_REWORK_LOCKED_BY_SUBMISSION
**Parameters:** Approved statuses: DLV_ACCEPTING, DLV_ACCEPTED, DLV_CONVERTED.
**Edge cases handled:** A rejected (DRAFT) submission allows in-place rework.
**Confidence:** High.

### RULE-216: Obsolete nodes must be acknowledged before a revision can be submitted
**Category:** Validation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/BudgetRevision.java:96-117`
**Plain English:** If a revision made any clause, deliverable or task package obsolete, the submitter must explicitly confirm the obsolete list. Their identity is recorded. A revision can be submitted only from IN_PROGRESS or REJECTED.
**Specification:**
  Given revision #2 IN_PROGRESS with `obsoleteNodeCount=5` (actual cost ¥12,400)
  When it is submitted with `obsoleteConfirmed=false`
  Then it is rejected with REVISION_OBSOLETE_UNCONFIRMED
  When it is submitted with `true` by user 1001
  Then status becomes PENDING and `obsoleteConfirmedBy="1001"`
**Parameters:** The change reason must be in `BudgetRevisionReasonEnum`. "OTHER" requires a note (lines 54-66).
**Edge cases handled:** With 0 obsolete nodes no confirmation is needed. A PENDING or EFFECTIVE revision cannot be resubmitted.
**Confidence:** High.

### RULE-217: Required submission inputs depend on the phase
**Category:** Validation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/DeliverableSubmission.java:220-253`
**Plain English:** Every submission needs a title and a phase of INITIAL or DEEPEN. INITIAL (stage review) needs a meeting time and attachments are optional. DEEPEN (internal review) needs at least one review file, the requirement-review date, the drawing category and whether construction-drawing review is included. Fields that belong to the other phase are cleared.
**Specification:**
  Given phase DEEPEN with 1 file, date 2026-10-01, category "A", and constructionDrawingReview not set
  When the PM submits
  Then it is rejected with CONSTRUCTION_DRAWING_REVIEW_REQUIRED
  Given phase INITIAL with no meeting time
  Then it is rejected with MEETING_TIME_REQUIRED
**Parameters:** Review scores elsewhere in the class must be integers in 0–100 (`REVIEW_SCORE_MIN=0`, `REVIEW_SCORE_MAX=100`).
**Edge cases handled:** Attachments missing a name or URL are dropped. Resubmission reruns the same checks and clears all earlier review outcomes.
**Confidence:** High.

### RULE-218: A budget change can only start from a locked budget
**Category:** Validation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/ProjectBudget.java:454-464`
**Plain English:** A budget change (revision) can be started only after the budget has been approved and locked. Starting one unlocks the budget and clears its approval state.
**Specification:**
  Given budget `locked=false`, `approvalStatus=PENDING`
  When the PM starts a revision
  Then it is rejected with REVISION_NOT_ALLOWED, "请等待审批终态后再发起变更"
  Given `locked=false` and not pending
  Then it is rejected with "尚未锁定，可直接编辑无需发起变更"
  Given `locked=true`
  Then the budget is unlocked, `approvalStatus=null`, `processInstanceId=null`
**Parameters:** None.
**Edge cases handled:** The use case also blocks the revision when the project archive has no contract terms (`BUDGET_ARCHIVE_TERMS_MISSING`, StartBudgetRevisionUseCase:61-64).
**Confidence:** High.

### RULE-219: On budget approval, role rates are frozen and cost snapshots are written
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
**Also stated by:** delivery-lifecycle — “The PM_BUDGET outcome is a three-way split, guarded by idempotency and the process instance; approval locks the budget, freezes rates and snapshots cost” (`psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/delivery/project/DecideBudgetApprovalUseCase.java:46-86`)

### RULE-220: ETC approval outcome — approved promotes the pending value; rejected or cancelled unfreezes and keeps the old value
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

### RULE-221: A budget revision can only start from a locked budget; it snapshots, unlocks and re-baselines the clause layer
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

### RULE-222: Approved revenue writes an append-only revenue fact, after a second quota check
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
**Suspected defect:** When the auto-reject happens, BPM shows the instance as approved while the legacy system shows it rejected. No notification or BPM correction is visible in the code.
**Confidence:** High.

### RULE-223: Task package execution states — DOING/REWORK → DONE, DONE → DOING (reopen), LOCKED → REWORK
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
**Also stated by:** delivery-validation — “Task package state transitions and their guards” (`psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/DeliverableProgress.java:47-84`)

### RULE-224: Submitting a deliverable requires all its task packages to be done, and locks them
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

### RULE-225: Deliverable approval outcome — approved → awaiting customer acceptance; rejected or cancelled → DRAFT
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

### RULE-226: Deliverable submission states, and which states hold the deliverable's single "submission slot"
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

### RULE-227: A budget can only be locked through an approved PM_BUDGET approval, and only when it is complete
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

### RULE-228: Budget editing is blocked while the budget is locked or awaiting approval
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

### RULE-229: On revision, clauses are re-matched to the archive; vanished clauses with history become obsolete, others are removed
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

### RULE-230: How rework is handled depends on the submission state
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

### RULE-231: Execution-phase structure changes freeze during budget approval; planned hours freeze after lock; a submission locks the package set
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

### RULE-232: Revision submit requires the obsolete list to be acknowledged; the revision status follows the budget outcome
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

### RULE-233: Resubmitting after a return to the initiator resets the review and wipes earlier conclusions
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

### RULE-234: First-level review pass moves deepen-phase submissions to PMO review; initial-phase submissions only record the trace
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

### RULE-235: Deepen-phase PMO review — a one-time pass ends the review; otherwise a director revision and second PMO round follow
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

### RULE-236: Customer acceptance can be registered once, only from ACCEPTING, with a sign date and two kinds of proof
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

### RULE-237: Approvers may change the cost tree only while the budget is PENDING; the adopted list is the full set
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

### RULE-238: Works stage: Design until the 深化 (deepening) stage output is fully confirmed, then Closing (one-way)
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

### RULE-239: Budget margin vs quotation margin decides whether CEO approval is needed
**Category:** Policy
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/delivery/project/ApplyBudgetApprovalUseCase.java:100-113`
**Plain English:** The budget gross margin is compared with the source quotation's gross margin. If the budget margin is at least as high, `budgetGmNotBelowQuote="true"` and the CEO step is skipped. If it is lower, or either margin is missing, the budget goes to CEO final approval. The comparison runs at submission (BudgetApprovalStarterImpl:58-60) and again after every approver change.
**Specification:**
  Given contract ¥1,060,000, quote tax rate 6%, budget cost ¥700,000, so budget GM = (1,000,000.00 − 700,000)/1,000,000.00 = 0.3000, and quote GM 0.3200
  When submitted or adopted
  Then `notBelow=false` and the budget is routed to the CEO
  Given quote GM 0.2800
  Then `notBelow=true` and the CEO is skipped
**Parameters:** Comparison `>=`. Process key `PM_BUDGET`.
**Edge cases handled:** No source quotation, contract amount ≤ 0 or no clauses all make the margin null, which routes to the CEO.
**Confidence:** High.

### RULE-240: The quota is checked again at final approval, and an over-quota approval is turned into a rejection
**Category:** Policy
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/receivables/revenue/DecideRevenueConfirmationUseCase.java:44-61`
**Plain English:** When a confirmation is finally approved, the quota is recalculated without this confirmation. If its amount no longer fits (because of concurrent approvals or a reduced clause amount), the system rejects it instead and no revenue fact is written. Only a real approval writes the append-only revenue fact, in the same transaction.
**Specification:**
  Given RC-2 for ¥50,000 pending, while the clause was cut to ¥120,000 and ¥100,000 is already confirmed (remaining excluding self is ¥20,000)
  When BPM approves RC-2
  Then status becomes REJECTED with note "审批通过时累计确认金额已超条款金额，系统自动驳回…" and no psa_revenue_fact row is written
**Parameters:** None.
**Edge cases handled:** A message for a confirmation not in PENDING does nothing (redelivery safe). A missing confirmation is only logged.
**Confidence:** High.

### RULE-241: An ETC increase needs PM_ETC approval only if it pushes forecast labour cost over budget labour cost
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
**Also stated by:** delivery-validation — “An ETC increase needs approval only when it pushes projected labour cost over budget” (`psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/DeliverableProgress.java:110-136`)

### RULE-242: Raising ETC needs PM_ETC approval only if it pushes project forecast labour cost above budget labour cost
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

### RULE-243: Archive reconciliation keeps clauses that have data and removes those that do not
**Category:** Policy
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/ProjectBudget.java:477-538`
**Plain English:** During a revision, clauses are matched to the archive by term code. New terms create clauses. Matched clauses update title, type and amount only. Clauses gone from the archive (including legacy clauses with no term code) are marked obsolete if they have incurred data (hours, deliverables, revenue), otherwise removed. Obsolete nodes become read-only everywhere.
**Specification:**
  Given clauses C1 (term T1, has timesheet hours) and C2 (term T2, no data), and an archive that now contains only T1 and T3
  When the revision starts
  Then C1 is kept with updated attributes, C2 is soft-deleted, and a new clause is created for T3 with code `{projectCode}-C{max used+1}`
  Given T1 is also removed from the archive
  Then C1 is marked obsolete and any later edit is rejected with NODE_OBSOLETE
**Parameters:** The code sequence continues from all used codes, including soft-deleted ones (never reused).
**Edge cases handled:** The PM's existing deliverables and task packages under matched clauses are never touched. Obsolete status cannot be undone.
**Confidence:** High.

### RULE-244: Budget approval routing — the CEO step is skipped only if budget gross margin ≥ quote gross margin
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

### RULE-245: Which role cost rate applies: project city, then quotation city, then unfiltered; latest active scheme wins
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

### RULE-246: Which quotation counts as the budget baseline
**Category:** Policy
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/delivery/project/BudgetSourceQuotationFinder.java:26-44`
**Plain English:** The baseline quotation (the source for the deliverable and task template, margin and tax rate) is the newest quotation on the opportunity that has a contract code (the winning bid). If none has one, it is the newest APPROVED quotation. Otherwise there is none.
**Specification:**
  Given opportunity O with Q3 (newest, APPROVED, no contract code) and Q2 (older, contract code "HT-001")
  When the baseline is resolved
  Then Q2 is used
**Parameters:** Status `QuotationStatus.APPROVED`.
**Edge cases handled:** A null opportunity returns null. Quotations whose clause code does not match any archive term are reported as unmatched and not imported (InitializeProjectBudgetUseCase:73-77).
**Confidence:** High.

### RULE-247: Reconciliation job replays lost ETC outcomes and releases orphaned freezes
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


## 审批（platform.approval）

### RULE-248: Countersign/or-sign nodes create one task per distinct approver
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

### RULE-249: A single-approver node picks one candidate at random
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

### RULE-250: Initiator-leader scripts pick the 1st or 2nd reporting-line leader
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

### RULE-251: Row reachability: a row reaches a node if its routing value activates a path to it
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

### RULE-252: Per-row approver resolution
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

### RULE-253: Approvers are resolved from the node's assignment-rule type
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

### RULE-254: "Custom data" approvers come from form fields, with a hard-coded fallback person
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

### RULE-255: Regional AR script (30) resolves dept id to `dept.ar_user_id`
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

### RULE-256: Department-director script (32) means holders of role BU-002 who sit directly in the department
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

### RULE-257: Department-leader script (31) resolves dept id to `dept.leader_user_id`
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

### RULE-258: Form-project-manager script (9): the field value is the approver
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

### RULE-259: Form-user-leader script (33): the field value's first-level leader is the approver
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

### RULE-260: Script approvers can be driven by a configurable field, resolved per value and unioned
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

### RULE-261: The to-do list merges row tasks into one item per (instance, node)
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

### RULE-262: Each node must have exactly one assignment rule
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

### RULE-263: A row-approval node requires row units, and the row set is narrowed by routing
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

### RULE-264: Idempotent process start by idempotency key
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

### RULE-265: The initiator of a customized OA process is the caller-supplied `creator`, otherwise the logged-in user
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/oa/CustomizedBpmOAServiceImpl.java:105-110`
**Plain English:** The initiator of a business-document approval is always the logged-in user of the current session. The request carries no `creator` or other identity field; any such field is ignored or refused (ADR-0009 decision 5, G33).
**Specification:**
  Given a request {documentId: 1789…, processKey: "OPP_QUOTATION", creator: 2048} sent by user 1001
  When  the approval is started
  Then  the initiator is user 1001, never 2048, and the initiator's roles, leader and departments are derived from user 1001
  Given a request with no logged-in session
  Then  it is refused with 401 and no process is started
**Parameters:** none.
**Legacy behaviour:** the initiator was the caller-supplied `creator` on an `@PermitAll` endpoint (patched in the old system by SEC-005).
**Scheduled jobs (decided by the user on the inbox page, 2026-10-22):** a scheduled job has no logged-in user and therefore never starts an approval; it only produces a to-do or a reminder, and a person starts the approval from it, so the initiator is always a real person (SECURITY_INVARIANTS I-5).
**Corrected:** 2026-10-21 per the verdict "wrong" of 2026-10-14 (RULE_REVIEWS.json); the legacy behaviour is kept only as evidence.
**Confidence:** High.

### RULE-266: Duplicate-start guard: one live process per business document, reused on resubmission
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

### RULE-267: A resubmission after a return must belong to the current return round
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
**Also stated by:** approval-lifecycle — “"Continue after return" is idempotent and bound to the current round” (`psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/oa/CustomizedBpmOAServiceImpl.java:227-283`)

### RULE-268: Only the initiator may cancel, before any cutoff node, with a reason
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmProcessInstanceServiceImpl.java:239-278`
**Plain English:** Cancelling a running approval is allowed only to the user who started it and only before any node marked "撤回截止" has completed. A reason is always required, with or without a BEFORE hook (user decision 2026-10-16: cancel and withdraw follow the same rule as reject; the old system let a process-level hook decide). After a cancel, the Flowable history is deleted unless the model says to keep it.
**Specification:**
  Given PI-A was started by 2048 and its cutoff node "财务确认" has not completed
  When 3001 cancels it
  Then the request is rejected with PROCESS_INSTANCE_CANCEL_FAIL_NOT_SELF
  When 2048 cancels it with reason "" (with or without a beforeHookKey)
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

### RULE-269: Withdraw to initiator: initiator only, before cutoff, single-instance current node, not already at initiator
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

### RULE-270: Batch approve acts on every user's pending tasks in each assignee's name
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

### RULE-271: A reject inside a subprocess returns to the initiator; a top-level reject terminates the process
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

### RULE-272: Validation for the unified "decide" action
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmTaskServiceImpl.java:863-975`
**Plain English:** One submission can decide several of the approver's own tasks, but only within a single process instance. Each decision must be approve (2) or reject (3). A rejection always needs a reason: a node with a business pre-check hook is not exempt (user decision 2026-10-16, intent 2026-10-16-085331-one-data-审批被驳回-被撤销后能不能再发起.md: the exemption is removed; a hook that wants a reason supplies it). Every business pre-check must pass before any task is completed.
**Specification:**
  Given approver 2002 submits decisions {T1 on node A: approve, T2 on node B: reject with an empty reason}, both in instance P, and node B has no BEFORE hook
  When  decide is called
  Then  it fails with TASK_DECIDE_FAIL_REASON_REQUIRED and nothing changes
  Given the same submission with reason "数量错误"
  Then  the node-A group is completed first and the node-B (reject) group last; for a non-row node exactly 1 decision per node is allowed
**Parameters:** allowed results {2,3}
**Edge cases handled:** a rejection with an empty reason fails on every node, with or without a hook (old system: nodes with a hook were exempt; not carried over). Tasks from different instances are refused (TASK_DECIDE_FAIL_CROSS_INSTANCE). More than one decision for a non-row node is refused (TASK_DECIDE_FAIL_SINGLE_ONLY). If a BEFORE hook asks for interaction, nothing is committed and the hook's dialog is returned. Groups that contain a rejection run last, so the sub-process cancellation cannot delete tasks that are still being approved.
**Confidence:** High.
**Also stated by:** approval-routing — “Unified decide: same instance only, reject reason required, rejecting nodes processed last” (`psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmTaskServiceImpl.java:865-975`); approval-validation — “Unified decide: same instance, result must be pass/fail, a reject needs a reason, one decision per non-row node” (`psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmTaskServiceImpl.java:865-975`)

### RULE-273: A reject from an added countersigner is advisory only
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmTaskServiceImpl.java:982-1040`
**Plain English:** If a person added by countersign votes "fail", their opinion is recorded but the process is not terminated and no result message is sent. Only originally assigned approvers can truly reject. The countersigner's rejection also needs a reason; an empty reason is refused (user decision 2026-10-16, intent 2026-10-16-085331-one-data-审批被驳回-被撤销后能不能再发起.md).
**Specification:**
  Given task T-5 has `bpm_task_ext.countersign=1`
  When its assignee decides 3 with reason "价格偏高"
  Then T-5 is completed with ext result 3 and the reason, the instance continues, and node exit follows the MI completion condition
**Parameters:** Flag `countersign=true` (set in RULE-12).
**Edge cases handled:** "Pass" by a countersigner follows the normal approveTask path, including the result MQ.
**Confidence:** High.

### RULE-274: Row-by-row approval must cover every pending row, and any rejected row rejects the node
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
**Also stated by:** approval-routing — “Row tasks must be decided all at once by their approver” (`psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmTaskServiceImpl.java:1042-1053`); approval-validation — “Row-approval nodes must be decided all at once, covering every pending row” (`psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmTaskServiceImpl.java:1042-1098`)

### RULE-275: Return to initiator requires a reason and passes the node's BEFORE hook
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

### RULE-276: Only the assigned approver may act on a task
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

### RULE-277: BEFORE-hook commit gate
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/task/WorkflowHookBeforeGate.java:16-38`
**Plain English:** A configured business hook can stop an approval action. Only "allow" or "not_applicable" lets it proceed. For cancel/withdraw the action also needs a reason (an explicit action value does not replace it; user decision 2026-10-16); task actions keep BPM's own reason rules, and a rejection always needs a reason (RULE-272).
**Specification:**
  Given a cancel with hook outcome "allow", reason "", and any actionValue
  Then it is not committed
  Given outcome "warn", "reject" or "supplement"
  Then it is never committed
  Given no hook result (null) and reason "改期"
  Then the cancel is committed; for a task action, a null result means not committed
**Parameters:** Committing outcomes: ALLOW, NOT_APPLICABLE.
**Edge cases handled:** Variable updates returned by an allowing task hook are written to the process before completion (BpmApprovalTaskHookService.java:125-130).
**Confidence:** High.

### RULE-278: Approval start rounds: a monotonic round number per (processKey, businessId) defines the idempotency key
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

### RULE-279: Process-result handling is idempotent per instance and status (reference: quotation)
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
- Result mapping: approved → approve; not approved → reject; cancelled by the initiator → back to draft with no rejection reason (RULE-169); others are ignored.
**Edge cases handled:**
- A missing document is ignored.
- ETC (DecideEtcApprovalUseCase.java:59-84) adds a "message arrived before the start transaction committed" check through `isCommittedInstance` and `latestProcessInstanceId`. It throws in order to be redelivered.
- Consumers rethrow every failure (ProcessResultConsumer.java:58-74; ApprovalTaskResultConsumer.java:58-74).
**Suspected defect:**
- Legacy: cancelled (4) was mapped to a quotation reject, which conflated initiator withdrawal with an approver rejection. Corrected 2026-10-21 per the review of 2026-10-14 ("发起人撤销后单据回到草稿，不记为驳回"; RULE-169).
- When `afterHookKey` is present, ApprovalTaskResultConsumer runs only the hook and skips the businessKey handler (lines 46-63). ProcessResultConsumer runs both. The asymmetry is undocumented.
- Idempotency is per handler and by convention only. The interface comment requires it, but nothing enforces it.
**Confidence:** Medium. SME questions: "Should a cancelled approval leave the quotation in REJECTED or return it to DRAFT?" and "Do all other ProcessResultHandlers (budget, revenue, deliverable, travel, timesheet) implement the same status, instance and time checks?"

### RULE-280: Unified approve: ownership gate before any field write, fail-closed field permission, default reasons
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
**Revision (2026-10-14, user: 「留空并如实审计 (Recommended)」):** In FORCITIS one a pass with an empty reason keeps the reason empty and is audited as such; no "同意" is filled in. Rejects and returns still require a reason. See proposals/RULE-280_DEFAULT_REASON.md and RULE_REVIEWS「实现方式变更」.
**Edge cases handled:** Ownership-check failures are fail-closed.
**Suspected defect:**
- `handler.applyApproved` commits on its own before `completeApprovalTask`. The fst gate accepts candidates, but BPM then requires the exact assignee and taskDefinitionKey (BpmProcessTaskApiImpl.java:494-506). An unclaimed candidate's field edits can therefore be committed and then fail completion, leaving changes without an approval record.
- The fst gate does not compare taskDefinitionKey, so permissions for node X could be applied while the request names node Y.
**Confidence:** High.

### RULE-281: Node field permissions: unlisted fields are read-only; configuration is a full overwrite per process key
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

### RULE-282: Batch submission returns the existing instance for any document already mapped
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

### RULE-283: Task BEFORE hooks require a bound business document and a registered hook key
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

### RULE-284: Return to a specific earlier node: single-instance source and target only
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

### RULE-285: Transfer (change assignee) allowed only by the current assignee, to any user
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

### RULE-286: Countersign can only target a running parallel multi-instance node
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

### RULE-287: Resubmission chooses between continuing the same instance, blocking, and starting a new instance
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

### RULE-288: Initiator cancels the whole process
**Category:** Lifecycle
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmProcessInstanceServiceImpl.java:237-356`
**Plain English:** Only the initiator can cancel, and only before the withdraw cutoff. A reason is always mandatory (user decision 2026-10-16), with or without a business pre-check; with a pre-check the hook must also allow it (or not apply). Cancelling marks open tasks as cancelled, ends the instance, and deletes its engine history unless the model says to keep it.
**Specification:**
  Given user 1001 started instance P with no BEFORE hook, and P is before any cutoff
  When  1001 cancels with reason "重复提交"
  Then  active tasks get result=4 with reason "主动取消任务，原因：重复提交", the instance is deleted (the record becomes FINISH/CANCEL via RULE-01), and the Flowable history is deleted unless the process attribute retain-history-on-cancel is "true"
  Given P has a BEFORE hook (for example travel) that returns ALLOW and the user picked a cancelAction and gave a reason
  Then  a process-result outbox row (result=4, cancelAction, reason, afterHookKey) is written in the same transaction before the deletion
**Parameters:** process attributes BEFORE_HOOK_KEY, AFTER_HOOK_KEY, RETAIN_HISTORY_ON_CANCEL. The reason falls back to `inputs["reason"]`.
**Edge cases handled:** in a batch cancel (lines 280-304), an instance that has already ended, was started by the same user and has ext result=4 is skipped (idempotent); any batch item not committed aborts the whole batch. When the hook does not commit, the dialog comes back with committed=false. A startup job (`BpmWithdrawnHistoryCleanupRunner`) removes leftover history of cancelled instances.
**Suspected defect:** Deleting history removes approval participation, so former approvers lose their participant visibility of the document (see RULE-12).
**Confidence:** High.

### RULE-289: Process-instance status and result lifecycle
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

### RULE-290: A top-level rejection ends the whole process; a rejection inside a sub-process sends it back to the initiator
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
**Also stated by:** approval-routing — “A reject at top level ends the process; a reject inside a sub-process returns to the initiator” (`psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmTaskServiceImpl.java:802-844`)

### RULE-291: A rejection by an added co-signer is advisory only
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

### RULE-292: Row approval: any rejected row rejects the whole submission, but all rows still finish
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

### RULE-293: Return to initiator using the `rejected` flag
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

### RULE-294: Task result lifecycle (bpm_task_ext)
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

### RULE-295: Reliable delivery task lifecycle (psa_reliable_delivery_task)
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

### RULE-296: Batch submission returns the existing instance instead of creating one
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

### RULE-297: Initiator "withdraw to edit" (process stays alive)
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

### RULE-298: Return to an earlier node (single-instance only)
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

### RULE-299: Notify tasks become unread to-do items until the detail page is opened
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

### RULE-300: "Skip when empty" bypasses a node that has no approver
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

### RULE-301: Auto-complete cascade order after each task creation
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

### RULE-302: Repeat approver on the same node is auto-approved, except on rework or "reapprove on return"
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

### RULE-303: Outbox redelivery, backoff and dead-letter
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

### RULE-304: Reconcile job re-sends missed results from the bpm records
**Category:** Policy
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/mq/producer/BpmApprovalMqReliableSender.java:402-485`
**Plain English:** Delivery of task and process results is guaranteed by the event publication register: an event is written in the same transaction as the decision and resent from the register until acknowledged. A missed result is never rebuilt from the engine's own records. What is published is decided when the task completes, not by a job afterwards: a person's decision is published as itself; an automatic approval for a repeat approver is published with effect AUTOMATIC (D-1a-66, user decision in intent `2026-10-14-131`); an advisory co-sign rejection is published only with effect ADVISORY and never as a binding result; a node skipped without any decision publishes nothing.
**Specification:**
  Given a task decided by a person whose result event is in the register but not acknowledged
  When  the redelivery job runs
  Then  that same event is resent unchanged
  Given a repeat approver passed automatically at publication time
  Then  its event (effect AUTOMATIC) is in the register and is redelivered like any other
  Given a node skipped without a decision, or a task the engine completed with no event written
  When  the redelivery job runs
  Then  nothing is sent for it; the job never reads the engine tables to find "missed" results
**Parameters:** redelivery interval and retention are configuration of the publication register, not of this rule.
**Legacy behaviour:** a job every 10 minutes rebuilt "missed" results from the engine tables over a 24-hour lookback; it emitted engine-made completions as results, and a rebuilt cancel lost its cancel action and reason.
**Corrected:** 2026-10-21 per the verdict "wrong" of 2026-10-14 (RULE_REVIEWS.json); the legacy behaviour is kept only as evidence.
**Confidence:** High.

### RULE-305: Task-result messages (topic-bpm-approval-task-result)
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

### RULE-306: Which process endings reach business modules
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

### RULE-307: Anti-self-approval escalates to the initiator's direct leader
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

### RULE-308: End-of-process write-back of approve_status to the business table
**Category:** Policy
**Priority:** P0
**Source:** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/oa/CustomizedBpmOAServiceImpl.java:303-337`
**Plain English:** There is no generic write-back to a business table. When a process ends, a typed result event is published and each business module applies the outcome to its own document in its own code, with its own status check.
**Specification:**
  Given a process of document type X ends with the result APPROVED
  Then  a typed result event is published and the module owning X updates its own document
  Given any process definition
  Then  no statement is built from a table name held in configuration
**Parameters:** none.
**Legacy behaviour:** the engine wrote `approve_status` into the table named on the process definition by string-built SQL (2→3, 3→2, 4→4), with no status precondition, so a late or repeated event could overwrite a newer status.
**Corrected:** 2026-10-21 per the verdict "wrong" of 2026-10-14 (RULE_REVIEWS.json); the legacy behaviour is kept only as evidence.
**Confidence:** High.

### RULE-309: Withdraw/cancel cutoff = any marked node with a completed task
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
**Also stated by:** approval-lifecycle — “Withdraw cutoff ("撤回截止")” (`psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmProcessInstanceServiceImpl.java:449-477`)

### RULE-310: Document detail is visible to holders of the function permission or to process participants (fail-closed)
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

### RULE-311: The task-result consumer routes by business key, and a hook replaces the handler
**Category:** Policy
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/mq/consumer/approval/ApprovalTaskResultConsumer.java:41-75`
**Plain English:** A task result is handled the same way as a process result: the node's after-hook and the business handler registered for the business key both run, in a fixed order. A failure is rethrown so the event is redelivered.
**Specification:**
  Given a task result with business key "OPP_QUOTATION" and no after-hook
  Then  the business handler runs as the approver and a failure causes redelivery
  Given the same result with an after-hook
  Then  both the hook and the business handler run, in the same order as for process results
**Parameters:** none.
**Legacy behaviour:** with an after-hook only the hook ran and the business handler was skipped, unlike the process-result consumer.
**Open (the review note says "fixed order" without naming it):** whether the hook runs before or after the business handler; follow the process-result order once that is written down.
**Corrected:** 2026-10-21 per the verdict "wrong" of 2026-10-14 (RULE_REVIEWS.json); the legacy behaviour is kept only as evidence.
**Confidence:** High.

### RULE-312: The process-end consumer runs the hook and then the handler, and must be idempotent
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

### RULE-313: Added countersign participants only give advisory rejections
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

### RULE-314: Approval-start rounds and idempotency key
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

### RULE-315: Asynchronous timesheet process creation (psa_process_to_be_sent)
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

## 需要 SME 确认的规则

置信度不足 High，或抽取时发现疑似缺陷（遗留行为看起来有误，或与文档不一致）。brief 的行为契约**不会**把这些当作已定行为，会列为开放问题。

- [ ] **RULE-002**（P0，工时与资源）Which daily wage is used (before 2020 vs from 2020 on) — 疑似缺陷：`Integer.parseInt(postCode.substring(1))` throws if the post code has a non-numeric suffix. ｜Medium — SME: "Is the pre-2020 rule 'posts with level greater than 4 carry no cost' still valid, and should a missing post-2020 wage show as 0 instead of blocking?"
- [ ] **RULE-004**（P0，工时与资源）Human-days split of a person's approved day across projects — 疑似缺陷：(1) If a null `approval_hours` row follows a non-null row on the same day, `hoursValue.add(null)` throws an NPE. That aborts the run for every remaining user, because the job catches the exception and only logs it. (2) The input table `psa_time_sheet_works_end` is filled once by `syncWorkHours` (app ｜Medium — the formula is explicit. SME: "Is a day with more than 7.5 approved hours meant to stay at 1.0 person-day in total (divisor = actual hours) rather than going above 1.0?"
- [ ] **RULE-006**（P0，工时与资源）Project labour cost = daily wage × human days, excluding admin/support posts — 疑似缺陷：`contains("A")`/`contains("S")` matches the letter anywhere in the post code, not just as a prefix. Rest-day human days for A/S posts are still added to non-chargeable days, because the rest-day check runs first. RULE-24 (budget used cost) applies neither exclusion. ｜Medium — SME: "Should the A/S exclusion test the first letter of the post code only (post channel), and should admin rest days count as non-chargeable?"
- [ ] **RULE-008**（P1，工时与资源）Mobile (H5) submission records approved hours = declared hours — 疑似缺陷：The seed glossary distinguishes declared from approved hours, but no code in scope lets an approver change approved hours. ｜Medium — SME: "Should approvers ever be able to reduce approved hours below declared hours? If so, where in the business process does that happen?"
- [ ] **RULE-010**（P1，工时与资源）Converting plan person-days to hours (and back) — 疑似缺陷：The code's own TODO at line 399 asks whether different posts work the same hours per day. The same 7.5 constant is defined in two places. ｜Medium — SME: "Is 7.5 hours per person-day uniform for every post and region?"
- [ ] **RULE-011**（P1，工时与资源）Budget cost already used, by budget code — 疑似缺陷：This is not the same as project labour cost (RULE-05): A/S posts are not excluded. It uses the resource calendar fallback (Mon-Fri) instead of the stored workday flag. A null daily wage throws an NPE. ｜Medium — SME: "Should used budget cost exclude administrative/support posts the same way project labour cost does?"
- [ ] **RULE-012**（P1，工时与资源）Nightly check of task-package actual hours — 疑似缺陷：Unlike RULE-13, this job does not filter `deleted = 0` (lines 53-56). Soft-deleted approved lines raise its expected total, so packages with superseded versions are flagged on every run. The recalculation then writes the lower, correct value, so the same packages are "repaired" every night and the f 
- [ ] **RULE-014**（P2，工时与资源）Resource-list busyness = assigned task hours ÷ (working days × 7.5) — 疑似缺陷：Task hours are not prorated to the period here (RULE-19 prorates). The Gantt filter (`ganttChart` 509-516) includes the upper bound, this one excludes it. ｜Medium — SME: "Should list busyness prorate long tasks to the selected window, as the Gantt does?"
- [ ] **RULE-015**（P2，工时与资源）Gantt expected hours over the forecast window — 疑似缺陷："今天0点" (today 00:00) is built as `now().plusDays(1)`, i.e. tomorrow, so the window starts a day late (also at 912 and 1117). The "6个月" (6-month) branch uses `plusMonths(6)` but the comment says 5 months later. ｜Medium — SME: "Should forecast windows start today or tomorrow?"
- [ ] **RULE-016**（P2，工时与资源）Task hours spread evenly over working days and clipped to the window — 疑似缺陷：A task with zero planned working days gives per-day 0 (division error swallowed), so its hours disappear from the load. 
- [ ] **RULE-017**（P2，工时与资源）Remaining-hours estimate for overdue tasks — 疑似缺陷：Rounding days down makes any remaining work under 7.5h end today (0 days). ｜Medium — SME: "Should remaining days round up, so that 3h of remaining work still takes one day?"
- [ ] **RULE-022**（P0，工时与资源）Who can see whose timesheets — 疑似缺陷：Several read endpoints have no ownership or permission check: 
- [ ] **RULE-023**（P0，工时与资源）Only the owner can edit or resubmit, and only when rejected, cancelled or returned — 疑似缺陷：Doc TS-R-003/008 says approved sheets can be reopened by configuration. No reopen path exists in this code. 
- [ ] **RULE-027**（P0，工时与资源）The three org fields are mandatory on every line — 疑似缺陷：The values come from the client and are not compared with the server's work-package list. The intended source rules are: project or project task → the project's delivery dept; opportunity or opportunity task → the opportunity's dept; OH, visit, lead and customer → the submitter's dept (`applyWorkSpa 
- [ ] **RULE-030**（P0，工时与资源）Timesheets can only be booked to a work package the employee is allowed to use right now — 疑似缺陷：Only type and id are compared. The three org fields (costOrgId/projectOrgId/profitCenterId), termNumber, stageId and managerId sent by the client are not checked against the server's list, so a crafted payload can book against a valid package with a different cost organisation (see RULE-12). 
- [ ] **RULE-033**（P1，工时与资源）Opportunity visibility for booking depends on post channel and a 90-day window after a win — 疑似缺陷：The doc (09 COST-R01) says a project or opportunity that is not "执行中" (executing) cannot be booked. Opportunities are actually filtered by close status and channel, not by an executing status. 
- [ ] **RULE-034**（P1，工时与资源）A whole day of leave blocks sending a timesheet for that day — 疑似缺陷：The draft branch is technically exempt, but the same transaction applies RULE-10, which passes, and the draft is persisted. A full-leave day can therefore still end up with a draft sheet, which conflicts with the comment's goal of "no sheet on full-leave days". ｜Medium. SME: should drafts also be blocked on full-leave days?
- [ ] **RULE-036**（P1，工时与资源）Today's timesheet cannot be submitted before 09:00 (Shanghai time) — 疑似缺陷：There is no rule against future dates; the only guards are the accounting period and the 7.5h minimum. The doc (09 COST-R02) describes a deadline ("finish by 09:00 the next day"), which is not implemented. The code has an opening gate instead. ｜Medium. SME: should future-dated timesheets be allowed, and is there a submission deadline?
- [ ] **RULE-037**（P1，工时与资源）Daily declared-hours cap of 15 and positive hours per line — 疑似缺陷：The message says "不能小于0" (cannot be less than 0), but `compareTo(ZERO) < 1` also rejects exactly 0. A null declareHours throws an NPE in `merge` before the check. 
- [ ] **RULE-038**（P1，工时与资源）OH, visit, lead and customer work packages — ｜Medium — the lead filter (`selectListForWorkSpace`, said to be "not closed + data permission") is in a mapper I did not read. SME: confirm which leads should be bookable.
- [ ] **RULE-039**（P1，工时与资源）Role-gated "indirect" project and opportunity work packages — ｜Medium — the role codes are constants whose stored values I did not inspect. SME: confirm which business roles map to 项目辅助/交付辅助/商机辅助, and whether indirect project booking should really cover every executing project in the data scope.
- [ ] **RULE-041**（P1，工时与资源）Tasks can be accepted or refused only while pending — 疑似缺陷：There is no check that the caller is the task's manager (member-raised tasks) or its assignee (manager-assigned tasks), so any authenticated user can accept or refuse any task id. The related deletion guard (`TaskServiceImpl.del`, `PlanWorksServiceImpl.delete`) also checks only the accept flag, not  
- [ ] **RULE-043**（P0，工时与资源）Row decisions from BPM task-completion messages — 疑似缺陷：The legacy fallback path (no rowIds, lines 122-135) rejects every id in the opinion JSON, even ids with a blank opinion and ids belonging to other approvers. That contradicts the "blank means agree" rule of the node path. 
- [ ] **RULE-048**（P0，工时与资源）Timesheet header approval-status state machine (allowed transitions) — 疑似缺陷：The state-machine doc (05-工时域-状态机.md §5.3, §3.8) lists a `通过 → 取消` edge and an "admin revoke to DRAFT". The global rule list COST-R09 says approved sheets can be reopened back to 审批中. In code, Approved has no outgoing edge at all. Also, the 1 → 0 edge is open to any caller of `transition()`, so the  
- [ ] **RULE-052**（P1，工时与资源）Secondary header "status" field follows the approval status — 疑似缺陷：The doc (§2.3, §3.7) says `status = 2` marks POSTED and is written by the posting engine. In code it is written when the sheet is approved, so APPROVED and POSTED cannot be told apart by this field. A Pending (0) sheet carries status 0 or 1 depending on its history. 
- [ ] **RULE-053**（P1，工时与资源）Revision snapshots and their event types — 疑似缺陷：The doc (§5.2) lists snapshot events SUBMITTED, APPROVED, WITHDRAWN and POSTED. The code writes no revision on first submit or on approval (createRevision=false for 通过), and it records event types as numbers, not those names. A resubmit where every row is kept as approved could produce a null event_ 
- [ ] **RULE-055**（P1，工时与资源）New timesheet starts in Pending with a TS-prefixed number and a locked company code — 疑似缺陷：The insert already sets approve_status 0. The follow-up `transition(… 待发起, "创建工时单")` at line 830 therefore hits the same-status no-op (ManHourApproveStatusService.java:74-76), so the "creation" audit row in `psa_time_sheet_approve_status_revision` is never written. 
- [ ] **RULE-064**（P1，工时与资源）Working-day calendar with fallback when a date is missing — 疑似缺陷：There are three different fallbacks for the same question. Also, `CalendarServiceImpl.getCalendarMap(DateTime)` (51-63) loads only the current and previous year, so 6- or 12-month forecasts that run into next year ignore next year's holidays. ｜Medium — SME: "Which fallback is the business rule when the calendar is not maintained, and is next year's calendar supposed to be loaded in advance?"
- [ ] **RULE-065**（P2，工时与资源）Today's timesheet opens at 09:00 Beijing time — 疑似缺陷：The global rule list COST-R02 says a timesheet must be filed by 09:00 the next day (a deadline). The code enforces an opening time for the same day, and no filing deadline was found. ｜Medium. SME question: "Is 09:00 meant as when today's sheet opens, or as the deadline for yesterday's sheet?"
- [ ] **RULE-066**（P0，项目成本与核算）OH basis is timesheet (source_type 0) not-tax cost; period 13 uses the full-year base — 疑似缺陷：Docs COST-R39/R40 still say "wpt0/1 work-package hours". Code uses source_type = 0 because work_package_type is never populated (#875). 
- [ ] **RULE-069**（P0，项目成本与核算）OH allocation – share each overhead pot by direct-labour cost share, with rounding remainder on the largest line — 疑似缺陷：Docs (09 rules list COST-R45) call this the "largest remainder method". The code does not do that: it puts the whole remainder on the largest-amount line. 
- [ ] **RULE-070**（P0，项目成本与核算）Reversing an OH accrual mirrors the original allocation in proportion — 疑似缺陷：Docs COST-R44 say "分摊无独立红冲入口" (allocation has no separate reversal). The code does have this reversal-mirror path. 
- [ ] **RULE-072**（P0，项目成本与核算）WIP write-off selection – one revenue confirmation releases one whole WIP entry; leftovers go cost-only or wait — 疑似缺陷：Docs COST-R60 (write-off cutoff by business_date ≤ latest confirmation date) are not implemented. Pairing is by count, so one confirmation of any size releases a whole entry and pulls every other entry on the term into cost-only. ｜Medium. Question for the SME: should pairing be by amount or by business-date cutoff, rather than one revenue confirmation per WIP entry?
- [ ] **RULE-079**（P0，项目成本与核算）Posting rule matching and voucher-line rendering — 疑似缺陷：Docs COST-R54 describe 3-level matching on (carry_type, source_type, company). The code matches on carry_type → trigger condition → company. Docs also say "同一 carry_type 应唯一" (one rule per carry_type, design doc line 205). 
- [ ] **RULE-080**（P0，项目成本与核算）Revenue-return red reversal – reverse the whole original cost, re-render the voucher and negate it — 疑似缺陷：- The return's own amount is never used: a partial return reverses the entire cost. ｜Medium. Question for the SME: should a partial revenue return reverse cost in proportion to the returned amount, or always reverse all linked cost?
- [ ] **RULE-082**（P0，项目成本与核算）WIP period balance and report increase/decrease use the absolute movement amount — 疑似缺陷：ABS() turns negative WIP entries into positive balances. WipReportMapper.java:52-61 (increase/decrease) does the same, so WIP balance and the report overstate WIP by twice any negative cost entered. ｜Medium. Question for the SME: should negative cost entries reduce WIP? If so, ABS should only normalise the sign of red-reversal and decrease movements.
- [ ] **RULE-084**（P1，项目成本与核算）Splitting opportunity cost across terms – drawer and export use different formulas — 疑似缺陷：The drawer comment (line 153) claims it matches Sheet2's fallback, but Sheet2 never weights by contract amount. The two outputs give different gross profit for the same term. ｜Medium. Question for the SME: which basis should the export use?
- [ ] **RULE-088**（P1，项目成本与核算）Zero-value cost never enters WIP; negative cost does — 疑似缺陷：Docs COST-R52 select candidates by `period_id ≤ current`. The code uses `period_id = current` (`.eq`). ｜Medium. Question for the SME: should late ledger rows from earlier open periods be picked up by the current WIP-enter batch?
- [ ] **RULE-089**（P1，项目成本与核算）Legacy carry voucher – debit = Σ credits per company × U8C dept × subject — 疑似缺陷：The debit is rounded from the unrounded sum while each credit is rounded separately, so the voucher can be off by a few cents. 
- [ ] **RULE-090**（P1，项目成本与核算）Legacy WIP/cost carry-forward – labour = person-days × daily wage; project bonus 5% — 疑似缺陷：These parallel legacy carry-forward endpoints coexist with the new carry engine (RULE-11 to RULE-15), so the two can produce conflicting postings. ｜Medium. Question for the SME: are the legacy `/fst/accounting/wip` and `/cost` carry-forward endpoints retired, and is the 5% bonus still policy?
- [ ] **RULE-095**（P0，项目成本与核算）Red reversal confirm requires a valid reason and a successful red-voucher preview — 疑似缺陷：The seed spec requires the "财务主管" (finance supervisor) role. The controller only checks the generic permission `cost:carry-batch:confirm` (RevenueRedReversalController:149), which is the same one used for normal carry confirms. ｜Medium. SME question: "Must red-reversal confirm be limited to finance supervisors, separately from normal carry confirm?"
- [ ] **RULE-096**（P0，项目成本与核算）Collection start preconditions and order of side effects — ｜Medium. The salary-readiness rules live in SalaryCalculationReadinessPort, which I did not read. SME question: "Which payroll state counts as 'calculated' per company, and should period 13 really bypass it?"
- [ ] **RULE-097**（P0，项目成本与核算）Exempting a collection error needs a reason and only works on OPEN errors — 疑似缺陷：There is no check on issue type, so a technical SYSTEM_PROCESS_FAILED error can be exempted too, and that unblocks the confirmation gate. The spec (`cost-exception-workbench` "技术异常必阻断确认、业务异常…可豁免") says only business errors are exemptable, and the gate message says "必阻断，先由 IT 处置". The spec also limit ｜Medium. SME: may a SYSTEM_PROCESS_FAILED error be exempted, and if so by whom?
- [ ] **RULE-099**（P0，项目成本与核算）Unreconciled expense lines block carry and period close — 疑似缺陷：Period close reuses the carry-specific error code COST_CARRY_HAS_UNRECONCILED, so the close screen will show a carry message. A non-zero threshold would let close/carry proceed with known unreconciled rows; the spec has no threshold. 
- [ ] **RULE-102**（P0，项目成本与核算）Ingest pre-routing guards: duplicates, exempt flag, department bonus — 疑似缺陷：Finance can set flag_exempt in bulk through the cost-correction Excel import ("是 / 否 / 豁免", CostCorrectionImportService.java:425-441). That bypasses every validation and exception-workbench control with no reason and no 财务主管 restriction. 
- [ ] **RULE-104**（P0，项目成本与核算）Mandatory dimensions depend on where the cost will land — 疑似缺陷：The read-only posting pre-check (RULE-20) checks the OH mapping only for cost_org_id, not for the cost centre. A row can pass the pre-check and still fail ingest with DEPT_NOT_MAPPED on its cost centre. 
- [ ] **RULE-108**（P0，项目成本与核算）Month-end cost processing only runs while the period is Pre-closed or Reopened — 疑似缺陷：The 重开 branch looks unreachable through the normal UI. `AccountingPeriodManagementService.transition` rejects the REOPEN action outright, and 已关账 has no available actions (`AccountingPeriodStatusEnum` CLOSED_BOOKS = empty list). A closed period therefore cannot get back into a cost-processable state 
- [ ] **RULE-110**（P1，项目成本与核算）Row-level recon disposition: which actions are allowed and when — 疑似缺陷：The spec `cost-recon-disposition` requires a materiality threshold with batch accept for AMOUNT_DIFF. The code removed the threshold, and only per-row accept or blanket exempt exists. Unlike entry-error exemption, there is no reason field or audit reason. 
- [ ] **RULE-112**（P1，项目成本与核算）Posting pre-check: pool-bound expenses need an allocation mapping effective on the business date — 疑似缺陷：See RULE-17: the cost-centre mapping is not checked here, but ingest checks it. 
- [ ] **RULE-113**（P1，项目成本与核算）Posting pre-check: an expense's links must match its fee type — 疑似缺陷：The design doc (费用过账检测规则说明.md §4 rule 2, "会计期间可定位") lists a period-resolvability check, but checkRowWithCtx (lines 130-148) has none, only a null business-date check. The code also has an extra opportunity-status rule (checkOpportunity) that the doc does not list. 
- [ ] **RULE-114**（P1，项目成本与核算）A project fee must be borne by a delivery department, unless no delivery departments are flagged yet — 疑似缺陷：If someone clears every flag, the check switches off silently with no alert. 
- [ ] **RULE-115**（P1，项目成本与核算）Legacy WIP second carry checks the wrong prerequisite — 疑似缺陷：This is the defect: a copy-paste error on line 446. Also, the legacy carry lookups ignore company_code, so multiple companies would collide. WipController still exposes these endpoints alongside the new CarryBatchService. 
- [ ] **RULE-120**（P0，项目成本与核算）Allocation batch lifecycle — 疑似缺陷：`DeleteTrialAllocationUseCase:23-30` deletes a trial batch without checking its status, so a QUEUED or RUNNING trial can be deleted while the worker is still writing it. 
- [ ] **RULE-121**（P0，项目成本与核算）Collection batch status and phase lifecycle — 疑似缺陷：`fail()` has no guard. Its comment says "non-terminal only", but it would overwrite a COMPLETED batch if called. 
- [ ] **RULE-122**（P0，项目成本与核算）Carry batch status and phase state machine — 疑似缺陷：REVERSED (4) is never assigned by any code; only OpeningEventCarryBridge:275 reads it. Batch-level reversal is not implemented, and red reversal works line by line through carry_type=3. 
- [ ] **RULE-124**（P0，项目成本与核算）Period month-end step gate: collection lock, confirm, and re-collection rollback — 疑似缺陷：Nothing reverses an effective full allocation, so a collection error found after step 2 cannot be fixed through re-collection. 
- [ ] **RULE-127**（P0，项目成本与核算）Red reversal returns a relieved WIP entry to in-WIP and books one negative P&L line per positive line — 疑似缺陷：Seed doc 09 rule PLT-R02 says "红冲无人工入口" (no manual entry for red reversal). In code, red reversal is a manual draft-and-confirm flow (RULE-21). 
- [ ] **RULE-128**（P0，项目成本与核算）Legacy carry-forward lifecycle (待结转 → 结转中 → 已结转), still exposed by controllers — 疑似缺陷：1. In `F/service/wip/CarryForwardServiceImpl.java:219-222` the U8C call is commented out. updateResult is hard-coded SUCCESS, and the 2nd company processed always gets the fake error "U8C错误信息:XXXXXXXXXX", so no real voucher is ever posted. ｜Medium. SME question: "Are the legacy CostController and WipController carry-forward endpoints still reachable in prod, or fully replaced by the psa_carry_batch flow?"
- [ ] **RULE-129**（P1，项目成本与核算）Accounting period status transition table — 疑似缺陷：No production code calls `transition()`; only `AccountingPeriodStatusEnumTest` uses it, so nothing actually enforces this table. REOPENED (4) cannot be reached: no state lists the REOPEN action, and CLOSED_BOOKS has no actions at all. Yet cost processing depends on status 4. ｜Medium. SME question: "How does a closed-books period get to 重开 (4) in production, and which code or screen writes the period status?"
- [ ] **RULE-132**（P0，项目成本与核算）U8C voucher delivery and the opening-year local bypass — 疑似缺陷：The voucher status only ever moves DRAFT(0) → CONFIRMED(2). SENT(1) and FAILED(3) are never written, even though VoucherWriterImpl:69 says "回写 SENT/CONFIRMED", so delivery state only exists on the delivery task. 
- [ ] **RULE-133**（P0，项目成本与核算）Opening-period collection confirm can bulk-exempt all open errors — 疑似缺陷：This bypasses the normal-flow rule that technical errors "must be resolved by IT" (CostCollectionBatch:381-383). The exemptions are permanent. ｜Medium. SME question: "Is bulk exemption of technical errors acceptable for opening periods, and who may set forceConfirm?"
- [ ] **RULE-135**（P0，项目成本与核算）Month-end cost processing only runs while the period is pre-closed or reopened — 疑似缺陷：The status checks use `FSTConstant` integer constants, not the `AccountingPeriodStatusEnum` single source of truth (CLAUDE.md forbids new FSTConstant status usage). Seed spec cost-revenue-red-reversal says red reversal needs the period to be "开账中" (open), but the code requires pre-closed or reopened 
- [ ] **RULE-136**（P1，项目成本与核算）Aged in-WIP cost is frozen into impairment (stage 4) after write-off posting — 疑似缺陷：Candidates are filtered with `entered_period_id <= periodId`, which assumes period IDs increase in date order; that is not guaranteed if IDs are snowflake or loaded out of order. ｜Medium. SME question: "Is 720 days the approved impairment threshold, and should it apply to stage 1 (pre-contract) cost?"
- [ ] **RULE-137**（P1，项目成本与核算）Pre-posting "BLOCK" checks are advisory; only the salary check is enforced — 疑似缺陷：Users will read "阻断" (block) as a hard gate. Several BLOCK items (unapproved hours, zero-cost hours, outsourcing accrual gap, which can drive U8C negative balances at WIP carry) have no enforcement anywhere. 
- [ ] **RULE-138**（P1，项目成本与核算）Voucher delivery retry, suspension and manual re-delivery — 疑似缺陷：The seed spec says external_no is `CF-{batchNo}-{companyCode}-{seq}`, but the code (VoucherWriterImpl:115-120) uses `CF-{batchNo}-{voucherWord}-{deptCode}`, and `CF-{batchNo}-{returnSlipId}-{deptCode}` for red reversal. 
- [ ] **RULE-139**（P0，售前与报价）Server rates always overwrite client rates; at submission every work package must have a fully priced role — 疑似缺陷：(1) PRE-R02 ("billing rates may be discounted, hours may not be changed") is not implemented: there is no rate discount, any client rate is overwritten, and hours are freely editable by cost editors. (2) The duplicate-rate check (lines 54-57) can never fire, because the query already removes duplica 
- [ ] **RULE-140**（P0，售前与报价）At submit, cost and margin are recalculated from the current rates before approval starts — 疑似缺陷：A sales submission can therefore go to approval with labour cost understated (unpriced work packages count as 0) and margin overstated. The safeguards are the approval cost-required check (RULE-15) and the BU approver's adoption. 
- [ ] **RULE-141**（P0，售前与报价）Cost-tree work package value = hours × billing rate; labour cost = hours × cost rate — 疑似缺陷：(1) The multiplication is not rounded; for example 12.5 h × ¥833.33 = ¥10,416.625, so the value saved depends on the DB column scale. (2) The seed docs (Q5 "逐层向上汇总", PRE-R04 "自底向上汇总标价") say the tree rolls up into the price. In code, deliverable and clause amounts are never derived from their work pa ｜Medium — the logic is explicit, but SME question: "Should the bottom-up billing value constrain or check the final quote, or is it informational only?"
- [ ] **RULE-142**（P0，售前与报价）Final quote equals the sum of line amounts plus the tail adjustment — 疑似缺陷：Nothing stops the tail adjustment from wiping out most of the line total. The only limits are finalAmount > 0 at submit and clause-sum equality (RULE-10). 
- [ ] **RULE-144**（P0，售前与报价）Gross margin is calculated on the ex-tax final quote using the header tax rate, to 4 decimals — 疑似缺陷：(1) The seed doc (Q6) says to divide by a fixed 1.06; the code uses the quotation's own tax rate. (2) The margin always uses the header rate even when lines carry different rates. Example: one line of ¥113,000 at 13% has a line ex-tax amount of ¥100,000.00, but the margin uses 113,000 ÷ 1.06 = ¥106, 
- [ ] **RULE-145**（P0，售前与报价）Approval-time edits recalculate the final quote from the stored line total plus the new tail adjustment — 疑似缺陷：(1) A tax-rate change during approval does not re-derive the stored lines' tax splits, so line amountNotax and taxAmount no longer match the header rate. (2) If a legacy row has labor_cost NULL and the approver adopts only other costs, buCost becomes other costs only: labour drops out and margin is  
- [ ] **RULE-147**（P0，售前与报价）Role rates come from the master-data schemes in effect today, by role and city, newest scheme first — 疑似缺陷：Rates are taken as of the server's current date (`LocalDate.now()`), not the quotation's creation or submit date. Every save, submit or approval adoption re-prices the tree at that day's rates. Two schemes with the same start date are resolved by DB row order, which is not deterministic. 
- [ ] **RULE-148**（P1，售前与报价）Header tax rate defaults to 6% and must be between 0 (inclusive) and 1 (exclusive) — 疑似缺陷：The seed doc limits rates to the set 0/1/3/5/6/9/13%. The server accepts any value in [0, 1), for example 0.4. 
- [ ] **RULE-150**（P1，售前与报价）A clause percentage is a UI convenience; only the amount is stored, rounded to cents — 疑似缺陷：Rounding each percentage to cents can leave a 1-cent remainder that the exact server check rejects, and there is no "put the remainder in the last clause" rule. Note that this rule's source is outside the listed back-end paths. ｜Medium — this is front-end-only behaviour. SME question: "When a percentage split doesn't divide evenly, which clause should absorb the remainder?"
- [ ] **RULE-151**（P2，售前与报价）Cost template summary shows clause, deliverable and work-package counts and total hours; templates store no rates — 疑似缺陷：The seed doc says template rates come from a front-end static table; in code, rates come from master data at use time. 
- [ ] **RULE-152**（P2，售前与报价）Opportunity stats card: total, won, lost, sum of amounts, and number of quoted opportunities — 疑似缺陷："Quoted" counts quotations in any status, including drafts and rejected ones. 
- [ ] **RULE-155**（P0，售前与报价）During approval clauses are locked, so the tail adjustment is effectively frozen, and BU cost must be > 0 when required — 疑似缺陷：Tail adjustment and tax/price changes are offered to approvers as adoptable fields, but whenever clauses exist any change to the final quote is guaranteed to fail. 
- [ ] **RULE-158**（P0，售前与报价）Conditions for submitting a quotation for approval — 疑似缺陷：The aggregate's comment says the new instance id is "由终态消息补写" (written back by the final-result message). The code actually binds it straight after the workflow starts (see RULE-03), so the comment is out of date. 
- [ ] **RULE-159**（P0，售前与报价）Clause amounts must each be greater than 0 and add up exactly to the final quote — 疑似缺陷：The comment at `SubmitQuotationUseCase.java:60` says "合计 ≤ 最终报价", but the code requires equality. The front end warns only when the total exceeds the final quote (+0.005 tolerance), so an under-allocated split gives no warning before the hard rejection at submit. 
- [ ] **RULE-162**（P1，售前与报价）Starting a quotation from an opportunity — 疑似缺陷：Draft, submit and approval never re-check that the opportunity is still open. A quotation can be approved for an opportunity that was already lost. ｜Medium — SME question: "Should submit or approval be blocked when the opportunity has been closed after the draft was created?"
- [ ] **RULE-163**（P1，售前与报价）A margin below 50% requires a written low-margin reason (BU submitter or cost-adopting approver only) — 疑似缺陷：The reason is never cleared automatically when the margin recovers. The repository comment says it "must be cleared together" (`QuotationRepositoryImpl.java:156`), but the code just saves whatever the client sends. 
- [ ] **RULE-168**（P0，售前与报价）Only DRAFT or REJECTED quotations can be edited, submitted or deleted — 疑似缺陷：The save path writes to the database with no status condition (`QuotationRepositoryImpl.saveEditorContent`, lines 89-150, plain `updateById` plus delete-and-reinsert of child rows). Submit (`updateStatusIf`), delete and approval adoption all re-check status in the database (compare-and-set), but sav 
- [ ] **RULE-169**（P0，售前与报价）How BPM final results map to quotation outcomes — 疑似缺陷：A cancellation (e.g. the initiator withdraws) is stored as REJECTED, labelled "OA 驳回", and, if no comment was given, with reason "审批未通过". Users and reports cannot tell a withdrawal from a real rejection. ｜Medium — mapping is explicit. SME question: "When an approval is cancelled or withdrawn, should the quotation show as 'OA 驳回 / 审批未通过', or go back to DRAFT or a separate CANCELLED state?"
- [ ] **RULE-172**（P0，售前与报价）Only the applicant can save, submit or delete a quotation — 疑似缺陷：The delete endpoint requires only the `fst:quotation:query` permission (`QuotationController.java:212-222`, by design per refs #1509), so any query-role user who owns a draft can delete it. Note this when mapping roles. 
- [ ] **RULE-173**（P0，售前与报价）Users without cost-edit permission cannot change cost inputs; work-package cost fields are kept from the database — 疑似缺陷：New clauses (those without a code) carry the hours and roles sent by the client, so sales can set labour hours by deleting a clause and re-adding it. ｜Medium — this is inferred from the merge structure. SME question: "Should sales-created clause branches (template or manual) be allowed to carry hours, or should hours always be left blank for BU?"
- [ ] **RULE-178**（P2，售前与报价）Quotation numbering — 疑似缺陷：The aggregate's comment still says codes use the "QUO- 前缀" (`Quotation.java:32`); that comment is stale. 
- [ ] **RULE-180**（P2，售前与报价）The opportunity-expense CRM status sync needs explicit period and company targets — 疑似缺陷：Nothing is synced unless an operator keeps the parameter up to date for each new period. There is no automatic "current period". ｜Medium — the actual status mapping lives in `IOpportunityExpenseLedgerUseCase.syncCrmStatus`, outside the paths I was given. SME question: "Who updates the period/company targets each month, and should the job default to the current open period?"
- [ ] **RULE-181**（P0，交付、预算与收入）WBS budget cost is task hours × role cost rate, summed up the tree, plus non-labour cost categories — 疑似缺陷：The task-level product (`TaskPackage.budgetCost`) is never rounded to cents, but the progress page rounds each task to 2 dp (`ExecutionCostBook.money`). So the budget page total and the sum of progress-tree rows can differ by a few cents. 
- [ ] **RULE-182**（P0，交付、预算与收入）Budget gross margin uses ex-tax contract amount at the source quotation's tax rate (default 6%) — 疑似缺陷：The legacy performance margins (RULE-20) hard-code 1.06 no matter what the quotation's tax rate is, so the two screens can disagree for 13%-tax contracts. 
- [ ] **RULE-183**（P0，交付、预算与收入）Task ETC, EAC and POC — 疑似缺陷：Rule DLV-R08 (09-业务规则清单) says "ETC empty ⇒ EAC = AC (treated as complete)". The code instead defaults an empty ETC to remaining budget hours. 
- [ ] **RULE-185**（P0，交付、预算与收入）Revenue confirmation needs a positive amount and complete evidence; the period is the sign-date month — 疑似缺陷：The docs disagree with the code on three points: 
- [ ] **RULE-187**（P1，交付、预算与收入）Execution cost uses the assignee's personal rate; budget cost uses the role rate (frozen first) — 疑似缺陷：Two issues: ｜Medium — ask the SME: should actual cost use the rate in force on the work date?
- [ ] **RULE-189**（P1，交付、预算与收入）Hours roll-up and POC per deliverable, clause and project; overrun flag — 疑似缺陷：PREPAY clauses are now loaded (line 73), contrary to the `ProgressTree` docstring "已排除 PREPAY". An empty PREPAY clause counts as "not done", so a project whose tasks are all done but have 0 approved hours gets POC 0% instead of 100%. 
- [ ] **RULE-192**（P1，交付、预算与收入）Legacy term labour EAC = incurred at forecast + remaining forecast days × budget daily wage, floored at actual — 疑似缺陷：Two issues: ｜Medium — ask the SME: is the legacy forecast still used by the risk light?
- [ ] **RULE-193**（P1，交付、预算与收入）Legacy per-term earned value: progress, accrued revenue, EV, CV and VAC — 疑似缺陷："Fully confirmed" uses an exact equality, `amount.compareTo(completeAmount)==0`; any over-confirmation never switches to actuals. 
- [ ] **RULE-194**（P1，交付、预算与收入）Legacy project progress, cost waste and cost performance index — 疑似缺陷：A single `contractTermPerformanceList` is shared by every project in a batch (lines 1150-1153), so `calculateProjectPerformance` adds up other projects' terms. A project with two contracts keeps only the last contract's totals. ｜Medium — this is legacy code that the WBS model is replacing. Ask the SME: are these figures still consumed?
- [ ] **RULE-195**（P1，交付、预算与收入）Legacy margins divide by a fixed 1.06 for tax — 疑似缺陷：This disagrees with RULE-02, which uses the quotation's tax rate. 
- [ ] **RULE-196**（P1，交付、预算与收入）Budget-risk traffic light from the cost-waste rate — 疑似缺陷：- A project with no activity, or a budget of 0, gets rate 0 and shows yellow. ｜Medium — ask the SME: should a project with no costs and no progress be green, and should closed or terminated projects be scored?
- [ ] **RULE-197**（P1，交付、预算与收入）Project WIP = sum of carried-forward rows posted to cost but not yet written off — 疑似缺陷：A project whose rows are all written off disappears from the result, so its old inventory_value is never reset to 0. 
- [ ] **RULE-198**（P1，交付、预算与收入）A daily job writes the progress-tree POC into project progress for locked projects — 疑似缺陷：The job's doc claims "POC null ⇒ do not write". But `Sum.fill` always sets a POC, so a locked project with no clauses or tasks gets 0, not "—". 
- [ ] **RULE-202**（P0，交付、预算与收入）Only the project's PM can change execution-side structure — 疑似缺陷：`delete()` (lines 440-453) does not call `requireProjectManager`. Any holder of `fst:pm-project:progress` can delete another project's task packages, as long as no hours are logged. Also, `/task/update`, `/task/create` and `/deliverable/create` have no `@PreAuthorize` at all (PmProjectProgressContro 
- [ ] **RULE-205**（P0，交付、预算与收入）Revenue confirmation submit gates — clause exists, clause not obsolete, contract signed back — 疑似缺陷：Seed doc DLV-R18 lists three preconditions: work package POC 100%, internal review passed, and customer confirmation. The code dropped the deliverable gate (#1524), and `allDeliverablesAccepted` / `allDeliverablesInternalApproved` are only for display. Seed doc ACC-R08 requires the project to be 执行中 
- [ ] **RULE-209**（P0，交付、预算与收入）Every task package must be complete before a budget can be submitted — 疑似缺陷：The class comment says the role "must be in the static rate-table domain", but `validateTask` (lines 802-808) only checks that hours are not negative. The role value domain is never checked on the server. The `TASK_ROLE_INVALID` reason is unused in this aggregate. 
- [ ] **RULE-210**（P0，交付、预算与收入）Budget can only be edited while it is unlocked and not awaiting approval — 疑似缺陷：The budget write endpoints (`PmProjectBudgetController` `/import`, `/task/*`, `/lock`, `/deliverable*`, `/cost-items`) check only the permission `fst:pm-project:budget` and turn off `@DataPermission`. No use case checks that the caller is that project's PM. Anyone with the permission can edit or sub 
- [ ] **RULE-212**（P0，交付、预算与收入）Rules for an approver changing the budget tree during approval — 疑似缺陷：This path does not run the RULE-02 rate-missing check. An approver can add a task package with hours but no matching rate, and it is locked into the baseline at cost 0. 
- [ ] **RULE-213**（P0，交付、预算与收入）Total confirmed revenue cannot exceed the clause amount — 疑似缺陷：Unlike deliverable acceptance (RULE-21), the sign date is not checked against today. A future date is accepted and gives a future revenue period. 
- [ ] **RULE-222**（P0，交付、预算与收入）Approved revenue writes an append-only revenue fact, after a second quota check — 疑似缺陷：When the auto-reject happens, BPM shows the instance as approved while the legacy system shows it rejected. No notification or BPM correction is visible in the code. 
- [ ] **RULE-223**（P0，交付、预算与收入）Task package execution states — DOING/REWORK → DONE, DONE → DOING (reopen), LOCKED → REWORK — 疑似缺陷：`FSTConstant.TaskStatus` (0 进行中 / in progress, 1 已保存 / saved, 2 已完成 / completed, 3 已逾期 / overdue, with the comment "TODO 其他两个状态需要完善" / "the other two states need completing") is a separate legacy integer vocabulary. It is not used by this state machine, which uses strings. 
- [ ] **RULE-224**（P0，交付、预算与收入）Submitting a deliverable requires all its task packages to be done, and locks them — 疑似缺陷：If the submission is rejected (DRAFT), the packages stay LOCKED. Nothing unlocks them. Resubmission still works because LOCKED is accepted. 
- [ ] **RULE-225**（P0，交付、预算与收入）Deliverable approval outcome — approved → awaiting customer acceptance; rejected or cancelled → DRAFT — 疑似缺陷：The `StageReviewProcessResultHandler.java:16-17` comment says that when a meeting fails, the director-node handler already set the submission to DRAFT, so a later "approved" end is a no-op. But `recordDirectorReview` (lines 377-400) never changes status, and the form handler only records `meetingRes ｜Medium. SME question: "In PM_STAGE_REVIEW, does 'meeting not passed' end the process as approved, rejected, or neither? Should the submission go back to DRAFT?"
- [ ] **RULE-226**（P0，交付、预算与收入）Deliverable submission states, and which states hold the deliverable's single "submission slot" — 疑似缺陷：`DLV_CONVERTED` has no writer anywhere in the codebase, although it is listed as a state. 
- [ ] **RULE-227**（P0，交付、预算与收入）A budget can only be locked through an approved PM_BUDGET approval, and only when it is complete — 疑似缺陷：`requireAllRolesSelected` does NOT skip obsolete packages, unlike `requireAllTasksComplete`. An obsolete package with no role is read-only, so it would block every future submit. Seed doc OPS-R01 says "预算保存即锁定" (saving the budget locks it), but the code says save = submit for approval and the lock h 
- [ ] **RULE-236**（P1，交付、预算与收入）Customer acceptance can be registered once, only from ACCEPTING, with a sign date and two kinds of proof — 疑似缺陷：Revenue confirmation no longer requires accepted deliverables (see RULE-18), so ACCEPTED no longer gates anything downstream. 
- [ ] **RULE-237**（P1，交付、预算与收入）Approvers may change the cost tree only while the budget is PENDING; the adopted list is the full set — 疑似缺陷：Deletion through adoption (and `removeTaskPackage` during compilation) does not check `hasHoursInvested`. Whether this is safe depends on the budget task table being separate from the execution table (baseline sync on lock). ｜Medium. SME question: "During a budget revision, can a budget task package whose execution twin already has timesheet hours be deleted by the PM or an approver? If so, what should happen to the execution package?"
- [ ] **RULE-238**（P2，交付、预算与收入）Works stage: Design until the 深化 (deepening) stage output is fully confirmed, then Closing (one-way) — 疑似缺陷：Over-confirmation (¥310,000) stays in Design because the check is exact equality. By contrast, `ContractTermServiceImpl.getContractTermStage` uses ≥. 
- [ ] **RULE-241**（P0，交付、预算与收入）An ETC increase needs PM_ETC approval only if it pushes forecast labour cost over budget labour cost — 疑似缺陷：Seed DLV-R08 says "ETC 为空时 EAC = AC → 视同完工" (empty ETC means EAC = actual, treated as complete). The code treats empty ETC as max(0, planned − actual) (`TaskPackage.java:276-286`). 
- [ ] **RULE-245**（P0，交付、预算与收入）Which role cost rate applies: project city, then quotation city, then unfiltered; latest active scheme wins — 疑似缺陷：`PositionRateQueryImpl.build` turns a missing cost rate into ¥0 (not null) when the role has only a bill rate. Such tasks pass the "rate missing" gate (RULE-05) and silently cost ¥0. ｜Medium — ask the SME: should a role with a bill rate but no cost rate block budget submission?
- [ ] **RULE-248**（P0，审批（platform.approval））Countersign/or-sign nodes create one task per distinct approver — ｜Medium — the completion conditions live in the BPMN XML stored in the database, not in code. SME/DBA: list the `completionCondition` of each deployed MI node (all-sign vs or-sign vs `||rejected`).
- [ ] **RULE-249**（P0，审批（platform.approval））A single-approver node picks one candidate at random — 疑似缺陷：Routing is non-deterministic. If a role or department is configured on a single-instance node, an arbitrary member becomes the sole approver. 
- [ ] **RULE-250**（P0，审批（platform.approval））Initiator-leader scripts pick the 1st or 2nd reporting-line leader — 疑似缺陷：If the initiator has no leader, there is a NullPointerException at line 50, even for level 1. If the leader has no leader, level 2 throws an NPE at line 72. Combined with RULE-05, a skip-when-empty node is then silently skipped. Otherwise task creation crashes. 
- [ ] **RULE-252**（P0，审批（platform.approval））Per-row approver resolution — 疑似缺陷：For non-row-specific types the "first" element of a HashSet is taken, so the choice is arbitrary but deterministic per JVM. This differs from the single-instance random pick (RULE-03). 
- [ ] **RULE-253**（P0，审批（platform.approval））Approvers are resolved from the node's assignment-rule type — 疑似缺陷：`removeDisableUsers` (lines 692-701) exists but nothing calls it, so disabled or deleted users can still be made approvers. For DEPT_LEADER, a department with no leader adds `null` to the candidate set. If the random pick (RULE-03) lands on that `null`, task creation fails with "任务处理人不能为空" (assignee 
- [ ] **RULE-254**（P0，审批（platform.approval））"Custom data" approvers come from form fields, with a hard-coded fallback person — 疑似缺陷：The hard-coded personal fallback silently routes business approvals to one IT person. There is also a leftover `System.out.println` that dumps all process variables to stdout (line 673). 
- [ ] **RULE-265**（P0，审批（platform.approval））The initiator of a customized OA process is the caller-supplied `creator`, otherwise the logged-in user — 疑似缺陷：The HTTP endpoint is `@PermitAll` with `@DataPermission(enable=false)` (CustomizedBpmController.java:29-38) and accepts `creator` from the request body. An unauthenticated caller could therefore start, or resume (see RULE-03), an approval for any document while posing as any user, with arbitrary pro 
- [ ] **RULE-266**（P0，审批（platform.approval））Duplicate-start guard: one live process per business document, reused on resubmission — 疑似缺陷：The lookup is by `params_id` alone, not (params_id, processKey). The comment in WorkflowReturnStateQuery.java:17 says paramsIds are 16-digit snowflakes that are unique across tables, but no code enforces that. Two process types sharing an id would cross-wire. 
- [ ] **RULE-267**（P0，审批（platform.approval））A resubmission after a return must belong to the current return round — 疑似缺陷：RULE-03's resume branch inside createOA performs the same resume without this round check, and it accepts any caller (see RULE-02). 
- [ ] **RULE-269**（P0，审批（platform.approval））Withdraw to initiator: initiator only, before cutoff, single-instance current node, not already at initiator — 疑似缺陷：`getWithdrawable` can show "allow" when the actual withdraw will fail (MI node or already at initiator). 
- [ ] **RULE-270**（P0，审批（platform.approval））Batch approve acts on every user's pending tasks in each assignee's name — 疑似缺陷：- This bypasses RULE-01. The audit trail shows the assignee, not the operator. 
- [ ] **RULE-276**（P0，审批（platform.approval））Only the assigned approver may act on a task — 疑似缺陷：The fst unified-approve gate accepts unclaimed candidates (`taskCandidateOrAssigned`), but this check and `validateApprovalTaskFacts` (BpmProcessTaskApiImpl.java:494-497) require the exact assignee. See RULE-25. 
- [ ] **RULE-278**（P0，审批（platform.approval））Approval start rounds: a monotonic round number per (processKey, businessId) defines the idempotency key — 疑似缺陷：`selectMaxRound` followed by insert is not locked. Two concurrent submissions could compute the same round. Whether a unique index exists is not visible here. ｜Medium. SME/DBA question: "Is there a unique constraint on (process_key, business_id, start_round) or on idempotency_key?"
- [ ] **RULE-279**（P0，审批（platform.approval））Process-result handling is idempotent per instance and status (reference: quotation) — 疑似缺陷：- Cancelled (4) is mapped to a quotation reject, which conflates initiator withdrawal with an approver rejection. ｜Medium. SME questions: "Should a cancelled approval leave the quotation in REJECTED or return it to DRAFT?" and "Do all other ProcessResultHandlers (budget, revenue, deliverable, travel, timesheet) implement the same status, instance and time checks?"
- [ ] **RULE-280**（P0，审批（platform.approval））Unified approve: ownership gate before any field write, fail-closed field permission, default reasons — 疑似缺陷：- `handler.applyApproved` commits on its own before `completeApprovalTask`. The fst gate accepts candidates, but BPM then requires the exact assignee and taskDefinitionKey (BpmProcessTaskApiImpl.java:494-506). An unclaimed candidate's field edits can therefore be committed and then fail completion,  
- [ ] **RULE-281**（P1，审批（platform.approval））Node field permissions: unlisted fields are read-only; configuration is a full overwrite per process key — 疑似缺陷：The `/by-task` endpoint only checks `bpm:task:query`, not task ownership, so any approver can read another task's field map. Enforcement is in fst (RULE-24); the service itself does no enforcement. 
- [ ] **RULE-282**（P1，审批（platform.approval））Batch submission returns the existing instance for any document already mapped — 疑似缺陷：This disagrees with the single-submit path (RULE-03), which starts a new instance once the old one has ended and refuses a live one. In batch mode a re-submission after a final reject silently returns a dead instance, and the duplicate-live case is accepted rather than refused. ｜Medium. SME question: "Is batch submission only ever used for first-time submissions? If re-submission is possible, should it follow single-submit semantics?"
- [ ] **RULE-284**（P1，审批（platform.approval））Return to a specific earlier node: single-instance source and target only — 疑似缺陷：The reason is optional here (BpmTaskReturnToActivityReqVO has no @NotEmpty), while return-to-initiator requires one. 
- [ ] **RULE-285**（P1，审批（platform.approval））Transfer (change assignee) allowed only by the current assignee, to any user — 疑似缺陷：- The target user is not validated (existence, active status, not the initiator). ｜Medium. SME question: "Are transfers to the initiator or to inactive users allowed, and must a reason be recorded?"
- [ ] **RULE-286**（P1，审批（platform.approval））Countersign can only target a running parallel multi-instance node — 疑似缺陷：The service does not check that the caller is the initiator or a current approver of the instance; only `bpm:task:update` gates the HTTP endpoint (BpmTaskController.java:125-130). There is also no check that the added users are active. ｜Medium. SME question: "Who is allowed to add countersigners: the current approver, the initiator, or admins only?"
- [ ] **RULE-288**（P0，审批（platform.approval））Initiator cancels the whole process — 疑似缺陷：Deleting history removes approval participation, so former approvers lose their participant visibility of the document (see RULE-12). 
- [ ] **RULE-289**（P0，审批（platform.approval））Process-instance status and result lifecycle — 疑似缺陷：Telling a rejection apart from a cancellation depends only on the prefix of a Chinese reason string (`BpmProcessInstanceDeleteReasonEnum.isRejectReason`). If that wording changes, rejected instances would be recorded and published as cancellations. 
- [ ] **RULE-291**（P0，审批（platform.approval））A rejection by an added co-signer is advisory only — 疑似缺陷：The task-level reconcile job (RULE-20) re-sends every bpm_task_ext row with result in {2,3} that has no sent marker. So this "advisory" REJECT would be published to the business module 2 minutes to 24 hours later. That directly contradicts the "no result message" intent. 
- [ ] **RULE-292**（P0，审批（platform.approval））Row approval: any rejected row rejects the whole submission, but all rows still finish — 疑似缺陷：Unlike the per-person path (`dispatchSingleDecision`, lines 982-1006), this path does not check whether a row task was added by countersign. A countersign participant's rejection on a row node would set `rejected=true` and carry real veto power, which contradicts the advisory-reject design. ｜Medium — depends on how the BPMN gateway is drawn. SME: confirm that every row-approval node is followed by a `${rejected}` gateway.
- [ ] **RULE-293**（P0，审批（platform.approval））Return to initiator using the `rejected` flag — 疑似缺陷：A comment at lines 1134-1135 says the remaining rows staying "in approval" after a return is still waiting for business confirmation (都业梁) on whether they should revert to "rejected". ｜Medium — the mechanics are explicit, but the business row state after a return is pending confirmation. SME: after a return to the initiator, should rows that were not rejected stay "审批中" or become "驳回"?
- [ ] **RULE-295**（P0，审批（platform.approval））Reliable delivery task lifecycle (psa_reliable_delivery_task) — 疑似缺陷：A null maxRetryCount is treated as 0, so the first failure goes straight to DEAD. 
- [ ] **RULE-296**（P1，审批（platform.approval））Batch submission returns the existing instance instead of creating one — 疑似缺陷：The check does not ask whether P is still alive. Unlike single submission (RULE-13), a rejected or cancelled document resubmitted through the batch path gets its dead instance id back and is never re-approved. ｜Medium — SME: are batch submissions only ever first-time submissions?
- [ ] **RULE-300**（P0，审批（platform.approval））"Skip when empty" bypasses a node that has no approver — 疑似缺陷：Swallowing exceptions means a misconfiguration (missing rule, or a leader-script NPE per RULE-09) is skipped silently. The approval is effectively waived without any error. 
- [ ] **RULE-302**（P0，审批（platform.approval））Repeat approver on the same node is auto-approved, except on rework or "reapprove on return" — 疑似缺陷：Row data changes are not detected. A row that was edited but kept its row id is auto-approved after resubmission. 
- [ ] **RULE-303**（P0，审批（platform.approval））Outbox redelivery, backoff and dead-letter — 疑似缺陷：Rows written by persistTaskResult or persistProcessResult also go through asyncSend after commit. The drainer can pick a row up before markSent, so duplicate delivery is possible. Consumers must be idempotent. 
- [ ] **RULE-304**（P0，审批（platform.approval））Reconcile job re-sends missed results from the bpm records — 疑似缺陷：(a) Any task completed by the engine gets result=2 from RULE-02, even when nothing was published: initiator auto-skip, resubmit completion, same-approver auto-approve. The same applies to advisory co-sign rejections (RULE-06). Reconcile would emit these as "missed". (b) A rebuilt cancel message lack ｜Medium — SME/tech lead: should reconcile limit itself to tasks completed by a human decision?
- [ ] **RULE-307**（P0，审批（platform.approval））Anti-self-approval escalates to the initiator's direct leader — 疑似缺陷：When other non-initiator candidates exist, the leader is still added. Combined with the random pick (RULE-03), the leader may become the approver even though a peer was available. 
- [ ] **RULE-308**（P0，审批（platform.approval））End-of-process write-back of approve_status to the business table — 疑似缺陷：- The table name is interpolated as `${tableName}` (SqlActionMapper.java:15), so it relies on trusted definition configuration. ｜Medium. SME question: "Which process keys still rely on this generic write-back rather than an fst ProcessResultHandler, and is overwriting without a state check acceptable?"
- [ ] **RULE-311**（P0，审批（platform.approval））The task-result consumer routes by business key, and a hook replaces the handler — 疑似缺陷：This is inconsistent with the process consumer (RULE-22), which runs both the hook and the handler. ｜Medium — SME: is it intended that a node AFTER hook fully replaces the business handler?
- [ ] **RULE-313**（P1，审批（platform.approval））Added countersign participants only give advisory rejections — 疑似缺陷：There is no guard against adding countersign to a row-approval node. The element variable would be set to a user id but read as a row id (BpmUserTaskActivityBehavior.java:84-87). The added task then gets a null assignee, or the node-level approver instead of the requested person (RULE-17). ｜Medium. SME: should add-sign be blocked on row-approval nodes?
- [ ] **RULE-315**（P1，审批（platform.approval））Asynchronous timesheet process creation (psa_process_to_be_sent) — 疑似缺陷：Comment lines 178-181 rely on a "compensation replay" in `CustomizedBpmOAServiceImpl.updateApproveStatus`, but that code was removed (#564), so the instant self-approval race described there is no longer compensated by that path. 
