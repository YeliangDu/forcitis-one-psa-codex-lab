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
- 夹具（`ReadOnlyBaselineFixture`，`StalledExternalSourceIT` 共用）与 RDS 的差异：RDS 平台经服务器角色 public 给所有登录服务器级 ALTER ANY CONNECTION、ALTER ANY EVENT SESSION、ALTER ANY LINKED SERVER、ALTER ANY LOGIN、ALTER SERVER STATE、ALTER TRACE、VIEW SERVER STATE（取证：四个真实登录在 `sys.server_permissions` 中各只有 `SERVER | CONNECT SQL | GRANT` 一行），容器里同样在建任何登录前把七项 GRANT 给 public（`createRdsPlatform`，每个容器一次）；SQL Server 2022 会由此派生出 RDS 导出里没有的细分权限（事件会话细分、CREATE LOGIN、VIEW SERVER PERFORMANCE / SECURITY STATE），也在服务器级 DENY 给 public，登录自身不单独授也不单独拒（RULING-ADR37-11）。授给 public 影响容器内所有登录：sa 是 sysadmin，不受 GRANT / DENY 影响；用例自建的登录都按基线建，反例只在本登录上另加授权；库级按 PR #135 形状 GRANT SELECT 并 DENY 11 项（库级 DENY ALTER 同时消去派生的 ALTER ANY DATABASE EVENT SESSION）；`rdscore` 建为空库并开 guest。`fixtureReproducesTheRdsBaseline` 先证明容器里的有效权限与 B-5 README 的 RDS 导出逐项相等，且登录自身的 `sys.server_permissions` 恰好一行 `100 CONNECT SQL GRANT`，其余用例才有意义（每个反例开始前与 `StalledExternalSourceIT` 类初始化时也做同一比对）。
- 拒绝的判定：应用启动抛出异常，且异常链消息含被违反的项目标记 `(a)`–`(g)`（全角括号也算；ADR-0037「按项报出不满足的一项」，(g) 为 RULING-ADR37-10 新增的库级以下授权项）；校验查询本身失败的用例接受 `(b)` 或 `(e)`。只断言「启动失败」会把无关的启动错误当成通过，所以必须带项目标记。
- 对照：`baselineAccountStarts`（每条连接单独启用）与 `allThreeBaselineAccountsStartTogether`；反例与对照用同一夹具，只差一处授权。
- 运行期重检：用索引使用统计（`sys.dm_db_index_usage_stats`）数视图底表被读的次数，证明重检失败后一次也没读。
- 新增反例：在 `VIOLATIONS` 里加一行 `new Violation(名称, 应报出的项目字母, 账号 -> 要执行的 SQL)`，自动在三条连接上各跑一次；授权只加在本用例新建的登录上，不必回滚。授权落到本登录以外（如授给 `public`）时用四参数形式再给一个 `undo`，用例结束（含失败）即撤销，免得同库后续用例被连累。
- 复审反例（名称以「复审 H-1」「复审 H-2」开头，引 PR #163 复审编号）：对象级 TAKE OWNERSHIP、schema 级 CONTROL、服务器上针对登录的 IMPERSONATE ON LOGIN::sa、`public` 在用户对象上的 UPDATE（H-1）；在 msdb 为该登录建用户并加入 db_owner、在 master 为该登录建用户（H-2）。按实现实际报出的标记收窄（RULING-ADR37-11）：TAKE OWNERSHIP、schema 级 CONTROL、`public` 的 UPDATE 断言 `(g)`；IMPERSONATE ON LOGIN::sa 断言 `(d)`；H-2 两例断言 `(f)`。「库级 DENY INSERT 被撤并 GRANT INSERT ON SCHEMA::dbo」实现同时报 `(b)` 与 `(g)`、「class 4 授权 IMPERSONATE ON USER」同时报 `(e)` 与 `(g)`，用例保留原有的 `(b)`、`(e)`，不放宽。
- 删改测试覆盖（复审 L）：`postgresqlAddressIsRefusedThroughTheRealFactory` 把每条只读连接配成新系统自己的 PostgreSQL 地址与账号，经真实工厂启动，断言拒绝并带项目标记（取代已删的「应用自己的账号不能配成老系统账号」）。
