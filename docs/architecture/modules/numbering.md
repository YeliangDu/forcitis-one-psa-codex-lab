# platform.numbering — 对外接口与「再加一个」的标准做法

范围：一页，格式同 identity、workforce 两页。上半是别的模块今天能用到的接口清单与引用统计；下半是它拥有的东西再加一个时的标准做法与文件数。核对日期 2026-11-06，origin/main；数量来自 `ls`、`git grep`，不是估计。「锁」分**今天已有**与**待建**。

职责：单据编号：编号规则版本、计数器、全局编号、旧号登记与查询。

## 1. 对外接口（别的模块可见的）

| 类别 | 内容 |
|---|---|
| 端口接口（8） | `DocumentNumbers`、`DocumentTypes`、`FindDocumentByLegacyNumber`、`FindDocumentsByLegacyPrefix`、`FindLegacyNumbersOfDocument`、`GlobalDocumentNumbers`、`RegisterLegacyNumbers`、`RegisteredDocumentTypes` |
| `api` 类型 | 共 21 个；错误码枚举 `NumberingError`（每模块一个，G48） |
| 事件（3） | `NumberingRuleConfirmationGiven`、`NumberingRuleConfirmed`、`NumberingRuleReconfigured`；载荷登记在 `docs/architecture/events/numbering.yaml` |
| HTTP | 7 个端点（`LegacyNumberController`、`NumberingRuleController`） |
| 谁在主代码里用它 | `cost/timesheet`（4 个类型）、`kernel 等`（1 个类型）、`organization/recruitment`（3 个类型）、`platform/approval`（2 个类型）、`platform/integration`（1 个类型） |

**引用统计**（2026-11-06，origin/main；每个类名一条命令：`git grep -lw <类名> -- backend/src/main`、`git grep -lw <类名> -- backend/src/test`，再去掉模块自己目录下的文件）：

| 类别 | 个数 | 主代码有引用 | 只有测试引用 | 两边都没有 |
|---|---|---|---|---|
| `api` 类型 | 21 | 8 | 5 | 8 |
| `events` 类型 | 3 | 0 | 0 | 3 |

`api` 里两边都没有引用的（退出候选，先看模块内是否在用，再决定改成模块内部类型）：`ConfirmationKind`、`FindDocumentByLegacyNumber`、`FindDocumentsByLegacyPrefix`、`FindLegacyNumbersOfDocument`、`LegacyNumberEntry`、`NumberingRuleView`、`NumberingTemplateView`、`RegisterLegacyNumbers`。
`events` 两边都没有引用的只列事实，不列退出候选（发布给目录和将来的订阅者）：`NumberingRuleConfirmationGiven`、`NumberingRuleConfirmed`、`NumberingRuleReconfigured`。
局限：按类名整词搜，同名的别处类型会让数字偏多；经 JSON 字符串、反射或 asyncapi 的使用看不到。目标形态是只暴露消费方真用的。

它用别人的（`backend/architecture/module-dependencies.txt`）：`platform.identity`、`organization.workforce`、`platform.integration`。

## 2. 它拥有的东西，再加一个的标准做法

### 2.1 加一个权限码

按 ADR-0091：在本模块的 `api` 权限声明接口加一条（码、授予方式、停用标记），`@PreAuthorize` 与使用点引用同一处，不复制常量类；要授予谁去 identity 的 `SystemRoleDeclaration` 加并加修订号。ADR-0091 迁移 P1 之前仍在 identity 的 `Permission` 里加。

**文件数**：今天以 `PLATFORM_NUMBERING_RULE_UPDATE` 为例，`git grep -l PLATFORM_NUMBERING_RULE_UPDATE -- backend/src/main` 命中 3 个主代码文件（量法如此，使用点若引用的是字符串常量则不计入）→ 目标 1 至 2 个：本模块的声明文件，加 `SystemRoleDeclaration`（要授予时）。目标数是 ADR-0091 的设计值，未实测。

锁：`NamingSurfaceTest` G43（今天已有）；收集目录的失败检查（**待建**，ADR-0091 P1）。

### 2.2 加一个事件

在 `events` 加过去时 record（只含 ID 与值对象），在聚合保存后的同一事务里发布；载荷写进 `docs/architecture/events/numbering.yaml` 与 `asyncapi.yaml`（设计类 PR 可写，其余 PR 改它会被入队拒绝）。

**文件数**：无消费方 3 个（事件 record、事件目录文件、`asyncapi.yaml`）→ 目标不变。

锁：`EventCatalogueTest`（今天已有）。

### 2.3 加一个 HTTP 端点

控制器在 `web`，前缀 `/api/v1/…`；`@PreAuthorize` 用权限声明的常量；响应 `*Response` record；列表加数据范围过滤；要幂等的 POST 登记在 `backend/architecture/idempotent-endpoints.txt`。

**文件数**：今天 2 至 3 个（控制器、请求或响应 record、幂等登记一行）→ 目标不变。

锁：`OpenApiExportTest`、`ArchitectureRulesTest` G28（每个处理器带 `@PreAuthorize` 或 `@PublicEndpoint`）、`IdempotentEndpointGuardTest`（均今天已有）。

### 2.4 加一个对外读端口或类型

在 `api` 加接口或 record（G2：`api` 只放接口、record、枚举），实现放 `application`；调用方在 `module-dependencies.txt` 有边（platform 层读 organization 的另要在 `platform-org-interfaces.txt` 有一行并重生成签名快照）。先看上面的引用统计，别新增没人用的。

**文件数**：今天 3 至 4 个（接口、实现、依赖登记一行、快照重生成）→ 目标不变。

锁：`ModuleDependencyTest`、`ArchitectureRulesTest` G2（今天已有）。

### 2.5 加一个错误码

`NumberingError` 是本模块唯一的错误码枚举（G48、CS-2.24），只往里加；拆分是规则变更，走 CS-7.5，由用户定，本页不提。

**文件数**：今天 1 个 → 目标不变。
锁：`NamingSurfaceTest` G48（今天已有）。

### 2.6 加一个单据类型（让新单据拿到编号）

单据类型是库里的行（`platform_numbering.document_types`），由**迁移**加一行，走数据设计门（例：recruitment 需求类型的迁移 `V20261105180100__platform_numbering_recruitment_requisition_type.sql`）；`DocumentTypeRegistry`/`RegisteredDocumentTypes` 只读它。使用方只经 `DocumentNumbers`/`GlobalDocumentNumbers` 取号，事务内取（coding-core 第 B 节第 5 条）；规则版本经确认后生效。

**文件数**：今天：迁移 1 + 规则配置 1 + 使用方取号调用 → 目标不变。

锁：发起审批时 `Approvals.requireAcceptedDocumentType` 经 `RegisteredDocumentTypes` 拒绝未登记类型（运行期检查，不是守护测试；取号路径上没有这个检查，今天如此）；表结构变化走数据设计门（`docs/data/data-design-gate.md`）。

## 3. 不要这样做

- 不 import 别的模块的内部类（`application`、`domain`、`infrastructure`）；只经对方的 `api`、`events` 或内核端口。
- 不在本模块复制别的模块的权限码常量。

## 4. 未核对

- 引用统计按类名整词搜，局限见第 1 节；端点数按控制器里的映射注解数出。
- 各「今天已有」的守护按类名与登记文件存在判断，断言未逐条读（identity 页第 4 节列出的几个除外）。
- 「谁在主代码里用它」按引用类型统计，不含经内核端口或事件目录的间接依赖。
- 各配方的目标文件数是 ADR-0090、0091 的设计值，未实测。
