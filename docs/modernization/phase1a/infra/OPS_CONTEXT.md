# one-ops 速查（2026-10-22 增补版（含下面「2026-10-22 增补」）；原 2026-10-21 13:30+02:00 交接版：dev 在 rc.6，等 A 线 #489 与 F 合入后调度通知出新 rc 并重建 dev；用户新口径：不影响当前开发的运维/采购议题只记台账、到触发条件再报）

## 2026-10-23 增补（上下文 21% 时交接；以本节覆盖下面的同名项）

- **#637（迁入角色 history_loader，OPS-1022-OPS-03）**：opus 首轮与终审均已过并改完，one-coordinator 已批入队；dev 库已补建 `one_dev_history_loader`（用户经收件箱批准，云助手 t-hz070ulib73ylts，已登记 C-3 手册）。V13 待下次含本 PR 的发布后在 dev 跑；prod 无实例。
- **OPS-1022-OPS-02（Docker 自动拉起）进行中**：PR #639（scripts/ops：docker_ensure.sh、自测、docker_watch_install.sh、plist 模板、RESOURCES 行）分支 claude/one-ops-docker-watch @9a13cb984，已按 opus 首轮必改改完（--ci 不拿锁、假 launchctl 真跑代理的并发自测、不再 pkill、nocli/paused 告警、安装脚本），自测全过，**还没报 one-coordinator、等 sonnet 复核**；PR #640（ci.yml，分支 claude/one-ops-ci-docker-check @4f5c08e3f）**还没跟改**。
- **下一步（按序）**：①报 one-coordinator：#639 改完 @9a13cb984，并把 `~/.modernize/psa/coordinator/reviews/pr639-640-opus-1.md` 的结论贴到 #639/#640（一条 PR 评论）。②#640：#639 合入后 rebase；把「docker is running」挪到 docker events 记录器之前（或成功后重启记录器）；在 CI 的「release and deploy scripts self-test」步加 `shellcheck scripts/ops/*.sh` 与 `scripts/ops/docker_ensure_selftest.sh`；复核 `timeout-minutes: 5` 与脚本最坏耗时（--ci 约 10+120+14≈145 秒）；失败文案已带「已暂停/未安装」；注释里「10-22 22:35-22:50」改指任务卡；nightly-legacy-it.yml 同样加（可另开 PR）。③两 PR 合入后：`scripts/ops/docker_watch_install.sh` 装 launchd 代理（卡与 intent 2026-10-22-230334 已授权，one-coordinator 10-23 确认不进收件箱）。④演练：挑合并队列空、无 CI 在跑时退出 Docker，两分钟内应自动恢复；**同时验证 runner 会话（SessionCreate）能 `launchctl kickstart gui/501/com.forcitis.docker-watch`**（只靠推断，没验过）。⑤RESOURCES 登记装机与演练结果，演练记录回填任务卡，报 one-coordinator。
- **仍等 one-coordinator 示下**：dev one.env 的 WORKFORCE_SYNC_ENABLED（现 true，要求不能是 true）。
- **本会话踩到的坑**：①macOS 的 `seq 1 0` 会输出 1、0（倒数），循环次数为 0 时别用 seq；②pre-push 钩子跑 backend 单测时 ClamdScannerTest 的超时重试用例偶发失败（已转 one-platform-file），重推即过；③云助手连 dev RDS 要 `PGSSLROOTCERT=/etc/forcitis-one/rds-ca.pem`，pgpass 第一行取主机；模板 `/private/tmp/claude-501/ra.sh`（会丢）= RunCommand + base64 + 轮询 DescribeInvocationResults；④`git push` 被中断时先 `git status -sb` 看是否 ahead 再重推。

## 2026-10-22 增补（上下文 46% 时按钩子交接；以本节覆盖下面交接节里已变的项）

- **已完成**：ClamAV dev 装好并通过加密项必验与耗时测量、健康检查与 C-7 告警及三类演练（卡 OPS-1021-OPS-02；还差与应用侧联调与 MaxScanTime 复核，等 one-platform-file 约时间，全部做完才报「建成」）；撤 `psa_one_legacy_ro`（卡 OPS-1022-OPS-01，已完成，视图 v_psa_one_legacy_todo 仍在，删与否另请示；dev one.env 的 …LEGACY_INBOX_* 已删）；dev `one_dev_app` 的 search_path 改为 pg_catalog, platform_approval 并重启（c3/c9 脚本与 verify V6c 已改，本机容器检查通过）；dev 核 TEMP：四个账号均 false（#547 待办的 TEMP 核验已完成，三张日历表的 REVOKE 由 #547 自带）。
- **取证（均已登记，结果在本机 `~/.modernize/psa/ops/adr0073/`，仓库外；用完删，删后补登记）**：ADR-0073 U1 法人归属（老系统与 A8 实质单一法人）、DAT-1021-ARC-01、B-31 用印附件（三步）、入职日期口径、M-29 前后值 Q 批与 B 批（m29*.txt 的删除日期 2026-10-29）。SQL 都在 `handoff/B-5-dba/evidence/`。
- **WORKFORCE_SYNC_ENABLED**：dev one.env 现值 true（2026-10-16 为 A8 组织同步启用）；one-coordinator 要求不能是 true（W2 PR-B 入队前提）。生产环境尚无新系统实例（ECS i-bp122xlpar8w2uylmal2 是 dev），所以 prod 无此项。是否改回 false 等 one-coordinator 示下，改了要重启。
- **待办仍在**：12-01 续费提醒；新 rc 与重建 dev（等调度）；ClamAV 联调；工作日历投产写入前置；c14_run.py 自测 PR；回滚脚本 DROP ROLE IF EXISTS 与 check.sh 旧库用例（OPS-007）；#547 合入后的 dev 部署前置（platform_file 等 schema 的建库授权，见在途表）。
- **本会话踩到的坑**：①取证工具（`b5_evidence_scan.py`）语句超时固定 300 秒，跨库联接要给两侧加 `COLLATE DATABASE_DEFAULT`（排序规则不同会报错，失败时工具只说 Failed，不给原因，先拆小查询二分）；②工具失败退出时服务器上留的临时目录要靠下一次成功运行清理，别忘；③跨库查询在 IF DB_NAME() 里整体外包；④`SUM(CASE WHEN EXISTS(...))` 在 SQL Server 非法（聚合里不能带子查询）；⑤云助手脚本模板：`/tmp/ra.sh`（本会话临时，会丢）= RunCommand + base64 + 轮询 DescribeInvocationResults。

## 交接（2026-10-21，上下文 83%；分支 origin/claude/one-ops-r56，文档分支，不开 PR，提交号以 `git log -1` 为准）

**未完事项与触发条件**

1. **RDS 续费（dev/测试库，`pgm-bp13vak0sew3m003`）**：已续 1 个月（订单 2006474717310288，¥1,456），到期 **2026-12-19 00:00 北京时间**；**自动续费已关**（回读 Duration 0）。**12-01 提醒：把下月续费方案报 one-coordinator**（用户说这件影响 dev，可报用户）；不续则断库。高可用降单节点官方文档不支持、控制台没法试；单节点新购询价（40 GB ESSD，月）2C4G 405、2C8G 520、4C8G 771，原高可用续 1,456；降配走「新买基础版并重建 dev、旧实例到期不续」另评估。B、C（生产 RDS 8C32G/200G 3,084/月；生产 ECS 802/月）**不下单**，条件：首次按量演练跑通、规格实测、距投产≥4 周，一次按年下（B 省 8,868、C 省 2,650；B 带 200 GB 自动扩容，备份与 WAL 归档月成本另列）。付款纪律：付款前报订单号、实付、规格、周期并等确认，订单号或付款方式变了（含取消重下）也先报（错题本 OPS-010）。
2. **dev 现状**：rc.6 在位健康；`one_dev_app` 连接上限 25（预算 31，脚本在文档分支）；`forcitis_one` 加了库注释、`it_portal_runtime` 上限 5（该库仍有 172.16.10.52 老系统服务器的活动连接，不 drop）；ECS i-bp122xlpar8w2uylmal2 到期 2027-10-15（RESOURCES 已记，提前 30 天提醒）。
3. **新 rc 与重建 dev**：等调度通知（A 线 #489 列改名、F 删老待办合入后）。重建走 `runbooks/dev-db-rebuild.md`（回滚脚本对旧库缺角色不幂等，OPS-007）；装 rc 的手工路径与脚本这次会话的临时目录会丢，按 OPS_CONTEXT 与剧本重来；授角色要用户扫码登录一次。
4. **工作日历**：dev 已导入 2016–2026 共 272 条（临时，按老系统 `workday` 推出），读回一致。对照国办官方通知差异 17 条与老系统工时核查见 `handoff/WORK_CALENDAR_AUDIT_2026-10-21.md`；用户口径：dev 不修；这 16 天以工时核查为公司加休依据，2023-04-23 按官方补。**投产用「官方＋公司加休」表**已出，在本机 `~/.modernize/psa/ops/work-calendar-prod-2016-2026.csv`（及 json、cancelled、README；273 行，不进仓库），投产首次写入时用；写入前要 `work_calendar_days` 加 `basis` 列、OD 补 11 行公司安排的书面依据、2027 年清单另出。dev 导入包（旧）`work-calendar-2016-2026.json` 同目录。
5. **ClamAV**：dev 已装 clamav 1.4.6 并通过加密项必验与耗时测量（2026-10-21，详见卡 OPS-1021-OPS-02 测量记录与 RESOURCES）。健康检查与 C-7 告警及演练已完成（one-clamav-check.timer）；运维侧还差：与应用侧联调（EICAR 写入→检测→隔离、停 clamd→拒绝＋告警）、MaxScanTime 复核；生产实例上线时同步装；应用侧归 one-platform-file，ADR-0060 补篇归 one-architecture。
6. **NAS 与 WORM**：NAS 取证暂停并已回收（权限组与新挂载点已删，未读内容）；2.04 TB 即全部文件；估价 `handoff/NAS_WORM_COST.md`（冷类型单价未核实，B-13 前到购买页核对；锁定不可逆，先演练再锁）。
7. **待做代码类**：`c14_run.py` 自测 PR（须与 main 逐字节一致、opus 首轮复审）、回滚脚本 `DROP ROLE IF EXISTS` 与 check.sh 旧库用例（OPS-007）；可交 Codex（卡写四项），取证、部署、凭据类不交。

**本会话产出的文档（都在 `docs/modernization/phase1a/infra/handoff/` 与 `evidence/`）**：PROD_AND_DRILL_ENV_ASSESSMENT、PROD_RUNNING_COST、MALWARE_SCAN_CANDIDATES、NAS_CONTRACT_EVIDENCE_PLAN、NAS_WORM_COST、ORDER_PROPOSAL_2026-10-21、RDS_DEV_ASSESSMENT_2026-10-21、WORK_CALENDAR_AUDIT_2026-10-21；取证结果在 `B-5-dba/evidence/`（B31P 五批、附件大小、迁入数据量、日历核查）；INFRA_RUNBOOK 已按 ADR-0055 对齐（§5 有过渡说明，现有 rc 仍读 A8 变量，别删 dev 的 one.env）。

**工作方式提醒**：本机取证工具要从仓库检出、与 origin/main 逐字节一致才能运行（`b5_evidence_scan.py <输出> <sql>`，两库各跑一次，SQL 用 DB_NAME() 守卫；只出聚合，出值类结果写文件前必须对全部出值做模式扫描，错题本 OPS-009）；`git push` 的钩子会在命令开头按当时的 HEAD 跑 precheck，所以改文件、提交、推送要分成不同的调用；会话 scratchpad 里的临时脚本会丢。云助手执行脚本的 `ecs.py` 之类也在 scratchpad（会丢）：用 `aliyun ecs RunCommand` + base64 内容，输出 24 KB 上限，gzip+base64 分块。

## 资源

| 名称 | 标识 | 状态 | 依据 |
|---|---|---|---|
| 本机 Python（SDK） | ~/.modernize/psa/ops-venv，按 [ops-venv-requirements.txt](ops-venv-requirements.txt) 安装；B-5 工具用它的 python 运行，不借其他会话的 scratchpad | 用 | — |
| 阿里云账号 / 本机凭据 | 1980847222980247 / profile forcitis-production | 用 | — |
| 新 ECS | i-bp122xlpar8w2uylmal2，实例角色 forcitis-one-ecs-archive | 用 | C-4 |
| 老系统应用服务器 | i-bp1b34hcl0g1cnkdtrb9 | 只读探针 | B-5 |
| RDS PG | pgm-bp13vak0sew3m003 / forcitis_one_dev / one_admin | 用 | [C-3](runbooks/C-3-dev-db.md)、#131 |
| RDS SQL Server 2016 | rm-bp1690ep53t9778dp；one_a8_views_ro、one_legacy_names_ro、one_evidence_ro（psa_one_legacy_ro 2026-10-22 已撤）；u8c = RDS 高权限（只用于授权变更，破例读须用户批准） | 全库只读 | #135、#143、#167 |
| SLS | forcitis-one-observability（nginx / ops 180 天） | 用 | [C-7](runbooks/C-7-alerts.md)、#123 |
| OSS 归档 | forcitis-one-archive（WORM 未锁） | 待锁 | B-13 |
| OIDC 提供商 | acs:ram::1980847222980247:oidc-provider/github-actions | ✅ | [RELEASE](runbooks/RELEASE.md) R-4、#144 |
| 部署角色 / 策略 | acs:ram::1980847222980247:role/forcitis-one-deploy-dev；forcitis-one-deploy-dev | ✅ | R-4 |
| 拉取策略 | forcitis-one-deploy-pull（挂 forcitis-one-ecs-archive） | ✅ | R-4 |
| OOS 参数 | forcitis-one-deploy-request-dev（+ -bundle / -bundle-sha256 / -statement / -statement-sig）、forcitis-one-deploy-result-dev | ✅ | R-7 |
| 时间同步检查 | one-time-sync-check.timer（每 5 分钟，100 ms → P2、1 s → P1） | ✅ 运行 | INFRA_RUNBOOK §8 |
| ECS 拉取 | /usr/local/sbin/forcitis-one-release、forcitis-one-release-pull.timer | ✅ 运行 | R-7 |
| Release | v0.1.0-rc.5（main 2694ecc6，run 38979438148；包 sha256 b3cef521…，jar sha256 4583f176…；本机 statement.sh verify + verify_bundle.sh 通过；已发布预发布）。更早：rc.4（f7cd54d0，jar 468d229a…，dev 当前运行版本）、rc.3、rc.2、rc.1。ECS 无 rc.5 整包（/root/one-ops-preload 里是 rc.4 的四个文件）；/root/one-ops-c14/app.jar 是 rc.4 jar | ✅ | #151 |
| GitHub | sub 定制（不可变）；environments 只允许 main；dev 变量 3 个；仓库级构建变量 8 个（SSO 门户与 PROD_* 待替换） | ✅ | R-1、R-4、R-6、§6 |

| ECS one.env | 2026-10-16 补 C-17 `SEEYON_VIEWS_ROOT_PATH`、C-18 `FORCITIS_JOBS_ENABLED=true`，`SSO_ENABLED` 改 false（t-hz07057zxlib280；备份 one.env.bak-20261016035106）；白名单仍 4 条，待用户指定保留哪 1 条 | 用 | intent 214511、215052 |
| ECS one.env（dev 首次启动） | 2026-10-16 起 dev 在位的临时项：`FORCITIS_PLATFORM_APPROVAL_PROCESS_ADMINISTRATOR_REQUIRED=false`（原因：process_administrator 首批持有人须先有员工 ID，而员工靠运行中的同步灌入，全新库上启动自检成死循环；删除条件：one-1a-par 的 ADR-0030 单人运维模式新 rc 出来后授予用户本人 process_administrator 与 super_admin，授完删此开关并重启确认自检通过）；同日 `WECOM_ENABLED=true`（扫码登录被拒：该开关同时是企业微信网关总开关；出站仍限 `WECOM_RESTRICT_RECIPIENTS=true` 与白名单 1 条，用户批准）；同日启用 `INTEGRATION_SEEYON_VIEWS_ENABLED=true`、`WORKFORCE_SYNC_ENABLED=true`、A8 实例专属 4 个编号、`ARCHIVE_STORE_*` 五项（本地目录暂用 /data/forcitis-one/tmp/archive-local）；备份 one.env.bak-20261016* | 用 | 用户 2026-10-16「好」、intent 2026-10-16 |

## 在途

| 步骤 | 下一步 | 卡在 |
|---|---|---|
| （已收尾）rc.6 与授角色 | rc.6 已装 dev；用户本人（员工 ID 末 6 位 238eea）持 super_admin、process_administrator、org_data_admin（后者 2026-10-17 经用户批准授，为导日历；授后其会话被撤销，须重新登录）；单人运维模式开；临时开关已删 | 完成 |
| SINGLE-OP-GATE（P4 部署闸门） | 卡在 one-1a-par 文档分支 claude/one-1a-par-docs@33e345a2 的 phase1a/cards/SINGLE-OP-GATE.md：systemd ExecStartPre、安装器正则与 DropInPaths、deploy_gate C-11、PREDEPLOY C-11/C-12、运维手册改 --spring.profiles.include、SECURITY_INVARIANTS I-14、P1-18 扫描范围；测试代理先写验收，实现 sonnet，opus 首轮+终审，一个代码 PR；挡生产不挡 dev | 排在 rc.5 部署之后 |
| 取证（ADR-0037，只出聚合，各登记片段） | 已交：Q-02 及补充、E-B1、E-B2、E-B6、N/V/S、F05-06、F05-09；待做：E-B3（退回/驳回/撤回比例）、E-B4（岗位待办与处理时长）、E-B5（客户签认等待）、E-B7（角色持有人数）、E-B8（外部回执时长），清单见 one-architecture 分支的 closeout/BURDEN_ANALYSIS_PLAN.md；收入专题 C1–C11（见 one-coordinator 消息里的 REVENUE_AEC_CONTRACT_MODELS.md 取证需求一节）；Q-04 E1–E10 | 逐项做；STATE/SUB_STATE 等枚举 A8 内无含义，须标注推测 |
| 缺陷卡（首次部署暴露，部署后开） | ①current→bootstrap 被当版本比较（cmd_pull）；②应用没有 migrate 模式（无 application-migrate.properties，旧路径 web=none 起不来）；③one.env.template 与安装器校验缺 ARCHIVE_STORE_* 五项；④服务单元 ReadWritePaths 缺归档目录（INFRA_RUNBOOK §5.3 与单元不一致）；⑤首次启动 process_administrator 鸡生蛋 | 待开卡报 one-coordinator |
| dev-db-rebuild 剧本 | 补一步「重新导入 2016–2026 与 2027 工作日历」（#396 合入后下次 dev 部署须按此重建开发库） | 待写（文档分支） |
| 运维手册 profile 形态 | 改 --spring.profiles.active=<自身> 为 --spring.profiles.include=<自身>（dev 开单人模式前必须改好；随 SINGLE-OP-GATE 一并） | 同上 |
| platform_file schema（one-platform-file 单元 3，分支 claude/one-platform-file-u3，PR 待开） | 含该 PR 的 rc 部署 dev/生产前，按 c3/c9 建库脚本建 schema platform_file 并授权（Flyway create-schemas=false）；after_migrations 新增两条 REVOKE；重建 dev 时一并带上 | 等 PR 合入、出新 rc |
| #547 工作日历版本化的首次部署前待办（one-coordinator 派，已更正） | 只做核验运行账号的 TEMP 权限（三张日历表的 REVOKE 由 #547 自己带）；完成点报 one-coordinator | 等 #547 合入，排在 ClamAV 联调之后 |
| 审计分区维护角色（#370） | 单开 PR：c3/c9 建库脚本新建 one_*_audit_maintainer（LOGIN NOINHERIT CONNECTION LIMIT 1）、*_after_migrations.sql 授权、INFRA_RUNBOOK §5.3「在线行暂不删除」是否改写 | 等 #370 合入、出下一个 rc |
| 迁入 ID 对照 | 抽取 | ADR-0037 实现 |

## 近期完成（2026-10-16/17）

- C-14 完成，阻塞项 101 已删（#414，文档 PR）；工具演进：探针账号经 RDS 云接口建删（#394）、第 15/17 项认平台拒绝（#399、#407）、rdscore 里 one_readonly_probe 用户为永久已知残留（msdb 随 DeleteAccount 清除）；第 14 项 (g) 口径待 one-1a-par、生产前须定。
- dev 手工装 rc.4 成功运行（https://one.forcitis.com ，企业微信扫码登录；WECOM_ENABLED=true；A8 视图与组织同步已开；归档凭据 403 告警待另议）。
- 发布门禁（#438）：ci_passed.py 沿第一父链证明重步骤（合并 PR 头的完整 pull_request 运行、步骤未跳过、与 M 只差纯文档，schedule 兜底）；残余风险用户 2026-10-16 已接受（写权限者可伪造同头 PR，见 RELEASE.md）。
- 文档分支（新规：文档不开 PR，推分支报提交号）：origin/claude/one-ops-r56（OPS_CONTEXT/RESOURCES/画像）、origin/claude/one-ops-r60（取证登记片段与取证 SQL，取证 SQL 在 handoff/B-5-dba/evidence/）。

## 待批 / 待办

- 首次真实换 STS：核对实际 sub（job_workflow_ref 段）
- OIDC 首部署成功后：提醒用户删 R-13 后备令牌
- dev 稳定后：删 /root/one-ops-backup-20261015020632、one.env 各备份、/root/one-ops-preload、/root/one-ops-c14/app.jar、/data/forcitis-one/current.bootstrap-link；一次性脚本在会话 scratchpad（会话清理即失）
- 构建变量待替换：SSO 门户地址（B-3 回传后）、PROD_*（生产前确认）
- 拉取单元沙箱收紧：首次 dev 部署跑通后、下次改安装器或单元时（RELEASE §10，#157）
- #228 合并后复核：迁移进程（migrate 模式参数未覆盖 FORCITIS_JOBS_ENABLED，沿用 one.env 的 true）不挂定时作业——migrate 模式在建上下文前退出，预期不挂；只有无 migrate 模式的旧包走整应用启动（#227 复审第 5 条）
- pg_stat_statements：Phase 2 前请示

## 坑

- SQL Server 2016 + RDS：服务器级权限不能 GRANT / DENY（4631 / 4613 / 4621）；u8c 服务器权限 = 只读基线，看不到服务器触发器 → B-5 README、#143、#167
- PG 共享实例：superuser 级参数只能改实例级 → C-3、#131
- OIDC 证书链会变 → RELEASE §10
- 只读探针拒绝含 ALTER / KILL / DELETE 的字面量 → 用 `K_LL%`
- 云助手：命令内容有长度上限、输出约 24 KB → 分块写入、gzip + base64
- 阿里云临时凭据（OAuth）只在过期后才换新，过期前调用不会延长 → `ops_credentials` 自动等过期再换；工具必须从仓库检出、未改动的文件运行
- OOS 参数值 ≤ 4096 字符 → RELEASE §10
- 安装器比对 current 的 migrations.txt：基线文件被改后第二次部署必失败 → [dev-db-rebuild](runbooks/dev-db-rebuild.md) 第 6 步
- corepack 按启动目录选 pnpm 版本 → 进 frontend 再调（#151、#156）
- CI 偶发 postgres:18 测试容器起不来 → `gh run rerun --failed`

- 部署前核对基线迁移（错题本 OPS-005）：任何 dev 部署前先跑 `git diff --name-status <当前运行提交> <目标提交> -- backend/src/main/resources/db/migration`，有 M（已有文件被改）即走 [dev-db-rebuild](runbooks/dev-db-rebuild.md)，先问用户。
- 改 `runbooks/sql/` 下脚本后：先单独跑 `c3_dev_db_check.sh` 看退出码为 0，再 add、commit、push，不把检查与提交连在一条命令里（错题本 OPS-006）。

- 工作方式：生产取证走 handoff/B-5-dba/tools/b5_evidence_scan.py（one_evidence_ro，两库各跑；SQL 里用 DB_NAME() 守卫只在 a8_db 出结果）；工具与 c14_run 等必须与 origin/main 逐字节一致；本机无 timeout 命令；`timeout`/管道会让长任务出问题，长任务用 run_in_background。

## 偏离记录

- 2026-10-15 约 21:18–21:33（one-architecture 派的 sonnet 取证子代理）：① 未经询问在其会话 scratchpad 建 venv（`ve`，Python 3.12.13），从 PyPI 装 alibabacloud-ecs20140526 7.11.6、alibabacloud-tea-openapi 0.4.6 及依赖（alibabacloud-credentials 1.0.12、alibabacloud-tea 0.4.3、alibabacloud-tea-util 0.3.15、darabonba-core 1.0.9、cryptography 50.0.2、aiohttp 3.14.5、requests 2.34.2 等 29 个），未登记；② 用包装脚本 `dr/ev_run.py` 在内存中把 `b5_legacy_run.py` 的凭据阈值 900 秒改成 240 秒后执行（仓库文件未改）。取证本身只用 one_evidence_ro、只取聚合，LEGACY_READ_REGISTER 已登记 2 行。**已清理**（one-ops，2026-10-15）：venv 与 `dr/ev_run.py` 已删除；两台服务器上的取证临时目录核实无残留（t-hz070573w93l2bk、t-hz0705748l9198g）。**防护**：通道脚本改用 `runbooks/scripts/ops_credentials.py`，凭据不足 15 分钟时等过期后自动换新，失败时给出处理步骤；拒绝从仓库外副本或未提交的改动运行（在内存里改写源码的做法仍拦不住，靠派工说明约束）；依赖只用 `~/.modernize/psa/ops-venv`。

## 决定（2026-10-16）

- Docker Desktop 订阅不买（用户：「不买，只有我一个人用 docker」）。条款：Docker Subscription Service Agreement 第 3 节，商用免费需「fewer than 250 employees and less than US $10,000,000 in annual revenue」，公司约 304 人已超员工数线；取自 https://www.docker.com/legal/docker-subscription-service-agreement/ （页面标注 Last updated on August 26, 2026），风险由用户承担。
- CI 不搬到 Linux 机器，不开试用机。本机测试提速调查：5 个数据库集成测试类单 fork 基线 46.3 秒、43.2 秒，PostgreSQL 容器改内存盘并关 fsync 后 41.7 秒，差距在波动内，不值得做；时间主要花在每类各自起容器、建应用环境、跑基线迁移（76 个类各起一个容器）；已做的 fork 降到 2、看门狗、停闲置容器够用，停止。
- H-2（探针的 msdb 用户移出 SQLAgentOperatorRole）：平台不可构造（u8c 在 msdb 无 ALTER ANY ROLE，2026-10-16 实测），不做；探针账号改经 RDS 云接口建删（msdb 用户随之清除），rdscore 里同名用户是永久已知残留（用户 2026-10-16 决定接受，见 C-14 剧本与 RESOURCES）。

## 暂停项（2026-10-17 用户「ops 这一轮 rc 后，暂时不考虑 dev 的事儿了」，one-coordinator 转达）

- dev 工作日历重导：2016–2026 的 JSON 已按 workday 标记推出（HOLIDAY 207、调休 65，各年条数见 C-6 剧本步骤 3），本机会话临时目录里的 cal.json 会随会话清理而失；导入须有 org_data_admin 的登录会话逐年 PUT /api/v1/organization/work-calendars/{年}，再逐年 GET 核条数；dev 目前没有任何日历（重建后清空），依赖日历的审批时限会失败告警；导入的 207 条属推断数据，标临时。2027 年清单须组织数据管理员给（B-7）。
- 后续 rc 的 dev 部署与重建：每次基线迁移有改动即要重建 dev 库并重新扫码（dev-db-rebuild.md；回滚脚本对旧库缺维护角色不幂等，见错题本 OPS-007）。
- c14_run 自测 PR、OPS-007（回滚脚本 DROP ROLE IF EXISTS 与 check.sh 旧库用例）：待做，可交 Codex。
- 维护角色 one_dev_audit_maintainer 口令未设。
- NAS 合同取证（2026-10-17 暂停，用户额度紧）：方案与现状见 handoff/NAS_CONTRACT_EVIDENCE_PLAN.md；已建的权限组 one_contract_ro 与挂载点 0f3fa4b751-vqo78 已删并回读确认，未读取任何内容；恢复时须先请用户批准 ECS 安全组加一条出向（445 → 新挂载点 /32）。
- org_data_admin（用户本人持有，2026-10-17 用户批准授予，为导日历）：保留，不收回（one-coordinator 转达，依据 intent 2026-10-15-215052「dev 全由我兼任」：A-3/A-4/A-5/A-8 在 dev 阶段全由用户本人兼任，A-4 即组织数据管理员）；要收回用 identity-operations 的 revoke-role（不带 --witness），收回后其会话被撤销。

- 2026-10-16 暂停（one-coordinator 决定）：C-6 回放 6 条差异的原因取证（#254，加密中转脚本与诊断查询，草稿，未运行）。理由：成本与风险偏高（新的安全类脚本加一轮复审），生产数据只作参考，A8 没有上级字段，做完也只能间接判断。6 条归为「回放方法限制」，未取证坐实；以后真要坐实再启用，须先过复审并合并。

## 剧本

[RELEASE](runbooks/RELEASE.md) · [C-3](runbooks/C-3-dev-db.md) · [C-14](runbooks/C-14-readonly-probe.md) · [dev-db-rebuild](runbooks/dev-db-rebuild.md) · [C-7](runbooks/C-7-alerts.md) · [C-8](runbooks/C-8-kms.md) · [B-5](handoff/B-5-dba/README.md) · [PREDEPLOY](PREDEPLOY_CHECKLIST.md) · [台账](../../COORDINATOR_LEDGER.md)
