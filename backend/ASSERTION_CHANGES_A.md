# 修复批 A：改动与删除的断言

范围：`platform.approval`、`cost.timesheet` 及 `ApplicationIntegrationTest` 中随之改的用例。基线 1606d45。每条写明为何改、是否有等价用例。

## 一、删除的用例

| 用例 | 为何删除 | 等价用例 |
|---|---|---|
| `cost.timesheet.web.TimesheetHttpIT`（整类：提交、撤回、撤销、读取、权限、他人与不存在的工时单） | D-1a-107：1a 不提供工时单的提交、撤回、撤销、读取接口，控制器已删 | `TimesheetEntryClosedIT` 断言这些路径为 404 或 405；用例层的规则由 `TimesheetApprovalScenarioIT` 覆盖 |
| `ApplicationIntegrationTest.missingPermissionIsForbidden`、`typedOutcomesMapToHttpStatuses` | 两者经工时单提交接口取得 403 与类型化结果的状态码，接口已按 D-1a-107 关闭 | 权限与错误链由 `HttpContractIT`、`ErrorChainTest` 及审批任务接口的 `TaskVersionHttpIT`、`TaskActorAuthorizationIT` 覆盖 |
| `ApplicationIntegrationTest.submitStartsFlowableApprovalInItsOwnSchema` | 提交不再同事务启动审批（H-1：事件解耦，一事务一聚合） | `TimesheetApprovalScenarioIT.submittedClaimedDecidedApproved` 断言审批由事件发起 |
| `ApplicationIntegrationTest.submitWithoutAnExistingProjectManagerFailsInsteadOfPassingSilently` | 经已关闭的接口 | 恢复为 `TimesheetApprovalScenarioIT.unknownProjectManagerIsRefused`（提交时校验项目经理在职，422）；发起时业务规则拒绝由 `StartApprovalOnSubmissionIT.aRefusedStartEndsAsReturned` 覆盖 |
| `TimesheetApprovalScenarioIT.concurrentSubmissionsCannotExceedTheWorkPackage`（M-3）、`unknownWorkPackage` | 交付执行模块整体删除（D-1a-127），工时单不再核对工作包剩余工时，没有可断言的行为 | `concurrentFirstSubmissionsOfOneTimesheetCreateOne`：同一工时单并发首次提交只建一张、一个事件序号 |
| `TimesheetStateMachineTest.everyPairBehavesAsTheTableSays`、`theTableCoversEveryAction`、`perform`；`ApprovalRequestStateMachineTest` 与 `ApprovalTaskStateMachineTest` 的同名三项 | 状态机改为内核 `StateMachine`，表驱动用例换成内核 `TransitionTable` | `everyOperationInEveryStatus`（请求、工时单）、`everyMoveInEveryStatus`（任务）逐状态逐操作尝试，非法对子必须被拒且不改变状态 |
| `ApprovalRequestStateMachineTest.cutoffBlocksBoth`、`withdrawAndCancelNeedTheInitiatorAndAReason`、`retract`、`restartIsTheInitiatorsAndStartsAFreshRound` | 同上；撤回与撤销的判断统一到 `checkWithdraw` | `withdrawAndCancelFollowTheCheck`、`displayAndExecutionShareTheCheck`、`theCheckNamesTheReason`、`restartIsTheInitiatorsAlone`、`startsAndRestartsRecordTheRound` |
| `ApprovalTaskStateMachineTest.digestOnlyForFormalApprovals`、`eventsCarryTheTaskSequence`、`transferIsTheHandlersAndToSomebodyElse` | 任务并入审批请求聚合：事件序号属于请求，不再有任务自己的版本与序号 | `hashOnlyForFormalApprovals`、`eventsCarryTheRequestsSequence`、`transferIsTheHandlersAndToSomebodyElseWithAReason` |
| `AggregateConcurrencyIT.staleApprovalTaskIsRefused` | 任务不再是独立聚合，没有自己的版本 | `staleTaskChangeIsRefused`：从旧请求副本保存任务的改动被拒 |
| `AggregateConcurrencyIT.staleTimesheetIsRefused` | 随工时单提交用例一并移动 | `TimesheetApprovalScenarioIT.aStaleCopyIsNotSaved` |
| `TaskActorAuthorizationIT.permissionsAreRequiredAsWell` | D-1a-179：办理任务只看是不是处理人或候选人，不再要求菜单权限（控制器只要求登录）；原断言「没有 approve 权限的候选人认领为 403」与决定相反 | `TaskActorAuthorizationIT` 其余用例断言非处理人、非候选人被拒；`TaskVersionHttpIT` 断言各动作的条件请求 |
| `BpmnRuleConventionsTest.timesheetNodesAreCountersignAndLeaveSkippingToTheTable` | D-1a-100：1a 不用决策表，「能否跳过」等已是节点属性 | `BpmnRuleConventionsTest` 其余用例读节点属性；`NodeRoutingIT` 覆盖路由 |
| 测试数据 `V20261014190000__testdata_work_packages.sql` | 工作包表随模块删除 | 无 |
| 各流程变体目录下的 `*_nodes.dmn` | 同 D-1a-100 | 变体 BPMN 直接带节点属性 |

## 二、改动的断言

| 用例 | 改动 | 理由 |
|---|---|---|
| 各 IT 里 `claimTask`、`releaseTask`、`transferTask`、`returnToInitiator`、`reassignTask`、`decideTasks` 的调用 | 末位加 `OptionalLong.empty()` | 用例层按期望版本在加锁后比较，内部调用方（任务、其他模块）不带版本；HTTP 层必带 |
| `TaskVersionHttpIT`：版本不符的错误码 | 由「审批请求被他人改动」改为 `PLATFORM_WEB_VERSION_MISMATCH`；新增缺失 428、格式错 400，release 与 reassign 同样适用 | 评审 A-H1：If-Match 在加锁后强比较 |
| `TimesheetApprovalScenarioIT`：事件聚合版本 | 通过事件的版本由 2 改为 3 | 一次提交现在记录 `TimesheetSubmitted` 与 `DocumentSubmittedForApproval` 两个事件，序号各加一 |
| `TimesheetApprovalScenarioIT`：`event_count` | 1 改 2 | 同上 |
| `TimesheetStateMachineTest.eventsCarryTheDraftFields` | 事件类型序列与序号序列改为每次提交两个事件（5..10） | 同上 |
| `TimesheetStateMachineTest.submittingPublishesWhatTheApprovalNeeds` → `submittingPublishesTheFactAndTheRequest` | 路由标记、摘要、内容摘要不再断言在 `TimesheetSubmitted` 上，改断言在 `DocumentSubmittedForApproval` 上 | 事实与发起审批所需信息分开 |
| `TimesheetApprovalScenarioIT.redeliveredSubmissionStartsNothingNew` | 不再直接调用监听器，改为用同一发起请求调用审批端口两次，断言收件箱只有一个任务、轮次仍为 1 | 监听器移入 `platform.approval`，包内可见；幂等性质不变 |
| `TimesheetApprovalScenarioIT.taskOf` 与重复发起用例 | 不再查 `platform_approval` 表和 `TaskService`，经 `ListInbox` 与审批端口取 | 评审 A-M5：跨模块不碰对方的表与引擎 |
| `ApprovalDeadlineIT.numbersComeFromTheDecisionTable` → `numbersComeFromTheNode`；`claimedTaskEscalates`、`escalationToTheSuperior` → `aClaimedTaskEscalatesToTheSuperior` | 取数来源改为节点属性；T 取任务创建所在工作日而非工作日期 | D-1a-100、D-1a-111 |
| `ApprovalDeadlineIT.anUnclaimedTaskEscalatesToTheAdministrator` | 流程管理员改为角色持有人，用两位管理员，升级对象与候选人都含两位；并断言原卡片被更新为「已超时，已升级给…」 | D-1a-61、D-1a-113 |
| `TaskLifecycleIT.aDecisionIsAuditedOnce` → `aDecisionPublishesItsFactsOnce` | 断言事件而非审计条目 | 办理事实在聚合事件里，审计由订阅者按事件写 |
| `TaskNotificationIT.returnSendsNoFurtherCard` → `returnSendsTheCompletionCardOnly` | 增加断言发起人收到完成卡片 | D-1a-135 |
| `TimesheetApprovalScenarioIT.legalEntityAndVersionAreStored` → `versionAndEventSequenceAreStored`；`submittingTwiceReturnsTheRunningInstance` → `submittingTwiceChangesNothing` | 工时单的法人与日期来自员工归属接口的替身；重复提交答复工时单本身而不是审批实例 | H-1：提交的答复是工时单 |
| `BpmnRuleConventionsTest` 中 9 处 `isInstanceOf(IllegalStateException.class)` | 改为 `DomainException.class` | 评审 A-nit：节点配置错误是领域异常，带节点标识与错误码 `PLATFORM_APPROVAL_NODE_CONFIG_INVALID` |
| `AssigneeResolverTest` | 管理员由单个用户改为用户集合 | 流程管理员是多人角色 |
| `ApplicationIntegrationTest` G42 | 放行标准 HTTP 头 `If-Match`、`If-None-Match`、`Idempotency-Key` | 评审 A-B2：头名由 HTTP 规定，不由本契约取名；只放这三个，其余头名仍须是 camelCase 术语 |
| `DdlNamingTest`（守护） | 模型认得 `DROP TABLE` | 删除交付执行模块的迁移需要；不放宽任何规则 |
| `NamingSurfaceTest` G44（守护） | 迁移目录可以是已退役模块，登记在 `architecture/retired-module-schemas.txt`，且该目录最后一个迁移必须 `DROP SCHEMA` | 已应用的迁移不改不挪，模块删除后目录仍在；加了「最后迁移必须删 schema」的新检查 |
| `ReadPathsDoNotLockTest`（守护） | 沿接口调用追进所有实现 | 评审 A-M8，只加严 |
| `SingleAggregateGuardTest` / `AggregateWriteGuard`（守护） | 登记的多聚合事务还须由事件的关联聚合点名全部聚合；新增经真实切面驱动的用例 | 评审 A-M8，只加严 |

## 三、新增的断言（不属于改动，列出便于评审）

`TaskHandlingRecordsIT`、`StartApprovalOnSubmissionIT`、`EventSequencePersistenceIT`、`EventSequenceCoverageTest`、`MergedInboxIT.theInboxIsPaged`、`TimesheetApprovalScenarioIT` 中的项目经理、法人、旧副本三项。
