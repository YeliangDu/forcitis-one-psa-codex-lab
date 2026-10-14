## 2026-10-14 · psa-1a-process-review · Phase 1a 流程梳理（7 个流程）
- 目标：按 SESSION_PLAYBOOK §13 为 Phase 1a 补做流程梳理——现状流程、目标流程、聚合与状态机、领域事件、权限、法人 / DMN / 状态归属、审批时限，差异逐条由用户决定，结论写入 INTENT 与 brief §5（psa-coordinator 派工）。
- 输入：SESSION_PLAYBOOK §3/§4/§5/§13、process/README.md、STATUS、AI_NATIVE_SPEC、REIMAGINED_ARCHITECTURE、RULE_REVIEWS、IDENTITY_ORG_RULES、BUSINESS_RULES、SECURITY_INVARIANTS、COLUMN_OWNERSHIP、TIMESHEET_REPLAY、OA_PROCESS_INVENTORY、runtime/、ADR-0026（psa-authz-model 分支）；老系统 psa 只读。
- 用户决定（原话见 intent 102–159）：范围「按建议 7 个、按此顺序」；各流程差异逐条或逐项勾选决定，绝大多数选推荐项；非推荐项：法人归属「1a 就建归属」（后由 ADR-0026 D-1 统一为组合模型）。经 psa-coordinator 转达：D-1a-35「加」、ADR-0026 D-1。与 ADR-0026 冲突的三处由用户选「合并：绑超管、不验在职」「归组织数据管理员」「审计归安全审计员，下线归管理模式」。
- 产出：process/1a/ 7 份流程文档（D-1a-01..181）与 contract.md（178 条，3 条文档更正不收）；brief 模板 Phase 1a 入口条件勾选、出口条件新增「在职但无企业微信 ID 清单为 0」；规格修订 10 处（AI_NATIVE_SPEC、GUIDING_PRINCIPLES、RULE_REVIEWS RULE-333 备注、runtime/README）；status/units/065。
- 验证：build_all.py 通过（brief §5 含 D-1a 条目 179 处引用）；gitleaks 无泄露；生产只读计数 11 条查询（云助手 + 只读 Java，只执行 SELECT，临时目录已删）。子代理：现状 / 目标 / 权限 / 文档修订用 sonnet（10 个），差异判断与建议由主会话 opus。
- 未完成与阻塞：需改代码约 70 项已报 psa-coordinator 转 1a 会话；ADR-0026 第 5 项第 2、6 条与 D-11 须按 intent 156–158 修订；ADR-0009 §6 第 21 行与 I-2 矛盾；ADR-0010 占位符与背景漏列；新企业微信自建应用（运维）。
- 下一步命令：Phase 1b 前新会话 `psa-1b-process-review`；Phase 2 流程梳理须决定 D-1a-127 所列工时单业务侧事项。
- 会话 ID：本会话（worktree xin-7143b1，分支 claude/psa-1a-process-review）
