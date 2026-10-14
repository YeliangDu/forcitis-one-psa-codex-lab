# psa-coordinator 调度台账

调度会话的工作记忆。会话被压缩或重开时，先读本文件、STATUS.md、SESSION_PLAYBOOK §11。只记「正在进行」的东西，完成即删；历史在 INTENT.md、SESSION_LOG.md 与 git 记录里。

更新：2026-10-14（片段化合并后）

## 用户授权调度会话自行决定的事
- 审核并合并 PR（「合 PR 交给你审核，决定权给你」）；main 受分支保护，只要求 CI 三项通过。
- 是否新开会话、如何划分（「是否需要新会话这种决策权交给你」）；会话由用户新开，或复用已完成的会话并改名。
- 用户直接答复的决定，以用户在任何会话中的原话为准；他人转达的决定先当面复核。

## 在途工作会话
| 会话 | 在做 | 下一步 |
|---|---|---|
| psa-1a-platform-bridge-reimagine | 第二波缺陷修复（H1–H3、M1/M4/M7–M9、TIMESHEET 新编号、G-1）；下一轮 A/B/C：M2、G-2 会签、RULE-302/268/269/285/309、RULE-280 留空审计；M3/M5/M6/第 9 条；M10 与 H5；之后 opus 复审 | 报告后开 PR；需补 INTENT 片段 |
| psa-1a-process-review | Phase 1a 流程梳理（7 个流程：登录会话、组织同步、审批通用、TIMESHEET、待办通知、编号、审计） | 先报范围请用户确认，每 2–3 个流程报一次 |
| psa-repo-fragments | 写代码规则第二批 11 条（含「能一开始做对就不留后续」）、pre-commit gitleaks、PR 模板；「流程梳理」写入 SESSION_PLAYBOOK 与 brief 各阶段入口条件、§5 预留流程梳理结论小节 | 报告后开 PR |

已完成可复用：psa-terms-review（第三轮用语确认待启动）、psa-r0-metric-baseline（R0 已收尾）、psa-gl-finance-review、psa-bi-decisions、psa-review-quotation、psa-review-approval、psa-sec-hotfix、psa-gl-scope。

## 记录方式
片段化已合并（#36）：用户决定 new_intent.py；日志 new_log.py；状态 status/units、status/blockers；ADR adr/src；brief 改模板；改完 build_all.py，片段与产物一起提交；产物冲突时重跑 build_all.py。本台账保持单文件，只由调度会话维护。

## 待合并 PR
- 无（截至 #36）。

## 待用户 / 财务决定（按到期先后）
- 财务：确认单 v2（~/.modernize/psa/finance/FINANCE_CONFIRM_v2.html，已发用户转财务）——7 份 ADR + FQ-01..FQ-22；答复 .txt 交回后用 r0/import_finance_answers.py 导入；G1 开工前必须答复。FQ-18 需先跑 U8C 分类查询另行发送。
- 用户：B1–B5 与经营问题清单（~/.modernize/psa/bi-inputs/questions/）。
- 用户：OAT 4 个文件在 ddd-theory 未提交，提交后 glossary/oat/README 改引提交号。
- 用户：确认单末尾「会计类新术语由财务逐条确认」是否保留（未答）。
- 部署前一次问清：D-3 域名与证书、D-4 A8 / 老系统只读视图与账号由谁建、D-5 应急管理员开通、新开企业微信应用（I-8 卡片回调）、企业微信接口真实租户核对、WECOM_RESTRICT_RECIPIENTS 生产值、AG Grid 企业版许可采购、新 ECS 云安全中心授权、新 ECS 机内加固、生产 RDS 是否另开、Phase 2/4 出口停用生产 SQL Agent 作业（#28–#31）。
- Phase 2 前：projectManagerId / regionalHeadId / 工时类别来源；ProjectCoordinate 维度。

## 排定的后续
- 第三轮用语确认（复用 psa-terms-review）：等 1a 第二波与 R0 交付后，合并 1a 约 150 条提议术语（业务 / 技术分组）、R0 TERMS_ROUND3_INPUT（A 组 19、新词约 45、NRPM 与「人工成本产出倍数」对齐）、界面用语「起始值、候选审批人、待办」、工时类别（项目 / 售前 / OH）。
- 统一编排路线（三份评估都已交）：Phase 1a–5、6a–6f、G1/G2、R0–R6 统一编号与依赖；6a 入口加「收付口径推送通道就绪」「镜像就绪」；Phase 4、6a–6c、G1 合并为业财一体阶段作为可选方案交用户选；客商主数据是否提前。1a 试点结束后随 brief 重新生成一起审批。
- 1b 前必须：G-1（取不到人与字段缺失须区分）、G-2 会签；区域应收负责人指派类型按规格建。
