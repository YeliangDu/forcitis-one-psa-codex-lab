# one-coordinator 台账（速查）

只记现行状态，一行一项，过程与决定只放链接（SESSION_PLAYBOOK §2.1 第 13 条、§11）。压缩后由钩子自动注入。更新：2026-10-15（one-coordinator）

## 授权与边界
- 合并 PR、开会话、派子代理：调度自定（「合 PR 交给你审核，决定权给你」）。不替用户定：部署、生产变更、购买、对外账号、ADR 变更。
- 代码 PR 须有 opus 复审结论；改守护文件（ci.yml、scripts/ci、.claude、验收 / 迁移守护）的 PR 单列并要 opus 复审。
- 他人转达的用户原话先核实；用户决定当场记 intent（`new_intent.py --session=one-coordinator`）。全程中文。

## 会话
| 会话 | 在做 | 下一步 |
|---|---|---|
| one-1a-next-2 | 1a 下一批：R-1b（P0–P6）、D1 缺陷批、ADR-0037 实现 | #139 合后开 P1；D1-1 → 只读校验重写（解阻塞 100） |
| one-ops（长期，不交接） | OIDC 8 项已全部建好、ECS 拉取首轮通过 | 写 8 个仓库级前端构建变量（门户临时 https://bpm.forcitis.com/seeyon/、PROD=DEV）→ 触发 Release 0.1.0-rc.1 → R-11 演练（先报）；上下文见 [OPS_CONTEXT](phase1a/infra/OPS_CONTEXT.md) |
| one-1a-next-1（已交接） | — | 待归档 |

## 合并
- strict 串行队列：scratchpad 的 `mq_run.sh` 读 `merge.queue`，`merge_pr.sh` 同步 main → 等 CI → 合并；`CONFLICT<n>` 若是非生成文件退回作者，生成产物取一边后跑 `build_all.py`。
- 在队（scratchpad/merge.queue，runner mq_run.sh 后台运行；/clear 后用 `pgrep -f mq_run.sh` 核对，不在就重启）：#141 #142 #143 #139 #144 #129 #146 #147 #148 #149 #150。#145（ADR-0036 已采纳）在 #129 合并后改基分支为 main 再入队。暂缓：#96（ADR-0029）。
- Release 0.1.0-rc.1：两次失败（CI 被取消的提交；缺前端构建变量），已交 one-ops 写变量并触发。

## 待用户
- 规则文档瘦身对照表（#129、#121 合后出）；同轮问 CI 覆盖率门槛是否恢复。
- 财务确认单 v3 已交用户（FQ-23..27 须 1b 前答复）；待补进 v4：ADR-0031 两小问、ADR-0036 U-1 / U-3。
- 生产前：第二名超管 / 审计员 / 组织数据管理员 / DBA 人选（私有记录 ~/.modernize/psa/infra/role_holders.md，不入仓）；C-11 关单人模式；CODEOWNERS；R-13 后备令牌待首次 OIDC 部署后由用户删除；归档 WORM 首个写入方前锁定。

## 部署阻塞
- 090 数据范围（F-1）；100 只读启动校验（ADR-0037 第 4 项实现）；基线开放标记（W4 单独 PR 删除）。

## 风险登记（已接受）
- RDS SQL Server 平台使所有登录带 ALTER ANY LOGIN 等服务器级权限，无法 DENY → 口令只放 ECS、90 天轮换、启动校验等于平台基线（intent 2026-10-15-082245）。
- 新系统只读账号可读凭据列（用户「全部可读，不排除」）；凭据值不得进入新系统任何表、日志、事件、会话。
- CI runner 以用户本人账号运行（用户本次未选专用用户，SUPPLY_CHAIN S6）。
- 老系统安全（实例白名单 0.0.0.0/0 等）：用户「老系统先不管」，已移出待办。

## 本机与调度自管
- CI runner mac-one-1..4（~/actions-runners/one-N）与部署 runner mac-one-deploy（/opt/forcitis-one-runner）；main 分支保护 strict。
- 私有文件：~/.modernize/psa/ 下 infra/role_holders.md、finance/FINANCE_CONFIRM_v3.html、audit/、codex/、handoff/。
- 会话内定时任务（/clear 后用 CronList 核对，不在就重设）：上下文巡检每 30 分钟（75% 准备交接 / 85% 批次边界交接；会话存活期内有效，最长 7 天）。

## 排定后续（指针）
- 第三轮用语确认（提议术语积压超 100 即启动，SESSION_PLAYBOOK §5）；job_grade 定义缺陷随岗位体系重梳。
- Codex 试点：R-4（R-5 之后），记录返工轮次与耗时（SESSION_PLAYBOOK §5「Codex 派工」）。
- 首个 Release 0.1.0-rc.1：OIDC 第 7–8 项完成后由调度手动触发 release.yml。
- 统一编排路线、brief 修订与 1b 前置项：见 MODERNIZATION_BRIEF 与 phase1a/NEXT_BATCH_PLAN.md。
- 运维资源与在途细节不在此重复：见 OPS_CONTEXT。
