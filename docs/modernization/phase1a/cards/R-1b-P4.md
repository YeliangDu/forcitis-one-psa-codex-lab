---
卡号: R-1b-P4
状态: 完成
前置: R-1b-P3b
---
# 派工卡 R-1b P4：审批任务、流程实例与单据标识改自有 UUIDv7，审批子表挂审批请求，外发投递去重键拆列

## 1. 卡号与类别
- 卡号：R-1b P4（R-1b 的第 4 个子 PR；并入 DA-6、DA-4 的审批与外发投递部分、DDL-04、DDL-08、G-A1 的审批部分；**并入改名**：售前工时节点「区域负责人审批」改称「部门负责人审批」（ARCH-D5 D5-06，RULE-389、RULE-392）；**随带修复** P3b 终审 Low 四项，见第 3.6 节）
- 类别：结构性重构（含改名）
- 批次与整合分支：Phase 1a 下一批 R-1b 线（`phase1a/NEXT_BATCH_PLAN.md` 7.11：R-1b-0a → P3b → **P4** → P5 → F-1）；单独一个 PR，直接合入 main
- 前置：P3b（PR #260，分支 `claude/one-1a-next-3-r1b-p3b`）合并。本卡按 P3b @ c70027d2 盘点；P3b 合并前开工的只能是测试代理，基线为 P3b 分支；实现代理在 P3b 合并后的 main 上开工，开工前核对第 3 节清单与 main 一致，不一致先报告
- 不并入（排在 P4 之后）：FLW-2（BPMN 不用引擎计时器与到期的守护，P4 独占 `EngineWiring`）、K-3b-3（审批三个列表响应统一与 `:count`）；D1-7（交互事务语句超时）不在本卡，若先合并则 rebase

## 2. 依据
- ADR-0033（已采纳）：第 1 项第 1、2、9、10、11 条（UUIDv7 主键、`process_archive_skips` 原主键改命名唯一约束、聚合 ID 在构造时生成、不依赖 ID 顺序、类型化 ID 包 `UUID`）；第 3 项第 3 条（审批办理人、候选人用用户 ID）；第 6 项第 2 条（ID 对外为字符串）；第 7 项第 3 条（`outbound_deliveries.deduplication_key` 拆结构化列，「具体列在 R-1b 的清单中列出」——本卡即该清单）、第 5 条（`DocumentReference(DocumentType, DocumentId)`，`DocumentId` 包 `UUID`）；第 8 项（Flowable `idGenerator` 接 kernel `IdGenerator`；`approval_tasks` 以任务 ID 为 uuid 主键；子表以 `approval_request_id` 引用根；OpenAPI 与事件中任务、实例 ID 为 uuid 字符串）；回滚方案「Flowable `idGenerator` 单独出问题时」；守护 1（只减不增部分）、10 `FlowableIdTest`
- ADR-0039（已采纳）：第 5 条第 2 款与第 6 条（老待办契约中性化放生产后——决定本卡对 `InboxItemResponse.taskId` 的处理，第 12 节 D-6）；「需同步修改的守护规则」中 `DataArchitectureTest` 一条
- 审计：DATA_AUDIT DA-4、DA-6；DDL_AUDIT DDL-04（`start_rounds.instance_id` 无唯一、`approval_requests.current_instance_id` 改部分唯一索引）、DDL-08（待办任务与到期升级部分索引）（`~/.modernize/psa/audit/`）
- 计划：`phase1a/NEXT_BATCH_PLAN.md` 7.2（P4 行、末段独占构件「P4 独占 `EngineWiring`、`DocumentId`」）、7.11（P4 不依赖拆分或 DA-3）、第 8 节 FLW-1 结论（`FlowableEngineClock` 已在 main）、FLW-2、K-3b-3；拆分图 `~/.modernize/psa/handoff/one-1a-next-2/r1b-split-plan.md`（P4 行、第 3 节「P4：`FlowableIdTest`；聚合在构造时生成 ID（审批部分）」）
- 规则卡：RULE-386（审批的一切时刻取注入 Clock，含引擎；`rules/APPROVAL_DEADLINE_RULES.md`）；RULE-389、RULE-392（`rules/TIMESHEET_ROUTING_RULES.md`，在 PR #249）
- 用户原话：「改为自有 UUIDv7 + A8 ID 对照列 (Recommended)」「主键统一」「也加，自有表零例外 (Recommended)」（ADR-0033 背景）；ARCH-D5 D5-06「组织架构中该部门登记的部门负责人（随组织同步维护），节点与用语改称「部门负责人审批」」（intent `2026-10-15-222619-one-rules-工时审批路径决策页第二组-D5.md`，PR #249）；用语问题已登记 `glossary/issues/2026-10-15-222628-one-rules-区域负责人-regional_h.md`（PR #249）
- 卡：`phase1a/cards/R-1b.md`（§3 至 §9 逐子 PR 适用；文末 2026-10-15 修订 G-A1）；范本 `phase1a/cards/R-1b-P3b.md`
- 规范：SESSION_PLAYBOOK §5（开工前术语门禁、子代理模型）、§6.1、§12.2（基线开放期直接改基线，附预期表结构 diff）、§14、§14.1（D2、D7、D16、D18）；CODING_STANDARD CS-1.8、CS-1.9、CS-1.10、CS-1.11、CS-2.20、CS-2.21、CS-5.5、CS-5.6、CS-5.9；NAMING_STANDARD §2.4（`_id` 为 uuid、`_key` 为 text、`_number` 为序号；约束 `pk_`/`fk_`/`uk_`/`ck_`/`ix_`）、§2.4a 第 1 条（外键目标必须是主键）
- dev 基线开放期：在途流程实例随库重建（`runbooks/dev-db-rebuild.md`），**不做实例迁移**，`act_*` 中旧形态 ID 随库清空（one-coordinator 派卡说明）

## 3. 范围与卡上文件

盘点基线：P3b @ c70027d2（`git grep`）。数字为文件数，实施时以 P3b 合并后的 main 为准。

### 3.1 表与列（改开放基线，不追加迁移；uuid 一律由应用侧 `IdGenerator` 生成；Flowable 的任务、实例 ID 由第 3.2 节第 1 条的 `idGenerator` 生成）

`platform_approval`（`V20261014132000__platform_approval_baseline.sql`）：

| 表 | 最终形态 |
|---|---|
| `approval_requests` | `document_key text` → **`document_id uuid`**（改名与类型，D-1）；`current_instance_id` 改 `uuid`；`uq_approval_requests_document` 改为 `(process_key, document_id)`，**名称不变**（C10）；删 `ix_approval_requests_instance`，新增部分唯一索引 `uk_approval_requests_current_instance_id ON (current_instance_id) WHERE current_instance_id IS NOT NULL`（DDL-04） |
| `start_rounds` | 新增 `approval_request_id uuid NOT NULL` + `fk_start_rounds_approval_request_id` → `approval_requests (id)`；删 `process_key`、`document_key` 与 `fk_start_rounds_document_key`（D-2）；`uq_start_rounds_round` 改为 `(approval_request_id, round_number)`，名称不变；`instance_id` 改 `uuid` 并加 `uk_start_rounds_instance_id`（DDL-04） |
| `approval_tasks` | `task_id` 改 `uuid`（仍是主键，`pk_approval_tasks` 不变；值即 Flowable 任务 ID，ADR-0033 第 8 项第 2 条）；`instance_id` 改 `uuid`；新增 `approval_request_id uuid NOT NULL` + `fk_approval_tasks_approval_request_id`；删 `process_key`、`document_key`、`fk_approval_tasks_document_key`、`ix_approval_tasks_process_key_document_key`，新增 `ix_approval_tasks_approval_request_id`；DDL-08：新增 `ix_approval_tasks_open ON (created_at, task_id) WHERE status IN ('PENDING','CLAIMED')`（待办游标）与 `ix_approval_tasks_escalate_at ON (escalate_at) WHERE status IN ('PENDING','CLAIMED') AND overdue_at IS NOT NULL AND escalated_at IS NULL`（到期升级）；`ix_approval_tasks_due_at`、`ix_approval_tasks_remind_at`、`ix_approval_tasks_assignee_id`、`ix_approval_tasks_instance_id` 保留。两个新索引的列与谓词以 `EXPLAIN` 对 `openTasks`、`findDue` 的实际 SQL 为准（证据放 PR），名称按 §2.4 |
| `approval_task_records` | `task_id`（外键 `fk_approval_task_records_task_id` 不变）、`instance_id` 改 `uuid`；新增 `approval_request_id uuid NOT NULL` + `fk_approval_task_records_approval_request_id`；删 `process_key`、`document_key`、`fk_approval_task_records_document_key`、`ix_approval_task_records_process_key_document_key`；**办理记录序号**：新增 `record_number integer NOT NULL CHECK (record_number >= 1)`（列名待术语门禁，D-3）与 `uk_approval_task_records_record_number UNIQUE (approval_request_id, record_number)`（兼作外键索引）；`record_id` 注释去掉「按时间有序的 UUID，列表翻页的游标取它」的次序含义（游标仍可用「时刻 + 主键」，ADR-0033 第 1 项第 10 条）；只追加触发器不变 |
| `process_archive_skips` | 新增代理主键 `process_archive_skip_id uuid NOT NULL`（列名待术语门禁），`pk_process_archive_skips` 改为只含该列（名称不变）；`process_instance_id` 改 `uuid` 并加 `uk_process_archive_skips_process_instance_id`（ADR-0033 第 1 项第 2 条）；只追加触发器不变 |

`platform_integration`（`V20261014133000__platform_integration_baseline.sql`）：

| 表 | 最终形态 |
|---|---|
| `outbound_deliveries` | **删** `deduplication_key`（连同 `outbound_deliveries_deduplication_key_key`）与 `target_key`；**新增**（列名与取值待术语门禁，D-4）：`subject_kind text NOT NULL` + `ck_outbound_deliveries_subject_kind`（`APPROVAL_TASK` / `APPROVAL_INSTANCE`）、`subject_id uuid NOT NULL`、`purpose_kind text NOT NULL` + `ck_outbound_deliveries_purpose_kind`（`TASK_ASSIGNED` / `TASK_TRANSFERRED` / `TASK_REASSIGNED` / `TASK_OVERDUE` / `TASK_ESCALATED` / `APPROVAL_COMPLETED`）、`trigger_event_id uuid`（只在一张卡可因多次事件重发时有值：转交、改派）、`target_delivery_id uuid` + `fk_outbound_deliveries_target_delivery_id` → `outbound_deliveries (id)`；唯一：卡片 `uk_outbound_deliveries_card ON (subject_kind, subject_id, purpose_kind, trigger_event_id) NULLS NOT DISTINCT WHERE kind = 'CARD'`，已处理说明 `uk_outbound_deliveries_target_delivery_id`（每张卡至多一条说明）；CHECK `outbound_deliveries_handled_note_target` 改写为 `(kind = 'HANDLED_NOTE') = (target_delivery_id IS NOT NULL)`，**名称不变**；已处理说明行的对象列与用途列取被替换卡片的值。收件人不进去重键（D-4）|

列与表注释：去掉「引擎任务 ID」「引擎的实例标识」「如 approval-task:42」等引擎 / 文本键说法，改为自有 ID 写法；`approval_requests.document_id` 注释写「单据 ID：拥有该单据的业务模块的自有 ID」。数据访问注册表 `backend/architecture/data-access-registry.yaml` 中这几张表的 `references`、`business_key`、`fk_exceptions` 随列同步。

**约束名（C10）**：凡保留的约束、索引名称一律不变（含 `uq_approval_requests_document`、`uq_start_rounds_round`、`outbound_deliveries_handled_note_target` 等仍在 `ddl-naming-baseline.txt` 中的旧写法，不改成 `uk_` / `ck_`，否则越进 R-2a）。只有本卡新增的约束与索引按 §2.4 命名；本卡删除的列所带的约束随列删除，由第 8 节列明。

### 3.2 Java

1. **Flowable ID 由 kernel 生成**（ADR-0033 第 8 项第 1 条）：在 `EngineWiring`（`@Configuration`，P4 独占）内以 `@Bean EngineConfigurationConfigurer<SpringProcessEngineConfiguration>` 设置 `idGenerator`，取 kernel `IdGenerator.next()` 的标准小写文本；与 FLW-1 的 `FlowableEngineClock`（只 `setClock`）并存、不依赖执行顺序、不改它。实施前先核对 Flowable 8.0.0 spring-boot 自动配置现在用的 ID 生成器（预计是 `StrongUuidGenerator`）与是否按 `@Process` 限定取 bean，PR 写明核对结论（D-7）。流程定义 ID、部署 ID 等 `act_*` 其余 ID 同样走它，不另豁免。
2. **类型**：`kernel.DocumentId`（P4 独占）、`platform.approval.api.ApprovalTaskId`、`ApprovalInstanceId` 由 `String` 改包 `UUID`，校验写法同 `OwnObjectId`（`Uuids.requireKey`）；`toString()` 输出标准文本。凡 `rs.getString("task_id" / "instance_id" / "current_instance_id" / "document_key")`、`new ApprovalTaskId(String)`、`new DocumentId(String)` 的位置改为 UUID 读写；Flowable 返回的任务、实例 ID 文本按规范形态解析，不合格即失败并告警（引擎配置出错的信号），不回退、不跳过。
   - approval：`api` 2 个 ID 类与 `ApprovalProcesses`、`HookContext`；`application` 20 个（`Approvals`、`ClaimTask`、`DecideTasks`、`StartApproval`、`ListInbox`、`ReassignTask`、`TransferTask` 等）；`domain` 11 个（`ApprovalRequest`、`ApprovalTask`、`TaskRecord`、`TaskHandling`、`ApprovalTaskQueries` 等）；`events` 11 个；`infrastructure/flowable`（`FlowableApprovalEngine`、`TaskAssignmentListener`、`ProcessArchiveJob`、`EngineWiring`）；`infrastructure/persistence`（`JdbcApprovalRequestRepository`、`JdbcApprovalTaskQueries`）；`infrastructure/notification/TaskNotifier`；`web`（`ApprovalTaskController` 与响应记录）
   - 连带：`cost/timesheet/domain/Timesheet.java:216`（`new DocumentId(String.valueOf(timesheetId.value()))` 改为直接包工时单 UUID）；`platform/numbering/infrastructure/persistence/JdbcLegacyNumberRepository.java`、`platform/numbering/web/LegacyNumberController.java`（`document_id` 已是 uuid，只换类型）
3. **聚合 ID 在构造时生成**（ADR-0033 第 1 项第 9 条；拆分图 P4 验收）：`ApprovalRequest` 在 `begin` 时向 `IdGenerator` 取审批请求 ID（新类型化 ID，名称待术语门禁，D-14），仓储不再在 `insert` 时 `ids.next()`；`start_rounds.id` 同样在领域侧取得或由仓储取（D-14）。子表写入带 `approval_request_id`。事件信封 `aggregateId` 仍为「单据类型:单据 ID」文本，**不改**（P5 独占 `EventEnvelope`）。
4. **ID 来源统一经 `IdGenerator`**：`Approvals.openRound` 预定义实例 ID（`Uuids.timeOrdered(time.clock())`）与 `ApprovalRequest` 中 `TaskRecordId`（`Uuids.timeOrdered(now.at())`）改经注入的 `IdGenerator`（便于测试注入非递增 ID 证明不依赖 ID 次序）；`predefineProcessInstanceId` 写法保留。
5. **不依赖 ID 次序**（ADR-0033 第 1 项第 10 条，CS-2.21）：`JdbcApprovalRequestRepository.restore` 的 `ORDER BY task_id` 改为 `ORDER BY created_at, task_id`；`historyOf` 的 `ORDER BY r.decided_at, r.record_id` 改为按办理记录序号；`handledBy`（`decided_at, record_id` 游标）与 `openTasks`（`created_at, task_id` 游标，`task_id` 改 uuid 比较）保持「时刻 + 主键」。
6. **办理记录序号**：在审批请求保存的同一事务内（请求已加锁）按「该请求已有最大序号 + 1」逐条分配，同一次保存的多条按领域产生顺序；并发由请求锁与 `uk_approval_task_records_record_number` 兜底，冲突即 `KERNEL_CONCURRENT_CHANGE`，不重试改号。分配位置（聚合内计数还是仓储取最大值）由实现定，PR 写明（D-3）。
7. **`process_archive_skips`**：`ProcessArchiveJob` 写入时取 `process_archive_skip_id`；与 `act_hi_procinst.id_`（varchar）比较处显式转换，转换失败即告警；仍在引擎中的已跳过实例每日告警的现行为不变。
8. **外发投递去重键拆列**（ADR-0033 第 7 项第 3 条）：integration 的 `WecomCard` 的 `String deduplicationKey` 与 `WecomGateway.markHandled(String, …)` 的键改为结构化的投递对象（对象类别、对象 ID、用途、触发事件 ID；类型名待术语门禁），**不新增 api 方法**（集成层 api 12 个方法上限不变）；`OutboundDelivery`、`OutboundDeliveryRepository`（`findByKey` / `lockByKey` 改按结构化键）、`JdbcOutboundDeliveryRepository`（`ON CONFLICT` 改到 `uk_outbound_deliveries_card`）、`QueueWecomCard`、`MarkWecomCardHandled`（已处理说明按 `target_delivery_id`，去掉 `#handled` 后缀拼接）、`DeliverWecomCards`、`HttpWecomClient`、`WecomGatewayAdapter`、`WecomMessagePayload`；`TaskNotifier` 的 `dedupKey` 与 `"approval-instance:…:completed"`、`":transfer:" + eventId` 等拼接全部改为结构化键。企业微信消息的 `task_id` 改由投递 `id` 确定性导出（同一行重试不变，D-5）。日志里打印结构化键的规范文本，不打印收件人。
9. **改名：区域负责人审批 → 部门负责人审批**（D5-06；新英文名以 one-glossary 定稿为准，下称「〈节点名〉」「〈字段名〉」）：
   - `backend/src/main/resources/processes/cost_timesheet_approval.bpmn20.xml`：`userTask id="regionalHeadReview" name="区域负责人审批"` → `id="〈节点名〉" name="部门负责人审批"`；`sequenceFlow` `toRegionalHeadReview` / `fromRegionalHeadReview` 与条件变量 `routed_regionalHeadReview` 同步；`assignmentRule field="regionalHeadId"` → `field="〈字段名〉"`
   - `cost/timesheet/api/TimesheetApprovalField.REGIONAL_HEAD("regionalHeadId")` 改常量名与字段名
   - 回放：`platform/approval/infrastructure/replay/TimesheetBranches.java`、`LegacyVocabulary.java`（老系统词 `area_leader` 与「区域负责人审批」映射到新节点名、新字段名；老系统的词本身不改）
   - 测试资源：`backend/src/test/resources/{deadline3,groupnode,noskip,unpaired}/cost_timesheet_approval.bpmn20.xml`、`replay/synthetic-timesheet-export.json`；非验收测试 `NodeRoutingIT`、`TimesheetBranchesIT`、`TimesheetApproverReplayIT` 随改（只改名，不改断言含义）
   - 架构真相源 `docs/modernization/architecture/bpmn/processes.yaml` 同步
   - **只改名，不改取人语义**：1a 仍是 `FORM_FIELD_USERS`，由提交方给出人员；RULE-389「任务创建时从组织架构读取售前对象归属部门的负责人」随 Phase 2 工时映射卡（ARCH-D5 映射卡）实现（D-9）
   - 前端界面文字、`frontend/apps/{web,h5}/src/modules/approval/pages/task-detail-page.test.tsx` 中的节点名交 one-1a-par（不在本卡）；`terms.yaml` 的 `regional_head` / `regional_head_approval` 词条处理归 one-glossary（已登记的用语问题）
   - 在途实例：dev 随库重建，不迁移、不保留旧节点名兼容
10. **契约**（契约先行，CS-1.10，与代码同一提交）：
    - OpenAPI：路径参数 `{taskId}` 7 个操作（`GET /api/v1/platform/approval-tasks/{taskId}` 与 `:claim`、`:decide`、`:reassign`、`:release`、`:return`、`:transfer`）改 `type: string, format: uuid`；属性 `DecisionResponse.instanceId`、`HandledTaskResponse.{taskId, instanceId, documentId}`、`HistoryEntry.taskId`、`TaskDetailResponse.{taskId, instanceId, documentId}`、`TaskOverviewResponse.{taskId, documentId}`、`InboxItemResponse.documentId`（可空）声明 `format: uuid`；`InboxItemResponse.taskId` 按 D-6
    - 非 uuid 的 `{taskId}` 路径参数返回 400（`CanonicalUuids` 现有写法），不查库、不 404
    - `OpenApiExportTest`：上列字段移入 `OWN_ID_FIELDS`；`OTHER_ID_NAMES` 按 D-6 收窄（守护文件改动，PR 单列）
    - 事件目录：`events/approval.yaml`（32 处）、`timesheet.yaml`（5 处）中 `x-type` 为 `ApprovalTaskId` / `ApprovalInstanceId` / `DocumentId` 的字段加 `format: uuid`，描述中「稳定 ID 的字符串形式」「如 TIMESHEET:7」等改为 uuid 写法（`aggregateId` 描述本身随 P5）；重新生成 `asyncapi.yaml`
    - 前端（G-A1）：重新生成 `frontend/packages/api`（`openapi.json`、`validators.ts`，`taskId` 等为 uuid 字符串）；`apps/{web,h5}/src/modules/approval` 中把这些 ID 当作非 uuid 文本或数字的地方随改（只改类型与校验，不改界面文字）

### 3.3 允许改动的文件
- 开放基线：`backend/src/main/resources/db/migration/platform_approval/V20261014132000__platform_approval_baseline.sql`；`platform_integration/V20261014133000__platform_integration_baseline.sql`（只限 `outbound_deliveries`）
- `backend/src/main/java/com/forcitis/one/`：`kernel/DocumentId.java`；`platform/approval/**`；`platform/integration/{api/WecomCard.java, api/WecomGateway.java, application/{QueueWecomCard,MarkWecomCardHandled,DeliverWecomCards}.java, domain/{OutboundDelivery,OutboundDeliveryRepository}.java, infrastructure/persistence/JdbcOutboundDeliveryRepository.java, infrastructure/wecom/**}`；`cost/timesheet/domain/Timesheet.java`、`cost/timesheet/api/TimesheetApprovalField.java`；`platform/numbering/infrastructure/persistence/JdbcLegacyNumberRepository.java`、`platform/numbering/web/LegacyNumberController.java`；第 3.6 节两处
- `backend/src/main/resources/processes/cost_timesheet_approval.bpmn20.xml`
- 测试：受影响模块的单元与集成测试（非验收目录）及测试资源（第 3.2 节第 9 条所列）；`OpenApiExportTest`；第 3.6 节两个测试
- `backend/architecture/`：`primary-key-type-baseline.txt`（删行）、`ddl-naming-baseline.txt`（只删第 8 节所列行）、`data-access-registry.yaml`
- `docs/modernization/events/{approval,timesheet}.yaml` 与 `asyncapi.yaml`；`docs/modernization/architecture/bpmn/processes.yaml`；`frontend/packages/api/**` 生成物；`frontend/apps/{web,h5}/src/modules/approval/**`（只限 ID 类型与校验）
- 越界先停下报告，不先做后报

### 3.4 归属
- 本卡独占：`EngineWiring`、kernel `DocumentId`（7.2 子 PR 独占构件）；`platform_approval` 五张表与 `outbound_deliveries` 上述列；`ApprovalTaskId`、`ApprovalInstanceId` 与新增的审批请求 ID 类型
- 共享构件（需改时停下报告）：`EventEnvelope`、`AggregateReference`（P5）；`FlowableEngineClock`（FLW-1，one-1a-par，不改）；kernel 中 `DocumentId` 以外的类型；`OrgUnitId`——F-1（one-1a-par，P5 后开）将把它下沉 kernel，**P4 不迁 `OrgUnitId`、不改其形状**，`AssigneeResolver` 与回放的 `ExportedIds`、`ExportedSnapshot`、`ReplayExport` 引用 `OrgUnitId` 处 P4 若改动，冲突只在 import，合并时以后合者解决
- 守护文件：P5 独占全部守护文件。本卡对守护文件的改动（第 8 节列表）是 P5 独占前的例外，PR 单列

### 3.5 不在 P4（越界即停）
- 事件信封 `aggregateId` / `AggregateReference` 改 UUID、`audit_entries.subject_key` 拆列（审计里 `"approval-task:" + taskId` 文本仍照旧）、`OTHER_ID_NAMES` 清空、`PrimaryKeyTypeGuardTest` 扩到复合 / text 主键、`primary-key-exemptions.txt`、SQL `ORDER BY` 守护：P5
- 收件人改为 `List<UserId>`、`WecomUserId` 移出 kernel：DA-10（DA-3 之后）
- 审批三个列表响应统一、收件箱计数 `:count`、删 `PLATFORM_APPROVAL_PAGE_INVALID`：K-3b-3
- BPMN 不含计时器与 `flowable:dueDate` 的守护：FLW-2
- 交互事务 `statement_timeout`：D1-7
- 老待办契约中性化（`InboxSource.LEGACY`、`legacyCount`、老系统任务键）：ADR-0039 第 6 条，生产后
- RULE-389 的取人语义（按售前对象归属部门取负责人）、RULE-391 旗标检查：Phase 2 工时映射卡
- 改名（R-2a，含 `uq_` → `uk_`、`ddl-naming-baseline.txt` 其他行）、时钟默认值（R-4）

### 3.6 随带修复（P3b 终审 Low，one-coordinator 派）

均为 `platform.integration` 的守护或测试；**守护文件改动单列，P5 独占前的例外**。

1. `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/legacy/LegacyReadRegisterTest.java`（`STATEMENT` 正则，P3b @ c70027d2 第 23 行）：WHERE 段不再只取第一个 `(\w+)\s*=`，改为逐个取出全部 `(\w+)\s*=` 过滤列与 `ORDER BY` 列，一并与登记核对；加负向样例：语句多一个未登记的过滤列时报出
2. 同文件 `TODO_VIEW`（第 21 行字面量 `"dbo.v_psa_one_legacy_todo"`）改为引用 `LegacyInboxProperties.DEFAULT_VIEW`（不另写字面量）
3. `backend/src/main/java/com/forcitis/one/platform/integration/infrastructure/legacy/LegacyInboxConfiguration.java:69`：完整类名 `org.springframework.context.annotation.Condition` 改为 import
4. `backend/src/test/java/com/forcitis/one/platform/integration/guard/SourceReferenceAllocationGuardTest.java`：新增规则「`AllocateOwnId` 只由 `SourceReferences` 调用」，带注入负向样例与反例（CS-5.9）

## 4. 验收测试（由测试代理先写，§6.1 第 8 条）
- 测试代理：`code-modernization:test-engineer`（**sonnet**），先于实现提交，提交说明带 `Acceptance-Test-Author: test-engineer`；术语门禁未定稿的名字先按第 11 节推荐名写，定稿后改名再交实现
- 路径：`backend/src/test/java/com/forcitis/one/acceptance/platform/approval/`、`…/acceptance/platform/integration/`、`…/acceptance/cost/timesheet/`；契约类放 `acceptance/` 根（同 `UserIdContractTest`、`EmployeeIdContractTest`）
- 测试名（`@DisplayName` 以 `R-1b P4` 与依据编号开头）：
  1. `R-1b P4 ADR-0033 守护 10 FlowableIdTest：新启动的流程实例与其任务的 ID 都是 version 7 的 uuid；固定注入 Clock 时 ID 的前 48 位等于该时刻毫秒（RULE-386）`
  2. `R-1b P4 ADR-0033 第 8 项第 2 条（新列断言，写法同 UserIdColumnsIT）`：第 3.1 节各列为 `uuid`；`approval_tasks`、`process_archive_skips` 主键为单列 uuid；三张子表有 `approval_request_id` 外键到 `approval_requests.id`、不再有 `process_key` / `document_key`；DDL-04 两条唯一与 DDL-08 两条部分索引存在；`outbound_deliveries` 不再有 `deduplication_key`、`target_key`
  3. `R-1b P4 DDL-04：同一流程实例 ID 写入第二条发起轮次被唯一约束拒绝；两个审批请求不能指向同一当前实例；当前实例为空的请求可有多条`
  4. `R-1b P4 办理记录序号：同一审批请求的办理记录序号从 1 起连续，历史按序号排列`——注入**递减**的 `IdGenerator`（记录 ID 次序与写入次序相反）、固定 Clock（同一时刻多条），历史顺序仍等于办理顺序；同一请求重复序号被唯一约束拒绝
  5. `R-1b P4 ADR-0033 第 1 项第 10 条：审批请求恢复时任务按创建时刻排列，不按任务 ID`（同样用递减 ID）
  6. `R-1b P4 ADR-0033 第 1 项第 9 条：审批请求的 ID 在构造时取得，仓储不生成 ID`（注入 `IdGenerator` 的第一个值即审批请求 ID；子表 `approval_request_id` 等于它）
  7. `R-1b P4 ADR-0033 第 1 项第 2 条：已归档但引擎删除失败的流程实例只记一次跳过，之后不再重复归档`（`process_instance_id` 为 uuid；同一实例第二次写入被唯一约束拒绝）
  8. `R-1b P4 ADR-0033 第 7 项第 3 条：外发投递按对象、用途去重`——同一任务的指派卡重复发送只一行；同一任务两次转交（不同事件）各一行；已处理说明每张卡至多一条且指向原卡的投递 ID；同一行重试时企业微信 `task_id` 不变
  9. `R-1b P4 G-A1 / ADR-0033 第 6 项第 2 条`（新 `ApprovalIdContractTest`）：OpenAPI 中 `{taskId}` 路径参数（7 个操作）与第 3.2 节第 10 条所列属性为 `type: string, format: uuid`；`OTHER_ID_NAMES` 只剩 D-6 定下的例外；事件目录中 `x-type` 为 `ApprovalTaskId` / `ApprovalInstanceId` / `DocumentId` 的字段声明 `format: uuid`；生成的 `validators.ts` 中这些字段不是数字、带 uuid 校验
  10. `R-1b P4 G-A1：非 uuid 的任务 ID 路径参数返回 400，不查库`
  11. `R-1b P4 D5-06 / RULE-389：售前工时节点名为「部门负责人审批」，按〈字段名〉取审批人`——提交带 `preSalesHours` 旗标与〈字段名〉人员时，该节点为该人建任务，节点名称为「部门负责人审批」；流程定义中不再出现 `regionalHeadReview`、`regionalHeadId`
  12. `R-1b P4 D5-06：表单只带旧字段名 regionalHeadId 时……`——**按 D-10 裁定后再写**（推荐：发起即失败并告警，不静默跳过该节点）
  13. ★ `R-1b P4 ADR-0033 第 3 项第 3 条：审批「本人」闸门与取人在任务 ID 为 UUID 时不变`——`ApprovalAccountsIT`、`RetractionInitiatorAccountsIT`、`AssigneeInputUuidIT` 原样重跑，断言不变
- **要按 Acceptance-Ruling 改的现有验收物**（测试代理改，实现代理不碰）：
  - `RULING-P4-1`：`acceptance/platform/approval/{ApprovalAccountsIT, AssigneeInputUuidIT, RetractionInitiatorAccountsIT}.java` 中 `new DocumentId("acceptance-…")` 改为 UUID，读 `task_id` 的 SQL 按 uuid 读，只改 ID 的构造与类型，不改断言
  - `acceptance/platform/integration/*` 中 `dbo.todo_rows` / 老待办视图的 `task_id nvarchar(64)` 是外部系统形态，**不改**
- 实现方认为验收测试有误：停下报告，由复审裁定（`Acceptance-Ruling: RULING-P4-n`），不自行修改

## 5. 执行者与模型
- 测试代理：`code-modernization:test-engineer`，**sonnet**（本卡不改「本人」判定一侧，只重跑既有反向测试）
- 实现：子代理，**sonnet**；按模块分提交，建议顺序：① 基线 DDL 与 `DocumentId`、`ApprovalTaskId`、`ApprovalInstanceId`、审批请求 ID；② approval（引擎 ID 生成器、仓储与查询、聚合构造时取 ID、办理记录序号、归档跳过）；③ integration 外发投递与 `TaskNotifier`；④ 改名（BPMN、timesheet 字段、回放、测试资源、`processes.yaml`）；⑤ 契约（OpenAPI、事件 yaml、前端生成物）与守护文件；⑥ 第 3.6 节随带修复。中间提交尽量可编译，不能时在提交说明注明；PR 末提交全绿
- 工作区：独立 worktree（`Agent` 的 `isolation: worktree`，计划 7.9），分支 `claude/one-1a-next-4-r1b-p4`（以负责会话命名为准），基线为 **P3b 合并后的 main**；开工前确认 P3b 已合并，否则停下报告
- 首轮复审：**opus 一次**，推理强度 high（§14、§14.1 全部条目 + 本卡专查项）
- 修后复核：**sonnet**（Blocker / High 的修复仍须 opus 复核，见第 9 节）
- 不交 Codex：本卡含引擎 ID 生成与表结构形状，判断类改动多

## 6. 禁止事项
- 不改卡外文件；不顺手重构、改名或统一风格；保留的约束、索引名字一律不改（C10）
- 不改业务语义：审批流转、会签、自动通过、时限、改派 / 转交、撤回、通知文案与收件人判定照旧；改名只改节点与字段的名字和显示名，不改取人方式
- 不依赖 ID 次序（排序与游标按时刻列 + 主键或显式序号，CS-2.21）；生产代码除 `kernel.Uuids` 外不调用 `UUID.randomUUID`
- 不改 `EventEnvelope`、`AggregateReference`、`FlowableEngineClock`；不迁、不改 `OrgUnitId`
- 不为兼容旧形态 ID 或旧节点名留回退分支（dev 随库重建）
- 不放宽、不删除断言；不改验收测试目录
- Testcontainers 临时容器（镜像限 `backend/architecture/test-images.txt`）可直接跑；其他容器、常驻容器、对外调用（含企业微信）、生产取证先问；不建账号
- 不打印、不写入密钥；测试密钥运行时生成；日志不打印收件人

## 7. 预期表结构 diff（SESSION_PLAYBOOK §12.2）
- 方法：两个空库分别执行改动前（P3b 合并后的 main）、后的基线，`pg_dump --schema-only`（带 / 不带权限）对比，种子另做 `--data-only` 对比；`act_*` 由引擎自建，不在对比范围
- 预期差异只能是：
  - **类型 `text` → `uuid`**：`approval_requests.current_instance_id`；`start_rounds.instance_id`；`approval_tasks.{task_id, instance_id}`；`approval_task_records.{task_id, instance_id}`；`process_archive_skips.process_instance_id`
  - **改名并改类型**：`approval_requests.document_key text` → `document_id uuid`（D-1）
  - **新增列**：`start_rounds.approval_request_id`、`approval_tasks.approval_request_id`、`approval_task_records.approval_request_id`（均 `uuid NOT NULL`）；`approval_task_records.record_number integer NOT NULL`；`process_archive_skips.process_archive_skip_id uuid NOT NULL`；`outbound_deliveries.{subject_kind text NOT NULL, subject_id uuid NOT NULL, purpose_kind text NOT NULL, trigger_event_id uuid, target_delivery_id uuid}`（列名以术语门禁为准）
  - **删除列**：`start_rounds.{process_key, document_key}`、`approval_tasks.{process_key, document_key}`、`approval_task_records.{process_key, document_key}`、`outbound_deliveries.{deduplication_key, target_key}`
  - **约束**：`pk_process_archive_skips` 列改为 `process_archive_skip_id`（名称不变）；`uq_approval_requests_document` 列改为 `(process_key, document_id)`、`uq_start_rounds_round` 列改为 `(approval_request_id, round_number)`、`outbound_deliveries_handled_note_target` 表达式改为 `target_delivery_id`（三者名称不变）；删 `fk_start_rounds_document_key`、`fk_approval_tasks_document_key`、`fk_approval_task_records_document_key`、`outbound_deliveries_deduplication_key_key`；新增 `fk_start_rounds_approval_request_id`、`fk_approval_tasks_approval_request_id`、`fk_approval_task_records_approval_request_id`、`fk_outbound_deliveries_target_delivery_id`、`uk_start_rounds_instance_id`、`uk_approval_task_records_record_number`、`uk_process_archive_skips_process_instance_id`、`ck_approval_task_records_*`（序号 ≥ 1，行内或具名以 `DdlNamingTest` 为准）、`ck_outbound_deliveries_subject_kind`、`ck_outbound_deliveries_purpose_kind`
  - **索引**：删 `ix_approval_requests_instance`、`ix_approval_tasks_process_key_document_key`、`ix_approval_task_records_process_key_document_key`；新增 `uk_approval_requests_current_instance_id`（部分唯一）、`ix_approval_tasks_approval_request_id`、`ix_approval_tasks_open`、`ix_approval_tasks_escalate_at`、`uk_outbound_deliveries_card`（部分唯一，`NULLS NOT DISTINCT`）、`uk_outbound_deliveries_target_delivery_id`
  - **注释**：第 3.1 节所列列与表注释
- 不得出现的差异：五张审批表与 `outbound_deliveries` 以外任何表的变化；保留约束、索引、触发器的改名或删除（上列明示者除外）；默认值、可空性（新增列除外）、已有 CHECK 取值的变化；`approval_requests.id`、`start_rounds.id`、`approval_task_records.record_id`、`outbound_deliveries.id` 的类型与主键变化；种子数据变化

## 8. 交付物与自查
- 交付物：基线 DDL；Java（类型、引擎 ID 生成器、聚合构造时取 ID、子表挂根、办理记录序号、归档跳过、投递去重键、改名）；单元与集成测试；OpenAPI、事件目录、前端生成物；`processes.yaml`；第 3.6 节随带修复
- **守护文件变动（PR 单列，P5 独占前的例外）**：
  - `primary-key-type-baseline.txt` 删 `platform_approval.approval_tasks`、`platform_approval.process_archive_skips` 两行（P3b @ c70027d2 第 6、7 行）；文件头注释不改
  - `ddl-naming-baseline.txt` 只删 `constraint platform_integration.outbound_deliveries.outbound_deliveries_deduplication_key_key`（第 58 行，约束随列删除）；其余行不动（C10）
  - `OpenApiExportTest`：`OWN_ID_FIELDS` 加入第 3.2 节第 10 条字段；`OTHER_ID_NAMES` 按 D-6 收窄
  - `data-access-registry.yaml` 随列同步
  - 第 3.6 节：`LegacyReadRegisterTest`、`SourceReferenceAllocationGuardTest`（新规则带负向样例）
- 自查：`scripts/precheck.sh <基线>` 全部 PASS 或有理由的 SKIP，输出贴进报告与 PR「precheck.sh 输出」；`pre-commit install`；报告前 gitleaks 8.30.1 扫全历史
- 受影响模块测试：precheck 给出的命令；交付前全量 `scripts/ci/backend_verify.sh` 与前端 `pnpm verify`
- PR 正文单列：预期表结构 diff 与实际 diff；`EXPLAIN` 证据（DDL-08）；Flowable 自动配置核对结论（D-7）；改动的断言 / 测试及理由（含 RULING-P4-n 与改名带来的测试资源改动）；守护文件变动；第 12 节各点的落法；「前端界面文字交 one-1a-par」
- 报告分开写「已验证」与「未运行」

## 9. 复审要求（§6.1 第 4、11 条）
- 首轮：opus 一次（high）；Blocker / High 修后由 opus 复核，合并前修完
- Medium / Low：执行者补测试、CI 通过即关闭，修后复核用 sonnet，负责会话抽查；不阻塞合并的登记进下一批
- 复审清单：SESSION_PLAYBOOK §14、§14.1（D2 键与约束、D7 引用、D16 自有标识、D18 DDL 原则）；CODING_STANDARD CS-1.11、CS-2.20、CS-2.21、CS-5.5、CS-5.6、CS-5.9
- 专查：表结构 diff 只含第 7 节声明项、保留约束名无一改动；Flowable 新建的任务与实例 ID 全为 v7 且取注入 Clock；没有任何排序只按主键或依赖 ID 次序；子表不再以文本键挂根、外键目标都是主键；投递去重在并发重复投递下只得一行、已处理说明不重复；OpenAPI 与事件中任务、实例、单据 ID 无非 uuid 形态（D-6 例外除外）；改名后 BPMN、字段、回放、`processes.yaml` 一致且无旧名残留（老系统词表除外）；`OrgUnitId`、`EventEnvelope`、`FlowableEngineClock` 未动

## 10. 报告去向
- 完成、部分完成或阻塞，都向负责会话 one-1a-next-4，抄 one-coordinator（SESSION_PLAYBOOK §4 第 7 步）
- PR／卡／交接记录（消息按 TEAM-53 四节点及需动作事件送达，不另报号）：分支、改动文件（按模块）、验证结果（已验证 / 未运行）、precheck 输出、预期与实际表结构 diff、未完成项、需要用户决定的事；合并后通知 P5 执行者、FLW-2 与 K-3b-3 的执行者、one-1a-par（前端界面文字改名、F-1）在此基础上开工

## 11. 本卡新词（开工前术语门禁，SESSION_PLAYBOOK §5）

查过：`docs/modernization/glossary/terms.yaml`（`approval_task`、`approval_instance`、`workflow_instance`、`document`、`org_unit_head`「部门负责人」、`regional_head`、`regional_head_approval`、`project_manager_approval`、`sequence_number`）与 `backend/glossary/proposed/*.yaml`（`platform-approval.yaml` 的 `approval_request`、`platform-integration.yaml` 的 `outbound_delivery`、`round3-pending-renames.yaml` 的 `round3_pending__regional_head_review`），中英文都查重。

**业务用语**：

| 中文 | 标识 | 说明 | 状态 |
|---|---|---|---|
| 部门负责人审批 | BPMN 节点 id 与英文名（候选 `orgUnitHeadApproval` / `OrgUnitHeadApproval`，snake `org_unit_head_approval`；与兄弟节点 `projectManagerReview`、`supervisorReview` 的 Review 后缀是否一致随 R3 改名卡 R3-AP-68/69 一并定） | 取代 `regional_head_approval`「区域负责人审批」（D5-06）；「部门负责人」复用已批准 `org_unit_head` | **待 one-glossary 定稿** |
| （表单字段） | 售前工时审批人表单字段名（候选 `orgUnitHeadId`），取代 `regionalHeadId` | 1a 仍装人员 ID（D-9） | **待 one-glossary 定稿** |
| 区域负责人 | `regional_head`、`regional_head_approval` | 词条改定义、退役或保留给「区域」范围维度，由 one-glossary 按已登记的用语问题处理，本卡不动词表 | 交 one-glossary |
| 办理记录序号 | `record_number`（列）| 同一审批请求内办理记录的递增序号，历史按它排列；`_number` 为序号（NAMING §2.4） | 待 one-glossary |
| 审批请求 ID | `approval_request_id`（列）/ 类型名候选 `ApprovalRequestId` | 审批请求聚合的自有 ID；「审批请求」`approval_request` 为已提议术语 | 待 one-glossary |
| 投递对象类别 / 投递对象 ID | `subject_kind`、`subject_id`（`outbound_deliveries`）；取值 `APPROVAL_TASK`、`APPROVAL_INSTANCE` | 与 P5 `audit_entries.subject_kind` 同名，须同义（「对象类别 + 对象 ID」） | 待 one-glossary |
| 投递用途 | `purpose_kind`；取值 `TASK_ASSIGNED`、`TASK_TRANSFERRED`、`TASK_REASSIGNED`、`TASK_OVERDUE`、`TASK_ESCALATED`、`APPROVAL_COMPLETED` | 一张卡为什么发 | 待 one-glossary |

**技术标识（交 one-glossary 预判；纯技术词由它直接定）**：

| 中文 | 标识 | 说明 | 状态 |
|---|---|---|---|
| 触发事件 ID | `outbound_deliveries.trigger_event_id` | 与已有 `start_rounds.trigger_event_id` 同名同义 | 待预判（预计复用） |
| 被替换的投递 ID | `outbound_deliveries.target_delivery_id` | 取代 `target_key` | 待预判 |
| 归档跳过记录 ID | `process_archive_skips.process_archive_skip_id` | 代理主键，按「`<表单数>_id`」 | 待预判 |
| 单据 ID | `approval_requests.document_id` | 取代 `document_key`（D-1）；`DocumentId` 类型已有 | 待预判 |
| （类型名） | integration api 中结构化投递键的记录类型名（候选 `DeliverySubject`） | 只在集成层 api | 待预判 |
| （约束 / 索引名） | `uk_outbound_deliveries_card`、`ix_approval_tasks_open`、`ix_approval_tasks_escalate_at` 等 | 按 §2.4，以 `DdlNamingTest` 为准 | 实现时定 |

负责会话把上表交 one-glossary；**定稿前不派实现代理**（测试代理可先按候选名写，定稿后改名再交实现）。

## 12. 需要负责会话决定的点

| # | 问题 | 建议 |
|---|---|---|
| D-1 | `approval_requests.document_key text` 存的是业务模块的自有 UUID，按 NAMING §2.4 `_key` 只用于 text 业务键 | 改名并改类型为 `document_id uuid`；`uq_approval_requests_document` 名称不变（C10） |
| D-2 | 子表挂 `approval_request_id` 后，`process_key`、`document_key` 留不留作冗余 | 删除（外键目标为主键，G-DDL1；ADR-0033 第 8 项第 2 条「不再以文本挂根」）；`historyOf`、已办列表经 `approval_requests` 连接取流程标识与单据 |
| D-3 | 「办理记录序号」的范围、列名、分配位置 | 按审批请求递增（跨轮次连续，轮次另有 `round_number`）；列名 `record_number`（待门禁）；在请求加锁的保存事务内分配，冲突按并发修改拒绝；聚合内计数或仓储取最大值由实现定 |
| D-4 | ADR-0033 第 7 项第 3 条写去重键含「收件人 `user_id`」，而现行一张卡多个收件人（`recipient_codes text[]`），且收件人现是企业微信 userid（DA-10 才改为 `UserId`） | 本卡不把收件人放进去重键（粒度仍为一张卡一行），ADR 原文「具体列在 R-1b 的清单中列出」即由本卡定；收件人列由 DA-10 补入唯一约束（必做，首次部署前，迁移方案第 5 条）。已处理说明用 `target_delivery_id` 外键 + 唯一，取代 `#handled` 后缀；对象类别取值由集成层 api 的枚举持有（取值是中性的「审批任务 / 审批实例」，不出现外部系统名）。此条偏离 ADR 字面，**需 one-coordinator 认可** |
| D-5 | 企业微信消息 `task_id` 现由去重键文本的 SHA-256 导出 | 改由投递行 `id` 导出（同一行重试不变，说明行与原卡各自独立）；确认企业微信侧对 `task_id` 的格式与长度限制不变 |
| D-6 | `InboxItemResponse.taskId` 在老待办条目里装的是老系统任务键（非 uuid），不能声明 `format: uuid`；P5 计划清空 `OTHER_ID_NAMES` | 不拆字段（契约中性化随 ADR-0039 第 6 条放生产后）：`OTHER_ID_NAMES` 收窄为只按全名放行 `InboxItemResponse.taskId` 一项并写理由与到期条件；P5 清空时保留这一项，到期随中性化删除。备选：本卡拆为 `taskId`（uuid，仅新待办）+ 老系统任务键字段（改契约、加新词、前端改，越出本卡） |
| D-7 | `idGenerator` 放哪里：`EngineWiring` 现为 `ApplicationReadyEvent` 监听器，此时引擎已建好，设置无效 | 在 `EngineWiring`（`@Configuration`）内加一个 `EngineConfigurationConfigurer` `@Bean`，保持 P4 只改独占文件；不改 `FlowableEngineClock`。若核实 Flowable 自动配置另有 `@Process` 限定的 `IdGenerator` bean 写法更稳妥，则用 bean 写法并在 PR 写明 |
| D-8 | 改名范围：除 BPMN、`TimesheetApprovalField`、回放、测试资源外，`process/1a/TIMESHEET审批.md`、`REIMAGINED_ARCHITECTURE.md`、`MODERNIZATION_BRIEF*`、`TIMESHEET_REPLAY.md`、C-6 剧本脚本 `c6_relay.py` 也有「区域负责人」或 `regionalHead*` | 本卡只改代码、BPMN、`architecture/bpmn/processes.yaml`（BPMN 真相源）与测试；流程梳理与规格文档由 one-rules / one-glossary 随用语定稿改；C-6 剧本脚本属 one-ops，经 one-coordinator 转 |
| D-9 | RULE-389 说审批人在任务创建时从组织架构读取「售前对象归属部门」的负责人；1a 仍由提交方经表单字段给人员 | 本卡只改名不改取人语义（CS-1.8）；取人语义随 Phase 2 工时映射卡。字段名按「装的是部门负责人这个人」命名；若映射卡改为传部门、由引擎读负责人，届时字段再改名（单列在映射卡） |
| D-10 | 改名后提交方若仍传旧字段 `regionalHeadId`：节点 `skipWhenEmpty="true"` 会把售前节点静默跳过 | 发起时对表单字段名做登记核对，未登记的字段名使发起失败并告警（CLAUDE.md 第 6 条）；若现有代码已核对则只补验收第 12 条。负责会话裁定后测试代理再写第 12 条 |
| D-11 | `process_archive_skips.process_instance_id` 与其他表的 `instance_id` 不同名 | 保留原列名（只改类型），不顺手统一（R-2a 再议） |
| D-12 | P5 独占全部守护文件，本卡须改 `primary-key-type-baseline.txt`、`ddl-naming-baseline.txt`（1 行）、`OpenApiExportTest`、`data-access-registry.yaml` 与第 3.6 节两处测试 | 按 P3b 先例作为「P5 独占前的例外」逐项单列在 PR，先告知 P5 执行者，避免与 P5 在途改动冲突 |
| D-13 | 执行模型：计划 7.2 P4 行为「sonnet / opus（high）」，拆分图写测试 sonnet；本卡按 one-coordinator 派卡说明为测试 sonnet、实现 sonnet、首轮复审 opus 一次、修后复核 sonnet | 以本卡为准；体量（approval 约 60 个 main 文件、integration 10 个、测试约 40 个）若实现中途超出 sonnet 能力，停下报告改派 opus，不自行降低范围 |
| D-14 | 审批请求聚合目前 `aggregateId()` 为「单据类型:单据 ID」文本、表主键由仓储 `ids.next()` 生成；ADR-0033 第 1 项第 9 条要求构造时生成 | 新增类型化审批请求 ID（名称待门禁），`begin` 时取得；`aggregateId()` 文本暂不改（信封随 P5）；`start_rounds.id` 由仓储取亦可（发起轮次不是聚合），PR 写明 |
| D-15 | Flowable 回滚预案：`idGenerator` 若在引擎内部出问题 | 按 ADR-0033 回滚方案退到 `StrongUuidGenerator`（产出 v1 uuid，表结构不变），`FlowableIdTest` 同一提交改为接受 v1 并注明原因，报负责会话；不退回顺序数字 ID |

## 13. 负责会话裁定（one-1a-next-4，2026-10-15）
- RULING-P4-D1..D3、D5..D9、D11..D15：按第 12 节建议采纳。
- RULING-P4-D4（one-architecture 判定为 ADR-0033 允许的分阶段落地，2026-10-15）：收件人 `user_id` 到 DA-10 才存在（ADR-0033 第 4 项第 4、5 条），P4 去重键先不含收件人；**收件人列由 DA-10 补入唯一约束（必做，首次部署前，迁移方案第 5 条）**。`target_delivery_id` 取代 `#handled` 后缀符合第 2 项第 10 条，`trigger_event_id` 为追加的结构化列，均不构成偏离。
  - DA-10 时点：随 DA-3 → DA-10，按计划 7.11 在 F-1 之后、首次生产部署前（基线关闭前）。
  - DA-10 卡验收（基线开放时直接改基线）：删 `recipient_codes`，加 `recipient_user_id uuid NOT NULL`；`uk_outbound_deliveries_card` 改为 `(subject_kind, subject_id, purpose_kind, trigger_event_id, recipient_user_id) NULLS NOT DISTINCT WHERE kind = 'CARD'`，名称不变；D-5 一人一行后 `task_id` 按收件人各一张卡，已处理替换逐张进行；守护断言唯一约束含 `recipient_user_id`。
  - 须交用户的情形：DA-10 不改为一人一行，或拖过基线关闭。
- RULING-P4-D10：发起时核对表单字段名，未登记即发起失败并 ALERT（CLAUDE.md 第 6 条）；测试代理据此写验收第 12 条。
- RULING-P4-D6：`OTHER_ID_NAMES` 收窄为只按全名放行 `InboxItemResponse.taskId`，写理由与到期条件（随 ADR-0039 第 6 条契约中性化），P5 清空时保留此项。
- 开工条件：第 11 节新词经 one-glossary 定稿（部门负责人审批节点与字段名须用户确认的走确认页）；D-4 已判定；P3b（#260）合并后 rebase。
- 新词定稿（one-glossary，2026-10-15）：节点 `OrgUnitHeadApproval` / id `orgUnitHeadApproval`（后缀 Approval；兄弟节点 Review → Approval 随 R3 改名卡，不在本卡）；表单字段 `orgUnitHeadId`；`record_number`（列注释以「办理记录序号」开头，写明同一审批请求内递增、与单据编号流水号无关）；`approval_request_id` / `ApprovalRequestId`；`subject_kind` / `subject_id`（注释「对象类别 / 对象 ID：被记录或被投递的业务对象的类别与自有 ID」，与 P5 `audit_entries` 同句）；`purpose_kind` 取值六个照候选，实现 PR 登记提议术语 `delivery_purpose` 投递用途 / DeliveryPurpose（kind state，bc platform.integration，放 `platform-integration.yaml`，由 one-glossary 放确认页，实现不等）；`trigger_event_id`（注释「触发事件 ID」）、`target_delivery_id`、`process_archive_skip_id`、`document_id`（类型 `DocumentId`）、`DeliverySubject`（只在集成层 api）。代码中 `RegionalHeadApproval`、`regionalHeadId` 等旧名本卡一并改；词表中 `regional_head*` 由 one-glossary 写回，本卡不动。

## 14. 验收阶段裁定（one-1a-next-4，2026-10-15）
- RULING-P4-1：既有三个审批验收（`ApprovalAccountsIT`、`AssigneeInputUuidIT`、`RetractionInitiatorAccountsIT`）改用文档 ID 辅助方法与按文档读行，使 P4 前后都能跑，断言不变。
- RULING-P4-2（第 9 条 validators.ts）：现有生成器对 uuid 属性只出 `z.string()`（P3b 复审 L12，生成器局限），本卡不改生成器；验收只断言 ID 不是数字（`z.string()`），不要求 `uuid` 校验。
- RULING-P4-3（第 6 条）：断言审批请求 ID 出自注入的 `IdGenerator`，且产生它的调用栈在 approval 领域或应用层、不在 `infrastructure.persistence`（不要求「第一个值」）。
- RULING-P4-4（第 10 条）：「不查库」不可确定性断言，只断言非 uuid 路径参数答 400 且不是 `PLATFORM_APPROVAL_TASK_NOT_FOUND`。
- RULING-P4-5：第 7 条验收只验表层，作业级「只跳过一次」由实现代理随 `ProcessArchiveJobIT` 改；第 8 条 `DeliverySubject` 组件顺序以验收为准（对象类别、对象 ID、用途、触发事件），企业微信 `task_id` 只断言重试稳定、行间不同、字符集合法；第 12 条不限定错误码；第 2 条两个部分索引只断言含 `created_at` / `escalate_at` 与状态谓词。
- 改名边界（one-glossary 经 one-coordinator，2026-10-16）：BPMN 节点名与 `LegacyVocabulary` 中新系统一侧改为「部门负责人审批 / OrgUnitHeadApproval」；`LegacyVocabulary` 中老系统旧名「区域负责人审批」「regionalHeadReview」**保留**（回放老数据要用），不得随改名删除。词表 #270 已把旧中文名列为禁用，G38 只扫代码标识、不扫字面量与资源文本，保留的旧名不会使守护变红。
- 随带类名改名（one-glossary-b2 交，来源 #272 B2 的 G40–G42 检查；**等 #272 合并后再改**，之前保持现状）：① `platform.approval` events 包 `ApprovalTaskOverdue` → `ApprovalTaskBecameOverdue`（领域事件以过去式业务动词结尾），同步事件目录条目与消费方，删 `code-term-baseline.txt` 对应行并调低 count，词条 `approval_task_overdue` 英文名如需随改则在 `backend/glossary/proposed/code-aggregates-events.yaml` 同步改 id 与 snake；② `TaskResulted` → `TaskHandled`（R3-AP-60「任务已办理」），删 `code-aggregates-events.yaml` 中临时 `task_resulted` 条目。`AccountDisabled` 同名冲突不在本卡，待 one-glossary 用户确认单裁定。事件改名属契约变化，验收与事件目录随改（测试代理按裁定）。
- RULING-P4-6：`EmployeeIdContractTest.otherIdNamesKeepOnlyInboxItemTaskId` 断言 `OTHER_ID_NAMES` 等于 {taskId, instanceId, documentId}，与 RULING-P4-D6 及新验收 `ApprovalIdContractTest` 冲突；由测试代理改为「只按全名放行 `InboxItemResponse.taskId`」，含义随 D6 收紧。
