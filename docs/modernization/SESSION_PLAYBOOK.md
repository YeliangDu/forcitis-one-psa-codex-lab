# 团队规则表 — FORCITIS one 现代化（SESSION_PLAYBOOK）

负责：one-coordinator。依据：GOV-1022-COO-15（intent 2026-10-22-203954）。一条规则一行、只此一个出处；画像、台账、记忆只引 TEAM 号，不抄条文。
强制：「守」=已注册守护会拦下；「半」=守护只提醒、只报告或只覆盖一部分；「无」=靠自觉。守护列空=无守护也不建。注册位置见附表 B，待建见附表 C，旧节号见附表 D。数据设计、DDD、迁入、集成清单在 `DATA_DESIGN_CHECKLIST.md`。

## 1. 会话与交接

| 号 | 规则 | 负责 | 强制 | 守护 |
|---|---|---|---|---|
| TEAM-01 | 会话类型与名单见附表 A；开、并、归档会话由调度定；调度开出的会话启动即 detach 为顶层、标题改为会话名 | one-coordinator | 无 | |
| TEAM-02 | 会话名 `one-*`、全局唯一（含远程）；每个在用会话一份画像 `sessions/<会话名>.md`，新会话先写或认领画像，随第一份提交入库 | one-coordinator | 半 | build_sessions.py（只校名称与字段） |
| TEAM-03 | 画像只写类型、职责、独占、不碰、输入、输出、触发、自动化、交接条件、权限边界，引用 TEAM 号不抄条文；不写「当前状态」「工作守则」；「上下文文档」一节只放路径与 `交接源:` 行，现行事实见 TEAM-08 | one-coordinator | 半 | build_sessions.py（字段集严格相等，多出或缺少即失败；不查是否抄条文） |
| TEAM-04 | 各画像「独占」路径两两不相交；相交由调度裁定，同一提交改两份画像 | one-coordinator | 无 | 待建：GOV-1022-QUA-13 |
| TEAM-05 | 用量 30% 准备交接（不接新大块、交接写到最新），40% 在下一批次边界交接；连续约 8 小时或已压缩两次也交接；调度例外：50% 准备、60% 交接（用户 2026-10-23，intent 081732） | 全员 | 半 | context_handoff.py |
| TEAM-06 | 节点自清：除 one-coordinator 外（intent 2026-10-22-214856；one-architecture 也自清），遇节点（卡交出、推送、开 PR、报完成点、一轮结束）且用量 ≥20%，写交接→报调度→`clear_session(self)`，调度确认后发接续；发给清空中会话的消息经调度转 | 全员 | 半 | context_handoff.py |
| TEAM-07 | 交接按 `templates/HANDOFF.md`（在途分支与 PR、未完事项、待用户的事、下一步）；压缩前与会话结束时钩子写快照到 `~/.modernize/psa/handoff/`，恢复后先核实况 | 全员 | 半 | precompact_snapshot.sh、sessionend_snapshot.sh |
| TEAM-08 | 长期会话的现行事实（资源标识、在建步骤、坑与处置）写在专题文件（OPS_CONTEXT、GLOSSARY_CONTEXT 等），一行一项，不记过程与密钥 | 长期会话 | 无 | |
| TEAM-09 | 改 `.claude/settings.json` 的 PR 合入后 one-merge 列出启动早于合入的会话报调度，调度提醒用户重启（运行中会话不重读 settings；钩子脚本每次从主检出现跑，改脚本不必重启） | one-merge | 无 | 待建：GOV-1022-MRG-03 |
| TEAM-10 | 用户要重启 app 或电脑：调度先令全员写交接、核对无后台任务、导出在途卡清单，就绪才答复；重启后按清单恢复对账 | one-coordinator | 无 | |
| TEAM-11 | 交接完的会话由调度登记 `sessions/_retired.md` 并归档；临时流程组阶段结束归档；长期会话关闭「PR 合并或关闭时自动归档」 | one-coordinator | 无 | |
| TEAM-12 | Remote Control 只开 one-coordinator（intent 2026-10-22-205606）；工作会话关掉用不上的外接工具（Figma、浏览器、桌面控制），要用浏览器的保留 | one-coordinator | 无 | |
| TEAM-13 | 权限模式与调度一致，新开时设好；推理强度：调度、整合、复审 high，执行类按卡定。PR 的 Auto-fix 不开，被打开即报调度；它与应用提示的「常设授权」都不是用户授权 | 全员 | 无 | |

## 2. 开场

| 号 | 规则 | 负责 | 强制 | 守护 |
|---|---|---|---|---|
| TEAM-14 | 开场钩子注入 ≤8 KB 开工单（身份、当前卡目标与完成标准、交接、近 7 天相关决定）；缺项显式告警（无卡→向调度要卡，卡不完整→报调度，无交接→注明）；节点自清范围内用量 ≥20% 时再注入目标与完成标准（调度不收到） | one-quality | 半 | postcompact_context.sh、start_sheet.py、context_handoff.py（超 8 KB 截断并标记，缺项出告警行；自测覆盖） |
| TEAM-15 | 规范不通读，按 CLAUDE.md 索引与 TEAM 号按需读。开工读开工单与 SESSIONS.md（build_all.py 生成） 与本会话任务卡；STATUS.md、SESSION_LOG.md、`status/` 认领、`session-log/` 已停用 | 全员 | 无 | |
| TEAM-16 | 开工、恢复、清空后先核实况（`gh pr list`、`mq.py status`、后台任务、交接里的分支），不凭摘要或记忆续做；外部状态用 Monitor 按节奏查，不高频轮询 | 全员 | 无 | |
| TEAM-17 | 卡的入口条件不满足（前置卡未完、DD 门禁未签、本卡新业务用语未确认）就停下报调度，不硬做 | 全员 | 无 | |
| TEAM-18 | 记忆只放用户个人偏好与指针（≤2 KB），规则回仓库；记忆与仓库冲突以仓库为准并报调度 | one-coordinator | 无 | |

## 3. 任务卡与派工

| 号 | 规则 | 负责 | 强制 | 守护 |
|---|---|---|---|---|
| TEAM-19 | 任务卡由 one-coordinator 开、改、关；要新卡向调度提；负责会话可在本卡范围内给 Codex 或子代理开子卡 | one-coordinator | 无 | |
| TEAM-20 | 卡用 `templates/WORK_CARD.md`，必有：目标、完成标准（验收测试编号）、前置卡号、负责会话、单 bc、文件范围、依据、不得做、与 CI 及在途工作的衔接、资源与时限、自测与破坏验证 | one-coordinator | 无 | 待建：GOV-1022-QUA-14 |
| TEAM-21 | 一卡：一个可验收结果、一个负责会话、单 bc、1–3 个 PR、约半天到一天；超出就拆 | one-coordinator | 无 | |
| TEAM-22 | bc 定后开卡顺序：签发卡（DDL、接口契约、用语）→骨架卡→命令卡（一个命令或用例一卡）→集成、迁入、页面卡；前提缺项先开签发卡 | one-coordinator | 无 | |
| TEAM-23 | 任务号 `<主线>-<MMDD>-<会话码>-<nn>`，任务名 `[主线·bc] 动作+对象`（码表 `tasks/_README.md`）；派卡即写 `tasks/<任务号>.md`；PR 标题、代码提交、完成点报告带任务号 | 派卡会话 | 半 | commit_msg_gate.py |
| TEAM-24 | 卡状态现行取值：已派、进行中、阻塞、完成、作废（check_tasks.py、context_handoff.py 的节点检测都按此）；QUA-14、QUA-17 上线后归一为 5 值：已派、进行中、阻塞、已交、关闭（关闭写结局：合入、作废或并入）；暂缓记为阻塞并写出处；状态变即改「最后更新」，12 小时未更新由 check_tasks.py 列出 | 负责会话 | 半 | check_tasks.py；待建：GOV-1022-QUA-14（归一后阻断） |
| TEAM-25 | 卡交出用 `finish.py <任务号>`：核 PR 已开、复审结论已贴，卡改「已交」并写标记文件；收到合入通知后改「关闭」 | 负责会话 | 无 | 待建：GOV-1022-QUA-17 |
| TEAM-26 | 后台任务的 description 写 `<任务号或 #PR号> <任务名> (<执行者>)` | 全员 | 守 | bg_task_name_gate.py |
| TEAM-27 | 调度派工前对照作战图、METHOD 当前步骤、相关 ADR 与双方画像的独占、不碰；派工一到三行、背景进卡；调度建议标「建议」，只有用户原话标「用户决定」 | one-coordinator | 无 | |
| TEAM-28 | 无主设计（无卡或无负责会话的设计稿、提议稿、待建守护）每周一由调度扫出，开卡或标「不做」并写理由 | one-coordinator | 无 | 待建：GOV-1022-QUA-23 |
| TEAM-29 | 边界清楚、一个 PR、数小时能完的派子代理；长期线、要反复问用户或再并行的开会话。后台子代理结果只回最上层会话，再派用前台等待 | 全员 | 无 | |
| TEAM-30 | 用户已决定的照做，有异议停下报告，不自行推迟或改变。卡、规范、裁定与已采纳 ADR 字面不一致时不自称「落地细化」，起草时交 one-architecture 判定，判为偏离的进收件箱 | 全员 | 无 | |

## 4. 子代理、Codex、DeepSeek

| 号 | 规则 | 负责 | 强制 | 守护 |
|---|---|---|---|---|
| TEAM-31 | 派子代理（Agent、Workflow 的 `agent()`）必须写 `model` 并要求中文回复：haiku 取数与机械活，sonnet 常规工程、改后复核、调研，opus 只用于 TEAM-67 所列；提示写明范围与输出上限 | 全员 | 无 | 待建：GOV-1022-QUA-19 |
| TEAM-32 | 能交 Codex 或 DeepSeek 的先交：结果 Claude 能审出对错的编码、设计初稿、取数与文档整理；Claude 只写卡、首轮审、安全判定、验收 | 负责会话 | 无 | |
| TEAM-33 | 不交 Codex、DeepSeek：生产取证与连生产操作；身份、授权、凭据等安全改动；opus 复审；验收测试；对用户与会话的沟通；语义、权限、越权边界判断；钩子、并发、解析类守护代码（后两类只可整理材料或起草测试） | 负责会话 | 无 | |
| TEAM-34 | Codex 在独立 worktree 跑 `codex exec -m gpt-6.1-sol -c model_reasoning_effort=<强度> -s <沙箱> -c sandbox_workspace_write.network_access=false --ephemeral -o <结果> < /dev/null`，`-s` 必写（read-only 或 workspace-write）；强度按卡：机械 low/medium、起草 medium/high、判定 xhigh | 负责会话 | 半 | codex_stdin_gate.py |
| TEAM-35 | DeepSeek 在独立 worktree 跑 `pi -p --no-session --no-extensions --no-mcp --offline --model deepseek/deepseek-flash --tools <最小集> "<卡>" < /dev/null`（用户付费账户，`pi auth check` 查就绪）；手工跑 pi 先 cd 到工作区外 | 负责会话 | 半 | codex_stdin_gate.py |
| TEAM-36 | Codex、DeepSeek 一律 `run_in_background` 启动，不用 nohup、`&`、setsid，不轮询；前端命令用 `pnpm --offline --config.verify-deps-before-run=false`，缺依赖即报告，不联网补装 | 负责会话 | 无 | |
| TEAM-37 | 不改同一批文件的卡各开 worktree 同时跑，改同一文件的串行，以 CI 不明显变慢为度；并行产出整合前同步 main、重跑全部守护（CS-7.6） | 负责会话 | 无 | |
| TEAM-38 | Codex、DeepSeek 产出照常开 PR、过 CI、opus 首轮，PR 注明执行者、模型、强度；卡记返工轮次与用时，同类返工两轮以上收回 Claude；两者分工按同卡对拍定 | 负责会话；对拍 one-quality | 无 | |
| TEAM-39 | 取证与运维子代理：不改、不包装、不在仓库外跑运维与通道脚本，不装依赖（用 `~/.modernize/psa/ops-venv`），凭据不足即停下回报 | 派工会话 | 无 | |

## 5. PR 与合并

| 号 | 规则 | 负责 | 强制 | 守护 |
|---|---|---|---|---|
| TEAM-40 | PR 要能单独合入、复审、回退，合入后 main 仍绿；守护、CI、钩子改动、跨 bc 接口、机械改名、部署阻塞各自单开；一个提交不混文档与代码 | 作者 | 无 | |
| TEAM-41 | 非生成改动不超过 800 行、15 个文件；生成物不手工合，冲突时重新生成（mq.py 自动处理） | 作者 | 无 | 待建：GOV-1022-QUA-21 |
| TEAM-42 | 中间改动只提交、不开 PR；工作单元完成（验收、实现、opus 首轮齐备）才开；顺手的小修并入同主题的下一个 PR | 作者 | 无 | |
| TEAM-43 | 每个会话同时开着的代码 PR 不超过 3 个；改同一批文件的用 `mq.py enqueue --after` 排序，作者自行同步 | 作者；one-merge | 无 | 待建：GOV-1022-MRG-04 |
| TEAM-44 | 提 PR 前 `scripts/precheck.sh` 全过（按改动选跑，本机跑不了算失败），输出贴 PR「precheck.sh 输出」栏；`git push` 单独一次调用，改文件与提交在前一次调用完成 | 作者 | 半 | precheck_before_pr.py、pre_push_gate.py（贴输出、分次调用不查） |
| TEAM-45 | PR 开出即报调度；调度核对后在 PR 上评论「调度批准入队 head <sha>」，one-merge 只认这条评论入队（GOV-1022-MRG-01 上线前，调度以消息批准并写明 head）；opus 首轮结论到达前不批 | 作者；调度 | 无 | 待建：GOV-1022-MRG-01 |
| TEAM-46 | 调度批准前核对：产出与报告一致、生成文件可重现、无凭据原值、已采纳 ADR 只改头部可变字段、已应用迁移未改、代码改动对得上 `RULE_REVIEWS.json` 结论、登记文件改动有 one-data 签发行 | one-coordinator | 半 | build_all.py --check、migrations_immutable.py、review_revisions.py；待建：GOV-1022-QUA-16 |
| TEAM-47 | 合并只由 one-merge 经 mq.py 队列执行，任何会话不得 `gh pr merge` 或网页合并；部署与阻塞相关随时插队；已复审、约 5 个文件内的小 PR 由调度定分组、one-merge 执行合批 | one-merge | 半 | mq.py 合并锁；待建：GOV-1022-QUA-20 |
| TEAM-48 | 合并前必过 backend verify、frontend verify、secret scan（非 light）；冲突与 CI 红由 one-merge 退回作者并报调度；超 48 小时无进展的 PR 调度追问，推进或关闭 | one-merge | 半 | ci.yml（三项检查） |
| TEAM-49 | 合入后 mq.py 向负责会话与调度发「#N 已合入（任务号）」，负责会话据此关卡；其余状态以 GitHub 为准，不另发消息 | one-merge | 无 | 待建：GOV-1022-MRG-02 |
| TEAM-50 | 快慢两道（现状）：分支保护 strict 关闭；mq.py 只合入含当前 main 且全量 CI 已过的 head，main 推送运行或每晚运行变红即暂停两道。main 因并行合入变红一次，one-merge 报调度后即恢复 strict，试行结束 | one-merge | 半 | mq.py 暂停 |
| TEAM-51 | 入队后作者不再往该分支推（后续改动另开 PR），被退回后再推；推送前核对当前分支；force-push 已开的 PR 先告知调度 | 作者 | 无 | |
| TEAM-52 | PR 模板各栏如实填（断言或测试改动及理由、新增术语、OpenAPI 与架构文档同步、执行者、绕过推送钩子）；opus 首轮必查断言栏 | 作者；首轮复审 | 半 | assertion_drift.py（只告警） |

## 6. 文档提交

| 号 | 规则 | 负责 | 强制 | 守护 |
|---|---|---|---|---|
| TEAM-53 | 跨会话消息只在四个节点各发一条：开 PR、CI 全绿请批、合入、交接；提交号写进 PR 描述或分支，不单独发。文档类（intent、台账、规则文本、用语、画像、提议稿、任务卡）不开 PR，推到本会话分支、提交信息带任务号即可，由 one-merge 建合集时自扫远端认领，不再报提交号；要调度批或裁的才报调度（用户 2026-10-23，intent 111209） | 全员 | 半 | 待建：GOV-1023-MRG-11 |
| TEAM-54 | one-merge 每天中午、晚上各收一次文档合集（逐个提交、只收 docs/，已批 ADR 的 PR 并入），合入后各会话同步 main | one-merge | 无 | |
| TEAM-55 | 提议稿（架构、研究草稿）留在分支，到里程碑（评审完成且用户确认）再合，中间版本不进 main | 起草会话 | 无 | |
| TEAM-56 | 只改片段与源、不改生成产物：`intent/`、`tasks/`、`adr/src/`、`phase1a/infra/legacy-read/`、`glossary/issues/`、`backend/glossary/proposed/*.yaml`（仓库根相对）一条一文件，改后跑 `build_all.py`；产物不入仓、发布到 generated-docs 分支；冲突时不入仓产物 `git rm --cached`，入仓的重新生成 | 全员 | 守 | build_all.py --check |
| TEAM-57 | 进不进 git：机密与个人信息永不进；可由库内生成的不入库；他人或 CI 作依据的入库；一人高频自用的不进 main（在线页、本会话分支或 scratchpad）；一事一个权威出处；一次性产出进 `.gitignore` | 全员 | 半 | gitleaks、build_all.py --check |

## 7. 消息、收件箱、确认页

| 号 | 规则 | 负责 | 强制 | 守护 |
|---|---|---|---|---|
| TEAM-58 | 跨会话消息只在对方要做事或决定时发，不回纯确认；一次说完、一到三行、不复述来信，长内容给文件路径；首行写类别（派工、报告、请用户决定、通知）；子会话不开完成通知，确需等结果才开 | 全员 | 无 | |
| TEAM-59 | 完成点与结束前必须报调度，未报不算完成；含提交号与分支、产出、验证方式、未完成项、需用户决定的事；文档提交号按 TEAM-53 报 one-merge | 全员 | 无 | |
| TEAM-60 | 派工或要对方动手用 ccd `send_message`（delivered 即开工）；技术协调会话间直连，不经调度转述、不抄送，影响他人排期或要用户定的才报调度 | 全员 | 无 | |
| TEAM-61 | 外发被限流时写进 `~/.modernize/psa/outbox/<会话名>.md`（一行一条，带时间与收件会话）后继续干活，没活写「空闲」；调度巡检代转并标「已转」 | 全员；调度代转 | 无 | |
| TEAM-62 | 收件箱是用户决定的唯一处理点：要用户定的写进收件箱（来源、选什么、卡住什么、紧急度），先经调度审题；已在 GitHub 发起的在原 PR 回复并在箱里建指向。不在会话里问用户，一轮结尾也不提问 | 全员；调度 | 无 | |
| TEAM-63 | 出题（确认页、批准页、收件箱事项）前对照 `DECISION_DIGEST.md` 并引用条目；与已定决定相悖的列入「待用户裁定」或写明为何重问 | 出题会话 | 半 | check_pages.py |
| TEAM-64 | 确认页按统一模板出：Artifact 加 ArtifactData 自动保存、版式简单、只用系统字体栈；出题会话出页、调度只审题；发布后用浏览器核对渲染（细则 `rules/decide/README.md`） | 出题会话；模板 one-rules | 半 | check_pages.py；待建：GOV-1022-QUA-15 |
| TEAM-65 | 用户在任何会话口头作的决定，当场 `new_intent.py` 记原话、报调度、登记收件箱「已定」；同一提交改受影响的规则表条、画像、台账 | 全员 | 无 | |
| TEAM-66 | 调度每天早上给用户一份简报（昨日合入、主线进度、今日待定），平时只推卡住主线的事；回复结论先行，转述只取用户需知或需定的部分 | one-coordinator | 无 | |

## 8. 复审与模型

| 号 | 规则 | 负责 | 强制 | 守护 |
|---|---|---|---|---|
| TEAM-67 | 会话主模型：执行类 sonnet，调度、架构、结构主线 opus。opus 只用于：每个代码或守护 PR 首轮复审一次、安全判定（授权、凭据、生产授权、数据分级）、ADR 起草与首轮评审、结构设计；不重复派同一件事 | 全员 | 无 | 待建：GOV-1022-QUA-19 |
| TEAM-68 | 复审分级：安全与生产（运维脚本、权限、凭据、守护）opus 首轮加修后 opus 终审；代码 opus 首轮加 sonnet 复核；纯文档作者自检。最多两轮，之后新发现的非 High 列残余项单列，不阻塞 | 复审方 | 无 | |
| TEAM-69 | 验收测试由独立测试代理先写先提交，实现方不改；改动须带 `Acceptance-Test-Author: test-engineer` 或 `Acceptance-Ruling: RULING-<n>`。裁定结论由独立复审出具，调度据此签发 RULING-COORD-* 登记、不自拟结论，卡负责与实现会话不得出裁定（附 M1 第 4 条） | 测试代理；复审 | 半 | acceptance_guard.py、acceptance_rulings.py、acceptance_reports.py |
| TEAM-70 | 宣告完成、可用、通过须有可复现验证并写明方式；转述他人结论前核原文，标「已核实 / 已抽查 / 未核实」，以原文为准 | 全员 | 无 | |
| TEAM-71 | 三道防线：①机械检查（测试、守护、precheck）；②按 `quality/review-briefs/` 模板独立评审，评审员自读原始材料；③交出前回测错题本 `quality/defect-bank/<会话名>.md`，「已落实」须指向真实守护 | 全员；模板 one-quality | 半 | defect_bank_check.py |
| TEAM-72 | 新增或改动的测试类在引入它的 PR 上重复跑 3 遍；每晚按错题本报错特征归类 CI 失败，命中「已落实」条目即判该检查失效须修 | one-quality | 守 | repeat_new_tests.py、ci_recurrence.py |
| TEAM-73 | 选框架、标准、选型前写本系统适配研究（要求、代价、收益），出页前派 opus 对抗式评审；外部知识先查 `~/Desktop/公开资料`（kq.py）、`~/Desktop/公开资料研究`（library.sqlite）、`~/Desktop/企业资料`（INDEX.md，个人信息不进会话）原文并引书名页码；架构节点按框架原文判定 | 架构类会话 | 无 | |
| TEAM-74 | 每周一调度跑守护出数（附表 C ①–③、陈旧卡、超期 ADR 提议、在制 PR、Codex 返工率）写进当日日报，代替每周胜任力评审与 ADR 周复审台账；抽审按风险派独立代理回测 | one-coordinator | 无 | 待建：GOV-1022-QUA-23 |
| TEAM-75 | 调度每天早上和交接时各用 `get_usage` 记一次用量到台账「额度」行 | one-coordinator | 无 | |

## 9. 记录与状态

| 号 | 规则 | 负责 | 强制 | 守护 |
|---|---|---|---|---|
| TEAM-76 | 唯一工作状态是任务卡片段；画像不写状态；台账只供调度自用，其他会话的画像与其他会话不引用，要共享的（暂停项等）写进本表或卡 | one-coordinator | 半 | build_sessions.py（画像出现「当前状态」节即失败） |
| TEAM-77 | 用户决定用 `new_intent.py --session=<会话名> "<原话>" "<落点>"` 记；背景与理由只进 intent，不进 ADR、不进本表 | 全员 | 无 | |
| TEAM-78 | 后台任务与持久配置（定时器、launchd、runner、云上临时资源）建立即登记 `phase1a/infra/RESOURCES.md`，用完清理并记录 | 建立方；one-ops | 无 | |
| TEAM-79 | 会话内定时任务迁到 GitHub 工作流，不依赖会话存活；迁完之前，台账登记在跑的会话内定时任务 | one-quality | 无 | 待建：GOV-1022-QUA-22 |
| TEAM-80 | brief 只勾选已满足的条件，不改写；改条件追加 `Proposed revision:` 行。记录与事实不一致时以可验证的事实为准并更正 | 全员 | 无 | |

## 10. 规则变更

| 号 | 规则 | 负责 | 强制 | 守护 |
|---|---|---|---|---|
| TEAM-81 | 规则集负责：本表、CLAUDE.md、画像体系 one-coordinator；CODING_STANDARD 条文 one-architecture（one-quality 管守护）；NAMING_STANDARD 按现分工；DATA_DESIGN_CHECKLIST one-data | one-coordinator | 无 | |
| TEAM-82 | 改规则：记 intent→改本表原条（不追加「增补」）→同一提交改掉所有引用（grep TEAM 号与旧节号）；新规则取下一号，废止的条留号写「废止：<intent 文件名>」 | 规则负责会话 | 无 | 待建：GOV-1022-QUA-11 |
| TEAM-83 | 「守」只标在已注册的守护上（settings.json、.pre-commit-config.yaml、ci.yml、precheck.sh），做不到的标「待建：卡号」并列入附表 C；守护上线的同一提交改为「守」 | one-quality | 无 | 待建：GOV-1022-QUA-11 |
| TEAM-84 | 待建守护 one-quality 按风险只排前 10，实现交 Codex（钩子、并发、解析类自写，opus 复审）；其余标「复审」或「不做」写理由 | one-quality | 无 | |
| TEAM-85 | 协作规则只写本表，不开 ADR（ADR-0020 的协作规则以本表为准）；架构决定、配置类与代码类规则的变更按附 M3 | 全员 | 无 | |

## 11. 本机与资源

| 号 | 规则 | 负责 | 强制 | 守护 |
|---|---|---|---|---|
| TEAM-86 | 只在自己的 worktree 分支工作，主检出不留改动；git 写操作用绝对路径，先核对目录与当前分支；分支名 `claude/<会话名>-<主题>`（NS 第 21 项旧写法由 one-glossary、one-architecture 改） | 全员 | 半 | main_checkout_gate.py（主检出） |
| TEAM-87 | 钩子或 CI 挡住按提示改，不用 `--no-verify`；确需绕过推送前钩子须带 `PUSH_BYPASS_REASON` 并在 PR 说明栏写明 | 全员 | 半 | pre_push_gate.py（只管 push）、pre_tool_dispatch.py（命令匹配防大小写与引号拆词，#616）；余下绕过手法见 GOV-1022-COO-17 |
| TEAM-88 | 本地只跑改动相关模块的测试，全量交 CI（卡另有要求除外）；重活排队进 heavy_slot.py 两个全机槽位；CI runner、本机资源、降负载、`deploy_gate.py` 归 one-ops | 全员；one-ops | 半 | precheck.sh |
| TEAM-89 | Testcontainers 在本机起、测完回收、镜像在 `backend/architecture/test-images.txt` 登记的临时容器不必问；其他容器、常驻容器先问用户 | 全员 | 无 | |
| TEAM-90 | 改文件用 Edit 改片段，不脚本整篇重写；长输出先筛选、存文件按需读，失败段落完整读；大块阅读交子代理并要求注明文件与行号 | 全员 | 半 | context_handoff.py（超长提示） |

## 12. 安全、生产与须用户决定

| 号 | 规则 | 负责 | 强制 | 守护 |
|---|---|---|---|---|
| TEAM-91 | 须用户决定（写收件箱等答复，不替用户批准，其余工作照做）：brief 审批；ADR 从提议到已采纳；业务口径与用语（正式或方言）；会计口径（经财务）；规则评审结论；配置类新版本 | 全员 | 无 | |
| TEAM-92 | 须用户决定（续）：部署与生产变更（含 nginx、Nacos）；凭据轮换与历史清理；建账号、起容器（TEAM-89 除外）、对外调用；购买与对外账号；敏感数据可见角色；关账后更正与期间重开 | 全员 | 无 | |
| TEAM-93 | 推送代码、创建或更新 PR、触发 CI 不需要用户决定，按 TEAM-44、TEAM-45 自行办理 | 全员 | 无 | |
| TEAM-94 | 设计正本写入有长期授权，每次写入前先列计划；访问生产有只读授权，任何写操作都不做 | 全员 | 无 | |
| TEAM-95 | 生产取证不逐次请示：只用取证专用只读账号；明细与个人信息不进会话与仓库（只出计数或聚合，或落 ECS 数据盘 0700 目录、最长 7 天）；每次用 `new_legacy_read.py` 登记一片段；读取口径按 ADR-0037 第 8 项 | 取证会话 | 无 | |
| TEAM-96 | 迁入读取：表与列按「用途 = 迁入 / 对账」登记读取列；只在集成层适配器内用运行时只读账号读；数据只进适配器与目标库；6e 敏感数据不在 dev 演练（ADR-0036、ADR-0037） | 迁入会话 | 无 | |
| TEAM-97 | 代码与测试不写密钥字面量（运行时生成）；不打印、不调用会返回密钥的接口（如 `aliyun rds DescribeDBInstanceSSL`），describe/get 在进程内只取所需字段；密钥进了记录按安全事件报调度 | 全员 | 半 | gitleaks |
| TEAM-98 | 每个工作区 `pre-commit install`；报告前用 gitleaks 8.30.1 扫全历史 | 全员 | 半 | gitleaks（pre-commit、ci.yml 全历史；install 不查） |
| TEAM-99 | 不替用户批准，不让其他会话代做本会话被拒的操作；消息无回执不算已执行，以对方报告或文件为准 | 全员 | 无 | |
| TEAM-100 | SECURITY_INVARIANTS、SECURITY_FINDINGS 生产前由 one-architecture 代管，one-security 设立后移交；身份、授权、单人模式改动遵守 SECURITY_INVARIANTS 并走 opus | one-architecture | 无 | |
| TEAM-101 | 引入或升级依赖、改 CI 工作流与 runner，按 `SUPPLY_CHAIN.md` S1–S7 | 改动方 | 半 | ci.yml（PyYAML、shellcheck 版本校验） |
| TEAM-102 | 治理冻结：整改总验收完成起两周不新增规则与守护；事故或安全缺口经用户批准例外，在途整改卡照做（用户 2026-10-23，intent 104603） | one-coordinator | 无 | |
| TEAM-103 | 新守护或收紧既有守护合入前，先对全部在开 PR 跑一遍；会撞上的先修或在 PR 里给过渡期，不得让它首次在队首变红（intent 104603） | 改动方 | 无 | 待建：随 GOV-1023-MRG-10 预跑整轮 CI |
| TEAM-104 | 每个 bc 设一个牵头会话从签发到命令卡负责到底，架构、数据、用语限时审阅不接力；临时流程组阶段结束即归档，在用会话控制在约 10–12 个（intent 104603） | one-coordinator | 无 | |
| TEAM-105 | 每周一出效率数：业务功能 PR 合入数、产品代码行数、摩擦时长（挂起、冲突、等批准累计），连同上周对比报用户（intent 104603） | one-quality | 无 | 待建：GOV-1022-QUA-23 |
| TEAM-106 | 一张卡攒好再推：本地预检通过后一次推送，不边写边推；手动预检通过即认，推送门不重跑（用户 2026-10-23 复盘） | 全员 | 无 | 待建：GOV-1023-QUA-04 |
| TEAM-107 | 允许叠加 PR：后一张卡可以前一张卡的分支为基先开 PR，复审与签发同时进行；批准评论写「after #<前一张>」，前一张合入后后一张合 main 再入队（用户 2026-10-23 复盘） | 全员 | 半 | mq.py after 依赖（GOV-1023-MRG-07） |

## 附 M：方法（不是团队规则，仍有效）

### M1 修复批

1. 派批前列出可能要用户定的点，调度一次问完；画归属图（各代理的表、权限码、事件、迁移号段、共享构件）写进卡，越界先报告。
2. 复审对 Blocker、High、Medium 尽量给可复现的失败测试（测试名引复审编号），以转绿为准、留作守护。复核：Blocker、High 由 opus；Medium、Low 修复方补测试、CI 过即关，负责会话抽查。
3. 滚动整合：复审通过即并入整合分支，每并一路跑全量与守护，后到者解冲突；修复循环只跑受影响模块。修复 sonnet、复审 opus、逐条核对 sonnet。
4. 验收测试（TEAM-69）：`code-modernization:test-engineer` 从 contract.md、规则卡、ADR、用户原话写 Given/When/Then（测试名引 D、RULE 编号或 intent）；实现方认为测试有误就停下报告。目录、trailer、合并提交、裁定记录与 surefire/failsafe 报告要求见 `scripts/ci/acceptance_guard.py`、`acceptance_rulings.py` 头部与 `backend/src/test/java/com/forcitis/one/acceptance/README.md`。单元测试由实现方写。
5. 缺陷修复、功能补做（应交付未做）、结构性重构（含改名）分开派工，不混批、不混提交。
6. 缺陷修复手术式：一张缺陷卡（复审编号、复现步骤、预计改动文件）对应一次最小改动加一个先失败的回归测试；只改卡上文件，不顺手重构、改名、统一风格；一张或几张卡一个小 PR，复核过即合入，不攒大整合分支；无法局部修的标「结构性」写理由，转重构批。
7. 整合复审后：Blocker、High 合并前修完，opus 只复核这几条；Medium、Low 不阻塞，进下一批卡；新发现的应交付未做进下一批，安全漏洞本批先堵。

### M2 并行开发

- 并行只在互不相交的工作单元上（不同 bc，或不同阶段的不同步骤）。
- 公共基础先行：先由一个代理建好并冻结共用构件（聚合基类与版本模型、状态机、事件信封、错误链、前端共用包）；要改停下报告，由整合者改。
- 每个代理交付先单独 opus 复审（DATA_DESIGN_CHECKLIST §14、§14.1），通过才整合，整合后再整体复审；每波交付后 opus 专查「老系统形状」（照搬的数据结构、接口形状、流程），结论写进该波报告。

### M3 变更治理

- **ADR**：已采纳正文冻结，只改头部可变字段；决定有任何变化都新增 ADR 写明「取代」范围，同一提交给旧 ADR 标「已被取代」或「被部分取代」。新 ADR 必写影响面、迁移、回滚、需同步修改的守护（同一 PR 或写明紧随的 PR）。源在 `adr/src/`，`adr/build_adrs.py` 生成并校验，`new_adr.py` 开空壳 PR 占号；状态、头部、档位、勘误、评审、提议复审以 `adr/src/_readme_lifecycle.md` 为准，进不进 ADR 见 `adr/src/_readme_*.md`。协作规则只写本表。
- **规则调整三类**（拿不准取更严：配置 < 代码 < ADR）：配置类（DMN、业务编号、经营口径、政策参数）新增版本不覆盖，业务口径用户批、会计口径财务批，运行时版本表与 `CONFIG_CHANGELOG.md` 只追加，按与会计期间对齐的生效日期生效、不追溯、不重算；代码类（领域规则、计算、校验、状态机、DDL）先在 `RULE_REVIEWS.json` 增改结论，再改以规则 ID 命名的测试，最后改实现，brief §5 由 `rules/build_brief.py` 重生成；ADR 类（模块边界、选型、跨 bc 契约、主干不变量）占号→提议→首轮 opus 评审（正式档 `code-modernization:architecture-critic` 对抗评审，轻量档按 `quality/review-briefs/03-adr.md` 子集，起草者写检索记录供重放）→用户批准→已采纳。
- **修订评审结论**：`RULE_REVIEWS.json` 原字段不改，只在 `revisions` 末尾追加 `{verdict, at, note, quote, source}`，当前结论取最后一条，brief 标「已修订」；已决定条目不得用插件评审面板或 `/code-modernization:modernize-review` 改写；字段与基线比对见 `rules/review_revisions.py` 头部。
- **迁移基线**（ADR-0038；开放期内取代各处「Flyway 只新增」）：首次生产部署前基线开放（标记 `backend/architecture/migration-baseline-open`），dev 可丢弃，dev 执行过不算已应用；每 schema 一份基线，表结构改动直接改基线，PR 附「预期表结构 diff」（两个空库 `pg_dump --schema-only` 对比，opus 核对）。首次生产部署前单开 PR 删除标记（夹带迁移改动即 CI 失败），此后已合入迁移只追加。`deploy_gate.py` 对 dev 告警、对 prod 拦截；开放期内基线改后 dev 库按重建剧本重置。守护 `migrations_immutable.py`。
- **已过账事实**不改不删（ADR-0016 第 2 条），更正只走冲销：反向事实加新过账序号，原行保留并双向关联，按正确口径重做；关账后更正落开放期间并标原期间；配置新版本、规则修正、数据迁移都不回写。
- **谁可以改什么**：ADR 状态与撤回权以 `adr/src/_readme_lifecycle.md` 为准——起草与改提议稿工作会话可、调度可派；改为已采纳、已被取代、已废弃须用户批准后照原话改，调度不能代批；勘误任一会话起草、调度核对，拿不准 one-architecture 判；性质改变由 one-architecture 起草并须有用户原话；已采纳正文谁都不改。配置新版本起草并登记 CONFIG_CHANGELOG（待批准）由用户批；代码类规则修正先提结论草案，用户确认后改测试与实现；关账后更正、期间重开须财务批准。

### M4 流程梳理（每阶段实施前）

- 阶段入口条件之一，调度派给负责会话，一阶段一单元；模板与 contract.md 格式见 `process/README.md`。每个端到端流程写现状（每步附证据：老代码 file:line、`OA_PROCESS_INVENTORY.md`、注日期与出处的生产只读计数）、目标（依规格、ADR、规则卡与已有决定）、差异清单（`D-<阶段>-<序号>`）。
- 差异逐条由用户定，不整批默认，原话记 intent；contract.md 只收已定差异，未决的不进、入口不勾。改变规则的差异按 M3 代码类追加 `revisions`，新规则补抽规则卡。
- 证据走已授权只读通道（`runtime/README.md`），按 TEAM-95；6a–6f 现状引 `OA_PROCESS_INVENTORY.md`。子代理：读老代码 sonnet，生产计数 haiku，差异判断 opus。
- 数据模型先行：梳理后、派功能代理前，逐表按 DATA_DESIGN_CHECKLIST §14.1 与 `phase1a/DATA_SCORECARD.md` 打分；表结构 PR（迁移、表与列注释、数据访问注册表）单开，合并后才派业务代码；入口条件「表结构评审已完成」。
- DD 门禁（已生效，`data/DATA_DESIGN_GATE.md`）：改表、列、约束、索引、事件载荷、读模型、迁入映射的卡，派验收测试代理前须 one-data 在卡上签「数据设计：通过（one-data，日期）」；顺序：卡→one-data 签发→表结构 PR（附预期 diff，one-data 复核数据项、opus 复审结构）合并→验收→实现。
- contract.md 每条与 ADR 要求本阶段交付的每条至少对应一个测试（测试名或注解引 D 编号或 ADR 条款），CI 守护对照；无测试不算交付。
- 横切关注点（舍入与拆分、时区、幂等、分页、导出、保留期等，类别定义见 `CROSS_CUTTING_MATRIX.md`）：梳理前负责会话更新矩阵——本阶段首次或加重的行重核证据与类别，补漏一行一条写依据，「已覆盖」附证据；待决行写成选择题汇入 `CROSS_CUTTING_DECISIONS.md` 由调度一次问清；地基行派功能代理前做进 kernel 或 platform 并冻结；阶段行进梳理入口；必问本阶段新敏感数据的可见角色，敏感权限只授给具体的人（两人确认、季度复核），不经岗位或同步自动授予。brief 各阶段入口加「矩阵已更新、待决已答、地基已冻结」。

### M5 写代码规则（CODING_STANDARD 引此为出处）

- 老代码只作证据：老仓库（psa、A8、U8C 及库表）只用于抽规则、写 file:line 引用、做等价判定；新代码只从规格、规则卡、ADR、terms.yaml 写起，不复制老代码的片段、类结构、表结构、接口路径、命名（含 `psa_`、`system_`、`bpm_` 表名、`/admin-api/`、yudao 包名与基类、拼音缩写）；等价靠以 RULE-id 命名的测试。守护 LegacyShapeTest，例外只限已批准的防腐层并经评审。
- 注释默认不写，只在「为什么」看不出时写一行（反直觉约束、规范条款、外部怪癖、性能或并发取舍）；不写复述、历史、作者日期、注释掉的代码、无编号 TODO、显而易见参数的 Javadoc；公开契约只写签名表达不了的约定；复审把多余注释当问题。
- 第二批（用户 2026-10-14）：①复刻老行为以生产数据或日志（只读）为证；②不为过测试放宽或删断言，改则在报告与 PR 单列理由；③缺失或无效输入失败并告警，规则卡明确允许的除外；④只实现当前阶段所需；⑤用语先查词表（M6）；⑥测试不写密钥字面量；⑦测试只断言本模块效果，异步用带上限的确定性等待，不写宽泛 `never()`；⑧契约先行，接口路径、必填字段、表名变化时 OpenAPI 与架构文档同提交更新；⑨并行子代理同一基线，整合提交重跑全部守护；⑩建账号、起容器、对外调用先问（TEAM-89、92）；⑪能一开始做对的不留后续，推迟须写「为何后做更有价值」（④管范围，⑪管质量）。

### M6 用语

- 登记：新标识符、界面文字、表列名先查 terms.yaml，查不到当场登记提议术语，写使用范围（domain、kernel、acl）与 bc；kind 只取 DDD 战术分类，聚合根与领域事件都要有词条，事件=聚合名加过去式业务动词，命令用祈使动词（G40–G42，`glossary/term_rules.py`）；同一 bc 内中英文名都查重；不同 bc 同名各自登记（id 加 `<bc>__` 前缀）并在 homonyms 互列；错误码中文名全局唯一（G48、R-5、CS-2.24）。
- 术语门禁、批次清理、决策单、写回的流程见 `glossary/gate/README.md`（门禁：测试代理在卡上写「本卡新词」，one-glossary 当天预判，业务用语全部确认后才派实现，提前派须写理由报调度；决策单：业务用语积压 100 条、距上次超 7 天或阶段出口前出；写回：改名 PR 先合、forbidden 后合，先禁用须带到期日与负责会话的允许清单）。另：整合会话先对本批提议去重归并；错误码单列不计门槛、阶段出口前清零；出口不留未确认的会计类提议（ADR-0018）。
- 文档里的方言、同名异义用 `glossary/new_term_issue.py` 登记（一条一文件），不自行裁定，由 one-glossary 处理。

## 附表 A 角色（GOV-1022-COO-15 定稿）

- 长期：one-coordinator（派工与任务卡、批 PR、规则集与画像、收件箱、额度）；one-architecture（架构正本、ADR、施工图、平台准入、CODING_STANDARD 条文，安全清单代管至 one-security）；one-data（数据设计签发、DD 门禁、迁入、登记文件签发）；one-glossary（用语与命名词源）；one-rules（规则卡与业务决策题、确认页模板）；one-quality（CI、测试、守护、钩子）；one-merge（合并队列、文档合集、分支清理、合入通知）；one-ops（云资源、部署、本机资源与 CI runner、deploy_gate）。
- 工作（按任务卡，一卡一单元）：one-1a-par、one-1a-next-5、one-1a-admin、one-platform-file。
- 临时（流程组，价值流骨架与表级模型，阶段结束归档）：one-arch-finance、one-arch-people、one-arch-revenue。

## 附表 B 已注册守护（主检出 main，2026-10-22 核）

- settings.json：PreToolUse 经 pre_tool_dispatch.py 跑 bg_task_name_gate、main_checkout_gate、codex_stdin_gate、pre_push_gate、precheck_before_pr；context_handoff.py（PostToolUse、UserPromptSubmit、Stop）；SessionStart 跑 git_ceiling_env.sh、postcompact_context.sh；PreCompact、SessionEnd 跑快照脚本。
- .pre-commit-config.yaml：gitleaks、main-checkout-gate、commit-msg-gate。
- ci.yml：build_all.py --check（含 build_sessions、check_tasks、defect_bank_check、build_adrs）、migrations_immutable、acceptance_guard/rulings/reports、repeat_new_tests、review_revisions、assertion_drift、gitleaks 全历史；precheck.sh 另跑 check_pages、heavy_slot；ci-recurrence.yml 每晚跑 ci_recurrence.py。

## 附表 C 待建守护（总方案第二节第 11 条；详单 PB_GUARDS_TODO.md）

| 卡号 | 内容 | 实现 |
|---|---|---|
| GOV-1022-QUA-11 | ①标「守」的条其守护存在且已注册；TEAM 号引用不悬空 | 自写 |
| GOV-1022-QUA-13 | ③画像独占路径两两无相交 | Codex |
| GOV-1022-QUA-14 | ⑤卡状态先归一再 5 值阻断；缺目标或完成标准即失败 | Codex |
| GOV-1022-QUA-15 | ⑥check_pages 查外部字体、状态值、runtime | Codex |
| GOV-1022-QUA-16 | ⑦登记文件改动的 PR 须有 one-data 签发行 | 自写 |
| GOV-1022-COO-17 | ⑧钩子命令匹配防绕过的余项（`$'g\x69t'`、通配符、`bash <<EOF`、`env -S`、`git -c alias.`、`gh -R`） | 自写 |
| GOV-1022-QUA-17～23、MRG-01～04 | finish.py、自清推全员、子代理 model 必填、拒 `gh pr merge`、PR 尺寸、定时迁工作流、周一出数；批准评论入队、合入通知、重启清单、在制上限 | 见详单 |

## 附表 D 旧节号对照（逐条详单 PB_MAPPING.md）

§0、§1、§2 表、§8–§10 删除（背景进 intent），仍有效的并入 TEAM-05、07、15、16、56、80；§2.1 → TEAM-01–13、57、78、97；§3、§4 → TEAM-14–17、44、53、56、59、65、77、86；§5 → TEAM-23–26、29–39、67、70、73、90、95–96、101 与附 M5、M6；§6、§6.1 → 附 M2、M1；§7 → TEAM-91–94；§11 → TEAM-19、27、45–50、53–54、58–66、68、71、74–76、99；§12.1–12.5 → 附 M3、TEAM-46、85；§12.6 → TEAM-57；§13 → 附 M4；§14–§14.3 → `DATA_DESIGN_CHECKLIST.md` 同节号。
