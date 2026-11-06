# organization.workforce — 对外接口与「再加一个」的标准做法

范围：一页。上半是别的模块今天能用到的接口清单；下半是它拥有的每一类东西，再加一个时的标准做法，按 ADR-0090（命令按形状强类型）与 ADR-0091（权限与角色由所属模块声明）的目标形状写，并点明由哪条守护锁住。核对日期 2026-11-06，origin/main；数量来自 `ls`、`grep -c`。「锁」一列分两种：**今天已有**（测试类名可在仓库找到）与**待建**（在对应 ADR 的迁移步骤里，写明在哪一步建）。

## 1. 对外接口（别的模块可见的）

| 类别 | 内容 | 位置 |
|---|---|---|
| 端口接口（13） | 读：`OrgDirectory`、`EmployeeProfiles`、`EmployeeLabels`、`EmployeeLegalEntities`、`EmploymentRelationships`（雇佣关系视图）、`LegalEntityDirectory`、`LegalEntityNumberUsage`、`PositionLookup`；写与流程：`PersonnelCommands`、`OrgStructureCommands`、`ProfileChangeRequests`、`SeparationHolds`、`AttributionCountersigns` | `organization/workforce/api` |
| 命令入口（宽 record） | `EnterPersonnelCommand`（24 个组件）、`EnterOrgStructureCommand`（15 个组件）；验收探针按名字反射它们，S3 之前不改名（ADR-0090 决定 2） | 同左 |
| 值与枚举 | 各类 ID、`PersonnelCommandKind`（14 种）、`OrgStructureCommandKind`（9 种）、`OrgStructureTargetKind`、`EmploymentStatus`、`EmploymentVerdict`、`WorkforceError`（错误码，每模块一个，G48）等 | 同左 |
| 事件（34） | 入离转调、任职关系、法人归属、组织单元与职位、离职冻结各类；载荷登记在 `docs/architecture/events/workforce.yaml` | `organization/workforce/events` |
| HTTP | 41 个端点，前缀 `/api/v1/organization`（员工、人事命令、组织结构命令、组织单元、职位、法人、法人归属、资料变更申请、会签、离职冻结、主任职） | `organization/workforce/web` |
| 内核端口（调用方） | 向 identity 问 `SeparationReview`、`AccountEmployees`、`EmployeeAccountAccess`、`CallerPermissions`、`OrgUnitAccess`；实现 `ExternalSubjectEmployees`（供 integration 查） | `kernel` |
| 谁用它 | 9 个模块：`accounting.ledger`、`cost.timesheet`、`organization.administration`、`organization.leave`、`organization.recruitment`、`organization.payroll`、`platform.approval`、`platform.identity`、`platform.numbering`，经 `backend/architecture/module-dependencies.txt` 登记，其中 platform 层的三个另在 `backend/architecture/platform-org-interfaces.txt` 登记；公开签名固定在 `backend/architecture/platform-org-interfaces.signatures.txt` | `backend/architecture` |

**引用统计**（2026-11-06，origin/main；做法同 identity 页：每个 `api`、`events` 类名在模块目录之外的主代码与测试里各按整词搜索，每个类名一条命令：`git grep -lw <类名> -- backend/src/main`、`git grep -lw <类名> -- backend/src/test`，再去掉模块自己目录下的文件）：

| 类别 | 个数 | 主代码有引用 | 只有测试引用 | 两边都没有 |
|---|---|---|---|---|
| `api` 类型 | 68 | 23 | 12 | 33 |
| `events` 类型 | 34 | 9 | 5 | 20 |

`api` 里两边都没有引用的 33 个（退出候选，先看模块内是否在用，再决定改成模块内部类型）：`ActingDutyEndCause`、`ActingDutyId`、`ApproveAttributionCountersign`、`AssignmentId`、`AttributionCountersignDetail`、`AttributionCountersignId`、`AttributionCountersignStatus`、`AttributionCountersignSummary`、`AttributionCountersigns`、`AttributionKind`、`AttributionSubjectId`、`BlockingArrival`、`ChangeEmployeeProfile`、`CountersignCommandKind`、`CountersignCommandStatus`、`CountersignDecisionKind`、`CountersignRequestKind`、`EmployeeProfiles`、`EmploymentPhaseKind`、`Gender`、`HeldSeparation`、`MergedPosts`、`OrgMergePreview`、`OrgStructureCommands`、`PreviewOrgUnitMerge`、`ProfileChangeRequestId`、`ProfileChangeRequestView`、`ProfileChangeRequests`、`ProfileChangeStatus`、`ProfileVoidReason`、`RegionKind`、`RejectAttributionCountersign`、`WithdrawOrgStructureCommand`。注意：`EmployeeProfiles`、`OrgStructureCommands` 在清单里是端口，但没有别的模块的 Java 代码引用它们（验收探针按名反射，不算类型引用），可能是真退出候选，也可能是 S3 前探针的依赖，退出前先看。`events` 两边都没有引用的 20 个只列事实，不列退出候选（发布给目录和将来订阅者）：`EmployeeActingDutyEnded`、`EmployeeActingDutyStarted`、`EmployeeLegalEntityChanged`、`EmployeeRegularized`、`EmploymentRelationshipCorrected`、`EmploymentRelationshipEnded`、`EmploymentRelationshipPhaseChanged`、`EmploymentRelationshipStarted`、`EmploymentRelationshipUnpaidLeaveChanged`、`LegalEntityActivated`、`LegalEntityCodeCorrected`、`LegalEntityDeactivated`、`LegalEntityRenamed`、`OrgUnitAttributesSet`、`OrgUnitCreated`、`OrgUnitHeadAssigned`、`OrgUnitReceivablesManagerAssigned`、`OrgUnitReparented`、`PositionCreated`、`PositionDisabled`。局限同 identity 页。目标形态是只暴露消费方真用的。

注：workforce 不能依赖 identity（图上 identity 依赖 workforce），所以它的权限声明用的类型必须在内核，而不是 identity 的 `Permission`（ADR-0091 决定 4，P1）。

## 2. 它拥有的东西，再加一个的标准做法

### 2.1 加一种人事命令（或给一种命令加一个形状）

**文件数**：今天，以最近加的 `ACTING_START` 为例（`git grep -l ACTING_START`）：主代码 11 个（`PersonnelCommandKind`、`PersonnelCommandService`、`PersonnelCommand`、`PersonnelCommandRules`、`ActingDutyRules`、`CommandEffects`、`ApplyDueCommand`、`EffectPublisher`、`JdbcPersonnelCommandRepository`、`CountersignCommandKind`、一个迁移）、`backend/architecture` 登记与快照 2 个、测试 15 个。这 11 个里是**每种命令都要碰的固定动作**的有：种类枚举、宽 record 加组件、聚合里的分支、HTTP 请求 record、前端 `KIND_FIELDS`（2 个文件：`personnel-command.ts`、`personnel-command-form-page.tsx`，`ACTING_START` 在前端 0 处，正是 #1579 的缺口），其余是该种类自己的行为。固定动作今天 6 个文件（不含行为与迁移）→ 目标（S3 后）2 个：种类枚举值、形状 record；HTTP 请求与前端字段表由密封类型生成，聚合分支消失。目标数是 ADR-0090 的设计值，未实测。

今天：加一个 `PersonnelCommandKind` 值，在 `EnterPersonnelCommand` 加可空组件，`PersonnelCommand` 里加该种类的分支，HTTP 请求 record 再写一份，前端 `KIND_FIELDS` 写第三份。

按 ADR-0090 的标准做法：

1. 在 `PersonnelCommandBody` 密封接口下为该**形状**加一个 record，只含自身组件；一个种类有两种形状就加两个（HIRE 的新人与已有员工）；共有的部分用嵌套 record（日期对、个人数据）。
2. record 构造时校验自己的必填与禁填，不在聚合里写 `if (kind == ...)`。
3. 提供转成 `EnterPersonnelCommand` 的方法；S3 之前 `enter` 的参数仍是宽 record，探针契约不动。
4. 状态机、到期应用、会签冻结、审计、幂等沿用同一份，表不改（同一行）。入库后才确定的状态留在聚合上，不进命令体。
5. 加等价测试：同一份平的 JSON 经强类型体转成的宽 record，与直接解析成宽 record 相同。

锁：强类型体到宽 record 的等价测试（**待建**，ADR-0090 S1 起随各形状）；`OpenApiExportTest`（密封体改变导出形状时更新基线，今天已有）；`PlatformOrgInterfacesTest` 的签名快照（api 形状变化要重生成，今天已有）；S3 的 7 个验收文件由测试会话的卡改。

### 2.2 加一种组织结构命令

**文件数**：今天以组织结构命令的种类为例（`git grep -l SET_ATTRIBUTES -- backend/src/main`）：主代码 5 个（`OrgStructureCommandKind`、`OrgStructureCommand`、`OrgEffects`、`OrgCommandEvents`、迁移）。固定动作今天：种类枚举、宽 record、`OrgStructureCommand` 里按（对象种类，命令种类）的规则、HTTP 请求 record、前端字段表 5 个 → 目标 2 个：（对象种类，命令种类）一对的形状 record 与枚举值。量法同 2.1，目标数未实测。

组织结构命令的字段由（对象种类，命令种类）一对决定，形状按这一对：为新的一对加一个 record（职位只接受 CREATE 与 DISABLE；组织单元的 CREATE 要交付单元标志）；其余同 2.1。HTTP 边缘用 `oneOf` 还是平的请求 record，由 ADR-0090 的 S0 原型按证据定，两种结果都不需要新 ADR。

锁：同 2.1；S0 原型的结论记入第一张转换卡（**待建**）。

### 2.3 加一个权限码

**文件数**：与 identity 页 2.1 同一例（`ORGANIZATION_WORKFORCE_SEPARATION_HOLD_APPROVE`）：今天 7 个文件跨 2 个模块 → 目标 2 个。

在 workforce 的 `api` 里的权限声明接口加一条（码、授予方式、停用标记）；`@PreAuthorize` 与 `PersonnelCommandService` 引用同一处，**不再复制** `WorkforcePermissions`（今天在 `web`，且在服务里又复制了一份字符串）；要授予谁，去 identity 的 `SystemRoleDeclaration` 加并加修订号。ADR-0091 迁移 P1 之前，仍在 identity 的 `Permission` 加，并先建内核的 `PermissionCode`。

锁：收集目录的失败检查（重复码、格式、别模块已声明的码）（**待建**，ADR-0091 P1）；`NamingSurfaceTest` G43（今天已有）。

### 2.4 加一个事件

**文件数**：无消费方的新事件今天 3 个（事件 record、`workforce.yaml`、`asyncapi.yaml`）；有消费方的再加消费方用例，platform 层的再加 `platform-org-interfaces.txt` 一行（量法：`git grep -lw EmployeeSeparated -- backend/src/main` 命中 9 个）→ 目标不变。

在 `organization/workforce/events` 加过去时 record，载荷写进 `docs/architecture/events/workforce.yaml`（设计类 PR 可写；其余 PR 改它会被入队拒绝），在聚合保存后的同一事务里发布。identity 等消费方若要新增订阅，先到 `platform-org-interfaces.txt` 登记接口。

锁：`EventCatalogueTest`（今天已有）；`PlatformOrgInterfacesTest`（订阅登记，今天已有）。

### 2.5 加一个对外读端口

**文件数**：今天 4 个：`api` 接口、`application` 实现、`module-dependencies.txt` 或 `platform-org-interfaces.txt` 一行、签名快照重生成 → 目标不变；另需在使用方引用点之前先看上面的引用统计，别新增没人用的。

在 `api` 加接口（G2：`api` 只放接口、record、枚举），实现放 `application`；调用方要在 `module-dependencies.txt` 有边，platform 层的还要在 `platform-org-interfaces.txt` 有一行并重生成签名快照；返回按数据范围过滤，范围外按不存在处理。在职判定一律经 `EmploymentRelationships`，不自己拼。

锁：`ModuleDependencyTest`、`PlatformOrgInterfacesTest`（今天已有）。

### 2.6 加一个 HTTP 端点

**文件数**：今天 2 至 3 个：控制器、响应 record、（要幂等时）`idempotent-endpoints.txt` 一行；人事与组织结构命令的请求 record 另算在 2.1、2.2 → 目标不变。

控制器在 `organization/workforce/web`，前缀 `/api/v1/organization`；`@PreAuthorize` 用 2.3 的常量；响应 `*Response` record；POST 要幂等的登记在 `backend/architecture/idempotent-endpoints.txt`。

锁：`OpenApiExportTest`、`IdempotentEndpointGuardTest`、`ArchitectureRulesTest` G28（每个处理器带 `@PreAuthorize` 或 `@PublicEndpoint`）（均今天已有）。

### 2.7 加一个错误码

**文件数**：今天 1 个（`WorkforceError`）→ 目标不变。

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
- 已读的断言：`ArchitectureRulesTest` G28、`NamingSurfaceTest` G43、`IdempotentEndpointGuardTest`；其余标「今天已有」的守护按类名与登记文件存在判断，断言未逐条读。
