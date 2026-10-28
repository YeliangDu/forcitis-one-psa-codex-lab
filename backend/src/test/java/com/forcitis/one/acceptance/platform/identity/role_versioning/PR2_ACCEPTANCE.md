# ADR-0034-IMPL PR-2 验收测试：角色版本化与系统角色声明

依据只有：卡 `docs/modernization/phase1a/cards/ADR-0034-IMPL.md`（第 4 节测试清单、7.2、第 12 节第 3 条）、`docs/modernization/adr/src/ADR-0034.md`（现行；ADR-0055、0068 取代的部分与角色版本化无关）、identity 基线 `V20261014130000__platform_identity_baseline.sql` 中 roles 的种子（main `f62af6ae8`，:523-586）。写法、夹具与容器用法照 PR-1 的 `authorization_history/` 与 `SingleOperatorModeEnvironment`。测试作者：独立测试代理（`Acceptance-Test-Author: test-engineer`）。

## 运行

```
cd backend
./mvnw -o test-compile
./mvnw -o surefire:test -Dtest='MigrationRoleDataScanTest,PermissionCatalogueTest'
./mvnw -o failsafe:integration-test -Dit.test='com/forcitis/one/acceptance/platform/identity/role_versioning/*IT'
```

需要本机 Docker（白名单 `postgres` 镜像，经 `TestPostgres.container()`）。`SystemRoleDeclarationIT` 另起子 JVM 启动真实 main（`SystemRoleDeclarationLauncher`），共约 9 次启动，耗时最长。

## 测试清单

| 编号 | 测试类 # 方法 | 规格出处 |
|---|---|---|
| 0034-G1-06 | `RoleIdentityImmutableIT#identityColumnsCannotChange` | 卡 §4 G1-06；7.2 触发器 `refuse_role_identity_change`；ADR 第 1 项第 1 条、守护第 1 条 |
| 0034-G1-06 | `RoleIdentityImmutableIT#versionAndEventCountChange` | 卡 §4 G1-06「改 version、event_count 成功」；ADR 第 1 项第 1 条 |
| 0034-G1-06 / 7.2 | `RoleIdentityImmutableIT#rolesKeepsOnlyItsIdentity` | 卡 7.2（移出三列、删 `uq_roles_name`、保留 `uq_roles_code` 与两条 CHECK、触发器） |
| 0034-G1-06 / 7.2 | `RoleIdentityImmutableIT#rolePermissionsRetiresAccountRolesStays` | 卡 7.2「删除整张 role_permissions」「不得出现 account_roles 的变化」 |
| 0034-G2-03（角色版本部分） | `RoleVersionIntervalPropertyIT#roleVersionsFollowTheReplay`（3 个固定种子） | 卡 §4 G2-03；ADR 守护第 2 条性质测试、第 1 项第 4–6 条、第 4 项第 3 条 |
| 0034-G5-03 | `RoleVersionWritesIT#aVersionNeverStartsBeforeTheCurrentOneWhenTheClockGoesBack` | 卡 §4 G5-03；ADR 第 1 项第 6 条、守护第 5 条 |
| 0034-G7-03 | `RoleVersionWritesIT#everyChangeAddsExactlyOneVersionAndNoChangeAddsNone` | 卡 §4 G7-03；ADR 第 1 项第 2、4 条、守护第 7 条 |
| 0034-G7-03 | `RoleVersionWritesIT#deletingAHeldRoleIsRefused` | 卡 §4 G7-03（409 `PLATFORM_IDENTITY_ROLE_IN_USE`）；ADR 第 1 项第 5 条 |
| 0034-G7-03 | `RoleVersionWritesIT#deletingClosesTheLastVersionAndTheCodeIsNeverReused` | 卡 §4 G7-03；ADR 第 1 项第 5 条；0034-Q3 / RULE-345 修订 |
| 0034-G8-01 | `SystemRoleDeclarationIT#anEmptyDatabaseGetsTheSixSystemRoles` | 卡 §4 G8-01、7.2（六个固定 role_id、修订号 1）；ADR 第 1 项第 7 条 |
| 0034-G8-01 | `SystemRoleDeclarationIT#theReconciliationIsDoneBeforeAnythingElseRuns` | 同上「对账先于 ApplicationRunner、事件监听与重投、定时任务、HTTP 就绪」 |
| 0034-G8-01 | `SystemRoleDeclarationIT#aRestartAddsNoVersion` | 同上「重启不新增版本」 |
| 0034-G8-01 | `SystemRoleDeclarationIT#aSmallerRevisionIsAlertedAndNotWritten` | 同上「修订号更小告警不写」 |
| 0034-G8-01 | `SystemRoleDeclarationIT#anEqualRevisionWithOtherContentStopsTheStart` | 同上「相等而内容不同启动失败」「对账失败时进程不启动」 |
| 0034-G8-01 | `SystemRoleDeclarationIT#twoInstancesStartingTogetherEndAlike` | 同上「两实例同时启动结果相同」 |
| 0034-G8-02 | `SystemRoleDeclarationIT#theDeclarationCarriesTheSeedCodeForCode` | 卡 §4 G8-02、7.2「种子内容原样搬进系统角色声明」；ADR 迁移方案第 3 条 |
| 0034-G8-03 | `MigrationRoleDataScanTest#noMigrationWritesRoleData`、`#injectedWritesAreCaught` | 卡 §4 G8-03、第 6 节；ADR 第 1 项第 7 条、守护第 8 条 |
| 0034-G8-03 | `MigrationsWriteNoRoleDataIT#migrationsLeaveNoRoleRows`、`#navigationSeedStays` | 同上；卡 7.2（navigation_items 种子保留） |
| 0034-G8-04 | `PermissionCatalogueTest#noCodeLeavesTheCatalogue`、`#theCatalogueMarksDeprecatedCodes` | 卡 §4 G8-04、3.2；ADR 第 1 项第 3 条、守护第 8 条 |
| 0034-G8-04 | `PermissionCatalogueIT#currentVersionsHoldOnlyLiveCatalogueCodes` | 同上「测试库全部角色当前版本的码都存在且未停用」 |
| 0034-G8-04 | `PermissionCatalogueIT#anUnknownCodeIsIgnoredAndAlertedTheOthersStillCount` | 同上「运行时读到未知码只忽略该码并告警，同一角色其他码照常生效、不抛异常」；卡第 6 节 |

辅助类：`MigratedRoleRows`（只跑迁移的库，事务不提交）、`RoleVersioningSupport`（经 HTTP 管角色、读 `role_versions`）、`SystemRoleDeclarationEnvironment` / `SystemRoleDeclarationLauncher`（子 JVM 启动与两个只读探针）。

## 假定契约（实现须满足，或经裁定改测试）

1. **权限目录停用标记**：`com.forcitis.one.platform.identity.api.Permission` 上有 `public boolean deprecated()`（实例方法），停用的码返回 true。卡 3.2 只写「加『已停用』标记」，形状未定；测试经反射读取，缺失时在运行时失败、不让本目录编译失败。若实现取别的形状（如注解），请走裁定改 `PermissionCatalogueTest.deprecatedMarker()` 一处。
2. **告警的形状**：沿用现有约定——ERROR 级、消息以 `ALERT ` 开头（`AcceptanceSupport.alertsSince`）。未知权限码的 ALERT 含该码；声明修订号更小的 ALERT 含角色 ID（卡数据设计 C4「对账 ALERT 日志：角色 ID、声明修订号与库中修订号、未知或停用权限码」）。
3. **启动对账在单例初始化阶段完成**：测试以 `Integer.MIN_VALUE` 相位的 `SmartLifecycle` 与最高优先级的 `ApplicationRunner` 作探针，两处都须已见六个当前 SEED 版本。
4. **删除后编码复用的应答**：沿用现有冲突码 409 `PLATFORM_IDENTITY_ROLE_DUPLICATE_CODE`（OpenAPI 不变）。
5. **MANUAL 版本**：`recorded_by` = 操作人用户 ID；`change_reason` 非空（A-5 已裁：应用写固定说明）；被新版本接替的版本关闭列为空；删除时最后版本的 `closed_by` = 删除人，`closure_reason`、`closure_audit_entry_id` 非空。
6. **删除的关闭时刻**：取 `greatest(:now, 当前版本开始时刻)`（ADR 第 1 项第 6 条「当前版本的关闭时刻取同一值」）。

## 在 main（f62af6ae8 + PR-1）上的结果

2026-10-28 在本分支（main `f62af6ae8`，含 PR-1）实测：全部可编译；26 条（含 3 个种子）中 22 条红、4 条绿。

- 红，原因均为行为未实现：`RoleIdentityImmutableIT` 3 条（改 code 未被拒、roles 仍有三列、role_permissions 仍在）；`MigrationsWriteNoRoleDataIT#migrationsLeaveNoRoleRows`（迁移写了 6 行 roles）；`MigrationRoleDataScanTest#noMigrationWritesRoleData`（基线 3 处 `INSERT INTO platform_identity.roles`）；`RoleVersionWritesIT` 4 条、`RoleVersionIntervalPropertyIT` 3 个种子（HTTP 全部成功，但 `role_versions` 无行；删除后 roles 行被物理删除）；`SystemRoleDeclarationIT` 7 条（子进程正常启动，探针读到 0 个 SEED 版本、无版本行）；`PermissionCatalogueIT` 2 条（无当前版本）；`PermissionCatalogueTest#theCatalogueMarksDeprecatedCodes`（无停用标记）。
- 绿（在 main 上本应成立，保持绿即可）：`RoleIdentityImmutableIT#versionAndEventCountChange`、`MigrationsWriteNoRoleDataIT#navigationSeedStays`、`MigrationRoleDataScanTest#injectedWritesAreCaught`（扫描自检）、`PermissionCatalogueTest#noCodeLeavesTheCatalogue`。

说明：`twoInstancesStartingTogetherEndAlike` 先单独启动一次再按卡 3.3 的夹具清库法（临时停用 `role_versions` 的用户触发器）清掉角色数据，然后两实例并发启动——因为流程引擎首次启动建自身表时并发会撞 `pg_type_typname_nsp_index`，与本卡无关。

## 未覆盖项

- **G8-01「修订号提高追加 SEED 版本」**：须换一份修订号更大的声明，卡与 ADR 没有定义测试换声明的缝；只靠改库无法构造「库中修订号 < 1」。待调度定接缝后补。
- **G8-04「运行时读到已停用码」**：今天目录没有停用码（卡 3.2），无法黑盒构造；未知码一支已覆盖。
- **G5-03「审计 detail 记实际钟点」**（ADR 第 1 项第 6 条）：detail 的格式未定，未断言。
- **G2-03 授予部分**、**G7-03 持有判定的授予侧**：属 PR-3。
- **ADR 守护第 8 条「首次部署后声明给有持有人的系统角色加双人逐人级码启动失败」**：A-8 已裁移 F-1。
- **G9、G10**：守护文件，本目录不改，见下节。

## 需实现方改的守护（G9、G10，单独提交、PR 正文单列）

- G9 `SingleAggregateGuardTest`：该测试本身没有表到聚合的映射；映射在 `data-access-registry.yaml` 的 `aggregate:`（`role_versions → Role` 已在，PR-2 把 `writers: []` 改 `[platform.identity]`，删 `role_permissions` 条目）与 `aggregates.yaml`（卡 3.3 所指 `docs/modernization/architecture/ddd/aggregates.yaml` 在本检出中未找到，待核）。`multi-aggregate-exceptions.txt` 不变；启动对账逐角色提交。
- G10：`ddl-naming-baseline.txt` 删 :17 `roles.access_scope`、:46 `role_permissions…fkey`、:61 `uq_roles_name`（:39 `account_roles` 留 PR-3；:47 `roles_pkey`、:60 `uq_roles_code` 保留）；`unversioned-table-writes.txt` 删 :18（`role_permissions`），`role_versions` 写入按需登记理由；`RepositoryWriteGuardTest`、`TimeSourceGuardTest`（新 SQL 无数据库时钟、`:now` 为参数）、`PrimaryKeyTypeGuardTest`（`primary-key-exemptions.txt` 头注提及 `role_permissions`）、`MustAuditActionsTest`（`role-version.opened/closed`、Role 事件 `@NotAudited`，A-6）、`GlossaryTest`（提议术语）、`SystemActorCatalogueTest`（`system-actors.txt` 加启动对账的系统操作者）、`DataArchitectureTest`（registry writers）全绿。

## 待实现方与调度确认

1. 声明修订号提高一支的测试接缝（见未覆盖项第 1 条）。
2. 停用标记的形状（假定契约第 1 条）。
3. 两条 ALERT 是否须含角色 ID / 权限码（假定契约第 2 条，出处为卡数据设计 C4 允许写入表，非强制字面）。
4. 删除角色后 `GET /roles/{id}` 应答（卡与 ADR 未写；测试未断言）。
5. 删除角色关闭时刻在时钟回拨时的取值（假定契约第 6 条，ADR 只对「新版本」写了公式）。
6. `aggregates.yaml` 的实际路径（G9）。
