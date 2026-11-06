# organization.workforce — 对外接口与「再加一个」的标准做法

范围：一页。上半是别的模块今天能用到的接口清单；下半是它拥有的每一类东西，再加一个时的标准做法，按 ADR-0090（命令按形状强类型）与 ADR-0091（权限与角色由所属模块声明）的目标形状写，并点明由哪条守护锁住。核对日期 2026-11-06，origin/main；数量来自 `ls`、`grep -c`。「锁」一列分两种：**今天已有**（测试类名可在仓库找到）与**待建**（在对应 ADR 的迁移步骤里，写明在哪一步建）。

## 1. 对外接口（别的模块可见的）

| 类别 | 内容 | 位置 |
|---|---|---|
| 端口接口（13） | 读：`OrgDirectory`、`EmployeeProfiles`、`EmployeeLabels`、`EmployeeLegalEntities`、`EmploymentRelationships`（含 `inServiceIn(month)` 在职判定）、`LegalEntityDirectory`、`LegalEntityNumberUsage`、`PositionLookup`；写与流程：`PersonnelCommands`、`OrgStructureCommands`、`ProfileChangeRequests`、`SeparationHolds`、`AttributionCountersigns` | `organization/workforce/api` |
| 命令入口（宽 record） | `EnterPersonnelCommand`（24 个组件）、`EnterOrgStructureCommand`（15 个组件）；验收探针按名字反射它们，S3 之前不改名（ADR-0090 决定 2） | 同左 |
| 值与枚举 | 各类 ID、`PersonnelCommandKind`（14 种）、`OrgStructureCommandKind`（9 种）、`OrgStructureTargetKind`、`EmploymentStatus`、`EmploymentVerdict`、`WorkforceError`（错误码，每模块一个，G48）等 | 同左 |
| 事件（34） | 入离转调、任职关系、法人归属、组织单元与职位、离职冻结各类；载荷登记在 `docs/architecture/events/workforce.yaml` | `organization/workforce/events` |
| HTTP | 41 个端点，前缀 `/api/v1/organization`（员工、人事命令、组织结构命令、组织单元、职位、法人、法人归属、资料变更申请、会签、离职冻结、主任职） | `organization/workforce/web` |
| 内核端口（调用方） | 向 identity 问 `SeparationReview`、`AccountEmployees`、`EmployeeAccountAccess`、`CallerPermissions`、`OrgUnitAccess`；实现 `ExternalSubjectEmployees`（供 integration 查） | `kernel` |
| 谁用它 | identity、approval、numbering（platform 层）与 leave、timesheet、administration、ledger（见 `module-dependencies.txt`），经 `backend/architecture/module-dependencies.txt` 与（platform 层）`backend/architecture/platform-org-interfaces.txt` 登记；公开签名固定在 `backend/architecture/platform-org-interfaces.signatures.txt` | `backend/architecture` |

注：workforce 不能依赖 identity（图上 identity 依赖 workforce），所以它的权限声明用的类型必须在内核，而不是 identity 的 `Permission`（ADR-0091 决定 4，P1）。

## 2. 它拥有的东西，再加一个的标准做法

### 2.1 加一种人事命令（或给一种命令加一个形状）

今天：加一个 `PersonnelCommandKind` 值，在 `EnterPersonnelCommand` 加可空组件，`PersonnelCommand` 里加该种类的分支，HTTP 请求 record 再写一份，前端 `KIND_FIELDS` 写第三份。

按 ADR-0090 的标准做法：

1. 在 `PersonnelCommandBody` 密封接口下为该**形状**加一个 record，只含自身组件；一个种类有两种形状就加两个（HIRE 的新人与已有员工）；共有的部分用嵌套 record（日期对、个人数据）。
2. record 构造时校验自己的必填与禁填，不在聚合里写 `if (kind == ...)`。
3. 提供转成 `EnterPersonnelCommand` 的方法；S3 之前 `enter` 的参数仍是宽 record，探针契约不动。
4. 状态机、到期应用、会签冻结、审计、幂等沿用同一份，表不改（同一行）。入库后才确定的状态留在聚合上，不进命令体。
5. 加等价测试：同一份平的 JSON 经强类型体转成的宽 record，与直接解析成宽 record 相同。

锁：强类型体到宽 record 的等价测试（**待建**，ADR-0090 S1 起随各形状）；`OpenApiExportTest`（密封体改变导出形状时更新基线，今天已有）；`PlatformOrgInterfacesTest` 的签名快照（api 形状变化要重生成，今天已有）；S3 的 7 个验收文件由测试会话的卡改。

### 2.2 加一种组织结构命令

组织结构命令的字段由（对象种类，命令种类）一对决定，形状按这一对：为新的一对加一个 record（职位只接受 CREATE 与 DISABLE；组织单元的 CREATE 要交付单元标志）；其余同 2.1。HTTP 边缘用 `oneOf` 还是平的请求 record，由 ADR-0090 的 S0 原型按证据定，两种结果都不需要新 ADR。

锁：同 2.1；S0 原型的结论记入第一张转换卡（**待建**）。

### 2.3 加一个权限码

在 workforce 的 `api` 里的权限声明接口加一条（码、授予方式、停用标记）；`@PreAuthorize` 与 `PersonnelCommandService` 引用同一处，**不再复制** `WorkforcePermissions`（今天在 `web`，且在服务里又复制了一份字符串）；要授予谁，去 identity 的 `SystemRoleDeclaration` 加并加修订号。ADR-0091 迁移 P1 之前，仍在 identity 的 `Permission` 加，并先建内核的 `PermissionCode`。

锁：收集目录的失败检查（重复码、格式、别模块已声明的码）（**待建**，ADR-0091 P1）；`NamingSurfaceTest` G43（今天已有）。

### 2.4 加一个事件

在 `organization/workforce/events` 加过去时 record，载荷写进 `docs/architecture/events/workforce.yaml`（设计类 PR 可写；其余 PR 改它会被入队拒绝），在聚合保存后的同一事务里发布。identity 等消费方若要新增订阅，先到 `platform-org-interfaces.txt` 登记接口。

锁：`EventCatalogueTest`（今天已有）；`PlatformOrgInterfacesTest`（订阅登记，今天已有）。

### 2.5 加一个对外读端口

在 `api` 加接口（G2：`api` 只放接口、record、枚举），实现放 `application`；调用方要在 `module-dependencies.txt` 有边，platform 层的还要在 `platform-org-interfaces.txt` 有一行并重生成签名快照；返回按数据范围过滤，范围外按不存在处理。在职判定一律经 `EmploymentRelationships`，不自己拼。

锁：`ModuleDependencyTest`、`PlatformOrgInterfacesTest`（今天已有）。

### 2.6 加一个 HTTP 端点

控制器在 `organization/workforce/web`，前缀 `/api/v1/organization`；`@PreAuthorize` 用 2.3 的常量；响应 `*Response` record；POST 要幂等的登记在 `backend/architecture/idempotent-endpoints.txt`。

锁：`OpenApiExportTest`、`IdempotentEndpointGuardTest`、`ArchitectureRulesTest`（今天已有）。

### 2.7 加一个错误码

`WorkforceError` 是本模块唯一的错误码枚举（G48、CS-2.24），只往里加；拆分是规则变更，走 CS-7.5，由用户定，本页不提。

锁：`NamingSurfaceTest` G48（今天已有）。

## 3. 不要这样做

- 不把 `EnterPersonnelCommand` 改名、换成接口或冻结它的组件（探针会全部变红）；新字段先进强类型体。
- 不在 workforce 里 import identity（成环）。
- 不在别的模块里复制 workforce 的权限码常量。

## 4. 未核对

- 13 个端口接口的方法清单与调用方没有逐个列；调用方范围取自两个登记文件，未逐文件核对。
- 41 个端点按控制器映射注解数出，不含继承或配置生成的端点。
- `ExternalSubjectEmployees` 由 workforce 实现取自 `module-dependencies.txt` 的注释（RULING-P3B-9），未读实现类。
- 「两个守护今天已有」按类名与登记文件存在判断，未逐条读断言。
