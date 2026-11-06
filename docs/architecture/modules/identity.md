# platform.identity — 对外接口与「再加一个」的标准做法

范围：一页。上半是别的模块今天能用到的接口清单；下半是它拥有的每一类东西，再加一个时的标准做法，按 ADR-0090（命令按形状强类型）与 ADR-0091（权限与角色由所属模块声明）的目标形状写，并点明由哪条守护锁住。核对日期 2026-11-06，origin/main；清单来自 `api`、`events`、`web` 目录与 `docs/architecture/events/identity.yaml`，数量用 `ls`/`grep -c` 数出，不是估计。「锁」一列分两种：**今天已有**（测试类名可在仓库找到）与**待建**（在对应 ADR 的迁移步骤里，写明在哪一步建）。

## 1. 对外接口（别的模块可见的）

| 类别 | 内容 | 位置 |
|---|---|---|
| 端口接口（3） | `RoleDirectory`、`SessionRoles`、`SingleOperatorMode` | `platform/identity/api` |
| 值与枚举 | `Permission`（今天的权限码登记表，见 ADR-0091）、`RoleCode`、`RoleId`、`AccessScope`、`AccessScopeKind`、`AccessGrant`、`AccessTarget`、`IdentityError`（错误码，每模块一个，G48）等 | `platform/identity/api` |
| 事件（24） | 账号、授权请求、角色、应急管理员、权限调整各类；载荷登记在 `docs/architecture/events/identity.yaml` | `platform/identity/events` |
| HTTP | 31 个端点，前缀 `/api/v1/platform`（会话、角色、账号授予、授权请求、权限调整、管理员模式、强制登出） | `platform/identity/web` |
| 内核端口（实现方） | `AccountEmployees`、`AccountRoles`、`CallerPermissions`、`OrgUnitAccess`、`RoleHolderAccounts`、`SeparationReview`、`EmployeeAccountAccess` 在 identity 的 `application` 里有实现类（`AccountEmployeeMapping`、`AccountRoleHolds`、`AuthorityCallerPermissions`（在 `infrastructure/security`）、`OrgUnitScopes`、`ListRoleHolders`、`AdministratorSeparationReview`、`EmployeeAccountAccessService`）；其余模块通过内核调用，不加模块边 | `kernel` |
| 它用别人的 | 只经 `backend/architecture/platform-org-interfaces.txt` 登记的 workforce `api` 与 `events`（组织单元、员工画像、登录查找、`EmployeeSeparated`） | 同左 |

**引用统计**（2026-11-06，origin/main；脚本取每个 `api`、`events` 类名，在模块目录之外的 `backend/src/main` 与 `backend/src/test` 里各按整词搜索；每个类名一条命令：`git grep -lw <类名> -- backend/src/main`、`git grep -lw <类名> -- backend/src/test`，再去掉模块自己目录下的文件）：

| 类别 | 个数 | 主代码有引用 | 只有测试引用 | 两边都没有 |
|---|---|---|---|---|
| `api` 类型 | 23 | 4 | 9 | 10 |
| `events` 类型 | 24 | 0 | 9 | 15 |

`api` 里两边都没有引用的（退出候选，先看模块内是否在用，再决定改成模块内部类型）：`AccessGrant`、`AccessScope`、`AccessTarget`、`AccountRoleGrantId`、`PermissionAdjustmentKind`、`PermissionAdjustmentOrigin`、`PermissionAdjustmentStatus`、`ProjectRole`、`SingleOperatorMode`、`SingleOperatorRelaxation`。`events` 没有 Java 消费方不等于多余（发布出去给目录和将来的订阅者用），所以不列退出候选，只列事实：主代码无任何引用，其中 15 个测试也不引用——`AccountEnabled`、`AccountSeparated`、`AuthorizationConfirmed`、`AuthorizationOpened`、`AuthorizationRejected`、`AuthorizationVoided`、`EmergencyAdminCredentialsReset`、`EmergencyAdminDisabled`、`EmergencyAdminEnabled`、`EmergencyAdminRegistered`、`PermissionAdjustmentAdjusted`、`PermissionAdjustmentClosedBySeparation`、`PermissionAdjustmentEscalated`、`PermissionAdjustmentExpired`、`PermissionAdjustmentUpheld`。局限：按类名整词搜，同名的别处类型会让数字偏多（不会偏少）；经 JSON 字符串、反射或 asyncapi 的消费不在其中。目标形态是只暴露消费方真用的。

注：identity 是被依赖最多的平台模块（payroll、approval、numbering、timesheet、eventing 依赖它，它只依赖 workforce、integration、audit），所以它不能 import 那些模块的类型，只能经内核或字符串码引用它们的权限（ADR-0091 决定 3）。

## 2. 它拥有的东西，再加一个的标准做法

### 2.1 加一个权限码（或停用一个）

**文件数**：今天 7 个文件、跨 2 个模块（量法：`git grep -l ORGANIZATION_WORKFORCE_SEPARATION_HOLD_APPROVE -- backend/src/main` 得 identity 的 `Permission`、`AdministratorMode`、`SensitivePermissions`、`SystemRoleDeclaration` 4 个，另加用到该码串的 workforce 的 `SeparationHolds`、`SeparationHoldService`、`WorkforcePermissions` 3 个，共 7，另有 `backend/architecture/data-scope-read-paths.txt` 1 个登记）→ 目标 2 个：所属模块的一个声明文件，加 `SystemRoleDeclaration`（要授予时）；使用方引用声明，不计入。目标数是 ADR-0091 的设计值，未实测。

1. 在**拥有该功能的模块**的 `api` 里的权限声明接口加一条：码（格式 G43）、授予方式（ADR-0032 决定 2）、停用标记（ADR-0034 决定 1 第 3 条）；注解用的字符串常量与 `PermissionCode` 值写在同一处。不在 identity 的 `Permission` 里加，也不在自己模块里再复制一份常量类。（ADR-0091 迁移 P1 之前，仍在 `Permission` 与 `Permission.Codes` 里加。）
2. 要授予谁：改 `SystemRoleDeclaration`，并加一次声明修订号（ADR-0034 决定 1 第 7 条）；这一处刻意集中，安全评审只看一处。
3. 授予方式快照 backend/architecture/permission-grant-modes.txt（ADR-0032，现不在 main 上）存在之后，改一次，归用户。
4. 停用不删：目录只增不删。

锁：`NamingSurfaceTest`（G43 格式，今天已有）；收集目录的失败检查——重复码、格式、授予了目录外的码、identity 内出现目录外的码、敏感清单与双人逐人码集合不一致、别模块已声明的码不得再声明（**待建**，ADR-0091 P1）。

### 2.2 加一个授予入口（谁能给账号加角色）

**文件数**：今天，一个新授予入口除自己的用例外，还要自己记得判断在职：identity 的 `application` 里 `isEmployed` 调用点分布在 5 个文件（`ConfirmAuthorizationRequest`、`GrantRoleByOperations`、`HandleEmployeeSeparated`、`ReplaceAccountRoles`（2 处）、`SubjectEmployment`，量法：`git grep -c isEmployed -- backend/src/main/java/com/forcitis/one/platform/identity`），其中授予入口自己判断的是 `GrantRoleByOperations`、`ReplaceAccountRoles` 两个文件共 3 处 → 目标（#1599 完成后）新入口 1 个文件、0 处判断，授予入口自带判断 3 → 0。

标准做法：**走 `AccountGrants.replace` 这扇写入门，不在入口里自己判断在职。** 「已离职的人不得被授予角色」只在这扇门里、在账号锁之下、用实时雇佣来源判断一次，对每种来源都一样（工单 #1599 把现有三处入口自己的判断收进这扇门）。新入口只做：认定调用者与目标、组装想要的角色集合、调用门、处理门的拒绝结果；不调用 `isEmployed`。

锁：#1599 要建的守护——`platform.identity` 内只有门类（与 `SubjectEmployment`）调用 `isEmployed`，别处多一个调用即红（**待建**，#1599 完成标准 2）。

### 2.3 加一种登录方式或会话类型

**文件数**：今天 1 个入口用例加共用的 `LoginAdmission`、`LoginThrottle`、`SessionRegistry`（复用不改）；目标不变，未实测各入口的实际改动文件数。

登录方式各有一个应用层入口（企业微信登录、签名票据登录、应急管理员登录、本地测试登录），共用 `LoginAdmission` 判定、`LoginThrottle` 限流与 `SessionRegistry`。新方式：新增一个入口用例，经 `LoginAdmission` 和 `SessionRegistry`，不另写判定与限流；会话种类加枚举值。

锁：`ArchitectureRulesTest` G28（每个 web 处理器带 `@PreAuthorize` 或 `@PublicEndpoint`）与 G18（禁 `@PermitAll`），`ApplicationIntegrationTest` I-3（匿名可达的处理器集合等于 `backend/architecture/public-endpoints.txt`）（均今天已有）。

### 2.4 加一个事件

**文件数**：无消费方的新事件今天 3 个：事件 record、`docs/architecture/events/identity.yaml`、`asyncapi.yaml`（量法：`git grep -lw EmployeeSeparated` 的目录登记命中 `docs/architecture/events` 2 个、`backend/architecture` 2 个）；有消费方的再加消费方用例与 `platform-org-interfaces.txt` 一行 → 目标不变。

1. 在 `platform/identity/events` 加一个过去时的 record，只含 ID 与值对象。
2. 载荷写进 `docs/architecture/events/identity.yaml` 与 `asyncapi.yaml`；设计类 PR（`docs`）可以写事件目录，其余类型的 PR 改它会被入队拒绝。
3. 在聚合保存后的同一事务里发布；消费方去重（coding-core 第 B 节第 8 条）。

锁：`EventCatalogueTest`（今天已有）。

### 2.5 加一个 HTTP 端点

**文件数**：今天 2 至 3 个：控制器、响应 record、（要幂等时）`idempotent-endpoints.txt` 一行 → 目标不变。

控制器在 `platform/identity/web`，前缀 `/api/v1/platform`；`@PreAuthorize` 用权限声明的字符串常量（2.1）；响应用 `*Response` record；列表加数据范围过滤，范围外按不存在处理；POST 要幂等时登记在 `backend/architecture/idempotent-endpoints.txt`；敏感响应字段不超出登记。

锁：`OpenApiExportTest`（导出基线）、`ArchitectureRulesTest` G28（每个处理器有 `@PreAuthorize` 或 `@PublicEndpoint`）、`NamingSurfaceTest` G43（`@PreAuthorize` 用的码在目录内）、`IdempotentEndpointGuardTest`（均今天已有）。

### 2.6 加一种命令类（多种类带可选字段的命令）

**文件数**：今天 identity 无此类命令；出现时按 ADR-0090 与 workforce 页 2.1 的数。

identity 今天没有这种命令家族（授权请求有 `AuthorizationRequestKind` 但字段随种类变化小；`PermissionAdjustmentKind` 同理）。出现一组随种类而变的可选字段时，按 ADR-0090 决定 1 的判据拆成每形状一个 record；不满足判据的枚举不动。

锁：ADR-0090 的等价测试与签名快照（**待建**，随首个转换的形状）；本模块无转换计划。

## 3. 不要这样做

- 不在 identity 里为别的模块的权限新增类型引用（会成环）；用字符串码并让收集检查兜底。
- 不在授予入口里调用 `isEmployed` 或自己判断在职。
- 不把角色授予分写到各模块（ADR-0091 决定 5）。

## 4. 未核对

- 已读的断言：`ArchitectureRulesTest` G28（:402）、G18（:275），`ApplicationIntegrationTest` I-3（:123、:148），`NamingSurfaceTest` G43，`IdempotentEndpointGuardTest`；其余标「今天已有」的守护按类名与登记文件存在判断，断言未逐条读。
- `identity.yaml` 中 24 个事件与 `events` 目录的 24 个类是否一一对应，没有逐个比对；数量来自 `ls`。
- 31 个端点按控制器里的映射注解数出，不含继承或配置生成的端点。
