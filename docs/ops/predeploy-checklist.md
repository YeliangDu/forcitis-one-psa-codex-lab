# Phase 1a 部署前清单

整理：psa-coordinator，2026-10-14。用途：dev 部署与 1a 出口验证之前，一次问清并跟踪所有需要用户决定、只能由用户或运维执行、或执行前须用户确认的事项。细节以 `INFRA_RUNBOOK.md`、ADR 与 `docs/business/processes/contract.md` 为准；本清单只做汇总与状态跟踪。

**1a 修复批合并前禁止部署任何环境**（status/blockers/080）。下表的交接包与剧本只是准备，执行仍须逐项经用户确认。

状态：⬜ 未开始 · 🟨 准备就绪、待执行（决定单 / 交接包 / 剧本已备好）· ✅ 完成。时点：**dev 前** = 部署 dev 之前；**出口前** = 1a 出口验证之前；**生产前** = 生产切换之前。

配套文件：A 类决定单 `DECISIONS_A.md`；B 类交接包 `handoff/`；C 类剧本 `runbooks/`；与代码的核对 `CROSSCHECK.md`。

## A. 需要用户决定

| # | 事项 | 建议 | 时点 | 依据 | 决定单 | 状态 |
|---|---|---|---|---|---|---|
| A-1 | `one.forcitis.com` 的 DNS 解析与证书由谁办理、用哪种证书 | 阿里云 SSL 证书服务（与现有证书统一管理）；DNS 指向新 ECS 公网地址 | dev 前 | INFRA_RUNBOOK D-3、E-9 | [A-1](DECISIONS_A.md#a-1-oneforcitiscom-的-dns-与证书谁办哪种证书) | ✅ 改选 B：Let's Encrypt + 自动续期（用户 2026-10-15「同意」） |
| A-2 | A8 库与老系统库的只读账号（ADR-0037 全库只读）与视图由谁建（对方 DBA 是谁） | 指定一名 DBA，按 `handoff/B-5-dba/` 执行；同一人负责 6a 前的 `mirror_*` 只写账号 | dev 前 | D-4、ADR-0025 第 5 条 | [A-2](DECISIONS_A.md#a-2-只读视图与账号由谁建dba) | ✅ 由 one-ops 执行（用户「需要我做的，都让ops 会话自己做」）；B-5 已完成 |
| A-3 | 超级管理员人选（至少 2 名）与应急账号持有人（至少 2 个） | 平台负责人 + 一名运维；应急账号绑定超管持有人 | dev 前 | ADR-0026 / 0027、intent 156 | [A-3](DECISIONS_A.md#a-3-超级管理员持有人-2-名与应急账号持有人2-个账号) | 🟨 dev：用户本人兼任（非生产单人运维模式，ADR-0030；intent 2026-10-15-215052），正式人选生产前定；原状态：🟨 选项已定，人选待定 |
| A-4 | 组织数据管理员、安全审计员、流程管理员的人选 | 组织数据管理员 1–2 名（HR 或行政）；安全审计员由内控 / 财务；流程管理员 1 名 | dev 前 | ADR-0026 D-11、ADR-0027、D-1a-61 | [A-4](DECISIONS_A.md#a-4-组织数据管理员安全审计员流程管理员人选) | 🟨 dev：用户本人兼任（非生产单人运维模式，ADR-0030；intent 2026-10-15-215052），正式人选生产前定；原状态：🟨 选项已定，人选待定 |
| A-5 | 告警接收：P1 企业微信群与短信接收人、P2 群、值班安排 | 新建「FORCITIS one 运维」群；P1 短信给值班人与平台负责人 | dev 前 | 平台约定方案 §4（告警通道）、E-13 | [A-5](DECISIONS_A.md#a-5-告警接收) | 🟨 dev：用户本人兼任（非生产单人运维模式，ADR-0030；intent 2026-10-15-215052），正式人选生产前定；原状态：🟨 选项已定，人选待定 |
| A-6 | 新 ECS 的云安全中心付费授权是否加购（现为免费版） | 生产前加购 1 台 | 生产前 | 用户「暂时用免费版」 | [A-6](DECISIONS_A.md#a-6-新-ecs-的云安全中心付费授权) | ✅ 已决定 |
| A-7 | dev 库在上线前迁出共用实例还是停用 | 生产切换前停用 dev 库，另在开发机或临时实例做开发 | 生产前 | ADR-0025 第 2 条 | [A-7](DECISIONS_A.md#a-7-dev-库上线前迁出还是停用) | ✅ 已决定 |
| A-8 | 企业微信消息收件人白名单：dev 白名单名单；生产 `WECOM_RESTRICT_RECIPIENTS=false` 的启用时点 | dev 只发给项目组成员；生产切换当日关闭白名单 | dev 前 / 生产前 | 1a 第二波报告 | [A-8](DECISIONS_A.md#a-8-企业微信收件人白名单) | 🟨 dev：用户本人兼任（非生产单人运维模式，ADR-0030；intent 2026-10-15-215052），正式人选生产前定；原状态：🟨 选项已定，人选待定 |

核对中新增的待决事项（H5 路径、服务账号口令到期、RDS SSL）列在 `DECISIONS_A.md` 末尾 N-1..N-3，由 one-coordinator 决定是否并入本表。

## B. 只能由用户或运维执行

| # | 事项 | 时点 | 依据 | 交接包 | 状态 |
|---|---|---|---|---|---|
| B-1 | 在本仓库运行 `/design-sync`：设计 token 与正本对齐、正本备份到本地（入口条件），以及首次同步（出口条件） | dev 前 / 出口前 | brief Phase 1a 入口与出口 | —（用户在会话中运行） | ⬜ |
| B-2 | 在企业微信后台新开自建应用（卡片按钮回调、扫码登录回调、可信域名） | dev 前 | SECURITY_INVARIANTS I-8（SEC-119）、D-1a-138、E-15 | [B-2](handoffs/b-2-wecom-app.md) | 🟨 应用已建，可信域名已验证（2026-10-15 外部核验 `WW_verify_*.txt` 返回 200）；凭据已写入 ECS `/etc/forcitis-one/one.env`（0640 root:forcitis），ECS 上 `gettoken` 与 `agent/get` errcode=0（session-log one-1a-infra-b2）；待：其他出口发消息被拒（可信 IP）、登录冒烟与可见范围（dev 部署后）、本机凭据副本清理与 KMS |
| B-3 | 由 A8 管理员登记新系统的致远验票回跳地址（不替换老 SSO） | dev 前 | E-16、D-1a-40 | [B-3](handoffs/b-3-a8-sso-return.md) | ⏸ dev 暂缓：dev 只用企业微信登录，`FORCITIS_PLATFORM_IDENTITY_SSO_ENABLED=false`（2026-10-16 已在 ECS 改，t-hz07057zxlib280）；要测致远登录前再做（intent 2026-10-15-215052）；原状态：🟨 |
| B-4 | DNS 解析 `one.forcitis.com`，证书申请 | dev 前 | A-1 | [B-4](handoffs/b-4-dns-tls.md) | ✅ 解析与证书（2026-10-15 外部核验：A 记录 47.114.56.157，Let's Encrypt 至 2027-01-12，HTTP 301 到 HTTPS）；自动续期 `certbot-renew.timer` 每日两次、deploy hook 先 `nginx -t` 再 reload，`renew --dry-run --run-deploy-hooks` 通过（session-log one-1a-infra-b4）；待：ICP 备案主体核验、证书到期告警（C-7）；ICP 与公安联网备案：用户「已经完成所有备案」（2026-10-15）✅ |
| B-5 | 对方 DBA 建 A8 与老系统只读账号（ADR-0037：库级 SELECT、库级 DENY 写 / DDL / EXECUTE / REFERENCES，`read-all/*_apply.sql`）与视图（按 `SEEYON_VIEWS.sql`、`LEGACY_INBOX_VIEW.sql`、`LEGACY_ORG_NAMES_VIEW.sql`，视图冻结） | dev 前 | A-2、INFRA_RUNBOOK §4 | [B-5](dba-scripts/README.md) | ✅ 2026-10-15：A8 视图与 `one_a8_views_ro`、老系统 `psa_one_legacy_ro` 与 `one_legacy_names_ro` 已建；ADR-0037 后三个运行时账号改为库级只读（#135），另建取证账号 `one_evidence_ro`（#143）。连接为 `encrypt=true;trustServerCertificate=true`（加密、不校验证书），dev 可用；SQL Server 证书校验列入生产前维护窗口 |
| B-6 | 组织数据管理员在 A8 补齐「在职但无企业微信 ID」清单至 0（确无者逐个登记） | 出口前 | D-1a-35（出口条件） | [B-6](handoffs/b-6-wecom-id-coverage.md) | 🟨 上线前预估（2026-10-15T01:33:35+08:00，经 B-5 视图、`SeeyonViewSql` 同一范围）：范围内 1,254 人、在职 304 人、在职无企业微信 ID **0**、在职共用企业微信 ID 0；以 dev 同步后的问题清单为准 |
| B-7 | 组织数据管理员录入 2027 年工作日历（节假日与调休），业务管理员挂官方通知与公司加休登记后确认（ADR-0072 四眼） | 出口前（且每年 12 月 1 日前确认下一年；11 月 15 日起告警，12 月 1 日起未确认即阻断发起审批） | D-1a-110..117 | [B-7](handoffs/b-7-work-calendar-2027.md) | 🟨 |
| B-8 | 业务管理员（用户）确认各单据编号模板；凭证号模板另需财务确认 | 出口前 | ADR-0027、出口条件「编号规则经管理员确认」 | [B-8](handoffs/b-8-numbering-templates.md)（四眼确认待 ADR-0027 下一批实现） | 🟨 |
| B-9 | 企业微信接口真实返回核对（`task_id` 重复的错误码、模板卡片更新） | 出口前 | 1a 第二波报告 | [B-9](handoffs/b-9-wecom-api-check.md) | 🟨 |
| B-10 | 发布与部署一次性设置。**必需前置：按 RELEASE.md R-0 重建专用部署 runner `forcitis-one-deploy`**（只接 deploy；程序、hook 与空主目录归 root，PATH 只含系统目录，`--disableupdate`；原注册已注销；发布打包与签名在 GitHub 托管机器上）；发布包由 ECS 按部署作业给出的短期签名地址从 GitHub 直接下载（不建发布用 OSS bucket；ECS 不持有 GitHub 令牌）；GitHub environments `dev` / `prod`（只允许 main；Team 计划私有仓库没有 required reviewers，prod 的人工批准由 `prod-approvals.txt` 批准行代替，RELEASE.md §11）、专用 runner 的 pre-job hook、构建期变量、environment `release` 与发布签名密钥（Team 计划私有仓库无出处证明，RELEASE.md §11）、immutable releases、`v*` 标签保护、prod 批准行；每环境一个经 GitHub OIDC 扮演的部署 RAM 角色（只许改写该环境的五个 OOS 请求参数、读结果参数，没有任何 ECS 接口；无 AccessKey）；ECS 现有实例角色另挂拉取策略（请求参数只读、结果参数只写）；ECS 侧安装器、`forcitis-migrate` 用户、目录归属与迁移凭据目录；最后一跳为 ECS 上每分钟运行的拉取定时器，请求经 OOS 参数传递 | dev 前 | 遗漏项「CI 无发布产物」、INFRA_RUNBOOK §7 | [RELEASE.md §7、§8](runbooks/release.md#7-一次性步骤用户或运维执行执行前确认) | ✅ 2026-10-15：R-0..R-13、OIDC 拉取式部署 8 项、v* 撤销（RELEASE.md R-1、R-4、R-6、R-7）；v0.1.0-rc.1 已发布并验签（#151）；R-11 连通演练通过（#155）。① ⑦ 随首次 dev 部署验证 |
| B-11 | dev 库按迁移基线重建：迁移基线合并（R-3a，#103，73 个迁移合为 10 份基线）后，任何执行过旧迁移的库都过不了 Flyway 校验（历史表里的版本在仓库中已不存在），只能删库重建，不能清表后续跑。dev 库 `forcitis_one_dev` 目前尚未执行过任何迁移，首次部署前按 C-3 现行脚本补齐（见 C-3）即可；若届时已执行过旧迁移则删库重建 | dev 前 | R-3a 基线；METHODS M3「迁移基线」 | [C-3](runbooks/dev-db.md) | ✅ dev 库从未执行过旧迁移，已按 C-3 现行脚本补齐增量（C-3 执行记录），无需重建；首次部署时由安装器执行基线迁移；此后基线开放期内基线文件改动，按 C-16（[dev 库重建剧本](runbooks/dev-db-rebuild.md)）删库重建并重置服务器发布状态（ADR-0038 第 2 项第 2 条） |
| B-12 | 指定流程管理员：把 `process_administrator` 角色授予 1 名以上的人（建议 2 名，A-4 定人选）；该角色没有任何持有人时应用在任何环境都拒绝启动（只有测试配置可关闭） | dev 前 | D-1a-61；`process_administrator` 角色由 platform_identity 基线迁移建立（基线合并前为 V20261014205200） | — | 🟨 dev 由用户本人兼任（用户决定，人名只在私有 role_holders.md）；首次部署后以运维命令授予；生产前另定 2 名 |
| B-13 | 归档 OSS bucket `forcitis-one-archive`：第一个归档写入方上线前重新 `InitiateBucketWorm` 2557 天并锁定（锁定不可撤销，锁定前报 one-coordinator）；处理演练对象 `drill/20261015.txt` | 第一个归档写入方上线前 | 用户「现在不锁，第一个写入方上线前再锁 (Recommended)」 | [B-13](handoffs/b-13-archive-oss-bucket.md) | 🟨 bucket 与写入方角色已建、演练通过 |
| B-14 | 投产前角色配置：迁移种子与管理员模式都不持有的 4 个权限码，须由管理员在对应功能投产前建角色并授予——`cost.timesheet:timesheet:submit`、`cost.timesheet:timesheet:approve`（工时提交与审批，随工时全员切换 TA-2）、`platform.numbering:legacy-number:read`（旧编号查询）、`platform.numbering:voucher-numbering-rule:approve`（凭证编号规则审批）；授予须走 ADR-0026 授权申请与四眼，生产前另核职责分离 | 各功能投产前 | ARC-1021-ARC-11 `ssot/07-org-roles/GAPS.md`（检查 N01-R3）；one-coordinator 2026-10-21 | — | ⬜ |

## C. 由会话执行，执行前须用户确认（对外变更或购买）

| # | 事项 | 时点 | 依据 | 剧本 | 状态 |
|---|---|---|---|---|---|
| C-1 | RDS 扩容到 8 核 32 GB、ESSD 200 GB，开启存储自动扩容（购买） | 生产前 | ADR-0025 第 2 条、用户「扩容现有实例共用」 | [C-1](runbooks/rds-scale.md) | 🟨 时点由「dev 前」改为「生产前」（用户：不影响部署的扩容从 dev 前代办取消；dev 用现有 4C16G）|
| C-2 | RDS 开启删除保护；备份策略满足 RPO ≤ 5 分钟（日志备份）、RTO ≤ 1 小时；首次恢复演练。**恢复演练前置**：#228 合并并进入所用发布包（演练以整应用冒烟，此前会跑全部 `@Scheduled` 作业），未满足不做演练 | dev 前（演练在出口前） | 平台约定方案 §5 | [C-2](runbooks/rds-backup-restore.md) | ✅ 2026-10-15：包年包月实例不支持删除保护（`OperationDenied.PayType`），已开自动续费；日志备份已开、保留 7 天，PG 不能调日志备份频率，RPO 按现状由用户接受；恢复演练在出口前 |
| C-3 | 建库建角色：`forcitis_one_dev` 与 `one_dev_*`；按角色设连接数、超时与 work_mem | dev 前 | INFRA_RUNBOOK §2、ADR-0025 | [C-3](runbooks/dev-db.md) · `runbooks/sql/c3_dev_db*.sql` | ✅ 2026-10-15：`forcitis_one_dev` 与 `one_dev_*` 已建（one_admin 先建后轮换，口令服务器侧生成），verify-full 连通；`platform_idempotency` 等增量已补（#108）；`portal_migrator` 口令阻塞已不存在 |
| C-4 | ECS 初始化 E-1..E-17（数据盘、运行时、systemd、nginx、TLS、Logtail、ARMS、出站白名单） | dev 前 | INFRA_RUNBOOK §3 | [C-4](runbooks/ecs-init.md) · `tools/ops/ecs/scripts/` · `tools/ops/ecs/files/` | 🟨 2026-10-16 核对：E-4 ✅ Temurin 25.0.4.1（RPM）；E-11 ✅ LoongCollector 运行；E-17 ✅ 数据盘与系统盘每日 03:00 快照、保留 7 天（forcitis-one-daily）。依赖部署：E-6 enable、E-10 静态资源、E-12 ARMS Prometheus 抓取（ADR-0042 只去掉 Java 探针）、E-13 告警演练；E-16 随 B-3 暂缓。 此前：🟨 已做：E-1 数据盘 XFS（one-data）挂载 /data、nginx 1.30.4 与 TLS、静态根 `/data/forcitis-one/current/web`（当前为 bootstrap）、`forcitis` 系统用户与 `/etc/forcitis-one/one.env`（session-log one-1a-infra-b4 / b2）；2026-10-15 one-ops-1a 按发布流水线的目录布局补 E-3 `forcitis-migrate`、E-5（`/data/forcitis-one` 与 `releases` root:forcitis 750、nginx 穿过 ACL、`logs`/`tmp`、`/etc/forcitis-one/migrate` 700、`SPRING_FLYWAY_ENABLED=false`）、E-6 单元与 logrotate 已装（首次部署前不 enable）、E-14 安全组出方向白名单（其余出站拒绝）；`psql` 客户端已装。**待**：E-4 发行版源无 Java 25，须用户确认来源；E-11 Logtail、E-12 ARMS 涉及计费，未做；E-17 数据盘快照策略。**注意**：企业微信校验文件 `WW_verify_*.txt` 现放在 current/web，发布切换 current 时须保留（见 RELEASE.md） |
| C-5 | 应急管理员与首任超管开通（运维命令，双人在场）。**前置**：#228（排除 Spring Modulith `MomentsAutoConfiguration`）合并并进入所用发布包；此前运维命令会建完整上下文、跑全部 `@Scheduled` 作业几秒钟，不执行 | dev 前 | INFRA_RUNBOOK §6、ADR-0027 | [C-5](runbooks/first-admins.md)（运维命令已实现，剧本待按 INFRA_RUNBOOK §6 对齐） | ⬜ |
| C-6 | 老系统数据只读导出：老角色与权限（ADR-0026 D-9 已授权）、TIMESHEET 流程实例（回放用）、`psa_calendar` 2016–2026（迁入工作日历） | dev 前 | ADR-0026、TIMESHEET_REPLAY、D-1a-110 | [C-6](runbooks/legacy-export.md) · `runbooks/sql/c6_legacy_readonly_export.sql` | 🟨 2026-10-16：三类导出已完成；回放已运行（500 实例 / 561 节点：MATCH 474、REVIEWED 81、DEFECT 6），见剧本「回放结果」；REVIEWED 待用户逐类接受，DEFECT 交代码会话（D-3）。 此前：🟨 |
| C-7 | 告警规则配置与 dev 演练（四类告警，含 NaN 缺数据规则与各模块自己的失败指标，见 INFRA_RUNBOOK E-13 补充） | 出口前 | E-13（出口条件） | [C-7](runbooks/alerts.md) · `tools/ops/ecs/files/alert-rules.yaml` | 🟨 另加：证书剩余不足 20 天告警（A-1 改选 Let's Encrypt 的前提） |
| C-8 | 配置与密钥从 `one.env` 迁到 KMS，确定轮换周期 | 生产前 | 平台约定方案 §6 | [C-8](runbooks/kms.md)（应用读取 KMS 需代码改动） | 🟨 |
| C-9 | 生产库 `forcitis_one_prod` 与 `one_prod_*` 角色 | 生产前 | ADR-0025 第 2 条 | [C-9](runbooks/prod-db.md) · `runbooks/sql/c9_prod_db*.sql` | 🟨 |
| C-13 | 生产主机上身份运维命令（`identity-operations`）的「见证人」`--witness` 只是自报文本：有服务器 shell 权限的人即可一人填两个员工 ID 执行。生产前定方案（例如见证人须在命令执行时以其本人凭据确认，或改为两人分持的审批步骤），与 C-11（关闭单人模式，ADR-0030）同批落实 | 生产前 | ADR-0027 双人在场；ADR-0030；INFRA_RUNBOOK §6 | — | ⬜ |
| C-10 | 每个环境的 one.env 设置 `FORCITIS_PLATFORM_IDENTITY_SSO_ENABLED`（`true` 或 `false`）；没有默认值，缺失则应用启动失败（交叉检查 7） | dev 前 | INFRA_RUNBOOK §5 | — | ⬜ |
| C-14 | ADR-0037 第 4 项只读账号校验（ADR37-1）：dev 依次配置三条只读连接，启动校验通过；人为撤销一项库级 DENY 后，下一次同步前的重检停掉该连接并写 ALERT；随后恢复授权并重启。实现合入时部署阻塞项 100 关闭，由阻塞项 101 接替：本项 dev 验证与真实账号逐项反例核实（runbooks/C-14-readonly-probe.md，含 PR #163 复审 H-1、H-2 新增反例（H-2 的 msdb 角色移出平台不可构造，不做））都完成后删除 101 | dev 前 | ADR-0037 迁移方案第 4、5 步 | INFRA_RUNBOOK §4 | ✅ 2026-10-16 真实账号核实完成（三个正式账号第一步 PASSED；探针第 1–15 项、第 17 项记录见 C-14 剧本「执行记录」）；阻塞项 101 已删；第 14 项 (g) 口径待 one-1a-par、生产前须定；dev 验证（撤销一项库级 DENY 后重检停连接并写 ALERT）随首次部署做 |
| C-15 | 删除迁移基线开放标记 `backend/architecture/migration-baseline-open`（单独 PR，不夹带迁移改动），此后迁移只追加；dev 期间保持开放，基线改动后 dev 库与服务器发布状态一起重置（见 C-16）。`deploy_gate.py` 对 prod 拦截、对 dev 只告警 | 生产前 | METHODS M3「迁移基线」；用户 2026-10-15（ADR-0038（已采纳，部分取代 ADR-0033 迁移方案第 5 条等）；intent `2026-10-15-124901-one-coordinator-部署前开发编排三项都采纳-加一个.md`） | — | ⬜ |
| C-16 | dev 库重建剧本 `runbooks/dev-db-rebuild.md`（one-ops）：基线开放期内基线迁移文件改过后，删库重建 dev 库并重置服务器发布状态（安装器比对两版 `migrations.txt`，迁移文件内容变了即拒绝安装，只删库不够；重置后不得回退到重置前的版本）；含 Flowable 首次安装项与首次部署前检查 | 基线改动后的第二次 dev 部署前 | ADR-0038（已采纳，部分取代 ADR-0033 迁移方案第 5 条等）；PAR-1 复审 H-1 | — | ⬜ |
| C-17 | ECS `/etc/forcitis-one/one.env` 补 `FORCITIS_PLATFORM_INTEGRATION_SEEYON_VIEWS_ROOT_PATH=00000001`（同步根单位的层级路径；没有默认值，启用视图读取而缺此项应用启动失败，INFRA_RUNBOOK §5）。2026-10-15 只读核对（t-hz0704aw6gtu0ao）该键缺失 | dev 首次部署前 | 值出处：规则卡 `docs/business/rules/identity-org-rules.md` 的 `A8_DEPT_ROOT_PATH = "00000001"`；R-1b-0 取证（PR #193）按此口径得到范围内 1254 人 | — | ⬜ |
| C-18 | ECS `/etc/forcitis-one/one.env` 补 `FORCITIS_JOBS_ENABLED=true`（dev 只有一个实例，须为 true），并确认没有 `SPRING_AUTOCONFIGURE_EXCLUDE`。`application.properties` 的 `forcitis.platform.scheduling.jobs-enabled=${FORCITIS_JOBS_ENABLED}` 没有默认值，模板与部署脚本原先都缺这一项，照模板部署应用起不来（复核 High） | dev 首次部署前 | INFRA_RUNBOOK §5、§9；`one.env.template` 与 `remote_install.sh` 已补校验（缺键、值不是 true / false、设置了 `SPRING_AUTOCONFIGURE_EXCLUDE` 都拒绝安装） | — | ⬜ |
| C-19 | 090（ADR-0026 1a 数据范围机制）完成并在 dev 验证之前，dev 只对用户本人开放（intent 2026-10-15-214650、2026-10-15-215052），同时收窄三处：(a) **登录入口**：dev 企业微信自建应用（B-2）的可见范围只含用户本人——在企业微信管理后台「应用管理 → 该自建应用 → 可见范围」只保留用户本人，删去其余成员与部门（需用户本人在后台操作，one-ops 执行时报 one-coordinator 转用户）；核对：用应用凭据调用 `agent/get` 回读 `allow_userinfos` 只有 1 人、`allow_partys` 与 `allow_tags` 为空，只记人数不记 userid；(b) **角色**：本系统除用户本人外不授予任何角色——核对：运维只读查询 `platform_identity` 中角色持有关系，持有人只有 1 人（只记人数）；(c) **消息**：`WECOM_RESTRICT_RECIPIENTS=true`，`WECOM_RECIPIENT_ALLOWLIST` 只含用户本人 1 条（只回读条目数）。090 删除之前三处都不得加人；每次加人前先核对 090 已删除 | dev 首次部署前（a、c）、首次部署后（b），及每次改可见范围、授角色、改白名单前 | `status/blockers/090-*`；A-8、B-2、B-12、C-5 | — | ⬜ |
| C-20 | ECS `/etc/forcitis-one/one.env` 把老待办连接三个键改名：`FORCITIS_PLATFORM_APPROVAL_LEGACY_URL / _USERNAME / _PASSWORD`（另有 `_VIEW_NAME`、`_DETAIL_URL_TEMPLATE` 若设置）改为 `FORCITIS_PLATFORM_INTEGRATION_LEGACY_INBOX_*`，值不变，改前备份；改后回读键名与非空。R-1b P3b（#260）起：留着旧键应用拒绝启动，新模板缺新键安装器拒绝安装，核实命令的旧连接名 `legacy-approval` 是用法错误（新名 `integration-legacy-inbox`，读新键）。需在含 P3b 的 rc 做 C-14 第一步之前完成，生产变更先报 one-coordinator | 含 P3b 的 rc 的 C-14 第一步前 | INFRA_RUNBOOK §4.1、§5；P3b RULING-P3B-10 | — | ⬜ |

## D. 部署后验证（Phase 1a 出口条件）

| # | 出口条件 | 依赖 | 状态 |
|---|---|---|---|
| D-1 | `one.forcitis.com` 上线；企业微信 OAuth 与致远 SSO 登录成功；匿名访问除白名单外全部 401 | A-1、B-2..B-5、C-1..C-5 | ⬜ |
| D-2 | 组织快照与 A8 对账零差异（人员数、部门数、上级关系，另加部门负责人、区域 AR、企业微信 ID） | B-5、D-1a-10 | ⬜ |
| D-3 | 回放生产 TIMESHEET 实例，审批节点集合与候选人和老系统一致 | C-6 | ⬜ |
| D-4 | 合并待办数量与老系统一致 | B-5 | ⬜ |
| D-5 | 编号规则按统一新格式经管理员确认；原编号可检索，新旧对照可查 | B-8 | ⬜ |
| D-6 | 四类告警接入并在 dev 演练触发 | A-5、C-7 | ⬜ |
| D-7 | 回滚演练：撤销 nginx 路由后老系统登录与审批不受影响 | D-1 | ⬜ |
| D-8 | 首次 `/design-sync` 完成 | B-1 | ⬜ |
| D-9 | 在职但无企业微信 ID 的清单为 0 | B-6 | ⬜ |
| D-10 | 阻塞项 090（ADR-0026 1a 数据范围机制）完成，并在 dev 验证：范围外的组织与人员读不到，`DataAccessRegistryTest` 等守护通过；随后删除 `status/blockers/090-*` | 090 | ⬜ |

## 不阻塞 1a，但在途

- 第三轮用语确认（ADR-0018：阶段结束时不得留有会计类提议术语）——1a 代码下一批交付后启动。
- 财务确认单 v2 的答复（G1 开工前必须）。
- AG Grid 企业版许可采购（R1 前）。
- OAT 文件在 ddd-theory 提交后，`docs/glossary/oat/README.md` 改引提交号。

## 核对中发现、清单尚未列出的前置项（供 one-coordinator 决定是否立项）

- **发布流水线**：已补 `release` / `deploy` 工作流与 [RELEASE.md](runbooks/release.md)（ECS 自取 GitHub Release、专用 runner、签名发布声明），用户侧一次性设置列为 B-10。~~仍待 1a 代码修复：`OneApplication` 无 `main`~~ 已解决：v0.1.0-rc.1 已构建出可启动的包（2026-10-15）。
- **迁移执行方式**：代码只有启动时以运行账号自动跑 Flyway，与剧本「迁移账号单独执行」不一致，且运行账号对 `one_migration` 无权限会拒绝启动；发布流水线以 `SPRING_FLYWAY_ENABLED=false` + 安装器单独迁移绕行（[RELEASE.md §4](runbooks/release.md#4-数据库迁移)），1a 的 migrate profile（已排入 B2）合入后安装器自动改用；「只增不删不改名」的迁移守护由 1a 在迁移基线 PR 实现。
- **运维命令**：首任超管与应急账号登记命令（C-5 的前提，ADR-0027 下一批）。
- **工作日历录入页**：B-7 与 C-6 日历导入目前只能调接口；PC 管理端尚无页面。
- **RDS SSL 与 CA 证书**：`sslmode=verify-full` 的前提（`DECISIONS_A.md` N-3）。
- **H5 子路径**：H5 构建的 `publicPath` 与 nginx 路径不一致（`CROSSCHECK.md` 第 4 条）。
- **Flowable 死信任务数指标**与定时任务失败指标确认（C-7 前）。
- **ICP 备案确认**：企业微信可信域名要求 `one.forcitis.com` 在公司主体下备案（B-2）。
- **ECS 数据盘快照策略与 SLS Logstore 保留期**：E-17 有，但清单 C 类未单列执行确认。
- **生产前移除样例模块** `cost_timesheet`、`delivery_execution`（C-9 前确认）。
