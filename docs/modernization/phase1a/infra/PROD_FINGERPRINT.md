# 生产特征表（给 QUA-24「拦生产写」用；只有特征，无密钥、口令与连接串）

整理：one-ops，2026-10-23。来源均为仓库内已登记文档与 PREDEPLOY_CHECKLIST、RESOURCES，未新连任何环境。

**先读这条**：名字带 production 的 ECS `i-bp122xlpar8w2uylmal2`（实例名 forcitis-one-production）**现在跑的是 dev**，不是生产。按名字拦会误伤 dev；按 ID 拦才对。新系统的生产 ECS/RDS 尚未购买。

## 一、现存的真生产（旧系统与 A8，一律只读，写即违规）

| 项 | 特征 |
|---|---|
| SQL Server RDS 实例 | `rm-bp1690ep53t9778dp`；端点形如 `rm-bp1690ep53t9778dp.sqlserver.rds.aliyuncs.com`（端口 1433）；同实例两个业务库 |
| 库名 | `a8_db`（A8/致远）、`ruoyi-vue-pro`（老系统）；新系统在 a8_db 里的只读视图在 schema `forcitis_one`（`v_org_unit`、`v_org_post`、`v_org_member`），老系统侧 `dbo.v_psa_one_legacy_todo`、`dbo.v_one_legacy_org_short_name` |
| 账号（均只读，库级 DENY 写） | `one_a8_views_ro`、`one_legacy_names_ro`、`one_evidence_ro`（取证）；`psa_one_legacy_ro` 2026-10-22 已撤 |
| 高权限账号 | `u8c`（RDS 高权限，只用于授权变更；任何读写生产的语句出现 u8c 都应拦截并告警） |
| 老系统应用服务器 | ECS `i-bp1b34hcl0g1cnkdtrb9`（ruoyi-vue-pro，私网 172.16.10.52），只经云助手跑只读探针 |
| A8 应用服务器 | ECS `i-bp1dqbdqgf4vj53qd1q9`（Windows，A8-Win2022-Production，私网 172.16.235.83，VPC `vpc-bp1ovla4k0s9pl70fs5jl`） |
| 云助手入口 | 阿里云 cn-hangzhou，`aliyun ecs RunCommand` 指向上面两个实例 ID（含任何写类命令、重启、`DROP/ALTER/INSERT/UPDATE/DELETE/TRUNCATE/EXEC`） |
| 公网白名单 | 该 RDS 白名单含 `0.0.0.0/0`（风险已登记，用户「保持不动」）；新系统出口白名单组 `forcitis_one` = 172.16.89.86 |

## 二、新系统生产（尚未建，命名已定，到时即为生产特征）

| 项 | 特征 |
|---|---|
| PostgreSQL 库与角色 | 库 `forcitis_one_prod`；角色 `one_prod_owner`、`one_prod_migrator`、`one_prod_app`、`one_prod_readonly`、`one_prod_audit_maintainer`、`one_prod_history_loader`（建库脚本 `runbooks/sql/c9_prod_db.sql`）。**角色名前缀 `one_prod_` 即生产**；dev 为 `one_dev_`、库 `forcitis_one_dev` |
| 部署目标 | GitHub 工作流 `deploy.yml`，输入 `environment=prod`；GitHub environment `prod`；OOS 参数名 `forcitis-one-deploy-request-prod`（及 `-bundle`、`-bundle-sha256`、结果参数同后缀，dev 为 `-dev`），地域 cn-hangzhou；runner 标签 `forcitis-one-deploy` |
| 应用配置 | `SPRING_PROFILES_ACTIVE=prod`；连接 URL 含 `sslmode=verify-full` |
| 生产 RDS/ECS | 尚无实例（规划：RDS 8C32G/200G、独立生产 ECS，条件见 OPS_CONTEXT）；购买后实例 ID 与端点由 one-ops 补入本表 |

## 三、不是生产（别误拦）

- dev 的 PostgreSQL RDS `pgm-bp13vak0sew3m003`（端点形如 `pgm-bp13vak0sew3m003.pg.rds.aliyuncs.com`）、库 `forcitis_one_dev`（及 `one_dev_history_loader` 等 `one_dev_*` 角色）。注意同实例另有空库 `forcitis_one`，不是生产。
- dev ECS `i-bp122xlpar8w2uylmal2`（实例名含 production，见上）。
- 本机 Testcontainers 临时容器、`test-images.txt` 登记镜像。

## 四、已知口径

- 允许的生产读取走专用只读账号并登记（ADR-0037、`LEGACY_READ_REGISTER.md`）；`u8c` 的读取属破例，逐次批准。
- 拦截应以「实例 ID / 实例端点 / 库名 / 账号前缀」匹配，不要依赖实例名称里的 production 字样。
