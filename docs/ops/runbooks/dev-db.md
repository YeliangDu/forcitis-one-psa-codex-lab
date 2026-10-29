# C-3 剧本：建 `forcitis_one_dev` 库与 `one_dev_*` 角色

依据：infra-runbook.md §2、ADR-0025 第 2 条、`one_db_roles_schemas.sql`（草稿）。时点：dev 前，C-1 之后。性质：对外变更，执行前须用户确认。

可执行脚本：`sql/dev-db.sql`（建）→ `sql/dev-db-verify.sql`（校验）；回滚 `sql/dev-db-rollback.sql`。

## 相对草稿的改动（都已写进可执行脚本）

| 改动 | 原因 |
|---|---|
| 建库用 `LOCALE_PROVIDER builtin BUILTIN_LOCALE 'C.UTF-8'`（`LC_COLLATE` / `LC_CTYPE` 为 `C`） | 实例操作系统没有 libc 的 `C.UTF-8`（2026-10-15 查 `pg_collation` 只有内置的 `ucs_basic`、`pg_c_utf8`、`pg_unicode_fast`）；PG 18 内置 provider 的 `C.UTF-8` 按码点排序、Unicode 字符分类，不随 glibc 升级变化，比退回 libc `C` 更贴近原意（one-coordinator 认可） |
| `GRANT CREATE ON DATABASE forcitis_one_dev TO one_dev_owner` | 现有 Flyway 迁移每个模块都有 `CREATE SCHEMA IF NOT EXISTS`；PG 在判断「已存在」之前先检查库级 CREATE 权限，草稿只给了 CONNECT，首次迁移会以「permission denied for database」失败 |
| `GRANT one_dev_owner TO CURRENT_USER WITH INHERIT FALSE, SET TRUE` | PG 16 起 CREATEROLE 账号建出的角色只得 ADMIN 选项；`CREATE SCHEMA … AUTHORIZATION one_dev_owner` 与 `ALTER DEFAULT PRIVILEGES FOR ROLE one_dev_owner` 要求执行人能 SET ROLE |
| `one_dev_app` 加 `work_mem = 16MB`；`one_dev_readonly` 加只读事务、60 s 语句超时、`work_mem = 8MB` | 清单 C-3 与 ADR-0025 第 2 条要求按角色设超时与 work_mem；数值为**建议**，C-1 后按实例规格调整 |
| `one_dev_migrator` 加 `lock_timeout = 10s` | 同实例共用，迁移拿不到锁时尽快失败，不长时间挡住 IT 服务门户 |
| 增加 STEP 0 的库存在、连接预算查询与 V1–V8 校验 | 执行前后可核对 |
| 增加 `platform_idempotency` schema 及其授权、预建 `btree_gist`（2026-10-15 one-ops，R-3a 复核发现） | 幂等键模块的基线迁移在该 schema 建表；不预建时 Flyway 自行建出，运行角色没有 USAGE 与默认权限，带幂等键的写请求报权限错误。`btree_gist` 与 `one_db_roles_schemas.sql` 对齐预建。另新增 `sql/dev-db-after-migrations.sql`（§4b：每次迁移后撤销运行角色对版本表的 DELETE、TRUNCATE）。验证：`sql/c3_dev_db_check.sh`（#742 已删）在本机临时 postgres:18 容器里建库建角色、以迁移角色执行全部基线迁移、再以运行角色检查 8 项（修复前幂等表读写被拒、版本表可删除；修复后 8 项全部通过） |
| 增加 `cost_timesheet` schema 及其授权（2026-10-15 one-ops-1a） | 工时迁移 `V20261014140000` 起在该 schema 建表；不预建时 Flyway 会自行建出（属主 one_dev_owner），但运行角色没有 USAGE 与默认权限，工时接口在运行时报权限错误。迁移基线合并（R-3a，#103）后 `delivery_execution` 已退役，不再有对应迁移与 schema。 |
| `dev-db-after-migrations.sql` 加 `GRANT EXECUTE ON FUNCTION platform_audit.create_audit_entry_partitions(date, date) TO one_dev_app`；verify 加 V9；check.sh（已删）加两项并把幂等表检查改成 uuid 主键与 64 位十六进制摘要（2026-10-17 one-ops，#405 ultrareview） | 基线对 PUBLIC 撤销了该函数的 EXECUTE，缺授权则 `AuditPartitionJob` 每天 permission denied，基线预建的 6 个月分区（至 2027-03）用尽后审计写入全部失败。函数随基线迁移建出，故授权放在迁移之后的幂等脚本 |

## 执行前确认点

1. C-1 已完成（或用户明确先在现规格上建 dev 库）；`SHOW max_connections` 已记录。
2. 连接预算：`one_dev_*` 合计 31（app 25、migrator 2、readonly 3、audit_maintainer 1；app 原 40，2026-10-21 改 25：连接池 10 + 运维命令池最多 10 + 余量 5），加上 it_portal 与以后的 `one_prod_*`，合计须小于 `max_connections` 减保留；不满足时先由用户决定下调哪一方。
3. 排序规则：脚本用 PG 内置 provider 的 `C.UTF-8`（PG 17 起可用），不依赖操作系统 locale；执行前确认 `SELECT collname FROM pg_collation WHERE collprovider = 'b'` 含 `pg_c_utf8`。
4. 执行账号是 FORCITIS one 专用高权限账号 `one_admin`（用户 2026-10-15「新建 FORCITIS one 专用高权限账号 (Recommended)」「先建后轮换 (Recommended)」；只用于建库建角色，不碰 IT 服务门户的 `portal_migrator` 与 `forcitis_one` 库），口令文件 ECS `/etc/forcitis-one/one-admin.pgpass`（root 0600）；执行环境可连 RDS 内网地址（ECS 已装发行版 `postgresql` 15 客户端，2026-10-15）。高权限口令只能经用户提供的安全通道到 ECS（例如用 ECS 上临时 RSA 公钥加密后传入），不经聊天、命令行参数或日志。
5. 1a 修复批已合并（status/blockers/080）——**建库可先做，但首次 Flyway 迁移与应用启动须等修复批合并**。

## 步骤

0. **基线开放标记不再是 dev 部署的前置**（用户 2026-10-15，intent `2026-10-15-124901-one-coordinator-部署前开发编排三项都采纳-加一个.md`）：`backend/architecture/migration-baseline-open` 在 dev 期间保持开放，`deploy_gate` 对 dev 只告警、对 prod 拦截；标记在首次生产部署前由单独 PR 删除（METHODS M3「迁移基线」，predeploy-checklist.md C-15）。开放期内基线文件改过后，dev 库与服务器发布状态按 dev 库重建剧本（[`runbooks/dev-db-rebuild.md`](dev-db-rebuild.md)，one-ops）一起重置，不追加迁移；依据 ADR-0038（已采纳，部分取代 ADR-0033 迁移方案第 5 条等）。建库建角色（下面的步骤）不受影响，已于 2026-10-15 完成。

0.1 **首次部署前确认 `platform_integration.event_publication` 没有未完成记录**：事件负载里的类型化 ID 已改写成标量字符串（R-1b P1），旧的 `{"value": …}` 格式读不回，重投会失败。首次部署前该表应为空；若不空，先让未完成的事件投递完成，或经负责会话批准后清理。

```bash
psql -v ON_ERROR_STOP=1 -h <RDS 内网地址> -U <高权限账号> -d forcitis_one -f sql/dev-db.sql
psql -v ON_ERROR_STOP=1 -h <RDS 内网地址> -U <高权限账号> -d forcitis_one_dev -f sql/dev-db-verify.sql | tee c3-verify-$(date +%F).txt
```

每次发布迁移之后（首次部署起）执行一次，幂等：

```bash
psql -v ON_ERROR_STOP=1 -h <RDS 内网地址> -U <高权限账号> -d forcitis_one_dev -f sql/dev-db-after-migrations.sql
```

改脚本后先在本机验证（需要 docker，临时 postgres:18 容器用完即删，口令运行时生成）；原脚本 `c3_dev_db_check.sh` 已于 #742 删除，原文见 `git show 0072e5e15^:docs/modernization/phase1a/infra/runbooks/sql/c3_dev_db_check.sh`。

STEP 0 / STEP 2 的冲突检查有任何一行返回即停止（`ON_ERROR_STOP` 不会因查询有结果而停，须人工看输出；用 DMS 时逐段执行）。

设口令（在 ECS 上生成，不经本机与聊天）：`openssl rand` 生成 ≥ 32 位随机口令，在 ECS 上算出 SCRAM-SHA-256 校验值后执行 `ALTER ROLE … PASSWORD 'SCRAM-SHA-256$…'`（服务器与日志只见校验值，不见原文；`psql` 的 `\password` 同理但要交互）。`one_dev_app` 写入 `/etc/forcitis-one/one.env` 的 `SPRING_DATASOURCE_PASSWORD`；`one_dev_migrator` 写入 `/etc/forcitis-one/migrate/spring.flyway.user` 与 `spring.flyway.password`（目录 root 700、文件 root 600，只由安装器的迁移步骤经 `LoadCredential` 读取，见 release.md §4）；`one_dev_readonly` 存运维密码管理器。

CA 证书：实例 SSL 已开启（阿里云签发，`ApsaraDB cn-hangzhou region CA`）。从阿里云公开的 ApsaraDB CA 链（`https://apsaradb-public.oss-ap-southeast-1.aliyuncs.com/ApsaraDB-CA-Chain.zip`）只取 `ApsaraDB cn-hangzhou region CA` 与 `ApsaraDB Root CA` 两类证书，写入 `/etc/forcitis-one/rds-ca.pem`（root:forcitis 0640）；`one.env` 用 `sslmode=verify-full&sslrootcert=/etc/forcitis-one/rds-ca.pem`。

> **注意：不得调用会返回密钥的接口。** `rds DescribeDBInstanceSSL` 的返回含实例证书的私钥（`ServerKey`）；查 SSL 状态用控制台，或在进程内只取 `SSLEnabled`、`CAType`、`SSLExpireTime` 等字段，不打印原始返回。2026-10-15 曾因直接打印而使私钥进入会话记录（见下方执行记录）。

## 校验

`dev-db-verify.sql` 各项符合注释中的期望；V8 在首次迁移与 Flowable 安装之后再跑一次：全部表属主 `one_dev_owner`，`act_*` 表全部在 `platform_approval`。

> 风险（须在首次部署时验证）：Flowable 安装运行用 `one_dev_migrator`，其 `search_path` 是 `one_migration`；Flowable 的建表语句不带 schema，`ACT_*` 可能建进 `one_migration`。安装那一次的连接串加 `&currentSchema=platform_approval`，并以 V8 第二个查询核对。

## 执行记录（2026-10-15，one-ops）

| 项 | 结果 |
|---|---|
| `one_admin` | 用户决定「先建后轮换」：口令在运维机进程内存中随机生成，同一进程用阿里云 SDK 调 `CreateAccount`（Super），经云助手 SendFile（f-hz0701colwwbzsw）写 ECS `/etc/forcitis-one/one-admin.pgpass`（root 0600），口令不进命令行参数、环境变量、日志与磁盘。登录验证：`rolcreaterole`、`rolcreatedb` 为真。**已轮换（2026-10-15，t-hz0701tj8quuccg）**：`tools/ops/ecs/scripts/c3_rotate_admin_password.py` 在 ECS 上生成新口令，用现有口令连接执行 `ALTER ROLE one_admin PASSWORD '<SCRAM 校验值>'`（PostgreSQL 允许角色改自己的口令，RDS 高权限账号实测可行），先写 `.new` 再改口令、新口令登录验证通过才替换正式文件；创建时经运维机进程内存的那个口令已作废。以后按同一脚本定期轮换，不依赖 RAM 角色 |
| STEP 0 | 4 项冲突检查全为空；`max_connections` 1620 |
| 建库与授权 | `dev-db.sql` 退出码 0；`dev-db-verify.sql`：4 个角色（此后脚本建 5 个）、10 个 schema 属主 `one_dev_owner`、默认权限 18 项、连接上限合计 45、无外来属主（云助手 t-hz0701ctw0hn3eo） |
| 口令 | `tools/ops/ecs/scripts/c3_set_role_passwords.py dev <RDS 内网地址>`（t-hz0701cxo2kjbpc）：在 ECS 上生成，数据库只收到 SCRAM-SHA-256 校验值；app → `one.env`，migrator → `/etc/forcitis-one/migrate/spring.flyway.{user,password}`，readonly → `/etc/forcitis-one/one-dev-readonly.pgpass`；三个角色真实登录，readonly 确认只读 |
| 增量补齐（2026-10-15，one-coordinator 批准） | dev 库按旧版脚本建成、未执行过迁移，不删库：以 `one_admin` 执行 `btree_gist`、`platform_idempotency` schema、USAGE、三条默认权限与 REVOKE CREATE（云助手 t-hz070246r8f8074）；新版 verify：11 个 schema 属主均为 `one_dev_owner`，默认权限 20 行（含 `platform_idempotency`），运行角色无 CREATE。`dev-db-after-migrations.sql` 待首次迁移后执行；首次部署前不执行任何迁移（W1 仍在改基线） |
| 已按旧脚本建库补建迁入角色（2026-10-22 one-ops 起草；用户经收件箱批准在 dev 执行，intent 2026-10-22-233703；**已于 2026-10-23 执行**，云助手 t-hz070ulib73ylts，回读：NOLOGIN、无 SUPERUSER/CREATEROLE/CREATEDB/BYPASSRLS/REPLICATION，不属于任何角色，成员行仅 one_admin（仅 ADMIN），dev 共 6 个 one_dev_* 角色。V13 待下次含本 PR 的发布后跑） | 以 `one_admin` 执行 `CREATE ROLE one_dev_history_loader NOLOGIN;`（只建角色，表权限到迁入窗口按 ADR-0036 另授），再跑 `dev-db-verify.sql` 的 V13（迁移之后再跑一次）与 V13b；补建之前旧 dev 库上仍可被持 CREATEROLE 者抢建同名角色。若 `CREATE ROLE` 报「已存在」：停下，先核对该角色的属性与成员行（V13/V13b），确认无人抢建再继续，不要直接复用。V13 引用运行类角色，旧库须先完成上一行的 `one_dev_audit_maintainer` 补建 |
| 已按旧脚本建库的补授（2026-10-17 one-ops 起草，随含 #405 的 rc 部署一并执行） | 部署含 #405 的 rc、迁移跑完之后，以 `one_admin` 先补建维护角色：`CREATE ROLE one_dev_audit_maintainer LOGIN NOINHERIT CONNECTION LIMIT 1; GRANT CONNECT ON DATABASE forcitis_one_dev TO one_dev_audit_maintainer; GRANT USAGE ON SCHEMA platform_audit TO one_dev_audit_maintainer;`（口令由运维另设），再执行 `sql/dev-db-after-migrations.sql`（幂等，本就是每次发布迁移后的固定一步；含两条函数 EXECUTE 授权）；再跑 `dev-db-verify.sql` 的 V9，期望 `one_dev_app = t`、`one_dev_readonly = f`，维护角色 `can_drop = t`、`can_create = f` |
| 慢日志不带参数值 | 实例级参数 `log_parameter_max_length` 由 -1 改为 0（2026-10-15，用户「改实例级，参数值不进日志 (Recommended)」；`ModifyParameter`，不重启，RequestId 01A13CFC-BE62-5683-954B-D29895E25DFA）：慢语句日志（实例级 `log_min_duration_statement` 1000 ms）不再附带绑定参数值。库级与角色级设置被拒（`ALTER DATABASE forcitis_one_dev SET log_parameter_max_length = 0` 报 permission denied，rds_superuser 无权设此参数，云助手 t-hz0702cuyk9bj0g），故只能改实例级。影响：同实例的 it_portal 慢日志同样不再带参数值，行为不变。`log_parameter_max_length_on_error` 原已为 0。dev 的 `log_min_duration_statement` 不改，保持实例级 1 s，Phase 2 再定。回退：`ModifyParameter` 改回 -1（不重启）；`DescribeParameters` 读回 0，实例 Running |
| verify-full | 三个角色与 `one_admin` 均以 `sslmode=verify-full` 连通（TLS 1.3，t-hz0701ffbczsglc）；改用 IP 连接时证书校验失败，主机名校验生效 |
| 安全事件 | 查 SSL 状态时直接打印了 `DescribeDBInstanceSSL` 的返回，实例证书私钥进入会话记录。影响：须能截获 VPC 内网流量才可利用。处置：用户决定「和生产前维护一并做」——生产前维护窗口重签证书（必要时重启，与 IT 门户协调）；已加「不得调用会返回密钥的接口」注意事项 |

### 2026-10-15 对已建 dev 库执行的增量（以 one_admin 在 forcitis_one_dev 执行，云助手 t-hz070246r8f8074）

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE SCHEMA IF NOT EXISTS platform_idempotency AUTHORIZATION one_dev_owner;
GRANT USAGE ON SCHEMA platform_idempotency TO one_dev_app, one_dev_readonly;
ALTER DEFAULT PRIVILEGES FOR ROLE one_dev_owner IN SCHEMA platform_idempotency GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO one_dev_app;
ALTER DEFAULT PRIVILEGES FOR ROLE one_dev_owner IN SCHEMA platform_idempotency GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO one_dev_app;
ALTER DEFAULT PRIVILEGES FOR ROLE one_dev_owner IN SCHEMA platform_idempotency GRANT SELECT ON TABLES TO one_dev_readonly;
REVOKE CREATE ON SCHEMA platform_idempotency FROM one_dev_app, one_dev_readonly;
```

须由当初执行 c3 的同一个高权限账号执行（它持有 one_dev_owner 的 SET 成员关系且是库属主）。执行后 `dev-db-verify.sql` 期望：V3 为 11 行；默认权限由 18 行变为 20 行。

## 回滚

`sql/dev-db-rollback.sql`（删库与 6 个角色；只在无须保留的数据时）。不影响 `forcitis_one` 库与 it_portal 角色。
