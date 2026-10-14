# 修复批 B2b 的断言改动

没有放宽或删除任何断言，也没有删除测试。下列既有测试被改动，原因都是被测代码的签名或事件载荷增加了字段，原有断言一字未动：

| 测试 | 改动 | 原因 |
|---|---|---|
| `SyncRunStateMachineTest` | `succeed(...)` 调用加快照日参数 | `SyncRun.succeed` 增加 `snapshotDate` |
| `EventSequenceContinuityTest` | `succeed`、`NumberingRule.restore`、`confirm`、`reconfigure` 调用加参数 | 同上，另有编号规则的版本与生效日 |
| `SessionLifecycleIntegrationTest` | 三处 `new OrgSnapshotChanged(...)` 加 `effectiveDate` | 事件载荷增加字段（该测试归身份模块，只改了构造调用） |
| `NumberingRuleStateMachineTest`、`NumberingPatternTest` | `restore`、`confirm`、`reconfigure` 调用加版本与生效日 | `NumberingRule` 增加 `ruleVersion`、`effectiveFrom` |
| `NumberingRulesIT` | `LegacyNumberEntry` 的法人包成 `Optional.of(...)`；`savingConfirmsTheRule` 的收尾复位多复位两列 | 法人可空（D-1a-152）；新增两列要复位到种子状态 |

新增的断言（不是改动）：`separation_date` 在离职同步后仍为空；`OrgSnapshotChanged` 的 `businessDate` 等于运行开始当天（Asia/Shanghai）。
