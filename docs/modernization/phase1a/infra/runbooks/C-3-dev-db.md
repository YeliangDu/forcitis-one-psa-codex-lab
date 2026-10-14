# C-3 剧本：建 `forcitis_one_dev` 库与 `one_dev_*` 角色

依据：INFRA_RUNBOOK §2、ADR-0025 第 2 条、`one_db_roles_schemas.sql`（草稿）。时点：dev 前，C-1 之后。性质：对外变更，执行前须用户确认。

可执行脚本：`sql/c3_dev_db.sql`（建）→ `sql/c3_dev_db_verify.sql`（校验）；回滚 `sql/c3_dev_db_rollback.sql`。

## 相对草稿的改动（都已写进可执行脚本）

| 改动 | 原因 |
|---|---|
| `GRANT CREATE ON DATABASE forcitis_one_dev TO one_dev_owner` | 现有 Flyway 迁移每个模块都有 `CREATE SCHEMA IF NOT EXISTS`；PG 在判断「已存在」之前先检查库级 CREATE 权限，草稿只给了 CONNECT，首次迁移会以「permission denied for database」失败 |
| `GRANT one_dev_owner TO CURRENT_USER WITH INHERIT FALSE, SET TRUE` | PG 16 起 CREATEROLE 账号建出的角色只得 ADMIN 选项；`CREATE SCHEMA … AUTHORIZATION one_dev_owner` 与 `ALTER DEFAULT PRIVILEGES FOR ROLE one_dev_owner` 要求执行人能 SET ROLE |
| `one_dev_app` 加 `work_mem = 16MB`；`one_dev_readonly` 加只读事务、60 s 语句超时、`work_mem = 8MB` | 清单 C-3 与 ADR-0025 第 2 条要求按角色设超时与 work_mem；数值为**建议**，C-1 后按实例规格调整 |
| `one_dev_migrator` 加 `lock_timeout = 10s` | 同实例共用，迁移拿不到锁时尽快失败，不长时间挡住 IT 服务门户 |
| 增加 STEP 0 的库存在、连接预算查询与 V1–V8 校验 | 执行前后可核对 |

## 执行前确认点

1. C-1 已完成（或用户明确先在现规格上建 dev 库）；`SHOW max_connections` 已记录。
2. 连接预算：`one_dev_*` 合计 45（app 40、migrator 2、readonly 3），加上 it_portal 与以后的 `one_prod_*`，合计须小于 `max_connections` 减保留；不满足时先由用户决定下调哪一方。
3. 排序规则：`SELECT collname FROM pg_collation WHERE collname IN ('C.utf8','C.UTF-8','ucs_basic');` 有 `C.UTF-8` 或 `C.utf8`；没有则把 STEP 1 的 `LC_COLLATE` / `LC_CTYPE` 改为实例支持的 `C`，并回报。
4. 执行账号是 RDS 高权限账号；执行环境可连 RDS 内网地址（ECS 上 `dnf -y install postgresql` 取 psql，或用 DMS）。
5. 1a 修复批已合并（status/blockers/080）——**建库可先做，但首次 Flyway 迁移与应用启动须等修复批合并**。

## 步骤

```bash
psql -v ON_ERROR_STOP=1 -h <RDS 内网地址> -U <高权限账号> -d forcitis_one -f sql/c3_dev_db.sql
psql -v ON_ERROR_STOP=1 -h <RDS 内网地址> -U <高权限账号> -d forcitis_one_dev -f sql/c3_dev_db_verify.sql | tee c3-verify-$(date +%F).txt
```

STEP 0 / STEP 2 的冲突检查有任何一行返回即停止（`ON_ERROR_STOP` 不会因查询有结果而停，须人工看输出；用 DMS 时逐段执行）。

设口令：RDS 控制台「账号管理」为 `one_dev_migrator`、`one_dev_app`、`one_dev_readonly` 重置口令（≥ 24 位随机，用密码管理器生成）。`one_dev_app` 写入 `/etc/forcitis-one/one.env` 的 `SPRING_DATASOURCE_PASSWORD`；`one_dev_migrator` 写入 `/etc/forcitis-one/migrate/spring.flyway.user` 与 `spring.flyway.password`（目录 root 700、文件 root 600，只由安装器的迁移步骤经 `LoadCredential` 读取，见 RELEASE.md §4）；`one_dev_readonly` 存运维密码管理器。

另需（RDS 控制台）：开启 SSL 并下载 CA 证书到 `/etc/forcitis-one/rds-ca.pem`（`0644`），否则 `sslmode=verify-full` 连不上。

## 校验

`c3_dev_db_verify.sql` 各项符合注释中的期望；V8 在首次迁移与 Flowable 安装之后再跑一次：全部表属主 `one_dev_owner`，`act_*` 表全部在 `platform_approval`。

> 风险（须在首次部署时验证）：Flowable 安装运行用 `one_dev_migrator`，其 `search_path` 是 `one_migration`；Flowable 的建表语句不带 schema，`ACT_*` 可能建进 `one_migration`。安装那一次的连接串加 `&currentSchema=platform_approval`，并以 V8 第二个查询核对。

## 回滚

`sql/c3_dev_db_rollback.sql`（删库与 4 个角色；只在无须保留的数据时）。不影响 `forcitis_one` 库与 it_portal 角色。
