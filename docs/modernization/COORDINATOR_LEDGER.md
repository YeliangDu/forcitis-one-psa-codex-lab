# one-coordinator 台账（速查）

只记现行状态，一行一项，过程与决定只放链接（SESSION_PLAYBOOK §2.1 第 13 条、§11）。新开会话、清空或压缩后由钩子自动注入（#205 合并后生效；未注入就手动读本台账）。更新：2026-10-21 09:00（one-coordinator）

## 交接（每次 /clear 前更新；清空后先照做，用户 2026-10-17 要求）
更新：2026-10-22 11:45（one-coordinator，用户要 clear）
- 接手先做：读 ~/.modernize/psa/outbox/；读工作台 board/request、分层总图 meta/request（有新请求就同步）；ListAgents + gh pr list 查断档；intent 2026-10-22-* 为今日决定。
- 今日已定：A7475/A7677/GL5/SGN/LEB/KPI/FCQ 全答并分发；7.5–8 小时不算加班；撤 psa_one_legacy_ro 已执行；git 原则 §12.6；生成产物移出 main 按推荐（1 期 #573 在队）；工作台钉左栏、按需同步；outbox 规则；年度目标在 BI（无贡献毛利目标）；不建 GitHub Project。
- 11:45 补：#574 已合（脚本与复审版逐字节一致）；one-merge 自动入队脚本停用、只按批准手动入队；mq.py remove 停不掉在跑 worker 交 one-quality 修（首轮 opus）；KPI.json（已答页）被 one-architecture 改了题面，已令回退（admission-file b220e6f9 不收）；one-rules 改 check_pages 范围为「改动页+未答页」；M-29 基线四注已写（结转偏差上界约 12.5pt 待财务核，攒问财务）；分层总图 11:00 的请求我漏看、11:40 已补。
- 不得再犯：回复用户前先读工作台 board/request 与分层总图 meta/request；批准入队后跟到合入。
- 在途待收：FCQ 落地（one-rules 补基线注四件、one-arch-finance 奖金与加班设计 ed99f3b8 待 one-architecture 核、one-arch-people 补发 8be32c18 已交）；#579/#581/#580/#570/#561/#573/#572/#574 在队或待 CI；PAR-04 两条裁定已进 main 后 one-1a-par 开 PR；one-quality 生成产物 4/5 期与 GOV-1022-COO-05；REG-3→RULE-412→REG-4→卡 N；KERR（one-1a-par）；one-platform-file 签章 api（新会话 local_87a73fc1）。
- 重启后先做：ReadNotifications；读 ~/.modernize/psa/outbox/；one-merge 恢复队列（队列 [572,574,570]，再入 #561、#573）并核主检出 f0c62f519；工作台按 ledger 同步一次。
- 今日已定（intent 10-22 各片段）：A7475/A7677/GL5/SGN/LEB/KPI 全答；撤 psa_one_legacy_ro 已执行；git 原则 §12.6；生成产物移出 main 按推荐（1/4/5 期先做，2 期上游已修，3 期缓）；工作台钉左栏；outbox 规则；年度目标在 BI 看板（无贡献毛利目标，Q03 结案）。
- 在途：财务口径确认页 6 题（one-arch-finance 出、我审题）；PAR-04 等两条裁定进 main 后开 PR；one-quality 生成产物 1 期 #573、GOV-1022-COO-05 台账补全；REG-3/REG-4（IMP-1022-COO-01）/KERR（IMP-1022-COO-02）；one-platform-file 签章 api。
- 清空后第一步：通读 SESSION_PLAYBOOK §11（含战队协作 1–15 条）、§5；读本节与下方各「**…**」行；扫收件箱 pending（https://claude.ai/artifact/RVU6nNNSsygF5sVRWLhQ15，当前 0 项）；intent 见 2026-10-21-15* 以后各片段。卡的状态以 tasks 片段为准，台账只写指针。
- **今日用户新口径**（详 intent）：交接钩子推全部会话、只交接不清空；回合结尾不向用户提问；后台任务命名适用所有后台任务；提交信息带 Session/任务号/为什么（钩子待做）；小代码 PR 可合批、已批 ADR PR 并入文档合集；纯文档合入不算代码 PR 落后；本机性能三项；分支/工作区清理归 one-merge 每周；重启前后固定步骤（§11 第 15 条）；派工登记表（intent 213907）。
- **等用户批**：ADR-0074（organization.administration 补进 ADR-0051 BC 清单、审批聚合补 AGG-113/114/合同借阅、合同原件借阅 W3 移交 contract.agreement、「经通用审批办理」对 ADR-0054/0055/0064 部分取代；采纳前 B-31 模块骨架不合入 main）与 ADR-0075（ADR-0058 补篇，子状态机各记转换）——one-architecture 在 opus 复审，批准页出后调度先审题再进收件箱。
- **C 线 one-1a-admin**（新，sonnet high，local_d0ced0f9）：ADM-01 契约/术语文档 PR 先走，骨架分支 claude/one-1a-admin-skeleton 等 ADR-0074；7.5/7.8 在分支写；platform.file 已准入（@2790c770）可登记依赖边；FileImports 随 7.10 落地，先行上线前未落地则撤。
- **队列/合并**：文件服务 #521（修词表）→#525→#527；词表 #536→#537→#538；文档合集按 one-merge 报；one-quality 的 #531（性能三项）、#541（mq：FF-MAIN 等）、主检出守护 PR 在走；主检出 7 个未跟踪提案文件随 admission-file 合集入库后由 one-merge 删。
- **one-quality 队列**：#531 → #541 → 主检出守护 → 派工登记表（陈旧检查+补登脚本）→ 后台任务命名钩子 → 提交信息钩子 → QUA-05 重跑 → 网络规则 CI → QUA-03。
- **one-1a-par**：日历改造 PAR-02（DDL 已签发设计层）→ F2 PAR-04 → PAR-03；W2 待办含 INV-62、RULE-387/394、RegisteredEmployee/SyncRun。
- **one-1a-next-5**：REG-2（verify 中）→ RULE-412 守护 → REG-3（#493 已合，不用等）；ADR-0066 决定 4 注册表改造须在 W0 收口窗口内完成（收口清单 proposals/freeze/W0_CONTRACT_BATCH_REVIEW.md，one-architecture 10-21）；P0 验收 11+2 例合入，RULE-376 缺口卡 IMP-1021-COO-11（待派）。
- **one-architecture**：ADR-0074/0075 → 回 one-arch-finance/people → 第三批卡 12–15（12 已交 Codex）；B-31 四项归架构（合同借阅 bc、撤回/退回映射、软件许可、§8.2）。
- **one-glossary**：GL4 写回（工单处理人先问 one-1a-admin 是否可转派）→ 第三批前缀/维度 → n20 → n22 → n21。
- 其余：one-ops ClamAV 待与应用侧联调后报建成；one-data、one-rules、one-arch-revenue 空闲。
- 不得再犯（10-22 12:xx 用户问「现在没有开发任务吗」）：恢复与每次巡检必须逐个查开发会话（one-1a-*、one-platform-file）最后一条与前置是否已满足，前置满足即叫醒，不只盯合并。
- 不得再犯：未核实就转述状态（REG-3 误等 #493）；台账写状态不写指针；重启前只保文件不保在途事。

## 作战图（派工前先看；SESSION_PLAYBOOK §11「战队协作」第 5 条）
| 主线 | 负责 | 当前 | 下一步 | 阻塞 |
|---|---|---|---|---|
| 开发主线（10-20 恢复，intent b58965a5） | A one-1a-par / B 待 ADR 入库 / C one-architecture+B31P / D one-ops | 里程碑=通用审批先行上线（TRN-03）。A：ADR-0055 拆 A8 同步改人事命令（IMP-1020-PAR-01，先报切分）、ADR-0034 收尾、数据范围；B：文件服务基础→签章服务第一段；C：行政事务模块+四类通用审批表单；D：恢复 dev、清单对齐、生产/演练评估、检测选型（one-ops 离线，消息排队） | 各线完成点报调度 | B 等 ADR 占号与用语（NAM-1020-COO-01）；C 等 B31P |
| 流程与业务架构对齐 | one-architecture + 三组 | 阶段层：SD-01～10 已裁（#422）；对象层：主对象脚本提议、三组确认中；VS01-10 定独立流程「作出参投决策」（#428）；已派五件（#429）：支撑本段、配置补读关系、内部服务价值流评估、集成类型、业务/管理/支撑分类 | 数据到后刷新对齐图（scratchpad(e1ce474b)/align/refresh.sh 后以同 file_path+files 重发；流程栏按业务/管理/支撑分三行，归属实线、支撑虚线，集成单列） | — |
| 命名与用语 | one-architecture / one-glossary / one-quality | 规则在 PROCESS_METHOD_DRAFT §11、name_id_check.py；用户定「先规范再登记」（#430）：架构产物只用规范名，改名对照表与用语决策单合并交用户，认可后再登记、再定稿；对象名一律名词（结果或记录）；86 个对象未登记 | #431 拆分：审计报告与用语问题先合，提议稿改为规范名+原名别名后随页交用户；one-quality 写架构用语检查 | — |
| 确认页 | one-architecture | 7 页在收件箱，用户暂不作答，待对齐与命名后一次改版 | PAGE_IMPACT.md | 对齐完成 |
| CI 与合并 | 调度批准 / one-merge 执行（10-17 已切 scripts/merge/mq.py，队列在 <git-common-dir>/merge-queue/）/ one-quality 写脚本 | 今日合入 #437–#455，队列空；10-17 08:5x 用户定「关」，strict 已关（intent 084339），快慢两道试行至 10-19 | 试行 2 天；main 因并行合入变红一次即恢复 strict（gh api PATCH required_status_checks strict=true） | — |

## 平台服务（10-20）
- 6 份平台 ADR 已批（intent 95c61e88，批准页 41cbCoxq487u81i1onT5ta），法务认可（dcb519eb），草案与复核在 architecture/research/platform-fit-2026-10-20/；占号入库交 one-architecture（部分恢复，只做五件 W0 事）；用语交 one-glossary NAM-1020-COO-01；AI 首批场景 087abdd0；财务：履约义务与合同条款分立 4911eb0a；平台分档冻结与准入 ADR 草案待复审（647ce42c）；分层总图 GANmVBuBzK58pT27xscJBE（已钉）。

## 调度惯例（本日新增）
- 分层总图（已钉左栏）：https://claude.ai/artifact/GANmVBuBzK58pT27xscJBE ——五层＋贯穿＋老系统退役，数据在页面 db（items/i000..，meta/page），状态变化用 ArtifactData update 改对应条目，不重发页面；源数据脚本 scratchpad(e9617959)/layers。
- 在线页：会话日报 https://claude.ai/artifact/8CAmf8sAJsoaFNwetoGC7x （每天早上：scripts/ops/session_daily.py 出数 → 补 grade/assessment/action → 数据放 scratchpad(19432a27)/daily/site/data/<日期>.json 并更新 index.json → 以同一 file_path 重发 session-daily.html 与 files）；对齐图 https://claude.ai/artifact/7M7q1KQTZ7XwaoXSELdETL （源与 refresh.sh 在 scratchpad(19432a27)/align，已钉左栏）。
- 收件箱待用户答：ANM 命名体系与施工图方法 https://claude.ai/artifact/KTzjRe3S2FvJEiDu1Priey ；N1 规则待定 https://claude.ai/artifact/BTo6TuYzW7H8vgjCanh4Vq 。用户说「答完」即取答复分发。
- 攒问（下一张确认页一并出）：员工薪酬支付失败后几天内须补发、超时提醒谁（one-arch-people U-1，推荐 3 天、提醒法人财务负责人，依据《工资支付暂行规定》第七条，出页前核原文）。（LEB 三项已于 10-22 答完）
- 下一文档合集清单：scratchpad(57026514)/next-docs-train.txt（调度批准、one-merge 中午/晚上收）；调度自己的规则、画像、台账、intent、裁定改动都在分支 claude/one-coordinator-two-lanes。
- 验收裁定登记后立即请 one-merge 加急小合集：acceptance_rulings 守护只认 base（main）上已有的裁定，随实现 PR 带入无效（10-21 F 卡被卡）。
- 裁定：rulings/COORD-2026-10-17.md（RULING-COORD-20261017-01..04），one-quality 登记 ACCEPTANCE_RULINGS.yaml。
- 在途：one-1a-par ADR-0034-IMPL（3 PR，PR-3 依赖 one-1a-next-5 的 WF-REINSTATE）；one-1a-next-5 NITS-1 待 #454 后入队；one-architecture 云端试点派单待写；ultrareview 免费剩 2 次（只能电脑发起）；云会话额度 11-05 到期。
- **首要目标（用户 2026-10-17）**：把每个会话调成本项目最优秀的团队——会话日报（每天早上，同一在线页）→ 每周胜任力评审 → 改进动作落为守护、评审模板、画像工作守则（intent 本日）。
- 云端额度（用户免费额度，2026-11-05 到期）：先试点一个价值流的流程骨架（等 #405 后，one-architecture 写派单）；497 处改名待 ADR-0053 批准、用户认可规范名后考虑走云端（intent 062720）。
- 合并执行归 one-merge（10-17 切到 mq.py），调度只批准入队。
- 合并：用户 10-16 定——文档不开 PR（会话报分支+提交号）；代码一工作单元一 PR、在制 ≤1（代码合集化暂不动）；main 只经队列合入；Auto-fix 已由用户在 app 统一关闭。
- 积压 PR：one-merge 每天两次盘点五类报调度，调度定去向（intent 222637）。
- 分支落后（10-22 用户问）：会话每开始一个工作单元先用命令合 main（不走 app 同步工具）；调度每天早上跑一次活跃工作区落后数，>100 的点名合；117 个 worktree 清理派 one-merge（已合入的删、有未推送的报调度）。
- 页面「请调度同步／更新」按钮（用户 10-22 选按需同步）：工作台写 board/request、分层总图写 meta/request（字段 at）；每次巡检读这两个文档，request.at 晚于上次同步（board/meta.at、meta/page.at）就同步一次，同步时写回新的 at。
- outbox 巡检（10-22）：会话外发被 app 限流时写 ~/.modernize/psa/outbox/<会话名>.md；我每次巡检（收到完成点、每天早上、用户问起）先 `ls -t ~/.modernize/psa/outbox/` 读未标「已转」的行代转，转完标「已转」。
- PR 断档兜底（10-22，昨晚 #552 等 6 小时无人入队，用户发现）：one-merge 队列清空即扫在开 PR，报「已批未入队 / 冲突或 CI 红 / 超 1 小时无人动」；调度每次收到完成点报告顺查 `gh pr list`，逐个确认有人在推。
- 错题本：只抓两主线（落实须有真实检查、每晚复发检测）+ 新测试重复跑（intent 235811）。
- 不稳定测试：不隔离、不放宽；外部失败找测试作者修。
- 不向会话要排期、不向用户转报估算（记忆 no-schedule-relay）。
- 会话报完成点即 get_usage 兜底查用量（≥75% 准备、≥85% 立即在批次边界交接）。
- 对齐图 https://claude.ai/artifact/7M7q1KQTZ7XwaoXSELdETL 只读源文件原样副本（src/），不在页里做人工映射。
- 工作台 https://claude.ai/artifact/TXzKwZFyzVUNEXxQW9M16r （已钉左栏，10-22 改版）：`python3 scratchpad(08ce11ca)/workbench/sync.py <工作区> <输出目录>` 读任务卡片段、gh、mq 生成 cards/sessions/prs/meta 四个 JSON，ArtifactData batch set board/*（带 if_version）；收到完成点报告和每天早上各同步一次，「要关注」里的停滞卡当天追。

## 授权与边界
- 合并 PR、开会话、派子代理：调度自定（「合 PR 交给你审核，决定权给你」）。不替用户定：部署、生产变更、购买、对外账号、ADR 变更。
- 代码 PR 须有 opus 复审结论；改守护文件（ci.yml、scripts/ci、.claude、验收 / 迁移守护）的 PR 单列并要 opus 复审。
- 他人转达的用户原话先核实；用户决定当场记 intent（`new_intent.py --session=one-coordinator`）。全程中文。

## 会话
- 10-21 15:09 上下文交接钩子在 one-coordinator、one-ops 实测生效（会话名正确识别）；试点一天后推全部会话。要点：app 读主检出的 .claude/settings.json，钩子 PR 合入后须快进主检出并重启 app；app 重启会结束所有由会话拉起的进程（含 mq.py），launchd 方案待 one-merge 出、问用户。用户「先解决钩子问题，再开展工作」的暂停于此解除。
- 10-21 归档：one-ops-calendar（日历导入完）、psa-gl-finance-review、psa-gl-scope（未推送提交备份到 origin/claude/psa-gl-scope @1b5cc823）、psa-review-quotation、psa-review-approval。one-arch-revenue/people/finance 用户定保留（「后面流程还用得到」）。
- 画像与当前状态见 `SESSIONS.md`（`build_all.py` 生成；画像 `sessions/<会话名>.md`，已结束的在 `sessions/_retired.md`）；实时情况看 `ListAgents`（SESSION_PLAYBOOK §2.1 第 16 条）。

## 开场核对
- 先通读 SESSION_PLAYBOOK §11 全文（含消息约定、战队协作 1–14 条），本行记通读日期：2026-10-17（/clear 后漏读致违反第 1、2、8 条与首行约定，用户指出）。
- `gh pr list`、`ListAgents`、合并队列（`pgrep -f mq_run2.sh`，须在仓库目录启动，gh 需仓库上下文）、`gh run list --workflow nightly-legacy-it.yml -L 1` 失败即开卡（#165 合并后生效）。

## 合并
- 执行归 one-merge：队列 `scripts/merge/mq.py`（队列目录 <git-common-dir>/merge-queue/，`python3 scripts/merge/mq.py status`）；strict 已关、快慢两道试行至 10-19（intent 084339）。旧 mq_run2.sh 已停用。

## 待用户
- 投产前（可选，用户转达 OD）：OD 能补到正式依据的附上即可；2022–2026 的 16 天已以老系统工时与审批核查为依据（用户「同意，16 天用工时记录作依据」），2023-04-23 按官方补调休；公司加休表投产前定稿。
- 财务口径决策单（46 题，~/.modernize/psa/finance/FINANCE_DECISIONS.html，#176）：用户全盘定；9 条事实问财务；A1、A10 待审计师书面意见。
- 用语：用户「先等等，我没有同意所有用语」「所有用语检查下，acl用语单列，不能进入新系统模块」（intent 161339）：第三轮写回作废；one-glossary 全量 ACL 归类（四类）→ 我抽查 → 交用户；「来源*」改归过渡期集成术语（intent 161053）。决策页必须有提交步骤（05bf4394），门禁页固定链接 https://claude.ai/artifact/MwoqwkhAYq9CtomgaedwZ1。R-1b-0 门禁暂停。ACL 全量归类（670：领域 562、过渡期 60、长期 18、去外部名 24、待定 6）已抽查放行：原编号改「领域去外部名」、A8 长期还是过渡交用户问、代码搬迁与 ADR 冲突转 one-architecture；同一固定链接挂两张单（ACL 108 条 + 第三轮重做），one-glossary 补测提交流程后直接发用户。
- 架构：ADR-0039 防腐层边界用户已定（4 项全推荐，C1 只定方向，intent 164527），#207 复审通过入队。身份生命周期研究（入职→企业微信开通）交 one-architecture，#207 合并后、C1 实施前，结论交用户。FRAMEWORKS.md、METHOD.md（分支 claude/one-architecture-method）待用户审，批准后排 METHOD 第 5 节 6 项偏差。架构决策页（7 问）仍待发。
- 规则文档瘦身对照表；同轮问 CI 覆盖率门槛是否恢复。
- 生产前：日历起草人是否即 OD 维护人员、日历确认人（业务管理员，须与起草人不同）由谁担任（ADR-0072 第 5 条第 2 款）；第二名超管 / 审计员 / 组织数据管理员 / DBA 人选（role_holders.md，不入仓；dev 流程管理员由用户兼任）；C-11 关单人模式；CODEOWNERS；R-13 后备令牌待首次 OIDC 部署后删除；归档 WORM 首个写入方前锁定；RDS 扩容 C-1。

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
- 发布门禁 #438：有写权限者可另建 PR 空转 CI 伪造成功运行、事后删 PR 则无法发现；写权限仅用户账号及其会话，门禁只管 dev，prod 另有用户审批（用户 2026-10-16「接受」）。

## 本机与调度自管
- CI runner mac-one-1..4（~/actions-runners/one-N）与部署 runner mac-one-deploy（/opt/forcitis-one-runner）；main 分支保护 strict。
- 私有文件：~/.modernize/psa/ 下 infra/role_holders.md、finance/（问卷 v2/v3、财务答复、FINANCE_DECISIONS.html）、audit/、codex/、handoff/。
- 会话内定时任务：无（用户 2026-10-16 停掉上下文巡检，各会话批次边界自查）。

## 暂缓清单（用户定生产口径前不开工，intent「生产自成体系」#344）
- R-1b P6 老系统对照导入（one-ops）；WF-03（拆分 a）；AP-12、AP-18；新开以老系统为数据源的生产期功能卡；地基批与校准车道（等架构全貌，intent #380）；三组流程铺开（等流程设计方法批准）。

## 排定后续
- 以「交接」「作战图」两节为准；10-16 及更早的「以此为准」段已删（画像审查 GOV-1017-COO-03 指出与现状冲突），历史见 git 记录与 intent。
