## 2026-10-14 · psa-authz-model · ADR-0027（提议）流程梳理带来的身份与授权修订
- 目标：psa-coordinator 派工——以新 ADR 部分取代 ADR-0026 第 5 项第 2、6 条等，吸收流程梳理 intent 156–158；消除 ADR-0009 第 6 条与 I-2 的矛盾；ADR-0010 勘误。
- 输入（读过的状态文件、上一条日志）：ADR-0009、ADR-0010、ADR-0026（已采纳）；origin/claude/psa-1a-process-review-2 上的 intent 155–159、process/1a（登录与会话、审计、业务编号、组织与人员同步）与 contract（D-1a-32、47、48、144、146、147、157、172）；main 上 NumberingPattern、LoginAsEmergencyAdmin、HandleOrgSnapshotChange、SessionPrincipals。
- 用户决定（原话）：本会话无新的用户原话；实现的是 intent 156「合并：绑超管、不验在职 (Recommended)」、157「归组织数据管理员 (Recommended)」、158「审计归安全审计员，下线归管理模式 (Recommended)」（psa-1a-process-review 已记录，未重复写 intent）。
- 产出（文件 / 提交 / 发布的页面）：adr/src/ADR-0027.md（提议）及生成的 adr/ADR-0027.md、adr/README.md；本日志；status/units 新单元。
- 验证（跑了什么、结果）：build_all.py 通过（27 份 ADR，必备小节齐全，取代清单可解析）；architecture-critic（opus）评审初稿：阻断 2、重要 7、建议 4，全部采纳并写入「证据」；修订稿未再派第二个评审代理。
- 未完成与阻塞：待用户批准 ADR-0027 并回答 Q-1..Q-4；采纳时同一提交标注 ADR-0009、0010、0026 状态，并同步 SECURITY_INVARIANTS I-2、I-11 与 AI_NATIVE_SPEC C1a-08。评审发现 ADR-0026 第 6 项第 4 条「离职收回角色（已实现）」与代码不符，已在 ADR-0027 背景中更正，并列为 1a 必须补齐。
- 下一步命令：psa-coordinator 交用户审批；批准后由 1a 会话实现。
- 会话 ID（用于 claude --resume）：本会话（worktree xin-af304c，分支 claude/psa-authz-revisions）
- 采纳（同日，接续）：psa-coordinator 转达用户决定——Q-1「明确离职就拒绝」、Q-2「1a 暂不列敏感，1b 前复核」、Q-3「暂停，转人工确认」、Q-4「新设「业务管理员」系统角色，由我持有；批准 ADR-0027」（均为 Recommended）。ADR-0027 改为已采纳；ADR-0009、0010、0026 状态行标注部分取代；同步 SECURITY_INVARIANTS I-2（无条件）、I-11，AI_NATIVE_SPEC C1a-08 与 §5.3；STATUS 阻塞新增 070（1a 下一批实现项）；两条 intent。合并 origin/main 时生成文件冲突，按片段重新生成解决。build_all 通过。
