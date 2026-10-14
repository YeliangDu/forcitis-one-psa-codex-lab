# 修复批 B2 单独 opus 复审

条目编号以本文为准，外部引用加前缀 B2-。按时间顺序收录首轮复审与各轮复核。

---

## 第 1 轮

# B2 复审：修后整合（只读，没有改任何文件）

**结论：修后整合。** 有一部分做得扎实，可以保留：
- 触发器能拦住删除和改写，同日覆盖的空区间与排他约束、按日期查询都能正确配合；
- 两个清理作业的设计正确：`SyncPurgeJob` 不动运行中与未放行的记录；`OutboundDeliveryPurgeJob` 按状态分保留期，不删待投递的；
- prod 下的 TLS 检查在建立连接前执行；
- migrate 模式的 listener 时机正确；
- 注册表与 SQL 双向核对、跨 schema 检查，以及失效缺口要求删除，这几处守护有效。

需要修的有 5 个 High：
- T2 首次维护被锁；
- 归档文件会损坏，并可能死循环；
- 外键会让整轮同步失败；
- 运行账号对 `delivery_execution` 没有授权；
- 归档实现与用户新口径差距很大（见第 2 节）。

**验证结果：** `./mvnw -B verify` 退出码 0，用时 2:28。surefire 711 / 0 / 19 跳过，failsafe 215 / 0，与 B2 自报一致。19 个跳过都是原有的 `Pending*RulesTest`。

**口径来源：** 用户关于 OSS 归档的新口径是你转达的，仓库的 `intent/` 里还没有记录，需要用 `new_intent.py` 补登原话。

下文路径均相对于 worktree 根目录 `/Users/yeliang/Projects/FORCITIS/forcitis-one-psa/.claude/worktrees/agent-a30d8cf1c14c4a5af`。

---

## 1. 问题清单

### High

**B2-H1　T2：已经过去的年份和当年都无法做首次维护（上线阻断）**
- **证据：**
  - `WorkCalendarYear.java:80-81`：只要日期早于今天就锁定，不管这一年以前有没有维护过（`unmaintained` 时 `storedRow == null`、`exceptions` 为空，所有过去的例外日都被算作「改动」）。
  - `WorkCalendarController.java:46` 写死传 `false`，HTTP 没有任何能放行的路径。
- **后果：**
  - 上线时录入 2026 年（年初的节假日已经过去）会返回 422；
  - D-1a-114 要求迁入的 2016–2026 年，经 API 也无法录入；
  - `WorkCalendarIT:185` 在保存前把时钟拨早，所以没有覆盖到这个情况。
- **理由：** 没维护过的年份上不会有已经算出的审批时效（计算本身会失败），锁定它没有意义。
- **建议：** 锁定只对「已维护过的年份」生效（`storedRow != null`）；补一条「首次维护过去年份成功」的测试。

**B2-H2　归档文件可能永久损坏，且一个出错的实例会卡住整个作业**
- **追加写 gzip：** `ProcessArchiveJob.java:156-171` 以 APPEND 方式往同一个文件里追加 gzip 成员。写到一半出错（磁盘满）或进程被杀时，会留下半截成员。下一次运行接着在它后面追加，然后删除引擎数据。之后 `zcat` 和 `GZIPInputStream` 读到半截处就会报 EOF 停下，后面已经从引擎删掉的实例实际上读不出来。
- **删除失败会死循环：**
  - `:107-108`：如果某个实例在 `deleteHistoricProcessInstance` 抛异常（或删完记录还在），整批作业失败。
  - 下次同一批仍排在最前，又追加一次、又失败，每天重复 100 条，归档永远停在这里。
  - `while(true)` 也没有「本批与上批相同就停」的保护。
- **测试缺口：** 没有任何「写盘失败，引擎里什么都不删」的测试。
- **建议：**
  - 每批写成一个不可变的独立文件或对象（临时文件 → fsync → 原子改名或上传 → 校验），不再追加到同一个文件；
  - 单个实例删除失败时跳过它并记录，不让整批失败；
  - 补写盘失败与删除失败两类测试。

**B2-H3　K6 外键会让整轮组织同步失败**
- **证据：** `V20261015010100__…add_snapshot_foreign_keys.sql:2-8` 给 `head_employee_id`、`regional_receivables_manager_id`、`parent_org_unit_id` 加了外键（DEFERRABLE），但对账代码没有对应的检查：
  - `SnapshotReconciler.java:313-331` 的 `pickSmallest` 不检查负责人是否在本次快照的人员里；
  - 上级部门也一样不查。
  - 对比之下，直属领导有 `SUPERVISOR_NOT_FOUND` 的 ISSUE 处理（`ReportingLines.java:27`）。
- **后果：**
  - A8 中只要有一条悬空引用，提交时外键就失败，整轮同步回滚，快照一直停在旧状态；
  - 已有数据的库在执行迁移、加约束时也会失败；
  - 违反「复刻老行为以生产数据为证据」：加外键前没有对生产数据做悬空引用计数。
- **建议：** 先补 `HEAD_NOT_FOUND` / `PARENT_NOT_FOUND` 两类 ISSUE，置空后继续同步；再用生产只读计数确认没有悬空引用。

**B2-H4　建库脚本没有给运行账号授权 `delivery_execution`**
- **证据：** `one_db_roles_schemas.sql:63` 说「脚本不为它建 schema，其迁移自行建」，但第 72 行的授权循环和第 89 行都没有它。
- **后果：**
  - 这个 schema 由迁移以 `one_dev_owner` 身份建出来，`one_dev_app` 没有 USAGE，也没有默认权限；
  - 工时单要经它的 api 读工作包，生产上会报 permission denied。
- **测试缺口：** `SchemaPlacementIT`、`MigrateOnlyIT` 都用超级用户，测不出来。
- **建议：**
  - 脚本里明确建 schema 并授权，或者在去留定下来之前把它从 `spring.flyway.schemas` 和运行时依赖里拿掉；
  - 补一个用真实角色执行的 IT：在测试容器里跑 `one_db_roles_schemas.sql`，以 app 角色启动应用，以 migrator 角色迁移。

**B2-H5　归档实现与用户新口径差距大**：明细见第 2 节。最关键的一点是，现在的设计是「按年追加到同一个文件，满 10 年由应用删除」，与 WORM 不兼容。

### Medium

**B2-M1　migrate 模式在 TLS 上默认放行**
- `MigrateOnlyListener.java:63` 配合 `DatabaseTransportRequirement.java:11-18`：只激活 `migrate` 时要求为 NONE，迁移账号的口令可以在不验证服务端证书的连接上发出去。RUNBOOK §7 只靠「记得写上 dev / prod」来保证。
- 建议：激活 `migrate` 但没有 `dev` / `prod` 时拒绝运行。

**B2-M2　手工构建的 Flyway 配置与 Spring 的配置分叉；dev 也没有挡住自迁移**
- 分叉：`MigrateOnlyListener.java:53-58` 只传了 6 项设置。测试走的是 Spring 自动配置，生产走这份手工配置，以后新增 `table`、`placeholders`、`out-of-order` 等设置时，两边会静默不一致。
- 失败日志：`:42` 只打印 `getMessage()`，丢掉了异常的 cause。
- dev 自迁移：`application-prod.properties` 只覆盖 prod。实际部署的是 dev 库，常驻服务不自迁移只靠环境变量 `SPRING_FLYWAY_ENABLED`，代码里没有任何保证。

**B2-M3　T2 的审计内容不完整**
- `AuditDomainEvents.java:36-38` 记入审计的只有事件类型、操作人和聚合键，没有被改的日期。
- `WorkCalendarLockedDaysChanged` 的载荷只有日期，没有改前、改后的日期类别。
- 事件登记表 30 天后归档，此后「改了哪天、从什么改成什么」就查不到了。RUNBOOK 写的「进审计，载荷列出被改的日期」与实际不符。
- 关于你问的「审计是否必然产生」：会产生。事件与日历在同一事务进登记表，之后由审计监听可靠写入；缺的是内容，不是记录本身。

**B2-M4　T2 的例外权限**
- 现在没有窗口：控制器写死 `false`。
- 风险在后续接线：应用层 `MaintainWorkCalendarYear` 收到 `lockedDaysConfirmed=true` 时不校验角色，B3 接线时如果只沿用 `work-calendar:update` 权限，就会出现「有更新权限就能改历史」。
- 建议：例外改为单独的应用服务方法，配独立权限（组织数据管理员），在应用层校验；这项缺口登记到注册表或 DEFERRED，负责人写 B3。

**B2-M5　版本表的保护可以绕过**
- 行级触发器（`V20261015010000…:17-35`）挡不住以下情况：
  - TRUNCATE；
  - 属主执行 `DISABLE TRIGGER`，或 `session_replication_role=replica`；
  - 插入一条一开始就是「已关闭」状态、或生效日倒签的新行。
- 运行角色仍然有 DELETE 权限（只靠触发器拦）。
- 同类的 `legal_entity_attribution_periods` 登记为 `DIMENSION_VERSIONED` + `REPLACE_SET`，实际是先删后插（`JdbcLegalEntityAttributionRepository.java:76`），守护 D1 只对 APPEND_ONLY 要求触发器，这里没有被拦。
- 建议：
  - 加 BEFORE TRUNCATE 语句级触发器；
  - 对运行角色 REVOKE 版本表的 DELETE 与 TRUNCATE；
  - 守护规定 DIMENSION_VERSIONED 必须是 APPEND_ONLY。

**B2-M6　DataArchitectureTest 可以被绕过，缺口可能变成永久豁免**
- **不写列名的缺口**：`DataArchitectureTest.java:422` 中不写 column 的缺口会豁免该表这条规则下的所有违规。
- **没有到期日**：缺口条目没有 due 字段。`audit_entries` 的缺口（`data-access-registry.yaml:188`）写的期限是 2029-10。
- **触发器只按名字认**：`:251` 只看触发器名字里有没有 `append_only`，一个什么都不做的同名触发器也能通过。
- **SQL 识别有漏洞**：`:541-545` 只识别写在字符串字面量里、带 schema 前缀的表名。不带 schema（依赖 search_path）或者表名放在常量里再拼接，都能绕过访问核对。
- **表的类别是自己报的**：把可变表登记成 SNAPSHOT，就能避开 D1。
- 建议：
  - 缺口加必填的 `due` 字段，并有过期即失败的检查；
  - 不写列名的缺口只允许用于 D3 / D4；
  - 凡含 DML 动词的 SQL 字面量必须带 schema 前缀；
  - 触发器改为按行为验证。

**B2-M7　作业的运行保障**
- 归档目录的可写性要到第 3 年第一次有数据可归档时才会暴露问题。
- 四个作业失败时都没有 `ALERT` 日志或指标。
- 建议：启动时和每天做一次写入探针，失败记 ALERT 并计数。

**B2-M8　K4 法人版本的生效日取的是系统当天**
- `JdbcLegalEntityRepository.java:51,80` 用 `zone.today(clock)` 作为生效日，不是业务生效日：停用或更名无法预约，也无法补录。
- 建议：如果规格要求「按生效日」，命令里要带生效日；否则在规则卡里写明「以记录日为准」。

**B2-M9　其他**
- **`consumed_events` 的保留理由不对**：90 天的理由写的是「长于事件归档的 30 天」，但重投风险来自没完成的事件发布，而它们不会被自动删除，也没有时间上限。应在规则卡里写明，并依赖状态转换本身的幂等。
- **T5 `separation_date` 无人认领**：没有交付，也没有按 intent 要求报给调度，DEFERRED 里也没登记。

### Nit

- **版本表注释过时**：`org_unit_versions` / `assignment_versions` 的表注释仍写「同一天的多次变化覆盖当天版本」（`V20261014215000…:68,98`），与 T1 现在的做法矛盾，需要追加一个改注释的迁移。
- **RUNBOOK §5.3 漏项**：表里漏了 sync purge、outbound purge 两个作业。§7 写「以 FLOWABLE_SCHEMA_UPDATE=true 启动常驻服务」，与 §2、§4「用 migrator 身份」的说法不一致。
- **术语未登记**：`revision_number`、`WorkCalendarLockedDaysChanged`、`lockedDaysConfirmed` 没有登记为术语，`proposed` 里只登记了错误码。
- **测试与代码放置位置**：
  - 生产配置的 TLS 测试写进了 identity 的 `ProductionConfigSanityTest`，属于越界放置，建议移到 integration 自己的测试里；
  - 数据库 TLS 守护放在 `platform.integration` 包下，它管的是主库连接，归属不太合适。
- **测试与校验细节**：
  - `WecomDeliveryIT` 把数组拼回 `|` 再断言，建议直接断言元素列表；
  - diff 保留期必须不长于 run 保留期，这一点没有校验；
  - `ProcessArchiveJob` 在行映射里嵌套查询（N+1，同时占两个连接）；
  - `lockAtMostFor=PT2H` 加上无上限循环，多节点时会并发追加同一个文件。
- **迁移版本号**：用了未来的时间戳 V20261015010000–010800。并行批次整合时要保证这一段排在最后，否则已经迁移过的库会因 outOfOrder 校验失败。

### 越界检查（第 6 项）

- 主代码只改了 B2 范围：attendance、workforce infra、approval 的 archive、integration infra、timesheet infra。没有动 platform.web、kernel、approval 领域、identity 主代码。
- `WorkCalendarController` 只改了一行（传 `false`），合理。
- 测试中改了 `EventSequenceContinuityTest`，只是签名随之变化，断言没改。
- 把测试写进 identity 的测试文件，见 Nit。
- `ProcessArchiveJob` 在 `platform.approval.infrastructure` 下，符合 `ArchitectureRulesTest:213-218`；用 JDBC 读 `ACT_HI_*` 不违反约束，但依赖了引擎内部表结构。

### 数组列迁移（第 5 项）

转换正确。`array_remove(string_to_array(…,'|'),'')` 与原来的分割和过滤语义一致。`changed_field_codes` 原来为 null 的仍为 null。

---

## 2. 归档实现与用户 OSS 口径的差距（单列）

| 用户要求 | B2 现状 | 差距 |
|---|---|---|
| 与 ECS 同地域的 OSS bucket，归档存储类型，WORM 合规保留满 10 年 | 只写本机 `/data/forcitis-one/archive/process/`；OSS 同步在 RUNBOOK §5.3 写的是「提议」 | 没有 OSS 客户端、上传、存储类型与 WORM 设置；按年追加同一个对象与 WORM 不兼容，要改成按批写不可变对象 |
| 本机数据盘只保留最近一份 | 本机按年留文件，满 10 年才由应用删（`ProcessArchiveJob.java:113-129`） | 应在 OSS 上传并校验成功后再删本机文件；10 年到期由 WORM 或生命周期规则处理，应用不删 |
| 审计 3 年归档作业本期实现 | 没有实现；注册表登记为缺口，期限 2029-10（`data-access-registry.yaml:188`） | 整个作业缺失，缺口要改成本期交付 |
| 含个人信息，不得放法兰克福 | 地域不可配置；归档含发起人、办理人 ID（个人信息），也没有在数据分级里登记 | 地域配置加白名单校验（只允许国内地域，拒绝 eu-central-1）；归档登记为 SENSITIVE；建议开启服务端加密 |
| bucket、地域、前缀可配置，生产缺配置时启动失败或作业告警 | 只有 `directory` 一项（缺失时启动失败） | 这三项配置及缺失时的处理都没有 |
| 写对象带校验（Content-MD5 / SHA256 元数据） | 只有本机 fsync | 上传前计算校验值、上传后核对；引擎删除的前提应是「OSS 已确认」，而不是「本机已写入」 |

另外：调用 OSS 属于对外调用，按 CLAUDE.md 要先经用户确认；bucket 名和账号不能写进仓库。

## 3. 只含 ID、时间、办理人，能否满足 10 年追溯

**不能单独满足。** 能回答「谁在什么时候办了哪个节点」，回答不了「为什么由他办、他的结论是什么、按哪个规则版本走的」。具体缺：

1. **流程定义的版本与部署 ID**：现在只有 key（`:95`），无法知道当时生效的是哪一版 BPMN / DMN。这违反 §12.2「配置按版本、不追溯」。
2. **候选人与权限链 `ACT_HI_IDENTITYLINK`**：包括谁是候选人、升级时追加的候选人（`TaskDeadline` 里的 `addCandidates`）、转交关系。
3. **任务本地变量**：`systemComplete`（系统自动通过还是人工通过）、`advisory`、`assignedAtMillis`。
4. **路由输入与内容摘要**：DMN 的输入变量、`contentDigest`、`legalEntityId`、`submittedOn`。这些只在引擎变量里。
5. **每个任务的结论与理由**：引擎里本来就没有。`approval_tasks` 只有状态 COMPLETED，结论原因只在请求级的 `result_reason` 上。逐个任务的通过、退回、驳回只能靠审计，而审计归档至今没有实现。
6. **DMN 决策历史**（`ACT_DMN_HI_*`，如果开启了）：既没归档也没清理。

**建议：**
- 归档里补第 1–4 项，变量按白名单只取不含个人内容的路由输入和摘要；
- 在 `approval_tasks` 上加任务级结论列，与 K5 的 `claimed_at` / `completed_at` 一起做；
- 写明 10 年追溯的依据是「审批表（业务记录）+ 审计（审批结论 30 年）+ 引擎归档」三者合起来；
- 把审计归档作业做在本期。
- 不包含变量这个取舍，需要用户确认。

---

## 4. 整合前必须修

1. **B2-H1**：首次维护不受锁定限制，补对应测试。
2. **B2-H2**：归档改为按批写不可变文件或对象，校验后再删引擎数据；单个实例删除失败不阻塞整批，没有进展时停止；补写盘失败与删除失败的测试。
3. **B2-H3**：负责人、区域应收负责人、上级部门加悬空引用的 ISSUE 处理；加外键前用生产只读计数取证。
4. **B2-H4**：建库脚本补齐 `delivery_execution`（或撤出），补一个按真实角色执行的 IT。
5. **B2-M1**：只有 `migrate`、没有 dev / prod 时拒绝运行。
6. **B2-M3**：审计或事件载荷带上被改的日期和改前、改后的类别；RUNBOOK 的描述与实现对齐。
7. **B2-M5**：版本表拦 TRUNCATE，REVOKE 运行角色的 DELETE；守护规定 DIMENSION_VERSIONED 必须是 APPEND_ONLY。
8. **B2-M6**：缺口加 `due` 字段，并有过期即失败的检查。
9. **OSS 与审计归档**：OSS 口径（第 2 节）和审计 3 年归档作业，作为 B2 的追加工作在本期完成。如果要先整合 B2，有两个前提：
   - 先把引擎删除挂到「OSS 已确认」这一步上，在此之前不删；
   - 用户原话先用 `new_intent.py` 入库。
10. **第 3 节的取舍**：归档字段的增补（第 3 节 1–4 项）和「不含变量」这个取舍，提交给用户决定。

**放在后续、但须登记负责人的：** B2-M4（例外权限，B3）、B2-M8、B2-M9（T5 与 consumed_events）、全部 Nit。

---

## 第 2 轮

# B2 修复复核（fbff950）：仍需修

复核范围是 90028a8..fbff950 共 7 个提交，HEAD 为 fbff950。只读，没有改任何文件。

**结论：仍需修。** 上一轮的各个 High 和多数 Medium 已修好，修法总体扎实：
- 归档先写入、核对通过后才删除；
- 审计归档只取连续前缀；
- 触发器改为按实际行为检查；
- migrate 模式拒绝不带环境 profile 的启动。

但还有 6 项要在整合前处理（第 5 节）：
- 1 项上轮的问题没修：H4；
- 3 项是新问题：白名单变量名对不上、审计归档缺两列、指标重复计数；
- 2 项是你后来下达的口径带出来的：RUNBOOK 里「人工延长到 30 年」的设计要删；WORM 天数怎么算须用户确认，桶一旦锁定就改不回来。

**全量 verify：** `./mvnw -B verify` 退出码 0，用时 2:47。surefire 729 / 0 / 19 跳过，failsafe 224 / 0。19 个跳过仍是原有的两个 Pending 测试。

**更正上轮一处：** 上轮 H3 说「负责人和区域应收负责人没有存在性检查」不准确。原代码在 `SnapshotReconciler.java:258-260` 已经有 `HEAD_NOT_FOUND` 和 `REGIONAL_MANAGER_NOT_FOUND`，真正缺的只有上级部门，这次已补。

---

## 1. 上轮各项的状态

| 项 | 状态 | 依据 |
|---|---|---|
| H1 首次维护被锁 | 已修 | `WorkCalendarYear` 中，只有维护过的年份（`storedRow != null`）才锁定，并补了测试 |
| H2 归档损坏、死循环 | 已修 | 每批写一个不可变对象，OSS 确认后才删引擎数据；引擎删不掉的实例跳过并计数；连续两批相同就停（`ProcessArchiveJob.java:144-161`）；写盘、上传、校验三种失败都有测试 |
| H3 悬空引用 | 已修 | 加了 `PARENT_NOT_FOUND`（`SnapshotReconciler.java:245`）；生产取证 0 条记录在 DATA-RULE-05 |
| **H4 `delivery_execution` 授权** | **未修** | `one_db_roles_schemas.sql:63`、`:72` 没有变化；`application.properties:15` 仍列着这个 schema；没有用真实角色执行的 IT |
| M1 只带 migrate 时跳过 TLS 检查 | 已修 | `MigrateOnlyListener.java:54` |
| M2 Flyway 配置分叉、dev 自迁移 | 已修 | 改用 `FlywayProperties` 绑定；加了 `application-dev.properties`；失败日志带上了异常 |
| M3 审计内容 | 已修 | 载荷里有改前、改后；`AuditDetail` 把它写进审计的 detail |
| M4 例外权限 | 已修 | 新增独立用例 `MaintainLockedWorkCalendarDays`，应用层 `@PreAuthorize`；还没有 HTTP 入口，授权给角色要等 B1 |
| M5 版本表 | 已修 | TRUNCATE 触发器；法人归属期间表也改为只追加；建库脚本 §4b 的 REVOKE 清单与注册表由守护核对一致 |
| M6 守护 | 已修（剩一处 Nit） | 缺口必须带到期日；不写列名的缺口只允许 D3 / D4；触发器按行为验证；写入类 SQL 必须带 schema。仍挡不住表名靠字符串拼接的写法 |
| M7 作业保障 | 已修 | 启动时和每天各探一次，记 ALERT 并计数 |
| M8 法人版本生效日 | 已修（文档） | DATA-RULE-03 写明以记录日为准 |
| M9 consumed_events | 已修 | DATA-RULE-04 写明幂等依赖状态转换 |
| M9 T5 `separation_date` | 未修 | 仍然无人认领，DEFERRED 里也没登记 |
| Nit：表注释、RUNBOOK 漏项、分页与 N+1、diff 不长于 run 的校验、测试移位、WecomDeliveryIT | 已修 | 测试移到了 `ProductionTransportConfigTest`；查询改成批量 IN |
| Nit：术语 | 已修，带出一个新 Nit | `locked_days_confirmed` 登记成了术语，但这个参数已经删掉 |
| Nit：`lockAtMostFor=PT2H` 配无上限循环；迁移版本号用了未来时间戳 | 未修 | 都属于整合时协调的事 |

## 2. 新问题

**N1（整合前修）白名单变量名与引擎不一致**
- `ProcessArchiveJob.java:47` 写的是 `assignedAt`，引擎实际的变量名是 `assignedAtMillis`（`FlowableApprovalEngine.java:61`）。分配时刻因此永远不会被归档。
- `ProcessArchivePolicyTest` 只比较白名单文件和作业里的常量，没有和引擎常量比，所以测不出来。
- 建议：白名单直接引用 `FlowableApprovalEngine.VARIABLE_*`，测试改为核对引擎常量。

**N2（整合前修）审计归档缺两列**
- `ArchivedEntry`（`AuditArchiveJob.java:49`）没有 `event_id` 和 `correlation_id`，归档后无法再关联到事件和调用链。

**N3（整合前修）失败指标重复计数**
- B2 的 `JobFailureAspect` 和 B2b 的 `ScheduledJobMetrics` 都对 `@SchedulerLock` 方法递增 `forcitis.platform.scheduling.job.failures{job}`，一次失败会计两次。
- **建议保留 B2b 的版本**，原因有三：
  - 它是最外层的 advisor（`HIGHEST_PRECEDENCE`），拿不到锁也会计数；
  - 启动时把每个作业的计数器预注册为 0，第一次失败就能在告警里看到；
  - 计数器经 ObjectProvider 延迟获取，不会提前创建。
- 做法：删掉 B2 的 `JobFailureAspect` 及其测试，把 B2 那行 ALERT 日志移进 B2b 的拦截器。RUNBOOK §5.3 中的指标名不用改。

**N4（可后续）删不掉的实例会天天写进 WORM**
- 被跳过的实例下次运行会再归档一遍。如果它永远删不掉，每天都会多一个 WORM 对象，10 年内无法删除，还要一直付存储费。
- 建议：把跳过的实例 ID 持久化，之后不再重复归档；或者只记录一次再告警。

**N5（可后续）法人归属聚合的行为变了**
- `LegalEntityAttribution.java:84-87` 改为拒绝缩短已关闭的期间，而且没有提供更正路径。
- 这是因为触发器不允许改已关闭的行而被迫做的规则改动。按 §12.2，应先写规则卡、由负责人确认，再改测试和实现。

**N6（可后续）跨范围修改**
- 改了 identity 的 `Permission` 枚举，新增了 `platform.audit.api.AuditDetail`。由此 attendance 的事件依赖了审计模块，上游依赖了下游。
- 建议：`AuditDetail` 移到 kernel；这两处改动要告知 B1 和审计的负责人。

**N7（Nit）**
- 缺口的到期日按 `LocalDate.now()` 判断：2026-11-30 起，A / B1 的缺口还没修完的话，main 的 CI 会失败。这是有意为之，但要提前告知 A / B1。
- RUNBOOK 把归档 AccessKey 的轮换写进了环境变量表，应移到 §5.2。

## 3. OSS 实现的安全性

| 检查点 | 判断 |
|---|---|
| 凭据来源 | 是 RAM 子账号的长期 AccessKey，从环境变量读，不进仓库。**建议**（可后续）：<br>• 改用 ECS 实例的 RAM 角色（STS 临时凭证），不再持有长期密钥；<br>• `ArchiveStoreProperties` 是 record，自动生成的 `toString` 会打印出 `accessKeySecret`，需要覆写并做掩码。 |
| RAM 授权 | RUNBOOK 写明不授予删除权限，正确。建议把策略写成显式 Deny `oss:DeleteObject*`，并限定在该前缀下。 |
| 地域白名单 | 有效：只接受国内地域，拒绝法兰克福和香港；endpoint 由地域拼出；探针核对 bucket 实际所在地域。 |
| 用分片上传后中止做探针 | 可行。WORM 桶允许发起和中止分片上传（中止的是未完成的上传，不算删除对象），不会留下对象。前提是 RAM 授予 `InitiateMultipartUpload` 与 `AbortMultipartUpload`。中止失败时只剩一个零分片的空上传，影响可以忽略。 |
| 读回核对 | 有效，但偏弱。Archive 类型的对象不解冻读不出内容，只能核对元数据，这一点合理。上传时 Content-MD5 由 OSS 服务端校验，完整性有保证。不过读回的 `sha256` 元数据是写入方自己填的，这一步只证明对象存在、大小一致。**建议**：再比较 ETag（简单上传时 ETag 就是服务端算出的 MD5）与本地 MD5。 |
| 删除只在 OSS 确认之后 | 满足：<br>• 流程归档：`vault.archive` 返回后才调用引擎删除，任何失败都在删除之前抛出；<br>• 审计：在线行一律不删。 |
| 对象命名 | 不泄露个人信息：`<前缀>/<类别>/<年>/<类别>-<UTC 时间>-<sha256 前 12 位>.ndjson.gz`。 |
| 加密 | 没有设置服务端加密，探针也不检查。**建议**（可后续）：bucket 默认开启 SSE-KMS，由探针核对。 |

## 4. 审计归档作业

- **只归档连续前缀：满足。** `AuditArchiveJob.java:100-121` 从进度点之后按 id 取一批，只归档开头那段「全部已满 3 年」的条目。用 id 当进度，正在提交、尚不可见的新行不会被跳过：只有 3 年前的旧行才会进归档，而新插入的行 id 都比它们大。有测试覆盖。
- **批次表只追加：满足。** 有行级 UPDATE / DELETE 触发器和 TRUNCATE 触发器，有测试；`last_entry_id` 有唯一约束，防止进度重复写入。
- **在线行不删：满足。**
- **小问题：**
  - 归档成功、写批次行失败时，下次会再写一个 WORM 对象，读取方按 id 去重即可；
  - 注册表把 `audit_entries` 的保留期写成 `3y`，但行永远不删，容易让人误读，建议改成「3y 后归档，在线永久」的写法；
  - 缺 `event_id` / `correlation_id`，见 N2。

## 5. 30 年口径（按最新口径：单一 10 年 bucket）

- **代码：** 没有 30 年相关逻辑。探针只核对「WORM 已锁定，且天数不少于 `ARCHIVE_STORE_WORM_LOCK_DAYS`」。
- **待修：** `INFRA_RUNBOOK.md:163` 仍写着「审计要满 30 年……建 bucket 时按 10 年锁定，审计前缀的保留期在到期前延长到 30 年……由用户在 OSS 控制台办理」，并说与 GUIDING_PRINCIPLES 不一致。需要按用户决定改写为：
  - 单一 10 年 bucket，流程归档与审计归档共用；
  - 过账类 30 年的存放见 DEFERRED。
  
  这一句里还写着「审计另按会计档案期限」，也要一起删掉。
- **须用户确认（不可逆）：「满 10 年」从哪天算。**
  - GUIDING_PRINCIPLES 的原文是「在线 3 年，之后归档，保留至满 10 年」，按字面是从数据产生起算满 10 年。
  - OSS 的 WORM 从对象写入时起算。按 RUNBOOK 现在的写法（按 10 年锁定，即 3650 天），数据实际会保留约 13 年，而且锁定期间删不掉。个人信息多存 3 年，与最小必要原则相冲突。
  - 在锁桶之前请用户二选一：
    - WORM 约 7 年（2557 天），合计约满 10 年；
    - WORM 10 年，接受合计约 13 年。
  - 另外，RUNBOOK 要写明到期删除用的生命周期规则（保留期满后过期删除）。现在只写了「由 WORM 与生命周期规则处理」，没写规则本身。

## 6. 归档内容白名单

- **基本满足用户的「补全关键信息，变量只取白名单」：**
  - 已归档：流程定义版本与部署 ID、身份关联（候选人、转交、升级时追加的人）、任务级的系统通过与仅建议标记、`contentDigest`、路由结果 `routed_*`；
  - 白名单之外的变量不读取（`ProcessArchiveJob.java:198-205` 的 SQL 只取白名单里的名字）。
- **问题与缺口：**
  - **名字对不上**：见 N1，`assignedAt` 实际上取不到。
  - **新增变量没有被强制显式分类**：精确名字由测试强制登记，但 `routed_*` 是按前缀匹配。任何业务模块在启动变量里传一个 `routed_xxx`，都会自动进归档，不经过分类。建议：
    - 限定为布尔类型（`var_type_ = 'boolean'`）；
    - 在 `EngineVariables` 层拒绝业务模块传入以 `routed_` 开头的变量名，因为这个前缀只由 `NodeDecisions` 生成。
  - **仍然缺的内容**：
    - DMN 决策表的版本：如果决策表与流程不在同一个部署里，现在归档的部署 ID 不能说明当时用的是哪一版；
    - `legalEntityId` / `submittedOn`；
    - 任务级的结论与理由：RUNBOOK 说「由审批的只追加办理记录表负责」，但这张表（D-1a-90 / 166）还没建。建表时要把它的保留期定为不少于 10 年，或者纳入归档。

---

## 7. 仍需修清单

**整合前必须修：**
1. **H4**：建库脚本补 `delivery_execution` 的建库与授权（或把它从 schemas 和运行时依赖中撤出），并补一个用真实角色（app / migrator）执行的 IT。
2. **N1**：白名单改为引用引擎常量，测试核对引擎常量（修正 `assignedAt`）。
3. **N2**：审计归档补上 `event_id` 与 `correlation_id`。
4. **N3**：删除 B2 的 `JobFailureAspect`，保留 B2b 的 `ScheduledJobMetrics`，并把 ALERT 日志并入后者。
5. **RUNBOOK §5.3（`INFRA_RUNBOOK.md:163`）**：删除「到期前人工延长到 30 年」，改写为单一 10 年 bucket；写明生命周期删除规则。
6. **WORM 天数**：桶创建与锁定之前，请用户确认「满 10 年」从哪天算（约 7 年或 10 年 WORM）；结论用 `new_intent.py` 入库。

**可整合后跟进（须登记负责人与到期日）：**
- 存储与安全：
  - N4：持久化跳过的实例，避免每天重复写入 WORM；
  - 用 ECS RAM 角色代替长期 AccessKey，`ArchiveStoreProperties` 的 `toString` 做掩码；
  - 读回时比较 ETag；
  - bucket 开 SSE-KMS，探针核对。
- 白名单与归档内容：
  - `routed_*` 限定为布尔，并拒绝业务模块传入这个前缀；
  - 补 DMN 版本、`legalEntityId`；
  - 建任务办理记录表并定保留期。
- 规则与范围：
  - N5：法人归属行为变化补规则卡，由负责人确认；
  - N6：`AuditDetail` 移到 kernel，通知 B1 和审计负责人；
  - T5 `separation_date` 认领或登记 DEFERRED。
- 小项：删除 `locked_days_confirmed` 术语、把 AccessKey 轮换移到 RUNBOOK §5.2；整合时协调迁移版本号的顺序和缺口到期日。

---

## 第 3 轮

# B2 第三轮复核（b8e94cf）：仍需修，还有 3 项

复核范围是 fbff950..b8e94cf 共 7 个提交（含 55d2030），HEAD 为 b8e94cf。只读，没有改任何文件。

**全量 verify：** `./mvnw -B verify` 退出码 0，用时 2:40。surefire 736 / 0 / 19 跳过，failsafe 227 / 0。19 个跳过仍是原有的两个 Pending 测试；`RoleSeparatedDeploymentIT` 2 / 0。

**结论：仍需修，只剩 3 项，都不大：**
1. H4 仍然没修，而且新加的 IT 也测不出来；
2. 探针核对 WORM 天数用的是「不少于」，桶上锁多了天数照样通过，用户要求的「在线 + WORM = 10 年」只在配置上校验了，桶本身没校验；
3. 审计归档用 `select *`，以后新加的列不经分级就会自动进入不可删除的归档。

这 3 项修完后可以整合。另有 N5 须负责人确认，但不挡整合。

---

## 1. 上轮「整合前必须修」1–6 项

| # | 项 | 状态 | 依据 |
|---|---|---|---|
| 1 | H4：`delivery_execution` 授权 | **未修** | 详见下文 |
| 2 | N1：白名单变量名 | 已修 | 白名单直接引用 `FlowableApprovalEngine.VARIABLE_*` / `NodeDecisions.ROUTED`，`assignedAtMillis` 已更正 |
| 3 | N2：审计归档缺 `event_id` / `correlation_id` | 已修，但修法有新问题 | 改成了 `select *`，见第 3 节 |
| 4 | N3：失败指标重复计数 | 已修 | `JobFailureAspect` 及其测试已删除，改用 B2b 的 `ScheduledJobMetrics` |
| 5 | RUNBOOK 中人工延长到 30 年 | 已修 | §5.3 改为单一 bucket、2557 天，写了生命周期规则，过账 30 年指向 DEFERRED；全仓库已没有「到期前人工延长到 30 年」的设计 |
| 6 | WORM 天数 | 配置已修，桶的核对是新问题 | 2557 天写进配置，`KeepPeriodRule` 在启动时校验「在线 + WORM = total-keep-years」。但探针只核对桶上的天数不少于 2557，见第 3 节 |

**H4 的情况：**
- `one_db_roles_schemas.sql:63` 仍然不为 `delivery_execution` 建 schema，第 72 行的授权循环里也没有它；`application.properties:15` 照旧列着这个 schema。
- 运行代码要读 `delivery_execution.work_packages`（`JdbcWorkPackageRepository.java:24`）。按脚本部署后，`one_dev_app` 对这个 schema 没有 USAGE，工时单一碰工作包就会报 permission denied。
- 新加的 `RoleSeparatedDeploymentIT` 冒烟只做了法人的写和读，没碰工作包，所以测不出来。
- 修法：脚本建这个 schema 并把它加进授权循环（或者整体撤出）；IT 里再加一步用运行账号读工作包。

## 2. 顺带做的跟进项

| 项 | 状态 | 说明 |
|---|---|---|
| N4 跳过表 | 已修 | 跳过表只追加，有 UPDATE / DELETE / TRUNCATE 触发器；查询排除已跳过的实例，不会重复归档；每次运行结束提醒仍在引擎里的；连续两批相同就停的保护还在 |
| toString 掩码 | 已修 | 有测试 |
| ETag 比对 | 已修 | 本地 MD5 改为十六进制，与 ETag 比较；Content-MD5 仍发 Base64；有测试 |
| `routed_*` 限定布尔 | 已修 | SQL 加了 `var_type_ = 'boolean'`；`EngineVariables` 拒绝业务模块传入 `routed_` 开头的变量，有测试 |
| 白名单补字段 | 已修 | 补了 `legalEntityId`、`submittedOn`<br>Nit：`ProcessArchivePolicyTest` 只把作业里的 PROCESS 列表和期望值比，TASK 列表和 `routed_` 前缀只和白名单文件比。作业里删掉一个任务变量，测试不会失败。建议三组都和作业常量比。 |
| 凭证三种方式 | 已修 | 优先级：AccessKey 对 > ECS RAM 角色 > 环境变量链。只给半对 AccessKey 时启动失败；什么都不给时，环境变量链在启动时找不到就失败；RAM 角色的凭证要到使用时才取，取不到由探针报 ALERT。RUNBOOK 写了显式 Deny 删除的 RAM 策略草稿。 |
| RoleSeparatedDeploymentIT | 已修，但漏测 H4 | 测试流程合理：用真实脚本建库，migrator 迁移，带 TLS（`sslmode=require`）安装引擎，再用 app 角色冒烟，并验证 app 不能建表、删版本表、TRUNCATE、改审计；口令都在运行时生成。问题：冒烟没覆盖 `delivery_execution`，所以 H4 漏掉了。 |
| 错误码统一为 `LOCKED_DAY_UNCONFIRMED` | 已修 | 与 B3 一致；术语表已同步，过时的 `locked_days_confirmed` 已删 |

## 3. 新问题

**N8（整合前修）探针只要求桶上的 WORM 天数不少于配置**
- `OssObjectStore.java:81` 的判断是 `getRetentionPeriodInDays() < wormLockDays` 才报错。
- 后果：桶如果被锁成 3650 天，探针照样通过，数据实际保留约 13 年。用户要求「在线 + WORM = 10 年」，现在只校验了配置，没校验桶本身。
- 修法：改为必须相等；同时把 DATA-RULE-08 里「探针核对不少于配置值」改为「等于」。
- 如果以后经决定延长 WORM，需要同时改 `worm-lock-days` 和 `total-keep-years`，这正是这条校验要守住的。

**N9（整合前修）`select *` 会自动归档以后新加的敏感列**，详见第 4 节。

**N10（Nit）** 注册表里的 `optional: true` 没有到期日。三个法人归属表的条目在整合后要删掉这个标记（脚本注释里已写），建议像缺口一样加 `due`，否则可能变成永久豁免。

## 4. AuditArchiveJob 改成 `select *` 是否稳妥

**不稳妥，建议改。**

- **列顺序：** 结果按表的物理列顺序放进 LinkedHashMap，读取方按列名取值，顺序本身不影响正确性。
- **类型能否序列化：** 现有的 10 列都没问题：数字、时间转成 ISO 字符串、UUID 转成字符串。但 `default -> value.toString()` 对 `bytea`（`byte[]`）会输出 `[B@…` 这样的无意义字符串；对 jsonb 会变成字符串而不是嵌套对象。现在没有这类列，以后加了就会悄悄输出错误数据。
- **以后加敏感列会不会被直接归档：会。** 这是主要问题。审计表以后可能加 IP、User-Agent、请求载荷之类的列，`select *` 会让它们不经任何分级审查，就写进 10 年内删不掉的 WORM 对象。归档里一旦有了不该有的个人信息，就无法撤回。
- **是否需要排除 RESTRICTED 列：需要，而且要做成显式决定。** 建议：
  - 改回显式列清单（比如 `architecture/archive-columns.txt`，与 `archive-variables.txt` 同一种做法，每列写分级和理由）；
  - 加一个守护测试：`audit_entries` 的每一列都必须在清单里明确标为「归档」或「排除」；`data-classification.txt` 里标为 RESTRICTED 的列不得标为归档，除非另有用户决定；
  - 这样以后加列时测试会失败，逼当事人做出决定。这就是「自动带上」想要的效果，只是把自动包含换成了自动提醒。
  - 未知类型（`byte[]` 等）直接报错，不要 `toString`。

## 5. 迁移 011000、011100 与建库脚本 §4b 的条件式写法

**作为整合前的过渡写法可以接受，整合后应改回无条件的写法。**

- **为什么现在行得通：**
  - B3 是直接在未合并的 `V20261014215000` 里建拆开后的两张表，版本号比 011000 早，所以整合后三张表要么在 011000 执行之前已经存在，要么根本不存在，条件判断的结果是确定的；
  - 注册表把它们标为 DIMENSION_VERSIONED + APPEND_ONLY，DataArchitectureTest 会实际执行检查触发器是否生效。万一哪张表存在却没加上触发器，测试会失败，不会被静默放过；
  - 这些迁移都还没在任何环境执行过（main 上没有，RUNBOOK 写的是未执行），这次修改不违反「Flyway 只追加」。
- **为什么不能长期保留：**
  - 一份迁移在不同的库上结果不同，只取决于那个库当时的表结构，迁移因此失去确定性；
  - 表名写错时会静默跳过，只能靠守护兜底；
  - §4b 同理。
- **建议：** 整合 B2 与 B3 时，把 011000、011100 和 §4b 改写成只针对最终表名的普通 DDL；删掉旧表名和注册表里的 `optional` 条目；在整合 PR 里注明「这些迁移未在任何环境执行过，因此修改而非追加」。这个改动必须在第一次部署之前完成。部署之后再改，就只能追加新迁移了。

## 6. N5：法人归属拒绝缩短已关闭的期间

**需要负责人确认。**
- 这是 organization.workforce 法人归属（ADR-0026）业务规则的收紧，按 §12.2 属于代码类规则改动，应先有规则评审结论再改实现。
- 归属表现在由 B3 拆分维护，负责人应是 B3；法人归属由组织数据管理员维护（D-1a-26），是否接受「结束得过晚无法更正」需要用户拍板。
- DATA-RULE-07 已标「需负责人确认」，但 RULE_REVIEWS 里还没有对应的结论。这条在 1a 上线前定下即可，不挡这次整合。

**可行的更正路径（都保持只追加）：**
- **(a) 记录日维度（推荐）：** 归属期间表加 `recorded_at`，查询时，对同一个日期，取「记录日不晚于查询时点」的行里最新的一行。
  - 更正「结束得过晚」：追加一行，结束日改对，记录日为更正时刻；原行保留。
  - 优点：完全只追加；还能回答「某天按当时掌握的信息，法人是谁」，正好对应「已算出的结果不重算」（单据上已经写入的法人不变）。
  - 代价：排他约束要按记录维度重新设计（例如同一记录版本内期间不重叠），查询会复杂一些。
- **(b) 更正记录表（简单）：** 新建只追加的 `*_legal_entity_corrections` 表，记被更正的期间、新的结束日、原因、操作人。
  - 解析时先看更正记录；更正操作单独开用例、单独授权（组织数据管理员），并发出 `LegalEntityAttributionCorrected` 事件进审计。
  - 优点：改动小；代价：解析逻辑要合并两张表。
- 两种做法都要写明：更正不追溯已经写入单据的法人（与 T2「已算出的不重算」一致）；需要改单据法人的，走冲销重做（§12.3）。

---

## 7. 仍需修清单

**整合前必须修：**
1. **H4**：建库脚本建 `delivery_execution` schema 并加进运行角色的授权（或整体撤出）；`RoleSeparatedDeploymentIT` 加一步用运行账号读工作包。
2. **N8**：探针改为桶上的 WORM 天数必须等于配置值，DATA-RULE-08 同步改。
3. **N9**：`AuditArchiveJob` 改用显式列清单加分级守护，排除 RESTRICTED 列；未知类型报错，不再 `toString`。

**整合时一并做：**
- 011000、011100 和 §4b 改写为针对最终表名的无条件写法；删掉 `optional` 条目；在 PR 里注明「这些迁移未在任何环境执行过」。

**不挡整合，须登记：**
- N5：规则评审结论由 B3 和用户确认，从第 6 节的 (a) 或 (b) 中选一种更正路径，1a 上线前定下。
- Nit：`ProcessArchivePolicyTest` 的三组变量都与作业常量比对。
- 上轮遗留：T5 `separation_date` 的认领；N6（`AuditDetail` 移到 kernel，通知相关负责人）；迁移版本号的顺序与缺口到期日的协调。
