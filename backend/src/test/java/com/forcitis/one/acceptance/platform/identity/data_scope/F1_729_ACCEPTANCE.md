# F-1 第 2 块（#729）：验收测试

独立测试代理所写；实现只让它们转绿、不改它们。认为测试有误时停下报告，由 lead 裁定后由测试代理改。依据：`docs/architecture/implementation-cards/f-1.md` 第 7.1 节 `permission_adjustments`、第 7.2 节、不变量表第 9–36 行中由库承担的各行，以及 one-design 的块边界（#729 评论，lead 已记录）。

## 运行

```
backend/mvnw -B -f backend/pom.xml verify -Dit.test='PermissionAdjustment*IT,PermissionAdjustmentSubjectKindIT'
```

不起 Spring；每个类一个迁移好的库，每条用例一个不提交的事务，探测语句在保存点里执行后回滚。

## 块边界（one-design 拆分，lead 记录于 #729）

- #729 只验收库：7.1 表结构原样、库内承担的不变量（直接 SQL 写入证明）、7.2 审计对象类别。
- 聚合类与 `UpholdExpiry` 属 #729 的实现，由实现者的单元测试覆盖，不写验收。
- 接口（列表、详情、`:uphold`、`:mark-adjusted`、权限码）、不变量 19 的 403、20–24、27、33，生成条目的监听器与作业、7.3、通用的数据范围拒绝守护，以及 `RoleScopeGrantIT` 两条停用用例，都归 #730。

## 测试与卡的对应

| 测试类 | 对应卡 |
|---|---|
| `PermissionAdjustmentSchemaIT`（5 条） | 7.1 列、默认值（只有 `version`、`event_count`）、约束名、外键与 RESTRICT、索引与部分索引条件、注释；不变量 31（没有权限码与范围列） |
| `PermissionAdjustmentShapeIT`（11 条） | 取值集合；不变量 9、10、11、12、35；原条目；去重 15、16、17、32 |
| `audit/PermissionAdjustmentSubjectKindIT`（1 条） | 7.2 审计对象类别登记（读 audit 的表，守护要求放在 audit 模块的验收目录） |
| `PermissionAdjustmentLifecycleIT`（11 条） | 每个状态的合法行；维持三列；不变量 13、14、19（库内 CHECK）、23 与 30（库内部分）、25、26（库内部分）、28、29、36 |

## 假定（卡没有写死、测试照此断言）

- 拒绝按约束名断言（`getConstraint()`）；一行同时违反多条 CHECK 时 PostgreSQL 按约束名字母序检查，用例尽量只破一条。未知 `origin_kind`（行上不带触发列）只会破 `kind_origin` 与 `origin_kind` 两条，断言是其中之一。
- 7.2：卡写「`ck_audit_entries_subject_kind` 加取值」，现行基线已按 ADR-0069 第 14 条把类别改为登记表 `platform_audit.audit_subject_kinds`；测试断言登记行（`platform.identity`、有对象、`ACTIVE`），不断言 CHECK。
- 维持原因的库内检查是 `char_length(btrim(uphold_reason)) BETWEEN 1 AND 500`，`btrim` 只去空格；只含制表符等其他空白的原因库里拦不住，「非空白」由 #730 的接口 400 承担。库内用例只断言全空格与空串被拒。
- 行内时刻全是字面量，不用库时钟。
