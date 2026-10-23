# 24 小时复盘四项派工：方案（one-quality，2026-10-16）

来源：one-coordinator 派工（用户「派」）。每项先出方案，批准后分 PR 实施；改守护 / 脚本 / 项目配置的部分单列，复审按现行 TEAM-67（代码首轮 opus，一般改后作者交 Codex 复核，安全及生产改动修后 opus 终审）。数据取自 origin/main 的合并历史与 CI 日志，命令见各节。

## ① 推送前钩子：按改动跑相关守护测试，不过就拒绝推送

**依据**：一个分支连挂 3 次 `SeeyonSourceScanTest`（错题 G-05）。该类是源码扫描测试，`precheck.sh` 的守护清单里没有它，所以本地从没跑到。

**关键数据**：不起容器、不起 Spring 的 `*Test` 类共 149 个，在上次 main 的 CI 日志里累计 81 秒（并行下更短）；起容器 / Spring 的 108 个类累计 1274 秒。所以**不需要再写“按改动选哪些测试”的映射**（漏一个就是下一个 G-05），推送前直接跑全部免容器单元类，约 1.5–2 分钟（含编译）。

**方案**
1. `scripts/precheck.sh --pre-push`：只跑快的确定性检查——改了什么就跑对应的联动与自测（已有）、`build_all --check`、**全部免容器单元类**；不跑 gitleaks 全历史、不起容器、不跑前端全量（前端依赖没装且改了前端 / 词表时仍判 FAIL，沿用已有规则）。推送的提交没动 `backend/`、`scripts/`、`.github/`、`frontend/` 时只跑文档检查，秒过。
2. 免容器类名单由脚本从源码生成，不手写：类名以 `Test` 结尾、源码不含 `@SpringBootTest`、`Testcontainers`、`TestPostgres`、`TestMssql`、`*IntegrationSupport`、`AcceptanceSupport`；用 `test-compile surefire:test -Dtest=<名单>` 直调（避开绑在 process-test-classes 的 OpenAPI 导出，见错题 QUAL-005）。名单为空或某类没产生报告即失败。
3. 入口：PreToolUse(Bash) 钩子匹配 `git push`（含 `git -C … push`），复用 `precheck_before_pr.py` 的框架：没以 ready 收尾就退出码 2 拒绝；出错一律拒绝。可选补充：`.pre-commit-config.yaml` 加 pre-push 阶段，覆盖人手推送。
4. **绕过与留痕**：`git push --no-verify` 不触发 git 钩子，所以留痕只能放在 Claude 层：命令含 `--no-verify` 时钩子不拒绝，但要求同一命令里带 `PUSH_BYPASS_REASON="<理由>"`（缺则拒绝并提示），并往 `.claude/push-bypass.log`（不入仓）追加一行（时间、分支、理由）；PR 模板加一栏「绕过推送前钩子」，写了就要在 PR 里单列。
5. 成本与风险：每次推送多 2–3 分钟，本机 CPU 紧时多个会话同时推送会同时编译（在提示里写明）；钩子崩溃按拒绝；`.claude/settings.json` 新钩子是对所有会话生效的持久配置，需要用户点头（走待决定收件箱，同上一个钩子）。
6. 验收：构造一个让 `SeeyonSourceScanTest` 失败的改动 → `git push` 被拒；带理由的 `--no-verify` 放行并留痕、不带理由被拒；纯文档推送秒过；`env -i LC_ALL=C` 下端到端各跑一次。

## ② 汇总文件片段化盘点：24 小时内合并队列 14 次 CONFLICT

**方法**：最近 30 小时 399 个合并提交，对每个合并取两个父提交各自相对合并基点改动的文件求交集（两边都改过 = 冲突候选），按次数排序（排除已是片段的 intent / session-log）。

| 次数 | 文件 | 性质 |
|---|---|---|
| 27 | `backend/glossary/proposed/README.md` | **生成产物，却入仓** |
| 24 | `docs/modernization/SESSION_PLAYBOOK.md` | 手写规范，多会话往列表里追加条目 |
| 12 | `phase1a/infra/INFRA_RUNBOOK.md` | 手写，表格与章节混合 |
| 11 | `phase1a/infra/LEGACY_READ_REGISTER.md` | 追加式表（#298 已改片段） |
| 9 | `phase1a/NEXT_BATCH_PLAN.md` | 调度独占的计划表 |
| 9 | `docs/modernization/adr/README.md` | **生成产物，入仓** |
| 9 / 6 / 5 / 3 | `backend/glossary/proposed/<模块>.yaml`（platform-integration、platform-numbering、data-migration、…） | 多会话各往同一模块文件追加词条 |
| 7 | `COORDINATOR_LEDGER.md` | 调度独占，仍自冲突（多分支改同一文件） |
| 6 | `phase1a/infra/PREDEPLOY_CHECKLIST.md` | 追加式表（已批准片段化） |
| 5 / 5 / 5 | `GLOSSARY_CONTEXT.md`、`CODING_STANDARD.md`、`NAMING_STANDARD.md` | 手写规范，追加条目 |
| 4 | `glossary/terms.yaml` | **生成产物，入仓**（前后端读取，必须入仓） |
| 4 | `RESOURCES.md` | 追加式表（已批准片段化） |

**建议（按收益排序）**
1. **生成产物不入仓 / 冲突时重新生成**（约占冲突的三成）：`proposed/README.md`、`adr/README.md` 与 INTENT.md 同类，改为不入仓、由 `build_all.py` 生成并发布到 generated-docs 分支，`--check` 只要求“能生成、片段合规”；`terms.yaml`（运行时被读）和 `adr/ADR-*.md`（每文件独立，不冲突）保留入仓，但提供 `scripts/regenerate_conflicts.sh`：合并冲突时取任一边再跑 `build_all.py`，不手工解。
2. **已批准的五个**：INFRA_RUNBOOK 的 §3 E 表与 §5 变量表、PREDEPLOY_CHECKLIST、DEV_DEPLOY_REMAINING、DEFERRED、RESOURCES（需先和 one-ops 约时机，门禁 `deploy_gate.py` 改读片段目录，失败关闭）。
3. **提议术语按词条一个片段**（`proposed/<模块>/<词条>.yaml`，现有 `build_proposed_index.py` 合并）：影响 one-glossary 的校验器、前端 `check:copy`、后端 `GlossaryTest`，**先问 one-glossary 再定**。
4. **SESSION_PLAYBOOK 与两份规范的“追加型条目”**（附 M5 写代码规则、TEAM-58–66 战队协作、附 M3）：建议抽成 `playbook/rules/<日期>-<slug>.md` 片段，主文件由生成器按序拼出；这是规范正本，改动大，**需调度 + 用户定**，先不动。
5. **COORDINATOR_LEDGER**：调度独占却自冲突 7 次，说明它被多个调度分支同时改；建议每条台账一个片段，或调度只在一个分支上改。
6. 不处理：会话画像“当前状态”（每会话独占自己的文件，24 小时内无冲突）、测试基础件重构（一次性）。

## ③ 基线 / 例外登记统一生命周期：编码规范 CS 新条款草案

**现状**（`backend/architecture` 下 29 个文件，加 `glossary/code-term-baseline.txt`、`glossary/term-rules-baseline.json`）：

| 写法 | 例 | “到期”在哪 |
|---|---|---|
| `键 \| 负责卡 \| 期限` 三列 | `list-paging-gaps.txt`、`test-container-gaps.txt` | 第三列，**只有文字**（如“R-1b P5 之后”），不校验 |
| `键  # 理由` | `repository-write-exceptions.txt`（21）、`unversioned-table-writes.txt`（27）、`versioned-table-unconditional-writes.txt`（12）、`primary-key-exemptions.txt`、`status-write-exceptions.txt` … | 无；有的在注释里写“随 X 删除” |
| `键 \| 说明` | `ddl-naming-baseline.txt`（69）、`source-type-exceptions.txt`（9）、`legacy-shape-exceptions.txt` | 注释里写，不校验 |
| YAML 带 `due / owner / ref` | `data-access-registry.yaml` 的 `gaps` | **唯一校验**：过期即失败 |
| JSON `entries / rules` | `term-rules-baseline.json` | 无（“已修好未删”会失败） |
| `G41 模块 说明` | `code-term-baseline.txt` | 注释里写“#296 合并后删除”，不校验（#318 两处过期在 CI 才暴露） |

另有一类**永久登记簿**，不是例外：`data-classification.txt`、`test-images.txt`、`archive-columns.txt`、`archive-variables.txt`、`public-endpoints.txt`、`system-actors.txt`、`module-dependencies.txt`、`ticket-issuers-allowlist.txt`、`retired-module-schemas.txt`、`data-access-registry.yaml` 的 `tables`。只需理由与负责，不需要到期。

**CS 新条款草案（拟放 CODING_STANDARD §1，编号待定 CS-1.11）**

> **基线与例外有生命周期。** 凡为“已知违规暂不修”而登记的行（基线、例外、gap），每条必须带三样：**理由**（一句，已有）、**负责**（会话名或卡号）、**到期**。到期写成 `until=YYYY-MM-DD`，可以在前面加更早的触发条件：`until=pr:#318,2026-11-30`（该 PR 合并即到期）、`until=card:K-3b-2,2026-11-30`；日期兜底必写。写法统一为行尾标记 `{owner=<>; until=<>}`（YAML / JSON 用同名字段）。
> 守护统一校验：缺字段失败；日期早于今天失败（过期）；`pr:#N` 已合并而条目仍在，失败（修好的条目必须在同一 PR 删除）；条目对应的违规已不存在，失败（沿用各守护现有的“过期行”检查）。新增条目仍需 CODEOWNERS 评审。
> 永久登记簿（见清单）不适用到期，但必须有理由与负责。

**实施方案**：`scripts/ci/baseline_lifecycle.py`（一个脚本读所有文件，按上表的写法解析；清单 `baseline_lifecycle_files.txt` 区分“例外”与“永久登记簿”，新文件不在清单即失败）。分两阶段：先**只报告**两周（缺字段、临近到期、已过期各列出来，生成一份“待补字段”工作表交各负责会话填；约 160 条），再切为**失败**。`pr:#N` 的合并状态在 CI 里用 GITHUB_TOKEN 查，离线的 precheck 里只提示不判。

**需要定的**：条款编号与放置；阶段切换日期；永久登记簿清单是否对；`code-term-baseline.txt` 与 `term-rules-baseline.json` 归 one-glossary，需和它对格式。

## ④ ApprovalAccountsIT / TimesheetApprovalScenarioIT 各一次失败

sonnet 诊断（只读，读 CI 日志与源码），证据状态逐条标注；**不是实跑复现**。

**TimesheetApprovalScenarioIT（运行 38805349816，main，112abd60）——偶发，等待方式有竞态（机理为读代码推断，未实测）**
- 失败：`cancelledTimesheetIsADraft`（`TimesheetApprovalScenarioIT.java:364`）调 `cancelTimesheet.execute` 得 `DomainException: The timesheet is not in a running approval`（`Retractions.require`，即 `Retraction.cancel` 里 `findForUpdate` 查不到审批请求）。同类 18 个测试仅此一个失败；下一提交起连续成功（已核实）。
- 根因：`submitAndAwaitApproval`（`:151-157`）只用 Modulith `Scenario.andWaitForEventOfType(TaskAssigned)` 等事件。审批请求由 `StartApprovalOnSubmission` 的 `@ApplicationModuleListener`（异步）在自己的事务里创建，`TaskAssigned` 在该事务内发布；事件到达时审批请求可能还没提交，测试线程随即取消就查不到。没有 sleep、不依赖时钟，**违反 CS-5.6 的是等待条件不是“审批已可见”**。
- 建议：在 `:154-155` 的等待之后，用 Awaitility 条件等待，在独立事务里查 `requests.find(COST_TIMESHEET_APPROVAL, document)` 直到存在，统一放进 `submitAndAwaitApproval`，其他提交后取消 / 撤回 / 驳回的用例同受益。

**ApprovalAccountsIT（运行 38791490769，main，ec37a8be）——无法判定，倾向偶发**
- 失败：`theInitiatorNeverApprovesTheirOwnTimesheet`（`ApprovalAccountsIT.java:132`，经 `candidatesSeenBy:121`）`expected: 200 but was: 401`，响应 `PLATFORM_WEB_UNAUTHENTICATED`（请求带了 `SESSION` cookie 却被当成未登录）；同类另两个用例通过，全后端 541 个测试只此一败；同分支其后约 8 次运行该测试都通过（已核实）。
- 等待方式：没有 sleep，没有靠时间的断言（已抽查），不是 CS-5.6 问题。
- 可疑点：`SessionGuardFilter` 在会话主体被判非活跃、或主体不是账号 ID 时会 `invalidate` 会话而返回 401；成功的运行说明平时不触发，但日志里找不到触发原因，也没有 ALERT 可佐证。
- 建议：先不改产品代码；在该测试的断言处加诊断（失败时打出 `principals.resolve` 结果与会话状态），若再现再定。

**处理方式**：`TimesheetApprovalScenarioIT`（`cost/timesheet/application/`，不在验收测试目录）改的是等待方式、不放宽断言，我自己做小 PR。`ApprovalAccountsIT` 在验收测试目录（`acceptance/platform/approval/`），哪怕只加诊断，提交也要带 `Acceptance-Test-Author: test-engineer` 或裁定编号，**请调度安排测试代理或给裁定**；在那之前只登记，不改。两项都登记进 `QUALITY_CONTEXT.md` 的不稳定测试台账。
