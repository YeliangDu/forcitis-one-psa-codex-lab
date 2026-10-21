# 决策影响面按登记网络系统分析（提案）

状态：草稿｜2026-10-21｜负责：one-architecture

任务 ARC-1021-COO-10，用户已批「做」（intent `6f1c27a8`）。本稿是提案，交 one-coordinator 审后上批准页；ADR 治理规则以用户批准为准，本会话不自行定。

用户原意：每做一个决定，影响面分析都沿两张网络系统地走——往上追到关注点和价值主张，往下推到子域、BC、聚合、命令、事件、契约、表、用例、指标，每一层都要把住。

两张网络：`research/networks/01_registration-network-v7.svg`（业务登记网络，17 个节点：01–16 与 C1）与 `02_implementation-network-v2.svg`（实现网络，I01–I12，与 v7 共用 8 个接缝节点：06、07、09、10、11、12、14、C1）。下文「节点」指这 25 个节点类型。

## 0. 结论先说

1. **网络现在还不是数据**：25 个节点里，在 main 上可机读、在用的只有 9 个（01、I01、I02、I03、I04b、I05 中的 API、I06、I07、I10/16）；10 个是提议稿，6 个完全没有登记（05、12、13、14、I08、I11）。节点之间的编号已经错位（第 1.3 节）。
2. **有现成的连线底座**：本分支 `architecture/panorama/panorama.yaml`（`panorama_build.py` 生成）已登记 1834 条带出处的连线、15 种连线类别，按 v5 的 15 节点出缺口清单；`OBJ46_MERGE_IMPACT.md`、`OBJ126_SHAREHOLDER_IMPACT.md` 是按对象走影响面的两份先例。提案在它上面扩到 v7 + v2 的 25 节点，不另起一套。
3. **做法分三步**：先把网络变成数据（第 1 节）；再让 ADR 与取舍决定都声明锚点、由脚本沿连线生成逐层影响清单、起草人逐条判定（第 2 节）；每层判定用统一的问题清单（第 3 节）。
4. **有 5 件事要用户定**（第 4 节），其中两件与已有决定冲突：实现网络「按能力划边界」与 METHOD 接缝 J4 相反；网络变数据须重启 10-19 暂停的 SSOT 重建线。

## 1. 网络变数据

### 1.1 每个节点落在哪

状态：**已有**＝在 main、机器可读、在用；**提议稿**＝只在分支、`proposals/` 或主检出未提交文件；**过时**＝停在旧日期或与现行 ADR、代码不一致；**缺**＝没有登记。路径缩写：A＝`docs/modernization/architecture`，P＝A/proposals，C＝P/closeout，D＝A/ddd，B＝`backend/architecture`。条数与日期为 2026-10-21 盘点（sonnet 只读盘点，本会话抽核）。

| 节点 | 现有登记 | 前缀 | 条数 | 上游字段 | 状态 | 提议的唯一登记与上游字段 |
|---|---|---|---|---|---|---|
| 01 干系人 | A/ssot/01-stakeholders/SSOT.yaml | SH-C、SH-C-L | 38 | relations→VS | 已有（10-20 冻结） | 不变；旧编号（value-streams 17 个、责任矩阵 13 个）对照一次后清掉 |
| 02 关注点 | P/concerns.proposal.yaml | CON- | 16 | by→SH、accepted_as→STG | 提议稿（主检出未提交） | ssot/02-concerns，`raised_by`→SH；引用的 SH-M1、SH-P1c 不在新 SSOT，须先对照 |
| 03 目标与战略 | A/togaf/vision.yaml | GOAL-、DRV- | 8 / 6 | goals.drivers | 过时（10-15，是现代化项目目标不是企业战略；STR 缺） | ssot/03-goals，GOAL、STR 两类，`addresses`→CON，带期限与度量 |
| 04 取舍决定 | A/panorama/tradeoffs.yaml | DECR- | 2 | subject | 提议稿（仅本分支） | ssot/04-tradeoffs，`assesses`→CON/GOAL，`outcome`＝做 / 不做，`review_by` 日期；「不做」也留 |
| 05 价值主张 | 无（暂由 value-streams.v2 的 `value` 字段代用） | — | 15 | — | 缺 | ssot/05-value-propositions，`from_decision`→DEC-R（只接「做」的） |
| 06 业务能力 | P/capabilities.v2.yaml | CAP- | 25+77 | object→OBJ | 提议稿（旧 togaf/capabilities.yaml 12 条未删） | ssot/06-capabilities，`realizes`→VP，`owner_org`→ORG |
| 07 组织与角色 | SSOT.yaml roles、terms kind=role、C/RESPONSIBILITY_MATRIX.yaml | ROLE- | 11 / 33 / 28 | host_category | ORG 缺，ROLE 分散 | ssot/07-org-roles：ORG（含法人）、ROLE（含授权），`bears`→CAP |
| 08 价值流与阶段 | P/value-streams.v2.yaml | VS-、VS-nn.n | 15+61 | triggering_stakeholders→SH、stage.capabilities→CAP | 提议稿 | ssot/08-value-streams；阶段门条件引用 OBJ 状态（METHOD J2） |
| 09 业务流程 | C/process-catalog.yaml（生成）；三组骨架 647 步（分支）；P/process-steps.proposal.yaml 36 步 | PRC-、PS- | 200 | stages、primary_object、owner_bc | 已有但偏大（含 L4/L5，与 v7「仅 L2/L3」不符）；步骤的 `prc` 全空 | 目录只留 L2/L3；步骤挂 PRC，每步改状态须声明 Command（v7 P-1） |
| 10 业务对象 | P/ENTERPRISE_INFO_MAP_DRAFT.md §1 | OBJ- | 126 | 父对象 | 提议稿（markdown 表） | ssot/10-objects（yaml），含 `states`、`transitions`；转换现有 OBJ 键 8 对象与 AGG 键 130 台机两套，须并成一套 |
| 11 受控业务执行 | C/RESPONSIBILITY_MATRIX.yaml；分支 C/*/commands.yaml | 无 ID（中文名为键） | 586 / 592 | object→AGG | 提议稿 | 与 I04a 合用一套 CMD- 编号，`changes`→OBJ 状态转换、`owner_bc`→BC |
| 12 业务事实 | 无 | — | 0 | — | 缺 | ssot/12-facts，`produced_by`→CMD/EVT，业务时间与记录时间 |
| 13 维度 | 无（注册表的 DIMENSION_VERSIONED 是版本化主数据，与分析维度同名异义） | — | 0 | — | 缺 | ssot/13-dimensions，`slices`→FACT，SCD 版本；同名异义交 one-glossary |
| 14 计算绑定 | 无 | — | 0 | — | 缺 | ssot/14-bindings，`fact`、`formula_version`、时间基准 |
| 15 指标与评价 | terms kind=metric 79；r0/METRIC_BASELINE_DRAFT.md 48；vision KPI 16 | 三套 | — | 无 | 过时且三口径互不引用 | ssot/15-kpis 唯一，`bound_by`→BIND、`answers`→CON |
| 16 决策与行动 | adr/*.md；adr/catalog.yaml；data/repair-cards/ 5 张 | ADR-、无 | 70；50；5 | category、bc、stage | ADR 已有；catalog 缺 0052–0070；整改无机器登记 | ADR 不变；整改与偏差行动并入任务登记 `tasks/*.md`，`triggered_by`→KPI 或 ADR |
| C1 约束与认定规则 | P/ENTERPRISE_AGGREGATES_DRAFT.md §1.2（INV）；rules/*.md（RULE） | INV-、RULE- | 134；126 | 归属 AGG | 提议稿（md 表）；INV 与 RULE 无对照 | ssot/c1-rules：INV 为主键、RULE 卡引用 INV；`execution_points`→EP、`tests`→TC、`exceptions`（审批、到期、补偿） |
| I01 子域 | D/subdomains.yaml | 无（id 如 cost） | 13 | — | 已有 | 不变；加 `capabilities`→CAP（见第 4 节 U1） |
| I02 限界上下文 | D/contexts.yaml | 点号名 | 41（已建 13） | subdomain | 已有，但与 glossary/bcs.yaml（68）、bc-map.yaml 三套不同步 | contexts.yaml 为唯一；bcs.yaml 只引用 |
| I03 聚合 | D/aggregates.yaml | `<模块>/<类>` | 15 | bc | 已有（与代码 15 个根一致） | 规划聚合 AGG-001..130 在 md 草稿，两套 ID 须对照后并成一份 |
| I04a 命令 | 同 11 | — | — | — | 见 11 | 见 11 |
| I04b 领域事件 | docs/modernization/events/*.yaml | eventType | 59 | 发布者 | 已有（守护测试） | 不变；加 `fact`→FACT |
| I05 接口契约 | openapi.json；B/external-read-registry.yaml；C/INTEGRATION_INVENTORY.yaml | — | 68 操作；19 | owner_bc | API 已有；集成清单提议稿、id 全空 | 不变；集成清单补 IE- 编号 |
| I06 表 | B/data-access-registry.yaml | schema.table | 53 | aggregate（28 张有值） | 已有 | 不变；`aggregate` 补齐 |
| I07 代码模块 | B/module-dependencies.txt | 模块名 | 12 行 | — | 已有 | 不变 |
| I08 执行点 | 无（各类 `*-exceptions.txt` 近似，不指向 INV） | — | 0 | — | 缺 | ssot/i08-execution-points：EP-，`enforces`→INV，类别（授权 / 不变量 / 数据约束） |
| I09 验收用例 | backend/src/test/.../acceptance/ 130 个文件；分支 scenarios 1444 条 | 无登记 | — | 测试名里的 RULE- | 登记缺 | 不另建登记：用例以注解或 DisplayName 带 `INV-`、`CMD-` 编号，脚本从代码抽出 |
| I10 ADR | 同 16 | — | — | — | — | 同 16 |
| I11 读模型 | terms kind=read_model 6；process-catalog type=read_model 6 | — | 6 | — | 缺登记 | ssot/i11-read-models：RM-，`binds`→BIND |
| I12 防腐层 | glossary/acl/acl-classification.json；P/ACL_BOUNDARY.md | — | 672 | — | 已有（JSON） | 不变；加 `contexts`→BC |

### 1.2 连线

- **以 `panorama.yaml` 为唯一连线登记**，由 `panorama_build.py` 从各节点登记的上游字段生成，不手写；每条连线带出处（文件与键）。现有 15 种连线类别扩到两张图上的全部连线：v7 的「提出 → 取舍依据 → 决定做 → 承诺回应 → 决定能力配置 → 承担能力 → 支撑阶段 → 由价值流兑现 → 指派角色 → 门条件引用状态 → 拆解为流程 → 声明编排的对象 → 每步声明 Command → Command 改变状态 → 产生新事实 → 认定并记录 → 绑定口径 → 版本化计算 → 维度切分 → 按维度聚合 → 偏差触发 → 执行行动 → 证据回答关注点 → 复审取舍」与 C1 的横切约束；v2 的「按能力划分 → 落到上下文 → 一上下文一模块 → 模型化为聚合 → 持久化 → 落为命令 → 改变聚合状态 → 发布事件 → 跨上下文发布 → 发布契约 → 投影 → 公式版本 → 出站 / 入站翻译 → 守卫 Command → 验证执行点 → 失败证据 → 用例」。
- **接缝不变式变成检查**：v2 图上列的 9 条（每项能力归属一个子域；每个对象属于且仅属于一个聚合；每个聚合 ≥1 张表；每个命令有 owner BC；每个事件有唯一发布者与版本；跨上下文只走契约或已发布事件；外部系统只经防腐层；每条约束 ≥1 执行点、≥1 用例；每个模块在登记表）与 v7 的 P-1（流程步骤改状态必须声明 Command）、「无执行点＝未求解」，逐条写成 `panorama_build.py --check` 的规则，输出到 GAPS.md。

### 1.3 先要清的错位

1. 旧干系人编号在 value-streams、责任矩阵、concerns 里同串异义（旧 SH-C3 在新表是 SH-C4），没有校验器。
2. BC 三套清单（contexts 41、bcs 68、聚合稿 B-01..31）不同步。
3. 已建聚合（`模块/类`）与规划聚合（AGG-nnn）无对照；状态转换有 OBJ 键与 AGG 键两套。
4. 命令没有编号；流程步骤的 `prc` 全空。
5. 对象、聚合、不变量、规则卡停在 markdown 表，不可机读；ADR catalog 缺 19 条。

### 1.4 顺序

按「锚点先、上下游后」：10 对象与 I03 聚合 → C1 规则与执行点 → 11/I04a 命令 → 09 流程 → 06 能力 → 02 关注点、04 取舍、05 价值主张 → 12–15 度量链。每补一类，跑一次 `panorama_build.py --check` 直到收敛。度量链（12–15、I11）现无任何登记，工作量最大，可放在 W5 前补。

## 2. 决定走网络

### 2.1 每份决定要写什么

适用：新起草的正式档 ADR，以及 04 取舍决定（DEC-R）。轻量档与已采纳的 ADR 不追补（范围待用户定，第 4 节 U4）。

1. **锚点**：决定直接改动的节点，写节点编号（如 `OBJ-029`、`INV-041`、`CMD-...`、`ADR-0068`）。锚点可以有多个。
2. **往上的依据链**：脚本沿连线反向走到 02 关注点与 05 价值主张，列出路径；起草人确认这条依据成立，或写「依据缺口」。走不到关注点的，说明是「上层未登记」还是「决定本身没有依据」。
3. **往下的逐层影响清单**：脚本沿连线正向走，按层列出受影响的条目（能力、角色、阶段、流程、对象、命令、事实、维度、绑定、指标；子域、BC、聚合、命令、事件、契约、表、模块、执行点、用例、读模型、防腐层）。起草人逐条判定：
   - **改**：写改什么，必须开卡（任务号），卡号写进清单；
   - **不改**：写理由（一句）；
   - **登记缺口**：该层登记还没有，人工判定并注明，随第 1 节补登记时回填。
4. 清单是 ADR 的附件（`research/<主题>/ADR-NNNN-影响面.md`），正文「影响面」小节只放摘要与附件链接，不挤占 20 KB 篇幅。

### 2.2 批准页与复审

- **批准页**：每份 ADR 加一块「依据链与影响摘要」：上溯到哪条关注点与价值主张；往下各层「改」几条、「不改」几条、「登记缺口」几条；开了哪些卡。
- **复审**：首轮复审（opus）按层核对：每层是否都判了，「不改」的理由是否成立，「改」的卡是否存在，依据链是否真能回答关注点；结论按层列出。

### 2.3 要改的文件

1. `adr/src/_readme_lifecycle.md`（README 生命周期节）：第 5 节必备小节增「网络影响面」（正式档、自生效日起新起草的）；写清锚点、依据链、逐层清单、判定三取值、「改」必开卡。
2. `adr/new_adr.py`：空壳增「## 网络影响面」小节，含锚点与附件链接两行占位。
3. `adr/build_adrs.py`：校验正式档新稿有该小节且附件存在；附件中每行有判定，「改」行的任务号在 `tasks/` 中存在；不符即失败。
4. 取舍决定（DEC-R）的登记字段加 `anchors`、`impact_file`，同一套校验。

### 2.4 遍历脚本

- **位置**：`architecture/panorama/impact_trace.py`（新，独占于本会话）。
- **输入**：`panorama.yaml`（节点与连线）；锚点编号列表；可选的层过滤与深度。
- **输出**：
  - Markdown 表（附件正文）：层 | 条目 | 到达路径 | 判定（空，待填）| 理由或卡号（空，待填）；另一节为上溯路径；
  - JSON（供批准页与 `build_adrs.py` 读取）；
  - 再次运行时保留已填的判定，只增删变化的行，并标出「锚点下游有新条目」。
- **退出码**：锚点不存在为失败；遇到「缺」的节点类型只告警并在表中标「登记缺口」。
- **与现有先例**：`OBJ46_MERGE_IMPACT.md` 一类手写影响清单今后由此脚本生成骨架，人只填判定。

### 2.5 过渡

在第 1 节补齐之前，多数下游层会是「登记缺口」，清单的价值有限。建议：先拿两份在途决定试点（例如 ADR-0073 的组织属性与法人归属、下一份 ADR-0066 第 9 条触发的平台集成模块拆分），看清单是否真能抓到人工漏掉的影响，再定是否全面推行。

## 3. 系统思考检查单

每个节点一行白话说明，以及做决定时在这一层要回答的问题。白话说明取自两张图的卡片；定义依据先查本地三库（用户 intent `cace83d4`），引书名与页码，查不到页码的注「页码未核」。

### 3.1 往上：为什么要做

| 节点 | 白话 | 要回答 |
|---|---|---|
| 01 干系人 | 谁在乎这件事、谁承担后果 | 这个决定影响谁？谁会因此受损或得益？有没有漏掉承担后果却没被问到的人？ |
| 02 关注点 | 他们在乎什么、担心什么（TOGAF 10 Part 4 §3.1：关注点是一个或多个干系人对系统的利益所在；ArchiMate 4 §6.3.1） | 这个决定回应哪条关注点？如果找不到，是关注点没登记，还是这个决定没有外部需要？ |
| 03 目标与战略 | 要去哪里、靠什么路线去（BMM：Goal、Objective、Strategy） | 它推进哪个目标？会不会让另一个目标变差？期限与度量是什么？ |
| 04 取舍决定 | 做什么、不做什么；不做也留痕（BMM：Assessment → Course of Action） | 这次是在「做」还是「不做」？放弃了什么、理由是什么、何时复审？ |
| 05 价值主张 | 我们对外承诺交付什么价值（BIZBOK v11 p.145） | 它兑现或改变了哪条对外承诺？承诺变了，客户和合同那一侧谁要知道？ |

### 3.2 往下：业务侧落到哪

| 节点 | 白话 | 要回答 |
|---|---|---|
| 06 业务能力 | 靠什么本事兑现承诺（做什么，不是谁做；BIZBOK v11 第 2 章，页码未核） | 涉及哪些能力？是新增能力、改变能力，还是只改实现？能力负责组织变不变？ |
| 07 组织与角色 | 谁来做、以什么身份、有什么权（ArchiMate：Business Actor、Role） | 谁的权限变了？有没有双人控制、职责分离、本人限制要跟着改？角色有没有对应的人？ |
| 08 价值流与阶段 | 从触发到交付分几步（ArchiMate 4 §7.3.2；BIZBOK） | 哪个阶段的进出条件变了？门条件引用的对象状态还成立吗？ |
| 09 业务流程 | 每步谁做、做什么、何时算完（仅 L2/L3） | 哪些流程步骤变了？每个改状态的步骤都声明了命令吗（P-1）？「何时算完」变了，验收用例要不要改？ |
| 10 业务对象（锚） | 业务里有身份、有状态的东西 | 改了哪个对象的属性、状态或转换？谁还引用这个状态（门条件、流程、事实、约束）？ |
| 11 受控业务执行 | 一次被授权的动作（命令与事件） | 新增或改了哪些命令？谁有权发、能不能被拒绝、能不能重试？产生什么事件？ |
| 12 业务事实 | 发生过的、不可改的记录（REA：Economic Event） | 会不会产生新事实，或改变事实的口径？历史事实要不要重算——按「不追溯」原则一般不要，例外须写明 |
| 13 维度 | 按人、项目、客户、组织切开看（Kimball：Conformed Dimension，Kimball Group Reader p.49） | 切分口径变了吗？按当时值还是当前值读？ |
| 14 计算绑定 | 这次指标用哪版公式与口径 | 公式或口径版本要不要升？旧版本的结果怎么解释？ |
| 15 指标与评价 | 目标多少、实际多少、差多少 | 哪些指标会动？能否用指标证明这个决定回答了关注点（闭环回到 02）？ |
| 16 决策与行动 | 谁负责改、什么时候改完 | 开了哪些卡、谁负责？复核点在哪？ |
| C1 约束与认定规则 | 什么情况下必须拒绝（BMM：Business Policy、Business Rule；SBVR 1.5 §16.1.2 p.110） | 新增或改了哪些规则？每条规则在哪里强制（执行点）、用什么用例证明？有没有例外，例外怎么审批、何时到期？ |

### 3.3 往下：实现侧落到哪

| 节点 | 白话 | 要回答 |
|---|---|---|
| I01 子域 | 问题空间的业务划分 | 涉及哪个子域？核心、支撑还是通用的定位变不变？ |
| I02 限界上下文 | 一套统一语言成立的边界（Millett & Tune 2015） | 改动落在哪个 BC？同一个词在两个 BC 里意思不同吗？要不要新 BC？ |
| I03 聚合 | 聚合内不变量须一致的边界（Evans p.74：提交聚合内任何对象的改动时，聚合的全部不变量都须满足） | 改的不变量在哪个聚合里守？会不会需要跨聚合一致（那要改成最终一致或改边界）？ |
| I04a 命令 | 改状态的请求：可校验、可拒绝、可重试 | 命令的入口（人、定时、外部）、幂等、失败提示变了吗？ |
| I04b 领域事件 | 已经发生的事实：不可撤、可重放 | 事件载荷要不要升版本？订阅方是谁，会不会被破坏？ |
| I05 接口契约 | 上下文之间的同步通道 | 契约是不是冻结对象（ADR-0067）？兼容新增还是破坏性变更？ |
| I06 表与数据结构 | 聚合与事实的持久化归属 | 改哪些表、哪些约束？迁移脚本、只追加规则、个人信息分级变不变？ |
| I07 代码模块 | 上下文在仓库里的物理落点 | 模块依赖边要不要增？会不会成环？api 方法数会不会超上限？ |
| I08 执行点 | 授权、不变量、数据约束在哪一处强制 | 每条新规则至少有一个执行点吗？库层拦不住的，进程内靠什么兜底？ |
| I09 验收用例 | 怎么证明它对（Given/When/Then） | 每条规则、每个命令有用例吗？要删或改的断言列出理由了吗？ |
| I10 架构决策记录 | 为什么这样划、这样做 | 取代或部分取代了哪些已采纳 ADR？字面冲突都列了吗？ |
| I11 读模型 | 给指标与查询用的投影 | 投影要不要重建？口径版本对得上吗？ |
| I12 防腐层 | 把外部模型翻译成我们的语言（Millett & Tune 2015 p.143） | 有没有外部系统受影响？只经防腐层进出吗？来源对照要不要改？ |

## 4. 要用户定的事

| 编号 | 事项 | 推荐 | 备选与代价 |
|---|---|---|---|
| U1 | 实现网络「按能力划边界：06 能力 → I01 子域 → I02 上下文」与现行 METHOD 接缝 J4「能力只作视图、不驱动 BC，能力文件禁 `bc`、`subdomain` 字段」相反 | 能力作为子域划分的**依据之一**并登记 `capabilities` 连线，但 BC 仍按统一语言与对象划（J4 保留「不驱动 BC」）；改 METHOD J4 措辞并留痕 | 照图改为「按能力划 BC」：现有 41 个上下文要逐个对能力重验，可能重划边界；维持 J4 原样：图上这条连线不登记 |
| U2 | 网络变数据须重启 10-19 暂停的 SSOT 重建线（intent `2026-10-19-223711`） | 按第 1.4 节顺序分批重启，先做锚点三类（对象、聚合、规则），每批交 one-coordinator 排期 | 不重启：脚本只能走 main 上已有的 9 类节点，多数层是「登记缺口」 |
| U3 | 流程只登 L2/L3 与现有目录 200 条（含 L4/L5）不符 | 目录保留全部条目，L4/L5 标为「步骤级」，不作为网络节点 | 删到只剩 L2/L3：现有 L4/L5 的阶段、对象、BC 关系要迁到步骤上 |
| U4 | 「决定走网络」的适用范围 | 新起草的正式档 ADR 与取舍决定，自批准日起；先拿两份在途决定试点，再全面推行 | 连已采纳 ADR 一起回补：70 份，工作量大，且多数下游层还是登记缺口 |
| U5 | 判定为「改」的条目必开卡，由谁开 | 起草会话开卡、登记任务号，one-coordinator 派工 | 由 one-coordinator 统一开卡：起草与派工之间多一次交接 |

另交 one-glossary：分析「维度」与注册表 `DIMENSION_VERSIONED`（版本化主数据）同名异义。

## 5. 证据

- 两张图：`claude/one-coordinator-two-lanes` 的 `research/networks/01_registration-network-v7.svg`、`02_implementation-network-v2.svg`（文字已提取核对）。
- 盘点：sonnet 只读盘点 2026-10-21（25 节点逐项，结果见第 1.1 节）。
- 现有底座：`architecture/panorama/panorama.yaml`、`panorama_build.py`、`GAPS.md`、`OBJ46_MERGE_IMPACT.md`、`OBJ126_SHAREHOLDER_IMPACT.md`（本分支）。
- METHOD 第 3 节接缝 J1–J12、第 4 节对抗检查。
- 本地三库原文：TOGAF 10 Part 4 §3.1（Concern）；ArchiMate 4 Specification（2026）§6.3.1、§7.3.2；BIZBOK Guide v11.0 p.145（Value Proposition）；Evans《Domain-Driven Design》p.74（Aggregate 不变量）；SBVR 1.5 §16.1.2 p.110（Business Policy 与 Business Rule）；Kimball Group Reader 2nd Ed p.49（Conformed Dimension）；Millett & Tune 2015 p.143（Anticorruption Layer）。
