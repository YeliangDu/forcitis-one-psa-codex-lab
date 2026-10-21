# 按改动影响挑选测试适配研究

QUAL-CARD-TIA · 2026-10-17 · Codex（gpt-6.1-sol，xhigh）起草，Claude 审稿（结论、数据口径与分母已核对；逐 PR 与逐失败表为脚本生成）。只做本地研究，未实施 CI 或门禁方案。

## 1 结论（一页）

**建议：可以做影子模式研究；当前不建议用 Spring Modulith 原生选测替换合并队列的全量门禁。按本项目的保守边界，收益有限。** 较大收益主要依赖过滤重验收测试，而其模块归属、隐式耦合和历史失败 SHA 均缺证，不能把这些收益当作可交付承诺。

- **数据分母**：461 个 PR，443 个 MERGED、18 个 CLOSED；创建时间 2026-10-14T08:36:48Z 至 2026-10-17T08:30:41Z。这只有项目启动后的约 3 天，虽覆盖给定“近两周”文件的全部历史，并没有两周观测。失败数据为 103 个 job，其中后端 70 个。
- **适配前提**：Modulith 2.1.1 支持扩展（派工给定的官方事实）；当前 starter-test 没有带入 `spring-modulith-junit`，需另加 test 依赖并显式启用 JUnit 自动扩展发现。本机没有该 artifact，因此未做原生执行验证。
- **不是顶层 4 个目录**：生产模块检测是自定义策略，生成 13 个模块：kernel 加 12 个 BC。把 platform 当成一个模块会错算。
- **真实增益很小**：按当前三档回放，246 个 docs、31 个 guarded PR 已不跑完整后端。剩余 166 个 code PR 中，保守方案 158 个回退全量，只有 8 个可减少类；估计平均每个 code PR 省 **3.4 秒**（未测类耗时减半/加倍的敏感性区间 **1.9–6.4 秒**），全体已合入 PR 平均 **1.3 秒**，中位数均为 0。
- **激进收益伴随缺口**：沿给定 Modulith 规则、把未匹配验收/场景包保留为根范围的 R 模型，code PR 平均估计省 **95.3 秒**（敏感性 **50.8–184.5 秒**）。44 个可关联 PR 的已知 JUnit 失败 job 中，模型会省略 18 个 job 的至少一个失败类，即 **19/61 个 job×失败类记录（31.1%）**；其中只有词表读取的 6 条可直接确认“非 Java 输入漏映射”，其余包含既存偶发、时钟漂移、连接故障及无法确认的异步问题。
- **不是生产回归漏检率**：上述是有条件的“已知失败省略率”，不是扩展实测，也不是 PR 引入缺陷的漏检率。使用每个 PR 的最终文件列表与当前源码图，缺失败时 SHA/文件差异，且失败日志只有筛选片段。不能据 31.1% 宣称扩展必漏同样比例，也不能据保守方案 code 子集 0/37 个 job 省略而宣称安全。
- **一分钟目标**：本研究不支持“一分钟可合入”。`CI_TIMING_REVIEW.md` §6.7 的路线仍是完整档约 9–12 分钟 → 约 3.5–5.5 分钟；一分钟用于快速反馈，完整集成在队列中完成。当前验收 105 类约占模型类秒的 72.5%，保留后无法靠模块选测移走主要工作量。

本文的 R（JSON 字段 `native`）只是给定选测规则的**源码近似模型**；S（字段 `safe`）是在 R 上加本项目的保守策略；O（字段 `owner_sensitivity`）仅展示把验收包强行归回业务模块的敏感性，不能冒充 native 行为。原生对 acceptance/scenario、非 Java 改动及工作目录的处理尚未核实；原生若因此全跑，实际收益可以为 0。

## 2 Modulith 扩展核对

### 2.1 版本、依赖、激活与边界

**外部事实来源**：`docs.spring.io/spring-modulith` 测试章节与 `org.springframework.modulith.junit` 包；Claude 已核对 2.1.x，按本卡要求直接采信。本次没有联网，没有取得扩展原件，也没有再次核验在线原文。

已给定的规则：根模块测试、被改模块测试、传递依赖了被改模块的模块测试一起跑；IDE、模块根构建文件、任何 classpath 资源、没有变更时退回全跑。`on-no-changes=skip-all` 是可选的跳过策略，本研究不建议在门禁开启。Boot `STANDALONE / DIRECT_DEPENDENCIES / ALL_DEPENDENCIES` 控制测试上下文切片，属于另一机制；这里也没有 Maven/Gradle 选测插件。

本机只读核对结果：

| 项目 | 结果与证据 |
| --- | --- |
| 版本 | `backend/pom.xml:4–7`：Boot 4.1.1、Java 25、Modulith 2.1.1；当前 BOM 已管理 junit artifact。 |
| starter 传递依赖 | `~/.m2/repository/org/springframework/modulith/spring-modulith-starter-test/2.1.1/*.pom:54–78` 只有 Boot starter-test、modulith-test、modulith-docs、Awaitility；modulith-test 的 POM:54–117 也没有 TIA artifact。只读 POM 证明声明链，不是本次执行的 Maven dependency tree。 |
| 需新增 | `org.springframework.modulith:spring-modulith-junit`，scope=test，版本取现有 BOM。缓存无该 JAR/POM，不能宣称当前可离线接入成功。 |
| JUnit SPI | 本机 JUnit Jupiter engine 6.0.3 的 `DefaultJupiterConfiguration`、`MutableExtensionRegistry` 字节码证明：`junit.jupiter.extensions.autodetection.enabled` 默认 false；开启后 ServiceLoader 发现 Extension。扩展自身 SPI 文件及 condition 类名因缺 JAR 尚未核验。 |
| 现有命令 | `backend_verify.sh:6–13` 先 package，再并行 Surefire 与 Failsafe；pom 默认 forks=4，CI 显式 `-Dtest.forks=2`。TIA 不跳编译、打包、非 JUnit 守护和队列等待。 |
| 独立导出 | `pom.xml:99–113`：OpenApiExportTest 在 process-test-classes 独立 execution，CI 还显式 `surefire:test@export-openapi`；须保持独立且先导出再扫描，不能被选测漏掉。 |
| 额外资源 | pom resources execution 还把 `architecture/system-actors.txt`、`idempotent-endpoints.txt` 复制进 classpath；R 模型已将这两项计为资源全跑，不能只扫描 src/main/resources。 |

未来接入草案（未应用、未跑通）：

```xml
<dependency>
  <groupId>org.springframework.modulith</groupId>
  <artifactId>spring-modulith-junit</artifactId>
  <scope>test</scope>
</dependency>
```

在一个显式选择 profile 中分别给 surefire/failsafe 配置以下 system properties（可用 `systemPropertyVariables`；原件到位后做端到端验证）：

```text
junit.jupiter.extensions.autodetection.enabled=true
spring.modulith.test.file-modification-detector=reference-commit
spring.modulith.test.reference-commit=<本次明确记录的基线 SHA>
```

全量入口必须显式关闭本扩展；若用自动发现开启，至少验证 `junit.jupiter.extensions.autodetection.enabled=false` 后完整类清单恢复。不要全局写入 test/resources 的配置永久开启选测。`backend_verify.sh` 的参数会传到 package、export、surefire、failsafe 三条 Maven 命令，因此实现时必须拆开“始终运行的导出/守护”和“允许过滤的业务测试”；不能只加一组全局 `-D` 就宣称完成。

已给定的检测器：`uncommitted-changes / reference-commit / default / 自定义 FileModificationDetector`。CI 不用 uncommitted-changes 代表 PR diff：检出通常是干净树。完整检出已经是 `fetch-depth: 0`，但仍须验证 base 对象存在、是受信祖先、检出含中间全部提交；缺失、零 SHA、未知基线、解析错误均回退全量。`on-no-changes=skip-all` 禁止作为门禁缺省，避免“没找到 diff”被误当“没有影响”。

**工作目录风险**：backend_verify 会 `cd backend`，扩展以当前目录为根；仓库根 workflow、scripts/ci、共享 docs 可能不在它的扫描范围。外层规划器应始终以 git 根收集完整差异，再将全局输入判定和选测计划传入测试。必要时用自定义检测器，不把 native 的目录相对行为当成已证明覆盖。只有 docs/workflow/scripts 时，native 是否把变化舍弃为“no changes”未知；R 仅按非空原始文件列表模拟，本报告不把这一情形计为已核实行为。

### 2.2 PR 与合并队列的 reference-commit

基线必须是“这棵生产代码树已被**成功完整测试**证明”的提交，不能随意取最新一个绿色 `ci` run。现在 main push 只跑免容器单测，普通 PR 也可能只跑 light；它们不构成全量基线。

- **PR 轻量反馈**：在当前被测树上，优先取 main 上最近成功全量的祖先（已认证的 queue/full 或 ci schedule）。若仅以 `merge-base(PR, origin/main)` 做效率预览，要标“基线未全量认证”，不能拿它替代队列；测试当前 merge tree 时，比较必须包含 main 自认证点以来的变化。
- **队首 PR**：队列同步最新 main 后，记录 `base main SHA + 实际 checkout SHA/tree + PR head SHA`。若当前 main 的生产输入树已被上一笔 queue 全量认证，可用它；否则取更早的成功全量祖先，把这之间所有变化一起纳入。验证“full”必须核对步骤、类清单和报告，不能仅凭 check 名无 light 后缀或 run conclusion=success。
- **main push**：保持现有所有免容器测试，不再按 TIA 缩小；推送绿不更新完整测试认证点。
- **夜间**：关闭 TIA，跑默认全集和相关 legacy-it 全集；只有完整报告、正确 tree、所有预期步骤通过才更新认证点。不能让 schedule 也沿用选测 profile。

当前 mq 的“代码道最新 main + 完整 CI + 固定 head 合并 + 合并锁”与上述基线要求兼容，但 TIA 会改变“完整”的含义，必须另行批准；代码道/文档道、仅缺纯 docs 时免更新的规则都要纳入认证树比较。严格基线与只经队列能防陈旧树合入，不能修复选择器遗漏。

### 2.3 与自写选择器的比较

| 比较项 | Modulith JUnit 扩展 | 在 relevant_change.py 思路上自写规划器 |
| --- | --- | --- |
| 生产模块图 | 复用实际 ApplicationModules 图，较源码正则可靠；本项目需保留自定义策略 | 可扫描 main imports/FQ 类型引用并反向闭包；需自己维护解析与隐式关系，不能把 allowed 清单当实际图 |
| 资源/构建 | 给定规则自动全跑；仓库根 CI/docs 是否可见仍须实证 | 可明确识别所有 repo 输入、复制进 classpath 的架构清单、workflow、helper，未知即全跑 |
| docs 与 helper | Java 模块图不能证明文件读取/夹具依赖 | 已有 ReaderIndex 扫字面路径、分段路径、文件名与 helper；但拼路径、反射、SQL/权限字符串仍会漏，应保留 wide/未覆盖输入回退 |
| always-run | 必须验证 condition 如何豁免，不能假定全局 -Dtest 能覆盖 JUnit condition | 可独立运行免容器守护/验收/导出，再把完整 FQCN 清单分别传 Surefire/Failsafe，覆盖证据更直观 |
| 报告核验 | JUnit 过滤可能仍写 skipped XML；有报告不等于执行过 | 规划器输出 selected/excluded/reasons；严格比对实际 executed/skipped，缺类、空计划、旧 SHA/旧报告失败 |
| 维护成本 | 少写包图逻辑，但仍需仓库外层安全策略与实验 | 无需新依赖；扫描规则、继承/helper/改名与资源识别长期成本较高 |
| 建议 | 先补原件并核验，作影子对照器 | 先用于影子日志/开发反馈；复用现有 docs 三档，别另造一份互相冲突的分档表 |

`light_unit_classes.py` 已通过源码及继承链辨别是否启 Spring/容器，不能把所有 `*Test` 都称轻量。`relevant_change.py` 的 `EXEMPT_HEAVY_READERS={}`：未批准跳过重 docs 读取方；新的 TIA 不能绕过这一决定。

## 3 本项目模块图与测试分布

### 3.1 模块识别与依赖

源码证据：`SubdomainModuleDetection.java:11–15`，`application.properties:9`；只有 `kernel/package-info.java:1` 带 `@ApplicationModule(type=OPEN)`。其余 BC 的 package-info 没有模块注解；api/events 的 `@NamedInterface` 约束出口，不另造模块；无 `allowedDependencies` 属性。`kernel.event` 属于 kernel。直接子目录 cost/kernel/organization/platform 是 4 个命名空间，不能直接作为 4 个应用模块。

下表边来自**main 源码 imports / 全限定类型引用**，去掉注释和字符串，非运行时 ApplicationModules read-back。批准清单恰与非 kernel 源码边一致，但 `module-dependencies.txt` 是允许边清单，二者语义不能互换。直接/传递依赖方都是“谁依赖本模块”；传递列含直接依赖方、不含自身。完整引用证据逐文件保存在 [module_graph.json](module_graph.json)。

| 模块 | 直接依赖的模块 | 直接依赖方 | 传递依赖方 | 原包测试类数 |
| --- | --- | --- | --- | --- |
| `cost.timesheet` | `kernel`, `organization.workforce`, `platform.approval`, `platform.identity`, `platform.numbering` | ∅ | ∅ | 7 |
| `kernel` | ∅ | `cost.timesheet`, `organization.attendance`, `organization.workforce`, `platform.approval`, `platform.audit`, `platform.eventing`, `platform.idempotency`, `platform.identity`, `platform.integration`, `platform.numbering`, `platform.web` | `cost.timesheet`, `organization.attendance`, `organization.workforce`, `platform.approval`, `platform.audit`, `platform.eventing`, `platform.idempotency`, `platform.identity`, `platform.integration`, `platform.numbering`, `platform.web` | 14 |
| `organization.attendance` | `kernel`, `platform.audit` | `platform.approval` | `cost.timesheet`, `platform.approval` | 3 |
| `organization.workforce` | `kernel`, `platform.audit`, `platform.integration` | `cost.timesheet`, `platform.approval`, `platform.identity`, `platform.numbering` | `cost.timesheet`, `platform.approval`, `platform.eventing`, `platform.identity`, `platform.numbering` | 28 |
| `platform.approval` | `kernel`, `organization.attendance`, `organization.workforce`, `platform.audit`, `platform.identity`, `platform.integration` | `cost.timesheet` | `cost.timesheet` | 44 |
| `platform.audit` | `kernel`, `platform.integration` | `organization.attendance`, `organization.workforce`, `platform.approval`, `platform.identity` | `cost.timesheet`, `organization.attendance`, `organization.workforce`, `platform.approval`, `platform.eventing`, `platform.identity`, `platform.numbering` | 17 |
| `platform.eventing` | `kernel`, `platform.identity` | ∅ | ∅ | 3 |
| `platform.idempotency` | `kernel` | ∅ | ∅ | 2 |
| `platform.identity` | `kernel`, `organization.workforce`, `platform.audit`, `platform.integration`, `platform.web` | `cost.timesheet`, `platform.approval`, `platform.eventing`, `platform.numbering` | `cost.timesheet`, `platform.approval`, `platform.eventing`, `platform.numbering` | 46 |
| `platform.integration` | `kernel` | `organization.workforce`, `platform.approval`, `platform.audit`, `platform.identity`, `platform.numbering` | `cost.timesheet`, `organization.attendance`, `organization.workforce`, `platform.approval`, `platform.audit`, `platform.eventing`, `platform.identity`, `platform.numbering` | 52 |
| `platform.numbering` | `kernel`, `organization.workforce`, `platform.identity`, `platform.integration` | `cost.timesheet` | `cost.timesheet` | 9 |
| `platform.scheduling` | ∅ | ∅ | ∅ | 4 |
| `platform.web` | `kernel` | `platform.identity` | `cost.timesheet`, `platform.approval`, `platform.eventing`, `platform.identity`, `platform.numbering` | 8 |

`platform.scheduling` 源码无 kernel import，因此纯静态闭包下 kernel 变更并不一定精确选中 397/397；保守策略直接全跑。`platform.web -> identity` 的反向影响还来自 `NotFromQuery` 标记这一静态边：identity 源码依赖 web，web 改动必须连带 identity、eventing、approval、numbering、timesheet。不能只根据业务直觉删边。

更关键的缺边在 `backend/architecture/module-dependencies.txt:5–7` 已明确写出：`SeparationReview`、`AccountEmployees`、`ExternalSubjectEmployees` 的端口调用方和实现方分属不同模块，经 kernel 连接而不新增普通模块边。更改端口实现不一定改变 kernel 文件；普通反向闭包无法证明会选中调用方的测试。事件的显式 import/监听方法引用可被静态图捕获；经共享端口、事件配置、outbox 表、共享权限字符串或运行时装配发生的协作不能一概认为已捕获。

### 3.2 测试清单与验收/基础设施判定

| 范围 | 默认具体测试类 | 解释 |
| --- | --- | --- |
| 生产模块同包 | 237 | 表中 13 行相加；不把辅助类当测试 |
| 应用根包 | 49 | 含 Modulith/ArchUnit、全局扫描、应用与打包测试，既有轻守护也有重测试 |
| acceptance | 105 | 122 个 Java 文件中有 helper/base，只有 105 个具体测试类；包名不在生产 BC 下 |
| scenario | 6 | 跨模块场景，位于生产模块外 |
| 默认全集 | 397 | 249 个 Surefire +148 个 Failsafe；其中免容器轻测试 205 类（用现有分类器） |
| legacy-it 额外 | 1 | SeeyonViewsMssqlIT 默认不编译；profile 打开时总清单 398，不能因默认模拟未计而漏掉已有路径门控 |

R 把根包、acceptance、scenario 合计 **160 类（40.3%）**一律保留，是本卡的保守归属推断，不是已核验的 native 结论。它们模型类秒合计约 **1130.8**，占全集模型类秒 **76.9%**；模型测试阶段墙钟下限约 **701 秒**。仅验收 105 类约 **677 秒**，足以盖住大部分所省普通模块测试。这解释了“删了六成类，却只省一小段时间”。

原生是否把 test-only `acceptance.platform` 当模块、归到根或无法匹配后回退全量，缺扩展源码与执行证据。`TestIsolationGuardTest:29–37,86–92` 的验收目录映射只是该 SQL 守护自己的规则，不能当成 Modulith 的规则。O 模型人为去掉 acceptance 前缀，风险更高，仅用于敏感性分析。

`TestPostgres`、`TestMssql`、`TestImages`、AcceptanceSupport、fixture/抽象基类本身不是可执行测试类；它们改动应影响所有使用方。当前 `TestPostgres:133–197` 共享容器、按类克隆模板、完整迁移与配置输入会跨 BC；S 对所有 helper/fixture、架构清单、main 根 Java、kernel 和 CI 改动退回全量。全局根守护不靠 Java import 判断影响；S 还始终保留全部 205 个免容器单测，使位于 BC 内的 `DocumentTypeCodeTest`、`SeeyonSourceScanTest` 等扫描守护也会运行。

## 4 PR 模拟结果（表）

### 4.1 方法、模型与耗时假设

`select.py` 输入路径列表，输出 changed_modules、反向闭包、具体 FQCN、选中数/397、全跑原因及触发路径。`reproduce.py` 对 461 个 PR 都计算；报告统计仅含 443 个 MERGED，18 个 CLOSED 保留在 CSV/JSON，不混入收益分母。

- **R**：按给定选测规则；资源/pom/空文件列表全跑；保留未匹配根/验收/场景；workflow、kernel、helper 不额外伪造“官方全跑规则”，由静态闭包决定。只有 no changes 的“文件列表为空”已模拟；docs 被 native 丢弃后的 no-changes 行为未知。
- **S**：R 加每次所有轻测试；kernel、根 Java、所有 test helper/fixture、架构清单、CI/workflow/Maven wrapper 全跑；code 档又找不到被改生产模块时全跑。现有 guarded/docs 仍按现有门控执行，不替换为 S 的大清单。它故意非常保守，展示满足已知边界后的收益。
- **O**：只把 `acceptance/<子域>/<BC>` 强行映射回 BC，其他与 R 相同；不是建议，也没有证据表明原生就这样做。

**耗时输入有限**：[CI_TIMING_DATA.md](../ci-data/out/CI_TIMING_DATA.md:1110) 仅完整档前 30，4 个观测/类；29 个 IT 合计中位数 845.58 秒、1 个 Surefire 类 12.24 秒。显示名 OperationsProfile 精确匹配 `OperationsProfileSmokeTest.java:37`，已还原全限定名。其余类分别用阶段剩余均值，不当成逐类实测。

研究中仓库外部前进，仅规范/复盘等文档变化，生产源码、pom、CI 和选择脚本未变；新增 `CI_TIMING_REVIEW.md` §6.1 已修正旧 142 IT/255 unit 的显示名漏计。本研究最终采用修正的 148/249 类，以及典型 run 的 IT 1318.087、Surefire 151.811 类秒作总量约束：

```text
未知 IT 均值 = (1318.087 - 845.58) / (148 - 29) = 3.970647 秒/类
未知 unit 均值 = (151.811 - 12.24) / (249 - 1) = 0.562786 秒/类
Wfull = 1469.898 类秒（累计 elapsed，不是 CPU 秒）
T(plan) = max(
  sum(IT 类秒)/1.56, 最长 IT 类,
  sum(unit 类秒)/1.90, 最长 unit 类
)
省秒 = max(0, T(full) - T(plan))
```

1.56 来自既有 failsafe 复盘；1.90 是 Surefire 等效并行度假设。当前完整清单模型约 845 秒，与实际 failsafe 808–858 秒量级相符，不能据此证明缩减清单后并行效率不变。选择会改变 fork 尾部、上下文缓存/模板初始化；固定编译、导出、准备、守护、队列等待、三遍重跑和 legacy-it 未计为可省。估算只表示后端测试阶段差额，不能直接叫实际端到端加速。

敏感性把所有未知类均值乘 0.5 或 2，已测前30不变，重新计算完整/所选两边；这是工程假设区间，不是统计置信区间。结构性未知（真实 native 全跑、隐性耦合需要扩大范围）还能把收益压到 0。不同 PR 的省秒只各计一个假想入队全量轮次，不乘历史重跑数，不等于实际 runner 分钟已节省。

### 4.2 现有分档与典型 PR

| 当前规则重放的 tier | 合入 PR 数 | 占 443 | TIA 增量基线 |
| --- | --- | --- | --- |
| docs | 246 | 55.5% | 已跳过完整后端，省秒记 0 |
| guarded | 31 | 7.0% | 已跳过完整后端，省秒记 0 |
| code | 166 | 37.5% | 只比较 queued 时原本会跑的完整后端 |

典型 PR 按数量落在 docs 档（55.5%），TIA 不再增加收益。code 档的典型 PR 在 S 下仍全跑，中位省秒 0；不能拿“全体 PR 选中率很低”推断常见代码提交也很便宜。三档是用**当前规则**重放历史文件列表，不是当时 CI 的真实档位，样本跨门控改造前后。

| 模型 | 全跑 PR（443分母） | code 档可减测/166 | 全体平均省秒 | code 平均省秒〔敏感性〕 | code 中位选中率 |
| --- | --- | --- | --- | --- | --- |
| R | 39 (8.8%) | 127/166 | 35.7 | 95.3 [50.8–184.5] | 40.3% |
| S | 158 (35.7%) | 8/166 | 1.3 | 3.4 [1.9–6.4] | 100.0% |
| O | 39 (8.8%) | 127/166 | 207.1 | 552.6 [455.7–746.3] | 16.1% |

以上全跑数恰均属于 code 档；R/O 的少选风险与 S 的安全回退不可混为“同样保障下的不同算法效率”。S 仅 8 个 code PR 少跑，清单为 #201、#291、#326、#408、#409、#413、#447、#453。

### 4.3 分布：按 PR 数与预计省秒

| 模型 | 选中类比例 | PR 数 | PR 占比 | code 增量省秒合计 | 占该模型省秒 |
| --- | --- | --- | --- | --- | --- |
| R | <=50% | 381 | 86.0% | 14943.7 | 94.4% |
| R | 100% | 39 | 8.8% | 0.0 | 0.0% |
| R | 75–100% | 13 | 2.9% | 235.5 | 1.5% |
| R | 50–75% | 10 | 2.3% | 644.2 | 4.1% |
| S | 75–100% | 285 | 64.3% | 566.5 | 100.0% |
| S | 100% | 158 | 35.7% | 0.0 | 0.0% |
| O（仅敏感性） | <=50% | 383 | 86.5% | 86400.0 | 94.2% |
| O（仅敏感性） | 100% | 39 | 8.8% | 0.0 | 0.0% |
| O（仅敏感性） | 75–100% | 10 | 2.3% | 162.9 | 0.2% |
| O（仅敏感性） | 50–75% | 11 | 2.5% | 5168.5 | 5.6% |

R 的 ≤50% 桶看似覆盖 381 个 PR，但其中大量 docs/guarded 早已不跑全集；且保留验收重类使“类数减少”远大于“类秒减少”。O 把 SQL Server/启动矩阵验收移走后数字大幅变好，恰是本研究最不能无证接受的部分。

### 4.4 全跑原因及共享输入

下面是**触及比例**与触及时的全跑数，多标签可重叠；不是因果归因，不能把各行相加。kernel/platform 是影响范围标签，不是给定官方规则中的强制全跑项；platform 也是 9 个独立模块，而不是一个超级模块。

| 触及类别 | PR 数 | 占443 | R 触及时全跑 | S 触及时全跑 |
| --- | --- | --- | --- | --- |
| 共享 kernel | 16 | 3.6% | 11 | 16 |
| 任一 platform 生产源码 | 41 | 9.3% | 28 | 38 |
| db/migration | 21 | 4.7% | 21 | 21 |
| workflow / scripts/ci / wrapper | 52 | 11.7% | 10 | 52 |
| test helper / fixture | 40 | 9.0% | 19 | 40 |
| 任何 classpath 资源（含额外复制资源） | 31 | 7.0% | 31 | 31 |
| pom / 模块根构建文件 | 10 | 2.3% | 10 | 10 |

R 的独立全跑原因：classpath_resource 31（7.0%）、build_file 10（2.3%）、graph_selects_all 1（0.2%），去重后 39（8.8%）。S 的原因还包括 build_ci_policy 52、architecture_policy 36、root_java_policy 5、kernel_policy 16、test_infrastructure_policy 40、unmapped_code_input_policy 108，去重后 158（35.7%，或 code 档的 95.2%）。逐 PR 的 `reason_paths` 保留原始触发路径；如 migration 同时也是 classpath_resource，前者不能再算一份额外强制全跑。

### 4.5 少量有增益的 S 场景

| PR | 改动 | 改动模块 | S 类数/397 | 省秒 | 敏感性 |
| --- | --- | --- | --- | --- | --- |
| #201 | test(identity): B1-R3 断言说明如实，登记 OPS-PORT 结构性卡 | `platform.identity` | 361 | 62.4 | 35.7–115.8 |
| #291 | fix(identity): 应急登录与进入管理员模式的告警日志不写客户端 IP（O-3） | `platform.identity` | 361 | 62.4 | 35.7–115.8 |
| #326 | test(cost.timesheet): 提交后等审批请求已提交再继续，修 cancelledTimesheetIsADraft 偶发 | `cost.timesheet` | 311 | 136.2 | 72.6–263.5 |
| #408 | test(approval): StartApprovalOnSubmissionIT 偶发修复（等已提交状态） | `platform.approval` | 335 | 75.1 | 42.0–141.3 |
| #409 | test(workforce): 同步放行用例的审计断言只看本测试自己的运行（共享 mock，CS-5.6） | `organization.workforce` | 374 | 59.8 | 34.4–110.7 |
| #413 | test(approval): ProcessArchiveJobIT 保留期窗口按 START 计（修时间炸弹） | `platform.approval` | 335 | 75.1 | 42.0–141.3 |
| #447 | fix(identity): 限流拒绝消息与审计写入失败日志不带客户端 IP（O-3 范围外缺陷） | `platform.identity` | 361 | 62.4 | 35.7–115.8 |
| #453 | feat(audit): AuditLog.record 返回所写条目的类型化 ID（ADR-0034-IMPL C-6） | `platform.audit` | 382 | 33.1 | 16.5–66.2 |

这 8 个 PR 合计估计省 566.5 秒（314.5–1070.5），平均每个可减测 PR 约 70.8 秒；它们主要是局部修复/测试修复，不能据此预测下一批业务开发的分布。

### 4.6 全部 443 个已合入 PR 逐项表

每行都是已给定最终路径集在当前源码上的反事实计算。R 类数是含根/验收保留的条件结果；“增量省秒”已扣掉 docs/guarded 基线，S/R 均未实际执行。全部 461 个 PR 的路径、additions/deletions、分支、模块、完整 FQCN 及原因见 [pr_simulation.csv](pr_simulation.csv) / [JSON](pr_simulation.json)。

| PR | 当前 tier | 改动模块 | R类数/397 | R全跑原因 | R增量省秒 | S增量省秒 |
| --- | --- | --- | --- | --- | --- | --- |
| #1 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #2 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #3 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #4 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #5 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #6 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #7 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #8 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #9 | code | `cost.timesheet`, `kernel`, `organization.workforce`, `platform.approval`, `platform.audit`, `platform.identity`, `platform.integration`, `platform.numbering`, `platform.scheduling` | 397 | build_file, classpath_resource | 0.0 | 0.0 |
| #10 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #11 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #12 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #13 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #14 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #15 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #16 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #17 | code | `organization.workforce`, `platform.approval` | 397 | build_file | 0.0 | 0.0 |
| #18 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #19 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #21 | code | `cost.timesheet`, `kernel`, `organization.workforce`, `platform.approval`, `platform.audit`, `platform.identity`, `platform.integration`, `platform.numbering` | 397 | classpath_resource | 0.0 | 0.0 |
| #22 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #23 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #24 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #25 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #26 | code | `cost.timesheet`, `kernel`, `organization.workforce`, `platform.approval` | 393 | — | 5.1 | 0.0 |
| #27 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #28 | code | `cost.timesheet`, `platform.approval` | 397 | classpath_resource | 0.0 | 0.0 |
| #29 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #30 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #31 | code | `organization.workforce`, `platform.audit`, `platform.identity`, `platform.integration`, `platform.numbering` | 397 | classpath_resource | 0.0 | 0.0 |
| #32 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #33 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #34 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #35 | code | `cost.timesheet`, `kernel`, `organization.workforce` | 393 | — | 5.1 | 0.0 |
| #36 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #37 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #38 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #39 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #40 | guarded | ∅ | 160 | — | 0.0 | 0.0 |
| #41 | code | `cost.timesheet`, `kernel`, `organization.attendance`, `organization.workforce`, `platform.approval`, `platform.audit`, `platform.identity`, `platform.integration`, `platform.numbering` | 397 | classpath_resource | 0.0 | 0.0 |
| #42 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #43 | guarded | ∅ | 160 | — | 0.0 | 0.0 |
| #44 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #45 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #46 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #47 | guarded | ∅ | 160 | — | 0.0 | 0.0 |
| #48 | guarded | ∅ | 160 | — | 0.0 | 0.0 |
| #49 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #50 | code | `cost.timesheet`, `kernel`, `organization.attendance`, `organization.workforce` | 397 | build_file | 0.0 | 0.0 |
| #51 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #52 | guarded | ∅ | 160 | — | 0.0 | 0.0 |
| #53 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #54 | code | ∅ | 397 | build_file | 0.0 | 0.0 |
| #55 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #57 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #58 | guarded | ∅ | 160 | — | 0.0 | 0.0 |
| #59 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #60 | guarded | ∅ | 160 | — | 0.0 | 0.0 |
| #61 | guarded | ∅ | 160 | — | 0.0 | 0.0 |
| #63 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #64 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #65 | guarded | ∅ | 160 | — | 0.0 | 0.0 |
| #66 | guarded | ∅ | 160 | — | 0.0 | 0.0 |
| #67 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #68 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #69 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #70 | guarded | ∅ | 160 | — | 0.0 | 0.0 |
| #71 | guarded | ∅ | 160 | — | 0.0 | 0.0 |
| #72 | code | `cost.timesheet`, `kernel`, `organization.attendance` | 397 | build_file | 0.0 | 0.0 |
| #73 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #74 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #75 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #76 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #77 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #78 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #79 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #80 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #81 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #82 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #83 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #84 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #85 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #86 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #87 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #88 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #89 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #90 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #91 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #92 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #93 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #94 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #95 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #97 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #98 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #99 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #100 | guarded | ∅ | 160 | — | 0.0 | 0.0 |
| #101 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #102 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #103 | code | `platform.integration`, `platform.numbering` | 397 | classpath_resource | 0.0 | 0.0 |
| #104 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #105 | guarded | ∅ | 160 | — | 0.0 | 0.0 |
| #106 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #107 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #108 | guarded | ∅ | 160 | — | 0.0 | 0.0 |
| #109 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #110 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #111 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #112 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #113 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #114 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #115 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #116 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #117 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #118 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #119 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #120 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #121 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #122 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #123 | guarded | ∅ | 160 | — | 0.0 | 0.0 |
| #124 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #125 | code | `cost.timesheet`, `kernel`, `organization.attendance`, `organization.workforce`, `platform.approval`, `platform.audit`, `platform.idempotency`, `platform.identity`, `platform.integration` | 393 | — | 5.1 | 0.0 |
| #126 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #127 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #128 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #129 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #130 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #131 | guarded | ∅ | 160 | — | 0.0 | 0.0 |
| #132 | code | `platform.identity` | 269 | — | 62.4 | 0.0 |
| #133 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #134 | code | `platform.identity`, `platform.web` | 397 | classpath_resource | 0.0 | 0.0 |
| #135 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #136 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #137 | code | `platform.numbering` | 397 | classpath_resource | 0.0 | 0.0 |
| #138 | guarded | ∅ | 160 | — | 0.0 | 0.0 |
| #139 | code | `kernel`, `organization.workforce`, `platform.identity`, `platform.integration`, `platform.numbering`, `platform.web` | 397 | classpath_resource | 0.0 | 0.0 |
| #140 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #141 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #142 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #143 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #144 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #145 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #146 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #147 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #148 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #149 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #150 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #151 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #152 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #153 | code | `organization.workforce`, `platform.approval`, `platform.integration` | 397 | classpath_resource | 0.0 | 0.0 |
| #154 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #155 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #156 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #157 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #158 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #159 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #160 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #161 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #162 | code | `cost.timesheet`, `kernel`, `organization.workforce`, `platform.approval`, `platform.eventing`, `platform.identity` | 397 | build_file | 0.0 | 0.0 |
| #163 | code | `platform.approval`, `platform.integration` | 397 | classpath_resource | 0.0 | 0.0 |
| #164 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #165 | code | ∅ | 397 | build_file | 0.0 | 0.0 |
| #166 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #167 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #168 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #169 | guarded | ∅ | 160 | — | 0.0 | 0.0 |
| #170 | guarded | ∅ | 160 | — | 0.0 | 0.0 |
| #171 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #172 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #173 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #174 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #175 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #176 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #177 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #178 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #179 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #180 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #181 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #182 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #183 | code | `platform.approval` | 397 | classpath_resource | 0.0 | 0.0 |
| #184 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #185 | guarded | ∅ | 160 | — | 0.0 | 0.0 |
| #186 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #187 | guarded | ∅ | 160 | — | 0.0 | 0.0 |
| #188 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #189 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #190 | code | `cost.timesheet`, `kernel`, `organization.attendance`, `organization.workforce`, `platform.approval`, `platform.audit`, `platform.idempotency`, `platform.identity`, `platform.integration`, `platform.numbering`, `platform.scheduling`, `platform.web` | 397 | graph_selects_all | 0.0 | 0.0 |
| #191 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #192 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #193 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #194 | guarded | ∅ | 160 | — | 0.0 | 0.0 |
| #195 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #197 | code | `platform.integration` | 397 | classpath_resource | 0.0 | 0.0 |
| #198 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #200 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #201 | code | `platform.identity` | 269 | — | 62.4 | 62.4 |
| #202 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #203 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #204 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #205 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #206 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #207 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #208 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #209 | code | `cost.timesheet`, `kernel`, `organization.workforce`, `platform.approval`, `platform.audit`, `platform.eventing`, `platform.idempotency`, `platform.identity` | 397 | classpath_resource, build_file | 0.0 | 0.0 |
| #210 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #211 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #212 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #213 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #214 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #215 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #216 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #217 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #218 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #219 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #220 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #221 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #222 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #223 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #224 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #225 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #226 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #227 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #228 | code | `platform.identity`, `platform.scheduling` | 397 | classpath_resource | 0.0 | 0.0 |
| #229 | guarded | ∅ | 160 | — | 0.0 | 0.0 |
| #230 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #231 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #232 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #233 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #234 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #235 | code | `platform.integration` | 369 | — | 20.4 | 0.0 |
| #236 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #237 | code | ∅ | 397 | build_file | 0.0 | 0.0 |
| #238 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #239 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #240 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #241 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #242 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #243 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #244 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #245 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #246 | code | `organization.attendance`, `organization.workforce` | 300 | — | 57.3 | 0.0 |
| #247 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #249 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #250 | code | `platform.integration` | 397 | classpath_resource | 0.0 | 0.0 |
| #251 | code | `platform.integration` | 369 | — | 20.4 | 0.0 |
| #252 | guarded | ∅ | 160 | — | 0.0 | 0.0 |
| #253 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #255 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #256 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #257 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #258 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #259 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #260 | code | `cost.timesheet`, `kernel`, `organization.workforce`, `platform.approval`, `platform.identity`, `platform.integration` | 393 | — | 5.1 | 0.0 |
| #261 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #262 | guarded | ∅ | 160 | — | 0.0 | 0.0 |
| #263 | code | `organization.attendance`, `organization.workforce`, `platform.integration` | 369 | — | 20.4 | 0.0 |
| #264 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #265 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #266 | guarded | ∅ | 160 | — | 0.0 | 0.0 |
| #267 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #268 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #269 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #270 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #272 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #273 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #274 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #276 | code | `cost.timesheet`, `kernel`, `platform.approval`, `platform.integration`, `platform.numbering` | 397 | classpath_resource | 0.0 | 0.0 |
| #277 | code | `platform.integration` | 369 | — | 20.4 | 0.0 |
| #278 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #279 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #280 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #281 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #282 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #283 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #284 | guarded | ∅ | 160 | — | 0.0 | 0.0 |
| #285 | guarded | ∅ | 160 | — | 0.0 | 0.0 |
| #286 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #287 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #288 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #290 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #291 | code | `platform.identity` | 269 | — | 62.4 | 62.4 |
| #292 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #293 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #294 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #295 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #296 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #297 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #298 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #299 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #300 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #301 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #302 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #303 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #304 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #305 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #306 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #307 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #308 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #309 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #310 | code | `cost.timesheet`, `kernel`, `organization.attendance`, `organization.workforce`, `platform.approval`, `platform.audit`, `platform.eventing`, `platform.identity` | 393 | — | 5.1 | 0.0 |
| #311 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #312 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #313 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #314 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #315 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #316 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #317 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #318 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #319 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #320 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #321 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #322 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #323 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #324 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #325 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #326 | code | `cost.timesheet` | 167 | — | 136.2 | 136.2 |
| #327 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #328 | code | `platform.identity` | 397 | classpath_resource | 0.0 | 0.0 |
| #329 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #330 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #331 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #332 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #333 | code | `cost.timesheet`, `kernel`, `platform.approval`, `platform.numbering` | 393 | — | 5.1 | 0.0 |
| #334 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #335 | code | ∅ | 397 | classpath_resource | 0.0 | 0.0 |
| #336 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #337 | code | `kernel`, `organization.workforce`, `platform.audit`, `platform.idempotency`, `platform.identity`, `platform.integration`, `platform.web` | 397 | classpath_resource, build_file | 0.0 | 0.0 |
| #338 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #339 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #340 | code | `cost.timesheet` | 167 | — | 136.2 | 0.0 |
| #341 | code | `organization.workforce` | 297 | — | 59.8 | 0.0 |
| #342 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #343 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #344 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #345 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #346 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #347 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #348 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #349 | code | `platform.identity` | 269 | — | 62.4 | 0.0 |
| #350 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #351 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #352 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #353 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #354 | code | `platform.approval` | 397 | classpath_resource | 0.0 | 0.0 |
| #356 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #357 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #359 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #361 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #362 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #363 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #364 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #365 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #366 | code | `organization.workforce` | 397 | classpath_resource | 0.0 | 0.0 |
| #367 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #369 | code | `platform.approval`, `platform.numbering` | 397 | classpath_resource | 0.0 | 0.0 |
| #370 | code | `platform.audit` | 397 | classpath_resource | 0.0 | 0.0 |
| #371 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #372 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #373 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #374 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #375 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #376 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #377 | code | `platform.audit`, `platform.web` | 397 | classpath_resource | 0.0 | 0.0 |
| #378 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #379 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #380 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #381 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #382 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #383 | code | `platform.audit` | 397 | classpath_resource | 0.0 | 0.0 |
| #384 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #385 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #386 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #387 | code | `organization.workforce` | 397 | classpath_resource | 0.0 | 0.0 |
| #388 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #391 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #392 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #393 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #394 | guarded | ∅ | 160 | — | 0.0 | 0.0 |
| #395 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #396 | code | ∅ | 397 | classpath_resource | 0.0 | 0.0 |
| #397 | code | ∅ | 397 | classpath_resource | 0.0 | 0.0 |
| #398 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #399 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #400 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #401 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #402 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #403 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #404 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #405 | code | `organization.workforce`, `platform.approval`, `platform.audit`, `platform.identity`, `platform.numbering` | 397 | classpath_resource | 0.0 | 0.0 |
| #406 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #407 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #408 | code | `platform.approval` | 211 | — | 75.1 | 75.1 |
| #409 | code | `organization.workforce` | 297 | — | 59.8 | 59.8 |
| #410 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #411 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #412 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #413 | code | `platform.approval` | 211 | — | 75.1 | 75.1 |
| #414 | guarded | ∅ | 160 | — | 0.0 | 0.0 |
| #415 | code | `platform.identity` | 397 | classpath_resource | 0.0 | 0.0 |
| #416 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #417 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #418 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #419 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #420 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #421 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #422 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #423 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #424 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #425 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #426 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #427 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #428 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #429 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #430 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #431 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #432 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #433 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #434 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #435 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #436 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #437 | code | `platform.identity` | 269 | — | 62.4 | 0.0 |
| #438 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #439 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #440 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #441 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #442 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #443 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #444 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #445 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #446 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #447 | code | `platform.identity` | 269 | — | 62.4 | 62.4 |
| #448 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #449 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #450 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #452 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #453 | code | `platform.audit` | 317 | — | 33.1 | 33.1 |
| #454 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #455 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #456 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #457 | code | `organization.workforce`, `platform.audit`, `platform.identity` | 317 | — | 33.1 | 0.0 |
| #458 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #459 | docs | ∅ | 160 | — | 0.0 | 0.0 |
| #460 | code | ∅ | 160 | — | 143.8 | 0.0 |
| #461 | code | ∅ | 160 | — | 143.8 | 0.0 |

## 5 失败回放（表，含漏检归因）

### 5.1 方法与分母

103 个失败 job 都有一个摘录文件，覆盖 2026-10-14 至 10-17 的几天；不是全文。88 个 PR 事件用 `headRefName` 匹配，再核对 PR 的创建/关闭时间区间，均得到唯一 PR；11 个 push、4 个 release workflow_dispatch 没有 PR 文件列表，不按“最近合入 PR”猜归因。

从 `<<< FAILURE/ERROR` 行抽取 FQCN，显示名汇总再用具体失败方法行辨识；无法定位类的 fork 崩溃/只有 exit 1 保持未知。报告缺失不是 JUnit 断言失败，单独分类。按 job×具体失败类去重，同类的参数化用例不重复计数。全部 103 行 job 回放见 [failure_replay.csv](failure_replay.csv)，所有类对照及日志行号见 [failure_class_pairs.csv](failure_class_pairs.csv)。

| 后端 70 个 job 的分类 | 数量 | 选测解释 |
| --- | --- | --- |
| 可识别 JUnit 类失败 | 53 | 44 个 PR job 可模拟；9 个 main push 无差异基线，无法判断选中 |
| 非 JUnit 工作流检查 | 8 | 验收变更守护3、system actor追加守护1、relevant_change输入/读取自测4；不受扩展过滤，应保留 |
| 验收报告守护 | 1 | 缺 SingleOperatorModeListenerTest 报告；这是执行证据缺失，不能算“测试通过/普通不选中” |
| 无法定位失败测试类 | 8 | 6 个可见 fork 启动/崩溃问题、2 个只见步骤失败；不能把任何类算作被捕获或漏选 |

**R 已知失败省略率**：61 个可比较 job×失败类中 19 个（31.1%）不在清单；44 个 PR job 中 18 个（40.9%）至少省略一类。只看当前 code 档，48 个类记录中 15 个省略，37 个 job 中 14 个至少省略一类。其余是 docs/guarded 历史行为差异。

**S 对照**：同一 61 个类记录中仍省略 4 个（6.6%），来自 4 个当前 docs 档历史 job；code 档 48 个类记录、37 个 job 均会选中。这个“零”主要是因为保守策略基本退回全跑，并不验证遗漏风险为零。O 强归业务模块会省略 41/61（67.2%）、28/44 个 job，说明假定验收可按 BC 跳过会显著改变覆盖。

这些样本是**失败条件样本、重复 PR/同一故障并不独立**；没有全部可比的全量成功样本、失败 head diff 或新缺陷因果，因此不做总体概率和置信区间推断。被省略的基础设施偶发可能意味着少运行少出错；也可能遮住环境退化，不能直接把它记为产品回归漏检或当作提高质量的收益。

### 5.2 R 会省略的全部 19 个失败类记录

日志链接直接指向给定摘录；行号是该摘录内行号。S“选中”不表示失败已解决。类别带“未证实”的只是已知故障模式线索，日志未显示详细断言，不硬判根因。

| job / PR | 失败类 | 摘录行 | R | S | 归因/边界 |
| --- | --- | --- | --- | --- | --- |
| [117055954450](../tia-data/failures/117055954450.txt) / #404 | `organization.attendance.WorkCalendarIT` | 9,13,15 | 省略 | 选中 | 测试基础设施/连接；日志存在容器或数据库连接错误；不据此证明由 PR 引发 |
| [117055954450](../tia-data/failures/117055954450.txt) / #404 | `platform.idempotency.IdempotentResponsesConstraintIT` | 30 | 省略 | 选中 | 测试基础设施/连接；日志存在容器或数据库连接错误；不据此证明由 PR 引发 |
| [117078749963](../tia-data/failures/117078749963.txt) / #401 | `platform.approval.infrastructure.flowable.ProcessArchiveJobIT` | 11 | 省略 | 选中 | 真实时钟/既存测试漂移；archivesthenDeletes；QUAL-010 记固定时钟与真实日历混用，摘录未独立复现 |
| [116957050483](../tia-data/failures/116957050483.txt) / #358 | `organization.workforce.application.OrgSnapshotSyncIntegrationTest` | 7 | 省略 | 选中 | 异步共享协作者（未证实）；本摘录只见失败类和方法；QUAL-011 仅提供同类解释，无法判定该次根因 |
| [116924131253](../tia-data/failures/116924131253.txt) / #342 | `organization.workforce.application.OrgSnapshotSyncIntegrationTest` | 7 | 省略 | 选中 | 异步共享协作者（未证实）；本摘录只见失败类和方法；QUAL-011 仅提供同类解释，无法判定该次根因 |
| [116540318924](../tia-data/failures/116540318924.txt) / #212 | `platform.numbering.DocumentTypeCodeTest` | 4 | 省略 | 选中 | 文档读取/词表；everyCodeIsAGlossaryWord；源代码读 Glossary，非 Java import 图输入 |
| [116695785312](../tia-data/failures/116695785312.txt) / #271 | `platform.identity.AdministratorModeIntegrationTest` | 7,17,19 | 省略 | 选中 | 测试基础设施/连接；日志存在容器或数据库连接错误；不据此证明由 PR 引发 |
| [117070715128](../tia-data/failures/117070715128.txt) / #392 | `platform.approval.infrastructure.flowable.ProcessArchiveJobIT` | 11 | 省略 | 选中 | 真实时钟/既存测试漂移；archivesthenDeletes；QUAL-010 记固定时钟与真实日历混用，摘录未独立复现 |
| [117071772424](../tia-data/failures/117071772424.txt) / #374 | `platform.approval.infrastructure.flowable.ProcessArchiveJobIT` | 11 | 省略 | 选中 | 真实时钟/既存测试漂移；archivesthenDeletes；QUAL-010 记固定时钟与真实日历混用，摘录未独立复现 |
| [117180098192](../tia-data/failures/117180098192.txt) / #406 | `platform.approval.infrastructure.flowable.ProcessArchiveJobIT` | 11 | 省略 | 选中 | 真实时钟/既存测试漂移；archivesthenDeletes；QUAL-010 记固定时钟与真实日历混用，摘录未独立复现 |
| [116555565794](../tia-data/failures/116555565794.txt) / #212 | `platform.numbering.DocumentTypeCodeTest` | 4 | 省略 | 选中 | 文档读取/词表；everyCodeIsAGlossaryWord；源代码读 Glossary，非 Java import 图输入 |
| [116477000597](../tia-data/failures/116477000597.txt) / #199 | `platform.numbering.DocumentTypeCodeTest` | 4 | 省略 | 选中 | 文档读取/词表；everyCodeIsAGlossaryWord；源代码读 Glossary，非 Java import 图输入 |
| [115814081024](../tia-data/failures/115814081024.txt) / #20 | `organization.workforce.application.OrgSnapshotSyncIntegrationTest` | 10 | 省略 | 省略 | 异步共享协作者（未证实）；本摘录只见失败类和方法；QUAL-011 仅提供同类解释，无法判定该次根因 |
| [116485437428](../tia-data/failures/116485437428.txt) / #199 | `platform.numbering.DocumentTypeCodeTest` | 4 | 省略 | 选中 | 文档读取/词表；everyCodeIsAGlossaryWord；源代码读 Glossary，非 Java import 图输入 |
| [116458951606](../tia-data/failures/116458951606.txt) / #196 | `platform.numbering.DocumentTypeCodeTest` | 4 | 省略 | 选中 | 文档读取/词表；everyCodeIsAGlossaryWord；源代码读 Glossary，非 Java import 图输入 |
| [116391087553](../tia-data/failures/116391087553.txt) / #177 | `platform.approval.StartApprovalOnSubmissionIT` | 4 | 省略 | 省略 | 事件/异步事务；场景类与 QUAL-009；确切 PR 因果未验证 |
| [116311751317](../tia-data/failures/116311751317.txt) / #148 | `organization.attendance.WorkCalendarIT` | 7 | 省略 | 省略 | 测试基础设施/连接；日志存在容器或数据库连接错误；不据此证明由 PR 引发 |
| [115820856012](../tia-data/failures/115820856012.txt) / #22 | `organization.workforce.application.OrgSnapshotSyncIntegrationTest` | 10 | 省略 | 省略 | 异步共享协作者（未证实）；本摘录只见失败类和方法；QUAL-011 仅提供同类解释，无法判定该次根因 |
| [116469318918](../tia-data/failures/116469318918.txt) / #199 | `platform.numbering.DocumentTypeCodeTest` | 4 | 省略 | 选中 | 文档读取/词表；everyCodeIsAGlossaryWord；源代码读 Glossary，非 Java import 图输入 |

归因汇总：

| 省略原因 | 类记录数 | 可证程度 |
| --- | --- | --- |
| 测试基础设施/连接 | 4 | 日志支持连接/容器症状，PR因果未证 |
| 真实时钟/既存测试漂移 | 4 | 已登记模式/类与方法线索；该次根因未独立复现 |
| 异步共享协作者（未证实） | 4 | 已登记模式/类与方法线索；该次根因未独立复现 |
| 文档读取/词表 | 6 | 直接源码输入与失败方法支持 |
| 事件/异步事务 | 1 | 已登记模式/类与方法线索；该次根因未独立复现 |

可直接核实的反例是 `DocumentTypeCodeTest`：源文件 `:21–22,47–62` 读 `docs/.../terms.yaml` 与 `glossary/proposed/platform-numbering.yaml`，它位于 platform.numbering，词表 PR 不改该 Java 模块；R 会漏掉 6 条历史失败。现有 ReaderIndex/light 守护覆盖这类读取，不能被更粗的 TIA 替代。

`OrgSnapshotSyncIntegrationTest` 的 4 个被省略样本只给出失败类/方法、没有 TooManyActualInvocations 正文；故本报告仅以 QUAL-011 提供“异步共享协作者”的候选解释，2 个早期样本的方法还是 recoveredSourceClosesTheHold，不能断言与后两个 releaseReasonLosesControlCharacters 同根因。`ProcessArchiveJobIT` 四条匹配 QUAL-010 已登记真实日期漂移，但没有重现当时源码与时钟。`StartApprovalOnSubmissionIT` 只见 expected RUNNING，是事件/提交等待问题的候选，不能用 QUAL-009 的 Timesheet 类直接替它定案。

**其他隐性耦合边界**：

- 事件/outbox、经 kernel 端口调用/实现：源码图有显式边的会选消费者；端口实现、事件顺序和共享登记表的运行耦合仍可能漏。本批没有足够失败时源码证明额外的确定漏例。
- 共享表/权限码：SourceReference*、ManagementEndpointIT、ErrorContractIT 等失败在本模型被选中（往往因资源全跑）；不代表静态包图看懂 SQL 表名、权限串或 KNOWN_SERVER_FAULTS。`defect-bank/one-1a-next-4.md` D3/D4 已记录“已知故障清单过期”“共享夹具缺列”问题，须继续保留全局/夹具回退。
- 配置/资源：R/S 全跑，可捕获的原因是保守退化，不是证明这些耦合有图边。
- 守护：根 TestContainerGuardTest / PrimaryKeyTypeGuardTest / GlossaryTest 都保留；SeeyonSourceScanTest 虽在 BC 内，S 通过轻测试常跑保护。未来缩小根守护会改变本结论。

### 5.3 全部后端失败 job 逐项结果

“无法判断”保留基线/日志边界；没有用空失败类列表当作零漏检。报告守护和其他工作流检查必须在 JUnit 选测之外执行。

| job 摘录 | run | PR/事件 | 检查类别 | 逐类判定或失败步骤 |
| --- | --- | --- | --- | --- |
| [116882415872](../tia-data/failures/116882415872.txt) | 38873340375 | #333 | workflow_check | Acceptance tests are changed only by the test engineer or under a ruling (SESSION_PLAYBOOK §6.1) |
| [117055954450](../tia-data/failures/117055954450.txt) | 38925523855 | #404 | junit_class | organization.attendance.WorkCalendarIT：R省略；platform.idempotency.IdempotentResponsesConstraintIT：R省略 |
| [116617231428](../tia-data/failures/116617231428.txt) | 38791490769 | push / main | junit_class | acceptance.platform.approval.ApprovalAccountsIT：无法判断（push无diff） |
| [117078749963](../tia-data/failures/117078749963.txt) | 38932209131 | #401 | junit_class | platform.approval.infrastructure.flowable.ProcessArchiveJobIT：R省略 |
| [116809302717](../tia-data/failures/116809302717.txt) | 38850088409 | push / main | unknown_test_or_infrastructure | surefire and failsafe, side by side |
| [116819257677](../tia-data/failures/116819257677.txt) | 38853352700 | #293 | junit_class | acceptance.platform.numbering.NumberingRuleConfirmerIT：R选中 |
| [116938172052](../tia-data/failures/116938172052.txt) | 38890388041 | #341 | unknown_test_or_infrastructure | surefire and failsafe, side by side |
| [117314372236](../tia-data/failures/117314372236.txt) | 39004596987 | #447 | workflow_check | System actor catalogue is append-only (backend/architecture/system-actors.txt) |
| [116685365906](../tia-data/failures/116685365906.txt) | 38811435140 | push / main | junit_class | acceptance.platform.integration.A8SourceFlagsIT：无法判断（push无diff）；acceptance.platform.integration.ReadOnlyAccountCheckIT：无法判断（push无diff）；acceptance.platform.integration.ReadOnlyVerificationCommandIT：无法判断（push无diff）；acceptance.platform.integration.StalledExternalSourceIT：无法判断（push无diff） |
| [116665111654](../tia-data/failures/116665111654.txt) | 38805349816 | push / main | junit_class | cost.timesheet.application.TimesheetApprovalScenarioIT：无法判断（push无diff） |
| [117257792976](../tia-data/failures/117257792976.txt) | 38986093998 | #441 | workflow_check | backend or frontend inputs changed? |
| [116809288938](../tia-data/failures/116809288938.txt) | 38850129740 | #289 | unknown_test_or_infrastructure | surefire and failsafe, side by side |
| [117101419855](../tia-data/failures/117101419855.txt) | 38938942883 | push / main | junit_class | platform.audit.AuditDomainEventsIT：无法判断（push无diff） |
| [116957050483](../tia-data/failures/116957050483.txt) | 38896171516 | #358 | junit_class | organization.workforce.application.OrgSnapshotSyncIntegrationTest：R省略 |
| [117135720800](../tia-data/failures/117135720800.txt) | 38949119944 | #415 | report_guard | every acceptance test class produced a surefire or failsafe report |
| [116924131253](../tia-data/failures/116924131253.txt) | 38886111471 | #342 | junit_class | organization.workforce.application.OrgSnapshotSyncIntegrationTest：R省略 |
| [117117017680](../tia-data/failures/117117017680.txt) | 38943494140 | push / main | junit_class | acceptance.platform.identity.UuidEmployeeIdsPremiseIT：无法判断（push无diff） |
| [117256336741](../tia-data/failures/117256336741.txt) | 38985635687 | #441 | workflow_check | backend or frontend inputs changed? |
| [116540318924](../tia-data/failures/116540318924.txt) | 38769213992 | #212 | junit_class | platform.numbering.DocumentTypeCodeTest：R省略 |
| [117289522624](../tia-data/failures/117289522624.txt) | 38996305297 | #412 | workflow_check | Acceptance tests are changed only by the test engineer or under a ruling (SESSION_PLAYBOOK §6.1) |
| [116937333340](../tia-data/failures/116937333340.txt) | 38890044165 | #345 | unknown_test_or_infrastructure | surefire and failsafe, side by side |
| [116695785312](../tia-data/failures/116695785312.txt) | 38814540718 | #271 | junit_class | platform.identity.AdministratorModeIntegrationTest：R省略 |
| [116701131955](../tia-data/failures/116701131955.txt) | 38814540718 | #271 | junit_class | acceptance.platform.integration.A8SourceFlagsIT：R选中；acceptance.platform.integration.ReadOnlyAccountCheckIT：R选中；acceptance.platform.integration.ReadOnlyVerificationCommandIT：R选中；acceptance.platform.integration.StalledExternalSourceIT：R选中 |
| [117204747283](../tia-data/failures/117204747283.txt) | 38969692169 | #438 | junit_class | acceptance.platform.identity.SingleOperatorModeStartupIT：R选中 |
| [116686606669](../tia-data/failures/116686606669.txt) | 38811806199 | #252 | junit_class | acceptance.platform.integration.A8SourceFlagsIT：R选中；acceptance.platform.integration.ReadOnlyAccountCheckIT：R选中；acceptance.platform.integration.ReadOnlyVerificationCommandIT：R选中；acceptance.platform.integration.StalledExternalSourceIT：R选中 |
| [116928004086](../tia-data/failures/116928004086.txt) | 38887285994 | #341 | junit_class | organization.workforce.application.OrgSnapshotSyncIntegrationTest：R选中 |
| [116911865518](../tia-data/failures/116911865518.txt) | 38882357383 | #335 | junit_class | PrimaryKeyTypeGuardTest：R选中 |
| [116677165667](../tia-data/failures/116677165667.txt) | 38808870533 | #262 | junit_class | acceptance.platform.integration.A8SourceFlagsIT：R选中；acceptance.platform.integration.ReadOnlyAccountCheckIT：R选中；acceptance.platform.integration.ReadOnlyVerificationCommandIT：R选中；acceptance.platform.integration.StalledExternalSourceIT：R选中 |
| [116999026973](../tia-data/failures/116999026973.txt) | 38908783247 | #340 | unknown_test_or_infrastructure | surefire and failsafe, side by side |
| [116751306789](../tia-data/failures/116751306789.txt) | 38831491348 | #277 | junit_class | platform.integration.guard.SeeyonSourceScanTest：R选中 |
| [116754995679](../tia-data/failures/116754995679.txt) | 38831491348 | #277 | junit_class | platform.integration.guard.SeeyonSourceScanTest：R选中 |
| [116743611755](../tia-data/failures/116743611755.txt) | 38829154795 | #277 | junit_class | platform.integration.guard.SeeyonSourceScanTest：R选中 |
| [116555419386](../tia-data/failures/116555419386.txt) | 38773589847 | push / main | junit_class | acceptance.platform.numbering.NumberingRuleConfirmerIT：无法判断（push无diff） |
| [117078364809](../tia-data/failures/117078364809.txt) | 38932094029 | push / main | junit_class | platform.approval.infrastructure.flowable.ProcessArchiveJobIT：无法判断（push无diff） |
| [117061170669](../tia-data/failures/117061170669.txt) | 38927052609 | #392 | unknown_test_or_infrastructure | surefire and failsafe, side by side |
| [117070715128](../tia-data/failures/117070715128.txt) | 38927052609 | #392 | junit_class | platform.approval.infrastructure.flowable.ProcessArchiveJobIT：R省略 |
| [116550070783](../tia-data/failures/116550070783.txt) | 38772041094 | #210 | junit_class | acceptance.platform.approval.ApprovalAccountsIT：R选中 |
| [116855691754](../tia-data/failures/116855691754.txt) | 38865012632 | #318 | junit_class | GlossaryTest：R选中；acceptance.platform.integration.NegativeSourceIdIT：R选中 |
| [116940875885](../tia-data/failures/116940875885.txt) | 38891206236 | #341 | junit_class | acceptance.platform.audit.AuditArchiveOrderIT：R选中 |
| [117288443525](../tia-data/failures/117288443525.txt) | 38926719279 | #405 | junit_class | platform.approval.infrastructure.flowable.ProcessArchiveJobIT：R选中 |
| [117168860672](../tia-data/failures/117168860672.txt) | 38958958079 | #435 | workflow_check | Acceptance tests are changed only by the test engineer or under a ruling (SESSION_PLAYBOOK §6.1) |
| [116728500468](../tia-data/failures/116728500468.txt) | 38824452453 | #276 | junit_class | platform.web.ErrorContractIT：R选中 |
| [116732063295](../tia-data/failures/116732063295.txt) | 38824452453 | #276 | junit_class | platform.web.ErrorContractIT：R选中 |
| [117071772424](../tia-data/failures/117071772424.txt) | 38930153579 | #374 | junit_class | platform.approval.infrastructure.flowable.ProcessArchiveJobIT：R省略 |
| [116691927596](../tia-data/failures/116691927596.txt) | 38813388161 | #267 | junit_class | acceptance.platform.integration.A8SourceFlagsIT：R选中；acceptance.platform.integration.ReadOnlyAccountCheckIT：R选中；acceptance.platform.integration.ReadOnlyVerificationCommandIT：R选中；acceptance.platform.integration.StalledExternalSourceIT：R选中 |
| [117180098192](../tia-data/failures/117180098192.txt) | 38962295103 | #406 | junit_class | platform.approval.infrastructure.flowable.ProcessArchiveJobIT：R省略 |
| [117216210046](../tia-data/failures/117216210046.txt) | 38973182830 | #440 | junit_class | acceptance.platform.integration.A8SourceFlagsIT：R选中 |
| [117002405614](../tia-data/failures/117002405614.txt) | 38909783025 | #392 | unknown_test_or_infrastructure | surefire and failsafe, side by side |
| [116728891061](../tia-data/failures/116728891061.txt) | 38824573978 | #277 | junit_class | platform.integration.guard.SeeyonSourceScanTest：R选中 |
| [117061027419](../tia-data/failures/117061027419.txt) | 38926958691 | push / main | unknown_test_or_infrastructure | surefire and failsafe, side by side |
| [116555565794](../tia-data/failures/116555565794.txt) | 38773631615 | #212 | junit_class | platform.numbering.DocumentTypeCodeTest：R省略；scenario.CorrelationIT：R选中 |
| [116477000597](../tia-data/failures/116477000597.txt) | 38750982228 | #199 | junit_class | platform.numbering.DocumentTypeCodeTest：R省略 |
| [116497337748](../tia-data/failures/116497337748.txt) | 38756825208 | #197 | junit_class | TestContainerGuardTest：R选中 |
| [116207266469](../tia-data/failures/116207266469.txt) | 38668957051 | #139 | junit_class | acceptance.platform.integration.SourceReferenceConstraintIT：R选中；acceptance.platform.integration.SourceReferenceQueryIT：R选中 |
| [115815505211](../tia-data/failures/115815505211.txt) | 38553088564 | push / main | junit_class | organization.workforce.application.OrgSnapshotSyncIntegrationTest：无法判断（push无diff） |
| [115814081024](../tia-data/failures/115814081024.txt) | 38552651044 | #20 | junit_class | organization.workforce.application.OrgSnapshotSyncIntegrationTest：R省略 |
| [116485437428](../tia-data/failures/116485437428.txt) | 38753411273 | #199 | junit_class | platform.numbering.DocumentTypeCodeTest：R省略 |
| [116444478932](../tia-data/failures/116444478932.txt) | 38741613950 | #190 | junit_class | TestContainerGuardTest：R选中 |
| [115967431341](../tia-data/failures/115967431341.txt) | 38597365240 | #59 | workflow_check | backend or frontend inputs changed? |
| [116458951606](../tia-data/failures/116458951606.txt) | 38745786764 | #196 | junit_class | platform.numbering.DocumentTypeCodeTest：R省略 |
| [116391087553](../tia-data/failures/116391087553.txt) | 38725760285 | #177 | junit_class | platform.approval.StartApprovalOnSubmissionIT：R省略 |
| [116311751317](../tia-data/failures/116311751317.txt) | 38701871475 | #148 | junit_class | organization.attendance.WorkCalendarIT：R省略 |
| [115820856012](../tia-data/failures/115820856012.txt) | 38554609605 | #22 | junit_class | organization.workforce.application.OrgSnapshotSyncIntegrationTest：R省略 |
| [116187484063](../tia-data/failures/116187484063.txt) | 38662713442 | #134 | junit_class | acceptance.platform.web.ManagementEndpointIT：R选中 |
| [116111349477](../tia-data/failures/116111349477.txt) | 38639571128 | #72 | workflow_check | backend or frontend inputs changed? |
| [116112142902](../tia-data/failures/116112142902.txt) | 38639809485 | #72 | junit_class | PackagedJarIT：R选中 |
| [116278305577](../tia-data/failures/116278305577.txt) | 38691533843 | #139 | junit_class | acceptance.platform.integration.SourceReferenceConfirmationIT：R选中；acceptance.platform.integration.SourceReferenceConstraintIT：R选中 |
| [115813264029](../tia-data/failures/115813264029.txt) | 38552405106 | push / main | junit_class | organization.workforce.application.OrgSnapshotSyncIntegrationTest：无法判断（push无diff） |
| [116368714339](../tia-data/failures/116368714339.txt) | 38719031551 | #163 | junit_class | acceptance.platform.integration.ReadOnlyAccountCheckIT：R选中 |
| [116469318918](../tia-data/failures/116469318918.txt) | 38748723144 | #199 | junit_class | platform.numbering.DocumentTypeCodeTest：R省略 |

### 5.4 非后端 33 个失败单列

19 个 frontend、9 个名为 secret scan 的 job、1 个 assertion drift、4 个 release。**没有一条能从名称直接归成“gitleaks 扫到密钥”**：9 个 secret scan 中失败步骤为生成文档4、规则修订追加守护2、命名守护1、数据库脚本对齐1、checkout/DNS1；assertion drift 的1条也失败在 checkout/DNS。前端 19 条的步骤是 pnpm verify，部分片段有用语/脚本断言，其他只有 exit 1，不能推定全部是同一根因。release 的2条是 push CI 尚未完成被发布门禁拒绝，2条构建 bundle 失败但摘录不足以定位内部原因。

当前 workflow 的实际条件：gitleaks 本身、生成文档、命名、规则修订等在 CI 事件上独立运行；assertion drift 只 PR；前端 pnpm verify **不是每次都全跑**，只在 code+heavy（queued 或 schedule），guarded 运行相应词表脚本，docs 跳重；release 只在发布工作流执行。保持这些现有触发职责，Java TIA 不应进一步过滤前端或非 JUnit 检查。

| job 摘录 | 工作 | PR/事件 | 失败步骤 |
| --- | --- | --- | --- |
| [116974681716](../tia-data/failures/116974681716.txt) | secret scan | #365 | no legacy system name in docs |
| [116699286854](../tia-data/failures/116699286854.txt) | frontend verify | #272 | Run pnpm verify |
| [116714163936](../tia-data/failures/116714163936.txt) | frontend verify | #272 | Run pnpm verify |
| [116715279152](../tia-data/failures/116715279152.txt) | frontend verify | #272 | Run pnpm verify |
| [117318632167](../tia-data/failures/117318632167.txt) | secret scan | #449 | database scripts aligned |
| [117229456274](../tia-data/failures/117229456274.txt) | assertion drift | #441 | Run actions/checkout@11bd71901bbe5b1630ceea73d27597364c9af683 |
| [117229456440](../tia-data/failures/117229456440.txt) | secret scan | #441 | Run actions/checkout@11bd71901bbe5b1630ceea73d27597364c9af683 |
| [116957050200](../tia-data/failures/116957050200.txt) | frontend verify | #358 | Run pnpm verify |
| [116289179745](../tia-data/failures/116289179745.txt) | release bundle | workflow_dispatch | build bundle with fresh HOME, Maven and pnpm caches |
| [116287585665](../tia-data/failures/116287585665.txt) | release bundle | workflow_dispatch | commit is a first-parent commit of main and its push ci run succeeded |
| [116695785413](../tia-data/failures/116695785413.txt) | frontend verify | #271 | Run pnpm verify |
| [116701131439](../tia-data/failures/116701131439.txt) | frontend verify | #271 | Run pnpm verify |
| [116845051236](../tia-data/failures/116845051236.txt) | secret scan | #296 | generated docs match fragments |
| [116288270501](../tia-data/failures/116288270501.txt) | release bundle | workflow_dispatch | build bundle with fresh HOME, Maven and pnpm caches |
| [116977165508](../tia-data/failures/116977165508.txt) | frontend verify | #340 | Run pnpm verify |
| [117060228320](../tia-data/failures/117060228320.txt) | secret scan | #406 | rule review revisions are append-only |
| [116839538213](../tia-data/failures/116839538213.txt) | secret scan | #296 | generated docs match fragments |
| [117288443535](../tia-data/failures/117288443535.txt) | secret scan | #405 | rule review revisions are append-only |
| [116691928272](../tia-data/failures/116691928272.txt) | frontend verify | #267 | Run pnpm verify |
| [117320192176](../tia-data/failures/117320192176.txt) | release bundle | workflow_dispatch | commit is a first-parent commit of main and its push ci run succeeded |
| [116719886218](../tia-data/failures/116719886218.txt) | frontend verify | #275 | Run pnpm verify |
| [116724017541](../tia-data/failures/116724017541.txt) | frontend verify | #275 | Run pnpm verify |
| [116346997239](../tia-data/failures/116346997239.txt) | frontend verify | #145 | Run pnpm verify |
| [116350487949](../tia-data/failures/116350487949.txt) | frontend verify | #145 | Run pnpm verify |
| [116278996327](../tia-data/failures/116278996327.txt) | frontend verify | #129 | Run pnpm verify |
| [116318407521](../tia-data/failures/116318407521.txt) | frontend verify | #145 | Run pnpm verify |
| [116331375227](../tia-data/failures/116331375227.txt) | frontend verify | #145 | Run pnpm verify |
| [116289145396](../tia-data/failures/116289145396.txt) | frontend verify | #145 | Run pnpm verify |
| [116284640044](../tia-data/failures/116284640044.txt) | frontend verify | #145 | Run pnpm verify |
| [116331917663](../tia-data/failures/116331917663.txt) | frontend verify | #145 | Run pnpm verify |
| [116170588190](../tia-data/failures/116170588190.txt) | secret scan | #119 | generated docs match fragments |
| [116342760425](../tia-data/failures/116342760425.txt) | frontend verify | #145 | Run pnpm verify |
| [115990156252](../tia-data/failures/115990156252.txt) | secret scan | #61 | generated docs match fragments |

## 6 方案与试验设计

### 6.1 哪些每次必跑，放在哪个入口

这里的“每次”指适用事件上的检查职责保持完整，不把现有 docs/guarded 再升成全量，也不把前端全构建塞进每次小反馈。没有失败样本的守护仍可能是必要不变量，不能用“没失败过”删除它。

| 入口 | 保留职责 | 选测位置 |
| --- | --- | --- |
| 任意 PR 事件的轻检查 | 密钥扫描、生成/用语/规则修订守护、Flyway不可变/单调与system actor追加守护、验收修改/裁定守护、读取登记自测、数据库脚本对齐；PR assertion drift及脚本自测。失败回放已有9个后端流程/报告失败和secret job多种守护失败支持其独立价值 | 继续现有三档；TIA影子日志可附在规划步骤，不执行额外Maven/容器 |
| PR 的 docs/guarded 档 | docs保持轻检查；guarded精确跑读取方及其前端/release脚本；code标签未入队仍不能伪装重门禁通过 | 不叠加新的模块选择器；已经省下的工作不重复报功 |
| PR code 轻量反馈 | 全部免容器守护优先；可在另一个显式实验入口展示局部模块结果，标清它是反馈检查；不增加同机runner压力 | 先仅影子；若未来启用选择执行，独立命名 light/experimental，不能满足队列 required full check |
| 合并队列重档 | 继续全量默认397、完整Modulith/ArchUnit/迁移与契约守护、OpenAPI导出/差异、全验收报告检查、变更测试三遍重跑，legacy路径命中再跑额外profile，前端按现有heavy完整verify | 本次不替换门禁；影子清单与同树实际全集对照 |
| main push | 维持所有免容器单测与非JUnit守护报警；不因轻量绿色更新full基线 | 不再缩小 |
| 夜间 ci 与 legacy-it | 明确关闭TIA；默认397与profile额外测试齐全，报告核对实际执行，不接受缺片/旧产物；结合QUAL复发归类 | 作为全量兜底/认证基线；不能替代尚未验证的合入前保护 |

S 中始终保留所有轻类与验收/场景，使当前 acceptance_reports.py 无须放宽；未来若要过滤任何验收，应另卡设计“计划清单 = 真实执行类 = 报告类”，全量模式仍核对全集。**该脚本目前只检查 XML 文件存在，并不检查 skipped 数**；扩展过滤若生成全 skipped XML，现守护可能误读为跑过。必须检测 executed/skipped/原因，并防旧报告复用，不能只改预期数量或删除断言。

### 6.2 main 红灯与暂停

静态已核实 `mq.py:263–278,356–385`：读取 `ci` 工作流 main push 与 schedule 最新已结束、未取消的 verdict；红则两道 `PAUSED`，写 `<queue dir>/mq.paused`；`resume <run id>...` 必须精确认领红运行，旧认领不屏蔽新红；读不到 API 时 `WAIT-SIGNAL`。取单前与合并前都检查，合并锁与固定 head 仍保持。没有读回正在运行的守护进程，不宣称部署已验证。

**单独 nightly-legacy-it 不在这条 Pause 查询里**（源码 `--workflow ci`）。若方案 H 把四个 SQL Server 验收移到该工作流，必须把它的红灯接入暂停（包括相应 dispatch/nightly），或把必需结果汇入被监听的 ci schedule；不能只说“每夜会发现，所以队列安全”。本卡仅列后续改动，不修改 mq。

main 红后保持暂停，调度核查故障、恢复并进行明确认领；不允许 TIA 的低覆盖绿色自动替红灯解锁。夜间是最长约一天的晚发现，不等价合入前拦截；当前“main只经队列”约束必须与可靠的全量/选择执行证据同时成立。

### 6.3 影子试验：建议 N=14 天，样本不足继续积累

1. **不改变门禁、事件、容器数量**：在现有全量入口只记录 R/S/O 的会选清单和回退原因。要测 native 的真实判断先取得依赖原件并走供应链/守护评审；可用不接管当前 JUnit 的独立探测器输出计划，不让它影响全量执行。
2. **同树配对**：保存 PR号、head/base/check-out SHA和tree、基线全量认证来源、diff全部路径（含rename前后）、规则版本/图hash、所有选择/排除/原因、三档和legacy_it判断。基线缺失、源码变化、图构建错误直接标全跑，不输出假想空集。
3. **全量作 oracle**：从原有同一轮全量 XML 和失败摘要取每类 executed/skipped/failure/error/time；显示名映射回FQCN，区别“未产生报告”“被TIA跳过”“已有显式Disabled”。对每个真实失败类看计划是否包含，保存已选/漏选/无法判断，不把后者归为通过。
4. **分层评价**：分别统计全部PR、code queued、叶模块/集成/内核/资源/共享fixture/词表输入；计时分运行阶段、队列、重跑和取消。建议至少完整14天、100个完成的code全量树、30个真正可减测样本；这些是实验的最低采样设计，不是安全证明或自动上线条件。
5. **查漏因果**：每条漏选读失败时源码、完整日志和后续修复；分新增回归、既存偶发/时间漂移、基础设施、非JUnit检查、证据不足。只要有新增回归漏选或缺证影响守护，继续修选择模型并保持原门禁。
6. **受控负向与干净对照**：未来获准实验时，在隔离试验分支注入漏依赖方、kernel端口实现、事件/outbox、共享fixture、权限字符串、资源/额外复制清单、docs读取、增删改名、空diff/缺base、skipped XML/旧报告等；必须被全量oracle发现，并验证正确选择计划没有省略它们。撤销注入后跑干净控制。不能以mock脚本通过替代真实JUnit/工作流验证。
7. **结束评审**：输出同树完整结果、所有漏选与未知、实际省秒和runner负载；由负责会话+opus决定是否只推广开发反馈，或另行向用户申请改变队列门禁。14天到期本身不意味着通过。

### 6.4 最小改动清单（后续卡，未实施）

| 阶段/文件 | 最小用途 | 守护/复审 |
| --- | --- | --- |
| 影子：新 scripts/ci/test_impact_plan.py（候选名） | 从git根生成计划/manifest/原因和离线自测；复用当前三档读取索引，不复制一份分档规则 | CI选择属于门禁守护面；opus首审 |
| 影子：.github/workflows/ci.yml | 在已有全量轮次上传计划和oracle比较；不改heavy gate、必需名称、原测试清单或结果 | 工作流守护文件；opus首审 |
| 扩展接入：backend/pom.xml | 新增test依赖+显式profile；Surefire/Failsafe可控system properties；全量关闭；独立export不被过滤 | 构建/测试入口守护面；opus首审+供应链核对 |
| 执行实验：scripts/ci/backend_verify.sh、guarded_tests.sh（若需要） | 分别传两类完整FQCN清单，保留始终跑的导出/守护；保持watchdog与失败摘要；无过滤全量模式 | CI守护面；opus首审 |
| 报告：scripts/ci/acceptance_reports.py + 新影子报告核验 | 增加计划/实际executed/skipped/旧产物核对；全量仍校验所有验收，保持原断言 | 验收守护；opus首审，不动受保护验收目录 |
| 路径复用：scripts/ci/relevant_change.py、light_unit_classes.py（确需时） | 只加共享输入/读取覆盖与保守回退；保留LEGACY_IT_PATHS和重docs读取方升code | 守护文件；opus首审 |
| 夜间/暂停（若移动覆盖）：nightly-legacy-it.yml、scripts/merge/mq.py及selftest | 扩展全量报告和被监听红灯来源；existing ci schedule已有默认全量，不为TIA重新造一套暂停 | 合并/发布安全守护；opus首审 |
| 文档规则（若改变合入前全量原则） | CS-5.11 / SESSION_PLAYBOOK §6.1等的正式决定另走治理，不由研究草稿变更 | 负责会话/用户批准；本卡不追认 |

仅影子脚本可先不新增依赖；真正 native 行为验证阶段才需要 pom/JAR。守护范围参照 SESSION_PLAYBOOK §12 的“决定什么要跑/能否合入”和既有 CI 复盘，最终文件清单由负责会话复核。受保护 acceptance 测试目录本卡和实现卡都不直接修改。

### 6.5 节省区间与一分钟路线

就本次文件分布，R 的 code PR 均值约 95 秒、条件敏感性 51–184 秒，但回放和未知 native 行为不支持作为合入门禁承诺。保守 S 的真实决策预算应采用 **0–6.4 秒/code PR 平均**（模型点值 3.4 秒）；约95%的code PR退回全量，典型为0。O 的几百秒依赖移走重验收的未证假设，不能作为本卡可兑现收益。

对照 `CI_TIMING_REVIEW.md` §4、§6.2、§6.6–6.7：优先方案 H（按四类真实读取输入分档）以及减少重复启动、调度均衡和缩窄 release 自测范围，收益比保守 TIA 大；H 是选择范围变更、须保留全部相关路径/夜间报告与暂停信号。H 与 A 对同一慢类重叠，不能加算；H落地后必须重新采类耗时再算TIA，不能继续沿用本报告397类模型。

一分钟目标真正还受 release/deploy 自测 PR P50 160秒、轻量工作流P90 316秒和排队影响（§6.1）。本报告的选择不消除这些路径，也保留324秒启动矩阵验收。§6.7 的阶段0补基线、阶段1完整560–740秒P50、阶段2完整210–330秒P50、阶段3快速反馈P90≤60秒，是有前提的工程目标；本卡数据只补了阶段0缺少的PR路径分布和失败反事实，不能把阶段3的反馈语义改成一分钟合入。

## 7 数据缺口与不确定

### 7.1 最不确定的三处

1. **真实原生选择集合**：缺 spring-modulith-junit 原件与运行证据，test-only acceptance/scenario 归属、非Java变更被丢弃后的no-changes、backend工作目录范围、自动扩展发现/报告skipped行为均未实证。R/O都是条件模型；真实native若全跑，收益为0。
2. **失败时树与因果**：PRS是最终文件列表，failed_jobs没有head SHA/当时完整diff；只匹配分支+时间区间。只有几天且日志筛选过，同一PR多次失败、同一偶发跨分支传播。已知失败省略不等于新增回归漏检；未知不能归零。
3. **墙钟兑现与未来分布**：只有前30类的4次计时、其余用阶段均值；选少类后fork/上下文/模板效率和共享runner竞争会变化。8个可减测PR不能代表未来业务开发；方案H等已讨论变更会使基线过时，必须重算。

另有边界：本卡未审计GitHub PR文件列表API的分页/重命名前路径完整性，prs.json未给失败时patch；只使用给定文件集合。构建/generated docs是外部进程并发演进的快照；没有在线查询部署、队列进程、分支保护或runner真实状态。分母443是给定样本已合入PR数，不能把它称整个项目永远完整的历史。

### 7.2 已验证、未运行与执行偏差

**已验证**：前置规范、版本/pom与本机POM链、13模块策略与静态源图、397默认类/1 profile额外类、现有三档/队列/夜间门控、全部461个PR逐项模拟、103个失败job分类和FQCN/日志行对照；规则模拟器边界/负向方向对照、输入内容hash、结果分母一致性。验证日志见 [validation.log](validation.log)，24 PASS / 0 FAIL，仅证明研究脚本与输入的一致性。

**未运行**：真实Spring Modulith JUnit TIA、Maven构建/Java测试、ApplicationModules运行时图、pnpm前端、容器、真实影子CI、故障注入分支、基线认证持久化、队列/runner线上read-back。没有提交、推送、开PR或修改门禁，也没有安装网络依赖。

**执行偏差（不隐藏）**：为满足precheck而建立外部克隆后，前两次调用未先cd副本，原版precheck通过当前目录定位原worktree，第二次触发生成器；已跟踪文件内容无git差异，部分生成文档被重写/时间戳刷新。未保存忽略文件的事前字节快照，不能证明其事前内容完全相同。已向用户说明；此后检查均在副本内。它违反本卡“只读使用”的操作边界，不能在结论中声称整个过程绝对零写入。

系统Python首次缺PyYAML；使用本机已有缓存拷贝PyYAML 6.0.3到tia-out内临时venv后，Python3.9在生成脚本报Non-UTF-8，而只读compile同字节可通过，原因未查明；没有改原脚本“修”它。最终改用本机已有Python3.12解释器+同一离线缓存，于独立检出的 `46fa7ccf` 副本成功运行原版 `scripts/precheck.sh HEAD`。研究过程中原worktree被外部更新到下述最终基线，差异仅文档；本研究无仓库提交，保留来源与复算hash，不把该外部更新归成自己操作。precheck的HEAD基线没有仓库代码差异，因此不替本研究方案证明业务测试通过。

最终复算源码/文档 HEAD：`67db111d612bcd54209b7a5b58a0d2e5181df9a4`；入场 `46fa7ccff78cff1b40017b15b24fdaa956c9624e`。外部新增§6一分钟路线已补读；生产源码/pom/CI未变化。

**最终 precheck 输出（退出码0，SKIP理由逐行保留）**：

```text
precheck: base HEAD (46fa7ccf), HEAD 46fa7ccf, 0 changed files (0 uncommitted)

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
PASS  gitleaks (full history)                2754 commits scanned; no leaks
SKIP  backend tests                          relevant_change.py: nothing the builds read changed
SKIP  frontend verify                        relevant_change.py: nothing the builds read changed
SKIP  SQL Server integration tests           relevant_change.py: nothing the *MssqlIT tests exercise changed

SUMMARY: 3 PASS, 0 FAIL, 12 SKIP (base HEAD) — ready
```

### 7.3 产物与复算

- [select.py](select.py)：文件列表→模块/测试类/全跑原因。`native` 是近似规则模型，`safe` 是仓库安全策略，`owner` 仅敏感性；默认入口无网络/容器/构建。
- [reproduce.py](reproduce.py)：读取给定PRS、失败摘录、源码和计时前30，生成以下CSV/JSON；[write_report.py](write_report.py) 从复算结果生成本草稿。
- [pr_simulation.csv](pr_simulation.csv) / [JSON](pr_simulation.json)：全部461项（含443 MERGED），含additions/deletions；JSON含全部FQCN和reason_paths。
- [failure_replay.csv](failure_replay.csv)、[failure_class_pairs.csv](failure_class_pairs.csv)及同名JSON：103个job和逐失败类对照、漏选原因、日志原始行号。
- [test_inventory.csv](test_inventory.csv)、[module_graph.csv](module_graph.csv)及JSON：类与helper排除、phase、轻重、模块、每类估算时间；图JSON保存逐源码引用。
- [summary.json](summary.json)、[extra_summary.json](extra_summary.json)、[input_manifest.json](input_manifest.json)：统计分母、阶段均值/分布和输入SHA256。[validation.log](validation.log)、[precheck-final.log](precheck-final.log)保留验证证据；早期失败日志保留供核查操作偏差。

```bash
# 从任意目录执行；脚本只在本tia-out内写研究产物。
python3 -I /绝对路径/tia-out/reproduce.py --repo /固定提交的只读检出
python3 -I /绝对路径/tia-out/validate.py --repo /固定提交的只读检出
python3 -I /绝对路径/tia-out/write_report.py

# 单个PR：输入为路径数组、files数组或含files的PR对象
python3 -I /绝对路径/tia-out/select.py   --repo /固定提交的只读检出   --files-json /绝对路径/tia-out/example-input.json --policy native
# --policy safe 加保守回退；--acceptance owner 仅用于敏感性分析。
```

默认数据路径为tia-out同级的tia-data，计时路径为同级ci-data/out/CI_TIMING_DATA.md；可用reproduce.py的--data-dir覆盖PRS/失败数据路径。默认repo为同级wt-docs；若其HEAD已前进，应先在仓库外准备本报告HEAD的固定检出并传--repo，禁止为复算改动原worktree。原输入未修改；未来数据或代码变更会改变结果，须保留新的manifest，不能覆盖本次证据后仍引用旧数字。

