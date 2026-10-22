# one-merge 上下文（MERGE_CONTEXT）

只记现行状态，一行一项，过程放链接（SESSION_PLAYBOOK §2.1 第 13 条）。接续会话先读本文件和 `sessions/one-merge.md`，不读 `COORDINATOR_LEDGER.md` 的旧队列指令。

## 队列

- 脚本：`scripts/merge/mq.py`（`enqueue/remove/before/list/status/run`，`enqueue n --after m` 声明依赖）；只用它改队列，不手工改队列文件、不手工 `gh pr merge`。
- 位置：队列 `<git-common-dir>/merge-queue/merge.queue`，日志 `merge.log`，输出 `run.out`，进程号 `run.pid`（`/Users/yeliang/Projects/FORCITIS/forcitis-one-psa/.git/merge-queue/`）。
- 运行方式（2026-10-21 用户批准「让 macOS 系统托管合并队列」）：launchd 用户代理 `com.forcitis.mq`（`~/Library/LaunchAgents/com.forcitis.mq.plist`，只含路径与 PATH，无令牌），工作目录为主检出 `/Users/yeliang/Projects/FORCITIS/forcitis-one-psa`，开机运行并保持存活，日志 `merge-queue/launchd.out`；app 重启不影响，不再手动拉起。
- 核对：`launchctl print gui/$(id -u)/com.forcitis.mq`（看 state=running 与 pid）；`mq.py status`。
- 脚本更新（改了 `scripts/merge/` 后）：先在主检出 `git -C <主检出> merge --ff-only origin/main`，再 `launchctl kickstart -k gui/$(id -u)/com.forcitis.mq`；停代理用 `launchctl bootout gui/$(id -u)/com.forcitis.mq`，装回用 `launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/com.forcitis.mq.plist`。
- 一个队列只许一个 runner（`mq.runner.lock`）：起手动 runner 前须先 bootout。
- 每次合入后都把主检出快进到 origin/main（用户经 one-coordinator 2026-10-21 要求，不只钩子类）：先查主检出在 main、`git rev-list origin/main..HEAD` 为 0、`git status -uno` 无已跟踪改动，再 `merge --ff-only`；任一不符则停下报 one-coordinator，不覆盖。
- 钩子或 `.claude/` 改动合入后：同样快进主检出，并报 one-coordinator 一行，由它判断是否请用户重启（桌面 app 读主检出的钩子）。
- 两道：文档道（`relevant_change.py` 判 tier=docs）与代码道，合并持锁串行；文档道在代码道最终 CI 时让位（`WAIT-CODE-LANE`）。
- 暂停：main 的 push 或夜间运行红则两道自停（`PAUSED`）；只报 one-coordinator，`mq.py resume` 与恢复 strict 归它。

## 节奏

- 只收 one-coordinator 批准的清单；每个提交先核 `git diff origin/main...<提交号> --name-only` 只含 `docs/`（守护文件须清单写明、PR 正文「守护单列」）。
- 文档合集每天两次（中午、晚上）；阻塞类插队合集随时。合集分支 `claude/one-merge-train-N`，逐个分支合并，不用 octopus；推送前 `build_all.py` 与 `scripts/precheck.sh`，输出摘要贴 PR。
- 每次合集同时报积压 PR 盘点（被包含待关、冲突、超 48 小时、草稿、原因不明）。
- 例行合入不逐个报；报异常（冲突、挂起、红 main）与合集完成。
- 队列清空即扫在开 PR（one-coordinator 2026-10-22）：三类一次报——已批准未入队、冲突或 CI 红、超 1 小时没人动，附 PR 号与卡在谁手上。

- 文档合集里只要出现 .py 或测试文件改动，先报 one-coordinator，复审通过前不入队（2026-10-22；#574 因此挂起）。
- 只在生成产物（FREEZE_FACTS、GAPS、panorama、generated/、tasks/TASKS.md、ssot/11-commands）上冲突的 PR：我在其分支合 main、跑 build_all、推送后直接入队，不退回作者；人写的文件冲突才退回（one-coordinator 2026-10-22）。推送前门禁要几分钟，main 前进快时易再冲突，故 #568（队列自动重生成）合入后以它为主。
- 待 #574 重建时带入：admission-file、network-batch3、rules-context-2 的最新提交以各会话届时所报为准，one-architecture 的提交号由其直报。

## 监视

- Monitor 两个（30 分钟到期重挂）：`merge.log` 关键事件（MERGED|CONFLICT|HOLD|ERROR|PAUSED|WAIT-SIGNAL）；main 上 ci 红运行。

## 分支清理（用户 2026-10-21 起归 one-merge，intent 2026-10-21-191158）

- 每周例行：删已在 main、无工作区占用、无在开 PR 的分支；本地先 `git branch -d`，被拒的逐个核 `merge-base --is-ancestor <分支> origin/main` 为真才 `-D`；远端经 `gh api -X DELETE .../git/refs/heads/<分支>`。
- 未合入的远端分支逐个问负责会话再定。2026-10-21 首轮：本地 794 个、远端 506 个已清。

- 下一趟文档合集待收：one-rules-context-2@10b2bb2c（仅 docs、与 main 无冲突，one-coordinator 此前已批同分支，新提交号为 one-rules 报）；one-quality-window-note@adc95768（已解冲突，调度已批；除 docs 外只含 context_windows.json 的 _note 一行）；one-architecture 画像时间戳修正待其报提交号。
- 预批待报提交号：claude/one-arch-revenue-closeout（4 个提交、全在 docs/；one-arch-revenue 解完冲突后报我，核 tip 与提交号一致再收）。

## 坑

- 工作流文件的 PR：gh 令牌无 workflow 权限，队列改走 ssh 本地合并。
- 队列文件被并发改写会丢行：只经 `mq.py`。
- 钩子不展开命令里的 shell 变量路径：推送命令用绝对路径 `cd`。

## 现状（2026-10-22 16:55+02:00，/clear 前交接）

- 队列 `[608, 606, 604, 609]`：#608（one-1a-admin-docs@2f74de2f6）、#609（one-1a-par 交接@661688ecd）为文档合集；#606（钩子三合一，守护文件，批准 head c922c7591，队列同步后 head 会变）；#604（KERR，one-1a-par，批准 head 446fcdd2f，与 main 有冲突，队列轮到时只能自动解生成产物，人写文件冲突退回作者）。launchd 代理 `com.forcitis.mq` running。
- **#606 合入后必做**：在主检出跑 `pre-commit install --install-hooks -t pre-commit -t commit-msg -t pre-push`，核对 `.git/hooks/commit-msg` 存在，报 one-coordinator 一行（钩子目录各工作区共用，装一次）。
- #587（one-platform-file 签章 api，批准 head a13848a93）卡在 openapi.json 快照过期（backend verify 红），作者会话已归档，等 one-coordinator 定谁接；修好 CI 绿再入。
- 待收文档（各会话直报提交号，收进下一文档合集）：one-data-handoff-1、one-quality-*、one-architecture-*（admission-file、network-batch3 等）、one-rules-context-2、one-coordinator-two-lanes、one-arch-*-closeout；脚本/测试文件改动先报 one-coordinator。
- 远端分支：已合入的我自己的合集分支 train-45/46/47/48… 下次清理时删；`archive/*` 27 个为归档（archived.txt）；`generated-docs` 必须保留。每周例行清理待做（已合入分支、干净 worktree；规则见上）。
- 本机常用脚本（在 /private/tmp/claude-501/，若丢失按下述重写）：`fold.sh <train分支> <sha>…`（建合集：合 main 与各提交，生成产物冲突取一边后 build_all，手写文件冲突中止）；`prbump.sh <PR>`（在 PR 分支的临时 worktree 合 main、解生成产物冲突、build_all；之后手动 `pnpm install --frozen-lockfile --offline` 于其 frontend/，再 `git push origin HEAD:refs/heads/<分支>`）；`autoenq2.sh pr:head…`（等 CI 四项出结果，head 一致且无红才入队）。注意：①zsh 不按换行拆变量，脚本用 bash；②macOS bash3 无 `declare -A`；③钩子在命令开始前跑 precheck，推送与改文件分两次调用；④推送路径不能含 shell 变量；⑤合并 `mq.py remove` 停不掉在处理的 PR；⑥已不入仓的产物（FREEZE_FACTS.md 等）合 main 后要 `git ls-files -ci --exclude-standard` 查并 `git rm --cached`。
- 批准口径不变：只收 one-coordinator 批准消息，入队前比对批准的 head（后续只是合 main/重生成才算同一批准）；手写文件冲突先报 one-coordinator；文档合集合入后通知相关会话；队列清空即扫在开 PR 三类报调度。
