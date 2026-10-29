# DATA_OBJECTS — psa

业务规则卡（`BUSINESS_RULES.md`）中引用到的核心记录：按表名或 DO 类名在规则正文中的出现次数排序。字段取自 DO 类的 `private` 声明（Java 类型）。

共 58 个对象被规则引用（DO 总数 471）。

| 表 | DO 类 | 被引用规则数 | 位置 |
|---|---|---|---|
| `(domain) Quotation` | Quotation | 19 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/presales/quote/quotation/Quotation.java` |
| `(domain) ProjectBudget` | ProjectBudget | 12 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/ProjectBudget.java` |
| `(domain) DeliverableSubmission` | DeliverableSubmission | 7 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/DeliverableSubmission.java` |
| `(domain) DeliverableProgress` | DeliverableProgress | 7 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/DeliverableProgress.java` |
| `(domain) CostCollectionBatch` | CostCollectionBatch | 6 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/batch/CostCollectionBatch.java` |
| `bpm_task_ext` | BpmTaskExtDO | 5 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/dal/dataobject/bpm/BpmTaskExtDO.java` |
| `(domain) TaskPackage` | TaskPackage | 4 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/TaskPackage.java` |
| `(domain) CostRoutingService` | CostRoutingService | 4 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/ingest/CostRoutingService.java` |
| `psa_time_sheet_approve_status_revision` | ManHourApproveStatusRevisionDO | 3 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/dal/dataobject/manhour/ManHourApproveStatusRevisionDO.java` |
| `bpm_oa_customized` | CustomizedBpmOADO | 3 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/dal/dataobject/bpm/CustomizedBpmOADO.java` |
| `(domain) PeriodProgress` | PeriodProgress | 3 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/period/PeriodProgress.java` |
| `(domain) CarrySelectorImpl` | CarrySelectorImpl | 3 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/carry/CarrySelectorImpl.java` |
| `(domain) BudgetRevision` | BudgetRevision | 3 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/BudgetRevision.java` |
| `system_dept` | TempSystemDeptDO | 2 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/dal/dataobject/manhour/TempSystemDeptDO.java` |
| `system_dept` | DeptDO | 2 | `psa-backend/yudao-module-system/yudao-module-system-biz/src/main/java/cn/iocoder/yudao/module/system/dal/dataobject/dept/DeptDO.java` |
| `system_dept` | DeptAllDO | 2 | `psa-backend/yudao-module-system/yudao-module-system-biz/src/main/java/cn/iocoder/yudao/module/system/dal/dataobject/dept/DeptAllDO.java` |
| `psa_time_sheet_works_end` | ManHourEndDO | 2 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/dal/dataobject/manhour/ManHourEndDO.java` |
| `psa_time_sheet` | ManHourDO | 2 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/dal/dataobject/manhour/ManHourDO.java` |
| `psa_revenue_fact` | RevenueFactDO | 2 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/dal/dataobject/revenue/RevenueFactDO.java` |
| `psa_process_to_be_sent` | ProcessToBeSentDO | 2 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/dal/dataobject/bpm/ProcessToBeSentDO.java` |
| `psa_pm_budget` | BudgetDO | 2 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/dal/dataobject/project/BudgetDO.java` |
| `psa_dept_u8c` | U8cDeptDO | 2 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/dal/dataobject/u8c/U8cDeptDO.java` |
| `psa_carry_batch` | CarryBatchDO | 2 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/infrastructure/cost/projectcost/carry/CarryBatchDO.java` |
| `psa_calendar` | CalendarDO | 2 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/dal/dataobject/calendar/CalendarDO.java` |
| `bpm_process_instance_ext` | BpmProcessInstanceExtDO | 2 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/dal/dataobject/bpm/BpmProcessInstanceExtDO.java` |
| `(domain) RevenueConfirmation` | RevenueConfirmation | 2 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/receivables/revenue/RevenueConfirmation.java` |
| `(domain) QuotationLine` | QuotationLine | 2 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/presales/quote/quotation/QuotationLine.java` |
| `(domain) ProgressTree` | ProgressTree | 2 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/ProgressTree.java` |
| `(domain) PostingRuleEngine` | PostingRuleEngine | 2 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/voucher/PostingRuleEngine.java` |
| `(domain) OhAllocationCalculator` | OhAllocationCalculator | 2 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/allocation/OhAllocationCalculator.java` |
| `(domain) LaborReclassDiffCalculator` | LaborReclassDiffCalculator | 2 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/laborreclass/LaborReclassDiffCalculator.java` |
| `(domain) LaborCostHeadroom` | LaborCostHeadroom | 2 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/LaborCostHeadroom.java` |
| `(domain) CostTemplate` | CostTemplate | 2 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/presales/quote/costestimate/CostTemplate.java` |
| `psa_wip_carry_forward_data_works` | CarryForwardDataWorksDO | 1 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/dal/dataobject/accounting/CarryForwardDataWorksDO.java` |
| `psa_reliable_delivery_task` | ReliableDeliveryTaskDO | 1 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/infrastructure/platform/integration/reliabledelivery/ReliableDeliveryTaskDO.java` |
| `psa_pm_cuvalue` | CuValueDO | 1 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/dal/dataobject/project/CuValueDO.java` |
| `psa_pl_ledger` | PlLedgerDO | 1 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/infrastructure/cost/projectcost/writeoff/PlLedgerDO.java` |
| `psa_ohproject_detail` | OhProjectDetailDO | 1 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/dal/dataobject/ohproject/OhProjectDetailDO.java` |
| `psa_oa_account_u8c` | OaAccountU8cDO | 1 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/dal/dataobject/accounting/OaAccountU8cDO.java` |
| `psa_deliverable` | DeliverableDO | 1 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/dal/dataobject/project/DeliverableDO.java` |
| `psa_cost_recon_disposition` | ReconDispositionDO | 1 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/infrastructure/cost/projectcost/recon/ReconDispositionDO.java` |
| `psa_bpm_mq_outbox` | BpmMqOutboxDO | 1 | `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/dal/dataobject/mq/BpmMqOutboxDO.java` |
| `(domain) WriteOffLine` | WriteOffLine | 1 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/carry/WriteOffLine.java` |
| `(domain) TimesheetElementRounding` | TimesheetElementRounding | 1 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/ingest/TimesheetElementRounding.java` |
| `(domain) SalaryMemberFact` | SalaryMemberFact | 1 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/prepostingcheck/SalaryMemberFact.java` |
| `(domain) QuotationSeal` | QuotationSeal | 1 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/presales/quote/quotation/QuotationSeal.java` |
| `(domain) ProjectBillingView` | ProjectBillingView | 1 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/receivables/billing/ProjectBillingView.java` |
| `(domain) PositionRate` | PositionRate | 1 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/presales/quote/quotation/PositionRate.java` |
| `(domain) OtherCosts` | OtherCosts | 1 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/presales/quote/quotation/OtherCosts.java` |
| `(domain) LedgerBasisRow` | LedgerBasisRow | 1 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/allocation/LedgerBasisRow.java` |
| `(domain) CostTemplateNode` | CostTemplateNode | 1 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/presales/quote/costestimate/CostTemplateNode.java` |
| `(domain) CostEstimateNode` | CostEstimateNode | 1 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/presales/quote/quotation/CostEstimateNode.java` |
| `(domain) CostAllocationBatch` | CostAllocationBatch | 1 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/allocation/CostAllocationBatch.java` |
| `(domain) ClauseRevenueQuota` | ClauseRevenueQuota | 1 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/receivables/revenue/ClauseRevenueQuota.java` |
| `(domain) CarryBatch` | CarryBatch | 1 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/carry/CarryBatch.java` |
| `(domain) BudgetDeliverable` | BudgetDeliverable | 1 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/BudgetDeliverable.java` |
| `(domain) BudgetClause` | BudgetClause | 1 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/BudgetClause.java` |
| `(domain) AnnualBonusSplitCalculator` | AnnualBonusSplitCalculator | 1 | `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/ingest/AnnualBonusSplitCalculator.java` |

## `(domain) Quotation` — Quotation
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/presales/quote/quotation/Quotation.java`  
**使用它的规则：** RULE-140, RULE-142, RULE-143, RULE-144, RULE-145, RULE-148, RULE-158, RULE-159, RULE-160, RULE-161, RULE-163, RULE-164, RULE-166, RULE-168, RULE-170, RULE-174, RULE-175, RULE-178, RULE-315

| 字段 | 类型 |
|---|---|
| DEFAULT_TAX_RATE | `static final BigDecimal` |
| id | `Long` |
| code | `String` |
| opportunityId | `Long` |
| contractCode | `String` |
| customerId | `Long` |
| applyUserId | `Long` |
| applyDeptId | `Long` |
| deliverDeptId | `Long` |
| pdcDeptId | `Long` |
| salesUserId | `Long` |
| taxRate | `BigDecimal` |
| finalAmount | `BigDecimal` |
| buCost | `BigDecimal` |
| laborCost | `BigDecimal` |
| otherCosts | `OtherCosts` |
| grossMargin | `BigDecimal` |
| visibleTeam | `List<String>` |
| status | `String` |
| quoteName | `String` |
| needSeal | `Boolean` |
| tailAdjust | `BigDecimal` |
| quoteNote | `String` |
| lowGmReason | `String` |
| rejectReason | `String` |
| submitTime | `LocalDateTime` |
| processInstanceId | `String` |
| returnedToInitiator | `boolean` |
| version | `Long` |
| createdTime | `LocalDateTime` |
| quoteFiles | `List<QuotationFile>` |
| lines | `List<QuotationLine>` |
| costNodes | `List<CostEstimateNode>` |
| costCategory | `String` |
| cityCategoryCode | `String` |
| seals | `List<QuotationSeal>` |
| labor | `BigDecimal` |
| other | `BigDecimal` |

## `(domain) ProjectBudget` — ProjectBudget
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/ProjectBudget.java`  
**使用它的规则：** RULE-181, RULE-182, RULE-209, RULE-210, RULE-211, RULE-212, RULE-218, RULE-227, RULE-228, RULE-229, RULE-237, RULE-243

| 字段 | 类型 |
|---|---|
| DEFAULT_TAX_RATE | `static final BigDecimal` |
| projectId | `Long` |
| projectCode | `String` |
| opportunityId | `Long` |
| contractAmount | `BigDecimal` |
| locked | `boolean` |
| approvalStatus | `String` |
| processInstanceId | `String` |
| returnedToInitiator | `boolean` |
| clauses | `List<BudgetClause>` |
| removedClauseIds | `List<Long>` |
| cityCategoryCode | `String` |
| createdClauses | `List<BudgetClause>` |
| obsoletedClauses | `List<BudgetClause>` |

## `(domain) DeliverableSubmission` — DeliverableSubmission
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/DeliverableSubmission.java`  
**使用它的规则：** RULE-208, RULE-217, RULE-225, RULE-233, RULE-234, RULE-235, RULE-236

| 字段 | 类型 |
|---|---|
| id | `Long` |
| code | `String` |
| deliverableId | `Long` |
| title | `String` |
| phase | `String` |
| status | `String` |
| submitterId | `Long` |
| submittedTime | `LocalDateTime` |
| internalReviewBy | `Long` |
| internalReviewTime | `LocalDateTime` |
| internalReviewNote | `String` |
| pmoReviewBy | `Long` |
| pmoReviewTime | `LocalDateTime` |
| pmoReviewNote | `String` |
| acceptanceSignDate | `LocalDate` |
| acceptanceBy | `Long` |
| acceptanceTime | `LocalDateTime` |
| processId | `String` |
| meetingTime | `LocalDateTime` |
| meetingResult | `String` |
| directorReviewBy | `Long` |
| directorReviewTime | `LocalDateTime` |
| reviewContent | `String` |
| reviewScore | `Integer` |
| chiefReviewBy | `Long` |
| chiefReviewTime | `LocalDateTime` |
| requirementReviewDate | `LocalDate` |
| drawingCategory | `String` |
| constructionDrawingReview | `Boolean` |
| directorOpinion | `String` |
| pdScore | `Integer` |
| firstScore | `Integer` |
| structureScore | `Integer` |
| safetyScore | `Integer` |
| firstPass | `Boolean` |
| finalSubmitBy | `Long` |
| finalSubmitTime | `LocalDateTime` |
| secondScore | `Integer` |
| managementEvaluation | `String` |
| secondReviewBy | `Long` |
| secondReviewTime | `LocalDateTime` |
| attachments | `List<SubmissionAttachment>` |

## `(domain) DeliverableProgress` — DeliverableProgress
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/DeliverableProgress.java`  
**使用它的规则：** RULE-206, RULE-207, RULE-223, RULE-224, RULE-231, RULE-241, RULE-242

| 字段 | 类型 |
|---|---|
| id | `Long` |
| obsolete | `boolean` |
| tasks | `List<TaskPackage>` |
| task | `TaskPackage` |
| approvalRequired | `boolean` |
| task | `TaskPackage` |
| etcChanged | `boolean` |
| assigneeChanged | `boolean` |
| assigneeKept | `boolean` |

## `(domain) CostCollectionBatch` — CostCollectionBatch
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/batch/CostCollectionBatch.java`  
**使用它的规则：** RULE-100, RULE-101, RULE-121, RULE-131, RULE-133, RULE-315

| 字段 | 类型 |
|---|---|
| id | `Long` |
| periodId | `Long` |
| companyCode | `String` |
| progressId | `Long` |
| status | `CostCollectionBatchStatusEnum` |
| phase | `CostCollectionBatchPhaseEnum` |
| baselineCount | `long` |
| baselineAmount | `BigDecimal` |
| actualLedgerCount | `long` |
| actualLedgerAmount | `BigDecimal` |
| actualPoolCount | `long` |
| actualPoolAmount | `BigDecimal` |
| actualOppexpCount | `long` |
| actualOppexpAmount | `BigDecimal` |
| actualErrorCount | `long` |
| actualErrorAmount | `BigDecimal` |
| actualExemptCount | `long` |
| actualExemptAmount | `BigDecimal` |
| actualTransferInCount | `long` |
| actualTransferInAmount | `BigDecimal` |
| actualRoundingAdjCount | `long` |
| actualRoundingAdjAmount | `BigDecimal` |
| businessErrorCount | `long` |
| businessErrorAmount | `BigDecimal` |
| technicalErrorCount | `long` |
| technicalErrorAmount | `BigDecimal` |
| processedCount | `long` |
| processedAmount | `BigDecimal` |
| sourceReconStatus | `BatchReconStatusEnum` |
| collectReconStatus | `BatchReconStatusEnum` |
| sourceDiffCount | `long` |
| sourceDiffAmount | `BigDecimal` |
| missingCount | `long` |
| missingAmount | `BigDecimal` |
| overCollectedCount | `long` |
| overCollectedAmount | `BigDecimal` |
| amountDiffCount | `long` |
| amountDiffAmount | `BigDecimal` |
| startedBy | `Long` |
| startedAt | `LocalDateTime` |
| finishedAt | `LocalDateTime` |
| lastError | `String` |
| reconMessage | `String` |
| version | `Integer` |

## `bpm_task_ext` — BpmTaskExtDO
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/dal/dataobject/bpm/BpmTaskExtDO.java`  
**使用它的规则：** RULE-273, RULE-291, RULE-294, RULE-304, RULE-315

| 字段 | 类型 |
|---|---|
| id | `Long` |
| assigneeUserId | `Long` |
| name | `String` |
| taskId | `String` |
| result | `Integer` |
| reason | `String` |
| endTime | `LocalDateTime` |
| processInstanceId | `String` |
| processDefinitionId | `String` |

## `(domain) TaskPackage` — TaskPackage
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/TaskPackage.java`  
**使用它的规则：** RULE-181, RULE-183, RULE-241, RULE-315

| 字段 | 类型 |
|---|---|
| id | `Long` |
| code | `String` |
| name | `String` |
| roleCode | `String` |
| assigneeId | `Long` |
| plannedHours | `BigDecimal` |
| actualHours | `BigDecimal` |
| etcHours | `BigDecimal` |
| status | `String` |
| roleCostRate | `BigDecimal` |
| lockedCostRate | `BigDecimal` |
| etcApprovalStatus | `String` |
| etcPendingHours | `BigDecimal` |
| etcProcessId | `String` |
| etcReason | `String` |
| sourceWorkpkgId | `Long` |
| planStartDate | `LocalDate` |
| planEndDate | `LocalDate` |
| trueStartDate | `LocalDate` |
| trueEndDate | `LocalDate` |
| persistenceVersion | `LocalDateTime` |
| obsolete | `boolean` |
| timesheetLogged | `boolean` |

## `(domain) CostRoutingService` — CostRoutingService
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/ingest/CostRoutingService.java`  
**使用它的规则：** RULE-073, RULE-074, RULE-102, RULE-103

| 字段 | 类型 |
|---|---|
| validationService | `CostValidationService` |
| deptPort | `OhAllocationDeptMappingPort` |
| idempotencyChecker | `IdempotencyChecker` |
| opportunityProjectPort | `OpportunityProjectPort` |
| bonusSourceType | `int` |
| annualBonusSourceType | `int` |
| annualBonusLedgerLevels | `Set<String>` |
| zeroHourSalarySourceType | `int` |

## `psa_time_sheet_approve_status_revision` — ManHourApproveStatusRevisionDO
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/dal/dataobject/manhour/ManHourApproveStatusRevisionDO.java`  
**使用它的规则：** RULE-055, RULE-060, RULE-315

| 字段 | 类型 |
|---|---|
| id | `Long` |
| tsheetId | `Long` |
| fromApproveStatus | `Integer` |
| toApproveStatus | `Integer` |
| operatorId | `Long` |
| operatorType | `String` |
| remark | `String` |
| processTitle | `String` |

## `bpm_oa_customized` — CustomizedBpmOADO
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/dal/dataobject/bpm/CustomizedBpmOADO.java`  
**使用它的规则：** RULE-283, RULE-287, RULE-306

| 字段 | 类型 |
|---|---|
| id | `Long` |
| paramsId | `Long` |
| dataStamp | `String` |
| processInstanceId | `String` |

## `(domain) PeriodProgress` — PeriodProgress
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/period/PeriodProgress.java`  
**使用它的规则：** RULE-106, RULE-124, RULE-125

| 字段 | 类型 |
|---|---|
| id | `Long` |
| periodId | `Long` |
| companyCode | `String` |
| collectConfirmedBy | `Long` |
| collectConfirmedAt | `LocalDateTime` |
| nextStep | `Integer` |
| lastPostedStep | `Integer` |
| stepStatus | `Integer` |
| version | `Integer` |

## `(domain) CarrySelectorImpl` — CarrySelectorImpl
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/carry/CarrySelectorImpl.java`  
**使用它的规则：** RULE-071, RULE-072, RULE-123

| 字段 | 类型 |
|---|---|
| POC_COMPLETED | `static final int` |
| contractStatusPort | `ContractStatusPort` |
| pocPort | `ContractTermPocPort` |
| contractTermPort | `ContractTermPort` |
| revenueFactPort | `RevenueFactPort` |
| projectStatusPort | `ProjectStatusPort` |
| manualWriteOffPort | `ManualWriteOffPort` |

## `(domain) BudgetRevision` — BudgetRevision
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/BudgetRevision.java`  
**使用它的规则：** RULE-216, RULE-221, RULE-232

| 字段 | 类型 |
|---|---|
| id | `Long` |
| projectId | `Long` |
| revisionNo | `Integer` |
| status | `String` |
| reasonType | `String` |
| reasonNote | `String` |
| snapshotId | `Long` |
| obsoleteNodeCount | `int` |
| obsoleteActualCost | `BigDecimal` |
| obsoleteConfirmedBy | `String` |
| processInstanceId | `String` |

## `system_dept` — TempSystemDeptDO
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/dal/dataobject/manhour/TempSystemDeptDO.java`  
**使用它的规则：** RULE-114, RULE-255

| 字段 | 类型 |
|---|---|
| id | `Long` |
| leaderUserId | `Long` |
| deleted | `Integer` |

## `system_dept` — DeptDO
**位置：** `psa-backend/yudao-module-system/yudao-module-system-biz/src/main/java/cn/iocoder/yudao/module/system/dal/dataobject/dept/DeptDO.java`  
**使用它的规则：** RULE-114, RULE-255

| 字段 | 类型 |
|---|---|
| id | `Long` |
| code | `String` |
| name | `String` |
| parentId | `Long` |
| sort | `Integer` |
| leaderUserId | `Long` |
| arUserId | `Long` |
| phone | `String` |
| email | `String` |
| status | `Integer` |
| finalDept | `Integer` |
| shortName | `String` |
| deliveryFlag | `Integer` |
| category | `String` |

## `system_dept` — DeptAllDO
**位置：** `psa-backend/yudao-module-system/yudao-module-system-biz/src/main/java/cn/iocoder/yudao/module/system/dal/dataobject/dept/DeptAllDO.java`  
**使用它的规则：** RULE-114, RULE-255

| 字段 | 类型 |
|---|---|
| deleted | `Boolean` |

## `psa_time_sheet_works_end` — ManHourEndDO
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/dal/dataobject/manhour/ManHourEndDO.java`  
**使用它的规则：** RULE-004, RULE-315

| 字段 | 类型 |
|---|---|
| id | `Long` |
| personId | `Long` |
| worksDate | `LocalDateTime` |
| deptId | `Long` |
| costDeptId | `Long` |
| stageId | `Long` |
| oppStageId | `Long` |
| postId | `Long` |
| taskName | `String` |
| projectId | `Long` |
| projectCode | `String` |
| projectName | `String` |
| termNumber | `String` |
| declareHours | `BigDecimal` |
| approvalHours | `BigDecimal` |
| progress | `BigDecimal` |
| type | `Integer` |
| remarks | `String` |
| managerId | `Long` |
| createTime | `LocalDateTime` |
| updateTime | `LocalDateTime` |
| humanDays | `BigDecimal` |
| deleted | `Integer` |
| writeOffFlag | `Integer` |
| projectWriteOffFlag | `Integer` |
| planDetailId | `Long` |
| workPackageId | `Long` |
| workPackageType | `Integer` |
| taskId | `Long` |
| budgetCode | `String` |
| postCode | `String` |
| workday | `Integer` |

## `psa_time_sheet` — ManHourDO
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/dal/dataobject/manhour/ManHourDO.java`  
**使用它的规则：** RULE-048, RULE-052

| 字段 | 类型 |
|---|---|
| id | `Long` |
| originId | `Long` |
| deptId | `Long` |
| postId | `Long` |
| status | `Integer` |
| worksDate | `LocalDateTime` |
| approveStatus | `Integer` |
| code | `String` |
| personId | `Long` |
| createTime | `LocalDateTime` |
| dataOrigin | `Integer` |
| companyCode | `String` |
| deleted | `Integer` |

## `psa_revenue_fact` — RevenueFactDO
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/dal/dataobject/revenue/RevenueFactDO.java`  
**使用它的规则：** RULE-222, RULE-240

| 字段 | 类型 |
|---|---|
| id | `Long` |
| clauseId | `Long` |
| deliverableId | `Long` |
| amount | `BigDecimal` |
| revenuePeriod | `String` |
| poc | `BigDecimal` |
| signDate | `LocalDate` |
| immutable | `Boolean` |
| createdTime | `LocalDateTime` |
| createdBy | `String` |

## `psa_process_to_be_sent` — ProcessToBeSentDO
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/dal/dataobject/bpm/ProcessToBeSentDO.java`  
**使用它的规则：** RULE-287, RULE-315

| 字段 | 类型 |
|---|---|
| id | `Long` |
| businessId | `Long` |
| code | `String` |
| title | `String` |
| creator | `Long` |
| creatorName | `String` |
| deptName | `String` |
| postName | `String` |
| createTime | `LocalDateTime` |
| processParams | `String` |
| processBusinessKey | `String` |
| actionType | `String` |
| status | `Integer` |
| retryCount | `Integer` |
| lastError | `String` |
| updateTime | `LocalDateTime` |

## `psa_pm_budget` — BudgetDO
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/dal/dataobject/project/BudgetDO.java`  
**使用它的规则：** RULE-191, RULE-194

| 字段 | 类型 |
|---|---|
| id | `Long` |
| projectId | `Long` |
| hours | `BigDecimal` |
| humanDays | `BigDecimal` |
| amount | `BigDecimal` |
| profitRate | `BigDecimal` |
| laborCost | `BigDecimal` |
| opticsCost | `BigDecimal` |
| bmuCost | `BigDecimal` |
| safetyCost | `BigDecimal` |
| sealCost | `BigDecimal` |
| mediacyCost | `BigDecimal` |
| otherCost | `BigDecimal` |
| dailyWage | `String` |

## `psa_dept_u8c` — U8cDeptDO
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/dal/dataobject/u8c/U8cDeptDO.java`  
**使用它的规则：** RULE-086, RULE-104

| 字段 | 类型 |
|---|---|
| sort | `Integer` |
| deptId | `Long` |
| deptName | `String` |
| u8cCompanyCode | `String` |
| u8cCompany | `String` |
| u8cDeptCode | `String` |

## `psa_carry_batch` — CarryBatchDO
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/infrastructure/cost/projectcost/carry/CarryBatchDO.java`  
**使用它的规则：** RULE-128, RULE-315

| 字段 | 类型 |
|---|---|
| id | `Long` |
| batchNo | `String` |
| periodId | `Long` |
| companyCode | `String` |
| carryType | `Integer` |
| status | `Integer` |
| totalAmount | `BigDecimal` |
| entryCount | `Integer` |
| reversalOfBatchId | `Long` |
| confirmedBy | `Long` |
| confirmedAt | `LocalDateTime` |
| baselineCount | `Long` |
| baselineAmount | `BigDecimal` |
| processedCount | `Long` |
| processedAmount | `BigDecimal` |
| phase | `String` |
| version | `Integer` |
| lastError | `String` |
| startedAt | `LocalDateTime` |
| finishedAt | `LocalDateTime` |
| redReversalReason | `String` |
| redReversalRemark | `String` |
| deleted | `Boolean` |

## `psa_calendar` — CalendarDO
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/dal/dataobject/calendar/CalendarDO.java`  
**使用它的规则：** RULE-024, RULE-040

| 字段 | 类型 |
|---|---|
| id | `String` |
| datetime | `LocalDateTime` |
| workday | `Integer` |
| year | `Integer` |
| week | `Integer` |

## `bpm_process_instance_ext` — BpmProcessInstanceExtDO
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/dal/dataobject/bpm/BpmProcessInstanceExtDO.java`  
**使用它的规则：** RULE-289, RULE-304

| 字段 | 类型 |
|---|---|
| id | `Long` |
| startUserId | `Long` |
| name | `String` |
| processInstanceId | `String` |
| processDefinitionId | `String` |
| category | `String` |
| status | `Integer` |
| result | `Integer` |
| endTime | `LocalDateTime` |
| formVariables | `Map<String, Object>` |
| title | `String` |
| createTime | `LocalDateTime` |

## `(domain) RevenueConfirmation` — RevenueConfirmation
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/receivables/revenue/RevenueConfirmation.java`  
**使用它的规则：** RULE-185, RULE-213

| 字段 | 类型 |
|---|---|
| id | `Long` |
| code | `String` |
| clauseId | `Long` |
| deliverableId | `Long` |
| amount | `BigDecimal` |
| revenuePeriod | `String` |
| remark | `String` |
| status | `String` |
| submitterId | `Long` |
| submittedTime | `LocalDateTime` |
| approverId | `Long` |
| approvedTime | `LocalDateTime` |
| approvalNote | `String` |
| compliant | `Integer` |
| processId | `String` |
| revenueFactId | `Long` |
| evidence | `AcceptanceEvidence` |

## `(domain) QuotationLine` — QuotationLine
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/presales/quote/quotation/QuotationLine.java`  
**使用它的规则：** RULE-146, RULE-149

| 字段 | 类型 |
|---|---|
| id | `Long` |
| seq | `Integer` |
| productCode | `String` |
| productName | `String` |
| productCategory | `String` |
| businessType | `String` |
| quantity | `BigDecimal` |
| unitType | `String` |
| unit | `String` |
| unitPriceTax | `BigDecimal` |
| unitDiscount | `BigDecimal` |
| unitPriceNotax | `BigDecimal` |
| taxRate | `BigDecimal` |
| taxRateCode | `String` |
| taxAmount | `BigDecimal` |
| amountNotax | `BigDecimal` |
| amountTaxDisc | `BigDecimal` |
| remark | `String` |

## `(domain) ProgressTree` — ProgressTree
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/ProgressTree.java`  
**使用它的规则：** RULE-189, RULE-315

| 字段 | 类型 |
|---|---|
| projectId | `Long` |
| projectCode | `String` |
| wbsBudgetLocked | `Boolean` |
| wbsBudgetApprovalStatus | `String` |
| planned | `BigDecimal` |
| actual | `BigDecimal` |
| etc | `BigDecimal` |
| eac | `BigDecimal` |
| poc | `Integer` |
| budgetCost | `BigDecimal` |
| actualCost | `BigDecimal` |
| etcCost | `BigDecimal` |
| eacCost | `BigDecimal` |
| savingsCost | `BigDecimal` |
| costPoc | `Integer` |
| budgetPoc | `Integer` |
| clauses | `List<Clause>` |
| id | `Long` |
| code | `String` |
| clauseNo | `String` |
| title | `String` |
| clauseType | `String` |
| obsolete | `Boolean` |
| planned | `BigDecimal` |
| actual | `BigDecimal` |
| etc | `BigDecimal` |
| eac | `BigDecimal` |
| poc | `Integer` |
| budgetCost | `BigDecimal` |
| actualCost | `BigDecimal` |
| etcCost | `BigDecimal` |
| eacCost | `BigDecimal` |
| savingsCost | `BigDecimal` |
| costPoc | `Integer` |
| budgetPoc | `Integer` |
| deliverables | `List<Deliverable>` |
| planStartDate | `LocalDate` |
| planEndDate | `LocalDate` |
| trueStartDate | `LocalDate` |
| trueEndDate | `LocalDate` |
| id | `Long` |
| code | `String` |
| title | `String` |
| obsolete | `Boolean` |
| fromBaseline | `Boolean` |
| migratedFromLegacy | `boolean` |
| execStatus | `String` |
| overrun | `Boolean` |
| submissionStatus | `String` |
| submissionReturnedToInitiator | `boolean` |
| reworkSourceDeliverableId | `Long` |
| reworkReason | `String` |
| planned | `BigDecimal` |
| actual | `BigDecimal` |
| etc | `BigDecimal` |
| eac | `BigDecimal` |
| poc | `Integer` |
| budgetCost | `BigDecimal` |
| actualCost | `BigDecimal` |
| etcCost | `BigDecimal` |
| …（另 41 个字段） | |

## `(domain) PostingRuleEngine` — PostingRuleEngine
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/voucher/PostingRuleEngine.java`  
**使用它的规则：** RULE-078, RULE-079

| 字段 | 类型 |
|---|---|
| DYNAMIC_SUBJECT_PREFIX | `static final String` |
| AUX_DIM_DEPT | `static final String` |
| AUX_DIM_SUPPLIER | `static final String` |
| KEY_SEP | `static final String` |
| strictBalance | `boolean` |
| voucherGroups | `Map<String, DraftAgg>` |
| ref | `VoucherDraft` |
| amountByLine | `Map<String, BigDecimal>` |
| repByLine | `Map<String, VoucherDraftLine>` |

## `(domain) OhAllocationCalculator` — OhAllocationCalculator
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/allocation/OhAllocationCalculator.java`  
**使用它的规则：** RULE-068, RULE-069

| 字段 | 类型 |
|---|---|
| DEPT_ROLE_DELIVERY_OWN | `static final int` |
| DEPT_ROLE_SERVICE | `static final int` |
| DEPT_ROLE_PUBLIC | `static final int` |
| EXPENSE_NATURE_MGMT | `static final int` |
| RATIO_SCALE | `static final int` |
| AMOUNT_SCALE | `static final int` |
| ledgerId | `Long` |
| projectId | `Long` |
| termNumber | `String` |
| deliveryDeptId | `Long` |
| notaxAmount | `BigDecimal` |
| timesheet | `boolean` |
| deptRatio | `BigDecimal` |
| companyRatio | `BigDecimal` |
| deptMgmt | `BigDecimal` |
| deptSales | `BigDecimal` |
| companyMgmt | `BigDecimal` |
| companySales | `BigDecimal` |
| deptTail | `boolean` |
| companyTail | `boolean` |
| ownOhMgmt | `BigDecimal` |
| ownOhSales | `BigDecimal` |
| serviceOhMgmt | `BigDecimal` |
| serviceOhSales | `BigDecimal` |
| deptBaseTotal | `BigDecimal` |
| companyOhMgmt | `BigDecimal` |
| companyOhSales | `BigDecimal` |
| companyBaseTotal | `BigDecimal` |
| deptBuckets | `Map<Long, DeptBucket>` |
| basisByDept | `Map<Long, BigDecimal>` |
| companyBasisTotal | `BigDecimal` |
| pickedPoolLineIds | `Set<Long>` |
| ownOhMgmt | `BigDecimal` |
| ownOhSales | `BigDecimal` |
| serviceOhMgmt | `BigDecimal` |
| serviceOhSales | `BigDecimal` |
| deptBaseTotal | `BigDecimal` |

## `(domain) LaborReclassDiffCalculator` — LaborReclassDiffCalculator
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/laborreclass/LaborReclassDiffCalculator.java`  
**使用它的规则：** RULE-076, RULE-105

| 字段 | 类型 |
|---|---|
| MATCH_EPS | `static final BigDecimal` |
| SOURCE_TIMESHEET | `static final int` |
| NO_DEPT | `static final String` |
| UNMAPPED | `static final String` |

## `(domain) LaborCostHeadroom` — LaborCostHeadroom
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/LaborCostHeadroom.java`  
**使用它的规则：** RULE-241, RULE-242

| 字段 | 类型 |
|---|---|
| budgetLaborCost | `BigDecimal` |
| eacLaborCost | `BigDecimal` |
| taskCostRate | `BigDecimal` |

## `(domain) CostTemplate` — CostTemplate
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/presales/quote/costestimate/CostTemplate.java`  
**使用它的规则：** RULE-151, RULE-165

| 字段 | 类型 |
|---|---|
| CODE_FORMATTER | `static final DateTimeFormatter` |
| NODE_TYPE_CLAUSE | `static final String` |
| NODE_TYPE_DELIVERABLE | `static final String` |
| NODE_TYPE_WORKPKG | `static final String` |
| id | `Long` |
| code | `String` |
| name | `String` |
| description | `String` |
| category | `String` |
| active | `Boolean` |
| nodes | `List<CostTemplateNode>` |
| clauseCount | `long` |
| deliverableCount | `long` |
| workpkgCount | `long` |
| totalHours | `BigDecimal` |

## `psa_wip_carry_forward_data_works` — CarryForwardDataWorksDO
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/dal/dataobject/accounting/CarryForwardDataWorksDO.java`  
**使用它的规则：** RULE-197

| 字段 | 类型 |
|---|---|
| id | `Long` |
| costU8cCompanyCode | `String` |
| wipU8cCompanyCode | `String` |
| dataId | `Long` |
| costId | `Long` |
| wipId | `Long` |
| wipSecondId | `Long` |
| termNumber | `String` |
| type | `Integer` |
| costDeptId | `Long` |
| wipDeptId | `Long` |
| writeOffFlag | `Integer` |
| costWriteOffFlag | `Integer` |
| amount | `BigDecimal` |
| projectId | `Long` |
| subjectCode | `String` |
| cvCode | `String` |

## `psa_reliable_delivery_task` — ReliableDeliveryTaskDO
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/infrastructure/platform/integration/reliabledelivery/ReliableDeliveryTaskDO.java`  
**使用它的规则：** RULE-315

| 字段 | 类型 |
|---|---|
| id | `Long` |
| targetSystem | `String` |
| deliveryScene | `String` |
| deliveryChannel | `Integer` |
| endpointKey | `String` |
| businessType | `String` |
| businessId | `Long` |
| businessKey | `String` |
| idempotencyKey | `String` |
| payloadMode | `Integer` |
| payloadJson | `String` |
| status | `Integer` |
| priority | `Integer` |
| retryCount | `Integer` |
| maxRetryCount | `Integer` |
| nextRetryAt | `LocalDateTime` |
| firstAttemptAt | `LocalDateTime` |
| lastAttemptAt | `LocalDateTime` |
| succeededAt | `LocalDateTime` |
| claimOwner | `String` |
| claimToken | `String` |
| claimedAt | `LocalDateTime` |
| claimExpiresAt | `LocalDateTime` |
| externalReceiptNo | `String` |
| lastError | `String` |
| version | `Integer` |
| initialDeliveryAttempt | `boolean` |
| deleted | `Boolean` |

## `psa_pm_cuvalue` — CuValueDO
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/dal/dataobject/project/CuValueDO.java`  
**使用它的规则：** RULE-184

| 字段 | 类型 |
|---|---|
| id | `Long` |
| projectId | `Long` |
| code | `String` |
| termNumber | `String` |
| termDepict | `String` |
| deptId | `Long` |
| completeDate | `LocalDateTime` |
| completeAmount | `BigDecimal` |
| creator | `Long` |
| createTime | `LocalDateTime` |
| writeOffFlag | `Integer` |
| rate | `BigDecimal` |
| tax | `BigDecimal` |
| noTaxAmount | `BigDecimal` |
| sourceSystem | `Integer` |
| type | `Integer` |
| companyCode | `String` |
| originCode | `String` |

## `psa_pl_ledger` — PlLedgerDO
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/infrastructure/cost/projectcost/writeoff/PlLedgerDO.java`  
**使用它的规则：** RULE-080

| 字段 | 类型 |
|---|---|
| id | `Long` |
| sourceCostLedgerId | `Long` |
| sourceWipEntryId | `Long` |
| sourceWipMovementId | `Long` |
| triggerType | `Integer` |
| fromWipStage | `Integer` |
| fromWipStatus | `Integer` |
| sourceType | `Integer` |
| sourceId | `Long` |
| companyCode | `String` |
| projectId | `Long` |
| termNumber | `String` |
| plAccountCode | `String` |
| plType | `Integer` |
| costOrgId | `Long` |
| costCenterId | `Long` |
| businessDate | `LocalDate` |
| periodId | `Long` |
| amount | `BigDecimal` |
| notaxAmount | `BigDecimal` |
| carryBatchId | `Long` |
| reversalOfId | `Long` |
| sourceCode | `String` |
| version | `Integer` |
| deleted | `Boolean` |

## `psa_ohproject_detail` — OhProjectDetailDO
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/dal/dataobject/ohproject/OhProjectDetailDO.java`  
**使用它的规则：** RULE-038

| 字段 | 类型 |
|---|---|
| id | `Long` |
| deptId | `Long` |
| code | `String` |
| name | `String` |
| taskCode | `String` |
| taskName | `String` |

## `psa_oa_account_u8c` — OaAccountU8cDO
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/dal/dataobject/accounting/OaAccountU8cDO.java`  
**使用它的规则：** RULE-104

| 字段 | 类型 |
|---|---|
| payTypeId | `Long` |
| feeType | `String` |
| costTypeCode | `String` |
| costTypeName | `String` |
| secU8cAccountCode | `String` |
| secU8cAccountName | `String` |
| u8cAccountCode | `String` |
| u8cAccountName | `String` |

## `psa_deliverable` — DeliverableDO
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/dal/dataobject/project/DeliverableDO.java`  
**使用它的规则：** RULE-226

| 字段 | 类型 |
|---|---|
| id | `Long` |
| code | `String` |
| clauseId | `Long` |
| sourceDeliverableId | `Long` |
| sourceBudgetDeliverableId | `Long` |
| reworkSourceDeliverableId | `Long` |
| reworkReason | `String` |
| title | `String` |
| scene | `String` |
| amount | `BigDecimal` |
| dlvStatus | `String` |
| lifecycle | `String` |
| createdTime | `LocalDateTime` |
| createdBy | `String` |
| updatedTime | `LocalDateTime` |
| updatedBy | `String` |
| obsolete | `Boolean` |
| obsoleteRevisionId | `Long` |
| deleted | `Integer` |

## `psa_cost_recon_disposition` — ReconDispositionDO
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/infrastructure/cost/projectcost/recon/ReconDispositionDO.java`  
**使用它的规则：** RULE-099

| 字段 | 类型 |
|---|---|
| id | `Long` |
| periodId | `Long` |
| companyCode | `String` |
| sourceId | `Long` |
| processType | `Integer` |
| costTypeCode | `String` |
| diffType | `String` |
| anomalyType | `String` |
| viewAmount | `BigDecimal` |
| sourceAmount | `BigDecimal` |
| diffAmount | `BigDecimal` |
| dispositionStatus | `String` |
| adjustAmount | `BigDecimal` |
| deferredToPeriodId | `Long` |
| payloadJson | `String` |

## `psa_bpm_mq_outbox` — BpmMqOutboxDO
**位置：** `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm/dal/dataobject/mq/BpmMqOutboxDO.java`  
**使用它的规则：** RULE-303

| 字段 | 类型 |
|---|---|
| id | `Long` |
| taskId | `String` |
| eventType | `String` |
| businessKey | `String` |
| paramsId | `Long` |
| processInstanceId | `String` |
| result | `Integer` |
| payload | `String` |
| status | `Integer` |
| retryCount | `Integer` |
| nextRetryTime | `LocalDateTime` |
| lastError | `String` |
| createTime | `LocalDateTime` |
| updateTime | `LocalDateTime` |

## `(domain) WriteOffLine` — WriteOffLine
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/carry/WriteOffLine.java`  
**使用它的规则：** RULE-080

| 字段 | 类型 |
|---|---|
| COMPLETED_POC | `static final int` |
| form | `WriteOffForm` |
| wipEntryId | `Long` |
| costLedgerId | `Long` |
| projectId | `Long` |
| termNumber | `String` |
| notaxAmount | `BigDecimal` |
| pairedCuValueId | `Long` |
| pairedCuValueCode | `String` |
| fromWipStage | `Integer` |
| toWipStage | `Integer` |
| fromWipStatus | `Integer` |
| toWipStatus | `Integer` |
| contractBackedSnap | `Boolean` |
| pocSnap | `Integer` |
| selectReason | `Integer` |

## `(domain) TimesheetElementRounding` — TimesheetElementRounding
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/ingest/TimesheetElementRounding.java`  
**使用它的规则：** RULE-075

| 字段 | 类型 |
|---|---|
| salaryNotax | `BigDecimal` |
| socialNotax | `BigDecimal` |
| fundNotax | `BigDecimal` |
| total | `BigDecimal` |

## `(domain) SalaryMemberFact` — SalaryMemberFact
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/prepostingcheck/SalaryMemberFact.java`  
**使用它的规则：** RULE-107

| 字段 | 类型 |
|---|---|
| personId | `Long` |
| memberCode | `String` |
| memberName | `String` |
| calcRows | `int` |
| calcPositiveRows | `int` |
| manualRows | `int` |
| salary | `BigDecimal` |
| social | `BigDecimal` |
| fund | `BigDecimal` |

## `(domain) QuotationSeal` — QuotationSeal
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/presales/quote/quotation/QuotationSeal.java`  
**使用它的规则：** RULE-164

| 字段 | 类型 |
|---|---|
| id | `Long` |
| docName | `String` |
| sealCompany | `String` |
| sealCompanyCode | `String` |
| sealType | `String` |
| sealTypeCode | `String` |
| copyType | `String` |
| copyTypeCode | `String` |
| copies | `Integer` |
| remark | `String` |

## `(domain) ProjectBillingView` — ProjectBillingView
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/receivables/billing/ProjectBillingView.java`  
**使用它的规则：** RULE-199

| 字段 | 类型 |
|---|---|
| rows | `List<Row>` |
| serial | `Integer` |
| termCode | `String` |
| name | `String` |
| downPay | `boolean` |
| amount | `BigDecimal` |
| confirmed | `BigDecimal` |
| invoiced | `BigDecimal` |
| collected | `BigDecimal` |

## `(domain) PositionRate` — PositionRate
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/presales/quote/quotation/PositionRate.java`  
**使用它的规则：** RULE-139

| 字段 | 类型 |
|---|---|
| roleCode | `String` |
| billRate | `BigDecimal` |
| costRate | `BigDecimal` |

## `(domain) OtherCosts` — OtherCosts
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/presales/quote/quotation/OtherCosts.java`  
**使用它的规则：** RULE-143

| 字段 | 类型 |
|---|---|
| safetyEval | `BigDecimal` |
| lightingEval | `BigDecimal` |
| windowMachine | `BigDecimal` |
| drawingSign | `BigDecimal` |
| otherOutsource | `BigDecimal` |
| infoService | `BigDecimal` |
| travel | `BigDecimal` |
| printing | `BigDecimal` |

## `(domain) LedgerBasisRow` — LedgerBasisRow
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/allocation/LedgerBasisRow.java`  
**使用它的规则：** RULE-066

| 字段 | 类型 |
|---|---|
| ledgerId | `Long` |
| projectId | `Long` |
| termNumber | `String` |
| deliveryDeptId | `Long` |
| sourceType | `int` |
| notaxAmount | `BigDecimal` |

## `(domain) CostTemplateNode` — CostTemplateNode
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/presales/quote/costestimate/CostTemplateNode.java`  
**使用它的规则：** RULE-151

| 字段 | 类型 |
|---|---|
| nodeType | `String` |
| nodeNo | `String` |
| title | `String` |
| clauseType | `String` |
| roleCode | `String` |
| hours | `BigDecimal` |
| amount | `BigDecimal` |
| sortNo | `Integer` |
| children | `List<CostTemplateNode>` |

## `(domain) CostEstimateNode` — CostEstimateNode
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/presales/quote/quotation/CostEstimateNode.java`  
**使用它的规则：** RULE-141

| 字段 | 类型 |
|---|---|
| id | `Long` |
| nodeType | `String` |
| nodeNo | `String` |
| clauseCode | `String` |
| name | `String` |
| clauseType | `String` |
| roleCode | `String` |
| hours | `BigDecimal` |
| billRate | `BigDecimal` |
| costRate | `BigDecimal` |
| amount | `BigDecimal` |
| sortNo | `Integer` |
| children | `List<CostEstimateNode>` |

## `(domain) CostAllocationBatch` — CostAllocationBatch
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/allocation/CostAllocationBatch.java`  
**使用它的规则：** RULE-120

| 字段 | 类型 |
|---|---|
| id | `Long` |
| periodId | `Long` |
| companyCode | `String` |
| batchNo | `String` |
| batchType | `Integer` |
| operatorId | `Long` |
| status | `Integer` |
| progress | `int` |
| companyOhMgmt | `BigDecimal` |
| companyOhSales | `BigDecimal` |
| companyBaseTotal | `BigDecimal` |
| startedAt | `LocalDateTime` |
| finishedAt | `LocalDateTime` |
| lastError | `String` |
| version | `Integer` |

## `(domain) ClauseRevenueQuota` — ClauseRevenueQuota
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/receivables/revenue/ClauseRevenueQuota.java`  
**使用它的规则：** RULE-184

| 字段 | 类型 |
|---|---|
| clauseAmount | `BigDecimal` |
| confirmedAmount | `BigDecimal` |
| pendingAmount | `BigDecimal` |

## `(domain) CarryBatch` — CarryBatch
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/carry/CarryBatch.java`  
**使用它的规则：** RULE-122

| 字段 | 类型 |
|---|---|
| id | `Long` |
| batchNo | `String` |
| periodId | `Long` |
| companyCode | `String` |
| carryType | `Integer` |
| status | `CarryBatchStatusEnum` |
| totalAmount | `BigDecimal` |
| entryCount | `Integer` |
| baselineCount | `long` |
| baselineAmount | `BigDecimal` |
| processedCount | `long` |
| processedAmount | `BigDecimal` |
| phase | `CarryBatchPhaseEnum` |
| reversalOfBatchId | `Long` |
| confirmedBy | `Long` |
| confirmedAt | `LocalDateTime` |
| lastError | `String` |
| startedAt | `LocalDateTime` |
| finishedAt | `LocalDateTime` |
| version | `Integer` |
| LAST_ERROR_MAX | `static final int` |

## `(domain) BudgetDeliverable` — BudgetDeliverable
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/BudgetDeliverable.java`  
**使用它的规则：** RULE-181

| 字段 | 类型 |
|---|---|
| id | `Long` |
| code | `String` |
| title | `String` |
| amount | `BigDecimal` |
| obsolete | `boolean` |
| sourceDeliverableId | `Long` |
| tasks | `List<TaskPackage>` |

## `(domain) BudgetClause` — BudgetClause
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/delivery/project/BudgetClause.java`  
**使用它的规则：** RULE-181

| 字段 | 类型 |
|---|---|
| id | `Long` |
| code | `String` |
| clauseNo | `String` |
| title | `String` |
| clauseType | `String` |
| amount | `BigDecimal` |
| termCode | `String` |
| obsolete | `boolean` |
| deliverables | `List<BudgetDeliverable>` |

## `(domain) AnnualBonusSplitCalculator` — AnnualBonusSplitCalculator
**位置：** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/ingest/AnnualBonusSplitCalculator.java`  
**使用它的规则：** RULE-073

| 字段 | 类型 |
|---|---|
| projectId | `Long` |
| termNumber | `String` |
| hours | `BigDecimal` |
| amount | `BigDecimal` |
| notaxAmount | `BigDecimal` |
