# CI 耗时复盘（2026-10-17）

范围：近 3 天 ci、nightly-legacy-it、release 的全部可取运行（ci 1000 条，恰达下载上限，实际覆盖 2026-10-15 15:49Z 至 10-17 08:34Z 约 41 小时）。只出报告，不改 `ci.yml`。取数与统计由 Codex（gpt-6.1-sol，xhigh）完成，Claude 抽查 3 个数字与关键表；原始数据与复跑脚本在本机 `scratchpad/ci-data/`（`out/CI_TIMING_DATA.md` 约 1750 行全表，`out/stats.py` 可无参复跑；未入库）。数据缺口见文末，结论里凡依赖缺口的都写了「待证」。

## 1. 结论（先看这里）

1. **一次完整 PR 运行（带 `queued` 标签）典型 934 秒，其中 90% 是后端 `surefire and failsafe, side by side` 一步（839 秒）**；排队 52 秒（5.6%），其余检查与收尾约 40 秒。前端、gitleaks、轻量检查都不在关键路径上。要缩短「16 分钟」，只有后端集成测试这一步值得动。
2. 这一步里，**failsafe（集成测试）808–858 秒，surefire（单元）82–98 秒并行**，单元测试不在关键路径。failsafe 有 142 个 IT 类、类耗时合计 1299 秒，实际墙钟 ≈ 830 秒，等效并行度只有 1.56（`-Dtest.forks=2`，理想 2.0）；**最慢的 3 个类合计 505 秒，占 39%：`ReadOnlyAccountCheckIT` 单类 324 秒（192 个用例，每例约 1.7 秒）**、`SingleOperatorModeStartupIT` 100 秒（19 例，每例 5.4 秒）、`ReadOnlyVerificationCommandIT` 82 秒（79 例，每例 1.1 秒）。
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
