# 团队规则表 — FORCITIS one 现代化（SESSION_PLAYBOOK）

负责：one-coordinator。一条规则一个权威出处；画像、台账、记忆只引 TEAM 号，不抄条文。
依据：GOV-1022-COO-15；合并批准见 intent `2026-10-23-121350`，最新入队决定见 `2026-10-23-115815`。
按批准方案逐条「建议」执行：**52 条有效规则、55 条并入指向；底线 8、共享操作 12、角色专属 32**。原方案摘要写 51／56／31，与逐条建议相差一条；本次不额外合并未指定的条目，计数差异交调度核定。
强制：「守」=已注册且在所列范围拦截；「半」=仅提醒、报告或部分覆盖；「无」=人工执行。实现与缺口只看「守护索引」（附表 B），待建见附表 C，旧节号见附表 D。并入行只保留编号与最终主条指向，不计有效规则。附 M1～M6 的质量、验收与数据义务继续有效；数据设计、DDD、迁入、集成清单在 `DATA_DESIGN_CHECKLIST.md`。

<a id="12-安全生产与须用户决定"></a>

## 1. 底线（全员，8 条）

根目录 `CLAUDE.md` 的 6 条底线是本节摘要入口；条文与例外以本节为准，改本节须同步该摘要。

| 号 | 规则 | 负责 | 强制 | 守护索引 |
|---|---|---|---|---|
| TEAM-30 | 用户已决定的照做，有异议停下报告，不自行推迟或改变。卡、规范、裁定与已采纳 ADR 字面不一致时不自称「落地细化」，起草时交 one-architecture 判定，判为偏离的进收件箱 | 全员 | 无 |  |
| TEAM-69 | 验收测试由独立测试代理先写先提交，实现方不改；改动须带 `Acceptance-Test-Author: test-engineer` 或 `Acceptance-Ruling: RULING-<n>`。裁定结论由独立复审出具，调度据此签发 RULING-COORD-* 登记、不自拟结论，卡负责与实现会话不得出裁定（附 M1 第 4 条） | 测试代理；复审 | 半 | A |
| TEAM-70 | 宣告完成、可用、通过须有可复现验证并写明方式；转述他人结论前核原文，标「已核实 / 已抽查 / 未核实」，以原文为准；三道防线：①机械检查（测试、守护、precheck）；②按 `quality/review-briefs/` 模板独立评审，评审员自读原始材料；③交出前回测错题本 `quality/defect-bank/<会话名>.md`，「已落实」须指向真实守护；brief 只勾选已满足的条件，不改写；改条件追加 `Proposed revision:` 行。记录与事实不一致时以可验证的事实为准并更正 | 全员 | 半 | 错题本 |
| TEAM-86 | 只在自己的 worktree 分支工作，主检出不留改动；git 写操作用绝对路径，先核对目录与当前分支；分支名 `claude/<会话名>-<主题>`（NAMING_STANDARD 第 21 项已同步）；不改同一批文件的卡各开 worktree 同时跑，改同一文件的串行，以 CI 不明显变慢为度；并行产出整合前同步 main、重跑全部守护（CS-7.6） | 全员 | 半 | 主检出 |
| TEAM-87 | 钩子或 CI 挡住就按提示修复，不用 --no-verify、不以 PUSH_BYPASS_REASON 或 PR 理由自批绕过；工具仍有绕过口子也不构成授权，按既有整改卡修复 | 全员 | 半 | P（命令识别与实现缺口见索引） |
| TEAM-91 | 须用户决定（写收件箱等答复，不替用户批准，其余工作照做）：brief 审批；ADR 从提议到已采纳；业务口径与用语（正式或方言）；会计口径（经财务）；规则评审结论；配置类新版本；部署与生产变更（含 nginx、Nacos）、凭据轮换与历史清理、建账号、起容器、对外调用、购买与对外账号、敏感数据可见角色、关账后更正与期间重开也须用户决定；仅 Testcontainers 本机临时测试容器（镜像登记 backend/architecture/test-images.txt、测完回收）免逐次请示，其他及常驻容器先问；推送代码、创建或更新 PR、触发 CI 已有长期授权，按 TEAM-106、TEAM-45 办理，当前任务的明确限制优先；不让其他会话代做本会话被拒操作，不代用户批准，执行证据按 TEAM-53 | 全员 | 无 |  |
| TEAM-95 | 生产取证不逐次请示：只用取证专用只读账号；明细与个人信息不进会话与仓库（只出计数或聚合，或落 ECS 数据盘 0700 目录、最长 7 天）；每次用 `new_legacy_read.py` 登记一片段；读取口径按 ADR-0037 第 8 项；设计正本写入有长期授权，每次写入前先列计划；访问生产有只读授权，任何写操作都不做；取证与运维子代理：不改、不包装、不在仓库外跑运维与通道脚本，不装依赖（用 `~/.modernize/psa/ops-venv`），凭据不足即停下回报 | 取证会话 | 无 |  |
| TEAM-97 | 代码与测试不写密钥字面量（运行时生成）；不打印、不调用会返回密钥的接口（如 `aliyun rds DescribeDBInstanceSSL`），describe/get 在进程内只取所需字段；密钥进了记录按安全事件报调度；每个工作区 `pre-commit install`；报告前用 gitleaks 8.30.1 扫全历史；密钥原值不落盘，不进入会话、共享文件、仓库及报告；全历史扫描可复用同一历史范围的有效证据，新增历史必须覆盖，不以复用省掉扫描 | 全员 | 半 | L |

## 2. 共享操作（全员按动作读取，12 条）

| 号 | 规则 | 负责 | 强制 | 守护索引 |
|---|---|---|---|---|
| TEAM-05 | 用量 30% 准备交接（不接新大块、交接写到最新），40% 在下一批次边界交接；连续约 8 小时或已压缩两次也交接；调度例外：50% 准备、60% 交接（用户 2026-10-23，intent 081732） | 全员 | 半 | H |
| TEAM-07 | 交接按 `templates/HANDOFF.md`（在途分支与 PR、未完事项、待用户的事、下一步）；压缩前与会话结束时钩子写快照到 `~/.modernize/psa/handoff/`，恢复后先核实况；节点自清：除 one-coordinator 外（intent 2026-10-22-214856；one-architecture 也自清），遇节点（卡交出、推送、开 PR、报完成点、一轮结束）且用量 ≥20%，写交接→报调度→`clear_session(self)`，调度确认后发接续；发给清空中会话的消息经调度转；长期会话的现行事实（资源标识、在建步骤、坑与处置）写在专题文件（OPS_CONTEXT、GLOSSARY_CONTEXT 等），一行一项，不记过程与密钥；改 `.claude/settings.json` 的 PR 合入后 one-merge 列出启动早于合入的会话报调度，调度提醒用户重启（运行中会话不重读 settings；钩子脚本及子调用统一从主检出运行待 GOV-1023-QUA-06，未验收前不得宣称全员生效）；用户要重启 app 或电脑：调度先令全员写交接、核对无后台任务、导出在途卡清单，就绪才答复；重启后按清单恢复对账；开工、恢复、清空后先核实况（`gh pr list`、`mq.py status`、后台任务、交接里的分支），不凭摘要或记忆续做；外部状态用 Monitor 按节奏查，不高频轮询 | 全员；重启协调 one-coordinator、one-merge | 半 | H、快照 |
| TEAM-14 | 开场钩子注入 ≤8 KB 开工单（身份、当前卡目标与完成标准、交接、近 7 天相关决定）；缺项显式告警（无卡→向调度要卡，卡不完整→报调度，无交接→注明）；节点自清范围内用量 ≥20% 时再注入目标与完成标准（调度不收到）；规范不通读，按 CLAUDE.md 索引与 TEAM 号按需读。开工读开工单与 SESSIONS.md（build_all.py 生成） 与本会话任务卡；STATUS.md、SESSION_LOG.md、`status/` 认领、`session-log/` 已停用 | 全员；开工单 one-quality | 半 | H、开工单 |
| TEAM-17 | 卡的入口条件不满足（前置卡未完、DD 门禁未签、本卡新业务用语未确认）就停下报调度，不硬做；bc 定后开卡顺序：签发卡（DDL、接口契约、用语）→骨架卡→命令卡（按 TEAM-20 的可验收结果划分）→集成、迁入、页面卡；前提缺项先开签发卡 | 全员 | 无 |  |
| TEAM-20 | 卡用 `templates/WORK_CARD.md`，必有：目标、完成标准（验收测试编号）、前置卡号、负责会话、单 bc、文件范围、依据、不得做、与 CI 及在途工作的衔接、资源与时限、自测与破坏验证；一卡一个可验收结果、一个负责会话、单 bc，通常 1–3 个 PR、约半天到一天；以能独立验收和推进为拆分依据，不机械按命令或时长拆卡 | one-coordinator | 无 | 待建：QUA-14 |
| TEAM-23 | 任务号 `<主线>-<MMDD>-<会话码>-<nn>`，任务名 `[主线·bc] 动作+对象`（码表 `tasks/_README.md`）；派卡即写 `tasks/<任务号>.md`；PR 标题、代码提交、完成点报告带任务号 | 派卡会话 | 半 | C |
| TEAM-24 | 卡状态现行取值：已派、进行中、阻塞、完成、作废（check_tasks.py、context_handoff.py 的节点检测都按此）；QUA-14、QUA-17 上线后归一为 5 值：已派、进行中、阻塞、已交、关闭（关闭写结局：合入、作废或并入）；暂缓记为阻塞并写出处；状态变即改「最后更新」，12 小时未更新由 check_tasks.py 列出；唯一工作状态是任务卡片段，画像不写状态，台账仅调度自用，其他会话与画像不引用，要共享的暂停项等写本表或卡；交出、合入通知与回填由既定 QUA-17／MRG-02 自动化承接：finish 核 PR 与复审后写交出标记，合入事件通知负责会话和调度并回填卡状态、用时及 contexts.yaml bc 状态；上述工具上线前由负责会话维护现行状态，未知用时保留未知，不提前使用未上线的状态协议 | 负责会话；自动化 one-quality、one-merge | 半 | T、S；交出／回填待建 QUA-17、MRG-02 |
| TEAM-42 | 中间改动只提交、不开 PR；工作单元完成（验收、实现、opus 首轮齐备）才开；顺手的小修并入同主题的下一个 PR；叠加 PR 按 TEAM-43，可先开 PR 并行复审、签发，但不得越过 TEAM-17 的实现入口 | 作者 | 无 |  |
| TEAM-43 | 每个会话同时开着的代码 PR 不超过 3 个；改同一批文件的用 `mq.py enqueue --after` 排序，作者自行同步；允许叠加 PR：后一张卡可用前一张分支为基先开 PR，复审与签发同时进行；批准评论注明「after #<前一张>」仅作依赖留痕，人工入队使用 --after；前一张合入后后一张同步 main、核 head 后再入队（intent 113112、115815） | 作者；one-merge | 半 | Q；在制数量门待建 MRG-04 |
| TEAM-52 | PR 模板各栏如实填（断言或测试改动及理由、新增术语、OpenAPI 与架构文档同步、执行者、模型与强度、钩子失败及处置）；不为过测试放宽或删断言，合法变更也须单列理由，opus 首轮必查断言栏；Codex、DeepSeek 产出照常过 PR、CI 与 TEAM-67 复审；卡记返工轮次与用时，同类返工两轮以上收回 Claude，两者分工按同卡对拍定 | 作者；首轮复审；对拍 one-quality | 半 | 断言漂移（仅告警） |
| TEAM-53 | 常规跨会话状态只在四节点各发一条：开 PR、CI 全绿请批、合入、交接；提交号写进 PR 描述或分支，不单独发。文档类（intent、台账、规则文本、用语、画像、提议稿、任务卡）不开 PR，推到本会话分支、提交信息带任务号，由 one-merge 建合集时自扫远端认领（比 main 多出且只改文档的提交），不再报文档提交号；要调度批或裁的才报调度。文档合集通常每天中午、晚上各收一次（逐个提交、只收 docs/，已批 ADR 的 PR 并入），紧急阻塞按需处理，合入后同步 main；派工、阻塞、安全事件、用户决定及需要对方动作的协调仍须送达，同一事件只报一次。派工与动作请求用 ccd send_message，技术协调直连、不抄送，影响排期或用户决定才报调度；消息一到三行、首行类别、一次说完、长内容给路径，不回纯确认，子会话确需等结果才开完成通知；完成证据与未完项归 PR／卡／交接，不另发结束报号。delivered 只是送达，执行以对方报告或文件为准；限流则写 ~/.modernize/psa/outbox/<会话名>.md（时间、收件会话、一行一条）后继续做，没活写空闲，调度代转并标已转；清空中会话按 TEAM-07 转接；每轮状态声明（GOV-1023-QUA-01）：除 one-coordinator 外，每轮最后一条消息末行写 `状态：继续｜待命｜等调度｜等用户｜完成点`，「等调度」「完成点」须本轮已发消息给调度或已写 outbox，Stop 钩子 `scripts/hooks/stop_status_gate.py` 挡，报告同时落 `~/.modernize/psa/mailbox/` | 全员；文档合集 one-merge；代转 one-coordinator | 无 | 自扫与通知待建：MRG-11、MRG-02 |
| TEAM-57 | 进不进 git：机密与个人信息永不进；可由库内生成的不入库；他人或 CI 作依据的入库；一人高频自用的不进 main（在线页、本会话分支或 scratchpad）；一事一个权威出处；一次性产出进 `.gitignore`；记忆只放用户个人偏好与指针（≤2 KB），规则回仓库；记忆与仓库冲突以仓库为准并报调度；提议稿（架构、研究草稿）留在分支，到里程碑（评审完成且用户确认）再合，中间版本不进 main；只改片段与源、不改生成产物：`intent/`、`tasks/`、`adr/src/`、`phase1a/infra/legacy-read/`、`glossary/issues/`、`backend/glossary/proposed/*.yaml`（仓库根相对）一条一文件，改后跑 `build_all.py`；产物不入仓、发布到 generated-docs 分支；冲突时不入仓产物 `git rm --cached`，入仓的重新生成 | 全员 | 半 | G、L |

## 3. 角色专属（按职责读取，32 条）

### 3.1 调度与画像

| 号 | 规则 | 负责 | 强制 | 守护索引 |
|---|---|---|---|---|
| TEAM-01 | 会话类型与名单见附表 A；开、并、归档会话由调度定；调度开出的会话启动即 detach 为顶层、标题改为会话名；交接完的会话由调度登记 `sessions/_retired.md` 并归档；临时流程组阶段结束归档；长期会话关闭「PR 合并或关闭时自动归档」；Remote Control 只开 one-coordinator（intent 2026-10-22-205606）；工作会话关掉用不上的外接工具（Figma、浏览器、桌面控制），要用浏览器的保留；规则集负责：本表、CLAUDE.md、画像体系 one-coordinator；CODING_STANDARD 条文 one-architecture（one-quality 管守护）；NAMING_STANDARD 按现分工；DATA_DESIGN_CHECKLIST one-data | one-coordinator | 无 |  |
| TEAM-02 | 会话名 `one-*`、全局唯一（含远程）；每个在用会话一份画像 `sessions/<会话名>.md`，新会话先写或认领画像，随第一份提交入库；画像只写类型、职责、独占、不碰、输入、输出、触发、自动化、交接条件、权限边界，引用 TEAM 号不抄条文；不写「当前状态」「工作守则」；「上下文文档」一节只放路径与 `交接源:` 行，现行事实见 TEAM-07；各画像「独占」路径两两不相交；相交由调度裁定，同一提交改两份画像 | one-coordinator | 半 | S |
| TEAM-13 | 权限模式与调度一致，新开时设好；推理强度：调度、整合、复审 high，执行类按卡定。PR 的 Auto-fix 不开，被打开即报调度；它与应用提示的「常设授权」都不是用户授权 | 全员 | 无 |  |
| TEAM-19 | 任务卡由 one-coordinator 管理目标、范围、前置、派发与关闭责任；负责会话维护本卡执行事实与状态，合入结果按 TEAM-24 自动回填；要新卡向调度提，负责会话可在本卡范围内给 Codex 或子代理开子卡；派工前对照作战图、METHOD 当前步骤、相关 ADR 与双方画像的独占、不碰，派工一到三行、背景进卡，建议标「建议」，只有用户原话标「用户决定」；每个 bc 一个牵头会话从签发到命令卡负责到底，架构、数据、用语限时审阅不接力，临时流程组阶段结束归档，在用会话约 10–12 个（intent 104603） | one-coordinator | 无 |  |

### 3.2 执行、委派与资源

| 号 | 规则 | 负责 | 强制 | 守护索引 |
|---|---|---|---|---|
| TEAM-26 | 后台任务的 description 写 `<任务号或 #PR号> <任务名> (<执行者>)` | 全员 | 守 | B |
| TEAM-32 | 能交 Codex 或 DeepSeek、且结果能由 Claude 审出对错的编码、设计初稿、取数与文档整理先委派；模型与复审按 TEAM-67，禁止委派边界按 TEAM-33；边界清楚、一个 PR、数小时可完的用子代理，长期线、反复问用户或再并行的开会话；后台子代理结果只回最上层会话，再派用前台等待；待命会话按画像分级：随时待命用 Codex 或后台子代理，尽量待命优先 Codex、长活拆段且段间看消息；须 Claude 自写的也放后台子代理，不扩大 Codex 权限（intent 113916、114258） | 负责会话 | 无 |  |
| TEAM-33 | 不交 Codex、DeepSeek：生产取证与连生产操作；身份、授权、凭据等安全改动；opus 复审；验收测试；对用户与会话的沟通；语义、权限、越权边界判断；钩子、并发、解析类守护代码（后两类只可整理材料或起草测试） | 负责会话 | 无 |  |
| TEAM-34 | Codex 在独立 worktree 跑 `codex exec -m gpt-6.1-sol -c model_reasoning_effort=<强度> -s <沙箱> -c sandbox_workspace_write.network_access=false --ephemeral -o <结果> < /dev/null`，`-s` 必写（read-only 或 workspace-write）；强度按卡：机械 low/medium、起草 medium/high、判定 xhigh；DeepSeek 在独立 worktree 跑 `pi -p --no-session --no-extensions --no-mcp --offline --model deepseek/deepseek-flash --tools <最小集> "<卡>" < /dev/null`（用户付费账户，`pi auth check` 查就绪）；手工跑 pi 先 cd 到工作区外；Codex、DeepSeek 一律 `run_in_background` 启动，不用 nohup、`&`、setsid，不轮询；前端命令用 `pnpm --offline --config.verify-deps-before-run=false`，缺依赖即报告，不联网补装 | 负责会话 | 半 | I |
| TEAM-40 | PR 要能单独合入、复审、回退，合入后 main 仍绿；守护、CI、钩子改动、跨 bc 接口、机械改名、部署阻塞各自单开；同主题的契约、OpenAPI 与架构文档随实现同提交更新，不再绝对分离文档与代码 | 作者 | 无 |  |
| TEAM-41 | 非生成改动以 800 行／15 文件为复审参考线，超出在 PR 写理由，不做硬拦截；生成物不手工合，冲突时重新生成（mq.py 仅自动处理已识别的生成物） | 作者 | 无 | Q（生成物冲突；尺寸无硬门） |
| TEAM-88 | 本地只跑改动相关模块的测试，全量交 CI（卡另有要求除外）；重活排队进 heavy_slot.py 两个全机槽位；CI runner、本机资源、降负载、`deploy_gate.py` 归 one-ops；相同对象的有效检查证据按 TEAM-106 复用，不重复排重活 | 全员；one-ops | 半 | P、重活槽位 |
| TEAM-90 | 编辑只改所需片段，工具按适用性选择，不作无关整篇重写；长输出先筛选、存文件按需读，失败段落完整读，大块阅读按上下文成本决定是否委派并保留文件与行号 | 全员 | 半 | H（只提醒） |
| TEAM-106 | 一张卡攒好再推：本地预检通过后一次推送，不边写边推；提 PR 前 scripts/precheck.sh 全过（按改动选跑，本机跑不了算失败），输出贴 PR「precheck.sh 输出」；git push 单独一次调用，改文件与提交在前一次完成；手动预检、推送门、开 PR 复用有效检查结果，须绑定提交、基线、干净工作树、时效与检查集合，任一不符重跑，默认 SKIP 不代表完整覆盖；QUA-04 上线后手动预检通过即认、推送门不重跑，上线前照现有门禁执行，不绕过 | 全员 | 半 | P；手动预检复用待建 QUA-04 |

### 3.3 复审与合并

| 号 | 规则 | 负责 | 强制 | 守护索引 |
|---|---|---|---|---|
| TEAM-45 | PR 按 TEAM-53 报调度；opus 首轮结论到达后调度对照必改清单及复核材料核准，在 PR 留「调度批准入队 head <sha>」；批准评论只留痕与核 head，不作入队依据，自动入队永久关闭；one-merge 按调度明确批准的清单人工入队，每次 mq.py 入队记录执行会话名，调度每批边界对照批准清单审计，不符报用户，不建独立账号（intent 115815）；批准前核对：产出与报告一致、生成文件可重现、无凭据原值、已采纳 ADR 只改头部可变字段、已应用迁移未改（基线开放期按附 M3）、代码改动符合 RULE_REVIEWS.json 结论、登记文件有 one-data 签发行；head 读取失败或不一致即不入队，已有 MRG-01／07 以本条最新边界实施；发帖侧门（GOV-1023-QUA-03）只防作者会话误发批准评论，同机共用账号下不防有意伪造 | 作者；one-coordinator；one-merge | 半 | G、M、R；签发、入队审计等见待建索引 |
| TEAM-47 | 合并只由 one-merge 经 mq.py 队列执行，任何会话不得 `gh pr merge` 或网页合并；部署与阻塞相关随时插队；已复审、约 5 个文件内的小 PR 由调度定分组、one-merge 执行合批；one-merge 一轮超过 15 分钟未处理调度批准时，调度可自行 mq.py enqueue（只入队），事后告知 one-merge；该例外按 intent 113916 在 MRG-07 上线后退场，仍遵守 TEAM-45 的人工批准与入队审计，禁止恢复自动入队 | one-merge | 半 | Q；直合门待建 QUA-20 |
| TEAM-48 | 代码 PR 合并前必过 backend verify、frontend verify、secret scan（非 light）；纯文档道按 TEAM-50；冲突与 CI 红由 one-merge 退回作者并报调度；超 48 小时无进展的 PR 调度追问，推进或关闭 | one-merge | 半 | Q、CI |
| TEAM-50 | 快慢两道（队列语义）：分支保护 strict 的试行决定不变；code 道须通过完整三项 CI，docs 道同步 main 后通过轻检查；head 包含当前 main，或仅落后构建与测试均不读取的纯文档提交时可复用有效 CI，否则同步并重跑；无法判明则暂停处理；main 推送或每晚运行变红暂停两道，main 因并行合入变红一次，one-merge 报调度后恢复 strict、结束试行；实时保护设置另核，不以条文代替实况 | one-merge | 半 | Q |
| TEAM-51 | 入队后作者不再往该分支推（后续改动另开 PR），被退回后再推；推送前核对当前分支；force-push 已开的 PR 先告知调度；合入对象必须是已核 head，head 读取失败或变化即停止入队／合入，退回后重新核准 | 作者 | 半 | Q（已有 head 变化检测；缺失闭合见既有 MRG 卡） |
| TEAM-67 | 会话主模型按既定角色：执行类 sonnet，调度、架构、结构主线 opus；子代理优先 Codex（intent 081732、083031），Claude 的 opus 保留每个代码或守护 PR 首轮一次、安全判定（授权、凭据、生产授权、数据分级）、ADR 起草与首轮评审、结构设计；安全与生产改动 opus 首轮及修后 opus 终审，一般代码首轮 opus、改后由作者会话交 Codex 复核并贴 PR，调度对照必改清单核过即批、不另派复核（intent 090817）；纯文档作者自检，最多两轮后新发现的非 High 单列残余项，不阻塞；不得重复派同一件事。派子代理（Agent、Workflow agent()）写 model、要求中文回复并指定范围与输出上限；机械取数、常规工程与调研按适配和额度选 Codex，允许的 Claude 机械任务用 haiku、常规用 sonnet；安全、验收、语义与守护自写范围仍按 TEAM-33 | 全员 | 无 | 模型参数待建 QUA-19 |
| TEAM-72 | 新增或改动的测试类在引入它的 PR 上重复跑 3 遍；每晚按错题本报错特征归类 CI 失败，命中「已落实」条目即判该检查失效须修 | one-quality | 半 | 重复测试、夜间复发 |

### 3.4 决定记录、专项职责与治理

| 号 | 规则 | 负责 | 强制 | 守护索引 |
|---|---|---|---|---|
| TEAM-62 | 收件箱是用户决定的唯一处理点：要用户定的写进收件箱（来源、选什么、卡住什么、紧急度），先经调度审题；已在 GitHub 发起的在原 PR 回复并在箱里建指向。不在会话里问用户，一轮结尾也不提问；出题（确认页、批准页、收件箱事项）前对照 `DECISION_DIGEST.md` 并引用条目；与已定决定相悖的列入「待用户裁定」或写明为何重问 | 全员；调度 | 半 | D |
| TEAM-64 | 确认页按统一模板出：Artifact 加 ArtifactData 自动保存、版式简单、只用系统字体栈；出题会话出页、调度只审题；发布后用浏览器核对渲染（细则 `rules/decide/README.md`） | 出题会话；模板 one-glossary | 半 | D |
| TEAM-73 | 选框架、标准、选型前写本系统适配研究（要求、代价、收益），出页前派 opus 对抗式评审；外部知识先查 `~/Desktop/公开资料`（kq.py）、`~/Desktop/公开资料研究`（library.sqlite）、`~/Desktop/企业资料`（INDEX.md，个人信息不进会话）原文并引书名页码；架构节点按框架原文判定 | 架构类会话 | 无 |  |
| TEAM-74 | 周期治理合成一份输出：每周一由调度组织、one-quality 采集守护统计（附表 C ①–③、陈旧卡、超期 ADR 提议、在制 PR、Codex 返工率）与无主设计（无卡或无负责会话的设计稿、提议稿、待建守护），开卡或标不做并写理由；同时报业务功能 PR 合入数、产品代码行数、挂起／冲突／等批准累计摩擦时长及上周对比，不用代码量代替业务验收或人员评价；写入当日日报，替代每周胜任力评审与 ADR 周复审台账，抽审按风险派独立代理回测；每天早上与交接用 get_usage 记额度行，早间简报含昨日合入、主线进度、今日待定，复用同份数据；平时只推里程碑、阻塞、待用户决定，结论先行，只转述用户需知或需定的部分 | one-coordinator；采集 one-quality | 无 | 待建：QUA-23 |
| TEAM-77 | 用户决定用 `new_intent.py --session=<会话名> "<原话>" "<落点>"` 记；背景与理由只进 intent，不进 ADR、不进本表；用户在任何会话口头作的决定，当场 `new_intent.py` 记原话、报调度、登记收件箱「已定」；同一提交改受影响的规则表条、画像、台账 | 全员 | 无 |  |
| TEAM-78 | 后台任务与持久配置（定时器、launchd、runner、云上临时资源）建立即登记 `phase1a/infra/RESOURCES.md`，用完清理并记录；会话内定时任务迁到 GitHub 工作流，不依赖会话存活；迁完之前，台账登记在跑的会话内定时任务 | 建立方；资源 one-ops；定时迁移 one-quality | 无 | 定时迁移待建 QUA-22 |
| TEAM-82 | 改规则：记 intent→改本表原条（不追加「增补」）→同一提交改掉所有引用（grep TEAM 号与旧节号）；新规则取下一号，废止的条留号写「废止：<intent 文件名>」；协作规则只写本表，不开 ADR（ADR-0020 协作规则以本表为准），架构、配置及代码类变更按附 M3；守护状态须核文件存在、注册接线、实际触发范围、自测及负向证据后如实标注，仅部分覆盖标半，待建写卡号，不能有文件就标守；上线同批更新规则与守护索引 | 规则负责会话 | 无 | 待建：QUA-11 |
| TEAM-96 | 迁入读取：表与列按「用途 = 迁入 / 对账」登记读取列；只在集成层适配器内用运行时只读账号读；数据只进适配器与目标库；6e 敏感数据不在 dev 演练（ADR-0036、ADR-0037） | 迁入会话 | 无 |  |
| TEAM-100 | SECURITY_INVARIANTS、SECURITY_FINDINGS 生产前由 one-architecture 代管，one-security 设立后移交；身份、授权、单人模式改动遵守 SECURITY_INVARIANTS 并走 opus | one-architecture | 无 |  |
| TEAM-101 | 引入或升级依赖、改 CI 工作流与 runner，按 `SUPPLY_CHAIN.md` S1–S7 | 改动方 | 半 | CI（部分版本） |
| TEAM-102 | 治理冻结：整改总验收完成起两周不新增规则与守护；事故或安全缺口经用户批准例外，在途整改卡照做（用户 2026-10-23，intent 104603）；待建守护 one-quality 按风险只排前 10，其余标复审或不做并写理由；实施遵守 TEAM-32、TEAM-33、TEAM-67，在途整改范围不能作为新增治理的授权 | one-coordinator | 无 |  |
| TEAM-103 | 新守护或收紧既有守护合入前，先对全部在开 PR 跑一遍；会撞上的先修或在 PR 里给过渡期，不得让它首次在队首变红（intent 104603） | 改动方 | 无 | 预跑待建：MRG-10 |

## 旧编号并入指向

旧引用继续有效；新引用直接使用主条编号。

| 旧号 | 指向 |
|---|---|
| TEAM-03 | 并入 TEAM-02 |
| TEAM-04 | 并入 TEAM-02 |
| TEAM-06 | 并入 TEAM-07 |
| TEAM-08 | 并入 TEAM-07 |
| TEAM-09 | 并入 TEAM-07 |
| TEAM-10 | 并入 TEAM-07 |
| TEAM-11 | 并入 TEAM-01 |
| TEAM-12 | 并入 TEAM-01 |
| TEAM-15 | 并入 TEAM-14 |
| TEAM-16 | 并入 TEAM-07 |
| TEAM-18 | 并入 TEAM-57 |
| TEAM-21 | 并入 TEAM-20 |
| TEAM-22 | 并入 TEAM-17 |
| TEAM-25 | 并入 TEAM-24 |
| TEAM-27 | 并入 TEAM-19 |
| TEAM-28 | 并入 TEAM-74 |
| TEAM-29 | 并入 TEAM-32 |
| TEAM-31 | 并入 TEAM-67 |
| TEAM-35 | 并入 TEAM-34 |
| TEAM-36 | 并入 TEAM-34 |
| TEAM-37 | 并入 TEAM-86 |
| TEAM-38 | 并入 TEAM-52 |
| TEAM-39 | 并入 TEAM-95 |
| TEAM-44 | 并入 TEAM-106 |
| TEAM-46 | 并入 TEAM-45 |
| TEAM-49 | 并入 TEAM-24 |
| TEAM-54 | 并入 TEAM-53 |
| TEAM-55 | 并入 TEAM-57 |
| TEAM-56 | 并入 TEAM-57 |
| TEAM-58 | 并入 TEAM-53 |
| TEAM-59 | 并入 TEAM-53 |
| TEAM-60 | 并入 TEAM-53 |
| TEAM-61 | 并入 TEAM-53 |
| TEAM-63 | 并入 TEAM-62 |
| TEAM-65 | 并入 TEAM-77 |
| TEAM-66 | 并入 TEAM-74 |
| TEAM-68 | 并入 TEAM-67 |
| TEAM-71 | 并入 TEAM-70 |
| TEAM-75 | 并入 TEAM-74 |
| TEAM-76 | 并入 TEAM-24 |
| TEAM-79 | 并入 TEAM-78 |
| TEAM-80 | 并入 TEAM-70 |
| TEAM-81 | 并入 TEAM-01 |
| TEAM-83 | 并入 TEAM-82 |
| TEAM-84 | 并入 TEAM-102 |
| TEAM-85 | 并入 TEAM-82 |
| TEAM-89 | 并入 TEAM-91 |
| TEAM-92 | 并入 TEAM-91 |
| TEAM-93 | 并入 TEAM-91 |
| TEAM-94 | 并入 TEAM-95 |
| TEAM-98 | 并入 TEAM-97 |
| TEAM-99 | 并入 TEAM-91 |
| TEAM-104 | 并入 TEAM-19 |
| TEAM-105 | 并入 TEAM-74 |
| TEAM-107 | 并入 TEAM-43 |

<a id="附-m方法不是团队规则仍有效"></a>

## 附 M：方法（义务继续有效，按适用工作读取）

### M1 修复批

1. 派批前列出可能要用户定的点，调度一次问完；画归属图（各代理的表、权限码、事件、迁移号段、共享构件）写进卡，越界先报告。
2. 复审对 Blocker、High、Medium 尽量给可复现的失败测试（测试名引复审编号），以转绿为准、留作守护。复核模型与职责按 TEAM-67；Medium、Low 修复方补测试、CI 过即关，负责会话抽查。
3. 滚动整合：复审通过即并入整合分支，每并一路跑全量与守护，后到者解冲突；修复循环只跑受影响模块。执行与逐条复核模型按 TEAM-32、TEAM-67。
4. 验收测试（TEAM-69）：`code-modernization:test-engineer` 从 contract.md、规则卡、ADR、用户原话写 Given/When/Then（测试名引 D、RULE 编号或 intent）；实现方认为测试有误就停下报告。目录、trailer、合并提交、裁定记录与 surefire/failsafe 报告要求见 `scripts/ci/acceptance_guard.py`、`acceptance_rulings.py` 头部与 `backend/src/test/java/com/forcitis/one/acceptance/README.md`。单元测试由实现方写。
5. 缺陷修复、功能补做（应交付未做）、结构性重构（含改名）分开派工，不混批、不混提交。
6. 缺陷修复手术式：一张缺陷卡（复审编号、复现步骤、预计改动文件）对应一次最小改动加一个先失败的回归测试；只改卡上文件，不顺手重构、改名、统一风格；一张或几张卡一个小 PR，复核过即合入，不攒大整合分支；无法局部修的标「结构性」写理由，转重构批。
7. 整合复审后：Blocker、High 合并前修完，复审方按 TEAM-67 只复核这几条；Medium、Low 不阻塞，进下一批卡；新发现的应交付未做进下一批，安全漏洞本批先堵。

### M2 并行开发

- 并行只在互不相交的工作单元上（不同 bc，或不同阶段的不同步骤）。
- 公共基础先行：先由一个代理建好并冻结共用构件（聚合基类与版本模型、状态机、事件信封、错误链、前端共用包）；要改停下报告，由整合者改。
- 代码交付首轮按 TEAM-67 由 opus 复审（DATA_DESIGN_CHECKLIST §14、§14.1），通过才整合；一般改后由作者交 Codex 复核，安全与生产改动修后 opus 终审；整合后整体复审与每波「老系统形状」专项核查仍保留，模型按 TEAM-67；每波交付后专项检查「老系统形状」（照搬的数据结构、接口形状、流程），结论写进该波报告。

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
- 第二批（用户 2026-10-14）：①复刻老行为以生产数据或日志（只读）为证；②不为过测试放宽或删断言，改则在报告与 PR 单列理由；③缺失或无效输入失败并告警，规则卡明确允许的除外；④只实现当前阶段所需；⑤用语先查词表（M6）；⑥测试不写密钥字面量；⑦测试只断言本模块效果，异步用带上限的确定性等待，不写宽泛 `never()`；⑧契约先行，接口路径、必填字段、表名变化时 OpenAPI 与架构文档同提交更新；⑨并行子代理同一基线，整合提交重跑全部守护；⑩建账号、起容器、对外调用先问（TEAM-91）；⑪能一开始做对的不留后续，推迟须写「为何后做更有价值」（④管范围，⑪管质量）。

### M6 用语

- 登记：新标识符、界面文字、表列名先查 terms.yaml，查不到当场登记提议术语，写使用范围（domain、kernel、acl）与 bc；kind 只取 DDD 战术分类，聚合根与领域事件都要有词条，事件=聚合名加过去式业务动词，命令用祈使动词（G40–G42，`glossary/term_rules.py`）；同一 bc 内中英文名都查重；不同 bc 同名各自登记（id 加 `<bc>__` 前缀）并在 homonyms 互列；错误码中文名全局唯一（G48、R-5、CS-2.24）。
- 术语门禁、批次清理、决策单、写回的流程见 `glossary/gate/README.md`（门禁：测试代理在卡上写「本卡新词」，one-glossary 当天预判，业务用语全部确认后才派实现，提前派须写理由报调度；决策单：业务用语积压 100 条、距上次超 7 天或阶段出口前出；写回：改名 PR 先合、forbidden 后合，先禁用须带到期日与负责会话的允许清单）。另：整合会话先对本批提议去重归并；错误码单列不计门槛、阶段出口前清零；出口不留未确认的会计类提议（ADR-0018）。
- 文档里的方言、同名异义用 `glossary/new_term_issue.py` 登记（一条一文件），不自行裁定，由 one-glossary 处理。

## 附表 A 角色（GOV-1022-COO-15 定稿）

- 长期：one-coordinator（派工与任务卡、批 PR、规则集与画像、收件箱、额度）；one-architecture（架构正本、ADR、施工图、平台准入、CODING_STANDARD 条文，安全清单代管至 one-security）；one-data（数据设计签发、DD 门禁、迁入、登记文件签发）；one-glossary（用语、命名词源、业务规则卡与决策题、统一确认页模板）；one-quality（CI、测试、守护、钩子）；one-merge（合并队列、文档合集、分支清理、合入通知）；one-ops（云资源、部署、本机资源与 CI runner、deploy_gate）。
- 工作（按任务卡，一卡一单元）：one-1a-par、one-1a-next-5、one-1a-admin、one-platform-file。
- 临时（流程组，价值流骨架与表级模型，阶段结束归档）：one-arch-finance；one-arch-people、one-arch-revenue 已退役（见 sessions/_retired.md）。

## 附表 B 守护索引

本节只记录实现：触发、覆盖、失败行为与责任会话，不重抄 TEAM 条文。接线依据本 worktree 的 `.claude/settings.json`、`.pre-commit-config.yaml`、CI 与脚本；未证明所有运行会话已加载。文件存在、已注册、样例触发、实际会话受保护须区分；待建不算已拦截。

| 实现／规则索引 | 触发条件 | 拦截范围与边界 | 失败行为 | 责任会话 |
|---|---|---|---|---|
| PreToolUse 分发 `pre_tool_dispatch.py` | Bash／Agent／Task 调用 | 只分发 B、主检出、I、P 五门；不覆盖模型、生产写、授权及直合 | 子门拒绝或分发异常返回 2；超时不构成可靠阻断 | one-quality |
| B `bg_task_name_gate.py`（TEAM-26） | 后台 Bash、Agent／Task | description 格式；不核 model、真实卡、身份，不含 Workflow | 格式不符拒绝 | one-quality |
| 主检出 `main_checkout_gate.py`、`git_ceiling_env.sh`（TEAM-86） | PreToolUse git；pre-commit／pre-merge；SessionStart | 主检出 main、部分共享 main 引用；不能定位目录时有覆盖缺口 | 已识别违规拒绝；环境脚本设 git ceiling | one-quality |
| I `codex_stdin_gate.py`（TEAM-34） | Bash 中 Codex／pi | stdin 硬门；禁网／沙箱仅告警，后台与 pi 最小工具集未强制 | stdin 未接管拒绝，其余告警 | one-quality |
| P `pre_push_gate.py`、`precheck_before_pr.py`（TEAM-87、106） | 识别到普通 git push／部分 gh pr create | push 运行 precheck；PR 可复用近期推送标记；删除／dry-run 放行；gh -R 等识别有缺口；手动预检复用待 QUA-04 | 预检失败或异常拒绝；现实现仍有带理由 bypass 缺口，TEAM-87 禁止使用，按既有卡修复 | one-quality |
| C `commit_msg_gate.py`（TEAM-23） | commit-msg | 代码提交任务号或 PR 号；追加 Session；文档、merge、fixup 等豁免；正文理由只提醒 | 缺编号／脚本异常拒绝，未识别身份告警 | one-quality |
| H `context_handoff.py`（TEAM-05、07、14、90） | PostToolUse、UserPromptSubmit、Stop | 用量／节点／长 Bash 提醒；Stop 仅以 mtime 有限挡停；不验证清空或交接内容；调度 50%／60% 例外尚未按角色接线，规则阈值不变 | 提醒；每级有限拒停；异常告警后放行 | one-quality |
| 快照 `precompact_snapshot.sh`、`sessionend_snapshot.sh`、`handoff_snapshot.py`（TEAM-07） | PreCompact／SessionEnd | 本地快照与遮蔽；不是交接完整性门 | 失败告警后放行 | one-quality |
| 开工单 `postcompact_context.sh`、`start_sheet.py`、`session_context.py`（TEAM-14） | 启动、clear、compact；开工单生成 | 身份、卡目标、交接与路径；8 KB 截断；不是开工批准门 | 缺项告警，截断标记 | one-quality |
| S `build_sessions.py`（TEAM-02、24） | 画像生成、build_all --check | 名称、字段、上下文路径、交接源、开工单大小；不核独占重叠或正文重复 | 不合规生成失败；旧位置交接问题仅告警 | one-quality；画像 one-coordinator |
| T `check_tasks.py`（TEAM-24） | 任务汇总／build_all | 状态与陈旧卡检查仍有历史兼容；5 值阻断未上线 | 多数问题告警；完整字段及归一门待 QUA-14 | one-quality |
| G `build_all.py --check`（TEAM-45、57） | 预检、CI、手动生成 | 源片段重建、产物可重现／跟踪状态、配套自测；不证语义正确 | 不一致或子检查失败返回非零 | one-quality；各生成器负责会话 |
| M `migrations_immutable.py`；R `review_revisions.py`（TEAM-45） | 适用改动预检／CI；build_all 修订核对 | 迁移开放期与不可变边界、原评审字段／追加 revisions；数据签发行门待 QUA-16 | 不合规失败；本地缺比较基线依脚本告警，CI 按其门禁失败 | one-quality；语义 one-data／one-glossary |
| A `acceptance_guard.py`、`acceptance_rulings.py`、`acceptance_reports.py`（TEAM-69） | 验收改动及适用 CI | 目录、trailer、裁定登记、测试报告；不代替独立验收责任 | 不合规失败 | one-quality；独立测试与复审 |
| 断言漂移 `assertion_drift.py`（TEAM-52） | 测试差异扫描 | 语法级断言变化，不证明断言未削弱 | findings 仅告警，脚本错误失败 | one-quality；首轮复审 |
| 错题本 `defect_bank_check.py`（TEAM-70） | build_all／CI | 登记及引用；不证明修复实际有效 | 登记不合规失败；有效性须回测 | one-quality |
| 重复 `repeat_new_tests.py`；复发 `ci_recurrence.py`（TEAM-72） | queued code PR 的重步骤；夜间工作流 | 后端选中 Test／IT，排除及最多 40 类，不含前端；夜间按特征归类 | 选中类任遍失败／无报告失败；超额明示；复发按脚本出报告 | one-quality |
| D `check_pages.py`（TEAM-62、64） | 适用确认页预检 | 题面规则；外部字体、状态、runtime 的扩充待 QUA-15；不证决定语义 | 已覆盖项不合规失败 | one-quality；模板 one-glossary |
| L gitleaks 8.30.1（TEAM-57、97） | pre-commit、CI 全历史、本地预检 | 提交／历史密钥模式；不覆盖所有运行时输出，不检查每 worktree 安装 | 命中失败；本地缺工具可能 SKIP，不能代替 CI 或声称完整覆盖 | one-quality；安装各会话 |
| Q `scripts/merge/mq.py`（TEAM-41、43、47、48、50、51） | 人工 enqueue、runner 取队与合入 | 锁、after、三项完整 CI、主干报警、已记录 head 变化、部分生成物再生成；不拦所有直合；读取 head 失败仍可无记录入队的缺口待既有 MRG 卡修复 | HOLD／退回／HEAD-CHANGED；非生成冲突退作者；自动入队永久关闭、审计边界见 TEAM-45，未实施不标已完成 | one-merge |
| CI 供应链／重活槽 `heavy_slot.py`（TEAM-88、101） | 工作流依赖核验；precheck 重步骤 | 部分版本校验；全机两槽排队，不是全部资源与供应链授权门 | 版本不符／槽等待或运行失败非零 | one-quality；资源 one-ops |

## 附表 C 待建守护（详单 PB_GUARDS_TODO.md；冲突以最新 intent 与本表为准）

| 卡号 | 内容 | 实现 |
|---|---|---|
| GOV-1022-QUA-11 | ①标「守」的条其守护存在且已注册；TEAM 号引用不悬空 | 自写 |
| GOV-1022-QUA-13 | ③画像独占路径两两无相交 | Codex |
| GOV-1022-QUA-14 | ⑤卡状态先归一再 5 值阻断；缺目标或完成标准即失败 | Codex |
| GOV-1022-QUA-15 | ⑥check_pages 查外部字体、状态值、runtime | Codex |
| GOV-1022-QUA-16 | ⑦登记文件改动的 PR 须有 one-data 签发行 | 自写 |
| GOV-1022-COO-17 | ⑧钩子命令匹配防绕过的余项（`$'g\x69t'`、通配符、`bash <<EOF`、`env -S`、`git -c alias.`、`gh -R`） | 自写 |
| GOV-1022-QUA-17～23、MRG-01～04 | finish.py、自清推全员、子代理 model 必填、拒 `gh pr merge`、PR 尺寸参考报告（不硬拦）、定时迁工作流、周一出数；批准留痕核 head／人工入队审计（自动入队永久关闭）、合入通知及自动回填、重启清单、在制上限 | 见详单 |

## 附表 D 旧节号对照（逐条详单 PB_MAPPING.md）

§0、§1、§2 表、§8–§10 删除（背景进 intent），仍有效的并入 TEAM-05、07、15、16、56、80；§2.1 → TEAM-01–13、57、78、97；§3、§4 → TEAM-14–17、44、53、56、59、65、77、86；§5 → TEAM-23–26、29–39、67、70、73、90、95–96、101 与附 M5、M6；§6、§6.1 → 附 M2、M1；§7 → TEAM-91–94；§11 → TEAM-19、27、45–50、53–54、58–66、68、71、74–76、99；§12.1–12.5 → 附 M3、TEAM-46、85；§12.6 → TEAM-57；§13 → 附 M4；§14–§14.3 → `DATA_DESIGN_CHECKLIST.md` 同节号。
