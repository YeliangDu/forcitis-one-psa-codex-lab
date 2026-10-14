## 2026-10-14 · psa-r0-metric-baseline · R0 第一步：在用报表结构分析
- 目标：psa-coordinator 派工——分析用户提供的在用报表（2 份 pptx、4 份 xlsx，仓库外 ~/.modernize/psa/bi-inputs/）：结构抽取、方言 → 正式用语与 OAT、口径问题、对照 BI_SCOPE E1–E14、初步重新梳理建议（指标基线草稿）。
- 输入：BI_SCOPE.md §6.1（PR #22 分支）、rules/GL_FINANCE_DECISIONS.md（PR #23 分支）、SESSION_PLAYBOOK §12（PR #24 分支）、glossary/terms.yaml（348 条）、OAT 机器主数据（ddd-theory 1ace877，245 条）。基线 origin/main eb52c8d，收尾时 rebase 到 eb006e1。
- 用户决定（原话，经 psa-coordinator 转达）：「这会目前正在使用的各类别报表，语言都是方言，要跟正式语言统一，目前基本靠人工分析，你转到目录，安排分析吧，这些都不是最佳的报表，全部需要重新梳理，这只作为你的参考」；用户即业务 Admin，B1–B5 由用户本人回答。
- 产出：r0/REPORT_INVENTORY.md；r0/METRIC_BASELINE_DRAFT.md；r0/structure/（pptx、pl、cash_ar、ops 结构与 term_mapping，脱敏）；glossary/r0_report_aliases.json 与 build_terms.py 接入（用语确认表 A 组 19 项）；STATUS 更新。
- 验证：只读输入（openpyxl 跳过损坏样式表）；产出文件人工抽查与脚本扫描无金额、人名、客户名、项目名（去掉一处人名缩写）；build_terms / build_confirmation / build_glossary 重新生成，terms.yaml 只新增别名与「产值」类禁用写法，348 条状态不变；gitleaks 通过。子代理：结构抽取 4 个（sonnet）、用语与 OAT 对照 1 个（sonnet）；口径判断与重新梳理由本会话（opus）完成。
- 未完成与阻塞：B6–B11、F6–F10 与 A 组 19 项待用户 / 财务；§5 新词条未起草；R0 其余（Q5、Q11、Q12、工具对比）待安排。scratchpad 中的未脱敏中间文件已删除。
- 下一步命令：psa-coordinator 安排 R0 其余内容；用语确认页（A 组 19 项 + 新词条起草稿）。
- 会话 ID：本会话（worktree xin-e2a79b，分支 claude/psa-r0-metric-baseline）
