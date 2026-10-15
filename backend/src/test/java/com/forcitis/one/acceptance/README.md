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

- 放进 `acceptance/<子域>/<BC>/`，类名以 `IT` 结尾，继承 `AcceptanceSupport`，并声明自己的容器：`@Container @ServiceConnection static PostgreSQLContainer postgres = database();`。
- `@DisplayName` 用中文，以卡号或规则编号开头并写明依据条款（如「R-1b P3a ADR-0033 第 2 项第 1 条：…」）。
- 只经公开接口断言：HTTP（`mvc` + `person(n, 权限码…)`）、模块 `api` 包的接口、或该模块自己的 schema（`schemaConnection("<schema>")`，SQL 不写 schema 前缀）。
- 时间取固定时钟 `AcceptanceSupport.NOW` / `TODAY`，要换日期用 `setToday`（例外：`SessionPurgeClockIT` 第 2 条直接读系统时间，因会话表时间由框架按系统时钟写入，见 RULING-FS-1）；等待异步结果用 Awaitility 带上限，不用 `Thread.sleep`。
- 不写密钥、口令、令牌字面量；测试数据用合成值。
- 尚未实现的接口不能让本目录编译失败：没有 HTTP 入口的服务按约定的名字在运行时查找（见 `platform/integration/SourceReferencesProbe`），找不到即失败，不跳过。

## K-3a 列表分页契约

- `platform/web/ListPagingContractIT`：limit 范围、未登记参数、游标合法性、OpenAPI 的 `limit` 声明，用法人、审计、来源引用、工作日历四个列表代表所有列表；新增列表接入时在 `lists()` 里加一个 `Endpoint`。
- `organization/attendance/WorkCalendarYearsPagingIT`：工作日历年份列表的键集分页试点；「由数据库切页」通过测试容器的 `log_statement=all` 日志判断，用前后两条标记语句圈出本次请求的语句。
- 名称带「特征测试」的用例在 K-3a 实现前已是绿的，必须保持绿。
