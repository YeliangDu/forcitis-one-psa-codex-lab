复审结论：通过
commit：b68faf789c4ee5a500fa39cb09c853fe9c061575
复审者：sonnet-首轮-1106

patch-id：a52b489d0938ec8c704e69a4e2d29fdd4b877b78

影响：仅 EmployeeSeparatedClosesAccessIntegrationTest 一个测试文件（platform.identity，无生产代码改动），不影响任何调用方，调用层数 0。

必改：无。

建议：
1. EmployeeSeparatedClosesAccessIntegrationTest.java 约 281 行（nothingBeforeTheEvent）仍是 `await ... identityCompletions(someoneElse)).isPositive()`，同样是"任一完成"语义。它之后断言的是 1006 号人员的状态，对 1007 的第二个监听器是否完成不敏感，所以不会误判；但慢的监听器可能漏到后面的用例。为一致可改用 `identityHandled(someoneElse)`（参数类型已是 EmployeeSeparated）。不阻塞。
2. 新方法 javadoc 里"the registry row is written at commit"略含糊。准确含义是：publish 事务提交时各监听器的发布记录已写入且 completion_date 为空，之后才逐个完成。这是下面第 3 项的前提，建议写明（与已有 incomplete() 的注释同一说法，不矛盾）。

已检查且无问题：
1. 提交与 PR 头：`git cat-file -e` 通过；`gh pr view 1106` 的 headRefOid 等于 b68faf789c4ee5a500fa39cb09c853fe9c061575。
2. 断言没有放宽或删除：diff 只有一处新增辅助方法，三处调用点。第一处和第三处把单一 `isPositive` 换成 `isPositive` 加 `identityIncomplete == 0`，更严；第二处（约 259 行）把内联的同样两个断言抽成 `identityHandled`，语义不变。未改验收用例之外的东西，没有绕过 hook 或 CI。
3. 修复逻辑成立：identityIncomplete 查 event_publication（未归档表）里 identity 监听器 completion_date 为空的行；publish 在事务内提交时发布记录已写入，所以 `completions > 0` 且 `incomplete == 0` 才表示全部 identity 监听器（应急管理员监听器与关闭授权/会话监听器共两个）都完成，修掉了"先完成者让 isPositive 提前通过"的竞态。identityCompletions 同时查归档表，identityIncomplete 只查未归档表：完成的行归档后不会被误算为未完成，方向正确。
4. 无防御式代码掩盖症状：没有吞异常、没有兜底默认值，使用同一个 DELIVERY 15 秒超时，没有新增 sleep。
5. 注释与代码一致：新 javadoc 描述（两个监听器、先完成不等于全部完成）与 `git grep` 看到的行为及用例一致；文件 365 行，远低于 1000 行。
6. 秘密/数据：无密钥、无真实数据，仅用测试夹具的编号。
7. 模块边界：只读 platform_integration.event_publication（既有做法，辅助方法原已存在），未新增跨模块访问。

破坏验证：
| 防御 | 如何破坏 | 结果 |
|---|---|---|
| identityHandled 等待全部 identity 监听器完成 | 把 `isZero` 一行去掉（即回到基线的 `isPositive` 单断言） | 不能确定性地变红：基线本身就是并发竞态偶发红（#1104、#932），时间窗口取决于两个监听器的完成顺序，无法稳定复现。这是并发测试修复固有的局限，不视为必改；未实际运行（需要集成环境），此项标记为 unverified。 |

核对方式：`git diff origin/main...b68faf789 | git patch-id --verbatim`；读取该提交下整个测试文件；`gh pr view 1106`。未运行集成测试（unverified）。
