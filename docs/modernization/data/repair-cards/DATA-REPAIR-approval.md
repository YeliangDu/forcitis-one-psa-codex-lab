---
卡号: DATA-REPAIR-approval
状态: 进行中
负责会话: one-1a-par
前置: R-1b-P5
---
# 派工卡 DATA-REPAIR-approval：platform_approval、platform_integration、platform_numbering 已建表的数据设计缺陷修复（草稿）

- 草稿（第二版）：one-data 起草助手，2026-10-16；第二版并入用户 2026-10-16 的决定、统一分级（阻断 0）与 `SCAN_SUPPLEMENT.md` 补入；待 one-data 终审、one-coordinator 派工。
- 证据来源：`docs/modernization/data/review-parts/platform_approval_integration_numbering.md`（下称 PA）与 `OPUS_VERIFY.md`（下称 OV）；以 OV 为准。「:N」行号指 PA 审查时 main 基线中对应迁移文件：`platform_approval/V20261014132000__platform_approval_baseline.sql`、`platform_integration/V20261014133000__platform_integration_baseline.sql`、`platform_numbering/V20261014100100__platform_numbering_baseline.sql`；R-1b P4 / P5 已改这些文件，实施前逐条重核行号。PA 按旧版门禁判定（无 B3a、O15、附录 C，OV 误报与修正第 8 条）。
- `SCAN_SUPPLEMENT.md`（下称 SS）：全量机械扫描与补审，行号取 `origin/main`（a27bf877），与上面 PA 的旧行号可能差几行；SS 的 O15 缺项已用 `git show origin/claude/one-1a-next-4-r1b-p5:backend/src/main/resources/db/migration/...`（下称 P5 分支，18f2c807）核对是否被覆盖。
- 级别以统一分级为准（`DATA_DESIGN_GATE.md` §4a、`BASELINE_TABLE_REVIEW.md` §1、§3）：阻断 0；本卡 AR-1 由高改中、AR-4 随之为中、NV-2 高、X-6 高（并入 TS-UNIQ，不起条目）。
- 「待核」表示分报告与复核报告都没有给出证据，实施者先核实再改。

## 1. 卡号与类别
- 卡号：DATA-REPAIR-approval
- 类别：缺陷修复（ 第 9 条）
- 批次：已建表数据设计审查修复批（门禁 v2 §1.2 第 6 条）；一个小 PR 直接合入 main
- 建议负责会话：one-1a-next-4
- 内部顺序：AP-01 第 1 步（删 `hookDecidesReason` 分支）必须先于 AP-01 / AP-02 的 CHECK；AP-03 与 AP-04 同改审批表，同一提交；AP-14 须在 one-architecture 对 ADR-0010 第 5 项判定后再动种子
- 排期：本卡排在 dev 部署之后（用户「同意，不挡 dev」，`BASELINE_TABLE_REVIEW.md` §3 末段）；本卡本身不是闸门，闸门只含 ADR-0034 W2、W2 版本化、审计分区、ADR-0043 实施 PR（见 README）

## 2. 依据
- 门禁 v2：B3、B5、O4、O7、O8、O10、C3、C5
- DATA_DESIGN_CHECKLIST §14.1 D2、D12、D15、D17 细则 2；§12.2；ADR-0038；ADR-0033；ADR-0027 第 2 项第 4 条；NAMING_STANDARD §2.4a-1（外键目标为主键）、§2.4a-5
- 用户决定（本卡「已定」，见第 5 节）：「驳回、撤销后可原单再发起；加签人驳回也须填原因；删掉免填放行」（收件箱 2026-10-16-one-data-approval-resubmit；intent `2026-10-16-085331-one-data-审批被驳回-被撤销后能不能再发起.md`）
- 规则卡：RULE-280 修订段「驳回与退回仍须原因」（`BUSINESS_RULES.md`，2026-10-14）；GUIDING_PRINCIPLES §11 O-3（日志不含数据库服务端错误详情）
- 复核编号：SS §1（`summary_text`）、§2、§3、§4（第 5、6、7、12、13 组）、§7（B7 第 1、2、4 条）、补审 B7-1 与 O15 第 2 条；OV「AR-1 确认，改法需修正」「高风险项判断 PA 第 1、2、4 条」「PA OD-1 确认」「PA NV-2 确认，升为高」「PA AR-2 降为低」；建议新增规则 2、3、5
- 分报告编号：PA AR-1、AR-3、AR-4、TK-1、RC-1、SR-1、OD-1、OD-2、OD-3、PS-2、SRF-1、NR-1、NR-2、NR-3、NV-1、NV-2、NC-1、LN-1、LN-2；SS 新增：B7 `document_type`、`summary_text`、第 4 节第 7 组

## 3. 范围与卡上文件

允许改动（越界先停下报告，不先做后报）：
- 迁移基线：`platform_approval/V20261014132000__platform_approval_baseline.sql`；`platform_integration/V20261014133000__platform_integration_baseline.sql`（只限 `outbound_deliveries`）；`platform_numbering/V20261014100100__platform_numbering_baseline.sql`（`numbering_rules`、`numbering_rule_versions`、`legacy_numbers`）
- `backend/architecture/data-access-registry.yaml`（审批四表、`outbound_deliveries`、`source_references`、numbering 各表条目）
- `backend/architecture/data-classification.txt`（只限 `approval_requests.summary_text` 一行，AP-15）
- Java：`backend/src/main/java/com/forcitis/one/platform/approval/application/DecideTasks.java`（AP-01 第 1 步）、`.../approval/infrastructure/flowable/ProcessArchiveJob.java`（AP-08）、`backend/src/main/java/com/forcitis/one/platform/approval/domain/ApprovalRequest.java`（AP-01 域内补校验，one-data 裁定⑥）、`.../platform/integration/domain/OutboundDelivery.java` 与 `infrastructure/persistence/JdbcOutboundDeliveryRepository.java`（AP-06、AP-07）、`.../platform/numbering/infrastructure/persistence/JdbcNumberingRuleRepository.java`、`JdbcLegacyNumberRepository.java`（AP-09 至 AP-12）
- 测试：上述类的现有测试与新增 CHECK 集成测试；`ProcessArchiveJobIT` 只改失败消息断言（须单列理由）

归属：本卡独占上述表的约束、索引与列注释片段。

不碰：`approval_tasks` 等表的列与类型（P4 已定）；`EventEnvelope`、`AggregateReference`、kernel `AggregateRoot`（共享构件，需改时停下报告）；`act_*` 引擎表；验收测试目录；他卡文件。

## 4. 条目清单

### AP-01　AR-1　中（统一分级：高→中；OV 确认、改法已修正；用户已定）　「驳回、退回、撤销必须有原因」库内无成对 CHECK
- 问题：`approval_requests.status` 含 REJECTED / RETURNED / CANCELLED，`result_reason` 可空，无成对 CHECK；`approval_task_records.record_reason`、`approval_tasks.result_reason` 同缺。
- 证据：PA `:12-13`；`DecideTasks.java:108`、`ReturnToInitiator.java:42`、`ApprovalRequest.java:285,342-345,188,198`；RULE-280 修订段。OV：`DecideTasks.java:106-108` 的 `hookDecidesReason`（节点配了前置钩子时允许无原因驳回）会让 `Reason.ofNullable` 得空、写成 `REJECTED` + `result_reason` 空，直接加成对 CHECK 会拒绝这条设计内路径；目前全仓无 `ApprovalHook` 实现，现有数据不会撞上。
- 改法（顺序不可颠倒）：
  1. 先删 `DecideTasks` 的 `hookDecidesReason` 放行分支（用户已定「删掉免填放行」；钩子要原因就由钩子返回原因；该分支与 RULE-280 修订「驳回与退回仍须原因」冲突）；改动在 PR 单列。
  2. 再加 `ck_approval_requests_result_reason`：`(status IN ('REJECTED','RETURNED','CANCELLED')) = (result_reason IS NOT NULL)`（RUNNING、APPROVED 时为空：`ApprovalRequest.restart` 置空、`approve` 传 null）。用户已定状态机保留 `REJECTED → RUNNING`、`CANCELLED → RUNNING`（原单再发起），本 CHECK 与之相容：`restart` 先把 `result_reason` 置空再转 RUNNING。
  3. `approval_task_records`：`ck_approval_task_records_record_reason`：`action_kind IN ('REJECTED','RETURNED','TRANSFERRED','REASSIGNED') ⇒ record_reason IS NOT NULL`（写成 `NOT (…) OR …`）；撤销、撤回类 `action_kind` 取值是否存在待核（`ApprovalRequest.java:188,198` 要求原因，对照 `ApprovalTaskRecord` 取值全集后一并纳入）。
  4. `approval_tasks`：单向写法 `ck_approval_tasks_result_reason`：`outcome_kind IN ('REJECTED','RETURNED') ⇒ result_reason IS NOT NULL`（写成 `NOT (…) OR …`）；**用户已定加签人（ADVISORY）驳回也须填原因**，故不再带 `effect_kind = 'BINDING'` 条件，覆盖加签人驳回；仍不写成双向（非驳回、退回的完成态原因可空）。待核：`DecideTasks` 加签人分支与 `ApprovalTask.complete` 是否都经 `requireReason`，没有则补（同样在 PR 单列）。
  5. 三列再加 `<> ''`（PA X-1：全库文本列无禁空串约束，本卡只覆盖这三列）。
- 预期表结构 diff：`approval_requests`：新增 `ck_approval_requests_result_reason`（含非空串）；`approval_task_records`：新增 `ck_approval_task_records_record_reason`；`approval_tasks`：新增 `ck_approval_tasks_result_reason`。
- 验收要点：删分支后，节点配钩子的驳回在无原因时失败并告警；加签人驳回无原因被拒；`REJECTED → RUNNING`、`CANCELLED → RUNNING` 再发起仍通过；`RUNNING` / `APPROVED` 带 `result_reason` 被拒；撤回、转交、改派、驳回、退回无原因被拒；扩展 G-D12：`requireReason` 调用点与表上成对 CHECK 由同一测试比对（OV 建议新增规则 2）。
- 依据：门禁 B3、B5；D2、D5；OV「高风险项判断 PA 第 1 条」；用户决定（第 5 节「已定」第 1 条）；SS §4 第 5、6、7 组。

### AP-02　TK-1　中　`approval_tasks` 任务状态与办理字段的成对约束不全（注意两个坑）
- 问题：迁移只有 CLAIMED、COMPLETED 两条成对约束，缺反向与相关不变量。
- 证据：PA `:89-95`；`ApprovalTask.java`（`release`、`escalate` 回 PENDING 时清空 `assignee`、`claimed_at`；`complete` 才写 `completed_at`、`outcome`、`effect`；`approved_hash` 只在「通过且非加签」时写；`markOverdue` 要求有 `deadline`；`escalate` 要求已 `overdue`）；OV 核过 `ApprovalTask.java:58-158`，建议条目可落，但须避开：
  - **坑 1**：CLOSED 任务可保留 `assignee_id`、`claimed_at`（`close()` 不清空），**不得写**「非 CLAIMED ⇒ 处理人为空」。
  - **坑 2**：AUTOMATIC 的 COMPLETED 有 `assignee_id`、无 `claimed_at`、无 `approved_hash`（`passedBySystem`），**不得写**「COMPLETED ⇒ `claimed_at` 非空」。
- 改法：只落以下几条，逐条对 `ApprovalTask.java` 状态机与 `ApprovalTaskTest`（名称待核）核对：
  - `status = 'PENDING' ⇒ assignee_id IS NULL AND claimed_at IS NULL`（仅 PENDING；不对 CLOSED 写空）
  - `outcome_kind` 与 `effect_kind` 同空同非空
  - `effect_kind = 'AUTOMATIC' ⇒ outcome_kind = 'APPROVED'`
  - `approved_hash IS NOT NULL ⇒ outcome_kind = 'APPROVED' AND effect_kind = 'BINDING'`（单向，不要求 BINDING 通过必有 hash，因 AUTOMATIC 无 hash 已排除）
  - `overdue_at IS NOT NULL ⇒ due_at IS NOT NULL`；`escalated_at IS NOT NULL ⇒ overdue_at IS NOT NULL`
  - 「非 COMPLETED ⇒ `completed_at`、`outcome_kind`、`effect_kind`、`result_reason`、`approved_hash` 全空」：**待核** CLOSED 是否写 `completed_at`（OV 只点了 CLOSED 保留处理人与认领时间）；核实前不落
- 预期表结构 diff：`approval_tasks`：新增上述具名 CHECK（`ck_approval_tasks_*`）。
- 验收要点：认领、释放、升级、关闭、自动通过、加签各路径写入成功；CLOSED 保留处理人的行、AUTOMATIC 完成行通过；违反行被拒。
- 依据：门禁 B3、B5；D2。

### AP-03　AR-4 + AR-3　中（统一分级：AR-4 随 AR-1 为中）　三处 `instance_id` 无外键；`current_instance_id` 可空
- 问题：`approval_requests.current_instance_id`、`approval_tasks.instance_id`、`approval_task_records.instance_id` 都应指向 `start_rounds(instance_id)` 却无 `REFERENCES`；`current_round >= 0` 与 `ApprovalRequest.begin` 一律从 1 起冲突；`current_instance_id` 每次写入都有值却可空。
- 证据：PA `:11,:24,:69,:134,:10,:30,:45,:53`；`ApprovalRequest.java` 构造方法 `requireNonNull(instanceId)`；`JdbcApprovalRequestRepository.java:166-176`；OV「高风险项判断 PA 第 2 条」：三处外键可行，`approval_requests` 一侧须 `DEFERRABLE INITIALLY DEFERRED`（同事务先写请求后写轮次，`start_rounds.approval_request_id` 反向引用请求，形成环），与 `lockForStart` 咨询锁（`JdbcApprovalRequestRepository.java:65`）无冲突（外键只加行级共享锁）。
- 改法：
  - 三处加 `fk_approval_requests_current_instance_id`（`DEFERRABLE INITIALLY DEFERRED`）、`fk_approval_tasks_instance_id`、`fk_approval_task_records_instance_id` → `start_rounds(instance_id)`（目标为 `uk_start_rounds_instance_id`，待核是否通过 G-DDL1「外键目标必须主键」守护，见 AP-04 同类问题）
  - `current_round` 的 CHECK 改 `>= 1`，注释改；`current_instance_id` 改 `NOT NULL`，`uk_approval_requests_current_instance_id` 由部分唯一改普通唯一；`readRow` 的 `new ApprovalInstanceId(null)` 路径改为缺失即失败（待核 P4 后该行是否还在）
  - 注册表登记 `current_round`、`current_instance_id` 为投影（可由 `start_rounds` 重算）
  - SS §4 第 5 组「`current_instance_id` ⇔ `current_round > 0`」随本条解决：列改 `NOT NULL`、`current_round >= 1` 后无须再写成对 CHECK
- 预期表结构 diff：`approval_requests`：`current_instance_id` 改 `NOT NULL`，唯一索引改普通唯一，`current_round` 的 CHECK 改写，新增 DEFERRABLE 外键；`approval_tasks`、`approval_task_records`：各新增一个外键。
- 验收要点：发起审批（先请求后轮次，同事务）、重新发起、转交、归档各路径通过；指向不存在实例的行被拒。
- 依据：门禁 B4、O9、O8；D2；SS §3（`current_instance_id:11` 无空值说明，随 NOT NULL 消失）。

### AP-04　RC-1　中　办理记录的 `task_id` 与 `approval_request_id` 分别外键，不保证同属一个请求
- 证据：PA `:146-147`、`:70`、`:148`（`record_number` 按请求编）；OV：复合外键 `(task_id, approval_request_id)` 可行，代价是 `approval_tasks` 多一个唯一约束。
- 改法（已被 PR-6 设计节取代：复合外键名沿用 `fk_approval_task_records_task_id`，不改名）：`approval_tasks` 加 `uk_approval_tasks_task_id_approval_request_id UNIQUE (task_id, approval_request_id)`；`approval_task_records` 改为复合外键 `fk_approval_task_records_task`，去掉单列 `fk_approval_task_records_task_id`。**待核**：该外键目标是唯一约束而非主键，NAMING_STANDARD §2.4a-1 要求外键目标为主键，须核 `DdlNamingTest` 是否放行；不放行则由 one-coordinator 判是否登记例外，未判前不改。
- 预期表结构 diff：`approval_tasks`：新增上述唯一约束；`approval_task_records`：单列外键换复合外键。
- 验收要点：任务属请求甲、记录挂请求乙的行被拒；`record_number` 序号按请求连续。
- 依据：门禁 O4、B4。

### AP-05　SR-1　中　`start_rounds` 登记 `APPEND_ONLY` 却无只追加触发器
- 证据：PA `:42-55`（同 schema 的 `approval_task_records`、`process_archive_skips` 有触发器，`:172-186`、`:209-218`）；注册表 `data-access-registry.yaml:176-182`；`DataArchitectureTest.java:279` 只对 `LOG_APPEND_ONLY`、`DIMENSION_VERSIONED` 检查触发器，`AGGREGATE_CHILD + APPEND_ONLY` 漏检（OV 建议新增规则 3）。
- 改法：给 `start_rounds` 加禁止 UPDATE、DELETE、TRUNCATE 的触发器（命名与同 schema 一致）。守护扩展归 one-quality，不在本卡。
- 预期表结构 diff：`start_rounds`：新增触发器及其函数（或复用现有函数，待核）。
- 验收要点：`start_rounds` 的 UPDATE、DELETE、TRUNCATE 被拒；全仓对 `start_rounds` 无 UPDATE 语句（实施前 grep 核实，待核）。
- 依据：门禁 O7；D8。

### AP-06　OD-1　中（OV 确认）　`outbound_deliveries` 状态与字段成对约束缺失，其中一条让投递静默停摆
- 问题：`claimDue` 只认领 `status = 'PENDING' AND next_attempt_at <= now`，PENDING 且 `next_attempt_at` 为空永不投递。
- 证据：PA `:44-60`；`JdbcOutboundDeliveryRepository.java:100-107`；OV：`OutboundDelivery.java:70-90` 的 PENDING 均带 `now`，`scheduleRetry` 要求非空，建议的 CHECK 可落；`recipient_codes` 为空 ⇒ FAILED / SKIPPED 写成单向。
- 改法：新增 `ck_outbound_deliveries_*`：`status = 'PENDING' ⇒ next_attempt_at IS NOT NULL`（OV 已核）；`status = 'SENT' ⇒ sent_at IS NOT NULL`；`attempt_count >= 0`；`cardinality(recipient_codes) = 0 ⇒ status IN ('FAILED','SKIPPED')`（单向）；`FAILED`、`SKIPPED`、`DEAD_LETTER ⇒ last_error IS NOT NULL`：**待核**（`OutboundDelivery.java:79` 无收件人直接 FAILED，是否写 `last_error` OV 未核；SKIPPED 同），核实前不落。`event_count >= 0` 随 AP-07。SS §4 第 12 组另有两条，均待核后再落：`next_attempt_at IS NOT NULL ⇒ status = 'PENDING'`（待核 SENT 之后是否清空该列）、`handled_note` 与 `handled_result` 同空同非空（待核 `OutboundDelivery` 人工处理路径是否总是同时写）。
- 预期表结构 diff：`outbound_deliveries`：新增上述 CHECK。
- 验收要点：各状态转换写入通过；PENDING 无下次尝试时间被拒；无收件人投递仍可建为 FAILED。
- 依据：门禁 B3、B5；D2；OV「PA OD-1 确认」；SS §4 第 12 组（`sent_at:49`、`next_attempt_at:46`、`last_error:47`、`handled_note:50`、`handled_result:51`）。

### AP-07　OD-2　中　`event_count` 是死列（聚合从不发事件）
- 问题：注释「每发一个事件加 1」，`OutboundDelivery` 无 `record(`，事件目录无投递事件，更新语句从不写该列。
- 证据：PA `:58`、`:91`；`JdbcOutboundDeliveryRepository.java:100-145`；`integration.yaml`。OV 未单独抽查。
- 改法：默认删列并去掉仓储对它的读写（CS-1.8、CS-1.9）；若 one-architecture 判投递需事件则本条取消（待决 5）。`AggregateRoot` 对事件序号的依赖若须改 kernel，停下报告。
- 预期表结构 diff：`outbound_deliveries`：删除 `event_count`（及其 CHECK）。
- 验收要点：投递全流程通过；无残留引用。
- 依据：CS-1.8、CS-1.9；门禁 B6。

### AP-08　PS-2　中（OV 维持）　`process_archive_skips.failure_message` 存引擎原始异常文本，同文本进 ERROR 日志
- 问题：Flowable 删除失败多由 JDBC 异常包裹，消息带服务端错误文本，与 O-3 冲突。
- 证据：PA 迁移 `:193`；`ProcessArchiveJob.java:176-179`（OV 行号；PA 写 :176-181）；GUIDING_PRINCIPLES §11 O-3；OV「高风险项判断 PA 第 4 条」。
- 改法：日志与入库都只写异常类名与引擎实例 ID，不写 `getMessage()`；列加 `char_length` CHECK（上限待核，建议与审计文本一致取 2000）与 `<> ''`；列注释写明不含数据库错误详情。
- 预期表结构 diff：`process_archive_skips`：新增 `ck_process_archive_skips_failure_message`；注释改写。
- 验收要点：模拟删除失败，日志与表中均无异常原文；`ProcessArchiveJobIT` 对应断言单列理由。
- 依据：门禁 O8、C4；§2.4a-5；O-3。

- **数据设计（PR-6：AP-01..05、AP-08，one-1a-next-5 补，待 one-data 签）**（基线：`origin/main` 4d0b7c34；下文行号取该基线；「基线」＝`platform_approval/V20261014132000__platform_approval_baseline.sql`；Java 路径省略前缀 `backend/src/main/java/com/forcitis/one/platform/approval/`，测试路径省略前缀 `backend/src/test/java/com/forcitis/one/`；本节只做文档核实，未在库内执行 DDL，未跑测试）
  - **范围与顺序**：AP-06、AP-07 并入 DA-10，AP-09 起归 PR-7，均不在本 PR。同一 PR 分两个提交：提交 A（AP-01 的 Java 与测试，不含 DDL）先于提交 B（全部 DDL 与夹具），保证 CHECK 不早于删分支。无新增列、无新术语，只新增具名约束、触发器、外键；按 ADR-0038 直接改基线，已部署库重建。
  - **① AP-01　驳回、退回、撤销须有原因**
    - 核实（对卡中「待核」的结论）：
      1. `hookDecidesReason` 在 `application/DecideTasks.java:106-108`；`validateShape` 对所有 REJECT 决定一视同仁，不看 `view.advisory()`（:124 才分流），故删分支后加签人驳回自然须原因，无需另加分支。
      2. 域内不拦：`ApprovalRequest.completeTask`（`domain/ApprovalRequest.java:206-212`）、`ApprovalTask.complete`（`domain/ApprovalTask.java:124-134`）、`ApprovalRequest.end`（:286-296）都不校验原因；现靠应用层保证：`DecideTasks:107`、`ReturnToInitiator:41-43`、`Retraction:91` 与 `ApprovalRequest.requireWithdrawable:283`、`RefuseApprovalStart:42`（`new Reason`）。加 CHECK 后违规会变成库错误（500），故在域内补校验（见 Java）。
      3. 卡所问「撤销、撤回类 `action_kind` 是否存在」：不存在。`api/TaskRecordAction.java` 九个取值无撤销/撤回；`withdraw`/`cancel` 走 `end()`，只关任务、不写办理记录（:286-296 无 `note`）。办理记录 CHECK 因此只含 REJECTED、RETURNED、TRANSFERRED、REASSIGNED（后两者 `requireReason`：`ApprovalRequest.java:186,196`）。
      4. 加签人驳回写的记录 `action_kind` 是 ADVISED（:210），而 ADVISED 也可能是加签人通过（无原因），记录表不能按 ADVISED 约束；加签人驳回的原因由 `approval_tasks` 的 `outcome_kind='REJECTED'` 条件兜住（`complete` 对加签人也写 `outcome=REJECTED`，:128-130）。
      5. 状态机 `REJECTED/CANCELLED → RUNNING` 保留（:66-67）；`restart` 先置 `resultReason=null` 再发新轮（:134），库层更新同一语句写 `status='RUNNING', result_reason=NULL`（`infrastructure/persistence/JdbcApprovalRequestRepository.java:185`），CHECK 相容。
    - Java：`DecideTasks.validateShape` 删 :106 的 `hookDecidesReason` 与 :107 的 `&& !hookDecidesReason`（保留 `HookGate`、`TaskView.beforeHookKey`，钩子仍可拦截，只是不能替人免填）；`ApprovalRequest.completeTask` 在 `outcome != APPROVED` 时 `requireReason(reason, "A rejection or a return")`，`end()` 在目标为 REJECTED/RETURNED/CANCELLED 时 `requireReason`。均抛现有 `PLATFORM_APPROVAL_REASON_REQUIRED`，不新增错误码。
    - DDL（三列均加 `<> ''`：`Reason` 在 Java 侧已禁空白，库层补空串）：
      ```sql
      -- approval_requests（表级）
      CONSTRAINT ck_approval_requests_result_reason CHECK (
          (status IN ('REJECTED', 'RETURNED', 'CANCELLED')) = (result_reason IS NOT NULL)
          AND (result_reason IS NULL OR result_reason <> ''))
      -- approval_task_records（表级）
      CONSTRAINT ck_approval_task_records_record_reason CHECK (
          (action_kind NOT IN ('REJECTED', 'RETURNED', 'TRANSFERRED', 'REASSIGNED') OR record_reason IS NOT NULL)
          AND (record_reason IS NULL OR record_reason <> ''))
      -- approval_tasks（表级，单向，不带 effect_kind 条件）
      CONSTRAINT ck_approval_tasks_result_reason CHECK (
          (outcome_kind IS NULL OR outcome_kind NOT IN ('REJECTED', 'RETURNED') OR result_reason IS NOT NULL)
          AND (result_reason IS NULL OR result_reason <> ''))
      ```
      三列注释补「为空表示」：`approval_requests.result_reason` 改为「驳回、退回、撤销的原因；为空表示状态为运行中或已通过」，`approval_tasks.result_reason` 为「办理意见……；为空表示没填（通过时可不填，驳回、退回必填）」，`approval_task_records.record_reason` 同口径。
    - 测试：新增 `acceptance/platform/approval/ApprovalRowRulesIT`（`@DisplayName` 前缀 `DATA-REPAIR-approval AP-nn`）：先写「钩子节点无原因驳回应 422」，再删分支；直插 REJECTED/RETURNED/CANCELLED 无原因被拒、RUNNING/APPROVED 带原因被拒、空串被拒；加签人（`CountersignIT` 夹具）无原因驳回 422；`REJECTED → RUNNING`、`CANCELLED → RUNNING` 再发起通过（沿用 `IdempotentStartIT.whichEndedApprovalsMayStartAgain`）；`ApprovalTaskStateMachineTest`、`ApprovalRequestStateMachineTest` 各加域内「无原因被拒」一例（现有 `completeTask` 调用 `ApprovalTaskStateMachineTest:100,166,175,180,192,196,235` 均为 APPROVED 或带原因，不受影响）。
    - 改断言与夹具的逐处理由：
      - `platform/approval/BeforeHookIT.java:203-212` `rejectionReasonCanBeLeftToTheHook`：现断言「钩子节点无原因驳回成功、状态 REJECTED」，与用户 2026-10-16「删掉免填放行」直接冲突，**反转断言**（改名 `rejectionWithoutReasonIsRefusedEvenWhenTheNodeHasAHook`：抛 `PLATFORM_APPROVAL_REASON_REQUIRED`、`hook.calls` 为空、请求仍 RUNNING）；PR 与报告单列。
      - `platform/approval/IdempotentStartIT.java:192`（`update … set status = ?` 把请求置 RETURNED/REJECTED/CANCELLED）与 `platform/approval/AggregateConcurrencyIT.java:95`（置 CANCELLED）：新 CHECK 要求同时写原因，夹具 SQL 加 `result_reason = 'synthetic: ended'`；断言不动。
      - `IdempotentStartIT:200` 置 APPROVED 不带原因，合法，不改。
  - **② AP-02　`approval_tasks` 成对约束（两个坑已避开）**
    - 核实（逐条对 `domain/ApprovalTask.java` 全部写路径；状态机只有 PENDING/CLAIMED 有出边，COMPLETED/CLOSED 为终态，:22-25）：
      - PENDING ⇒ 无处理人无认领时刻：`assigned` 不赋值（:59-62）；`release` 清空（:96-101）；`escalate` 回 PENDING 清空（:148-159）；`reassign` 经 PENDING 立即转 CLAIMED（:112-122）。成立。CLOSED 不清（`close` 只 `move`，:136-138），故不对 CLOSED 写（坑 1）。
      - 结果三件套：`complete` 同时写 `outcome`、`effect`（:128-129）；`passedBySystem` 同时写（:69-70）。同空同非空成立。
      - AUTOMATIC ⇒ APPROVED：仅 `passedBySystem` 产生，固定 APPROVED（:69-70）。成立；AUTOMATIC 无 `claimed_at`、无 hash，故不写「COMPLETED ⇒ claimed_at」（坑 2）。
      - `approved_hash` 仅在 `APPROVED && !advisory` 时写（:131-133），即 effect=BINDING。成立（单向）。
      - `overdue_at` ⇒ `due_at`：`markOverdue` 要求 `deadline != null`（:141），`due_at` 随 `deadline` 写（`JdbcApprovalRequestRepository.java:212`）；`escalated_at` ⇒ `overdue_at`：`escalate` 要求 `overdueAt != null`（:149）。成立。
      - 「非 COMPLETED ⇒ 办结字段全空」（卡上原标待核）：已核实可落——`completedAt`、`outcome`、`effect`、`resultReason`、`approvedHash` 只在 `complete`/`passedBySystem` 写，`close()` 不写；`complete` 先 `move(COMPLETED)` 后赋值（:126-130）。落。
    - DDL（表级，名称 `ck_approval_tasks_<规则>`）：
      ```sql
      CONSTRAINT ck_approval_tasks_pending_unassigned CHECK (status <> 'PENDING' OR (assignee_id IS NULL AND claimed_at IS NULL)),
      CONSTRAINT ck_approval_tasks_outcome_effect CHECK ((outcome_kind IS NULL) = (effect_kind IS NULL)),
      CONSTRAINT ck_approval_tasks_automatic CHECK (effect_kind IS DISTINCT FROM 'AUTOMATIC' OR outcome_kind = 'APPROVED'),
      CONSTRAINT ck_approval_tasks_approved_hash CHECK (approved_hash IS NULL OR (outcome_kind = 'APPROVED' AND effect_kind = 'BINDING')),
      CONSTRAINT ck_approval_tasks_overdue CHECK (overdue_at IS NULL OR due_at IS NOT NULL),
      CONSTRAINT ck_approval_tasks_escalated CHECK (escalated_at IS NULL OR overdue_at IS NOT NULL),
      CONSTRAINT ck_approval_tasks_not_completed CHECK (status = 'COMPLETED' OR (completed_at IS NULL AND outcome_kind IS NULL
          AND effect_kind IS NULL AND result_reason IS NULL AND approved_hash IS NULL))
      ```
    - Java：无。测试：`acceptance/platform/approval/ApprovalRowRulesIT` 每条一例违反行被拒；正例走既有路径回归（认领、释放、升级、关闭、自动通过 `RepeatApproverIT`、加签 `CountersignIT`）。
    - 夹具：`platform/approval/ApprovalDeadlineIT.java:98` 置 CLOSED（保留处理人，合法）、`:359` 置 PENDING 且清 `completed_at`（该任务在 `scene(true)` 里未认领，`assignee_id`、`claimed_at` 本为空，合法）；均不改。
  - **③ AP-03　三处 `instance_id` 外键、`current_instance_id` 非空、`current_round >= 1`**
    - 核实：`start_rounds.instance_id` 是 `uuid`（基线 :47），且有 `uk_start_rounds_instance_id UNIQUE (instance_id)`（:53）→ 属「仅由 uuid 列组成的唯一约束」，按 one-architecture 判定直接作外键目标，不走 AP-17 的 CHECK 路线。三个引用列均为 `uuid`（:11、:69、:134）。
    - 写入次序（决定哪条外键须延迟）：`save`（`JdbcApprovalRequestRepository.java:100-131`）先写或更新请求（含 `current_instance_id`）→ 再插 `start_rounds`（:107-116）→ 再写任务 → 再写办理记录。故 `approval_requests` 一侧须 `DEFERRABLE INITIALLY DEFERRED`，任务与办理记录两侧立即检查即可。`start_rounds` 无更新删除（AP-05 后由触发器保证），外键共享锁不与 `lockForStart`（:65 咨询锁）冲突。同类先例：workforce 基线 :463-468。
    - `current_round`：`ApprovalRequest.begin` 固定从 1 起（`domain/ApprovalRequest.java:112`），`restart` 递增（:130），无写 0 的路径。`current_instance_id`：构造即 `requireNonNull`（:99），`readRow`（`JdbcApprovalRequestRepository.java:270`）经 `ApprovalInstanceId` 的 `Uuids.requireKey` 拒空，无须改 Java。
    - DDL：
      ```sql
      -- approval_requests 建表处
      current_round       integer     NOT NULL,
      current_instance_id uuid        NOT NULL,
      CONSTRAINT ck_approval_requests_current_round CHECK (current_round >= 1),
      CREATE UNIQUE INDEX uk_approval_requests_current_instance_id ON platform_approval.approval_requests (current_instance_id);  -- 去掉 WHERE
      -- start_rounds 建表之后追加
      ALTER TABLE platform_approval.approval_requests ADD CONSTRAINT fk_approval_requests_current_instance_id
          FOREIGN KEY (current_instance_id) REFERENCES platform_approval.start_rounds (instance_id) DEFERRABLE INITIALLY DEFERRED;
      -- approval_tasks、approval_task_records 表内
      CONSTRAINT fk_approval_tasks_instance_id FOREIGN KEY (instance_id) REFERENCES platform_approval.start_rounds (instance_id),
      CONSTRAINT fk_approval_task_records_instance_id FOREIGN KEY (instance_id) REFERENCES platform_approval.start_rounds (instance_id),
      ```
      注释：`current_round` 去掉「0 表示刚建立、尚未发起」；`current_instance_id` 加「不为空」。
    - 测试：`acceptance/platform/approval/ApprovalRowRulesIT`：直插指向不存在实例的任务、办理记录被拒（23503）；`current_round = 0` 被拒；`current_instance_id` 空被拒（23502）；请求与首轮同事务提交通过，请求无首轮在提交时被拒；对账 IT：每个请求的 `current_round`、`current_instance_id` 等于其 `start_rounds` 最大轮次那一行（DATA_DESIGN_CHECKLIST §14.1 D1「投影可重算并有对账」）。发起、重新发起（`IdempotentStartIT`、`RetractionIT`）、转交（`TaskTransferIT`）、系统退回（`RefuseApprovalStart`）全路径回归。
    - 夹具（直插请求者，必须改，否则被 NOT NULL 与延迟外键拒；断言均不动）：
      - `platform/approval/IdempotentStartIT.java:139-142`（`roundsAreUniqueInTheDatabase`）：请求没有 `current_instance_id`，且分两条自动提交语句，第一条提交时延迟外键即失败。改成**单条语句**：`WITH r AS (INSERT INTO approval_requests (…, current_instance_id) VALUES (…, :instance) RETURNING id) INSERT INTO start_rounds (…, instance_id, …) SELECT …, :instance, … FROM r`，首轮 `instance_id` 取同一值；其后两条期望 `DuplicateKeyException` 的插入（:144-148）不改。
      - `IdempotentStartIT.java:156-159`（`requestsAreUniqueInTheDatabase`）：第一条同上改单条语句带首轮；第二条期望 `DuplicateKeyException` 的插入补一个 `current_instance_id`（任意新 uuid；否则先撞 NOT NULL，异常类型变成 `DataIntegrityViolationException`）。
      - `platform/approval/AggregateConcurrencyIT.java:133-135`、`:141-143`：两条反例（缺法人、缺请求）仍被拒，不改。
    - 验收目录受影响（`acceptance/`，卡 §9 不改，须 Acceptance-Ruling，见裁 1）：`acceptance/platform/approval/ApprovalUniqueKeysIT.java:76-86` `requestsWithoutACurrentInstanceMayBeMany`（把 `current_instance_id` 置空，前提消失）、`acceptance/platform/approval/ApprovalOwnIdColumnsIT.java:179-180` 对 `WHERE (current_instance_id IS NOT NULL)` 的断言。`ApprovalUniqueKeysIT:52-73`（唯一冲突先于延迟外键）与 `ApprovalOwnIdColumnsIT:144-165`（子表外键计数，新增外键方向不是指向 `approval_requests`）经核不受影响。
    - 未核实，不落：卡中「注册表登记 `current_round`、`current_instance_id` 为投影」——`data-access-registry.yaml` 与 `DataArchitectureTest` 没有投影登记的键（后者只读 role、mutability、condition、retention、lineage、references、fk_exceptions 等），登记形式须先定（裁 2）；本 PR 用对账 IT 代替。
  - **④ AP-04　办理记录的任务与请求须同属一个请求**
    - 核实：复合外键目标 `(task_id, approval_request_id)` 两列都是 `uuid`，属「仅由 uuid 列组成的唯一约束」，按 one-architecture 判定放行，不登例外；任务入库时 `approval_request_id` 写所属请求 ID（`JdbcApprovalRequestRepository.java:202-209`），办理记录用同一请求 ID（:148-153），任务先于记录入库（:117-130）。保留 `fk_approval_task_records_approval_request_id`（`DataArchitectureTest.d6` 要求 `approval_request_id` 列有指向 `approval_requests` 的外键；`ApprovalOwnIdColumnsIT:150-158` 也数它）。
    - DDL：
      ```sql
      -- approval_tasks
      CONSTRAINT uk_approval_tasks_task_id_approval_request_id UNIQUE (task_id, approval_request_id),
      -- approval_task_records：单列 fk_approval_task_records_task_id 改为复合外键，名称不变
      CONSTRAINT fk_approval_task_records_task_id FOREIGN KEY (task_id, approval_request_id)
          REFERENCES platform_approval.approval_tasks (task_id, approval_request_id),
      ```
    - 测试：`acceptance/platform/approval/ApprovalRowRulesIT` 反例：任务属请求甲、记录挂请求乙被拒（23503，约束名 `fk_approval_task_records_task_id`）；既有 `TaskHandlingRecordsIT`、`ApprovalOrderNotFromIdsIT.numbersRestartPerRequestAndNeverRepeat`（:94-111 复制既有行，任务与请求一致；期望 23505 的断言先于外键触发）回归，不改。无夹具改动；全仓测试无引用 `fk_approval_task_records_task_id`（`git grep` 无命中）。
    - 索引：复合外键首列 `task_id` 已有 `ix_approval_task_records_task_id`（:170）。新唯一约束的索引以 `task_id` 开头，与主键索引前缀相同，G-DDL3（待实现）实现时须豁免「作外键目标的唯一约束」（裁 4）。
  - **⑤ AP-05　`start_rounds` 加只追加触发器**
    - 核实：全仓对 `start_rounds` 只有一处 INSERT（`JdbcApprovalRequestRepository.java:109`）和两处 SELECT（:251、`infrastructure/persistence/JdbcApprovalTaskQueries.java:43`）；测试只插入与查询，无 UPDATE、DELETE、TRUNCATE（`git grep` 核）；无测试对 `platform_approval` 清库式截断。`unversioned-table-writes.txt:13` 已写「insert-only」，注册表 `APPEND_ONLY`（`data-access-registry.yaml:176-182`）从此名实相符。
    - DDL（照 `approval_task_records` 的函数与触发器形态，基线 :172-186；错误码 `restrict_violation`，`DataArchitectureTest.truncateIsRefused` 认 23001）：
      ```sql
      CREATE FUNCTION platform_approval.reject_start_round_change() RETURNS trigger LANGUAGE plpgsql AS $$
      BEGIN
          RAISE EXCEPTION 'platform_approval.start_rounds is append-only: % is not allowed', TG_OP USING ERRCODE = 'restrict_violation';
      END;
      $$;
      CREATE TRIGGER start_rounds_reject_update_delete BEFORE UPDATE OR DELETE ON platform_approval.start_rounds
          FOR EACH ROW EXECUTE FUNCTION platform_approval.reject_start_round_change();
      CREATE TRIGGER start_rounds_reject_truncate BEFORE TRUNCATE ON platform_approval.start_rounds
          FOR EACH STATEMENT EXECUTE FUNCTION platform_approval.reject_start_round_change();
      ```
      表注释保留「发起轮次」，补「只追加」。守护扩展（`DataArchitectureTest.d1` 对 `AGGREGATE_CHILD + APPEND_ONLY` 也查触发器，现 :278 只查日志与版本表）归 one-quality，不在本 PR。
    - 测试：`acceptance/platform/approval/ApprovalRowRulesIT`：UPDATE、DELETE、TRUNCATE 被拒（参照 `TaskHandlingRecordsIT.java:91-96`），INSERT 照常。无夹具改动。
  - **⑥ AP-08　`process_archive_skips.failure_message` 不存引擎异常原文**
    - 核实：日志在 `infrastructure/flowable/ProcessArchiveJob.java:177`，入库在 :179 → `insertSkipped`（:185-190）；该列只被这一处写。`ProcessArchiveJobIT`（`platform/approval/infrastructure/flowable/`）对 `failure_message` 与日志**没有任何现有断言**（T3 用例 :174-193 只断言跳过计数、记录存在、不重复归档），故没有断言需要放宽或删除；卡里「`ProcessArchiveJobIT` 对应断言单列理由」按实际改为「新增断言，不改既有断言」。
    - Java：新增私有方法 `failureKind(Throwable)`：从外到内最多 5 层，取 `getClass().getName()` 以 `" <- "` 连接，绝不含 `getMessage()`，Java 侧截到 2000 字（与 CHECK 同值常量，避免 `insertSkipped` 因超限抛错而中断整批归档——此时实例已归档，中断会重复归档）；:177 日志与 :179 入库都用它，日志不再传异常对象、不带 `getMessage()`（SLF4J 的异常参数会打印消息与栈）。:113 的探测失败日志带 `failure.getMessage()`，属对象存储探测，非数据库错误，不在本条（见「不在本 PR」）。
    - DDL：`CONSTRAINT ck_process_archive_skips_failure_message CHECK (char_length(failure_message) BETWEEN 1 AND 2000)`；列注释改为「引擎删除失败的异常类名链（由外到内，至多 5 层）；不含异常消息，消息可能带数据库服务端错误详情（GUIDING_PRINCIPLES §11 O-3）」。列名不改（改名牵动验收测试 `ApprovalUniqueKeysIT:92`，见裁 5）。
    - 测试（新增断言，加在 `ProcessArchiveJobIT` 的 T3 用例旁，不改既有断言）：`doThrow(new IllegalStateException("secret-detail"))` 后，用 Logback `ListAppender`（参照 `acceptance/platform/web/ErrorLogDetailIT.java`）捕获 `ProcessArchiveJob` 日志；断言日志与 `failure_message` 都**不含** `secret-detail`、**含** `IllegalStateException`；另一例直插空串、超长被拒。`ApprovalUniqueKeysIT:92-100` 直插 `'synthetic failure'` 满足新 CHECK，不改。
  - **预期表结构 diff（两个空库 `pg_dump --schema-only` 对比，只应出现）**：
    - `approval_requests`：`current_round` 的无名 CHECK（`>= 0`）换为具名 `ck_approval_requests_current_round (>= 1)`；`current_instance_id` 变 NOT NULL；`uk_approval_requests_current_instance_id` 去掉 `WHERE`；新增 `ck_approval_requests_result_reason`、`fk_approval_requests_current_instance_id`（`DEFERRABLE INITIALLY DEFERRED`）；`current_round`、`current_instance_id`、`result_reason` 三条列注释。
    - `start_rounds`：新增函数 `reject_start_round_change()` 与触发器 `start_rounds_reject_update_delete`、`start_rounds_reject_truncate`；表注释。
    - `approval_tasks`：新增 `ck_approval_tasks_result_reason` 与 `pending_unassigned`、`outcome_effect`、`automatic`、`approved_hash`、`overdue`、`escalated`、`not_completed` 共 8 条 CHECK、`uk_approval_tasks_task_id_approval_request_id`、`fk_approval_tasks_instance_id`；`result_reason` 列注释。
    - `approval_task_records`：新增 `ck_approval_task_records_record_reason`、`fk_approval_task_records_instance_id`；单列 `fk_approval_task_records_task_id` 改为复合外键（名称不变）；`record_reason` 列注释；（裁 3 通过时）新增 `ix_approval_task_records_instance_id`。
    - `process_archive_skips`：新增 `ck_process_archive_skips_failure_message`；`failure_message` 列注释。
    - 不得出现：列类型或列名变化、主键变化、种子数据变化、`act_*` 变化。
  - **验收要点**：`DdlNamingTest`、`DataArchitectureTest`、`GlossaryTest` 全绿；`scripts/ci/baseline_ddl_scan.py` 无新增；新增约束全部具名；`scripts/precheck.sh` 输出贴 PR；报告分开写「已验证」「未运行」。
  - **不在本 PR**：AP-06、AP-07（并入 DA-10）；AP-09 至 AP-18（PR-7，含 AP-13 注册表、AP-16 的 `on_behalf_of_id`/`assignee_id` 成对）。本 PR 顺带不做：`infrastructure/persistence/JdbcApprovalTaskQueries.java:43` 的 `COALESCE((SELECT … start_rounds …), 0)` 在外键落地后成为死路径，留待下次动该查询时简化；`ProcessArchiveJob.java:113` 的探测日志；`current_round` 改名 `current_round_number`（R-2a，`ddl-naming-baseline.txt:9`）；全库文本列长度（X-1，one-quality）。
  - **请 one-data 裁定**：
    1. **AP-03 与验收目录**：`current_instance_id` 改 NOT NULL 后，`acceptance/…/ApprovalUniqueKeysIT.java:76-86`（空值可多条）前提消失、`ApprovalOwnIdColumnsIT.java:179-180` 的部分索引谓词断言失效。方案 A（推荐，与卡一致）：普通唯一索引，由 Acceptance-Ruling 授权测试代理把前者改为「空值被 23502 拒绝」、后者去掉谓词断言（先例 RULING-DR-TB01-1）；方案 B：保留部分索引不动（对非空列谓词恒真，无验收改动，但留下冗余谓词）。
    2. **AP-03 投影登记**：注册表无投影键，是否新增 `projections`（列、重算来源、对账测试名）并扩展守护；本 PR 先落对账 IT，登记待你定形式。
    3. **AP-03 外键索引**：`approval_task_records.instance_id` 新外键无以该列开头的索引（G-DDL2 待实现将要求；父表只追加，无性能必要）。默认随本 PR 加 `ix_approval_task_records_instance_id`，请确认或判缓。
    4. **AP-04 命名与 G-DDL3**：复合外键名取卡上的 `fk_approval_task_records_task`，或沿用旧名 `fk_approval_task_records_task_id`（R-1b P4 卡写该名不变，见「不一致」d），请定；`uk_approval_tasks_task_id_approval_request_id` 的索引与主键前缀相同，G-DDL3 实现时须豁免「作外键目标的唯一约束」。
    5. **AP-08 列语义**：`failure_message` 改存类名链后名实不符，是否改列名（如 `failure_kind`；须先查词表并同改验收测试 `ApprovalUniqueKeysIT:92`）；上限取 2000（与卡一致，类名链实际远小于此）。
    6. **范围**：卡 §3「允许改动」的 Java 只列 `DecideTasks.java`、`ProcessArchiveJob.java`，AP-01 的域内补校验要改 `domain/ApprovalRequest.java`（`completeTask`、`end`）；请确认纳入本 PR（不纳入则无原因的驳回会在库层才失败，得到 500 而非 422）。
  - **与卡或既有文字的不一致（列出，不自行解释）**：(a) 卡 AP-08 称 `ProcessArchiveJobIT` 有对应断言需单列理由，实际无此断言（⑥）。(b) 卡 AP-01 步骤 3 写撤销、撤回类 `action_kind` 待核，实际取值全集无此类（①-3）。(c) `BUSINESS_RULES.md` RULE-272 正文（:4473「A rejection needs a reason unless the node delegates that check to a business pre-check hook」）仍写钩子可免填，与用户 2026-10-16 决定及 RULE-280 修订冲突；`process/1a/审批通用流程.md` T10 行（:169）「原因规则」同源；文档同步不在本 PR。(d) R-1b P4 卡（`phase1a/cards/R-1b-P4.md:34`）写 `fk_approval_task_records_task_id` 名称不变，AP-04 换成复合外键后名称不同。(e) 卡 AP-03 与 AP-17 写「外键目标为唯一约束，待核是否放行」，NAMING_STANDARD G-DDL1（:365）已写明放行「仅由 uuid 列组成的唯一约束」，该「待核」已结。
  - 按 one-data 裁定①–⑥修订（2026-10-16）。

### AP-09　NV-2　高（OV 升级，原中；统一分级维持高）　`numbering_rule_versions` 的 CONFIRMED 不要求确认人
- 问题：迁移只约束「作者不得确认自己的版本」「两个确认人不同」「确认人与确认时间成对」；触发器 `guard_rule_version_change` 只管状态流转，INSERT 不受控；与 authorization_requests 同类的双人确认防线缺口。
- 证据：PA `:94-120`、`:104,:114-119`、`:141-163`；`JdbcNumberingRuleRepository.update`（`:148-166`）；`NumberingRule.completeIfAllGiven`（`NumberingRule.java:187-193`）；ADR-0027 第 2 项第 4 条；OV「PA NV-2 确认，建议升为高」：所有现有写入点不会撞上（种子与 `propose` 都是 PENDING）。
- 改法：新增 `ck_numbering_rule_versions_confirmed`：`status <> 'CONFIRMED' OR confirmed_by IS NOT NULL`（确认人与时间已成对，故时间随之）；凭证号另需财务确认的条件依赖单据类型，库里落不成 CHECK，注册表写明由聚合保证。**先 grep `backend/src/test`**：无确认人的 CONFIRMED 夹具（OV 标「未核」）。是否追加「待确认 ⇒ 确认列全空」：待核状态取值全集。
- 预期表结构 diff：`numbering_rule_versions`：新增 `ck_numbering_rule_versions_confirmed`。
- 验收要点：直接插入无确认人的 `CONFIRMED` 被拒；正常确认流程通过。
- 依据：门禁 B3、B5；OV 建议新增规则 1；SS §4 第 13 组（`status:104`、`confirmed_by:105`、`confirmed_at:106`；`:118` 已有两列成对）。

### AP-10　NV-1　中　配置型版本表缺 `decision_ref`
- 证据：PA `:94-120`；METHODS.md:286、:409（配置类版本表须含批准人、决定出处）。
- 改法：加 `decision_ref text`（含义：用户决定的 intent 或 ADR 编号）与长度、非空串 CHECK；种子行写 ADR-0010 第 5 项。是否「CONFIRMED ⇒ `decision_ref` 非空」与填什么由用户决定（待决 2），默认只落格式 CHECK，可空。
- 预期表结构 diff：`numbering_rule_versions`：新增列 `decision_ref` 与 `ck_numbering_rule_versions_decision_ref`。
- 验收要点：种子行带出处；空串被拒。
- 依据：门禁 O6；D15、D17 细则 2。

### AP-11　NR-1（含 NR-2、NR-3）　中　`numbering_rules` 四个停用列仍在基线，种子还在写
- 证据：PA `:4,:8-10,:15,:171-182`（`pattern`、`reset_policy`、`start_value`、`confirmation_status`，种子 INSERT 写入后再 SELECT 搬到版本表）；`JdbcNumberingRuleRepository` 更新只写 `updated_at`、`version`、`event_count`（`:114-122`）；NR-2：`is_active` 仅被读（`:47,:55`，`where is_active`），无写入；NR-3：种子 `event_count = 1` 无对应事件。OV 未单独抽查。
- 改法：删四列及其 CHECK、默认值，种子直接写版本表；NR-2：删 `is_active` 与两处谓词；NR-3：种子 `event_count = 0`。**待核**其他模块有无 SQL 引用这些列（PA 做过跨模块 SQL 引用脚本核对，但未单列结论）。
- 预期表结构 diff：`numbering_rules`：删列 `pattern`、`reset_policy`、`start_value`、`confirmation_status`、`is_active` 及相关约束；种子 `event_count` 值变化。
- 验收要点：取号、版本确认、`inForceOn` 全部通过；种子对比差异只限上述。
- 依据：CS-1.8、CS-1.9；门禁 O12（基线开放直接改基线）；§2.4a-4。

### AP-12　LN-1　中　`legacy_numbers` 前缀检索无可用索引
- 证据：PA `JdbcLegacyNumberRepository.java:47-57`（`starts_with(legacy_number, ?) AND legacy_number > ? ORDER BY legacy_number LIMIT ?`）；`uk_legacy_numbers_…`（`:68`）`starts_with` 不是索引条件；老工时单 12 万张（OBJ-66）；数据库排序规则未核。OV 未单独抽查。
- 改法：查询改范围条件 `legacy_number >= prefix AND legacy_number < prefix_upper`，或建 `text_pattern_ops` 索引；**先 EXPLAIN**，证据放 PR。
- 预期表结构 diff：`legacy_numbers`：可能新增一个索引（按 EXPLAIN 结论）。
- 验收要点：EXPLAIN 显示前缀检索走索引；分页游标不变。
- 依据：门禁 O10；D19。

### AP-13　OD-3 + SRF-1　中　注册表登记不符
- 改法（只改注册表，无 DDL）：
  - OD-3：`outbound_deliveries` 的 `retention` 如实写两档（SENT、SKIPPED 90 天，FAILED、DEAD_LETTER 1 年）并指向 `OutboundDeliveryPurgeJob`；「人工处理过的」含义待用户（待决 3）
  - SRF-1：`source_references` 的 `lineage` 改 `{source: ORG_SOURCE, migrated_from: [老系统人员与部门对照], via: ONE_OFF_IMPORT}`（核算组织编码是人工登记，另写）
- 证据：PA GUIDING_PRINCIPLES.md:114、注册表 :325-331、`OutboundDeliveryPurgeJob.java`；注册表 :332-338、ADR-0033 第 2 项第 7、11 条、ADR-0036、`SourceReference.java:73`。
- 预期表结构 diff：无。
- 验收要点：注册表与作业实际口径一致，守护通过。
- 依据：门禁 C3、C5。

### AP-14　B7（SS §7 第 1、2 条、补审 B7-1）　中　`document_type` 的 CHECK 含本阶段未建模块的取值
- 问题：`approval_requests.document_type` 的 CHECK 含 `QUOTE`（`presales.quoting`，TA-1b 未建）与 `JOURNAL_VOUCHER`（`accounting.ledger`，TA-4 未建），只 `TIMESHEET`（`cost.timesheet`）已建；`numbering_rules` 表级 `ck_numbering_rules_document_type` 与种子同含这两个取值；现状审批只有 `ProcessKey.COST_TIMESHEET_APPROVAL` 一个流程。
- 证据：SS §7：`apv.approval_requests.document_type:17`、`num.numbering_rules:18`、种子 `numbering_rules` :173、:175、`numbering_rule_versions` 种子 :181（QUOTE、JOURNAL_VOUCHER 各一行，待确认）；`transitions.yaml`（TA-1b、TA-4 未开始）、`module-dependencies.txt` 无这两个模块；P5 分支基线 `platform_approval` :17、`platform_numbering` :18、:173、:175、:181 同样存在，未被覆盖。
- 改法（CS-1.8：未建模块的取值待模块上线时再追加）：
  1. 两处 CHECK 收窄为只含 `TIMESHEET`；种子的 `QUOTE`、`JOURNAL_VOUCHER` 两行（`numbering_rules`、`numbering_rule_versions`）删去；待建模块上线时由该模块的 PR 同时追加 CHECK 取值与种子。
  2. 补审 B7-1：`numbering_rule_versions.finance_confirmed_by` / `finance_confirmed_at`（SS :107-108）、CHECK 的财务确认部分（:114-118）、列注释（:136）只服务 `JOURNAL_VOUCHER`（`NumberingRule.NEEDING_FINANCE_CONFIRMATION`）：与上同判，随 `JOURNAL_VOUCHER` 一并删去，或写明保留理由。
  3. **待核（先于动手）**：基线注释 :170 引 ADR-0010 第 5 项「三种单据都从默认模板起步」，是否视为已决定「现在就建」由 one-architecture 判（SS §7 第 2 条、补审 B7-1）；未判前不删 `numbering_*` 的种子与财务确认列，只落第 1 步对 `approval_requests` 的收窄。同时 grep `backend/src/test`、`NumberingRule.java`、单据类型枚举对这两个取值的引用（待核）。
- 预期表结构 diff：`approval_requests`：`document_type` 的 CHECK 收窄；`numbering_rules`：表级 CHECK 收窄、种子少 1 行；`numbering_rule_versions`：种子少 1 行、（若判删）删财务确认两列及其 CHECK。
- 验收要点：取号、版本确认全路径只用 `TIMESHEET` 通过；插入 `QUOTE`、`JOURNAL_VOUCHER` 被拒；种子差异只限上述。
- 依据：CS-1.8；门禁 B7；SS §7、补审 B7-1。

### AP-15　C4（SS §1、DATA-017）　中　`approval_requests.summary_text` 无长度上限、无分级登记
- 问题：`summary_text`（NOT NULL）由拥有单据的业务模块在发起时给出、卡片直接显示，库内无任何 CHECK，`data-classification.txt` 无该列（默认 INTERNAL + NONE），内容是否含个人信息取决于写入点。
- 证据：SS §1：`summary_text:20`（全库 `char_length` 出现次数为 0）、「已知必须出现项已找到」；错题本 DATA-017（C4 漏判来源）；注释写「业务摘要：发起时由拥有单据的业务模块给出」（P5 分支 :40）。写入点（哪个类拼摘要、拼了哪些字段）：待核。
- 改法：
  1. `ck_approval_requests_summary_text`：`char_length(summary_text) BETWEEN 1 AND N`，N 待核（取现有 `TIMESHEET` 摘要最大长度加余量，须有测试证据）；超限失败并告警，不静默截断。
  2. `data-classification.txt` 登记 `approval_requests.summary_text`：先登记 `INTERNAL`，是否加 PI 标记由写入点内容定（待核，待用户确认）；文件仍是三段格式，第二维写在本卡表内，四段化随 ADR-0043 实施 PR。
  3. C4「允许写入内容清单」草稿：字段名、来源模块、是否可能含个人信息（员工姓名、客户名等）；交用户确认，本卡不替用户判。
- 预期表结构 diff：`approval_requests`：新增 `ck_approval_requests_summary_text`。
- 验收要点：超长摘要被拒且有告警；`TIMESHEET` 发起通过；分级守护通过。
- 依据：门禁 C4、O8；ADR-0043 第 3 项；NAMING_STANDARD §2.4a-5。

### AP-16　SS §4 第 7 组　中（待核后落）　`approval_task_records` 的 `on_behalf_of_id`、`assignee_id` 与 `action_kind` 无成对约束
- 证据：SS §4 第 7 组：`on_behalf_of_id:139` / `assignee_id:140` ⇔ `action_kind:137`（TRANSFERRED、REASSIGNED、代办）。各 `action_kind` 对应哪几列必有值：待核（须读 `ApprovalRequest` 转交、改派、代办路径与记录工厂，OV 与 PA 均未核）。
- 改法：核实后每个 `action_kind` 一条单向 CHECK（`action_kind IN (…) ⇒ 列 IS NOT NULL`），反向（其余动作该列为空）只在核实全部路径后才写；核实前不落。`record_reason` 部分已在 AP-01 第 3 步。
- 预期表结构 diff：`approval_task_records`：新增 `ck_approval_task_records_*`（条数待核）。
- 验收要点：转交、改派、代办、驳回、退回、撤回各路径通过；缺列行被拒。
- 依据：门禁 B5、O4；D2；SS §4 第 7 组。

### AP-17　NC-1　中　`numbering_counters` 取值与格式约束不全
- 证据：PA NC-1（迁移 `:34-45`）：`document_type` 无 CHECK 也无指向 `numbering_rules(document_type)` 的外键；`period_key` 注释写了 `- / 2026 / 202610 / 20261014` 却无格式 CHECK；`last_value` 无 `>= 1`；SS §1 `numbering_counters`：`document_type:35`、`period_key:38` 无格式约束。
- 改法：`period_key ~ '^(-|[0-9]{4}|[0-9]{6}|[0-9]{8})$'`；`last_value >= 1`（待核计数初值写法，PA 称 `start_value` 恒 ≥ 1）；`document_type` 二选一：外键指向 `uk_numbering_rules_document_type`，或与 AP-14 收窄后的取值同一 CHECK。外键目标是唯一约束而非主键，与 AP-04 同问题：先核 `DdlNamingTest` 是否放行（NAMING_STANDARD §2.4a-1），不放行则用 CHECK。
- 预期表结构 diff：`numbering_counters`：新增 `ck_numbering_counters_period_key`、`ck_numbering_counters_last_value`，及 `document_type` 的外键或 CHECK。
- 验收要点：按月、按年、按日、无周期四种取号通过；非法 `period_key`、`last_value = 0` 被拒。
- 依据：门禁 O4、O8；NAMING_STANDARD §2.4a-5。

- **数据设计（PR-7：AP-09、11–17，one-1a-next-5 补，待 one-data 签）**（基线：`origin/main` 4d0b7c34；下文行号取该基线；「编号基线」＝`platform_numbering/V20261014100100__platform_numbering_baseline.sql`，「审批基线」＝`platform_approval/V20261014132000__platform_approval_baseline.sql`；Java 路径省略前缀 `backend/src/main/java/com/forcitis/one/platform/numbering/`，测试路径省略前缀 `backend/src/test/java/com/forcitis/one/`；本节只做文档核实，未在库内执行 DDL，未跑测试，未跑 EXPLAIN）
  - **范围与顺序**：不含 AP-10（`decision_ref`，待用户）、AP-12（`legacy_numbers` 索引：不在本 PR，待用户定 `legacy_numbers` 去留）、AP-18（随 `legacy_numbers` 去留）、AP-06/07（并入 DA-10）。按 ADR-0038 直接改基线、已部署库重建，不追加迁移。#333 已合入：`TemplateVersion` 带 `id`（`domain/TemplateVersion.java`）、`IssuedNumber` 带版本 ID（`application/IssueDocumentNumber.java:54`），编号基线表结构不因它变化，本 PR 以此为底。**AP-14 依 one-architecture 2026-10-16 更正（ADR-0030 U-3「dev 保留凭证号财务确认」、D-1a-157）**：numbering 侧（种子含 QUOTE、JOURNAL_VOUCHER、财务确认列、触发器、枚举、接口）一律不动，AP-14 只收窄 `approval_requests.document_type`。与 PR-6 都改审批基线 `approval_requests` 的相邻行（PR-6 是 `result_reason`，本 PR 是 `document_type`、`summary_text`），合并顺序不限，后合者变基；`ApprovalRowConstraintsIT` 两个 PR 共用一个类，后合者并入。
  - **建议提交顺序（同一 PR）**：提交 A（Java：删 `is_active` 谓词）先于提交 B（全部 DDL 与种子）先于提交 C（测试夹具与断言）；基线一改即全库重建，须同一次构建内全绿，分提交只为评审。本 PR 不改契约（无接口、事件、枚举变化）。

  - **① AP-09　CONFIRMED 必有确认人**
    - 核实：`status` 取值全集 `PENDING_CONFIRMATION / CONFIRMED / SUPERSEDED`（编号基线:104）；全仓只有 `domain/NumberingRule.java:197`（`completeIfAllGiven`）经 `withStatus(..., CONFIRMED)` 置 CONFIRMED，且前置 `target.confirmedBy() != null`（:196）；`infrastructure/persistence/JdbcNumberingRuleRepository.java:139-150` 插入只写 PENDING（`propose` :128 固定 PENDING、确认列全 null）；:152-170 更新同写 `confirmed_by`。种子（编号基线:178-182）为 PENDING。故所有现有写入点不撞新 CHECK。
    - 反向「非 CONFIRMED ⇒ 确认列空」：现状不成立——凭证号版本业务已确认而财务未确认时是 PENDING 且 `confirmed_by` 非空（`NumberingRule.java:141-147`、:195-200）、再被新版本取代则是 SUPERSEDED 且 `confirmed_by` 非空（:125）。财务确认保留（ADR-0030 U-3），故**只写单向**，不加「非 CONFIRMED ⇒ 确认列空」。
    - DDL（具名，表级）：
      ```sql
      CONSTRAINT ck_numbering_rule_versions_confirmed CHECK (status <> 'CONFIRMED' OR confirmed_by IS NOT NULL)
      ```
      `confirmed_at` 已由 `ck_numbering_rule_versions_confirmation_time` 与 `confirmed_by` 成对（编号基线:118-119），不重复写。`confirmed_by` 注释补「为空表示尚未业务确认；已确认状态必填」。
    - 触发器与 CHECK 的先后：PostgreSQL 先跑 BEFORE 行触发器再校验 CHECK，故既有断言的报错文本不变（见下夹具）。
    - 测试：新增 `acceptance/platform/numbering/NumberingRowRulesIT`（`@DisplayName` 前缀 `DATA-REPAIR-approval AP-09`）：直插 `status='CONFIRMED'` 且 `confirmed_by` 空被拒（断言约束名）；PENDING 行不写确认列通过；走 `ConfirmNumberingRule` 全流程通过。
    - 会被新 CHECK 拒绝的夹具：**核完无**——全仓 `'CONFIRMED'` 的写入只有 `ApplicationIntegrationTest.java:206`、`platform/numbering/NumberingFixtures.java:38`、`acceptance/platform/numbering/NumberingRuleVersionIdIT.java:111`，三处都同写 `confirmed_by`。须回归的断言：`platform/numbering/NumberingRuleFourEyesIT.java:307-315`（经触发器报错，文本不变，仍须跑通）。

    - 凭证号另需财务确认的条件依赖单据类型，库里落不成 CHECK，注册表写明由聚合保证（照卡）。
  - **② AP-11　删 5 个停用列，改种子**
    - 核实（全仓引用）：对 `pattern`/`reset_policy`/`start_value`/`confirmation_status`/`is_active` 且限定 `numbering_rules` 表，**源码侧唯一引用**是 `JdbcNumberingRuleRepository.java:51`、`:59` 的 `and is_active` / `where is_active` 两处谓词；`save` 的 `update numbering_rules`（:119-122）只写 `updated_at`、`version`、`event_count`；其余命中均属 `numbering_rule_versions` 的同名列（:74、:142-147，不动）。非 Java 引用：`backend/architecture/ddl-naming-baseline.txt:25`（`numbering_rules.pattern` 改名行，删列后须删该行，否则 `DdlNamingTest` 报「已修好却没删」，见该文件头注）；`backend/data-dictionary.sample.md:26` 是过期样例、不含这些列，无须动；`scripts/ci/baseline_ddl_scan.py` 无引用。测试侧唯一引用 `platform/numbering/NumberingVersionMigrationIT.java:34-37`（断言旧四列「仍在且可空」）。
    - DDL/种子（编号基线）：删 :4 注释、:8-11、:15 五列与其 CHECK、默认值；删 :23-26、:30 的 `COMMENT ON COLUMN`；种子 :172-175 改为只写 `(numbering_rule_id, document_type, version, event_count)`，**`event_count` 写 0**（NR-3：种子无对应事件；`kernel/EventSequenceContinuityTest.java:149` 已以 0 构造 `NumberingRule.restore`，口径一致，仍须跑）；:178-182 的版本种子不再从 `rules.pattern` 等取值，改为在原 `seeds` VALUES 里直接写模板文本（三种单据与各自版本 ID 不变） `'{TYPE}-{LE}-{yyyyMM}-{seq:4}'`、`'MONTHLY'`、`1`。
    - Java：删 `JdbcNumberingRuleRepository.java:51`、`:59` 的 `is_active` 谓词；方法名 `findActive` 不改（CS-1.8），语义变为「按类型查」。
    - 改断言与夹具的逐处理由：
      - `NumberingVersionMigrationIT.java:34-37`：断言「旧四列仍在」与本修复直接相反，**反转为「五列均已不存在」**（`information_schema.columns` 计数为 0）；PR 与报告单列。
    - 验收：取号、版本确认、`inForceOn`、`ListNumberingRules` 全部通过；种子对比差异只限：五列消失、`event_count` 1→0。
    - 依据：CS-1.8、CS-1.9；门禁 O12；§2.4a-4。

  - **③ AP-12**　不在本 PR：待用户定 `legacy_numbers` 去留；本 PR 不做 EXPLAIN、不改 `JdbcLegacyNumberRepository` 查询、不加索引。

  - **④ AP-13　注册表登记（无 DDL）**
    - 核实现状：`backend/architecture/data-access-registry.yaml:332-338` `outbound_deliveries` 仍为 `retention: {policy: 90d, job: platform.integration.outbound.purge}` 单档；:339-345 `source_references` 仍为 `lineage: {source: NEW}`、`retention: {policy: PERMANENT}`。`OutboundDeliveryPurgeJob` 的状态值与两档口径：**未核实，不落**（需读该作业与 `OutboundDelivery` 状态枚举，不在本任务读取范围）。`lineage` 新写法能否被 `DataArchitectureTest` 解析（`TRACES = {"legacy_numbers", "ONE_OFF_IMPORT"}`，`DataArchitectureTest.java:50`，`via: ONE_OFF_IMPORT` 在集合内；`migrated_from` 项写法）：**未核实，不落**，实施者先读该测试再改。
    - 改法（只改注册表）：`source_references.lineage` 改 `{source: ORG_SOURCE, migrated_from: [老系统人员与部门对照], via: ONE_OFF_IMPORT}`（照卡）；`outbound_deliveries` 保留期两档依 `RETENTION_PROPOSAL.md`，待核后落。
    - 卡内不一致（只列）：`source_references` 注册表现写 `PERMANENT`，而卡末「保留期与匿名化」已定人员类「离职满 10 年删除」；AP-13 未列此行，是否本 PR 同改见裁定点 6。
    - 验收：`DataArchitectureTest` 通过。

  - **⑤ AP-14　`approval_requests.document_type` 收窄到 TIMESHEET**（依 one-architecture 2026-10-16 更正，ADR-0030 U-3、D-1a-157：QUOTE 审批到报价模块上线再加；numbering 侧不动）
    - 核实：审批基线:17 内联 CHECK 含 `QUOTE`、`TIMESHEET`、`JOURNAL_VOUCHER`；审批侧现状只有 `ProcessKey.COST_TIMESHEET_APPROVAL(true, DocumentType.TIMESHEET)`（`platform/approval/api/ProcessKey.java:8`），收窄不影响运行。`DocumentType` 枚举不动，故 `ApprovalTexts.java:27-28`、`JdbcApprovalRequestRepository.java:268`、`JdbcApprovalTaskQueries.java:89`、`FlowableApprovalEngine.java:88` 的 `DocumentType.valueOf` 与 `switch` 都不变；库里只会有 TIMESHEET 行。
    - DDL（审批基线:17 内联 CHECK 改具名表级）：
      ```sql
      CONSTRAINT ck_approval_requests_document_type CHECK (document_type IN ('TIMESHEET'))
      ```
      待报价模块上线时由该模块的 PR 同时追加 CHECK 取值与审批流程（CS-1.8）。
    - 夹具：**核完无**——全仓向 `approval_requests` 直插的 SQL 只有 `platform/approval/IdempotentStartIT.java:139,156-158`、`AggregateConcurrencyIT.java:133`，`document_type` 均为 `'TIMESHEET'`；经应用启动审批的测试夹具都走 `ProcessKey.COST_TIMESHEET_APPROVAL`（`acceptance/ApprovalP4Support.java:141` 等）。`acceptance/ApprovalIdContractTest.java:174` 正则 `(TIMESHEET|QUOTE)` 只做描述文本匹配，不受影响。`scripts/ci/baseline_ddl_scan.py` 自测里的 `QUOTE` 是脚本内的合成输入（:407、:421），不读真实基线，不受影响。
    - 测试：`acceptance/platform/approval/ApprovalRequestTextAndRecordRulesIT` 加 `DATA-REPAIR-approval AP-14`：直插 `QUOTE`、`JOURNAL_VOUCHER` 被拒（断言约束名），`TIMESHEET` 通过。
    - 预期表结构 diff：`approval_requests`：`document_type` CHECK 收窄并具名。numbering 三表与种子无变化。
    - 验收：审批发起、全路径回归通过；`baseline_ddl_scan.py` 对 `approval_requests` 不再报「未建模块取值」（对 numbering 的既有提示不在本 PR）。

  - **⑥ AP-15　`summary_text` 长度 CHECK 与分级**
    - 核实：写入点只有一处——`cost/timesheet/domain/Timesheet.java:137`，`number.value() + " " + workDate + " " + submittedHours.value().toPlainString() + "h"`（单据编号、工作日、工时，约 30 至 50 字符，不含姓名、客户名）。`platform/approval/api/ApprovalSummary.java:8-17` 已在 Java 侧限 1 至 `MAX_LENGTH = 200` 且禁空白（`isBlank`）。读出处 `JdbcApprovalRequestRepository.java:272`、`JdbcApprovalTaskQueries.java:159`；并随事件 `DocumentSubmittedForApproval`、`TaskAssigned` 出域。
    - DDL：
      ```sql
      CONSTRAINT ck_approval_requests_summary_text CHECK (char_length(summary_text) BETWEEN 1 AND 200 AND summary_text ~ '\S')
      ```
      N = 200 取 Java 常量（单一口径）；`char_length` 计码点、Java `length()` 计 UTF-16 单元，库不严于 Java，不会出现 Java 放行而库拒绝；`~ '\S'` 对应 `isBlank`。超限 Java 侧已抛 `IllegalArgumentException`，不静默截断；库拒绝只会在绕过领域对象时出现。注释改写：「业务摘要：1 至 200 字符，发起时由拥有单据的业务模块给出……」。
    - 分级登记：`backend/architecture/data-classification.txt` 增一行 `platform_approval.approval_requests.summary_text | INTERNAL | 用途：审批卡片上显示的业务摘要；目前内容为单据编号、工作日、工时，不含个人信息；写入方新增字段须先评估个人信息`（沿该文件「用途：…」写法）。**登记 INTERNAL、不加 PI**，未来写入方扩内容时重判（裁定点 4，也是卡第 5 节待决 5 的子项）。
    - C4「允许写入内容清单」草稿：`cost.timesheet`／单据编号＋工作日＋合计工时／否；其他模块上线时由其 PR 补一行。
    - 夹具：既有 `new ApprovalSummary(...)` 的测试值均短于 200 且非空白（`acceptance/ApprovalP4Support.java:141`、`platform/approval/ApprovalIntegrationSupport.java:261`、`platform/approval/AggregateConcurrencyIT.java:56,98` 等）；直插 SQL 的 `summary_text` 为 `'s'`（`platform/approval/IdempotentStartIT.java:139,156-158`、`AggregateConcurrencyIT.java:133`），**不受影响**。
    - 测试：`acceptance/platform/approval/ApprovalRequestTextAndRecordRulesIT` 加 `DATA-REPAIR-approval AP-15`：201 字符被拒、空串与纯空白被拒、200 字符通过、中文 200 字通过。
    - 预期表结构 diff：`approval_requests` 新增 `ck_approval_requests_summary_text`；分级文件多一行。

  - **⑦ AP-16　办理记录成对 CHECK**
    - 核实（唯一写入点 `platform/approval/infrastructure/persistence/JdbcApprovalRequestRepository.java:146-161` `insertRecord`，唯一生产者 `platform/approval/domain/ApprovalRequest.java:306` `note(...)`，调用共 6 处）：
      | `action_kind` | 调用 | `on_behalf_of_id` | `assignee_id` |
      |---|---|---|---|
      | PASSED_AUTOMATICALLY | `ApprovalRequest.java:163` | 空 | 空 |
      | CLAIMED | :171 | 空 | 空 |
      | RELEASED | :179 | 空 | 空 |
      | TRANSFERRED | :189 | 空 | `to`（`ApprovalTask.transfer` 对 `to` 做 `requireNonNull`，`ApprovalTask.java:108`）|
      | REASSIGNED | :200 | `previous`，**可空**（`task.assignee().orElse(null)`，任务待认领时无原处理人）| `to`（`ApprovalTask.reassign` 的 `requireNonNull`，:121）|
      | APPROVED / REJECTED / RETURNED / ADVISED | :210 | 空 | 空 |
      两个方向都成立，可双向写；「代办」只在改派出现（`domain/TaskHandling.java:17` 注释）。
    - DDL（表级，两条）：
      ```sql
      CONSTRAINT ck_approval_task_records_assignee CHECK ((action_kind IN ('TRANSFERRED', 'REASSIGNED')) = (assignee_id IS NOT NULL))
      CONSTRAINT ck_approval_task_records_on_behalf_of CHECK (on_behalf_of_id IS NULL OR action_kind = 'REASSIGNED')
      ```
      不写「REASSIGNED ⇒ `on_behalf_of_id` 非空」（原处理人可空，上表已核）。`record_reason` 部分归 PR-6 的 `ck_approval_task_records_record_reason`，本条不重复。注释补：`on_behalf_of_id`「为空表示不是改派，或改派时任务尚无处理人」，`assignee_id`「为空表示动作不是转交、改派」。
    - 测试：`ApprovalRequestTextAndRecordRulesIT` 加 `DATA-REPAIR-approval AP-16`：转交缺处理人被拒、认领带处理人被拒、通过带被代理人被拒；转交、改派（有无原处理人各一）、认领、释放、通过、驳回、退回、加签建议、自动通过各一条合法通过（复用现有流程夹具）。
    - 夹具：**核完无**——直插办理记录的只有 `acceptance/platform/approval/ApprovalOrderNotFromIdsIT.java:105-110`，以 `INSERT … SELECT` 复制既有行，所复制的行由 `claimReleaseClaimAndApprove`（:63-68）造出，动作为 CLAIMED、RELEASED、APPROVED，两列恒空，新 CHECK 通过，无须改。
    - 预期表结构 diff：`approval_task_records` 新增上述两条。

  - **⑧ AP-17　`numbering_counters` 取值与格式（改 CHECK，不作外键）**
    - 核实：`period_key` 的唯一生产者 `api/ResetPolicy.java` 的 `periodKey`：NEVER→`'-'`、YEARLY→`yyyy`、MONTHLY→`yyyyMM`、DAILY→`yyyyMMdd`；`last_value` 写入点只有 `JdbcNumberingRuleRepository.reserveNext`（:178-195），首值为模板 `start_value`（`>= 1`，编号基线:100 CHECK 与 `NumberingRule.propose` :116），之后 `+1`。`{seq:N}` 的 N 为 1 至 12（`domain/NumberingPattern.java:34`），超宽时 `render` 抛 `IllegalStateException`（:93-95）并使事务回滚。
    - DDL（具名，表级）：
      ```sql
      CONSTRAINT ck_numbering_counters_period_key CHECK (period_key ~ '^(-|[0-9]{4}|[0-9]{6}|[0-9]{8})$')
      CONSTRAINT ck_numbering_counters_last_value CHECK (last_value BETWEEN 1 AND 999999999999)
      CONSTRAINT ck_numbering_counters_document_type CHECK (document_type IN ('QUOTE', 'TIMESHEET', 'JOURNAL_VOUCHER'))   -- 与 ck_numbering_rules_document_type 同一取值集，不随 AP-14 收窄
      ```
      上界 999999999999 即 `{seq:12}` 的容量，卡原文只写 `>= 1`，上界为本节追加（裁定点 5）。`document_type` 不建外键：目标是 `uk_numbering_rules_document_type` 唯一约束而非主键（NAMING_STANDARD §2.4a-1），`DdlNamingTest` 是否放行**未核实，不采用**。`ddl-naming-baseline.txt:22-24` 三行（`document_type` 改名建议「或保留并补 CHECK」）：本 PR 给 `numbering_counters` 补了 CHECK（`numbering_rules` 已有），是否可删这两行取决于 `DdlNamingTest` 对「保留并补 CHECK」的判定，**未核实，不落**；`legacy_numbers` 那行不动。
    - 夹具：**核完无**——全仓直插 `numbering_counters` 的只有 `platform/numbering/NumberingRulesIT.java:206`（`'TIMESHEET'`、`'20270506'`、1）与 :214-225（`'QUOTE'`、`'202701'`、1，期望命中 `scope_kind` 系约束）；取值均在三值内、周期键合法、`last_value` 为 1，新 CHECK 全部通过，断言与期望约束名不动。
    - 测试：`NumberingRowRulesIT` 加 `DATA-REPAIR-approval AP-17`：四种周期（`-`、`2026`、`202610`、`20261014`）取号通过；`'2026-10'`、`'20261'`、`''` 被拒；`last_value = 0`、负数、`10^12` 被拒；`document_type = 'PAYMENT'` 之类三值外取值被拒。
    - 预期表结构 diff：`numbering_counters` 新增三条 CHECK。


  - **夹具与断言总表**
    - 本 PR 真正要改的测试只有：
      - `platform/numbering/NumberingVersionMigrationIT.java:34-37`（AP-11，反转，非验收目录；PR 与报告单列理由：断言「旧列仍在」与删列直接相反）。
      - 新增 `acceptance/platform/numbering/NumberingRowRulesIT`（AP-09、AP-17）与 `acceptance/platform/approval/ApprovalRequestTextAndRecordRulesIT`（AP-14、15、16）。
    - **验收目录（`acceptance/`）：无需改动。** 逐项重核：AP-14 收窄后 `QUOTE` 不再有审批侧夹具（见 ⑤）；财务确认链不删，`UserIdColumnsIT.java:49`、`UserIdContractTest.java:34`、`AuditIdContractTest.java:29,89`、`OpenApiExportTest.java:125` 的 `financeConfirmedBy` 条目保留；`NumberingRuleConfirmerIT`、`NumberingRuleVersionIdIT`、`LegacyNumberSourceIT`、`IdQueryParameterIT`、`LegalEntityCodeCorrectionIT` 的 `QUOTE` 仍是合法单据类型。故本 PR 不需 Acceptance-Ruling（实施中若发现需要，再单列）。
    - 既有断言回归（不改，须跑通）：`NumberingRuleFourEyesIT.java:307-315`（触发器先于 CHECK）；`EventSequenceContinuityTest.java:149`（AP-11 种子 `event_count` 0）；`NamingSurfaceTest.java:274-293`（numbering 种子与枚举不变）。

  - **请 one-data 裁定**
    1. `summary_text` 登记 INTERNAL、不加 PI（唯一写入点不含个人信息）是否接受（卡待决 5 的子项）。
    2. `last_value` 上界 999999999999 是否追加（卡原文只写 `>= 1`）。
    3. AP-13 是否同时把 `source_references` 注册表保留期改为与 `RETENTION_PROPOSAL.md` 一致；`outbound_deliveries` 两档与 `lineage` 写法需先核。
    4. `ddl-naming-baseline.txt:22-24` 的 `document_type` 三行在补 CHECK 后能否删（取决于 `DdlNamingTest` 判定，未核实）。

  - **卡与 ADR 字面不一致（仅余未解决项）**：AP-14 依 one-architecture 2026-10-16 更正（ADR-0030 U-3），原「ADR-0010 第 5 项待判」「删财务确认」「D-1a-157 冲突」均已解决，不再列。余：卡 AP-13 注册表 `source_references` 写 `PERMANENT`，与卡末保留期「离职满 10 年删除」不符；卡 AP-17 只写 `last_value >= 1`，本节追加了上界；卡第 7 节「种子数据变化（AP-11 的 event_count 除外）不得出现」与 AP-11 删列后种子写法的变化需一并理解为允许。

  - **落地记录（one-1a-next-5，2026-10-16）**：签发见 `data/reviews/DATA-REPAIR-approval-PR7.md`；裁定 RULING-DR-PR7-1：AP-16 `ck_approval_task_records_assignee` 落地后，PR-6 验收 `ApprovalRowRulesIT` 夹具 `COPY_CLAIMED_RECORD` 复制 CLAIMED 行改转交/改派时须带处理人，否则先撞新约束、测不到 `record_reason`——只补夹具前提，断言不变；`ck_approval_requests_document_type` 写成列内联具名 CHECK（DdlNamingTest 对 `_type` 列要求内联），名称与取值同本节；AP-09 凭证号的财务确认依赖单据类型，库内不设 CHECK，注册表注明由聚合 `NumberingRule.completeIfAllGiven` 保证。
### AP-18　LN-2　中　`legacy_numbers.source_system` 含组织源取值
- 证据：PA LN-2（迁移 `:59`、`:60`）；SS §7 第 4 条：`num.legacy_numbers.source_system:59` 含 `ORG_SOURCE`、`ACCOUNTING_SYSTEM`，组织源不持有单据编号；`ACCOUNTING_SYSTEM` 在 `source_references` 已被已建代码使用（`SourceReference.java:80`），本条不判它。
- 改法：`source_system` 收窄到持有单据原编号的来源（去 `ORG_SOURCE`）；`document_type` 补 CHECK（口径同 AP-14、AP-17）。待核：`JdbcLegacyNumberRepository`、迁入导入脚本与测试夹具有无使用 `ORG_SOURCE`；`legacy_numbers` 唯一范围需 one-ops 按 ADR-0037 取证是否有跨法人重复原编号（`BASELINE_TABLE_REVIEW.md` §7 第 6 项，不在本卡）。ADR-0039 第 4 条第 2 款要把本表整体搬到 `platform_integration`（首个迁入阶段前，允许延后），本卡改动随表搬迁。
- 预期表结构 diff：`legacy_numbers`：`source_system` 的 CHECK 收窄，新增 `document_type` 的 CHECK。
- 验收要点：取值全集核对后只剩有单据编号的来源；原编号分页检索（AP-12）原样通过。
- 依据：门禁 B7、O8；CS-1.8；SS §7 第 4 条。

低级顺带（不阻塞）：AR-2（降为低；`ProcessKey` 只有一个取值，唯一约束名 `uq_approval_requests_document` 不合 `uk_` 归 R-2a）、AR-5、TK-2、TK-3、TK-4、RC-2、EA-1、OD-4、OD-5、OD-7、SRF-2、SRF-3、X-3；NR-2、NR-3 随 AP-11。证据见 PA 对应条目。

## 5. 已定与待用户决定

### 已定（用户 2026-10-16 决定）
1. 审批被驳回、撤销后可否再发起，加签人驳回是否须原因，`hookDecidesReason` 去留（原 PA 待决 8、9）：用户答「驳回、撤销后可原单再发起；加签人驳回也须填原因；删掉免填放行」（收件箱 2026-10-16-one-data-approval-resubmit；intent `2026-10-16-085331-one-data-审批被驳回-被撤销后能不能再发起.md`）。落点：AP-01 成对 CHECK 覆盖驳回、撤销、退回及加签人驳回；删 `DecideTasks` 的 `hookDecidesReason` 放行分支；状态机保留 `REJECTED → RUNNING`、`CANCELLED → RUNNING`。

### 待用户决定
2. 编号规则版本的生效日语义（NV-3，PA 待决 6）、`decision_ref` 填什么与 CONFIRMED 是否必填（AP-10）。
3. `outbound_deliveries` 保留口径里「人工处理过的」指什么（PA 待决 3）；审批四表保留期与归档（待决 1）；`process_archive_skips` 保留期（待决 2，PS-1）。
4. **`source_references` 的个人信息判定与永久保留**：`source_id` 对 `EMPLOYEE` 是来源系统人员标识，OV 判 `INTERNAL + PI`；它须与自有对象同寿命，故 `PERMANENT` 需像 `emergency_admins` 那样给出用户决定出处，否则违反 R-2。判 PI 与否、永久或期限，登记待决收件箱，本卡不起条目。
5. `event_count` 去留（PA 待决 12，AP-07）；升级、关闭是否进办理记录（RC-3，待决 11）；平台能力入信息图还是登记技术表（X-5，待决 10）；自由文本列的分级与是否允许原因文本进事件与日志（待决 5，含 AP-15 的 `summary_text`）。
6. （one-architecture 判，非用户项，列此作提示）ADR-0010 第 5 项「三种单据都从默认模板起步」是否已决定现在就建（AP-14 第 3 步，SS §7 第 2 条）。

## 6. 不含（已有归属，本卡不起条目）
| 项 | 归属 |
|---|---|
| AR-2 `document_type` 与 `process_key` 一一对应 | 降为低（OV），顺带；`uq_` → `uk_` 改名归 R-2a |
| OD-6 `recipient_codes` 分级（统一分级：中） | U-4(b) 已定 `INTERNAL + PI`，改文件归 ADR-0043 实施 PR |
| X-4 分级四段格式 | ADR-0043 实施 PR |
| X-1 全部文本列长度与禁空串（中） | one-quality 守护一次补（本卡仅覆盖 AP-01、AP-08、AP-15 涉及的列；其余列清单见末节「机械类低级项」） |
| X-2 内联枚举 CHECK 具名 | G-D12 实现时一次改基线（OV 建议新增规则 4） |
| X-6 `numbering_rule_version_id` | 并入 TS-UNIQ（cost.timesheet，同表同基线开放期） |
| NV-3 生效日次序约束 | 待用户（待决 2）后再落 |
| `uk_outbound_deliveries_card` 漏收件人（O15，ADR-0033 第 7 项第 3 条；SS 补审缺项 2） | **高**；P5 未覆盖（已核 P5 分支 `platform_integration` :59-64，去重键仍为 `(subject_kind, subject_id, purpose_kind, trigger_event_id)`，收件人只是 `recipient_codes text[]`）；归 DA-10（RULING-P4-D4），首次部署前；改法涉及「一张卡一行」是否变「一收件人一行」，须结构设计，本卡不起条目 |
| `source_references` 缺 `migration_batch_id`、`source_row_digest`、`source_reference_status`（ADR-0036 第 3 条，SS 缺项 6） | 无级别；首个迁入阶段（NEXT_BATCH_PLAN P6）前补，CS-1.8 现在不算缺陷 |
| `legacy_numbers` 整表搬 `platform_integration`（ADR-0039 第 4 条第 2 款，SS 缺项 7） | 无级别；可放生产后；`migration_batches` 表尚不存在，无外键可建 |
| `source_references` 永久保留与个人信息判定（SS 缺项 9） | 待用户（第 5 节第 4 条），待核 |
| `migration_batches`、`external_identities` 等 ADR-0036 / 0039 新表 | 首个迁入阶段前（CS-1.8）；`external_identities` 与 `employees` 删 `login_name`、`wecom_user_id`（O15 高）归 DA-3，见 DATA-REPAIR-workforce 不含 |
| X-6（高） | 并入 TS-UNIQ（上面已列） |

## 7. 验证办法
- 守护：`DdlNamingTest`、`DataArchitectureTest`、`GlossaryTest` 全绿；新增约束全部具名；不新增例外清单条目。
- 测试：AP-01 先写「钩子节点驳回无原因应失败」的测试再删分支；AP-01 至 AP-06、AP-09、AP-14、AP-15、AP-17 各一条「违反即被拒、合法即通过」的集成测试；发起、转交、改派、撤回、升级、自动通过、归档全路径回归；测试名以 `DATA-REPAIR-approval AP-nn` 开头。
- 表结构 diff：两个空库分别执行改动前（P5 合并后的 main）、改动后的基线，`pg_dump --schema-only` 对比；预期差异只能是第 4 节所列。不得出现：审批表列类型与列名变化；`approval_requests.id` 等主键变化；种子数据变化（AP-11 的 `event_count` 除外）。
- `scripts/precheck.sh <基线>` 全部 PASS 或有理由的 SKIP，输出贴进 PR。

## 8. 执行者与复审
- 执行者：sonnet 子代理；AP-01、AP-02、AP-03、AP-09 首轮复审用 opus（状态机与并发），其余 sonnet 复核。
- 工作区：独立 worktree，分支 `claude/one-1a-next-4-data-repair-approval`，基线为 P5 合并后的 main。
- 报告：向 one-1a-next-4 并抄 one-coordinator，分开写「已验证」与「未运行」。

## 9. 禁止事项
- 不放宽、不删除断言（AP-01 删的是应用代码里的放行分支，不是断言；PR 单列）；不改验收测试目录；不改卡外文件。
- AP-02 不写「非 CLAIMED ⇒ 处理人为空」「COMPLETED ⇒ `claimed_at` 非空」；AP-01 的 CHECK 不得早于删分支落地。
- 不追加迁移（ADR-0038）；不改共享构件。
- 不联网、不起容器（Testcontainers 白名单镜像除外）、不建账号；不打印、不写入密钥；日志不打印收件人与数据库错误详情。
- 缺失或无效输入失败并告警，不静默跳过。

## 10. 机械类低级项（按表列出，不逐条展开；列清单与行号见 `review-parts/SCAN_SUPPLEMENT.md`）

SS 的行号取 `origin/main`；同一类的改法一致，由本卡执行者按表一次改完，每类一条验收即可。「级别」按统一分级：无长度上限为中，其余为低。§1 虽为中，因机械且同一改法，一并列此。

| SS 节 | 类 | 级别 | 属本卡 schema 的范围 |
|---|---|---|---|
| §1 | text 列无长度与格式约束（含 `summary_text`，已单列 AP-15） | 中 | apv：approval_requests（process_key、result_reason、summary_text）、approval_tasks（node_key、approved_hash、result_reason、node_name）、approval_task_records（node_key、node_name、record_reason）、process_archive_skips（object_key、failure_message，后者已单列 AP-08）共 12 列；itg：outbound_deliveries 9 列（title、description、target_url、last_error、response_code、handled_note、handled_result、recipient_codes、skipped_recipient_codes）；num：numbering_rules.pattern（AP-11 删列）、numbering_counters（document_type、period_key，AP-17）、legacy_numbers（document_type、legacy_number）、numbering_rule_versions.template_text 共 6 列；全部 27 列。其中分级登记待核、注释写了「至多 N」却无 CHECK 的另有 `numbering_rule_versions.template_text:98` |
| §2 | 业务默认值（NAMING_STANDARD §2.4a-3 要求应用显式写入） | 低 | itg.outbound_deliveries.kind:54；num.numbering_rules.is_active:11、confirmation_status:15（后两项随 AP-11 删列消失）；共 3 项 |
| §3 | 可空列缺「为空表示」注释 | 低 | 「无空值说明」共 23 列：apv 7（approval_requests 2、approval_tasks 5）、itg outbound_deliveries 8、num 8（numbering_rules 4 列随 AP-11 删除，numbering_rule_versions 的 confirmed_by、confirmed_at、finance_confirmed_by、finance_confirmed_at 4 列）；「等价措辞」列另需 one-quality 判守护是否接受 |
| §5 | `DEFAULT now()` 与「应用注入时钟」矛盾（NAMING_STANDARD §2.4a-3；`sql-database-clock-exceptions.txt`） | 低 | apv 5 表、itg 2 表、num 4 表共 11 表 22 列（`created_at`、`updated_at`）；另 `num` 种子 :179 `numbering_rule_versions.authored_at` 取 `now()`。全库口径是否统一改由应用传时钟属另卡（identity 卡不含 X-1、PI 待决 10），本卡不单改 |
| §6 | 内联枚举 CHECK 未具名 | 低 | apv 6 处（approval_requests 2、approval_tasks 3、approval_task_records 1）；itg outbound_deliveries 2 处；num 4 处（numbering_rules 2 个随 AP-11 删、numbering_rule_versions 2）；共 12 处，另内联数值 CHECK 7 处（`current_round:10`、`start_rounds.round_number:45`、`record_number:131`、`round_number:132`、`numbering_rules.start_value:10`、`numbering_rule_versions.version_number:97`、`start_value:100`）；随 G-D12 实现一次改基线，不在本卡单做 |
| §4 | 成对可空列缺 CHECK（中） | 中 | apv 第 5、6、7 组 → AP-01、AP-02、AP-03、AP-16；itg 第 12 组 → AP-06；num 第 13 组 → AP-09；第 15 组「已含」的 `source_references`、`numbering_counters` 对照无须动 |

### 保留期与匿名化（用户已定「按 one-data 建议」，见 `../RETENTION_PROPOSAL.md`）

- 审批四表：10 年，归档到对象存储后在线按期删除。
- `process_archive_skips`：引擎历史清理完成后再留 1 年；注册表同表两行须去重。
- `outbound_deliveries`：成功 90 天；失败（FAILED、DEAD_LETTER）或已人工处理 1 年；注册表改两档（状态值待核）。
- `source_references`（人员类，`source_id` 为来源人员标识）：员工离职满 10 年后删除；其他类别维持永久。用户决定出处即本条。
