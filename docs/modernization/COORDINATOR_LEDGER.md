# psa-coordinator 调度台账

调度会话的工作记忆。会话被压缩或重开时，先读本文件、`python3 docs/modernization/build_all.py` 生成的 STATUS.md（或 generated-docs 分支）、SESSION_PLAYBOOK §11–§14。只记「正在进行」的东西，完成即删；历史在 intent/、session-log/ 与 git 记录里。本文件只由调度会话维护，不拆片段。

更新：2026-10-14（第一个调度会话交接前）

## 用户授权调度会话自行决定的事
- 审核并合并 PR（「合 PR 交给你审核，决定权给你」）。main 受保护：只要求 backend / frontend / gitleaks 三项 CI 通过（另有 assertion drift 仅警告），管理员也受限。
- 是否新开会话、如何划分（「是否需要新会话这种决策权交给你」）。会话由用户新开，或复用已完成的会话并改名（`mcp__ccd_session_mgmt__set_session_title`）。新会话名用 `one-*` 前缀，已有 `psa-*` 不改。
- 用户在任何会话中的原话以会话记录为准；他人转达的决定先用会话记录搜索或当面复核。
- 不能替用户决定：部署、生产变更、购买、对外账号、已采纳 ADR 的变更（须新 ADR 取代并经用户批准）。

## 合并 PR 的操作要点
- 生成产物（INTENT.md、SESSION_LOG.md、STATUS.md、MODERNIZATION_BRIEF.md、adr/ADR-*.md、adr/README.md）冲突：取任一边后运行 `python3 docs/modernization/build_all.py`，再 `--check`。scratchpad 曾有脚本 update_pr.sh 做这件事（会话 scratchpad 不保留，必要时重写：worktree 检出 PR 分支 → merge origin/main → 生成产物取 theirs → build_all → push）。
- 非生成文件冲突：退回给作者会话合并（不要替作者手工合并业务文档）；作者推到本地分支后，由调度会话 `git push origin <commit>:refs/heads/<PR 分支>`（先确认是快进）。
- zsh 中 `"$H:claude/..."` 会被当成修饰符，写成 `"${H}:refs/heads/..."`。
- 审核要点：与 main 是否冲突；gitleaks 全历史；build_all --check；文档不含金额与个人信息；不得修改已合入的迁移（CI 有 migrations_immutable）；文档不用「PSA」指代系统（CI 有 check_legacy_name）。
- CI 偶发失败先看是否与 PR 内容无关（如测试抖动），`gh run rerun <id> --failed`，并让 1a 修成确定性。

## 在途工作会话
| 会话 | 在做 | 下一步 |
|---|---|---|
| psa-1a-platform-bridge-reimagine | A+B 整合已交（PR #50）；opus 复审本批中；起草下一批派工；5 个事件补 businessDate（OLAP 结论）未做 | 复审结论后派下一批三路：下一批三路：A 审批 / TIMESHEET / 待办通知；B 组织 / 登录 / 编号 / 审计 / 数据权限（ADR-0026/0027）/ 平台约定 / 84 项命名改名；C 前端（共用包、工作日历录入页、H5 与 PC 差异）；完成后 opus 按 §14 复审 |
| psa-repo-fragments | 记录类产物不入仓：INTENT / SESSION_LOG / STATUS 移出仓库，main 合并后 Action 生成到 generated-docs 分支 | 报告后开 PR；合并前通知各会话不再提交这三个文件 |

已完成、可复用：psa-olap-architecture、psa-terms-review（第三轮用语确认待启动）、psa-1a-process-review、psa-authz-model、one-docs-naming、psa-gl-finance-review、psa-review-*。

## 待合并 PR
- #51 Phase 1a 数据架构打分卡（K1–K15、T1–T5、D1–D10，§14.1）——等 CI。K、D 与 T 的用户决定已转 1a 下一批 B（T1 现在建组织版本表；T2 过去日期日历锁定；T3 consumed_events 90 天、ACT_HI_* 在线 3 年归档满 10 年、separation_date；T4 Phase 2 定）。
- #50 已合并（1a A+B 整合）。调度会话已同意：意见字段进任务详情与已办、工时提交错误统一 ProblemDetail（下一批 A）；ExportedSnapshot 例外接受；effect / opinion / binding / nullable 交第三轮用语。

## 待用户 / 财务 / 运维决定
- 部署前清单：`docs/modernization/phase1a/infra/PREDEPLOY_CHECKLIST.md`（A 需用户决定 8 项、B 只能用户或运维执行 9 项、C 执行前须确认 9 项、D 出口验证 9 项）。已发给用户；A 类人选与群名需用户直接回复；建议用户先做 B-1 `/design-sync` 与 B-2 新开企业微信应用。
- 财务：确认单 v2（`~/.modernize/psa/finance/FINANCE_CONFIRM_v2.html`，FQ-01..FQ-22 + 7 份 ADR），答复用 `r0/import_finance_answers.py` 导入；G1 开工前必须。FQ-18 需先跑 U8C 分类查询另行发送。
- 用户：B1–B5 与经营问题清单（`~/.modernize/psa/bi-inputs/questions/`）；OAT 4 个文件在 ddd-theory 提交后改引提交号；确认单末尾「会计类新术语由财务逐条确认」是否保留（未答）。
- 生产前：Phase 2 / 4 出口停用生产 SQL Agent 作业（A8_ACCESS_REGISTER #28–#31），属运维动作须用户批准。

## 排定的后续
- 第三轮用语确认（复用 psa-terms-review）：1a 下一批交付后启动；合并 1a 全部提议术语（按业务 / 技术分组）、R0 的 TERMS_ROUND3_INPUT（A 组 19、新词约 45、NRPM 与「人工成本产出倍数」对齐）、前端用语（含同步 / 放行 / 被中止与 held_run 重叠、待办与 todo / inbox 重叠）、工时类别（项目 / 售前 / OH，OH 疑似方言）、legacy_system（老系统，方言为旧名称）、TS / JV 单据类型代码。ADR-0018：阶段结束时不得留有会计类提议术语。
- 统一编排路线（1a 试点结束、brief 重新生成时一起审批）：Phase 1a–5、6a–6f（ADR-0021）、G1/G2（ADR-0022）、R0–R6（ADR-0023 / 0025）统一编号与依赖；6a 入口加「收付口径推送通道就绪」「过渡镜像就绪」；Phase 4、6a–6c、G1 合为业财一体阶段作为可选方案；客商主数据是否提前；6a–6f、G 与 R 阶段并入模板时补「流程梳理」入口条件。
- 1b 前必须：区域应收负责人指派类型按规格建；代理审批授权模型评审（RULE-270）；ADR-0026 列出的新规则先在 RULE_REVIEWS 登记（法人限定、审批参与只读访问、授予时职责分离组合）；组织数据管理员是否列敏感复核；Phase 1b 流程梳理（新会话 one-1b-process-review）。
- 新 ECS（i-bp122xlpar8w2uylmal2）机内加固随部署清单 C-4 执行。
