# 共享 Spring 上下文的 mock 与精确次数断言（one-quality，2026-10-16）

起因：#341（one-1a-par）里 `OrgSnapshotSyncIntegrationTest.releaseReasonLosesControlCharacters` 连挂两次：它在共享上下文的 `AuditLog` mock 上写 `verify(audit).record(entry.capture())`（恰好一次、任意参数），而其他模块会异步响应先前的事件，也调用 `audit.record`，次数偶发偏大。调度派工：全仓库扫一遍这类写法，列清单，按 CS-5.6 给改法，评估能否做成守护（扩 G-C8）。

## 判据

`@MockitoBean` / `@MockBean`（及 spy 形式）声明的 mock 由同一个缓存的 Spring 上下文里的所有测试和所有异步监听器共享。**次数断言安全，当且仅当调用里写着只有本测试才产生的东西**：`argThat(...)` 挑出本测试的动作或 ID、`eq(...)`、具体值。**不安全**：`verify(m)` / `times(n)` / `only()` / `timeout(..)` / `never()` 且参数为空、`any*()`、`isNull()` 或 `captor.capture()`，另有 `verifyNoInteractions` / `verifyNoMoreInteractions`。

扫描脚本：`python3 scripts/ci/shared_mock_verify_scan.py`（只读、只报告，带自测；`--json` 可机读）。范围：`backend/src/test/java` 中跑在 Spring 上下文里（自身或祖先类带 `@SpringBootTest` 等）且 mock 字段由 `@MockitoBean` / `@MockBean` / `@MockitoSpyBean` / `@SpyBean` 声明（本类或祖先类）的类。已核对的安全写法：`TaskNotificationIT`、`ApprovalDeadlineIT`（全部 `argThat(card -> card.subject().equals(...))`）、`OrgDirectoryIntegrationTest`、`OwnLegalEntityAttributionForbiddenIntegrationTest`（按动作名 `argThat`）、`WecomLoginIntegrationTest:55-56`、`SsoLoginIntegrationTest:54-55`（具体参数值）。

## 清单（5 处，3 个类）

| # | 位置 | 写法 | 风险 | 改法（CS-5.6：捕获后按动作过滤；或用只挑本测试调用的参数） |
|---|---|---|---|---|
| 1 | `OrgSnapshotSyncIntegrationTest:281` | `verify(audit).record(entry.capture())`（恰好一次，任意参数） | **高**（已发生，#341） | `verify(audit, timeout(10_000).atLeastOnce()).record(entry.capture());` 然后只取本测试的那条：`AuditEntry released = entry.getAllValues().stream().filter(e -> e.action().equals("organization.workforce.sync-run.released") && e.actorId().equals(TestAccounts.account(9001L))).findFirst().orElseThrow();` 再断言 `released.detail()`。同类的第 326 行已是按动作 `argThat` 的 `never()`，不用改。 |
| 2 | `WecomLoginIntegrationTest:110` | `verify(orgDirectory, never()).findEmployee(any())` | 中 | 该用例的结果已由 401、无会话、无账号三条断言证明；`never(any())` 会被其他模块对 `OrgDirectory.findEmployee` 的异步调用误伤。改为只挑本测试的参数（本测试里员工不存在的那个 ID），或删掉这一行。 |
| 3 | `SsoLoginIntegrationTest:128` | `verify(orgDirectory, never()).findEmployeeByLogin(any())` | 中 | 改为 `never().findEmployeeByLogin(argThat(l -> l instanceof LoginLookup.ByLoginName n && n.value().equals(<本用例票据对应的登录名>)))`；与第 55 行用同一登录名常量。 |
| 4、5 | `SsoLoginIntegrationTest:151`、`:179` | `verify(ssoVerifier, never()).verify(anyString())` | 低 | `SsoTicketVerifier` 只有登录路径调用，异步方不用它，目前不会误伤；收紧为 `never().verify(<本用例的票据字符串>)`，与第 54 行的 `verify("T1")` 一致即可。 |

owner：#1 归 one-1a-par（#341 的文件，我不改他们的分支）；#2–#5 随各自模块的下一次改动，或由我另开一个只改这四行的小 PR（需调度确认，改的是 `identity` 的测试）。

## 能否做成守护（扩 G-C8）

**能。** `CODING_STANDARD.md` §8 的 G-C8 设计原文已写“`never()` 必须与具体方法和参数匹配器同用，`verifyNoInteractions` / `verifyNoMoreInteractions` 需登记理由”，只是尚未实现；上表 #2–#5 正是“`never()` 配 `any()`”，不满足“具体参数匹配器”。建议把 G-C8 的实现范围定为两条源码扫描（同一个测试类，`backend/src/test/java/…/TestWaitGuardTest`）：

1. 禁 `Thread.sleep`（原设计，现有 6 处入基线）；
2. **共享 mock 的精确次数断言必须带本测试专属的参数**（本文判据）；`verifyNoInteractions` / `verifyNoMoreInteractions` 在共享 mock 上需登记理由。

可行性依据：当前源码扫描 5 处全部是真问题或低风险，**没有误报**；判据是纯源码文本，不依赖运行时。基线：这 5 处入基线（`shared-mock-verify` 一类的基线文件，格式随 CS-1.11 的 `{owner; until}`），新增违例即失败，已修的条目要求同 PR 删除。已知局限（写进守护说明）：① 只认 `@MockitoBean` 等字段声明，`@TestConfiguration` 里 `return mock(...)` 的 bean 不识别（现仓库没有这种）；② `verify(m, atLeastOnce())` 加 `captor.getValue()` 取到的是**最后一次**调用，不是本测试的那次——判据放行但仍可能误取，评审模板 01 的 C6 已提示；③ 参数写成局部变量而不是 `argThat` 时按“具体值”放行，变量取自 `any()` 的极端写法无法识别。

下一步（需要调度确认再做）：守护改动单列，opus 复审；先把本脚本接进 precheck 做只报告，G-C8 实现 PR 排在 CS-1.11 之后。
