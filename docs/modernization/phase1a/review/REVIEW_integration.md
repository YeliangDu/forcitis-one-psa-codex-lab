# FORCITIS one Phase 1a 修复批整合复审（opus，只读）

- **对象**：分支 `claude/psa-1a-fix-integration`。初审 HEAD 为 eba8530（实际 27ac707，多出的提交只改文档）；High 修复后的终审 HEAD 为 **e408317c**。对比基线为 origin/main。
- **方式**：全程只读，没有改仓库文件。四路检查（原复审对照、ADR 与用户决定、安全语义、DDD）由 opus 子代理完成，集成接线和契约抽查由 sonnet 子代理完成。所有结论我都按代码复核过；High 修复逐条读了 diff 和测试体。
- **审阅范围内没有发现针对自动化分析的指令型文字。**

## 0. 总结论

**修后合并。** 代码层面的 Blocker 和 High 都已清零。剩下两条属于「偏离用户决定」：INT-G1、INT-G3。调度已裁定把它们推到下一批，但按收口规则，这类事须回到用户确认，调度不能替用户决定。

- 用户确认推迟（或 INT-G3 当场修掉，只改一行）后即可合并。
- `status/blockers/070` 和 `080` 可以在本 PR 删除，`090` 保留。

**验证数字（我自己跑的）**

| 运行 | HEAD | 结果 |
|---|---|---|
| 后端 `./mvnw -B clean verify` | eba8530 | 852 + 302 + 2 = **1156** 个测试，0 失败，19 跳过，BUILD SUCCESS |
| 后端 `./mvnw -B clean verify`（临时 worktree，跑完已删除） | e408317c | 860 + 302 + 2 = **1164** 个测试，0 失败，19 跳过，BUILD SUCCESS |
| 前端 `pnpm verify`（离线安装依赖） | 两个 HEAD | 都是 9 步全过：vitest 225 个，脚本测试 34 个 |

**子代理另外跑过的检查**

- `build_asyncapi --check`、`build_all --check` 都没有差异。
- `migrations_immutable.py origin/main` 报 0 条违规。
- `generate-api.mjs --check` 一致（64 个操作）。

---

## 1. 原复审 REVIEW_b12f1df 逐条对照（26 条）

文件路径相对 `backend/src/main/java/com/forcitis/one/`。

| 编号 | 结论 | 实现位置 | 测试 | 备注 |
|---|---|---|---|---|
| **B-1** | 已修 | `platform/identity/infrastructure/security/SessionCurrentUser.java:39-41` 按营业日查员工的法人；查不到时，`EmployeeLegalEntityService.java:27-28` 抛 `ORGANIZATION_WORKFORCE_EMPLOYEE_LEGAL_ENTITY_UNRESOLVED`（422）。`SubmitTimesheet.java:83` 按工作日期取法人；事件信封取聚合上的法人（`Timesheet.java:210`）；`TaskRegistrar.java:46` 用请求的法人算日历 | `SessionCurrentUserTest.unresolvedFails`；`TimesheetApprovalScenarioIT.missingLegalEntityIsRefused`；`CurrentUserGuardTest.noMadeUpLegalEntity`、`madeUpAnswersAreCaught` | 固化 `isEqualTo(1L)` 的断言已删。生产代码里唯一的字面量是 `OrgUnitScopes.java:29` 的 `NOT_USED = new LegalEntityId(1)`：它不写库、不进事件、不对外返回，已登记为 B1-scope-M2 |
| **H-1** | 已修 | 提交只改工时单（`SubmitTimesheet.java:62-93`）；聚合发出 `TimesheetSubmitted`（`Timesheet.java:140-146`）；审批经 `StartApprovalOnSubmission`（`@ApplicationModuleListener` + REQUIRES_NEW，按轮次幂等）订阅后发起；任务并入请求聚合（`ApprovalRequest.java:51-57`，`ApprovalTask` 是内部实体）；`ApprovalLifecycle` 已删除；提交接口不再返回 `approvalInstanceId` | `TimesheetApprovalScenarioIT.submittedClaimedDecidedApproved`、`redeliveredSubmissionStartsNothingNew`；`StartApprovalOnSubmissionIT` 三个用例；`SingleAggregateGuardTest` | 发起失败的出路有缺口，见 INT-M1 |
| H-2 | 已修 | 发号对法人行 `FOR SHARE`（`JdbcLegalEntityQueries.java:38`）；更正走 `findForUpdate` | `LegalEntityCodeRaceIT` 中三个「H-2」用例 | |
| H-3 | 部分 | 401 和 403 都输出 ProblemDetail（`SecurityConfiguration.java:58-60`、`GlobalExceptionHandler.java:69-77`）；`IdentityResponses` 已删；DMN 时限与升级天数成对校验 | `ErrorContractIT.everyRefusalIsAProblem`；`ErrorChainTest`；`ErrorCodeGlossaryTest` | 残留：`decide` 请求体为空时返回 500（`ErrorContractIT.java:81` 把它登记为已知故障），见 INT-M11 |
| **H-4** | 已修 | `UserAccount.open` 发出 `AccountOpened`，角色是聚合状态（`UserAccount.java:45-50`）；INSERT 显式写状态列（`JdbcAccountRepository.java:54-99`）；投递按条件 `saveHandled`（`JdbcOutboundDeliveryRepository.java:110-114`） | `IdentityStateMachineTest` 中的 opening 用例；`RepositoryWriteGuardTest` 三条规则 | 守护按方法名前缀放行，已登记为 B3-R2 |
| **H-5** | 已修 | `kernel/AggregateRoot.java:13-59` 把锁版本 version 和事件序号 eventSequence 分开；全仓只剩一个 `PendingEvent`；审批和工时单迁到 `AggregateRoot` + `StateMachine`；`OrgSnapshotChanged` 只由 `SyncRun` 发出 | `EventSequenceContinuityTest`（每个聚合，含「没有事件的保存不推进序号」）；`EventSequencePersistenceIT`；`EventSequenceCoverageTest.everyAggregateRootIsCovered` | |
| H-6 | 已修 | 命令里带 `workDate`；不允许未来日期，重新提交不得改日期（`Timesheet.java:104-126`） | `TimesheetApprovalScenarioIT.returnedThenRevisedAndApproved`；`TimesheetStateMachineTest` | |
| H-7 | 已修（e408317c） | 5 个跨模块测试移到 `scenario/`；`test-isolation-exceptions.txt` 只剩注释 | `TestIsolationGuardTest.testsStayInTheirOwnSchema` | |
| M-1 | 已修 | 查询类 `readOnly = true`；仓储拆成 `find` 和 `findForUpdate` | `ReadPathsDoNotLockTest` | 守护不管 `Find*` / `Search*`，见 INT-L6 |
| M-2 | 已修 | `SubmitTimesheet.java:103-108`（`COST_TIMESHEET_RESULT_PENDING`） | `TimesheetApprovalScenarioIT.resubmittingBeforeTheResultArrivedIsRefused` | |
| M-3 | 改期 | 1a 不对外暴露工时提交入口 | — | 已登记在 DEFERRED「工作包剩余工时校验」，依据是调度按 D-1a-107 / D-1a-127 的判定，不是用户的直接原话 |
| M-4 | 部分 | `ApprovalRoundStarted`、`TaskClosed` 已补；系统自动通过改为发 `TaskResulted`（AUTOMATIC） | `ApprovalTaskStateMachineTest`「RULE-295 M-4…」 | 外发投递进入 DEAD_LETTER 仍不发事件，见 INT-L9 |
| M-5 | 已修 | `ApprovalRequest.java:58-64`；转换检查已移到 `engine.startInstance` 之前 | `ApprovalRequestStateMachineTest.runningCannotRestart` | REJECTED→RUNNING 是按 D-1a-78 保留的，见 INT-L10 |
| M-6 | 已修 | `releaseTask` / `reassignTask` 都发事件 | `TaskHandlingRecordsIT` | |
| M-7 | 已修 | `TaskDeadline.java:53-69` 按任务锁住所属请求，旧轮次的任务直接关闭 | `ApprovalDeadlineIT`「M-7…」 | |
| M-8 | 部分 | `AccountProvisioning` 改为发事件 | — | `HandleOrgSnapshotChange.java:84,107-108` 仍在监听器里直接写审计，见 INT-M3 |
| M-9 | 部分 | `MarkWecomCardHandled` 已幂等 | `WecomDeliveryIT` 的 Q-3 用例 | `HandleOrgSnapshotChange` 重投不幂等，见 INT-M3 |
| M-10 | 已修 | `StartApproval.java:87-98` 拒绝未知或缺失的路由标记 | `NodeRoutingIT`「M-10…」 | |
| M-11 | 已修 | 时区与时钟改为注入，写死的时区已去掉 | `TimeSourceGuardTest`、`EventBusinessDateGuardTest` | 残留两处兜底，见 INT-L11 |
| M-12 | 部分 | 错误 detail 按类别输出固定文案（`ProblemDetails.java:24-44`）；非参与人统一 404（`TaskGuard.java:58`） | `TaskActorAuthorizationIT`「M-12…」 | 没有配置 `StrongUuidGenerator`，见 INT-L12 |
| **M-13** | 已修 | state 绑定浏览器：HttpOnly、SameSite=Lax 的 cookie 存绑定值，服务端只存哈希，按「state 哈希 + 绑定哈希」原子删除（`LoginStateCookie.java:14-16`、`JdbcOneTimeCredentials.java:29-32`）；`LoginThrottle` 按 IP 限流 | `WecomLoginIntegrationTest` 两个「M-13」用例；`LoginThrottleIntegrationTest`「M-13…」 | 限流计数只在单节点内，类注释已写明 |
| M-14 | 部分 | ProblemDetail 与 `ErrorCode` 联合类型已生成；文案表改为 `Record<ErrorCode, …>` | `failure.test.ts:61` | 两个状态字段仍生成为 `z.string()`；契约里混进测试专用错误码，见 INT-M6 |
| M-15 | 已修 | 共用代码移到 `packages/core`；`check-structure` 按去空白、去注释后的哈希比对 | `check-structure.test.mjs` | |
| M-16 | 部分 | 单字只能作连接词；待办标题改为 `ApprovalTexts.inboxTitle` | `copy-glossary.test.mjs` | 后端对外文案不在守护范围内；`standalone` 清单含单字，见 INT-L13 |
| M-17 | 部分 | 测试配置关闭定时作业 | — | 已移到 `scenario/` 的 `SyncLockoutGuardIntegrationTest` 仍在等整张 `event_publication` 表清空，见 INT-M14 |
| M-18 | 已修 | 集成配置不再带生产值默认 | `IntegrationConfigurationTest`「RULE-356 / M-18…」 | |

**小计**：已修 17 条，部分 8 条，改期 1 条，未修 0 条。部分修的残留都是 Medium 或 Low，不阻塞合并。

---

## 2. ADR-0027 八项与 ADR-0028 对照（以 e408317c 为准）

| 条目 | 实现 | 测试 | 结论 |
|---|---|---|---|
| ① 离职删除全部角色授予（含超管，逐条审计），停用应急账号，吊销会话和浏览器标识 | `HandleOrgSnapshotChange.java:79-111`；`DisableEmergencyAdminOnSeparation.java:46-57` | `SeparationRevokesRolesIntegrationTest.separationRemovesRolesEmergencyAccountAndSessions`、`deliveringTheEventTwiceIsHarmless` | ✅ |
| ② 应急和管理模式会话每次请求复核账号启用、应急账号启用、仍持有超管；快照明确离职就拒绝应急登录 | `SessionPrincipals.java:45-66`；`LoginAsEmergencyAdmin.java:87-98` | `EmergencyLoginIntegrationTest`：`separatedPersonIsRefused`、`missingSnapshotIsAllowed`、`boundPersonMustHoldSuperAdmin`、`sessionEndsWhenSuperAdminIsTakenAway` | ✅（没有「快照过期」的用例，见 INT-L14） |
| ③ 防锁死：运行保持 HELD 并告警 | `AdministratorSeparationReview.java:38-53`；`ApplySourceSnapshot.java:79-133` | `SyncLockoutGuardIntegrationTest.aSuperAdministratorLeavingHoldsTheRun`、`anAuditorLeavingHoldsTheRun` | ✅（放行路径的缺口已由 INT-ADR-H2 补上） |
| ④ 至少 2 个应急账号，每日核对 | `ReconcileEmergencyAdmins.java:43-69`；`EmergencyAdminCheckJob` | `EmergencyAdminDailyCheckTest`（5 个用例） | ✅ |
| ⑤ 三个系统角色；管理模式恰好五项权限 | `V20261015000000…seed_business_roles.sql`；`AdministratorMode.java:12-17` | `AdministratorModeIntegrationTest.enteringGivesExactlyThePlatformPermissions`；`RoleTest.superAdmin`（集合相等） | ✅ |
| ⑥ 编号模板按版本四眼确认；未确认不发号；凭证模板另需财务确认；原有已确认的回到待确认 | `NumberingRule.java:105-179`；`IssueDocumentNumber.java:44`；`V20261015000300` | `NumberingRuleFourEyesIT`（作者不能确认、凭证需财务确认、表结构强制四眼、生效日早于确认当天拒绝）；`NumberingVersionMigrationIT` | ✅ |
| ⑦ 审计导出权限；员工本人登录记录（只取结构化列） | `OwnLoginRecords`、`AuditExportController` | `AuditExportAndOwnRecordsIT`（4 个用例） | ✅（离职吊销的记录没有专门造数据，见 INT-L14） |
| ⑧ 不能改自己的法人归属 | `OwnAttributionGuard`，已接入新增、结束、**更正**三条路径（`CorrectLegalEntityAttribution.java:66`） | `OwnLegalEntityAttributionForbiddenIntegrationTest`，含「INT-ADR-H1」 | ✅（e408317c） |
| 28-1 暂停原因类型化，按类解除 | `HoldKind`；`ReleaseSyncRunHolds.java:46-66` | `bothKindsAreLiftedSeparately`、`theOrgDataAdministratorCannotLiftTheSeparationHold` | ✅ |
| 28-2 `separation-hold:approve` 权限与 HELD 只读视图 | `SyncRunController.java:41-80`；`ListHeldSyncRuns` | `theHeldView` | ✅ |
| 28-3 管理模式和安全审计员角色持有该权限 | `AdministratorMode.java:17`；`V20261015000800` | `theAuditorRoleCarriesThePermission`、`aRetainedAuditorReleasesInAnOrdinarySession` | ✅ |
| 28-4 留任判定；重跑名单出现新管理员时重新 HELD；放行后超管为 0 拒绝、为 1 写明人数；必须填原因；审计 | `ReleaseSyncRun.java:43-80`；`RecordSyncRunRelease` | `thoseLeavingCannotRelease`、`aNewAdministratorInTheListHoldsAgain`、`noSuperAdministratorLeftIsRefused`、`aReasonIsRequired`；**INT-ADR-H2 `aFailedRerunTakesTheLiftsBack`** | ✅ |
| 28-5 管理模式五项权限 | 同 ⑤；另由 INT-SEC-H2 保证普通会话不能经角色拿到平台权限 | 同上 | ✅ |
| 28-6 应急会话在快照缺失时不能放行 | `AdministratorSeparationReview.java:58` | `anEmergencySessionNeedsTheSnapshot` | ✅（断言偏弱，见 INT-L14） |
| ADR-0027 守护第 1–12 条 | 名称对照见 SECURITY_INVARIANTS §4 | 全部存在且有实际断言 | ✅ |

**结论：ADR-0027 第 ①–⑧ 项和 ADR-0028 第 1–6 条都已实现。** `070` 的删除条件已满足；本批 opus 整合复审的结论就是 `080` 所等的复审，可以随本 PR 删除；`090` 保留。截至 e408317c，本 PR 还没删这两个文件，需要补上。

另有一项文档缺口：ADR-0027 第 3 项第 2 条的敏感清单少了 `separation-hold:approve` 和 `voucher-numbering-rule:approve`，代码里的 `SensitivePermissions.CODES` 是全的。见 INT-L15。

---

## 3. §14 DDD 12 项

| # | 判定 | 证据 |
|---|---|---|
| 1 单事务单聚合 | **符合（有一处 Medium）** | 审批各用例只锁、只存 `ApprovalRequest`；授权确认与生效分在两个事务；应急账号停用改由独立监听完成。<br>INT-DDD-H1（登录事务顺带登记员工）和 INT-DDD-H2（`ApplySourceSnapshot` 写 `SyncRun` 和组织快照）已在 e408317c 修好或登记。<br>残留：`HandleOrgSnapshotChange.java:57-102` 在一个事务里改 N 个账号（INT-M2）。<br>G-T1 守护偏弱，已登记 F-2。§14 正文的「已登记的例外」还没有加上 `SyncRun+OrgSnapshot` 这一条（INT-L16） |
| 2 状态机在聚合内 | 符合 | 11 个聚合使用 `kernel.StateMachine`，`StateWriteGuardTest` 只有 1 条例外。<br>Low：`JdbcOrgSnapshotRepository.java:123,148` 在 SQL 里决定撤销和离职时刻（INT-L17） |
| 3 事件由聚合产生 | 符合 | 事件都是不可变 record，都经 `DomainEvents.publish(aggregate)` 发出；有 `EventCatalogueTest`、`EventCatalogueCoverageTest`、`EventBusinessDateGuardTest`；`SyncRun` 的事件已在 `relatedAggregates` 列出 OrgSnapshot。<br>残留：生产路径不校验 businessDate（INT-M7）；覆盖守护只做文本匹配（INT-M8） |
| 4 乐观锁 | 符合 | 各仓储写入都带版本条件；没有版本条件的写都已登记，由 `RepositoryWriteGuardTest` 守护（守护漏洞已登记 B3-R2） |
| 5 法人与期间，来源不确定即失败 | 符合 | `Timesheet` 和 `ApprovalRequest` 必须带法人；查不到法人时报 422。`NOT_USED` 已登记 B1-scope-M2，G-T2 守护已登记 F-2 |
| 6 按 ID 引用 | **问题（Medium）** | `AttributionSubject.java:7` 用 `long subjectKey` 混装员工 ID 和部门 ID，并进入事件负载（INT-M10）；应用层入参用裸 long（INT-L18） |
| 7 值对象 | 符合 | 已有 `Hours`、`DocumentNumber`、`RoundNumber`、`Reason`、`LegalEntityCode`；Money 内核已登记 F-7 |
| 8 防腐层 | 符合 | `LegacyShapeTest` 加上 1 条已批准的例外（老待办视图）；`check_legacy_name` 通过 |
| 9 只读不加锁 | 符合 | `ReadPathsDoNotLockTest`、`ReadModelGuardTest`；登录查询顺带写库的问题已随 INT-DDD-H1 修掉。守护范围偏窄（INT-L6） |
| 10 错误链 | 符合（守护有残留） | `DomainException` → `ErrorCode` → ProblemDetail；错误码都登记在 glossary。<br>`ErrorCodeGlossaryTest.java:18` 的 `PENDING_MODULES` 豁免已过期（INT-L19）；契约里混进测试专用错误码（INT-M6） |
| 11 上下文映射 | **问题（Low）** | `ModuleDependencyTest` 守住了代码依赖，但 REIMAGINED_ARCHITECTURE §2.2 的图缺 audit→integration 等边，kernel 端口 `CallerPermissions` 和 `IdempotentResponses` 也没登记（INT-L20） |
| 12 统一语言 | 基本符合 | `NamingSurfaceTest` 通过；e408317c 已登记 install 和 `ORDINARY_SESSION` 相关术语。<br>复合概念（离职暂停 separation-hold、`HoldKind`、浏览器标识 browser identifier、`AttributionSubject`）和 `voucher-numbering-rule` 还没登记为提议术语（INT-L21） |

---

## 4. 跨路整合风险

| 检查项 | 结论 | 证据 |
|---|---|---|
| 管理模式五项权限 | ✅（e408317c） | 合并丢失的语义已补回：A 路按 D-1a-179 去掉权限码后，管理模式和应急会话可以办理审批（INT-SEC-H1）；修法是给普通会话附加 `ROLE_ORDINARY_SESSION` 标记，三个审批控制器改为要求它。自定义角色可以把平台权限带进普通会话（INT-SEC-H2，main 上早已存在）：`Role` 现在拒绝这类权限，`SessionPrincipals` 和 `GetCurrentUserProfile` 在普通会话里过滤掉 `PLATFORM_ONLY` |
| 敏感权限双人确认 | ✅（有一处 Medium） | `SensitivePermissions.java:10-16` 含 `separation-hold:approve`（用户已定为敏感）；按角色内含的权限判定；管理员不豁免；有 `SensitiveGrantTwoPersonIntegrationTest.separationHoldApproveIsSensitive`。残留：重新启用含敏感权限的角色或账号时，可以由一个人完成（INT-M5） |
| 未确认会话的全局闸门 | ✅ | `SessionGuardFilter.java:59-68` 对未确认会话清空全部权限（会话标记也不加）；`SecurityConfiguration.java:48-56` 只放行 current / confirm-identity / logout。我枚举了全部端点：`LegalEntityAttributionController` 的两个 `:correct` 端点、`AdministratorModeController`、`SessionController` 只要求 `isAuthenticated()`，但都会先被闸门拦下，`:correct` 在用例层另外检查权限。浏览器标识的重放检测不是原子的，见 INT-M4 |
| 数据范围 fail-closed（两个 GET） | ✅ | `OrgUnitScopes.java:62-66`；`GetOrgUnit.java:36-45` 越权时审计并返回与不存在相同的 404；`GetLegalEntityAttribution.java:40-51`；`OrgUnitScopeIntegrationTest` 8 个用例。其余接入已登记 F-1 / blocker 090 |
| 离职作废浏览器标识 | ✅ | `RevokeBrowserIdentifiersOnSeparation`；HELD 期间不发快照变更，所以保留到放行，符合 I-13；`BrowserIdentifierIntegrationTest.separationInvalidates` |
| 事件目录与 businessDate 检查是否接入全部测试上下文 | ✅ | 测试自动配置里的 `CatalogueCheckingEnvelopes` 会包住所有 `EventEnvelopes` bean；29 个 `@SpringBootTest` 都不带 `classes=`，全部接入；没有切片测试。3 处手工信封绕过检查（INT-L22）；生产路径不校验（INT-M7） |
| 迁移顺序与重复版本 | ✅ | main 上最高版本 V20261014145000，分支新增迁移最低 V20261014200000，共 48 个，全是新增，没有重复版本；没有开 out-of-order；`migrations_immutable` 报 0 条违规。迁移 DML 里有 `now()`（INT-L23）；已删除的 schema 仍在 `spring.flyway.schemas`（INT-L24） |
| 守护例外文件 | 基本 ✅ | `test-isolation-exceptions.txt` 已清空；`multi-aggregate-exceptions.txt` 两条都有理由（第二条用中文写在英文文件里，Nit）。不该登记的：`primary-key-type-baseline.txt` 末尾为两张新表登记了 bigint 主键，违反文件头「只减不增」（INT-M9）。`JdbcIdempotentResponses` 例外的理由与已登记的 B3-R3 互相矛盾，只作提示 |
| 前后端契约 | ⚠️ Medium | OpenAPI 由 `OpenApiExportTest` 导出，CI 用 `git diff --exit-code` 检查；生成物一致；文案表 150 个键与枚举逐项一致（包括新增的 `PLATFORM_IDENTITY_PERMISSION_ADMINISTRATOR_MODE_ONLY`）。但枚举里有一个测试专用的假错误码 `COST_TIMESHEET_PROJECT_MANAGER_INVALID`，在 e408317c 仍然存在（INT-M6） |
| 打包 | ✅ | `pom.xml:53-58` 中 `mainClass=OneApplication`、`layout=ZIP`、`build-info`；回放命令是独立 main，不连库、不联网，用 `PropertiesLauncher` 离线运行，由 `PackagedJarIT` 实跑 |
| UtcJvm 与部署 | ✅（e408317c） | INT-P1：部署机时区是 Asia/Shanghai，单元文件又没带 `-Duser.timezone=UTC`，原先会拒绝启动。现在 `OneApplication.main` 在启动 Spring 之前先 `installAsDefault()`。服务、迁移、运维命令都走 `-jar`，也就都经过这个入口（我已核对全部 `java -jar` 调用）。RUNBOOK E-6 已写明。残留：RUNBOOK 说 `-Duser.timezone=UTC`「作为第二道保留」，但 `runbooks/files/forcitis-one.service:13` 和 `remote_install.sh:353,365` 实际没带（INT-L25） |
| 时区 | ✅ | 业务日用注入的 `BusinessZone`，时刻用 UTC 的 `Clock`；`src/main` 的 SQL 里没有读数据库时钟的写法，`sql-database-clock-exceptions.txt` 为空。`DEFAULT now()` 已登记 R-4 |
| 与 main 合并 | 提示 | origin/main 已前移到 a78a711；`INFRA_RUNBOOK.md` 两边都改过，会冲突。解冲突时保留本分支的 E-6 |

---

## 5. 契约抽查（15 条）

**抽样方法**：先取编号 mod 7 == 3 的条目，再保证 7 个流程小节每节都有条目。

| 条目 | 流程 | 内容 | 测试 | 结论 |
|---|---|---|---|---|
| D-1a-38 | 登录与会话 | H5 企业微信静默登录与待办 | `session-gate.test.tsx`、`wecom-login.test.ts`、`inbox-page.test.tsx` | 有测试 |
| D-1a-45 | 登录与会话 | 停用、离职、角色变更写会话吊销审计 | `SessionLifecycleIntegrationTest.expiredSessionsAreAuditedThenRemoved`；`SeparationRevokesRolesIntegrationTest` | 部分：「离职写会话吊销审计」没有断言（INT-L26） |
| D-1a-10 | 组织同步 | 每日对账并可手动触发 | `OrgReconciliationIntegrationTest.headsManagersAndWecomIdsAreCompared`；`WorkforceHttpIntegrationTest.reconciliationOnDemand` | 有测试 |
| D-1a-17 | 组织同步 | 复职时重新授予 employee 并审计 | 只有 `EmployeeSyncRulesTest.rehireRestoresTheEmployee`，只断言员工恢复在职 | **应交付未做**（INT-M12） |
| D-1a-59 | 审批通用 | 候选组先认领后处理，通知发候选组全员 | `MergedInboxIT.groupTasksFollowTheClaim`；`TaskNotificationIT` | 有测试 |
| D-1a-73 | 审批通用 | 撤销不删历史 | `RetractionIT.cancelKeepsTheHistory` | 有测试 |
| D-1a-87 | 审批通用 | 规格中的路径与权限点按代码修订 | 文档类条目 | 只有间接覆盖 |
| D-1a-108 | TIMESHEET 审批 | 离职者在途待办重新取人，取不到转流程管理员 | 无 | **应交付未做**（INT-M13，同类还有 D-1a-18、D-1a-168） |
| D-1a-122 | TIMESHEET 审批 | 1a 不用 DMN，用 BPMN 扩展属性 | `BpmnRuleConventionsTest.timesheetProcessUsesThePublishedNames`；`AssigneeResolverTest` | 有测试 |
| D-1a-129 | 合并待办与通知 | 认领后其他候选人的卡片一起更新 | `TaskNotificationIT.claimMarksTheCardHandled`；`WecomDeliveryIT.markHandledReplacesTheCard` | 有测试 |
| D-1a-143 | 合并待办与通知 | 收件箱不按法人筛选 | 无 | 没有专门测试，属负向行为（INT-L26） |
| D-1a-150 | 业务编号 | 先按 ADR-0026 取法人再取号 | `NumberingRulesIT.templateWithoutLegalEntityTokenSharesOneCounter` | 部分：前半句没有断言 |
| D-1a-157 | 业务编号 | 四眼确认；凭证模板另需财务确认 | `NumberingRuleFourEyesIT`；`NumberingRuleStateMachineTest` | 有测试 |
| D-1a-164 | 审计 | 放行须填原因 | `WorkforceHttpIntegrationTest.releaseNeedsAReason` | 有测试 |
| D-1a-171 | 审计 | 按多个维度查审计 | `AuditLogIT.traceColumnsAreStoredAndSearchable` | 有测试 |

- 结果：10 条有测试，3 条部分或间接覆盖，**2 条应交付未做**（D-1a-17、D-1a-108）。
- 契约到测试的机器对照（G-T4）目前不存在：测试的 DisplayName 里只出现 33 个 D-1a 编号，约占 178 条契约的 19%。G-T4 已登记 F-2。

---

## 6. High 修复复核（e408317c）

| 编号 | 判定 | 复核要点 |
|---|---|---|
| INT-P1 | **已修** | `OneApplication.main` 在 `SpringApplication.run` 之前调用 `UtcJvm.installAsDefault()`；`UtcJvmTest.entryPointInstallsUtc`（「INT-P1…」）。服务、迁移、运维命令都走 `-jar`，都经过这个入口；回放命令不起 Spring，不受影响。残留见 INT-L25 |
| INT-SEC-H1 | **已修** | 普通会话附加 `ROLE_ORDINARY_SESSION`，管理模式和应急会话不加，未确认会话什么都不加。`ApprovalTaskController`、`ApprovalTaskRecordController`、`ApprovalTaskOverviewController` 改为 `hasRole(ORDINARY_SESSION)`。`AdministratorModeIntegrationTest.administratorModeCannotHandleApprovals` 对管理模式和应急会话逐一检查列表、详情、记录、总览和 6 种操作，全部 403，普通会话 200 |
| INT-SEC-H2 | **已修** | `Role` 拒绝 `AdministratorMode.PLATFORM_ONLY` 中的 4 项（`separation-hold:approve` 例外，符合 ADR-0028），错误码 `PLATFORM_IDENTITY_PERMISSION_ADMINISTRATOR_MODE_ONLY`。`SessionPrincipals` 和 `GetCurrentUserProfile` 在普通会话中兜底过滤（库里已有的旧角色也拿不到）。`RoleManagementIntegrationTest.platformPermissionsStayInTheAdministratorMode`；`RoleTest`。**改了断言**：`noEscalation` 原先断言「创建含 `role:update` 的角色返回 201」，现改为用 `task:read`，并给 role-editor 预置 `task:read`，原测试意图（权限上限）不变。理由已写，PR 中须单列 |
| INT-ADR-H1 | **已修** | `CorrectLegalEntityAttribution.java:66` 在 `subjects.require` 之后调用 `ownAttribution.requireNotOwn`。`correctingOwnAttributionIsRefused` 覆盖本人员工条目、本人部门两种情况：403、写审计、更正表为空。上级部门链没有单独用例，与新增、结束路径共用同一个守卫，可以接受 |
| INT-ADR-H2 | **已修** | `ReleaseSyncRun`：重跑结果不是 SUCCEEDED 时，调用 `WithdrawSyncRunReleases`（独立事务，`withdrawReleases`）撤回已解除的类别；`aFailedRerunTakesTheLiftsBack`。残留：`SyncOrgSnapshot.execute` 中 `begin.execute(trigger)` 在 try 块之外，如果它抛异常，撤回不会执行（INT-L27） |
| INT-DDD-H1 | **已修** | `PersistRegisteredEmployee` 改为 REQUIRES_NEW，账号由 `EmployeeRegistered` 或登录本身幂等开出；`LoginMissTransactionIntegrationTest.registeringTheEmployeeCommitsApartFromTheLoginTransaction` 断言登录回滚后员工登记仍在，且快照写锁不再持有到登录提交。副作用：首次登录时会同时占用两个数据库连接，并发高时有连接池压力（INT-L28） |
| INT-DDD-H2 | **已修** | `multi-aggregate-exceptions.txt` 新增 `ApplySourceSnapshot \| SyncRun+OrgSnapshot`；`SyncRunSucceeded` 和 `OrgSnapshotChanged` 的 `relatedAggregates` 列出 OrgSnapshot；`AggregateWriteGuard` 接入 workforce；`OrgSnapshotSyncIntegrationTest`「INT-DDD-H2…」。§14 正文还没同步（INT-L16） |
| b12-H7 | **已修** | 5 个跨模块测试移到 `scenario/`；`test-isolation-exceptions.txt` 只剩注释；`TestIsolationGuardTest` 通过 |

**偏离用户决定、由调度裁定推迟的两项**（按收口规则列为 High，**须回到用户确认**）：

- **INT-G1　审计在线行永不删除**
  - **偏离了什么**：用户定的是「在线 3 年 + 归档 7 年 = 满 10 年」（intent 2026-10-14-192237、192731）。代码 `AuditArchiveJob.java:32-36` 和 `platform_audit.properties:5-6` 不删在线行，`AuditArchiveJobIT` 还把「不删」当成正确行为来断言。
  - **调度的处理**：已登记 DEFERRED 和 NEXT_BATCH，标为「生产部署前完成」。首批到期在 2029 年，实际风险低。
  - **最小修法**：请用户确认这样推迟。

- **INT-G3　H5 子路径缺失时不让构建失败**
  - **偏离了什么**：用户定的是「H5 用 /h5/ 子路径」，落点写明缺失则构建失败。`frontend/apps/h5/config/public-path.mjs:7` 缺失时默认 `/h5/`，`public-path.test.ts:6` 把这个默认当作正确行为。
  - **最小修法（二选一）**：
    - 本批直接改成代码常量 `/h5/`：配置值不等于 `/h5/` 就构建失败，测试同步改写，一行级改动；
    - 或请用户确认推迟。

---

## 7. Medium / Low 清单（进下一批，带复审编号）

标「已登记」的在 NEXT_BATCH_DRAFT / DEFERRED 中已有，这里只作关联，不重复派工。

### Medium

| 编号 | 文件:行 | 一句话 | 回归测试名 |
|---|---|---|---|
| INT-M1 | `platform/approval/application/StartApprovalOnSubmission.java:75-87`；`Retraction.java:42-44` | 审批因配置故障发起不了时，工时单一直停在 SUBMITTED；重投耗尽或遇 `AccessDenied` 后既撤回不了，也不可见 | `INT-M1 审批发起失败后工时单状态可见、可撤回，修好配置后重投能恢复` |
| INT-M2 | `platform/identity/application/HandleOrgSnapshotChange.java:57-102` | 一个监听事务里撤 N 个人的权限，一人冲突就回滚整批 | `INT-M2 离职处理一人冲突不回滚他人撤权` |
| INT-M3 | `HandleOrgSnapshotChange.java:84,107-108` | 监听器直接写 `session.revoked` 审计，没有 causation，事件重投时重复写（原 M-8 / M-9 残留） | `INT-M3 快照变更事件重投不重复写会话吊销审计` |
| INT-M4 | `platform/identity/infrastructure/persistence/JdbcBrowserIdentifiers.java:34-40`；`BrowserIdentity.java:55-77` | 浏览器标识先查后换，不检查更新行数；同一旧值并发使用两次都会免确认，不报重放（I-13） | `INT-M4 concurrentUseOfOneValueIsAReplay` |
| INT-M5 | `platform/identity/application/UpdateRole.java:74-76`；`ChangeAccountStatus.java:37` | 重新启用含敏感权限的角色或账号时，一个人就能完成，不经第二人确认 | `INT-M5 重新启用含敏感权限的角色或账号需第二人确认` |
| INT-M6 | `platform/web/OpenApiProblems.java`（`errorCodes()`）；`src/test/.../ErrorChainTest.java:31-33`；`frontend/packages/core/src/error-messages.ts:13` | 测试专用的 `StubError` 枚举被扫进 OpenAPI 和前端文案表 | `INT-M6 OpenAPI 的 ErrorCode 枚举等于 src/main 常量集合` |
| INT-M7 | `platform/eventing/infrastructure/ContextualEventEnvelopes.wrap` | businessDate 与事件目录只在测试里核对，生产路径不校验 | `INT-M7 生产封装按目录校验 businessDate，缺失或多余即失败并告警` |
| INT-M8 | `src/test/.../EventCatalogueCoverageTest` | 用 `String.contains` 判断事件「被封装过」 | `INT-M8 目录覆盖按实际封装记录判定` |
| INT-M9 | `backend/architecture/primary-key-type-baseline.txt`（末尾 2 行） | 新表 `process_archive_skips`、`audit_archive_batches` 被登记进「只减不增」的基线 | `INT-M9 主键基线只含 origin/main 已有的表`（关联已登记 R-1） |
| INT-M10 | `organization/workforce/api/AttributionSubject.java:7` | 裸 long 混装员工 ID 和部门 ID，并进入事件负载 | `DDD-6 api 与事件负载不含裸 long 标识` |
| INT-M11 | `platform/approval/web/ApprovalTaskController`（decide）；`src/test/.../ErrorContractIT.java:81` | `decide` 请求体缺 outcome 时返回 500，并被登记为已知故障 | `INT-M11 decide 缺 outcome 返回 422 ProblemDetail` |
| INT-M12 | `platform/identity/application/AccountProvisioning.java:38-50`；`HandleOrgSnapshotChange` | **应交付未做**：D-1a-17 复职不重新授予 employee。ADR-0027 ① 离职已删除全部角色，复职者因此没有任何角色 | `INT-M12 D-1a-17 复职重新授予 employee 并审计` |
| INT-M13 | `platform/approval/`（没有离职监听） | **应交付未做**：D-1a-18 / 108 / 168，离职者在途待办重新取人，取不到转流程管理员并告警、审计 | `INT-M13 D-1a-108 离职者在途待办重新取人` |
| INT-M14 | `src/test/.../scenario/SyncLockoutGuardIntegrationTest.java:104` | 仍在等整张 `event_publication` 表清空，有连锁超时风险（原 M-17 残留） | `INT-M14 场景测试只等本用例相关的事件` |
| INT-M15 | `src/test/.../ProcessArchivePolicyTest.java:36-55` | 归档变量清单与常量逐项比对，但引擎新增的 `VARIABLE_*` 常量不强制分类（用户决定落点「新增变量显式分类」只部分实现） | `INT-M15 引擎全部 VARIABLE_ 常量都须在 archive-variables.txt 分类（归档或排除）` |
| INT-M16 | `src/test/.../fixture/doubles/AggregateWriteGuard.java:41,66,83` | G-T1 只拦截 `save` / `insert`，例外不绑定用例类 | 已登记 F-2，补充具体缺口：`DDD-1 守护拦截 insertIfAbsent / reserveNext 等全部写方法` |

### Low

| 编号 | 文件:行 | 一句话 | 回归测试名 |
|---|---|---|---|
| INT-L6 | `src/test/.../ReadModelGuardTest.java:21-23`；`ReadPathsDoNotLockTest` | 读模型守护只覆盖 3 个包，不管 `Find*` / `Search*` | `DDD-9 Find* 用例只读且不加锁` |
| INT-L9 | `platform/integration/.../DeliverWecomCards.java:126-127` | 投递进入 DEAD_LETTER 不发事件 | `INT-L9 投递进入 DEAD_LETTER 发出不入审计的事件` |
| INT-L10 | `platform/approval/domain/ApprovalRequest.java:58-64` | REJECTED→RUNNING（D-1a-78）与 D-1a-89 只写了 RETURNED→RUNNING，请调度核对 | `INT-L10 重启起点与契约一致` |
| INT-L11 | `kernel/KernelConfiguration.java:33`；`platform/integration/.../WecomConfiguration.java:33` | 业务时区带默认值；还有一处 `getIfAvailable(Clock::systemUTC)` 兜底 | `INT-L11 缺 business-zone 启动失败` |
| INT-L12 | Flowable 配置 | 没配 `StrongUuidGenerator`，任务 ID 是顺序数字 | `INT-L12 引擎 ID 不可推测`（可随 R-1） |
| INT-L13 | `frontend/scripts/copy-allow-list.yaml`（`standalone`） | 清单里有单字词，削弱用语守护 | `copy-glossary.test.mjs`：`standalone` 不得含单字词 |
| INT-L14 | `src/test/.../EmergencyLoginIntegrationTest`、`AuditExportAndOwnRecordsIT`、`SyncLockoutGuardIntegrationTest.anEmergencySessionNeedsTheSnapshot` | 缺「快照过期」用例；缺离职吊销记录的造数；应急放行用例不断言错误码，也没有正向对照 | `INT-L14 …`（三个用例） |
| INT-L15 | `docs/modernization/adr/src/ADR-0027.md` 第 3 项第 2 条；`SensitivePermissions.CODES` | 文档敏感清单与代码不一致；测试只断言 `contains`，不断言集合相等 | `INT-L15 敏感清单集合相等`（ADR 正文冻结，用新 ADR 或 SECURITY_INVARIANTS 对齐） |
| INT-L16 | `docs/modernization/DATA_DESIGN_CHECKLIST.md:24` | §14「已登记的例外」还没写入 `ApplySourceSnapshot SyncRun+OrgSnapshot` | 文档项（psa-coordinator） |
| INT-L17 | `organization/workforce/infrastructure/.../JdbcOrgSnapshotRepository.java:123,148` | 撤销和离职时刻由 SQL 推出，不由领域对象给出 | `DDD-2 撤销与离职时刻由领域对象给出` |
| INT-L18 | `ReplaceAccountRoles.java:56`、`ChangeAccountStatus.java:31`、`UpdateRole.java:53`、`DeleteRole.java:39`、`ReleaseSyncRun.java:45`；`kernel/IdempotentResponses.java:21-27` | 应用层与 kernel 端口用裸 long | `DDD-6 应用服务与 kernel 端口使用类型化 ID` |
| INT-L19 | `src/test/.../ErrorCodeGlossaryTest.java:18` | `PENDING_MODULES` 豁免已过期 | `DDD-10 错误码守护无模块豁免` |
| INT-L20 | `REIMAGINED_ARCHITECTURE.md` §2.2；`module-dependencies.txt` | 架构图与依赖登记不一致；kernel 端口没登记 | `DDD-11 上下文映射的边与 module-dependencies.txt 一致`（`OrgUnitAccess` 已登记 B1-scope-M2） |
| INT-L21 | `backend/glossary/proposed/` | `separation-hold`、`HoldKind`、browser identifier、`AttributionSubject`、`voucher-numbering-rule` 没登记为提议术语 | 登记补齐 |
| INT-L22 | `src/test/.../EventPublicationOperationsIT:76`、`AuditDomainEventsIT:66`、`AggregateRootTest:39` | 手工构造信封，绕过目录检查 | `ArchitectureRulesTest`：测试不得直接 `new EventEnvelope` |
| INT-L23 | `platform_numbering` 迁移 V20261014135200:11、V20261014135300:3、V20261015000300:78 | 数据迁移 DML 用 `now()` | 并入已登记 R-3 / R-4 的说明 |
| INT-L24 | `application.properties:15` | 已删除的 `delivery_execution` 仍在 `spring.flyway.schemas` | `SchemaPlacementIT`：flyway schemas 不含已退役的 schema |
| INT-L25 | `docs/.../infra/runbooks/files/forcitis-one.service:13`；`scripts/release/remote_install.sh:353,365` | RUNBOOK 说 `-Duser.timezone=UTC` 作为第二道保留，部署物实际没带 | `scripts/release` 自测：每条 `java … -jar` 都带该参数 |
| INT-L26 | 契约抽查 | D-1a-45「离职写会话吊销审计」、D-1a-143「收件箱不按法人筛选」、D-1a-150 前半句没有断言 | `INT-L26 D-1a-45 / 143 / 150 …`（随 F-2 G-T4） |
| INT-L27 | `organization/workforce/application/SyncOrgSnapshot.java`（`begin.execute` 在 try 块之外） | 开始新运行这一步抛异常时，已解除的类别不会撤回 | `INT-L27 放行时开始运行失败也撤回已解除类别` |
| INT-L28 | `PersistRegisteredEmployee`（REQUIRES_NEW） | 首次登录同时占用两个连接，并发高时有连接池压力 | `INT-L28 并发首次登录不耗尽连接池`（或在 RUNBOOK 写明池大小） |
| INT-L29 | `V20261015000800`；`V20261015000000:21-23` | 系统角色种子直接授予敏感权限（ADR 允许）；F-1「敏感权限只授给人」落地时须写明系统角色豁免 | 随 F-1 |
| INT-L30 | `platform/web/IdempotencyFilter.java:59-67` | 权限收回后，24 小时内仍能取回自己缓存的旧响应（不跨用户、没有副作用） | `INT-L30 幂等重放前复核授权` |
| INT-L31 | `platform/approval/domain/ApprovalRequest.java:330-332`；`GlobalExceptionHandler.java:100-107`；`MarkWecomCardHandled.java:41-43` | aggregateId 不含流程类型；MVC 标准异常一律打成「请求无效」；卡片只认第一条「已处理」说明 | `INT-L31 …`（三个用例） |
| INT-L32 | `DEFERRED.md:29`；`CROSSCHECK.md:12`；INTENT「专业产值切分纳入 Phase 2」 | 文档条目过期；Phase 2 清单没收这一条 | 文档项 |

**已登记、不重复的关联项**：B1-scope-M2（`NOT_USED`）、F-2（G-T1、G-T2、G-T4）、B3-R2、B3-R3、B3-R5、B3-R7、R-1、R-3、R-4、INT-G1、INT-G3（后两项是否推迟待用户确认，见 §6）。

---

## 8. 合并前必须做

1. **INT-G1**：经 one-coordinator 请用户确认「审计在线行到期删除推迟到下一批，生产部署前完成」。用户不同意就本批实现。
2. **INT-G3**：二选一。本批把 `public-path.mjs` 改成常量 `/h5/`（配置值不等于 `/h5/` 就构建失败，`public-path.test.ts` 同步改写）；或请用户确认推迟。
3. **本 PR 删除 `status/blockers/070` 和 `080`**（ADR-0027 ①–⑧ 和 ADR-0028 都已实现，本复审即为 080 所等的 opus 复审），并重新运行 `build_all.py`、提交生成产物；`090` 保留。
4. **PR 正文单列改过的断言**：`RoleManagementIntegrationTest.noEscalation`，从 `role:update` 改为 `task:read`（理由 INT-SEC-H2）；`test-isolation-exceptions.txt` 中删除的例外（b12-H7）。
5. **合入 main 时**：`INFRA_RUNBOOK.md` 会冲突，保留本分支的 E-6 写法。合并后在整合提交上重跑全部守护。

---

## 收口记录（psa-1a-platform-bridge-reimagine）

- INT-G3：本批已修（H5 子路径改为常量 `/h5/`，`H5_PUBLIC_PATH` 只能重复该值，否则构建失败；`public-path.test.ts`「INT-G3 …」）。
- INT-G1：用户 2026-10-15 确认「同意推迟，生产部署前完成 (Recommended)」，已登记 DEFERRED。
- `status/blockers/070`、`080` 随本 PR 删除；`090` 保留。
