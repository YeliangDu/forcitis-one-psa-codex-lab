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

1. 直接授权（授给 msdb 本人用户自己的行，`sys.database_permissions`）按类别允许清单：DATABASE 类（class 0）只允许 `G CONNECT`；OBJECT_OR_COLUMN 类只允许 `G SELECT`、`G EXECUTE` 与任意 `D`；其他 class（SCHEMA、DATABASE_PRINCIPAL 等）与所有带转授权（state `W`）一律违规。
2. 有效权限兜住角色嵌套、角色与 public 的授权：以本登录身份在 msdb 内查 `HAS_PERMS_BY_NAME('msdb', 'DATABASE', p)`，p 取 CONTROL、ALTER、ALTER ANY ROLE、ALTER ANY USER、ALTER ANY SCHEMA、CREATE PROCEDURE，**任一为真即违规**。
3. 违规原因 `SYSTEM_DATABASE_OWN_USER_PRIVILEGE`（暂定，技术码，(f) 项）；`count` 为违规的库数（本节只有 msdb，故为 1），不随权限个数增减；定位行与 `SYSTEM_DATABASE_OWN_USER` 同形，字段只有 `reason database`（`database="msdb"`），不输出权限名、对象名、角色名（待裁定：是否加 `permission` 字段）。角色信息行 `ownUser` 不变（嵌套、授给角色与 public 的授权都不改本人用户的成员关系）。
4. 不误判：夹具基线账号（msdb 本人用户加三个 SQLAgent 角色，平台默认授权）在新检查下六项有效权限全假、直接授权全在清单内，仍 `PASSED`。one-ops 在 RDS 实测的 34 条平台默认授权（直接：DATABASE CONNECT G 1；OBJECT_OR_COLUMN EXECUTE G 1、SELECT G 31、SELECT D 1；其余经 SQLAgentUserRole、SQLAgentOperatorRole 继承）的形状由 `oneOpsShapedMsdbGrantsStillPass`、`oneOpsShapedMsdbGrantsDoNotRefuseStartup` 显式覆盖（容器里用 msdb 的 backupset、backupfile、backupmediaset 三个 SELECT、sp_help_category 一个 EXECUTE、restorehistory 一个 DENY SELECT 同形）。
5. 启动校验与运行期重检：违规照 (f) 拒绝或停连接，拒绝原因可含库名，不含对象名（原因文本不断言，只断言项标记 `(f)`）。
6. 夹具反例（`ReadOnlyBaselineFixture.MSDB_COUNTEREXAMPLES`，两个 IT 共用）：(1) GRANT CONTROL ON DATABASE::msdb；(2) GRANT ALTER ANY ROLE；(3) GRANT IMPERSONATE ON USER::dbo（六项有效权限都假，只有直接授权越线）；(4) GRANT ALTER ON SCHEMA::dbo 加 GRANT CREATE PROCEDURE（有效 CREATE PROCEDURE 真）；(5) `ALTER ROLE db_owner ADD MEMBER SQLAgentUserRole`；(6) GRANT CONTROL ON DATABASE::msdb TO SQLAgentUserRole；(7) GRANT ALTER ANY SCHEMA TO public（msdb 内）；(8) GRANT EXECUTE ON dbo.sp_help_category WITH GRANT OPTION（state W）。(5)(6)(7) 是**全局性**的：改的是 SQLAgent 角色或 public，msdb 内所有登录都受影响，每个用例在 finally 里撤销，`createRdsPlatform` 另在每个类初始化时幂等清理它们；同一容器（同一 fork）里的类顺序执行才安全，不得与别的用到 msdb 的类并发。夹具核对 `msdbCounterexampleReallyHoldsItsPrivilegeAndUndoRestoresTheBaseline` 证明每个反例让 `ownMsdbPrivileges` 恰报出预期的有效权限与越线的直接授权，撤销后基线核对仍通过。

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
