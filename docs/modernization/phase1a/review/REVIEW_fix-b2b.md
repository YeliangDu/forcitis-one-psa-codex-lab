# 修复批 B2b 单独 opus 复审

条目编号以本文为准，外部引用加前缀 B2b-。按时间顺序收录首轮复审与各轮复核。

---

## 第 1 轮

**结论：修后整合。**

我在 `agent-a56d9e523992f0034` 跑了 `./mvnw -B verify`：BUILD SUCCESS，surefire 700 个通过、0 失败、19 跳过，failsafe 206 个通过、0 失败，与自述一致。另外几项检查也都通过：`build_asyncapi.py --check`、`build_all.py --check`、`check_legacy_name.py`，以及 gitleaks 8.30.1 扫这 4 个提交未发现泄漏。前端 `pnpm verify` 没有跑：该 worktree 没有 node_modules。B2b-H1 是按文件内容推断的，未实测。

## 问题

### High

**B2b-H1 前端生成代码没有重新生成，CI 前端那一项会失败**
- 证据：`frontend/packages/api/openapi.json:200` 已把 `legalEntityId` 从 required 里去掉，但 `frontend/packages/api/src/generated/schema.ts:628` 仍是必填的 `legalEntityId: string`，`validators.ts:67` 仍是 `z.string()`。`pnpm verify` 的第一步就是 `check:generated`，见 `frontend/package.json:16`。
- 第二个问题：后端没有全局开启 NON_NULL，所以没有法人时会输出 `"legalEntityId": null`（`NumberingRulesIT` 用 `doesNotExist()` 断言，对 null 也成立）。生成器把可空字段生成为 `.optional()`，而 Zod 的 `.optional()` 不接受 null，前端运行时校验会失败。所有带 `@Nullable` 的出参都有这个问题，不是 B2b 引入的，但这次新增的字段在生产里一定会遇到 null。
- 建议：运行 `pnpm generate:api` 并提交；整合时和 fix-c-frontend 协调，它也改了 `openapi.json`。可空字段输出 null 还是省略，要么 OpenAPI 声明可空，要么出参用 `@JsonInclude(NON_NULL)`，另开一项处理。

**B2b-H2 OrgSnapshotChanged 的日期与交易库版本行的生效日可能不一致，违反 §6.8**
- 证据：事件的日期取运行开始那天，`businessZone.dateOf(run.startedAt())`（`ApplySourceSnapshot.java:66`）。版本表 `org_unit_versions` / `assignment_versions` 的 `effective_from_date` 取的却是应用快照那一刻的日期：`now` 来自 `ApplySourceSnapshot.java:56`，再经 `JdbcOrgSnapshotRepository.java:129,165` 的 `zone.dateOf(now)` 写入。同步跨过上海午夜时，两者相差一天。
- 建议：在 ApplySourceSnapshot 里只算一次快照日，同时传给 `snapshots.save(...)`（改为接收 LocalDate）和 `run.succeed(...)`。补一个跨午夜的测试，断言事件日期等于版本行的 `effective_from_date`。

### Medium

**B2b-M1 EventBusinessDateGuardTest 能被绕过，说明文字也不准确**
- 只禁止依赖 `Clock` 和 `BusinessZone` 两个类。领域类直接调用 `LocalDate.now()` 或 `Instant.now()` 时，字节码里不出现 Clock，守护照样通过。
- 应用层照样把「今天」传进去，例如 B2b 自己的 `ConfigureNumberingRule.java:37`，以及已有的 `SubmitTimesheet.java:67`、approval 里的 `time.today()`。所以第 15 行写的 "never today's date" 不成立，这个守护只是把「今天」挪到了上一层。
- 注入用例用的包是 `com.forcitis.one..`，不是 `..domain..`，证明不了真实规则的包选择器有效。
- 建议：增加「domain 不得调用 java.time 各类的 `now()`」；注入样例放进 `..domain..` 包；改写说明文字。另外可以在 publisherTest 层加一条检查：目录里声明「无」的事件，businessDate 必须为空，反之必须不为空。

**B2b-M2 编号规则生效日（取舍 e）的推迟没有登记；原样保存也会让版本号加 1**
- D-1a-147 要求修改后的版本「重新确认后按生效日期启用」。现在生效日取保存当天，但 `DEFERRED.md` 和规则卡里都没有登记。fix-b1-identity 目前一个提交也没有，「等 B1」没有着落。
- `NumberingRule.java:408-416`：对已确认的规则原样再保存一次，也会让 `ruleVersion` 加 1 并重置生效日，而 D-1a-147 说的是「修改」才产生新版本。
- 建议：在 DEFERRED 登记这项推迟，写明原因、负责方和关联的 D-1a-147；模板和起始值都没变时，不加版本，或直接拒绝。

**B2b-M3 与 fix-b2-data 必然冲突**
- 两个分支都在 `WorkCalendarYear.java` 新增了同名的 `private static List<LocalDate> changedDates(...)`，B2 的 `maintain` 签名也变了（多了 today 和 lockedDaysConfirmed），并且仍按 2 个参数构造 `WorkCalendarYearMaintained`。
- `asyncapi.yaml`、`attendance.yaml`、`EventSequenceContinuityTest` 两边都改了。
- 建议：整合时只保留一个 helper，把 B2 新增的构造调用补上 `changedDates`，然后重跑全部守护。

**B2b-M4 告警语义没有写进 runbook**
- NaN 与现有 `SyncMetrics`、`DeliveryMetrics` 的约定一致，本身可以接受。但 `> 0` 这类告警规则遇到 NaN 不会触发，必须另配一条 `x != x` 或 absent 规则。
- `job.failures` 只统计没有被捕获、抛出来的异常。下面三处在内部吞掉了失败，计数器看不到：
  - `SyncOrgSnapshot.java:39`
  - `EnforceDeadlines.java:34`
  - `RefreshShortNames.java:34`：这一处连任何指标都没有。
- 建议：在 `INFRA_RUNBOOK.md` 的 E-13 / C-7 补上指标名、NaN 规则，以及「内部处理的失败由各模块自己的指标覆盖」，并列出对应的指标名。

**B2b-M5 新增用语没有登记提议术语（违反 CLAUDE.md 硬规则）**
- 只登记了 `separation_date`。以下都没有登记：
  - `rule_version_number` / `RuleVersion` / `ruleVersion`
  - `effective_from_date` / `effectiveFrom`
  - `effectiveDate`
  - `changedDates`
  - `snapshotDate` / 快照截止日
  - dead letter jobs、job failures 两个指标用语
- 同一个概念 OrgSnapshotChanged 用 `effectiveDate`，numbering 和 attribution 用 `effectiveFrom`，需要统一或在术语里说明区别。

### Nit
- **N1** 代码里内联写了全限定名，`REVIEW_b12f1df` 已指出过同类问题：`JdbcLegacyNumberRepository.java:480`（`java.sql.ResultSet`）、`JdbcNumberingRuleRepository.java:527`，以及 `SyncRunStateMachineTest`、`OrgSnapshotSyncIntegrationTest`、`OperationalMetricsIT` 几个测试。
- **N2** `V20261015020200` 第 2 行的 CHECK 没有命名，同一文件里另一条约束却用了 `ck_` 前缀，不一致。
- **N3** `NumberingRulesIT` 里的 `today()` 用的是 `LocalDate.now(Asia/Shanghai)`，午夜前后可能偶发失败。
- **N4** 失败计数的 advisor 和 ShedLock 的 advisor 都是 LOWEST_PRECEDENCE，先后顺序不确定，因此 ShedLock 自身抛出的 LockException 是否计数不确定。行为本身安全，建议显式设置 order。
- **N5** `backend/ASSERTION_CHANGES_B2B.md` 放在仓库根层；按 #53「记录类产物不入仓」，内容应放进报告和 PR 正文。
- **N6** `DeadLetterJobMetrics` 在引擎不可读时每次抓取都写一条 warn，会刷屏。
- **N7** `OperationalMetricsIT` 放在 platform.scheduling 包下，却断言 approval 的死信指标；建议把这条断言移到 approval 的测试里。
- **N8** 日志里出现一次 `Surefire is going to kill self fork JVM`，构建仍然成功；我没有核对这是不是之前就有的。

### 越界检查
没有改 platform.web、kernel、identity 的 main 代码，也没有改 approval 的领域代码。动到的地方都可以接受：
- 新增的 `approval/infrastructure/flowable/DeadLetterJobMetrics.java`，放在 infrastructure，不涉及领域代码。
- `SessionLifecycleIntegrationTest`（identity）和 `EventSequenceContinuityTest`（kernel）只是改了构造调用，并已在断言改动说明里列出。

所有既有断言都没有放宽。

## 对 (a)~(h) 的判断
- **(a) 同意。** `260000` 的小时位是 26，不是合法时间戳，`NamingSurfaceTest` 会拒绝。B2 用的是 `20261015010xxx` 段，与本分支不冲突，排序也在 main 之后。只是这个时间戳比提交时间（上海时间 00:38）还晚，「真实时间」说得有点宽，不影响合并。
- **(b) 同意。** 但见 H2：这个日期必须和版本行的生效日用同一个值。
- **(c) 同意。** LegalEntity 本身没有生效区间的模型；生效区间在归属事件上，那两个事件在 B2b 之前就已经带 `effectiveFrom/To` 和 businessDate（`LegalEntityAttribution.java:90,110`）。以后法人主数据如果加版本，再回头处理。
- **(d) 同意。** 这个事件 `formsFact=false`，按 §2 和 §6.2 可以为空。可以考虑把 `businessDate` 取为 `min(changedDates)`，方便以后从这一天起重算审批截止时间。整合冲突见 M3。
- **(e) 作为过渡可以接受。** 前提是补登记推迟，并处理原样保存加版本的问题（M2）。
- **(f) 同意。** 放在 approval/infrastructure/flowable 是对的；NaN 符合现有约定，但必须配套告警规则（M4）。
- **(g) 同意。** 切面捕获后原样抛出，不吞异常；ShedLock 抢不到锁时正常返回，不会被计数，也不改变 ShedLock 的行为。`OperationalMetricsIT` 证明生产上下文里的 `counterCapacityJob` 确实被包裹了。统计范围的说明见 M4，顺序问题见 N4。
- **(h) 同意。** 我核对了 `backend/architecture/seeyon-view-columns.yaml`，`v_org_member` 确实没有离职日期列。但 T5 的用户决定原话是「A8 视图能提供时加；不能提供则报 psa-coordinator」，先建一个暂时为空的列，与这句话略有出入。B2b 必须在报告里向 psa-coordinator 单列说明，由协调者确认。

## 整合前必须修
1. **H1**：重新生成 `frontend/packages/api/src/generated/*` 并提交，与 fix-c-frontend 协调 `openapi.json`，确认前端 `pnpm verify` 通过。
2. **H2**：快照日只算一次，同时用于事件和版本行，并补跨午夜测试。
3. **M1**：补上 `now()` 这条绕过路径，注入样例放进 `..domain..` 包，改正说明文字。
4. **M2**：在 DEFERRED 登记生效日的推迟；原样保存不再加版本。
5. **M5**：新增用语登记提议术语，并统一 `effectiveDate` 与 `effectiveFrom`。
6. **M3**：整合时合并两边的 `changedDates`，同步最新 main 后重跑全部守护。
7. **(h)**：在给 psa-coordinator 的报告里单列 separation_date 的情况。

M4（runbook 和告警规则）应在 C-7 演练前完成，不阻塞这次整合。

---

## 第 2 轮

**结论：可整合。** 我要求整合前修的 H1、H2、M1、M2、M4、M5 都已修好，各项 Nit 也处理了，没有引入阻塞整合的新问题。剩下的都是跟进项，不挡这次整合；但 M1 的覆盖面缺口建议在下一批里补上。

## 我实际跑过的验证
- **全量 verify**（`cd backend && ./mvnw -B verify`）：BUILD SUCCESS，surefire 708 个通过、0 失败、19 跳过，failsafe 206 个通过、0 失败。`Surefire is going to kill self fork JVM` 这条日志又出现了一次，构建仍然成功。
- **其他检查**：`build_asyncapi.py --check`、`build_all.py --check`、`check_legacy_name.py` 都通过；gitleaks 8.30.1 扫 992c3ae..HEAD 未发现泄漏；前端 `generate-api.mjs --check` 报告与 openapi.json 一致（47 个接口），`pnpm typecheck` 通过。前端完整的 `pnpm verify` 没有跑。
- **H2 测试在旧代码上确实会失败**：我从 HEAD 建了临时分支 `tmp-review-h2-revert`，放在 scratchpad 下单独的 worktree 里，只撤回 26fdf7a 的 main 代码、测试保持 HEAD 版本，然后只跑 `OrgSnapshotSyncIntegrationTest#snapshotDayIsTheSameForTheEventAndTheHistoryRowsAcrossMidnight`。结果失败：`expected: 2026-10-16 but was: 2026-10-15`。跑完已删除临时 worktree 和分支，B2b 的 worktree 是干净的。

## 逐项结果
| 项 | 结果 | 说明 |
|---|---|---|
| H1 | 已修 | 前端生成文件重新生成，漂移检查和类型检查都通过。顺带补上了基线里本来就漏掉的法人归属接口，所以 diff 有 240 多行，整合时要和 fix-c-frontend 协调生成文件。 |
| H2 | 已修 | 快照日只算一次（`ApplySourceSnapshot.java`），同时写入版本行和事件；跨午夜测试在旧代码上失败、新代码上通过。 |
| M1 | 部分已修，有覆盖面缺口（见下文） | 新增「领域包不得调用 java.time 的 now()」；注入样例放进了 `fixture.domain` 包，并有反向用例证明不是样例本身触发；说明文字已改准确；新增目录声明与信封 businessDate 的一致性检查。 |
| M2 | 已修 | 原样保存不再加版本、不动生效日，待确认的规则原样保存仍算确认；有单元测试和 IT。DEFERRED 已登记生效日的推迟，理由合理。 |
| M4 | 已修 | runbook 写明了 E-13 的指标名、NaN 的 `x != x` / absent 规则、各模块内部吞掉失败时用的指标；部门简称刷新补了失败计数和 IT；C-7 已引用。runbook 里列的指标名我逐个在代码里找到了。 |
| M5 | 已修 | 7 个提议术语已登记，`effectiveDate` 统一改为 `effectiveFrom`。 |
| T5 撤回 | 已确认 | 迁移文件、列和术语都已移除，DEFERRED 改成「A8 提供后再建列」。 |
| N1 | 已修，有一处残留 | main 代码已改为 import；H2 新测试里还有 `java.time.LocalDate.now(shanghai)`。 |
| N2 | 已修 | CHECK 已命名为 `ck_numbering_rules_rule_version_number`。迁移还没进 main，改它不违反只增不改的规则。 |
| N3 | 已修 | `NumberingRulesIT` 改用固定时钟。 |
| N4 | 已修 | 失败计数 advisor 设为 HIGHEST_PRECEDENCE，IT 断言它排在第一位。 |
| N5 | 已修 | 断言改动说明文件已移出仓库。 |
| N6 | 已修 | 死信指标读不到时的警告每 5 分钟最多一条。 |
| N7 | 已修 | 死信指标的断言移到新的 `DeadLetterJobMetricsIT`（approval 测试包）。 |

## M1 的覆盖面缺口
- **一致性检查只覆盖了部分事件。** 它只接在三个测试用的信封工厂上：`TestEnvelopes.INSTANCE`、`TestEventEnvelopes`、`WorkCalendarYearTest.ENVELOPES`。
  - 审批的 7 个事件（ApprovalCompleted、Task*、ApprovalTaskOverdue/Escalated）和工时的 5 个事件，单元测试里没有走这些工厂：例如 `TimesheetStateMachineTest:172` 是手工拼信封的。这 12 个事件里有好几个正好声明的是「X 当日」，最该被检查。
  - 集成测试用的是生产的 `ContextualEventEnvelopes`；`TestEventEnvelopes` 带 `@ConditionalOnMissingBean`，实际上很可能不生效，所以集成测试路径整体没有被检查。
  - 也没有一条断言要求「目录里每个事件至少被检查过一次」，漏掉的事件不会报错。
  - 建议：在测试里给 `EventEnvelopes` 套一层包装（BeanPostProcessor），把审批和工时测试拼信封的地方也接进检查；再加一条「已检查的事件集合 = 目录事件集合 − 显式豁免」的断言。
- **目录里没登记的事件会被直接跳过**（`problem` 返回 null）。登记由 `EventCataloguePolicy` 兜底，可以接受，但说明里应写明。

## 新发现（不是 B2b 引入，也不挡这次整合）
- **NEW-1 结束日为空的归属区间会被前端拒收。** `LegalEntityAttributionResponse.java:8` 的 `Period.effectiveTo` 可以为空（表示至今），但没有标 `@Nullable`，重新生成后得到 `effectiveTo: z.string()`（必填）。仍在生效的归属区间会让前端运行时校验失败。这段代码来自 28e7e15（B-1），建议另开一项处理。
- **NEW-2 「后端输出 null、Zod 用 `.optional()`」的系统性问题还在。** `legalEntityId` 可空时后端输出的是 null，`.optional()` 不接受 null。这是第一轮我说的「另开一项」，仍未处理。
- **Nit**：
  - 新增的 `rule_version` 和 `rule_version_number` 是同一个概念登记了两条。`rule_version` 这个名字太泛，以后可能和过账负载里的 `ruleVersion`（EVENT_ANALYTICS §3）冲突。建议合并成一条、另一条作别名，或改成编号规则专用的名字。
  - 新的 DeadLetterJobMetricsIT 多起了一个 Spring 上下文和一个容器，测试套件会变慢一些。

## 仍需修的清单（跟进项，不阻塞本次整合）
1. M1 覆盖面缺口：审批和工时的事件、集成测试路径，以及「每个目录事件至少被检查一次」的断言。
2. NEW-1：`Period.effectiveTo` 标 `@Nullable`（归 B-1 范围）。
3. NEW-2：统一后端 null 与前端 `.optional()` 的约定。
4. 整合时：和 fix-b2-data 合并 `WorkCalendarYear.changedDates`，和 fix-c-frontend 协调生成文件；同步最新 main 后重跑全部守护。
5. Nit：去掉 H2 测试里残留的全限定名；合并 `rule_version` 和 `rule_version_number` 两条术语。
