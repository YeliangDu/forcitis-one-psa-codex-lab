# 命名、包结构与架构守护规范 — FORCITIS one 现代化

日期：2026-10-14 · 适用范围：`reimagine` 产出的全部新代码（后端、PC、H5、数据库、事件、API）

用户诉求原话：
- 「可以在现代化过程中规范命名吗？将原代码的命名和编码规范化」
- 「命名要符合英文、行业、规范的习惯，agent 开发友好。而且要考虑 archunit 和包结构来保证目录结构不会让 agent 犯错。」
- 「一些基本的原则：深模块、窄接口，要给 agent 约束，避免错代码」

**结论**：新系统是重建（reimagine），老代码的名字和目录一律不沿用。规范的落地分三层：

1. **词源**：每个英文名都来自词表。
2. **结构**：用固定的包结构决定代码「放哪里」。
3. **守护**：用 Modulith 和 ArchUnit 让放错位置、起错名字、越过边界的代码在 CI 中失败，失败信息里直接写明该怎么改。

第 3 层已实证：`architecture-guardrails/backend` 是可运行的模板工程（Boot 4.1.1 / Java 25 / Modulith 2.1.1 / Flowable 8 / Flyway / Testcontainers PG17 / ArchUnit 1.4.2），52 项测试全绿，注入的错误全部被拦下；`architecture-guardrails/frontend` 对应前端（ESLint + TS strict），注入 16 类错误全部被拦下。决策见 `adr/ADR-0003`、`ADR-0004`、`ADR-0005`、`ADR-0006`。

## 0. 基本原则

| 原则 | 含义 | 怎么落地 |
|---|---|---|
| **深模块** | 每个模块（= 一个 BC）把复杂度藏在内部：工时的费率锁定、人天折算、行级审批，都在 `timesheet` 内部完成，调用方只看到一个结果 | 模块内部（domain / application / infrastructure / web）对外不可见，由 Modulith 校验 |
| **窄接口** | 模块对外只有两个出口：`api`（查询或命令的接口 + record）和 `events`（已发生事实的 record） | 规则：`api` 只放接口和 record；签名不得出现内部类型；每个模块的 api 方法总数 ≤ 12（超出说明应加深模块，而不是加宽接口） |
| **对 agent 的约束** | 每个概念只有一个名字、一个位置。规则失败时直接说明「为什么」和「放哪里」 | 规则文本里写 `because(...)`；每个模块带 `package-info.java` 说明职责、拥有的 schema 和允许的依赖；仓库里配「代码放哪里」速查表（§3） |
| **防错代码** | 让错误代码在编译期或 CI 阶段就失败，而不是靠 review 发现 | ArchUnit + Modulith + Checkstyle / Error Prone + 以规则 ID 命名的测试 |

## 1. 老代码的命名问题（实测）

| 问题 | 规模 | 例子 |
|---|---|---|
| 中文标识符（枚举、常量、类成员） | 129 个 Java 文件，代码引用 1,234 处 | `FSTConstant.IsOrNo.是`（145 处）、`WorkSpaceType.项目`（134 处）、`ApproveStatus.通过`（121 处）、`CommonConstants.日小时`、`RoleCode.营销` |
| 同一概念多套叫法 | 工时：`manhour` 3,491 处、`works` 949 处、`timesheet` 689 处，另有 `man_hour`、`time_sheet` | 表 `psa_time_sheet` + `psa_time_sheet_works` + `psa_time_sheet_works_end`；包 `service/manhour`；页面 `manhour` 和 `timesheet` 并存 |
| 同一状态在两套代码中数值互换 | 审批结果：BPM 中 2 = 通过、3 = 不通过；fst 中 3 = 通过、2 = 驳回 | `BpmProcessInstanceResult` 与 `ApproveStatus` |
| 雪花 ID 当状态值 | `FSTConstant` 中 27 个 | `OppCloseStatus.未关闭 = 5665184657220652440` |
| 同一常量多处定义 | 7.5 小时 / 人天 | `CommonConstants.日小时`、`FSTConstant.HUMAN_DAYS`，以及多处字面量 |
| 拼写错误、拼音、自造缩写 | | `costopenning`、`socail`、`biget`、`cuvalue`、`arbad`、`ohproject` |
| 名不副实 | 9 个 `dal/mysql` 包实际访问 SQL Server；`fst` 没有业务含义；`controller` 包里放着 `@Service` | |
| 空泛后缀 | 61 个类 | `*Util`、`*Helper`、`*Manager`、`*Info`、`*Data`、`*Impl`、`*DO` |
| 可变的 `public static` 常量 | 56 个内部类 | `FSTConstant`；`CommonConstants` 里的密钥字段（SEC-006） |

## 2. 命名规则：英文、行业惯用、可预测

### 2.1 词源

**唯一来源是 `docs/domain/00_战略设计/03-通用语言.md` 的「代码名」列。新概念先进词表定名，再进代码。** 词表本身已经采用 PSA / 专业服务和财务的行业惯用语，新代码直接使用：

| 中文 | 代码名（行业惯用） | 不用 |
|---|---|---|
| 报价单 | `Quote` | Quotation、BaoJia |
| 工时单 / 工时条目 | `Timesheet` / `TimeEntry` | ManHour、Works、WorkHour |
| 可计费工时 | `BillableHours` | |
| 费率表 | `RateCard` / `RateCardItem` | HourlyRate、BillRate 表 |
| 交付物 | `Deliverable` | |
| ETC / EAC / 完工百分比 | `EstimateToComplete` / `EacAssessment` / `PercentageOfCompletion`（简写 `Poc`） | |
| WIP / 合同资产 | `Wip` / `WipAccount` | |
| 结转批次 / 分摊 | `CarryBatch` / `AllocateOverhead` | |
| 合同 / 条款 | `Contract` / `ContractClause` | Term（与「期间」歧义） |
| 验收 | `AcceptanceRecord` / `MilestoneAccepted` | |
| 核算主体 | `LegalEntity` / `legal_entity_id` | companyCode |

**词表改词建议**：这几处词表本身不够地道，需要按「同批改词表」的规则处理：
- `ManhourEstimateItem` → `LaborEstimateLine`：manhour 是旧称，行业用 labor 或 effort。
- `TimeEntry`（工时）与 `TimesheetLine`（工时明细）是同义重复，建议统一为 `TimeEntry`。
- 「费率版本」`RateVersion` 与客户域的 `CustomerRateCard` 建议统一到 `RateCard` 词族。

### 2.2 写法

- 只用英文 ASCII 标识符；中文只出现在 UI 文案、i18n 和注释里（由 ArchUnit `identifiersAreAscii` 守护）。
- 缩写按单词处理：`WbsNode`、`PocValue`、`ArItem`，不写 `WBSNode`。
- 类名后缀**只允许**下表这些，每种后缀对应唯一的位置：

| 后缀 / 形态 | 位置 | 例子 |
|---|---|---|
| 聚合、实体、值对象：名词，无后缀 | `<module>.domain` | `Timesheet`、`Quote`、`Money` |
| `*Repository`（接口） | `<module>.domain` | `TimesheetRepository` |
| `Jdbc*Repository`（实现，包内可见） | `<module>.infrastructure.persistence` | `JdbcTimesheetRepository` |
| 用例：动宾，无后缀，一个类一个用例 | `<module>.application` | `SubmitTimesheet`、`ApproveQuote` |
| `*Queries` / `*Commands`（接口） | `<module>.api` | `WorkPackageQueries` |
| 事件：过去式，无 `Event` 后缀，record | `<module>.events` | `TimesheetApproved`、`QuoteApproved` |
| `*Controller`（包内可见） | `<module>.web` | `TimesheetController` |
| `*Request` / `*Response`（record） | `<module>.web` | `SubmitTimesheetRequest` |
| `*Client` / `*Gateway`（防腐层） | `integration.<system>` | `U8cVoucherClient` |
| 状态枚举 `*Status`；存库时存枚举名 | `<module>.domain` | `TimesheetStatus.APPROVED` |
| **禁止** | 任何位置 | `Util(s)`、`Helper`、`Manager`、`Info`、`Data`、`Impl`、`DO`、`ManHour` |

### 2.3 数据库、API、前端

- **PostgreSQL**：
  - 一个 BC 一个 schema，schema 名为 `<subdomain>_<bc>`（例如 `cost_timesheet`）；表名用 snake_case 复数，不加 `psa_` 前缀。
  - 外部引用写 `<对象>_id`；审计列为 `created_at`、`created_by`、`updated_at`、`updated_by`、`version`；布尔列以 `is_` / `has_` 开头。
  - 金额成对存放 `<名>_amount` + `<名>_currency`；状态存枚举名（`text` + `CHECK`）。
  - Flyway 脚本放在各模块目录下，命名为 `V{yyyyMMddHHmmss}__{module}_{描述}.sql`，描述用英文 snake_case（中文说明写在脚本首行注释）。
- **HTTP**：路径为 `/api/v{n}/{subdomain}/{复数资源}`（ADR-0008，例如 `/api/v1/cost/timesheets`；第二段是**子域** slug，不是 BC 或功能名），用 kebab-case；动作写成 `:submit` / `:approve`；**所有 JSON（接口、领域事件载荷、外部投递）字段一律 camelCase**，snake_case 只用于数据库；错误码按模块分段。
  - 更正（2026-10-14）：原示例 `/api/v1/timesheet/timesheets` 把 BC 当成子域，原规则「事件载荷 snake_case」与接口 camelCase 不一致，均已统一。
- **前端**：`src/modules/<module>/{pages,components,hooks,api}`；API 客户端由 OpenAPI 生成（禁止手写 URL）；TypeScript strict；组件用 PascalCase，hook 用 `useXxx`，文件名用 kebab-case。

### 2.4 列名类别词、约束与索引命名（借鉴 DAMA / ISO 11179，2026-10-14）

用户 2026-10-14：问「1a 还有哪些设计需要现在考虑？所有表命名是否有标准？需要借鉴 dama 的标准吗」，选「全部在 1a 做 (Recommended)」。

**列名 = 限定词 + 类别词**（ISO 11179 representation term）。类别词决定类型；列名也可以只由类别词本身构成（如 `code`、`name`、`status`），表名就是它的对象。

| 类别词 | 含义 | 类型 |
|---|---|---|
| `_id` | 标识符 | `bigint`、`uuid`；`text` 只用于外部系统标识（如企业微信 userid） |
| `_key` | 业务键、幂等键 | `text` |
| `_code` / `_codes` | 代码 / 代码集合 | `text` / `text[]` |
| `_name` | 名称 | `text` |
| `_text`、`_description`、`_reason`、`_note`、`_title`、`_message` | 自由文本 | `text` |
| `_address` | 地址（邮箱、IP） | `text` |
| `_ciphertext` | 加密后的值 | `text` |
| `_status`、`_kind`、`_type`、`_policy` | 枚举 | `text` + `CHECK` 列出枚举名 |
| `_at` | 时间点 | `timestamptz` |
| `_date` | 业务日期 | `date` |
| `_amount` | 金额，必须配同名 `_currency` | `numeric` |
| `_currency` | 币种（ISO 4217） | `text` / `char(3)` |
| `_qty`、`_hours`、`_days` | 数量；带单位的数量（工时、人天） | `numeric`（`_qty` 也可整数） |
| `_rate` / `_pct` | 比率、单价 / 百分比 | `numeric` |
| `_count` | 计数 | `integer` / `bigint` |
| `_number` | 编号（单据编号）、序号（轮次） | `text` / `integer` / `bigint` |
| `_order` | 排序 | `integer` |
| `_hash` | 摘要 | `text` |
| `_url` / `_path` | 地址 / 路径 | `text` |
| `_by` | 操作人（审计） | `bigint` |
| `_value` | 数值（计数器、起始值） | `numeric` / `integer` / `bigint` |
| `is_` / `has_`（前缀） | 指示 | `boolean` |

另允许 `id`、`version` 两个独立列名。单据编号用 `_number`（与编号模块用语 `legacy_number`、`round_number` 一致），不用 `_no`。

**约束与索引**：主键 `pk_<表>`；外键 `fk_<表>_<列>`；唯一 `uk_<表>_<列>`（唯一约束和唯一索引都用）；表级检查 `ck_<表>_<规则>`；普通索引 `ix_<表>_<列>`。主键、外键、唯一约束都要用 `CONSTRAINT <名>` 显式命名，不依赖 PostgreSQL 的默认名；只写在列上的枚举 `CHECK` 可不命名。

**守护**：`DdlNamingTest` 按全部迁移脚本推算最终表结构（跟踪 `ADD COLUMN`、`RENAME COLUMN`、`RENAME CONSTRAINT`、`ALTER INDEX ... RENAME TO`），检查类别词与类型、约束与索引命名；注入样例覆盖每类错误。规则生效前已存在的项登记在 `backend/architecture/ddl-naming-baseline.txt`（每行附改名建议），新违规和「已修好却没删」的基线行都会失败。框架拥有列名的表豁免：`platform_identity.sessions`、`sessions_attributes`（Spring Session JDBC）、`platform_integration.event_publication`（Spring Modulith）、`platform_scheduling.scheduler_locks`（ShedLock）。

### 2.5 列级敏感分级

| 级别 | 含义 | 处理 |
|---|---|---|
| `PUBLIC` 公开 | 可对外 | — |
| `INTERNAL` 内部 | 默认级别 | 登录可见，按功能权限 |
| `SENSITIVE` 敏感 | 个人信息、工资与日薪、银行卡（GUIDING_PRINCIPLES §6） | 脱敏展示、按法人 × 部门 × 项目角色授权、访问留痕；不进分析层明细 |
| `RESTRICTED` 高度敏感 | 口令、密钥、会话内容 | 不出库（只在本模块内部使用）、不进日志、不进分析层 |

登记在 `backend/architecture/data-classification.txt`（`<schema.table.column> | <级别> | <理由>`），未登记的列默认 `INTERNAL`。守护：`DdlNamingTest` 要求名字像敏感信息的列（口令、密钥、TOTP、银行、工资、日薪、证件、手机、邮箱、生日、性别、地址、会话内容）必须登记为 `SENSITIVE` 或 `RESTRICTED`，登记的列必须存在。web / api 出参不得直接暴露 `SENSITIVE`、`RESTRICTED` 列，这一守护由 1a 会话实现。

## 3. 包结构：代码放在哪里（agent 速查表）

```
com.forcitis.one
├── OneApplication.java
├── SubdomainModuleDetection.java   模块识别策略：每个 <subdomain>.<bc> 是一个顶层模块（ADR-0003）
├── kernel/                         共享内核（OPEN 模块）：Money、Hours、LegalEntityId、UserId、@ValueObject、@PublicEndpoint
├── <subdomain>/                    子域命名空间（12 个，见 02-子域划分）：presales | contract | delivery | cost | receivables
│   │                               | expense | procurement | km | organization | accounting | platform | operations
│   └── <bc>/                       一个 BC = 一个 Modulith 模块，例如 cost.timesheet、delivery.execution、platform.approval
│       ├── package-info.java       职责、对应的 BC 中文名、拥有的 schema（不加 @ApplicationModule 注解）
│       ├── api/                    @NamedInterface("api")：接口 + record + 类型化枚举 + 类型化 ID（窄接口）
│       ├── events/                 @NamedInterface("events")：已发生事实的 record（过去式）
│       ├── domain/                 聚合、实体、值对象、领域服务、*Repository 端口。不依赖 Spring 或 JDBC
│       ├── application/            用例（一类一用例，唯一的 public 方法），唯一的事务边界
│       ├── infrastructure/
│       │   └── persistence/        Jdbc*Repository（JdbcClient + 类型化映射），只访问 <subdomain>_<bc> schema
│       └── web/                    *Controller（/api/{version}/…）、*Request / *Response record
└── (resources) db/migration/<subdomain>_<bc>/V<yyyyMMddHHmmss>__<subdomain>_<bc>_<描述>.sql
architecture/module-dependencies.txt  已批准的模块依赖图（上下文映射的代码形态），改动需评审
```

| 我要写… | 放在 | 名字 |
|---|---|---|
| 新的业务规则或计算 | `<module>.domain`（聚合方法或值对象） | 用词表名词 |
| 一个用户动作 / 接口背后的流程 | `<module>.application` | 动宾，例如 `SubmitTimesheet` |
| 给其他模块用的查询 | `<module>.api` 接口 + `application` 实现 | `*Queries` |
| 通知其他模块「发生了什么」 | `<module>.events` record，在用例中 publish | 过去式 |
| SQL | `<module>.infrastructure.persistence` | `Jdbc*Repository` |
| REST 接口 | `<module>.web` | `*Controller` |
| 调用外部系统 | `integration.<system>` | `*Client` |
| 共享的值类型 | `kernel`（需评审；不得放业务行为） | |
| 新的跨模块依赖 | 先改 `architecture/module-dependencies.txt` 并通过评审 | |
| 新表 / 新字段 | `db/migration/<subdomain>_<bc>/`，必须带中文 COMMENT | 表名和列名由词表派生 |
| 业务编号 | 调用 `platform.numbering` 的 `DocumentNumbers.next(...)`，在本用例的事务内调用 | 新单据类型要在 `DocumentType` 中登记 |

## 4. 守护规则（模板工程中已实现并验证）

| # | 规则 | 防的是哪类 agent 错误 | 实现 |
|---|---|---|---|
| G1 | 模块之间只能经 `api` / `events` 访问，无环，依赖只能指向 `allowedDependencies` | 直接 import 别的模块的 domain 或 repository | Modulith `ApplicationModules.verify()` |
| G2 | `api` 只放接口和 record | 把实现类或可变对象当契约 | `apiPackagesContainOnlyInterfacesAndRecords` |
| G3 | `api` 签名不得引用 domain / application / infrastructure / web | 通过接口泄露内部模型（窄接口变宽） | `apiSignaturesDoNotLeakModuleInternals` |
| G4 | 每个模块的 api 方法总数 ≤ 12 | 为图方便不断给接口加方法 | `apiSurfaceStaysNarrow` |
| G5 | `events` 只放 record，名称不以 `Event` 结尾 | 可变事件、命名不一致 | `eventsAreRecords` |
| G6 | web 和 infrastructure 的类不得 public | 其他代码直接引用控制器或适配器 | `webAndInfrastructureAreNotPublic` |
| G7 | domain 不依赖 Spring、Servlet 或其他层 | 在领域对象里写框架代码或 SQL | `domainIsFrameworkFree` |
| G8 | application 不依赖 web 或 infrastructure | 用例里直接写 JDBC 或调控制器 | `applicationDoesNotTouchWebOrInfrastructure` |
| G9 | web 只调用 application | 控制器直连仓储或领域对象 | `webOnlyCallsApplication` |
| G10 | JDBC / `java.sql` 只出现在 infrastructure | 在任意位置写 SQL | `jdbcOnlyInInfrastructure` |
| G11 | `@Transactional` 只出现在 application | 事务边界散落（老代码 WIP 结转缺陷的根源） | `transactionsOnlyInApplication` |
| G12 | `@RestController` 只在 web，且以 `Controller` 结尾 | 控制器放错包 | `controllersLiveInWebAndEndWithController` |
| G13 | domain 里的 `*Repository` 必须是接口；实现以 `Jdbc` 开头 | 端口和适配器混淆 | `repositoriesFollowPortAdapterNaming` |
| G14 | 禁止 `Util` / `Helper` / `Manager` / `Info` / `Data` / `Impl` / `DO` / `ManHour` 等命名 | 沿用老代码的命名习惯 | `noVagueOrLegacyNames` |
| G15 | 标识符只能是 ASCII | 中文或拼音标识符 | `identifiersAreAscii` |
| G16 | domain 中的 `*status` 字段必须是 enum | 魔法数、字典 ID 当状态 | `statusesAreEnums` |
| G17 | kernel 以外，`*amount` / `*price` / `*cost` 字段不得是裸 `BigDecimal` | 违反 Money = (amount, currency) | `moneyIsNeverABareDecimalNamedAmount` |
| G18 | 禁止字段注入、`System.out`、JUL、`java.util.Date`、`@PermitAll` | 老代码的常见坏味道和 SEC-003 类漏洞 | `generalCodingRules` |
| G19 | 只有 `platform.approval.infrastructure` 能使用 Flowable | 业务模块直接操作流程引擎 | `flowableOnlyInsideApprovalInfrastructure` |
| G20 | 控制器必须映射到 `/api/{version}/…` | 接口无版本 | `controllersAreVersioned` |
| G21 | `@ValueObject` 必须是 record | 可变值对象 | `valueObjectsAreRecords` |
| G22 | 值对象内不得有 `Date`、可变集合、数组 | 值对象被意外修改 | `valueObjectsHoldNoMutableState` |
| G23 | domain 中的 `*hours` 字段必须用 `Hours` | 用裸数字表示工时 | `hoursAndQuantitiesUseValueObjects` |
| G24 | 领域层和应用层禁止 `BigDecimal.equals` 和 `new BigDecimal(double)` | 精度和小数位比较错误 | `noBigDecimalEqualsInBusinessCode` |
| G25 | 业务代码禁止直接调用 `now()`，必须注入 `Clock` | 按「今天」取费率导致漂移 | `timeComesFromAnInjectedClock` |
| G26 | 主代码禁止 `Fix*`、`Demo*`、`Mock*`、`Test*`、`Sample*`、`*Example*` | 调试和修数代码上生产 | `noDebugOrDataFixCodeInMain` |
| G27 | 用例类只有一个 public 方法 | 超大类、`this.` 自调用绕过事务 | `useCasesExposeExactlyOnePublicMethod` |
| G28 | 每个接口方法有 `@PreAuthorize` 或 `@PublicEndpoint(reason)` | 匿名写接口、缺少权限校验 | `everyHandlerIsAuthorizedOrExplicitlyPublic` |
| G29 | 事件名不得以 Request / Requested / Command / Query 结尾；禁用普通 `@EventListener` | 用事件冒充 RPC、在同一事务里耦合 | `eventsAreFactsNotRequests` |
| G30 | `@NamedInterface` 只能用在 api / events 包 | 多开出口，绕过窄接口预算 | `namedInterfacesAreOnlyApiAndEvents` |
| G31 | api / events / domain 中的 `*Id` 字段必须是类型化 ID record | 把一种 ID 传给另一种 | `contractsAreStronglyTyped` |
| G32 | api / events 中禁止 `Object` 和 `Map` | `Map<String,Object>` 契约 | `contractsAreStronglyTyped` |
| G33 | 只有 identity 能配置 Spring Security；请求 record 不得含 userId / creator 等身份字段 | 到处 `permitAll()`、冒充发起人 | `onlyIdentityConfiguresSecurity`、`requestsNeverCarryTheCallerIdentity` |
| G34 | 【已采纳（ADR-0016），Phase 2 骨架实现】`operations.facts` 的事实仓储只能有插入和查询方法；事实表有拒绝 UPDATE / DELETE 的触发器 | 直接改已过账的数据，而不是冲销 | ArchUnit + DDL 测试（ADR-0016） |
| G35 | 【已采纳（ADR-0016）】只有 `accounting.ledger` 能依赖会计科目和科目映射 | 业务 BC 写死科目代码 | ArchUnit（ADR-0016） |
| G36 | 【已采纳（ADR-0016）】触账 BC 只能通过 `operations.facts` 的 api 提交过账候选，不能写事实表 | 绕过期间校验和借贷平衡 | 依赖图 D1 + ArchUnit（ADR-0016） |
| G37 | 【已采纳（ADR-0018）】domain / api / events / web / kernel 中的类型名、record 组件、枚举常量、非私有方法，以及迁移中的表名、列名，拆词后必须已在 `glossary/terms.yaml` 登记或属于 `common_words` | 自造词、同义词漂移 | `GlossaryTest`（失败时生成 `target/glossary-draft.yaml` 草稿） |
| G38 | 【已采纳（ADR-0018）】出现词表中的禁用写法（老名、拼音、非标准用语）即失败 | 把 ManHour、Quotation、cuvalue 带进新代码 | `GlossaryTest` |
| G39 | 【已采纳（ADR-0018）】列名对应词表术语时，列注释必须以该术语的中文开头 | 数据字典与统一语言不一致 | `GlossaryTest`（解析迁移中的 COMMENT ON） |
| G40 | 【已采纳（ADR-0019）】新代码禁止依赖 `cn.iocoder..` 及 yudao 的基类和工具（`BaseDO`、`CommonResult`、`TenantBaseDO` 等） | 把脚手架习惯带进新系统 | ArchUnit |
| G41 | HTTP 路径为 `/api/v{n}/{所在模块的子域}/{kebab 复数资源}[/{id}][:{动作}]`，资源词已登记 | 路径随手起名、把 BC 当子域 | `NamingSurfaceTest` |
| G42 | OpenAPI schema 名 PascalCase、属性与参数 camelCase 且已登记；事件序列化后的 JSON 键为 camelCase | 接口与事件字段大小写混用 | `NamingSurfaceTest`（读 `/v3/api-docs`，springdoc 3.1.1） |
| G43 | 权限码 `{子域}.{BC}:{资源}:{动作}`，动作取自词表 `standard_actions`；`@PreAuthorize` 中的权限码必须在目录中 | 自造权限串 | `NamingSurfaceTest` |
| G44 | Flyway 文件 `V{真实 14 位时间戳}__{module}_{english_snake}.sql`，目录为模块 schema，前缀与目录一致，版本不重复 | 中文描述、放错目录 | `NamingSurfaceTest` |
| G45 | 业务编号单据类型枚举与种子行一一对应，大写下划线且已登记 | 编号规则代码与代码不一致 | `NamingSurfaceTest` |
| G46 | `@Scheduled` 作业必须有 `{module}.{name}` 的 ShedLock 锁；指标名为字面量且按模块前缀；`job_type` 受检；配置键以 `forcitis.{module}.` 开头或为框架已知键 | 作业互踩、指标和配置命名混乱 | `NamingSurfaceTest` |
| G47 | 流程 / 决策 id 为 `{子域}_{BC}_{snake}`，文件名同 id，`ProcessKey` 与流程一一对应，用户任务 id camelCase | 流程键与代码脱节 | `NamingSurfaceTest` |
| G48 | 错误码：每个模块一个实现 `kernel.ErrorCode` 的枚举，格式 `{SUBDOMAIN}_{BC}_{REASON}` | 错误码散落为字符串 | `NamingSurfaceTest` |
| D1 | 模块依赖必须在 `architecture/module-dependencies.txt` 中登记 | 随手引入新依赖 | `ModuleDependencyTest` |
| D2 | DDL：文件名规范、只建本 schema 的表、snake_case、禁用老名和空泛词、审计列、金额带币种、状态 `CHECK`、布尔 `is_` / `has_`、每列有 COMMENT；列名类别词与类型、约束与索引命名（§2.4）；敏感列登记（§2.5） | 数据字典混乱 | `DdlNamingTest` |
| D3 | 数据字典从数据库元数据生成，每张表、每个字段必须有注释 | 字段无说明 | `ApplicationIntegrationTest.everyTableAndColumnIsDocumented…` |

**负向验证（后端）**：向模板注入以下错误，`./mvnw verify` 失败并报出 8 条违规，信息逐条指出位置和原因：
- 在 timesheet 用例里 import `delivery.execution.domain.WorkPackage`（模板初版时为 TaskPackage）；
- 新建 `ManHourServiceImpl`，内含中文常量和裸 `BigDecimal costAmount`，并在 application 层用 `JdbcClient`；
- 在 web 层写 public 控制器并返回领域对象；
- 在 delivery 的 api 中返回 domain 类型。

删除注入的代码后恢复全绿。

**计划补充的守护**（模板中尚未实现，Phase 1 骨架补齐）：
- SQL 只引用本模块 schema（扫描 `infrastructure.persistence` 里的 SQL 文本）；
- 每个 P0 规则都有一个以 `RULE_NNN_` 开头的测试（对照 `BUSINESS_RULES.md` 检查覆盖率）；
- 新出现的聚合、事件、表名必须在词表中；
- 单个文件不超过 400 行、单个方法不超过 60 行（Checkstyle）；
- 前端用 ESLint 的 `no-restricted-imports` 禁止跨模块 import 内部目录。


**前端守护（`architecture-guardrails/frontend`，ESLint + TypeScript strict）**

| # | 规则 | 防的是哪类错误 |
|---|---|---|
| F1–F4 | `app → modules(index) → shared` 单向依赖；跨模块只能经 `modules/<m>/index.ts`（eslint-plugin-boundaries） | 跨模块引用内部文件、shared 反向依赖业务 |
| F5 | 禁 axios、fetch、手写 `/api` URL；只用 OpenAPI 生成的客户端 | 手写 URL 失配 |
| F6 | 只用 ASCII 标识符；文件名 kebab-case；禁用 Util / Helper / Manager / Info / Data / Impl 类名 | 命名混乱 |
| F7 | 禁 `any`；React hooks 规则 | 类型漏洞、hooks 误用 |
| F8 | hooks 放在 hooks/ 目录，且命名为 `useXxx` | 放错位置 |
| F9 | `model/` 和 `shared/lib` 为纯逻辑，不得引用 React 或 API | 逻辑与界面耦合 |
| F10 | 只有 `modules/<m>/api` 能 import 生成的客户端 | 页面直接调接口 |
| F11 | 禁 `dangerouslySetInnerHTML` | 存储型 XSS（SEC-008） |
| F12 | 禁 `export *` | 公开接口失控 |
| F13 | 每个模块 `index.ts` 的导出数 ≤ 8（`check-public-api.mjs`） | 窄接口变宽 |
| F14 | 业务模块禁止直接 import antd、禁内联样式、禁硬编码颜色；原始令牌只在 `shared/design-system` 中读取 | 偏离设计系统（ADR-0013） |

## 5. 对 agent 友好的约定

- 每个模块根目录放 `AGENTS.md`（与 `package-info.java` 一致），写明：职责、拥有的 schema、对外的 api / events 清单、允许的依赖、相关规则 ID 范围。
- 一个文件一个顶层类型；一个用例一个类，入口方法统一为 `execute`。
- 测试与生产代码的包结构一一对应；P0 规则测试命名为 `RULE_012_clauseTotalMustEqualFinalAmount`，agent 可以按规则 ID 定位。
- 生成的代码（OpenAPI 客户端、jOOQ 之类）只放在 `generated/` 目录，不手改，并排除在守护规则之外。
- 守护规则的失败信息必须包含「放哪里 / 用什么名字」，这样 agent 读到报错就能自我修正。

## 6. 老名 → 新名对照（起点，各阶段补全）

| 老（老系统） | 新（模块.对象 / schema.table） |
|---|---|
| `yudao-module-fst`、`cn.iocoder.yudao.module.fst` | 按 BC 拆为 `presales.quoting`、`delivery.execution`、`receivables.wip`、`cost.timesheet`、`cost.costing`、`expense.spend`、`accounting.ledger` 等模块（GLOSSARY §1） |
| `yudao-module-bpm`、`bpm_*`、`ACT_*` | `platform.approval` 模块、`platform_approval` schema |
| `yudao-module-system`（用户 / 部门 / 角色 / 菜单） | `platform.identity` + `organization.workforce` 模块 |
| `psa_time_sheet` / `psa_time_sheet_works` / `psa_time_sheet_works_end` | `cost_timesheet.timesheets` / `cost_timesheet.time_entries` / `cost_costing.labor_cost_entries` |
| `ManHourDO`、`ManHourServiceImpl`（3,085 行） | `Timesheet` 聚合 + `TimeEntry`；用例 `SubmitTimesheet`、`ApproveTimeEntries`、`WithdrawTimesheet`…… |
| `ApproveStatus`（0 / 1 / 2 / 3 / 4） | `ApprovalStatus.DRAFT / IN_APPROVAL / REJECTED / APPROVED / CANCELLED` |
| `BpmProcessInstanceResult`（1 / 2 / 3 / 4） | `platform.approval.api.ApprovalOutcome.PENDING / APPROVED / REJECTED / CANCELLED` |
| `FSTConstant.ProjectStatus.*`（字典 ID） | `delivery.execution.api.ProjectStatus.PLANNING / EXECUTING / PAUSED / COMPLETED / CLOSED / TERMINATED` |
| `FSTConstant.OppCloseStatus.*`（雪花 ID） | `presales.quoting.api.OpportunityStatus.OPEN / WON / LOST` |
| `FSTConstant.IsOrNo.是 / 否` | `boolean` |
| `CommonConstants.日小时`、`FSTConstant.HUMAN_DAYS`、7.5 字面量 | `cost.timesheet` 的 `StandardWorkday` 参数（单一来源） |
| `psa_quotation*`、`psa_cost_template*` | `presales_quoting.quotes`、`presales_quoting.quote_lines`、`presales_quoting.cost_estimate_nodes`、`presales_quoting.cost_templates` |
| `psa_pm_project`、`psa_task_package`、`psa_budget_*` | `delivery_execution.projects`、`delivery_execution.work_packages`、`delivery_execution.project_budgets`、`delivery_execution.budget_revisions` |
| `psa_pm_cuvalue`（产值） | `receivables_wip.progress_recognitions`（收入确认准备，原「产值」） |
| `psa_cost_ledger`、`psa_cost_allocation*`、`psa_carry_batch*`、`psa_wip_*` | `cost_costing.cost_entries`、`cost_costing.overhead_allocations`、`cost_costing.carry_batches`、`cost_costing.cost_wip_entries` |
| `psa_voucher*`、`psa_u8c_voucher`、`mdm_accounting_period` | `accounting_ledger.journal_vouchers`、`accounting_ledger.accounting_periods` |
| `psa_travel_application*`、`psa_fenbeitong_*` | `expense_spend.expense_requests`、`expense_spend.expense_claims`；分贝通快照在 `platform_integration` |
| `psa_bd_customer`、`psa_crm_*` | `presales_customer.customers`（CRM 只读快照） |
| `psa_biget_*`（必捷巡检） | 待定：生产 180 天只调用过 11 个端点中的 2 个，先确认是否下线 |
| `companyCode` | `legal_entity_id` / `LegalEntity` |
| 包 `dal/mysql`、`dal/dataobject`、`service/impl`、`controller/admin` | 模块内的 `infrastructure.persistence`、`domain`、`application`、`web` |
| 前端 `views/costopenning`、`socail` | `modules/cost-costing/pages/opening`、`modules/platform-identity/pages/social-login` |

完整对照表在每个阶段开工前，由 reimagine 的骨架步骤从 `DATA_OBJECTS.md` 和词表补全。它同时就是该阶段反向同步和数据迁移的字段映射。

## 7. 命名面清单：新系统中所有需要统一命名的地方（2026-10-14）

统一的来源只有一个：`glossary/terms.yaml`（ADR-0018）。下表逐项列出命名面、规则和守护。状态：✔ 已有守护并实测（G41–G48 于 2026-10-14 实测：模板 68 项测试全绿，8 类注入全部被拦下）；○ 有规则、尚无守护，按阶段补建。

| # | 命名面 | 规则 | 守护 | 状态 |
|---|---|---|---|---|
| 1 | 仓库、模块、Java 包 | `forcitis-one-psa`；`com.forcitis.one.<子域>.<BC>.{api,events,domain,application,infrastructure,web}` | G1、D1、ModularityTest | ✔ |
| 2 | 类、record、枚举、方法 | 英文、行业标准词；禁含糊词、老名、拼音、`fst`、`cn.iocoder` | G12–G15、G26、G37、G38、G40 | ✔ |
| 3 | 领域事件 | 过去式事实（`TimesheetSubmitted`），不以 Event / Request / Command 结尾 | G5、G29、G37 | ✔ |
| 4 | 值对象、类型化 ID | `Money`、`Hours`、`TimesheetId`… | G16、G17、G21–G23、G31 | ✔ |
| 5 | 数据库 schema、表、列、约束、注释 | `<子域>_<BC>`；snake_case 复数表名；审计列、金额 + 币种、`is_` / `has_`；列注释中文与词表一致 | D2、D3、G37、G39 | ✔ |
| 6 | Flyway 文件名 | `V{时间戳}__{module}_{english_snake}.sql` | D2；G44 | ✔ |
| 7 | HTTP 路径 | `/api/v{n}/{子域}/{kebab 复数资源}[:{动作}]` | G20（版本）；G41（子域与资源登记） | ✔ |
| 8 | JSON 字段、OpenAPI schema、事件载荷 | 一律 camelCase，schema 名 PascalCase，拆词已登记 | G42 | ✔ |
| 9 | 权限码 | `{子域}.{BC}:{资源}:{动作}`，动作取自标准动作集 | G28（类型化）；G43（格式） | ✔ |
| 10 | 业务编号规则代码 | 大写下划线（`QUOTE`、`TIMESHEET`），已登记 | G45 | ✔ |
| 11 | 定时作业、锁名、作业表 `job_type`、指标名、配置键 | `{子域}.{BC}.{name}`；配置键前缀 `forcitis.{子域}.{BC}.` | G46 | ✔ |
| 12 | BPMN 流程、DMN 决策、用户任务 | 键名按子域 / BC 前缀统一；任务 id camelCase 且已登记 | G19（引擎隔离）；G47 | ✔ |
| 13 | 错误码 | `{SUBDOMAIN}_{BC}_{REASON}` | G48 | ✔ |
| 14 | 前端 PC 目录、文件、组件、hooks | `modules/<m>`；kebab-case 文件、PascalCase 组件、`useXxx` | F1–F8、F13 | ✔ |
| 15 | 设计令牌 | 只用正本语义令牌名（`--surface-*`、`--status-*`…） | F14 | ✔ |
| 16 | 前端界面中文文案 | 业务名词取词表 `zh`，同一概念全站同一叫法 | 无 | ○ 建议在 `shared/i18n` 由词表生成文案键，lint 禁止页面硬写业务名词 |
| 17 | H5（Taro） | 目录、命名、接口客户端与 PC 同规则 | 无（守护模板只覆盖 PC） | ○ Phase 2 前为 H5 建同等 lint |
| 18 | `reporting` 视图与 BI 列 | 视图名 `rpt_<主题>`，列名与词表 `snake` 一致 | 无 | ○ Phase 5 前补 |
| 19 | 外部投递通道与防腐层 | 外部系统名统一（`wecom`、`seeyon`、`u8c`、`fenbeitong`），外部字段只在防腐层内出现 | G1（防腐层隔离） | ○ 补名称约定守护 |
| 20 | 日志字段、追踪属性 | 结构化日志键 camelCase，业务键取词表 | 无 | ○ Phase 1a 可观测性接入时定 |
| 21 | Git 分支、提交、PR | 沿用老仓库约定（`feature/issue-{N}-…`、`type(scope): 中文`，scope 用子域 slug） | 老仓库 pr-hygiene | ○ 新仓库 CI 复用 |

**本次统一时发现并更正的不一致**：
- HTTP 示例 `/api/v1/timesheet/timesheets` 把 BC 当成子域，已改为 `/api/v1/cost/timesheets`；
- 「事件载荷字段 snake_case」与接口 camelCase 不一致，已统一为所有 JSON 用 camelCase；
- Flyway 描述明确用英文 snake_case（老仓库使用中文描述）；
- 收入确认口径：词表「投入法、产出法并存」与定稿规则 ACC-R01「统一产出法 · 客户价值确认」冲突，以 ACC-R01 为准（ADR-0016）。

**G41–G48 的已知盲区**：运行时拼出的指标名；`.yml` 中的配置键；Flowable `candidateGroups` 名；DMN 文件与 `job_type` 表尚无真实样例（只用注入用例验证）；测试数据迁移不受 G44 约束；事件发布登记表未启用，事件键经应用的 JSON 映射器检查；写成普通字符串的错误码检测不到。模板为满足守护新增了 4 个词条（cost、error-code、capacity、scheduler-lock），状态为提议，进入下一轮用语确认。
