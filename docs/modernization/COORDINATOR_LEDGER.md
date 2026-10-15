# one-coordinator 台账（速查）

只记现行状态，一行一项，过程与决定只放链接（SESSION_PLAYBOOK §2.1 第 13 条、§11）。压缩后由钩子自动注入。更新：2026-10-15（one-coordinator）

## 授权与边界
- 合并 PR、开会话、派子代理：调度自定（「合 PR 交给你审核，决定权给你」）。不替用户定：部署、生产变更、购买、对外账号、ADR 变更。
- 代码 PR 须有 opus 复审结论；改守护文件（ci.yml、scripts/ci、.claude、验收 / 迁移守护）的 PR 单列并要 opus 复审。
- 他人转达的用户原话先核实；用户决定当场记 intent（`new_intent.py --session=one-coordinator`）。全程中文。

## 会话
- 画像与当前状态见 `SESSIONS.md`（`build_all.py` 生成；画像 `sessions/<会话名>.md`，已结束的在 `sessions/_retired.md`）；实时情况看 `ListAgents`（SESSION_PLAYBOOK §2.1 第 16 条）。

## 开场核对
- `gh pr list`、`ListAgents`、合并队列（`pgrep -f mq_run.sh`，须在仓库目录启动，gh 需仓库上下文）、`CronList`；`gh run list --workflow nightly-legacy-it.yml -L 1` 失败即开卡（#165 合并后生效）。

## 合并
- strict 串行队列：scratchpad 的 `mq_run2.sh`（常驻，队空等待，固定在仓库目录启动）读 `merge.queue`，`merge_pr.sh` 每个队首先取消其余在队 PR 的 CI → 同步 main（gh 无 workflow 权限时改本地合并经 ssh 推送）→ 等 CI（首次失败自动重跑一次）→ 合并；`CONFLICT<n>` 非生成文件退回作者，生成产物取一边后跑 `build_all.py`，推送前跑 precheck。
- 核对：`pgrep -f mq_run2.sh`，不在就在仓库目录 `nohup <scratchpad>/mq_run2.sh >> mq.log &` 重启。
- 等前置再入队：#175（等 #177）。暂缓：#96（ADR-0029）。
- 每开一个 PR 立即关 Auto-fix（用户「不允许自动打开自动修复」）。

## 待用户
- 财务口径决策单（46 题，~/.modernize/psa/finance/FINANCE_DECISIONS.html，#176）：用户全盘定；9 条事实问财务；A1、A10 待审计师书面意见。
- 规则文档瘦身对照表；同轮问 CI 覆盖率门槛是否恢复。
- 请用户新开 one-1a-next-3、one-glossary（画像已写，#186）。
- 生产前：第二名超管 / 审计员 / 组织数据管理员 / DBA 人选（role_holders.md，不入仓；dev 流程管理员由用户兼任）；C-11 关单人模式；CODEOWNERS；R-13 后备令牌待首次 OIDC 部署后删除；归档 WORM 首个写入方前锁定；RDS 扩容 C-1。

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
- 会话内定时任务（/clear 后用 CronList 核对，不在就重设）：上下文巡检每 30 分钟（每小时第 13、43 分）（75% 准备交接 / 85% 批次边界交接；会话存活期内有效，最长 7 天）。

## 排定后续（指针）
- 第三轮用语确认：由 one-glossary 承担（画像 `sessions/one-glossary.md`，待用户新开），调度只监督门槛（业务用语积压达到 100 条或距上次决策单超过 7 天，见 `glossary/ISSUES.md` 首段；错误码单列归 R-5，SESSION_PLAYBOOK §5）；job_grade 定义缺陷随岗位体系重梳。
- Codex 试点：R-4（R-5 之后），记录返工轮次与耗时（SESSION_PLAYBOOK §5「Codex 派工」）。
- 首次 dev 部署：dev 前阻塞清零后经用户批准（部署归用户决定）。
- 统一编排路线、brief 修订与 1b 前置项：见 MODERNIZATION_BRIEF 与 phase1a/NEXT_BATCH_PLAN.md。
- 后台任务与持久配置登记：[RESOURCES](phase1a/infra/RESOURCES.md)；运维在途细节：OPS_CONTEXT。
