# 合并队列切换步骤：旧 mq_run2.sh 切到 scripts/merge/mq.py

适用：one-merge 在调度（one-coordinator）旁观下执行；决定与前提见 `intent`（用户 2026-10-16：不用第三方，自建队列）。strict（分支保护「必须基于最新 main」）**仍开着**先跑几个，稳定后由调度报用户再关，本页不含关 strict（末尾只列前提）。命令里的路径以调度台账「合并」节为准；下面用 `<仓库>`（队列运行所在的检出目录）、`<旧队列>`（旧 `merge.queue`）、`<队列目录>`（`<仓库的 git 公共目录>/merge-queue`，即 `$(git rev-parse --path-format=absolute --git-common-dir)/merge-queue`）。

## 0 切换前提（缺一不切）

- #441（队列）、队列增量（重复步骤红不重跑；strict 开着时只落后纯文档的 PR 被 GitHub 报 BEHIND 会同步重跑）、#442（CI 三档）已在 main；`<仓库>` 已同步 main。
- `python3 scripts/merge/mq.py --self-test` 在 `<仓库>` 通过（约 7 秒，输出 `mq self-test: ok`）。
- `gh auth status` 正常；`gh pr list --state open` 能列出队列里的 PR。
- 旧 runner 正在跑的 PR 已有结果（合入、HOLD 或冲突），不要在 `merge_pr.sh` 处理某个 PR 的中途停（见第 1 步）。

## 1 停旧进程

```bash
pgrep -fl 'mq_run2.sh|merge_pr.sh'     # 看旧 runner 与它正在处理的脚本
```

- 只有 `mq_run2.sh`（队空等待或在两个 PR 之间）：`pkill -f mq_run2.sh`，再 `pgrep -fl 'mq_run2.sh|merge_pr.sh'` 应无输出。
- 同时有 `merge_pr.sh 号`：等它结束（日志出现 `MERGED号` / `CONFLICT号` / `HOLD号`，或 `gh pr view 号 --json state`），**先不停**；结束后旧 runner 会马上取下一个，所以看到 `merge_pr.sh` 退出的那一刻立刻 `pkill -f mq_run2.sh`。拿不准就先把 `<旧队列>` 里除队首外的行记到别处再停（队列文件会被旧脚本的 `sed -i` 改，别在它运行时编辑）。
- 同时停掉监视旧队列的 Monitor（若有），避免它报「队列进程已退出」。

## 2 迁队列文件

按原顺序登记到新队列（`enqueue` 加锁、原子写，重复的号不会重复加）：

```bash
cd 仓库
nums=$(grep -E '^[0-9]+$' 旧队列 | tr '\n' ' ')
echo "$nums"                                   # 先看一眼：顺序与调度记录一致
python3 scripts/merge/mq.py enqueue $nums
python3 scripts/merge/mq.py list              # 与 $nums 逐行一致
cp 旧队列 旧队列.pre-cutover                   # 旧文件留作回退依据，不删
```

已 MERGED 或已关闭的号不用登记（`gh pr view 号 --json state`）；若旧队列里有合集号，原样登记。

## 3 起跑前核对

```bash
cd 仓库
python3 scripts/merge/mq.py status
```

- 应见 `paused: no`；`red main runs: none`。若 `paused` 或有红的 main 运行：停下，把输出报调度（`mq.py resume 运行号…` 只由调度确认后执行，且运行号必须与 `status` 显示的红运行完全一致）。
- `gh run list --workflow ci --branch main --event push --limit 3` 与 `--event schedule --limit 3`：最近的已结束运行是绿的（取消的不算）。
- `ls 队列目录`：有 `merge.queue`；起跑后会出现 `merge.log`、`merge.queue.lock`、`mq.runner.lock`。

## 4 启动

```bash
cd 仓库                                        # 须在仓库内启动（gh 要仓库上下文）
nohup python3 scripts/merge/mq.py run >> 队列目录/run.out 2>&1 &
echo $! > 队列目录/run.pid
tail -f 队列目录/merge.log                     # 旁观
```

- 一个队列只许一个 `run` 或 `once`（`mq.runner.lock`）；再起一个会直接报 `another mq.py run or once is working on this queue`。
- 两道并行：代码道（含中间档）与文档道（`relevant_change.py` 判 tier=docs）；合并由锁串行。strict 开着时，文档道的 PR 若被 GitHub 报 BEHIND，队列同步后重跑，不会去撞合并（自测 `bd-strict2`、`bd-strict3`、`bd-strict4` 覆盖：只落后纯文档、合并前 GitHub 报 BEHIND → 不尝试合并、同步后重跑；合并被拒而状态为 BEHIND → 算同步不算拒绝，最多同步 6 次后 HOLD「main moved」，不死循环、不误判成「被拒 5 次」）。

## 5 核对（头 3 个 PR 旁观，之后看日志即可）

每个 PR 的日志事件应依次是：`START` →（落后则 `UPDATE` 或 `SSH-UPDATE`）→ `LABEL-QUEUED` → 等 CI → `MERGED`（带 head 前 10 位）→ `DEQUEUE … MERGED`。逐项核：

- 日志每行形如 `2026-10-17T…+02:00 MERGED pr=#123 run=38… elapsed=…s head 前缀`；
- `gh pr view 号 --json state,mergeCommit`：已合入，合并方式是 merge commit；
- 没有出现 `HOLD`、`ERROR`、`WAIT-SIGNAL`、`PAUSED`；出现任一个，按下表处理并报调度：

| 事件 | 含义 | 处理 |
|---|---|---|
| `HOLD` | 检查红（已重跑一次）、CI 超时 90 分钟、PR 已关闭、同步推不上去、新测试重复步骤红（不重跑） | 日志里有失败检查名与运行号；退回作者或调度判断；该号已自动移出队列 |
| `CONFLICT` | 与 main 有文本冲突 | 退回作者（生成产物冲突按旧做法：取一边、跑 `build_all.py`） |
| `PAUSED` | main 的 push 运行或每晚全量的裁决运行红 | 两道都停；调度确认后 `mq.py resume 运行号…` |
| `WAIT-SIGNAL` | gh 读不到 main 的运行 | 两道等待，不放行；查网络与 gh 登录 |
| `WAIT-CODE-LANE` | 文档道让位代码道的最终 CI | 正常，不用处理 |
| `ERROR` | 一轮里某步抛异常，会退避重试，第 3 次同一 PR 失败转 HOLD | 看详情；反复出现报调度 |

日常命令：`mq.py list`（队列）、`mq.py status`（暂停与红运行）、`mq.py enqueue 号…`、`mq.py remove 号…`、`mq.py before 号 另一号`（插队）、`mq.py once 号`（只处理一个，不碰队列；须先停 `run`）。**不要**手工 `echo >> merge.queue` 或 `sed -i` 改队列文件，也不要手工 `gh pr merge`（main 只经队列合并，SESSION_PLAYBOOK TEAM-47；手工合并绕过合并锁）。

## 6 回退（任一时刻可做，不丢任何 PR）

```bash
kill "$(cat 队列目录/run.pid)"                  # 或 pkill -f 'scripts/merge/mq.py run'
pgrep -fl 'scripts/merge/mq.py'                  # 应无输出
cd 仓库
python3 scripts/merge/mq.py list > /tmp/mq-remaining.txt
cat /tmp/mq-remaining.txt                        # 还没处理完的号，顺序保留
```

- 正在处理的 PR 的状态都在 GitHub 上（`queued` 标签、检查、分支），中途停不会丢；它仍在队列里（只有处理完才移出），旧 runner 会重新处理它。
- 把剩余号写回旧队列：`cp /tmp/mq-remaining.txt 旧队列`（一行一个号，旧脚本只认纯数字行），然后按台账「合并」节的命令重启旧 runner（先 `cd 仓库` 再启动调度 scratchpad 里的 `mq_run2.sh`，常驻，须在仓库目录启动）；`pgrep -fl mq_run2.sh` 有输出、日志里出现对队首的处理即回退完成。
- 若队列因红的 main 运行暂停（`队列目录/mq.paused`）：回退到旧 runner 不受此影响（旧脚本没有这个信号），回退前先把原因报调度。
- 保留 `队列目录/merge.log` 与 `run.out` 供复盘，不删。

## 7 稳定的判据与关 strict 的前提（调度另行决定并报用户）

- 用 `mq.py` 连续合入 ≥10 个 PR，覆盖：落后同步、ssh 同步（改 workflow 的 PR）、文档道与代码道并行各至少 2 个、至少一次 HOLD 或 CONFLICT 的正确处理；期间无 ERROR 反复、无错合（`git log --first-parent origin/main` 里每个合并的 PR 当时检查全绿）。
- CI 三档（#442）与 push main 的免容器单测报警已在 main 上跑过（`gh run list --workflow ci --branch main --event push` 最近为绿），每晚全量（schedule）最近为绿，复发检测在跑。
- 以上满足后，由调度报用户；关 strict 是改分支保护设置，属用户决定，本页不执行。
