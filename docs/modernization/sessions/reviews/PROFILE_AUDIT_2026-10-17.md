# GOV-1017-COO-03 [治理·跨域] 全部会话画像完整性审查（只读）

审查日期：2026-10-17，Europe/Paris。Codex 起草；按工作卡只写本文件，不修改画像、规则、钩子、任务登记或其他仓库文件。

## 1. 结论与证据边界

13 份画像的 12 个必备节均齐全、有序、非空，全部通过现有结构解析；但没有一份完整写入本卡要求的七项全员新规。优先处理：one-merge 的手工合并表述、架构目录独占重叠、恢复时注入旧画像与旧上下文、旧状态和旧派工策略。

- **已核实的仓库快照**：起始本 worktree 为 detached HEAD，`HEAD = origin/main = 23bf4216e9b567c605257a901cc5231a5036a7d6`；不联网、不 fetch。以下路径与行号固定此基线，不能证明审查时 GitHub 的最新状态。13:20+02:00 共享 `origin/main` 已由外部更新到 `72012f701c97587985c27f06be3adcd914284848`；增量核对画像、钩子、校验器、所审上下文与 tasks 路径均无变化，范围内仅手册有变化，且新手册与卡提供的最新副本逐字一致，故审计结论适用；本 worktree HEAD 未改。
- **审查对象**：main 的 `docs/modernization/sessions/one-*.md` 共 10 份，加 `.codex-inputs/one-arch-{finance,revenue,people}.md` 共 3 份。`_retired.md` 是退役名录，不计画像；`sessions/reviews/` 不是画像目录。三份输入副本均与各自会话 worktree 当前画像逐字一致。
- **规则基线**：根 `AGENTS.md`、完整 `CLAUDE.md`、`CODING_STANDARD.md`、`NAMING_STANDARD.md`；流程以卡指定 `.codex-inputs/PLAYBOOK_latest.md` 为准。该输入比起始 main 新增了关闭 stdin 与「任务命名与登记」；结束回读时这些规则已进入上述新 main，但 tasks 总表的实现仍不能由文字证明。
- **今日事实**：dev 暂停、strict 已关试行、三组骨架已铺完、ADR-0052/54/55/56 已采纳、命名在 N9→N7，均是 `CARD.md:9` 给定的审查基线。dev 与 strict 另有 main 台账 `COORDINATOR_LEDGER.md:11,18,22` 支持；四份 ADR 的「已采纳」另核对了本地 `claude/one-architecture-adr-governance` 的 `adr/src/ADR-0052.md`、`0054.md`、`0055.md`、`0056.md` 第 3 行。N9→N7 未另作运行态核验。
- **证据等级**：下文文件与行号为已直接读回；路径、结构、会话识别为本地可复现检查；任务卡给定事实单独标明；PR、云环境、后台任务、在线收件箱的当前实况未核验。未见画像写明规则，只证明画像覆盖不足，不能据此推断该会话实际违规执行。

文中 `sessions/`、`architecture/`、`quality/` 等简称均相对 `docs/modernization/`；分支画像行号对应 `.codex-inputs/` 输入副本。

## 2. 总表：会话 × 问题

「字段」按 `build_sessions.py:24,47–83`；「路径」只计钩子实际解析的上下文引用；「状态」同时检查文字与时间戳，不把缺时间戳等同于事实错误。

| 会话 / 会话码 | 字段 | 上下文路径对 main | 今日规则缺口 | 状态与主要问题 |
|---|---|---|---|---|
| one-1a-next-5 / NX5 | 12/12 PASS | 2/2 存在 | R1–R5、R7 缺 | 状态只到 10-16、无时分；分支画像已更新为合集 #405，main 仍列 8 个旧 PR；独占登记文件范围太宽 |
| one-1a-par / PAR | 12/12 PASS | 0 个可注入路径 | R1–R5 缺；R7 不完整 | 无时间戳、长队列旧状态；输入仍找已退役 next-3，工作守则仍找 next-4；无标记且仅靠分支名识别 |
| one-architecture / ARC | 12/12 PASS | 4/4 存在 | R1–R6 缺；R7 不完整 | 04:47 仍写改名对照表、待定层；未反映已采纳四 ADR 与 N9→N7；独占整个 architecture 与子组、quality 相交 |
| one-coordinator / COO | 12/12 PASS | 2/2 存在 | R1–R7 均未完整写入 | 状态「在岗」无明确错误，但规则、台账和画像脱节；「合并→main」与只批准入队的职责表述不一致 |
| one-data / DAT | 12/12 PASS | 2/2 存在 | R1–R5、R7 缺 | 无规范时间戳；仍写上下文 76% 快照、门禁「用户确认后生效」；需对齐新 ADR 下迁入与人事版本口径 |
| one-glossary / GLO | 12/12 PASS | 2/2 存在 | R1–R5 缺；R7 不完整 | 无时间戳；「首件事」与当前命名 N9→N7 不一致；明确写执行 sonnet/haiku；上下文内还留旧 PR 与旧积压清单 |
| one-merge / MRG | 12/12 PASS | 2/2 存在 | R1–R7 缺 | 状态仍「待用户新开」；第 46 行允许手工 gh pr merge，与队列规则冲突；注入台账全文含旧队列重启命令 |
| one-ops / OPS | 12/12 PASS | 2/2 存在 | R1–R7 缺 | 状态仍 rc.1、首次 dev 待批准，与 rc.6 后暂停冲突；事件触发未加暂停条件；取证、部署不能交 Codex |
| one-quality / QUA | 12/12 PASS | 2/2 存在 | R1–R7 缺 | 07:04 状态本身不宣称 strict 开启；但恢复文档仍写「保留 strict」「关 strict 前提」，与试行冲突 |
| one-rules / RUL | 12/12 PASS | 2/2 存在 | R1–R5、R7 缺 | 状态停在 10-15，无时分；三张旧页待答与今日 ADR 决策是否一致需逐项重算，不可猜测已全部解决 |
| one-arch-finance / AFI | 12/12 PASS | 0/1；1 个仅分支 | R1、R2 不完整；R3、R5、R7 缺；R6 不完整 | 骨架完成与卡一致；无时分；「首轮后执行 sonnet」与 Codex 优先并存；接续 NOTES 等写在状态，不会被钩子注入 |
| one-arch-revenue / ARE | 12/12 PASS | 0/2；2 个仅分支 | R1–R5、R7 缺；R6 不完整 | 骨架完成与卡一致；10-16 状态无时分；旧 SD 待答需对照四 ADR；仍以 ADR-0050/51 为主要输入 |
| one-arch-people / APE | 12/12 PASS | 0/1；1 个仅分支 | R1 有；R2 不完整；R3–R5、R7 缺；R6 不完整 | 骨架完成与卡一致；无规范时间戳；已写 Codex 命令但未关 stdin，未指定 run_in_background；安全边界须保留给 Claude |

## 3. 必备字段、上下文路径与校验器覆盖

### 3.1 必备字段

13/13 的类型、职责、独占、不碰、输入、输出、触发与节奏、自动化、上下文文档、交接条件、权限边界、当前状态均通过；无额外二级标题、重复节、空节或顺序错误。「工作守则」在 `###` 或权限边界正文内，不会破坏结构。无需新增一个与「权限边界」并列的 `## 工作守则`。

检查代码只导入 `parse_profile`、`check`、`freshness` 与钩子 `context_docs`，未调用写 `SESSIONS.md` 的生成入口。main 10 份逐份用 main 树作 `exists` 判断均通过；三份分支画像放到 main 树校验均因缺分支路径失败，这不等于其作者 worktree 缺文件。

9/13 状态缺规范 `YYYY-MM-DD HH:MM[+HH:MM]` 前缀：NX5、PAR、DAT、GLO、OPS、RUL、AFI、ARE、APE。ARC、COO、MRG、QUA 在本次约 13:15+02:00 检查时均未超过 24 小时。MRG 虽时间戳未过期，文字已过时，说明仅靠时效守护不够。`REQUIRE_TIMESTAMP = False`（`build_sessions.py:27`），目前缺时间戳只告警。

### 3.2 main 上确实存在的全部上下文引用

| 会话 | 「上下文文档」节实际解析结果（均为仓库根相对 .md 路径） |
|---|---|
| one-1a-next-5 | `docs/modernization/phase1a/HANDOFF_one-1a-next-4.md`；`docs/modernization/phase1a/HANDOFF_one-1a-next-5.md` |
| one-1a-par | 空列表；第 45 行没有反引号 .md 路径 |
| one-architecture | `docs/modernization/architecture/ARCH_CONTEXT.md`；`docs/modernization/architecture/FRAMEWORKS.md`；`docs/modernization/architecture/METHOD.md`；`docs/modernization/sessions/one-architecture.md` |
| one-coordinator | `docs/modernization/COORDINATOR_LEDGER.md`；`docs/modernization/sessions/one-coordinator.md` |
| one-data | `docs/modernization/data/DATA_CONTEXT.md`；`docs/modernization/sessions/one-data.md` |
| one-glossary | `docs/modernization/glossary/GLOSSARY_CONTEXT.md`；`docs/modernization/sessions/one-glossary.md` |
| one-merge | `docs/modernization/sessions/one-merge.md`；`docs/modernization/COORDINATOR_LEDGER.md` |
| one-ops | `docs/modernization/phase1a/infra/OPS_CONTEXT.md`；`docs/modernization/sessions/one-ops.md` |
| one-quality | `docs/modernization/quality/QUALITY_CONTEXT.md`；`docs/modernization/sessions/one-quality.md` |
| one-rules | `docs/modernization/rules/RULES_CONTEXT.md`；`docs/modernization/sessions/one-rules.md` |

合计 20 次引用、19 个不同路径，均经 `git ls-tree -r --name-only origin/main` 核实；无不安全路径。PAR 是工作会话，现有校验器允许没有画像外上下文，不能把它写成结构 FAIL；但恢复时只有画像中的旧队列，属于实际恢复信息不足。

### 3.3 main 不存在、各自分支存在的全部引用

| 画像与行号 | 上下文路径 | main | 本地分支 HEAD / worktree |
|---|---|---|---|
| AFI:45 | `docs/modernization/sessions/one-arch-finance.md` | 不存在 | `claude/one-arch-finance-closeout` 已跟踪且文件存在 |
| ARE:49 | `docs/modernization/architecture/proposals/closeout/revenue/README.md` | 不存在 | `claude/one-arch-revenue-closeout` 已跟踪且文件存在 |
| ARE:50 | `docs/modernization/sessions/one-arch-revenue.md` | 不存在 | 同上 |
| APE:49 | `docs/modernization/architecture/proposals/closeout/people/README.md` | 不存在 | `claude/one-arch-people-closeout` 已跟踪且文件存在 |

另：APE 自身画像也不在 main，但它没有在「上下文文档」节自引，所以不计入上述 4 个引用；三份分支画像均尚不属于 main 的 10 份画像。

不能将「只在分支」统一判为链接失效。钩子把 main 与当前 worktree 的画像名取并集，main 缺画像时回退工作区，故三组在各自 worktree 可识别并读到文件；换到仅 main 的新 worktree 则没有这些画像与草稿。建议把画像及最小恢复说明独立作为治理文档合入，设计提议仍按既定里程碑留分支，不借此次审计要求提前合设计稿。

### 3.4 容易漏掉的恢复行为

- `session_context.py:20–22` 只读 `## 上下文文档` 节内反引号括起、没有空白、以 `.md` 结尾的路径。放在「输入」「当前状态」「自动化」的路径均不注入。AFI:60 的 `finance/NOTES.md`、`STAGES.md`、`OBJECTS.md` 因位置不对不会注入。
- `session_context.py:84–88` **画像 main 优先**，即使作者分支已更新，也仍注入 main 的旧版。已核实 NX5 分支画像末行已写 #405，main:56 仍列八个旧 PR；这是可复现的遮蔽，不能只建议作者更新分支后就算解决。
- `session_context.py:99` **上下文工作区优先**；所以 main 新画像可能和旧 worktree 上的上下文混读。检查 main 路径存在不能证明注入内容也是最新。
- MRG:53 在台账路径后写「合并」「合并队列」两节，钩子实际上注入全文，无法只选小节。该台账:55–59 仍含旧 `mq_run2.sh`、旧队列、旧 rc.2 关键路径，与同文件:11,22 的新状态矛盾。

## 4. 今日全员新规逐份覆盖

R1＝Codex 优先且 `gpt-6.1-sol xhigh`；R2＝Bash `run_in_background`、本会话可见、完成自动唤醒、不脱离会话与不轮询；R3＝`< /dev/null`；R4＝任务号、任务名、tasks 片段、后台 description、PR/提交/报告带号；R5＝跨会话派工用唤醒式消息；R6＝所有待用户定事项进收件箱；R7＝只报关键节点、不回纯确认、不广播例行状态。

符号：✓ 明确写入；△ 只覆盖一部分或仅泛引规则；— 未明确写入。R5 的具体机制取自 main `COORDINATOR_LEDGER.md:9`：派工用 `mcp__ccd_session_mgmt__send_message` 开启对方回合；普通跨会话 `SendMessage` 曾停在未读。该细则尚未在最新手册 §11 明确区分，不能将其缺失伪称为某条已经写明的手册原文。

| 会话 | R1 | R2 | R3 | R4 | R5 | R6 | R7 | 部分覆盖证据 |
|---|---|---|---|---|---|---|---|---|
| one-1a-next-5 | — | — | — | — | — | ✓ | — | :50 明确经收件箱 |
| one-1a-par | — | — | — | — | — | ✓ | △ | :63,65 有验证与收件箱，无关键节点消息完整规则 |
| one-architecture | — | — | — | — | — | — | △ | :66–67 限长、直连，但无收件箱与节点约束 |
| one-coordinator | — | — | — | — | — | △ | △ | :10 泛引 §11；:35 仍写清单交用户；:45 有只发批准与去向 |
| one-data | — | — | — | — | — | ✓ | — | :41 明确收件箱 |
| one-glossary | — | — | — | — | — | ✓ | △ | :67,70–71 有少提醒、消息限长，未写不回纯确认与例行状态不通知；:73 仍写 sonnet/haiku 执行策略 |
| one-merge | — | — | — | — | — | — | — | 画像队列 Monitor 不是 Codex 的 run_in_background，不能据此算 R2 |
| one-ops | — | — | — | — | — | — | — | :57 仅写须问用户，未写收件箱 |
| one-quality | — | — | — | — | — | — | — | 后台 nightly 巡检不是 R2；:73 仅限制容器 |
| one-rules | — | — | — | — | — | ✓ | — | :66 写只有用户事项走收件箱，但要求技术协调抄送与最新少转述规则不一致 |
| one-arch-finance | △ | △ | — | ✓ | — | △ | — | :56 任务规则完整；:60 有 Codex 优先与 run_in_background，缺模型强度和完整可见约束；:53 只交 architecture 合页 |
| one-arch-revenue | — | — | — | — | — | △ | — | :35,58 只交 architecture 合页，未写登记收件箱 |
| one-arch-people | ✓ | △ | — | — | — | △ | — | :58 有模型强度与不轮询，但没有显式 run_in_background / 可见要求；:57 只交 architecture |

明确缺口：R3、R5 为 13/13；R4 仅 AFI 写全；R1 只有 APE 写全。现有结构校验器不检查七项规则，结构 PASS 不代表这张矩阵通过。

## 5. 当前状态与今日事实对照

1. **dev 暂停**：OPS:61 的「首次 dev 部署待门禁解除与用户批准」已被台账:18 的 rc.6 后暂停取代；OPS:36 不能把 main 合入视为恢复 dev 的授权。PAR:9,30 的部署前卡与 rc.2 触发也应改成待调度重排，不据此再催部署。
2. **strict 已关试行**：画像中最直接的风险是 MRG:46 的手工合并说法；QUA 状态未明确说 strict 开，不能误报其状态文字有该矛盾。但其注入的 `QUALITY_CONTEXT.md:16,51,66` 与 MRG/COO 共用台账的旧合并节均保留相反口径。时间基线为试行至 10-19，main 因并行合入变红一次即恢复；这里只复述已读台账，未回读远端分支保护。
3. **三组骨架已铺完**：三份分支画像均已记录完成，不应再判其「骨架未做」。ARC 的主线与产出说明仍需从铺骨架更新到接缝、命名、采纳 ADR 回写；COO 注入台账:91 还把「三组流程铺开」列暂缓，与任务卡给定事实冲突。
4. **ADR-0052/54/55/56 已采纳**：三组输入仍主要列 ADR-0050/51；PAR 的 A8 同步相关旧待办、DAT 的迁入/版本化口径、RUL/AFI/ARE 的财务待决应逐条按四 ADR 重算。不能把历史实现已存在写成目标方案仍被批准，也不能未经卡片就删除历史代码。已采纳不等于实施完成，不等于准许部署。
5. **命名 N9→N7**：GLO:77、ARC:72 未写当前接续点。AFI/ARE/APE 需挂接该阶段的用语与接缝输入，不能自行重启骨架或另出确认页。N9→N7 按卡给定事实使用，未声明 N9 已验收或 N7 已运行。
6. **其他可证过时项**：NX5 main 仍为八个原 PR，分支画像已写并入 #405；MRG:67 仍待开会话，而本地已有带 MRG 标记的 worktree 及 train-5 分支。PAR 状态列 #291、旧队列，COO 当前台账:29 记录的在途已是 ADR-0034-IMPL 等；只能判需重做在途清单，不从本地文本推断这些 PR 今天的远端状态。

## 6. 画像间冲突、会话码与识别

### 6.1 权责交叉

| 问题 | 直接证据 | 判定与建议归属 |
|---|---|---|
| architecture 大目录独占与三组子目录相交 | ARC:18；AFI:13、ARE:17、APE:17 | 明确的路径独占相交，但三组已有委派背景，不能据此认定发生抢写。ARC 独占列减去 closeout 三组片段目录，三组仅写各自片段，正式 SSOT 仍由 ARC 整合 |
| architecture 与 quality 检查脚本相交 | ARC:18 的整个 architecture；QUA:35 的 `architecture/decision-page/check_pages.py` | 明确重叠；现有具体认领归 QUA，ARC 的目录独占显式排除此文件。新增架构语义检查与通用题面检查按卡分开 |
| NX5 的 backend/architecture 登记范围与其他角色交叉 | NX5:14；PAR:16 的 list-paging-gaps；DAT:22 的签发；ARC:25 的只引用 | 潜在并发写入边界过宽；不是已证实冲突。改为本卡列明的登记行/构件，DAT 只签发结论，不包揽文件写权 |
| 数据设计初稿、最终签发与规则裁定可能混同 | AFI:29–30、ARE:34–35、APE:33–34；DAT:39；RUL:18 | 现有初稿→复核链基本正确。补明三组无签发权，DAT 签数据，RUL 管业务规则，用户定财务口径，ARC 管接缝 |
| coordinator 与 merge 的合并表述 | COO:17,34,45；MRG:18,22；QUA:33 | 批准、运行、源码三权已有主分工。修 COO:34「合并→main」和 QUA:33「运行由 merge、coordinator」，统一为 COO 只批准、MRG 运行、QUA 维护源码 |
| 常驻名额文字与现有类型冲突 | 最新手册:259 只留 COO/OPS/GLO/ARC；DAT/QUA/RUL/MRG:5 均为长期 | 规则与画像不一致，不能由审计自行关闭或降级会话。由 COO 明确长期职责的现行例外或按批次工作会话管理，并同步手册与画像 |
| 沟通规则对冲 | RUL:66 要技术协调抄送；最新手册:254 不经调度转述、也不抄送 | 删除常规技术抄送要求；影响排期、需用户决定才向 COO 报关键节点 |

### 6.2 会话码

总表列出的 13 个显式会话码（NX5、PAR、ARC、COO、DAT、GLO、MRG、OPS、QUA、RUL、AFI、ARE、APE）唯一，与最新手册:149 枚举一致；本卡 `GOV-1017-COO-03` 合乎枚举。AFI:56 也正确。

但手册同句「去掉 one- 的前三字母大写」无法推导 AFI/ARE/APE、MRG、PAR、NX5；按字面截取三组都会变 ARC，与 architecture 撞码。建议明确以映射表优先，特殊码不得用通用截取算法产生。当前未发现已有任务号撞号；本次未对全分支任务库做全量去重，不能宣称全局无重号。

### 6.3 worktree 与会话识别实测

使用现有 `session_context.match()`，候选为真实目录 basename 与真实分支去掉 `claude/`；读取本机 `.claude/session-name`，未修改标记。已结束名称也参与最长匹配（`session_context.py:65–76`）。

| 会话 | 真实目录 basename | 当前分支（略 claude/） | 标记 | 当前识别 | 分支换成 unrelated-topic 后 |
|---|---|---|---|---|---|
| one-glossary | modest-torvalds-293503 | one-glossary-bcs-systems | 无 | PASS | **失配** |
| one-architecture | xin-4e9c83 | one-architecture-adr-governance | 无 | PASS | **失配** |
| one-1a-par | elated-mclaren-be9a20 | one-1a-par-server-address | 无 | PASS | **失配** |
| one-ops | one-ops-a5ff89 | one-ops-r56 | 无 | PASS | 目录仍匹配，PASS |
| one-coordinator | one-coordinator-888aff | one-coordinator-two-lanes | 无 | PASS | 目录仍匹配，PASS |
| one-1a-next-5 | lucid-almeida-1748de | one-1a-next-5-docs3 | one-1a-next-5 | PASS | 标记保证，PASS |
| one-data | magical-franklin-00c1c4 | one-data-handoff-1 | one-data | PASS | 标记保证，PASS |
| one-quality | xin-61164f | one-quality-codex-stdin-gate | one-quality | PASS | 标记保证，PASS |
| one-rules | xin-c15a36 | one-rules-context-2 | one-rules | PASS | 标记保证，PASS |
| one-merge | xin-c2cfec | one-merge-train-5 | one-merge | PASS | 标记保证，PASS |
| one-arch-finance | objective-hertz-0e3c0a | one-arch-finance-closeout | one-arch-finance | PASS | 标记保证，PASS |
| one-arch-revenue | practical-hodgkin-3f1b8a | one-arch-revenue-closeout | one-arch-revenue | PASS | 标记保证，PASS |
| one-arch-people | zealous-shaw-1e4eba | one-arch-people-closeout | one-arch-people | PASS | 标记保证，PASS |

此处改名仅向纯匹配函数提供替换字符串，没有真的改 Git 分支。不能泛称「五个无标记会话改分支都失配」；只有前三个必然失配。OPS/COO 若同时移到中性目录也会失配。最长匹配还可能识别成更长的退役会话，不是任意同前缀都安全。建议五个均补标记；错标记不会回退分支匹配（:37–38），应同时核验所指画像存在。

## 7. 可直接粘贴的修改建议

本节只提供建议，没有应用。先按下列「通用块」补入每份画像的 `## 权限边界` 内，再按逐份建议替换相应条目；不增加新的二级字段。状态中的时间为本次取证快照，负责会话实际粘贴时应更新时间并核对在途实况。

### 7.1 十三份画像共用的最小规则块

```markdown
### 全员工作守则

- 可由负责会话审出对错的编码、设计初稿、整理与汇总，优先交 Codex：gpt-6.1-sol，model_reasoning_effort=xhigh；机械活按工作卡可用 high。Claude 负责派卡、首轮独立评审、安全判定、验收及会话沟通。生产取证、连生产操作、安全改动、opus 复审与验收测试不交 Codex。
- Codex 在独立 worktree 执行，不联网，只改卡列文件；先读根 AGENTS.md 与所指规范。用本会话 Bash 的 run_in_background，任务在本会话后台列表可见、完成自动唤醒，不轮询，不用 nohup、& 或 setsid 脱离会话。
- 调用形式：codex exec -m gpt-6.1-sol -c model_reasoning_effort=xhigh -s <read-only|workspace-write> --ephemeral -o <结果文件> '<完整工作卡提示>' < /dev/null。实际执行须选定一种 sandbox 模式并替换占位项；显式传提示词，关闭 stdin。
- 每卡一个任务号与任务名，按 SESSION_PLAYBOOK §5 的显式会话码登记 docs/modernization/tasks/<任务号>.md；后台 description 为「<任务号> <任务名> (<执行者>)」，PR、提交、完成点报告带任务号；登记返工轮次与耗时。
- 跨会话派工用可唤醒对方的 mcp__ccd_session_mgmt__send_message，首行「派工：<任务号> <任务名>」，附入口、出口、卡与结果路径；普通 SendMessage 不作已唤醒或已执行的证据。
- 要用户定、补值、转财务或配合的事一律登记待决定收件箱；子组由 one-architecture 合成确认页后登记链接，不在各自会话另问。用户口头决定当场记 intent 并登记已定；出题先查 DECISION_DIGEST。
- 只报完成、阻塞、验收失败、影响他人排期或需用户定的关键节点；不回纯确认，不广播例行入队/合并状态；同一对象一次说完，消息一到三行，长内容写文件。技术协调直连，不要求常规抄送调度。
```

这是现行要求的汇总建议，不声称公共文件已建立或钩子已支持它。若采用第 8 节集中注入方案，各画像改为短引用，避免长期复制十三份。

### 7.2 one-1a-next-5

依据：:14,23,31,56；作者分支末行已写 #405，与 main 不同。

1. 将「独占」第 1 条替换为：

```markdown
- 仅独占当前已派工作卡列明的迁移文件、测试与 backend/architecture 登记条目；data-access-registry.yaml、data-classification.txt、unversioned-table-writes.txt 按卡逐项登记并串行整合，不独占整个目录；one-data 负责签发结论。
```

2. 将「触发与节奏」事件条替换为：

```markdown
- 事件：收到已通过数据设计与 ADR 对齐检查的卡后开工；在制代码 PR 不超过一个，下一卡只准备不另开 PR，调度明确例外除外。旧修复批按 ADR-0054/0055 重判适用性，不凭旧队列继续。
```

3. 将「当前状态」替换为：

```markdown
2026-10-17 13:15+02:00 数据修复批以合集 #405 与后续小卡为接续线索，八个原 PR 不再作为独立待办；实际在途按调度台账与本会话交接文件核对，历史合集状态不作当前远端状态。新卡先对照 ADR-0054/0055、数据设计签发与任务登记；dev 暂停不影响未被撤销的本地设计与检查。
```

4. 合集包含更新后的本画像，并确认 main 已纳入，避免分支新状态被 main 旧画像遮蔽；本机已有 NX5 标记，无需再写第二份标记。

### 7.3 one-1a-par

依据：:20–26,30,43–45,65,69。

1. 将「不碰」「输入」中 next-3 的确认依赖及「工作守则」中的 next-4，统一替换为「one-1a-next-5（当前数据修复线）」，历史交接报告引用保留为历史证据。PAR-2 合并即催 rc.2 的条目替换为：

```markdown
- 发布相关结果只交 one-ops 核对并报关键节点；dev 当前暂停，只有用户恢复后由 one-coordinator 重排，不因 main 合入自动触发部署。
```

2. 「上下文文档」替换为以下已存在路径；下一步卡仍从调度取得，不能把整份旧计划当当前授权：

```markdown
- `docs/modernization/COORDINATOR_LEDGER.md`（当前派工与暂停项；冲突处以本日决定原文核对）
- `docs/modernization/phase1a/NEXT_BATCH_PLAN.md`（历史卡与设计依据；须结合当前派工重判适用性）
```

本画像:69 还提到 `quality/defect-bank/one-1a-par.md`，但该路径不在本次 main 树，不能直接补进上下文造成新缺链；由负责会话确认实际落点或另卡建立后再列。

3. 「当前状态」替换为：

```markdown
2026-10-17 13:15+02:00 原 PAR 卡与旧队列仅作历史证据；当前实施以调度在途卡为准，ADR-0034-IMPL 等安全相关工作由负责会话按新 ADR 核对，不能交 Codex。dev 暂停；与 one-1a-next-5、one-ops 直接协调，完成或阻塞时报调度。
```

4. 在「自动化」加：`本 worktree 的 .claude/session-name 固定为 one-1a-par（不入仓）；切分支后核对恢复识别。` 删除状态末尾笼统的执行模型策略，采用通用块；验收测试仍由独立测试代理写。

### 7.4 one-architecture

依据：:18–19,36,47,62–68,72；四 ADR 分支源文件状态。

1. 「独占」目录条替换为：

```markdown
- architecture/ 正式 SSOT、架构语义生成器与接缝整合；排除 proposals/closeout/finance/、revenue/、people/ 的受派片段，以及 decision-page/check_pages.py（one-quality）。三组只写分组片段，本会话整合后写共享文件；不与子组同时编辑同一文件。
```

2. 「输出」PR 条替换为：

```markdown
- 纯架构文档与画像：文档交付与四节点报告按 TEAM-53，由 one-merge 自扫远端收合集；代码、生成器、守护脚本按工作单元单独 PR。提议设计稿仍按评审与用户确认里程碑合入。
- 结构与财务待决先查 DECISION_DIGEST，确认页链接登记收件箱；不把已采纳 ADR 重新列成待批准。
```

3. 「当前状态」替换为：

```markdown
2026-10-17 13:15+02:00 三组骨架已铺完，转接缝与新决定回写；ADR-0052/0054/0055/0056 已采纳，采纳记录与实施完成分开跟踪；命名接续 N9→N7，规范名、指称登记与施工图回写按已批准方法推进，旧改名对照表只作输入。实际下一卡见调度派工与 ARCH_CONTEXT，dev 暂停。
```

4. 自动化补 `object_layer_check`、`name_id_check` 等现行检查的准确路径后再写已落地，不继续保留「第一批落地后再列」；添加固定 `one-architecture` 标记要求。同步精简 ARCH_CONTEXT 的已过时待批项。

### 7.5 one-coordinator

依据：:11,22,34–35,40,45,64；台账:8–11,55–59,86,91。

1. 「输出」替换为：

```markdown
- 派工单经唤醒式消息交工作会话；批准入队清单交 one-merge，调度不运行或手改队列，不执行合并。
- 待用户决定的事只汇总进收件箱，确认页附链接；决定原话用 intent 片段登记并更新收件箱已定状态。
```

2. 「职责」补：

```markdown
- 收到关键完成点时核对证据与 get_usage；不设上下文定时巡检。每日简报和每周抽审按 SESSION_PLAYBOOK 执行，不逐件代替负责会话验收。
- 画像变更落到 main 后再视为恢复规则已生效；协调 architecture/quality/三组的独占排除项与长期会话例外。
```

3. 「当前状态」替换为：

```markdown
2026-10-17 13:15+02:00 在岗；dev 暂停，strict 已关试行至 10-19，main 因并行合入变红一次即恢复 strict；命名 N9→N7，三组骨架完成，ADR-0052/0054/0055/0056 已采纳。只批准入队，one-merge 执行；待用户事项只在收件箱，当前在途见台账。
```

4. 由本会话另卡清掉台账旧 mq_run2 重启命令、旧 strict 常态、三组铺开暂缓与旧 PR 队列，保留过程链接；补固定 `one-coordinator` 标记。任务登记含本卡 GOV-1017-COO-03 由派卡会话处理，本审计不越界写 tasks。

### 7.6 one-data

依据：:16,22,41,51,65,70；ADR-0054/55。

1. 将职责门禁条括号「本会话起草，用户确认后生效」替换为 `已生效，按 DATA_DESIGN_GATE 的现行条目签发`；它与本画像状态的「生效」应一致。
2. 「权限边界」加：

```markdown
- 数据设计初稿、对象到表汇总与映射整理可交 Codex；数据设计签发、证据复核与安全分级判断由本会话承担。生产读取仅经授权的 one-ops 取证通道。
- ADR-0054/0055 下的一次迁入、人事业务生效日与生产不接老系统/A8，替换旧运行时同步设计；只改受派设计卡，不据此自行删迁移或业务代码。
```

3. 「当前状态」替换为：

```markdown
2026-10-17 13:15+02:00 数据设计门禁已生效；新卡按 ADR-0052/0054/0055/0056 及一次投产路线重核，三组表级初稿交本会话复核签发。移除历史上下文百分比，未完成与下一步只列 DATA_CONTEXT 当前项；长期类型与常驻规则的例外由调度统一。
```

### 7.7 one-glossary

依据：:12–13,35,67,70,73,77；任务卡 N9→N7。

1. 「首件事」替换为：

```markdown
- 当前优先处理命名主线 N9→N7：规范名与指称登记按已批准命名方法衔接，存量积压门槛继续作为兜底；不再把历史第三轮决策单写成首件事。
```

2. 工作守则第 7 条替换为：

```markdown
7. 汇总、候选名、确认页初稿、映射与机械写回优先交 Codex gpt-6.1-sol xhigh；正式/方言/业务用语裁定以用户答复为准，负责会话核对原文后写回。生产取证、安全判定、独立 opus 复审与验收测试不交 Codex。
```

3. 「输出」中的词表文档 PR 改为 `按 TEAM-53 文档合集自扫分支；同时改代码或守护时另按代码卡 PR`；在自动化加固定 `one-glossary` 标记要求。
4. 「当前状态」替换为：

```markdown
2026-10-17 13:15+02:00 在岗；命名主线接续 N9→N7，旧确认单写回仅保留历史索引；规范名、门禁与代码改名按已批准步骤推进，积压数字用当日生成结果。实际在途见 GLOSSARY_CONTEXT，用户待决只进收件箱。
```

### 7.8 one-merge

依据：:9–14,43,46,53,57,63,67。

1. 第 46 行整条替换为：

```markdown
- 改 .github/workflows 的 PR 若需要经 ssh 更新，只更新已批准的 PR 分支并重过门禁；main 一律由受管 mq.py 队列按批准清单合入，本会话不手工 gh pr merge，不使用 --admin，不直推 main。
```

2. 权限边界加：

```markdown
- Codex 仅用于离线日志整理、清单与报告草稿；批准、入队、队列运行、合并及远端状态操作由本会话在授权边界内执行，不委派 Codex。
```

3. 当前上下文路径后的「只取两节」说明改成 `钩子注入全文；台账必须先去除旧队列指令`；后续建议建立 MRG 自有的最小上下文文件并由画像列入，本次不创造这个文件。生命周期与长期例外由调度明确，不自行归档。
4. 「当前状态」替换为：

```markdown
2026-10-17 13:15+02:00 已有本会话 worktree 与固定标记，按 one-coordinator 批准清单运行 scripts/merge/mq.py；strict 已关试行至 10-19，只走受管队列，当前队列与失败看实际日志，不沿用台账旧队列数字。dev 暂停。
```

### 7.9 one-ops

依据：:10,21,31–32,36–43,56–61；台账:18。

1. 「触发与节奏」事件条替换为：

```markdown
- 事件：收到授权运维卡后执行；main 合入只产生发布候选，不能自动恢复 dev。dev 当前暂停，用户明确恢复后再由调度排部署；生产变更仍按逐项授权与回读执行。
```

2. 「权限边界」加：

```markdown
- 可审出的离线脚本草稿、剧本与汇总优先交 Codex；生产取证、任何连生产操作、身份/授权/凭据/单人模式防线改动不得交 Codex。要用户定的事进收件箱，不因定时器存在就认为部署获批。
```

3. 「当前状态」替换为：

```markdown
2026-10-17 13:15+02:00 本地调度台账记录 rc.6 后 dev 暂停，待用户明确恢复；不再以 rc.1 或首次 dev 待部署接续。保留已建资源与暂停项的回读线索，实际服务器状态未由本次审计复核；ADR-0054/0055 的连接退出与迁入用途另按授权运维卡处理。
```

4. 自动化补固定 `one-ops` 标记要求；由 OPS 自己更新 OPS_CONTEXT、资源台账及定时器状态，本审计不操作运行中资源。

### 7.10 one-quality

依据：:14,33–36,56,73,77；`QUALITY_CONTEXT.md:16,51,66`。

1. 「独占」队列条替换为：

```markdown
- scripts/merge/ 与 scripts/queue/merge_train.py 的源码与自测由本会话维护；运行、入队、合并归 one-merge，批准归 one-coordinator。architecture/decision-page/check_pages.py 由本会话维护，architecture 目录独占显式排除此文件。
```

2. 「权限边界」加：

```markdown
- 普通脚本与离线自测按卡优先交 Codex；涉及安全、权限、凭据、生产防线或相关守护的改动先由负责会话判定，不因属于 CI 就一律派 Codex。验收测试仍由独立测试代理编写。
```

3. 「当前状态」替换为：

```markdown
2026-10-17 13:15+02:00 队列、CI 三档与裁定守护已有本地合入记录；strict 已关试行至 10-19，监控与回退按现行队列规则。旧 #449/#450 等 PR 状态需回读后再列，不能由旧画像当作仍在途；新增或改动测试重复运行三次，失败复发记错题本并报关键节点。
```

4. 更新 QUALITY_CONTEXT 的 strict 前提与旧在途，不把历史「保留 strict」继续当现行要求；常驻例外由调度同步手册。

### 7.11 one-rules

依据：:11–13,34,57–58,64–67,71；四份新 ADR。

1. 「首件事」替换为：

```markdown
- 按 ADR-0052/0054/0055/0056 与 DECISION_DIGEST 重核现存待决：已被决定覆盖的题标出处并撤下，未覆盖或仍需财务证据的继续进收件箱；不从相邻题推导用户答复。
```

2. 工作守则第 3 条替换为：

```markdown
3. 技术协调直接找 one-architecture、one-data、one-glossary，不要求常规抄送调度；只有影响排期或需用户定的结论才向 one-coordinator 报关键节点。用户待决只走收件箱。
```

3. 「输出」文档 PR 改为分支+提交号收合集。取证执行仍交受授权的 Claude/OPS 通道，Codex 只做去敏汇总、规则与题面初稿；不把「取证交 sonnet」误扩成可以派 Codex 连生产。
4. 「当前状态」替换为：

```markdown
2026-10-17 13:15+02:00 已认领；旧财务、ARCH-D5 与 F-1 题不再以 10-15 未答快照续接，按四份已采纳 ADR 与用户原话逐项重核。当前规则写回、待财务证据与待用户事项见 RULES_CONTEXT 和收件箱，未核实的不宣称已解决。
```

### 7.12 one-arch-finance

依据：:13–14,24,41,45,53–60。

1. 「独占」条目加限定：`仅写 closeout/finance/ 自有片段，不直接写共享 processes.v2.yaml、data-model.v1.yaml；共享文件由 one-architecture 整合。` 不将表口径初稿说成 one-data 已签发。
2. 「输入」加：`ADR-0052/0054/0055/0056 的已采纳源文件与批准证据；ADR-0050/51 只保留未被取代部分。`
3. 「上下文文档」替换为以下在本组分支已跟踪的路径；只在分支存在的事实随合并方案写明：

```markdown
- `docs/modernization/sessions/one-arch-finance.md`（本画像）
- `docs/modernization/architecture/proposals/closeout/finance/NOTES.md`（本组接缝与现状）
- `docs/modernization/architecture/proposals/closeout/finance/STAGES.md`（阶段对齐）
- `docs/modernization/architecture/proposals/closeout/finance/OBJECTS.md`（对象对齐）
```

4. 删除第 54 行笼统「执行用 sonnet、取数用 haiku」，替换为通用块并保留 AFI 任务登记条；「自动化」改成 `复用压缩前快照、恢复画像钩子；Codex 用本会话 run_in_background 并显式关闭 stdin。`
5. 「当前状态」替换为：

```markdown
2026-10-17 13:15+02:00 财务组骨架与表级模型已交，当前转新 ADR 回写、命名 N9→N7 与接缝核对；ADR-0052/0054/0055/0056 已采纳，未定财务口径仍标待定。新卡按 AFI 登记并优先交 Codex，用户事项由 one-architecture 合页后进收件箱；不沿用旧提交号当作分支最新提交。
```

### 7.13 one-arch-revenue

依据：:11–13,18,28–29,49–50,58–64。

1. 共享 YAML 条加与财务组相同的「仅片段、architecture 整合」限定；保留 B-01–B-05 等本组边界，不据此变更 BC 划分。
2. 「输入」新加：`ADR-0052/0054/0055/0056 已采纳文件与批准依据；收入待决仅保留尚未覆盖部分。` 原 ADR-0050/51 不直接删除，标剩余有效条款。
3. 第 59 行模型策略替换成通用块；任务号会话码固定 ARE；「上下文文档」两条分支路径可以保留，但画像与 README 的最小恢复版本应独立安排治理文档收集，不能假定 main 新 worktree 已有。
4. 「当前状态」替换为：

```markdown
2026-10-17 13:15+02:00 收入组骨架、表级稿与接缝产出已交；原 SD-08/13/14 等待项先对照 ADR-0052/0054/0055/0056 与答复原文重核，不能直接宣称全部关闭。接续命名 N9→N7 与 one-architecture 当前层派工，用户事项合页后进收件箱，文档合集按 TEAM-53 自扫分支。
```

### 7.14 one-arch-people

依据：:13,18,28–29,49,57–64。

1. 第 58 行旧命令替换为通用块，显式补完整提示词、`< /dev/null`、本会话 `run_in_background` 与 APE 任务登记，不能把「后台跑」当作可见性已实现。
2. 「权限边界」加：

```markdown
- 人事骨架、对象与表级候选稿可交 Codex；账号开通/吊销、授权与安全不变量的设计改动、代码、验收测试及生产取证均由负责会话按安全流程处理。不得因本组含人事对象就把身份生命周期改动打包派给 Codex。
- 只交 closeout/people/ 片段，one-architecture 写共享文件；one-data 签发表口径，one-rules 管规则，用户事项由 one-architecture 合页后登记收件箱。
```

3. 「输入」补四份已采纳 ADR；现有 README 上下文路径在本组分支可用，需与画像一起安排最小恢复版本，保持本机已存在的 APE 标记。
4. 「当前状态」替换为：

```markdown
2026-10-17 13:15+02:00 人员组骨架、表级稿与阶段/对象对齐已交，下一步按 ADR-0052/0054/0055/0056 及命名 N9→N7 修正接缝；人事记录系统和业务生效日沿已定口径，身份授权变更交安全流程。待用户项先核对是否已被采纳决定覆盖，不笼统保留旧确认页待答。
```

## 8. 系统性建议（均未实施）

1. **全员规则只保留一个语义来源**：继续以 SESSION_PLAYBOOK 为规则源，公共注入内容从其 §2.1、§5、§11 生成最小摘要；不要另写一份会独立漂移的规则正本。可提供 `sessions/_COMMON.md` 作为生成摘要并由钩子先注入，画像只保留角色差异与版本指针。此路径目前未建立，需另卡授权与生成一致性检查；不要现在就往 13 份上下文里写一个不存在文件。已有 `scripts/hooks/playbook_digest.py` 可优先评估复用，不另造重复维护链。
2. **治理画像与设计提议分开发布**：三组画像和最小恢复文档应能随治理合集到 main；设计提议稿仍留原分支至里程碑。main 优先读画像的策略才能既保规则一致，也不遮蔽作者最新职责。若要引入画像版本/分支引用，须另卡设计并测试，不能悄悄改钩子为工作区一律优先。
3. **最小上下文先清陈旧指令**：优先清 COO 台账旧队列与 strict 段、QUA 的旧 strict 前提、GLO 的旧积压/PR、OPS 的旧 rc.1 接续；恢复内容只留当前状态与原文链接。MRG 建自己的小上下文后不依赖 COO 大台账两节文字筛选，因为当前钩子不支持选择小节。
4. **补标记并在开场读回**：五个缺标记会话由本会话维护者写不入仓的 `.claude/session-name`，换分支/清空后核验解析结果。这里的目录与分支证据只证明可识别，不证明桌面会话正在运行。
5. **任务治理落实到工具**：在本地 main 树未找到 `docs/modernization/tasks/` 或 `build_tasks.py`，`build_all.py` 也未挂 tasks 生成步骤；所以最新文本所称总表自动生成尚不能在本基线证明已实现。另卡补任务片段 schema、13 项显式会话码映射、每天每会话序号分配、全局重复检查、后台 description 与任务号关联、返工/耗时字段汇总。任务种类至少覆盖手册已有七类，不扩充未经确认的新枚举。
6. **结构检查与语义审查分开**：迁完 9 份时间戳后再评估开启 REQUIRE_TIMESTAMP；新增扫描优先报告缺少公共守则引用、main/分支恢复缺口、退役输入会话、状态中的旧任务。独占冲突按 §2.1 仍由调度人工裁定；不未经批准把目录重叠自动变成 CI 阻塞。给校验器补功能时用真实负向样例证明会拦截，不只验字符串存在。
7. **先改冲突再重发规则**：先由 COO 协调 ARC/QUA/三组独占排除与长期会话例外，移除 MRG 手工合并口径，再处理共用守则注入。文档复审按当前纯文档自检规则执行，钩子/CI 源码另卡按对应评审等级执行，不把本报告当成这些修改的批准。

## 9. 验证记录

### 已验证

- 已直接阅读规定的根规则、工作卡、最新手册及 13 份完整画像。
- 使用仓库现有解析函数验证 13/13 画像的 12 字段、顺序、内容、会话名与当前状态单行要求；main/分支画像加退役名录无会话名重复。
- 使用 main 树检查全部 24 次上下文引用：main 画像 20 次全部存在；分支画像 4 次只在对应分支存在；逐个确认各作者 worktree 文件与分支 HEAD 均存在。main 10 份逐份路径/长期上下文校验 PASS；三份分支画像放到 main 树校验按预期报告缺路径。
- 实测 13 个真实 worktree 的标记、目录与分支识别，以及中性分支名模拟；所有模拟仅调用纯匹配函数，没有改分支或标记。
- 审查源码确认当前钩子的节范围、反引号路径规则、画像与上下文不同的读取优先级；核对 NX5 的 main/分支状态遮蔽实例。
- `scripts/precheck.sh` 最终 **5 PASS、0 FAIL、11 SKIP，退出码 0**；解析器与钩子自测均退出码 0。检查放在临时 Git 副本，避免 build_all --check 写出生成产物而突破只写本报告的边界；原仓库 `git diff --exit-code HEAD --` 为 0，受跟踪文件未改。完整输出见下。

### 未运行

- 未联网刷新 main、PR、CI、分支保护、在线收件箱、桌面会话或云环境；未调用生产账号、未部署、未建账号、未起容器。
- 未执行任何画像或脚本修复；未写 tasks 登记、公共守则、会话标记；未提交、推送或发送跨会话消息。
- 本卡无实现验收测试；未运行后端、前端或容器集成测试。precheck 的逐项 SKIP 理由见原样输出，不能据结构 PASS 宣称实际流程验收通过。

### precheck 与自测输出

执行方式：本机临时目录内 local clone（共享只读 Git objects），checkout 起始 `23bf4216`，副本 `origin/main` 也固定该 SHA，复制全部 `.codex-inputs` 后执行原版脚本。副本与自测临时目录执行后自动清理；未改原仓库引用、索引或源文件。报告收尾补的是验证文字，同属 `.codex-inputs`，不改变选检范围。

第一轮用默认 `/usr/bin/python3`：`SUMMARY: 4 PASS, 1 FAIL, 11 SKIP (base origin/main) — not ready`，脚本退出码 1。唯一 FAIL 是 `generated docs (build_all --check)`，原文为：`/Library/Developer/CommandLineTools/usr/bin/python3 缺少 PyYAML，无法运行 glossary/build_terms.py 校验 terms.yaml 可复现`。随后核对 `SUPPLY_CHAIN.md:15` 与现成本机环境，使用已登记的 pyenv Python 3.12.13 / PyYAML 6.0.3 重跑；没有安装或下载依赖。

解析器与钩子自测原样输出：

```text
$ python3 -B docs/modernization/build_sessions.py --self-test
self-test: ok
EXIT_CODE=0

$ python3 -B scripts/hooks/session_context.py --self-test
self-test: ok
EXIT_CODE=0
```

最终使用 `/Users/yeliang/.pyenv/versions/3.12.13/bin` 置于 PATH 首位，`PYTHONDONTWRITEBYTECODE=1`；环境读回 Python 为 3.12.13、PyYAML 为 6.0.3。`bash scripts/precheck.sh` 退出码 **0**，stdout 原样如下（每项 SKIP 的理由随行保留）：

```text
precheck: base origin/main (23bf4216), HEAD 23bf4216, 7 changed files (7 uncommitted)

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
PASS  release scripts self-test              deploy_gate.py self-test passed
PASS  shellcheck scripts/release             ok
PASS  gitleaks (full history)                2829 commits scanned; no leaks
      note: uncommitted files are not in history; the pre-commit hook scans them on commit
SKIP  backend tests                          not run by default; no module code changed (shared inputs, resources, build or architecture lists)
      run: (cd backend && ./mvnw -q test-compile surefire:test -Dtest='ArchitectureRulesTest,ModularityTest,ModuleDependencyTest,NamingSurfaceTest,GlossaryTest,DdlNamingTest,LegacyShapeTest,TimeSourceGuardTest,TestIsolationGuardTest' -Dsurefire.failIfNoSpecifiedTests=false)
      before delivery run everything: scripts/ci/backend_verify.sh (Testcontainers: ask the user before starting containers)
SKIP  frontend verify                        前端依赖未安装：先在 frontend/ 运行 pnpm install --frozen-lockfile --offline（只用本机缓存、不下载）；缓存缺包而失败时，再问用户是否联网安装（本次未改 frontend/，CI 仍会跑）
SKIP  SQL Server integration tests           relevant_change.py: nothing the *MssqlIT tests exercise changed

SUMMARY: 5 PASS, 0 FAIL, 11 SKIP (base origin/main) — ready
```

其中 `.codex-inputs` 属未登记的输入路径，precheck 保守选择发布脚本自测；后端/前端没有业务代码变化且本卡禁止容器，所以不额外运行构建测试。gitleaks 扫的是 Git 历史，不含本次未提交报告；报告内容已人工核对未抄入凭据值。此通过只证明本次报告交付的适用检查通过，不表示画像问题已修好，也不表示目标系统完成验收。
