# psa-coordinator 调度台账

调度会话的工作记忆。会话被压缩或重开时，先读本文件、STATUS.md、SESSION_PLAYBOOK §11。只记「正在进行」的东西，完成即删；历史在 INTENT.md、SESSION_LOG.md 与 git 记录里。

更新：2026-10-14

## 用户授权调度会话自行决定的事
- 审核并合并 PR（「合 PR 交给你审核，决定权给你」）；main 受分支保护，只要求 CI 三项通过。
- 是否新开会话、如何划分（「是否需要新会话这种决策权交给你」）；会话由用户新开，或复用已完成的会话并改名。
- 用户直接答复的决定，以用户在任何会话中的原话为准；他人转达的决定先当面复核。

## 在途工作会话
| 会话 | 在做 | 下一步 |
|---|---|---|
| psa-1a-platform-bridge-reimagine | 1a 第二波收尾：注释清理、/api/v1、DTO required、opus 复审；Phase 2 / 4 草稿（5bd3c38 待 rebase） | #31 合并后为 5bd3c38 开 PR |
| psa-r0-metric-baseline | R0：工具对比小样（本机 Docker，合成数据） | 报对比结论 → 用户选 BI 工具 |
| psa-gl-finance-review | 财务确认单 v2（ADR 7 份 + FQ-01..FQ-22） | #30 合并后加 FQ-21/22 并提交；我核对要点后交用户转财务 |
| psa-repo-fragments | 记录文件拆为片段、ADR 源文件拆开 | #30、#31 合并后 rebase 交付；随后通知所有会话改用片段 |

已完成可结束：psa-terms-review（第三轮复用）、psa-review-*、psa-sec-hotfix、psa-gl-scope、psa-bi-decisions、psa-oa-inventory（已改名复用）。

## 待合并 PR
- #30 R0 第 1–5 段与 SQL Agent 通道登记；#31 1a 第二波。

## 待用户 / 财务决定（按到期先后）
- 财务：确认单 v2（ADR 0010/0016/0017/0021/0022/0023/0024 + FQ-01..FQ-22），G1 开工前必须答复。
- 用户：OAT 4 个文件在 ddd-theory 未提交，提交后 glossary/oat/README 改引提交号。
- 用户：BI 工具（R0 对比后定）。
- 部署前一次问清：D-3 域名与证书、D-4 A8 / 老 PSA 只读视图与账号由谁建、D-5 应急管理员开通、企业微信接口真实租户核对、WECOM_RESTRICT_RECIPIENTS 生产值、新 ECS 云安全中心是否加购授权、新 ECS 机内加固、生产 RDS 是否另开。
- Phase 2 前：projectManagerId / preSalesHours / regionalHeadId 来源；ProjectCoordinate 维度。

## 排定的后续
- 第三轮用语确认（复用 psa-terms-review）：等 1a 第二波与 R0 交付后，合并 1a 约 150 条提议术语（业务 / 技术分组）、R0 TERMS_ROUND3_INPUT（A 组 19、新词约 45、NRPM 与「人工成本产出倍数」对齐）、界面用语「起始值、候选审批人、待办」、工时类别（项目 / 售前 / OH）。
- 统一编排路线（三份评估都已交）：Phase 1a–5、6a–6f、G1/G2、R0–R6 统一编号与依赖；6a 入口加「收付口径推送通道就绪」「镜像就绪」；Phase 4、6a–6c、G1 合并为业财一体阶段作为可选方案交用户选；客商主数据是否提前。1a 试点结束后随 brief 重新生成一起审批。
- 1b 前必须：G-1（取不到人与字段缺失须区分）、G-2 会签；区域应收负责人指派类型按规格建。
