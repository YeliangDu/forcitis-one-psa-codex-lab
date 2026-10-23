# 旧 PB → 新 PB 对照与引用同步清单（GOV-1022-COO-15 草稿）

旧 PB = `docs/modernization/SESSION_PLAYBOOK.md`（分支 claude/one-coordinator-two-lanes，112 KB，含 a73d2ed52 记忆移回）。新 PB = `pb/SESSION_PLAYBOOK.new.md`；DDC = `pb/DATA_DESIGN_CHECKLIST.md`。

## 一、逐节逐条对照

| 旧节 / 条 | 新位置 | 说明 |
|---|---|---|
| §0 核心原则 1–4 | 删除 | 背景进 intent；仍有效的落 TEAM-07（状态写文件）、TEAM-21（一卡一单元）、TEAM-91–94（人做决定）、TEAM-14（固定开场） |
| §1 状态文件表 | TEAM-15、24、56、76；STATUS.md、SESSION_LOG.md 行删除 | status/、session-log/ 已停（总方案第二节第 9 条）；「版本化（待用户确认）」已过时，删除 |
| §2 会话种类表 | 删除；角色见附表 A | 种类表是 10-14 的旧划分 |
| §2.1 第 1 条 生命周期与交接 | TEAM-05、06、07 | 30/40 阈值（P-01）；75/85 旧值删 |
| §2.1 第 2 条 命名与唯一 | TEAM-02 | |
| §2.1 第 3、4 条 权限模式、推理强度 | TEAM-13 | |
| §2.1 第 5 条 工作区纪律 | TEAM-86 | |
| §2.1 第 6 条 必须报告 | TEAM-59 | |
| §2.1 第 7 条 用户决定当场记 | TEAM-65 | |
| §2.1 第 8 条 子代理再派 | TEAM-29 | |
| §2.1 第 9 条 后台任务登记 | TEAM-78 | |
| §2.1 第 10 条 密钥不进记录 | TEAM-97 | |
| §2.1 第 11、12 条 自动归档、收尾归档 | TEAM-11 | |
| §2.1 第 13 条 长期会话上下文文档 | TEAM-08、07 | 「上下文文档」注入改由开工单（TEAM-14） |
| §2.1 第 14 条 记录存放 | TEAM-56、57、07、08、18 | session-log、status 删除 |
| §2.1 第 15 条 Auto-fix、本机并发 | TEAM-13、88 | |
| §2.1 第 16 条 会话画像 | TEAM-02、03、04、14 | 「当前状态只在画像」删除（P-09）；独占相交改为待建守护 QUA-13 |
| §3 开场 1 build_all | TEAM-15 | 改读开工单与 SESSIONS.md |
| §3 开场 2 认领 status/units | 删除 | 仪式已停 |
| §3 开场 3 入口条件 | TEAM-17 | |
| §3 开场 4 确认目标 | TEAM-14（开工单含卡目标） | |
| §3 开场 5 命名示例 | 删除 | 旧式名（P-13）；规则在 TEAM-02 |
| §4 收尾 0 只改片段 | TEAM-56 | |
| §4 收尾 1 记 intent | TEAM-65、77 | |
| §4 收尾 2 勾 brief | TEAM-80 | |
| §4 收尾 3 new_log | 删除 | session-log 已停 |
| §4 收尾 4 status 片段 | 删除；状态改在任务卡（TEAM-24） | |
| §4 收尾 5 重生成 | TEAM-56 | |
| §4 收尾 6 清理与 §12 自查 | TEAM-97、46；附 M3 | 「Flyway 只新增」按 ADR-0038 改写（P-04） |
| §4 收尾 6a worktree 与单独 push | TEAM-86、44 | 「所有改动都开 PR」与文档不开 PR 冲突，按 TEAM-53 裁（P-03） |
| §4 收尾 7 报调度 | TEAM-59 | |
| §5 把大输出放文件、探索交子代理、上下文节约 | TEAM-90 | context_handoff 超长提示为「半」 |
| §5 老代码只作证据、代码注释 | 附 M5 第 1、2 项 | CS-1.1、1.2 出处 |
| §5 写代码规则第二批 1–11 | 附 M5「第二批」①–⑪ | 编号不变 |
| §5 提议术语按批次清理、开工前术语门禁 | 附 M6 | 流程细节指向 glossary/README.md |
| §5 生产取证不逐次请示 | TEAM-95 | |
| §5 迁入读取授权 | TEAM-96 | |
| §5 依赖与供应链 | TEAM-101 | |
| §5 Codex 派工 | TEAM-32、33、34、36、37、38 | 强度按卡（P-11） |
| §5 DeepSeek 派工 | TEAM-35 | |
| §5 架构判断 | TEAM-73 | |
| §5 任务命名与登记 | TEAM-23、26 | |
| §5 子代理必须指定模型 | TEAM-31、67 | opus 使用点收为 TEAM-67 一处 |
| §5 转述前核原文 | TEAM-70 | |
| §5 压缩不可怕、切换信号 | TEAM-07、05 | |
| §6 并行（互不相交、worktree、子代理还是会话、公共基础、小批复审、共享记录） | 附 M2；TEAM-29、37、86、56 | 分支名 `feature/issue-` 删除（P-14），见 TEAM-86 |
| §6.1 第 1、2 条 | 附 M1 第 1 条 | 修复批条目合并重编 |
| §6.1 第 3、4 条 | 附 M1 第 2 条 | |
| §6.1 第 5–7 条 | 附 M1 第 3 条 | |
| §6.1 第 8 条 验收测试分开 | 附 M1 第 4 条；TEAM-69 | 裁定措辞按 P-12 改 |
| §6.1 第 9、10、11 条 | 附 M1 第 5、6、7 条 | |
| §7 须用户决定 | TEAM-91、92、93、94 | 只移出推送、开 PR、触发 CI（P-02）；其余逐条保留；加 CLAUDE.md 底线 5 的建账号、起容器、对外调用 |
| §7 记录方式 | TEAM-77 | |
| §8 会话日志模板 | 删除 | session-log 已停 |
| §9 长时间任务 | TEAM-16、36 | 跨天短会话做法并入 TEAM-29 |
| §10 中断与恢复 | TEAM-16、80 | 读 STATUS.md 改为核实况 |
| §11 定位 | 附表 A；TEAM-69 | 「不做评审结论」与裁定冲突按 P-12 裁 |
| §11 职责 1–6 | TEAM-16、27、59、60、62、66、07 | |
| §11 消息约定（类别、建议与决定、派工单、报告） | TEAM-58、27、20、59 | 派工单内容并入任务卡模板 TEAM-20 |
| §11 少叫醒少重复 1–7 | TEAM-58、49、62、66、61 | 第 5 条（不开完成通知）并入 TEAM-58 |
| §11 唤醒空闲会话 | TEAM-60 | |
| §11 战队协作 1 收件箱 | TEAM-62 | |
| §11 战队协作 2 直连 | TEAM-60 | |
| §11 战队协作 3 precheck | TEAM-44 | |
| §11 战队协作 4 共享登记改片段 | TEAM-56 | |
| §11 战队协作 5 作战图 | TEAM-27 | |
| §11 战队协作 6 复审分级 | TEAM-68 | |
| §11 战队协作 7 大卡开短期会话、常驻 4 个 | TEAM-21、29；附表 A | 「常驻只留 4 个」按定稿角色表改为 8 个长期（P-07） |
| §11 战队协作 8 每日简报 | TEAM-66 | |
| §11 战队协作 9 ADR 边界不自定 | TEAM-30 | |
| §11 战队协作 10 宣告前验证 | TEAM-70 | |
| §11 战队协作 11 三道防线 | TEAM-71、72 | |
| §11 战队协作 12 抽审与转述 | TEAM-74、70 | 胜任力评审改周一出数 |
| §11 战队协作 13 ADR 提议复审 | TEAM-74；`adr/src/_readme_lifecycle.md` §10 | 台账节改为周一出数（lifecycle §10 须同步） |
| §11 战队协作 14 合入节奏 | TEAM-47、53、54、42 | 组批归 one-merge 执行、调度定分组（P-06）；文档提交号直报 one-merge（P-57） |
| §11 战队协作 15 重启前后 | TEAM-10 | |
| §11 用户授权调度自行决定 | TEAM-45、46、47、01 | 「任何改动都经 PR」删除（P-03） |
| §11 边界 | TEAM-99、76 | 台账不再被他人引用（P-10） |
| §12 前言、§12.1 | 附 M3「ADR」 | |
| §12.2 三类、修订评审结论、迁移基线 | 附 M3 对应三项 | 基线写法按 P-04 |
| §12.3 | 附 M3「已过账事实」 | |
| §12.4 | 附 M3「谁可以改什么」 | ADR 细则指 lifecycle |
| §12.5 | TEAM-85 | |
| §12.6 什么进 git | TEAM-57 | |
| §13 流程梳理 | 附 M4 | DD 门禁写「已生效」（P-08）；子代理名 `psa-…-process-review` 删除（P-13） |
| §14、§14.1、§14.2、§14.3 | DDC 同节号 | 原文原样拆出 |

冲突裁决落点：P-01 TEAM-05；P-02 TEAM-91–93；P-03 TEAM-53；P-04 附 M3「迁移基线」；P-05 TEAM-43；P-06 TEAM-47；P-07 附表 A；P-08 附 M4；P-09、P-10 TEAM-76、03；P-11 TEAM-34；P-12 TEAM-69；P-13 删除旧示例、TEAM-02；P-14 TEAM-86（NAMING_STANDARD 第 21 项另交 one-glossary、one-architecture）；P-57 TEAM-53。

## 二、落地前置（不处理会让 CI 变红）

1. `scripts/hooks/playbook_digest.py` 按 `## 5` 与 §2.1、§11 编号条目解析旧 PB 生成摘要，自测对真实文件断言（`scripts/ci/relevant_change.py:70`、`docs/modernization/build_all.py:27,173`、`scripts/hooks/handoff_snapshot.py:315`、`handoff_selftest.py:75,124`、`handoff_storage_selftest.py:376-380`、`scripts/precheck.sh:304-305`）。新 PB 合入必须同一 PR 改写或下线它（开工单 COO-13 已取代整份注入，建议下线），属钩子与解析类，one-quality 自写。
2. `docs/modernization/build_sessions.py` 仍要求画像有「上下文文档」「当前状态」节并在 docstring 引 §2.1 第 16 条，须随 COO-13/QUA-12 改（TEAM-03）。
3. `scripts/hooks/session_context.py` 与 `context_handoff.py` 头部引旧节号，见下表。
4. CLAUDE.md（最先改，同一提交）建议新写法：底线 5「须用户决定的事（PB §7）」→「（PB TEAM-91–92）」；索引：数据设计→`DATA_DESIGN_CHECKLIST.md` §14/14.1、迁入 §14.2、集成 §14.3；用语登记→PB 附 M5⑤、附 M6；Codex/DeepSeek→PB TEAM-32–38 + templates/WORK_CARD.md；子代理模型、任务号→TEAM-31、67、23、26；取证、迁入读取→TEAM-95、96、ADR-0037；交接、开会话、画像、后台登记→TEAM-01–13、78 + templates/HANDOFF.md；记录片段、进不进 git→TEAM-56、57；消息、outbox→TEAM-58–61；ADR 与规则变更→附 M3、TEAM-82；流程→附 M4；修复批→附 M1；外部知识、架构判断→TEAM-73。AGENTS.md 同理。
5. CODING_STANDARD.md 第 15、25–41 行等以 `SESSION_PLAYBOOK.md#5-上下文管理` 锚点作出处：改为 `SESSION_PLAYBOOK.md#附-m方法不是团队规则仍有效`（附 M5）或直接写「PB 附 M5 第二批第 n 条」；§14 系列改指 DATA_DESIGN_CHECKLIST.md。CS 条文归 one-architecture（TEAM-81），由它改。

## 三、引用旧节号、需同步改的文件:行

由 `git grep` 自动扫出（tracked 文件，排除旧 PB 自身；生成产物已被 .gitignore 排除）；规则：行内 `SESSION_PLAYBOOK` / `PB` / `本手册` 后 40 字内的 § 号，以及 CLAUDE.md、AGENTS.md、画像、模板、收件箱 README、tasks/_README、process/README 中的裸 § 号（标「疑似」）。「建议新写法」按第一节自动套用，同行多个引用按出现顺序对应；改时写成「SESSION_PLAYBOOK TEAM-nn」或「DATA_DESIGN_CHECKLIST §14.x」。

### 甲、须同步改（活文件：规范、画像、模板、脚本、CI、测试注释、在用说明）

共 338 行。「疑似」= 行内只有裸 § 号、出自引用 PB 的活文件，改前人工确认指的是 PB。

| 文件:行 | 旧引用 | 建议新写法 | 备注 |
|---|---|---|---|
| `.github/pull_request_template.md:5` | §5 第2 | 附 M5「第二批」第 2 条 |  |
| `.github/pull_request_template.md:18` | §6.1 第8 | 附 M1 第 4 条、TEAM-69 |  |
| `.github/pull_request_template.md:22` | §5 | TEAM-32–38 |  |
| `.github/workflows/ci.yml:39` | §12.2 | 附 M3「规则调整三类」（迁移基线、修订评审结论同节） |  |
| `.github/workflows/ci.yml:61` | §6.1 | 附 M1 |  |
| `.github/workflows/ci.yml:236` | §12.2 | 附 M3「规则调整三类」（迁移基线、修订评审结论同节） |  |
| `.gitignore:11` | §1 | TEAM-15（STATUS.md 已停） |  |
| `AGENTS.md:3` | §5；§6.1 | TEAM-29–39、附 M5（按主题）；附 M1 |  |
| `AGENTS.md:5` | §5 | TEAM-32–38 |  |
| `CLAUDE.md:11` | §7 | TEAM-91–94 |  |
| `CLAUDE.md:22` | §14；§14.2；§14.3 | DATA_DESIGN_CHECKLIST.md §14；DATA_DESIGN_CHECKLIST.md §14.2；DATA_DESIGN_CHECKLIST.md §14.3 |  |
| `CLAUDE.md:23` | §5 | 附 M5「第二批」 |  |
| `CLAUDE.md:25` | §5 | TEAM-29–39、附 M5（按主题） |  |
| `CLAUDE.md:26` | §5 | TEAM-29–39、附 M5（按主题） |  |
| `CLAUDE.md:27` | §5 | TEAM-29–39、附 M5（按主题） |  |
| `CLAUDE.md:28` | §2.1 | TEAM-01–13 |  |
| `CLAUDE.md:29` | §4 第0；§12.6 | TEAM-56；TEAM-57 |  |
| `CLAUDE.md:30` | §11 | TEAM-58–61 |  |
| `CLAUDE.md:32` | §12；§13；§6.1 | 附 M3；附 M4；附 M1 | 疑似 |
| `CLAUDE.md:34` | §5 | TEAM-73 |  |
| `backend/architecture/cross-schema-version-references.txt:1` | §14 | DATA_DESIGN_CHECKLIST.md §14 |  |
| `backend/architecture/migration-baseline-open:1` | §12.2 | 附 M3「规则调整三类」（迁移基线、修订评审结论同节） |  |
| `backend/src/test/java/com/forcitis/one/acceptance/README.md:3` | §6.1 第8 | 附 M1 第 4 条、TEAM-69 |  |
| `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/READONLY_VERIFY_ACCEPTANCE.md:5` | §6.1 第8 | 附 M1 第 4 条、TEAM-69 |  |
| `docs/modernization/CODING_STANDARD.md:25` | §6 | 附 M2、TEAM-29 |  |
| `docs/modernization/CODING_STANDARD.md:32` | §5；§5 | 附 M5；TEAM-29–39、附 M5（按主题） |  |
| `docs/modernization/CODING_STANDARD.md:33` | §5；§5 | 附 M5；TEAM-29–39、附 M5（按主题） |  |
| `docs/modernization/CODING_STANDARD.md:34` | §14 第8 | DATA_DESIGN_CHECKLIST.md §14 |  |
| `docs/modernization/CODING_STANDARD.md:35` | §5 第5；§5 | 附 M5「第二批」第 5 条；TEAM-29–39、附 M5（按主题） |  |
| `docs/modernization/CODING_STANDARD.md:37` | §5 第1；§5 | 附 M5「第二批」第 1 条；TEAM-29–39、附 M5（按主题） |  |
| `docs/modernization/CODING_STANDARD.md:38` | §5 第3；§14 第5；§5 第5 | 附 M5「第二批」第 3 条；DATA_DESIGN_CHECKLIST.md §14；TEAM-29–39、附 M5（按主题） |  |
| `docs/modernization/CODING_STANDARD.md:39` | §5 第4；§5 | 附 M5「第二批」第 4 条；TEAM-29–39、附 M5（按主题） |  |
| `docs/modernization/CODING_STANDARD.md:40` | §5 第11；§5 | 附 M5「第二批」第 11 条；TEAM-29–39、附 M5（按主题） |  |
| `docs/modernization/CODING_STANDARD.md:41` | §5 第8；§5 | 附 M5「第二批」第 8 条；TEAM-29–39、附 M5（按主题） |  |
| `docs/modernization/CODING_STANDARD.md:56` | §14 第1 | DATA_DESIGN_CHECKLIST.md §14 |  |
| `docs/modernization/CODING_STANDARD.md:57` | §14 第2 | DATA_DESIGN_CHECKLIST.md §14 |  |
| `docs/modernization/CODING_STANDARD.md:58` | §14 第3 | DATA_DESIGN_CHECKLIST.md §14 |  |
| `docs/modernization/CODING_STANDARD.md:59` | §14 第4 | DATA_DESIGN_CHECKLIST.md §14 |  |
| `docs/modernization/CODING_STANDARD.md:60` | §14 第6 | DATA_DESIGN_CHECKLIST.md §14 |  |
| `docs/modernization/CODING_STANDARD.md:61` | §14 第9 | DATA_DESIGN_CHECKLIST.md §14 |  |
| `docs/modernization/CODING_STANDARD.md:114` | §14.1；§14 第8 | DATA_DESIGN_CHECKLIST.md §14.1；DATA_DESIGN_CHECKLIST.md §14 |  |
| `docs/modernization/CODING_STANDARD.md:121` | §14.1 | DATA_DESIGN_CHECKLIST.md §14.1 |  |
| `docs/modernization/CODING_STANDARD.md:122` | §14.1；§14.1；§14.1 | DATA_DESIGN_CHECKLIST.md §14.1；DATA_DESIGN_CHECKLIST.md §14.1；DATA_DESIGN_CHECKLIST.md §14.1 |  |
| `docs/modernization/CODING_STANDARD.md:123` | §14.1 | DATA_DESIGN_CHECKLIST.md §14.1 |  |
| `docs/modernization/CODING_STANDARD.md:124` | §12.2 | 附 M3「规则调整三类」（迁移基线、修订评审结论同节） |  |
| `docs/modernization/CODING_STANDARD.md:128` | §12.3 | 附 M3「已过账事实」 |  |
| `docs/modernization/CODING_STANDARD.md:131` | §14.1 | DATA_DESIGN_CHECKLIST.md §14.1 |  |
| `docs/modernization/CODING_STANDARD.md:142` | §5 第5 | 附 M5「第二批」第 5 条 |  |
| `docs/modernization/CODING_STANDARD.md:156` | §13；§6.1 | 附 M4；附 M1 |  |
| `docs/modernization/CODING_STANDARD.md:157` | §6.1 第8 | 附 M1 第 4 条、TEAM-69 |  |
| `docs/modernization/CODING_STANDARD.md:158` | §5 第2；§5 | 附 M5「第二批」第 2 条；TEAM-29–39、附 M5（按主题） |  |
| `docs/modernization/CODING_STANDARD.md:159` | §5 第7；§14；§5 | 附 M5「第二批」第 7 条；DATA_DESIGN_CHECKLIST.md §14；TEAM-29–39、附 M5（按主题） |  |
| `docs/modernization/CODING_STANDARD.md:160` | §5 第7；§5 | 附 M5「第二批」第 7 条；TEAM-29–39、附 M5（按主题） |  |
| `docs/modernization/CODING_STANDARD.md:161` | §5 第6；§5 | 附 M5「第二批」第 6 条；TEAM-29–39、附 M5（按主题） |  |
| `docs/modernization/CODING_STANDARD.md:164` | §6.1 第3 | 附 M1 第 2 条 |  |
| `docs/modernization/CODING_STANDARD.md:165` | §6.1 第6；§5 第9 | 附 M1 第 3 条；附 M5「第二批」第 9 条 |  |
| `docs/modernization/CODING_STANDARD.md:173` | §5 第6；§5 | 附 M5「第二批」第 6 条；TEAM-29–39、附 M5（按主题） |  |
| `docs/modernization/CODING_STANDARD.md:174` | §2.1 第10；§2.1 | TEAM-97；TEAM-01–13 |  |
| `docs/modernization/CODING_STANDARD.md:175` | §1 第6；§4 第6 | TEAM-15（STATUS.md 已停）；TEAM-97、附 M3 |  |
| `docs/modernization/CODING_STANDARD.md:176` | §5 | TEAM-32–38 |  |
| `docs/modernization/CODING_STANDARD.md:178` | §5 第10；§7；§5 | 附 M5「第二批」第 10 条；TEAM-91–94；TEAM-29–39、附 M5（按主题） |  |
| `docs/modernization/CODING_STANDARD.md:179` | §5；§5 第7 | TEAM-95；TEAM-29–39、附 M5（按主题） |  |
| `docs/modernization/CODING_STANDARD.md:186` | §5 | TEAM-32–38 |  |
| `docs/modernization/CODING_STANDARD.md:187` | §6.1 | 附 M1 |  |
| `docs/modernization/CODING_STANDARD.md:188` | §4 第0；§8 | TEAM-56；删除（session-log 已停） |  |
| `docs/modernization/CODING_STANDARD.md:189` | §4 第6；§12 | TEAM-97、附 M3；附 M3 |  |
| `docs/modernization/CODING_STANDARD.md:190` | §5 第9；§5 | 附 M5「第二批」第 9 条；TEAM-29–39、附 M5（按主题） |  |
| `docs/modernization/CODING_STANDARD.md:191` | §6；§11 | 附 M2、TEAM-29；TEAM-19、27、45–50、58–66（按主题） |  |
| `docs/modernization/CODING_STANDARD.md:217` | §14；§14.1；§14.1 | DATA_DESIGN_CHECKLIST.md §14；DATA_DESIGN_CHECKLIST.md §14.1；DATA_DESIGN_CHECKLIST.md §14.1 |  |
| `docs/modernization/CODING_STANDARD.md:224` | §13 | 附 M4 |  |
| `docs/modernization/CODING_STANDARD.md:227` | §6 第21 | 附 M2、TEAM-29 |  |
| `docs/modernization/CODING_STANDARD.md:228` | §5；§5 | 附 M5「第二批」；TEAM-29–39、附 M5（按主题） |  |
| `docs/modernization/CODING_STANDARD.md:232` | §14 第1 | DATA_DESIGN_CHECKLIST.md §14 |  |
| `docs/modernization/CONFIG_CHANGELOG.md:3` | §12.2 | 附 M3「规则调整三类」（迁移基线、修订评审结论同节） |  |
| `docs/modernization/CROSS_CUTTING_DECISIONS.md:78` | §13 | 附 M4 |  |
| `docs/modernization/CROSS_CUTTING_DECISIONS.md:80` | §13 | 附 M4 |  |
| `docs/modernization/CROSS_CUTTING_MATRIX.md:273` | §13；§13 | 附 M4；附 M4 |  |
| `docs/modernization/GUIDING_PRINCIPLES.md:141` | §14.3 | DATA_DESIGN_CHECKLIST.md §14.3 |  |
| `docs/modernization/GUIDING_PRINCIPLES.md:148` | §5 | TEAM-29–39、附 M5（按主题） |  |
| `docs/modernization/GUIDING_PRINCIPLES.md:161` | §14.3 | DATA_DESIGN_CHECKLIST.md §14.3 |  |
| `docs/modernization/MODERNIZATION_BRIEF.template.md:121` | §13；§5 | 附 M4；TEAM-29–39、附 M5（按主题） |  |
| `docs/modernization/MODERNIZATION_BRIEF.template.md:150` | §13；§5 | 附 M4；TEAM-29–39、附 M5（按主题） |  |
| `docs/modernization/MODERNIZATION_BRIEF.template.md:151` | §13 | 附 M4 |  |
| `docs/modernization/MODERNIZATION_BRIEF.template.md:176` | §13；§5 | 附 M4；TEAM-29–39、附 M5（按主题） |  |
| `docs/modernization/MODERNIZATION_BRIEF.template.md:177` | §13 | 附 M4 |  |
| `docs/modernization/MODERNIZATION_BRIEF.template.md:198` | §13；§5 | 附 M4；TEAM-29–39、附 M5（按主题） |  |
| `docs/modernization/MODERNIZATION_BRIEF.template.md:199` | §13 | 附 M4 |  |
| `docs/modernization/MODERNIZATION_BRIEF.template.md:224` | §13；§5 | 附 M4；TEAM-29–39、附 M5（按主题） |  |
| `docs/modernization/MODERNIZATION_BRIEF.template.md:225` | §13 | 附 M4 |  |
| `docs/modernization/MODERNIZATION_BRIEF.template.md:244` | §13；§5 | 附 M4；TEAM-29–39、附 M5（按主题） |  |
| `docs/modernization/MODERNIZATION_BRIEF.template.md:245` | §13 | 附 M4 |  |
| `docs/modernization/NAMING_STANDARD.md:170` | §5 第5 | 附 M5「第二批」第 5 条 |  |
| `docs/modernization/NAMING_STANDARD.md:276` | §14.1 | DATA_DESIGN_CHECKLIST.md §14.1 |  |
| `docs/modernization/NAMING_STANDARD.md:473` | §14.1 | DATA_DESIGN_CHECKLIST.md §14.1 |  |
| `docs/modernization/NAMING_STANDARD.md:474` | §14.3 | DATA_DESIGN_CHECKLIST.md §14.3 |  |
| `docs/modernization/NAMING_STANDARD.md:489` | §14.3 | DATA_DESIGN_CHECKLIST.md §14.3 |  |
| `docs/modernization/NAMING_STANDARD.md:490` | §14.3 | DATA_DESIGN_CHECKLIST.md §14.3 |  |
| `docs/modernization/NAMING_STANDARD.md:491` | §14.3 | DATA_DESIGN_CHECKLIST.md §14.3 |  |
| `docs/modernization/NAMING_STANDARD.md:492` | §14.3 | DATA_DESIGN_CHECKLIST.md §14.3 |  |
| `docs/modernization/NAMING_STANDARD.md:493` | §14.3 | DATA_DESIGN_CHECKLIST.md §14.3 |  |
| `docs/modernization/NAMING_STANDARD.md:510` | §14.1 | DATA_DESIGN_CHECKLIST.md §14.1 |  |
| `docs/modernization/NAMING_STANDARD.md:578` | §14.2 | DATA_DESIGN_CHECKLIST.md §14.2 |  |
| `docs/modernization/REIMAGINED_ARCHITECTURE.md:97` | §13 第11 | 附 M4 |  |
| `docs/modernization/RULE_REVIEWS.md:111` | §12.2 | 附 M3「规则调整三类」（迁移基线、修订评审结论同节） |  |
| `docs/modernization/SUPPLY_CHAIN.md:24` | §12.2 | 附 M3「规则调整三类」（迁移基线、修订评审结论同节） |  |
| `docs/modernization/adr/build_adrs.py:2` | §12 | 附 M3 |  |
| `docs/modernization/adr/src/_readme_intro.md:5` | §12.1 | 附 M3「ADR」 |  |
| `docs/modernization/adr/src/_readme_intro.md:17` | §12.2 | 附 M3「规则调整三类」（迁移基线、修订评审结论同节） |  |
| `docs/modernization/adr/src/_readme_lifecycle.md:3` | §12；§12.4 | 附 M3；附 M3「谁可以改什么」 |  |
| `docs/modernization/adr/src/_readme_lifecycle.md:28` | §11；§12.4 | TEAM-99；附 M3「谁可以改什么」 |  |
| `docs/modernization/adr/src/_readme_lifecycle.md:145` | §11 第1 | TEAM-62 |  |
| `docs/modernization/architecture-guardrails/ci/migrations_immutable.py:3` | §12 | 附 M3 |  |
| `docs/modernization/architecture/ARCH_CONTEXT.md:6` | §2.1 第13 | TEAM-07、08 |  |
| `docs/modernization/architecture/DESIGN.md:60` | §14 | DATA_DESIGN_CHECKLIST.md §14 |  |
| `docs/modernization/architecture/FRAMEWORKS.md:59` | §14 第1 | DATA_DESIGN_CHECKLIST.md §14 |  |
| `docs/modernization/architecture/FRAMEWORKS.md:165` | §14.1 | DATA_DESIGN_CHECKLIST.md §14.1 |  |
| `docs/modernization/architecture/FRAMEWORKS.md:192` | §14.1 | DATA_DESIGN_CHECKLIST.md §14.1 |  |
| `docs/modernization/architecture/decision-page/ADRS.json:283` | §14.1 | DATA_DESIGN_CHECKLIST.md §14.1 |  |
| `docs/modernization/architecture/decision-page/ADRS_0046_0047.json:123` | §12.2 | 附 M3「规则调整三类」（迁移基线、修订评审结论同节） |  |
| `docs/modernization/architecture/decision-page/PPL_TRACE.md:90` | §12.2 第7 | 附 M3「规则调整三类」（迁移基线、修订评审结论同节） |  |
| `docs/modernization/architecture/decision-page/adr-approval-0046-0047.html:129` | §12.2 | 附 M3「规则调整三类」（迁移基线、修订评审结论同节） |  |
| `docs/modernization/architecture/decision-page/adr-approval.html:126` | §14.1 | DATA_DESIGN_CHECKLIST.md §14.1 |  |
| `docs/modernization/architecture/decision-page/archive/METHOD_PAGE.json:1511` | §14 | DATA_DESIGN_CHECKLIST.md §14 |  |
| `docs/modernization/architecture/decision-page/archive/RULES.json:246` | §11；§12.4 | TEAM-19、27、45–50、58–66（按主题）；附 M3「谁可以改什么」 |  |
| `docs/modernization/architecture/decision-page/archive/SAMPLES.json:1273` | §14 | DATA_DESIGN_CHECKLIST.md §14 |  |
| `docs/modernization/architecture/decision-page/build_samples.py:165` | §14 | DATA_DESIGN_CHECKLIST.md §14 |  |
| `docs/modernization/architecture/decision-page/method.html:131` | §14 | DATA_DESIGN_CHECKLIST.md §14 |  |
| `docs/modernization/architecture/decision-page/rules.html:129` | §11；§12.4 | TEAM-19、27、45–50、58–66（按主题）；附 M3「谁可以改什么」 |  |
| `docs/modernization/architecture/decision-page/samples.html:130` | §14 | DATA_DESIGN_CHECKLIST.md §14 |  |
| `docs/modernization/build_all.py:172` | §2.1；§5；§11 | TEAM-01–13；TEAM-29–39、附 M5（按主题）；TEAM-19、27、45–50、58–66（按主题） |  |
| `docs/modernization/build_decision_digest.py:4` | §11 第1 | TEAM-19、27、45–50、58–66（按主题） |  |
| `docs/modernization/build_records.py:4` | §1 | TEAM-15（STATUS.md 已停） |  |
| `docs/modernization/build_sessions.py:2` | §2.1 第16 | TEAM-02–04、14 |  |
| `docs/modernization/build_sessions.py:13` | §2.1 第16 | TEAM-02–04、14 |  |
| `docs/modernization/build_sessions.py:82` | §2.1 | TEAM-01–13 |  |
| `docs/modernization/build_sessions.py:184` | §2.1 第16 | TEAM-02–04、14 |  |
| `docs/modernization/check_legacy_name.py:14` | §12.1 | 附 M3「ADR」 |  |
| `docs/modernization/data/B31_PHYSICAL_MODEL_RULINGS.md:3` | §14；§14.1 | DATA_DESIGN_CHECKLIST.md §14；DATA_DESIGN_CHECKLIST.md §14.1 |  |
| `docs/modernization/data/DATA_CONTEXT.md:3` | §2.1 第13 | TEAM-07、08 |  |
| `docs/modernization/data/DATA_DESIGN_GATE.md:5` | §14.1 | DATA_DESIGN_CHECKLIST.md §14.1 |  |
| `docs/modernization/data/DATA_DESIGN_GATE.md:6` | §13 | 附 M4 |  |
| `docs/modernization/data/DATA_DESIGN_GATE.md:18` | §13 第5；§7 | 附 M4；TEAM-91–94 |  |
| `docs/modernization/data/DATA_DESIGN_GATE.md:24` | §13 | 附 M4 |  |
| `docs/modernization/data/DATA_DESIGN_GATE.md:59` | §6.1 | 附 M1 |  |
| `docs/modernization/data/DATA_DESIGN_GATE.md:75` | §13 | 附 M4 |  |
| `docs/modernization/data/DATA_DESIGN_GATE.md:81` | §13 | 附 M4 |  |
| `docs/modernization/data/DATA_DESIGN_GATE.md:104` | §12.2 | 附 M3「规则调整三类」（迁移基线、修订评审结论同节） |  |
| `docs/modernization/data/DATA_DESIGN_GATE.md:106` | §13 | 附 M4 |  |
| `docs/modernization/data/DATA_DESIGN_GATE.md:194` | §14 | DATA_DESIGN_CHECKLIST.md §14 |  |
| `docs/modernization/data/MEASURABLE_RAW_MATERIAL_SPEC.md:4` | §14 | DATA_DESIGN_CHECKLIST.md §14 |  |
| `docs/modernization/data/MIGRATION_QUALITY_CHECKS.md:1` | §14.2 | DATA_DESIGN_CHECKLIST.md §14.2 |  |
| `docs/modernization/data/STATE_TRANSITIONS_DESIGN.md:181` | §14 第1 | DATA_DESIGN_CHECKLIST.md §14 |  |
| `docs/modernization/data/evidence/B31P_LOCAL_INVENTORY_AND_QUERIES.md:579` | §5 | TEAM-95 |  |
| `docs/modernization/data/review-parts/organization_workforce.md:3` | §14.1 | DATA_DESIGN_CHECKLIST.md §14.1 |  |
| `docs/modernization/data/reviews/ADR-0034-IMPL.md:15` | §11 第9 | TEAM-19、27、45–50、58–66（按主题） |  |
| `docs/modernization/data/reviews/IMP-1021-COO-02-U3-file-service.md:3` | §14；§14.1 | DATA_DESIGN_CHECKLIST.md §14；DATA_DESIGN_CHECKLIST.md §14.1 |  |
| `docs/modernization/data/reviews/IMP-1021-COO-02-U3-file-service.md:77` | §14.1 | DATA_DESIGN_CHECKLIST.md §14.1 |  |
| `docs/modernization/data/reviews/IMP-1021-NX5-03.md:3` | §14.1 第1 | DATA_DESIGN_CHECKLIST.md §14.1 |  |
| `docs/modernization/data/reviews/IMP-1021-NX5-03.md:20` | §14.2 | DATA_DESIGN_CHECKLIST.md §14.2 |  |
| `docs/modernization/data/reviews/WORK-CALENDAR-DDL-2026-10-21.md:4` | §14；§14.1 | DATA_DESIGN_CHECKLIST.md §14；DATA_DESIGN_CHECKLIST.md §14.1 |  |
| `docs/modernization/data/reviews/WORK-CALENDAR-DDL-2026-10-21.md:131` | §14.1 第9 | DATA_DESIGN_CHECKLIST.md §14.1 |  |
| `docs/modernization/glossary/GLOSSARY_CONTEXT.md:3` | §2.1 第13 | TEAM-07、08 |  |
| `docs/modernization/glossary/b1/B1-SCOPE.json:1489` | §12.2 | 附 M3「规则调整三类」（迁移基线、修订评审结论同节） |  |
| `docs/modernization/glossary/build_issues.py:10` | §5 | 附 M6 |  |
| `docs/modernization/glossary/build_issues.py:155` | §5 | TEAM-29–39、附 M5（按主题） |  |
| `docs/modernization/glossary/build_terms.py:694` | §5 | TEAM-29–39、附 M5（按主题） |  |
| `docs/modernization/glossary/decision-sheets/_README.md:3` | §5 | TEAM-29–39、附 M5（按主题） |  |
| `docs/modernization/glossary/gate/README.md:3` | §5 | 附 M6 |  |
| `docs/modernization/glossary/gate/build_gate_page.py:2` | §5 | TEAM-29–39、附 M5（按主题） |  |
| `docs/modernization/glossary/issues/_README.md:3` | §5 | 附 M6 |  |
| `docs/modernization/glossary/oat/README.md:30` | §12.2 | 附 M3「规则调整三类」（迁移基线、修订评审结论同节） |  |
| `docs/modernization/glossary/terms.yaml:9085` | §12.3 | 附 M3「已过账事实」 |  |
| `docs/modernization/glossary/terms.yaml:9683` | §12.2 | 附 M3「规则调整三类」（迁移基线、修订评审结论同节） |  |
| `docs/modernization/glossary/terms.yaml:9685` | §12.2 | 附 M3「规则调整三类」（迁移基线、修订评审结论同节） |  |
| `docs/modernization/merge/MERGE_CONTEXT.md:3` | §2.1 第13 | TEAM-07、08 |  |
| `docs/modernization/new_log.py:2` | §8 | 删除（session-log 已停） |  |
| `docs/modernization/phase1a/DATA_SCORECARD.md:11` | §12；§14 | 附 M3；DATA_DESIGN_CHECKLIST.md §14 |  |
| `docs/modernization/phase1a/DATA_SCORECARD.md:35` | §14.1 | DATA_DESIGN_CHECKLIST.md §14.1 |  |
| `docs/modernization/phase1a/DATA_SCORECARD.md:113` | §12.2 | 附 M3「规则调整三类」（迁移基线、修订评审结论同节） |  |
| `docs/modernization/phase1a/DATA_SCORECARD.md:114` | §14 第4 | DATA_DESIGN_CHECKLIST.md §14 |  |
| `docs/modernization/phase1a/DATA_SCORECARD.md:126` | §14 第9 | DATA_DESIGN_CHECKLIST.md §14 |  |
| `docs/modernization/phase1a/DATA_SCORECARD.md:169` | §14.1；§4 | DATA_DESIGN_CHECKLIST.md §14.1；TEAM-59、56、65 |  |
| `docs/modernization/phase1a/HANDOFF_one-1a-next-2.md:3` | §2.1 第1 | TEAM-05、06 |  |
| `docs/modernization/phase1a/HANDOFF_one-1a-next-3.md:3` | §2.1 第1 | TEAM-05、06 |  |
| `docs/modernization/phase1a/HANDOFF_one-1a-next-4.md:6` | §2.1 第1 | TEAM-05、06 |  |
| `docs/modernization/phase1a/HANDOFF_one-1a-next.md:3` | §2.1 第1 | TEAM-05、06 |  |
| `docs/modernization/phase1a/NEXT_BATCH_DRAFT.md:3` | §6.1 | 附 M1 |  |
| `docs/modernization/phase1a/NEXT_BATCH_DRAFT.md:12` | §14 | DATA_DESIGN_CHECKLIST.md §14 |  |
| `docs/modernization/phase1a/NEXT_BATCH_DRAFT.md:29` | §12.2 | 附 M3「规则调整三类」（迁移基线、修订评审结论同节） |  |
| `docs/modernization/phase1a/NEXT_BATCH_PLAN.md:9` | §5；§6；§6.1；§12；§14.1 | TEAM-29–39、附 M5（按主题）；附 M2、TEAM-29；附 M1；附 M3；DATA_DESIGN_CHECKLIST.md §14.1 |  |
| `docs/modernization/phase1a/NEXT_BATCH_PLAN.md:211` | §12.2 | 附 M3「规则调整三类」（迁移基线、修订评审结论同节） |  |
| `docs/modernization/phase1a/NEXT_BATCH_PLAN.md:306` | §14.2 | DATA_DESIGN_CHECKLIST.md §14.2 |  |
| `docs/modernization/phase1a/NEXT_BATCH_PLAN.md:499` | §12.2 第4 | 附 M3「规则调整三类」（迁移基线、修订评审结论同节） |  |
| `docs/modernization/phase1a/NEXT_BATCH_PLAN.md:507` | §12.2 | 附 M3「规则调整三类」（迁移基线、修订评审结论同节） |  |
| `docs/modernization/phase1a/NEXT_BATCH_PLAN.md:552` | §5 | TEAM-29–39、附 M5（按主题） |  |
| `docs/modernization/phase1a/SCAFFOLD_BRIEF.md:32` | §5 | TEAM-29–39、附 M5（按主题） |  |
| `docs/modernization/phase1a/SCAFFOLD_BRIEF.md:43` | §5 | TEAM-29–39、附 M5（按主题） |  |
| `docs/modernization/phase1a/SCAFFOLD_BRIEF.md:64` | §14 | DATA_DESIGN_CHECKLIST.md §14 |  |
| `docs/modernization/phase1a/infra/DEV_DEPLOY_REMAINING.md:29` | §12.2 | 附 M3「规则调整三类」（迁移基线、修订评审结论同节） |  |
| `docs/modernization/phase1a/infra/PREDEPLOY_CHECKLIST.md:40` | §12.2 | 附 M3「规则调整三类」（迁移基线、修订评审结论同节） |  |
| `docs/modernization/phase1a/infra/PREDEPLOY_CHECKLIST.md:61` | §12.2 第4 | 附 M3「规则调整三类」（迁移基线、修订评审结论同节） |  |
| `docs/modernization/phase1a/infra/RESOURCES.md:3` | §2.1 第9 | TEAM-78 |  |
| `docs/modernization/phase1a/infra/legacy-read/_intro.md:3` | §4 第0 | TEAM-56 |  |
| `docs/modernization/phase1a/infra/runbooks/C-3-dev-db.md:31` | §12.2 第4 | 附 M3「规则调整三类」（迁移基线、修订评审结论同节） |  |
| `docs/modernization/phase1a/review/REVIEW_fix-a.md:52` | §14 | DATA_DESIGN_CHECKLIST.md §14 |  |
| `docs/modernization/phase1a/review/REVIEW_integration.md:224` | §14 | DATA_DESIGN_CHECKLIST.md §14 |  |
| `docs/modernization/process/1a/业务编号.md:175` | §12 | 附 M3 |  |
| `docs/modernization/process/README.md:3` | §13 | 附 M4 |  |
| `docs/modernization/quality/CI_TIMING_REVIEW.md:407` | §5；§6.1；§11 | TEAM-29–39、附 M5（按主题）；附 M1；TEAM-58–66 |  |
| `docs/modernization/quality/CONFLICT_HOTSPOTS.md:20` | §2.1 第9 | TEAM-78 |  |
| `docs/modernization/quality/GENERATED_OUT_OF_MAIN_DESIGN.md:3` | §12.6 | TEAM-57 |  |
| `docs/modernization/quality/GENERATED_OUT_OF_MAIN_DESIGN.md:102` | §2.1 | TEAM-01–13 |  |
| `docs/modernization/quality/GENERATED_OUT_OF_MAIN_DESIGN.md:108` | §11 | TEAM-19、27、45–50、58–66（按主题） |  |
| `docs/modernization/quality/GOVERNANCE_RESEARCH.md:137` | §2.1 | TEAM-01–13 |  |
| `docs/modernization/quality/MERGE_QUEUE_CUTOVER.md:77` | §11 第13 | TEAM-19、27、45–50、58–66（按主题） |  |
| `docs/modernization/quality/PROPOSALS_2026-10-16.md:42` | §5；§11；§12 | 附 M5；TEAM-58–66；附 M3 |  |
| `docs/modernization/quality/QUALITY_CONTEXT.md:3` | §2.1 第13 | TEAM-07、08 |  |
| `docs/modernization/quality/QUALITY_CONTEXT.md:107` | §11 第1 | TEAM-62 |  |
| `docs/modernization/quality/QUALITY_CONTEXT.md:131` | §11 第6 | TEAM-19、27、45–50、58–66（按主题） |  |
| `docs/modernization/quality/QUALITY_CONTEXT.md:132` | §5 | TEAM-26 |  |
| `docs/modernization/quality/TASK_LEDGER_DESIGN.md:67` | §5 | TEAM-29–39、附 M5（按主题） |  |
| `docs/modernization/quality/TASK_LEDGER_DESIGN.md:81` | §6.1 | 附 M1 |  |
| `docs/modernization/quality/defect-bank/README.md:3` | §11 第11 | TEAM-19、27、45–50、58–66（按主题） |  |
| `docs/modernization/quality/defect-bank/one-1a-next-4.md:3` | §11 第11 | TEAM-19、27、45–50、58–66（按主题） |  |
| `docs/modernization/quality/defect-bank/one-1a-next-5.md:3` | §11 第11 | TEAM-19、27、45–50、58–66（按主题） |  |
| `docs/modernization/quality/defect-bank/one-architecture.md:3` | §11 第11 | TEAM-19、27、45–50、58–66（按主题） |  |
| `docs/modernization/quality/defect-bank/one-data.md:3` | §11 第11 | TEAM-19、27、45–50、58–66（按主题） |  |
| `docs/modernization/quality/defect-bank/one-data.md:13` | §11 第10 | TEAM-19、27、45–50、58–66（按主题） |  |
| `docs/modernization/quality/defect-bank/one-glossary.md:3` | §11 第11 | TEAM-19、27、45–50、58–66（按主题） |  |
| `docs/modernization/quality/defect-bank/one-ops.md:3` | §11 第11 | TEAM-19、27、45–50、58–66（按主题） |  |
| `docs/modernization/quality/defect-bank/one-rules.md:3` | §11 第11 | TEAM-19、27、45–50、58–66（按主题） |  |
| `docs/modernization/quality/review-briefs/01-code-card.md:15` | §6.1；§14；§14.1 | 附 M1；DATA_DESIGN_CHECKLIST.md §14；DATA_DESIGN_CHECKLIST.md §14.1 |  |
| `docs/modernization/quality/review-briefs/01-code-card.md:24` | §6.1 第8 | 附 M1 第 4 条、TEAM-69 |  |
| `docs/modernization/quality/review-briefs/01-code-card.md:30` | §6.1 第10 | 附 M1 第 6 条 |  |
| `docs/modernization/quality/review-briefs/02-table-structure.md:15` | §14；§14.1；§12.2 | DATA_DESIGN_CHECKLIST.md §14；DATA_DESIGN_CHECKLIST.md §14.1；附 M3「规则调整三类」（迁移基线、修订评审结论同节） |  |
| `docs/modernization/quality/review-briefs/02-table-structure.md:28` | §12.2；§12.2 | 附 M3「规则调整三类」（迁移基线、修订评审结论同节）；附 M3「规则调整三类」（迁移基线、修订评审结论同节） |  |
| `docs/modernization/quality/review-briefs/02-table-structure.md:29` | §14.1 | DATA_DESIGN_CHECKLIST.md §14.1 |  |
| `docs/modernization/quality/review-briefs/03-adr.md:14` | §12；§12.1；§12.2 | 附 M3；附 M3「ADR」；附 M3「规则调整三类」（迁移基线、修订评审结论同节） |  |
| `docs/modernization/quality/review-briefs/03-adr.md:24` | §7 | TEAM-91–94 |  |
| `docs/modernization/quality/review-briefs/03-adr.md:31` | §12.2 | 附 M3「规则调整三类」（迁移基线、修订评审结论同节） |  |
| `docs/modernization/quality/review-briefs/04-production-change.md:14` | §7 | TEAM-91–94 |  |
| `docs/modernization/quality/review-briefs/05-decision-page.md:14` | §7；§12.2 | TEAM-91–94；附 M3「规则调整三类」（迁移基线、修订评审结论同节） |  |
| `docs/modernization/quality/review-briefs/05-decision-page.md:31` | §11 第1 | TEAM-19、27、45–50、58–66（按主题） |  |
| `docs/modernization/quality/review-briefs/06-forensics-report.md:32` | §11 第12 | TEAM-19、27、45–50、58–66（按主题） |  |
| `docs/modernization/quality/review-briefs/README.md:3` | §11 第11 | TEAM-19、27、45–50、58–66（按主题） |  |
| `docs/modernization/quality/review-briefs/README.md:9` | §6.1 | 附 M1 |  |
| `docs/modernization/quality/review-briefs/README.md:11` | §12 | 附 M3 |  |
| `docs/modernization/quality/review-briefs/README.md:13` | §7；§12 | TEAM-91–94；附 M3 |  |
| `docs/modernization/quality/review-briefs/README.md:41` | §6.1 | 附 M1 |  |
| `docs/modernization/quality/review-briefs/README.md:42` | §11 第12 | TEAM-19、27、45–50、58–66（按主题） |  |
| `docs/modernization/quality/review-briefs/README.md:53` | §12.1 | 附 M3「ADR」 |  |
| `docs/modernization/rules/BID_DECISION_RULES.md:20` | §12.2 | 附 M3「规则调整三类」（迁移基线、修订评审结论同节） |  |
| `docs/modernization/rules/DATA_RULE_REVIEW.md:3` | §14.1 | DATA_DESIGN_CHECKLIST.md §14.1 |  |
| `docs/modernization/rules/IDENTITY_SINGLE_OPERATOR_RULES.md:3` | §14.1 | DATA_DESIGN_CHECKLIST.md §14.1 |  |
| `docs/modernization/rules/IDENTITY_SINGLE_OPERATOR_RULES.md:100` | §14.1 | DATA_DESIGN_CHECKLIST.md §14.1 |  |
| `docs/modernization/rules/IDENTITY_SINGLE_OPERATOR_RULES.md:132` | §14.1 | DATA_DESIGN_CHECKLIST.md §14.1 |  |
| `docs/modernization/rules/IDENTITY_SINGLE_OPERATOR_RULES.md:143` | §14.1 | DATA_DESIGN_CHECKLIST.md §14.1 |  |
| `docs/modernization/rules/R1_RULING_REPLY.md:38` | §12.2 | 附 M3「规则调整三类」（迁移基线、修订评审结论同节） |  |
| `docs/modernization/rules/RULES_CONTEXT.md:3` | §2.1 第13 | TEAM-07、08 |  |
| `docs/modernization/rules/SOURCE_REFERENCE_RULES.md:118` | §14.1 | DATA_DESIGN_CHECKLIST.md §14.1 |  |
| `docs/modernization/rules/TIMESHEET_FILING_RULES.md:122` | §12.2 | 附 M3「规则调整三类」（迁移基线、修订评审结论同节） |  |
| `docs/modernization/rules/build_brief.py:86` | §13 | 附 M4 |  |
| `docs/modernization/rules/review_revisions.py:2` | §12.2 | 附 M3「规则调整三类」（迁移基线、修订评审结论同节） |  |
| `docs/modernization/sessions/_retired.md:1` | §2.1 第16 | TEAM-02–04、14 |  |
| `docs/modernization/sessions/one-1a-admin.md:55` | §2.1 第1 | TEAM-05、06 |  |
| `docs/modernization/sessions/one-1a-admin.md:61` | §5 | TEAM-29–39、附 M5（按主题） |  |
| `docs/modernization/sessions/one-1a-next-5.md:46` | §2.1 第1 | TEAM-05、06 | 疑似 |
| `docs/modernization/sessions/one-1a-par.md:54` | §2.1 第1 | TEAM-05、06 | 疑似 |
| `docs/modernization/sessions/one-1a-par.md:61` | §11 | TEAM-19、27、45–50、58–66（按主题） |  |
| `docs/modernization/sessions/one-1a-par.md:63` | §2.1 | TEAM-01–13 | 疑似 |
| `docs/modernization/sessions/one-arch-finance.md:25` | §14；§14.1 | DATA_DESIGN_CHECKLIST.md §14；DATA_DESIGN_CHECKLIST.md §14.1 |  |
| `docs/modernization/sessions/one-arch-finance.md:58` | §11 第7 | TEAM-58–61 |  |
| `docs/modernization/sessions/one-arch-finance.md:59` | §5 | TEAM-23、26 |  |
| `docs/modernization/sessions/one-arch-people.md:29` | §14；§14.1；§14.2 | DATA_DESIGN_CHECKLIST.md §14；DATA_DESIGN_CHECKLIST.md §14.1；DATA_DESIGN_CHECKLIST.md §14.2 |  |
| `docs/modernization/sessions/one-arch-people.md:54` | §2.1 第1 | TEAM-05、06 | 疑似 |
| `docs/modernization/sessions/one-arch-people.md:59` | §5 | TEAM-32–38 |  |
| `docs/modernization/sessions/one-arch-people.md:62` | §5 | TEAM-29–39、附 M5（按主题） |  |
| `docs/modernization/sessions/one-arch-revenue.md:30` | §14；§14.1；§14.2 | DATA_DESIGN_CHECKLIST.md §14；DATA_DESIGN_CHECKLIST.md §14.1；DATA_DESIGN_CHECKLIST.md §14.2 |  |
| `docs/modernization/sessions/one-arch-revenue.md:67` | §5 | TEAM-29–39、附 M5（按主题） |  |
| `docs/modernization/sessions/one-architecture.md:72` | §11 第2 | TEAM-19、27、45–50、58–66（按主题） |  |
| `docs/modernization/sessions/one-architecture.md:73` | §11 第9 | TEAM-19、27、45–50、58–66（按主题） | 疑似 |
| `docs/modernization/sessions/one-coordinator.md:10` | §11 | TEAM-19、27、45–50、58–66（按主题） |  |
| `docs/modernization/sessions/one-coordinator.md:11` | §5 | TEAM-29–39、附 M5（按主题） | 疑似 |
| `docs/modernization/sessions/one-coordinator.md:34` | §11 | TEAM-58–61 | 疑似 |
| `docs/modernization/sessions/one-coordinator.md:40` | §2.1 第1 | TEAM-05、06 |  |
| `docs/modernization/sessions/one-coordinator.md:55` | §11 | TEAM-19、27、45–50、58–66（按主题） | 疑似 |
| `docs/modernization/sessions/one-data.md:12` | §14.1 | DATA_DESIGN_CHECKLIST.md §14.1 |  |
| `docs/modernization/sessions/one-data.md:68` | §11 第10 | TEAM-19、27、45–50、58–66（按主题） |  |
| `docs/modernization/sessions/one-glossary.md:13` | §5 | TEAM-29–39、附 M5（按主题） |  |
| `docs/modernization/sessions/one-glossary.md:81` | §5 | TEAM-29–39、附 M5（按主题） |  |
| `docs/modernization/sessions/one-merge.md:40` | §11 第14 | TEAM-19、27、45–50、58–66（按主题） |  |
| `docs/modernization/sessions/one-platform-file.md:54` | §2.1 第1 | TEAM-05、06 |  |
| `docs/modernization/sessions/one-quality.md:10` | §6.1 | 附 M1 |  |
| `docs/modernization/sessions/one-rules.md:14` | §11 第1 | TEAM-19、27、45–50、58–66（按主题） |  |
| `docs/modernization/sessions/one-rules.md:69` | §11 第10 | TEAM-19、27、45–50、58–66（按主题） |  |
| `docs/modernization/tasks/GOV-1021-QUA-04.md:18` | §5 | TEAM-35 |  |
| `docs/modernization/tasks/_README.md:3` | §5 | TEAM-23、26 |  |
| `docs/modernization/templates/WORK_CARD.md:3` | §5；§6.1；§11 | TEAM-32–38；附 M1；TEAM-19、27、45–50、58–66（按主题） |  |
| `docs/modernization/templates/WORK_CARD.md:9` | §5 | TEAM-23、26 |  |
| `docs/modernization/templates/WORK_CARD.md:25` | §6.1 第9 | 附 M1 第 5 条 |  |
| `docs/modernization/templates/WORK_CARD.md:26` | §6.1 第10 | 附 M1 第 6 条 | 疑似 |
| `docs/modernization/templates/WORK_CARD.md:38` | §6.1 第2 | 附 M1 第 1 条 | 疑似 |
| `docs/modernization/templates/WORK_CARD.md:41` | §6.1 第8 | 附 M1 第 4 条、TEAM-69 | 疑似 |
| `docs/modernization/templates/WORK_CARD.md:54` | §5 | TEAM-29–39、附 M5（按主题） |  |
| `docs/modernization/templates/WORK_CARD.md:61` | §12.2 | 附 M3「规则调整三类」（迁移基线、修订评审结论同节） |  |
| `docs/modernization/templates/WORK_CARD.md:73` | §6.1 | 附 M1 | 疑似 |
| `docs/modernization/templates/WORK_CARD.md:76` | §14；§14.1 | DATA_DESIGN_CHECKLIST.md §14；DATA_DESIGN_CHECKLIST.md §14.1 |  |
| `docs/modernization/templates/WORK_CARD.md:79` | §4 第7 | TEAM-59 |  |
| `docs/modernization/templates/WORK_CARD.md:145` | §5 | TEAM-32–38 |  |
| `scripts/ci/acceptance_guard.py:2` | §6.1 | 附 M1 |  |
| `scripts/ci/acceptance_rulings.py:2` | §6.1 | 附 M1 |  |
| `scripts/ci/migrations_immutable.py:2` | §12.2 | 附 M3「规则调整三类」（迁移基线、修订评审结论同节） |  |
| `scripts/ci/relevant_change.py:70` | §11 | TEAM-19、27、45–50、58–66（按主题） |  |
| `scripts/hooks/codex_stdin_gate.py:4` | §5 | TEAM-29–39、附 M5（按主题） |  |
| `scripts/hooks/codex_stdin_gate.py:31` | §5 | TEAM-32–38 |  |
| `scripts/hooks/codex_stdin_gate.py:33` | §5 | TEAM-29–39、附 M5（按主题） |  |
| `scripts/hooks/context_handoff.py:2` | §2.1 | TEAM-01–13 |  |
| `scripts/hooks/context_handoff.py:9` | §2.1 | TEAM-01–13 |  |
| `scripts/hooks/context_handoff.py:11` | §5 | TEAM-29–39、附 M5（按主题） |  |
| `scripts/hooks/context_handoff.py:114` | §5 | TEAM-29–39、附 M5（按主题） |  |
| `scripts/hooks/context_handoff.py:118` | §5 | TEAM-29–39、附 M5（按主题） |  |
| `scripts/hooks/handoff_snapshot.py:138` | §2.1 | TEAM-01–13 |  |
| `scripts/hooks/playbook_digest.py:12` | §2.1；§5；§11 | TEAM-01–13；TEAM-32–38；TEAM-58–61 |  |
| `scripts/hooks/playbook_digest.py:13` | §2.1；§5；§11 | TEAM-01–13；TEAM-29–39、附 M5（按主题）；TEAM-19、27、45–50、58–66（按主题） |  |
| `scripts/hooks/playbook_digest.py:14` | §2.1 | TEAM-01–13 |  |
| `scripts/hooks/playbook_digest.py:15` | §11 | TEAM-19、27、45–50、58–66（按主题） |  |
| `scripts/hooks/playbook_digest.py:58` | §5 | TEAM-29–39、附 M5（按主题） |  |
| `scripts/hooks/playbook_digest.py:66` | §5 | TEAM-32–38 |  |
| `scripts/hooks/playbook_digest.py:92` | §5 | TEAM-29–39、附 M5（按主题） |  |
| `scripts/hooks/playbook_digest.py:146` | §2.1 | TEAM-01–13 |  |
| `scripts/hooks/playbook_digest.py:207` | §2.1；§5；§11 | TEAM-01–13；TEAM-32–38；TEAM-58–61 |  |
| `scripts/hooks/precheck_before_pr.py:2` | §11 | TEAM-19、27、45–50、58–66（按主题） |  |
| `scripts/hooks/session_context.py:2` | §2.1 | TEAM-01–13 |  |
| `scripts/hooks/session_context.py:107` | §2.1 第16 | TEAM-02–04、14 |  |
| `scripts/merge/mq.py:18` | §11 | TEAM-19、27、45–50、58–66（按主题） |  |
| `scripts/precheck.sh:7` | §11 | TEAM-19、27、45–50、58–66（按主题） |  |
| `scripts/release/deploy_gate.py:14` | §12.2 | 附 M3「规则调整三类」（迁移基线、修订评审结论同节） |  |
| `scripts/release/deploy_gate.py:118` | §12.2 | 附 M3「规则调整三类」（迁移基线、修订评审结论同节） |  |

### 乙、不改（历史记录或冻结正文，靠本对照表解析）

| 文件或目录 | 行数 | 理由 |
|---|---|---|
| `docs/modernization/adr/src/ADR-0020.md` | 1 | ADR 正文（已采纳冻结；提议态随下次修订改） |
| `docs/modernization/adr/src/ADR-0022.md` | 3 | ADR 正文（已采纳冻结；提议态随下次修订改） |
| `docs/modernization/adr/src/ADR-0024.md` | 2 | ADR 正文（已采纳冻结；提议态随下次修订改） |
| `docs/modernization/adr/src/ADR-0027.md` | 1 | ADR 正文（已采纳冻结；提议态随下次修订改） |
| `docs/modernization/adr/src/ADR-0031.md` | 9 | ADR 正文（已采纳冻结；提议态随下次修订改） |
| `docs/modernization/adr/src/ADR-0033.md` | 5 | ADR 正文（已采纳冻结；提议态随下次修订改） |
| `docs/modernization/adr/src/ADR-0034.md` | 4 | ADR 正文（已采纳冻结；提议态随下次修订改） |
| `docs/modernization/adr/src/ADR-0035.md` | 2 | ADR 正文（已采纳冻结；提议态随下次修订改） |
| `docs/modernization/adr/src/ADR-0036.md` | 9 | ADR 正文（已采纳冻结；提议态随下次修订改） |
| `docs/modernization/adr/src/ADR-0037.md` | 14 | ADR 正文（已采纳冻结；提议态随下次修订改） |
| `docs/modernization/adr/src/ADR-0038.md` | 6 | ADR 正文（已采纳冻结；提议态随下次修订改） |
| `docs/modernization/adr/src/ADR-0039.md` | 2 | ADR 正文（已采纳冻结；提议态随下次修订改） |
| `docs/modernization/adr/src/ADR-0041.md` | 1 | ADR 正文（已采纳冻结；提议态随下次修订改） |
| `docs/modernization/adr/src/ADR-0043.md` | 4 | ADR 正文（已采纳冻结；提议态随下次修订改） |
| `docs/modernization/adr/src/ADR-0044.md` | 1 | ADR 正文（已采纳冻结；提议态随下次修订改） |
| `docs/modernization/adr/src/ADR-0045.md` | 3 | ADR 正文（已采纳冻结；提议态随下次修订改） |
| `docs/modernization/adr/src/ADR-0046.md` | 4 | ADR 正文（已采纳冻结；提议态随下次修订改） |
| `docs/modernization/adr/src/ADR-0047.md` | 6 | ADR 正文（已采纳冻结；提议态随下次修订改） |
| `docs/modernization/adr/src/ADR-0048.md` | 1 | ADR 正文（已采纳冻结；提议态随下次修订改） |
| `docs/modernization/adr/src/ADR-0050.md` | 2 | ADR 正文（已采纳冻结；提议态随下次修订改） |
| `docs/modernization/adr/src/ADR-0051.md` | 1 | ADR 正文（已采纳冻结；提议态随下次修订改） |
| `docs/modernization/adr/src/ADR-0053.md` | 1 | ADR 正文（已采纳冻结；提议态随下次修订改） |
| `docs/modernization/adr/src/ADR-0054.md` | 1 | ADR 正文（已采纳冻结；提议态随下次修订改） |
| `docs/modernization/adr/src/ADR-0056.md` | 2 | ADR 正文（已采纳冻结；提议态随下次修订改） |
| `docs/modernization/adr/src/ADR-0062.md` | 1 | ADR 正文（已采纳冻结；提议态随下次修订改） |
| `docs/modernization/adr/src/ADR-0063.md` | 1 | ADR 正文（已采纳冻结；提议态随下次修订改） |
| `docs/modernization/adr/src/ADR-0064.md` | 1 | ADR 正文（已采纳冻结；提议态随下次修订改） |
| `docs/modernization/adr/src/ADR-0066.md` | 1 | ADR 正文（已采纳冻结；提议态随下次修订改） |
| `docs/modernization/adr/src/ADR-0067.md` | 3 | ADR 正文（已采纳冻结；提议态随下次修订改） |
| `docs/modernization/adr/src/ADR-0071.md` | 3 | ADR 正文（已采纳冻结；提议态随下次修订改） |
| `docs/modernization/adr/src/ADR-0072.md` | 3 | ADR 正文（已采纳冻结；提议态随下次修订改） |
| `docs/modernization/adr/src/ADR-0073.md` | 2 | ADR 正文（已采纳冻结；提议态随下次修订改） |
| `docs/modernization/adr/src/ADR-0075.md` | 1 | ADR 正文（已采纳冻结；提议态随下次修订改） |
| `docs/modernization/data/repair-cards/DATA-REPAIR-approval.md` | 3 | 历史任务卡 |
| `docs/modernization/data/repair-cards/DATA-REPAIR-identity.md` | 2 | 历史任务卡 |
| `docs/modernization/data/repair-cards/DATA-REPAIR-timesheet-audit.md` | 3 | 历史任务卡 |
| `docs/modernization/data/repair-cards/DATA-REPAIR-workforce-REJUDGE-2026-10-21.md` | 1 | 历史任务卡 |
| `docs/modernization/data/repair-cards/DATA-REPAIR-workforce.md` | 2 | 历史任务卡 |
| `docs/modernization/phase1a/cards/ADR-0034-IMPL.md` | 5 | 历史任务卡 |
| `docs/modernization/phase1a/cards/D1.md` | 1 | 历史任务卡 |
| `docs/modernization/phase1a/cards/F-1.md` | 1 | 历史任务卡 |
| `docs/modernization/phase1a/cards/K-3b-4.md` | 2 | 历史任务卡 |
| `docs/modernization/phase1a/cards/NUM-VERSION-ID.md` | 1 | 历史任务卡 |
| `docs/modernization/phase1a/cards/R-1b-0.md` | 2 | 历史任务卡 |
| `docs/modernization/phase1a/cards/R-1b-P3b.md` | 6 | 历史任务卡 |
| `docs/modernization/phase1a/cards/R-1b-P4.md` | 5 | 历史任务卡 |
| `docs/modernization/phase1a/cards/R-1b-P5.md` | 5 | 历史任务卡 |
| `docs/modernization/phase1a/cards/SINGLE-OP.md` | 6 | 历史任务卡 |
| `docs/modernization/phase1a/cards/TA-A.md` | 7 | 历史任务卡 |
| `docs/modernization/phase1a/cards/TS-UNIQ.md` | 8 | 历史任务卡 |
| `docs/modernization/r0/FINANCE_DECISIONS.html` | 1 | 历史决策页 |
| `docs/modernization/r0/FINANCE_DECISIONS.md` | 3 | 历史决策页 |
| `docs/modernization/r0/FINANCE_QUESTIONS.md` | 1 | 历史决策页 |
| `docs/modernization/r0/METRIC_BASELINE_DRAFT.md` | 2 | 历史决策页 |
| `docs/modernization/r0/REPORT_INVENTORY.md` | 1 | 历史决策页 |
| `docs/modernization/r0/TERMS_ROUND3_INPUT.md` | 1 | 历史决策页 |
| `docs/modernization/r0/TOOL_COMPARISON.md` | 2 | 历史决策页 |
| `docs/modernization/rules/decide/sheets/A1.json` | 1 | 历史决策页数据 |
| `docs/modernization/rules/decide/sheets/ADR58.json` | 1 | 历史决策页数据 |
| `docs/modernization/rules/decide/sheets/GL2.json` | 1 | 历史决策页数据 |
| `docs/modernization/sessions/reviews/COMPETENCY_2026-10-16.md` | 2 | 历史评审 |
| `docs/modernization/sessions/reviews/PROFILE_AUDIT_2026-10-17.md` | 4 | 历史评审 |
| `docs/modernization/session-log/` | 40 | 已停的会话日志 |
| `docs/modernization/status/blockers/` | 1 | 已停的认领 |
| `docs/modernization/status/units/` | 3 | 已停的认领 |
| `docs/modernization/architecture/proposals/ACL_BOUNDARY.md` | 1 | 提议稿（起草会话随下一版改） |
| `docs/modernization/architecture/proposals/ADR_RULES_REVISION_DRAFT.md` | 12 | 提议稿（起草会话随下一版改） |
| `docs/modernization/architecture/proposals/ADR_RULES_REVISION_REVIEW.md` | 1 | 提议稿（起草会话随下一版改） |
| `docs/modernization/architecture/proposals/AEC_OBJECTS_SPEND_ORG_FINANCE.md` | 1 | 提议稿（起草会话随下一版改） |
| `docs/modernization/architecture/proposals/ARCH_CLOSEOUT_PLAN.md` | 1 | 提议稿（起草会话随下一版改） |
| `docs/modernization/architecture/proposals/ARCH_SSOT_CONSOLIDATION_PLAN.md` | 3 | 提议稿（起草会话随下一版改） |
| `docs/modernization/architecture/proposals/B31_ARCH_RULINGS_2026-10-21.md` | 2 | 提议稿（起草会话随下一版改） |
| `docs/modernization/architecture/proposals/CONSTRUCTION_PLAN_DRAFT.md` | 3 | 提议稿（起草会话随下一版改） |
| `docs/modernization/architecture/proposals/DMN_FIT_RESEARCH.md` | 2 | 提议稿（起草会话随下一版改） |
| `docs/modernization/architecture/proposals/ENTERPRISE_AGGREGATES_DRAFT.md` | 1 | 提议稿（起草会话随下一版改） |
| `docs/modernization/architecture/proposals/FOUNDATION_NEW_ITEMS.md` | 2 | 提议稿（起草会话随下一版改） |
| `docs/modernization/architecture/proposals/FOUNDATION_RECHECK_REVIEW.md` | 1 | 提议稿（起草会话随下一版改） |
| `docs/modernization/architecture/proposals/FOUNDATION_RECHECK_STATUS.md` | 1 | 提议稿（起草会话随下一版改） |
| `docs/modernization/architecture/proposals/FRAMEWORKS_V4_DRAFT.md` | 3 | 提议稿（起草会话随下一版改） |
| `docs/modernization/architecture/proposals/HANDOFF_2026-10-16-NIGHT.md` | 1 | 提议稿（起草会话随下一版改） |
| `docs/modernization/architecture/proposals/JOB_CATALOG_DRAFT.md` | 1 | 提议稿（起草会话随下一版改） |
| `docs/modernization/architecture/proposals/NAMING_ADR_0053_NOTES.md` | 1 | 提议稿（起草会话随下一版改） |
| `docs/modernization/architecture/proposals/Q04_REVENUE_OBJECT_RESEARCH.md` | 1 | 提议稿（起草会话随下一版改） |
| `docs/modernization/architecture/proposals/REVIEW_ADR_PLATFORM_TIERS_2026-10-20.md` | 1 | 提议稿（起草会话随下一版改） |
| `docs/modernization/architecture/proposals/STEP2_CAPABILITIES.md` | 1 | 提议稿（起草会话随下一版改） |
| `docs/modernization/architecture/proposals/adversarial/R1-E.md` | 1 | 提议稿（起草会话随下一版改） |
| `docs/modernization/architecture/proposals/closeout/APPROVAL_RULES.yaml` | 1 | 提议稿（起草会话随下一版改） |
| `docs/modernization/architecture/proposals/closeout/finance/REVIEW.md` | 2 | 提议稿（起草会话随下一版改） |
| `docs/modernization/architecture/proposals/closeout/finance/SAMPLE.md` | 1 | 提议稿（起草会话随下一版改） |
| `docs/modernization/architecture/proposals/closeout/finance/processes.finance.yaml` | 2 | 提议稿（起草会话随下一版改） |
| `docs/modernization/architecture/proposals/closeout/people/DATA_MODEL.md` | 4 | 提议稿（起草会话随下一版改） |
| `docs/modernization/architecture/proposals/closeout/people/REVIEW_VS14.md` | 3 | 提议稿（起草会话随下一版改） |
| `docs/modernization/architecture/proposals/closeout/people/b31-rules/B31_ADM02_BLUEPRINT.md` | 3 | 提议稿（起草会话随下一版改） |
| `docs/modernization/architecture/proposals/closeout/people/b31-rules/B31_ALIGNMENT_AUDIT.md` | 1 | 提议稿（起草会话随下一版改） |
| `docs/modernization/architecture/proposals/closeout/people/b31-rules/B31_CONSTRUCTION_DESIGN.md` | 3 | 提议稿（起草会话随下一版改） |
| `docs/modernization/architecture/proposals/closeout/people/parts/b15data.DATA_MODEL.md` | 1 | 提议稿（起草会话随下一版改） |
| `docs/modernization/architecture/proposals/closeout/people/parts/b31.DATA_MODEL.md` | 1 | 提议稿（起草会话随下一版改） |
| `docs/modernization/architecture/proposals/closeout/people/parts/vs07.processes.yaml` | 1 | 提议稿（起草会话随下一版改） |
| `docs/modernization/architecture/proposals/closeout/people/parts/vs0911.DATA_MODEL.md` | 1 | 提议稿（起草会话随下一版改） |
| `docs/modernization/architecture/proposals/closeout/people/parts/vs0911.data-model.yaml` | 1 | 提议稿（起草会话随下一版改） |
| `docs/modernization/architecture/proposals/closeout/people/processes.people.yaml` | 1 | 提议稿（起草会话随下一版改） |
| `docs/modernization/architecture/proposals/closeout/revenue/C_DEFAULTS.md` | 2 | 提议稿（起草会话随下一版改） |
| `docs/modernization/architecture/proposals/closeout/revenue/NOTES.md` | 1 | 提议稿（起草会话随下一版改） |
| `docs/modernization/architecture/proposals/closeout/revenue/QC-LIFECYCLE.md` | 1 | 提议稿（起草会话随下一版改） |
| `docs/modernization/architecture/proposals/closeout/revenue/SAMPLE.md` | 2 | 提议稿（起草会话随下一版改） |
| `docs/modernization/architecture/proposals/closeout/revenue/data-model.revenue.yaml` | 1 | 提议稿（起草会话随下一版改） |
| `docs/modernization/architecture/proposals/closeout/rulings/S-31_FINANCE_SQ01-33.md` | 1 | 提议稿（起草会话随下一版改） |
| `docs/modernization/proposals/ADR-0022_CRITIC.md` | 1 | 提议稿（起草会话随下一版改） |
| `docs/modernization/proposals/EVENT_ANALYTICS_FIELDS.md` | 1 | 提议稿（起草会话随下一版改） |
| `docs/modernization/proposals/GL_SCOPE_REVISIONS.md` | 2 | 提议稿（起草会话随下一版改） |
| `docs/modernization/proposals/PHASE1A_PLATFORM_CONVENTIONS.md` | 1 | 提议稿（起草会话随下一版改） |
| `docs/modernization/architecture/frameworks/notes/bernstein-tp2.md` | 5 | 框架笔记 |
| `docs/modernization/architecture/frameworks/notes/dama-dmbok2.md` | 10 | 框架笔记 |
| `docs/modernization/architecture/frameworks/notes/ddd-reference.md` | 10 | 框架笔记 |
| `docs/modernization/architecture/frameworks/notes/evans-ddd2003.md` | 13 | 框架笔记 |
| `docs/modernization/architecture/frameworks/notes/hohpe-eip.md` | 4 | 框架笔记 |
| `docs/modernization/architecture/frameworks/notes/kimball-dwt3.md` | 2 | 框架笔记 |
| `docs/modernization/architecture/frameworks/notes/silverston-dmrb1.md` | 4 | 框架笔记 |
| `docs/modernization/intent/` | 65 | 用户原话记录 |
| `docs/modernization/glossary/arch-naming/N4_COVERAGE.md` | 89 | 用语决策数据与覆盖记录 |
| `docs/modernization/glossary/arch-naming/N4_VERBS_STATES.md` | 1 | 用语决策数据与覆盖记录 |
| `docs/modernization/glossary/arch-naming/NAMING_ATOMS_COVERAGE.md` | 1 | 用语决策数据与覆盖记录 |
| `docs/modernization/glossary/arch-naming/RECONCILE_DECIDED.md` | 1 | 用语决策数据与覆盖记录 |
| `docs/modernization/glossary/arch-naming/reconcile-decided.json` | 11 | 用语决策数据与覆盖记录 |
| `docs/modernization/glossary/round3/ROUND3_DECISIONS.html` | 1 | 用语轮次记录 |
| `docs/modernization/glossary/round3/inventory.json` | 1 | 用语轮次记录 |
| `docs/modernization/glossary/round3/r0-items.json` | 2 | 用语轮次记录 |
| `docs/modernization/glossary/round3/round3-items.json` | 4 | 用语轮次记录 |
| `docs/modernization/glossary/round3/sheets/R3-NU.json` | 1 | 用语轮次记录 |
| `docs/modernization/glossary/round3/sheets/R3-RN.json` | 2 | 用语轮次记录 |
| `docs/modernization/architecture/research/R-W2设计复核-2026-10-21.md` | 2 | 研究稿 |
| `docs/modernization/architecture/research/networks/UNTESTED_P0_RULES.md` | 1 | 研究稿 |
| `docs/modernization/architecture/research/platform-fit-2026-10-20/adr-drafts/ADR-草案-在线预览.md` | 1 | 研究稿 |
| `docs/modernization/architecture/research/platform-fit-2026-10-20/adr-drafts/ADR-草案-外部访问.md` | 1 | 研究稿 |
| `docs/modernization/architecture/research/platform-fit-2026-10-20/adr-drafts/ADR-草案-平台分档与准入.md` | 1 | 研究稿 |
| `docs/modernization/architecture/research/platform-fit-2026-10-20/adr-drafts/ADR-草案-平台分档冻结与准入.md` | 5 | 研究稿 |
| `docs/modernization/architecture/research/platform-fit-2026-10-20/adr-drafts/ADR-草案-平台契约冻结.md` | 3 | 研究稿 |
| `docs/modernization/architecture/research/platform-fit-2026-10-20/adr-drafts/ADR-草案-签章服务.md` | 1 | 研究稿 |
| `docs/modernization/architecture/research/platform-fit-2026-10-20/adr-drafts/R-ADR草案复审.md` | 1 | 研究稿 |
| `docs/modernization/architecture/research/platform-fit-2026-10-20/平台分档适配复核.md` | 5 | 研究稿 |
| `docs/modernization/quality/TEST_IMPACT_RESEARCH.md` | 5 | 研究稿 |
| `docs/modernization/rulings/ACCEPTANCE_RULINGS.yaml` | 1 | 裁定记录 |
| `docs/modernization/rulings/COORD-2026-10-17.md` | 1 | 裁定记录 |
| `docs/modernization/rulings/COORD-2026-10-21.md` | 1 | 裁定记录 |
| `docs/modernization/rulings/COORD-2026-10-22.md` | 2 | 裁定记录 |
| `docs/modernization/COORDINATOR_LEDGER.md` | 6 | 调度台账（调度自改） |
