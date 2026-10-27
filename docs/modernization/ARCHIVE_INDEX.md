# 归档索引（ARCHIVE_INDEX）

D 类零读者批（定稿 1.11，O16，GOV-1023-FV2）已从 main 移出，原文件留在只读归档分支 `archive/2026-10`（建于提交 `d288b2b594f1`，之后不再改动）。
清单与判定依据：O16 归档清单（调度 2026-10-26 签）；判定口径：代码、配置、CI、生成器、在用文档里零读者，ADR 里只作出处的目录名引用不算读者（这类出处仍可按下表在归档分支上找到，ADR 正文不改）。

取回某个文件：`git show archive/2026-10:<路径>`；整目录：`git checkout archive/2026-10 -- <目录>`。

合计 459 个文件，22.4 MB（closeout_check `--local` 仍读的 26 个文件已留在 main，见 O16 清单表四）。

| 原目录（归档分支上同一路径） | 文件数 | 大小 KB | 文件 |
|---|---:|---:|---|
| `docs/modernization/` | 5 | 6356.4 | REPORT.html、TOPOLOGY.html、extract-rules.modules.json、live_code.json、live_code_prod.json |
| `docs/modernization/architecture-guardrails/backend/` | 2 | 6.3 | data-dictionary.sample.md、pom.xml |
| `docs/modernization/architecture-guardrails/backend/architecture/` | 3 | 1.9 | legacy-shape-exceptions.txt、module-dependencies.txt、ticket-issuers-allowlist.txt |
| `docs/modernization/architecture-guardrails/backend/glossary/` | 1 | 15.5 | terms.yaml |
| `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/` | 2 | 1.2 | OneApplication.java、SubdomainModuleDetection.java |
| `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/cost/timesheet/` | 1 | 0.2 | package-info.java |
| `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/cost/timesheet/api/` | 2 | 0.4 | TimesheetId.java、package-info.java |
| `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/cost/timesheet/application/` | 4 | 3.9 | SubmissionResult.java、SubmitTimesheet.java、SubmitTimesheetCommand.java、TimesheetError.java |
| `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/cost/timesheet/domain/` | 2 | 0.5 | Timesheet.java、TimesheetStatus.java |
| `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/cost/timesheet/events/` | 3 | 0.6 | TimesheetApproved.java、TimesheetSubmitted.java、package-info.java |
| `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/cost/timesheet/web/` | 3 | 2.7 | SubmitTimesheetRequest.java、SubmitTimesheetResponse.java、TimesheetController.java |
| `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/delivery/execution/` | 1 | 0.2 | package-info.java |
| `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/delivery/execution/api/` | 3 | 0.8 | WorkPackageId.java、WorkPackageQueries.java、package-info.java |
| `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/delivery/execution/application/` | 1 | 0.9 | GetRemainingHours.java |
| `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/delivery/execution/domain/` | 3 | 0.8 | WorkPackage.java、WorkPackageRepository.java、WorkPackageStatus.java |
| `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/delivery/execution/infrastructure/persistence/` | 1 | 1.5 | JdbcWorkPackageRepository.java |
| `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/kernel/` | 8 | 4.6 | ErrorCode.java、Hours.java、LegalEntityId.java、Money.java、PublicEndpoint.java、UserId.java、… |
| `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/platform/approval/` | 1 | 0.2 | package-info.java |
| `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/platform/approval/api/` | 5 | 1.8 | ApprovalInstanceId.java、ApprovalProcesses.java、ApprovalRequest.java、ProcessKey.java、package-info.java |
| `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/platform/approval/infrastructure/flowable/` | 1 | 1.7 | FlowableApprovalProcesses.java |
| `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/platform/identity/` | 1 | 0.2 | package-info.java |
| `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/platform/identity/api/` | 3 | 1.6 | CurrentUser.java、Permission.java、package-info.java |
| `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/platform/identity/infrastructure/security/` | 2 | 2.4 | SecurityConfiguration.java、SessionCurrentUser.java |
| `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/platform/numbering/` | 1 | 0.2 | package-info.java |
| `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/platform/numbering/api/` | 5 | 1.7 | DocumentNumber.java、DocumentNumbers.java、DocumentType.java、ResetPolicy.java、package-info.java |
| `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/platform/numbering/application/` | 3 | 3.6 | CheckCounterCapacity.java、ConfigureNumberingRule.java、IssueDocumentNumber.java |
| `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/platform/numbering/domain/` | 3 | 5.4 | NumberingPattern.java、NumberingRule.java、NumberingRuleRepository.java |
| `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/platform/numbering/infrastructure/persistence/` | 1 | 4.2 | JdbcNumberingRuleRepository.java |
| `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/platform/numbering/infrastructure/scheduling/` | 1 | 1.8 | CounterCapacityJob.java |
| `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/platform/numbering/web/` | 2 | 1.7 | NumberingRuleController.java、NumberingRuleRequest.java |
| `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/platform/scheduling/` | 1 | 0.3 | package-info.java |
| `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/platform/scheduling/infrastructure/scheduling/` | 1 | 1.2 | SchedulingConfiguration.java |
| `docs/modernization/architecture-guardrails/backend/src/main/resources/` | 1 | 0.9 | application.properties |
| `docs/modernization/architecture-guardrails/backend/src/main/resources/db/migration/delivery_execution/` | 1 | 1.3 | V20261014100000__delivery_execution_create_work_packages.sql |
| `docs/modernization/architecture-guardrails/backend/src/main/resources/db/migration/platform_numbering/` | 1 | 3.0 | V20261014100100__platform_numbering_create_numbering.sql |
| `docs/modernization/architecture-guardrails/backend/src/main/resources/db/migration/platform_scheduling/` | 1 | 1.2 | V20261014100200__platform_scheduling_create_scheduler_locks.sql |
| `docs/modernization/architecture-guardrails/backend/src/main/resources/processes/` | 1 | 0.7 | cost_timesheet_approval.bpmn20.xml |
| `docs/modernization/architecture-guardrails/backend/src/test/java/com/forcitis/one/` | 9 | 143.6 | ApplicationIntegrationTest.java、ArchitectureRulesTest.java、DdlNamingTest.java、Glossary.java、GlossaryTest.java、LegacyShapeTest.java、… |
| `docs/modernization/architecture-guardrails/backend/src/test/java/com/forcitis/one/kernel/` | 1 | 1.1 | MoneyTest.java |
| `docs/modernization/architecture-guardrails/backend/src/test/resources/` | 1 | 0.0 | init-schemas.sql |
| `docs/modernization/architecture-guardrails/backend/src/test/resources/db/testdata/` | 1 | 0.1 | V20261014190000__testdata_work_packages.sql |
| `docs/modernization/architecture-guardrails/ci/` | 2 | 5.7 | assertion_drift.py、migrations_immutable.py |
| `docs/modernization/architecture-guardrails/frontend/` | 8 | 161.5 | eslint.config.js、index.html、package.json、pnpm-lock.yaml、pnpm-workspace.yaml、stylelint.config.js、… |
| `docs/modernization/architecture-guardrails/frontend-vue/` | 9 | 149.0 | eslint.config.js、index.html、negative-lint.log、package.json、pnpm-lock.yaml、pnpm-workspace.yaml、… |
| `docs/modernization/architecture-guardrails/frontend-vue/scripts/` | 1 | 1.4 | check-structure.mjs |
| `docs/modernization/architecture-guardrails/frontend-vue/src/api/generated/` | 2 | 0.9 | client.ts、schema.ts |
| `docs/modernization/architecture-guardrails/frontend-vue/src/app/` | 3 | 0.4 | App.vue、env.d.ts、main.ts |
| `docs/modernization/architecture-guardrails/frontend-vue/src/modules/delivery/` | 1 | 0.3 | index.ts |
| `docs/modernization/architecture-guardrails/frontend-vue/src/modules/delivery/api/` | 1 | 0.3 | task-package-api.ts |
| `docs/modernization/architecture-guardrails/frontend-vue/src/modules/delivery/composables/` | 1 | 0.5 | use-remaining-hours.ts |
| `docs/modernization/architecture-guardrails/frontend-vue/src/modules/delivery/model/` | 1 | 0.1 | task-package.ts |
| `docs/modernization/architecture-guardrails/frontend-vue/src/modules/delivery/pages/` | 1 | 0.1 | ProjectProgressPage.vue |
| `docs/modernization/architecture-guardrails/frontend-vue/src/modules/timesheet/` | 1 | 0.1 | index.ts |
| `docs/modernization/architecture-guardrails/frontend-vue/src/modules/timesheet/api/` | 1 | 0.3 | timesheet-api.ts |
| `docs/modernization/architecture-guardrails/frontend-vue/src/modules/timesheet/components/` | 1 | 0.7 | TimeEntryRow.vue |
| `docs/modernization/architecture-guardrails/frontend-vue/src/modules/timesheet/composables/` | 1 | 0.4 | use-timesheet.ts |
| `docs/modernization/architecture-guardrails/frontend-vue/src/modules/timesheet/model/` | 2 | 0.7 | timesheet-status.ts、timesheet.ts |
| `docs/modernization/architecture-guardrails/frontend-vue/src/modules/timesheet/pages/` | 1 | 1.4 | TimesheetPage.vue |
| `docs/modernization/architecture-guardrails/frontend-vue/src/modules/timesheet/stores/` | 1 | 0.2 | use-timesheet-filter-store.ts |
| `docs/modernization/architecture-guardrails/frontend-vue/src/shared/api/` | 1 | 0.4 | http-client.ts |
| `docs/modernization/architecture-guardrails/frontend-vue/src/shared/design-system/` | 2 | 4.7 | index.ts、tokens.css |
| `docs/modernization/architecture-guardrails/frontend-vue/src/shared/lib/` | 2 | 0.6 | format-hours.ts、format-money.ts |
| `docs/modernization/architecture-guardrails/frontend-vue/src/shared/ui/` | 5 | 3.6 | AppButton.vue、MoneyText.vue、PageTitle.vue、StatusBadge.vue、index.ts |
| `docs/modernization/architecture-guardrails/frontend/scripts/` | 2 | 4.2 | check-lint-config.mjs、check-structure.mjs |
| `docs/modernization/architecture-guardrails/frontend/src/api/generated/` | 2 | 0.8 | client.ts、schema.ts |
| `docs/modernization/architecture-guardrails/frontend/src/app/` | 2 | 0.7 | main.tsx、routes.tsx |
| `docs/modernization/architecture-guardrails/frontend/src/modules/delivery/` | 1 | 0.2 | index.ts |
| `docs/modernization/architecture-guardrails/frontend/src/modules/delivery/api/` | 1 | 0.3 | task-package-api.ts |
| `docs/modernization/architecture-guardrails/frontend/src/modules/delivery/hooks/` | 1 | 0.4 | use-remaining-hours.ts |
| `docs/modernization/architecture-guardrails/frontend/src/modules/delivery/model/` | 1 | 0.1 | task-package.ts |
| `docs/modernization/architecture-guardrails/frontend/src/modules/delivery/pages/` | 1 | 0.1 | project-progress-page.tsx |
| `docs/modernization/architecture-guardrails/frontend/src/modules/timesheet/` | 1 | 0.1 | index.ts |
| `docs/modernization/architecture-guardrails/frontend/src/modules/timesheet/components/` | 2 | 0.4 | time-entry-row.css、time-entry-row.tsx |
| `docs/modernization/architecture-guardrails/frontend/src/modules/timesheet/model/` | 1 | 0.2 | timesheet-status.ts |
| `docs/modernization/architecture-guardrails/frontend/src/modules/timesheet/pages/` | 1 | 0.6 | timesheet-page.tsx |
| `docs/modernization/architecture-guardrails/frontend/src/shared/design-system/` | 4 | 8.0 | design-system-provider.tsx、index.ts、theme.ts、tokens.css |
| `docs/modernization/architecture-guardrails/frontend/src/shared/lib/` | 1 | 0.1 | format-hours.ts |
| `docs/modernization/architecture-guardrails/frontend/src/shared/ui/` | 4 | 1.9 | button.tsx、page-title.tsx、status-badge.css、status-badge.tsx |
| `docs/modernization/architecture/proposals/` | 1 | 328.4 | DMN_FIT_RESEARCH.md |
| `docs/modernization/architecture/proposals/closeout/` | 34 | 551.0 | ANCHORING_PROPOSALS.md、ANCHORING_REPORT.md、ANCHORING_ROUND2.md、B31P_DESIGNER_DEFAULTS.md、B31P_EVIDENCE_SPEC.md、BURDEN_ANALYSIS_PLAN.md、… |
| `docs/modernization/architecture/proposals/closeout/finance/` | 16 | 270.1 | ANSWERS.md、BONUS_OVERTIME_COSTING.md、EXPENSE_COVERAGE.md、LEGAL_ENTITY_COSTING.md、OBJECTS.md、PAYROLL_REISSUE_SEAM.md、… |
| `docs/modernization/architecture/proposals/closeout/finance/bpmn/` | 29 | 777.8 | PRC-100.bpmn、PRC-101.bpmn、PRC-102.bpmn、PRC-104.bpmn、PRC-105.bpmn、PRC-112.bpmn、… |
| `docs/modernization/architecture/proposals/closeout/finance/bpmn/sample/` | 1 | 25.7 | PRC-87.hand.bpmn |
| `docs/modernization/architecture/proposals/closeout/people/` | 16 | 499.8 | DATA_MODEL.md、OBJECT_LAYER.md、PAGE_IMPACT.md、PAYROLL_LINE_PAYMENT.md、QC-B-31.md、QC-OBJECTS.md、… |
| `docs/modernization/architecture/proposals/closeout/people/b31-rules/` | 6 | 173.0 | B31_ADM02_BLUEPRINT.md、B31_CONTRACT.md、B31_PLATFORM_CONSTANTS.md、B31_RULES.yaml、B31_TERM_GAPS.md、DDL_DRAFT_CARD_A.sql |
| `docs/modernization/architecture/proposals/closeout/people/bpmn/` | 3 | 43.4 | PRC-137.bpmn、PRC-83.bpmn、PRC-84.bpmn |
| `docs/modernization/architecture/proposals/closeout/people/bpmn/tools/` | 2 | 20.6 | bpmngen.py、make_people_sample.py |
| `docs/modernization/architecture/proposals/closeout/people/parts/` | 31 | 546.1 | b15data.DATA_MODEL.md、b15data.data-model.yaml、b31.DATA_MODEL.md、b31.commands.yaml、b31.data-model.yaml、b31.events.yaml、… |
| `docs/modernization/architecture/proposals/closeout/pilot/` | 1 | 1.9 | ACCEPTANCE.md |
| `docs/modernization/architecture/proposals/closeout/rename/` | 9 | 4278.8 | RENAME_A.md、RENAME_A.yaml、RENAME_B.md、RENAME_B.yaml、RENAME_C.md、RENAME_C.yaml、… |
| `docs/modernization/architecture/proposals/closeout/revenue/` | 18 | 304.8 | CODEX_A01.md、CODEX_C7.md、C_DEFAULTS.md、DEFAULT_PAYMENT_TERM.md、OBJECT_LAYER.md、Q04_STORAGE.md、… |
| `docs/modernization/architecture/proposals/closeout/revenue/bpmn/` | 56 | 1111.4 | PRC-03.bpmn、PRC-07.bpmn、PRC-08.bpmn、PRC-13.bpmn、PRC-140.bpmn、PRC-141.bpmn、… |
| `docs/modernization/architecture/proposals/closeout/revenue/legal-entity/` | 3 | 10.1 | LE_CONTRACT_AGREEMENT.md、LE_DELIVERY_EXECUTION.md、LE_RECEIVABLES_WIP.md |
| `docs/modernization/architecture/proposals/closeout/revenue/tools/` | 1 | 1.1 | bpmn_gen.suggested.patch |
| `docs/modernization/architecture/proposals/closeout/rulings/` | 3 | 62.0 | S-31_FINANCE_SQ01-33.md、S-43_OBJECT_CLASSIFICATION.md、S-43_exemptions.draft.yaml |
| `docs/modernization/architecture/ssot/i09-acceptance/` | 1 | 194.4 | GAPS.md |
| `docs/modernization/data/` | 1 | 8.5 | build_table_inventory.py |
| `docs/modernization/glossary/answers/` | 1 | 104.6 | 2026-10-15-acl-round3.json |
| `docs/modernization/glossary/arch-naming/` | 1 | 4939.5 | N4_COVERAGE.md |
| `docs/modernization/glossary/b2/` | 2 | 14.7 | AGG_PREJUDGE.md、GL_RENAME_CARDS.md |
| `docs/modernization/glossary/gate/` | 3 | 33.0 | build_gate_page.py、export_decisions.py、page-template.html |
| `docs/modernization/glossary/round3/` | 8 | 600.8 | build_round3_decisions.py、build_round3_page.py、export_decisions.py、inventory.py、page-template.html、round3-items.json、… |
| `docs/modernization/map/` | 4 | 512.3 | add_desc.py、backend.json、mini.json、pc.json |
| `docs/modernization/map/packets/` | 39 | 358.0 | bpm_service_task.txt、framework_common.txt、fst_application_cost.txt、fst_application_delivery.txt、fst_application_expense.txt、fst_controller_admin_cost.txt、… |
| `docs/modernization/quality/` | 3 | 208.5 | CI_TIMING_REVIEW.md、GOVERNANCE_RESEARCH.md、TEST_IMPACT_RESEARCH.md |

## 逐文件

- `docs/modernization/REPORT.html`
- `docs/modernization/TOPOLOGY.html`
- `docs/modernization/architecture-guardrails/backend/architecture/legacy-shape-exceptions.txt`
- `docs/modernization/architecture-guardrails/backend/architecture/module-dependencies.txt`
- `docs/modernization/architecture-guardrails/backend/architecture/ticket-issuers-allowlist.txt`
- `docs/modernization/architecture-guardrails/backend/data-dictionary.sample.md`
- `docs/modernization/architecture-guardrails/backend/glossary/terms.yaml`
- `docs/modernization/architecture-guardrails/backend/pom.xml`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/OneApplication.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/SubdomainModuleDetection.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/cost/timesheet/api/TimesheetId.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/cost/timesheet/api/package-info.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/cost/timesheet/application/SubmissionResult.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/cost/timesheet/application/SubmitTimesheet.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/cost/timesheet/application/SubmitTimesheetCommand.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/cost/timesheet/application/TimesheetError.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/cost/timesheet/domain/Timesheet.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/cost/timesheet/domain/TimesheetStatus.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/cost/timesheet/events/TimesheetApproved.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/cost/timesheet/events/TimesheetSubmitted.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/cost/timesheet/events/package-info.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/cost/timesheet/package-info.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/cost/timesheet/web/SubmitTimesheetRequest.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/cost/timesheet/web/SubmitTimesheetResponse.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/cost/timesheet/web/TimesheetController.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/delivery/execution/api/WorkPackageId.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/delivery/execution/api/WorkPackageQueries.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/delivery/execution/api/package-info.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/delivery/execution/application/GetRemainingHours.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/delivery/execution/domain/WorkPackage.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/delivery/execution/domain/WorkPackageRepository.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/delivery/execution/domain/WorkPackageStatus.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/delivery/execution/infrastructure/persistence/JdbcWorkPackageRepository.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/delivery/execution/package-info.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/kernel/ErrorCode.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/kernel/Hours.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/kernel/LegalEntityId.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/kernel/Money.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/kernel/PublicEndpoint.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/kernel/UserId.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/kernel/ValueObject.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/kernel/package-info.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/platform/approval/api/ApprovalInstanceId.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/platform/approval/api/ApprovalProcesses.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/platform/approval/api/ApprovalRequest.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/platform/approval/api/ProcessKey.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/platform/approval/api/package-info.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/platform/approval/infrastructure/flowable/FlowableApprovalProcesses.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/platform/approval/package-info.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/platform/identity/api/CurrentUser.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/platform/identity/api/Permission.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/platform/identity/api/package-info.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/platform/identity/infrastructure/security/SecurityConfiguration.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/platform/identity/infrastructure/security/SessionCurrentUser.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/platform/identity/package-info.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/platform/numbering/api/DocumentNumber.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/platform/numbering/api/DocumentNumbers.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/platform/numbering/api/DocumentType.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/platform/numbering/api/ResetPolicy.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/platform/numbering/api/package-info.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/platform/numbering/application/CheckCounterCapacity.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/platform/numbering/application/ConfigureNumberingRule.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/platform/numbering/application/IssueDocumentNumber.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/platform/numbering/domain/NumberingPattern.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/platform/numbering/domain/NumberingRule.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/platform/numbering/domain/NumberingRuleRepository.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/platform/numbering/infrastructure/persistence/JdbcNumberingRuleRepository.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/platform/numbering/infrastructure/scheduling/CounterCapacityJob.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/platform/numbering/package-info.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/platform/numbering/web/NumberingRuleController.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/platform/numbering/web/NumberingRuleRequest.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/platform/scheduling/infrastructure/scheduling/SchedulingConfiguration.java`
- `docs/modernization/architecture-guardrails/backend/src/main/java/com/forcitis/one/platform/scheduling/package-info.java`
- `docs/modernization/architecture-guardrails/backend/src/main/resources/application.properties`
- `docs/modernization/architecture-guardrails/backend/src/main/resources/db/migration/delivery_execution/V20261014100000__delivery_execution_create_work_packages.sql`
- `docs/modernization/architecture-guardrails/backend/src/main/resources/db/migration/platform_numbering/V20261014100100__platform_numbering_create_numbering.sql`
- `docs/modernization/architecture-guardrails/backend/src/main/resources/db/migration/platform_scheduling/V20261014100200__platform_scheduling_create_scheduler_locks.sql`
- `docs/modernization/architecture-guardrails/backend/src/main/resources/processes/cost_timesheet_approval.bpmn20.xml`
- `docs/modernization/architecture-guardrails/backend/src/test/java/com/forcitis/one/ApplicationIntegrationTest.java`
- `docs/modernization/architecture-guardrails/backend/src/test/java/com/forcitis/one/ArchitectureRulesTest.java`
- `docs/modernization/architecture-guardrails/backend/src/test/java/com/forcitis/one/DdlNamingTest.java`
- `docs/modernization/architecture-guardrails/backend/src/test/java/com/forcitis/one/Glossary.java`
- `docs/modernization/architecture-guardrails/backend/src/test/java/com/forcitis/one/GlossaryTest.java`
- `docs/modernization/architecture-guardrails/backend/src/test/java/com/forcitis/one/LegacyShapeTest.java`
- `docs/modernization/architecture-guardrails/backend/src/test/java/com/forcitis/one/ModularityTest.java`
- `docs/modernization/architecture-guardrails/backend/src/test/java/com/forcitis/one/ModuleDependencyTest.java`
- `docs/modernization/architecture-guardrails/backend/src/test/java/com/forcitis/one/NamingSurfaceTest.java`
- `docs/modernization/architecture-guardrails/backend/src/test/java/com/forcitis/one/kernel/MoneyTest.java`
- `docs/modernization/architecture-guardrails/backend/src/test/resources/db/testdata/V20261014190000__testdata_work_packages.sql`
- `docs/modernization/architecture-guardrails/backend/src/test/resources/init-schemas.sql`
- `docs/modernization/architecture-guardrails/ci/assertion_drift.py`
- `docs/modernization/architecture-guardrails/ci/migrations_immutable.py`
- `docs/modernization/architecture-guardrails/frontend-vue/eslint.config.js`
- `docs/modernization/architecture-guardrails/frontend-vue/index.html`
- `docs/modernization/architecture-guardrails/frontend-vue/negative-lint.log`
- `docs/modernization/architecture-guardrails/frontend-vue/package.json`
- `docs/modernization/architecture-guardrails/frontend-vue/pnpm-lock.yaml`
- `docs/modernization/architecture-guardrails/frontend-vue/pnpm-workspace.yaml`
- `docs/modernization/architecture-guardrails/frontend-vue/scripts/check-structure.mjs`
- `docs/modernization/architecture-guardrails/frontend-vue/src/api/generated/client.ts`
- `docs/modernization/architecture-guardrails/frontend-vue/src/api/generated/schema.ts`
- `docs/modernization/architecture-guardrails/frontend-vue/src/app/App.vue`
- `docs/modernization/architecture-guardrails/frontend-vue/src/app/env.d.ts`
- `docs/modernization/architecture-guardrails/frontend-vue/src/app/main.ts`
- `docs/modernization/architecture-guardrails/frontend-vue/src/modules/delivery/api/task-package-api.ts`
- `docs/modernization/architecture-guardrails/frontend-vue/src/modules/delivery/composables/use-remaining-hours.ts`
- `docs/modernization/architecture-guardrails/frontend-vue/src/modules/delivery/index.ts`
- `docs/modernization/architecture-guardrails/frontend-vue/src/modules/delivery/model/task-package.ts`
- `docs/modernization/architecture-guardrails/frontend-vue/src/modules/delivery/pages/ProjectProgressPage.vue`
- `docs/modernization/architecture-guardrails/frontend-vue/src/modules/timesheet/api/timesheet-api.ts`
- `docs/modernization/architecture-guardrails/frontend-vue/src/modules/timesheet/components/TimeEntryRow.vue`
- `docs/modernization/architecture-guardrails/frontend-vue/src/modules/timesheet/composables/use-timesheet.ts`
- `docs/modernization/architecture-guardrails/frontend-vue/src/modules/timesheet/index.ts`
- `docs/modernization/architecture-guardrails/frontend-vue/src/modules/timesheet/model/timesheet-status.ts`
- `docs/modernization/architecture-guardrails/frontend-vue/src/modules/timesheet/model/timesheet.ts`
- `docs/modernization/architecture-guardrails/frontend-vue/src/modules/timesheet/pages/TimesheetPage.vue`
- `docs/modernization/architecture-guardrails/frontend-vue/src/modules/timesheet/stores/use-timesheet-filter-store.ts`
- `docs/modernization/architecture-guardrails/frontend-vue/src/shared/api/http-client.ts`
- `docs/modernization/architecture-guardrails/frontend-vue/src/shared/design-system/index.ts`
- `docs/modernization/architecture-guardrails/frontend-vue/src/shared/design-system/tokens.css`
- `docs/modernization/architecture-guardrails/frontend-vue/src/shared/lib/format-hours.ts`
- `docs/modernization/architecture-guardrails/frontend-vue/src/shared/lib/format-money.ts`
- `docs/modernization/architecture-guardrails/frontend-vue/src/shared/ui/AppButton.vue`
- `docs/modernization/architecture-guardrails/frontend-vue/src/shared/ui/MoneyText.vue`
- `docs/modernization/architecture-guardrails/frontend-vue/src/shared/ui/PageTitle.vue`
- `docs/modernization/architecture-guardrails/frontend-vue/src/shared/ui/StatusBadge.vue`
- `docs/modernization/architecture-guardrails/frontend-vue/src/shared/ui/index.ts`
- `docs/modernization/architecture-guardrails/frontend-vue/stylelint.config.js`
- `docs/modernization/architecture-guardrails/frontend-vue/tsconfig.json`
- `docs/modernization/architecture-guardrails/frontend-vue/vite.config.ts`
- `docs/modernization/architecture-guardrails/frontend/eslint.config.js`
- `docs/modernization/architecture-guardrails/frontend/index.html`
- `docs/modernization/architecture-guardrails/frontend/package.json`
- `docs/modernization/architecture-guardrails/frontend/pnpm-lock.yaml`
- `docs/modernization/architecture-guardrails/frontend/pnpm-workspace.yaml`
- `docs/modernization/architecture-guardrails/frontend/scripts/check-lint-config.mjs`
- `docs/modernization/architecture-guardrails/frontend/scripts/check-structure.mjs`
- `docs/modernization/architecture-guardrails/frontend/src/api/generated/client.ts`
- `docs/modernization/architecture-guardrails/frontend/src/api/generated/schema.ts`
- `docs/modernization/architecture-guardrails/frontend/src/app/main.tsx`
- `docs/modernization/architecture-guardrails/frontend/src/app/routes.tsx`
- `docs/modernization/architecture-guardrails/frontend/src/modules/delivery/api/task-package-api.ts`
- `docs/modernization/architecture-guardrails/frontend/src/modules/delivery/hooks/use-remaining-hours.ts`
- `docs/modernization/architecture-guardrails/frontend/src/modules/delivery/index.ts`
- `docs/modernization/architecture-guardrails/frontend/src/modules/delivery/model/task-package.ts`
- `docs/modernization/architecture-guardrails/frontend/src/modules/delivery/pages/project-progress-page.tsx`
- `docs/modernization/architecture-guardrails/frontend/src/modules/timesheet/components/time-entry-row.css`
- `docs/modernization/architecture-guardrails/frontend/src/modules/timesheet/components/time-entry-row.tsx`
- `docs/modernization/architecture-guardrails/frontend/src/modules/timesheet/index.ts`
- `docs/modernization/architecture-guardrails/frontend/src/modules/timesheet/model/timesheet-status.ts`
- `docs/modernization/architecture-guardrails/frontend/src/modules/timesheet/pages/timesheet-page.tsx`
- `docs/modernization/architecture-guardrails/frontend/src/shared/design-system/design-system-provider.tsx`
- `docs/modernization/architecture-guardrails/frontend/src/shared/design-system/index.ts`
- `docs/modernization/architecture-guardrails/frontend/src/shared/design-system/theme.ts`
- `docs/modernization/architecture-guardrails/frontend/src/shared/design-system/tokens.css`
- `docs/modernization/architecture-guardrails/frontend/src/shared/lib/format-hours.ts`
- `docs/modernization/architecture-guardrails/frontend/src/shared/ui/button.tsx`
- `docs/modernization/architecture-guardrails/frontend/src/shared/ui/page-title.tsx`
- `docs/modernization/architecture-guardrails/frontend/src/shared/ui/status-badge.css`
- `docs/modernization/architecture-guardrails/frontend/src/shared/ui/status-badge.tsx`
- `docs/modernization/architecture-guardrails/frontend/stylelint.config.js`
- `docs/modernization/architecture-guardrails/frontend/tsconfig.json`
- `docs/modernization/architecture-guardrails/frontend/vite.config.ts`
- `docs/modernization/architecture/proposals/DMN_FIT_RESEARCH.md`
- `docs/modernization/architecture/proposals/closeout/ANCHORING_PROPOSALS.md`
- `docs/modernization/architecture/proposals/closeout/ANCHORING_REPORT.md`
- `docs/modernization/architecture/proposals/closeout/ANCHORING_ROUND2.md`
- `docs/modernization/architecture/proposals/closeout/B31P_DESIGNER_DEFAULTS.md`
- `docs/modernization/architecture/proposals/closeout/B31P_EVIDENCE_SPEC.md`
- `docs/modernization/architecture/proposals/closeout/BURDEN_ANALYSIS_PLAN.md`
- `docs/modernization/architecture/proposals/closeout/BURDEN_EVIDENCE.md`
- `docs/modernization/architecture/proposals/closeout/CAPINFO_NEWTERMS.md`
- `docs/modernization/architecture/proposals/closeout/CLOUD_PILOT_CARD.md`
- `docs/modernization/architecture/proposals/closeout/COVERAGE_REPORT.md`
- `docs/modernization/architecture/proposals/closeout/CRUD_MATRIX.md`
- `docs/modernization/architecture/proposals/closeout/INTEGRATION_INVENTORY.md`
- `docs/modernization/architecture/proposals/closeout/INTEGRATION_INVENTORY.yaml`
- `docs/modernization/architecture/proposals/closeout/METHOD_VALIDATION_LOG.md`
- `docs/modernization/architecture/proposals/closeout/N7_DOWNSTREAM_RENAMES.md`
- `docs/modernization/architecture/proposals/closeout/N7_DOWNSTREAM_RENAMES_ADDENDUM.md`
- `docs/modernization/architecture/proposals/closeout/NAME_ID_REPORT.md`
- `docs/modernization/architecture/proposals/closeout/OBJECT_LAYER_REPORT.md`
- `docs/modernization/architecture/proposals/closeout/ORG_NEEDS.md`
- `docs/modernization/architecture/proposals/closeout/PAGE_IMPACT.md`
- `docs/modernization/architecture/proposals/closeout/PRC35_PLACEMENT.md`
- `docs/modernization/architecture/proposals/closeout/PROCESS_METHOD_PLAN.md`
- `docs/modernization/architecture/proposals/closeout/RACI_MERGE_REPORT.md`
- `docs/modernization/architecture/proposals/closeout/README.md`
- `docs/modernization/architecture/proposals/closeout/SEAM_REPORT.md`
- `docs/modernization/architecture/proposals/closeout/STAGE_ALIGN_REPORT.md`
- `docs/modernization/architecture/proposals/closeout/STAGE_DEFINITION_ISSUES.md`
- `docs/modernization/architecture/proposals/closeout/assignments.yaml`
- `docs/modernization/architecture/proposals/closeout/finance/ANSWERS.md`
- `docs/modernization/architecture/proposals/closeout/finance/BONUS_OVERTIME_COSTING.md`
- `docs/modernization/architecture/proposals/closeout/finance/EXPENSE_COVERAGE.md`
- `docs/modernization/architecture/proposals/closeout/finance/LEGAL_ENTITY_COSTING.md`
- `docs/modernization/architecture/proposals/closeout/finance/OBJECTS.md`
- `docs/modernization/architecture/proposals/closeout/finance/PAYROLL_REISSUE_SEAM.md`
- `docs/modernization/architecture/proposals/closeout/finance/QC-LIFECYCLE.md`
- `docs/modernization/architecture/proposals/closeout/finance/QC-VS-05.md`
- `docs/modernization/architecture/proposals/closeout/finance/QC-VS-06.md`
- `docs/modernization/architecture/proposals/closeout/finance/QC-VS-08.md`
- `docs/modernization/architecture/proposals/closeout/finance/QC-VS-10.md`
- `docs/modernization/architecture/proposals/closeout/finance/QC-VS-12.md`
- `docs/modernization/architecture/proposals/closeout/finance/REVIEW.md`
- `docs/modernization/architecture/proposals/closeout/finance/STAGES.md`
- `docs/modernization/architecture/proposals/closeout/finance/bpmn/PRC-100.bpmn`
- `docs/modernization/architecture/proposals/closeout/finance/bpmn/PRC-101.bpmn`
- `docs/modernization/architecture/proposals/closeout/finance/bpmn/PRC-102.bpmn`
- `docs/modernization/architecture/proposals/closeout/finance/bpmn/PRC-104.bpmn`
- `docs/modernization/architecture/proposals/closeout/finance/bpmn/PRC-105.bpmn`
- `docs/modernization/architecture/proposals/closeout/finance/bpmn/PRC-112.bpmn`
- `docs/modernization/architecture/proposals/closeout/finance/bpmn/PRC-114.bpmn`
- `docs/modernization/architecture/proposals/closeout/finance/bpmn/PRC-170.bpmn`
- `docs/modernization/architecture/proposals/closeout/finance/bpmn/PRC-171.bpmn`
- `docs/modernization/architecture/proposals/closeout/finance/bpmn/PRC-172.bpmn`
- `docs/modernization/architecture/proposals/closeout/finance/bpmn/PRC-173.bpmn`
- `docs/modernization/architecture/proposals/closeout/finance/bpmn/PRC-175.bpmn`
- `docs/modernization/architecture/proposals/closeout/finance/bpmn/PRC-64.bpmn`
- `docs/modernization/architecture/proposals/closeout/finance/bpmn/PRC-70.bpmn`
- `docs/modernization/architecture/proposals/closeout/finance/bpmn/PRC-71.bpmn`
- `docs/modernization/architecture/proposals/closeout/finance/bpmn/PRC-72.bpmn`
- `docs/modernization/architecture/proposals/closeout/finance/bpmn/PRC-73.bpmn`
- `docs/modernization/architecture/proposals/closeout/finance/bpmn/PRC-74.bpmn`
- `docs/modernization/architecture/proposals/closeout/finance/bpmn/PRC-75.bpmn`
- `docs/modernization/architecture/proposals/closeout/finance/bpmn/PRC-76.bpmn`
- `docs/modernization/architecture/proposals/closeout/finance/bpmn/PRC-77.bpmn`
- `docs/modernization/architecture/proposals/closeout/finance/bpmn/PRC-87.bpmn`
- `docs/modernization/architecture/proposals/closeout/finance/bpmn/PRC-92.bpmn`
- `docs/modernization/architecture/proposals/closeout/finance/bpmn/PRC-94.bpmn`
- `docs/modernization/architecture/proposals/closeout/finance/bpmn/PRC-95.bpmn`
- `docs/modernization/architecture/proposals/closeout/finance/bpmn/PRC-96.bpmn`
- `docs/modernization/architecture/proposals/closeout/finance/bpmn/PRC-97.bpmn`
- `docs/modernization/architecture/proposals/closeout/finance/bpmn/PRC-98.bpmn`
- `docs/modernization/architecture/proposals/closeout/finance/bpmn/PRC-99.bpmn`
- `docs/modernization/architecture/proposals/closeout/finance/bpmn/sample/PRC-87.hand.bpmn`
- `docs/modernization/architecture/proposals/closeout/finance/events.finance.yaml`
- `docs/modernization/architecture/proposals/closeout/finance/responsibility.finance.yaml`
- `docs/modernization/architecture/proposals/closeout/name_id_check.py`
- `docs/modernization/architecture/proposals/closeout/object_truth_trial.py`
- `docs/modernization/architecture/proposals/closeout/org_needs_build.py`
- `docs/modernization/architecture/proposals/closeout/people/DATA_MODEL.md`
- `docs/modernization/architecture/proposals/closeout/people/OBJECT_LAYER.md`
- `docs/modernization/architecture/proposals/closeout/people/PAGE_IMPACT.md`
- `docs/modernization/architecture/proposals/closeout/people/PAYROLL_LINE_PAYMENT.md`
- `docs/modernization/architecture/proposals/closeout/people/QC-B-31.md`
- `docs/modernization/architecture/proposals/closeout/people/QC-OBJECTS.md`
- `docs/modernization/architecture/proposals/closeout/people/QC-VS-07.md`
- `docs/modernization/architecture/proposals/closeout/people/QC-VS-09.md`
- `docs/modernization/architecture/proposals/closeout/people/QC-VS-11.md`
- `docs/modernization/architecture/proposals/closeout/people/QC-VS-13.md`
- `docs/modernization/architecture/proposals/closeout/people/README.md`
- `docs/modernization/architecture/proposals/closeout/people/REVIEW_PACK.md`
- `docs/modernization/architecture/proposals/closeout/people/REVIEW_VS14.md`
- `docs/modernization/architecture/proposals/closeout/people/STAGE_ALIGN.md`
- `docs/modernization/architecture/proposals/closeout/people/b31-rules/B31_ADM02_BLUEPRINT.md`
- `docs/modernization/architecture/proposals/closeout/people/b31-rules/B31_CONTRACT.md`
- `docs/modernization/architecture/proposals/closeout/people/b31-rules/B31_PLATFORM_CONSTANTS.md`
- `docs/modernization/architecture/proposals/closeout/people/b31-rules/B31_RULES.yaml`
- `docs/modernization/architecture/proposals/closeout/people/b31-rules/B31_TERM_GAPS.md`
- `docs/modernization/architecture/proposals/closeout/people/b31-rules/DDL_DRAFT_CARD_A.sql`
- `docs/modernization/architecture/proposals/closeout/people/bpmn/PRC-137.bpmn`
- `docs/modernization/architecture/proposals/closeout/people/bpmn/PRC-83.bpmn`
- `docs/modernization/architecture/proposals/closeout/people/bpmn/PRC-84.bpmn`
- `docs/modernization/architecture/proposals/closeout/people/bpmn/tools/bpmngen.py`
- `docs/modernization/architecture/proposals/closeout/people/bpmn/tools/make_people_sample.py`
- `docs/modernization/architecture/proposals/closeout/people/events.yaml`
- `docs/modernization/architecture/proposals/closeout/people/parts/b15data.DATA_MODEL.md`
- `docs/modernization/architecture/proposals/closeout/people/parts/b15data.data-model.yaml`
- `docs/modernization/architecture/proposals/closeout/people/parts/b31.DATA_MODEL.md`
- `docs/modernization/architecture/proposals/closeout/people/parts/b31.commands.yaml`
- `docs/modernization/architecture/proposals/closeout/people/parts/b31.data-model.yaml`
- `docs/modernization/architecture/proposals/closeout/people/parts/b31.events.yaml`
- `docs/modernization/architecture/proposals/closeout/people/parts/b31.raci.yaml`
- `docs/modernization/architecture/proposals/closeout/people/parts/b31.scenarios.yaml`
- `docs/modernization/architecture/proposals/closeout/people/parts/b31.state-machines.yaml`
- `docs/modernization/architecture/proposals/closeout/people/parts/rebuild.py`
- `docs/modernization/architecture/proposals/closeout/people/parts/vs07.commands.yaml`
- `docs/modernization/architecture/proposals/closeout/people/parts/vs07.events.yaml`
- `docs/modernization/architecture/proposals/closeout/people/parts/vs07.raci.yaml`
- `docs/modernization/architecture/proposals/closeout/people/parts/vs07.scenarios.yaml`
- `docs/modernization/architecture/proposals/closeout/people/parts/vs07.state-machines.yaml`
- `docs/modernization/architecture/proposals/closeout/people/parts/vs0911.DATA_MODEL.md`
- `docs/modernization/architecture/proposals/closeout/people/parts/vs0911.commands.yaml`
- `docs/modernization/architecture/proposals/closeout/people/parts/vs0911.data-model.yaml`
- `docs/modernization/architecture/proposals/closeout/people/parts/vs0911.events.yaml`
- `docs/modernization/architecture/proposals/closeout/people/parts/vs0911.raci.yaml`
- `docs/modernization/architecture/proposals/closeout/people/parts/vs0911.scenarios.yaml`
- `docs/modernization/architecture/proposals/closeout/people/parts/vs0911.state-machines.yaml`
- `docs/modernization/architecture/proposals/closeout/people/parts/vs13.scenarios.yaml`
- `docs/modernization/architecture/proposals/closeout/people/parts/vs13att.commands.yaml`
- `docs/modernization/architecture/proposals/closeout/people/parts/vs13att.events.yaml`
- `docs/modernization/architecture/proposals/closeout/people/parts/vs13att.raci.yaml`
- `docs/modernization/architecture/proposals/closeout/people/parts/vs13att.state-machines.yaml`
- `docs/modernization/architecture/proposals/closeout/people/parts/vs13pay.commands.yaml`
- `docs/modernization/architecture/proposals/closeout/people/parts/vs13pay.events.yaml`
- `docs/modernization/architecture/proposals/closeout/people/parts/vs13pay.raci.yaml`
- `docs/modernization/architecture/proposals/closeout/people/parts/vs13pay.state-machines.yaml`
- `docs/modernization/architecture/proposals/closeout/people/raci.yaml`
- `docs/modernization/architecture/proposals/closeout/pilot/ACCEPTANCE.md`
- `docs/modernization/architecture/proposals/closeout/raci_build.py`
- `docs/modernization/architecture/proposals/closeout/rename/RENAME_A.md`
- `docs/modernization/architecture/proposals/closeout/rename/RENAME_A.yaml`
- `docs/modernization/architecture/proposals/closeout/rename/RENAME_B.md`
- `docs/modernization/architecture/proposals/closeout/rename/RENAME_B.yaml`
- `docs/modernization/architecture/proposals/closeout/rename/RENAME_C.md`
- `docs/modernization/architecture/proposals/closeout/rename/RENAME_C.yaml`
- `docs/modernization/architecture/proposals/closeout/rename/RENAME_CHANGES.json`
- `docs/modernization/architecture/proposals/closeout/rename/RENAME_V2.md`
- `docs/modernization/architecture/proposals/closeout/rename/RENAME_V2.yaml`
- `docs/modernization/architecture/proposals/closeout/revenue/CODEX_A01.md`
- `docs/modernization/architecture/proposals/closeout/revenue/CODEX_C7.md`
- `docs/modernization/architecture/proposals/closeout/revenue/C_DEFAULTS.md`
- `docs/modernization/architecture/proposals/closeout/revenue/DEFAULT_PAYMENT_TERM.md`
- `docs/modernization/architecture/proposals/closeout/revenue/OBJECT_LAYER.md`
- `docs/modernization/architecture/proposals/closeout/revenue/Q04_STORAGE.md`
- `docs/modernization/architecture/proposals/closeout/revenue/QC-LIFECYCLE.md`
- `docs/modernization/architecture/proposals/closeout/revenue/QC-VS-01.md`
- `docs/modernization/architecture/proposals/closeout/revenue/QC-VS-02.md`
- `docs/modernization/architecture/proposals/closeout/revenue/QC-VS-03.md`
- `docs/modernization/architecture/proposals/closeout/revenue/QC-VS-04.md`
- `docs/modernization/architecture/proposals/closeout/revenue/README.md`
- `docs/modernization/architecture/proposals/closeout/revenue/RECHECK.md`
- `docs/modernization/architecture/proposals/closeout/revenue/REVIEW.md`
- `docs/modernization/architecture/proposals/closeout/revenue/STAGE_ALIGN.md`
- `docs/modernization/architecture/proposals/closeout/revenue/VS_REVIEW.md`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-03.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-07.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-08.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-13.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-140.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-141.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-146.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-147.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-148.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-149.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-150.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-151.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-152.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-153.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-16.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-17.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-179.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-18.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-183.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-19.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-20.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-23.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-24.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-26.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-28.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-30.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-31.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-32.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-34.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-36.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-37.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-38.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-41.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-43.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-44.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-45.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-46.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-47.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-48.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-49.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-50.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-51.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-53.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-54.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-56.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-57.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-58.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-59.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-60.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-61.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-63.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-66.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-69.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-78.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-86.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/bpmn/PRC-91.bpmn`
- `docs/modernization/architecture/proposals/closeout/revenue/converge_check.txt`
- `docs/modernization/architecture/proposals/closeout/revenue/legal-entity/LE_CONTRACT_AGREEMENT.md`
- `docs/modernization/architecture/proposals/closeout/revenue/legal-entity/LE_DELIVERY_EXECUTION.md`
- `docs/modernization/architecture/proposals/closeout/revenue/legal-entity/LE_RECEIVABLES_WIP.md`
- `docs/modernization/architecture/proposals/closeout/revenue/raci.yaml`
- `docs/modernization/architecture/proposals/closeout/revenue/tools/bpmn_gen.suggested.patch`
- `docs/modernization/architecture/proposals/closeout/rulings/S-31_FINANCE_SQ01-33.md`
- `docs/modernization/architecture/proposals/closeout/rulings/S-43_OBJECT_CLASSIFICATION.md`
- `docs/modernization/architecture/proposals/closeout/rulings/S-43_exemptions.draft.yaml`
- `docs/modernization/architecture/proposals/closeout/seam_check.py`
- `docs/modernization/architecture/proposals/closeout/sync_catalog_stages.py`
- `docs/modernization/architecture/ssot/i09-acceptance/GAPS.md`
- `docs/modernization/data/build_table_inventory.py`
- `docs/modernization/extract-rules.modules.json`
- `docs/modernization/glossary/answers/2026-10-15-acl-round3.json`
- `docs/modernization/glossary/arch-naming/N4_COVERAGE.md`
- `docs/modernization/glossary/b2/AGG_PREJUDGE.md`
- `docs/modernization/glossary/b2/GL_RENAME_CARDS.md`
- `docs/modernization/glossary/gate/build_gate_page.py`
- `docs/modernization/glossary/gate/export_decisions.py`
- `docs/modernization/glossary/gate/page-template.html`
- `docs/modernization/glossary/round3/build_round3_decisions.py`
- `docs/modernization/glossary/round3/build_round3_page.py`
- `docs/modernization/glossary/round3/export_decisions.py`
- `docs/modernization/glossary/round3/inventory.py`
- `docs/modernization/glossary/round3/page-template.html`
- `docs/modernization/glossary/round3/round3-items.json`
- `docs/modernization/glossary/round3/round3.css`
- `docs/modernization/glossary/round3/round3.js`
- `docs/modernization/live_code.json`
- `docs/modernization/live_code_prod.json`
- `docs/modernization/map/add_desc.py`
- `docs/modernization/map/backend.json`
- `docs/modernization/map/mini.json`
- `docs/modernization/map/packets/bpm_service_task.txt`
- `docs/modernization/map/packets/framework_common.txt`
- `docs/modernization/map/packets/fst_application_cost.txt`
- `docs/modernization/map/packets/fst_application_delivery.txt`
- `docs/modernization/map/packets/fst_application_expense.txt`
- `docs/modernization/map/packets/fst_controller_admin_cost.txt`
- `docs/modernization/map/packets/fst_controller_admin_expense.txt`
- `docs/modernization/map/packets/fst_controller_admin_manhour.txt`
- `docs/modernization/map/packets/fst_controller_admin_pmproject.txt`
- `docs/modernization/map/packets/fst_domain_cost.txt`
- `docs/modernization/map/packets/fst_domain_delivery.txt`
- `docs/modernization/map/packets/fst_domain_expense.txt`
- `docs/modernization/map/packets/fst_domain_presales.txt`
- `docs/modernization/map/packets/fst_infrastructure_delivery.txt`
- `docs/modernization/map/packets/fst_infrastructure_expense.txt`
- `docs/modernization/map/packets/fst_service_costsummaryext.txt`
- `docs/modernization/map/packets/fst_service_manhour.txt`
- `docs/modernization/map/packets/fst_service_project.txt`
- `docs/modernization/map/packets/fst_service_resource.txt`
- `docs/modernization/map/packets/fst_service_wip.txt`
- `docs/modernization/map/packets/h5_pages_work_project.txt`
- `docs/modernization/map/packets/h5_pages_work_travel.txt`
- `docs/modernization/map/packets/pc_api_accounting.txt`
- `docs/modernization/map/packets/pc_components.txt`
- `docs/modernization/map/packets/pc_utils.txt`
- `docs/modernization/map/packets/pc_views_accounting_costCarry.txt`
- `docs/modernization/map/packets/pc_views_accounting_costCollection.txt`
- `docs/modernization/map/packets/pc_views_accounting_travel.txt`
- `docs/modernization/map/packets/pc_views_bpm_processInstance.txt`
- `docs/modernization/map/packets/pc_views_components_dialog.txt`
- `docs/modernization/map/packets/pc_views_costopenning.txt`
- `docs/modernization/map/packets/pc_views_infra_build.txt`
- `docs/modernization/map/packets/pc_views_project_lists.txt`
- `docs/modernization/map/packets/pc_views_project_manhour.txt`
- `docs/modernization/map/packets/pc_views_project_planning.txt`
- `docs/modernization/map/packets/pc_views_project_pmList.txt`
- `docs/modernization/map/packets/pc_views_project_quotation.txt`
- `docs/modernization/map/packets/pc_views_project_timesheet.txt`
- `docs/modernization/map/packets/pc_views_project_weeklyMeeting.txt`
- `docs/modernization/map/pc.json`
- `docs/modernization/quality/CI_TIMING_REVIEW.md`
- `docs/modernization/quality/GOVERNANCE_RESEARCH.md`
- `docs/modernization/quality/TEST_IMPACT_RESEARCH.md`

## 二乙批（E 类，GOV-1023-FV2）

二乙从 main 移出的有读者文件，原文件留在只读归档分支 `archive/2026-10-b2b`（由 one-ops 在二乙合入前、自移出提交的父提交建出，之后不再改动；建出前以该父提交上的同一路径取回）。与上面「逐文件」一节分开登记，不并入其合计。

取回某个文件：`git show archive/2026-10-b2b:<路径>`。

E1 只因 policy.json 登记而有读者的一次性脚本及其读入物（#34，11 个；登记已同提交删除；归档位置 `archive/2026-10-b2b`，同一路径）：

- `docs/modernization/describe_nodes.py`
- `docs/modernization/extract_topology.py`
- `docs/modernization/glossary/migrate_to_concepts.py`
- `docs/modernization/glossary/test_checkers_on_concepts.py`
- `docs/modernization/glossary/test_migrate_to_concepts.py`
- `docs/modernization/live_code.py`
- `docs/modernization/map/descriptions.json`
- `docs/modernization/rules/data_objects.py`
- `docs/modernization/rules/merge_rules.py`
- `docs/modernization/rules/pull_result.py`
- `docs/modernization/topology.json`

E2 文字出处与同批引用（#35，23 个；归档位置 `archive/2026-10-b2b`，同一路径；ADR 正文不改，其中的出处文字按下列路径在归档分支上取回；`.gitleaksignore` 的指纹行保留，指纹对应历史提交）：

- `docs/modernization/architecture-guardrails/backend/coverage.csv`
- `docs/modernization/architecture-guardrails/backend/negative-test.log`
- `docs/modernization/architecture-guardrails/backend/src/test/java/com/forcitis/one/SecurityInvariantsTest.java`
- `docs/modernization/architecture-guardrails/frontend/negative-lint.log`
- `docs/modernization/architecture/proposals/closeout/ARCH_VS14.md`
- `docs/modernization/architecture/proposals/closeout/BPMN_STYLE.md`
- `docs/modernization/architecture/proposals/closeout/CONVENTIONS.md`
- `docs/modernization/architecture/proposals/closeout/PROCESS_METHOD_RESEARCH.md`
- `docs/modernization/architecture/proposals/closeout/PROCESS_METHOD_REVIEW.md`
- `docs/modernization/architecture/proposals/closeout/PROCESS_QUALITY_CHECKLIST.md`
- `docs/modernization/architecture/proposals/closeout/RESPONSIBILITY_MATRIX_SPEC.md`
- `docs/modernization/architecture/proposals/closeout/bpmn_check.py`
- `docs/modernization/architecture/proposals/closeout/bpmn_gen.py`
- `docs/modernization/architecture/proposals/closeout/bpmn_gen_fixtures/activity_cancel.yaml`
- `docs/modernization/architecture/proposals/closeout/bpmn_gen_fixtures/entry.yaml`
- `docs/modernization/architecture/proposals/closeout/bpmn_gen_fixtures/exits.yaml`
- `docs/modernization/architecture/proposals/closeout/bpmn_gen_fixtures/mini.yaml`
- `docs/modernization/architecture/proposals/closeout/bpmn_gen_fixtures/timeout.yaml`
- `docs/modernization/architecture/proposals/closeout/converge_check.py`
- `docs/modernization/architecture/proposals/closeout/people/b31-rules/B31_ALIGNMENT_AUDIT.md`
- `docs/modernization/architecture/proposals/closeout/people/b31-rules/B31_CONSTRUCTION_DESIGN.md`
- `docs/modernization/build_oa_inventory.py`
- `docs/modernization/map/packets/fst_infrastructure_cost.txt`

E4 glossary 重放链里移出的一份（#37，1 个；归档位置 `archive/2026-10-b2b`，同一路径；重放链其余 36 个留 main）：构建只读其 `proposed`、`missing_terms` 两段，已逐字抽成 `docs/modernization/glossary/arch-naming/reconcile-decided-slim.json` 留在 main，`apply_recon.py` 改读瘦身文件；RECONCILE_DECIDED.md 等文字出处不改，按下列路径在归档分支上取回：

- `docs/modernization/glossary/arch-naming/reconcile-decided.json`

#40 删除的脚本（1 个；不属 E 类，复审建议补登；`parse_intent` 已迁入 `rules/build_rule_index.py`，policy 登记同提交删除；归档位置 `archive/2026-10-b2b`，同一路径）：

- `docs/modernization/build_records.py`
