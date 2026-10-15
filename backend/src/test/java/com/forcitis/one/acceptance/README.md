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
- 时间取固定时钟 `AcceptanceSupport.NOW` / `TODAY`，要换日期用 `setToday`；等待异步结果用 Awaitility 带上限，不用 `Thread.sleep`。
- 不写密钥、口令、令牌字面量；测试数据用合成值。
- 尚未实现的接口不能让本目录编译失败：没有 HTTP 入口的服务按约定的名字在运行时查找（见 `platform/integration/SourceReferencesProbe`），找不到即失败，不跳过。
