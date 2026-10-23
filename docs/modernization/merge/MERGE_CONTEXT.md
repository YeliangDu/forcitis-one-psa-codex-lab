# one-merge 上下文（MERGE_CONTEXT）

只记现行状态，一行一项，过程放链接（SESSION_PLAYBOOK TEAM-07、08）。接续会话先读本文件和 `sessions/one-merge.md`，不读 `COORDINATOR_LEDGER.md` 的旧队列指令。

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

## 现状（2026-10-22 23:00+02:00，节点交接）

- 队列：launchd 代理 `com.forcitis.mq` running；接手先 `mq.py status`、看 `merge.log` 末 30 行。10-22 晚 Docker Desktop 停过（镜像检查失败后 CI 转去 pull，撞钥匙串报错），backend verify 红在「test images are present」一步时先查 `docker info`，再重跑失败任务并重新入队，不必先找用户（one-coordinator 10-22 22:5x）。
- **手上的活**：PR #635（分支 `claude/one-merge-gapscan`，GOV-1022-MRG-05 + MRG-06）已按 opus 首轮 3 项必改改完（去重键不因一闪而没、CI 红只看三项必需检查并豁免 light、批准 sha 大小写；另 gaps.md 列全部、GAPS 行只写数量、状态文件校验、limit 41），头 89dfa4c45；**下一步**：①更新 PR 正文，单列改动的断言（`select_new` 消失再出现不再立即重报，原自测相反，依据 opus 复审必改项 1 与派工「4 小时内只报一次」）；②sonnet 复核改后（给代码不给结论）；③报 one-coordinator 后按其批准 head 入队；④「已批未入队」依据 PR 评论「调度批准入队 head <sha>」，当前无人发这类评论，COO-15 做完才生效，PR 正文已写明。MRG-06：入队时 `mq.py enqueue` 记当前 head（`<队列>.heads`），取走时与 CI 等待中、合并前对比，不同则 `HEAD-CHANGED` 出队（日志含新旧 sha 与作者会话），重新 `enqueue` 即视为新批准。
- **COO-15 未做**（one-coordinator 派，单列 PR、opus 首轮）：①入队依据改为 PR 上调度评论「调度批准入队 head <sha>」（作者 YeliangDu、正文含 head、head 变即失效），不靠跨会话消息；②合入后按 `scripts/merge/session_map.json`（分支前缀→会话）给负责会话发「#N 已合入（任务号）」并抄 one-coordinator；③钩子类 PR（.claude/、scripts/hooks/、.pre-commit-config.yaml）合入后列出启动早于合入时刻的会话报 one-coordinator（仅 settings.json 改动才需重启，脚本类每次从主检出现跑）。合入前 one-coordinator 仍发消息批准，合入后改评论。
- 断档报告：mq.py 在 launchd 里发不了会话消息，`GAPS` 行写入 merge.log、明细在 `<队列目录>/gaps.md`，由我挂的 Monitor 转 one-coordinator（它同意，10-22）。Monitor 关键字补 `GAPS|HEAD-CHANGED`；30 分钟到期重挂。#589、#587、#599 已派作者，断档里不再报这三个。
- 队列收文档口径：①one-coordinator 批的才收，作者追加提交号取分支 origin 末端（建合集那一刻再取，不逐条回应）；②one-glossary 的 `backend/glossary/proposed/*.yaml` 提交一律收，合集词表测试红就摘出退回（不必再问）；③合集里出现 .py/测试/CLAUDE.md 等非 docs 文件先核 one-coordinator 清单是否写明；④`CLAUDE.md` 等此前已批；⑤脚本 `fold.sh <train分支> <sha>…` 在 /private/tmp/claude-501/，丢了按下述重写：合 origin/main 与各提交，生成产物冲突取一边后 build_all，手写文件冲突中止；合集分支合入后 main 又前进时队列自己 update-branch，不必手工合。合集里 `git ls-files -ci --exclude-standard` 有输出（已不入仓的生成产物）要 `git rm --cached` 再推。
- 在开且我管的合集：#633（one-glossary 7a1087f26，等 CI）、#629（KERR-2）、#587（签章 api，head de4557aa9，one-platform-file 报、one-coordinator 预批）按批准 head 在队列里；#632 等 one-1a-par 报 head 且 one-coordinator 批准后才入。
- 待收文档（各会话直报，建合集时取最新末端）：one-arch-finance（79ab0c9a0 起）、one-arch-people、one-quality、one-data、one-rules、one-arch-revenue、nx5、one-platform-file、one-1a-par、one-coordinator-two-lanes（其工作区本地分支可能领先 origin，经共享对象库可直接收）。
- 重启判断只看 `.claude/settings.json` 有没有改（#616、#628 只改脚本，不用重启）。
- 每周例行分支与 worktree 清理待做；train-45…65 等我自己合集分支下次清理时删；`archive/*` 为归档，`generated-docs` 必须保留。
- 本机脚本（/private/tmp/claude-501/，丢失按用途重写）：`fold.sh`、`prbump.sh <PR>`（在 PR 分支的临时 worktree 合 main、解生成产物冲突、build_all；之后 `pnpm install --frozen-lockfile --offline` 于其 frontend/，再 `git push origin HEAD:refs/heads/<分支>`）、`autoenq2.sh pr:head…`（等 CI 四项结果，head 一致且无红才入队；后台跑时 description 要写成「<任务号> <名> (<执行者>)」）。注意：zsh 不拆变量，脚本用 bash 且 bash3 无关联数组；钩子在命令开始前跑 precheck，改文件/提交与 git push 分两次调用；推送路径不含 shell 变量；工作区没装前端依赖时 precheck 的词表前端项会挡推送，先 `pnpm install --frozen-lockfile --offline`；`mq.py remove` 停不掉在处理的 PR；`git push` 前核对分支；本工作区 `xin-c2cfec` 的分支会被其他会话（含复审子代理）切走，开工先 `git branch --show-current`。
- 批准口径不变：只收 one-coordinator 批准消息（经作者转述的条件批准，核一次 PR 上的复审结论即可，one-coordinator 10-22 22:5x），入队前比对批准的 head（之后只是合 main/重生成才算同一批准），手写文件冲突先报 one-coordinator，文档合集合入后通知相关会话，队列清空即扫在开 PR（现由 mq.py 每 30 分钟自扫）。
