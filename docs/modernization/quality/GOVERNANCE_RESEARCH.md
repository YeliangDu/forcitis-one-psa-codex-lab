<!-- 来源：one-coordinator 的工件治理研究（2026-10-16，用户批准「派」）；原文照录，入库供六项工具（decide_sync、工作看板、规则索引、提议稿状态、画像时间戳、错题本）对照。 -->

# 工件治理研究（六类合订）

第 1–3 章：工作卡与派工、规则卡、用户决定链路；第 4–6 章：会话画像与台账、提议稿目录、错题本。

# 治理研究 A：工作卡与派工 / 规则卡 / 用户决定链路

基线 origin/main，网页访问日期均 2026-10-16；「未核实」指只见二手摘要、未读原文。

# 第 1 章 工作卡与派工

## 一、业界做法
- 看板方法六项实践：可视化、限制在制品、管理流动、策略显式、反馈回路、协作改进；看板须画真实流程，每环节写「拉取条件」。https://kanban.university/kanban-guide/ （Anderson 原书表述未核实）
- Scrum Guide 2020：DoD 是承诺，无 DoR（DoR 为社区做法）；「Product Owner 是一个人，不是委员会」。https://scrumguides.org/scrum-guide.html
- DRI：一事一个直接负责人。出处 Lashinsky 2011 等二手：https://www.bitesizelearning.co.uk/resources/directly-responsible-individual-dri-apple（GitLab 手册原文未核实）
- GitHub Issues 原生「blocked by / blocking」，看板显示 Blocked 图标。https://docs.github.com/en/issues/tracking-your-work-with-issues/using-issues/creating-issue-dependencies
- 要点：工作项单一来源；恰一个负责人；阻塞与前置是数据；准入与完成写成清单。

## 二、本项目现状
- 工作项散在五处：`phase1a/NEXT_BATCH_PLAN.md`（611 行，第 6、7、8 节为追加章节，约 130 张卡，现状要拼读 7.11 与 8）；`phase1a/cards/`（10 张，无状态/负责/前置字段）；`data/repair-cards/`；台账作战图；`status/units/`（28 个单元，061 一条覆盖整个下一批，10-15 后未改）。
- K-3b-4：只在 NEXT_BATCH_PLAN 第 520 行表里，「前置：R-1b P5 之后」，无负责人字段；P5 合并后没有机制发现它可开工。
- 派给已交接会话：`sessions/one-1a-next-3.md`「当前状态」仍写「P2 实现进行中」，`sessions/_retired.md` 只列 next-1、next-2，next-3 不在内；派工无「收件人在用吗」检查。
- 「并入、暂缓」只在调度本地 scratchpad 与台账叙述（如「暂缓：#96」）；仓库无字段。
- NEXT_BATCH_PLAN 近 30 小时冲突 9 次（`quality/PROPOSALS_2026-10-16.md`）。可复用：`build_all.py`、片段化惯例、`templates/WORK_CARD.md`。

## 三、差距
1. 无单一来源与状态机（待派/进行/阻塞/完成/并入/暂缓）。
2. 负责人非机器可读，不校验是否在用。
3. 前置是散文，算不出「可开工无人认领」。
4. DoR/DoD 只是模板标题。
5. 并入、暂缓无理由与出处。

## 四、推荐方案
1. 卡首加键值头（沿用 status/units 风格）：卡号、状态、负责会话、前置卡、并入目标、暂缓理由与出处；`build_all.py` 生成「工作看板」。现有卡补头；计划表里未开工的 K-3、F、R 卡建最小卡。做：one-quality 工具，调度一次性迁移。不需用户批，报一句。
2. `--check` 校验：进行中必有在用负责人；并入指向存在的卡；暂缓带理由与出处；前置存在无环；「待派且前置全完成」列入早报「可开工无人认领」（直接对应 K-3b-4）。做：one-quality。
3. 画像加「生命周期」字段（在用 / 已交接→接任），交接 PR 同改画像与 `_retired.md`，负责人指向非在用会话即失败。做：one-quality + one-rules 改 §2.1。用户批一次（改 PLAYBOOK）。
4. WORK_CARD 增 DoR/DoD 勾选行，lint 必填节。做：one-rules 改模板，one-quality 做 lint。
5. NEXT_BATCH_PLAN 冻结为历史，现状只看生成看板，台账作战图引用之。做：调度。

## 五、不建议做的
- 不引入 Jira，也不把约 130 卡整体迁 GitHub Issues：Issues 的好处是状态变更不用 PR（本项目 main 串行合并，一次状态变更一个 PR 太重），代价是双份来源；折中：状态变更随工作 PR 与文档合集走，摩擦大再评估。
- 不做燃尽图、故事点、优先级打分、WIP 硬限；不为每次派工另建片段。

# 第 2 章 规则卡

## 一、业界做法
- Business Rules Manifesto 2.0（BRG，2003-11-01）：规则是需求一等公民（1.1）、建立在事实与术语上（3.1）、与业务动机挂钩（8.1）。https://www.brcommunity.com/brg/BRManifesto.pdf （条号取自搜索摘要，未逐条核对）
- 双向可追溯：向前到设计/测试，向后到来源；无来源者为「孤儿」须复审。定义见 24765，29148 条文付费未读（未核实）。https://swehb.nasa.gov/x/iwD7
- Specification by Example（Adzic）：例子即规格，自动校验，活文档。https://www.infoq.com/articles/specification-by-example-book
- DMN 决策表：命中策略、完整性、重叠可机器校验；仅读厂商文档，OMG 原文未核实。https://www.trisotech.com/dmn-hit-policy-explained/
- 要点：一规则一现行口径；决定与规则双向挂接；规则到测试可核对。

## 二、本项目现状
- `BUSINESS_RULES.md` 315 张英文卡，取自老系统源码；`rules/*.md` 另有约 80 张新增（如 `TIMESHEET_ROUTING_RULES.md` RULE-388..392，「Review: No」「Tests: to be written」）。
- `RULE_REVIEWS.json` 仅 89 条结论（confirmed 48、wrong 41），272 张老卡无评审记录；`rules/review_revisions.py` 的追加修订（quote、source）设计好，但只能挂已有条目。
- 结论写在自由文本 note（「正确+修」…），不是结构化；实际口径散在 `process/1a/审批通用流程.md`、contract、intent，卡本身不变。
- RULE-272：卡仍写「驳回须填原因，除非节点把校验交给钩子」，无评审记录；流程文档 T10 引它，intent 2026-10-14-129 另定「钩子不做弹窗协议」；全部 intent 只有 13 个文件提到 24 条规则，无一提 RULE-272。用户今日决定与卡冲突，没有机制能拦。
- 追溯：`backend/src` 引用过 106 个规则号（main 代码 47），290 条从未被引用；流程文档引 113 条，其中 50 条无评审。无反向检查，无「被决定覆盖」状态。

## 三、差距
1. 无生命周期状态（老行为待评审 / 沿用 / 被决定覆盖 / 新规则 / 作废）。
2. 决定到规则无结构化链接。
3. 规则到测试单向不检查；评审覆盖率无人看。
4. 老卡直接当现行卡，与「老代码只作证据」相悖。

## 四、推荐方案
1. 生成「规则索引」（不手写）：每规则一行，列状态、现行口径出处、最近决定、测试引用数；状态取 RULE_REVIEWS 结论加卡头新字段。做：one-quality 生成，one-rules 定状态词表。不需批。
2. `new_intent.py` 加可选 `--rules=RULE-272,…`，片段记规则号，索引反查「被哪些决定改过」。做：one-quality。不需批。
3. 派工前校验：工作卡「依据」里的规则号在索引中为「老行为未评审」则不得派工，先评审（直接拦 RULE-272 类）。做：one-quality 校验，one-rules 补评审。不需批。
4. 追溯报告（只报不拦）：代码或测试引用的规则号须存在；P0 规则无测试时列入批次计划。守护改动单列 PR。做：one-quality。
5. 评审按「即将实现」排序补课，并入 one-rules 在途的「规则文档瘦身对照表」，评审结论仍由用户批（沿用决策页）。

## 五、不建议做的
- 不全量改写 315 张老卡：只在实现前评审时写新卡，旧卡标「老行为」。
- 不上 DMN 引擎；仅对少数决策表（费率、路由）做完整性检查。
- 不要求每规则都有 Gherkin 例子，只对 P0。

# 第 3 章 用户决定链路

## 一、业界做法
- 决策登记册：谁、何时、为何、选了什么。PRINCE2 7 无独立决策日志（二手）：https://open-exam-prep.com/study-guides/prince2-7-foundation/practices-risk-change-progress/management-reports-records ；PMBOK 对应定义未核实。MADR front matter 含 status、date、decision-makers：https://adr.github.io/madr/
- DACI：驱动者、唯一批准人、贡献者、知会者；决定后记录理由并通知。https://www.atlassian.com/team-playbook/plays/daci
- RAPID（Bain，HBR 2006）：核心是只有一个决定人；Bain 官页 404，角色说明为二手（未核实）。https://umbrex.com/?p=237129
- GOV.UK「检查答案」：提交前集中列出全部答案、可逐项改并回到本页、确认前交易未完成；「确认页」给参考编号与下一步。https://design-system.service.gov.uk/patterns/check-answers/ ；https://design-system.service.gov.uk/patterns/confirmation-pages/
- 要点：决定人唯一；整单一次提交；提交后回显并给编号；自动落档。

## 二、本项目现状
- 四套机制并存：收件箱页（`rules/decide/inbox/README.md`）；`rules/decide/`（sheets + `build_decision_page.py`）；`glossary/gate/`（自带生成器、导出、写回与页模板）；`architecture/decision-page/`（22 张 HTML、13 个 JSON，仅 12 张用共享答复库，其余为静态选择页）。
- 提交粒度是组：`rules/decide/README.md`「每组底部单独提交」，无整单回显；`export_answers.py` 对「提交后又改」的组直接失败。漏点一组即为草稿，会话读不到（A-2 即此）。
- 答复转 intent 靠人工 `new_intent.py`；收件箱 `intent` 字段只在「登记口头决定时必填」，页面答复事后补写无检查。
- 读取：须 ArtifactData 取两个集合再跑导出（`rules/decide/README.md` 第 4 步）；调度误报未答，原因是收件箱 `pending` 与确认页提交记录是两处数据，无对账。

## 三、差距
1. 4 套模板、3 套导出脚本，格式不一。
2. 无整单提交、回显与编号。
3. 页面答复到 intent、规则卡、ADR 无自动流转与「已转写」标记。
4. 收件箱与确认页状态不对账。

## 四、推荐方案
1. 新单子统一用 `rules/decide/sheets/*.json` 格式与生成器（已有 info、side、basis、practice），gate 与 decision-page 旧页冻结不迁。做：one-quality（只加不删），one-rules 为格式负责人。不需批。
2. 整单提交：页脚「检查答案」汇总全单（按推荐没动过、改选、暂不定各几题），逐项可改，一次「确认提交」产出单一提交记录与编号；有未提交组时按钮灰掉。做：one-quality 改页模板（固定 contract 版本，手机实看）。用户验收看一次。
3. 一条龙 `decide_sync.py <单子>`：读两个集合 → 导出 → 对账（各组已提交、无提交后又改）→ 生成 intent 片段（所选项文字+题号）→ 收件箱事项置 decided 并写 intent 文件名；`--check` 报「pending 但单子已提交」。做：one-quality 写，调度日常跑。不需批。
4. intent 片段头加可选「单子」「题号」「影响的规则/ADR」，供规则索引与 ADR 治理反查。做：one-quality。
5. 调度向用户报「待答」前先跑 `--check`，纳入早报。做：调度。

## 五、不建议做的
- 不引入 RAPID/DACI 角色矩阵：决定人只有用户，保留 source 与 urgency 即可。
- 不迁移全部 22 张旧确认页，不自建投票或评论。
- 不让页面直接写仓库或自动合并；转写到规则卡、ADR 仍由会话执行并复核。

# 治理研究 B：会话画像与台账、提议稿、错题本（只读调研，2026-10-16，基线 origin/main d150fc33）

通用结论沿用上一轮：单一来源、显式生命周期、单一负责人、可追溯、自动检查优先、新鲜度。

## 第 4 章 会话画像、调度台账、交接文件

### 一、业界做法（均访问于 2026-10-16）
- Docs as Code（Write the Docs，https://www.writethedocs.org/guide/docs-as-code/）：文档用与代码同样的版本库、评审、自动检查，功能合入时同步写；该页不讨论新鲜度。
- 新鲜度（Google，《Software Engineering at Google》第 10 章 https://abseil.io/resources/swe-book/html/ch10.html）：文档必须有负责人，否则变陈旧；每份文档带「新鲜日期」（最后复审日），约三个月未动自动发提醒；改日期要过代码评审；文档不再适用时删除或标「已过时」并指向现行文档；多份冲突时指定一份为准。
- Diátaxis（https://diataxis.fr/）：按读者需要分教程、操作指南、参考、解释四类，不混写。
- SRE 交接（Google SRE book 第 14 章 https://sre.google/sre-book/incident-response/）：指挥权交接要明确、当面确认、通知所有人。班次交接文档的原文未核实（只见二手模板）。

### 二、本项目现状
- 有：画像 12 份（`docs/modernization/sessions/*.md`，12 字段）、`build_sessions.py` 校验字段齐全、路径存在；`_retired.md` 3 行；`COORDINATOR_LEDGER.md`（111 行）；交接文件两处：`phase1a/HANDOFF_*.md`（4 份）与 `architecture/proposals/HANDOFF_2026-10-1{5,6}.md`。
- 台账末次提交 2026-10-16 07:03，正文自称 07:05，现已约 6 小时；仍写「#197 在队列」「R-1b P5 实现中、P4 已合」，而 P5 已合（#310，见 HANDOFF_one-1a-next-4）。
- 画像「当前状态」无时间戳：`one-1a-next-3.md` 写「P2 实现进行中」（最后改于 10-15 14:56，该会话已交接）；`one-data.md` 写「待用户新开会话后认领」（已在岗并有错题本）；`one-1a-par.md` 写「等 README 格式」（早已出）。`_retired.md` 的 next-1、next-2 仍「待归档」，next-3、next-4 已交接却仍占画像。`build_sessions.py` 对这些全不报。
- 交接文件无统一模板：命名两套（按会话名 / 按日期），无状态、无写作时间、无基线提交；开头写「上下文约 75%」「76%」「94%」——这是写文时的触发原因，新会话读到就成了旧数。`HANDOFF_2026-10-15.md` 自己写「ARCH_CONTEXT 现状段已过期」，`ARCH_CONTEXT.md` 顶部再补一句「接续必读」覆盖——补丁叠补丁。
- 「当前状态」同时散在画像、台账、ARCH_CONTEXT 现状、status/units、session-log，五处。

### 三、差距
缺负责人之外的「复审日期」；缺「已交接则必须退役」的检查；交接文件缺状态与消费标记；现状多源。

### 四、推荐方案
1. 画像「当前状态」强制以 `YYYY-MM-DD HH:MM` 开头；`build_sessions.py`（one-quality）超 24 小时标「过期」写进 SESSIONS.md，状态含「已交接」而仍在 sessions/ 时校验失败。无需用户批。
2. 上下文文档（台账、ARCH/OPS/GLOSSARY/QUALITY_CONTEXT）文首一行 `复审：日期｜负责：会话名`；注入钩子（one-quality）打印「已 N 小时未复审」，台账只增不写重复事实，PR 号只留链接。调度流程，无需批。
3. 交接报告统一模板（one-rules/调度写进 SESSION_PLAYBOOK TEAM-01–13）：固定目录与命名 `HANDOFF_<原会话>_<日期>.md`；头部 `写于/基线提交/状态(有效|已接手)`；不写上下文百分比，只写触发原因；接手会话开场第一步用 gh/git 核对「在途」并在头部改状态。需用户知晓即可（规范小改）。
4. 交接后同一 PR 更新画像状态与 `_retired.md`（调度）；现有 4 份旧交接标「已接手」。
5. 现状唯一来源：台账只放作战图与链接，会话状态只在画像（§2.1 第 16 条本意），ARCH_CONTEXT 现状段改指向画像。one-architecture 执行。

### 五、不建议做的
不建 Docs-as-code 站点或 Diátaxis 重排（只借「参考/一次性交接/解释」分开的思想）；不做邮件提醒与定时巡检（用户已停）；不照搬值班轮换与当面确认仪式；不给每份文档加多级评审人。

## 第 5 章 提议稿目录 `docs/modernization/architecture/proposals/`

### 一、业界做法（访问于 2026-10-16）
- Rust RFC（https://github.com/rust-lang/rfcs/blob/master/README.md）：以 PR 提交；子团队提议处置（合并/关闭/搁置）后进入十个日历日的最终评论期；合并后即「生效」，每份生效 RFC 有跟踪 issue；小修用后续 PR，实质变更须新 RFC 并在原文加注。
- Python PEP 1（https://peps.python.org/pep-0001/）：状态为 Draft、Accepted、Provisional、Final、Active、Deferred、Rejected、Withdrawn、Superseded；Status 为必填头字段，另有 `Superseded-By`、`Replaces`；已采纳、终结、被拒、被取代的 PEP 一般不再实质修改；无进展可转 Deferred。
- IETF RFC 2026（https://www.rfc-editor.org/rfc/rfc2026.html）：被取代或过时的规范标 Historic；同一成熟度停留 24 个月由 IESG 复查，之后每 12 个月再查；头部用 `Obsoletes` 记取代关系。
- Google 设计文档（Malte Ubl，2020-07-06，https://www.industrialempathy.com/posts/design-docs-at-google/）：设计变更用链接的补充文档，文档会与现实漂移；文中无正式状态标签（原文未写，不作推断）。

### 二、本项目现状
目录共 47 个文件：41 个 .md，6 个脚本与 YAML。文首前 8 行含「状态」的 27 份，没有的 14 份（34%）；已有的状态也是自由文本，至少 5 种写法（「研究提议，待用户裁定」「草案」「提议稿」「记录」「已由用户确认」）。初步分类（约数）：
- 已成文到 ADR，仍留在目录且文首仍写「提议」「待用户定」：`ADR_DRAFT_0032_REVISION.md`（实为 ADR-0044，文件名与内容不一致）、`ADR_DRAFT_0045_NO_REOPEN.md`、`ADR_DRAFT_DATA_CLASSIFICATION.md`（ADR-0043）、`ACL_BOUNDARY.md`（ADR-0039）；`FD46_RECOMMENDATION.md` 自注「移入 adr/src 前删除」（ADR-0045/0046 已引 FD-46）。共 5 份。对照做法：ADR-0048 草稿采纳后已移出，目录里没有它。
- 评审输入，已并入 ADR-0041：`FIT_REVIEW_*` 3 份。
- 已被取代或已并入：`PRESALES_AGGREGATES`、`PRESALES_INFO_MAP`（已自标被全企业稿取代）、`METHOD_V3_DRAFT`、`FRAMEWORKS_V3_ADDITIONS_DRAFT`、`AEC_*` 5 份（仍写「待用户裁定」）、`REVIEW_SEGMENTS`、`ENTERPRISE_INFO_MAP_QUESTIONS`，约 11 份。
- 过时：两份 `HANDOFF_*`（前者自称现状段过期，后者被 ARCH_CONTEXT 称「只作历史」）。
- 现行草稿或持久证据：约 20 份（METHOD_V4、FRAMEWORKS_V4、ENTERPRISE_*、STEP1–3/6A、取证报告等）。
即约 21 份（五成）的文首状态与实况不符或缺失，且无目录索引。

### 三、差距
无受控状态词；无「取代/被取代」字段；ADR 采纳时不要求处置提议稿；无新鲜度。

### 四、推荐方案
1. 文首一行 `状态：<草稿|评审中|已确认|已成文→ADR-NNNN|已并入→文件|已取代→文件|历史记录>｜日期｜负责：会话名`（Status 取自 PEP，Superseded-By 取自 PEP/RFC）。规范由 one-architecture 定，调度写进 README；无需用户批。
2. one-quality 加检查：所有 .md 前 8 行有合法状态；「已成文→ADR-N」所指 ADR 须存在且已采纳；「草稿」超 14 天无提交告警（IETF 24 个月复查的缩小版）。
3. ADR 采纳的同一 PR 里，对应提议稿改状态并移入 `proposals/archive/`（按 ADR-0048 先例；调度写入 §12 清单）。
4. one-architecture 开一个清理 PR：补 14 份标注，改正上述 5 份，旧稿与交接稿移 archive；目录 README 索引由脚本生成（仿 adr/README.md）。
5. 目录 README 写明：可从 git 历史找回，故 archive 只保留仍被引用的文件。

### 五、不建议做的
不引入 RFC 编号、最终评论期、PEP-Delegate、九种状态全套（取 7 种足矣）、跟踪 issue（ADR 已承担）；不对旧稿逐份评审后再归档，直接按分类处置。

## 第 6 章 错题本 `docs/modernization/quality/defect-bank/`

### 一、业界做法（访问于 2026-10-16）
- Google SRE 无责复盘（https://sre.google/sre-book/postmortem-culture/ 与 https://sre.google/workbook/postmortem-culture/）：聚焦成因不追人；未经评审的复盘等于没写；行动项进集中缺陷库、每项有唯一负责人、有优先级、有可验证的终态（避免「改进」类措辞）；复发时回查「上次行动项为何没落地」；每月跨部门复查复盘模板。
- CAPA（ISO 9001:2015 第 10.2 条）：标准原文需付费，以下取自咨询机构转述（未核实原文，如 https://blog.auditortrainingonline.com/blog/iso-9001-clause-10.2-nonconformity-and-corrective-action）：先纠正，再评估是否消除原因，查类似不合格是否存在，实施后复查有效性，并保留记录；2015 版取消独立预防条款。
- 防错（poka-yoke，Shingo 20 世纪 60 年代）：让错误不可能发生或立即可见，分「控制型」「警示型」（二手来源，如 https://www.techtarget.com/enterprise-software/definition/poka-yoke；ASQ 原页未核实）。
- 5 Whys：相传由大野耐一推广，Toyota 内部也批评其过浅、结果不可重复（二手来源 https://en.wikipedia.org/wiki/Five_whys；丰田官网未查到，未核实）。

### 二、本项目现状
- 6 本错题本（one-quality 9 条、one-data 17 条、one-1a-next-4 4 条、one-architecture 4 条、one-glossary 8 条、one-rules 6 条），共 48 条。12 个画像中 6 个没有册：one-coordinator、one-ops、one-1a-par（画像写「待办」）、one-1a-next-3/5、one-glossary-b1。
- 两种格式：README 格式（编号、日期、现象、根因、拦下于、漏过于、新增检查项、状态）仅 one-quality、one-data（26 条）；其余四本（22 条）是各自的表格，且都写「格式待 README 定稿后对齐」，README 已于 10-16 定稿，至今未对齐。表格册无「拦下于/漏过于」；只有 one-rules 有状态列。有状态字段的 32/48（67%）。
- 26 条有「新增检查项」，但多指向「画像工作守则」「清单条目」这类软约束（DATA-001、002、005）；机械落实的如 QUAL-001/008（precheck、自测）。「待落实」4 条（QUAL-009 等），无负责人与日期的写法不一致。无任何脚本检查册本身；`review-briefs/README.md` 提到固定 contract 0.2.80，未进发布脚本。
- 跨会话重复（同一类错）：
  1. 转述或断言未核原文：DATA-001、002、005、013（子代理结论）、RB-02（调研转述写进依据）、RB-04（套用相邻题答复）、G-06（凭印象写数）、G-07、D2，涉及 4 个会话，约 9 条；主拦截者是调度抽查。
  2. 只测局部或先推后测：G-05、G-01、G-02、QUAL-008（同一类词表检查，两会话三次）、D3、D4、QUAL-002、QUAL-005，约 8 条。
  3. 发布在线页未固定 contract、未真实渲染或落库：one-architecture（ADR-0046/0047 页打不开）、RB-01（同一天、同一版本 0.2.82 白屏）、G-08，3 个会话，各自入册、各自补守则。
  4. 把授权或 ADR 读宽：D1、D2、RB-04、G-03、one-architecture 越步，约 5 条。
  5. 提交卫生、沟通噪声：各 1–2 条。

### 三、差距
无统一字段；无类别，无法发现重复；无「落实位置可定位」的校验；无有效性复查；缺册会话正是易犯类 1 的调度本身。

### 四、推荐方案
1. 统一为 README 格式，加「类别」（受控词：转述未核、局部测试、先推后测、授权读宽、发布未验、提交卫生、沟通、其他）；22 条表格册一次性迁移（sonnet 子代理做，各会话复核）。one-quality 执行；无需用户批。
2. one-quality 写 `defect_bank_check.py` 接 precheck 与 build_all：字段齐全；状态限三值；「已落实」必须带存在的路径；「待落实」必须带负责人与日期，超 7 天告警。
3. 重复升级：同类别出现于两个以上会话，即由 one-quality 出一条机械守护（防错）。例：发布在线页的统一脚本（固定 contract、发布后读回并写入自测）；派工与回报模板加「证据位置 file:line」栏，缺栏则评审模板判不合格。落实后下一次同类产出抽查一次（CAPA 有效性复查）。调度流程。
4. 补开 one-coordinator、one-ops、one-1a-par 的册；无责，只记机制。
5. one-quality 月汇总改为出「重复类」清单，随台账链接。更改 §11 第 11 条只是补充，告知用户即可。

### 五、不建议做的
不建 FixIt 周、燃尽图、绩效排名；不强制五层 Why（只要求到「可防的那一层」，现 README 已有）；不做 CAPA 审批表与根因会议；不为每条错都开新守护，只对重复类与高影响类做。
