# 编码规范 — FORCITIS one

日期：2026-10-15 · 用户要求：问编码规范是否需要完善，建议 A 合并为 CODING_STANDARD.md 唯一来源、B 补语言层规范、C 交付前自查命令、D 派工卡模板：「A–D 全做」（intent `2026-10-15-014844-one-coordinator-…`）

## 0. 适用范围与优先级

**适用范围**：FORCITIS one 仓库中的全部新代码——后端（Java / Spring Boot / Spring Modulith）、数据库迁移与 SQL、前端（React + TypeScript，PC `apps/web`，H5 `apps/h5`（Taro））、测试、CI 与交付脚本。人和代理（Claude 会话、子代理、Codex）同样适用。

**规范文档的分工**（三份规范 + 两份代理须知）：

| 文档 | 管什么 | 不管什么 |
|---|---|---|
| 本文件 `CODING_STANDARD.md` | **怎么写代码**：通用写法、语言层规范、测试、日志与密钥、交付前自查。编码规则的唯一入口 | 命名与 DDL 的细则、会话流程 |
| `NAMING_STANDARD.md` | **叫什么、放哪里**：词源、类名后缀、包结构、列名类别词、约束与索引命名、DDL 设计原则（§2.4a）、守护 G1–G48 / F1–F14 / D1–D3 | 写法与流程 |
| `METHODS.md` | **怎么干活**：会话、派工、并行、修复批、变更治理、流程梳理（DDD 与数据架构检查清单拆在 `DATA_DESIGN_CHECKLIST.md` §14、§14.1） | 具体写法 |
| `CLAUDE.md` | 最硬的规则摘要与指向，开工必读 | 细则（以本文件与上两份为准） |

**优先级**（高到低）：用户原话（`intent/`） > 已采纳 ADR > 规格、规则卡、`contract.md` > 本文件、`NAMING_STANDARD.md`、`METHODS.md`（各管各的范围） > 现有代码的写法。现有代码只是证据，不是先例：与本文件不一致的代码按缺陷处理（按 §7.3 走缺陷卡或重构批，不顺手改）。

**本文件怎么引用原规则**：每条写出处；出处是另两份文档的，本文件只汇总，**原规则的含义以出处为准**，本文件措辞与出处不一致时按出处执行并更正本文件。新增的编码规则（§2–§6 中标「新增」的）以本文件为出处，经用户批准后生效（见 §10 待决问题）。

**标注**：
- `守护：<名>`——CI 中自动检查的测试或脚本；
- `守护（待实现：G-Cn）`——本文件新提的守护，设计见 §8，实现前靠复审；
- `复审`——每波 opus 复审逐项检查（METHODS M2）。
- 出处为 METHODS.md 的条目，规则列只写规则名，原文以出处链接为准，本文件不转述（避免两处措辞漂移）。

## 1. 通用

| # | 规则 | 出处 | 检查 |
|---|---|---|---|
| CS-1.1 | **代码注释能不写就不写** | [METHODS M5「注释」](METHODS.md#m5-写代码规则coding_standard-引此为出处) | 复审 |
| CS-1.2 | **老代码只作证据，不得照搬** | [METHODS M5「老代码只作证据」](METHODS.md#m5-写代码规则coding_standard-引此为出处) | 守护：`LegacyShapeTest`、ArchitectureRulesTest（G40）；复审（每波 opus 专查「老系统形状」） |
| CS-1.3 | **领域模块不出现外部或老系统名称**；外部系统只在 `platform.integration` 的适配器内 | CLAUDE.md；DATA_DESIGN_CHECKLIST §14 第 8 项 | 守护：`LegacyShapeTest`、`ExternalConnectionIsolationTest`、Modulith（G1） |
| CS-1.4 | **新标识符、界面文字、表列名先查 `glossary/terms.yaml`** | [METHODS M5「第二批」第 5 条](METHODS.md#m5-写代码规则coding_standard-引此为出处)；ADR-0018 | 守护：`GlossaryTest`（G37–G42：G40 聚合根、G41 领域事件按 bc 加类名反查词条，G42 类名技术泛词；存量见 `backend/glossary/code-term-baseline.txt`，只减不增）、`ErrorCodeGlossaryTest`、R-5 中文名唯一检查（错误码全局唯一，其余同一 bc 内唯一）、用语登记规则 `glossary/term_rules.py`（B1）、前端 `pnpm check:copy` |
| CS-1.5 | **命名与位置遵守 `NAMING_STANDARD.md`**，守护不通过不得合并 | CLAUDE.md；NAMING_STANDARD §4 | 守护：G1–G48、D1–D3、F1–F14 |
| CS-1.6 | **复刻老行为以生产为证据，不凭推测** | [METHODS M5「第二批」第 1 条](METHODS.md#m5-写代码规则coding_standard-引此为出处) | 复审 |
| CS-1.7 | **缺失或无效输入失败并告警，不静默跳过** | [METHODS M5「第二批」第 3 条](METHODS.md#m5-写代码规则coding_standard-引此为出处)；DATA_DESIGN_CHECKLIST §14 第 5 项、G-T2 | 守护：`CurrentUserGuardTest`（法人不得写死或默认）；其余复审 |
| CS-1.8 | **只实现当前阶段规格所需** | [METHODS M5「第二批」第 4 条](METHODS.md#m5-写代码规则coding_standard-引此为出处) | 复审 |
| CS-1.9 | **能一开始做对的，不留到后续阶段** | [METHODS M5「第二批」第 11 条](METHODS.md#m5-写代码规则coding_standard-引此为出处) | 复审 |
| CS-1.10 | **契约先行** | [METHODS M5「第二批」第 8 条](METHODS.md#m5-写代码规则coding_standard-引此为出处) | 守护：CI `api-contract` 导出断言 + frontend verify；PR 模板。快照移出后不再逐字节比较入库契约，跨版本兼容性比对仍待 G-C1（见 frontend/packages/api/README.md） |
| CS-1.11 | **强类型**：类型化 ID、值对象、状态枚举、sealed 结果类型；契约中禁止 `Object` / `Map<String, Object>`；前端禁 `any` | GUIDING_PRINCIPLES §10 | 守护：G16、G17、G21–G24、G31、G32、F7 |
| CS-1.12 | **深模块、窄接口**：模块对外只有 `api` 与 `events`；`api` 方法总数 ≤ 12；前端模块经 `index.ts` 导出 ≤ 8 | NAMING_STANDARD §0 | 守护：G1–G4、G30、F1–F4、F12、F13 |
| CS-1.13 | **生成的代码只放 `generated/`，不手改**；改生成器或源契约后重新生成 | NAMING_STANDARD §5 | 守护：前端 `pnpm check:generated` |
| CS-1.14 | **系统名称**：新系统写 FORCITIS one，老系统写「老系统」（英文 the legacy system），不用旧缩写指代系统；老路径、表名、接口、代码标识、仓库名、会话名与用户原话照原样 | CLAUDE.md（用户 2026-10-14） | 守护：`check_legacy_name.py` |
| CS-1.15 | 一个文件一个顶层类型；单文件 ≤ 400 行、单方法 ≤ 60 行 | NAMING_STANDARD §5；§4「计划补充的守护」 | 复审（Checkstyle 守护计划中，未实现） |

## 2. Java / Spring（DDD 落地）

### 2.1 结构与边界（汇总）

| # | 规则 | 出处 | 检查 |
|---|---|---|---|
| CS-2.1 | 包结构 `com.forcitis.one.<子域>.<BC>.{api,events,domain,application,infrastructure.persistence,web}`；类名后缀只用 NAMING_STANDARD §2.2 表中的写法 | NAMING_STANDARD §2.2、§3 | 守护：G6–G15、G26 |
| CS-2.2 | **用例一类一个**，动宾命名，唯一 public 方法 `execute` | NAMING_STANDARD §2.2、§5 | 守护：G27（唯一 public 方法；入口名 `execute` 靠复审） |
| CS-2.3 | **事务边界只在 application**；**一个应用服务只写一个聚合**的仓储，例外登记在 `backend/architecture/multi-aggregate-exceptions.txt` 并附理由 | NAMING_STANDARD G11；DATA_DESIGN_CHECKLIST §14 第 1 项、G-T1 | 守护：G11、`SingleAggregateGuardTest` |
| CS-2.4 | **状态机在聚合内**：只能经领域方法改状态，非法转换抛领域异常；application / infrastructure / SQL 不直接写状态 | DATA_DESIGN_CHECKLIST §14 第 2 项 | 守护：`StateWriteGuardTest`、`RepositoryWriteGuardTest`、G16 |
| CS-2.5 | **事件由聚合产生**：不可变 record、过去式、不以 Event / Request / Command 结尾，负载只含 ID 与值对象，登记事件目录；事件上的业务日期来自参数或聚合状态 | DATA_DESIGN_CHECKLIST §14 第 3 项；G5、G29 | 守护：G5、G29、`EventBusinessDateGuardTest` |
| CS-2.6 | **并发**：可变聚合带 `version`（乐观锁）或用带前置状态条件的更新 | DATA_DESIGN_CHECKLIST §14 第 4 项 | 守护：`RepositoryWriteGuardTest`（版本表无条件写须登记） |
| CS-2.7 | **聚合之间按类型化 ID 引用**，不持有对方对象；跨模块标识不用裸 `String` / `long`；业务单据跨模块用 `kernel.DocumentReference` | DATA_DESIGN_CHECKLIST §14 第 6 项 | 守护：G31、`DocumentReferenceGuardTest` |
| CS-2.8 | **读模型与命令分开**：查询投影经 `*Queries` 端口读，不经加载与保存聚合的仓储 | DATA_DESIGN_CHECKLIST §14 第 9 项 | 守护：`ReadModelGuardTest` |
| CS-2.9 | **鉴权**：每个接口方法 `@PreAuthorize` 或 `@PublicEndpoint(reason)`；请求 record 不带调用者身份字段；只有 identity 配置 Spring Security | NAMING_STANDARD G28、G33 | 守护：G28、G33 |
| CS-2.10 | 禁字段注入、`System.out`、JUL、`java.util.Date`、`@PermitAll`；禁 `BigDecimal.equals` 与 `new BigDecimal(double)`；主代码禁 `Fix*` / `Demo*` / `Mock*` / `Test*` / `Sample*` | NAMING_STANDARD G18、G24、G26 | 守护：G18、G24、G26 |
| CS-2.11 | 流程引擎只在 `platform.approval.infrastructure`；会计科目只在 `accounting.ledger`；过账只经 `operations.facts` | NAMING_STANDARD G19、G34–G36 | 守护：G19、G34–G36 |
| CS-2.12 | 服务端给人看的文字（卡片、待办标题、消息）只放在 `*Texts` 类，中文切词后须是已登记术语或允许的非业务词 | 复审 M-16 | 守护：`OutwardTextGuardTest` |
| CS-2.13 | web / api 出参不直接暴露 `SENSITIVE` / `RESTRICTED` 列 | NAMING_STANDARD §2.5 | 守护：`SensitiveColumnExposureGuardTest` |

### 2.2 语言层规范（新增，依据现有代码写法与 R-1a 复审）

**CS-2.20 ID 值对象输出用 `value()`**（新增；R-1a 复审（PR #125 正文）H-1）
- 类型化 ID（`UserId`、`OrgUnitId`、`SyncRunId` 等 `@ValueObject` record）写到接口响应、事件负载、日志键、游标、审计明细、SQL 参数时，一律取 `id.value()`（需要文本时 `id.value().toString()` 或 `String.valueOf(id.value())`），**不得对 ID 对象本身调用 `String.valueOf(id)` / `id.toString()` / 字符串拼接**：record 的 `toString()` 是 `OrgUnitId[value=…]`，换实现（long → UUID）时会静默改变对外形状。
- 响应 record 的 ID 字段类型优先直接用 ID 的值类型（由 OpenAPI 生成器声明格式），而不是自己转字符串。契约以 springdoc 实际输出为准，版本升级走 ADR；错误响应为 RFC 9457 problem details（用户 2026-10-15 采纳，ADR-0041，FRAMEWORKS §9（intent 195827））。
- 守护（待实现：G-C1）。现状：`organization/workforce/web/*Response.java` 中有 `String.valueOf(view.orgUnitId())` 一类写法，须按 R-1 一并核对。

**CS-2.21 不靠 ID 的顺序表达业务顺序**（新增；R-1a 复审（PR #125 正文）H-2）
- UUIDv7 大致按时间递增、自增 ID 按插入递增，都**不是业务事实**：同毫秒、时钟回拨、批量导入、迁入数据都会打乱它。需要顺序时用显式序号列（`_number` / `_order`，在同一事务里取号）或「业务时间 + `id`」：业务时间决定顺序，`id` 只作并列时的稳定兜底（游标分页的排序键同理，NAMING_STANDARD §2.4a 第 2 条）。
- 禁止：`ORDER BY id` 表达「先后」、取 `max(id)` 当「最新」、比较两个 ID 大小判断先后、测试断言依赖 ID 顺序。
- 守护（待实现：G-C2）+ 复审。

**CS-2.22 值对象、record 与不可变性**（汇总 + 新增）
- 值对象是 `@ValueObject` record，校验放在紧凑构造器中，非法值抛 `IllegalArgumentException`（只表示编程错误，对外输入先由 web 边界校验，见 CS-2.24）（§14 第 7 项；守护：G21）。
- record 中不放可变状态：集合组件在构造器里 `List.copyOf` / `Set.copyOf` / `Map.copyOf`，不放数组和 `Date`（守护：G22；集合拷贝为新增，守护待实现：G-C3）。
- 聚合与实体不是 record：它们有身份和生命周期，状态只经领域方法改变（CS-2.4）；字段 `private`，不提供 setter。
- `api` / `events` 只放 interface、record、enum、类型化 ID（守护：G2、G5、G32）。
- 常量 `static final` 且不可变；不用可变 `public static` 字段（NAMING_STANDARD §1 老代码问题）。

**CS-2.23 空值**（新增，按现有写法定边界）
- 默认非空：domain、application、api 的参数、返回值、record 组件都不为 `null`；构造处 `Objects.requireNonNull(x, "x")`。
- 「可能找不到」的单值查询返回 `Optional<T>`（现有用例 `Optional<OrgUnitDetailView> execute(...)` 等）；`Optional` **只用作返回值**，不作字段、参数、record 组件（响应 record 除外，见下）、集合元素。
- 集合返回空集合，不返回 `null`；分页返回空页。
- 响应 record 中确需「无值」的组件标 `@Nullable`（生成的契约随之声明 nullable）；统一用 `org.jspecify.annotations.Nullable`（现有 33 个文件用 JSpecify、8 个文件用 `org.springframework.lang.Nullable`，见 §9 冲突 C-9）。
- 守护：`NullableResponseGuardTest`（装箱类型组件须 `@Nullable`）；其余守护（待实现：G-C3）。

**CS-2.24 异常分类**（汇总 + 新增）
- 业务拒绝：抛 `kernel.DomainException`（或其子类），带本模块错误码枚举（实现 `kernel.ErrorCode`，格式 `{SUBDOMAIN}_{BC}_{REASON}`）与 `ErrorKind`；`platform.web.GlobalExceptionHandler` 统一转成 ProblemDetail。链路：领域异常 → ErrorCode → ProblemDetail（§14 第 10 项；守护：G48、`ErrorCodeGlossaryTest`）。
- **通用错误码只在 `kernel` 定义**（状态转换不合法、被他人同时修改、必须填写原因、已过撤回截止点、未登录等），模块只定义本模块特有的错误，不得与通用或彼此同义（NAMING_STANDARD §2.3、G48；用户 2026-10-15「通用错误码只在公共基础定义」；守护：G48、R-5）。
- 异常 message 写给日志；调用方看到的只有错误码、类别与异常自己声明可展示的扩展字段（`DomainException` 约定）。message 中不放敏感值（CS-6.2）。
- web 边界的输入校验失败（缺字段、格式不对、取值越界）用 `kernel.InvalidRequestException`（400），在进入值对象之前拦下；值对象构造器抛的 `IllegalArgumentException` 只表示编程错误（调用方没先校验），不作为对外的业务拒绝。
- 编程错误与不变式破坏用 `IllegalArgumentException` / `IllegalStateException` / `NullPointerException`，**不**用它们表达业务拒绝；外部系统不可用用 `kernel.ExternalSystemUnavailableException`。
- 不吞异常：`catch` 后要么转换为上面的类型再抛，要么记录并按规则卡明确的降级处理；空 `catch` 块禁止（新增；守护待实现：G-C4）。

**CS-2.25 事务边界**（汇总）：`@Transactional` 只在 application 用例上；一个用例一个聚合（CS-2.3）；事务内不调外部系统、不发 HTTP、不跑批量计算，外部调用走发件箱与可靠投递（GUIDING_PRINCIPLES §7）；业务编号在本用例事务内取号（NAMING_STANDARD §3）。守护：G11、`SingleAggregateGuardTest`；事务内外部调用靠复审。

**CS-2.26 时间只经注入 `Clock`**（汇总）：业务代码不调用 `now()`、`Clock.system*`、`System.currentTimeMillis`；domain 不持有 `Clock`，时刻与业务日由 application 传入；业务日按 `kernel.BusinessZone`；SQL 字符串与迁移不读数据库时钟（`now()`、`current_timestamp`、时钟类 `DEFAULT`），时刻由应用显式写入（NAMING_STANDARD G25、§2.4a 第 3 条；R-4）。守护：`TimeSourceGuardTest`、`EventBusinessDateGuardTest`；迁移文件扫描随 R-4 实现。例外：Spring Session 框架表（会话表）的过期比较走 `identity.infrastructure.persistence.SessionStoreClock`，因框架无时钟注入点；随 C1 自写会话仓库撤销；守护 `TimeSourceGuardTest`。

**CS-2.27 金额只经 `Money`**（原则；K-1 金额内核进行中，实现以 K-1 为准）：金额总是「数值 + 币种」，kernel 以外不出现裸 `BigDecimal` 金额字段；不用 `double` / `float` 表示金额或比率；舍入口径集中在金额内核一处，领域代码不自行 `setScale` / `divide` / 指定 `RoundingMode`（K-1 守护）；拆分与分摊守恒（各份之和等于原额）且确定；跨币种运算拒绝；接口上金额为十进制字符串加币种（G-M5）。守护：G17、G24；K-1 新守护随 K-1 交付。数量同理用 `Hours` 等值对象（G23）。

**CS-2.28 依赖注入与可见性**（汇总 + 新增）
- 只用构造器注入（单构造器不写 `@Autowired`），依赖字段 `private final`（守护：G18 禁字段注入）。
- web 与 infrastructure 的类包内可见（守护：G6）；其余类默认也用包内可见，只有确需被别的包使用的才 `public`（`api` / `events` 类型、用例类、domain 中被 application 使用的类型）（新增；复审）。
- 配置用 `@ConfigurationProperties("forcitis.<子域>.<bc>…")` 的 record 注入，不在代码里散读 `Environment`；配置键前缀由 G46 检查。
- 不用静态可变单例、不在静态初始化里读配置或时间。

**CS-2.29 领域层不引用外部系统类型**（新增）：外部来源类型（`platform.integration.api` 中的 `Source*`，如 `SourceUnitId`、`SourcePersonId`、`SourceOrgSnapshot`）只在 `platform.integration` 与消费方模块的**防腐翻译处**出现；业务模块的聚合、实体、值对象、`api`、`events` 不持有、不暴露它们，翻译后只用自有类型与自有枚举（DATA_DESIGN_CHECKLIST §14.1 D16「外部 ID 只作来源对照列……只在同步防腐层使用」；§14 第 8 项）。守护（待实现：G-C5）。现状：`organization/workforce/domain/` 的 `SourceTerms`、`SnapshotReconciler`、`EmployeeChecks` 引用 `Source*`，须由用户或调度决定翻译处的位置（§10 Q-4）。

## 3. SQL 与迁移

| # | 规则 | 出处 | 检查 |
|---|---|---|---|
| CS-3.1 | schema、表、列、约束、索引、Flyway 文件命名 | NAMING_STANDARD §2.3、§2.4 | 守护：`DdlNamingTest`、`NamingSurfaceTest`（G44）、`GlossaryTest`（G37–G39） |
| CS-3.2 | DDL 设计十条：业务唯一约束、外键与查询路径索引、空值与默认值（只许技术默认值）、删除语义、代码格式与文本长度、`text + CHECK` 枚举、`jsonb` 边界、大表分区规划、上线后迁移安全、高频更新行 | NAMING_STANDARD §2.4a；DATA_DESIGN_CHECKLIST §14.1 D18 | 守护：`DdlNamingTest` / `DataArchitectureTest`（按各条）；其余复审 |
| CS-3.3 | **数据设计以 DATA_DESIGN_CHECKLIST §14.1 全部条目为准** | [DATA_DESIGN_CHECKLIST §14.1](DATA_DESIGN_CHECKLIST.md#141-数据架构检查清单用户-2026-10-14问这些表符合olapoltpdama的核心思想吗这么检查对吗后选做并把能自动化的变成守护-recommended) 全部条目 | 守护：`DataArchitectureTest`、`DdlNamingTest`、`PrimaryKeyTypeGuardTest`；其余复审 |
| CS-3.4 | **自有表主键 UUIDv7**，由 `kernel.IdGenerator` 生成；外部 ID 只作来源对照列；外部代码在防腐层映射为自有枚举 | DATA_DESIGN_CHECKLIST §14.1 D16 | 守护：`PrimaryKeyTypeGuardTest` |
| CS-3.5 | **迁移只追加**；首次生产部署前基线开放期内直接改基线，每个改表 PR 附「预期表结构 diff」（两个空库 `pg_dump --schema-only` 对比），opus 核对；删除开放标记单独成 PR | METHODS M3「迁移基线」 | 守护：`scripts/ci/migrations_immutable.py`；复审（diff） |
| CS-3.6 | SQL 只在 `infrastructure.persistence` 的 `Jdbc*Repository` 中，用 `JdbcClient` 与类型化手写映射，不用反射 ORM；只访问本模块 schema；持久化类与访问的表登记在 `architecture/data-access-registry.yaml` | NAMING_STANDARD G10、§3；GUIDING_PRINCIPLES §10；SECURITY_INVARIANTS I-10 | 守护：G10、`DataArchitectureTest`（注册表与 SQL 对照）、`WorkforceSchemaGuardTest` |
| CS-3.7 | **参数一律绑定**（`:name` 参数），不把值拼进 SQL 文本；动态排序列、动态表名只能取自代码内的白名单枚举（新增） | 新增（SEC 类缺陷的预防） | 守护（待实现：G-C6） |
| CS-3.8 | SQL 不读数据库时钟；DDL 不写时钟默认值 | R-4；NAMING_STANDARD §2.4a 第 3 条 | 守护：`TimeSourceGuardTest`（SQL 字符串；迁移扫描随 R-4） |
| CS-3.9 | 已过账事实、审计、版本表只追加；更正靠冲销或新版本 | METHODS M3「已过账事实」；ADR-0016 | 守护：G34、`DataArchitectureTest`（D8） |
| CS-3.10 | 列表查询只用索引覆盖的游标分页（`cursor` + `limit`，默认 50、上限 200），不内存分页、不全表扫描；排序键以 `id` 兜底（CS-2.21） | GUIDING_PRINCIPLES §7；NAMING_STANDARD §2.4a 第 2 条；NEXT_BATCH_PLAN K-3 | 复审（EXPLAIN 抽查） |
| CS-3.11 | 建库脚本（C-3、C-9）与参考 DDL、迁移目录的 schema 清单一致 | `db_scripts_aligned.py` 说明 | 守护：`scripts/ci/db_scripts_aligned.py` |
| CS-3.12 | 交易表上不跑分析；汇总、趋势走 `reporting` | DATA_DESIGN_CHECKLIST §14.1 D9；ADR-0025 | 复审 |
| CS-3.13 | **按中文排序的 SQL 显式写 `COLLATE "zh-x-icu"`**：库的默认排序规则是内建 C.UTF-8（中文按码位排序，测试库与 dev 库一致，DDL-11），需要拼音顺序的 `ORDER BY` 自己带上；键集分页的排序键若带该 `COLLATE`，游标比较与索引也用同一个 | CROSS_CUTTING_DECISIONS XD-03；CROSS_CUTTING_MATRIX CC-I01、G-X2 | 复审（不做 SQL 守护，K-3a 裁定第 5 条） |

## 4. 前端（React + TypeScript；H5 为 Taro）

| # | 规则 | 出处 | 检查 |
|---|---|---|---|
| CS-4.1 | **严格类型**：`strict`、`noUncheckedIndexedAccess`、`exactOptionalPropertyTypes`、`verbatimModuleSyntax`；禁 `any`；不用非空断言 `!` 绕过可空（新增） | `frontend/tsconfig.base.json`；NAMING_STANDARD F7；GUIDING_PRINCIPLES §10 | 守护：`pnpm typecheck`、F7；非空断言（待实现：G-C7） |
| CS-4.2 | **接口调用只经生成的客户端**：禁 axios、`fetch`、`XMLHttpRequest`、手写 `/api` URL；只有 `modules/<m>/api` 能 import `@forcitis/api`；类型由 OpenAPI 生成，不手写接口类型 | NAMING_STANDARD §2.3、F5、F10 | 守护：F5、F10、`pnpm check:generated` |
| CS-4.3 | 模块边界：`app → modules(index) → shared` 单向；跨模块只经 `modules/<m>/index.ts`；禁 `export *`；`model/` 与 `shared/lib` 为纯逻辑 | F1–F4、F9、F12、F13 | 守护：ESLint、`pnpm check:structure`（F13） |
| CS-4.4 | 命名：ASCII 标识符、文件 kebab-case、组件 PascalCase、hook `useXxx` 且在 `hooks/` | F6、F8 | 守护：ESLint、`pnpm check:structure` |
| CS-4.5 | **界面文字取词表**：业务名词用 `terms.yaml` 的 `zh`，同一概念全站同一叫法；非业务词登记在 `frontend/scripts/copy-allow-list.yaml` | NAMING_STANDARD §7 第 16 项；METHODS M5「第二批」第 5 条 | 守护：`pnpm check:copy` |
| CS-4.6 | **错误提示**：只经 `@forcitis/core` 的 `describeFailure`；每个 `ErrorCode` 在 `error-messages.ts` 中决定文案或明确为 `null`（用 HTTP 状态的通用文案）；页面不直接显示服务端 `detail`、异常 message 或英文；不吞错误，失败要让用户看到可操作的提示 | `packages/core/src/failure.ts`、`error-messages.ts`（现有写法）；CS-1.7 | 守护：`pnpm typecheck`（`Record<ErrorCode, …>` 穷尽）；页面直接读 message（待实现：G-C7） |
| CS-4.7 | 设计系统：业务模块不直接 import antd 或 Taro 原语；禁内联样式与硬编码颜色；令牌只在 `packages/design-system` 读取 | F14；ADR-0013 | 守护：ESLint、stylelint |
| CS-4.8 | 安全：禁 `dangerouslySetInnerHTML`；外部链接经 `safe-url` | F11；SEC-008 | 守护：F11；`safe-url` 靠复审 |
| CS-4.9 | 不用 `console.log`，用共用 logger | `frontend/eslint.config.js` | 守护：ESLint |
| CS-4.10 | 日期时间与业务日只经 `@forcitis/core` 的 `format-date-time`、`business-day`；金额格式化按 K-1 交付的共用函数（G-M6） | 现有写法；NEXT_BATCH_PLAN K-1 | 复审；G-M6 随 K-1 |
| CS-4.11 | H5 与 PC 同一套规则 | NAMING_STANDARD §7 第 17 项 | 守护：ESLint（H5 已接 F14）；其余复审 |
| CS-4.12 | **`localeCompare` 必带 `'zh'`，`Intl.DateTimeFormat` 必带 `timeZone`**：中文按拼音排序，界面时间不随浏览器的区域和时区变 | CROSS_CUTTING_MATRIX G-X2、CC-T02、CC-I01 | 守护：ESLint（`no-restricted-syntax`，`scripts/check-lint-config.mjs` 自测） |

## 5. 测试

| # | 规则 | 出处 | 检查 |
|---|---|---|---|
| CS-5.1 | **以模块级集成测试为主**：走 HTTP → 鉴权 → 用例 → 领域 → 真实 PostgreSQL（Testcontainers，镜像以 CI 为准，现为 `postgres:18`）；不 mock 自己的模块，外部系统用 WireMock 或替身隔离；单元测试只补边界（值对象、计算、决策表） | GUIDING_PRINCIPLES §9 | 复审 |
| CS-5.2 | **测试名引用依据编号**：`@DisplayName` 以规则 / 差异 / ADR / 复审 / intent 编号开头（现有写法，如 `@DisplayName("RULE-356 …")`、`"ADR-0027 …"`）；`contract.md` 每条与 ADR 本阶段交付条款都对应至少一个测试 | NAMING_STANDARD §5；METHODS M4、附 M1 第 2、4 条、TEAM-69；G-T4 | 复审；对照守护 G-T4 待实现 |
| CS-5.3 | **验收测试与实现分开**：测试代理先写并提交验收测试，实现代理只让它转绿、不得修改；目录约定与提交 trailer 见出处 | `roles/design.md` 职责「独立验收测试」、`backend/src/test/java/com/forcitis/one/acceptance/README.md` | 守护：`scripts/ci/acceptance_guard.py`、`acceptance_rulings.py`（裁定尾注须对应已登记裁定）、`acceptance_reports.py` |
| CS-5.4 | **不为通过测试而放宽或删除断言** | [METHODS M5「第二批」第 2 条](METHODS.md#m5-写代码规则coding_standard-引此为出处) | 守护：`scripts/ci/assertion_drift.py`（只警告）；PR 模板；复审 |
| CS-5.5 | **测试只断言本模块的效果** | [METHODS M5「第二批」第 7 条](METHODS.md#m5-写代码规则coding_standard-引此为出处)；DATA_DESIGN_CHECKLIST §14 G-T3 | 守护：`TestIsolationGuardTest` |
| CS-5.6 | **确定性等待，不写宽泛的 `never()`**；另：测试不用 `Thread.sleep` 等时间（新增，现有 6 处） | [METHODS M5「第二批」第 7 条](METHODS.md#m5-写代码规则coding_standard-引此为出处)；`Thread.sleep` 一句为本文件新增 | 守护（待实现：G-C8） |
| CS-5.7 | **测试不写密钥字面量** | [METHODS M5「第二批」第 6 条](METHODS.md#m5-写代码规则coding_standard-引此为出处) | 守护：gitleaks（pre-commit、CI） |
| CS-5.8 | 测试用固定 `Clock` 与固定 `IdGenerator`，不依赖运行时刻与 ID 顺序（CS-2.21、CS-2.26） | 新增（由 CS-2.21、CS-2.26 推出） | 复审 |
| CS-5.9 | **新守护带负向样例**：每条守护有「注入错误被拦下」的测试，失败信息写明「放哪里 / 用什么名字」；例外清单每行附理由，新增例外须复审 | NAMING_STANDARD §4「负向验证」、§5；现有守护的 `injection:` 测试 | 复审 |
| CS-5.10 | **复审产出失败测试**：Blocker / High / Medium 尽量给出可复现的失败测试，测试名引用复审编号；修复以测试转绿为准 | RULES ⑤ | 复审 |
| CS-5.11 | 修复循环只跑受影响模块；交付前与并入整合分支时跑全量与全部守护 | METHODS M5「第二批」第 9 条 | `scripts/precheck.sh` 给出命令；CI 全量 |

## 6. 日志、安全与密钥

| # | 规则 | 出处 | 检查 |
|---|---|---|---|
| CS-6.1 | 日志用 SLF4J，输出 ECS 结构化 JSON（`logging.structured.format.console=ecs`），上下文带 `correlationId`、`userId`；结构化日志键 camelCase、业务键取词表；不用 `System.out`、JUL | `backend/src/main/resources/application.properties`；NAMING_STANDARD G18、§7 第 20 项 | 守护：G18；键名复审 |
| CS-6.2 | `RESTRICTED`（口令、密钥、会话内容）不进日志、不进异常 message、不出库；`SENSITIVE`（个人信息、工资日薪、银行卡）日志中只记 ID 不记明文 | NAMING_STANDARD §2.5 | 复审；守护（待实现：G-C9） |
| CS-6.3 | **测试不写密钥字面量**（与 CS-5.7 同一条，此处为密钥视角的索引） | [METHODS M5「第二批」第 6 条](METHODS.md#m5-写代码规则coding_standard-引此为出处) | 守护：pre-commit、CI gitleaks、`scripts/precheck.sh` |
| CS-6.4 | **密钥不进会话记录** | CLAUDE.md 底线 | 复审 |
| CS-6.5 | 凭据原值只在 `~/.modernize/psa/`（权限 600）或 KMS，不进任何可共享文件 || 守护：gitleaks；复审 |
| CS-6.6 | 身份、授权、单人模式防线遵守 `phase1a/SECURITY_INVARIANTS.md` 的 I-1…I-13；这类改动不交给 Codex | SECURITY_INVARIANTS| 守护：各不变量对应测试；复审 |
| CS-6.7 | 生产配置不含测试值、固定验证码或 mock 取值 | SECURITY_INVARIANTS I-4 | 守护：`ProductionConfigSanityTest`（按 SECURITY_INVARIANTS） |
| CS-6.8 | **建账号、起容器、对外调用前先问用户**（Testcontainers 本机临时测试容器除外） | [METHODS M5「第二批」第 10 条](METHODS.md#m5-写代码规则coding_standard-引此为出处)；TEAM-92 | 复审 |
| CS-6.9 | **生产取证不逐次请示（两条底线）** | CLAUDE.md 底线、ADR-0037 第 7 项 | 复审 |

## 7. 交付前自查与 PR

| # | 规则 | 出处 | 检查 |
|---|---|---|---|
| CS-7.1 | **交付前运行 `scripts/precheck.sh`**（默认基线 `origin/main`，可传参数），全部 PASS 或有理由的 SKIP 才开 PR；输出贴进 PR「precheck.sh 输出」一栏。脚本会执行分支自带的检查脚本，复审方在不可信分支上不运行 precheck，以 CI 为准 | 用户「A–D 全做」之 C（intent `2026-10-15-014844-one-coordinator-…`） | PR 模板 |
| CS-7.2 | PR 模板各栏如实填写：改动的断言 / 测试及理由、新增术语、OpenAPI 与架构文档同步、验收测试 trailer、precheck 输出 | `.github/pull_request_template.md`| 复审（调度合并前核对） |
| CS-7.3 | **只改卡上文件**，越界先报告；缺陷修复、功能补做、结构性重构分开派工、分开提交；缺陷修复手术式（最小改动 + 回归测试），不顺手重构、改名、统一风格 | METHODS M5「缺陷修复手术式」 | 复审；派工卡模板 `templates/WORK_CARD.md` |
| CS-7.4 | **记录只改片段**：`INTENT.md`、`SESSION_LOG.md`、`STATUS.md`、`adr/ADR-*.md`、`adr/README.md`、brief 由 `build_all.py` 生成；用 `new_intent.py --session=<会话名> --category=<类别> --owner=<负责会话> --paths=<路径前缀|无> --supersedes=<被取代片段|无>`（四项必填，用法见脚本头）、`new_log.py <会话名>` 新增片段（文件名由脚本按时刻与会话名生成，不手工起名），改完运行 `build_all.py`；INTENT / SESSION_LOG / STATUS、ADR 拷贝与索引、brief 等生成产物都不入仓（只提交片段或源；本地运行 `build_all.py` 或看 generated-docs 分支）；不直接编辑生成产物 || 守护：`build_all.py --check` |
| CS-7.5 | **变更治理自查**：已采纳 ADR 只改状态行；新 ADR 带四个必备小节；代码类改动先有 `RULE_REVIEWS.json` 结论；配置变更登记版本与生效日期 |附 M3 | 守护：`adr/build_adrs.py`（经 `build_all.py`）；复审 |
| CS-7.6 | **并行子代理同一基线出发** | [METHODS M5「第二批」第 9 条](METHODS.md#m5-写代码规则coding_standard-引此为出处) | 复审 |
| CS-7.7 | **复审、入队与报告**：按执行；代码首轮 opus，一般改后由作者交 Codex 复核，安全与生产改动修后 opus 终审；调度核准清单，one-merge 经 mq.py 人工入队、合入并登记执行会话，调度逐批审计；自动入队永久关闭，评论只留痕与核 head；文档由合集自扫，常规状态只在四节点上报，不单报提交号 || CI；调度核对 |
| CS-7.8 | 提交说明中文，格式 `type(scope): 摘要`，scope 用子域 slug；验收测试 trailer 见 CS-5.3 | NAMING_STANDARD §7 第 21 项 | 复审 |
| CS-7.9 | **IntelliJ 构建**：构建进程不读 `backend/.mvn/jvm.config`，Error Prone 会因 `IllegalAccessError` 加载失败；在 Settings → Build, Execution, Deployment → Compiler → Shared build process VM options 里加入与该文件相同的 `--add-exports` / `--add-opens` 参数，或改用 Maven 构建 | ADR-0033 守护 14；`backend/.mvn/jvm.config` | 复审 |

## 8. 待实现守护设计（G-C 系列）

本节只写设计，不在本次实现。实现时各自开 PR（结构性重构批或守护批），带负向样例（CS-5.9）；落地前已有违规的，按 `ddl-naming-baseline.txt` 的做法登记基线文件，新违规与「已修好却没删」的基线行都失败。

| 编号 | 对应规则 | 实现要点 |
|---|---|---|
| G-C1 | CS-2.20 ID 输出用 `value()` | ArchUnit：扫描 `web`、`application`、`infrastructure` 中对 `String.valueOf(Object)`、`Object.toString()`、`StringBuilder.append(Object)`、字符串拼接（`StringConcatFactory` 调用的参数类型）的调用，参数静态类型是带 `@ValueObject` 且只有一个名为 `value` 组件的 record 即违规。再加一条集成断言：OpenAPI 导出中不出现 `[value=` 样式的示例，事件 JSON 序列化后 ID 字段为标量 |
| G-C2 | CS-2.21 不靠 ID 顺序 | 文本扫描 `infrastructure.persistence` 中的 SQL：`ORDER BY` 的**第一个**排序键是 `id` 或 `*_id`、或出现 `max(id)` / `min(id)` 即违规；排序键里 `id` 只允许出现在业务时间或序号之后。ArchUnit：domain / application 中对类型化 ID 或 `UUID` 调用 `compareTo`、或把它们放进 `Comparator.comparing` 即违规 |
| G-C3 | CS-2.22、CS-2.23 不可变与空值 | ArchUnit：record 组件类型为 `List` / `Set` / `Map` 的，紧凑构造器字节码中须有对应 `copyOf` 调用；domain / application / api 的方法不得 `return null`（字节码 `ACONST_NULL` + `ARETURN`，排除 `@Nullable` 返回）；`Optional` 不得作字段、参数类型、集合元素（`web` 响应 record 例外）；禁 import `org.springframework.lang.Nullable`（统一 JSpecify，见 §9 C-9） |
| G-C4 | CS-2.24 异常 | ArchUnit：domain / application 不得 `new RuntimeException` / `new Exception`；业务模块中抛出的非 kernel 异常须是 `DomainException` 子类或 JDK 的编程错误类；源码扫描空 `catch` 块（`catch (...) {}` 且块内无语句） |
| G-C5 | CS-2.29 领域不引用外部类型 | ArchUnit：除 `platform.integration..` 与登记的防腐翻译包外，任何 `..domain..`、`..api..`、`..events..` 不得依赖 `platform.integration.api.Source*`；翻译包清单与现有违规登记在 `backend/architecture/source-type-exceptions.txt`（新文件，只减不增） |
| G-C6 | CS-3.7 SQL 参数绑定 | 源码扫描 `Jdbc*Repository`：传给 `JdbcClient.sql(...)` / `JdbcTemplate` 的字符串参数不得由非 `static final` 变量拼接；允许拼接的只有代码内枚举或 `static final` 常量 |
| G-C7 | CS-4.1、CS-4.6 前端 | ESLint：`@typescript-eslint/no-non-null-assertion: error`；`no-restricted-syntax` 禁止在 `pages/`、`components/` 中访问 `.message` / `.detail`（`MemberExpression[property.name=/^(message|detail)$/]` 且对象来自 `catch` 参数或 `error`），提示改用 `describeFailure` |
| G-C8 | CS-5.6 确定性等待 | 源码扫描 `src/test/java`：禁 `Thread.sleep`（测试支撑类中的轮询辅助登记例外）；`never()` 必须与具体方法和参数匹配器同用，`verifyNoInteractions` / `verifyNoMoreInteractions` 需登记理由；现有 `Thread.sleep` 6 处入基线 |
| G-C9 | CS-6.2 日志不含敏感值 | ArchUnit：`Logger` 的 `info/warn/error/debug` 调用参数中不得出现类型或字段名命中 `data-classification.txt` 中 `SENSITIVE` / `RESTRICTED` 列（按 snake → camel 映射）的取值；补一条集成测试：用固定敏感值跑登录与同步流程，断言日志输出不含该值 |

## 9. 冲突与重复

只列出、给建议，不在本次改原规则的语义。编号 C-n，处理须用户或调度决定（建议项标「建议」）。

| 编号 | 内容 | 建议取舍 |
|---|---|---|
| C-1 | `CLAUDE.md` 写「DATA_DESIGN_CHECKLIST §14、§14.1 D1–D16」（旧版），§14.1 现已到 D17（D18 在途） | 建议：引用写「§14.1 全部条目」，不写区间，免得每加一条就漂移（本次精简 CLAUDE.md 已这样改，未改规则含义） |
| C-2 | 迁移规则三处写法：§12.2 表「Flyway 只追加」、§12.4 表「Flyway 迁移：只新增脚本」、§4 第 6 步「Flyway 只新增文件」与同节「首次部署前的迁移基线」（开放期直接改基线）并存 | 建议：§12.2 / §12.4 / §4 的「只追加」后加「（基线开放期内按『首次部署前的迁移基线』）」，以开放标记为准；`migrations_immutable.py` 已按此实现 |
| C-3 | NAMING_STANDARD §2.4 `_id` 类型「`bigint`、`uuid`」、`_by` 类型 `bigint`，与 §14.1 D16「所有自有表主键 UUIDv7」及 R-1（ID 改 UUID）不一致 | 建议：R-1 合入时同步改 §2.4：`_id` 为 `uuid`（外部来源对照列 `text`），`_by` 随 `UserId` 类型 |
| C-4 | Testcontainers 版本：GUIDING_PRINCIPLES §9 与 NAMING_STANDARD 序言写 PG17，CI 与测试实际用 `postgres:18` | 建议：以 CI 为准，两处改为「镜像以 CI 为准」，不写死版本号 |
| C-5 | GUIDING_PRINCIPLES §9「架构测试：守护 G1–G33 和前端 F1–F13」已过时（现 G1–G48、F1–F14 及多项专用守护） | 建议：改为「NAMING_STANDARD §4 与本文件各节所列守护」 |
| C-6 | NAMING_STANDARD §2.1「唯一来源是 `docs/domain/00_战略设计/03-通用语言.md` 的代码名列」与 §7、ADR-0018、CLAUDE.md「以 `terms.yaml` 为准」冲突 | 建议：§2.1 改为 `glossary/terms.yaml`（ADR-0018 已定），原文件只作历史来源 |
| C-7 | NAMING_STANDARD §7 第 16 项「前端界面文案：无守护 ○」已过时，现有 `pnpm check:copy`（`check-copy-glossary.mjs`）与后端 `OutwardTextGuardTest` | 建议：状态改 ✔ 并写守护名 |
| C-8 | 测试命名：NAMING_STANDARD §5 写方法名 `RULE_012_clauseTotal…`，§4 计划「以 `RULE_NNN_` 开头的测试」；现有代码统一用 `@DisplayName("RULE-356 …")`；METHODS M4 写「测试名或注解引用」 | 建议：以 `@DisplayName` 开头引用编号为准（现状、可读、支持 D / ADR / 复审编号），G-T4 守护按 `@DisplayName` 前缀实现；§5 示例同步改 |
| C-9 | `@Nullable` 两套：`org.jspecify.annotations.Nullable`（33 个文件）与 `org.springframework.lang.Nullable`（8 个文件，`platform.approval.web` 与 `OpenApiRequiredProperties`）；`NullableResponseGuardTest` 只认 JSpecify | 建议：统一 JSpecify（Spring 7 本身已转 JSpecify），G-C3 禁 Spring 的注解；8 个文件走结构性重构批 |
| C-10 | 覆盖率门槛：GUIDING_PRINCIPLES §9「行 ≥ 80%、分支 ≥ 70%」，CI `backend_verify.sh` 为 `-Djacoco.skip=true`（「nothing in CI reads coverage」），门槛无人检查 | 待决：恢复覆盖率门槛进 CI，或在 GUIDING_PRINCIPLES 写明「复审参考，不作门槛」 |
| C-11 | 分支命名：旧版 与 NAMING_STANDARD §7 第 21 项写 `feature/issue-{N}-…`，实际会话分支为 `claude/one-*` worktree 分支 | 建议：两处改为「会话 worktree 分支（`claude/<会话名>-<主题>`）」（TEAM-86 已定；NAMING_STANDARD 第 21 项已同步） |
| C-12 | `CLAUDE.md` 与 METHODS M5「第二批」逐条重复（11 条），措辞略有差异 | 本次已处理：CLAUDE.md 保留最硬的条目并指向本文件；第二批原文仍在 METHODS M5「第二批」，本文件引用 |
| C-13 | CLAUDE.md「数据设计有自己的规则……所有自有表主键 UUIDv7……」与 §14.1 原则段、D16 重复 | 本次已处理：CLAUDE.md 只留一句指向，正文以 §14.1 为准（CS-3.3、CS-3.4） |
| C-14 | NAMING_STANDARD §2.3「Flyway 描述中文说明写在脚本首行注释」与 CS-1.1「注释能不写就不写」有张力；表与列含义已由 `COMMENT ON` 承载（D3、D10） | 建议：首行注释只写「为什么」（如基线合并来源），含义一律写 `COMMENT ON` |
| C-15 | GUIDING_PRINCIPLES §10 例子 `TaskPackageId` 已改名为 `WorkPackage` 词族（NAMING_STANDARD §4 负向验证注） | 建议：随下次 GUIDING_PRINCIPLES 修订更正 |
| C-16 | DATA_DESIGN_CHECKLIST §14 第 1 项「一个事务只修改一个聚合」与 G-T1「一个 `@Transactional` 应用服务只写一个聚合的仓储」重复 | 不冲突；本文件合并为 CS-2.3 |
| C-17 | F14 路径：NAMING_STANDARD §4 F14 写「原始令牌只在 `shared/design-system` 中读取」，实际与 `eslint.config.js` 是 `packages/design-system`（`tokens.css` 只在该包内读取） | 建议：NAMING_STANDARD F14 改为 `packages/design-system` |

## 10. 待决问题

| 编号 | 问题 | 建议 |
|---|---|---|
| Q-1 | 本文件标「新增」的规则（CS-2.20–CS-2.29 中的新增部分、CS-3.7、CS-4.1 非空断言、CS-4.6 页面不读 message、CS-5.6 禁 `Thread.sleep`、CS-5.8、CS-7.8 格式；CS-7.1 已由用户「A–D 全做」覆盖，不在此列）是否采纳 | 建议采纳；采纳后记 intent |
| Q-2 | §0 的优先级顺序（用户原话 > 已采纳 ADR > 规格与规则卡 > 三份规范 > 现有代码）是否认可 | 建议认可 |
| Q-3 | G-C1…G-C9 的实现时机与归属：并入 R-1（G-C1、G-C2 与 ID 改 UUID 同批最省）、K-3 平台共用件，或单开守护批 | 建议 G-C1 / G-C2 随 R-1，G-C3 / G-C4 / G-C8 单开守护批（sonnet 执行、opus 复审），G-C5 待 Q-4，G-C6 / G-C9 随安全类缺陷卡，G-C7 随 W3 前端 |
| Q-4 | `organization.workforce.domain` 中 `SourceTerms`、`SnapshotReconciler`、`EmployeeChecks` 引用 `Source*`：把翻译移到 `application`（或新的防腐翻译包），还是登记为同步防腐层例外 | 建议移到 application 层的翻译类，domain 只见自有类型；走结构性重构批 |
| Q-5 | §9 的 C-2…C-11、C-14、C-15 是否按建议更正原文（属规则文字修订，按 §12 由负责会话起草、调度合并） | 建议一次性更正，单独 PR |
| Q-6 | C-10 覆盖率门槛：恢复进 CI，还是改为复审参考 | 待用户决定 |

