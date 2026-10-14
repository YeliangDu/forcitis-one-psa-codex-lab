## 2026-10-14 · psa-repo-fragments · 写代码规则第二批与流程梳理步骤
- 目标：把用户采纳的写代码规则第二批（11 条）写进 SESSION_PLAYBOOK 与 CLAUDE.md，能自动化的做成守护；把「流程梳理」步骤写进 SESSION_PLAYBOOK 与 brief 阶段入口条件、§5 行为契约（psa-coordinator 派工，#36 合并后做）。
- 输入：origin/main fde2c74（片段化已合并）；SESSION_PLAYBOOK、CLAUDE.md、brief 模板与 rules/build_brief.py、.github。
- 用户决定（原话见 INTENT.md）：「全部采纳，并把能自动化的做成守护 (Recommended)」；「能从一开始就做对的，就不要留在后续阶段，除非后续做更有价值」；流程梳理步骤（原话全文见 intent 片段）。
- 产出：提交 1——SESSION_PLAYBOOK §5「写代码规则第二批」11 条（每条附起因与守护，第 4 条管范围、第 11 条管质量）、CLAUDE.md 11 行、.pre-commit-config.yaml（gitleaks v8.30.1，hook id gitleaks）、.github/pull_request_template.md（三栏）。提交 2——SESSION_PLAYBOOK §13 流程梳理、process/README.md 模板、brief 模板 1a–5 入口条件各加一条（正式条件，1a 注明补做）、§5 新增 {{PROCESS_CONTRACT}}，rules/build_brief.py 从模板的阶段标题自动列出各阶段并读 process/<阶段>/contract.md（缺省「待流程梳理」）；CLAUDE.md 开工须读加 §13。
- 验证：build_all.py --check 通过；brief 规则计数不变（P0 226 / 已定 103 / 阻塞 123 / 已评审 73），只多 6 条入口条件与 §5 一节；gitleaks 钩子定义经 GitHub 核实（v8.30.1 的 .pre-commit-hooks.yaml 含 id gitleaks）；本机未装 pre-commit，配置仅做 YAML 解析校验。未使用子代理。
- 未完成与阻塞：6a–6f、G1 / G2、R 系列尚未并入 brief 模板，并入时自动出现在 §5「流程梳理结论」，入口条件须同时加上本条；第 5 条前端界面文字进词表守护由 1a 会话负责。
- 下一步命令：调度会话派 `psa-1a-process-review` 按 SESSION_PLAYBOOK §13 补做 Phase 1a 流程梳理。
- 会话 ID：本会话（worktree xin-884358，分支 claude/psa-coding-rules-2）
