---
卡号: DATA-REPAIR-identity
状态: 进行中
负责会话: one-1a-par
前置: R-1b-P5
---
# 派工卡 DATA-REPAIR-identity：platform_identity 已建表的数据设计缺陷修复（草稿）

- 草稿（第二版）：one-data 起草助手，2026-10-16；第二版并入用户 2026-10-16 的决定、统一分级（阻断 0）与 `SCAN_SUPPLEMENT.md` 补入；待 one-data 终审、one-coordinator 派工。
- 证据来源：`docs/modernization/data/review-parts/platform_identity.md`（下称 PI）与 `OPUS_VERIFY.md`（下称 OV）；以 OV 的复核结论与新级别为准。「迁移:N」指 `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql` 行号（取自 PI，按审查时 main 基线；R-1b P5 合并后行号会变，实施前逐条重核）。
- `SCAN_SUPPLEMENT.md`（下称 SS）：全量机械扫描与补审，行号取 `origin/main`（a27bf877），与 PI 旧行号可能差几行；实施前以 P5 合并后的 main 逐条重核。
- 级别以统一分级为准（`DATA_DESIGN_GATE.md` §4a、`BASELINE_TABLE_REVIEW.md` §1、§3）：阻断 0；本卡 N-1 降中（表只由迁移维护，`BASELINE_TABLE_REVIEW.md` §1）；UA-2、UA-3、N-4、AQ-8 由低升中，已起条目 ID-12、ID-13。
- 「待核」表示分报告与复核报告都没有给出证据，实施者先核实再改，不凭推测落约束。

## 1. 卡号与类别
- 卡号：DATA-REPAIR-identity
- 类别：缺陷修复（SESSION_PLAYBOOK §6.1 第 9 条，只选一类）
- 批次：已建表数据设计审查（门禁 v2 §1.2 第 6 条「补审不通过而卡已合并」）的修复批；一个小 PR 直接合入 main（§6.1 第 10 条）
- 建议负责会话：one-1a-next-4
- 与 W2 的关系：ADR-0034 的 R-1 / RP-1 / AR-1 / AQ-2 / AQ-6 已排 W2 独立卡（ADR-0034:288-290；NEXT_BATCH_PLAN:269；COORDINATOR_LEDGER:47），本卡不含，见第 6 节
- 排期：本卡排在 dev 部署之后（用户「同意，不挡 dev」，`BASELINE_TABLE_REVIEW.md` §3 末段）；本卡本身不是闸门；W2 须在 dev 正式交用户使用前完成（one-coordinator 2026-10-17 口径：dev 前阻塞，不挡 rc.6 重建；one-ops 登记到 PREDEPLOY，C-15 一组，见 README）

## 2. 依据
- 门禁 v2（`origin/claude/one-data-gate-v2:docs/modernization/data/DATA_DESIGN_GATE.md`）：B3、B5、O4、O3、O10、O8、C3、C4、C5
- SESSION_PLAYBOOK §14.1 D2（成对 / 互斥落约束）、D4、D8、D12、D13；§12.2（基线开放期直接改基线，附预期表结构 diff）；ADR-0038（迁移基线开放期）
- ADR-0030:15-16（授权申请两人约束为不可放宽的双人防线）、ADR-0026 第 6 项第 2 条
- ADR-0043 第 2、3 项（会话内容 RESTRICTED、列分级）、第 6 项 R-2、U-12（登录地址「改为有期限」、期限提出前以带到期日 gap 登记）、U-13（四段格式前的写法）
- CLAUDE.md 第 6 条（缺失输入失败，不静默）
- 用户决定（第 5 节「已定」）：会话标识级别「RESTRICTED（口令、密钥级，不出库、不进日志与分析层）」（收件箱 2026-10-16-one-data-baseline-classification；intent `2026-10-16-085331-one-data-会话标识-session_id.md`）
- 复核编号：OV「阻断与高逐条复核」PI AQ-1；「高风险项判断」PI 第 1、3、4、5 条；「中级抽查」PI AQ-3；建议新增规则第 1、8 条
- 分报告编号：PI AQ-1、AQ-3、AQ-4、AQ-8、S-1、LS-1、BI-1、UA-1、UA-2、UA-3、R-2、R-3、EA-1、EA-2、N-1、N-2、N-4；SS §1、§2、§3、§4（第 10、11 组）

## 3. 范围与卡上文件

允许改动（越界先停下报告，不先做后报）：
- `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql`（只限 `authorization_requests`、`roles`、`emergency_admins`、`navigation_items` 的约束与索引、列注释）
- `backend/architecture/data-access-registry.yaml`（只限 `user_accounts`、`browser_identifiers`、`emergency_admins`、`authorization_requests`、`navigation_items` 条目）
- `backend/architecture/data-classification.txt`（只限 `sessions`、`login_states` 条目；`user_accounts.last_login_address` 的登记不动）
- `backend/architecture/unversioned-table-writes.txt`（只限 `browser_identifiers` 一行，ID-05）
- Java：`backend/src/main/java/com/forcitis/one/platform/identity/infrastructure/persistence/JdbcBrowserIdentifiers.java`（ID-05）、`JdbcAuthorizationRequestRepository.java`（ID-02 若需补空值失败的写法）
- 基线文件的允许范围另含 `user_accounts`（ID-12）、`navigation_items`、`authorization_requests` 的文本列约束（ID-13）
- 测试：`platform.identity` 下 `authorization_requests`、`browser_identifiers`、`emergency_admins`、`user_accounts`、`navigation_items` 的集成测试（路径实施者以 grep 为准，待核）；`DataArchitectureTest`、`DdlNamingTest` 只读不改

归属：本卡独占 `platform_identity` 基线文件的上述片段；`roles`、`role_permissions`、`account_roles`、`authorization_requests` 的 ADR-0034 部分归 W2 卡，本卡改动的是不会被 W2 卡重写的列与约束（`roles.code`、`roles.kind`、`authorization_requests` 的 `decided_*` 与 `request_kind` 配对）。

不碰：`role_versions`、`account_role_grants` 等 ADR-0034 新表；`events/identity.yaml`；验收测试目录；他卡文件；`sessions` 与 `sessions_attributes` 的表结构（框架表）。

## 4. 条目清单

### ID-01　AQ-1　高（OV 确认）　双人确认的 CHECK 对「已确认但无确认人」不设防
- 问题：`ck_authorization_requests_two_people` 形如 `decided_by IS NULL OR (…) OR request_status='REJECTED'`，`CONFIRMED` 且 `decided_by` 为空时整条为真，四眼约束被绕过；状态与 `decided_by`、`decided_at` 之间无成对约束。
- 证据：PI 迁移:282、:274-276；OV 复核「AQ-1 确认」：唯一写入点 `JdbcAuthorizationRequestRepository.save` 总是同时写两列，`grant-role` 运维路径不经本表（`GrantRoleByOperations.java:61-62` 直改账号）；ADR-0030:15-16。
- 改法（OV 建议两条）：
  - 新增 `ck_authorization_requests_decision`：`(request_status = 'PENDING') = (decided_by IS NULL) AND (decided_by IS NULL) = (decided_at IS NULL)`
  - 两人约束改为：`request_status IN ('PENDING','REJECTED') OR (decided_by <> requested_by AND decided_by IS DISTINCT FROM subject_user_id)`，去掉 `decided_by IS NULL OR` 放行
  - W2 加 `VOIDED` 时并入「非 PENDING」一侧（作废保留确认人）；「确认人不持有被改角色」跨表，留应用（`AuthorizationRequest.java` 的 `confirm`）
- 预期表结构 diff：`platform_identity.authorization_requests`：新增 `ck_authorization_requests_decision`；`ck_authorization_requests_two_people` 表达式改写。
- 验收要点：直接插入 `CONFIRMED` 且 `decided_by` 为空、`PENDING` 但 `decided_by` 非空、`decided_by = requested_by` 的 `CONFIRMED` 行，三者都被拒；`REJECTED` 行带两列通过；先 grep `backend/src/test` 有无夹具直接插 `CONFIRMED` 不带 `decided_by`（OV 标「未核」，须先核）。
- 依据：门禁 B3、B5、O4；D2；ADR-0030；ADR-0026 第 6 项第 2 条；OV 建议新增规则 1（双人确认库内防线跨表规则）；SS §4 第 11 组（`decided_by:281`、`decided_at:282` ⇔ `request_status:280 ≠ 'PENDING'`；`:288` 对「CONFIRMED 且 decided_by 为空」放行）。

### ID-02　AQ-3　中（OV 确认）　申请类型与各列取值配对未约束；`is_enabled` 为空被读成 false
- 问题：库内只有逐列 CHECK，没有按 `request_kind` 的配对；`rs.getBoolean("is_enabled")` 把 NULL 读成 false，违反 CLAUDE.md 第 6 条。
- 证据：PI 迁移:286-296（各列注释「某类型才有」：`subject_user_id` 仅 REPLACE_ROLES，`role_id` 仅 UPDATE_ROLE，`new_role_code` 仅 CREATE_ROLE，`role_name`、`is_enabled`、`permission_codes` 仅 CREATE / UPDATE）；`JdbcAuthorizationRequestRepository.java:101`；OV「中级抽查 AQ-3 确认」。
- 改法：新增 `ck_authorization_requests_kind_columns`，按 `request_kind` 规定哪些列必须非空、哪些必须为空；`role_codes` 属哪个类型、`VOIDED` 之后的基准列如何配对：待核（W2 的 AQ-2 会再加列，约束写成可追加的形态）。
- 预期表结构 diff：`authorization_requests`：新增 `ck_authorization_requests_kind_columns`。
- 验收要点：每个 `request_kind` 的合法组合通过、缺列或多列被拒；`is_enabled` 为空的 CREATE_ROLE 行写不进去（不再靠读取时默认 false）。
- 依据：门禁 O4、B5、O8；D2；CLAUDE.md 第 6 条；SS §4 第 11 组（`request_kind:269` ⇔ `subject_user_id:272`、`role_id:273`、`new_role_code:275`、`role_name:276`、`is_enabled:277`）。

### ID-03　S-1　高（OV 升级，原中；级别用户已定）　会话标识等价于 Cookie，明文入库且未登记
- 问题：`sessions.session_id` 是 Spring Session JDBC 的会话标识，等同 Cookie 持有凭据；未登记即默认 INTERNAL，任何读库者可冒用会话。
- 证据：PI 迁移:197、212 注释「Cookie 值」；`data-classification.txt` 只登记 `principal_name`（:17）与 `sessions_attributes.attribute_bytes`；OV「高风险项判断 PI 第 3 条」。
- 用户决定：「RESTRICTED（口令、密钥级，不出库、不进日志与分析层）」（收件箱 2026-10-16-one-data-baseline-classification；intent `2026-10-16-085331-one-data-会话标识-session_id.md`）。
- 改法：在 `data-classification.txt` 登记 `sessions.session_id`、`sessions.primary_id` 为 `RESTRICTED`；个人信息标记待 one-data / U-11 裁定（复审 M2 后不再写 `NONE`），四段化随 ADR-0043 实施 PR。理由以「用途：」之外的安全材料说明，同 `state_hash`。
- 待核（落笔前）：ADR-0043 第 2、3 项对 `RESTRICTED` 列的守护口径（是否要求只存摘要、是否有「不进日志与分析层」的静态扫描）；若要求只存摘要，须自定义会话仓储（Spring Session 无内建，OV 标「未核实」），那属另卡，本卡不改会话仓储，只登记并在 PR 写明该缺口。
- 预期表结构 diff：无（登记文件变化，不改 DDL）。
- 验收要点：`data-classification.txt` 含两列且级别为 `RESTRICTED`；分级守护通过；不改会话仓储。
- 依据：门禁 C4；ADR-0043 第 3 项（会话内容 RESTRICTED）；OV 建议新增规则 8（名含 session、token、ticket、cookie 的非代理键列须登记且不低于 SENSITIVE）；用户决定（第 5 节「已定」）。

### ID-04　LS-1　中（OV 维持）　`login_states.binding_hash` 未分级
- 问题：同表 `state_hash` 已登记 SENSITIVE，`binding_hash`（发起浏览器所持绑定值的 SHA-256）未登记。
- 证据：PI 迁移:114、124 注释；`data-classification.txt:12`；OV 判「按 `state_hash` 同口径登记 `SENSITIVE + NONE`」。
- 改法：**级别待用户定**（用户对会话标识已答 RESTRICTED，对 `binding_hash`「同口径另议」，见第 5 节）；用户答复前不落；OV 建议口径为 `SENSITIVE + NONE`（同 `state_hash`），若用户改判 RESTRICTED 则随之。
- 预期表结构 diff：无。
- 验收要点：用户答复后，分级文件含该列且级别与答复一致。
- 依据：门禁 C4；ADR-0043 第 2、3 项。

### ID-05　BI-1　中（OV 维持）　`browser_identifiers` 登记 INSERT_DELETE，实际更新行并改写其他账号行的 `identifier_hash`
- 问题：`replace` 换新时把同一浏览器上其他账号未退役行的 `identifier_hash` 原地改成新值，其他账号失去旧值「退役后重放」检测。
- 证据：PI 迁移:247-250、注册表 :273-278（`ONE_TIME` + `INSERT_DELETE`）、`unversioned-table-writes.txt:23`；`JdbcBrowserIdentifiers.java:34-49`；OV「高风险项判断 PI 第 4 条」：语义是信任随 Cookie 迁移、不扩大信任面。
- 改法（OV 建议）：其他账号的旧行同样置 `retired_at` 并插入新行，不再改写键值列；注册表 `mutability` 改 `CONDITIONAL_UPDATE`，前置条件 `retired_at IS NULL`；`unversioned-table-writes.txt:23` 对应行随之修正。
- 预期表结构 diff：无 DDL 变化（注册表与写入语句变化）。
- 验收要点：换新后原行 `identifier_hash` 不变、`retired_at` 已置，新行存在；旧值重放被识别；注册表与实际 SQL 一致（OV 建议新增规则 3）。
- 依据：门禁 O5、O7、C5；D4、D8。

### ID-06　UA-1　中（OV 降级，原高）　含 PI 的 `last_login_address` 永久保留，未登记带到期日的 gap
- 问题：`user_accounts` 注册表为 `PERMANENT`，表内 `last_login_address` 含 PI，注册表无 `gaps:` 条目。
- 证据：PI 迁移:6；注册表 `data-access-registry.yaml:227-233`；ADR-0043 R-2、U-12；OV「UA-1 降级」「高风险项判断 PI 第 5 条」：与 `versioned-table-unconditional-writes.txt` 不冲突，清除是停用 / 离职事务里把列置空的另一条写入。
- 改法：注册表为 `user_accounts` 加带 owner 与到期日的 gap（期限随 ADR-0043 实施 PR 提出，到期日由用户在待决 2 给）；表结构侧清除办法不在本卡（见第 6 节）。
- 预期表结构 diff：无。
- 验收要点：注册表有 gap，含 owner、到期日、指向 U-12；R-2 守护（随 ADR-0043 实施 PR）不在本卡。
- 依据：门禁 C3；ADR-0043 R-2、U-12。保留期：SS 与 PI 不另起；清除路径若只置空 `last_login_address`，须与 ID-12 的成对 CHECK 协调（见 ID-12）。
- one-data 2026-10-16 裁定：接受改为注释；转 gap 挂在 ADR-0043 实施 PR 上（R-2 守护建成后）。

### ID-07　R-2 + R-3（编码部分）　中　系统角色保留码与角色编码格式只靠 Java
- 问题：「super_admin 为预留码，只能由迁移脚本建立」只在 `Role.java`（`isReserved`、`requireModifiable`），角色编码无格式约束。
- 证据：PI 迁移:44 注释、:29-30；`Role.java` 的 `CODE` 正则 `[A-Za-z0-9][A-Za-z0-9._-]{0,63}`；RULE-345。OV 未单独抽查，按 PI 证据取，实施前核对 `Role.java`。
- 改法：新增 `ck_roles_reserved_code`：`lower(code) <> 'super_admin' OR kind = 'SYSTEM'`；新增 `ck_roles_code`：与 `Role.CODE` 同一正则。`roles.code`、`roles.kind` 在 ADR-0034 后保留（ADR-0034 第 1 项第 1 条），故不会被 W2 卡重写；名称长度上移到 `role_versions.role_name`，归 W2。
- 预期表结构 diff：`roles`：新增 `ck_roles_reserved_code`、`ck_roles_code`。
- 验收要点：种子六个系统角色通过；`kind = 'CUSTOM'` 且编码为 `Super_Admin`（任意大小写）被拒；非法字符编码被拒。
- 依据：门禁 O4、O8；D2；NAMING_STANDARD §2.4a-5。

### ID-08　EA-1　中　`emergency_admins` 登录名格式与计数列取值范围只在应用里
- 证据：PI `RegisterEmergencyAdmin.java:55-57`（`LOGIN_NAME` 正则，3 至 32 位，小写字母开头，限小写字母、数字、点、短横线、下划线）；迁移:169、:172-173（`account_name` 仅 UNIQUE，`last_totp_step`、`failed_attempts` 无非负 CHECK）；RULE-317。
- 改法：新增 `ck_emergency_admins_account_name`（同一正则）、`ck_emergency_admins_failed_attempts`（`>= 0`）、`ck_emergency_admins_last_totp_step`（`>= 0`）。
- 预期表结构 diff：`emergency_admins`：新增三条 CHECK。
- 验收要点：合法登记通过；大写、过短、`failed_attempts = -1` 被拒；`last_totp_step` 初值是否为 0 或空：待核（核对 `EmergencyAdmin` 初值后再定约束写法）。
- 依据：门禁 O4、O8；D2。

### ID-09　EA-2　中　`emergency_admins` 永久保留缺理由与决定出处
- 证据：PI 注册表 :285-292（`PERMANENT`）；U-12 已答「认可永久保留并登记理由」；注册表无理由字段。
- 改法：注册表字段未建成前（DG-0），把理由与出处写在本卡与注册表注释：U-12、intent `2026-10-15-225255`；字段建成后补进字段。
- 预期表结构 diff：无。
- 验收要点：注册表条目含出处，R-2 守护不将其判为未登记。
- 依据：门禁 C3；ADR-0043 R-2、U-12。

### ID-10　AQ-4　中　`authorization_requests` 的用户引用无外键、无索引
- 问题：`requested_by`、`subject_user_id`、`decided_by` 无外键；守护只看 `_id` 结尾的列。
- 证据：PI 迁移:264、266、267、275；注册表 `fk_exceptions` :293-301。
- 改法：加 `fk_authorization_requests_requested_by`、`_decided_by`、`_subject_user_id`（指向 `user_accounts.user_id`，`RESTRICT`）及以这三列开头的索引；`role_id` 外键与索引、删 `fk_exceptions` 随 ADR-0034 的 R-1 归 W2，本卡不动。
- 预期表结构 diff：`authorization_requests`：新增三个外键与三个索引（`ix_authorization_requests_requested_by` 等，名称按 §2.4）。
- 验收要点：引用不存在用户的行被拒；删除被引用账号被拒（账号实际不物理删除，PI BI-4）。
- 依据：门禁 B4、O10；NAMING_STANDARD §2.4a-2。

### ID-11　N-1 + N-2　中（OV 降级，原高；统一分级后仍为中，PI 分报告表内的「高」已被取代）　`navigation_items` 无业务唯一
- 问题：除主键外无任何 UNIQUE；`parent_item_id` 外键无索引。表为 `MIGRATION_ONLY`、无应用写入方，重复只可能来自迁移种子。
- 证据：PI 迁移:84-95、:86；注册表 :255-260；OV「N-1 降级」：RULE-347 是老系统规则卡，只作证据，不作依据。
- 改法：以门禁 O3（配置表至少一个业务唯一约束）为依据，加 `uk_navigation_items_parent_item_id_name UNIQUE NULLS NOT DISTINCT (parent_item_id, name)`，并在注册表写明它同时承担 `parent_item_id` 的索引（N-2）；`route_path` 唯一待核（须先核种子是否有重复）。
- 预期表结构 diff：`navigation_items`：新增 `uk_navigation_items_parent_item_id_name`。
- 验收要点：种子数据通过；同一上级下重名被拒；顶层（`parent_item_id` 为空）重名也被拒。
- 依据：门禁 O3、O10；NAMING_STANDARD §2.4a-1。

### ID-12　UA-2 + UA-3　中（统一分级：低→中）　`user_accounts` 最近登录时间与地址的成对约束、地址长度上限
- 问题：`last_login_at` 与 `last_login_address` 应同时有值或同时为空；`last_login_address` 是 `text`，无长度上限。
- 证据：PI UA-2、UA-3（迁移:5-6、20-21；`recordLogin` 同一语句写两列，`JdbcAccountRepository.java`；RULE-330「成功登录同时盖 IP 与时间」）；SS §4 第 10 组（`last_login_at:8`、`last_login_address:9`）、SS §1（`last_login_address:9`，已登记、无长度约束）、SS §3（两列均无空值说明）。
- 改法：
  1. 成对 CHECK 默认写单向：`last_login_address IS NOT NULL ⇒ last_login_at IS NOT NULL`。**待核**：ADR-0043 U-12「账号停用或离职后清除」`last_login_address`（OV「高风险项判断 PI 第 5 条」：清除是把列置空的另一条写入）若只清地址、留时间，双向 CHECK（PI 原写法）会让清除事务失败；清除路径随 ADR-0043 实施 PR，该 PR 定下同清两列后才可改写成双向。
  2. `last_login_address` 加 `char_length BETWEEN 1 AND 45`（IPv6 文本最长 45）；待核该列是否存过非地址取值（同 DATA-REPAIR-timesheet-audit TB-05 对 `source_address` 的待核）。
  3. 两列补「为空表示」注释（从未成功登录，或地址已按 U-12 清除）。
- 预期表结构 diff：`user_accounts`：新增 `ck_user_accounts_last_login`、`ck_user_accounts_last_login_address`；列注释补充。
- 验收要点：首次登录前（两列空）、成功登录（两列有值）通过；只有地址无时间被拒；超长地址被拒；`LoginAdmission` 等写入点原样通过。
- 依据：门禁 O4、B5、O8；D2；NAMING_STANDARD §2.4a-5；SS §4 第 10 组。
- one-data 2026-10-16 裁定：选 C（64）+ 应用侧保护；不选 A（违反 §2.4a-5）、不选 B（规范化解析可能触发 DNS、引入新语义）。签发行：数据设计：通过（one-data，2026-10-16，依据 data-pr5 @b429bc80 加本裁定）。
- 落实：基线加 `ck_user_accounts_last_login_address`（`char_length BETWEEN 1 AND 64`，可空）；`JdbcAccountRepository.recordLogin` 在写入前判定，长度超 64 或不符「四段点分十进制，或至少含两个冒号且不含 `:::` 的十六进制串，可带 `%` 区域标识」（复审 Low 收紧）的取值不写、置空并打 ALERT（只记长度，不含原值），登录照常成功，不截断。测试：`IdentityBaselineConstraintsIntegrationTest`、`WecomLoginIntegrationTest` 中名以 `DATA-REPAIR-identity ID-12` 开头者。

#### 数据设计补充（one-1a-next-4，待 one-data 签）

核对 `last_login_address` 的值来源（one-data 签发记录要求：长度上限 45 先核对来源再议）。结论：值是经可信代理解析后的单个地址，但不保证是 45 位内的规范地址文本，长度上限本批不落。

- 写入链：`LoginAdmission.complete`（`LoginAdmission.java:75`）调 `accounts.recordLogin(userId, now, origin.address())`，`JdbcAccountRepository.recordLogin`（`JdbcAccountRepository.java:121`）原样写入；只有登录成功路径写，无其他写入点。
- 值的出处：`RequestClientOrigin.address()` 返回 `HttpServletRequest.getRemoteAddr()`（`RequestClientOrigin.java:14-18`）；无请求上下文时返回字面量 `"unknown"`（登录路径上不会出现）。
- `getRemoteAddr()` 是 Tomcat 经可信代理解析后的单个地址，不是原始 `X-Forwarded-For` 整串：`platform_identity.properties:21-22` 配 `server.forward-headers-strategy=native`，只有直连对端匹配 `server.tomcat.remoteip.internal-proxies`（默认仅 127.0.0.1 与 ::1，可由 `FORCITIS_TRUSTED_PROXIES` 改）时，才取 `X-Forwarded-For` 中从右数第一个非可信代理的条目；否则就是 TCP 对端地址。部署样例 `phase1a/infra/runbooks/files/nginx-one.conf:67` 用 `proxy_set_header X-Forwarded-For` 加 nginx 变量 `remote_addr` 覆盖而非追加，正常部署下是 nginx 看到的客户端地址，客户端自带的头被丢弃。
- 可能取值：正常为 IPv4 点分十进制（最长 15）或 IPv6 文本（含 `::ffff:a.b.c.d` 映射形态，最长 45）。不保证的情形：(1) Tomcat 取出的 `X-Forwarded-For` 条目不做地址语法校验，若某个可信代理转发了带端口（`ip:port`）、带方括号或非地址串的条目，会原样入库；(2) 直连对端为链路本地 IPv6 时，`getHostAddress()` 可带区域标识（如 `fe80::1%eth0`），超过 45；(3) `FORCITIS_TRUSTED_PROXIES` 配宽后，被信任的内部主机可发任意文本。
- 影响：`recordLogin` 与登录同事务，45 位上限的 CHECK 在上述情形会让合法登录被拒，故本批不落。
- 供 one-data 定的选项：(A) 不加长度 CHECK，只留成对 CHECK 与注释（本批现状）；(B) 在 `RequestClientOrigin` 用地址字面量解析（去端口、方括号、区域标识，非法则按失败处理并告警，不静默），保证入库值为规范地址后再落 `char_length BETWEEN 1 AND 45`（须另开 Java 改动，不在本卡文件范围）；(C) 只加宽松上限（如 64），仅防异常长串。同一值也写进审计 `source_address`（各处 `.withTrace(origin.address(), …)`），选 B 时与 DATA-REPAIR-timesheet-audit TB-05 同口径处理。

### ID-13　N-4 + AQ-8　中（统一分级：低→中）　导航项与授权申请的文本列格式、长度，`decided_*` 空值注释
- 问题：`navigation_items` 的 `route_path`、`permission_code`、`name` 无格式与长度约束；`authorization_requests` 的 `new_role_code`、`role_name` 无格式与长度 CHECK（与 ID-07 的 R-3 同口径）；`decided_by`、`decided_at` 注释缺「为空表示」。
- 证据：PI N-4（迁移:87-89）；PI AQ-8（迁移:298-299）；SS §1：`navigation_items`（`name:93`、`route_path:94`、`permission_code:95`）、`authorization_requests`（`new_role_code:275`、`role_name:276`、`role_codes:274`、`permission_codes:279`）；SS §3：`authorization_requests` 7 列无空值说明。
- 改法：`navigation_items`：`route_path`、`permission_code` 各加具名格式 CHECK，`name` 加长度上限与禁空串；格式与上限待核（读种子与前端路由表，`permission_code` 格式与 `role_permissions.permission_code`、权限清单同源，PI RP-2）；`authorization_requests`：`new_role_code` 与 ID-07 的 `ck_roles_code` 同一正则，`role_name` 的上限与 `Role` 的校验常量一致（待核 `Role.java`）；`role_codes`、`permission_codes` 数组元素格式待核（`role_codes` 随 AQ-6 归 W2，不动）；补注释。
- 预期表结构 diff：`navigation_items`：新增 `ck_navigation_items_route_path`、`ck_navigation_items_permission_code`、`ck_navigation_items_name`；`authorization_requests`：新增 `ck_authorization_requests_new_role_code`、`ck_authorization_requests_role_name`。
- 验收要点：种子数据全部通过；非法路径、空名、超长名被拒；CREATE_ROLE 申请的编码与角色编码口径一致。
- 依据：门禁 O8、C1；NAMING_STANDARD §2.4a-5。

低级顺带（不阻塞，卡作者可一并改）：R-5 去业务默认值（SS §2 第 1 条的 `user_accounts.status:7 DEFAULT 'ENABLED'` 同类）；R-6 `is_administrative` 只属 SYSTEM；TI-2、TI-3；AQ-7 待处理清单部分索引；S-2、SA-1 表注释粒度；SA-2 注册表 `writers`；X-1 `DEFAULT now()`、X-2 内联枚举 CHECK 未具名（统一分级为低，见末节）。证据见 PI 对应条目。

## 5. 已定与待用户决定

### 已定（用户 2026-10-16 决定）
1. `sessions.session_id`、`primary_id` 的访问级别（原 PI 待决 4）：用户答「RESTRICTED（口令、密钥级，不出库、不进日志与分析层）」（收件箱 2026-10-16-one-data-baseline-classification；intent `2026-10-16-085331-one-data-会话标识-session_id.md`）。落点：ID-03 登记 `RESTRICTED`，个人信息标记待 one-data / U-11 裁定。

### 待用户决定
1. `login_states.binding_hash` 的级别（PI 待决 6）：用户对会话标识答 RESTRICTED 时写明「同口径另议」，故仍待用户决定；OV 建议 `SENSITIVE + NONE`（ID-04）。
2. `user_accounts.last_login_address` 的保留期限与清除时机（PI 待决 2，U-12 已定「有期限」）。
3. `authorization_requests` 的保留期（PI 待决 3，现为永久，GUIDING_PRINCIPLES §6 未登记）。
4. `browser_identifiers` 的保留天数与口径（PI BI-2、待决 9）；`sso_ticket_uses` 保留口径（PI 待决 11，GUIDING §6 写「到期后再留 7 天」，代码为「使用后保留 `replayWindow`」，`SsoSettings.java:10`、`LoginWithSso.java:59`）。
5. `roles.access_scope = CUSTOM_ORG_UNITS` 在明细表建成前是否允许（PI R-4、待决 5）；`ticket_issuers.allowed_role_codes` 的形态（PI TI-1、待决 7，与 ADR-0039 的 C1 一并定）。

## 6. 不含（已有归属，本卡不起条目）
| 项 | 归属 |
|---|---|
| R-1 `roles` 版本化、系统角色迁移不写（高） | ADR-0034 W2 独立卡；**dev 正式交用户使用前完成**（用户「同意，不挡 dev」，one-ops 登记到 PREDEPLOY，C-15 一组），不是 dev 部署闸门（OV「R-1 降级」） |
| RP-1（高）`role_permissions` 复合主键 / 删表 | 主键部分已被 R-1b P5 覆盖（`pk_role_permissions`，RULING-P5-D12）；删表并入 ADR-0034 W2 卡 |
| AR-1（高）`account_roles` 复合主键 / `account_role_grants` | 主键部分已被 P5 覆盖；授予历史并入 ADR-0034 W2 卡 |
| AQ-2（高）`VOIDED`、基准列、事件 `AuthorizationVoided` | ADR-0034 W2 卡；本卡 ID-01 的 CHECK 写成可并入 `VOIDED` 的形态 |
| AQ-6（高）`role_codes` 改 `role_ids uuid[]` | 随 AQ-2 定形态，归 W2 卡（dev 正式交用户使用前完成） |
| UA-1 的清除路径、四段格式分级、R-2 守护 | ADR-0043 实施 PR（首次部署前） |
| X-1 时间列数据库默认值（低） | 全库口径，另卡（PI 待决 10）；本 schema 11 表 22 列见末节 |
| X-2 内联枚举 CHECK 具名（低） | 守护 G-D12 实现时一次改基线（OV 建议新增规则 4） |
| X-3 `_code` 列格式与长度（中） | 能落的部分已在 ID-07、ID-08、ID-13；其余列见末节 §1 |
| AQ-5 `authorization_requests` 保留期、BI-2 `browser_identifiers` 保留天数、R-4、TI-1（中） | 待用户（第 5 节第 3、4、5 条），不起条目 |
| X-4 D17 类别登记、X-5 迁入血缘（中） | 注册表字段 DG-0 建成后；`roles` 迁入血缘随 W2 授予表 |
| N-3 目录 / 页面树完整性 | 待核（PI 未找到新系统规则卡） |

## 7. 验证办法
- 守护：`DdlNamingTest`、`DataArchitectureTest`（含注册表 `mutability` 与 SQL 一致）、分级文件守护、`GlossaryTest`（新增标识）全绿；不新增例外清单条目（唯一变动为 ID-05 对 `unversioned-table-writes.txt:23` 的修正）。
- 测试：ID-01、ID-02、ID-07、ID-08、ID-10、ID-11、ID-12、ID-13 各一条「违反即被拒、合法即通过」的集成测试（Testcontainers，镜像限 `backend/architecture/test-images.txt`）；ID-05 的换新测试；测试名以 `DATA-REPAIR-identity ID-nn` 开头，具体类名由测试代理定。
- 表结构 diff：两个空库分别执行改动前（P5 合并后的 main）、改动后的基线，`pg_dump --schema-only` 对比；预期差异只能是第 4 节「预期表结构 diff」所列。不得出现：`platform_identity` 以外任何表的变化；`roles`、`account_roles`、`role_permissions` 的列增删；种子数据变化。
- `scripts/precheck.sh <基线>` 全部 PASS 或有理由的 SKIP，输出贴进 PR「precheck.sh 输出」。

## 8. 执行者与复审
- 执行者：sonnet 子代理（改动机械、有证据）；测试先写；ID-01 与 ID-03 首轮复审用 opus（安全不变量与分级）；其余 sonnet 复核。
- 工作区：独立 worktree，分支 `claude/one-1a-next-4-data-repair-identity`，基线为 P5 合并后的 main。
- 报告：向 one-1a-next-4 并抄 one-coordinator，分开写「已验证」与「未运行」。

## 9. 禁止事项
- 不放宽、不删除断言；不改验收测试目录；不改卡外文件；不顺手重构、改名或统一风格。
- 不追加迁移（基线开放期，ADR-0038）；不为通过测试删约束。
- 不联网、不起容器（Testcontainers 白名单镜像除外）、不建账号；不打印、不写入密钥，测试密钥运行时生成。
- 不改 ADR-0034 范围内的表形状；需改时停下报告。
- 缺失或无效输入失败并告警，不静默跳过。

## 10. 机械类低级项（按表列出，不逐条展开；列清单与行号见 `review-parts/SCAN_SUPPLEMENT.md`）

SS 行号取 `origin/main`。同一类改法一致，由执行者按表一次改完，每类一条验收即可。无长度上限为中，其余为低；§1 虽为中，因机械且同一改法，一并列此。框架表 `sessions`、`sessions_attributes` 不计。

| SS 节 | 类 | 级别 | 属本卡 schema 的范围 |
|---|---|---|---|
| §1 | text 列无长度与格式约束 | 中 | idt 共 23 列：user_accounts 1（`last_login_address`，ID-12）、roles 2（`code`、`name`，ID-07）、role_permissions 1（`permission_code`）、navigation_items 3（ID-13）、login_states 2（`state_hash`、`binding_hash`）、sso_ticket_uses 2、ticket_issuers 4（`allowed_role_codes` 只有「不含 super_admin」）、emergency_admins 3（ID-08 只覆盖 `account_name`）、browser_identifiers 1、authorization_requests 4（ID-13）；其中 `state_hash`、`ticket_hash`、`identifier_hash` 等哈希列建议同一正则 `^[0-9a-f]{64}$`（待核各列编码） |
| §2 | 业务默认值（NAMING_STANDARD §2.4a-3 要求应用显式写入） | 低 | idt 共 11 项：`user_accounts.status:7`、`roles.kind:31` / `status:32` / `access_scope:33` / `is_administrative:34`、`navigation_items.is_enabled:97`、`ticket_issuers.is_enabled:156` / `allowed_role_codes:157`、`emergency_admins.is_enabled:181`、`authorization_requests.role_codes:274` / `permission_codes:279`（其中 `roles` 三项 PI R-5 已记，其余 8 项分报告未记） |
| §3 | 可空列缺「为空表示」注释 | 低 | 「无空值说明」共 9 列：`user_accounts.last_login_at:8`、`last_login_address:9`（ID-12）；`authorization_requests`（`subject_user_id:272`、`role_id:273`、`new_role_code:275`、`role_name:276`、`is_enabled:277`、`decided_by:281`、`decided_at:282`，其中前五列有隐含说明）；「等价措辞」列 navigation_items 2、user_accounts 无 |
| §4 | 成对可空列缺 CHECK（中） | 中 | 第 10 组 → ID-12；第 11 组 → ID-01、ID-02 |
| §5 | `DEFAULT now()` 与「应用注入时钟」矛盾 | 低 | idt 11 表 22 列（user_accounts、roles、role_permissions、account_roles、navigation_items、login_states、sso_ticket_uses、ticket_issuers、emergency_admins、browser_identifiers、authorization_requests 的 `created_at`、`updated_at`）；全库口径另卡（PI 待决 10），本卡不单改 |
| §6 | 内联枚举 CHECK 未具名 | 低 | idt 8 处：`user_accounts.status:7`、`roles.kind:31` / `status:32` / `access_scope:33`、`login_states.entry:116`、`authorization_requests.request_kind:269` / `access_scope_kind:278` / `request_status:280`；随 G-D12 一次改基线（`authorization_requests` 的 `request_status` 同时受 ID-01、W2 的 `VOIDED` 影响，先随 W2 定形态） |

### 保留期与匿名化（用户已定「按 one-data 建议」，见 `../RETENTION_PROPOSAL.md`）

- `authorization_requests`：10 年，归档到对象存储后在线删除。
- `user_accounts.last_login_address`：在职只留最近一次；账号停用或离职起 30 天内清除（U-12；与 ID-06 的 gap 衔接，成对 CHECK 单向，见 ID-12）。
- `browser_identifiers`：退役后 30 天（保留天数以代码值为准，待核）。
- `sso_ticket_uses`：票据到期后 7 天（GUIDING_PRINCIPLES §6 原文），代码现为「使用后保留 replayWindow」，改代码对齐。
