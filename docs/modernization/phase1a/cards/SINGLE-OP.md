---
卡号: SINGLE-OP
状态: 待派
前置:
---
# 派工卡 SINGLE-OP：实现 ADR-0030 非生产环境单人运维例外（启动自检与可见性、四项放宽、revoke-role、无作者模板起草）

- 草稿：one-1a-par 起草助手，2026-10-16，基线 origin/main d718caf7。ADR-0030 已采纳；本卡把它写成可派的工作单元。
- 「待核」表示手头证据不足，开工前由实现者或 one-data 先核实再改，见第 13 节。
- 时间要求：用户「先实现单人模式」（intent 片段文件名由 one-coordinator 补，见第 2.1 节说明），dev 等本卡出 rc 后才能授予用户本人流程管理员与超管，所以 P1、P2 排最前、尽快；P3、P4 不挡 dev 的 rc（理由见第 1 节）。

## 1. 卡号与类别
- 卡号：SINGLE-OP（ADR-0030 的实现卡，对应 ADR 影响面所说「Phase 1a 下一批 F-12」中的单人模式部分）
- 类别：功能补做（应交付未做：ADR-0030 已采纳，代码尚无任何对应实现）。一张卡，4 个顺序 PR（ 第 9、10 条：一个工作单元一个 PR，各自直接合入 main）
- 批次：Phase 1a，dev 首次部署前的阻塞项；调度 one-coordinator，负责 one-1a-par

### PR 拆分、顺序与依赖

| PR | 内容 | 依赖 | 是否挡 dev 的 rc |
|---|---|---|---|
| P1 | 开关、启动自检、可见性（ADR 第 1 节全部） | 无 | 挡 |
| P2 | `platform.identity` 内的放宽：见证人省略、应急账号下限 1、同步暂停原因文本下限 1、新增 `revoke-role`；放宽项枚举与标注审计写入器 | P1 合并（读模式的只读查询在 P1 建立） | 挡 |
| P3 | `platform.numbering`：编号运维起草命令（无作者版本）、有效日期检查改为只豁免种子版本 1、`NumberingRule` 允许系统起草 | P2 合并（用放宽项枚举与标注写入器） | 不挡（dev 上种子版本 1 由用户在界面直接确认，ADR 第 3 节第 1 行；要改模板时才用，B-8） |
| P4 | 生产前的纵深防御与文档：`ExecStartPre`、安装器正则与 `DropInPaths`、`deploy_gate.py` 的 C-11、PREDEPLOY C-11/C-12、SECURITY_INVARIANTS I-14、运维手册改 include 写法、ADR-0026/0027/0028 状态行 | P1（P2、P3 之后收尾更省事，但不强制） | 不挡 dev；挡生产部署（C-11 本身就是生产闸门） |

拆成 4 个而不是调度建议的 2 个的理由：
- 调度建议里的 P2 含两个互不相干的部分：`platform.identity` 内的三项放宽加 `revoke-role`，和 `platform.numbering` 的新用例。后者要新增领域方法（`NumberingRule` 的系统起草）、新错误码、新运维入口分派方式（见第 13 节需决定第 3 条），审查面和风险都与前者不同；dev 的 rc 只需要前者（授予各角色、登记 1 个应急账号、同步放行），拆开能更早出 rc。
- P4 全是文档与脚本，不进后端 jar，不应排在安全代码前面，也不该把后端 PR 拖大。ADR-0030 守护第 3 条（部署守护）归 P4。
- 若调度认为 P3 并入 P2 更省事（同一审查会话一次看完），可合并，代价见第 13 节需决定第 2 条（放宽项枚举第四个取值何时落地）。

## 2. 依据与出处核对

### 2.1 用户决定与来源
- ADR-0030（已采纳）：用户 2026-10-15「批准并接受例外 (Recommended)」；U-2 / U-3「两项都保留 (Recommended)」；U-4 / U-5「都不做 (Recommended)」；U-6「不放宽 (Recommended)」（intent `2026-10-15-000912-one-coordinator-ADR-0030-dev-单人操.md`）。U-1 至 U-6 在已采纳状态下全部有答复（U-1 由「批准并接受例外」覆盖，见第 13 节待核第 1 条）。
- 用户「dev 白名单把我也加上，我们开发过程中所有操作只有我一个人，不要给我第二个人，这些安全措施等到生产部署前再真正落实」（经 one-coordinator 转达，intent `2026-10-15-225419-one-ops-经-one-coordinato.md`，ADR-0030 引用）。
- 「dev 全由我兼任（推荐）」：A-3 / A-4 / A-5 / A-8 dev 阶段全部由用户本人兼任（intent `2026-10-15-215052-one-coordinator-现在解决-dev-前跟合入无关的.md`）。
- 用户「先实现单人模式」：调度转达的最新指示。main 上 `docs/modernization/intent/` 里没有含这句话的片段（已在 origin/main 与全部远端分支检索），**文件名待 one-coordinator 补**，补上后改本行。
- 内部约束：ADR-0026 回滚方案第 1 条（不提供配置开关）由 ADR-0030 作例外取代，范围见 ADR-0030「取代」行。

### 2.2 规则、ADR、规范
- ADR-0030 全文（本卡 P1 = 第 1 节；P2、P3 = 第 2、3 节；P4 = 第 6 节与「需同步修改的守护规则」第 3、11 条）；ADR-0026 第 5 项第 5 条；ADR-0027 第 1 项第 1、7、8 条、第 2 项第 4 条、第 3 项第 1 条；ADR-0028 第 1、4 条。
- ADR-0043 第 4 项（审计 detail 不写直接标识）与 U-7b（运维命令的操作人 / 见证人以员工 ID 记入审计）；ADR-0038（开放期直接改基线，本卡不改基线）。
- DATA_DESIGN_CHECKLIST §14、§14.1（D13 敏感分级、D19 之外的审计相关条）；§12；§6.1。
- CODING_STANDARD：CS-1.8 只实现当前阶段、CS-1.9 当期做对、CS-2.x 配置键（G46：`forcitis.{子域}.{BC}.` 前缀）、CS-2.24（通用错误码只在 kernel）、CS-5.5 / 5.6（测试只断言本模块与确定性等待）。NAMING_STANDARD：配置键与审计动作名。
- SECURITY_INVARIANTS：I-4（无认证旁路）、I-11 至 I-13；本卡 P4 新增 I-14（文本见 ADR-0030 守护第 11 条）。

### 2.3 ADR 与代码现状核对（基线 d718caf7）

| # | 事项 | 核对结果 | 证据 |
|---|---|---|---|
| 1 | 配置键、监听器、数据库判据 | **全部不存在**，需新建 | `grep -r single-operator backend/src` 无结果；`META-INF/spring.factories` 只登记 `MigrateOnlyListener` 与 `ReadOnlyVerificationCommand` |
| 2 | 见证人必填 | 属实；**代码按员工 ID 而非「名字」** | `OperationAttestation.java:14-18`：operator、witness 为 `EmployeeId`，缺任一或相等抛 `PLATFORM_IDENTITY_OPERATION_WITNESS_REQUIRED`（ADR-0043 U-7b 已把姓名改为员工 ID，ADR-0030 背景仍写「名字」「自报文本」，事实不变） |
| 3 | 审计 detail 里的「witness=none」 | **ADR 的写法与现有守护冲突**：现有格式是 `operatorId=<UUID> witnessId=<UUID>`（`OperationAttestation.java:34-36`），`AuditDetailPolicy` 的 FORBIDDEN 模式禁止审计相关代码里出现 `witness=`、`operator=` 字面量（`AuditDetailPolicy` 的 `FORBIDDEN` 列表）。落地写法应为 `witnessId=none` | 见第 13 节待核第 3 条 |
| 4 | 审计写入点登记 | **新增或增加任何 `new AuditEntry(` 的主代码文件都要改 `backend/architecture/audit-text-writers.txt`**，并对应 `DATA-REPAIR-timesheet-audit.md` TB-01 第 2 项允许内容表的行；现状：`GrantRoleByOperations`（1 处，行 15）、`RegisterEmergencyAdmin`（行 14）、`Enable/Disable/ResetEmergencyAdmin…`（行 15）、`ReconcileEmergencyAdmins`（行 6） | `AuditWritePointsGuardTest`（文件集与次数双向相等，`LAST_ROW = 19`） |
| 5 | 动作名登记 | 新审计动作须登记在 `docs/modernization/audit/MUST_AUDIT_ACTIONS.yaml` 的某个类别下，否则 `MustAuditActionsTest` 失败；已有 `platform.identity.operations.account.role.granted`（行 26）、`platform.identity.account.role.revoked`（行 20）、`platform.identity.emergency-admin.check-failed`（行 104） | `MustAuditActionsTest` |
| 6 | `revoke-role` 运维命令 | **不存在**（只有 `grant-role`）；收回角色的现有路径是 `ReplaceAccountRoles`（应用内，收回敏感角色要建两人申请，`ReplaceAccountRoles.java:128-138`）与离职撤销（`HandleOrgSnapshotChange`） | `OperationsCommands.java:54-65` 的 switch 只有 5 个操作 |
| 7 | 编号运维起草命令 | **不存在**；`platform.numbering` 没有任何运维入口、没有 `ApplicationRunner`；`NumberingRule.propose` 要求作者非空（`NumberingRule.java:109`，`Objects.requireNonNull(author, …)`） | `ConfigureNumberingRule.execute` 只有应用内路径 |
| 8 | 有效日期检查 | 现状是 `target.author() != null && effectiveFrom.isBefore(today)` 才拒绝（`NumberingRule.java:177`）：即**所有无作者版本一律豁免**。ADR 要求改为只豁免种子版本 1；要做到这点须能区分「种子版本 1」与「运维起草的无作者版本」——唯一可用判据是 `number == 1 && author == null`（第 13 节待核第 5 条） | 种子见基线 `V20261014100100__platform_numbering_baseline.sql:178` 起 |
| 9 | ADR 引用的迁移文件 | `V20261015000300__platform_numbering_create_rule_versions.sql` 与 `V20261015000600__platform_identity_create_authorization_requests.sql` **已不存在**（ADR-0038 开放期改基线），约束现在在各 schema 的 `…_baseline.sql`；`ck_numbering_rule_versions_confirmer_is_not_author`、`ck_numbering_rule_versions_two_different_confirmers` 仍在，`author_id` 仍可空（基线 `:102`、`:114-117`） | 行号漂移，不影响决定 |
| 10 | ADR 引用的行号 | 多处漂移（#72 合并后）：`ReconcileEmergencyAdmins` 下限常量在 `:28`，判断在 `:61-62`；`AdministratorSeparationReview` 常量 `:26`，原因文本 `:53-55`；`OperationsCommands.warnIfTooFew` `:89-92`；`GrantRoleByOperations` 角色码解析 `:54-55`；`ApplySourceSnapshot` HELD 判断 `:86` | 实现者以符号为准，不以行号为准 |
| 11 | 同步 HELD 与超管下限 | 属实：只有离职名单含 `super_admin` 或安全审计员持有人才 HELD；「少于 2 名」只进原因文本，且只在 `separatedAdministrators` 非空时才拼接（`AdministratorSeparationReview.java:53-56`） | 放宽项 3 只改这段文本里的数字 |
| 12 | 流程管理员启动检查 | `ProcessAdministratorCheck.verify()` 由 `EngineWiring` 在 `ApplicationReadyEvent` 调用（`EngineWiring.java:55-58`）：在所有 `ApplicationRunner` **之后**，所以运维命令进程不被它卡住；常驻服务在无流程管理员持有人时启动失败，配置键 `forcitis.platform.approval.process-administrator-required`（缺省 true）。这就是 dev 在本卡 rc 之前要带 `PROCESS_ADMINISTRATOR_REQUIRED=false` 的原因，见第 12 节「合并后」 | `ProcessAdministratorCheck.java:23-39` |
| 13 | `OperationsCommands.single()` 的多值行为 | `single()` 在 `--witness` 出现多次时返回 null（`OperationsCommands.java:122-128`）。一旦「null 的见证人」在单人模式下被当作省略，`--witness=a --witness=b` 或 `--witness=`（空值）就会**静默当作省略**，违背 CLAUDE.md 第 6 条（无效输入失败并告警）。必须区分「没给」与「给了但无效」 | 第 4 节 P2-07 |
| 14 | 其他位置对 ≥2 的依赖 | 只有 `ReconcileEmergencyAdmins`（常量与告警）、`OperationsCommands.warnIfTooFew`、`AdministratorSeparationReview` 三处；`DisableEmergencyAdmin` 等不检查下限 | `grep MINIMUM|< 2` |

## 3. 范围与卡上文件（越界先停下报告，不先做后报）

### P1：开关、启动自检、可见性
- 允许改动：
  - 新增 `backend/src/main/java/com/forcitis/one/platform/identity/` 下的：包内私有模式设置类型；`ApplicationEnvironmentPreparedEvent` 监听器（建议放 `infrastructure/startup/`，参照 `MigrateOnlyListener`，字符串判断、含 profile 白名单与 `identity-operations` 须带 `dev` 或 `prod`）；数据库判据检查（单例初始化阶段，类型与时机见第 13 节待核第 6 条）；启动 WARN 日志与 `platform.identity.single-operator-mode.enabled` 审计写入点；`api/` 下一个只读的模式查询（P2、P3 用）
  - `backend/src/main/resources/META-INF/spring.factories`（追加监听器登记；该文件被 `platform.integration` 的两个监听器共用，只追加一项，不动原有行）
  - `backend/src/main/resources/application-identity-operations.properties`（只改注释里的运行方式，`--spring.profiles.active` 改 `--spring.profiles.include`）
  - `backend/architecture/audit-text-writers.txt`（新启动审计写入点一行，行号取自允许内容表，见数据设计一节；**one-data 复核**）
  - `docs/modernization/audit/MUST_AUDIT_ACTIONS.yaml`（登记 `platform.identity.single-operator-mode.enabled`）
  - `backend/glossary/proposed/platform-identity.yaml`（第 11 节两条已由起草登记，实现者只在需要时补）
  - 新增本卡的单元测试 / 守护（`ProductionConfigSanityTest` 内加一个方法，或新类，见第 13 节待核第 7 条）
- 归属（§6.1 第 2 条）：本卡独占配置键 `forcitis.platform.identity.single-operator-mode`、审计动作 `platform.identity.single-operator-mode.enabled`
- 不碰：验收测试目录；`platform.numbering`、`organization.workforce`；`OperationAttestation` 及各运维命令（P2）；`application.properties`、`config/*.properties`、`application-dev.properties`、`application-prod.properties`（**仓库不发布该键**，ADR 第 1 节第 6 条）；`one.env.template`；`kernel` 共享构件；`MigrateOnlyListener`（只参照，不改）

### P2：`platform.identity` 内的放宽与 `revoke-role`
- 允许改动（均在 `platform/identity/application/` 及 `api/`，除非另注）：
  - `OperationAttestation`（见证人可选：仅模式开启时允许「没给」，给了就必须合法且不同于操作人；`describe()` 在省略时写 `witnessId=none`）
  - `OperationsCommands`（用法文本、`warnIfTooFew` 的阈值、新增 `revoke-role` 分派）
  - `GrantRoleByOperations`、`RegisterEmergencyAdmin`、`ResetEmergencyAdminCredentials`、`DisableEmergencyAdmin`、`EnableEmergencyAdmin`（放宽时同事务写标注审计）
  - 新增 `RevokeRoleByOperations`（与 `GrantRoleByOperations` 对称：收回角色、吊销该人全部会话、清缓存、发 `AccountRolesReplaced`、写审计）
  - 新增放宽项枚举（四值）与标注审计写入器
  - `ReconcileEmergencyAdmins`（下限取自模式）、`EmergencyBinding`（只读，若需 `usableCount` 之外的接口）
  - `AdministratorSeparationReview`（原因文本下限取自模式）
  - `platform.identity.api` 的只读模式查询（P1 已建，P2 只使用）
  - `architecture/audit-text-writers.txt`（`GrantRoleByOperations` 由 1 变 2 等次数更新；`RevokeRoleByOperations`、标注写入器新行；**one-data 复核**）
  - `docs/modernization/audit/MUST_AUDIT_ACTIONS.yaml`（`platform.identity.single-operator-mode.applied`、`platform.identity.operations.account.role.revoked`）
  - `backend/glossary/proposed/platform-identity.yaml`
- 归属：本卡独占上述审计动作
- 不碰：验收测试目录；既有断言；`ck_authorization_requests_two_people` 及全部库约束；`ReplaceAccountRoles`、`CreateRole`、`UpdateRole`、`AssigneeResolver` 等第 4 节「不放宽」所列代码；`organization.workforce`（`ApplySourceSnapshot` 不改，ADR 影响面明确）；`platform.numbering`

### P3：`platform.numbering` 编号运维起草
- 允许改动：
  - `platform/numbering/` 下：新用例（运维起草，作者为空，记录操作人，写标注 `platform.numbering.single-operator-mode.applied`，模式关闭一律拒绝）；`NumberingRule` 的系统起草方法与 `confirmable` 的有效日期判断；`api/NumberingError`（新错误码，已登记提议术语，第 11 节）
  - 运维入口分派方式的实现（第 13 节需决定第 3 条定后；不得让 `platform.identity` 依赖 numbering 的应用层）
  - `architecture/audit-text-writers.txt`、`MUST_AUDIT_ACTIONS.yaml`（`platform.numbering.single-operator-mode.applied` 与运维起草的正常审计动作）
  - `backend/glossary/proposed/platform-numbering.yaml`
- 不碰：验收测试目录；`NumberingRuleFourEyesIT` 的既有断言（含 `:303-304`）；库约束与基线 DDL；`platform.identity`（除 P2 已建的只读查询、放宽项枚举与标注写入器的使用）

### P4：生产前纵深防御与文档
- 允许改动：`docs/modernization/phase1a/infra/runbooks/files/forcitis-one.service`（`ExecStartPre`）；`scripts/release/remote_install.sh`（prod 正则与 `systemctl show forcitis-one -p DropInPaths` 检查）；`scripts/release/deploy_gate.py`（`PROD_PREREQUISITES` 加 C-11 与自检）；`docs/modernization/phase1a/infra/PREDEPLOY_CHECKLIST.md`（C-11、C-12，A-3 时点，D-5 说明；现有 C-13 已引用 C-11，行不存在）；`docs/modernization/phase1a/SECURITY_INVARIANTS.md`（I-14）；`INFRA_RUNBOOK.md` §6 与 `runbooks/C-5-first-admins.md`（include 写法，归属见第 13 节需决定第 5 条）；`DEV_DEPLOY_REMAINING.md`、`DECISIONS_A.md` A-3、`handoff/B-8-numbering-templates.md`；`adr/src/ADR-0026.md`、`ADR-0027.md`、`ADR-0028.md` 状态行加「…已被 ADR-0030 取代」（ADR 生成物用 `build_all.py`，不直接改生成产物，CLAUDE.md 第 12 条）
- 部署守护测试：`ProductionConfigSanityTest` 内或新类（`one.env.template` 不含该键，单元文件含 `ExecStartPre`，安装器含正则与 `DropInPaths`，`deploy_gate.py` 自检）
- 不碰：后端 Java；dev 的 `one.env`（ECS 上由 one-ops 手工改，见第 12 节）；ECS 上任何文件

## 4. 验收测试（由测试代理先写，§6.1 第 8 条）
- 测试代理：`code-modernization:test-engineer`（sonnet），每个 PR 先于实现提交，提交说明带 `Acceptance-Test-Author: test-engineer`
- 路径：后端 `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/`、`…/acceptance/platform/numbering/`、`backend/src/test/resources/acceptance/…`
- 编号即 `@DisplayName` 前缀（`SINGLE-OP P1-nn`、`P2-nn`、`P3-nn`、`P4-nn`）
- 实现方认为验收测试有误：停下报告，由复审裁定（`Acceptance-Ruling: RULING-SINGLE-OP-n`），不自行修改

### 4.0 测试基建：开启模式要用名为 `forcitis_one_dev` 的库和 `one_dev_*` 角色
ADR 第 1 节第 4 条的取舍：白名单判据使开启模式的测试必须把库建成 `forcitis_one_dev`、角色建成 `one_dev_*`。做法：
1. 容器：`TestPostgres.container().withDatabaseName("forcitis_one_dev").withUsername("one_dev_app").withPassword(<运行时生成>)`（参照 `RoleSeparatedDeploymentIT` 常量 `DATABASE`、`RUNTIME`；`TestPostgres.shared()` 的库名与用户名是默认值，**不可用于开启模式的用例**）。镜像取自 `backend/architecture/test-images.txt` 已登记的 `postgres:18`，属长期授权的临时容器，不必逐次问。
2. 迁移：容器的初始用户是超级用户，在该库内按 `TestPostgres.migratedTemplate` 同样的方式跑一次 Flyway（init-schemas 加 `classpath:db/migration`），只需要读判据的用例（P1-13、P1-14）不需要迁移。
3. 反例库：同一容器里用超级用户 `CREATE DATABASE forcitis_one_prod`、`forcitis_one_dev2`、`forcitis_one_dev_x`、`FORCITIS_ONE_DEV`，`CREATE ROLE one_prod_app LOGIN`、`app`、`xone_dev_app`、`ONE_DEV_app` 并授权连接，用不同的 JDBC URL / 用户名组合驱动判据（一个容器、多组合，避免每个反例起一个容器）。
4. 应用级 IT（P2、P3 的大部分用例）**不走真实启动检查**：沿用 `IdentityIntegrationTest` 底座（库名为 `TestPostgres` 默认），在测试配置里覆盖 P1 建立的只读模式查询 bean 为固定 true / false；启动检查本身只由 P1 用例覆盖。这是测试代码里的替身，不进主代码（I-4 禁止的是主代码里的旁路）。另设 P2-22 一个端到端用例，真实走键 + `forcitis_one_dev` 库 + 子进程，证明整条链路。
5. 子进程：参照 `MigrateOnlyIT` + `MigrateOnlyLauncher`（`java -cp <classpath> <Launcher> --spring.profiles.active=…`）；退出码、输出与库内计数在子进程退出后查。需要新增一个运维命令的 Launcher（测试目录内）。
6. 监听器层的判定（P1-01 至 P1-12）不需要数据库：用 `ConfigurableEnvironment` 加属性源直接驱动监听器的静态判定方法，环境变量用名为 `systemEnvironment` 的 `SystemEnvironmentPropertySource` 构造，命令行用 `SimpleCommandLinePropertySource`，`SPRING_APPLICATION_JSON` 用 `spring.application.json` 属性；**监听器的判定须暴露为包内可测的静态方法**（与 `MigrateOnlyListener.migrate` 同一写法）。

### P1：开关与启动自检（ADR 第 1 节、守护第 1、2、9、10 条）
监听器判定（无数据库）：
  - `SINGLE-OP P1-01 键未设置：任何 profile 组合（无、dev、prod、dev+identity-operations）都不因此失败，模式为关闭`
  - `SINGLE-OP P1-02 值恰为 false：dev 通过，模式为关闭；无 profile 与非 dev 非 prod 的 profile 同样通过`
  - `SINGLE-OP P1-03 值恰为 true 且 profile 含 dev 不含 prod：通过，模式为开启`
  - `SINGLE-OP P1-04 取值不是字面量 true 或 false 时启动失败：空串、仅空白、" true"、"true "、TRUE、True、yes、1、on、null（逐值参数化）`
  - `SINGLE-OP P1-05 同一判定覆盖全部来源：环境变量 FORCITIS_PLATFORM_IDENTITY_SINGLE_OPERATOR_MODE、命令行 --forcitis.platform.identity.single-operator-mode、SPRING_APPLICATION_JSON、宽松写法（驼峰与下划线）；空串在每个来源下都失败，不被当作未设置`
  - `SINGLE-OP P1-06 激活 profile 含 prod 时，键存在即启动失败：true、false、空串、乱码、值带空格，逐值`
  - `SINGLE-OP P1-07 值为 true 时 profile 必须含 dev 且不含 prod，其余一律失败：仅 identity-operations、无 profile、仅 prod、dev+prod、migrate、仅 test（逐组合）`
  - `SINGLE-OP P1-08 identity-operations 不带 dev 或 prod 时启动失败，无论模式键是否设置；identity-operations+dev 与 identity-operations+prod 在键未设置时通过`
  - `SINGLE-OP P1-09 启动失败的信息指明配置键与 ADR-0030，且不回显取值之外的任何配置值（数据源口令等）`
  - `SINGLE-OP P1-10 只在启动时读取：上下文启动后改环境属性源，模式查询的结果不变；没有刷新入口、没有对外 API 暴露模式（actuator 仍只暴露 health 与 prometheus 的既有集合）`
数据库判据（Testcontainers，见 4.0）：
  - `SINGLE-OP P1-11 值为 true 时库名不是 forcitis_one_dev 即启动失败：forcitis_one_prod、forcitis_one_dev2、forcitis_one_dev_x、FORCITIS_ONE_DEV（逐库）`
  - `SINGLE-OP P1-12 值为 true 时会话用户不以 one_dev_ 开头即启动失败：one_prod_app、app、xone_dev_app、ONE_DEV_app（逐用户；库名正确）`
  - `SINGLE-OP P1-13 库名与会话用户都满足时启动通过；模式关闭（键为 false 或未设置）时不做这两项检查，库名为 forcitis_one_prod 的环境照常启动`
  - `SINGLE-OP P1-14 检查先于任何 ApplicationRunner：identity-operations+dev、值为 true、库名 forcitis_one_prod，子进程退出码非 0，且库内授予（账号角色）、应急账号与审计的行数与启动前相同`
  - `SINGLE-OP P1-15 检查先于 Web 服务器接受请求与调度器启动：dev、值为 true、库名错误的常驻形态，上下文刷新失败，指定端口没有任何监听，调度任务计数为 0`
可见性：
  - `SINGLE-OP P1-16 模式开启时每次启动写一行固定文本的 WARN 日志，含激活 profile、数据库名与「ADR-0030」；模式关闭时没有这行`
  - `SINGLE-OP P1-17 模式开启时每次启动写一条审计 platform.identity.single-operator-mode.enabled，actor 为空；连续启动两次得两条；模式关闭不写`
守护：
  - `SINGLE-OP P1-18 仓库不发布该键：src/main/resources 下所有 properties 与 yaml、config/*.properties、one.env.template 都不匹配 (?i)single[-_.]?operator；扫描函数对注入的样例文件（含该键）能报出，证明不是空转`
  - `SINGLE-OP P1-19 源码扫描：platform.identity 以外的主代码不出现该键（点号、连字符、下划线、驼峰、环境变量写法）；样例注入能报出`
  - `SINGLE-OP P1-20 设置类型为 platform.identity 包内私有且不是 @RefreshScope、不带公开的 @ConfigurationProperties；Spring Modulith 校验通过（ModularityTest 原样重跑）`
  - `SINGLE-OP P1-21 GlossaryTest（G37）与 NamingSurfaceTest（G46 配置键前缀）原样重跑通过；MustAuditActionsTest、AuditWritePointsGuardTest、AuditDetailGuardTest 原样重跑通过`
  - `SINGLE-OP P1-22 模式缺省关闭，已有全部测试行为不变（全量后端测试重跑，不改任何既有断言）`

### P2：identity 内的放宽与 revoke-role（ADR 第 2 项第 1、2、3 行，第 3 项第 4 行，守护第 4、5、6、8 条）
见证人省略：
  - `SINGLE-OP P2-01 模式开启：grant-role 省略 --witness 成功，授予生效；正常审计 operations.account.role.granted 照写，detail 为 operatorId=<UUID> witnessId=none role=<码>`
  - `SINGLE-OP P2-02 同一事务中另写标注审计 platform.identity.single-operator-mode.applied：subjectKey 与正常条目相同，detail 恰为放宽项代码；命令在写完正常审计后失败回滚时两条都不留`
  - `SINGLE-OP P2-03 逐个运维操作（register-emergency-admin、reset-emergency-admin-credentials、disable-emergency-admin、enable-emergency-admin、grant-role、revoke-role）模式开启下省略见证人均成功并各写一条标注；"已是目标状态、无改动"的情形不写标注（待核第 9 条）`
  - `SINGLE-OP P2-04 模式开启：--operator 缺失仍被拒（退出码 1，错误码 PLATFORM_IDENTITY_OPERATION_WITNESS_REQUIRED 的既有形态），库内无变化、无标注`
  - `SINGLE-OP P2-05 模式开启：操作人与见证人同一员工 ID 仍被拒，无变化、无标注`
  - `SINGLE-OP P2-06 模式开启：给了 --witness 但不是小写规范 UUID（大写、姓名、残缺）仍被拒，不被当作省略`
  - `SINGLE-OP P2-07 模式开启：--witness 出现两次、--witness= 空值、--witness 无值，一律被拒，不被当作省略`
  - `SINGLE-OP P2-08 模式开启且给了合法的不同见证人：行为与模式关闭时逐字节相同（detail 为 operatorId=… witnessId=<UUID>…），不写标注（待核第 9 条）`
  - `SINGLE-OP P2-09 模式关闭：省略 --witness 仍被拒（退出码 1），库内无变化、无标注；同名仍被拒`
  - `SINGLE-OP P2-10 模式关闭时全部既有运维命令用例原样重跑（OperationsCommandsIntegrationTest、OperationAttestationTest、OperationsCommandEmployeeIdIT），不改断言`
应急账号下限：
  - `SINGLE-OP P2-11 每日核对：模式开启、可用 1 个 → 无问题、不告警；可用 0 个 → 告警；唯一账号的绑定人已不持有 super_admin → 告警；绑定人账号停用 → 告警；账号本身停用 → 告警`
  - `SINGLE-OP P2-12 每日核对：模式关闭、可用 1 个 → 仍告警（下限 2），消息仍写 at least 2`
  - `SINGLE-OP P2-13 登记提示：模式开启登记第 1 个账号后不再提示「至少 2 个」；模式关闭时仍提示；两种模式下提示文本之外的输出（凭证展示）不变`
  - `SINGLE-OP P2-14 应急账号只能绑定当前的 super_admin 持有人（EmergencyBinding）：模式开启下绑定一个不持有 super_admin 的人仍被拒`
同步暂停原因文本：
  - `SINGLE-OP P2-15 AdministratorSeparationReview：同一输入下，模式开启与关闭给出相同的 separatedAdministrators、actorRetained、remaining；只有 reason 文本里的下限不同（开启写 at least 1，关闭写 at least 2）`
  - `SINGLE-OP P2-16 同步运行对唯一超管离职：两种模式下都进入 HELD，由留任的持有人放行；模式不改变放行条件`
revoke-role：
  - `SINGLE-OP P2-17 revoke-role 收回已持有的角色：账号不再持有、该人全部会话被吊销、权限缓存被清、发布账号角色变更事件、写审计 platform.identity.operations.account.role.revoked（detail 同 grant-role 的格式）`
  - `SINGLE-OP P2-18 revoke-role 对未持有的角色：退出码 0，输出「无变化」，无审计、无标注；对不存在的角色码：拒绝（退出码 1，ROLE_NOT_FOUND）；对没有账号的员工：拒绝，不开户`
  - `SINGLE-OP P2-19 revoke-role 在模式关闭时要求见证人，开启时可省略并写标注；收回敏感角色同样可由运维命令完成（对应 ADR 第 3 项第 4 行）`
封闭清单与登记：
  - `SINGLE-OP P2-20 放宽项枚举恰好四个取值，与 ADR 第 2 项表逐行对应（见证人省略、应急账号下限一个、超级管理员下限一个、模板版本无作者）；新增或改名取值使测试失败；每个标注写入点只接受该枚举，不接受自由文本`
  - `SINGLE-OP P2-21 全部新增审计动作已登记在 MUST_AUDIT_ACTIONS.yaml 与 audit-text-writers.txt，detail 不含 witness= 或 operator= 字面量，不含姓名、登录名（AuditDetailGuardTest、AuditWritePointsGuardTest、MustAuditActionsTest 原样通过）；运维用法文本含 revoke-role`
  - `SINGLE-OP P2-22 端到端：identity-operations+dev、键为 true、库名 forcitis_one_dev、用户 one_dev_app、子进程运行 grant-role 不带 --witness，退出码 0、进程自行结束；库内出现授予、正常审计、标注审计，启动 WARN 与 enabled 审计各一；同一命令键未设置时退出码 1 且无变化`
  - `SINGLE-OP P2-23 回归：模式开启下应用内敏感授予的本人确认仍 403（SensitiveGrantTwoPersonIntegrationTest 场景），ck_authorization_requests_two_people 直接改库仍被拒；既有断言原样重跑`

### P3：编号运维起草（ADR 第 2 项第 4 行，守护第 7、9 条）
  - `SINGLE-OP P3-01 模式关闭：编号运维起草命令被拒绝（新错误码），库内无新版本、无审计、无标注`
  - `SINGLE-OP P3-02 模式开启：起草出 author_id 为空、状态待确认的新版本，版本号为现有最大号加 1，原待确认版本记为已取代；发布 NumberingRuleReconfigured`
  - `SINGLE-OP P3-03 起草的审计记操作人（员工 ID）并写标注 platform.numbering.single-operator-mode.applied，detail 为「模板版本无作者」的放宽项代码；正常审计格式不变`
  - `SINGLE-OP P3-04 --operator 缺失或不是小写规范 UUID 被拒；见证人参数的处理见待核第 10 条`
  - `SINGLE-OP P3-05 无作者版本由持 approve 权限的用户在界面确认成功，状态变已确认，生效日之后按该版本发号；确认人不是作者的既有路径不受影响`
  - `SINGLE-OP P3-06 有效日期早于确认当天的运维起草版本不能确认（422，EFFECTIVE_DATE_PASSED）；种子版本 1（无作者、版本号 1、生效日 2000-01-01）仍可确认——有效日期检查对所有起草版本生效，只豁免种子版本 1`
  - `SINGLE-OP P3-07 界面起草的版本，作者本人确认仍 403（CONFIRMER_IS_AUTHOR）`
  - `SINGLE-OP P3-08 凭证号：同一人做业务确认与财务确认仍被拒（CONFIRMATION_REPEATED），对无作者版本同样；两条库约束与 NumberingRuleFourEyesIT 的直接改库断言原样通过`
  - `SINGLE-OP P3-09 platform.numbering 只经 platform.identity.api 查询模式：源码扫描无 identity 的 application 或 infrastructure 依赖；platform.identity 不依赖 platform.numbering 的应用层（ModularityTest、ModuleDependencyTest 通过）`
  - `SINGLE-OP P3-10 起草参数无效（单据类型不存在、模板文本非法、起始值小于 1、生效日缺失）：被拒，库内无变化，不吞异常`
  - `SINGLE-OP P3-11 platform.numbering.single-operator-mode.applied 与运维起草的正常动作已登记（MustAuditActionsTest、AuditWritePointsGuardTest 通过）`

### P4：部署守护与文档（ADR 守护第 3、11 条）
  - `SINGLE-OP P4-01 forcitis-one.service 含 ExecStartPre 预检；预检脚本在 prod profile 且 one.env 匹配 (?i)single[-_.]?operator 时返回非 0（守护只验证存在与该条件，不声称不可绕过）`
  - `SINGLE-OP P4-02 one.env.template 不含该键；与 P1-18 同一扫描共用`
  - `SINGLE-OP P4-03 remote_install.sh 对 prod 含同一正则检查与 systemctl show forcitis-one -p DropInPaths 检查：prod 上存在任何 drop-in 即拒绝安装`
  - `SINGLE-OP P4-04 deploy_gate.py 的 PROD_PREREQUISITES 含 C-11，PREDEPLOY_CHECKLIST 有 C-11 与 C-12 行，自检要求二者存在；C-11 未完成时 prod 部署被拒`
  - `SINGLE-OP P4-05 文档中的运维命令形态不再含 --spring.profiles.active=identity-operations（INFRA_RUNBOOK §6、C-5 剧本）`
  - `SINGLE-OP P4-06 SECURITY_INVARIANTS 含 I-14，其测试对照表指向 P1-01 至 P1-15 与 P2-20`

## 5. 执行者与模型
- 测试代理：`code-modernization:test-engineer`，**sonnet**，每个 PR 先写验收（红）并先提交
- 实现：子代理，**sonnet**，每个 PR 一个独立 worktree，分支建议 `claude/one-1a-par-single-op-p1`、`-p2`、`-p3`、`-p4`（名字以负责会话为准），基线为各自开工时的 main
- 首轮复审：**opus**，推理强度 high，P1、P2、P3 必须；P4 用 sonnet 复审即可（文档与脚本，不含新的判定逻辑，但安装器正则与 `DropInPaths` 检查由 opus 过一遍）。修后复核 **sonnet**；最多两轮，之后新发现的非 High 列为残余项在 PR 单列（ 第 6 条）
- 用 opus 终审安全相关的 P1、P2、P3 的理由：本卡全部是安全控制的放宽，评审者要判断的不是代码风格，而是（a）白名单是否真的覆盖所有进程入口（常驻服务、运维命令、迁移、只读核实命令），（b）检查时点是否真的早于 `ApplicationRunner`、Web 服务器与其他会写库的单例（含 Flowable 引擎建表），（c）「没给」与「给了但无效」是否被严格区分（第 2.3 节第 13 条），（d）放宽是否真的封闭、标注是否与放宽一一对应且同事务。这几项需要对 Spring 启动顺序和安全不变量做对抗性判断，属 METHODS.md 与模型分级策略中「安全判定、结构设计」用 opus 的范围。
- 数据设计：不涉 DDL（见数据设计一节），仍请 one-data 审查审计允许内容表与登记文件的变化后再开工
- 不交 Codex：全是安全代码

## 6. 禁止事项
- 不改卡外文件；不顺手重构、改名或统一风格
- **不放宽、不删除任何既有断言**，特别是 `ck_authorization_requests_two_people`、编号的两条库约束、`NumberingRuleFourEyesIT.java` 的直接改库断言；不改验收测试目录
- 不新增放宽项（ADR：新增任何放宽须另立 ADR）；不为模式增加运行期开关、HTTP 接口、刷新入口或 actuator 暴露
- 不在 `application*.properties`、`config/*.properties`、yaml、`one.env.template` 中出现该键；不在 `platform.identity` 以外读该键
- 不把「没给见证人」与「给了无效值」混为一谈；缺失或无效输入失败并告警，不静默跳过（CLAUDE.md 第 6 条）
- 不改 `ApplySourceSnapshot` / `organization.workforce`；不实现 ADR 明确不在清单内的「超管将为 0 时的运维放行途径」（ADR 第 2 项第 1 行脚注：以后另立 ADR）
- 不用黑名单；不加「只要不是 prod 就放行」的任何判断
- 不新增表、列、约束、迁移文件（ADR-0038）；不改 `kernel` 共享构件（需要时停下报告）
- 审计 detail 不带姓名、登录名、地址；不出现 `witness=`、`operator=` 字面量；日志不带口令、密钥
- 不打印、不写入密钥；测试密钥与口令运行时生成；代码与测试不写密钥字面量
- Testcontainers 临时容器（镜像限 `backend/architecture/test-images.txt`）可直接跑；其他容器、**任何常驻库（ECS 上的 dev 库）**、对外调用、生产取证先问；不建账号；不连 ECS
- 用语遵守 `terms.yaml`；不用「PSA」指代系统；注释能不写就不写（CS-1.1）

## 7. 预期表结构 diff
- 无。本卡不改任何迁移文件、不动任何表、列、约束、索引、种子；`git diff origin/main -- backend/src/main/resources/db` 须为空，PR 里贴这一条的输出作为证明。

## 8. 交付物与自查
- 交付物：见第 3 节各 PR；每个 PR 带验收与单元测试；提议术语（第 11 节）；`audit-text-writers.txt`、`MUST_AUDIT_ACTIONS.yaml` 的登记
- 守护文件：`data-access-registry.yaml`、`data-classification.txt`、`ddl-naming-baseline.txt`、`primary-key-exemptions.txt` 与各 `*-exceptions.txt` 本卡不改、不新增条目
- 自查：`scripts/precheck.sh origin/main` 全部 PASS 或有理由的 SKIP，输出贴进报告与 PR「precheck.sh 输出」；`pre-commit install`；报告前 gitleaks 8.30.1 扫全历史
- 受影响模块测试：precheck 给出的命令；交付前全量 `scripts/ci/backend_verify.sh`（起容器的只用白名单镜像）
- PR 正文单列：数据设计签发行或 one-data 对「不涉 DDL」的确认；`db/migration` diff 为空的证明；改动的既有断言（预期为零）；P1-14 / P2-22 子进程用例的实际输出摘要
- 报告分开写「已验证」与「未运行」；开启模式的用例是否真的跑过（不是被跳过）单独写一句

## 9. 复审要求（§6.1 第 4、11 条）
- Blocker / High：opus 复核，合并前修完；Medium / Low：执行者补测试、CI 通过即关闭，负责会话抽查
- 复审清单：DATA_DESIGN_CHECKLIST §14、§14.1；CODING_STANDARD CS-1.8、CS-1.9、CS-2.24、CS-5.5、CS-5.6；SECURITY_INVARIANTS I-4、I-11 至 I-13
- 专查（安全 PR 的对抗性问题）：
  1. 白名单是否对**每一个**能连库的进程入口生效：常驻服务、`identity-operations`、`migrate`、`integration-operations`、`read-only-verification`（后两者与模式无关，但值为 true 时是否也被检查，由待核第 8 条定）
  2. 取值读取是否绕过 Boolean 宽松绑定（空串、`TRUE`、` true` 都失败）
  3. 检查时点：先于 Flowable 引擎等会写库的单例、先于 `ApplicationRunner`、Web 服务器与调度器；失败后进程「什么都没做」
  4. 「没给」与「给了无效」的区分（`single()` 多值返回 null 的坑）
  5. 标注与放宽一一对应、同事务；正常审计格式在模式关闭与「给了见证人」时与现状逐字节相同
  6. 仓库不发布该键的守护对 `one.env.template`、yaml、config 目录都有效，并带反例
  7. 数据库判据用 `current_database()` 与会话用户的**精确**规则（前缀 `one_dev_` 区分大小写；库名全等）
  8. `platform.identity` 不依赖 numbering 应用层；numbering 只读 api
  9. 没有顺手放宽 ADR 第 4 节「不放宽的规则」所列任何一条

## 10. 报告去向
- 每个 PR 完成、部分完成或阻塞，都向 one-1a-par，抄 one-coordinator（ 第 7 步）
- P1、P2 合并后立即通知 one-coordinator 与 one-ops：可以出 rc（见第 12 节）
- PR／卡／交接记录（消息按 TEAM-53 四节点及需动作事件送达，不另报号）：分支、改动文件、验证结果（已验证 / 未运行）、precheck 输出、`db/migration` diff 证明、未完成项、需要用户决定的事

## 11. 本卡新词（开工前术语门禁）
已查 `docs/modernization/glossary/terms.yaml` 与 `backend/glossary/proposed/*.yaml`：下列各词在同一 bc 内无同名、同中文名条目（`grep` 中文名「单人运维模式」「单人运维放宽项」「须开启单人运维模式」、英文名与 snake 名均无命中；仓库内与「单人」相关的现有词条为零）。起草本卡时已把前三条登记为提议术语（`status: proposed`），其余是实现者在实现 PR 里登记的候选。

| 标识符 | 中文名 | 种类 | 使用范围 | bc | 说明 |
|---|---|---|---|---|---|
| `single_operator_mode`（en `SingleOperatorMode`） | 单人运维模式 | 值对象 | 领域 | `platform.identity` | 已登记于 `platform-identity.yaml`。ADR 点名的提议术语 |
| `single_operator_relaxation`（en `SingleOperatorRelaxation`） | 单人运维放宽项 | 值对象（四值枚举） | 领域 | `platform.identity` | 已登记于 `platform-identity.yaml`。ADR 说「名称在实现时登记」，类型名先定，**取值名待 one-glossary 确认** |
| `error__platform_numbering_single_operator_mode_required`（`PLATFORM_NUMBERING_SINGLE_OPERATOR_MODE_REQUIRED`） | 须开启单人运维模式 | 错误码 | 领域 | `platform.numbering` | 已登记于 `platform-numbering.yaml`。中文名全局唯一已核；`ErrorKind` 与 HTTP 状态实现时按 CS-2.24 复核，登记暂写 403 |
| 放宽项四个取值，建议标识符：`WITNESS_OMITTED`（见证人省略）、`EMERGENCY_ACCOUNT_MINIMUM_ONE`（应急账号下限一个）、`SUPER_ADMIN_MINIMUM_ONE`（超级管理员下限一个）、`TEMPLATE_VERSION_WITHOUT_AUTHOR`（模板版本无作者） | 如括号 | 值对象的取值 | 领域 | `platform.identity` | **未登记**。是否要作为词条登记，取决于 `GlossaryTest`（G37）对枚举常量的检查；若失败则按报错登记词条或 common_words。四个中文名需 one-glossary 在 P2 开工前确认，避免与「见证人」（`witness`，已有词条）、「超级管理员」「应急账号」的既有中文写法冲突 |
| 审计动作 `platform.identity.single-operator-mode.enabled`、`platform.identity.single-operator-mode.applied`、`platform.numbering.single-operator-mode.applied`、`platform.identity.operations.account.role.revoked` | — | 审计动作 | — | — | 登记于 `MUST_AUDIT_ACTIONS.yaml`，不进术语表（与现有 `platform.identity.operations.account.role.granted` 同类） |
| 运维命令名 `revoke-role`（ADR 指定）、编号起草命令名（待定，见第 13 节） | — | 命令行操作名 | — | — | 与 `grant-role` 同类，不进术语表；若 G 守护要求，则按报错处理 |
| 配置键 `forcitis.platform.identity.single-operator-mode` | — | 配置键 | — | `platform.identity` | G46 合规，ADR 已定 |

## 12. 顺序与前置，以及合并后（抄送 one-ops）
1. 本卡 → one-coordinator 看 → one-data 对「审计允许内容表与登记文件的变化」出意见（数据设计一节）→ P1 验收测试（红）→ P1 实现 → opus 复审 → 合并 → P2 同样 → 合并 → **出 rc（见下）** → P3 → P4。
2. P3、P4 在 dev 的 rc 之后进行，P4 必须在生产部署前完成（C-11 是生产闸门）。

### 合并后（抄送 one-ops）
- **rc**：P1、P2 合并后由 one-ops 出新 rc（`DEV_DEPLOY_REMAINING.md` S-1 已有「新 rc」一项；按调度称 rc.4）。dev 库由 one-ops 在 ECS 上手工安装；**本卡不碰任何常驻库，也不连 ECS**。
- **在 rc 出来之前**：dev 常驻服务不带 `process_administrator` 持有人，应用启动时 `ProcessAdministratorCheck` 在 `ApplicationReadyEvent` 失败。临时做法是在 dev 的 `one.env` 设置 `FORCITIS_PLATFORM_APPROVAL_PROCESS_ADMINISTRATOR_REQUIRED=false`（对应配置键 `forcitis.platform.approval.process-administrator-required`，缺省 true；用户转达的简写 `PROCESS_ADMINISTRATOR_REQUIRED=false` 的**全称待 one-ops 核**，见第 13 节待核第 11 条）。此时检查只记 WARN 不阻止启动（`ProcessAdministratorCheck.java:33-38`）。
- **rc 出来后**，按 ADR 迁移方案第 2 条，由 one-ops 在 ECS 上依次做：
  1. 在 dev 的 `/etc/forcitis-one/one.env` 加 `FORCITIS_PLATFORM_IDENTITY_SINGLE_OPERATOR_MODE=true`（**只写这里，不写 `one.env.template`、不进仓库**）。注意：该键为 true 时，进程只在 profile 含 `dev` 且不含 `prod`、库名为 `forcitis_one_dev`、会话用户以 `one_dev_` 开头时才启动；one.env 里的 `SPRING_PROFILES_ACTIVE` / 数据源必须已是这套 dev 值，否则服务起不来（这是设计）。值只能是小写 `true` 或 `false`，不能留空串，不能带空格。
  2. 以 `--spring.profiles.include=identity-operations`（不再用 `--spring.profiles.active`；环境档案 dev 来自 one.env）、不带 `--witness` 运行运维命令，授予用户本人 `super_admin` 与 `process_administrator`，登记 1 个应急账号；`--operator` 填用户本人的员工 ID。命令里的 `-p UnsetEnvironment=SERVER_PORT -p UnsetEnvironment=MANAGEMENT_SERVER_PORT` 兜底（OPS-PORT）保持。
  3. 确认启动 WARN 日志、`enabled` 审计与每次放宽的标注审计都已出现（SQL 只读查询，不打印机密）。
  4. **`FORCITIS_PLATFORM_APPROVAL_PROCESS_ADMINISTRATOR_REQUIRED=false` 的退出条件**：用户本人已持有 `process_administrator` 之后，删除该行并重启，确认常驻服务在检查开启下正常启动；删除前后各看一次启动日志。若删除后启动失败，回填并报 one-1a-par。
- **运维手册（C-5、INFRA_RUNBOOK §6）改 include 写法的改动归谁**：见第 13 节需决定第 5 条。在改之前 one-ops 照本节第 2 步的形态执行。
- **P4 之后（生产前）**：C-11 关闭单人模式、C-12 启用第二名超管与第二个应急账号；ECS 复用为生产时重新生成 `EMERGENCY_TOTP_ENCRYPTION_KEY`（ADR 第 5 项第 3 条、第 6 项）。

## 13. 待核事项与需要决定的点

### 待核（证据不足，开工前先核实）
1. **U-1 的答复**：ADR 状态行列了 U-2 / U-3 / U-4 / U-5 / U-6 的答复，U-1 以「批准并接受例外 (Recommended)」覆盖（intent `…ADR-0030-dev-单人操.md`「落点」写明「U-1 接受为 ADR-0026「不设配置开关」的例外」）。六项均已答复；请 one-coordinator 确认无遗漏。
2. **ADR 与代码现状不一致，实现以代码符号为准**：第 2.3 节第 2、3、9、10 条（员工 ID 而非名字、`witness=none` 与 `AuditDetailPolicy` 冲突、迁移文件已不存在、行号漂移）。
3. **`witness=none` 的落地写法**：`AuditDetailPolicy` 的禁止模式 `"…(address|agent|loginName|operator|witness)="` 会命中字面量 `witness=`。建议写 `witnessId=none`（与现有 `witnessId=<UUID>` 一致）。ADR 条文写的是 `witness=none`，**需 one-data / one-coordinator 确认以 `witnessId=none` 为准**，并同步 ADR 的措辞（通过 ADR 片段修订，不直接改生成物）。
4. **审计允许内容表（DATA-REPAIR-timesheet-audit TB-01 第 2 项）需新增 / 扩展哪几行**：现有行 15 的内容是 `operatorId=<UUID> witnessId=<UUID>`（`GrantRoleByOperations` 另加 `role=`）；`witnessId=none` 与放宽项代码属新增内容；启动审计（`enabled`）与编号起草是新写入点。表属 one-data 的卡，本卡不改，等 one-data 回复要新增的行号（`AuditWritePointsGuardTest` 的 `LAST_ROW` 也可能要加）。
5. **种子版本 1 的判据**：`number == 1 && author == null`。需核两点：种子是否对每个单据类型都是版本 1（基线 `:178` 起的 INSERT）；以及未来若有「运维起草的版本 1」是否可能出现（版本号总是在现有最大号上加 1，起草时已有种子，所以不会）。
6. **启动检查的 Spring 机制与时点**：ADR 写「上下文刷新的单例初始化阶段」。`SmartInitializingSingleton.afterSingletonsInstantiated` 在所有单例创建之后，**此时 Flowable 引擎等单例已经创建，若它们创建时就写库（建表、升级、锁），则检查晚于这些写入**，与「进程什么都没做就退出」不符。实现者须核实：Flowable、Modulith 事件登记、ShedLock 等在 bean 创建期是否访问数据库；如有，检查应放在更早的 bean（例如其他单例对其有依赖的检查 bean，或 `BeanFactoryPostProcessor` 内取数据源直接查），并用 P1-14、P1-15 加一个「引擎建表前失败」的断言。
7. **守护落点**：ADR 守护第 1 条写「`ProductionConfigSanityTest` 加扫描」，第 2 条写新增 `SingleOperatorModeStartupTest`。验收测试按流程放在 `acceptance/…` 且由测试代理写、实现者不得改；而在 `ProductionConfigSanityTest` 里新增方法算改守护文件。建议：扫描守护放新类（验收目录），`ProductionConfigSanityTest` 不动；**请 one-coordinator 确认可否偏离 ADR 的落点文字**。
8. **值为 true 时，非 `OperationsCommandRunner` 的入口（`integration-operations`、`read-only-verification`、`migrate`）要不要也受检**：ADR 第 1 节第 3 条说「覆盖所有进程」；`migrate` 与 `read-only-verification` 在 `ApplicationEnvironmentPreparedEvent` 阶段就 `System.exit`，监听器顺序（`MigrateOnlyListener` 是 `LOWEST_PRECEDENCE`）会决定先后。要核：值为 true 且 profile 为 `migrate`（无 dev）时是否应因 profile 白名单失败；`read-only-verification` 通常只带自己的 profile，会因白名单失败，是否符合预期（它只连 SQL Server，不连本库）。
9. **「每次放宽」的触发点**：(a) 省略见证人 —— 明确是命令里没给 `--witness` 时写标注；给了合法见证人（模式开启）不写（P2-08）。(b) 应急账号下限 1、同步暂停原因文本下限 1 —— 是每次核对 / 每次生成原因文本都写，还是只在「按旧规则本会告警 / 本会写 2」的情形（可用数恰为 1）才写？每日核对每天一条标注是噪声，但「只在差异出现时写」更贴近「标注专门表示这里放宽了」（ADR 第 3 节「审计」列说明）。测试草案按「只在放宽实际改变结果时写」起草，**请 one-coordinator 与 one-data 确认**。(c) 「已是目标状态、无改动」是否写标注（P2-03 草案：不写）。
10. **编号起草命令是否需要 `--witness` 参数**：ADR 第 2 项第 4 行只说「`author_id` 为空，审计记 `operator` 为用户本人并写标注」；第 1 行把「编号模板起草」列入「运维命令双人在场」被放宽的命令。故该命令的语义是「需要操作人，见证人可省（模式开启时）」。给了见证人时是否记录、是否必须不同于操作人，草案按与其他运维命令一致处理（P3-04）。
11. **dev 上 `PROCESS_ADMINISTRATOR_REQUIRED=false` 的环境变量全称**：按 Spring 宽松绑定应为 `FORCITIS_PLATFORM_APPROVAL_PROCESS_ADMINISTRATOR_REQUIRED`（`@Value("${forcitis.platform.approval.process-administrator-required:true}")`）。待 one-ops 核实 dev 当前 one.env 实际写的是哪个名字，若是短名则不会生效。
12. **`grant-role` 接受任何角色码**（`GrantRoleByOperations.java:54-55`）：包括 `process_administrator`。本卡不限制。命令一次只接受一个 `--role-code`，所以授予用户本人流程管理员与超管是两次命令，各写一条审计与一条标注。
13. **`ProcessAdministratorCheck` 与 `identity-operations` 的顺序**：运维命令在 `ApplicationRunner` 里完成并退出，早于 `ApplicationReadyEvent`，所以授予 `process_administrator` 不会被它卡住（见第 2.3 节第 12 条）。待实现者在 P2-22 的子进程里顺带验证一次（不带 `process-administrator-required=false` 也能完成授予）。

### 需要用户或 one-coordinator 决定的点（不预选）
1. **拆分粒度**：4 个 PR（本卡建议）还是按调度建议 P1 + P2（含 numbering）+ 文档？影响 dev 的 rc 要等多久，和审查面大小。
2. **放宽项枚举第四个取值何时落地**：ADR 守护第 8 条要求枚举恰好四个值。(a) P2 就建四个值，第四个（`TEMPLATE_VERSION_WITHOUT_AUTHOR`）在 P3 之前只有定义没有使用（略违 CS-1.8，但守护可从 P2 起锁死四值）；(b) P2 只建三个值，P3 加第四个并把守护改为四值（P2 的守护写成「恰为已实现的几项」，P3 同 PR 内改成四项，要列为「有理由的断言改动」）；(c) 把 P3 并入 P2，不存在这个问题。
3. **编号运维起草命令的入口分派**：ADR 只要求「`platform.identity` 不依赖 numbering 的应用层」。可选：(a) `platform.identity.api` 定义一个运维命令处理器接口，`platform.numbering` 实现并注册，`OperationsCommands` 按操作名分派给已注册的处理器；(b) 新开一个 `platform.numbering` 内的 `ApplicationRunner`，由新 profile（例如 `numbering-operations`）启动，不经过 identity 的 runner；(c) 由 kernel 提供处理器端口（需改共享内核，须另报）。三者对「检查先于 `ApplicationRunner`」「运维手册命令形态」「是否要新 profile」影响不同。
4. **`revoke-role` 的护栏**：是否禁止通过运维命令收回「最后一个 `super_admin`」或「自己的 `super_admin`」？ADR 对此没有规定；`ReplaceAccountRoles` 在应用内有「放行后超管为 0 则拒绝」一类护栏（ADR-0028），运维命令按 ADR-0028 第 4 条「将为 0 时只能走运维命令」本就是恢复路径。草案测试没有写护栏用例，等决定。
5. **运维手册改动归谁**：INFRA_RUNBOOK §6、C-5 剧本、PREDEPLOY 等在 `phase1a/infra/` 下，平时归 one-ops；ADR 影响面把它们列为本实现的一部分。(a) 随 P4 由 one-1a-par 改，one-ops 复核；(b) 由 one-ops 自己在 rc 出来时改；(c) 先 P1 只改 `application-identity-operations.properties` 里的一行注释，文档放 P4。
6. **P4 放不放进本卡**：它不挡 dev，但是 C-11 生产闸门；可以留在本卡，也可以单独立卡（例如 SINGLE-OP-GATE），由 one-ops 与 one-1a-par 分担（`remote_install.sh`、systemd 单元文件、`deploy_gate.py` 都是 one-ops 的领域）。
7. **intent 文件名**：用户「先实现单人模式」那句话的 intent 片段名待补（第 2.1 节）。

## 数据设计（DATA_DESIGN_GATE.md §1.1 / 附录 A；起草人判断，不代 one-data 签发）

### 涉数据判定（§1.1 五项）
结论：**不涉 DDL**；按 §1.1 五项字面也不涉数据，但有一项边界情形（第 5 项旁的审计 detail 允许内容与登记文件）需要 one-data 知悉并确认，所以建议按「不涉 DDL 但涉数据」的流程走一次审查（卡 → one-data → 签发 → 验收测试 → 实现），**由 one-data 判定，本卡不自行签发**。

| §1.1 项 | 本卡 | 理由 |
|---|---|---|
| 1 表、列、约束、索引 | 无 | ADR-0030「不新增表或列」；`ck_authorization_requests_two_people`、编号两条约束不变；`author_id` 本来可空，运维起草的无作者版本是库层已允许的状态；`db/migration` 无 diff（第 7 节） |
| 2 事件载荷 | 无 | 运维起草发布的是既有 `NumberingRuleReconfigured`，载荷形状不变；`revoke-role` 发布既有的账号角色变更事件；不新增事件 |
| 3 读模型 / 投影 / 报表视图 | 无 | — |
| 4 迁入映射 | 无 | ADR 第 5 项：dev 数据不进生产，单人模式期间的记录不迁移 |
| 5 指标、注册表、`data-classification.txt` | 无改动 | 不新增列，故 `data-classification.txt`、`data-access-registry.yaml` 不动；不新增指标 |

### 需 one-data 知悉的登记文件与允许内容变化（C4）
- **审计 `detail` 的允许写入内容（C4）**：`detail` 是自由文本列，其「允许写入内容」登记在 `docs/modernization/data/repair-cards/DATA-REPAIR-timesheet-audit.md` TB-01 第 2 项的表（行 1 至 19），守护 `AuditWritePointsGuardTest` 按文件逐一核对 `backend/architecture/audit-text-writers.txt`。本卡的新增 / 变化：
  - 行 15（`OperationAttestation.describe()`）：见证人省略时写 `witnessId=none`（无个人信息；`operatorId` 仍是代理键）；
  - 标注审计的 `detail`：恰为放宽项代码（四个固定取值之一，无个人信息）；
  - `platform.identity.single-operator-mode.enabled` 的 `detail`：profile 与数据库名（非个人信息；待核是否写入 detail 还是只进日志）；
  - 编号运维起草的审计：操作人员工 ID（代理键）、单据类型、版本号。
  上述均不含 PI / SPI，预计归入已有行（14、15 或新增 1 至 2 行），**行号由 one-data 给**。
- `backend/architecture/audit-text-writers.txt`：预计变化——`GrantRoleByOperations` 次数 1 → 2；`Register/Reset/Disable/EnableEmergencyAdmin` 各 1 → 2；新增 `RevokeRoleByOperations`、启动审计写入点、标注写入器（或其宿主）、numbering 运维起草用例各一行。**这是 one-data 复核对象。**
- `docs/modernization/audit/MUST_AUDIT_ACTIONS.yaml`：新增 4 个动作（第 11 节），类别由 one-data / 审计负责人选（建议「权限与角色」或「安全控制」下新增类别「单人运维模式」，待定）。
- 不改：`data-classification.txt`、`data-access-registry.yaml`、`ddl-naming-baseline.txt`、`primary-key-exemptions.txt`、各例外清单。
- 数据设计签发行：待 one-data 填（`数据设计：通过（one-data，日期，依据 <本卡提交号>）`或「不涉 DDL，登记文件变化已知悉」）。其余附录 A 小节（表→出处、状态与基数、不变量→约束清单、查询路径、基线 / 目标 / 回滚 T1–T4 等）均「无：不涉及表与列」。
