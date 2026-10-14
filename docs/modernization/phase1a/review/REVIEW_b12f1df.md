# Phase 1a 第三批 opus 复审（b12f1df）

# FORCITIS one Phase 1a 本批交付复审（HEAD b12f1df，基线 9f3cd83）

## 结论

**本批暂不能合入。** 状态机、乐观锁、事件信封、按 eventId 幂等的审计这几块基础是对的。但有 1 个 Blocker 和 7 个 High 问题，直接违反 §14 第 1、5、10 项和 SCAFFOLD_BRIEF 的 G-1。

最严重的问题是：所有带法人的聚合，法人都来自一个写死的 `1`。数据库里的「法人必填」约束因此只是形式上满足，并没有起到保护作用。

另外两个结构性问题：
- 审批与工时单在同一个事务里修改多个聚合，而且没有登记为例外。
- 仓库里同时存在两套聚合、版本模型：kernel 的 `AggregateRoot` 和审批 / 工时单自带的 `eventSequence`。两套对 `aggregateVersion` 的定义不同。

本次复审为只读，没有修改任何文件。审阅的源码中没有发现针对自动化分析的指令型文字。

---

## 问题清单（按严重度）

### Blocker

**B-1　会话法人写死为 1，所有业务单据、审批和事件都带着这个猜测值**
- **证据**
  - `backend/src/main/java/com/forcitis/one/platform/identity/infrastructure/security/SessionCurrentUser.java:24`：`return new LegalEntityId(1L);`
  - 下游写入：`cost/timesheet/application/SubmitTimesheet.java:70`（`Timesheet.draft(..., currentUser.legalEntityId(), ...)`）→ `approval_requests.legal_entity_id`、`approval_tasks.legal_entity_id`。
  - 每个事件信封的 `legalEntityId` 也来自它；工作日历的时限计算也按它算（`TaskRegistrar.java:43`）。
  - 测试把这个值固化成了期望结果：`TimesheetApprovalScenarioIT.java:330-332` 断言 `isEqualTo(1L)`。
  - ADR-0026 §背景已经点名这个问题，本批反而让它扩散到了新的聚合和 NOT NULL 列。
- **为什么严重**：违反 §14 第 5 项「来源不确定时失败，不得默认」和 G-1。数据库约束只证明列非空，不证明值正确。上线后每张工时单、每条审批都会落在法人 1 上，事后无法区分。
- **建议**
  - 在实现「员工 → 法人生效区间」（ADR-0026 §2.5）之前，`legalEntityId()` 应在无法解析时抛出领域异常。
  - 工时单提交在这种情况下返回 422（`COST_TIMESHEET_LEGAL_ENTITY_UNRESOLVED`）。
  - 删除 `TimesheetApprovalScenarioIT:330` 中对 `1L` 的断言，改为断言「无法解析时拒绝」。

### High

**H-1　一个事务修改多个聚合，未登记为例外（§14 第 1 项）**
- **证据**
  - `SubmitTimesheet.java:73-78` 在同一个 `@Transactional` 里做了三件事：
    - 同步调用 `approvals.start(...)`，写 `ApprovalRequest`；
    - 引擎监听器 `TaskAssignmentListener` → `TaskRegistrar.register` 写 N 个 `ApprovalTask`；
    - 写 `Timesheet`。
  - `ApprovalLifecycle.java:55-76`：`returned` / `reject` 改 request，再在 `closeOpenTasks` 里逐个改 task。`DecideTasks.java:111-133` 也同时改 task 和 request。
  - `ApprovalLifecycle.java:13-16` 的注释自称「request 是聚合根，task 在它的锁下变化」，但 `ApprovalTask` 有自己的 version、自己的 `aggregateType`，也单独发事件，实际上是独立聚合。
  - 所有事件的 `relatedAggregates` 都是 `List.of()`（例如 `ApprovalTask.java:176`、`Timesheet.java:206`），违反 EVENT_ANALYTICS_FIELDS §6.4。
- **建议**：二选一，并写进 §14「已登记的例外」。
  - 做法 (a)：承认「审批请求 + 任务」是一个聚合（task 作为内部实体，共用 request 的 version 和事件序号）；工时单改为发 `TimesheetSubmitted`，由审批订阅后发起。
  - 做法 (b)：登记例外并写明理由，同时把同一事务内改动的其他聚合填进 `relatedAggregates`。

**H-2　法人代码更正与发号之间存在竞态，可能产生永远无法更正的错号**
- **证据**
  - `platform/numbering/application/IssueDocumentNumber.java:32-36`：先读 `findActiveCode`（不加锁），再 `reserveNext`。
  - `organization/workforce/application/UpdateLegalEntity.java:36`：检查 `hasIssuedNumbers`，只看 `numbering_counters` 是否存在（`JdbcNumberingRuleRepository.java:68`），然后改 code。两边没有共同的锁。
- **竞态过程**：T1 读到旧代码 ABC → T2 发现还没有计数器，把代码改成 ABD 并提交 → T1 插入计数器并提交编号 `QT-ABC-…`。结果是法人代码为 ABD，却已有一张 ABC 的单号，而且此后代码再也不能更正。停用法人与发号之间也有同样的窗口。
- **建议**：发号时对法人行加共享锁（例如在 `LegalEntityDirectory` 上新增 `lockActiveCode`，执行 `SELECT … FOR SHARE`）；更正时 `UPDATE` 自然需要排他锁，两者就会串行。补一个双事务的并发 IT。

**H-3　错误链没有统一（§14 第 10 项）**
- **证据**
  - 身份模块仍返回自定义的 `ErrorResponse`：`platform/identity/web/IdentityResponses.java:12-13`，并通过 `AdminOutcome.Refused` 返回。
  - 工时单提交的 422 / 404 用业务响应体表达：`cost/timesheet/web/TimesheetController.java`，`submit` 中的 `SubmitTimesheetResponse("EXCEEDS_REMAINING_HOURS", …)`。前端为此在 `frontend/packages/api/src/http.ts:92` 额外读取 `record['outcome']`。
  - 401 没有响应体：`SecurityConfiguration.java:41` 使用 `HttpStatusEntryPoint`。
  - 403 由 `GlobalExceptionHandler.java:53-55` 重新抛出，交给 Spring 默认错误页处理，结果不是 ProblemDetail，也没有 `errorCode`。
  - 本应是业务拒绝的情况被抛成 `IllegalStateException`，最终变成 500：
    - `IssueDocumentNumber.java:31,33`（没有生效规则、法人已停用）；
    - `NodeAssignment.java:72`（没有人可指派）；
    - `TaskRegistrar.java:45`：`escalationWorkdays().getAsInt()`，DMN 只给了时限、没给升级天数时抛 `NoSuchElementException`，见 `NodeDecisions.java:84-85`，两者没有成对校验。
  - 审批与工时单的错误码没有作为条目登记：`backend/glossary/proposed/platform-approval.yaml` 和 `cost-timesheet.yaml` 中没有 `error__platform_approval_*`、`error__cost_timesheet_*`；workforce、identity、integration 则有。`NamingSurfaceTest.java:526-560` 只检查错误码由词表中的词组成，不检查错误码本身是否登记。
- **建议**
  - 身份模块和工时单提交改为抛 `DomainException`。
  - 为安全过滤链配置输出 ProblemDetail 的 `AuthenticationEntryPoint` 和 `AccessDeniedHandler`。
  - DMN 的时限与升级天数在 `decide` 时成对校验，缺一项就拒绝。
  - 扩展守护，要求每个 `ErrorCode` 常量都有对应的 `error__` 条目。

**H-4　绕开聚合写状态，现有守护看不到**
- **证据**
  - 账号创建靠数据库默认值决定状态：`JdbcAccountRepository.java:41-43`（`insert … (user_id)`，status 取 DEFAULT 'ENABLED'）。
  - 授予角色直接写 SQL：`AccountProvisioning.java:36-49`、`JdbcAccountRepository.java:91-94`。没有经过 `UserAccount`，没有 version，没有事件，审计是直接写入的。
  - `UserAccount.replaceRoles`（`UserAccount.java:79-82`）只记录事件，角色集合本身不是聚合状态，真正的写入在 `ReplaceAccountRoles.java:67` 另行完成。
  - 外发投递：
    - `JdbcOutboundDeliveryRepository.java` 的 `claimDue` 在 SQL 中改 `attempt_count` / `next_attempt_at`；
    - `recordHandledNote` / `recordHandledResult` 在 108-125 行无条件 `UPDATE`，不经聚合，也不带并发条件。
  - `StateWritePolicy` 只识别名为 `*Status` 的枚举和 SQL 字面量，对 INSERT 时省略状态列、靠 DEFAULT 生效的情况完全看不到。
- **建议**
  - 新增 `UserAccount.open(userId, defaultRole)` 工厂方法，发 `AccountOpened`；角色集合纳入聚合状态，由仓储按聚合保存。
  - 投递的「已处理说明」改为聚合方法加条件更新。
  - 守护增加两条：带状态列的表，INSERT 必须显式写出状态列；`*Repository` 中除 `save` 以外的 UPDATE 需要登记。

**H-5　两套聚合与版本模型，`aggregateVersion` 语义不一致**
- **证据**
  - kernel 的 `AggregateRoot.java:29-31,48`：没有事件的保存也把 version 加 1，结果事件序号出现空洞。例如 `UpdateLegalEntity.java:38-41` 即使没有任何变化也会保存。
  - 审批与工时单不继承 `AggregateRoot`，自带 `eventSequence` / `event_count`，`version` 只当锁使用（`ApprovalRequest.java:61`、`ApprovalTask.java:56`、`Timesheet.java:86`）。
  - 有三个同名的 `PendingEvent`：`kernel/event/PendingEvent.java`、`platform/approval/domain/PendingEvent.java`、`cost/timesheet/domain/PendingEvent.java`。另有两份逐字相同的 `ApprovalEvents` / `TimesheetEvents`。
  - 审批与工时单也不用 `kernel.StateMachine`。
  - `RegisteredEmployee.java:15-24` 的 version 恒为 0，同一个 `OrgSnapshotChanged` 同时由 `SyncRun` 和 `Employee` 两种聚合类型发出。
  - EVENT_ANALYTICS_FIELDS §6.3 的定义是「每发一个事件加一」，只有审批这一套符合。
- **建议**：统一为一套。`AggregateRoot` 内部分开持有 `version`（锁）和 `eventSequence`（每个事件加一）；审批与工时单迁到 `AggregateRoot` + `StateMachine`；删除两个模块私有的 `PendingEvent` 与 `*Events` 类。

**H-6　工时单的工作日期取「提交当天」，重新提交还会改写**
- **证据**：`SubmitTimesheet.java:67,71` 用 `timesheets.today()` 作为 `workDate`；命令 `SubmitTimesheetCommand` 里根本没有工作日期字段。事件的 `businessDate` 也取它（`Timesheet.java:207`）。
- **为什么严重**：退回后过几天再提交，工时会被归到另一天，甚至另一个期间。`Timesheet` 也没有 `AccountingPeriod`（§14 第 5 项）。
- **建议**：工作日期由用户填写并在聚合构造时校验；如果规格确实要求「提交日 = 工作日」，在规则卡里写明，并在 `revise` 中禁止改动。

**H-7　测试断言其他模块的异步副作用，并跨模块直接读写表（违反 BRIEF「测试只断言本模块效果」）**
- **断言审计模块的异步写入**：`platform/approval/TaskLifecycleIT.java:117,133-138`、`RetractionIT.java:95`、`TaskTransferIT.java:81`，以及 `ApprovalIntegrationSupport.java:218-224` 提供的 `auditActions` / `auditActors`。
- **在审批测试里测工时单表**：`platform/approval/AggregateConcurrencyIT.java:130,140`。
- **工时单测试读审批表**：`cost/timesheet/application/TimesheetApprovalScenarioIT.java:331`。
- **workforce 测试截断 numbering 的表**：`organization/workforce/legal/LegalEntityIntegrationTest.java:60`。
- **numbering 测试直接写 workforce 的表**：`platform/numbering/NumberingRulesIT.java:100,167,174`，用状态字面量插入 / 删除 `legal_entities`。
- **建议**：
  - 审批 IT 改为断言本模块发出的事件（`ApplicationEvents`）；「一次办理只产生一条审计记录」移到 `platform/audit` 的 IT，用合成事件驱动。
  - 跨模块的前置数据通过对方的 api 准备，或用 `@MockitoBean` 替身。

### Medium

**M-1　只读路径获取行锁（§14 第 9 项）**
- `GetTimesheet.java:27-35` 标的是 `@Transactional`，不是只读。它调用 `JdbcTimesheetRepository.find`，带 `FOR UPDATE`（第 34 行）。
- 随后经 `approvals.checkWithdraw` → `Retraction.check` → `JdbcApprovalRequestRepository.find`，也带 `FOR UPDATE`（第 44-50 行）。`CheckWithdraw.java:19` 同样是可写事务。
- 结果是查看工时单会与审批办理争抢同一把行锁。
- **建议**：仓储拆成 `find`（不加锁）和 `findForUpdate`，查询类一律用 `readOnly = true`。

**M-2　撤回 / 撤销后结果事件到达前，再次提交会得到已失效的实例**
- `SubmitTimesheet.java:64-66`：状态还是 SUBMITTED 时，直接返回 `approvalInstanceId`。但审批请求此时可能已经是 RETURNED / CANCELLED（`ApplyApprovalResult` 是异步的）。
- 用户会收到「已提交」，实例却已经结束。
- **建议**：返回前用 `approvals.status(document)` 校验；不一致时返回 409 `COST_TIMESHEET_RESULT_PENDING`。

**M-3　剩余工时先检查后执行**
- `SubmitTimesheet.java:48-53` 在加锁之前、按工作包检查剩余工时；并发提交不同工时单可以一起超额。
- **建议**：在工作包上加锁或做预留，或者在 delivery.execution 一侧用条件更新扣减。

**M-4　状态变化没有事件（§14 第 3 项）**
- `ApprovalRequest.begin` / `restart`（`ApprovalRequest.java:82-104`）不发事件：开新一轮这件事不可观测。
- `ApprovalTask.close` / `completeAutomatically` / `assignAutomatically`（`ApprovalTask.java:96-100,127-135`）不发事件。系统自动通过改为直接写审计：`TaskRegistrar.java:36`。
- `OutboundDelivery` 进入 SENT / DEAD_LETTER 不发事件。
- **建议**：补 `ApprovalRoundStarted`、`TaskClosed`、`TaskPassedBySystem`；投递进入 DEAD_LETTER 时发 `@NotAudited` 事件，便于告警。

**M-5　`ApprovalRequest` 允许从 RUNNING 执行 RESTART**
- `ApprovalRequest.java:33` 允许 RUNNING 作为 RESTART 的起点。目前 `StartApproval.java:50-52` 提前返回，所以碰不到；但在领域层这是一个合法转换，会把正在运行的实例孤立掉。
- `StartApproval.java:57-62` 先启动引擎实例，再做转换检查；状态为 APPROVED / REJECTED 时会白白启动一个实例，再整体回滚。
- **建议**：RESTART 只允许从 RETURNED / CANCELLED 出发；转换检查放在 `engine.startInstance` 之前。

**M-6　任务没有主动释放的路径**
- CLAIMED → PENDING 只能由超时升级触发（`ApprovalTask.java:154-166`）。没有配置时限的节点，认领人休假就只能等他自己转交。
- **建议**：增加由认领人发起的 `release`，以及流程管理员的改派，两者都要发事件。

**M-7　`TaskDeadline` 中的判断是死代码，旧轮次的残留任务会每 5 分钟失败一次**
- `TaskDeadline.java:61-64`：`lifecycle.lock(instance)` 按 `current_instance_id` 查（`JdbcApprovalRequestRepository.java:54-60`），实例不是当前轮次时直接抛 NOT_FOUND，第 63 行的比较永远走不到。
- 如果有旧实例的任务没被关闭，它会一直留在 `findDue` 结果里，`deadline.failed` 计数持续增长。
- **建议**：按 document 加锁；实例不是当前轮次时关闭该任务并发事件。

**M-8　审计幂等只覆盖事件路径**
- `AuditDomainEvents` 按 `event_id` 去重是可靠的：有唯一索引，`on conflict do nothing`，eventId 随事件一起序列化进登记表。
- 但 `HandleOrgSnapshotChange.java:55` 和 `AccountProvisioning.java:40-48` 在监听器里调用 `audit.record`，事件重投就会重复写审计。
- **建议**：改为发事件，或者把监听器收到的 `causationId` 写进 `audit_entries.event_id`。

**M-9　事件登记表重投时各消费方的幂等情况**
- **幂等**：
  - `ApplyApprovalResult`（`consumed_events`，同一事务内回滚，可靠）；
  - `AuditDomainEvents`；
  - `TaskNotifier`（按卡片的去重键）。
- **不幂等或只是近似幂等**：
  - `HandleOrgSnapshotChange`：重复写审计，见 M-8；
  - `TaskNotifier.onTaskClaimed`：每次重投都调用 `markHandled`，取决于 gateway 是否幂等，需要确认。

**M-10　DMN 输入静默缺省**
- `NodeDecisions.java:67-75`：模块传入的路由标记如果不是决策表的输入，会被直接忽略；表里的其他输入一律当作 false。这违反 G-1。
- **建议**：出现未知标记时拒绝。

**M-11　时区写死，没有使用 `BusinessZone`**
- `platform/approval/application/BusinessTime.java:14`、`cost/timesheet/application/Timesheets.java:18` 写死了 `ZoneId.of("Asia/Shanghai")`。kernel 已经有可配置的 `BusinessZone`（`KernelConfiguration.java:12`）。
- `Clock` 通过 `getIfAvailable(Clock::systemUTC)` 静默兜底（6 处以上）。
- **建议**：注入 `BusinessZone` 和 `Clock`，缺少时启动失败。

**M-12　错误详情泄露内部信息**
- `GlobalExceptionHandler.java:34` 把 `DomainException.getMessage()` 原样放进 `detail`。消息中包含：
  - 任务号与状态（`ApprovalTask.java:171`）；
  - 「Transfer target {id} is not an employed person」（`TransferTask.java:50`），可借此探测任意用户 ID 的在职状态；
  - 聚合键（`JdbcApprovalRequestRepository.java:108`）。
- 另外，Flowable 默认使用顺序数字 ID；`GetTaskDetail` 对非参与人返回 404，而 `DecideTasks` / `ClaimTask` 对同样的人返回 403。两者不一致，可以用来枚举任务是否存在。
- **建议**：`detail` 只输出按错误码配置的对外文案，内部消息只写日志；任务操作对非参与人统一返回 404；配置 `StrongUuidGenerator`。

**M-13　H5 静默登录的 state 没有绑定浏览器**
- `BeginWecomLogin.java:29-34` 发出的 state 只在服务端存了哈希，和发起请求的浏览器没有关联。`/login-states` 是公开端点。
- 攻击方式：攻击者用自己的 code 和 state 拼出 `?code=&amp;state=` 链接；受害者在 H5 中打开后，`use-session-gate.ts:39-42` 会直接用它登录，受害者就以攻击者身份进入系统（登录 CSRF）。
- 该端点也没有限流（仓库里没有任何限流实现）。
- **已具备的部分**：I-9 的原子消费；code 在请求前就从地址栏去掉（`use-session-gate.ts:41`）；会话没保存下来时不会无限重定向（第 44 行）。
- **建议**：发 state 时同时下发 HttpOnly、SameSite=Lax 的 cookie（内容为 state 的哈希），登录时比对；`/login-states` 按 IP 限流。

**M-14　生成的 Zod 校验不覆盖错误响应**
- `packages/api/openapi.json` 中没有任何 4xx 响应，也没有 ProblemDetail 模式（检索结果为 0）。
- `http.ts:123-136` 只校验成功响应，错误码按 `errorCode` 或 `outcome` 临时读取。
- 前端的错误码文案表以字符串为键，不受类型检查：`apps/h5/src/modules/session/model/wecom-login.ts:45-52`。
- 部分应为枚举的字段生成成了普通字符串：`DecisionResponseSchema.status`、`InboxItemResponseSchema.source`。
- **建议**：OpenAPI 为每个操作声明 ProblemDetail 和 `errorCode` 枚举；生成 `ErrorCode` 联合类型，文案表改为 `Record&lt;ErrorCode, string&gt;`；状态字段输出枚举。

**M-15　H5 与 PC 之间复制的代码，已经开始分叉**
- 逐字相同（diff 为 0）的文件：
  - `shared/lib/failure.ts`、`safe-url.ts`、`format-date-time.ts`；
  - `modules/approval/api/approval-api.ts`、`hooks/use-inbox.ts`。
- 已经分叉的文件：`model/task.ts`。驳回、退回「必须填写原因」的规则只在 H5 有（第 26-44 行），同时在后端也有一份。另外，web 端的 `APPROVAL_STATUS_LABELS` 缺少 CANCELLED。
- **建议**：抽到 `packages/`（例如 `@forcitis/approval-model`）；`check-structure` 禁止两个 app 之间出现相同内容的文件。

**M-16　前端用语守护的漏洞**
- 允许清单里有 47 个单字（`scripts/copy-allow-list.yaml`），与词表的切分组合后，几乎任何中文都能被拼出来。
- 只扫描前端源码。服务端下发的显示文字不受约束：
  - 待办标题是 `processKey.name() + " " + documentId`（`ListInbox.java:34`），用户会看到 `COST_TIMESHEET_APPROVAL 42`；
  - `TaskNotifier.java:47-75` 中的中文卡片文案；
  - 转交卡片的标题用的是 `nodeKey`，不是节点名（第 61 行）。
- 英文、枚举名出现在界面上不会被拦截。
- `SKIPPED_DIRECTORY` 按目录名跳过（`copy-glossary.mjs:13`），生产代码中任何叫 `testing` 的目录都会被跳过。
- **建议**：单字只允许出现在相邻词之间；后端对外显示的文案集中定义，并纳入同一守护；待办标题由前端按 `processKey` 映射显示。

**M-17　测试之间的共享状态**
- `WorkforceIntegrationSupport.java:64-96` 在 TRUNCATE 之前轮询 `event_publication`，最多等 20 秒，这是防锁冲突的权宜之计。但只要有一个监听器永久失败、留下未完成记录，之后所有测试都会超时，形成连锁失败。
- 默认测试配置没有关闭审批时限作业：`src/test/resources/application-default.properties` 只关了登记表作业；`ApprovalDeadlineJob` 的开关在主配置 `platform_approval.properties:19` 默认为 true，每 5 分钟运行一次。
- **建议**：测试中关闭全部 `@Scheduled`；TRUNCATE 之前关闭监听执行器（或者每个模块用独立 schema 或独立容器），不靠轮询等待。

**M-18　magic number 默认值与老系统 ID**
- `config/platform_integration.properties:14-18` 给 A8 关系类型 ID、枚举 ID 设了生产值作为默认（例如 `${A8_HEAD_RELATIONSHIP_TYPE_ID:-1726…}`），缺少配置时不会失败，违反 G-1。
- **建议**：去掉默认值，缺少即启动失败。

### Nit

- **类型化 ID 与值对象没用到底（§14 第 6、7 项）**：
  - `ReplaceAccountRoles.execute(long userId, …)`、`UpdateLegalEntity.execute(long, String, String, String)` 的应用层入参是裸类型；
  - `ApprovalTask.nodeKey` 是 `String`，尽管已经有 `NodeKey` 类型；
  - `resultReason`、`approvedDigest`、`contentDigest` 是 `String`；
  - `ProcessVariables` 用 `Map&lt;String, List&lt;String&gt;&gt;` 表达流程变量，是老 BPM 表单变量的形状。
- **命名不一致**：列名 `approved_hash`，字段名 `approvedDigest`（`JdbcApprovalTaskRepository.java:29`）。
- **兜底默认**：`FlowableApprovalEngine.java:292` 在缺少分配时间时退回 `Instant.EPOCH`，违反 G-1。
- **异常处理细节**：`SessionCurrentUser.java:16-19` 中，匿名 token 的 `isAuthenticated()` 为 true，随后 `parseLong("anonymousUser")` 会抛 `NumberFormatException`。
- **复述代码的注释**：
  - `LegalEntityRepository.java:10`（「Returns the stored legal entity …」）；
  - `AggregateRoot.java:41`；
  - `AccountRepository.java:25`（「Enabled or not」）；
  - `TaskDeadline.java:111` 中内联写了 `java.util.Comparator` 的全名，`GlobalExceptionHandler.java:65,67` 也有同样写法。
  - 本批新增注释共 192 处，整体基本克制。
- **审批模块配置中的老表名**：`platform_approval.properties:15` 的视图名带有 `psa_`。这属于已批准的防腐层范围，但该配置文件属于领域模块，建议改为环境变量、不设默认值。

---

## §14 逐项结论

| # | 项 | 结论 |
|---|---|---|
| 1 | 聚合边界 | **问题**：H-1（`SubmitTimesheet.java:73-78`；`ApprovalLifecycle.java:55-76`；`DecideTasks.java:111-133`），未登记为例外；`relatedAggregates` 全为空。 |
| 2 | 状态机在聚合内 | **问题**。&lt;br&gt;• 转换表：审批请求、工时单有转换表，但不用 kernel 的 `StateMachine`；`ApprovalTask` 只有零散的 `require`，没有转换表。&lt;br&gt;• 非法与缺失的路径：RUNNING→RESTART（M-5）；任务没有主动释放路径（M-6）。&lt;br&gt;• 绕开聚合写状态：账号创建、角色授予、投递认领与说明都绕开了聚合（H-4）；守护对 DEFAULT 状态列和非状态字段看不到。&lt;br&gt;• Flowable 回调：`TaskAssignmentListener` 经 `TaskRegistrar` 走聚合，没有直接写状态。&lt;br&gt;• 定时作业：`TaskDeadline` 走聚合，没有直接写状态。 |
| 3 | 事件由聚合产生 | **部分符合**：事件是不可变 record，负载只含 ID 与值对象，AsyncAPI 中全部登记（34 个事件类全部能找到）。&lt;br&gt;**问题**：&lt;br&gt;• 多处状态变化不发事件（M-4）；&lt;br&gt;• `aggregateVersion` 两套语义，kernel 那套有空洞（H-5）；&lt;br&gt;• EVENT_ANALYTICS §6.8：审批事件的 `businessDate` 没有落到 OLTP 行上（`approval_requests` 没有完成日期列）。 |
| 4 | 并发 | **大体符合**：审批、工时单、账号、角色、法人、编号规则都带 version + 前置状态条件；投递用「认领号 + 状态」。&lt;br&gt;**问题**：&lt;br&gt;• 法人代码更正与发号竞态（H-2）；&lt;br&gt;• `recordHandledNote` / `recordHandledResult` 无条件更新；&lt;br&gt;• `account_roles` 不受 version 保护；&lt;br&gt;• 剩余工时先检查后执行（M-3）。&lt;br&gt;**会签、转交、撤回之间**：都先锁 `approval_requests` 行，加锁后重新读引擎，是串行的，**符合**。&lt;br&gt;**时限作业与办理之间**：同样先锁请求行，**符合**。 |
| 5 | 法人与期间维度 | **问题**：B-1（法人写死为 1）；H-6（工时单没有期间，工作日期取提交日）。 |
| 6 | 按 ID 引用 | **基本符合**：跨模块一律用 `DocumentReference` / 类型化 ID，有 `DocumentReferenceGuardTest` 守护。&lt;br&gt;**问题**：应用层入参用裸 `long`、`String`（见 Nit）。 |
| 7 | 值对象 | **部分符合**：`Hours`、`Reason`、`RoundNumber`、`LegalEntityCode` 校验都在构造处。&lt;br&gt;**问题**：原因、内容摘要、轮次、流程变量仍是裸类型（见 Nit）。 |
| 8 | 防腐层 | **符合**：供应商名只出现在 `platform.integration.infrastructure.*`；老待办视图与回放工具是已批准的例外。&lt;br&gt;**问题**：magic 默认值（M-18）。 |
| 9 | 读模型 | **问题**：&lt;br&gt;• 查询路径加 `FOR UPDATE`，且用可写事务（M-1）；&lt;br&gt;• 合并待办读的是 Flowable，不读任务聚合，两个数据源只靠同一事务里双写保持一致；&lt;br&gt;• 待办标题直接暴露枚举名（M-16）。 |
| 10 | 错误链 | **问题**：H-3。 |
| 11 | 上下文映射 | **符合**：`module-dependencies.txt` 与 REIMAGINED_ARCHITECTURE §2.2 的图一致。&lt;br&gt;**问题**：图中的「一致性：同一事务只改一个聚合」与实际实现（H-1）不符；cost.timesheet 订阅 `ApprovalCompleted` 这条事件边没有画成订阅关系。 |
| 12 | 统一语言 | **部分符合**：命名守护和用语守护都在运行。&lt;br&gt;**问题**：&lt;br&gt;• 审批、工时单的错误码没有登记为条目（H-3）；&lt;br&gt;• `approved_hash` 与 `approvedDigest` 命名不一致；&lt;br&gt;• 前端用语守护有漏洞，后端显示文案不在守护范围内（M-16）。 |

---

## 建议补充的守护

1. **会话上下文不得返回常量**：ArchUnit 或源码扫描，要求 `CurrentUser` 的实现里不出现 `new LegalEntityId(&lt;字面量&gt;)`；同时补一个 IT：员工无法解析法人时，提交返回 422。
2. **单事务单聚合**：在测试中统计每个 `@Transactional` 用例内 `save` 了几种聚合类型，超过 1 种且不在 `architecture/multi-aggregate-exceptions.txt` 中登记的就失败。同时要求多聚合事务的事件 `relatedAggregates` 非空。
3. **状态写入守护增强**：
   - 带 `status` / `*_status` 列的表，INSERT 必须显式列出该列；
   - 仓储中 `save` / `insert` 以外的 UPDATE 需要登记；
   - 直接 `insert into … account_roles` 等关联表需要登记。
4. **事件序号连续**：属性测试，同一聚合任意操作序列下，`aggregateVersion` 必须是 1..n 连续；不含事件的保存不能推进事件序号。
5. **错误链**：对全部控制器做 MockMvc 合约测试，凡是 4xx / 5xx 响应，`Content-Type` 必须是 `application/problem+json`，且带 `errorCode`（401 / 403 也包括在内）；每个 `ErrorCode` 常量都必须在 glossary 中有 `error__` 条目。
6. **只读路径不加锁**：ArchUnit 规定 `Get*` / `List*` / `Check*` 类上的 `@Transactional` 必须是 `readOnly = true`，且不得调用名称包含 `ForUpdate` 或会加锁的仓储方法。
7. **跨模块测试隔离**：扫描 `src/test/java/&lt;模块&gt;/`，SQL 字符串中只允许出现本模块的 schema（`platform_audit` 也不例外）。
8. **发号与法人更正的并发 IT**：两个事务交错执行，断言不会出现「编号里的法人代码 ≠ 当前法人代码」。
9. **前端**：
   - 生成的错误码类型做穷尽性检查；
   - `apps/*/src` 之间禁止出现相同哈希的文件；
   - 用语守护中的单字只能作为连接词使用，并扫描后端 `TaskNotifier` 一类的对外文案。
10. **DMN 输出成对**：部署后的冒烟测试遍历所有节点，断言时限与升级天数要么同时出现、要么同时缺失；遇到未知路由标记时拒绝。

---

**如果只能改一件事**：我会先把法人改成「无法解析就拒绝」。具体是：去掉 `SessionCurrentUser.java:24` 的写死值，在 ADR-0026 的员工法人归属落地之前，`CurrentUser.legalEntityId()` 无法确定时直接抛领域异常，并删掉固化 `1L` 的测试断言。其余问题都能在合入后逐步修正；而这个写死值每多跑一天，就多一批带错误法人、事后无法区分的工时单、审批记录和事件。
