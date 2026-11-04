# FORCITIS one · Phase 1a 基础设施运维清单（dev）

日期：2026-10-14 · 会话：psa-1a-platform-bridge-reimagine · 状态：**草稿，未执行**

**清单里的每一项都是对外变更**，包括云助手执行命令、建库建角色、DNS、证书、企业微信的配置及 A8 一次性迁入通道（受控退役用途）的配置。执行前由 psa-coordinator 请用户逐项确认，确认后由运维执行。本文件和仓库都不保存任何凭据。

逐项执行剧本见 `runbooks/`（C 类），交给外部执行人的交接包见 `handoff/`（B 类），用户决定单见 `DECISIONS_A.md`，与代码的核对结果见 `CROSSCHECK.md`。

> **运维纪律：不得调用会返回密钥的接口，也不打印云接口的原始返回。** 例如 `rds DescribeDBInstanceSSL` 会返回实例证书私钥（2026-10-15 曾因此使私钥进入会话记录，见 C-3 执行记录）。查询云资源时在进程内只取所需字段；口令、私钥、令牌只在服务器上生成与使用，跨机只以加密形式或经云助手文件下发。
>
> **生产库查询纪律：只做有索引的查询，并设语句超时。** 老系统、A8 仅限一次性迁入、核对、归档调阅及取证的受控退役访问，不作为 FORCITIS one 运行时依赖。对生产库（老系统、A8、RDS）的只读查询只走主键或已有索引，分页取数；不跑无索引的大表关联或全表扫描；每条语句设超时（探针为 120 秒）。2026-10-15 曾有一条探查对应关系的无索引关联查询被超时终止，未造成影响。

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
| D-4（受控退役用途执行前确认） | A8 库（SQL Server）与老系统库（SQL Server RDS）的一次性迁入、核对及归档调阅通道由谁办理 | 见 §4。读取范围、账号、白名单及退出条件随用途方案确认，由 A8 管理员和老系统库 DBA 按另行授权的运维卡执行；不列为常驻服务部署前提，用完回收 |
| D-5（部署前再问） | 应急管理员开通方式 | 见 §6，建议使用一次性运维命令，不提供 HTTP 接口 |

## 2. PostgreSQL（`forcitis_one_dev`；生产库 `forcitis_one_prod` 同实例另建，见 D-1）

脚本见 `one_db_roles_schemas.sql`（评审草稿）；**可执行版**为 `runbooks/sql/dev-db.sql`（dev）与 `runbooks/sql/prod-db.sql`（生产，由 dev 版生成），剧本见 `runbooks/dev-db.md`。可执行版另加了库级 CREATE 授权（迁移含 `CREATE SCHEMA IF NOT EXISTS`）、PG 16 起执行人对属主角色的 SET 授权、按角色的 `work_mem` 与只读角色超时。执行顺序：
1. 用 RDS 高权限账号执行 §0 冲突检查。任何一项查到结果就停止，并把结果报给 psa-coordinator。
2. 执行 §1 至 §3：
   - 建角色：`one_dev_owner`（属主，不可登录）、`one_dev_migrator`（迁移）、`one_dev_app`（运行）、`one_dev_readonly`（排障，可选）；
   - 建 schema，共 8 个：`one_migration`（Flyway 历史表）和 7 个模块 schema；
   - 授权：运行角色只有 DML，审计表只有 INSERT 和 SELECT；只授权本系统自己的 schema，不改数据库级授权和 `public` 的现有授权。
3. 在 RDS 控制台为 `one_dev_migrator`、`one_dev_app`、`one_dev_readonly` 设置随机口令（≥24 位）。口令只写入服务器受保护文件（§5）或 KMS，不经过聊天、不进仓库。
4. 首次部署（§7）：用 `one_dev_migrator` 运行 Flyway；再以 `FLOWABLE_SCHEMA_UPDATE=true` 启动一次，建 `ACT_*`；之后改用 `one_dev_app` 常驻运行。

与 it_portal 隔离：所有 schema 和角色都以本系统模块名或 `one_` 为前缀；不触碰 it_portal 的 schema；连接数上限分别设为 one_dev_app 25、one_dev_migrator 2、one_dev_readonly 3。

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
| E-2 | 系统更新与时区 | `dnf update`；时区 Asia/Shanghai；启用 chronyd（时间源与告警见 §8「节点时间同步」） | `timedatectl` |
| E-3 | 运行用户 | `useradd --system --home /data/forcitis-one --shell /sbin/nologin forcitis` | `id forcitis` |
| E-4 | Java 运行时 | Eclipse Temurin 25 JRE（RPM 包，固定小版本），或 Alibaba Dragonwell 25；要与 CI 的 Java 25 一致 | `java -version` |
| E-5 | 目录 | `/data/forcitis-one/{releases,current→releases/<ver>,logs,tmp}`，属主 forcitis，权限 750；`/etc/forcitis-one/` 属主 root:forcitis，权限 750 | |
| E-6 | 服务 | systemd 单元 `forcitis-one.service`：User=forcitis；`EnvironmentFile=/etc/forcitis-one/one.env`；`ExecStart=/usr/bin/java -Duser.timezone=UTC -XX:MaxRAMPercentage=60 -jar /data/forcitis-one/current/forcitis-one.jar`；`Restart=on-failure`；加固项 `NoNewPrivileges=yes`、`ProtectSystem=strict`、`ReadWritePaths=/data/forcitis-one/logs /data/forcitis-one/tmp`、`PrivateTmp=yes`。应用入口（`OneApplication.main`）在启动 Spring 之前自行把 JVM 默认时区设为 UTC，所以机器时区为 Asia/Shanghai（E-2）时也能启动，`-Duser.timezone=UTC` 作为第二道保留；同一 jar 的迁移模式与运维档案也经此入口 | `systemctl status` |
| E-7 | 应用端口 | 应用只监听 `127.0.0.1:8080`；管理端点另设 `127.0.0.1:8081`，只开放 health 和 prometheus。代码默认未设监听地址与管理端口、只暴露 health，须在 one.env 设 `SERVER_ADDRESS`、`MANAGEMENT_SERVER_ADDRESS`、`MANAGEMENT_SERVER_PORT`、`MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,prometheus`（`tools/ops/ecs/files/one.env.template`） | 外网扫描只看到 80/443 |
| E-8 | 反向代理 | nginx（发行版包）：80 端口 301 跳转到 443；443 用 TLS 1.2/1.3；`proxy_pass http://127.0.0.1:8080`；**覆盖** `X-Forwarded-For` 和 `X-Forwarded-Proto`（应用的 `FORCITIS_TRUSTED_PROXIES` 只信任 127.0.0.1）；开启 HSTS；`client_max_body_size` 20m；不转发 `/actuator`、`/v3/api-docs`；**访问日志不记查询串**（`/login/wecom?code=&state=` 的一次性凭证，D-1a-39），并加 `Referrer-Policy: no-referrer`（`tools/ops/ecs/files/nginx-one.conf`） | SSL Labs A；`curl -I` |
| E-9 | 证书 | `one.forcitis.com` 证书放在 `/etc/nginx/tls/`，权限 600；如用 ACME，配置自动续期 | 到期日监控 |
| E-10 | 前端静态资源 | PC 端 `dist` 放到 `/data/forcitis-one/current/web`，由 nginx 直接提供；H5 同理放到 `/h5`（构建期 `H5_PUBLIC_PATH` 默认 `/h5/`，用户决定 N-1，构建检查断言资源路径带子路径） | |
| E-11 | 日志接 SLS | 安装 Logtail；采集 `/data/forcitis-one/logs/*.json`（应用输出结构化 JSON）和 nginx access / error 日志；Project 与门户分开或按 Logstore 区分 | SLS 能检索到日志 |
| E-12 | 指标接 ARMS | 方案 A：ARMS Prometheus 抓取 `127.0.0.1:8081/actuator/prometheus`。方案 B：ARMS Java 探针，需先核实对 Java 25 的支持，不支持就用方案 A。**实际（2026-10-15）**：由 ECS 上的 LoongCollector 抓取，写入 SLS Prometheus 指标库 `forcitis-one-observability/metrics`（管理端口只听回环，VPC 内托管采集器抓不到；写 ARMS 需 AccessKey，不建），PromQL 告警在 SLS 上配置（C-7） | 指标库有数据 |
| E-13 | 三类告警 | 在 ARMS / SLS 中配置以下三类，并在 dev 上用故障注入演练（出口条件）：<br>• 未完成事件发布：event_publication 中超过 10 分钟仍未完成，或出站投递进入死信<br>• 作业失败：`forcitis_platform_scheduling_job_failures_total{job}` 与 `forcitis_platform_approval_dead_letter_jobs`，规则见下方「E-13 补充」<br>• 登录失败率：5 分钟内失败率超过 20 % 且失败数 ≥ 20 | 演练记录 |
| E-14 | 出站网络 | 安全组出方向放行：RDS 5432（同 VPC）、`qyapi.weixin.qq.com:443`；其余出站按需收紧。A8 验票不再放行；A8 / 老系统 SQL Server 仅在 §4 的受控退役用途运维卡授权范围内临时放行，读取结束后回收对应白名单及出站规则，不供常驻服务使用 | |
| E-15 | 企业微信 | 在企业微信后台为新应用配置：可信域名 `one.forcitis.com`、扫码登录回调域、企业可信 IP 47.114.56.157；工作台**新增**「FORCITIS one」入口，**不替换**老 H5（切换手册 §3.11） | 登录冒烟 |
| E-17 | 备份 | RDS 自动备份保留 ≥7 天；ECS 数据盘设快照策略；日志在 SLS 中保留：nginx 与 ops 180 天（《网络安全法》第二十一条：网络日志留存不少于六个月），app 30 天 | |
| E-18 | 数据库日志 | 慢语句阈值用实例级 `log_min_duration_statement` 1000 ms；实例级 `log_parameter_max_length = 0`，慢日志不带绑定参数值（库级无法设置；同实例 it_portal 同样不带，行为不变）；回退为改回 -1，不重启 | `DescribeParameters` 读回 |

**时区（E-6 补充）。** 业务日一律按 `forcitis.kernel.business-zone`（Asia/Shanghai）截取；时刻一律以 UTC 存储和输出，不依赖机器的时区：
- **JVM 必须以 UTC 运行**，systemd 的 `ExecStart` 带 `-Duser.timezone=UTC`。选「启动时校验」而不是只写进手册：应用启动时检查 JVM 默认时区，不是 UTC 就拒绝启动并说明如何修正（`kernel.UtcJvm`），因为手册步骤会被漏掉，而一个按本地时区跑起来的节点会让驱动、日志和 `java.util.Date` 悄悄与别的节点不一致。测试 JVM 同样带该参数。应用入口 `OneApplication.main` 先把默认时区设为 UTC（整合复审 INT-P1），所以这道校验在正常启动路径上总能通过，只拦其他方式建出的上下文。
- **JSON**：时刻写成带偏移量的 ISO-8601（UTC，形如 `2026-10-14T04:00:00Z`），时长写成 ISO-8601，业务日写成不带时区的日期；见 `application.properties` 的 `spring.jackson.*`。
- **数据库会话**：连接池对每个新连接执行 `SET TIME ZONE 'UTC'`（`spring.datasource.hikari.connection-init-sql`），不依赖 RDS 参数组、角色或库级默认值；URL 不用另加时区参数。

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
| `EnforceDeadlines`（审批时限，单个任务失败只记日志） | `forcitis_platform_approval_deadline_failed_total`（第一次失败时才出现，用 `increase`，不要用 `absent` 判断它） | `increase(…[10m]) > 0` |

## 4. 一次性迁入通道（受控退役用途；SQL Server，由对方 DBA 执行）

老系统与 A8 的连接仅用于一次性迁入、迁入核对、对账、归档调阅及取证，不随 FORCITIS one 常驻服务启动。读取对象与列、用途、账号、期限、数据去向和退出条件须在相应用途方案中登记；迁入与核对完成后关闭通道并回收对应配置、白名单及出站放行。归档调阅另用专用只读审计账号，不兼任取证或迁入；调阅按次授权，用完回收当次通道。原运行时只读账号在迁入、对账与归档调阅方案完成后撤，取证账号在老库归档确认与审计调阅方案落地后撤并登记；不按估算的一个月自动退出，实际动作须另有 one-ops 运维卡授权。下表只保留既有账号、脚本的核对入口，不据此新建、合并或撤销账号。

| 库 | 对象 | 账号 | 授权 | 脚本 |
|---|---|---|---|---|
| A8（a8_db） | 全库只读仅限受控退役用途；组织、人员等读取范围随一次性迁入规格确认，不保留运行时组织视图读取 | 既有专用只读账号 `one_a8_views_ro`；仅按用途方案使用并退出 | 库级 GRANT SELECT；库级 DENY INSERT、UPDATE、DELETE、ALTER、EXECUTE、REFERENCES、CREATE TABLE / VIEW / PROCEDURE / FUNCTION / SCHEMA；不入任何数据库角色 | `docs/ops/dba-scripts/read-all/a8-apply.sql`（历史组织视图：`docs/data/legacy-views/seeyon-views.sql`，仅供迁入适配器核对，不作常驻部署步骤） |
| 老系统（ruoyi-vue-pro） | 全库只读仅限受控退役用途；撤销合并待办与老待办视图的运行时读取 | 既有专用只读账号 `psa_one_legacy_ro`；仅按用途方案使用并退出 | 同上；凭据列可读但应用代码不引用（ADR-0037 第 3 项，凭据列拒绝清单守护） | `docs/ops/dba-scripts/read-all/legacy-apply.sql`；原老待办视图 `dbo.v_psa_one_legacy_todo` 及 `docs/data/legacy-views/legacy-inbox-view.sql` 不再作为部署步骤 |
| 老系统 | 部门简称仅在迁入规格确认有需要时用于一次性迁入匹配，须有取证与差异清单，不定时刷新 | 既有专用只读账号 `one_legacy_names_ro`；用途与退出另行确认，不自动与其他账号合并 | 同上 | 同上（历史视图 `dbo.v_one_legacy_org_short_name`、`docs/data/legacy-views/legacy-org-names-view.sql` 仅供迁入适配器核对） |

受控退役用途进程只对本次实际使用的只读连接做同一项校验（ADR-0037 第 4 项的访问控制保留，按 ADR-0055 决定 7 限定用途；`platform.integration` 连接工厂）：服务器级与库级有效权限**等于**写死的 RDS 平台基线，库级 DENY 11 项存在且库级 GRANT 恰好 CONNECT、SELECT，只在 public 角色，不在任何固定服务器角色，不是 dbo、不拥有任何 schema / 对象 / 角色、无主体类授权，`sys.server_permissions` 中本登录的行恰好一行 GRANT CONNECT SQL（无针对登录等的授权，也无其他 GRANT 或 DENY），`HAS_DBACCESS` 只有本库与系统库（master、tempdb、model、msdb 及 rdscore），系统库只经 guest 进入，例外是 RDS 为每个登录自动建的 msdb 与 rdscore 本人用户（见 4.1「(f) 系统库本人用户」，msdb 本人用户的直接授权与有效权限另有限制），且本登录不拥有 SQL Agent 作业，库级以下的授权（本登录的，及 public 在用户对象上的）只有 DENY、GRANT SELECT，或被本登录自己的同名 DENY 覆盖的 GRANT（被拒绝覆盖的授权，见 4.1「(g) 的覆盖口径」）。任一不满足或无法判定即拒绝启动本次退役用途进程，日志按 (a)–(g) 报出违反项；每次受控读取前重检；通道跨日仍在使用时至少每天重检一次：确认违规即停掉该连接（写 ALERT，重启前不再读取）；暂时判定不了（查询失败、超时、为空）只让本次读取不可用并写 ALERT，连续 3 次判定不了才停掉，一次判定通过即清零（用户 2026-10-15，intent 2026-10-15-120940）。**没有关闭开关**；账号授权变更后先用真实登录核对基线再启动本次退役用途进程。

DBA 可直接执行的脚本（含口令随机生成、校验与回滚）：`docs/ops/dba-scripts/`。

仅在受控退役用途运维卡授权的读取窗口内，将执行端实际地址加入所需库的白名单（既有 ECS 地址为 172.16.89.86）；不用的库不放行，结束后回收。账号口令只进入退役用途专用受保护环境文件或 KMS，按次注入专用进程，不进入常驻服务的 `/etc/forcitis-one/one.env`。

### 4.1 只读核实命令（PAR-2；C-14 与授权变更后的核对用）

同一个发布包里的 jar（`forcitis-one.jar`，入口不变），仅为受控退役用途账号核实，以档案 `read-only-verification` 运行，对**一条**只读连接的登录跑与该受控读取进程启动校验同一套判定（`ReadOnlyBaseline`，查询用 `JdbcReadOnlyCatalog`，连接与超时用连接工厂的后台限值），逐项输出后退出。**只支持 SQL Server**（URL 须以 `jdbc:sqlserver:` 开头，PostgreSQL 等一律用法错误）：

```
java -Duser.timezone=UTC -jar forcitis-one.jar --spring.profiles.active=read-only-verification --connection=<连接名>
```

- 连接名与读取的环境变量（以下是现有核实命令的兼容名称，不代表运行时读取仍保留；三项只放退役用途专用受保护环境文件，root 0600，one-ops 按用途方案确定；探针仍用 `/etc/forcitis-one/readonly-probe.env`）：

  | `--connection=` | 环境变量 |
  |---|---|
  | `integration-seeyon-background` | `A8_DB_URL`、`A8_DB_USERNAME`、`A8_DB_PASSWORD` |
  | `integration-legacy` | `LEGACY_DB_RO_URL`、`LEGACY_DB_RO_USERNAME`、`LEGACY_DB_RO_PASSWORD` |
  | `probe`（C-14 探针登录） | `READONLY_PROBE_DB_URL`、`READONLY_PROBE_DB_USERNAME`、`READONLY_PROBE_DB_PASSWORD` |

  三项**只从进程环境变量**读：`-D` 系统属性、命令行参数、`SPRING_APPLICATION_JSON` 里的同名设置一律不读；命令行出现这些变量名（`--A8_DB_URL=…`、`--readonly.probe.db.username=…` 等宽松写法也算）或 `--spring.application.json` 即用法错误。环境变量的给法：按次用 `systemd-run --pipe --wait --collect -p EnvironmentFile=<退役用途专用受保护环境文件> /usr/bin/java …`，不要把 env 文件 source 进交互 shell。只读所指定连接的三项；另两条连接配了什么不影响结果。缺任一项：用法错误，列出缺的变量名（不列值），不做核实。
- 输出：每项一行 `(a) PASSED|VIOLATED|UNDETERMINED`，之后可带 ` reason=<原因代码> count=<个数>`，以及查询失败时的 ` error=<SQL 错误号> sqlstate=<SQLState>`（驱动报出的最内层 SQL 异常；驱动未给 SQLState 时写占位 `sqlstate=none`）；末行 `RESULT PASSED|VIOLATED|UNDETERMINED`，有任一项违规即违规，否则有判定不了即判定不了。
  - `count` 按调用累计：判定中每次检查或查询算一次，违规时计该次发现的条数（多出或缺少的权限、角色、库、授权行），查询失败或返回为空计 1；同一项内同一原因代码（与同一错误号）合并相加。所以连不上时七项都带同一个错误号，计数为各项失败的查询数：(d) 3、(f) 2、其余各 1（只连一次，不按每个查询等连接池超时）。
  - 原因代码（`ReadOnlyBaseline.Reason`，22 个，只是类别，不含角色名、库名、登录名；属技术码，不进 `terms.yaml`——one-1a-next-3 同意 Unmet 加原因代码的条件⑤，PR 中单列）：

    | 项 | 确认违规 | 判定不了 |
    |---|---|---|
    | (a) | `EFFECTIVE_PERMISSIONS_DIFFER` 库级有效权限与基线不等 | |
    | (b) | `DATABASE_DENY_MISSING` 库级 DENY 缺项；`DATABASE_GRANT_NOT_BASELINE` 库级 GRANT 不恰为 CONNECT、SELECT；`GRANT_WITH_GRANT_OPTION` 可转授的授权 | |
    | (c) | `DATABASE_ROLE_MEMBERSHIP` 在 public 以外的库角色中 | |
    | (d) | `EFFECTIVE_PERMISSIONS_DIFFER` 服务器级有效权限与基线不等；`FIXED_SERVER_ROLE_MEMBERSHIP` 在固定服务器角色中；`SERVER_PERMISSION_ROW_NOT_BASELINE` 本登录在 `sys.server_permissions` 的行不恰为 GRANT CONNECT SQL | `FIXED_SERVER_ROLE_UNANSWERED` 服务器答不出某固定角色的成员关系 |
    | (e) | `DATABASE_OWNER` 是 dbo；`OWNS_SCHEMA`、`OWNS_OBJECT`、`OWNS_ROLE` 拥有 schema / 对象 / 角色；`DATABASE_PRINCIPAL_GRANT` 有主体类授权 | |
    | (f) | `OTHER_DATABASE_REACHABLE` 能进基线外的库；`SYSTEM_DATABASE_OWN_USER` 系统库里有超出允许形状的本人用户；`SYSTEM_DATABASE_OWN_USER_PRIVILEGE` msdb 本人用户的权限超出平台默认（`count` 为库数）；`OWNS_AGENT_JOB` 本登录拥有 SQL Agent 作业（`count` 为作业个数，不出定位行） | `OWN_DATABASE_NOT_LISTED` HAS_DBACCESS 不列本库；`NO_ANSWER` msdb 本人用户有叫不出名称的角色，或 msdb 有效权限（含 IMPERSONATE）某项答不出，或令牌范围扫描答 NULL、某行有列缺答；`EMPTY_ANSWER` msdb 本人用户的直接授权、msdb 令牌范围扫描或 rdscore 本人用户的授权为空 |
    | (g) | `SECURABLE_GRANT_BEYOND_SELECT` 库级以下有 DENY、GRANT SELECT 与被拒绝覆盖的授权以外的授权 | 可被覆盖的授权遇本登录 DENY 查不到：`CATALOG_QUERY_FAILED` 或 `EMPTY_ANSWER` |
    | 任一项 | | `CATALOG_QUERY_FAILED` 查询失败（带错误号）；`CHECK_FAILED` 判定本身出错（带错误号，若有）；`NO_ANSWER` 无应答；`EMPTY_ANSWER` 返回为空 |
- 定位行（(f)(g)；one-coordinator 2026-10-15 C-14 定位裁定；契约第 3a 节）：紧跟所属项行，两个空格开头，字段顺序固定，便于 `grep`：

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
    (g) violation reason=SECURABLE_GRANT_BEYOND_SELECT class=OBJECT_OR_COLUMN schema="dbo" object="t_x" objectId=1205579333 permission="CONTROL" state=GRANT grantee="public" granteeType=DATABASE_ROLE
    (g) overridden class=OBJECT_OR_COLUMN schema=- object=- objectId=1237579447 permission="EXECUTE" state=GRANT grantee="public" granteeType=DATABASE_ROLE by=DATABASE_DENY
  ```

  - (f) `violation`：`reason database`，原因 `SYSTEM_DATABASE_OWN_USER`（每个本人用户超出允许形状的系统库一行）、`SYSTEM_DATABASE_OWN_USER_PRIVILEGE`（msdb 本人用户权限越线，一行 `database="msdb"`，不写权限名与对象名）与 `OTHER_DATABASE_REACHABLE`（每个能进的基线外库一行）。
  - (f) `ownUser`（信息行，不计数、不违规，合规与违规都出）：`database role`，本登录在能进的系统库中有本人用户时每个角色一行，无角色一行 `role=-`；只列角色名，不列成员、权限与其他对象名。
  - (g) `violation`：`reason class schema object objectId permission state grantee granteeType`；(g) `overridden`（被拒绝覆盖的授权，信息行，不计数、不违规，项为 `PASSED` 时也出）：`class schema object objectId permission state grantee granteeType by`，`by` 写被哪条 DENY 覆盖：`DATABASE_DENY` 本登录库级同名 DENY，`SCHEMA_DENY` 本登录在该对象所属 schema 上的同名 DENY（两者都有写 `DATABASE_DENY`）。
  - 值：库名、schema、对象、grantee、权限名带双引号，只做最小转义（`\\`、`\"`、`\n`、`\r`、`\t`，其余控制字符、行 / 段分隔符与格式字符写 `\uXXXX`）；`class`、`state`（`GRANT` / `GRANT_WITH_GRANT_OPTION`）、`granteeType`、`by` 不带引号；取不到或没有的值写 `-`（schema 级授权的 `object`、无所属 schema 的安全对象的 `schema`）；授给本登录自己的写 `grantee=SELF`（用户名即登录名，不输出）。`objectId` 为 `sys.database_permissions.major_id`（数字，不带引号，始终输出）。
  - 看不见的对象写 `-`，用 `objectId` 定位：登录对某安全对象没有任何生效权限时（典型是授给 public 的 EXECUTE 被本登录库级 DENY EXECUTE 覆盖的过程），SQL Server 的元数据可见性让它在 `sys.objects` / `sys.schemas` 中不可见，`schema`、`object` 只能写 `-`；C-14 用取证账号在该库按编号反查，如 `SELECT SCHEMA_NAME(schema_id), name, type_desc FROM sys.objects WHERE object_id = <objectId>`（schema 级授权的 `objectId` 是 schema_id，查 `sys.schemas`）。限制（one-coordinator 2026-10-15 补充）：取证账号若同样没有该对象的生效权限，也看不见它，按 `objectId` 未必反查得到名称；要确认被拒绝覆盖的授权确实不可执行，可「以被核实账号执行该对象，被拒（错误号 229）」验证——这一步是否做由用户决定。同一条授权按 `(class, objectId, permission, state, grantee)` 只出一行。
  - 每条授权一行（列级授权记在所在表上），`count` 等于该原因的 `violation` 行数；先 `violation` 后 `overridden`，各按字典序。
  - 定位字段只由本命令输出：启动校验的拒绝原因、运行期重检的 ALERT、日志里只有项目标记与类别，不出现 schema 名与对象名。
- (g) 的覆盖口径（RULING-ADR37-10 依 ADR-0037 第 4 项 (b) 收窄为按有效权限判定；启动校验、运行期重检与本命令同一套）：授给 public 或本登录、状态为 GRANT（无转授权）、权限名在库级 DENY 11 项内的对象级（含列级，同表同权限多列记为表上一条）或 schema 级授权（class 为 `OBJECT_OR_COLUMN` 或 `SCHEMA`；TYPE、数据库主体等其他 class 一律违规），若本登录自己有同名 DENY——库级，或在该安全对象所属的同一 schema 上——即为被拒绝覆盖的授权，不算违规；登录看不见所属 schema 的对象不按 schema 级 DENY 推断覆盖，按违规（one-coordinator 2026-10-15 裁定方向 A，不推断「看不见即被覆盖」）。按权限名精确比对 `sys.database_permissions` 中本登录的 DENY 行（库级行读 (b) 同一查询，schema 级行读 (g) 同一查询），不用 `HAS_PERMS_BY_NAME`；授给 public 的 DENY 不算。CONTROL、TAKE OWNERSHIP、IMPERSONATE、VIEW DEFINITION 等不在 11 项内的权限与 `GRANT_WITH_GRANT_OPTION` 一律违规，CONTROL 不因其中某项被 DENY 而视为覆盖。本登录的 DENY 查不到（查询失败，或库级与 schema 级 DENY 一行都没有）时，可被覆盖的授权判定不了，不出定位行；没有可被覆盖的授权时不查。
- (f) 系统库本人用户（C-14 (f) 纳入基线：用户 2026-10-15「纳入基线」，one-coordinator 收紧口径，one-ops 在 RDS 核实；启动校验、运行期重检与本命令同一套，判定在 `SystemDatabaseUsers`）：
  - 允许：msdb（按编号 4 与名称认）中的本人用户只属于 `SQLAgentOperatorRole`、`SQLAgentReaderRole`、`SQLAgentUserRole` 的子集（三个名称显式列出、按名称精确匹配，不用通配；一个角色都没有也算子集）；rdscore（按名称认，编号须大于 4）中的本人用户不属于任何角色、`sys.database_permissions` 中只有库级 GRANT CONNECT。这是 RDS 为每个登录自动建的形状（one-ops 以取证账号核对三个正式账号：rdscore 本人用户无角色、只有 CONNECT；msdb 本人用户属上述三个角色）。
  - 违规（`SYSTEM_DATABASE_OWN_USER`，定位该库）：master、tempdb、model 中有本人用户（无论角色）；msdb 本人用户另属任一其他角色（如 `db_datareader`、`db_owner`、`DatabaseMailUserRole`）；rdscore 本人用户属任一角色，或有库级 GRANT CONNECT 以外的任何授权行。
  - msdb 本人用户的权限（opus 安全复审 H-1，one-coordinator 2026-10-15 裁定；判定在 `MsdbPrivileges`）：msdb 默认 TRUSTWORTHY ON、拥有者 sa，db_owner 级能力可借 EXECUTE AS 升到 sysadmin，只核对角色名等于不设防。msdb 本人用户的角色在允许形状内（或有叫不出名称的角色）时另做两条检查，任一越线即违规 `SYSTEM_DATABASE_OWN_USER_PRIVILEGE`（`count` 为库数，只有 msdb 故为 1，定位行只写 `database="msdb"`；角色已越线时只报 `SYSTEM_DATABASE_OWN_USER`，不重复报）：
    - 直接授权按类别允许清单（读 `msdb.sys.database_permissions` 中授给本人用户的行）：DATABASE 类只许 GRANT CONNECT；OBJECT_OR_COLUMN 类只许 GRANT SELECT、GRANT EXECUTE；任何 class 的 DENY 放行；其他 class（SCHEMA、DATABASE_PRINCIPAL、TYPE 等）的 GRANT 与所有带转授权（state `W`）一律违规。
    - 有效权限：以本登录身份在 msdb 内（`EXEC msdb.sys.sp_executesql`，使答案是本登录在 msdb 的用户而非本库的用户）查 `HAS_PERMS_BY_NAME('msdb', 'DATABASE', p)`，p 为 CONTROL、ALTER、ALTER ANY ROLE、ALTER ANY USER、ALTER ANY SCHEMA、CREATE PROCEDURE，任一为真即违规。这一条兜住直接授权看不到的来源：角色嵌套（如 SQLAgentUserRole 被加进 db_owner）、授给 SQLAgent 角色的授权、授给 public 的授权——它们都不改本人用户自己的授权行与成员关系。
    - 两条都要：`GRANT IMPERSONATE ON USER::dbo` 与 `GRANT EXECUTE ... WITH GRANT OPTION` 不使上述六项有效权限中任何一项为真（验收夹具 `msdbCounterexampleReallyHoldsItsPrivilegeAndUndoRestoresTheBaseline` 在容器实测），只能靠直接授权类别清单拦下；角色嵌套、授给角色或 public 的授权不出现在本人用户的授权行里，只能靠有效权限拦下。
    - 不误判：one-ops 在 RDS 实测正式账号的 34 条平台默认授权——直接授给本人用户的 DATABASE CONNECT G 1 条、对象级 SELECT G 31 条、SELECT D 1 条、EXECUTE G 1 条都在类别清单内；其余经 SQLAgentUserRole、SQLAgentOperatorRole 继承，是对象级授权，不使六项库级有效权限为真。验收以 msdb 的 backupset、backupfile、backupmediaset 三个 SELECT、sp_help_category 一个 EXECUTE、restorehistory 一个 DENY SELECT 同形覆盖（`oneOpsShapedMsdbGrantsStillPass`、`oneOpsShapedMsdbGrantsDoNotRefuseStartup`）。容器（SQL Server 2022）与 RDS（SQL Server 2016）上三个 SQLAgent 角色的默认授权可能不同，六项有效权限在正式账号上全假这一点留 one-ops 以真实登录跑本命令实测确认。
    - 判定不了（不放过）：直接授权或有效权限查询失败为 `CATALOG_QUERY_FAILED`（带错误号与 SQLState）；直接授权为空为 `EMPTY_ANSWER`（真实本人用户总有 CONNECT）；某项有效权限答 NULL 或缺答为 `NO_ANSWER`。一条确认越线时违规优先。
    - 拒绝原因、ALERT、日志只写「msdb 本人用户的权限超出平台默认」，不含权限名与对象名。
  - msdb 令牌范围的权限（opus 复核 H-1′，one-coordinator 2026-10-16 裁定「同意现在补」；判定仍在 `MsdbPrivileges`，原因码不增）：上面六项有效权限只覆盖数据库级的几项，授给 SQLAgent 角色、嵌套角色、public、guest 的间接授权中，数据库级 TAKE OWNERSHIP、ALTER ANY DATABASE DDL TRIGGER、CREATE FUNCTION / ASSEMBLY / TYPE / VIEW / TABLE，用户级 IMPERSONATE ON USER::dbo，架构级 ALTER / CONTROL ON SCHEMA::dbo，对象级 ALTER 某个 dbo 存储过程都使六项为假，拦不住。在上面两条（保留，六项仍是兜底）之外另加：
    - 令牌范围扫描：以本登录身份经 `msdb.sys.sp_executesql`（上下文切换与常量拼接同上，无外部输入）读 `sys.database_permissions WHERE grantee_principal_id IN (SELECT principal_id FROM sys.user_token)`，对令牌内所有主体（本人用户、三个 SQLAgent 角色、嵌套角色、public；只经 guest 进入时是 guest 与 public）的授权套同一份类别允许清单：DATABASE 只许 GRANT CONNECT；OBJECT_OR_COLUMN 只许 GRANT SELECT、GRANT EXECUTE；任何 class 的 DENY 放行；其他 class 的 GRANT 与所有 state `W` 一律违规。只对 public 另放行 SQL Server 2016 起默认授给它的库级 `VIEW ANY COLUMN MASTER KEY DEFINITION`、`VIEW ANY COLUMN ENCRYPTION KEY DEFINITION`（按 grantee 编号 0 认，授给其他主体仍违规）。查询只取「是否 public」、class、state、权限名四列，不取主体名与对象名；字符串列带 `COLLATE DATABASE_DEFAULT`（msdb 目录列 `Latin1_General_CI_AS_KS_WS` 与服务器排序规则可能不同，验收夹具已踩到冲突）。
    - IMPERSONATE：同一次有效权限查询另问 `HAS_PERMS_BY_NAME(N'dbo', N'USER', N'IMPERSONATE')`，为真即违规（不论来自本人用户、角色还是 public）。
    - guest：`sys.user_token` 只有本登录进 msdb 用的身份。有本人用户时令牌里没有 guest，授给 guest 的权限到不了本登录，不算违规；没有本人用户、只经 guest 进入（msdb 默认启用 guest）时，msdb 可进（`HAS_DBACCESS` 为 1）就照跑令牌范围扫描与有效权限检查（此前只在有本人用户时检查），授给 guest 的越线授权使 (f) 违规，定位行仍只 `database="msdb"`，没有 msdb 的 `ownUser` 信息行，也不因「无本人用户」违规。本人用户的角色读不到时同样照跑，确认越线即违规优先。
    - 不误判：基线账号（本人用户加三个 SQLAgent 角色）、one-ops 样本形状、SQL Server 默认授给 public 的上述两条库级授权与 public、SQLAgent 角色的默认对象级 GRANT SELECT / EXECUTE（SQL Server 2022 容器实测：public 约 249 条 SELECT、15 条 EXECUTE；SQLAgentUserRole 约 47 条 EXECUTE、3 条 SELECT；SQLAgentOperatorRole 5 条 EXECUTE、4 条 SELECT；没有其他 class）都在清单内。RDS（SQL Server 2016）上 public 与 SQLAgent 角色的默认授权是否同样只有这些 class，留 one-ops 以真实登录跑本命令确认。
    - 空列表口径统一：msdb 本人用户的直接授权、令牌范围扫描、rdscore 本人用户的授权返回空列表一律 `EMPTY_ANSWER`（判定不了，不按通过也不按违规；此前 rdscore 空列表按允许处理）；令牌范围扫描答 NULL 或某行有列缺答为 `NO_ANSWER`；查询失败 `CATALOG_QUERY_FAILED`；违规与判定不了同时出现时违规优先。
    - 令牌角色（opus 复审 H-A，one-coordinator 2026-10-16；判定仍在 `MsdbPrivileges`，原因码不增）：db_ssisadmin、dc_admin（微软文档称可提权到 sysadmin）、DatabaseMailUserRole 等角色的能力全是对象级 EXECUTE / SELECT，落在上面的类别允许清单内，经 `ALTER ROLE <r> ADD MEMBER SQLAgentUserRole`（有本人用户）或 `ADD MEMBER guest`（只经 guest 进入）嵌进令牌后，授权扫描与六项有效权限都看不到。故改按成员关系：经 `msdb.sys.sp_executesql` 读 `sys.user_token WHERE type = 'ROLE'` 的名称（`COLLATE DATABASE_DEFAULT`），只许 `public`、`SQLAgentUserRole`、`SQLAgentReaderRole`、`SQLAgentOperatorRole`（按名称精确匹配，大小写与空格不同也违规），其余角色（含 db_owner 等固定角色、自建无授权角色）一律违规 `SYSTEM_DATABASE_OWN_USER_PRIVILEGE`；名称只在进程内比较，problem、定位行、日志都不出。读不到 `CATALOG_QUERY_FAILED`、空列表（真实令牌至少有 public）`EMPTY_ANSWER`、答 NULL 或某名称缺答 `NO_ANSWER`，违规优先。本人用户存在与只经 guest 进入两种形态都查。
    - 令牌拥有关系（opus 复审 M-A）：有效权限看不到「拥有」带来的潜伏提权（管理员误配 `CREATE ROLE r AUTHORIZATION [账号]` 后账号自行加入 db_owner 级角色，或 schema、过程的所有权给了账号）。经 `msdb.sys.sp_executesql` 计数令牌内主体（`sys.user_token`）拥有的 schema（`sys.schemas.principal_id`）、对象（`sys.objects.principal_id`）、角色（`sys.database_principals.owning_principal_id`），大于 0 即违规，同一原因码；只取计数，不取名称。平台默认放行：guest 拥有 schema `guest`，三个 SQLAgent 角色各拥有同名 schema（只限这四个主体与各自同名 schema，不是「与拥有者同名一律放行」）。读不到、答 NULL 同上判定不了。
    - 其他：令牌范围与本人用户直接授权里的 DENY 在任何 class 都放行（DENY 不能提权，有人用它加固 msdb 不应使账号拒绝启动；复审 Low-1）；提示文字写「the login in msdb」，只经 guest 进入时也适用（Low-3）；rdscore 授权行有 null 列时过滤并记 `NO_ANSWER`，与 msdb 同做法（Low-4）。
    - 残余（未处理，PR 单列，待用户知情确认）：
      - H-2 SQL Agent 能力：`SQLAgentOperatorRole` 可启动、停止、禁用实例上全部本地作业并删除作业历史；作业以拥有者身份运行（可能是高权限账号）；`OWNS_AGENT_JOB` 只能在下一次重检（启动校验、运行期重检、核实命令）时事后发现账号自建的作业，查不出「启动别人的作业」；`msdb.dbo.sysproxylogin`（代理账号授权）未查。
      - 对象级 SELECT / EXECUTE 不限对象（风险中，下一次按对象名允许清单收紧）：令牌内任何主体对任何 msdb 对象的 SELECT、EXECUTE 都放行，含带 `EXECUTE AS OWNER` 的 dbo 过程（等于提权）与 `sp_send_dbmail`（数据外传），以及读 `sysjobsteps.command`、`sysmail_*`。令牌角色限定后，嵌套角色带来的这类能力已被拦下，令牌内任何主体（含 public、三个 SQLAgent 角色）任何新增的对象级 SELECT / EXECUTE 都放行，不论是否默认，包括带 `EXECUTE AS OWNER` 或已签名的 dbo 过程，以及读 `sysjobsteps.command`、`sysmail_*`。
      - 固定角色嵌套：db_datawriter、db_denydatareader 等固定角色的隐含权限不出现在 `sys.database_permissions`，也不在六项有效权限里；被嵌进令牌时由令牌角色检查拦下（它们不在四个名称内）。
    - RDS 实测待 one-ops（第 6 项，与上文既有各项并列）：用正式账号的真实登录在 msdb 内读 `sys.user_token WHERE type = 'ROLE'` 的名称集合与大小写（只记是否恰为 `public` 加 SQLAgent 三个角色的子集，不抄名称）。
    - RDS 实测待 one-ops（第 7 项）：令牌内主体拥有的 schema、角色、对象、证书、密钥、程序集、类型、XML schema 集合的类别与计数（只出类别与计数，不抄名称），确认合计为 0；并确认 SQL Server 2016 上 guest 与 SQLAgent 角色拥有同名 schema 这一前提，且这四个 schema 内没有 `principal_id` 为 NULL 的对象。
    - 拥有关系口径补充（opus 复核 M-1）：除上述 schema、对象、角色外，计数还含 `sys.certificates`、`sys.asymmetric_keys`、`sys.symmetric_keys`、`sys.assemblies`、`sys.xml_schema_collections` 的 `principal_id` 在令牌内的个数，`sys.types` 中 `is_user_defined = 1` 且 `principal_id` 在令牌内的个数，以及放行的四个平台 schema 内 `principal_id` 为 NULL 的对象个数（这类对象隐含归 schema 拥有者，默认应为 0）；仍只取计数。
  - 角色名以本登录自己的身份经三段名读 `<库>.sys.database_role_members`、`<库>.sys.database_principals`（`sid = SUSER_SID()`），rdscore 的授权读 `rdscore.sys.database_permissions`；查询失败为判定不了（rdscore 授权读不到不按允许处理，opus 复审 M-1）。rdscore 本人用户有叫不出名称的角色即违规（rdscore 不许有任何角色，复审 L-3）。msdb 本人用户有本登录叫不出名称的角色时判定不了（`NO_ANSWER`），不按通过处理。
  - Agent 作业：本登录拥有的作业数必须为 0，否则违规 `OWNS_AGENT_JOB`（`count` 为作业个数，不出定位行，任何输出、拒绝原因、ALERT 与日志都不含作业名）。读法 `SELECT COUNT(*) FROM msdb.dbo.sysjobs_view WHERE owner_sid = SUSER_SID()`：SQL Server 2022 容器中 `msdb.dbo.sysjobs` 只有 TargetServersRole 能读，三个 SQLAgent 角色的成员读它报 229，`sysjobs_view` 的 SELECT 授给 SQLAgentUserRole（另两个角色是它的成员）且对作业所有者列出自己的作业；RDS 上两者都能读，统一用 `sysjobs_view`。读不到（查询失败、229）为判定不了，带错误号与 SQLState；msdb 本人用户一个角色都没有时读 `sysjobs_view` 会报 229，按判定不了处理（已裁定可以）。本登录在 msdb 没有本人用户、只经 guest 进入时照读作业数（one-coordinator 裁定 B，CLAUDE.md 第 6 条：缺失或无效输入失败并告警，不静默跳过）：guest 读不了 `sysjobs_view`（229），作业属主无法判定，(f) 判定不了（`CATALOG_QUERY_FAILED`，带错误号与 SQLState），运维据此处理（核实该账号为何在 msdb 没有本人用户；RDS 给每个登录建 msdb 本人用户，正常不会出现）；令牌范围扫描与有效权限检查照做，确认越线时违规优先。msdb 本人用户读不到（查询失败）或 msdb 不可进时照读作业数。只在 (f) 的本库与可进库查询成功后才读作业，所以连不上时 (f) 的计数仍为 2。
  - 启动校验与运行期重检：允许的本人用户不拒绝、不告警；违规照 (f) 拒绝或停连接。拒绝原因、ALERT、日志沿用现有 problem 文本口径（可含库名），不含作业名与其他对象名；角色名清单只由本命令的 `ownUser` 信息行输出。
- 退出码：0 通过；2 用法错误（缺少、重复或未知的连接名，非 `--名=值` 形式的参数，参数名含 `password` / `secret` / `credential`（不分大小写），命令行出现连接变量名或 `--spring.application.json`，进程环境缺变量，URL 不是 SQL Server，与 `migrate` 或 `identity-operations` 档案同时激活，日志系统不是应用自带的 Logback——参数值一律不回显）；3 违规（含与判定不了同在）；4 判定不了。1 只表示进程本身失败（与 Spring Boot 启动失败一样），不是任何结果。
- 不输出：口令（任何连接的）、登录名、连接串（探针 URL 里有 `trustStorePassword`）、数据库消息原文、启动校验的 problem 文本、数据；名称只出现在上述定位行与 `ownUser` 信息行，作业名不输出。命令自己的行直接写标准输出 / 标准错误，不经日志；日志在核实之前全部关闭：根 logger、`logging.level.*` 配置过的每个 logger（如 `--logging.level.com.zaxxer.hikari.HikariConfig=DEBUG` 也被关掉）与已有的全部 logger 都设为 OFF，关不掉即拒绝运行。`-Dorg.springframework.boot.logging.LoggingSystem=none` 时 Logback 默认根级 DEBUG、无法由应用关掉，命令拒绝运行（退出 2）。
- 不启动 Web、不迁移、不起作业：命令在 Spring 准备好环境（档案、配置文件都已读入）之后、建立任何应用上下文之前运行并结束进程（与 `migrate` 模式同一做法，`spring.factories` 登记的环境就绪监听器，排在 `migrate` 之前），所以不建服务库数据源、不跑 Flyway、不起 Tomcat 与管理端口、不起定时任务、调度、事件发布作业、流程引擎与异步执行器，也不读 OSS、企业微信、单点登录、应急密钥与服务库的设置。one.env 里的 `SERVER_PORT`、`MANAGEMENT_SERVER_PORT` 等环境变量因此不起作用；命令另把 `spring.main.web-application-type=none` 放在最高优先级的属性源（高于 `SPRING_MAIN_WEB_APPLICATION_TYPE` 等环境变量），档案文件 `application-read-only-verification.properties` 再写一遍 web=none、Flyway 与作业关闭，作为第二道。
- 发布包：命令与档案都在 jar 内，签名发布包不需要另登记文件；发布脚本与签名流程不变。
- 验收：`ReadOnlyVerificationCommandIT`（以打包好的 jar 起子进程，探针证明不监听端口、服务库无表无 schema）；契约见 `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/READONLY_VERIFY_ACCEPTANCE.md`。

## 5. dev 环境连接配置的取法

> **过渡说明（one-ops 补，2026-10-21）**：本节与 §3、§4 已按 ADR-0055 删去或改写 A8 / 老系统的运行时配置，写的是目标状态，**不等于代码已拆**。A 线按 ADR-0055 拆代码、发布新 rc 之前，现有发布包（含 rc.6）仍读取已删各行的变量：`FORCITIS_PLATFORM_IDENTITY_SSO_ENABLED` 无默认值、缺失则应用启动失败（dev 的 one.env 里设为 false）；`INTEGRATION_SEEYON_VIEWS_ENABLED`、`A8_DB_*` 与 A8 实例专属编号等，dev 当前仍为开启状态（见 OPS_CONTEXT）。所以：**不要从 dev 的 one.env 删这些变量**；拆代码的 rc 合入、dev 重建时按新 rc 的变量清单同步修改 one.env 与 `one.env.template`（该模板本次未改，另卡承接）。

- **唯一来源**：服务器上的 `/etc/forcitis-one/one.env`，权限 `0640 root:forcitis`，由 systemd 的 `EnvironmentFile` 加载。仓库、镜像、聊天、工单中都不出现这些值。
- **后续计划**：迁到阿里云 KMS 凭据管家，启动时拉取，one.env 只保留 KMS 引用。上线前完成。
- **PG 连接**：`SPRING_DATASOURCE_URL=jdbc:postgresql://<RDS 内网地址>:5432/forcitis_one_dev?sslmode=verify-full&sslrootcert=/etc/forcitis-one/rds-ca.pem&logServerErrorDetail=false`，`SPRING_DATASOURCE_USERNAME=one_dev_app`，口令单独一行。迁移连接用 `SPRING_FLYWAY_USER` / `SPRING_FLYWAY_PASSWORD`（one_dev_migrator），只在部署步骤注入，常驻进程不持有迁移凭据。
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
| 归档用 RAM 子账号的 AccessKey（只在不用 ECS RAM 角色时存在） | one.env / KMS | 不设到期 | 每 90 天：在 RAM 控制台新建第二把、写入 one.env、重启应用确认归档探针通过，再停用并删除旧的；RAM 角色（STS）无需轮换 |
| `FORCITIS_TICKET_KEY_<KID>` | 签名票据签发方密钥；签发方默认关闭，当前白名单为空 | 否 |
| `FORCITIS_TRUSTED_PROXIES` | 只信任 nginx（127.0.0.1） | 是 |
| `APPROVAL_DEADLINE_ENABLED` | 审批时限提醒与升级作业开关（默认 true） | 否 |
| `FORCITIS_JOBS_ENABLED` | 定时作业总开关，`true` 或 `false`，**没有默认值**（缺失或为空应用起不来，安装器拒绝安装）；值只写裸的小写 `true` / `false`（单引号括起也可），带双引号、尾随空格或大写的安装器都拒绝；所有实例中至少一个为 `true`（见 §9） | 是 |
| `EVENT_PUBLICATION_JOBS_ENABLED` | 事件发布重投与清理作业开关（默认 true，见 §9） | 否 |
| `FORCITIS_PLATFORM_INTEGRATION_WECOM_DELIVERY_DUPLICATE_TASK_ERROR_CODES` | 企业微信「task_id 已用过」错误码（B-9 核实后设置） | 启用时 |
| `SERVER_ADDRESS`、`SERVER_PORT`、`MANAGEMENT_SERVER_ADDRESS`、`MANAGEMENT_SERVER_PORT`、`MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE` | E-7 的监听地址与管理端点（代码未设默认） | 是 |
| `WECOM_ENABLED` | 企业微信网关与出站投递总开关（默认 false） | 否 |
| `WECOM_BASE_URL` | 企业微信 API 地址（默认 `https://qyapi.weixin.qq.com`） | 否 |
| `WECOM_CORP_ID`、`WECOM_AGENT_ID` | 新企业微信应用的**新值**（老应用的值已在老仓历史中泄露，I-8） | 启用时 |
| `WECOM_APP_SECRET` | 同上，应用 secret，只写入 one.env 或 KMS | 启用时 |
| `WECOM_RESTRICT_RECIPIENTS` | 是否只向白名单收件人发送（默认 true）；**生产必须为 false** | 是 |
| `WECOM_RECIPIENT_ALLOWLIST` | 收件人白名单（`WECOM_RESTRICT_RECIPIENTS=true` 时生效，dev 联调用） | 否 |
| `SPRING_PROFILES_ACTIVE` | `dev` 或 `prod`；决定数据库连接的 TLS 要求（§5.1） | 是 |
| 生产附加 | `springdoc.api-docs.enabled=false` | 生产 |
| 前端构建时（CI 变量，非机密） | `VITE_WECOM_CORP_ID`、`VITE_WECOM_AGENT_ID`（PC）；`TARO_APP_WECOM_CORP_ID`（H5） | 构建时 |

模板：`tools/ops/ecs/files/one.env.template`（只有变量名与非机密默认值）。

### 5.1 数据库连接的 TLS 与 CA（用户决定 N-3）

- **应用侧的硬规则**（`DatabaseTransportConfiguration`，在任何连接建立之前检查）：
  - 激活 `prod` 时，主库、迁移连接（`spring.flyway.url`）的 JDBC URL 都必须验证服务端证书和主机名，且 CA 文件可读；否则**启动失败**，日志列出是哪条连接、缺什么。
  - 激活 `dev` 时至少加密：PostgreSQL `sslmode` 为 `require`、`verify-ca` 或 `verify-full`；SQL Server `encrypt=true`。
  - 没有激活这两个 profile（本地、测试）不检查。
  - 常驻服务不建立 A8 / 老系统视图及老待办连接；受控退役用途仅在专用进程中检查本次实际使用的连接，不影响常驻服务启动。
- **PostgreSQL（RDS）**：URL 带 `sslmode=verify-full&sslrootcert=/etc/forcitis-one/rds-ca.pem`。`prod` 不接受 `require`。
- **SQL Server（A8、老系统，仅限 §4 受控退役用途）**：驱动没有 PEM 形式的 CA 路径，URL 用 `encrypt=true;trustServerCertificate=false;trustStore=/etc/forcitis-one/sqlserver-ca.p12;trustStorePassword=<取自退役用途专用环境文件>`。读取窗口结束后回收该通道配置，不注入常驻服务；`prod` 不接受 `trustServerCertificate=true`、`encrypt=false` 或缺 `trustStore`。
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
| A8、老系统受控退役用途只读账号的口令（账号退出条件见 §4） | 退役用途专用受保护环境文件 / KMS，不进入常驻 one.env | 使用期间不设到期（SQL Server 登录 `CHECK_EXPIRATION = OFF`），账号按条件退出 | 使用期间仍每 90 天由对方 DBA 轮换并更新专用进程配置；用途完成后回收通道，达到 §4 条件后另卡撤账号，不因轮换周期延长使用 |
| `WECOM_APP_SECRET` | one.env / KMS | 以企业微信后台为准 | 每 90 天在企业微信后台重置并更新 |
| `EMERGENCY_TOTP_ENCRYPTION_KEY` | one.env / KMS | 不到期 | 不随 90 天轮换：轮换要重加密全部应急管理员的 TOTP 密钥，单独写手册后才能做 |
| `FORCITIS_TICKET_KEY_<KID>` | one.env / KMS | 签发方默认关闭 | 启用某个签发方时，按该签发方的约定轮换（`kid` 并存期内可平滑换） |

- 轮换到点由运维日历提醒；任何一项逾期 7 天按 P2 报告 psa-coordinator。
- 口令只在服务器受保护文件或 KMS 中出现，轮换过程不经聊天、工单和仓库。

### 5.3 数据保留作业与归档去向（数据打分卡 T3；用户决定 2026-10-15：用国内 OSS 归档存储）

| 数据 | 在线保留 | 到期后 | 作业（ShedLock 名） | 配置项（缺失则启动失败） |
|---|---|---|---|---|
| `cost_timesheet.consumed_events` | 90 天 | 删除 | `cost.timesheet.consumed.events.purge`，每日 04:20 | `forcitis.cost.timesheet.consumed-events.keep-period`、`.cron` |
| `platform_integration.outbound_deliveries` | 成功或跳过 90 天、失败 1 年 | 删除 | `platform.integration.outbound.purge`，每日 04:10 | `forcitis.platform.integration.outbound-purge.*` |
| 引擎历史 `ACT_HI_*`（已结束的流程实例） | 3 年 | 先写入归档库，库确认后才从引擎删除 | `platform.approval.process.archive`，每日 03:40 | `forcitis.platform.approval.process-archive.*` |
| `platform_audit.audit_entries` | 3 年 | 先写入归档库；**在线行暂不删除**（表有触发器拒绝 DELETE，删除要单独决定） | `platform.audit.entry.archive`，每日 02:00 | `forcitis.platform.audit.archive.*` |

- **归档库**：阿里云 OSS，**与 ECS 同地域的国内地域**（应用只接受国内地域，拒绝 `eu-central-1` 等；归档含个人信息，不得放到法兰克福），存储类型 Archive，开启 WORM 合规保留（锁定天数等于 `ARCHIVE_STORE_WORM_LOCK_DAYS`）。bucket 和 RAM 子账号由用户创建，名字、账号、密钥都不进仓库。RAM 子账号只授予该 bucket 前缀下的写入（`oss:PutObject`、分片上传的发起与中止）和读元数据，不授予删除。
- **写入方式**：每批一个不可变对象，键为 `<前缀>/<类别>/<年>/<类别>-<UTC 时间>-<SHA-256 前 12 位>.ndjson.gz`；写入前算 SHA-256，上传带 `Content-MD5`、禁止覆盖、归档存储类型，SHA-256 同时写进对象元数据；上传后读回元数据，哈希或大小不符则整批失败，引擎和在线行都不动。多节点下由 ShedLock 保证同一时刻只有一个节点归档，对象名也各不相同。
- **本机**：数据盘 `ARCHIVE_STORE_LOCAL_DIRECTORY/<类别>/latest.ndjson.gz` 只留最近一份（目录属主 `forcitis`，权限 `0750`，systemd 单元的 `ReadWritePaths` 要加上它）。
- **保留与到期（单一 bucket）**：流程归档与审计归档共用同一个 bucket，用前缀区分类别，WORM 合规保留 **2557 天（约 7 年）**：依据是在线 3 年加归档 7 年，从数据产生起算满 10 年（用户已定）；以后需要时还可以延长，只增不减。天数是配置项 `ARCHIVE_STORE_WORM_LOCK_DAYS`（`forcitis.platform.integration.archive-store.worm-lock-days`，随发行物默认 2557），总保留期 10 年是另一个配置项 `total-keep-years`；每类数据的「在线保留期 + WORM 天数」必须等于总保留期，不一致则启动失败（`ArchiveVault.requireTotalKeepPeriod`，并有守护测试 `ArchiveKeepPeriodTest` 核对随发行物的配置）。建桶时在控制台按 2557 天锁定。
- **生命周期**：bucket 配置生命周期规则，对象保留期满（创建后满 WORM 天数）后过期删除；应用不删除归档。过账类单据按 30 年保存，存放另行安排，见已删除的 `docs/modernization/phase1a/DEFERRED.md`（`git show b09f58b0f^:docs/modernization/phase1a/DEFERRED.md`；用户已定，主分支提交 7d5b9d3），不在这个 bucket 的 10 年规则里。
- **凭证与 RAM 策略**：推荐给 ECS 绑定 RAM 角色（STS 临时凭证），应用走默认凭证链，不配置 AccessKey；不用角色时才配置 `ARCHIVE_STORE_ACCESS_KEY_ID` / `_SECRET`（见 §5.2 的轮换）。策略（草稿，由用户在 RAM 控制台建）：Allow `oss:PutObject`、`oss:InitiateMultipartUpload`、`oss:AbortMultipartUpload`、`oss:GetObjectMetadata`（`oss:GetObject` 仅排障需要，默认不给）、`oss:GetBucketInfo`、`oss:GetBucketWorm`、`oss:GetBucketLocation`，资源限定在该 bucket 及 `<前缀>/*`；**显式 Deny** `oss:DeleteObject*`（含 `DeleteObject`、`DeleteMultipleObjects`、`DeleteObjectVersion`）于同一资源，显式 Deny 优先于任何 Allow。
- **探针**：应用启动时和每天一次，检查本机目录可写、bucket 在配置的地域、WORM 已锁定且天数等于配置值、有写入权限（发起并中止一次分片上传，不在 WORM 桶里留对象）。失败不阻止启动，记 `ALERT` 日志并计数 `forcitis.platform.approval.archive.probe.failures`、`forcitis.platform.audit.archive.probe.failures`；归档作业本身失败记 `ALERT` 并计数 `forcitis.platform.scheduling.job.failures{job=…}`（所有带锁作业共用）。生产缺少上述配置项则**启动失败**。
- **归档内容**：流程实例的 ID、流程定义键、版本与部署 ID、业务键、起止时间、发起人、删除原因，任务（办理人、认领、办结、到期时间）、活动、身份关联（候选人、转交、升级追加的人），以及白名单内的变量（`backend/architecture/archive-variables.txt`：`contentDigest`、`routed_*`，任务级 `systemComplete`、`advisory`、`assignedAt`），不含其他流程变量。任务级的结论与理由由审批的只追加办理记录表负责。
- 一个实例写入归档后引擎删除失败，则跳过、记 `ALERT`、计数 `forcitis.platform.approval.archive.skipped`，并记入只追加表 `platform_approval.process_archive_skips`，之后不再重复归档；仍在引擎里的每次运行结束时再告警一次，直到人工处理。
- 审计 3 年归档按 id 前缀成批，进度记在 `platform_audit.audit_archive_batches`。

### 5.4 审计在线月分区的预建与删除（ADR-0048、O-8；DATA-REPAIR-timesheet-audit TB-07）

- **预建**：`platform_audit.audit_entries` 按 UTC 月分区（`audit_entries_pYYYYMM`），没有默认分区，某月没有分区时该月的审计写入会失败。应用的 `AuditPartitionJob`（ShedLock `platform.audit.entry.partitions`）在启动时和每天 01:20 把当月起、再往后 `forcitis.platform.audit.partitions.months-ahead`（3）个月的分区补齐；失败记 `ALERT platform.audit …` 并计数 `forcitis.platform.audit.partitions.failures`。基线本身建 2026-10 起 6 个月。
- **删除已归档月份**（在线 3 年之后；应用不做，只由维护角色手工执行）：
  1. 确认该月已全部归档：`AuditArchiveJob` 最近一次运行无 `ALERT platform.audit the audit trail up to entry …`，`audit_archive_batches` 里该月的批次已写入归档库。
  2. 用 `one_<环境>_audit_maintainer` 连接（口令由运维持有，不入仓库），执行：`SELECT platform_audit.drop_archived_audit_entry_partition(DATE 'YYYY-MM-01');`（月初，UTC 月）。（低峰执行：删除会锁住审计表全部月分区，最长阻塞写入 5 秒（`lock_timeout`），超时即失败，重试即可）
  3. 函数自己校验：分区存在；分区里没有排在归档位置之后的行；分区行数等于该月归档批次的条目数之和。任一不符报错、不删，返回值为删除的行数；不符时不要绕过，先查清归档缺口。
  4. 删除后无需其他操作：对账只比较仍在线的月份。运行角色（`one_<环境>_app`）没有此函数的执行权，也不能 DETACH 或 DROP。

## 6. 应急管理员与首批持有人开通（运维命令，已实现；执行需用户确认）

- 不提供 HTTP 开通接口（设计如此）。入口是同一个 jar 以档案 `identity-operations` 启动，**一次只做一个命令，做完进程结束**：

  > **待改（F-12）**：`--spring.profiles.active=identity-operations` 会顶掉环境档案（dev / prod），跳过 `VERIFIED_SERVER` 等环境校验。one-1a-next 在 F-12 改为追加档案（include）写法，届时本节与 C-5 的命令形式同步修改；在此之前不在生产使用。

  ```
  java -jar forcitis-one.jar --spring.profiles.active=identity-operations --operation=<命令> --employee-id=<员工 ID> \
       [--login-name=<应急账号名>] [--role-code=<角色码>] --operator=<操作人员工 ID> --witness=<见证人员工 ID>
  ```

  - 人员以 `--employee-id`（员工 ID，小写规范 uuid 文本）指定：命令按员工取得其账号，员工在职而尚无账号时当场开通（ADR-0033 第 3 项第 6 条）；格式不对（整数、负数、大写 uuid 等）为用法错误、退出码 2，员工不存在时退出码 1，都不写任何数据。原 `--user-id` 已删除。
  - 员工 ID 从只读查询命令得到（R-1b P3b，ADR-0033 第 3 项第 6 条；RULING-P3B-D8、P3B-7）：同一个 jar 以档案 `integration-operations` 启动，按企业微信 userid 列出匹配的全部员工 ID 与用户 ID（同名两人都列出），**只读，不写任何数据、不记审计、不开通账号**：
    ```
    java -jar forcitis-one.jar --spring.profiles.active=integration-operations --operation=find-accounts-by-external-subject \
         --provider-kind=WECOM_USER_ID --external-subject=<企业微信 userid>
    ```
    每个匹配输出一行 `employee-id=<uuid> user-id=<uuid 或 (no account)>`；退出码 0 找到、1 无人匹配、2 用法错误。不按员工编号查（员工编号不作键，ADR-0033 第 6 项）。端口与 F-12 的注意事项同上。
  - 命令：`register-emergency-admin`（需 `--login-name`）、`reset-emergency-admin-credentials`、`disable-emergency-admin`、`enable-emergency-admin`、`grant-role`（需 `--role-code`，用于首批超级管理员、业务管理员、组织数据管理员）。
  - **双人在场**：`--operator` 与 `--witness` 必填，各为员工 ID（小写规范 uuid 文本），且必须是两个不同的、已存在的员工（以本地人事记录为准，不要求在职）；两人的员工 ID 记入审计 `detail`（`operatorId=`、`witnessId=`，姓名不入审计，读时再解析；ADR-0043 U-7b）；缺一、不是员工 ID、员工不存在或相同则命令拒绝（退出码 1，错误码 `PLATFORM_IDENTITY_OPERATION_WITNESS_REQUIRED`），什么也不做。本地人事记录读取失败（不可用）时命令同样失败、退出码非 0，什么也不做、不记审计。原先填姓名的写法作废。
  - 口令与 TOTP 密钥由命令**生成**，只在这一次输出里显示（口令、base32 密钥、认证器链接），库里只存口令的 Argon2id 哈希与加密后的密钥；不要把输出复制进文件或聊天。口令强度与密钥长度由代码保证，不经命令行参数传入。
  - 档案关闭所有定时任务（`forcitis.platform.scheduling.jobs-enabled=false` 及各任务自己的开关；总开关生效的前提是 Spring Modulith 的 `MomentsAutoConfiguration` 已被排除，#228 起，见 §9），Web 部分只监听本机随机端口，几秒钟后随进程结束；命令发布的事件若监听器没来得及处理，仍留在事件发布登记表里，由常驻应用的重投作业接手（§9）。
  - 退出码：0 成功（含「已是该状态，未改变」）；1 被拒绝（错误码在标准错误输出）；2 用法错误。
  - 注册后若可用应急账号少于 2 个，命令输出 `WARNING`；必须有至少 2 个、绑定不同的超级管理员持有人。
- 应急账号只能从内网段登录，失败 5 次锁 15 分钟；检查与计数在凭据验证之前一步完成，并发猜测也只有 5 次机会。应急登录直接进入管理模式，1 小时后会话结束。
- 应急登录的在职校验（ADR-0055 决定 3.1）：按同事务的本地人事记录校验绑定员工；本地记录缺失或无效须拒绝并告警，离职人员拒绝登录。不再依赖 A8 同步快照、同步周期或外部故障时放行的分支，dev 同样不因关闭旧同步而放行。
- 每日核对：任务 `platform.identity.emergency.admin.check`（ShedLock，每天一次，`forcitis.platform.identity.emergency.check.cron`）核对应急账号数量与可用性，少于 2 个可用即告警；总开关 `forcitis.platform.scheduling.jobs-enabled`（默认 true），本任务另有 `forcitis.platform.identity.emergency.check.enabled`。
- **告警走 C-7 的日志告警规则**：本节与管理模式的告警都是一行 `ALERT platform.identity …` 的日志（结构化日志，见 §8），由 SLS 按 `ALERT` 关键字匹配后发往 A-5 指定的接收人；应用本身不直接发消息。涉及的告警：进入管理模式、退出或自然到期、应急登录、结束超级管理员或安全审计员的会话、应急账号因离职被停用、本地离职放行（日志规则随离职命令实施卡核对，不再匹配同步放行日志）。
- 管理员离职按本地离职命令独立放行（ADR-0055 决定 3）：可用超级管理员少于 2 名仍暂停；留任超级管理员在管理模式、或安全审计员在普通会话填理由，只放行该条离职，不重跑同步，其余人事变更不受阻。旧同步放行接口撤销，新接口与日志按实施卡核对。**恢复路径**：可先用本节的 `grant-role --role-code=super_admin` 给留任人员授予超级管理员（双人在场、写审计）；放行后为 0 名只能走双人在场、写审计的运维命令，为 1 名须告警。已生效的授予关闭与审计保留，不靠恢复同步改写历史。
- 开通人和被开通人都记入审计（`platform.identity.operations.*`）。
- **放行待放行离职的运维命令 `release-separation-hold`**（#877，ADR-0068 第 2 条第 4 项；组织 workforce 自己的入口，档案 `workforce-operations`，不用 `identity-operations`）：只在放行后可用超级管理员剩 0 名时执行，剩 1 名或更多时拒绝并提示改走界面单条放行。同一个 jar，一次一个命令，做完进程结束，端口与 F-12 的注意事项同上：
  ```
  java -jar forcitis-one.jar --spring.profiles.include=workforce-operations --operation=release-separation-hold \
       --separation-hold-id=<暂停记录 ID> --reason=<原因，1 至 500 字> --operator=<操作人员工 ID> --witness=<见证人员工 ID>
  ```
  - **双人在场且都须留任**：`--operator` 与 `--witness` 必填，为两个不同的、已存在的员工，且都须本地在职、不在待放行名单中、不在停薪留职；见证人始终必填，没有单人模式。违反则拒绝（退出码 1），库内无任何变化。
  - 效果与界面放行相同：离职自原生效日起生效、不重判；暂停记录记为 `OPERATIONS_COMMAND` 放行、放行账号为空，状态转换与事件的操作者是系统主体 `organization.workforce.operations`；审计 `organization.workforce.separation-hold.released` 一条，`detail` 含原因、途径、`operatorId=`、`witnessId=`、命令 ID、暂停记录 ID 与剩余超管数 0。先取与离职、界面放行、撤回同一把咨询锁。
  - 退出码：0 已放行；1 被拒绝（含当月审计分区不存在，需先启动常驻应用建分区）；2 用法错误（缺参数、未知选项、ID 格式不对）。
- **一次性迁入命令 `migrate-initial-versions`**（#960，one-design 数据设计签发于 #960 评论；档案同 `workforce-operations`）：把本库快照里还没有任何员工版本的人，按当前状态写成 MIGRATION 版本，让所有读取方都读版本表。不用 Flyway：必须可重跑且重跑不写行，且生产只走发布闸门，不在部署时静默执行。
  ```
  java -jar forcitis-one.jar --spring.profiles.include=workforce-operations --operation=migrate-initial-versions \
       --operator=<操作人员工 ID> --witness=<见证人员工 ID>
  ```
  - **运行时机**：旧组织同步已删除（#934），快照不再被同步改写，命令随时可运行。操作人与见证人必填，为两个不同的已存在员工（不要求留任）。
  - 每人写什么：没有任何员工版本的人（任意渠道、任意日期）写一条（已离职且离职日晚于左边界时两条）；左边界取最早未作废任职版本的生效日，没有则取运行当天；在职者雇佣阶段一律记正式（用户答复），入职日未知（左截断），非停薪留职，收费标记取快照；离职日取 `separated_at` 的业务日期，是快照观察到离职的日期，不是人事离职日。有快照任职但无任职版本的人补一条任职版本；已有版本的人（含命令版本）一行不动，与快照不一致的任职只计数不覆盖。
  - 副作用：一个事务、逐人先锁员工行；不发领域事件、不发通知、不改授予；审计 `organization.workforce.initial-versions.migrated` 一条，只含操作人、见证人 ID 与计数（新增在职、离职人数，补写任职版本数，因已有版本跳过数，任职与快照不一致数）。
  - 退出码：0 完成（重跑也是 0，计数为 0）；1 被拒绝（缺见证人、两人相同或不是员工、当月审计分区不存在）；2 用法错误。dev 只走正常路径跑；生产由发布闸门执行，不在本卡内运行。
- **#960 迁入命令的运维步骤（一次性，由运维在发布闸门放行后执行，开发会话不执行）**：
  1. 发布含 #934 的版本，确认旧组织同步已不存在（旧同步的作业开关变量已不再有作用，可从 one.env 去掉）。
  2. 运行 `migrate-initial-versions`。
  3. 核对命令输出的计数与审计 `organization.workforce.initial-versions.migrated`；`assignmentsDifferingFromSnapshot` 不为 0 时交人事核对，不要重跑覆盖。命令可重跑且重跑不写行。
- 剧本见 `runbooks/first-admins.md`（待按本节对齐）。

## 7. 首次部署步骤（dev）

1. CI 在 main 上构建 jar 和前端 dist，产物附带版本号与 SHA256。（**CI 目前只做 verify，不产出发布包**；Maven 产物名为 `forcitis-one-<版本>.jar`，上传时改名为 `forcitis-one.jar` 以符合 E-6。）
2. 经云助手上传到 `/data/forcitis-one/releases/<ver>/`，校验 SHA256。
3. 迁移（只迁移后退出）：用迁移账号启动一次，带 profile `migrate`，进程在建立应用上下文之前直接执行 Flyway 迁移并退出，不启动 web、作业、流程引擎和事件监听。迁移成功退出码 0，输出 `MIGRATE-ONLY OK`；失败退出码 1，输出 `MIGRATE-ONLY FAILED: <原因>`，部署在这一步停下。
   - 命令：`SPRING_PROFILES_ACTIVE=migrate,dev java -jar forcitis-one.jar`（生产用 `migrate,prod`），环境变量只给迁移账号：`SPRING_FLYWAY_URL`（缺省取 `SPRING_DATASOURCE_URL`）、`SPRING_FLYWAY_USER`、`SPRING_FLYWAY_PASSWORD`；schema 与历史表的设置见 §2。连接的 TLS 要求同 §5.1（`dev` / `prod` 同时写上才会检查）。
   - 首次还要以 `FLOWABLE_SCHEMA_UPDATE=true` 启动常驻服务一次建 `ACT_*`（见 §2）。
   - **常驻服务不自己迁移**：生产 profile 的 `application-prod.properties` 已设 `spring.flyway.enabled=false`，环境里也写 `SPRING_FLYWAY_ENABLED=false`；常驻进程不持有迁移凭据。
4. 切换 `current` 软链，`systemctl restart forcitis-one`，检查健康。
5. 冒烟：匿名访问返回 401；企业微信登录；`/me` 正常；确认常驻服务无老系统 / A8 生产连接、不读老待办、不做组织同步及 A8 单点登录，本地人事异动与离职放行可用。
   - **一次性迁入核对另行执行**：在 §4 授权的受控窗口内按迁入规格执行并留存迁入批次、来源对照与差异清单；完成后关闭通道、回收配置及临时放行，账号达到 §4 条件后另卡撤销。不得以首轮同步成功作为部署验收。
6. 回滚：`current` 指回上一版本并重启。数据库迁移只做向前兼容的变更，1a 期间不做破坏性迁移。

## 8. 日志、关联号与业务时区（本轮实施，运维需要知道的行为）

- **结构化日志**：应用写 ECS 格式的 JSON，一行一个事件（Spring Boot 结构化日志）。字段含 `@timestamp`、`log.level`、`log.logger`、`message`，以及日志上下文里的 `correlationId`（每个请求、作业运行、事件处理各一个）和 `userId`（只记 ID，不记姓名、手机号、令牌）。
  - 默认写标准输出，由 systemd 单元转入 `/data/forcitis-one/logs/*.json`，再由 SLS 采集；也可设置环境变量 `LOGGING_FILE_NAME` 直接落文件。
  - 排查一次操作：拿到响应头 `X-Correlation-Id`，在 SLS 里按 `correlationId` 检索，请求、事件处理、出站投递、发往企业微信的请求用的是同一个值。
- **关联号**：请求头 `X-Correlation-Id`（必须是 UUID，不是则 400）；没有就生成，并写进响应头。发给企业微信的请求带同一个头（对方忽略也无妨）。
- **业务时区**：`forcitis.kernel.business-zone`，默认 `Asia/Shanghai`；业务日、工作日历、`businessDate` 都按它截日，时刻一律以 UTC 存储（数据库只允许 `timestamptz`）。
- **审批时刻只有一个时间来源**（FLW-1，RULE-386）：审批任务的创建、领取、完成、截止、提醒、升级、改派与流程归档截止日都取应用注入的时钟（UTC）；流程引擎在构建时接上同一个时钟（`FlowableEngineClock`），不读数据库的 `now()`，其时钟不可被改写或重置。时限不靠引擎计时器：截止、提醒、升级记在 `platform_approval.approval_tasks`，由定时作业判定（ShedLock 保证单节点执行）。因此 JVM 时区须为 UTC（应用启动时校验），各节点系统时间须经 NTP 同步（同步方式与偏差阈值见本节「节点时间同步」）；数据库时间偏差不影响审批业务时刻。行级 `created_at` / `updated_at` 列目前仍取数据库默认时间，由 R-4 去掉。不得用改机器时间的方式演练「时间穿越」，只在测试中替换时钟。
- **指标命名**：`forcitis.<子域>.<bc>.…`，例如 `forcitis.platform.integration.deliveries.dead.letters`；看板和告警规则用新名字。
- **新增 schema**：`organization_attendance`（工作日历），已写入 `one_db_roles_schemas.sql`；上线前需在每个环境执行该脚本中对应的建 schema 与授权语句，再启动应用。
- **工作日历要先维护**：依赖工作日历的计算（审批时限等）在所属年份没有维护时失败（提交本身已成功，失败的是审批发起，单据保持已提交，补录日历后重投）并记 `ALERT` 日志与指标 `forcitis.organization.attendance.calendar.unmaintained`；管理员用 `PUT /api/v1/organization/work-calendars/{年}` 维护（权限 `organization.attendance:work-calendar:update`），每年底前维护下一年。
- **今天之前的日期不可修改**（T2）：已维护过的年份里，改动早于今天的日期会得到 422 `ORGANIZATION_ATTENDANCE_LOCKED_DAY_UNCONFIRMED`；一个年份的首次维护不受此限。例外是独立用例 `MaintainLockedWorkCalendarDays`，要求权限 `organization.attendance:locked-day:update`（只授给组织数据管理员），并发出 `WorkCalendarLockedDaysChanged`：载荷与审计条目的说明里列出每个被改日期及其改前改后的类别。已算出的审批截止时刻不重算。
- **节点时间同步**（one-ops 核定，2026-10-15）：chronyd（E-2），时间源为阿里云内网 NTP（`ntp.cloud.aliyuncs.com` 等 14 个，实际选中 100.100.x.x），`makestep 10 3`（只在启动后前 3 次更新内对超过 10 秒的偏差跳变，之后只平滑校正，时钟不倒退）、`rtcsync`。2026-10-15 只读核对：已同步，Stratum 3，偏差 0.4 ms，RMS 0.15 ms。告警阈值：偏差绝对值 > 100 ms，或未同步（`Leap status` 不是 Normal、参考时间早于 2 小时）→ P2；偏差绝对值 > 1 s → P1。正常偏差在 1 ms 以内，100 ms 已偏离两个数量级，仍远小于审批时刻的分钟级粒度；到 1 s 会影响多节点间审批事件的先后与令牌有效期校验。告警方式沿用证书检查（C-7）：`tools/ops/ecs/scripts/one_time_sync_check.sh` → `/usr/local/sbin/`，`tools/ops/ecs/files/one-time-sync-check.service` / `.timer` → `/etc/systemd/system/`，每 5 分钟读一次 `chronyc -n tracking`，结果写 `/data/forcitis-one/logs/ops/time-sync.json`；超阈值的行以 `ALERT P1` / `ALERT P2` 开头，由 SLS 兜底规则捕获，同时经 `one_alert_send` 推送到群机器人；读不到 chronyc 输出按 P1、退出码 2。阈值可用 `ONE_TIME_WARN_MS` / `ONE_TIME_CRIT_MS` / `ONE_TIME_MAX_REF_AGE_S` 调。云监控没有时间偏差指标，不接。已安装（2026-10-15，用户批准，one-coordinator 转达；云助手 t-hz07045fzz8xtkw，回读 t-hz07045h6e7d91c：三个文件 SHA256 与仓库一致，定时器已启用，首次运行 `Result=success`）。安装前在 ECS 临时目录用假 chronyc 验证（t-hz07045eix6kpvk）：正常 → 0；250 ms → P2；2 s → P1；未同步且 3 小时未更新 → P2；chronyc 失败 → P1、退出 2。演练：`ONE_TIME_SYNC_DRILL=1 ONE_TIME_WARN_MS=0.0001 /usr/local/sbin/one_time_sync_check.sh`（消息带「[演练]」，向群里发一条 P2；阈值只在本次调用中生效，不改常驻配置）；2026-10-15 已演练一次，送达无失败。回滚：`systemctl disable --now one-time-sync-check.timer`，删除两个单元文件与脚本。

## 9. 事件发布登记表的运维（ADR-0025 评审 C-4、C-8）

- 事件与业务写入同事务写入 `platform_integration.event_publication`；处理完成的发布移入 `event_publication_archive`（`spring.modulith.events.completion-mode=archive`），保留 30 天。
- **重投作业**（`platform.integration.event.republish`，ShedLock 互斥）：每 `republish-interval`（默认 1 分钟）重投超过 `minimum-age`（默认 1 分钟）仍未完成的发布，最多 `maximum-attempts`（默认 8）次。
- **告警**：用尽次数仍未完成的发布数 → 指标 `forcitis.platform.integration.event.publications.exhausted` 与每分钟一条 `ALERT` 日志；未完成超过 10 分钟的发布数 → `forcitis.platform.integration.event.publications.stale`。两者对应方案中的 P2 告警（未完成事件发布 / 死信）。
- **清理作业**（`platform.integration.event.cleanup`，每日 04:30）：删除归档中超过 30 天的行。
- 定时作业总开关：环境变量 `FORCITIS_JOBS_ENABLED` **必须设置**（`true` 或 `false`），没有默认值，缺失时应用启动失败；多个实例同时设为 `true` 没有问题，ShedLock 保证每个作业同一时刻只在一个实例上运行。所有实例中**至少一个**必须为 `true`，否则全部定时作业停止（重投、清理、核对都不跑，也没有告警提示）；模板 `one.env.template` 默认 `true`，安装器在值不是 `true` / `false` 时拒绝安装。one.env **不得设置** `SPRING_AUTOCONFIGURE_EXCLUDE`（含 `SPRING_AUTOCONFIGURE_EXCLUDE_0_` 等带下标的写法）或 `SPRING_APPLICATION_JSON`：经宽松绑定，它们会整体替换应用自己在配置中排除的自动配置（#228 起排除 Spring Modulith `MomentsAutoConfiguration`），被排除项随之回来；安装器发现即拒绝安装。
- 开关：环境变量 `EVENT_PUBLICATION_JOBS_ENABLED`（默认 true）；`MomentsAutoConfiguration` 已在 `application.properties` 排除（它自带 `@EnableScheduling`，会使 `FORCITIS_JOBS_ENABLED=false` 失效）。其余参数见 `config/platform_integration.properties` 的 `forcitis.platform.integration.event-publication.*`。
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
| P2 | 功能降级但可绕过 | 定时作业连续失败；企业微信投递死信；对账有差异 | 当班工作日内处理 |
| P3 | 需要关注 | 序号用到 90 %；登录失败率偏高；未维护的工作日历年度 | 每周例会处理 |

- 规则放在 ARMS / SLS 中，名称以 `forcitis.<子域>.<bc>.…` 指标为准；每条告警写明级别、阈值、处理手册链接。
- 新增指标：`forcitis.organization.workforce.reconciliation.differences`（每日对账差异数，非零为 P2）。
- 新增限流：登录类请求按「客户端地址 + 请求类别」每分钟计数，超出返回 429；类别与配置项为 `forcitis.platform.identity.login-throttle.limits.<类别>`，类别包括 `login-states`、`wecom-login`、`ticket-login`、`emergency-login`，没有默认值，缺一个启动失败；计数器在单个节点内，多节点部署时总额 = 节点数 × 限额。
- **限额依据办公室出口地址**：全所人员常经同一个出口 IP 上网，上班时段短时间内集中登录，所以人人都要用的 `login-states`（600）和 `wecom-login`（300）取数百，单人使用的取较低值。上线前用生产反向代理访问日志实测：取工作日早高峰最忙一分钟内「同一出口地址的 `login-states` 与 `wecom-login` 请求数」的最大值，限额至少取其 2 倍；若各分所出口地址不同，按最大的那个算。命中 429 时先查日志中的客户端地址与请求类别，再决定调限额，不要整体关闭限流。

## 12. 登录失败的排查：默认角色缺失
- 现象：所有人登录都返回 503，错误码 `PLATFORM_IDENTITY_DEFAULT_ROLE_MISSING`。
- 原因：新员工首次登录时系统按配置项 `forcitis.platform.identity.default-role`（当前为 `employee`）授予默认角色；该角色在库里不存在或已被停用时，不能给新账号授权，登录按「服务不可用」拒绝，而不是放进一个没有权限的账号。
- 处理：在角色管理中确认该编码的角色存在且启用（系统预置角色不可删除；自定义角色被误删或停用时重建或启用）；不要为了让人登进来而把配置改成别的角色。已有账号不受影响。

## 13. 数据库扩展 btree_gist
- 用途：`organization_workforce` 的法人归属期间表（`org_unit_legal_entities`、`employee_legal_entities`）用 EXCLUDE 约束保证同一部门或员工的期间不重叠，约束里的等值比较需要 `btree_gist`。
- 建库时由有权限的账号执行 `CREATE EXTENSION IF NOT EXISTS btree_gist;`（见 `one_db_roles_schemas.sql`）。Flyway 迁移中也带同一句 `IF NOT EXISTS`；迁移账号没有建扩展权限时，迁移会失败，先补上这一步再重试。
- RDS PostgreSQL 的 `btree_gist` 默认可用，不需要改 `shared_preload_libraries`，也不需要重启。

## 14. ADR-0055 对齐记录

派工卡：OPS-1021-OPS-01；Codex 起草。本节记录文档对齐目标，不证明代码、发布包、环境或账号已完成清理。依据为已采纳的 `../../adr/src/ADR-0055.md`，尤其决定 1、5–7、影响面及迁移方案。

计数按下表处置项：改写 19 处、删除 6 处、保留 3 处。同一用途的一组配置或多处重复说明计一处，不按关键词命中次数计数；新增记录与待决清单不计入。行号均为本次修改后的正文行号，删除项指向原位置之后的首个保留行。

| 编号 | 处置 | 行号（改后） | 章节 | 原写法要点 | 新写法或删除 / 保留理由 | 依据（ADR-0055） |
|---|---|---|---|---|---|---|
| R01 | 改写 | L5、L11 | 开场纪律 | A8 配置与生产库查询未区分用途 | A8 配置仅限一次性迁入通道（受控退役用途）；查询纪律保留，明确无运行时读取 | 决定 1、7 |
| R02 | 改写 | L28 | §1 D-4 | 部署前建立两库视图、账号并加白名单 | 仅在受控退役用途方案确认后另卡开通并按条件回收 | 决定 1、7 |
| R03 | 改写 | L63 | §3 E-8 | 访问日志同时举企业微信与 A8 回跳凭证 | 删除 A8 回跳示例，保留不记查询串与企业微信凭证保护 | 决定 1、6 |
| R04 | 改写 | L68 | §3 E-13 | 四类告警含组织同步延迟与缩水保护 | 撤同步告警，其余三类原样保留 | 决定 6、7 |
| R05 | 改写 | L69 | §3 E-14 | 常驻出站放行 A8 验票与两库 SQL Server | 保留既有 RDS、企业微信；退役用途按卡临时放行 SQL Server，结束后回收 | 决定 1、7 |
| R06 | 删除 | L71（删除处锚点） | §3 E-16 | 登记 A8 验票回跳地址并做 SSO 冒烟 | 删除整项，无迁入用途 | 决定 1、6 |
| R07 | 删除 | L94、L95（删除处锚点） | §3 E-13 补充 | 组织同步、部门简称刷新、老待办读取的作业与告警 | 删除三行；审批时限及其他作业告警原样保留 | 决定 5.1、6、7 |
| R08 | 改写 | L96–98、L102–104、L106、L110 | §4 | 三个运行时只读账号、视图、常驻校验与白名单 | 改为受控退役用途；旧账号与脚本仅作核对入口，按实际用途审定，不自动执行或合并；保留最小权限与用途完成后的退出条件 | 决定 1、5.1、6、7 |
| R09 | 改写 | L114、L120、L126、L129、L167、L178、L197 | §4.1 | 核实命令按运行时三连接、one.env 与同步日志说明 | 仅核实受控退役账号；兼容连接名与变量原样保留，专用环境文件按次注入；撤同步结果说明 | 决定 6、7 |
| R10 | 删除 | L226、L229（删除处锚点） | §5 应用变量 | 两行 A8 单点登录开关及必需配置说明 | 删除两行，不再作为常驻服务启动或启用要求 | 决定 1、6 |
| R11 | 删除 | L229（删除处锚点） | §5 应用变量 | A8 验票与失败后回老系统登录的 URL | 删除验票地址与回跳配置 | 决定 1、6 |
| R12 | 删除 | L240（删除处锚点） | §5 应用变量 | A8 视图参数、两库连接、老待办 URL、部门简称读取及同步任务开关 | 删除九行常驻配置；受控访问连接仅见 §4.1，确需的迁入字段另随迁入规格确认 | 决定 5.1、6、7 |
| R13 | 改写 | L242 | §5 前端构建变量 | PC 构建包含 A8 SSO 门户 URL | 删除 `VITE_SSO_PORTAL_URL`，企业微信变量原样保留 | 决定 1、6 |
| R14 | 改写 | L249、L252、L254 | §5.1 TLS | A8、部门简称、老待办连接参与常驻 prod 启动检查 | 主库与迁移连接要求保留；SQL Server TLS 仅用于受控退役通道，用完回收配置 | 决定 1、7 |
| R15 | 改写 | L267 | §5.2 轮换 | 两库三个只读账号口令常驻 one.env 并定期重启 | 口令仅随受控退役用途按次注入；保留使用期间轮换，不因轮换延长账号寿命 | 决定 7 |
| R16 | 删除 | L280（删除处锚点） | §5.3 保留作业 | 同步运行与差异表的保留期、清理作业及配置 | 删除整行，同步表随用途撤销；独立离职记录与审计保留 | 决定 3、6 |
| R17 | 改写 | L316、L319 | §6 员工查询 | 按 A8 登录名或企业微信 userid 查询员工 ID | 运行时仅保留企业微信 userid；A8 ID 仅用于一次性迁入追溯，不作组织源查询入口 | 决定 1、2、6 |
| R18 | 改写 | L323 | §6 双人在场 | 见证人员存在性依赖组织快照 | 改用本地人事记录，双人、审计、拒绝条件原样保留 | 决定 2、3 |
| R19 | 改写 | L329 | §6 应急在职校验 | 同步快照过期或关闭同步时放行并告警 | 改为本地在职校验；缺失或无效拒绝并告警，无外部快照不可用分支 | 决定 3.1、6 |
| R20 | 改写 | L331 | §6 日志告警 | 告警包含同步放行日志 | 仅将同步放行改为本地离职放行，其他告警与接收方式原样保留 | 决定 3、6 |
| R21 | 改写 | L332 | §6 管理员离职放行 | 同步暂停与 sync-runs 放行接口、依赖同步的恢复路径 | 本地离职命令独立放行，保留管理模式、审计、留任下限与双人运维约束，不杜撰新接口 | 决定 3.2–4、6 |
| R22 | 改写 | L345–346 | §7 冒烟 | A8 登录、组织同步成功及首轮同步延迟说明 | 仅保留企业微信登录；验证无旧生产连接、本地人事异动与放行可用，迁入核对另在受控窗口执行 | 决定 1、6、7；迁移方案 3、4 |
| R23 | 改写 | L369 | §9 作业总开关 | 停用总开关的后果仍列组织同步 | 只移除同步示例，重投、清理、核对要求原样保留 | 决定 6 |
| R24 | 改写 | L388–389 | §11 分级告警 | P2 组织同步延迟、P3 缩水保护扣留 | 删除两项同步示例，其他级别、响应时限和外部服务告警原样保留 | 决定 6、7 |
| R25 | 改写 | L393 | §11 登录限流 | 请求类别包含 A8 sso-login 与通用 ticket-login | 删除 A8 类别；通用签名票据类别原文保留，见待决 D01，不以保留批准 A8 接入 | 决定 1、6 |
| B01 | 保留 | L112–205 | §4.1 账号核实 | 兼容连接名、变量、权限判定、命令、退出码与 SQL Server 核实边界 | 仅为受控退役账号核实保留；旧 legacy / LEGACY / A8 标识是兼容输入，不建立常驻依赖；权限断言与已知未核实项不放宽 | 决定 6、7；需同步修改的守护规则 4 |
| B02 | 保留 | L227、L270、L393 | §5、§5.2、§11 通用签名票据 | `FORCITIS_TICKET_KEY_<KID>` 配置、轮换与 `ticket-login` 限流 | 原文保留，去留见待决 D01；不能由表名或配置名推定无非 A8 调用方 | 决定 6；迁移方案 3 |
| B03 | 保留 | L392 | §11 每日对账指标 | `forcitis.organization.workforce.reconciliation.differences` 每日对账差异告警 | 原文保留，是否属于撤销的外部组织同步对账见待决 D02 | 决定 6、7 |

### 关键词复查与保留理由

下表列出正文全部命中行及对应记录。对齐记录、复查表与待决清单中的关键词仅用于说明旧写法、删除证据、兼容标识或待核事项，不是部署指令。正文中保留的账号名、脚本路径、连接名和环境变量名保持原样；用途仅限 §4 的受控退役访问及 §4.1 的独立核实。

| 关键词 | 正文命中行（改后） | 交代 |
|---|---|---|
| A8 | L5、L11、L28、L69、L98、L102、L124、L129、L252、L254、L267、L329、L345 | R01、R02、R05、R08、R09、R14、R15、R19、R22、B01 |
| 致远 | 无命中 | 相关旧用途已删除或改写，见上表；本行仅为检索词 |
| 老系统 | L11、L28、L69、L98、L103、L104、L252、L254、L267、L345 | R01、R02、R05、R08、R14、R15、R22 |
| legacy | L103、L104、L125、L126、L198 | R08、R09、B01 |
| LEGACY | L103、L104、L125、L126 | R08、R09、B01 |
| SEEYON | L102 | R08 |
| 只读账号 | L98、L102、L103、L104、L267 | R08、R15 |
| 视图 | L102、L103、L104、L252 | R08、R14 |
| SSO | 无命中 | 相关旧用途已删除或改写，见上表；本行仅为检索词 |
| 验票 | L69 | R05 |
| 回跳 | 无命中 | 相关旧用途已删除或改写，见上表；本行仅为检索词 |
| 短名 | 无命中 | 相关旧用途已删除或改写，见上表；本行仅为检索词 |
| 老待办 | L103、L126、L252、L345 | R08、R09、R14、R22、B01 |

### 待决

- **D01（L227、L270、L393）通用签名票据的非 A8 合法调用方**：现有材料不能确认是否仍有非 A8 合法调用方，配置、轮换及 `ticket-login` 限流原文保留。两种写法的取舍：有合法调用方时改写为该调用方专用的配置与运维要求；身份生命周期研究与 T-13 取证确认无使用方时删除对应配置、轮换及限流类别。依据：ADR-0055 决定 6、迁移方案 3；本次不裁定，不据此启用 A8 单点登录。
- **D02（L392）每日组织对账指标的实际用途**：原文只写每日对账差异数，不能确认是否依赖 A8 / 老系统，原文保留。两种写法的取舍：若仅核对本地人事记录则保留并补明本地用途；若只服务已撤组织同步则删除常驻告警，确有迁入核对需要时仅写入一次性受控退役方案。依据：ADR-0055 决定 6、7；不自行把原指标改作其他用途。

### 没能核实的地方

- 本卡未联网，未运行云、数据库或容器命令，未读取或打印凭据；未核实实际账号授权、白名单、出站规则、CA、部署配置、调用方及账号撤销条件是否达成。§4.1 既有 SQL Server 实测待核项及安全残余原文保留，不因本次文档对齐视为通过。
- 仅本文件纳入修改；旧部署模板、剧本、SQL 和代码引用只作关联入口，本卡未证明它们已经按 ADR-0055 清理。常驻配置、票据去留与离职接口须由后续实施卡核对。
- 新增业务名词沿用 `docs/glossary/terms.yaml` 已批准的迁入、迁入核对、迁入批次、来源对照、外部身份、组织单元、员工、人员、岗位、在职、离职与部门简称；不新增领域标识或术语。
