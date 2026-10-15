# 后台任务与持久配置登记

SESSION_PLAYBOOK §2.1 第 9 条：定时器、本机 runner、云上资源与长期配置建立时在此登记一行，用完清理并删行。调度台账只放链接。

| 位置 | 项 | 性质 | 负责 | 清理 |
|---|---|---|---|---|
| ECS i-bp122xlpar8w2uylmal2 | /root/one-ops-backup-20261015020632（nginx 切换前配置与续期配置备份） | 临时（回滚用） | one-ops | 首次 dev 部署验证通过后删除（已同意） |
| ECS | /etc/forcitis-one/release-signers、release-source、github-release-token（空，OIDC 改造后删除）、one-release-token-check.{service,timer}（未启用，OIDC 改造后删除） | 长期 / 待撤 | one-ops | 随 OIDC 改造 PR |
| ECS | /data/forcitis-one/import/c6/20261015/（C-6 导出，root 0700） | 临时 | one-ops | 1a 生产切换后 30 天 |
| ECS | one-cert-check.timer、one-time-sync-check.timer（2026-10-15，t-hz07045fzz8xtkw）、LoongCollector（ilogtaild / loongcollectord）、/etc/forcitis-one/ops-alert.env（群机器人 Webhook，root 0600）、/etc/forcitis-one/one.env、one-admin.pgpass、migrate/ | 长期 | one-ops | — |
| 本机 Mac | 自托管 runner mac-one-1..4（~/actions-runners/one-N，LaunchAgent，用户 yeliang）与 mac-one-deploy（/opt/forcitis-one-runner，LaunchDaemon，用户 forcitis-deploy，pre-job hook） | 长期 | one-coordinator / one-ops | — |
| 本机 Mac | ~/.modernize/psa/infra/role_holders.md（人名，0600）、~/.modernize/psa/finance/FINANCE_CONFIRM_v3.html（0600） | 长期私有 | one-coordinator | — |
| 本机 Mac | ~/.modernize/psa/tools/mermaid-cli（@mermaid-js/mermaid-cli 11.4.2，锁版本；puppeteer 23.11.1 用 ~/.cache/puppeteer/chrome-headless-shell/mac_arm-131.0.6778.204）：架构图本机渲染（2026-10-15，SUPPLY_CHAIN S1） | 长期 | one-architecture | 不再生成架构 SVG 时删除该目录 |
| 阿里云 | SLS Project forcitis-one-observability（nginx、ops 180 天，app 与 metrics 30 天）、云监控 OS 指标、ECS 快照策略 forcitis-one-daily | 长期付费（约 40 元/月以内） | one-ops | — |
| 阿里云 | RDS 实例 pgm-bp13vak0sew3m003 参数 log_parameter_max_length -1 → 0（2026-10-15，共享实例，it_portal 慢日志同样不再带参数值；回退改回 -1） | 长期配置 | one-ops | — |
| SQL Server rm-bp1690ep53t9778dp | 新系统只读账号 one_a8_views_ro（a8_db）、psa_one_legacy_ro 与 one_legacy_names_ro（老系统库）改为库级只读加库级写 / DDL / EXECUTE DENY（2026-10-15；依据用户「新系统可以读任何旧系统数据」「两者都是」「修改权限」「全部可读，不排除」；回滚 B-5 read-all/*_rollback.sql；应用启动校验 I-1 待 ADR-0037 改） | 长期配置 | one-ops | — |
| SQL Server rm-bp1690ep53t9778dp / ECS | 取证登录 one_evidence_ro（a8_db 与老系统库各一用户，库级只读加库级写 / DDL / EXECUTE DENY）；口令在 ECS /etc/forcitis-one/evidence.env（root 0600），C-8 90 天轮换（2026-10-15，ADR-0037；回滚 B-5 evidence/evidence_rollback.sql） | 长期配置 | one-ops | — |
| 阿里云 | OSS bucket forcitis-one-archive（杭州、归档、本地冗余、私有、SSE-OSS、仅 HTTPS；WORM 未锁，第一个写入方上线前重建并锁定）、RAM 策略 forcitis-one-archive-writer、RAM 角色 forcitis-one-ecs-archive（已绑定 ECS） | 长期 | one-ops | — |
| GitHub（用户账号） | fine-grained 令牌 forcitis-one-release-dev-2026-10-15（FORCITIS 组织、仅 forcitis-one-psa、Actions / Contents / Deployments / Metadata 只读，到期 2027-01-13）——OIDC 方案的后备，未安装到任何位置；令牌值不经会话 | 后备 | 用户 | 首次 dev 部署经 OIDC 成功后由用户删除（one-coordinator 提醒） |
| GitHub | 仓库 OIDC sub 定制：include_claim_keys repo / context / job_workflow_ref，保留不可变 sub（2026-10-15 已执行；回退 PUT {"use_default":true,"use_immutable_subject":true}） | 长期配置 | one-ops | — |
| 阿里云 | IMS OIDC 身份提供商 github-actions（acs:ram::1980847222980247:oidc-provider/github-actions；ClientIds sts.aliyuncs.com；指纹为 Let's Encrypt YR2、Root YR、ISRG Root X1，2026-10-15 已执行；证书链变化的处置见 RELEASE.md §10；回退 DeleteOIDCProvider） | 长期 | one-ops | — |
| 阿里云 | RAM 角色 forcitis-one-deploy-dev（acs:ram::1980847222980247:role/forcitis-one-deploy-dev，信任 github-actions 提供商、精确匹配不可变 sub，MaxSessionDuration 3600；2026-10-15 已建，策略待挂；回退 DeleteRole） | 长期 | one-ops | — |
| 阿里云 | RAM 策略 forcitis-one-deploy-dev（挂 forcitis-one-deploy-dev 角色）与 forcitis-one-deploy-pull（挂实例角色 forcitis-one-ecs-archive，与 archive-writer 并存）：只对 5 个部署请求参数与结果参数各一方向（2026-10-15 已建；回退 DetachPolicyFromRole 后 DeletePolicy） | 长期 | one-ops | — |
| GitHub | environment dev、prod 部署分支策略撤去 v*（tag），三个 environment 均只允许 main（2026-10-15，用户「撤掉，只留 main (Recommended)」） | 长期配置 | one-ops | — |
| 阿里云 | OOS 参数（cn-hangzhou，String）forcitis-one-deploy-request-dev（`{}`）、-bundle、-bundle-sha256、-statement、-statement-sig（`unset`）、forcitis-one-deploy-result-dev（`{}`）（2026-10-15 已建；回退 DeleteParameter） | 长期 | one-ops | — |
| ECS | 安装器 /usr/local/sbin/forcitis-one-release 与 forcitis-one-release-pull.timer / .service（每次运行结束 1 分钟后再跑，读 OOS 部署请求；2026-10-15 已装并启用，首装取自 main 7f04d6f5，此后以签名发布包为准；旧令牌文件与检查单元已删；回退 disable --now 后删单元与安装器） | 长期后台任务 | one-ops | — |
| GitHub | environment dev 变量 ALIBABA_CLOUD_ROLE_ARN、ALIBABA_CLOUD_OIDC_PROVIDER_ARN、ECS_REGION（2026-10-15；非机密） | 长期配置 | one-ops | — |
| GitHub | environments release / dev / prod、签名私钥 secret（release）、v* 标签规则（仓库级，组织级待用户定）、immutable releases | 长期 | one-ops | — |
| 阿里云（计划，PR #111 合并后由 one-ops 经用户确认建） | OIDC 身份提供商 github-actions；部署 RAM 角色与策略 forcitis-one-deploy-dev / -prod（只改写请求参数、只读结果参数，无 ECS 接口）；OOS 参数 forcitis-one-deploy-request-<env> 及 -bundle、-bundle-sha256、-statement、-statement-sig，结果参数 forcitis-one-deploy-result-<env>；拉取策略 forcitis-one-deploy-pull 挂在现有实例角色 forcitis-one-ecs-archive 上——该实例角色承载归档写入与部署请求拉取两份最小策略（不改名、不重绑）；GitHub 仓库 OIDC sub 定制；不建云助手命令 | 长期 | one-ops | 停用部署时删除；实例角色只解挂拉取策略 |
| ECS i-bp122xlpar8w2uylmal2（计划，PR #111 合并后由 one-ops 装） | forcitis-one-release-pull.timer / .service（root，每分钟拉取 OOS 请求参数、单实例）；/var/lib/forcitis-one-release/{processed-requests,alerts-sent}（root 0600） | 长期（后台任务） | one-ops | 停用部署时 disable 并删除单元 |
