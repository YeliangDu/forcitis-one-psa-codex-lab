## 2026-10-14 · psa-olap-architecture · Phase 1a 数据架构逐表检查（DATA_SCORECARD）
- 目标：psa-coordinator 派工——1a 全部表逐表按 OLTP / OLAP 准备 / DAMA 检查；可自动化项给出守护规格；结论分「要改」与「有取舍」；数据架构检查清单并入 SESSION_PLAYBOOK §14。
- 输入：1a 分支 claude/new-session-ff6cac 提交 9e89c02 的导出快照（派工时为 7cb1ca7，检查时已前进）；main d6de849 的 DdlNamingTest、ddl-naming-baseline、data-classification；GUIDING_PRINCIPLES §6–§8；NAMING_STANDARD §2.4–§2.5；ADR-0016、0025、0026、0027（后者在 origin/claude/psa-authz-revisions）；EVENT_ANALYTICS_FIELDS §6；SESSION_PLAYBOOK §12、§14。
- 用户决定（原话，经 psa-coordinator 转达）：问「这些表符合olap、oltp、dama的核心思想吗？这么检查对吗？」后选「做，并把能自动化的变成守护 (Recommended)」（intent 片段 2026-10-14-164903）。
- 产出：phase1a/DATA_SCORECARD.md（34 表逐表评分、要改 K1–K15、取舍 T1–T5、守护规格 D1–D10、ACT_* 说明）；SESSION_PLAYBOOK §14.1 数据架构检查清单（15 项）。
- 验证：表结构由脚本按 26 个迁移推算（10 schema、34 表、322 列，未解析语句 0）；Java SQL 字面量扫描；结论逐条附迁移文件:行。子代理：清点 1 个（haiku）；判断由本会话（opus）。
- 未完成与阻塞：K1–K15 待 1a 下一批；T1–T5 待用户；守护由 1a 实现；ACT_HI_* 保留期待下一轮确认。
- 下一步命令：psa-coordinator 把 T1–T5 交用户、K1–K15 与守护规格派给 1a。
- 会话 ID（用于 claude --resume）：本会话（worktree xin-e2a79b，分支 claude/psa-data-scorecard）
