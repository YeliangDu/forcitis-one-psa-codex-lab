## 2026-10-14 · psa-sec-hotfix · 新系统安全不变量（接续第 7 轮）
- 目标：按用户决定，老系统不再处理，确保新系统没有 SEC-116 至 121 这类缺陷。
- 输入：origin/main b9868aa（含检查点 2：桥接已删除）；AI_NATIVE_SPEC、REIMAGINED_ARCHITECTURE、ADR-0009、RULE_REVIEWS（RULE-265/328/376）。
- 用户决定（原话）：「老系统先不管，确保新系统没有这些缺陷」；身份桥接「取消桥接 (Recommended)」（与检查点 2 Q-1 一致）。
- 产出：phase1a/SECURITY_INVARIANTS.md（I-1..I-9，每条带验收测试）；ADR-0009 §6 补充「老 PSA 后端不得作为签名票据签发方」；INTENT 两条；STATUS 老系统安全止血改为搁置。
- 验证：逐项核对已有规格——已覆盖：短信登录不迁移、发起人只取当前用户、`touser` 精确不带 `@all`、回调以 FromUserName 定人、公开端点白名单、票据唯一约束；新增：老后端不得签发票据（I-2）、发起时单据归属与重新提交（I-5）、空收件人不发送（I-6）、所有任务操作校验处理人（I-7）、一次性凭证并发测试（I-9）。
- 未完成与阻塞：I-1..I-9 需并入 AI_NATIVE_SPEC §5.3 出口测试与守护模板（1a 会话）；brief 入口条件「老系统已止血」的 `Proposed revision:`（psa-coordinator）；AI_NATIVE_SPEC 第 132 行 OpenAPI 仍保留桥接端点注释，需随 Q-1 删除。
- 下一步命令：Phase 1a 会话将 SECURITY_INVARIANTS 的测试列入出口条件并实现。
- 会话 ID：本会话（worktree xin-af304c，分支 claude/psa-sec-hotfix-r7）
