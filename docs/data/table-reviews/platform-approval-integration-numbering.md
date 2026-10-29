# 数据设计审查：platform_approval / platform_integration / platform_numbering（13 张自有与框架表）

- 审查：one-data 子代理（中文），2026-10-16，基线迁移 `backend/src/main/resources/db/migration/{platform_approval,platform_integration,platform_numbering}/*_baseline.sql`。
- 标尺：`docs/data/data-design-gate.md` §3。该文件在本分支只有 B1–B8、O1–O14、C1–C5、A1–A8、T1–T4、E1–E3，没有 B3a、O15 与附录 C；本次把「状态 CHECK ↔ 事件目录 ↔ Java 状态机三方一致」按 B3 / B5 的精神判定并标「B3a」，未找到 O15、C4 新要求原文，C4 按 ADR-0043 原文判。
- 方法：迁移 SQL 逐行读、Java 仓储 SQL 与领域类逐文件读、注册表与分级文件、事件目录、ADR-0033 / 0036 / 0039 / 0043 对照；脚本核对了列注释（有无、可空列是否写「为空表示」）、未具名 CHECK、跨模块 SQL 引用。未跑 EXPLAIN、未起库。
- 术语：下文「注册表」指 `backend/architecture/data-access-registry.yaml`。`ddl-naming-baseline.txt` 已登记的命名债（`current_round`、`handled_result`、`last_error`、各 `document_type`、`pattern`、未改名的主键与唯一约束、两个索引名）不重复计数。

## 逐表结论

| 表 | 结论 | 阻断 | 高 | 中 | 低 |
|---|---|---|---|---|---|
| platform_approval.approval_requests | 需修改 | 0 | 0 | 4 | 1 |
| platform_approval.start_rounds | 需修改 | 0 | 0 | 1 | 0 |
| platform_approval.approval_tasks | 需修改 | 0 | 0 | 1 | 3 |
| platform_approval.approval_task_records | 需修改 | 0 | 0 | 1 | 2 |
| platform_approval.process_archive_skips | 需修改 | 0 | 0 | 2 | 0 |
| platform_integration.event_publication | 通过（框架表，注册表 FRAMEWORK） | 0 | 0 | 0 | 0 |
| platform_integration.event_publication_archive | 需修改（仅注释） | 0 | 0 | 0 | 1 |
| platform_integration.outbound_deliveries | 需修改 | 0 | 0 | 4 | 3 |
| platform_integration.source_references | 需修改 | 0 | 0 | 1 | 2 |
| platform_numbering.numbering_rules | 需修改 | 0 | 0 | 1 | 2 |
| platform_numbering.numbering_rule_versions | 需修改 | 0 | 1 | 1 | 1 |
| platform_numbering.numbering_counters | 需修改 | 0 | 0 | 1 | 0 |
| platform_numbering.legacy_numbers | 需修改 | 0 | 0 | 2 | 0 |
| 跨表共性（不计入各表） | — | 0 | 1 | 3 | 2 |
| 合计 | | 0 | 2 | 22 | 17 |

整体判断：主键（O2）、外键索引（O10 的外键部分）、乐观锁（O5）、时间类型、`timestamptz`、无 PG `ENUM`、无 `CASCADE`、无软删除标志（`numbering_rules.is_active` 除外，见 NR-2）、表注释含粒度（框架表除外）、状态 `CHECK` 取值与 Java 枚举一致（B3a 取值层面）都通过。主要问题集中在「状态与字段成对约束没有落到库里」（B3 / B5）、「同 schema 内引用缺外键」（B4）、保留期与分级的口径（C3 / C4，多项要用户定）。

## 问题清单

### platform_approval.approval_requests

**AR-1（中）B3 / B5：「拒、退回、撤销必须有原因」只在应用里保证，库里没有成对 CHECK**
- 证据：`V20261014132000__platform_approval_baseline.sql:12-13`（`status` 含 REJECTED / RETURNED / CANCELLED，`result_reason` 可空，无成对 CHECK）；应用侧承担者 `DecideTasks.java:108`（驳回须有原因）、`ReturnToInitiator.java:42`、`ApprovalRequest.java:285,342-345`（撤回、撤销、转交、改派须有原因）；用户决定见 `BUSINESS_RULES.md` RULE-280 修订段「Rejects and returns still require a reason」（2026-10-14）。
- 反向也缺：`RUNNING`、`APPROVED` 时 `result_reason` 必须为空（`ApprovalRequest.restart` 把它置空、`approve` 传 null），库里没约束。
- 同类缺口还在 `approval_tasks`（`outcome_kind IN ('REJECTED','RETURNED')` 且 `effect_kind = 'BINDING'` 时 `result_reason` 应非空）与 `approval_task_records`（`action_kind IN ('REJECTED','RETURNED','TRANSFERRED','REASSIGNED')` 时 `record_reason` 应非空；`ApprovalRequest.java:188,198`）。三处合并成本条，不另计。
- 建议：`approval_requests` 加 `ck_approval_requests_result_reason`：`(status IN ('REJECTED','RETURNED','CANCELLED')) = (result_reason IS NOT NULL)`；`approval_tasks`、`approval_task_records` 加对应成对 CHECK；三表的 `result_reason`、`record_reason` 加 `CHECK (col <> '')`（见 X-1）。加签人（ADVISORY）的驳回是否也须原因，见「待用户决定」第 9 项。
- 依据：B3（原因必填的终态有原因列并成对 CHECK）、B5、§14.1 D2、D5。

**AR-2（中）O4 / O9 / O8：`document_type` 与 `process_key` 一一对应，库里不保证，且 `process_key` 无取值约束**
- 证据：迁移 `:7,:17,:21`；列注释 `:37`「与流程标识一一对应」；`ProcessKey.java`（`COST_TIMESHEET_APPROVAL → TIMESHEET`，仅一个取值）；`document_type` 的 CHECK 允许 QUOTE、TIMESHEET、JOURNAL_VOUCHER 任意组合；`process_key` 无 CHECK。
- 影响：可写入 `(COST_TIMESHEET_APPROVAL, QUOTE)`；`find` 按三列查（`JdbcApprovalRequestRepository.java:72`）会查不到，唯一约束 `(process_key, document_id)` 又不含 `document_type`，同一单据 ID 可能被另一类型占用。
- 建议：二选一：删 `document_type` 列（由 `process_key` 派生，符合 D1「派生值不存」）；或加 `process_key` 的枚举 CHECK 与 `(process_key, document_type)` 组合 CHECK。
- 依据：O4、O9（§14.1 D1）、O8。

**AR-3（中）O8：`current_instance_id` 可空，但每次写入都有值**
- 证据：迁移 `:11`（可空）、`:24`（为排除空值建了部分唯一索引）；`ApprovalRequest.java` 构造方法 `requireNonNull(instanceId)`；`JdbcApprovalRequestRepository.java:166-176` 插入与更新都写入；`readRow` 把空值包成 `new ApprovalInstanceId(null)`。
- 建议：改 `NOT NULL`；`uk_approval_requests_current_instance_id` 改为普通唯一约束。
- 依据：O8（默认 NOT NULL，可空列须有「为空表示」）、§2.4a-3。

**AR-4（中）B4 / O9：同 schema 内三处 `instance_id` 引用没有外键，当前轮次与实例是可由 `start_rounds` 重算的派生值却无一致性约束**
- 证据：`approval_requests.current_instance_id`（`:11`）、`approval_tasks.instance_id`（`:69`）、`approval_task_records.instance_id`（`:134`）都应指向 `start_rounds.instance_id`（`uk_start_rounds_instance_id`，`:53`），迁移里均无 `REFERENCES`；`current_round` 应等于该请求 `start_rounds` 的最大 `round_number`，也无约束。`ck_approval_requests` 的 `current_round >= 0`（`:10`）与注释「0 表示刚建立、尚未发起」（`:30`）冲突于 `ApprovalRequest.begin` 一律从 1 起、`start_rounds.round_number >= 1`（`:45`），0 不可达。
- 对照：`DATA_SCORECARD.md` K6 当年只补了 `approval_request_id` 外键，没补 `instance_id`。
- 建议：三处 `instance_id` 加指向 `start_rounds(instance_id)` 的外键（`approval_requests` 一侧用 `DEFERRABLE INITIALLY DEFERRED`，因为同事务先写请求再写轮次）；`current_round` 改 `>= 1`并改注释；注册表登记 `current_round`、`current_instance_id` 为投影（可由 `start_rounds` 重算）。
- 依据：B4、O9、§14.1 D2。

**AR-5（低）C1：注释**
- `result_reason` 注释只写「驳回、退回原因」（`:33`），撤销（CANCELLED）也写同一列（`ApprovalRequest.cancel`），且缺「为空表示」；`current_instance_id` 注释同缺。
- 建议：补全。依据：C1、O8。

### platform_approval.start_rounds

**SR-1（中）O7：注册表登记 `APPEND_ONLY`，库内没有禁止 UPDATE / DELETE 的触发器**
- 证据：迁移 `:42-55` 无触发器（同 schema 的 `approval_task_records`、`process_archive_skips` 都有，`:172-186`、`:209-218`）；注册表 `data-access-registry.yaml:176-182`（`start_rounds`：`role: AGGREGATE_CHILD, mutability: APPEND_ONLY`）；`DataArchitectureTest.java:279` 只对 `LOG_APPEND_ONLY`、`DIMENSION_VERSIONED` 角色检查触发器，`AGGREGATE_CHILD + APPEND_ONLY` 漏检。
- 建议：给 `start_rounds` 加禁止更新、删除、清空的触发器；并建议 one-quality 把守护扩到「注册表 `APPEND_ONLY` 的所有角色」。
- 依据：O7（§14.1 D8）。

### platform_approval.approval_tasks

**TK-1（中）B5 / O4：任务状态与办理字段的成对约束只有两条（CLAIMED、COMPLETED），缺反向与相关不变量**
- 证据：迁移 `:89-95`。`ApprovalTask.java`（`release`、`escalate` 回到 PENDING 时清空 `assignee`、`claimed_at`；`complete` 才写 `completed_at`、`outcome`、`effect`；`approved_hash` 只在「通过且非加签」时写；`markOverdue` 要求有 `deadline`；`escalate` 要求已 `overdue`）。
- 库里缺：PENDING 时 `assignee_id`、`claimed_at` 为空；非 COMPLETED 时 `completed_at`、`outcome_kind`、`effect_kind`、`result_reason`、`approved_hash` 全空；`outcome_kind` 与 `effect_kind` 同空同非空；`effect_kind = 'AUTOMATIC'` 时 `outcome_kind = 'APPROVED'`；`approved_hash` 非空当且仅当 `outcome_kind = 'APPROVED'` 且 `effect_kind = 'BINDING'`；`overdue_at` 非空时 `due_at` 非空；`escalated_at` 非空时 `overdue_at` 非空。
- 建议：补上述 `ck_approval_tasks_*`。原因必填见 AR-1。
- 依据：B3、B5、§14.1 D2。

**TK-2（低）O10：三个索引在仓储 SQL 里未见使用方（待核 EXPLAIN）**
- 证据：`ix_approval_tasks_assignee_id`（`:103`，`assignee_id, completed_at DESC WHERE COMPLETED`）、`ix_approval_tasks_instance_id`（`:97`）、`ix_approval_tasks_due_at`（`:98`）。全仓 `main` 下对 `approval_tasks` 的查询只有 `JdbcApprovalRequestRepository` 与 `JdbcApprovalTaskQueries`（`approval_request_id`、`task_id`、`status IN 开放`、`remind_at`、`escalate_at`、`created_at, task_id` 游标），没有按 `assignee_id`、单独按 `instance_id`、`due_at` 过滤排序的语句；已办列表走 `approval_task_records`。`findDue` 无 `LIMIT`（`JdbcApprovalTaskQueries.java:133-144`），到期任务多时结果集无上限（低）。
- 建议：EXPLAIN 确认后删多余索引；`findDue` 加上限与游标。依据：O10、§14.1 D19。

**TK-3（低）O3：无业务唯一键**
- 主键即流程引擎任务 ID（`:105` 注释），自然键不存在。建议在注册表写明豁免理由「聚合子行，标识由引擎任务沿用」。依据：§2.4a-1（子行可写理由豁免）。

**TK-4（低）C1：可空列注释缺「为空表示」**
- `escalate_at`、`approved_hash`、`completed_at`、`effect_kind`（`:112,:115,:120,:122`）。依据：O8、§2.4a-3。

### platform_approval.approval_task_records

**RC-1（中）O4 / B4：记录的 `task_id` 与 `approval_request_id` 分别建外键，不保证两者属于同一请求**
- 证据：迁移 `:146-147`；`approval_tasks` 的 `approval_request_id` 在 `:70`。可写入「任务属于请求甲、记录挂在请求乙」的行，`record_number` 序号按请求编（`:148`）也会错位。
- 建议：`approval_tasks` 加 `UNIQUE (task_id, approval_request_id)`，`approval_task_records` 改为复合外键 `(task_id, approval_request_id) → approval_tasks`，去掉单列的 `task_id` 外键。依据：O4、B4。

**RC-2（低）O9：`round_number`、`instance_id`、`node_key` 是任务或轮次的冗余副本，没有一致性约束**
- 证据：`:131-135`；注释写了「做这件事时」的快照意图（`:154,:158`），属有理由的反范式，但 `round_number` 与 `instance_id` 应一致（经 `start_rounds`）。
- 建议：AR-4、RC-1 落地后补 `(instance_id, round_number)` 复合引用，或在注册表登记为快照并写明不改。依据：O9。

**RC-3（低）B3a / B6 / A6：记录不覆盖升级与关闭，也不带法人**
- 证据：事件目录 `approval.yaml` 有 `ApprovalTaskEscalated`（升级会把已认领任务退回待认领并清空处理人，`ApprovalTask.java` `escalate`）与 `TaskClosed`，`approval_task_records.action_kind`（`:137`）没有对应取值；注释「每次对任务的认领、释放……一行」（`:150`）未列这两种。已办列表与逐轮历史（`historyOf`）因此看不到「何时被升级收回」。另外，审批时效按注释从本表统计（`approval_tasks.completed_at`，`:120`），行上无法人与业务日期，需经请求表联接取（A6）。
- 建议：先由用户或 one-architecture 定「升级、关闭是否进办理记录」（见待用户决定第 11 项）；A6 经联接可接受则在注册表写明。依据：B3a、B6、§14.1 D7。

### platform_approval.process_archive_skips

**PS-1（中）C3 / O7：保留期 `PERMANENT` 未经用户认可，且追加专用触发器使行永远无法清理**
- 证据：注册表 `:191-196`（`retention: {policy: PERMANENT}`）；`GUIDING_PRINCIPLES.md` §6 保留表无此表；迁移 `:209-218`（`BEFORE UPDATE OR DELETE` 与 `TRUNCATE` 一律拒绝）；`ProcessArchiveJob.java:194-199`（提醒 SQL 靠 `act_hi_procinst` 联接判断「仍在引擎里」，引擎里的实例人工处理后，跳过记录没有作用但永远留着）。
- 建议：保留期交用户定（见待用户决定第 2 项）；若要清理，触发器应只拒绝 UPDATE，并允许清理作业按期限 DELETE（作业名登记注册表）。依据：C3、§14.1 D12、ADR-0043 R-2（若 `object_key` 被判为 PI 则不可永久，待核）。

**PS-2（中）O8 / C4：`failure_message` 存引擎原始异常文本，无长度上限，且同一文本也写进 ERROR 日志**
- 证据：迁移 `:193`；`ProcessArchiveJob.java:176-181`（`failure.getMessage()` 同时写日志与入库）；`GUIDING_PRINCIPLES.md` §11 O-3 禁止日志出现数据库服务端错误详情。
- 建议：入库前截断到固定上限并只存错误类别与摘要，日志不带异常原文；加 `char_length` CHECK；列注释说明内容不含数据库错误详情。依据：O8、§2.4a-5、O-3。

### platform_integration.event_publication（框架表）

- 通过：框架拥有结构、`uuid` 主键由 Modulith 生成（ADR-0033 第 8 项豁免），保留 30 天与清理作业已登记（`EventPublicationJobs.java:79-81`，作业名 `platform.integration.event.cleanup`）。仅注释缺项，见 EA-1。

### platform_integration.event_publication_archive（框架表）

**EA-1（低）C1：表注释没有粒度，可空列注释缺「为空表示」**
- 证据：迁移 `:112`、`:118-121`；`completion_date`、`status`、`completion_attempts`、`last_resubmission_date` 注释无空值含义。
- 建议：补「粒度：每个监听者的每个已完成发布一行」与空值含义。依据：C1、O8。

### platform_integration.outbound_deliveries

**OD-1（中）B5 / O4：状态与字段的成对约束缺失，其中一条缺失会让投递静默停摆**
- 证据：迁移 `:44-60`。`JdbcOutboundDeliveryRepository.claimDue`（`:100-107`）只认领 `status = 'PENDING' and next_attempt_at <= now`，`next_attempt_at` 为空的 PENDING 行永远不被取到；`OutboundDelivery.java:79`（无收件人的投递直接是 FAILED 且收件人为空数组）。
- 库里缺：`status = 'PENDING'` 时 `next_attempt_at` 非空；`status = 'SENT'` 时 `sent_at` 非空；`FAILED`、`SKIPPED`、`DEAD_LETTER` 时 `last_error` 非空；`attempt_count >= 0`；`recipient_codes` 为空数组仅当 `status IN ('FAILED','SKIPPED')`；`event_count >= 0`。
- 建议：补 `ck_outbound_deliveries_*`。依据：B3、B5、§14.1 D2。

**OD-2（中）B6 / CS-1.8：`event_count` 是死列：聚合从不发事件**
- 证据：迁移 `:58`、注释 `:91`「每发一个事件加 1」；`OutboundDelivery.java` 无 `record(`（仅构造方法接收 `eventSequence`）；事件目录 `integration.yaml` 没有任何投递事件；`JdbcOutboundDeliveryRepository` 的更新语句从不写 `event_count`（`:100-145`）。
- 建议：删 `event_count`（并去掉 `AggregateRoot` 的事件序号依赖），或为投递登记事件并说明消费者；二选一由 one-architecture 定。依据：CS-1.8、CS-1.9、B6。

**OD-3（中）C3：注册表保留期与用户认可口径不一致**
- 证据：`GUIDING_PRINCIPLES.md:114`「成功的 90 天；失败与人工处理过的 1 年」；注册表 `:325-331` 只写 `90d`；`OutboundDeliveryPurgeJob.java` 实际两档（SENT、SKIPPED 90 天，FAILED、DEAD_LETTER 1 年）。「人工处理过的」没有对应状态或列（`handled_note` 是把卡片改为已处理说明，不是人工处理）。
- 建议：注册表的 `retention` 如实写两档并指向作业；「人工处理过的」的含义待用户确认（待用户决定第 3 项）。依据：C3、§14.1 D12。

**OD-4（低）O4：`purpose_kind` 决定 `subject_kind`，库里不保证**
- 证据：迁移 `:37,:39`；`DeliveryPurpose.java`（每个用途固定一个对象类别）。
- 建议：加 `ck_outbound_deliveries_purpose_subject`。依据：O4、ADR-0033 第 2 项第 10 条。

**OD-5（低）O10：清理作业的扫描路径无索引**
- 证据：`OutboundDeliveryPurgeJob.java` 的 DELETE 按 `status IN (...) AND updated_at < ?` 过滤；现有索引只有 `outbound_deliveries_due_idx`（PENDING）与 `outbound_deliveries_dead_idx`（`status` 上只含 DEAD_LETTER）。每日一次、行数小，仅记录。
- 建议：数据量大于预期时加 `(status, updated_at)` 部分索引。依据：O10。

**OD-6（中）C4：分级登记与 ADR-0043 第 3 项表不一致，自由文本列未判**
- 证据：`data-classification.txt:23-24` 仍为 `SENSITIVE`，ADR-0043 第 3 项表为 `INTERNAL + PI`（用户 U-4(b)，已定）；该文件改四段格式前，本条作为卡内分级表登记：`recipient_codes`、`skipped_recipient_codes`：INTERNAL、PI、用途「向审批人推送待办卡片」。`last_error`（外部接口返回文本，可能带收件人 ID）、`handled_note`、`title`、`description` 未判（待用户决定第 5 项）。
- 依据：C4、ADR-0043 第 1–3 项。

**OD-7（低）C1：8 个可空列注释缺「为空表示」**
- `description`、`next_attempt_at`、`last_error`、`sent_at`、`handled_note`、`handled_result`、`target_delivery_id`、`correlation_id`（以及 `response_code` 已写「未返回则为空」）。依据：O8。

### platform_integration.source_references

**SRF-1（中）C5：注册表血缘写 `NEW`，实际有迁入与同步来源**
- 证据：注册表 `:332-338`（`lineage: {source: NEW}`）；ADR-0033 第 2 项第 7、11 条（老系统人员、部门对照一次性导入，组织源同步分配）；`SourceReference.allocate` 写入即 `CONFIRMED`（`SourceReference.java:73`）。
- 建议：`lineage: {source: ORG_SOURCE, migrated_from: [老系统人员与部门对照], via: ONE_OFF_IMPORT}`（核算组织编码是人工登记，另写）。依据：C5（有迁入来源的表不论 `source` 取值写 `migrated_from` 与 `via`）、ADR-0033、ADR-0036。

**SRF-2（低）O10：列表路径没有索引覆盖**
- 证据：`JdbcSourceReferenceQueries.java:85-100`（`source_system = ? AND source_object_kind = ? [AND confirmation_status = ?] ORDER BY source_reference_id LIMIT ?`，游标 `source_reference_id > ?`）；索引只有 `uk_*_source_id`、`uk_*_own_id`（部分，按来源 ID 与自有 ID）、`ix_*_pending`（只 PENDING）、`ix_*_own_id`（`own_id` 开头）。按系统与类别分页浏览已确认行需要排序。行数预期为千级，仅记录。
- 建议：加 `(source_system, source_object_kind, source_reference_id)`，或把游标键改为已有索引的列。依据：O10、§14.1 D19。

**SRF-3（低）B3a / B6：同步分配的对照直接成为 CONFIRMED，没有对应事件（待核）**
- 证据：事件目录 `integration.yaml` 只有人工登记、确认、拒绝、取代、拒绝登记五个事件；`AllocateOwnId` 路径用 `insertIfAbsent`（`JdbcSourceReferenceRepository.java:41-42`）不发事件。分配可由 `created_by IS NULL` 与来源同步追溯，是否需要事件留痕请 one-architecture 判。依据：B3a、B6。

### platform_numbering.numbering_rules

**NR-1（中）CS-1.8 / CS-1.9 / D18：四个「停用」列仍在基线里且种子还在写**
- 证据：迁移 `:4`（注释「停用的旧列，不再写入」）、`:8-10,:15`（`pattern`、`reset_policy`、`start_value`、`confirmation_status`）；`:171-182` 的种子 INSERT 写入这四列再由 SELECT 搬到版本表；`JdbcNumberingRuleRepository` 的更新只写 `updated_at`、`version`、`event_count`（`:114-122`）。基线开放期（`migration-baseline-open`）应直接删列。
- 建议：删四列与它们的 CHECK、默认值；种子直接写版本表。依据：CS-1.8、CS-1.9、O12（基线开放期改基线）、§14.1 D1。

**NR-2（低）O7：`is_active` 是只读、永不写的标志列**
- 证据：迁移 `:11`；全仓 `main` 仅 `JdbcNumberingRuleRepository.java:47,:55` 读取（`where is_active`），无任何写入。
- 建议：删列与两处谓词；若要停用规则，按版本表表达。依据：§2.4a-4（禁 `is_deleted` 类标志）、CS-1.8。

**NR-3（低）B5：种子 `event_count = 1`，但没有对应事件**
- 证据：迁移 `:172-175`（`version=1, event_count=1`）；种子版本为待确认，目录里首个事件是确认时才发。
- 建议：种子 `event_count = 0`，或登记「种子」事件。依据：B5。

### platform_numbering.numbering_rule_versions

**NV-1（中）D17 细则 2 / D15：配置型版本表缺「决定出处」**
- 证据：迁移 `:94-120` 有版本号、生效日、作者、确认人、确认时间，没有 `decision_ref`；`METHODS.md:286`（配置类版本表须含批准人、决定出处）、`:409`。
- 建议：加 `decision_ref text`（含义：用户决定的 intent 或 ADR 编号；种子行写 ADR-0010 第 5 项）与长度、非空串 CHECK。依据：D15、D17 细则 2。

**NV-2（高）B3 / B5：`CONFIRMED` 状态不要求有确认人与确认时间**
- 证据：迁移 `:104,:114-119` 只约束「作者不得确认自己的版本」「两个确认人不同」「确认人与确认时间成对」；`JdbcNumberingRuleRepository.update`（`:148-166`）写入状态与确认列；触发器 `guard_rule_version_change`（`:141-163`）只管状态流转，不管 `CONFIRMED` 必须有确认。`NumberingRule.completeIfAllGiven`（`NumberingRule.java:187-193`）在业务确认齐全后才置 `CONFIRMED`，凭证号另需财务确认。
- 建议：加 `ck_numbering_rule_versions_confirmed`：`status <> 'CONFIRMED' OR confirmed_by IS NOT NULL`；凭证号需要财务确认的条件依赖单据类型，库里可在触发器内查 `numbering_rules.document_type`，落不成 CHECK 则在注册表写明由聚合保证。依据：B3、B5、ADR-0027 第 2 项第 4 条。

**NV-3（低）O4（待核）：版本选择按版本号取最大，库里不约束生效日次序**
- 证据：`NumberingRule.java:84-85`（`inForceOn` 取已开始的最高版本号）；`propose`（`:105-124`）不检查新版本生效日与既有已确认版本的关系；迁移无 `(numbering_rule_id, effective_from_date)` 的唯一或排他约束。同一生效日有两个已确认版本时按版本号定，结果确定，但属于隐含规则。
- 建议：用户确认规则语义后在库里落约束（待用户决定第 6 项）。依据：O4、O6。

### platform_numbering.numbering_counters

**NC-1（中）O4 / O8：取值与格式约束不全**
- 证据：迁移 `:34-45`：`document_type` 无 CHECK 也无指向 `numbering_rules(document_type)` 的外键（`uk_numbering_rules_document_type` 可作被引用键，注册表基线登记其为「可扩展登记项」）；`period_key` 注释写了 `- / 2026 / 202610 / 20261014`，无格式 CHECK；`last_value` 无 `>= 1`（`start_value` 恒 ≥ 1）。
- 建议：加外键或 CHECK；`period_key ~ '^(-|[0-9]{4}|[0-9]{6}|[0-9]{8})$'`；`last_value >= 1`。依据：O4、O8（`_key` 格式）、§2.4a-5。

### platform_numbering.legacy_numbers

**LN-1（中）O10：前缀检索路径没有可用索引**
- 证据：`JdbcLegacyNumberRepository.java:47-57`（`source_system = ? AND document_type = ? AND starts_with(legacy_number, ?) AND legacy_number > ? ORDER BY legacy_number LIMIT ?`）；现有索引 `uk_legacy_numbers_...`（`:68`）为 `(source_system, document_type, legacy_number)`，`starts_with` 不是索引条件，只能从范围起点或游标处顺序扫描并过滤；老单据量（仅工时单近 24 个月已 12 万张，`ENTERPRISE_INFO_MAP_DRAFT.md` OBJ-66）下前缀靠后的检索接近全范围扫描。数据库排序规则未核，建在默认排序规则上的 B 树对 `LIKE 'x%'` 同样不可用。
- 建议：为该路径建 `text_pattern_ops` 索引并把条件改成可用索引的前缀写法，或在查询里用范围条件 `legacy_number >= prefix AND legacy_number < prefix_upper`；先 EXPLAIN。依据：O10、§14.1 D19。

**LN-2（中）B7 / O8：取值与单据类型约束**
- 证据：迁移 `:59`（`source_system` 取值含 `ORG_SOURCE`，组织源不持有单据编号，对单据原编号无意义）；`:60`（`document_type` 无 CHECK，基线已登记）。
- 建议：`source_system` 取值收窄到有单据编号的来源；`document_type` 补 CHECK 或外键。依据：B7、CS-1.8。

### 跨表共性问题

**X-1（中）O8 / §2.4a-5：全部文本列没有长度上限，也没有「禁空串」CHECK**
- 证据：三个 schema 的迁移里无 `char_length`、`<> ''`（`grep` 全 `db/migration` 仅 `platform_identity` 有 `lower()` 唯一索引）；涉及列：`process_key`、`node_key`、`node_name`、`summary_text`、`result_reason`、`record_reason`、`object_key`、`failure_message`、`title`、`description`、`target_url`、`handled_note`、`last_error`、`legacy_number`、`template_text`、`period_key`。`template_text` 格式只在应用里校验（`NumberingPattern`）。
- 建议：按列加 `ck_<表>_<列>`（上限与格式由卡作者按用途定）；此项属于全库共性，建议由 one-quality 的守护（§2.4a 第 5 条标「守护」）一次性补。依据：O8、§2.4a-3、5。

**X-2（低）O8 / NAMING_STANDARD §2.4：12 处自有表枚举 CHECK 未具名**
- 证据：`approval_requests.status:12`、`document_type:17`；`approval_tasks.status:72`、`outcome_kind:84`、`effect_kind:85`；`approval_task_records.action_kind:137`；`outbound_deliveries.status:44`、`kind:54`；`numbering_rules.reset_policy:9`、`confirmation_status:15`；`numbering_rule_versions.reset_policy:99`、`status:104`；另 2 处在框架表（`event_publication`、`_archive` 的 `status`）。规范要求枚举 CHECK 命名为 `ck_<表>_<列>`，`ddl-naming-baseline.txt` 没登记这些行，说明守护未覆盖行内 CHECK（待核）。
- 依据：NAMING_STANDARD §2.4（DDL-22）。

**X-3（低）O7 / CS-1.8：插入后不可改的表保留 `updated_at`**
- 证据：`start_rounds:49`、`approval_task_records:144`、`process_archive_skips:196`、`legacy_numbers:66`（注释自己写「行不可更新，恒等于创建时间」，`:80`）。建议：删列，或在注释写明保留理由。

**X-4（中）C4：分级登记缺第二维度，PI 判定要等 ADR-0043 实施 PR**
- 证据：`data-classification.txt` 仍为三段格式；本次各表的 PI 判定写在卡里：见 OD-6，另见「待用户决定」第 4、5 项（`source_references.source_id`、各自由文本原因列）。其余 UUID 代理键（`initiator_id`、`assignee_id`、`actor_id`、`on_behalf_of_id`、`author_id`、`confirmed_by`、`created_by`、`decided_by`）按 ADR-0043 第 2 项「代理键不登记」。
- 依据：C4、ADR-0043 第 2、4 项。

**X-5（中）B1：平台能力在信息图里没有对象，也未登记为「技术表」**
- 证据：`ENTERPRISE_INFO_MAP_DRAFT.md` 全文检索无审批请求、编号规则、来源对照、外发投递对应的 OBJ（只有 OBJ-28 工作日历提到「审批时限」）；注册表角色为 `AGGREGATE_ROOT`（`approval_requests`、`source_references`）、`CONFIG_VERSIONED`（`numbering_rules`）属业务类。
- 建议：交 one-architecture 判：补「平台对象」，或把这几张表在注册表改登记为技术表并写服务于哪个用例。B1 规定业务类表缺对象时「停，交 one-architecture，不自行建表」，这些表已建，按补审处理，不回滚。依据：B1。

**X-6（高）D7 / D17 细则 6：使用方不记所用编号规则版本**
- 证据：全库 `db/migration` 里除 `platform_numbering` 外没有 `numbering_rule_version_id` 一列（`grep` 无结果）；`cost_timesheet.timesheets` 有 `timesheet_number` 但不记发号所用版本；ADR-0033 第 1 项第 5 条、K11 都要求「已发的编号可追溯到版本」。目前靠 `inForceOn(业务日期)` 重算，不可审计。
- 建议：单据表带 `numbering_rule_version_id`（该卡属 `cost.timesheet`，建议由 one-data 转 `cost.timesheet` 的下一张涉数据卡）。依据：D7、D17 细则 6、ADR-0033。

## 待用户决定

1. **审批四表的保留期**（`approval_requests`、`start_rounds`、`approval_tasks`、`approval_task_records`）：注册表写 `BUSINESS_RECORD`，`GUIDING_PRINCIPLES.md` §6 没有这些表的条目；只有「审批结论按 10 年（用户 2026-10-14）」针对审计。要定：过程表是否同样 10 年、是否归档到对象存储、清理作业名。注册表 `platform_approval.act_*` 的 `3y`（`:197-202`）也无用户认可记录（`DATA_SCORECARD.md` §5 写「列入下一轮保留期确认」）。
2. **`process_archive_skips` 的保留期**（PS-1）：永久（当前）还是在引擎历史清理后按期限清理。
3. **`outbound_deliveries` 的保留口径**（OD-3）：「失败与人工处理过的保留 1 年」里「人工处理」指什么状态或列；注册表是否写成两档。
4. **`source_references` 的分级与保留期**：`source_id` 对 `EMPLOYEE` 类别是来源系统的人员标识，若按 ADR-0043 第 2 项判为 PI，则注册表 `PERMANENT`（`:332-338`）违反 R-2（PI 表不得永久，除非有用户决定出处）。要定：判 PI 与否、永久或期限。
5. **自由文本列的分级**：`result_reason`、`record_reason`（审批人手填，可能含姓名等）、`handled_note`、`last_error`（外部返回，可能含企业微信 ID）、`summary_text`（当前由工时单拼成「编号 日期 小时」，无 PI）。并且这些原因文本会进入事件载荷（`approval.yaml` 的 `ApprovalCompleted.reason`、`TaskTransferred.reason` 等）与 `event_publication.serialized_event`（30 天）。要定：是否允许原因文本进事件与日志。
6. **编号规则版本的生效日语义**（NV-3）：新版本生效日是否必须晚于已确认版本；`decision_ref` 填什么。
7. **`legacy_numbers` 的唯一范围**：`(source_system, document_type, legacy_number)` 不含法人（`legal_entity_id` 可空）。若老系统的同一原编号在不同法人下重复，迁入会因唯一冲突失败。需 one-ops 按 ADR-0037 取生产证据（重复情况只给计数），登记一行。
8. **审批请求被驳回、撤销后可否再发起**：`ApprovalRequest.MACHINE`（`ApprovalRequest.java:70-75`）允许 `REJECTED → RUNNING`、`CANCELLED → RUNNING`，迁移列注释与 `ApprovalStatus` 注释只说「RETURNED 后重提」。
9. **加签人（ADVISORY）的驳回是否也须填原因**（AR-1 的 CHECK 写法取决于此）。
10. **B1**（X-5）：平台能力入信息图还是登记为技术表，由 one-architecture 与用户定。
11. **升级、关闭是否进办理记录**（RC-3）。
12. **`event_count` 的去留**（OD-2）：投递聚合是否需要事件。

## 需 opus 复核的高风险项

1. **AR-1 与 TK-1**：成对约束的全集决定了库能否成为状态机的最后防线；加签驳回、系统退回的原因规则要结合规则卡与用户决定（待用户第 9 项）判断，写错会拒绝合法数据。
2. **AR-4 与 RC-1**：同事务循环引用（请求 ↔ 轮次）用延迟外键还是复合外键，是结构设计，涉及仓储写入顺序与并发（`lockForStart` 用 advisory lock，`JdbcApprovalRequestRepository.java:65`）。
3. **SRF 的 PI 与保留期、OD-6、X-4**：个人信息判定与 ADR-0043 R-2 的适用，属于安全与合规判定。
4. **PS-2**：异常原文入库与入日志，是否违反 O-3，需 opus 核对生产日志口径。
5. **X-6 与 NV-3**：编号规则版本的使用方留痕与生效日语义，牵涉 `cost.timesheet` 的取号流程与已发编号的可审计性。

## 未核实

- 未跑 `EXPLAIN`、未起库：TK-2、SRF-2、LN-1、OD-5 的索引判断均出自 SQL 与索引定义对照；数据库排序规则（影响 LN-1）未核。
- B8（用语）：未逐词核对 `terms.yaml`，依赖 `GlossaryTest`（G37）；本次抽查的 `start_round`、`approval_task_record`、`process_archive_skip`、`outbound_delivery`、`source_reference` 的表名在 `terms.yaml` 里未按 `snake` 找到同名项（`approval_request`、`approval_task`、`numbering_*`、`legacy_number` 有），是否由 `common_words` 覆盖未核。
- 规则卡只读了与审批原因（RULE-280 修订段）、状态生命周期相关的部分；`rules/APPROVAL_DEADLINE_RULES.md`、`TIMESHEET_ROUTING_RULES.md` 未逐条对照 `due_at`、`remind_at`、`escalate_at` 的计算与存储。
- 事件载荷 Java record 与 `events/*.yaml` 未逐字段比对（E1 只核了载荷里有无 `SENSITIVE`、`RESTRICTED`、PI 直接标识与来源标识：来源对照事件只带 `ownId`，符合）。`DocumentSubmittedForApproval` 的 `publisher` 写 `platform.approval`，聚合与测试却是 `Timesheet`（`approval.yaml:162-172`），发布方应写发布它的模块，未做修正建议，交事件目录维护者判。
- `platform_approval.act_*`（Flowable 引擎表）不在迁移里，未审。
- ADR-0036 第 3 条规定的 `migration_batches` 表与 `source_references` 三个新列、ADR-0039 规定的 `external_identities` 与 `legacy_numbers` 搬到 `platform_integration`：基线里都还没有（ADR-0039 写明「首个迁入阶段前」搬表，表现为空），按 CS-1.8 不算缺陷，但 `legacy_numbers.migration_batch_id`（`:64`）已是 `NOT NULL` 而被引用的批次表尚不存在，直到 R-1b 前该列只是无处可查的 ID。
- 未读 `DATA_CONTEXT.md`、`BASELINE_TABLE_REVIEW.md`（后者在本分支不存在）；`DATA_SCORECARD.md` 为 2026-10-14 版本，其 K 项多数已修，未逐条对账。
