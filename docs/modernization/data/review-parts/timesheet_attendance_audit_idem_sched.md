# 数据设计审查：工时、考勤、审计、幂等、调度（基线表）

- 审查：one-data 子代理，2026-10-16；对象：`cost_timesheet`（2 表）、`organization_attendance`（2 表）、`platform_audit`（2 表）、`platform_idempotency`（1 表）、`platform_scheduling`（1 表），共 8 张表。
- 标尺：门禁清单取 `origin/claude/one-data-gate-v2` 的版本（含 B0、B3a、O15、O16、附录 C；当前工作树的 `DATA_DESIGN_GATE.md` 是旧版）。其余依据见各条。
- 方法：只读迁移、注册表、分级文件、事件目录、信息图与聚合草稿、规则卡和 Java 源码，用 grep 核对；未起容器、未跑 `pg_dump`，表结构均按基线 SQL 推算。
- 通用说明：基线开放（`backend/architecture/migration-baseline-open` 存在），所有建议改基线，不追加迁移。B0 是阶段入口条目，不按表判。O1 各表写入只涉及本聚合（工时单取号为已登记的多聚合例外 `multi-aggregate-exceptions.txt`），通过。A 系列（OLAP）：这 8 张表本身不是分析事实表，不适用；工时单事件的分析消费方未建（事件目录 `noConsumer`）。O13：工时单带 `legal_entity_id NOT NULL`，通过；其余表不进过账。E3：日历有 `ONE_OFF_IMPORT` 迁入声明，迁入实现不在本次范围（见「未核实」）。

## 逐表结论

| 表 | 结论 | 阻断 / 高 / 中 / 低 |
|---|---|---|
| cost_timesheet.timesheets | 需修改 | 0 / 1 / 5 / 2 |
| cost_timesheet.consumed_events | 需修改 | 0 / 0 / 0 / 2 |
| organization_attendance.work_calendar_years | 需修改 | 0 / 3 / 0 / 0 |
| organization_attendance.work_calendar_days | 需修改 | 0 / 0 / 2 / 2 |
| platform_audit.audit_entries | 需修改 | 0 / 3 / 2 / 2 |
| platform_audit.audit_archive_batches | 需修改 | 0 / 1 / 3 / 0 |
| platform_idempotency.idempotent_responses | 需修改 | 0 / 0 / 4 / 0 |
| platform_scheduling.scheduler_locks | 通过（框架表，仅 2 条低级提示） | 0 / 0 / 0 / 2 |
| 跨表共性（X-1 至 X-4，不计入各表） | — | 0 / 0 / 1 / 3 |
| **合计** | | **0 / 8 / 17 / 13** |

## 问题清单

### cost_timesheet.timesheets（粒度：每张工时单一行；注册表 `BUSINESS_DOCUMENT`）

**T-1　高　O3 / B5 / O4：没有「员工 × 工作日」唯一约束**
- 问题：用户决定「一人一日一单」，库里只有单据编号唯一，同一员工同一天可以存在多张单；应用层也没有按（员工，工作日）查重。
- 证据：迁移 `cost_timesheet/V20261014140000__cost_timesheet_baseline.sql:24`（只有 `uk_timesheets_timesheet_number`）、`:26`（`ix_timesheets_employee_id (employee_id, work_date)` 为非唯一）；信息图 OBJ-66 行（`ENTERPRISE_INFO_MAP_DRAFT.md:123`，Q15）；`ENTERPRISE_AGGREGATES_DRAFT.md:160`（INV-64，写明按 B 类落部分唯一索引）与 `:996`（N-10 已登记并报 one-coordinator）；规则卡 `rules/raw/timesheet-lifecycle.md:44`（RULE-04，旧系统用过滤唯一索引镜像）；`SubmitTimesheet.java:66-67,80-88`（只按调用方给的工时单 ID 加锁、查找，新建分支不查（员工，日期））。
- 建议改法：加（`employee_id`，`work_date`）的唯一索引（`uk_timesheets_employee_id_work_date`），谓词（哪些状态排除）见「待用户决定」第 1 条；建成后 `ix_timesheets_employee_id` 成为其前缀冗余，删除。
- 依据：门禁 O3、B5、附录 C 缺陷 a；§14.1 D2。

**T-2　中　B3 / B3a：状态集合、事件名与信息图、聚合草稿对不上**
- 问题：①信息图 OBJ-66 的状态含「已撤回」，`CHECK` 没有；撤回走 `WithdrawTimesheet` 却落到 `RETURNED` 并发 `TimesheetReturned`，状态与事件上看不出撤回与退回的区别；②事件 `TimesheetCancelled` 的目标状态是 `DRAFT`，名字与目标不一致（B3a ④）；③`REJECTED` 不在信息图的工时状态里，事件目录自述「驳回即退回，目前只在流程以 REJECTED 结束时才出现」，`CHECK` 多出的取值没有来由；④N-10 指出 `CHECK` 缺 `CANCELLED`，Phase 2 要定是否设终态；⑤没有测试比对 `CHECK` ↔ `TimesheetStatus` ↔ `Timesheet.MACHINE`（`TimesheetStateMachineTest` 里没有 `CHECK` 比对）。
- 已核通过的部分：`CHECK` 五个取值 = `TimesheetStatus` 五个枚举值 = `MACHINE` 状态集（`Timesheet.java:55-60`，`TimesheetStatus.java:4-9`），每个取值都有进入它的转换，五个事件的目标状态都在 `CHECK` 内。
- 证据：迁移 `:11`、`:36`；事件目录 `events/timesheet.yaml:135,195,258`（三个事件的说明）；`CancelTimesheet.java`、`WithdrawTimesheet.java`、`Timesheet.java:163-175`；`ENTERPRISE_AGGREGATES_DRAFT.md:996`。
- 建议改法：状态集合由用户定（见待决第 2 条）；定后在 `CHECK`、事件目录、事件类、列注释、用例名同步，并补一个三方一致的测试（同 G-D12）。
- 依据：门禁 B3、B3a、B7；§14 第 2、3 项。

**T-3　中　B3 / O4：状态与「轮次、提交日」成对约束缺失**
- 问题：`submit()` 总是同时让 `round_number` 加一并写 `submitted_date`（`Timesheet.java:133-135`），列注释也写「0 表示从未提交」「空表示从未提交」（迁移 `:37,:44`），但库里没有约束保证二者同零同空，也不保证已提交过的状态（`SUBMITTED`、`RETURNED`、`APPROVED`、`REJECTED`）轮次不小于 1。
- 建议改法：加表级 `CHECK`：`(round_number = 0) = (submitted_date IS NULL)`，以及状态属于上述四个时 `round_number >= 1`（取消后回 `DRAFT` 时轮次与提交日保留，不受影响）。
- 依据：门禁 O4；§14.1 D2。

**T-4　中　A6 / ADR-0026 第 9 条：`org_unit_id` 取创建时的当前部门，不取工作日所在部门**
- 问题：ADR-0026 第 9 条规定业务事实的部门取事实发生时的部门；工时的事实发生在 `work_date`，而 `org_unit_id` 取填报当时的在职部门（`SubmitTimesheet.java:81`），同一个方法里法人却按工作日解析（`:83` `legalEntities.on(employee, command.workDate())`），口径不一致；工作日期可以是此前几天，期间调岗就会错归。注释（迁移 `:43`）是有意写的，复审 A-M4 以「workforce 没有按日期查部门的 api」接受（`phase1a/review/REVIEW_fix-a.md:151`），数据打分卡 K1 当初的改法是「按 `work_date` 解析写入」。
- 建议改法：表结构不变；口径要么改为按 `work_date` 取部门（需 workforce 提供按日期查询），要么由用户确认接受并写入规则卡；在此之前这条不能算通过。
- 依据：门禁 A6、O14；ADR-0026 第 9 条；§14.1 D7。待决第 3 条。

**T-5　中　B1 / B7（已知）：表粒度与信息图 OBJ-66 不一致**
- 问题：信息图 OBJ-66 是「员工 × 填报期间的工时集合，含工时条目（对工作包、商机或间接事项）」，现表是一员工一日一行、只有一个 `submitted_hours` 与 `project_manager_id`，没有条目、工作坐标、工时类别、「已过账」状态。这是 1a 的切片，N-10 已登记并报 one-coordinator 排 Phase 2；列在这里是因为现表无法归集到项目，且条目一旦引入，T-1 的唯一约束口径（按日还是按条目）要一起定。
- 证据：迁移 `:4-19`；信息图 `:123`；`ENTERPRISE_AGGREGATES_DRAFT.md:996`。
- 依据：门禁 B1、B6；§14.1 D16。

**T-6　低　O10：三个索引没有对应的查询路径**
- 证据：迁移 `:26-28`（`ix_timesheets_employee_id`、`ix_timesheets_org_unit_id`、部分索引 `ix_timesheets_status`）；`JdbcTimesheetRepository.java` 只有按 `id` 的 `find`、`findForUpdate`，没有按员工、部门或状态的查询；卡上也没有登记查询路径（注册表无该字段，DG-0）。
- 建议改法：列表路径出现之前不留；`ix_timesheets_employee_id` 随 T-1 替换。
- 依据：门禁 O10；CS-1.8。

**T-7　中　C3：`BUSINESS_RECORD` 保留期未在 GUIDING_PRINCIPLES §6 登记**
- 证据：注册表 `data-access-registry.yaml:24-31`；`GUIDING_PRINCIPLES.md` §6「Phase 1a 表的保留与归档」无工时单行。登记待用户（见待决第 4 条）。

**T-8　低　C1：列注释与实现不符**
- 证据：迁移 `:32`（`legal_entity_id`「由提交人所属法人传入」）而 `SubmitTimesheet.java:83` 是按员工与工作日解析，不是传入；`:33` 写「创建时校验不得晚于今天」，该规则取决于时钟，只能在应用里保证（合理，注释应写明为何不落库）。

### cost_timesheet.consumed_events（粒度：每个消费方每个事件一行；`IDEMPOTENCY`）

**CE-1　低　O7 / 注册表：登记为 `APPEND_ONLY` 但保留作业会 `DELETE`**
- 证据：注册表 `:20`；`ConsumedEventsPurgeJob.java:49`（`DELETE … WHERE consumed_at < :cutoff`）；`unversioned-table-writes.txt:26` 已承认。应登记为 `INSERT_DELETE`（同 `idempotent_responses`）。
- 依据：门禁 O7、C3；§14.1 D8（技术表删除限于过期清理）。

**CE-2　低　O10：清理按 `consumed_at` 删除，无索引**
- 证据：迁移 `:46-55`（只有主键与 `(event_id, consumer_name)` 唯一）；`ConsumedEventsPurgeJob.java:49`。90 天窗口内行数不大，但随事件量线性扫描。
- 建议改法：加 `ix_consumed_events_consumed_at (consumed_at)`。保留期 90 天本身待用户确认（待决第 4 条）。

### organization_attendance.work_calendar_years（粒度：每个日历的每个年份一行；`AGGREGATE_ROOT`）

**WY-1　高　O3 / ADR-0033 第 9 项第 2 条：默认日历用「法人为空 + `NULLS NOT DISTINCT`」，没有 `calendar_scope_kind`**
- 问题：ADR 明文要求不用「法人为 NULL」表示默认日历，新增 `calendar_scope_kind`（`DEFAULT` / `LEGAL_ENTITY`，text + 命名 `CHECK`，两者与 `legal_entity_id` 空或非空成对），唯一性用两个部分唯一索引，并注明「并入 R-1b」。现状仍是可空列加 `UNIQUE NULLS NOT DISTINCT`。同时查询用 `legal_entity_id IS NOT DISTINCT FROM :x`，这种谓词用不上 btree 唯一索引（表很小，影响低，但改成部分索引后应拆成两条语句）。
- 证据：迁移 `organization_attendance/V20261014141000__organization_attendance_baseline.sql:6,13`；`JdbcWorkCalendarQueries.java`（`find`、`findMaintainedYearsBefore` 的 `is not distinct from`）；`JdbcWorkCalendarRepository.java`（`find`）；`WorkCalendarKey.java`（`legalEntityId` 为 null 表示默认）。R-1b 是否已关闭待核。
- 建议改法：加 `calendar_scope_kind text NOT NULL`（`ck_work_calendar_years_calendar_scope_kind`），成对 `CHECK`（`DEFAULT` ⇒ `legal_entity_id IS NULL`，`LEGAL_ENTITY` ⇒ 非空），把 `uk_work_calendar_years_legal_entity_id_year_number` 换成两个部分唯一索引（`DEFAULT` 按年份、`LEGAL_ENTITY` 按法人 + 年份）。
- 依据：ADR-0033 第 9 项第 2 条；门禁 O3、O15。

**WY-2　高　O6 / D17 / ADR-0033 第 9 项第 1、3 条：工作日历是配置，未版本化，历史被覆盖，使用方不记版本**
- 问题：用户已定日历按配置类版本化（DA-17）。现状：年度行原地改 `version`，日期明细是整年「删光再插入」（`REPLACE_SET`），没有版本表、作者、确认人、确认时间、决定出处、生效日；过去日期的更改历史只在事件里。审批时限计算读日历（`DeadlinePlanner` → `WorkCalendarService`），任务上没有记录所用的日历版本（其他基线里无日历列：只有 `platform_identity` 基线 `:342-343` 的权限码提到 calendar）。D17 细则 6 要求使用方记版本 ID，ADR-0033 第 9 项第 3 条已定拟建 `work_calendar_versions` 与 `work_calendar_version_days`。ADR 自述版本化「随 W2 K-2 时态基座一次改到位」，所以这是已排期项，但在基线关闭前必须落地，也没有进注册表的 D17 豁免清单。
- 证据：迁移 `:4-23`（无生效日、确认人、版本号）；注册表 `:42-47`（`mutability: REPLACE_SET`、`PERMANENT`）；`JdbcWorkCalendarRepository.java`（`save` 内 `delete from … work_calendar_days`）；`WorkCalendarYear.maintain` 的 Javadoc（整年替换，过去日期靠确认标志放行）；`DeadlinePlanner.java:33,41`。
- 建议改法：按 ADR-0033 第 9 项第 3 条建日历版本表（UUIDv7 主键；自然键 = 日历标识 + 版本号唯一；`work_calendar_version_days` 的 `(work_calendar_version_id, calendar_date)` 唯一；配置型版本的作者、确认人、确认时间、`decision_ref`、生效日），年度行并入版本；审批任务等使用方增 `work_calendar_version_id`。
- 依据：门禁 O6、O15；§14.1 D15、D17 细则 2、6；ADR-0033 第 9 项。

**WY-3　高　E1：日历事件的聚合标识是文本 `default:2026`，不是 UUID**
- 问题：`WorkCalendarYear` 以 `key.asText()`（`default:2026` 或 `<法人 uuid>:2026`）作事件 `aggregateId`；ADR-0033 第 7 项第 1 条要求 `aggregateId` 为 UUID；年度行有自己的 UUIDv7 主键却没有传给聚合。默认日历事件的 `legalEntityId` 为空。
- 证据：`WorkCalendarYear.java`（两处 `new EventDraft(…, key.asText(), …)`）；`WorkCalendarKey.java asText`；ADR-0033 第 7 项第 1 条。
- 建议改法：聚合带年度行 ID（版本化后为日历标识的 UUID）作事件聚合标识；事件目录 `aggregateId` 声明 `format: uuid`。
- 依据：门禁 E1；ADR-0033 第 7 项第 1 条。内核 `EventDraft.aggregateId` 目前整体仍是 `String`（`kernel/event/EventDraft.java:12`），属跨 BC 项，见 X 区说明。

### organization_attendance.work_calendar_days（粒度：每个年份内每个例外日期一行；`AGGREGATE_CHILD`）

**WD-1　中　O4 / B5：「调休上班日必须落在周六或周日」只在应用里**
- 证据：迁移 `:41`（注释写了规则）；`CalendarDay.java:19-22`（抛 `ORGANIZATION_ATTENDANCE_MAKE_UP_WORKDAY_ON_WEEKDAY`）；表上无约束。这条规则只依赖本行的两列，可以落库。
- 建议改法：加 `ck_work_calendar_days_make_up_weekend`：`day_kind <> 'MAKE_UP_WORKDAY' OR EXTRACT(ISODOW FROM calendar_date) IN (6, 7)`。
- 依据：门禁 O4、B5；§14.1 D2（能落成约束的都落成约束）。

**WD-2　中　O4：例外日期必须在所属年度内，库里没有约束**
- 证据：`WorkCalendarYear.java`（`maintain` 对不在本年的日期抛 `DAY_OUTSIDE_YEAR`）；迁移 `:25-36` 只有 `(work_calendar_year_id, calendar_date)` 唯一。跨行规则，要落库须在明细行冗余年份并用复合外键。
- 建议改法：二选一：①明细加 `calendar_year_number`，复合外键 `(work_calendar_year_id, calendar_year_number)` → 年度行 `(id, year_number)`（先加唯一），并 `CHECK (EXTRACT(YEAR FROM calendar_date) = calendar_year_number)`；②写明只能在应用里保证的理由。若 WY-2 的版本化重做，此条随版本表一并处理。
- 依据：门禁 O4；§14.1 D2。

**WD-3　低　O8：枚举 `CHECK` 未具名**　迁移 `:29`（`day_kind` 的内联 `CHECK`），应为 `ck_work_calendar_days_day_kind`。依据：NAMING_STANDARD §2.4（DDL-22）。取值与 `DayKind` 一致，通过。

**WD-4　低　O7：`ON DELETE CASCADE` 未登记**　迁移 `:34`。同聚合子行允许 `CASCADE` 但须登记（NAMING_STANDARD §2.4a-4）；登记机制随 G-DDL6 待建，现无处可登记，建成时补。

### platform_audit.audit_entries（粒度：每个被审计的动作或领域事件一行；`LOG_APPEND_ONLY`）

**AE-1　高　O15 / ADR-0033 第 7 项第 2 条：`subject_key` 未拆成「类别 + uuid」**
- 问题：ADR 要求 `audit_entries.subject_key` 拆为 `subject_kind`（text + CHECK）与 `subject_id uuid`，索引 `(subject_kind, subject_id, created_at)`，员工查本人登录记录改按 `subject_kind = USER_ACCOUNT` 与 `subject_id`。基线仍是 `subject_key text`（`{类型}:{id}`），应用到处拼字符串。同一 ADR 在 `platform_integration` 的 `outbound_deliveries` 已落实（迁移 `platform_integration/V20261014133000__platform_integration_baseline.sql:37-38`），审计没有。拆分时要处理不带 ID 的对象：登录失败写 `login:<方式>`（`LoginAdmission.java:94`），拒绝访问写资源路径（`AccessDeniedAudit.java`），所以 `subject_id` 需可空并与类别成对。
- 证据：迁移 `platform_audit/V20261014134000__platform_audit_baseline.sql:11,26,34`（注释示例 `approval-task:42` 还是旧的整数 ID）；`JdbcAuditEntryQueries.java:75`（`where subject_key = :subject`）及其 `"user-account:" + subject.value()`；`AuditDomainEvents.java`（`aggregateType + ":" + aggregateId`）；`archive-columns.txt`（`subject_key` 一行）。
- 建议改法：`subject_key` → `subject_kind`（命名 `CHECK`）+ `subject_id uuid NULL`，成对 `CHECK`（`subject_id IS NULL` 仅限登录、资源类类别）；`audit_entries_subject_idx` → `ix_audit_entries_subject (subject_kind, subject_id, created_at DESC, id DESC)`；`archive-columns.txt` 同步。
- 依据：ADR-0033 第 7 项第 2 条；门禁 O15；§14.1 D16。

**AE-2　高　O11 / O7 / C3：只追加、保留 10 年的大表没有分区键，在线行永不删除，与用户已定方案不符**
- 问题：GUIDING_PRINCIPLES §11 O-8 已定「`audit_entries` 按月分区，分区归档并校验后由专用维护角色删除在线分区（作为只追加约束的登记例外）」；NAMING_STANDARD §2.4a-8 要求建表时定分区键，「上线后再分区要重写全表」。基线是非分区表；`AuditArchiveJob` 的 Javadoc 自述「在线行保留，表拒绝 DELETE（G34），删除要另行决定」，所以 `retention: {policy: 3y}` 实际是「永久在线」；含 `source_address`（PI）的表按 ADR-0043 R-2 不得实质永久。分区还带来结构冲突：分区表的主键与唯一约束必须含分区键，与 ADR-0033「单列 uuid 主键」冲突，且 `uk_audit_entries_event_id` 将无法跨分区保证事件幂等（`saveForEvent` 的 `ON CONFLICT (event_id)` 依赖它）。这一冲突不能由本审查裁定，见「需 opus 复核」。
- 证据：迁移 `:6-28`（无分区）、`:47-61`（触发器禁 UPDATE / DELETE / TRUNCATE，未留维护角色例外）；`GUIDING_PRINCIPLES.md` §6 审计行、§11 O-8；注册表 `:207`；`AuditArchiveJob.java` 类注释；`JdbcAuditEntryRepository.saveForEvent`（`on conflict (event_id)`）。
- 建议改法：按月范围分区（分区键 `created_at`）；事件幂等改由「`event_id` 登记表」或「分区键纳入唯一」之一承担；触发器为维护角色的分区摘除留登记例外。具体方案由待决第 7 条定。
- 依据：门禁 O11、C3、O7；GUIDING_PRINCIPLES §11 O-8；ADR-0043 R-2。

**AE-3　高　C4 / E1：自由文本列无「允许写入内容清单」，且存在已知的个人信息写入**
- 问题：`detail`、`before_text`、`after_text`、`change_reason` 都是自由文本，卡上没有允许写入的内容清单，也没有个人信息标记（ADR-0043 U-7(b) 允许 `before_text` / `after_text` 记被变更字段自身的 L2 个人信息原值，即这几列设计上会含个人信息，却未登记）；应用只拦截「像密钥的文本」（`AuditEntry.java:16-17,23`）。现存违例（ADR-0043 第 4 项第 5 条已列）仍在：登录审计把 `address=`（IP）与 `agent=`（浏览器标识）写进 `detail`，同时还写进 `source_address` 列；检索与导出审计把 `sourceAddress` 条件写进 `detail`；操作登记把操作人、见证人姓名写进 `detail`（U-7b 未定，`backend/architecture/` 下没找到带到期日的例外登记）。`agent=`（浏览器标识）不在 ADR-0043 的处置里，是否属个人信息待核。
- 证据：`LoginAdmission.java:108-114`（`describe`，被 `:75,98` 调用）；`AuditSearch.java:16-21`；`OperationAttestation.java:32-34`；`AuditEntry.java:16-23`；`data-classification.txt` 无这四列；`archive-columns.txt` 把它们全部标 `INTERNAL`、`ARCHIVE`。
- 建议改法：登记这四列的第二维（访问级别、个人信息标记、「用途：」）并写允许内容清单（字段名、来源、是否可能含个人信息）；IP 与浏览器标识从 `detail` 去掉（只留 `source_address`）；清单外内容不得写入，实现复核对照写入点。ADR-0043 文件格式四段化前，第二维写在卡里的分级表。
- 依据：门禁 C4、E1；ADR-0043 第 4 项第 1、5 条、U-7；ADR-0026 第 7 项第 3 条。

**AE-4　中　O8 / O2：命名与长度、空串约束**
- 问题：①`ddl-naming-baseline.txt` 里本表有 7 行存量违规（`action` → `action_code` `:10`，`detail` → `detail_text` `:11`，`audit_entries_pkey` `:43`，4 个索引名 `:68-71`）；门禁 §5 要求涉及既有非框架例外行的卡在本卡改掉并删行，且基线开放期应直接改基线（该文件头仍写「追加迁移改名」，与 ADR-0038 不一致）；②`outcome_kind`、`login_method_kind` 的枚举 `CHECK` 未具名（迁移 `:21-22`，应为 `ck_audit_entries_outcome_kind`、`ck_audit_entries_login_method_kind`；取值与 `AuditOutcome`、`LoginMethod` 枚举一致，通过）；③改名后 `action_code` 是 `_code` 列，须有格式 `CHECK`（取值有 `platform.identity.role.created` 与事件类型 `cost.timesheet.TimesheetSubmitted` 两种写法，先定格式）；④`action`、`subject_key`、`detail`、`change_reason`、`before_text`、`after_text`、`source_address` 在库里都无长度上限，应用只限 2000（`AuditEntry.java:16`），也未禁空串。
- 建议改法：按 baseline 改名并删基线行；具名 `CHECK`；`char_length <= 2000` 与 `<> ''` 的 `CHECK`；`source_address` 另设较小上限。
- 依据：门禁 O8、O2、§5；NAMING_STANDARD §2.4、§2.4a-5。

**AE-5　中　O10：查询路径与索引不匹配，导出先做无上限计数**
- 证据：检索可按 `on_behalf_of_id`（`JdbcAuditEntryQueries.java:92`）、`source_address`（`:104`）、`outcome_kind`、`starts_with(action, :prefix)`（`:100`，btree 用不上）过滤，均无索引；排序是 `created_at DESC, id DESC` 的游标，而四个索引都不含 `id`（迁移 `:24-27`），不符合 §2.4a-2「游标排序键以 `id` 兜底组成联合索引」；`ExportAuditEntries.java:43` 先 `count(filters)`（`JdbcAuditEntryQueries.java:57`）再 `findAll(…, maxRows)`，无过滤时对整张 10 年表计数。
- 建议改法：索引改为 `(actor_id, created_at DESC, id DESC)` 等并补 `source_address`、`on_behalf_of_id` 的路径（或明确限制这两个过滤必须同时带时间范围）；`starts_with` 改用 `text_pattern_ops` 索引或改精确匹配；导出用 `limit maxRows + 1` 判断超限，去掉 `count`。查询路径登记待注册表字段建成（DG-0）。
- 依据：门禁 O10；§14.1 D19；NAMING_STANDARD §2.4a-2。

**AE-6　低　C1：列注释与实际用法不符，可空列缺「为空表示」**
- 证据：`source_address`、`outcome_kind`、`login_method_kind` 注释写「登录与会话类条目才有」（迁移 `:43-45`），实际 `AccessDeniedAudit.java:75`（拒绝访问，无登录方式）、`CorrectLegalEntityAttribution.java:52`（无地址）、`HandleOrgSnapshotChange.java:90`、`ForceUserLogout.java:66` 也写结果列；`action` 注释写 `{子域}.{bc}.{对象}.{动词}`（`:31`），而事件审计写 `envelope.eventType()`（`AuditDomainEvents.java`，如 `cost.timesheet.TimesheetSubmitted`）；`detail`、`correlation_id`、`change_reason`、`before_text`、`after_text` 为可空却未写「为空表示」；`updated_at` 恒等于 `created_at`（注释 `:37`），属冗余列（O9，`archive-columns.txt` 已排除）。
- 依据：门禁 C1、O8、O9；§14.1 D10。

**AE-7　低（待核）　数据正确性：`source_address` 的取值来源不一致**
- 证据：列注释写「只取受信代理解析后的地址」（迁移 `:43`），`SessionGuardFilter.java:91` 用 `request.getRemoteAddr()`，其他写入点用 `origin.address()`。若部署在反向代理后且未做受信代理还原，该行写入的是代理地址。待核：是否已用 Tomcat 的 remote-ip 还原。

### platform_audit.audit_archive_batches（粒度：每个归档批次一行；`LOG_APPEND_ONLY`）

**AB-1　高　O15 / ADR-0033 第 1 项第 10 条：归档进度按 UUID 顺序推进，漏归档只告警，不补**
- 问题：ADR 明文「任何代码不得依赖跨实例的 ID 顺序；归档批次按「时刻列 + 主键」排序」。`AuditArchiveJob` 以 `id > last order by id` 取批、以最后一条 `id` 作进度（`:135-136`），对账以 `id <= last` 计数比对（`:123`）；多实例或时钟回拨时，晚写入但 `id` 更小的条目永远不会被归档，对账只记一次告警和计数（`:124-129`）。归档是将来删除在线行（AE-2）的前提，漏档即丢数据。表只有两个 uuid 边界列，没有时间边界，`first_entry_id <= last_entry_id` 的 `CHECK` 也依赖 UUID 顺序（迁移 `:76`）；任务里还用全零 UUID 作「没有任何条目」的哨兵（`:47`，ADR 第 7 项第 6 条「去掉哨兵」）。
- 证据：同上；`AuditArchiveJob.java` 类注释（自述 id 顺序与对账）。
- 建议改法：批次表增 `first_created_at`、`last_created_at`（`timestamptz NOT NULL`），进度按（`created_at`，`id`）边界推进，`CHECK` 用时间边界；若 AE-2 采用按月分区，则一批对应一个分区，边界即分区范围。
- 依据：ADR-0033 第 1 项第 10 条、第 7 项第 6 条；门禁 O15；§14.1 D8。

**AB-2　中　O3 / B5：批次不重叠、无缺口、对象不重复，库里只有 `last_entry_id` 唯一**
- 证据：迁移 `:75`（仅 `uk_audit_archive_batches_last_entry_id`）、`:76`；`object_key` 无唯一，`sha256_hash` 无格式 `CHECK`（注释写小写十六进制，`:84`）。区间不重叠无法用唯一约束表达，应写明由对账承担（AB-1 改完后以时间边界对账）。
- 建议改法：加 `uk_audit_archive_batches_first_entry_id`、`uk_audit_archive_batches_object_key`，`sha256_hash ~ '^[0-9a-f]{64}$'` 的命名 `CHECK`。
- 依据：门禁 B5、O4、O8。

**AB-3　中　O10：每次对账对整个在线表前缀计数**
- 证据：`AuditArchiveJob.java:123`（`select count(*) from audit_entries where id <= :last`）及 `:121`（`sum(entry_count)`）；表按 10 年增长，对账成本随行数线性上升。AB-1 改成按分区或按时间边界后可改为按批次对账。
- 依据：门禁 O10（语句数 / 扫描量不随行数增长）；§14.1 D19。

**AB-4　中　O8 / C3：命名与保留期**
- 证据：触发器命名与 `audit_entries` 的 `audit_entries_reject_*` 风格不一，本表用 `tg_` 前缀（迁移 `:95-98`），两处统一即可；`updated_at` 在只追加表上恒等于 `created_at`（`:87`）；注册表 `retention: PERMANENT`（`:216`）与 GUIDING §6「归档保留至满 10 年」不一致，见待决第 4 条。

### platform_idempotency.idempotent_responses（粒度：每个用户的每个幂等键一行；`IDEMPOTENCY`）

**IR-1　中　C4：整份应答正文落库 24 小时，没有分级、没有内容边界**
- 问题：`IdempotencyFilter` 对所有非 `/sessions`、`/login-states` 的 POST 保存完整应答正文（`:122-123`，`ContentCachingResponseWrapper` 全量读取），`response_text` 因此是任意接口应答的副本：以后出现工资、证件号、银行卡、姓名邮箱等字段的接口，其数据会复制进这张表；ADR-0043 第 4 项要求 `SENSITIVE`、`RESTRICTED` 列不进 web 出参、`PI` 直接标识受限，这张表绕过了列级登记。`data-classification.txt` 无该列；`IdempotencyScope` 只排除了凭据类路径，不是按「应答不含个人信息」的白名单。正文也无大小上限。
- 证据：`IdempotencyFilter.java:120-125`；`IdempotencyScope.java`；迁移 `platform_idempotency/V20261014221000__platform_idempotency_baseline.sql:12,28`。
- 建议改法：①`response_text` 登记第二维（至少 `PI` 可能，级别由用户定，见待决第 5 条）；②写明允许写入范围（按接口白名单，不收含 `SENSITIVE` 列的接口），或只存应答的标识与状态、重放时重新读取；③加 `char_length` 上限。
- 依据：门禁 C4、O8；ADR-0043 第 4 项；ADR-0026 第 7 项第 3 条。

**IR-2　中　O5 / O7：登记为 `INSERT_DELETE`，实际还有条件更新**
- 证据：注册表 `:222`；`JdbcIdempotentResponses.java:64-72`（`finish`：`update … where … and response_number is null`），`unversioned-table-writes.txt:22` 已写「finished once」。应登记 `CONDITIONAL_UPDATE` 并写前置条件 `response_number IS NULL`。
- 依据：门禁 O5、O7；§14.1 D4。

**IR-3　中　O8 / O9：列类别词与「拼成一个字符串」**
- 问题：`response_header_text` 存 JSON 对象文本（迁移 `:13,:29`），§2.4a-7 允许幂等应答体用 `jsonb`，但要 `jsonb_typeof` `CHECK` 并在注册表登记用途；现为 `text`，也没有登记。`response_number` 存的是 HTTP 状态码、`response_name` 存的是内容类型（如 `application/json`），与类别词定义（`_number` 编号 / 序号，`_name` 名称）不符。`request_hash`（注释：SHA-256 十六进制）无格式 `CHECK`；`idempotency_key` 注释写至多 128 个可见字符（`:24`，过滤器 `MAX_KEY_LENGTH = 128`）而库里无长度与空串约束；`response_number` 无 100 至 599 范围约束。
- 建议改法：`response_header_text` 改 `jsonb` 加 `jsonb_typeof = 'object'` 的 `CHECK` 并登记用途，或拆成 `etag`、`location` 两列；`response_number` / `response_name` 改为 `response_status_code`、`response_content_type`（新词先查 `terms.yaml`）；补格式、长度、范围的命名 `CHECK`。
- 依据：门禁 O8、O9；NAMING_STANDARD §2.4、§2.4a-5、7。

**IR-4　中　O4 / C1：「处理中」状态的成对约束与注释**
- 证据：处理中时 `response_number`、`response_name`、`response_text`、`response_header_text` 一起为空（`finish` 一次写全，`:64-72`），库里无成对 `CHECK`；后三列为可空却未写「为空表示」（迁移 `:27-29`，只有 `response_number` 写了）。
- 建议改法：加 `CHECK (response_number IS NOT NULL OR (response_name IS NULL AND response_text IS NULL AND response_header_text IS NULL))`，补注释。

### platform_scheduling.scheduler_locks（粒度：每个作业一行；`FRAMEWORK`，ShedLock）

通过（O2 主键豁免属框架自建表，ADR-0033 第 1 项第 8 条）。只有两条低级提示：
- **SL-1　低　C1：** `created_at`、`updated_at` 是自加列，ShedLock 只写四个配置列（`SchedulingConfiguration.java` 的 `ColumnNames`），`updated_at` 永远等于创建时间，注释「更新时间」误导；建议注释写明「不由 ShedLock 维护」或删去这两列。
- **SL-2　低（待核）：** ADR-0033 第 1 项第 8 条说豁免登记在 `backend/architecture/primary-key-exemptions.txt`，仓库没有这个文件，实际靠 `DdlNamingTest.FRAMEWORK_TABLES`（NAMING_STANDARD §2.4「守护」段）；文档与守护的出处不一致，请 one-quality 或 one-coordinator 对齐。

## 跨表共性问题

- **X-1　中　O8：枚举 `CHECK` 未具名与文本列长度、空串约束缺失，散在多张表。** 未具名的枚举 `CHECK`：`timesheets.status`（迁移 `:11`）、`work_calendar_days.day_kind`（`:29`）、`audit_entries.outcome_kind` 与 `login_method_kind`（`:21-22`），而 `platform_integration` 的基线已用 `CONSTRAINT ck_… CHECK` 写法；规则出自 NAMING_STANDARD §2.4（DDL-22），守护 G-D12 未建，所以没有测试拦。文本列无长度上限或空串约束：审计各文本列（AE-4）、`idempotent_responses` 的 `idempotency_key`、`response_text`（IR-1）、`consumed_events.consumer_name`、`timesheets.timesheet_number`。整改随各表条目一起做。
- **X-2　低　C2 / DG-0：注册表缺门禁要求的字段。** 数据类别（D17 细则 1）、`business_key`、查询路径、分区键、`jsonb` 用途、个人信息标记在注册表都还没有（DG-0 待建），所以本次的 O3、O6、O10、O11 都是用迁移与代码推出来的；日历属「配置」类、本应进版本化，当前没有任何字段能让守护发现它（WY-2）。
- **X-3　低　O9：`updated_at` 在从不更新的表上。** `consumed_events`、`audit_entries`、`audit_archive_batches`、`work_calendar_days`（整年删光再插）、`scheduler_locks` 都带从不被改写的 `updated_at`，是冗余列；若全库约定每表带 `created_at` / `updated_at`，请在 NAMING_STANDARD 写明对只追加表的例外，否则逐表删。
- **X-4　低　O12：基线开放期的文档口径冲突。** `ddl-naming-baseline.txt` 文件头仍写「1a 用 Flyway 追加迁移改名」，与 ADR-0038（基线开放期直接改基线）不一致；`audit_entries` 的 7 行存量应在基线里改掉后删行（AE-4）。
- 其他跨 BC 项（不计数）：事件信封 `aggregateId` 在内核仍为 `String`（`kernel/event/EventDraft.java:12`），ADR-0033 第 7 项第 1 条要求 UUID，影响日历（WY-3）与所有聚合；非本批范围，请 one-coordinator 派工。

## 待用户决定

1. **工时单「一人一日一单」的唯一范围。** 规则卡 RULE-04：任何状态（含已取消）都占用该日；聚合草稿 INV-64 / N-10：部分唯一索引，排除已驳回、已取消；现行代码取消后回 `DRAFT`（不是终态）。要定：驳回、撤回、取消后同一天能否另起一张新单；谓词写哪些状态。影响 T-1 的索引谓词与 T-2 的状态集合。
2. **工时单状态集合。** 信息图有「已撤回」，现行实现把撤回并入 `RETURNED`、取消回 `DRAFT`、保留 `REJECTED`；是否设终态 `CANCELLED` / `WITHDRAWN`、`REJECTED` 是否保留。影响 `CHECK`、事件名、用例名。
3. **工时单的部门口径（T-4）。** 取「工作日当天的部门」（需 workforce 提供按日期查询）还是「提交当时的部门」；后者须写入规则卡并经用户认可。
4. **保留期（C3，一律待认可）。** `timesheets`（`BUSINESS_RECORD`，是否随会计档案）；`consumed_events` 90 天；`idempotent_responses` 24 小时；`work_calendar_*` 永久；`audit_archive_batches` 永久还是随归档满 10 年；`audit_entries` 在线行实际是否按 O-8 分区后删除，在线 3 年内 `source_address` 的删除时点。GUIDING_PRINCIPLES §6 只登记了审计一行。
5. **分级（C4）。** `idempotent_responses.response_text` 的访问级别与个人信息标记，及是否限制只对不含 `SENSITIVE` 的接口保存；`audit_entries` 的 `detail`、`before_text`、`after_text`、`change_reason` 的个人信息标记；浏览器标识（`agent=`）是否属个人信息；工时数据（员工 × 日期 × 小时）是否按 `PI` 登记。
6. **操作登记写入姓名（ADR-0043 U-7b 未定）。** `OperationAttestation` 的操作人、见证人是改写员工 ID 还是保留姓名并登记例外，定前请按 ADR 写带到期日的例外登记。
7. **`audit_entries` 分区方案（AE-2）。** 分区键进主键与唯一约束，与 ADR-0033「单列 uuid 主键」及事件幂等唯一性冲突：要定是修订 ADR 的表述（分区表主键含 `created_at`）、还是事件幂等改由独立登记表承担；以及维护角色删除分区的只追加例外怎样登记。
8. **日历版本化的时点（WY-2）。** ADR 排在 K-2，请确认是否仍在基线关闭前完成，以及配置型版本的「确认人」由谁担任。

## 需 opus 复核的高风险项

1. **AE-2 与待决 7：审计表分区与主键、事件幂等唯一性的结构冲突。** 分区表主键须含分区键，与 ADR-0033 单列主键冲突，`event_id` 唯一约束无法跨分区；方案选错会让幂等失效或要重写全表，且涉及维护角色对只追加表的 `DELETE` 例外（安全判定）。
2. **AB-1：归档进度按 UUID 顺序，漏档只告警。** 会丢数据，改法与 AE-2 的分区、批次边界相互牵连；需要对「多实例乱序写入 + 在线行将来被删除」的完整性论证再审。
3. **T-1 + T-2 + 待决 1、2：工时唯一约束谓词与状态集合。** 谓词写错会误拒合法重填或放过重复单；与「是否设终态 `CANCELLED`」、规则卡 RULE-04 和聚合草稿 INV-64 的口径互相冲突，须一次定清。
4. **WY-2：日历版本化与使用方记版本。** 涉及审批时限已算出的截止时间是否随日历更新重算（旧规则是「已算的不重算」），版本表形状、确认人与 `decision_ref` 是配置型版本的首个落地样例，会被后续配置表照搬。
5. **IR-1：幂等应答正文入库。** 这是平台级的个人信息与敏感数据复制点，涉及哪些接口可存、是否改为只存标识重放，属安全与隐私判定。

## 未核实

- 门禁清单用的是 `origin/claude/one-data-gate-v2`，未确认该版本已被用户采纳。
- 注册表的数据类别、`business_key`、查询路径等字段不存在，O3、O6、O10 均按迁移与代码推导，未对照卡上的声明。
- 未起数据库，未跑 `DdlNamingTest`、`DataArchitectureTest`、`pg_dump`，约束与索引名按 SQL 文本推算；`DdlNamingTest` 是否检查 `CHECK` 具名未逐条核（只看到 `:353,:378,:426` 有 `ck_` 逻辑，G-D12 标「待实现」）。
- R-1b 是否已关闭（决定 WY-1、AE-1 是否仍在该批次范围内）、K-2 的排期，未向 one-coordinator 核对。
- 旧日历（2016 至 2026）一次性迁入的实现与对账，未查（`ONE_OFF_IMPORT` 只在注册表声明）。
- 工时单日工时上限（是否应有 `<= 24` 的 `CHECK`）：未找到规则卡，信息图只写「超日标准只预警」（OBJ-72）；最低时数 Q15 的数值留第 6 步，均未落库，符合 B7。
- `OperationAttestation` 的例外登记：只在 `backend/architecture/*.txt` 里搜索未见，可能登记在别处。
- RULE-270 是旧系统「批量审批以被代理人名义记录」的规则卡，被 `on_behalf_of_id` 的列注释引用；新系统的代理动作规则卡未找到，`on_behalf_of_id` 与 `actor_id` 的成对关系未核。
- 事件目录 `events/attendance.yaml` 的载荷字段未逐项核对 ADR-0035 的兼容规则。
- 工时单事件与审计 `change_reason` 带驳回、退回、撤销的自由文本原因（事件目录 `timesheet.yaml:179,242,305`），E1 与 ADR-0043 第 4 项对「原因全文」是否允许进事件载荷，未取得明确条文，未单列为问题。
