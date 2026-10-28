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

补缺（独立测试代理，2026-10-28 第二次提交）：

| 编号 | 测试类 # 方法 | 规格出处 |
|---|---|---|
| 0034-G8-01（修订号提高追加 SEED 版本） | `SystemRoleRaisedRevisionIT#aHigherRevisionAppendsASeedVersion` | 卡 §4 G8-01「修订号提高追加 `SEED` 版本」；ADR 第 1 项第 7 条第 3 步「声明更大 → 关闭当前版本、追加新版本，渠道 `SEED`，记修订号，理由写声明出处」；ADR 回滚方案（提高修订号追加新版本） |
| 0034-G8-04（已停用码） | `DeprecatedCodeIT#aDeprecatedCodeIsIgnoredAndAlertedTheOthersStillCount` | 卡 §4 G8-04「运行时读到未知或已停用码，只忽略该码并告警，同一角色其他码照常生效、不抛异常」；ADR 第 1 项第 3 条、守护第 8 条 |
| 0034-G5-03（审计 detail 的实际钟点） | `RoleVersionAuditDetailIT#theAuditDetailKeepsTheActualClockReadingAsAnIsoDateTime` | ADR 第 1 项第 6 条「实际钟点 `:now` 写进审计 detail」、守护第 5 条；卡 C4「`role-version.opened / closed` detail：……实际钟点」、G5-02「审计 detail 记实际钟点」 |

注：卡 §4 的 G5-03 行字面是「角色版本时钟回拨时开始时刻不早于当前版本」，已由 `RoleVersionWritesIT` 覆盖；「审计 detail 记实际钟点」出自 ADR 第 1 项第 6 条与卡 G5-02、C4，这里按调度要求把它归在 G5-03（角色版本的钟点）名下补测。

辅助类：`RaisedDeclarationLauncher`（换声明的子 JVM 启动器）、`MigratedRoleRows`（只跑迁移的库，事务不提交）、`RoleVersioningSupport`（经 HTTP 管角色、读 `role_versions`）、`SystemRoleDeclarationEnvironment` / `SystemRoleDeclarationLauncher`（子 JVM 启动与两个只读探针）。

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

- ~~G8-01 修订号提高~~、~~G8-04 已停用码~~、~~G5-03 审计 detail 钟点~~：已在补缺一节补测，选择与理由、仍待定的部分见「补缺的选择与待定」。
- **G2-03 授予部分**、**G7-03 持有判定的授予侧**：属 PR-3。
- **ADR 守护第 8 条「首次部署后声明给有持有人的系统角色加双人逐人级码启动失败」**：A-8 已裁移 F-1。
- **G9、G10**：守护文件，本目录不改，见下节。

## 补缺的选择与待定（G8-01 修订号提高、G8-04 已停用码、G5-03 审计 detail 钟点）

卡与 ADR 没写死的地方，各选一种写法，理由如下。

**G8-01 修订号提高**
- 换声明的接缝：卡与 ADR 没定义。选用实现已提供并写在 `SystemRoleReconciliation` 类注释里的做法：上下文里有 `SystemRoleDeclaration` bean 就取它、否则取 `SystemRoleDeclaration.CURRENT`。理由：不改 main、不新增系统属性；只靠改库构造不出「声明 > 库中」（见原未覆盖项）。耦合点只有这一处，在 `RaisedDeclarationLauncher`；换接缝时只改这个类。启动器仍是子 JVM 起真实 `OneApplication`，探针同 `SystemRoleDeclarationLauncher`。
- 换什么：员工角色改为修订 2，名称、权限码集合都与种子不同（去掉 `platform.file:file:create`，加 `platform.numbering:legacy-number:read`）；其余五个角色沿用代码声明、修订号 1。理由：内容逐项都不同，才能抓到「追加了旧内容」「只改了名称没改码」这类写错。
- 断言：员工恰有两个版本；版本 1 除关闭外原样；版本 2 当前、`SEED`、修订号 2、名称、状态、范围、权限码集合逐项等于声明、无记录人、无授权申请、理由非空、有审计条目且动作为 `platform.identity.role-version.opened`；版本 2 开始时刻 = 版本 1 关闭时刻且不早于版本 1 开始（ADR 第 1 项第 4–6 条，无缝）；被接替的版本关闭列为空（卡 7.1 列注释：`closure_audit_entry_id` 「为空表示……被新版本接替」，且 `ck_role_versions_closure` 三列同空）；其他五个角色的版本行与之前逐列相同；`roles` 仍六行；再用同一声明启动一次，不再新增版本。
- 不断言：版本 2 `change_reason` 的具体文字（C4 只说「声明出处（类名与修订号）」，文字未定）；审计 detail 的文字。

**G8-04 已停用码**
- 构造：目录今天没有停用码（卡 3.2），所以在一个用例内临时把一个码标成停用，用后还原。先按正常路径写角色（两个码此时都是现行码，这是写入时唯一允许的写法，ADR 第 1 项第 3 条），再把其中一个码标停用（模拟之后某个版本把它停用），持有人登录后读当前会话。
- 做法：用反射改 `Permission` 枚举常量背后的私有字段 `deprecated`，`deprecated()` 方法读到的就是它。理由：契约只给了公开方法 `deprecated()`，没有可注入的接缝；枚举常量改不了别的办法。代价：耦合字段名；实现改名时用例会以明确的信息失败（见类注释）。用例在 `finally` 里还原。
- 断言：200；留下的码仍在权限里，停用的码不在；有 ERROR 级、`ALERT ` 开头且含该码的日志；库中版本未被读取改写。

**G5-03 审计 detail 的钟点**
- 卡与 ADR 规定的：detail 记「实际钟点」`:now`（注入时钟的读数），不是 `greatest(:now, 开始时刻)` 之后的生效时刻；C4 表列明 `role-version.opened / closed` 的 detail 含实际钟点。**没有规定**：文字格式、键名、小数位、时区写法。
- 选择：不断言键名与位数；从 detail 里取出所有带偏移（`Z` 或 `±hh:mm`）的 ISO-8601 日期时间，按时刻比较，要求其中有一个等于注入时钟的读数。理由：全库时刻都是 `timestamptz`、值来自注入 `Clock`（卡第 605 行），ISO-8601 带偏移无歧义，也是 Java `Instant`/`OffsetDateTime` 的标准形式；再严（如要求 `Z`、固定位数）是替实现方定了卡与 ADR 没定的事。
- 场景：时钟回拨后修订（新版本开始时刻 t2 晚于时钟读数），检查开启审计；未回拨的版本，detail 的时刻等于时钟；回拨后删除，检查关闭审计。每处读数都与生效时刻不同，实现方若写成生效时刻会红。
- 待定：detail 里时刻的确切文字形式（`Z` 还是 `+00:00`、秒后几位、键名）。若调度要定死，请改卡 C4 一行，再收紧 `RoleVersionAuditDetailIT#instantsIn`。

**实跑**：在 `d586aa610`（PR-2 实现）上，`role_versioning` 包全部 25 条（含 3 条新增）绿。G8-01 新用例另在临时副本上改坏 `JdbcRoleRepository.saveDeclared` 三次，均红，已弃副本：① 修订号提高时不追加版本（红于「恰有两个版本」）；② 追加旧定义（红于 `role_name`）；③ 名称用新值、权限码用旧值（红于 `permission_codes`）。G5-03、G8-04 新用例未做改坏验证。

**未做的**
- 「声明修订号提高但内容与当前版本相同」：ADR 字面是「声明更大 → 关闭当前版本、追加新版本」，没有「内容相同则不写」的例外，也没有明说要写；两种实现都说得通，列待定，不写用例。
- 写入时拒绝已停用码（ADR 第 1 项第 3 条前半）：不在本次三项内。

## 需实现方改的守护（G9、G10，单独提交、PR 正文单列）

- G9 `SingleAggregateGuardTest`：该测试本身没有表到聚合的映射；映射在 `data-access-registry.yaml` 的 `aggregate:`（`role_versions → Role` 已在，PR-2 把 `writers: []` 改 `[platform.identity]`，删 `role_permissions` 条目）与 `aggregates.yaml`（卡 3.3 所指 `docs/modernization/architecture/ddd/aggregates.yaml` 在本检出中未找到，待核）。`multi-aggregate-exceptions.txt` 不变；启动对账逐角色提交。
- G10：`ddl-naming-baseline.txt` 删 :17 `roles.access_scope`、:46 `role_permissions…fkey`、:61 `uq_roles_name`（:39 `account_roles` 留 PR-3；:47 `roles_pkey`、:60 `uq_roles_code` 保留）；`unversioned-table-writes.txt` 删 :18（`role_permissions`），`role_versions` 写入按需登记理由；`RepositoryWriteGuardTest`、`TimeSourceGuardTest`（新 SQL 无数据库时钟、`:now` 为参数）、`PrimaryKeyTypeGuardTest`（`primary-key-exemptions.txt` 头注提及 `role_permissions`）、`MustAuditActionsTest`（`role-version.opened/closed`、Role 事件 `@NotAudited`，A-6）、`GlossaryTest`（提议术语）、`SystemActorCatalogueTest`（`system-actors.txt` 加启动对账的系统操作者）、`DataArchitectureTest`（registry writers）全绿。

## 待实现方与调度确认

1. ~~声明修订号提高一支的测试接缝~~：已按实现提供的 `SystemRoleDeclaration` bean 接缝补测；请确认这是正式接缝（卡与 ADR 未列），不会被去掉。
2. 停用标记的形状（假定契约第 1 条）；`DeprecatedCodeIT` 还依赖其背后的私有字段名 `deprecated`。
3. 两条 ALERT 是否须含角色 ID / 权限码（假定契约第 2 条，出处为卡数据设计 C4 允许写入表，非强制字面）。
4. 删除角色后 `GET /roles/{id}` 应答（卡与 ADR 未写；测试未断言）。
5. 删除角色关闭时刻在时钟回拨时的取值（假定契约第 6 条，ADR 只对「新版本」写了公式）。
6. `aggregates.yaml` 的实际路径（G9）。
