## 数据设计审查
- 审查：one-data，2026-10-16，依据 `docs/modernization/phase1a/cards/SINGLE-OP.md`（分支 `claude/one-1a-par-single-operator` @519fc88b，PR #398；实现 ADR-0030 单人运维模式，4 个 PR）末节「数据设计」
- 核对：卡对 §1.1 五项的判定我复核——不涉 DDL（`db/migration` 无 diff）、不新增事件载荷字段、读模型、迁入映射；`ck_authorization_requests_two_people` 与编号两条约束不变；边界在审计 `detail` 允许内容（C4）与登记文件，下面裁定
- 判定：
  - O1–O16、A 系列、T/E 系列：不适用（无表、列、约束、索引、事件载荷、读模型、迁入映射变化）。
  - **C4：通过（含裁定）**：新增写入点的 `detail` 均不含个人信息。
- 裁定（登记文件与措辞，卡与实现以本记录为准）：
  1. **见证人省略写 `witnessId=none`**：确认，与现有 `witnessId=<UUID>` 同一字段名；`AuditDetailPolicy` 禁 `witness=`，ADR-0030 的措辞 `witness=none` 改为 `witnessId=none`：one-architecture 判为落地细化，登勘误 E-004（随 #365），不另起 ADR 片段。`none` 是字面量，不是个人信息。
  2. **TB-01「允许写入内容」表（`DATA-REPAIR-timesheet-audit.md`）改与加**：
     - 行 15（`OperationAttestation.describe()`）：写法改为 `operatorId=<员工 UUID> witnessId=<员工 UUID 或 none>`，个人信息一栏仍为「代理键」，处置「允许」；`RevokeRoleByOperations` 归此行。
     - 新增行 20：`detail`｜启动审计写入点（`platform.identity.single-operator-mode.enabled`，actor 为空）｜`profile=<profile 名>`，**只写 profile 名，不写数据库名**（基础设施信息不进审计 detail）｜否｜允许。
     - 新增行 21：`detail`｜放宽项标注写入器（`platform.identity.single-operator-mode.applied`、`platform.numbering.single-operator-mode.applied`）｜`relaxation=<四个固定放宽项代码之一>`，代码取 `WITNESS_OMITTED`、`EMERGENCY_ACCOUNT_MINIMUM_ONE`、`SUPER_ADMIN_MINIMUM_ONE`、`TEMPLATE_VERSION_WITHOUT_AUTHOR`（取值名最终以 one-glossary 登记为准，改名同改本行）｜否｜允许。
     - 新增行 22：`detail`｜编号运维起草用例｜`operatorId=<员工 UUID> documentType=<枚举> version=<整数>`｜代理键｜允许。
  3. **`audit-text-writers.txt`**：`GrantRoleByOperations`、`RevokeRoleByOperations`、`Register/Reset/Disable/EnableEmergencyAdmin` 各行指向行 15（次数随实现更新）；启动审计写入点 → 行 20；放宽项标注写入器 → 行 21；numbering 运维起草 → 行 22；`AuditWritePointsGuardTest` 的 `LAST_ROW` 改 22。写入点的类别栏：行 20、21 为「否」，行 22 为「代理键」。
  4. **`MUST_AUDIT_ACTIONS.yaml`**：4 个新动作（`platform.identity.single-operator-mode.enabled`、`platform.identity.single-operator-mode.applied`、`platform.numbering.single-operator-mode.applied`、`platform.identity.operations.account.role.revoked`）全部归入既有类别「权限与角色」，不新增类别。
  5. 其余不改：`data-classification.txt`、`data-access-registry.yaml`、`ddl-naming-baseline.txt`、`primary-key-exemptions.txt`、各例外清单。
  6. 若实现中单人模式的任何一步需要新增表、列、约束或事件载荷字段，本签发作废，重新送审。
- 豁免与待用户决定：无（ADR-0030 的单人运维模式是用户已定，且只限 dev：生产启动必须拒绝，属卡上的验收，不属数据设计）。
- 结论：通过（不涉 DDL；登记文件与允许内容变化已知悉并裁定）
- 数据设计：通过（one-data，2026-10-16，依据 `SINGLE-OP.md` @519fc88b，含上述裁定）
