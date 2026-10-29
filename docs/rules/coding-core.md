---
paths:
  - "backend/**"
---

# 编码规范核心页 — FORCITIS one

读写 `backend/` 下文件时自动载入（`.claude/rules/coding-core.md` 软链到本文件）。
括号里的编号（CS-x.y）指向 `docs/rules/coding-standard.md` 细则原行；两处不一致以原行为准，并更正本页。

## A. 黄金样板

每类一种写法，照「为何」写；标「现」的为样板偏差，#812 修。

| 构件 | 样板 | 为何 |
|---|---|---|
| 用例 | `SubmitTimesheet:65` | execute 上 `@Transactional`，一个聚合，save 后 publish |
| 调用者 | `SubmitTimesheet:66` | 注入 `CurrentUser`，不收 `UserId caller`（现在 identity） |
| 数据范围 | `GetOrgUnit:31` | 用例注入 `OrgUnitAccess`；范围外答找不到并审计（现由参数传入） |
| 控制器 | `OrgUnitController:33` | 只转换、调用例；200 返回 record，非 200 或带头才 `ResponseEntity`（现算范围、收 caller） |
| 仓储 | `JdbcRoleRepository:117` | 文本块小写 SQL，命名参数，版本条件更新 |
| 事件 | `ApplyApprovalResult:41` | application 中 `@ApplicationModuleListener`，先去重 |
| 时间 | `JdbcRoleRepository:347` | `Instant` + `BusinessZone`；JDBC 用 `OffsetDateTime`，不用 `Timestamp` |
| ID | `CreateLegalEntity:29` | 注入 `IdGenerator`，不调 `randomUUID` |
| 金额 | `Money:10` | 计算不舍入，成额时 HALF_UP 到分；合计 = 各行舍入后之和（FD-02/07/08；现构造即舍入） |
| 流程 | `FlowableApprovalEngine:54` | Flowable 只在适配器，业务经 `ApprovalProcesses` |
| 单元测试 | `TimesheetStateMachineTest:62` | 不起 Spring，固定日期 |
| 集成测试 | `AuditExportAndOwnRecordsIT:36` | MockMvc + `TestPostgres.database` |
| 验收测试 | `WorkCalendarMakeUpWeekendIT:38` | 同上，`@DisplayName` 以卡号开头 |

## B. 底线

「待建」：现只靠复审，检查待 #810 建。

1. 模块间只经对方 `api`、`events`，依赖同 `backend/architecture/module-dependencies.txt`（CS-1.12）。执行：`ModularityTest`、`ModuleDependencyTest`
2. domain 不依赖 Spring，web 只调 application，JDBC 只在 infrastructure（CS-2.1）。执行：`ArchitectureRulesTest`（分层各条）
3. `@Transactional` 只在 application 用例，一个用例只写一个聚合（CS-2.3）。执行：`transactionsOnlyInApplication`、`SingleAggregateGuardTest`
4. 接口方法都有 `@PreAuthorize` 或 `@PublicEndpoint`；请求不带调用者身份（CS-2.9）。执行：`ArchitectureRulesTest`（鉴权两条）
5. 每个查询、列表按数据范围过滤，fail-closed：空范围零行、算不出即拒绝、越范围按 id 读回 404（`docs/ops/security-invariants.md` I-10）。执行：待建
6. 金额只用 `kernel.Money`，不用 `double`、`float`、裸 `BigDecimal`（CS-2.27）。执行：`moneyIsNeverABareDecimal`（只认字段名）
7. 已过账事实不改不删，审计、版本表只追加；更正只走冲销；关账后更正、期间重开须财务批准（全文 CS-3.9）。执行：G34、`DataArchitectureTest`（D8）
8. 迁移：`backend/architecture/migration-baseline-open` 在时改表直接改基线并附预期表结构 diff；撤掉后已合入迁移只追加（CS-3.5，ADR-0038）。执行：`scripts/guards/migrations_immutable.py`
9. 不写、不打印密钥；测试无密钥字面量；`RESTRICTED` 不进日志与异常 message（CS-5.7、CS-6.2）。执行：gitleaks
10. 不做生产写；建账号、起容器（本机 Testcontainers 除外）、对外调用先问用户（CS-6.8）。执行：`scripts/gates/prod_write_gate.py`
11. 老代码只作证据，不搬其片段、结构、路径、命名；复刻老行为以生产数据为证；领域模块无外部或老系统名称（CS-1.2、CS-1.6、CS-1.3）。执行：`LegacyShapeTest`、`ExternalConnectionIsolationTest`
12. 实现方不改验收测试（CS-5.3）。执行：`scripts/guards/acceptance_guard.py`
13. 不为过测试放宽或删断言；确需改，PR 单列理由（CS-5.4）。执行：PR 模板、复审
14. 标识符只用 ASCII，用语取 `docs/glossary/terms.yaml`，查不到先登记提议术语（CS-1.4）。执行：`identifiersAreAscii`、`GlossaryTest`、`term_rules.py`
15. 命名与位置按 `docs/rules/naming-standard.md`（CS-1.5）。执行：`NamingSurfaceTest`
16. 单文件超 1000 行必改（硬上限）；宜单文件 ≤ 400 行、单方法 ≤ 60 行，超出作复审建议（CS-1.15）。执行：#810 行数检查（待建）
17. 缺失或无效输入失败并告警，不静默跳过（规则卡明确允许的除外；CS-1.7）；不吞异常，无空 `catch`（CS-2.24）。执行：`CurrentUserGuardTest`；其余待建
18. 时间只经注入的 `Clock`，domain 不持有它，时刻与业务日由 application 传入；SQL 与迁移不读库时钟（CS-2.26、CS-3.8）。执行：`TimeSourceGuardTest`
19. 状态只经聚合的领域方法改（CS-2.4）。执行：`StateWriteGuardTest`
20. SQL 只在 `Jdbc*Repository`，只访问本模块 schema 并登记 `backend/architecture/data-access-registry.yaml`（CS-3.6）。执行：`DataArchitectureTest`

## C. 领域写法

1. 金额是「数值 + 币种」，跨币种运算拒绝；数量用 `Hours` 等值对象（CS-2.27）。防错加。执行：`ArchitectureRulesTest`
2. 舍入只在金额内核一处，`HALF_UP` 到分；领域代码不 `setScale`、`divide`、指定 `RoundingMode`（CS-2.27，XD-08）。防各处自舍。执行：待建
3. 按权重拆分（`prorate`，XD-07）守恒且确定，用最大余数法（XD-08）；财务答 FQ-23、FQ-24 不同则自某期首日起改、不追溯（`docs/architecture/cross-cutting-decisions.md`）。执行：复审
4. 接口金额 `{amount: 十进制字符串, currency: ISO 4217}`（XD-01）。防丢精度。执行：待建
5. 时刻用 `Instant`，库里 `timestamptz`（UTC），不用 `LocalDateTime`。无时区会随机器变。执行：`timestampWithoutTimeZoneIsRejected`
6. 业务日是 `LocalDate`，只经 `kernel.BusinessZone`（Asia/Shanghai）`today(clock)`，不另写 `ZoneId.of`（CS-2.26）。执行：`EventBusinessDateGuardTest`
7. 进过账或受「法人 × 期间」闸门约束的聚合带 `LegalEntityId` 与 `AccountingPeriod`，来源不确定即失败（DATA_DESIGN_CHECKLIST §14 第 5 项）；配置版本按与会计期间对齐的日期生效、不追溯（CS-7.5）。执行：复审
8. 聚合 ID 由 application 经注入的 `kernel.IdGenerator` 生成（UUIDv7，`kernel.Uuids`），不调 `UUID.randomUUID`（CS-3.4）。测试可固定 ID。执行：`IdMakingGuardTest`
9. ID 是类型化 record，输出取 `value()`，不用其顺序表达业务顺序；聚合间按 ID 引用，跨模块单据用 `kernel.DocumentReference`（CS-2.20、CS-2.21、CS-2.7）。执行：`IdPrintingGuardTest`、`SqlOrderByGuardTest`、`DocumentReferenceGuardTest`
10. 状态是 enum，转换表在聚合内（可用 `kernel.StateMachine`），非法转换抛领域异常（CS-2.4）。执行：`statusesAreEnums`
11. `POST` 的应答只在登记于 `backend/architecture/idempotent-endpoints.txt` 时按 `Idempotency-Key` 保存，应答不含个人直接标识与敏感列（ADR-0043 第 4 节）。防重复入账。执行：`IdempotentEndpointGuardTest`
12. 可变聚合带 `version`；修改类接口必带 `If-Match`（缺 428、不符 412），读给 `ETag`，用 `kernel.ExpectedVersion`（CS-2.6）。防覆盖他人修改。执行：`RepositoryWriteGuardTest`
13. 须审计的动作登记 `docs/architecture/must-audit-actions.yaml`，审计明细不写人名。执行：`MustAuditActionsTest`、`AuditDetailGuardTest`
14. 单据编号只经 `platform.numbering` 的 `DocumentNumbers`，在本用例事务内取号（CS-2.25）。执行：复审
15. 业务拒绝抛 `kernel.DomainException` 带本模块 `ErrorCode`，由 `GlobalExceptionHandler` 转 ProblemDetail；通用错误码只在 kernel（CS-2.24）。执行：`ErrorCodeGlossaryTest`
16. 外部输入在 web 边界解析一次，非法抛 `kernel.InvalidRequestException`（400），之后信任类型、不重复判空（CS-2.24）。重复防御掩盖缺陷。执行：复审
17. 默认非空；找不到返回 `Optional`（只作返回值）；集合返回空集合；响应可空组件标 `@Nullable`（CS-2.23）。执行：`NullableResponseGuardTest`
18. 事件由聚合记录，是过去式不可变 record，只含 ID 与值对象，登记 `docs/architecture/events/asyncapi.yaml`；用例在同一事务内 `save` 后 `events.publish(x)`（`DomainEvents`；CS-2.5）。发布登记与业务写一起提交。执行：`EventCatalogueTest`；事务内复审
19. 跨模块监听只用 `@ApplicationModuleListener`（提交后、新事务、异步）：失败不回滚发布方，可能重投，须幂等（如 `ConsumedEvents`）。执行：`ArchitectureRulesTest`；幂等复审
20. Flowable 只在 `platform.approval.infrastructure`，其他模块经 `platform.approval.api`（CS-2.11）。执行：`flowableOnlyInsideApprovalInfrastructure`
21. `JdbcClient` 用命名参数 `:name`，SQL 写文本块、列名显式；值不拼进 SQL，动态排序列取白名单（CS-3.6、CS-3.7）。防注入。执行：待建
22. Controller 路径 `/api/v1/…`，出参是 `*Response` record（要状态码或头时用 `ResponseEntity<*Response>`）。执行：`controllersAreVersioned`；出参待建
23. 不写只有一个调用方、只转发的包装。多一层无信息。执行：复审
24. 注释默认不写，只在「为什么」看不出时写一行（反直觉约束、规范条款、外部怪癖、性能或并发取舍、单位精度与时区、老行为证据出处）；不写复述、历史、作者日期、注释掉的代码、无编号 TODO、显而易见参数的 Javadoc；与代码不符按正确性必改（全文 CS-1.1）。执行：复审

## D. 技术栈禁用表

| 旧写法 | 替代 | 执行 |
|---|---|---|
| `javax.annotation`、`javax.inject` 等（JDK 的 `javax.sql`、`javax.crypto` 除外） | `jakarta.*` | #810 ArchUnit |
| `@MockBean`、`@SpyBean`；以为 `@SpringBootTest` 自带 MockMvc | `@MockitoBean`、`@MockitoSpyBean`；显式 `@AutoConfigureMockMvc` | 编译或测试失败 |
| `RestTemplate`、`TestRestTemplate` | `RestClient`、`RestTestClient` | #810 ArchUnit |
| `com.fasterxml.jackson.databind`、`ObjectMapper` | `tools.jackson`、`JsonMapper`（注解包不变） | #810 ArchUnit |
| `org.springframework.lang.Nullable` | `org.jspecify.annotations.Nullable` | #810 ArchUnit |
| `spring-retry` | spring-context `@Retryable`；不重试非幂等写 | #810 ArchUnit |
| `spring-boot-starter-web`（现 pom 仍用） | `spring-boot-starter-webmvc` | #810 |
| Java 25 预览特性 | 不用 | #810 |
| JPA、Hibernate | `JdbcClient` | #810 ArchUnit |
| `LocalDateTime`、`java.sql.Timestamp` | `Instant`、`LocalDate` | #810 ArchUnit |
| 测试里 `Thread.sleep` | 确定性等待（CS-5.6） | #810 ArchUnit |

待核（未核实，不作规则）：Testcontainers 2 的 `PostgreSQLContainer` 新包名；Security 7 去掉 `.and()` 与 `AntPathRequestMatcher`；等待库用 Awaitility（未引入）。

## E. 测试写法

1. 测行为：集成测试走 HTTP → 鉴权 → 用例 → 真实 PostgreSQL，不 mock 自己的模块（CS-5.1）。执行：`TestContainerGuardTest`
2. 先红后绿；缺陷的回归测试在基线上须失败（CS-7.3）。执行：复审
3. `@DisplayName` 以规则、差异、ADR、复审或 intent 编号开头（CS-5.2）。执行：复审
4. 确定性等待，不用 `Thread.sleep`，不写宽泛的 `never()`（CS-5.6）。执行：待建
5. 只断言本模块的效果（CS-5.5）。执行：`TestIsolationGuardTest`
6. 固定 `Clock` 与 `IdGenerator`（CS-5.8）。执行：复审
