## 2026-10-14 · psa-r0-metric-baseline · R0 其余内容与收尾
- 目标：psa-coordinator 派工——B6–B11 请用户确认、F6–F10 并入待财务清单、Q5 复制 OAT、Q12 问题模板、工具对比、Q11 BI 审计计数、第三轮用语输入；补派：生产 SQL Agent 通道登记。
- 用户决定（原话）：B6「保留，作为订单前置指标 (Recommended)」；B7「保留五种，其余推导 (Recommended)」；B8「同意两项 (Recommended)」；B9「迁入激励模块并版本化 (Recommended)」；B10「作为外部参考数据集纳入 (Recommended)」；B11「纳入，作为报表包 P8 (Recommended)」（本会话当面询问）；Q11「同意；无权时我另给只读账号」、Q1「C 为主，D 待定为后期可选 (Recommended)」、透视「沿用 AG Grid 企业版」（经 psa-coordinator 转达）。
- 产出：r0/FINANCE_QUESTIONS.md（FQ-01..20，含 ADR-0022 评审 C-5）；glossary/oat/（OAT 正本 4 个文件，来源未提交，以 SHA-256 记版本）；r0/QUESTION_TEMPLATE.md 与仓库外 questions.md、answers.md；r0/bi_audit_readonly_queries.sql、r0/BI_USAGE_SUMMARY.md、r0/structure/tfst_objects.md；phase1a/A8_ACCESS_REGISTER.md #28–#32 与 OA_PROCESS_INVENTORY §5.3 #37–40；r0/TERMS_ROUND3_INPUT.md（A 组 19 项与约 45 个新词条起草稿；「本年度未开票」改挂合同资产）；r0/TOOL_COMPARISON.md 与 r0/tool-sample/（含 R1 入口条件草稿）；BI_SCOPE §6.1 Q1、INTENT 两行、STATUS。
- 验证：生产只读共 9 次云助手执行（BI 库权限探测通过，未用其他凭据；只取计数与元数据；服务器临时目录每次删除；原始结果在仓库外 runtime/，权限 600）；工具小样合成数据、本机 Docker，四选项三看板逐格一致，容器与网络已删除；生成器重跑无差异；gitleaks 通过。子代理：工具小样 1 个（sonnet）。流程问题：小样子代理在本机一次性 Metabase 容器中用随机测试凭据创建了管理员账号，未事先征得同意，已如实报告并转告用户。
- 未完成与阻塞：B1–B5、问题清单待用户填写；FQ-01..20 待财务；第三轮用语确认待合并；R1 入口条件待 psa-coordinator 并入 brief。
- 下一步命令：R1（reporting 骨架与语义层）入口条件满足后新会话；财务答复到后回填基线。
- 会话 ID：本会话（worktree xin-e2a79b，分支 claude/psa-r0-seg2）
