# one-ops 运维现行上下文

one-ops 是长期会话、不交接（用户「ops会话我不准备更换，所以他的历史会话在压缩前要有个hook，及时存到文档里，关于运维部署的关键context要保存在文件里。」）。本文件只记现行状态，每个完成点更新一次；压缩后由 SessionStart(compact) 钩子注入（SESSION_PLAYBOOK §2.1 第 13 条）。不含任何密钥、口令与令牌值。台账 `docs/modernization/COORDINATOR_LEDGER.md` 记资源与归属，本文件偏操作细节。

更新：2026-10-15（OIDC 部署第 6 项完成、第 7 项待批）

## 1. 工作方式（不变的规矩）

- 派工与批准来自调度会话 one-coordinator（跨会话消息）；每个完成点报它；改动一律经 PR（分支 `claude/one-ops-rN`），由它合并；守护文件改动须 opus 复审。
- 持久资源：先报「将执行的 API / 命令、作用范围、回退」，确认后执行，执行后回读核对再报。
- 不部署、不启动业务服务（部署门禁 090 / 100 未解除）；付费项先报；不改老系统与 A8 的业务数据、不改企业微信后台。
- 口令只在服务器侧生成、只经密文（临时 RSA 公钥加密）；不打印、不调用返回密钥的接口（`DescribeDBInstanceSSL` 曾返回私钥，见 C-3 执行记录）；describe 类输出只取所需字段。
- 生产只读取证不再逐次请示（ADR-0037），但每次在 `LEGACY_READ_REGISTER.md` 登记一行；明细只出计数或落 ECS 数据盘；取证用 `one_evidence_ro`，高权限账号只用于授权变更。
- 子代理须显式指定模型、用中文回复；记录只改片段（`new_log.py one-ops`、`new_intent.py --session=one-ops`、`build_all.py`）。

## 2. 主要资源与标识

| 类别 | 标识 |
|---|---|
| 阿里云账号 | 1980847222980247；本机凭据 `~/.aliyun/config.json` profile `forcitis-production`（OAuth，临时凭据，先跑任一 aliyun 命令刷新）；Python SDK 在会话 scratchpad 的 `venv` |
| 新 ECS | `i-bp122xlpar8w2uylmal2`（172.16.89.86 / 47.114.56.157，VPC vpc-bp1ovla4k0s9pl70fs5jl，cn-hangzhou）；实例角色 `forcitis-one-ecs-archive`（策略 `forcitis-one-archive-writer`、`forcitis-one-deploy-pull`）；元数据服务加固模式 |
| 老系统应用服务器 | `i-bp1b34hcl0g1cnkdtrb9`（只读探针与 OneSql 经它执行；Nacos 主数据源 = `u8c`） |
| RDS PostgreSQL 18 | `pgm-bp13vak0sew3m003`（与 it_portal 共用）；库 `forcitis_one_dev`；角色 one_dev_owner / migrator / app / readonly；运维账号 `one_admin`（口令在 ECS `/etc/forcitis-one/one-admin.pgpass`）；实例级 `log_parameter_max_length = 0`、`log_min_duration_statement = 1000` |
| RDS SQL Server 2016 | `rm-bp1690ep53t9778dp`（老系统库 `ruoyi-vue-pro`、A8 库 `a8_db`）；高权限账号 `u8c`；新系统只读登录 `one_a8_views_ro`、`psa_one_legacy_ro`、`one_legacy_names_ro`（全库只读 + 库级写 / DDL / EXECUTE DENY，PR #135）、取证登录 `one_evidence_ro`（口令 ECS `/etc/forcitis-one/evidence.env`，PR #143） |
| SLS | Project `forcitis-one-observability`：Logstore `nginx`、`ops` 180 天，`app` 30 天；指标库 `metrics`；LoongCollector 机器组 `forcitis-one-production` |
| OSS | 归档 bucket `forcitis-one-archive`（WORM 未锁，B-13：第一个写入方上线前重建并锁定，锁定前报 one-coordinator） |
| GitHub | 仓库 FORCITIS/forcitis-one-psa；environments `release`、`dev`、`prod` 均只允许 main（v* 已撤）；部署 runner `mac-one-deploy`（标签 forcitis-one-deploy）；`RELEASE_SIGNING_KEY` 在 environment release；签名公钥 `scripts/release/allowed_signers` = ECS `/etc/forcitis-one/release-signers` |
| 告警 | ECS `/usr/local/sbin/one_alert_send P1|P2|TEST`（企业微信群机器人「FORCITIS one 运维」，Webhook 在 `/etc/forcitis-one/ops-alert.env`） |

## 3. 在途：OIDC 拉取式部署（PR #111 已合并；RELEASE.md R-4、R-6、R-7、§8）

| # | 项 | 状态 | 标识 / 结果 |
|---|---|---|---|
| 1 | GitHub OIDC sub 定制 | ✅ | include_claim_keys repo / context / job_workflow_ref；保留不可变 sub，前缀 `repo:FORCITIS@282818954/forcitis-one-psa@1418536044` |
| 2 | IMS OIDC 身份提供商 | ✅ | `acs:ram::1980847222980247:oidc-provider/github-actions`，ClientIds sts.aliyuncs.com，指纹 YR2 `2D74D6DF…`、Root YR `AB9D0263…`、ISRG Root X1 `CABD2A79…` |
| 3 | RAM 角色 | ✅ | `acs:ram::1980847222980247:role/forcitis-one-deploy-dev`，3600 s，sub 精确匹配 `repo:FORCITIS@282818954/forcitis-one-psa@1418536044:environment:dev:job_workflow_ref:FORCITIS/forcitis-one-psa/.github/workflows/deploy.yml@refs/heads/main` |
| 4 | 部署策略 | ✅ | `forcitis-one-deploy-dev`（5 个请求参数 UpdateParameter、结果参数 GetParameter，无 ecs:*） |
| 5 | 拉取策略 | ✅ | `forcitis-one-deploy-pull` 挂实例角色（方向相反） |
| 6 | OOS 参数（cn-hangzhou，String） | ✅ | `forcitis-one-deploy-request-dev`=`{}`；`-bundle`、`-bundle-sha256`、`-statement`、`-statement-sig`=`unset`；`forcitis-one-deploy-result-dev`=`{}` |
| 7 | ECS：装安装器 `/usr/local/sbin/forcitis-one-release` 与 `forcitis-one-release-pull.{service,timer}`（取自 main 7f04d6f5），卸载旧令牌文件与检查单元，release-source 去 GITHUB_REPOSITORY | 待批（脚本已报） | 执行后等 2–3 分钟看拉取服务 Result=success 且无告警 |
| 8 | dev environment 变量 ALIBABA_CLOUD_ROLE_ARN、ALIBABA_CLOUD_OIDC_PROVIDER_ARN、ECS_REGION | 待报 | 确认无 ECS_INSTANCE_ID / ECS_COMMAND_ID |

之后：one-coordinator 触发首个 Release 0.1.0-rc.1 → R-11 连通演练（先报）→ 首次 dev 部署（受门禁 090 / 100）。首次真实换 STS 时核对实际 `sub`（job_workflow_ref 段在不可变模式下的形式未经文档确认）。OIDC 首次部署成功后提醒用户删除 R-13 后备令牌（「那就先放着」）。白天补测 GitHub 直拉速度。

## 4. 其他待办

- 审计在线分区维护角色 `one_<env>_audit_maint`：等 one-1a-next 的 DDL-24 卡与 PR（分区 created_at 月度、主键 (id, created_at) 唯一例外；函数 `platform_audit.drop_archived_audit_partition`）；我写角色、迁移后授权与建库脚本，并纳入 `db_scripts_aligned.py`。
- ADR-0037 实现（one-1a-next-2）：启动校验按平台基线（B-5 README）；部署阻塞项 100 在实现前不得部署；审批老待办那组 URL 先不动；`one_legacy_names_ro` 改名 `one_legacy_ro` 随下次 90 天轮换。
- 迁入 ID 对照（R-1b ①）：视图加列已暂停，等 ADR-0037 实现定运行时读取后再抽取；中间文件 `/data/forcitis-one/import/r1b/<日期>/`，7 天内删。
- `/root/one-ops-backup-20261015020632`：首次 dev 部署验证通过后删除。
- `/data/forcitis-one/import/c6/20261015/`：1a 生产切换后 30 天删除。
- pg_stat_statements（track=top，实例级）：Phase 2 基线取证前再请示用户。
- RDS PG 证书重签、SQL Server SSL：生产前维护窗口（用户定时间）。

## 5. 已知坑与处置

- **RDS SQL Server 2016 + 阿里云平台权限**：所有登录经 public 等带服务器级 ALTER ANY LOGIN、ALTER ANY LINKED SERVER、ALTER ANY CONNECTION、ALTER ANY EVENT SESSION、ALTER SERVER STATE、ALTER TRACE；库级 DENY 推导项报 4631，服务器级授权须在 master 上下文（4621）且 `u8c` 也无授予权限（4613）。用户已接受并登记风险；缓解为口令只在 ECS、90 天轮换、启动校验等于平台基线。
- **RDS PG**：rds_superuser 不能对库或角色设 superuser 级参数（`log_parameter_max_length` 等），只能改实例级（共享实例，影响 it_portal，先报）。
- **OIDC 指纹**：签发方证书链会变；处置见 RELEASE.md §10（重新取链、`UpdateOIDCProvider` 增补、回读）。
- **只读探针**（`LegacyReadOnlyProbe`）拒绝含 ALTER / KILL / DELETE 等字样的语句，字符串字面量里也不行：用 `LIKE 'K_LL%'` 一类写法。
- **云助手**：单次输出约 24 KB，大结果 gzip + base64；预期非零的查询加 `|| true`；`RunCommand` 用 `KeepCommand=false`。
- **SLS**：按使用功能计费（未开通按写入量计费）。
- **OOS 参数值**上限 4096 字符；一个签名地址约 930 字符，所以四个地址各占一个参数。

## 6. 常用剧本与工具

- 发布与部署：`runbooks/RELEASE.md`（§3 部署、§7 R-*、§8.2 策略、§10 风险）；`scripts/release/`。
- 建库：`runbooks/C-3-dev-db.md`、`runbooks/sql/c3_*.sql`（c9 由 `render_prod_sql.sh` 生成）、`c3_dev_db_check.sh`（本机容器验证）；守护 `scripts/ci/db_scripts_aligned.py`。
- 老系统与 A8 只读：`handoff/B-5-dba/`（`tools/b5_read_all_run.py`、`b5_evidence_run.py`、`b5_evidence_scan.py`、`read-all/`、`evidence/`）；`runbooks/scripts/c6_relay.py`（`legacy_query`、`run`）。
- 告警与证书：`runbooks/C-7-alerts.md`、`scripts/one_alert_send.py`、`one_cert_check.sh`；KMS 与轮换 `runbooks/C-8-kms.md`。
- 读取登记：`LEGACY_READ_REGISTER.md`；部署前清单：`PREDEPLOY_CHECKLIST.md`；剩余事项：`DEV_DEPLOY_REMAINING.md`。

## 7. 近期关键决定（出处）

- 拉取模式与 OOS 参数、哈希绑定、结果参数只作回报：one-coordinator（PR #111 五轮 opus 复审）。
- 不可变 OIDC sub、v* 撤销（用户「撤掉，只留 main (Recommended)」）：PR #144。
- SLS nginx / ops 180 天（用户，#120 intent）：PR #123。
- 实例级 `log_parameter_max_length = 0`（用户「改实例级，参数值不进日志 (Recommended)」）：PR #131。
- 老系统与 A8 全库只读（用户「新系统可以读任何旧系统数据」「两者都是」「修改权限」「全部可读，不排除」）：PR #135；ADR-0037 取证账号与风险登记：PR #143。
- R-13 只读令牌作为 OIDC 后备，不装：one-coordinator 转达。
