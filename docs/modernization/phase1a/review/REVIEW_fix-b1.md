# 修复批 B1 单独 opus 复审

条目编号以本文为准，外部引用加前缀 B1-。按时间顺序收录首轮复审与各轮复核。

---

## 第 1 轮

## B1（fix-b1-identity，992c3ae..aef9fe1）opus 复审结论：修后整合

我没有改任何文件。`./mvnw -B verify` 在本机跑通：surefire 746 / 0 / 19，failsafe 218 / 0，BUILD SUCCESS，与 B1 自报一致。新增测试里没有 `@Disabled` 或假设跳过。gitleaks 8.30.1 扫过 6 个提交，没有泄露。跑完后 worktree 是干净的。

结论是修后整合：有 1 个 Blocker、5 个 High，修完才能整合。其余做得扎实：TOTP 防重放、会话与 CSRF 轮换、应急会话到期即结束、每请求复核、凭据只显示一次，测试都覆盖到了。

下文行号除另注外都相对 `backend/src/main/java/com/forcitis/one/`。

### Blocker

**B1-B1 管理员离职被暂停后，经 HTTP 实际没有人能放行**
- 放行接口 `organization/workforce/web/SyncRunController.java:46-49` 要求 `sync-run:execute`。种子 `V20261015000000…seed_business_roles.sql:14` 只把这项权限给了 `org_data_admin`。
- `platform/identity/application/AdministratorSeparationReview.java:54-57` 又要求放行人持有 `super_admin` 或 `security_auditor`。
- 超管在普通会话里没有任何权限，管理模式里也只有 4 项平台权限，都不含 `sync-run:execute`。所以除非有人同时持有组织数据管理员和超管（或审计员），否则放不了行。
- 后果：任一超管或审计员离职后，每次同步都会保持 HELD，全公司的入职、离职同步停摆。
- `SyncLockoutGuardIntegrationTest` 直接调用 `ReleaseSyncRun.execute`，绕过了 `@PreAuthorize`，所以没发现这个问题。
- 修法有几种：放行入口按「暂停原因是管理员离职」单独授权；或在管理模式加一项放行权限；或接受兼任。后两种会改动用户已定的「管理模式只保留三项」，**需用户确认**。修完补一个经 MockMvc 走真实会话的 IT。

### High

**B1-H1 编号四眼确认存在「先看后改」的竞态**
- `platform/numbering/web/NumberingRuleController.java:68-78`、`domain/NumberingRule.java:119-162`：确认请求不带版本号，确认的是那一刻正在等待的版本。
- 业务管理员看过 v2 后，作者抢先保存 v3，业务管理员随后点确认，确认的就是自己没看过的 v3。四眼被绕过。
- 修法：确认与财务确认都必须带 `templateVersion`，不一致返回 409，并补并发 IT。

**B1-H2 编号迁移删列，违反「1a 不做破坏性迁移」和 ADR 回滚方案，并与 B2b 硬冲突**
- `V20261015000300__platform_numbering_create_rule_versions.sql:68-72` 删掉了 `numbering_rules` 的 `pattern`、`reset_policy`、`start_value`、`confirmation_status` 四列。
- 这违反 `INFRA_RUNBOOK.md` §7.6「1a 期间不做破坏性迁移」。也违反 ADR-0027 回滚方案「回退提交即恢复保存即确认；版本表保留不用」：删列后旧代码回不去。
- B2b 的迁移 `V20261015020200` 要读取 `confirmation_status`，按版本号排在 B1 之后执行，会直接失败。
- 修法：改为只追加。保留旧列，去掉 NOT NULL，注释写明「停用，由 numbering_rule_versions 取代」。合并方式见下文 (e)。

**B1-H3 没有生效日期，偏离用户决定，需用户确认**
- intent 146 与 D-1a-147 写明「修改已确认的模板生成新版本，须重新确认后按生效日期启用」。B1 在确认那一刻立即生效，没有生效日。这是 B1 自行偏离用户决定。

**B1-H4 ADR-0026 一期范围与 ADR-0027 守护第 10 条没做，已可被利用**
- 缺 I-12「不能改自己的角色」、敏感权限双人确认、`AccessDeniedAudit`。ADR-0026 第 5 项第 4 条的 `privileged.request` 也没有记录，全仓 grep 无结果。
- `platform/identity/application/PrivilegeCeiling.java:23` 对管理员整体豁免，`ReplaceAccountRoles.java:40-70` 没有本人检查。于是超管进入管理模式后，可以立即给自己授 `security_auditor` 去查看、导出全部审计，或建一个含审计权限的角色授给自己。ADR-0027 第 3 项「查看与导出都需双人确认授予」形同虚设。
- ADR-0027「需同步修改的守护规则」写明要与 1a 实现在同一个 PR 中提交，I-12 又是 1a 出口条件，所以不能推迟。

**B1-H5 工作日历权限名与 B2 不一致**
- `platform/identity/api/Permission.java:64` 和种子第 18 行用的是 `organization.attendance:locked-work-calendar-day:update`。B2 已统一为 `organization.attendance:locked-day:update`，B2 也改了 `Permission.java`。
- 不改的话，组织数据管理员实际拿不到 B2 新增的那项权限。

### Medium
- **B1-M1** 管理模式到点自然结束时，不写「退出」审计也不告警（`IdentityAuthentication.java:64-66`）。ADR-0026 第 5 项第 2 条要求进入与退出各记一条并告警。
- **B1-M2** `EnterAdministratorMode.java:51-85` 拒绝时抛异常，整个事务回滚，已消费的 OAuth state 也随之恢复，违背 I-9 的「一次性」。state 的消费应独立提交。
- **B1-M3** `HandleOrgSnapshotChange.java:80-114` 在一个事务里同时改 `UserAccount` 和 `EmergencyAdmin`，违反 §14 第 1 项，例外也没登记。可拆成两个监听：每请求复核已兜底，拆开是安全的。
- **B1-M4** 版本表的触发器（迁移约 46-58 行）只冻结模板内容，不冻结确认人与状态：已填的 `confirmed_by` 能被改写，CONFIRMED 也能被改回其他状态。建议确认列只允许从空到有值，状态只允许合法转换。
- **B1-M5** 强制下线和每日核对的告警只写 `log.error ALERT` 与计数器，没有真正「告警给其他持有人与安全审计员」。若按 C-7 的日志告警规则执行也可接受，但要在文档里写明。
- **B1-M6** 缺守护：`OrgDataAdminRoleTest`（ADR 守护第 6 条前半）和 `NumberingPatternTest` 的占位符集合（第 12 条）。测试名与 ADR、SECURITY_INVARIANTS 里的名字也对不上，例如 `AdminModeBoundaryTest` 实际叫 `AdministratorModeIntegrationTest`，需要在文档里做对照。
- **B1-M7** 契约与文档没同步：
  - `INFRA_RUNBOOK.md` §6 仍写「从标准输入读口令、首登强制改口令」，与实现不符（实现是生成口令并打印一次）。
  - `FORCITIS_PLATFORM_IDENTITY_SSO_ENABLED` 没进部署前清单。
  - `REIMAGINED_ARCHITECTURE.md` 的表清单没有 `numbering_rule_versions`。
- **B1-M8** 术语：管理模式、强制下线、组织数据管理员、业务管理员、安全审计员、流程管理员、模板版本都没登记。提议的 `separation 离职` 与已有的 `separated 离职` 撞词。
- **B1-M9** 审计查询本身不写审计（intent 152「查询与导出本身写审计」，B1 只做了导出）。请确认是否归 B1。
- **B1-M10** 种子里加了 `process_administrator`（流程管理员，D-1a-61），这属于审批的范围。整合前要确认审批批次没有重复插入。
- **B1-M11** Argon2 参数：`Argon2PasswordHasher.java:7` 的注释写 1 次迭代，Spring 5.8 默认值实际是 t=2、16 MiB、p=1，低于 OWASP 建议的 19 MiB、t=2。这是老代码，但在本次审查重点里，建议调高并改正注释。
- **B1-M12** 运维命令没有跑过真实的非 web 进程，见下文 (i)。

### Nit
- 应急锁定是「先读锁定状态、后原子加一」，并发请求可以超过 5 次尝试（老代码）。
- 退出管理模式时不轮换会话 ID。
- 操作人、见证人是自由文本，`"alice"` 和 `"alice "` 就算两个人。
- 有几处全限定名写法：`OwnAttributionGuard` 里的 `Locale`、`AuditCsv` 里的 `UserId`。
- `SECURITY_AUDITOR` 常量放在 application 层，应与 `Role.SUPER_ADMIN` 放在一起。
- RULE-340 规则卡没标注已被 ADR-0026 D-7 取代。
- CSV 防护没覆盖全角 `＝＋－＠` 和开头空白。
- 强制下线的审计没记操作人的来源 IP。
- 管理模式权限多出一项 `role:read`，可以算在「角色定义」内，I-11 措辞同步一下即可。

### (a)~(l) 逐条判断
- **(a)** 不偏离。ADR-0027 第 1 项第 7 条和 Q-3 原文就是「少于 2 名，**或**涉及超管、审计员离职」即暂停。B1 的实现等同原文，自述「更严」不准确。真正的问题在放行路径（B1-B1）。另外，审计员能否放行超管离职，建议顺带请用户确认。
- **(b)** 可接受。ADR 第 1 项第 4 条明写「随权限缓存（≤ 60 秒）」。本节点上角色变化、停用会在提交后立即清缓存，测试也覆盖了 60 秒失效。
- **(c)** 需用户确认。B1 自拟了 `voucher-numbering-rule:approve`，没有任何系统角色持有，结果是凭证号模板永远无法确认。哪个角色做财务确认、它是否算敏感权限（应与 `numbering-rule:approve` 同类），都要用户定，并登记术语。
- **(d)** 可接受，但要在 `module-dependencies` 和架构文档里登记这条上下文映射（§14 第 11 项）。更好的做法是放到 workforce 的 spi 包。
- **(e)** 两边模型冲突，按 D-1a-147 合并：
  - 以 B1 的 `numbering_rule_versions` 为准。B2b 的单行模型「保存即确认」违背 ADR-0027 第 2 项第 4 条。
  - 在版本表加 `effective_from_date`，作为作者提交的版本内容的一部分，让确认人看得到。确认时如果生效日早于当天，拒绝或顺延，具体请用户定。
  - B2b 的 `rule_version_number` 就是 `version_number`，B1 的事件已经携带 `RuleVersion`，两边这个类内容相同。B2b 的 `NumberingRuleConfirmed.effectiveFrom` 字段保留。
  - 发号改为「`effective_from <= 业务日期` 的最高 CONFIRMED 版本」。
  - B2b 的 `V20261015020200` 作废重写，B1 的删列改为只追加（B1-H2）。
- **(f)** 必须本期做，不能推迟，理由见 B1-H4。若要转给其他批次，需协调者明确指派，并在 1a 内完成。
- **(g)** 符合 ADR：超管不再自带全部权限，管理模式只有平台权限，角色授予改用 `account-role:update`。`role:read` 见 Nit。
- **(h)** 可接受。原因写在列注释和 `repository-write-exceptions.txt` 里，这三处更新不会与带 version 的保存冲突。
- **(i)** 不足（B1-M12）。专用进程没有被真正启动过：启动时是否还有定时作业或事件重发在跑、`System.exit` 是否会丢掉异步事件，都没验证过。建议补一个用该 profile 启动 Spring 上下文的冒烟测试。
- **(j)** 符合 G-1 和上轮复审的建议，补上部署文档即可（B1-M7）。
- **(k)** 偏离统一命名，整合前必须修（B1-H5）。
- **(l)** 可接受。整合时把 `EmergencyAdminCheckJob` 同时接到 B3 的作业总开关 `forcitis.platform.scheduling.jobs-enabled` 上。

### 其他重点
- **离职联动与 B3（C-M7）的接合点**：作废浏览器标识应接在 `HandleOrgSnapshotChange.separate()`。更好的做法是 identity 发一个「账号离职」事件，由 B3 幂等监听。注意：离职被 HELD 期间，浏览器标识会保留到放行为止。
- **改断言是否正当**：RoleTest、删掉的 `superAdminHoldsEverything`、`savingConfirmsTheRule` 改为 `savingDoesNotConfirm`，都有 ADR-0026 D-7 或 ADR-0027 第 2 项第 4 条为依据，正当。但删 `superAdminHoldsEverything` 时，「系统管理」导航的覆盖也一起丢了，需要补「管理模式下可见」的测试。守护清单没有放宽：只有新增的版本表例外，以及因删列、改为带 version 而去掉的行。这些都要在 PR 里单列。
- **越界**：没有动 platform.web、approval、timesheet；kernel 只新增 `SeparationReview` 一个文件。`ApplicationIntegrationTest` 加了用 SQL 确认种子规则的准备步骤和两次确认请求，有必要，但要在 PR 里列出。

### 整合前必须修
1. B1-B1：管理员离职暂停的放行路径（放行人与授权方式**需用户确认**）。
2. B1-H1：确认请求带版本号。
3. B1-H2 与 (e)：编号迁移改为只追加；与 B2b 按上面的方案合并，生效日规则**需用户确认**（B1-H3）。
4. B1-H4：I-12 不能改自己的角色、敏感权限双人确认（含审计查询、导出与两项编号确认权限）、`AccessDeniedAudit`、`privileged.request`；或由协调者明确指派到 1a 内的其他批次。
5. B1-H5：权限名改为 `organization.attendance:locked-day:update`。
6. (c)：财务确认由哪个角色持有，**需用户确认**。
7. B1-M1 管理模式到期的审计与告警；B1-M2 拒绝时 state 回滚。
8. PR 中单列所有改、删的断言与守护清单变动。

---

## 第 2 轮

## B1 复核（整合分支 fix-b1-identity，HEAD e598323）：可整合

一个 Blocker 和五个 High 都已修好，没有新出现的 Blocker 或 High。另外有三条新问题，都是 Medium 或 Nit，可以在整合后关闭。

我只读了代码，没有改任何文件。在 e598323 上跑了全量 `./mvnw -B verify`：第一组 2 / 0 / 0，surefire 777 / 0 / 19，failsafe 277 / 0 / 0，BUILD SUCCESS。跑完后 worktree 是干净的。gitleaks 8.30.1 扫描 992c3ae..HEAD，没有发现泄露。

### 逐项结论

- **B1-B1（管理员离职暂停后无人能放行）：已修。** 按 ADR-0028 逐条核对都符合：
  - **原因类型化、按类解除：** 暂停原因分为缩水保护和管理员离职两类，各自由有权的人解除，只解除一类时运行仍暂停（`ReleaseSyncRunHolds.java:46-66`）。
  - **新权限：** 放行接口接受 `sync-run:execute` 或新增的 `separation-hold:approve` 之一。管理模式的权限清单现在正好是 5 项（`AdministratorMode.java`），安全审计员角色由新迁移 `V…000800` 授予该权限。
  - **留任判定：** 放行人要账号启用、持有超管或审计员角色、快照中明确在职、且不在离职名单中（`AdministratorSeparationReview.java:46,55-62`）。应急会话的绑定人快照缺失时不算在职，所以不能放行。
  - **超管下限：** 放行后可用超管为 0 时返回 409（`ReleaseSyncRunHolds.java:52-55`）。
  - **重跑重新暂停：** 重跑时离职名单里新出现的管理员会让运行重新暂停（`ApplySourceSnapshot.java:82-85`）。
  - **原因、审计与告警：** 放行必须填原因，有审计也有告警。
  - **测试：** `SyncLockoutGuardIntegrationTest` 已改为走 MockMvc 和真实会话，覆盖了 ADR-0028 守护第 2 条的全部情形。
- **B1-H1（确认存在先看后改的竞态）：已修。** 确认请求必须带 `templateVersion`，和当前等待的版本不一致就返回 409，两种确认都一样（`NumberingRule.confirmable`）。有对应的 IT 和单元测试。
- **B1-H2（迁移删列）：已修。**
  - `V20261015000300` 不再删列，只去掉 NOT NULL 并加上「停用」注释。
  - B2b 的 `V20261015020200` 已删掉，编号只保留版本表这一套。
  - 触发器冻结了确认列，也禁止非法的状态转换和 TRUNCATE。
- **B1-H3（没有生效日期）：已修。**
  - 生效日由作者填写，是版本内容的一部分。确认时生效日早于当天就拒绝，并给出明确的错误码。
  - 发号用 `inForceOn(businessDate)`，即生效日不晚于业务日期的最高已确认版本。
  - 种子版本的生效日固定为 2000-01-01、不检查生效日，这是合理的例外。
- **B1-H4（I-12、双人确认、拒绝审计、管理模式请求审计）：已修。**
  - 不能改自己的角色，也不能改自己持有角色的定义，被拒时写审计。
  - `PrivilegeCeiling` 不再对管理员整体豁免。敏感权限、超管角色和管理类角色都要先建授权申请，再由另一名持有人确认；申请人和受益人都不能确认。
  - `AccessDeniedAudit` 和 `PrivilegedRequestAudit` 都已就位。
  - `SensitiveGrantTwoPersonIntegrationTest` 覆盖了「超管在管理模式下给自己授审计员再导出」这条路径，确认已被堵上。
- **B1-H5（权限名）：已修。** 全仓已统一为 `organization.attendance:locked-day:update`，旧名称已无残留。

### 新问题（都不阻塞整合）

- **B1-R1（Medium）：修改角色定义的申请没有排除受益人。** 「申请人和受益人不能确认」这条规则只对授予角色的申请生效。修改角色定义的申请里，受益人字段为空，所以该角色的现有持有人可以确认给自己所持角色加敏感权限的申请，实际上就是间接改了自己持有角色的定义。修法：确认人不能持有被修改的那个角色（`AuthorizationRequest.confirm`）。
- **B1-R2（Medium）：「可用超管将为 0 时只能走运维命令」没有落实。** 代码里没有放行用的运维命令。实际的恢复路径是先用 `grant-role` 授出超管，再由此人进入管理模式放行，但运维手册没写这一步。修法：二选一，在手册中写明这条路径，或者补一个放行命令。
- **B1-R3（Nit）：** 生效日相关的测试标的是「B1-H2」，按本次复审的编号应为 B1-H3。另外，种子版本的确认事件里 businessDate 会是 2000-01-01，建议在事件目录中注明这一点。

### 抽查测试名是否引用了复审编号

大多数没有引用。只有 B1-H1、B1-H2、B1-M4、B1-M12 出现在测试名里。M1、M2、M3、M9、M11 等都有对应的测试，比如「管理模式到点自然结束写审计」「被拒的进入仍消费掉 OAuth state」「审计查询本身入账」「二十个并发猜测恰好只有五次机会」，但测试名里没有写编号。如果要靠测试名做追溯，需要补上编号。

### 三个判断

**（1）运维档案改为监听本机随机端口的 servlet 应用：攻击面只是略有扩大，不实质。**
- 端口只绑定 127.0.0.1，进程也只在命令运行的几秒内存在。
- 本机来源本来就在受信代理和应急「内网」名单里，正式应用也同样暴露在本机回环地址上。能从本机发起请求的攻击者，打正式应用就够了，并且仍然需要口令和 TOTP。
- 不过完全可以不开端口：保留 `web-application-type=servlet`，把 `server.port` 改为 `-1`。这样 Spring 仍会创建 Web 应用上下文，会话仓库、安全链等 bean 都在，但不启动内嵌服务器。建议这样改，再在 `OperationsProfileSmokeTest` 里断言没有监听端口。

**（2）事件字段与事件目录：仍然一致。**
- `NumberingRuleConfirmed` 和 `NumberingRuleReconfigured` 都带 ruleVersion（即版本号）和 effectiveFrom，信封里的 businessDate 等于 effectiveFrom。
- `NumberingRuleConfirmationGiven` 没有 businessDate，事件目录里也写的是「无」。这些与 `events/numbering.yaml` 一致。
- 「目录声明的业务日期必须与信封一致」这条守护已接入所有测试上下文，并在本次 verify 中通过。
- 语义上有一处变化：B2b 原来的 ruleVersion 是「已生效过的模板计数」，现在改为版本表的版本号，会跳过被取代、从未确认的版本。事件目录已经写成「每种单据从 1 递增」。下游分析不能假定确认事件的版本号是连续的。

**（3）`separation-hold:approve` 是否列为敏感权限：建议列为敏感，由用户决定。**
- 现在它不在 `SensitivePermissions.CODES` 里。管理员可以把含有这一项（且只有这一项）的自定义角色直接授给别人，不需要第二人确认。
- 被授予的人就能解除「管理员离职」暂停，让超管和审计员被收回角色。这正是锁死保护要防的事，性质与 `account-role:update` 相当。
- ADR-0028 自己也以「安全审计员角色已列为敏感」作为把这项权限授给审计员的前提，所以列为敏感后口径才一致。
- 代价很小：系统角色的种子授予不受影响，只有运行期把它授给个人时才需要双人确认。

### 整合后跟进（不阻塞）
1. B1-R1：修改角色定义的申请，确认人不能持有该角色。
2. B1-R2：在运维手册写明「可用超管为 0」时的恢复路径，或补一个放行命令。
3. 判断（1）：运维档案改为 `server.port=-1`。
4. 判断（3）：把是否列为敏感交给用户决定。
5. Medium 级测试名补上复审编号。

---

## 第 3 轮

## B1-scope（org_units 按数据范围 fail-closed）复核：仍有漏洞

部门列表和部门详情这两个接口本身已经堵住了：越权读详情返回 404 并写审计，范围算不出来返回 403，全局范围的组织数据管理员不受影响。但同一个权限 `org-unit:read` 还守着另外两个读接口，它们没有按范围过滤，所以整体上没有堵完。

我只读了代码，没有改文件。看的是 worktree 当前的 HEAD 4e37153，修复提交是 3f2f4f9。跑了相关测试，全部通过：

| 测试 | 通过 / 总数 |
|---|---|
| `OrgUnitScopeIntegrationTest` | 6 / 6 |
| `WorkforceHttpIntegrationTest` | 7 / 7 |
| `AccessScopeTest` | 10 / 10 |

### 1. 绕过路径

**仍有漏洞（High）：法人归属的两个 GET 没有按范围过滤。** 位置在 `organization/workforce/web/LegalEntityAttributionController.java:31-35` 和 `:50-54`。
- `GET /org-unit-legal-entities/{orgUnitId}` 和 `GET /employee-legal-entities/{employeeId}` 只要求 `org-unit:read`，不经过 `OrgUnitAccess`。
- 一个只持有「本人」或「本部门」范围自定义角色的人，可以逐个枚举 id：
  - 部门或员工存在时返回 200，不存在时返回 404，相当于一个存在性探测；
  - 返回 200 时，还能看到任意部门、任意员工的法人归属和生效区间。
- 要修：部门归属按 `OrgUnitVisibility` 判断，员工归属按员工所在部门是否可见判断。越权一律返回 404 并写审计，与部门详情同一口径。再补对应的 IT。

**可以接受的部分：**
- **同步记录明细：** 它要求 `sync-run:read`。ADR-0027 第 3 项第 4 条已经把同步记录定为全局数据，只授给组织数据管理员，所以不算漏洞。
- **离职暂停视图：** 持有 `separation-hold:approve` 的人能看到暂停运行和离职名单，这是 ADR-0028 明确允许的。
- **员工详情：** 1a 目前没有员工详情或通讯录接口，所以不存在这个口子。
- **事件载荷：** 只在模块之间流转，不对外暴露。审计条目里的部门键只有审计员能看，而审计查看本来就是全局的。

**Nit：**
- 法人列表 `LegalEntityController.java:31-32` 也只用 `org-unit:read` 守着。它列的是法人小表，不是部门，算全局数据可以，但建议在数据访问注册表里登记为全局，免得以后被当成漏网的接口。
- 部门响应里的 `parentOrgUnitId` 会暴露最上层可见部门的上级 id，只是一个 id，不带内容。可以随下一批一起裁掉。

### 2. 全局范围（ALL）下 403 与 404 的口径：一致
- 全局范围下所有存在的部门都可见：不存在的 id 返回 404，不会出现「越权」这种情况。
- 受限范围下：不存在返回 404 且不写审计；越权返回 404 并写审计。两种情况的响应完全相同，没有存在性探测。
- 范围算不出来时，列表和详情都在查 id 之前就返回 403，与 id 存不存在无关，所以 403 也不泄露存在性。

### 3. 组织里找不到调用人部门时拒绝：结论合理，实现靠的是巧合
- 返回 403 而不是返回全部，符合 fail-closed，这个结论我同意。
- 但它实际上是这样得到的：`AccessGrant` 的构造函数对 `orgUnitId` 做非空检查，部门为空时抛出 `NullPointerException`，再被 `OrgUnitScopes` 里兜底的 `catch (RuntimeException)` 转成 403。代码里并没有一条明确的规则写「没有部门就拒绝」。
- 这带来两个问题：
  - **结果偏严：** 如果这个人同时持有「指定部门」角色，本可以看到那些部门，也会被整体拒绝。
  - **测试名不符：** 测试显示名写的是「gets nothing」，实际断言的是 403，两者对不上。
- 建议：在 `OrgUnitScopes` 里显式处理部门为空的情况（按本部门类的授予跳过，或者明确返回 403，二选一，并写进规则卡），同时改正测试名。
- 另一处 Nit：`NOT_USED = new LegalEntityId(1)` 是一个假的法人值，以后接入法人维度时容易出错。建议下一批随类型下沉一起去掉。

### 4. 新增的 kernel 端口 `OrgUnitAccess`：可以接受，但要补登记
- 依赖方向是对的：workforce 不能依赖 identity，所以和 `SeparationReview` 一样采用依赖倒置，把端口放在共享内核。
- 有两处欠账：
  - **没有登记（Medium）：** `backend/architecture/module-dependencies.txt` 第 5 行和 `REIMAGINED_ARCHITECTURE.md` §2.2 只登记了 `SeparationReview`，没有 `OrgUnitAccess`（§14 第 11 项）。
  - **跨模块用了裸类型（§14 第 6 项）：** 端口用的是 `Set<Long>` 和 `String permissionCode`。原因是 `OrgUnitId` 还没下沉到 kernel，作为过渡可以接受。但要在推迟项里写明：下一批下沉 `OrgUnitId` 时改成类型化 id。

### 需要修的清单
1. **High（必须修，否则不能算已堵）：** 法人归属的两个 GET 按调用人范围过滤，越权返回 404 并写审计，补 IT。
2. **Medium：** 显式处理调用人没有部门的情况，不再靠空指针兜底，并改正对应的测试名。
3. **Medium：** 在 `module-dependencies.txt` 和架构文档里登记 `OrgUnitAccess`；在推迟项里写明下一批改成类型化 id，并去掉 `NOT_USED` 这个假法人值。
4. **Nit：** 法人列表在注册表里登记为全局；`parentOrgUnitId` 的裁剪随下一批一起做。

---

## 第 4 轮

## 法人归属两个 GET 越权（7a7b204）复核：已堵

这次修复是对的，`org-unit:read` 守护的读接口里也没有找到其他漏网的。

我只读了代码，没有改文件。看的是 fix-b1-identity 的 7a7b204，跑了相关的三组测试，全部通过：

| 测试 | 通过 / 总数 |
|---|---|
| `OrgUnitScopeIntegrationTest` | 8 / 8（含新增 2 例） |
| `LegalEntityAttributionIntegrationTest` | 5 / 5 |
| `WorkforceHttpIntegrationTest` | 7 / 7 |

### 修复本身
- **部门的法人归属：** 按 `OrgUnitVisibility` 判断能不能看。
- **员工的法人归属：** 按这名员工所在的部门能不能看来判断。查不到员工的组织记录时一律拒绝，不会放行。
- **越权与「不存在」完全一样：** 越权时抛出的错误码、HTTP 状态和文案（`"no such " + kind`），都与 `AttributionSubjects.require` 对不存在对象抛出的那个一字不差（`GetLegalEntityAttribution.java`）。因为先查存在、再查范围，两条路的响应完全相同，没法拿来探测某个部门或员工是否存在。
- **审计：** 越权时用 `recordIndependently` 写 `organization.workforce.legal-entity-attribution.denied`，请求失败也会留下记录。
- **范围算不出来：** 照样在 `visibleOrgUnits` 里返回 403，与部门接口口径一致。
- **全局范围：** 组织数据管理员读任何部门、员工都正常，不受影响。

### `org-unit:read` 守护的读接口
全仓 grep 后，这个权限只守着以下 5 个接口：

| 接口 | 现状 |
|---|---|
| 部门列表 | 已按范围过滤 |
| 部门详情 | 已按范围过滤 |
| 部门的法人归属 | 已按范围过滤 |
| 员工的法人归属 | 已按范围过滤 |
| 法人列表（`LegalEntityController.java:31-32`） | 未过滤，可接受 |

法人列表只返回法人小表的 id、代码、名称和状态，不含部门或人员数据。它和上一轮一样，只需要在数据访问注册表里登记为全局（Nit）。别的模块没有其他控制器读组织数据。

### Nit（不影响结论）
- **测试只比了错误码：** 测试只断言越权和不存在两种响应的 `errorCode` 相同。建议改成比较整个响应体，把「完全相同」用测试固定下来。
- **审计主体键写法不统一：** 本处写的是 `org_unit:20` 和 `employee:902`，部门详情那边写的是 `org-unit:…`，`OwnAttributionGuard` 写的是 `legal-entity-attribution:org_unit:…`。建议统一成一种写法，方便审计员检索。
- **本人范围看不到自己的法人归属：** 持有「本人」范围角色的人读自己的员工法人归属也返回 404。按「人员类范围看不到部门」的口径，这样是对的。如果以后要让员工查看自己的法人，应该另开一个本人接口，不要放宽这里。
