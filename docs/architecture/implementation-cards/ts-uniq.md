---
卡号: TS-UNIQ
状态: 完成
前置: NUM-VERSION-ID
---
# 派工卡 TS-UNIQ：工时单「每人每个工作日一张」唯一约束、驳回后改原单再提交、唯一冲突的错误码、状态与事件对齐、记所用编号规则版本

- 草稿：one-1a-par 起草助手，2026-10-16，基线 origin/main bd73edd2。数据设计已由 one-data 签发（见「数据设计」一节首行）；测试代理与实现代理开工前仍须核对签发有效（`DATA_DESIGN_GATE.md` §1.2、§1.3）。
- 「待核」表示手头证据不足，开工前由实现者或 one-data 先核实再改，见第 13 节。

## 1. 卡号与类别
- 卡号：TS-UNIQ（对应 `data/review-parts/` 的 T-1、T-2、X-6，及 T-6 中 `ix_timesheets_employee_id` 一项；对应 `data/repair-cards/DATA-REPAIR-timesheet-audit.md` 第 6 节的三行归属）
- 类别：缺陷修复（ 第 9 条）。一张卡，一个小 PR 序列：先表结构 PR（见第 12 节），后实现 PR；各自直接合入 main（§6.1 第 10 条）
- 批次：已建表数据设计审查修复批；顺序上排在 `DATA-REPAIR-timesheet-audit` A 部分（TA-01、TA-02）之前
- 不含（已有归属，本卡不做）：
  - 工时填报义务（哪天须填、最低时数、请假是否入单、非工作日加班是否关联审批或调休）：交 one-rules 取证后出决策页，定之前不实现任何最低时数拦截（用户 2026-10-16，intent `2026-10-16-052923-one-coordinator-工时单唯一与填报义务-我的想法是.md`）
  - `DATA-REPAIR-timesheet-audit` 的 TA-01（状态与轮次、提交日成对 CHECK）、TA-02：排在本卡合并之后。**TA-01 依赖本卡定稿的状态集合**：集合不变，仍为 `DRAFT / SUBMITTED / RETURNED / APPROVED / REJECTED` 五个，TA-01 据此写「提交过」的集合，不另加状态
  - T-4（`org_unit_id` 取创建时部门还是工作日所在部门）、T-5（表粒度与信息图 OBJ-66 的差异，Phase 2）、T-7（保留期登记与归档作业归 `DATA-REPAIR-timesheet-audit` 新增的 TA-03，A 部分，one-1a-par，不在本卡，见第 13 节第 5 条）、T-8 中 `legal_entity_id` 注释：不动
  - 状态列内联 `CHECK` 的具名（`ck_timesheets_status`）：随 G-D12 / R-2a 一次改基线（`SCAN_SUPPLEMENT.md` §6），本卡不动该 `CHECK`，已由 one-data 裁定留给 G-D12（R-2a），见第 13 节第 3 条
  - 事件改名 `TimesheetCancelled` → `TimesheetApprovalWithdrawn`（B2-B07）：**已由 R-1b P5 完成并合入 main**（提交 `95ced398`），见第 2 节核对结果第 4 条；本卡只对齐、不重复改

## 2. 依据与出处核对

### 2.1 用户决定
- 「我的想法是一人一天一张单的权利，非工作日员工加班也需要填工时」；落点：每人每天最多一张，含非工作日，唯一约束对所有状态生效，驳回或撤回后改原单再提交，不设终态取消（intent `2026-10-16-052923-one-coordinator-工时单唯一与填报义务-我的想法是.md`）。
- 「一人一日一单、最低时数拦截」（用户 2026-10-15 Q15，intent `2026-10-15-233152-one-architecture-全企业业务对象确认页-11-组全.md`；信息图 OBJ-66）。
- 事件改名：GL 确认单 76 题全部按推荐，B2-B07 `TimesheetCancelled` → `TimesheetApprovalWithdrawn`，中文「工时单已撤回审批」（`docs/glossary/kind-judgements/GL_RENAME_CARDS.md`）。
- 审批被驳回、撤销后可原单再发起（用户 2026-10-16，intent `2026-10-16-085331-one-data-审批被驳回-被撤销后能不能再发起.md`，`data/repair-cards/README.md` 决定表第一行）：该决定的落点是 approval 侧的成对 CHECK 与状态机 `REJECTED → RUNNING`；工时单侧由 052923 的「驳回后改原单再提交」承接。

### 2.2 规则、ADR、规范
- ADR-0038（开放期直接改基线、不追加迁移）；ADR-0033 第 1 项第 5 条（使用方引用版本行的 UUIDv7，列名 `numbering_rule_version_id`）；ADR-0043 第 4 项第 2 条（`employee_id` 是系统代理键）；ADR-0037 第 7 项（生产取证）；ADR-0036（历史数据迁入）。
- DATA_DESIGN_CHECKLIST §14.1 D2、D7、D17 细则 6、D18；§12.2；§14「已登记的例外」第 1 项（`SubmitTimesheet` 同事务取号，`backend/architecture/multi-aggregate-exceptions.txt`）。
- NAMING_STANDARD §2.4（约束命名 `uk_<表>_<列>`）、§2.4a 第 1、2、3 条、G48（错误码 `{SUBDOMAIN}_{BC}_{REASON}`，中文名唯一）。
- DATA_DESIGN_GATE：O3、B3a、B5、O4、O10、C4、T1–T4、E3 及附录 C 缺陷 a、b。
- 复审意见：`data/review-parts/timesheet_attendance_audit_idem_sched.md` T-1、T-2、T-6；`data/review-parts/OPUS_VERIFY.md` 的「TS T-1」行与「建议新增规则」之外的 T-1 / T-2、X-6 两条；`data/review-parts/platform_approval_integration_numbering.md` X-6。

### 2.3 核对结果（逐条，基线 bd73edd2）

| # | 事项 | 核对结果 | 证据 |
|---|---|---|---|
| 1 | 基线缺（员工，工作日）唯一约束 | **属实** | 迁移 `cost_timesheet/V20261014140000__cost_timesheet_baseline.sql` 只有 `uk_timesheets_timesheet_number`；`ix_timesheets_employee_id (employee_id, work_date)` 非唯一；`SubmitTimesheet.java:67` 只按工时单 ID 加锁、查找，`:81-` 新建分支不查（员工，工作日） |
| 2 | `REJECTED` 无出边、不可编辑 | **属实** | `Timesheet.java:53-58` 的 `MACHINE` 对 `REJECTED` 无 `.allow`；`TimesheetStatus.java:11` `isEditable()` 只含 `DRAFT`、`RETURNED`；`revise` 在 `Timesheet.java:118` 据此拒绝 |
| 2a | `platform.approval` 接受被驳回单据的新一轮 | **已核实（代码与既有测试）** | `ApprovalRequest.java:63-67` 的状态机含 `REJECTED → RUNNING`、`CANCELLED → RUNNING`、`RETURNED → RUNNING`，`restart` 使 `round++`；`IdempotentStartIT` 的 `D-1a-78` 用例断言「已退回、已驳回、已撤销的审批都能起下一轮，已通过的不能」。**未核**：从工时单提交到审批真正起第二轮的端到端路径（经 `DocumentSubmittedForApproval` → `StartApprovalOnSubmission`）在 `REJECTED` 起点上无测试，由本卡验收测试第 4 条补 |
| 2b | 事件目录 `TimesheetRejected` 的「终态」措辞 | **目录里没有「终态」二字**；`events/timesheet.yaml:135` 的描述写「驳回即退回，目前只在流程以 REJECTED 结束时才会出现」，标题写「工时不成立」 | 实现者加一句「驳回后填报人可改原单再提交」，不删事件，不改载荷；标题「工时不成立」是否保留见第 13 节第 4 条（待 one-architecture） |
| 3 | `DuplicateKeyException` 一律当并发修改 | **属实**，位置 `JdbcTimesheetRepository.java:74`（任务描述写约 77 行，实为 74）；两个唯一约束（`pk_timesheets` 之外）加上新约束后须按约束名区分 | |
| 3a | 新建分支先查业务错误 | **属实，现状缺**：`OPUS_VERIFY.md` 第 64 行「新建分支要先查（员工，日期）给出业务错误，唯一冲突只作最后防线」 | 即第 3.2 节第 2、3 条 |
| 4 | 状态 CHECK、事件目录、状态机、枚举 | 五个取值一致，见数据设计一节的对照表；**改名已完成**：`TimesheetApprovalWithdrawn` 已在事件类、`events/timesheet.yaml:258`、`events/asyncapi.yaml`、`architecture/ddd/aggregates.yaml`、`audit/MUST_AUDIT_ACTIONS.yaml`、`backend/glossary/proposed/code-aggregates-events.yaml`（词条 `timesheet_approval_withdrawn`，中文「工时单已撤回审批」）；验收物 `acceptance/cost/timesheet/TimesheetApprovalWithdrawnRenameIT` 已在 main | `grep TimesheetCancelled` 在 main 上只剩历史文档与验收测试里的「旧名不得出现」断言（`P5Names.OLD_CANCELLED_EVENT`） |
| 4a | 撤回 = `SUBMITTED → DRAFT` | **属实** | `Timesheet.java:53-58` 的 `.allow(SUBMITTED, …, DRAFT)`；`cancelSubmission` 发 `TimesheetApprovalWithdrawn`；类注释写「a cancel gives it back as a draft」；`backend/glossary/proposed/README.md` 与 `code-aggregates-events.yaml` 的定义「填报人撤销审批，工时单回到草稿；不记为驳回」。注意代码里 `WithdrawTimesheet`（退回自己重改，审批轮次以 RETURNED 结束，工时单 → `RETURNED`）与 `CancelTimesheet`（撤销整个审批，工时单 → `DRAFT`，发 `TimesheetApprovalWithdrawn`）两个用例名与事件名的「撤回」含义对调，**只记不改**，见第 13 节第 6 条 |
| 5 | X-6 记 `numbering_rule_version_id` | **属实** | 全库 `db/migration` 除 `platform_numbering` 外无此列；`DocumentNumbers.next` 返回的 `IssuedNumber` 只含号码与计数格 ID（`platform/numbering/api/IssuedNumber.java`），**不含版本 ID**；`TemplateVersion` 无 ID 字段。故 X-6 需扩 `platform.numbering` 的对外类型，见第 3.2 节第 6 条与第 13 节第 1 条 |
| 6 | 「121,453 张、一人一日一单」口径 | **口径不明确，只能确认出处**：信息图 `ENTERPRISE_INFO_MAP_DRAFT.md:124`（OBJ-66）写「近 24 个月 121,453 张」，出处为另一外部系统的表与〔交证〕§13，不是老系统库的（人员，日期）去重计数；规则卡 `rules/raw/timesheet-lifecycle.md` RULE-04 说明老系统用「未删除」过滤唯一索引，**已取消的单据也占用该日**；RULE-08 说明工时行有类型 0 项目 / 1 售前 / 2 OH（及混合） | 第 3.4 节 |
| 7 | `data-classification.txt` 无工时条目 | **属实**；`employee_id` 属系统代理键（ADR-0043 第 4 项第 2 条）、`numbering_rule_version_id` 同属代理键引用，按 C4 不登记（未登记默认 `INTERNAL`），本卡不补登记；「工时数据整体是否按 `PI` 登记」是 `DATA-REPAIR-timesheet-audit` 第 5 节待用户决定第 2 条，本卡不替它定 | 数据设计一节分级表 |
| 8 | CC-T01 时区口径 | **矩阵该行不是「待决」**：`CROSS_CUTTING_MATRIX.md` CC-T01 类别为「地基 · 本批处理中（B2b fix-time-zone）」；业务时区 `Asia/Shanghai` 与 `BusinessZone.today(Clock)` 已在 `kernel`；`SubmitTimesheet.java` 的 `today` 取 `zone.today(clock)`。本卡按下述口径写，无需等该行 | 数据设计一节横切口径 |
| 9 | TA-01 依赖本卡 | **属实** | `DATA-REPAIR-timesheet-audit.md` §1「顺序」与 TA-01「改法」 |

## 3. 范围与卡上文件（越界先停下报告，不先做后报）

### 3.1 基线 DDL（改开放基线，不追加迁移，ADR-0038）
文件：`backend/src/main/resources/db/migration/cost_timesheet/V20261014140000__cost_timesheet_baseline.sql`，只限 `cost_timesheet.timesheets`，不碰 `consumed_events`。
1. 增 `CONSTRAINT uk_timesheets_employee_id_work_date UNIQUE (employee_id, work_date)`：全表唯一、无谓词、对所有状态生效（用户 intent `2026-10-16-052923`）；不用部分唯一索引。
2. 删 `CREATE INDEX ix_timesheets_employee_id`（与上一条的唯一约束前缀相同，冗余；G-DDL3）。
3. 增列 `numbering_rule_version_id uuid`（可空，不设默认值；X-6）。可空是因为迁入单据沿用原编号、未经本系统发号，该列为空；新建单必填由应用保证（见数据设计一节不变量表）。已由 one-data 裁定（2026-10-16，`data/reviews/TS-UNIQ.md`）。跨 BC 只存 ID，不建外键（B4）。
4. 表注释的粒度改为「每人每个工作日一行，含非工作日，所有状态」；`employee_id`、`work_date` 两列注释补写与唯一约束的关系；新列注释以「编号规则版本」开头（G39，词条 `numbering_rule_version`）。注释草稿见数据设计一节。
5. 其余一律不动：包括 `status` 内联 `CHECK`、`ix_timesheets_org_unit_id`、`ix_timesheets_status`、其余列与约束。

### 3.2 Java（实现 PR）
1. `backend/src/main/java/com/forcitis/one/cost/timesheet/domain/Timesheet.java`：
   - `MACHINE` 增 `.allow(TimesheetStatus.REJECTED, TimesheetStatus.SUBMITTED)`；`revise` 允许 `REJECTED`；类注释改为与新流转一致（驳回后可改原单再提交）。
   - 新增 `numberingRuleVersionId`（类型见第 6 条）：`draft` 必填，缺失即失败并告警，不写空值；`restore` 读出，迁入行为空；创建后不改。
   - 不改 `cancelSubmission` 与事件名。
2. `backend/src/main/java/com/forcitis/one/cost/timesheet/api/TimesheetStatus.java`：`isEditable()` 含 `REJECTED`；类注释同步。
3. `backend/src/main/java/com/forcitis/one/cost/timesheet/application/SubmitTimesheet.java`：新建分支在取号**之前**按（员工，工作日期）查已有工时单，已有（任何状态）即抛新错误码，不消耗单据编号；类注释「refused cannot be submitted at all」改为与新流转一致。仓储接口 `domain/TimesheetRepository.java` 增一个只读查询。
4. `backend/src/main/java/com/forcitis/one/cost/timesheet/infrastructure/persistence/JdbcTimesheetRepository.java`：
   - 插入语句增 `numbering_rule_version_id`。
   - `DuplicateKeyException` 按**约束名**区分（取自底层 `PSQLException` 的服务器错误信息中的约束名）：

     | 约束名 | 返回 |
     |---|---|
     | `uk_timesheets_employee_id_work_date` | 新错误码 `COST_TIMESHEET_WORK_DATE_DUPLICATE`（`ErrorKind.CONFLICT`） |
     | `uk_timesheets_timesheet_number` | `KERNEL_CONCURRENT_CHANGE`（保持现状） |
     | `pk_timesheets` | `KERNEL_CONCURRENT_CHANGE`（保持现状；同一工时单被同时创建；`lockForCreate` 按工时单 ID 加锁，正常到不了这里；待复审确认，见第 13 节第 7 条） |
     | 其他或取不到约束名 | 不吞：原样抛出并记一条 `ALERT` 日志，**日志不带冲突值**（D1-2 同口径），不得默认当并发修改 |
   - 更新语句路径（`updated != 1`）仍为并发修改，不动。
5. `backend/src/main/java/com/forcitis/one/cost/timesheet/api/TimesheetError.java`：增 `COST_TIMESHEET_WORK_DATE_DUPLICATE(ErrorKind.CONFLICT)`。
6. X-6 取得版本 ID（**前置：numbering 小卡，待 one-coordinator 指定负责会话；`platform.numbering` 的 API 扩展不由本卡带做，该小卡合并后本卡实现 PR 才开工，见第 13 节第 1 条**）。小卡的内容是：`platform/numbering/api/IssuedNumber.java` 增版本行 ID（新类型 `NumberingRuleVersionId`，放 `platform/numbering/api/`），`application/IssueDocumentNumber.java` 填入发号时 `inForceOn` 选中的那一版；`TemplateVersion` 带 ID 还是仓储按（类型，版本号）取 ID，由实现者在 `platform.numbering` 内选改动最小的一种，**先报告再动手**。测试里直接构造 `IssuedNumber` 的两处（`platform/approval/ApprovalIntegrationSupport.java`、`cost/timesheet/application/TimesheetOwnerGateTest.java`）只补参数，不改断言（以上随小卡走，不在本卡 PR）。本卡只消费小卡提供的 `IssuedNumber` 版本 ID。
7. 前端：`frontend/packages/core/src/error-messages.ts` 增 `COST_TIMESHEET_WORK_DATE_DUPLICATE` 一条（该表对 OpenAPI 的每个 `ErrorCode` 逐条要求，缺则类型检查失败）；`frontend/packages/api/openapi.json` 与 `src/generated/*` 为生成物，按生成脚本重新生成，不手改。
8. 文档与目录（与代码同 PR）：
   - `docs/modernization/events/timesheet.yaml`：`TimesheetRejected` 的描述补「驳回后填报人可改原单再提交」；改后运行 `build_asyncapi.py` 重新生成 `events/asyncapi.yaml`（`EventCatalogueTest` 校验两者一致）。
   - `docs/business/processes/timesheet-approval.md` 中两处状态流转（`REJECTED` 无出边）：由实现者核对后同步，只改与本卡流转相关的句子。
   - `backend/glossary/proposed/cost-timesheet.yaml`：新错误码的提议术语（已在起草本卡时登记，见第 11 节）。
9. 现有测试的夹具与断言（PR 单列，附理由）：
   - `cost/timesheet/domain/TimesheetStateMachineTest.java` 的 `everyOperationInEveryStatus`：`revise` 增 `REJECTED → REJECTED`、`submit` 增 `REJECTED → SUBMITTED`。理由：用户决定「驳回后改原单再提交」（intent `2026-10-16-052923`）。这是**业务规则变更引起的断言变更**，不是放宽。
   - 基线加唯一约束后，同一测试类内对同一员工、同一 `workDay()` 建多张工时单的夹具会撞约束（`cost/timesheet/application/TimesheetApprovalScenarioIT`、`acceptance/cost/timesheet/TimesheetOwnerAccountsIT`、`TimesheetApprovalWithdrawnRenameIT`、`acceptance/platform/eventing/EventEnvelopeAggregateIdIT`、`scenario/EventSequencePersistenceIT`，**待核**哪些真的撞）：只改夹具的员工或工作日期，使每张单（员工，工作日）互异，不改断言。
10. `backend/architecture/data-access-registry.yaml`：本卡不改条目（`business_key` 等字段在 DG-0 建成前写在第「数据设计」一节）；保留期登记与归档作业归 TA-03，不在本卡（第 13 节第 5 条）。

### 3.3 归属与不碰
- 归属：本卡独占 `cost_timesheet.timesheets` 的唯一约束、`numbering_rule_version_id` 列、状态集合与流转、新错误码。
- 不碰：验收测试目录（由测试代理写）；`consumed_events`；`platform_approval` 的任何文件（approval 侧已支持，只补测试不改代码）；他卡文件；`kernel` 共享构件。
- 不改 `platform.numbering`：其 API 扩展由 numbering 小卡负责（待 one-coordinator 指定会话，第 13 节第 1 条）；本卡实现 PR 在该小卡合并前不开工，需要该模块再改动时停下报告。

### 3.4 前置并行事项：迁入违约取证（T3 / E3，**不是本卡执行者要做的事**）
- 背景：信息图「一人一日一单、121,453 张」的口径不明，不能当作「老系统数据无重复」的证据；老系统工时行有类型 0 项目 / 1 售前 / 2 OH（RULE-08），若按类型分单，同一人同一日可有多条；老账号合并到同一员工后也会撞（员工，工作日期）。
- 须经 **one-ops** 按 ADR-0037 对老系统生产库取**全量、全状态**下（人员，日期）重复组数与分布（只出计数与聚合，明细与个人信息不进会话与仓库），并登记一行（`phase1a/infra/new_legacy_read.py`）。
- 结果不为 0 时的处理办法（取舍规则、合并、分属不同日期等）由用户或 one-rules 定；**不得为通过约束静默丢行、静默合并或改日期**（CLAUDE.md 第 6 条）。
- 与本卡的关系：不阻塞表结构 PR（基线开放期，无生产数据）；但在迁入阶段入口之前必须有结果。本卡合并不以它为条件，卡里不实现任何迁入代码。

## 4. 验收测试（由测试代理先写，§6.1 第 8 条）
- 测试代理：`code-modernization:test-engineer`（**sonnet**），先于实现提交，提交说明带 `Acceptance-Test-Author: test-engineer`；**在 one-data 签发且表结构 PR 合并之后才开工**
- 路径：`backend/src/test/java/com/forcitis/one/acceptance/cost/timesheet/`（新类：`TimesheetEmployeeDayUniqueIT`、`TimesheetRejectedResubmitIT`、`TimesheetStatusConsistencyTest`、`TimesheetNumberingRuleVersionIT`）；前端 `frontend/packages/core/src/` 下既有的错误码文案类型检查原样通过，不另写
- 测试名（`@DisplayName` 以 `TS-UNIQ` 开头）：
  1. `TS-UNIQ 同一员工同一工作日并发创建两张工时单，后到者被拒，返回 COST_TIMESHEET_WORK_DATE_DUPLICATE 而不是 KERNEL_CONCURRENT_CHANGE，库内只有一行，也没有多发一个单据编号`
  2. `TS-UNIQ 已有草稿、已提交、已退回、已通过、已驳回任一状态的工时单时，同一员工同一工作日新建被预检拒绝，返回 COST_TIMESHEET_WORK_DATE_DUPLICATE，且不消耗单据编号`
  3. `TS-UNIQ 非工作日（周末）与工作日一样，每人每天只能有一张工时单`
  4. `TS-UNIQ 工时单被驳回后，填报人可修改原单并再次提交：状态 REJECTED→SUBMITTED，仍是同一张单，轮次加一，审批发起新一轮；已通过的工时单不能再提交`（覆盖 `platform.approval` 在 `REJECTED` 起点上的第二轮端到端）
  5. `TS-UNIQ REJECTED 与 DRAFT、RETURNED 一样可编辑，APPROVED 与 SUBMITTED 不可编辑`
  6. `TS-UNIQ 撤销审批使 SUBMITTED→DRAFT，发布的事件类型为 cost.timesheet.TimesheetApprovalWithdrawn，旧名不存在`（已有 `TimesheetApprovalWithdrawnRenameIT` 覆盖事件名；本条另断言状态转换，二者都须通过）
  7. `TS-UNIQ 表结构守护：timesheets 上存在唯一约束 uk_timesheets_employee_id_work_date，列为（employee_id, work_date），无谓词；ix_timesheets_employee_id 不存在；uk_timesheets_timesheet_number 仍在；numbering_rule_version_id 列存在、类型 uuid、可空`（读 `pg_constraint`、`pg_index`）
  8. `TS-UNIQ timesheet_number 冲突仍映射为 KERNEL_CONCURRENT_CHANGE`（直接用已有单据编号插入另一员工的单）
  9. `TS-UNIQ 其他约束名的重复键异常不被当作并发修改：原样抛出，且日志不含冲突值`（注入一个未知约束名）
  10. `TS-UNIQ 状态 CHECK 取值 = TimesheetStatus 枚举 = Timesheet.MACHINE 的状态集合 = 事件目录各转换事件的目标状态集合，每个取值都有进入它的转换（初始状态除外）`（B3a 三方一致，写一次，不依赖 G-D12 建成）
  11. `TS-UNIQ 新建的工时单记录发号时所用的编号规则版本 ID，且等于发号时生效的已确认版本；新建路径取不到版本 ID 时失败并告警，不写空值（库内该列可空，为迁入行保留）`
  12. `TS-UNIQ 工作日期按自然日保存不做时区转换；「不晚于今天」按上海业务日判断（固定 Clock 在 UTC 前一日 16:30 时，上海次日的工作日期被接受）`
  13. `TS-UNIQ TimesheetStateMachineTest 原样重跑`（含第 3.2 节第 9 条所列的有理由的断言扩充，PR 单列）
- 实现方认为验收测试有误：停下报告，由复审裁定（`Acceptance-Ruling: RULING-TS-UNIQ-n`），不自行修改

## 5. 执行者与模型
- 测试代理：`code-modernization:test-engineer`，**sonnet**
- 实现：子代理，**sonnet**
- 首轮复审：**opus 一次**，推理强度 high（涉数据完整性与错误映射；§14、§14.1 全部条目 + 本卡专查项）；修后复核 **sonnet**；最多两轮，之后新发现的非 High 列为残余项在 PR 单列（ 第 6 条）
- 数据设计门禁（`DATA_DESIGN_GATE.md` §1.2）：本卡发 one-data 审查 → **签发（已于 2026-10-16 完成）** → 表结构 PR（预期 diff，one-data 复核，合并）→ 验收测试 → 实现。验收与实现开工前核对签发有效（§1.3）
- 工作区：独立 worktree，分支 `claude/one-1a-par-ts-uniq`（表结构 PR）、`claude/one-1a-par-ts-uniq-impl`（实现 PR，名字以负责会话为准），基线为当时的 main
- 不交 Codex：含数据完整性判断与跨模块 API 扩展

## 6. 禁止事项
- 不改卡外文件；不顺手重构、改名或统一风格；保留的约束、索引名字一律不改
- 不用部分唯一索引；不设「已取消」终态；不加 `CANCELLED` 之类状态；不改状态列 `CHECK`
- 不放宽、不删除断言；不改验收测试目录；第 3.2 节第 9 条所列的断言扩充与夹具调整须在报告与 PR 中单列并写理由
- 不实现最低时数拦截、不实现填报义务；不实现任何迁入代码或迁入映射
- 不为通过测试而改日期、合并或丢弃行；缺失或无效输入失败并告警，不静默跳过
- 日志不带冲突值、不带员工标识之外的个人信息；不打印、不写入密钥；测试密钥运行时生成
- 不追加迁移文件（ADR-0038）；不改 `kernel` 共享构件
- Testcontainers 临时容器（镜像限 `backend/architecture/test-images.txt`）可直接跑；其他容器、常驻容器、对外调用、生产取证先问；不建账号
- 用语遵守 `terms.yaml`；不用「PSA」指代系统；老系统事实（类型 0 / 1 / 2 等）只作证据，不进目标表设计

## 7. 预期表结构 diff（）
- 方法：两个空库分别执行改动前（main）、后的基线，`pg_dump --schema-only` 对比，种子另做 `--data-only` 对比
- 预期差异**只能是**（全部在 `cost_timesheet.timesheets`）：
  - 新增列 `numbering_rule_version_id uuid`（可空；位置在 `timesheet_number` 之后；无默认值），及其列注释
  - 新增约束 `uk_timesheets_employee_id_work_date UNIQUE (employee_id, work_date)`
  - 删除索引 `ix_timesheets_employee_id`
  - 表注释改（粒度）；列注释改：`employee_id`、`work_date`
- **不得出现**的差异：`timesheets` 其他列、类型、可空性、默认值的变化；`status` `CHECK` 取值或名称的变化；`ix_timesheets_org_unit_id`、`ix_timesheets_status`、`pk_timesheets`、`uk_timesheets_timesheet_number`、`ck_timesheets_*` 的任何变化；`cost_timesheet.consumed_events` 的任何变化；任何部分唯一索引；新列带非空约束或默认值；任何外键（含到 `platform_numbering` 的）；其他 schema 的任何变化；种子数据变化

## 8. 交付物与自查
- 交付物：基线 DDL；Java（状态机、可编辑、预检、约束名映射、新错误码、版本 ID 的写入；取得由 numbering 小卡提供）；前端文案一条与生成物；事件目录与流程文档的相应句子；验收与单元测试；提议术语一条
- 守护文件：`data-access-registry.yaml`、`data-classification.txt` 本卡不改；`ddl-naming-baseline.txt`、`primary-key-exemptions.txt`、各 `*-exceptions.txt` 不新增条目
- 自查：`scripts/precheck.sh origin/main` 全部 PASS 或有理由的 SKIP，输出贴进报告与 PR「precheck.sh 输出」；`pre-commit install`；报告前 gitleaks 8.30.1 扫全历史
- 受影响模块测试：precheck 给出的命令；交付前全量 `scripts/ci/backend_verify.sh` 与前端 `pnpm verify`（起容器的只用白名单镜像）
- PR 正文单列：预期与实际表结构 diff；签发行（`数据设计：通过（one-data，…）`，DG-1）；改动的断言与夹具及理由；numbering 小卡的卡号与合并确认
- 报告分开写「已验证」与「未运行」

## 9. 复审要求（§6.1 第 4、11 条）
- Blocker / High：opus 复核，合并前修完；Medium / Low：执行者补测试、CI 通过即关闭，负责会话抽查；不阻塞合并的登记进下一批
- 复审清单：DATA_DESIGN_CHECKLIST §14、§14.1（D2、D4、D7、D16、D17 细则 6、D18）；CODING_STANDARD CS-1.8、CS-1.9、CS-2.24（通用错误码只在 kernel，新码是模块专属）、CS-5.5、CS-5.6
- 专查：表结构 diff 只含第 7 节声明项；约束名映射不留「其他一律当并发修改」的兜底；预检在取号之前、冲突时不消耗编号；并发下两个同日新建只一个成功；`REJECTED` 重提后审批侧确为新一轮；版本 ID 取自发号时实际选中的版本而非事后按日期推断；被改的现有断言只有第 3.2 节第 9 条所列

## 10. 报告去向
- 完成、部分完成或阻塞，都向 one-1a-par，抄 one-coordinator（ 第 7 步）
- PR／卡／交接记录（消息按 TEAM-53 四节点及需动作事件送达，不另报号）：分支、改动文件、验证结果（已验证 / 未运行）、precheck 输出、预期与实际表结构 diff、未完成项、需要用户决定的事；合并后通知 `DATA-REPAIR-timesheet-audit` A 部分执行者（TA-01 可开工）与 one-data

## 11. 本卡新词（开工前术语门禁）
已查 `docs/glossary/terms.yaml` 与 `backend/glossary/proposed/*.yaml`：下列各词均无同名、同中文名条目。起草本卡时已把第一条登记为提议术语（`backend/glossary/proposed/cost-timesheet.yaml`，`status: proposed`），其余是已有词条的组合，无需新增。

| 标识符 | 中文名 | 种类 | 使用范围 | bc | 说明 |
|---|---|---|---|---|---|
| `COST_TIMESHEET_WORK_DATE_DUPLICATE`（词条 `error__cost_timesheet_work_date_duplicate`，en `CostTimesheetWorkDateDuplicate`，snake `cost_timesheet_work_date_duplicate`） | 工作日期已有工时单 | 错误码 | 领域 | `cost.timesheet` | 同一员工在该工作日期已有一张工时单（任何状态），不能再建（409）。中文名全局唯一已核（同类 `日期重复` 属 `organization.attendance`，含义不同）；命名遵循既有 `*_DUPLICATE_*` 与 `COST_TIMESHEET_WORK_DATE_*` 写法 |
| `NumberingRuleVersionId` | 编号规则版本 ID | 值对象（类型化 ID） | 领域 | `platform.numbering` | 由词 `numbering_rule_version` + `id` 组成；列名 `numbering_rule_version_id` 已是 ADR-0033 第 1 项第 5 条点名的列名，且 `platform_numbering.numbering_rule_versions` 主键同名。由 numbering 小卡引入，本卡消费。注意既有词条 `numbering_rule_version` 的定义是「版本号值对象」，与「版本行 ID」不同，G39 只要求列注释以词条中文名「编号规则版本」开头，已满足；是否需要把词条定义补一句交 one-glossary（用 `docs/glossary/new_term_issue.py` 登记，不自行裁定） |
| `uk_timesheets_employee_id_work_date` | — | 约束名 | — | `cost.timesheet` | 按 NAMING_STANDARD §2.4 `uk_<表>_<列>`，列取自 `employee_id`、`work_date` |

## 12. 顺序与前置
1. 本卡 → one-coordinator 看 → one-data 审查并签发（`数据设计：通过（one-data，日期，依据 <卡提交号>）`）；**已完成**，见数据设计一节首行
2. 并行：第 3.4 节取证（one-ops），不阻塞 3
3. 表结构 PR：基线 DDL + 注册表无改 + 守护全绿；one-data 复核预期 diff；合并。新增的是可空列，表结构 PR 不需带 Java、不需顺序豁免（one-data 裁定）。唯一约束对现有测试夹具的影响见第 3.2 节第 9 条的待核；若确有测试撞约束，停下报告 one-data，不自行在表结构 PR 里改 Java
4. 测试代理写验收（第 4 节），先于实现提交
5. 实现 PR（第 3.2 节），opus 首轮复审，sonnet 复核。**前置：numbering 小卡（`IssuedNumber` 带版本 ID），待 one-coordinator 指定负责会话**；它是实现 PR 的前置，不是表结构 PR 的前置
6. 合并后：通知 `DATA-REPAIR-timesheet-audit` A 部分（TA-01 依赖本卡定稿的状态集合，集合不变）；信息图 OBJ-66 与聚合草稿 INV-64 / N-10 已由 one-data 另发 one-architecture 改写，不阻塞（第 13 节第 4 条）

## 13. 待核事项与需要决定的点

### 待核（证据不足，开工前先核实）
1. **前置：numbering 小卡，待 one-coordinator 指定负责会话**：`platform.numbering` 的 API 扩展（`IssuedNumber` 增版本 ID，`TemplateVersion` 是否带 ID 的取舍）先开小卡做，是本卡实现 PR 的前置，不是表结构 PR 的前置；不再由本卡带做。
2. 迁入行的 `numbering_rule_version_id` 可空：**已由 one-data 裁定**（2026-10-16，`data/reviews/TS-UNIQ.md`）：列可空，空表示迁入单据，新建单必填由应用保证；表结构 PR 不需带 Java、不需顺序豁免。
3. 状态列内联 `CHECK` 具名：**已由 one-data 裁定**留给 G-D12（R-2a），本卡不改（`DATA-REPAIR-timesheet-audit.md` 第 10 节 §6 行与 `README.md` 的「随 TS-UNIQ 改」表述以此为准，由对应卡同步）。
4. 信息图 OBJ-66（状态列含「审批中」「已撤回」「已过账」、粒度「员工 × 填报期间」）与 `ENTERPRISE_AGGREGATES_DRAFT.md` INV-64 / N-10（部分唯一索引、`CANCELLED`）和本卡决定不一致：**已由 one-data 另发 one-architecture 改写**，B3 按「信息图过期」判通过，不阻塞本卡；本卡以用户 2026-10-16 决定为准（全表无谓词、无取消终态）。`TimesheetRejected` 标题「工时不成立」是否改，同交 one-architecture。
5. 保留期：用户已认可 `timesheets` 10 年、归档到对象存储后在线删除（`data/RETENTION_PROPOSAL.md`）；**注册表 `retention` 登记与归档 / 清理作业归 `DATA-REPAIR-timesheet-audit` 新增的 TA-03（A 部分，one-1a-par），不在本卡，C3 对本卡不适用**（one-data 裁定）。财务是否认定工时单为会计档案原始凭证（改 30 年）仍待财务确认，转到 TA-03，不在本卡。
6. `WithdrawTimesheet` / `CancelTimesheet` 两个用例名与事件名里的「撤回」含义对调（`WithdrawTimesheet` 令审批以 `RETURNED` 结束，`CancelTimesheet` 令工时单回草稿并发 `TimesheetApprovalWithdrawn`）；本卡不改名（缺陷卡不得改名），登记给 one-glossary。
7. `pk_timesheets` 冲突映射为并发修改是否合适，交 opus 首轮复审。
8. 其余索引 `ix_timesheets_org_unit_id`、`ix_timesheets_status` 在代码里没有查询路径（T-6），本卡不删，留待列表路径出现或另卡。

### 需要用户或 one-architecture 决定的点（不预选，走待决定收件箱）
1. **迁入违约取证结果不为 0 时的处理办法**（用户或 one-rules；3.4 节）。
2. 表结构 PR 怎样保持 CI 绿：已由 one-data 裁定（新增可空列，不需带 Java、不需顺序豁免），不再是决定点。
3. `platform.numbering` 的 API 扩展：已定为先开 numbering 小卡，负责会话由 one-coordinator 指定（待核第 1 条），不再由本卡带做。
4. 迁入工时单的 `numbering_rule_version_id` 可空与否：已由 one-data 裁定为可空（待核第 2 条）。
5. `timesheets` 保留期登记与归档作业：已定归 TA-03（待核第 5 条）；30 年是否会计档案仍待财务，转到 TA-03。
6. 信息图 OBJ-66 与聚合草稿 INV-64 / N-10 的改写：已由 one-data 另发 one-architecture，不阻塞（待核第 4 条）。
7. **新错误码的中文名「工作日期已有工时单」与 HTTP 409**：交 one-glossary 确认词条；用户无需决定。

## 数据设计（DATA_DESIGN_GATE.md 附录 A；材料按条目号对应）

数据设计：通过（one-data，2026-10-16，依据 TS-UNIQ.md @40edf239，含上述裁定），记录 data/reviews/TS-UNIQ.md。

### 涉数据
是。§1.1 第 1 项（列、约束、索引、基线 `db/migration` 改动）；第 2 项事件载荷**无变化**（只对齐，不改字段；改名已由 P5 完成）；第 3、4、5 项无（迁入映射仅有前置取证，不在本卡；无指标；`data-classification.txt` 与注册表条目不改）。

### 表 → 出处（B1、B2）
| 表 | 出处 | 聚合 | 注册表 role |
|---|---|---|---|
| `cost_timesheet.timesheets` | 信息图 OBJ-66「工时单」的单据行（当前为 1a 切片：员工 × 工作日一行，粒度与 OBJ-66「员工 × 填报期间」的差异是 T-5，已登记 N-10 排 Phase 2，**不在本卡**）；用户 Q15「一人一日一单」 | `Timesheet`（`architecture/ddd/aggregates.yaml`，`cost.timesheet`） | `BUSINESS_DOCUMENT` |

schema 归属：`cost_timesheet`，BC `cost.timesheet`，子行无。

### 状态、关系、基数落点（B3、B3a、B4）
**状态 `CHECK` ↔ 事件目录 ↔ 状态机 ↔ Java 枚举（本卡后）**

| `CHECK` 取值 | `TimesheetStatus` | `MACHINE` 出边 | 进入它的转换 | 进入时发布的事件（`events/timesheet.yaml`） | 判定 |
|---|---|---|---|---|---|
| `DRAFT` | `DRAFT` | → `SUBMITTED` | 初始；`SUBMITTED → DRAFT`（撤销审批，`cancelSubmission`） | 撤销时 `TimesheetApprovalWithdrawn` | 一致 |
| `SUBMITTED` | `SUBMITTED` | → `APPROVED`、`REJECTED`、`RETURNED`、`DRAFT` | `DRAFT → SUBMITTED`、`RETURNED → SUBMITTED`、**`REJECTED → SUBMITTED`（本卡新增）** | `TimesheetSubmitted`（并发布 `platform.approval.DocumentSubmittedForApproval`，属审批目录） | 一致 |
| `RETURNED` | `RETURNED` | → `SUBMITTED` | `SUBMITTED → RETURNED`（审批人退回，或填报人撤回自己的一轮） | `TimesheetReturned` | 一致 |
| `APPROVED` | `APPROVED` | 无（终态，唯一终态） | `SUBMITTED → APPROVED` | `TimesheetApproved` | 一致 |
| `REJECTED` | `REJECTED` | → `SUBMITTED`（**本卡新增**，原为无出边） | `SUBMITTED → REJECTED` | `TimesheetRejected` | 一致；`isEditable` 含 `REJECTED` |

- 事件 5 个（`TimesheetSubmitted`、`TimesheetApproved`、`TimesheetRejected`、`TimesheetReturned`、`TimesheetApprovalWithdrawn`）与转换一一对应：①每个事件目标状态都在 `CHECK` 内；②`CHECK` 每个取值都有进入它的转换；③三份状态集合相同（验收第 10 条自动比对）；④改名在事件目录、事件类、`aggregates.yaml`、`MUST_AUDIT_ACTIONS.yaml`、提议术语同步（P5 已做），状态列注释不含事件名，无需改。
- 撤回 = `SUBMITTED → DRAFT`，**没有「撤回」状态**，`CHECK` 不改；`TimesheetApprovalWithdrawn` 载荷不变（文档引用、员工、填报工时、轮次、原因）。
- 与信息图 OBJ-66 关键状态的差异（已撤回、审批中、已过账）见第 13 节待核第 4 条，B3 的这几处按「信息图过期」判通过（one-data 裁定；信息图与聚合草稿已另发 one-architecture 改写），不阻塞本卡。
- 关系与基数（B4）：`employee_id`、`legal_entity_id`、`org_unit_id`、`project_manager_id` 均跨 BC，只存 ID、不建外键；新增 `numbering_rule_version_id` 同理（跨 BC `platform.numbering`），不建外键；新列 `numbering_rule_version_id` 可空（`0..1`，空表示迁入单据）；其余各列均为 `NOT NULL`（基数 1）；无 N↔N。

### 不变量 → 约束清单（B5、O4）
| 不变量（来源） | 承担者 | 无法落约束时的理由 |
|---|---|---|
| 每人每个工作日至多一张工时单，含非工作日，所有状态（用户 intent `2026-10-16-052923`；Q15；RULE-04） | **`uk_timesheets_employee_id_work_date`**（本卡新增）；应用预检给出业务错误 | — |
| 单据编号全局唯一（列注释；ADR-0010 可配置业务编号） | `uk_timesheets_timesheet_number`（已有） | — |
| 填报工时、轮次、事件序号非负 | `ck_timesheets_hours`、`ck_timesheets_round`、`ck_timesheets_event_count`（已有） | — |
| 状态取值 | 状态内联 `CHECK`（已有，不改） | — |
| 状态与轮次、提交日成对（提交过 ⇔ 轮次 ≥ 1 ⇔ 提交日非空） | 聚合 `submit()`；库内 CHECK 归 `DATA-REPAIR-timesheet-audit` TA-01 | 排在本卡之后，依赖本卡定稿的状态集合 |
| 状态转换合法（含驳回后可重提） | 聚合 `MACHINE`；更新语句带 `status = :previous` 与 `version` 条件 | 转换图无法用静态约束表达 |
| 工作日期创建后不改 | 聚合 `revise`（`COST_TIMESHEET_WORK_DATE_FIXED`） | 需触发器才能落库，当前无必要（CS-1.8） |
| 工作日期不晚于今天（上海业务日） | 聚合 `draft`（`COST_TIMESHEET_WORK_DATE_IN_FUTURE`） | 取决于时钟，不能落 CHECK |
| 只有填报人能改、提交、撤回、撤销 | 聚合 `requireOwner` 与用例的本人闸门 | 权限判定，非数据约束 |
| 已发编号可追溯到所用规则版本（ADR-0033 第 1 项第 5 条；D17 细则 6） | **新建路径由应用层保证必填**：`Timesheet.draft` 取不到版本 ID 即失败并告警；写入的值取自发号时实际选中的版本 | 库内该列可空（迁入单据沿用原编号、未经本系统发号，该列为空），故 DB 无法落非空约束，承担者只能是应用层；该版本属 `TIMESHEET` 规则、生效日不晚于发号业务日，跨 BC 无法库内约束，由验收第 11 条断言 |

### 用例 → 聚合 → 表（O1）
| 用例 | 聚合 | 表 |
|---|---|---|
| `SubmitTimesheet`（首次提交创建并提交；再次提交修改并提交） | `Timesheet` | `cost_timesheet.timesheets`；首次提交同事务向编号服务取号（已登记例外：DATA_DESIGN_CHECKLIST §14「已登记的例外」第 1 项，`multi-aggregate-exceptions.txt`），不新增例外 |
| `WithdrawTimesheet`、`CancelTimesheet` | 经 `platform.approval` | 只写审批侧，工时单由结果事件回写 |
| `ApplyApprovalResult`（`ApprovalCompleted` 的监听） | `Timesheet` | `timesheets`、`consumed_events` |
| `GetTimesheet` | — | 只读 `timesheets`（按 ID） |

### 业务键、数据类别与时态（O3、O6）
| 表 | 业务键 | 类别 | 版本化方式或不需要的理由 |
|---|---|---|---|
| `timesheets` | ① 业务上「每个员工在一个工作日至多一张工时单」：`(employee_id, work_date)`，出处用户 intent `2026-10-16-052923` 与 Q15；② `timesheet_number` 单据编号全局唯一，出处 ADR-0010 | 交易（单据） | 不需要：交易单据不属 D17 细则 1 的主数据、参考、配置；用到版本化配置（编号规则）的地方，记所用版本 ID（D17 细则 6，即 `numbering_rule_version_id`） |

### 查询路径（O10、O10a）
| 路径 | 过滤列 | 排序列 | 预期行数 | 分页 | 索引 |
|---|---|---|---|---|---|
| 按 ID 取单、加锁取单 | `id` | — | 1 | — | `pk_timesheets` |
| 新建前预检（员工，工作日期）是否已有 | `employee_id`、`work_date` | — | 0 或 1 | — | `uk_timesheets_employee_id_work_date` |
| 列表 | 无（现无列表路径；`ix_timesheets_org_unit_id`、`ix_timesheets_status` 暂无对应查询，T-6，本卡不动） | — | — | — | — |

预算类别：单行主键或唯一键读写，毫秒级，无列表与汇总，不涉 O10a 的列表预算。语句数不随行数增长。

### 横切口径、法人与期间（O14、O13）
- **时区（CC-T01）**：`work_date` 是员工填写的自然日（`date`），**不做时区转换**；「不晚于今天」用注入 `Clock` 经 `BusinessZone`（`Asia/Shanghai`）取上海业务日；`submitted_date` 同口径；时刻列 `timestamptz`。矩阵 CC-T01 现状为「地基 · 本批处理中」，非待决，不阻塞本卡（O14 通过，若 B2b 改了 `BusinessZone` 的接口，实现时跟随）。
- **幂等（CC-C01）**：同一工时单 ID 的重复提交在 `SubmitTimesheet` 内按 ID 加锁并返回已有结果；不同 ID 的同人同日重复由新约束拒绝。
- **并发（CC-C02、CC-C04）**：乐观锁 `version` 与更新语句 `status = :previous` 条件不变；新建的并发由唯一约束兜底。
- **保留期（CC-P02）**：登记与归档作业归 TA-03，不在本卡，C3 对本卡不适用（第 13 节待核第 5 条）。
- **金额、舍入、分页**：本卡不涉及；工时为 `numeric(10,2)` 的 `Hours`（CC-N09 属 Phase 2，不动）。
- **法人与期间（O13）**：`legal_entity_id NOT NULL`，由提交人在工作日期所属的法人解析（`EmployeeLegalEntities.on(employee, workDate)`），缺失即失败，不设默认；会计期间闸门属 Phase 2（CC-T05），本卡不带期间列。

### 适用的 ADR 条款对照（O15）
| ADR 条款 | 规定的列 / 键 / 约束 | 预期 diff 位置或「不适用（理由）」 |
|---|---|---|
| ADR-0033 第 1 项第 1、2 条 | 单列 uuid 主键，应用侧生成 | 已含：`pk_timesheets (id uuid)`，不变 |
| ADR-0033 第 1 项第 5 条 | 引用版本行的 UUIDv7 主键，列名 `numbering_rule_version_id` | **已含**：新增列 `numbering_rule_version_id uuid`（可空，迁入行为空） |
| ADR-0033 第 1 项第 10、11 条 | 排序不依赖 ID 次序；类型化 ID 包 `UUID` | 已含：本卡无排序；`NumberingRuleVersionId` 包 `UUID` |
| ADR-0033 第 7 项（事件载荷与去重键） | 事件载荷用结构化 ID；去重键拆列 | 不适用（理由：本卡不改载荷；`timesheets` 无去重键） |
| ADR-0036 / ADR-0033 `legacy_numbers` | 迁入数据不在业务表加 `legacy_id`；原单号进 `legacy_numbers` | 已含：不加 `legacy_id`；注册表 lineage 已写 `via: legacy_numbers` |
| ADR-0038 | 开放期改基线、附预期 diff、不追加迁移 | 已含：第 3.1、7 节 |
| ADR-0043 第 4 项第 2 条 | 代理键 `employee_id` 可出现在事件与日志，仍是个人信息 | 已含：本卡不新增含直接标识的列、不新增日志里的个人信息 |
| ADR-0026 第 9 条 | 业务事实的部门取事实发生时的部门 | 不适用（理由：T-4 单列待用户决定，本卡不动 `org_unit_id`） |

### 值对象、`detail` / 自由文本 / `jsonb`（O16、C4）
- 值对象：`Hours`（`numeric(10,2)`，非负，两位小数，已有）、`DocumentNumber`、类型化 ID（`TimesheetId`、`EmployeeId`、`LegalEntityId`、`OrgUnitId`，已有）；新增 `NumberingRuleVersionId`（`UUID`，构造处非空校验；聚合上迁入行以无值表示，不构造空 ID）。Java 侧无 `double`、`float`。
- 本表无 `detail`、自由文本、`jsonb` 列；事件 `reason` 字段在事件目录，载荷不变。

### 注册表条目草稿（C2、C3、C5）
| 项 | 内容 |
|---|---|
| role / mutability | `BUSINESS_DOCUMENT` / `MUTABLE`（不变） |
| business_key（DG-0 前写在卡里） | `(employee_id, work_date)`；`timesheet_number` |
| business_date_column / document_number_column | `work_date` / `timesheet_number`（不变） |
| retention | 现 `BUSINESS_RECORD`；用户已认可 10 年、归档到对象存储后在线删除（`data/RETENTION_PROPOSAL.md`）；**注册表登记与归档作业归 TA-03**（A 部分，one-1a-par；待核第 5 条），本卡不改注册表 |
| lineage | `{source: NEW, migrated_from: [old timesheet rows], via: legacy_numbers}`（不变） |
| writers / readers | `[cost.timesheet]`；无其他 BC 读本表，`platform.audit` 只消费事件；依赖 `cost.timesheet → platform.numbering`、`→ platform.approval` 已在 `module-dependencies.txt` |

### 分级表（C4）
| 列 | 访问级别 | 个人信息标记 | 理由 |
|---|---|---|---|
| `employee_id` | `INTERNAL`（未登记默认） | 不登记 | 用途：系统代理键（ADR-0043 第 4 项第 2 条）；C4 规定代理键列不登记。工时数据整体是否按 `PI` 登记，是 `DATA-REPAIR-timesheet-audit` 第 5 节待用户决定第 2 条，本卡不替它定 |
| `numbering_rule_version_id` | `INTERNAL`（未登记默认） | 不登记 | 用途：代理键引用，不含个人信息 |
| 其他列 | 不变 | 不变 | — |

### 表注释与列注释草稿（C1）
- 表：`工时单：聚合根；粒度：每人每个工作日一行，含非工作日，所有状态的工时单都占用该日；状态机在聚合内，审批结果经审批完成事件回写`
- `employee_id`：`员工（员工 ID）：填报人，只有填报人能提交、撤回、撤销；与工作日期合起来唯一`
- `work_date`：`工作日期：员工填写的、工时发生的那一天，自然日，不做时区转换，非工作日也可填报；与员工合起来唯一；创建时校验不得晚于今天（按上海业务日，取决于时钟，只在应用里保证），之后不再改写；事件的业务日期取它`
- `numbering_rule_version_id`：`编号规则版本 ID：创建时发号所用的编号规则版本（platform.numbering 版本行的 UUIDv7），随取号写入，之后不改；为空表示迁入单据，原编号作展示号，未经本系统发号；新建单由应用保证必填；跨模块只存 ID，不建外键`

### 分析事实（A1–A8）
无分析事实：本卡不新增事件载荷字段、读模型、指标。（现有事件 `TimesheetSubmitted` 等将被分析层订阅，粒度与口径在 Phase 2 工时规格中定，N-10。）A6 对本表判：法人（`legal_entity_id`）、业务日期（`work_date`）、单据编号（`timesheet_number`）已有；用到可配置规则（编号规则）的记规则版本 ID，**本卡新增**；迁入行可经 `legacy_numbers` 追到原单号（lineage 已登记）。

### 基线 / 目标 / 过渡 / 回滚（T1–T4）
- T1 基线：`cost_timesheet.timesheets` 已存在（注册表条目 `cost_timesheet.timesheets`），迁移 `V20261014140000__cost_timesheet_baseline.sql`；改动前现状：仅 `uk_timesheets_timesheet_number`，无（员工，工作日期）唯一，无版本 ID 列，`REJECTED` 无出边。老系统的表、列、类型码只出现在第 2.3、3.4 节作证据，不进目标表设计。
- T2 目标：BC `cost.timesheet`，阶段 Phase 1a 的修复批；其他 BC 不读本表。
- T3 过渡（基线开放期）：对在途开发库的影响——改基线后须按 `phase1a/infra/runbooks/dev-db-rebuild.md` 重建开发库，开发库中同人同日的多张测试单会令重建后的写入失败，重建前清空；`legacy_numbers` 不受影响。迁入过渡（迁入批次、映射、对账、新旧并存）**不在本卡**，仅有第 3.4 节的前置取证。
- T4 回滚：基线开放期，随库重建（`dev-db-rebuild.md`）；基线关闭后不适用本卡（本卡须在基线关闭前合并）。

### 专项（E1–E3）
- E1 事件载荷：无字段变化。`TimesheetApprovalWithdrawn` 载荷（`EventEnvelope`、`DocumentReference`、`EmployeeId`、`Hours`、`RoundNumber`、`Reason`）无 `SENSITIVE`、`RESTRICTED` 列、无直接标识（只带 ID）；`reason` 为人填原因文本，其在事件载荷中的允许范围是已登记的悬而未决项（`review-parts/timesheet_attendance_audit_idem_sched.md` 待决，E1 与 ADR-0043 第 4 项对「原因全文」无明确条文），本卡不改。
- E2 读模型：无。
- E3 迁入映射：迁入行的 `numbering_rule_version_id` 为空（原编号写入 `legacy_numbers` 并作展示号，未经本系统发号）；迁入批次与映射代码不在本卡（见 T3）。迁入违约取证见第 3.4 节，结果不为 0 的处理办法由用户或 one-rules 定，不阻塞本卡。

### 预期表结构 diff
见第 7 节。
