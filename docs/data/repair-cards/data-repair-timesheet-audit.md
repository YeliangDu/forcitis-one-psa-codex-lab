---
卡号: DATA-REPAIR-timesheet-audit
状态: 进行中
负责会话: one-1a-par
前置: R-1b-P5, TS-UNIQ
---
# 派工卡 DATA-REPAIR-timesheet-audit：工时、考勤日历、审计、幂等已建表的数据设计缺陷修复（草稿）

- 草稿（第二版）：one-data 起草助手，2026-10-16；第二版并入用户 2026-10-16 的决定、统一分级（阻断 0）与 `SCAN_SUPPLEMENT.md` 补入；待 one-data 终审、one-coordinator 派工。
- 证据来源：`docs/data/table-reviews/timesheet-attendance-audit-idem-sched.md`（下称 TS）与 `OPUS_VERIFY.md`（下称 OV）；以 OV 为准。「:N」指 TS 审查时 main 基线对应迁移行：`cost_timesheet/V20261014140000__cost_timesheet_baseline.sql`、`organization_attendance/V20261014141000__organization_attendance_baseline.sql`、`platform_audit/V20261014134000__platform_audit_baseline.sql`、`platform_idempotency/V20261014221000__platform_idempotency_baseline.sql`；R-1b P5 已改审计两表，实施前重核。
- `SCAN_SUPPLEMENT.md`（下称 SS）：全量机械扫描与补审，行号取 `origin/main`（a27bf877），与 TS 旧行号可能差几行。
- 级别以统一分级为准（`DATA_DESIGN_GATE.md` §4a、`BASELINE_TABLE_REVIEW.md` §1、§3）：阻断 0；T-1、AE-1 由阻断归高（有归属、不起条目）；WY-1、AB-1 降中（WY-1 用户已追认时点偏离，AB-1 P5 已解决）；IR-1 高；T-7、AB-4、IR-4 由低升中；WY-3 由中升高（不起条目，归 W2）。
- 「待核」表示分报告与复核报告都没有给出证据，实施者先核实再改。

## 1. 卡号与类别
- 卡号：DATA-REPAIR-timesheet-audit
- 类别：缺陷修复（ 第 9 条）
- 批次：已建表数据设计审查修复批（门禁 v2 §1.2 第 6 条）；两部分各开一个小 PR，直接合入 main
- 建议负责会话：**A 部分（工时与日历）one-1a-par；B 部分（审计与幂等）one-1a-next-4**
- 顺序：A 部分的 TA-01 依赖 TS-UNIQ 定稿的状态集合，须在 TS-UNIQ 合并后开工；B 部分不依赖 A 部分；B 部分的 TB-07（分区）**依 ADR-0048（已采纳）实施，须在基线关闭前做，属生产前闸门**，其余条目不受影响
- 排期：本卡排在 dev 部署之后（用户「同意，不挡 dev」，`BASELINE_TABLE_REVIEW.md` §3 末段）；本卡本身不是闸门；审计分区（TB-07）、W2 日历版本化、ADR-0043 实施 PR 是生产前闸门（one-ops 登记到 PREDEPLOY，C-15 一组，见 README）

## 2. 依据
- 门禁 v2：O3、O4、O8、O10、O7、C3、C4、E1、B3、B5
- DATA_DESIGN_CHECKLIST §14.1 D2、D4、D8、D12、D13、D19；§12.2；ADR-0038；ADR-0026 第 7 项第 3 条；ADR-0033 第 1 项第 10 条、第 7 项；ADR-0043 第 3、4 项、U-7(b)、U-7(c)、R-2、U-13
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
- 问题：用户已认可 `timesheets` 保留 10 年、归档到对象存储后在线删除（`docs/data/retention-proposal.md`，用户「按 one-data 建议」）；注册表现为 `BUSINESS_RECORD`（无期限），没有清理或归档作业；GUIDING_PRINCIPLES §6 无该行。TS-UNIQ 不改保留期，故不阻塞该卡；本条是承接。
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
  2. U-7b 用户已答（2026-10-16）「改写员工 ID，阅读时再解析姓名」：`OperationAttestation` 的操作人、见证人改写员工 ID（运维命令入参 `--operator`、`--witness` 改为员工 ID），`detail` 记 `operatorId=`、`witnessId=`；**不建**例外登记与到期日守护（one-data 更正，见 `reviews/DATA-REPAIR-timesheet-audit-TB-01-02.md` 末尾）。运维面（INFRA_RUNBOOK §6、C-5、PREDEPLOY C-13）已同步，交 one-ops 复核。
  3. 本卡内写「允许写入内容清单」草稿（`detail`、`before_text`、`after_text`、`change_reason`：字段名、来源、是否可能含个人信息；U-7(b) 允许 `before_text` / `after_text` 记被变更字段自身的 L2 个人信息原值），**待核**并交用户确认；四列的访问级别与 PI 标记登记待用户（第 5 节待决 2）与 ADR-0043 实施 PR（文件仍三段格式，U-13）。
- 预期表结构 diff：无 DDL 变化（Java、登记文件变化）。
- 验收要点：登录成功与失败后 `detail` 不含 `address=`、`agent=`、IP 串；`source_address` 仍有值；U-7b 例外已取消，无例外登记；静态扫描禁止写入点拼 `address=`、`getMessage()` 直写，姓名与联系方式在审计文本中一律不允许（OV 建议新增规则 5，守护归 one-quality）。
- 依据：门禁 C4、E1；ADR-0043 第 4 项第 1、5 条、U-7；ADR-0026 第 7 项第 3 条。
- **数据设计（one-1a-next-4 补，待 one-data 签）**（基线：`origin/claude/one-1a-next-4-data-pr2`；写入点以 `git grep "new AuditEntry("` 与 `AuditEntry.withChange` 核实，`new AuditEntry(` 共 41 处、35 个文件，`withChange` 仅 `AuditDomainEvents.java:56` 一处）
  - **① U-7b 例外登记：已取消**（用户答改写员工 ID，不再有例外；`audit-detail-exceptions.txt` 与到期日守护不做）。原例外说明已删。
    - 守护：`AuditDetailGuardTest`（`backend/src/test/java/com/forcitis/one/`，源码扫描）：`new AuditEntry(`、`.reject(`、`new Outcome.Failed(` 的实参与 `describe()` 方法体里，不得出现 `address=`、`agent=`、`loginName=`、`operator=`、`witness=` 拼接，`.address()`、`userAgent()`、`getRemoteAddr()`、`loginName`、`wecomUserId`，不得把登录名、企业微信 ID 拼在 `login name`、`WeCom user id` 之后；没有例外。
  - **② 允许写入内容清单**（`detail`、`before_text`、`after_text`、`change_reason`；PI 栏按 ADR-0043 口径：`PI` = 含直接标识或可能含；`代理键` = 只含自有 UUID，仍是 PI 但按第 4 项第 2 条允许；`否` = 无个人信息）

| # | 列 | 写入点（类:行） | 来源与示例形态 | 个人信息 | 处置 |
|---|---|---|---|---|---|
| 1 | detail | `GetOrgUnit:42`、`GetLegalEntityAttribution:50`、`ReplaceAccountRoles:67`、`UpdateRole:63`、`DeleteRole:49`、`ConfirmIdentity:42`、`Logout:35`、`Retraction:97`、`StartApproval:73`、`ReassignTask:51`、`ListAllTasks:40`、`OwnAttributionGuard:56` | 代码内固定英文短语，如 `outside the scope of the caller`、`nobody changes their own roles`、`logout by the user`、`not the initiator`、`reassign`、`list`、`the account names no employee`、`own legal entity attribution` | 否 | 允许 |
| 2 | detail | `ExitAdministratorMode:42`、`ListAllTasks:43`（`overdue only` 或空） | 空值或固定短语 | 否 | 允许 |
| 3 | detail | `TaskGuard:53` | 方法入参 `action`（调用方写死的动作名，如 `claim`） | 否 | 允许 |
| 4 | detail | `CorrectLegalEntityAttribution:51`、`EnterAdministratorMode:79`、`LoginAdmission:88`、`SessionGuardFilter:92`、`PurgeExpiredSessions:42`、`HandleOrgSnapshotChange:114` | 固定短语加权限码、时刻或枚举：`the caller lacks <权限码>`、`until <Instant>`、`administrator mode until <Instant>`、`expired at <Instant>`、`cause=<枚举>`、`role=<角色码> cause=separation` | 否 | 允许 |
| 5 | detail | `HandleOrgSnapshotChange:91`、`SessionRevocation:23`、`EnterAdministratorMode:87` | 调用方传入的固定原因短语（`separated in the organisation snapshot`、`the account was disabled`、`the roles of the account were replaced`）；`EnterAdministratorMode:87` 取 `WecomIdentification.Outcome.Failed.reason()`（固定短语，但见第 14 行） | 否（`EnterAdministratorMode:87` 随第 14 行修后为否） | 允许 |
| 6 | detail | `ReconcileEmergencyAdmins:66` | `the emergency account of user <UUID> is bound to …`；`only n usable emergency account(s) …` | 代理键 | 允许 |
| 7 | detail | `AccessDeniedAudit:77`、`PrivilegedRequestAudit:44` | `RequestLabel.of`：`METHOD <路由模板> key=<规范 UUID>`（只取规范 UUID 路径变量，不取原路径）；或 `类名.方法名` | 代理键 | 允许 |
| 8 | detail | `SearchAuditEntries:40`、`ExportAuditEntries:51` | `returned=n filter=actorId=<UUID>,action=…,subjectId=<UUID>,from=…`（`AuditSearch.describe()`） | 代理键；其中 `sourceAddress=<IP>` 为 PI 直接标识 | 允许，但 `sourceAddress` 条件须删出 `describe()`（ADR-0043 第 4 项第 5 条、U-7(c)） |
| 9 | detail | `AuditDomainEvents:55`（事件型审计） | 事件实现 `AuditDetail` 时取 `auditDetail()`，目前仅 `WorkCalendarLockedDaysChanged`：`<日期> <前状态> -> <后状态>; …`；否则 `by <系统执行者名称>`（`SystemActors`，登记在 `system-actors.txt`）；人为执行者为空 | 否 | 允许；新增 `AuditDetail` 实现须在卡上追加一行 |
| 10 | detail | `LoginAdmission:76`、`:100`（`describe()`，`:108-114`） | `method=<登录方式> address=<IP> reason=<原因> agent=<浏览器标识，截至 200 字>` | **PI**（IP；`agent=` 是否属个人信息见第 5 节待决 2） | 禁写 `address=`、`agent=`；只留 `method=…` 与 `reason=…`；IP 仍在 `source_address` 列 |
| 11 | detail | `BrowserIdentity:72`、`:81` | `risk=HIGH identifier=<哈希前 8 位> …`；`outcome=SKIPPED\|CONFIRMATION_REQUIRED reason=<枚举> identifier=<前 8 位>` | 不确定：`identifier_hash` 登记为 PI（ADR-0043 第 3 项表），8 位前缀是其截断 | 建议允许（截断后无法还原、不能单独指向某人），请 one-data 确认 |
| 12 | detail | `LoginAdmission:100` 的 `reason`，来自 `LoginWithSso:65-66` | `no employee for login name <登录名>`（登录名由请求方输入，可能是任何字符串） | **PI**（登录名，ADR-0043 第 3 项表 U-2） | 禁；改 `login name unknown`。**本项不在 ADR 第 4 项第 5 条三处之列，属本次核实新发现** |
| 13 | detail | `LoginAdmission:100` 的 `reason`，来自 `WecomIdentification:57-58`（经 `LoginWithWecom:28`） | `no employee for WeCom user id <企业微信 ID>` | **PI**（企业微信 ID） | 禁；改 `wecom user not bound`。**新发现** |
| 14 | detail | `RegisterEmergencyAdmin:79` | `operator=<姓名> witness=<姓名> loginName=<登录名>` | 代理键（姓名已改员工 ID） | `loginName=` 删，主体已是应急账号 ID（`AuditSubject`），读时再解析。**新发现** |
| 15 | detail | `GrantRoleByOperations:68`、`EnableEmergencyAdmin:43`、`DisableEmergencyAdmin:50`、`ResetEmergencyAdminCredentials:49` | `OperationAttestation.describe()`：`operator=<姓名> witness=<姓名>`（`GrantRoleByOperations` 另加 `role=<角色码>`）；姓名由命令行/运维入参，正则 `[\p{L}\p{N}][\p{L}\p{N} ._-]{0,63}` | 代理键（员工 ID） | 改写员工 ID：`operatorId=<员工 UUID> witnessId=<员工 UUID 或 none>`（单人运维模式省略见证人时写 `none`）；`RevokeRoleByOperations` 同归此行 |
| 16 | detail | `ForceUserLogout:67` | `forced logout: <原因>`，原因由人填，去控制字符，1 至 200 字（`MAX_REASON_LENGTH`） | 可能（人填自由文本） | 允许，与 `change_reason` 同口径：只提示不拦截（ADR-0043 第 4 项第 1 条表） |
| 17 | detail | `RecordSyncRunRelease:38`（调用方 `ReleaseSyncRun:67-70`） | `reason: <人填原因>; session mode: …; held: <UUID 列表>; applied: <UUID 列表>; remaining super_admin: <数>` | 可能（人填原因）；其余代理键与计数 | 允许；原因无长度上限，超 2000 字 `AuditEntry` 构造会抛 `IllegalArgumentException`，见「拿不准」 |
| 18 | change_reason | `AuditDomainEvents:56`（`withChange(reason, null, null)`，唯一调用） | 事件实现 `Reasoned` 时取 `reason().value()`：目前 `SyncRunReleased`、`LegalEntityAttributionCorrected`，人填自由文本 | 可能 | 允许，只提示不拦截（U-7(b)） |
| 19 | before_text / after_text | 无（全库唯一写入点 `AuditDomainEvents:56` 写 `null, null`） | 现在不写 | — | **除第 26 行外一律不得写非空值**；将来写入按 U-7(b)：被变更字段自身的 L2 个人信息原值可写，L3 及以上与 `SPI` 不记，并在本表追加一行 |
| 20 | detail | `SingleOperatorModeStartup`（启动审计 `platform.identity.single-operator-mode.enabled`，ADR-0030 第 1 节第 7 条） | 恰为 `profile=<profile 名>`；actor 为空；不写数据库名 | 否 | 允许（one-data 签发，SINGLE-OP P1） |
| 21 | detail | `SingleOperatorMarks`（放宽项标注 `platform.identity.single-operator-mode.applied`，ADR-0030 第 2 项） | 恰为 `relaxation=<固定代码>`，代码取 `WITNESS_OMITTED`、`EMERGENCY_ACCOUNT_MINIMUM_ONE`、`SUPER_ADMIN_MINIMUM_ONE`（第四个 `TEMPLATE_VERSION_WITHOUT_AUTHOR` 随编号运维起草落地） | 否 | 允许（one-data 签发，SINGLE-OP P2） |
| 22 | detail | `StoreAuditLog`（文件服务检测闸，ADR-0070 决定 2 第 4 条；one-platform-file 2026-10-21 新增，待 one-data 复核） | `sha256=<64 位十六进制> engine=<引擎版本> definitions=<病毒库版本> [threat=<特征名>] [uploader=<ONE_TIME_LINK\|PLATFORM_MODULE>:<自有 ID 或模块代码>]`；拒收为 `reason=<ENCRYPTED（另写 bytes=<字节数> media=<媒体类型> name=<原文件名>）\|SCANNER_UNAVAILABLE\|OBJECT_STORE_UNAVAILABLE\|STAGING_UNAVAILABLE\|QUARANTINE_FAILED> [sha256=…] [threat=…] [uploader=…]`；原文件名只写在加密拒收一条（用户 2026-10-21 决定，intent 2026-10-21-170547；ADR-0043 INTERNAL＋PI，保留期随审计；去控制字符、至多 255 字、像口令的整段不写），其余不写原文件名、链接与异常文本 | 可能（上传人起的文件名） | 允许 |
| 23 | detail | `BindingAuditLog`（文件服务关联、封存、签发下载链接、读取内容、哈希不符，ADR-0060 决定 2；one-platform-file 2026-10-21 单元 6 新增，待 one-data 复核） | `document=<单据类型码>:<单据 ID>`，关联另写 `access=<PUBLIC\|INTERNAL\|SENSITIVE\|RESTRICTED> personal=<NONE\|PI>`，签发链接另写 `validity=<ISO-8601 时长>`，哈希不符另写 `sha256=<64 位十六进制>`；主体不是用户时另写 `by=<ONE_TIME_LINK\|PLATFORM_MODULE>:<自有 ID 或模块代码>`；不写原文件名、链接与异常文本 | 代理键 | 允许 |
| 24 | detail | `PersonnelCommandService`（人事命令录入 `organization.workforce.personnel-command.entered`，ADR-0068 第 1 条；#872，one-design 2026-10-30 签） | 恰为 `command <命令 UUID> <种类枚举> effective <ISO 日期>` | 代理键 | 允许 |
| 25 | detail | `PersonnelCommandService`（人事命令撤回 `organization.workforce.personnel-command.withdrawn`，ADR-0068 第 1 条第 6 项；#872，one-design 2026-10-30 签） | `command <命令 UUID> <种类枚举>; reason: <人填原因>`；原因为 `personnel_commands.withdrawal_reason` 原文的全文（1 至 500 字，列 CHECK），只把控制字符换成空格，不截断 | 可能（人填自由文本）；其余代理键 | 允许，与第 16 行同口径：只提示不拦截（ADR-0043 第 4 项第 1 条表）；不截断，因审计须能还原撤回理由，500 字低于 `AuditEntry` 每段 2000 字上限 |
| 26 | detail、change_reason、before_text / after_text | `EmployeeProfileService`（员工档案修改 `organization.workforce.employee-profile.changed`，#959；one-lead 裁定 2026-11-02，依据 ADR-0043 U-7(b)） | detail 恰为 `profile changed`；change_reason 恰为 `profile change`；before_text / after_text 为被改列的 `列名=值` 以分号相连，只含 `display_name`、`gender`、`email`、`wecom_user_id`（均为 L2，data-classification SENSITIVE）的原值与新值，未改的列不写，**绝不含工号**（L3） | 是（姓名、邮箱、企业微信账号原值） | 允许：U-7(b) 的 L2 个人信息原值例外，仅限本写入点；其余写入点仍一律写 null |

  - 清单共 26 行（`detail` 23 行、`change_reason` 1 行、`before_text` / `after_text` 合 1 行）；覆盖全部 45 处 `new AuditEntry(`。所有写入点另受 `AuditEntry` 构造的既有限制：每段至多 2000 字，含 `password|secret|token|…=` 样式文本即拒绝（`AuditEntry.java:15-25`）。
  - **登记四列分级**（建议，待用户第 5 节待决 2 与 ADR-0043 实施 PR 的四段格式；落地前按本表视为已登记，C4 末句）：
      `platform_audit.audit_entries.detail | INTERNAL | PI | 用途：说明动作的上下文；不得含直接标识（IP、登录名、企业微信 ID）与人名，操作人、见证人只记员工 ID`
      `platform_audit.audit_entries.change_reason | INTERNAL | PI | 用途：人填的变更理由；只提示不拦截`
      `platform_audit.audit_entries.before_text | INTERNAL | PI | 用途：被变更字段的原值；仅 L2 个人信息原值，L3 及以上与 SPI 不记`
      `platform_audit.audit_entries.after_text | INTERNAL | PI | 用途：被变更字段的新值；同 before_text`
  - **守护锁定**（三层）：① 上述 `AuditDetailGuardTest`（拼接点禁字）；② 新增 `AuditWritePointsGuardTest`（沿 `ApplicationIntegrationTest` 对 `public-endpoints.txt` 的双向快照办法）：`src/main` 中 `new AuditEntry(` 与 `withChange(` 的调用点文件集合（35 个）须与新建 `backend/architecture/audit-text-writers.txt`（`<路径> | <本表行号> | <个人信息：否\|代理键\|可能\|PI>`）逐行一致，新增写入点必须登记其所属行；③ 测试（`TB-01 …` 开头；与 `LoginAuditDetailTest` 重复故不另建 `LoginAuditDetailIT`，并入 `SsoLoginIntegrationTest`、`WecomLoginIntegrationTest`、`AuditSearchDescribeTest`）：登录成功、失败、拒绝访问后 `detail` 不含 `address=`、`agent=`、IP 串、登录名，`source_address` 有值；检索与导出审计 `detail` 不含 `sourceAddress=`。
  - **拿不准的点**（请 one-data 裁）：(1) 已取消：U-7b 例外取消后不再有到期日，`AuditDetailGuardTest` 不读系统日期。(2) 第 12、13、14 行是本次读代码新发现的直接标识写入，ADR-0043 第 4 项第 5 条只列三处，是否并入本条或另起条目，请裁。(3) 第 11 行哈希前 8 位按「允许」写的，属判断。(4) 第 17 行 `ReleaseSyncRun` 的人填原因无长度限制，超 2000 字时审计构造抛异常发生在放行之后，建议在 `ReleaseSyncRun` 入口限 1 至 200 字（与 `ForceUserLogout` 一致），本卡未列此改法，请裁是否加。

- RULING-DR-TB01-1（one-1a-next-4，2026-10-16）：用户答 U-7b「改写员工 ID，阅读时再解析姓名」后，运维命令 `--operator`、`--witness` 入参改为员工 ID；5 个验收文件中这两个参数的取值随之机械改为员工 ID，断言未动。

- 后续小卡 DR-TB01-FU1（one-data 2026-10-16 准许移出 PR-2，交 one-1a-next-5）：运维证明的操作人、见证人员工 ID 须为存在员工（不存在 → 退出码 1，不写审计）；需在 5 个验收文件造出操作人 / 见证人员工（测试代理按 Acceptance-Ruling 改）。PR-2 暂未校验存在，「阅读时再解析姓名」可能遇到解析不到的 ID。退出码 1（one-data 2026-10-16 裁定：与格式错误同为拒绝，沿用错误码 PLATFORM_IDENTITY_OPERATION_WITNESS_REQUIRED）；已在 one-1a-next-5 落地。「员工存在」口径见 one-data `reviews/DR-TB01-FU1-1.md`（存在 = employees 有该 ID 的行，不要求任职、在职；现实现联接任职偏严，随 NITS-1 修）。

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
- **数据设计（one-1a-next-4 补，待 one-data 签）**（基线同上；入口 `IdempotencyFilter.java:64-72`，范围判定 `IdempotencyScope.applies`，OpenAPI 的 `Idempotency-Key` 参数也由它决定（`OpenApiProblems.java:64`），所以「标了幂等」即 `applies` 为真）
  - **① N = `response_text` 长度上限：N = 32768（字符，`char_length`）**
    - 现状核实：过滤器对带 `Idempotency-Key` 且状态码小于 500 的应答全量保存（含 4xx 的 `problem+json`）。`@PostMapping` 共 34 个，其中 `/sessions…`（8 个）与 `/login-states`（1 个）被范围排除（实际是 `path.contains("/sessions")`，含 `AdministratorModeController` 的 `sessions:enter/exit-administrator-mode`），余 25 个在范围内；其中 3 个 `SourceReferenceController`、`:reconcile`、`SyncRunController :release` 按下面第 ② 条移出白名单，**白名单 20 个**。
    - 白名单内各接口最大应答（按响应 record 字段上限估算，未起容器实测；估算依据是字段约束，不是样例）：
      `RoleResponse`（`POST /roles`）最大：`code` ≤ 64（`Role.CODE`）、`name` ≤ 100（`Role.java:173`）、`permissionCodes` 现有 27 个权限码合计 1111 字符（含引号逗号，`git grep` 全部权限码常量算得），其余字段约 150，**合计约 1.4K**；这是白名单内现在最大的；
      `NumberingRuleResponse`（含 `pending`，3 个 UUID、4 个日期、模板串）约 0.7K；
      `LegalEntityAttributionResponse` 每个任职期约 118 字符（UUID 36 + 2 个日期 + 键名），周期数无上限，N 可容约 270 个；
      `LegalEntityResponse`（`name` ≤ 100）约 0.3K；`DecisionResponse`、`AwaitingConfirmationResponse` 约 0.1K；4xx `problem+json` 约 0.35K（`ProblemDetails` 文本按 `ErrorKind` 固定，无请求值回显）；其余接口 204 无正文。
    - 取值：现有最大约 1.4K，取 N = 32768（约 23 倍余量）。余量理由：权限码目录增长到约 500 个仍容得下（500 × 70 ≈ 35K 临界，届时本卡守护测试先失败，提醒复核，而不是静默放大）；任职期史以百计也容得下。N 写在 `ck_idempotent_responses_response_text`：`CHECK (char_length(response_text) <= 32768)`，并在 `IdempotencyFilter` 用同一常量（`MAX_RESPONSE_LENGTH = 32768`），测试断言两处相等。
    - 超限（one-data 裁定，2026-10-16）：不截断、不存，**不释放幂等键**（行保持「处理中」，`holdUntilExpiry` 把 `updated_at` 置为 `expires_at`，使重试在 24 小时到期前一直得到处理中冲突 409，且不被「处理中超时 2 分钟后接管」重新执行已提交的写操作）；打 `ALERT platform.idempotency response of <接口模板> exceeds 32768 characters: not stored`，`forcitis.platform.web.idempotency.response.oversize` 计数加一；应答照常发给调用方。
    - 守护：新增测试 `IdempotentResponseSizeTest`（`TB-02 …` 开头）：为每个白名单接口的响应类型构造字段取上限的夹具（`name` 100 字、`permissionCodes` 取当前全部权限码、任职期取 100 个），序列化后长度须小于 N 的一半。
  - **② 白名单登记处与形式：新建 `backend/architecture/idempotent-endpoints.txt`**
    - 选它不选别的：`data-classification.txt` 登记列、`archive-columns.txt` 登记归档列、`data-access-registry.yaml` 登记表与写入者，都不是接口；`sensitive-response-exceptions.txt` 是出参字段的例外，语义相反（它登记「可以暴露」，这里要登记「可以保存」）。形式与对象最接近的是 `public-endpoints.txt`（接口集合，`ApplicationIntegrationTest` 做双向快照比对），沿用它的行格式：`METHOD 路径模板  # 应答类型；核对结论`。
    - `IdempotencyScope.applies(method, path)` 改为：方法为 POST 且路径匹配本文件某行的模板（用 Spring `PathPatternParser`，模板里 `{x}` 与实际路径变量对应）；不在文件里的接口，即使带 `Idempotency-Key` 也直接放行不保存，OpenAPI 也不声明该参数。原「`/sessions`、`/login-states`」两条排除规则作废，由「不在清单」代替。
    - 白名单（20 行，`#` 后为应答类型与核对结论）：

```
POST /api/v1/organization/legal-entities  # LegalEntityResponse(legalEntityId, code, name, status); 无个人信息
POST /api/v1/organization/org-unit-legal-entities/{orgUnitId}:end  # LegalEntityAttributionResponse(kind, subjectId, periods[legalEntityId, 日期]); 全为 UUID 与日期
POST /api/v1/organization/employee-legal-entities/{employeeId}:end  # 同上
POST /api/v1/organization/org-unit-legal-entities/{orgUnitId}:correct  # 同上
POST /api/v1/organization/employee-legal-entities/{employeeId}:correct  # 同上
POST /api/v1/platform/authorization-requests/{requestId}:confirm  # 204 无正文
POST /api/v1/platform/authorization-requests/{requestId}:reject  # 204 无正文
POST /api/v1/platform/user-accounts/{userId}:force-logout  # 204 无正文
POST /api/v1/platform/roles  # RoleResponse(roleId, code, name, kind, enabled, accessScope, administrative, permissionCodes); 201 或 202 AwaitingConfirmationResponse(requestId); 无个人信息
POST /api/v1/platform/user-accounts/{userId}:replace-roles  # 204 或 202 AwaitingConfirmationResponse(requestId)
POST /api/v1/platform/user-accounts/{userId}:enable  # 204 无正文
POST /api/v1/platform/user-accounts/{userId}:disable  # 204 无正文
POST /api/v1/platform/numbering-rules/{documentType}:confirm  # NumberingRuleResponse; authorId、confirmedBy 为用户 UUID(代理键)
POST /api/v1/platform/numbering-rules/{documentType}:confirm-finance  # 同上
POST /api/v1/platform/approval-tasks/{taskId}:claim  # 204 无正文
POST /api/v1/platform/approval-tasks/{taskId}:release  # 204 无正文
POST /api/v1/platform/approval-tasks/{taskId}:decide  # DecisionResponse(instanceId, status)
POST /api/v1/platform/approval-tasks/{taskId}:return  # DecisionResponse
POST /api/v1/platform/approval-tasks/{taskId}:transfer  # 204 无正文
POST /api/v1/platform/approval-tasks/{taskId}:reassign  # 204 无正文
```

    - **移出白名单的 5 个**（保留在下面第 ③ 条清单里，写明原因）：`POST …/organization/sync-runs:reconcile`（`OrgReconciliationResponse` 六个 `List<String>` 随人员与组织数线性增长，单条约 92 字符，没有上限；它本是对账计算，重复调用天然无副作用，不需要幂等）；`POST …/organization/sync-runs/{syncRunId}:release`（`SyncRunResponse.heldSeparations` 是 UUID 列表，无上限，每个约 40 字符；重复发送已由状态判定挡住：非 `HELD` 返回 `NotHeld`）；`POST …/platform/source-references`、`:confirm`、`:reject`（`SourceReferenceResponse.sourceId` 当 `sourceObjectKind=EMPLOYEE` 时是外部系统的员工编号，等同工号类直接标识，ADR-0043 第 3 项表 U-2，不应落库 24 小时；见「拿不准」）。
  - **③ 「应答不含个人信息直接标识与 SENSITIVE 列」接口清单与核对办法**
    - 清单：范围内全部 25 个 POST，逐个给结论（25 行：白名单 20 + 移出 5）：白名单 20 个见上；移出 5 个（`:reconcile` 无界、`:release` 无界、`source-references` 三个含外部员工编号）；范围外 9 个（`/sessions…` 8 个 + `/login-states` 1 个，应答含会话与凭据材料，本来就不存，不入清单）。
    - 核对办法（守护测试，不靠人工）：新增 `IdempotentEndpointGuardTest`（`backend/src/test/java/com/forcitis/one/`）：(1) 用反射枚举全部 `@PostMapping` 接口，算出 `IdempotencyScope.applies` 为真的集合，与 `idempotent-endpoints.txt` 的行集合双向一致，多一行少一行都失败；(2) 对每个白名单接口的返回类型递归展开 record 组件（含 `List<…>` 泛型实参、嵌套 record、`ResponseEntity<…>` 与 `ResponseEntity<?>` 的各分支所用类型），任一组件名（转 snake_case）命中 `data-classification.txt` 中 `SENSITIVE`/`RESTRICTED` 列名，或命中直接标识名单（`display_name`、`email`、`wecom_user_id`、`employee_code`、`login_name`、`source_address`、`recipient_codes`；ADR-0043 实施后改读 `PI` 标记列并排除代理键），即失败；现有 `SensitiveColumnExposureGuardTest` 只看顶层 `*Response` 记录的字段，不展开嵌套，所以不够用；(3) 每个白名单接口的应答类型必须在清单行注释里点名，注释与实际返回类型不符失败。
    - 登记 `response_text`：`platform_idempotency.idempotent_responses.response_text | INTERNAL | PI | 用途：重放幂等请求的第一次应答；正文仅限白名单接口，不含直接标识，可含自有 UUID（代理键仍是个人信息，ADR-0043 第 4 项第 2 条）`（保守取 PI，OV 建议；文件仍三段格式时，第二维写在本卡）。
    - 同表其余文本列随 TB-03 处理，本条不动。
  - **拿不准的点**（请 one-data 裁）：(1) N 为估算值，未起容器实测；`IdempotentResponseSizeTest` 落地后才有实测数。(2) 把 `:reconcile`、`:release`、`source-references` 三类移出白名单会改变它们对 `Idempotency-Key` 的行为（带键请求不再重放），如要保留幂等，只能给无界应答设上限，请裁。(3) 超限「不存、告警、仍返回应答」是我对卡上「失败并告警」的解读，若要求返回错误则写操作已提交的情况下会误导调用方。(4) `sourceId` 按外部员工编号论是判断，`data-classification.txt` 里没有登记它。

- 超限计数器名：`forcitis.platform.web.idempotency.response.oversize`（NamingSurfaceTest G46 要求以声明模块 `platform.web` 开头，one-data 2026-10-16 认可以实现为准）。

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
- 依据：门禁 O10；D19。　已由 K-3b-4 覆盖（one-coordinator 2026-10-16），PR-3 不含。

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
- 证据：TS AE-2（迁移 `:6-28` 无分区、`:47-61` 触发器禁 UPDATE / DELETE / TRUNCATE；`GUIDING_PRINCIPLES.md` §6 审计行、§11 O-8；注册表 `:207`；`AuditArchiveJob.java` 类注释；`JdbcAuditEntryRepository.saveForEvent` 的 `on conflict (event_id)`）；OV「高风险项判断 TS 第 1 条」。用户决定：intent `2026-10-15-020820`「接受，登记为唯一例外」，落点「主键 (id, created_at)、去重键 (event_id, created_at)，id 仍全局唯一 UUIDv7、引用只用 id；主键守护登记此唯一例外，新增分区表须另行批准」；2026-10-16 用户答「知道了，ADR 成文后我再确认」（收件箱 2026-10-16-one-data-audit-partition；intent `2026-10-16-085331-one-data-审计表按月分区-知道了-ADR.md`）。ADR-0048 由 one-architecture 起草，本分支 `docs/adr/` 最大编号为 ADR-0047，尚未成文。
- 改法（按 ADR-0048 与 intent 落点；分区键用 `created_at`，不是 OV 原建议的 `occurred_at`，已被用户决定取代）：
  1. `PARTITION BY RANGE (created_at)` 按月；主键 `(id, created_at)`，`id` 仍全局唯一 UUIDv7，引用只用 `id`。
  2. 去重键 `uk_audit_entries_event_id_created_at UNIQUE (event_id, created_at)` 取代 `uk_audit_entries_event_id`；`saveForEvent` 的 `ON CONFLICT (event_id)` 改 `(event_id, created_at)`。**待核（关键，交 ADR-0048 回答）**：去重键含 `created_at`，同一事件重放时 `created_at` 必须与首次相同才会冲突；现 `created_at` 注释写「来自注入的时钟」（SS §5：aud 列注释），若事件审计仍取写入时刻，重放会写出第二行，去重失效。须明确事件审计的 `created_at` 取信封发生时刻（确定性），或另有幂等手段；未答复前不开工。 **ADR-0048 决定 2 已回答**：事件条目的 `created_at` 一律取事件信封的发生时刻（现状如此，定为规则），同一事件重投时两列都相同、必落同一分区；为使前提成立，基线 `created_at` 的 `DEFAULT now()` 须删除，或由守护锁住写入侧赋值（写入 SQL 必须显式给 `created_at`），二者由本卡选一（建议删 DEFAULT，与 TB-09 同做）。直接审计条目 `event_id` 为空，不受此约束。
  3. 主键守护登记此唯一例外（`primary-key-exemptions.txt`，文件名与登记形式待核），新增分区表须另行批准。
  4. 维护角色摘除在线分区，作为只追加触发器的登记例外（O-8）；行级触发器是否随分区继承、TRUNCATE 语句级触发器是否对各分区生效：待核。
  5. 索引按分区重建；TB-04 点名的索引改造（`(actor_id, created_at DESC, id DESC)` 等、`starts_with` 的 `text_pattern_ops`）与本条同批做；`AuditArchiveJob` 按 `(created_at, id)` 推进，与按月分区兼容（OV AE-2）；`audit_archive_batches` 与对账（TS AB-3）随之调整，范围待核。
- 开工条件：无（ADR-0048 已采纳，用户 2026-10-16 批准成文）；须在基线关闭前（首次部署生产前）完成，属生产前闸门（用户「同意，不挡 dev」，one-ops 登记 PREDEPLOY C-15 一组）；本卡其余条目照常做，TB-05 的文本 CHECK、TB-09 与本条改同一张表，合并顺序由 one-1a-next-4 协调。
- 预期表结构 diff：`audit_entries`：改为按月范围分区表；主键 `(id, created_at)`；`uk_audit_entries_event_id` 换为 `uk_audit_entries_event_id_created_at`；索引随分区重建；初始分区与分区创建作业按 ADR-0048。
- 验收要点：同一事件重复投递只落一行；分区边界写入通过；维护角色可摘除分区、其他角色 DELETE 被拒；UPDATE、DELETE、TRUNCATE 触发器对各分区生效；归档作业与对账通过。 守护（ADR-0048「需同步修改的守护规则」1–5）：① `PrimaryKeyTypeGuardTest` 读 `primary-key-exemptions.txt`，除 5 张框架表外只接受 `platform_audit.audit_entries` 一行并校验形状（主键恰为 `id uuid` + `created_at timestamptz`，`created_at` 是范围分区键）；② 分区表守护：出现 `PARTITION BY` 的表必须登记且注明批准它的 ADR 号；③ 引用只用 `id`：任何 `FOREIGN KEY … REFERENCES platform_audit.audit_entries`（含复合外键）失败，`uk_audit_entries_event_id_created_at` 恰为 `(event_id, created_at)`；④ `AuditDomainEventsIT`：同一事件重投两次只产生一条，事件条目 `created_at` 等于信封发生时刻；⑤ `primary-key-exemptions.txt` 文件头注明「非框架表只允许 ADR-0048 的 audit_entries；新增须另立 ADR」。另按 ADR-0048 影响面：DATA_DESIGN_CHECKLIST §14.1 D16 注明本例外；NAMING_STANDARD §2.4a 第 8 条的分区登记本表分区键。
- 依据：门禁 O11、O7、C3；GUIDING_PRINCIPLES §11 O-8；NAMING_STANDARD §2.4a-8；ADR-0043 R-2；用户决定（第 5 节「已定」）。

- **数据设计（PR-1：TB-07 + TB-09 + TB-05，one-1a-next-5 补，待 one-data 签）**（基线 `origin/main` @4d0b7c34；依据 ADR-0048 决定 1–7 与「需同步修改的守护规则」1–5；one-architecture 判定 TB-09 删 DEFAULT 与 `created_at` 注释口径）
  1. **分区**：`audit_entries` 改 `PARTITION BY RANGE (created_at)`，每月一个分区，边界取 UTC 月初（`[YYYY-MM-01 00:00+00, 次月 01 00:00+00)`），分区名 `audit_entries_pYYYYMM`。取 UTC 而不取业务时区：分区只是存储与删除的单位，不对用户呈现；UTC 边界不依赖应用配置，库内函数可确定地算出。**不建 DEFAULT 分区**：落不进任何分区的行直接报错（SQLSTATE 23514），不静默收容。
  2. **主键与去重键**：`CONSTRAINT pk_audit_entries PRIMARY KEY (id, created_at)`；`CONSTRAINT uk_audit_entries_event_id_created_at UNIQUE (event_id, created_at)`（`event_id` 为空的直接审计行不受约束）；`saveForEvent` 改 `on conflict (event_id, created_at) do nothing`。`id` 仍由 `IdGenerator` 生成，引用只用 `id`，任何表不得外键指向本表。
  3. **TB-09**：删 `created_at DEFAULT now()`，保留 NOT NULL；列注释改为「审计事实发生的时间：事件条目取事件信封的发生时刻，直接审计取注入的时钟；分区键」；`event_id` 注释改「与 `created_at` 一起唯一，重复投递同一事件不产生第二条」。`AuditArchiveJobIT` 三处夹具补 `created_at`（只补列值，不改断言，PR 单列）。`updated_at` 的 DEFAULT 属全库口径，不动。
  4. **索引**（在父表上建，随分区自动建；名字按 NAMING_STANDARD，删 `ddl-naming-baseline.txt` 对应 4 行：`audit_entries_pkey`、`audit_entries_action_idx`、`audit_entries_actor_idx`、`audit_entries_subject_idx`）：
     - `ix_audit_entries_actor_id_created_at (actor_id, created_at DESC, id DESC)`
     - `ix_audit_entries_action_created_at (action text_pattern_ops, created_at DESC, id DESC)`：等值与前缀两用；若 `starts_with(action, :p)` 用不上该索引，查询改为 `action like :p || '%'`（前缀转义 `%`、`_`、`\`），以 EXPLAIN 为证，写进 PR
     - `ix_audit_entries_subject_kind_subject_id_created_at (subject_kind, subject_id, created_at DESC, id DESC)`
     - `ix_audit_entries_created_at_id (created_at, id)`（归档推进与列表游标）
     - 主键 `(id, created_at)` 兼作按 `id` 查单条的前导列索引。
  5. **只追加触发器**：行级 `BEFORE UPDATE OR DELETE` 建在父表上（PG 自动克隆到每个分区）；语句级 `BEFORE TRUNCATE` 父表一条，并由建分区函数（第 6 条）在每个新分区上各建一条（语句级触发器不克隆）。验收逐分区核对：经父表与直接对分区的 UPDATE、DELETE、TRUNCATE 都被拒。
  6. **分区预建**：基线建函数 `platform_audit.create_audit_entry_partitions(p_from date, p_through date) RETURNS integer`，`SECURITY DEFINER`、`SET search_path = pg_catalog, platform_audit`、属主 `one_*_owner`，`REVOKE EXECUTE … FROM PUBLIC`；为 `[p_from, p_through]` 内缺失的月份建分区并加 TRUNCATE 触发器，已有的跳过，返回新建个数。拒绝条件（报错，不静默）：参数不是月初、`p_from > p_through`、跨度超过 24 个月、目标月份已在归档位置之内（月末 `<=` `audit_archive_batches` 中最大 `last_entry_created_at`，防止给已归档或已删除的月份重建分区，让迟到行进入已归档区间）。函数不读数据库时钟。基线本身建 2026-10 起 6 个月分区（迁移时刻无关、`pg_dump` 可重现）。
     - 应用作业 `AuditPartitionJob`（`platform.audit.infrastructure.archive`，ShedLock `platform.audit.entry.partitions`）：启动时与每日一次，以注入时钟取当月 UTC 月初，调用函数补到「当月 + 3 个月」；失败记 `ALERT platform.audit …` 并计数 `forcitis.platform.audit.partitions.failures`；调用后核对次月分区存在，不存在同样 ALERT。配置 `forcitis.platform.audit.partitions.months-ahead=3`（必填、正数，缺失启动失败）。
     - 运行角色获 `EXECUTE` 该函数（`one_db_roles_schemas.sql` §4b 草稿与 `RoleSeparatedDeploymentIT`；`runbooks/sql/c3_*`、`c9_*` 可执行版由 one-ops 同步，PR 中列为待 one-ops）。运行角色仍无 `CREATE`。
  7. **迟到事件**（ADR-0048 第 7 项必答）：① 发生时刻所在月份无分区（早于最早分区或已删除）：插入报错，事件处理失败，事件发布保持未完成并由现有重投与未完成发布告警兜底；`saveForEvent` 捕获「无分区」错误时另记 `ALERT platform.audit late event … no partition`（只记事件号与月份，不记内容）后原样抛出。② 所在月份有分区但已在归档位置之前：照常写入，归档不会再收它，由现有对账计数不一致告警（`AuditArchiveJob.reconcile`）。不建兜底分区、不改写时刻。
  8. **维护角色删除在线分区**（O-8，只追加约束的登记例外）：基线建函数 `platform_audit.drop_archived_audit_entry_partition(p_month date) RETURNS bigint`，`SECURITY DEFINER`、属主 `one_*_owner`、`REVOKE EXECUTE … FROM PUBLIC`，只授予新角色 `one_*_audit_maintainer`（LOGIN、NOINHERIT、连接上限 1；草稿脚本 §1 与 §4b，可执行版待 one-ops；口令由运维设置）。校验全部通过才 `DETACH PARTITION` 并 `DROP TABLE`，返回删除的行数：分区存在；该月整月在归档位置之内（分区中没有 `(created_at, id) >` 最后归档位置的行）；分区行数等于首末条都落在该月的归档批次 `entry_count` 之和。任一不符即报错，不删。运行角色无 EXECUTE，也不是属主，不能 DETACH、DROP；应用代码不调用此函数。`data-access-registry.yaml` 的 `audit_entries` 登记分区键、粒度、预建作业、删除方式（维护角色经此函数）；`retention` 写 3 年在线、10 年归档。
  9. **归档与对账**：`AuditArchiveJob` 每批只取同一 UTC 月的条目（批次不跨分区），使分区与批次可逐月对上；`reconcile` 改为只对仍在线的月份比较：在线分区内 `(created_at, id) <=` 归档位置的行数，等于首条落在在线月份的批次 `entry_count` 之和。`audit_archive_batches` 列与约束不变（TB-06 另批）。
  10. **TB-05**（具名 CHECK）：`action`、`detail`、`change_reason`、`before_text`、`after_text`：`char_length BETWEEN 1 AND 2000`（禁空串，与 `AuditEntry` 上限 2000 一致），名 `ck_audit_entries_<列>_length`；`source_address`：`BETWEEN 1 AND 64`，超限或不像地址则置空并 ALERT，审计照常写入（one-data 裁定⑤）。改名（`action_code` 等）归 R-2a，不做。
  11. **守护**（ADR-0048 1–5）：`PrimaryKeyTypeGuardTest` 读 `primary-key-exemptions.txt`，非框架表只接受 `platform_audit.audit_entries` 一行并校验形状（主键恰为 `id uuid` + `created_at timestamptz`，`created_at` 是 RANGE 分区键）；出现 `PARTITION BY` 的表未登记或未写 ADR 号即失败；`DdlNamingTest` 能解析 `PARTITION BY RANGE` 与复合主键，任何 `REFERENCES platform_audit.audit_entries` 失败，`uk_audit_entries_event_id_created_at` 恰为 `(event_id, created_at)`；`AuditDomainEventsIT` 同一事件重投两次只一条、`created_at` 等于信封发生时刻；例外文件头写「非框架表只允许 ADR-0048 的 audit_entries；新增须另立 ADR」。
  - **预期表结构 diff**（空库执行改前、改后基线，`pg_dump --schema-only`）：`audit_entries` 变为分区父表 + 6 个月分区；主键、唯一约束、4 个索引如上（旧 5 个索引名消失）；`created_at` 无 DEFAULT；新增 6 条 TB-05 CHECK；两函数及其 `REVOKE`；各分区 TRUNCATE 触发器；两列注释。不得出现其他表、列、种子变化。
  - **不在本 PR**：TB-04（导出去 count，PR-3）、TB-06、TB-08（PR-3）；`audit_archive_batches` 10 年后删除作业（首次删除在 10 年后，CS-1.8，注册表只写口径）；内联 CHECK 具名（G-D12）。
  - **请 one-data 裁定**：① UTC 月边界与分区名；② 初建 6 个月、预建 3 个月是否合适；③ 第 6 条拒绝条件够不够（是否还要禁止早于最早分区）；④ 第 8 条删除校验口径；⑤ `source_address` 上限（one-data 裁定改为 64）。

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

- **数据设计（PR-3：TB-06、TB-08，one-1a-next-5 补，待 one-data 签）**（基线：PR-1 合入后的 `origin/main`；PR-1 已把 `audit_entries` 改为 UTC 月分区、`AuditArchiveJob` 每批只取同一 UTC 月、`reconcile` 只比在线月份，本节与之兼容，两处改同一个基线文件，PR-3 在 PR-1 之后 rebase；TB-04 已移出，归 K-3b-4，本节不含）
  1. **TB-06　DDL**（改 `V20261014134000__platform_audit_baseline.sql` 中 `audit_archive_batches` 的建表语句，内联具名）：
     - `CONSTRAINT uk_audit_archive_batches_first_entry_id UNIQUE (first_entry_id)`
     - `CONSTRAINT uk_audit_archive_batches_object_key UNIQUE (object_key)`
     - `CONSTRAINT ck_audit_archive_batches_sha256_hash CHECK (sha256_hash ~ '^[0-9a-f]{64}$')`
     - `sha256_hash` 列注释不改（已写「小写十六进制」）；表注释末尾追加「批次区间不重叠、无缺口由归档作业按位置推进保证、由对账核验（只比在线月份），库内不设排他约束」。
  2. **TB-06　哈希格式核实**：`sha256_hash` 的值来自 `AuditArchiveJob.insertBatch`（`AuditArchiveJob.java:199`）的 `stored.sha256()`；`ObjectStoreArchiveVault.archive` 先以 `HexFormat.of().formatHex(...)`（`ObjectStoreArchiveVault.java:120`，小写、定长 64）算出 `sha256`，再要求 `sha256.equals(stored.sha256())`，不等则抛 `ExternalSystemUnavailableException`（同文件 `:68-71`），所以入库值必等于本地算出的小写 64 位十六进制，`^[0-9a-f]{64}$` 与实际格式一致。`OssObjectStoreTest` 的 `"abc"`、`ObjectStoreArchiveVaultTest` 的 `"f".repeat(64)` 是金库层替身，不入库，不受影响。
  3. **TB-06　唯一约束为何成立**：批次由 `archiveOlderThan` 严格按 `(created_at, id)` 推进，下一批的首条必在上一批末条之后，故 `first_entry_id` 不会重复；`object_key` 含 UTC 时刻到秒与内容哈希前 12 位（`ObjectStoreArchiveVault.java:66`），不同批内容不同、哈希不同，同键只会出现在重复登记同一批的错误里，正是要挡的。约束违反发生在金库已写入之后：对象留在归档库而批次行未登记，下一轮作业会从原位置重新取同一批、写新键（现状对任何登记失败都如此，不新增处理，CS-1.8）。两个 uk 触发时的现状：由调度器记 ERROR，无 ALERT 前缀与计数，金库对象成孤儿，本轮 reconcile 不执行；按现有作业逻辑起止位置只后推、实际撞不上，按 CS-1.8 不新增处理，列后续卡。
  4. **TB-06　触发器 `tg_` 前缀改名：已核，未排入 R-2a，本 PR 不做**。证据：`ddl-naming-baseline.txt` 只含列、约束、索引三类，没有触发器行；`DdlNamingTest` 不检查触发器名；`NAMING_STANDARD.md` 没有触发器命名条款（G-D12 是枚举 CHECK）；`NEXT_BATCH_PLAN.md` 第 249 行 R-2a 条目是「79 处命名改名 + DDL-09/10/12/13/14」，不含触发器。基线里 `tg_` 与 `<表>_reject_*` 两种风格并存且 `tg_` 占多数（`organization_workforce` 5 对、`platform_integration` 3 个、`platform_approval`、`platform_numbering`、`platform_audit` 批次表），单改本表只会换一种不一致，且 `AuditArchiveJobIT.java:70,71,168,169` 按旧名禁用与启用触发器。统一须先在 `NAMING_STANDARD` 增触发器条款再一次性改全库，交 **one-data 裁定**：是否另立条目（建议并入 R-2a 之后的命名小卡，本卡 AB-4 的触发器部分关闭为「转卡」）。
  5. **TB-06　注册表**：`data-access-registry.yaml` 没有自由说明字段，在 `platform_audit.audit_archive_batches` 条目上方加 `#` 注释：「批次区间不重叠、无缺口不设数据库约束：区间由作业按 (created_at, id) 推进产生，由 `AuditArchiveJob.reconcile` 对账（只比在线月份）；分区摘除前由 `drop_archived_audit_entry_partition` 逐月核对批次条数之和。」并诚实写明：已摘除月份的缺口，库内此后无法再核验。`retention: PERMANENT` 与 GUIDING §6「归档保留至满 10 年」不一致属待用户（第 5 节第 2 条），本条不动。
  6. **TB-06　Java 与测试**：主代码无改动（键、哈希本就合规）。约束用例由测试代理先写在验收目录 `acceptance/platform/audit/AuditArchiveAndOutcomeConstraintsIT`（582a1602），不再在 `AuditArchiveJobIT` / `AuditLogIT` 追加（断言重复）。TB-06 部分共 3 条，名以 `DATA-REPAIR-timesheet-audit TB-06:` 开头：重复 `object_key` 被拒；重复 `first_entry_id` 被拒；`sha256_hash` 为大写、63 位、65 位、含非十六进制字符各被拒，小写 64 位通过。**不改任何现有夹具或断言**，理由：`RecordingArchiveVault` 返回的键 `test/<kind>/<第几批>` 与哈希 `"0".repeat(64)` 都已合规；键靠 `batches.size()` 计数，只要金库与批次表一起重置键就不重复，`AuditArchiveJobIT.java:65-72` 正是这样做的；`acceptance/.../AuditArchiveOrderIT` 只有一条 `@Test`、一个容器、一个金库，键不会重复。
  7. **TB-06　验收要点**：`AuditArchiveJobIT`、`AuditArchiveOrderIT`、`AuditSubjectColumnsIT` 原样通过；三类违反各被具名约束拒绝（错误信息含约束名）。
  8. **TB-06　预期表结构 diff**：`audit_archive_batches` 新增 `uk_audit_archive_batches_first_entry_id`、`uk_audit_archive_batches_object_key`（各带一个同名索引）、`ck_audit_archive_batches_sha256_hash`；表注释一句。触发器、列、其他表无变化。
  9. **TB-08　写入点核实**：三列只由 `AuditEntry.withTrace`（`AuditEntry.java:33`）写入，其余构造路径三列恒为空；`withTrace` 共 15 处主代码调用，结果如下（S 成功、F 失败）：

     | 位置 | action | subject_kind | 地址 | 结果 | 方式 |
     |---|---|---|---|---|---|
     | `LoginAdmission.java:76-77` | `platform.identity.login.succeeded` | USER_ACCOUNT | 有 | S | 有 |
     | `LoginAdmission.java:88-90` | `platform.identity.emergency.login` | USER_ACCOUNT | 有 | S | EMERGENCY |
     | `LoginAdmission.java:97-101` | `platform.identity.login.failed` | LOGIN_ATTEMPT（账号未识别）或 USER_ACCOUNT | 有 | F | 有 |
     | `BrowserIdentity.java:72-74`、`:81-84` | `…browser-identifier.replayed`、`…login.identity-check` | USER_ACCOUNT | 有 | F 或 S | 有 |
     | `EnterAdministratorMode.java:79-80`、`:87-88` | `…administrator-mode.entered`、`.refused` | USER_ACCOUNT | 有 | S、F | 有 |
     | `ExitAdministratorMode.java:42-43`、`SessionGuardFilter.java:92-94` | `…administrator-mode.exited` | USER_ACCOUNT | 有 | S | 有 |
     | `Logout.java:35-36` | `platform.identity.logout` | USER_ACCOUNT | 有 | S | 有 |
     | `PrivilegedRequestAudit.java:44-46` | `platform.identity.privileged.request` | REQUEST | 有 | S | 有 |
     | `ForceUserLogout.java:67-68` | `platform.identity.session.revoked` | USER_ACCOUNT | 有 | S | **空** |
     | `AccessDeniedAudit.java:77-78` | `platform.identity.access.denied` | OPERATION 或 REQUEST（`:89-97`） | 有 | F | **空** |
     | `HandleOrgSnapshotChange.java:91-92` | `platform.identity.session.revoked`（系统动作） | USER_ACCOUNT | **空** | S | **空** |
     | `CorrectLegalEntityAttribution.java:51-53` | `organization.workforce.legal-entity-attribution.correction-refused` | ORG_UNIT 或 EMPLOYEE（`AttributionAuditKinds.java:13-14`） | **空** | F | **空** |

  10. **TB-08　结论一（卡与列注释的前提不成立）**：三列并非「登录与会话类才有」，也并非同空同非空：(a) `outcome_kind` 还写在拒绝访问、特权请求、归属更正被拒这类非登录条目上；(b) 方式为空而结果非空的有 4 处（强制下线、拒绝访问、离职收回会话、归属更正被拒）；(c) 地址为空而结果非空的有 2 处（系统动作、归属更正被拒，二者未传来源）；(d) 失败登录的方式**从不为空**：`LoginAdmission.reject` 的 `method` 是非空参数，全部调用点（`LoginAsEmergencyAdmin.java:65,92,100,106,127`、`LoginWithSignedTicket.java:82,95`、`LoginWithSso.java:45-70`、`LoginWithWecom.java:24-32`）都传入入口对应的方式，账号未识别时只是 `actor` 与 `subject_id` 为空、主体类别为 `LOGIN_ATTEMPT`。因此：「`(outcome_kind IS NULL) = (login_method_kind IS NULL)`」会被 4 处现有写入违反，**不落**；「`source_address` 与另两列同非空」同样不成立（与 PR-1 的置空规则并存更不成立），**不落**；「按 `subject_kind` 限定携带三列」：携带三列的 `subject_kind` 含 USER_ACCOUNT、LOGIN_ATTEMPT、REQUEST、OPERATION、ORG_UNIT、EMPLOYEE，且新增审计点会继续增加，写成枚举就是把应用的审计清单抄进 DDL，**未核实为稳定规则，不落**。
  11. **TB-08　能落的 CHECK（单向）**：登录方式只对「有结果的条目」有意义，15 处写入点在方式非空时结果必非空，故加 `CONSTRAINT ck_audit_entries_login_method_outcome CHECK (login_method_kind IS NULL OR outcome_kind IS NOT NULL)`，位置在 `audit_entries` 建表语句中 `ck_audit_entries_subject_id` 之后；声明在分区父表上，PG 自动带到每个分区，与 PR-1 的分区键、主键、TB-05 的 CHECK 互不依赖。另在 `AuditEntry` 紧凑构造器加同一断言（`loginMethod != null && outcome == null` 抛 `IllegalArgumentException`），使违反在写入前、带出处地失败，不靠数据库报错（规则 6）。
  12. **TB-08　可选的第二条（本节不含，待裁定）**：`source_address IS NULL OR outcome_kind IS NOT NULL`（有地址必有结果）。15 处主代码写入都满足，但验收夹具 `P5Support.auditRow` 的调用 `OwnLoginRecordsBySubjectIT.java:48`（`ROLES_REPLACED`，结果与方式为空而地址 `10.1.0.4` 非空）违反；验收目录本卡不碰，且这一行是构造出来的数据，现实中没有对应写入点。要落须先由 one-data 与验收目录归属会话同意把该行地址改为空（该测试断言的是 `ACTIONS` 内三条的地址，改后不影响断言）。建议：**本 PR 不落**。
  13. **TB-08　列注释**（基线 `COMMENT ON COLUMN`，按核实结果纠正）：`outcome_kind` 改「结果：SUCCEEDED / FAILED；记录动作成败的条目才有（登录与会话、拒绝访问、特权请求、归属更正被拒等），其他条目为空」；`login_method_kind` 改「登录方式：企业微信（工作台、小程序、扫码）、单点登录、签名票据、应急登录；登录、会话、管理员模式、特权请求类条目才有，强制下线、拒绝访问等为空；有登录方式必有结果」；`source_address` 改「来源地址：带登录与会话轨迹的条目（登录、会话、管理员模式、拒绝访问、特权请求、强制下线）记录的客户端地址；其他条目、系统动作与取不到可信地址时为空」。注意 `RequestClientOrigin.java:22` 在无请求上下文时返回字面量 `"unknown"`，它不是地址，由 PR-1 的「不像地址则置空并 ALERT」兜住，本条不另处理；`SessionGuardFilter.java:93` 取 `request.getRemoteAddr()` 是否经 Tomcat remote-ip 还原属 AE-7，不在本节。
  14. **TB-08　测试**：新增 `AuditEntryTest`（`platform/audit/api`）：`loginMethod` 非空且 `outcome` 为空构造失败；结果非空而方式为空、三列全空均通过。集成测试同在验收目录 `AuditArchiveAndOutcomeConstraintsIT`（不在 `AuditLogIT` 追加，断言重复）一条 `DATA-REPAIR-timesheet-audit TB-08:`：直接 INSERT 方式非空而结果为空，被 `ck_audit_entries_login_method_outcome` 拒；全空、仅结果、结果加方式、结果加地址不加方式四种通过。**现有夹具与断言不改**，理由：逐一核对 `withTrace` 的 15 处主代码调用、测试中全部 `withTrace`（`AuditExportAndOwnRecordsIT.java:92-103`、`AuditLogIT.java:234,236`）与 `P5Support.auditRow` 的 7 次调用（`OwnLoginRecordsBySubjectIT.java:45-51,69-70`），没有任何一行是「方式非空而结果为空」。
  15. **TB-08　验收要点**：登录成功、失败（账号已识别与未识别）、拒绝访问、强制下线、系统收回会话、归属更正被拒各路径写入通过；`AuditExportAndOwnRecordsIT`、`OwnLoginRecordsBySubjectIT`、`AuditLogIT`、`LoginAuditDetailTest`、`ClientAddressNotLoggedTest` 原样通过。
  16. **TB-08　预期表结构 diff**：`audit_entries` 新增 `ck_audit_entries_login_method_outcome`（分区后每个分区各带一条继承的同名约束）；三条列注释；无列、索引、其他表变化。
  - **PR-3 总 diff**：`audit_archive_batches` 2 个唯一约束 + 1 条 CHECK + 表注释；`audit_entries` 1 条 CHECK + 3 条列注释；`data-access-registry.yaml` 一段注释；`AuditEntry` 构造器一处断言；新增测试 `AuditEntryTest`；约束用例（TB-06 共 3 条、TB-08 共 1 条）在验收目录 `AuditArchiveAndOutcomeConstraintsIT`，不在 `AuditArchiveJobIT` / `AuditLogIT` 追加。不得出现：触发器改名、分区或主键变化、`retention` 变化、种子变化。
  - **与卡原文不一致处**：(1) 卡 TB-08 写「三列应同空同非空、对应登录与会话类 `subject_kind`」，核实后不成立，只落单向一条；(2) 卡 TB-06 写触发器改名「随 R-2a、待核是否已排」，核实未排入，转 one-data；(3) PR-1 写 `source_address` 上限 45，本任务说明写「超 64 字置空」，两个上限需由 PR-1 对齐，本节不涉及。
  - **请 one-data 裁定**：① 第 10 条：三列不做成对约束、不按 `subject_kind` 限定，是否同意；② 第 11 条：只落「方式非空 ⇒ 结果非空」一条；③ 第 12 条：「地址非空 ⇒ 结果非空」是否另落（需改验收夹具一行）；④ 第 4 条：触发器统一命名是否另立卡及由谁起草 `NAMING_STANDARD` 条款；⑤ 第 5 条：注册表用注释表达「区间不重叠、无缺口由对账承担」是否可接受，或要求登记结构化字段；⑥ 是否给批次表加「首末条同一 UTC 月」的 CHECK（PR-1 约定批次不跨月，写法 `date_trunc('month', first_entry_created_at AT TIME ZONE 'UTC') = date_trunc('month', last_entry_created_at AT TIME ZONE 'UTC')`；本节倾向不落，理由是 PR-1 已写明批次表列与约束不变，且作业与摘除函数已按月核对）。

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
