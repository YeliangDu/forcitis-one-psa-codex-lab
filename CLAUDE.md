# FORCITIS one 现代化——agent 须知

开场钩子已注入「开工单」（身份、任务、交接、近期决定）。规范不通读，遇事按索引查。

## 底线

1. 用中文对话、写报告与跨会话消息；派子代理指定模型并要求中文回复。
2. 老代码、老系统与外部系统只作证据，不照搬结构与命名；设计依规格、规则卡、ADR、terms.yaml 与 AEC 行业通行做法；复刻老行为以生产数据为证。
3. 不写密钥字面量（测试运行时生成）；不打印、不调用返回密钥的接口。
4. 生产取证不逐次请示，但只用专用只读账号，明细与个人信息不进会话与仓库，每次登记（ADR-0037）；不做生产写操作。
5. 须用户决定的事（PB §7）及建账号、起容器、对外调用：写收件箱等答复，不替用户批准。例外：backend/architecture/test-images.txt 登记镜像经 Testcontainers 起的临时容器。
6. 用户决定当场 new_intent.py 记原话并报 one-coordinator；已定的照做，有异议停下报告；转述他人结论先核原文。
7. 只在自己 worktree 分支工作，git 写操作用绝对路径；推送前核分支，force-push 已开 PR 先告知调度；main 只经合并队列，不手工合并。
8. 不为过测试放宽或删断言（改则单列理由）；缺失或无效输入失败并告警。
9. 钩子或 CI 挡住按提示改，不绕过（不用 --no-verify）。gitleaks、主检出、推送前 precheck、系统称呼、命名用语守护已由机器强制。
10. 完成点与结束前报 one-coordinator，文档提交号报 one-merge。

## 索引（PB/CS/NS = SESSION_PLAYBOOK/CODING_STANDARD/NAMING_STANDARD.md；除 scripts/ 外路径相对 docs/modernization/）

- 编码：CS（1.1 注释、1.8–1.10、2.24、5.5/5.6、7.6）
- 命名、包结构 NS §2–3；DDL NS §2.4/2.4a；接口 NS §2.6
- 数据设计 PB §14/14.1；迁入 §14.2；集成 §14.3
- 用语：glossary/terms.yaml；登记 PB §5「写代码规则第二批」5；文档方言 glossary/new_term_issue.py
- 系统称呼（FORCITIS one／老系统）：check_legacy_name.py
- Codex/DeepSeek：PB §5 + templates/WORK_CARD.md
- 子代理模型、任务号：PB §5
- 取证、迁入读取：PB §5、ADR-0037
- 交接、开会话、画像、后台登记：PB §2.1、templates/HANDOFF.md
- 记录片段、进不进 git：PB §4 第 0 步、§12.6
- 消息、outbox：PB §11「消息约定」
- 收件箱、确认页：rules/decide/README.md、rules/decide/inbox/README.md
- ADR 与规则变更 §12；流程 §13；修复批 §6.1
- 交付：CS §7、scripts/precheck.sh（输出贴 PR）
- 外部知识、架构判断：PB §5「架构判断」
