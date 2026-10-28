# 签章服务第一段契约（CS-1.10）

- 作者：one-platform-file，2026-10-22；回应 one-architecture 准入裁定 `ADMISSION_PLATFORM_SIGNING_2026-10-22.md`（claude/one-architecture-admission-file @a62cb635）第 4、5、7、9 条。
- 依据：ADR-0064 决定 1、2、3、9 与迁移方案第 2 条（先行上线批）；ADR-0066 决定 3–6；DATA_DESIGN_CHECKLIST §14.1 D15、D17。
- 模块：签章服务 `platform.signing`，schema `platform_signing`，权限码前缀 `platform.signing`（冒号后资源与动作按 ADR-0067 冻结，不在词表）；模块标识与台账类术语已由 one-glossary 登记（#564，proposed）。

## 1. 第一段范围（准入第 9 条）

| 建 | 不建（本批） |
|---|---|
| 印章类型清单（参考数据，D15 配置型版本化，行政提供、另一人确认） | 电子用印、服务商适配与回调入口（电子印章批，ADR-0064 迁移方案第 3 条） |
| 印章台账：台账条目（法人 × 印章类型）及其版本表（D17 维度型，排他约束） | 签章记录（随电子用印批；W0 用印登记由 `organization.administration` 自记，只引用台账条目） |
| 给 `organization.administration` 的台账查询 api（选条目、按用印时刻取当时有效版本） | 数字证书元数据、离职订阅（电子印章批） |
| 台账与类型清单的维护命令（模块内 web 端点，不计 api） | 执业印章条目、图纸签章登记（W2 交付物批） |
| 迁入：A8 用印按（归属公司，印章类型）映射台账条目所用的同一查询 | 逐枚台账（触发条件未成立，ADR-0064 决定 3 第 5 条） |

第一个使用方：`organization.administration`（B-31 用印 7.3、7.4）。范围须 one-1a-admin 另行确认（准入裁定 3.3）。

## 2. api（`platform.signing.api`，4 项，≤12）

```java
interface SealLedger {
    /** 按用印时刻（按业务时区切成日期）取当时有效的版本；条目不存在或时刻早于最早版本即抛错并告警（ADR-0064 决定 3 第 3 条）。 */
    SealLedgerEntryVersionView versionAt(SealLedgerEntryId entry, Instant at);

    /** 某法人在某日启用的条目，供用印申请选择；按印章类型排序。 */
    List<SealLedgerEntryView> activeEntries(LegalEntityId legalEntity, LocalDate on);

    /** 某法人某类型在某日的启用条目；排他约束保证至多一条。迁入映射 A8（归属公司，印章类型）用此方法。 */
    Optional<SealLedgerEntryView> activeEntry(LegalEntityId legalEntity, SealTypeCode sealType, LocalDate on);

    /** 某日有效的印章类型清单（已确认版本）。 */
    List<SealTypeView> sealTypes(LocalDate on);
}

record SealLedgerEntryId(UUID value)                    // UUIDv7，条目 ID 不随版本变
record SealLedgerEntryVersionId(UUID value)
record SealTypeCode(String value)                        // 构造即校验非空；是否登记由清单表校验
enum SealCarrier { PHYSICAL }                            // W0 只有实体；电子印章批追加 ELECTRONIC（冻结后兼容新增）
record SealLedgerEntryView(SealLedgerEntryId id, LegalEntityId legalEntity, SealTypeCode sealType, SealLedgerEntryVersionId currentVersion,
                           SealCarrier carrier, LocalDate effectiveFrom)
record SealLedgerEntryVersionView(SealLedgerEntryVersionId id, SealLedgerEntryId entry, LegalEntityId legalEntity, SealTypeCode sealType,
                                  SealCarrier carrier, boolean active, LocalDate effectiveFrom, LocalDate effectiveTo)
record SealTypeView(SealTypeCode code, String name)
```

### 2.1 不认识业务单据（准入第 2 条，请 opus 复审）

- 第一段 api 的参数只有台账条目 ID、法人（kernel `LegalEntityId`）、印章类型码、时刻或日期，**不出现任何业务单据类型、单据 ID 或用印申请**；签章服务不依赖、不回调 `organization.administration`。
- 用印登记引用台账条目版本 ID 由 administration 自存；W0 签章服务不记「谁引用了条目」。
- `DocumentReference` 从电子用印批的签章记录开始使用（ADR-0064 决定 7 第 1 条「请求方与申请 ID」写作 `DocumentReference`，不枚举单据类型）；第一段无此参数，不预留。
- 保管部门只在版本表存部门 ID（kernel 无 `OrgUnitId`，不建跨 schema 外键、不依赖 `organization.workforce` api），第一段 api 视图不暴露：administration 用印只需条目、版本、载体与启停。

### 2.2 事件

W0 不发布事件：维护命令的结果由维护人当场得知；administration 按用印时刻查版本，不需要订阅（CS-1.8）。

### 2.3 错误码（`SigningError`，中文名以 `backend/glossary/proposed/platform-signing.yaml` 为准，全局唯一）

| 码 | ErrorKind | 中文名 |
|---|---|---|
| `PLATFORM_SIGNING_LEDGER_ENTRY_NOT_FOUND` | NOT_FOUND | 印章台账条目不存在 |
| `PLATFORM_SIGNING_NO_VERSION_AT` | UNPROCESSABLE | 用印时刻无有效台账版本 |
| `PLATFORM_SIGNING_SEAL_TYPE_UNKNOWN` | UNPROCESSABLE | 印章类型未登记 |
| `PLATFORM_SIGNING_ACTIVE_ENTRY_EXISTS` | CONFLICT | 同一法人同一印章类型已有启用条目 |
| `PLATFORM_SIGNING_VERSION_NOT_EDITABLE` | CONFLICT | 台账版本只许关闭不许改写 |
| `PLATFORM_SIGNING_CONFIRMER_IS_AUTHOR` | UNPROCESSABLE | 印章类型确认人与作者相同 |

## 3. 模块内命令（web 端点，不计 api）

| 命令 | 权限码（暂名） | 要点 |
|---|---|---|
| 查看台账、类型清单 | `platform.signing:seal-ledger:read` | — |
| 新建台账条目（含首版本） | `platform.signing:seal-ledger:update` | 法人 × 类型唯一启用；首版本生效日可回溯到迁入范围起点 |
| 开新版本（改启停、保管部门） | 同上 | 关闭当前版本（失效日 = 新版本生效日），只追加；生效依据为业务声明日（DECLARED） |
| 提交印章类型清单新版本 | `platform.signing:seal-type:update` | D15：生效日、`decision_ref`、作者 |
| 确认印章类型清单版本 | `platform.signing:seal-type:confirm` | 确认人不得是作者 |

审计：上述写命令全部写 `platform.audit`。权限码与状态取值属 ADR-0067 冻结对象，随表结构与 one-data 定。

## 4. 策略项载体（准入第 4 条，ADR-0066 决定 5 第 1 条）

| 策略项 | 载体 | 说明 |
|---|---|---|
| 印章类型取值 | 版本化配置表（D15）：印章类型清单根表 + 版本表 | 行政提供（用户 B31-3），作者与确认人分离，`decision_ref` 必填；不写死枚举 |
| 电子印章就绪开关（某类改电子用印，A1b 两段切换） | 版本化配置表（D15）：印章类型清单版本表上的「用印方式」项 | 切换须制作备案、服务商接入、one-security 评审与该类验收（ADR-0064 决定 9），由确认人确认新版本生效；**本列随电子印章批建**，W0 不建（CS-1.8），W0 全部类型按实体章办理 |
| 某条目实际载体（实体 / 电子） | 台账条目版本表（D17）载体列 | 事实而非策略：切换后按台账开新版本改载体或停用；W0 取值只有实体 |

## 5. 模块依赖与守护落点（准入第 3、7 条）

`module-dependencies.txt` 增一行（随 api PR，守护文件单列、opus 复审）；第一段 api 不用任何模块，登记空行，audit（维护命令审计）与 identity（web 权限；ADR-0064 决定 1 第 3 条未列，待 one-architecture 认）随维护命令落地时再加：

```
# ADR-0064 decision 1 item 3: the signing service depends on foundation modules only; the first segment (seal ledger query api) uses none yet.
platform.signing ->
```

- 电子印章批再加 `platform.integration`、`platform.file`、`organization.workforce`（ADR-0064 决定 1 第 3 条），不预留。
- `organization.administration -> platform.signing` 由 one-1a-admin 随用印 7.3 登记。

守护落点（ADR-0064「需同步修改的守护规则」）：

| # | 守护 | 落点 | 本批 |
|---|---|---|---|
| 1 | 签章服务 ↛ `organization.administration` 与除 `organization.workforce` 外的业务 BC（含传递闭包）；`platform.approval` ↛ 签章服务 | `ModuleDependencyTest` 禁止边 | 是 |
| 2 | 服务商名称与协议只在 `platform.integration` | 现有外部连接隔离测试与 ACL 用语检查 | 自动覆盖新包 |
| 3 | schema 无私钥、口令、密钥类列 | `DdlNamingTest` 或新增 DDL 断言（按 `platform_signing` 列名） | 是 |
| 4 | 签章记录只追加 | 表触发器 + 注册表 `APPEND_ONLY` + DDL 测试 | 否（随签章记录） |
| 5 | 台账 D17 维度型、排他约束 | `data-access-registry.yaml` `DIMENSION_VERSIONED` + 排他约束 DDL 测试 | 是 |
| 6 | 新列访问级别与个人信息标记 | `data-classification.txt` | 是 |
| 7 | 回调端点不在公开入口白名单 | 外部访问白名单守护 | 否（随切换） |
| — | owner | `contexts.yaml` `owner: one-platform-file` | 已登记 |

## 6. 表（交 one-data 签发，代码前）

- `seal_types`（根，类型码唯一）+ `seal_type_versions`（D15：版本号、名称、生效日、作者、确认人、确认时间、`decision_ref`；确认人 ≠ 作者；每类至多一个待确认版本）。
- `seal_ledger_entries`（根：条目 ID、法人 ID、类型码；不可改）+ `seal_ledger_entry_versions`（D17：生效日 / 失效日左闭右开、载体、启停、保管部门 ID、声明依据、记录人；只追加、只许关闭；`EXCLUDE USING gist (legal_entity_id WITH =, seal_type_code WITH =, daterange(effective_from, effective_to) WITH &&) WHERE (is_active)`）。
- 迁入：A8 映射由迁入编排调用 `activeEntry`；首版本生效日回溯到迁入范围起点（ADR-0064 决定 3 第 3 条）。

## 7. 待交其他会话

- **one-glossary**：模块标识、schema、权限码前缀与台账类术语已登记（#564）。待删词条 `error__platform_signing_entry_inactive`（印章台账条目已停用）：停用由调用方按版本视图 `active` 判，签章服务不发此码；签章服务自己的命令需要时再登记。「印章类别」与印章类型同名异义，统一称印章类型。
- **one-1a-admin**：已确认（2026-10-22）够 B-31 用印 7.3、7.4：选条目用 `activeEntries` / `activeEntry`，登记按用印时刻 `versionAt` 取版本并存版本 ID，法人、类型、启停、载体在版本视图内自行校验，用途与类型对应用 `sealTypes`，迁入映射用 `activeEntry`；不需要保管部门、签章记录、事件。`versionAt` 只抛 `NO_VERSION_AT`；停用由调用方按视图 `active` 判，用 administration 自己的错误码，签章服务不发 `ENTRY_INACTIVE`（one-architecture 2026-10-22）。
- **one-data**：第 6 节四张表；`seal_type_code` 是否以清单版本外键或代码校验；W0 载体列只有一个取值是否先建。
- **one-architecture**：本稿齐后复核九条。
