# 新系统安全不变量：老系统缺陷不得进入 FORCITIS one

日期：2026-10-14 · 会话：psa-sec-hotfix · 依据：用户决定「老系统先不管，确保新系统没有这些缺陷」，以及「取消桥接 (Recommended)」（与检查点 2 Q-1「1a 删除桥接」一致）。

范围：老系统安全审计第 7 轮的 SEC-116 至 121（见 `SECURITY_FINDINGS.md`）。老系统不再修补、不做止血，因此新系统**不得以任何方式信任老系统签发的身份**，并且自身不能出现同类缺陷。

每条不变量都必须有自动化测试。测试在相应能力交付的阶段启用，而且作为该阶段的出口条件，不作为建议项。

## 1. 与老系统的身份隔离

| # | 不变量 | 来源 | 现状 | 验收测试 |
|---|---|---|---|---|
| I-1 | 新系统不写入、不校验老系统令牌（`system_oauth2_*`），不以任何方式信任老系统签发的身份；应用代码不引用令牌表，凭据值不进入新系统的任何表、日志、事件与会话记录。只读账号对令牌表与凭据列的读取授权按 ADR-0037 放开（用户「全部可读，不排除」） | 检查点 2 Q-1；ADR-0009 §6 修订；ADR-0037 第 3 项 | 架构已采纳（REIMAGINED_ARCHITECTURE §3.1、§3.6）；ADR-0037 已采纳 | `LegacyTokenIsolationTest`（ArchUnit）：任何模块都不访问 `system_oauth2_*`；`CredentialColumnGuardTest`：`src/main` 的 SQL 与读取列登记不引用凭据列拒绝清单中的列；只读账号启动校验（ADR-0037 第 4 项 (a)–(f)，`ReadOnlyAccountCheckIT`）：有写、DDL、EXECUTE 权限或偏离平台基线即拒绝启动 |
| I-2 | **老系统后端不得被配置为签名票据签发方**（C1a-03 的 `ticket_issuers`）。**无条件**：老系统后端永远不是签发方（ADR-0027 第 4 项取代 ADR-0009 第 6 条中「由老后端签发」一句；D-1a-32）；以后若需要须另立 ADR | 本决定；ADR-0027 | 新增 | `TicketIssuerAllowlistTest`：`ticket_issuers` 中只允许评审过的签发方，老系统后端不在其中；新增签发方须改入库的允许清单 |
| I-3 | 新系统只接受由自己向身份源核实过的登录方式：企业微信 OAuth（含小程序）、致远 SSO（服务端验票）、外部签名票据、应急管理员 | C1a-01..04 | 已在规格中 | `AnonymousAccessIT` + 公开端点白名单快照（§3.8）：白名单之外不存在任何能建立会话的端点 |

## 2. 第 7 轮缺陷在新系统中的对应要求

| 老缺陷 | 新系统不变量 | 现状 | 验收测试 |
|---|---|---|---|
| SEC-116 固定验证码 + 匿名短信登录 | I-4 不提供短信或验证码登录（D-01，ADR-0019「短信不迁移」）。以后若要引入任何一次性验证码，必须满足：随机 6 位及以上，只比对最近一条，原子占用后再比对（一次机会），有效期 ≤ 10 分钟，按手机号与 IP 限流；生产配置中不得出现固定值或测试值 | 已在规格中（D-01） | 公开端点白名单快照中没有验证码类端点；`ProductionConfigSanityTest`：prod profile 中不出现 `9999`、`test`、`mock` 类取值的认证相关配置 |
| SEC-117 重新提交覆盖他人流程 | I-5 发起人只取当前登录用户（RULE-265）。**发起时调用方必须拥有该业务单据**：由业务模块在自己的事务内校验归属后，再经 approval 的模块 API 发起；approval 不对外提供以「客户端传入单据 ID」为参数的 HTTP 发起接口。**退回发起人后的重新提交，就是办理发起人任务**，只有该实例的发起人能办理（按 I-7） | 发起人规则已评审；单据归属与重新提交未写明 | `ApprovalStartOwnershipIT`：用户 A 以用户 B 的单据 ID 发起 → 403，B 的实例与变量不变；B 的实例退回后，A 办理 B 的发起人任务 → 403 |
| SEC-118 消息 `toparty`/`totag` = `@all` | I-6 企业微信消息只用 `touser` 精确投递，请求体中不出现 `toparty`、`totag`、`@all`；收件人取不到企业微信 ID 时不发送，并记入 `outbound_deliveries` 告警 | 已在架构中（§3.7），空收件人处理未写明 | `WecomMessagePayloadTest`：所有消息类型的请求体都不含 `toparty`/`totag`/`@all`；收件人为空 → 不调用 `message/send`，并记录失败 |
| SEC-119 卡片回调定人 | I-8 卡片按钮回调（C1a-15 的 P1 部分，已推迟）上线时：操作人只由验签后的 `FromUserName` → `we_com_id` → 唯一启用用户确定；按钮键严格解析；操作人必须是该任务的当前处理人（I-7）；回调 Token / AESKey 只从 KMS 读取，并且是**新企业微信应用的新值**（老应用的值已在老仓历史中泄露） | 已评审（RULE-376），已推迟 | `WecomCallbackActorIT`：伪造 EventKey 中的用户段 → 忽略；按钮键格式不符 → 忽略；非处理人点击 → 忽略 |
| SEC-120 接受 / 拒绝任务不校验处理人 | I-7 **所有任务类操作**（审批的认领、办理、退回、转交、加签，以及 Phase 2 资源任务的接受 / 拒绝）必须由服务端校验：操作人是该任务的当前处理人或候选组成员（认领后只认认领人）。不依赖 Flowable `complete` 自身（它不校验操作人）。代理必须显式（D-07） | 未写明 | `TaskActorAuthorizationIT`：对每种任务操作，非处理人 → 403、任务状态不变；批量操作中只要有一条不属于本人，整批 403 |
| SEC-121 一次性凭证非原子消费 | I-9 一次性凭证（致远票据、外部签名票据 nonce、OAuth `state`）的消费必须原子：唯一约束或条件更新，并发重放只有一个成功 | 已在架构中（`sso_ticket_uses` 唯一约束、nonce 一次性），无并发测试 | `OneTimeCredentialConcurrencyIT`：同一票据并发提交 N 次 → 恰好 1 次成功 |

## 2a. 授权模型带来的不变量（ADR-0026，用户 2026-10-14 批准）

| # | 不变量 | 验收测试 |
|---|---|---|
| I-10 | 数据范围 fail-closed：空范围零行、范围计算异常即拒绝；只有登记在数据访问注册表中的持久化类能访问数据库，触及 `SCOPED` 表的方法必须带 `AccessScope`；越权按 id 读取返回 404 | 本批（B1-scope）只堵 `org_units` 的读路径：范围算不出来或覆盖不到即拒绝，列表只含范围内部门，越权按 id 读取 404 并审计（`OrgUnitScopeIntegrationTest`）；其余机制（类型下沉、`CurrentAccess`、授予明细表、注册表 SCOPED 登记）与下列三项守护排在下一批第一项，首次部署前必须完成：`DataAccessRegistryTest`、ArchUnit `onlyRegisteredPersistenceTouchesTheDatabase`、`ScopedRepositoryContract` |
| I-11 | 管理模式与业务权限分离：普通会话中 `super_admin` 持有人没有平台权限；管理模式（再次验证、1 小时）只有五项权限：角色读取 `role:read`、角色定义 `role:update`、账号授权 `account-role:update`、强制下线 `session:delete`、放行管理员离职造成的同步暂停 `separation-hold:approve`（ADR-0027 第 2 项、ADR-0028），不含业务、敏感读与审计查看，不能办理审批；应急登录直接进入管理模式；应急与管理模式会话每请求复核「账号启用、应急账号启用、当前持有 `super_admin`」，快照明确离职时拒绝应急登录；离职（收到 `EmployeeSeparated`）在同一事务关闭全部开放的角色授予（授予行保留，原因 SEPARATION，离职命令 ID 与审计条目 ID 写回）、吊销会话并停用应急账号 | `AdminModeBoundaryTest`、`EmergencyLoginIT`、`SeparationRevokesRolesIT`、`SyncLockoutGuardIT`（ADR-0028 规则 1 至 3：按类放行、留任判定、余量为 0 时界面拒绝、重跑出现新管理员回到暂停、应急会话需快照、HELD 视图权限、放行写审计与告警） |
| I-12 | 不能修改自己的角色集合，也不能修改自己持有的任何角色的定义；敏感权限（含 `separation-hold:approve`）的授予需第二人确认，管理员不豁免；修改角色定义的申请，确认人不得持有被修改的角色 | `SelfGrantForbiddenIT`、`SensitiveGrantTwoPersonIT` |
| I-13 | 外部票据（致远 SSO、外部签名票据）登录的会话，在本人确认「这是我的账号」前不持有任何权限（`sessions:current` 也不返回权限与菜单）。只有浏览器带着由本账号确认后签发的标识（`__Host-` 前缀 Cookie，库内只存摘要，每次使用换新值）才免确认；旧值重放作废该账号全部标识并记高风险审计；账号停用或员工离职时标识与会话同事务作废（离职触发点是应用服务 `RevokeBrowserIdentifiers.execute`，当前由 `EmployeeSeparated` 监听 `HandleEmployeeSeparated` 调用；组织同步处于 HELD 状态期间，离职员工的浏览器标识一直保留到放行，放行时才作废）；确认后会话 ID 与 CSRF 令牌重发。**边界：此机制只防「被他人伪造的登录请求把受害者登进别人账号」（登录 CSRF），不防票据被盗、Cookie 被盗或终端被控；这几类由票据一次性与短时效、Cookie 的 HttpOnly 与 Secure、会话超时负责** | `BrowserIdentifierIntegrationTest`、`SsoLoginIntegrationTest.ticketSessionNeedsConfirmation`、`SignedTicketLoginIntegrationTest.ticketSessionNeedsConfirmation` |
| I-14 | 手工撤销 `super_admin` 或 `security_auditor` 授予、停用其持有人账号，与离职一样受超管下限保护：先取分离咨询锁，再锁账号行，再共享锁角色行；按「已走」口径（暂停放行、已放行待生效、已录入未开始的停薪留职）统计后，可用超管将为 0 则拒绝且不写任何东西（只有双人运维命令 `revoke-role` 可越过），剩 1 名则发通知卡并记 ALERT；「当前有效授予」只用 `effective_to_at is null` 这一个谓词常量 | `RevokeDisableFloorIT`、`RevokeDisableLockOrderIT`、`RevokeDisableLockOrderGuardTest`、`CurrentGrantPredicateGuardTest` |

## 3. 落地方式

- I-1 至 I-12 加入 Phase 1a 出口条件对应的验收测试（AI_NATIVE_SPEC §5.3）；I-7 中资源任务的部分，以及 I-5 中业务单据的部分，在第一个接入业务单据的阶段（1b 报价）启用。
- 能用 ArchUnit 表达的（I-1、I-2、I-6 的请求体构造入口）放进 `architecture-guardrails/backend`，CI 必跑。
- Phase 1a 入口条件中「SEC-001 至 SEC-006、SEC-107 已在老系统止血」：按本决定不再阻塞新系统（新系统不信任老系统身份）。brief 按约定以 `Proposed revision:` 追加，不改写原条件（由 psa-coordinator 处理）。
- 老系统中的这些缺陷仍然存在，影响的是**老系统自身的数据**（包括过渡期仍在老系统中的业务）。用户已决定老系统先不处理（2026-10-14：「老系统先不管，确保新系统没有这些缺陷」）。

## 4. 测试名对照（ADR 与本表用的名字 → 代码里的测试）

ADR-0026、ADR-0027、ADR-0028 与上文用的是目标名；实际类名按仓库约定带 `Integration` 后缀或合并在一个类里。守护以类名和 `@DisplayName` 的前缀为准。对应关系：

| 文中的名字 | 代码里的测试 |
|---|---|
| `LegacyTokenIsolationTest`（I-1） | `SecurityInvariantsTest.legacyTokenTablesAreNeverTouched` |
| `TicketIssuerAllowlistTest`（I-2） | `SecurityInvariantsTest.ticketIssuersAreReviewedAndNeverTheLegacyBackend` |
| `AnonymousAccessIT`（I-3） | `ApplicationIntegrationTest.anonymousCallersAreRefusedExceptOnTheReviewedPublicEndpoints` |
| `ProductionConfigSanityTest`（I-4） | `ProductionConfigSanityTest` |
| `WecomMessagePayloadTest`（I-6） | `WecomMessagePayloadTest`、`SecurityInvariantsTest.wecomMessagesNeverBroadcast` |
| `ApprovalStartOwnershipIT`（I-5）、`TaskActorAuthorizationIT`（I-7） | 同名 |
| `OneTimeCredentialConcurrencyIT`（I-9） | `OneTimeCredentialConcurrencyIntegrationTest` |
| `AdminModeBoundaryTest`（I-11） | `AdministratorModeIntegrationTest`（`@DisplayName` 以 `AdminModeBoundary` 开头）、`RoleTest.superAdmin` |
| `EmergencyLoginIT`（I-11） | `EmergencyLoginIntegrationTest` |
| `SeparationRevokesRolesIT`（I-11） | `SeparationRevokesRolesIntegrationTest` |
| `SyncLockoutGuardIT`（I-11、ADR-0028 规则 1 至 3） | `SyncLockoutGuardIntegrationTest`（在 `platform.identity` 包，经 MockMvc 与真实会话）；端口本身另有 `AdministratorSeparationReviewTest` |
| `EmergencyAccountDailyCheckTest` | `EmergencyAdminDailyCheckTest` |
| `SelfGrantForbiddenIT`、`SensitiveGrantTwoPersonIT`（I-12） | `SensitiveGrantTwoPersonIntegrationTest`（含 `noSelfGrantAndNoWayToTheAuditTrail`：管理模式下给自己授予审计员再导出审计的路被堵死）、`RoleManagementIntegrationTest` |
| `AccessDeniedAuditIT` | `OrgDataAdminRoleIntegrationTest`（`@DisplayName` 含 AccessDeniedAudit 与 PrivilegedRequestAudit） |
| `OrgDataAdminRoleTest` | `OrgDataAdminRoleIntegrationTest` |
| `SelfLegalEntityEditForbiddenIT` | `OwnLegalEntityAttributionForbiddenIntegrationTest` |
| `NumberingRuleFourEyesIT`、`NumberingPatternTest` | 同名 |
| `OwnLoginRecordsIT` | `AuditExportAndOwnRecordsIT` |
| `ForcedLogoutIT` | `ForcedLogoutIntegrationTest` |
| `DataAccessRegistryTest`、`ScopePerPermissionTest`、`ScopedRepositoryContract`（I-10）、`SensitiveFieldExposureTest`、`ParticipantProjectionReconcileIT` | 尚未落地：数据范围与敏感字段随第一个业务读模型（1b）启用 |
| `WecomCallbackActorIT`（I-8） | 尚未落地：卡片回调已推迟 |

