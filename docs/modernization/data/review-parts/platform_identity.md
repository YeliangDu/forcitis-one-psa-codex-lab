# 数据设计审查：platform_identity（13 张表）

- 审查：one-data 子代理，2026-10-16；基线 `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql`（366 行，下称「迁移」）。
- 标尺：门禁清单。本分支的 `DATA_DESIGN_GATE.md` 是旧版（无 B0、B3a、O15、附录 C），实际按 `claude/one-data-gate-v2` 分支的版本（`git show` 只读）判定。
- 整表不适用并视同通过：A1–A8（无分析事实）、O11（无大表，保留期均短）、O13（不进过账、不受法人 × 期间闸门）、A6 / D7（无业务单据）、E2 / E3（无读模型、无迁入映射）、T 系列（基线开放期，随库重建）。
- E1、B3a 全部通过：事件目录 `events/identity.yaml` 载荷只含 ID 与 `cause` 枚举，无 PI、无 L3/L4 列；`AccountStatus`、`RoleStatus`、`EmergencyAdminStatus`、`AuthorizationRequestStatus`、`AuthorizationRequestKind`、`RoleKind`、`AccessScopeKind`、`WecomEntry` 的取值与迁移 `CHECK` 一致；每个目标状态都有对应事件（AccountEnabled / Disabled、RoleEnabled / Disabled、AuthorizationConfirmed / Rejected）。ADR-0034 要求的 VOIDED 状态及事件尚未建（见 AQ-2）。
- 命名约定：下文「迁移:N」为迁移文件行号。

## 逐表结论

| 表 | 结论 | 阻断 / 高 / 中 / 低 |
|---|---|---|
| user_accounts | 需修改 | 0 / 0 / 3 / 0 |
| roles | 需修改 | 0 / 1 / 3 / 2 |
| role_permissions | 需修改 | 0 / 1 / 0 / 1 |
| account_roles | 需修改 | 0 / 1 / 0 / 0 |
| navigation_items | 需修改 | 0 / 1 / 3 / 0 |
| login_states | 需修改 | 0 / 0 / 1 / 1 |
| sso_ticket_uses | 需修改 | 0 / 0 / 0 / 1 |
| ticket_issuers | 需修改 | 0 / 0 / 1 / 2 |
| emergency_admins | 需修改 | 0 / 0 / 2 / 1 |
| sessions | 需修改 | 0 / 1 / 0 / 3 |
| sessions_attributes | 需修改 | 0 / 0 / 0 / 2 |
| browser_identifiers | 需修改 | 0 / 0 / 2 / 2 |
| authorization_requests | 需修改 | 0 / 3 / 4 / 1 |
| 跨表共性（另计） | — | 0 / 0 / 3 / 4 |

合计：阻断 0、高 8、中 22（表内 19 + 跨表 3）、低 20（表内 16 + 跨表 4）。

## 问题清单

### user_accounts

#### UA-1 [中] 含 PI 列的表永久保留，且未登记带到期日的 gap
- 条目：C3、C4；ADR-0043 第 6 项 R-2、U-12。
- 证据：注册表 `data-access-registry.yaml:227-233` 为 `retention: {policy: PERMANENT}`，表内有 `last_login_address`（迁移:6，`data-classification.txt:8` 登记 SENSITIVE，ADR-0043 第 3 项判 PI）；注册表无任何 `gaps:` 条目（文件头 `:15` 只有格式说明）。U-12 用户答复：该列「改为有期限」，期限提出前「以带到期日的 gap 登记」。`JdbcAccountRepository.java` 的 `recordLogin` 只覆盖写入，没有任何清除路径；迁移无清理列。
- 建议：注册表加带 owner 与到期日的 gap（规则 C3 / R-2）；表结构侧待期限定后加清除办法（例如账号停用或离职后置空该列，列注释写明「为空表示」的含义）。无结构差异可先做。
- 依据：ADR-0043 第 6 项 R-2、U-12；§14.1 D12、D13。

#### UA-2 [中] 最近登录时间与地址应同空同非空
- 条目：O4 / B5。证据：迁移:5-6、20-21；`recordLogin` 同一语句写两列（`JdbcAccountRepository.java` `recordLogin`），RULE-330「成功登录同时盖 IP 与时间」。
- 建议：加 `ck_user_accounts_last_login`（两列同空同非空）。依据：§14.1 D2。

#### UA-3 [中] `last_login_address` 无长度上限与格式约束
- 条目：O8。证据：迁移:6 `text`，无 `char_length` CHECK（IPv6 文本最长 45）。建议：加长度上限 CHECK。依据：NAMING_STANDARD §2.4a-5。

### roles

#### R-1 [高] 角色定义未按已采纳的 ADR-0034 版本化，系统角色仍由迁移写入
- 条目：O15、O6、O7、B3a；ADR-0034 第 1 项第 1、2、7 条（状态「已采纳」，`adr/src/ADR-0034.md:3`）。
- 证据：
  - 迁移:27-39 的 `roles` 仍含 `name`、`status`、`access_scope`；ADR-0034 要求只留 `role_id / code / kind / is_administrative / version / event_count`，其余移入 `role_versions`（`role_version_id`、`version_number`、`effective_from_at / effective_to_at`、`role_name`、`role_status`、`access_scope_kind`、`permission_codes text[]`、变更来源与关闭列，排他约束 `role_id` 与 `lower(role_name)` 各一）；迁移无 `role_versions`。
  - 迁移:305-366 用 `INSERT` 写入六个系统角色与权限，ADR-0034 第 1 项第 7 条要求「迁移不再写角色权限，DDL 迁移只建表」，系统角色改由代码声明启动对账。
  - `JdbcRoleRepository.java` 的 `update` 原地改名称、状态、范围，`delete` 物理删除角色行：改后的定义与已删角色的定义都无法还原（ADR-0034 背景「在库中无法还原」）。
  - `primary-key-type-baseline.txt:7-8` 把两张关联表登记为「随 ADR-0034」，`COORDINATOR_LEDGER.md:47` 与 `phase1a/cards/R-1b.md:17` 写明「ADR-0034 实现留 W2、dev 前」；即已排期，但基线现状仍违反已采纳 ADR。
- 建议（表结构差异）：`roles` 去掉 `name`、`status`、`access_scope`，加 `BEFORE UPDATE` 触发器拒改 `code / kind / is_administrative`；新增 `role_versions`（含上述全部约束、`permission_codes` 规范化 CHECK、开放版本唯一、只许关闭的触发器）；迁移不再 `INSERT` 角色与权限。
- 依据：ADR-0034 第 1 项；§14.1 D17（授权关系须时刻区间）；门禁 O15 漏一条即不通过。
- 备注：排期是否构成豁免由 one-coordinator 与用户定，见「待用户决定」第 1 项。

#### R-2 [中] 「系统角色冻结」「super_admin 保留码」只靠 Java，库内无约束
- 条目：O4、B5。证据：迁移:44 注释写「super_admin 为预留码，只能由迁移脚本建立」，库内无对应 CHECK；`Role.java` 的 `isReserved`、`requireModifiable` 是唯一承担者；RULE-345、`aggregates.yaml` 的 Role 不变量列了 RULE-345。
- 建议：加 `ck_roles_reserved_code`（`lower(code) <> 'super_admin' OR kind = 'SYSTEM'`）；系统角色冻结见 R-1 的触发器（ADR-0034 第 1 项第 1 条）。依据：§14.1 D2；ADR-0034。

#### R-3 [中] 角色编码、名称无格式与长度约束
- 条目：O8。证据：迁移:29-30；`Role.java` 的 `CODE` 正则 `[A-Za-z0-9][A-Za-z0-9._-]{0,63}` 与 `requireName`（去空白后 ≤100）。
- 建议：加 `ck_roles_code`（格式）与名称长度 CHECK（版本化后落在 `role_versions.role_name`）。依据：NAMING_STANDARD §2.4a-5。

#### R-4 [中] `access_scope` 可取 `CUSTOM_ORG_UNITS`，但明细无处存放
- 条目：B7 / B4 / CS-1.8；待核。证据：迁移:33 的 CHECK 含 `CUSTOM_ORG_UNITS`，迁移:48 注释「1a 只存类型，范围明细随 1b 的 data_scope_grants」；全库与 ADR 中没有 `data_scope_grants` 定义（只在 `PendingIdentityRulesTest.java:29` 的 `@Disabled` 说明里）；`CreateRole.java:70-71` 允许创建角色时直接选该范围；`AccessScopeCalculator.java:57` 取 `role.customOrgUnits()`，其来源待核。
- 建议：二选一（见待决第 5 项）：1a 的 CHECK 暂不含该取值，随 1b 的明细表一起加；或同时建明细表。依据：CS-1.8、CS-1.9。

#### R-5 [低] 业务值作数据库默认值
- 条目：O8（§2.4a-3：数据库只允许技术默认值）。证据：迁移:31-33 `kind DEFAULT 'CUSTOM'`、`status DEFAULT 'ENABLED'`、`access_scope DEFAULT 'SELF'`（RULE-345 的最小权限是业务决定）；`JdbcRoleRepository.insert` 显式写了三列，默认值实际不起作用，却掩盖漏写。
- 建议：去掉这三个 DEFAULT。依据：NAMING_STANDARD §2.4a-3。

#### R-6 [低] `is_administrative = true` 应只属于 SYSTEM 角色
- 条目：O4。证据：迁移:34；`Role.newCustom` 恒为 false，库内无 `CHECK (NOT is_administrative OR kind = 'SYSTEM')`。依据：§14.1 D2。

### role_permissions

#### RP-1 [高] 复合主键，且 ADR-0034 已取代该表
- 条目：O2、O15、O7；ADR-0033 第 1 项第 2 条、第 8 条，ADR-0034 第 1 项第 3 条。
- 证据：迁移:55-61 `PRIMARY KEY (role_id, permission_code)`；ADR-0033 第 1 项第 2 条写明「R-1b 合并时若两张旧表仍在，同样按本条改，不登记豁免」，第 8 条「豁免只剩框架自建表」；`primary-key-type-baseline.txt:8` 仍登记该表；`JdbcRoleRepository.java` 的 `savePermissions` 用 `delete … not in` 原地改写，权限变更历史丢失。
- 建议：删表，权限集合进 `role_versions.permission_codes text[]`（ADR-0034 第 1 项第 3 条）；基线清单删对应行。依据：同上。

#### RP-2 [低] `permission_code` 无格式约束
- 条目：O8。证据：迁移:57，注释写明格式 `{子域}.{BC}:{资源}:{动作}`，无 CHECK。随 RP-1 改为数组后，按 ADR-0034 第 1 项第 3 条的规范化 CHECK 与目录校验处理。

### account_roles

#### AR-1 [高] 复合主键并物理删除授予，授权历史丢失
- 条目：O2、O7、O15、O6；ADR-0033 第 1 项第 2 条，ADR-0034 第 2、3 项，ADR-0026 第 8 项第 7 条。
- 证据：迁移:69-76 `PRIMARY KEY (user_id, role_id)`；`JdbcAccountRepository.java` 的 `saveRoles` 用 `delete from account_roles … not in`；离职（`recordSeparation` → `replaceRoles(Set.of())`）整组删除；「某人在某时刻持有哪些角色」无法还原（ADR-0034 背景）。ADR-0034 要求 `account_role_grants`：UUIDv7 主键、`user_id`、`role_id`、`effective_from_at / effective_to_at`、开启列（`granted_by`、`grant_channel_kind`、`grant_authorization_request_id`、`migration_batch_id`、`grant_audit_entry_id`）、关闭列（`revoked_by`、`revocation_cause_kind`、`revocation_authorization_request_id`、`revocation_sync_run_id`、`revocation_audit_entry_id`）、排他约束、成对 CHECK、只许关闭的触发器、三个部分 / 时刻索引。迁移均无。
- 建议：删 `account_roles`，建 `account_role_grants`（按 ADR-0034 第 2 项）；ADR-0026 第 8 项第 7 条的 `migration_batch_id` 随之落在授予行。
- 依据：ADR-0034 第 2 项；§14.1 D17 细则 2（授权关系按时刻区间）。

### navigation_items

#### N-1 [高] 配置表没有业务唯一约束
- 条目：O3、B5；RULE-347「同一上级下名称唯一」。
- 证据：迁移:84-95 除主键外无任何 `UNIQUE`；注册表 role 为 `CONFIG`（`data-access-registry.yaml:255-260`）。
- 建议：业务上「同一上级导航项下名称唯一」：`UNIQUE NULLS NOT DISTINCT (parent_item_id, name)`（顶层 `parent_item_id` 为空，普通唯一约束对空值不起作用）；有路由的页面再加 `route_path` 唯一（部分索引 `WHERE route_path IS NOT NULL`）。注册表登记 `business_key`（DG-0 建成前写在卡里）。
- 依据：NAMING_STANDARD §2.4a-1；§14.1 D2；RULE-347。

#### N-2 [中] 外键列 `parent_item_id` 无以它开头的索引
- 条目：O10。证据：迁移:86 外键，全文无 `ix_navigation_items_*`。建议：加 `ix_navigation_items_parent_item_id`（或由 N-1 的 `(parent_item_id, name)` 唯一索引承担，须在注册表写明是同一个索引）。依据：NAMING_STANDARD §2.4a-2。

#### N-3 [中] 目录 / 页面 / 树完整性只靠应用与种子
- 条目：O4、B5；RULE-347。证据：迁移:94 只挡自引用 `parent_item_id <> item_id`；注释迁移:96「目录（无路由）与页面（有路由）」无约束：页面可以挂在页面下，环路无约束，目录可带 `permission_code`。RULE-347 原文「上级必须是目录或菜单」对应新系统的语义待核（新系统以 `route_path` 是否为空区分，未见新规则卡）。
- 建议：能落约束的落（如 `route_path` 为空的项不得有……由规则确认后定）；环路无法落成约束的，在卡里写明「只能在应用里保证」的理由，并对种子数据加守护测试。依据：§14.1 D2。

#### N-4 [中] `route_path`、`permission_code`、`name` 无格式与长度约束
- 条目：O8。证据：迁移:87-89。依据：NAMING_STANDARD §2.4a-5。

### login_states

#### LS-1 [中] `binding_hash` 未分级，同表的 `state_hash` 已登记 SENSITIVE
- 条目：C4。证据：迁移:114、124 注释（发起浏览器所持绑定值的 SHA-256，用于挡住「攻击者带自己的 state 诱导他人登录」）；`data-classification.txt:12` 只登记了 `state_hash`；ADR-0043 第 3 项表也无此列。未登记即默认 INTERNAL + NONE。
- 建议：登记 `binding_hash`（访问级别由用户定，见待决第 6 项；标记 NONE，理由以「用途：」之外的安全材料说明，同 `state_hash`）。依据：ADR-0043 第 2、3 项。

#### LS-2 [低] 清理路径 `expires_at` 无索引，与 `sso_ticket_uses` 做法不一致
- 条目：O10。证据：`JdbcOneTimeCredentials.java` 的 `purgeExpired`（`delete … where expires_at <= :now`，每 10 分钟，`CredentialPurgeJob.java:19`）；迁移:107-116 无 `expires_at` 索引，迁移:136 给 `sso_ticket_uses` 建了。行数很小，故为低；建议统一做法或在注册表写明不需要的理由。

### sso_ticket_uses

#### SU-1 [低] `ticket_source` 是「SSO」或签发方编码，无约束
- 条目：O8、O4。证据：迁移:139 注释；签名票据时值为 `ticket_issuers.issuer_code`，库内无 CHECK 或外键。基线清单已有改名 `ticket_source_code`（`ddl-naming-baseline.txt:18`）；建议改名时一并加格式 CHECK。保留期口径见「未核实」。

### ticket_issuers

#### TI-1 [中] `allowed_role_codes` 以业务键数组引用角色，无引用完整性
- 条目：O2、O9、B4。证据：迁移:151 `text[]`；`JdbcTicketIssuerRepository.java` 的 `findByKid` 读成角色编码集合；`LoginWithSignedTicket.java:103` 以 `roleCodes` 比对。门禁 O2「引用一律 UUID，不以业务键作外键」；数组元素既不校验存在也不校验格式。ADR-0039 第 31 行写明该列归属「随 C1、待研究」，本 ADR 不定。
- 建议：改为关联表 `ticket_issuer_roles`（`ticket_issuer_id`、`role_id`，联合唯一）或至少 `role_id uuid[]`；形态待 ADR-0039 的 C1 研究后定（待决第 7 项）。依据：门禁 O2、O9；ADR-0033 第 1 项。

#### TI-2 [低] 「不得含 super_admin」的 CHECK 区分大小写
- 条目：O4。证据：迁移:155 `'super_admin' = ANY(...)`；`Role.isReserved` 用 `equalsIgnoreCase`（`Role.java`），角色编码唯一性按 `lower(code)`（迁移:40）。登录时另有 `AccessProfile.isAdministrator` 兜底（`LoginWithSignedTicket.java:98-101`），故为低。建议：CHECK 改比较规范化值，或在注册表登记「真正的承担者是登录闸门」。

#### TI-3 [低] `issuer_code`、`signing_key_code`、`key_reference` 无格式约束
- 条目：O8。证据：迁移:147-149；`key_reference` 注释写明形如 `env:NAME`。依据：NAMING_STANDARD §2.4a-5。

### emergency_admins

#### EA-1 [中] 登录名格式与计数列取值范围只在应用里校验
- 条目：O4、O8；RULE-317。证据：`RegisterEmergencyAdmin.java:55-57` 的 `LOGIN_NAME` 正则（3 至 32 位，小写字母开头，限小写字母、数字、点、短横线、下划线）；迁移:169 `account_name` 仅 `UNIQUE`；迁移:172-173 `last_totp_step`、`failed_attempts` 无非负 CHECK。
- 建议：加 `ck_emergency_admins_account_name`（格式）、`failed_attempts >= 0`、`last_totp_step >= 0`。依据：§14.1 D2；NAMING_STANDARD §2.4a-5。

#### EA-2 [中] 含 PI 的永久保留表，注册表没有可检查的理由与决定出处
- 条目：C3；ADR-0043 第 6 项 R-2。证据：注册表 `:285-292` 为 `PERMANENT`；`account_name` 在 ADR-0043 第 3 项判 PI；U-12 已答「认可永久保留并登记理由」，但注册表既无理由字段也无用户决定出处（字段名「由实施 PR 定」）。
- 建议：字段建成前写在卡里；字段建成后补理由与出处（U-12、intent `2026-10-15-225255`）。依据：ADR-0043 R-2。

#### EA-3 [低] 「只能在应用里保证」的不变量需在卡里写理由
- 条目：B5、O4。证据：「绑定人持有 super_admin」「登记至少两个」（迁移:181 注释，ADR-0027 第 1 项）跨表或跨行，落不成约束；承担者为 `EmergencyBinding.java`、`EmergencyAdminCheckJob`。建议卡上列入「应用承担」清单并各写一句理由。

### sessions

#### S-1 [高] `session_id` 是 Cookie 值，未分级
- 条目：C4。证据：迁移:197、212 注释「Cookie 值」；`data-classification.txt` 只登记 `principal_name`（`:17`）与 `sessions_attributes.attribute_bytes`；`session_id`、`primary_id` 未登记，默认 INTERNAL。读库者可凭此冒用会话。
- 建议：登记 `session_id`（访问级别待决，见待决第 4 项）；`primary_id` 同判。依据：ADR-0043 第 3 项（会话内容 RESTRICTED）；NAMING_STANDARD §2.5。

#### S-2 [低] 表注释缺粒度
- 条目：C1。证据：迁移:210 只写「服务端会话……列名由框架固定」。建议补「粒度：每个登录会话一行」。

#### S-3 [低] 过期会话查询无法用索引
- 条目：O10。证据：`JdbcExpiredSessions.java` 的 `find`：`where expiry_time < :now or creation_time + :absolute < :now`，第二个分支是表达式，`ix_sessions_expiry`（迁移:208）不能单独支撑，行数 = 在线会话数，有 `limit`。待 EXPLAIN 核实，故为低。

#### S-4 [低] `principal_name` 可空，注释无「为空表示」
- 条目：O8。证据：迁移:202、217。

### sessions_attributes

#### SA-1 [低] 表注释缺粒度
- 条目：C1。证据：迁移:230。建议补「粒度：每个会话的每个属性一行」。

#### SA-2 [低] 注册表条目没有 `writers`
- 条目：C2。证据：注册表 `:308-312` 有 `role` 与 `lineage` 无 `writers`（同 schema 的 `sessions` 有 `writers: [platform.identity]`）。写入方是框架，建议在条目里写明框架经 identity 的数据源写入。

### browser_identifiers

#### BI-1 [中] 注册表称 INSERT_DELETE，代码却更新行并改写其他账号行的 `identifier_hash`
- 条目：O7、O5、C5。证据：注册表 `:273-278` 为 `ONE_TIME` + `INSERT_DELETE`；`JdbcBrowserIdentifiers.java` 的 `replace` 对旧值 `update … set retired_at`，并对「同一旧值、其他账号」`update … set identifier_hash = :new`（把别的账号的键值换掉）；迁移无 `version`。登记的例外在 `unversioned-table-writes.txt:23`。
- 建议：注册表 `mutability` 改为 `CONDITIONAL_UPDATE` 并写出前置条件（`retired_at IS NULL`），或把「换新」改成「关闭旧行 + 插入新行」、不改写键值列。是否保留「其他账号同浏览器一并换值」的语义请安全复核（见需 opus 复核第 4 项）。依据：§14.1 D4、D8；§14 第 4 项。

#### BI-2 [中] 保留天数未在 GUIDING_PRINCIPLES §6 登记
- 条目：C3（不通过，待用户决定）。证据：注册表 `:273-278` `EXPIRY`，作业 `platform.identity.credential.purge`；`BrowserIdentifierSettings.java` 的 `lifetime` 为配置项；GUIDING_PRINCIPLES §6 表（`:117`）只列 `login_states`、`sso_ticket_uses`、`sessions*`。`identifier_hash` 按 ADR-0043 为 PI，保留期必须有期限且经用户认可。登记待决收件箱。

#### BI-3 [低] 清理谓词跨两列且无索引
- 条目：O10。证据：`JdbcBrowserIdentifiers.java` 的 `purge`：`last_login_at < :limit or retired_at < :limit`；迁移:250 只有 `user_id` 索引。行数随账号数线性增长，量小，故为低。

#### BI-4 [低] `ON DELETE CASCADE` 不属同一聚合
- 条目：O7、D3。证据：迁移:247；`aggregates.yaml` 中 UserAccount 聚合的表只有 `user_accounts`、`account_roles`，不含本表；规则「CASCADE 仅限同聚合子行并已登记」。账号实际不会物理删除，建议改 `RESTRICT`。

### authorization_requests

#### AQ-1 [高] `ck_authorization_requests_two_people` 对「已确认但无确认人」不设防
- 条目：O4、B3、B5；I-12、ADR-0030 第 16 行。
- 证据：迁移:282 `decided_by IS NULL OR (…) OR request_status = 'REJECTED'`：`request_status = 'CONFIRMED'` 且 `decided_by IS NULL` 时整条 CHECK 为真，四眼约束被绕过；状态与 `decided_by`、`decided_at` 之间无成对约束（迁移:274-276）。应用侧 `AuthorizationRequest.confirm` 同时设置两列，但库内可写出非法组合。ADR-0030 称该 CHECK 为不可放宽的守护。
- 建议：加成对 CHECK：`PENDING` ⇒ `decided_by`、`decided_at` 均空；`CONFIRMED`、`REJECTED` ⇒ 均非空；然后两人约束可去掉 `decided_by IS NULL OR`。「确认人不得持有被修改的角色」跨表，写入「应用承担」清单（`AuthorizationRequest.java` 的 `confirm`）。
- 依据：§14.1 D2；ADR-0026 第 6 项第 2 条；ADR-0030；门禁 B3（终态与原因成对）。

#### AQ-2 [高] ADR-0034 要求的状态、列与事件缺失
- 条目：O15、B3a；ADR-0034 第 2 项第 5 条。
- 证据：ADR-0034 要求新增状态 VOIDED（CONFIRMED → VOIDED）、`base_account_version_number`、相关角色版本 ID 数组、审计 `platform.identity.authorization.voided`、`authorization_request_id` 被角色版本引用；迁移:263-274 的 CHECK 仅 `PENDING / CONFIRMED / REJECTED`；`AuthorizationRequest.MACHINE` 只有两条转换；事件目录无 `AuthorizationVoided`。
- 建议：随 R-1 一并建（状态 CHECK 加 VOIDED、两个基准列、成对 CHECK、事件登记）。依据：ADR-0034；B3a ①②④。

#### AQ-3 [中] 申请类型与各列的取值配对未约束；`is_enabled` 为空会被静默读成 false
- 条目：O4、B5、O8。证据：迁移:286-296 各列注释写「某类型才有」（`subject_user_id` 仅 REPLACE_ROLES，`role_id` 仅 UPDATE_ROLE，`new_role_code` 仅 CREATE_ROLE，`role_name`、`is_enabled`、`permission_codes` 仅 CREATE / UPDATE）；库内只有逐列 CHECK，无按 `request_kind` 的配对；`JdbcAuthorizationRequestRepository.java` 的 `row()` 用 `rs.getBoolean("is_enabled")`，库中为空被读成 false。
- 建议：加 `ck_authorization_requests_kind_columns`（按类型规定哪些列非空、哪些必须为空）。依据：§14.1 D2；CLAUDE.md 第 6 条（缺失输入失败，不静默）。

#### AQ-4 [中] 同 schema 的用户 / 角色引用无外键，也无索引
- 条目：B4、O10；§14.1 D2。证据：迁移:264、266、267、275 的 `requested_by`、`subject_user_id`、`role_id`、`decided_by` 均无外键；注册表 `fk_exceptions` 只豁免了 `role_id`，理由是「角色可能被删除」（`:293-301`），但 ADR-0034 第 1 项第 5 条之后角色不再物理删除，该例外失效；守护只看 `_id` 结尾的列，`requested_by`、`decided_by` 看不到。
- 建议：加 `fk_authorization_requests_requested_by`、`_decided_by`、`_subject_user_id`（指向 `user_accounts.user_id`，`RESTRICT`）及以这些列开头的索引；`role_id` 外键与索引随 R-1 建，同时删 `fk_exceptions`。依据：门禁 B4、O10；ADR-0034。

#### AQ-5 [中] 保留期未在 GUIDING_PRINCIPLES §6 登记
- 条目：C3（不通过，待用户决定）。证据：注册表 `:293-301` `PERMANENT`；§6 表无该表。含 `requested_by`、`decided_by` 等用户代理键，属 PI 的代理键，不触发 R-2，但保留期须登记并经用户认可。登记待决收件箱。

#### AQ-6 [高] `role_codes` 以业务键数组引用角色
- 条目：O2、O9。证据：迁移:268；事件 `AccountRolesReplaced` 载荷用 `roleIds`（`events/identity.yaml`），ADR-0034 第 2 项第 5 条要求申请记「相关角色当时的 `role_version_id` 数组」。建议改为 `role_ids uuid[]`（随 AQ-2 的基准列一并定形态）。依据：ADR-0033 第 1 项第 1 条、门禁 O2。

#### AQ-7 [低] 待处理清单无索引
- 条目：O10。证据：`findPending`（`where request_status = 'PENDING' order by requested_at`）；`list-paging-gaps.txt:10` 已登记分页缺口。建议：部分索引 `(requested_at, request_id) WHERE request_status = 'PENDING'`，与游标分页一并做。

#### AQ-8 [中] 可空列注释缺「为空表示」，文本列无长度上限
- 条目：O8、C1。证据：迁移:298-299（`decided_by`、`decided_at`）；`new_role_code`、`role_name` 无格式与长度 CHECK（与 R-3 同口径）。

### 跨表共性问题

#### X-1 [低] 时间列默认值取数据库时钟，而写入都不传
- 条目：O8（§2.4a-3「业务值与时间一律由应用显式写入」）、O14（时区与时钟）。证据：13 张表的 `created_at`、`updated_at` 均 `DEFAULT now()`（如迁移:11-12、35-36），而 `JdbcRoleRepository.insert`、`JdbcAccountRepository.insertIfAbsent`、`JdbcOneTimeCredentials`、`JdbcBrowserIdentifiers.replace` 的 INSERT 都不写 `created_at`；`sql-database-clock-exceptions.txt` 文件头的原则是「写入时刻取注入的 Clock」。守护只扫 Java 里的 SQL，不扫 DDL 默认值。
- 建议：去掉 DDL 里的时间默认值，INSERT 显式传 `Clock`（全库口径，建议单独成卡，不只改本 schema）。

#### X-2 [低] 枚举 CHECK 写在列上、没有名字，守护放过
- 条目：O8。证据：迁移:7、31-33、110、263、272、274 的列内 `CHECK` 无 `CONSTRAINT ck_<表>_<列>`；NAMING_STANDARD §2.4 要求一律命名；`DdlNamingTest.java:410-412` 对 `inlineCheck()` 直接 `continue`，基线清单无 `_check` 行（`ddl-naming-baseline.txt` 中 0 处）。G-D12 在 NAMING_STANDARD §4 属「待实现的守护」。
- 建议：全部改具名（基线开放期直接改基线）。依据：NAMING_STANDARD §2.4；§14.1 D18。

#### X-3 [中] `_code` 列格式与文本长度上限普遍缺失
- 条目：O8。涉及 `roles.code`、`role_permissions.permission_code`、`navigation_items.permission_code`、`ticket_issuers.issuer_code`、`signing_key_code`、`sso_ticket_uses.ticket_source`、`authorization_requests.new_role_code` 与各 `name`、`address` 类文本列；上文各表低 / 中项逐一列出，本条合并为一次修复。依据：NAMING_STANDARD §2.4a-5。

#### X-4 [中] D17 数据类别与版本化方式未登记
- 条目：O6、D17 细则 1、2。注册表尚无数据类别字段（DG-0），按门禁「写在卡里」。建议的判定（待 one-data 与 one-architecture 确认）：`roles`、`account_roles` 属授权关系，按 ADR-0034 做时刻区间版本；`user_accounts`（审计可完整还原单一状态，豁免理由二）；`navigation_items`、`ticket_issuers`（只由迁移维护，豁免理由一）；`emergency_admins`（安全凭据不得留历史，豁免理由三）；`authorization_requests` 属交易类；一次性凭证与会话属技术类。豁免须写进注册表且「只减不增」，现均未写。

#### X-5 [中] 迁入血缘未登记
- 条目：C5、O15。证据：ADR-0026 第 8 项规定老角色与授予一次性迁入（范围对照、批次号）；注册表 `roles`、`account_roles` 为 `source: NEW` 且无 `migrated_from`、`via`（`:234-254`）。账号是否也由老系统迁入待核（见未核实）。
- 建议：`roles`、授予表补 `migrated_from` 与 `via: ONE_OFF_IMPORT`；`migration_batch_id` 随 ADR-0034（见 AR-1）。

#### X-6 [低] 信息图没有身份与权限类对象，B1 的判定归类待定
- 条目：B1。证据：`ENTERPRISE_INFO_MAP_DRAFT.md` 中无账号、角色、授权申请、应急账号对象（对象列表只有业务对象，OBJ-56 人员是员工）；注册表对 `user_accounts`、`roles`、`authorization_requests`、`emergency_admins` 的 role 是 `AGGREGATE_ROOT`，而 B1 的技术类只列 `QUEUE`、`COUNTER`、`IDEMPOTENCY` 等。按条文「业务类表无对象：停，交 one-architecture」。见待决第 8 项。

#### X-7 [低] `data-classification.txt` 仍是三段格式
- 条目：C4。ADR-0043 已采纳四段格式，登记文件尚未改（U-13 要求 `SPI` 定名前不开实施 PR）；本 schema 各列的个人信息标记目前只存在于 ADR-0043 第 3 项表，卡上须另列分级表。

## 待用户决定

1. **ADR-0034 的落地时点**：基线现状违反已采纳 ADR（R-1、RP-1、AR-1、AQ-2），台账写「dev 前」。是否接受「排期即不阻断」，还是在下一次基线改动前就落地。
2. `user_accounts.last_login_address` 的具体保留期限与清除时机（U-12 已定「有期限」，期限待提出）。
3. `authorization_requests` 的保留期（GUIDING §6 未登记，现为永久）。
4. `sessions.session_id`、`primary_id` 的访问级别：RESTRICTED 还是 SENSITIVE。
5. `roles.access_scope = CUSTOM_ORG_UNITS` 在明细表建成前是否允许（R-4）。
6. `login_states.binding_hash` 的访问级别（标记按安全材料 NONE，待确认）。
7. `ticket_issuers.allowed_role_codes` 的形态（关联表还是数组），与 ADR-0039 的 C1 研究一并定。
8. 平台身份类聚合（账号、角色、授权申请、应急账号）在 B1 里按「业务类」还是「技术类」归类，是否补信息图对象。
9. `browser_identifiers` 的保留天数与口径（BI-2），以及「同一浏览器上其他账号的标识一并换值」是否保留。
10. 全库时间默认值（X-1）：是否统一去掉数据库默认，由应用传时钟。
11. `sso_ticket_uses` 的保留口径：GUIDING §6 写「到期后再留 7 天」，代码为「使用后保留 `replayWindow`（默认 7 天）」（`SsoSettings.java:10`、`LoginWithSso.java:59`），待确认以哪个为准。

## 需 opus 复核的高风险项

1. **AQ-1 双人确认的 CHECK 漏洞**：安全不变量（I-12、ADR-0030）在库内可绕过，需核对所有写入路径与 ADR-0030 对该 CHECK「不放宽」的承诺，并设计成对约束。
2. **R-1 / RP-1 / AR-1 / AQ-2 一并的 ADR-0034 结构落地**：涉及 `role_versions`、`account_role_grants`、触发器、排他约束与启动对账，是结构设计，且与 ADR-0039（登录表拆分）、ADR-0032 的双人规则交叉。
3. **S-1 与 LS-1 的分级**：会话 Cookie 值与 state 绑定值进库明文，分级与访问路径决定读库者能否冒用会话。
4. **BI-1 浏览器标识的换新语义**：`replace` 改写其他账号行的 `identifier_hash`，既违反注册表登记的 INSERT_DELETE，也改变了「谁已确认过本人」的信任边界，需安全判定。
5. **UA-1 登录地址保留与 `recordLogin` 无条件写入**：R-2 要求的清除路径与 `versioned-table-unconditional-writes.txt` 的例外如何并存，期限定了才能落结构。

## 未核实

- 本分支的 `DATA_DESIGN_GATE.md` 为旧版；按 `claude/one-data-gate-v2` 版本判定，未核对该版是否已合并到 main。
- 未起容器、未跑任何测试、未做 EXPLAIN：S-3、LS-2、BI-3、AQ-7 的索引判断来自 SQL 与行数量级估计，标「待核」。
- `DdlNamingTest` 对内联 CHECK 的处理只读了 `:410-412`，未跑测试；`DataArchitectureTest` 对 `mutability` 与实际写入的比对规则未逐条读。
- `sso_ticket_uses`：门户票据自身的有效期未查，SU-1 与待决第 11 项的两种口径是否实际冲突待核。
- `user_accounts` 是否也有老系统迁入来源（ADR-0036 的账号范围）：未核；`roles` 的迁入见 X-5。
- `AccessScopeCalculator` 里 `role.customOrgUnits()` 的数据来源（R-4）未追到。
- RULE-347 的「上级必须是目录」在新系统的对应规则卡未找到（N-3）。
- ADR-0027、ADR-0032、ADR-0044 是否另要求 identity 表增列（授予方式等）：只核了与 ADR-0034 重叠的部分，未逐条对照。
- Web 出参与日志是否输出 `SENSITIVE`、`RESTRICTED` 列（E1 之外）未核；`RegisterEmergencyAdmin.java` 的异常信息含登录名，是否违反 O-3 未核。
- `asyncapi.yaml` 与 `events/identity.yaml` 同源，未单独核对生成物。
- Spring Session JDBC 对迁移里额外的 `created_at`、`updated_at` 列是否兼容（框架 INSERT 的列清单）未核；`primary-key-exemptions.txt` 在仓库中不存在，框架表豁免实际由 `DdlNamingTest.FRAMEWORK_TABLES`（`:37`）承担，与 ADR-0033 第 1 项第 8 条的写法不一致，请 one-quality 确认。
