# 验收测试

由独立测试代理先写、实现代理只让它们转绿（SESSION_PLAYBOOK §6.1 第 8 条）。改动本目录的提交须带 trailer `Acceptance-Test-Author: test-engineer` 或 `Acceptance-Ruling: <裁定编号>`（`scripts/ci/acceptance_guard.py`）。

## 运行

```
cd backend
./mvnw -o test-compile
./mvnw -o failsafe:integration-test -Dit.test='com/forcitis/one/acceptance/**/*IT'
```

需要本机 Docker：每个测试类起一个自己的 `postgres:18` 容器（Testcontainers，用完即删）。报告在 `target/failsafe-reports/`，每个类都必须有报告（`scripts/ci/acceptance_reports.py`）。

## 加一条用例

- 放进 `acceptance/<子域>/<BC>/`，类名以 `IT` 结尾，继承 `AcceptanceSupport`，并在本类声明自己的库（共享 PostgreSQL 上每类一库，#783）：`static final TestDatabase postgres = TestPostgres.database(本类.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");` 加 `@DynamicPropertySource static void database(DynamicPropertyRegistry registry) { postgres.register(registry); }`。字段不放基类：基类的静态字段只初始化一次，子类会共用一个库。要读容器日志的才用 `database()` 自起容器。
- `@DisplayName` 用中文，以卡号或规则编号开头并写明依据条款（如「R-1b P3a ADR-0033 第 2 项第 1 条：…」）。
- 只经公开接口断言：HTTP（`mvc` + `person(n, 权限码…)`）、模块 `api` 包的接口、或该模块自己的 schema（`schemaConnection("<schema>")`，SQL 不写 schema 前缀）。
- 时间取固定时钟 `AcceptanceSupport.NOW` / `TODAY`，要换日期用 `setToday`（例外：`SessionPurgeClockIT` 第 2 条直接读系统时间，因会话表时间由框架按系统时钟写入，见 RULING-FS-1）；等待异步结果用 Awaitility 带上限，不用 `Thread.sleep`。
- 不写密钥、口令、令牌字面量；测试数据用合成值。
- 尚未实现的接口不能让本目录编译失败：没有 HTTP 入口的服务按约定的名字在运行时查找（见 `platform/integration/SourceReferencesProbe`），找不到即失败，不跳过。

## K-3a 列表分页契约

- `platform/web/ListPagingContractIT`：limit 范围、未登记参数、游标合法性、OpenAPI 的 `limit` 声明，用法人、审计、来源引用、工作日历四个列表代表所有列表；新增列表接入时在 `lists()` 里加一个 `Endpoint`。
- `organization/attendance/WorkCalendarYearsPagingIT`：工作日历年份列表的键集分页试点；「由数据库切页」通过测试容器的 `log_statement=all` 日志判断，用前后两条标记语句圈出本次请求的语句。
- 名称带「特征测试」的用例在 K-3a 实现前已是绿的，必须保持绿。

## R-1b P3b 员工、部门、岗位 ID 改 UUID

- 员工、部门 ID 在测试里一律是 `UUID`（`AccountsAcceptanceSupport.newEmployeeId()` / `newOrgUnitId()`）。写库、拼路径、命令参数与表单字段经 `stored(id)` / `text(id)`，构造 kernel `EmployeeId` 经 `employeeIdOf(id)`，读回经 `fromStored`；P3b 实现前（`EmployeeId` 仍包 `long`）这些方法按登记的编号写旧形态，所以改写后的现有用例在实现前后结果相同（RULING-P3B-1/2）。`serialOf(id)` 只用于合成编码、姓名与企业微信 userid，不能当 ID 用；别处分配来的自有 ID（如来源对照）先 `adoptId`。
- 组织源与老系统（`platform/integration/OrgSourceWorldSupport`）：一个 SQL Server 容器，每个测试 JVM 起一次，继承它的类共用；类开始时只留启动校验需要的基础行，各类自己加行；每类自己的 PostgreSQL。触发同步只经 `syncOnce()`，拆分 a 把同步移进集成层时只改这一处。分配次数用测试库里 `source_references` 上的语句级触发器计数（`countAllocations()` / `allocationStatements()`）。
- `SourceTypeBoundaryTest` 读 `backend/architecture/source-type-exceptions.txt`：每行 `路径 | 理由 | 拆分 a`，文件头含「只减不增」；规则名 `SOURCE_TYPE`、`SOURCE_OR_LEGACY_ID`。
- 断言用到的新名字（列名、约束名、命令名、事件字段）集中为各测试类的常量，术语门禁改名时只改常量。

## R-1b P4 审批 ID、投递去重键、部门负责人审批改名

- 新名字（列名、约束名、节点 id、字段名、投递对象类型名）集中在 `P4Names`，术语变化时只改它。
- 审批类用例继承 `ApprovalP4Support`：一个发起人、一个直属领导、两个项目经理、一个部门负责人，各有账号；文档 ID 经 `documentIdOf` 构造（P4 前是 `DocumentId(String)`，之后是 `DocumentId(UUID)`）；`DescendingIds` 是递减的 `IdGenerator`（后产生的 ID 更小，并记下调用栈），用 `@TestConfiguration` 的 `@Primary` bean 注入，用来证明排序与游标不依赖 ID 次序。
- `OutboundDeliveryKeysIT` 用真实 `WecomGateway` 加企业微信桩（同 `WecomDeliveryIT`，不继承 `AcceptanceSupport`，因其把网关换成了桩）；结构化投递键 `DeliverySubject` 与新的 `WecomCard` 经反射按记录组件类型装配，类型尚不存在时在运行时失败，不让目录编译失败。
- RULING-P4-1：`AccountsAcceptanceSupport.newDocumentId()`、`approvalRowsOf`、`pendingTaskIdsOf` 让既有的 `ApprovalAccountsIT`、`AssigneeInputUuidIT`、`RetractionInitiatorAccountsIT` 在 P4 前后都能跑，断言未改。
