# F-1 第 1 块（#728）：验收测试与假定契约

独立测试代理所写；实现只让它们转绿、不改它们。认为测试有误时停下报告，由复审裁定后由测试代理改。依据：`docs/modernization/phase1a/cards/F-1.md` 第 7.1 节、不变量表第 1–8 行、「引用」一节，以及调度对本块的四条裁定（见下）。

## 运行

```
backend/mvnw -B -f backend/pom.xml verify -Dit.test='RoleScopeGrantIT,CustomOrgUnitSchemaIT,CustomOrgUnitGuardIT,CustomOrgUnitRulesIT,SystemRoleDeclarationScopeIT'
```

每个类起自己的数据库（Testcontainers）。

## 测试与卡的对应

| 测试类 | 对应卡 |
|---|---|
| `CustomOrgUnitSchemaIT`（7 条） | 7.1 两表的列、约束名、索引、外键、触发器、注释；不变量 1、2、3、8 的库内承担者 |
| `CustomOrgUnitGuardIT`（14 条） | 不变量 1（同版本同部门唯一）、2（只在开放的 CUSTOM_ORG_UNITS 版本上写明细）、3（行不可改不可删不可截断）、8（申请子行只在 PENDING 的 CREATE_ROLE / UPDATE_ROLE 且 CUSTOM_ORG_UNITS 的申请上写）；引用一节（部门 ID 无外键） |
| `CustomOrgUnitRulesIT` 前六条 | 不变量 4：定义不变不增版本；明细只随新版本、同一事务写入；新版本存完整明细；用例表「提交授权申请」 |
| `CustomOrgUnitRulesIT` 不变量 7 三条 | `CUSTOM_ORG_UNITS` 至少列一个部门，缺失 400 |
| `CustomOrgUnitRulesIT` 更新语义三条 | 调度裁定：null 沿用、空数组 400 |
| `CustomOrgUnitRulesIT` 引用两条 | 调度裁定：保存不校验部门是否存在 |
| `SystemRoleDeclarationScopeIT`（3 条） | 不变量 5：系统角色声明对账（启动失败）及两条对照 |
| `RoleScopeGrantIT`（迁自旧验收，5 条在跑、2 条停用） | 不变量 5、6 的应用校验与 F1-01；`changingTheCustomOrgUnitsMovesTheHoldersScope`、`aTwoPersonRoleIsNotRevisedToIncludeDescendants` 停用，均归 #729 |

## 调度裁定（照任务卡原话落成断言）

1. 范围是 `CUSTOM_ORG_UNITS` 却一个部门都没列：400，走请求校验，不用 422。
2. `customOrgUnits` 在更新时：传 null（不带该成员或显式 null）沿用原值；`CUSTOM_ORG_UNITS` 范围下传空数组按不变量 7 返回 400。
3. 保存时不校验部门是否真实存在，悬空由范围复核兜底。

## 假定契约（卡没有写死、测试照此断言）

- 请求与应答：`customOrgUnits: [{orgUnitId, includeDescendants}]`，同旧验收契约第 1 条；`PUT` 不带 `accessScope` 即沿用（现行行为）。
- 400 只断言状态码，不断言 `errorCode`。
- 触发器拒绝：`restrict_violation`（23001），由 `platform_identity.guard_custom_org_unit_rows()` 抛出（卡 7.1 原文）；唯一约束 23505，约束名同卡。
- 明细表读回用卡 7.1 的列名；`role_versions.access_scope_kind`、`effective_to_at` 取 ADR-0034-IMPL PR-2 建成的。
- 系统角色声明：`SystemRoleDeclaration` bean 是启动对账的缝（`SystemRoleReconciliation` 类注释）。`RoleDefinition` 须增加一个成员，装「部门 ID（UUID 或只包一个 UUID 的记录）与布尔『含下级』」构成的记录的集合；测试按形状找这个成员，不限定名字。找不到时测试失败并说明，不跳过。启动失败的具体异常类型与文字不断言。
- 调度裁定：`RoleScopeGrantIT.aTwoPersonRoleIsNotRevisedToIncludeDescendants` 已 @Disabled，归 #729：范围变化走授权申请（ADR-0032 第 1 项第 3 条，有持有人 409、无持有人放宽范围走申请）。该条断言「对已含敏感权限的角色再多列一个仅本部门返回 202」，主线 UpdateRole 现对此返回 403；断言原样不动，随 #729 转绿。
- 调度裁定 10-28：`RoleScopeGrantIT.changingTheCustomOrgUnitsLeavesHistory` 按 `subjectKind = ROLE` 且 `subjectId = 角色 ID` 过滤审计条目（应答里没有 `subjectKey`）；改前改后的部门写在审计条目的 `detail`（结构化 JSON，卡 C4），不用 `before_text`、`after_text`（二者按 DATA-REPAIR-timesheet-audit 必须为空）。`detail` 的键为 `before`、`after`，各是部门 ID 字符串数组（顺序不限，按集合比较）；键名卡没有写死，由本文约定，实现可在 `detail` 里另加其他键。
- 调度裁定 10-28：`GrantHistoryAppendOnlyIT.deleteAndTruncateAreRefusedOnBothTables` 中 `TRUNCATE role_versions` 只断言被拒、SQLSTATE 属于 {0A000, 23001}（role_versions 有子表外键后，截断先报 0A000）。其余 TRUNCATE 与 DELETE 断言不动。
- 授权申请确认时的重新校验（卡「提交与确认两处」）没有黑盒触发方式，本块不写用例，留给实现卡的集成测试。
