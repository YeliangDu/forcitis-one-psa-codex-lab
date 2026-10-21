# one-coordinator 台账（速查）

只记现行状态，一行一项，过程与决定只放链接（SESSION_PLAYBOOK §2.1 第 13 条、§11）。新开会话、清空或压缩后由钩子自动注入（#205 合并后生效；未注入就手动读本台账）。更新：2026-10-17 13:28（one-coordinator）

## 交接（每次 /clear 前更新；清空后先照做，用户 2026-10-17 要求）
更新：2026-10-17 09:40（one-coordinator）
- 清空后第一步：通读 SESSION_PLAYBOOK §11 全文（`sed -n '/^## 11\./,/^## 12\./p'`），再做「开场核对」。
- 本会话已犯、不得再犯（用户 10-17 指出）：①要用户定、补值、转财务、需配合的事一律写收件箱 inbox（status pending），对话里不问（§11 第 1 条，记忆 inbox-only）；②会话之间技术问题直连，不居中转述、不要求抄送（第 2 条）；③跨会话消息首行标「派工：/通知：/请用户决定：」；④每日简报每天早上发；⑤转述注明已核实 / 已抽查 / 未核实（第 12 条）。
- 派工用 mcp__ccd_session_mgmt__send_message（会直接开启对方回合）；SendMessage 跨会话常停在「未读」不唤醒空闲会话（10-17 全员停工的原因）。
- Codex 优先（intent 085534，§5 已改，记忆 codex-first）：各会话能交 Codex 的先交（gpt-6.1-sol xhigh），Claude 只派工、首轮审稿、安全、验收；按日报「Codex 卡数 / 返工轮次」评估，返工两轮以上收回。
- 在途：strict 已关，快慢两道试行到 10-19（main 并行合入变红一次即 `gh api -X PATCH .../required_status_checks -F strict=true`）；收件箱待答 3 项（N1 五个值、AN-06 转财务、dev 日历）；one-architecture 下一件起草一次投产过渡 ADR（C1，施工图关键路径），前提清单 `architecture/proposals/PREREQ_NAMING_CONSTRUCTION_2026-10-17.md`（分支 one-architecture-adr-governance）；今日简报未发。
- 主线常驻条（用户 10-17 要求钉住、滚动更新）：本会话 mod mainline-band 读 ~/.modernize/psa/handoff/mainline-band.txt 显示在输入框上方；每个命名主线完成点更新该文件（首行目标，后续行进度，末行「要你做」与更新时间，≤8 行）。同一 mod 另有状态栏（队列、在开 PR、main 红绿、strict 试行剩余天数、Codex 在跑任务自动取；收件箱待答数读 ~/.modernize/psa/handoff/status-extra.txt，收件箱增减时更新）。
- 本分支待收进文档合集：claude/one-coordinator-two-lanes（intent 4 条、§5 改动、裁定 -05、台账）。

## 作战图（派工前先看；SESSION_PLAYBOOK §11「战队协作」第 5 条）
| 主线 | 负责 | 当前 | 下一步 | 阻塞 |
|---|---|---|---|---|
| dev | one-ops | **暂停**（用户 10-17「ops 这一轮 rc 后，暂时不考虑 dev 的事儿了」）：rc.6 已授角色、单人运维模式开；日历未导（C-6 SQL 已修，one-ops-r56 @ 1da307b9） | 用户说恢复再排 | — |
| 流程与业务架构对齐 | one-architecture + 三组 | 阶段层：SD-01～10 已裁（#422）；对象层：主对象脚本提议、三组确认中；VS01-10 定独立流程「作出参投决策」（#428）；已派五件（#429）：支撑本段、配置补读关系、内部服务价值流评估、集成类型、业务/管理/支撑分类 | 数据到后刷新对齐图（scratchpad(e1ce474b)/align/refresh.sh 后以同 file_path+files 重发；流程栏按业务/管理/支撑分三行，归属实线、支撑虚线，集成单列） | — |
| 命名与用语 | one-architecture / one-glossary / one-quality | 规则在 PROCESS_METHOD_DRAFT §11、name_id_check.py；用户定「先规范再登记」（#430）：架构产物只用规范名，改名对照表与用语决策单合并交用户，认可后再登记、再定稿；对象名一律名词（结果或记录）；86 个对象未登记 | #431 拆分：审计报告与用语问题先合，提议稿改为规范名+原名别名后随页交用户；one-quality 写架构用语检查 | — |
| 确认页 | one-architecture | 7 页在收件箱，用户暂不作答，待对齐与命名后一次改版 | PAGE_IMPACT.md | 对齐完成 |
| CI 与合并 | 调度批准 / one-merge 执行（10-17 已切 scripts/merge/mq.py，队列在 <git-common-dir>/merge-queue/）/ one-quality 写脚本 | 今日合入 #437–#455，队列空；10-17 08:5x 用户定「关」，strict 已关（intent 084339），快慢两道试行至 10-19 | 试行 2 天；main 因并行合入变红一次即恢复 strict（gh api PATCH required_status_checks strict=true） | — |

## 调度惯例（本日新增）
- 在线页：会话日报 https://claude.ai/artifact/8CAmf8sAJsoaFNwetoGC7x （每天早上：scripts/ops/session_daily.py 出数 → 补 grade/assessment/action → 数据放 scratchpad(19432a27)/daily/site/data/<日期>.json 并更新 index.json → 以同一 file_path 重发 session-daily.html 与 files）；对齐图 https://claude.ai/artifact/7M7q1KQTZ7XwaoXSELdETL （源与 refresh.sh 在 scratchpad(19432a27)/align，已钉左栏）。
- 收件箱待用户答：ANM 命名体系与施工图方法 https://claude.ai/artifact/KTzjRe3S2FvJEiDu1Priey ；N1 规则待定 https://claude.ai/artifact/BTo6TuYzW7H8vgjCanh4Vq 。用户说「答完」即取答复分发。
- 下一文档合集清单：scratchpad(57026514)/next-docs-train.txt（调度批准、one-merge 中午/晚上收）；调度自己的规则、画像、台账、intent、裁定改动都在分支 claude/one-coordinator-two-lanes。
- 裁定：rulings/COORD-2026-10-17.md（RULING-COORD-20261017-01..04），one-quality 登记 ACCEPTANCE_RULINGS.yaml。
- 在途：one-1a-par ADR-0034-IMPL（3 PR，PR-3 依赖 one-1a-next-5 的 WF-REINSTATE）；one-1a-next-5 NITS-1 待 #454 后入队；one-architecture 云端试点派单待写；ultrareview 免费剩 2 次（只能电脑发起）；云会话额度 11-05 到期。
- **首要目标（用户 2026-10-17）**：把每个会话调成本项目最优秀的团队——会话日报（每天早上，同一在线页）→ 每周胜任力评审 → 改进动作落为守护、评审模板、画像工作守则（intent 本日）。
- 云端额度（用户免费额度，2026-11-05 到期）：先试点一个价值流的流程骨架（等 #405 后，one-architecture 写派单）；497 处改名待 ADR-0053 批准、用户认可规范名后考虑走云端（intent 062720）。
- 合并执行归 one-merge（10-17 切到 mq.py），调度只批准入队。
- 合并：用户 10-16 定——文档不开 PR（会话报分支+提交号）；代码一工作单元一 PR、在制 ≤1（代码合集化暂不动）；main 只经队列合入；Auto-fix 已由用户在 app 统一关闭。
- 积压 PR：one-merge 每天两次盘点五类报调度，调度定去向（intent 222637）。
- 错题本：只抓两主线（落实须有真实检查、每晚复发检测）+ 新测试重复跑（intent 235811）。
- 不稳定测试：不隔离、不放宽；外部失败找测试作者修。
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
- 先通读 SESSION_PLAYBOOK §11 全文（含消息约定、战队协作 1–14 条），本行记通读日期：2026-10-17（/clear 后漏读致违反第 1、2、8 条与首行约定，用户指出）。
- `gh pr list`、`ListAgents`、合并队列（`pgrep -f mq_run2.sh`，须在仓库目录启动，gh 需仓库上下文）、`gh run list --workflow nightly-legacy-it.yml -L 1` 失败即开卡（#165 合并后生效）。

## 合并
- 执行归 one-merge：队列 `scripts/merge/mq.py`（队列目录 <git-common-dir>/merge-queue/，`python3 scripts/merge/mq.py status`）；strict 已关、快慢两道试行至 10-19（intent 084339）。旧 mq_run2.sh 已停用。

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
- 发布门禁 #438：有写权限者可另建 PR 空转 CI 伪造成功运行、事后删 PR 则无法发现；写权限仅用户账号及其会话，门禁只管 dev，prod 另有用户审批（用户 2026-10-16「接受」）。

## 本机与调度自管
- CI runner mac-one-1..4（~/actions-runners/one-N）与部署 runner mac-one-deploy（/opt/forcitis-one-runner）；main 分支保护 strict。
- 私有文件：~/.modernize/psa/ 下 infra/role_holders.md、finance/（问卷 v2/v3、财务答复、FINANCE_DECISIONS.html）、audit/、codex/、handoff/。
- 会话内定时任务：无（用户 2026-10-16 停掉上下文巡检，各会话批次边界自查）。

## 暂缓清单（用户定生产口径前不开工，intent「生产自成体系」#344）
- R-1b P6 老系统对照导入（one-ops）；WF-03（拆分 a）；AP-12、AP-18；新开以老系统为数据源的生产期功能卡；地基批与校准车道（等架构全貌，intent #380）；三组流程铺开（等流程设计方法批准）。

## 排定后续
- 以「交接」「作战图」两节为准；10-16 及更早的「以此为准」段已删（画像审查 GOV-1017-COO-03 指出与现状冲突），历史见 git 记录与 intent。
