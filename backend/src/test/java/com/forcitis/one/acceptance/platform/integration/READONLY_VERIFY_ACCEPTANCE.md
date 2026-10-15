# PAR-2 只读核实命令：验收测试假定的契约

依据：ADR-0037 第 4 项 (a)–(g)（含 RULING-ADR37-10 的 (g)）、第 4 项第 2 条、第 3 项第 5 条；交接 `HANDOFF_one-1a-next-2.md` 第 6 节 one-coordinator 口径；PREDEPLOY_CHECKLIST C-14 与 `runbooks/C-14-readonly-probe.md`「应用侧核实入口」。验收测试：`ReadOnlyVerificationCommandIT`（同目录）。用语：只读核实（ReadOnlyVerification）及三种结果「通过 / 违规 / 判定不了」在 `backend/glossary/proposed/platform-integration.yaml` 登记为提议术语。

测试不引用任何生产类，只按下列字符串约定驱动打包好的应用。实现须对齐本契约；认为契约有误时停下报告，由复审裁定后由测试代理改（SESSION_PLAYBOOK §6.1 第 8 条）。

## 1. 调用

```
java -Duser.timezone=UTC -jar forcitis-one.jar --spring.profiles.active=read-only-verification --connection=<连接名>
```

- 档案名：`read-only-verification`。命令只随这个档案运行，跑完一次即退出进程（不是常驻服务；测试给 120 秒，超时即失败）。
- 主类：发布包的默认入口（`OneApplication`），测试以 `java -jar target/forcitis-one-*.jar` 运行，与 ECS 上一样；不改主类。
- `--connection=<连接名>`：运行中服务的只读连接名（建池名、度量标签 `connection` 与 ALERT 日志中用的同一名称）：

| 连接名 | 读哪组环境变量（与 one.env 相同） |
|---|---|
| `integration-seeyon-background` | `A8_DB_URL`、`A8_DB_USERNAME`、`A8_DB_PASSWORD` |
| `integration-legacy` | `LEGACY_DB_RO_URL`、`LEGACY_DB_RO_USERNAME`、`LEGACY_DB_RO_PASSWORD` |
| `legacy-approval` | `FORCITIS_PLATFORM_APPROVAL_LEGACY_URL`、`FORCITIS_PLATFORM_APPROVAL_LEGACY_USERNAME`、`FORCITIS_PLATFORM_APPROVAL_LEGACY_PASSWORD` |

  （A8 的交互连接 `integration-seeyon-interactive` 与 `integration-seeyon-background` 是同一登录，测试只用后者；实现是否也接受前者不测。）
- 只核实所指定的那一条连接：测试的环境里另两条连接都配了违规账号（加入 `db_datareader`），所核实的连接仍须得出自己的结果。
- 缺少 `--connection`、连接名不在上表：用法错误（第 4 节），不做核实。

## 2. 环境（测试给子进程的全部环境变量）

子进程的环境只有下列变量加 `PATH`、`HOME`、`TMPDIR`、`LANG`，模拟 `/etc/forcitis-one/one.env` 中与本命令有关的部分：

- 三组只读连接变量（第 1 节表格），`INTEGRATION_SEEYON_VIEWS_ENABLED=true`、`INTEGRATION_LEGACY_ENABLED=true`、`FORCITIS_PLATFORM_INTEGRATION_SEEYON_VIEWS_*` 五项实例编号、`FORCITIS_PLATFORM_APPROVAL_LEGACY_DETAIL_URL_TEMPLATE`；
- 服务自己的库 `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD`（一个空的 PostgreSQL）；
- `SPRING_PROFILES_ACTIVE=dev`（被命令行 `--spring.profiles.active` 取代）；
- `SERVER_ADDRESS`、`SERVER_PORT`、`MANAGEMENT_SERVER_ADDRESS`、`MANAGEMENT_SERVER_PORT`（各给一个空闲端口）：one.env 里有这几项，**环境变量的优先级高于档案的 properties 文件**，所以只在档案里写 `server.port=-1`、`management.server.port=-1` 挡不住它们；
- `FORCITIS_JOBS_ENABLED=true`（与常驻服务相同）。

不给：归档库（OSS）、企业微信、单点登录、应急管理员密钥等与本命令无关的变量。命令不得依赖它们，也不得调用它们背后的外部服务。

## 3. 输出

标准输出与标准错误合起来按行解析；其他行（日志等）不解析，但同样受第 5 节约束。

- 每项恰好一行，(a) 到 (g) 依次七行：

  ```
  (a) PASSED
  (b) VIOLATED
  (c) UNDETERMINED error=<SQL 错误号> sqlstate=<SQLState>
  ```

  正则：`^\(([a-g])\) (PASSED|VIOLATED|UNDETERMINED)((?: [a-z]+=[A-Za-z0-9_.-]+)*)$`。结果词后只允许 ` 键=值` 形式的附加项，值不含空格（预留给原因代码等，见回报中的建议）。
- 判定（与启动校验同一套 `ReadOnlyBaseline`，不另写）：一项有确认违规 → `VIOLATED`；只有判定不了 → `UNDETERMINED`；都没有 → `PASSED`。
- `UNDETERMINED` 因 SQL 失败而来时，该行带 ` error=<n> sqlstate=<s>`：数据库驱动报出的错误号与 SQLState（测试先以同一登录直连取得作为预期值：库不存在为 4060；目录视图被拒为 229；端口无人监听取驱动所报）。
- 结果行恰好一行：`^RESULT (PASSED|VIOLATED|UNDETERMINED)$`，有任一 `VIOLATED` 即 `VIOLATED`（违规优先于判定不了），否则有 `UNDETERMINED` 即 `UNDETERMINED`，否则 `PASSED`。
- 用法错误（第 4 节）不输出项目行与结果行。

## 4. 退出码

| 结果 | 退出码 |
|---|---|
| 通过 `PASSED` | 0 |
| 用法错误（含命令行出现口令类参数、缺少或未知连接名） | 2 |
| 违规 `VIOLATED`（含与判定不了同在） | 3 |
| 判定不了 `UNDETERMINED`（无确认违规；含连不上、库不存在、目录查询被拒） | 4 |

不用 1：Spring Boot 启动失败（上下文起不来）的退出码是 1，与任何结果区分开，崩溃不会被当成某个结果。2 与 `identity-operations` 运维命令的「用法错误」一致。

## 5. 不输出什么

全部输出（含日志）中不得出现：

- 口令（所核实连接的，以及 env 中其他连接的）、所核实连接的登录名与完整连接串；
- 数据库消息原文：库名带标记值的不存在库，消息里含该标记，输出里不得有；
- `ReadOnlyBaseline.Unmet.problem` 文本：其中可能带角色名、库名等对象名；测试建一个以标记值命名的自定义角色并把账号加进去，输出里只能有 `(c) VIOLATED`，不得有该标记；
- 数据。

## 6. 口令只从环境读

命令行出现口令类参数即拒绝，退出 2，不做核实，不回显参数值。测试覆盖：`--password=…`、`--forcitis.platform.integration.seeyon.views.password=…`、`--A8_DB_PASSWORD=…`、`--spring.datasource.password=…`（同一环境去掉该参数即通过）。建议实现按「参数名中含 `password`（不分大小写）」判定，另可加 `secret`、`pwd`。

## 7. 不监听端口、不迁移、不起作业

- 不监听任何 TCP 端口：测试以 `-Djava.nio.channels.spi.SelectorProvider` 装入探针（`ListeningSocketProbe`，`-Xbootclasspath/a` 加 `--add-exports java.base/sun.nio.ch=ALL-UNNAMED`），进程打开任何 server socket channel（Tomcat 的业务端口与管理端口、Netty 都经此）都会在标准错误写出标记；输出中出现标记即失败。对照用例 `probeSeesAProcessThatListens` 证明探针有效。
- 不跑迁移：命令结束后服务库（空 PostgreSQL）中没有任何表、没有新 schema（因此没有 Flyway 历史表）。
- 不起调度与事件发布作业：黑盒不直接观察（命令跑完即退出进程，120 秒内须自行结束）；实现以档案关闭，复审核对。
