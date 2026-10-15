# 验收测试：外部来源卡住时的降级（D1-1）

`StalledExternalSourceIT` 用真实 SQL Server 容器（组织来源与老系统待办各一个库）和真实适配器，验证外部库卡住或极慢时请求线程不被占住。

- 运行：`cd backend && ./mvnw -B verify -Dit.test=StalledExternalSourceIT`（failsafe，不需要 `-Plegacy-it`；Testcontainers 自起 PostgreSQL 与 SQL Server，镜像经 `TestImages` 读 `backend/architecture/test-images.txt`，不在测试里写死；需要本机 Docker）。
- 卡住的做法：测试持有表锁（SQL Server 默认无限等锁，等价于生产故障）；视图内不能放 `WAITFOR`，所以另用一个只耗 CPU 的标量函数做「不加锁但极慢」。
- 连接地址里不带任何超时参数：超时必须来自应用建连接的方式（工厂）。
- 上限：交互路径 3 s 外部调用预算加 1.5 s 余量；后台同步 30 s（ShedLock 锁 4 分钟）。
- 对照用例（`controlWithoutAnyStall`）证明不卡时世界是通的，避免全红或全绿都是假象。
- 新增用例：在同一个类里加 `@Test`，用 `Blocker.hold(库, 表)` 或 `replaceView` 制造卡住，用 `timed(...)` 在独立线程里调用并断言 `hung()` 为假、`elapsed()` 在预算内。
- 复审用例（`D1-1 复审` 开头，引复审编号）：H2 断言极慢视图在调用方降级后 2 s 内从 `sys.dm_exec_requests` 消失（服务器端已取消，不只是客户端断开）；M1 断言连接池 2 个连接占满时第三个请求的取连接等待与查询合计仍在 3 s 预算内。
