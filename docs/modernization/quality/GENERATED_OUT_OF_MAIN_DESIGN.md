# 生成产物、状态记录与历史快照移出 main——设计（待 one-coordinator 给用户看过再实施）

依据：用户 2026-10-22 定的 git 原则（intent 2026-10-22-080434，SESSION_PLAYBOOK TEAM-57）；起因是合并队列里 PR 反复只在生成产物上冲突、挂十几个小时，和用户问「文档为什么这么大」。本文只设计，不改仓库。四份逐项调研报告（含文件:行证据）见 `~/.modernize/psa/design-gen/`（A 纯生成判定、B 读者清单、C 快照与一次性产出、D 画像与调度台账），本文引用时写「报告 A/B/C/D」。

## 〇、结论与建议

1. **范围量级**（origin/main 5badfa3a3 实测）：入库文本 119.8 万行，其中 yaml/json 72.4 万行、md 19.4 万行。入库的生成产物 95 个共 13.1 万行（yaml/json 11.4 万，其余 1.7 万）；历史快照与一次性产出另有约 24.6 万行 yaml/json。两项都移走后 main 约少 37.6 万行（31%），yaml/json 从 72.4 万降到约 36.5 万。
2. **分五期，各期单列 PR、可单独回退**，风险从低到高：①无代码读者的生成产物（ADR 拷贝等）→ ②全景链（panorama）→ ③用语词表 `terms.yaml`（读者最多，唯一要改测试代码和 CI 作业的）→ ④历史快照挪到只读归档分支 → ⑤画像「当前状态」迁任务卡片段、`COORDINATOR_LEDGER.md` 本机化。每期的行数、读者、改法、验证、回退见第七节。
3. **「纯生成」不等于「能直接移出」**：有 4 个混合体（生成器读自己的旧输出并保留手写部分）必须先拆成「手写源 + 纯产物」才能动：`STAKEHOLDERS.md`（23% 手写）、`ssot/10-objects/transitions.yaml`（几乎全手写，生成器只回填 `by_command`）、`RESPONSIBILITY_MATRIX.yaml`（1 条手登记）、`processes.people.yaml`（13 行文件头）。另有 4 个本机 mermaid 渲染物 CI 无法重现，建议继续入库。
4. **「mq 自动重生成」（#568）**：本设计做完后它基本用不上（冲突的源头不在库里了），但迁移要几天到几周、且前端生成物（`openapi.json`、`validators.ts`）和 4 个渲染物仍在库里。建议 #568 照合入当过渡，不再扩展；全部移出后看一个月日志，没有 `AUTO-RESOLVE` 再删。
5. **需要用户拍板的几点**放在第九节（归档分支怎么保护、`terms.yaml` 是否冷启动自动生成、`panorama.yaml` 这条链上游落后的产物谁来补、`HANDOFF_*.md` 和 `evidence/` 的去向）。

## 一、判定方法

- **哪些是生成产物**：`build_all.py` 全量运行时在 hook 下记录它写入的入库文件（95 个，含没变就不写的靠读代码补上），再用「垃圾复原」验证：把产物改成垃圾，重跑生成器，结果与正常运行逐字节一致的才算纯生成（报告 A）。
- **谁在读**：对每个产物 grep `.github/`、`scripts/`、`backend/src/test`、`frontend/`、`docs/**` 的路径与文件名，含 python 里拼出的路径；markdown 链接单独统计（报告 B）。
- **快照是否还在用**：同样查读者，再用 `git log --since=2026-10-18` 看最近是否还被改（报告 C）。

## 二、生成产物的分类（报告 A）

| 类 | 内容 | 行数（约） | 处置 |
|---|---|---|---|
| 纯生成·无读者 | `adr/ADR-*.md` 76 份（与 `adr/src/` 逐字相同）、`adr/INDEX.md`、`adr/README.md`、`MODERNIZATION_BRIEF.md`、`backend/glossary/proposed/README.md`、`frameworks/CONCEPT_INDEX.md`/`NOTES_INDEX.md`、`panorama/FREEZE_FACTS.{md,yaml}`、`FREEZE_IMPACT.md`、`generated/*.mmd` 与 Python 直画的 3 张 svg | 约 1.5 万 | 第一期移出 |
| 纯生成·有顺序依赖 | `panorama/panorama.yaml`（9.5 万行，3.8 MB）、`GAPS.md` | 约 9.5 万 | 第二期：先调 STEPS 顺序、缺文件改失败 |
| 纯生成·读者最多 | `glossary/terms.yaml`（1.6 万行） | 1.6 万 | 第三期：CI 加生成步骤、改测试 |
| 混合体 | `proposals/STAKEHOLDERS.md`、`ssot/10-objects/transitions.yaml`、`closeout/RESPONSIBILITY_MATRIX.yaml`、`closeout/people/processes.people.yaml` | — | 先拆再说；`transitions.yaml` 不当产物 |
| 本机渲染 | `generated/{1b-context-map.svg,3-event-chain.svg,index.html,svg-sources.sha256}` | — | 继续入库（CI 无 mermaid-cli，重渲染结果随版本变）；或装进 CI 后再移 |
| 误称生成的手写源 | `bpmn/processes.yaml`、`dmn/decisions.yaml`、`ddd/aggregates.yaml`、`closeout/{process-catalog,bc-map,assignments}.yaml`、`categories.proposal.yaml` 头里写「generated」 | — | 保留；改头注释防误判 |
| 不在 build_all 里的「生成物」 | `ssot/**` 下各 `extract_*`、`scan_*`、`convert_*` 的产物，`closeout/` 下 `CATALOG.md`、`CRUD_MATRIX.md`、`SEAM_REPORT.md` 等 | — | **本期不动**：它们没有 CI 守护、入库版已落后（`execution-points.yaml`、`tests.yaml` 差几百行），`scan_aggregates` 在 HEAD 上直接失败（`platform_file.stored_files: AGGREGATE_ROOT 未唯一对应 domain 类：StoredFile`）。panorama 的上游含这些，所以第二期等价迁移不成立（第九节问题 3） |
| 前端生成物 | `openapi.json`、`schema.ts`、`validators.ts` | — | 不是 build_all 的产物，由后端导出测试写；**不在本期范围** |

## 三、读者与改法（报告 B）

### 三-1 `glossary/terms.yaml`（耦合最强，第三期）

- 读者：6 个后端测试类经 `Glossary.java:20` 读，另有 `DocumentTypeCodeTest.java:20`；前端 `check-copy-glossary.mjs:13`、`copy-glossary.test.mjs:14`；`build_all` 链上全景追溯、对象冻结、资料笔记、`scan_org_roles` 等；`relevant_change.py` 靠它分级；CLAUDE.md 第 1、2 条要求「查词表」。
- 缺文件时的现状：前端抛错；后端 `Glossary.java:95-97`、`DocumentTypeCodeTest.java:45-48` 用 `Files.exists` **静默跳过**；`check_pages.py:212` 也静默跳过。
- 改法：
  1. `ci.yml` 的 backend 作业与 frontend 作业各加一步 `python3 docs/modernization/glossary/build_terms.py`（需 PyYAML 6.0.3，runner 已有，因为 gitleaks 作业的 `build_all.py --check` 在用）。
  2. 后端两处与 `check_pages.py:212` 改成缺文件即**失败**并写明「先运行 build_all.py」（CLAUDE.md 第 6 条：不静默跳过）。
  3. `build_terms.py` 读自己旧输出的两处（`apply_round3.py:197`、`apply_recon.py:152`）只影响校验与待改名过滤；已验证把 `terms.yaml` 换成空桩 `terms: []` 后输出不变，冷启动可行。
  4. 本地：新建工作区没有词表，开发者与会话要先生成。在 SessionStart 钩子或 `precheck.sh` 开头加「缺 `terms.yaml` 就生成」（几秒），并在 CLAUDE.md 第 2 条写明路径与生成命令。
  5. `backend/glossary/terms.yaml`（3 KB，手写）和 `architecture-guardrails/` 里的副本不是产物，别误删。

### 三-2 `panorama.yaml`、`GAPS.md`（第二期）

- 读者：`object_freeze_check.py`、`notes_check.py`。
- 问题：`build_all.py` 的 STEPS 里 `notes_build`（42 行）排在 `panorama_build`（44 行）之前；移出后 `notes_check.py:148-150` 找不到文件返回 None，**静默跳过** VS/PRC/OBJ 编号校验，CI 不会红但守护失效。
- 改法：调 STEPS 顺序（panorama 在前）；`notes_check.py` 缺文件改失败。

### 三-3 `adr/ADR-*.md`、`adr/README.md`、`adr/INDEX.md`（第一期）

- 无代码读者。唯一的消费者是 `generated-docs.yml:40`，它在 `build_all` 之后跑，不依赖入库。
- 链接（第四节）：约 12 处活文档与数据文件要改成 `adr/src/`；`N4_COVERAGE.md` 有 5432 处路径引用，是历史快照，不改。
- `generated-docs.yml` 目前不拷 `INDEX.md`，移出时补。

### 三-4 其余产物

- `MODERNIZATION_BRIEF.md`、`FREEZE_*`、两个 frameworks 索引、`proposed/README.md`：只有文字引用，改文字。
- `generated/*.mmd` 和 Python 直画的 3 张 svg：只被生成器自己读。

### 三-5 通用改动

- `.gitignore`：每期把移出的路径加进去（ADR 拷贝用锚定通配避开 `adr/src/`）。**必须同时做**，否则 `build_all.py:55-59` 的前后快照比对在 CI 全新检出里会报「生成产物与片段不一致」。
- `build_all.py` 头注释（`:7-12`）「terms.yaml、ADR、README、brief… 过期或缺失时失败」改为「不入仓，只要求能生成」。
- `generated-docs.yml` 拷贝清单扩充：`terms.yaml`、`adr/INDEX.md`、panorama 三件、frameworks 两个索引、`proposed/README.md`、`architecture/generated`，README 清单同步。
- 规范文字：CLAUDE.md:7,11,12,22、AGENTS.md:9、README.md:3,5,7、SESSION_PLAYBOOK:22,79,305、CODING_STANDARD:188 把产物当入库文件的说法改成「不入仓，本地 `build_all.py` 生成或看 generated-docs」。
- `check_legacy_name.py:14,29,31` 注释与 SKIP 名单同步；`relevant_change.py` 自测复核。

## 四、指向 `adr/ADR-*.md` 等文件的链接怎么处理

- 采用「**改指 `adr/src/`**」：源文件一直是真源，已采纳 ADR 正文冻结，链接稳定；不指向 generated-docs 分支（那里是只读展示副本，链接会随分支更新漂移）。
- CLAUDE.md、SESSION_PLAYBOOK、CODING_STANDARD、NAMING_STANDARD 里没有指向 `adr/ADR-xxxx.md` 的链接；需要改的是 `GUIDING_PRINCIPLES.md:105`、`PHASE1A_PLATFORM_CONVENTIONS.md:6,35`、`GL_FINANCE_REVIEW.md:3`、`INFRA_RUNBOOK.md:410`、`platform-integration.yaml`、`INTEGRATION_INVENTORY.yaml`、`decisions-recon-b.yaml` 等约 12 处，加 `adr/README.md` 被约 15 处文字指向（改指 `adr/src/_readme_*.md` 或写「生成后」）。
- `src/ADR-0036.md` 里有 13 处自引用，属已采纳 ADR 正文，**不改**。`N4_COVERAGE.md` 的 5432 处是历史快照，不改。
- 没有链接校验器，所以这部分不影响 CI；做完用本机 grep 脚本（报告 B 附 `scan_adr.py`）核一遍。

## 五、历史快照与一次性产出挪到只读归档分支（报告 C）

| 类 | 文件 | 行数 | 读者 | 建议 |
|---|---|---|---|---|
| 老系统一次性分析 | `topology.json`、`map/*.json`、`extract-rules.modules.json`、`live_code.json`、`live_code_prod.json` | 67,893 | 只有对应的一次性脚本（一起归档）；`TOPOLOGY.html` 内嵌数据，不读这些；`RULE_REVIEWS.json` 是活数据，**保留** | 归档 |
| 用语对账里没人读的 | `N7_RENAME_FINAL.json`、三份 `*-draft.yaml`、`round3-items.json` | 19,010 | 无 | 归档 |
| 已答完且 7 天没改的确认页题面 | `rules/decide/sheets/` 下 18 份（A1、GL、NM1、NM6 … OBJD、TRN） | 14,311 | 无（答复在 intent 与页面数据库） | 归档 |
| `reconcile-decided.json` | 132,571 行；构建只读其中 `missing_terms`、`proposed` 两段约 13,300 行 | 省约 119,000 | `apply_recon.py:190` | 抽成瘦身文件，该行改一行路径；原文件归档 |
| `NM8A`、`NM8B` | 已撤回、等定稿重新生成的题面 | 25,057 | 无 | 先问 one-architecture 同意再归档 |
| ssot 原始抽取稿 | `roles.raw.yaml` 被 `panorama_build.py` 读，**保留**；`state-machines`/`facts`/`dimensions` 的 raw 共 11,289 行无读者但才建 3 天仍在重新生成 | — | — | 等 ssot 冻结后再归档 |
| **必须保留** | 用语生成链输入（第三轮 `sheets/`、`inventory.json`、`r0-items.json`、`decisions-recon*.yaml`——`build_terms.py` 每次 `build_all --check` 都读并比对）；`rules/decide/answers/` 全部 25 份（`decisions-recon.yaml` 对其哈希校验）；确认页题面 `NM2`~`NM5`（`apply_nm.py` 读）；仍在用的 `OBJFIN`、`STKH`、`STKH2`、`SGN`、`ADV1`；`B31P`、`OBDS`、`PAN`（状态不明，先问 one-architecture）| | | 保留 |
| 范围外值得另评 | `panorama.yaml`（9.5 万行，已在第二期）、`RENAME_C.yaml`（1.5 万行） | | | 另议 |

- **归档方式**：新建只读分支 `archive/2026-10-22-snapshots`（从当时的 main 分出，删去无关内容只留这些文件，保留原路径），main 上留一个 `docs/modernization/ARCHIVE_INDEX.md`（手写，列「文件｜行数｜归档分支:原路径｜原因」），文档里提到这些路径的文字（`GUIDING_PRINCIPLES.md`、`MODERNIZATION_BRIEF.template.md`、`decide/README.md`、B31P 证据文等）改成「归档分支:原路径」。仓库没有链接校验器，不影响 CI。
- **保护**：分支保护规则禁止删除与强推（需要用户在 GitHub 设置里做，我不动仓库设置）。
- **前后对比**：yaml/yml/json 行数 723,333 → 约 477,800（-34%，只算本节），再算第二节的产物移出后约 36.5 万。

## 六、画像「当前状态」与 COORDINATOR_LEDGER（报告 D）

### 六-1 会话画像的「当前状态」行 → 任务卡片段

- 16 份画像的 12 个字段里只有「当前状态」是随进展变化的流水账（PR 号、在途项、HANDOFF 指向）；`one-merge.md:67` 已是指针型，可作目标形态。10-18 起 `sessions/` 改了 22 次，同类的 `*_CONTEXT.md` 也在频繁改（OPS 12 次、QUALITY 9 次、MERGE 6 次）。
- 要改：
  - `build_sessions.py:25` `FIELDS` 把「当前状态」去掉必填；`:82`「以已交接开头报错」规则去掉或改成对任务卡状态的检查；`:116` 起的 24 小时 freshness 检查改成读任务卡片段的「最后更新」（#545 已加该字段）。
  - `session_context.py` 开场注入画像时改为注入「该会话名下状态为进行中/阻塞的任务卡片段摘要」（读 `tasks/*.md`）；`handoff_snapshot.py` 的开场摘要、`context_handoff.py` 的「是否更新过」判定同步改。
  - SESSION_PLAYBOOK TEAM-07、08（「现状原地覆盖、当前状态一行」）改写；记 intent。
- 画像里的「当前状态」节删除（或改成一句指针「见 tasks/ 中本会话的进行中卡」）。

### 六-2 `COORDINATOR_LEDGER.md` 本机化

- 18 KB、105 行、15 节，10-18 起被改 39 次（10-21 一天 31 次）；钩子、`build_*.py`、CLAUDE.md 都没有硬编码引用，靠四份画像（one-coordinator、one-1a-admin、one-1a-par、one-platform-file）的「上下文文档」被注入；全仓约 37 个文件提到它，多数是历史片段。
- 拆分：状态类（交接、会话、合并、技术裁定在途、部署阻塞）和待办类（待用户、暂缓、排定后续）→ 本机；决定记录类（授权与边界、风险登记）→ 补成 intent 片段入仓；稳定类（作战图、开场核对、长效惯例）→ 升入 SESSION_PLAYBOOK（TEAM-27 等）。
- 本机路径 `~/.modernize/psa/coordinator/LEDGER.md`；每次覆盖前自动备份到 `coordinator/backups/`（保留 14 天或 100 份），/clear 前再复制一份进现有 `~/.modernize/psa/handoff/`（那里是一次一个的快照，别混放）。
- 要改：`session_context.py` 的 `unsafe_reason` 现在禁绝对路径，需加受控本机路径白名单；`build_sessions.py` 的「上下文文档必须存在」校验对本机路径放行；SESSION_PLAYBOOK 5 处、ADR-0038（引用「部署阻塞」节和行号）、MERGE_CONTEXT 和画像的提及要改。

### 六-3 一次性产出的 `.gitignore`

- 现有规则已覆盖生成物、`*.local.*`、`__pycache__`。仍被追踪的：`*.log` 3 个（`architecture-guardrails/`）、`*.patch` 2 个、`HANDOFF_*.md` 19 个（`templates/` 下 1 个是模板要留）、`evidence/` 115 个文件（含 7 个 `*_result.md`）、`*_CONTEXT.md` 7 份。
- 建议新增的忽略规则见报告 D 3.3；已追踪的需 `git rm --cached` 才会消失；全局忽略 `*.log`、`*.patch` 前先确认没有测试把它们当固定输入；`evidence/` 先对照 ADR-0037（生产取证登记，明细不进仓库）再定；**HANDOFF 的去向定下来之前不忽略**（第九节问题 4）。

## 七、迁移步骤与回退

通则：每期一个 PR（守护代码与 CI 改动单列、opus 首轮复审）；每期合入前在 `main` 打 tag `pre-gen-out-N`；移出用 `git rm --cached` + `.gitignore`，**同一个 PR 里**把读者改好；合入后第一次 CI 全新检出必须绿（这是关键验证）。

| 期 | 内容 | 减少行数（约） | 读者改动 | 回退 |
|---|---|---|---|---|
| 0 | 预备：`generated-docs.yml` 拷贝清单扩充、`build_all.py` 头注释、第三-5 节的文字改动、混合体拆分的设计小结（不拆代码）| 0 | 无 | 还原 PR |
| 1 | 无读者产物：76 个 ADR 拷贝 + `INDEX/README`、`BRIEF`、`proposed/README`、frameworks 索引、`FREEZE_*`、`generated/*.mmd` 与 Python svg；约 12 处链接改指 `adr/src/` | 约 1.5 万 | 无代码读者 | 还原 PR（`git revert`）即恢复入库版（内容是移出时的旧值，可 `build_all` 重生成）；generated-docs 分支另有一份 |
| 2 | `panorama.yaml`、`GAPS.md`：STEPS 调序、`notes_check.py` 缺文件失败 | 约 9.5 万 | `notes_check`、`object_freeze_check` | 还原 PR |
| 3 | `terms.yaml`：CI 两个作业加生成步骤、三处「缺文件静默跳过」改失败、SessionStart/precheck 冷启动生成、CLAUDE.md 第 2 条 | 1.6 万 | 后端 6+1 测试类、前端 3 脚本、build_all 链 5 脚本 | 还原 PR；风险最高，在一个 CI 周期内观察；各会话要合 main 并重启 |
| 4 | 快照归档：建 `archive/…` 分支、`ARCHIVE_INDEX.md`、`reconcile-decided` 瘦身与 `apply_recon.py:190` 改路径、文字改归档分支:原路径 | 约 24.6 万（yaml/json 口径 245,500，含瘦身） | `apply_recon.py` | 从归档分支 `git checkout archive/… -- 路径` 取回；瘦身可还原 PR |
| 5 | 画像「当前状态」迁任务卡、COORDINATOR_LEDGER 本机化、`.gitignore` 一次性产出、钩子与 `build_sessions.py` 改动 | 数千行，但改动频率最高 | 开场钩子、`build_sessions.py`、`context_handoff.py` | 钩子按旧逻辑可共存：先让新逻辑「读任务卡，缺则读画像」，一个周期后再删旧逻辑 |

验证清单（每期）：`precheck.sh`、CI 三项在**全新检出**上绿、`build_all.py --check` 两次一致、`relevant_change.py --self-test`、本期读者的自测；第 3 期另跑后端全量与前端 verify。

## 八、对 #568（mq 自动重生成）的影响

- 冲突只发生在入库的生成产物与前端生成物上。本设计第 1～3 期做完，库内 build_all 产物只剩 4 个渲染物 + 混合体；第 4 期后快照也不再冲突。#568 解决的场景（只因 build_all 产物冲突）会基本消失。
- 但迁移分期、总共要几天到几周，期间每天都有 PR 被退回；前端生成物不在 #568 范围。**建议 #568 当过渡合入，不扩展；全部移出且一个月日志无 `AUTO-RESOLVE` 后删。**
- 反过来，第 1 期合入前，已在飞的 PR 里如果改过要移出的产物，会在合入 main 时出现「文件在 main 已不入库、PR 里还在改」的冲突：第 1、2、3 期各自合入前，由调度会话通知各会话先合 main（它们本来也要合 main 才能拿到新钩子）。

## 九、需要用户或 one-coordinator 拍板的问题

1. **归档分支的保护**：`archive/…` 要在 GitHub 设置里禁删禁强推；设置由用户做。
2. **`terms.yaml` 冷启动**：新工作区没有词表，要靠钩子/precheck 自动生成（几秒，需 PyYAML）。同意吗？还是保留 `terms.yaml` 入库（它 1.6 万行，仅占总量 1.3%，但读者最多、冲突也少，因为 `build_terms` 的输入改动并不频繁）？**我的建议：第三期暂缓，先做 1、2、4、5，看冲突是否已经消失，再决定是否值得动 terms.yaml。**
3. **`panorama.yaml` 的上游产物落后**（`ssot/**` 里一半生成器不在 `build_all`、入库版已落后、`scan_aggregates` 在 HEAD 失败）：把 panorama 链移出等于第一次按现状重算，缺口数会变，不是等价迁移。要不要先让 one-architecture 把上游生成器纳入 `build_all` 并修复，或者第二期改成只移 `GAPS.md`，`panorama.yaml` 等上游补齐后再动？
4. **`HANDOFF_*.md`（19 个）和 `evidence/`（115 个文件）的去向**：归档分支、`.gitignore` + `git rm --cached`，还是保留？`evidence/` 要先对照 ADR-0037。
5. **`NM8A`/`NM8B` 与 `B31P`/`OBDS`/`PAN`** 状态不明，要 one-architecture 确认能否归档。
6. **4 个本机渲染物**继续入库（建议），还是把 mermaid-cli 装进 CI 后移出。
7. **执行方式**：每期一个 PR，我自己写守护/CI 改动，机械改动（链接、`.gitignore`、`git rm --cached`）可交 Codex；是否同意这样分工与每期 opus 首轮复审。
