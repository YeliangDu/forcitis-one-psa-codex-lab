# C-14 剧本：只读启动校验拒绝路径的真实登录核实（计划，待用户批准）

依据：ADR-0037 第 4 项第 7 条；PREDEPLOY_CHECKLIST C-14；校验实现 #163（`ReadOnlyBaseline`、`JdbcReadOnlyCatalog`）；阻塞项 101（安全复审 H-1、H-2）。标记为 (a)–(g)：(g) 库内 class<>0 的授权只允许 DENY 或 SELECT 的 GRANT（public 主体也算）；服务器上针对登录的授权并入 (d)；系统库只能经 guest 进入并入 (f)。dev 首次部署前完成。**用户批准前不建登录、不建库、不改授权。**

执行方式：在 ECS 上从签名发布包（rc.3 起，含核实命令的定位输出与 #228）解出 jar，直接跑只读命令；不需要先部署 dev（阻塞项 101 的删除条件②据此成立）。正式账号核实先单独执行；通过后，准备、逐项与收尾由 `c14_run.py`（#742 已删除，原文见 `git show 0072e5e15^:docs/modernization/phase1a/infra/handoff/B-5-dba/tools/c14_run.py`） 编排（须先合入 main 才能运行，通道脚本只认 origin/main 上的文件）：每项依次「对照计数 → 偏离 → 跑校验 → 回退 → 计数复原」，任一偏离意外失败、第 1 项不通过或计数不复原即回退当前项、删除全部探针对象后停止；结果逐行写入结果文件（不含凭据）。

## 范围

- 不动四个正式登录（one_a8_views_ro、psa_one_legacy_ro、one_legacy_names_ro、one_evidence_ro），不碰 a8_db、ruoyi-vue-pro。
- RDS SQL Server rm-bp1690ep53t9778dp 上新建：临时登录 `one_readonly_probe`；空库 `one_readonly_probe_db`（第 9 项另建 `one_readonly_probe_db2`）；探针库内的辅助主体（第 6–8 项）与一张空表 `dbo.probe_t`（第 12、14 项）。全部做完删除。
- 例外：第 17 项在系统库 master 中为**探针登录**建用户，做完立即删除；不改系统库里任何已有主体与对象；平台拒绝时记「平台不可构造」，不换方式。依据：用户 2026-10-15 追加批准（原批准只覆盖探针库）。第 16 项（msdb 加入 db_owner）用户决定不做：msdb 存有实例的作业与备份历史，共享实例上不构造。
- 执行账号：老系统主数据源 u8c（RDS 高权限账号，只用于这些授权变更，ADR-0037 第 5 项第 4 条）；探针以自己的口令真实登录跑校验。

## 应用侧核实入口：选 one-1a-next-2 补的只读命令

- 选它的理由：第 7 条要核实的是**实现**的拒绝路径；只读命令执行的就是 `ReadOnlyBaseline.violations` 本身，拿到的标记即应用启动时会给出的标记。`docs/ops/dba-scripts/read-all/readonly-check.sql` 只覆盖同一组查询，比对规则要人工复述 `ReadOnlyBaseline`，核实的是我对代码的理解而不是代码。
- 前提：命令在签名发布包中（#163 与命令合并后的 rc.2）；在 ECS 上从发布包中的 jar 运行（解到临时目录，不安装、不切换、不启动服务），连接参数与探针口令从 ECS 文件读取。
- 若命令赶不上：退回 `readonly_check.sql` 加人工比对，执行记录中注明「未经应用代码核实」，并保留 C-14 未关闭。

## 口令与记录

- 探针账号经 RDS 云接口创建与删除（`aliyun rds CreateAccount / DeleteAccount`，普通账号 `one_readonly_probe`）。口令由 `c14_run.py` 在进程内存里运行时生成（`secrets`，满足 RDS 口令格式，不超 32 位），只经云接口命令行（本机，创建的一瞬间）和以新 ECS 临时公钥加密的密文，存 ECS `/etc/forcitis-one/readonly-probe.env`（root 0600），不经会话、不落盘、不打印；结束时删除。依据：2026-10-16 实测，T-SQL 的 CREATE / DROP LOGIN 在 msdb、rdscore 留下执行账号删不掉的孤儿用户，而 RDS 托管的删除会清 msdb（用户 2026-10-16 选 B、批准用云接口）。
- `readonly-probe.env` 只有三个变量，只读核实命令以 `--connection=probe` 读取：`READONLY_PROBE_DB_URL`（与 one.env 的 `LEGACY_DB_RO_URL` 同形，`databaseName=one_readonly_probe_db`，TLS 参数与 one.env 中 SQL Server 的 URL 相同：`encrypt=true;trustServerCertificate=false;trustStore=/etc/forcitis-one/sqlserver-ca.p12;trustStorePassword=…`）、`READONLY_PROBE_DB_USERNAME`（`one_readonly_probe`）、`READONLY_PROBE_DB_PASSWORD`。第 9 项连的仍是 `one_readonly_probe_db`（核实的是能否进入基线外的库），不改 URL。
- 记录只写：每项的拒绝标记（(a)–(g) 与「通过」）、错误号；不记消息原文与任何值。
- 每项前后各取一次探针的有效权限计数（库级、服务器级 `fn_my_permissions` 的行数），回退后须回到对照值。
- RESOURCES.md 登记临时登录与两个探针库（建与删各一行）；LEGACY_READ_REGISTER 不涉及（不读老系统与 A8 数据）。

## 时间窗口与共享实例影响

- 约 75 分钟，工作日非高峰（建议 12:00–13:00 或 18:30 以后），提前告知 one-coordinator。
- 影响：实例上临时多一个登录、两个空库，以及第 17 项在 master 中一个短暂存在的探针用户（做完即删），另有 RDS 自动给探针在 msdb、rdscore 建的用户（msdb 的随 DeleteAccount 清除；**rdscore 的是平台固有残留，永久留 1 个，见下**）（几 MB，进入实例备份，删库后随备份保留期过期）；所有偏离只在探针库内或针对探针登录；不改 public、不改其他库，对 a8_db、ruoyi-vue-pro、it_portal 等库没有锁与授权变化。第 10 项的服务器级 DENY 预期被平台拒绝（4613），不产生变更。
- 停止条件：任一回退后计数不回到对照值、对照项不通过、可见性对照不通过、出现对探针库以外对象的任何变更、第 14 项之前的可见性核实读不到或为 0 → 立即停下（脚本先回退当前项并执行收尾）报 one-coordinator。

## 准备（批准后执行）

1. 云接口：`CreateAccount`（普通账号 `one_readonly_probe`，口令运行时生成），等账号 `Available`（至多 5 分钟）。记请求号。
2. master 上下文建库、探针库内建用户与授权（执行账号 u8c）：

```sql
CREATE DATABASE [one_readonly_probe_db];   -- 若 T-SQL 建库被平台拒绝：停下，改用 RDS CreateDatabase 前先报
-- one_readonly_probe_db 上下文：与正式账号同形
CREATE USER [one_readonly_probe] FOR LOGIN [one_readonly_probe] WITH DEFAULT_SCHEMA = dbo;
CREATE TABLE dbo.probe_t (id int);   -- 第 12、14 项与可见性核实用，属主 dbo
GRANT CONNECT, SELECT ON DATABASE::[one_readonly_probe_db] TO [one_readonly_probe];
DENY INSERT, UPDATE, DELETE, ALTER, EXECUTE, REFERENCES, CREATE TABLE, CREATE VIEW, CREATE PROCEDURE, CREATE FUNCTION, CREATE SCHEMA
  ON DATABASE::[one_readonly_probe_db] TO [one_readonly_probe];
```

## 回读对照（准备之后、任何偏离之前；失败退出码 2）

回读用 `USER_ID(N'one_readonly_probe')`（不受元数据可见性限制；`sys.database_principals` 对执行账号看不见别人的用户，2026-10-16 实测）。账号建好后立即读一次：登录数须为 1、探针库 1、master 用户 0、msdb 与 rdscore 的用户号须大于 0（RDS 已建），否则 Stop，照常收尾但**强制退出码 2 并告警**——回读读不出 RDS 刚建的用户，收尾后的 0 就没有证明力。

## 正式运行时账号核实（逐项之前）

- 用只读命令对 one_a8_views_ro、psa_one_legacy_ro、one_legacy_names_ro 各跑一次，连接参数与口令取自 `/etc/forcitis-one/one.env`；不改任何授权，不在探针库里做。
- 预期三个都「通过」；记录只写每个账号的标记与错误号。
- 任一账号出现 (g)（例如老库把 EXECUTE 授给了 public）或其他拒绝标记：停下报 one-coordinator，不临场调整授权，也不继续做逐项。

## 预检与停止（`c14_run.py` 开工前，什么都没改，失败退出码 3）

- 结果文件是新名字且可写（已存在则拒绝，不覆盖旧结果）；运行方式：tmux 或 nohup，输出重定向到文件，不经管道（终端断开时收尾也要能做完）。
- `app.jar` 的 SHA-256 与参数一致；ECS 上没有残留的 `readonly-probe.env`。
- 执行账号能打开 msdb、rdscore（回读要在这两库里取 `USER_ID`）。
- RDS 上不存在同名账号（`DescribeAccounts`）。
- 回读（探针登录、探针库、master 用户、msdb 用户）开工前必须全为 0；rdscore 用户只记录，不要求为 0（永久已知残留，见下）。
- 收尾顺序：先停 ECS 上的 `one-c14-probe-*` 单元，再杀探针登录的会话，然后逐条删除探针库和 master 用户，最后经云接口 `DeleteAccount` 删探针账号（它同时清 msdb 用户），回读前四项为 0、rdscore 用户只记录；任何一步失败都继续做下一步，最后不干净则告警、退出码 2。收到 SIGTERM / SIGHUP / Ctrl-C 也走同一条收尾。

## 逐项（偏离 → 预期标记 → 回退）

偏离均在 `one_readonly_probe_db` 上下文执行（第 9、10 项另注），每项：对照计数 → 偏离 → 跑校验 → 回退 → 计数复原。

| # | 偏离 SQL | 预期 | 回退 SQL |
|---|---|---|---|
| 1 | 无（基线） | 通过 | — |
| 2 | `REVOKE INSERT ON DATABASE::[one_readonly_probe_db] FROM [one_readonly_probe]; GRANT INSERT ON SCHEMA::dbo TO [one_readonly_probe];` | (b)（DENY 缺 INSERT）、(g)（schema 级 INSERT 的 GRANT），可能另有 (a) | `REVOKE INSERT ON SCHEMA::dbo FROM [one_readonly_probe]; DENY INSERT ON DATABASE::[one_readonly_probe_db] TO [one_readonly_probe];` |
| 3 | `GRANT VIEW DEFINITION ON DATABASE::[one_readonly_probe_db] TO [one_readonly_probe];` | (a)（多出 VIEW DEFINITION）；(b)（GRANT 不再恰为 CONNECT、SELECT） | `REVOKE VIEW DEFINITION ON DATABASE::[one_readonly_probe_db] FROM [one_readonly_probe];` |
| 4 | `ALTER ROLE db_datareader ADD MEMBER [one_readonly_probe];` | (c)，可能另有 (a) | `ALTER ROLE db_datareader DROP MEMBER [one_readonly_probe];` |
| 5 | `CREATE ROLE [probe_select_role]; GRANT SELECT ON DATABASE::[one_readonly_probe_db] TO [probe_select_role]; ALTER ROLE [probe_select_role] ADD MEMBER [one_readonly_probe];` | (c) | `ALTER ROLE [probe_select_role] DROP MEMBER [one_readonly_probe]; DROP ROLE [probe_select_role];` |
| 6 | `CREATE SCHEMA [probe_owned] AUTHORIZATION [one_readonly_probe];` | (e)（拥有 schema） | `DROP SCHEMA [probe_owned];` |
| 7 | `CREATE ROLE [probe_owned_role] AUTHORIZATION [one_readonly_probe];` | (e)（拥有角色） | `DROP ROLE [probe_owned_role];` |
| 8 | `CREATE USER [probe_target] WITHOUT LOGIN; GRANT IMPERSONATE ON USER::[probe_target] TO [one_readonly_probe];` | (e)（主体级授权），另有 (g)（class<>0 的 IMPERSONATE） | `REVOKE IMPERSONATE ON USER::[probe_target] FROM [one_readonly_probe]; DROP USER [probe_target];` |
| 9 | master：`CREATE DATABASE [one_readonly_probe_db2];` 其中：`CREATE USER [one_readonly_probe] FOR LOGIN [one_readonly_probe];` | (f)（可访问基线外的库） | 其中 `DROP USER [one_readonly_probe];`；master：`DROP DATABASE [one_readonly_probe_db2];` |
| 10 | master：`DENY VIEW SERVER STATE TO [one_readonly_probe];` | 预期平台报 4613 → 记「平台不可构造」；若执行成功则预期 (d) | 成功时：`REVOKE VIEW SERVER STATE FROM [one_readonly_probe];` |
| 11 | `DENY SELECT ON sys.database_permissions TO [one_readonly_probe];` | 无法判定、拒绝启动：(b)、(e)、(g) 报 cannot be determined | `REVOKE SELECT ON sys.database_permissions FROM [one_readonly_probe];` |
| 12 | `GRANT TAKE OWNERSHIP ON OBJECT::dbo.probe_t TO [one_readonly_probe];` | (g) | `REVOKE TAKE OWNERSHIP ON OBJECT::dbo.probe_t FROM [one_readonly_probe];` |
| 13 | `GRANT CONTROL ON SCHEMA::dbo TO [one_readonly_probe];` | (g)，可能另有 (a) | `REVOKE CONTROL ON SCHEMA::dbo FROM [one_readonly_probe];` |
| 14 | `GRANT UPDATE ON OBJECT::dbo.probe_t TO public;` | (g)（public 主体） | `REVOKE UPDATE ON OBJECT::dbo.probe_t FROM public;` |
| 15 | master：`GRANT IMPERSONATE ON LOGIN::sa TO [one_readonly_probe];` | 预期平台报 4613 → 记「平台不可构造」；若成功则预期 (d)；RDS 上没有 sa 登录时报 15151，脚本按意外失败停下，此时在执行记录写明「sa 不存在，该项不可构造」，其余项另行重跑（需 one-coordinator 同意） | 成功时：`REVOKE IMPERSONATE ON LOGIN::sa FROM [one_readonly_probe];` |
| 17 | master：`CREATE USER [one_readonly_probe] FOR LOGIN [one_readonly_probe];`（用户 2026-10-15 追加批准，在原批准范围外） | (f) | master：`DROP USER [one_readonly_probe];` |

**可见性核实（第 14 项之前做，结论决定 (g) 在 RDS 上是否有效）**：在探针库 `GRANT SELECT ON OBJECT::dbo.probe_t TO public;`，以探针登录执行 `SELECT COUNT(*) FROM sys.database_permissions WHERE grantee_principal_id = 0 AND class <> 0`，再 `REVOKE SELECT ON OBJECT::dbo.probe_t FROM public;`。计数为 0（SQL Server 2016 上运行时账号看不到 public 在用户对象上的授权，而容器 2022 上能看到）→ **停下报 one-coordinator 与 one-1a-next-2**，不做第 14 项。

平台上不构造、由 `ReadOnlyAccountCheckIT` 覆盖：第 16 项「msdb 中本登录的用户加入 db_owner」（用户 2026-10-15 决定不做）；CONTROL SERVER、加入 securityadmin、多出服务器级权限（u8c 无服务器级授予权限，4613；见 B-5 README 风险登记）。

## H-2：不做（平台不可构造）

原计划试一次把探针的 msdb 用户移出 SQLAgentOperatorRole（用户 2026-10-16 批准的一次性例外）。2026-10-16 `c14_u8c_status` 实测执行账号 u8c 在 msdb 不是 db_owner，没有 ALTER ANY ROLE / ALTER ANY USER / VIEW DEFINITION（rdscore 同），也不是 sysadmin / securityadmin，做不了这条语句，也无法授权给自己（见 PREDEPLOY_CHECKLIST C-14）。依据 B-5 的原则「平台拒绝时记平台不可构造，不换方式」，`c14_run.py` 不再带 `--msdb-role-trial`。(f) 的结论维持 #277 基线：RDS 自动给登录建的 msdb、rdscore 用户及其 SQLAgent 三角色为接受项。

## rdscore 永久已知残留

RDS 给每个新账号在 rdscore 建的用户，删账号时不清（实测 `one_readonly_probe`：删账号后 rdscore `USER_ID` 仍为 30、msdb 已清），u8c 无权删；同名重建沿用同一用户号，不累积。接受为永久已知残留：rdscore 里固定留 1 个名为 `one_readonly_probe` 的无登录用户（用户 2026-10-16 决定，intent 2026-10-16-160509）。登记于 RESOURCES。

## 收尾

```sql
-- 各库上下文各一条（第 17 项的 master 用户应已在回退中删除，这里再核对一次）
-- master：DROP USER [one_readonly_probe]（若存在）
-- master 上下文：
DROP DATABASE [one_readonly_probe_db];
```

随后云接口 `DeleteAccount`（删登录并清 msdb 用户）；回读（`USER_ID`）：探针登录、探针库、master 用户、msdb 用户均为 0；rdscore 用户只记录。

ECS 删除 `/etc/forcitis-one/readonly-probe.env` 与临时目录（解开的 jar）；RESOURCES.md 记删除；执行记录写入本文件与 PREDEPLOY_CHECKLIST C-14。


## 执行记录（2026-10-16，one-ops；本机结果文件 c14_result_20261016_rds*.jsonl 不进仓库）

- **第一步（rc.4，jar sha 468d229a…）**：正式账号连接 integration-seeyon-background、integration-legacy、integration-legacy-inbox 各 (a)–(g) 全 PASSED，RESULT PASSED，退出码 0（云助手 t-hz0707odaeyqv40、t-hz0707odpgkpwcg、t-hz0707oe4dvvzeo）。
- **探针逐项**（账号经 RDS 云接口建删；共三次完整运行（结果文件 rds、rds3、rds6），第 1–14 项三次完全一致，第 15、17 项取自最后一次，其创建请求 01A1458F-67B1-51D9-B189-25517BDF928B、删除请求 01A14597-035A-5B68-9D76-4D293AF38112）：第 1 项 PASSED；第 2 项 (b)(g)；第 3 项 (a)(b)；第 4、5 项 (c)；第 6、7 项 (e)；第 8 项 (e)(g)；第 9 项 (f)；第 10 项平台 4613；第 11 项 UNDETERMINED（229，无权读系统目录）；第 12、13 项 (g)；每项回退后有效权限计数与授权指纹复原。
- **第 14 项**（`public` 在用户对象上授 UPDATE）：预期 (g) public，实测 PASSED（可见性核实通过，探针看得到 public 授权）；推测因库级 DENY UPDATE 已覆盖该授权，(g) 接受「被 DENY 覆盖的 public 授权」，与正式账号 (g) 的 7 个 public EXECUTE 同口径。**口径待 one-1a-par 回答，生产前须定**（one-coordinator 定为不阻塞 dev 与阻塞项 101）。
- **第 15 项**（`GRANT IMPERSONATE ON LOGIN::sa`）：记「平台不可构造」。证据：被拒 15151；探针登录与 sa 对执行账号可见（计数均为 1）；执行账号无 CONTROL SERVER（0），对 sa 无 CONTROL（0）。
- **第 17 项**（master 为探针登录建用户）：偏离被拒 15247（无权限），按本剧本「平台拒绝时记平台不可构造、不换方式」记「平台不可构造」；(f) 的反例由第 9 项与正式账号第一步覆盖。第 16 项按用户 2026-10-15 决定不做；H-2 平台不可构造（执行账号在 msdb 无 ALTER ANY ROLE）。
- **收尾与残留**：每轮收尾后回读（USER_ID）探针登录、探针库、master 与 msdb 用户均为 0；**rdscore 里 `one_readonly_probe` 用户（用户号 30）为永久已知残留**（RDS 对任何账号都不清，同名重建沿用不累积，用户 2026-10-16 决定接受，见 RESOURCES）。ECS 与老系统服务器的暂存文件 CLEAN。
- **工具演进（同日）**：T-SQL 建删登录会在 msdb、rdscore 留孤儿用户，改经 RDS 云接口建删（msdb 随之清除，#394）；建库约 3 分钟后进 RDS 镜像，T-SQL 删库报 3743，改经 DeleteDatabase（#399）；第 15 项 15151 的判据（#407）。
- **结论**：C-14 完成（one-coordinator 2026-10-16 认定），阻塞项 101 删除。
