# 多人追加、经常冲突的共享文件（one-quality，2026-10-16）

依据：origin/main 最近 5 天非合并提交里，被提交次数最多的共享文件（排除每条一个片段的目录、业务代码）。次数是提交数，不等于冲突数；冲突多的另按“是否追加式”判断。

## 已改为片段（本 PR）

| 文件 | 做法 |
|---|---|
| `phase1a/infra/LEGACY_READ_REGISTER.md` | `legacy-read/` 每条一个片段，总表生成、不入仓 |

## 建议下一批改片段（追加式、多会话写）

| 文件 | 近 5 天提交 | 形态 | 建议 |
|---|---|---|---|
| `phase1a/infra/INFRA_RUNBOOK.md` | 68 | 章节 + 表格混合，one-ops 与各会话都改 | 先把表格类（如决定记录、检查项）拆出为片段；正文章节仍单文件，由 one-ops 独占写 |
| `phase1a/infra/PREDEPLOY_CHECKLIST.md` | 37 | 57 行表格 / 100 行，多会话追加一行 | 每项一个片段，`release/deploy_gate.py` 读生成的总表；它被发布脚本读取（relevant_change READ_BY_BUILDS），改动时要同步 |
| `phase1a/NEXT_BATCH_PLAN.md` | 33 | 232 行表格 / 611 行，调度与各会话更新状态列 | 状态列不适合片段；改为各会话只写自己的画像 / STATUS，计划表由调度独占 |
| `phase1a/infra/DEV_DEPLOY_REMAINING.md` | 21 | 63 行表格 / 89 行，追加待办 | 每项一个片段，完成即删片段 |
| `phase1a/DEFERRED.md` | 17 | 29 行表格 / 47 行 | 每项一个片段 |
| `phase1a/infra/RESOURCES.md` | 6（本周才多会话写） | 28 行表格 / 32 行，登记与删行 | 每项一个片段，总表生成已规定“建立时登记、用完删” |
| `docs/modernization/events/asyncapi.yaml` | 34 | 4366 行单文件 | 难按片段：按 BC 拆成多个文件再合并生成；须与 one-architecture 商定 |
| `backend/architecture/*.txt` 基线 / 例外清单（`module-dependencies.txt` 19 次、`unversioned-table-writes.txt` 12 次、`ddl-naming-baseline.txt` 9 次） | 见左 | 按行排序的清单：基线类（如 `ddl-naming-baseline.txt`）只减不增；`module-dependencies.txt`（批准的依赖图）、`unversioned-table-writes.txt`（例外清单）可增 | 不改片段：行级冲突易解；若冲突仍多，改为每模块一个文件 |
| `scripts/ci/relevant_change.py` 的 `READ_BY_BUILDS` / `LEGACY_IT_PATHS` 元组 | 23 | 代码内路径清单，守护文件 | 不改片段；冲突点小，改动须带复审 |

## 不在范围

- 调度独占：`COORDINATOR_LEDGER.md`（56 次）、`METHODS.md`（77 次，章节式正文）。
- 已是生成产物或片段：`INTENT.md`、`SESSION_LOG.md`、`STATUS.md`、`adr/README.md`、`MODERNIZATION_BRIEF.md`。
- 各会话自己的速查（`*_CONTEXT.md`）。
