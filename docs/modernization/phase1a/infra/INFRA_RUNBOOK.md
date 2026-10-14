# FORCITIS one · Phase 1a 基础设施运维清单（dev）

日期：2026-10-14 · 会话：psa-1a-platform-bridge-reimagine · 状态：**草稿，未执行**

**清单里的每一项都是对外变更**，包括云助手执行命令、建库建角色、DNS、证书、企业微信和致远的配置。执行前由 psa-coordinator 请用户逐项确认，确认后由运维执行。本文件和仓库都不保存任何凭据。

逐项执行剧本见 `runbooks/`（C 类），交给外部执行人的交接包见 `handoff/`（B 类），用户决定单见 `DECISIONS_A.md`，与代码的核对结果见 `CROSSCHECK.md`。

## 0. 资源（用户已确认）

| 资源 | 标识 | 说明 |
|---|---|---|
| RDS PostgreSQL | pgm-bp13vak0sew3m003 · 杭州 · PG 18.6 高可用 · 4C16G · ESSD 40G · 库 `forcitis_one`（it_portal）；FORCITIS one dev 用新库 `forcitis_one_dev` | 用户：「这个就是你需要的 RDS……目前是测试开发用」。同库已有 IT 服务门户（`it_portal_runtime`） |
| ECS | i-bp122xlpar8w2uylmal2（forcitis-one-production）· 杭州 K 区 · g8i.xlarge 4C16G · Alibaba Cloud Linux 4 · 系统盘 100G + 数据盘 200G（未挂载）· 内网 172.16.89.86 · 公网 47.114.56.157 | 用户：「新开专用 ECS」。不设登录密码和密钥，运维经云助手 / Workbench 操作 |
| 网络 | VPC vpc-bp1ovla4k0s9pl70fs5jl · 安全组 sg-bp1hjbq8dkoaxa2nljzq 入方向只开 80/443 · RDS 白名单分组 `forcitis_one` = 172.16.89.86 | |

## 1. 待用户决定

| # | 问题 | 建议 |
|---|---|---|
| D-1 | 环境划分 | **已定**（用户 2026-10-14：dev「同实例分库，生产前再定」；生产「扩容现有实例共用」）：<ul><li>dev 使用同一实例上的独立库 `forcitis_one_dev`，角色为 `one_dev_*`。</li><li>生产沿用现有 PG 18 高可用实例，另建生产库（`forcitis_one_prod`）与 `one_prod_*` 角色。</li><li>按 ADR-0025 第 2 条设定各库、各角色的连接数预算，并按角色设置 `statement_timeout`、`idle_in_transaction_session_timeout`、`work_mem`。</li><li>实例扩容（建议 8 核 32 GB、ESSD 200 GB、开启存储自动扩容）属于部署前的购买操作，执行前须经用户确认。</li><li>dev 库在上线前迁出还是停用，部署前决定。</li></ul> |
| D-2 | dev 部署位置 | **已定**（同上）：dev 部署在 forcitis-one-production（i-bp122xlpar8w2uylmal2）；切生产时换配置与库 |
| D-3（部署前再问） | `one.forcitis.com` 的 DNS 与证书由谁办理 | 证书建议用阿里云 SSL 证书服务（或 ACME），DNS 由域名管理员添加 A 记录指向 47.114.56.157 |
| D-4（部署前再问） | A8 库（SQL Server）与老系统库（SQL Server RDS）的只读视图和账号由谁建 | 见 §4。需要 A8 管理员和老 RDS 的 DBA 分别执行，并把 172.16.89.86 加入两库白名单 |
| D-5（部署前再问） | 应急管理员开通方式 | 见 §6，建议使用一次性运维命令，不提供 HTTP 接口 |

## 2. PostgreSQL（`forcitis_one_dev`；生产库 `forcitis_one_prod` 同实例另建，见 D-1）

脚本见 `one_db_roles_schemas.sql`（评审草稿）；**可执行版**为 `runbooks/sql/c3_dev_db.sql`（dev）与 `runbooks/sql/c9_prod_db.sql`（生产，由 dev 版生成），剧本见 `runbooks/C-3-dev-db.md`。可执行版另加了库级 CREATE 授权（迁移含 `CREATE SCHEMA IF NOT EXISTS`）、PG 16 起执行人对属主角色的 SET 授权、按角色的 `work_mem` 与只读角色超时。执行顺序：
1. 用 RDS 高权限账号执行 §0 冲突检查。任何一项查到结果就停止，并把结果报给 psa-coordinator。
2. 执行 §1 至 §3：
   - 建角色：`one_dev_owner`（属主，不可登录）、`one_dev_migrator`（迁移）、`one_dev_app`（运行）、`one_dev_readonly`（排障，可选）；
   - 建 schema，共 8 个：`one_migration`（Flyway 历史表）和 7 个模块 schema；
   - 授权：运行角色只有 DML，审计表只有 INSERT 和 SELECT；只授权本系统自己的 schema，不改数据库级授权和 `public` 的现有授权。
3. 在 RDS 控制台为 `one_dev_migrator`、`one_dev_app`、`one_dev_readonly` 设置随机口令（≥24 位）。口令只写入服务器受保护文件（§5）或 KMS，不经过聊天、不进仓库。
4. 首次部署（§7）：用 `one_dev_migrator` 运行 Flyway；再以 `FLOWABLE_SCHEMA_UPDATE=true` 启动一次，建 `ACT_*`；之后改用 `one_dev_app` 常驻运行。

与 it_portal 隔离：所有 schema 和角色都以本系统模块名或 `one_` 为前缀；不触碰 it_portal 的 schema；连接数上限分别设为 one_dev_app 40、one_dev_migrator 2、one_dev_readonly 3。

应用侧的 Flyway 与 Flowable 配置（已在 `application.properties`，用 `SchemaPlacementIT` 断言）：
- `spring.flyway.default-schema=one_migration`：历史表 `flyway_schema_history` 只在 `one_migration`，不落在任何模块 schema。
- `spring.flyway.schemas` 列出 `one_migration` 与全部模块 schema，`spring.flyway.create-schemas=false`：schema 由 DBA 预建，应用不替 DBA 建。测试库是空库，测试配置里为 `true`。
- `flowable.database-schema=platform_approval`：引擎表 `ACT_*` 一律落在 `platform_approval`，业务模块不连接它们；安装 / 升级时的连接串也加 `currentSchema=platform_approval`，建完用 `SELECT table_schema FROM information_schema.tables WHERE table_name LIKE 'act\_%' GROUP BY 1` 核对只有这一个 schema。
- **首次运行 Flyway 需要库级 CREATE 权限**：每个模块的第一个迁移都以 `CREATE SCHEMA IF NOT EXISTS` 开头，PostgreSQL 先检查库级 CREATE 权限再判断 schema 是否已存在，所以迁移角色（继承 `one_dev_owner`）要有 `GRANT CREATE ON DATABASE forcitis_one_dev TO one_dev_owner`（`one_db_roles_schemas.sql` §0 已列出）。应用运行角色不需要。
- 部署时迁移连接用 `one_dev_migrator`，运行连接用 `one_dev_app`，分开配置 `spring.flyway.user` / `password` 与 `spring.datasource.*`。
- 每次发布迁移之后执行 `one_db_roles_schemas.sql` 的 §4b：对运行角色撤销版本表的 DELETE 和 TRUNCATE（版本表只追加）。

## 3. ECS 初始化（i-bp122xlpar8w2uylmal2）

| # | 项 | 做法 | 验证 |
|---|---|---|---|
| E-1 | 数据盘 | 200G ESSD 格式化为 xfs，挂载到 `/data`，fstab 用 UUID 并加 `nofail` | `df -h /data`；重启后仍在 |
| E-2 | 系统更新与时区 | `dnf update`；时区 Asia/Shanghai；启用 chronyd | `timedatectl` |
| E-3 | 运行用户 | `useradd --system --home /data/forcitis-one --shell /sbin/nologin forcitis` | `id forcitis` |
| E-4 | Java 运行时 | Eclipse Temurin 25 JRE（RPM 包，固定小版本），或 Alibaba Dragonwell 25；要与 CI 的 Java 25 一致 | `java -version` |
| E-5 | 目录 | `/data/forcitis-one/{releases,current→releases/<ver>,logs,tmp}`，属主 forcitis，权限 750；`/etc/forcitis-one/` 属主 root:forcitis，权限 750 | |
| E-6 | 服务 | systemd 单元 `forcitis-one.service`：User=forcitis；`EnvironmentFile=/etc/forcitis-one/one.env`；`ExecStart=/usr/bin/java -XX:MaxRAMPercentage=60 -jar /data/forcitis-one/current/forcitis-one.jar`；`Restart=on-failure`；加固项 `NoNewPrivileges=yes`、`ProtectSystem=strict`、`ReadWritePaths=/data/forcitis-one/logs /data/forcitis-one/tmp`、`PrivateTmp=yes` | `systemctl status` |
| E-7 | 应用端口 | 应用只监听 `127.0.0.1:8080`；管理端点另设 `127.0.0.1:8081`，只开放 health 和 prometheus。代码默认未设监听地址与管理端口、只暴露 health，须在 one.env 设 `SERVER_ADDRESS`、`MANAGEMENT_SERVER_ADDRESS`、`MANAGEMENT_SERVER_PORT`、`MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,prometheus`（`runbooks/files/one.env.template`） | 外网扫描只看到 80/443 |
| E-8 | 反向代理 | nginx（发行版包）：80 端口 301 跳转到 443；443 用 TLS 1.2/1.3；`proxy_pass http://127.0.0.1:8080`；**覆盖** `X-Forwarded-For` 和 `X-Forwarded-Proto`（应用的 `FORCITIS_TRUSTED_PROXIES` 只信任 127.0.0.1）；开启 HSTS；`client_max_body_size` 20m；不转发 `/actuator`、`/v3/api-docs`；**访问日志不记查询串**（`/login/wecom?code=&state=`、`/login/sso?ticket=` 的一次性凭证，D-1a-39），并加 `Referrer-Policy: no-referrer`（`runbooks/files/nginx-one.conf`） | SSL Labs A；`curl -I` |
| E-9 | 证书 | `one.forcitis.com` 证书放在 `/etc/nginx/tls/`，权限 600；如用 ACME，配置自动续期 | 到期日监控 |
| E-10 | 前端静态资源 | PC 端 `dist` 放到 `/data/forcitis-one/current/web`，由 nginx 直接提供；H5 同理放到 `/h5`（构建期 `H5_PUBLIC_PATH` 默认 `/h5/`，用户决定 N-1，构建检查断言资源路径带子路径） | |
| E-11 | 日志接 SLS | 安装 Logtail；采集 `/data/forcitis-one/logs/*.json`（应用输出结构化 JSON）和 nginx access / error 日志；Project 与门户分开或按 Logstore 区分 | SLS 能检索到日志 |
| E-12 | 指标接 ARMS | 方案 A：ARMS Prometheus 抓取 `127.0.0.1:8081/actuator/prometheus`。方案 B：ARMS Java 探针，需先核实对 Java 25 的支持，不支持就用方案 A | ARMS 看板有数据 |
| E-13 | 四类告警 | 在 ARMS / SLS 中配置以下四类，并在 dev 上用故障注入演练（出口条件）：<br>• 组织同步延迟：`organization.workforce.sync.*` 指标，或缩水保护中止<br>• 未完成事件发布：event_publication 中超过 10 分钟仍未完成，或出站投递进入死信<br>• 作业失败：`forcitis_platform_scheduling_job_failures_total{job}` 与 `forcitis_platform_approval_dead_letter_jobs`，规则见下方「E-13 补充」<br>• 登录失败率：5 分钟内失败率超过 20 % 且失败数 ≥ 20 | 演练记录 |
| E-14 | 出站网络 | 安全组出方向放行：RDS 5432（同 VPC）、`qyapi.weixin.qq.com:443`、致远 OA 验票地址、A8 SQL Server、老系统 SQL Server（经白名单）；其余出站按需收紧 | |
| E-15 | 企业微信 | 在企业微信后台为新应用配置：可信域名 `one.forcitis.com`、扫码登录回调域、企业可信 IP 47.114.56.157；工作台**新增**「FORCITIS one」入口，**不替换**老 H5（切换手册 §3.11） | 登录冒烟 |
| E-16 | 致远 | 由 A8 管理员登记新系统的验票回跳地址，**不替换**老 SSO | 登录冒烟 |
| E-17 | 备份 | RDS 自动备份保留 ≥7 天；ECS 数据盘设快照策略；日志在 SLS 中保留 30 天 | |

**E-13 补充：作业失败告警（部署前清单 C-7）**

Prometheus 里的名字是 Micrometer 名字把点换成下划线、计数器加 `_total`。

| 指标 | 含义 | 规则 |
|---|---|---|
| `forcitis_platform_scheduling_job_failures_total{job}` | 带 `@SchedulerLock` 的定时作业抛出异常的次数，`job` 为锁名；启动时每个作业从 0 起，第一次失败就能看到增量。另一节点持锁而被跳过的运行不抛异常，不计数 | `increase(…[10m]) > 0` 按 `job` 分组告警 |
| `forcitis_platform_approval_dead_letter_jobs` | 流程引擎用尽重试、进入死信表的作业数，需要运维处理 | `> 0` 告警 |

**读不到时是 NaN，不是 0。** 死信指标查询引擎失败时返回 NaN（日志里有一条限频的警告），NaN 与任何数比较都为假，`> 0` 永远不会触发，所以必须再配一条缺数据规则：`forcitis_platform_approval_dead_letter_jobs != forcitis_platform_approval_dead_letter_jobs`（NaN 的特征），或 `absent(forcitis_platform_approval_dead_letter_jobs)`，持续 5 分钟告警。出站投递死信、事件发布积压的指标（`forcitis_platform_integration_deliveries_dead_letters`、`…_pending_overdue`）同理加这两条。

**模块内部吞掉的失败，由各模块自己的指标覆盖。** 下面这些作业自己捕获了异常（只记日志、不抛出），上面的作业失败计数看不到，必须单独告警：

| 作业 | 指标 | 规则 |
|---|---|---|
| `SyncOrgSnapshot`（组织同步，失败写成 FAILED 记录，不抛出） | `forcitis_organization_workforce_sync_runs_total{status="FAILED"}` 与 `{status="HELD"}`；另有 `forcitis_organization_workforce_sync_lag_seconds`、`forcitis_organization_workforce_sync_held_runs` | `increase(…{status="FAILED"}[30m]) > 0`；`held_runs > 0` |
| `EnforceDeadlines`（审批时限，单个任务失败只记日志） | `forcitis_platform_approval_deadline_failed_total`（第一次失败时才出现，用 `increase`，不要用 `absent` 判断它） | `increase(…[10m]) > 0` |
| `RefreshShortNames`（部门简称刷新，老系统不可用时跳过） | `forcitis_organization_workforce_short_name_refresh_failures_total`（启动时为 0） | `increase(…[1d]) > 0` 仅提示，不升级为 P1：简称保持上一次的值 |

## 4. 外部只读账号（SQL Server，由对方 DBA 执行）

| 库 | 对象 | 账号 | 授权 | 脚本 |
|---|---|---|---|---|
| A8（a8_db） | 组织视图（只含白名单列） | 只读，专用 | 只授予视图 SELECT，基表 DENY，无 EXEC | `phase1a/SEEYON_VIEWS.sql` |
| 老系统（ruoyi-vue-pro） | 老待办视图 `dbo.v_psa_one_legacy_todo` | 只读，专用（`psa_one_legacy_ro`） | 只授予视图 SELECT，**令牌表不授权**（I-1）；应用启动时校验能 SELECT 的对象恰好只有这一个 | `phase1a/LEGACY_INBOX_VIEW.sql` |
| 老系统 | 部门简称视图 `dbo.v_one_legacy_org_short_name` | 只读，专用（`one_legacy_names_ro`）；**必须另设**，不能与待办共用账号——两个模块各自校验「恰好只能读一个对象」 | 同上 | `phase1a/LEGACY_ORG_NAMES_VIEW.sql` |

DBA 可直接执行的脚本（含口令随机生成、校验与回滚）：`handoff/B-5-dba/`。

两库都要把 172.16.89.86 加入白名单。账号口令只写入 `/etc/forcitis-one/one.env` 或 KMS。

## 5. dev 环境连接配置的取法

- **唯一来源**：服务器上的 `/etc/forcitis-one/one.env`，权限 `0640 root:forcitis`，由 systemd 的 `EnvironmentFile` 加载。仓库、镜像、聊天、工单中都不出现这些值。
- **后续计划**：迁到阿里云 KMS 凭据管家，启动时拉取，one.env 只保留 KMS 引用。上线前完成。
- **PG 连接**：`SPRING_DATASOURCE_URL=jdbc:postgresql://<RDS 内网地址>:5432/forcitis_one_dev?sslmode=verify-full&sslrootcert=/etc/forcitis-one/rds-ca.pem`，`SPRING_DATASOURCE_USERNAME=one_dev_app`，口令单独一行。迁移连接用 `SPRING_FLYWAY_USER` / `SPRING_FLYWAY_PASSWORD`（one_dev_migrator），只在部署步骤注入，常驻进程不持有迁移凭据。
- **应用变量清单**（以代码为准，重构后键名改为按角色命名）：

| 变量 | 用途 | 必需 |
|---|---|---|
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | PG 运行连接 | 是 |
| `SPRING_FLYWAY_URL` / `SPRING_FLYWAY_USER` / `_PASSWORD` | 迁移账号的连接，只给 `migrate` 步骤（缺省 URL 取 `SPRING_DATASOURCE_URL`） | 部署时 |
| `SPRING_FLYWAY_ENABLED` | 常驻服务必须为 `false`（生产 profile 已默认） | 是 |
| `ARCHIVE_STORE_BUCKET` / `_REGION` / `_PREFIX` | 归档库（OSS）的 bucket、地域（只允许国内地域，如 `cn-hangzhou`）和对象前缀（§5.3）；缺任一项启动失败 | 是 |
| `ARCHIVE_STORE_LOCAL_DIRECTORY` | 本机只留最近一份归档的目录（绝对路径） | 是 |
| `ARCHIVE_STORE_WORM_LOCK_DAYS` | bucket 已锁定的 WORM 保留天数，随发行物默认 2557（约 7 年；探针核对桶上已锁定的天数等于它，延长保留期时与 `total-keep-years` 一起改） | 否 |
| `ARCHIVE_STORE_ECS_ROLE_NAME` | 绑定给 ECS 的 RAM 角色名（推荐，应用经 STS 临时凭证访问归档库，§5.3）；与 AccessKey 二选一，都不设时读 `OSS_ACCESS_KEY_ID` / `OSS_ACCESS_KEY_SECRET` | 否 |
| `ARCHIVE_STORE_ACCESS_KEY_ID` / `_SECRET` | 归档用 RAM 子账号的密钥，只写入 one.env 或 KMS；绑定 ECS RAM 角色时两项都不设，应用走默认凭证链 | 否 |
| `FLOWABLE_SCHEMA_UPDATE` | 仅引擎安装 / 升级时为 true | 否 |
| `FLOWABLE_ASYNC_EXECUTOR` | 流程引擎异步执行器开关（默认 true） | 否 |
| `APPROVAL_PROCESS_ADMINISTRATOR_USER_ID` | 取不到审批人时的兜底人 | 是 |
| `APPROVAL_TASK_URL_TEMPLATE` | 卡片跳转地址 | 否（有默认值） |
| `EMERGENCY_TOTP_ENCRYPTION_KEY` | 应急管理员 TOTP 密钥加密（32 字节 Base64） | 是 |
| `FORCITIS_PLATFORM_IDENTITY_SSO_ENABLED` | 致远单点登录开关（`true` / `false`）；**没有默认值，缺失则应用启动失败**（部署前清单 C-10） | 是 |
| 归档用 RAM 子账号的 AccessKey（只在不用 ECS RAM 角色时存在） | one.env / KMS | 不设到期 | 每 90 天：在 RAM 控制台新建第二把、写入 one.env、重启应用确认归档探针通过，再停用并删除旧的；RAM 角色（STS）无需轮换 |
| `FORCITIS_TICKET_KEY_<KID>` | 签名票据签发方密钥；签发方默认关闭，当前白名单为空 | 否 |
| `FORCITIS_TRUSTED_PROXIES` | 只信任 nginx（127.0.0.1） | 是 |
| `SEEYON_OA_BASE_URL`、`LEGACY_LOGIN_URL` | 致远验票地址；失败时回老系统登录 | 是 |
| `FORCITIS_PLATFORM_IDENTITY_SSO_ENABLED` | 致远单点登录开关；配置文件中写死为 false，B-3 完成后在 one.env 设 true | 启用时 |
| `APPROVAL_DEADLINE_ENABLED` | 审批时限提醒与升级作业开关（默认 true） | 否 |
| `EVENT_PUBLICATION_JOBS_ENABLED` | 事件发布重投与清理作业开关（默认 true，见 §9） | 否 |
| `FORCITIS_PLATFORM_INTEGRATION_WECOM_DELIVERY_DUPLICATE_TASK_ERROR_CODES` | 企业微信「task_id 已用过」错误码（B-9 核实后设置） | 启用时 |
| `SERVER_ADDRESS`、`SERVER_PORT`、`MANAGEMENT_SERVER_ADDRESS`、`MANAGEMENT_SERVER_PORT`、`MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE` | E-7 的监听地址与管理端点（代码未设默认） | 是 |
| `WECOM_ENABLED` | 企业微信网关与出站投递总开关（默认 false） | 否 |
| `WECOM_BASE_URL` | 企业微信 API 地址（默认 `https://qyapi.weixin.qq.com`） | 否 |
| `WECOM_CORP_ID`、`WECOM_AGENT_ID` | 新企业微信应用的**新值**（老应用的值已在老仓历史中泄露，I-8） | 启用时 |
| `WECOM_APP_SECRET` | 同上，应用 secret，只写入 one.env 或 KMS | 启用时 |
| `WECOM_RESTRICT_RECIPIENTS` | 是否只向白名单收件人发送（默认 true）；**生产必须为 false** | 是 |
| `WECOM_RECIPIENT_ALLOWLIST` | 收件人白名单（`WECOM_RESTRICT_RECIPIENTS=true` 时生效，dev 联调用） | 否 |
| `INTEGRATION_SEEYON_VIEWS_ENABLED` | 致远 A8 组织视图读取开关（默认 false） | 否 |
| `A8_DB_URL` / `A8_DB_USERNAME` / `A8_DB_PASSWORD` | A8 视图只读连接（第 4 节的专用账号） | 启用时 |
| `INTEGRATION_SEEYON_VALIDATE_ON_STARTUP` | 启动时校验 A8 账号只能读视图（默认 true） | 否 |
| `FORCITIS_PLATFORM_INTEGRATION_SEEYON_VIEWS_HEAD_RELATIONSHIP_TYPE_ID`、`..._REGIONAL_RECEIVABLES_MANAGER_RELATIONSHIP_TYPE_ID`、`..._DELIVERY_UNIT_YES_ENUM_ID`、`..._FEE_EARNER_YES_ENUM_ID`、`..._ROOT_PATH` | A8 中关系类型、枚举的编号与同步根单位的层级路径（Spring 宽松绑定，对应 `forcitis.platform.integration.seeyon.views.*`）；**没有默认值**：启用视图读取而缺任何一项，应用启动失败，值由 A8 管理员提供 | 启用时 |
| `FORCITIS_PLATFORM_APPROVAL_LEGACY_URL` / `_USERNAME` / `_PASSWORD` | 合并待办读老待办视图的只读连接（`psa_one_legacy_ro`）；URL 为空则只显示新待办 | 启用时 |
| `FORCITIS_PLATFORM_APPROVAL_LEGACY_DETAIL_URL_TEMPLATE` | 老待办详情页地址，含 `{processInstanceId}`、`{taskId}` | 启用时 |
| `INTEGRATION_LEGACY_ENABLED` | 老系统**部门简称**视图读取开关（默认 false） | 否 |
| `LEGACY_DB_RO_URL` / `LEGACY_DB_RO_USERNAME` / `LEGACY_DB_RO_PASSWORD` | 部门简称视图的只读连接（`one_legacy_names_ro`） | 启用时 |
| `INTEGRATION_LEGACY_VALIDATE_ON_STARTUP` | 启动时校验该账号只能读部门简称视图（默认 true） | 否 |
| `WORKFORCE_SYNC_ENABLED` | 从 A8 视图同步组织与人员的定时任务开关（默认 false） | 否 |
| `WORKFORCE_SHORT_NAME_ENABLED` | 从老库同步组织短名的定时任务开关（默认 false） | 否 |
| `SPRING_PROFILES_ACTIVE` | `dev` 或 `prod`；决定数据库连接的 TLS 要求（§5.1） | 是 |
| 生产附加 | `springdoc.api-docs.enabled=false` | 生产 |
| 前端构建时（CI 变量，非机密） | `VITE_WECOM_CORP_ID`、`VITE_WECOM_AGENT_ID`、`VITE_SSO_PORTAL_URL`（PC）；`TARO_APP_WECOM_CORP_ID`（H5） | 构建时 |

模板：`runbooks/files/one.env.template`（只有变量名与非机密默认值）。

### 5.1 数据库连接的 TLS 与 CA（用户决定 N-3）

- **应用侧的硬规则**（`DatabaseTransportConfiguration`，在任何连接建立之前检查）：
  - 激活 `prod` 时，主库、迁移连接（`spring.flyway.url`）、A8 视图、老系统短名视图、老待办视图的 JDBC URL 都必须验证服务端证书和主机名，且 CA 文件可读；否则**启动失败**，日志列出是哪条连接、缺什么。
  - 激活 `dev` 时至少加密：PostgreSQL `sslmode` 为 `require`、`verify-ca` 或 `verify-full`；SQL Server `encrypt=true`。
  - 没有激活这两个 profile（本地、测试）不检查。
  - 未启用的 A8 / 老系统视图连接不检查。
- **PostgreSQL（RDS）**：URL 带 `sslmode=verify-full&sslrootcert=/etc/forcitis-one/rds-ca.pem`。`prod` 不接受 `require`。
- **SQL Server（A8、老系统）**：驱动没有 PEM 形式的 CA 路径，URL 用 `encrypt=true;trustServerCertificate=false;trustStore=/etc/forcitis-one/sqlserver-ca.p12;trustStorePassword=<取自 one.env>`。`prod` 不接受 `trustServerCertificate=true`、`encrypt=false` 或缺 `trustStore`。
- **CA 的放置与下发**：
  1. 在 RDS 控制台开启 SSL 并下载 CA 证书链（PEM），在另一条渠道核对指纹。SQL Server 两边向对方 DBA 要各自的 CA 证书，不信任 `trustServerCertificate`。
  2. 运维经云助手把 PEM 放到 `/etc/forcitis-one/rds-ca.pem`，属主 `root:forcitis`，权限 `0640`；SQL Server 的 CA 用 `keytool -importcert` 导入 PKCS12 信任库 `/etc/forcitis-one/sqlserver-ca.p12`，权限同上。CA 是公开证书，不是密钥，但文件被替换等于信任被替换，所以只由 root 写。
  3. 同一份文件被运行连接和迁移连接共用；文件不进仓库、镜像和聊天，下发动作记入运维记录。
  4. 证书到期前 30 天更新文件并重启应用；到期日列入证书监控（与 `one.forcitis.com` 的证书一起）。RDS 控制台更换服务端证书后必须同步更新 CA 文件，否则应用重启后连不上，而不是静默降级。
- **验证**：`psql "host=<RDS 内网地址> dbname=forcitis_one_dev user=one_dev_app sslmode=verify-full sslrootcert=/etc/forcitis-one/rds-ca.pem" -c "select ssl, version from pg_stat_ssl where pid = pg_backend_pid()"`，应返回 `t`。

### 5.2 密钥清单与轮换（用户决定 N-2：服务账号口令不设到期，每 90 天轮换）

| 密钥 | 存放 | 到期 | 轮换 |
|---|---|---|---|
| `one_dev_app`、`one_dev_migrator`、`one_dev_readonly` 的口令 | one.env / KMS（迁移口令只在部署步骤注入） | 不设到期（PostgreSQL 角色不加 `VALID UNTIL`） | 每 90 天：RDS 控制台改口令 → 写入 one.env → 重启；迁移口令随部署轮换 |
| A8 只读账号、老系统两个只读账号的口令 | one.env / KMS | 不设到期（SQL Server 登录 `CHECK_EXPIRATION = OFF`） | 每 90 天：对方 DBA 改口令 → 写入 one.env → 重启 |
| `WECOM_APP_SECRET` | one.env / KMS | 以企业微信后台为准 | 每 90 天在企业微信后台重置并更新 |
| `EMERGENCY_TOTP_ENCRYPTION_KEY` | one.env / KMS | 不到期 | 不随 90 天轮换：轮换要重加密全部应急管理员的 TOTP 密钥，单独写手册后才能做 |
| `FORCITIS_TICKET_KEY_<KID>` | one.env / KMS | 签发方默认关闭 | 启用某个签发方时，按该签发方的约定轮换（`kid` 并存期内可平滑换） |

- 轮换到点由运维日历提醒；任何一项逾期 7 天按 P2 报告 psa-coordinator。
- 口令只在服务器受保护文件或 KMS 中出现，轮换过程不经聊天、工单和仓库。

### 5.3 数据保留作业与归档去向（数据打分卡 T3；用户决定 2026-10-15：用国内 OSS 归档存储）

| 数据 | 在线保留 | 到期后 | 作业（ShedLock 名） | 配置项（缺失则启动失败） |
|---|---|---|---|---|
| `cost_timesheet.consumed_events` | 90 天 | 删除 | `cost.timesheet.consumed.events.purge`，每日 04:20 | `forcitis.cost.timesheet.consumed-events.keep-period`、`.cron` |
| `organization_workforce.sync_runs` / `sync_diffs` | 运行 2 年、差异 1 年（已放行的从放行日算；未放行的中止运行不删） | 删除 | `organization.workforce.sync.purge`，每日 03:50 | `forcitis.organization.workforce.sync-purge.*` |
| `platform_integration.outbound_deliveries` | 成功或跳过 90 天、失败 1 年 | 删除 | `platform.integration.outbound.purge`，每日 04:10 | `forcitis.platform.integration.outbound-purge.*` |
| 引擎历史 `ACT_HI_*`（已结束的流程实例） | 3 年 | 先写入归档库，库确认后才从引擎删除 | `platform.approval.process.archive`，每日 03:40 | `forcitis.platform.approval.process-archive.*` |
| `platform_audit.audit_entries` | 3 年 | 先写入归档库；**在线行暂不删除**（表有触发器拒绝 DELETE，删除要单独决定） | `platform.audit.entry.archive`，每日 02:00 | `forcitis.platform.audit.archive.*` |

- **归档库**：阿里云 OSS，**与 ECS 同地域的国内地域**（应用只接受国内地域，拒绝 `eu-central-1` 等；归档含个人信息，不得放到法兰克福），存储类型 Archive，开启 WORM 合规保留（锁定天数等于 `ARCHIVE_STORE_WORM_LOCK_DAYS`）。bucket 和 RAM 子账号由用户创建，名字、账号、密钥都不进仓库。RAM 子账号只授予该 bucket 前缀下的写入（`oss:PutObject`、分片上传的发起与中止）和读元数据，不授予删除。
- **写入方式**：每批一个不可变对象，键为 `<前缀>/<类别>/<年>/<类别>-<UTC 时间>-<SHA-256 前 12 位>.ndjson.gz`；写入前算 SHA-256，上传带 `Content-MD5`、禁止覆盖、归档存储类型，SHA-256 同时写进对象元数据；上传后读回元数据，哈希或大小不符则整批失败，引擎和在线行都不动。多节点下由 ShedLock 保证同一时刻只有一个节点归档，对象名也各不相同。
- **本机**：数据盘 `ARCHIVE_STORE_LOCAL_DIRECTORY/<类别>/latest.ndjson.gz` 只留最近一份（目录属主 `forcitis`，权限 `0750`，systemd 单元的 `ReadWritePaths` 要加上它）。
- **保留与到期（单一 bucket）**：流程归档与审计归档共用同一个 bucket，用前缀区分类别，WORM 合规保留 **2557 天（约 7 年）**：依据是在线 3 年加归档 7 年，从数据产生起算满 10 年（用户已定）；以后需要时还可以延长，只增不减。天数是配置项 `ARCHIVE_STORE_WORM_LOCK_DAYS`（`forcitis.platform.integration.archive-store.worm-lock-days`，随发行物默认 2557），总保留期 10 年是另一个配置项 `total-keep-years`；每类数据的「在线保留期 + WORM 天数」必须等于总保留期，不一致则启动失败（`ArchiveVault.requireTotalKeepPeriod`，并有守护测试 `ArchiveKeepPeriodTest` 核对随发行物的配置）。建桶时在控制台按 2557 天锁定。
- **生命周期**：bucket 配置生命周期规则，对象保留期满（创建后满 WORM 天数）后过期删除；应用不删除归档。过账类单据按 30 年保存，存放另行安排，见 `phase1a/DEFERRED.md`（用户已定，主分支提交 7d5b9d3），不在这个 bucket 的 10 年规则里。
- **凭证与 RAM 策略**：推荐给 ECS 绑定 RAM 角色（STS 临时凭证），应用走默认凭证链，不配置 AccessKey；不用角色时才配置 `ARCHIVE_STORE_ACCESS_KEY_ID` / `_SECRET`（见 §5.2 的轮换）。策略（草稿，由用户在 RAM 控制台建）：Allow `oss:PutObject`、`oss:InitiateMultipartUpload`、`oss:AbortMultipartUpload`、`oss:GetObjectMetadata`（`oss:GetObject` 仅排障需要，默认不给）、`oss:GetBucketInfo`、`oss:GetBucketWorm`、`oss:GetBucketLocation`，资源限定在该 bucket 及 `<前缀>/*`；**显式 Deny** `oss:DeleteObject*`（含 `DeleteObject`、`DeleteMultipleObjects`、`DeleteObjectVersion`）于同一资源，显式 Deny 优先于任何 Allow。
- **探针**：应用启动时和每天一次，检查本机目录可写、bucket 在配置的地域、WORM 已锁定且天数等于配置值、有写入权限（发起并中止一次分片上传，不在 WORM 桶里留对象）。失败不阻止启动，记 `ALERT` 日志并计数 `forcitis.platform.approval.archive.probe.failures`、`forcitis.platform.audit.archive.probe.failures`；归档作业本身失败记 `ALERT` 并计数 `forcitis.platform.scheduling.job.failures{job=…}`（所有带锁作业共用）。生产缺少上述配置项则**启动失败**。
- **归档内容**：流程实例的 ID、流程定义键、版本与部署 ID、业务键、起止时间、发起人、删除原因，任务（办理人、认领、办结、到期时间）、活动、身份关联（候选人、转交、升级追加的人），以及白名单内的变量（`architecture/archive-variables.txt`：`contentDigest`、`routed_*`，任务级 `systemComplete`、`advisory`、`assignedAt`），不含其他流程变量。任务级的结论与理由由审批的只追加办理记录表负责。
- 一个实例写入归档后引擎删除失败，则跳过、记 `ALERT`、计数 `forcitis.platform.approval.archive.skipped`，并记入只追加表 `platform_approval.process_archive_skips`，之后不再重复归档；仍在引擎里的每次运行结束时再告警一次，直到人工处理。
- 审计 3 年归档按 id 前缀成批，进度记在 `platform_audit.audit_archive_batches`。

## 6. 应急管理员与首批持有人开通（运维命令，已实现；执行需用户确认）

- 不提供 HTTP 开通接口（设计如此）。入口是同一个 jar 以档案 `identity-operations` 启动，**一次只做一个命令，做完进程结束**：

  ```
  java -jar forcitis-one.jar --spring.profiles.active=identity-operations --operation=<命令> --user-id=<人员 ID> \
       [--login-name=<应急账号名>] [--role-code=<角色码>] --operator=<操作人> --witness=<见证人>
  ```

  - 命令：`register-emergency-admin`（需 `--login-name`）、`reset-emergency-admin-credentials`、`disable-emergency-admin`、`enable-emergency-admin`、`grant-role`（需 `--role-code`，用于首批超级管理员、业务管理员、组织数据管理员）。
  - **双人在场**：`--operator` 与 `--witness` 必填且必须是两个不同的人（比较前去掉首尾空白、折叠空格、不分大小写、全角按半角），两人都记入审计；缺一或相同则命令拒绝，什么也不做。
  - 口令与 TOTP 密钥由命令**生成**，只在这一次输出里显示（口令、base32 密钥、认证器链接），库里只存口令的 Argon2id 哈希与加密后的密钥；不要把输出复制进文件或聊天。口令强度与密钥长度由代码保证，不经命令行参数传入。
  - 档案关闭所有定时任务（`forcitis.platform.scheduling.jobs-enabled=false` 及各任务自己的开关），Web 部分只监听本机随机端口，几秒钟后随进程结束；命令发布的事件若监听器没来得及处理，仍留在事件发布登记表里，由常驻应用的重投作业接手（§9）。
  - 退出码：0 成功（含「已是该状态，未改变」）；1 被拒绝（错误码在标准错误输出）；2 用法错误。
  - 注册后若可用应急账号少于 2 个，命令输出 `WARNING`；必须有至少 2 个、绑定不同的超级管理员持有人。
- 应急账号只能从内网段登录，失败 5 次锁 15 分钟；检查与计数在凭据验证之前一步完成，并发猜测也只有 5 次机会。应急登录直接进入管理模式，1 小时后会话结束。
- 每日核对：任务 `platform.identity.emergency.admin.check`（ShedLock，每天一次，`forcitis.platform.identity.emergency.check.cron`）核对应急账号数量与可用性，少于 2 个可用即告警；总开关 `forcitis.platform.scheduling.jobs-enabled`（默认 true），本任务另有 `forcitis.platform.identity.emergency.check.enabled`。
- **告警走 C-7 的日志告警规则**：本节与管理模式的告警都是一行 `ALERT platform.identity …` 的日志（结构化日志，见 §8），由 SLS 按 `ALERT` 关键字匹配后发往 A-5 指定的接收人；应用本身不直接发消息。涉及的告警：进入管理模式、退出或自然到期、应急登录、结束超级管理员或安全审计员的会话、应急账号因离职被停用、同步放行（`ALERT organization.workforce sync run …`）。
- 同步因管理员离职而暂停时（ADR-0028）：留任的超级管理员在管理模式、或安全审计员在普通会话，通过 `POST /api/v1/organization/sync-runs/{id}:release` 写明理由放行；放行后将没有可用超级管理员时页面拒绝。**恢复路径**（B1-R2，选的是写明路径而不是新增命令）：先用本节的 `grant-role --role-code=super_admin` 给一个留任的人授出超级管理员（双人在场、写审计），再由此人进入管理模式放行该暂停。
- 开通人和被开通人都记入审计（`platform.identity.operations.*`）。
- 剧本见 `runbooks/C-5-first-admins.md`（待按本节对齐）。

## 7. 首次部署步骤（dev）

1. CI 在 main 上构建 jar 和前端 dist，产物附带版本号与 SHA256。（**CI 目前只做 verify，不产出发布包**；Maven 产物名为 `forcitis-one-<版本>.jar`，上传时改名为 `forcitis-one.jar` 以符合 E-6。）
2. 经云助手上传到 `/data/forcitis-one/releases/<ver>/`，校验 SHA256。
3. 迁移（只迁移后退出）：用迁移账号启动一次，带 profile `migrate`，进程在建立应用上下文之前直接执行 Flyway 迁移并退出，不启动 web、作业、流程引擎和事件监听。迁移成功退出码 0，输出 `MIGRATE-ONLY OK`；失败退出码 1，输出 `MIGRATE-ONLY FAILED: <原因>`，部署在这一步停下。
   - 命令：`SPRING_PROFILES_ACTIVE=migrate,dev java -jar forcitis-one.jar`（生产用 `migrate,prod`），环境变量只给迁移账号：`SPRING_FLYWAY_URL`（缺省取 `SPRING_DATASOURCE_URL`）、`SPRING_FLYWAY_USER`、`SPRING_FLYWAY_PASSWORD`；schema 与历史表的设置见 §2。连接的 TLS 要求同 §5.1（`dev` / `prod` 同时写上才会检查）。
   - 首次还要以 `FLOWABLE_SCHEMA_UPDATE=true` 启动常驻服务一次建 `ACT_*`（见 §2）。
   - **常驻服务不自己迁移**：生产 profile 的 `application-prod.properties` 已设 `spring.flyway.enabled=false`，环境里也写 `SPRING_FLYWAY_ENABLED=false`；常驻进程不持有迁移凭据。
4. 切换 `current` 软链，`systemctl restart forcitis-one`，检查健康。
5. 冒烟：匿名访问返回 401；企业微信 / 致远登录；`/me` 正常；组织同步跑一轮，sync_runs 为 SUCCEEDED。
6. 回滚：`current` 指回上一版本并重启。数据库迁移只做向前兼容的变更，1a 期间不做破坏性迁移。

## 8. 日志、关联号与业务时区（本轮实施，运维需要知道的行为）

- **结构化日志**：应用写 ECS 格式的 JSON，一行一个事件（Spring Boot 结构化日志）。字段含 `@timestamp`、`log.level`、`log.logger`、`message`，以及日志上下文里的 `correlationId`（每个请求、作业运行、事件处理各一个）和 `userId`（只记 ID，不记姓名、手机号、令牌）。
  - 默认写标准输出，由 systemd 单元转入 `/data/forcitis-one/logs/*.json`，再由 SLS 采集；也可设置环境变量 `LOGGING_FILE_NAME` 直接落文件。
  - 排查一次操作：拿到响应头 `X-Correlation-Id`，在 SLS 里按 `correlationId` 检索，请求、事件处理、出站投递、发往企业微信的请求用的是同一个值。
- **关联号**：请求头 `X-Correlation-Id`（必须是 UUID，不是则 400）；没有就生成，并写进响应头。发给企业微信的请求带同一个头（对方忽略也无妨）。
- **业务时区**：`forcitis.kernel.business-zone`，默认 `Asia/Shanghai`；业务日、工作日历、`businessDate` 都按它截日，时刻一律以 UTC 存储（数据库只允许 `timestamptz`）。
- **指标命名**：`forcitis.<子域>.<bc>.…`，例如 `forcitis.platform.integration.deliveries.dead.letters`；看板和告警规则用新名字。
- **新增 schema**：`organization_attendance`（工作日历），已写入 `one_db_roles_schemas.sql`；上线前需在每个环境执行该脚本中对应的建 schema 与授权语句，再启动应用。
- **工作日历要先维护**：依赖工作日历的计算（审批时限等）在所属年份没有维护时失败（提交本身已成功，失败的是审批发起，单据保持已提交，补录日历后重投）并记 `ALERT` 日志与指标 `forcitis.organization.attendance.calendar.unmaintained`；管理员用 `PUT /api/v1/organization/work-calendars/{年}` 维护（权限 `organization.attendance:work-calendar:update`），每年底前维护下一年。
- **今天之前的日期不可修改**（T2）：已维护过的年份里，改动早于今天的日期会得到 422 `ORGANIZATION_ATTENDANCE_LOCKED_DAY_UNCONFIRMED`；一个年份的首次维护不受此限。例外是独立用例 `MaintainLockedWorkCalendarDays`，要求权限 `organization.attendance:locked-day:update`（只授给组织数据管理员），并发出 `WorkCalendarLockedDaysChanged`：载荷与审计条目的说明里列出每个被改日期及其改前改后的类别。已算出的审批截止时刻不重算。

## 9. 事件发布登记表的运维（ADR-0025 评审 C-4、C-8）

- 事件与业务写入同事务写入 `platform_integration.event_publication`；处理完成的发布移入 `event_publication_archive`（`spring.modulith.events.completion-mode=archive`），保留 30 天。
- **重投作业**（`platform.integration.event.republish`，ShedLock 互斥）：每 `republish-interval`（默认 1 分钟）重投超过 `minimum-age`（默认 1 分钟）仍未完成的发布，最多 `maximum-attempts`（默认 8）次。
- **告警**：用尽次数仍未完成的发布数 → 指标 `forcitis.platform.integration.event.publications.exhausted` 与每分钟一条 `ALERT` 日志；未完成超过 10 分钟的发布数 → `forcitis.platform.integration.event.publications.stale`。两者对应方案中的 P2 告警（未完成事件发布 / 死信）。
- **清理作业**（`platform.integration.event.cleanup`，每日 04:30）：删除归档中超过 30 天的行。
- 定时作业总开关：环境变量 `FORCITIS_JOBS_ENABLED` **必须设置**（`true` 或 `false`），没有默认值，缺失时应用启动失败；多个实例同时设为 `true` 没有问题，ShedLock 保证每个作业同一时刻只在一个实例上运行。
- 开关：环境变量 `EVENT_PUBLICATION_JOBS_ENABLED`（默认 true）；其余参数见 `config/platform_integration.properties` 的 `forcitis.platform.integration.event-publication.*`。
- 人工处理用尽次数的发布：先看 `event_publication` 里该行的 `listener_id` 与应用日志中同一 `correlationId` 的错误，修复原因后把 `completion_attempts` 清零，重投作业会再次接手。

## 10. 备份与恢复（平台约定，目标值用户已定）

- **目标**：数据丢失窗口 RPO ≤ 5 分钟，恢复时间 RTO ≤ 1 小时。
- **做法**：RDS 开启日志备份（实时增量）与每日全量，保留 ≥ 7 天；恢复到同实例新库或新实例，应用改连接串后启动（Flyway 与 Flowable 不再建表，见 §2、§7）。
- **恢复演练**：上线前一次、之后每季度一次，计时并留记录（恢复到新库 → 应用启动 → 冒烟：登录、取待办、取号、查审计）。演练超过 RTO 即视为未达标，列入下一轮改进。
- **应用状态**：应用本身无状态（会话在 `platform_identity.sessions`，随库恢复，恢复后所有会话仍有效；如恢复到较早时间点，建议同时清空会话表让所有人重新登录）。ECS 数据盘快照策略见 E-17。
- **取号连续性**：计数器在 `platform_numbering.numbering_counters`，随库恢复；恢复到较早时间点会让已发出的号码重复，因此**恢复后先用「计数器对账」核对**每个计数器不小于已发单据的最大序号，再放行取号。

## 11. 分级告警（平台约定）

接收群与值班人**部署前由用户决定**（不在仓库中写任何人名、群号或 webhook）；下表只定级别与响应时限。

| 级别 | 含义 | 例子 | 响应 |
|---|---|---|---|
| P1 | 业务中断或数据风险 | 应用不可用；数据库连接耗尽；取号失败；审计写入失败；事件发布用尽次数 | 立即通知值班人，30 分钟内处理 |
| P2 | 功能降级但可绕过 | 组织同步延迟超过两个周期；定时作业连续失败；企业微信投递死信；对账有差异 | 当班工作日内处理 |
| P3 | 需要关注 | 序号用到 90 %；登录失败率偏高；缩水保护扣留；未维护的工作日历年度 | 每周例会处理 |

- 规则放在 ARMS / SLS 中，名称以 `forcitis.<子域>.<bc>.…` 指标为准；每条告警写明级别、阈值、处理手册链接。
- 新增指标：`forcitis.organization.workforce.reconciliation.differences`（每日对账差异数，非零为 P2）。
- 新增限流：登录类请求按「客户端地址 + 请求类别」每分钟计数，超出返回 429；类别与配置项为 `forcitis.platform.identity.login-throttle.limits.<类别>`，类别包括 `login-states`、`wecom-login`、`sso-login`、`ticket-login`、`emergency-login`，没有默认值，缺一个启动失败；计数器在单个节点内，多节点部署时总额 = 节点数 × 限额。
- **限额依据办公室出口地址**：全所人员常经同一个出口 IP 上网，上班时段短时间内集中登录，所以人人都要用的 `login-states`（600）和 `wecom-login`（300）取数百，单人使用的取较低值。上线前用生产反向代理访问日志实测：取工作日早高峰最忙一分钟内「同一出口地址的 `login-states` 与 `wecom-login` 请求数」的最大值，限额至少取其 2 倍；若各分所出口地址不同，按最大的那个算。命中 429 时先查日志中的客户端地址与请求类别，再决定调限额，不要整体关闭限流。

## 12. 登录失败的排查：默认角色缺失
- 现象：所有人登录都返回 503，错误码 `PLATFORM_IDENTITY_DEFAULT_ROLE_MISSING`。
- 原因：新员工首次登录时系统按配置项 `forcitis.platform.identity.default-role`（当前为 `employee`）授予默认角色；该角色在库里不存在或已被停用时，不能给新账号授权，登录按「服务不可用」拒绝，而不是放进一个没有权限的账号。
- 处理：在角色管理中确认该编码的角色存在且启用（系统预置角色不可删除；自定义角色被误删或停用时重建或启用）；不要为了让人登进来而把配置改成别的角色。已有账号不受影响。

## 13. 数据库扩展 btree_gist
- 用途：`organization_workforce` 的法人归属期间表（`org_unit_legal_entities`、`employee_legal_entities`）用 EXCLUDE 约束保证同一部门或员工的期间不重叠，约束里的等值比较需要 `btree_gist`。
- 建库时由有权限的账号执行 `CREATE EXTENSION IF NOT EXISTS btree_gist;`（见 `one_db_roles_schemas.sql`）。Flyway 迁移中也带同一句 `IF NOT EXISTS`；迁移账号没有建扩展权限时，迁移会失败，先补上这一步再重试。
- RDS PostgreSQL 的 `btree_gist` 默认可用，不需要改 `shared_preload_libraries`，也不需要重启。

