# 错题本：one-quality

前缀：`QUAL`。格式见 [README.md](README.md)。起步条目取自 2026-10-15/16 本会话的实际问题。

### QUAL-001　断言漂移检查对中文路径和整删的测试文件漏报

- **日期**：2026-10-15
- **现象**：中文路径的测试文件被删断言时 `assertion_drift.py` 不报警；整个被删的测试文件统计不到（派工时由调度指出）。
- **根因**：`git diff` 没关 `core.quotepath`，中文路径被转义成带引号的八进制串，`+++ b/` 前缀对不上被跳过；删除文件的头是 `+++ /dev/null`，旧代码只认 `+++ b/`。
- **拦下于**：调度（派工时指出，参照 #220）。
- **漏过于**：机械检查——这个检查脚本自己没有自测；同类的 quotepath 问题 #220 只修了 `relevant_change.py`。
- **新增检查项**：`scripts/ci/assertion_drift.py --self-test`（中文路径、整删文件、内容以 `--` 开头三种），接进 `ci.yml` 与 `precheck.sh`（#243）。
- **状态**：已落实

### QUAL-002　引号路径静默跳过；自测里一条断言恒为真

- **日期**：2026-10-15
- **现象**：含 `"` 或 `\` 的路径 git 仍会加引号，被静默跳过；自测的 `kept` 断言在文件没改动时恒成立，起不到检查作用。
- **根因**：修 QUAL-001 时只处理了 `core.quotePath`，没处理 git 对引号和反斜杠的转义；自测断言写的是「没有出现」，没验证「被读到」。
- **拦下于**：独立评审——opus 对 #243 的首轮复审（调度转述三条建议）。
- **漏过于**：作者自审（自测写完没做破坏验证）。
- **新增检查项**：`unquote()` 与引号路径用例；`kept` 文件真实新增断言并要求「被读到且不报」；自测临时仓库关 `commit.gpgsign`（#268）。**此后凡新增自测，先让被测逻辑失效，确认自测会红。**
- **状态**：已落实

### QUAL-003　共享 PostgreSQL 的模板库失败后无法重试；库名可能撞名

- **日期**：2026-10-15
- **现象**：模板迁移失败后，下一个类报 `already exists`，真正原因被盖住；库名只用 `hashCode` 8 位十六进制。
- **根因**：建模板库在 `computeIfAbsent` 里，失败后库已建但没记进缓存；库名只取哈希，没带可读的类名，也没保证清洗后不撞。
- **拦下于**：独立评审——opus 对 #246 的复审，及 sonnet 对补充提交的复核（见 #268 评论）。
- **漏过于**：作者自审。
- **新增检查项**：`DROP DATABASE IF EXISTS … WITH (FORCE)` 后再建；库名为可读类名加哈希后缀；`TestPostgresNameTest`（63 字节边界与撞名用例）。
- **状态**：已落实

### QUAL-004　Colima 试点前没核对镜像架构，one-4 上三次后端作业失败

- **日期**：2026-10-16
- **现象**：one-4 接 Colima 后，需要 SQL Server 容器的测试全部超时（运行 38811435140、38811806199、38813388161）；一度也怀疑交换区。
- **根因**：Colima 虚拟机是 aarch64，SQL Server 2022 镜像只有 linux/amd64，这台机器没装 Rosetta；试点前没做兼容性预检。回退判据最初用交换量累计增量，太粗。
- **拦下于**：作者自查——巡检里读 one-4 失败作业日志发现，按规则回退。
- **漏过于**：试点没有出题模板可用，方案评审阶段没有覆盖架构兼容性。
- **新增检查项**：画像「工作守则」的兼容性预检清单（镜像架构、依赖前提、回退判据用速率、对其他 runner 的影响）；评审模板 `review-briefs/04` P8、通用盲区 B3。
- **状态**：待落实（本 PR 合入后改为已落实）

### QUAL-005　precheck 跑后端测试类时连带触发 OpenAPI 导出（起容器、改写 openapi.json）

- **日期**：2026-10-16
- **现象**：`./mvnw test -Dtest=…` 会跑 pom 里绑在 `process-test-classes` 的 export-openapi 执行：`OpenApiExportTest` 起 PostgreSQL 容器并改写 `frontend/packages/api/openapi.json`；说明里却写「这几个测试不起容器」。
- **根因**：以为 `-Dtest` 能挡住别的执行；没看 pom 的执行绑定。
- **拦下于**：独立评审——opus 对 #298 的首轮复审（Blocker）。
- **漏过于**：作者自审。
- **新增检查项**：`backend_classes()` 用 `test-compile surefire:test`，并检查每个类都产生了报告；实测改守护测试后不再出现 `OpenApiExportTest`。
- **状态**：已落实（#298 已合入）

### QUAL-006　提 PR 前的钩子：C 语言环境下报错、崩溃等于放行

- **日期**：2026-10-16
- **现象**：钩子里 `precheck.sh` 因 `"$missing（…"` 的全角括号被当成变量名一部分而 `set -u` 报错；钩子解码被 `cut` 截断的中文输出时崩溃，退出码 1，对 PreToolUse 来说是「不拦截」。
- **根因**：只在交互终端（UTF-8 语言环境）测过；崩溃路径没按「失败即关闭」设计。
- **拦下于**：作者自查——写了端到端（C 语言环境、放行与拦截两种情形）才发现。
- **漏过于**：机械检查（自测只测了纯函数）；独立评审——首轮复审没跑端到端。
- **新增检查项**：钩子 `guarded_main`（出错一律拒绝）、宽松解码；`${变量}` 紧跟非 ASCII 字符的 grep 列入评审模板；**凡接进钩子的脚本，必须在 `env -i LC_ALL=C` 下端到端各跑一次放行与拦截。**
- **状态**：已落实（#298 已合入）

### QUAL-007　派工的前提没先量化：PR 里 gitleaks 增量扫描

- **日期**：2026-10-15
- **现象**：派工说「每个 PR 的 gitleaks 要 4 到 6 分钟」，要改增量扫描；实测扫描本身只要 15–25 秒，慢在同作业的发布脚本自测（216–287 秒），纯文档 PR 的该作业本来只要 28–41 秒。
- **根因**：把作业耗时归到了作业名上，没拆到步骤。
- **拦下于**：作者动手前的数据核查（读近 20 次运行的步骤耗时）；调度、用户确认取消。
- **漏过于**：无。
- **新增检查项**：画像「工作守则」：做性能或耗时类改动前，先按步骤拆耗时，再定方向。同类结论还有：轻量 CI 派工实测瓶颈是 runner 排队，不是 CI 逻辑。
- **状态**：待落实（本 PR 合入后改为已落实）

### QUAL-008　改词表不跑前端词表检查与后端词表测试，合集里挂过

- **日期**：2026-10-16
- **现象**：#267、#272 在合集里因词表检查失败挂过（调度告知）。
- **根因**：`precheck.sh` 对词表改动没有联动检查；前端依赖没装时只 SKIP。
- **拦下于**：CI（调度转述，#267、#272 的运行）才发现。
- **漏过于**：机械检查（precheck 缺联动）；作者自审。
- **新增检查项**：`precheck.sh` 改词表时跑前端 `check:copy`、`test:scripts` 与后端 4 个词表测试；前端依赖缺失判 FAIL 并写明「需先问用户安装」；`gh pr create` 前置钩子（#298）。
- **状态**：已落实（#298 已合入）

### QUAL-009　集成测试只等事件，审批请求还没提交就去读

- **日期**：2026-10-16
- **现象**：运行 38805349816（main，112abd60）里 `TimesheetApprovalScenarioIT.cancelledTimesheetIsADraft` 偶发失败：`The timesheet is not in a running approval`；同类 18 个用例仅此一个，下一提交起连续成功。
- **根因**：`submitAndAwaitApproval` 只用 `Scenario.andWaitForEventOfType(TaskAssigned)` 等事件；该事件在审批监听器的事务内发布，事件到达时请求可能还没提交，测试线程随即取消就查不到（读代码推断，未实测复现）。等待条件不是「审批已可见」，违反 CS-5.6 的精神。
- **拦下于**：CI 才发现（push 到 main 后失败）。
- **漏过于**：作者自审、独立评审（该类早先的评审没查“事件到达 ≠ 数据已提交”）。
- **新增检查项**：`awaitApprovalCommitted(document, round)`（Awaitility 等 `ApprovalProcesses.progress` 在该轮次可见；第 1 轮与重提的第 2 轮两处）；评审模板 `review-briefs/01-code-card.md` C6 的 CS-5.6 判据补一句：事件到达不等于数据已提交，等待条件必须是读到的数据。
- **状态**：待落实（#326 合入后改为已落实）

以下七条（QUAL-010 至 QUAL-016）来自调度对 2026-10-15 11:30Z 至 10-16 21:51Z 共 50 次 CI 失败的归类（`ci-failure-modes-2026-10-16.md`，sonnet 子代理只读归类，逐条未复核）；每条的「报错特征」供每晚复发检测按特征把当日失败归到旧条目。

### QUAL-010　测试用真实当前日期，真实日期越界后必现失败

- **日期**：2026-10-16
- **类别**：其他
- **现象**：`ProcessArchiveJobIT.archivesThenDeletes` 在 main 与多个 PR 上失败 5 次（运行 38962295103、38932209131、38932094029、38930153579、38927052609）。报错特征：`Expecting empty but was: [["{"instanceId"` 加 `ProcessArchiveJobIT.archivesThenDeletes`（断言 `vault.batches` 为空）。
- **根因**：测试用 `ZonedDateTime.now(Asia/Shanghai).plusYears(3).minusDays(2)` 算保留期窗口，而被测数据的结束时刻固定在注入时钟的 START（10-14）；真实日期过了某界，第一轮就把本该留下的探针归档。
- **拦下于**：CI 才发现（c4cced3c / #413 改为按固定 START 计）；这 5 次都发生在 head 不含该修复的分支或 main 上。
- **漏过于**：作者自审与独立评审（没查「断言依赖真实日期」）；没有机械检查，`TimeSourceGuardTest` 只管主代码。
- **新增检查项**：集成测试反模式检查（固定时间等待、真实时钟调用，全仓测试里 57 处真实时钟调用待逐处判）；负责人 one-quality，先报告后定是否拦截。待补。修复提交：`c4cced3c`
- **状态**：待落实（修复已合入；机械检查待补；负责：one-quality 2026-10-24）

### QUAL-011　异步审计让对共享协作者的 verify 次数偏发

- **日期**：2026-10-16
- **类别**：其他
- **现象**：`OrgSnapshotSyncIntegrationTest` 失败 3 次。报错特征：`TooManyActualInvocations`（出现在对 `AuditLog` 一类共享协作者的 `verify(...)` 之后）。
- **根因**：测试对被异步路径共用的协作者做不限定条目的 `verify(times(n))`，别的路径的调用混进计数（违反 CS-5.6：只断言本测试的动作、等待要确定）。
- **拦下于**：CI 才发现（05a38acd 修）。
- **漏过于**：作者自审、独立评审（评审清单没有「对共享协作者 verify 必须限定条目」一条）。
- **新增检查项**：评审模板 `review-briefs/01-code-card.md` C6 补一句「对共享协作者的 verify 必须限定到本测试的条目」；集成测试反模式检查加「无参数匹配的 `verify(shared, times(n))`」一类；待补，负责人 one-quality。
- **状态**：待落实（负责：one-quality 2026-10-24）

### QUAL-012　测试用 PostgreSQL 容器连接被断，等满 120 秒或运行中断开

- **日期**：2026-10-16
- **类别**：其他
- **现象**：3 次，2 次在 main：run 38938942883、38943494140（类加载时 `Container startup failed for image postgres:18`，恰好约 120 秒）；run 38925523855（`WorkCalendarIT` 全部用例 ERROR，`DROP DATABASE IF EXISTS template_migrated_testdata`）。报错特征：`Container startup failed for image postgres:18`、`PSQLException: An error occurred while setting up the SSL connection`、`PSQLException: The connection attempt failed` 加 `EOFException`。
- **根因**：容器已启动，但 JVM 到容器的连接在 SSL 协商时被对端立刻断开，首个 JDBC 连接的等待（带 init-script 的类按 120 秒默认值重连）用尽；是 Docker 端口转发断开还是 VM 资源紧张，当时没有取证（Docker Desktop 日志每 10 分钟滚动，事故时刻已丢）。
- **拦下于**：CI 才发现。
- **漏过于**：无任何防线（基础设施偶发）。
- **新增检查项**：#439：`TestPostgres.container()` 启动重试 3 次、首个 JDBC 连接等待 90 秒，耗尽时把原因、日志末尾与 Docker 摘要打进异常；ci.yml 失败时上传 docker 事件、容器日志与引擎摘要。
- **状态**：待落实（#439 合入、观察一周后定；负责：one-quality 2026-10-24）

### QUAL-013　runner 并发互扰：fork 的 JVM 崩溃、同毫秒失败

- **日期**：2026-10-16
- **类别**：其他
- **现象**：fork VM 崩溃 2 次、同毫秒失败 2 次（调度归类，疑似同机进程清理互杀，未证实）。报错特征：`The forked VM terminated without properly saying goodbye`、`ExecutionException The forked VM terminated`，或多个测试类在同一毫秒报同一错。
- **根因**：未查明。四台 runner 共用一个 Docker Desktop 与同一台机器；不排除一个作业的清理步骤杀了别的作业的进程。
- **拦下于**：CI 才发现。
- **漏过于**：无任何防线。
- **新增检查项**：保留失败作业的 `*.dump` 文件（surefire 的 fork 崩溃转储）与失败步骤末 50 行，下次有证据再判；待补，并进 #439 的取证，负责人 one-quality。
- **状态**：待落实（负责：one-quality 2026-10-24）

### QUAL-014　单元全绿但步骤失败、日志里没有失败行

- **日期**：2026-10-16
- **类别**：其他
- **现象**：4 次（含 main 的 38926958691）：`surefire and failsafe, side by side` 步骤失败，日志里找不到 `<<< FAILURE`/`<<< ERROR` 行。报错特征：步骤结论 failure 且 `Tests run:` 汇总全为 0 失败。
- **根因**：失败来自步骤本身（看门狗超时、fork 超时、failsafe 汇总、报告缺失等），`backend_verify.sh` 失败时不打印失败摘要，调查要翻整份日志。
- **拦下于**：CI 才发现。
- **漏过于**：无。
- **新增检查项**：`scripts/ci/backend_verify.sh` 失败时打印失败摘要（哪个阶段、哪个退出码、末 50 行）；待补，并进 #439，负责人 one-quality。
- **状态**：待落实（负责：one-quality 2026-10-24）

### QUAL-015　两个 PR 各自绿，合并后主线验收失效

- **日期**：2026-10-16
- **类别**：其他
- **现象**：4 次：合并后旧验收测试或「已知故障清单」过期，主线变红（调度归类）。报错特征：主线 push 或夜间运行红，而两个相关 PR 的运行都绿。
- **根因**：strict 模式下合并的树是带标签的 PR 刚测过的树，所以不该出；出现在合集合并与后改基线之间；关 strict 后更会发生：测试的是 PR 头，不是合并结果。
- **拦下于**：CI 才发现（合并后）。
- **漏过于**：队列（原来只在 PR 上跑，不在合并结果上跑）。
- **新增检查项**：队列代码道合入前必须基于最新 main 跑完整 CI（`scripts/merge/mq.py` 的 BEHIND-AT-MERGE 与合并锁）；main push 报警（ci.yml 的免容器单测步骤）与夜间全量变红即暂停两道；待落实，负责人 one-quality（队列 PR）。
- **状态**：待落实（负责：one-quality 2026-10-24）

### QUAL-016　守护测试违反，作者没在本地跑

- **日期**：2026-10-16
- **类别**：局部测试
- **现象**：约 4 次文档守护失败，另有 `TestContainerGuardTest`、`SingleOperatorModeListenerTest`（无报告）、`PrimaryKeyTypeGuardTest` 的违反在 CI 才发现。报错特征：`TestContainerGuardTest`、`PrimaryKeyTypeGuardTest` 等 *Guard*Test 红，或「produced no report: it did not run」。
- **根因**：改动触到守护读取的输入（`backend/architecture/` 清单、测试类、文档）时，precheck 没跑读它们的守护；新守护类没被登记进 precheck 的类列表。
- **拦下于**：CI 才发现。
- **漏过于**：机械检查（precheck 覆盖不全）；作者自审。
- **新增检查项**：#342（改 `backend/architecture` 时跑全部读它的免容器守护类）；`scripts/ci/light_unit_classes.py` 自动列出免容器单测（含新增守护）；CI 三档里 guarded 档按读取关系触发（`relevant_change.py`）；待补：precheck 对新增或改动的 `*Test` 类自身必跑，负责人 one-quality。
- **状态**：待落实（#342 合入后改为已落实；负责：one-quality 2026-10-24）

### QUAL-017　验收测试改动的裁定尾注只查有无，不查真伪

- **日期**：2026-10-17
- **类别**：其他
- **现象**：`Acceptance-Ruling: RULING-x` 尾注只要形式对就放行（`acceptance_guard.py`），裁定是否真有记录、是不是复审作出的、是否被反复借用都没人核对；#405 train 自拟 10 条裁定曾无任何记录，历史上 45 个裁定号无记录。报错特征：验收测试被改的提交带裁定尾注但仓库里找不到对应裁定。
- **根因**：守护把尾注当作裁定本身，没有裁定记录这一可核对的来源。
- **拦下于**：调度人工发现（one-coordinator 2026-10-17）。
- **漏过于**：机械检查（acceptance_guard 只查尾注格式）。
- **新增检查项**：`scripts/ci/acceptance_rulings.py`：尾注裁定号须在 PR base 的 `docs/modernization/rulings/ACCEPTANCE_RULINGS.yaml`，记录要素齐全且裁定人不是实现会话，裁定一次只放行一次，改动文件须被 files 覆盖；已知缺口：`Acceptance-Test-Author` 尾注仍可自填。
- **状态**：待落实（合入后改已落实；负责：one-quality 2026-10-24）

### QUAL-018　参考 DDL 给了函数授权，可执行脚本没有，守护只比 schema 与版本表

- **日期**：2026-10-17
- **类别**：其他
- **现象**：#405 草稿的 `one_db_roles_schemas.sql` 给了 `platform_audit.create_audit_entry_partitions` 的 EXECUTE 和新角色 `one_dev_audit_maintainer`，可执行的 `runbooks/sql` c3、c9 脚本没有；`db_scripts_aligned.py` 通过。报错特征：运行时「permission denied for function …」，或建库脚本跑出来缺角色。
- **根因**：守护只比 schema 列表、版本表和审计表的追加专用授权，函数授权与角色创建没纳入对齐。
- **拦下于**：#405 的 ultrareview（人工）。
- **漏过于**：机械检查（db_scripts_aligned 不比函数授权与角色）。
- **新增检查项**：`scripts/ci/db_scripts_aligned.py` 比对 CREATE ROLE 集合、函数的 GRANT/REVOKE/默认权限语句、授权对象角色须已创建；PR 待开（分支 claude/one-quality-db-grants），负责人 one-quality。
- **状态**：待落实（合入后改已落实；负责：one-quality 2026-10-24）

### QUAL-019　DeadLetterJobMetricsTest 的 gauge 实例被丢弃，GC 后读出 NaN

- **日期**：2026-10-19
- **类别**：局部测试
- **现象**：pre-push 时 `DeadLetterJobMetricsTest.gaugeFollowsTheEngine` 第 51 行偶发失败，期望 0.0、实际 NaN，重试通过（one-architecture 报告）。
- **根因**：`DeadLetterJobMetrics` 的构造器用 `Gauge.builder(name, this, fn)` 注册，Micrometer 对该对象只持弱引用；测试里 `new DeadLetterJobMetrics(management, registry, NOW);` 不保存返回值，GC 一旦回收该实例，gauge 读出 NaN。生产里实例是 Spring 单例，不受影响。同一文件的 `unreadableEngineIsNotZero` 断言 NaN，GC 与否都通过，掩盖了同一隐患。与 QUAL 中 `SingleAggregateGuardTest` 的 GC 失效同一类（弱引用对象无强引用）。
- **拦下于**：pre-push 钩子（偶发，重试通过）。
- **漏过于**：作者自审（测试作者）；机械检查没有覆盖“测试里丢弃注册 gauge 的实例”。
- **新增检查项**：测试修复——保存实例并在断言后 `Reference.reachabilityFence(metrics)`（或把实例放进字段）；测试已由 one-1a-par 修复（PR #486，CI-1019-PAR-01，opus 首轮通过）；机械检查 `scripts/ci/weak_registration_scan.py`（PR #487）：测试里对只弱引用注册的类（Gauge.builder 三参、WeakHashMap 等）丢弃实例或赋给之后不再用的局部变量时报告，先只报告，跑一周无误报转 `--enforce`。不为此隔离或放宽断言。
- **状态**：待落实（测试修复 #486 合入、检查项 #487 合入并转报错后改已落实；负责：one-1a-par 2026-10-26；检查项 one-quality）

### QUAL-020　SeeyonTicketVerifierTest 在整包并发下偶发 Unavailable：真实 HTTP 往返用 2 秒墙钟超时

- **日期**：2026-10-21
- **类别**：局部测试
- **现象**：one-1a-par 整包单测里 `SeeyonTicketVerifierTest.loginName` 一次得 `Unavailable`（预期 `LoginName`），单跑 3 次稳定。
- **根因**：（推断，待日志确认）被测类对每次校验都发真实 HTTP 请求到测试里的 WireMock，请求超时是测试传入的 2 秒墙钟（`Duration.ofSeconds(2)`）；`SsoVerdict.Unavailable` 只在非 200、`IOException`（含 `HttpTimeoutException`、连接失败）、中断或基址不可用时出现。整包并发（backend_verify 同时跑 surefire 与 failsafe 各 4 个 fork）下首个真实 HTTP 往返遇到 JVM 冷启动（HttpClient 选择器线程、类加载）加 CPU 争抢，停顿超过 2 秒即抛 `HttpTimeoutException` → `Unavailable`。本机在 24 个满载进程下重复 8 次未复现（只出现该类 `unavailable` 用例预期内的 500/302/HttpTimeout/基址告警），所以这是推断；确认只需看失败那次的日志有没有 `Seeyon OA ticket check failed: <异常类>` 或 `answered HTTP <码>`。
- **拦下于**：整包单测（偶发，重跑通过）。
- **漏过于**：作者自审（成功路径的用例与“故意超时”的用例共用了同一量级的墙钟超时）。
- **新增检查项**：测试修复——期望成功的用例（`loginName`、`invalidAnswers`）改用宽松超时（例如 10 秒，仅作兜底，不影响用时），“故意超时”的用例保持 500 ms 对 2000 ms 服务端延迟不变；不隔离、不放宽断言。机械检查另议：扫描测试里“期望成功却用 ≤3 秒的真实网络超时”，先只报告。
- **状态**：待落实（负责：one-1a-par 2026-10-28；归属 platform.integration，待核）

### QUAL-021　HttpWecomClientTest 在机器繁忙时偶发 HttpTimeoutException：成功路径用例用 5 秒墙钟读超时

- **日期**：2026-10-22
- **类别**：局部测试
- **现象**：one-architecture 在 pre-push 预检里，`HttpWecomClientTest.sendReturnsTheResponseCode`（第 161 行）报 `ExternalSystemUnavailableException: WeCom call failed: HttpTimeoutException`，本次只改了文档，重跑通过。
- **根因**：被测类对每次调用都发真实 HTTP 到测试里的 WireMock，读超时是 `freshClient` 里传入的 5 秒墙钟（连接超时也是 5 秒）；本机同时跑着多份构建与测试容器（负载曾到 80/18 核），WireMock 的 Jetty 首个请求（冷启动）被饿死超过 5 秒就超时。与 QUAL-020 同类：成功路径用例与「故意超时」用例共用了同一量级的墙钟超时。
- **拦下于**：pre-push 预检（偶发，重跑通过）。
- **漏过于**：作者自审（没有区分「期望成功的用例的兜底超时」与「要测超时的用例的超时」）。
- **新增检查项**：测试修复——成功路径用例的连接与读超时改为 30 秒兜底（不影响用时，只在桩卡死时才用到），故意超时的用例（`impatient`，300 ms 对 1500 ms 延迟）保持不变；同类扫描留给「集成测试反模式检查」（固定时间等待、墙钟超时，QUALITY_CONTEXT 待办 6）。
- **状态**：待落实（修复 PR 合入后改已落实；负责：one-quality 2026-10-24）
