# CI 耗时复盘（2026-10-17）

范围：近 3 天 ci、nightly-legacy-it、release 的全部可取运行（ci 1000 条，恰达下载上限，实际覆盖 2026-10-15 15:49Z 至 10-17 08:34Z 约 41 小时）。只出报告，不改 `ci.yml`。取数与统计由 Codex（gpt-6.1-sol，xhigh）完成，Claude 抽查 3 个数字与关键表；原始数据与复跑脚本在本机 `scratchpad/ci-data/`（`out/CI_TIMING_DATA.md` 约 1750 行全表，`out/stats.py` 可无参复跑；未入库）。数据缺口见文末，结论里凡依赖缺口的都写了「待证」。

## 1. 结论（先看这里）

1. **一次完整 PR 运行（带 `queued` 标签）典型 934 秒，其中 90% 是后端 `surefire and failsafe, side by side` 一步（839 秒）**；排队 52 秒（5.6%），其余检查与收尾约 40 秒。前端、gitleaks、轻量检查都不在关键路径上。要缩短「16 分钟」，只有后端集成测试这一步值得动。
2. 这一步里，**failsafe（集成测试）808–858 秒，surefire（单元）82–98 秒并行**，单元测试不在关键路径。failsafe 的 IT 类按类名后缀统计 142 个（按 failsafe 阶段复算为 148 个，6 个用显示名报告，见 §6.1）、类耗时合计约 1300 秒，实际墙钟 ≈ 830 秒，等效并行度只有 1.56（`-Dtest.forks=2`，理想 2.0）；**最慢的 3 个类合计 505 秒，占 39%：`ReadOnlyAccountCheckIT` 单类 324 秒（192 个用例，每例约 1.7 秒）**、`SingleOperatorModeStartupIT` 100 秒（19 例，每例 5.4 秒）、`ReadOnlyVerificationCommandIT` 82 秒（79 例，每例 1.1 秒）。
3. **自托管 runner 在白天是满载的**：四台（mac-one-1..4）窗口内各占用约 1370–1520 分钟（约 60%），每个整点小时都是 4 并发满载，同时排队峰值 23 个，已领用作业的排队中位数 20 秒、P90 199 秒、最大 990 秒。
4. **约 25% 的 runner 分钟（1439 / 5869）花在最终被取消的运行上**；其中 255 个运行取消时同分支已出现新 SHA（疑似被推送取代，未能确认原因，见缺口）。
5. **main 的 push 运行里，gitleaks 作业 81% 的时间（中位数 155 秒）花在「release and deploy scripts self-test」**：该步骤写成 `if: github.event_name == 'push' || …relevant`，每次 push 无条件跑，160 次 push 共约 423 runner 分钟，占全部 runner 分钟的 7%，对 PR 关键路径没有影响，但占着满载的 runner。

**补充发现（2026-10-17，SQL Server 测试）**：默认 failsafe 里有四个验收类用 SQL Server 容器（`TestMssql`）：ReadOnlyAccountCheckIT 324 s、ReadOnlyVerificationCommandIT 82 s、StalledExternalSourceIT 32 s、A8SourceFlagsIT 28 s，合计 ≈466 s，占 failsafe 类耗时 1299 s 的 36%，每个完整 PR 都跑。用户 2026-10-17：投产后仍用 U8C 但经防腐层（ACL）访问，下一步开发只有财务权责事实到收付 U8C 时才设计，所以这些老系统只读核实与迁入适配器的 SQL Server 测试不必每个 PR 都跑——只在相关路径（`relevant_change.py` 的 `LEGACY_IT_PATHS`）变了和每夜全量时跑（方案 H）。这条比方案 A 的收益大，且与 A 重叠（A 压缩的正是这几个类）。

前三条建议（详见 §4，按性价比）：
- **A. 压缩三个慢类里的完整应用启动次数**（`ReadOnlyAccountCheckIT` 的 192 个用例里日志可见 145 次「Application run failed」（故意失败的启动）与 51 次成功启动，约每例一次应用启动，≈1.65 秒/次；不是固定等待），共用上下文或把校验逻辑下沉到更小的测试，不碰守护文件，预计类耗时 −250 到 −400 秒，关键路径 −130 到 −260 秒。
- **B. push 到 main 时让 release 脚本自测也按「改了才跑」**（改 `ci.yml`，守护档 opus 复审），预计回收 ~420 runner 分钟 / 2.5 天，缓解满载排队，风险低。
- **C. 调整 failsafe 的类调度（最长类先跑或拆开最慢的 3 个类）使 2 个 fork 更均衡**，预计关键路径 −100 到 −180 秒；动 `backend_verify.sh` / `pom.xml`，守护档 opus 复审，需先在试验分支量。

## 2. 数据（摘要，全表见 `out/CI_TIMING_DATA.md`）

口径：UTC；耗时 = completed − started；排队 = 已领用作业的 started − created；取消的占位不计机器占用；样本 < 5 标「样本少」。

### 2.1 工作流与 job

| 工作流/事件 | run 数 | 成功/失败/取消 | 耗时中位数 / P90 | 首 job 等待中位数 / P90 |
|---|---|---|---|---|
| ci / pull_request | 839 | 419 / 42 / 377 | 138 s / 836 s | 13 s / 136 s |
| ci / push（main） | 160 | 84 / 9 / 66 | 338 s / 918 s | 6 s / 83 s |
| nightly-legacy-it | 2（样本少） | 2 / 0 / 0 | 925 s / 1109 s | 113 s / 201 s |
| release | 10 | 6 / 4 / 0 | 164 s / 229 s | 4 s / 5 s |

| job（ci / pull_request） | n | 执行中位数 / P90 | 已领用排队中位数 / P90 |
|---|---|---|---|
| backend verify（完整与轻量混合） | 629 | 38 s / 839 s | 27 s / 191 s |
| backend verify (light) | 47 | 29 s / 42 s | 3 s / 26 s |
| frontend verify | 588 | 11 s / 44 s | 32 s / 237 s |
| secret scan (gitleaks) | 649 | 40 s / 238 s | 20 s / 207 s |
| assertion drift | 635 | 12 s / 16 s | 32 s / 212 s |

PR 上 backend verify 共 2647 runner 分钟，占全部 5869 的 45%；其中真正跑集成测试的运行约 41%（surefire/failsafe 步骤执行 255 次）。

### 2.2 一次完整 PR 运行的关键路径（run 39015755788，934 秒）

触发到最长 job 创建 17 s；该 job 排队 35 s；检出等准备 ~9 s；Flyway 检查 5 s、验收测试守护 11 s、镜像核验 2 s 等 ~20 s；**surefire + failsafe 839 s（89.8%）**；`new or changed test classes, repeated` 0 s（该次无新增类）；收尾 12 s。

### 2.3 failsafe 内部（4 份完整日志，样本少）

- Maven 阶段自报：编译/打包 6.5–6.8 s；surefire 82–98 s（含 OpenAPI 容器测试）；failsafe 808–858 s。
- 397 个类、类耗时合计 1470 s（IT 142 个合计 1299 s）；`-Dtest.forks=2`。
- 按类前 8（中位数）：ReadOnlyAccountCheckIT 324 s、SingleOperatorModeStartupIT 100 s、ReadOnlyVerificationCommandIT 82 s、StalledExternalSourceIT 32 s、A8SourceFlagsIT 28 s、SingleOperatorModeFailClosedIT 25 s、SingleOperatorEndToEndIT 20 s、SingleOperatorModeQueryIT 17 s；第 9–30 名每个 9–15 s。
- Testcontainers：每次完整运行起 133–134 个容器（postgres:18 中位数 0.73 s，mssql 5.5 s 共 8 个次），启动区间并集 89–122 s（占 job 9–14%）；未见启动重试证据。
- `ReadOnlyAccountCheckIT` 在 324 秒里有 11,903 条应用日志（时间戳无超过 15 秒的空档），其中 145 次「Application run failed」与 51 次成功的 `Started … in …`：用例几乎每例启动一次 Spring 应用（含大量故意失败的启动）。更正：本报告先前版本曾把它推断为「等 60 秒超时」，核对日志后不成立（那是两条抽样日志的时间戳，不是间隔）。

### 2.4 runner 与排队

| runner | job 数 | 累计作业分钟 | 窗口占用（约 2445 分钟） |
|---|---|---|---|
| mac-one-1 | 767 | 1478 | 60% |
| mac-one-2 | 753 | 1517 | 62% |
| mac-one-3 | 768 | 1483 | 61% |
| mac-one-4 | 782 | 1367 | 56% |

北京/欧洲白天与傍晚几乎每个整点都是 4 并发满载，排队峰值 12–23；夜间 4–6。已领用作业排队 n=3086：中位数 20 s、P90 199 s、最大 990 s；另有 757 个作业未领用即取消（等待中位数 80 s）。

### 2.5 浪费

- 最终取消的运行：443 个，占运行墙钟 25.0%、runner 分钟 24.5%（1439 分钟）。
- 取消时同分支已有新 SHA 的候选：255 个，runner 分钟 16.1%（947 分钟）；**确认「被推送取代」的比例缺测**。
- 重跑（attempt > 1）14 个运行，1.4%，新增 118 runner 分钟（2.0%）。
- push 到 main：gitleaks 作业 31282 秒（521 分钟），其中 release 自测 81%（≈ 423 分钟）。

## 3. 分析

**16 分钟里各占多少。** 完整运行 934 秒 = 排队与准备 ~60 s（6%）+ 守护类小检查 ~40 s（4%）+ 后端测试 839 s（90%）。后端测试里：单元测试 ~90 s 与集成测试并行，被集成测试完全盖住；集成测试 ~830 s，其中容器启动的并集 ~100 s 但分散在两个 fork 里，真正占墙钟的部分约一半。

**可并行或拆分。**
1. failsafe 只有 2 个 fork，并行度 1.56：说明类的分配不均。最慢的 3 个类（505 s）若被分到同一个 fork 上就会拖出尾巴；`ReadOnlyAccountCheckIT` 是 192 个用例的单个类，fork 级并行无法拆它。
2. 机器 16 核、64 GB，白天 4 个 runner 同时各跑 1 个 job，每个 job 2 fork + 若干容器，再加 fork 要看 Docker 内存与 CPU 争用，**不能凭空加到 3 或 4，要先在试验分支量**。
3. 两道小守护（Flyway 检查 7 s、验收测试守护 10 s）在重 job 里串行，可挪到并行的轻 job，但只有 ~17 s，不值得先做。

**缓存。** 日志里没有可见的依赖缓存还原步骤；编译/打包只有 6.5 s（依赖已在 runner 机器上），**缓存不是瓶颈**。

**容器复用。** postgres 容器启动 0.73 s，133 次合计 ~100 s（并集 ~90–120 s）。复用同一容器能省的上限约 60–100 s 的 CPU 时间，墙钟约 30–50 s，且牵涉测试隔离守护（TestContainerGuard、TestIsolationGuard），**收益有限、风险偏高，排后面**。

**重复跑新测试三遍的代价。** `new or changed test classes, repeated` 只在 22 个运行里执行，中位数 0 s，P90 220 s，占 job 时间 0.9%；**代价小，保持**。

**轻量档是否该跳没跳。** PR 上 backend 作业 47 个轻量档中位数 29 s，正常；main push 轻量档的「container-free unit tests on a push to main」未见异常。**该跳没跳的是 push 上的 release 自测**（见结论 5）；PR 上同一步骤已按「读到的文件变了才跑」门控，中位数 40 s。

**排队。** 只有 runner 满载时排队才大：P90 199 s。许多被排队的是 12 秒就能完成的小作业（assertion drift 执行中位数 12 s、排队中位数 32 s、P90 212 s；frontend verify 执行中位数 11 s、排队 P90 237 s）。PR 的总时长 = 最慢作业完成时间，所以任何一个小作业排队 200 秒都会拖整个 PR。

**取消浪费。** 25% 的 runner 分钟最终被取消，说明 PR 反复推送、合并队列更新分支重跑（队列日志里 `behind-after-fail` 79 次/天）会重复付费。`#406` 后重步骤只在 `queued` 标签下跑，这个数字是否已因此下降要看之后几天的数据（当前样本跨 #406 前后）。

## 4. 方案（按性价比排序）

| # | 做什么 | 预计节省 | 改动面 | 风险 |
|---|---|---|---|---|
| A | 压缩 `ReadOnlyAccountCheckIT`（324 s，192 例）、`SingleOperatorModeStartupIT`（100 s，19 例）、`ReadOnlyVerificationCommandIT`（82 s，79 例）的应用启动次数：同一配置的用例共用启动好的上下文，或把只验校验逻辑的用例下沉到不启动应用的测试；断言不变 | 类耗时合计可降 250–400 s；关键路径 −130 到 −260 s（取决于排布） | 只改这三个测试类（验收测试目录，按 §6.1 由测试代理改，one-1a-par 负责）；不碰守护 | 中：共用上下文要守隔离（CS-5.5/5.6）；下沉测试不得放宽断言（CLAUDE.md 第 5 条） |
| B | `ci.yml` 里 release 自测与 shellcheck 步骤的条件改成「push 时按 `github.event.before..after` 判断 release 脚本及其读取的文档是否变了」，不变则跳过；定时全量与 workflow_dispatch 照跑 | 160 次 push × ≈155 s；回收 ≈ 420 runner 分钟 / 2.5 天（占 7%），缓解满载排队；PR 关键路径不变 | `ci.yml`（守护档，opus 复审）；push 上 `relevant_change.py` 需接受前一提交作基线 | 低：release 脚本变了仍会跑；要保证 `before` 为全零（新分支）时回落为全跑 |
| C | failsafe 调度：试验 `runOrder`/拆分最慢的 3 个类/按类历史耗时分桶，使 2 个 fork 均衡；必要时评估 forks=3（只在试验分支量白天满载下的争用） | 关键路径 −100 到 −180 s（失去并行度 0.44 的部分） | `backend_verify.sh`、`pom.xml`（守护档，opus 复审）；试验分支先量 | 中：forks 增加会加剧 Docker 内存/CPU 争用，满载时可能反而变慢；先量再改 |
| D | 把无需 Docker 的小作业（assertion drift、frontend light、gitleaks）改到 GitHub 托管 runner，或增设第 5 个自托管 runner（one-ops 评估机器余量） | 去掉小作业 P90 ~200 s 的排队，PR 总时长 P90 可降 ~100–200 s；托管 runner 窗口内才用 25 分钟，成本可忽略 | `ci.yml` 的 `runs-on`（守护档，opus 复审）；第 5 个 runner 属 one-ops | 中：托管 runner 的可用性与私有仓库分钟配额；第 5 个 runner 要看内存（4 个已满载） |
| E | 补测「取消原因」数据（PR synchronize、标签、队列更新分支）：为 CI 运行加原因标注，量出 25% 取消里多少是可避免的 | 目前无法估计；若其中一半可避免，≈ 12% runner 分钟 | 先只加日志/标注，不改行为 | 低 |
| H | 把四个用 SQL Server 的验收类从默认 failsafe 挪到 legacy-it 档：pom 的 failsafe excludes（默认档）与 legacy-it 档 includes 这四类，「每个验收类都出报告」检查按档计，路径命中 `LEGACY_IT_PATHS` 时才在 PR 上跑，每夜全量照跑；不改验收测试文件 | 类耗时 −466 s，完整 PR 关键路径 −300 秒以上（934 s → ~600 s）；每个 PR 轮次都省 | `backend/pom.xml`、`ci.yml`、`scripts/ci/backend_verify.sh`/`relevant_change.py`（守护档，opus 复审） | 中：PR 上不再发现这四类的回归（只在相关路径变化与夜间发现）；须保证 `LEGACY_IT_PATHS` 覆盖它们读的东西（relevant_change 自测已核对 *MssqlIT 的输入，要对新增四类同样核对） |
| F | 容器复用（同 JVM 单例容器） | 墙钟 −30 到 −50 s | 测试基础设施公共件（TestPostgres）、隔离守护 | 中到高：隔离被破坏会引入偶发（QUAL 系列的教训）；排在 A–C 之后 |
| G | 把 Flyway 检查、验收测试守护两步（~17 s）挪到并行的轻 job | −17 s | `ci.yml` | 低，收益小，不单独做 |
| — | 重复跑新测试 3 遍 | 不改 | — | 成本占 0.9%，保持 |

**U8C 防腐层设计的测试建议（调度 2026-10-17）**：将来为 U8C 做防腐层时，测试以契约测试和替身（fake / stub）为主，不再每个 PR 起真实的 SQL Server；真实库只放在每夜全量与按路径触发的少量冒烟测试里。这样新增的 U8C 适配器不会重演「几个类占默认测试 36%」。

建议顺序：先 H（最大收益，见上）、再 A 与 B（互相独立、风险低、立刻有数），同时 E 的数据先加；C 在拿到 A 之后的新类耗时再量（A 改变了最长类，C 的收益要重算）；D 视 A–C 后队列是否仍长，再让 one-ops 评估；F 最后。

## 5. 数据缺口与不确定

- ci 恰为 1000 条（下载上限），无法证明近 3 天完整；`ci` 在窗口里跨越了 #406（CI 门控）等改动，样本混合了新旧行为，数字是「近期混合」，不是改造后的稳态。
- 日志样本只有 12 个 backend job（4 份完整重测、5 份轻量 push、3 份重步骤跳过），完整档每个类只有 4 个观测，**按类表全部是「样本少」**；没有 nightly、release、前端 job 的日志，前端内部拆分未知；没有独立的依赖缓存还原耗时。
- 取消原因、被推送取代的确认、PR synchronize 事件均缺失，只能给「候选」；`headBranch` 是 PR 的代理。
- 缺 1 个 job 文件，9 个运行 job 列表为空；下载非原子，3 个未结束运行只给下界。
- 88 条测试汇总行是 JUnit 显示名而非类名（例如 OperationsProfile…），原样保留。
- ReadOnlyAccountCheckIT 慢的原因（约每例一次应用启动）来自日志计数，具体能否共用上下文须由该类的维护会话看代码确认。
- 当前 `ci.yml` 与窗口早期的工作流不一致（heavy gate、light 后缀、repeat step 是中途加入的），统计保留了实际 job/step 名，没有按当前文件回推历史行为。

## 6. 1 分钟目标可行性

> QUAL-CARD-1MIN；Codex（gpt-6.1-sol，xhigh）起草，Claude 审稿并抽查（模块清单与 module-dependencies.txt 一致，类计数与日志核对一致）。只读评估，未实施任何建议。编号说明：本 worktree 的 `CI_TIMING_REVIEW.md` 实际止于「## 5. 数据缺口与不确定」，故续写第 6 节。代码/统计基线为 `37519e753e24c8e3d32d1bbcba28d0015adf7451`；交付期间 HEAD 外部前进到 `46fa7ccff78cff1b40017b15b24fdaa956c9624e`，仅修改该复盘文档，已补读并在6.2追加方案H场景，源代码未变。离线读取，没有核验 GitHub 当前设置或 runner 实时负载。strict 仍开启、main 只经队列，作为派工卡给定约束。

**结论：现有完整后端集成测试在当前 4 个同机 runner 上不能降到 60 秒。快速反馈可以争取 60 秒，但须把完整集成、启动矩阵、部分重检查留在合入前的队列中，并控制排队；把这些检查全部后移到每夜，会降低 main 的合入前保护，现有证据不支持这样做。**

### 6.1 基线、口径与不能混用的数字

以下标注分别表示：**实测**＝现有日志/API 快照；**静态**＝本 worktree 源码；**计算**＝由实测值推导；**假设**＝必须通过后续实验验证。除明确列出的样本分位数外，方案时间区间是条件估算，不是已实现的 P50/P90。

| 指标 | 证据与含义 |
|---|---|
| 完整 PR 从触发到全部 job 结束 | 4 个成功完整样本为 926、931、934、1139 秒；P50 **932.5 秒**、P90 **1077.5 秒**，样本少。原报告选 934 秒作典型样本。[S1][D1] |
| 后端关键步骤 | 编译/打包 6.456–6.832 秒；surefire 82–98 秒与 failsafe 808–858 秒并行。典型 job 882 秒，其中后端测试步骤 839 秒；另有触发至 job 创建 17 秒、job 排队 35 秒。[D1] |
| 测试工作量 | 4 次完整运行的**类耗时累计**为 1415.576–1527.462 秒，典型运行 1469.898 秒。这是并行测试类自报 elapsed 的总和，**不是 CPU 秒**，含数据库、Spring、子进程和等待。后文用约 1470「类秒」建模。[D1] |
| IT 数量修正 | 原报告的 142 个 IT / 约 1299 秒，是全限定类名直接以 `IT` 结尾的统计。按 `phase` 复算实际为 **148 个 failsafe 类 + 249 个 surefire 类＝397 类**；6 个 failsafe 类用显示名报告。148 类累计 1275.479–1356.613 秒，典型运行 1318.087 秒。直接按旧 142 类分片会漏 6 类。[D1][S4] |
| 最长单类 | `ReadOnlyAccountCheckIT` 314.4–331.9 秒，中位数 323.95 秒；其次 `SingleOperatorModeStartupIT` 约 99.56 秒、`ReadOnlyVerificationCommandIT` 约 81.81 秒。类级分片仍有 ≥314 秒的串行下限。[D1] |
| 轻量反馈 | backend `(light)` job 执行 P50/P90 为 29/42 秒。重新关联带该后缀、最终成功且有 job 时间戳的 **21 次工作流**，触发至全部 job 结束的 P50/P90 为 **171/316 秒**，范围 47–405 秒。30–40 秒不能用来代表整条快速工作流。[D1][D2] |
| 快速档的另一条长路径 | PR 的 release/deploy 自测实际执行时 n=267，P50/P90 **160/228.8 秒**；secret scan 整个 job 混合执行/跳过后的中位数才约 40 秒。当前条件是 `push || relevant`，普通代码 PR 即使未 queued 也可能执行。仅加后端分片不能让所有快速运行到 60 秒。[D1][S2] |
| 排队 | 已领用作业的排队 P50/P90 为 20/199 秒，最大 990 秒；四个 runner 共用一台 16 核/64GB Mac 和 Docker。它们是 4 个作业槽，不能等同于 4 台独立机器。[S1][D1][S8] |

数据实际覆盖 ci 约 41 小时，恰好命中 1000 条下载上限，混合门控改造前后版本；不是完整三天或稳态样本。缺 1 个 job 文件、9 个运行 job 列表为空，快照非原子。所有“概率”和容量估计均受此限制。[D1]

### 6.2 路线一：按改动影响选择模块与测试

#### 模块与耗时分布

按 `SubdomainModuleDetection` 的识别规则，当前有 **12 个 BC 模块 + 1 个 OPEN 共享内核＝13 个 Modulith 模块**。`kernel.event` 是内核中的包，不另算模块；顶层 `acceptance`、`scenario` 是测试目录，不是生产模块。[S4][S5]

下表将 `acceptance/<子域>/<BC>` 归回对应模块。默认可执行测试类共 397 个，另有 `SeeyonViewsMssqlIT` 1 个仅在 `legacy-it` profile 编译/执行。辅助 Java、抽象基类不计；后缀为 `Test` 的类仍可能启动 Spring 和容器。88 条显示名日志经源码的类级 `@DisplayName` 精确映射，全部找到类，不再留作未知模块。[D1][S4]

| Modulith 模块 / 测试范围 | surefire 类 | failsafe 类 | 其中验收类 | 每次类秒合计中位数〔最小–最大〕 | 单类中位数 / 最长类中位数（秒） |
|---|---:|---:|---:|---:|---:|
| `cost.timesheet` | 5 | 10 | 8 | 52.2〔50.2–53.6〕 | 2.47 / 9.98 |
| `kernel` | 13 | 1 | 0 | 8.7〔7.5–9.7〕 | <0.01 / 8.44 |
| `organization.attendance` | 2 | 3 | 2 | 15.1〔13.9–16.6〕 | 3.56 / 7.27 |
| `organization.workforce` | 27 | 10 | 9 | 65.2〔62.5–69.0〕 | 0.52 / 7.57 |
| `platform.approval` | 20 | 36 | 12 | 116.1〔100.5–121.6〕 | 0.51 / 15.02 |
| `platform.audit` | 11 | 13 | 7 | 89.5〔86.1–95.0〕 | 3.21 / 13.94 |
| `platform.eventing` | 3 | 2 | 2 | 20.3〔17.6–21.6〕 | 1.08 / 9.88 |
| `platform.idempotency` | 0 | 2 | 0 | 3.6〔3.2–3.9〕 | 1.79 / 3.53 |
| `platform.identity` | 49 | 22 | 25 | 276.4〔268.5–289.6〕 | 0.44 / 99.56 |
| `platform.integration` | 48 | 25（另 1 个 MssqlIT） | 21 | 636.6〔603.8–666.2〕 | 0.21 / 323.95 |
| `platform.numbering` | 6 | 8 | 5 | 38.5〔34.6–49.7〕 | 0.97 / 8.82 |
| `platform.scheduling` | 2 | 2 | 0 | 6.4〔5.9–7.0〕 | 1.59 / 3.22 |
| `platform.web` | 5 | 8 | 5 | 43.9〔33.2–64.7〕 | 3.18 / 7.92 |
| 全局守护、应用、场景及其验收测试，非额外模块 | 58 | 6 | 9 | 98.1〔93.9–103.1〕 | 0.32 / 14.84 |
| **默认合计** | **249** | **148** | **105** | **每次总和 P50 1475.6；范围 1415.6–1527.5** | 各模块中位数不能相加冒充一次实测 |

所有时间列都只来自 4 份完整日志。全局 64 类含应用与容器测试，不能把它们全部称作“免容器守护”。

#### 反向依赖闭包能缩到多小

方向是“被改模块 → 依赖它的消费者”，递归取并集，不能只跑它所依赖的提供方。以下先仅使用已批准的 `backend/architecture/module-dependencies.txt`，再保守附加全局 64 类；**这只是模块图下界模型，尚不能作为安全的选择器**。[S5]

| 修改模块 | 图上自身与传递依赖方模块数 | 模块测试类数 + 全局 64 类 | 模块类秒中位数，不含全局约 98 类秒 |
|---|---:|---:|---:|
| `cost.timesheet` | 1 | 15 + 64＝79 | 52.2 |
| `platform.idempotency` / `platform.scheduling` / `platform.eventing` | 各 1 | 2 / 4 / 5 + 64 | 3.6 / 6.4 / 20.3 |
| `platform.numbering` | 2 | 29 + 64＝93 | 90.0 |
| `platform.approval` | 2 | 71 + 64＝135 | 168.0 |
| `organization.attendance` | 3 | 76 + 64＝140 | 184.0 |
| `platform.identity` | 5 | 161 + 64＝225 | 504.4 |
| `organization.workforce` | 6 | 198 + 64＝262 | 567.8 |
| `platform.audit` | 8 | 227 + 64＝291 | 672.1 |
| `platform.web` | 6 | 174 + 64＝238 | 551.6 |
| `platform.integration` | 9 | 300 + 64＝364 | 1308.8 |
| `kernel` | 13 | 333 + 64＝397 | 1377.0 |

不能遗漏的输入：

- **kernel 与隐式协作**：图注释明确登记 `SeparationReview`、`AccountEmployees`、`ExternalSubjectEmployees` 等内核端口，因此普通模块边不足以表达全部提供方/消费方关系。应用扫描、事件订阅、全局 web/鉴权基础配置也需要补边；例如上表“idempotency 仅自己”不证明所有 HTTP 行为只受这 2 类覆盖。
- **测试基础设施**：`TestPostgres`、`TestImages`、公共 fixture、抽象测试基类改变，初版选择器应回退默认全部 **397 类**；相关 SQL Server 基础设施或读取输入改变再加 profile 测试。精细化到“仅全部使用方”须覆盖继承、辅助类和传递调用。
- **迁移/Flyway**：`application.properties` 的迁移目录和 schema 列表、`init-schemas.sql`、数据库测试模板、共享资源或构建插件改变，回退 397 类；仅按 SQL 文件目录选择本 BC 不够，许多完整应用上下文会迁移全库。Maven `legacy-it` 的附加范围仍按独立判定保留。
- **新增、删除、改名、未知路径、图不一致或映射无报告**：全跑或失败，不以空测试集合放行。未知影响不得静默判作无关。

#### 已有三档做到了什么，还差什么

`relevant_change.py` 已有 `docs / guarded / code`，但它是**文档读取关系门控，不是 Java 模块测试影响分析**：[S3]

- `docs`：没有构建读取的文档变化，仍执行始终运行的轻检查；不是“所有文档都跳过”。
- `guarded`：变化限于被构建读取的文档；扫描字面路径、分段路径、文件名和测试 helper 使用方，仅运行可覆盖全部读取方的免容器测试、前端词表脚本或发布脚本自测。有未覆盖读取方、Spring/容器测试、主代码读取或宽泛读取，就升到 `code`。重读取豁免表当前为空。`terms.yaml`、`bcs.yaml`、`asyncapi.yaml` 目前都会升到全量 `code`。
- `code`：任意 Java、前端源码、pom、CI 等变化一般均进入；后端不会再细分 BC。另有 `legacy_it` 路径门控，不等于默认集成测试的模块选择。
- `heavy_gate.py` 再按 `queued` 决定是否执行重步骤；因此“未跑重步骤”也可能是未入队或被取消，不能倒推出它属于 `docs`。

后端是一个 Maven 项目，pom 没有多子工程列表；`-pl/-am` 不能直接选择 Modulith 的 Java 包。当前也没有已配置的动态测试影响插件或覆盖率映射。可先做保守的源码/模块读取关系选择器，之后再评价动态覆盖率方案；本卡未联网验证任何新插件的版本与能力。

**需改机制/文件（后续工作卡范围）**：`scripts/ci/relevant_change.py` 或独立影响规划器、`backend/architecture/module-dependencies.txt` 的隐式关系表达、`backend_verify.sh` 的 surefire/failsafe 双清单、`ci.yml` 的证据产物；`acceptance_reports.py` 须核对“本次应跑清单”与报告、全量模式仍核对全部验收类。不能靠放宽“有报告”断言让未运行类通过。全局守护必须仍基于完整编译结果运行；保留独立 OpenAPI 导出及后续扫描顺序。

**条件估算**：以当前 2 个 fork、有效并行度假设 1.56–2、固定开销假设 30–50 秒，保留上述全局 64 类，按 `max(所选类秒/并行度, 最长类) + 固定开销`：叶模块约 **80–170 秒**，approval/attendance 约 **160–250 秒**，identity 约 **330–480 秒**，integration/shared 输入约 **700–950 秒**。均不含排队、三遍重跑或另跑 legacy-it；新 JVM/上下文排布可能使范围失准。通过进一步拆分全局测试，少量叶模块有机会 60 秒，但本批数据不能证明。

**典型 PR 的分档比例：缺测。** `runs.json` 没有 changed files、标签、PR 编号或 `headSha`，只用分支/标题不能判断影响。255 次后端重步骤执行也不能解释为代码 PR 占 41%。补测应按 **PR 号 + head SHA** 去重，保存 base SHA、增删改路径、tier、反向闭包、全跑回退原因、预计/实际类秒，分别统计“全部 PR”与“代码 PR”；先离线回放至少一个完整工作周，再与全量并跑校准。当前只能确认两类大模块 identity/integration 占绝大部分工作量，不能假定多数 PR 都是便宜的叶模块。

**main 门禁影响**：选择器会引入漏选风险，strict 只能保证基线新，不能弥补漏测。建议先只用于开发反馈，队列继续全量；未经全量对照验证与调度批准，不将它替代队列门禁。

#### 交付时复盘新增的方案 H：四类 SQL Server 验收按路径运行

交付增量 `46fa7ccf` 新增 H：将 `ReadOnlyAccountCheckIT`、`ReadOnlyVerificationCommandIT`、`StalledExternalSourceIT`、`A8SourceFlagsIT` 四类移出默认 failsafe，仅相关路径与每夜运行；报告还转述了用户对未来 U8C 防腐层测试范围的决定。**已核实的是新增报告原文与对应源码，未取得对应用户 intent 原文；本卡按条件场景评价，不将其转述视为本卡实施授权。** pom/CI 目前没有这项代码改造。

按四份日志精确扣除，H 对应 **453.8–484.3 类秒**（典型465.8），默认剩余 **144 个 IT +249 个 surefire＝393类**，IT剩余 **821.7–872.4 类秒**，全部类剩余 **961.8–1043.2 类秒**。取剩余IT工作量中位数约859类秒，若有效并行度1.56–1.9、固定50秒、触发/等待20–50秒，可规划不触及这些路径的PR约 **520–660秒 P50情景、700–950秒 P90预算**；这是尚未实施的选择范围变化，不是保留原397类后的无损提速。相关PR和每夜仍须验证原检查集。

H 与原方案A高度重叠：A的两个慢类（约324与82秒）正是H移走的类，不能再将A的250–400类秒收益全额叠加；H后还剩约100秒的 `SingleOperatorModeStartupIT`，所以**即使H落地，按类分片仍不能到60秒**。未触及相关路径的PR占比仍缺测，不能说每个PR都省466类秒。

后续实现还须扩展 `relevant_change.py` 对这四类及其继承/helper/资源/配置/共享内核/迁移/启动校验输入的覆盖核对；当前 `mssql_it_inputs()` 只扫描 `*MssqlIT`，不能自动证明新四类输入齐全。`legacy_it.sh` 当前只选择 `*MssqlIT`，新增四类不会自动在那里运行；pom默认排除与profile包含、脚本清单、`acceptance_reports.py` 分档清单、nightly实际执行报告和队列暂停信号都须一并设计。若四类只放进 `nightly-legacy-it`，其红灯当前不被 `mq.py` Pause读取，须接入6.6所述信号。

### 6.3 路线二：将后端测试分片到 N 个 runner

记 `W≈1470 类秒`，`L≈324 秒`，`P` 为同时执行测试类的槽位，`H` 为未包含在类秒中的准备/收尾开销。理想下界为：

`T ≥ max(W/P, L) + H`。

这个模型假定各类用时不因同机竞争变化。Spring 和容器启动已经计入类 elapsed 的部分，不能再作为固定开销重复加；新增分片复制的启动、数据库模板初始化和产物传输则需另量。

**固定开销的证据**：典型 backend job 在 839 秒测试步骤外还有 **43 秒**；编译/打包另在该步骤中占 **6.6 秒**，检出约 5 秒。当前机制若原样复制，每片约 **50 秒**非测试开销，尚未包含工作流触发与排队。把守护集中、只给 worker 检出和测试，可暂用 **10–30 秒**作乐观设计假设；编译产物传输、JVM 冷启动、全新模板的成本未独立测量。不能把“编译 7 秒”当作每片完整固定开销。

先**假设最长类已被拆小**，只算 60 秒墙钟需要的槽位：

| 每片固定开销 H（秒） | 所需测试类槽位 `ceil(1470/(60-H))` | 单 fork 分片 job / runner 槽 | 每片双 fork 时至少多少分片 job / runner 槽 |
|---|---:|---:|---:|
| 0，物理开销全忽略 | 25 | 25 | 13 |
| 10，极乐观 worker | 30 | 30 | 15 |
| 20，乐观 worker | 37 | 37 | 19 |
| 30，集中守护后的待测假设 | 49 | 49 | 25 |
| 50，接近现有 job 原样复制 | 147 | 147 | 74 |

这是连续并行供给所需的 **15–25 个双 fork 作业槽**的乐观量级，不是“同机注册 25 个 runner 就够”。表内双 fork 还假定能对同一清单统一均衡；现有 surefire/failsafe 分两组、OpenAPI 独跑，实际可能更多。若端到端还需额外 20 秒集中准备/汇总，`H=20` 时只剩 20 秒测试预算，便要 74 个类槽、37 个双 fork 作业槽。**原始 L 没变时，无论分几片，以上任何行都不能实现 60 秒。**

用典型样本 397 类按耗时从大到小贪心分配，静态模拟结果如下；不执行测试，不模拟 Docker/CPU 竞争：

| 同时测试类槽位 | 理想平均 `W/P`（秒） | 最长桶（秒） | 实际含准备的判断 |
|---|---:|---:|---|
| 2 | 734.9 | 734.9 | 保留两个槽只靠调度，仍约 12 分钟以上 |
| 4 | 367.5 | 367.5 | 约 400–450 秒，属乐观计算 |
| 8 | 183.7 | 324.1 | 约 350–420 秒；最长类主导 |
| 16 | 91.9 | 324.1 | 没拆最长类，继续加槽几乎无收益 |
| 32 | 45.9 | 324.1 | 工作量均摊已够，单类仍不够 |

**现有 4 runner 能做什么**：即使独占四个 runner、每个 failsafe 2 fork，也只有 8 个 IT 类槽；surefire 可以另用 JVM，但其额外槽不会拆开 324 秒的 IT。现有机器上四个 backend 同开时，最多还会叠加约 16 个测试 JVM及 Maven/应用子进程，不能视为 16 个独占 CPU。最小更改分片在资源不退化、四个槽同时可得时，完整作业可试验 **350–500 秒**，属于容量假设；白天仍沿现有队列争用时没有可靠上界，必须再叠加实测排队，不能声称 P90 一分钟。

**需要改什么**：`ci.yml` 的分片 matrix 与最大并发、`backend_verify.sh` 的编译/测试解耦、pom 的测试清单参数/调度、全量类清单与历史耗时产物、结果汇总。按全限定类名分桶，同时处理上述显示名；OpenAPI 导出只做一次，并把它产生的契约供扫描使用。分片须共享同一提交的构建产物，防止拿到别次运行的 jar。新增/改动测试三遍重跑、legacy-it 另计容量。

**门禁**：保留原名的 `backend verify (...)` 作为最终汇总检查；仅全部预期分片完成、无遗漏/重复、验收报告完整且无失败才报告成功。缺分片、取消、超时、空清单都不能绿。`mq.py` 当前只消费三个必需检查，不会自动理解新增 matrix 名；不能删除原检查后仅留一堆不受队列约束的分片结果。[S2][S6]

**GitHub 托管 runner**：可获得不同机器上的并发、隔离共享 Docker 争用，但须处理平台/镜像架构、Java/pnpm 工具链、冷拉镜像、依赖缓存与产物传输，当前 darwin 安装脚本也不能原样用于 Linux。成本随实际计费时间、最小计费单位、并发数及重跑增加；分片复制开销会增加总 runner 分钟，最快墙钟不等于最低成本。本卡不联网，不引用当前价格或配额。现有 `CI_RUNNERS.md` 明确 CI 留在本机，转托管须另经调度/用户决定，本评估未改变该决定。[S8]

### 6.4 路线三：测试容器复用或预热

#### 已有能力与真实调用方式

`TestPostgres` 已同时提供两条路径：[S7]

1. `container()` 每次构建新容器，统一排序规则、连接超时、3 次启动尝试与失败取证。
2. `shared()` 是**每个测试 JVM 一个**、延迟启动、JVM 结束后由 Ryuk 回收的实例；`database(testClass, purpose)` 从每 JVM 迁移一次的模板复制独立数据库，按类名与 purpose 分配，重复领取拒绝，支持 `MIGRATED` 与 `MIGRATED_TESTDATA`。共享实例 `max_connections=500`，并关闭测试库 fsync。

源码去除注释与字符串后，52 个 Java 文件含 `TestPostgres.container()` 调用，6 个含 `database()` 调用；未见 `.withReuse(...)`。这些是调用文件数，**不是容器次数或测试类数**：一个公共基类可被很多类继承，辅助类也可被多个测试调用。不能把“52 处”与日志的 133 次一一对应。`TestMssql` 也已经是每 fork 共享一个实例、按类与用途分配数据库/登录；不应再把该能力当成新方案。

#### 可省多少

4 次完整运行各起 133–134 个容器；其中 PostgreSQL 126–127 次，普通启动中位数约 0.73 秒。PostgreSQL 自报启动时长累计分别 **105.0、91.9、92.7、124.0 秒**，末次含一个约 30.64 秒长启动。全部镜像启动区间并集 **89.2–121.9 秒**，占 backend job 9–14%。这些仍是 elapsed/重叠区间，不能标成 CPU 时间，不能将整个并集从关键路径直接减掉。[D1]

| 方案 | 条件下可节省的墙钟 / 完成时间 | 要改什么与主要风险 | 对 main 门禁的影响 |
|---|---|---|---|
| 扩大现有 JVM 单例 + 每类独立数据库 | **纯容器启动**收益暂估 **30–60 秒/完整 job**，沿用两 fork 分散启动的保守估算；无竞争时收益也不能超过该批全部启动区间并集 89–122 秒。仅此一项，典型约 934 秒可到 **874–904 秒**，排队另变。模板迁移复用的额外收益缺测 | 迁移适合的测试使用方至 `TestPostgres.database()`，保留不同初始化/角色/故障模式专用容器；补模板失败、类间隔离、连接池与并发验证。不需要先放宽容器守护 | 全量与报告检查仍在，逻辑上保留覆盖；共享实例失效可令整个 fork 同时失败，必须观察偶发率 |
| Testcontainers 跨 JVM/跨运行 reuse | 只能避免首次启动和某些初始化；不能在上述收益外再次相加。**没有依据承诺额外节省**，完整运行仍约 **15 分钟量级** | 生命周期、配置指纹、清理和并发租约需重做；旧库/权限/镜像状态可能跨运行污染。本源码未启用，离线未验证当前库版本所需配置 | 持久状态可产生顺序依赖与假绿；现有临时容器回收方式不再成立，不适合先作为门禁基础 |
| 每 runner 常驻 PostgreSQL，以独立数据库隔离 | 已知直接启动部分的收益同上；模板、迁移、清理净收益需实验，**不能给比 874–904 秒更乐观的有证据完成时间** | one-ops 管生命周期、升级、健康、独立运行 ID、数据库/角色命名、清理；每 runner 一个库服务并不隔离同 runner 前后作业。schema 隔离更复杂，现有 SQL 多含固定 schema，优先独立数据库 | 引入新环境依赖、清理失败与旧迁移污染；需要空库控制组和失败注入，且属于常驻资源，现有临时 Testcontainers 例外不覆盖 |

**Spring 上下文比单次容器启动更值得量。** 对四份完整日志，仅提取 `Started … in … seconds` 的数值，不输出日志正文：每份 164–165 条成功启动，所报时长累计 **340.2–381.0 秒**，约为同次类 elapsed 合计的 **23.6%–25.7%**；另各有 146 条 `Application run failed`，失败启动没有同样的耗时汇总。成功启动累计有并行重叠，既不是全体 Spring 开销比例，也不是可直接节省的墙钟。[D2]

最慢类 192 例附近可见大量成功/故意失败启动；源码确有 `new SpringApplicationBuilder(OneApplication.class).run(...)`。`SingleOperatorModeStartupIT` 经辅助类起独立子进程、检查端口/退出码/启动顺序，不能简单用缓存替代。原复盘“减少三类重复应用启动可省 250–400 类秒”仍是**待测试维护方验证的设计假设**。只有在不丢掉真实启动、失败关闭与数据库校验断言时，才能兑现；不能把安全启动验收全替成 mock。[S1][S4]

#### 守护与偶发风险

- `TestContainerGuardTest` 要求 PostgreSQL 构建只在 `TestPostgres`，禁止绕过排序/连接配置；它**没有禁止现有 `shared()/database()`**。SQL Server 守护也认可 `TestMssql`。集中复用工厂可符合守护，直接在业务测试里 new 或另起常驻连接会改变原防线。
- `TestIsolationGuardTest` 检查测试 SQL 只访问本模块 schema，并核对例外；它**不能证明同一模块的不同测试类、不同作业或 PostgreSQL 全局角色互相隔离**。守护变绿不等于共享方案安全。
- CS-5.1 保留真实数据库模块集成；CS-5.3/5.4 限制验收测试修改与断言漂移；CS-5.5/5.6 要求效果归属与确定性等待。数据库独立不等于异步事件/审计、连接池、调度与上下文缓存都独立。[S9]
- 已有缺陷证据：QUAL-003 模板失败后重试和库名冲突；QUAL-009 事件到达先于事务提交（条目将根因标为读代码推断）；QUAL-011 共享协作者 verify 次数被异步审计影响；QUAL-012 容器连接被断；QUAL-013 同机并发 JVM 崩溃（根因未证实）。不得以共享实例或加 fork 为由掩盖它们。QUAL-004 另说明 runner/镜像架构变化曾使 SQL Server 测试超时。[S10]

### 6.5 路线四：构建缓存与依赖预热

**仅靠缓存，完整运行仍约 15 分钟；已测可优化部分的上限很小。** 本机 runner 已使用共享 `~/.m2` 与 pnpm store，pnpm 明确 `--prefer-offline`；工作流不包含 `actions/cache` 还原步骤。不能把没有单列的缓存时间说成“缓存未生效”。[S2][S8]

| 项目 | 已知上限/区间 | 机制与边界 |
|---|---|---|
| Maven 依赖、增量编译/打包 | 已测整段仅 6.456–6.832 秒。即使全部免费，934 秒也仅到约 **927 秒**；真实收益 **0–7 秒** | `backend_verify.sh` 已只 package 一次。跨提交缓存 `target/` 要防删除类、资源/迁移变化与旧报告污染；稳妥缓存依赖，收益主要在未量到的冷启动 |
| pnpm 依赖 | PR install 执行 n=244，P50/P90 8/9 秒，范围 7–14 秒；verify 19/30 秒。只缓存 install 的理论收益 **0–14 秒**，且通常不在后端关键路径 | 可评价 store 预热与锁文件指纹；不能直接复用别次 `node_modules` 或跳过 lock 校验。前端成为快速档关键路径时再独立优化 |
| Spring TestContext 缓存 | **无法给独立节省值**；已有成功启动累计不是缓存可省预算 | pom 两插件均 `reuseForks=true`，上下文不跨 JVM；动态数据库地址、不同配置/自定义器、手工 SpringApplication、独立应用子进程均限制复用。合并上下文还可能共享 mock/事件和数据状态，需类级实验证明 |
| 预热 JVM / daemon 类做法 | **收益缺测，不能按几百秒列入** | 当前是 Maven，不是 Gradle；没有现成 Gradle daemon 可打开。JVM 已设置 `TieredStopAtLevel=1`、SerialGC、`Xshare:auto`。引入 Maven daemon、CDS/AOT 或常驻测试 JVM 会改变构建/隔离机制，需要新实验及版本兼容核验 |

需要调整的文件为 `ci.yml`、`backend_verify.sh`、pom/`.mvn`、前端依赖步骤及 runner 预热管理；不能把本卡当作引入新工具或常驻进程的授权。全量与三项必需检查不变时，缓存本身不降低覆盖；危险在缓存不完整地复用产物而假绿。

**仍未量到**：首次依赖下载、镜像缺失拉取、各 Maven/JVM 冷启动、Spring cache 命中/失效与容量、迁移次数/耗时、分片产物传输、Docker VM 的 CPU/内存/磁盘压力。本批热运行不能推算这些冷路径的 P90。[D1]

### 6.6 路线五：快速档必过，全量放队列或每夜

#### 当前已经有两档，不能重复计算收益

`ci.yml + heavy_gate.py + mq.py` 已实现：非 queued PR 跑轻检查，backend/frontend 名字加 `(light)`；排到队首、同步 main 后标 queued，才给出无后缀的必需检查并执行相关重步骤。main push 是免容器单测及轻检查，`ci` schedule 在 21:03 UTC 全量。strict 的保留是本卡约束。[S2][S6][S8]

队列明确要求 `backend verify`、`frontend verify`、`secret scan` 三项**存在且全为 pass**，不接受 `(light)` 代替；合并锁内重新读取 head、落后状态和检查，按被检查的 head 固定合入。更名、移 job 或引入汇总时必须保持这条语义。

| 门禁安排 | 可达到的反馈时间 | main 风险与判断 |
|---|---|---|
| 快速反馈 + **队列合入前全量**，保留 strict | backend 快速 job 已 29–42 秒；整条快速工作流当前不达 60 秒。释放排队、优化 release 自测/集中轻检查后，才可争取 60 秒；**代码合入仍需约 15 分钟或优化后的全量时间** | 推荐。完整测试较晚启动，但仍在写入 main 之前阻断；风险主要是原有偶发、影响选择错误、检查汇总或新鲜度判断错误 |
| 快速档直接合入，只有每夜全量 | 可省每次约 808–858 秒集成步骤，但 quick 仍有发布自测与排队长路径，不能自动等于 60 秒 | 不推荐。新代码可在约一天的窗口内持续带错合入；strict 只保证代码基线，不证明未跑的集成/启动/契约正确。与 CS-5.11、整合前全量及当前队列“完整检查”语义冲突 |
| 快速档 + 影响测试作为队列最终门禁，夜间全量兜底 | 叶模块可能 80–170 秒，shared/integration 仍接近全量 | 待单独批准。需要先证明影响规划的完备性，不能用一夜没红替代对照验证；不纳入本次推荐的可靠门禁基线 |

#### 近三天快照能估出多大风险

| 观察对象 | 成功 / 失败 / 取消 / 未完 | 失败 ÷ 全部运行 | 失败 ÷ 已有成败结局 |
|---|---:|---:|---:|
| ci / PR，839 次 | 419 / 42 / 377 / 1 | 42/839＝**5.01%** | 42/461＝**9.11%** |
| ci / main push，160 次 | 84 / 9 / 66 / 1 | 9/160＝**5.63%** | 9/93＝**9.68%** |

这两组都是**运行失败率**，不是业务缺陷率，也不是 PR 漏检概率；同一个问题可在多个分支复发，取消有选择性，主线既有偶发又跨越门控改造。

进一步把 `stats.json.effective_job_observations` 与其指向的原始 `jobs/*.json` 按 job ID 关联，沿用数据集的去复用结果：[D1][D2]

- PR 后端完整步骤出现 **35 次失败 job attempt、31 个独立 run**；其中最终为 failure 的 run 有 28 个。前端 `pnpm verify` 出现 **10 次失败 attempt、7 个 run**，其中最终失败 6 个。验收报告缺失另 1 个最终失败 run。这些集合有重叠，不能相加当失败 PR 数。
- 在最终失败的 42 次 PR 运行中，以上完整步骤失败的并集为 **32 次**；其余快检失败集合为 11 次，两集合重叠 1 次，因此 **31 次没有观察到快检失败、只观察到完整步骤失败**。这里只能说它们是“把完整步骤移走后可能延后暴露”的候选，不能证明这些运行的新快检都会成功。
- 9 次 main push 失败全部落在 `surefire and failsafe, side by side`。它们发生在历史完整 push 路径中，不能据此说当前 push 轻量门禁能捕获这些错误。
- 快检可发现的实际失败包括验收修改权限/裁定、system actors 追加规则、相关性脚本、生成文档、规则修订、命名和检出错误；本次没有足够失败日志把 35 次后端步骤失败再拆成“免容器单测能抓”与“只有集成测试能抓”。完整步骤失败也可能来自 fork/容器，而非断言。

**所求概率** `p = P(全量失败 | 快速档通过，同一被测树)` **不可由本批样本识别**。缺少同一 SHA 的快/全量配对、新快速单测的结果、失败类型与去重后的 PR 变更清单，也混入重跑。

可以给调度一个有边界的压力情景：假设成功的 419 次仍通过；上述 31 次候选的快检均会通过；保留已有快检失败的 11 次作为阻断，则会有 `31/(419+31)＝6.89%` 的“快绿结果”在后续遇到完整步骤失败。若新增快单测能抓住部分候选，候选会减少；设剩余候选为 r，且成功的 419 次保持不变，则 `r/(419+r)`、`0≤r≤31` 对应 **0–6.89%**。**这是对固定历史集合的情景，不是未来概率上界，也不是当前 quick-pass 的统计估计。** main 的 9.68% 同样只表示已结束运行的历史报错量级，不能拿来宣称“取消全量以后有 9.68% 的合并会出错”。

QUAL-009/010/011/012/013/015 说明被延后的问题可能包含异步提交、日期漂移、共享 mock、基础设施和合并相互作用；本卡只核对条目原文，不把其中转述/未证实的根因升级为实证。由此应判断“漏到 main 的风险尚未量清，不能当作接近零”，而非给出虚假的小概率。[S10]

**补测方法**：固定 main/base 和 head，快速档与全量同树对照；按 head SHA 记录四格结果，单列“快绿/全量红”、基础设施失败、取消和缺结果；同一错误在多个分支复发另行去重。先覆盖完整工作周及 shared/迁移/启动/异步等高影响变更。若计划声称漏检率 <1%，即使观察到 0 漏检也至少需要约 300 个相对独立、结果齐全的快绿对照样本，才有粗略的单侧 95% 上限 `3/n≈1%`；同源重复运行不能充数。这仍只衡量观察期内的门禁差异，不证明业务覆盖充分。

#### strict 与队列吞吐

队列只有一个代码道，队首最终验证，其他排队 PR 的过早 CI 会被取消；文档道在代码最终 CI 期间等待。四个 runner 不会自动提供四条代码合入流水线。[S6]

按每个代码 PR 一次 **934 秒**完整验证，理想服务率 `3600/934＝3.85 PR/小时`：8 小时 **30.8**、10 小时 **38.5** 个，全天不停约 **92.5** 个。未计重跑、分支同步、排队、文档合集、暂停或部署插队，实际更低。若一天需处理 40 个代码 PR、只在 8 小时服务，理想也积压约 9 个；60 个积压约 29 个，80 个积压约 49 个。按原 4 样本 P90 1077.5 秒逐单计，8 小时只能约 26.7 个；这只是慢服务情景，不是测得队列 P90 容量。

10 月 16 日有 600 次 PR 运行、168 个活跃分支、89 次 main push；它们都不等于“168 或 600 个待合代码 PR”，也不能用 89 次 push 证明全天都承担 15 分钟/代码 PR。需要队列到达/完成日志、tier、唯一 PR、服务时间、取消原因来算真实到达率。strict 要求合入前基于最新 main；在最终 CI 期间主线前进即重新同步验证，原报告提到的 `behind-after-fail` 只作浪费线索。当前代码道优先和合并锁应保留，不能通过并行手工合并来换吞吐。

#### 夜间变红怎样接暂停信号

`mq.py` 的 `Pause.red_runs()` **已有**对 `ci` 工作流 main push 与 schedule 的信号读取：各取最新一个已结束且未取消的结论，红时写 `mq.paused` 暂停两道，无法读取时 `WAIT-SIGNAL`，取队首和最终合并前均检查；恢复须 `mq.py resume <run id>...` 精确确认当时红的运行集合。[S6]

因此全量保留在 `ci.yml` 的 schedule 时，无需另造暂停通道；但暂停发生在发现错误之后，**不会撤销已经进入 main 的提交，也不会阻止发现前的合入**。发现延迟可接近 24 小时，再加排队和全量运行时间，不能按“一分钟内报警”理解。调度应定位首个坏树、处理修复或回退、验证恢复，再确认对应 run，不能自动 acknowledge。若将全量另移到新 workflow、`nightly-legacy-it` 或只让 `ci_recurrence` 变红，当前 Pause **不会自动读取它们**；要扩展信号或在 `ci` 的 schedule 中汇总，补 fail-closed 自测。`ci_recurrence` 是复发诊断，不是现成的队列暂停器。发布门禁目前读 main push 的轻量结果，改检查层级时还须由 one-ops 核对它与合入前全量证据的关联。[S8][S11]

### 6.7 现实的分阶段目标

**各阶段保留“代码合入前全量 + strict + 三项必需检查 + 合并锁”。** 选择性测试先服务反馈；任何门禁范围变更都另开卡，不由本评估追认。下表的时间均以 **触发至必需检查完成**为目标口径；“快速反馈完成”和“可以合入”分别记录。除阶段 0 外，均为带前置条件的工程目标区间，不能当作已有统计结果。

若负责会话核实用户原话后实施报告新增的H，可在阶段1优先采用6.2的520–660秒情景；下表仍以保留原397类为比较基线。H后阶段1的A须只计算剩余慢类的收益，阶段2须用新的类清单与耗时重算，不能沿用原A的250–400类秒节省。

| 阶段 | 包含的工作 | 预计/目标 P50 与 P90 | 前置条件、主要风险 | 负责归属 | 验收指标 |
|---|---|---|---|---|---|
| 0：补齐稳态基线，不改门禁范围 | 保存 tier、实际清单、base/head、分片/阶段时间、全量失败类型和队列到达/服务时间；区分后缀 light 与真正 docs；补失败对照 | **实测基线**：完整 P50 932.5 / P90 1077.5 秒（n=4）；近期成功 light 工作流 P50 171 / P90 316 秒（n=21）。不承诺本阶段提速 | 至少完整工作周；混合历史、取消和重复 run 容易使“平均变快”失真 | one-quality 主持统计；one-ops 提供资源与 runner 数据；调度提供队列事件 | 每个运行有 head/base/tier/实际类清单；取消、缺结果单列；ci 复盘脚本复算一致，人工核对 3 条时间戳；测试类/报告无遗漏 |
| 1：完整约 9–12 分钟量级 | 测试维护方减少三个慢类冗余启动；one-quality 改最长类优先/均衡调度；按真实 release 输入缩窄 push 自测；保留新测试三遍重跑 | **条件估算**完整 P50 **560–740 秒**，P90 **750–1050 秒**；轻量若仍共享队列与运行 release 自测，不承诺 60 秒 | 假设可保留验收含义地省 250–400 类秒、failsafe 有效并行度升至 1.7–1.9，P50 等待/调度 20–50 秒；若拆不掉启动矩阵或资源变差，目标不成立 | one-1a-par 协调原测试维护/独立测试代理；one-quality 负责 CI 与守护；one-ops 量负载；调度排期 | 同树/同负载对照至少 30 次完整运行，另报样本数与取消率；断言和规则映射不减；慢类确实下降；P50≤740、P90≤1050 秒；runner 分钟/合入不增加，偶发不恶化 |
| 2：完整约 3.5–5.5 分钟量级 | 阶段 1 后按新类耗时做 2–3 个后端分片，优先试 3×2 failsafe 槽；留至少一个 runner 周转轻检查；共享编译产物、严格汇总；影响选择器先做全量旁路对照 | **条件估算**完整 P50 **210–330 秒**，P90 **360–600 秒**；保守叶模块反馈约 80–170 秒，shared 仍跑全量 | 假设完整 IT 工作量约 925–1075 类秒、最长类≤90 秒、6 槽有效并行且竞争退化≤约 30%、固定与调度预算≤约 90 秒。需实际预约槽位；增加 JVM、Docker/数据库争用、报告汇总错误是主要风险 | one-quality 做分片/汇总与旁路比较；one-ops 定并发预算与隔离；one-1a-par 协调测试分组；调度限制在途重任务 | 同树全集合恰好覆盖、预期分片全完成；缺片/取消/错误 head/旧产物的故障注入都红；至少 30 次且含白天满载对照，达 P50≤330、P90≤600 秒；队列积压和 runner 分钟不退化 |
| 3：快速反馈争取 1 分钟，完整继续留在队列 | 快速档只保留预算内的密钥、生成/迁移/验收治理、必要单测与守护；完整模块集成、启动矩阵、全量契约/前端及相关 release 自测由队列终审；给轻检查明确的调度预算。若改变 runner 分配或托管政策，另经批准 | **目标而非可预测结果**：快速 P50 **40–60 秒**、P90 **≤60 秒**；队列合入时间沿阶段 2，仍为分钟级。若检查执行本身>45 秒或等待/调度合计>15 秒，则该目标不可达 | 必须先实测整个快档执行≤45 秒、排队与触发≤15 秒；当前 main 免容器单测一步已 29/36.2 秒，release 自测中位数160秒，须明确优化或后移哪些检查。全量前不合入；不能只改名称让快速结果冒充完整结果 | one-quality 定快档清单/预算与完整汇总；one-ops 保障作业槽；调度批准检查职责与排队政策；one-1a-par 确认功能验收留在全量 | 完整工作周、至少100次结果齐全的快档运行，含白天高峰；触发至检查完成 P90≤60秒且取消不作成功；队列仍拒绝未完成全量/旧head/红nightly；逐项记录被延后的覆盖范围 |

阶段 1 的算式：148 个 IT 实测类秒中位数约 1324；减 250–400 后为 924–1074，除 1.7–1.9 为约 486–632 秒，再计约 50 秒固定开销和 20–50 秒触发/等待，为约 556–732 秒，取整至 560–740。P90 750–1050 是给排队/竞争尾部的**验收预算假设**，不是减两个 P90 得到的预测。阶段 2 相同工作量用 6 槽约 154–179 秒，加准备/传输/调度后规划 210–330 秒；P90 预算同样需实测。两阶段节省不能再与容器/缓存收益直接相加，因它们已经影响类 elapsed、排布及上下文次数。

全程使用现有 `repeat_new_tests.py --times 3`：新增/改动测试类按脚本规则再跑三遍，单次失败不靠队列重跑漂绿。当前脚本有最多 40 类、抽象类展开及 OpenAPI/Mssql 排除等边界，须在报告单列。历史重复步骤 P90 约 220 秒，虽总体占比小，对“改了最慢测试类”的单个 PR 不能省略；阶段 1/2 时间目标先按**无需重复慢类的常规 PR**统计，涉及慢类改动的 PR 另列重跑增量，不能删除它们以改善分位数。[S1][S12]

阶段验收还应共同做到：

- 计时复测复用 `ci-data/out/stats.py` 的口径，在新的输出目录运行；按 docs/guarded/code、成功/失败/取消、峰时/非峰时分别报告 job 执行、触发到完成、队列等待、服务率与 runner 分钟。**本卡没有运行原脚本，避免写入只读原始数据目录。**
- 对选择器注入“漏一个依赖方、漏共享输入、漏验收类、删除/改名、只有显示名”并确认失败；恢复正确清单后跑干净控制组。分片/缓存同样做缺结果、旧产物、错误 SHA 的失败验证。不能把 mocked 汇总测试称为真实工作流验收。
- 新测试按规定三遍重跑；每晚用 `ci_recurrence` 分类 QUAL-003/009/011/012/013/014/015/016 等特征，报告原条目状态、旧基线、未登记与日志不可得；已落实条目复发按失败处理。它能监测已登记故障，不证明所有新型漏检都为零。
- one-quality 对 CI/守护改动执行规定评审；one-1a-par 不能自行修改受保护验收目录；涉及真实启动验收重排由原测试维护/测试代理负责，保留所有业务与启动语义。one-ops 只执行另行授权的 runner/常驻资源变更。

**直接回答两个问题：**

1. **完整后端集成测试在现有 4 个 runner 上能不能到 60 秒？当前不能。** 最长类单独就 314–332 秒；原样 148 个 IT 类工作量约 1324 类秒，8 个 IT 槽即使无额外开销也约 166 秒。即便先解决最长类，若总准备预算20秒，8槽要在余下40秒完成，就须把 IT 工作量压到≤320类秒，比现在再少约76%，同时所有类≤40秒；本次五条路没有证据支持这么大的无损压缩。增加同机 runner 注册数不解决这一点。将来若保留当前工作量，要接近一分钟，至少还需把最长类拆到预算内、约30–49个通用类槽的独立资源供给及极低调度开销，或先大幅减少实际工作量；都超出现有已验证能力。
2. **快速档到60秒需要放弃什么？放弃“这次快速反馈已证明完整集成验收、真实启动矩阵、全量契约与构建都通过”的含义，并将超预算检查延后。** 保留“main只经队列且门禁可靠”时，不必放弃这些检查本身，但必须放弃“所有代码一分钟可合入”的承诺：它们仍在队列写入main之前执行。若目标同时包含一分钟合入而全量只每夜跑，就放弃了现有合入前全量保护，不能宣称等价可靠。

### 6.8 证据索引、已验证与未运行

以下均为本机文件。源码路径相对于仓库根；原始数据根为本 worktree 的兄弟目录 `../ci-data/`。数字变换可按给出的 JSON 字段重算，没有使用外部网络或当前价格信息。

- **[S1]** `docs/modernization/quality/CI_TIMING_REVIEW.md`：已有复盘、慢类优化假设、样本边界；原37519版本115行，交付时46fa7增补方案H后已补读，实际最后一级编号仍为5。
- **[S2]** `.github/workflows/ci.yml:1–304`；`scripts/ci/backend_verify.sh:1–55`；`scripts/ci/heavy_gate.py`：三项检查名称、事件门控、实际fork参数、并行插件、release条件、nightly。
- **[S3]** `scripts/ci/relevant_change.py:1–627`，重点 `ReaderIndex`、`classify_reader`、`plan_for`、`tier_of` 与 `main`；`scripts/ci/light_unit_classes.py`；`scripts/ci/guarded_tests.sh`：分档与免容器选择。
- **[S4]** `backend/pom.xml:1–133`；`backend/src/test/java/com/forcitis/one/**`：按文件名/JUnit测试与抽象类排除统计，验收归回对应BC；三个慢类及其辅助类按前文全限定名定位。pom默认fork=4，CI显式覆盖为2，以实际CI调用为准。
- **[S5]** `backend/src/main/java/com/forcitis/one/SubdomainModuleDetection.java`；各模块 `package-info.java`；`backend/architecture/module-dependencies.txt`：13模块、依赖闭包与内核端口注释。
- **[S6]** `scripts/merge/mq.py:1–41,320–329,331–400,499–508,552–696`：文档字符串、必需检查、Pause、落后自查与合并锁。此为静态机制证据，没有证明正在运行的队列进程已装载该版本。
- **[S7]** `backend/src/test/java/com/forcitis/one/TestPostgres.java:25–229`；`TestDatabase.java`；`TestMssql.java`；`TestContainerGuardTest.java`；`TestIsolationGuardTest.java`：工厂、单例、模板、回收与守护范围。
- **[S8]** `docs/modernization/CI_RUNNERS.md`；`.github/workflows/nightly-legacy-it.yml`；`scripts/release/ci_passed.py`：机器、缓存、运行政策与发布关联。说明文档部分旧段仍写“各4fork”“push全量”，与当前yml冲突；本评估以yml及实际日志为准。
- **[S9]** `docs/modernization/CODING_STANDARD.md:149–165`（CS-5.x）；`SESSION_PLAYBOOK.md` §5、§6.1、§11「战队协作」第6/11/14条：评审分级、复发检查/三遍重跑、合入节奏；`CLAUDE.md`、`NAMING_STANDARD.md`：优先级与边界。
- **[S10]** `docs/modernization/quality/defect-bank/one-quality.md:25–173`：QUAL-003/004/009–016。正文保留条目“待落实、推断、未证实”等限定；未以册中状态替代源码核对。
- **[S11]** `scripts/ci/ci_recurrence.py` 的文档字符串、`Gh.runs`、`report`、`render`：覆盖范围、复发判定与缺日志边界；没有运行它的联网取数模式。
- **[S12]** `scripts/ci/repeat_new_tests.py:1–12` 及选择/执行实现；`scripts/merge/mq.py:617–619`：新增测试重复与重复步骤失败不重跑放行。
- **[D1]** `../ci-data/out/stats.json`：`metadata`、`workflows`、`jobs[].steps`、`critical_path`、`logs.test_class_observations`、`logs.test_class_ranking.full`、`logs.maven_phase_timings`、`logs.container_launches`、`effective_job_observations`、`pr_frequency`、`runner_usage`。总计与分位数按原数据的 `(n−1)×p` 线性插值；LPT模拟按典型run `39015755788` 的类seconds降序、每次放入当前最短桶。
- **[D2]** `../ci-data/out/CI_TIMING_DATA.md`（重点31–91、135–177、223–254、289–324、347–389、1061–1191、1731起）；`../ci-data/jobs/*.json`；`../ci-data/runs.json`；`../ci-data/logs/{117340117849,117343810713,117347463988,117350484526}.log`。失败集合按有效job指向的source读取step结论，并以run最终结局筛选；light端到端按有backend light job且run最终success的21个run，取最晚job completed_at减run createdAt；成功Spring启动只汇总数值，不复制可能包含敏感值的日志正文。

**已验证**：必读规范、既有复盘、工作流/脚本/pom/容器与队列机制已离线核对；397类与13模块、显示名映射、反向闭包、类耗时/容器与成功Spring启动累计、失败集合、轻量端到端和分片下界已从本机材料重算。仓库开始时与交付前均为干净工作区，本卡只在指定仓库外目录写草稿。

**precheck（完整输出）**：`build_all.py --check` 会先生成文件，故在仓库外做同提交的本地共享对象克隆，独立检出；在其中运行原版 `scripts/precheck.sh`。快照为 `/private/tmp/qual-1min-precheck-p6p7fyoc/repo`，最终检出交付基线 `46fa7ccff78cff1b40017b15b24fdaa956c9624e`，`origin/main=b85f75e76b86ee63d083b83dcf4a50d4ede6b484`，差异仅为已有复盘文档。本草稿在仓库之外，不进入precheck的改动集合。第一次默认系统Python因缺PyYAML失败；未安装依赖，改用已有的Python 3.12.13 / PyYAML 6.0.3后原命令通过；补读报告增量后又在交付基线上复测。下面为最终运行，退出码0；每个SKIP原文附理由。它证明快照文档检查通过，不证明本报告的优化方案已实现或测试业务覆盖。

```text
precheck: base origin/main (b85f75e7), HEAD 46fa7ccf, 1 changed files (0 uncommitted)

PASS  generated docs (build_all --check)     check: 生成后工作区无差异
PASS  legacy system name                     check_legacy_name: 无违规
SKIP  migrations immutable                   no migration, marker or Flyway setting changed
SKIP  system actors append-only              system-actors.txt and its script did not change
SKIP  acceptance guard                       no acceptance test changed
SKIP  database scripts aligned               no database script or migration changed
SKIP  docs read by builds are registered     no backend, frontend or script changed
SKIP  assertion drift (warning only)         no test file changed
SKIP  glossary: check:copy, test:scripts, term tests terms.yaml, bcs.yaml and backend/glossary unchanged
SKIP  guards: backend guard tests            no guard test or architecture list changed
SKIP  release scripts self-test, shellcheck  relevant_change.py: nothing the release scripts or the builds read changed
PASS  gitleaks (full history)                2746 commits scanned; no leaks
SKIP  backend tests                          relevant_change.py: nothing the builds read changed
SKIP  frontend verify                        relevant_change.py: nothing the builds read changed
SKIP  SQL Server integration tests           relevant_change.py: nothing the *MssqlIT tests exercise changed

SUMMARY: 3 PASS, 0 FAIL, 12 SKIP (base origin/main) — ready
```

**未运行**：Maven/pnpm测试或构建、容器、常驻库、分片/共享/缓存实验、真实快/全量同树对照、CI触发、`ci_recurrence`实时取数、GitHub分支保护/runner/队列进程读回；也没有提交、推送或修改仓库文件。方案时间与风险情景均未被运行验收。

**最不确定的三处**：①唯一PR的路径与模块影响分布，决定大部分改动能否实际命中便宜档；②慢类可无损减少多少启动、同机分片竞争与固定开销，决定阶段1/2提速能否兑现；③快绿/全量红的同树条件概率及其中真实缺陷/偶发的比例，决定进一步缩减合入门禁是否可接受。
