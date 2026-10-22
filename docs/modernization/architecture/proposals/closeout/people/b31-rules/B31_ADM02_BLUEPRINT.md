# ADM-02 蓝图：AGG-116 员工活动申请（施工设计 7.8）只读调研

基线：分支 claude/one-1a-admin-module（HEAD 1748c914e，已含 origin/main 1f3674280）。调研时未改任何文件、未运行 mvn、未起容器。
读过：CLAUDE.md、CODING_STANDARD（CS-2.24、2.26、2.27 等）、NAMING_STANDARD §2.3–2.6/§3、SESSION_PLAYBOOK §12.2/§14/§14.1、B31_CONSTRUCTION_DESIGN §3.6/§5.3/§7.8/§10/§11、B31_CONTRACT、B31_PLATFORM_CONSTANTS、B31_ARCH_RULINGS 第 11 项、B31_PHYSICAL_MODEL_RULINGS（origin/claude/one-data-handoff-1 版，与工作树版逐字相同，且已在 main）、ADR-0075（origin/claude/one-architecture-adr0075 版；PR #552 仍 OPEN，文中写「已采纳」）、ADR-0066 决定 4、STATE_TRANSITIONS_DESIGN。

---

## 0. 先说结论与四个要紧的发现

1. 7.8 是行政事务里依赖最少的一张（无审批、无文件、无签章），适合当「铺路卡」：它要带出本模块第一份 schema 基线、state_transitions、配置集。所以不宜把 7.8 当单卡做，建议拆 5 张（第 6 节）。
2. 仓库里没有名叫「工作日历版本」的 K-2 版本表。`organization_attendance` 只有 `work_calendar_years` / `work_calendar_days`（无 version_number、无确认列）。真正「根表 + 版本头 + 作者/确认人 + 四眼触发器」形状的只有两处：`platform_file.preservation_categories` / `preservation_category_versions`（只有 DDL，没有 Java）和 `platform_numbering.numbering_rules` / `numbering_rule_versions`（DDL + Java 都有）。配置集 DDL 应抄 platform_file 的写法（one-data 裁定第 2 节也点名「形状同 #521 的保存类别版本」），Java 生命周期抄 numbering。
3. 周年自动生成目前**没有可调用的数据**：`organization.workforce.api` 没有入职日期，`organization_workforce` 的表里也没有入职日期列（employees 表无 hire 类列；W2 的 `employee_versions` 只在设计文档里，未建）。裁定第 11 项说「没有这个查询方法的，按兼容新增补」，但缺的不止方法，是数据。周年那一半（7.9）被 W2/人事侧卡阻塞。
4. 两处「契约与代码对不上」会在实现时撞墙：
   - B31_CONTRACT §4.2 与 B31_PLATFORM_CONSTANTS §4 写「不新增 AuditSubjectKind 常量」，但注册表改造 REG-3 没做：`AuditSubjectKind` 仍是枚举，`EventCatalogueTest` 还核对「聚合名必须有 AuditSubjectKind，且枚举与 `ck_audit_entries_subject_kind` CHECK 相等」。不加 `EMPLOYEE_ACTIVITY_REQUEST`（枚举 + platform_audit 基线 CHECK），AGG-116 的事件和审计都写不进去。先例：platform.file 卡自己改了这两处（提交 024a017ef 加 STORED_FILE）。
   - 设计 §2.3 说「活动取消仅记内部状态与审计，不发事件」。但本仓库的审计主路径是「聚合事件 → `AuditDomainEvents` 监听 → 审计条目」。不发事件就没有自动审计，要么直接调 `AuditLog.record(new AuditEntry(...))`（须登记 `audit-text-writers.txt`、`MUST_AUDIT_ACTIONS.yaml`），要么发事件。需 one-architecture 定（第 7 节 G-1）。

---

## 1. 先例对照（路径以「最小可抄」为准；路径均相对 repo 根）

| 项 | 最小可抄先例 | 本卡该怎么用 |
|---|---|---|
| 配置版本表（根 + 版本头 + 四眼） | DDL：`backend/src/main/resources/db/migration/platform_file/V20261021120000__platform_file_baseline.sql` 第 338–436 行（`preservation_categories`、`preservation_category_versions`、`guard_*_insert/change` 触发器、`uk_*_pending` 部分唯一索引）。Java 生命周期：`backend/src/main/java/com/forcitis/one/platform/numbering/domain/NumberingRule.java`、`TemplateVersion.java`、`VersionStatus.java`，应用层 `ConfigureNumberingRule` / `ConfirmNumberingRule`，仓储 `infrastructure/persistence/JdbcNumberingRuleRepository.java`，HTTP `web/NumberingRuleController.java`。 | DDL 照 platform_file 第 5 节改成 one-data 裁定第 2 节的形状（多了 `config_set_code` 复合外键、生效区间排他、`effective_to_date`、`decision_audit_entry_id`）；Java 照 numbering 的「PENDING 插入 → 他人确认 → 不可改」。注意 `organization_attendance` 的工作日历表不是版本表，别抄。 |
| state_transitions 落库 | DDL 模板：platform_file 基线第 0 节（第 6–86 行，含 `refuse_transition_rewrite` 触发器、注释）；模板说明 `docs/modernization/data/STATE_TRANSITIONS_DESIGN.md`。写入 Java：`platform/file/infrastructure/persistence/JdbcStoredFileRepository.java` 第 70 行起（内联 INSERT，`WorkContext.correlationId()`、`SystemActors.idOf`）。登记：`data-access-registry.yaml` 第 507–513 行；`unversioned-table-writes.txt` 第 34 行；`system-actors.txt` 里的 `platform.file.*`。 | 全仓只有这一份实现，且是内联 SQL。kernel 里没有 `StateTransition` 值对象和标记接口（`STATE_TRANSITIONS_DESIGN` §8 第 0 步「kernel」与第 1 步「模板与守护公共卡」都没做；`cost_timesheet` 也没有该表）。所以本卡要么抄 JdbcStoredFileRepository 的内联写法，要么先要求公共卡。建议按 §6 卡 A 里「抄内联写法、转换落表点收口到一个 `JdbcStateTransitions` 类」做，并写进卡的备注。 |
| 乐观锁 version | `kernel/AggregateRoot.java`（version 与 eventSequence 两个计数）、`kernel/ExpectedVersion.java`；`cost/timesheet/infrastructure/persistence/JdbcTimesheetRepository.java` 第 72–105 行（`WHERE id=:id AND version=:version AND status=:previous`）；HTTP：`organization/attendance/web/WorkCalendarController.java`（`If-Match` + `eTag`）。 | AGG-116 根表要 `version` 和 `event_count` 两列（设计 §3.6 的列草案漏了 `event_count`，`EventSequenceCoverageTest` 与时间表先例都有）。命令一律带 If-Match，428/412 由 kernel 异常转换。 |
| 编号 DocumentNumbers | `cost/timesheet/application/SubmitTimesheet.java`（`numbers.next(TimesheetDocument.TYPE, legalEntity, today)`，`IssuedNumber` 里取回 `counterId`、`ruleVersionId`），类型声明 `cost/timesheet/api/TimesheetDocument.java`；表列 `timesheet_number`、`numbering_rule_version_id`。多聚合例外登记：`backend/architecture/multi-aggregate-exceptions.txt` 第 6 行（Timesheet+NumberingRule）。 | 照抄：`EmployeeActivityRequestDocument.TYPE`；根表存编号和所用规则版本 ID；事件 `relatedAggregates` 列出 NumberingCounter；`SubmitEmployeeActivityRequest` 要在 `multi-aggregate-exceptions.txt` 加一行，否则 `SingleAggregateGuardTest` 失败。 |
| 审计 audit api | 路径 1（事件，主路径）：`platform/audit/application/AuditDomainEvents.java`，聚合事件落库即自动审计；`AuditSubjectKind.ofAggregateType`。路径 2（直接写）：`platform/audit/api/AuditLog.java` + `AuditEntry`；写入点登记 `backend/architecture/audit-text-writers.txt`（`AuditWritePointsGuardTest`）；动作清单 `docs/modernization/audit/MUST_AUDIT_ACTIONS.yaml`（`MustAuditActionsTest`）。事件文本类：`backend/architecture/audit-detail-implementations.txt`。 | AGG-116 事件走路径 1；`AuditEntry` 不得带个人信息，取消原因放 `changeReason`（POSSIBLE 级，先例 `RecordSyncRunRelease`）。 |
| `@Scheduled` + `@SchedulerLock` 自声明作业 | 最薄：`organization/workforce/infrastructure/scheduling/ShortNameRefreshJob.java`（5 行调一个用例）；带指标：`platform/numbering/infrastructure/scheduling/CounterCapacityJob.java`；一天一次且受总开关控制：`platform/identity/infrastructure/scheduling/EmergencyAdminCheckJob.java`。锁名即系统操作者名，必须登记 `backend/architecture/system-actors.txt`（`SystemActorCatalogueTest`，`UnitOfWorkAspect` 在 @Scheduled 外包一层 `WorkContext.enter`）。配置键：`config/<schema>.properties` + `application.properties` 的 `spring.config.import`。 | 作业类放 `organization/administration/infrastructure/scheduling/AnniversaryCardJob.java`；锁名 `organization.administration.anniversary.card.generate`（G46：`{subdomain}.{bc}.{name}`）；`@ConditionalOnProperty` 两个开关（`forcitis.platform.scheduling.jobs-enabled` 全局 + 本作业 enabled）；cron 键 `forcitis.organization.administration.anniversary.cron`。 |
| `@ApplicationModuleListener` | `cost/timesheet/application/ApplyApprovalResult.java`（`@SystemActorName`、`consumed.firstTime(eventId, CONSUMER)` 幂等、`ConsumedEvents` 表）。 | **7.8 不需要**（无审批回调、无事件监听）；7.5 起才用。写在这里是为了其他卡。 |
| web 控制器 + OpenAPI + If-Match / Idempotency-Key | 控制器：`organization/workforce/web/LegalEntityController.java`（POST 创建返回 201 + Location + ETag；PUT 带 If-Match）、`LegalEntityAttributionController.java`（`:end`、`:correct` 命令，`UserId caller` 参数）。OpenAPI 导出：`backend/src/test/java/com/forcitis/one/OpenApiExportTest.java` 写 `frontend/packages/api/openapi.json`。Idempotency-Key 白名单：`backend/architecture/idempotent-endpoints.txt`（`IdempotentEndpointGuardTest`：应答不得含个人直接标识、SENSITIVE/RESTRICTED 列）。 | 路径按 §2.3 `/api/v1/organization/employee-activity-requests`，命令 `:conclude`、`:cancel`。`OpenApiExportTest.OWN_ID_FIELDS` 是硬编码的「自有 ID 字段」清单，新增响应里的 ID 字段（如 `EmployeeActivityRequestResponse.requestId`）要加进去，否则它报错。是否把提交 POST 加进 `idempotent-endpoints.txt`：应答含活动说明文本，需 one-data 判断是否算个人信息；HD-05「重复提交返回同一结果」建议靠业务键幂等（见 §6 卡 C），不依赖 HTTP 头。 |
| Permission 枚举与权限目录 | `platform/identity/api/Permission.java`（枚举 + `Codes` 接口，G43）；各模块 web 包里有一份字符串拷贝：`organization/attendance/web/AttendancePermissions.java`、`organization/workforce/web/WorkforcePermissions.java`（`NamingSurfaceTest` G43 核对拷贝与枚举一致，因为这些模块不依赖 identity）；角色授权在 `platform_identity` 基线（`role_permissions` 种子，第 540–584 行；`employee` 角色只有审批任务两个码）；敏感权限清单 `platform/identity/domain/SensitivePermissions.java`；目录 `docs/modernization/architecture/ssot/07-org-roles/permissions.yaml` 由生成器写（引用 Permission.java 行号），不手改。 | admin 没有 identity 边，照 attendance 建 `AdministrationPermissions`。码：`organization.administration:employee-activity-request:read|create`、`employee-activity-completion:execute`（资源名里的 completion 对应已办结，见 G-9）、`employee-activity-cancellation:execute`、`employee-activity-type:read|create|approve`（approve＝定版，建议进 SensitivePermissions 评估，需 one-security）。这些要改 identity 模块的 Permission.java 和基线，须告知 identity owner。 |
| 事件目录 | `docs/modernization/events/*.yaml`（每模块一个分片，`x-event` 含 eventType、class、aggregate、publisher、consumers、businessDate、audit、publisherTest、noConsumer、relatedAggregates）；`docs/modernization/events/build_asyncapi.py` 合成 `asyncapi.yaml`（`EventCatalogueTest` 要二者一致）；先例 `events/timesheet.yaml`、`events/attendance.yaml`。 | 新建 `events/administration.yaml`，一个事件 `EmployeeActivityRequestApproved`（businessDate 取活动的业务日期，`EventBusinessDateGuardTest`）。 |
| 数据访问注册表 | `backend/architecture/data-access-registry.yaml`（表头注释列全字段；`cost_timesheet.timesheets` 第 26–34 行是单据先例；`platform_file.preservation_categories(_versions)` 第 464–480 行是配置版本先例；`platform_file.state_transitions` 第 507–513 行）。 | 每张新表一条；`DataArchitectureTest` 双向核对（需容器）。 |

---

## 2. 新增「有表的业务模块」要改的文件与登记处（按顺序）

参考提交：platform.file 建表 `a608eaa2d`（21 个文件）、行政模块骨架 `d2f0b69d2`+`a5bf0e603`（骨架已在本分支，见下面「已就绪」）。

已就绪（本分支已有，不用再做）：`docs/modernization/architecture/ddd/contexts.yaml` 的 `organization.administration`（status: built）、`ddd/context-map.yaml` 的边、`backend/architecture/module-dependencies.txt` 第 11 行（edges：workforce、approval、audit、file、numbering）、`organization/administration/{package-info,api/package-info,events/package-info}.java`、提议术语 `backend/glossary/proposed/organization-administration.yaml`。注意：没有 identity 边、没有 integration 边，周年通知要加边（§4）。

按顺序要做：

1. 术语：`backend/glossary/proposed/organization-administration.yaml` 补缺（见 G-9、G-10 的名字问题），改后跑 `python3 docs/modernization/build_all.py` 更新 `backend/glossary/proposed/README.md`。`GlossaryTest` 对 proposed 词只告警；但 B31_CONTRACT §1 写「提议术语批准前不落代码」，以此为准，需 one-glossary 转正。
2. 数据设计签发：one-data 对 DDL 草案出「数据设计：通过」，记录进 `docs/modernization/data/reviews/`（目前该目录没有任何 ADM 条目）。
3. 迁移：`backend/src/main/resources/db/migration/organization_administration/V<14位时间戳>__organization_administration_baseline.sql`（文件名 G44；每张表中文 COMMENT、约束与索引按 §2.4 命名；时间列无 `now()` 默认）。基线开放期的写法见 §6「基线文件冲突」。
4. `backend/src/main/resources/application.properties` 第 15 行 `spring.flyway.schemas` 追加 `organization_administration`；如有作业配置，新建 `backend/src/main/resources/config/organization_administration.properties` 并加入第 21 行 `spring.config.import`；`application-identity-operations.properties`、`application-integration-operations.properties`、`application-read-only-verification.properties` 里按需关作业开关。
5. 测试库 schema：`backend/src/test/resources/init-dba-schemas.sql` 加 `CREATE SCHEMA organization_administration;`（`init-schemas.sql` 只建 platform_approval，不用改）。
6. `backend/src/test/java/com/forcitis/one/DataArchitectureTest.java` 第 635 行 `SCHEMAS` 加 schema 名（`TABLE_USE` 正则靠它识别 Java 里的表）。
7. 基础设施脚本（`scripts/ci/db_scripts_aligned.py` 离线核对三处一致，不需容器）：
   - `docs/modernization/phase1a/infra/one_db_roles_schemas.sql`：schema 清单检查、`CREATE SCHEMA ... AUTHORIZATION one_dev_owner`、授权循环、`REVOKE CREATE`、§4b 的只追加表 `REVOKE UPDATE, DELETE, TRUNCATE`、§5 回滚清单；
   - `docs/modernization/phase1a/infra/runbooks/sql/c3_dev_db.sql`、`c3_dev_db_after_migrations.sql`、`c3_dev_db_verify.sql`；
   - `.../c9_prod_db.sql`、`c9_prod_db_after_migrations.sql`、`c9_prod_db_verify.sql`；
   - `c3_dev_db_rollback.sql`、`c9_prod_db_rollback.sql` 在 platform_file 提交里没改（grep 无 platform_file），是否漏改我没有证据，写成「核对」项。
8. `backend/architecture/data-access-registry.yaml`：每表一条（role、aggregate、mutability、retention、lineage、writers；单据表加 `business_date_column`、`document_number_column`；配置表 `CONFIG_VERSIONED` + `versions_table`；state_transitions `LOG_APPEND_ONLY`）。
9. `backend/architecture/data-classification.txt`：人填文本列（活动说明、取消原因）、预估金额、参与人 ID 的分级（先例：`platform_file.file_holds.hold_reason | INTERNAL | …个人信息 PI`）。
10. `backend/architecture/system-actors.txt`：每个作业锁名、每个 `@SystemActorName` 监听器、`state_transitions` 的系统操作者各一行，`UUIDv5` 由命名空间 `0d4f6c1e-7b2c-4c39-9e5d-3f1b7c2e6d90` 计算，名字以所属模块开头（`SystemActorCatalogueTest`；`scripts/ci/system_actors_append_only.py` 保证只追加）。
11. `backend/architecture/unversioned-table-writes.txt`（无 `version` 列的子表/只追加表写入）、`repository-write-exceptions.txt`、`multi-aggregate-exceptions.txt`（取号的提交用例）、`idempotent-endpoints.txt`（如要 Idempotency-Key）、`audit-text-writers.txt`（如直接写审计）。
12. `backend/architecture/` 其余：`list-paging-gaps.txt`（列表接口须有游标分页，否则要登记缺口）。
13. 其它模块的联动（跨 owner，须在卡里点名）：
    - `platform/identity/api/Permission.java`（枚举 + Codes）与 `platform_identity` 基线的 `role_permissions` 种子、`navigation_items`；
    - `platform/audit/api/AuditSubjectKind.java` + `platform_audit` 基线第 12 行 `ck_audit_entries_subject_kind`（二者必须相等）；
    - `platform_numbering` 基线（单据类型 + 规则 + 版本 1 种子，见 §3）；
    - `docs/modernization/audit/MUST_AUDIT_ACTIONS.yaml`（每个被审计事件类型）。
14. 文档登记：`docs/modernization/architecture/ddd/aggregates.yaml`（目前没有任何 admin 聚合，AGG-116 与配置集各一条；`ssot/i03-aggregates/built.yaml` 由生成器写）、`docs/modernization/events/administration.yaml` 并运行 `python3 docs/modernization/events/build_asyncapi.py`、`docs/modernization/architecture/ddd/context-map.yaml`（如加 integration 边）。
15. 代码：`domain`（聚合、状态机用 `kernel.StateMachine`）、`application`（`@Transactional` 用例）、`infrastructure/persistence`（Jdbc 仓储）、`web`（控制器、`*Request`/`*Response`）、`api`（错误码枚举 `AdministrationError`、类型码 `EmployeeActivityRequestDocument`）、`events`。
16. 接口契约：`frontend/packages/api/openapi.json` 由 `OpenApiExportTest` 重写后提交（CS-1.10）；该测试里的 `OWN_ID_FIELDS` 同步；`OpenApiExportTest` 有 `@MockitoBean`（OrgDirectory、AuditLog、RoleDirectory、WecomGateway），新模块若注入了别的跨模块 api bean，可能要补 mock。
17. 验收测试：`backend/src/test/java/com/forcitis/one/acceptance/organization/administration/`，由独立测试方先写，提交带 `Acceptance-Test-Author: test-engineer` trailer（`scripts/ci/acceptance_guard.py`），实现卡不改该目录。
18. 任务片段：`docs/modernization/tasks/<任务号>.md`（`new_*`/`check_tasks.py`）、记录片段按 CLAUDE.md 第 12 条。

哪些检查要容器（我没有运行任何一项）：
- 需要 Testcontainers：`DataArchitectureTest`（注册表对数据库、truncate 拒绝）、`OpenApiExportTest`、`ApplicationIntegrationTest`、`SchemaPlacementIT`、`ListPagingGuardTest`、`RoleSeparatedDeploymentIT`、所有 `acceptance/**/*IT`、仓储 IT（先例 `JdbcStoredFileRepositoryIT`、`FileServiceSchemaIT`）；`pg_dump --schema-only` 的「预期表结构 diff」（SESSION_PLAYBOOK §12.2）要两个空库；`scripts/ci/baseline_ddl_scan.py` / `measurable_ddl_scan.py` 我未核对是否需要库。
- 不需要容器（离线）：`DdlNamingTest`、`GlossaryTest`、`ArchitectureRulesTest`、`ModularityTest`、`ModuleDependencyTest`、`NamingSurfaceTest`（G43、G44、G45、G46、G47）、`SystemActorCatalogueTest`、`EventCatalogueTest`/`EventCataloguePolicyTest`、`MustAuditActionsTest`、`AuditWritePointsGuardTest`、`AuditDetailGuardTest`、`SingleAggregateGuardTest`、`RepositoryWriteGuardTest`、`StateWriteGuardTest`、`IdempotentEndpointGuardTest`、`EventSequenceCoverageTest`、`TimeSourceGuardTest`、`DocumentTypeRegistryGuardTest`、`SensitiveColumnExposureGuardTest`；脚本 `scripts/ci/db_scripts_aligned.py`、`migrations_immutable.py`、`build_all.py --check`、`scripts/precheck.sh`。

---

## 3. 单据类型注册（ADR-0066 决定 4）

事实：
- 决定 4 第 6 项判据：「新增一类单据，只需在注册表插一行，再在声明它的 BC 改代码；平台模块不改代码」。
- 登记表是 `platform_numbering.document_types`（`V20261014100100__platform_numbering_baseline.sql` 第 5–55 行），「只由迁移增删，运行时只读」（注册表 `MIGRATION_ONLY`；`REVOKE INSERT, UPDATE, DELETE, TRUNCATE ... FROM one_dev_app` 在 `one_db_roles_schemas.sql` §4b）。种子在同一文件第 236–251 行：三行类型（QUOTE/TIMESHEET/JOURNAL_VOUCHER，含 `number_code`、`owner_bc_code`）、三行 `numbering_rules`、三行 `numbering_rule_versions`（模板 `{TYPE}-{LE}-{yyyyMM}-{seq:4}`，状态 `PENDING_CONFIRMATION`，`source_kind='SEEDED'`）。
- 守护 `NamingSurfaceTest.numberingDocumentTypesAreUpperSnakeGlossaryWordsOfARegisteredBcAndEachHasExactlyTheSeededRule`（G45）：扫**所有**迁移文件里对 `platform_numbering.document_types` 的 INSERT；类型码 UPPER_SNAKE 且各词在词表里；`owner_bc_code` 必须是 `contexts.yaml` 里 kind 为 bc 的 id（行政事务已是 built，通过）；每个类型必须同时种一行 `numbering_rules`（失败信息写明「add one in a new platform_numbering migration」）。
- TIMESHEET 先例：提交 `a5d4b26b9`（REG-1，任务 IMP-1021-NX5-01，会话 one-1a-next-5）由**编号模块的建造卡**把类型种进编号基线；`cost.timesheet` 只在自己的 `api/TimesheetDocument.java` 声明 `new DocumentTypeCode("TIMESHEET")`。cost.timesheet 自己不带任何 platform_numbering 迁移。审批侧（platform.approval）的 TIMESHEET 先例是 `ProcessKey` 里带单据类型（REG-2 未做完，`DocumentTypeRegistryGuardTest.REG_2_PENDING`）。

对 EMPLOYEE_ACTIVITY_REQUEST 要做的：
1. 业务模块（本卡）：`organization/administration/api/EmployeeActivityRequestDocument.java` 声明类型码；根表存编号、规则版本 ID。
2. 编号模块侧（单独小卡，owner＝platform.numbering 的建造会话；`contexts.yaml` 里没有登记 numbering 的 owner，只登记了 platform.file 和签章服务，**证据缺口**，需 one-coordinator 指派）：在 `platform_numbering` 迁移（基线开放期改基线，`migration-baseline-open` 存在；之后追加 V 文件）里加三行种子：`document_types`（code、`number_code` 2–4 位大写且全局唯一——现有 QT/TS/JV，缩写取什么由 numbering/glossary 定，我不猜）、`numbering_rules`、`numbering_rule_versions` 版本 1（PENDING_CONFIRMATION）；更新测试辅助 `src/test/java/.../platform/numbering/domain/SeededDocumentTypes.java`；`DocumentTypeRegistryIT` 第 68 行把三个种子类型硬编码了，可能需要跟改。
3. 规则版本四眼确认：种子版本是 PENDING，由 `business_admin` 角色经 `POST /api/v1/platform/numbering-rules/{documentType}:confirm` 确认（确认人≠作者）；**未确认的规则不发号**，所以未确认时 AGG-116 的提交会被编号模块拒绝。把「编号规则版本已确认」写进上线出口条件，并在验收测试里覆盖「未确认则拒绝，且活动行没有落库」。
4. 业务模块自己能不能带这条迁移：技术上 G45 允许任何迁移目录 INSERT 进该表，但「只由迁移增删」的所有权在编号模块，且运行角色无权限；我的建议是不让业务模块带，由编号 owner 一并做，业务卡只依赖其合入。
5. 审批侧：AGG-116 不走审批（无 ProcessKey），所以 `DocumentTypes` 校验只发生在编号时。

---

## 4. 周年自动生成需要的 workforce api

现有方法（`backend/src/main/java/com/forcitis/one/organization/workforce/api/`）：
- `OrgDirectory`：`findEmployee(EmployeeId)`、`findEmployeeByLogin(LoginLookup)`、`employmentOf(EmployeeId)`（返回 `EmploymentVerdict`：EMPLOYED/SEPARATED/NOT_LISTED/SNAPSHOT_UNAVAILABLE）、`findSupervisor`、`findOrgUnitHead`、`findRegionalReceivablesManager`、`findOrgUnit`、`findAncestors`。
- `EmployeeProfile(employeeId, name, orgUnitId, wecomUserId, employmentStatus, feeEarner)`；`EmploymentStatus`＝EMPLOYED/SEPARATED。
- `EmployeeLegalEntities.on(EmployeeId, LocalDate)`：员工在某日的法人（取不到抛 `ORGANIZATION_WORKFORCE_EMPLOYEE_LEGAL_ENTITY_UNRESOLVED` 422，不默认）。
- `EmployeeExistence.exists(EmployeeId)`、`LegalEntityDirectory`、`LoginLookup`。
- 事件（`events/`）：只有 `EmployeeRegistered(envelope, employeeId)`（首次登录登记）等同步类事件，**没有离职事件、没有入职事件**。

缺什么：
1. 入职日期（本次入职日，重新入职从本次重算）：无方法；`organization_workforce` 基线表（positions、org_units、employees、assignments、sync_runs、sync_diffs、legal_entities、*_versions 等）里无入职日期列；`employees` 无雇用阶段、无停薪留职标记；词表里 `hire_date` 未登记（`B31_TERM_GAPS` 第 106 行；`organization-administration.yaml` 第 670–678 行说明该词属 workforce 且未登记）。
2. 「某业务日满周年的在职员工」查询：无。W2 设计 `docs/modernization/data/W2_EMPLOYEE_POSITION_VERSIONING_DESIGN.md` 规划了 `employee_versions`、`employment_phase`（PROBATION/REGULAR）、`is_on_unpaid_leave`，但未建、本仓库无对应任务卡文件（`docs/modernization/tasks/` 中未检索到）。
3. 「正式员工」（ZN-01）：没有任何现成判断；若「正式」指转正后，需要 `employment_phase`；若含试用期则不需要。**证据缺口**，需 one-rules 裁（G-3）。
4. 停薪留职照发（ZN-06）、离职不发：用「在业务日仍在职」即可，停薪留职状态不影响；不需新字段，但取在职状态要按「生效日≤今天」判断（ADR-0068 用户原话）而不是快照。
5. 上述每日扫描的量：需要由 workforce 侧按「月日」过滤后返回（含闰日规则：当天是 02-28 且当年为平年，要把 02-29 入职的人一并查出），避免每天拉全员；返回量有上限（CS/D19）。

建议的 api 形状（仅提议，不是已有事实）：`organization.workforce.api` 增一个窄接口，例如
`CurrentEmploymentStarts`：`Optional<LocalDate> currentStartOf(EmployeeId)`，与 `List<EmployeeId> employedWithStartOnMonthDay(MonthDay… , LocalDate asOf, 游标)`。周年日期的计算（`AnniversaryDate` 值对象，闰日→02-28）在 admin 侧（词条已在 admin，kind value_object）。方法命名交 one-glossary。

结论：缺的是人事数据与 api，属 W2/人事侧卡；周年作业（我的卡 D）被它阻塞；7.8 的手工登记不受影响。

另：ZN-06「发送本人、通知行政」需要通知投递：现有 `DeliveryPurpose` 只有 6 个审批用途、`DeliverySubjectKind` 只有 APPROVAL_TASK/APPROVAL_INSTANCE（`platform/integration/api/`），admin 没有 integration 边；`B31_PLATFORM_CONSTANTS` §3 已列 `ANNIVERSARY_CARD_GENERATED` / `EMPLOYEE_ACTIVITY_REQUEST` 候选，接收人（员工关系负责人 vs 行政，M20）还没定。通知应从周年生成卡里拆出（卡 E）。

---

## 5. 配置集（config_sets / config_set_versions）：同卡还是前置卡

one-data 裁定第 2 节的形状（已核）：
- `config_sets` 根（`config_set_code` 五个取值：SEAL_APPROVERS、IT_SERVICE_CATEGORIES、POSITION_ASSET_STANDARDS、EMPLOYEE_ACTIVITY_TYPES、REMINDER_RULES；`owner_role_code`；乐观锁），**由迁移种子写入**，每个配置集一行。
- `config_set_versions` 版本头：`version_number`、生效区间（`effective_to_date` 只能关闭一次）、`confirmation_status`、作者/确认人、`decision_ref_key`、`decision_audit_entry_id`；四眼 CHECK、待确认至多一个、`BEFORE INSERT` 只许 PENDING、确认触发器只改确认列、已确认区间排他、`UNIQUE (id, config_set_code)`。
- 五张条目表各配一类，条目表「只在版本待确认时可插入，确认后拒绝」（追加表触发器），`(config_set_version_id, config_set_code)` 复合外键 + `config_set_code` 具名 CHECK 固定。
- 单据存 `config_set_version_id`（同 schema 具名外键）。

判断：共享根表被五个配置共用，但五个条目表各被不同卡消费（7.4 用印审批人表、7.5 IT 类别表、7.6 岗位资产标准、7.8 活动类型、7.9 提醒规则）。因此：
**建议拆成一张前置卡（卡 A），把共享部分——schema 基线、config_sets（五行种子）、config_set_versions 与四眼触发器、版本生命周期代码与「按业务日期取已确认版本」查询、state_transitions——做完；活动类型条目表 `employee_activity_type_items` 随卡 A 一并建（它是最小的验证用例，且卡 C 立刻要用）；其他四个条目表留给各自消费卡增量加。**

两种分法涉及的文件：
- 与 7.8 同卡：一张超大卡，包含 §2 的第 1–14 项加 AGG-116 的全部代码；另四张条目表也不得不在后面的卡里「改基线」（基线开放期直接改基线文件，需 `pg_dump` 预期 diff 与 one-data 复签），多卡争用同一份基线文件，冲突风险高；而且 one-data 对共享根的签发与对 AGG-116 的签发被捆在一起，互相拖。
- 单独前置卡：卡 A 文件＝基线迁移（config_sets、config_set_versions、employee_activity_type_items、state_transitions）、`domain/ConfigSet*.java`、`application/{DraftConfigSetVersion,ConfirmConfigSetVersion,...}`、`infrastructure/persistence/JdbcConfigSetRepository.java`、小型 web（活动类型维护与定版，权限 `employee-activity-type:*`）、注册表/分级/系统操作者/脚本登记、`Permission.java`+identity 基线、`events/administration.yaml` 里的版本确认事件（若发）、aggregates.yaml、术语（`config_set`/`config_set_version` 现登记为 entity，代码要有聚合类须改为 aggregate，G40，需 one-glossary）。卡 C 依赖卡 A 合入后读取已确认版本。

实施时的未决点（影响卡 A 的 DDL，必须在 one-data 签发时写清）：
- `config_set_versions` 有 PENDING→CONFIRMED 状态。platform_file 先例把它登记为 `aggregate_type_code='PreservationCategoryVersion'` 并写 state_transitions，但该版本表没有 `version` 列；ADR-0075 决定 2 要求「适用的子表行必须各自带 version 列」。所以要裁：配置版本是否写转换、是否加 `version` 列。ADR-0075 在本分支文本里写「已采纳」，但 PR #552 仍 OPEN，合入前不当作已生效（G-6）。
- 裁定第 2 节列清单里没有 `version` 与 `created_by`/`updated_by`；设计 §3.1 写了审计列，`cost_timesheet.timesheets` 也没有 `_by` 列。以 one-data 签发为准。

---

## 6. 7.8 拆分建议（独立交付小卡）

约定：任务号占位 `IMP-1022-ADM-0x`（仅占位，不是已登记编号）；每卡的验收测试由独立测试方先写，放 `acceptance/organization/administration/`，只经 HTTP、模块 api、或本模块 schema（`AcceptanceSupport` 约定）；时间用固定时钟。

### 卡 N：编号单据类型登记（platform.numbering owner）
- 范围：`platform_numbering` 基线种子（类型 + 规则 + 版本 1 PENDING）、`SeededDocumentTypes.java`、`DocumentTypeRegistryIT`（若硬编码）、术语。
- 依赖：无。是否需 one-data 签表：是（种子行，轻）。是否被 ADR-0075 阻塞：否。
- 验收场景：
  - Given 登记完成 When 查 `DocumentTypes` Then `EMPLOYEE_ACTIVITY_REQUEST` 已登记且属主为 organization.administration（G45）。
  - Given 版本 1 为 PENDING When 取号 Then 拒绝（未确认的规则不发号）。
  - Given 作者本人 When 确认版本 1 Then 拒绝（四眼）；Given 另一名 business_admin 确认 Then 取号得到 `{TYPE}-…` 格式编号。

### 卡 S：审计主体类别（platform.audit owner，或并入卡 C，须 audit owner 同意）
- 范围：`AuditSubjectKind.EMPLOYEE_ACTIVITY_REQUEST` + `platform_audit` 基线 CHECK + `EventCatalogueTest` 相等性。先例 `024a017ef`。依赖无；不被 ADR-0075 阻塞。
- 验收：Given 新类别 When 发布 `EmployeeActivityRequest` 聚合的事件 Then `AuditDomainEvents` 写入的条目 `subject_kind='EMPLOYEE_ACTIVITY_REQUEST'` 且 `subject_id` 为聚合 ID。

### 卡 A：行政事务 schema 基线 + 配置集 + 活动类型
- 文件范围：见第 5 节；§2 的 1–14 项里与配置、state_transitions、schema 相关部分。
- 依赖：one-data 签发（需要，含 state_transitions 与五行 config_sets 种子）；一般不需等 ADR-0075，除非 G-6 裁定要记转换。外部：identity owner 同意加权限码；one-glossary 把 config_set 定为聚合。
- 验收场景（Given/When/Then 一行一个）：
  - Given 空库迁移后 When 查 `config_sets` Then 有且仅有五个已知 `config_set_code`，`EMPLOYEE_ACTIVITY_TYPES` 的 owner 为员工关系负责人角色（B31 裁定 §2）。
  - Given 员工关系负责人起草活动类型版本 When 保存 Then 版本为 PENDING、`author_id` 为本人、条目可插入（Q6/HD-01 前提）。
  - Given 版本 PENDING 且作者本人 When 确认 Then 拒绝（确认人≠作者，四眼）。
  - Given 另一名持 `employee-activity-type:approve` 的人 When 确认 Then 版本变 CONFIRMED，确认人/时间落库，条目不可再插入、不可改。
  - Given 无该权限的人 When 确认 Then 403。
  - Given 同一配置集已有一个 PENDING 版本 When 再起草 Then 拒绝（至多一个待确认）。
  - Given 版本号>1 的版本 When 确认时生效日早于确认日 Then 拒绝。
  - Given 两个已确认版本 When 生效区间重叠 Then 拒绝（排他约束）。
  - Given 业务日 D 落在已确认版本区间内 When 按业务日取版本 Then 返回该版本；Given D 早于最早版本或没有已确认版本 When 取 Then 失败并告警，不回退、不默认（CLAUDE.md 第 6 条，契约 §3 第 12 项「启用前提」）。
  - Given 条目 `activity_type_code` 重复 When 同版本插入 Then 拒绝（唯一键）。
  - Given 版本状态变更 When 成功 Then 同事务写一条 `state_transitions`，序号/原状态/新状态按模板（G-6 裁定后定）；事务回滚则无转换行（需容器）。
  - Given 直接对 `state_transitions` UPDATE/DELETE/TRUNCATE When 以运行角色 Then 被拒（需容器，D8）。
  - Given 取最新版本的并发确认 When 两人同时确认同一版本 Then 其一因 If-Match/前置状态失败（乐观锁）。

### 卡 C：AGG-116 员工活动申请生命周期（本体）
- 文件范围：`employee_activity_requests`、`employee_activity_participants` 两表（加入基线）、`EmployeeActivityRequest` 聚合 + `StateMachine`（草稿→已批准→已办结；已批准/已办结→已取消）、用例（提交/办结/取消/重登）、仓储、web、`EmployeeActivityRequestApproved` 事件、权限、注册表、`multi-aggregate-exceptions.txt`、`events/administration.yaml`、aggregates.yaml、OpenAPI。
- 依赖：卡 A（读取已确认活动类型版本）、卡 N（取号）、卡 S（审计类别）；one-data 签 DDL（预估金额列：裁定 §7 写 `estimated_amount numeric(19,4)` + `estimated_currency_code`；注意 `Money` 内核是 2 位小数 HALF_UP，列精度与 `DdlNamingTest` 的 `_amount`↔`_currency` 配对规则如何对齐需在签发时确认，这是仓库首批金额列）；一般不被 ADR-0075 阻塞（AGG-116 只有根有状态机，参与人表无状态机）；B31_CONTRACT §6 只点名 AGG-113、AGG-115 受 ADR-0075 约束。
- 验收场景：
  - Given 已确认的活动类型版本含「节日活动」、在职员工、预估金额与币种、至少一名参与人 When 提交 Then 创建活动、直接为已批准（无审批实例）、取得 `EMPLOYEE_ACTIVITY_REQUEST` 编号、记录所用类型版本 ID 与编号规则版本 ID，发布 `EmployeeActivityRequestApproved`（HD-03，契约 §3 第 12 项）。
  - Given 提交成功 When 查审批平台 Then 没有为该活动发起任何审批（HD-03，无人审）。
  - Given 活动类型不在已确认版本里或已停用 When 提交 Then 以 `…ACTIVITY_TYPE_NOT_LISTED` 拒绝，无活动行（HD-01）。
  - Given 没有任何已确认的活动类型版本 When 提交 Then 拒绝并告警，不用默认类型（契约 §3，配置缺失）。
  - Given 预估金额缺失或币种缺失 When 提交 Then `…ESTIMATE_REQUIRED` 拒绝（Q6、HD-03）；Given 金额为 0 When 提交 Then 以规则卡为准（规则未明写是否允许 0，**证据缺口**，先不断言）。
  - Given 任意预估金额 When 提交 Then 不做预算校验（HD-03，W2 前不查预算）。
  - Given 申请人已离职或不在职 When 提交 Then 拒绝（HD-02 在职员工）。
  - Given 申请人在业务日无法人归属 When 提交 Then 失败且不默认法人（G-T2、`EmployeeLegalEntities` 抛 422）。
  - Given 提交同一业务键（相同请求标识）两次 When 第二次到达 Then 返回同一条活动与同一编号，不产生第二条、不多耗编号（HD-05 重复提交返回同一结果）。
  - Given 已批准的活动 When 试图改说明、类型、参与人、预估金额 Then 拒绝（内容与参与人登记后不可改，HD-04）。
  - Given 已批准 When 办结 Then 状态变已办结，写一条状态转换（B31 GL4 状态词为「已办结」，不是「已完成」）。
  - Given 已办结 When 再次办结 Then 以相同结果返回或明确拒绝（需规则裁定，**证据缺口**），不重复写转换。
  - Given 已批准 When 取消且原因非空 Then 状态变已取消，记录取消人、取消时间、原因、取消前状态（设计 §3.6，HD-04）。
  - Given 已办结 When 取消且原因非空 Then 同样可取消，取消前状态记「已办结」（设计「双状态取消」）。
  - Given 取消原因为空或全空白 When 取消 Then 拒绝（`…CANCEL_REASON_REQUIRED`，HD-04），状态不变。
  - Given 已取消 When 再次取消（重试） Then 保留首次取消的人、时间、原因，不覆盖（设计 7.8「重试保留首次取消事实」）。
  - Given 已取消 When 办结、修改或恢复 Then 一律拒绝（`…EMPLOYEE_ACTIVITY_CANCELLED`，设计 §3.6）。
  - Given 已取消的活动 When 显式重登（带被取消登记引用） Then 新建一条新记录、新编号、引用原单，原记录保留不变（HD-05）。
  - Given 同一被取消记录 When 重登两次 Then 第二次返回第一次的后继记录，不产生第三条（设计 §3.6「按被取消登记引用唯一」）。
  - Given 重登时活动类型此刻已停用或预估金额缺失 When 重登 Then 重新校验并拒绝（设计：「重新校验当前规则」）。
  - Given 引用的被取消记录不存在、或不是已取消状态 When 重登 Then 拒绝。
  - Given 取消 When 完成 Then 不冲销任何报销、费用、采购单据（设计：取消不自动冲销；以「本模块不发任何向外的冲销命令」断言）。
  - Given 每次状态变化 When 完成 Then 同事务写一条 `state_transitions`（聚合类型 `EmployeeActivityRequest`，序号取该次变更后的 `version`，`legal_entity_id` 非空，`ck_state_transitions_first` 等通过）；回滚则无转换行（需容器）。
  - Given 取消 When 完成 Then 审计里能看到取消人与原因（经事件或直接审计，G-1 裁定后定），且事件/审计条目不含个人信息（`AuditEntry` 文本约束）。
  - Given 并发两人同时取消同一活动 When 提交 Then 其一因 If-Match 版本不一致得 412，且仅一次取消事实落库。
  - Given 缺 If-Match When 调办结或取消 Then 428（NAMING §2.6 A-8）。
  - Given 无 `employee-activity-request:create` 权限 When 提交 Then 403；Given 无 `employee-activity-cancellation:execute` When 取消 Then 403。
  - Given 列表查询 When 超过 limit 上限 Then 400，游标分页（A-3）。数据范围（谁能读谁的活动）**证据缺口**（契约 §5：共享 `CurrentAccess` 未落地，DATA-11），先不断言。
  - Given 迁移后空库 When 查表结构 Then `employee_activity_participants` 对 (申请, 参与人) 唯一，参与人表只可插入不可改（设计 §3.6，需容器）。

### 卡 E：workforce 入职日期与满周年查询（organization.workforce / W2 owner）
- 范围：`employee_versions`（含 hire/本次入职日期、雇佣阶段、停薪留职）的数据与 api 方法；词条 `hire_date`。依赖：one-data 签发、W2 设计（ADR-0068 命令）；不被 ADR-0075 阻塞。**非 admin 范围，admin 只提需求。**
- 验收场景：
  - Given 员工 02-29 入职 When 在平年查 02-28 的满周年名单 Then 包含该员工（ZN-03）；在闰年查 02-29 也包含；查 03-01 不包含。
  - Given 员工离职后重新入职 When 查本次入职日期 Then 返回重新入职之日而非首次入职日（ZN-04）。
  - Given 员工当日已离职（生效日≤当日） When 查满周年名单 Then 不包含（ZN-06 离职不发）。
  - Given 员工处于停薪留职 When 查 Then 包含（ZN-06）。
  - Given 员工入职日期缺失 When 按员工取入职日期 Then 返回空/明确失败，不返回默认（G-T2，不静默）。
  - Given 名单很长 When 查 Then 有分页/上限（D19）。

### 卡 D：周年贺卡每日自动生成（不含通知）
- 范围：`AnniversaryCardJob` + `GenerateAnniversaryCards` 用例（每员工一个事务，G-T1）+ `AnniversaryDate` 值对象 + 系统操作者 + 配置键 + 活动类型「周年贺卡」常量对应已确认类型；以（员工、本次入职日期、周年日期、活动类型）去重，包括已取消记录（设计 §3.6、§5.3、契约 §3 第 13 项）。
- 依赖：卡 C（提交用例）、卡 E（人事数据）、卡 A（周年贺卡类型已确认）、卡 N；不被 ADR-0075 阻塞。契约 §3 第 13 项「入职日期查询未到」＝启用前提。
- 验收场景：
  - Given 业务日 D（Asia/Shanghai）是在职员工的满 N 年周年（N≥1）且「周年贺卡」类型已确认 When 作业运行 Then 为该员工生成一条已批准的周年贺卡活动（ZN-01、ZN-02）。
  - Given 员工 2月29日入职、当年为平年 When 02-28 作业运行 Then 生成；When 03-01 Then 不生成（ZN-03）。
  - Given 员工重新入职 When 计算周年 Then 以本次入职日期为准，首次入职的周年不再触发（ZN-04）。
  - Given 同一业务日内作业运行两次（或重试） When 第二次运行 Then 不重复生成，返回原记录（ZN-05 同一业务日可重试；契约 §3 第 13 项幂等键）。
  - Given 员工 D 日的周年没有被生成（作业停了一天） When D+1 作业运行 Then 不补发（ZN-05 漏发不补，回溯 0）。
  - Given 员工当日已离职（生效日≤D） When 作业运行 Then 不生成（ZN-06）。
  - Given 员工停薪留职 When D 为其周年 Then 生成（ZN-06）。
  - Given 当日周年的贺卡已被取消 When 作业再次运行 Then 不重建（HD-05、设计 §5.3 自动任务不重建已取消记录）。
  - Given 员工入职日期取不到 When 作业运行 Then 该员工失败并告警（`…HIRE_DATE_UNAVAILABLE`），不影响其他员工（单个失败只重试自己），不静默跳过（CLAUDE.md 第 6 条）。
  - Given 员工在 D 日无法人归属 When 作业运行 Then 该员工失败并告警，不默认法人。
  - Given 「周年贺卡」类型未被已确认版本列出 When 作业运行 Then 全部拒绝并告警，不生成（契约 §3 第 13 项）。
  - Given 双实例同时触发 When 到点 Then 只有持锁实例运行（`@SchedulerLock`），且去重键使重复无副作用（双实例重复，设计 §7.9 验收）。
  - Given 入职当年（满 0 年） When 作业运行 Then 不生成（规则未明写，列为**待确认**，不断言）。
  - Given 手工提交类型为周年贺卡 When 提交 Then 以规则裁定为准（HD-02「周年贺卡由系统自动生成」，**证据缺口**）。

### 卡 F：周年通知与投递（可并入 7.9 的提醒卡）
- 范围：admin→platform.integration 依赖边、`DeliveryPurpose.ANNIVERSARY_CARD_GENERATED`、`DeliverySubjectKind.EMPLOYEE_ACTIVITY_REQUEST`、接收角色解析（员工关系负责人 vs 行政，M20；admin 无 identity 边，需 role 解析方案）。依赖：卡 D、one-architecture 批量审核枚举、one-rules 定接收人。验收：Given 周年卡生成 When 提交成功 Then 同事务登记投递且去重键含（员工、周年年份）；投递失败可重试且不重复生成。当前接收人未定，不在本轮。

依赖链：N、S、A 可并行（A 依赖 one-data 与 identity/glossary 同意）→ C → D（另需 E）→ F。7.8 的「手工登记」交付线（N、S、A、C）与「周年」交付线（E、D、F）互相独立。

基线文件冲突：本模块的 schema 基线由卡 A 首建。基线开放期（`backend/architecture/migration-baseline-open` 存在）下，后续卡改同一基线文件，需每次附 `pg_dump` 预期 diff（SESSION_PLAYBOOK §12.2 第 2 点）；若改为同目录追加新 V 文件，`migrations_immutable.py` 对「新增的版本号必须大于 base 最高」在开放期只告警。哪个更合规我没有找到明文，请 one-data/one-architecture 在签发卡 A 时一并说清。

---

## 7. 证据缺口与待裁清单（不猜）

- G-1 取消/办结不发事件时，审计走哪条路（事件 vs 直写 `AuditLog`）？设计 §2.3 与「审计靠事件」的现状不一致。影响：事件目录、`MUST_AUDIT_ACTIONS.yaml`、`audit-text-writers.txt`。
- G-2 契约「不新增 AuditSubjectKind 常量」与 REG-3 未做的现状冲突；是否由本批提前做 REG-3，还是 7.8 照 platform.file 先例加枚举与 CHECK。
- G-3 「正式员工」（ZN-01）是否排除试用期？依赖 W2 的 `employment_phase`；入职当年是否发（满 0 年）、手工提交「周年贺卡」类型是否一律拒绝（HD-02）。
- G-4 AGG-116 的「法人」口径：契约 §3.1 要求逐表登记。申请人在业务日的法人归属？组织者？周年卡取员工当日法人？one-data 签发时必须写。
- G-5 `estimated_amount numeric(19,4)` 与 `kernel.Money` 2 位小数 HALF_UP 不一致；列名 `estimated_currency_code` 与 NAMING §2.4 的 `_currency` 配对写法不同（`DdlNamingTest` 第 855 行用「包含」判断，能通过，但表述不一致）；仓库内此前没有任何金额列，这是首批。
- G-6 `config_set_versions` 的 PENDING→CONFIRMED 是否写 state_transitions、是否要 `version` 列（ADR-0075 决定 2）。ADR-0075 的 PR #552 仍 OPEN。platform_file 先例的版本表无 `version` 列却登记了 `PreservationCategoryVersion` 转换。
- G-7 kernel 没有通用错误码枚举（CS-2.24 要求通用错误只在 kernel 定义），`StateMachine.require` 需传入模块错误码，timesheet 仍定义自己的 `TRANSITION_ILLEGAL`/`CONCURRENT_CHANGE`；而提议术语里 `…CANCEL_REASON_REQUIRED` 属「必须填写原因」类通用错误，可能违反 G48/CS-2.24。需 one-architecture/one-glossary 指明落点。
- G-8 数据范围与读取权限（契约 §5，DATA-11）：`CurrentAccess` 未落地；`kernel.OrgUnitAccess` 存在但本卡是否用它未定。
- G-9 命名：命令词条仍叫 `complete_employee_activity`（完成员工活动）、权限资源 `employee-activity-completion`、状态词是 `concluded`（已办结，GL4）。命令/资源是否改为「办结」词族，需 one-glossary 定；`config_set` 现为 entity，需改 aggregate 才能写聚合类；数据库状态值英文名也未定（`administration_status__concluded` 等仍是 proposed）。
- G-10 单据类型缩写 `number_code`、编号模板、持有人（numbering owner 未在 contexts.yaml 登记）。
- G-11 `employee_activity_requests` 的列：设计 §3.6 草案未含 `event_count`、`created_by/updated_by`；`event_count` 与 `version` 必须有（`AggregateRoot`、`EventSequenceCoverageTest`），`_by` 以 one-data 签发为准（`cost_timesheet.timesheets` 无 `_by`）。
- G-12 一个信息核对：本卡引用「工作日历版本」作 K-2 先例；仓库实际没有该版本表（见 §0 第 2 点）。若用户另有指向的分支或 PR，我未看到。
- G-13 数据取证：老系统/A8 是否有入职日期可迁入、迁入后最早版本的覆盖（W2 设计第 190 行说要 E3 与 one-ops 取证），影响周年迁移期行为，不影响本蓝图。
