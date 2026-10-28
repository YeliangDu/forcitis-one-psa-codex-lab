# 地基项复核：矩阵 vs main 现状

状态：历史记录｜2026-10-16｜负责：one-architecture

日期：2026-10-16 · 只读核对，未改任何代码。
- 规格来源：`docs/modernization/CROSS_CUTTING_MATRIX.md`（2026-10-14 版，含 10-15 增补的 §2.9）。
- 代码现状：origin/main `cf3b9a3e`（只读工作树），自 2026-10-14 起合入约 1946 个提交。
- 判定口径：必须在代码里找到实现或守护才算「已建」；只有文档、ADR、派工卡的一律算未建。路径相对 `backend/src/main/java/com/forcitis/one/`（记 M/）、`backend/src/test/java/com/forcitis/one/`（记 T/）、`backend/architecture/`（记 A/）。

矩阵 §1 统计写「地基 28 条」，按类别列逐行数得 27 行（N01、N03、N06、N10、N11、N13、N14、N15；T01、T02、T04；C01、C02、C03、C05；P03、P04、P05、P08、P09、P10；E04；I01、I06；B01、B02、B04）。本表按 27 行列出，差的 1 条在矩阵里找不到对应行，建议修矩阵统计。

结果：**已建 6、部分建 9、未建 12**。

| 编号 | 名称 | 矩阵原状态 | main 现状 | 证据 | 缺口 |
|---|---|---|---|---|---|
| CC-N01 | 舍入点与中间精度 | 地基 | 未建 | `M/kernel/Money.java:16` 构造即 `setScale(2, HALF_UP)`，`plus/minus/times` 每步舍入；全库无 `MoneyCalculation` 或同类不舍入类型（grep 零命中）；`T/kernel/MoneyTest.java` 仍只测单步 | 没有「只在形成金额时舍入一次」的计算类型；连乘仍多次舍入 |
| CC-N03 | 按权重拆分原语（prorate） | 地基 | 未建 | `M/` 与 `T/` 中无 `prorate`、拆分原语（grep 零命中）；`Money` 无 `divide`、无拆分 | 无拆分原语，无 G-M2 属性测试 |
| CC-N06 | 拒绝非 2 位小数币种 | 地基 | 未建 | `Money.java:11-17` `SCALE = 2` 固定，构造不看 `Currency.getDefaultFractionDigits`；`Money.of("1","JPY")` 仍会静默得 2 位；`T/kernel/MoneyTest` 无此用例 | 无校验、无 G-M4 单测；DDL 也无币种 `CHECK` |
| CC-N10 | 费率、单价、比率的中间精度类型 | 地基 | 未建 | `M/kernel/` 无 `Rate`、`Ratio`；`Hours.java:16` 仍是 2 位 `HALF_UP` 的唯一数量类型 | `Rate`、`Ratio` 均未建 |
| CC-N11 | 百分比与比率的表示（0–1 小数） | 地基 | 未建 | 同 N10；无 `Ratio` 类型；XD-02 已答，但无实现 | `Ratio` 未建；`_pct` 列规则仍只比对类型名 |
| CC-N13 | 金额与比率列的 DDL 精度守护（G-M3） | 地基 | 未建 | `T/DdlNamingTest.java:60-66` 仍只是 `("_amount","numeric")`、`("_rate","numeric")` 类型名比对；无 `numeric(18,2)`、币种 `^[A-Z]{3}$` 检查；`:550` 币种成对只看 `CREATE TABLE`，`ADD COLUMN`（`:280` 只解析列定义）不查成对。现行迁移仍无 `_amount` 列，仅 `cost_timesheet.timesheets.submitted_hours numeric(10,2)` | p、s 不检查；`ALTER TABLE ADD COLUMN` 不查币种成对；无币种 `CHECK` |
| CC-N14 | 接口与前端的金额表示与格式化 | 地基 | 未建 | `frontend/packages/{api,core,design-system,ui}` 无金额类型、无 `Intl.NumberFormat` 金额格式化（`Intl.NumberFormat`、`toFixed` 全量检索零命中）；OpenAPI 无 `{amount,currency}` 约定与 G-M5 | XD-01「十进制字符串 + 币种」只是决定，无契约、无前端组件、无 G-M5、G-M6 守护 |
| CC-N15 | 领域代码禁止自行舍入和除法（G-M1） | 地基 | 未建 | `T/ArchitectureRulesTest.java:193-201`（`moneyIsNeverABareDecimal`，只查字段名且只扫 domain、api、events）、`:283-290`（`hoursAndQuantitiesUseValueObjects`）、`:292-298`（只禁 `BigDecimal.equals` 与 `new BigDecimal(double)`）；没有禁 `setScale/divide/round/RoundingMode/MathContext/doubleValue` 的规则。现状下 `M/` 中仅 `kernel/Money.java:16`、`Hours.java:16` 用 `setScale`，所以尚无违规，但无守护 | G-M1、G-M8（参数与返回值不得是裸 `BigDecimal`）均未建 |
| CC-T01 | 时钟、SQL `now()`、Jackson/JVM/JDBC 时区 | 地基 · 本批处理中 | 已建 | JVM：`M/kernel/UtcJvm.java` + `T/kernel/UtcJvmTest`，提交 `2efb1319` 启动前把 JVM 默认时区设为 UTC；Jackson：`backend/src/main/resources/application.properties:33-35`；JDBC 会话：`:36` `spring.datasource.hikari.connection-init-sql=SET TIME ZONE 'UTC'` + `T/kernel/DatabaseSessionTimeZoneIT`；JSON 时刻带偏移：`T/kernel/JsonTimeFormatTest`；时钟守护：`T/TimeSourceGuardTest`（扫全部生产类，覆盖 infrastructure、web、`ZonedDateTime/OffsetDateTime/LocalDateTime/LocalDate/Instant.now`、`Clock.system*`、`System.currentTimeMillis`，另有注入式反例 10 处）；SQL 数据库时钟：同测试 `sqlStringsDoNotReadTheDatabaseClock`（`:82` 起）+ `A/sql-database-clock-exceptions.txt`（除注释外为空，零例外） | 无。矩阵所写「17 个文件用 SQL `now()`」「守护不覆盖 infrastructure、web」已过期 |
| CC-T02 | 前端时刻显示的时区 | 地基 | 已建 | `frontend/packages/core/src/format-date-time.ts:1-9` 固定 `timeZone: 'Asia/Shanghai'`；`format-date-time.test.ts`；ESLint G-X2：`2a235f90`、`bcc5545a`（`Intl.DateTimeFormat`、`toLocale*String` 必带 `timeZone`，`frontend/eslint.config.js`，自测探针 `frontend/scripts/check-lint-config.mjs`） | 无 |
| CC-T04 | 跨日、跨月、跨年、闰日的边界测试夹具 | 地基 | 部分建 | 业务日跨日、跨年：`T/kernel/AggregateRootTest.java:79-81`（15:59:59Z、16:00Z、12-31 16:30Z）；前端 `frontend/packages/core/src/business-day.test.ts`；闰日仅在 `T/kernel/AccountingPeriodTest.java:40-44`（2028-02-29，会计期间值对象）；`T/EventBusinessDateGuardTest` | 没有共用的边界时刻夹具（G-X8）；跨月（月末 23:59:59+08:00）与闰日业务日未见测试；各测试各自手写时刻 |
| CC-C01 | 请求幂等（`Idempotency-Key`） | 地基 | 已建 | 过滤器：`M/platform/web/IdempotencyFilter.java:45-53`；范围：`M/platform/web/IdempotencyScope.java`（只对 `A/idempotent-endpoints.txt` 登记的 POST 生效，ADR-0043 §4）；存储：`M/platform/idempotency/infrastructure/JdbcIdempotentResponses.java`、`M/kernel/IdempotentResponses.java`、迁移 `platform_idempotency/V20261014221000__platform_idempotency_baseline.sql`、清理 `PurgeExpiredResponses`；OpenAPI 声明：`M/platform/web/OpenApiProblems.java:93-95`；守护：`T/IdempotentEndpointGuardTest`（登记簿与代码一致、应答不含直接标识）、`T/IdempotentResponseSizeTest`、`T/platform/web/IdempotencyScopeTest`、`T/platform/web/HttpContractIT.java:160`；前端：`frontend/packages/api/src/http.ts:179` + `http.test.ts:215` | 按设计只覆盖白名单内 POST（目前约 20 条）。矩阵所写「后端没有实现、openapi.json 没有标记」已过期 |
| CC-C02 | 并发版本（`If-Match` / `ETag`） | 地基 | 部分建 | 共用类型：`M/kernel/ExpectedVersion.java`（解析、428/400/412 三态、`etag()`）及 `VersionRequired/Malformed/MismatchException`；已被法人、法人归属、工作日历、角色、账号状态、编号规则、审批任务等控制器共用（grep `ExpectedVersion` 约 20 个文件）；OpenAPI 必填：`OpenApiProblems.java:71-76`（`requireIfMatch`）；前端 `http.ts` `versioned` 特性；测试分散在各模块（`T/platform/approval/TaskVersionHttpIT` 等） | 没有全局 OpenAPI 契约守护（G-X3：每个更新类接口必须声明 `If-Match`），也没有统一的 ETag 契约测试；是否漏接靠各控制器自觉 |
| CC-C03 | 事件消费幂等共用构件 | 地基 | 未建 | 仅 `M/cost/timesheet/domain/ConsumedEvents.java` + `infrastructure/persistence/JdbcConsumedEvents.java` + 表 `cost_timesheet.consumed_events`（`V20261014140000__cost_timesheet_baseline.sql:48-56`，`uk (event_id, consumer_name)`）+ `ConsumedEventsPurgeJob`，模块私有；`platform/identity`、`platform/approval` 的 5 个 `@ApplicationModuleListener`（`HandleOrgSnapshotChange`、`HandleEmployeeRegistered`、`RevokeBrowserIdentifiersOnSeparation`、`StartApprovalOnSubmission`、`ApplyConfirmedAuthorization`）未经任何共用消费登记；`T/` 中没有「每个监听器经共用构件或登记例外」的守护（G-X4） | 无 kernel 或 platform 层共用件；无 G-X4 守护。各监听器是否自身幂等未逐个核实 |
| CC-C05 | 主键 UUIDv7 | 地基（已排） | 已建 | 生成：`M/kernel/Uuids.java`、`TimeOrderedIds.java`、`IdGenerator`（默认 UUIDv7，提交 `a59805d7`、`aac92d51`）；DDL 守护：`T/PrimaryKeyTypeGuardTest`（ADR-0033：每表单列 uuid 主键、不得序列、标识列或数据库默认值；有效期间表须 `EXCLUDE`）；豁免仅 5 张框架表：`A/primary-key-exemptions.txt`；生成守护：`T/IdMakingGuardTest`（`UUID.randomUUID` 只在 `A/random-uuid-exceptions.txt` 的 1 例）；测试：`T/kernel/UuidsTest`、`TimeOrderedIdsTest` | 无。矩阵所写「表主键仍以 bigint 为主」已过期；审计表分区主键例外见下文 ADR-0048 |
| CC-P03 | 出参不暴露敏感列 | 地基 · 本批处理中 | 已建 | 守护：`T/SensitiveColumnExposureGuardTest`（响应字段的 snake_case 名与 `A/data-classification.txt` 中 SENSITIVE、RESTRICTED 列同名即失败）；登记：`A/data-classification.txt`、例外：`A/sensitive-response-exceptions.txt`（3 条，附理由）；另有 `T/OutwardTextGuardTest`、`T/NullableResponseGuardTest` | 按「同名」匹配，改名或换结构可绕过；仍属约定级守护，非数据流分析 |
| CC-P04 | 共用掩码组件 | 地基 | 未建 | `backend/src`、`frontend/packages` 中 `mask|redact|desensit` 零命中（仅 `T/` 的 `OssObjectStoreTest`、`TestAccounts` 命中无关词）；无 `<字段>Masked` 约定 | 整件未建；1b 报价首次返回费率与成本前必须有 |
| CC-P05 | 数据范围（`AccessScope`）在读路径生效 | 地基 · 本批处理中 | 部分建 | 端口：`M/kernel/OrgUnitAccess.java`（`Visible`，取不到范围就抛出，不放行）；实现：`M/platform/identity/application/OrgUnitScopes.java`、`domain/AccessScopeCalculator.java`；读路径接入：`OrgUnitController` → `ListOrgUnits`/`GetOrgUnit`（提交 `3f2f4f94` B1-scope）、`LegalEntityAttributionController` → `GetLegalEntityAttribution`；`T/` 仅 `ManagementEndpointIT`、`ErrorLogDetailIT` 引用端口 | 只覆盖组织单元的列表与详情、法人归属；员工、岗位、同步运行、审计条目、法人列表等读路径未按范围；无「读路径必须经范围」的通用守护；数据访问范围决策页 F-1 的 RULE-387 只在文档 |
| CC-P08 | 通用导出控制 | 地基 | 部分建 | 常量：`M/kernel/ListLimits.java:8` `EXPORT_MAX = 10_000`、`IMPORT_MAX`；守护：`T/ListPagingGuardTest.java:147`（`A-3` 配置里的导出、导入行数上限不得超过约定，或登记为缺口）；仅审计导出一处实现：`M/platform/audit/application/ExportAuditEntries.java`、`AuditCsv.java`（公式注入前缀含全角触发符）、`web/AuditExportController.java` | 没有可复用的通用导出构件（权限、审计、行数上限、公式注入、掩码一体）；公式注入处理仍在审计模块包内私有；无 G-X5；掩码依赖 P04（未建） |
| CC-P09 | 维度 ID 不得写死（G-T2） | 地基（已排） | 部分建 | `T/CurrentUserGuardTest`：对 `CurrentUser`、`EmployeeLegalEntities` 的实现禁止字面量、常量、`orElse` 默认的法人 ID（`INITIALISED_LEGAL_ENTITY` 仍只认 `LegalEntityId`），禁止现场 `new CurrentUser(`；相关：`T/TypedIdComparisonGuardTest`、`T/IdMakingGuardTest`、`T/IdPrintingGuardTest`；类型化 ID：`M/kernel/EmployeeId`、`UserId`、`ProjectId`、`WbsNodeId` 等 | G-X7 要求的「扩展到全部维度 ID（`OrgUnitId`、`UserId` 等）与配置默认值」未做；字面量检测只管法人 |
| CC-P10 | 敏感数据可见角色与授予方式 | 地基 · 下一批 F-1 | 部分建 | 敏感授予两人确认：`M/platform/identity/domain/SensitivePermissions.java`（6 个敏感权限码）、`application/AuthorizationGate`、`PrivilegeCeiling`、`ReplaceAccountRoles`、`UpdateRole`；测试：`T/platform/identity/SensitiveGrantTwoPersonIntegrationTest`、`OrgDataAdminRoleIntegrationTest` | 禁止经岗位或同步自动授予的守护未建；调岗、离岗生成「待调整权限」待办未建（代码中无相应概念）；ADR-0032 只有提议 |
| CC-E04 | 组织维度的历史（版本表） | 地基（已排） | 已建 | 迁移 `organization_workforce/V20261014131000__organization_workforce_baseline.sql`：`org_unit_versions`（`:307`，`ex_org_unit_versions_overlap` 排他约束 `:327`）、`assignment_versions`（`:346`，`:362`）、`legal_entity_versions`（`:377`，`:392`）、`legal_entity_attributions` 等及各自的区间不重叠约束；实现 `M/organization/workforce/infrastructure/persistence/OrgHistory.java`；测试 `T/organization/workforce/application/OrgHistoryAppendOnlyIntegrationTest`；跨 schema 版本引用守护 `A/cross-schema-version-references.txt` + `T/CrossSchemaVersionReferences`（`c6db7c12`） | 无（与组织、岗位版本相关的新增行为随 R1 迁入再核） |
| CC-I01 | 中文排序与检索 | 地基 | 部分建 | 前端：ESLint G-X2 要求 `localeCompare`、`Intl.Collator` 必带 `'zh'`（`2a235f90`、`bcc5545a`，`frontend/eslint.config.js`），`work-calendar.ts` 已修；后端：CODING_STANDARD CS-3.13「按中文排序的 SQL 显式 `COLLATE "zh-x-icu"`，不做 SQL 守护」只是规范；`T/SqlOrderByGuardTest` 存在 | 数据库仍按内建 C.UTF-8 建库（`T/TestPostgres.java:22-28`，DDL-11 统一为 C.UTF-8），迁移与代码中无 `COLLATE`、无拼音排序实现；后端人员、部门列表仍按码位排序；无后端守护 |
| CC-I06 | 统一分页与上限 | 地基 | 部分建 | 常量：`M/kernel/ListLimits.java`（默认 50、上限 200）；游标页：`M/kernel/CursorPage.java`、`PageRequest.java`、`InvalidCursorException`；守护：`T/ListPagingGuardTest`（`G-A2` 集合接口应答 `{items,nextCursor}` 并声明 `cursor`、`limit`；`G-P1` 契约中 `limit` 声明最小 1、最大 200、默认 50；`A-3` 不得内存切页）；契约：`T/platform/web/HttpContractIT.java:119`、`T/acceptance/platform/web/ListPagingContractIT`、`ListPagingKernelTest`；前端 `frontend/packages/api/src/paging.ts` | 缺口清单 `A/list-paging-gaps.txt` 仍有 8 行：`GET /organization/org-units`、`GET /platform/own-login-records`、`GET /platform/approval-tasks`（上限 200 但非游标，裁定例外）、`AuditQuery.MAX_LIMIT`、`AuthorizationRequestController`、`LegacyNumberController`、`ListNumberingRules` 的切片、审计导出 `max-rows` 配置 |
| CC-B01 | 税率上下文 TaxContext | 地基（F-7） | 未建 | `backend/src`、`backend/architecture` 中 `tax_context`、`tax_rate` 等零命中 | 整件未建 |
| CC-B02 | 通用时态生效基座 | 地基（F-8） | 部分建 | 没有共用的 kernel 类型；各表自行实现：`organization_workforce` 的 5 张版本与归属表、`platform_numbering` 规则版本表（`platform_numbering_baseline.sql`）、`platform_integration` 中的生效日期表，均带 `EXCLUDE USING gist (… daterange(effective_from_date, effective_to_date) WITH &&)`；共用守护：`T/PrimaryKeyTypeGuardTest.java:109-127`（带起止日期的表必须有排他约束）、`T/DataArchitectureTest`（D2、D10） | 没有共用的生效区间值对象、「按业务日期取值」读法与只追加写法；费率卡、税率、利润中心标识、科目映射仍无处可用 |
| CC-B04 | 利润中心 | 地基（下一批） | 未建 | `backend/src` 中 `profit_center`、`利润中心` 零命中；组织单元表无利润中心标识；`terms.yaml` 里只有 `profit_or_loss_ledger` | 整件未建；依赖 B02 |

## 其他核对到的相关情况（不在 27 行之内）

- 审计分区：ADR-0048（`docs/modernization/adr/src/ADR-0048.md`，用户 2026-10-16 批准）与 DATA_DESIGN_CHECKLIST §14.1 D16 已成文；main 迁移里没有任何 `PARTITION`（`platform_audit/V20261014134000__platform_audit_baseline.sql` 未分区），所以审计分区**只有决定，没有落库**。
- 数据访问登记簿：`backend/architecture/data-access-registry.yaml`、`external-read-registry.yaml`、`cross-schema-version-references.txt` 已存在并有守护，矩阵没有对应行。
- 字段级敏感分级：`A/data-classification.txt` 已含审计表 `detail`、`before_text`、`after_text`、幂等表 `response_text` 的 PI 标记（TB-01、TB-02，提交 `0122d21c`、`97a1a7c6`、`c12a1694`）。

## 矩阵需要改的状态

与矩阵当前记录不符的条目：

| 编号 | 矩阵记录 | 应改为 | 理由 |
|---|---|---|---|
| CC-T01 | 地基 · 本批处理中（B2b fix-time-zone） | **已覆盖** | JVM UTC、Jackson、JDBC 会话时区、`TimeSourceGuardTest`（含 infrastructure、web 与 SQL 数据库时钟，零例外）均已合入；「17 个文件用 SQL `now()`」「守护只覆盖 domain、application」两句过期 |
| CC-T02 | 地基 | **已覆盖** | `format-date-time.ts` 固定上海时区，G-X2 ESLint 规则与探针已合入 |
| CC-C01 | 地基（「后端没有实现」「openapi.json 没有标记」） | **已覆盖**（范围按 `idempotent-endpoints.txt` 白名单，ADR-0043 §4） | 过滤器、存储表、OpenAPI 声明、守护与前端发送均已合入 |
| CC-C05 | 地基（已排）；「表主键仍以 bigint 为主」 | **已覆盖** | ADR-0033 + `PrimaryKeyTypeGuardTest`，只剩 5 张框架表豁免 |
| CC-P03 | 地基 · 本批处理中 | **已覆盖**（注明按同名匹配、3 条例外） | `SensitiveColumnExposureGuardTest` 已合入 |
| CC-E04 | 地基（已排） | **已覆盖** | `org_unit_versions`、`assignment_versions`、`legal_entity_versions` 及排他约束已在基线迁移 |
| CC-C02 | 地基（「只有审批控制器私有实现」） | 仍为地基，但缺口改写为「共用类型已建（`kernel.ExpectedVersion`），缺全局 OpenAPI 契约守护 G-X3」 | 实现现状描述过期 |
| CC-P05 | 地基 · 本批处理中（B1），「`AccessScope` 只在测试中被调用」 | 仍为地基，缺口改写为「只接入组织单元列表与详情、法人归属，其余读路径未接，无通用守护」 | B1-scope 已合入一部分 |
| CC-I01 | 地基（XD-03，1b 前平台约定 + G-X2） | 仍为地基，拆成两半：前端 G-X2 已建；后端 `COLLATE "zh-x-icu"` 约定只在 CS-3.13，库仍是 C.UTF-8，未建 | 前端守护已合入 |
| CC-I06 | 地基（「法人、角色、组织单元、编号规则等列表没有分页」「OpenAPI 契约测试未实现」） | 仍为地基，缺口改写为「守护 G-A2、G-P1、A-3 已建，登记缺口 8 行待清」 | `ListPagingGuardTest` 与 `list-paging-gaps.txt` 已合入 |
| CC-T04 | 地基 | 仍为地基；注明「业务日跨日、跨年已有测试，缺共用夹具、跨月与闰日业务日」 | 部分测试已有 |
| CC-P09 | 地基（已排） | 仍为地基；注明「`CurrentUserGuardTest` 仍只认 `LegalEntityId`，G-X7 未扩」 | 守护未扩到全部维度 ID |
| CC-P08 | 地基 | 仍为地基；注明「`EXPORT_MAX` 常量与配置上限守护已建，通用构件未建」 | 部分已建 |
| CC-P10 | 地基 · 下一批 F-1 | 仍为地基；注明「敏感授予两人确认已建，禁止自动授予与待调整权限未建」 | 与矩阵现状一致，仅补证据 |
| CC-N15 | 证据行号 `:192-200`、`:288-294` | 行号改为 `:193-201`、`:292-298`，缺口不变 | 行号漂移 |
| 矩阵 §1 | 地基 28 条 | 按逐行数为 27；统计需随上面 6 条改类别重算（地基 21、已覆盖增 6） | 统计与行数不一致 |

仍未建、矩阵状态无需改的地基项（12 条）：CC-N01、N03、N06、N10、N11、N13、N14、N15、C03、P04、B01、B04。金额与比率一族（N01、N03、N06、N10、N11、N13、N14、N15）自矩阵成文以来**没有任何一项落地**，`Money.java` 与 `MoneyTest.java` 自 10-14 起无变化。
