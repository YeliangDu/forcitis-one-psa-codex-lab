# one-rules 速查（2026-10-16 傍晚）

一行一项，过程放链接（SESSION_PLAYBOOK §2.1 第 13 条）。

设计原则（用户 2026-10-15，经 one-coordinator 转达）：以 AEC 行业项目制专业服务的通行做法为主（合同范本、收入准则、成熟产品），老系统生产数据只作参考与核对；确需复刻的老行为仍以生产数据为证。业务决策页做法见 `rules/decide/README.md`；页面顶部是分组标签，选择自动存为草稿，每组点「提交本组」才算答复（用户 2026-10-15 首次只提交了各页第一组）。

## 在途

| 项 | 状态 | 出处 |
|---|---|---|
| 顾问答题单（用户与调度共同答财务、审计、法务类题；用户 2026-10-16 改派，不转发） | 第 1 批已出（W-7、P-001～005）；第 2 批已开头：「A1 重议」草稿（含 CAS 14 第十一至十三、四十一条官方原文核对，已按 one-architecture 的 `Q04_OVER_TIME_PROGRESS_RESEARCH.md` 收窄）。**专题研究已由 one-architecture 完成（`proposals/Q04_AEC_CONTRACT_MODELS.md`，opus 评审结论「改后再出」）；合成页本轮只问事实，已发布（R-1～R-9，Artifact EQg3euo9H7JkW1wAX7iG7y，contract 0.2.80），等用户答复；答复回来由 one-architecture 转我，我写 A–D 口径下一页（含 Q-04 存储方式），并把 R-1、R-3 答案并入 A1 重议**。第 2 批其余照写：W-2、W-3、W-4、W-5、W-6、W-8（见 Q-04 研究稿 §7）与按一次投产路线（2028-01-01、全部历史迁入、U8C 继续做法定账，intent 133348 U-2、U-4、U-6、U-8）改写的 P-003～005，写之前对照 U-1～U-18 | 私有：`~/.modernize/psa/finance/ADVISOR_BATCH_1.md`、`ADVISOR_BATCH_2.md`、`external-drafts/`；W-7、P-001、P-002 已请用户答 |
| 决策页决定链路 | PR #362（整页「检查答案」一次提交、`decide_sync.py` 对账与回写与 `--check`、不预选开关；用户答 B：业务口径页预选、框架标准选型类不预选）；opus 首轮复审加 sonnet 改后复核都已过，等调度入队；新模板首个新单子发布前须先在手机上看渲染 | `rules/decide/README.md` |
| 填报义务规则 RULE-397～405 | #286 待入队；实现要等 B-27 请假对象与标准工时版本（AGG-108）落地 | `rules/TIMESHEET_FILING_RULES.md` |
| 撤销、撤回一律须原因，再发起状态机 | #347 待合并（含 RULE-268、277、288 与步骤 30；#346 已合并） | intent 085331、123736 |
| 手册 §5 转述前核对原文 | #323 待合并（编号已改为 DATA-001、002、004、005、013） | |
| RULES_CONTEXT 更新 | #361 待合并，本次再叠一次 | |
| 财务口径决策单 | 46 题与 9 条事实已写回；F-05、F-10、F-11 与 ADR-0047 八项（F-12～F-19）暂缓不外发；A1、A10 待审计师（A1 重议见上） | `r0/FINANCE_DECISIONS.md` |
| F-1 数据范围；RULE-387 | F1-01、02 已定（ADR-0044 修订在 one-architecture）；RULE-387 维持条目的业务负责人与有效期待 ADR-0044 Q1；区域维度并入 one-architecture 的组织梳理 | `rules/decide/answers/F1.json` |
| 规则卡补「类别」 | ADR-0040 第 2 步之后 | ADR-0040 |
| 规则文档瘦身对照表 | 待用户 | COORDINATOR_LEDGER「待用户」 |
| 画像「区域负责人」改名 | 用语已由 one-glossary 处理；1a 审批节点 regionalHeadReview 与字段 regionalHeadId 的改名卡待调度派 | |

## 用户原则（2026-10-16，经 one-coordinator 转达）

「能通过规则和不变量守卫的就不要人来守」：one-architecture 会把流程中可规则化的控制（重复、额度、预算余额、合同依据、期间开闭、职责分离等）交本会话出规则卡，替代人工审批节点；评审现有规则卡时也看是否还有可撤的人工环节。

## A1 与收入线进度

A1 后续页已答（intent 2026-10-16-162827）：维持按时点；无法定年审，把关改为财务负责人书面确认并留档，每年复核加重大变化临时复核；GL-026 已写回，ADR-0047 修订由 one-architecture 写。Q-04 收窄稿（`Q04_STORAGE.md`，one-arch-revenue）已按我四点意见改，VS-04 用户页出稿前再给我过目；开票口径待财务核对是否符合所执行准则，不作已定规则。

## 2026-10-16 晚进度

- #418 已决策速查表（`build_decision_digest.py`、`decision-digest/`）已入队：新决定推翻旧决定时用 `new_intent.py --supersedes=`；疑似取代未登记 17 条待补；未分类约 123 条待增补 topics.txt；Q24（intent 2026-10-15-233152）与 Q-10（intent 2026-10-16-134134，现仅在 claude/one-architecture-adr-governance 分支，one-architecture 拆小 PR 合入 main 后）的借款冲抵矛盾行待加到 `decision-digest/relations.txt`。
- 规则卡：RULE-406～411（`APPROVAL_CONTROL_RULES.md`，406/407/410 已按用户答复去 Review）、RULE-412～413（`OVERTIME_SEAM_RULES.md`，413 待 people 设计采纳）。N/V/S 校审级别待 one-ops 取 S 与 V 高低后写卡。
- #419（one-quality）合入后：#362 同步 main 解冲突；FD 的 9 条「向财务核实的事实」是否改 kind: fact（需重发页面）由我定。
- 待：补休与最低时数（RULE-413）等 one-arch-people 定稿；Q-04 收窄稿（VS-04 用户页）出稿前再给我过目。

## 下一张用户页待问（与 Q-04 合并，先交 one-coordinator 审题）

- RULE-407 保证金付款收款方是否限招标人或代理机构（用户原话只到「以招标文件为依据」）。
- RULE-413 补休是否算请假、是否扣减最低时数（等 one-arch-people 设计定稿）。
- RULE-415 毛利底线值、产能度量与上限、加审人，及触发条件是否仅示例（配置项）。
- DATA-RULE-07 过晚结束的法人归属期间的更正形态（追加更正版本，带修订序号与原因；one-data 已改卡，确认前两头不实现）。
- `consumed_events` 保留 90 天（one-data 已按提议补入 GUIDING_PRINCIPLES §6，待用户认可）。
- DATA-RULE-01…08 评审见 `rules/DATA_RULE_REVIEW.md`；one-data 已按评审改卡（分支 claude/one-data-handoff-1 @ 64238ff1，内容已抽查与评审意见一致）。

## 三张旧页待答项重算（2026-10-17 13:26+02:00，对照 ADR-0052/0054/0055/0056 与用户原话；这些 ADR 此刻在 claude/one-architecture-adr-governance 分支，尚未见于 main）

| 旧页 | 项 | 重算结果 | 依据 |
|---|---|---|---|
| 财务口径 | A1 待审计师书面确认 | 已覆盖：无法定年审，由财务负责人书面确认并留档（GL-026 已写回） | ADR-0056 决定 1；intent 2026-10-16-162827 |
| 财务口径 | A10 现金等价物口径变更附注，待审计师书面确认 | **未覆盖**：ADR-0056 只管收入确认证据；A10 的把关人改谁，须另问（并入下一张用户页） | GL-051 |
| 财务口径 | F-12…F-17（视为同意等证据的事实）、F-19（GF-0209 条文核对与法务） | **仍需财务与法务证据**：ADR-0056 改的是谁来开放，不是事实本身 | ADR-0056 决定 1 |
| 财务口径 | F-18 四类证据是否开放、何时开放 | 部分覆盖：开放由财务负责人逐类确认（2a、2b、4a、4b 分别表态），具体何时开放仍待财务 | ADR-0056 决定 1 |
| 财务口径 | F-05（银行流水与确认单回收时间）、F-10（各法人企业规模）、F-11（A7 的 N 与转费用方式） | **未覆盖**，仍待财务 | — |
| 财务口径 | D17-12（只读分类查询每一类是否漏记） | **未覆盖**，仍待财务 | — |
| 财务口径 | GL-083 期初来源「起步月上一月」、GL-123「G1 首季度关账日」、A8 制证插件按表单逐类停用 | **措辞与前提需重算**：ADR-0054 改为统一切换（年度首日同日），ADR-0055 不接 A8；原按 G1 与逐类停用的答复要对照新路线改写后再确认 | ADR-0054 决定 1；ADR-0055 决定 1 |
| ARCH-D5 | 六题均已答 | 无待答；D5 里的「区域负责人来源」依赖组织数据由谁维护，ADR-0055 改为本系统人事命令维护、不从 A8 同步，需对照改一句 | ADR-0055 决定 2 |
| F-1 | RULE-387 维持条目的业务负责人与有效期 | 已覆盖：范围复核不要求业务负责人、不设有效至（ADR-0044 第 2 项第 6 条；RULE-417） | ADR-0044；N1-09 |
| F-1 | 区域维度是否需要 | **未覆盖**，仍并入 one-architecture 的组织梳理 | — |

不认定「已全部解决」：已覆盖 3 项，部分覆盖 1 项，其余待用户或财务；下一张用户页（与 Q-04 合页）加 A10 把关人一题。

## 2026-10-21 进度（clear 前）

- 对抗验证第 1 轮转 one-rules 的 37 条已逐条回复：`rules/R1_RULING_REPLY.md`（①1、②22、③0、非规则 14）；缺口里需用户或财务、人事、法务定的，随用户页出题，先问范围的（所得税汇算、对外报表附注、非货币出资、设施保养、领导力调查）已写成 `rules/decide/sheets/ADV1R.json`，由 one-architecture 并入对抗确认页 ADV1，过调度审题后发布。
- N1、A1 用户页均已答并写回（RULE-407/413/415 去待确认，新增 416、417）；N1 的 A10 把关人一题尚未出（ADR-0056 只管收入确认证据）。
- Q-04 存储方式页等 one-architecture 的 VS-04 稿；DATA-RULE 评审见 `rules/DATA_RULE_REVIEW.md`，one-data 已改卡。
- 用户端没有我发起的未答页面；收件箱当前唯一待答是 one-architecture 的 ADR-0073 两题（U3/U4）。
- 协作规矩：文档类提交推送后报「分支 + 提交号」不开 PR；Codex 卡写四项且规则口径判断自己做；会话间消息一到三行。

- 2026-10-21 调度派：UNTESTED_P0_RULES 第 2 节 9 张「需修正」卡（RULE-144/145/169/265/304/308/311/337/376）已按评审结论改写为正确行为，老行为留作证据；待用户定的两处（RULE-265 定时任务发起人、RULE-311 钩子与业务处理的先后）随对齐后的确认页改版一起问。RULE-279 规格里「已取消=驳回」与 RULE-169 修正后不一致，待下次动 279 时同步。

- 2026-10-21 调度派：TEST_RULE_MAPPING 里 193 条「业务测试断言了行为、找不到规则卡」已补登 87 张新卡 RULE-418～504（五个文件：IDENTITY_AUTHORIZATION、IDENTITY_SINGLE_OPERATOR、SOURCE_REFERENCE、TIMESHEET_CALENDAR_ATTRIBUTION、APPROVAL_INTEGRITY 各 `_RULES.md`），测试到卡号对照在 `rules/TEST_RULE_BACKFILL_MAP.tsv`（2 条纯技术契约不成卡）。Review=Yes 共 9 张带 Needs decision，攒着随对齐后的确认页改版问；卡末 New terms 待 one-glossary 处理。

- 2026-10-21 one-1a-admin 来问 B-31 四项：SCH-06（起算点＝离职版本 businessDate，ADR-0068）、冲正形态、用途→印章类型对应表已可定；待用户 Q-B31-1～4（INV-138 只数实际用印还是占用；老系统单号份数上限首次为准；冲正是否腾出份数；一用途多印章类型，可由行政答）已回复 `rules/B31_RULING_REPLY.md`，攒着随确认页改版问；RUL-01～08 逐条回复待做。

## 2026-10-22 进度（上下文 48% 交接）

- 已完成并推送（分支 claude/one-rules-context-2 @71f8b8210，one-merge 排合集）：9 张「需修正」P0 卡改写（RULE-144/145/169/265/304/308/311/337/376；304 已与 D-1a-66 对齐，279 与 169 对齐）；193 条无卡业务测试补登 RULE-418～504（五个 `*_RULES.md`，测试对照 `rules/TEST_RULE_BACKFILL_MAP.tsv`）；INV-89 改为「项目分配额 + 人工成本分配差异 = 实际人工成本」（改来源稿 ENTERPRISE_AGGREGATES_DRAFT.md 并重新生成）；`check_pages.py` R4 遮盖只用词表正式词、已答题不再检查（`answered_pages.txt` 登记 15 页，新页仍查）；M-29 前后值与 FCQ 四注（`r0/M29_BASELINE_RESULT.md`、`r0/m29*_queries.sql` 已取证并登记）。
- 待用户定（攒着随对齐后的确认页改版一起问，不单发）：RULE-265 无登录的定时任务如何发起审批；RULE-311 钩子与业务处理先后；Q-B31-1～5（INV-138 只数实际用印还是占用、老系统单号份数上限以首次登记为准、冲正是否腾出份数、一用途可否多印章类型、周年「正式员工」雇佣类型清单）；补登卡 Review=Yes 的 8 张：RULE-423、433、440、457、480、484、501、503；M-29 五个未决点（奖金算不算直接人工、居间费算不算扣减项、代垫分不出先只扣委外还是等财务识别办法、2025 成本取进损益还是发生口径、老系统人工是日薪×人天）。
- 在手未做：one-1a-admin 的卡 C「员工活动申请」规则点 T-R1…R9（分支 claude/one-1a-admin-card-c @890e8cd8b 的 `b31-rules/B31_CARD_C_API.md` 第 11 节：金额为 0、重复办结/取消响应、谁能登记/办结/取消、手工提交周年贺卡类型（已答：一律拒绝）、重登限制、字段上限、草稿与活动发生日、参与人规则、本人/下属范围）；RUL-01～08 逐条回复（`rules/B31_RULING_REPLY.md` 已回 DATA-03/06、RUL-05、SCH-06、周年三问）；RULE-387/394 等原待办见上文。
- 守则：每个工作单元开始前先合 main；提交与推送分两次调用；FREEZE_FACTS.md 合并冲突取 main 版再 build_all；precheck 约 2 分钟，用后台跑；取证走 one-ops 只读通道、只取聚合并登记；转述的 intent 先核对原文。

## 工作守则要点（画像「权限边界」下有全文）

- 本会话主模型 sonnet；会话上下文 2026-10-16 傍晚到 76%，按手册先更新本文件再 `/clear`；新开后从本文件接续，不凭记忆。
- 发布决策页固定 `contract: "0.2.80"`；发布后核对真实渲染，没登录就报「未实看」。
- 要别人定的事直连对方，抄送 one-data 或 one-coordinator；用户待答事项只由调度汇总到收件箱。
- 写回只写用户原话覆盖的范围；转述的结论先核对原文并标已核实 / 已抽查 / 未核实。
- 每个 PR 开后立即关 Auto-fix；子代理显式指定模型（haiku 取数，sonnet 调研与草稿，opus 极少）。
- 错题本 `quality/defect-bank/one-rules.md`，决策页发布前回测。

## 决策页工具

`rules/decide/`：生成器 `build_decision_page.py`、导出 `export_answers.py`、收件箱 `inbox/`；已发布的页面与链接见 `rules/decide/README.md`。
