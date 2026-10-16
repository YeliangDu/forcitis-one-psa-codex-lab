# 共享 Spring 上下文的 mock 与精确次数断言（one-quality，2026-10-16）

起因：#341（one-1a-par）里 `OrgSnapshotSyncIntegrationTest.releaseReasonLosesControlCharacters` 连挂两次：它在共享上下文的 `AuditLog` mock 上写 `verify(audit).record(entry.capture())`（恰好一次、任意参数），而其他模块会异步响应先前的事件，也调用 `audit.record`，次数偶发偏大。调度派工：全仓库扫一遍这类写法，列清单，按 CS-5.6 给改法，评估能否做成守护（扩 G-C8）。

## 判据

`@MockitoBean` / `@MockBean`（及 spy 形式）声明的 mock 由同一个缓存的 Spring 上下文里的所有测试和所有异步监听器共享。**次数断言安全，当且仅当调用里写着只有本测试才产生的东西**：`argThat(...)` 挑出本测试的动作或 ID、`eq(...)`、具体值。**不安全**：`verify(m)` / `times(n)` / `only()` / `timeout(..)` / `never()` 且参数为空、`any*()`、`isNull()` 或 `captor.capture()`，另有 `verifyNoInteractions` / `verifyNoMoreInteractions`。

扫描脚本：`python3 scripts/ci/shared_mock_verify_scan.py`（只读、只报告，带自测；`--json` 可机读）。范围：`backend/src/test/java` 中跑在 Spring 上下文里（自身或祖先类带 `@SpringBootTest` 等）且 mock 字段由 `@MockitoBean` / `@MockBean` / `@MockitoSpyBean` / `@SpyBean` 声明（本类或祖先类）的类。已核对的安全写法：`TaskNotificationIT`、`ApprovalDeadlineIT`（全部 `argThat(card -> card.subject().equals(...))`）、`OrgDirectoryIntegrationTest`、`OwnLegalEntityAttributionForbiddenIntegrationTest`（按动作名 `argThat`）、`WecomLoginIntegrationTest:55-56`、`SsoLoginIntegrationTest:54-55`（具体参数值）。

## 清单（5 处，3 个类）

| # | 位置 | 写法 | 风险 | 改法（CS-5.6：捕获后按动作过滤；或用只挑本测试调用的参数） |
|---|---|---|---|---|
| 1 | `OrgSnapshotSyncIntegrationTest:281` | `verify(audit).record(entry.capture())`（恰好一次，任意参数） | **高**（已发生，#341） | 审计在 `release.execute` 里同步写入（`RecordSyncRunRelease:38`），不需要 `timeout`：`verify(audit, atLeastOnce()).record(entry.capture());` 然后只取本测试的那条：`AuditEntry released = entry.getAllValues().stream().filter(e -> e.action().equals("organization.workforce.sync-run.released") && e.subject().equals(<本用例 `held.syncRunId()` 对应的审计对象>)).findFirst().orElseThrow();` 再断言 `released.detail()`。**按操作人 `TestAccounts.account(9001L)` 过滤不够专属**（同一类第 293 行的另一个用例也用它），用 subject（运行 ID）挑。同类的第 326 行已是按动作 `argThat` 的 `never()`，不用改。 |
| 2 | `WecomLoginIntegrationTest:110` | `verify(orgDirectory, never()).findEmployee(any())` | 中（风险是真的：`HandleEmployeeRegistered:25` 的异步监听器会调用 `OrgDirectory.findEmployee`） | 该用例的结果已由 401、无会话、无账号三条断言证明，且这个员工不存在、没有 ID 可挑，**删掉这一行**。 |
| 3 | `SsoLoginIntegrationTest:128` | `verify(orgDirectory, never()).findEmployeeByLogin(any())` | **低**（`findEmployeeByLogin` 只被 `WecomIdentification`、`LoginWithSso` 同步调用，没有异步调用方） | 该用例的票据是 `SsoVerdict.Invalid`，没有登录名可挑；保持不改，或删掉这一行（结果已由 401 与“票据未被消耗”两条断言证明）。 |
| 4、5 | `SsoLoginIntegrationTest:151`、`:179` | `verify(ssoVerifier, never()).verify(anyString())` | 低 | `SsoTicketVerifier` 只有登录路径调用，异步方不用它，目前不会误伤；可收紧为 `never().verify(<本用例的票据字符串>)`，与第 54 行的 `verify("T1")` 一致。 |

owner：#1 归 one-1a-par（#341 的文件，我不改他们的分支）；#2–#5 随各自模块的下一次改动，或由我另开一个只改这四行的小 PR（需调度确认，改的是 `identity` 的测试）。

## 能否做成守护（扩 G-C8）

**能。** `CODING_STANDARD.md` §8 的 G-C8 设计原文已写“`never()` 必须与具体方法和参数匹配器同用，`verifyNoInteractions` / `verifyNoMoreInteractions` 需登记理由”，只是尚未实现；上表 #2–#5 正是“`never()` 配 `any()`”，不满足“具体参数匹配器”。建议把 G-C8 的实现范围定为两条源码扫描（同一个测试类，`backend/src/test/java/…/TestWaitGuardTest`）：

1. 禁 `Thread.sleep`（原设计，写作时仓库有 7 处，以实现时的实际数入基线；`CODING_STANDARD.md` 写的是 6 处）；
2. **共享 mock 的精确次数断言必须带本测试专属的参数**（本文判据）；`verifyNoInteractions` / `verifyNoMoreInteractions` 在共享 mock 上需登记理由。

可行性依据：当前源码扫描的 5 处都对应真实的不安全写法（风险从高到低各有，见上表），判据是纯源码文本，不依赖运行时。基线：这 5 处入基线（`shared-mock-verify` 一类的基线文件，格式随 CS-1.11 的 `{owner; until}`），新增违例即失败，已修的条目要求同 PR 删除。已知局限（写进守护说明；以下写法仓库里目前**没有实例**，所以 5 处这个数是对的，但守护实现时要么覆盖要么写明）：① 只认 `@MockitoBean` 等字段声明，`@TestConfiguration` 里 `return mock(...)` 的 bean 不识别（现仓库没有这种）；② `verify(m, atLeastOnce())` 加 `captor.getValue()` 取到的是**最后一次**调用，不是本测试的那次——判据放行但仍可能误取，评审模板 01 的 C6 已提示；③ 参数写成局部变量而不是 `argThat` 时按“具体值”放行，变量取自 `any()` 的极端写法无法识别；④ 不识别：BDD `then(m).should(...)`、经本地变量或 `this.audit` 引用 mock、`@SpringJUnitConfig`、父类写全限定名 `extends com.x.Base`、字段泛型带逗号 `Map<String, X>`；⑤ 按简单类名找父类，仓库里有 3 个同名类（`MutableClock`、`Samples`、`SharedName`）可能找错。

下一步（需要调度确认再做）：守护改动单列，opus 复审；先把本脚本接进 precheck 做只报告，G-C8 实现 PR 排在 CS-1.11 之后。
