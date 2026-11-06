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

注：identity 是被依赖最多的平台模块（payroll、approval、numbering、timesheet、eventing 依赖它，它只依赖 workforce、integration、audit），所以它不能 import 那些模块的类型，只能经内核或字符串码引用它们的权限（ADR-0091 决定 3）。

## 2. 它拥有的东西，再加一个的标准做法

### 2.1 加一个权限码（或停用一个）

1. 在**拥有该功能的模块**的 `api` 里的权限声明接口加一条：码（格式 G43）、授予方式（ADR-0032 决定 2）、停用标记（ADR-0034 决定 1 第 3 条）；注解用的字符串常量与 `PermissionCode` 值写在同一处。不在 identity 的 `Permission` 里加，也不在自己模块里再复制一份常量类。（ADR-0091 迁移 P1 之前，仍在 `Permission` 与 `Permission.Codes` 里加。）
2. 要授予谁：改 `SystemRoleDeclaration`，并加一次声明修订号（ADR-0034 决定 1 第 7 条）；这一处刻意集中，安全评审只看一处。
3. 授予方式快照 backend/architecture/permission-grant-modes.txt（ADR-0032，现不在 main 上）存在之后，改一次，归用户。
4. 停用不删：目录只增不删。

锁：`NamingSurfaceTest`（G43 格式，今天已有）；收集目录的失败检查——重复码、格式、授予了目录外的码、identity 内出现目录外的码、敏感清单与双人逐人码集合不一致、别模块已声明的码不得再声明（**待建**，ADR-0091 P1）。

### 2.2 加一个授予入口（谁能给账号加角色）

标准做法：**走 `AccountGrants.replace` 这扇写入门，不在入口里自己判断在职。** 「已离职的人不得被授予角色」只在这扇门里、在账号锁之下、用实时雇佣来源判断一次，对每种来源都一样（工单 #1599 把现有三处入口自己的判断收进这扇门）。新入口只做：认定调用者与目标、组装想要的角色集合、调用门、处理门的拒绝结果；不调用 `isEmployed`。

锁：#1599 要建的守护——`platform.identity` 内只有门类（与 `SubjectEmployment`）调用 `isEmployed`，别处多一个调用即红（**待建**，#1599 完成标准 2）。

### 2.3 加一种登录方式或会话类型

登录方式各有一个应用层入口（企业微信登录、签名票据登录、应急管理员登录、本地测试登录），共用 `LoginAdmission` 判定、`LoginThrottle` 限流与 `SessionRegistry`。新方式：新增一个入口用例，经 `LoginAdmission` 和 `SessionRegistry`，不另写判定与限流；会话种类加枚举值。

锁：每个端点要么 `@PreAuthorize` 要么 `@PublicEndpoint`，公开端点登记在 `backend/architecture/public-endpoints.txt`（今天已有）。

### 2.4 加一个事件

1. 在 `platform/identity/events` 加一个过去时的 record，只含 ID 与值对象。
2. 载荷写进 `docs/architecture/events/identity.yaml` 与 `asyncapi.yaml`；设计类 PR（`docs`）可以写事件目录，其余类型的 PR 改它会被入队拒绝。
3. 在聚合保存后的同一事务里发布；消费方去重（coding-core 第 B 节第 8 条）。

锁：`EventCatalogueTest`（今天已有）。

### 2.5 加一个 HTTP 端点

控制器在 `platform/identity/web`，前缀 `/api/v1/platform`；`@PreAuthorize` 用权限声明的字符串常量（2.1）；响应用 `*Response` record；列表加数据范围过滤，范围外按不存在处理；POST 要幂等时登记在 `backend/architecture/idempotent-endpoints.txt`；敏感响应字段不超出登记。

锁：`OpenApiExportTest`（导出基线）、`ArchitectureRulesTest`（分层与注解）、`IdempotentEndpointGuardTest`（均今天已有）。

### 2.6 加一种命令类（多种类带可选字段的命令）

identity 今天没有这种命令家族（授权请求有 `AuthorizationRequestKind` 但字段随种类变化小；`PermissionAdjustmentKind` 同理）。出现一组随种类而变的可选字段时，按 ADR-0090 决定 1 的判据拆成每形状一个 record；不满足判据的枚举不动。

锁：ADR-0090 的等价测试与签名快照（**待建**，随首个转换的形状）；本模块无转换计划。

## 3. 不要这样做

- 不在 identity 里为别的模块的权限新增类型引用（会成环）；用字符串码并让收集检查兜底。
- 不在授予入口里调用 `isEmployed` 或自己判断在职。
- 不把角色授予分写到各模块（ADR-0091 决定 5）。

## 4. 未核对

- 两个守护的具体断言没有逐条读（`ArchitectureRulesTest` 对公开端点登记、`IdempotentEndpointGuardTest` 对幂等登记）；表中只说「今天已有」是按类名与登记文件存在判断的。
- `identity.yaml` 中 24 个事件与 `events` 目录的 24 个类是否一一对应，没有逐个比对；数量来自 `ls`。
- 31 个端点按控制器里的映射注解数出，不含继承或配置生成的端点。
