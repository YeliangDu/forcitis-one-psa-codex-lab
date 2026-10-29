# 防腐层边界：外部系统概念搬出新系统模块（ADR-0039 草稿，第 2 版，待用户定）

状态：已成文→ADR-0039｜2026-10-16｜负责：one-architecture

- 起因：用户 2026-10-15「先等等，我没有同意所有用语」「所有用语检查下，acl用语单列，不能进入新系统模块」（经 one-coordinator 转达）；one-glossary 全量归类 `docs/glossary/acl-classification/acl-classification.json`（分支 `claude/one-glossary-term-gate`）。
- 证据：代码以 origin/main 为准；计数口径写在各行。
- 起草：one-architecture（Opus 5.5）。第 2 版按 one-coordinator opus 复审（2026-10-15）修改：删去原 ②b、②c；改写 A、B3、C、E、②a；排期按生产前后切分。每块第一个选项为推荐，结构性选择以用户答复为准。

## 0. 判定口径（推荐，交用户认可，U-ACL-0）

1. **ACL 概念**：只因某个外部系统（A8 / 致远、企业微信、U8C、老系统）存在才有的概念、标识、协议与取值，例如企业微信 userid、致远登录名、来源数据的缩水保护、老待办、原单号。只在 `platform.integration` 内出现。与 ADR-0033 第 2 项第 4、9 条、ADR-0036 第 2 条一致。
2. **不是 ACL 的**：新系统自己的概念，即使数据来自外部，例如员工、组织单元、待办、单据编号、外部身份、迁入、迁入占用、历史期间。它们留在业务模块，但不带外部系统名或外部取值；用语问题交 one-glossary。
3. 业务模块从集成层拿到的只能是已翻译成新系统用语的结果（CS-2.29）；翻译在集成层完成，业务模块不设翻译包（回答 CODING_STANDARD §10 Q-4）。
4. 表的位置在 P3b 开工前定下，P3b 直接写到新 schema，避免同一张表改两次。搬已有表可用 `ALTER TABLE … SET SCHEMA` 连数据一起搬，代价不高；代价主要在事件、权限码与对外契约改名。

## 1. 逐块方案

### A 组织同步（`organization.workforce`）

现状：SyncRun、ShrinkGuard、SnapshotReconciler、SyncedFields、SourceTerms 等约 72 个 main 文件（3272 行）；表 `sync_runs`、`sync_diffs`、`sync_run_held_separations`，`org_units` / `employees.last_sync_run_id`；5 个 `SyncRun*` 事件；3 个权限码；页面 `/admin/sync-runs`。

「待放行」（HELD）有两类原因，性质不同，拆开处理：

| 暂停原因 | 性质 | 归属（推荐） |
|---|---|---|
| `SHRINK_GUARD` 缩水保护：来源数据一次少了太多 | 来源数据质量问题，属 ACL | 集成层：同步运行在应用前就暂停，组织数据管理员核对后放行或丢弃 |
| `ADMINISTRATOR_SEPARATION` 管理员离职（ADR-0027 第 1 项第 7 条，ADR-0028；经 `kernel.SeparationReview` 由 identity 实现） | 安全业务规则：防止全员锁死 | 业务侧 workforce：应用变更集时逐条判断，离职暂停作为 workforce 自己的「待放行离职」记录，按 ADR-0028 由另一名持有人放行 |

**A1（推荐）同步机制搬进集成层，离职暂停留在 workforce。**
- 集成层「组织来源同步」：运行记录、缩水保护、差异、调度、重试、运维页面都在集成层；把来源数据翻译成新系统用语的「组织变更集」，调用 workforce 唯一的 api 方法「应用组织变更」。
- workforce：应用变更集、维护组织快照、发 `OrgSnapshotChanged`；遇到管理员离职，其余变更照常应用，只把这条离职挂为「待放行离职」，返回给集成层登记。
- **两个事务的一致性**（INT-DDD-H2，DATA_DESIGN_CHECKLIST §14 已登记例外第 3 条所防的窗口）：
  1. 集成层先把运行记为「应用中」并提交，再以运行 ID 作幂等键调用 workforce；
  2. workforce 在一个事务内应用整个变更集，并记下「已应用的变更集 ID」；同一 ID 重复调用直接返回上次结果，不重复应用；
  3. 集成层收到结果后把运行记为「已应用」。这一步失败时快照是对的，只是运行记录落后；
  4. 补偿：集成层的对账作业扫描超过 N 分钟仍为「应用中」的运行，向 workforce 查该变更集是否已应用，已应用就补记，未应用就按失败处理并可重试；
  5. 告警：「应用中」超时、补记发生、重试耗尽都发运维告警（指标与告警名按 G46 登记）。
  - 结果：快照不会半新半旧；最坏情况是运行记录短时落后，有补偿和告警。撤销 `multi-aggregate-exceptions.txt` 中 `ApplySourceSnapshot` 一条，§14 已登记例外第 3 条随之删除。
- 代价：main 约 72 个、测试约 26 个、前端约 10 个文件；3 张表与 2 列换 schema；5 个事件改到 `platform.integration.*`；3 个权限码改前缀；`sync_run_held_separations` 改为 workforce 的「待放行离职」表。

**A2 整体搬，离职暂停也随运行放在集成层**：集成层要知道「谁是管理员」，安全业务规则进了防腐层，违背口径第 2 条。不推荐。

**A3 不搬**：保留现状，只把 `Source*` 翻译移出（P3b 已排）。不满足「不能进入新系统模块」。

### B kernel 中的外部色彩

| 项 | 现状（main） | 推荐 |
|---|---|---|
| **B1 `WecomUserId`** | main 共 33 个文件引用；集成层以外 16 个（kernel 1、workforce 12、approval 2、identity 1，2026-10-15 按 origin/main `git grep`，one-coordinator 复核） | 随 ADR-0033 第 4 项落地：移入集成层，`employees` 删 `wecom_user_id`、`login_name`，收件人解析在集成层，审批通知只传 `UserId` |
| **B2 `ExternalSystemUnavailableException`** | kernel；workforce 4 处、web 映射 1 处 | 改中性名，留 kernel（错误链属 kernel，CS-2.24）；用语交 one-glossary |
| **B3 事件信封 `source`**（已定：ADR-0039 第 5 条；落地：R-1b P5 删除信封 `source`，本行「现状」为 2026-10-15 的 main，删除后不再成立） | `kernel.event.EventSource(system, documentRef)`；`SourceSystem` 取值 `ONE` / `MIGRATION`；`SourceSystem.ONE` 在 main 14 个文件构造；信封 schema 在 `docs/architecture/events/asyncapi.yaml:1283-1292`（`source.system` 枚举、`documentRef` 为原单号）；`MIGRATION` 无人构造 | **从信封删除 `source`（含 `documentRef`）**：ADR-0036 第 5 条规定迁入用例不发布领域事件，信封里的 `MIGRATION` 与原单号没有使用场景，原单号属 ACL。这同时解决与「来源系统」的撞名：NEXT_BATCH_PLAN C7（:319）已把新词定为 `ExternalSource`，删掉旧的 `SourceSystem` 后只剩一个。并入 P5（P5 独占 `EventEnvelope`，:308） |

### C 登录（企业微信、致远单点、签名票据）

现状：外部协议已在集成层（`WecomGateway`、`SsoTicketVerifier`）；identity 留登录用例约 15 个文件，表 `login_states`（`entry` 取值 WORKBENCH 等）、`sso_ticket_uses`、`ticket_issuers`，4 个公开端点。

**C1（推荐）联合身份**：identity 只有一个中性用例「以外部凭据登录」。集成层校验外部凭据（企业微信 OAuth 态、致远票据防重放、签名票据的 HMAC 与密钥），返回「已验证的外部主体」，并按外部身份对照（见 ②a）找到 `UserId`；identity 只决定能否登录、发会话。
- `login_states`、`sso_ticket_uses`（短时数据）搬到 `platform_integration`。
- `ticket_issuers` **不整表搬**：`allowed_role_codes` 与「不得含 `super_admin`」的 CHECK（`ck_ticket_issuers_no_administrator`）是 identity 的准入策略，留在 identity；只把签发方的密钥引用与 `kid` 搬到集成层。两边以签发方代码关联。
- 端点路径可保留（前端不改），由 identity web 转给集成层。
- C1 的实现 PR 须另做安全复审（SECURITY_INVARIANTS、`SecurityInvariantsTest` 与 `ticket-issuers-allowlist.txt` 路径随之变化）。

**C2**：identity 保留用例，只改名去外部名。取值仍是外部的，不满足口径。

### D 老待办（`platform.approval`）

按 ADR-0033 第 2 项第 9 条把 `JdbcLegacyInbox`、`LegacyInstanceId` 移入集成层（P3b 已排）。对外契约（`InboxSource.NEW/LEGACY`、`legacyCount`、`legacyAvailable`）改为中性用语，在首次外部对接前完成；用语交 one-glossary。

### E 原编号（`platform.numbering`）

现状：13 个 main 文件、表 `legacy_numbers`（`source_system` 取外部值、`migration_batch_id`）、2 个端点、权限码 `platform.numbering:legacy-number:read`；**无调用方**。

**E1（推荐）`legacy_numbers` 原样整表搬到 `platform_integration`**，表结构与 G-MG1 / G-MG6 不变，查询端点与权限码改到集成层前缀；numbering 只管新编号。不并入 `source_references`，因为结构冲突：`source_id` 是 `bigint`（ADR-0033:89），原单号是文本；一个自有对象在一个来源只对应一个来源对象（:90），而一张单据可有多个原编号。
**E2**：留在 numbering，中性改名「历史单号」。取值仍是外部的。

## 2. 与已采纳 ADR 的冲突（ADR-0039 部分取代或修订）

| # | 已采纳条款 | 冲突 | ADR-0039 处理（推荐） |
|---|---|---|---|
| ②a | ADR-0033 第 4 项：`platform_identity.external_identities`（`provider_kind` 取外部系统名，`external_subject_key`）放在 identity | 外部身份的取值是 ACL 长期概念 | **`external_identities` 原样建在 `platform_integration`**：表结构、按生效区间的排他约束（ADR-0033:139）不变，不并入 `source_references`（`source_id` 为 `bigint`、一对一约束，均不适用于文本型外部主体）。登录时由集成层按（提供方, 外部主体）找 `UserId`。第 4 项其余内容不变：删 `login_name` / `wecom_user_id`，`WecomUserId` 移出 kernel，收件人解析在集成层。该表尚未建，改位置不产生返工 |
| A-1 | ADR-0026 第 1 项「接入」表（`sync_runs`、`sync_diffs` 登记为 `GLOBAL`，:57） | 表搬到集成层 | 登记改到集成层表名，范围仍为 `GLOBAL` |
| A-2 | ADR-0027 第 1 项第 7 条（已被 ADR-0028 取代）：「该运行保持待放行」 | 离职暂停不再挂在运行上 | 改为「该条离职保持待放行」，其余变更照常应用；锁死判定（可用 `super_admin` 少于 2 名）不变 |
| A-3 | ADR-0027 第 2 项：同步放行归组织数据管理员，权限 `organization.workforce:sync-run:execute` | 缩水放行搬到集成层 | 缩水放行权限改为集成层前缀，仍授组织数据管理员；离职放行仍按 ADR-0028 |
| A-4 | ADR-0028：管理员离职暂停的放行（按原因授权、放行按最新源数据重跑） | 放行对象从「运行」变为「待放行离职」记录 | 放行只放这一条离职，不重跑整次同步、不再绕过缩水保护（同时修掉 ADR-0028 背景所述的「放行一并绕过缩水保护」） |
| E-1 | ADR-0036 中 `platform_numbering.legacy_numbers` 的表路径（迁入登记的读写授权 :55、批次列 :70、原编号对照为准 :74） | 表搬到集成层 | 表路径改为 `platform_integration.legacy_numbers`，其余规则不变 |
| E-2 | ADR-0033 迁移方案第 4 条（:242）「原编号进 `legacy_numbers`」与第 2 项第 7 条（老 ID 进来源对照） | 同上 | 只改表路径；老 ID 仍进来源对照，原编号仍进 `legacy_numbers` |
| B3 | ADR-0036 第 5 条（迁入不发布领域事件）；信封 `source` 的来历（proposals/EVENT_ANALYTICS_FIELDS） | 不冲突；据此删除信封 `source` | 记入 ADR-0039，同步改 `asyncapi.yaml` 信封 schema 与 EVENT_ANALYTICS_FIELDS |

已删除的原条目：原 ②b（迁入、迁入占用、历史期间是新系统自有用语，按口径第 2 条不属 ACL，用语问题交 one-glossary，不修订 ADR-0036）；原 ②c（来源对照的「长期 / 过渡」属性没有使用方，CS-1.8）。

## 3. 排期（并入 P3b / P5 的顺序由 one-coordinator 定）

**生产前必须完成**
- 本 ADR 的表位置在 P3b 开工前定（P3b 要改 `sync_run_held_separations`、`sync_diffs.subject_key`，:303）。
- 外部身份建在哪里（②a，DA-3）；`employees` 删 `login_name`、`wecom_user_id`（B1）。
- A：5 个事件改命名空间、3 个权限码改前缀，同步机制与离职暂停按 A1 拆分。
- B3：删除信封 `source`（P5）。

**可放生产后**
- B2 改名。
- D 契约中性化（首次外部对接前）。
- E 搬表（首个迁入阶段前；表现在为空）。
- C 中 `login_states`、`sso_ticket_uses` 两张短时表的搬迁（C 的联合身份用例本身随 ②a 在生产前做）。

**工作量重估**：生产前部分约 90–110 个 main 文件、50 个测试文件、15 个前端文件，含 5 个事件和 3 个权限码改名、A1 的补偿与告警、C1 的安全复审。按 R-1b 子 PR 的节奏，约 3–4 个子 PR（A1 两个：集成层同步与 workforce 变更集各一个；②a 与 C1 一个；B1 / B3 并入 P3b / P5），不是 2 个修复批。

## 4. 交用户决定（并入架构决策页）

- **U-ACL-0** 判定口径（第 0 节）：认可（推荐）/ 修改。
- **U-ACL-A** 组织同步：A1 同步机制搬进集成层、管理员离职暂停留在 workforce（推荐）/ A2 整体搬 / A3 不搬。
- **U-ACL-C** 登录：C1 联合身份（推荐）/ C2。
- **U-ACL-落点**：外部身份（②a）与原编号（E）的表原样建在 `platform_integration`（推荐）/ 留在 identity 与 numbering。
- 排期只在会推迟生产时才交用户；B2、B3、D 的用语交 one-glossary。
