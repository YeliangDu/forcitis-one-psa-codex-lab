# 派工卡 DATA-REPAIR-approval：platform_approval、platform_integration、platform_numbering 已建表的数据设计缺陷修复（草稿）

- 草稿：one-data 起草助手，2026-10-16；待 one-data 终审、one-coordinator 派工。
- 证据来源：`docs/modernization/data/review-parts/platform_approval_integration_numbering.md`（下称 PA）与 `OPUS_VERIFY.md`（下称 OV）；以 OV 为准。「:N」行号指 PA 审查时 main 基线中对应迁移文件：`platform_approval/V20261014132000__platform_approval_baseline.sql`、`platform_integration/V20261014133000__platform_integration_baseline.sql`、`platform_numbering/V20261014100100__platform_numbering_baseline.sql`；R-1b P4 / P5 已改这些文件，实施前逐条重核行号。PA 按旧版门禁判定（无 B3a、O15、附录 C，OV 误报与修正第 8 条）。
- 「待核」表示分报告与复核报告都没有给出证据，实施者先核实再改。

## 1. 卡号与类别
- 卡号：DATA-REPAIR-approval
- 类别：缺陷修复（SESSION_PLAYBOOK §6.1 第 9 条）
- 批次：已建表数据设计审查修复批（门禁 v2 §1.2 第 6 条）；一个小 PR 直接合入 main
- 建议负责会话：one-1a-next-4
- 内部顺序：AP-01 第 1 步（删 `hookDecidesReason` 分支）必须先于 AP-01 / AP-02 的 CHECK；AP-03 与 AP-04 同改审批表，同一提交

## 2. 依据
- 门禁 v2：B3、B5、O4、O7、O8、O10、C3、C5
- SESSION_PLAYBOOK §14.1 D2、D12、D15、D17 细则 2；§12.2；ADR-0038；ADR-0033；ADR-0027 第 2 项第 4 条；NAMING_STANDARD §2.4a-1（外键目标为主键）、§2.4a-5
- 规则卡：RULE-280 修订段「驳回与退回仍须原因」（`BUSINESS_RULES.md`，2026-10-14）；GUIDING_PRINCIPLES §11 O-3（日志不含数据库服务端错误详情）
- 复核编号：OV「AR-1 确认，改法需修正」「高风险项判断 PA 第 1、2、4 条」「PA OD-1 确认」「PA NV-2 确认，升为高」「PA AR-2 降为低」；建议新增规则 2、3、5
- 分报告编号：PA AR-1、AR-3、AR-4、TK-1、RC-1、SR-1、OD-1、OD-2、OD-3、PS-2、SRF-1、NR-1、NR-2、NR-3、NV-1、NV-2、LN-1

## 3. 范围与卡上文件

允许改动（越界先停下报告，不先做后报）：
- 迁移基线：`platform_approval/V20261014132000__platform_approval_baseline.sql`；`platform_integration/V20261014133000__platform_integration_baseline.sql`（只限 `outbound_deliveries`）；`platform_numbering/V20261014100100__platform_numbering_baseline.sql`（`numbering_rules`、`numbering_rule_versions`、`legacy_numbers`）
- `backend/architecture/data-access-registry.yaml`（审批四表、`outbound_deliveries`、`source_references`、numbering 各表条目）
- Java：`backend/src/main/java/com/forcitis/one/platform/approval/application/DecideTasks.java`（AP-01 第 1 步）、`.../approval/infrastructure/flowable/ProcessArchiveJob.java`（AP-08）、`.../platform/integration/domain/OutboundDelivery.java` 与 `infrastructure/persistence/JdbcOutboundDeliveryRepository.java`（AP-06、AP-07）、`.../platform/numbering/infrastructure/persistence/JdbcNumberingRuleRepository.java`、`JdbcLegacyNumberRepository.java`（AP-09 至 AP-12）
- 测试：上述类的现有测试与新增 CHECK 集成测试；`ProcessArchiveJobIT` 只改失败消息断言（须单列理由）

归属：本卡独占上述表的约束、索引与列注释片段。

不碰：`approval_tasks` 等表的列与类型（P4 已定）；`EventEnvelope`、`AggregateReference`、kernel `AggregateRoot`（共享构件，需改时停下报告）；`act_*` 引擎表；验收测试目录；他卡文件。

## 4. 条目清单

### AP-01　AR-1　高（OV 确认，改法已修正）　「驳回、退回、撤销必须有原因」库内无成对 CHECK
- 问题：`approval_requests.status` 含 REJECTED / RETURNED / CANCELLED，`result_reason` 可空，无成对 CHECK；`approval_task_records.record_reason`、`approval_tasks.result_reason` 同缺。
- 证据：PA `:12-13`；`DecideTasks.java:108`、`ReturnToInitiator.java:42`、`ApprovalRequest.java:285,342-345,188,198`；RULE-280 修订段。OV：`DecideTasks.java:106-108` 的 `hookDecidesReason`（节点配了前置钩子时允许无原因驳回）会让 `Reason.ofNullable` 得空、写成 `REJECTED` + `result_reason` 空，直接加成对 CHECK 会拒绝这条设计内路径；目前全仓无 `ApprovalHook` 实现，现有数据不会撞上。
- 改法（顺序不可颠倒）：
  1. 先删 `DecideTasks` 的 `hookDecidesReason` 放行分支（钩子要原因就由钩子返回原因；该分支与 RULE-280 修订「驳回与退回仍须原因」冲突）；改动在 PR 单列。
  2. 再加 `ck_approval_requests_result_reason`：`(status IN ('REJECTED','RETURNED','CANCELLED')) = (result_reason IS NOT NULL)`（RUNNING、APPROVED 时为空：`ApprovalRequest.restart` 置空、`approve` 传 null）。
  3. `approval_task_records`：`ck_approval_task_records_record_reason`：`action_kind IN ('REJECTED','RETURNED','TRANSFERRED','REASSIGNED') ⇒ record_reason IS NOT NULL`（写成 `NOT (…) OR …`）。
  4. `approval_tasks`：单向写法 `ck_approval_tasks_result_reason`：`NOT (outcome_kind IN ('REJECTED','RETURNED') AND effect_kind = 'BINDING') OR result_reason IS NOT NULL`；加签人（ADVISORY）驳回是否须原因待用户（待决 1），故不写成双向。
  5. 三列再加 `<> ''`（PA X-1：全库文本列无禁空串约束，本卡只覆盖这三列）。
- 预期表结构 diff：`approval_requests`：新增 `ck_approval_requests_result_reason`（含非空串）；`approval_task_records`：新增 `ck_approval_task_records_record_reason`；`approval_tasks`：新增 `ck_approval_tasks_result_reason`。
- 验收要点：删分支后，节点配钩子的驳回在无原因时失败并告警；`RUNNING` / `APPROVED` 带 `result_reason` 被拒；撤回、转交、改派、驳回、退回无原因被拒；扩展 G-D12：`requireReason` 调用点与表上成对 CHECK 由同一测试比对（OV 建议新增规则 2）。
- 依据：门禁 B3、B5；D2、D5；OV「高风险项判断 PA 第 1 条」。

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

### AP-03　AR-4 + AR-3　高（AR-4 随 AR-1 成对同级）　三处 `instance_id` 无外键；`current_instance_id` 可空
- 问题：`approval_requests.current_instance_id`、`approval_tasks.instance_id`、`approval_task_records.instance_id` 都应指向 `start_rounds(instance_id)` 却无 `REFERENCES`；`current_round >= 0` 与 `ApprovalRequest.begin` 一律从 1 起冲突；`current_instance_id` 每次写入都有值却可空。
- 证据：PA `:11,:24,:69,:134,:10,:30,:45,:53`；`ApprovalRequest.java` 构造方法 `requireNonNull(instanceId)`；`JdbcApprovalRequestRepository.java:166-176`；OV「高风险项判断 PA 第 2 条」：三处外键可行，`approval_requests` 一侧须 `DEFERRABLE INITIALLY DEFERRED`（同事务先写请求后写轮次，`start_rounds.approval_request_id` 反向引用请求，形成环），与 `lockForStart` 咨询锁（`JdbcApprovalRequestRepository.java:65`）无冲突（外键只加行级共享锁）。
- 改法：
  - 三处加 `fk_approval_requests_current_instance_id`（`DEFERRABLE INITIALLY DEFERRED`）、`fk_approval_tasks_instance_id`、`fk_approval_task_records_instance_id` → `start_rounds(instance_id)`（目标为 `uk_start_rounds_instance_id`，待核是否通过 G-DDL1「外键目标必须主键」守护，见 AP-04 同类问题）
  - `current_round` 的 CHECK 改 `>= 1`，注释改；`current_instance_id` 改 `NOT NULL`，`uk_approval_requests_current_instance_id` 由部分唯一改普通唯一；`readRow` 的 `new ApprovalInstanceId(null)` 路径改为缺失即失败（待核 P4 后该行是否还在）
  - 注册表登记 `current_round`、`current_instance_id` 为投影（可由 `start_rounds` 重算）
- 预期表结构 diff：`approval_requests`：`current_instance_id` 改 `NOT NULL`，唯一索引改普通唯一，`current_round` 的 CHECK 改写，新增 DEFERRABLE 外键；`approval_tasks`、`approval_task_records`：各新增一个外键。
- 验收要点：发起审批（先请求后轮次，同事务）、重新发起、转交、归档各路径通过；指向不存在实例的行被拒。
- 依据：门禁 B4、O9、O8；D2。

### AP-04　RC-1　中　办理记录的 `task_id` 与 `approval_request_id` 分别外键，不保证同属一个请求
- 证据：PA `:146-147`、`:70`、`:148`（`record_number` 按请求编）；OV：复合外键 `(task_id, approval_request_id)` 可行，代价是 `approval_tasks` 多一个唯一约束。
- 改法：`approval_tasks` 加 `uk_approval_tasks_task_id_approval_request_id UNIQUE (task_id, approval_request_id)`；`approval_task_records` 改为复合外键 `fk_approval_task_records_task`，去掉单列 `fk_approval_task_records_task_id`。**待核**：该外键目标是唯一约束而非主键，NAMING_STANDARD §2.4a-1 要求外键目标为主键，须核 `DdlNamingTest` 是否放行；不放行则由 one-coordinator 判是否登记例外，未判前不改。
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
- 改法：新增 `ck_outbound_deliveries_*`：`status = 'PENDING' ⇒ next_attempt_at IS NOT NULL`（OV 已核）；`status = 'SENT' ⇒ sent_at IS NOT NULL`；`attempt_count >= 0`；`cardinality(recipient_codes) = 0 ⇒ status IN ('FAILED','SKIPPED')`（单向）；`FAILED`、`SKIPPED`、`DEAD_LETTER ⇒ last_error IS NOT NULL`：**待核**（`OutboundDelivery.java:79` 无收件人直接 FAILED，是否写 `last_error` OV 未核；SKIPPED 同），核实前不落。`event_count >= 0` 随 AP-07。
- 预期表结构 diff：`outbound_deliveries`：新增上述 CHECK。
- 验收要点：各状态转换写入通过；PENDING 无下次尝试时间被拒；无收件人投递仍可建为 FAILED。
- 依据：门禁 B3、B5；D2；OV「PA OD-1 确认」。

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

### AP-09　NV-2　高（OV 升级，原中）　`numbering_rule_versions` 的 CONFIRMED 不要求确认人
- 问题：迁移只约束「作者不得确认自己的版本」「两个确认人不同」「确认人与确认时间成对」；触发器 `guard_rule_version_change` 只管状态流转，INSERT 不受控；与 authorization_requests 同类的双人确认防线缺口。
- 证据：PA `:94-120`、`:104,:114-119`、`:141-163`；`JdbcNumberingRuleRepository.update`（`:148-166`）；`NumberingRule.completeIfAllGiven`（`NumberingRule.java:187-193`）；ADR-0027 第 2 项第 4 条；OV「PA NV-2 确认，建议升为高」：所有现有写入点不会撞上（种子与 `propose` 都是 PENDING）。
- 改法：新增 `ck_numbering_rule_versions_confirmed`：`status <> 'CONFIRMED' OR confirmed_by IS NOT NULL`（确认人与时间已成对，故时间随之）；凭证号另需财务确认的条件依赖单据类型，库里落不成 CHECK，注册表写明由聚合保证。**先 grep `backend/src/test`**：无确认人的 CONFIRMED 夹具（OV 标「未核」）。是否追加「待确认 ⇒ 确认列全空」：待核状态取值全集。
- 预期表结构 diff：`numbering_rule_versions`：新增 `ck_numbering_rule_versions_confirmed`。
- 验收要点：直接插入无确认人的 `CONFIRMED` 被拒；正常确认流程通过。
- 依据：门禁 B3、B5；OV 建议新增规则 1。

### AP-10　NV-1　中　配置型版本表缺 `decision_ref`
- 证据：PA `:94-120`；SESSION_PLAYBOOK:286、:409（配置类版本表须含批准人、决定出处）。
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

低级顺带（不阻塞）：AR-2（降为低；`ProcessKey` 只有一个取值，唯一约束名 `uq_approval_requests_document` 不合 `uk_` 归 R-2a）、AR-5、TK-2、TK-3、TK-4、RC-2、EA-1、OD-4、OD-5、OD-7、SRF-2、SRF-3、NC-1、LN-2、X-3。证据见 PA 对应条目。

## 5. 待用户决定
1. 加签人（ADVISORY）的驳回是否也须填原因（PA 待决 9）：决定 AP-01 第 4 步写法；审批请求被驳回、撤销后可否再发起（待决 8：`ApprovalRequest.MACHINE` 允许 `REJECTED → RUNNING`、`CANCELLED → RUNNING`）。
2. 编号规则版本的生效日语义（NV-3，PA 待决 6）、`decision_ref` 填什么与 CONFIRMED 是否必填（AP-10）。
3. `outbound_deliveries` 保留口径里「人工处理过的」指什么（PA 待决 3）；审批四表保留期与归档（待决 1）；`process_archive_skips` 保留期（待决 2，PS-1）。
4. **`source_references` 的个人信息判定与永久保留**：`source_id` 对 `EMPLOYEE` 是来源系统人员标识，OV 判 `INTERNAL + PI`；它须与自有对象同寿命，故 `PERMANENT` 需像 `emergency_admins` 那样给出用户决定出处，否则违反 R-2。判 PI 与否、永久或期限，登记待决收件箱，本卡不起条目。
5. `event_count` 去留（PA 待决 12，AP-07）；升级、关闭是否进办理记录（RC-3，待决 11）；平台能力入信息图还是登记技术表（X-5，待决 10）；自由文本列的分级与是否允许原因文本进事件与日志（待决 5）。

## 6. 不含（已有归属，本卡不起条目）
| 项 | 归属 |
|---|---|
| AR-2 `document_type` 与 `process_key` 一一对应 | 降为低（OV），顺带；`uq_` → `uk_` 改名归 R-2a |
| OD-6 `recipient_codes` 分级 | U-4(b) 已定 `INTERNAL + PI`，改文件归 ADR-0043 实施 PR |
| X-4 分级四段格式 | ADR-0043 实施 PR |
| X-1 全部文本列长度与禁空串 | one-quality 守护一次补（本卡仅覆盖 AP-01、AP-08 涉及的列） |
| X-2 内联枚举 CHECK 具名 | G-D12 实现时一次改基线（OV 建议新增规则 4） |
| X-6 `numbering_rule_version_id` | 并入 TS-UNIQ（cost.timesheet，同表同基线开放期） |
| NV-3 生效日次序约束 | 待用户（待决 2）后再落 |
| 收件人列、`uk_outbound_deliveries_card` 加 `recipient_user_id` | DA-10（RULING-P4-D4） |
| `migration_batches`、`external_identities` 等 ADR-0036 / 0039 新表 | 首个迁入阶段前（CS-1.8） |

## 7. 验证办法
- 守护：`DdlNamingTest`、`DataArchitectureTest`、`GlossaryTest` 全绿；新增约束全部具名；不新增例外清单条目。
- 测试：AP-01 先写「钩子节点驳回无原因应失败」的测试再删分支；AP-01 至 AP-06、AP-09 各一条「违反即被拒、合法即通过」的集成测试；发起、转交、改派、撤回、升级、自动通过、归档全路径回归；测试名以 `DATA-REPAIR-approval AP-nn` 开头。
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
