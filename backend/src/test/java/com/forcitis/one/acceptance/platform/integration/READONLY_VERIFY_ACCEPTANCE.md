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

  建池名与各自读取的环境变量（与 one.env 同名）不在本文写出，以 `docs/modernization/phase1a/infra/INFRA_RUNBOOK.md` §4.1 的表格为准；测试类中的枚举 `ReadOnlyConnection` 与之逐项对应。测试覆盖其中三条：组织源只读连接（后台建池名）、老系统部门简称只读连接、老系统待办只读连接；§4.1 中的 `probe`（C-14 探针登录）不测。

  组织源的交互建池名虽与后台建池名同一登录，**不在本命令范围**：实现只认 §4.1 的三个建池名加 `probe`，测试不用交互建池名。
- 只核实所指定的那一条连接：测试的环境里另两条连接都配了违规账号（加入 `db_datareader`），所核实的连接仍须得出自己的结果。
- 缺少 `--connection`、连接名不在 §4.1 表中：用法错误（第 4 节），不做核实。

## 2. 环境（测试给子进程的全部环境变量）

子进程的环境只有下列变量加 `PATH`、`HOME`、`TMPDIR`、`LANG`，模拟 `/etc/forcitis-one/one.env` 中与本命令有关的部分：

- 三组只读连接变量（INFRA_RUNBOOK §4.1 表格），以及 one.env 中这三条连接的启用开关、组织源的五项实例编号、老系统待办详情地址模板（变量名见测试类 `environment()`）；
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

## 3a. 定位行（(f)(g)；one-coordinator 2026-10-15 C-14 定位裁定）

依据：rc.2 在 C-14 第一步三个正式账号都未通过（(f) `SYSTEM_DATABASE_OWN_USER count=2`；(g) `SECURABLE_GRANT_BEYOND_SELECT count=7`，one-ops 取证为业务库授给 public 的对象级 GRANT EXECUTE），只有原因码与计数定位不了。one-coordinator 定：此前「不输出名称」只对下列字段放开。

### 格式

定位行紧跟所属项的项行之后（中间只隔同一项的其他定位行），两个空格开头，不匹配第 3 节的项行正则与结果行正则，现有 `(x) ... reason= count=` 项行格式不变：

```
(f) VIOLATED reason=SYSTEM_DATABASE_OWN_USER count=2
  (f) violation reason=SYSTEM_DATABASE_OWN_USER database="master"
  (f) violation reason=SYSTEM_DATABASE_OWN_USER database="msdb"
(g) VIOLATED reason=SECURABLE_GRANT_BEYOND_SELECT count=1
  (g) violation reason=SECURABLE_GRANT_BEYOND_SELECT class=OBJECT_OR_COLUMN schema="dbo" object="t_x" permission="CONTROL" state=GRANT grantee="public" granteeType=DATABASE_ROLE
  (g) covered class=OBJECT_OR_COLUMN schema="dbo" object="p_x" permission="EXECUTE" state=GRANT grantee="public" granteeType=DATABASE_ROLE by=DATABASE_DENY
```

- 正则：`^  \(([fg])\) (violation|covered)((?: [a-zA-Z]+=VALUE)+)$`，`VALUE` 为 `"(?:[^"\\\p{Cntrl}  ]|\\["\\nrt]|\\u[0-9A-F]{4})*"` 或 `[A-Z0-9_-]+`。以两个空格加 `(` 开头而不合此式的行算格式错误。
- 种类：`violation` 违规定位（计入该项计数）；`covered` 已被 DENY 覆盖的授权（信息行，不计数，不使该项违规；项为 `PASSED` 时也输出）。
- 字段顺序固定（便于 `grep 'permission="EXECUTE" state=GRANT'`）：
  - (f) `violation`：`reason database`。只对原因 `SYSTEM_DATABASE_OWN_USER` 出行，每个有本登录自己用户的系统库（master、tempdb、model、msdb 与 rdscore）一行。
  - (g) `violation`：`reason class schema object permission state grantee granteeType`。
  - (g) `covered`：`class schema object permission state grantee granteeType by`。
- 值：
  - 带双引号：数据库目录中的名字（`database`、`schema`、`object`、`grantee`）与权限名 `permission`（含空格，如 `"TAKE OWNERSHIP"`），原样输出，只做最小转义：`\` → `\\`，`"` → `\"`，换行 → `\n`，回车 → `\r`，制表 → `\t`，其余 ISO 控制字符与 Unicode 行分隔符、段分隔符、格式字符（Zl、Zp、Cf，如 U+2028、U+202E）→ `\uXXXX`（四位大写十六进制）；其他字符（含中文）原样。
  - 不带引号：固定词表或占位。`class` 为 `sys.database_permissions.class_desc`（`OBJECT_OR_COLUMN`、`SCHEMA` 等）；`state` 为 `GRANT` 或 `GRANT_WITH_GRANT_OPTION`；`granteeType` 为 `sys.database_principals.type_desc`（public 为 `DATABASE_ROLE`）；`by` 为 `DATABASE_DENY` 或 `SCHEMA_DENY`。
  - 占位（字段缺失或不得输出时出占位，不省略字段）：`-` 无此值或取不到（schema 级授权的 `object`；无所属 schema 的安全对象的 `schema`）；`SELF` 授权对象为本账号自己的数据库用户——用户名在夹具与 RDS 上就是登录名，第 5 节禁止输出，所以一律写 `SELF`，`granteeType` 照常（`SQL_USER`）。
- `schema` 为安全对象所属 schema（schema 级授权即该 schema 本身）；`object` 为安全对象名（列级授权记在所在表上）。`grantee` 只输出主体名与类型，不展开角色成员。
- 每条授权一行：同一 `(class, schema, object, permission, state, grantee)` 只一行；该项行中 `SECURABLE_GRANT_BEYOND_SELECT` 的 `count` 等于 `(g) violation` 行数，`SYSTEM_DATABASE_OWN_USER` 的 `count` 等于 `(f) violation` 行数。排序：先 `violation` 后 `covered`，各按字段值字典序（测试不断言顺序）。
- 其他项、其他原因（含 (f) 的 `OTHER_DATABASE_REACHABLE`，待裁定）不出定位行。

### (g) 的已覆盖判定（RULING-ADR37-10 的收窄解释，安全判定放宽）

RULING-ADR37-10 定「库内 class≠0 的授权只允许 DENY 或 SELECT 的 GRANT，public 也算」。依 ADR-0037 第 4 项 (b)「库级 DENY 覆盖 schema 级、对象级与经自定义角色得到的 GRANT」，one-coordinator 2026-10-15 补充、one-1a-next-3 加安全条件，收窄为按有效权限判定。影响只读核实命令、启动校验与运行期重检（同一套 `ReadOnlyBaseline`）：

1. 一条授给 public 或本账号的 class≠0 的 `GRANT`（state `G`）是「已覆盖」，当且仅当：其权限名在库级 DENY 清单（ADR-0037 第 4 项 (b) 的 11 项：INSERT、UPDATE、DELETE、ALTER、EXECUTE、REFERENCES、CREATE TABLE、CREATE VIEW、CREATE PROCEDURE、CREATE FUNCTION、CREATE SCHEMA）内，且本账号自己有同名的 `DENY`——库级（class 0，`by=DATABASE_DENY`）或该安全对象所属 schema 上（class SCHEMA，`by=SCHEMA_DENY`）；两者都有时写 `DATABASE_DENY`。按权限名精确匹配，不按「有库级 DENY 即全覆盖」；授给 public 的 DENY 不算本账号的 DENY。
2. 不在清单内的权限一律仍是违规，即使本账号另有同名 DENY：尤其 CONTROL、TAKE OWNERSHIP、IMPERSONATE、VIEW DEFINITION；CONTROL 隐含的权限不因其中某一项被 DENY 而视为整体覆盖。`GRANT_WITH_GRANT_OPTION` 一律违规。
3. 判定不了 DENY 是否存在时，可被覆盖的授权按判定不了（`UNDETERMINED`），不按已覆盖：DENY 查询失败；或本账号的 DENY 行（class 0 与 SCHEMA）一行都没有（真实账号总有 11 条库级 DENY）。这类授权不出定位行。黑盒覆盖了「一行都没有」；「只有 DENY 查询失败而授权查询成功」黑盒构造不出（两者读同一目录视图），**留给实现的单元测试（待补）**。
4. 已覆盖的授权在启动校验与运行期重检中不拒绝、不告警；在只读核实命令中出 `covered` 信息行。

### 只由只读核实命令输出

定位字段（库名、schema 名、对象名、权限名、grantee）只出现在只读核实命令的定位行。启动校验的拒绝原因、日志，运行期重检的 ALERT、日志与同步结果说明中不出现 schema 名与对象名（`ReadOnlyAccountCheckIT.startupRefusalNamesNoSchemaOrObject`、`recheckAlertNamesNoSchemaOrObject`）。

### 用例

`ReadOnlyVerificationCommandIT`：`systemDatabaseWithAnOwnUserIsLocatedByName`、`publicExecuteCoveredByTheDatabaseDenyPasses`、`controlAndTakeOwnershipStayViolationsBesideACoveredGrant`、`schemaLevelGrantsCarryTheObjectPlaceholder`、`aSchemaDenyCoversOnlyItsOwnSchema`、`noDenyAnswerLeavesTheGrantUndetermined`、`grantsToTheLoginItselfShowTheSelfPlaceholder`、`locatorValuesAreEscaped`。(g) 的用例在各自新建的库里（`isolatedAccount`），授给 public 的授权不波及其他用例。`ReadOnlyAccountCheckIT`：`coveredPublicGrantsDoNotRefuseStartup` 及上节两条；复审 H-1 的 public 反例由 GRANT UPDATE 改为 GRANT CONTROL，另加 GRANT TAKE OWNERSHIP。

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
- 名称：唯一例外是第 3a 节定位行中的字段（(f) 系统库名；(g) schema、对象、权限、grantee），其他项与其他原因的名称照旧不输出；定位行中本账号的用户名也写占位 `SELF`；
- 数据。

## 6. 口令只从环境读

命令行出现口令类参数即拒绝，退出 2，不做核实，不回显参数值。测试覆盖四种参数名（同一环境去掉该参数即通过）：`--password`、组织源连接配置键中的 password 属性、组织源口令环境变量名、`--spring.datasource.password`（原样写法见测试类 `passwordOnTheCommandLineIsRefused`）。建议实现按「参数名中含 `password`（不分大小写）」判定，另可加 `secret`、`pwd`。

## 7. 不监听端口、不迁移、不起作业

- 不监听任何 TCP 端口：测试以 `-Djava.nio.channels.spi.SelectorProvider` 装入探针（`ListeningSocketProbe`，`-Xbootclasspath/a` 加 `--add-exports java.base/sun.nio.ch=ALL-UNNAMED`），进程打开任何 server socket channel（Tomcat 的业务端口与管理端口、Netty 都经此）都会在标准错误写出标记；输出中出现标记即失败。对照用例 `probeSeesAProcessThatListens` 证明探针有效。
- 不跑迁移：命令结束后服务库（空 PostgreSQL）中没有任何表、没有新 schema（因此没有 Flyway 历史表）。
- 不起调度与事件发布作业：黑盒不直接观察（命令跑完即退出进程，120 秒内须自行结束）；实现以档案关闭，复审核对。
