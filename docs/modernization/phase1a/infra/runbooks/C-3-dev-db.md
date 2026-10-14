# C-3 剧本：建 `forcitis_one_dev` 库与 `one_dev_*` 角色

依据：INFRA_RUNBOOK §2、ADR-0025 第 2 条、`one_db_roles_schemas.sql`（草稿）。时点：dev 前，C-1 之后。性质：对外变更，执行前须用户确认。

可执行脚本：`sql/c3_dev_db.sql`（建）→ `sql/c3_dev_db_verify.sql`（校验）；回滚 `sql/c3_dev_db_rollback.sql`。

## 相对草稿的改动（都已写进可执行脚本）

| 改动 | 原因 |
|---|---|
| 建库用 `LOCALE_PROVIDER builtin BUILTIN_LOCALE 'C.UTF-8'`（`LC_COLLATE` / `LC_CTYPE` 为 `C`） | 实例操作系统没有 libc 的 `C.UTF-8`（2026-10-15 查 `pg_collation` 只有内置的 `ucs_basic`、`pg_c_utf8`、`pg_unicode_fast`）；PG 18 内置 provider 的 `C.UTF-8` 按码点排序、Unicode 字符分类，不随 glibc 升级变化，比退回 libc `C` 更贴近原意（one-coordinator 认可） |
| `GRANT CREATE ON DATABASE forcitis_one_dev TO one_dev_owner` | 现有 Flyway 迁移每个模块都有 `CREATE SCHEMA IF NOT EXISTS`；PG 在判断「已存在」之前先检查库级 CREATE 权限，草稿只给了 CONNECT，首次迁移会以「permission denied for database」失败 |
| `GRANT one_dev_owner TO CURRENT_USER WITH INHERIT FALSE, SET TRUE` | PG 16 起 CREATEROLE 账号建出的角色只得 ADMIN 选项；`CREATE SCHEMA … AUTHORIZATION one_dev_owner` 与 `ALTER DEFAULT PRIVILEGES FOR ROLE one_dev_owner` 要求执行人能 SET ROLE |
| `one_dev_app` 加 `work_mem = 16MB`；`one_dev_readonly` 加只读事务、60 s 语句超时、`work_mem = 8MB` | 清单 C-3 与 ADR-0025 第 2 条要求按角色设超时与 work_mem；数值为**建议**，C-1 后按实例规格调整 |
| `one_dev_migrator` 加 `lock_timeout = 10s` | 同实例共用，迁移拿不到锁时尽快失败，不长时间挡住 IT 服务门户 |
| 增加 STEP 0 的库存在、连接预算查询与 V1–V8 校验 | 执行前后可核对 |
| 增加 `platform_idempotency` schema 及其授权、预建 `btree_gist`（2026-10-15 one-ops，R-3a 复核发现） | 幂等键模块的基线迁移在该 schema 建表；不预建时 Flyway 自行建出，运行角色没有 USAGE 与默认权限，带幂等键的写请求报权限错误。`btree_gist` 与 `one_db_roles_schemas.sql` 对齐预建。另新增 `sql/c3_dev_db_after_migrations.sql`（§4b：每次迁移后撤销运行角色对版本表的 DELETE、TRUNCATE）。验证：`sql/c3_dev_db_check.sh` 在本机临时 postgres:18 容器里建库建角色、以迁移角色执行全部基线迁移、再以运行角色检查 8 项（修复前幂等表读写被拒、版本表可删除；修复后 8 项全部通过） |
| 增加 `cost_timesheet` schema 及其授权（2026-10-15 one-ops-1a） | 工时迁移 `V20261014140000` 起在该 schema 建表；不预建时 Flyway 会自行建出（属主 one_dev_owner），但运行角色没有 USAGE 与默认权限，工时接口在运行时报权限错误。迁移基线合并（R-3a，#103）后 `delivery_execution` 已退役，不再有对应迁移与 schema。 |

## 执行前确认点

1. C-1 已完成（或用户明确先在现规格上建 dev 库）；`SHOW max_connections` 已记录。
2. 连接预算：`one_dev_*` 合计 45（app 40、migrator 2、readonly 3），加上 it_portal 与以后的 `one_prod_*`，合计须小于 `max_connections` 减保留；不满足时先由用户决定下调哪一方。
3. 排序规则：脚本用 PG 内置 provider 的 `C.UTF-8`（PG 17 起可用），不依赖操作系统 locale；执行前确认 `SELECT collname FROM pg_collation WHERE collprovider = 'b'` 含 `pg_c_utf8`。
4. 执行账号是 FORCITIS one 专用高权限账号 `one_admin`（用户 2026-10-15「新建 FORCITIS one 专用高权限账号 (Recommended)」「先建后轮换 (Recommended)」；只用于建库建角色，不碰 IT 服务门户的 `portal_migrator` 与 `forcitis_one` 库），口令文件 ECS `/etc/forcitis-one/one-admin.pgpass`（root 0600）；执行环境可连 RDS 内网地址（ECS 已装发行版 `postgresql` 15 客户端，2026-10-15）。高权限口令只能经用户提供的安全通道到 ECS（例如用 ECS 上临时 RSA 公钥加密后传入），不经聊天、命令行参数或日志。
5. 1a 修复批已合并（status/blockers/080）——**建库可先做，但首次 Flyway 迁移与应用启动须等修复批合并**。

## 步骤

0. **首次部署 dev 之前，单独开一个 PR 删除 `backend/architecture/migration-baseline-open`**（SESSION_PLAYBOOK §12.2：首次部署前删除基线开放标记，此后迁移恢复只追加）。该标记存在时 `deploy_gate` 拒绝部署（W0 #97）。这个 PR 只做这一件事，不夹带其他改动；计划在 W4 执行。建库建角色（下面的步骤）不受影响，已于 2026-10-15 完成。

```bash
psql -v ON_ERROR_STOP=1 -h <RDS 内网地址> -U <高权限账号> -d forcitis_one -f sql/c3_dev_db.sql
psql -v ON_ERROR_STOP=1 -h <RDS 内网地址> -U <高权限账号> -d forcitis_one_dev -f sql/c3_dev_db_verify.sql | tee c3-verify-$(date +%F).txt
```

每次发布迁移之后（首次部署起）执行一次，幂等：

```bash
psql -v ON_ERROR_STOP=1 -h <RDS 内网地址> -U <高权限账号> -d forcitis_one_dev -f sql/c3_dev_db_after_migrations.sql
```

改脚本后先在本机验证（需要 docker，临时 postgres:18 容器用完即删，口令运行时生成）：`sql/c3_dev_db_check.sh`。

STEP 0 / STEP 2 的冲突检查有任何一行返回即停止（`ON_ERROR_STOP` 不会因查询有结果而停，须人工看输出；用 DMS 时逐段执行）。

设口令（在 ECS 上生成，不经本机与聊天）：`openssl rand` 生成 ≥ 32 位随机口令，在 ECS 上算出 SCRAM-SHA-256 校验值后执行 `ALTER ROLE … PASSWORD 'SCRAM-SHA-256$…'`（服务器与日志只见校验值，不见原文；`psql` 的 `\password` 同理但要交互）。`one_dev_app` 写入 `/etc/forcitis-one/one.env` 的 `SPRING_DATASOURCE_PASSWORD`；`one_dev_migrator` 写入 `/etc/forcitis-one/migrate/spring.flyway.user` 与 `spring.flyway.password`（目录 root 700、文件 root 600，只由安装器的迁移步骤经 `LoadCredential` 读取，见 RELEASE.md §4）；`one_dev_readonly` 存运维密码管理器。

CA 证书：实例 SSL 已开启（阿里云签发，`ApsaraDB cn-hangzhou region CA`）。从阿里云公开的 ApsaraDB CA 链（`https://apsaradb-public.oss-ap-southeast-1.aliyuncs.com/ApsaraDB-CA-Chain.zip`）只取 `ApsaraDB cn-hangzhou region CA` 与 `ApsaraDB Root CA` 两类证书，写入 `/etc/forcitis-one/rds-ca.pem`（root:forcitis 0640）；`one.env` 用 `sslmode=verify-full&sslrootcert=/etc/forcitis-one/rds-ca.pem`。

> **注意：不得调用会返回密钥的接口。** `rds DescribeDBInstanceSSL` 的返回含实例证书的私钥（`ServerKey`）；查 SSL 状态用控制台，或在进程内只取 `SSLEnabled`、`CAType`、`SSLExpireTime` 等字段，不打印原始返回。2026-10-15 曾因直接打印而使私钥进入会话记录（见下方执行记录）。

## 校验

`c3_dev_db_verify.sql` 各项符合注释中的期望；V8 在首次迁移与 Flowable 安装之后再跑一次：全部表属主 `one_dev_owner`，`act_*` 表全部在 `platform_approval`。

> 风险（须在首次部署时验证）：Flowable 安装运行用 `one_dev_migrator`，其 `search_path` 是 `one_migration`；Flowable 的建表语句不带 schema，`ACT_*` 可能建进 `one_migration`。安装那一次的连接串加 `&currentSchema=platform_approval`，并以 V8 第二个查询核对。

## 执行记录（2026-10-15，one-ops）

| 项 | 结果 |
|---|---|
| `one_admin` | 用户决定「先建后轮换」：口令在运维机进程内存中随机生成，同一进程用阿里云 SDK 调 `CreateAccount`（Super），经云助手 SendFile（f-hz0701colwwbzsw）写 ECS `/etc/forcitis-one/one-admin.pgpass`（root 0600），口令不进命令行参数、环境变量、日志与磁盘。登录验证：`rolcreaterole`、`rolcreatedb` 为真。**已轮换（2026-10-15，t-hz0701tj8quuccg）**：`scripts/c3_rotate_admin_password.py` 在 ECS 上生成新口令，用现有口令连接执行 `ALTER ROLE one_admin PASSWORD '<SCRAM 校验值>'`（PostgreSQL 允许角色改自己的口令，RDS 高权限账号实测可行），先写 `.new` 再改口令、新口令登录验证通过才替换正式文件；创建时经运维机进程内存的那个口令已作废。以后按同一脚本定期轮换，不依赖 RAM 角色 |
| STEP 0 | 4 项冲突检查全为空；`max_connections` 1620 |
| 建库与授权 | `c3_dev_db.sql` 退出码 0；`c3_dev_db_verify.sql`：4 个角色、10 个 schema 属主 `one_dev_owner`、默认权限 18 项、连接上限合计 45、无外来属主（云助手 t-hz0701ctw0hn3eo） |
| 口令 | `scripts/c3_set_role_passwords.py dev <RDS 内网地址>`（t-hz0701cxo2kjbpc）：在 ECS 上生成，数据库只收到 SCRAM-SHA-256 校验值；app → `one.env`，migrator → `/etc/forcitis-one/migrate/spring.flyway.{user,password}`，readonly → `/etc/forcitis-one/one-dev-readonly.pgpass`；三个角色真实登录，readonly 确认只读 |
| verify-full | 三个角色与 `one_admin` 均以 `sslmode=verify-full` 连通（TLS 1.3，t-hz0701ffbczsglc）；改用 IP 连接时证书校验失败，主机名校验生效 |
| 安全事件 | 查 SSL 状态时直接打印了 `DescribeDBInstanceSSL` 的返回，实例证书私钥进入会话记录。影响：须能截获 VPC 内网流量才可利用。处置：用户决定「和生产前维护一并做」——生产前维护窗口重签证书（必要时重启，与 IT 门户协调）；已加「不得调用会返回密钥的接口」注意事项 |

## 回滚

`sql/c3_dev_db_rollback.sql`（删库与 4 个角色；只在无须保留的数据时）。不影响 `forcitis_one` 库与 it_portal 角色。
