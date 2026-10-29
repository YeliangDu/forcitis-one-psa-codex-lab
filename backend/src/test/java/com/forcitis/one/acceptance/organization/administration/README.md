# 行政事务卡 A 验收测试（organization_administration）

由独立测试代理先写，实现代理只让它们转绿、不得修改（SESSION_PLAYBOOK §6.1 第 8 条）。被测对象是卡 A 迁移 `db/migration/organization_administration/V<时间戳>__organization_administration_baseline.sql` 落库后的行为（DDL 草案 `DDL_DRAFT_CARD_A.sql`，one-data 已签「数据设计：通过」）。直接用 JDBC 断言数据库，不引用任何尚不存在的 Java 类型。

## 编号

| 类 | 编号 | 内容 |
|---|---|---|
| `ConfigSetVersionLifecycleIT` | ADM-A-01 至 34、67 | 版本头生命周期、四眼、确认日、关闭前一版本、排他、撤回终态、`config_sets` 守护；67 为迁移种子（五个配置集、无种子版本） |
| `ConfigSetItemsIT` | ADM-A-35 至 44 | 条目只在待确认时可插入、固定配置集、格式、只追加 |
| `AdministrationStateTransitionsIT` | ADM-A-45 至 61 | `state_transitions` 合法流转、序号、起因、只追加、与版本行 version 的配合 |
| `AdministrationGrantsIT` | ADM-A-62 至 66 | 运行账号权限（真实建库脚本 + 运行账号连接） |

`AdministrationDb` 是共用支撑（一个不提交的事务、保存点探测、固定 ID 与时间）。

## 运行

```
cd backend
./mvnw -o test-compile
./mvnw -o failsafe:integration-test -Dit.test='com/forcitis/one/acceptance/organization/administration/*IT'
```

前三个类用 `TestPostgres.database(类, MIGRATED)`：共享 `postgres:18` 容器上由模板库复制出的库，模板用 `application.properties` 的 `spring.flyway.schemas` 迁移，所以实现须把 `organization_administration` 加进该清单。`AdministrationGrantsIT` 自起一个容器，执行 `docs/ops/one-db-roles-schemas.sql`（含迁移后一节）、以迁移账号跑 Flyway，再以运行账号连接，所以实现须同改该脚本（schema 清单、`REVOKE`）。

## 约定

- 确认日取库的事务日（Asia/Shanghai），测试里用 `db.today()` 取同一个值再算相对日期，不依赖运行时刻。
- `ADM-A-23`、`24` 用 `session_replication_role = replica` 关闭触发器，单独检验排他与区间 CHECK；`ADM-A-58` 在事务内建临时非属主角色；都随事务回滚。
- 多条约束同时被破时只断言 SQLSTATE（`refusedWith`），单条时断言约束名（`refusedByConstraint`），触发器抛出的断言 SQLSTATE 与消息片段（`refusedByRule`）。

## 加一条用例

在对应类里加方法，`@DisplayName` 以下一个 `ADM-A-NN` 开头并写一行 Given/When/Then；条目表新增（后续卡）另起类或在 `ConfigSetItemsIT` 里仿 `ITEMS` 常量扩展。改动本目录的提交须带 trailer `Acceptance-Test-Author: test-engineer` 或 `Acceptance-Ruling: <裁定编号>`。
