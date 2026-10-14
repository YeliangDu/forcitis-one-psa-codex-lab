## 2026-10-14 · psa-authz-model · 授权模型 ADR-0026（提议）
- 目标：psa-coordinator 派工——写授权模型 ADR-0026（数据范围机制、法人维度、审批访问、职责分离、super_admin 边界、授权治理、敏感字段、老角色迁移），提议阶段做 architecture-critic 评审，列出需用户决定的事项。
- 输入（读过的状态文件、上一条日志）：STATUS、SESSION_PLAYBOOK §5、§12、adr/src/_readme_lifecycle.md、ADR-0009、ADR-0024（格式）、RULE_REVIEWS（RULE-270、307、336–353、363、366）、IDENTITY_ORG_RULES、REIMAGINED_ARCHITECTURE §2.1、§3、GUIDING_PRINCIPLES §6、U8C_CAPABILITY_MAP §2.2、SECURITY_INVARIANTS；main fde2c74 的 platform.identity / workforce / audit / approval 代码（sonnet 子代理盘点）。
- 用户决定（原话）：问「1a 还有哪些设计需要现在考虑？权限系统？」，选「写授权模型 ADR，1a 实现数据权限 (Recommended)」（经 psa-coordinator 转达，已用 new_intent.py 记录）。
- 产出（文件 / 提交 / 发布的页面）：adr/src/ADR-0026.md（提议；部分取代 ADR-0009 第 4 条）及生成的 adr/ADR-0026.md、adr/README.md；intent 片段；status/units 新单元；本日志。
- 验证（跑了什么、结果）：build_all.py 通过（25 份 ADR，必备小节齐全）；architecture-critic（opus）评审初稿：阻断 3、重要 14、建议 4，全部采纳并写入 ADR「证据」；修订稿未再派第二个评审代理。代码事实盘点用 sonnet。
- 未完成与阻塞：待用户批准 ADR-0026 并回答 D-1..D-7、D-9..D-13；批准后由 1a 会话按「1a 交付范围」实现并同步守护规则；法人限定与审批参与访问在 1b 实现前须按 §12.2 在 RULE_REVIEWS 登记新规则。
- 下一步命令：psa-coordinator 汇总 ADR-0026 的待决事项给用户；批准后 1a 会话实现。
- 会话 ID（用于 claude --resume）：本会话（worktree xin-af304c，分支 claude/psa-authz-model）
