## 2026-10-15 · one-architecture · 架构 SSOT PR-A：骨架与子域 / BC / 上下文映射初稿
- 目标：按 intent 2026-10-15-160111（施工图、4 张图）建架构 SSOT；PR-A 交付设计说明、骨架与 DDD 战略层初稿。
- 输入（读过的状态文件、上一条日志）：CLAUDE.md；SESSION_PLAYBOOK §2.1、§14、§14.1；GUIDING_PRINCIPLES §1–§3；ADR-0003、0017、0022、0023、0036；sonnet 子代理盘点现有 SSOT 与抽取老仓库 00_战略设计（02、04、05、06）。
- 用户决定（原话）：无新增（mermaid-cli 安装与 ACL 用语单列由 one-coordinator 转达并记 intent）。
- 产出（文件 / 提交 / 发布的页面）：architecture/DESIGN.md（one-coordinator 审过）、README.md、ARCH_CONTEXT.md；ddd/subdomains.yaml（13 个子域，分类待 Q6）、ddd/contexts.yaml（41 个 BC：已建 13、规划 28）、ddd/context-map.yaml（130 条：已建 20 条与 module-dependencies.txt 一致，规划 110 条来自老 05）；画像 sessions/one-architecture.md。
- 验证（跑了什么、结果）：scripts/precheck.sh 通过（见 PR）。
- 未完成与阻塞：架构决策页（Q1–Q7）等 one-coordinator 通知再发（用户正在复核用语、ACL 用语单列）；已建边的关系类型为提议，待架构评审；PR-B 生成器与图①②。
- 下一步命令：PR-B：安装 mermaid-cli 到 ~/.modernize/psa/tools/mermaid-cli，写 architecture/build_architecture.py，生成图①②。
- 会话 ID（用于 claude --resume）：43cd30eb-c7d2-43a8-a4bd-41bc13a637a5（worktree xin-4e9c83，分支 claude/one-architecture-ssot）
