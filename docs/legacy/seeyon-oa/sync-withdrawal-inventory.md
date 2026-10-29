# ADR-0055 落地盘点：撤同步、登录补建、老待办、致远票据（只读盘点）

- 基线：工作树 `elated-mclaren-be9a20`，分支 `claude/one-1a-par-a8-split`，HEAD `8cddaee3` = `origin/main`。本地 `main` 分支已落后，不能用它核对。
- 路径缩写：`M/` = `backend/src/main/java/com/forcitis/one/`，`T/` = `backend/src/test/java/com/forcitis/one/`，`R/` = `backend/src/main/resources/`，`A/` = `backend/architecture/`，`D/` = `docs/`，`F/` = `frontend/`。
- 归类代号：**删** = 删除；**命令** = 改为人事命令；**留** = 保留；**迁** = 只留迁入适配器；**证** = 待取证。
- 依据：ADR-0055 决定 2（`:56`）、3（`:66-71`）、4（`:75-77`）、5（`:81-84`）、6（`:88-94`）、7（`:98`），守护节（`:123-128`）。
- 本盘点不改仓库文件。“有/没有”的结论都用 grep 核实过；读不到的标“未核实”。

---

## 1. 应用类与端口

### 1.1 organization.workforce 同步主链（`M/organization/workforce/`）

| 类 | 位置 | 现状 | 归类 | 理由 |
|---|---|---|---|---|
| `SyncOrgSnapshot` | application/SyncOrgSnapshot.java（60 行）；`:4-5` import `integration.api.OrgSource`、`SourceOrgSnapshot` | 定时读 A8 快照，驱动整次同步 | 删 | ADR-0055:88 |
| `BeginSyncRun` / `FailSyncRun` / `ApplySourceSnapshot` | application/ApplySourceSnapshot.java:30-86 | 对账、缩水保护、管理员离职暂停；`:73` 写快照与版本，版本日 = `businessZone.dateOf(run.startedAt())`（`:46`，即同步观察日）；`:50` 调 `SeparationReview` | 删；暂停逻辑改为“命令” | 管理员离职暂停改挂离职命令（ADR-0055:68）；版本按人事业务生效日（:62） |
| `ReleaseSyncRun` / `ReleaseSyncRunHolds` / `RecordSyncRunRelease` / `WithdrawSyncRunReleases` | application/ReleaseSyncRunHolds.java:25-53，`:38-39` 写“only the operations command can release this run”；RecordSyncRunRelease.java:38 审计动作 `organization.workforce.sync-run.released` | 两类暂停分权放行 | 缩水放行删；离职放行改为“命令”（只放该条离职） | ADR-0055:68、:90 |
| `GetSyncRun` / `ListSyncRuns` / `ListHeldSyncRuns` / `SyncRunView` / `SyncRunDetailView` / `SyncDiffView` / `SyncResult` / `ReleaseResult` | application/* | 同步记录读模型 | 删；待放行离职另做读模型 | — |
| `ReconcileOrgSnapshot` / `RunOrgReconciliation` / `OrgReconciliationView` | application/ReconcileOrgSnapshot.java（用 `OrgSource`）；web/OrgReconciliationController.java:19 `POST /sync-runs:reconcile` | 每日拿快照与 A8 比对 | 运行时删；迁入核对另作适配器（迁/证） | ADR-0055:58“保留迁入映射、差异与核对” |
| `RefreshShortNames` / `ShortNameRefreshJob` / `ShortNameAdapter` / `ShortNameSource` | application/RefreshShortNames.java；infrastructure/external/ShortNameAdapter.java（用 `integration.api.OrgShortNames`） | 每天从老系统刷部门简称 | 删；`org_units.short_name` 的维护方见 §8 | ADR-0055:88（LegacyOrgNames） |
| `SnapshotFreshness` | application/SnapshotFreshness.java:24-62，读 `sync.stale-after` / `sync.cron` | 判断快照是否“过期” | 删 | ADR-0055:66：不设外部快照不可用分支 |
| 领域对象：`SyncRun*`、`SyncDiff`、`SyncTrigger`、`SyncedFields`、`RunStats`、`ShrinkGuard`、`SnapshotReconciler`、`SnapshotChange`、`Reconciliation`、`OrgReconciliation*`、`ChangeKind`、`EntityKind`、`IssueKind`、`SourceTerms`、`EmployeeChecks` | domain/* | 同步专用 | 删；`SourceTerms`、`EmployeeChecks` 有一部分可能进迁入适配器（迁/证） | — |
| 持久化：`JdbcSyncRunRepository` / `JdbcSyncRunQueries` / `SyncRunRows` | infrastructure/persistence/* | — | 删 | — |
| `JdbcOrgSnapshotRepository` / `OrgSnapshotRows` / `OrgHistory`（快照整表写与版本写） | infrastructure/persistence/* | 当前唯一写组织、人员、任职及其版本的路径 | 改为“命令”（命令逐条写，不整表对账） | ADR-0055:56 |
| 作业：`OrgSnapshotSyncJob` / `SyncPurgeJob` / `OrgReconciliationJob` / `ShortNameRefreshJob`；`SyncMetrics` | infrastructure/scheduling/*、metrics/SyncMetrics.java | — | 删 | — |
| `SyncRunController` 及 Request/Response | web/SyncRunController.java:30 `/api/v1/organization/sync-runs`、`:58` `GET /{id}`、`:66` `POST /{id}:release` | — | 删；待放行离职另设接口 | — |
| `ExternalSubjectLookup`（实现 kernel `ExternalSubjectEmployees`） | application/ExternalSubjectLookup.java；kernel/ExternalSubjectEmployees.java 注明是“临时端口，C1 落地后删除或改形” | 运维按 A8 登录名或企业微信 ID 查人 | 证 | 企业微信分支要留；`SEEYON_LOGIN_NAME` 分支随 A8 撤 |

### 1.2 登录补建（`M/organization/workforce/application/`）

| 类 | 位置 | 归类 | 理由 |
|---|---|---|---|
| `RegisterEmployeeOnLogin`（企业微信未命中时向 A8 单人查询） | RegisterEmployeeOnLogin.java:4-6 用 `OrgSource`、`StaffLookup` | 删 | ADR-0055:56、:88 |
| `RegisterEmployeeOnLoginName`（按 A8 登录名） | RegisterEmployeeOnLoginName.java:3-5 | 删 | 同上 |
| `LoginMissRegistration`、`PersistRegisteredEmployee`、`domain/RegisteredEmployee`、事件 `EmployeeRegistered` | LoginMissRegistration.java（`@SystemActorName("organization.workforce.login-registration")`） | 删；`EmployeeRegistered` 由入职命令事件取代（命令） | 建档改走入职命令 |
| `OrgDirectoryService.findEmployeeByLogin` 的补建分支 | OrgDirectoryService.java:59-73（`:61`、`:71` 的 `.or(() -> register…)`） | 改为只查本地 | — |

### 1.3 api 端口（`M/organization/workforce/api/`）

| 端口或方法 | 位置 | 是否读 A8 或快照 | 归类 |
|---|---|---|---|
| `OrgDirectory.findEmployee` / `findSupervisor` / `findOrgUnitHead` / `findRegionalReceivablesManager` / `findOrgUnit` / `findAncestors` | OrgDirectory.java:8、17-26 | 只读本地表（`OrgSnapshotQueries`），不读 A8 | 留（实现的“快照”名改为本地人事记录） |
| `OrgDirectory.findEmployeeByLogin(LoginLookup)` | OrgDirectory.java:10-11，注释写“未知时回落到单人来源查询” | 读 A8（经补建） | 改为只查本地；`ByLoginName` 随致远 SSO 撤（删/证） |
| `OrgDirectory.employmentOf` → `EmploymentVerdict` | OrgDirectory.java:13-14；EmploymentVerdict.java:8-13（`SNAPSHOT_UNAVAILABLE`、`NOT_LISTED`）；实现 OrgDirectoryService.java:48-57 读 `SnapshotFreshness` | 读同步运行表 | 改：本地记录缺失或无效即拒绝并告警（ADR-0055:66），`SNAPSHOT_UNAVAILABLE` 删 |
| `LoginLookup.ByLoginName` | LoginLookup.java:11 | A8 登录名 | 删或证（随 `LoginWithSso`） |
| `EmploymentStatus`（只有 EMPLOYED / SEPARATED） | EmploymentStatus.java:4-7，注释“无来源登录名即离职” | — | 留；ADR 有“停薪留职”状态，见 §8 |
| `SyncRunId`、`HoldKind` | SyncRunId.java、HoldKind.java:4-7 | — | 删；`ADMINISTRATOR_SEPARATION` 的语义移到待放行离职 |
| `EmployeeExistence` | EmployeeExistence.java | 本地 | 留 |
| kernel `SeparationReview` | `M/kernel/SeparationReview.java:10-28`（注释“Asked before a sync run turns people into separated ones”） | — | 改：调用方由同步改为离职命令（命令） |

### 1.4 platform.identity（`M/platform/identity/application/`）

| 类 | 位置 | 现状 | 归类 |
|---|---|---|---|
| `HandleOrgSnapshotChange` | `:65-84` 订阅 `OrgSnapshotChanged`；`:69-71` 对离职者调 `separate()`；`:72-81` 对变更的在职员工调 `ensureAccount` 开账号；`:96-117` 用 `recordSeparation()` 清 `account_roles`，没有写 `account_role_grants` | 离职收回加开账号 | 改为订阅离职、入职、复职命令事件（命令）；同步订阅删 |
| `DisableEmergencyAdminOnSeparation` | `:42-50` 订阅 `OrgSnapshotChanged`，经 `SeparatedPeople` | 停用应急账号 | 改为订阅离职命令事件（命令） |
| `SeparatedPeople` | `:16-23` 从快照事件算离职名单 | — | 删 |
| `AdministratorSeparationReview`（实现 `SeparationReview`） | `:30-49` 留任、下限 2（单人模式 1）；`:51-58` 在职判定用 `directory.findEmployee` | 管理员离职暂停判定 | 留，改为由离职命令调用（命令） |
| `HandleEmployeeRegistered` | `:24-25` 订阅 `EmployeeRegistered` 开账号 | — | 改为订阅入职命令事件（命令） |
| `AccountProvisioning.ensureAccount` / `existingOrOpened` / `accountForOperations` | AccountProvisioning.java:50-80 | 只读本地 `OrgDirectory.findEmployee`，在职才开账号 | 留；调用点见 §7 |
| `LoginAdmission` | `:56` 在职判定；`:59` 登录时 `ensureAccount` | 登录时补开账号（不是补建人员） | 证（见 §8） |
| `LoginAsEmergencyAdmin` | `:98-112` 用 `employmentOf`；`:109` 的 `SNAPSHOT_UNAVAILABLE` 分支告警后放行 | 违背 ADR-0055:66 | 改（命令，安全项） |
| `SessionPrincipals` | `:51-56` 每次请求查在职 | 本地 | 留；管理模式分支 `:48-49` 是否另查在职未核实 |
| `WecomIdentification` | `:55` `findEmployeeByLogin(ByWecomUser)` | 会触发补建 | 留，补建去掉后只查本地 |
| `LoginWithSso` + integration `SsoTicketVerifier` / `SeeyonTicketVerifier` | LoginWithSso.java:63 `ByLoginName`；web/SessionController.java:95-100 `POST /sessions:sso-login` | 致远门户单点登录 | 删（ADR-0055:51 不保留 A8 单点）；时点见 §8 |
| `LoginWithSignedTicket` + `ResolveTicketSubject` / `TicketSubjects` | SessionController.java:103-108 `POST /sessions:ticket-login` | 签名票据（签发方如 km） | 证（ADR-0055:92：身份生命周期研究与 T-13 之后） |

### 1.5 platform.integration（`M/platform/integration/`）

| 类 | 位置 | 归类 | 理由 |
|---|---|---|---|
| `api/OrgSource`、`StaffLookup`、`SourceOrgSnapshot`、`SourcePerson`、`SourceOrgUnit`、`SourcePosition`、`SourceRelationship*`、`SourceGender`、`SourceRegionKind`、`SourceRecordState` | api/* | 运行时用途删；迁入是否沿用这些类型待定（迁/证） | 使用方仅 workforce 同步、补建与 seeyon 实现 |
| `infrastructure/seeyon/SeeyonOrgSource`、`SeeyonViewReader`、`SeeyonViewSql`、`SeeyonOrgTree`、`SeeyonMapping`、`SourceIdTranslation`（运行时分配 `source_references`）、`DisabledOrgSource`、`SeeyonConfiguration`、`SeeyonStartupCheck`、`SeeyonSchemaInspector`、`*Row` | seeyon/* | 迁（一次性迁入适配器）或删，证 | ADR-0055:81；ADR-0036 迁入 |
| `seeyon/SeeyonTicketVerifier`、`api/SsoTicketVerifier`、`SsoVerdict` | — | 删（随致远 SSO） | ADR-0055:51 |
| `infrastructure/legacy/JdbcLegacyInbox`、`LegacyInboxConfiguration`、`LegacyInboxProperties`、`InboxMerger`、`LegacyTodoRow`；`api/LegacyInbox`、`LegacyTodo` | legacy/*；approval/application/ListInbox.java:9、26、30、52 合并老待办 | 删 | ADR-0055:90 |
| `legacy/LegacyOrgNames`、`DisabledOrgNames`、`LegacyConfiguration`、`LegacyProperties`；`api/OrgShortNames` | — | 删（或迁，证） | ADR-0055:88 |
| `application/IntegrationOperationsCommands`（`find-accounts-by-external-subject`，`:24-56`，含 `SEEYON_LOGIN_NAME`） | — | 证 | 企业微信分支留 |
| `infrastructure/connection/*`（只读连接、只读校验命令） | — | 留，收窄到迁入、对账、取证用途 | ADR-0055:98 |
| `SourceReferences` / `AllocateOwnId` / `ResolveSourceId` / 来源对照 Web | — | 留 | 投产后全表只读（TRN-06，ADR-0055:84） |

### 1.6 事件订阅者

- `OrgSnapshotChanged` 只有 identity 订阅：`HandleOrgSnapshotChange.java:66`、`DisableEmergencyAdminOnSeparation.java:43`。grep 核实 approval 没有订阅。
- `EmployeeRegistered` 只有 identity 订阅：`HandleEmployeeRegistered.java:24`。
- `SyncRunHeld/Succeeded/Failed/Released/HoldReleased`：main 里没有业务订阅者，只进审计（MUST_AUDIT，见 §6）和 asyncapi 的 `handleSyncRunHeldInPlatformAudit`。

---

## 2. 表与 DDL

### 2.1 organization_workforce（`R/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql`）

| 对象 | 行 | 归类 | 说明 |
|---|---|---|---|
| 头注释“组织快照：只读，外部组织源为源” | :1、:3 | 改 | 本系统是记录系统 |
| `positions` 注释“组织源岗位的快照” | :17 | 改为“命令” | — |
| `org_units.short_name`（“每天从老系统只读刷新”） | :38、:61 | 证 | 维护方待定 |
| `org_units.last_sync_run_id` | :40、:63 | 删或改为命令 ID | 悬空指针（DATA-REPAIR-workforce WF-13） |
| `employees.login_name`（“为空表示已离职 RULE-363”） | :72、:95 | 证 | A8 登录名；离职改由命令判定 |
| `employees.separated_at`（“首次同步到离职的时间”） | :101 | 改为“命令” | 人事业务离职日（W2 E-2） |
| `employees.last_sync_run_id` | :79、:102 | 删或改 | — |
| `employees.event_count` 注释“登录时登记发一个” | :106 | 改 | — |
| `sync_runs`（含 `hold_kind_codes`、`released_*`） | :128-189 | 删 | ADR-0055:88 |
| `sync_diffs` | :191-217 | 删 | 同上 |
| `sync_run_held_separations`；FK `sync_run_id → sync_runs ON DELETE CASCADE` | :463-480（FK 在 :471） | 用途改，表结构须重做 | 待放行离职要保留，但现表挂在同步运行上 |
| `org_unit_versions` / `assignment_versions` 的 `effective_from_date`（“按业务时区截日”，同步写入时取观察日） | :319-390 | 留结构，改写入口径 | 按人事业务生效日（W2） |
| 版本表与法人表的只追加触发器 | :521-571 | 留 | — |

### 2.2 platform_identity（`R/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql`）

| 对象 | 行 | 归类 |
|---|---|---|
| `account_role_grants.revocation_sync_run_id uuid` | :447 | 改名为 `revocation_separation_command_id`（ADR-0055:70，基线开放期直接改基线） |
| CHECK：`(cause='SEPARATION') = (revocation_sync_run_id IS NOT NULL)`，且 `SEPARATION` 时 `revoked_by` 为空 | :469-470 | 随列改名 |
| 只追加触发器的关闭列清单（含 `revocation_sync_run_id`） | :499-501（函数在 :339-353） | 随列改名 |
| 列注释“触发离职收回的同步运行 ID” | :518 | 改 |
| `account_roles`（现有角色授予，`JdbcAccountRepository.java:63-173` 仍在读写） | :84 | 留到 ADR-0034-IMPL PR-3 |
| `sso_ticket_uses` | :145-162 | 证（签名票据的 nonce 也用这张表） |
| `ticket_issuers` | :164-184 | 证 |
| `login_states` | :126-143 | 留（企业微信） |
| `emergency_admins` | :186-215 | 留 |
| 导航项 `/admin/sync-runs`（`organization.workforce:sync-run:read`） | :540 | 删 |
| 权限授予 `org_data_admin` → `sync-run:read`、`sync-run:execute` | :557-558 | 删 |
| `security_auditor` → `organization.workforce:separation-hold:approve` | :574-578 | 留（管理员离职独立放行权限，ADR-0055:90） |

- 核实结果：main 的 Java 代码不读写 `account_role_grants`，grep 无命中。PR-1 只建了表。

### 2.3 platform_integration（`R/db/migration/platform_integration/V20261014133000__platform_integration_baseline.sql`）

- `source_references`（:134-187）及守卫触发器（:189-222）：**留**。注释（`confirmation_status`、`created_by`、`decided_by`）有“来源同步分配”的写法，要改。投产后全表只读的约束如何落地（新触发器还是授权），ADR 未写。

---

## 3. 事件、权限码、审计动作、OpenAPI、错误码、配置

### 3.1 事件

- Java：`M/organization/workforce/events/`
  - `OrgSnapshotChanged.java:7-15`：负载有 `changedEmployees`、`separatedEmployees`、`changedOrgUnits`、`effectiveFrom`；注释写“来源未给生效日，用截止日”。
  - `SyncRunFailed`、`SyncRunHeld`、`SyncRunHoldReleased`、`SyncRunReleased`、`SyncRunSucceeded`、`EmployeeRegistered`。
  - 以上全部**删**，由人事命令事件取代（命令）。
- 目录：`D/events/workforce.yaml:4、29、55、81、117、154`（`publisherTest` 指 `SyncRunStateMachineTest`）。`D/events/asyncapi.yaml` 的 channels 在 :230-258，operations 从 :998 起。asyncapi 由 `build_asyncapi.py` 生成，不手改。
- `AccountSeparated`（`D/events/identity.yaml:218`）：**留**。

### 3.2 权限码

- `M/platform/identity/api/Permission.java:63-65`：`ORGANIZATION_WORKFORCE_SYNC_RUN_READ`、`…_SYNC_RUN_EXECUTE` **删**；`…_SEPARATION_HOLD_APPROVE` **留**。
- `M/organization/workforce/web/WorkforcePermissions.java:6-8`：同上。
- 种子行见 §2.2。

### 3.3 审计动作

- `RecordSyncRunRelease.java:38`：`organization.workforce.sync-run.released`，主体类型 `AuditSubjectKind.SYNC_RUN`。改为离职放行动作（命令）。
- `HandleOrgSnapshotChange.java:91、114-115`：`platform.identity.session.revoked` 和 `platform.identity.account.role.revoked`，原因写 `cause=separation`。动作名留，执行体换。

### 3.4 OpenAPI

文件 `F/packages/api/openapi.json`：
- 路径：`sync-runs` 四条在 :2406、:2460、:2497、:2544；`sessions:sso-login` 在 :5219；`sessions:ticket-login` 在 :5257。
- Schema：:381-385、:1313-1355。
- `InboxResponse` 的 `legacyAvailable`、`legacyCount`：:644-667。

### 3.5 错误码

- `M/organization/workforce/api/WorkforceError.java`：
  - `:6-7`、`:21` 的 `SYNC_RUN_*` **删**。
  - `:30-31` 的 `SEPARATION_HOLD_FORBIDDEN/REFUSED` **留**（改为离职放行）。
  - `RELEASE_REASON_REQUIRED` **留**。
- `IdentityError`：`TICKET_INVALID`、`TICKET_REPLAYED`、`SSO_UNAVAILABLE` 随票据处理（删/证）；`EMPLOYEE_SEPARATED` **留**。
- 前端文案：`F/packages/core/src/error-messages.ts:51、54-56、121、123-124`。

### 3.6 配置与 profile

- `R/config/organization_workforce.properties`：
  - `:5-10` `sync.*`
  - `:13-14` `reconciliation.*`
  - `:17-18` `short-name.*`
  - `:22-24` `sync-purge.*`
  - 全部**删**。
- `R/config/platform_integration.properties`：
  - `:18-21`、`:27` `seeyon.views.*`：迁/证。
  - `:31-32` `seeyon.sso.*`：删。
  - `:35-39` `legacy.*`、`:44` `legacy.inbox.view-name`：删。
- `R/config/platform_identity.properties`：`:36` `sso.legacy-login-url`、`:47` `login-throttle.limits.ticket-login`，随票据处理。
- 各 profile 文件：
  - `R/application-identity-operations.properties:9-10`、`R/application-integration-operations.properties:12-13`：关同步与简称的开关，随删。
  - `R/application-integration-operations.properties:6`：`SEEYON_LOGIN_NAME`，证。
  - `R/application-read-only-verification.properties:4`：连接名单含 legacy，改。
  - 测试资源 `application-default.properties:15` 的 `sync-purge.enabled`。
- profile `identity-operations`（`M/platform/identity/infrastructure/operations/OperationsCommandRunner.java:21`）的运维命令只有应急账号与 grant/revoke-role（`OperationsCommands.java:89-95`），**没有**“放行后 0 名超管时双人在场放行”的命令（grep 核实）。

---

## 4. 前端（`F/`）

- 同步记录页：
  - `apps/web/src/app/app-routes.tsx:8、42`（`/admin/sync-runs`）
  - `apps/web/src/modules/workforce/pages/sync-runs-page.tsx`（`:5`、`:23-26`、`:77-82` 放行）及其测试
  - `modules/workforce/index.ts:4`
  - `model/sync-run.ts`
  - `api/workforce-api.ts:7-16`
  - `hooks/use-workforce.ts:3、5、11、32`
  - 归类：删。这是现有唯一的放行界面，待放行离职需要新界面（命令）。
- 部门简称列：`modules/workforce/pages/org-units-page.tsx:9`（`unit.shortName`）。证。
- 致远 SSO 与签名票据：
  - `modules/session/api/session-api.ts:15-20`
  - `pages/login-callback-page.tsx:7、14-15、19、29-33、48、60`（`sso`、`ticket` 分支，`legacyLoginUrl`）
  - `packages/core/src/failure.ts:7、12-13`
  - `packages/api/src/http.test.ts:139-148`
  - 归类：sso 分支删；ticket 分支证；wecom 分支留。
- 老待办：
  - `apps/web/src/modules/approval/pages/inbox-page.tsx:79、86`
  - `apps/h5/src/modules/approval/pages/inbox-page.tsx:83、90`
  - `packages/core/src/approval/model.ts:12、17、23-26、36-39、49`
  - 对应测试与 `apps/h5/README.md:9`
  - 归类：删。
- 生成产物 `packages/api/src/generated/*`：后端 OpenAPI 改完后重新生成，不手改。
- grep 核实：前端没有人员补建入口。

---

## 5. 测试与守护

### 5.1 守护类（均已核实存在）

- **`T/SecurityInvariantsTest.java`**
  - `:33-53` I-2 扫 `ticket_issuers`，依赖 `A/ticket-issuers-allowlist.txt`：随票据处理。
  - `:25-27` I-1：留。
  - 离职与放行不变量在这里没有直接引用；ADR-0055:125 点名要改，具体改哪段未核实。
- **`T/platform/identity/EmergencyLoginIntegrationTest.java`**
  - `:197-204` 离职拒绝：改为命令。
  - `:209-210` `unavailableSnapshotIsAllowed`：随 ADR-0055:66 删除。这是**删断言**，PR 中须单列理由。
- **`T/acceptance/platform/identity/EmergencyLoginEmploymentIT.java`**：`:125-135` 清 `sync_runs`、`sync_run_held_separations` 并造“刚成功的运行”。改为命令夹具。
- **`T/platform/integration/guard/ExternalConnectionIsolationTest.java`**
  - `:32`、`:39`、`:49-75`、`:88-104`
  - `:136-145` 要求 `LegacyOrgNames` 和 `JdbcLegacyInbox` 各一个
  - 随 legacy 删除改写。seeyon 部分（`:29`、`:58-66`、`:71-74`）随迁入决定。
- **`T/LegacyShapeTest.java:29-35`** 允许 seeyon/ 与 legacy/：legacy 去掉，seeyon 证。
- **`T/DataArchitectureTest.java`**、**`T/DdlNamingTest.java`**：本身留，改登记数据（§6）。
- **CS-2.29 G-C5**：`T/acceptance/ScalarIdContractTest.java:21、91、104`、`T/platform/eventing/infrastructure/ScalarIdModuleTest.java:47-89`、`T/acceptance/platform/integration/EventPayloadScalarIdsIT.java:49、162-193`。测试留，随事件目录变化。
- **`T/MustAuditActionsTest`**、**`EventCatalogueTest`**、**`EventCatalogueCoverageTest`**、**`SystemActorCatalogueTest`**：留，改数据。
- 其他守护：
  - `T/StateWriteGuardTest.java:65-72` 用 `SyncRunStatus` 作语料：换语料。
  - `T/AuditDetailGuardTest.java:84-85`：删该用例。
  - `T/TypedIdComparisonGuardTest.java:38`：删 `ShortNameAdapter` 例外。
  - `T/OpenApiExportTest.java:102、134`：随删。
  - `T/organization/workforce/guard/WorkforceConfigurationTest.java:33-52`：随删。
  - `T/platform/integration/guard/CredentialColumnGuardTest.java:180`：随删。
  - `T/platform/integration/guard/SeeyonSourceScanTest.java:204`：删 legacy 视图断言，A8 部分证。
  - `T/platform/integration/guard/SourceReferenceAllocationGuardTest.java:63`：证。

### 5.2 验收与集成测试

- **改为命令**，安全项，要先由测试代理重写：
  - `T/platform/identity/SeparationRevokesRolesIntegrationTest`（`:14、56、144`）
  - `SessionLifecycleIntegrationTest`（`:16、205`）
  - `BrowserIdentifierIntegrationTest`（`:237-238`）
  - `T/acceptance/platform/identity/AccountEntriesConcurrencyIT`（`:16、202`）
  - `T/acceptance/organization/workforce/HeldSeparationReleaseIT`（`:46、55、114`）
  - `T/platform/identity/AdministratorSeparationReviewTest`、`application/AdministratorSeparationReviewAccountsTest`
  - `T/scenario/WorkforceHttpIntegrationTest`（`:28、32、191`）
  - `T/platform/identity/OrgDataAdminRoleIntegrationTest:31`
- **列改名**：
  - `T/acceptance/platform/identity/authorization_history/AuthorizationHistoryRows.java:47`
  - `AuthorizationHistorySchemaIT.java:103`
  - `GrantHistoryAppendOnlyIT.java:173`
- **公用夹具**，改动波及面大：
  - `T/acceptance/…/AccountsAcceptanceSupport.java:311`（`insert into sync_runs`）
  - `T/organization/workforce/application/WorkforceIntegrationSupport.java:85`
  - `T/organization/workforce/domain/Fixtures.java:120`
- **随删**，被测对象删除：
  - `SyncRunStateMachineTest`、`OrgReconciliationTest`、`SyncedFieldsContractTest`、`OrgSnapshotSyncIntegrationTest`、`OrgReconciliationIntegrationTest`、`SnapshotFreshnessTest`、`SyncPurgeJobIT`、`SyncPurgeKeepPeriodsTest`
  - `scenario/SyncLockoutGuardIntegrationTest`
  - legacy/ 目录下：`JdbcLegacyInboxTest`、`LegacyConfigurationTest`、`LegacyInboxAccessIT`、`LegacyOrgNamesOnPostgresTest`、`LegacyReadRegisterTest` 等
  - `T/acceptance/platform/integration/LegacyAdapterPlacementTest:29`、`LegacyReferencesIT`、`StalledExternalSourceIT`、`A8SourceFlagsIT`（R-1b-0b 不再建设）
  - `SsoLoginIntegrationTest`、`SeeyonTicketVerifierTest`
  - `HttpContractIT:176`、`IdempotencyScopeTest:38-39`
  - `ManagementEndpointIT:39、84、93`（`forcitis_organization_workforce_sync_lag_seconds`）
  - 契约类：`P5Names:71、74`、`ApprovalIdContractTest:38`、`EmployeeIdContractTest:51`、`UserIdContractTest:34`、`SingleColumnPrimaryKeysIT:20`、`OwnIdColumnsIT:66、215-219`、`UserIdColumnsIT:50、120`
- **证**（票据）：`SignedTicketLoginIntegrationTest`、`SignedTicketSubjectIT:76`、`OneTimeCredentialConcurrencyIntegrationTest`、`IdentityIntegrationTest:215-216`。
- **证**（补建与企业微信首登混在一起）：`FirstWecomLoginIntegrationTest`、`LoginMissTransactionIntegrationTest`、`Nits1EmployeeExistenceIntegrationTest`、`OrgDirectoryIntegrationTest:45、76`、`EmploymentVerdictIntegrationTest:22、26`。
- **证**（迁入与来源 ID 匹配）：`OrgSourceWorldSupport`、`NegativeSourceIdIT`、`SourceIdMatchingIT`，以及 `seeyon/SeeyonViews*`、`SourceIdTranslationTest`、`SeeyonConfigurationTest`。
- **只换夹具**：`T/platform/audit/AuditDomainEventsIT:16、18`、`T/kernel/EventSequenceContinuityTest:16、21`、`T/fixture/doubles/AggregateWriteGuard:86-87` 借 `SyncRun` 当样本聚合，换成别的聚合即可。
- **测试替身**：`T/platform/approval/FakeOrganisation` 和 main 的 `M/platform/approval/infrastructure/replay/ExportedSnapshot.java:24、43-50` 实现了 `OrgDirectory`（含 `employmentOf`、`findEmployeeByLogin`），端口一变它们必须同批改。

---

## 6. 运维与登记

- **`A/data-access-registry.yaml`**
  - `:69、79、89` 的 `last_sync_run_id`：删或改。
  - `:104、111、118` 的 `sync_runs`、`sync_diffs`、`sync_run_held_separations`：删或改。
  - `:307、320` 的 `sso_ticket_uses`、`ticket_issuers`：证。
  - `legacy_numbers`：留。
- **`A/external-read-registry.yaml`**
  - `:22-55` A8 `v_org_*` 视图，用途 `RUNTIME_SYNC`：降为迁入或对账用途，证。
  - `:57` 起 `dbo.v_psa_one_legacy_todo`（`RUNTIME_TODO`）：删。
  - `:64` 起 `dbo.v_one_legacy_org_short_name`：删。
- **`A/data-classification.txt:13-14`**：票据两列，证。
- **`A/ddl-naming-baseline.txt`**
  - `:7-8、33-35、59-61` 的 sync：删。
  - `:18-19、49-50` 的票据：证。
- **其他登记文件**：
  - `A/system-actors.txt`：`:8-12` 的 `login-registration`、`short.name.refresh`、`snapshot.reconcile`、`snapshot.sync`、`sync.purge` 删；`:28` 的 `platform.identity.separation` 留，改描述。
  - `A/audit-text-writers.txt:12、30`：删。
  - `A/multi-aggregate-exceptions.txt:6`：删。
  - `A/primary-key-exemptions.txt:3`：删。
  - `A/repository-id-exceptions.txt:3`：删。
  - `A/source-type-exceptions.txt:5、9、12`：删。
  - `A/typed-text-key-exceptions.txt:5`：删。
  - `A/unversioned-table-writes.txt:6-7、17、27`：删；`:9-10` 证。
  - `A/versioned-table-unconditional-writes.txt:4、10`：删；`:3、9` 证。
  - `A/public-endpoints.txt:4、8-9`：随票据处理。
  - `A/legacy-shape-exceptions.txt:1-4`：改注释。
  - `A/module-dependencies.txt:5`（`kernel.SeparationReview`）：留，改调用方；`:7`（`kernel.ExternalSubjectEmployees`）：证。
  - `A/ticket-issuers-allowlist.txt`：证。
- **`A/test-images.txt:4`**（`mssql/server:2022-latest`）：证。迁入或取证的 SQL Server 测试若保留，这行也要留。
- **`D/audit/MUST_AUDIT_ACTIONS.yaml`**
  - `:44-50` 的放行类（`OrgSnapshotChanged`、`SyncRun*`、`organization.workforce.sync-run.released`）：删，补离职放行动作（命令）。
  - `:21` `AccountSeparated`：留。
  - `:53` `EmployeeRegistered`：由入职事件取代。
- **runbooks**：
  - `D/phase1a/infra/runbooks/files/alert-rules.yaml:40-46`（`OneWorkforceSyncLagging`、`OneWorkforceSyncHeld`）：删。
  - `D/phase1a/infra/runbooks/C-7-alerts.md:13、122-123`：删。
  - `D/phase1a/infra/INFRA_RUNBOOK.md`：`:68、95、98、104-106、126-128、230-252（环境变量）、294、344、347、360` 改或删。
  - grep 核实：没有 `docs/ops` 或 `runbooks` 顶层目录。
- **流程文档**：`D/process/1a/组织与人员同步.md`（374 行）没有标退役。`合并待办与通知.md`、`登录与会话.md` 需局部改。
- **台账滞后**：`D/tasks/TASKS.md:89-90` 仍记 IMP-1017-PAR-01/02 为进行中或待派，实际 PR #476 已合并。`D/phase1a/cards/ADR-0034-IMPL.md:3` 仍写“待派”。

---

## 7. 不能直接删的依赖

- **`OrgDirectory` 的调用方**（main，grep 核实）：
  - cost：`cost/timesheet/application/SubmitTimesheet.java:82、100`（在职判定、所在部门）。
  - approval：`GetTaskDetail`、`ListHandledTasks`、`ReassignTask`、`TaskDeadline`、`TransferTask`、`domain/AssigneeResolver`、`flowable/NodeAssignment`、`notification/TaskNotifier`、`replay/ExportedSnapshot`（实现方）。
  - identity：`AccountProvisioning`、`AdministratorSeparationReview`、`DisableEmergencyAdminOnSeparation`、`GetCurrentUserProfile`、`HandleEmployeeRegistered`、`HandleOrgSnapshotChange`、`LoginAsEmergencyAdmin`、`LoginWithSignedTicket`、`LoginWithSso`、`OrgUnitScopes`、`RoleHolders`、`SeparatedPeople`、`SessionPrincipals`、`WecomIdentification`。
  - 结论：`OrgDirectory` 必须**留**，只改实现（本地人事记录）并去掉 `employmentOf` 的快照语义和 `findEmployeeByLogin` 的补建分支。除 `findEmployeeByLogin`、`employmentOf` 外，其余方法已经只读本地表。
- **“在职快照”**：业务上的在职判定全部来自 `organization_workforce.employees.employment_status`。这张表目前只由同步与登录补建写入，所以人事命令必须**先于**删同步上线，否则投产前没有任何途径建人或离职。
- **identity 的在职核对现状**：
  - `AccountProvisioning.existingOrOpened`（`:55-57`）：已有账号就返回，否则 `findEmployee`，在职则开账号。
  - `accountForOperations`（`:50-53`）：给 `GrantRoleByOperations.java:58` 用。
  - `existingOrOpened` 另被 `RegisterEmergencyAdmin.java:62` 使用。
  - 两者都不读 A8，也不读同步表，只靠本地 `employees` 行，所以可以**留**。
  - 但 `ensureAccount` 的调用点有三个：`HandleOrgSnapshotChange:78`（同步）、`HandleEmployeeRegistered:25`（登录补建）、`LoginAdmission:59`（登录时）。前两个要换成入职命令事件，第三个的去留待定。
- **`SeparationReview`**：被 workforce 同步（`ApplySourceSnapshot:50`、`ReleaseSyncRunHolds:35`）调用、由 identity 实现。离职命令要接着调用它，端口只能改语义注释和调用方，不能随同步一起删。
- **`sync_run_held_separations`**：有 FK `ON DELETE CASCADE` 挂在 `sync_runs` 上（:471）。“待放行离职记录保留”要求先给它建新的载体（挂离职命令），再删 `sync_runs`。
- **审计的 `AuditSubjectKind.SYNC_RUN`**：已写入的审计条目可能引用它，枚举能否删未核实（dev 库里的历史数据可能已有）。

---

## 8. ADR-0055 与现状、其他 ADR 的不一致和缺口（原样列出，不裁定）

1. 基线已有 `revocation_sync_run_id`（identity 基线 :447、:469、:501、:518），尚未改名（ADR-0055:70）。
2. ADR-0034-IMPL（`D/phase1a/cards/ADR-0034-IMPL.md`）仍以 `OrgSnapshotChanged` 和 `HandleOrgSnapshotChange` 为离职、复职触发（`:36、40、102、476、479`）。DDL 和验收 G4-01 用的是旧列名。卡里没有 ADR-0055 字样。
3. ADR-0034 决定第 4 项第 4 条（`ADR-0034:272`）仍写“精度按日，依据是同步观察日（OBSERVED）”。ADR-0055 的取代清单不含这一条。
4. F-1 的去重键与三列都按同步运行设计：`F-1.md:134、559`；`trigger_sync_run_id`（:462、:520）、`closure_sync_run_id`（:485）、`expiry_held_sync_run_id`（:478、:145）。监听器挂在 `OrgSnapshotChanged` 上（:163）。ADR-0055:77 只改了首次触发的去重键，没提这三列。
5. F-1 来源②（部门撤销或合并，`ORG_UNIT_INACTIVE`）依赖同步。ADR-0055 列出的人事命令只有入职、转正、调岗、停薪留职、离职（另有复职，见 :71），**没有组织单元、岗位的维护命令**。
6. WF-REINSTATE 卡只在分支 `claude/one-1a-next-5-docs4`（`34cd01ca`），没进 main。它在同步事务里按 `REHIRED` 发 `EmployeeReinstated`。ADR-0055:71 改成复职命令，没说这张卡撤销还是改写。ADR-0034-IMPL 的 A-13 和 PR-3 依赖它（`COORDINATOR_LEDGER.md:29`）。
7. DATA-REPAIR-workforce（进行中，one-1a-next-5）：WF-01、02、07、08、13、14 和 SD-1 仍按“拆分 a 迁 `sync_runs` 到集成层”设计（`:20、164、335-336`）。W2 没有独立卡，负责会话不明确：ADR-0055:62 写交 one-data，画像 `sessions/one-1a-next-5.md:18` 写“不碰 W2”。`repair-cards/README.md:35` 和 `DATA-REPAIR-workforce.md:27` 留着“以同步观察日为生效依据”的旧原话。
8. ADR-0055 没有定义人事命令的表结构、聚合、命令 ID 的生成方与类型，也没有定义事件契约。“人事变更命令 ID”（:77）、“离职命令 ID”（:70）、复职命令是否同一个 ID 空间，没有说明。
9. 待放行离职的新载体没有说明。现表挂在 `sync_runs` 上且级联删除（workforce 基线 :471）。ADR-0055:68 只写“载体改为本地离职命令”。
10. “放行后为 0 名只能走双人在场、写审计的运维命令”（ADR-0055:69）：main 里**没有**这条运维命令（grep 核实，只有 `ReleaseSyncRunHolds.java:39` 的报错文字提到它）。
11. “停薪留职”在 `EmploymentStatus`（只有 EMPLOYED / SEPARATED）和 `employees.employment_status` CHECK（基线 :76）里没有对应状态。停薪留职是否关闭授予、吊销会话，ADR 未写。
12. `employees.login_name` 的语义是“为空即离职（RULE-363）”（基线 :95；`EmploymentStatus.java:3` 注释）。撤 A8 后这个列的去向（迁入追溯，改放 `external_identities`？）没有说明。`IntegrationOperationsCommands` 的 `SEEYON_LOGIN_NAME` 分支和 kernel `ExternalSubjectEmployees`（注释写“临时端口”）也一样。
13. `org_units.short_name` 的来源：ADR-0055:88 要删 `LegacyOrgNames`，但没说简称以后由谁维护（人事命令？一次迁入？）。前端 `org-units-page.tsx:9` 在用这个字段。
14. 已有的 `last_sync_run_id`（`org_units`、`employees`）：ADR-0039:48 原定改引用变更集，ADR-0055 撤同步后没说它们变成什么。
15. `LoginAdmission.java:59` 登录时补开账号（人员已在本地、账号缺失时）。ADR-0055:56 要求账号开通由人事命令驱动，但没说登录时补开账号还留不留。
16. 应急登录：ADR-0055:66 要求本地记录缺失或无效即拒绝，现行 `LoginAsEmergencyAdmin.java:109` 是告警后放行。ADR-0028:29 的“应急会话放行人须快照中明确在职”在本地化后怎么表述，没有单独写。
17. ADR-0055:98 引用的 ADR-0037 条号对不上：ADR-0037 决定 5 是“账号”（:97），启动校验在决定 4（:80），读取列登记在 6.1（:129）。
18. ADR-0055:88 要求同步、`HandleOrgSnapshotChange` 等“删去或仅改为迁入适配器”，但没说哪些改成适配器、由哪张卡承担。ADR-0036 的状态行没有标注被 ADR-0055:82 合并。
19. 被点名但没有卡的工作（`phase1a/cards/` 下找不到）：W0“平台与人事”的人事命令、身份生命周期研究、T-13 外部调用方取证、迁入适配器。拆分 a、b 和 R-1b-0b 只是不再建设，但已提交的 `A8SourceFlagsIT` 还在 main 上。
20. `source_references`“投产后全表只读”（TRN-06）没有落地方式（触发器、授权还是应用层），现有触发器（integration 基线 :189-222）只禁止改两侧 ID 和删除。
21. `AuditSubjectKind.SYNC_RUN` 和已有审计条目的处置没有说明。
22. 文档滞后：`docs/business/processes/org-and-personnel-sync.md` 没标退役；`TASKS.md:89-90` 和 `ADR-0034-IMPL.md:3` 的状态与 PR #476 已合并不符。

---

## 9. 建议切分方案

总原则：先建人事命令，再改 identity 订阅，最后删同步。否则投产前没有途径建人或离职。凡涉及离职、授权、登录的 PR 都是安全项：不交 Codex，opus 做首轮审和终审，验收测试由测试代理先写。

| 序 | PR | 范围 | 依赖 | 表结构（one-data 签发） | 安全项 | Codex | 先写的验收测试 |
|---|---|---|---|---|---|---|---|
| 0 | 文档（不开 PR） | one-architecture 裁 §8 缺口（第 8、9、10、11、5、15 条优先）；新人事命令卡（W0）；改写 ADR-0034-IMPL、F-1、WF-REINSTATE（撤或改为复职命令事件）、DATA-REPAIR-workforce、W2 设计（交 one-data）；`docs/business/processes/org-and-personnel-sync.md` 标退役；TASKS 状态补正 | — | 设计签发 | — | — | — |
| R | 列改名 | identity 基线 `revocation_sync_run_id` 改为 `revocation_separation_command_id`，连同 CHECK、触发器列清单、注释和 3 个授权历史测试；基线开放期直接改 | 无（可立即做；必须早于 ADR-0034-IMPL PR-3） | 是 | 是（授权表） | 否（机械，但属授权，sonnet 做，opus 审） | 改现有 3 个 IT |
| F | 删老待办 | §1.5 legacy inbox 全套、`ListInbox` 合并、`InboxResponse` 两个字段、OpenAPI 和前端 web/h5/core、external-read 的 RUNTIME_TODO、配置、runbook、相关测试 | 无 | 否 | 否 | 可 | 收件箱只含本系统待办的负向测试 |
| G | 删部门简称刷新 | `RefreshShortNames`、Job、`ShortNameAdapter`、`OrgShortNames`、`LegacyOrgNames`/`DisabledOrgNames`/`LegacyConfiguration`、配置、登记、`ExternalConnectionIsolationTest` 的 legacy 段 | 先裁 §8-13 简称的维护方 | 列是否保留看裁定 | 否 | 可 | — |
| A | 人事命令契约先行（CS-1.10） | workforce api 与事件：入职、转正、调岗、停薪留职、离职、复职命令事件（带命令 ID、人事业务生效日）；`OrgDirectory.employmentOf` 改为本地语义（契约层面）；事件目录、MUST_AUDIT 登记 | 0 | 否（只有契约） | 是 | 否 | 事件契约测试 |
| B | 人事命令实现 + 待放行离职 | workforce 命令服务、命令表、版本写入（按业务生效日，对齐 W2）、待放行离职新表（挂离职命令）、单条放行接口与权限 `separation-hold:approve`、0 名超管时的双人运维命令、`SeparationReview` 改由离职命令调用；前端待放行离职页 | A、W2 设计签发 | 是 | 是 | 否 | 命令重放幂等、留任、超管下限 2/1/0、单条放行、不重跑、复职只授 employee |
| C | identity 改订阅（= ADR-0034-IMPL PR-3 重定范围） | 离职事件关闭全部授予（`SEPARATION` 加命令 ID，逐条审计）、吊销会话、清缓存、停用应急账号；复职只新开 employee；入职开账号；删 `HandleOrgSnapshotChange` 的同步订阅、`DisableEmergencyAdminOnSeparation` 旧订阅、`SeparatedPeople`、`HandleEmployeeRegistered` | B、R、ADR-0034-IMPL PR-2 | 否（R 已改列） | 是 | 否 | `SeparationRevokesRoles`、`SessionLifecycle`、`AccountEntriesConcurrency`、`BrowserIdentifier` 改写为命令；复职只授基础角色 |
| D1 | 在职判定本地化 | `employmentOf` 去掉 `SNAPSHOT_UNAVAILABLE`、删 `SnapshotFreshness`；`LoginAsEmergencyAdmin` 缺失即拒绝并告警；`ExportedSnapshot`、`FakeOrganisation` 同批改；`EmergencyLoginIntegrationTest:209` 删断言（PR 中单列理由） | C | 否 | 是 | 否 | `EmergencyLoginEmploymentIT` 改写 |
| D2 | 删同步 | §1.1 其余全部、`sync_runs`/`sync_diffs`/旧 `sync_run_held_separations`、`last_sync_run_id`、`OrgSnapshotChanged` 与 `SyncRun*` 事件、`SyncRunController`、`/sync-runs:reconcile`、权限 `sync-run:*`、导航项、错误码、配置、前端同步页、§6 登记、告警与 runbook、随删测试 | D1（identity 已不订阅、`SeparationReview` 已换调用方） | 是（删表、删列） | 否（安全语义已在 B、C、D1 移走） | 可（sonnet 复核，opus 终审守护删改） | 负向：注入同步事件或旧连接须失败（ADR-0055:127） |
| E | 删登录补建 | `RegisterEmployeeOnLogin*`、`LoginMissRegistration`、`PersistRegisteredEmployee`、`RegisteredEmployee`、`EmployeeRegistered`、`findEmployeeByLogin` 的补建分支；`LoginAdmission:59` 按裁定处理 | C（入职已能开账号） | 否 | 是（登录） | 否 | 企业微信首登：本地有人才放行，未知即拒绝 |
| H | 致远 SSO 与签名票据 | 先删致远门户 SSO（`LoginWithSso`、`SeeyonTicketVerifier`、`sso-login`、`legacyLoginUrl`、前端 sso 分支）；`ticket_issuers`、`sso_ticket_uses`、签名票据等身份生命周期研究与 T-13 完成后再处理 | 研究与 T-13；致远 SSO 能否先删待裁 | 后半是 | 是 | 否 | 负向：旧票据须失败；企业微信不受影响 |
| I | seeyon 改迁入适配器 | `SeeyonOrgSource` 等转为一次性迁入与核对（ADR-0036）、`SourceIdTranslation` 运行时分配撤、external-read 改用途、`OrgReconciliation` 改迁入核对、`test-images` 的 mssql 去留 | ADR-0036 迁入卡 | 视迁入设计 | 否 | 部分可 | G-MG 迁入守护 |

### 同批要求（不同批会编译失败或出现安全空窗）

- **C**：删 identity 的 `OrgSnapshotChanged` 订阅和接入新事件必须同批，否则离职收回会出现空窗或重复收回。
- **D1**：`SnapshotFreshness`、`EmploymentVerdict.SNAPSHOT_UNAVAILABLE`、`OrgDirectoryService.employmentOf`、`LoginAsEmergencyAdmin`、`ExportedSnapshot`、`FakeOrganisation` 必须同批改。
- **D2**：`SyncRun*` 领域与持久化、`OrgSnapshotChanged`、`SyncRunId`、`HoldKind`、`SyncRunController`、OpenAPI、前端生成物和同步页、审计与事件目录、`StateWriteGuardTest`、`AuditDomainEventsIT`、`EventSequenceContinuityTest`、`AggregateWriteGuard` 的样本必须同批，否则编译或目录守护失败。
- **F**：`LegacyInbox` api、`ListInbox`、`InboxResponse`、`openapi.json`、前端生成物和 web/h5/core 必须同批。
- **E**：`EmployeeRegistered` 与 `HandleEmployeeRegistered` 必须同批删，前提是 C 已接入入职事件。

### 与其他工作的衔接

- **ADR-0034-IMPL**：PR-1 已合并（#476）；PR-2（角色版本化）不受 ADR-0055 影响，可照常推进。PR-3 改为上表 C，前置 R 与 B；卡中的 A-10“信封 aggregateId 即同步运行 ID”、A-13 和 G4-01 要重写。
- **WF-REINSTATE（one-1a-next-5）**：同步内的 `REHIRED` 已不成立，建议撤销或改为 B 中复职命令发出的 `EmployeeReinstated`，由 one-1a-next-5 与 one-1a-par 对齐契约。
- **数据修复批 W2**：B 写版本必须按人事业务生效日，所以 W2 设计签发是 B 的前置。D2 删表后，DATA-REPAIR-workforce 的 WF-01、02、07、08、13、14 和 SD-1 失去对象，须由 one-data 重判。
- **F-1**：在 C 之后，去重键与三列改为命令 ID；来源②要等组织单元命令（§8-5）有了结论。
