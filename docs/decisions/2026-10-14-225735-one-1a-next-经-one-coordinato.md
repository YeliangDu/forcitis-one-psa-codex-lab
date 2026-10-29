## 原话

（经 one-coordinator 转达，已在其会话记录中核实）Q1 迁移基线开放期间 R-1/R-2/R-4 怎么写：「开放期内直接改基线 (Recommended)」

## 落点

SESSION_PLAYBOOK §12.2「首次部署前的迁移基线」：先纯合并基线并证明 diff 为空；开放期内表结构改动直接改基线文件，每个 PR 附预期表结构 diff（两个空库 pg_dump --schema-only 对比）由 opus 核对；首次 dev 部署前删除开放标记、恢复只追加；migrations_immutable.py 放行逻辑与之一致（W0）。phase1a/NEXT_BATCH_PLAN.md 第 4 节
