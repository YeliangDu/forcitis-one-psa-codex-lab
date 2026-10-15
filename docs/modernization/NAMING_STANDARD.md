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
- **HTTP**：路径为 `/api/v{n}/{subdomain}/{复数资源}`（ADR-0008，例如 `/api/v1/cost/timesheets`；第二段是**子域** slug，不是 BC 或功能名），用 kebab-case；动作写成 `:submit` / `:approve`；**所有 JSON（接口、领域事件载荷、外部投递）字段一律 camelCase**，snake_case 只用于数据库；错误码按模块分段；跨模块通用的错误（状态转换不合法、被他人同时修改、必须填写原因、已过撤回截止点、未登录等）只在公共基础 `kernel` 定义一次，各模块直接使用，模块只定义本模块特有的错误（用户 2026-10-15 对抽规则答「通用错误码只在公共基础定义」）。状态码、列表、类型表示、并发与错误响应等接口约定见 §2.6。
  - 更正（2026-10-14）：原示例 `/api/v1/timesheet/timesheets` 把 BC 当成子域，原规则「事件载荷 snake_case」与接口 camelCase 不一致，均已统一。
- **前端**：`src/modules/<module>/{pages,components,hooks,api}`；API 客户端由 OpenAPI 生成（禁止手写 URL）；TypeScript strict；组件用 PascalCase，hook 用 `useXxx`，文件名用 kebab-case。

### 2.4 列名类别词、约束与索引命名（借鉴 DAMA / ISO 11179，2026-10-14）

用户 2026-10-14：问「1a 还有哪些设计需要现在考虑？所有表命名是否有标准？需要借鉴 dama 的标准吗」，选「全部在 1a 做 (Recommended)」。

**列名 = 限定词 + 类别词**（ISO 11179 representation term）。类别词决定类型；列名也可以只由类别词本身构成（如 `code`、`name`、`status`），表名就是它的对象。

| 类别词 | 含义 | 类型 |
|---|---|---|
| `_id` | 标识符 | `bigint`、`uuid`；`text` 只用于外部系统标识（如企业微信 userid）。**随 ADR-0033 采纳生效**：一律 `uuid`；唯一例外 `platform_integration.source_references.source_id`（按来源实际类型，A8 为 `bigint`，可为负数）；外部系统的文本标识不用 `_id`，用 `_key`（如 `external_identities.external_subject_key`） |
| `_key` | 业务键、幂等键；**随 ADR-0033 采纳生效**：另含外部系统的文本标识（如 `external_identities.external_subject_key`） | `text` |
| `_code` / `_codes` | 代码 / 代码集合 | `text` / `text[]` |
| `_name` | 名称 | `text` |
| `_text`、`_description`、`_reason`、`_note`、`_title`、`_message` | 自由文本 | `text` |
| `_address` | 地址（邮箱、IP） | `text` |
| `_ciphertext` | 加密后的值 | `text` |
| `_status`、`_kind`、`_type`、`_policy`、`_system` | 枚举（`_system`：外部系统的类别，如 `source_system` 来源系统，ADR-0033） | `text` + `CHECK` 列出枚举名 |
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
| `_by` | 操作人（审计） | `bigint`。**随 ADR-0033 采纳生效**：`uuid`（用户账号的 `user_id`） |
| `_value` | 数值（计数器、起始值） | `numeric` / `integer` / `bigint` |
| `is_` / `has_`（前缀） | 指示 | `boolean` |

另允许 `id`、`version` 两个独立列名。单据编号用 `_number`（与编号模块用语 `legacy_number`、`round_number` 一致），不用 `_no`。

**约束与索引**：主键 `pk_<表>`；外键 `fk_<表>_<列>`；唯一 `uk_<表>_<列>`（唯一约束和唯一索引都用）；表级检查 `ck_<表>_<规则>`；普通索引 `ix_<表>_<列>`。主键、外键、唯一约束都要用 `CONSTRAINT <名>` 显式命名，不依赖 PostgreSQL 的默认名。**枚举 `CHECK` 一律命名为 `ck_<表>_<列>`**（用户 2026-10-15：对 DDL 审计的待决项答「全部按推荐 (Recommended)」，DDL-22），不写不具名的列上 CHECK——上线后改取值要按名字删除重建；同一列的取值集合与对应 Java 枚举一致。基线开放期内的存量按此改名并入基线 DDL 收紧（R-2）；守护由 `DdlNamingTest` 扩展（§4「待实现的守护」G-D12）。

**守护**：`DdlNamingTest` 按全部迁移脚本推算最终表结构（跟踪 `ADD COLUMN`、`RENAME COLUMN`、`RENAME CONSTRAINT`、`ALTER INDEX ... RENAME TO`），检查类别词与类型、约束与索引命名；注入样例覆盖每类错误。规则生效前已存在的项登记在 `backend/architecture/ddl-naming-baseline.txt`（每行附改名建议），新违规和「已修好却没删」的基线行都会失败。框架拥有列名的表豁免：`platform_identity.sessions`、`sessions_attributes`（Spring Session JDBC）、`platform_integration.event_publication`（Spring Modulith）、`platform_scheduling.scheduler_locks`（ShedLock）。

### 2.4a DDL 设计原则（用户 2026-10-15：问「看看 ddl 设计上还有什么需要强化的原则？比如索引？」后选「全部采纳 (Recommended)」）

适用于全部自有表；框架自建表（Spring Session、ShedLock、Modulith 事件登记）豁免并在守护中登记。标「守护」的由 `DdlNamingTest` / `DataArchitectureTest` 检查，其余靠每波复审（SESSION_PLAYBOOK §14.1）。

1. **业务唯一约束**：主键一律 UUIDv7（§14.1 D16）；交易、主数据、参考、配置表另须至少一个业务唯一约束（如单据类型 + 单据编号、法人代码、角色代码、来源系统 + 对象类型 + 来源 ID；带生效区间的用排他约束），并在数据访问注册表登记 `business_key`。纯日志、事件类表可豁免，须写理由。引用一律用 UUID，不以业务键作外键。守护。
2. **索引**：
   - 外键列必须有以该列开头的索引（PostgreSQL 不自动建）。守护（G-DDL2，待实现）。
   - 每条列表 / 查询路径在注册表登记（过滤列、排序列），并有索引覆盖；游标分页的排序键以 `id` 兜底组成联合索引。守护（§4 G-P3 与 G-DDL4 合并为一个 EXPLAIN 集成测试，待实现；实现前复审）。
   - 状态类热点用部分索引（`WHERE status IN (...)`）。
   - 不留重复或冗余索引（与另一索引前缀相同、与唯一约束重复）。守护（G-DDL3，待实现）。
3. **空值与默认值**：默认 `NOT NULL`；可空列须在列注释写明「空」的业务含义（守护：可空列注释含「为空表示」或等价说明）。文本列禁止空串代替空值（`CHECK (col <> '')`）。数据库只允许技术默认值（如 `version` 初值）；业务值与时间一律由应用显式写入。守护。
4. **删除语义**：外键默认 `ON DELETE RESTRICT`；仅同一聚合内的子行允许 `CASCADE`，须登记。不用软删除标志列；业务删除以状态或版本表达，物理删除只用于技术数据过期清理。守护（CASCADE 白名单、禁 `is_deleted` 类列）。
5. **代码格式与文本长度**：`_code` 列有格式 CHECK（字符集、大小写、长度）；不区分大小写比较的列，唯一约束建在规范化值上（如 `lower(email_address)`）。自由文本类列设长度上限（`CHECK (char_length(col) <= n)`）。守护。
6. **枚举演进**：不用 PostgreSQL `ENUM` 类型，统一 `text + CHECK`（命名见 §2.4）；新增取值以迁移改 CHECK（`DROP CONSTRAINT` 后 `ADD CONSTRAINT … NOT VALID` 再 `VALIDATE`，避免长时间持锁），删除取值先迁移数据。守护（禁 `CREATE TYPE ... AS ENUM`）。
7. **jsonb 边界**：`jsonb` 只用于本来无固定结构的载荷（事件归档、外部原文、幂等应答体），领域字段必须是正式列；每个 `jsonb` 列在注册表登记用途并有 `jsonb_typeof` CHECK。守护。
8. **大表规划**：保留期超过 3 年或预计超过千万行的只追加表（审计、事件归档、过程归档等），在建表时定好分区键（按时间范围）与归档 / 清理方式，登记在注册表；上线后再分区要重写全表。复审。
9. **上线后迁移安全**（基线开放标记删除后生效）：表结构变更只能「先扩后缩」（expand / contract）；加索引用 `CREATE INDEX CONCURRENTLY`（单独迁移、非事务）；迁移设 `lock_timeout` 与 `statement_timeout`；禁止会重写全表的变更（如改列类型、加带易变默认值的列）；删列、改名分两次发布。守护（迁移脚本静态检查）。
10. **高频更新行**：同一行被反复更新的表（编号计数格、幂等登记等）可设 `fillfactor`，减少膨胀与锁竞争；属性能建议，按需使用并在表注释说明。

### 2.5 列级敏感分级

| 级别 | 含义 | 处理 |
|---|---|---|
| `PUBLIC` 公开 | 可对外 | — |
| `INTERNAL` 内部 | 默认级别 | 登录可见，按功能权限 |
| `SENSITIVE` 敏感 | 个人信息、工资与日薪、银行卡（GUIDING_PRINCIPLES §6） | 脱敏展示、按法人 × 部门 × 项目角色授权、访问留痕；不进分析层明细 |
| `RESTRICTED` 高度敏感 | 口令、密钥、会话内容 | 不出库（只在本模块内部使用）、不进日志、不进分析层 |

登记在 `backend/architecture/data-classification.txt`（`<schema.table.column> | <级别> | <理由>`），未登记的列默认 `INTERNAL`。守护：`DdlNamingTest` 要求名字像敏感信息的列（口令、密钥、TOTP、银行、工资、日薪、证件、手机、邮箱、生日、性别、地址、会话内容）必须登记为 `SENSITIVE` 或 `RESTRICTED`，登记的列必须存在。web / api 出参不得直接暴露 `SENSITIVE`、`RESTRICTED` 列，这一守护由 1a 会话实现。

### 2.6 HTTP 接口约定（用户 2026-10-15：对原则核对 A 的待决项答「全部按推荐 (Recommended)」）

适用于全部 `/api/v{n}/…` 接口；由 `proposals/PHASE1A_PLATFORM_CONVENTIONS.md` §2–3 升格而来，那两节不再是现行规则。路径与命名见 §2.3；接口版本与契约演进见 ADR-0008 与 ADR-0035。守护见 §4「待实现的守护」G-A1–G-A9，未实现前由每波复审逐条检查。

- **A-1 资源与命令**：读取用 `GET`；在集合上创建用 `POST /{复数资源}`；整体替换可编辑字段用 `PUT /{复数资源}/{id}`；状态转换与业务动作写成命令 `POST /{复数资源}/{id}:{动作}`（动作取自 `terms.yaml` 的 `standard_actions`），不用 `PUT` 改状态；删除用 `DELETE`，只用于允许删除的对象（如未提交的草稿），业务上的「作废」「撤销」是命令。不改变任何状态的只读计算（如试算、预览）可用 `GET /{复数资源}:{动作}` 或 `GET /{复数资源}/{id}:{动作}`，参数放查询串，不得有副作用。
- **A-2 状态码**：创建返回 `201`，带 `Location` 与 `ETag`；命令返回 `200`（响应体为命令后的资源）或 `204`；删除返回 `204`；读取返回 `200` 与 `ETag`。契约中的状态码与运行时一致。
- **A-3 列表**：响应一律为 `{items, nextCursor}`；参数 `cursor`（不透明，内部编码排序键 + `id`）与 `limit`。`limit` 默认 50、上限 200，两个值是全系统唯一的常量（kernel），OpenAPI 声明 `maximum: 200`，超出上限返回 400，不静默截断。列表一律按索引做键集分页，不在内存中切片（GUIDING_PRINCIPLES §7）；需要总数时另设 `GET /{复数资源}:count`，与列表用同一组过滤参数。不分页的小表上限见 GUIDING_PRINCIPLES §7a R-P2。
- **A-4 过滤与排序**：过滤参数为 camelCase 字段名，多值用逗号分隔，不提供通用表达式语言；排序写 `sort=field,-field`，只接受白名单字段；未知参数、白名单外的字段返回 400。导出接口与对应列表使用同一组过滤参数。
- **A-5 不提供 `PATCH`**：局部修改用 `PUT` 整体替换可编辑字段，或用具名命令表达业务意图。
- **A-6 批量**：写成 `POST /{复数资源}:{动作}-batch`，一次最多 100 项（与 GUIDING_PRINCIPLES §7a R-P2 同一上限；出处：one-coordinator 2026-10-15 在两份推荐值（100 / 200）中取较严者，用户「全部按推荐」）；逐项鉴权、逐项执行，各项各自成败。响应 `200`，按请求顺序返回逐项结果（对象 ID、结果、失败时的 ProblemDetail），部分成功也返回 `200`；整批格式不合法时返回 400、一项都不执行。
- **A-7 类型表示**：
  - 自有 ID 一律是字符串 uuid：JSON string，OpenAPI `type: string, format: uuid`（ADR-0033 第 6 项第 2 条），属性、路径参数与查询参数同样；前端把它当不透明字符串，不做数值运算、不解析；来源系统的 ID 不进接口（ADR-0033）；
  - 时刻用 ISO-8601 UTC，带 `Z`（`2026-10-15T02:00:00Z`），不用 `+08:00` 偏移，不用纪元毫秒；业务日期用 `YYYY-MM-DD`，按业务时区（`Asia/Shanghai`）取日；
  - 金额为 `{amount, currency}`，`amount` 是十进制字符串；比率为 0–1 的十进制字符串；其他十进制量（工时、人天）同样用字符串；
  - 状态、类型等取值在 OpenAPI 中是具名 `enum` schema，不用无约束的 `string`。
- **A-8 幂等与并发**：
  - 有副作用的 `POST` 接受 `Idempotency-Key`：服务端按「用户 + 键」保存 24 小时的应答（状态、正文、`ETag`、`Location`），重复请求原样回放；同键不同请求体返回 422，首个请求仍在处理时返回 409；处理中占位超过配置的超时后允许接管；5xx 不保存。会话与登录状态端点（路径含 `/sessions`、以 `/login-states` 结尾）永不保存应答正文，也不在契约中声明该请求头；范围由 `IdempotencyScope` 唯一表达，`HttpContractIT.idempotencyKeyIsOfferedExactlyWhereItIsStored` 守护。
  - 修改已有资源的 `PUT`、`DELETE` 与命令必须带 `If-Match: "<version>"`：缺失返回 428，版本不一致返回 412；契约中声明该请求头为必填，并声明 412 / 428。版本不放在请求体。确实不需要版本的命令（如强制登出）在守护中登记豁免与理由。
  - 409 只表示「当前状态不允许该操作」（状态转换不合法等），不用于并发冲突。
- **A-9 错误**：一律 RFC 9457 ProblemDetail，`type` 与 `code` 取自 `ErrorCode`；通用错误只在 kernel 定义（§2.3、G48）；每个接口在 OpenAPI 中声明自己可能返回的错误状态码；5xx 的响应体只带关联号（correlationId），细节只进日志。错误响应由一处统一生成。
- **A-10 访问控制在接口上的表现**：未登录 401；没有功能权限 403；有功能权限但单个资源不在本人数据范围内时返回 404，不暴露资源是否存在；列表按数据范围过滤，不报错；数据范围由会话决定，不作请求参数。
- **A-11 出参**：响应体一律是 `web` 包内的 `*Response` record，不直接返回 application 层的 `*View` 或领域对象；`SENSITIVE` 字段按 §2.5 掩码，`RESTRICTED` 字段不出现。

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
| G48 | 错误码：每个模块一个实现 `kernel.ErrorCode` 的枚举，格式 `{SUBDOMAIN}_{BC}_{REASON}`；通用错误只在 `kernel` 定义，模块枚举不得与之或彼此同义（中文名唯一，R-5 守护） | 错误码散落为字符串；同义错误码各模块各一份 | `NamingSurfaceTest` |
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

**待实现的守护**（用户 2026-10-15：对 DDL 审计、原则核对 A / B 的待决项答「全部按推荐 (Recommended)」）：以下守护**只登记、尚未实现**，状态均为「待实现」。规则正文在「出处」一列所指的地方，本表只写检查什么、何时必须有、归属哪个工作单元。**归属由 one-1a-next-2 在计划中排定**：「归属」一列中已由 ADR 或用户决定落定的写工作单元名，其余写「one-1a-next-2 排定」，括号内是初拟的分组，供排定参考。未实现前由每波复审按出处逐条检查。与已有守护重叠的，实现时合并为一个测试，不重复写。

| # | 检查什么 | 出处 | 必须在何时之前 | 归属 |
|---|---|---|---|---|
| G-A1 | OpenAPI 中所有自有 ID（属性、路径参数、查询参数）为 `type: string, format: uuid`；前端生成类型中 ID 不是 number | §2.6 A-7 | 立即（转交 / 改派界面上线前） | R-1b（与自有 UUIDv7 同改） |
| G-A2 | 列表响应形状为 `{items, nextCursor}`，参数为 `cursor` / `limit` | §2.6 A-3 | 首次部署前 | one-1a-next-2 排定（初拟：HTTP 契约守护；与 G-X3 合并） |
| G-A3 | 并入 G-C4（修改类接口声明必填 `If-Match` 与 412 / 428，运行时一致；豁免逐条登记理由） | §2.6 A-8、ADR-0035 第 8 条 | 见 G-C4 | 见 G-C4 |
| G-A4 | 并入 G-C4（集合上的 `POST` 返回 201 并带 `Location`） | §2.6 A-2、ADR-0035 第 8 条 | 见 G-C4 | 见 G-C4 |
| G-A5 | 没有 `PATCH` 映射 | §2.6 A-5 | 1b 前 | one-1a-next-2 排定（初拟：HTTP 契约守护） |
| G-A6 | 时刻字段为带 `Z` 的 `date-time`；金额为 `{amount: string, currency}` | §2.6 A-7 | 1b 前 | one-1a-next-2 排定（初拟：HTTP 契约守护；金额部分与 G-M5 合并） |
| G-A7 | 控制器返回类型只能是 `web` 包内的 `*Response` | §2.6 A-11 | 首次部署前 | one-1a-next-2 排定（初拟：HTTP 契约守护；与 G-X6 互补） |
| G-A8 | kernel 以外不得定义通用错误码 | §2.3、§2.6 A-9 | 首次部署前 | R-5（与 G48 同一实现） |
| G-A9 | 错误响应只由统一的处理器生成，控制器不手工拼 ProblemDetail | §2.6 A-9 | 1b 前 | one-1a-next-2 排定（初拟：HTTP 契约守护） |
| G-C1 | `scripts/ci/openapi_compat.py`：与 merge-base 的 OpenAPI 比对；读取弃用登记，已登记且到期的删除放行；其他破坏性变更即失败，只能用提交 trailer `Contract-Break:` 放行并在 PR 中单列 | ADR-0035 第 3、7、9 条 | 首次生产部署前 | one-1a-next-2 排定（初拟：契约演进守护） |
| G-C2 | 事件兼容（AsyncAPI 形状比对）：`asyncapi.yaml` 各消息的负载 schema 与类名同 merge-base 比对，形状或类名、包名有变化即判为破坏性，要求 `schemaVersion + 1` 或新事件类型二选一，并把本次发布标为「需排空」 | ADR-0035 第 3、5、9 条 | 首次生产部署前 | 同上 |
| G-C3 | 部署前排空预检：标为「需排空」的发布，在开启维护模式（库内标记 `platform_scheduling.maintenance_modes`）之后检查未完成的事件发布与死信为 0，排不空的经隔离登记处理，否则中止部署 | ADR-0035 第 6 条 | 首次生产部署前 | 同上（接入部署剧本，one-ops） |
| G-C4 | 契约如实：OpenAPI 的状态码（201 与 `Location`）、必填请求头（`If-Match`、412 / 428）、`ETag`、具名枚举、无无类型响应体、`info` 与运行时一致（扩展 `HttpContractIT`；G-A3、G-A4 并入）；dev 期生效的两条契约守护之一，另一条为 G-C5 | ADR-0035 第 2、8 条、§2.6 | H-1 合入即生效（H-1 排在 R-5 之后） | H-1 |
| G-C5 | 事件负载与出站负载扁平：ID 为字符串 uuid，不出现 `{value: …}` 包装（dev 期生效的两条契约守护之一，另一条为 G-C4） | ADR-0035 第 2、5 条 | R-1b 合入即生效 | R-1b |
| G-C6 | 弃用到期：定时检查到期未删的弃用项，发 P3 提醒并报调度会话（不让无关 PR 变红）；PR 中改动已弃用对象（删除除外）即失败 | ADR-0035 第 7 条 | 首次生产部署前 | one-1a-next-2 排定（初拟：契约演进守护） |
| G-C7 | `reporting` 数据集契约：视图列名、类型与登记一致，破坏性变更按 ADR-0035 处理 | ADR-0035 | R1 | R1 工作单元 |
| G-P1 | `limit` 上限只来自 kernel 一处常量，OpenAPI `maximum: 200`；禁止其他 `MAX_LIMIT` 类常量 | §2.6 A-3、GUIDING_PRINCIPLES §7a R-P2 | 1b 前 | one-1a-next-2 排定（初拟：HTTP 契约守护；与 G-X3 合并） |
| G-P2 | 列表与批量用例的 SQL 语句数不随行数增长（计数的 DataSource 装饰器，默认预算 ≤ 5） | GUIDING_PRINCIPLES §7a R-P3 | 1b 前 | one-1a-next-2 排定（初拟：性能守护） |
| G-P3 | 登记的查询路径在 `enable_seqscan = off` 下 `EXPLAIN` 走索引 | GUIDING_PRINCIPLES §7a、SESSION_PLAYBOOK §14.1 D19 | 1b 前 | 同上 |
| G-P4 | 外部只读连接三种超时生效：对挂起端口的集成测试在预算内失败或降级 | SESSION_PLAYBOOK §14.3 I2 | 首次部署前 | 同上（与 G-I1、G-I3 同卡） |
| G-P5 | 生产配置中连接池、语句超时、直方图桶、慢查询阈值存在且取值符合 R-P4–R-P6 | GUIDING_PRINCIPLES §7a | 首次部署前 | 同上 |
| G-P6 | k6 压测：R-P1 各类接口达标（阶段出口） | GUIDING_PRINCIPLES §7a R-P1 | Phase 2 入口 | one-1a-next-2 排定（初拟：性能守护；安装 k6 前按 CLAUDE.md 请示） |
| G-S1 | `scripts/ci/workflows_pinned.py`：工作流 `uses:` 一律固定 40 位 SHA；禁 `pull_request_target` | SUPPLY_CHAIN.md S6 | 首次部署前 | one-1a-next-2 排定（初拟：CI 供应链） |
| G-S2 | osv-scanner：漏洞与许可证检查（并入已有作业，不加作业数） | SUPPLY_CHAIN.md S3、S4 | 首次部署前 | 同上 |
| G-S3 | `pom.xml` 不声明自定义仓库，只用 Maven Central | SUPPLY_CHAIN.md S1 | 首次部署前 | 同上 |
| G-S4 | 测试数据库镜像只在一处常量定义，带摘要 | SUPPLY_CHAIN.md S7 | 首次部署前 | 同上 |
| G-S5 | 发布包含 SBOM，且 SBOM 写入签名声明 | SUPPLY_CHAIN.md S5 | 生产部署前 | 同上 |
| G-S6 | runner 作业前钩子检查运行用户与凭据目录 | SUPPLY_CHAIN.md S6 | 待决（随 runner 专用用户的决定） | 同上 |
| G-O1 | 日志扫描：`ERROR` 行必须以「ALERT P1\|P2\|P3 <子域>.<BC> 」开头 | GUIDING_PRINCIPLES §11 O-2 | 首次部署前 | one-1a-next-2 排定（初拟：可观测性） |
| G-O2 | 日志参数不得是个人信息类方法的返回值；未知异常只记类名 | GUIDING_PRINCIPLES §11 O-3 | 首次部署前 | 同上 |
| G-O3 | 集成测试断言每行日志有 correlationId，唯一约束冲突中的邮箱等值不落盘 | GUIDING_PRINCIPLES §11 O-1、O-3 | 首次部署前 | 同上 |
| G-O4 | 生产配置暴露 Prometheus 端点、连接串含 `logServerErrorDetail=false`；部署后抓取到的业务指标数为 0 即失败 | GUIDING_PRINCIPLES §11 O-3、O-4 | 首次部署前 | 同上（部署后检查归 one-ops） |
| G-O5 | 告警规则引用的指标在应用中存在 | GUIDING_PRINCIPLES §11 O-6 | 首次部署前 | 同上 |
| G-O6 | 关联断开计数：事件发布时缺 correlationId 计数并告警，不静默新建 | GUIDING_PRINCIPLES §11 O-1 | 1b 前 | 同上 |
| G-I1 | 外部连接只经集成层的连接工厂，工厂强制连接、查询、锁等待三种超时 | SESSION_PLAYBOOK §14.3 I2 | 首次部署前 | one-1a-next-2 排定（初拟：外部集成韧性） |
| G-I2 | 集成登记册 `backend/architecture/integration-register.yaml` 与代码中的适配器一致 | SESSION_PLAYBOOK §14.3 | 首次部署前 | 同上 |
| G-I3 | 用 `WAITFOR` 挂住的视图验证超时生效与降级 | SESSION_PLAYBOOK §14.3 I2、I7 | 首次部署前 | 同上 |
| G-I4 | 用户路径冷却：连续失败 3 次后 60 秒内不再调用 | SESSION_PLAYBOOK §14.3 I6 | 1b 前 | 同上 |
| G-I5 | 一次故障只记一次带堆栈的 `ERROR` | SESSION_PLAYBOOK §14.3 I10 | 首次部署前 | 同上 |
| G-MG1 | `legacy_numbers` 形状：UUIDv7 主键、来源系统列、`uk(来源系统, 单据类型, 原编号)`、`migration_batch_id uuid NOT NULL`、只追加 | ADR-0033；ADR-0036（另行 PR） | 首次部署前 | R-1b |
| G-MG6 | 批次术语唯一：代码与表结构中只出现「迁入批次」一种写法（R-1b 改名后给 `migration_batch` 加禁用写法 `import_batch` / `ImportBatch`） | 用户 2026-10-15 批次术语统一；ADR-0036（另行 PR） | 首次部署前 | R-1b |
| G-D12 | 枚举 `CHECK` 已命名为 `ck_<表>_<列>`，取值集合与对应 Java 枚举一致（与 G-DDL8 同一测试） | §2.4、§2.4a 第 6 条 | 首次部署前 | R-2a |
| G-DDL1 | 交易、主数据、参考、配置表在注册表登记 `business_key` 且有对应唯一约束；外键目标必须是主键 | §2.4a 第 1 条；DDL 审计（2026-10-15）守护 G1 | 首次部署前 | one-1a-next-2 排定（初拟：基线 DDL 收紧） |
| G-DDL2 | 外键列有以该列开头的索引 | §2.4a 第 2 条；DDL 审计（2026-10-15）守护 G2 | 首次部署前 | one-1a-next-2 排定（初拟：基线 DDL 收紧） |
| G-DDL3 | 没有重复或冗余索引（前缀相同、与唯一约束重复） | §2.4a 第 2 条；DDL 审计（2026-10-15）守护 G3 | 首次部署前 | one-1a-next-2 排定（初拟：基线 DDL 收紧） |
| G-DDL4 | 查询路径登记且 `EXPLAIN` 走索引（与 G-P3 合并为一个测试；依赖 G-DDL12） | §2.4a 第 2 条、SESSION_PLAYBOOK §14.1 D19；DDL 审计（2026-10-15）守护 G4 | 1b 前 | one-1a-next-2 排定（初拟：基线 DDL 收紧） |
| G-DDL5 | 可空列注释写明「为空表示」；文本列有空串 CHECK；默认值只在技术默认白名单内 | §2.4a 第 3 条；DDL 审计（2026-10-15）守护 G5 | 首次部署前 | one-1a-next-2 排定（初拟：基线 DDL 收紧） |
| G-DDL6 | `CASCADE` 登记、禁软删除标志列、物理删除只在白名单内 | §2.4a 第 4 条；DDL 审计（2026-10-15）守护 G6 | 首次部署前 | one-1a-next-2 排定（初拟：基线 DDL 收紧） |
| G-DDL7 | `_code` 列有格式 CHECK；自由文本有长度上限；不区分大小写的唯一建在规范化值上 | §2.4a 第 5 条；DDL 审计（2026-10-15）守护 G7 | 首次部署前 | one-1a-next-2 排定（初拟：基线 DDL 收紧） |
| G-DDL8 | 禁 `CREATE TYPE … AS ENUM`；枚举 CHECK 命名并与 Java 枚举一致（与 G-D12 同一测试） | §2.4a 第 6 条；DDL 审计（2026-10-15）守护 G8 | 首次部署前 | one-1a-next-2 排定（初拟：基线 DDL 收紧） |
| G-DDL9 | `jsonb` 列已登记用途并有 `jsonb_typeof` CHECK | §2.4a 第 7 条；DDL 审计（2026-10-15）守护 G9 | 首次部署前 | one-1a-next-2 排定（初拟：基线 DDL 收紧） |
| G-DDL10 | 长期保留或大表在注册表登记规模与分区决定 | §2.4a 第 8 条；DDL 审计（2026-10-15）守护 G10 | 首次部署前 | one-1a-next-2 排定（初拟：基线 DDL 收紧） |
| G-DDL11 | `scripts/ci/migration_safety.py`：`CONCURRENTLY`、`lock_timeout` / `statement_timeout`、禁全表重写、外键与 CHECK 用 `NOT VALID`、先扩后缩、无无效索引 | §2.4a 第 9 条；DDL 审计（2026-10-15）守护 G11 | 首次部署前（C-3 删基线标记前合入） | one-1a-next-2 排定（初拟：基线 DDL 收紧） |
| G-DDL12 | 测试库排序规则为 C（与生产一致）；迁移后无无效索引；高频更新表的 fillfactor 有注释 | §2.4a 第 2、10 条；DDL 审计（2026-10-15）守护 G12 | 首次部署前 | one-1a-next-2 排定（初拟：基线 DDL 收紧） |
| F15 | VI 色值以 sRGB 十六进制登记在 palette；`oklch` 必须落在 sRGB 色域内 | §4a U1 | D-8 首次 design-sync 前 | one-1a-next-2 排定（初拟：前端设计系统） |
| F16 | 令牌配对的文字与非文字对比度达标 | §4a U2 | D-8 首次 design-sync 前 | 同上 |
| F17 | `eslint-plugin-jsx-a11y` 规则 | §4a U3 | 1b 前 | 同上 |
| F18 | 组件测试中跑 axe | §4a U3 | 1b 前 | 同上 |
| F19 | 应用根部设置 antd `zhCN` 与 dayjs `zh-cn` | §4a U4 | 首次部署前 | 同上 |
| F20 | `check-bundle-size.mjs`：gzip 体积超过预算即失败 | §4a U8 | 1b 前 | 同上 |
| F21 | PC 与 H5 的语义组件契约类型一致（类型测试） | §4a U7 | 1b 前 | 同上 |
| F22 | 查询视图三态结构检查 | §4a U5 | 1b 前 | 同上 |

## 4a. 前端界面规则（用户 2026-10-15：对原则核对 B 的待决项答「全部按推荐 (Recommended)」）

适用于 PC 与 H5。守护见上表 F15–F22。

- **U1 品牌色与字体**：VI 色值按 sRGB 十六进制写入设计令牌的原始调色板，语义层只引用调色板：FOR 灰 `#595757`、CITIS 红 `#CF000E`（精确值，取代现有近似的 `oklch` 值，并同步设计令牌正本）。使用 `oklch` 时取值必须落在 sRGB 色域内。品牌字标用 SVG，不用字体渲染。Montserrat 等字体按 OFL 随包分发，字体文件在 1b 前入库。
- **U2 对比度**：文字对比度 ≥ 4.5:1（大字 ≥ 3:1），非文字元素（图标、边框、焦点环）≥ 3:1，按令牌实际配对（文字色 × 所在背景）计算。三级文字色与待审批状态文字色加深到达标，其余配对保持。
- **U3 无障碍**：全部操作键盘可达，焦点可见，弹层关闭后焦点回到触发处；错误提示 `role="alert"`，非打断的状态提示 `role="status"`（H5 同样）；只有图标的按钮带 `aria-label`。
- **U4 语言与格式**：只做简体中文，不做多语言；antd 设 `zhCN`、dayjs 设 `zh-cn`；日期、时刻、金额、数量的格式化只经 core 的格式化函数。
- **U5 加载、空、错误三态**：数据视图统一经查询视图组件呈现三态；5xx 错误显示可复制的「关联号」（即 correlationId，与 §2.6 A-9 同一个值），便于报障时对日志。
- **U6 不做乐观更新**：单据、审批、金额相关的命令等服务端确认后再更新界面；只有无业务后果的界面偏好可乐观更新。
- **U7 组件契约只定义一次**：语义组件（状态标签、金额、审批动作等）的契约只在 `shared/design-system` 定义一次，PC 与 H5 各自实现同一契约；core 只用 React 18 API。
- **U8 包体积预算**（gzip）：PC 入口 ≤ 300 KB，单个路由 ≤ 150 KB，H5 入口 ≤ 200 KB；超出即 CI 失败，调整预算须在 PR 中写理由。

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
| 20 | 日志字段、追踪属性 | 结构化日志键 camelCase，业务键取词表；字段集与追踪键见 GUIDING_PRINCIPLES §11 O-1 | G-O1、G-O3（待实现） | ○ |
| 21 | Git 分支、提交、PR | 沿用老仓库约定（`feature/issue-{N}-…`、`type(scope): 中文`，scope 用子域 slug） | 老仓库 pr-hygiene | ○ 新仓库 CI 复用 |

**本次统一时发现并更正的不一致**：
- HTTP 示例 `/api/v1/timesheet/timesheets` 把 BC 当成子域，已改为 `/api/v1/cost/timesheets`；
- 「事件载荷字段 snake_case」与接口 camelCase 不一致，已统一为所有 JSON 用 camelCase；
- Flyway 描述明确用英文 snake_case（老仓库使用中文描述）；
- 收入确认口径：词表「投入法、产出法并存」与定稿规则 ACC-R01「统一产出法 · 客户价值确认」冲突，以 ACC-R01 为准（ADR-0016）。

**G41–G48 的已知盲区**：运行时拼出的指标名；`.yml` 中的配置键；Flowable `candidateGroups` 名；DMN 文件与 `job_type` 表尚无真实样例（只用注入用例验证）；测试数据迁移不受 G44 约束；事件发布登记表未启用，事件键经应用的 JSON 映射器检查；写成普通字符串的错误码检测不到。模板为满足守护新增了 4 个词条（cost、error-code、capacity、scheduler-lock），状态为提议，进入下一轮用语确认。
