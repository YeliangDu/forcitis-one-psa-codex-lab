# 已建表数据设计审查：修复卡草稿索引

- 起草：one-data 起草助手，2026-10-16；状态：草稿，待 one-data 终审后交 one-coordinator 派工。
- 输入：`docs/modernization/data/review-parts/` 四份分报告与 `OPUS_VERIFY.md`；级别与结论以 `OPUS_VERIFY.md` 为准。被降级为低、被 R-1b P5 覆盖、并入 TS-UNIQ、归 W2 或 ADR-0043 实施 PR 的项不起条目，只在各卡「不含」一节列出归属。
- 通用约定：迁移基线仍在开放期（ADR-0038），直接改基线、不追加迁移，每张卡附预期表结构 diff；所有卡的基线以 R-1b P5 合并后的 main 为准，卡内行号取自审查时的 main，实施前逐条重核；「待核」项由实施者先核实再改。

## 四张卡

| 卡 | 文件 | 覆盖 schema | 建议负责会话 | 条目数 |
|---|---|---|---|---|
| DATA-REPAIR-identity | `DATA-REPAIR-identity.md` | platform_identity | one-1a-next-4 | 11（ID-01 至 ID-11） |
| DATA-REPAIR-workforce | `DATA-REPAIR-workforce.md` | organization_workforce | one-1a-next-4 | 10（WF-01 至 WF-10） |
| DATA-REPAIR-approval | `DATA-REPAIR-approval.md` | platform_approval、platform_integration、platform_numbering | one-1a-next-4 | 13（AP-01 至 AP-13） |
| DATA-REPAIR-timesheet-audit | `DATA-REPAIR-timesheet-audit.md` | cost_timesheet、organization_attendance；platform_audit、platform_idempotency | A 部分 one-1a-par；B 部分 one-1a-next-4 | 8（A 部分 TA-01、TA-02；B 部分 TB-01 至 TB-06） |

## 与 R-1b P5、TS-UNIQ、W2、ADR-0043 实施 PR 的关系

| 来源 | 已覆盖的项 | 对修复卡的影响 |
|---|---|---|
| R-1b P5 | `sync_run_held_separations` 主键与外键索引（HS-1、HS-3）；`audit_entries.subject_kind` / `subject_id`（AE-1）；归档批次「时刻 + ID」（AB-1）；`role_permissions`、`account_roles` 主键；`primary-key-exemptions.txt` | 四卡全部在 P5 合并后开工；workforce 卡不碰 held_separations 主键；audit 部分 TB-05、TB-06 对照 P5 列名；identity 卡 R-2 类项只改 P5 未重写的约束 |
| TS-UNIQ（one-1a-par 另卡） | T-1 `uk_timesheets_employee_id_work_date`（无谓词）；T-2 状态集合与事件改名、`REJECTED` 去终态；X-6 `numbering_rule_version_id`；删冗余 `ix_timesheets_employee_id` | timesheet-audit 卡 A 部分的 TA-01（T-3）须在 TS-UNIQ 合并后按其状态集合改；approval 卡不含 X-6 |
| W2 | ADR-0034 卡（R-1、RP-1、AR-1、AQ-2、AQ-6；dev 部署闸门）；DA-7（`employee_versions`、E-1、E-2、P-1）；DA-17 / K-2（日历版本化 WY-2、`calendar_scope_kind` WY-1、WD-2、WY-3）；法人代码规则（LV-1(b)） | 各卡「不含」逐条列出；OV 建议把这些与审计分区、ADR-0043 实施 PR 合并列入 dev 部署闸门的人工核对项 |
| ADR-0043 实施 PR | 分级文件四段格式、`user_accounts.last_login_address` 清除路径与 R-2 守护、`recipient_codes` 改 `INTERNAL + PI` | identity 卡 ID-06 只登记带到期日 gap；timesheet-audit 卡 TB-01、TB-02 的分级写在卡内表中 |
| R-2a / G-D12 | 全库改名（`ddl-naming-baseline.txt`）、内联枚举 CHECK 具名 | 各卡不含改名；新增约束一律具名 |
| DA-10 | `outbound_deliveries` 收件人列与去重键 | approval 卡不含 |

## 建议顺序

1. **前置**：R-1b P5 合并（四卡共同基线）；TS-UNIQ 合并（仅 timesheet-audit 卡 A 部分依赖）。
2. **第一批（互不依赖，可并行）**：DATA-REPAIR-identity（含高：ID-01、ID-03）、DATA-REPAIR-approval（含高：AP-01、AP-03、AP-09；先删 `hookDecidesReason` 再加 CHECK）、DATA-REPAIR-timesheet-audit B 部分（含高：TB-01、TB-02）。这三卡含 OV 判定的高级别项，优先。
3. **第二批**：DATA-REPAIR-workforce（全部为中；WF-09 须先由 one-coordinator 接受撤销外键例外）；timesheet-audit A 部分（TS-UNIQ 之后）。
4. **并行事项（不属本批卡，须由 one-coordinator 排期）**：AE-2 审计分区需先修订 ADR-0033 并经用户决定，基线开放期内做；ADR-0034 W2 卡、W2 主数据与日历版本化、ADR-0043 实施 PR 作为 dev 部署前人工闸门。

## 须用户或 one-coordinator 先决定的事（汇总，详见各卡「待用户决定」）

- 用户：`session_id` / `primary_id` / `binding_hash` 访问级别；`last_login_address` 保留期；各表保留期；`source_references` 个人信息判定与永久保留；加签驳回是否须原因；`decision_ref` 填法；U-7b 例外与到期日；工时单部门口径；AE-2 分区方案与 ADR-0033 修订。
- one-coordinator：workforce 版本表 `fk_exceptions` 撤销；approval 复合外键 `(task_id, approval_request_id)` 是否放行 G-DDL1「外键目标为主键」；ADR-0033 第 9 项第 2 条与 NEXT_BATCH_PLAN:299 不一致的出处补记。
