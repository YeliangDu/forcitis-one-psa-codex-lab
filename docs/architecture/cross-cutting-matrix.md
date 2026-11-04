> 冻结（2026-11-04）：横切关注点矩阵，不再更新；已答事项落在 ADR-0031、0043、0045、0046、0053、0060，现行规则见 `docs/rules/coding-standard.md`，现行决定见 `docs/adr/current.md`。

# 横切关注点矩阵

日期：2026-10-14 · 会话：one-crosscut（由 one-coordinator 派出）· 决定单：`CROSS_CUTTING_DECISIONS.md`

2026-10-16 按 main cf3b9a3e 重核状态（one-architecture，见 docs/architecture/proposals/foundation-recheck.md）

用户原话：问「怎么才能将类似问题一次识别出来呢？」，对普查方案答「启动」。起因是用户指出「money 的尾差问题是很重要的问题，尤其我们涉及到分摊」。

**横切关注点**指所有阶段共用、晚补代价成倍的问题。本矩阵把这类问题一次列全，逐项核对证据，并分成四类：

- **地基**：应在 1a 下一批做进 kernel 或 platform，附「晚补代价」。其中 1a 会话已排进本批修复分支的，标「地基 · 本批处理中」。
- **待决**：需要用户或财务决定，题目在决定单里（XD- 为用户题，FQ- 为财务题）。
- **阶段**：写进某个阶段流程梳理的入口条件。
- **已覆盖**：已有决定、实现和守护，给出证据。

不编造覆盖：没有证据的一律记为缺口。

## 证据来源与缩写

本次是只读普查：五路 sonnet 子代理各负责一类证据源，本会话汇总后再抽查关键出处。

| 缩写 | 指什么 |
|---|---|
| K/ | `backend/src/main/java/com/forcitis/one/kernel/`，以 PR #72 分支 `claude/psa-1a-fix-integration` 为准 |
| M/ | `backend/src/main/java/com/forcitis/one/`（#72） |
| T/ | `backend/src/test/java/com/forcitis/one/`（#72） |
| DB/ | `backend/src/main/resources/db/migration/`（#72） |
| BR | `docs/business/business-rules.md` |
| GL | `docs/business/rules/gl-rules.md` |
| GP | `GUIDING_PRINCIPLES.md` |
| NS | `NAMING_STANDARD.md` |
| PC | `docs/architecture/proposals/phase1-a-platform-conventions.md` |
| EAF | `docs/architecture/events/event-analytics-fields.md` |
| DS | `docs/data/data-scorecard.md` |
| 契约 | `docs/business/processes/contract.md`（D-1a-n） |
| brief | `MODERNIZATION_BRIEF.md`（行号是生成后的行号） |
| FQ | `docs/business/finance-questions.md`，以及确认单 v2（FQ-01..22 全部未答） |

## 1. 统计

| 类别 | 条数 |
|---|---|
| 地基（含本批处理中 1 条、已排 1 条） | 21 |
| 待决（全部待财务） | 6 |
| 阶段 | 33 |
| 已覆盖 | 23 |
| 决定层已覆盖（ADR-0031，提议） | 3 |
| **合计** | **86** |

2026-10-15 按用户提交的业财架构核查清单新增 §2.9 共 11 行（CC-B01..B11）：地基 4 行、阶段 5 行、已覆盖 2 行。

首版（2026-10-14）有待决 11 条。待决行的题号，加上另外 8 道由地基行和证据冲突引出的题，共 19 题，汇总在 `CROSS_CUTTING_DECISIONS.md`。2026-10-15 用户答了 7 题（XD-01、02、03、07、08、09、10），另有 3 题（XD-04、05、06）确认为已有决定。因此 5 行改了类别：CC-N11、CC-I01 改为地基，CC-E04 改为地基（已排），CC-T08、CC-P02 改为已覆盖。剩下的 6 条待决都等财务答复（FQ-23、24、26、27、28、29）。

每条只计一个主类别；有次要类别时在「缺口」列注明。

## 2. 矩阵

### 2.1 数值与金额

| 编号 | 关注点 | 首次阶段 | 用户 / 财务决定 | ADR / 契约 | 实现现状 | 守护 | 缺口 | 类别 |
|---|---|---|---|---|---|---|---|---|
| CC-N01 | 舍入点与中间精度 | 1b（RULE-141 工作包值 = 工时 × 费率） | RULE-141 评审：「四舍五入到分；汇总由服务端计算」（brief §5 1b） | ADR-0006:12（`Money` 统一 2 位小数） | `K/Money.java:16` 构造即 `setScale(2, HALF_UP)`，`plus`、`minus`、`times` 每一步都舍入；没有不舍入的计算类型 | `T/kernel/MoneyTest`（只测单步 `times`） | 连乘会多次舍入，例如 工时 × 日薪 ÷ 7.5 × 比率；没有「只在形成金额时舍入一次」的类型 | **地基**（§3.2） |
| CC-N02 | 舍入模式 | 1b | 1b 已定「四舍五入到分」（RULE-141、RULE-142）；会计口径（凭证、分摊）财务未确认 | 无 | `HALF_UP` 写死在 `K/Money.java:16` | 无 | 老系统有三处用 `DOWN` 或 `FLOOR`（BR 场景 3、4、12，见 §3.4）；凭证口径未经财务确认 | **待决**（FQ-23） |
| CC-N03 | 按权重拆分原语：总额守恒、确定性、偏差有界 | 1b（报价条款拆分 RULE-159；整单尾调 RULE-142）；2（人天拆分 RULE-004） | 无 | 无；GP:92 只规定「取整、分摊写在领域层」 | 无 `divide`、无拆分原语（`K/Money.java`） | 无 | 各模块会各自手写 `divide`，老系统就是这样形成了三种倒挤法；`allocate` 已被术语 `allocate_indirect_cost`（terms.yaml:2813）占用，拆分原语须另立术语 | **地基**（§3.3；XD-08「先按建议建」、XD-07 术语「按权重拆分 prorate」已定，2026-10-15） |
| CC-N04 | 尾差归属规则 | 1b | 无；brief 把「分摊余数归属」放在 Phase 4 入口（brief:208），晚于首次需要 | 无 | 老系统三种做法：最大余数法（年终奖）、全部记入最大行（OH）、记入最后一行（重分类、商机费用、差旅）；OH 的文档写最大余数法，代码却是全部记入最大行（BR:1316，RULE-069 疑似缺陷） | 无 | 无统一约定；入口条件放错了阶段 | **待决**（FQ-24；入口条件已按 XD-09「提前到 1b」以 Proposed revision 移到 Phase 1b） |
| CC-N05 | 冲销镜像 | 2（首个过账事实：工时成本） | ADR-0022 评审 C-8 选「红字」；冲销按原分录逐行取反，不按现行规则重推（ADR-0022:32） | ADR-0016:23；SP 附 M3「已过账事实」 | `K/Money.java:39` 有 `negate`；拆分结果的镜像（拆 −X 是否等于拆 X 后取负）无从谈起；冲销工厂不存在 | G34 只覆盖审计表只追加 | 老系统红冲是「按当前规则重算再取负」（GL-076），与 ADR-0022 相反；部分冲销的拆分规则未定 | 决定层已覆盖（ADR-0031，提议） |
| CC-N06 | 币种与小数位 | 1b | 只用人民币（GL-124；GL_COMPLIANCE_CHECKLIST:40「外币折算暂不需要」） | ADR-0006:12（2 位）；NS:116（ISO 4217） | `SCALE = 2` 固定，不看 `Currency.getDefaultFractionDigits`；`Money.of("1","JPY")` 会静默得到 2 位 | 无 | 不是 2 位小数的币种被静默接受；DDL 无 `CHECK (length(currency)=3)` | **地基**：拒绝不是 2 位小数的币种。不改 ADR-0006；按币种取小数位推迟到出现外币时，理由是现在只有人民币，做了也无从验证 |
| CC-N07 | 汇率与折算 | R0（判定是否真有外币）；G2（现金流量表汇率变动行） | 外币折算「暂不需要」 | EAF:73（汇率不进事件，由语义层折算） | 无 | 无 | 老 BI 有财务手工汇率 CSV（BI_SCOPE:64），但用途不明 | **阶段**：R0 入口「确认手工汇率 CSV 的用途；有外币则新开 ADR」 |
| CC-N08 | 税额计算、价税分离、发票误差容忍 | 1b（RULE-144 按表头税率拆税） | RULE-144 不复刻：不含税 = Σ行 + 尾调 ÷（1+税率），不用 1.06；收入「不含税为准，含税另列」（intent 经营分析 Q6-1） | ADR-0016:27（含税存储，另存不含税额与税额）；EAF:53 | 无 | 无 | 税额的计算方式（减法得出还是乘税率）、逐行还是整单、发票与账面的误差容忍度都没有决定；数电对接时（6b）误差容忍是硬约束 | **待决**（FQ-27） |
| CC-N09 | 工时精度、步长与冲销表示 | 2 | 「`Hours` 非负」（ADR-0006:12） | ADR-0006 | `K/Hours.java:13-16` 拒绝负数，2 位 `HALF_UP`；`numeric(10,2)`（DB/cost_timesheet …:10） | `MoneyTest.hoursCannotBeNegative`；没有 `HoursTest` | 冲销工时成本时，事实行的工时符号无从表达；步长、单日上限（老系统 15 小时）待 Phase 2 规则评审 | **阶段**：Phase 2 流程梳理入口「工时冲销行的数量表示（带符号数量还是冲销标记）已定；如需负数，新 ADR 取代 ADR-0006 第 1 条的『非负』」 |
| CC-N10 | 费率、单价、比率的中间精度类型 | 1b（工时 × 费率） | 无 | NS:117-118（`_rate`、`_pct` 为 numeric，未定小数位） | 无类型，只能写 `money.times(hours.value())` | 无 | 日薪 ÷ 7.5 是除不尽的小数；老系统比率入库 8 位，代码却用 10 位（BR 场景 2） | **地基**（§3.2 `Rate`、`Ratio`） |
| CC-N11 | 百分比与比率的表示 | 1b（毛利率） | 无 | 无 | 无 | 无 | 老系统四种口径混用：0–1、×100、整数百分数、阈值 100/120（BR A9）。XD-02 已答「一律 0–1 小数 (Recommended)」（2026-10-15） | **地基**（`Ratio` 随 §3.2 一起做；原为待决 XD-02） |
| CC-N12 | 计量单位（小时、人天、标准日 7.5 小时） | 2 | 人天口径待评审（brief:166） | ADR-0023:31（指标须声明单位） | 无 | 无 | 7.5 小时是否按法人区分、人天保留几位 | **阶段**：Phase 2 流程梳理入口 |
| CC-N13 | 金额与比率列的 DDL 精度 | 1b | 无 | NS:115-118 | 现行无 `_amount` 列；只有 `numeric(10,2)` 工时 | `T/DdlNamingTest.java:58-75`（只比对类型名）、`:538-540`（币种成对，只查 `CREATE TABLE`） | 不检查 p、s；`ALTER TABLE ADD COLUMN` 不检查币种成对 | **地基**（§3.5 G-M3） |
| CC-N14 | 接口与前端的金额表示和格式化 | 1b | 无 | 无 | 前端无 `Intl.NumberFormat`、无千分位、无负数样式、无数字输入组件（frontend/packages 全量检索） | 无 | 金额若以 JSON number 传输，前端按双精度浮点运算会出错；表示方式须在第一个金额接口之前定 | **地基**（XD-01 已答「十进制字符串 + 币种」，2026-10-15） |
| CC-N15 | 领域代码禁止自行舍入和除法 | 1b | 无 | 无 | `M/` 目前只有 kernel 用到 `setScale`（`K/Money.java:16`、`K/Hours.java:16`） | `T/ArchitectureRulesTest.java:193-201` 只查字段名；`:292-298` 禁 `BigDecimal.equals` 与 `new BigDecimal(double)` | 没有规则禁止 kernel 以外调用 `setScale`、`divide`、`round`、`RoundingMode`、`MathContext`、`doubleValue` | **地基**（§3.5 G-M1） |
| CC-N16 | 先舍入明细再汇总，还是先汇总再舍入 | 1b（RULE-142 合计取整到分） | RULE-141：汇总由服务端算 | ADR-0025:122（只有可加性测试） | 无 | 无 | 老结转凭证借方先汇总再舍入，贷方逐行舍入，凭证会差几分（BR:1673，RULE-089）；预算页与进度页不一致（RULE-181） | **待决**（FQ-26） |
| CC-N17 | 对账与校验的容差 | 4 | 无 | 无 | 无 | 无 | 老系统有零容差（归集守恒）、行数 × 0.005 + 0.005（个人补差）、1.00（重分类）、前端 0.005 四种 | **待决**（FQ-28，Phase 4 前答即可） |

### 2.2 时间

| 编号 | 关注点 | 首次阶段 | 用户 / 财务决定 | ADR / 契约 | 实现现状 | 守护 | 缺口 | 类别 |
|---|---|---|---|---|---|---|---|---|
| CC-T01 | 时钟来源、SQL 中的 `now()`、Jackson / JVM / JDBC 的时区 | 1a | PC §2「全部在 1a 做 (Recommended)」 | PC:31-36 | JVM：`K/UtcJvm.java` 启动前把默认时区设为 UTC；Jackson：`application.properties:33-35`；JDBC 会话：`:36` `connection-init-sql=SET TIME ZONE 'UTC'`；`Clock` bean 与业务时区在 `K/KernelConfiguration.java`；仓储 SQL 不再读数据库时钟，`A/sql-database-clock-exceptions.txt` 零例外 | `T/TimeSourceGuardTest`（扫全部生产类，含 infrastructure、web，覆盖 `ZonedDateTime`、`OffsetDateTime`、`LocalDateTime`、`LocalDate`、`Instant.now`、`Clock.system*`、`System.currentTimeMillis`，以及 `sqlStringsDoNotReadTheDatabaseClock`）；`T/kernel/UtcJvmTest`、`DatabaseSessionTimeZoneIT`、`JsonTimeFormatTest` | 无 | **已覆盖** |
| CC-T02 | 前端时刻显示的时区 | 1a | 同上 | PC:36 | `frontend/packages/core/src/format-date-time.ts:1-9` 固定 `timeZone: 'Asia/Shanghai'` | `format-date-time.test.ts`；ESLint G-X2（`Intl.DateTimeFormat`、`toLocale*String` 必带 `timeZone`，`frontend/eslint.config.js`，探针 `frontend/scripts/check-lint-config.mjs`，提交 `2a235f90`、`bcc5545a`） | 无 | **已覆盖** |
| CC-T03 | 业务日期与系统日期分开 | 1a | 契约 D-1a-155（编号日期取单据业务日期） | EAF:83；D-1a-114 | `K/BusinessZone.java:10-22`；`K/event/EventEnvelope.java:23-27` | `T/EventBusinessDateGuardTest.java:35-103` | — | **已覆盖** |
| CC-T04 | 跨日、跨月、跨年、闰日的边界测试 | 1a | PC §2 要求「跨日、跨月边界上按上海时区计算业务日」的测试 | PC:35；ADR-0010（跨年重置实测） | 业务日跨日、跨年已有测试（`T/kernel/AggregateRootTest:79-81`、前端 `business-day.test.ts`、`EventBusinessDateGuardTest`）；闰日仅在 `AccountingPeriodTest:40-44`（会计期间值对象）；Asia/Shanghai 无夏令时 | 同左 | 缺共用边界时刻夹具（G-X8）；跨月（月末 23:59:59+08:00）与闰日业务日未见测试；各测试各自手写时刻 | **地基**（可并入 B2b） |
| CC-T05 | 会计期间闸门 | 2 | ADR-0016:21「期间按 (月份 × 法人) 开关」 | brief:169（Phase 2 入口要求 `accounting.period` 可用） | `K/AccountingPeriod.java` 只是年月值对象，没有闸门、状态表与关账 | `T/kernel/AccountingPeriodTest` | 已在 Phase 2 入口条件中 | **阶段**（brief Phase 2 已有，不新增） |
| CC-T06 | 倒填、补录与填报截止 | 2 | 「1a流程-填报截止」转 Phase 2（契约 D-1a-127） | — | 老系统无补填期限，不拦未来日期（BR:845） | — | — | **阶段**：Phase 2 流程梳理入口「补填期限、未来日期、已关账期间的倒填三件事有决定」 |
| CC-T07 | 关账节奏与反结账口径 | 4 / G1 | FQ-20 未答；GL-123「关账后不反结账」与 U8C_CAPABILITY_MAP:69 保留 `period:reopen` 并存 | ADR-0016:21；ADR-0022:74 | — | — | 「不反结账」和「保留反结账能力、须审批」两种说法并存 | **待决**（FQ-29） |
| CC-T08 | 工作日历：法人专属与改过去日期 | 1a | 「一份默认日历，预留法人专属 (Recommended)」；DS:124 T2（改过去日期的影响）未决 | 契约 D-1a-114、115 | `WorkCalendarYear.java:88` 锁定早于今天的日期（待核实） | — | 已有决定：数据打分卡 T2「禁止修改，例外由组织数据管理员操作并审计 (Recommended)」（intent 2026-10-14-170302-…-T2，随 #72 已在 main）；代码已按此实现 | **已覆盖**（原为待决 XD-05） |
| CC-T09 | 定时作业互斥与月结窗口 | 1a（ShedLock）；4（月结） | 月结窗口值未定（FQ-20） | ADR-0002:22-23；ADR-0025:64 | `@SchedulerLock` 已用于全部 1a 作业 | `T/DataArchitectureTest.java:316-326`（D4） | 租约 `(legal_entity_id, period, job_type)` 未实现，Phase 4 才需要 | **阶段**：Phase 4 入口「月结窗口与作业租约」 |

### 2.3 一致性

| 编号 | 关注点 | 首次阶段 | 用户 / 财务决定 | ADR / 契约 | 实现现状 | 守护 | 缺口 | 类别 |
|---|---|---|---|---|---|---|---|---|
| CC-C01 | 请求幂等（`Idempotency-Key`） | 1a | PC:45（已决定，按建议） | 只在 PC，不在 ADR | 过滤器 `M/platform/web/IdempotencyFilter.java`，范围 `IdempotencyScope`（只对 `A/idempotent-endpoints.txt` 登记的 POST 生效，ADR-0043 §4）；存储 `JdbcIdempotentResponses`、迁移 `platform_idempotency_baseline`、清理 `PurgeExpiredResponses`；OpenAPI 声明 `OpenApiProblems.java:93-95`；前端 `packages/api/src/http.ts:179` 发送 | `T/IdempotentEndpointGuardTest`（登记簿与代码一致、应答不含直接标识）、`IdempotentResponseSizeTest`、`platform/web/IdempotencyScopeTest`、`HttpContractIT:160`、`http.test.ts:215` | 按设计只覆盖白名单内 POST（目前约 20 条） | **已覆盖**（范围按 `idempotent-endpoints.txt` 白名单，ADR-0043 §4） |
| CC-C02 | 更新的并发版本（`If-Match` / `ETag`） | 1a | PC:46（「未单独作答，按建议」） | 只在 PC | 共用类型 `K/ExpectedVersion.java`（解析，428、400、412 三态，`etag()`）及三个异常；法人、法人归属、工作日历、角色、账号状态、编号规则、审批任务等控制器共用（约 20 个文件）；OpenAPI 必填 `OpenApiProblems.java:71-76`；前端 `http.ts` `versioned` | 各模块分散（如 `T/platform/approval/TaskVersionHttpIT`） | 共用类型已建，缺全局 OpenAPI 契约守护 G-X3（每个更新类接口必须声明 `If-Match`）与统一 ETag 契约测试；是否漏接靠各控制器自觉 | **地基** |
| CC-C03 | 事件消费幂等 | 1a | 契约 D-1a-82、98、106（至少一次投递，消费方幂等） | EAF:62 | 只有 `cost_timesheet.consumed_events` 一张表；审计按事件 id 去重 | `ConsumedEventsPurgeJobIT` | 每个消费模块各建一张表，或者不建 | **地基**：共用的消费幂等构件，加守护「每个 `@ApplicationModuleListener` 都经过它或登记例外」 |
| CC-C04 | 聚合乐观锁与事件序号 | 1a | 「法人维度与乐观锁直接实施」 | GP:131；DATA_DESIGN_CHECKLIST §14 第 4 项 | `K/AggregateRoot.java:13-60`（`version` 与 `eventSequence` 两个计数器） | `T/kernel/EventSequenceContinuityTest`；`T/DataArchitectureTest.java:269-275`（D1） | — | **已覆盖** |
| CC-C05 | 主键 UUIDv7 | 1a | 「UUIDv7 (Recommended)」 | GP:113；PC:9-29 | `K/Uuids.java`、`TimeOrderedIds.java`、`IdGenerator` 默认 UUIDv7（提交 `a59805d7`、`aac92d51`）；仅 5 张框架表豁免（`A/primary-key-exemptions.txt`） | `T/PrimaryKeyTypeGuardTest`（ADR-0033：每表单列 uuid 主键，不得序列、标识列或数据库默认值）；`T/IdMakingGuardTest`（`UUID.randomUUID` 仅 1 例）；`UuidsTest`、`TimeOrderedIdsTest` | 无；审计表分区主键例外见 ADR-0048（已决定，迁移未落） | **已覆盖** |
| CC-C06 | 生效区间与追溯修改 | 1a | 配置类不追溯（SP 附 M3「规则调整三类」）；员工与部门的法人归属带生效区间（ADR-0026:70-77） | 契约 D-1a-27、147 | 维度版本表、编号规则版本表（#72 `platform_numbering.rule_versions`） | `T/DataArchitectureTest`（D2、D10） | 法人表本身缺生效区间（DS:106 K4），待核实 #72 是否已修 | **已覆盖** |
| CC-C07 | 已过账事实的冲销与更正 | 2 | SP 附 M3「已过账事实」；ADR-0016:23 | brief:169（Phase 2 入口） | 过账管道不存在 | G34 | 同 CC-N05 的镜像要求 | 决定层已覆盖（ADR-0031，提议） |
| CC-C08 | 删除策略 | 1a | 业务事实与审计只追加，到期归档（GP:112） | 契约 D-1a-154、176 | 无软删除列；17 处硬删除都在清理作业与子表替换 | `T/DataArchitectureTest.java:280`（只追加触发器）、`:674`（撤销 DELETE） | 业务单据作废的通用做法 1b 起才需要 | **已覆盖** |
| CC-C09 | 业务编号 | 1a | ADR-0010、ADR-0027 与契约 D-1a-144..161 | 同左 | `M/platform/numbering/` | `NumberingRulesIT` 等 5 个测试；G45 | — | **已覆盖** |
| CC-C10 | 新旧 ID 映射与来源标记 | 1a | GP:113（`legacy_id` 唯一）；契约 D-1a-152、161 | ADR-0025:130、147 | `platform_numbering.legacy_numbers`；`K/event/SourceSystem.java`、`EventSource.java:7-16` | `T/DataArchitectureTest.java:407-416`（D8） | 行级没有来源列，来源只在登记表与事件信封（按设计如此） | **已覆盖** |
| CC-C11 | 外部系统失败与补偿 | 1a | 契约 D-1a-91、132 | GP:130；ADR-0022:85,102,109 | 事件发布登记表加重试 8 次（`EventPublicationJobs.java:60-76`）；企业微信 `RetrySchedule` | `WecomDeliveryIT`、`EventPublicationOperationsIT` | U8C、分贝通、税务数电在各自阶段 | **已覆盖**（1a 范围） |
| CC-C12 | 批处理互斥租约 | 4 | — | GP:131；ADR-0002:22 | — | — | 并入 CC-T09 | **阶段** |

### 2.4 合规与安全

| 编号 | 关注点 | 首次阶段 | 用户 / 财务决定 | ADR / 契约 | 实现现状 | 守护 | 缺口 | 类别 |
|---|---|---|---|---|---|---|---|---|
| CC-P01 | 审计 | 1a | 契约 D-1a-162..178 | 同左 | `M/platform/audit/`；`K/event/NotAudited.java` | `AuditLogIT`、`EventCataloguePolicyTest` | — | **已覆盖** |
| CC-P02 | 保留与归档期限 | 1a | 两份原话并存：契约 D-1a-173「至少 5 年，1a 不删，最终保留期在老系统下线前定」；GP:120「在线 3 年、归档满 10 年、权责相关 30 年」（「都认可 (Recommended)」） | GP §6 | 作业按在线 3 年、WORM 约 7 年实现（`config/platform_integration.properties`） | `T/DataArchitectureTest.java:316-326`（D4，只验证作业名存在） | 已有决定（用户 2026-10-15 前）：「一个 10 年的 bucket」「不算，按 10 年」「锁约 7 年（2557 天）」，即在线 3 年 + 归档 7 年满 10 年，审批结论按 10 年；契约 D-1a-173 已更正；过账类审计 30 年由首个过账阶段定；审计分区：ADR-0048 已决定，迁移未落 | **已覆盖**（原为待决 XD-04；30 年部分转 Phase 2 入口） |
| CC-P03 | 出参不暴露敏感列 | 1a | ADR-0026:122-129 | `data-classification.txt` 文件头 | `T/SensitiveColumnExposureGuardTest`：响应字段的 snake_case 名与 `A/data-classification.txt` 中 SENSITIVE、RESTRICTED 列同名即失败；例外 `A/sensitive-response-exceptions.txt` 3 条，附理由 | `T/SensitiveColumnExposureGuardTest`、`OutwardTextGuardTest`、`NullableResponseGuardTest` | 按同名匹配，改名或换结构可绕过；属约定级守护，非数据流分析 | **已覆盖**（按同名匹配，3 条例外） |
| CC-P04 | 共用掩码组件（接口与 Excel 共用） | 1b（费率是 L3） | ADR-0026:127-128（无权限时数值给 `null` 加 `<字段>Masked: true`） | ADR-0026 | 无（`mask|desensit|redact` 检索零命中） | 无 | 1b 报价第一次返回费率与成本时就要用 | **地基** |
| CC-P05 | 数据范围（`AccessScope`）在读路径生效 | 1a | ADR-0026:29-48 | 同左 | 端口 `K/OrgUnitAccess.java`（取不到范围就抛出）；实现 `OrgUnitScopes`、`AccessScopeCalculator`；已接入组织单元列表与详情、法人归属（提交 `3f2f4f94`） | `T/` 仅 `ManagementEndpointIT`、`ErrorLogDetailIT` 引用端口 | 只接入组织单元列表与详情、法人归属；员工、岗位、同步运行、审计条目、法人列表等读路径未接；无「读路径必须经范围」的通用守护；RULE-387 只在文档 | **地基 · 本批处理中**（B1-scope 已合入一部分） |
| CC-P06 | 代理审批（RULE-270） | 1a | 契约 D-1a-77「仅流程管理员、逐次、填原因、通知被代理人」 | — | 审批模块 | — | `phase1a/DEFERRED.md:17` 仍写「1b 前单独评审」，与 D-1a-77 不一致，属文档漂移 | **已覆盖**（另需修文档） |
| CC-P07 | 职责分离 | 1a；G1（凭证制单、审核、记账） | ADR-0026:91-100；ADR-0027:43-49 | 同左 | `K/SeparationReview.java`；四眼确认 | `SensitiveGrantTwoPersonIntegrationTest`、`NumberingRuleFourEyesIT` | 凭证三岗分离在 G1 定（现状：审核人 = 记账人 998 张） | **已覆盖**（1a）；G1 部分见 CC-X11 |
| CC-P08 | 通用导出控制：权限、审计、行数上限、掩码、防公式注入 | 1b（第一个业务导出） | 审计导出已定（契约 D-1a-172） | ADR-0025:63,69（长导出写对象存储、配额） | `K/ListLimits.java` 的 `EXPORT_MAX = 10_000`、`IMPORT_MAX`；仅审计导出一处实现（`ExportAuditEntries`、`AuditCsv`、`AuditExportController`） | `T/ListPagingGuardTest:147`（A-3，导出、导入行数上限不得超约定）；`AuditExportAndOwnRecordsIT` | `EXPORT_MAX` 常量与配置上限守护已建，通用构件未建：权限、审计、行数上限、公式注入、掩码未一体；公式注入处理仍私有于审计模块；无 G-X5；掩码依赖 P04。老 BI 166 人几乎人人导出（BI_SCOPE:81） | **地基** |
| CC-P09 | 维度 ID 不得写死（G-T2） | 1a | DATA_DESIGN_CHECKLIST §14 G-T2 | — | `T/CurrentUserGuardTest` 对 `CurrentUser`、`EmployeeLegalEntities` 的实现禁止字面量、常量、`orElse` 默认的法人 ID，禁止现场 `new CurrentUser(`；类型化 ID 已有 `EmployeeId`、`UserId`、`ProjectId`、`WbsNodeId` 等 | `T/CurrentUserGuardTest`、`TypedIdComparisonGuardTest`、`IdMakingGuardTest`、`IdPrintingGuardTest` | `CurrentUserGuardTest` 仍只认 `LegalEntityId`，G-X7 要求的扩到全部维度 ID（`OrgUnitId`、`UserId` 等）与配置默认值未做 | **地基**（已排） |
| CC-P10 | 敏感数据可见角色与授予方式 | 1b（报价成本与毛利、费率与日薪）；6e（薪酬、银行卡） | 用户 2026-10-15：「采纳，敏感权限授给人」；ADR-0026:122-129（敏感分级）、敏感权限两人确认与季度复核 | — | 敏感授予两人确认已实现：`M/platform/identity/domain/SensitivePermissions.java`（6 个敏感权限码）、`AuthorizationGate`、`PrivilegeCeiling`、`ReplaceAccountRoles`、`UpdateRole` | `T/platform/identity/SensitiveGrantTwoPersonIntegrationTest`、`OrgDataAdminRoleIntegrationTest` | 禁止经岗位 / 同步自动授予的守护未建；调岗、离岗生成「待调整权限」待办未建（代码中无相应概念）；各阶段可见角色按 §13 第 7 步逐阶段问用户；非敏感权限按岗位授予属新增能力，待 ADR-0029（F-9） | **地基 · 下一批 F-1** |

### 2.5 多主体

| 编号 | 关注点 | 首次阶段 | 用户 / 财务决定 | ADR / 契约 | 实现现状 | 守护 | 缺口 | 类别 |
|---|---|---|---|---|---|---|---|---|
| CC-E01 | 业务行带法人 | 1a | DATA_DESIGN_CHECKLIST §14 第 5 项 | EAF:18；DS D7 | `K/LegalEntityId.java`；`timesheets.legal_entity_id NOT NULL` | `T/DataArchitectureTest.java:387-403`（D7） | — | **已覆盖** |
| CC-E02 | 会话按哪个法人生效、数据范围授予表由谁维护 | 1b | 契约 D-1a-58「在 1b 流程梳理时定」 | — | — | — | — | **阶段**（1b 入口已排） |
| CC-E03 | 内部交易与抵销 | G2 之后 | D11、D12 不在 brief 内（ADR-0017:17） | — | — | — | 52 个收付公司档案里有一个内部往来抵销专用账套（U8C_GL_PRACTICE:17）；抵销规则无证据 | **阶段**（G2 入口「跨法人结算与抵销是否纳入」） |
| CC-E04 | 组织调整对历史的影响（组织维度的历史） | 1a | DS:123 T1「组织维度无历史，1a 上线到 R1 之间的变化会丢」未决 | ADR-0025:110-111（按事实发生时的组织） | 基线迁移 `organization_workforce_baseline.sql`：`org_unit_versions`（排他约束 `ex_org_unit_versions_overlap`）、`assignment_versions`、`legal_entity_versions`、`legal_entity_attributions`，均有区间不重叠约束；实现 `M/organization/workforce/infrastructure/persistence/OrgHistory.java` | `T/organization/workforce/application/OrgHistoryAppendOnlyIntegrationTest`；跨 schema 版本引用守护 `A/cross-schema-version-references.txt` + `T/CrossSchemaVersionReferences`（`c6db7c12`） | 无；组织、岗位版本相关的新增行为随 R1 迁入再核 | **已覆盖** |

### 2.6 交互与集成

| 编号 | 关注点 | 首次阶段 | 用户 / 财务决定 | ADR / 契约 | 实现现状 | 守护 | 缺口 | 类别 |
|---|---|---|---|---|---|---|---|---|
| CC-I01 | 中文排序与检索 | 1a（人员、部门列表） | 无 | ADR-0025:133（只把排序规则列入镜像映射规格） | 前端已建：ESLint G-X2 要求 `localeCompare`、`Intl.Collator` 必带 `'zh'`（`2a235f90`、`bcc5545a`），`work-calendar.ts` 已修。后端未建：CS-3.13「`COLLATE "zh-x-icu"`」只是规范；库仍按内建 C.UTF-8 建库（`T/TestPostgres.java:22-28`），迁移与代码无 `COLLATE`，无拼音排序 | 前端 G-X2；后端无（`T/SqlOrderByGuardTest` 不管排序规则） | 后端人员、部门列表仍按码位排序；`COLLATE "zh-x-icu"` 约定与后端守护未建。XD-03 已答「按拼音 (Recommended)」（2026-10-15） | **地基**：1b 前平台约定（ICU `zh` 排序规则）加守护 G-X2，交 1a 下一批（原为待决 XD-03） |
| CC-I02 | Excel 导入导出 | 2（预算导入 RULE-210） | 「各模块各自实现，共用一个小工具」（ADR-0019:32） | 同左 | 无 | 无 | 老系统成本修正的 Excel 导入可以批量绕过校验（RULE-102） | **阶段**：第一个用到的阶段，入口要求「导入走领域校验，不绕过；共用工具已建」 |
| CC-I03 | 附件存储 | 1b / 6a | 阿里云 OSS（ADR-0019:28） | 契约 D-1a-119（审批参与人只读） | 无 | 无 | 保留期未定 | **阶段**：首个上传附件的阶段，入口要求「保留期、病毒扫描、权限随单据」 |
| CC-I04 | 通知 | 1a | 「短信不迁移，审批通知走企业微信」 | 契约 D-1a-83..92、128..143 | `M/platform/integration/` 企业微信适配器 | `WecomDeliveryIT` | — | **已覆盖** |
| CC-I05 | 错误文案 | 1a | — | DATA_DESIGN_CHECKLIST §14 第 10 项；NS:232 G48 | 后端 `detail` 为英文；中文文案在 `frontend/packages/core/src/error-messages.ts`，一码一条 | `ErrorChainTest`；前端类型检查；`check-copy-glossary.mjs` | 前端有后端不存在的死码（`COST_TIMESHEET_EXCEEDS_REMAINING_HOURS`） | **已覆盖** |
| CC-I06 | 统一分页与上限 | 1a | 「游标分页 (Recommended)」，`limit` 默认 50、上限 200（PC:42） | 只在 PC | `K/ListLimits.java`（默认 50、上限 200）、`CursorPage`、`PageRequest`、`InvalidCursorException`；前端 `packages/api/src/paging.ts` | `T/ListPagingGuardTest`（G-A2 `{items,nextCursor}` 并声明 `cursor`、`limit`；G-P1 `limit` 最小 1、最大 200、默认 50；A-3 不得内存切页）；`HttpContractIT:119`、`ListPagingContractIT`、`ListPagingKernelTest` | 守护 G-A2、G-P1、A-3 已建；`A/list-paging-gaps.txt` 登记缺口 8 行待清（组织单元列表、本人登录记录、审批任务非游标例外、`AuditQuery.MAX_LIMIT`、授权申请、旧号、编号规则切片、审计导出 `max-rows` 配置） | **地基** |
| CC-I07 | 外部接口限流与频控 | 1a（企业微信） | — | ADR-0002:26（nginx 限流） | 企业微信出站无频控；`duplicate-task-error-codes` 为空 | — | — | **阶段**：3 与 6b 入口「对方频控限制已查明并配置」 |
| CC-I08 | 报表打印与版式文件 | 1b（报价单含印章，DS_GAPS:11）；G1（凭证、账簿 PDF/OFD） | — | GL_COMPLIANCE_CHECKLIST:73 | 无 | — | — | **阶段** |

### 2.7 分析

| 编号 | 关注点 | 首次阶段 | 用户 / 财务决定 | ADR / 契约 | 实现现状 | 守护 | 缺口 | 类别 |
|---|---|---|---|---|---|---|---|---|
| CC-A01 | OLTP 与 OLAP 一致性 | 1b（反向同步后 BI 结果不变） | ADR-0025 采纳 | ADR-0025:78-98 | — | — | — | **阶段**（R1） |
| CC-A02 | 口径版本 | R0 / R1 | ADR-0023:31 | EAF:70（`policyVersion` 不进事件） | — | — | — | **阶段** |
| CC-A03 | 关账水位 | 4 / R1 | ADR-0025:99-101 | — | — | — | — | **阶段** |
| CC-A04 | 报表金额单位（元、万元）与显示舍入 | R0 | — | — | — | — | 老报表元与万元混用（REPORT_INVENTORY:138 K20）；万元化后的行不进指标（METRIC_BASELINE_DRAFT:144） | **阶段**：R0 入口 |

### 2.8 清单外的横切关注点（子代理在 OA 盘点与 BI 范围中补出）

| 编号 | 关注点 | 首次阶段 | 依据 | 类别 |
|---|---|---|---|---|
| CC-X01 | 在途实例与僵尸实例的处置 | 每个迁入阶段 | OA_PROCESS_INVENTORY:403；brief:178 | **阶段**（每阶段入口「逐模板判定排空、关闭或补结论」） |
| CC-X02 | 历史数据按 (期间, 法人) 迁移对账 | 1b 起 | OA_PROCESS_INVENTORY:350；ADR-0024:19 | **阶段** |
| CC-X03 | 逐列所有权与反向同步契约测试 | 1b 起 | brief:76-83、153 | **阶段**（brief 已有） |
| CC-X04 | 预算占用、释放与超预算控制 | R4 / 6c | BI_SCOPE:133、414 | **阶段** |
| CC-X05 | 电子签章与电子原始凭证验签 | 6a / G2 | OA_PROCESS_INVENTORY:233、342；GL_COMPLIANCE_CHECKLIST:48、88 | **阶段** |
| CC-X06 | 批量操作（批量审批只限本人、批量豁免） | 1a / 4 | brief:370（RULE-270）；RULE-133 | **阶段** |
| CC-X07 | 富文本 XSS 净化 | 首个富文本输入 | DS_GAPS:19 | **阶段** |
| CC-X08 | 无遗留检查（切换日后 A8 无新记录） | 每个 6x 阶段 | OA_PROCESS_INVENTORY:329；`oa_residual_check.sql` | **阶段**（brief 已有） |
| CC-X09 | 隐性同步通道（生产 SQL Agent 作业 #28–#31） | 2 / 4 | A8_ACCESS_REGISTER；brief:172、216 | **阶段**（brief 已有 Proposed revision） |
| CC-X10 | 凭证连续编号、不可插入 | G1 | ADR-0022；GL_COMPLIANCE_CHECKLIST:61 | **阶段** |
| CC-X11 | 控制科目按来源通道授权；凭证三岗分离 | G1 | ADR-0022:38；U8C_CAPABILITY_MAP:48-63 | **阶段** |

另外两条没有列入：多语言没有任何需求证据；术语与方言治理已有 ADR-0018 与守护 G37–G39，属于已覆盖的常设机制。

### 2.9 业财架构核查清单（用户 2026-10-15 提交，经 one-coordinator 转达）

前提：收入确认用产出法，以客户签认为准（「产出法 · 客户价值确认」，`docs/business/finance-adr-points.md:20`；ACC-R01），不用完工百分比。下列各行中凡涉及收入的，都以此为前提。用户原话的 intent 片段由 1a 会话记录，随 #72 已在 main：`docs/decisions/2026-10-14-222541-psa-1a-platform-bridge-reimagine-利润中心就是某一级部门.md`、`…-按职级标准费率看到个人行.md`、`…-适用-纳入-Phase-2.md`。

| 编号 | 关注点 | 首次阶段 | 用户 / 财务决定 | 实现现状与缺口 | 类别 |
|---|---|---|---|---|---|
| CC-B01 | 税率上下文 TaxContext：税率按版本保存，带生效日 | 1b（RULE-144 按表头税率拆税） | 用户 2026-10-15 核查清单；税率是配置类参数，须版本化（METRIC_BASELINE_DRAFT:17） | 无实现。没有它，各单据会各自保存一份税率，税率调整时无法按业务日期取值 | **地基**（下一批 F-7） |
| CC-B02 | 通用时态生效基座：生效区间、按业务日期取值、版本只追加、区间不重叠 | 1a | 用户 2026-10-15 核查清单；DATA_DESIGN_CHECKLIST §14.1 D6、D15 | 现在是逐表各自实现（维度版本表、`rule_versions`），没有共用基座。费率卡、税率、利润中心标识、科目映射都要用到 | **地基**（下一批 F-8） |
| CC-B03 | 业务单据锁定、红冲、修正的通用模式 | 2（首个过账事实） | 用户 2026-10-15 核查清单；SP 附 M3「已过账事实」；ADR-0016:23；ADR-0022:32 | 无共用模式；与 CC-N05、CC-C07 是同一件事在单据层的体现 | 决定层已覆盖（ADR-0031，提议） |
| CC-B04 | 利润中心 | 2（工时成本归属） | 用户 2026-10-15 原话：「利润中心就是某一级部门」 | 无实现。组织单元上加利润中心标识，带生效区间（依赖 CC-B02 与 XD-06 的组织版本表） | **地基**（下一批） |
| CC-B05 | 内部借调工时结算（跨利润中心） | 2 | 用户 2026-10-15 核查清单 | 无 | **阶段**：Phase 2 流程梳理入口「借调工时的成本归属与跨利润中心结算规则已定」 |
| CC-B06 | 项目经理可以看到的人工成本粒度 | 1b / 2 | 用户 2026-10-15 原话：「按职级标准费率看到个人行」 | 掩码组件（CC-P04）尚未实现；须区分职级标准费率（可见）与真实薪资（L3 掩码） | **阶段**：1b 与 Phase 2 流程梳理入口；依赖 CC-P04 与 F-8 |
| CC-B07 | 专业产值切分 | 2 | 用户 2026-10-15 原话：「适用，纳入 Phase 2」 | 无。核算维度须预留「专业」 | **阶段**：Phase 2 流程梳理入口「专业维度及其产值切分规则」；切分走 `prorate`（§3.3） |
| CC-B08 | 分包进度确权与暂估成本 | 6d | 用户 2026-10-15 核查清单 | 无 | **阶段**：6d 流程梳理入口 |
| CC-B09 | 关账期间快照事实表 | 4 / R1 | ADR-0025 采纳（L2 期间快照） | ADR-0025:99-106（L2 在可重复读事务中生成，例外重开时作废重建并留版本） | **已覆盖**（决定层；实现在 R1） |
| CC-B10 | 利用率、乘数、变现率统一口径 | R0 | ADR-0023:31（指标声明口径版本）；R0 指标基线 | `docs/data/metric-baseline/baseline-draft.md`；「人工成本产出倍数」与 NRPM 的对齐排在第三轮用语确认 | **已覆盖**（另需第三轮用语） |
| CC-B11 | 业财穿刺验收 | 1a 出口；2 出口 | 用户 2026-10-15 核查清单 | brief 模板已以 `Proposed revision:` 提出：1a 出口「平台穿刺」，Phase 2 出口「1 小时工时业财穿刺」（工时 → 审批 → 人工成本 → 过账 → 项目成本 / 利润中心 / L2 快照逐分一致，冲销后归零） | **阶段** |

## 3. 金额尾差专节

### 3.1 结论

one-coordinator 的初步判断全部成立，另有三处需要细化：

| 初步判断 | 核实结果 | 细化 |
|---|---|---|
| `Money` 每步立即 `HALF_UP` 舍入 | 成立（`K/Money.java:16`） | ADR-0006 第 1 条「统一 2 位小数」不要求每步舍入：`Money` 作为「已形成的金额」保持 2 位，另设不舍入的计算类型，只在形成金额时舍入一次，因此**不需要新 ADR** |
| 没有分摊原语 | 成立 | 不能叫 `allocate`：术语表中「分摊」是业务动作（`allocate_indirect_cost`，terms.yaml:2813）；kernel 原语叫「按权重拆分」，英文 `prorate`，已按 XD-07 登记为提议术语 `prorate`（`backend/glossary/proposed/kernel.yaml`） |
| 舍入口径写死 | 成立；1b 已有用户决定「四舍五入到分」（RULE-141、142） | 写死 `HALF_UP` 与已有决定一致；缺的是会计口径的财务确认（FQ-23），以及口径集中在一处 |
| 冲销镜像未入内核 | 成立 | 冲销按 ADR-0022:32 取原分录逐行取负，这本身不需要拆分；需要内核保证的是「拆分 −X 等于拆分 X 后逐项取负」，以及「部分冲销按原分录比例拆分，累计不超过原额」 |
| — | 新发现：「尾差」一词有两个意思 | 1b 的 RULE-142「尾差」是报价的整单人工尾调；舍入产生的分厘差是另一个概念。须登记两个术语，建议「尾调」（报价整单调整）与「舍入差」（rounding residual），不得都叫「尾差」（已按 XD-07 登记：`tail_adjustment` 尾调、`rounding_residual` 舍入差，「尾差」为方言并禁用） |
| — | 新发现：决定的时点放晚了 | brief 把「分摊余数归属」放在 Phase 4 入口（brief:208），但 1b 的条款拆分（RULE-159）与 Phase 2 的人天拆分（RULE-004）已经需要它 |

### 3.2 kernel 下一批应提供的金额原语（设计要点，不是代码）

- **`Money`**（已有）：已形成的金额，2 位小数，同币种运算。新增：
  - 拒绝小数位不是 2 的币种（失败并告警，不静默）。
  - `prorate(List<Weight>)`，按权重拆分，见 §3.3。
  - 不提供 `divide(BigDecimal)`：除法只经 `prorate` 或下面的计算类型。
- **不舍入的计算类型** `kernel.ExactMoney`（提议术语 exact_money）：保留全部精度做乘法和累加（除法待定入口），`round()` 时只按统一口径舍入一次，得到 `Money`。工时成本 = 工时 × 时薪、不含税额 = 含税 ÷ (1 + 税率) 都走这条路。
- **`Rate`**（单价、费率，带分母单位，例如「元 / 小时」）与 **`Ratio`**（0–1 的比率，表示方式见 XD-02）：小数位按 FQ-25 的答复，不在构造时舍入到 2 位。
- **舍入口径集中一处**：`HALF_UP` 与 2 位只出现在 kernel 的一个常量里；财务若答 FQ-23 改口径，只改这一处，并按配置类规则从某个会计期间首日起生效、不追溯（SP 附 M3「规则调整三类」）。

### 3.3 按权重拆分（`prorate`）的规格

| 性质 | 要求 | 测试 |
|---|---|---|
| 守恒 | Σ 各份 = 总额，一分不差 | 随机权重与总额的属性测试 |
| 确定性 | 同样的输入（含顺序）得到同样的结果；余数按「余数大者优先，余数相同按稳定键（输入顺序或调用方给的键）」分配 | 重复运行与权重重排后按键比较 |
| 偏差有界 | 每份与精确值之差小于 0.01 | 属性测试 |
| 镜像 | `prorate(−X)` 等于 `prorate(X)` 逐项取负 | 属性测试 |
| 零权重 | 权重为 0 的份额为 0 | 单测 |
| 非法输入 | 权重全为 0、权重为负、空列表：失败并告警，不默认均分（老系统 RULE-084「全缺失时均分」不复刻，除非规则卡另有决定） | 单测 |
| 余数分配方法 | 默认最大余数法（Hamilton）；FQ-24 若选别的方法，作为具名策略加入，调用方必须显式指定 | 每个策略一组测试 |

不选「全部记入最后一行」作为默认，原因有两个：余数会随行的顺序变化，同一笔分摊重跑可能得出不同结果；行数多时，最后一行的偏差可达 (n − 1) 分。

### 3.4 全系统已知的分摊与拆分场景

| # | 场景 | 首次阶段 | 老系统做法（证据） | 新系统应走 |
|---|---|---|---|---|
| 1 | 报价条款（预付款、进度款）拆最终报价 | 1b | 人工输入，前端按百分比换算可能余 0.01，被后端拒绝（RULE-159、150） | `prorate`，按条款百分比拆，守恒 |
| 2 | 报价工作包值、人工成本 = 工时 × 费率 | 1b | 3 位 `HALF_UP`（RULE-003、007） | 计算类型，`round()` 一次（RULE-141「到分」） |
| 3 | 报价价税分离 | 1b | 不含税 = 含税 ÷ (1+税率) 取 2 位，税额用减法（RULE-149） | 计算类型加 FQ-27 的口径 |
| 4 | 个人日工时拆人天 | 2 | ÷ max(当日总工时, 7.5)，4 位，无守恒（RULE-004） | `Ratio` 加 `prorate`（人天守恒） |
| 5 | 工时成本 = 工时 × 费率（锁定费率） | 2 | 3 位（RULE-007） | 同 2 |
| 6 | 预算拆到任务 | 2 | 任务级不取整，进度页逐行取整，合计不一致（RULE-181） | `prorate`，加 FQ-26 |
| 7 | 收入按条款与进度 | 2 / 4 / 6a | 条款金额 × 进度，「已全额确认」用精确相等判断（RULE-193） | 计算类型；最后一次确认 = 条款余额（不再用乘积） |
| 8 | 差旅补贴分摊到行程 | 3 | 2 位 `DOWN`，余数记入最后一个（TravelExpenseAllocation…:271） | `prorate` |
| 9 | 差旅费用人工分摊 | 3 | 人工录入，校验 Σ = 总额（PlatformExpenseGroup:151） | 守恒校验共用 kernel |
| 10 | 工资、社保、公积金三要素凑整 | 4 | 差额记入绝对值最大项（RULE-075） | `prorate`（权重 = 三要素原值） |
| 11 | 个人月度补差 | 4 | 记入最大行，容差 行数 × 0.005 + 0.005（RULE-087） | FQ-28 |
| 12 | OH 间接成本分摊（四档） | 4 | 比率 10 位，余数全部记入最大行，加尾差标志位（RULE-069） | `prorate`，每个池守恒 |
| 13 | OH 红冲镜像 | 4 | 按原占比重拆，余数记入最大行（RULE-070） | 原分录逐行取负（ADR-0022:32），不重拆 |
| 14 | 年终奖按条款拆分 | 4 / 6e | 最大余数法，按分 `FLOOR`（RULE-073） | `prorate` |
| 15 | 人工重分类到薪资档案部门 | 4 | 余数记入最后一行，容差 1.00（RULE-076） | `prorate` |
| 16 | OA 计提行拆回 U8C 行 | 6c | 余数记入最后一行（RULE-086） | `prorate` |
| 17 | 商机费用拆到条款（抽屉、导出两种口径） | 4 / R3 | 抽屉按合同额占比，导出均分，结果不同（RULE-084） | 一个口径，`prorate` |
| 18 | 部分冲销（部分退回） | 4 / 6a | 无证据 | 原分录按比例 `prorate`，累计 ≤ 原额（FQ-28） |
| 19 | 管理口径 BU 层级分摊（总部、支持、销售、居间） | R3 / G1 | 表格内四档硬编码费率（FQ-05） | `prorate`；费率走配置版本 |
| 20 | BI 辅助归属比例拆分 | R2 | 主归属唯一，辅助归属比例守恒（BI_SCOPE:130） | 语义层同一规则 |
| 21 | 内部交易分摊 | G2 之后 | 无证据 | 届时定 |

### 3.5 守护

| 编号 | 守护 | 形式 |
|---|---|---|
| G-M1 | kernel 以外不得调用 `BigDecimal.setScale`、`divide`、`round`、`stripTrailingZeros` 后比较，不得使用 `RoundingMode`、`MathContext`、`doubleValue`、`floatValue` | ArchUnit，加入 `ArchitectureRulesTest`；例外登记 |
| G-M2 | `prorate` 的守恒、确定性、偏差有界、镜像 | kernel 属性测试 |
| G-M3 | `_amount` 列必须是 `numeric(18,2)`；`_rate`、`_ratio` 的小数位按 FQ-25；`_currency` 加 `CHECK (currency ~ '^[A-Z]{3}$')`；`ALTER TABLE ADD COLUMN` 也检查币种成对 | 扩展 `DdlNamingTest` |
| G-M4 | `Money` 拒绝小数位不是 2 的币种 | kernel 单测 |
| G-M5 | OpenAPI 中金额一律为 `{amount: string（十进制）, currency: string}`，名字像金额的字段不得是 `number` | 扩展 OpenAPI 契约测试 |
| G-M6 | 前端金额只用共用的 `Money` 类型与格式化组件；禁止对金额调用 `Number()`、`parseFloat`、`toFixed` 或做 `+ - * /` 运算 | ESLint 规则 |
| G-M7 | 冲销事实只能由「原分录逐行取负」生成；原行与冲销行之和逐行为 0 | Phase 2 过账管道的集成测试 |
| G-M8 | `api`、`events` 包中，金额参数与返回值（不只字段）不得是裸 `BigDecimal` | 扩展 `moneyIsNeverABareDecimal` |

## 4. 固化建议

### 4.1 写进 METHODS M4 的步骤（XD-10 已采纳，2026-10-15 已写入附 M4「横切关注点」；brief 各阶段入口已加 Proposed revision 行）

> **横切关注点矩阵**（每个阶段开工前更新）：流程梳理开始前，负责会话先更新 `CROSS_CUTTING_MATRIX.md`：
> 1. 对矩阵中「首次阶段」为本阶段、或「加重阶段」包含本阶段的每一行，重新核对证据（ADR、契约、实现、守护），更新类别；
> 2. 按本阶段的规则卡、OA 盘点与 BI 范围，补充矩阵里没有的横切关注点，一行一条；
> 3. 「待决」行的题目汇入 `CROSS_CUTTING_DECISIONS.md`，由调度会话一次问清；本阶段需要的题没有答复，阶段入口条件不勾选；
> 4. 「地基」行必须在本阶段派功能代理之前做进 kernel 或 platform，并冻结（§6「公共基础先行」）；
> 5. 「阶段」行写进本阶段 `process/<阶段>/` 的流程梳理入口；
> 6. 矩阵中没有证据的「已覆盖」视为缺口。
>
> brief 各阶段入口条件增加一条：「横切关注点矩阵已按本阶段更新，本阶段需要的待决题已有答复，地基行已冻结」。

### 4.2 可以做成守护的条目

除 §3.5 的 G-M1..G-M8 之外：

| 编号 | 守护 | 对应行 |
|---|---|---|
| G-X1 | 业务时间只来自注入的 `Clock`：扩展到 infrastructure、web 包，以及 `ZonedDateTime`、`OffsetDateTime`；仓储 SQL 中不得出现 `now()` | CC-T01（本批 B2b） |
| G-X2 | 前端：`Intl.DateTimeFormat` 必须带 `timeZone`；`localeCompare` 必须带 `'zh'` 区域参数 | CC-T02、CC-I01 |
| G-X3 | OpenAPI 契约：列表接口有 `limit` 且上限 ≤ 200；有副作用的 POST 声明 `Idempotency-Key`；更新接口要求 `If-Match` | CC-C01、C02、I06 |
| G-X4 | 每个 `@ApplicationModuleListener` 都经过共用的消费幂等构件，或登记例外 | CC-C03 |
| G-X5 | 每个导出端点：导出权限、审计、行数上限、防公式注入、掩码 | CC-P08、P04 |
| G-X6 | 出参不得含 `data-classification.txt` 中 SENSITIVE、RESTRICTED 的列 | CC-P03（本批 B2b） |
| G-X7 | G-T2 扩展到全部维度 ID（`OrgUnitId`、`UserId` 等）与配置默认值 | CC-P09 |
| G-X8 | 共用的边界时刻测试夹具：跨日、跨月、跨年、2 月 29 日 | CC-T04 |
| G-X9 | 矩阵自检：`build_all.py --check` 校验矩阵每行都有类别与首次阶段，待决行在决定单中有题号 | 本矩阵 |
