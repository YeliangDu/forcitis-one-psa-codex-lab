## 2026-10-14 · one-docs-naming · 文档去除旧名称 PSA
- 目标：按用户决定（原话见 INTENT）去除文档中的旧名称：指代新系统的 PSA 改为 FORCITIS one，指代老系统的改为「老系统」，字面标识保留；文档标题改；加守护。
- 输入：psa-coordinator 派工与对统计的 8 项决定（a 行业术语保留、b 英文 the legacy system、c session-log 不改、d brief 模板只换名称、e 标题改、f「新系统（PSA）」改老系统、g backend 断言交 1a、h adr/README 加说明）；基线 origin/main 0708eef。
- 用户决定（原话见 INTENT.md）：「当前仓库的所有文档里要避免使用 psa 旧名称，FORCITIS one 是新名称」；「老系统称「老系统」，字面标识保留 (Recommended)」；「文档标题改，仓库名和会话名暂不改 (Recommended)」。
- 产出：B1 标题、CLAUDE.md、README、SESSION_PLAYBOOK 会话命名 one-*、adr/README 说明、check_legacy_name.py 与白名单；B2 顶层文档、OA 盘点生成器、brief 模板；B3 规则卡与评审记录（英文 the legacy system）；B4 词表与术语表生成器（terms.yaml、GLOSSARY.md 重新生成）；B5 phase1a、r0、proposals、拓扑显示名；B6 guardrails 模板断言消息、调度台账一行、CI 接入守护。
- 验证：check_legacy_name.py 无违规；每批 build_all.py --check 通过；GlossaryTest 通过（B4 后）；抽样复核三批子代理的改动，纠正 A8 作业名（恢复字面并加反引号）、幻灯片原文、期初「项目级事实」的指代（统一为老系统）、拓扑根节点（老系统而非 FORCITIS one）、BI_SCOPE R2（FORCITIS one 自有事实）。子代理：B2、B3、B5 各 1 个（sonnet）；统计脚本、B4、复核由本会话（opus）完成。
- 关键发现：terms.yaml 不能把「PSA」列入 forbidden（G38 拆词会误伤 psa_ 字面标识与 Flyway 注释），守护改在文档层；backend/glossary/proposed/frontend-ui.yaml 已有提议术语 legacy_system（老系统），未在本单元转正，避免与 1a 在途分支冲突。
- 未完成与阻塞：backend SecurityInvariantsTest 断言消息由 1a 会话改；legacy_system 术语转正（建议加方言 PSA、老 PSA）待下一轮用语确认；topology.json / TOPOLOGY.html 未重新生成（依赖仓库外老代码）。
- 下一步命令：其他在途 PR 合并后若再引入 PSA，CI「no legacy system name in docs」会拦下，按脚本提示修。
- 会话 ID：本会话（worktree xin-a2d389，分支 claude/one-docs-naming）
