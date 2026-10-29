# ADR-0050 / ADR-0051 提议稿独立对抗评审

状态：历史记录｜2026-10-16｜负责：one-architecture

- 评审对象：`origin/claude/one-architecture-adr-0050-0051`（`41830f9d`）下 `docs/architecture/proposals/ADR_DRAFT_0050_METHOD_AND_FRAMEWORKS.md`、`ADR_DRAFT_0051_ENTERPRISE_BC_NAMESPACES.md`
- 评审人：独立 opus 子代理，未参与起草；只读（用 `git archive` 导出到会话临时目录后运行脚本，未改仓库）
- 日期：2026-10-16
- 已运行：`aggregates_dep_check.py`（退出码 0：106 个聚合、141 条边、无环、L0–L14，与 ADR-0051 决定 7 一致）、`vs_links_gen.py --check`（通过）、`processes_check.py`（通过）；另把两份草稿放进 `adr/src/` 后调用 `build_adrs.py` 的 `parse`（**两份都失败**，见 B-1）

## 结论

两份草稿的推导主体（BC 清单、141 条边、无环分层、证据规则、方向规则）与聚合稿、脚本输出、FIT_AUDIT 处理记录一致，没有发现数字或标识错误。问题主要有四类：

1. 成文格式过不了 ADR 生成器；
2. 取代清单漏了 ADR-0021 等条款；
3. 采纳当天 SSOT（`contexts.yaml`、`transitions.yaml`）就会与 ADR 矛盾；
4. 若干标成「用户」或「推出」的条目实际是起草人裁量。

ADR-0051 的过渡架构条款按现行「OA 分阶段迁入、新老并存」的阶段编号来写，会预先锁死用户刚提出、尚未定案的「自成体系」思路。按现稿采纳会出错，改完下列「采纳前必须改」各项后可以进入 sonnet 复核。

## 问题表

| 编号 | 级别 | 位置 | 问题 | 建议改法 |
|---|---|---|---|---|
| B-1 | Blocker | 两份 ADR 的结构与「取代」行 | `build_adrs.py` 只允许 `背景、决定、影响面、迁移方案、回滚方案、需同步修改的守护规则、后果、证据、备选方案（未采用）` 这几个二级标题，且必须按这个顺序。ADR-0050 多出「与 ADR-0041 的关系」「起草中发现的未定事项」「与 ADR-0051 的接口」，ADR-0051 多出后两节，实测 `parse` 都报「小节须取自……」。生成器还按「、」切分「取代」行，每段须以 `ADR-NNNN` 开头：ADR-0050 切出 11 段，其中 10 段不合格；ADR-0051 切出 12 段，其中 11 段不合格。采纳（状态改「已采纳」）后，`validate` 必报「取代对象不存在」。ADR-0049 的提交 `6719c407` 已经踩过同一个坑 | 「与 ADR-0041 的关系」并入「背景」或「决定」末尾；「未定事项」「接口」并入「后果」或「证据」，或采纳前清空。「取代」行改为只用「、」分隔、每段以 `ADR-NNNN` 开头的短引用，例如 `ADR-0041 决定 2、ADR-0041 决定 1（部分）、ADR-0041 决定 7（补充）`；逐处细目移到「决定」或「背景」。改完把两份放进 `adr/src/` 跑一次 `build_adrs.py`，再把被取代 ADR 的状态行也改好跑一次，确认通过 |
| B-2 | Blocker | ADR-0051 决定 1 第 2 条、影响面「SSOT 文件的更新时点」、决定 9 第 4 条、未定事项 1；ADR-0050 决定 3 | ADR-0051 写 `ddd/contexts.yaml`「只登记已建与本阶段在建的部分（ADR-0041 决定 4）」，与事实不符：现 `contexts.yaml` 登记了全部 29 个业务 BC（status planned），文件头还写「新 BC 先在此登记（status: planned）」。ADR-0041 决定 4 只删了 context-map 的规划边，没有管 BC 条目。采纳后，`contexts.yaml` 与 `transitions.yaml` 仍有 7 个被本 ADR 退役的标识（`presales.customer`、`presales.quoting`、`contract.baseline` 等），SSOT 与已采纳 ADR 当天就矛盾。决定 9 第 4 条要求改 `transitions.yaml` 的 `delivers`，但 `build_architecture.py` 要求 `delivers` 引用 `contexts.yaml` 里已有的标识；未定事项 1 不答，这一条执行不了。迁移方案第 2 条又写「答复前不改」，等于 ADR 采纳了、SSOT 不跟 | 采纳前由用户定未定事项 1（建议：7 个旧条目改 `retired` 并写去向；`build_architecture.py` 已接受 `retired`）。删去「只登记已建与本阶段在建」这一事实陈述，改成「现有 planned 条目按 X 处理」。ADR-0050 决定 3 补一句：L-5「不进 SSOT」不追溯删除既有的 planned BC 条目，或者明说要删 |
| H-1 | High | ADR-0051「取代」行 | 漏了 ADR-0021 决定 3「6f 客户经营与经营计划（视情况；**客商主档随 6b / 6c 先行**）」。它与决定 9 第 1 条「`masterdata.counterparty` 不晚于 TA-1b」字面冲突；i210513 是用户决定，但被修订的条款要列全。ADR-0021 只取代了「后果」中的两处 | 「取代」行加上 ADR-0021 决定 3 中的客商主档时点（如果按 H-2 把决定 9 改成与阶段编号无关，则改为「ADR-0021 决定 3 中客商主档的先后表述，按本 ADR 的依赖次序读」） |
| H-2 | High | ADR-0051 决定 9 全节；决定 4 第 7 条与 B-22、B-23、B-24 行的「G1」「ledger 内导入通道」；影响面「阶段」；迁移方案第 4 条；守护第 1、2 条的「TA-2」「TA-6a」 | 用户 intent `2026-10-16-122043`「投入生产时不从老系统接任何东西、自成体系」是初步思考、非定案，落点写明「施工方案由用户定」。下列条款预设了现行的「OA 分阶段迁入、老系统并存、按会计期间切换」路线：TA-1b、TA-6a、6b、6e、6f 等阶段名（这些都是 ADR-0021 的 OA 迁入阶段）；「保证金 6b 前先登记」；`accounting.tax`「自 G1 起」独立（G1 是 ADR-0022 中与 U8C 收付账套并行的阶段）；cashbank、fixedasset「G1 为 ledger 内导入通道」；在途单据「按 ADR-0021 各阶段的切换规则处理」。采纳后，如果用户选「自成体系」，就要另开 ADR 改这些条款。ADR-0050 基本中立：⑦ 的过渡架构、TOGAF 过渡架构和「确需复刻的老行为以生产数据为证据」都是通用写法，不锁定路线 | 决定 9 改成与阶段编号无关的依赖次序约束，例如：「任一 BC 不早于其全部 api 或事件上游交付；`receivables.collection` 的保证金聚合先于开票收款类功能；`accounting.tax`（AGG-89）不晚于第一个占用方」。`transitions.yaml` 的改动整体移到施工方案。决定 4 第 7 条改为「自其首个占用方上线起即为独立 BC」，B-22、B-24 删去「G1 为导入通道」括注（ADR-0022 本身不变，用不着复述）。迁移方案第 4 条只写「规划 BC 均未上线，无在途单据」。守护第 1、2 条的时点改为「`delivery.execution` 或 `delivery.acceptance` 的首个 PR」。未定事项 2、3、6 明确交施工方案 |
| H-3 | High | ADR-0050 决定 15；文末未定事项 1 | 决定 15 的依据标成「用户」，但三条判据在 `STEP6A_PROCESSES.md` §0.3 标【裁量】，⑥a 清单状态仍是「待用户确认」；P-5 只是按这个口径移了三项，不等于确认了判据。ADR-0050 自己的总表规定 ⑥a 由「用户确认」、在 ADR 之前完成（M-1，intent `211634`）。按 ADR 自己的方法，这是前置条件没满足，不是可以留着的未定事项 | 二选一：采纳前出确认页，让用户确认判据（必要时连同清单）；或把决定 15 的依据改为「裁量，待用户确认」，并在背景里删去「待用户决定：无」 |
| H-4 | High | ADR-0050 背景「待用户决定：无」与「标记」段；决定 9、13、16、21、22、23；ADR-0051 B-14、B-18 行、决定 4 第 5 条 | 「标记」段把一切「推出」都说成「用户已过目认可（L-11）」，但 L-11 的认可页只列了 10 项（不变量 ADR 优先、提议稿不进 SSOT、不拆子价值流……回溯检查）。没在其中的有：十二条接缝表（决定 16）、规则四分（决定 9）、「待独立复核」标记（决定 21，出自 METHOD 第 4 稿第 4 节）、校验覆盖 `proposals/`（决定 23）、命名空间 = 包首段 = schema 前缀（决定 13）、删除 `togaf/value-streams.yaml`（决定 22）。ADR-0051 B-14 把 AGG-108「标准工时版本」放进 `organization.attendance`，标依据 D-5，但 D-5 原文是「考勤只留工作日历」（METHOD 草目条目 21 同样如此），AGG-108 是起草会话在聚合稿「9 续五」加的。B-18 `operations.planning` 表里写「薄」，同时又列为未定事项 4 | 「推出」与「L-11 已认可」分开标：不在 L-11 清单里的写「推出（未经用户过目）」，交确认页一次性过目，或者如实写进背景。B-14 依据改为「D-5；AGG-108 为裁量」，或者请用户确认。B-18 深 / 薄一栏改为「待定（未定事项 4）」，不写「薄」 |
| H-5 | High | ADR-0050 决定 12 第 3 条、决定 16「一个事实只手写一处」、决定 23、守护第 2、4 条；ADR-0051 决定 7 第 1 条 | 依赖边的唯一来源有三种互相矛盾的说法：① ADR-0050 说「BC 依赖边由聚合表的引用列生成，不另手写」；② ADR-0003 决定 3 与 ADR-0041 决定 4（ADR-0050 声称「保留不动」）规定 `module-dependencies.txt` 手写、经 CODEOWNERS 评审，上下文映射以它为准；ADR-0051 影响面也这样写；③ ADR-0051 说「唯一手写来源」是聚合稿 Markdown §2.2，ADR-0050 的 PR-3 却改成 `proposals/aggregates.yaml`。SSOT 的 `ddd/aggregates.yaml` 现在没有引用列，ADR 也没规定要加。另外，业务层 SSOT 的校验（门不引用派生值）依赖 `proposals/` 下的派生值清单，等于让 SSOT 的正确性依附于「不进 SSOT」的提议稿；提议稿被 CI 卡住，也就成了事实上的 SSOT | 写清分工：规划边只在提议稿的聚合 YAML 中手写；已建边只在 `module-dependencies.txt` 中手写（CODEOWNERS）；CI 校验两者一致（已建边必须出现在聚合引用中）。删去「不另手写」。如果要让阶段登记后的聚合也带引用列，在决定 22、23 中写明给 `ddd/aggregates.yaml` 加哪些字段。派生值清单写明放在哪个机读字段，并说明提议稿归档后这项校验读什么 |
| H-6 | High | ADR-0051 决定 1 第 1 条、决定 3 表的「所含聚合」列 | 「复核结果与本 ADR 一致的直接登记，不一致的另开 ADR 取代」没有界定「一致」指什么。表中逐 BC 列了 106 个聚合编号，按字面任何聚合移动都要开 ADR。而未定事项 5 已列出 Q20、G-11、招聘多级审批、薪酬代发等可能改动聚合或边的题，可预见会频繁开 ADR，也与「快照只供对照、不是 SSOT」的自我定位矛盾 | 分开规范性与参考性内容：规范性的有 BC 标识与命名空间、BC 的合并与新增、协议类型归属、禁止边（守护第 1–3 条）、方向事实（决定 7 第 2 条）；参考性的有聚合归属列表、分层快照、边数。只有改动规范性内容才开 ADR，参考性内容在阶段复核中直接更新提议稿 |
| M-1 | Medium | ADR-0050「与 ADR-0041 的关系」决定 3 一行 | 声称 ADR-0041 决定 3「保留不动」，但它的原文是「规划项进入阶段前**按决定 2** 重新推导」，而决定 2 已被整条取代，指向悬空 | 在「取代」或背景中写明：ADR-0041 决定 3 中「按决定 2」读作「按 ADR-0050 决定 1 的七步」，作为部分取代登记 |
| M-2 | Medium | ADR-0050 决定 13、J11、守护第 6 条；ADR-0051 决定 2 第 1 条、B-29、`reporting` 行 | 「每个 BC 恰属一个命名空间」「BC = 模块 = schema」「命名空间 = 包首段 = schema 前缀」没有例外，但 `reporting`（`contexts.yaml` 中 subdomain 为 null、路径待定）、`kernel`（null；`bcs.yaml` 里却写 `subdomain: kernel`）、`delivery.closeout`（不建 schema）都违反。守护第 6 条照写会让 CI 失败 | 写出例外名单（kernel；reporting 在定路径前；无 schema 的流程模块），或者把 reporting 的命名空间一并定下来；J11 改为「有聚合的 BC = 模块 = schema」 |
| M-3 | Medium | ADR-0051 决定 4 第 4 条、决定 7 第 2 条第 5 点 | `delivery.closeout`「结项检查单经 `platform.audit` 留痕」，但依赖边只有 7 条，不含 `→ platform.audit`；脚本输出的 B-29 上游里也没有。调用 audit 的 api 须有边（参照 `organization.workforce → platform.audit`） | 补边并重跑（不会成环：audit 位于 L1），边数改为 8 条；或者改写留痕方式 |
| M-4 | Medium | ADR-0050 决定 12 第 2 条「不校验」 | 「只存对方的类型化 ID，不成边」：如果类型化 ID 定义在对方 BC 的 `api` 包，`ModuleDependencyTest` 会把它判为依赖违规。现有做法是把 ID 放进 `kernel`（`ProjectId`、`WbsNodeId`、`EmployeeId`、`DocumentReference`）。聚合稿中「不校验」引用有 13 条，涉及的 ID 若都进 kernel，共享内核会扩大，ADR 对此未置一词（CS-2.7） | 写明「不校验」引用的 ID 放哪里：kernel 中的通用 ID、`DocumentReference`，或本 BC 内的不透明 ID。如需扩大 kernel，列出清单 |
| M-5 | Medium | ADR-0050 决定 6、16、22，迁移方案 PR-2 | 一边说「能力图由信息图生成」「一个事实只手写一处」，一边用 `git mv capabilities.v2.yaml` 把一份手写文件（名称、定义、`primary_parent`、pending / excluded）放进 SSOT。文件头自己也说信息图缺「主父对象」列 | 改为「能力文件手写名称与定义，结构由 CI 校验与信息图一一对应」，或者真正改为生成（定义字段移进 `information-map.yaml`）；决定 22 给信息图字段加上主父对象 |
| M-6 | Medium | ADR-0051 影响面「词表」 | 只提到 `bcs.yaml` 和 `terms.yaml` 的 `customer` 条目。`terms.yaml` 还有 14 条 `bc:` 字段指向已退役的 BC（`contract.baseline`、`contract.changeorder`、`contract.riskdelivery`、`operations.budgeting`、`presales.customer`），`glossary/build_terms.py` 硬编码了这些标识（`KEY_BCS`），`NAMING_STANDARD.md`、`GLOSSARY.md` §1、`MODERNIZATION_BRIEF.md`、`BI_SCOPE.md`、`build_oa_inventory.py`、`docs/data/metric-baseline/baseline-draft.md`、`architecture/generated/*` 也都还在用 | 在影响面中列全，并写明归属（one-glossary 管 `terms.yaml` 和 `build_terms.py`，one-architecture 管其余）。「按此读作新标识」只适用于冻结的 ADR 正文，不适用于 SSOT 与生成器 |
| M-7 | Medium | ADR-0051「取代」行（ADR-0022） | 只取代了 ADR-0022 决定 7 的括注。决定 6 的「自建明细账 BC 顺序：应付与付款 → 银行与现金 → **税费** → 固定资产」与 D-7「tax 提前独立」冲突；决定 7 表中税费 BC 的说明「增值税（进项类型、销项）」与 ADR-0051 把销项发票（AGG-46）放进 `receivables.wip` 的写法也需要一句交代 | 「取代」加上 ADR-0022 决定 6 中税费的排序；ADR-0051 决定 4 第 7 条补一句：销项发票的开具在 `receivables.wip`，税费 BC 管计提与申报 |
| M-8 | Medium | ADR-0051 决定 4 第 9 条、决定 7 第 2 条第 8 点 | 写的是「只读过账事实、不读各来源 BC」（L-8），同一句里又有「加三条事件边（售前、交付执行、合同）」，这三条都是来源 BC。L-8 的原文是「成本读已过账事实」 | 写明这三条事件边的用途（例如售前投入转入 P-2、WBS 归属），并说明它们不是成本数据的来源；否则就违背了 L-8 的字面 |
| M-9 | Medium | ADR-0050 守护第 2、3 条「`build_architecture.py` 或 J 检查脚本」；ADR-0051 守护第 4 条「`SingleAggregateGuardTest` 或 `NamingSurfaceTest`」、守护第 7 条「三组边」 | 守护落点用「或」，无法分派、无法验收。禁止边实际有 4 条（execution↛acceptance；acceptance↛inspection、contract、wip），不是「三组」。另外，传递闭包断言比 M-3 题面（只禁直接依赖）更严，属于起草人加码，没有标出来 | 每条守护只写一个落点和一个负责会话；改为「4 条禁止边」；传递闭包这一点标「裁量」，或请用户确认 |
| M-10 | Medium | 两份的依赖顺序 | ADR-0051 依赖 PR #339（`delivery.closeout` 登记进 `bcs.yaml`，目前未合并）和 ADR-0049（只在本分支、未进 main）；ADR-0050 的 PR-3 依赖 one-quality 尚未产出的 J 检查脚本（ADR-0050 未定事项 5） | 在迁移方案中写明合并顺序：#339 与 ADR-0049 先合并，然后合并 ADR-0050 的 PR-1，最后合并 ADR-0051 的采纳 PR；守护第 2、3、5 条写明在 J 检查脚本入仓前不生效 |
| L-1 | Low | ADR-0050 决定 13 | 「命名空间与 Java 包首段一致」：包首段是 `com` | 改为「`com.forcitis.one` 之后的第一段」 |
| L-2 | Low | ADR-0051 决定 7 第 2 条第 1 点 | 「两个主数据 BC 只依赖 `organization.workforce`、`km.knowledge` 与平台」，但 `masterdata.catalog` → `masterdata.counterparty`（api） | 补上「及同命名空间的往来单位」 |
| L-3 | Low | ADR-0050 决定 20 | 只修订了 ADR-0041 中的「业务过程」；ADR-0023 决定 2 L1「一业务过程一事实表」同样是这个用语 | 补一句：ADR-0023 中的同一用语读作「度量事件」，不另取代 |
| L-4 | Low | ADR-0051 决定 6 第 6 条 | S6 原文是「只启用招采、施工或自有子阶段」，ADR 只保留了「自有」，依据是 Q-A 和 intent 090004 | 写明 Q-A 取代了 S6 的前两项 |
| L-5 | Low | ADR-0050 决定 22 | 删除 `togaf/capabilities.yaml` 后，ADR-0047 影响面要求在该文件第 131 行补引 ADR-0047 的那条就没了着落 | 在新的能力文件或信息图中保留对 ADR-0047 的引用 |
| L-6 | Low | ADR-0050「取代」行第二项 | 取代 ADR-0041 决定 1 中深浅标记的「落点」，但原文根本没写落点（起草人在未定事项 2 中已自认） | 从「取代」中删去；改写为背景说明 |
| L-7 | Low | ADR-0050 影响面「会话画像」 | `sessions/one-data.md` 中「METHOD 第 11 步」等旧步号不止一处 | 在 PR-1 中列明要改的画像 |

## 逐项检查结论

1. **出处**：大部分决定有 intent 或 ADR 可追。「用户」标签用错的有：决定 15 的判据（H-3）、AGG-108（H-4）、`operations.planning` 的「薄」（H-4）、传递闭包守护（M-9）。「推出」被泛化为「经 L-11 认可」（H-4）。
2. **取代完整性**：漏了 ADR-0021 决定 3（H-1）、ADR-0022 决定 6（M-7）、ADR-0041 决定 3 的悬空引用（M-1）；ADR-0023 决定 2 的用语（L-3）可以不取代，但要交代一句。ADR-0016、0017、0024、0031、0040 都已核过：0040 已整份被取代，不用再列。
3. **一致性**：BC 共 28 个加 1 个流程模块，106 个聚合、141 条边、15 层，与聚合稿 §4.1 和脚本输出一致；退役标识的去向与 `bcs.yaml` 一致。不一致的有：边的唯一来源（H-5）、`delivery.closeout` 的边（M-3）、`cost.costing` 的表述（M-8）、命名空间例外（M-2）。
4. **可落地性**：ADR 生成器过不了（B-1）；`contexts.yaml`、`transitions.yaml` 的校验执行不了（B-2）；守护落点有歧义（M-9）；派生值校验依附提议稿（H-5）。
5. **未定事项**：
   - **阻断采纳的**：ADR-0051 未定事项 1（B-2）、ADR-0050 未定事项 1（H-3）。
   - **可以留到施工方案的**：ADR-0051 未定事项 2、3、6（过渡阶段与交付顺序，正好与「自成体系」施工方案一起定，H-2）。
   - **不阻断的**：ADR-0051 未定事项 4（表中先改标「待定」即可）、5（按改后的决定 1 第 1 条处理）、7、8、9；ADR-0050 未定事项 2–5。
   - **漏列的**：生成器格式（B-1）；「自成体系」对 ADR-0051 决定 9 的影响（H-2）；`contexts.yaml` 既有 planned 条目与 L-5 的关系，ADR-0050 未提；AGG-108 超出 D-5（H-4）；closeout 的 audit 边（M-3）；「不校验」的 ID 放在哪里（M-4）；`terms.yaml` 和 `build_terms.py` 中的退役 BC（M-6）；合并顺序（M-10）。
   - **「自成体系」**：ADR-0050 不锁定路线；ADR-0051 决定 9、决定 4 第 7 条的 G1 写法、迁移方案第 4 条、守护时点会锁定，应改成不预设（H-2）。

## 采纳前必须改

1. B-1：两份按生成器的小节白名单与「取代」行格式改写，并用 `build_adrs.py` 实测通过（包括把状态改成「已采纳」、同步改好被取代 ADR 状态行的情形）。
2. B-2：由用户定 ADR-0051 未定事项 1；删去关于 `contexts.yaml` 的错误事实陈述；ADR-0050 决定 3 写明既有 planned 条目怎么处理。
3. H-1、M-7、M-1：补全「取代」——ADR-0021 决定 3 的客商主档时点、ADR-0022 决定 6 的税费顺序、ADR-0041 决定 3 中「按决定 2」的改指。
4. H-2：ADR-0051 决定 9 改成与阶段编号无关的依赖次序约束，`transitions.yaml` 的改动移到施工方案；删去或中性化「G1」「6b」「TA-x」「按 ADR-0021 切换规则」等预设新老并存的写法。
5. H-3：⑥a 判据交用户确认，或把决定 15 降标为「裁量、待确认」。
6. H-4：区分「L-11 已认可」与「推出（未过目）」；AGG-108 与 `operations.planning` 深 / 薄改标。
7. H-5：写清依赖边的手写位置（规划边与已建边分开）与一致性校验；说明派生值清单的机读位置。
8. H-6：界定 ADR-0051 中哪些是规范性内容、哪些是参考性快照。
9. M-3：`delivery.closeout` 补 `→ platform.audit` 边，或者改写留痕方式。

其余 Medium 与 Low 可以在 sonnet 复核轮一并改，也可以记进施工方案或后续 PR。
