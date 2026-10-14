## 2026-10-14 · psa-gl-finance-review（续）· ADR-0022 采纳前补齐
- 目标：按 psa-coordinator 派工补充（用户：ADR-0022「要，补齐后再拿来审批 (Recommended)」；部分取代写法「认可」），把评审结论与修订稿并入 ADR-0022，并按 SESSION_PLAYBOOK §12 补齐必备小节。
- 输入：PR #23（评审结论）、PR #24（变更治理：§12、ADR 生命周期、生成器校验）两个分支；两者在本次提交时均未合并。
- 用户决定（原话）：本会话中用户：「提交」（在 #23、#24 合并前提交）。
- 做法：分支 claude/psa-gl-adr22-complete 基于 #23 分支，合并 #24 分支（INTENT、SESSION_LOG 只追加，冲突时两边都保留）；build_adrs.py 中 ADR-0022 改为 9 元组：supersedes「ADR-0016 第 6 条、ADR-0017 第 4 条」，以及 impact、migration、rollback、guards 四个小节；决定第 2 条（两个账簿、两张科目表加映射、口径转换规则分类、三条对账等式）、第 4 条（报表行两账共用）、第 5 条（现金流量标在现金分录、现金等价物范围）、第 8 条（权责账簿不与 U8C 权责账套对账）、第 10 条（两账同时关账、不反结账）改写，新增第 11 条（G1 起步与期初）、第 12 条（取代声明）；CAS 14 差距风险写入影响面。GL_SCOPE_REVISIONS §A、§B、§E 注明已并入。
- 验证：合并后先重跑生成器，与分支内容一致；打补丁后重跑，只有 ADR-0022.md 与 README.md（状态文字）变化；生成器校验通过；ADR-0016、0017 状态未改。
- 未完成：ADR-0022 待用户审批；采纳时同一提交把 ADR-0016、0017 状态标为「已采纳（第 N 条已被 ADR-0022 取代）」并把 ADR-0022 状态改为已采纳；建议审批前按 §12 做一次 architecture-critic（opus）对抗性评审（ADR-0022 早于该规则，非强制，待 psa-coordinator 或用户决定）。
- 下一步命令：#23、#24 合并后 psa-coordinator 为本分支开 PR，并把 ADR-0022 交用户审批。
- 子代理与模型：无新子代理。
- 会话 ID：本会话（worktree psa-gl-finance-review-bd1b33，分支 claude/psa-gl-adr22-complete）
