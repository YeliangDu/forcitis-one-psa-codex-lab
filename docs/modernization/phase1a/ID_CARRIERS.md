# ID 承载位置清单

ADR-0033 第 7 项第 7 条要求 R-1b 交付：ID 出现的每一类位置，各自的形态与改法，或「不是标识」的结论。本清单随 R-1b P5 交付（卡 `cards/R-1b-P5.md` D-9），结论以合并后的 main 为准。能由守护生成或校验的部分写明出处；守护之外的结论写明理由和到期。

## 1. 总则（来自 ADR-0033）

- 自有对象的 ID 一律是 UUIDv7，由 `IdGenerator` 在应用里生成；聚合根的 ID 在构造聚合时取得，仓储不生成（`IdMakingGuardTest`）。
- ID 在库里是 `uuid`，在接口、事件、日志里是规范小写的字符串；不依赖 ID 的次序，排序与游标按「时刻列 + 主键」（`SqlOrderByGuardTest`）。
- 文本键「类型:ID」不再出现在自有对象上；对象用「类别 + ID」两个字段表达（审计对象 `AuditSubject`）。
- 外部系统的 ID 只作来源 ID 列保存，不当作自有 ID 用。

## 2. 主键

| 项 | 结论 | 出处 |
|---|---|---|
| 自有表主键 | 单列 `uuid`，应用生成；序列、IDENTITY、数据库默认值生成的主键与 `*_id` 列不允许；带 `effective_from_*` 与 `effective_to_*` 的表另有排他约束；`*_version_id` 引用版本行主键 | `PrimaryKeyTypeGuardTest` |
| 框架表 | 五张：`platform_identity.sessions`、`platform_identity.sessions_attributes`、`platform_integration.event_publication`、`platform_integration.event_publication_archive`、`platform_scheduling.scheduler_locks`；键由框架决定，长期豁免，新增须报 one-coordinator | `backend/architecture/primary-key-exemptions.txt` |
| 复合主键的旧表 | 无。`account_roles`、`role_permissions`、`sync_run_held_separations` 已在 P5 改为 UUIDv7 单列主键（`account_role_id`、`role_permission_id`、`sync_run_held_separation_id`），原键成为命名唯一约束 | 本节「D-12 落地记录」 |
| `primary-key-type-baseline.txt` | 已删除（自有表无基线行） | `PrimaryKeyExemptionsTest`（验收） |

### D-12 落地记录（RULING-P5-D12）

- 原建议（保留基线文件放三行）被 one-architecture 判定偏离 ADR-0033 第 1 项第 2 条，one-coordinator 撤回认可；按 CS-1.9「当期做对」在 P5 改。
- `platform_identity.account_roles`：加 `account_role_id uuid` 主键，`uk_account_roles_user_id_role_id (user_id, role_id)`；`platform_identity.role_permissions`：加 `role_permission_id uuid` 主键，`uk_role_permissions_role_id_permission_code (role_id, permission_code)`；`organization_workforce.sync_run_held_separations`：加 `sync_run_held_separation_id uuid` 主键，`uk_sync_run_held_separations_sync_run_id_employee_id (sync_run_id, employee_id)`。种子行的主键由迁移里的 `uuidv7()` 生成，运行时写入由 `IdGenerator` 生成。
- `sync_run_held_separations` 在拆分 b 改造为 workforce 待放行离职表时会再改一次（用户「先保 dev」已接受，intent `2026-10-15-214511`）；ADR-0033 正文不动。

## 3. 引用列与版本引用

| 项 | 结论 | 出处 |
|---|---|---|
| 引用自有对象的列（`*_id`） | `uuid`，引用目标的主键；跨模块引用不建外键，写在注册表 `fk_exceptions` | `DataArchitectureTest`、`data-access-registry.yaml` |
| 审计条目的对象 | `subject_kind text`（CHECK，取值同 `AuditSubjectKind`）+ `subject_id uuid`（为空当且仅当类别为 `REQUEST` / `OPERATION` / `LOGIN_ATTEMPT`）；无外键，指向的对象由类别决定 | `V20261014134000__platform_audit_baseline.sql`；`EventCatalogueTest`（类别与 CHECK 一致） |
| 审计归档批次 | 水位是「时刻 + ID」：`first_entry_created_at`、`last_entry_created_at` 与首末条 ID；取批按 `(created_at, id)` | `AuditArchiveJob`；验收 `AuditArchiveOrderIT` |
| 来源 ID（外部系统） | `source_references.source_id bigint`：外部系统给的整数，不是自有 ID，接口上写成字符串形式的整数（`EXTERNAL_ID_NAMES`）。到期：外部系统退出时随该系统的来源对照一起删除 | `OpenApiExportTest` |

## 4. 文本键

| 项 | 结论 | 到期 |
|---|---|---|
| `organization_workforce.sync_diffs.subject_key`（「employee:<id>」「org-unit:<id>」「position:<id>」） | 保留。同步差异的主体键，随同步一起重做 | 拆分 a；`architecture/typed-text-key-exceptions.txt` 过期即失败 |
| 咨询锁键 `"timesheet-create:" + 工时单 ID`（`JdbcTimesheetRepository`）、`"approval-start:" + 流程键 + 单据类型 + 单据 ID`（`JdbcApprovalRequestRepository`） | 不是标识：只在事务内取咨询锁，不落库、不出接口 | 无 |
| `unique` / `exclude` 里的 `COALESCE(…, 常量)` 哨兵 | 不允许；无例外 | `DdlNamingTest` |
| `kind` 一类的枚举文本列（`*_kind`） | 不是 ID：文本加 CHECK，取值与 Java 枚举一致 | `DdlNamingTest` |

## 5. 事件

| 项 | 结论 | 出处 |
|---|---|---|
| 信封 `aggregateId`、`actorId`、`relatedAggregates[].aggregateId` | `UUID`（Java）/ `format: uuid`（目录）；`aggregateId` 是该聚合自己的主键（审批请求事件为审批请求 ID，工作日历、法人归属、编号规则事件为各自表的主键） | `EventCatalogueTest`、验收 `EventEnvelopeAggregateIdIT` |
| 信封 `source` | 已删除（ADR-0039 第 5 条第 1 款） | `EventEnvelopeContractTest`（验收） |
| 负载里 `x-type` 为类型化 ID 的字段 | `format: uuid` | `EventCatalogueTest` |
| 系统主体 | `actorId` 为 `system-actors.txt` 里名字的 UUIDv5；该目录只增不删 | `SystemActorCatalogueTest`、`scripts/ci/system_actors_append_only.py` |
| 事件的聚合类型 → 审计对象类别 | 驼峰转大写下划线，换算不到即失败并告警；事件目录里每个聚合都能换算 | `AuditDomainEvents`、`EventCatalogueTest` |

## 6. 接口（OpenAPI）

| 项 | 结论 | 出处 |
|---|---|---|
| 路径参数、查询参数、属性中名字以 `Id`、`Ids`、`By` 结尾的 ID | `string` + `format: uuid`，传非规范 uuid 答 400 | `OpenApiExportTest`；验收 `IdQueryParameterIT`、`OpenApiUuidParametersIT` |
| 名字不以 Id / By 结尾的员工 ID | `TaskDetailResponse.initiator`、`TaskDetailResponse.assignee`、`TaskOverviewResponse.assignee` 按全名列入守护，`format: uuid`，不改名（改名是契约变更，随 K-3b-3 或 R3 改名卡） | `OpenApiExportTest.NAMED_OWN_ID_FIELDS` |
| `InboxItemResponse.taskId` | 保留为字符串：承载老系统待办的任务键。到期：ADR-0039 第 6 条契约中性化（RULING-P4-D6） | `OpenApiExportTest.OTHER_ID_NAMES` |
| `SyncDiffResponse.subjectKey` | 保留：同步差异的「类型:ID」文本键。到期：拆分 a（RULING-P5-1） | `OpenApiExportTest.OTHER_ID_NAMES`（`subjectKey` 由守护的识别名单认出） |
| 审计查询参数 | `subjectKind`（枚举）+ `subjectId`（uuid）；`subjectId` 须与 `subjectKind` 一起给；旧参数 `subjectKey` 被拒绝（400） | 验收 `AuditIdContractTest` |

## 7. 会话、日志与其他位置

| 项 | 结论 | 出处 |
|---|---|---|
| 会话主体名 `sessions.principal_name` | 用户 ID 的规范 uuid 文本（框架列，文本类型） | `V20261014130000__platform_identity_baseline.sql` 列注释 |
| 日志字段 `userId`、`correlationId` | 规范 uuid 文本；日志只写 ID，不写姓名 | `UserLogFilter`、`CorrelationIdFilter` |
| 前端测试桩 | 用户、部门、员工 ID 用 uuid 形态；`sync-runs` 页的 `subjectKey: '1001'` 属同步差异，随拆分 a 再改 | `frontend` 各测试文件 |
| 随机值 | 除 `kernel.Uuids` 外不调用 `UUID.randomUUID`；仅 `OperationsCommands` 的命令关联号是登记的例外，到期 OPS-CID | `IdMakingGuardTest`、`architecture/random-uuid-exceptions.txt` |
| 仓储生成聚合根 ID | 仅 `JdbcSyncRunRepository.insertRunning` 是登记的例外，到期拆分 a / b | `IdMakingGuardTest`、`architecture/repository-id-exceptions.txt` |
