# W2 PR-B DDL 草案：employee_versions（含入职日期）与 employees 收窄第一步（供 one-data 签发）

- 日期：2026-10-22；作者：one-1a-par（A 线）；派工：one-coordinator；状态：草案，待 one-data 签发，签后开 PR。
- 依据：`W2_EMPLOYEE_POSITION_VERSIONING_DESIGN.md` 第 4 版 §2、§3.1、§3.2、§4；`EMPLOYMENT_START_DATE_OPINION.md` §1、§5；ADR-0068 附件「DDL 草案」（`personnel_commands`）。
- 做法：迁移基线开放，直接改 `V20261014131000__organization_workforce_baseline.sql`（SESSION_PLAYBOOK §12.2）；PR 附预期表结构 diff，opus 核对。

## 0. 本批范围与边界

1. 新建 `employee_versions`（W2 §3.2 全文，加下面第 2 节的入职日期两列与约束）。
2. 新建 `personnel_commands`（ADR-0068 附件 DDL 草案原样）：`employee_versions.change_command_id`、`superseding_command_id` 是真外键，W2 §11 W-13 要求命令表与版本表同迁移或先于它。
3. `employees` 本批只做：删列 `last_sync_run_id`（同步退役，W2 §3.1）；唯一约束 `uk_employees_employee_code` 替换 `employees_employee_code_idx`（用户已定，W2 §3.1）。其余列（`employment_status`、`is_fee_earner`、`separated_at`、`login_name`）仍有读写方，删除随读取改写与命令实现的后续批做，本批不动。
4. 不在本批：`position_versions`、`assignment_versions` 通用列、两张归属期间表、`assignments` 去除、`org_structure_commands`、`separation_holds`、`purge_employee_history`、`employee_code_conflicts`。

## 1. 需 one-data 先答的一点

`personnel_commands.org_structure_command_id` 的外键指向 `org_structure_commands`，后者本批没有。建议：本批只建列、**不加这条外键**，建 `org_structure_commands` 的批次同时补外键（基线开放期可直接改基线）。同意则本批如此；不同意则要先有 `org_structure_commands` 的 DDL 签发。

## 2. employee_versions 相对 W2 §3.2 的新增

在 §3.2 全文基础上加两列：

```sql
    employment_start_date        date,
    employment_start_basis_code  text,
```

约束（`EMPLOYMENT_START_DATE_OPINION.md` §1、§5，并按迁入行例外落成可执行形式）：

```sql
    CONSTRAINT ck_employee_versions_employment_start_basis CHECK (
        employment_start_basis_code IS NULL
        OR employment_start_basis_code IN ('DECLARED', 'HR_PROVIDED', 'LEGACY_HR_RECORD', 'LEGACY_ASSIGNMENT_START', 'UNKNOWN')),
    CONSTRAINT ck_employee_versions_employment_start_employed CHECK (
        (employment_status = 'EMPLOYED') = (employment_start_basis_code IS NOT NULL)),
    CONSTRAINT ck_employee_versions_employment_start_known CHECK (
        (employment_start_date IS NULL) = (employment_start_basis_code IS NULL OR employment_start_basis_code = 'UNKNOWN')),
    CONSTRAINT ck_employee_versions_employment_start_unknown_migration CHECK (
        employment_start_basis_code IS DISTINCT FROM 'UNKNOWN' OR change_channel_kind = 'MIGRATION'),
    CONSTRAINT ck_employee_versions_employment_start_command CHECK (
        change_channel_kind <> 'COMMAND' OR employment_start_basis_code IS NULL OR employment_start_basis_code IN ('DECLARED', 'HR_PROVIDED')),
    CONSTRAINT ck_employee_versions_employment_start_not_after CHECK (
        employment_start_date IS NULL OR employment_start_date <= effective_from_date)
```

- 在职版本必有依据码，离职版本无；日期为空当且仅当依据码为 `UNKNOWN`（或离职行）。`UNKNOWN` 只允许迁入行。命令行只能是 `DECLARED`（入职、复职命令）或 `HR_PROVIDED`（人事更正命令补录）。
- 意见稿 §1 写「在职版本必填」，同时 §5 允许迁入行为空；上面把二者合成一套约束，请 one-data 确认取舍。
- 注：`LEGACY_ASSIGNMENT_START` 与 `UNKNOWN` 不进满周年批量，由查询层过滤，库层不管。

索引（意见稿 §1）：

```sql
CREATE INDEX ix_employee_versions_employment_start_month_day
    ON organization_workforce.employee_versions ((EXTRACT(MONTH FROM employment_start_date)), (EXTRACT(DAY FROM employment_start_date)))
    WHERE employment_status = 'EMPLOYED' AND superseded_at IS NULL AND employment_start_date IS NOT NULL;
```

比意见稿多一个 `employment_start_date IS NOT NULL`，使迁入未知行不进索引。

## 3. 同批改动清单（实现 PR 一并做）

- `data-access-registry.yaml`：`employee_versions`、`personnel_commands` 登记（`employee_versions` 为 DIMENSION_VERSIONED、APPEND_ONLY、DECLARED、INTERNAL + PI、`PERMANENT` 并写理由与出处，R-2）；`employees` 删 `last_sync_run_id` 及 `fk_exceptions` :79 对应项。
- `aggregates.yaml`：Employee 聚合补 `employee_versions`、`personnel_commands`。
- `one_db_roles_schemas.sql` §4b：新表回收 DELETE、TRUNCATE。
- 触发器：`tg_employee_versions_append_only`（及 TRUNCATE 拒绝）、`tg_personnel_commands_append_only`（及 TRUNCATE 拒绝），放行列按 W2 §2 与 ADR-0068 第 1 条 7 参数化，模板用 identity 的 `refuse_history_rewrite(...)`。
- `ddl-naming-baseline.txt`、`data-classification.txt`、`versioned-table-unconditional-writes.txt` 随新表与删列调整（W2 §5 W-08 清单中属本批的部分）。
- `JdbcOrgSnapshotRepository` 去掉 `last_sync_run_id` 的读写；业务代码不新增对新表的使用（命令与读取改写属后续批）。
- 词条：`employment_start_date`、`employment_start_basis_code`、`personnel_commands` 相关列的登记交 one-glossary（`hire_date` 未登记，见意见稿 §7）。
- 测试：新表的追加只读触发器行为测试、各 CHECK 的正反例、防重叠 `EXCLUDE` 测试、`EmployeeVersionsDdlIT`（Testcontainers）。
