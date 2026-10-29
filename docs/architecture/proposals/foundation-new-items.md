# 四项新增地基：main 现状、缺口与校准切片最小范围（2026-10-16，只读核对，未评审）

状态：评审中｜2026-10-16｜负责：one-architecture

依据：施工方案 `CONSTRUCTION_PLAN_DRAFT.md` §4.2、§5.3、§10、§10.1；`CALIBRATION_SLICE.md`；ADR-0035、0036、0031、0037。
核对基线：origin/main @ `cf3b9a3e`（2026-10-16，#372 合并后）。证据路径相对 `backend/`、`frontend/`、`docs/`（main 上的同名目录）。
「车道周」= 一个会话加其 Codex 派工一周的产出，**全部为粗估，未实测**，按 CS-1.8 只估切片用得到的部分，另列「全量」仅供排期参考。

## 0. 先说三个会影响估算的事实

1. main 目前只有 `cost.timesheet`、`organization.*`、`platform.*` 七八个模块（`src/main/java/com/forcitis/one/` 下仅 `cost`、`kernel`、`organization`、`platform`）；**没有 `accounting.*`、`operations.facts`、B-01、B-02、B-04、B-16**。ADR-0036 里的「历史期间」「迁入占用」「迁入过账通道」都依赖尚不存在的模块，切片里只能做到单据层。
2. `cost.timesheet` 在 main 上只有 `api / application / domain / events / infrastructure`，**没有 web 控制器**；前端也没有工时页面。切片的工时填报界面是从零开始。
3. 首次 dev 部署尚未发生（`phase1a/infra/DEV_DEPLOY_REMAINING.md` 的 S-5 待用户批准）；`migration-baseline-open` 标记仍在，基线迁移可直接改，新增地基表可以并入基线而不必另写迁移（首次生产部署前删标记）。

---

## 1. `platform.migration` 迁入框架

### 1.1 ADR-0036 的要求（摘要，按决定编号）

| 项 | 要求 |
|---|---|
| 决定 1 | 独立模块 `platform.migration`，只依赖 `platform.integration` api 与各 BC api，不被任何模块依赖；离线命令，不加载 `@Scheduled`、ShedLock、Flowable 异步、事件监听、出站 worker；各 BC 一个迁入入口（`Propagation.MANDATORY`，计入 api 方法上限 12）；专用目标库角色 `one_<环境>_history_loader`（无 DDL，只 SELECT / INSERT 与限定列 UPDATE，时间窗外 NOLOGIN） |
| 决定 3 | `platform_integration.migration_batches`、状态机 STAGING→AWAITING_TIE_OUT→COMMITTING→EFFECTIVE（终态 ABANDONED、REVERSED）；`source_references` 增 `migration_batch_id`、`source_row_digest`、`source_reference_status`；业务表不加批次列 |
| 决定 4 | 迁入单据编号列存原编号作展示号，`legacy_numbers` 为权威（G-MG9） |
| 决定 5 | 迁入不发事件、不发通知、不耗计数器、不启动流程（G-MG4、G-MG5） |
| 决定 6 | 历史期间、迁入占用表、迁入过账通道 |
| 决定 7 | 暂存表（Flyway 固定表）、续跑、试提交后回滚、先原行后冲销行、拒收表 `migration_rejects`（原因代码、处置 `RERUN_AFTER_FIX` / `EXCLUDED`，处置经应用内操作） |
| 决定 8 | 核对 `migration_tie_outs`：行数、金额、来源 ID 摘要、按 (期间, 法人, 来源单号) 行数与金额；来源侧在抽取同段单独聚合；零差异或财务签字才能提交（G-MG7） |
| 决定 9 | 提交阶段失败与生效后纠错 = 整批冲销，冲销前查下游引用；已冲销批次重跑 = 新批次 |
| 决定 11 | 批次级审计，不逐行写 |
| ADR-0037 | 读取列登记按用途 `MIGRATION` / `MIGRATION_TIE_OUT`；迁入、对账类适配器端口只能被迁入、对账模块依赖；凭据列拒绝清单 |
| 施工方案 §4.2 | 全部历史明细迁入；老数据不完整，拒收不静默跳过；**ADR-0036 的「随各阶段迁入」改为一次投产前的全量迁入** |

### 1.2 main 现状（证据）

| 方面 | main 上已有 | 证据 |
|---|---|---|
| 来源对照表 | `platform_integration.source_references`：来源系统 × 来源对象类别（仅 EMPLOYEE、ORG_UNIT、POSITION、LEGAL_ENTITY）× 来源 ID（bigint）→ 自有 ID；确认状态；排除约束；**没有 `migration_batch_id`、`source_row_digest`、`source_reference_status` 三列**，对象类别也不含工时单等单据 | `db/migration/platform_integration/V20261014133000__platform_integration_baseline.sql`:134 起；`JdbcSourceReferenceRepository` |
| 原编号 | `platform_numbering.legacy_numbers` 已按 ADR-0033 形状建好，`migration_batch_id uuid NOT NULL`，但**没有批次表**（无外键，批次 ID 只是个 UUID）；`LegacyNumberEntry`、`JdbcLegacyNumberRepository`；`NumberingRulesIT`:326-356 覆盖读写 | `db/migration/platform_numbering/V20261014100100__platform_numbering_baseline.sql`:57-78 |
| 批次标识类型 | 只有值对象 `MigrationBatchId` | `platform/integration/api/MigrationBatchId.java` |
| 读取通道 | 老系统与 A8 的只读连接、启动校验、只读核实命令、读取登记守护（`CredentialColumnGuardTest`、`ExternalConnectionIsolationTest`、`LegacyAdapterPlacementTest`、`QualifiedBaseTableGuardTest`、`ReadOnlyVerificationCommandIT`） | `platform/integration/infrastructure/connection/*`；`src/test/.../platform/integration/guard/*` |
| 读取列登记 | `architecture/external-read-registry.yaml` 只含组织、岗位、员工、任职、待办的 `RUNTIME_SYNC` / `RUNTIME_TODO` 行；**用途 `MIGRATION` 行数为 0**；词表 `read_purpose` 已有 `MIGRATION`、`MIGRATION_TIE_OUT` 取值 | 同文件:19；`docs/glossary/terms.yaml`:6493 |
| 离线命令范式 | `integration-operations`、`identity-operations`、`read-only-verification`、`migrate` 四种 profile，进程跑完一条命令即退出 | `IntegrationOperationsRunner`；`application-*.properties`；`MigrateOnlyIT` |
| 数据架构守护 | 变更性词表已含 `MIGRATION_ONLY`，来源词表含 `MIGRATION`，迁移溯源只认 `legacy_numbers` 或 `ONE_OFF_IMPORT` | `DataArchitectureTest`:47-50、:414 |
| 归档 | `ArchiveVault`（对象存储、WORM、保留期一致性检查） | `platform/integration/api/ArchiveVault.java` |

**不存在**：`platform.migration` 包（`module-dependencies.txt` 无此行）；`migration_batches`、`migration_rejects`、`migration_tie_outs`、暂存表、`migration_period_reservations`；`history_loader` 角色（`docs/ops/one-db-roles-schemas.sql` 只有 owner、migrator、app、readonly 四类）；G-MG2、G-MG4、G-MG5、G-MG7、G-MG9、G-MG10 的守护；各 BC 的迁入入口；`accounting.period`、`operations.facts`。已有的 `GlossaryTest`、`DdlNamingTest` 会要求新表列有注释、有词表登记、有数据分级（`data-classification.txt`）。

### 1.3 缺口

1. 三张登记表加暂存表加 `source_references` 三列加对象类别扩展（需含工时单这类单据，且暂存、来源 ID 类型要处理「文本标识」——ADR-0033 第 2 项第 1 条写明文本标识另立条款，工时若来源是 bigint 则不触发，**须先确认老系统工时主键类型**）。
2. 编排模块：抽取（分段、断点、行摘要）→ 暂存 → 来源侧聚合 → 试提交（同一入口，写完回滚）→ 核对 → 分块提交 → 提交后复核 → 冲销。
3. 每个目标 BC 的迁入入口（`cost.timesheet` 先做）、迁入角色与时间窗。
4. 拒收处置与核对签字的应用内操作（需要后端接口与前端页面，权限码单列）。
5. 守护与读取登记。
6. 与全量方案的差距：一次投产前的全量（施工方案 §4.2）意味着编排模块要支持 28 个 BC 逐批在 dev 演练，切片只验证框架可行性。

### 1.4 切片最小范围

只服务「抽样迁一个已结月份的工时」，并做成**框架的真实最小骨架**，不为切片写一次性脚本：

- 表：`migration_batches`、`migration_rejects`、`migration_tie_outs`、一张工时暂存表；`source_references` 三列与单据类对象类别（`TIMESHEET`）；`legacy_numbers` 补外键指向批次表（基线开放，直接改基线）。
- 编排：STAGING → AWAITING_TIE_OUT → COMMITTING → EFFECTIVE 的主路径加 ABANDONED；试提交回滚；断点续跑；拒收写表；核对只做行数、金额合计（工时数）、来源 ID 摘要、按 (月份, 法人, 来源单号) 的行数与金额四项。
- 目标：`cost.timesheet` 一个迁入入口（`MANDATORY`，同事务写来源对照、原编号对照；审批结论作为状态直接迁入，不启动流程）。
- 读取：登记工时来源列，用途 `MIGRATION`、`MIGRATION_TIE_OUT`，随迁入规格走 DATA_DESIGN_CHECKLIST §14.2 评审与 ADR-0037 登记一行取证。
- 角色：dev 上建 `one_dev_history_loader`（无 DDL、限定授权、时间窗外 NOLOGIN），在 `one_db_roles_schemas.sql` 补出。
- **不做**：历史期间、迁入占用、迁入过账通道（依赖不存在的 B-19、B-16）；整批冲销与下游引用检查；财务签字界面（切片用命令行录入核对结论并在报告中单列，全量前补应用内签字）；多对象类型的依赖顺序调度。
- 可选加做（若要校准「过账事实迁入」的真实代价）：待切片的 B-19、B-16 建成后，再加历史期间终态与迁入过账通道，约加 1 车道周；建议**等 B-16 过账管道稳定后再决定**，不放进首批。

### 1.5 建议守护

| 守护 | 写法 |
|---|---|
| G-MG6（批次术语唯一） | 并入 `NamingSurfaceTest` 或 `check_legacy_name.py` 的禁用写法表：`import_batch`、`ImportBatch` 在代码、表结构、词表里出现即失败；R-1b 遗留的 `legacy_numbers` 已改名，现在加守护零成本 |
| G-MG2（暂存续跑一致） | Testcontainers 集成测试：同一合成数据，一次跑完与「中途杀进程续跑」两次，比对暂存与目标行集合相同 |
| G-MG4 / G-MG5（无副作用、上下文不含调度与监听） | `@SpringBootTest` + `@RecordApplicationEvents` 跑一个批次断言事件数为 0、编号计数器不变；另一个测试加载迁入 profile 的 Bean 清单，断言无 `@Scheduled`、`@SchedulerLock`、`@ApplicationModuleListener`、出站 worker；ArchUnit 断言没有任何类依赖 `platform.migration` |
| G-MG7（核对完整） | 单元测试：规格对象清单里缺一项核对行、或差异未签字，`commit` 抛错 |
| G-MG9（编号一致） | 集成测试：工时单编号列与 `legacy_numbers` 不一致时告警并以后者为准 |
| G-MG10（入口只被迁入模块调用、`MANDATORY`、无 `REQUIRES_NEW`） | ArchUnit，参照 `ArchitectureRulesTest` 现有写法；`MAX_API_METHODS_PER_MODULE` 不动 |
| 读取登记 | 扩 `ExternalReadRegisterTest`：每个迁入读取语句的列在登记中、用途匹配；`SELECT *` 禁止；凭据列沿用 `CredentialColumnGuardTest` |
| 角色授权 | 扩 `RoleSeparatedDeploymentIT`：`history_loader` 对业务表无 UPDATE、DELETE，对 `event_publication` 无任何写权限 |

### 1.6 粗估

- 切片最小：**2.5 至 3 车道周**（1 条车道：表加编排主路径加工时入口；Codex 派工做暂存、核对与守护；含 R-1b 式基线改动的复审）。
- 全量框架（含历史期间、占用、过账通道、冲销、签字界面、守护全套，不含各 BC 适配器）：约 **6 至 8 车道周**。施工方案 §6.1 另按「每个有迁入数据的 BC 加 25%」计适配器，不重复计。

---

## 2. 事件机制运维

### 2.1 现状对照（施工方案 §10 的 E-1 至 E-8、§10.1 逐条）

| 编号 | 要求 | main 现状（证据） | 状态 |
|---|---|---|---|
| E-1 | 不引入消息中间件；判据入库；T-12 压测 | Modulith JDBC 登记表在用（`event_publication`、`event_publication_archive`，baseline :7、:97）；ADR-0002 勘误未补；无压测 | 判据与压测未做（文档项加压测项） |
| E-2 | 每个监听者声明幂等写法，守护测试 | 17 个 `@ApplicationModuleListener`，分布 8 个文件（identity 5、audit 1、approval 1、`TaskNotifier` 9、`cost.timesheet.ApplyApprovalResult` 1）；**只有 `cost.timesheet` 用去重表**（`ConsumedEvents` 在 `cost/timesheet/domain/`，`JdbcConsumedEvents`，表 `cost_timesheet.consumed_events`，`uk(event_id, consumer_name)`，`V20261014140000__cost_timesheet_baseline.sql`:48-58；清理作业 `ConsumedEventsPurgeJob`，`ConsumedEventsPurgeJobIT`）；其余 16 个各自如何幂等本次**未逐一核实**；`ArchitectureRulesTest`:393-399 只禁止 `@EventListener`，**没有「必须声明」的守护** | 缺 |
| E-3 | 重放命令（按类型或时间段、从 30 天归档、指定监听者、干运行、限速、审计、权限码单列） | 只有 Modulith 自动重投；无命令；`EventPublicationOperationsIT` 只测归档与统计 | 缺 |
| E-4 | 运维页面与按监听者、类型的积压指标 | 仅两个总量 gauge：`...event.publications.exhausted`、`...stale`（`EventPublicationJobs.java`:50-54）；`JdbcEventPublicationStatistics` 只返回总数；出站死信另有 `deliveries.dead.letters`（`DeliveryMetrics.java`:27）；无运维页面，前端无运维模块 | 缺 |
| E-5 | 死信责任人与时限 | `alert-rules.yaml` 有 `OneEventPublicationStale` / `Exhausted`（P2，`ONE_ALERT_P2`）与 `OneOutboundDeadLetters`（`phase1a/infra/runbooks/files/alert-rules.yaml`）；责任人登记与升级路径未落；§10.1 已写规则 | 告警有，责任人表无 |
| E-6 | 分析侧死信放行部署 | `reporting` 模块在 main 不存在；`event_quarantines` 表不存在 | 不适用于切片 |
| E-7 | 迁入不发事件 | 无迁入进程 | 随 §1 的 G-MG4、G-MG5 |
| E-8 | W5 压测 | 无 | 未做 |
| ADR-0035 决定 6 | 维护模式表 `platform_scheduling.maintenance_modes`、隔离登记表 `platform_integration.event_quarantines`、排空预检、写请求过滤器、隔离命令 | **两表都不存在**（baseline 与 `platform_scheduling` 基线无此名）；`scripts/release/*` 与 `deploy.yml` 无排空步骤 | 缺；ADR 写明首次生产部署前实现 |
| §10.1 死信规则 | 谁负责、时限、纪律 | 文档已定；dev 阶段只发现不处理（用户一人） | 文档已有，运维手册未入 |
| 统一幂等做法 | — | `platform.idempotency` 是 **HTTP 请求幂等**（`JdbcIdempotentResponses`、`IdempotencyFilter`），与事件消费幂等无关；事件去重组件只在 `cost.timesheet` 内，**没有升到 `platform.eventing` 或 kernel 供各 BC 共用**（跨模块只能依赖 api / kernel） | 缺共用组件 |
| 重投与告警 | 每分钟重投、超 1 分钟、最多 8 次、10 分钟 stale、ShedLock；`ALERT` 日志；`EventPublicationJobsTest`、`EventPublicationOperationsIT` | `EventPublicationJobs.java`；`config/platform_integration.properties`:54-60 | 已有 |

### 2.2 缺口

1. 幂等共用组件：把 `ConsumedEvents` 这一套抽到 `platform.eventing`（或 kernel 端口加 eventing 实现），含按事件 ID × 消费方的去重表模板、清理作业。注意去重表是每个 BC schema 各一张（ADR 的 schema 归属要求），所以共用的是**接口与同形表约定**，不是一张全局表。
2. 声明机制与守护：注解（如 `@IdempotentBy(DEDUP_TABLE | CURRENT_STATE)`）或登记清单，二选一，名称走词表流程。
3. 重放命令、隔离登记表与命令、维护模式表与过滤器、按监听者与类型的指标、运维页面、责任人登记表。
4. 17 个现有监听者补声明（并借机逐个核实其真实幂等性，这是本次未做的核实）。

### 2.3 切片最小范围

切片新增的监听者（B-16 订阅「工时通过」、B-06 订阅审批结论与项目状态、B-04 或 B-01 的跨 BC 读取如有事件）是真实的新增订阅方，且事件登记表要承接「过账请求」类发布（ADR-0031 第 3 项把未完成的过账请求算作过账队列），因此切片最小做：

- E-2：共用幂等组件加声明机制加守护测试，17 个存量补声明，切片新监听者按声明写。
- E-4 的指标半边：按监听者、按事件类型的积压 gauge（不做页面）；告警沿用现有 10 分钟阈值。
- 不做：E-3 重放命令（切片无重放需求，且依赖 E-2）、E-4 页面、隔离登记与维护模式（首次生产部署前，非切片）、E-6、E-8、E-1 压测。
- 顺带：用 §10.1 初稿补一份 dev 阶段死信手册片段（一页），不写代码。

### 2.4 建议守护

| 守护 | 写法 |
|---|---|
| 幂等声明（E-2） | ArchUnit：每个带 `@ApplicationModuleListener` 的方法或类必有声明；声明去重表者其类必须依赖共用组件接口；声明读当前态者必须在注解属性中列出读取的聚合类型；无声明失败。参照 `ArchitectureRulesTest`:393 的写法，空集合用 `allowEmptyShould(false)` 防止守护空转 |
| 去重行为 | 集成测试：同一事件 ID 投递两次，副作用只有一次；`ConsumedEventsPurgeJobIT` 模式复用于共用组件 |
| 指标 | 集成测试：造一条失败发布，断言按监听者标签的 gauge 为 1；`alert-rules.yaml` 增一条按监听者的规则并有 YAML 语法检查（现有 `scripts/ci` 无此项，需补一个小检查） |
| 重放（全量时） | 集成测试：干运行报数、超 30 天拒绝、对 `cost.timesheet`（去重表）与读当前态各一例；审计写入断言 |
| 隔离与排空（全量时） | `MigrateOnlyIT` 同款：部署剧本脚本测试，排空预检在死信非 0 时中止 |

### 2.5 粗估

- 切片最小（E-2 加指标）：**1 至 1.5 车道周**（含 17 个存量监听者的核实与补声明，核实量不确定）。
- 全量（再加重放、隔离表与命令、维护模式、运维页面、责任人登记、压测）：约 **3.5 至 4.5 车道周**，其中 ADR-0035 的维护模式与隔离约 1.5，页面约 1（前端侧）。

---

## 3. 前端框架与共用组件

### 3.1 main 现状（证据）

- 栈：React 19.3、TypeScript 5.9、Vite 7、antd 5.29（`frontend/pnpm-workspace.yaml` catalog）、React Router 7、TanStack Query 5；PC `apps/web`，H5 `apps/h5`（Taro 4）；包 `packages/api`、`core`、`design-system`、`ui`。依据 ADR-0013（已采纳）。**没有表单库**（无 react-hook-form 等）、没有图表库。
- 生成的 API 客户端：`pnpm generate:api` 由 `packages/api/openapi.json`（后端 `OpenApiExportTest` 写出）生成类型、Zod 校验器、每个操作一个函数（`packages/api/src/generated/{schema,validators,operations}.ts`），自带 `If-Match`、`Idempotency-Key` 特征；`ApiError`、`MissingVersionError`、`ResponseShapeError`；游标分页 `collectPages`、`nextCursorOf`（`packages/api/src/paging.ts`）；`check:generated` 在 `pnpm verify` 中守护。
- 共用组件（`packages/ui/src/index.ts`，共 14 个导出）：`AppShell`、`Button`、`DataTable`（antd `Table`，`pagination={false}`，只有渲染函数式列）、`DescriptionList`、`Dialog`、`EmptyState`、`LoadingState`、`Notice`、`TextField`、`TextAreaField`、`SelectField`、`InboxList`、`LoginPanel`、`PageTitle`、`ResultPage`、`StatusBadge`、`TextLink`。
- `packages/core`：`useVersionedQuery`（版本与乐观并发）、`useIntentKeys`、`describeFailure`、`failureOf`、`isVersionConflict`、`ERROR_MESSAGES`（错误码到中文）、`formatDateTime`、`businessToday`、会话与审批 hooks（`useInbox`、`useTask`、`useTaskActions`、`useTaskRecords`）、`installApiGuards`（会话过期与无权限跳转）。
- 应用结构：`apps/web/src/modules/{approval,attendance,numbering,session,workforce}`，每模块 `api / hooks / model / pages / components`，`index.ts` 出口至多 8 个名字（`scripts/check-structure.mjs` F13）；页面共 9 个（待办、任务详情、编号规则、工作日历、组织单元、同步运行、登录相关）；路由硬编码在 `app-routes.tsx`；导航由后端返回树，`toNavEntries` 转换（`app/navigation.ts`）。
- 设计规范：`packages/design-system`（`tokens.css` 调色板与语义令牌含 `--status-*`、`--ledger-*`，`theme.ts` 映射到 antd，`design-system-provider.tsx`）；ADR-0013 写明「设计正本就用 Forcitis One Design System」；仓库内**没有页面模板、表单规范、表格规范的说明文档**（`docs/architecture-guardrails/frontend` 是守护模板副本，不是规范）。
- 守护：ESLint（`eslint-plugin-boundaries`）、stylelint、`check-structure`（含 M-15 跨根目录重复代码）、`check-copy-glossary`（界面文字对词表）、`generate-api --check`、`verify.mjs`。
- 权限：后端有 `permissionCodes`（`schema.ts`:976、1027 等）与角色接口；**前端没有权限判断工具**（无按钮级可见性或路由守卫，仅 `installApiGuards` 处理 403 跳转）。
- 审批：待办与任务详情已建，`TaskActionBar` 在 `apps/web/src/modules/approval/components/`（H5 另一份）；但**没有「在单据页发起提交审批并显示审批进度」的共用件**。

### 3.2 切片页面对照

切片页面：人员（B-13）、客户（B-01）、项目（B-04）、工时单（B-06）、费率表（B-02）、会计期间（B-19）、过账查询（B-16）。需要的共用件与现状：

| 共用件 | 现状 | 切片是否必需 | 说明 |
|---|---|---|---|
| 列表页模板（筛选栏、游标分页、排序、空态、错误态） | `DataTable` 无分页；现有页面用 `collectPages` 一次拉全量 | **必需** | 过账查询、工时单、人员都可能上千行；游标分页加筛选栏加「加载更多」或翻页 |
| 详情页与编辑页模板（`DescriptionList` 已有；表单布局、保存、`If-Match` 冲突提示） | `useVersionedQuery`、`isVersionConflict` 有；表单布局无 | **必需** | 人员、客户、项目、费率表都是主数据表单 |
| 表单字段补全 | 只有文本、多行文本、下拉；`TextField` 的 `type` 含 `date`、`number` | **必需**：日期选择、数字或金额与工时输入（小数位、范围）、搜索式选择（客户、项目、人员引用）、校验提示 | 无表单库：选一个（或自写最小受控表单加 Zod），是需要一次决定的结构选择，**须按「架构选择先做适配研究」做研究，本稿不推荐** |
| 状态流转操作条（提交、批准、关账等，带确认与理由） | `Dialog` 有；`TaskActionBar` 绑定审批 | **必需** | 工时单提交、费率表批准与生效、期间关账都是状态流转 |
| 审批接入件（提交审批、审批进度、结论展示） | 无 | **必需**（工时单与费率表批准） | 复用 `platform.approval` 的 hooks，补单据页内嵌件 |
| 状态标签映射 | `StatusBadge`、`--status-*` 令牌 | 已有，每个 BC 补取值映射 | 取值映射放各 BC 模型层 |
| 权限判断（按钮与菜单可见、路由守卫） | 无 | **必需**（B-13 人事与财务期间只能指定角色操作） | 一个 `useCan(permissionCode)` 加 `<Can>` 即可 |
| 金额与工时格式化 | `formatDateTime`、`businessToday`；无金额 | **必需** | 钱用整数分或十进制字符串，不用 `number` 做运算，须与后端 JSON 形状一致 |
| 日历式工时填报（周视图、每人每天一张、节假日） | 工作日历管理页有；填报界面无 | 工时页**必需**，但属 B-06 业务页，不是共用件 | 可放 `modules/timesheet`，若别的 BC 要用再上提 |
| 路由与菜单登记 | 路由硬编码，菜单来自后端 | 需要约定：每个 BC 一个路由片段，避免所有 BC 改同一个 `app-routes.tsx`（28 个 BC 并行会冲突） | 切片先试一次拆分 |
| 错误处理 | `describeFailure`、`ERROR_MESSAGES`、`Notice` 已有 | 已有，补新错误码映射 | `ErrorCodeGlossaryTest` 要求错误码中文名 |
| 导出、批量操作 | 无 | 切片不做 | 过账查询的导出放后续 |
| 设计规范文档 | 令牌有，用法说明无 | 切片建议补**一页**页面类型约定（列表、详情、表单、流转） | 否则 28 个 BC 各画各的 |

### 3.3 切片最小范围

补 7 件：游标分页列表模板（含筛选栏）、编辑与详情页模板、补全表单字段（日期、数字与金额、搜索式选择）加校验约定、状态流转操作条、审批内嵌件、权限判断、金额与工时格式化；路由按 BC 拆片；一页页面约定。7 个切片页面本身属各 BC 的建造，不计入地基。不做：H5、导出、批量、图表。

### 3.4 建议守护

- `check-structure.mjs` 加规则：业务模块不得直接 `import 'antd'`（只能经 `packages/ui`）；列表页必须使用列表模板（可用 ESLint 自定义规则或结构检查，检查 `pages/` 下含 `DataTable` 的文件是否同时引用分页状态）。
- `check-copy-glossary` 已覆盖界面文字；新页面随词表登记。
- 权限：后端 `OpenApi` 声明每个操作的权限码（若尚未声明，作为契约先行补充），前端测试断言「无权限时按钮不渲染」；参照 `app-routes.test.tsx`。
- 金额：lint 规则禁止对金额字段用 `Number()` 与浮点运算（`eslint-plugin-unicorn` 之外需一条自定义规则，或用品牌类型 `Money`）。
- `generate-api --check`、`pnpm verify` 沿用。

### 3.5 粗估

- 切片最小：**2 至 2.5 车道周**（含表单库的选型研究；页面类型约定一页）。
- 全量共用件（含 H5 对应件、导出、批量、图表）：**4 至 5 车道周**；28 个 BC 的页面是各自的建造，不在其内。

---

## 4. 演练环境

### 4.1 main 现状

| 项 | 现状（证据） |
|---|---|
| 环境清单 | 只有 `dev` 与 `prod`：`deploy.yml`:18-21（`options: [dev, prod]`）；`scripts/release/build.sh`:25、:57（`dev,prod`）；`deploy_gate.py`；`application-dev.properties`、`application-prod.properties`；GitHub environments `release`、`dev`、`prod`（`phase1a/infra/DEV_DEPLOY_REMAINING.md` R-1） |
| 实际资源 | dev：ECS `i-bp122xlpar8w2uylmal2`，库 `forcitis_one_dev`，角色 `one_dev_*`；**prod 的 ECS 待建**（同文件 R-6）；两库共用一个 PG 18 实例（`pgm-bp13vak0sew3m003`，与 it_portal 共用；`INFRA_RUNBOOK.md` D-1：生产「扩容现有实例共用」，建议 8 核 32 GB） |
| 部署脚本 | 发布包签名、GitHub OIDC 换 RAM 角色、OOS 参数下发、ECS 每分钟自取（`scripts/release/{build,ship,remote_install,deploy_gate,ci_passed}`；`.github/workflows/{deploy,release}.yml`）；每环境一个 RAM 角色、一组 OOS 参数、一个 `one.env` |
| 数据库角色与 schema | `docs/ops/one-db-roles-schemas.sql` 按环境名写死 `one_dev_*`；无 `rehearsal` |
| 配置 | `one.env` 模板每个键必须存在（`remote_install.sh`:230-232）；profile 与目标环境一致（`remote_install.sh`:407-409）；企业微信收件人白名单（dev 仅 1 条）、`DisabledWecomClient`、单点登录开关、`FORCITIS_JOBS_ENABLED` 必设 |
| 生产闸门 | 生产部署需 Phase 1a 出口条件、C-9、基线关闭（`deploy_gate.py`） |
| 敏感数据与加密 | `runbooks/C-8-kms.md` 有 KMS；ADR-0037 允许运行时只读两库，读取列登记含敏感级别 |
| 恢复约束 | ADR-0036 决定 9：生产库与 it_portal、dev 同一 RDS 实例，**不按时间点恢复**（会连带退回其他库）；这对「演练库放同一实例」意味着，演练的回退只能靠重建或备份恢复到新库 |

### 4.2 用户已定与 ADR-0036 决定 10 的冲突

用户已定：演练环境接近生产、只有指定角色能看、用真实数据。ADR-0036 决定 10 现状：dev 演练可用真实数据但 **6e 敏感数据（薪酬、个税社保、银行卡）只在生产迁入，dev 用合成数据**。施工方案 §5.3 的两个已结期间重算要用真实薪酬数据，二者冲突。**建议交 one-architecture 修订决定 10**（本稿不改），修订要点：

1. 命名「演练环境」为独立于 dev 的环境（独立 ECS、独立库，不与 dev 共库）。
2. 6e 敏感数据允许进入演练环境，条件：访问控制到具体角色与人（RAM、安全组或跳板、数据库角色、应用角色四层都限定）；会话只拿计数与核对结果（沿用决定 10 第一款）；读取授权按 DATA_DESIGN_CHECKLIST §14.2 评审；银行卡等按 ADR-0037 登记敏感级别；演练结束后删除，并留删除记录。
3. dev 保持合成数据（含 6e）；dev 上非 6e 真实数据沿用现有规定。
4. 外联全部关闭：企业微信空白名单或禁用客户端、U8C 推送指向沙箱或禁用、单点登录关、出站白名单清空，防止真实数据触发真实通知。
5. 备份策略：演练库不入长期备份，或短保留；磁盘加密。
6. 与 ADR-0030（非生产单人运维例外）的关系：演练环境装了真实敏感数据，**不能沿用单人例外**，须说明（预计需要至少两人在场的操作规矩）。

### 4.3 地基需求

1. 第三环境的流水线：`deploy.yml` 选项、`build.sh` 环境列表、`deploy_gate.py`、GitHub environment、OIDC 与 RAM 角色、OOS 参数、`one.env` 模板、`application-rehearsal.properties`、`remote_install.sh` 的 profile 对应。每处现在写死 `dev|prod`，加一个值要逐处改并配守护（现有 `ship_test.sh`、`deploy_gate.py` 自测需扩）。
2. 资源：独立 ECS（接近生产规格）、独立数据库及角色（`one_rehearsal_*`，含 `history_loader`）、网络隔离与访问限制、监控与告警接入（`alert-rules.yaml` 的 `channel` 标签区分）。**实例是否与生产共用要用户定**：共用则连接数预算与资源竞争进 ADR-0025 第 2 条预算，且回退受限于决定 9；独立实例则要买资源（付费项，须用户确认，CLAUDE.md 第 9 条）。
3. 数据通道：迁入读取老系统与 A8 生产库的授权（已有 ADR-0037 登记机制）、迁入角色时间窗、迁入后清理剧本、演练数据删除记录。
4. 演练剧本与计时（施工方案 §5.3：至少两次全量模拟切换，最后一次计时）：属 W5 工作，不是地基。
5. 访问控制验证：一条测试或检查证明「非指定角色读不到」——数据库层 `RoleSeparatedDeploymentIT` 模式扩展，应用层按角色的接口测试，基础设施层由 one-ops 出核验记录。

### 4.4 切片阶段是否需要

**不需要**。切片的迁入抽样（§1.4）：合成数据用 Testcontainers 做自动化验证；一个已结月份的真实工时抽样可在 dev 上用单独 schema 与 `one_dev_history_loader`，按 ADR-0036 决定 10 第一款（非 6e 真实数据可用、只有运维角色可访问、会话只拿计数）执行，工时不属于 6e。因此演练环境最晚在 **2027-06 前就绪**即可（W5 模拟切换之前）；建议**开建时间在就绪日前约 8 至 10 周**（估计值，含用户确认购买、RAM 与网络配置的等待，这些是日历时间而非车道周），决定 10 的修订则应在开建前定稿。

### 4.5 建议守护

- 脚本级：`deploy_gate.py`、`build.sh`、`ship_test.sh` 自测扩一个 `rehearsal` 用例；新增检查「所有出现 `dev|prod` 的发布脚本与工作流必须同时枚举 `rehearsal`」（可参照 `scripts/ci/db_scripts_aligned.py`）。
- 配置级：`ProductionConfigSanityTest` 一类测试对 `application-rehearsal.properties` 断言：出站白名单空、企业微信与 U8C 推送关闭、单点登录关、`FORCITIS_JOBS_ENABLED` 必设。
- 数据级：演练角色授权测试（`history_loader` 授权项、`NOLOGIN` 时间窗）；演练后「库已删除」核验行写入运维记录，登记台账。
- 访问级：one-ops 出访问核验记录一次并在每次演练前重做。

### 4.6 粗估

- 切片阶段：**0**（不建）。
- 全量演练环境（流水线加第三环境、独立库与角色、外联关闭配置、守护、访问核验）：约 **2 至 2.5 车道周**，主要在 one-ops，另有用户确认购买与配置的等待时间；演练剧本本身不在内。

---

## 5. 汇总与建议顺序

| 项 | 切片最小（车道周） | 全量（车道周） | 与切片关系 |
|---|---|---|---|
| 1 迁入框架 | 2.5–3 | 6–8 | 切片内抽样迁一个月工时，需要；无过账通道部分 |
| 2 事件机制运维 | 1–1.5 | 3.5–4.5 | 切片新增监听者须按 E-2 声明；重放、隔离、页面后置 |
| 3 前端共用件 | 2–2.5 | 4–5 | 7 个切片页面的前提 |
| 4 演练环境 | 0 | 2–2.5 | 切片不需要；决定 10 的修订先于开建 |
| 合计 | **5.5–7** | 15.5–20 | 估计，未实测 |

建议：地基 2 与 3 可与 B-13、B-01、B-19 并行（互不依赖）；地基 1 依赖工时单的迁入入口，应排在 B-06 补齐规则之后、B-16 之前或同时；地基 4 不排入切片，决定 10 的修订另交 one-architecture。

## 6. 本次核对的限度

- 未逐个核实 16 个非 `cost.timesheet` 监听者的幂等做法；未运行任何测试或构建，也未读取生产数据。
- 老系统工时主键类型（是否 bigint）未核对；若是文本标识，迁入框架须先修订 ADR-0033 第 2 项第 1 条（ADR-0036 决定 2 已声明）。
- 车道周全部为结构判断的粗估，不含用户决策等待时间。
- 「表单库选型」「演练环境是否独立实例」是需要研究或用户决定的项，本稿只列出，不推荐。
