# 已建成表数据设计审查：opus 独立复核

- 复核：one-data 的 opus 复核子代理，2026-10-16；基线 main（本 worktree，分支 `claude/one-data-signoffs`）、`origin/claude/one-1a-next-4-r1b-p5`（下称 P5）、`origin/claude/one-data-gate-v2`（门禁 v2）。只读：未起容器、未连库、未跑测试，结论来自迁移、注册表、分级文件、事件目录、Java 源码、ADR 原文与 grep。
- 被复核：`organization_workforce.md`（OW）、`platform_identity.md`（PI）、`platform_approval_integration_numbering.md`（PA）、`timesheet_attendance_audit_idem_sched.md`（TS）。
- 级别口径：门禁 v2 本身不分级（§3「默认不通过即阻断签发」），四份报告的级别是子代理自定。本复核按：**阻断** = 违反已采纳 ADR / 用户决定且未排期、或数据完整性 / 安全不变量在库内可被破坏且影响大；**高** = 同类问题但已在 ADR 或计划里排定首次部署前完成、或首次部署前必须定的结构；**中** = 库内防线缺口但应用已保证、或合规登记缺口；**低** = 注释、命名、可读性。
- 计数：阻断与高 20 条：**确认 12、降级 8、驳回 0**；另从「中」抽查 6 条，建议升 1 条为高、降 1 条为低；高风险项中建议把 PI 的 S-1 升为高。

## 阻断与高逐条复核

| 编号 | 表 | 原级别 | 复核结论 | 新级别 | 证据 | 重叠标注 |
|---|---|---|---|---|---|---|
| OW HS-1 | sync_run_held_separations | 阻断 | 确认 | 阻断（已解决于 P5） | main 迁移 :449 复合主键；ADR-0033 第 1 项第 2 条逐表点名；`primary-key-type-baseline.txt:5` | 已被 P5 覆盖：P5 基线 :444-455 新增 `sync_run_held_separation_id` 主键、`uk_…_sync_run_id_employee_id`、`ix_…_employee_id`（HS-3 同时解决）；HS-2 保留期未覆盖（P5 注册表 :116 仍写 `1y`） |
| PI R-1 | roles | 阻断 | 降级 | 高 | 属实：main 迁移 :27-39 仍含 `name/status/access_scope`，:305-366 迁移写系统角色；`JdbcRoleRepository.java:101-104` 物理删角色。但 ADR-0034 第 6 项自定实施时机「W2 独立卡、与 K-2 同期、首次部署任何环境前」（ADR-0034:288-290），NEXT_BATCH_PLAN:269、COORDINATOR_LEDGER:47 已排「dev 前」，不是未排期的违反 | 未被 P5 覆盖（P5 基线仍 `INSERT INTO platform_identity.roles`）；作为 dev 部署闸门跟踪 |
| PI RP-1 | role_permissions | 阻断 | 降级 | 高（并入 R-1） | 复合主键部分属实于 main；表的去留随 ADR-0034 第 1 项第 3 条 | 主键部分已被 P5 覆盖（P5 :55-62 `pk_role_permissions` + `uk_role_permissions_role_id_permission_code`，RULING-P5-D12）；删表并入 ADR-0034 W2 卡 |
| PI AR-1 | account_roles | 阻断 | 降级 | 高（并入 R-1） | 物理删除授予属实（`saveRoles` 的 `delete … not in`）；`account_role_grants` 按 ADR-0034 第 2 项、W2 实现 | 主键部分已被 P5 覆盖（P5 :72-79）；授予历史并入 ADR-0034 W2 卡 |
| TS T-1 | timesheets | 阻断 | 确认 | 阻断 | main 迁移 :24-26 只有 `uk_timesheets_timesheet_number`，`ix_timesheets_employee_id` 非唯一；`SubmitTimesheet.java:66-87` 只按工时单 ID 取咨询锁与查找，新建分支不查（员工，日期），并发两次新建可得两单 | 并入 TS-UNIQ。注意：子代理「待用户决定第 1 条」已决定（intent `2026-10-16-052923`、one-1a-par 画像 :58）：唯一约束对所有状态生效、不做部分唯一、不设取消终态，建议改法按此取 `uk_timesheets_employee_id_work_date` 无谓词 |
| TS WY-1 | work_calendar_years | 阻断 | 降级 | 中 | 属实：main 与 P5 迁移 :13 均为 `UNIQUE NULLS NOT DISTINCT (legal_entity_id, year_number)`，无 `calendar_scope_kind`。但唯一性在库内已正确成立（不是哨兵 `COALESCE`），差别在形态；且 NEXT_BATCH_PLAN:299 已把 `calendar_scope_kind` 从 R-1b 挪到 W2 日历版本化（DA-17），子代理未看到这一处 | 未被 P5 覆盖（P5 是 R-1b 最后一个子 PR）。ADR-0033 第 9 项第 2 条原文仍写「并入 R-1b」，计划与 ADR 不一致，需 one-coordinator 在 ADR 影响面或台账补一行出处（门禁 §5：偏离 ADR 不由会话自定） |
| TS AE-1 | audit_entries | 阻断 | 确认 | 阻断（已解决于 P5） | main 迁移 :11 `subject_key text`；ADR-0033 第 7 项第 2 条 | 已被 P5 覆盖：P5 基线 :11-12、:24、:28 有 `subject_kind` + `ck_audit_entries_subject_kind`（19 值）、`subject_id uuid`、`ck_audit_entries_subject_id` 成对、索引 `(subject_kind, subject_id, created_at DESC)`；`archive-columns.txt` 与 `AuditArchiveJob.COLUMNS` 同步 |
| TS AB-1 | audit_archive_batches | 阻断 | 降级 | 中（已解决于 P5） | 违反 ADR-0033 第 1 项第 10 条属实（main `AuditArchiveJob.java:117-136` 按 `id` 推进、全零哨兵）。但「漏档即丢数据」夸大：只归档早于 3 年截止点的前缀、遇第一条未到期即停，漏档需要某实例的 ID 时钟落后 3 年以上；对账已告警 | 已被 P5 覆盖：P5 批次表 :69-81 加 `first/last_entry_created_at` 与 `ck_audit_archive_batches_range`，作业 :123-145 按 `(created_at, id)` 取批与对账，索引 `ix_audit_entries_created_at_id` |
| OW P-1 | positions | 高 | 确认 | 高 | REG 无版本化、`JdbcOrgSnapshotRepository` 覆盖写属实；D17 主数据须三选一 | 已排期 W2「主数据版本化 25 张表」（NEXT_BATCH_PLAN:269）；positions 是否在 25 张内未核实 |
| OW E-1 | employees | 高 | 确认 | 高 | 属实；W2 有 DA-7 `employee_versions` 与「离职两字段」（NEXT_BATCH_PLAN:269、ADR-0033:212），E-2 的 `separated_at` 口径随之定 | 已排期 W2（DA-7） |
| OW SR-1 | sync_runs | 高 | 降级 | 中 | 属实：迁移 :119-149 无跨列约束。降级理由：技术表、状态全由作业驱动，四个原因 / 时间字段无用户输入，应用已全部保证（见高风险项 OW-1）；建议的四条 CHECK 我逐条对过调用点，**可直接落、不会误拒** | — |
| PI UA-1 | user_accounts | 高 | 降级 | 中 | 属实：REG :227-233 `PERMANENT`，注册表无 `gaps:` 条目。但 ADR-0043 R-2 / U-12 明写「期限由实施 PR 提出、提出前以带到期日的 gap 登记」，R-2 守护也随 ADR-0043 实施 PR；属合规登记缺口，非结构缺陷 | 随 ADR-0043 实施 PR（首次部署前） |
| PI N-1 | navigation_items | 高 | 降级 | 中 | 无业务唯一属实（:84-95）。但 REG :255-260 `mutability: MIGRATION_ONLY`、无应用写入方（grep `navigation_items` 无 insert/update），重复只可能来自迁移种子；RULE-347 是老系统规则卡，只作证据 | — |
| PI AQ-1 | authorization_requests | 高 | 确认 | 高 | :282 `decided_by IS NULL OR … OR request_status='REJECTED'`：`CONFIRMED` 且 `decided_by` 为空整条为真，ADR-0030:15-16 称该约束为不可放宽的双人防线。建议改法可行：`grant-role` 运维路径不经本表（`GrantRoleByOperations.java:61-62` 直改账号），唯一写入点 `JdbcAuthorizationRequestRepository.save` 总是同时写两列 | — |
| PI AQ-2 | authorization_requests | 高 | 确认 | 高（并入 R-1） | ADR-0034:215-222、309 要求 `VOIDED` 与两个基准列，迁移 :274 只有三值 | ADR-0034 W2 卡；AQ-1 的成对 CHECK 须同时覆盖 `VOIDED`（作废保留确认人） |
| PA AR-1 | approval_requests 等三表 | 高 | 确认，**改法需修正** | 高 | 成对 CHECK 缺失属实（:12-13）。但 `DecideTasks.java:106-108` 有 `hookDecidesReason`：节点配了前置钩子时允许无原因驳回，随后 `Reason.ofNullable` 得空，经 `Approvals.reject` 写成 `REJECTED` + `result_reason` 空——建议的 `(status IN (…)) = (result_reason IS NOT NULL)` 会拒绝这条设计内的路径。目前全仓没有 `ApprovalHook` 实现，所以现有数据不会撞上 | 见高风险项 PA-1 |
| TS WY-2 | work_calendar_* | 高 | 确认 | 高 | `JdbcWorkCalendarRepository.java:85` 整年 `delete` 再插入；`DeadlinePlanner` 读日历但任务不记版本；ADR-0033 第 9 项第 1、3 条 | 已排期 W2 K-2（DA-17）；首次部署前须落 |
| TS AE-2 | audit_entries | 高 | 确认 | 高 | GUIDING_PRINCIPLES:248（O-8 按月分区、维护角色删分区）、NAMING_STANDARD:149（建表定分区键）；main 与 P5 均非分区表 | 未被 P5 覆盖；P5 的「时刻 + ID」归档与按月分区兼容。主键冲突见高风险项 TS-1 |
| TS AE-3 | audit_entries | 高 | 确认 | 高 | `LoginAdmission.java:108-114` 仍写 `address=` 与 `agent=`；ADR-0043 第 4 项第 5 条、U-7c 已选「IP 只留 `source_address`」却未修；`one-1a-par-log-ip` 已合入（与 main 无差异）只改了日志，P5 同行仍在（P5 :115）；U-7b 的带到期日例外在 `backend/architecture/` 未找到登记 | 未被 P5 覆盖 |
| TS IR-1 | idempotent_responses | 高 | 确认 | 高 | `IdempotencyFilter.java:113-125` 对 `IdempotencyScope` 放行的所有 POST 存整份应答，范围只排除 `/sessions`、`/login-states`（黑名单）；`response_text` 未分级、无长度上限。当前未发现返回密钥或 `SENSITIVE` 列的 POST（审计导出是 GET），属前向风险 | — |

## 高风险项判断

### OW
1. **SR-1 / SR-2（sync_runs 成对 CHECK）**：逐条核对 `SyncRun.java:37-40`（状态机）、`:171-233`、`ApplySourceSnapshot.java:88-99、121-130`、`FailSyncRun`、`SyncOrgSnapshot.java:51`、`BeginSyncRun.java:33`。
   - `ck_sync_runs_failure_reason`（FAILED ⇒ 非空）：两条入口都给非空串（`getClass().getSimpleName() + ": " + getMessage()`；遗弃关闭固定串）。安全。
   - `ck_sync_runs_held_reason`（HELD ⇒ 原因非空且 `cardinality(hold_kind_codes) > 0`）：`kinds` 只在至少一类成立时构造；原因由 `joining("; ")` 产生，最坏是空串而非 NULL。安全；若同时加「非空串」须先让 `verdict.reason()` 保证非空。
   - `ck_sync_runs_released`（RELEASED ⇒ `released_at`、`released_by_run_id` 非空；`released_by` 可空）：`release()` 必写两列。安全。
   - `ck_sync_runs_finished_at`（`(status='RUNNING') = (finished_at IS NULL)`）：除 `release()` 外每个转换都写 `finishedAt`，RELEASED 保留 HELD 时的值。安全。
   - SR-2：(a) `released_kind_codes <@ hold_kind_codes` 安全（`hold()` 换类型时清空，`releaseKind` 先校验）；(b)「统计列全空或全有」先核 `RunStats` 各字段是否都非空再加；(c) 计数 `>= 0` 安全；(d) **`finished_at >= started_at` 不建议加**：HELD→HELD 刷新会由另一实例给早先的运行写 `finished_at`，实例间时钟偏差可让每 5 分钟的生产作业在提交时失败；(e) 长度上限按代码截断值 2000 加。
2. **E-1 / E-2 / P-1**：建议员工、岗位都走维度型版本表（OBSERVED），不选「个人信息最小化只留当前值」——规则卡要「每次离职各记一条」，只留当前值做不到，且任职史已在 `assignment_versions`，员工侧不版本化会出现半边历史。`separated_at` 随 W2 DA-7 的「离职两字段」改名 `separation_observed_at` 并按规则卡改写入；W2 前不改现列。
3. **HS-1/2/3**：P5 已解决主键与外键索引；P5 仍是 `REPLACE_SET`（delete + insert）写法，新主键每次重生成，表无外部引用，无害。**HS-2 仍开放**：本表实际随运行 `ON DELETE CASCADE` 保留 2 年，注册表写 1 年；建议注册表改为「随 `sync_runs` 删除（2 年）」并在 GUIDING §6 补一行，比给作业加单独删除更简单。
4. **LE-2 / LV-1**：LV-1(a) 版本表 `code` 格式 CHECK 应加（主表有、版本表无，属实）。LV-1(b) 排他约束 `EXCLUDE USING gist (code WITH =, daterange(...) WITH &&)` 可行：同日更正产生的空区间不与任何区间重叠，不会误拒；但主表 `legal_entities.code` 已全局唯一且 RULE「发号后禁改」，跨法人复用代码的窗口很小，列为中、随 W2 法人代码规则一起做。LE-2 归 D17 细则 5：注册表登记「当前值投影 + 一致性测试」即可，不必改根表。
5. **X-3**：本地不建工号 / 编码唯一是对的（取证显示来源工号不唯一）。同步与登录建档的竞态由 `platform_integration.source_references` 的 `(source_system, source_object_kind, source_id)` 唯一与 ADR-0033 测试 5（并发首次分配只得一个 `own_id`）承担；需要做的只是注册表写明「身份来自来源对照」。

### PI
1. **AQ-1**：建议落两条：`ck_authorization_requests_decision`：`(request_status = 'PENDING') = (decided_by IS NULL) AND (decided_by IS NULL) = (decided_at IS NULL)`；两人约束改为 `request_status IN ('PENDING','REJECTED') OR (decided_by <> requested_by AND decided_by IS DISTINCT FROM subject_user_id)`。W2 加 `VOIDED` 时并入「非 PENDING」一侧。上线前先 grep 测试夹具里有无直接插 `CONFIRMED` 而不带 `decided_by` 的行（未核）。「确认人不持有被改角色」跨表，留应用。
2. **R-1 / RP-1 / AR-1 / AQ-2**：四项是同一张 ADR-0034 W2 卡，属实且已排期；P5 只改了两张关联表的主键（RULING-P5-D12），授予与角色定义的时刻区间、只许关闭触发器、迁移不写角色、`VOIDED` 都留 W2。建议 one-coordinator 把「ADR-0034 卡合并」列入 dev 部署前检查项（DG-1 签发行之外的人工闸门）。
3. **S-1 / LS-1**：**S-1 建议升为高**。`sessions.session_id` 是 Spring Session JDBC 的会话标识，等同 Cookie 持有凭据，明文入库且未登记（`data-classification.txt` 只有 `principal_name`、`attribute_bytes`），默认 INTERNAL，任何读库者可冒用会话。建议登记 `RESTRICTED + NONE`（安全材料，与 `attribute_bytes` 同级），`primary_id` 同判 `SENSITIVE`；是否改为只存摘要需自定义会话仓储（Spring Session 无内建，未核实）。LS-1 `binding_hash` 是随机绑定值的 SHA-256，按 `state_hash` 同口径登记 `SENSITIVE + NONE` 即可，维持中。
4. **BI-1**：`JdbcBrowserIdentifiers.java:34-49` 属实：换新时把同一浏览器上其他账号未退役行的 `identifier_hash` 原地改成新值。语义上是「同一浏览器的信任随 Cookie 迁移」，不扩大信任面（新值仍只在这个浏览器里）；副作用是其他账号失去旧值的「退役后重放」检测（旧值行被改写而非退役）。建议改为「其他账号的旧行同样置 `retired_at` 并插入新行」，注册表改 `CONDITIONAL_UPDATE`；维持中。
5. **UA-1**：U-12 已定「账号停用或离职后清除」。与 `versioned-table-unconditional-writes.txt` 不冲突：清除是在停用 / 离职事务里把列置空的另一条写入。先按 R-2 登记带到期日 gap，期限随 ADR-0043 实施 PR 提出。

### PA
1. **AR-1 / TK-1**：AR-1 先定 `hookDecidesReason` 去留——它与 RULE-280 修订「驳回与退回仍须原因」冲突，且目前无钩子实现。建议删掉该放行分支（钩子要原因就由钩子返回原因），然后落 `ck_approval_requests_result_reason`、`approval_task_records` 的 `action_kind IN ('REJECTED','RETURNED','TRANSFERRED','REASSIGNED') ⇒ record_reason 非空`；`approval_tasks` 的成对式要写成单向（`outcome_kind IN ('REJECTED','RETURNED') AND effect_kind='BINDING' ⇒ 非空`），加签驳回待用户第 9 项。TK-1 我核过 `ApprovalTask.java:58-158`，建议条目可落，但须避开两个坑：**CLOSED 任务可保留 `assignee_id`、`claimed_at`**（`close()` 不清空），不得写「非 CLAIMED ⇒ 处理人为空」；**AUTOMATIC 的 COMPLETED 有 `assignee_id` 无 `claimed_at`、无 `approved_hash`**（`passedBySystem`），不得写「COMPLETED ⇒ claimed_at 非空」。
2. **AR-4 / RC-1**：三处 `instance_id` 指向 `start_rounds(instance_id)` 的外键可行；`approval_requests` 一侧须 `DEFERRABLE INITIALLY DEFERRED`（同事务先写请求后写轮次，且 `start_rounds.approval_request_id` 反向引用请求，形成环）。RC-1 复合外键 `(task_id, approval_request_id)` 可行，代价是 `approval_tasks` 多一个唯一约束。与 `lockForStart` 的咨询锁无冲突（外键只加行级共享锁）。
3. **SRF / OD-6 / X-4**：`source_references.source_id` 对 `EMPLOYEE` 是来源系统的人员标识，按 ADR-0043 第 4 项第 2 条的同一理由（去标识化仍是个人信息）判 `INTERNAL + PI`；它须与自有对象同寿命（迁入对账与同步要用），所以 `PERMANENT` 需要像 `emergency_admins` 那样拿一条用户决定出处，否则违反 R-2。登记待决收件箱。OD-6 `recipient_codes` 已由 U-4(b) 定为 `INTERNAL + PI`，文件未改是 ADR-0043 实施 PR 的事。
4. **PS-2**：`ProcessArchiveJob.java:176-179` 把 `failure.getMessage()` 同时写 `ALERT` 日志与 `failure_message`。Flowable 删除失败多由 JDBC 异常包裹，消息带服务端错误文本（`logServerErrorDetail=false` 只去掉列值），与 O-3「数据库服务端的错误详情」冲突，属实。建议日志与入库都只写异常类名与引擎实例 ID，维持中。
5. **X-6 / NV-3**：X-6 属实（ADR-0033 第 1 项第 5 条、D7）：`timesheets` 不记 `numbering_rule_version_id`。按日期重算版本在版本只追加、生效日不追溯（D15）时结果确定，但不可审计；建议并入 TS-UNIQ 同卡（同表同基线开放期），否则开 cost.timesheet 小卡。NV-3 生效日次序由用户第 6 项定后再落约束。

### TS
1. **AE-2（分区与主键、事件幂等）**：结构冲突属实——分区表的主键与唯一约束必须含分区键，与 ADR-0033「无复合主键」冲突，`uk_audit_entries_event_id` 也无法跨分区。我的建议（须用户 / ADR 修订定）：分区键用「动作发生时刻」`occurred_at`（事件审计取信封发生时刻，直接审计取写入时刻），主键 `(id, occurred_at)`，事件幂等改 `uk (event_id, occurred_at)`——同一事件重投的发生时刻相同，必落同一分区，唯一约束仍生效，不需要另建登记表。ADR-0033 第 1 项第 2 条加一句「按时间分区的只追加表，主键为（UUIDv7 ID，分区键），ID 仍全局唯一」。维护角色删分区作为只追加触发器的登记例外，权限只给维护角色。基线开放期内做，否则上线后要重写全表。
2. **AB-1**：P5 已改为「时刻 + ID」。残余：迁入早于归档进度的老审计条目会被跳过，P5 类注释已写明「迁入须先于归档进度越过其日期」，对账会告警。若采用按月分区，建议一批 = 一个分区，边界即分区范围，对账改按分区计数，顺带解决 AB-3 的整表前缀计数。
3. **T-1 / T-2**：用户已定（intent `2026-10-16-052923`）：唯一约束对所有状态生效、无部分谓词、无取消终态、驳回或撤回后改原单再交。所以 T-1 无需谓词，子代理列的「待用户决定 1、2」已过时；剩余是状态值（撤回状态名、`TimesheetCancelled` → `TimesheetApprovalWithdrawn` B2-B07）与 `REJECTED` 是否仍是终态——按「驳回后改原单再提交」，现状机 `REJECTED` 无出边（`Timesheet.java:55-60`），与用户决定冲突，须在 TS-UNIQ 一并改。新建分支要先查（员工，日期）给出业务错误，唯一冲突只作最后防线。
4. **WY-2**：已排 W2。建议版本表照搬 `numbering_rule_versions` 已有形态（作者、两确认人不同、确认时刻成对、只许状态单向流转的触发器），补 `decision_ref`；使用方（审批任务）记 `work_calendar_version_id`，已算出的截止时间不随新版本重算（与旧规则一致，也正是记版本 ID 的意义）。
5. **IR-1**：建议改为白名单：`IdempotencyScope` 只对登记过「应答不含 PI 直接标识与 `SENSITIVE` 列」的接口生效；`response_text` 登记 `INTERNAL + PI`（保守）并加长度上限；中长期改为只存资源 ID 与状态、重放时重读。

## 中级抽查

| 编号 | 原级别 | 复核结论 | 证据 |
|---|---|---|---|
| PA OD-1 | 中 | 确认 | `JdbcOutboundDeliveryRepository.java:103` 只认领 `next_attempt_at <= now`；`OutboundDelivery.java:70-90` 的 PENDING 均带 `now`，`scheduleRetry` 要求非空；PENDING 且为空即永不投递。建议的 CHECK 可落；`recipient_codes` 为空 ⇒ FAILED / SKIPPED 写成单向 |
| PI AQ-3 | 中 | 确认 | `JdbcAuthorizationRequestRepository.java:101` `rs.getBoolean("is_enabled")`，NULL 读成 false，违反 CLAUDE.md 第 6 条；按 `request_kind` 的列配对 CHECK 可落 |
| PA AR-2 | 中 | 降级为低 | 属实，但 `ProcessKey` 只有一个取值、单据 ID 为 UUIDv7 全局唯一，「同一单据 ID 被另一类型占用」只是理论可能；删 `document_type` 或加组合 CHECK 均可。另：唯一约束名 `uq_approval_requests_document`（:21）不合 `uk_` 规范，子代理未提 |
| PA NV-2 | 中 | 确认，建议升为高 | 迁移 :94-120 无「CONFIRMED ⇒ `confirmed_by` 非空」；触发器只管 UPDATE，INSERT 不受控；与 PI AQ-1 同类（双人确认的库内防线）。所有现有写入点不会撞上（种子与 `propose` 都是 PENDING），可直接落 |
| OW SD-1 | 中 | 确认 | main 迁移 :187 `subject_key text`，:202 示例 `employee:2001` | 已被 P5 覆盖为「保留例外」：P5 卡 §3.1「拆分 a 按最终形态在集成层新建」，P5 L-5 只改注释示例；拆分 a 前仍开放 |
| TS AE-5 | 中 | 确认 | `ExportAuditEntries.java:43` 先 `count(filters)`，`AuditSearch` 不强制时间范围，无过滤时对整张 10 年表计数；建议 `limit maxRows + 1` 判超限 |

## 误报与修正

1. **TS WY-1**：「R-1b 是否已关闭待核」——R-1b 范围已在 NEXT_BATCH_PLAN:299 把 `calendar_scope_kind` 挪到 W2 DA-17，子代理未发现；该项不是 R-1b 漏做而是计划偏离 ADR 原文。另外库内唯一性已正确，「阻断」过重。
2. **TS AB-1**：「漏档即丢数据」不成立于现实时钟偏差（需 ID 时钟落后超过 3 年在线期）；属 ADR 形态违反。
3. **TS 待用户决定 1、2**：已由用户决定（intent `2026-10-16-052923`），且与子代理倾向的「部分唯一索引」相反。
4. **PA AR-1**：漏看 `DecideTasks.java:106-108` 的 `hookDecidesReason`，建议的成对 CHECK 会拒绝设计内路径。
5. **OW SR-1 建议附带的 (d) `finished_at >= started_at`**（SR-2）：跨实例刷新 HELD 时有时钟偏差风险，子代理未提示。
6. **PA TK-1**：未提示 CLOSED 保留处理人、AUTOMATIC 无认领时间两种合法组合（建议本身没写错，但实施者照「全集」补时容易误加）。
7. **PI / TS 「`primary-key-exemptions.txt` 不存在」**（PI 未核实末条、TS SL-2）：main 属实，P5 已新建该文件（P5 卡 §15.1 末行），已被 P5 覆盖。
8. **OW、PA 两份按旧版门禁判定**（无 B0、B3a、O15、O16、附录 C）：OW 自己标了，PA 用「B3 精神」代判；O15 的 ADR 逐条对照在这两份里不完整，本复核补了 WY-1（不属 OW/PA）以外未发现新的 ADR 漏项，但未逐条重做 O15。
9. **PI N-1**：把老系统规则卡 RULE-347 当新系统依据（CLAUDE.md 第 1 条：只作证据），且未看到表是 `MIGRATION_ONLY`。

## 建议新增的跨表规则或守护

1. **双人确认的库内防线（跨表规则）**：凡状态含「已确认」类终态且有确认人列的表，CHECK 必须写「确认态 ⇒ 确认人与时刻非空」与「待确认 ⇒ 均空」，两人互异约束不得以 `IS NULL OR` 放行确认态。适用 `authorization_requests`、`numbering_rule_versions`、`source_references`（待核）、W2 的日历与角色版本。守护：读取 ADR-0030 登记的双人约束清单，逐表断言约束文本含确认人非空分支。
2. **原因必填终态（B3）的单一事实源**：状态机里声明「需原因」的转换（`requireReason` 调用点）与表上成对 CHECK 由同一测试比对（扩展 G-D12）；`hookDecidesReason` 这类放行分支须在规则卡登记，否则失败。
3. **注册表 `mutability` 与实际 SQL 一致**：`APPEND_ONLY` 不分 role 都要有拒改触发器（`start_rounds` 漏检，`DataArchitectureTest.java:279`）；`INSERT_DELETE` 的表出现 UPDATE 即失败（`browser_identifiers`、`idempotent_responses`、`consumed_events` 现为反例）。
4. **内联 CHECK 具名**：`DdlNamingTest.java:410-412` 对内联 CHECK 直接跳过，四份报告共列约 40 处未具名枚举 CHECK；G-D12 实现时去掉跳过并一次改基线。
5. **自由文本 / 应答副本列的内容清单（C4）**：`detail`、`before_text`、`after_text`、`change_reason`、`response_text`、`failure_message`、`last_error`、`handled_note` 每列在卡上有允许内容清单；守护对写入点做静态扫描（禁止拼 `address=`、`getMessage()` 直写）。
6. **计划改动 ADR 批次须留出处**：NEXT_BATCH_PLAN 把 ADR 条款挪批次（如 WY-1）时，同一 PR 在 ADR 影响面或 COORDINATOR_LEDGER 记一行（条款、原批次、新批次、裁定人），否则审查者按 ADR 原文判阻断。
7. **首次部署前清单**：ADR-0034 W2 卡、W2 主数据与日历版本化、审计分区（AE-2）、ADR-0043 实施 PR（R-2 gap、四段分级）合并为 dev 部署闸门的人工核对项，避免「已排期」被逐卡默认放过。
8. **会话与凭据等价列**：名字含 `session`、`token`、`ticket`、`cookie` 的非代理键列必须在 `data-classification.txt` 登记且不低于 `SENSITIVE`（S-1 现为反例）。

## 未核实

- positions 是否在 W2「主数据版本化 25 张表」清单内（P-1 的排期）。
- 测试夹具是否直接插入 `CONFIRMED` 而不带 `decided_by` 的授权申请、或无确认人的 `CONFIRMED` 编号版本（加 CHECK 前须 grep `backend/src/test`）。
- `RunStats` 各统计字段是否全为非空（SR-2(b)）。
- Spring Session JDBC 能否只存会话标识摘要（S-1 的中长期改法）。
- 生产库 PostgreSQL 版本是否 ≥ 15（`NULLS NOT DISTINCT`，WY-1 现形态依赖它）。
- TS-UNIQ 卡正文（只读到 one-1a-par 画像 :58 与 intent 摘要，卡未找到）。
- 门禁 v2 是否已被用户采纳合入 main（本复核按 v2 判）。
- 未重做四份报告的 O15 逐条对照、未核其余 71 条「中」与 84 条「低」。
