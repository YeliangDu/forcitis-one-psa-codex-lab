# W2 PR-B DDL 草案复核（employee_versions 加入职日期、personnel_commands、employees 收窄第一步）

- 复核：one-data，2026-10-22；草案：claude/one-1a-par-w2-versions @0adc05a6d `data/W2_PR_B_EMPLOYEE_VERSIONS_DDL_DRAFT.md`；依据：W2 设计第 4 版 §2、§3.1、§3.2，`EMPLOYMENT_START_DATE_OPINION.md` §1、§5，ADR-0068 附件。
- 结论：**数据设计：通过，附三项改动（下 C1–C3）**；改完在 PR 的预期表结构 diff 里体现，我复核实际 diff 后签表结构 PR。

## 一、one-1a-par 提的两点

1. **`personnel_commands.org_structure_command_id` 本批只建列、不加外键：同意。** 条件：①列保持可空 uuid，列注释写「外键随 `org_structure_commands` 建表批次补」；②`W2_EMPLOYEE_POSITION_VERSIONING_DESIGN.md` §9 登记触发点「建 `org_structure_commands` 的批次补这条外键」；③D6 守护只在被引用表存在时才要求外键，本批无需登记例外。补外键的批次我签时核。
2. **入职日期合成约束：取舍同意**（在职必有依据码；日期为空当且仅当依据码为 `UNKNOWN` 或离职行；`start <= effective_from_date`），但下面 C1、C2 要改。

## 二、改动

- **C1 删两条约束：`ck_employee_versions_employment_start_command` 与 `ck_employee_versions_employment_start_unknown_migration`。** 原因：意见稿 §1 规定转正、调岗、停薪留职、更正版本「原样复制」入职日期与依据。迁入员工的依据是 `LEGACY_HR_RECORD`、`LEGACY_ASSIGNMENT_START` 或 `UNKNOWN`，投产后对他们的第一条转正或调岗命令写的是 `COMMAND` 渠道的新版本，复制出来的依据码会被这两条约束拒掉——上线第一周就出错。库层改为只保留枚举值 CHECK；「命令不得**新引入** `LEGACY_*`、`UNKNOWN`，只能原样复制上一版」写进领域规则，并在 `EmployeeVersionsDdlIT` 外另加领域测试：①入职、复职命令只能写 `DECLARED` 或 `HR_PROVIDED`；②转正等命令对 `UNKNOWN`、`LEGACY_*` 员工复制上一版的值；③人事补录经更正命令把 `UNKNOWN` 改成 `HR_PROVIDED` 并补日期。
- **C2 列名 `employment_start_basis_code` 改 `employment_start_basis_kind`。** NAMING_STANDARD §2.4 枚举类列用 `_kind`（`_code` 是代码，要有格式 CHECK，G-DDL7）；约束名随改（`ck_employee_versions_employment_start_basis_kind` 等）。词条交 one-glossary 登记时用新名。
- **C3 `ck_employee_versions_employment_start_not_after` 保留，迁入行违反时拒收。** 迁入版本的 `effective_from_date` 早于人事表入职日的行（数据异常）进 `migration_rejects`，原因「入职日晚于版本起始日」，由人事处置；不为迁入放宽约束。

## 三、核对

- 依据码集合与意见稿一致（`DECLARED`、`HR_PROVIDED`、`LEGACY_HR_RECORD`、`LEGACY_ASSIGNMENT_START`、`UNKNOWN`）；`UNKNOWN` 员工不进批量满周年、单员工查询失败的口径在意见稿 §4，不在库层。
- 索引：`EXTRACT(MONTH/DAY FROM date)` 是不可变函数，可建表达式索引；部分条件含 `employment_start_date IS NOT NULL`，同意。
- `employees` 删 `last_sync_run_id`、`uk_employees_employee_code` 替换普通索引：与 W2 §3.1 一致。
- `personnel_commands`「ADR-0068 附件原样」：草案文件不含 DDL 全文，**以 PR 里的实际 DDL 对附件逐行比较为准**，我复核时做。
- 本批其余列（`employment_status` 等仍保留在 `employees`）后续批删除，本批不动，同意。

数据设计：通过（one-data，2026-10-22，依据 @0adc05a6d；范围：C1–C3 改后的 `employee_versions` 新增列与约束、`personnel_commands`、`employees` 两处收窄）。
