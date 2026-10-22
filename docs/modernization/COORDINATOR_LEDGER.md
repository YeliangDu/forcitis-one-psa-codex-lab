# one-coordinator 台账（速查）

只记现行状态，一行一项，过程与决定只放链接（SESSION_PLAYBOOK §2.1 第 13 条、§11）。新开会话、清空或压缩后由钩子自动注入（#205 合并后生效；未注入就手动读本台账）。更新：2026-10-21 09:00（one-coordinator）

## 交接（每次 /clear 前更新；清空后先照做，用户 2026-10-17 要求）
更新：2026-10-22 22:2x（one-coordinator，30% 交接；清空后先读本条）
- **22:2x 在途**：
  - #628 自清推全员（排除只留 one-coordinator，intent 214820/214856）：opus 首轮「改后通过」（~/.modernize/psa/coordinator/reviews/pr628-opus-1.md，两项必改：端到端自测 D–G、调度硬排除）→ one-quality（已清空、已发接续）改→sonnet 复核→我批入队。合入后 one-ops、one-architecture 可自清；之前它们拒自清（钩子文案），one-architecture 已请自清待看结果。
  - one-quality 队列：#628 必改→#627 派 opus→预检提速两项（intent 213020：开 PR 不重跑推送已跑检查、预检只跑相关测试）→新 PB 落地（#627、#628 合入后整包交：新 PB+DATA_DESIGN_CHECKLIST+playbook_digest/build_sessions 改写+mapping 338 处引用）。
  - 合并：#630 加急合集（裁定 -05/-06 登记、ADR-0078 批准 intent 等）已入队，#624（ADR-0078 已采纳）排其后；#622 已合；#616 处理中；#632 W2 PR-B 有条件批（opus 结论贴评论+sonnet 复核、文档移出、CI 绿→直接报 one-merge）。下趟合集：one-glossary @67a29eaa1（proposed 词表今后同口径直收）、本分支最新、one-arch-people @eb119607、one-quality @1b6d768e3、one-arch-finance @79ab0c9a（outbox 待并入下条消息）。
  - one-arch-people（已清空并接续）做 ARC-1022-APE-01：B-15 核定重分类过账步 + 按月奖金计提（S-6 @cfc721a34；排在 PRC-96 后，承担 S23/S34）。
  - one-1a-par：#624 合入后开 PR-B2（唯一键+隔离+验收，分支 claude/one-1a-par-w2-employee-code-unique @0426bf3bb，裁定 -05）；dev WORKFORCE_SYNC_ENABLED=true 不关，走「隔离路径已实现」。PAR-02～05 已登记，三条 handoff 记忆已删（备份 ~/.modernize/psa/memory-backup-20261022/）。
  - one-1a-next-5：KERR-2 等 #630 合入（裁定 -06）。one-ops：46% 待自清（等 #628）。one-arch-finance：空闲。one-glossary：NM8S 存档改 git bundle，不推。
  - 额度：本周 62%（10-26 重置）；opus 会话与调度大上下文是主因（用户已知，未定收紧措施）。
- 清空确认方法（10-22 晚实测）：list_events 显示「idle — 0 of 0 messages」即已清空；新对话文件要到第一条消息才生成，不能用盯文件判断。23:4x 已对 one-1a-next-5、one-quality、one-platform-file 发接续；one-merge 空闲未清空（排队消息顶掉），已令再自清。
- **整改收尾清单（用户 23:1x「agent团队整改全部结束了告诉我一下」；全勾才报用户，并 PushNotification）**：
  批1 □自清推全员 #628 ✔合 □开工单 #627 □防绕过 #616 ✔合 □CLAUDE.md/记忆移仓（@427318381 合入核实＋按 memory-plan 备份后删记忆） ✔三条 handoff 记忆
  批2 □新 PB 规则表落地（含 DATA_DESIGN_CHECKLIST、playbook_digest/build_sessions 改写、338 处引用） □15 份画像按角色表重写（含交接源字段） □任务卡模板 WORK_CARD 更新 □finish.py（QUA-17） □无主设计扫描（QUA-23）
  批3 □守护①–⑦（QUA-11～16） □tasks 状态归一 □cron 迁工作流（QUA-22，迁移清单先进收件箱） □MRG-01 批准评论入队 □MRG-02 合入通知 □MRG-03 重启清单 □MRG-04 在制上限 □MRG-05 断档扫描 #635 □MRG-06 head 变出队
  补充 □QUA-19 子代理 model 必填 □QUA-20 拒 gh pr merge □QUA-21 PR 尺寸 □QUA-24 拦生产写 □QUA-25 拦须用户决定命令 □QUA-26 Codex 后台 □预检提速两项 □OPS-02 Docker 自动拉起
- 21:00 交接（以下为旧条）
- **（已解除，用户 20:4x「你一次性到位」，总方案 GOV-1022-COO-15 见 ~/.modernize/psa/coordinator/team-audit/program.md）讨论期冻结（用户 10-22 晚）**：「我想和你很认真的讨论下所有会话的角色和工作方式，这是个过程，所以在我们讨论结束之前，不要有什么决定」——讨论结束前不改会话角色、画像、规则、协议，不开/并/退会话；日常派工、批 PR 照常；确认页 E1CEBZoJ5TCJJX5ncQzkwa A 组与 COO-09 推全员暂停，等讨论。
- **20:15 现状**：讨论议题 2（任务卡规划、PR 拆分、清空节点）已给用户讨论稿（建议节点=卡交出 finish.py，不用 PR 合入；bc 开卡顺序 签发→骨架→命令→集成/页面；卡 1–3 PR；第一阶段派工卡为模板加目标/完成标准/前置卡号），等用户回三问；下一轮我出「守护覆盖哪些规则」总结。
- 在途：one-1a-par W2 PR-B 草案 @0adc05a6d 等 one-data 签；one-data 做 DATA_SCORECARD 等；one-1a-admin #599 后开卡 C、INV-138/Q-B31-5 进收件箱；one-arch-revenue、one-arch-finance 已令自清，清空后我 list_events 确认再发接续（revenue→AGG-20/16/02；finance→BONUS_OVERTIME_COSTING §3 PRC-96/AGG-41）；one-quality 试点通过，在做 COO-10；#611 已批入队；one-merge 待收合集：one-1a-par-w2-versions @0adc05a6d、本分支最新。
- 讨论进度：议题 1 开场注入已定 5 点（intent 本晚，全文 ~/.modernize/psa/coordinator/team-audit/discussion-1-injection.md），等讨论结束一起改；下一议题候选：守护覆盖哪些规则 / 任务卡由谁写。
- 收件箱重排（用户 10-22 晚「好，照这样改」）：确认页 E1CEBZoJ5TCJJX5ncQzkwa 改为只留仓库内容治理 3 题（v2）；团队规则 7 题撤下并入讨论（用户撤前草选 TEAM-01、TEAM-02 推荐项，未提交，讨论时参考）；业务题攒齐出新页：INV-138 四项、Q-B31-5（one-1a-admin 写收件箱）、终止/取消项目复核（薪酬补发时限与提醒对象已在 FCQ 10-22 08:38 答，删）。
- 21:0x 等用户 /clear 后我发接续：one-arch-revenue（→AGG-20/16/02）、one-rules（48%，→先写待用户清单进收件箱再做卡 C T-R/RUL）、one-glossary（71%，→glossary/HANDOFF_20261022.md 待办①起）。自清只限 one-quality 试点，别再让其他会话自清（钩子写着不要）。记忆删除等 @427318381 合入后先备份；三条 handoff-* 记忆待办待开卡（one-1a-par）。迁入写入通道两卡（NX5-08、par 日历）前置已解除。
- **21:00 交接（清空后先读这条）**：团队优化总方案 GOV-1022-COO-15（用户授权「一次性到位」，intent 203954，全文 ~/.modernize/psa/coordinator/team-audit/program.md；同目录 implementation-check.md 为落实核查：已落实 24、部分 13、未落实 1）。在途：
  - opus 子代理在写新 PB 规则表草稿 → ~/.modernize/psa/coordinator/team-audit/pb/（SESSION_PLAYBOOK.new.md、DATA_DESIGN_CHECKLIST.md、mapping.md、guards-todo.md）；文件齐了我审→sonnet 子代理在本分支落地（含 mapping 里的引用同步改）→报 one-merge。文件不在就重派同一提示（见 program 第三节批 2）。
  - opus 审题收件箱 one-rules 12 项（RULE-265、311、A10、补登卡 8 张、卡 C 发生日），结论若丢失重派；按结论撤/改题。
  - one-quality 队列：自清推全员（node_handoff 改排除名单、兜底文案改自清）→COO-13 开工单+画像删节（可改 PB §2.1 第16条）→finish.py→守护①–⑦→cron 迁工作流→仓库内容治理→COO-17 绕过补漏；#616 已批待 CI。
  - one-merge：改 mq.py（PR 评论批准「调度批准入队 head <sha>」、合入通知、钩子 PR 列需重启会话）；收文档合集含本分支 @427318381 起（新 CLAUDE.md、记忆移仓），合入后我备份再删记忆（memory-plan.md）。
  - 画像 15 份重写等 COO-13 合入后按 program 第一节一次改（含 75/85 残留）。
  - one-1a-par：W2 PR-B（裁定 -05 已进 main，入队前确认 A8 同步已停）→日历迁入通道→登记 3 条 handoff 记忆卡与 KERNEL_REASON_REQUIRED 卡（卡号回来后删那三条记忆）。one-1a-next-5：search_path→NX5-08 编号迁入。one-arch-finance：AFI-02 阻塞等 one-architecture S-5（AGG-137 并入 AGG-05）。one-arch-revenue：AGG-20/16/02。one-rules：卡 C 等 one-1a-admin 贴 §11.2 原文。one-glossary：待办②起。one-data：等各 PR；收件箱 T-D10 待用户。
  - 21:10 增：收件箱 one-rules 12 项已审（opus），撤 11 项（withdrawn 注依据），只留 RULE-265 待 one-rules 改题。KERR-2（KERNEL_REASON_REQUIRED）派 one-1a-next-5（search_path 后、NX5-08 前），卡 C 取消命令等它。工号唯一改走 ADR-0078（#619 占号已批；正文分支 claude/one-architecture-adr-0078-body @dd5ef55d6，opus+sonnet 已过）→ one-architecture 开正文 PR 后我出批准页给用户；W2 PR-B 已令拆开：先合不含唯一键部分，唯一键与裁定 -05 那几处等 ADR-0078 采纳后另开 PR-B2。
  - 21:15 答完：RULE-265 定时任务不得发起审批只产生待办/提醒→已转 one-rules；卡 C 保留期答「A」→已转 one-data。收件箱现无 pending。
  - 清空规则：除 coordinator、architecture 外由我管，现钩子未推全员前，会话到线先写交接，等 one-quality PR 合入再自清；我清空要用户 /clear（或 one-merge 重开远程+发接续）。
- 21:0x 接续核查（新会话）：PB 规则表草稿主体已在（43KB），另三份未出，已重派 opus 续作（草稿自查压到 ≤40KB + DATA_DESIGN_CHECKLIST/mapping/guards-todo）；one-arch-revenue 已完成 AGG-20/16/02（@40be8db36 报 one-merge，三点问 one-architecture）；one-rules、one-glossary 已在自行推进，不另发接续。
- one-arch-finance：AFI-02 作废（S-5 撤 AGG-137），S-4/S-5 已落 PRC-96、AGG-41（@6866f4494）；S-4b 已裁（@a747e42af，负债内部重分类）；派 one-arch-people 给 B-15 加「奖金核定重分类」过账步（outbox 待发，限流）。ADR-0078 用户已批（intent 211131）；裁定 -05/-06 补登 ACCEPTANCE_RULINGS（@dd62eee26）待 one-merge 加急；one-1a-par W2 PR-B 等 one-ops 核 WORKFORCE_SYNC_ENABLED、HELD→PENDING 交 architecture+data。PB 规则表四份草稿已齐待我审（新 PB 须与 playbook_digest.py 改写同 PR；TEAM-47 改仓库设置须问用户）。
- 21:4x：one-quality 报「清空接续」（交接 claude/one-quality-handoff-1022 @335755246），我发的两条（预检提速两项 intent 213020、自清推全员排除 coordinator/architecture）可能顶掉其自清——确认清空后发接续时一并带上，顺序：#627 opus 复审→自清推全员→预检提速。HELD 放行已裁方案 B（@20f99479c）；#616 已在队列；BI 空闲进程已关；NM8S 存档令 one-glossary 离线装依赖后推远端。
- 22:0x PB 规则表草稿已审（~/.modernize/psa/coordinator/team-audit/pb/，审前备份 .bak-pre-review）：101 条 TEAM（守 3/半 27/无 71），§14 拆出逐字一致；已改 TEAM-06/12（自清与远程只除调度）、TEAM-09（只 settings.json 需重启）、TEAM-45（MRG-01 前消息批准）、QUA-20 不改仓库设置。落地：等 #627、#628 合入后整包交 one-quality 单 PR（新 PB+DATA_DESIGN_CHECKLIST+playbook_digest/build_sessions 改写+mapping 338 处引用同步，引用同步可派 sonnet）。#628 自清推全员已开待 opus。
- 接手先做：ReadNotifications；`ls -t ~/.modernize/psa/outbox/`；`gh pr list` + `mq.py status`；逐个查开发会话前置是否满足（不得再犯：只盯合并）；intent 2026-10-22-15* 起为今日下午决定。
- 下午已定（intent）：交接阈值 30%/40%（154722，§2.1 已改并进 main #602）；降负载不得牺牲开发机功能（151546）；仓库内容治理四项交 one-quality（153421，GOV-1022-COO-06）；one-ops search_path 三项「都做」（155217）。
- 在开 PR：#603（已批，队列）；#587（已批，等 one-platform-file 解冲突+openapi）；#604 KERR（one-1a-par，等 opus+CI）；#599 REG-3（等 one-data 签发与 M1 裁定，验收补丁按 RULING-COORD-20261022-03）；#589（已批，one-quality）；#606 钩子三合一（守护文件，单列 opus）。
- 在途派工：one-1a-next-5 已清空、按 claude/nx5-handoff-1022 恢复（RULE-412 PR→REG-3 验收→search_path IMP-1022-COO-03→REG-4）；one-quality：#606 → 治理设计按 opus 8 条改稿（结论「改后交用户」，见本会话）→ 我出确认页；one-data：#599 签发；one-ops：search_path 三项执行报回；one-merge：#601 后删 one-merge-docs2，分支清理已完（146→69，27 个归档 archive/*，清单 ~/.modernize/psa/branch-cleanup-20261022/）。
- 等用户：Spotlight 隐私排除 ~/Projects、~/actions-runners、~/.m2；开发者工具已加（10-22 17:5x 核实：终端、Claude、Ghostty 均开）；治理确认页（待改稿）。
- 额度：本周全模型 55%（10-26 重置），超额已停；主会话读缓存是主因，少发会话消息、知会合并。
- **团队体检 GOV-1022-COO-07**（intent 2026-10-22-162957，用户「对，开工吧」）：第 1 步盘点已完成（193 条规则：机器强制 18%、部分 23%、靠自觉 58%；问题 58 条，最重：P-01 阈值多处仍 75/85、P-30~32 已批钩子未落地、P-02 PB §7 与 CLAUDE.md 相反、P-09/10/45 当前状态存放矛盾、P-25/29 规范无负责会话），文件 ~/.modernize/psa/coordinator/team-audit/inventory.md；第 2、3 步已写成 ~/.modernize/psa/coordinator/team-audit/plan.md（6 根因、6 节方案、3 批落地、7 项待拍板），opus 复审「改后通过」已按 9 条必改改成第二稿；确认页 https://claude.ai/artifact/E1CEBZoJ5TCJJX5ncQzkwa（10 题，含治理第二稿第七节 3 题，答复集合 answers/answers-submit）已进收件箱 pending → 用户答完按 plan.md「落地分批」执行；第 1 批不待批部分（P-01/11/57、CS-7.7、_retired、记忆清理）可先做 → 确认页进收件箱 → 用户批准后分批落地；文件不在则重派同一取证。
- 治理设计第二稿（GOV-1022-COO-06）：claude/one-quality-repo-governance @22e083a54，已按 opus 8 条改完，待我审后出确认页（第七节待拍板项）；可与团队体检确认页合并出。#606 已批入队，合入后 one-merge 装 commit-msg 钩子。
- **交接全自动 GOV-1022-COO-09**（intent 172418）：节点事件+≥20% 即自清，30/40 兜底；会话报「清空接续」→我 get_session 确认已清空再发「接续」；Remote Control 只保 one-coordinator（21:15 用户把 one-architecture 也交我管、远程已关）（用户 10-22 17:5x「只保留你和架构会话远程，其他都关了」，已关 one-rules、one-arch-people，其余本已关）（我自清时 one-merge 用 set_remote_control 重开）；已派 one-quality（与 COO-08 合 PR，先自身试点），试点通过后我改 §2.1 第 1 条与画像。
- COO-09 实测（10-22 晚）：①他会话的消息排队会顶掉 self 清空（one-arch-revenue 首次被 one-arch-finance 消息顶掉）→协议须写「清空中的会话不收消息，转调度」；②clear_session 不能清已 detach 的会话（即便由我开出），只能靠 self。重启后 mq.py 存活（15:37 起），新钩子 pre_tool_dispatch 已生效；one-architecture 远程重启后掉线，已重开。
- 其他下午事项：#604 已批（:99 显示名按 -04 覆盖）；Actions 保留期已改 14 天；Spotlight 已排除（用户）；开发者工具已加并核实。
- 不得再犯：恢复后只盯合并漏查开发会话；提交信息钩子交接写「等 §5」却不核实前提。

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
- 攒问（下一张确认页一并出）：薪酬补发 U-1 已在 FCQ 答，删。（LEB 三项已于 10-22 答完）；终止/取消项目是否需上级复核（one-arch-revenue 命令卡待定，10-22）；人工成本分配差异表 labor_cost_allocation_variances 含个人薪酬差额的保留期（不能永久，one-data 建议同薪酬类记录；ADR-0043 R-2、D12；出处 finance @587efe0c NOTES 第 14 条）
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
- 自检：派工前看作战图、METHOD 当前步骤、相关 ADR 字面；同一件工具派工前查是否已有负责会话。告诉用户「已派/已完成/未做」与转给用户的数字先核实（会话记录、gh、证据文件），注明已核实/已抽查/未核实；说某会话「上下文高」前看实况。自己的裁定当场记 intent。改 scratchpad 合并脚本先 `bash -n`；写在线页时间用 `date -u`。合集分支推送前跑完整 precheck。答应「合入后通知」的挂 Monitor 盯 MERGED。告诉用户可重启前，除问各会话有无 Codex，还 `ps` 查常驻进程与 Monitor，并说明谁在重启后拉起。超 7 天未决的提议 ADR 每周交用户（采纳/拒绝/搁置）。
- 开会话：start_session 开出后立即 detach_session 解绑为顶层，set_session_title 改为 one-* 名。
- 用户待答事项再提醒前，先问负责会话是否已拿到答复。

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
- 持久配置：`~/.cargo/config.toml` 的 `[build] jobs = 4`（10-22 用户「你处理这个」，限 dsm-morden 等 Rust 并行编译；撤销即删该文件）。
- 仓库设置：Actions 产物与日志保留 14 天（10-22，原 90）。
- 会话内定时任务：无（用户 2026-10-16 停掉上下文巡检，各会话批次边界自查）。

## 暂缓清单（用户定生产口径前不开工，intent「生产自成体系」#344）
- R-1b P6 老系统对照导入（one-ops）；WF-03（拆分 a）；AP-12、AP-18；新开以老系统为数据源的生产期功能卡；地基批与校准车道（等架构全貌，intent #380）；三组流程铺开（等流程设计方法批准）。

## 排定后续
- 以「交接」「作战图」两节为准；10-16 及更早的「以此为准」段已删（画像审查 GOV-1017-COO-03 指出与现状冲突），历史见 git 记录与 intent。
