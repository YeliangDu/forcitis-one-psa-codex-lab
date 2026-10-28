# 首次 dev 部署前运维剩余事项（按依赖排序）

维护：one-ops。更新：2026-10-16（第三轮：门禁、one.env、告警、白名单与代码前置按现况；见下「距离首次 dev 部署还剩什么」）；此前 2026-10-15（第二轮：C-3、C-4、C-6 已执行项，并入 RELEASE.md R-0..R-12；发布通路改为 ECS 自取 GitHub Release，R-3 取消；同日按用户「需要我做的，都让ops 会话自己做」改为 GitHub OIDC、OOS 请求参数与 ECS 定时拉取，R-13 取消，R-4 / R-6 / R-11 改由运维执行）。依据：`PREDEPLOY_CHECKLIST.md`、`CROSSCHECK.md`、`DECISIONS_A.md`、各剧本，以及 2026-10-15 对阿里云的只读查询。本表只列**首次 dev 部署之前**还要做的事；生产前事项见清单「生产前」时点。

「谁」：**ops** = one-ops（或 one-coordinator 派的运维子代理）；**用户** = 只能用户本人做或决定；**1a** = 1a 代码会话；**DBA** = 老系统 / A8 的数据库管理员（A-2 人选未定）。「花钱」：否 / 是（付费项编号见 C-7「落地方案」与本表）/ 按量（已开通服务的小额用量）。耗时是动手时间，不含等人。

## 距离首次 dev 部署还剩什么（2026-10-16）

**结论**：运维侧准备都已就位，剩两件有先后的事——① 代码：含 P3b 键与标签改名、(f) 基线、P5 的新 rc，等 R-1b P5（#310）合并；② 门禁 101：新 rc 上重跑 C-14 第一步 → 探针（含一次性 H-2）→ 删 101。两件做完就可以报用户批准部署。090 与 F-1 不再是 dev 前置（intent 2026-10-15-214650：改到「dev 白名单外开放前、上生产前」；blockers/090 已去掉 `blocks: deploy`，由 C-19 与 PREDEPLOY D-10 约束）。

| # | 事项 | 谁 | 卡住？ | 依赖 | 证据 |
|---|---|---|---|---|---|
| S-1 | 新 rc（含 P3b 键与标签改名、(f) 基线 #277、P5） | one-ops 出包，one-coordinator 定时机 | **卡**：#310 未合并（BLOCKED） | #310；出包前先报 one-coordinator | 现有 rc.3 不含 P3b 与 P5 |
| S-2 | C-14 第一步：新 rc 对三个正式账号重跑只读核实 | one-ops | 等 S-1 | one.env 老待办键已改名（C-20 ✅）；7 项 msdb 实测已通过 | 改名：t-hz0706dvd4pudc0；7 项：evidence/c14_f_msdb_effective_result.md |
| S-3 | C-14 探针逐项（探针账号经 RDS 云接口建删、USER_ID 回读，#394；H-2 平台不可构造，不做）| one-ops（约 75 分钟） | 等 S-2 与 #297 合并（脚本只认 main 上的文件）| ✅ 2026-10-16 完成（见 C-14 剧本执行记录）| 剧本 H-2 与 rdscore 残留两节 |
| S-4 | 删阻塞项 101 | one-coordinator | ✅ 与 S-3 同日完成 | — | — |
| S-5 | 报用户批准部署 → `deploy.yml` dev | one-coordinator 报；用户批准；one-ops 观察 | 等 S-4 | 门禁只剩 101 | `deploy_gate dev` 现仅被 101 拦 |
| S-6 | 部署当日：C-5 开通超管与应急账号（单人）、B-2 登录冒烟、C-19 (b) 角色持有人数核对、C-7 演练、R-11 ① ⑦ 随首次部署验证 | 用户 + one-ops | 部署后 | 见第 4 层 | — |

**要用户本人做的**：批准部署（S-5）；部署当日的 C-5、登录冒烟（4-1、4-2）。B-3（A8 单点登录入口）dev 暂缓，不做（intent 2026-10-15-214511，ECS 上单点登录开关已改 false）。
**已完成、有证据**：one.env 补同步根路径与作业总开关、单点登录关（t-hz07057zxlib280）；老待办键改名（t-hz0706dvd4pudc0）；dev 收件人白名单只留 1 条（t-hz0706mkmb6jsow）；企业微信应用可见范围只有 1 人（回读 t-hz0706sdyv97mrk）；告警行动策略 one-alert-p1 / p2 已建，SLS 兜底规则 `one-app-alert-error` 已启用；时间同步检查已装（t-hz07045fzz8xtkw）；C-6 导出与回放已做（t-hz07058klbng3cw）。

## 第 0 层：硬前置（不满足就不部署）

| # | 事项 | 谁 | 花钱 | 耗时 | 状态 / 依赖 |
|---|---|---|---|---|---|
| 0-1 | 1a 修复批合并，且 opus 复审通过（status/blockers/080） | 1a | 否 | — | ✅ 修复批已合并，blockers/080 已删除 |
| 0-2 | 发布流水线 PR #59 合并（发布包、签名、部署工作流） | 发布会话 → one-coordinator | 否 | — | ✅ 发布流水线已合并并多次使用（rc.1–rc.3 已发布并验签） |
| 0-3 | 迁移基线：基线开放标记改为首次生产部署前删除（METHODS M3「迁移基线」，PREDEPLOY_CHECKLIST C-15），不再是 dev 部署前置；dev 期间基线改动后按 dev 库重建剧本重建 dev 库 | 1a | 否 | — | 不阻塞 dev |

## 第 1 层：资源与账号（可与第 0 层并行）

| # | 事项 | 谁 | 花钱 | 耗时 | 状态 / 依赖 |
|---|---|---|---|---|---|
| 1-1 | C-3 建 `forcitis_one_dev` 与 `one_dev_*` 角色；RDS SSL 与 CA（N-3） | ops | 否 | — | ✅ 建库建角色与 verify（t-hz0701ctw0hn3eo）；三个登录角色口令在 ECS 上生成、只交 SCRAM 校验值；建库用内置 provider 的 `C.UTF-8`。实例 SSL 本已开启，CA 已装 `/etc/forcitis-one/rds-ca.pem`，三个角色 `verify-full` 实连通过 |
| 1-2 | C-2 免费部分 | ops | 否 | — | ✅ 备份窗口改到北京 02:00–03:00（改前核对夜间 CPU 峰值 0.1 %）；自动续费已开；续费用户自理，运维不核对（用户「续费不用管」）；删除保护对包年包月不可开。RPO：PostgreSQL 无日志备份频率选项，按用户决定接受现状（C-2「RPO 结论」） |
| 1-3 | B-5 A8 与老系统只读视图和账号 | ops | 否 | — | ✅ A8（one-ops-1a）；老系统（one-ops 重跑：verify 全符合、真实登录 8 项越权全拒、`one.env` 已写） |
| 1-4 | B-6 在职但无企业微信 ID 的只读计数 | ops | 否 | — | ✅ 在职 304、无 ID 0（2026-10-15T01:33:35+08:00） |
| 1-5 | C-4 余项 | ops | 否 | — | ✅ E-3 用户与目录、E-4 Temurin 25.0.4.1、E-8 / E-9 / E-10 新布局（TLS 软链、static-pinned、续期 webroot、套件收紧，续期演练通过）、E-11 / E-12 SLS 与指标采集、E-14 出站白名单；服务单元已装未启用；云监控操作系统级指标（内存、磁盘使用率含数据盘）已在上报（接口状态字段 `OsMonitorStatus` 显示 stopped 与实际不符，以指标为准） |
| 1-6 | A-3 超管与应急账号；A-8 dev 收件人白名单；A-5 值班 | 用户 | 否 | — | ✅ dev 阶段全部由用户本人兼任（非生产单人运维模式，ADR-0030；intent 2026-10-15-215052）；A-8 白名单已收窄为 1 条（t-hz0706mkmb6jsow）；值班：用户本人 |
| 1-7 | A-5 告警渠道 | 用户建群；ops 写入 | 否 | — | ✅ 「FORCITIS one 运维」群机器人；Webhook 经安全输入窗口写入 `ops-alert.env`（root 0600），测试消息用户确认收到；P1 不加短信 |
| 1-8 | C-7 证书到期检查安装（`one_cert_check.sh` + 定时器） | ops | 否 | 0.5 h | ✅ 2026-10-15 已安装（t-hz0701b833ib7r4）；告警经 `one_alert_send` 推送到运维群 |
| 1-9 | 付费决定 | 用户 | 是 | — | ✅ 用户选开 P-1、P-2、P-4、P-5，P-3 不开；P-4 快照策略已存在（含系统盘，保留现状）；P-5 无超额；P-1 / P-2 已开通（SLS 按使用功能计费、指标走 SLS Prometheus 指标库） |
| 1-10 | B-3 在 A8「第三方应用」新建「FORCITIS one」单点登录入口（仿照未启用的「`FORCITIS PSA`」，页面地址 `https://one.forcitis.com/login/sso`） | 用户 / A8 管理员（A8 后台） | 否 | 0.5 h | ⏸ dev 暂缓（intent 2026-10-15-214511）：dev 只用企业微信登录，`FORCITIS_PLATFORM_IDENTITY_SSO_ENABLED=false`（t-hz07057zxlib280）；要测致远登录前再做 此前：ops 已查清：老系统现用的「数字运营平台」关联系统只 POST `memberId`、不验票，不可照搬；票据方式见 `handoff/B-3`；A8 验票根地址 `https://bpm.forcitis.com`，ECS 可达；`one.env` 已设 `SEEYON_OA_BASE_URL`、`FORCITIS_PLATFORM_IDENTITY_SSO_ENABLED=true`、`LEGACY_LOGIN_URL` |
| 1-12 | C-3 `one_admin` 口令轮换（用户「先建后轮换」） | ops | 否 | — | ✅ 2026-10-15：ECS 上以 `ALTER ROLE` 自改口令（`scripts/c3_rotate_admin_password.py`），不依赖 R-4 |
| 1-11 | B-2 余项：企业可信 IP 47.114.56.157；H5 工作台入口（依赖 1a 改 H5 子路径，CROSSCHECK 第 4 条） | 用户（企业微信后台） | 否 | — | 可信 IP ✅ 2026-10-15（用户配置；ECS 出口 47.114.56.157 调 message/send 得 81013 而非 60020，IP 检查已通过，未发出消息）；H5 入口待 1a |

## 第 2 层：代码侧前置（1a）

| # | 事项 | 谁 | 花钱 | 状态 |
|---|---|---|---|---|
| 2-1 | Flyway `default-schema` / `schemas` 配置（CROSSCHECK 第 5 条；部署以环境变量兜底） | 1a | 否 | ✅ 迁移用 `--spring.flyway.default-schema=one_migration`（安装器传参，`application.properties` 也设） |
| 2-2 | H5 `publicPath` 改为子路径（CROSSCHECK 第 4 条，N-1） | 1a | 否 | ✅ H5 `publicPath` 由 `publicPathOf(env)` 决定，构建变量 `H5_PUBLIC_PATH` 提供子路径 |
| 2-3 | 运维命令：首任超管与应急账号登记（C-5 前提，blockers/070） | 1a | 否 | ✅ 运维命令已实现（`OperationsCommands`，档案 identity-operations）；C-5 剧本已按 INFRA_RUNBOOK §6 对齐并加端口兜底（UnsetEnvironment）；F-12（档案顶掉环境档案）dev 不受影响，生产前改 此前：ADR-0027 下一批 |
| 2-4 | 定时任务失败指标确认、Flowable 死信任务数指标（CROSSCHECK 第 13 条） | 1a | 否 | ✅ Flowable 死信指标已有（`DeadLetterJobMetrics`）；定时任务失败指标 `tasks_scheduled_execution_*` 待部署后在 prometheus 端点确认（C-7 前） |
| 2-5 | `SEEYON_VIEWS.sql` 注释与旧授权块（CROSSCHECK 第 10、11 条） | 1a | 否 | ✅ 不影响（B-5 以交接包为准）；`SEEYON_VIEWS.sql` 注释归 1a |

## 第 3 层：发布与部署一次性设置（RELEASE.md §7，PR #59 已合并）

| # | 事项 | 谁 | 花钱 | 耗时 | 状态 / 依赖 |
|---|---|---|---|---|---|
| R-0 | 专用部署 runner 重建（`/opt/forcitis-one-runner` 归 root、hook、LaunchDaemon、`--disableupdate`、演练） | 用户（需 Mac 管理员密码与一次性注册令牌） | 否 | 1.5 h | ✅ 2026-10-15：`mac-one-deploy` 在线（`scripts/release/runner_rebuild.sh`）；第 8 步拒绝演练通过（分支工作流与伪造变量均被 hook 拒绝），放行路径在首次 dev 部署时验证 |
| R-1 | GitHub environments `release` / `dev` / `prod`，只允许 `main` | 用户（仓库设置） | 否 | 0.2 h | ✅ 2026-10-15；dev / prod 另加 `v*` 标签部署策略（用户「允许，首次部署前加上 (Recommended)」） |
| R-2 | 仓库级 Actions variables：8 个构建期变量 | 用户 | 否 | 0.2 h | 8 个变量均已有值（DEV_* 与 PROD_* 的企业微信企业 ID、应用 ID、SSO 门户地址；`gh variable list` 回读非空）；SSO 门户的最终值待 B-3，不阻塞 dev 构建；生产前确认 PROD_* 是否同值 |
| R-3 | ~~发布用 OSS bucket~~ | — | — | — | **不再需要**：改为 ECS 自取 GitHub Release（用户「先测 GitHub 直拉，可行就用 (Recommended)」；ECS 夜间实测约 9 MB/s） |
| R-4 | GitHub OIDC `sub` 定制（`repo`、`context`、`job_workflow_ref`）；阿里云 OIDC 身份提供商 `github-actions`；每环境一个部署 RAM 角色 `forcitis-one-deploy-<env>`（信任策略精确匹配 `iss`、`aud=sts.aliyuncs.com`、`sub`=该环境 main 上的 deploy.yml）与部署策略（五个请求参数只有 `oos:UpdateParameter`、结果参数只有 `oos:GetParameter`，没有任何 ECS 接口）；拉取策略 `forcitis-one-deploy-pull`（请求参数只读、结果参数只写）挂到现有实例角色 `forcitis-one-ecs-archive`（RELEASE.md R-4、§8.2） | ops（经用户确认；按 RELEASE.md R-4 的接口清单执行并回读） | 否 | 0.5 h | 不建 RAM 用户、不生成 AccessKey；不新建、不重绑实例角色：该实例角色承载归档写入与部署请求拉取两份最小策略 |
| R-5 | 各 environment 的 secrets：dev / prod **不需要任何阿里云 secret**；只剩 `release` 的 `RELEASE_SIGNING_KEY` | — | 否 | — | ✅ 随 R-12 已就位；SSH 触发方式已删除 |
| R-6 | 各 environment 的 variables：`ALIBABA_CLOUD_ROLE_ARN`、`ALIBABA_CLOUD_OIDC_PROVIDER_ARN`、`ECS_REGION`（不是机密）；dev 的 ECS 为 `i-bp122xlpar8w2uylmal2`，prod 的 ECS（待建）不进变量 | ops（`gh api` 写入并回读） | 否 | 0.1 h | R-4；`ECS_INSTANCE_ID`、`ECS_COMMAND_ID`、`DEPLOY_TRIGGER`、`DEPLOY_*`、`OSS_*` 不再使用，已建的删除 |
| R-7 | ECS：安装器、签名公钥、`release-source`（`ENVIRONMENT`、`WECOM_VERIFY_FILE`）、拉取定时器与服务 `forcitis-one-release-pull.timer` / `.service`（root，每分钟，单实例）；预建五个 OOS 请求参数与结果参数 `forcitis-one-deploy-result-<env>`（占位初值 `{}`）；不建云助手命令；已装的旧令牌文件与令牌检查单元按 RELEASE.md R-7「卸载」删除 | ops | 否 | 1 h | R-4、R-12；E-3 / E-9 / E-10 部分已完成；ECS 上不需要 aliyun CLI 或 GitHub 令牌；ECS 出方向须能访问 `oos.cn-hangzhou.aliyuncs.com` |
| R-8 | 标签规则 `v*` | 用户 | 否 | 0.1 h | ✅ 2026-10-15（只限更新与删除，见 RELEASE.md R-8） |
| R-9 | 启用 immutable releases；确认 Actions 权限与托管 runner | 用户 | 否 | 0.1 h | ✅ 2026-10-15 |
| R-10 | `one.env` 补 `SPRING_FLYWAY_ENABLED=false`；`/etc/forcitis-one/migrate/` 迁移凭据 | ops | 否 | — | ✅ 两项都已就位（one-ops-1a；迁移凭据由 C-3 写入）；首次 Flowable 安装项待首次部署 |
| R-11 | 连通演练（不部署，不需要令牌；RELEASE.md R-11 ①–⑧）：真实读写参数（请求设为 `invalid` 应被拒并告警一次）、4096 上限、告警限频、混搭被拒且不消耗防重放记录、已处理请求静默跳过、定时器单实例；OIDC 换凭据与合法请求只能在首次 dev 部署时验证（ship.sh 的「STS session …」与结果参数） | ops | 否 | 0.5 h | R-4、R-6、R-7 |
| R-12 | 发布签名密钥：可信机器生成，私钥进 secret，公钥经 PR 写入 `allowed_signers` 并装到 ECS | 用户生成；ops 装 ECS 公钥 | 否 | 0.3 h | ✅ 2026-10-15；ECS 公钥随 R-7 安装 |
| R-13 | ~~GitHub 只读令牌~~ | — | — | — | **不再需要**：ECS 不访问 GitHub API；安装内容写在只有该环境 deploy 作业（经 OIDC 证明身份）能写的 OOS 参数中，由 ECS 定时拉取（用户「需要我做的，都让ops 会话自己做」；one-coordinator 批准）；已装的令牌文件与检查随 R-7 卸载 |

## 第 4 层：部署当日与部署后（出口前）

| # | 事项 | 谁 | 花钱 | 依赖 |
|---|---|---|---|---|
| 4-1 | C-5 首任超管与应急账号开通（双人在场） | 用户 + ops（dev 单人模式，不要求双人在场） | 否 | S-5 部署完成；2-3 ✅、1-6 ✅；需 rc 含 `find-accounts-by-external-subject` 与档案修正（F-12 对 dev 无影响） |
| 4-2 | B-2 登录冒烟、可见范围核对、其他出口发消息被拒 | 用户 + ops | 否 | 部署后；企业微信应用可见范围已只剩用户本人（t-hz0706sdyv97mrk），其他出口发消息被拒由 C-19 (c) 的白名单保证 |
| 4-3 | C-6 导出：0 计数 → 角色 / 日历 / 回放；回放在 ECS 上运行；日历导入 dev | ops（导出、回放）；组织数据管理员（导入） | 否 | 导出与回放已做（C-6 剧本「回放结果」：500 实例 / 561 节点，MATCH 474、REVIEWED 81、6 条归为回放方法限制，未取证坐实，D-3 由用户在出口评审时看）；日历导入待部署后由组织数据管理员（用户本人）做 |
| 4-4 | C-7 四类告警配置与演练（出口条件 D-6） | ops | 是（P-1、P-2） | SLS 行动策略与兜底规则 `one-app-alert-error` 已建（回读）；指标类规则（登录失败率、应用不可达、同步延迟）待部署后接 ARMS 抓取（P-1）并演练；D-6 |
| 4-5 | B-4 余项：ICP 主体直接核验；TLS 1.2 套件收紧后视情况跑 SSL Labs（`hideResults=on`） | 用户（ICP）；ops（套件，经确认） | 否 | 不阻塞 dev 部署；ICP 主体核验用户本人做 |

## 并行、不阻塞 dev 部署

| 事项 | 谁 | 花钱 | 说明 |
|---|---|---|---|
| B-13 归档 OSS bucket（在线 3 年 + 归档 7 年） | ops | 归档存储按量（空 bucket 不计费） | ✅ 2026-10-15 已建（bucket、写入方角色、演练通过），WORM 未锁定；第一个写入方上线前重新初始化并锁定（PREDEPLOY B-13）。[`handoff/B-13-archive-oss-bucket.md`](handoff/B-13-archive-oss-bucket.md) |
| C-1 RDS 扩容 | 用户 | 是 | 用户「现在还在开发阶段，生产阶段可以扩容的现在不做」：移到生产前 |
| C-8 KMS、C-9 生产库、A-6 云安全中心付费版 | 用户 / ops | C-8、A-6 是 | 生产前 |
| 生产前：维护窗口开启 SQL Server 实例 `rm-bp1690ep53t9778dp` 的 SSL，生产连接强制校验证书 | 用户定时间；ops | 否 | 用户「dev 先信任服务器证书，生产前开 SSL (Recommended)」；开 SSL 会重启承载 A8 与老系统的实例 |
| 生产前：RDS PG 实例证书重签（私钥曾进入会话记录，必要时重启，与 IT 门户协调） | 用户定时间；ops | 否 | 用户「和生产前维护一并做」；PG 侧 SSL 已开启、`verify-full` 已就绪，重签后核对 CA 未变 |
| 生产前：启用第二名超级管理员与第二个应急账号，C-5 恢复「双人在场」；撤销 dev 单人例外（若已批准） | 用户；ops | 否 | 用户「这些安全措施等到生产部署前再真正落实」；人选已定，见私有文件 |
| 生产前：RPO 复核 | 用户 | 可能（高频快照按量） | 已确认 PostgreSQL 无日志备份频率选项；平台约定为「主机故障不丢数据（高可用）；误操作恢复精度取决于日志备份频率（实测约 2.4 小时），生产前再评估」；生产前决定是否开高频快照备份 |

## 安全事件与风险登记

| 日期 | 事项 | 影响 | 处置 |
|---|---|---|---|
| 2026-10-15 | 安全事件：调用 `rds DescribeDBInstanceSSL`（pgm-bp13vak0sew3m003）时直接打印了返回，返回中的实例证书私钥（`ServerKey`）进入 one-ops 会话记录 | 持有私钥者可冒充该 RDS 实例做中间人，须能截获 VPC 内网流量 | 用户「和生产前维护一并做」：生产前重签证书；已在 INFRA_RUNBOOK、C-2、C-3 加「不得调用会返回密钥的接口、不打印云接口原始返回」 |
| 2026-10-15 | 风险：老系统应用服务器（公网 47.96.80.40）上的配置中心 8848 无需登录即可读出生产数据库高权限口令；安全组只对 `58.213.91.210/32`、`58.213.91.218/32` 放行 8848 | 用户确认两地址「是公司出口」：公司办公网内任何人可不登录读取该口令 | 按用户「老系统先不管」不处理 |
| 2026-10-15 | 风险：SQL Server 实例 `rm-bp1690ep53t9778dp` 公网白名单组含 `0.0.0.0/0` | 该实例同时承载新系统读取 A8 与老系统的只读账号 | 用户「保持不动」 |
