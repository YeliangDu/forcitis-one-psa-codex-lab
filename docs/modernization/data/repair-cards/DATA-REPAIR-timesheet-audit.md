# 派工卡 DATA-REPAIR-timesheet-audit：工时、考勤日历、审计、幂等已建表的数据设计缺陷修复（草稿）

- 草稿（第二版）：one-data 起草助手，2026-10-16；第二版并入用户 2026-10-16 的决定、统一分级（阻断 0）与 `SCAN_SUPPLEMENT.md` 补入；待 one-data 终审、one-coordinator 派工。
- 证据来源：`docs/modernization/data/review-parts/timesheet_attendance_audit_idem_sched.md`（下称 TS）与 `OPUS_VERIFY.md`（下称 OV）；以 OV 为准。「:N」指 TS 审查时 main 基线对应迁移行：`cost_timesheet/V20261014140000__cost_timesheet_baseline.sql`、`organization_attendance/V20261014141000__organization_attendance_baseline.sql`、`platform_audit/V20261014134000__platform_audit_baseline.sql`、`platform_idempotency/V20261014221000__platform_idempotency_baseline.sql`；R-1b P5 已改审计两表，实施前重核。
- `SCAN_SUPPLEMENT.md`（下称 SS）：全量机械扫描与补审，行号取 `origin/main`（a27bf877），与 TS 旧行号可能差几行。
- 级别以统一分级为准（`DATA_DESIGN_GATE.md` §4a、`BASELINE_TABLE_REVIEW.md` §1、§3）：阻断 0；T-1、AE-1 由阻断归高（有归属、不起条目）；WY-1、AB-1 降中（WY-1 用户已追认时点偏离，AB-1 P5 已解决）；IR-1 高；T-7、AB-4、IR-4 由低升中；WY-3 由中升高（不起条目，归 W2）。
- 「待核」表示分报告与复核报告都没有给出证据，实施者先核实再改。

## 1. 卡号与类别
- 卡号：DATA-REPAIR-timesheet-audit
- 类别：缺陷修复（SESSION_PLAYBOOK §6.1 第 9 条）
- 批次：已建表数据设计审查修复批（门禁 v2 §1.2 第 6 条）；两部分各开一个小 PR，直接合入 main
- 建议负责会话：**A 部分（工时与日历）one-1a-par；B 部分（审计与幂等）one-1a-next-4**
- 顺序：A 部分的 TA-01 依赖 TS-UNIQ 定稿的状态集合，须在 TS-UNIQ 合并后开工；B 部分不依赖 A 部分；B 部分的 TB-07（分区）**依 ADR-0048（已采纳）实施，须在基线关闭前做，属生产前闸门**，其余条目不受影响
- 排期：本卡排在 dev 部署之后（用户「同意，不挡 dev」，`BASELINE_TABLE_REVIEW.md` §3 末段）；本卡本身不是闸门；审计分区（TB-07）、W2 日历版本化、ADR-0043 实施 PR 是生产前闸门（one-ops 登记到 PREDEPLOY，C-15 一组，见 README）

## 2. 依据
- 门禁 v2：O3、O4、O8、O10、O7、C3、C4、E1、B3、B5
- SESSION_PLAYBOOK §14.1 D2、D4、D8、D12、D13、D19；§12.2；ADR-0038；ADR-0026 第 7 项第 3 条；ADR-0033 第 1 项第 10 条、第 7 项；ADR-0043 第 3、4 项、U-7(b)、U-7(c)、R-2、U-13
- GUIDING_PRINCIPLES §11 O-8（审计按月分区）；NAMING_STANDARD §2.4a-5
- 用户决定（第 5 节「已定」）：审计分区，intent `2026-10-15-020820-one-coordinator-审计表按月分区主键须含分区键-与.md` 与 `2026-10-16-085331-one-data-审计表按月分区-知道了-ADR.md`（收件箱 2026-10-16-one-data-audit-partition）；日历范围列追认，intent `2026-10-16-085332-one-data-日历-calendar_scop.md`（收件箱 2026-10-16-one-data-wy1-calendar-scope）
- 复核编号：SS §1、§3、§4（第 8、9、14 组）、§5；OV「AE-3 确认」「IR-1 确认」「AE-5 确认」「高风险项判断 TS 第 1、2、5 条」「T-1 / T-2：用户已定」；建议新增规则 3、5
- 分报告编号：TS T-3、WD-1、AE-2、AE-3、AE-4、AE-5、AB-2、AB-4、IR-1、IR-2、IR-3、IR-4；SS 新增：§4 第 8 组、§5 `audit_entries.created_at`

## 3. 范围与卡上文件

A 部分（one-1a-par）允许改动：
- `backend/src/main/resources/db/migration/cost_timesheet/V20261014140000__cost_timesheet_baseline.sql`（只限 `timesheets` 的 T-3 CHECK）
- `backend/src/main/resources/db/migration/organization_attendance/V20261014141000__organization_attendance_baseline.sql`（只限 `work_calendar_days` 的 WD-1 CHECK）
- `backend/architecture/data-access-registry.yaml`（`consumed_events` 的 CE-1 登记，可选）
- 测试：`backend/src/test/java/com/forcitis/one/cost/timesheet/domain/TimesheetStateMachineTest.java`（只读）、新增 CHECK 集成测试

B 部分（one-1a-next-4）允许改动：
- `platform_audit/V20261014134000__platform_audit_baseline.sql`（只限 `audit_entries` 文本列 CHECK、`audit_archive_batches` 的 AB-2）
- `platform_idempotency/V20261014221000__platform_idempotency_baseline.sql`
- `backend/architecture/data-access-registry.yaml`（`idempotent_responses`、审计两表）、`data-classification.txt`、`archive-columns.txt`（仅 TB-02 涉及时）、`backend/architecture/primary-key-exemptions.txt`（仅 TB-07 登记分区表唯一例外，P5 已新建，待核）、`backend/architecture/` 下 U-7b 带到期日例外登记（文件名待核）
- Java：`backend/src/main/java/com/forcitis/one/platform/audit/infrastructure/persistence/JdbcAuditEntryRepository.java`（TB-07 的 `saveForEvent`）、`backend/src/main/java/com/forcitis/one/platform/identity/application/LoginAdmission.java`、`platform/audit/application/ExportAuditEntries.java`、`platform/audit/infrastructure/persistence/JdbcAuditEntryQueries.java`、`platform/web/IdempotencyFilter.java`、`IdempotencyScope.java`、`AuditSearch.java`（路径待核）
- 测试：对应类的现有测试与新增集成测试；TB-09 须改 `AuditArchiveJobIT.java:76,106,141` 三处不带 `created_at` 的 INSERT 夹具（只补列值，不改断言，PR 单列）

归属：本卡独占上述表的约束片段。T-1 与 X-6 的 `timesheets` 改动归 TS-UNIQ，本卡 T-3 与其同一基线文件，须按 TS-UNIQ 合并后的内容改。

不碰：`audit_entries` 分区、主键与 `uk_audit_entries_event_id`，TB-07 之外一律不动，TB-07 依 ADR-0048；`work_calendar_*` 版本化与 `calendar_scope_kind`（W2）；`EventDraft` 等 kernel 构件；验收测试目录；他卡文件。

## 4. 条目清单

### 第一部分　工时与日历（建议 one-1a-par）

#### TA-01　T-3　中　`timesheets` 状态与「轮次、提交日」成对约束缺失
- 问题：`submit()` 总是同时让 `round_number` 加一并写 `submitted_date`，列注释写「0 表示从未提交」「空表示从未提交」，库内无约束保证同零同空，也不保证已提交过的状态轮次不小于 1。
- 证据：TS `Timesheet.java:133-135`；迁移 `:37,:44`；OV 未单独抽查（T-3 在 OV 报告之外，按 TS 证据取）。
- 改法：加 `ck_timesheets_round_submitted`：`(round_number = 0) = (submitted_date IS NULL)`；加 `ck_timesheets_round_after_submit`：状态属于「提交过」的集合时 `round_number >= 1`。集合的取值**以 TS-UNIQ 定稿的状态集合为准**（OV：「驳回后改原单再提交」使 `REJECTED` 不再是终态，撤回状态名与 `TimesheetCancelled` → `TimesheetApprovalWithdrawn` 同改），不得照 TS 写死 `SUBMITTED / RETURNED / APPROVED / REJECTED`；取消后回 `DRAFT` 时轮次与提交日保留，不受影响。
- 预期表结构 diff：`cost_timesheet.timesheets`：新增上述两条 CHECK。
- 验收要点：提交、退回、撤回、驳回后改原单再提交各路径通过；直接插入「已提交状态但 `round_number = 0`」被拒；`TimesheetStateMachineTest` 原样重跑。
- 依据：门禁 O4；D2；SS §4 第 14 组（`submitted_date:19` ⇔ `round_number:12 > 0`，注释 :44；表内 CHECK 只有 :21-23 三条非负约束）。

#### TA-02　WD-1　中　「调休上班日必须落在周六或周日」只在应用里
- 证据：TS 迁移 `:41`（注释写了规则）；`CalendarDay.java:19-22`（抛 `ORGANIZATION_ATTENDANCE_MAKE_UP_WORKDAY_ON_WEEKDAY`）；这条规则只依赖本行两列，可以落库。OV 未单独抽查。
- 改法：新增 `ck_work_calendar_days_make_up_weekend`：`day_kind <> 'MAKE_UP_WORKDAY' OR EXTRACT(ISODOW FROM calendar_date) IN (6, 7)`。W2 日历版本化若重建明细表，此约束随明细表带走（DA-17 卡须承接）。
- 预期表结构 diff：`organization_attendance.work_calendar_days`：新增 `ck_work_calendar_days_make_up_weekend`。
- 验收要点：周六 / 周日调休通过；周三调休行被拒；`CalendarDay` 原有异常用例原样重跑。
- 依据：门禁 O4、B5；D2。

#### TA-03　TS-UNIQ 待核 5　中　`timesheets` 保留期 10 年的登记与归档 / 清理作业
- 问题：用户已认可 `timesheets` 保留 10 年、归档到对象存储后在线删除（`data/RETENTION_PROPOSAL.md`，用户「按 one-data 建议」）；注册表现为 `BUSINESS_RECORD`（无期限），没有清理或归档作业；GUIDING_PRINCIPLES §6 无该行。TS-UNIQ 不改保留期，故不阻塞该卡；本条是承接。
- 改法：注册表 `retention` 改 `{policy: 10y, job: cost.timesheet.timesheet.archive}`；归档 / 清理作业按审计、审批归档同一口径新建（属功能补做，与财务是否认定工时单为会计档案原始凭证（改 30 年）一并在 Phase 2 工时规格前定；**作业未建成前**，注册表登记带 owner 与到期日的 gap，不得静默留空）；GUIDING_PRINCIPLES §6 补一行（交 one-architecture / one-coordinator）。
- 预期表结构 diff：无 DDL；注册表与作业。
- 验收要点：注册表有 10y 与作业名，作业按期归档并删除在线行；或有 gap 条目含 owner、到期日。
- 依据：门禁 C3；ADR-0043 R-2（`timesheets` 含员工 ID，属个人信息关联数据）；用户决定（保留期，intent 随 #319）。

低级顺带（A 部分，不阻塞）：CE-1（`consumed_events` 登记 `APPEND_ONLY` 但 `ConsumedEventsPurgeJob.java:49` 会 DELETE，应登记 `INSERT_DELETE`，`unversioned-table-writes.txt:26`）、CE-2（`consumed_at` 索引）、WD-3（`day_kind` 内联 CHECK 具名，随 G-D12）、T-8（列注释与实现不符）。

### 第二部分　审计与幂等（建议 one-1a-next-4）

#### TB-01　AE-3　高（OV 确认）　登录审计把 IP 与浏览器标识写进 `detail`；U-7b 例外未登记
- 问题：登录审计把 `address=`（IP）与 `agent=`（浏览器标识）写进 `detail`，同时写进 `source_address` 列；检索与导出审计把 `sourceAddress` 条件写进 `detail`；操作登记把操作人、见证人姓名写进 `detail`。ADR-0043 第 4 项第 5 条、U-7(c) 已选「IP 只留 `source_address`」却未修。
- 证据：TS `LoginAdmission.java:108-114`（`describe`，被 `:75,98` 调用）；`AuditSearch.java:16-21`；`OperationAttestation.java:32-34`；`AuditEntry.java:16-23`；`data-classification.txt` 无这四列；OV：`one-1a-par-log-ip` 已合入只改了日志，P5 同行仍在（P5 :115）；U-7b 的带到期日例外在 `backend/architecture/` 未找到登记。
- 改法：
  1. `LoginAdmission.describe` 去掉 `address=` 与 `agent=`，IP 只留 `source_address`；`AuditSearch` 的 `sourceAddress` 条件同样不再拼进 `detail`（待核：检索与导出审计是否在本卡范围，ADR-0043 第 4 项第 5 条原文为准）。
  2. U-7b（`OperationAttestation` 把操作人、见证人姓名写进 `detail`）未定：按 ADR-0043 写带到期日例外登记（owner、到期日、指向 U-7b），到期日由用户给（第 5 节待决 3）。
  3. 本卡内写「允许写入内容清单」草稿（`detail`、`before_text`、`after_text`、`change_reason`：字段名、来源、是否可能含个人信息；U-7(b) 允许 `before_text` / `after_text` 记被变更字段自身的 L2 个人信息原值），**待核**并交用户确认；四列的访问级别与 PI 标记登记待用户（第 5 节待决 2）与 ADR-0043 实施 PR（文件仍三段格式，U-13）。
- 预期表结构 diff：无 DDL 变化（Java、登记文件变化）。
- 验收要点：登录成功与失败后 `detail` 不含 `address=`、`agent=`、IP 串；`source_address` 仍有值；例外登记含到期日；静态扫描禁止写入点拼 `address=`、`getMessage()` 直写（OV 建议新增规则 5，守护归 one-quality）。
- 依据：门禁 C4、E1；ADR-0043 第 4 项第 1、5 条、U-7；ADR-0026 第 7 项第 3 条。

#### TB-02　IR-1　高（OV 确认）　整份应答正文落库 24 小时，无分级、无内容边界、无长度上限
- 问题：`IdempotencyFilter` 对所有非 `/sessions`、`/login-states` 的 POST 保存完整应答正文（黑名单），`response_text` 是任意接口应答副本；`data-classification.txt` 无该列。
- 证据：TS `IdempotencyFilter.java:113-125`（`ContentCachingResponseWrapper` 全量读取，:122-123）；`IdempotencyScope.java`；迁移 `:12,:28`；OV：当前未发现返回密钥或 `SENSITIVE` 列的 POST（审计导出是 GET），属前向风险。
- 改法（OV 建议）：
  1. `IdempotencyScope` 改白名单：只对登记过「应答不含 PI 直接标识与 `SENSITIVE` 列」的接口生效，白名单登记处与形式待核（注册表或 `sensitive-response-exceptions.txt` 旁新增清单，实施者核对现有守护后选一）
  2. `response_text` 登记 `INTERNAL + PI`（保守，OV），文件三段格式前第二维写本卡表内
  3. 加 `ck_idempotent_responses_response_text`：`char_length(response_text) <= N`，N 待核（取现有应答最大值加余量，须有测试证据）；超限时失败并告警，不静默截断
  4. 中长期改为只存资源 ID 与状态、重放时重读，不在本卡
- 预期表结构 diff：`platform_idempotency.idempotent_responses`：新增 `response_text` 长度 CHECK。
- 验收要点：白名单外接口不写 `response_text`；白名单内接口重放一致；超长应答不被存且有告警。
- 依据：门禁 C4、O8；ADR-0043 第 4 项；ADR-0026 第 7 项第 3 条。

#### TB-03　IR-2 + IR-3 + IR-4　中　幂等表的登记、格式与成对约束
- 问题：注册表登记 `INSERT_DELETE`，实际 `finish` 有条件更新；`request_hash`、`idempotency_key`、`response_number` 无格式、长度、范围约束；「处理中」状态四列无成对约束。
- 证据：TS 注册表 `:222`、`JdbcIdempotentResponses.java:64-72`、`unversioned-table-writes.txt:22`（IR-2）；迁移 `:13,:24,:29`、过滤器 `MAX_KEY_LENGTH = 128`（IR-3）；迁移 `:27-29`（IR-4）。OV 未单独抽查，按 TS 证据取。
- 改法：
  - 注册表 `idempotent_responses` 改 `CONDITIONAL_UPDATE`，前置条件 `response_number IS NULL`
  - `request_hash ~ '^[0-9a-f]{64}$'`；`char_length(idempotency_key) BETWEEN 1 AND 128`；`response_number BETWEEN 100 AND 599`
  - IR-4：`response_number IS NOT NULL OR (response_name IS NULL AND response_text IS NULL AND response_header_text IS NULL)`
  - `response_header_text` 改 `jsonb` 加 `jsonb_typeof = 'object'` 与 `response_number` / `response_name` 改名（`response_status_code`、`response_content_type`）：改名若已在 `ddl-naming-baseline.txt` 排入 R-2a 则不含，否则新词先查 `terms.yaml`（待核）
- 预期表结构 diff：`idempotent_responses`：新增上述具名 CHECK；改名与 `jsonb` 视待核结论。
- 验收要点：首次请求、重放、处理中冲突三态通过；非法哈希、空键、状态码 99 被拒。
- 依据：门禁 O5、O7、O8、O9；D4；NAMING_STANDARD §2.4a-5、7；SS §4 第 9 组（`response_number:10` / `response_name:11` / `response_text:12` / `response_header_text:13` 同空同非空，处理中为全空）、§1（`idempotency_key:8` 注释「至多 128 个可见字符」库内无 CHECK）。

#### TB-04　AE-5　中（OV 确认）　导出先做无上限 `count`
- 问题：`ExportAuditEntries` 先 `count(filters)` 再 `findAll(…, maxRows)`，`AuditSearch` 不强制时间范围，无过滤时对整张 10 年表计数。
- 证据：TS `ExportAuditEntries.java:43`、`JdbcAuditEntryQueries.java:57`；OV：建议 `limit maxRows + 1` 判超限。
- 改法：导出改 `limit maxRows + 1`，返回行数大于 `maxRows` 即判超限并按现有超限错误失败，去掉 `count`；索引改造（`(actor_id, created_at DESC, id DESC)` 等、`starts_with` 的 `text_pattern_ops`）与 AE-2 分区同批做，归 TB-07（分区后索引需重建），不在本条。
- 预期表结构 diff：无。
- 验收要点：行数恰为 `maxRows` 时成功，`maxRows + 1` 时失败；无 `count` 语句。
- 依据：门禁 O10；D19。

#### TB-05　AE-4（子集）　中　`audit_entries` 文本列无长度与空串约束
- 问题：`action`、`subject_key`、`detail`、`change_reason`、`before_text`、`after_text`、`source_address` 库内无长度上限，应用只限 2000（`AuditEntry.java:16`），也未禁空串。
- 证据：TS AE-4④；P5 已改 `subject_key` 为 `subject_kind` + `subject_id`（OV「AE-1 已被 P5 覆盖」），实施前对照 P5 列名。
- 改法：对仍存在的文本列加 `char_length <= 2000` 与 `<> ''` 的具名 CHECK；`source_address` 另设较小上限（IPv6 文本最长 45，待核该列是否存非地址取值）。改名（`action` → `action_code`、`detail` → `detail_text` 等，`ddl-naming-baseline.txt:10,11,43,68-71`）归 R-2a；`action_code` 格式 CHECK 待先定格式（事件审计写 `cost.timesheet.TimesheetSubmitted`，直接审计写 `platform.identity.role.created`）。
- 预期表结构 diff：`audit_entries`：新增各文本列 CHECK。
- 验收要点：2001 字符与空串被拒；事件审计与直接审计写入通过。
- 依据：门禁 O8；NAMING_STANDARD §2.4a-5。

#### TB-06　AB-2 + AB-4　中（AB-4 统一分级：低→中）　`audit_archive_batches` 批次对象不重复、哈希格式未约束；触发器命名
- 问题：只有 `last_entry_id` 唯一，`object_key` 无唯一，`sha256_hash` 无格式 CHECK；区间不重叠无法用唯一约束表达。
- 证据：TS 迁移 `:75-76,:84`；P5 批次表 `:69-81` 已加 `first/last_entry_created_at` 与 `ck_audit_archive_batches_range`（OV「AB-1 已被 P5 覆盖」），实施前对照 P5。
- 改法：加 `uk_audit_archive_batches_first_entry_id`、`uk_audit_archive_batches_object_key`、`sha256_hash ~ '^[0-9a-f]{64}$'` 的具名 CHECK；注册表写明「区间不重叠、无缺口由对账承担」。AB-4：本表触发器用 `tg_` 前缀（迁移 `:95-98`），与 `audit_entries_reject_*` 风格不一，两处统一（改名随 R-2a 命名卡的触发器部分，待核是否已排）；保留期登记不一致（注册表 `PERMANENT:216` 对 GUIDING §6「归档保留至满 10 年」）属待用户（第 5 节第 2 条），本条不落。
- 预期表结构 diff：`audit_archive_batches`：新增两个唯一约束、一条 CHECK（触发器改名视待核结论）。
- 验收要点：归档作业与对账通过；重复 `object_key` 被拒。
- 依据：门禁 B5、O4、O8。

#### TB-07　AE-2　高（用户已定；ADR-0048 已采纳，2026-10-16；**须在基线关闭前做，属生产前闸门**）　`audit_entries` 按月分区，主键与去重键含分区键
- 问题：GUIDING_PRINCIPLES §11 O-8 已定 `audit_entries` 按月分区、分区归档并校验后由专用维护角色删除在线分区（只追加约束的登记例外）；基线是非分区表，`retention: 3y` 实际是「永久在线」，含 `source_address`（PI）的表按 ADR-0043 R-2 不得实质永久；NAMING_STANDARD §2.4a-8 要求建表时定分区键，上线后再分区要重写全表。分区表主键与唯一约束必须含分区键，与 ADR-0033「单列 uuid 主键」冲突，`uk_audit_entries_event_id` 无法跨分区保证事件幂等。
- 证据：TS AE-2（迁移 `:6-28` 无分区、`:47-61` 触发器禁 UPDATE / DELETE / TRUNCATE；`GUIDING_PRINCIPLES.md` §6 审计行、§11 O-8；注册表 `:207`；`AuditArchiveJob.java` 类注释；`JdbcAuditEntryRepository.saveForEvent` 的 `on conflict (event_id)`）；OV「高风险项判断 TS 第 1 条」。用户决定：intent `2026-10-15-020820`「接受，登记为唯一例外」，落点「主键 (id, created_at)、去重键 (event_id, created_at)，id 仍全局唯一 UUIDv7、引用只用 id；主键守护登记此唯一例外，新增分区表须另行批准」；2026-10-16 用户答「知道了，ADR 成文后我再确认」（收件箱 2026-10-16-one-data-audit-partition；intent `2026-10-16-085331-one-data-审计表按月分区-知道了-ADR.md`）。ADR-0048 由 one-architecture 起草，本分支 `docs/modernization/adr/` 最大编号为 ADR-0047，尚未成文。
- 改法（按 ADR-0048 与 intent 落点；分区键用 `created_at`，不是 OV 原建议的 `occurred_at`，已被用户决定取代）：
  1. `PARTITION BY RANGE (created_at)` 按月；主键 `(id, created_at)`，`id` 仍全局唯一 UUIDv7，引用只用 `id`。
  2. 去重键 `uk_audit_entries_event_id_created_at UNIQUE (event_id, created_at)` 取代 `uk_audit_entries_event_id`；`saveForEvent` 的 `ON CONFLICT (event_id)` 改 `(event_id, created_at)`。**待核（关键，交 ADR-0048 回答）**：去重键含 `created_at`，同一事件重放时 `created_at` 必须与首次相同才会冲突；现 `created_at` 注释写「来自注入的时钟」（SS §5：aud 列注释），若事件审计仍取写入时刻，重放会写出第二行，去重失效。须明确事件审计的 `created_at` 取信封发生时刻（确定性），或另有幂等手段；未答复前不开工。 **ADR-0048 决定 2 已回答**：事件条目的 `created_at` 一律取事件信封的发生时刻（现状如此，定为规则），同一事件重投时两列都相同、必落同一分区；为使前提成立，基线 `created_at` 的 `DEFAULT now()` 须删除，或由守护锁住写入侧赋值（写入 SQL 必须显式给 `created_at`），二者由本卡选一（建议删 DEFAULT，与 TB-09 同做）。直接审计条目 `event_id` 为空，不受此约束。
  3. 主键守护登记此唯一例外（`primary-key-exemptions.txt`，文件名与登记形式待核），新增分区表须另行批准。
  4. 维护角色摘除在线分区，作为只追加触发器的登记例外（O-8）；行级触发器是否随分区继承、TRUNCATE 语句级触发器是否对各分区生效：待核。
  5. 索引按分区重建；TB-04 点名的索引改造（`(actor_id, created_at DESC, id DESC)` 等、`starts_with` 的 `text_pattern_ops`）与本条同批做；`AuditArchiveJob` 按 `(created_at, id)` 推进，与按月分区兼容（OV AE-2）；`audit_archive_batches` 与对账（TS AB-3）随之调整，范围待核。
- 开工条件：无（ADR-0048 已采纳，用户 2026-10-16 批准成文）；须在基线关闭前（首次部署生产前）完成，属生产前闸门（用户「同意，不挡 dev」，one-ops 登记 PREDEPLOY C-15 一组）；本卡其余条目照常做，TB-05 的文本 CHECK、TB-09 与本条改同一张表，合并顺序由 one-1a-next-4 协调。
- 预期表结构 diff：`audit_entries`：改为按月范围分区表；主键 `(id, created_at)`；`uk_audit_entries_event_id` 换为 `uk_audit_entries_event_id_created_at`；索引随分区重建；初始分区与分区创建作业按 ADR-0048。
- 验收要点：同一事件重复投递只落一行；分区边界写入通过；维护角色可摘除分区、其他角色 DELETE 被拒；UPDATE、DELETE、TRUNCATE 触发器对各分区生效；归档作业与对账通过。 守护（ADR-0048「需同步修改的守护规则」1–5）：① `PrimaryKeyTypeGuardTest` 读 `primary-key-exemptions.txt`，除 5 张框架表外只接受 `platform_audit.audit_entries` 一行并校验形状（主键恰为 `id uuid` + `created_at timestamptz`，`created_at` 是范围分区键）；② 分区表守护：出现 `PARTITION BY` 的表必须登记且注明批准它的 ADR 号；③ 引用只用 `id`：任何 `FOREIGN KEY … REFERENCES platform_audit.audit_entries`（含复合外键）失败，`uk_audit_entries_event_id_created_at` 恰为 `(event_id, created_at)`；④ `AuditDomainEventsIT`：同一事件重投两次只产生一条，事件条目 `created_at` 等于信封发生时刻；⑤ `primary-key-exemptions.txt` 文件头注明「非框架表只允许 ADR-0048 的 audit_entries；新增须另立 ADR」。另按 ADR-0048 影响面：SESSION_PLAYBOOK §14.1 D16 注明本例外；NAMING_STANDARD §2.4a 第 8 条的分区登记本表分区键。
- 依据：门禁 O11、O7、C3；GUIDING_PRINCIPLES §11 O-8；NAMING_STANDARD §2.4a-8；ADR-0043 R-2；用户决定（第 5 节「已定」）。

#### TB-08　SS §4 第 8 组　中（待核后落）　`audit_entries` 登录类三列无成对约束
- 证据：SS §4 第 8 组：`source_address:21` / `outcome_kind:22` / `login_method_kind:23` 应同空同非空，且对应登录与会话类 `subject_kind:11`；表内仅 `ck_audit_entries_subject_id:24` 约束了 `subject_id`；SS §3：`source_address`、`outcome_kind`、`login_method_kind` 为「等价措辞」、`on_behalf_of_id`、`event_id`、`correlation_id` 等 7 列无空值说明。
- 改法：待核哪些 `subject_kind`（如登录尝试、会话类）携带这三列：读 `LoginAdmission`、`AuditEntry` 工厂与 `AuditSearch`，核实前不落；候选写法 `(outcome_kind IS NULL) = (login_method_kind IS NULL)`，`source_address` 是否同空另待核（AE-7 对远端地址为空的情形未定）。
- 预期表结构 diff：`audit_entries`：新增 `ck_audit_entries_login_columns`（条数与形态待核）。
- 验收要点：登录成功、失败、拒绝访问与非登录审计各路径通过；缺列行被拒。
- 依据：门禁 B5、O4；D2；SS §4 第 8 组。

#### TB-09　SS §5　低　`audit_entries.created_at` 的 `DEFAULT now()` 与注释「来自注入的时钟」直接矛盾
- 证据：SS §5：`aud.audit_entries.created_at:14 DEFAULT now()`，列注释（aud:39）写「创建时间：即审计事实发生的时间，来自注入的时钟」，全库唯一一处注释与默认值直接矛盾；NAMING_STANDARD §2.4a 第 3 条、`sql-database-clock-exceptions.txt` 文件头。已核 main：应用写入点 `JdbcAuditEntryRepository.java:27,53` 与 `AuditLogIT.java:91` 显式传 `created_at`；`AuditArchiveJobIT.java:76,106,141` 的 INSERT 不带该列、依赖默认值。
- 改法：去掉 `audit_entries.created_at` 的 `DEFAULT now()`（保留 NOT NULL；TB-07 之后它是分区键与主键成员，更须显式写入）；补 `AuditArchiveJobIT` 三处夹具的 `created_at` 值，PR 单列；同表 `updated_at` 与其余各表的 `DEFAULT now()` 属全库口径（PI 待决 10），不在本条。
- 预期表结构 diff：`audit_entries.created_at`：去 `DEFAULT now()`。
- 验收要点：全部写入点显式传时钟；不带 `created_at` 的 INSERT 被拒；`AuditArchiveJob` 相关测试通过。
- 依据：NAMING_STANDARD §2.4a-3；SS §5。

低级顺带（B 部分，不阻塞）：AE-6（注释）、AE-7（待核：`SessionGuardFilter.java:91` 用 `getRemoteAddr()`，是否已用 Tomcat remote-ip 还原）、IR 注释、SL-1、SL-2、X-3（只追加表的冗余 `updated_at`）。

## 5. 已定与待用户决定

### 已定（用户决定）
1. 审计分区：**已定**——用户 2026-10-15 定主键 `(id, created_at)`、去重键 `(event_id, created_at)`、登记唯一例外（intent `2026-10-15-020820`）；2026-10-16 批准 ADR-0048 成文（见 ADR-0048 状态行；ADR-0048 现在 one-architecture 分支 `claude/one-architecture-step3-answers`，合入 main 前以该分支为准）。本卡 TB-07 依此实施，不再待用户决定。
2. WY-1 `calendar_scope_kind` 由 R-1b 挪到 W2：用户答「追认：W2 随日历版本化完成，基线关闭前必须完成」（收件箱 2026-10-16-one-data-wy1-calendar-scope；intent `2026-10-16-085332-one-data-日历-calendar_scop.md`）；NEXT_BATCH_PLAN 第 299 行补注出处由 one-architecture 做（intent 落点），故原待决「由 one-coordinator 补一行出处」不再列用户项。见第 6 节。

### 待用户决定
1. 保留期：`timesheets`（是否随会计档案，T-7 统一分级为中）、`consumed_events` 90 天、`idempotent_responses` 24 小时、`work_calendar_*`、`audit_archive_batches`、`audit_entries` 在线行与 `source_address` 删除时点（TS 待决 4）。
2. 分级：`idempotent_responses.response_text` 级别与 PI 标记；`detail`、`before_text`、`after_text`、`change_reason` 的 PI 标记；浏览器标识 `agent=` 是否属个人信息；工时数据是否按 `PI` 登记（TS 待决 5）。
3. U-7b：`OperationAttestation` 的操作人、见证人是改写员工 ID 还是保留姓名并登记例外；例外到期日（TS 待决 6）。
4. 工时单部门口径（T-4，TS 待决 3）：取工作日当天的部门还是提交当时的部门（后者须写规则卡）。
5. 日历版本化时点与确认人（WY-2，TS 待决 8；时点已由上面第 2 条已定约束为「基线关闭前」，确认人仍待定）。

## 6. 不含（已有归属，本卡不起条目）
| 项 | 归属 |
|---|---|
| T-1（高；原阻断）「员工 × 工作日」唯一 | 并入 TS-UNIQ（one-1a-par 另卡）；用户已定无谓词、对所有状态生效（intent `2026-10-16-052923`、one-1a-par 画像 :58），`uk_timesheets_employee_id_work_date` |
| T-2 状态集合与事件名、`REJECTED` 终态、新建分支先查业务错误 | TS-UNIQ（OV「T-1 / T-2」） |
| X-6 `timesheets` 记 `numbering_rule_version_id` | 并入 TS-UNIQ |
| T-6 `ix_timesheets_employee_id` 冗余 | TS-UNIQ 替换索引时一并删 |
| WY-1（中）`calendar_scope_kind` | W2 日历版本化（DA-17）；**用户已追认**「W2 随日历版本化完成，基线关闭前必须完成」（intent `2026-10-16-085332-one-data-日历-calendar_scop.md`，收件箱 2026-10-16-one-data-wy1-calendar-scope）；W2 卡按此约束写；生产前闸门（见 README） |
| WY-2（高）日历版本化、使用方记 `work_calendar_version_id` | W2 K-2（DA-17）；OV 建议版本表照搬 `numbering_rule_versions` 形态并补 `decision_ref` |
| WY-3（高）日历事件 `aggregateId` 为文本 | W2；SS：main 的 P5 已改 `EventDraft`，日历聚合已不再用 `asText`，待 P5 合并后核（待核） |
| WD-2 例外日期必须在所属年度内 | 随 WY-2 版本表一并处理 |
| AE-1（高；原阻断）`subject_key` 拆列 | 已被 R-1b P5 覆盖 |
| AB-1（中；原阻断）归档按 UUID 顺序 | 已被 P5 覆盖（`first/last_entry_created_at`、按 `(created_at, id)`） |
| AB-3 对账计数 | 随 TB-07（AE-2 分区）一并调整，见 TB-07 第 5 步 |
| AE-4 改名、X-1 内联 CHECK 具名（中，SS §6 里 aud 2 处、cts 1 处、att 1 处） | R-2a / G-D12 |
| 分级文件四段格式 | ADR-0043 实施 PR |

## 7. 验证办法
- 守护：`DdlNamingTest`、`DataArchitectureTest`、`GlossaryTest`、分级文件守护全绿；新增约束全部具名；不新增例外清单条目（TB-01 新增的 U-7b 例外须有 ADR-0043 出处与到期日）。
- 测试：TA-01、TA-02、TB-02 至 TB-06、TB-08、TB-09 各一条；TB-07（批准后）加「同一事件重复投递只落一行」与分区摘除权限测试「违反即被拒、合法即通过」集成测试；TB-01 的登录审计 `detail` 断言（登录成功、失败、拒绝访问）；TB-04 的 `maxRows` 与 `maxRows + 1`；`TimesheetStateMachineTest`、`AuditArchiveJob` 相关测试原样重跑；测试名以 `DATA-REPAIR-timesheet-audit TA-nn` 或 `TB-nn` 开头。
- 表结构 diff：两个空库分别执行改动前、改动后的基线，`pg_dump --schema-only` 对比，A、B 部分各做一次；预期差异只能是第 4 节所列。不得出现：`timesheets` 的唯一约束与列变化（归 TS-UNIQ）；`audit_entries` 的分区、主键、索引变化（TB-07 在 ADR-0048 批准前不做，批准后才允许出现）；种子数据变化。
- `scripts/precheck.sh <基线>` 全部 PASS 或有理由的 SKIP，输出贴进两个 PR。

## 8. 执行者与复审
- 执行者：sonnet 子代理；TB-01、TB-02、TB-07 首轮复审用 opus（个人信息与敏感数据复制点，安全判定）；其余 sonnet 复核。
- 工作区：独立 worktree，A 部分分支 `claude/one-1a-par-data-repair-timesheet`，B 部分分支 `claude/one-1a-next-4-data-repair-audit`；基线为 P5 与 TS-UNIQ（A 部分）合并后的 main。
- 报告：A 部分向 one-1a-par、B 部分向 one-1a-next-4，均抄 one-coordinator，分开写「已验证」与「未运行」。

## 9. 禁止事项
- 不放宽、不删除断言；不改验收测试目录；不改卡外文件；不顺手重构、改名或统一风格。
- 不动 `timesheets` 的唯一约束与状态集合（归 TS-UNIQ）；TB-07 之外不动 `audit_entries` 分区与主键，TB-07 依 ADR-0048（已采纳）在基线关闭前做；不用部分唯一索引。
- TB-02 超长应答不得静默截断；TB-01 不得为通过测试把 IP 换个字段名继续写进 `detail`。
- 不追加迁移（ADR-0038）；不改 kernel 共享构件。
- 不联网、不起容器（Testcontainers 白名单镜像除外）、不建账号；不打印、不写入密钥。
- 缺失或无效输入失败并告警，不静默跳过。

## 10. 机械类低级项（按表列出，不逐条展开；列清单与行号见 `review-parts/SCAN_SUPPLEMENT.md`）

SS 行号取 `origin/main`。同一类改法一致，由执行者按表一次改完，每类一条验收即可。无长度上限为中，其余为低；§1 虽为中，因机械且同一改法，一并列此。

| SS 节 | 类 | 级别 | 属本卡 schema 的范围 |
|---|---|---|---|
| §1 | text 列无长度与格式约束 | 中 | cts 2 列（`timesheets.timesheet_number:17`、`consumed_events.consumer_name:49`）；aud 8 列：audit_entries 6（`action:8`、`detail:13`、`change_reason:18`、`before_text:19`、`after_text:20`、`source_address:21`，已由 TB-05 覆盖）、audit_archive_batches 2（`object_key:74`、`sha256_hash:75`，TB-06）；idm 5 列（`idempotency_key:8` 注释「至多 128」库内无 CHECK、`request_hash:9`、`response_name:11`、`response_text:12`、`response_header_text:13`，TB-02、TB-03）；共 15 列；att 无 |
| §2 | 业务默认值 | 低 | 本卡 schema 无 |
| §3 | 可空列缺「为空表示」注释 | 低 | 「无空值说明」共 10 列：aud 7（`audit_entries.on_behalf_of_id:10`、`detail:13`、`event_id:16`、`correlation_id:17`、`change_reason:18`、`before_text:19`、`after_text:20`）；idm 3（`response_name:11`、`response_text:12`、`response_header_text:13`）；「等价措辞」cts `timesheets.submitted_date:19`、aud 4 列、idm `response_number:10` 另待 one-quality 判守护是否接受 |
| §4 | 成对可空列缺 CHECK（中） | 中 | 第 8 组 → TB-08；第 9 组 → TB-03（IR-4）；第 14 组 → TA-01 |
| §5 | `DEFAULT now()` 与「应用注入时钟」矛盾 | 低 | cts 2 表 5 列（`timesheets` created/updated、`consumed_events` 的 `consumed_at`、created、updated）；att 2 表 4 列；aud 2 表 4 列（`audit_entries.created_at` 单列 TB-09）；idm 1 表 2 列；共 7 表 15 列；全库口径另卡（PI 待决 10） |
| §6 | 内联枚举 CHECK 未具名 | 低 | cts `timesheets.status:11`（随 TS-UNIQ 的状态集合改，不单做）；att `work_calendar_days.day_kind:29`；aud `audit_entries.outcome_kind:22`、`login_method_kind:23`；共 4 处；随 G-D12 一次改基线 |

### 保留期与匿名化（用户已定「按 one-data 建议」，见 `../RETENTION_PROPOSAL.md`）

- `timesheets`：10 年，归档后在线删除；若财务认定为会计档案原始凭证，改 30 年（待财务确认）。
- `idempotent_responses`：24 小时，注册表写明 `24h`。
- `audit_archive_batches`：保留至其所含最后一条审计满 10 年（随 ADR-0048 分区归档一起改）。
