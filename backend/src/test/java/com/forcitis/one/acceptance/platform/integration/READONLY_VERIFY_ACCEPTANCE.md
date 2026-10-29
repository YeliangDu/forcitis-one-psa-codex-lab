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

  建池名与各自读取的环境变量（与 one.env 同名）不在本文写出，以 `docs/ops/infra-runbook.md` §4.1 的表格为准；测试类中的枚举 `ReadOnlyConnection` 与之逐项对应。测试覆盖其中三条：组织源只读连接（后台建池名）、老系统部门简称只读连接、老系统待办只读连接；§4.1 中的 `probe`（C-14 探针登录）不测。

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
  (f) violation reason=SYSTEM_DATABASE_OWN_USER database="tempdb"
  (f) ownUser database="master" role=-
  (f) ownUser database="msdb" role="SQLAgentOperatorRole"
  (f) ownUser database="msdb" role="SQLAgentReaderRole"
  (f) ownUser database="msdb" role="SQLAgentUserRole"
  (f) ownUser database="rdscore" role=-
  (f) ownUser database="tempdb" role=-
(g) VIOLATED reason=SECURABLE_GRANT_BEYOND_SELECT count=1
  (g) violation reason=SECURABLE_GRANT_BEYOND_SELECT class=OBJECT_OR_COLUMN schema="dbo" object="t_x" objectId=901578250 permission="CONTROL" state=GRANT grantee="public" granteeType=DATABASE_ROLE
  (g) overridden class=OBJECT_OR_COLUMN schema=- object=- objectId=1093578934 permission="EXECUTE" state=GRANT grantee="public" granteeType=DATABASE_ROLE by=DATABASE_DENY
```

- 正则：`^  \(([fg])\) (violation|overridden|ownUser)((?: [a-zA-Z]+=VALUE)+)$`，`VALUE` 为 `"(?:[^"\\\p{Cntrl}\u2028\u2029]|\\["\\nrt]|\\u[0-9A-F]{4})*"` 或 `[A-Z0-9_-]+`。以两个空格加 `(` 开头而不合此式的行算格式错误。
- 种类：`violation` 违规定位（计入该项计数）；`overridden` 被拒绝覆盖的授权（OverriddenGrant，提议术语由实现 PR 登记；DENY overrides GRANT；信息行，不计数，不使该项违规；项为 `PASSED` 时也输出）；`ownUser` 系统库本人用户的角色信息行（见下节「(f) 系统库本人用户」；信息行，不计数，不使该项违规；项为 `PASSED` 时也输出）。
- 字段顺序固定（便于 `grep 'permission="EXECUTE" state=GRANT'`）：
  - (f) `violation`：`reason database`。原因 `SYSTEM_DATABASE_OWN_USER`：每个本登录自己的用户超出下节「(f) 系统库本人用户」所允许形状的系统库（master、tempdb、model、msdb 与 rdscore）一行；原因 `SYSTEM_DATABASE_OWN_USER_PRIVILEGE`：msdb 本人用户的权限超出下节「(f) msdb 本人用户的权限」一行；原因 `OTHER_DATABASE_REACHABLE`：每个能进的基线外用户库一行（one-coordinator 2026-10-15 裁定，`reason` 取该原因码；由实现的单元测试覆盖，本验收不断言）。
  - (f) `ownUser`：`database role`。
  - (g) `violation`：`reason class schema object objectId permission state grantee granteeType`。
  - (g) `overridden`：`class schema object objectId permission state grantee granteeType by`。
- 值：
  - 带双引号：数据库目录中的名字（`database`、`schema`、`object`、`grantee`）与权限名 `permission`（含空格，如 `"TAKE OWNERSHIP"`），原样输出，只做最小转义：`\` → `\\`，`"` → `\"`，换行 → `\n`，回车 → `\r`，制表 → `\t`，其余 ISO 控制字符与 Unicode 行分隔符、段分隔符、格式字符（Zl、Zp、Cf，如 U+2028、U+202E）→ `\uXXXX`（四位大写十六进制）；其他字符（含中文）原样。
  - 不带引号：固定词表或占位。`class` 为 `sys.database_permissions.class_desc`（`OBJECT_OR_COLUMN`、`SCHEMA` 等）；`objectId` 为该行的 `major_id`（十进制数字，只是编号、非凭据；对象为 object_id，schema 级授权为 schema_id），始终输出，取不到写 `-`；`state` 为 `GRANT` 或 `GRANT_WITH_GRANT_OPTION`；`granteeType` 为 `sys.database_principals.type_desc`（public 为 `DATABASE_ROLE`）；`by` 为覆盖该授权的那条 DENY（被哪条 DENY 覆盖）：`DATABASE_DENY` 本账号库级同名 DENY，`SCHEMA_DENY` 本账号在该安全对象所属 schema 上的同名 DENY。
  - 占位（字段缺失或不得输出时出占位，不省略字段）：`-` 无此值或取不到（schema 级授权的 `object`；无所属 schema 的安全对象的 `schema`；账号看不见的安全对象的 `schema` 与 `object`，见下条）；`SELF` 授权对象为本账号自己的数据库用户——用户名在夹具与 RDS 上就是登录名，第 5 节禁止输出，所以一律写 `SELF`，`granteeType` 照常（`SQL_USER`）。
- `schema` 为安全对象所属 schema（schema 级授权即该 schema 本身）；`object` 为安全对象名（列级授权记在所在表上）。
- 元数据可见性（one-1a-par 裁定：元数据可见性，方向 A）：名称以被核实账号自己的身份读取，取决于它的元数据可见性——SQL Server 只让账号看见它持有未被拒绝的某项权限的安全对象。被本账号 DENY 覆盖的 public GRANT EXECUTE（过程）正是看不见的情形：权限行在 `sys.database_permissions` 中，但 `sys.objects`、`sys.schemas`、`OBJECT_NAME`、`INFORMATION_SCHEMA`、`HAS_PERMS_BY_NAME` 都取不到名称与所属 schema。看不见的对象写 `schema=-`、`object=-`，用 `objectId` 定位（运维以取证账号反查名称）；看得见的照常输出名称。测试以账号身份先查 `sys.objects` / `sys.schemas` 确认每个期望的可见性前提。`grantee` 只输出主体名与类型，不展开角色成员。
- 每条授权一行：同一 `(class, objectId, permission, state, grantee)` 只一行；该项行中 `SECURABLE_GRANT_BEYOND_SELECT` 的 `count` 等于 `(g) violation` 行数，(f) 中 `SYSTEM_DATABASE_OWN_USER`、`SYSTEM_DATABASE_OWN_USER_PRIVILEGE`、`OTHER_DATABASE_REACHABLE` 的 `count` 等于该原因的 `(f) violation` 行数；`OWNS_AGENT_JOB` 不出定位行（作业名是对象名，不输出），`count` 为作业个数。排序：先 `violation` 后 `overridden`，各按字段值字典序（测试不断言顺序）。
- 其他项、其他原因不出定位行。

### (f) 系统库本人用户（C-14 (f) 纳入基线；用户 2026-10-15「纳入基线」，one-coordinator 收紧口径）

依据：rc.3 前三个正式只读账号 (f) `SYSTEM_DATABASE_OWN_USER count=2`，是 RDS 平台为每个登录在 msdb 与 rdscore 自动建的本人用户；one-ops 以取证账号核对样本：rdscore 本人用户 SQL_USER、无角色、库级只有 CONNECT；msdb 本人用户 SQL_USER、属 SQLAgentOperatorRole、SQLAgentReaderRole、SQLAgentUserRole。夹具（`ReadOnlyBaselineFixture`）为每个登录建同样的两个本人用户，`assertMatchesTheRdsBaseline` 逐项核对。影响只读核实命令、启动校验与运行期重检（同一套 `ReadOnlyBaseline`）：

1. 允许（不算违规）：
   - rdscore 中的本人用户：不属于任何角色，库级授权只有 GRANT CONNECT；
   - msdb 中的本人用户：只属于 SQLAgentOperatorRole、SQLAgentReaderRole、SQLAgentUserRole 的子集（显式三个名称，按名称精确匹配，不用通配）。
2. 仍判违规，原因 `SYSTEM_DATABASE_OWN_USER`，定位行 `database` 为该库：master、tempdb、model 中有本人用户（无论角色）；msdb 本人用户属于上列三个以外的任一角色（如 db_datareader、db_owner、DatabaseMailUserRole）；rdscore 本人用户属于任一角色，或库级有 CONNECT 以外的授权。
3. 附加条件：本登录拥有的 SQL Agent 作业数必须为 0，否则违规，原因 `OWNS_AGENT_JOB`（技术码，(f) 项），`count` 为作业个数，不出定位行，输出不含作业名。读法：`SELECT COUNT(*) FROM msdb.dbo.sysjobs_view WHERE owner_sid = SUSER_SID()`。**不读 `msdb.dbo.sysjobs`**：测试代理 2026-10-15 在 SQL Server 2022 容器实测，msdb 中只有 TargetServersRole 有 sysjobs 的 SELECT，三个 SQLAgent 角色的成员读 sysjobs 报 229；SELECT on `sysjobs_view` 授给 SQLAgentUserRole（另两个角色是它的成员），视图对作业所有者总列出自己的作业（`owner_sid = SUSER_SID()`）。读不到（查询失败、被拒）为判定不了，带 SQL 错误号与 SQLState（`unreadableAgentJobsAreUndetermined`：msdb 中 DENY SELECT ON sysjobs_view，预期错误先以同一登录直连取得，229）。
4. 信息行 `ownUser`：本登录在系统库（master、tempdb、model、msdb、rdscore 中 `HAS_DBACCESS` 为 1 的）有本人用户时，每个角色一行 `  (f) ownUser database="<库>" role="<角色名>"`；无角色的本人用户一行 `role=-`。合规与违规都输出，供运维对三个正式账号逐个核对与样本一致。只列角色名称，不列成员、权限与其他对象名。角色名以本登录身份读取（经三段名读 `<库>.sys.database_role_members` 与 `<库>.sys.database_principals`，`sid = SUSER_SID()`；容器实测本登录看得见自己的成员关系）。
5. 启动校验与运行期重检：允许的本人用户不拒绝、不告警；违规照 (f) 拒绝或停连接。拒绝原因、ALERT、日志沿用现有 problem 文本口径（可含库名与角色名），不含作业名等其他对象名（`ReadOnlyAccountCheckIT.ownedAgentJobRefusesStartupWithoutTheJobName`、`agentJobAfterStartupFailsTheRecheckWithoutTheJobName`）；名称清单只由只读核实命令的定位行与信息行输出。

### (f) msdb 本人用户的权限（复审 H-1；one-coordinator 2026-10-15 裁定）

依据：opus 安全复审 H-1。只查 msdb 本人用户的角色，等于对 msdb 内提权不设防：msdb 默认 TRUSTWORTHY ON、拥有者 sa，db_owner 级能力可借 EXECUTE AS 升到 sysadmin。只改 msdb；其余系统库口径不变。影响只读核实命令、启动校验与运行期重检（同一套 `ReadOnlyBaseline`）：

1. 直接授权（授给 msdb 本人用户自己的行，`sys.database_permissions`）按类别允许清单：DATABASE 类（class 0）只允许 `G CONNECT`；OBJECT_OR_COLUMN 类只允许 `G SELECT`、`G EXECUTE` ；任何 class 的 DENY 放行（复审 Low-1，DENY 不能提权）；其他 class 的 GRANT（SCHEMA、DATABASE_PRINCIPAL 等）与所有带转授权（state `W`）一律违规。
2. 有效权限兜住角色嵌套、角色与 public 的授权：以本登录身份在 msdb 内查 `HAS_PERMS_BY_NAME('msdb', 'DATABASE', p)`，p 取 CONTROL、ALTER、ALTER ANY ROLE、ALTER ANY USER、ALTER ANY SCHEMA、CREATE PROCEDURE，**任一为真即违规**。
3. 违规原因 `SYSTEM_DATABASE_OWN_USER_PRIVILEGE`（技术码，(f) 项，已定）；`count` 为违规的库数（本节只有 msdb，故为 1），不随权限个数增减；定位行与 `SYSTEM_DATABASE_OWN_USER` 同形，字段只有 `reason database`（`database="msdb"`），不输出权限名、对象名、角色名（已定：定位行只含 `reason` 与 `database`，不加 `permission` 字段）。角色信息行 `ownUser` 不变（嵌套、授给角色与 public 的授权都不改本人用户的成员关系）。
4. 不误判：夹具基线账号（msdb 本人用户加三个 SQLAgent 角色，平台默认授权）在新检查下六项有效权限全假、直接授权全在清单内，仍 `PASSED`。one-ops 在 RDS 实测的 34 条平台默认授权（直接：DATABASE CONNECT G 1；OBJECT_OR_COLUMN EXECUTE G 1、SELECT G 31、SELECT D 1；其余经 SQLAgentUserRole、SQLAgentOperatorRole 继承）的形状由 `oneOpsShapedMsdbGrantsStillPass`、`oneOpsShapedMsdbGrantsDoNotRefuseStartup` 显式覆盖（容器里用 msdb 的 backupset、backupfile、backupmediaset 三个 SELECT、sp_help_category 一个 EXECUTE、restorehistory 一个 DENY SELECT 同形）。
5. 启动校验与运行期重检：违规照 (f) 拒绝或停连接，拒绝原因可含库名，不含对象名（原因文本不断言，只断言项标记 `(f)`）。
6. 夹具反例（`ReadOnlyBaselineFixture.MSDB_COUNTEREXAMPLES`，两个 IT 共用）：(1) GRANT CONTROL ON DATABASE::msdb；(2) GRANT ALTER ANY ROLE；(3) GRANT IMPERSONATE ON USER::dbo（六项有效权限都假，只有直接授权越线）；(4) GRANT ALTER ON SCHEMA::dbo 加 GRANT CREATE PROCEDURE（有效 CREATE PROCEDURE 真）；(5) `ALTER ROLE db_owner ADD MEMBER SQLAgentUserRole`；(6) GRANT CONTROL ON DATABASE::msdb TO SQLAgentUserRole；(7) GRANT ALTER ANY SCHEMA TO public（msdb 内）；(8) GRANT EXECUTE ON dbo.sp_help_category WITH GRANT OPTION（state W）。(5)(6)(7) 是**全局性**的：改的是 SQLAgent 角色或 public，msdb 内所有登录都受影响，每个用例在 finally 里撤销，`createRdsPlatform` 另在每个类初始化时幂等清理它们；同一容器（同一 fork）里的类顺序执行才安全，不得与别的用到 msdb 的类并发。夹具核对 `msdbCounterexampleReallyHoldsItsPrivilegeAndUndoRestoresTheBaseline` 证明每个反例让 `ownMsdbPrivileges` 恰报出预期的有效权限与越线的直接授权，撤销后基线核对仍通过。

### (f) msdb 令牌范围的权限（复审 H-1′；one-coordinator 2026-10-16 裁定，补上一节）

依据：opus 复核 H-1′。上一节的六项有效权限只覆盖数据库级的 CONTROL、ALTER、ALTER ANY ROLE / USER / SCHEMA、CREATE PROCEDURE；**授给 SQLAgent 角色、嵌套角色、public、guest 的间接授权**到达本人用户时六项为 0，不被拦：数据库级 TAKE OWNERSHIP、ALTER ANY DATABASE DDL TRIGGER、CREATE FUNCTION / ASSEMBLY / TYPE / VIEW / TABLE；用户级 IMPERSONATE ON USER::dbo；架构级 ALTER / CONTROL ON SCHEMA::dbo；对象级 ALTER 某个 dbo 存储过程。上一节第 1–6 条全部保留（六项仍是兜底），本节在其上追加：

1. 令牌范围扫描：以本登录身份经 `msdb.sys.sp_executesql` 查 `sys.database_permissions WHERE grantee_principal_id IN (SELECT principal_id FROM sys.user_token)`，对令牌内**所有**主体（本人用户、三个 SQLAgent 角色、嵌套角色、public、guest）套**同一份类别允许清单**：DATABASE 类只许 `G CONNECT`；OBJECT_OR_COLUMN 类只许 `G SELECT`、`G EXECUTE` ；任何 class 的 DENY 放行（复审 Low-1，DENY 不能提权）；其他 class 的 GRANT 与 state `W` 一律违规。只对 `public` 另放行 SQL Server 2016 起默认授给它的两条库级 `G VIEW ANY COLUMN MASTER KEY DEFINITION`、`G VIEW ANY COLUMN ENCRYPTION KEY DEFINITION`（SQL Server 2022 容器实测 msdb 的 public 恰有这两条库级授权，加上对象级 `G SELECT` 约 249 条、`G EXECUTE` 约 15 条；SQLAgentUserRole 有对象级 `G EXECUTE` 约 47 条、`G SELECT` 3 条，SQLAgentOperatorRole 有 `G EXECUTE` 5 条、`G SELECT` 4 条；没有其他 class）。本人用户自己的行仍是上一节第 1 条，合并在同一次扫描里。目录列与字面量排序规则不同（msdb 目录列 `Latin1_General_CI_AS_KS_WS`，容器服务器 `SQL_Latin1_General_CP1_CI_AS`），拼接时需 `COLLATE DATABASE_DEFAULT`（夹具已踩到）。
2. IMPERSONATE：另查 `HAS_PERMS_BY_NAME(N'dbo', N'USER', N'IMPERSONATE')`（msdb 内），为真即违规。上一节的反例 (3) 授给本人用户自己，现在两条路都拦。
3. guest：`sys.user_token` 里只有「本登录在该库用什么身份进入」：有本人用户时是本人用户加其角色与 public，**没有 guest**；没有本人用户而 guest 启用时（msdb 默认启用）是 guest 加 public。因此 (a) 本人用户存在时，授给 guest 的权限到不了本登录，不算违规；(b) 本登录在 msdb 没有本人用户、只经 guest 进入时，**msdb 可进即检查令牌范围权限**（六项、IMPERSONATE、令牌范围扫描照跑），授给 guest 的 ALTER ANY SCHEMA 使 (f) 违规，原因仍 `SYSTEM_DATABASE_OWN_USER_PRIVILEGE`、定位行只 `database="msdb"`；此时没有 msdb 的 `ownUser` 信息行，也不因「无本人用户」违规（上一节第 1 条：只允许本人用户的口径不变，guest 在系统库一向允许）。**Agent 作业数不跳过**（one-coordinator 2026-10-16 裁定 B，CLAUDE.md 第 6 条）：只经 guest 进入的账号读不了 `msdb.dbo.sysjobs_view`（错误 229），作业属主无法判定，所以 (f) 为 `UNDETERMINED`（原因 `CATALOG_QUERY_FAILED`，带 SQL 错误号与 SQLState）、结果判定不了、退出判定不了码，报告写明原因，运维据此处理；若同时有权限越线，仍按"违规优先于判定不了"给 VIOLATED。RDS 平台总会自动建本人用户，该形态现实中不会出现。测试 `guestOnlyAccountCannotReadItsAgentJobsAndIsUndetermined` 固化此预期；其余 guest-only 用例让 guest 临时获得 `GRANT SELECT ON dbo.sysjobs_view`，使判定只取决于权限。上一实现对「msdb 无本人用户」不查权限（`SystemDatabaseUsers` 只在 `ownUserMemberships` 非空时调 `MsdbPrivileges`），本节要求改为 msdb 可进（`HAS_DBACCESS` 为 1）就查。
4. 空列表：msdb 与 rdscore 的本人用户权限、令牌范围扫描结果为空列表，一律是 `EMPTY_ANSWER`（判定不了，不按通过也不按违规），两边口径一致；真实 msdb 令牌里至少有 public 的授权，所以空列表只会来自读不到，不属 IT 能构造的情形，由实现的单元测试覆盖。
5. 不得误判：夹具基线账号（本人用户加三个 SQLAgent 角色）、one-ops 样本形状、只经 guest 且无越线的账号都仍 `PASSED`；`msdbDefaultGrantsOfPublicAndTheAgentRolesStillPass`、`msdbDefaultGrantsOfPublicAndTheAgentRolesDoNotRefuseStartup` 先核实上述默认授权在容器里确实存在，再断言通过，显式证明扫描不把 public 默认的两条 VIEW ANY COLUMN * KEY DEFINITION 与 SQLAgent 角色、public 的默认对象级授权当违规。
6. 新原因码不增，沿用 `SYSTEM_DATABASE_OWN_USER_PRIVILEGE`，`count` 仍为违规的库数（1）。
7. 夹具反例（接上一节第 6 条，同一清单、同一套全局性约束，`ownMsdbPrivileges` 另报 `EFFECTIVE IMPERSONATE` 与 `TOKEN <主体> <class> <state> <权限>` 行，每例先由夹具以被核实账号身份断言前提）：(9) GRANT TAKE OWNERSHIP ON DATABASE::msdb TO SQLAgentUserRole；(10) GRANT ALTER ANY DATABASE DDL TRIGGER TO public；(11) GRANT CREATE FUNCTION TO SQLAgentUserRole；(12) GRANT IMPERSONATE ON USER::dbo TO SQLAgentUserRole（用户级，六项为 0）；(13) GRANT ALTER ON SCHEMA::dbo TO SQLAgentUserRole；(14) GRANT ALTER ON OBJECT::dbo.sp_help_category TO public（msdb 自带的无害 dbo 过程，不新建对象）；(15) GRANT ALTER ANY SCHEMA TO guest，只对没有本人用户的账号成立，故不在清单里，单独用 `guestOnlyAccount`（`DROP USER` 本人用户）：`alterAnySchemaGrantedToGuestIsAViolationWhenMsdbIsReachedThroughGuestOnly`、`alterAnySchemaGrantedToGuestRefusesStartupWhenMsdbIsReachedThroughGuestOnly`，对照 `guestOnlyAccountWithTheDefaultGuestGrantsPasses` / `…Starts`，以及 `aGrantToGuestDoesNotMakeAnAccountWithItsOwnMsdbUserViolate` / `aGrantToGuestDoesNotRefuseAnAccountWithItsOwnMsdbUser`。既有反例 (1)(3)(5)(6)(7) 的预期行相应加上 `EFFECTIVE IMPERSONATE`（CONTROL、db_owner 蕴含它）与 `TOKEN …` 行。(9)–(14) 全局性，(15) 改 guest，也是全局性，都在 finally 撤销，`createRdsPlatform` 幂等清理。

### (f) msdb 令牌角色与拥有关系（复审 H-A / M-A；调度确认，依据 SQL Server 默认）

依据：opus 安全复审 H-A、M-A。db_ssisadmin、dc_admin（微软文档称可提权到 sysadmin）、DatabaseMailUserRole 等角色的能力全是对象级 EXECUTE / SELECT，落在上一节令牌范围扫描的类别允许清单内；经 `ALTER ROLE <r> ADD MEMBER SQLAgentUserRole`（账号有本人用户时）或 `ALTER ROLE <r> ADD MEMBER guest`（只经 guest 进 msdb 时）进入令牌后，令牌授权扫描与六项有效权限都看不到，msdb 又是 TRUSTWORTHY ON。故不再按授权判断角色，而是按**成员关系**。同一套 `ReadOnlyBaseline`，影响只读核实命令、启动校验与运行期重检。

1. 令牌角色（H-A）：RDS 上的角色集合待 one-ops 第 6 项实测（INFRA_RUNBOOK 第 6 项，只记是否恰为 public 加三个 SQLAgent 角色的子集）。以本登录身份在 msdb 内读 `sys.user_token WHERE type = 'ROLE'`，除 `public`、`SQLAgentUserRole`、`SQLAgentReaderRole`、`SQLAgentOperatorRole`（按名称精确匹配，不用通配）外出现**任何**角色即违规，不论其能力（含 db_owner 等固定角色、自建的无授权角色、嵌套得来的角色）。原因 `SYSTEM_DATABASE_OWN_USER_PRIVILEGE`，`count` 为违规的库数（1），定位行只有 `reason database`（`database="msdb"`），**不输出角色名**；`ownUser` 信息行不变（嵌套不改本人用户自己的成员关系）。本人用户存在与只经 guest 进入两种形态都查（msdb 可进即查）。
2. 拥有关系（M-A）：令牌内主体（本人用户或 guest，加上允许的 public 与三个 SQLAgent 角色）在 msdb 拥有角色（`sys.database_principals.owning_principal_id`）、schema（`sys.schemas.principal_id`）或对象（`sys.objects.principal_id`）的个数大于 0 即违规，同一原因码与定位行形状，不输出名称。**平台默认放行**：guest 拥有 schema `guest`，三个 SQLAgent 角色各拥有同名 schema（容器实测基线账号令牌里就有这三个；夹具以 sa 核实这一前提，`assertPlatformSchemasAreOwnedByTheirNamesakes`）；dbo、sys、INFORMATION_SCHEMA、固定角色同名 schema 等由 dbo 或各自的固定角色拥有，不在令牌内，不算。放行的是四个精确配对：SQLAgentUserRole、SQLAgentReaderRole、SQLAgentOperatorRole 各拥有同名 schema，guest 拥有 schema `guest`（调度确认，依据 SQL Server 默认）；不放行任意同名。RDS 上的拥有关系可能多于容器，待 one-ops 第 7 项实测（拥有关系类别与计数，只出类别与计数；INFRA_RUNBOOK 第 7 项），有多出的按实测补入并写明来源。例外只限这四个主体与各自同名的 schema，不是「与拥有者同名的 schema 一律放行」（本人用户自建同名 schema 仍是违规）。
3. 判定不了：令牌角色读不到、拥有关系读不到，或结果为空（真实令牌至少有 public）沿用既有口径：`EMPTY_ANSWER`、`NO_ANSWER`、`CATALOG_QUERY_FAILED`，不按通过也不按违规。黑盒构造不出，留给实现的单元测试。
4. 不误判：夹具基线账号（本人用户加三个 SQLAgent 角色）、one-ops 样本形状、只经 guest 且无越线的账号（令牌恰为 guest 与 public，guest 拥有自己的 schema，SQLAgent 角色拥有同名 schema）、默认授权用例都仍 `PASSED`；这些用例里 `ownMsdbPrivileges` 断言为空，现在包含令牌角色与拥有关系两项。
5. 夹具反例（`MSDB_COUNTEREXAMPLES` 追加，两个 IT 共用，同一套全局性约束；`ownMsdbPrivileges` 另报 `TOKEN_ROLE <角色>`、`OWNS ROLE|SCHEMA|OBJECT`，只供夹具核对，命令不输出）：
   - A1 = (16)：`ALTER ROLE db_ssisadmin ADD MEMBER SQLAgentUserRole`（全局性，finally 撤销；容器实测 SQL Server 2022 的 db_ssisadmin 在 msdb 另有对象级 INSERT / UPDATE / DELETE / REFERENCES，现行令牌扫描已经因此拦下它，所以 A1 单独钉不住「按成员关系」，钉住它的是 A2、A3、M1、M2）；
   - A3 = (17)：自建无授权角色 `forcitis_nested_role`，`ADD MEMBER SQLAgentUserRole`（全局性，撤销时先 DROP MEMBER 再 DROP ROLE）；
   - A4 = 既有 (5)：`ALTER ROLE db_owner ADD MEMBER SQLAgentUserRole`；该例此前已被六项有效权限拦下，现在另报 `TOKEN_ROLE db_owner`，没有新增用例；
   - M1 = (18)：`CREATE ROLE <登录>_owned_role AUTHORIZATION <本人用户>`（只影响本账号）；
   - M2 = (19)：`CREATE SCHEMA <登录>_owned_schema AUTHORIZATION <本人用户>`（只影响本账号）；
   - A2（`DatabaseMailUserRole` 嵌入 guest，账号只经 guest 进入）不在清单（清单面向有本人用户的账号），单独用 `guestOnlyAccount`：`aRoleNestedIntoGuestRefusesStartupWhenMsdbIsReachedThroughGuestOnly`、`aRoleNestedIntoGuestIsAViolationWhenMsdbIsReachedThroughGuestOnly`（让 guest 临时读 `sysjobs_view`，使判定只取决于令牌；finally 撤销）；对照 `guestOwningItsOwnSchemaDoesNotRefuseStartup` 与既有 `guestOnlyAccountWithTheDefaultGuestGrantsPasses` / `…Starts`。
   - 命令路径另断言输出不含令牌角色名（`msdbOwnUserPrivilegeBeyondTheBaselineIsAViolation` 对 `TOKEN_ROLE` 行，A2 对 `DatabaseMailUserRole`）。`createRdsPlatform` 幂等清理上述全局性改动。

### (g) 的被拒绝覆盖判定（RULING-ADR37-10 的收窄解释，安全判定放宽）

RULING-ADR37-10 定「库内 class≠0 的授权只允许 DENY 或 SELECT 的 GRANT，public 也算」。依 ADR-0037 第 4 项 (b)「库级 DENY 覆盖 schema 级、对象级与经自定义角色得到的 GRANT」，one-coordinator 2026-10-15 补充、one-1a-next-3 加安全条件，收窄为按有效权限判定。影响只读核实命令、启动校验与运行期重检（同一套 `ReadOnlyBaseline`）：

1. 一条授给 public 或本账号、class 为 `OBJECT_OR_COLUMN`（对象级与列级）或 `SCHEMA`（schema 级）的 `GRANT`（state `G`）是「被拒绝覆盖的授权」（overridden），当且仅当：其权限名在库级 DENY 清单（ADR-0037 第 4 项 (b) 的 11 项：INSERT、UPDATE、DELETE、ALTER、EXECUTE、REFERENCES、CREATE TABLE、CREATE VIEW、CREATE PROCEDURE、CREATE FUNCTION、CREATE SCHEMA）内，且本账号自己有同名的 `DENY`——库级（class 0，`by=DATABASE_DENY`）或该安全对象所属 schema 上（class SCHEMA，`by=SCHEMA_DENY`）；两者都有时写 `DATABASE_DENY`。`SCHEMA_DENY` 要求知道安全对象所属 schema：账号看不见该对象（所属 schema 取不到）时无法确认 schema 级 DENY 是否同一 schema，按保守口径判违规（`violation`，`schema=-`、`object=-`），不得按「看不见即被覆盖」处理（看不见源于 DENY，但也可能是授给 public 的 DENY，而授给 public 的 DENY 不算本账号的 DENY）；`DATABASE_DENY` 不需要所属 schema，看不见的对象照常可判被拒绝覆盖。按权限名精确匹配，不按「有库级 DENY 即全覆盖」；授给 public 的 DENY 不算本账号的 DENY。
   - 可被覆盖的 class 只有这两种（opus 安全复审 Medium-1，负责会话 one-1a-par 收窄，依裁定原文「对象级 / schema 级 GRANT」）：`TYPE`、`ASSEMBLY`、`DATABASE_PRINCIPAL`、`XML_SCHEMA_COLLECTION`、证书、密钥等其他 class 上的 GRANT，即使权限名在 11 项清单内、本账号也有同名库级 DENY，一律按原规则判违规（`ReadOnlyVerificationCommandIT.grantOnATypeStaysAViolation`：TYPE 上 GRANT REFERENCES + 库级 DENY REFERENCES）。
   - 列级 GRANT（`OBJECT_OR_COLUMN`，`minor_id` 为列号）折叠到所在表上：同一表、同一权限、同一 grantee 的多列授权只出一行，`objectId` 为表的编号；覆盖判定与对象级相同（`columnGrantsFoldIntoTheTableAndAreOverriddenByTheSchemaDeny`）。
2. 不在清单内的权限一律仍是违规，即使本账号另有同名 DENY：尤其 CONTROL、TAKE OWNERSHIP、IMPERSONATE、VIEW DEFINITION；CONTROL 隐含的权限不因其中某一项被 DENY 而视为整体覆盖。`GRANT_WITH_GRANT_OPTION` 一律违规。
3. 判定不了 DENY 是否存在时，可被覆盖的授权按判定不了（`UNDETERMINED`），不按被拒绝覆盖：DENY 查询失败；或本账号的 DENY 行（class 0 与 SCHEMA）一行都没有（真实账号总有 11 条库级 DENY）。这类授权不出定位行。黑盒覆盖了「一行都没有」；「只有 DENY 查询失败而授权查询成功」黑盒构造不出（两者读同一目录视图），**留给实现的单元测试（待补）**。
4. 被拒绝覆盖的授权在启动校验与运行期重检中不拒绝、不告警；在只读核实命令中出 `overridden` 信息行。

### 只由只读核实命令输出

定位字段（库名、schema 名、对象名、权限名、grantee）与 `ownUser` 信息行的库名、角色名只出现在只读核实命令的定位行与信息行。启动校验的拒绝原因、日志，运行期重检的 ALERT、日志与同步结果说明中不出现 schema 名与对象名（`ReadOnlyAccountCheckIT.startupRefusalNamesNoSchemaOrObject`、`recheckAlertNamesNoSchemaOrObject`）。

### 用例

`ReadOnlyVerificationCommandIT`：`systemDatabaseWithAnOwnUserIsLocatedByName`（C-14 (f) 纳入基线后改为 master 与 tempdb 两库）、`msdbOwnUserInASubsetOfTheAgentRolesPasses`、`msdbOwnUserWithAnExtraRoleIsAViolation`、`rdscoreOwnUserInARoleIsAViolation`、`rdscoreOwnUserWithMoreThanConnectIsAViolation`、`ownedAgentJobIsAViolation`、`unreadableAgentJobsAreUndetermined`、`msdbOwnUserPrivilegeBeyondTheBaselineIsAViolation`（8 个反例）、`oneOpsShapedMsdbGrantsStillPass`，`baselineAccountPassesEveryItem` 另断言 `ownUser` 信息行；`publicExecuteOverriddenByTheDatabaseDenyPasses`、`controlAndTakeOwnershipStayViolationsBesideAnOverriddenGrant`、`schemaLevelGrantsCarryTheObjectPlaceholder`、`aSchemaDenyOverridesOnlyInItsOwnSchema`、`columnGrantsFoldIntoTheTableAndAreOverriddenByTheSchemaDeny`、`grantOnATypeStaysAViolation`、`noDenyAnswerLeavesTheGrantUndetermined`、`grantsToTheLoginItselfShowTheSelfPlaceholder`、`locatorValuesAreEscaped`。(g) 的用例在各自新建的库里（`isolatedAccount`），授给 public 的授权不波及其他用例。`ReadOnlyAccountCheckIT`：`overriddenPublicGrantsDoNotRefuseStartup` 及上节两条；复审 H-1 的 public 反例由 GRANT UPDATE 改为 GRANT CONTROL，另加 GRANT TAKE OWNERSHIP；C-14 (f) 纳入基线：`ownedAgentJobRefusesStartupWithoutTheJobName`、`agentJobAfterStartupFailsTheRecheckWithoutTheJobName`；复审 H-1：`MSDB_VIOLATIONS`（8 个反例，每例三条连接）、`msdbCounterexampleReallyHoldsItsPrivilegeAndUndoRestoresTheBaseline`、`oneOpsShapedMsdbGrantsDoNotRefuseStartup`；`VIOLATIONS` 加 msdb 多角色 DatabaseMailUserRole、rdscore 本人用户入 db_datareader、拥有 Agent 作业三例，复审 H-2 的 msdb 例改为「本人用户另加 db_owner」（夹具已建本人用户）。每个账号都有 `ownUser` 行，所以 (g) 各用例的「定位行恰为…」断言由 `run.locators()` 改为 `run.findings()`（只含 `violation` 与 `overridden`；`ownUser` 由 `run.ownUsers()` 取），所断言的 (f)(g) 定位行不变。


### (f) msdb 拥有关系补全（复审 M-1；调度 2026-10-16）

依据：opus 复核 M-1。上一节的拥有关系只数角色、schema、对象三类，漏了两处：(1) 拥有者在令牌内的其他可保护对象（拥有者对其有 CONTROL）；(2) 放行的四个平台 schema 里的对象（`sys.objects.principal_id` 为 NULL，随 schema 拥有者隐含拥有，拥有者在令牌内）。管理员误配（`ALTER AUTHORIZATION ON CERTIFICATE::<映射到高权限用户的证书> TO [账号]`，或在 `SQLAgentUserRole` schema 里建过程）后，账号可凭证书的 CONTROL 与过程的 ALTER 做 `ADD SIGNATURE`，以证书用户的权限运行，旧检查计数仍为 0。

1. 在 msdb 内（令牌内主体，同上一节口径）追加计数，任一大于 0 即违规：`sys.certificates`、`sys.asymmetric_keys`、`sys.symmetric_keys`、`sys.assemblies`、`sys.xml_schema_collections` 的 `principal_id` 在令牌内；`sys.types WHERE is_user_defined = 1` 的 `principal_id` 在令牌内；`sys.objects WHERE principal_id IS NULL` 且所在 schema 是 `guest`、`SQLAgentUserRole`、`SQLAgentReaderRole`、`SQLAgentOperatorRole` 之一。默认状态这些计数都应为 0（见第 3 条）。
2. 原因码与定位行不变：`SYSTEM_DATABASE_OWN_USER_PRIVILEGE`，`count` 为违规的库数（1），定位行只有 `reason database`（`database="msdb"`），不输出证书、类型、过程名。默认状态与 one-ops 样本仍 `PASSED`。
3. 默认值核实：测试代理 2026-10-16 在 SQL Server 2022 容器以夹具基线账号实测，上述全部类别 `ownMsdbPrivileges` 均为空（基线用例断言它为空，故任何一类默认非 0 都会使基线用例变红）。RDS（SQL Server 2016）上是否为 0，并入 one-ops 第 7 项实测。
4. 夹具反例（`MSDB_COUNTEREXAMPLES` 追加，两个 IT 共用；`ownMsdbPrivileges` 另报 `OWNS CERTIFICATE|KEY|ASSEMBLY|TYPE|XML_SCHEMA_COLLECTION|SCHEMA_OBJECT`，只报种类，只供夹具核对，命令不输出）：
   - (20)：`CREATE CERTIFICATE <登录>_owned_cert AUTHORIZATION <本人用户> ENCRYPTION BY PASSWORD = <运行期随机生成> WITH SUBJECT = ...`（msdb 没有库主密钥，必须用口令加密私钥；口令运行期生成、不写字面量；只影响本账号；撤销 `DROP CERTIFICATE`）；
   - (21)：`CREATE TYPE dbo.<登录>_owned_type FROM int`，再 `ALTER AUTHORIZATION ON TYPE::... TO <本人用户>`（别名类型的 CREATE TYPE 没有 AUTHORIZATION 子句，所以分两步；只影响本账号；撤销 `DROP TYPE`）；
   - (22)：`CREATE PROCEDURE SQLAgentUserRole.forcitis_schema_owned_proc AS SELECT 1`（拥有者随 schema，`principal_id` NULL；全局性：msdb 内所有登录都受影响，finally 撤销，`createRdsPlatform` 幂等清理）。
   - 不做：程序集（`CREATE ASSEMBLY` 需要 CLR 编译的 dll 字节与 `clr enabled`、TRUSTWORTHY 或签名，容器黑盒下既重又改服务器配置，可行性不佳）；非对称/对称密钥、`xml_schema_collections` 的计数在夹具查询里有，但没有对应反例；若需要，同 (20) 写法补。夹具查询对这几类仍报种类，生产查询要覆盖它们，由实现的单元测试钉。
5. 口径差异：夹具的 `OWNED_BY_THE_TOKEN` 只统计本人用户或 guest 加允许的四个角色，生产统计令牌内全部主体，比夹具严；令牌里出现其他角色时先被令牌角色检查拦下，所以不会造成夹具误判。

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
- 名称：唯一例外是第 3a 节定位行与信息行中的字段（(f) 库名、本人用户的角色名；(g) schema、对象、权限、grantee），作业名不输出，其他项与其他原因的名称照旧不输出；定位行中本账号的用户名也写占位 `SELF`；
- 数据。

## 6. 口令只从环境读

命令行出现口令类参数即拒绝，退出 2，不做核实，不回显参数值。测试覆盖四种参数名（同一环境去掉该参数即通过）：`--password`、组织源连接配置键中的 password 属性、组织源口令环境变量名、`--spring.datasource.password`（原样写法见测试类 `passwordOnTheCommandLineIsRefused`）。建议实现按「参数名中含 `password`（不分大小写）」判定，另可加 `secret`、`pwd`。

## 7. 不监听端口、不迁移、不起作业

- 不监听任何 TCP 端口：测试以 `-Djava.nio.channels.spi.SelectorProvider` 装入探针（`ListeningSocketProbe`，`-Xbootclasspath/a` 加 `--add-exports java.base/sun.nio.ch=ALL-UNNAMED`），进程打开任何 server socket channel（Tomcat 的业务端口与管理端口、Netty 都经此）都会在标准错误写出标记；输出中出现标记即失败。对照用例 `probeSeesAProcessThatListens` 证明探针有效。
- 不跑迁移：命令结束后服务库（空 PostgreSQL）中没有任何表、没有新 schema（因此没有 Flyway 历史表）。
- 不起调度与事件发布作业：黑盒不直接观察（命令跑完即退出进程，120 秒内须自行结束）；实现以档案关闭，复审核对。
