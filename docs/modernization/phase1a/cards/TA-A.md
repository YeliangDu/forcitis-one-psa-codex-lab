# 派工卡 TA-A：工时单状态与轮次、提交日成对约束，调休上班日落在周末，工时单保留期登记，已处理事件登记口径（DATA-REPAIR-timesheet-audit 的 A 部分）

- 草稿：one-1a-par 起草助手，2026-10-16，基线 origin/main 3ce1b15a。数据设计一节尚未经 one-data 签发；A1 先写完整，one-data 当天签 A1，A2、A3 随后（`DATA_DESIGN_GATE.md` §1.2、§1.3）。
- 「待核」表示手头证据不足，开工前由实现者或 one-data 先核实再改，见第 13 节。
- 本卡把 `data/repair-cards/DATA-REPAIR-timesheet-audit.md` 的 A 部分（TA-01、TA-02、TA-03，低级顺带 CE-1、CE-2、WD-3、T-8）落成三个 PR；B 部分（TB-01 至 TB-09）不在本卡。

## 1. 卡号与类别
- 卡号：TA-A，对应 `data/repair-cards/DATA-REPAIR-timesheet-audit.md` 第 4 节「第一部分　工时与日历」的 TA-01、TA-02、TA-03 与低级顺带项；负责会话 one-1a-par
- 类别：缺陷修复（SESSION_PLAYBOOK §6.1 第 9 条）。一张卡，三个小 PR（A1、A2、A3），各自直接合入 main（§6.1 第 10 条）
- 批次：已建表数据设计审查修复批；TS-UNIQ 已合入 main（`3ce1b15a` 含其实现 PR），TA-01 依赖的状态集合已定稿
- 三个 PR 的范围一览（详见第 3 节）：

| PR | 条目 | 一句话 | DDL |
|---|---|---|---|
| A1 | TA-02 | `organization_attendance.work_calendar_days` 加「调休上班日必须落在周六或周日」CHECK | 是，新增 1 条 CHECK，无迁入风险 |
| A2 | TA-01 + T-8 | `cost_timesheet.timesheets` 加两条成对 CHECK；`round_number`、`submitted_date`、`legal_entity_id` 三列注释 | 是，新增 2 条 CHECK，注释改 3 列 |
| A3 | TA-03 + CE-1 | 工时单保留期登记（注册表注释，不改 retention）；已处理事件的登记口径改成与实现一致 | 无约束变更；有 1 处表注释改动 |

- 不含（已有归属或已关闭，本卡不做）：
  - CE-2（`consumed_events.consumed_at` 索引）：**关闭为不做**（one-data 裁定 2026-10-16：约每月 5 千张单，90 天内 1.5 至 3 万行，全表扫描可忽略）
  - WD-3（`day_kind` 内联 CHECK 具名）：随 G-D12（R-2a）一次改基线，不进本批
  - `timesheets.status` 内联 CHECK 具名：同属 G-D12，留给 R-2a（TS-UNIQ 第 13 节待核 3）
  - `timesheets` 保留期的生效改动（`retention` 改 `10y` 加作业）与归档作业本身：作业建成时才改（见 A3）；GUIDING_PRINCIPLES §6 补行交 one-architecture，不在本卡
  - 迁入映射：迁入单据沿用原编号、状态直接迁入、不经审批（ADR-0036）；工时迁入在 Phase 2，E3 迁入映射尚未写，本卡不替它定，只保证新 CHECK 对迁入单形态安全
  - T-4、T-5、T-6 余下索引、AE/IR/AB 各条（B 部分）、W2 日历版本化（WY-1、WY-2、WD-2）：他卡
  - 前置事项：TS-UNIQ 的迁入违约取证（one-ops）与本卡无关，不阻塞

## 2. 依据与出处核对

### 2.1 用户决定与 one-data 裁定
- 修复卡 `data/repair-cards/DATA-REPAIR-timesheet-audit.md` 第 4 节 TA-01、TA-02、TA-03 与低级顺带；其第 5 节（已定与待决）、第 6 节（TS-UNIQ 归属）。
- one-data 裁定（2026-10-16，消息）：
  1. TA-01 第二条 CHECK 选出口 (a)，写成补集 `status = 'DRAFT' OR round_number >= 1 OR numbering_rule_version_id IS NULL`：版本 ID 为空即迁入单，沿用 TS-UNIQ 裁定（`data/reviews/TS-UNIQ.md`），不枚举四个「提交过」状态；第一条 `(round_number = 0) = (submitted_date IS NULL)` 照落；`round_number`、`submitted_date` 列注释写明「迁入单为 0／空」；已合入的验收 `TimesheetNumberingRuleVersionIT` 里的迁入单（APPROVED、轮次 0、提交日空、版本 ID 空）原样通过。
  2. TA-03 选「保留 `BUSINESS_RECORD` + `#` 注释」（`DATA-REPAIR-timesheet-audit` PR-3 裁定 5 的先例，`data/reviews/DATA-REPAIR-timesheet-audit-PR3.md`）：注释写 owner（one-1a-par）、10 年口径（`data/RETENTION_PROPOSAL.md`）、到期锚「工时迁入入口前，归档作业须先建成」；不加 gap（门禁 §1.4）；`retention` 在作业建成时才改 `10y` 加作业名 `cost.timesheet.timesheet.archive`；GUIDING_PRINCIPLES §6 补行交 one-architecture。
  3. CE-2 关闭为不做；WD-3 随 G-D12 不进本批；T-8 只剩 `timesheets.legal_entity_id` 注释（代码按员工加工作日 `legalEntities.on(employee, workDate)` 解析，注释须与实现一致）；CE-1 纳入。
  4. 允许文件含 TA-03、T-8、CE-1 涉及的注释与登记文件。
  5. 拆三个 PR：A1=TA-02，A2=TA-01+T-8，A3=TA-03+CE-1。单 PR 内先提交测试代理验收（红）再提交 DDL（绿），前提是 one-data 签发行先于验收提交出现（对 `DATA_DESIGN_GATE.md` §1.2「表结构 PR 先合并再写验收」的顺序豁免，见第 12 节与第 13 节决定点 1）。
- 违反 CHECK 时库抛 `DataIntegrityViolationException`，接口返回 500。CHECK 只作最后防线，应用层已在聚合内保证；**不为这两条 CHECK 加错误码映射**（CS-1.8）。测试用直接 SQL 断言 SQLState `23514` 与约束名。

### 2.2 规则、ADR、规范
- ADR-0038（开放期直接改基线、不追加迁移）；ADR-0036（历史数据迁入）；ADR-0033 第 1 项第 5 条（`numbering_rule_version_id` 列）；ADR-0043 R-2（含个人信息关联数据保留期不得无期限，用户决定出处即 `RETENTION_PROPOSAL.md`）。
- SESSION_PLAYBOOK §12.2（预期表结构 diff）、§14.1 D2、D12；NAMING_STANDARD §2.4（`ck_<表>_<规则>`）、§2.4a。
- DATA_DESIGN_GATE：O4、B5、O8、C1、C3、T3、T4、E3。
- 复审意见：`data/review-parts/timesheet_attendance_audit_idem_sched.md` T-3、WD-1、T-7、CE-1、CE-2、T-8；`data/review-parts/SCAN_SUPPLEMENT.md` §4 第 14 组。

### 2.3 核对结果（逐条，基线 3ce1b15a；TS-UNIQ 已改过 `timesheets`，行号以此为准）

| # | 事项 | 核对结果 | 证据 |
|---|---|---|---|
| 1 | `timesheets` 状态与轮次、提交日库内无成对约束 | **属实** | 迁移 `cost_timesheet/V20261014140000__cost_timesheet_baseline.sql` 第 12 行 `round_number`、第 20 行 `submitted_date`，表内 CHECK 只有第 22 至 24 行三条非负约束；`Timesheet.java:147-150` 的 `submit()` 总是同时 `round++` 与写 `submittedOn`；列注释第 38、46 行写「0 表示从未提交」「空表示从未提交」 |
| 1a | TS-UNIQ 定稿的状态集合 | 五值不变：`DRAFT / SUBMITTED / RETURNED / APPROVED / REJECTED`；`REJECTED → SUBMITTED` 已在 `Timesheet.java` 的 `MACHINE` 中；撤销审批 `SUBMITTED → DRAFT` 保留轮次与提交日（`cancelSubmission`，`Timesheet.java:185-186`，不改 `round`、`submittedOn`） | 因此「提交过的状态」不必枚举：库内用补集写法（裁定 1） |
| 1b | 迁入单形态 | 迁入单据状态直接迁入、不经审批、版本 ID 为空（TS-UNIQ 裁定）；已合入验收 `acceptance/cost/timesheet/TimesheetNumberingRuleVersionIT.java:74-80` 直接插入 `APPROVED`，不给 `round_number`、`submitted_date`（取默认 0、空）、版本 ID 空 | 新两条 CHECK 对该形态为真：第一条 0 与空相等；第二条因版本 ID 空为真 |
| 2 | 调休上班日落周末只在应用里 | **属实** | `CalendarDay.java:19-22` 抛 `ORGANIZATION_ATTENDANCE_MAKE_UP_WORKDAY_ON_WEEKDAY`（422）；迁移 `organization_attendance/V20261014141000__organization_attendance_baseline.sql` 第 29 行内联 `CHECK (day_kind IN ('HOLIDAY','MAKE_UP_WORKDAY'))` 之外无日期约束；列注释第 41 行已写「必须是周六或周日」 |
| 2a | 无迁入风险 | **属实**（基线开放期无生产数据；旧日历 2016 至 2026 经 `CalendarDay` 构造器逐行校验后才写入，导入走同一聚合，不会绕过） | `JdbcWorkCalendarRepository` 经聚合写入；一次性导入由 one-ops 在重建后执行（第 8 节「合并后」） |
| 3 | 工时单保留期现为 `BUSINESS_RECORD` 且无作业 | **属实** | `backend/architecture/data-access-registry.yaml:24-32`（`retention: {policy: BUSINESS_RECORD}`）；全库无 `cost.timesheet.timesheet.archive` |
| 3a | 10 年口径已获用户认可 | **属实** | `data/RETENTION_PROPOSAL.md` 第 28 行（`cost_timesheet.timesheets`：10 年，归档到对象存储，在线按期删除；负责卡为 timesheet-audit A 部分；财务若认定为会计档案原始凭证改 30 年，待财务确认） |
| 4 | `consumed_events` 登记 `APPEND_ONLY` 与实现不符 | **属实** | 注册表第 18-23 行 `mutability: APPEND_ONLY`；`ConsumedEventsPurgeJob.java:49` 执行 `DELETE FROM cost_timesheet.consumed_events WHERE consumed_at < :cutoff`；`unversioned-table-writes.txt` 第 26 行已登记该清理作业写同表（「rows older than the keep period are deleted」） |
| 4a | 基线表注释与实现不符 | **属实** | 迁移第 58 行表注释写「只追加」；`JdbcConsumedEvents` 只插入（`unversioned-table-writes.txt:21`「insert-only」，描述的是该写入类本身，见第 13 节待核 3） |
| 5 | T-8：`legal_entity_id` 注释与实现不符 | **属实** | 迁移第 33 行写「由提交人所属法人传入」；实现 `SubmitTimesheet.java:84` `legalEntities.on(employee, command.workDate())`，按员工与工作日期解析，不是「传入」 |
| 6 | CHECK 违约时的返回 | `DataIntegrityViolationException`，未映射则 500（`JdbcTimesheetRepository` 只对 `DuplicateKeyException` 按约束名映射，不涉 CHECK） | 见 2.1 末段 |

## 3. 范围与卡上文件（越界先停下报告，不先做后报）

### 3.1 A1　TA-02：调休上班日 CHECK（`organization_attendance.work_calendar_days`）
- 范围：基线 DDL 增 `CONSTRAINT ck_work_calendar_days_make_up_weekend CHECK (day_kind <> 'MAKE_UP_WORKDAY' OR EXTRACT(ISODOW FROM calendar_date) IN (6, 7))`，写成表内具名约束，放在 `uk_work_calendar_days_…` 之后。约束名 36 个字符，合 NAMING_STANDARD §2.4 与 63 字符上限。`calendar_date` 为 `date`，`ISODOW` 不受会话时区影响。
- 允许文件：
  - `backend/src/main/resources/db/migration/organization_attendance/V20261014141000__organization_attendance_baseline.sql`（只限 `work_calendar_days` 一条 CHECK）
  - 测试：`backend/src/test/java/com/forcitis/one/acceptance/organization/attendance/`（新类 `WorkCalendarMakeUpWeekendIT`，由测试代理写）
- 不改：`day_kind` 内联 CHECK 与其列注释（WD-3 随 G-D12）；`CalendarDay`、错误码、`WorkCalendarYear`；日历版本化。W2 若重建明细表，本约束随表带走（DA-17 卡须承接，已写在修复卡 TA-02）。
- 注释：表注释、列注释均不变（`day_kind` 注释已写「必须是周六或周日」）。
- 预期失败形态：直接 SQL 违反返回 SQLState `23514`、约束名为 `ck_work_calendar_days_make_up_weekend`；经接口录入周三调休日仍由聚合在写库前拒绝，返回 422 与 `ORGANIZATION_ATTENDANCE_MAKE_UP_WORKDAY_ON_WEEKDAY`，不是 500。

### 3.2 A2　TA-01 + T-8：`timesheets` 两条成对 CHECK 与注释
- 范围：
  1. 增 `CONSTRAINT ck_timesheets_round_submitted CHECK ((round_number = 0) = (submitted_date IS NULL))`：从未提交（轮次 0）⇔ 提交日空；一旦提交，两者同时有值，撤销审批回草稿时轮次与提交日保留，不受影响。
  2. 增 `CONSTRAINT ck_timesheets_round_after_submit CHECK (status = 'DRAFT' OR round_number >= 1 OR numbering_rule_version_id IS NULL)`：非草稿的新建单（版本 ID 非空）必已提交过，轮次不小于 1；草稿可以是 0（新建未提交）或 ≥ 1（撤销后回草稿）；版本 ID 空为迁入单，状态直接迁入、不经审批，轮次可为 0（one-data 裁定 1，出口 (a)）。不枚举状态集合，将来状态集合变化时本条不必跟改。
  3. 列注释：`round_number`、`submitted_date`（新文本见数据设计一节）；表注释不改。
  4. T-8：`legal_entity_id` 列注释改成与实现一致（新文本见数据设计一节）。
- 允许文件：
  - `backend/src/main/resources/db/migration/cost_timesheet/V20261014140000__cost_timesheet_baseline.sql`（只限 `timesheets` 的两条 CHECK 与三列注释；`consumed_events` 的表注释归 A3，不在此 PR）
  - 测试：`backend/src/test/java/com/forcitis/one/acceptance/cost/timesheet/`（新类 `TimesheetRoundSubmittedCheckIT`，由测试代理写）
  - 夹具：若已有测试直接 INSERT 的 `timesheets` 行违反新 CHECK（目前只核到 `TimesheetNumberingRuleVersionIT` 的迁入单，原样通过，见第 2.3 节第 1b 行），停下报告 one-data，不自行放宽
- 不改：`status` 内联 CHECK（G-D12）、唯一约束、索引、其余列；`Timesheet`、`SubmitTimesheet` 等 Java（聚合已保证，本卡不改代码）；不加错误码映射。

### 3.3 A3　TA-03 + CE-1：保留期登记注释与已处理事件登记口径（无约束变更）
- 范围：
  1. **TA-03**：`backend/architecture/data-access-registry.yaml` 中 `cost_timesheet.timesheets` 的 `retention` 行保持 `{policy: BUSINESS_RECORD}`，行尾加 `#` 注释，写：owner one-1a-par；口径为保留 10 年、归档到对象存储后在线删除（`docs/modernization/data/RETENTION_PROPOSAL.md`，用户已认可）；财务是否认定会计档案原始凭证（改 30 年）待财务确认；到期锚「工时迁入入口前，归档作业须先建成」。**不加 `gaps` 条目**（门禁 §1.4）；`retention` 在归档作业建成时才改为 `{policy: 10y, job: cost.timesheet.timesheet.archive}`，那是另一张功能补做卡，不在本卡。
  2. **CE-1**：
     - 注册表 `cost_timesheet.consumed_events` 的 `mutability` 由 `APPEND_ONLY` 改 `INSERT_DELETE`（因 `ConsumedEventsPurgeJob.java:49` 会 `DELETE`）。
     - 基线表注释的「只追加」改成与实现一致（新文本见数据设计一节）。**这是表注释改动，`pg_dump --schema-only` 会产生注释差异，写进第 7 节预期 diff。**
     - `backend/architecture/unversioned-table-writes.txt` 第 21 行（`JdbcConsumedEvents`）的「insert-only」改成与实现一致的措辞；第 26 行（清理作业）已正确，不动。具体措辞见第 13 节待核 3。
  3. GUIDING_PRINCIPLES §6 补「工时单 10 年」一行：**不在本卡**，交 one-architecture（A3 的 PR 正文与报告抄送）。
- 允许文件：
  - `backend/architecture/data-access-registry.yaml`（只限 `cost_timesheet.timesheets` 的 retention 行注释、`cost_timesheet.consumed_events` 的 mutability 行）
  - `backend/architecture/unversioned-table-writes.txt`（只限第 21 行的注释文字）
  - `backend/src/main/resources/db/migration/cost_timesheet/V20261014140000__cost_timesheet_baseline.sql`（只限 `consumed_events` 的表注释一处）
  - 测试：`backend/src/test/java/com/forcitis/one/acceptance/cost/timesheet/`（新类 `TimesheetRegistryEntriesTest`，由测试代理写）；既有 `DataArchitectureTest` 原样通过
- 不改：任何列、约束、索引；`consumed_events` 的 `consumed_at` 索引（CE-2 关闭）；保留期数值（仍 90 天）；`ConsumedEventsPurgeJob`、`JdbcConsumedEvents` 的代码；不新增例外清单条目。

### 3.4 归属与不碰（三个 PR 共同）
- 归属：A1 独占 `work_calendar_days` 的本条 CHECK；A2 独占 `timesheets` 的两条成对 CHECK 与三列注释；A3 独占注册表两处登记与 `consumed_events` 表注释。
- 不碰：验收测试目录（由测试代理写，实现者不改）；B 部分的文件（`platform_audit`、`platform_idempotency`、`data-classification.txt` 等）；他卡文件；kernel 共享构件。
- 合并顺序：A1、A2、A3 彼此无代码依赖。A2、A3 改同一迁移文件（不同区段），后合的 PR 须先同步 main，冲突只在该文件，停下报告不自行取舍。

## 4. 验收测试（由测试代理先写，§6.1 第 8 条）
- 测试代理：`code-modernization:test-engineer`（**sonnet**），提交说明带 `Acceptance-Test-Author: test-engineer`
- 次序（每个 PR 内）：one-data 签发行先出现 → 测试代理提交验收（此时 DDL 未改，新约束相关断言为红）→ 实现者提交 DDL（绿）。顺序豁免见第 12 节
- 失败断言口径：直接 SQL 插入或更新违反 CHECK 时，断言 SQLState `23514` 且约束名等于期望值（取自 `PSQLException` 的服务器错误信息）；不断言错误码映射、不断言 HTTP 500
- 测试名前缀统一 `DATA-REPAIR-timesheet-audit TA-nn`（修复卡第 7 节）

### 4.1 A1 验收：`acceptance/organization/attendance/WorkCalendarMakeUpWeekendIT`（草案）
1. `DATA-REPAIR-timesheet-audit TA-02 周六、周日的调休上班日可以直接插入`
2. `DATA-REPAIR-timesheet-audit TA-02 周三的调休上班日被拒绝，SQLState 23514，约束名 ck_work_calendar_days_make_up_weekend`（另参数化周一至周五各一例）
3. `DATA-REPAIR-timesheet-audit TA-02 工作日与周末的节假日都可以直接插入`
4. `DATA-REPAIR-timesheet-audit TA-02 经接口录入周三调休日仍返回 422 与 ORGANIZATION_ATTENDANCE_MAKE_UP_WORKDAY_ON_WEEKDAY，不是 500`
5. `DATA-REPAIR-timesheet-audit TA-02 会话时区为非上海时区（如 America/Los_Angeles、Pacific/Kiritimati）时，同一日期的判定结果相同`
6. `DATA-REPAIR-timesheet-audit TA-02 读 pg_constraint 核对约束定义：表为 work_calendar_days、名为 ck_work_calendar_days_make_up_weekend、类型为检查约束，day_kind 内联枚举 CHECK 仍在`
7. 原样重跑：`WorkCalendarIT`（`backend/src/test/java/com/forcitis/one/organization/attendance/`）、`WorkCalendarYearTest`（`…/organization/attendance/domain/`）

### 4.2 A2 验收：`acceptance/cost/timesheet/TimesheetRoundSubmittedCheckIT`（草案）
1. `DATA-REPAIR-timesheet-audit TA-01 首次提交后轮次为 1，提交日为当天（上海业务日）`
2. `DATA-REPAIR-timesheet-audit TA-01 退回后重新提交，轮次为 2，提交日更新为重提当天`
3. `DATA-REPAIR-timesheet-audit TA-01 被驳回后修改原单再提交，轮次加一，提交日更新`
4. `DATA-REPAIR-timesheet-audit TA-01 撤销审批回草稿时保留轮次与提交日，再次提交轮次加一`
5. `DATA-REPAIR-timesheet-audit TA-01 审批通过、审批驳回的回写不改轮次与提交日`
6. `DATA-REPAIR-timesheet-audit TA-01 直接插入 SUBMITTED、RETURNED、APPROVED、REJECTED 且轮次为 0、提交日为空、版本 ID 非空，被拒，SQLState 23514，约束名为 ck_timesheets_round_after_submit`（逐状态参数化；提交日同为空使第一条 CHECK 为真，单独打到第二条）
7. `DATA-REPAIR-timesheet-audit TA-01 轮次 ≥ 1 而提交日为空被拒，轮次为 0 而提交日非空被拒，约束名为 ck_timesheets_round_submitted`
8. `DATA-REPAIR-timesheet-audit TA-01 草稿且轮次 0、提交日空可以插入；草稿且轮次 ≥ 1、提交日非空（撤销审批后的形态）可以插入`
9. `DATA-REPAIR-timesheet-audit TA-01 迁入单（APPROVED、轮次 0、提交日空、版本 ID 空）可以插入；同一形态但版本 ID 非空被拒`
10. `DATA-REPAIR-timesheet-audit TA-01 读 pg_constraint 核对两条约束定义与名字`（定义文本以 `pg_get_constraintdef` 归一后比较语义，不比较空白）
11. `DATA-REPAIR-timesheet-audit T-8 legal_entity_id 列注释与实现一致`：断言列注释包含「工作日期」且不含「传入」；`round_number`、`submitted_date` 注释含「迁入单」
12. 原样重跑：`TimesheetStateMachineTest`、`scenario/EventSequencePersistenceIT`、`TimesheetEmployeeDayUniqueIT`、`TimesheetNumberingRuleVersionIT`

### 4.3 A3 验收：`acceptance/cost/timesheet/TimesheetRegistryEntriesTest`（草案）
1. `DATA-REPAIR-timesheet-audit TA-03 注册表中 cost_timesheet.timesheets 的 retention 仍为 BUSINESS_RECORD，行内注释含 owner one-1a-par、10 年、到期锚「工时迁入入口前，归档作业须先建成」，且无 gaps 条目指向该表`
2. `DATA-REPAIR-timesheet-audit CE-1 注册表中 cost_timesheet.consumed_events 的 mutability 为 INSERT_DELETE`
3. `DATA-REPAIR-timesheet-audit CE-1 consumed_events 的表注释与 unversioned-table-writes.txt 第 21 行不再写「只追加」「insert-only」`
4. 原样重跑：`DataArchitectureTest` 全绿（注册表与迁移双向一致，无新增 `gaps`）
- 说明：注册表与清单文件的断言属文本核对，测试代理在 `acceptance` 下只写不依赖实现细节的读文件断言；如果认为这类断言应归 `DataArchitectureTest` 本体，停下报告 one-data，不自行改守护。

- 实现方认为验收测试有误：停下报告，由复审裁定（`Acceptance-Ruling: RULING-TA-A-n`），不自行修改

## 5. 执行者与模型
- 测试代理：`code-modernization:test-engineer`，**sonnet**
- 实现：子代理，**sonnet**（三个 PR 可由同一实现者顺序做，也可并行，同基线 CS-7.6）
- 复审：**sonnet 一轮**（opus 只用于 B 部分 TB-01、TB-02、TB-07，不用于本卡）；修后复核 sonnet；最多两轮，之后新发现的非 High 列为残余项在 PR 单列（SESSION_PLAYBOOK §11 第 6 条）
- 数据设计门禁（`DATA_DESIGN_GATE.md` §1.2）：本卡发 one-data 审查 → A1 当天签发，A2、A3 随后签发 → 各 PR 内验收（红）→ DDL 或登记（绿）。验收与实现开工前核对签发有效（§1.3）
- 开发库：**不碰任何常驻库**。ECS 上 dev 库由 one-ops 手工安装 rc.4，另请勿重建；测试只用 Testcontainers 临时容器（镜像限 `backend/architecture/test-images.txt`，CLAUDE.md 第 9 条例外）
- 工作区：每个 PR 一个独立 worktree，分支建议 `claude/one-1a-par-ta-a1`（TA-02）、`claude/one-1a-par-ta-a2`（TA-01、T-8）、`claude/one-1a-par-ta-a3`（TA-03、CE-1），基线为当时的 main（本卡草稿所在的 `claude/one-1a-par-ta` 仅放卡文件）
- 不交 Codex：含数据完整性判断

## 6. 禁止事项
- 不改卡外文件；不顺手重构、改名或统一风格；保留的约束、索引、列名一律不改
- 不为任何 CHECK 加错误码映射或把 500 改成别的返回；CHECK 只作最后防线（CS-1.8）
- 不枚举「提交过」的状态集合；不加触发器；不用部分唯一索引；不动 `status` 内联 CHECK；不改 `day_kind` 内联 CHECK
- A3 不加 `gaps` 条目，不改 `retention` 数值；不写归档作业；不改 GUIDING_PRINCIPLES §6
- 不放宽、不删除断言；不改验收测试目录；既有测试夹具若撞新 CHECK，停下报告，不自行改
- 不追加迁移文件（ADR-0038）；不改 kernel 共享构件
- 不碰常驻库，不对 ECS 上 dev 库做任何操作，不重建 dev 库；Testcontainers 临时容器可直接跑，其他容器、常驻容器、对外调用先问；不建账号
- 不打印、不写入密钥；用语遵守 `terms.yaml`；不用「PSA」指代系统；老系统事实只作证据，不进目标表设计
- 缺失或无效输入失败并告警，不静默跳过

## 7. 预期表结构 diff（SESSION_PLAYBOOK §12.2）
- 方法：两个空库分别执行改动前（main）、后的基线，`pg_dump --schema-only` 对比，种子另做 `--data-only` 对比；每个 PR 各做一次，只含本 PR 的差异。注意 `pg_dump` 含注释，注释差异也要列出

### 7.1 A1 预期差异（只能是）
- `organization_attendance.work_calendar_days`：新增约束 `ck_work_calendar_days_make_up_weekend CHECK (day_kind <> 'MAKE_UP_WORKDAY' OR EXTRACT(ISODOW FROM calendar_date) IN (6, 7))`（`pg_dump` 会输出 `(((day_kind)::text <> 'MAKE_UP_WORKDAY'::text) OR (EXTRACT(isodow FROM calendar_date) = ANY (ARRAY[(6)::numeric, (7)::numeric])))` 之类的归一形式，以语义为准）
- 不得出现：该表其他列、类型、可空性、默认值、约束、索引、注释的任何变化；`work_calendar_years` 的任何变化；其他 schema；种子数据变化

### 7.2 A2 预期差异（只能是）
- `cost_timesheet.timesheets`：
  - 新增约束 `ck_timesheets_round_submitted CHECK ((round_number = 0) = (submitted_date IS NULL))`
  - 新增约束 `ck_timesheets_round_after_submit CHECK (status = 'DRAFT' OR round_number >= 1 OR numbering_rule_version_id IS NULL)`
  - 列注释改 3 处：`round_number`、`submitted_date`、`legal_entity_id`（新文本见数据设计一节 A2 的 C1 小节）
- 不得出现：`timesheets` 其他列、类型、可空性、默认值的变化；`status` CHECK 取值或名称的变化；`ck_timesheets_hours`、`ck_timesheets_round`、`ck_timesheets_event_count`、`pk_timesheets`、两个唯一约束、两个索引的任何变化；表注释变化；`consumed_events` 的任何变化；其他 schema；种子数据变化

### 7.3 A3 预期差异（只能是）
- **无约束、列、索引变化**
- 有**注释差异**：`cost_timesheet.consumed_events` 的表注释（「只追加」改成与实现一致，新文本见数据设计一节 A3 的 C1 小节）。这是 A3 唯一的 `pg_dump` 差异，必须在 PR 正文单列
- 非 DDL 差异：`data-access-registry.yaml`（`timesheets` retention 行 `#` 注释，`consumed_events` mutability）、`unversioned-table-writes.txt` 第 21 行注释文字
- 不得出现：任何列、约束、索引、默认值变化；`timesheets` 表与列注释变化（归 A2）；`gaps` 新增条目；`retention` 数值变化；种子数据变化

## 8. 交付物与自查
- 交付物：
  - A1：基线 DDL 一条 CHECK；验收与既有测试
  - A2：基线 DDL 两条 CHECK 与三列注释；验收与既有测试
  - A3：注册表两处、清单文件一处、基线表注释一处；验收与既有测试
- 守护文件：`data-classification.txt`、`ddl-naming-baseline.txt`、`primary-key-exemptions.txt`、各 `*-exceptions.txt` 本卡不新增条目
- 自查：`scripts/precheck.sh origin/main` 全部 PASS 或有理由的 SKIP，输出贴进 PR「precheck.sh 输出」一栏；`pre-commit install`；报告前 gitleaks 8.30.1 扫全历史
- 受影响模块测试：precheck 给出的命令；交付前全量 `scripts/ci/backend_verify.sh`（Testcontainers 临时容器可直接跑）
- PR 正文单列：预期与实际表结构 diff；签发行（`数据设计：通过（one-data，…）`，DG-1，三个 PR 都含迁移文件改动，A3 的表注释同）；被改的既有断言或夹具（预期为无）；A3 的注释差异
- 报告分开写「已验证」与「未运行」

### 合并后（抄送 one-ops）
- main 相对 rc.4 已改三个基线文件（TS-UNIQ 的 `cost_timesheet`、R-1b P5 等），下次 dev 部署本就要按 `phase1a/infra/runbooks/dev-db-rebuild.md` 重建 dev 库。**A1、A2 赶在下次部署前合入，不多一次重建**；A3 只改注释，随同一次重建。
- 本卡与 one-ops 无直接操作：不碰 ECS 上 dev 库，不要求在 rc.4 上重建。
- 重建后 one-ops 需重新导入 2016 至 2026 日历与 2027 日历，并重新做授权（日历明细表在重建中清空，A1 的新 CHECK 随基线生效；导入的调休日均为周末，不受影响）。该动作已在 dev-db-rebuild 步骤中，本卡仅提示抄送，不替 one-ops 排。
- 合并后通知：one-data（复核预期 diff，落 `phase1a/DATA_SCORECARD.md` 新增行）、one-ops（重建与导入）、one-architecture（GUIDING_PRINCIPLES §6 补「工时单 10 年」一行）。

## 9. 复审要求（§6.1 第 4、11 条）
- 复审：sonnet 一轮；Blocker / High 修后 sonnet 复核；Medium / Low 执行者补测试、CI 通过即关闭，负责会话抽查；不阻塞合并的登记进下一批
- 复审清单：SESSION_PLAYBOOK §14、§14.1（D2、D12）；CODING_STANDARD CS-1.8、CS-1.9、CS-5.5、CS-5.6
- 专查：
  - 实际表结构 diff 只含第 7 节声明项；A3 的注释差异已在 PR 正文单列
  - 两条 `timesheets` CHECK 对迁入单形态（APPROVED、0、空、版本 ID 空）为真，对新建的提交过单为真，对撤销回草稿后的形态（轮次 ≥ 1、提交日非空、DRAFT）为真
  - 未为 CHECK 加错误码映射，聚合行为不变
  - 新注释遵守 `terms.yaml` 用语，不含方言，不用「PSA」；`legal_entity_id` 注释与 `SubmitTimesheet.java:84` 一致
  - A3 没有 `gaps` 新增，`retention` 数值未变；`INSERT_DELETE` 登记后 `DataArchitectureTest` 全绿
  - 被改的既有断言为零

## 10. 报告去向
- 完成、部分完成或阻塞，都向 one-1a-par，抄 one-coordinator（SESSION_PLAYBOOK §4 第 7 步）；A1、A2、A3 各报一次
- PR／卡／交接记录（消息按 TEAM-53 四节点及需动作事件送达，不另报号）：分支、改动文件、验证结果（已验证 / 未运行）、precheck 输出、预期与实际表结构 diff、未完成项、需要用户决定的事

## 11. 本卡新词（开工前术语门禁，SESSION_PLAYBOOK §5）
已查 `docs/modernization/glossary/terms.yaml`（见 `backend/glossary/terms.yaml` 与 `backend/glossary/proposed/*.yaml`）：本卡没有新标识符、界面文字、表列名、错误码；新增的只有三个约束名，按 NAMING_STANDARD §2.4 `ck_<表>_<规则>` 组合已有词（`timesheets`、`round`、`submitted`、`work_calendar_days`），约束名不属术语登记对象。新注释文本用已有中文名（轮次、提交日、工作日期、法人主体、迁入单据）；「迁入单」作为说明性短语出现在注释中，与 TS-UNIQ 注释所用「迁入单据」同义，开工前由实现者在词表中再核一次，查不到同义词条且需要登记时，当场在 `backend/glossary/proposed/` 登记提议术语并写明使用范围与 bc，不自行裁定。

| 标识符 | 种类 | 说明 |
|---|---|---|
| `ck_work_calendar_days_make_up_weekend` | 约束名 | 36 字符，`ck_<表>_<规则>`，规则取自已有词「调休上班日」`make_up_workday` 与「周末」 |
| `ck_timesheets_round_submitted` | 约束名 | 29 字符，规则为「轮次与提交日成对」 |
| `ck_timesheets_round_after_submit` | 约束名 | 32 字符，规则为「提交后轮次不小于 1」 |

## 12. 顺序与前置
1. 本卡 → one-coordinator 看 → one-data 审查；**A1 必须先写完整，one-data 当天签 A1**，A2、A3 随后签（签发行写法 `数据设计：通过（one-data，日期，依据 <卡文件的 git 提交号>）`）
2. 每个 PR 内：签发行先出现 → 提交测试代理验收（红）→ 提交 DDL 或登记改动（绿）→ precheck → 复审（sonnet）→ one-data 复核预期 diff → one-coordinator 合并（CLAUDE.md 第 14 条）
3. 顺序豁免：`DATA_DESIGN_GATE.md` §1.2 的常规次序是「表结构 PR 先合并，再写验收」。本卡三个 PR 的 DDL 都只是新增 CHECK 或注释，不改列，不影响 Java；按 one-data 的裁定，同一 PR 内先验收后 DDL，前提是签发行先于验收提交出现。这条豁免需 one-data 在审查记录里写明（第 13 节决定点 1）
4. 三个 PR 彼此无依赖；A1、A2 赶在下次 dev 部署（重建 dev 库）之前合入
5. 前置：TS-UNIQ 已合入（状态集合与版本 ID 列已在 main）；无其他前置

## 13. 待核事项与需要决定的点

### 待核（证据不足，开工前先核实）
1. **新旧夹具撞约束**：除 `TimesheetNumberingRuleVersionIT` 外，是否还有测试直接 INSERT `timesheets` 且给了非默认的 `round_number`、`submitted_date`、状态。目前 `grep` 只在该一个类里找到直接 INSERT；A2 开工前由实现者全仓复核。
2. **ISODOW 与 `pg_dump` 的归一形式**：第 7.1 节的归一文本是预期形态，实际以 PostgreSQL 版本输出为准，验收按语义比较（读 `pg_get_constraintdef`），不按文本。
3. **`unversioned-table-writes.txt:21` 与表注释的新措辞**：第 21 行描述的是 `JdbcConsumedEvents` 这个写入类，该类实际只插入，「insert-only」对它并不错；清理作业另在第 26 行登记。one-data 裁定要求「与实现一致」，字面改法需确认：建议第 21 行改成「idempotency record of handled events: rows are inserted and never updated, the unique key is the check; the retention job deletes old rows (see the purge line)」；表注释建议改成「已处理的事件：幂等记录；粒度：每个消费方每个事件标识一行，只插入、不改写，由保留期清理作业删除过期行（保留 90 天）；消费方按事件标识去重，事件至少投递一次」。**以 one-data 在审查中的文字为准**。
4. **`DataArchitectureTest` 对 `INSERT_DELETE` 的检查**：改登记后是否触发新规则（例如要求有 DELETE 的写入类在清单中登记；`unversioned-table-writes.txt:26` 已登记）。A3 开工后先跑守护确认，若出现新的违规，停下报告。
5. **`legal_entity_id` 注释中「创建后不改」是否属实**：`Timesheet` 的 `revise` 是否改法人，实现者读代码核实；不属实则去掉该半句。
6. **财务是否认定工时单为会计档案原始凭证（改 30 年）**：仍待财务确认，转到归档作业卡，不在本卡；注释中只写「待财务确认」。
7. **旧日历导入是否会写周中调休日**：旧系统日历 2016 至 2026 的导入经 `CalendarDay` 构造器逐行校验，若旧数据有周中的调休日，导入会在聚合层失败（今天已是如此），不是 A1 引入的新问题；由 one-ops 导入时发现，不在本卡处理。

### 需要 one-data 或用户决定的点（不预选，逐条）
1. **同一 PR 内先验收后 DDL 的顺序**，是否视为 `DATA_DESIGN_GATE.md` §1.2「表结构 PR 先合并，再写验收」的豁免，需 one-data 在审查记录里写明；若不接受，则每个 PR 拆成「DDL PR → 验收 + 实现 PR」两步，PR 数翻倍。
2. **`unversioned-table-writes.txt:21` 与 `consumed_events` 表注释的最终措辞**（待核 3）。
3. **A3 的 `#` 注释放在 `retention` 行尾还是 `timesheets` 条目上一行**：两处都不改变解析，PR-3 先例放在条目内；格式以 one-data 为准。
4. **GUIDING_PRINCIPLES §6 补「工时单 10 年」一行**：已定交 one-architecture，待其排期；本卡 A3 合并后在报告中抄送。
5. **财务认定（10 年或 30 年）**：待财务（待核 6）。
6. **`round_number`、`submitted_date` 注释中「迁入单为 0／空」是否与 E3 迁入映射将来要定的口径冲突**：E3 未写；若 Phase 2 迁入时决定把迁入单的提交日填为业务日期，则本卡注释与新 CHECK 的第二条之外的路径需重议。由 one-data 在 E3 立项时回看，本卡不预设。

## 数据设计（DATA_DESIGN_GATE.md 附录 A；材料按条目号对应，按 PR 分 A1、A2、A3）

数据设计：待审（one-data）。A1 先写完整，one-data 当天签 A1，A2、A3 随后。签发行尚未出现；测试代理与实现代理开工前须核对签发有效（`DATA_DESIGN_GATE.md` §1.2、§1.3）。

### 涉数据（三个 PR 共同）
是。§1.1 第 1 项（约束、注释、基线 `db/migration` 改动）；第 5 项（改动数据访问注册表条目，A3）。第 2 项事件载荷、第 3 项读模型与第 4 项迁入映射：**无变化**（A1、A2 不改字段、不迁入；E3 迁入映射不在本卡，只保证新 CHECK 对迁入单形态安全）；`data-classification.txt` 不改。

---

## A1 数据设计：TA-02　`organization_attendance.work_calendar_days`

### 表 → 出处（B1、B2）
| 表 | 出处 | 聚合 | 注册表 role |
|---|---|---|---|
| `organization_attendance.work_calendar_days` | 工作日历（`WorkCalendarYear` 的子行）；业务规则「调休上班日必须是周六或周日」见 `CalendarDay.java:19-22`、列注释 | `WorkCalendarYear`（`architecture/ddd/aggregates.yaml`，`organization.attendance`），子行 | `AGGREGATE_CHILD`（现状，本 PR 不改）|

schema 归属不变：`organization_attendance`，BC `organization.attendance`。

### 状态、关系、基数落点（B3、B3a、B4）
- 状态：`day_kind` 两个取值 `HOLIDAY`、`MAKE_UP_WORKDAY` 已在内联 CHECK 中，与 Java `DayKind` 枚举一致，本 PR 不动（具名随 G-D12）。无状态机、无事件目标状态变化。
- 关系与基数：`work_calendar_year_id` 同 schema 外键（`ON DELETE CASCADE`，同聚合子行）、本 PR 不动；无新增列、关系。

### 不变量 → 约束清单（B5、O4）
| 不变量（来源） | 承担者 | 无法落约束时的理由 |
|---|---|---|
| 调休上班日必须是周六或周日（列注释；`CalendarDay.java:19-22`；修复卡 WD-1） | **`ck_work_calendar_days_make_up_weekend`（本 PR 新增）**；聚合构造器在写库前抛 422 并给业务错误码 | —；规则只依赖本行两列，可落库 |
| 同一年度内一个日期一行 | `uk_work_calendar_days_work_calendar_year_id_calendar_date`（已有） | — |
| 例外日期必须在所属年度内（WD-2） | 随 W2 版本表处理，**不在本卡** | 跨表（行日期 ⊂ 年度），随版本化重建 |
| `day_kind` 取值 | 内联 CHECK（已有，不动） | — |

### 用例 → 聚合 → 表（O1）
| 用例 | 聚合 | 表 |
|---|---|---|
| 维护工作日历年度（含例外日期） | `WorkCalendarYear` | `work_calendar_years`、`work_calendar_days`（子行随根写入） |
| 查询是否工作日 | — | 只读 |
本 PR 不改用例。

### 业务键、数据类别与时态（O3、O6）
| 表 | 业务键 | 类别 | 版本化方式或不需要的理由 |
|---|---|---|---|
| `work_calendar_days` | 业务上「一个日历年度内一个例外日期至多一行」：`(work_calendar_year_id, calendar_date)` | 配置（子行）| 日历版本化属 W2（WY-2，DA-17），**不在本卡**；本 PR 不新增列，不碰区间 |

### 查询路径（O10）
无变化。新 CHECK 仅在写入时对单行求值，无查询路径、无新索引。预算类别：单行写入常数开销。

### 横切口径、法人与期间（O14、O13）
- 时区：`calendar_date` 是 `date` 自然日，`EXTRACT(ISODOW …)` 对 `date` 不受会话时区影响；验收覆盖非上海时区会话。CC-T01 不受影响。
- 法人与期间（O13）：日历为全企业共用配置，无法人归属，不适用。
- 幂等、并发、保留期、金额、分页：不涉及（保留期 `PERMANENT` 不变）。

### 适用的 ADR 条款对照（O15）
| ADR 条款 | 规定的列 / 键 / 约束 | 预期 diff 位置或「不适用（理由）」 |
|---|---|---|
| ADR-0038 | 开放期改基线、附预期 diff、不追加迁移 | 已含：第 3.1、7.1 节 |
| ADR-0033 第 1 项 | uuid 主键、UUIDv7 | 不适用（理由：不改主键与标识） |
| ADR-0036 | 迁入数据 lineage | 不适用（理由：无新列；旧日历 2016 至 2026 由一次性导入，注册表 lineage 已登记 `ONE_OFF_IMPORT`，导入经聚合校验） |
| ADR-0043 | 个人信息分级 | 不适用（理由：日历配置无个人信息） |

### 值对象、`detail` / 自由文本 / `jsonb`（O16、C4）
无新增值对象；本 PR 不新增列，无自由文本、`detail`、`jsonb`。

### 注册表条目草稿（C2、C3、C5）
不变：`role: AGGREGATE_CHILD`（或现登记值）、`mutability` 不变、`retention: {policy: PERMANENT}`、`lineage: {source: NEW, migrated_from: [old work calendar 2016-2026], via: ONE_OFF_IMPORT}`、`writers: [organization.attendance]`。本 PR 不改注册表。

### 分级表（C4）
本 PR 不新增列，无新增登记；`calendar_date`、`day_kind` 非个人信息，维持未登记默认 `INTERNAL`。

### 表注释与列注释草稿（C1）
- 表注释、列注释：**均不改**。`day_kind` 注释「类型：HOLIDAY 节假日 / MAKE_UP_WORKDAY 调休上班日（必须是周六或周日）」已描述该规则，新 CHECK 使其库内成立；约束注释（`COMMENT ON CONSTRAINT`）本仓库惯例无，不加。

### 分析事实（A1–A8）
无分析事实：不新增事件载荷字段、读模型、指标。A6：日历配置行不是业务单据或事实行，不适用。

### 基线 / 目标 / 过渡 / 回滚（T1–T4）
- T1 基线：`organization_attendance.work_calendar_days` 已存在，迁移 `V20261014141000__organization_attendance_baseline.sql`；改动前仅有 `day_kind` 内联 CHECK、年度外键、年度加日期唯一约束，规则只在聚合里。
- T2 目标：BC `organization.attendance`，Phase 1a 修复批；其他 BC 经 `WorkCalendar` 查询接口读，不直接读表。
- T3 过渡（基线开放期）：对在途开发库的影响——基线改动须按 `phase1a/infra/runbooks/dev-db-rebuild.md` 重建开发库；已存在的日历数据来自聚合校验过的导入，均不违反新 CHECK（待核 7）；重建后 one-ops 重新导入 2016 至 2026 日历与 2027 日历并授权（见第 8 节「合并后」）。`legacy_numbers` 不受影响。
- T4 回滚：随库重建（`dev-db-rebuild.md`）；基线关闭后不适用。

### 专项（E1–E3）
- E1 事件载荷：无变化。E2 读模型：无。E3 迁入映射：无变化；旧日历导入已存在且经聚合校验，新 CHECK 对其安全。

### 预期表结构 diff
见第 7.1 节。

---

## A2 数据设计：TA-01 + T-8　`cost_timesheet.timesheets`

### 表 → 出处（B1、B2）
| 表 | 出处 | 聚合 | 注册表 role |
|---|---|---|---|
| `cost_timesheet.timesheets` | 信息图 OBJ-66「工时单」的单据行（粒度差异是 T-5，已登记 N-10，排 Phase 2，不在本卡）；用户 Q15「一人一日一单」；TS-UNIQ 数据设计一节 | `Timesheet`（`cost.timesheet`） | `BUSINESS_DOCUMENT`（不变） |

schema 归属不变：`cost_timesheet`，BC `cost.timesheet`。

### 状态、关系、基数落点（B3、B3a、B4）
- 状态 `CHECK` ↔ 事件目录 ↔ 状态机 ↔ Java 枚举：由 TS-UNIQ 定稿，**本 PR 不改**，五值 `DRAFT / SUBMITTED / RETURNED / APPROVED / REJECTED`，三方一致已由验收 `TimesheetStatusConsistencyTest` 守护。
- 本 PR 的两条 CHECK 与状态的关系（供 B3 判定）：

| 状态 | 进入它的转换 | `round_number` | `submitted_date` | 版本 ID | 两条 CHECK |
|---|---|---|---|---|---|
| `DRAFT`（新建） | 初始 | 0 | 空 | 非空（新建单） | 通过：第一条 0 与空；第二条因 `DRAFT` |
| `DRAFT`（撤销审批后） | `SUBMITTED → DRAFT`（`cancelSubmission`，保留轮次与提交日） | ≥ 1 | 非空 | 非空 | 通过：第一条非零与非空；第二条因 `DRAFT` |
| `SUBMITTED` | `DRAFT/RETURNED/REJECTED → SUBMITTED`（`submit()`：`round++`，写提交日） | ≥ 1 | 非空 | 非空 | 通过 |
| `RETURNED`、`REJECTED`、`APPROVED`（经审批） | 审批结果回写，不改轮次与提交日 | ≥ 1 | 非空 | 非空 | 通过 |
| `APPROVED`（迁入单） | 状态直接迁入，不经审批（ADR-0036） | 0 | 空 | **空** | 通过：第一条 0 与空；第二条因版本 ID 空 |

- 关系与基数（B4）：本 PR 不增列、不增关系；`numbering_rule_version_id` 的 `0..1`（空表示迁入单据）已由 TS-UNIQ 落定，新 CHECK 把它用作「迁入单」的判别。

### 不变量 → 约束清单（B5、O4）
| 不变量（来源） | 承担者 | 无法落约束时的理由 |
|---|---|---|
| 从未提交 ⇔ 轮次为 0 ⇔ 提交日为空（列注释；`Timesheet.java:147-150`；SS §4 第 14 组） | **`ck_timesheets_round_submitted`（本 PR 新增）** | — |
| 新建的非草稿单必已提交过，轮次不小于 1（修复卡 TA-01） | **`ck_timesheets_round_after_submit`（本 PR 新增）**；聚合 `submit()` | 迁入单（版本 ID 空）状态直接迁入、不经审批，轮次可为 0，故补集中加 `numbering_rule_version_id IS NULL`；不枚举状态，避免将来状态集合变化时要改约束 |
| 轮次、事件序号非负 | `ck_timesheets_round`、`ck_timesheets_event_count`（已有）| — |
| 状态取值 | 内联 CHECK（已有，不动） | — |
| 每人每个工作日至多一张 | `uk_timesheets_employee_id_work_date`（TS-UNIQ，已有） | — |
| 提交日是上海业务日，不晚于提交时刻所在日 | 聚合 `submit()` 取注入时钟；不落 CHECK | 取决于时钟 |
| 版本 ID 在新建路径必填 | 应用层（TS-UNIQ 已落定） | 库内该列因迁入单而可空，无法落 `NOT NULL` |

### 用例 → 聚合 → 表（O1）
| 用例 | 聚合 | 表 |
|---|---|---|
| `SubmitTimesheet`（首次提交创建并提交；再次提交修改并提交，含驳回后重提） | `Timesheet` | `timesheets`；首次提交同事务取号（已登记例外，TS-UNIQ 数据设计一节） |
| `CancelTimesheet`、`WithdrawTimesheet`、`ApplyApprovalResult` | `Timesheet` | `timesheets`（`ApplyApprovalResult` 另写 `consumed_events`） |
本 PR 不改用例与代码。

### 业务键、数据类别与时态（O3、O6）
| 表 | 业务键 | 类别 | 版本化方式或不需要的理由 |
|---|---|---|---|
| `timesheets` | `(employee_id, work_date)`；`timesheet_number`（均已有，TS-UNIQ）| 交易（单据）| 不需要，同 TS-UNIQ：交易单据不属 D17 细则 1 的主数据、参考、配置 |

### 查询路径（O10）
无变化，两条 CHECK 仅在写入时对单行求值，无查询路径、无新索引。预算类别：单行写入常数开销。

### 横切口径、法人与期间（O14、O13）
- 时区（CC-T01）：`submitted_date` 取注入 `Clock` 经 `BusinessZone`（`Asia/Shanghai`）的业务日，`work_date` 为自然日，同 TS-UNIQ；本 PR 不改。
- 并发（CC-C02）：乐观锁 `version`、更新语句 `status = :previous` 条件不变；CHECK 对每次保存的最终行求值，聚合在内存里已同时改 `round` 与 `submittedOn`，同一条 UPDATE 写入，不会出现中间态违约。
- 保留期：见 A3；金额、舍入、分页：不涉及。
- 法人与期间（O13）：`legal_entity_id NOT NULL`，由 `EmployeeLegalEntities.on(employee, workDate)` 解析（`SubmitTimesheet.java:84`），缺失即失败；本 PR 只纠正其列注释（T-8），不改行为。

### 适用的 ADR 条款对照（O15）
| ADR 条款 | 规定的列 / 键 / 约束 | 预期 diff 位置或「不适用（理由）」 |
|---|---|---|
| ADR-0038 | 开放期改基线、附预期 diff、不追加迁移 | 已含：第 3.2、7.2 节 |
| ADR-0033 第 1 项第 5 条 | `numbering_rule_version_id` 列 | 已含于 TS-UNIQ；本 PR 用它判别迁入单，不改列 |
| ADR-0036 | 迁入单沿用原编号，状态直接迁入，不经审批，不加 `legacy_id` | 已含：第二条 CHECK 对迁入单形态为真；不加 `legacy_id` |
| ADR-0033 第 1 项第 1、2 条 | uuid 主键 | 不适用（理由：不改主键） |
| ADR-0043 第 4 项第 2 条 | 代理键 `employee_id` 属个人信息 | 不适用（理由：本 PR 不新增列，不改访问级别与标记） |

### 值对象、`detail` / 自由文本 / `jsonb`（O16、C4）
无新增值对象、自由文本、`jsonb`。`round_number` 对应 Java `int`/`RoundNumber`（非负），提交日对应 `LocalDate`，类型与列一致。

### 注册表条目草稿（C2、C3、C5）
不变；`retention` 的注释归 A3。本 PR 不改注册表。

### 分级表（C4）
本 PR 不新增列；`round_number`、`submitted_date` 非个人信息直接标识，维持未登记默认 `INTERNAL`；工时数据整体是否按 `PI` 登记仍是 `DATA-REPAIR-timesheet-audit` 第 5 节待用户决定第 2 条，不在本卡。

### 表注释与列注释草稿（C1）
表注释不改。三列新注释（完整文本，遵守 terms.yaml 用语）：
- `round_number`：`轮次：已提交审批的次数，与审批请求的轮次一致；0 表示从未提交，此时提交日为空，二者同零同空；迁入单为 0；撤销审批回到草稿时保留轮次`
- `submitted_date`：`提交日：员工提交审批的业务日期（Asia/Shanghai）；重新提交时更新；与工作日期是两件事；空表示从未提交，此时轮次为 0，二者同零同空；迁入单为空；撤销审批回到草稿时保留提交日`
- `legal_entity_id`：`法人主体（法人 ID）：填报人在工作日期当天所属的法人，提交时按员工与工作日期解析，缺失即失败，不设默认；创建后不改`（「创建后不改」待核 5）

### 分析事实（A1–A8）
无分析事实。A6：法人（`legal_entity_id`）、业务日期（`work_date`）、单据编号（`timesheet_number`）、规则版本 ID（TS-UNIQ）均已在表上，迁入单可经 `legacy_numbers` 追到原单号，本 PR 无变化。

### 基线 / 目标 / 过渡 / 回滚（T1–T4）
- T1 基线：`cost_timesheet.timesheets`，迁移 `V20261014140000__cost_timesheet_baseline.sql`；改动前表内 CHECK 只有三条非负约束加状态枚举；轮次与提交日成对只在聚合里；`legal_entity_id` 注释写「传入」与实现不符。
- T2 目标：BC `cost.timesheet`，Phase 1a 修复批。
- T3 过渡（基线开放期）：对在途开发库的影响——须按 `dev-db-rebuild.md` 重建；开发库中若有手工造的违约行（轮次与提交日不同零同空）会令重建后的写入失败，重建前清空；ECS 上 dev 库不在本卡范围，随 one-ops 下次部署重建。迁入过渡不在本卡（E3），**新 CHECK 对迁入单形态安全**（迁入单：状态直接迁入，轮次 0，提交日空，版本 ID 空，两条 CHECK 为真）；`legacy_numbers` 不受影响。
- T4 回滚：随库重建；基线关闭后不适用。

### 专项（E1–E3）
- E1 事件载荷：无变化（不改字段）。E2 读模型：无。
- E3 迁入映射：不在本卡，也不预设。本 PR 只保证：迁入单（状态直接迁入、轮次 0、提交日空、版本 ID 空）满足两条 CHECK；若 E3 将来决定迁入单带轮次或提交日，则需成对给值（第一条要求），否则被拒，那时再议（决定点 6）。

### 预期表结构 diff
见第 7.2 节。

---

## A3 数据设计：TA-03 + CE-1　注册表与 `consumed_events` 注释

### 表 → 出处（B1、B2）
| 表 | 出处 | 聚合 | 注册表 role |
|---|---|---|---|
| `cost_timesheet.timesheets`（仅 retention 行注释） | 同 A2 | `Timesheet` | `BUSINESS_DOCUMENT`（不变） |
| `cost_timesheet.consumed_events` | 技术表：服务于 `Timesheet` 聚合的审批结果回写用例（`ApplyApprovalResult`）的事件幂等记录 | 无（技术表） | `IDEMPOTENCY`（不变） |

### 状态、关系、基数落点（B3、B3a、B4）
无变化：不改任何列、状态、关系。

### 不变量 → 约束清单（B5、O4）
| 不变量（来源） | 承担者 | 无法落约束时的理由 |
|---|---|---|
| 同一消费方对同一事件至多处理一次 | `uk_consumed_events_event_id_consumer_name`（已有，不动）| — |
| 已处理记录只在保留期满后由清理作业删除，其余不改写 | 应用层：`JdbcConsumedEvents` 只插入、`ConsumedEventsPurgeJob` 只删除（`unversioned-table-writes.txt:21、26`）| 无库内禁止 UPDATE 的触发器；该表非审计表，保留期 90 天；本卡不加触发器（CS-1.8）|
| 工时单保留期 10 年、归档后在线删除（`RETENTION_PROPOSAL.md`）| **本卡只登记意图（`#` 注释）**，生效改动与作业另卡 | 归档作业尚不存在；登记 `10y` 而无作业会令 `DataArchitectureTest` 的 D4 项失败（`retention` 的时限策略须点名作业）|

### 用例 → 聚合 → 表（O1）
不变。`ApplyApprovalResult` 写 `timesheets` 与 `consumed_events`（已登记例外，同 TS-UNIQ）。

### 业务键、数据类别与时态（O3、O6）
| 表 | 业务键 | 类别 | 版本化方式或不需要的理由 |
|---|---|---|---|
| `timesheets` | 同 A2 | 交易（单据）| 同 A2 |
| `consumed_events` | `(event_id, consumer_name)`（已有唯一约束）| 技术（幂等记录）| 不需要：技术表，无业务版本 |

### 查询路径（O10）
无变化。CE-2（`consumed_at` 索引）**关闭为不做**（one-data 裁定）：清理作业 `DELETE … WHERE consumed_at < :cutoff` 每次扫全表；约每月 5 千张工时单，90 天内 1.5 至 3 万行，全表扫描可忽略；若将来行数增长到影响清理，另卡再加。预算类别：后台作业，非用户路径。

### 横切口径、法人与期间（O14、O13）
- 保留期（CC-P02）：`timesheets` 目标 10 年（`RETENTION_PROPOSAL.md` 第 28 行，用户「按 one-data 建议」认可），现登记 `BUSINESS_RECORD`；`consumed_events` 90 天（不变，作业 `cost.timesheet.consumed.events.purge`）。含个人信息关联数据（员工 ID）的 `timesheets` 保留期不得无期限，用户决定出处即 `RETENTION_PROPOSAL.md`（ADR-0043 R-2），故 `BUSINESS_RECORD` 现状有出处，不判不通过。
- 其余（时区、幂等、金额、分页、法人与期间）：不涉及。

### 适用的 ADR 条款对照（O15）
| ADR 条款 | 规定的列 / 键 / 约束 | 预期 diff 位置或「不适用（理由）」 |
|---|---|---|
| ADR-0038 | 开放期改基线、附预期 diff | 已含：第 7.3 节（唯一差异为 `consumed_events` 表注释）|
| ADR-0043 R-2 | 含个人信息的表保留期不得无期限，除非用户决定 | 已含：`RETENTION_PROPOSAL.md` 为用户决定出处；`10y` 登记随作业建成 |
| ADR-0033 | 主键、去重键 | 不适用（理由：不改键与约束）|

### 值对象、`detail` / 自由文本 / `jsonb`（O16、C4）
无新增值对象、自由文本、`jsonb`、列。

### 注册表条目草稿（C2、C3、C5）
| 项 | 内容 |
|---|---|
| `cost_timesheet.timesheets` retention | 保持 `{policy: BUSINESS_RECORD}`，行尾加注释（草稿）：`# owner: one-1a-par；口径：保留 10 年，归档到对象存储后在线删除（docs/modernization/data/RETENTION_PROPOSAL.md，用户已认可；财务若认定为会计档案原始凭证改 30 年，待财务确认）；到期锚：工时迁入入口前，归档作业须先建成；建成时 retention 改为 {policy: 10y, job: cost.timesheet.timesheet.archive}`。**不加 `gaps`**（门禁 §1.4）|
| `cost_timesheet.consumed_events` mutability | `APPEND_ONLY` 改 `INSERT_DELETE`；其余不变（`role: IDEMPOTENCY`，`retention: {policy: 90d, job: cost.timesheet.consumed.events.purge}`，`lineage: {source: NEW}`，`writers: [cost.timesheet]`）|
| 其余各项 | 不变 |

### 分级表（C4）
本 PR 不新增列，无新增登记。

### 表注释与列注释草稿（C1）
- `cost_timesheet.consumed_events` 表注释（A3 唯一的 `pg_dump` 差异，措辞待 one-data 确认，待核 3）：`已处理的事件：幂等记录；粒度：每个消费方每个事件标识一行，只插入、不改写，由保留期清理作业删除过期行（保留 90 天）；消费方按事件标识去重，事件至少投递一次`
- `timesheets` 的表注释、列注释：A3 不改（归 A2）。

### 分析事实（A1–A8）
无分析事实。

### 基线 / 目标 / 过渡 / 回滚（T1–T4）
- T1 基线：注册表 `cost_timesheet.consumed_events` 登记 `APPEND_ONLY` 而清理作业会 DELETE；`timesheets` 保留期 `BUSINESS_RECORD`、无作业、无登记说明。
- T2 目标：BC `cost.timesheet`，Phase 1a 修复批；归档作业属另一张功能补做卡，Phase 2 工时规格前定（含财务认定）。
- T3 过渡（基线开放期）：表注释改动随基线，须按 `dev-db-rebuild.md` 重建开发库才生效；无数据影响；`legacy_numbers` 不受影响。到期锚「工时迁入入口前，归档作业须先建成」意味着工时迁入前这是硬前置，由 one-1a-par 在迁入立项时检查。
- T4 回滚：随库重建；登记与注释文件可直接回退。

### 专项（E1–E3）
无：不涉事件载荷、读模型、迁入映射。

### 预期表结构 diff
见第 7.3 节。
