## 2026-10-14 · psa-olap-architecture · OLAP 物理架构与数据流（ADR-0025）
- 目标：psa-coordinator 派工——先定领域事件的分析必带字段（交 1a 事件目录定稿），再起草 ADR-0025（提议）「OLAP 物理架构与数据流」（6 处：取代关系、物理位置、L1 构建、行级权限、过渡镜像、历史数据），并做 architecture-critic 评审。
- 输入：ADR-0009、0011、0016、0022、0023、0024；BI_SCOPE §3–§4、§6.1；r0/（BI_USAGE_SUMMARY、TOOL_COMPARISON、tfst_objects）；phase1a/A8_ACCESS_REGISTER #28–#32、INFRA_RUNBOOK（D-1）、SECURITY_INVARIANTS；GUIDING_PRINCIPLES §8、§9a；SESSION_PLAYBOOK §12。基线 origin/main fde2c74。
- 用户决定（原话，经 psa-coordinator 转达）：问「OLTP、OLAP 现在架构清晰吗」，选「现在写，并先定事件必带字段 (Recommended)」（intent 片段 099）。另：工时审批时限 T+1，超时事件计入审批时效统计（psa-coordinator 转达的输入）。
- 产出：proposals/EVENT_ANALYTICS_FIELDS.md（信封与过账负载契约，§6 为评审后修订）；adr/src/ADR-0025.md（提议，经 build_all 生成）；BI_SCOPE §3.2 总线矩阵增加「审批时效」。
- 验证：build_all 生成通过（25 份 ADR，必备小节校验通过）；architecture-critic（opus）对抗性评审 23 条（阻断 5、重要 13、建议 5），处理结果写入 ADR-0025 证据表。核心修改：财务事实由 L1 直读 operations.facts（视图起步，按 postSeq 物化），事件只用于过程事实与累积快照；L2 由关账水位门控、从 OLTP 生成；权责历史一律进 OLTP（部分取代 ADR-0011 第 2 条）；RLS 改为防应用缺陷的兜底（FORCE、失败即拒绝、禁物化视图）；镜像改为 staging + 单事务替换、敏感对象默认不镜像。子代理：architecture-critic 1 个（opus）。
- 未完成与阻塞：ADR-0025 待用户审批与 8 项决定；信封修订（删 recordedAt 等）须 1a 同步；ApprovalTaskOverdue / Escalated 事件尚未实现；工作日历引用 1a 的 organization.attendance。
- 下一步命令：用户审批 ADR-0025 后，R1 骨架会话按「需同步修改的守护规则」实现。
- 会话 ID（用于 claude --resume）：本会话（worktree xin-e2a79b，分支 claude/psa-olap-architecture）
