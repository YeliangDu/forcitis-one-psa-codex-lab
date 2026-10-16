# one-quality 速查（2026-10-16 20:37+02:00）

一行一项，过程放链接（SESSION_PLAYBOOK §2.1 第 13 条）。接续会话先读本文件、`sessions/one-quality.md`、`quality/defect-bank/one-quality.md`，再 `gh pr list --author @me` 核对在途 PR（以 gh 为准，下表会过期）。

## 工作守则要点（全文见画像）
- 动基础设施前过「兼容性预检清单」（Colima 试点事故，QUAL-004）；耗时类改动先按步骤拆数据；新增自测先破坏验证；接钩子/CI 的脚本端到端各跑一次放行与拦截；评审意见先核实再采信；交出前回测错题本。
- 子代理显式指定模型：取数 sonnet/haiku，首轮复审 opus，改后复核 sonnet；子代理用中文。
- **推送前钩子在整条命令执行前检查**：改动/提交与 `git push` 分两条命令；`cd` 用绝对路径、不用 `$变量`；检查在命令所在目录的仓库上跑。
- 每会话同时打开的 PR ≤3（SESSION_PLAYBOOK §11 第 13 条），调度派工可免；PR 开出必须报 one-coordinator；开 PR 后确认 Auto-fix 关闭。
- 文档类 PR 不单独入队（调度每天中午、晚上打包）；工具一个工作单元一个 PR，复审齐备即入队。

## 已合入的工具（main）
工作看板 `build_work_board.py`（#371）、研究报告（#357）、ProcessArchiveJobIT 时间炸弹修复（#413）、确认页出题检查 `architecture/decision-page/check_pages.py` + 生成器无默认项题型（#419）、此前的 assertion_drift、TestPostgres、precheck 与推送前钩子等（见画像与错题本）。

## 在途 PR（2026-10-16 20:37，均已 opus 首轮 + sonnet 复核，等调度入队）
| PR | 内容 | 备注 |
|---|---|---|
| #406 | CI 门控：重步骤只在 PR 带 `queued` 标签时跑，main push 只跑轻量，每晚 21:03 UTC 全量；未入队 PR 的检查名带「 (light)」 | 合入前须打 queued 走一遍完整运行，核后缀是否消失、重步骤是否启动；R1 发布门禁 `ci_passed.py` 只证明轻量检查，待 one-ops 定是否另核 |
| #408 | StartApprovalOnSubmissionIT 等已提交状态再断言 | opus 另指出 TimesheetApprovalScenarioIT 225/311/331/342 行同款竞态，待开小 PR |
| #392 | SingleAggregateGuardTest：lambda 目标致 AspectJ 匹配被 GC 后永久失效（静态缓存）→ 具名仓库类 + 同步器数断言 | 守护改动单列 |
| #410 | 代码合集脚本 `scripts/queue/merge_train.py`（build/split/finish） | 调度试跑过不带 --push；与 main 冲突（DIRTY）待我同步 |
| #374 | 规则索引、派工前检查 `--dispatch`、intent 片段 `--rules/--adrs/--sheet/--items` | 与 main 冲突（DIRTY）待同步；Review 列语义（Yes=需评审）请 one-rules 确认 |
| #375 | 提议稿状态行检查 `check_proposals.py` | BEHIND；旧写法 28、缺行 19 只统计，one-architecture 清理后改 `REQUIRE_STATUS_LINE=True` |
| #376 | 画像当前状态时间戳、交接文件模板与检查 | BEHIND；各画像迁移后改 `REQUIRE_TIMESTAMP=True` |
| #381 | 错题本检查 `defect_bank_check.py` | BEHIND；4 个表格册迁移后改 `STRICT_SOFT=True` |
| #342 #345 #352 | architecture 守护读取者、J1–J7 检查、共享 mock 次数断言扫描 | BEHIND；叠合 342→345→352 已验证无冲突（#352 的 precheck 块已挪位） |
| #368 | decide_sync.py | 与 one-rules 的 #362 重复，#362 合入后只提增量（build_all 自测登记）并关闭 #368；DIRTY |

build_all.py 里各治理工具的登记各占一行、位置分开（SELF_TESTED 之后 / 术语注释之后 / PyYAML 判断块之后），避免几个 PR 叠合时冲突。

## 待办
1. `.confirm{display:grid}` 盖掉 `hidden` 属性，确认框隐藏可能不生效（main 上原本如此，page-template.html，one-rules 的文件）。
2. TimesheetApprovalScenarioIT 225/311/331/342 行事件先于提交的竞态（等已提交再读，用现成 eventually / awaitApprovalCommitted）。
3. OrgSnapshotSyncIntegrationTest 两个偶发（共享 @MockitoBean audit 上断言未按本测试 syncRunId 限定）：改法已发 one-1a-par，催并跟踪。
4. 4 个表格式错题本迁移成条目格式并补「类别」，之后 `defect_bank_check.py` 的 `STRICT_SOFT=True`（sonnet 子代理做，各册会话复核；PR 额度限制下排在入队后）。
5. ADR R-11（intent 核对脚本）：先整理正反例样本集交 one-architecture 核对口径；ADR 工具已推到 one-architecture 的分支（catalog.yaml 与 ADR-0029 已并入）。
6. CS-1.11 基线/例外统一生命周期条款草案（过 opus 后走收件箱请用户批准）与 G-C8 守护实现 PR（排在其后）。
7. FD 财务口径单 9 条事实题是否改 `kind: fact`（one-rules 定，需重新发布页面）；GL/GL2 用语审定页下次改动加 `terms_review`。
8. 每晚 23:17 本机 nightly-legacy-it 巡检的会话内定时任务（job b9569dfe）7 天到期（约 10-22）前续设；接续后用 CronList 核对。
9. 合并队列评估结论：私有仓库在 team 套餐下不能用 GitHub 合并队列，保留 strict + `merge_train.py` 合集；仓库保护设置由用户定。
10. 推送门禁缺失的 worktree 清单已交调度（#332 之前的老分支，合并 origin/main 即得）。

## 不稳定测试台账
| 测试 | 判断 | 状态 |
|---|---|---|
| ManagementEndpointIT、ApprovalAccountsIT、NumberingRuleConfirmerIT（401） | 会话清理与固定时钟不同源 / 管理端口安全链 | 已修（f31e91b0、d5d45336 #228），再出现一次即报调度派测试代理 |
| TimesheetApprovalScenarioIT.cancelledTimesheetIsADraft | 事件先于提交 | 已修（#326），同类 4 处待清 |
| StartApprovalOnSubmissionIT.aFixableRefusalEndsAsReturnedBySystem | 同上 | #408 |
| SingleAggregateGuardTest 两条 | AspectJ lambda 目标 | #392 |
| OrgSnapshotSyncIntegrationTest.recoveredSourceClosesTheHold / releaseReasonLosesControlCharacters | 共享 mock 次数断言 | one-1a-par 修 |
| ProcessArchiveJobIT.archivesThenDeletes | 时间炸弹（真实 now 与固定 START 混用） | 已修 #413 |
| SourceReference*IT | 先红后实现的提交，不是偶发 | 无需处理 |

## 合入失败根因分析结论（2026-10-16，已报调度与用户）
取消 + 被取代的成功运行约占 PR 运行时长一半；main push 运行约占全部 CI 时长 25%。用户决定：保留 strict；main push 只跑轻量（#406）；重步骤只在入队时跑（#406）；偶发逐个修；推送前门禁挡自身缺陷；代码合集（#410）。

## 临时工作目录
`/private/tmp/claude-501/-Users-yeliang-Projects-FORCITIS-forcitis-one-psa--claude-worktrees-xin-61164f/138cb52b-4117-4136-9339-6ccc0ca6a2b4/scratchpad/` 下 `wt-*` 是各 PR 的独立 worktree（wt-ds=#368，wt-rules=#374，wt-prop=#375，wt-sess=#376，wt-bank=已合入分支，wt-ci=#406/#410 分支，wt-352=#352，wt-board=最近的测试修复分支，wt-adr=one-architecture 的 ADR 分支）；会话结束后可 `git worktree remove`。
