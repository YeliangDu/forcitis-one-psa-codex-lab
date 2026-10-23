# 修复批 A 单独 opus 复审

条目编号以本文为准，外部引用加前缀 A-。按时间顺序收录首轮复审与各轮复核。

---

## 第 1 轮

**结论：修后整合。** 审批这边的 H-1 结构基本立住了：任务是请求聚合的内部实体，引擎回调只改正在办理的那个请求，结果回写走事件、按 eventId 幂等。问题有三类：整套构建不绿；审批接口与前端批次在 If-Match 上冲突；还有几处 A 自述的偏离未获批准。按调度口径（surefire 与 failsafe 必须全绿），现在不能合入。

## 实测（`./mvnw -B verify`，worktree agent-a26e156ea446a7bdb，HEAD e55af59）
- **surefire**：572 个测试，5 个失败，19 个跳过，构建失败。跳过的是之前就有的 `@Disabled`（Phase 2）。
- **failsafe**：surefire 失败后不会继续跑，所以我加了 `-Dmaven.test.failure.ignore=true` 再跑一次。29 个类、214 个测试，0 失败、0 错误；但 fork 的 JVM 没有正常退出（有 Surefire kill 告警）。
- **gitleaks 8.30.1**：扫描基线以来 6 个提交，无泄漏。`check_legacy_name.py` 无违规。
- **5 个失败的根因**："基线就有"属实，但不是巧合。基线提交 b2fc504 改了 kernel 的 `AggregateRoot`：version 改为每次保存加一，事件序号另计。有两处没跟上：
  - 测试没改：`AggregateRootTest.contiguousSequence:49` 期望 6 实为 5；`LegalEntityStateMachineTest.severalEventsInOneUnit:137` 期望 10 实为 8；`SyncRunStateMachineTest.succeedingRecordsEvents:77` 期望 5 实为 4；`NumberingRuleStateMachineTest.events:43` 期望 6 实为 5；`LegalEntityIntegrationTest.updateLegalEntity:137` 数据库 version 期望 4 实为 2。
  - 七个聚合还在用过渡构造器 `super(version)`（即 `eventSequence = version`）：WorkCalendarYear、LegalEntity、SyncRun、Role、UserAccount、OutboundDelivery、NumberingRule。
  - **这是真缺陷，不只是测试过时。** 例如法人一次保存发 3 个事件（序号 2、3、4），存回去 version=2；下次加载时事件序号从 2 起算，下一个事件又是 3，与已发的事件重号。
  - 修法：这 7 张表加 `event_count` 列，迁移时回填为当前 version（旧语义下 version 正好等于事件数）；改用双参构造器并删除过渡构造器；5 处断言改为分别断言 version 与 eventSequence，作为断言修改单列理由。

## 问题

### Blocker
- **A-B1　全量构建不绿，且事件序号会重号**：证据与修法见上。7 个聚合分属别的批次，需要调度统一指派；但不修就不能整合。
- **A-B2　审批接口与前端批次的 If-Match 冲突**
  - 后端：`ApprovalTaskController.java:86-117` 要求 If-Match，缺了返回 428；但 OpenAPI 里把它设成了 `hidden`。
  - 当前 main 上的前端（H5 与 PC）完全不发 If-Match（在 `apps`、`packages` 里搜不到）。单独合入 A，界面上所有认领、办理、退回、转交都会得到 428。
  - fix-c-frontend 分支的 openapi.json 在 claim、decide、return、transfer 上声明了 `If-Match`，与 A 重新生成的 openapi.json 直接冲突。
  - 建议：G42 守护放行标准 HTTP 头，OpenAPI 正式声明 If-Match（release 也补上），两个批次一起合入。

### High
- **A-H1　ETag 校验不在锁内，412 不能保证**
  - `ApprovalTaskController.java:131-151`：先在控制器里用另一个只读事务查一次 version 做比较，之后用例重新开事务执行，中间可能已被别人改过。
  - `reassign`（第 123 行）完全不校验 If-Match。
  - 建议：期望版本作为参数传进用例，加锁后与 `request.version()` 比较。
- **A-H2　订阅侧对过期轮次和不可重试的错误处理不当**
  - 旧轮次的 TimesheetSubmitted 在新一轮已开始后被重投，会走到 `StartApproval.java:64` 抛 `ROUND_MISMATCH`，在 `StartApprovalOnSubmission.java:51` 被重新抛出，结果重试 8 次后报 ALERT。
  - 业务性拒绝（项目经理不存在、找不到路由）同样被当作可重试处理。
  - 另外 A 删掉了「未知项目经理返回 422」的用例，`SubmitTimesheet` 也不再校验项目经理是否在职。
  - 建议：轮次小于当前轮次时记日志后忽略；提交时用 OrgDirectory 校验项目经理在职；`DomainException` 不重试（与 (h) 的修法一起做）。
- **A-H3　(g) 改变了用户决定 D-1a-90**
  - D-1a-90 要求只追加的办理记录，包括转交和代理。A 用任务行代替，但转交与改派会覆盖 `assignee_id`（`ApprovalTask.java:105,114`），`handledBy` 只查当前处理人，转交前的处理人和改派记录都查不到。
  - 要么实现只追加记录表（在同一聚合的保存里插入，量不大），要么拿到用户同意。
- **A-H4　M-3 修复后，剩余工时仍然算错**
  - `JdbcTimesheetRepository.java:61-67` 只扣 `status='SUBMITTED'` 的工时。
  - `WorkPackage.remainingHours` 等于计划工时减实际工时，而实际工时没有任何地方回写；全仓也没有 TimesheetApproved 的消费方。
  - 后果：工时单一旦审批通过，它的工时就不再被扣除，可以反复超额提交。
  - 建议：扣除口径改为 SUBMITTED 加 APPROVED，或者由 delivery.execution 同步扣减；补一个对应的 IT。

### Medium
- **A-M1　(b) 时限规则卡正文过时**：表头已经改为 D-1a-111，但第 5 行仍写「T counts from the submission day」，RULE-377（第 9、18、22 行）仍按提交日，RULE-378（第 10、36、40 行）仍写决策表。需按调度要求明确写出「T 取任务创建所在工作日，不取工作日期，也不取提交日」，并重写 RULE-377、378。
- **A-M2　(f) 多聚合例外没有登记进 DATA_DESIGN_CHECKLIST §14**：只写在 `multi-aggregate-exceptions.txt`。需要登记进 DATA_DESIGN_CHECKLIST §14「已登记的例外」，理由写「编号与单据同生同灭，避免空号或重号」。另外实际写的是编号计数器（`reserveNext`），并不是 `NumberingRule` 聚合，登记名称要对上。
- **A-M3　(l) 新增依赖的两个条件都没满足**：
  - REIMAGINED_ARCHITECTURE §2.2 上下文图没有加 TS→WF、TS→NM 两条边（第 136-138 行）。
  - `module-dependencies.txt`（10ae858）与架构文档（b05be7a）不在同一个提交。
  - 依赖本身只走对方的 api 包，没有成环：workforce 与 numbering 都不依赖 cost.timesheet。
- **A-M4　(i) 部门列的注释前后矛盾**：`V20261014201000` 中 org_unit_id 的注释同时写了「创建时的部门」和「事实发生时的部门」，代码实际取的是当前部门。需要改正注释，并在规则卡写明推迟的理由。
- **A-M5　H-7 还有残留**：`TimesheetApprovalScenarioIT.java:216-225` 直接查 `platform_approval` 的表和 Flowable 的 TaskService，并继承审批模块的测试支撑类。应改为通过 `ApprovalProcesses.progress` 断言，或改用替身。
- **A-M6　(j) 删除的断言没有补位**：
  - 工时单的「旧副本保存被拒」（DDD-4）、「缺法人被数据库拒绝」（DDD-5）、「未知项目经理」三个用例删除后，cost.timesheet 里没有对应的新用例。
  - 断言的改动和删除没有在会话记录片段或 PR 里单列理由；A 没有新增任何 session-log 片段。
- **A-M7　(d) 428 的错误码没有登记**：
  - `PLATFORM_WEB_VERSION_REQUIRED` 在全仓和 glossary 中都不存在，第 44 行注释「platform.web 给的名字」不属实。
  - If-Match 格式不对返回 428，应为 400。
  - 接受弱 ETag（`W/`），与 RFC 9110 要求 If-Match 用强比较不符。
- **A-M8　守护容易被绕过**：
  - `ReadPathsDoNotLockTest:66` 不沿接口调用追踪，跨模块的读路径（例如 GetTimesheet → ApprovalProcesses）看不到，而且只按 Get/List/Check 命名识别。
  - `SingleAggregateGuardTest.anUnregisteredSetIsCaught` 只检查登记文件内容，没有真正驱动切面；文件头声称的 relatedAggregates 检查也没有实现。
  - 事件序号的属性测试只在审批和工时单的领域层，没有经过持久化往返，也没有覆盖全部 AggregateRoot 子类；如果覆盖了，A-B1 早就能发现。
- **A-M9　抽查契约，有两条没做到**：
  - D-1a-113 要求原卡片改为「已超时，已升级给某人」：`TaskNotifier.onTaskEscalated` 只给接收人发了新卡，原卡片没改。
  - D-1a-61 要求流程管理员是可多人的角色：现在是配置的单个用户 ID（`ConfiguredProcessAdministrator`）。
- **A-M10　(h) 审批发起永久失败后没有出路**：工时单停在 SUBMITTED，撤回返回 NOT_RUNNING，重新提交直接返回「已提交」。按 CLAUDE.md，要么做完整：发起被拒时发事件，工时单退回 RETURNED 并附原因；要么在规则卡写明为何后做更有价值（D-1a-107 下 1a 没有入口，这是可以写的理由）。
- **A-M11　迁移会删列，库里有数据就失败**：`V20261014200000` 删除了若干列，新增的必填列没有默认值。这在部署前没问题，但要写进部署前提，并确认还没有任何环境存了数据。另外 `remind_at = due_at` 的回填和 `NOT VALID` 约束，与文件头「不补猜测值」的原则不一致。
- **A-M12　任务状态机没有用到底**：`ApprovalTask.reassign` 和 `escalate`（第 113、149 行）直接给 status 赋值，绕过了 `MACHINE`。

### Nit
- `findByTaskForUpdate` 的子查询不带 document_type。
- `InboxItemResponse` 里 `dueAt` 与 `deadlineAt` 是重复字段。
- `openTasks` 没有分页。
- 改派不检查目标人在同一节点是否已有任务。
- TaskClosed、TaskReleased 不更新企业微信卡片。
- 术语提议文件按批次命名为 `fix-a.yaml`，应按模块命名。
- 节点配置错误抛 `IllegalStateException`，会变成 500。
- `SessionCurrentUser.java:24` 法人仍写死为 1（B-1，属于 B1 批次）。
- ETag 用的是请求聚合的 version：并行会签的另一位审批人办理后，或时限作业把任务标记超时后，客户端都会收到 412，需要重新读取。这在语义上成立，但要告诉前端。

## (a)~(l) 判断
- **(a) 可行，建议改。** 在 `platform.approval.api` 定义发布语言事件 `ApprovalRequested`，内容是发起所需的字段，包括目前 TimesheetSubmitted 里的 routingFlags、contentHash、summary。由 `Timesheet.submit` 记录这个事件，platform.approval 新增 `StartApprovalOnRequest` 订阅它。
  - 不会成环：approval 订阅的是自己 api 里的类型。
  - 改动量约半天：1 个 record、移动一个监听器、改 Timesheet 和 3 个左右测试、更新 AsyncAPI 与术语。
  - 要先确认 ArchitectureRules 是否允许「事件类型放在别的模块的 api 包、由本聚合发出」，不允许就要加一条登记的例外。
- **(b) A 的做法正确**，与调度口径一致。
  - 与「提交当天」的实际差异主要不在异步：正常情况下发起只延迟几秒。只有两种情况 T 会后移：在零点前后提交，或发起失败、重投时已跨天。
  - 真正的差异在串行节点：后续节点的任务各自从创建日重新起算 T。
  - 规则卡正文需按 A-M1 修改。
- **(c) 可接受**：沿用 `event_count` 列名，列注释已说明是事件序号。
- **(d) 部分可接受**：控制器本地的处理器可以暂时保留；但错误码要登记，校验要挪到锁内（见 A-H1、A-M7）。
- **(e) 不可接受**：应在守护里放行标准 HTTP 头并正式声明，否则与前端批次冲突（见 A-B2）。
- **(f) 可接受**，但要登记进 §14，并修正登记的聚合名（见 A-M2）。
- **(g) 不可接受**：这是对用户决定 D-1a-90 的偏离，要么实现，要么问用户（见 A-H3）。
- **(h) 现状不可接受**：至少要做到提交时校验、忽略过期轮次、业务性拒绝不重试；或者在规则卡写明推迟的理由（见 A-H2、A-M10）。
- **(i) 1a 可接受**：前提是改正注释，并在规则卡登记推迟（见 A-M4）。
- **(j) 改动本身多数正当**：依据是 D-1a-107、D-1a-179（契约第 113 行）、D-1a-100。缺的是补位用例和单列理由（见 A-M6）。
- **(k) 正当**：依据 D-1a-100 移除 DMN，DMN 已无残留。
- **(l) 原则上认可**：只走对方 api 包、没有成环；但调度提的两个条件都没满足（见 A-M3）。

## 整合前必须修
1. A-B1：7 个聚合持久化 `event_count` 并改用双参构造器，删除过渡构造器，5 处断言按新语义改并单列理由；之后 surefire 与 failsafe 全绿，并查明 fork 未退出的原因。
2. A-B2：OpenAPI 正式声明 If-Match（G42 放行标准头），与 fix-c-frontend 协调合入顺序。
3. A-H1：If-Match 的期望版本传进用例、在锁内比较；reassign 也要求 If-Match。
4. A-H2 与 A-M10：提交时校验项目经理；过期轮次的重投直接忽略；业务性拒绝不重试，并给工时单出路，或在规则卡写明推迟理由。
5. A-H3：实现只追加的办理记录，或取得用户书面同意。
6. A-H4：剩余工时把 APPROVED 也算作已占用，并补 IT。
7. A-M1、A-M2、A-M3：时限规则卡正文、§14 例外登记、上下文图补边，依赖文件与架构文档放在同一个提交。
8. A-M6、A-M7：补回删除的用例，单列断言改动的理由，登记 428 的错误码。

本次复审只读，没有修改任何文件；审阅的源码里没有发现针对自动化分析的指令型文字。

---

## 第 2 轮

**结论：仍需修，但只剩小项。** 全量构建已经全绿，上次的 Blocker 和 High 都修好了。剩下三件：退回来源记错、D-1a-90 只做了一半、流程管理员角色缺种子与启动检查。另外还有两处整合时要对齐。

**全量 verify（`clean verify`，HEAD 955f817）**：surefire 607 个测试，0 失败、0 错误、19 个跳过（都是之前就有的 Phase 2 `@Disabled`）；failsafe 32 个类、226 个测试，0 失败；BUILD SUCCESS，用时 2:32。只剩一个老问题：failsafe 结束时仍报 fork 的 JVM 在 `System.exit` 后 30 秒才被强制结束，说明有线程没停。

## 1. 上次的清单逐项复核
- **A-B1 已修。** 0f88592 合入了 B 的 a28927d：7 个聚合都持久化了 `event_count`，kernel 的过渡构造器已删除，5 个失败已消失，新增的 `EventSequencePersistenceIT` 和 `EventSequenceCoverageTest` 都通过。
- **A-B2 已修，但留下一处新的不一致。** OpenAPI 里 6 个操作都正式声明了 If-Match，问题是都标成 `required: false`；fix-c-frontend 那边声明的是 `required: true`。后端缺 If-Match 就返回 428，所以应改为 true，否则两个批次整合时生成代码的比对会冲突。
- **A-H1 已修。** 期望版本作为参数传进用例，在 `Approvals.requireVersion` 里加锁后强比较；缺失返回 428，格式错返回 400，不符返回 412；release 和 reassign 也要求 If-Match。
  - 小瑕疵：三个错误码 `PLATFORM_WEB_VERSION_*` 已登记在 `fix-a.yaml`，但在 `ApprovalTaskController.java:45-47` 仍是字符串，platform.web 并没有定义它们。
- **A-H2 已修，A-M10 有一个新问题。**
  - 已修的部分：旧轮次的请求直接忽略；提交时校验项目经理在职；业务规则拒绝不再重试。
  - **新问题：退回来源记错了。** 发起被拒后，`RefuseApprovalStart` 调用的 `ApprovalRequest.returnRound` 写死 `ReturnSource.APPROVER`（`ApprovalRequest.java:244-246`）。这一轮根本没有审批人，统计会把它算成审批人退回。
  - 复用 ApprovalCompleted(RETURNED) 让工时单回到 RETURNED，这个思路本身可以接受：员工能看到原因、改好后重提。但要做到两点：
    - 增加 `ReturnSource.SYSTEM` 之类的取值，并登记术语。
    - 区分两类拒绝：员工自己能改的（例如项目经理离职），和员工改不了的配置问题（例如节点找不到审批人、缺工作日历年份）。后者退回给员工也解决不了，应保持重试并告警。
  - 另外 `AccessDeniedException` 不是 `DomainException`，会被重试 8 次；目前碰不到，属于小问题。
- **A-H3 部分已修。**
  - 已修：只追加的 `approval_task_records` 表覆盖了认领、释放、办理、加签建议、系统自动通过、转交、改派和代办人，在同一次聚合保存里写入。
  - 未修：D-1a-90 原文是「已办与逐轮历史从中查询」，但任务详情的历史仍从任务表读（`GetTaskDetail.java:57` 调 `historyOf`）。任务转交后，详情里看不到转交前的处理人。
  - 已办列表只列决定类动作，转交人自己的「已办」里看不到转交，这一点需要确认是否符合用户本意。
  - 「只追加」只靠约定，数据库层面没有拦截 UPDATE 或 DELETE。
- **A-H4 撤销**，按调度裁定：剩余工时校验推迟到 Phase 2，交付执行模块整体删除。
- **A-M1、A-M2、A-M3 已修。**
  - 规则卡已写明「T 取任务创建所在工作日，既不取工作日期，也不取提交日」，RULE-377 和 378 已重写。
  - §14「已登记的例外」新增了取号这一条，理由也写了。
  - 上下文图补上了 TS→WF、TS→NM 两条边。
  - 小瑕疵：RULE-383 仍写「fails the submission」，但现在提交本身成功，失败的是之后的审批发起。
- **A-M4 已修。** 注释改为「创建时的当前部门，不按工作日期」。目前 workforce 没有按日期查部门的 api，这样处理可以接受。
- **A-M5 大部分已修。** 工时单的 IT 不再查审批模块的表和 TaskService，但仍继承审批模块的测试支撑类。
- **A-M6、A-M7 已修。** 删除的用例已补回，错误码已登记。
- **A-M8 已修。**
  - 只读路径守护现在会沿接口追到实现，我核对了遍历逻辑，具体类的调用也仍在追踪。
  - 单聚合守护经真实切面驱动，并核对事件的 relatedAggregates。
  - 事件序号经数据库往返校验，另有覆盖面守护。
- **A-M9 部分已修。** 释放、关闭、升级都会同步更新原卡片，改派也不能落到同节点已有任务的人。流程管理员改为角色后的遗留见第 4 项。
- **A-M11 已修。** 已写入部署前清单 B-10。
- **A-M12 已修。** 任务的改派和升级都改为走状态机。
- **Nit**：
  - 已修：节点配置错误改为领域异常；总览改为游标分页；`InboxItemResponse` 去掉了重复字段 `dueAt`（前端没有地方用到）。
  - 未修：`findByTaskForUpdate` 的子查询仍不带 document_type；术语提议文件仍按批次命名为 `fix-a.yaml`；fork 未正常退出。

## 2. 删除交付执行模块：已修，没有放宽守护
- 迁移 V20261014202000（删除工时单的工作包列）和 V20261014203000（删表、删 schema）顺序正确。
- `retired-module-schemas.txt` 的机制比原来更严：只有当该目录最后一个迁移确实执行 `DROP SCHEMA` 时，NamingSurfaceTest（G44）才接受这个目录。
- DdlNamingTest 新增了对 `DROP TABLE` 的模型跟踪，并支持 EXCLUDE 约束，前缀 `ex_`。这是扩充，不是放松。

## 3. 改动未合并的迁移 V20261014200000：可接受
它从未进入 main。改动是删掉了猜测性的回填、去掉了 `NOT VALID`，都让迁移更严格；PREDEPLOY_CHECKLIST B-10 也写明 dev 库要先清空。
- 条件：在 PR 里写明「已在开发库执行过这个迁移的环境，Flyway 校验和会不一致，需要清库」。

## 4. 流程管理员改为角色：未修完
`RoleProcessAdministrator` 已经按角色取全部持有人，但还缺三样：
- identity 里没有 `process_administrator` 角色的种子迁移。
- D-1a-61 要求「生产未配置时系统拒绝启动」，目前没有启动检查（角色不存在或没有任何持有人时应启动失败）。
- PREDEPLOY_CHECKLIST 里没有「指定流程管理员」这一条。

## 5. `backend/ASSERTION_CHANGES_A.md`：内容不全，而且不该入仓
- 按 #53，这类记录不入仓。应删除这个文件，把内容放进 PR 描述和交给调度的报告。
- 漏列了以下改动：
  - `RetractionIT`：删除了 `auditActors(... ApprovalCompleted)` 断言。
  - `TaskTransferIT`：删除了审计断言，改了 3 处异常断言。
  - `RepeatApproverIT`：事件断言改写。
  - `CountersignIT`、`TimesheetBranchesIT`：各有错误码断言改动。

## 仍需修
1. 发起被拒时，退回来源不要记为审批人，新增 `ReturnSource.SYSTEM` 一类取值；员工改不了的配置类拒绝应重试并告警，不退回给员工（A-M10）。
2. 任务详情的逐轮历史改为从 `approval_task_records` 读；确认转交是否要进转交人的已办，必要时请用户定（A-H3）。
3. 流程管理员角色：补种子迁移、生产环境的启动检查，以及部署前清单条目（第 4 项）。
4. OpenAPI 里的 If-Match 改为 `required: true`，与 fix-c-frontend 对齐后一起合入（A-B2）。
5. 删除 `backend/ASSERTION_CHANGES_A.md`，补全漏列的条目，内容移到 PR 描述和报告（第 5 项）。

可以留到之后的小项：三个 `PLATFORM_WEB_VERSION_*` 错误码改为常量、RULE-383 措辞、fork 未正常退出、`findByTaskForUpdate` 补 document_type、只追加记录表在数据库层禁止修改与删除。

本次只读，没有修改任何文件。

---

## 第 3 轮

**结论：可整合，整合前先修一项。** 第二轮的 5 项都已修好，两项追加做得正确。剩下一个上线前必须改的问题：生产环境的流程管理员启动检查依赖一个仓库里从未设置过的 profile。

**全量 verify（`clean verify`，HEAD 03e4fc4，worktree 干净）**：surefire 607 个测试，0 失败、0 错误、19 个跳过（之前就有的 Phase 2 `@Disabled`）；failsafe 230 个测试，0 失败；BUILD SUCCESS，用时 2:43。failsafe 的 fork 不再被强制结束（pom 把退出等待放宽到 180 秒）。gitleaks 8.30.1 扫基线以来的提交，无泄漏；老系统名称检查无违规。

## 第二轮的 5 项（b5a9867）
1. **发起被拒的处理：已修。**
   - 新增 `ReturnSource.SYSTEM` 和 `returnRoundBySystem`，不再记成审批人退回。
   - 只有 `ASSIGNEE_INPUT_INVALID` 由系统退回给员工。配置类错误（例如路由标记不认识）保持重试并告警；相应的用例改成断言「重试」，这一改与新的分类口径一致。
   - 「请求方不是发起人」的情况告警后丢弃，不再重试 8 次。
2. **D-1a-90：已修。**
   - 逐轮历史改为从办理记录表读，已办包含转交和改派，只排除系统自动通过。
   - 数据库层用触发器拦截修改、删除和清空。
   - 小瑕疵：已办里也会出现「认领」「释放」，一次办理会出现两行。
3. **流程管理员角色：部分已修，有一个新问题。** 角色种子迁移 V20261014205200 和部署前清单 B-11 都有了，但启动检查有漏洞：
   - `EngineWiring.java:59` 只在 profile 为 `prod` 时拒绝启动，而全仓没有任何地方设置 `prod`。运行手册 `INFRA_RUNBOOK.md:109` 里写的只有 `SPRING_PROFILES_ACTIVE=dev`。
   - 结果是生产环境大概率只记一条警告，D-1a-61 的「未配置拒绝启动」实际不生效。
4. **If-Match 必填：已修。** OpenAPI 已标为 `required: true`，与 fix-c-frontend 一致。
5. **断言清单：已修。** `backend/ASSERTION_CHANGES_A.md` 已删除，按 #53 内容移到 A 的报告。我抽查本轮删改的断言，都有对应依据：
   - 「不是本人」改为「不存在」，403 改为 404；
   - 发起被拒的分类改了；
   - 已办的范围改了。

## 两项追加（03e4fc4）
- **404 统一：正确。**
  - 任务这边，非参与人和不存在的任务返回同一个错误码、同一段文案，`TaskGuard.notFound` 不带 taskId。新增的 IT 对 5 个动作逐一比对状态码、Content-Type、errorCode、title、detail、status、type，全部一致。
  - 工时单这边也改成同码同文案。
  - `instance` 字段的差异可以接受：它只是把调用方自己请求的路径原样带回，不透露任何新信息。
  - 遗留两个小问题：
    - 发起人能 GET 到任务详情，但对同一任务做 POST 却得到「不存在」，前后不一致，不泄露信息。
    - `TaskGuard` 的类注释仍写「throws AccessDeniedException」，已过时。
- **namedApproverInvalid：判断可靠。**
  - 只有一种情况会被标记：节点按单据字段取审批人（`FormFieldUsers`），取到了人，但这些人都已离职。
  - 字段值本身无效时，仍按原来的路径直接返回 `ASSIGNEE_INPUT_INVALID`。
  - 按上级取人、找不到人、缺工作日历年份这类配置问题，都不会被标记，走「找不到审批人」继续重试并告警。
  - 边界情况：项目经理离职和某个配置缺失同时出现、且全部节点都被跳过时，整单会退回给员工。员工改了项目经理后能正常走下去，配置缺失的节点本来就允许跳过，可以接受。
  - 这个判断从 Flowable 历史表读变量，依赖当前的历史级别；对应的 IT 已经覆盖。

## 整合前仍需修
1. **流程管理员的启动检查要改成默认严格。** 只有 dev 或 test profile 才降级为警告，其他环境（包括没设 profile）一律拒绝启动；或者在运行手册里明确规定生产必须设 `SPRING_PROFILES_ACTIVE=prod`，并加一个守护测试保证这一点。现在的写法遇到缺配置时会静默放行，违反 G-1。

可以留到之后的小项：
- `TaskGuard` 类注释过时；
- 已办列表是否保留「认领」「释放」两种动作，需要确认；
- 三个 `PLATFORM_WEB_VERSION_*` 错误码仍是字符串字面量，没有做成常量；
- RULE-383 的措辞仍写「提交失败」，实际失败的是之后的审批发起；
- `findByTaskForUpdate` 的子查询仍不带 document_type。

本次只读，没有修改任何文件。
