# Phase 1a 部署前清单

整理：psa-coordinator，2026-10-14。用途：dev 部署与 1a 出口验证之前，一次问清并跟踪所有需要用户决定、只能由用户或运维执行、或执行前须用户确认的事项。细节以 `INFRA_RUNBOOK.md`、ADR 与 `process/1a/contract.md` 为准；本清单只做汇总与状态跟踪。

**1a 修复批合并前禁止部署任何环境**（status/blockers/080）。下表的交接包与剧本只是准备，执行仍须逐项经用户确认。

状态：⬜ 未开始 · 🟨 准备就绪、待执行（决定单 / 交接包 / 剧本已备好）· ✅ 完成。时点：**dev 前** = 部署 dev 之前；**出口前** = 1a 出口验证之前；**生产前** = 生产切换之前。

配套文件：A 类决定单 `DECISIONS_A.md`；B 类交接包 `handoff/`；C 类剧本 `runbooks/`；与代码的核对 `CROSSCHECK.md`。

## A. 需要用户决定

| # | 事项 | 建议 | 时点 | 依据 | 决定单 | 状态 |
|---|---|---|---|---|---|---|
| A-1 | `one.forcitis.com` 的 DNS 解析与证书由谁办理、用哪种证书 | 阿里云 SSL 证书服务（与现有证书统一管理）；DNS 指向新 ECS 公网地址 | dev 前 | INFRA_RUNBOOK D-3、E-9 | [A-1](DECISIONS_A.md#a-1-oneforcitiscom-的-dns-与证书谁办哪种证书) | ✅ 改选 B：Let's Encrypt + 自动续期（用户 2026-10-15「同意」） |
| A-2 | A8 库与老系统库的只读视图、只读账号由谁建（对方 DBA 是谁） | 指定一名 DBA，按 `handoff/B-5-dba/` 执行；同一人负责 6a 前的 `mirror_*` 只写账号 | dev 前 | D-4、ADR-0025 第 5 条 | [A-2](DECISIONS_A.md#a-2-只读视图与账号由谁建dba) | 🟨 选项已定，人选待定 |
| A-3 | 超级管理员人选（至少 2 名）与应急账号持有人（至少 2 个） | 平台负责人 + 一名运维；应急账号绑定超管持有人 | dev 前 | ADR-0026 / 0027、intent 156 | [A-3](DECISIONS_A.md#a-3-超级管理员持有人-2-名与应急账号持有人2-个账号) | 🟨 选项已定，人选待定 |
| A-4 | 组织数据管理员、安全审计员、流程管理员的人选 | 组织数据管理员 1–2 名（HR 或行政）；安全审计员由内控 / 财务；流程管理员 1 名 | dev 前 | ADR-0026 D-11、ADR-0027、D-1a-61 | [A-4](DECISIONS_A.md#a-4-组织数据管理员安全审计员流程管理员人选) | 🟨 选项已定，人选待定 |
| A-5 | 告警接收：P1 企业微信群与短信接收人、P2 群、值班安排 | 新建「FORCITIS one 运维」群；P1 短信给值班人与平台负责人 | dev 前 | 平台约定方案 §4（告警通道）、E-13 | [A-5](DECISIONS_A.md#a-5-告警接收) | 🟨 选项已定，人选待定 |
| A-6 | 新 ECS 的云安全中心付费授权是否加购（现为免费版） | 生产前加购 1 台 | 生产前 | 用户「暂时用免费版」 | [A-6](DECISIONS_A.md#a-6-新-ecs-的云安全中心付费授权) | ✅ 已决定 |
| A-7 | dev 库在上线前迁出共用实例还是停用 | 生产切换前停用 dev 库，另在开发机或临时实例做开发 | 生产前 | ADR-0025 第 2 条 | [A-7](DECISIONS_A.md#a-7-dev-库上线前迁出还是停用) | ✅ 已决定 |
| A-8 | 企业微信消息收件人白名单：dev 白名单名单；生产 `WECOM_RESTRICT_RECIPIENTS=false` 的启用时点 | dev 只发给项目组成员；生产切换当日关闭白名单 | dev 前 / 生产前 | 1a 第二波报告 | [A-8](DECISIONS_A.md#a-8-企业微信收件人白名单) | 🟨 选项已定，人选待定 |

核对中新增的待决事项（H5 路径、服务账号口令到期、RDS SSL）列在 `DECISIONS_A.md` 末尾 N-1..N-3，由 one-coordinator 决定是否并入本表。

## B. 只能由用户或运维执行

| # | 事项 | 时点 | 依据 | 交接包 | 状态 |
|---|---|---|---|---|---|
| B-1 | 在本仓库运行 `/design-sync`：设计 token 与正本对齐、正本备份到本地（入口条件），以及首次同步（出口条件） | dev 前 / 出口前 | brief Phase 1a 入口与出口 | —（用户在会话中运行） | ⬜ |
| B-2 | 在企业微信后台新开自建应用（卡片按钮回调、扫码登录回调、可信域名） | dev 前 | SECURITY_INVARIANTS I-8（SEC-119）、D-1a-138、E-15 | [B-2](handoff/B-2-wecom-app.md) | 🟨 应用已建，可信域名已验证（2026-10-15 外部核验 `WW_verify_*.txt` 返回 200）；凭据已写入 ECS `/etc/forcitis-one/one.env`（0640 root:forcitis），ECS 上 `gettoken` 与 `agent/get` errcode=0（session-log one-1a-infra-b2）；待：其他出口发消息被拒（可信 IP）、登录冒烟与可见范围（dev 部署后）、本机凭据副本清理与 KMS |
| B-3 | 由 A8 管理员登记新系统的致远验票回跳地址（不替换老 SSO） | dev 前 | E-16、D-1a-40 | [B-3](handoff/B-3-a8-sso-return.md) | 🟨 |
| B-4 | DNS 解析 `one.forcitis.com`，证书申请 | dev 前 | A-1 | [B-4](handoff/B-4-dns-tls.md) | ✅ 解析与证书（2026-10-15 外部核验：A 记录 47.114.56.157，Let's Encrypt 至 2027-01-12，HTTP 301 到 HTTPS）；自动续期 `certbot-renew.timer` 每日两次、deploy hook 先 `nginx -t` 再 reload，`renew --dry-run --run-deploy-hooks` 通过（session-log one-1a-infra-b4）；待：ICP 备案主体核验、证书到期告警（C-7） |
| B-5 | 对方 DBA 建 A8 与老系统只读视图和账号（按 `SEEYON_VIEWS.sql`、`LEGACY_INBOX_VIEW.sql`、`LEGACY_ORG_NAMES_VIEW.sql`） | dev 前 | A-2、INFRA_RUNBOOK §4 | [B-5](handoff/B-5-dba/README.md) | 🟨 A8 部分完成（2026-10-15 one-ops-1a）：`a8_db` 的 `forcitis_one` schema 与 4 个视图、登录 `one_a8_views_ro`；`a8_20_verify` 全部符合，真实登录可读视图、10 项写入与越权读取全部被拒；口令只在 ECS `one.env`（`A8_DB_*`）；RDS 白名单组 `forcitis_one` 放行 172.16.89.86。老系统部分 STEP 1 排序规则冲突失败、未建任何对象，视图已修正待复核后重跑。待决：SQL Server 未开 SSL，`encrypt=true` 下应用证书校验会失败 |
| B-6 | 组织数据管理员在 A8 补齐「在职但无企业微信 ID」清单至 0（确无者逐个登记） | 出口前 | D-1a-35（出口条件） | [B-6](handoff/B-6-wecom-id-coverage.md) | 🟨 上线前预估（2026-10-15T01:33:35+08:00，经 B-5 视图、`SeeyonViewSql` 同一范围）：范围内 1,254 人、在职 304 人、在职无企业微信 ID **0**、在职共用企业微信 ID 0；以 dev 同步后的问题清单为准 |
| B-7 | 组织数据管理员录入 2027 年工作日历（节假日与调休） | 出口前（且每年 12 月 1 日前录入下一年） | D-1a-110..117 | [B-7](handoff/B-7-work-calendar-2027.md) | 🟨 |
| B-8 | 业务管理员（用户）确认各单据编号模板；凭证号模板另需财务确认 | 出口前 | ADR-0027、出口条件「编号规则经管理员确认」 | [B-8](handoff/B-8-numbering-templates.md)（四眼确认待 ADR-0027 下一批实现） | 🟨 |
| B-9 | 企业微信接口真实返回核对（`task_id` 重复的错误码、模板卡片更新） | 出口前 | 1a 第二波报告 | [B-9](handoff/B-9-wecom-api-check.md) | 🟨 |

## C. 由会话执行，执行前须用户确认（对外变更或购买）

| # | 事项 | 时点 | 依据 | 剧本 | 状态 |
|---|---|---|---|---|---|
| C-1 | RDS 扩容到 8 核 32 GB、ESSD 200 GB，开启存储自动扩容（购买） | dev 前 | ADR-0025 第 2 条、用户「扩容现有实例共用」 | [C-1](runbooks/C-1-rds-scale.md) | 🟨 |
| C-2 | RDS 开启删除保护；备份策略满足 RPO ≤ 5 分钟（日志备份）、RTO ≤ 1 小时；首次恢复演练 | dev 前（演练在出口前） | 平台约定方案 §5 | [C-2](runbooks/C-2-rds-backup-restore.md) | 🟨 已核对（2026-10-15）：包年包月实例不支持删除保护（API `OperationDenied.PayType`），已开自动续费；日志备份已开、保留 7 天，但约 2.5 小时上传一次，**按时间点恢复达不到 RPO ≤ 5 分钟**；改动可能产生费用，待用户决定；演练未做 |
| C-3 | 建库建角色：`forcitis_one_dev` 与 `one_dev_*`；按角色设连接数、超时与 work_mem | dev 前 | INFRA_RUNBOOK §2、ADR-0025 | [C-3](runbooks/C-3-dev-db.md) · `runbooks/sql/c3_dev_db*.sql` | 🟨 脚本已补 `cost_timesheet`；实例现为 4C16G、PG 18、`max_connections` 1600，现有库只有 `forcitis_one`（IT 服务门户）。**阻塞**：唯一高权限账号 `portal_migrator` 的口令不在任何服务器上，须用户提供或授权另建高权限账号 |
| C-4 | ECS 初始化 E-1..E-17（数据盘、运行时、systemd、nginx、TLS、Logtail、ARMS、出站白名单） | dev 前 | INFRA_RUNBOOK §3 | [C-4](runbooks/C-4-ecs-init.md) · `runbooks/scripts/` · `runbooks/files/` | 🟨 已做：E-1 数据盘 XFS（one-data）挂载 /data、nginx 1.30.4 与 TLS、静态根 `/data/forcitis-one/current/web`（当前为 bootstrap）、`forcitis` 系统用户与 `/etc/forcitis-one/one.env`（session-log one-1a-infra-b4 / b2）；2026-10-15 one-ops-1a 按发布流水线的目录布局补 E-3 `forcitis-migrate`、E-5（`/data/forcitis-one` 与 `releases` root:forcitis 750、nginx 穿过 ACL、`logs`/`tmp`、`/etc/forcitis-one/migrate` 700、`SPRING_FLYWAY_ENABLED=false`）、E-6 单元与 logrotate 已装（首次部署前不 enable）、E-14 安全组出方向白名单（其余出站拒绝）；`psql` 客户端已装。**待**：E-4 发行版源无 Java 25，须用户确认来源；E-11 Logtail、E-12 ARMS 涉及计费，未做；E-17 数据盘快照策略。**注意**：企业微信校验文件 `WW_verify_*.txt` 现放在 current/web，发布切换 current 时须保留（见 RELEASE.md） |
| C-5 | 应急管理员与首任超管开通（运维命令，双人在场） | dev 前 | INFRA_RUNBOOK §6、ADR-0027 | [C-5](runbooks/C-5-first-admins.md)（运维命令未实现，阻塞于 blockers/070） | ⬜ |
| C-6 | 老系统数据只读导出：老角色与权限（ADR-0026 D-9 已授权）、TIMESHEET 流程实例（回放用）、`psa_calendar` 2016–2026（迁入工作日历） | dev 前 | ADR-0026、TIMESHEET_REPLAY、D-1a-110 | [C-6](runbooks/C-6-legacy-export.md) · `runbooks/sql/c6_legacy_readonly_export.sql` | 🟨 |
| C-7 | 告警规则配置与 dev 演练（四类告警） | 出口前 | E-13（出口条件） | [C-7](runbooks/C-7-alerts.md) · `runbooks/files/alert-rules.yaml` | 🟨 另加：证书剩余不足 20 天告警（A-1 改选 Let's Encrypt 的前提） |
| C-8 | 配置与密钥从 `one.env` 迁到 KMS，确定轮换周期 | 生产前 | 平台约定方案 §6 | [C-8](runbooks/C-8-kms.md)（应用读取 KMS 需代码改动） | 🟨 |
| C-9 | 生产库 `forcitis_one_prod` 与 `one_prod_*` 角色 | 生产前 | ADR-0025 第 2 条 | [C-9](runbooks/C-9-prod-db.md) · `runbooks/sql/c9_prod_db*.sql` | 🟨 |

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

## 不阻塞 1a，但在途

- 第三轮用语确认（ADR-0018：阶段结束时不得留有会计类提议术语）——1a 代码下一批交付后启动。
- 财务确认单 v2 的答复（G1 开工前必须）。
- AG Grid 企业版许可采购（R1 前）。
- OAT 文件在 ddd-theory 提交后，`glossary/oat/README.md` 改引提交号。
- 老系统安全止血：用户决定不处理（「仍然完全不处理」），不阻塞 1a。

## 核对中发现、清单尚未列出的前置项（供 one-coordinator 决定是否立项）

- **发布流水线**：CI 只做 verify，不产出 jar / 前端 dist / SHA256 发布包（INFRA_RUNBOOK §7 第 1 步），首次部署前须补（代码会话）。
- **运维命令**：首任超管与应急账号登记命令（C-5 的前提，ADR-0027 下一批）。
- **工作日历录入页**：B-7 与 C-6 日历导入目前只能调接口；PC 管理端尚无页面。
- **RDS SSL 与 CA 证书**：`sslmode=verify-full` 的前提（`DECISIONS_A.md` N-3）。
- **H5 子路径**：H5 构建的 `publicPath` 与 nginx 路径不一致（`CROSSCHECK.md` 第 4 条）。
- **Flowable 死信任务数指标**与定时任务失败指标确认（C-7 前）。
- **ICP 备案确认**：企业微信可信域名要求 `one.forcitis.com` 在公司主体下备案（B-2）。
- **ECS 数据盘快照策略与 SLS Logstore 保留期**：E-17 有，但清单 C 类未单列执行确认。
- **生产前移除样例模块** `cost_timesheet`、`delivery_execution`（C-9 前确认）。
