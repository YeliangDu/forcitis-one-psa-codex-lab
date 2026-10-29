# 验收测试：外部来源卡住时的降级（D1-1）

`StalledExternalSourceIT` 用真实 SQL Server 容器（组织来源与老系统待办各一个库）和真实适配器，验证外部库卡住或极慢时请求线程不被占住。

- 运行：`cd backend && ./mvnw -B verify -Dit.test=StalledExternalSourceIT`（failsafe，不需要 `-Plegacy-it`；Testcontainers 自起 PostgreSQL 与 SQL Server，镜像经 `TestImages` 读 `backend/architecture/test-images.txt`，不在测试里写死；需要本机 Docker）。
- 卡住的做法：测试持有表锁（SQL Server 默认无限等锁，等价于生产故障）；视图内不能放 `WAITFOR`，所以另用一个只耗 CPU 的标量函数做「不加锁但极慢」。
- 连接地址里不带任何超时参数：超时必须来自应用建连接的方式（工厂）。
- 三个只读登录（组织视图、老系统待办、老系统部门简称各一个）按 RDS 平台基线建（`ReadOnlyBaselineFixture`，与 `ReadOnlyAccountCheckIT` 共用），类初始化时先与基线逐项比对，不符即全部失败；ADR37-1 的启动校验无关闭开关，本类照常经过它，组织视图夹具因此带有配置的汇报关系类型、交付单元与计费人员标记、根路径（RULING-ADR37-6）。
- 上限：交互路径 3 s 外部调用预算加 1.5 s 余量；后台同步 30 s（ShedLock 锁 4 分钟）。
- 对照用例（`controlWithoutAnyStall`）证明不卡时世界是通的，避免全红或全绿都是假象。
- 新增用例：在同一个类里加 `@Test`，用 `Blocker.hold(库, 表)` 或 `replaceView` 制造卡住，用 `timed(...)` 在独立线程里调用并断言 `hung()` 为假、`elapsed()` 在预算内。
- 复审用例（`D1-1 复审` 开头，引复审编号）：H2 断言极慢视图在调用方降级后 2 s 内从 `sys.dm_exec_requests` 消失（服务器端已取消，不只是客户端断开）；M1 断言连接池 2 个连接占满时第三个请求的取连接等待与查询合计仍在 3 s 预算内。

# 验收测试：只读连接启动校验与运行期重检（ADR37-1）

`ReadOnlyAccountCheckIT` 按 ADR-0037 第 4 项与实现卡口径（one-coordinator、one-ops 2026-10-15）验证三条只读连接（组织视图、老系统待办、老系统部门简称）：账号有效权限与 RDS 平台基线逐项相等才能启动，多出、缺少、无法判定都拒绝启动；同步任务开始前重检，失败停该适配器、写 ALERT、不再读取。

- 运行：`cd backend && ./mvnw -B verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=ReadOnlyAccountCheckIT`（failsafe，需要本机 Docker；镜像经 `TestImages` 读 `backend/architecture/test-images.txt`）。每个用例新建一个登录并真实启动一次应用（`OneApplication`，随机端口），共约 70 次启动，本机约 4–5 分钟。
- 夹具（`ReadOnlyBaselineFixture`，`StalledExternalSourceIT` 共用）与 RDS 的差异：RDS 平台经服务器角色 public 给所有登录服务器级 ALTER ANY CONNECTION、ALTER ANY EVENT SESSION、ALTER ANY LINKED SERVER、ALTER ANY LOGIN、ALTER SERVER STATE、ALTER TRACE、VIEW SERVER STATE（取证：四个真实登录在 `sys.server_permissions` 中各只有 `SERVER | CONNECT SQL | GRANT` 一行），容器里同样在建任何登录前把七项 GRANT 给 public（`createRdsPlatform`，每个容器一次）；SQL Server 2022 会由此派生出 RDS 导出里没有的细分权限（事件会话细分、CREATE LOGIN、VIEW SERVER PERFORMANCE / SECURITY STATE），也在服务器级 DENY 给 public，登录自身不单独授也不单独拒（RULING-ADR37-11）。授给 public 影响容器内所有登录：sa 是 sysadmin，不受 GRANT / DENY 影响；用例自建的登录都按基线建，反例只在本登录上另加授权；库级按 PR #135 形状 GRANT SELECT 并 DENY 11 项（库级 DENY ALTER 同时消去派生的 ALTER ANY DATABASE EVENT SESSION）；`rdscore` 建为空库并开 guest；每个登录另建 RDS 平台自动建的两个本人用户：msdb 中属 SQLAgentOperatorRole、SQLAgentReaderRole、SQLAgentUserRole，rdscore 中无角色只有 CONNECT（C-14 (f) 纳入基线，用户 2026-10-15「纳入基线」，one-ops 样本；三个角色不存在即夹具失败）。`fixtureReproducesTheRdsBaseline` 先证明容器里的有效权限与 B-5 README 的 RDS 导出逐项相等，且登录自身的 `sys.server_permissions` 恰好一行 `100 CONNECT SQL GRANT`，其余用例才有意义（每个反例开始前与 `StalledExternalSourceIT` 类初始化时也做同一比对）。
- 拒绝的判定：应用启动抛出异常，且异常链消息含被违反的项目标记 `(a)`–`(g)`（全角括号也算；ADR-0037「按项报出不满足的一项」，(g) 为 RULING-ADR37-10 新增的库级以下授权项）；校验查询本身失败的用例接受 `(b)` 或 `(e)`。只断言「启动失败」会把无关的启动错误当成通过，所以必须带项目标记。
- 对照：`baselineAccountStarts`（每条连接单独启用）与 `allThreeBaselineAccountsStartTogether`；反例与对照用同一夹具，只差一处授权。
- 运行期重检：用索引使用统计（`sys.dm_db_index_usage_stats`）数视图底表被读的次数，证明重检失败后一次也没读。
- 新增反例：在 `VIOLATIONS` 里加一行 `new Violation(名称, 应报出的项目字母, 账号 -> 要执行的 SQL)`，自动在三条连接上各跑一次；授权只加在本用例新建的登录上，不必回滚。授权落到本登录以外（如授给 `public`）时用四参数形式再给一个 `undo`，用例结束（含失败）即撤销，免得同库后续用例被连累。
- 复审反例（名称以「复审 H-1」「复审 H-2」开头，引 PR #163 复审编号）：对象级 TAKE OWNERSHIP、schema 级 CONTROL、服务器上针对登录的 IMPERSONATE ON LOGIN::sa、`public` 在用户对象上的 UPDATE（H-1）；msdb 本人用户另加 db_owner（夹具已按 RDS 建 msdb 本人用户，原「建用户并加入 db_owner」改为只加角色）、在 master 为该登录建用户（H-2）。C-14 (f) 纳入基线另加：msdb 本人用户多 DatabaseMailUserRole、rdscore 本人用户入 db_datareader、拥有一个 Agent 作业（sa 以 `sp_add_job @owner_login_name` 建，容器不启用 Agent 也写入 msdb，用例结束即 `sp_delete_job`），均断言 `(f)`；作业名不出现在拒绝原因、ALERT 与日志中。按实现实际报出的标记收窄（RULING-ADR37-11）：TAKE OWNERSHIP、schema 级 CONTROL、`public` 的 UPDATE 断言 `(g)`；IMPERSONATE ON LOGIN::sa 断言 `(d)`；H-2 两例断言 `(f)`。「库级 DENY INSERT 被撤并 GRANT INSERT ON SCHEMA::dbo」实现同时报 `(b)` 与 `(g)`、「class 4 授权 IMPERSONATE ON USER」同时报 `(e)` 与 `(g)`，用例保留原有的 `(b)`、`(e)`，不放宽。
- 删改测试覆盖（复审 L）：`postgresqlAddressIsRefusedThroughTheRealFactory` 把每条只读连接配成新系统自己的 PostgreSQL 地址与账号，经真实工厂启动，断言拒绝并带项目标记（取代已删的「应用自己的账号不能配成老系统账号」）。

# 验收测试：组织来源部门类型直读基表与读取列登记（R-1b-0a）

部门类型直读验收类（`*SourceFlagsIT`）按卡 R-1b-0 第 4、14 节（D-1a-01；ADR-0037 第 2 项第 4 条、第 3 项第 5 条、第 6 项第 1、3 条、第 8 项第 4 条；RULING-R1B0-7）验证：部门的 `TYPE` 从 `dbo.ORG_UNIT` 读出并校验（根以下出现 `Account`，或任一单元 `TYPE` 为 NULL / 非 Department、Account 即整轮 FAILED + ALERT）；启动时运行时用途的基表行做子集检查，经视图读取的路径仍做相等检查；迁入、对账用途的登记行不进启动检查；人员语句不变。

- 运行：`cd backend && ./mvnw -B verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test='*SourceFlagsIT'`（failsafe，需要本机 Docker；PostgreSQL 经 `TestPostgres.container()`，SQL Server 镜像经 `TestImages`）。主库启动一次应用，启动校验用例各建一个库、各启动一次，约 1 分钟。
- 夹具：老系统形状的基表加 DBA 视图脚本原文（`docs/data/legacy-views/` 下的视图脚本，取其 schema、views 两段；部门负责人与区域应收负责人的关系类型编号运行时从该脚本读出），只读登录按 `ReadOnlyBaselineFixture` 建并与 RDS 基线比对。人员与三个人员标志列留在夹具里（本卡不读），供纯视图人员语句比对与 R-1b-0b 复用。列类型：人员标志 smallint 可空、`ORG_PRINCIPAL.LOGIN_NAME` nvarchar(50)（取证）；`ORG_UNIT.TYPE` nvarchar、部门标志与 `IS_INTERNAL` smallint 为假设，待取证核对。
- 黑盒：只用 `OrgSource`、`SyncOrgSnapshot`、`GetSyncRun`、`OrgDirectory`、日志与 SQL Server 计划缓存，不引用改动后才有的类型。等价性用例从计划缓存取适配器实际发出的部门与人员语句，与冻结在测试里的改动前纯视图语句做两个方向 `EXCEPT`。
- 待术语确认的名字（读取列登记文件名 `external-read-registry.yaml`、用途取值 `RUNTIME_SYNC` / `RUNTIME_TODO`）集中在类顶部常量，确认后只改那里。
- 移到 R-1b-0b 的用例（人员标志、两项不一致判定、单人查找、登录查人遇来源损坏）不在本类，逐条记在 `backend/src/test/resources/acceptance/platform/integration/r1b0b-pending/MemberFlagsCases.md`，0b 开卡后据此重写。
- 新增用例：在 `corruptions()` 里加一行 `Corruption`，或照启动用例用 `create…Database(库名, 变体 SQL...)`（建库辅助方法，见验收类） 建库后 `startAndClose`；修改基表的用例在 `finally` 中还原。
- `ReadOnlyAccountCheckIT`、`StalledExternalSourceIT` 的老系统夹具按 RULING-R1B0-1 把视图底表改为 `dbo.ORG_MEMBER`（带三个标志列）与 `dbo.ORG_UNIT`（带 `TYPE`），视图列与测试意图不变；R-1b-0a 合并后启动校验要求 `dbo.ORG_UNIT` 带 `TYPE`。

# 验收测试：只读核实命令（PAR-2，ADR-0037 第 4 项、C-14）

`ReadOnlyVerificationCommandIT` 以打包好的应用（`java -jar target/forcitis-one-*.jar`，与 ECS 一样）运行只读核实命令，每个用例一个独立 JVM，对真实 SQL Server 容器（登录由 `ReadOnlyBaselineFixture` 建）核实一条只读连接，断言逐项标记 (a)–(g)、结果行与退出码。测试假定的契约（档案名、参数、输出行格式、退出码、环境变量）见同目录 `READONLY_VERIFY_ACCEPTANCE.md`；测试不引用生产类。

- 运行：`cd backend && ./mvnw -B verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=ReadOnlyVerificationCommandIT`（failsafe 在 package 之后运行，需要本机 Docker；镜像经 `TestImages`）。`target/` 中没有发布包、或发布包比 `target/classes` 旧时，类初始化即失败，不跳过。
- 每个用例另断言：命令没有打开任何监听套接字（`ListeningSocketProbe` 作为选择器提供者装入子进程，对照用例 `probeSeesAProcessThatListens` 证明它能发现监听），服务库（空 PostgreSQL）中没有表、没有新 schema（无 Flyway 历史表、无迁移）；输出不含口令、登录名、连接串。
- 预期值以同一登录直连 SQL Server 取得（`connectionFailure`、`catalogRefusal`），不凭推测：库不存在 4060、目录视图被拒 229，SQLState 取驱动所报。
- 新增反例：在 `DEVIATIONS` 加一行 `new Deviation(名称, 应标违规的项目字母, 账号 -> 要执行的 SQL)`；授权只加在本用例新建的登录上。新增「不得输出」的情形：构造一个带 `marker()` 的对象名，断言输出不含它。
- 定位行（契约第 3a 节，one-coordinator 2026-10-15 C-14 定位裁定）：(f) 系统库名、(g) 授权的 schema、对象、权限、grantee 逐条一行；`run.locators()` 解析并检查每行紧跟所属项行，预期值用 `violation(...)`、`overridden(...)` 构造。(f) 的本人用户角色信息行（`ownUser`）由 `run.ownUsers()` 取、预期值用 `ownUser(库, 角色或 null)` 与 `RDS_OWN_USERS` 构造；`run.findings()` 只含 `violation` 与 `overridden`。要授权给 public 的用例用 `isolatedAccount`（新建一个库），不波及其他用例。

- 复审 H-1（msdb 本人用户的权限，契约第 3a 节「(f) msdb 本人用户的权限」）：反例清单在 `ReadOnlyBaselineFixture.MSDB_COUNTEREXAMPLES`，两个 IT 共用；加一个新反例只加清单一项（apply、undo、预期的有效权限与越线的直接授权），夹具核对、启动校验与核实命令三处自动覆盖。(5) 角色嵌套、(6) 授给 SQLAgentUserRole、(7) 授给 msdb 的 public 是全局性的（msdb 内所有登录受影响），只在 finally 撤销、只能在同一容器里顺序执行；加这类反例前先想清楚别的类是否同时用 msdb（同 fork 的类顺序执行，不同 fork 各有容器）。
- 复审 H-A / M-A（msdb 令牌角色与拥有关系，契约同文件「(f) msdb 令牌角色与拥有关系」）：msdb 令牌里 public 与三个 SQLAgent 角色以外的任何角色、令牌内主体在 msdb 拥有角色 / schema / 对象，都违规（同一原因码）。夹具 `ownMsdbPrivileges` 另报 `TOKEN_ROLE …` 与 `OWNS ROLE|SCHEMA|OBJECT`；新反例 (16)–(19) 在 `MSDB_COUNTEREXAMPLES`，(5) 即案例 A4；只经 guest 的 A2 在两个 IT 里单独成例。平台默认拥有关系（guest 与三个 SQLAgent 角色各拥有同名 schema）由 `assertPlatformSchemasAreOwnedByTheirNamesakes` 在 `guestOnlyAccount` 里先核实。
- 复审 H-1′（令牌范围权限，契约第 3a 节「(f) msdb 令牌范围的权限」）：`MSDB_COUNTEREXAMPLES` 追加 (9)–(14)（授给 SQLAgentUserRole 或 public 的 TAKE OWNERSHIP、ALTER ANY DATABASE DDL TRIGGER、CREATE FUNCTION、IMPERSONATE ON USER::dbo、ALTER ON SCHEMA::dbo、ALTER ON 某 dbo 过程），记录多一个 `reached` 字段，写 `ownMsdbPrivileges` 额外报出的 `EFFECTIVE IMPERSONATE` 与 `TOKEN …` 行；(15) 授给 guest 的 ALTER ANY SCHEMA 只对「msdb 没有本人用户、只经 guest 进入」成立，单独用 `guestOnlyAccount` 构造。默认授权不误判由 `msdbDefaultGrantsOf…` 两个用例证明（先 `assertMsdbDefaultGrantsExist`）。全局性反例都在 `createRdsPlatform` 里幂等清理；跑这两个类要同一 fork：`-Dtest.forks=1`。
