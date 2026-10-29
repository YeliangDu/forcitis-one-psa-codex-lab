# 决策影响面按登记网络系统分析（提案）

状态：已确认｜2026-10-21｜负责：one-architecture

任务 ARC-1021-COO-10，用户已批「做」（intent `6f1c27a8`）。用户 2026-10-21 已在批准页答复 U1–U6，均选推荐并附备注（批准页 KrgQEbzwNuxvbLZgVQpp1q，原文 `claude/one-coordinator-two-lanes` @40f165e9 `research/networks/决策影响面提案-用户答复-2026-10-21.md`）；U7–U11 按推荐执行。下文已按答复改定，第 4 节记为已定。

用户原意：每做一个决定，影响面分析都沿两张网络系统地走——往上追到关注点和价值主张，往下推到子域、BC、聚合、命令、事件、契约、表、用例、指标，每一层都要把住。

两张网络：`research/networks/01_registration-network-v7.svg`（业务登记网络，17 个节点：01–16 与 C1）与 `02_implementation-network-v2.svg`（实现网络，I01–I12 共 13 个，I04 分 a、b，与 v7 共用 8 个接缝节点：06、07、09、10、11、12、14、C1）。I04a 与 11、I10 与 16 实为同一类登记，合并后共 28 个节点类型，下文「节点」即指这 28 类。

## 0. 结论先说

1. **网络现在还不是数据**：28 类里，在 main 上可机读、在用的 10 类（01、16/I10、I01、I02、I03、I04b、I05 的 API 部分、I06、I07、I12），另 09 已有但口径偏大不计入；提议稿或过时 11 类（02、03、04、06、07、08、10、11/I04a、15、C1、I09；其中 03、15 过时，I09 只有场景提议稿而无登记）；I05 的集成清单为提议稿，归在已有的 I05 内；完全没有登记 6 类（05、12、13、14、I08、I11）。此前一处写「9 个」一处写「约 10 类」，以本句为准（用户 U2 要求先订正）。节点之间的编号已经错位（第 1.3 节）。
2. **有现成的连线底座**（只在本分支，main 上没有，合入时点见 U8）：本分支 `architecture/panorama/panorama.yaml`（`panorama_build.py` 生成）已登记 1834 条带出处的连线、15 种连线类别，按 v5 的 15 节点出缺口清单；`OBJ46_MERGE_IMPACT.md`、`OBJ126_SHAREHOLDER_IMPACT.md` 是按对象走影响面的两份先例。提案在它上面扩到 v7 + v2 的 28 类，不另起一套。
3. **做法分三步**：先把网络变成数据（第 1 节）；再提议让 ADR 与取舍决定都声明锚点、由脚本沿连线生成逐层影响清单、起草人逐条判定（第 2 节）；每层判定用统一的问题清单（第 3 节）。第 1 节的新前缀与目录布局、第 2 节的全部做法都是提议，规则由用户在第 4 节定。
4. **有 11 件事要用户定**（第 4 节），其中三件与已有规则冲突：实现网络「按能力划边界」与 METHOD 接缝 J4 相反；网络变数据须重启 10-19 暂停的 SSOT 重建线；ADR README 第 5 节不许增设小节，因此影响面只能放进现有「影响面」小节。

## 1. 网络变数据

### 1.1 每个节点落在哪

「提议的唯一登记」一列中的新前缀（STR、ORG、FACT、DIM、BIND、CMD、EP、RM、IE 等）与 `ssot/NN-*` 目录布局都是提议：按 CLAUDE.md 第 2 条先作为提议术语交 one-glossary 登记（写明使用范围与 bc），由谁批见 U9。

状态：**已有**＝在 main、机器可读、在用；**提议稿**＝只在分支、`proposals/` 或主检出未提交文件；**过时**＝停在旧日期或与现行 ADR、代码不一致；**缺**＝没有登记。路径缩写：A＝`docs/architecture`，P＝A/proposals，C＝P/closeout，D＝A/ddd，B＝`backend/architecture`。条数与日期为 2026-10-21 盘点（sonnet 只读盘点，本会话抽核）。

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
| I03 聚合 | D/aggregates.yaml | `<模块>/<类>` | 15 | bc | 过时（停在 10-16，此后已建模块的新聚合未登记；调度 2026-10-21 审定） | 规划聚合 AGG-001..130 在 md 草稿，两套 ID 须对照后并成一份 |
| I04a 命令 | 同 11 | — | — | — | 见 11 | 见 11 |
| I04b 领域事件 | docs/architecture/events/*.yaml（8 个文件） | eventType | 59 条消息 | 发布者 | 已有（守护测试） | 不变；加 `fact`→FACT |
| I05 接口契约 | openapi.json；B/external-read-registry.yaml；C/INTEGRATION_INVENTORY.yaml | — | 68 操作；19 | owner_bc | API 已有；集成清单提议稿、id 全空 | 不变；集成清单补 IE- 编号 |
| I06 表 | B/data-access-registry.yaml | schema.table | 52 | aggregate（28 张有值） | 已有 | 不变；`aggregate` 补齐 |
| I07 代码模块 | B/module-dependencies.txt | 模块名 | 12 行 | — | 已有 | 不变 |
| I08 执行点 | 无（各类 `*-exceptions.txt` 近似，不指向 INV） | — | 0 | — | 缺 | ssot/i08-execution-points：EP-，`enforces`→INV，类别（授权 / 不变量 / 数据约束） |
| I09 验收用例 | backend/src/test/.../acceptance/ 130 个文件；分支 scenarios 1444 条 | 无登记 | — | 测试名里的 RULE- | 登记缺 | 不另建登记：用例以注解或 DisplayName 带 `INV-`、`CMD-` 编号，脚本从代码抽出 |
| I10 ADR | 同 16 | — | — | — | — | 同 16 |
| I11 读模型 | terms kind=read_model 6；process-catalog type=read_model 6 | — | 6 | — | 缺登记 | ssot/i11-read-models：RM-，`binds`→BIND |
| I12 防腐层 | glossary/acl/acl-classification.json；P/ACL_BOUNDARY.md | — | 672 | — | 已有（JSON） | 不变；加 `contexts`→BC |

### 1.2 连线

- **提议以 `panorama.yaml` 为唯一连线登记**（须先合入 main，见 U8），由 `panorama_build.py` 从各节点登记的上游字段生成，不手写；每条连线带出处（文件与键）。现有 15 种连线类别扩到两张图上的全部连线：v7 的「提出 → 取舍依据 → 决定做 → 承诺回应 → 决定能力配置 → 承担能力 → 支撑阶段 → 由价值流兑现 → 指派角色 → 门条件引用状态 → 拆解为流程 → 声明编排的对象 → 每步声明 Command → Command 改变状态 → 产生新事实 → 认定并记录 → 绑定口径 → 版本化计算 → 维度切分 → 按维度聚合 → 偏差触发 → 执行行动 → 证据回答关注点 → 复审取舍」与 C1 的横切约束；v2 的「按能力划分 → 落到上下文 → 一上下文一模块 → 模型化为聚合 → 持久化 → 落为命令 → 改变聚合状态 → 发布事件 → 跨上下文发布 → 发布契约 → 投影 → 公式版本 → 出站 / 入站翻译 → 守卫 Command → 验证执行点 → 失败证据 → 用例」。
- **接缝不变式变成检查**：v2 图上列的 9 条（每项能力归属一个子域；每个对象属于且仅属于一个聚合；每个聚合 ≥1 张表；每个命令有 owner BC；每个事件有唯一发布者与版本；跨上下文只走契约或已发布事件；外部系统只经防腐层；每条约束 ≥1 执行点、≥1 用例；每个模块在登记表）与 v7 的 P-1（流程步骤改状态必须声明 Command）、「无执行点＝未求解」，逐条写成 `panorama_build.py --check` 的规则，输出到 GAPS.md。

### 1.3 先要清的错位

1. 旧干系人编号在 value-streams、责任矩阵、concerns 里同串异义（旧 SH-C3 在新表是 SH-C4），没有校验器。
2. BC 三套清单（contexts 41、bcs 68、聚合稿 B-01..31）不同步。
3. 已建聚合（`模块/类`）与规划聚合（AGG-nnn）无对照；状态转换有 OBJ 键与 AGG 键两套。
4. 命令没有编号；流程步骤的 `prc` 全空。
5. 对象、聚合、不变量、规则卡停在 markdown 表，不可机读；ADR catalog 缺 19 条。

### 1.4 顺序（用户 U2：数据驱动）

1. **先统计**：把 origin/main 上已采纳的 ADR 逐份过一遍，只统计、不回填，数出「每类节点被多少份决定改过」。统计表另成文件（`research/networks/ADR_NODE_FREQUENCY.md`），交 one-coordinator 审后派工。
2. **按频次排批次**，同频次时**能自动生成的层优先**（业务对象、聚合、规则、命令、事件、表、模块等可从代码或登记表自动出）。
3. **纯人工层放最后并标「人工维护」**：02 关注点、04 取舍决定、05 价值主张。用户原话：「手工登记的层一定会腐坏，而网络的可信度 = 自动化率。」每批完成后报告该层的自动化率（自动生成条目 / 总条目）。
4. 每补一类，先运行 `python3 docs/build_all.py` 生成命令目录与全景，再运行 `python3 docs/architecture/panorama/panorama_build.py --check` 直到收敛。命令目录与全景产物不入仓，也可在 generated-docs 分支阅读快照；缺失时校验失败，不以空目录代替。

## 2. 决定走网络（提议）

本节全部是提议，规则以用户在第 4 节的决定为准。

### 2.1 每份决定要写什么

适用范围见 U4。

1. **锚点**：决定直接改动的节点，写节点编号（如 `OBJ-029`、`INV-041`、`ADR-0068`）。锚点可以有多个。
2. **往上的依据链**：脚本沿连线反向走到 02 关注点与 05 价值主张，列出路径；起草人确认依据成立，或写「依据缺口」，并说明是「上层未登记」还是「决定本身没有依据」。
3. **往下的逐层影响清单**：脚本沿连线正向走，按层列出受影响条目。起草人逐条判定：**改**（写改什么，开卡并写任务号，谁开卡见 U5）、**不改**（一句理由）、**登记缺口**（该层尚无登记，人工判定并注明）。
4. **落点**：README 第 5 节不许增设小节，故不新建小节。正文现有「影响面」小节写锚点、依据链摘要（到哪条关注点与价值主张）、各层改 / 不改 / 缺口条数与附件链接；逐层清单放附件，位置与是否计入 20 KB 见 U7。
5. **与 README 第 9 节第 2 条「全文检索被改条款」的分工**：全文检索查 ADR 正文字面，网络遍历查已登记节点的连线；两者都做，检索记录仍写在「证据」小节，遍历清单在附件。

### 2.2 批准页与复审

- **批准页**：每份 ADR 加一块「依据链与影响摘要」，数字取自附件。
- **复审**：沿用 METHOD 第 4 节「每步对抗检查」（opus 首轮、sonnet 复核），首轮复审在既有清单之外加一项「逐层判定是否站得住、『改』的卡是否存在」，不另立一套复审制度。

### 2.3 要改的文件（用户批准后才改）

1. `adr/src/_readme_lifecycle.md` 第 5 节：在「影响面」小节的写法中加锚点、依据链摘要与附件链接三项。
2. `adr/new_adr.py`：「影响面」小节空壳加这三项占位。
3. `adr/build_adrs.py`：是否校验附件、硬失败还是告警，见 U6；试点期不改（第 2.5 节）。
4. 取舍决定（DEC-R）登记加 `anchors`、`impact_file` 两个字段（随 U2 的 SSOT 重建批次）。

### 2.4 遍历脚本

- **位置**：`architecture/panorama/impact_trace.py`（新，本会话独占）。
- **输入**：`panorama.yaml`；锚点编号列表；可选层过滤；深度（默认 3 跳，可调）。
- **扇出控制**：
  - 06 能力 → I01 子域允许一对多，连线标「主要承载」（用户 U1）；没有任何子域承载的能力列为缺口，这正是这条线的产出（能力清单冗余或架构缺口）。
- 默认只走「直接改变」类连线（如对象 → 状态转换 → 命令 → 事件 → 事实），引用类连线（如「门条件引用状态」）只列一层、不再下推；
  - 同一层超过 30 条时按上游归组汇总（例如「OBJ-029 的 12 个命令」一行），起草人对组判定，组内有例外再展开；
  - OBJ-46 并入先例约数百行，按此规则应收敛到每层十条以内的组。
- **输出**：
  - Markdown 附件：上溯路径一节；逐层表（层 | 条目或组 | 到达路径 | 判定 | 理由或卡号）；
  - JSON（供批准页读取）。
- **增量重跑**：
  - 判定按键（层, 条目编号）保留；
  - 条目编号因重编号（第 1.3 节：AGG 两套合并、新立 CMD- 等）而消失的，旧判定标「失效」并要求重判，不静默丢弃；重编号对照表由重编号的那一批 SSOT 提交一并给出，脚本据此迁移可迁的判定；
  - 到达路径变了的「不改」标「待复核」，起草人确认后清除；
  - 新出现的下游条目标「新增」，未判定不得改为已采纳。
- **退出码**：锚点不存在为失败；遇到「缺」的节点类型只告警并在表中标「登记缺口」。

### 2.5 过渡与试点

第 1 节补齐之前，多数下游层是「登记缺口」，清单价值有限。建议试点期：
- 不改 `build_adrs.py`、不强制附件；
- **试点 10 份**（用户 U4），覆盖 5 种决定类型：平台件、业务 BC、数据模型、流程、合规，每类两份；候选含 ADR-0073（数据模型）与平台集成模块拆分（平台件），其余由 one-coordinator 从在途与近期决定中选；每份用第 3 节检查单逐层作答，再加脚本对已登记各层的输出；
- 已采纳 ADR 不回补，但第 1.4 节的统计同时用来校准 U6 的检查规则（哪些层最常漏判、扇出多大）；
- 比对检查单与脚本各抓到什么、人工漏了什么，据此由用户定是否全面推行与是否硬校验（U4、U6）。

## 3. 系统思考检查单

每个节点一行白话说明，以及做决定时在这一层要回答的问题。白话说明取自两张图的卡片；定义依据先查本地三库（用户 intent `cace83d4`），引书名与页码，查不到页码的注「页码未核」。

### 3.1 往上：为什么要做

| 节点 | 白话 | 要回答 |
|---|---|---|
| 01 干系人 | 谁在乎这件事、谁承担后果 | 这个决定影响谁？谁会因此受损或得益？有没有漏掉承担后果却没被问到的人？ |
| 02 关注点 | 他们在乎什么、担心什么（TOGAF 10 Part 4 §3.1，PDF p.41：关注点是一个或多个干系人对系统的利益所在；ArchiMate 4 §6.3.1） | 这个决定回应哪条关注点？如果找不到，是关注点没登记，还是这个决定没有外部需要？ |
| 03 目标与战略 | 要去哪里、靠什么路线去（BMM：Goal、Objective、Strategy） | 它推进哪个目标？会不会让另一个目标变差？期限与度量是什么？ |
| 04 取舍决定 | 做什么、不做什么；不做也留痕（BMM：Assessment → Course of Action） | 这次是在「做」还是「不做」？放弃了什么、理由是什么、何时复审？ |
| 05 价值主张 | 我们对外承诺交付什么价值（BIZBOK v11，PDF p.145） | 它兑现或改变了哪条对外承诺？承诺变了，客户和合同那一侧谁要知道？ |

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
| 13 维度 | 按人、项目、客户、组织切开看（Kimball：Conformed Dimension，Kimball Group Reader 2nd Ed PDF p.49） | 切分口径变了吗？按当时值还是当前值读？ |
| 14 计算绑定 | 这次指标用哪版公式与口径 | 公式或口径版本要不要升？旧版本的结果怎么解释？ |
| 15 指标与评价 | 目标多少、实际多少、差多少 | 哪些指标会动？能否用指标证明这个决定回答了关注点（闭环回到 02）？ |
| 16 决策与行动 | 谁负责改、什么时候改完 | 开了哪些卡、谁负责？复核点在哪？ |
| C1 约束与认定规则 | 什么情况下必须拒绝（BMM：Business Policy、Business Rule；SBVR 1.5 §16.1.2 p.110） | 新增或改了哪些规则？每条规则在哪里强制（执行点）、用什么用例证明？有没有例外，例外怎么审批、何时到期？ |

### 3.3 往下：实现侧落到哪

| 节点 | 白话 | 要回答 |
|---|---|---|
| I01 子域 | 问题空间的业务划分 | 涉及哪个子域？核心、支撑还是通用的定位变不变？ |
| I02 限界上下文 | 一套统一语言成立的边界（DDD Reference 2015，页码未核） | 改动落在哪个 BC？同一个词在两个 BC 里意思不同吗？要不要新 BC？ |
| I03 聚合 | 聚合内不变量须一致的边界（DDD Reference 2015 的 Aggregates 一节为准，页码未核；补充：Evans《Domain-Driven Design》PDF p.74，提交聚合内任何对象的改动时，聚合的全部不变量都须满足） | 改的不变量在哪个聚合里守？会不会需要跨聚合一致（那要改成最终一致或改边界）？ |
| I04a 命令 | 改状态的请求：可校验、可拒绝、可重试 | 命令的入口（人、定时、外部）、幂等、失败提示变了吗？ |
| I04b 领域事件 | 已经发生的事实：不可撤、可重放 | 事件载荷要不要升版本？订阅方是谁，会不会被破坏？ |
| I05 接口契约 | 上下文之间的同步通道 | 契约是不是冻结对象（ADR-0067）？兼容新增还是破坏性变更？ |
| I06 表与数据结构 | 聚合与事实的持久化归属 | 改哪些表、哪些约束？迁移脚本、只追加规则、个人信息分级变不变？ |
| I07 代码模块 | 上下文在仓库里的物理落点 | 模块依赖边要不要增？会不会成环？api 方法数会不会超上限？ |
| I08 执行点 | 授权、不变量、数据约束在哪一处强制 | 每条新规则至少有一个执行点吗？库层拦不住的，进程内靠什么兜底？ |
| I09 验收用例 | 怎么证明它对（Given/When/Then） | 每条规则、每个命令有用例吗？要删或改的断言列出理由了吗？ |
| I10 架构决策记录 | 为什么这样划、这样做 | 取代或部分取代了哪些已采纳 ADR？字面冲突都列了吗？ |
| I11 读模型 | 给指标与查询用的投影 | 投影要不要重建？口径版本对得上吗？ |
| I12 防腐层 | 把外部模型翻译成我们的语言（DDD Reference 2015 为准，页码未核；补充：Millett & Tune 2015 PDF p.143） | 有没有外部系统受影响？只经防腐层进出吗？来源对照要不要改？ |

## 4. 用户已定（2026-10-21）

| 编号 | 事项 | 用户决定 |
|---|---|---|
| U1 | 能力与边界 | 按能力查漏，不按能力划 BC；能力 → 子域允许一对多，写「主要承载」；无子域承载的能力即缺口。图上「按能力划」改为「按能力查漏」 |
| U2 | 网络变数据 | 分批重启 SSOT；批次按已采纳 ADR 的节点被改频次排序；能自动生成的层优先；关注点、取舍、价值主张最后并标「人工维护」；先订正节点计数 |
| U3 | 流程层级 | 目录全留，L4/L5 标步骤级；L4/L5 必须能上卷到唯一的 L2/L3 父节点；分支与异常流挂多个父节点的，定「主路径归属」规则（规则由本会话起草、随第一批流程登记交审） |
| U4 | 适用范围 | 新起草的正式 ADR 起；试点 10 份覆盖 5 种类型；已采纳的不回补，但用于统计与校准 |
| U5 | 开卡 | 判为「改」必开卡，起草会话开卡、one-coordinator 派工 |
| U6 | 校验强度 | 试点期不查；试点后新起草的正式 ADR 硬失败 |
| U7–U11 | 附件位置、底座合入、前缀审批、catalog 补齐、重编号判定 | 按推荐执行（第 4 节原推荐） |

另交 one-glossary：分析「维度」与注册表 `DIMENSION_VERSIONED`（版本化主数据）同名异义。

## 5. 证据

- 两张图：`claude/one-coordinator-two-lanes` 的 `research/networks/01_registration-network-v7.svg`、`02_implementation-network-v2.svg`（文字已提取核对）。
- 盘点：sonnet 只读盘点 2026-10-21（28 类逐项，结果见第 1.1 节）；opus 首轮复审抽查 5 个节点与 3 条出处属实。
- 现有底座：`architecture/panorama/panorama.yaml`、`panorama_build.py`、`GAPS.md`、`OBJ46_MERGE_IMPACT.md`、`OBJ126_SHAREHOLDER_IMPACT.md`（本分支）。
- METHOD 第 3 节接缝 J1–J12、第 4 节对抗检查。
- 本地三库原文：TOGAF 10 Part 4 §3.1（Concern）；ArchiMate 4 Specification（2026）§6.3.1、§7.3.2；BIZBOK Guide v11.0 p.145（Value Proposition）；Evans《Domain-Driven Design》p.74（Aggregate 不变量）；SBVR 1.5 §16.1.2 p.110（Business Policy 与 Business Rule）；Kimball Group Reader 2nd Ed p.49（Conformed Dimension）；Millett & Tune 2015 p.143（Anticorruption Layer）。
