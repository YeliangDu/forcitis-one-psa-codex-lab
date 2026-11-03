# F-1 #730 切片 S3：待调整权限的 HTTP 接口验收测试

独立测试代理所写；实现只让它们转绿、不改它们。认为测试有误时停下报告，由 lead 裁定后由测试代理改。依据：`docs/architecture/implementation-cards/f-1.md` 第 7.1、7.3 节与不变量表 13、14、19–24、27、28、31–33 行；接口契约取自旧验收分支 `origin/claude/one-1a-par-f1-acceptance` 的 `F1_ACCEPTANCE.md` 第 10–16 条（卡正文未重写接口），照 one-design 在 #730 的裁定去掉复职与同步触发的部分。

## 运行

```
backend/mvnw -B -f backend/pom.xml verify -Dit.test='PermissionAdjustmentReadApiIT,PermissionAdjustmentActionApiIT,OutboundDeliveryPermissionAdjustmentRowRulesIT'
```

条目由测试直接写入 `platform_identity.permission_adjustments`（卡的合法形状），不经监听器；生成条目属 S1、S2。

## 测试与完成标准的对应

| 完成标准（来源） | 测试 |
|---|---|
| 列表 `{items,nextCursor}`、条目字段、角色条目带 roleId/orgUnitId、两类读者都能读全部类型、条目与详情不含权限码与范围（契约 10、11、13；不变量 31） | `PermissionAdjustmentReadApiIT.bothReadersSeeEveryKindAndNoPermissionCodeOrScope` |
| 无权限码者（无角色、原部门负责人、当事人）列表与读取 403（契约 13） | `callersWithoutThePermissionAreRefused` |
| 列表不含本人条目（契约 14；不变量 33） | `aListLeavesOutTheCallersOwnItems` |
| 新部门负责人只见收件人为本人的条目，其余 403（契约 16 来源①；不变量 22） | `theNewHeadSeesOnlyTheItemsAddressedToThem` |
| 收件人为空由管理员读；多重角色相加（契约 16；待裁定 R-5） | `anItemWithoutARecipientIsReadByTheAdministratorAndTwoRolesAddUp` |
| 详情 ETag、404、400（契约 10） | `aDetailCarriesAVersionAndAbsentIdsAreRefused` |
| 分页走全、无重复、末页无游标（K-3a） | `pagingWalksEveryItemOnceAndEndsWithoutACursor` |
| 查看写 viewed 审计、被拒的读取不写（ADR-0032 第 5 项第 5 条） | `readingAnItemIsAuditedAsViewed` |
| 管理员维持敏感角色复核、库内值、授予不变（契约 12；不变量 27） | `PermissionAdjustmentActionApiIT.anAdministratorUpholdsASensitiveReview` |
| 有效至为下一自然季度首日（含四个边界）（契约 12a；不变量 23） | `theExpiryIsTheNextQuarterStartInShanghai` |
| 办理人不能定有效至 | `theHandlerCannotChooseTheExpiry` |
| 业务负责人可为办理人本人（契约 12） | `theBusinessOwnerMayBeTheHandler` |
| 十种无效请求 400 且条目不变；500 字边界（不变量 14、20、21） | `invalidUpholdRequestsAreRefusedAndChangeNothing`、`aReasonOfExactlyFiveHundredCharactersIsAccepted` |
| 缺 If-Match 428、过期 412（ADR-0035 第 8 项） | `upholdNeedsTheCurrentVersion` |
| 只有另一位管理员能维持敏感角色复核；本人条目 403 带指定错误码（契约 13、14；不变量 19、22） | `onlyAnotherAdministratorMayUpholdASensitiveReview` |
| 维持写 upheld 审计，文字含业务负责人 ID 不含姓名 | `upholdingIsAudited` |
| 已维持的条目不再转换（状态机） | `anUpheldItemTakesNoFurtherCommand` |
| 来源①由收件人凭原因维持，无有效至、无业务负责人，其余角色 403（契约 16） | `onlyTheRecipientUpholdsATransferScopeReview` |
| 收件人为空由管理员办；多重角色（契约 16；N1-09） | `anItemWithoutARecipientGoesToTheAdministratorAndTwoRolesAddUp` |
| 来源②由管理员办，安全审计员只读（契约 16；RULE-387） | `anInactiveUnitItemIsHandledByTheAdministratorOnly` |
| 标记已调整（审计条目）、安全审计员 403（契约 12c；不变量 28） | `anAdministratorMarksAnItemAdjusted` |
| 标记已调整必须恰好一个关联（不变量 28） | `markAdjustedNeedsExactlyOneReference` |
| 7.3：投递对象类别 PERMISSION_ADJUSTMENT、用途 PERMISSION_ADJUSTMENT_ASSIGNED；原有值不变；近似值被拒 | `platform/integration/OutboundDeliveryPermissionAdjustmentRowRulesIT`（3 条） |

## 红绿

红（main 上无实现）：`PermissionAdjustment{Read,Action}ApiIT` 23 条全红（列表、读取、命令都得 404；两条在造数阶段因列表 404 报错）。`OutboundDeliveryPermissionAdjustmentRowRulesIT` 3 条中接受新值那条红（`ck_outbound_deliveries_purpose_kind`），其余两条在基线上本来就绿。

绿：7.3 用临时迁移（加两个取值）3 条全绿，临时文件未提交。接口两个类没有实现可以跑绿，**未验证绿**；实现落地后由实现者核对。

## 未覆盖与存疑

- 路径、`:uphold`、`:mark-adjusted`、请求字段名是契约的「假定」（卡正文没有接口段），请 one-design/lead 确认或在实现 PR 里以 OpenAPI 为准通知我改。
- 标记已调整只用审计条目 ID 一条路径；用授权申请 ID 的路径需要真实授权申请，未写；不存在的审计条目或授权申请 ID 的结果卡没写，未断言。
- 读取本人条目时的状态码卡没写（只写列表不含），测试只断言不是 200。
- 办理人在请求里带有效至：卡只写「办理人不能填」，测试接受 400 或忽略两种实现。
- 已维持的条目再命令的状态码没写，测试只断言 4xx 且行不变。
- 超期升级、到期作业、重新生成属 S2；通用 fail-closed 读取守护属 S4；条目生成监听器属 S1。
- 升级与「无可办理的管理员」是否发卡未定（R-4），7.3 不断言。
- 新部门负责人是否可经条目看到当事人账号 ID（userId 字段）卡没写，未断言。
