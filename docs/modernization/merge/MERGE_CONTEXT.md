# one-merge 上下文（MERGE_CONTEXT）

只记现行状态，一行一项，过程放链接（SESSION_PLAYBOOK §2.1 第 13 条）。接续会话先读本文件和 `sessions/one-merge.md`，不读 `COORDINATOR_LEDGER.md` 的旧队列指令。

## 队列

- 脚本：`scripts/merge/mq.py`（`enqueue/remove/before/list/status/run`，`enqueue n --after m` 声明依赖）；只用它改队列，不手工改队列文件、不手工 `gh pr merge`。
- 位置：队列 `<git-common-dir>/merge-queue/merge.queue`，日志 `merge.log`，输出 `run.out`，进程号 `run.pid`（`/Users/yeliang/Projects/FORCITIS/forcitis-one-psa/.git/merge-queue/`）。
- 运行目录：`.claude/worktrees/xin-c2cfec`，detach 到 origin/main；改了 `mq.py` 后先同步 main 再重启 runner，且在队列空时重启。
- 两道：文档道（`relevant_change.py` 判 tier=docs）与代码道，合并持锁串行；文档道在代码道最终 CI 时让位（`WAIT-CODE-LANE`）。
- 暂停：main 的 push 或夜间运行红则两道自停（`PAUSED`）；只报 one-coordinator，`mq.py resume` 与恢复 strict 归它。

## 节奏

- 只收 one-coordinator 批准的清单；每个提交先核 `git diff origin/main...<提交号> --name-only` 只含 `docs/`（守护文件须清单写明、PR 正文「守护单列」）。
- 文档合集每天两次（中午、晚上）；阻塞类插队合集随时。合集分支 `claude/one-merge-train-N`，逐个分支合并，不用 octopus；推送前 `build_all.py` 与 `scripts/precheck.sh`，输出摘要贴 PR。
- 每次合集同时报积压 PR 盘点（被包含待关、冲突、超 48 小时、草稿、原因不明）。
- 例行合入不逐个报；报异常（冲突、挂起、红 main）与合集完成。

## 监视

- Monitor 两个（30 分钟到期重挂）：`merge.log` 关键事件（MERGED|CONFLICT|HOLD|ERROR|PAUSED|WAIT-SIGNAL）；main 上 ci 红运行。

## 坑

- 工作流文件的 PR：gh 令牌无 workflow 权限，队列改走 ssh 本地合并。
- 队列文件被并发改写会丢行：只经 `mq.py`。
- 钩子不展开命令里的 shell 变量路径：推送命令用绝对路径 `cd`。

## 现状

见 `sessions/one-merge.md` 的「当前状态」；队列实况看 `mq.py status` 与 `merge.log`，不抄数字。
