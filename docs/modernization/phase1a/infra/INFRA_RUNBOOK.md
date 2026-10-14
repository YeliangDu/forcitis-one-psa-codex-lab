# FORCITIS one · Phase 1a 基础设施运维清单（dev）

日期：2026-10-14 · 会话：psa-1a-platform-bridge-reimagine · 状态：**草稿，未执行**

**清单里的每一项都是对外变更**，包括云助手执行命令、建库建角色、DNS、证书、企业微信和致远的配置。执行前由 psa-coordinator 请用户逐项确认，确认后由运维执行。本文件和仓库都不保存任何凭据。

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
| D-4（部署前再问） | A8 库（SQL Server）与老 PSA 库（SQL Server RDS）的只读视图和账号由谁建 | 见 §4。需要 A8 管理员和老 RDS 的 DBA 分别执行，并把 172.16.89.86 加入两库白名单 |
| D-5（部署前再问） | 应急管理员开通方式 | 见 §6，建议使用一次性运维命令，不提供 HTTP 接口 |

## 2. PostgreSQL（`forcitis_one_dev`；生产库 `forcitis_one_prod` 同实例另建，见 D-1）

脚本见 `one_db_roles_schemas.sql`。执行顺序：
1. 用 RDS 高权限账号执行 §0 冲突检查。任何一项查到结果就停止，并把结果报给 psa-coordinator。
2. 执行 §1 至 §3：
   - 建角色：`one_dev_owner`（属主，不可登录）、`one_dev_migrator`（迁移）、`one_dev_app`（运行）、`one_dev_readonly`（排障，可选）；
   - 建 schema，共 8 个：`one_migration`（Flyway 历史表）和 7 个模块 schema；
   - 授权：运行角色只有 DML，审计表只有 INSERT 和 SELECT；只授权本系统自己的 schema，不改数据库级授权和 `public` 的现有授权。
3. 在 RDS 控制台为 `one_dev_migrator`、`one_dev_app`、`one_dev_readonly` 设置随机口令（≥24 位）。口令只写入服务器受保护文件（§5）或 KMS，不经过聊天、不进仓库。
4. 首次部署（§7）：用 `one_dev_migrator` 运行 Flyway；再以 `FLOWABLE_SCHEMA_UPDATE=true` 启动一次，建 `ACT_*`；之后改用 `one_dev_app` 常驻运行。

与 it_portal 隔离：所有 schema 和角色都以本系统模块名或 `one_` 为前缀；不触碰 it_portal 的 schema；连接数上限分别设为 one_dev_app 40、one_dev_migrator 2、one_dev_readonly 3。

应用侧需要的配置（随骨架补齐，属代码改动，由本会话完成）：
- `spring.flyway.default-schema=one_migration`
- `spring.flyway.schemas` 列出全部模块 schema，`create-schemas=false`（由 DBA 预建）
- 部署时迁移连接用 one_dev_migrator，运行连接用 one_dev_app。分开配置 `spring.flyway.user` / `password` 和 `spring.datasource.*`。

## 3. ECS 初始化（i-bp122xlpar8w2uylmal2）

| # | 项 | 做法 | 验证 |
|---|---|---|---|
| E-1 | 数据盘 | 200G ESSD 格式化为 xfs，挂载到 `/data`，fstab 用 UUID 并加 `nofail` | `df -h /data`；重启后仍在 |
| E-2 | 系统更新与时区 | `dnf update`；时区 Asia/Shanghai；启用 chronyd | `timedatectl` |
| E-3 | 运行用户 | `useradd --system --home /data/forcitis-one --shell /sbin/nologin forcitis` | `id forcitis` |
| E-4 | Java 运行时 | Eclipse Temurin 25 JRE（RPM 包，固定小版本），或 Alibaba Dragonwell 25；要与 CI 的 Java 25 一致 | `java -version` |
| E-5 | 目录 | `/data/forcitis-one/{releases,current→releases/<ver>,logs,tmp}`，属主 forcitis，权限 750；`/etc/forcitis-one/` 属主 root:forcitis，权限 750 | |
| E-6 | 服务 | systemd 单元 `forcitis-one.service`：User=forcitis；`EnvironmentFile=/etc/forcitis-one/one.env`；`ExecStart=/usr/bin/java -XX:MaxRAMPercentage=60 -jar /data/forcitis-one/current/forcitis-one.jar`；`Restart=on-failure`；加固项 `NoNewPrivileges=yes`、`ProtectSystem=strict`、`ReadWritePaths=/data/forcitis-one/logs /data/forcitis-one/tmp`、`PrivateTmp=yes` | `systemctl status` |
| E-7 | 应用端口 | 应用只监听 `127.0.0.1:8080`；管理端点另设 `127.0.0.1:8081`，只开放 health 和 prometheus | 外网扫描只看到 80/443 |
| E-8 | 反向代理 | nginx（发行版包）：80 端口 301 跳转到 443；443 用 TLS 1.2/1.3；`proxy_pass http://127.0.0.1:8080`；**覆盖** `X-Forwarded-For` 和 `X-Forwarded-Proto`（应用的 `FORCITIS_TRUSTED_PROXIES` 只信任 127.0.0.1）；开启 HSTS；`client_max_body_size` 20m；不转发 `/actuator`、`/v3/api-docs` | SSL Labs A；`curl -I` |
| E-9 | 证书 | `one.forcitis.com` 证书放在 `/etc/nginx/tls/`，权限 600；如用 ACME，配置自动续期 | 到期日监控 |
| E-10 | 前端静态资源 | PC 端 `dist` 放到 `/data/forcitis-one/current/web`，由 nginx 直接提供；H5 同理放到 `/h5` | |
| E-11 | 日志接 SLS | 安装 Logtail；采集 `/data/forcitis-one/logs/*.json`（应用输出结构化 JSON）和 nginx access / error 日志；Project 与门户分开或按 Logstore 区分 | SLS 能检索到日志 |
| E-12 | 指标接 ARMS | 方案 A：ARMS Prometheus 抓取 `127.0.0.1:8081/actuator/prometheus`。方案 B：ARMS Java 探针，需先核实对 Java 25 的支持，不支持就用方案 A | ARMS 看板有数据 |
| E-13 | 四类告警 | 在 ARMS / SLS 中配置以下四类，并在 dev 上用故障注入演练（出口条件）：<br>• 组织同步延迟：`organization.workforce.sync.*` 指标，或缩水保护中止<br>• 未完成事件发布：event_publication 中超过 10 分钟仍未完成，或出站投递进入死信<br>• 作业失败：定时任务观测指标，或 Flowable 死信任务数<br>• 登录失败率：5 分钟内失败率超过 20 % 且失败数 ≥ 20 | 演练记录 |
| E-14 | 出站网络 | 安全组出方向放行：RDS 5432（同 VPC）、`qyapi.weixin.qq.com:443`、致远 OA 验票地址、A8 SQL Server、老 PSA SQL Server（经白名单）；其余出站按需收紧 | |
| E-15 | 企业微信 | 在企业微信后台为新应用配置：可信域名 `one.forcitis.com`、扫码登录回调域、企业可信 IP 47.114.56.157；工作台**新增**「FORCITIS one」入口，**不替换**老 H5（切换手册 §3.11） | 登录冒烟 |
| E-16 | 致远 | 由 A8 管理员登记新系统的验票回跳地址，**不替换**老 SSO | 登录冒烟 |
| E-17 | 备份 | RDS 自动备份保留 ≥7 天；ECS 数据盘设快照策略；日志在 SLS 中保留 30 天 | |

## 4. 外部只读账号（SQL Server，由对方 DBA 执行）

| 库 | 对象 | 账号 | 授权 | 脚本 |
|---|---|---|---|---|
| A8（a8_db） | 组织视图（只含白名单列） | 只读，专用 | 只授予视图 SELECT，基表 DENY，无 EXEC | `phase1a/SEEYON_VIEWS.sql`，重构完成前名为 `A8_VIEWS.sql` |
| 老 PSA（ruoyi-vue-pro） | 老待办视图 | 只读，专用 | 只授予视图 SELECT，**令牌表不授权**（I-1）；应用启动时校验能 SELECT 的对象恰好只有这一个 | `phase1a/LEGACY_INBOX_VIEW.sql` |
| 老 PSA | 组织短名视图 | 同上或另设 | 同上 | 随重构给出 |

两库都要把 172.16.89.86 加入白名单。账号口令只写入 `/etc/forcitis-one/one.env` 或 KMS。

## 5. dev 环境连接配置的取法

- **唯一来源**：服务器上的 `/etc/forcitis-one/one.env`，权限 `0640 root:forcitis`，由 systemd 的 `EnvironmentFile` 加载。仓库、镜像、聊天、工单中都不出现这些值。
- **后续计划**：迁到阿里云 KMS 凭据管家，启动时拉取，one.env 只保留 KMS 引用。上线前完成。
- **PG 连接**：`SPRING_DATASOURCE_URL=jdbc:postgresql://<RDS 内网地址>:5432/forcitis_one_dev?sslmode=verify-full&sslrootcert=/etc/forcitis-one/rds-ca.pem`，`SPRING_DATASOURCE_USERNAME=one_dev_app`，口令单独一行。迁移连接用 `SPRING_FLYWAY_USER` / `SPRING_FLYWAY_PASSWORD`（one_dev_migrator），只在部署步骤注入，常驻进程不持有迁移凭据。
- **应用变量清单**（以代码为准，重构后键名改为按角色命名）：

| 变量 | 用途 | 必需 |
|---|---|---|
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | PG 运行连接 | 是 |
| `SPRING_FLYWAY_USER` / `_PASSWORD` | 迁移（仅部署） | 部署时 |
| `FLOWABLE_SCHEMA_UPDATE` | 仅引擎安装 / 升级时为 true | 否 |
| `FLOWABLE_ASYNC_EXECUTOR` | 流程引擎异步执行器开关（默认 true） | 否 |
| `APPROVAL_PROCESS_ADMINISTRATOR_USER_ID` | 取不到审批人时的兜底人 | 是 |
| `APPROVAL_TASK_URL_TEMPLATE` | 卡片跳转地址 | 否（有默认值） |
| `EMERGENCY_TOTP_ENCRYPTION_KEY` | 应急管理员 TOTP 密钥加密（32 字节 Base64） | 是 |
| `FORCITIS_TICKET_KEY_<KID>` | 签名票据签发方密钥；签发方默认关闭，当前白名单为空 | 否 |
| `FORCITIS_TRUSTED_PROXIES` | 只信任 nginx（127.0.0.1） | 是 |
| `SEEYON_OA_BASE_URL`、`LEGACY_LOGIN_URL` | 致远验票地址；失败时回老系统登录 | 是 |
| `WECOM_ENABLED` | 企业微信网关与出站投递总开关（默认 false） | 否 |
| `WECOM_BASE_URL` | 企业微信 API 地址（默认 `https://qyapi.weixin.qq.com`） | 否 |
| `WECOM_CORP_ID`、`WECOM_AGENT_ID` | 新企业微信应用的**新值**（老应用的值已在老仓历史中泄露，I-8） | 启用时 |
| `WECOM_APP_SECRET` | 同上，应用 secret，只写入 one.env 或 KMS | 启用时 |
| `WECOM_RESTRICT_RECIPIENTS` | 是否只向白名单收件人发送（默认 true）；**生产必须为 false** | 是 |
| `WECOM_RECIPIENT_ALLOWLIST` | 收件人白名单（`WECOM_RESTRICT_RECIPIENTS=true` 时生效，dev 联调用） | 否 |
| `INTEGRATION_SEEYON_VIEWS_ENABLED` | 致远 A8 组织视图读取开关（默认 false） | 否 |
| `A8_DB_URL` / `A8_DB_USERNAME` / `A8_DB_PASSWORD` | A8 视图只读连接（第 4 节的专用账号） | 启用时 |
| `INTEGRATION_SEEYON_VALIDATE_ON_STARTUP` | 启动时校验 A8 账号只能读视图（默认 true） | 否 |
| `A8_HEAD_RELATIONSHIP_TYPE_ID`、`A8_REGIONAL_RECEIVABLES_MANAGER_RELATIONSHIP_TYPE_ID`、`A8_DELIVERY_UNIT_YES_ENUM_ID`、`A8_FEE_EARNER_YES_ENUM_ID`、`A8_ROOT_PATH` | A8 中关系类型、枚举的编号与同步根单位的层级路径（有默认值，与 A8 实例不一致时覆盖） | 否 |
| `INTEGRATION_LEGACY_ENABLED` | 老 PSA 待办视图读取开关（默认 false） | 否 |
| `LEGACY_DB_RO_URL` / `LEGACY_DB_RO_USERNAME` / `LEGACY_DB_RO_PASSWORD` | 老库只读连接（第 4 节的专用账号） | 启用时 |
| `INTEGRATION_LEGACY_VALIDATE_ON_STARTUP` | 启动时校验老库账号只能读那一个视图（默认 true） | 否 |
| `WORKFORCE_SYNC_ENABLED` | 从 A8 视图同步组织与人员的定时任务开关（默认 false） | 否 |
| `WORKFORCE_SHORT_NAME_ENABLED` | 从老库同步组织短名的定时任务开关（默认 false） | 否 |
| `SPRING_PROFILES_ACTIVE=dev` | 环境 | 是 |
| 生产附加 | `springdoc.api-docs.enabled=false` | 生产 |

## 6. 应急管理员开通（设计，执行需用户确认）

- 不提供 HTTP 开通接口（设计如此）。
- 由应用提供一次性运维命令，例如 `java -jar forcitis-one.jar --forcitis.ops.command=register-emergency-admin`，经云助手在服务器上执行。执行流程：
  1. 从标准输入读取账号名和初始口令，不经命令行参数，避免进入进程列表和历史；
  2. 校验口令强度（≥12 位并有复杂度要求，RULE-317）；
  3. 生成 TOTP 密钥，用 `EMERGENCY_TOTP_ENCRYPTION_KEY` 加密后入库；
  4. 只输出一次 TOTP 绑定二维码 / URI，供管理员当场绑定；
  5. 写审计记录，然后退出，不启动 Web。
- 只能从内网段登录，失败 5 次锁 15 分钟（已实现）。
- 开通人和被开通人都记入审计；首次登录后强制改口令（待实现）。

## 7. 首次部署步骤（dev）

1. CI 在 main 上构建 jar 和前端 dist，产物附带版本号与 SHA256。
2. 经云助手上传到 `/data/forcitis-one/releases/<ver>/`，校验 SHA256。
3. 迁移：以 one_dev_migrator 运行 Flyway（`--spring.main.web-application-type=none` 的迁移模式）。首次还要以 `FLOWABLE_SCHEMA_UPDATE=true` 建 ACT_*。
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
- **工作日历要先维护**：依赖工作日历的计算（审批时限等）在所属年份没有维护时失败并记 `ALERT` 日志与指标 `forcitis.organization.attendance.calendar.unmaintained`；管理员用 `PUT /api/v1/organization/work-calendars/{年}` 维护（权限 `organization.attendance:work-calendar:update`），每年底前维护下一年。

## 9. 事件发布登记表的运维（ADR-0025 评审 C-4、C-8）

- 事件与业务写入同事务写入 `platform_integration.event_publication`；处理完成的发布移入 `event_publication_archive`（`spring.modulith.events.completion-mode=archive`），保留 30 天。
- **重投作业**（`platform.integration.event.republish`，ShedLock 互斥）：每 `republish-interval`（默认 1 分钟）重投超过 `minimum-age`（默认 1 分钟）仍未完成的发布，最多 `maximum-attempts`（默认 8）次。
- **告警**：用尽次数仍未完成的发布数 → 指标 `forcitis.platform.integration.event.publications.exhausted` 与每分钟一条 `ALERT` 日志；未完成超过 10 分钟的发布数 → `forcitis.platform.integration.event.publications.stale`。两者对应方案中的 P2 告警（未完成事件发布 / 死信）。
- **清理作业**（`platform.integration.event.cleanup`，每日 04:30）：删除归档中超过 30 天的行。
- 开关：环境变量 `EVENT_PUBLICATION_JOBS_ENABLED`（默认 true）；其余参数见 `config/platform_integration.properties` 的 `forcitis.platform.integration.event-publication.*`。
- 人工处理用尽次数的发布：先看 `event_publication` 里该行的 `listener_id` 与应用日志中同一 `correlationId` 的错误，修复原因后把 `completion_attempts` 清零，重投作业会再次接手。
