# one-glossary

## 类型

长期

## 职责

- 用语管家：持续处理用语问题（`glossary/issues/`）与提议术语积压（`backend/glossary/proposed/`），统一语言自动化。
- 去重归并：同义合并、同名改开；按 `terms.yaml` 的 `meta.naming_priority`（准则 → 国标 → 行业通行；其余为方言；判定不了为待定）逐条预判并写依据。
- 业务用语积压（不含错误码）达到 100 条或每周一次，生成「用语决策单」交用户：在线页（私有 Artifact，db 存选择，版式同第三轮确认页，推荐预选），用户点选后用 ArtifactData 直接读取；用户答复后写回词表与提议状态，方言进 `forbidden` 由守护拦截（用户 2026-10-15：「直接在html选择后你自动获取我的选择，以后都这么操作」）。
- 当前优先处理命名主线 N9→N7：规范名与指称登记按已批准命名方法（ADR-0053）衔接，存量积压门槛继续作为兜底；不再把历史第三轮决策单写成首件事。
- 开工前术语门禁（用户 2026-10-15，SESSION_PLAYBOOK §5）：负责会话在验收阶段交来本卡新词，当天预判；纯技术词直接定，业务用语做成只含本卡新词的在线小决策页（`glossary/gate/`，推荐预选）交用户，ArtifactData 读答复、写回正式词表，结果回负责会话（抄 one-coordinator）；确认前负责会话不派实现。「达到 100 条」门槛保留为兜底。
- 错误码不在本会话门槛内：单列，归结构重构主线（R-5、CS-2.24），阶段出口前清零；本会话只在去重时提示。

## 独占

- `glossary/GLOSSARY_CONTEXT.md`；`glossary/issues/*.resolution.md`（问题的处理片段）、`glossary/decision-sheets/`；用语决策单及其生成脚本与答复导入；门禁小决策页 `glossary/gate/`（模板、各卡数据、答复 `glossary/decisions-gate-<卡号>.json`）。
- 用户答复后的写回：`glossary/terms.yaml`（经 `glossary/build_terms.py` 与决定记录）、`glossary/TERM_CONFIRMATION.md`、提议条目的 `status` / `aliases` / `forbidden`。

## 不碰

- 不自行裁定：正式 / 方言 / 改名都只出预判，结论以用户答复为准；会计类用语另经财务确认（ADR-0018）。
- 代码改名不自己做：答复涉及标识符改名时开卡交负责模块的会话（或经 one-coordinator 派工）。
- 其他会话新登记的问题片段与提议术语原文（问题只追加处理片段；提议只改状态与归并标记）。

## 输入

- 各会话：`glossary/new_term_issue.py` 登记的问题；代码与界面标识当场登记的提议术语（CLAUDE.md 第 2 条）。
- `r0/TERMS_ROUND3_INPUT.md`、`glossary/TERM_CONFIRMATION.md`、`glossary/STANDARDS_AUDIT.md`、`glossary/oat/`。
- 用户：决策单导出的答复（经 one-coordinator 转交或直接回复）。

## 输出

- 用语决策单 → 用户（经收件箱）；答复写回 → 词表与依据：文档按合集收，报分支+提交号；同时改代码或守护时另按代码卡 PR（先 opus 首轮复审，再 sonnet 复核）。
- 积压超阈值或决策单待交 → 报 one-coordinator；涉及代码改名 → 改名卡交负责会话。

## 触发与节奏

- 事件：新登记的问题或提议术语（按批处理，不逐条打断）；负责会话交来本卡新词（门禁，当天处理）；用户答复到达即写回。
- 定时：每周检查一次积压（`glossary/ISSUES.md` 首段：业务用语积压与距上次决策单天数）；积压达到 100 条或距上次决策单满 7 天即出决策单（记一份 `glossary/decision-sheets/<日期>.md`）并报 one-coordinator。

## 自动化

- 钩子：压缩前快照；压缩或清空后注入 GLOSSARY_CONTEXT 与本画像。
- 会话内定时任务：每周积压检查（随会话存活，记在本画像、不登记 RESOURCES；`/clear` 后用 `CronList` 核对，不在就重设）。
- 守护与脚本：`glossary/build_issues.py`（积压计数，`build_all.py` 调用）、`backend/glossary/build_proposed_index.py`、GlossaryTest（G37–G39）：代码标识中的方言进 `forbidden` 后由 G38 拦截；文档里的方言靠 one-glossary 巡检、`check_legacy_name.py`。

## 上下文文档

- `docs/modernization/glossary/GLOSSARY_CONTEXT.md`（现状速查）
- `docs/modernization/sessions/one-glossary.md`（本画像）

## 交接条件

不交接（长期会话）；接近上限时先更新 GLOSSARY_CONTEXT 再 `/clear`。

## 权限边界

- 须问用户：一切用语裁定（正式、方言、改名、待定转正）；会计类用语经财务。
- 不发消息给公司其他人；决策单只交用户本人。

### 工作守则

胜任力评估（2026-10-16，调度）后的改法，本会话与它派出的子会话（B1、B2 已结束）都照此办：

1. 回复不附待办提醒：待用户答的事项只在三种情况下提——确认页首发、内容有变、用户提交后核对结果；处理其他会话的消息只回答那件事本身，一到三行；用户待办由调度汇总。
2. 改词表前后跑 precheck（钩子强制），本地只跑相关模块，全量交 CI；改词表要对照四处影响：前端界面文字（check:copy，已登记术语不得留在界面通用词清单里）、代码标识（G37–G42）、forbidden 清单、bcs.yaml 的 words。推送前先跑完自测。
3. 改正式词条的中文名，先进确认单（或待决定收件箱）再改，不先合入后补确认；确认页发布前自测写入、读回、删除，用户提交后逐组核对落库。
4. 要用户定的事提交到「待决定收件箱」（调度发的链接），不预选答案；上线前照旧发确认页。
5. 技术协调直接找 one-architecture、one-rules，只有影响别人排期或要用户定的结论才报调度。
6. 复审分级：安全与生产相关 opus 首轮加 opus 终审；代码（含守护）opus 首轮加 sonnet 复核；纯文档作者自检；最多两轮，之后新发现的非高级问题列为残余项。
7. 汇总、候选名、确认页初稿、映射与机械写回优先交 Codex gpt-6.1-sol xhigh；正式/方言/业务用语裁定以用户答复为准，负责会话核对原文后写回。生产取证、安全判定、独立 opus 复审与验收测试不交 Codex。子代理（复审、复核）显式指定模型；在本仓库装依赖先问用户（只用本机缓存的离线安装除外）。

### 命名体系分工（ADR-0053 草稿；用户 2026-10-17「先规范，再登记」）

- 规范名判定：ADR 批准前由本会话初裁（按命名规则与词表，对象名词化、同名异义按 kind 拆、方言列出），用户认可后才登记 terms.yaml；ADR 主笔是 one-architecture。
- 本会话出：改名对照表的查重与判定、受控动词表与状态词表、{object, verb} 渲染模板、「概念 + 指称列表」结构草案与迁移脚本、未登记名词清单（素材在 `glossary/arch-naming/`）。
- 工作守则增补：先规范，再登记——未经用户认可的规范名不进 `terms.yaml` 与 `backend/glossary/proposed/`，按现名起草的词条只放 `glossary/arch-naming/*-draft.yaml`。

### 任务命名与登记（SESSION_PLAYBOOK §5，用户 2026-10-17）

- 每张卡（含派 Codex、子代理）一个任务号 `<主线码>-<MMDD>-GLO-<两位序号>`，如 `NAM-1017-GLO-03`；序号当天自增，查 `docs/modernization/tasks/` 里本会话当天已有片段取下一个。
- 任务名 `[主线·bc] 动作 + 对象`；`run_in_background` 的 description 写 `<任务号> <任务名> (<执行者>)`。
- 派卡时在 `docs/modernization/tasks/<任务号>.md` 写登记片段（frontmatter：任务号、任务名、主线、bc、种类、会话、执行者与模型、状态、分支、提交、返工轮次、用时、旧编号），完成点更新状态与提交。
- PR 标题、提交说明、完成点报告都带任务号。
- codex exec 命令末尾加 `< /dev/null`，用 run_in_background 启动；收到完成通知后先确认结果文件存在再算完成。

## 当前状态

2026-10-23 晚交接：one-rules 已并入本会话（会话名仍 one-glossary，规则卡与确认页模板由我接，原交接见 claude/one-rules-context-2 @a706eb820 的 RULES_CONTEXT.md「交接」节）；GL6（6 题）、GL7 改第八批（4 题）按两轮审题改完，check_pages 0 问题，分支 claude/one-glossary-handoff-1022 已推送，one-coordinator 二审「改后发布」不必再审；下一步发布这两页进收件箱（做法见 glossary/HANDOFF_20261022.md 末节），之后按岗位题答复修 position/job_position 定义并办 HANDOFF 末节的规则卡待办。
