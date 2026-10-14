# 部署前清单核对记录（2026-10-14）

范围：`PREDEPLOY_CHECKLIST.md` 各项的依据，与当前 main（e922842）的代码、配置、迁移脚本、视图脚本逐条比对。只读核对，未连接任何环境。行号以核对时为准。

「处理」列：**已改**＝本次在 `phase1a/infra/` 内改准；**待他处**＝须由对应会话改，本目录只记录并在剧本中给出绕行。

| # | 不一致 | 位置 | 处理 |
|---|---|---|---|
| 1 | 合并待办读老待办视图的连接变量是 `FORCITIS_PLATFORM_APPROVAL_LEGACY_URL/_USERNAME/_PASSWORD/_DETAIL_URL_TEMPLATE`；运行手册写成 `LEGACY_DB_RO_*` 与 `INTEGRATION_LEGACY_ENABLED`，而后两者实际是**部门简称**视图的连接与开关 | `INFRA_RUNBOOK.md` §5 原 104–106 行；代码 `backend/src/main/resources/config/platform_approval.properties:12-15`、`platform_integration.properties:25-30` | 已改（§5 表） |
| 2 | 老系统两个视图必须用两个账号：两个模块各自在启动时校验「能 SELECT 的对象恰好一个」，共用账号两边都会拒绝启动；运行手册写「同上或另设」 | `INFRA_RUNBOOK.md` §4 原 71 行；代码 `platform/approval/infrastructure/legacy/LegacyAccessGuard.java`、`platform/integration/infrastructure/legacy/SingleObjectGuard.java` | 已改（§4 表）；B-5 脚本建两个账号 |
| 3 | E-7 要求应用只听 127.0.0.1:8080、管理端点 127.0.0.1:8081 并开放 prometheus；代码未设 `server.address`、`management.server.port`，且只暴露 `health` | `INFRA_RUNBOOK.md` E-7（53 行）；`config/platform_identity.properties:21` | 已改：以环境变量补齐（`one.env.template`），不改代码 |
| 4 | E-10 把 H5 放在 `/h5`，但 H5 构建 `publicPath: '/'`，放在子路径下静态资源会 404；企业微信应用主页地址随之未定 | `INFRA_RUNBOOK.md` E-10（56 行）；`frontend/apps/h5/config/index.ts:33` | 待他处（前端会话改 publicPath，或用户选子域，`DECISIONS_A.md` N-1）；nginx 模板已留 `/h5/` |
| 5 | 运行手册 §2 说 Flyway 的 `default-schema`、`schemas`、`create-schemas=false` 「随骨架补齐」，配置中仍没有 | `INFRA_RUNBOOK.md` §2（38–41 行）；`backend/src/main/resources/application.properties` | 待他处（代码会话）；首次部署以 `SPRING_FLYWAY_DEFAULT_SCHEMA=one_migration` 兜底，已写入 C-4 |
| 6 | 全部模块迁移都以 `CREATE SCHEMA IF NOT EXISTS` 开头；PG 在判断「已存在」之前先查库级 CREATE 权限，草稿 `REVOKE ALL … FROM PUBLIC` 后只授 CONNECT，首次迁移会失败 | `one_db_roles_schemas.sql:15,46`；`backend/src/main/resources/db/migration/*/V*__*_create_*.sql` 首行 | 已改：可执行版授 `CREATE ON DATABASE` 给 `one_dev_owner`。若代码会话去掉迁移中的 `CREATE SCHEMA`，可撤回此授权 |
| 7 | PG 16 起 CREATEROLE 账号建的角色只得 ADMIN 选项；`CREATE SCHEMA … AUTHORIZATION one_dev_owner` 与 `ALTER DEFAULT PRIVILEGES FOR ROLE one_dev_owner` 要求执行人能 SET ROLE，草稿缺这一步 | `one_db_roles_schemas.sql:28,51-59,65-82` | 已改（可执行版 STEP 3） |
| 8 | 清单 C-3 与 ADR-0025 第 2 条要求按角色设 `work_mem`；草稿没有，只读角色也没有超时 | `PREDEPLOY_CHECKLIST.md` C-3；`one_db_roles_schemas.sql:38-44` | 已改（建议值，C-1 后可调） |
| 9 | 迁移角色的 `search_path` 是 `one_migration`；Flowable 安装运行用它，建表语句不带 schema，`ACT_*` 可能落在 `one_migration`，运行角色（`search_path=platform_approval`）找不到 | `one_db_roles_schemas.sql:32,89-93`；`application.properties` 中 `flowable.database-schema=platform_approval` | 已在 C-3 / C-4 写明：安装那次连接串加 `currentSchema=platform_approval`，以 V8 核对。**须在 dev 首次部署时验证** |
| 10 | `SEEYON_VIEWS.sql` 注释说实例 ID 配在 `forcitis.organization.workforce.a8.*`，实际键是 `forcitis.platform.integration.seeyon.views.*`（环境变量 `A8_*`） | `phase1a/SEEYON_VIEWS.sql:18`；`config/platform_integration.properties` | 待他处（不在 infra 目录） |
| 11 | `SEEYON_VIEWS.sql` 末尾「legacy-short-names」块仍是直接对 `dbo.system_dept` 列授权的旧方案，与 `LEGACY_ORG_NAMES_VIEW.sql` 的专用视图和应用的单对象校验（只认 `v_one_legacy_org_short_name`）矛盾 | `phase1a/SEEYON_VIEWS.sql:114` 起 | 待他处（建议删除该块，指向 `LEGACY_ORG_NAMES_VIEW.sql`） |
| 12 | 两份老系统视图脚本的 `CREATE LOGIN … PASSWORD = N'<<…>>'` 若照原样执行，会建出口令为占位文字的账号；`LEGACY_ORG_NAMES_VIEW.sql` 没有回滚段 | `phase1a/LEGACY_INBOX_VIEW.sql:45`、`phase1a/LEGACY_ORG_NAMES_VIEW.sql:26` | 已在 B-5 绕开（口令服务器端随机生成、只显示一次；补回滚）；正本建议加「勿直接执行」 |
| 13 | E-13 的「作业失败」写了「或 Flowable 死信任务数」，代码中没有这项指标；定时任务失败依赖 Spring 的 `tasks.scheduled.execution` 观测，需在 dev 端点确认存在 | `INFRA_RUNBOOK.md` E-13（59 行） | 已标注；C-7 规则暂不含 Flowable 死信。指标待代码会话补 |
| 14 | §6 的应急管理员运维命令、ADR-0026 的首任 `super_admin` 命令都未实现（无运维命令入口） | `INFRA_RUNBOOK.md` §6（115 行）；`backend/src/main/java` 中无 `forcitis.ops` / CommandLineRunner | 已标注；C-5 阻塞于 ADR-0027 下一批（blockers/070） |
| 15 | §7 第 1 步「CI 构建 jar 与 dist 并附 SHA256」：CI 只跑 verify，不产出发布包；Maven 产物名 `forcitis-one-0.0.1.jar` 与 E-6 的 `forcitis-one.jar` 不同 | `INFRA_RUNBOOK.md` §7（126 行）；`.github/workflows/ci.yml`；`backend/pom.xml:5` | 已标注；发布流水线待补（见清单遗漏项） |
| 16 | 致远单点登录开关 `forcitis.platform.identity.sso.enabled=false` 写死在配置文件，没有环境变量占位，运行手册只列了 `SEEYON_OA_BASE_URL` | `config/platform_identity.properties:28` | 已改（§5 增 `FORCITIS_PLATFORM_IDENTITY_SSO_ENABLED`，B-3 写明） |
| 17 | §5 漏列：`APPROVAL_DEADLINE_ENABLED`、`EVENT_PUBLICATION_JOBS_ENABLED`、企业微信重复错误码、前端构建变量 `VITE_WECOM_CORP_ID`、`VITE_WECOM_AGENT_ID`、`VITE_SSO_PORTAL_URL`、`TARO_APP_WECOM_CORP_ID` | `config/platform_approval.properties:19`、`platform_integration.properties:62`；`frontend/apps/web/src/modules/session/model/login-config.ts:11-13`；`frontend/apps/h5/config/index.ts:18` | 已改（§5 表） |
| 18 | D-1a-39 要求一次性凭证不进服务端访问日志；企业微信 `code/state` 与致远 `ticket` 以 GET 查询串到达 nginx（`/login/wecom`、`/login/sso` 落地页），nginx 默认日志会记下 | `process/1a/contract.md` D-1a-39；`frontend/apps/web/src/app/app-routes.tsx:28-29` | 已改（E-8 与 `nginx-one.conf`：日志只记 `$uri`，加 `Referrer-Policy: no-referrer`） |
| 19 | 清单 A-5、C-2、C-8 的「平台约定第 N 项」编号口径不一（A-5、C-2 按方案末尾「汇总」编号，C-8 按章节编号） | `PREDEPLOY_CHECKLIST.md:15,39,45` | 已改为引用 `PHASE1A_PLATFORM_CONVENTIONS.md` 的章节 |
| 20 | 运行手册 §4 仍写「重构完成前名为 `A8_VIEWS.sql`」，文件早已是 `SEEYON_VIEWS.sql` | `INFRA_RUNBOOK.md` §4（69 行） | 已改 |
| 21 | 契约 D-1a-145 说「TIMESHEET、凭证仍是旧模板」，迁移 `V20261014135300` 已把两者改为统一新格式并置为待确认 | `process/1a/contract.md:167`；`db/migration/platform_numbering/V20261014135300__…sql` | 待他处（流程梳理文档） |
| 22 | `sslmode=verify-full` 要求 RDS 开启 SSL 并下载 CA，清单与运行手册都没有这一步 | `INFRA_RUNBOOK.md` §5（79 行） | 已写入 C-3；决定见 `DECISIONS_A.md` N-3 |

核对无误的：回调路径（PC 扫码回跳 `/login/wecom`、致远落地 `/login/sso?ticket=`、验票 `/seeyon/thirdpartyController.do`）；A8 视图 33 列与 `seeyon-view-columns.yaml` 一致；老待办视图 7 列与 `JdbcLegacyInbox` 读取的列一致；部门简称视图 2 列一致；四类告警用到的指标名都在代码中；schema 清单与 `db/migration/` 目录一致（`cost_timesheet`、`delivery_execution` 为样例模块，见下）；ADR-0025 / 0026 / 0027、D-1a-10/35/61/110..117/138、I-8（SEC-119）引用均存在。

附注：样例模块 `cost_timesheet`、`delivery_execution` 的迁移会在库级 CREATE 授权后自行建 schema，运行角色对其无 USAGE；草稿称「生产前移除」，C-9 前须确认已移除。
