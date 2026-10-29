---
卡号: ADR-0034-IMPL
状态: 进行中
负责会话: one-1a-par
前置: R-1b-P5
---

> 进度（2026-10-21）：PR-1 已合入（#476）；PR-2、PR-3 待做，PR-3 按 `phase1a/a8-split/SPLIT_PLAN.md` 改范围（切分方案 C）。
# 派工卡 ADR-0034-IMPL：授权历史可还原——角色与授予版本化（含 DDL-17 角色不物理删除、复职重授）

- 草稿：one-1a-par，2026-10-17，基线 origin/main `cfb04dd4`。本卡是 F-1 的前置（F-1 卡头 `前置: R-1b-P5, ADR-0034-IMPL`，分支 `claude/one-1a-par-f1-card` `369769fd`）；F-1 预期 diff 的「改动前」以本卡全部 PR 合并后的 `platform_identity` 基线为准。
- 数据设计：待审（one-data）。签发行尚未出现；表结构 PR、验收测试与实现开工前须核对签发有效（`DATA_DESIGN_GATE.md` §1.2、§1.3）。
- 「待核」表示手头证据不足，开工前由实现者或 one-data 核实；「未核实」表示本稿作者没读到证据。卡与已采纳 ADR 字面不一致处不自行裁定，列在第 13 节「待 one-architecture 裁定」。
- 行号以 `cfb04dd4` 为准；ADR-0034 正文引用的行号取自 `3010496d`，已漂移的在下文按今天的行号改写。

## 1. 卡号与类别
- 卡号：ADR-0034-IMPL（NEXT_BATCH_PLAN 第 269 行 W2「ADR-0034 实现（权限三表、复职重授 F-13 建在其上、DDL-17 角色退役不物理删除）」、第 587 行；COORDINATOR_LEDGER 第 52 行「ADR-0034 实现与 DDL-17」；HANDOFF_one-1a-next 第 42 行；F-1 卡 C-1 裁定：分两卡，先做本卡，one-1a-par 负责）
- 类别：功能补做（应交付未做：已采纳 ADR 的实现，属安全项）
- 批次：W2；PR 拆分见第 12 节；不并入他卡的整合分支

## 2. 依据与出处核对

### 2.1 用户决定
- ADR-0034 已采纳：用户 2026-10-15「两份都批准 (Recommended)」；0034-Q2「授予与角色版本保留期与审计同周期（在线 3 年 + 归档到满 10 年）；在线删除只走受控特权路径，运行角色始终没有 DELETE」；0034-Q3「删除角色关闭最后一个版本、编码不得复用」。
- 复职 D-1a-17（`process/1a/contract.md`:56）：「只授基础角色 (Recommended)」，复职重授 `employee`，其余由管理员重新分配，审计记录（intent `2026-10-14-115-1a流程-复职角色.md`）。
- §14.1 D17（METHODS.md:419-429）：主数据默认版本化；授权关系按时刻、`timestamptz` 区间；授权取未关闭的行；早于最早版本的查询失败告警。
- 迁移基线开放期直接改基线（ADR-0038；`backend/architecture/migration-baseline-open` 存在，已核）。

### 2.2 规则、ADR、规范
- ADR-0034 全文（`adr/src/ADR-0034.md`），逐条见数据设计「ADR 条款对照」。
- RULE-345 修订已写入 `RULE_REVIEWS.json`（第 438 行起 `revisions`，2026-10-15，出处 ADR-0034 / 0034-Q3，已核）；RULE-344、RULE-363 不变。
- DDL 审计 DDL-17（`~/.modernize/psa/audit/DDL_AUDIT_2026-10-15.md`:31，未入仓）：「roles 被物理删除（JdbcRoleRepository.java:100）→ RETIRED 状态 + 关闭版本，代码不复用；ApplySourceSnapshot 删合并的同步运行属技术去重，登记 delete_reason」。前半由本卡承担（形态按 ADR-0034，见待裁定 A-1）；后半属 `organization.workforce`，见待定 C-4。
- DATA-REPAIR-identity 并入 W2 的条目（`HANDOFF_one-1a-next-4.md`:34「ID-01/02/10/13 → W2」；`data/repair-cards/DATA-REPAIR-identity.md` §6：AQ-2「`VOIDED`、基准列、事件 `AuthorizationVoided`」、AQ-6「`role_codes` 改 `role_ids uuid[]`」归 W2 卡）。是否由本卡承担见待定 C-3。
- NAMING_STANDARD §2.4（类别词、约束与索引命名）、§2.4a（UUIDv7、外键索引、空值与默认值、删除语义、文本长度、枚举 CHECK 具名）、§14、§14.1；`DATA_DESIGN_GATE.md` 附录 A。

### 2.3 核对结果（基线 `cfb04dd4`，凡「有 / 没有」均已 grep 或读文件）
- `platform_identity` 只有一个基线文件 `V20261014130000__platform_identity_baseline.sql`（380 行）：`roles`（:29-57，含 `name`、`status`、`access_scope`，`uq_roles_code`、`uq_roles_name`）、`role_permissions`（:59-73）、`account_roles`（:76-92，表注释「离职时全部删除（ADR-0027）」）、`authorization_requests`（:275-317，状态只有 `PENDING / CONFIRMED / REJECTED`）、角色与权限种子（:319-380）。全仓没有 `role_versions`、`account_role_grants`。
- 物理删除与整组替换：`JdbcRoleRepository.java`:104 `delete from platform_identity.roles`；:119 起 `savePermissions` 按 `not in` 删除；`JdbcAccountRepository.java`:115 起 `saveRoles` 同法；`UserAccount.java`:122 `recordSeparation` 置空角色集合；`HandleOrgSnapshotChange.java`:96 起 `revokeRoles`，:115 写 `platform.identity.account.role.revoked`，detail 只有角色码。
- 读取经仓储一处：`JdbcAccountRepository.java`:151 `findEnabledRoles`、:160 `findAssignedRoleCodes`、:170 `findEnabledHolders`，调用方 19 个文件（含 `RoleHolders`，经 `RoleDirectory` 被 `platform.approval` 使用）。
- 授权申请：`AuthorizationRequestStatus` 只有三值；`ApplyConfirmedAuthorization.java`:33 监听器无基准比对、拒绝时只写 ALERT，申请停在 `CONFIRMED`。
- `AuditLog.record` 返回 `void`（`platform/audit/api/AuditLog.java`）；角色与授予的领域事件由 `platform.audit` 的 `AuditDomainEvents` 监听后另一事务写审计，业务事务内拿不到条目 ID。
- `OrgSnapshotChanged` 负载无同步运行 ID；信封 `aggregateId` 是 `syncRunId`（`SyncRun.java`:177、:240）。
- `Permission` 是枚举，没有「已停用」标记（`platform/identity/api/Permission.java`:4）。
- ADR-0032 第 1 项第 3 条（有持有人不得扩大效力）与第 2 项（授予方式声明）未实现：`UpdateRole`、`DeleteRole` 无持有人判定，全仓无 `GrantMode`、`permission-grant-modes.txt`；NEXT_BATCH_PLAN:202「随 F-1 落地」。
- D2 守护只认 `date` 区间（`DataArchitectureTest.java`:288-306）；D4 的 `<n>y` 保留期必须指名已存在的 `@SchedulerLock` 作业（:316-328）。
- `DdlNamingTest` 类别词表（:42-79）没有 `_ids`、没有 `_revision`；每张表必须有 `created_at`、`updated_at`（:628-632）；排他约束名 `ex_<表>_<规则>`（:495）。
- `btree_gist`：建库脚本已预建（`one_db_roles_schemas.sql`:29、`runbooks/sql/c3_dev_db.sql`:24、`c9_prod_db.sql`:24）；`platform_integration` 基线已用「只检查存在」写法（:125-129）；workforce 基线 :243 仍 `CREATE EXTENSION IF NOT EXISTS`；测试建库脚本 `src/test/resources/init-schemas.sql`、`init-dba-schemas.sql` 都不建扩展，52 处 `TestPostgres.container()` 依赖 workforce 基线建出扩展。identity 基线（V…130000）先于 workforce（V…131000）执行。
- §4b 撤销清单（`one_db_roles_schemas.sql`:108-121）只有 workforce 五张版本表；`RoleSeparatedDeploymentIT.java`:155-156 断言其中两张。
- `ddl-naming-baseline.txt` 中与本卡相关的行：:17 `roles.access_scope`、:41 `account_roles…_user_id_fkey`、:48 `role_permissions…_role_id_fkey`、:49 `roles.roles_pkey`、:63 `uq_roles_code`、:64 `uq_roles_name`（ADR 写的 :45-46、:53-54 已漂移）。
- `unversioned-table-writes.txt`:15（`account_roles`）、:18（`role_permissions`）两行随表退役删除。
- 测试直接读写旧表：28 个文件（`account_roles` 25 个、`role_permissions` 6 个；ADR 估 14 个），其中 12 个在 `acceptance/` 目录（见 3.3）。
- `RULE_REVIEWS.json` RULE-345 修订已在，「先改评审结论」一步已完成。
- dev 库「首次部署前不执行任何迁移」（`runbooks/C-3-dev-db.md`:69），库内没有角色与授予数据。

## 3. 范围与卡上文件（越界先停下报告，不先做后报）

### 3.1 基线 DDL（改开放基线，不追加迁移，ADR-0038）
- `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql`（第 7 节全部）
- 不改其他 schema 的基线；workforce :243 的 `CREATE EXTENSION` 见待定 C-5

### 3.2 Java 与配置（PR-2、PR-3）
- `platform/identity/domain/`：`Role`、`UserAccount`、`RoleRepository`、`AccountRepository`、`AuthorizationRequest`、`AuthorizationRequestStatus`、`SensitivePermissions`；新增角色版本、角色授予、授予渠道、收回原因、变更渠道等值对象与枚举
- `platform/identity/application/`：`CreateRole`、`UpdateRole`、`DeleteRole`、`ReplaceAccountRoles`、`HandleOrgSnapshotChange`（离职与复职）、`AccountProvisioning`、`GrantRoleByOperations`、`RevokeRoleByOperations`、`ApplyConfirmedAuthorization`、`ConfirmAuthorizationRequest`、`AuthorizationGate`，以及第 2.3 节所列 19 个读取方中需要改调用的
- `platform/identity/infrastructure/persistence/`：`JdbcAccountRepository`、`JdbcRoleRepository`、`RoleRows`、`JdbcAuthorizationRequestRepository`；新增 `AuthorizationHistory`（只读仓储，按时刻还原）
- 新增系统角色声明与启动对账（`platform.identity` 内，包位置按 NAMING_STANDARD §3）
- `platform/identity/api/Permission.java`：加「已停用」标记（今天无停用码）
- `platform/identity/events/`：`AccountRolesReplaced`（加 `openedGrantIds`、`closedGrantIds`）、`RoleCreated`、`RoleRevised`、`RoleEnabled`、`RoleDisabled`（加 `roleVersionId`）；授权申请作废的事件或审计见待裁定 A-7
- `platform/audit/api/AuditLog.java` 与其实现：`record` 返回条目 ID（跨模块 api 变更，`platform.audit` 归属，须 one-coordinator 认可，见待定 C-6）
- `backend/src/main/resources/application*.properties|yml` 中 `forcitis.platform.identity.default-role` 不变

### 3.3 测试、守护与登记
- 新增守护与集成测试（第 4 节）
- 改写夹具（直接读写旧表的 16 个非验收测试文件）：`AdministratorModeIntegrationTest`、`BrowserIdentifierIntegrationTest`、`EmergencyLoginIntegrationTest`、`IdentityBaselineConstraintsIntegrationTest`、`IdentityIntegrationTest`、`OperationsCommandsIntegrationTest`、`RoleManagementIntegrationTest`、`SensitiveGrantTwoPersonIntegrationTest`、`SeparationRevokesRolesIntegrationTest`、`SessionLifecycleIntegrationTest`、`WecomLoginIntegrationTest`、`OperationsProfileSmokeTest`、`scenario/LegalEntityAttributionIntegrationTest`、`OrgUnitScopeIntegrationTest`、`SyncLockoutGuardIntegrationTest`、`WorkforceHttpIntegrationTest`
- **验收目录下 12 个文件**（`acceptance/AccountsAcceptanceSupport`、`P5Names`、`SingleColumnPrimaryKeysIT`、`acceptance/platform/identity/` 的 `OperationsCommandEmployeeIdIT`、`SingleOperatorEmergencyMinimumIT`、`SingleOperatorEndToEndIT`、`SingleOperatorIdentitySupport`、`SingleOperatorMarkTransactionIT`、`SingleOperatorModeEnvironment`、`SingleOperatorRevokeRoleIT`、`UserIdColumnsIT`、`acceptance/platform/integration/ExternalSubjectLookupCommandIT`）：实现者不改；由测试代理按门禁 §1.2 第 5 条「改动涉及的部分重写，验收代理判定」改写，单独提交
- 测试建库：`TestPostgres` 或 `src/test/resources/init-*.sql` 预建 `btree_gist`（ADR 迁移方案第 2 条「测试建库脚本同步」）
- 守护文件（单独提交）：`DataArchitectureTest`（D2 时刻变体）、`RoleSeparatedDeploymentIT`、`SingleAggregateGuardTest` 的映射、`RepositoryWriteGuardTest` 相关登记、`DdlNamingTest` 基线行
- 登记文件：`data-access-registry.yaml`、`ddl-naming-baseline.txt`（删 6 行）、`unversioned-table-writes.txt`（删 2 行，新表按需加理由）、`system-actors.txt`（启动对账的系统操作者）、`docs/modernization/phase1a/infra/one_db_roles_schemas.sql` §4b 及可执行版 `runbooks/sql/c3_dev_db_after_migrations.sql`（存在性待核）、`docs/modernization/audit/MUST_AUDIT_ACTIONS.yaml`、`docs/modernization/events/identity.yaml`、`docs/architecture/ddd/aggregates.yaml`、`backend/glossary/proposed/platform-identity.yaml`（提议术语）、权限目录快照文件（新建，名由实现定，守护第 8 条「只能标停用不能删除」）
- 文档（与实现同一提交，ADR 影响面）：`phase1a/SECURITY_INVARIANTS.md`（I-11 改句、I-11/I-12 注「持有」、新增 I-14 及验收对照）、`AI_NATIVE_SPEC.md`:25 C1a-08、`REIMAGINED_ARCHITECTURE.md` 与 `phase1a/DATA_SCORECARD.md` 中这几张表的描述、`docs/glossary/terms.yaml` 的 `separation` 定义改为「关闭全部有效授予」（经 one-glossary，`new_term_issue.py` 登记，不自行改正式词表）、`phase1a/NEXT_BATCH_PLAN.md` K-2 行范围说明

### 3.4 归属与不碰
- 本卡独占：`roles`、`role_versions`、`account_role_grants`、`authorization_requests` 的列与约束；退役 `role_permissions`、`account_roles`；系统角色声明；`AuthorizationHistory`
- 不碰：F-1 的自定义部门明细表、待调整权限表与 ADR-0032 的授予方式（随 F-1）；`organization_workforce` 任何表（DDL-17 后半见 C-4）；`platform_audit` 表结构；OpenAPI；权限码（不新增）；老角色迁入（ADR-0026 第 8 项，另卡）；季度复核与历史查询接口（ADR-0034 第 4 项第 5 条推迟）

## 4. 验收测试（由测试代理先写，§6.1 第 8 条）
- 测试代理：`code-modernization:test-engineer`（**opus**：安全不变量与并发），先于实现提交，提交说明带 `Acceptance-Test-Author: test-engineer`
- 路径：`backend/src/test/java/com/forcitis/one/acceptance/platform/identity/authorization_history/…`（子包名由测试代理定）；固定 `Clock`，确定性等待（CS-5.6），只断言 `platform.identity` 与其登记表（CS-5.5）
- 每条先红后绿：在本卡改动前的 main 上运行必须失败（或因表、类不存在而编译失败——此类在报告中注明），实现后转绿；Testcontainers 镜像限 `backend/architecture/test-images.txt`
- 测试名（`@DisplayName` 以编号开头，编号对 ADR-0034「需同步修改的守护规则」第 n 条）：

| 编号 | Given / When / Then | PR |
|---|---|---|
| 0034-G1-01 | 两张新表各有一行：以运行角色执行 DELETE、TRUNCATE，均被拒（触发器 `restrict_violation`；`RoleSeparatedDeploymentIT` 中为 `permission denied`） | 1 |
| 0034-G1-02 | 已关闭的授予行或角色版本：任何 UPDATE 被拒 | 1 |
| 0034-G1-03 | 开放行：改关闭列以外的列（如 `role_id`、`permission_codes`、`grant_channel_kind`）被拒；同时置 `effective_to_at` 与关闭列成功；再改一次被拒 | 1 |
| 0034-G1-04 | 开放授予：只置 `effective_to_at`、不写 `revocation_cause_kind` 与审计条目 ID，被成对 CHECK 拒 | 1 |
| 0034-G1-05 | 角色没有开放版本：插入该角色的授予被 `BEFORE INSERT` 触发器拒 | 1 |
| 0034-G1-06 | `roles` 行：改 `code`、`kind`、`is_administrative` 各被拒；改 `version`、`event_count` 成功 | 2 |
| 0034-G2-01 | `DataArchitectureTest` 注入样例：时刻版本表缺 `EXCLUDE`、缺 `isfinite` CHECK、`effective_from_at` 可空、类型为 `date`，各自失败；本卡两张表通过；§4b 清单与注册表 `DIMENSION_VERSIONED` 一致 | 1 |
| 0034-G2-02 | `permission_codes` 含 NULL 元素、含重复、未按字典序（C 排序）升序，三者各被 CHECK 拒；空数组与规范数组通过 | 1 |
| 0034-G2-03 | 性质测试 `GrantIntervalPropertyTest`：随机序列（开启、关闭、同刻开关、时钟回拨、快节点关闭后慢节点再开）回放到库：每人每角色至多一条开放行、无倒挂、不撞排他约束；任一时刻的还原等于回放得到的集合；角色版本按同一序列验证 | 3（角色版本部分 2） |
| 0034-G3-01 | 夹具含已关闭授予、开放授予、已关闭旧版本与当前版本：`SessionPrincipals`、`PrivilegeCeiling`、`ReplaceAccountRoles`、`UpdateRole`、`DeleteRole` 的 I-12 自查、`AdministratorSeparationReview`、`RoleHolders`、`EmergencyBinding`、`ReconcileEmergencyAdmins`、`EnterAdministratorMode`、`LoginAsEmergencyAdmin`、`LoginWithSignedTicket`、`ForceUserLogout`、`GetCurrentUserProfile`、`GetUserAccount`、`OrgUnitScopes`、`roleIsSensitive` 只反映开放授予与当前版本 | 3 |
| 0034-G3-02 | 时钟偏差：读取节点时钟早于新授予或新版本的开启时刻，新授予与新版本仍为当前，I-12 仍拒绝改自己持有的角色；读取时钟早于关闭时刻，已关闭授予仍不算有效 | 3 |
| 0034-G3-03 | 文本守护：`src/main` 中触及 `account_role_grants`、`role_versions` 的 SQL 要么引用当前有效谓词常量、要么属于 `AuthorizationHistory`；注入一条手写 `effective_to_at is null` 的 SQL 被拦下 | 3 |
| 0034-G4-01 | 同步标为离职：当前有效授予为零（含 `super_admin`、安全审计员）；授予行总数不变；每行原因 `SEPARATION`、带同步运行 ID 与审计条目 ID；每关一行一条 `platform.identity.account.role.revoked`，detail 含授予行 ID、原因、实际钟点；应急账号停用、会话吊销 | 3 |
| 0034-G4-02 | HELD 期间不关闭；放行后关闭 | 3 |
| 0034-G4-03 | 复职（`EmployeeReinstated` 事件触发，先核对目录当前状态，幂等）：重新标为在职后，只新开一条 `employee` 授予，渠道 `DEFAULT_ROLE`，写审计；离职前其他授予仍关闭（D-1a-17） | 3 |
| 0034-G5-01 | 固定时钟「开立即关」：同一时刻开启并关闭得到零长区间，关闭成功、会话吊销；该行不出现在任何时刻的还原中 | 3 |
| 0034-G5-02 | 时钟回拨到开启时刻之前再关闭：关闭时刻取开启时刻，成功，会话吊销，审计 detail 记实际钟点 | 3 |
| 0034-G5-03 | 角色版本：时钟回拨时新版本开始时刻不早于当前版本，也不早于已关闭版本的最大失效时刻 | 2 |
| 0034-G6-01 | `AuthorizationHistoryAsOfIntegrationTest`：t1 建角色（{A}、`SELF`）并授予；t2 改为 {A,B}、`ALL`；t3 离职；t4 复职；t5 管理员重新授予该角色：各区间内还原的角色、权限集合、范围类型逐项等于当时的值；t4 后只还原出 `employee`；t5 新开一行 | 3 |
| 0034-G6-02 | 早于最早记录的 `asOf`：失败并写 ALERT，不回退到最早版本 | 3 |
| 0034-G7-01 | `ReplaceAccountRoles` 由 {X,Y} 改为 {Y,Z}：只关闭 X、只开启 Z，Y 的授予行 ID 与开启时刻不变；事件带 `openedGrantIds`、`closedGrantIds` | 3 |
| 0034-G7-02 | 授权申请提交后，账号版本或任一相关角色的当前版本变化：确认后监听器在持锁事务内把申请转为 `VOIDED`、写作废审计，不停在 `CONFIRMED`、不改授予 | 3 |
| 0034-G7-03 | `UpdateRole` 每次修改恰好新增一个版本；内容不变不新增；删除仍被持有的角色返回 409 `PLATFORM_IDENTITY_ROLE_IN_USE`；删除后角色行与全部版本仍在、`GET /roles` 不再列出、新建同编码（任意大小写）被拒 | 2（持有判定的授予侧在 3 复测） |
| 0034-G7-04 | `RoleGrantLockIntegrationTest`：事务甲删除角色、事务乙同时授予该角色，只有一个成功，不会出现「已删除角色上有开放授予」 | 3 |
| 0034-G8-01 | `SystemRoleDeclarationIntegrationTest`：空库启动建出六个系统角色，`role_id` 等于声明中的固定值，版本 1 记声明修订号、渠道 `SEED`；重启不新增版本；修订号提高追加 `SEED` 版本；修订号更小告警不写；相等而内容不同启动失败；两实例同时启动结果相同；对账先于 `ApplicationRunner`、事件监听与重投、定时任务、HTTP 就绪 | 2 |
| 0034-G8-02 | 种子转声明对照：改前基线迁移后的「角色 → 权限码」与改后对账后的当前版本逐码相等（差异为空） | 2 |
| 0034-G8-03 | 迁移扫描：迁移文件写 `roles`、`role_versions` 数据即失败（注入一条 INSERT 被拦下） | 2 |
| 0034-G8-04 | 权限目录：测试库全部角色当前版本的码都存在且未停用；目录中的码只能标停用不能删除（快照比对）；运行时读到未知或已停用码，只忽略该码并告警，同一角色其他码照常生效、不抛异常 | 2 |
| 0034-G9-01 | `SingleAggregateGuardTest`：表到聚合映射含 `account_role_grants → UserAccount`、`role_versions → Role`；`multi-aggregate-exceptions.txt` 不变 | 2、3 |
| 0034-G10-01 | `RepositoryWriteGuardTest`、`TimeSourceGuardTest`（新 SQL 无数据库时钟，`greatest(:now, …)` 的 `:now` 为参数）、`PrimaryKeyTypeGuardTest`、`DdlNamingTest`、`MustAuditActionsTest`、`GlossaryTest` 全绿，`DdlNamingTest` 基线删 6 行后不报 stale | 1–3 |

- ADR 守护第 7 条第二个交错用例（扩大效力 × 授予）与第 8 条「首次部署后声明给有持有人的系统角色加双人逐人级码启动失败」依赖 ADR-0032 未实现部分，本卡是否承担见待裁定 A-8；未裁定前测试代理不写这两条。
- 第 11 条（ADR-0026、0027 守护中直接读写 `account_roles` 的测试）属夹具改写，见 3.3；「行数为零」改为「当前有效授予为零」，断言强度不降低，逐条列在 PR 中（CLAUDE.md 第 5 条）。
- 实现方认为验收测试有误：停下报告，由复审裁定（`Acceptance-Ruling: RULING-n`），不自行修改。

## 5. 执行者与模型
- 测试代理：`code-modernization:test-engineer`，opus（安全不变量、并发交错、性质测试）
- 实现：子代理，sonnet；PR-1 表结构可 sonnet
- 首轮复审：**opus 一次**，推理强度 high（安全项：授权历史、离职收回、锁协议、启动对账；§14、§14.1 全部条目）；修后复核 sonnet；最多两轮，之后新发现的非 High 在 PR 单列（ 第 6 条）
- 表结构 diff 证据由 opus 核对（ADR 迁移方案第 2 条）
- 不交 Codex：安全判定与并发协议
- 工作区：每个 PR 一个独立 worktree，分支 `claude/one-1a-par-adr0034-schema`、`claude/one-1a-par-adr0034-roles`、`claude/one-1a-par-adr0034-grants`（名字以负责会话为准），基线为当时的 main；并行子代理用同一基线（CS-7.6）

## 6. 禁止事项
- 不改卡外文件；不顺手重构、改名或统一风格；保留的约束、索引名字不改（`uq_roles_code`、`roles` 的不具名主键与 CHECK 归 R-2a）
- 不追加迁移文件；迁移不写 `roles`、`role_versions` 数据
- 不放宽、不删除断言；不改验收测试目录（验收目录的夹具改写只由测试代理做）；改断言逐条列理由
- 不新增权限码、不改 OpenAPI、不加历史查询接口
- 不在授予或角色版本表上做物理删除，任何代码路径都不 `DELETE` / `TRUNCATE` 这两张表（测试清库按 `NumberingFixtures` 先例在夹具内临时停用触发器，不给运行角色加回权限）
- SQL 不读数据库时钟；`:now` 一律由注入 `Clock` 传入；新表不写 `DEFAULT now()`
- 缺失或无效输入失败并告警（未知或已停用权限码除外：ADR-0034 第 1 项第 3 条明确只忽略该码并告警）
- 日志与审计 detail 只放 ID、枚举与时刻，不放姓名、工号、权限码以外的个人信息
- Testcontainers 临时容器（白名单镜像）可直接跑；其他容器、常驻容器、对外调用先问；不建账号；不打印、不写入密钥
- 用语遵守 `terms.yaml`；授予的结束一律称「收回」，不用「撤销」；领域模块不出现老系统名称

## 7. 预期表结构 diff（；ADR-0034 迁移方案第 2 条）
- 方法：两个空库分别执行改动前（该 PR 的基线 main）与改动后的基线，`pg_dump --schema-only` 对比；种子另做 `--data-only` 对比；注释差异一并列出。只能出现下列差异，多出或少了都退回（门禁 §1.4 第 1 条）。
- 三个 PR 各自一节；三节合起来即「`cfb04dd4` → 本卡完成」的总 diff，也是 F-1 的「改动前」。
- 新表时间列 `created_at`、`updated_at`、`effective_*_at` 全部由应用以注入时钟写入，DDL 无时钟默认值；`roles` 既有列的 `DEFAULT now()` 与业务默认值不在本卡改（归 R-4、R-2b），但本卡新写的 SQL 显式传入这些列。

### 7.1 PR-1 表结构（只扩，不破坏现有代码）

```sql
-- 文件开头（CREATE SCHEMA 之后）：排他约束需要 btree_gist，由建库脚本预建，迁移只检查存在（ADR-0034 影响面、DDL-28）。
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'btree_gist') THEN
        RAISE EXCEPTION 'extension btree_gist is missing: the database setup script (one_db_roles_schemas.sql) creates it before the migrations run';
    END IF;
END $$;

-- 放在 authorization_requests 之后（外键依赖顺序）。
CREATE FUNCTION platform_identity.is_normalized_code_set(codes text[]) RETURNS boolean LANGUAGE sql IMMUTABLE STRICT AS $$
    SELECT (array_ndims(codes) IS NULL OR array_ndims(codes) = 1)
       AND array_position(codes, NULL) IS NULL
       AND codes = ARRAY(SELECT c FROM (SELECT DISTINCT c FROM unnest(codes) AS c) AS d ORDER BY c COLLATE "C")
$$;
COMMENT ON FUNCTION platform_identity.is_normalized_code_set(text[]) IS '代码集合是否已规范化：一维、无空元素、无重复、按 C 排序升序；应用写入前先规范化（ADR-0034 第 1 项第 2 条）';

-- 两张授权历史表共用：开放行只能被关闭一次（只改 TG_ARGV 所列的关闭列），已关闭的行不能改，任何行不能删或清空。
CREATE FUNCTION platform_identity.refuse_history_rewrite() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP IN ('DELETE', 'TRUNCATE') THEN
        RAISE EXCEPTION 'authorization history is append-only: % on %.% is refused', lower(TG_OP), TG_TABLE_SCHEMA, TG_TABLE_NAME
            USING ERRCODE = 'restrict_violation';
    END IF;
    IF OLD.effective_to_at IS NOT NULL THEN
        RAISE EXCEPTION 'a closed row cannot change: %.%', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;
    IF NEW.effective_to_at IS NULL OR (to_jsonb(NEW) - TG_ARGV) <> (to_jsonb(OLD) - TG_ARGV) THEN
        RAISE EXCEPTION 'an open row can only be closed: %.%', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END $$;
COMMENT ON FUNCTION platform_identity.refuse_history_rewrite() IS '授权历史只追加：开放行只许一次性写入关闭列（参数列出），已关闭的行不能改，任何行不能删或清空（ADR-0034 第 1 项第 2 条、第 2 项第 2 条）';

CREATE TABLE platform_identity.role_versions (
    role_version_id             uuid        NOT NULL,
    role_id                     uuid        NOT NULL,
    version_number              integer     NOT NULL,
    effective_from_at           timestamptz NOT NULL,
    effective_to_at             timestamptz,
    role_name                   text        NOT NULL,
    role_status                 text        NOT NULL,
    access_scope_kind           text        NOT NULL,
    permission_codes            text[]      NOT NULL,
    change_channel_kind         text        NOT NULL,
    recorded_by                 uuid,
    change_reason               text        NOT NULL,
    authorization_request_id    uuid,
    declaration_revision_number integer,
    version_audit_entry_id      uuid        NOT NULL,
    closed_by                   uuid,
    closure_reason              text,
    closure_audit_entry_id      uuid,
    created_at                  timestamptz NOT NULL,
    updated_at                  timestamptz NOT NULL,
    CONSTRAINT pk_role_versions PRIMARY KEY (role_version_id),
    CONSTRAINT fk_role_versions_role_id FOREIGN KEY (role_id) REFERENCES platform_identity.roles (role_id) ON DELETE RESTRICT,
    CONSTRAINT fk_role_versions_authorization_request_id FOREIGN KEY (authorization_request_id)
        REFERENCES platform_identity.authorization_requests (request_id) ON DELETE RESTRICT,
    CONSTRAINT fk_role_versions_recorded_by FOREIGN KEY (recorded_by) REFERENCES platform_identity.user_accounts (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_role_versions_closed_by FOREIGN KEY (closed_by) REFERENCES platform_identity.user_accounts (user_id) ON DELETE RESTRICT,
    CONSTRAINT uk_role_versions_role_id_version_number UNIQUE (role_id, version_number),
    CONSTRAINT ck_role_versions_version_number CHECK (version_number >= 1),
    CONSTRAINT ck_role_versions_role_name CHECK (btrim(role_name) <> '' AND char_length(role_name) <= 100),
    CONSTRAINT ck_role_versions_role_status CHECK (role_status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_role_versions_access_scope_kind CHECK (access_scope_kind IN ('ALL', 'CUSTOM_ORG_UNITS', 'OWN_ORG_UNIT', 'OWN_ORG_UNIT_SUBTREE', 'SELF', 'SELF_AND_DIRECT_REPORTS')),
    CONSTRAINT ck_role_versions_permission_codes CHECK (platform_identity.is_normalized_code_set(permission_codes)),
    CONSTRAINT ck_role_versions_change_channel_kind CHECK (change_channel_kind IN ('SEED', 'MANUAL', 'MIGRATION')),
    CONSTRAINT ck_role_versions_change_reason CHECK (btrim(change_reason) <> '' AND char_length(change_reason) <= 500),
    CONSTRAINT ck_role_versions_seed CHECK ((change_channel_kind = 'SEED') = (declaration_revision_number IS NOT NULL)
        AND (change_channel_kind <> 'SEED' OR recorded_by IS NULL)),
    CONSTRAINT ck_role_versions_manual CHECK (change_channel_kind <> 'MANUAL' OR recorded_by IS NOT NULL),
    CONSTRAINT ck_role_versions_declaration_revision_number CHECK (declaration_revision_number IS NULL OR declaration_revision_number >= 1),
    CONSTRAINT ck_role_versions_closure CHECK ((closed_by IS NULL) = (closure_reason IS NULL)
        AND (closure_reason IS NULL) = (closure_audit_entry_id IS NULL)
        AND (closure_reason IS NULL OR effective_to_at IS NOT NULL)),
    CONSTRAINT ck_role_versions_closure_reason CHECK (closure_reason IS NULL OR (btrim(closure_reason) <> '' AND char_length(closure_reason) <= 500)),
    CONSTRAINT ck_role_versions_period CHECK (effective_to_at IS NULL OR effective_to_at >= effective_from_at),
    CONSTRAINT ck_role_versions_finite CHECK (isfinite(effective_from_at) AND (effective_to_at IS NULL OR isfinite(effective_to_at))),
    CONSTRAINT ex_role_versions_role_id_period EXCLUDE USING gist (role_id WITH =, tstzrange(effective_from_at, effective_to_at, '[)') WITH &&),
    CONSTRAINT ex_role_versions_role_name_period EXCLUDE USING gist (lower(role_name) WITH =, tstzrange(effective_from_at, effective_to_at, '[)') WITH &&)
);
CREATE INDEX ix_role_versions_role_id_current ON platform_identity.role_versions (role_id) WHERE effective_to_at IS NULL;
CREATE INDEX ix_role_versions_authorization_request_id ON platform_identity.role_versions (authorization_request_id) WHERE authorization_request_id IS NOT NULL;
CREATE INDEX ix_role_versions_recorded_by ON platform_identity.role_versions (recorded_by) WHERE recorded_by IS NOT NULL;
CREATE INDEX ix_role_versions_closed_by ON platform_identity.role_versions (closed_by) WHERE closed_by IS NOT NULL;
CREATE TRIGGER tg_role_versions_append_only BEFORE UPDATE OR DELETE ON platform_identity.role_versions
    FOR EACH ROW EXECUTE FUNCTION platform_identity.refuse_history_rewrite('effective_to_at', 'closed_by', 'closure_reason', 'closure_audit_entry_id', 'updated_at');
CREATE TRIGGER tg_role_versions_append_only_truncate BEFORE TRUNCATE ON platform_identity.role_versions
    FOR EACH STATEMENT EXECUTE FUNCTION platform_identity.refuse_history_rewrite();
COMMENT ON TABLE platform_identity.role_versions IS '角色版本：角色定义（名称、状态、范围类型、完整权限集合）的每次变化一行，只追加；当前版本 = effective_to_at 为空的那一行，每个角色至多一个；删除角色 = 关闭最后一个版本、不再开新版本（ADR-0034 第 1 项、RULE-345 修订）。粒度：每个角色的每个定义版本一行';
COMMENT ON COLUMN platform_identity.role_versions.role_version_id IS '角色版本 ID（UUIDv7，应用生成）';
COMMENT ON COLUMN platform_identity.role_versions.role_id IS '角色 ID';
COMMENT ON COLUMN platform_identity.role_versions.version_number IS '版本序号：同一角色从 1 起递增';
COMMENT ON COLUMN platform_identity.role_versions.effective_from_at IS '生效时刻（含）：greatest(注入时钟, 当前版本生效时刻, 该角色已有版本的最大失效时刻)';
COMMENT ON COLUMN platform_identity.role_versions.effective_to_at IS '失效时刻（不含）；为空表示当前版本。与生效时刻相等为零长区间，不出现在任何时刻的还原中';
COMMENT ON COLUMN platform_identity.role_versions.role_name IS '角色名称：同一时刻内不区分大小写唯一（排他约束），1 至 100 字';
COMMENT ON COLUMN platform_identity.role_versions.role_status IS '角色状态：ENABLED / DISABLED；只有 ENABLED 计入权限与数据范围（RULE-343/349）';
COMMENT ON COLUMN platform_identity.role_versions.access_scope_kind IS '数据范围类型：新角色默认 SELF（RULE-345）；CUSTOM_ORG_UNITS 的部门明细挂在本版本下（F-1）';
COMMENT ON COLUMN platform_identity.role_versions.permission_codes IS '完整权限集合：权限码 {子域}.{BC}:{资源}:{动作}，规范化存储（无空、无重复、C 排序升序）；按原文本保存，不随权限目录改写';
COMMENT ON COLUMN platform_identity.role_versions.change_channel_kind IS '变更渠道：SEED 系统角色声明对账 / MANUAL 角色管理界面 / MIGRATION 老角色迁入';
COMMENT ON COLUMN platform_identity.role_versions.recorded_by IS '记录人用户 ID；为空表示系统写入（SEED 必为空，MANUAL 必有）';
COMMENT ON COLUMN platform_identity.role_versions.change_reason IS '变更理由：SEED 写声明出处，MANUAL 写操作说明，1 至 500 字（来源见 ADR-0034-IMPL 待裁定 A-5）';
COMMENT ON COLUMN platform_identity.role_versions.authorization_request_id IS '授权申请 ID：经双人确认的版本必填；为空表示未经授权申请';
COMMENT ON COLUMN platform_identity.role_versions.declaration_revision_number IS '声明修订号：只有 SEED 版本有，单调递增；为空表示不是声明写入';
COMMENT ON COLUMN platform_identity.role_versions.version_audit_entry_id IS '写入本版本的同一事务中写下的审计条目 ID（跨 schema，只存 ID、不建外键）';
COMMENT ON COLUMN platform_identity.role_versions.closed_by IS '关闭人用户 ID：删除角色关闭最后版本时写入；为空表示未关闭，或被新版本接替（由接替版本记操作人）';
COMMENT ON COLUMN platform_identity.role_versions.closure_reason IS '关闭理由：删除角色时写入，1 至 500 字；为空表示未关闭或被新版本接替';
COMMENT ON COLUMN platform_identity.role_versions.closure_audit_entry_id IS '关闭（删除角色）的审计条目 ID（跨 schema）；为空表示未关闭或被新版本接替';
COMMENT ON COLUMN platform_identity.role_versions.created_at IS '创建时间（应用注入时钟）';
COMMENT ON COLUMN platform_identity.role_versions.updated_at IS '更新时间（应用注入时钟）：关闭时写入关闭时刻（见待裁定 A-4）';

CREATE TABLE platform_identity.account_role_grants (
    account_role_grant_id               uuid        NOT NULL,
    user_id                             uuid        NOT NULL,
    role_id                             uuid        NOT NULL,
    effective_from_at                   timestamptz NOT NULL,
    effective_to_at                     timestamptz,
    granted_by                          uuid,
    grant_channel_kind                  text        NOT NULL,
    grant_authorization_request_id      uuid,
    migration_batch_id                  uuid,
    grant_audit_entry_id                uuid        NOT NULL,
    revoked_by                          uuid,
    revocation_cause_kind               text,
    revocation_authorization_request_id uuid,
    revocation_separation_command_id    uuid,
    revocation_audit_entry_id           uuid,
    created_at                          timestamptz NOT NULL,
    updated_at                          timestamptz NOT NULL,
    CONSTRAINT pk_account_role_grants PRIMARY KEY (account_role_grant_id),
    CONSTRAINT fk_account_role_grants_user_id FOREIGN KEY (user_id) REFERENCES platform_identity.user_accounts (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_account_role_grants_role_id FOREIGN KEY (role_id) REFERENCES platform_identity.roles (role_id) ON DELETE RESTRICT,
    CONSTRAINT fk_account_role_grants_granted_by FOREIGN KEY (granted_by) REFERENCES platform_identity.user_accounts (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_account_role_grants_revoked_by FOREIGN KEY (revoked_by) REFERENCES platform_identity.user_accounts (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_account_role_grants_grant_authorization_request_id FOREIGN KEY (grant_authorization_request_id)
        REFERENCES platform_identity.authorization_requests (request_id) ON DELETE RESTRICT,
    CONSTRAINT fk_account_role_grants_revocation_authorization_request_id FOREIGN KEY (revocation_authorization_request_id)
        REFERENCES platform_identity.authorization_requests (request_id) ON DELETE RESTRICT,
    CONSTRAINT ck_account_role_grants_grant_channel_kind CHECK (grant_channel_kind IN ('MANUAL', 'DEFAULT_ROLE', 'OPERATIONS', 'MIGRATION')),
    CONSTRAINT ck_account_role_grants_revocation_cause_kind CHECK (revocation_cause_kind IS NULL
        OR revocation_cause_kind IN ('MANUAL', 'SEPARATION', 'OPERATIONS', 'MIGRATION_ROLLBACK')),
    CONSTRAINT ck_account_role_grants_grant CHECK ((grant_channel_kind = 'MANUAL') = (granted_by IS NOT NULL)
        AND (grant_channel_kind = 'MIGRATION') = (migration_batch_id IS NOT NULL)
        AND (grant_authorization_request_id IS NULL OR grant_channel_kind = 'MANUAL')),
    CONSTRAINT ck_account_role_grants_revocation CHECK ((effective_to_at IS NULL) = (revocation_cause_kind IS NULL)
        AND (revocation_cause_kind IS NULL) = (revocation_audit_entry_id IS NULL)
        AND (revocation_cause_kind IS DISTINCT FROM 'MANUAL' OR revoked_by IS NOT NULL)
        AND ((revocation_cause_kind IS NOT DISTINCT FROM 'SEPARATION') = (revocation_separation_command_id IS NOT NULL))
        AND (revocation_cause_kind IS DISTINCT FROM 'SEPARATION' OR revoked_by IS NULL)
        AND (revocation_authorization_request_id IS NULL OR revocation_cause_kind = 'MANUAL')
        AND (revoked_by IS NULL OR effective_to_at IS NOT NULL)),
    CONSTRAINT ck_account_role_grants_period CHECK (effective_to_at IS NULL OR effective_to_at >= effective_from_at),
    CONSTRAINT ck_account_role_grants_finite CHECK (isfinite(effective_from_at) AND (effective_to_at IS NULL OR isfinite(effective_to_at))),
    CONSTRAINT ex_account_role_grants_user_id_role_id_period EXCLUDE USING gist
        (user_id WITH =, role_id WITH =, tstzrange(effective_from_at, effective_to_at, '[)') WITH &&)
);
CREATE INDEX ix_account_role_grants_user_id_current ON platform_identity.account_role_grants (user_id) WHERE effective_to_at IS NULL;
CREATE INDEX ix_account_role_grants_role_id_current ON platform_identity.account_role_grants (role_id) WHERE effective_to_at IS NULL;
CREATE INDEX ix_account_role_grants_user_id_effective_from_at ON platform_identity.account_role_grants (user_id, effective_from_at);
CREATE INDEX ix_account_role_grants_migration_batch_id ON platform_identity.account_role_grants (migration_batch_id) WHERE migration_batch_id IS NOT NULL;
CREATE INDEX ix_account_role_grants_granted_by ON platform_identity.account_role_grants (granted_by) WHERE granted_by IS NOT NULL;
CREATE INDEX ix_account_role_grants_revoked_by ON platform_identity.account_role_grants (revoked_by) WHERE revoked_by IS NOT NULL;
CREATE INDEX ix_account_role_grants_grant_authorization_request_id ON platform_identity.account_role_grants (grant_authorization_request_id)
    WHERE grant_authorization_request_id IS NOT NULL;
CREATE INDEX ix_account_role_grants_revocation_authorization_request_id ON platform_identity.account_role_grants (revocation_authorization_request_id)
    WHERE revocation_authorization_request_id IS NOT NULL;

CREATE FUNCTION platform_identity.require_open_role_version() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM platform_identity.role_versions v WHERE v.role_id = NEW.role_id AND v.effective_to_at IS NULL) THEN
        RAISE EXCEPTION 'role % has no open version (deleted) and cannot be granted', NEW.role_id USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END $$;
COMMENT ON FUNCTION platform_identity.require_open_role_version() IS '新开授予前检查角色仍有当前版本：已删除的角色不能再授予（ADR-0034 第 2 项第 1 条）';
CREATE TRIGGER tg_account_role_grants_open_role_version BEFORE INSERT ON platform_identity.account_role_grants
    FOR EACH ROW EXECUTE FUNCTION platform_identity.require_open_role_version();
CREATE TRIGGER tg_account_role_grants_append_only BEFORE UPDATE OR DELETE ON platform_identity.account_role_grants
    FOR EACH ROW EXECUTE FUNCTION platform_identity.refuse_history_rewrite('effective_to_at', 'revoked_by', 'revocation_cause_kind',
        'revocation_authorization_request_id', 'revocation_separation_command_id', 'revocation_audit_entry_id', 'updated_at');
CREATE TRIGGER tg_account_role_grants_append_only_truncate BEFORE TRUNCATE ON platform_identity.account_role_grants
    FOR EACH STATEMENT EXECUTE FUNCTION platform_identity.refuse_history_rewrite();
COMMENT ON TABLE platform_identity.account_role_grants IS '角色授予：某账号在一段时间内持有某角色，只追加；收回 = 关闭开放行，再次获得新增一行；当前有效授予 = effective_to_at 为空；离职关闭全部当前有效授予（ADR-0034 第 2、3 项）。粒度：每个账号每次持有某角色一行';
COMMENT ON COLUMN platform_identity.account_role_grants.account_role_grant_id IS '角色授予 ID（UUIDv7，应用生成）';
COMMENT ON COLUMN platform_identity.account_role_grants.user_id IS '用户 ID：持有人账号';
COMMENT ON COLUMN platform_identity.account_role_grants.role_id IS '角色 ID';
COMMENT ON COLUMN platform_identity.account_role_grants.effective_from_at IS '生效时刻（含）：greatest(注入时钟, 此人此角色已有行的最大失效时刻)';
COMMENT ON COLUMN platform_identity.account_role_grants.effective_to_at IS '失效时刻（不含，即收回时刻）：greatest(注入时钟, 生效时刻)；为空表示当前有效。是系统实际收回的时刻，不是离职日期';
COMMENT ON COLUMN platform_identity.account_role_grants.granted_by IS '授予人用户 ID：有且只有 MANUAL 有；为空表示非界面授予（默认角色、运维命令、迁入；运维命令的操作人与见证人记在审计）。将来增加授予渠道时同步改 ck_account_role_grants_grant';
COMMENT ON COLUMN platform_identity.account_role_grants.grant_channel_kind IS '授予渠道：MANUAL 账号授权界面（含经双人确认的申请）/ DEFAULT_ROLE 建档或复职时的默认角色 / OPERATIONS 运维命令 grant-role / MIGRATION 老角色迁入';
COMMENT ON COLUMN platform_identity.account_role_grants.grant_authorization_request_id IS '授予所据的授权申请 ID；为空表示未经双人确认（只 MANUAL 可有）';
COMMENT ON COLUMN platform_identity.account_role_grants.migration_batch_id IS '迁入批次 ID（跨模块，只存 ID）：只 MIGRATION 有；为空表示不是迁入';
COMMENT ON COLUMN platform_identity.account_role_grants.grant_audit_entry_id IS '开启本行的同一事务中写下的审计条目 ID（跨 schema，只存 ID、不建外键）';
COMMENT ON COLUMN platform_identity.account_role_grants.revoked_by IS '收回人用户 ID：MANUAL 必有、SEPARATION 必为空；为空表示未收回或系统收回';
COMMENT ON COLUMN platform_identity.account_role_grants.revocation_cause_kind IS '收回原因：MANUAL 账号授权 / SEPARATION 离职 / OPERATIONS 运维命令 revoke-role / MIGRATION_ROLLBACK 迁入批次回退；为空表示未收回';
COMMENT ON COLUMN platform_identity.account_role_grants.revocation_authorization_request_id IS '收回所据的授权申请 ID；为空表示未收回或未经双人确认（只 MANUAL 可有）';
COMMENT ON COLUMN platform_identity.account_role_grants.revocation_separation_command_id IS '触发离职收回的离职命令 ID（跨 schema，只存 ID）：只 SEPARATION 有；为空表示不是离职收回';
COMMENT ON COLUMN platform_identity.account_role_grants.revocation_audit_entry_id IS '收回本行的审计条目 ID（platform.identity.account.role.revoked 等，跨 schema）；为空表示未收回';
COMMENT ON COLUMN platform_identity.account_role_grants.created_at IS '创建时间（应用注入时钟）';
COMMENT ON COLUMN platform_identity.account_role_grants.updated_at IS '更新时间（应用注入时钟）：关闭时写入关闭时刻（见待裁定 A-4）';

-- authorization_requests：基准两列（PR-1 只加可空 / 默认空数组的列，配对约束随 PR-3）。
-- 开放期直接改 CREATE TABLE，不写 ALTER：在列表中 permission_codes 之后加两列——
--     base_account_version_number bigint,
--     base_role_version_ids       uuid[]      NOT NULL DEFAULT '{}',
COMMENT ON COLUMN platform_identity.authorization_requests.base_account_version_number IS '基准账号版本：账号授权申请记申请时 user_accounts.version；为空表示不是账号授权申请';
COMMENT ON COLUMN platform_identity.authorization_requests.base_role_version_ids IS '基准角色版本：申请时相关角色的当前 role_version_id；账号授权申请为申请后集合与当时持有集合中每个角色，角色修改申请为该角色一个；新建角色为空数组（列名见待裁定 A-3）';
```

- 注册表、§4b、守护（非 DDL，同一 PR 单独提交）：两张新表登记（见数据设计「注册表条目草稿」；PR-1 中 `writers: []`，因 `DataArchitectureTest`:571-573 要求登记的写方有 SQL，PR-3 / PR-2 改为 `[platform.identity]`——待核 D-4）；§4b 撤销清单加两表；`DataArchitectureTest` D2 时刻变体；测试建库预建 `btree_gist`。
- **B 组（待 one-coordinator 确认并入，见 C-3；并入则其 diff 按 `data/repair-cards/DATA-REPAIR-identity.md` 对应条目「预期表结构 diff」原文追加到本节，并须 one-data 补签，这几条曾被排除在 PR-5 签发之外）**：ID-01（`ck_authorization_requests_decision` 新增、`ck_authorization_requests_two_people` 改写为 `request_status IN ('PENDING','REJECTED') OR (…)`，VOIDED 落在「非 PENDING、保留确认人」一侧）、ID-02（`ck_authorization_requests_kind_columns`）、ID-10（`fk_authorization_requests_requested_by`、`_decided_by`、`_subject_user_id` 及三索引；另加 `fk_authorization_requests_role_id` 与 `ix_authorization_requests_role_id`，删注册表 `fk_exceptions.role_id`：角色行不再删除，原豁免理由失效）、ID-13 中 `authorization_requests` 部分（`ck_authorization_requests_new_role_code`、`ck_authorization_requests_role_name`）。
- 不得出现：`roles`、`role_permissions`、`account_roles`、`user_accounts` 及其他表的任何变化；种子变化；B 组未获确认时 `authorization_requests` 除两列与两条注释外的变化。

### 7.2 PR-2 角色版本化与系统角色声明

```sql
CREATE TABLE platform_identity.roles (
    role_id           uuid        PRIMARY KEY,
    code              text        NOT NULL,
    kind              text        NOT NULL DEFAULT 'CUSTOM' CHECK (kind IN ('SYSTEM', 'CUSTOM')),
    is_administrative boolean     NOT NULL DEFAULT false,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    version           bigint      NOT NULL DEFAULT 0,
    event_count       bigint      NOT NULL DEFAULT 0,
    CONSTRAINT ck_roles_reserved_code CHECK (lower(code) <> 'super_admin' OR kind = 'SYSTEM'),
    CONSTRAINT ck_roles_code CHECK (code ~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$')
);
CREATE UNIQUE INDEX uq_roles_code ON platform_identity.roles (lower(code));
-- 删除：name、status、access_scope 三列及其注释；uq_roles_name。

CREATE FUNCTION platform_identity.refuse_role_identity_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.role_id IS DISTINCT FROM OLD.role_id OR NEW.code IS DISTINCT FROM OLD.code OR NEW.kind IS DISTINCT FROM OLD.kind
       OR NEW.is_administrative IS DISTINCT FROM OLD.is_administrative THEN
        RAISE EXCEPTION 'a role keeps its id, code, kind and administrative marker: %', OLD.role_id USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END $$;
COMMENT ON FUNCTION platform_identity.refuse_role_identity_change() IS '角色的身份列不可改：编码、类别、是否管理员角色写入后不变（ADR-0034 第 1 项第 1 条）';
CREATE TRIGGER tg_roles_identity_immutable BEFORE UPDATE ON platform_identity.roles
    FOR EACH ROW EXECUTE FUNCTION platform_identity.refuse_role_identity_change();
```
- 注释改：`roles` 表注释改为「角色：只存不可变的身份（编码、类别、是否管理员角色）；名称、状态、范围类型与权限集合在角色版本（role_versions）；删除角色只关闭最后一个版本，角色行保留，编码不得复用（RULE-345 修订、ADR-0034）。粒度：每个角色一行」；`roles.code` 注释「super_admin 为预留码，只能由迁移脚本建立」改为「…只能由系统角色声明建立」；`roles.is_administrative` 注释加「写入后不可改」。
- 删除整张 `role_permissions`（表、约束、索引、注释）。
- 种子：删除 :319-328、:338-380 中写 `roles`、`role_permissions` 的全部 INSERT 与其注释（`navigation_items` 种子 :330-336 保留）。种子内容原样搬进系统角色声明，六个 `role_id` 沿用 `019a0000-0000-7000-8000-000000000101` 至 `…106`，声明修订号 1；`--data-only` 对比后 `roles`、`role_permissions` 种子行消失，对账后的当前版本另做逐码对照（0034-G8-02）。
- 不得出现：`account_roles`、`role_versions`、`account_role_grants`、`authorization_requests`、`user_accounts` 的任何变化；`uq_roles_code`、`roles` 主键与两条既有 CHECK 的变化；`navigation_items` 种子变化。

### 7.3 PR-3 授予版本化、离职复职、授权申请基准

- 删除整张 `account_roles`（表、约束、索引、注释、前置注释行 :75）。
- `authorization_requests`：
  - `request_status` 的不具名列 CHECK 改为具名 `CONSTRAINT ck_authorization_requests_request_status CHECK (request_status IN ('PENDING', 'CONFIRMED', 'REJECTED', 'VOIDED'))`（重写这条 CHECK 时按 NAMING_STANDARD §2.4 具名；与 ADR「一个状态值」的字面差异见待裁定 A-11）；
  - 新增 `CONSTRAINT ck_authorization_requests_base CHECK ((request_kind = 'REPLACE_ROLES') = (base_account_version_number IS NOT NULL) AND (request_kind <> 'UPDATE_ROLE' OR cardinality(base_role_version_ids) = 1) AND (request_kind <> 'CREATE_ROLE' OR cardinality(base_role_version_ids) = 0))`；
  - 注释改：`request_status` 加「VOIDED 已作废：确认后执行前发现基准已变或已有持有人（ADR-0034 第 2 项第 5 条、ADR-0032 第 1 项第 3 条）」；表注释加「确认后由监听器在持锁事务内比对基准，不一致即作废」。
- **B 组（待 C-3）**：AQ-6 `role_codes text[]` 改 `role_ids uuid[]`（命名问题同 A-3）。
- 不得出现：`roles`、`role_versions`、`account_role_grants`、`user_accounts` 的任何变化；其他 schema 的变化。

### 7.4 三个 PR 合并后相对 `cfb04dd4` 的总 diff（核对 ADR 迁移方案第 2 条的五类，供 F-1 引用）
- `roles` 移出三列、删 `uq_roles_name`、加不可变触发器；
- 新建 `role_versions`、`account_role_grants` 及其约束、索引、触发器与三个函数；
- 删除 `role_permissions`、`account_roles`；
- `authorization_requests` 加两列、一个状态值（及 A-11 的具名、`ck_authorization_requests_base`、B 组）；
- identity 基线开头加 `btree_gist` 存在性检查（建库脚本已预建，不需改；测试建库同步）。
- F-1 草稿所依赖的列名与本卡一致：`role_versions.role_version_id`、`role_id`、`effective_to_at`、`access_scope_kind`；`authorization_requests.request_id`、`request_status`（含 `PENDING`）、`request_kind`、`access_scope_kind`；当前版本部分索引名 `ix_role_versions_role_id_current`；`role_version_custom_org_units` 的保留期写法跟随本卡 `role_versions` 条目（见数据设计「注册表条目草稿」与待核 D-1）。

## 8. 交付物与自查
- 交付物：基线 DDL（7.1–7.3）；Java（3.2）；系统角色声明与启动对账；`AuthorizationHistory`；`AuditLog.record` 返回 ID；事件字段；守护与测试（第 4 节）；登记与文档（3.3）；提议术语（第 11 节）
- 自查：`scripts/precheck.sh origin/main` 全部 PASS 或有理由的 SKIP，输出贴进报告与 PR「precheck.sh 输出」；`pre-commit install`；报告前 gitleaks 8.30.1 扫全历史
- 受影响模块测试：precheck 给出的命令；交付前全量 `scripts/ci/backend_verify.sh`（Testcontainers 白名单镜像）
- PR 正文单列：预期与实际表结构 diff（含 `--data-only`）；种子 → 声明逐码对照；签发行（DG-1）；改动的断言与夹具逐条及理由；守护文件改动（PR 正文单列、合并前 opus 复审，ADR-0032 Q4）；opus 首轮复审结论
- 报告分开写「已验证」与「未运行」

## 9. 复审要求（§6.1 第 4、11 条）
- Blocker / High：opus 复核，合并前修完；Medium / Low：执行者补测试、CI 通过即关闭，负责会话抽查；不阻塞合并的登记进下一批
- 首轮 opus（安全相关）：每个 PR 一次；PR-3 必查锁协议（`FOR UPDATE` / `FOR SHARE` 顺序与范围）、当前有效谓词常量的唯一性、离职关闭与会话吊销同事务语义、`VOIDED` 判定在持锁事务内、`greatest(:now, …)` 下界、审计条目 ID 回写
- 复审清单：DATA_DESIGN_CHECKLIST §14、§14.1（D2、D4、D6、D8、D12、D15、D16、D17 细则 2–5、D18、D19）；CODING_STANDARD CS-1.8、CS-1.9、CS-1.10（事件与 api 契约先行）、CS-2.24、CS-5.5、CS-5.6
- 专查：表结构 diff 只含第 7 节声明项；迁移不写角色数据；任何代码路径不删授权历史；未知权限码 fail-closed 不抛异常；对账时点先于 `ApplicationRunner` 与监听重投

## 10. 报告去向
- 完成、部分完成或阻塞，都向 one-1a-par，抄 one-coordinator（ 第 7 步）
- PR／卡／交接记录（消息按 TEAM-53 四节点及需动作事件送达，不另报号）：分支、改动文件、验证结果（已验证 / 未运行）、precheck 输出、预期与实际表结构 diff、未完成项、需要用户决定的事；PR-3 合并后通知 F-1（「改动前」基线就绪，待核 3 可核）与 one-data（复核表结构 PR、F-1 待核 3）

## 11. 本卡新词（开工前术语门禁；登记到 `backend/glossary/proposed/platform-identity.yaml`，交 one-glossary）
已查 `docs/glossary/terms.yaml` 与 `backend/glossary/proposed/*.yaml`：下列各词均无同名条目（`separation` 已批准，定义待改；`migration_batch` 在 `data-migration.yaml` 已提议，直接用）。

| 标识符 | 种类 | 中文名 | 使用范围 / bc | 说明 |
|---|---|---|---|---|
| `role_version` / `role_versions` | 概念、表 | 角色版本 | 领域 / `platform.identity` | ADR-0034 提议术语 |
| `account_role_grant` / `account_role_grants` | 概念、表 | 角色授予 | 领域 / `platform.identity` | 同上；结束称「收回」 |
| `grant_channel_kind` 及 `MANUAL`、`DEFAULT_ROLE`、`OPERATIONS`、`MIGRATION` | 列、枚举 | 授予渠道：账号授权、默认角色、运维命令、迁入 | 领域 / `platform.identity` | — |
| `change_channel_kind` 及 `SEED`、`MANUAL`、`MIGRATION` | 列、枚举 | 变更渠道：声明对账、角色管理、迁入 | 领域 / `platform.identity` | 与授予渠道的 `MANUAL`、`MIGRATION` 同名同义，互列 |
| `revocation_cause_kind` 及 `MANUAL`、`SEPARATION`、`OPERATIONS`、`MIGRATION_ROLLBACK` | 列、枚举 | 收回原因：账号授权、离职、运维命令、迁入回退 | 领域 / `platform.identity` | — |
| `effective_from_at` / `effective_to_at` | 列 | 生效时刻 / 失效时刻 | 共享内核 / `kernel`（K-2 时刻变体复用） | 与已有 `effective_from_date` / `effective_to_date` 互列 |
| `base_version`（`base_account_version_number`、`base_role_version_ids`） | 概念、列 | 基准版本：基准账号版本、基准角色版本 | 领域 / `platform.identity` | 列名见 A-3 |
| `VOIDED` | 状态值 | 已作废 | 领域 / `platform.identity` | 授权申请状态；与 ADR-0032「作废」共用 |
| `system_role_declaration`、`declaration_revision_number` | 概念、列 | 系统角色声明、声明修订号 | 领域 / `platform.identity` | 列名见 A-2 |
| `AuthorizationHistory` | 只读仓储 | 授权历史 | 领域 / `platform.identity` | — |
| `version_number`、`role_name`、`role_status`、`recorded_by`、`change_reason`、`closed_by`、`closure_reason`、`*_audit_entry_id`、`granted_by`、`revoked_by`、`revocation_separation_command_id` | 列 | 版本序号、角色名称、角色状态、记录人、变更理由、关闭人、关闭理由、审计条目、授予人、收回人、离职收回所据离职命令 | 领域 / `platform.identity` | 由已批准词组合；`role_name` 与 `roles.name` 退役同时生效 |
| 审计动作 `platform.identity.account.role.granted`、`platform.identity.role-version.opened`、`platform.identity.role-version.closed`、`platform.identity.authorization.voided` | 审计动作 | 角色已授予、角色版本已开启、角色版本已关闭、授权申请已作废 | 领域 / `platform.identity` | 前三个为本卡提议（A-6），第四个出自 ADR-0034 第 2 项第 5 条（与事件形式见 A-7） |

文档用语问题（`separation` 定义中「删除全部角色授予」改「关闭全部有效授予」）用 `new_term_issue.py` 登记给 one-glossary，不自行改正式词表。约束、索引、触发器、函数名按 NAMING_STANDARD §2.4 组合已有词，不属术语登记对象。

## 12. 顺序与 PR 拆分建议（一个工作单元一个 PR）
1. 本卡 → one-coordinator 看（待定 C 组）→ one-architecture 裁定 A 组 → one-data 审查并签发。
2. **PR-1 表结构（只扩）**：7.1 的 DDL；注册表两条（`writers: []`）、§4b、D2 时刻变体、测试建库预建 `btree_gist`、守护 0034-G1-01..05、G2-01..02 的库内部分。不含 Java 业务改动，现有测试应全绿；one-data 复核预期 diff；合并。
3. **PR-2 角色版本化与系统角色声明**：验收测试先提交（0034-G1-06、G2-03 角色部分、G5-03、G7-03、G8-01..04、G9、G10）；实现 7.2 与 `Role`、`RoleRows`、`JdbcRoleRepository`（`FOR UPDATE` 后关闭并插入版本；删除 = 关闭最后版本）、`CreateRole` / `UpdateRole` / `DeleteRole`、`roleIsSensitive` 读当前版本、权限目录停用标记、启动对账、`AuditLog.record` 返回 ID、Role 事件加 `roleVersionId`。授予仍在 `account_roles`（删除前占用检查暂读它，PR-3 换谓词）。
4. **PR-3 授予版本化、离职复职、授权申请基准、按时刻还原**：验收测试先提交（其余全部）；实现 7.3 与 `UserAccount`（持有当前授予行）、`JdbcAccountRepository`（只载当前有效、按差异关闭与插入、开启时对角色行 `FOR SHARE`、全部读取用同一谓词常量）、`ReplaceAccountRoles`、`HandleOrgSnapshotChange`（离职关闭、复职 `DEFAULT_ROLE`）、`AccountProvisioning`、运维命令两条、`ApplyConfirmedAuthorization`（持锁比对、`VOIDED`）、`AuthorizationHistory`、`AccountRolesReplaced` 字段、夹具改写、文档与 SECURITY_INVARIANTS I-14。F-13（D-1a-17）在本 PR 落地（C-2）。
5. 备选：若 one-coordinator 的「一个工作单元一个 PR」指整张卡一个 PR，则三步合为一个 PR、三段提交，表结构 diff 按 7.4 一次核对（门禁 §1.2 的「表结构先合并」需 one-data 顺序豁免）。与 ADR 守护节「与本 ADR 的实现在同一个 PR 中提交」的关系见 A-9。
6. PR-3 合并后：F-1 开工（其 7.1 的改动前基线就绪）；通知 one-data 复核 F-1 待核 3。

## 13. 待裁定、待定与待核

### 待 one-architecture 裁定（卡与已采纳 ADR 或审计字面不一致，不自行裁定）
- **A-1** **已裁定（one-architecture 2026-10-17）**：照 ADR，不加 RETIRED，“已删除”=无开放版本，DDL-17 前半由此满足。原问题：DDL-17 写「RETIRED 状态 + 关闭版本」；ADR-0034 第 1 项第 2、5 条：`role_status` 只有 `ENABLED / DISABLED`，删除 = 关闭最后版本、不开新版本，`roles` 行保留。本卡按 ADR（不加 `RETIRED`，「已删除」= 无开放版本），请确认 DDL-17 前半由此满足。
- **A-2** **已裁定（one-architecture 2026-10-17）**：用 `declaration_revision_number`，属实现落点。原问题：ADR 列名 `declaration_revision` 无类别词，`DdlNamingTest`（:42-79、:532）会拒；本卡写 `declaration_revision_number`。
- **A-3** **已裁定（one-architecture 2026-10-17）**：取 (a)：NAMING_STANDARD §2.4 增 `_ids`（uuid[]）类别词并同 PR 改 DdlNamingTest，文本改动报 one-architecture 过目。原问题：基准角色版本数组（ADR 第 2 项第 5 条「列名在实现 PR 中按 NAMING_STANDARD 定」）与 AQ-6 `role_ids`：`uuid[]` 在 §2.4 与 `DdlNamingTest` 都没有类别词（只有 `_codes text[]`）。选项：(a) §2.4 增 `_ids`（`uuid[]`）类别词并改守护；(b) 改为申请的子表（ADR 写的是「两列」）。本卡草拟 (a) `base_role_version_ids`。
- **A-4** **已裁定（one-architecture 2026-10-17）**：关闭时 `updated_at` 写关闭时刻（同 workforce 先例）。原问题：ADR「触发器只许改六个关闭列」（角色版本为四个关闭列），而 `DdlNamingTest`:628-632 要求每表有 `updated_at`。本卡草拟：关闭时 `updated_at` 一并写入关闭时刻（与 workforce `refuse_version_rewrite` 排除 `updated_at` 一致）；另一选项是 `updated_at` 恒等于 `created_at`（F-1 子表注释的写法）。
- **A-5** **已裁定（one-architecture 2026-10-17）**：应用写固定说明 + 授权申请 ID，不加接口字段，不放宽 NOT NULL。原问题：`role_versions.change_reason NOT NULL`（ADR 第 1 项第 2 条），但「OpenAPI 不变」（ADR 影响面），角色管理请求体 `RoleRequest` 没有原因字段（已核）。MANUAL 版本的理由从哪来：本卡草拟由应用写固定说明（如「角色管理：新建 / 修订 / 启用 / 停用」+ 授权申请 ID），不加接口字段；或放宽为 MANUAL 可空（会改 ADR 字面）。
- **A-6** **已裁定（one-architecture 2026-10-17）**：同事务直写审计并回写条目 ID，新增三个动作；对应事件标 @NotAudited，MUST_AUDIT_ACTIONS 同步改，避免双写。原问题：「同一事务写入的审计条目 ID」（授予开启、角色版本开启与关闭）：今天这些变更的审计由 `AuditDomainEvents` 监听事件、在另一事务写入（已核），业务事务内无条目 ID。本卡草拟：应用服务在同一事务内 `AuditLog.record` 直写一条并回写 ID，新增审计动作 `account.role.granted`、`role-version.opened`、`role-version.closed`；运维命令沿用 `operations.account.role.granted / revoked` 的条目。与事件审计并存造成同一变化两条审计，是否把相应事件标 `@NotAudited`，请裁定。
- **A-7** **已裁定（one-architecture 2026-10-17）**：照 ADR 直写 `platform.identity.authorization.voided`；同时发 `AuthorizationVoided` 事件给消费方，标 @NotAudited。原问题：作废的记录形式：ADR 写审计动作 `platform.identity.authorization.voided`；现行授权申请走领域事件审计（`platform.identity.AuthorizationConfirmed / Rejected`，`MUST_AUDIT_ACTIONS.yaml`:22-24）；DATA-REPAIR AQ-2 写事件 `AuthorizationVoided`。取直写审计、取事件，还是两者都要。
- **A-8** **已裁定（one-architecture 2026-10-17）**：接受：本卡交锁协议、当前持有人谓词与自建迁移扫描；两条用例与“首次部署后”判据移 F-1，卡与 ADR-0034 落地记录写明分阶段。原问题：ADR 守护第 7 条第二个交错用例、第 8 条「首次部署后声明给有持有人的系统角色加双人逐人级码启动失败」、第 8 条迁移扫描「扩展 ADR-0032 守护第 4 条」都依赖 ADR-0032 第 1 项第 3 条与第 2 项，而 ADR-0032 未实现、计划随 F-1 落地（NEXT_BATCH_PLAN:202）。本卡草拟：本卡交付锁协议与「当前持有人」谓词，迁移扫描本卡自建（只扫 `roles`、`role_versions`），上述两条用例移到 F-1；「首次部署后」的判据（运行时如何知道已首次部署）也须定。
- **A-9** **已裁定（one-architecture 2026-10-17）**：接受三 PR，条件：每个 PR 带本部分守护（守护随实现落地），卡内写明。原问题：ADR「需同步修改的守护规则」：「与本 ADR 的实现在同一个 PR 中提交」；本卡建议拆三个 PR，各 PR 带本部分守护（第 12 节）。是否接受。
- **A-10** **已裁定（one-architecture 2026-10-17）**：不改负载，在事件目录契约里写明信封 aggregateId 即同步运行 ID（即 ADR 所说“在契约中补上”）。原问题：ADR 第 3 项第 1 条「同步运行 ID 由 `OrgSnapshotChanged` 提供，现有负载没有该字段的，实现时在事件契约中补上」：负载确实没有，信封 `aggregateId` 就是同步运行 ID（`SyncRun.java`:240）。本卡草拟读信封、不改负载与事件契约。
- **A-11** **已裁定（one-architecture 2026-10-17）**：接受（具名与配对约束比字面更严，属实现落点）。原问题：ADR 迁移方案第 2 条把 `authorization_requests` 的差异限为「加两列与一个状态值」；本卡重写状态 CHECK 时按 NAMING_STANDARD §2.4 具名（`ck_authorization_requests_request_status`），并加 `ck_authorization_requests_base` 配对约束——超出字面。
- **A-12** **已裁定（one-architecture 2026-10-17）**：授权数据归 platform.identity 自有，出处 ADR-0034 与数据访问注册表，不进信息图。原问题：信息图（`architecture/proposals/ENTERPRISE_INFO_MAP_DRAFT.md`）没有角色、角色版本、角色授予的对象（同 F-1 A-1，F-1 卡已检索），门禁 B1 业务类表无对象即停。请补对象或给出平台授权对象的出处口径。
- **A-13** **已裁定并对齐（one-architecture 2026-10-17；契约 one-1a-next-5 卡 `WF-REINSTATE.md` §6、§6a）**：不靠授予历史推断。workforce 在“已落库的离职→在职”那次成功同步的事务内发布单独事件 `EmployeeReinstated`（载荷 `employeeId`、`effectiveFrom`，同步运行 ID 读信封，每人一个事件，去重键 eventId；不加 workforce api 方法，不改 `OrgSnapshotChanged` v1）。本卡 PR-3 的 identity 监听器用 `@ApplicationModuleListener`（提交之后、异步、可重投），先核对目录当前状态，账号当前在职且没有有效授予时才开 `DEFAULT_ROLE` 的 `employee`；离职分支乱序时也先核对当前状态再收回。PR-3 依赖 `WF-REINSTATE` 卡先合入；PR-1、PR-2 不依赖。原问题：复职的判定：ADR「同步把离职者重新标为在职时」；`OrgSnapshotChanged` 只给 `changedEmployees`，`OrgDirectory` 只给当前在职状态，没有「由离职转在职」的事实。本卡草拟：账号存在、当前在职、无当前有效授予，且该账号最近一次授予变化是 `SEPARATION` 关闭、其后无新开授予时，开 `DEFAULT_ROLE` 的 `employee`。离职时本无角色的账号（今天 `recordSeparation` 对空角色不写任何东西）复职时不会被识别——是否需要 workforce 提供「复职」事实，请裁定（或交 one-rules）。

### 待 one-coordinator 定
- **C-1** **已裁定（one-coordinator 2026-10-17）**：按台账“dev 前阻塞”口径（NEXT_BATCH_PLAN 第 8 节）：须在 dev 正式交用户使用前完成，不挡 rc.6 重建；DATA-REPAIR-identity 的“生产前闸门”表述同步改为一致。原问题：时点口径不一致：NEXT_BATCH_PLAN:587 与 ADR-0034 第 6 项「首次部署任何环境前」/「dev 前」；DATA-REPAIR-identity §6 写「R-1 `roles` 版本化 … 生产前闸门（用户「同意，不挡 dev」）」。以哪条为准（先问 one-data / one-ops 是否另有答复）。
- **C-2** **已裁定（one-coordinator 2026-10-17）**：F-13 与本卡同表（`account_role_grants`）同模块（identity）、建在授予版本化之上，**并入本卡 PR-3**，不留原卡。原问题：F-13（NEXT_BATCH_PLAN:133，复职重授）由本卡 PR-3 承担（ADR 第 3 项第 5 条、HANDOFF_one-1a-next:42「含复职 F-13」），F-13 状态改「并入 ADR-0034-IMPL」。
- **C-3** **已裁定（one-coordinator 2026-10-17）**：只承担落在角色 / 授予 / 版本表上的 ID 项，其余留在 DATA-REPAIR-identity。原问题：DATA-REPAIR-identity 的 ID-01、ID-02、ID-10、ID-13（授权申请部分）、AQ-2、AQ-6 是否由本卡承担（HANDOFF_one-1a-next-4:34「→ W2」，W2 不止本卡）；承担则 one-data 对这几条补签。
- **C-4** **已裁定（one-coordinator 2026-10-17）**：同表进本卡，不同表另起小卡并两卡互链（DDL-17 后半在 workforce 同步运行表，不同表，另起小卡）。原问题：DDL-17 后半（`ApplySourceSnapshot.java`:126 删除合并的同步运行，登记 `delete_reason`）属 `organization.workforce`，不在本卡；归哪张卡。
- **C-5** workforce 基线 :243 `CREATE EXTENSION IF NOT EXISTS btree_gist` 改为「只检查存在」：ADR 影响面写「各基线迁移」，NEXT_BATCH_PLAN:254 归 DDL-26 / DDL-28 卡。本卡只改 identity 基线与测试建库，workforce 那一行留给 DDL-28 卡。
- **C-6** **已裁定（one-coordinator 2026-10-17）**：`AuditLog.record` 返回条目 ID 是 `platform.audit` 的 api 变更，按契约先行（CS-1.10）单独一个小 PR 先合，并请 one-architecture 确认接口形状；接口形状已由 one-architecture 定：返回类型化 `AuditEntryId`、参数不变、向后兼容；不并入本卡三个 PR。原问题：`AuditLog.record` 返回条目 ID 是 `platform.audit` 的 api 变更（ADR 第 3 项第 3 条），由本卡改，需 audit 归属会话知悉。
- **C-7** K-2 时态基座的时刻变体（ADR 第 6 项第 2 条）尚无卡、未实现；本卡自行扩展 D2 守护的时刻变体，K-2 复用。
- **C-8** `permission-grant-modes.txt`（F-1 C-2「谁先用谁建」）：本卡不用（授予方式随 ADR-0032 / F-1），由 F-1 建。
- **C-9** **已裁定（one-coordinator 2026-10-17）**：拆成依次合入的三个 PR（DDL 与迁移 → 领域与应用 → 接口），每个能独立过 CI 与复审，不拆更细。原问题：PR 拆分：三 PR（第 12 节第 2–4 条）还是一 PR（第 5 条）。

### 待 one-data 核
- **D-1** 保留期写法：0034-Q2 已答「在线 3 年 + 归档到满 10 年」，但注册表 `{policy: 3y}` 必须指名已存在的 `@SchedulerLock` 作业（`DataArchitectureTest`:323-326），而 ADR 影响面写清理作业「3 年内不会有到期行，届时随审计在线删除一并实现」。本卡草拟 `{policy: PERMANENT}` 加注释写明 0034-Q2 口径与作业落点（与 `authorization_requests` 先例 `RETENTION_PROPOSAL.md`:20 同法）；F-1 的 `role_version_custom_org_units` 跟随。是否接受，或改 `gaps` 写法。
- **D-2** **已答（one-data 2026-10-17，`data/reviews/ADR-0034-IMPL.md` @35643e0f）**：接受，见该记录。原问题：`_by` 列（`recorded_by`、`closed_by`、`granted_by`、`revoked_by`）建同 schema 外键与部分索引（门禁 B4，与 ID-10 方向一致）；F-1 待核 5 用的是「不建外键、注册表 `references`」，两卡口径须统一。
- **D-3** **已答（one-data 2026-10-17，`data/reviews/ADR-0034-IMPL.md` @35643e0f）**：接受，见该记录。原问题：门禁 B7：`MIGRATION`、`MIGRATION_ROLLBACK` 取值与 `migration_batch_id` 列在老角色迁入实现前落库。出自已采纳 ADR-0034 第 1 项第 2 条、第 2 项第 1、3、4、6 条，按门禁文件头「与上位规则冲突时以上位规则为准」保留；请 one-data 确认不判 B7 不通过。
- **D-4** **已答（one-data 2026-10-17，`data/reviews/ADR-0034-IMPL.md` @35643e0f）**：接受，见该记录。原问题：PR-1 注册表 `writers: []`（`DataArchitectureTest`:571-573），PR-2 / PR-3 改为 `[platform.identity]`；或 PR-1 用 `optional` + `due`。
- **D-5** **已答（one-data 2026-10-17，`data/reviews/ADR-0034-IMPL.md` @35643e0f）**：接受，见该记录。原问题：本卡对 `granted_by` 的成对规则比 ADR 收紧：ADR 只写「MANUAL 须有」「OPERATIONS 为空」，本卡写「有且只有 MANUAL 有」（`DEFAULT_ROLE`、`MIGRATION` 也为空）。
- **D-6** **已答（one-data 2026-10-17，`data/reviews/ADR-0034-IMPL.md` @35643e0f）**：接受，见该记录。原问题：外键索引 G-DDL2：`account_role_grants.role_id` 只有部分索引（`WHERE effective_to_at IS NULL`），`role_versions.role_id` 由 `uk_role_versions_role_id_version_number` 前缀覆盖；`roles` 行不删除，外键 `RESTRICT` 检查不会发生。是否接受不建全量索引。

### 待核（开工前由实现者核实）
1. `runbooks/sql/c3_dev_db_after_migrations.sql` 的实际路径与 §4b 内容（C-3-dev-db.md:17 提到，未打开）。
2. 测试建库预建 `btree_gist` 的落点：`TestPostgres.container()` 统一加初始化，还是改两份 `init-*.sql`（无初始化脚本的容器也要覆盖，52 处调用）。
3. 启动对账放在单例初始化阶段的具体机制（`SmartInitializingSingleton` 等）与 Flyway 完成、`SingleOperatorModeStartup`、`OperationsCommandRunner` 的先后（ADR 第 1 项第 7 条），读 `infrastructure/startup/` 后定。
4. 测试清库停用触发器的写法：`NumberingFixtures` 的先例具体如何停用（未读其实现）。
5. `AccessCache` / `CachedSessionPrincipals` 的缓存失效点在按差异写入后是否仍覆盖全部路径。
6. 前端是否读取角色或授予的任何字段形状（OpenAPI 不变，预期无影响；未查前端）。

## 数据设计（DATA_DESIGN_GATE.md 附录 A）

数据设计：待审（one-data）。

### 涉数据
是：新建两表、改两表、退役两表（§1.1 第 1 项）；事件载荷加字段（第 2 项）；注册表与 §4b 登记（第 5 项）。无读模型、无迁入映射、无指标。

### 表 → 出处（B1、B2）

| 表 | 出处 | 聚合（schema） | 注册表 role |
|---|---|---|---|
| `platform_identity.roles`（改） | 既有表；信息图无对象（A-12）；规格：RULE-345 及修订、ADR-0034 第 1 项第 1 条 | `Role` | `AGGREGATE_ROOT`（不变） |
| `platform_identity.role_versions`（新） | 信息图无对象（A-12）；ADR-0034 第 1 项第 2 条；§14.1 D17 | `Role` 的版本子行 | `DIMENSION_VERSIONED`（ADR 影响面） |
| `platform_identity.account_role_grants`（新） | 信息图无对象（A-12）；ADR-0034 第 2 项；D17 细则 2「授权关系」 | `UserAccount` 的子行 | `DIMENSION_VERSIONED`（ADR 影响面） |
| `platform_identity.authorization_requests`（改） | 既有表；ADR-0034 第 2 项第 5 条、ADR-0032 第 1 项第 3 条 | `AuthorizationRequest` | 不变 |
| `platform_identity.role_permissions`（退役） | 被 ADR-0034 第 1 项第 3 条取代 | — | 删条目 |
| `platform_identity.account_roles`（退役） | 被 ADR-0034 第 2 项第 1 条取代 | — | 删条目 |

同一聚合的表在同一 schema；`aggregates.yaml` 的 `platform.identity/Role`、`/UserAccount` 的 `tables` 更新。

### 状态、关系、基数落点（B3、B3a、B4）
- 角色版本状态 `ENABLED / DISABLED` = `RoleStatus` 枚举（`domain/RoleStatus.java`）= `ck_role_versions_role_status`；「已删除」不是状态值，是「无开放版本」（A-1）。转换 ↔ 事件：新建 → `RoleCreated`（版本 1）；修订 → `RoleRevised`；启用 / 停用 → `RoleEnabled` / `RoleDisabled`；删除 → `RoleDeleted`（关闭最后版本）。审计动作见 A-6。
- 授权申请状态：`PENDING → CONFIRMED / REJECTED`、`CONFIRMED → VOIDED`；`CHECK` 取值 = `AuthorizationRequestStatus` 枚举 = 状态机（G-D12 同一测试）。事件：`AuthorizationOpened`、`AuthorizationConfirmed`、`AuthorizationRejected`、`AuthorizationVoided`（A-7 已裁）。`VOIDED` 无原因列：原因（基准已变 / 已有持有人）只进审计 detail（ADR 第 2 项第 5 条）。
- 授予无状态列：开放 / 已收回由 `effective_to_at` 表达；收回原因 `revocation_cause_kind` 成对（`ck_account_role_grants_revocation`）。
- 关系：版本 → 1 角色（`NOT NULL` 外键）；版本 → 0..1 授权申请、0..1 记录人、0..1 关闭人（可空外键）；授予 → 1 账号、1 角色（`NOT NULL` 外键）、0..1 授予人 / 收回人、0..1 开启 / 收回申请（可空外键）；迁入批次、离职命令、审计条目跨 schema 只存 ID。

### 不变量 → 约束（B5、O4）

| # | 不变量（来源） | 承担者 | 落不成约束的理由 |
|---|---|---|---|
| 1 | 同一角色同一时刻至多一个版本，至多一个当前版本（ADR 1.2） | `ex_role_versions_role_id_period` | — |
| 2 | 同一时刻角色名称不区分大小写唯一（ADR 1.2，取代 `uq_roles_name`） | `ex_role_versions_role_name_period` | — |
| 3 | 版本序号每角色唯一、从 1 起 | `uk_role_versions_role_id_version_number`、`ck_role_versions_version_number`；递增由仓储 | 连续递增跨行 |
| 4 | 区间不倒挂、允许零长、有限值（ADR 1.2、2.1） | `ck_*_period`、`ck_*_finite`（两表） | — |
| 5 | 版本行与授予行只追加、只许关闭一次、不删不清空（ADR 1.2、2.2；D8、D17） | `tg_*_append_only`、`tg_*_append_only_truncate`；运行角色无 `DELETE / TRUNCATE`（§4b） | — |
| 6 | 权限集合规范化（ADR 1.2） | `ck_role_versions_permission_codes` | — |
| 7 | 权限码在目录中存在且未停用（写入时） | 应用按 `Permission` 校验；生产启动对账告警；测试库守护 0034-G8-04 | 目录在代码中，历史版本保留原文本 |
| 8 | SEED ⇔ 有声明修订号、无记录人；MANUAL 有记录人（ADR 1.2） | `ck_role_versions_seed`、`ck_role_versions_manual` | — |
| 9 | 关闭列同空同非空、只在已关闭时有值 | `ck_role_versions_closure` | — |
| 10 | 经双人确认的版本必带申请 ID | 应用（`ApplyConfirmedAuthorization` 路径） | 是否经确认取决于敏感判定，不在库内 |
| 11 | `roles` 的编码、类别、是否管理员不可改（ADR 1.1） | `tg_roles_identity_immutable` | — |
| 12 | 被删除角色的编码不得复用（0034-Q3） | `uq_roles_code`（角色行保留） | — |
| 13 | 同一人同一角色至多一条开放授予、区间不重叠（ADR 2.1） | `ex_account_role_grants_user_id_role_id_period` | — |
| 14 | 已删除角色不能新开授予（ADR 2.1） | `tg_account_role_grants_open_role_version`；并发由 `FOR SHARE` / `FOR UPDATE`（ADR 1.6） | — |
| 15 | 授予渠道与授予人、迁入批次、申请配对（ADR 2.1） | `ck_account_role_grants_grant`（D-5） | — |
| 16 | 收回列成对；MANUAL 有收回人；SEPARATION 有离职命令、无收回人（ADR 2.1） | `ck_account_role_grants_revocation` | — |
| 17 | 新开授予下界 = `greatest(:now, max(effective_to_at))`；新版本下界同理（ADR 1.6、2.2） | 仓储 SQL；排他约束兜底拒绝重叠 | 下界依赖同组已有行 |
| 18 | 删除角色前无当前有效授予（ADR 1.5） | 仓储在 `FOR UPDATE` 锁内计数，返回 409 | 跨表计数 |
| 19 | 不能改自己持有的角色、不能改自己持有角色的定义（I-12，按当前有效授予） | 应用 | 涉调用人 |
| 20 | 离职后无当前有效授予（I-14） | `HandleOrgSnapshotChange`；0034-G4-01 | 跨模块事件 |
| 21 | 账号授权申请记基准账号版本，角色修改申请记一个基准版本（ADR 2.5） | `ck_authorization_requests_base`（PR-3） | — |
| 22 | 基准变了即作废，不应用 | 监听器在持锁事务内比对；0034-G7-02 | 跨聚合读 |
| 23 | 迁移不写角色数据（ADR 1.7） | 迁移扫描守护 0034-G8-03 | 静态扫描 |
| 24 | 读取当前有效只用一个谓词常量（ADR 4.2） | 文本守护 0034-G3-03 | 代码结构 |

### 用例 → 聚合 → 表（O1）

| 用例 | 聚合 | 写的表 |
|---|---|---|
| 新建 / 修订 / 启用 / 停用 / 删除角色（含确认后的应用） | `Role` | `roles`（行锁 + 乐观锁）、`role_versions` |
| 系统角色启动对账（逐个角色提交） | `Role`（同类型多实例，逐个事务） | `roles`、`role_versions` |
| 账号授权（替换集合，按差异）、运维 `grant-role` / `revoke-role`、建档默认角色、复职默认角色、离职收回 | `UserAccount` | `user_accounts`（乐观锁）、`account_role_grants`；对所涉 `roles` 行只 `FOR SHARE`、不写 |
| 提交 / 确认 / 拒绝 / 作废授权申请 | `AuthorizationRequest` | `authorization_requests`；作废在执行监听器事务中，写申请不写授予 |
| 离职处理（一个监听事务多名离职者） | `UserAccount`（同类型多实例） | 同上 |

不新增多聚合事务，`multi-aggregate-exceptions.txt` 不变；作废与「应用确认」在同一监听事务内二选一（要么作废写申请，要么应用写账号或角色），由复审核对 `SingleAggregateGuardTest` 结论（ADR 守护第 9 条）。

### 业务键、数据类别与时态（O3、O6）

| 表 | 业务键（出处） | 类别（D17 细则 1） | 版本化方式 |
|---|---|---|---|
| `roles` | 角色编码不区分大小写唯一，含已删除（RULE-345 修订） | 主数据（授权实体） | 根表只留不可变列；可变定义在 `role_versions`（D17 细则 5「根表不存可版本化的列」）；`code`、`kind`、`is_administrative` 不进版本，理由 ADR 1.1（细则 3 逐列理由写进注册表注释） |
| `role_versions` | 同一角色同一时刻一个版本；同一时刻名称唯一（ADR 1.2） | 主数据 | 维度型，`timestamptz` 区间，生效依据 `DECLARED`（新系统自有声明，不是同步观察），一个来源（`platform.identity`）、一个负责人 |
| `account_role_grants` | 同一账号同一角色同一时刻至多一条（ADR 2.1） | 主数据（授权关系） | 同上；当前值 = 未关闭行（细则 5「授权取未关闭的行」）；早于最早记录的查询失败告警（0034-G6-02） |
| `authorization_requests` | 不变（单据，只有 ID） | 交易 | 不属 D17 |

不登记 `CONFIG_VERSIONED`，理由照 ADR 影响面（无生效日、非敏感改动无确认人、决定出处由 `change_reason` 与申请承担）。

### 查询路径（O10、O10a）
预期行数为估算（未取证）：约 1,300 人；角色数十个；授予 10 年 < 5 万行；角色版本 10 年 < 5,000 行。

| 路径 | 过滤列 | 排序列 | 预期行数 | 分页 | 索引 | 预算 |
|---|---|---|---|---|---|---|
| 每请求权限与范围：某人当前有效授予及其角色当前版本 | `g.user_id = :u AND g.effective_to_at IS NULL`，联 `v.role_id = g.role_id AND v.effective_to_at IS NULL` | — | 每人个位数 | 不分页（上限 1,000 断言） | `ix_account_role_grants_user_id_current`、`ix_role_versions_role_id_current` | 随请求，缓存为主 |
| 某角色的当前持有人（HELD、放行下限、应急账号核对、管理模式、`RoleHolders`、删除前占用） | `g.role_id = :r AND g.effective_to_at IS NULL` | — | 每角色 ≤ 数百 | 不分页（上限 1,000） | `ix_account_role_grants_role_id_current` | 命令 200 ms |
| 角色列表（当前版本） | `v.effective_to_at IS NULL` | `r.code` | 数十 | 小表上限 1,000（K-3b-2） | `ix_role_versions_role_id_current` | 列表 300 ms |
| 角色详情 / 修订前读当前版本（`FOR UPDATE` 锁 `roles` 行） | `role_id = :r` | — | 1 | — | 主键、`ix_role_versions_role_id_current` | 详情 200 ms |
| 新开授予前的下界 | `user_id = :u AND role_id = :r`，取 `max(effective_to_at)` | — | 每对个位数 | — | `ex_account_role_grants_user_id_role_id_period`（gist，前导列 `user_id`）或 `ix_account_role_grants_user_id_effective_from_at` | 命令 |
| 按时刻还原（`AuthorizationHistory`） | `g.user_id = :u AND tstzrange(...) @> :asOf`，联版本同谓词 | — | 每人个位数 | 不分页（上限 1,000） | `ix_account_role_grants_user_id_effective_from_at`；版本经 `ex_role_versions_role_id_period` | 无接口，仓储测试 |
| 迁入批次回退 | `migration_batch_id = :b AND effective_to_at IS NULL` | — | 批次级 | 按批 | `ix_account_role_grants_migration_batch_id` | 作业 |
| 声明对账 | `role_id = :id`（固定六个） | — | 6 | — | 主键、`ix_role_versions_role_id_current` | 启动 |

热点：当前行占全表比例随时间下降至远低于 10%，故当前读取用部分索引（门禁 O10）。

### 横切口径、法人与期间（O14、O13）
- 时区与时钟：全部 `_at` 列 `timestamptz`，值来自注入 `Clock`，SQL 以参数传入；`greatest(:now, …)` 的 `:now` 为参数（`TimeSourceGuardTest`）；实际钟点写审计 detail。
- 并发：`roles.version`、`user_accounts.version` 乐观锁；写锁协议 ADR 1.6（修订 / 删除 `FOR UPDATE`，开启授予按 `role_id` 顺序 `FOR SHARE`）；对账同一行锁。
- 幂等：离职监听至少一次投递，重投时无开放授予即无事可做；复职判定见 A-13。
- 分页：不新增列表接口；小表上限沿用 K-3b-2。
- 保留期：0034-Q2（在线 3 年 + 归档到满 10 年），写法见 D-1。
- 法人（O13）：不带法人 ID，授权数据登记 `GLOBAL`（ADR-0026 第 1 项；1b 的 `legal_entity_scope` 随 ADR-0026 第 2 项加在版本上，本卡不建，门禁 B7）。不受期间闸门约束。A6 不适用。
- 金额：不涉及。

### 适用的 ADR 条款对照（O15）

| ADR 条款 | 规定 | 预期 diff 位置或「不适用（理由）」 |
|---|---|---|
| 0034 第 1 项第 1 条 | `roles` 只留不可变列 + 触发器 | 7.2 |
| 0034 第 1 项第 2 条 | `role_versions` 全部列与约束、关闭列、触发器、不建 GIN | 7.1（列名差异 A-2；`updated_at` A-4） |
| 0034 第 1 项第 3 条 | 数组不建子表；目录停用、fail-closed | 7.1；代码（`Permission` 停用标记） |
| 0034 第 1 项第 4–6 条 | 改定义 = 新增版本；删除 = 关闭最后版本；时刻与锁 | 代码；不变量 17、18 |
| 0034 第 1 项第 7 条 | 系统角色代码声明、启动对账、迁移不写 | 7.2 删种子；代码；守护 0034-G8 |
| 0034 第 2 项第 1–4 条 | `account_role_grants` 全部列、约束、索引、渠道与原因 | 7.1 |
| 0034 第 2 项第 5 条 | 按差异、基准版本、`VOIDED` | 7.1 两列、7.3 状态值与配对；代码 |
| 0034 第 2 项第 6 条 | 迁入批次只新增、回退关闭 | 列与索引已含；迁入代码不在本卡 |
| 0034 第 2 项第 7 条 | 授予是 `UserAccount` 子行 | 注册表 `aggregate` |
| 0034 第 3 项 | 离职关闭、逐条审计、复职 `DEFAULT_ROLE` | 代码；`AuditLog.record` 返回 ID |
| 0034 第 4 项 | 当前有效 = 未关闭；按时刻还原；只交付仓储 | 部分索引；`AuthorizationHistory`；OpenAPI 不变 |
| 0034 第 5 项 | 范围明细挂角色版本 | 不适用（F-1；本卡给出 `role_version_id` 与当前版本索引） |
| 0034 第 6 项 | 首次部署前；K-2 时刻变体；在 F-1、F-13 前 | 第 12 节；C-1、C-7 |
| 0034 影响面「数据库运行角色」 | §4b 加两表；清理作业后做 | 3.3；D-1 |
| 0034 影响面「btree_gist」 | 建库预建、迁移只检查 | 7.1 开头；C-5 |
| 0033 第 1 项第 1、3、4 条（提议，ADR-0034 引用） | UUIDv7 主键、两列 `_at` + `tstzrange` 表达式、数组 | 7.1 |
| 0026 第 1 项（`GLOBAL`） | 授权表 `GLOBAL` | 注册表 |
| 0027 第 1 项第 6、7 条（经 0034 取代「删除」） | 离职关闭、HELD 不关、逐条审计 | 代码；0034-G4 |
| 0032 第 1 项第 3 条、第 2 项 | 有持有人不得扩大效力；授予方式 | 不适用于本卡 DDL；锁协议在本卡，判定随 F-1（A-8） |
| 0038 | 开放期改基线、附预期 diff | 第 7 节 |
| 0043 | 分级、代理键不登记 | 分级表 |
| 0048 | 审计条目只按 ID 引用、无表指向它 | `*_audit_entry_id` 不建外键 |

### 值对象（O16）
`RoleVersionId`、`AccountRoleGrantId`（UUID）；`PermissionCodeSet`（规范化集合，构造处排序去重、拒空元素）；`GrantChannel`、`RevocationCause`、`ChangeChannel`（枚举）；`DeclarationRevision`（≥ 1）。名字由实现定，按 `terms.yaml`。无金额、工时。

### `detail` / 自由文本的允许写入内容（C4）

| 位置 | 允许写入 | 来源 | 可能含个人信息 |
|---|---|---|---|
| `role_versions.change_reason` | SEED：声明出处（类名与修订号）；MANUAL：固定操作说明 + 申请 ID（A-5）；MIGRATION：批次 ID | 系统 | 否 |
| `role_versions.closure_reason` | 删除角色的固定说明 + 操作人账号 ID 以外不写 | 系统（A-5 同口径） | 否 |
| 审计 `account.role.granted` detail | 授予行 ID、角色 ID、渠道、申请 ID、实际钟点 | 系统 | 只有代理键 |
| 审计 `account.role.revoked` detail | 授予行 ID、角色 ID、原因、离职命令 ID、实际钟点 | 系统 | 只有代理键 |
| 审计 `role-version.opened / closed` detail | 版本 ID、角色 ID、版本序号、渠道、声明修订号、实际钟点 | 系统 | 否 |
| 审计 `authorization.voided` detail | 申请 ID、原因（`base-changed` / `has-holders`）、基准与实际的版本号或版本 ID | 系统 | 否 |
| 对账 ALERT 日志 | 角色 ID、声明修订号与库中修订号、未知或停用权限码 | 系统 | 否 |

运维命令的自报操作人与见证人仍按 ADR-0043 U-7b 例外写在既有 `operations.*` 审计中，本卡不新增。

### 注册表条目草稿（C2、C3、C5）

```yaml
  platform_identity.role_versions:
    # 保留：0034-Q2 在线 3 年、归档到满 10 年。清理作业落点：随审计在线删除作业实现（ADR-0034 影响面），届时改 {policy: 3y, job: …}。负责人：one-1a-par。期限：首次生产部署后 30 个月内。清理按依赖顺序（先 account_role_grants、后 role_versions，再其引用的行）（D-1，one-data 2026-10-17 接受）。
    # D17 细则 2：维度型，生效依据 DECLARED。roles 的 code、kind、is_administrative 不进版本：决定角色本质、无修改路径（ADR-0034 第 1 项第 1 条）。
    role: DIMENSION_VERSIONED
    aggregate: Role
    mutability: APPEND_ONLY
    retention: {policy: PERMANENT}
    lineage: {source: NEW}
    references: {recorded_by: platform_identity.user_accounts, closed_by: platform_identity.user_accounts}
    writers: [platform.identity]      # PR-1 为 []（D-4）
  platform_identity.account_role_grants:
    # 保留同上。生效依据 DECLARED；当前值 = 未关闭行。
    role: DIMENSION_VERSIONED
    aggregate: UserAccount
    mutability: APPEND_ONLY
    retention: {policy: PERMANENT}
    lineage: {source: NEW}
    references: {granted_by: platform_identity.user_accounts, revoked_by: platform_identity.user_accounts}
    writers: [platform.identity]      # PR-1 为 []
```
- 删除 `platform_identity.role_permissions`、`platform_identity.account_roles` 两条；`roles` 条目不变；`authorization_requests` 的 `fk_exceptions.role_id` 随 B 组 ID-10 删除。
- `readers` 无：其他模块经 `RoleDirectory`、`CurrentAccess` 等 api 读，不直读表。
- `scope_kind`（F-1 新增字段）由 F-1 补 `GLOBAL`。

### 分级表（C4）

| 列 | 访问级别 | 个人信息标记 | 理由 |
|---|---|---|---|
| `role_versions.role_name`、`role_status`、`access_scope_kind`、`permission_codes`、`change_channel_kind`、`change_reason`、`closure_reason` | INTERNAL | NONE | 用途：角色定义与变更出处，系统写入，无个人信息 |
| `account_role_grants.grant_channel_kind`、`revocation_cause_kind` 与全部 `_at` 列 | INTERNAL | NONE | 用途：授权历史还原与审计取证 |
| 两表全部 `_id`、`_by` 列 | 不登记（代理键，ADR-0043 第 2 项） | 出口按 NONE；持有人账号 ID 法律属性为去标识化个人信息（ADR-0043 第 4 项第 2 条） | 代理键 |

`data-classification.txt` 不新增行（无 `SENSITIVE` / `RESTRICTED` 列，未登记默认 `INTERNAL`）；门禁 C4 若要求每个非代理键新列登记，按上表补行（同 F-1 待核 4）。

### 表注释与列注释草稿（C1）
见第 7 节 DDL；用语取自 `terms.yaml` 与第 11 节提议术语。

### 分析事实（A1–A8）
无分析事实：授予与角色版本是授权主数据，不是业务过程事实；季度授权复核报表推迟（ADR-0026 第 6 项第 3 条、ADR-0034 第 4 项第 5 条）。A6 不适用。

### 基线 / 目标 / 过渡 / 回滚（T1–T4）
- T1 基线：`roles`（可变定义原地覆盖）、`role_permissions`、`account_roles`（整组替换、物理删除）、`authorization_requests` 三状态；种子写角色与权限（2.3）。老系统角色只作迁入证据（ADR-0026 第 8 项），不进目标设计。
- T2 目标：BC `platform.identity`，Phase 1a W2，首次部署前（时点口径见 C-1）。
- T3 过渡（开放期）：**没有已部署的数据**——dev 库首次部署前不执行任何迁移（`C-3-dev-db.md`:69），无角色、授予、申请行；三个 PR 直接改 identity 基线，dev 按 `phase1a/infra/runbooks/dev-db-rebuild.md` 随库重建；系统角色由首次启动对账写入（替代种子）；没有在途授权申请（ADR 迁移方案第 5 条）。`legacy_numbers` 不受影响。老角色迁入（`MIGRATION` 渠道）不在本卡，迁入卡另写 E3。
- T4 回滚：首次部署前回退实现提交即恢复基线（随库重建）；首次部署后不回到删除语义，按 ADR 回滚方案（只读视图投影、提高声明修订号回退定义）。

### 专项（E1–E3）
- E1：`AccountRolesReplaced` 加 `openedGrantIds`、`closedGrantIds`（ID 列表），`RoleCreated`、`RoleRevised`、`RoleEnabled`、`RoleDisabled` 加 `roleVersionId`；作废事件 `AuthorizationVoided`（A-7 已裁：同时直写审计 `platform.identity.authorization.voided`，事件标 @NotAudited）。载荷只含 ID；加字段属兼容变更（ADR-0035，首次生产部署前），事件目录同步；不改 `EventEnvelope`。
- E2：无读模型。E3：无迁入映射。
- 预期表结构 diff：见第 7 节。
