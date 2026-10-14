## 2026-10-14 · psa-review-identity-org · brief §5 显示身份与组织规则
- 目标：让 brief §5 行为契约体现 RULE-316..376 及其评审结论（psa-coordinator 决定：改 rules/build_brief.py）。
- 输入：origin/main b1765b3（PR #5 已合并）；用户复核「是，44 条都同意」已由 psa-coordinator 记入 INTENT。
- 产出：rules/build_brief.py 同时读 rules/IDENTITY_ORG_RULES.md，P0 中 Review=No 视为已定、Review=Yes 视为需评审，按 RULE_REVIEWS.json 呈现（不复刻 / 修正）；MODERNIZATION_BRIEF.md 重新生成，新增「身份与组织（Phase 1a）」一节（已定 6、需评审 31、已评审 31），汇总表增加一行；REPORT.html 刷新。
- 验证：改前改后 diff 只有 4 处——§5 引言 1 行（规则来源与总数 189→226 条 P0，共 315→376 条）、已定 / 阻塞计数 2 行（97→103、92/42→123/73）、汇总表新增 1 行、文末新增一节；原有五个域分节逐行不变。build_brief 输出 P0 226、已定 103、阻塞 123、已评审 73。未使用子代理。
- 未完成与阻塞：§5 只列 P0；身份与组织域已评审的 P1/P2 条目（14 条）只在 RULE_REVIEWS 中出现，与其他域口径一致。
- 下一步命令：Phase 2 前新会话 `psa-review-delivery` 运行 `/code-modernization:modernize-review psa`。
- 会话 ID：本会话（worktree xin-7143b1，分支 claude/psa-brief-identity-org）
