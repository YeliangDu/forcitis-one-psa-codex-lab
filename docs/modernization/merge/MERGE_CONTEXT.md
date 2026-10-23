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
- 钩子或 `.claude/` 改动合入后同样快进主检出；settings 注册变化的重启清单按 TEAM-07。钩子及子调用统一从主检出运行待 GOV-1023-QUA-06，不把文件快进等同全员已生效。
- 两道：文档道（`relevant_change.py` 判 tier=docs）与代码道，合并持锁串行；文档道在代码道最终 CI 时让位（`WAIT-CODE-LANE`）。
- 暂停：main 的 push 或夜间运行红则两道自停（`PAUSED`）；只报 one-coordinator，`mq.py resume` 与恢复 strict 归它。

## 节奏

- 文档合集按 TEAM-53 自扫远端：比 main 多出、只改 docs/、提交带任务号的提交自动认领，混代码或缺号报缺口；代码只按 TEAM-45 明确批准清单人工入队。每个文档提交先核 `git diff origin/main...<提交号> --name-only` 只含 `docs/`（守护文件须清单写明、PR 正文「守护单列」）。
- 文档合集每天两次（中午、晚上）；阻塞类插队合集随时。合集分支 `claude/one-merge-train-N`，逐个分支合并，不用 octopus；推送前 `build_all.py` 与 `scripts/precheck.sh`，输出摘要贴 PR。
- 每次合集同时报积压 PR 盘点（被包含待关、冲突、超 48 小时、草稿、原因不明）。
- 合入节点通知按 TEAM-53、24 一次送达作者与调度；冲突、挂起、红 main 等需动作异常及时送达，不重复报号。
- 队列清空即扫在开 PR（one-coordinator 2026-10-22）：三类一次报——已批准未入队、冲突或 CI 红、超 1 小时没人动，附 PR 号与卡在谁手上。

- 文档合集里只要出现 .py 或测试文件改动，先报 one-coordinator，复审通过前不入队（2026-10-22；#574 因此挂起）。
- 只在生成产物（FREEZE_FACTS、GAPS、panorama、generated/、tasks/TASKS.md、ssot/11-commands）上冲突的 PR：我在其分支合 main、跑 build_all、推送后直接入队，不退回作者；人写的文件冲突才退回（one-coordinator 2026-10-22）。推送前门禁要几分钟，main 前进快时易再冲突，故 #568（队列自动重生成）合入后以它为主。
- 待 #574 重建时带入：admission-file、network-batch3、rules-context-2 的最新提交由 one-merge 建合集时自扫远端核 tip 与文件范围，不等待作者单独报号。

## 监视

- Monitor 两个（30 分钟到期重挂）：`merge.log` 关键事件（MERGED|CONFLICT|HOLD|ERROR|PAUSED|WAIT-SIGNAL）；main 上 ci 红运行。

## 分支清理（用户 2026-10-21 起归 one-merge，intent 2026-10-21-191158）

- 每周例行：删已在 main、无工作区占用、无在开 PR 的分支；本地先 `git branch -d`，被拒的逐个核 `merge-base --is-ancestor <分支> origin/main` 为真才 `-D`；远端经 `gh api -X DELETE .../git/refs/heads/<分支>`。
- 未合入的远端分支逐个问负责会话再定。2026-10-21 首轮：本地 794 个、远端 506 个已清。

- 下一趟文档合集待收：one-rules-context-2@10b2bb2c（仅 docs、与 main 无冲突，one-coordinator 此前已批同分支，建合集时自扫远端核 tip）；one-quality-window-note@adc95768（已解冲突，调度已批；除 docs 外只含 context_windows.json 的 _note 一行）；one-architecture 画像时间戳修正由合集自扫远端核实。
- 预批待自扫核 tip：claude/one-arch-revenue-closeout（4 个提交、全在 docs/；one-arch-revenue 解完冲突后由合集自扫，核 tip 与范围再收）。

## 坑

- 工作流文件的 PR：gh 令牌无 workflow 权限，队列改走 ssh 本地合并。
- 队列文件被并发改写会丢行：只经 `mq.py`。
- 钩子不展开命令里的 shell 变量路径：推送命令用绝对路径 `cd`。

## 现状（2026-10-23 18:45+02:00，节点交接；以此段为准，下一段是 10-22 的旧记录）

- 队列 [667, 680, 673, 678]，#667 处理中，未暂停。运行器 18:40 重启过（pid 62979，已加载 #676）。**铁律（one-coordinator 10-23 17:5x）**：`scripts/merge/` 每有改动合入，都在队列空档（上一个出队、下一个刚开始时）`launchctl kickstart -k gui/$(id -u)/com.forcitis.mq` 重启一次，重启前后核 `mq.py status` 一致，并往 merge.log 手记一行 `RESTART pr=- run=- elapsed=0s lane=code <原因>`（pr 写 `-`，写 `#-` 会让日志解析不认）。做成自动重启放 MRG-10 后续，未做。
- 合入策略 10-23 17:19 改过（intent 2026-10-23-171926：自动入队有条件放开、合批等），调度改完卡再派，新活等调度，不要自行恢复自动入队。
- MRG-10 四项（intent 2026-10-23-164349，一项一个 PR，one-quality 复核）：①急标记 #675 已合；②#676 已合（update 后等新头与新 CI，新 CI 未见的 moved 不计）；④#678 head 347f90daf 已合 main 解冲突，复核过的必改（ENV 按 ci.yml 真步骤名「docker is running」「test images are present」、所有红 run 只红环境步骤且 docker info 不通才算）已改，**等 one-coordinator 重批该 head**，已在队列；③#677（openapi.json 三方 JSON 合并＋generate-api.mjs 重生成）复核通过，但与用户原指令（取 main 版重导出）有偏离，调度正向用户确认，**确认前不入队**，现与 main 冲突（等确认做法后再 rebase）。四个 PR 在 mq.py/mq_selftest.py 互相冲突，按入队顺序合完一个后下一个要合 main。
- #669（MRG-07，分支 claude/one-merge-mrg07-02，工作区 `.claude/worktrees/mrg07-codex`）：head 244e5d2f8，已合 main 解冲突（MRG-01/05/06 三卡取 main 版，本 PR 不改卡）。**复核改由 one-quality 做**（不用 Codex）；必改项：删 `record_update`，改用 `await_new_head`（#676 已在 main）返回的头＋parents 校验；调度重派时再做。#669 待复核后批。
- 「急」标签：仓库里曾没有，调度问用户后建；调度打「急」的 PR 同道先取、不被 CANCEL-RUN。
- MRG-10 余项（卡里原有、未做）：定时每 2 小时同步在开 PR、预跑整轮 CI（verify 标签）；MRG-10 ③ 之后的文字以卡为准。MRG-02/05/06 卡的状态由调度在文档合集里改，我不改。
- 文档合集：one-data 的 22268017a（DATA_DESIGN_GATE 改指 ssot 路径）引用 ssot/t-transitions/transitions.yaml，**#673 合入前合集不收**，合入后的下一趟再收。TEAM-45 行冲突解法：规则文本取 two-lanes 版、守护索引列取 #654 的「A」。
- 全员合 main 的通知：#668 合入后由 one-coordinator 一次发，我没有广播渠道。
- 工作区：`xin-mrg10-urgent`（#678 分支 claude/one-merge-mrg10-env，另有 -update、-openapi 已用分支）、`mrg07-codex`（#669）。
- 脚本 `mq.py status` 在主检出目录跑；push 偶发被 pre_push_gate 的 `LAST_LOG` NameError 拦（#668 修了，已合），重试即可，不绕过。

## 旧：现状（2026-10-22 23:00+02:00，节点交接；已过时，仅作历史）

- 队列：launchd 代理 `com.forcitis.mq` running；接手先 `mq.py status`、看 `merge.log` 末 30 行。10-22 晚 Docker Desktop 停过（镜像检查失败后 CI 转去 pull，撞钥匙串报错），backend verify 红在「test images are present」一步时先查 `docker info`，再重跑失败任务并重新入队，不必先找用户（one-coordinator 10-22 22:5x）。
- **手上的活**：PR #635（分支 `claude/one-merge-gapscan`，GOV-1022-MRG-05 + MRG-06）已按 opus 首轮 3 项必改改完（去重键不因一闪而没、CI 红只看三项必需检查并豁免 light、批准 sha 大小写；另 gaps.md 列全部、GAPS 行只写数量、状态文件校验、limit 41），头 89dfa4c45；**下一步**：①更新 PR 正文，单列改动的断言（`select_new` 消失再出现不再立即重报，原自测相反，依据 opus 复审必改项 1 与派工「4 小时内只报一次」）；②作者按 TEAM-67 交 Codex 复核改后（给代码不给结论）；③报 one-coordinator 后按其批准 head 入队；④评论只留痕与核 head；入队按调度明确批准清单人工执行，每次登记执行会话、PR、head、依赖、时刻，调度每批审计；自动入队永久关闭（intent 115815）。MRG-06：入队时 `mq.py enqueue` 记当前 head（`<队列>.heads`），取走时与 CI 等待中、合并前对比，不同则 `HEAD-CHANGED` 出队（日志含新旧 sha 与作者会话），重新入队前须重新取得调度明确批准并核 head。
- 只报「复审通过」的 PR 先等 one-coordinator 明确批准后再人工进队；评论不能单独作授权。
- 断档报告：mq.py 在 launchd 里发不了会话消息，`GAPS` 行写入 merge.log、明细在 `<队列目录>/gaps.md`，由我挂的 Monitor 转 one-coordinator（它同意，10-22）。Monitor 关键字补 `GAPS|HEAD-CHANGED`；30 分钟到期重挂。#589、#587、#599 已派作者，断档里不再报这三个。
- 队列收文档口径：①按 TEAM-53 自扫远端只改 docs/ 且带任务号的提交，建合集时核 origin tip；②含 `backend/glossary/proposed/*.yaml` 或其他非 docs/ 文件不自动收，单独报范围由调度安排；③混 .py/测试/根 CLAUDE.md 等报缺口，不混进文档自扫；④原预批按当前范围重核；⑤脚本 `fold.sh <train分支> <sha>…` 在 /private/tmp/claude-501/，丢了按下述重写：合 origin/main 与各提交，生成产物冲突取一边后 build_all，手写文件冲突中止；合集分支合入后 main 又前进时队列自己 update-branch，不必手工合。合集里 `git ls-files -ci --exclude-standard` 有输出（已不入仓的生成产物）要 `git rm --cached` 再推。
- 在开且我管的合集：#633（one-glossary 7a1087f26，等 CI）、#629（KERR-2）、#587（签章 api，head de4557aa9，one-platform-file 报、one-coordinator 预批）按批准 head 在队列里；#632 等 one-1a-par 报 head 且 one-coordinator 批准后才入。
- 待收文档（TEAM-53 自扫远端，建合集时取最新末端）：one-arch-finance（79ab0c9a0 起）、one-arch-people、one-quality、one-data、one-rules、one-arch-revenue、nx5、one-platform-file、one-1a-par、one-coordinator-two-lanes（只收远端可核 tip，本地领先部分待推送后自扫）。
- settings 变更的重启判断按 TEAM-07；只改脚本是否已从主检出及子调用生效待 GOV-1023-QUA-06。
- 每周例行分支与 worktree 清理待做；train-45…65 等我自己合集分支下次清理时删；`archive/*` 为归档，`generated-docs` 必须保留。
- 本机脚本（/private/tmp/claude-501/，丢失按用途重写）：`fold.sh`、`prbump.sh <PR>`（在 PR 分支的临时 worktree 合 main、解生成产物冲突、build_all；之后 `pnpm install --frozen-lockfile --offline` 于其 frontend/，再 `git push origin HEAD:refs/heads/<分支>`）；历史 `autoenq2.sh` 禁止再运行，永久关闭自动入队；旧脚本/进程盘点与停用证据由 MRG-01／07 验收，本轮未操作进程。注意：zsh 不拆变量，脚本用 bash 且 bash3 无关联数组；钩子在命令开始前跑 precheck，改文件/提交与 git push 分两次调用；推送路径不含 shell 变量；工作区没装前端依赖时 precheck 的词表前端项会挡推送，先 `pnpm install --frozen-lockfile --offline`；`mq.py remove` 停不掉在处理的 PR；`git push` 前核对分支；本工作区 `xin-c2cfec` 的分支会被其他会话（含复审子代理）切走，开工先 `git branch --show-current`。
- 批准口径按 TEAM-45：只按调度明确批准清单人工入队，不以作者转述或评论单独作授权；入队前比对批准 head，不可读或不一致不入；合 main/重生成后的证据复用按 TEAM-48，记录验证；每次登记执行会话并由调度批次审计，手写文件冲突先报 one-coordinator，文档合集合入后通知相关会话，队列清空即扫在开 PR（现由 mq.py 每 30 分钟自扫）。
