## 2026-10-14 · psa-gl-finance-review（续）· ADR-0022 补两点（psa-coordinator 决定）
- 目标：① ADR-0023 L2「重述新版本」按 §12 部分取代处理，不另开编号；② 收付推送前移到 6a 对 6a 入口、Phase 4 排期、G1 草案的影响写入 ADR-0022，brief 只在模板追加 Proposed revision 草稿。
- 产出：ADR-0022「取代」增加「ADR-0023 L2 中『重述新版本』对总账与财务报表的适用」，决定第 10、12 条写明总账与 accounting.reporting 不重述；影响面「阶段」与迁移方案写明三项影响；MODERNIZATION_BRIEF.template.md Phase 4 入口追加 `Proposed revision:`；proposals/ADR-0022_CRITIC.md 补记。改写脚本第三段（patch3）按顺序在 critic 改写之后运行。ADR-0023 状态未改。
- 验证：生成器校验通过，只有 ADR-0022 与 README 变化。
- 下一步命令：PR #25 合并后 rebase 到最新 main，按顺序重跑三个改写脚本，报告提交号。
