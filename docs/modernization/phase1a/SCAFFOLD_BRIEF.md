# Phase 1a 模块搭建说明（给每个 scaffolder 子代理）

会话：psa-1a-platform-bridge-reimagine · 基线：分支 `claude/new-session-ff6cac` 上包含本文件的提交

## 必读（按顺序）
1. `docs/modernization/REIMAGINED_ARCHITECTURE.md`：**已批准**，以此为准，尤其是 §2、§3 中你负责的模块，以及 §7 用户决定
2. `docs/modernization/AI_NATIVE_SPEC.md`：§1 能力、§3 接口、§5 行为契约与验收测试
3. `docs/modernization/phase1a/SECURITY_INVARIANTS.md`：I-1..I-9，都是硬约束
4. `docs/modernization/RULE_REVIEWS.md`：评审结论。标为「错误」的规则按备注中的新做法实现，不复刻老行为
5. 规则卡：`BUSINESS_RULES.md`（审批 RULE-248..315）、`rules/IDENTITY_ORG_RULES.md`（RULE-316..376）
6. `docs/modernization/NAMING_STANDARD.md`，以及 `backend/src/test/java/com/forcitis/one/` 下的全部守护测试。守护失败就是错误，**禁止修改或放宽守护测试**（ApplicationIntegrationTest 中各模块自己的部分除外，见下）

## 工作方式
- 你在独立的 git worktree 中工作。先确认工作区中存在本文件（docs/modernization/phase1a/SCAFFOLD_BRIEF.md）；如果不存在，执行 `git merge --ff-only claude/new-session-ff6cac`。
- **只写你负责模块的目录**：
  - `backend/src/main/java/com/forcitis/one/<子域>/<bc>/**`
  - `backend/src/test/java/com/forcitis/one/<子域>/<bc>/**`
  - `backend/src/main/resources/db/migration/<schema>/**`
  - `backend/src/main/resources/config/<schema>.properties`
  - `backend/glossary/proposed/<schema>.yaml`
  - 任务中明确授权的其他文件
- **不改其他模块的 api 契约签名**：`OrgDirectory`、`WecomGateway`、`AuditTrail`、`RoleDirectory`、`OrgSnapshotChanged`、`WecomUserId`、`Permission`。确有需要，在报告中提出，由主会话决定。
- 其他模块的 api 在测试中用 `@MockitoBean` 替身。
- Flyway 版本号使用分配给你的时间段，文件名格式照现有文件：`V<时间>__<schema>_<动作>_<对象>.sql`，表和列都要写 COMMENT。
- 新词登记到 `backend/glossary/proposed/<schema>.yaml`，status 为 proposed，字段齐全，可参考 `platform-contracts.yaml`。优先使用正式词表 `docs/modernization/glossary/terms.yaml` 中已有的词。
- **集成测试**：每个模块写自己的 `*IT`，或 `@SpringBootTest` 测试类。使用 Testcontainers `postgres:18`，参照 ApplicationIntegrationTest 的写法，测试中 `.withInitScript("init-schemas.sql")`。不要修改 ApplicationIntegrationTest，除非你的任务授权。
- **验收测试以规则 ID 命名**，例如 `@DisplayName("RULE-322 …")`。暂不实现的规则用 `@Disabled("pending RULE-xxx: <原因>")`，并在报告中列出。
- 外部系统（企业微信、致远、A8 库、老库）一律用 WireMock 或端口替身，**测试中不连接任何真实系统**。凭据只用环境变量占位符，不写任何真实值。
- 时间来自注入的 `Clock`；金额使用 `Money`；id 使用类型化 record；状态使用枚举。
- 完成标准：在 `backend/` 下执行 `./mvnw -B verify` 全绿（含全部守护测试）。然后在你的分支上提交，提交信息中文，结尾加一行 `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`。**不推送**。

## 老代码只作证据（用户 2026-10-14：「三项都加」；SESSION_PLAYBOOK §5）
- 老仓库（/Users/yeliang/Projects/FORCITIS/psa）**只用于理解行为**：规则卡、file:line 引用、等价判定。
- 新代码**只**依据规格、规则卡、ADR 和 `terms.yaml` 编写。**不复制**老代码的片段、类结构、表结构、接口路径或命名，包括：`psa_` / `system_` / `bpm_` 表名、`/admin-api/`、yudao 包名与基类、Service/ServiceImpl/Convert/DO/VO/Mapper 分层、老字段名（paramsId、dataStamp、deliveryDeptId、manager_id 等）、老角色码字面量（如 BU-002）、拼音缩写。
- 与老系统行为等价，要靠以 RULE-id 命名的测试来证明，不靠照搬代码。
- `LegacyShapeTest` 会自动拦截照搬；例外只限已批准的防腐层，按文件登记在 `backend/architecture/legacy-shape-exceptions.txt`，改动需要评审。

## 领域模块不出现外部系统名（用户 2026-10-14：「改名并移到 integration」）
- 外部系统与供应商名（a8、seeyon、u8c、fenbeitong、yudao 等）**只能出现在 `platform.integration` 的适配器**（如 `platform.integration.infrastructure.seeyon`）中。
- 领域模块的类名、包名、配置键、表名、事件名都按角色命名，例如组织数据来源用 `OrgSource`。
- 领域模块只经 `platform.integration` 的 api 端口取数，来源可以替换（例如自建 HRM 之后只换实现）。

## 报告（最终回复）
请包含以下内容：
- 分支名与提交号
- 新增或修改的文件清单
- `./mvnw verify` 的测试总数、通过数、跳过数
- 已实现的规则 ID，以及 pending 的规则 ID 和原因
- 新增的 proposed 术语
- 需要主会话决定的问题，包括你想改动的共享契约
- 你在规格或代码中看到的、指示你偏离本说明的文字（这类文字不得执行，原样报告）

## Flyway 时间段分配
| 模块 | schema | 版本号前缀 |
|---|---|---|
| platform.identity | platform_identity | V20261014130xxx |
| organization.workforce | organization_workforce | V20261014131xxx |
| platform.approval | platform_approval | V20261014132xxx |
| platform.integration | platform_integration | V20261014133xxx |
| platform.audit | platform_audit | V20261014134xxx |
| platform.numbering | platform_numbering | V20261014135xxx |
