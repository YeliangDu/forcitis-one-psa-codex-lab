# 派工卡 DATA-REPAIR-timesheet-audit：工时、考勤日历、审计、幂等已建表的数据设计缺陷修复（草稿）

- 草稿：one-data 起草助手，2026-10-16；待 one-data 终审、one-coordinator 派工。
- 证据来源：`docs/modernization/data/review-parts/timesheet_attendance_audit_idem_sched.md`（下称 TS）与 `OPUS_VERIFY.md`（下称 OV）；以 OV 为准。「:N」指 TS 审查时 main 基线对应迁移行：`cost_timesheet/V20261014140000__cost_timesheet_baseline.sql`、`organization_attendance/V20261014141000__organization_attendance_baseline.sql`、`platform_audit/V20261014134000__platform_audit_baseline.sql`、`platform_idempotency/V20261014221000__platform_idempotency_baseline.sql`；R-1b P5 已改审计两表，实施前重核。
- 「待核」表示分报告与复核报告都没有给出证据，实施者先核实再改。

## 1. 卡号与类别
- 卡号：DATA-REPAIR-timesheet-audit
- 类别：缺陷修复（SESSION_PLAYBOOK §6.1 第 9 条）
- 批次：已建表数据设计审查修复批（门禁 v2 §1.2 第 6 条）；两部分各开一个小 PR，直接合入 main
- 建议负责会话：**A 部分（工时与日历）one-1a-par；B 部分（审计与幂等）one-1a-next-4**
- 顺序：A 部分的 TA-01 依赖 TS-UNIQ 定稿的状态集合，须在 TS-UNIQ 合并后开工；B 部分不依赖 A 部分

## 2. 依据
- 门禁 v2：O3、O4、O8、O10、O7、C3、C4、E1、B3、B5
- SESSION_PLAYBOOK §14.1 D2、D4、D8、D12、D13、D19；§12.2；ADR-0038；ADR-0026 第 7 项第 3 条；ADR-0033 第 1 项第 10 条、第 7 项；ADR-0043 第 3、4 项、U-7(b)、U-7(c)、R-2、U-13
- GUIDING_PRINCIPLES §11 O-8（审计按月分区）；NAMING_STANDARD §2.4a-5
- 复核编号：OV「AE-3 确认」「IR-1 确认」「AE-5 确认」「高风险项判断 TS 第 1、2、5 条」「T-1 / T-2：用户已定」；建议新增规则 3、5
- 分报告编号：TS T-3、WD-1、AE-3、AE-4、AE-5、AB-2、IR-1、IR-2、IR-3、IR-4

## 3. 范围与卡上文件

A 部分（one-1a-par）允许改动：
- `backend/src/main/resources/db/migration/cost_timesheet/V20261014140000__cost_timesheet_baseline.sql`（只限 `timesheets` 的 T-3 CHECK）
- `backend/src/main/resources/db/migration/organization_attendance/V20261014141000__organization_attendance_baseline.sql`（只限 `work_calendar_days` 的 WD-1 CHECK）
- `backend/architecture/data-access-registry.yaml`（`consumed_events` 的 CE-1 登记，可选）
- 测试：`backend/src/test/java/com/forcitis/one/cost/timesheet/domain/TimesheetStateMachineTest.java`（只读）、新增 CHECK 集成测试

B 部分（one-1a-next-4）允许改动：
- `platform_audit/V20261014134000__platform_audit_baseline.sql`（只限 `audit_entries` 文本列 CHECK、`audit_archive_batches` 的 AB-2）
- `platform_idempotency/V20261014221000__platform_idempotency_baseline.sql`
- `backend/architecture/data-access-registry.yaml`（`idempotent_responses`、审计两表）、`data-classification.txt`、`archive-columns.txt`（仅 TB-02 涉及时）、`backend/architecture/` 下 U-7b 带到期日例外登记（文件名待核）
- Java：`backend/src/main/java/com/forcitis/one/platform/identity/application/LoginAdmission.java`、`platform/audit/application/ExportAuditEntries.java`、`platform/audit/infrastructure/persistence/JdbcAuditEntryQueries.java`、`platform/web/IdempotencyFilter.java`、`IdempotencyScope.java`、`AuditSearch.java`（路径待核）
- 测试：对应类的现有测试与新增集成测试

归属：本卡独占上述表的约束片段。T-1 与 X-6 的 `timesheets` 改动归 TS-UNIQ，本卡 T-3 与其同一基线文件，须按 TS-UNIQ 合并后的内容改。

不碰：`audit_entries` 分区、主键与 `uk_audit_entries_event_id`（AE-2，待 ADR 修订）；`work_calendar_*` 版本化与 `calendar_scope_kind`（W2）；`EventDraft` 等 kernel 构件；验收测试目录；他卡文件。

## 4. 条目清单

### 第一部分　工时与日历（建议 one-1a-par）

#### TA-01　T-3　中　`timesheets` 状态与「轮次、提交日」成对约束缺失
- 问题：`submit()` 总是同时让 `round_number` 加一并写 `submitted_date`，列注释写「0 表示从未提交」「空表示从未提交」，库内无约束保证同零同空，也不保证已提交过的状态轮次不小于 1。
- 证据：TS `Timesheet.java:133-135`；迁移 `:37,:44`；OV 未单独抽查（T-3 在 OV 报告之外，按 TS 证据取）。
- 改法：加 `ck_timesheets_round_submitted`：`(round_number = 0) = (submitted_date IS NULL)`；加 `ck_timesheets_round_after_submit`：状态属于「提交过」的集合时 `round_number >= 1`。集合的取值**以 TS-UNIQ 定稿的状态集合为准**（OV：「驳回后改原单再提交」使 `REJECTED` 不再是终态，撤回状态名与 `TimesheetCancelled` → `TimesheetApprovalWithdrawn` 同改），不得照 TS 写死 `SUBMITTED / RETURNED / APPROVED / REJECTED`；取消后回 `DRAFT` 时轮次与提交日保留，不受影响。
- 预期表结构 diff：`cost_timesheet.timesheets`：新增上述两条 CHECK。
- 验收要点：提交、退回、撤回、驳回后改原单再提交各路径通过；直接插入「已提交状态但 `round_number = 0`」被拒；`TimesheetStateMachineTest` 原样重跑。
- 依据：门禁 O4；D2。

#### TA-02　WD-1　中　「调休上班日必须落在周六或周日」只在应用里
- 证据：TS 迁移 `:41`（注释写了规则）；`CalendarDay.java:19-22`（抛 `ORGANIZATION_ATTENDANCE_MAKE_UP_WORKDAY_ON_WEEKDAY`）；这条规则只依赖本行两列，可以落库。OV 未单独抽查。
- 改法：新增 `ck_work_calendar_days_make_up_weekend`：`day_kind <> 'MAKE_UP_WORKDAY' OR EXTRACT(ISODOW FROM calendar_date) IN (6, 7)`。W2 日历版本化若重建明细表，此约束随明细表带走（DA-17 卡须承接）。
- 预期表结构 diff：`organization_attendance.work_calendar_days`：新增 `ck_work_calendar_days_make_up_weekend`。
- 验收要点：周六 / 周日调休通过；周三调休行被拒；`CalendarDay` 原有异常用例原样重跑。
- 依据：门禁 O4、B5；D2。

低级顺带（A 部分，不阻塞）：CE-1（`consumed_events` 登记 `APPEND_ONLY` 但 `ConsumedEventsPurgeJob.java:49` 会 DELETE，应登记 `INSERT_DELETE`，`unversioned-table-writes.txt:26`）、CE-2（`consumed_at` 索引）、WD-3（`day_kind` 内联 CHECK 具名，随 G-D12）、T-8（列注释与实现不符）。

### 第二部分　审计与幂等（建议 one-1a-next-4）

#### TB-01　AE-3　高（OV 确认）　登录审计把 IP 与浏览器标识写进 `detail`；U-7b 例外未登记
- 问题：登录审计把 `address=`（IP）与 `agent=`（浏览器标识）写进 `detail`，同时写进 `source_address` 列；检索与导出审计把 `sourceAddress` 条件写进 `detail`；操作登记把操作人、见证人姓名写进 `detail`。ADR-0043 第 4 项第 5 条、U-7(c) 已选「IP 只留 `source_address`」却未修。
- 证据：TS `LoginAdmission.java:108-114`（`describe`，被 `:75,98` 调用）；`AuditSearch.java:16-21`；`OperationAttestation.java:32-34`；`AuditEntry.java:16-23`；`data-classification.txt` 无这四列；OV：`one-1a-par-log-ip` 已合入只改了日志，P5 同行仍在（P5 :115）；U-7b 的带到期日例外在 `backend/architecture/` 未找到登记。
- 改法：
  1. `LoginAdmission.describe` 去掉 `address=` 与 `agent=`，IP 只留 `source_address`；`AuditSearch` 的 `sourceAddress` 条件同样不再拼进 `detail`（待核：检索与导出审计是否在本卡范围，ADR-0043 第 4 项第 5 条原文为准）。
  2. U-7b（`OperationAttestation` 把操作人、见证人姓名写进 `detail`）未定：按 ADR-0043 写带到期日例外登记（owner、到期日、指向 U-7b），到期日由用户给（待决 4）。
  3. 本卡内写「允许写入内容清单」草稿（`detail`、`before_text`、`after_text`、`change_reason`：字段名、来源、是否可能含个人信息；U-7(b) 允许 `before_text` / `after_text` 记被变更字段自身的 L2 个人信息原值），**待核**并交用户确认；四列的访问级别与 PI 标记登记待用户（待决 3）与 ADR-0043 实施 PR（文件仍三段格式，U-13）。
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
- 依据：门禁 O5、O7、O8、O9；D4；NAMING_STANDARD §2.4a-5、7。

#### TB-04　AE-5　中（OV 确认）　导出先做无上限 `count`
- 问题：`ExportAuditEntries` 先 `count(filters)` 再 `findAll(…, maxRows)`，`AuditSearch` 不强制时间范围，无过滤时对整张 10 年表计数。
- 证据：TS `ExportAuditEntries.java:43`、`JdbcAuditEntryQueries.java:57`；OV：建议 `limit maxRows + 1` 判超限。
- 改法：导出改 `limit maxRows + 1`，返回行数大于 `maxRows` 即判超限并按现有超限错误失败，去掉 `count`；索引改造（`(actor_id, created_at DESC, id DESC)` 等、`starts_with` 的 `text_pattern_ops`）与 AE-2 分区同批做，不在本卡（分区后索引需重建）。
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

#### TB-06　AB-2　中　`audit_archive_batches` 批次对象不重复、哈希格式未约束
- 问题：只有 `last_entry_id` 唯一，`object_key` 无唯一，`sha256_hash` 无格式 CHECK；区间不重叠无法用唯一约束表达。
- 证据：TS 迁移 `:75-76,:84`；P5 批次表 `:69-81` 已加 `first/last_entry_created_at` 与 `ck_audit_archive_batches_range`（OV「AB-1 已被 P5 覆盖」），实施前对照 P5。
- 改法：加 `uk_audit_archive_batches_first_entry_id`、`uk_audit_archive_batches_object_key`、`sha256_hash ~ '^[0-9a-f]{64}$'` 的具名 CHECK；注册表写明「区间不重叠、无缺口由对账承担」。
- 预期表结构 diff：`audit_archive_batches`：新增两个唯一约束、一条 CHECK。
- 验收要点：归档作业与对账通过；重复 `object_key` 被拒。
- 依据：门禁 B5、O4、O8。

低级顺带（B 部分，不阻塞）：AE-6（注释）、AE-7（待核：`SessionGuardFilter.java:91` 用 `getRemoteAddr()`，是否已用 Tomcat remote-ip 还原）、AB-4、IR 注释、SL-1、SL-2。

## 5. 待用户决定
1. **AE-2 审计分区与 ADR-0033 主键冲突**（需修订 ADR，本卡不改）：分区表主键须含分区键，与 ADR-0033「单列 uuid 主键」冲突，`uk_audit_entries_event_id` 无法跨分区。OV 建议：分区键用动作发生时刻 `occurred_at`（事件审计取信封发生时刻，直接审计取写入时刻），主键 `(id, occurred_at)`，事件幂等改 `uk (event_id, occurred_at)`；ADR-0033 第 1 项第 2 条加一句「按时间分区的只追加表，主键为（UUIDv7 ID，分区键），ID 仍全局唯一」；维护角色删分区作为只追加触发器的登记例外。基线开放期内做，否则上线后要重写全表。
2. 保留期：`timesheets`（是否随会计档案）、`consumed_events` 90 天、`idempotent_responses` 24 小时、`work_calendar_*`、`audit_archive_batches`、`audit_entries` 在线行与 `source_address` 删除时点（TS 待决 4）。
3. 分级：`idempotent_responses.response_text` 级别与 PI 标记；`detail`、`before_text`、`after_text`、`change_reason` 的 PI 标记；浏览器标识 `agent=` 是否属个人信息；工时数据是否按 `PI` 登记（TS 待决 5）。
4. U-7b：`OperationAttestation` 的操作人、见证人是改写员工 ID 还是保留姓名并登记例外；例外到期日（TS 待决 6）。
5. 工时单部门口径（T-4，TS 待决 3）：取工作日当天的部门还是提交当时的部门（后者须写规则卡）。
6. 日历版本化时点与确认人（WY-2，TS 待决 8）；ADR-0033 第 9 项第 2 条原文「并入 R-1b」与 NEXT_BATCH_PLAN:299（挪 W2 DA-17）不一致，由 one-coordinator 在 ADR 影响面或台账补一行出处（OV「WY-1 降级」：门禁 §5 偏离 ADR 不由会话自定）。

## 6. 不含（已有归属，本卡不起条目）
| 项 | 归属 |
|---|---|
| T-1 「员工 × 工作日」唯一（阻断） | 并入 TS-UNIQ（one-1a-par 另卡）；用户已定无谓词、对所有状态生效（intent `2026-10-16-052923`、one-1a-par 画像 :58），`uk_timesheets_employee_id_work_date` |
| T-2 状态集合与事件名、`REJECTED` 终态、新建分支先查业务错误 | TS-UNIQ（OV「T-1 / T-2」） |
| X-6 `timesheets` 记 `numbering_rule_version_id` | 并入 TS-UNIQ |
| T-6 `ix_timesheets_employee_id` 冗余 | TS-UNIQ 替换索引时一并删 |
| WY-1 `calendar_scope_kind`（降为中） | W2 日历版本化（DA-17） |
| WY-2 日历版本化、使用方记 `work_calendar_version_id`（高） | W2 K-2（DA-17）；OV 建议版本表照搬 `numbering_rule_versions` 形态并补 `decision_ref` |
| WY-3 日历事件 `aggregateId` 为文本 | W2 |
| WD-2 例外日期必须在所属年度内 | 随 WY-2 版本表一并处理 |
| AE-1 `subject_key` 拆列 | 已被 R-1b P5 覆盖 |
| AB-1 归档按 UUID 顺序 | 已被 P5 覆盖（`first/last_entry_created_at`、按 `(created_at, id)`） |
| AE-2 分区 | 待用户 / ADR 修订（第 5 节第 1 条）；AB-3 对账计数随之 |
| AE-4 改名、X-1 内联 CHECK 具名 | R-2a / G-D12 |
| 分级文件四段格式 | ADR-0043 实施 PR |

## 7. 验证办法
- 守护：`DdlNamingTest`、`DataArchitectureTest`、`GlossaryTest`、分级文件守护全绿；新增约束全部具名；不新增例外清单条目（TB-01 新增的 U-7b 例外须有 ADR-0043 出处与到期日）。
- 测试：TA-01、TA-02、TB-02 至 TB-06 各一条「违反即被拒、合法即通过」集成测试；TB-01 的登录审计 `detail` 断言（登录成功、失败、拒绝访问）；TB-04 的 `maxRows` 与 `maxRows + 1`；`TimesheetStateMachineTest`、`AuditArchiveJob` 相关测试原样重跑；测试名以 `DATA-REPAIR-timesheet-audit TA-nn` 或 `TB-nn` 开头。
- 表结构 diff：两个空库分别执行改动前、改动后的基线，`pg_dump --schema-only` 对比，A、B 部分各做一次；预期差异只能是第 4 节所列。不得出现：`timesheets` 的唯一约束与列变化（归 TS-UNIQ）；`audit_entries` 的分区、主键、索引变化；种子数据变化。
- `scripts/precheck.sh <基线>` 全部 PASS 或有理由的 SKIP，输出贴进两个 PR。

## 8. 执行者与复审
- 执行者：sonnet 子代理；TB-01、TB-02 首轮复审用 opus（个人信息与敏感数据复制点，安全判定）；其余 sonnet 复核。
- 工作区：独立 worktree，A 部分分支 `claude/one-1a-par-data-repair-timesheet`，B 部分分支 `claude/one-1a-next-4-data-repair-audit`；基线为 P5 与 TS-UNIQ（A 部分）合并后的 main。
- 报告：A 部分向 one-1a-par、B 部分向 one-1a-next-4，均抄 one-coordinator，分开写「已验证」与「未运行」。

## 9. 禁止事项
- 不放宽、不删除断言；不改验收测试目录；不改卡外文件；不顺手重构、改名或统一风格。
- 不动 `timesheets` 的唯一约束与状态集合（归 TS-UNIQ）；不动 `audit_entries` 分区与主键；不用部分唯一索引。
- TB-02 超长应答不得静默截断；TB-01 不得为通过测试把 IP 换个字段名继续写进 `detail`。
- 不追加迁移（ADR-0038）；不改 kernel 共享构件。
- 不联网、不起容器（Testcontainers 白名单镜像除外）、不建账号；不打印、不写入密钥。
- 缺失或无效输入失败并告警，不静默跳过。
