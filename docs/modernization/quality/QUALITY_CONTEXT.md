# one-quality 速查（2026-10-17 12:00+02:00）

一行一项，过程放链接（SESSION_PLAYBOOK §2.1 第 13 条）。接续会话先读本文件、`sessions/one-quality.md`、`quality/defect-bank/one-quality.md`，再 `gh pr list --author @me` 核对在途 PR（以 gh 为准）。

## 工作守则要点
- 动基础设施前过兼容性预检清单；耗时类改动先按步骤拆数据；新增自测先破坏验证；接钩子/CI 的脚本端到端各跑一次放行与拦截；评审意见先核实再采信；交出前回测错题本。
- 子代理显式指定模型：首轮复审守护用 opus、改后复核 sonnet、取数 haiku；中文。**能交 Codex 的先交**（SESSION_PLAYBOOK §5：`codex exec -m gpt-6.1-sol -c model_reasoning_effort=xhigh -s workspace-write --ephemeral -o <结果>`，用 Bash run_in_background 启动，不用 nohup 脱离；派工单写在 scratchpad，我只做派工、审稿、验收）。守护文件改动 PR 单列、opus 首轮 +（有修正则）终审。
- 推送前钩子在整条命令执行前检查：新建 worktree 与后续命令分开跑；`gh pr view/edit` 须在仓库目录内。入队后的 PR 不再推；在制代码 PR ≤1（改造期调度给例外）；文档不开 PR，推 claude/one-quality-docs 由调度收合集。
- 验收裁定：实现会话来请求登记时，先请调度确认（或核对调度分支 COORD-2026-10-17.md 原文）再登记，ruled_by 写 one-coordinator。

## 在途（以 gh 为准）
- 选测 S 规则 + 影子日志：Codex 在 worktree `scratchpad/wt-s`（分支 claude/one-quality-tia-shadow，卡 `scratchpad/card-s.md`）后台跑，结果文件 `scratchpad/codex-s-result.md`；**clear 后先看它是否完成**，完成则审稿→复跑自测与 precheck→提交→开 PR→opus 首轮。执行开关默认关，启用须调度/用户另批；S 下平均仅省 ~3.4 秒，不得启用激进规则 R（31% 已知失败会被省）。
- B（push/PR 上 release 自测按「它读的东西变了才跑」）：Codex 已产出在 `scratchpad/wt-b`（分支 claude/one-quality-push-release-gate，结果 `scratchpad/codex-b-result.md`），**未审稿未提交未开 PR**；排在选测与并行度之后（每次只开一个 PR）。
- #466（登记 RULING-COORD-20261017-07，含 A8SourceFlagsIT）、#456（日报 Codex 两列）、#458（复发检测 CI 工作流，须在文档合集之后入队）、#460（交接钩子）等：调度/one-merge 入队中，以 gh 为准。

## 待办（顺序按调度：选测 S → 并行度 → B → E → C）
1. 并行度：failsafe/surefire 提高并行度或按类分片，压到单机 16 核上限，先在试验分支量实际节省（Docker 争用）再开 PR；改守护文件走 opus。
2. B 见上；E：补「取消原因」数据（给 CI 运行加原因标注）；C：failsafe 类调度/拆最慢类（先试验分支量）。
3. #464 合入后跑满 3–5 个代码 PR，用复盘脚本（`scratchpad/ci-data/fetch_jobs.py` 取数、`out/stats.py` 统计，未入库）复测 SQL Server 挪档的实际节省（SQL Server 步骤耗时、关键路径）报调度。
4. 单跑超过 60 秒的类清单已发调度（ReadOnlyAccountCheckIT 324、SingleOperatorModeStartupIT 100、ReadOnlyVerificationCommandIT 82；A 缩成只调短超时与加 --server.address，one-1a-par 做）。
5. 复发检测（#458）合入后撤会话内定时任务 227e981d 并删 RESOURCES.md 对应行；每晚 23:17 巡检会话内定时任务 b9569fde…（b9569dfe）7 天到期（约 10-22）前续设；`/clear` 后用 CronList 核对。
6. 长期：集成测试反模式检查（先只报告）；队列 ② 自动批量、③ 乐观并行；Testcontainers 观察；CS-1.11 基线生命周期条款草案；错题本已入册至 QUAL-018（待落实项合入后改已落实）。

## 已合入 / 已做的要点（main）
合并队列 mq.py（含 lane=、enqueue --after）、CI 三档、复发检测脚本与 CI 工作流（#458 待）、验收裁定守护 acceptance_rulings.py（ENFORCE，裁定记录 ACCEPTANCE_RULINGS.yaml）、db_scripts_aligned 比对函数授权与角色（#449）、会话日报 session_daily.py（#452，加 Codex 两列 #456）、SQL Server 七个验收类挪档（#464）、交接钩子（#460）。复盘文档：`quality/CI_TIMING_REVIEW.md`（含 §6 一分钟可行性、方案 H）、`quality/TEST_IMPACT_RESEARCH.md`（均在文档分支，随调度合集进 main）。

## 临时工作目录
`/private/tmp/claude-501/-Users-yeliang-Projects-FORCITIS-forcitis-one-psa--claude-worktrees-xin-61164f/ee4b3c7c-49b4-41a8-92b2-999df1d808bc/scratchpad/`：`wt-*` 是各 PR 的 worktree（wt-docs=文档分支、wt-s=选测影子、wt-b=B、wt-gr=主 worktree 管理用），`ci-data/`（CI 复盘数据与脚本）、`tia-data/`、`tia-out/`（测试影响研究数据）、`card-*.md`（Codex 派工单）、`codex-*-result.md`。会话结束后可 `git worktree remove`。
