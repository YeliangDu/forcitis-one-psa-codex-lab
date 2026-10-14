## 2026-10-14 · psa-gl-finance-review（续）· 财务确认单第 2 版
- 目标：用户：「做成财务确认页」「他们没有 claude」「把前面涉及财务的 ADR 整体让财务确认下」；psa-coordinator：确认单以 r0/FINANCE_QUESTIONS.md 为唯一题源、覆盖 FQ-01..FQ-22、按来源分发、标 v2 取代 v1。
- 产出：r0/FINANCE_CONFIRM.html（离线 HTML，不需登录；一、涉及财务的 7 份 ADR 整体确认：0010、0016、0017、0021、0022、0023、0024，每份白话要点与「留意」，已被取代条款写明以取代后的 ADR 为准；二、FQ-01..FQ-22，FQ-18 另行发送；共 28 项作答）；r0/FINANCE_ADR_POINTS.md（ADR 要点数据源，opus 子代理改写）；r0/build_finance_confirm.py（生成与校验：FQ 与 HINTS 一一对应、GL 来源题都有回填位置、GL 13 个待财务问题恰好各覆盖一次）、finance_confirm.css / .js；r0/import_finance_answers.py（总账类 → rules/GL_FINANCE_DECISIONS.json finance_answers 并重新生成；经营分析类与 ADR 确认 → r0/FINANCE_ANSWERS.json，追加 reply；不改结论；只收第 2 版）；r0/FINANCE_QUESTIONS.md 加 FQ-21、FQ-22；rules/build_gl_decisions.py 待财务清单显示财务答复。第 1 版（分支 claude/psa-gl-finance-confirm）作废，未提 PR。
- 验证：生成器校验通过（22 题、ADR 7 份、状态与 main 的 README 一致）；浏览器中桌面与手机宽度无横向滚动，小问、进度、导出正确；在 main 的真实文件上用模拟答复跑导入，分发正确、空答跳过，之后还原。
- 未完成：psa-coordinator 核对 ADR 要点准确性后由用户转财务；FQ-18 的 U8C 只读分类查询未执行。
- 下一步命令：财务交回答复后运行 `python3 docs/modernization/r0/import_finance_answers.py <答复文件>`。
- 子代理与模型：ADR 要点改写（opus，1 个，续用一次）。
