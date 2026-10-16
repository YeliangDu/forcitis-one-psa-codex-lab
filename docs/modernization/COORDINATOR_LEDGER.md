# one-coordinator 台账（速查）

只记现行状态，一行一项，过程与决定只放链接（SESSION_PLAYBOOK §2.1 第 13 条、§11）。新开会话、清空或压缩后由钩子自动注入（#205 合并后生效；未注入就手动读本台账）。更新：2026-10-16 21:45（one-coordinator）

## 作战图（派工前先看；SESSION_PLAYBOOK §11「战队协作」第 5 条）
| 主线 | 负责 | 当前 | 下一步 | 阻塞 |
|---|---|---|---|---|
| dev | one-ops / one-1a-par | rc.4 运行；用户已用企业微信扫码登录成功（WECOM_ENABLED=true，用户批准）；C-14 完成、101 已删；SINGLE-OP P1 #415 已合 | P2 → 新 rc → grant-role 用户本人（员工 ID 末 6cd894）process_administrator+super_admin → 删 PROCESS_ADMINISTRATOR_REQUIRED=false 开关重启 | P2 在写 |
| 流程与业务架构对齐 | one-architecture + 三组 | 阶段层：SD-01～10 已裁（#422）；对象层：主对象脚本提议、三组确认中；VS01-10 定独立流程「作出参投决策」（#428）；已派五件（#429）：支撑本段、配置补读关系、内部服务价值流评估、集成类型、业务/管理/支撑分类 | 数据到后刷新对齐图（scratchpad(e1ce474b)/align/refresh.sh 后以同 file_path+files 重发；流程栏按业务/管理/支撑分三行，归属实线、支撑虚线，集成单列） | — |
| 命名与用语 | one-architecture / one-glossary / one-quality | 规则在 PROCESS_METHOD_DRAFT §11、name_id_check.py；用户定「先规范再登记」（#430）：架构产物只用规范名，改名对照表与用语决策单合并交用户，认可后再登记、再定稿；对象名一律名词（结果或记录）；86 个对象未登记 | #431 拆分：审计报告与用语问题先合，提议稿改为规范名+原名别名后随页交用户；one-quality 写架构用语检查 | — |
| 确认页 | one-architecture | 7 页在收件箱，用户暂不作答，待对齐与命名后一次改版 | PAGE_IMPACT.md | 对齐完成 |
| CI 与合并 | 调度 / one-quality | 队列串行；merge_pr.sh 打 queued；#406（重步骤入队才跑）在队；合集 #411 #412 #405 在队 | #406 合入后核后缀 | — |

## 调度惯例（本日新增）
- 不向会话要排期、不向用户转报估算（记忆 no-schedule-relay）。
- 会话报完成点即 get_usage 兜底查用量（≥75% 准备、≥85% 立即在批次边界交接）。
- 对齐图 https://claude.ai/artifact/7M7q1KQTZ7XwaoXSELdETL 只读源文件原样副本（src/），不在页里做人工映射。
- 工作台 https://claude.ai/artifact/TXzKwZFyzVUNEXxQW9M16r ：sync.py 后 batch set board/{lines,cards,prs,meta}（版本号见 on-merge.txt）。

## 授权与边界
- 合并 PR、开会话、派子代理：调度自定（「合 PR 交给你审核，决定权给你」）。不替用户定：部署、生产变更、购买、对外账号、ADR 变更。
- 代码 PR 须有 opus 复审结论；改守护文件（ci.yml、scripts/ci、.claude、验收 / 迁移守护）的 PR 单列并要 opus 复审。
- 他人转达的用户原话先核实；用户决定当场记 intent（`new_intent.py --session=one-coordinator`）。全程中文。

## 会话
- 画像与当前状态见 `SESSIONS.md`（`build_all.py` 生成；画像 `sessions/<会话名>.md`，已结束的在 `sessions/_retired.md`）；实时情况看 `ListAgents`（SESSION_PLAYBOOK §2.1 第 16 条）。

## 开场核对
- `gh pr list`、`ListAgents`、合并队列（`pgrep -f mq_run2.sh`，须在仓库目录启动，gh 需仓库上下文）、`gh run list --workflow nightly-legacy-it.yml -L 1` 失败即开卡（#165 合并后生效）。

## 合并
- strict 串行队列：scratchpad 的 `mq_run2.sh`（常驻，队空等待，固定在仓库目录启动）读 `merge.queue`，`merge_pr.sh` 每个队首先取消其余在队 PR 的 CI → 同步 main（gh 无 workflow 权限时改本地合并经 ssh 推送）→ 等 CI（首次失败自动重跑一次）→ 合并；`CONFLICT<n>` 非生成文件退回作者，生成产物取一边后跑 `build_all.py`，推送前跑 precheck。
- 核对：`pgrep -f mq_run2.sh`，不在就在仓库目录 `nohup /private/tmp/claude-501/-Users-yeliang-Projects-FORCITIS-forcitis-one-psa--claude-worktrees-one-coordinator-888aff/57026514-d6ff-470a-b547-693585d7522a/scratchpad/mq_run2.sh >> /private/tmp/claude-501/-Users-yeliang-Projects-FORCITIS-forcitis-one-psa--claude-worktrees-one-coordinator-888aff/57026514-d6ff-470a-b547-693585d7522a/scratchpad/mq.log 2>&1 &` 重启；队列文件 `/private/tmp/claude-501/-Users-yeliang-Projects-FORCITIS-forcitis-one-psa--claude-worktrees-one-coordinator-888aff/57026514-d6ff-470a-b547-693585d7522a/scratchpad/merge.queue`（/clear 后 scratchpad 路径可能变，仍用这个绝对路径）。
- 队列：197 193 195 194 198 200 201 202 203 205 206 207。叠加链 #203 → #204 → #208：每合一个把下一个 `gh pr edit --base main` 再入队。暂缓：#96（ADR-0029）。#196、#199 已关（#206 取代）。
- #197 后端 CI 曾卡 54 分钟（Testcontainers 连 PG 认证读无超时），已取消重跑；PG 测试连接缺 socketTimeout，待开卡。
- 关键路径：#197 只读核实命令合并 → 通知 one-ops 出 rc.2 → C-14（用户已批准）→ 删 101；F-1 实现在 R-1b P5 后。
- Auto-fix 已由用户在 app 统一关闭，不再逐个关（intent 2026-10-16-220312）。

## 待用户
- 财务口径决策单（46 题，~/.modernize/psa/finance/FINANCE_DECISIONS.html，#176）：用户全盘定；9 条事实问财务；A1、A10 待审计师书面意见。
- 用语：用户「先等等，我没有同意所有用语」「所有用语检查下，acl用语单列，不能进入新系统模块」（intent 161339）：第三轮写回作废；one-glossary 全量 ACL 归类（四类）→ 我抽查 → 交用户；「来源*」改归过渡期集成术语（intent 161053）。决策页必须有提交步骤（05bf4394），门禁页固定链接 https://claude.ai/artifact/MwoqwkhAYq9CtomgaedwZ1。R-1b-0 门禁暂停。ACL 全量归类（670：领域 562、过渡期 60、长期 18、去外部名 24、待定 6）已抽查放行：原编号改「领域去外部名」、A8 长期还是过渡交用户问、代码搬迁与 ADR 冲突转 one-architecture；同一固定链接挂两张单（ACL 108 条 + 第三轮重做），one-glossary 补测提交流程后直接发用户。
- 架构：ADR-0039 防腐层边界用户已定（4 项全推荐，C1 只定方向，intent 164527），#207 复审通过入队。身份生命周期研究（入职→企业微信开通）交 one-architecture，#207 合并后、C1 实施前，结论交用户。FRAMEWORKS.md、METHOD.md（分支 claude/one-architecture-method）待用户审，批准后排 METHOD 第 5 节 6 项偏差。架构决策页（7 问）仍待发。
- 规则文档瘦身对照表；同轮问 CI 覆盖率门槛是否恢复。
- 生产前：第二名超管 / 审计员 / 组织数据管理员 / DBA 人选（role_holders.md，不入仓；dev 流程管理员由用户兼任）；C-11 关单人模式；CODEOWNERS；R-13 后备令牌待首次 OIDC 部署后删除；归档 WORM 首个写入方前锁定；RDS 扩容 C-1。

## 技术裁定在途
- OPS-PORT：运维命令真正不起 Web（结构性，one-1a-par，P2 合并后）；dev 前兜底 C-5 UnsetEnvironment（#202）。
- R-1b P2 复审修复中（kernel 新端口移出）；R-1b-0 集成层算出「判定是否一致」交 workforce，Source* 不出集成层。
- 只读核实命令 #197 合并 → 通知 one-ops 出 rc.2 → C-14。

## 部署阻塞
- dev 前（NEXT_BATCH_PLAN 第 8 节，ADR-0038）：090 / F-1（R-1b P5 后）；100 / #163 → 101（真实登录核实 + C-14，前提 rc.2）；F-12；DA-13；ADR-0034 实现与 DDL-17；O-1、S-1、D1 余卡、F-2、F-3、安全缺陷卡。
- 生产前：基线关闭（C-15）及形态 / 命名类重构（R-6、R-2、R-5、R-4、DA-3/10、DDL 分区等）。

## 风险登记（已接受）
- RDS SQL Server 平台使所有登录带 ALTER ANY LOGIN 等服务器级权限，无法 DENY → 口令只放 ECS、90 天轮换、启动校验等于平台基线（intent 2026-10-15-082245）。u8c 不是 sysadmin，ALTER ANY LOGIN 理论上能改它的口令；是否有拦截 ALTER LOGIN 的平台保护触发器：看不到、无法判定（u8c 破例只读：触发器 0 行，但其服务器级有效权限与只读基线相同，无 VIEW ANY DEFINITION / CONTROL SERVER）；u8c 属 processadmin。
- 新系统只读账号可读凭据列（用户「全部可读，不排除」）；凭据值不得进入新系统任何表、日志、事件、会话。
- CI runner 以用户本人账号运行（用户本次未选专用用户，SUPPLY_CHAIN S6）。
- 老系统安全（实例白名单 0.0.0.0/0 等）：用户「老系统先不管」，已移出待办。

## 本机与调度自管
- CI runner mac-one-1..4（~/actions-runners/one-N）与部署 runner mac-one-deploy（/opt/forcitis-one-runner）；main 分支保护 strict。
- 私有文件：~/.modernize/psa/ 下 infra/role_holders.md、finance/（问卷 v2/v3、财务答复、FINANCE_DECISIONS.html）、audit/、codex/、handoff/。
- 会话内定时任务：无（用户 2026-10-16 停掉上下文巡检，各会话批次边界自查）。

## 暂缓清单（用户定生产口径前不开工，intent「生产自成体系」#344）
- R-1b P6 老系统对照导入（one-ops）；WF-03（拆分 a）；AP-12、AP-18；新开以老系统为数据源的生产期功能卡；地基批与校准车道（等架构全貌，intent #380）；三组流程铺开（等流程设计方法批准）。

## 合并队列（18:05）
- 规则：SESSION_PLAYBOOK §11 第 13 条（#393）：代码逐个入队；文档每天中午、晚上打包合集；提议稿到里程碑再合；文档不开 PR、每会话在制代码 PR ≤1（2026-10-16 新规）；未报不入队。队列文件与脚本见「合并」节；合入后待办在 scratchpad on-merge.txt，下一合集候选在 next-docs-train.txt（357 320 339 355 358 389 393 286）。文档合集 #386（15 个）在队。
- 调度自检：合集推送前跑完整 precheck；答应「合入后通知」的挂 Monitor（每 30 分钟重挂）；开出的会话立即解绑顶层并改 one-* 标题；派工前查是否已有负责会话；对用户报「待答」前先核收件箱与页面 answers-submit。

## 排定后续（指针）
- 04:50 现状（以此为准）：
  - 合并：一律合集（`claude/one-coordinator-train-N`，只放合集或单个已复审 PR 进 `merge.queue`）；merge_pr.sh 已改为被取消的检查也重跑。今晚已合 #262 #263 #266 #274 #260 #276（P4）#272 #273 #267 #277 #278 等。队列空。
  - 代码线：P5 卡已定（三表照 ADR-0033 改 UUID 主键、基线文件 P5 删除；D-12 撤回），验收在写 → 实现 → opus。F-1 在 P5 后（one-1a-par）。
  - C-14：#277 (f) 已合；rc 等 one-ops RDS 只读实测第 1–7 项与 #242（加 H-2 探针一项、opus 复审）；rc 须带 P3b 键改名与指标标签改名。企业微信 task_id 待真机核对（部署后冒烟）。
  - 用户决定（本日）：H-2 选 A；会话通信六条与停定时巡检（intent 本日）；Docker Desktop 不买；Colima 试点已停并卸载。
  - 待用户：用语确认页 https://claude.ai/artifact/QPwiXzEFWCTqSGVCQRdK6J ；ADR-0046/0047 批准页 https://claude.ai/artifact/CtoezQ9CY25i9xmPWGkp1F ；Docker Desktop 是否重启（交换区紧）；全企业对象页 Q06；财务 F-05/F-10/F-11；CI 搬 Linux。
- 23:55 现状（以此为准）：
  - 合并一律走合集：调度在 scratchpad 建 `claude/one-coordinator-train-N`（文档与已复审代码 PR 合并，生成产物取一边后 build_all），只把合集号放进 `merge.queue`；合入后原 PR 自动标为已合并。CI 失败对半拆分。候选清单 scratchpad `next-train.txt`。已合：#262（文档 16 个）、#263（#234 #236 #243 #246 #251）。
  - 待组：#260（与 #251 冲突，退回 one-1a-next-4）；#242（one-ops 加 H-2 一项后 opus 复审再进）。
  - 用户决定：H-2 选 A（intent 234252；one-ops 另行核实「批准这一次」）——C-14 探针阶段对探针 msdb 用户试移出 SQLAgentOperatorRole；正式账号处理另报用户。
  - 撤销调度「范围复核调动来源由组织数据管理员办理」的裁定，以 ADR-0044 第 2 项第 6 条为准。
  - ADR 编号：0046 期间模型、0047 ADR-0016 决定 4 修订（视为同意作收入证据），one-architecture 起草中。
  - 待用户：全企业对象页 Q06 阶段类型；财务 F-05、F-10、F-11（用户转财务）；CI 搬 Linux 未答。
  - 轻量 CI：one-quality 核实现有 relevant_change 已覆盖，慢在 runner 排队，定方向 c 不改 ci.yml。
- 23:25 现状（以此为准）：
  - 合并改「文档合集」：纯文档 PR 不再逐个入队，由调度合成 `claude/one-coordinator-docs-train-N` 一个 PR 跑一次 CI 合入（用户采纳 PR 策略三条，intent 本日 PR 策略）；代码 PR 仍串行入队。合集 1 含 #233 #225 #229 #238 #244 #245 #247 #255 #249 #253 #256 #257 #261 #224 #258 #259；#252（one-ops，PREDEPLOY C-4..C-6 行冲突）退回作者。
  - 代码队列：226 234 236 243 246 251 260。文档 PR 只跑轻量 CI（跳过前后端重步骤）派 one-quality（改 ci.yml，守护单列，opus 复审）。
  - 用户决定：Docker Desktop 不买（intent 本日）；CI 搬 Linux / 试用机未答；Colima 试点在 mac-one-4（one-quality，样本够后出对比表）。
  - P4 卡 D-4：one-architecture 判定属 ADR-0033 分阶段落地，收件人列由 DA-10 必补（F-1 后、基线关闭前）；DA-10 排期记此。
  - C-14 (f)：opus 复审两个 High——H-1 msdb 直接授权与嵌套不设防（调度定补检查）、H-2 SQLAgent 角色固有能力（交用户知情确认，写入 ADR-0037 第 4 项 (f)）。
- 22:40 /clear 前现状（以此为准，较早条目可能过时）：
  - 合并队列：Monitor「合并队列」需重设；队列文件见「合并」节；mq_run2.sh 在跑；merge_pr.sh 已改为等待期间持续取消他人 CI（新版本是新 inode）。
  - 会话：one-1a-next-4（opus，接 next-3）、one-1a-par、one-ops、one-glossary、one-glossary-b1、one-rules、one-quality（以上 sonnet）、one-architecture（opus）。模型策略见 SESSION_PLAYBOOK §5（#253）。
  - dev 首次部署剩：C-14（(f) 纳入基线 + 本人拥有 Agent 作业数为 0，one-1a-par 改 ReadOnlyBaseline → 新 rc → 重跑第一步；#242 待 #238 合并后复核 → 探针 → 删 101）；C-19 白名单留 1 条（用户在 one-ops 会话答第几条或 userid，未答）；SLS 行动策略 one-alert-p1/p2 未查到（用户确认是否建在 SLS 主账号 1980847222980247 下）。
  - 代码：#250（0a）→ P3b（next-4，H1 已修，合并时 one.env 老待办键改名同一 rc）→ P4（含 regionalHead 改名）→ P5 → F-1（one-1a-par，P5 后）。
  - 待用户批：ADR-0043（分级两维度）、0044（0032 修订，Q1 撤并来源）、0045（FD-27 关账不重开）合一页（one-architecture 先发我过目）；C-6 回放差异页 https://claude.ai/artifact/Y7ogz8AWPmZdeuE4HCEGqL 未提交。
  - 财务事实待外部：F-01..F-04、F-08 财务 / 税务复核，F-05 待财务（FINANCE_DECISIONS，#249）。
  - 在途小项：C-6 DEFECT 不再取证（#254 暂停，按「回放方法限制、未坐实」记入 C-6 报告，D-3 出口评审由用户看）；one-quality 小 PR 待 #243、#246 合并后开；C1 全量铺开排 P3b 后；ARCH-D5 写回在 #249。
- 22:10 现状：常驻会话新增 one-rules、one-quality；one-1a-next-3 已交接给 one-1a-next-4。
- dev 首次部署只剩：101（C-14：rc.3 → 正式账号核实 → (f)/(g) 定处理 → #242 → 探针）、C-19 可见范围与白名单（用户本人操作 / 告知 userid）、one.env 已改三键；090 改为白名单外开放前与生产前（intent 214650，#241）；人选 dev 全由用户兼任（215052）；B-3 dev 暂缓。
- 代码：先保 dev（214511）——0a → P3b → P4 → P5 → F-1（P5 后，one-1a-par）；拆分 a/b、R-1b-0b、DA-3 生产前。P3b 合并时 one.env 老待办键改名同一 rc（ops）。
- 待用户：财务决策页、ARCH-D5 方案页、F-1 决策页（one-rules）；分级两维度 ADR（one-architecture）。
- 债务：验收测试 ReadOnlyVerificationCommandIT（~980 行）、ReadOnlyAccountCheckIT（~575 行）超 400 行未拆（one-1a-par，#235）
- P3B-H1：P3b 合并时核实命令连接名与指标标签随改（one-1a-next-4 + one-1a-par），同步 INFRA_RUNBOOK §4.1
- 本机：用户授权停了 psa-local（4）与 pentagi（4）容器；ci-runner 5 个与两个 PG 未动
- CI 并发：test.forks=2 等打包 ci.yml PR（one-ops），runner / Docker 配置不动观察
- SESSION_PLAYBOOK §2.1 第 15 条补取证子代理派工约束（#233 合并后，锚点在其新增行）
- 常驻会话（intent 本日 one-rules / one-quality）：one-rules、one-quality 画像已起草待用户新开；one-security 在生产前设（接 C1 安全复审、ASVS / 等保级别、个人信息）；one-data 在迁入阶段前设（ADR-0036、ADR-0038 基线关闭、对账）。
- 18:30：已合并 #200–#207、#209、#211；队列 208 197 210。#197 合并后通知 one-ops 出 rc.2。
- P3b 排序定为甲′：R-1b-0a → P3b → 拆分 b → 拆分 a → R-1b-0b → DA-3 → P5（next-3 的 p3b-sequencing.md）；G06/G07 撤下随拆分 a。待转 one-architecture（/clear 后）：ADR-0039 勘误（:41 排期解释、3 个权限码实为 2、SyncRunHoldReleased 语义）+ 拆分前设计卡。
- one-glossary：A（写回）、C（项目样例页）开工；B1 等 A，B2 等 B1，均 opus 复审；kind 映射裁定见 18:20 消息（单据→聚合 / 实体，字段挂实体，子域等入 bcs.yaml）。
- one-architecture：C1 方案确认页（不复刻默认口令）、工程框架勾选页、3NF / 六边形待用户；step01 分支待用户确认第 0、1 步后开 PR。
- one-1a-next-3：P2（含 H-1 登录登记缺陷）→ ARCH-D1..D7 小修复批（D5、D7 疑行为缺陷，修前报我）→ P3b（先定 ADR-0039 表的 schema）→ P5（并入 B3 删信封 source）。
- one-glossary 已 /clear（4f921bec）：#206 合并后开写回 A、B（含 DDD 8 条，intent 171959）、C 三个 PR，先报方案。
- 钩子：/clear 路径已实测生效（one-architecture d2803f88，来源 startup，注入画像与 ARCH_CONTEXT）；未合 main 的 worktree（glossary、1a-par、ops）仍是旧 matcher，合 main 受保护文件需用户确认。压缩路径：第一次真实压缩时核对是否注入（压缩路径尚无样本；74995d04 有快照无 compact_boundary，可从此查）。
- 第三轮用语确认已完成（365 条全部同意，写回 #196；改名卡后端并入 R-2a〔next-3，生产前〕、前端界面文字归 one-1a-par〔dev 前〕）；之后按「开工前术语门禁」（§5）逐卡确认，one-glossary 承担，调度只监督门槛（业务用语积压达到 100 条或距上次决策单超过 7 天，见 `glossary/ISSUES.md` 首段；错误码单列归 R-5，SESSION_PLAYBOOK §5）；job_grade 定义缺陷随岗位体系重梳。
- Codex 试点：R-4（R-5 之后），记录返工轮次与耗时（SESSION_PLAYBOOK §5「Codex 派工」）。
- 首次 dev 部署：dev 前阻塞清零后经用户批准（部署归用户决定）。
- 统一编排路线、brief 修订与 1b 前置项：见 MODERNIZATION_BRIEF 与 phase1a/NEXT_BATCH_PLAN.md。
- 后台任务与持久配置登记：[RESOURCES](phase1a/infra/RESOURCES.md)；运维在途细节：OPS_CONTEXT。
