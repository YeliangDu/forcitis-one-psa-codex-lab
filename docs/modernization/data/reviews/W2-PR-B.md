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

## 补：加 `uk_employees_employee_code` 后同步路径的重复工号口径（2026-10-22，答 RULING-COORD-20261022-05）

事实：老系统 A8 有 5 个重复工号；`sync_runs`/`sync_diffs` 已有「被隔离的人员行」（RULE-365）与数据例外 `ISSUE` 行的机制（`isolated_row_count`、`issue_count`）；同步开关 `forcitis.organization.workforce.sync.enabled` 默认 `false`（`config/organization_workforce.properties`），ADR-0055 下生产不接 A8，同步随 W2 退役。

**① 拒收口径（同步路径，过渡期；不放宽唯一约束，不自动改号）**
- **拒哪条**：会使「不同来源人」占用已被占用的 `employee_code` 的那一行（新建或改号）。已存在的员工保留；同一次同步里两个新人同号，按来源主键升序，先者建立、后者拒收，保证重跑结果一致。同一来源人的行不受影响。
- **怎么拒**：该行按「被隔离的人员行」处理（不建员工、不改号），计入 `isolated_row_count` 与 `issue_count`，写一条 `sync_diffs`：`entity_kind = EMPLOYEE`、`change_kind = ISSUE`、`issue_kind = DUPLICATE_EMPLOYEE_CODE`；`detail` 只写命中员工的自有 ID 和来源对照键，**不写姓名、工号以外的个人信息**；`subject_key` 用来源对照键。不新增表，不捕获 23505 后吞掉：先查再写，查不到命中才插入，仍撞约束（并发）则该次同步 `FAILED` 并写 `failure_reason`。
- **告警给谁**：沿用同步记录页已有的数据例外展示给组织管理员（有 `issue_count > 0` 即显示）；不另造通道。冲突如何处置（合并 / 改号 / 不迁）不在同步里做，归迁入阶段的 `employee_code_conflicts`（W2 §7、`MIGRATION_QUALITY_CHECKS.md` MQ-M-01）。
- **记在哪里**：`sync_diffs`（上述），保留期随同步记录。同步退役时随 `sync_runs` 一并移除，不迁入新表。
- `SourceIdMatchingIT`「同号两人各有一条」改为：第二人被隔离、写 `DUPLICATE_EMPLOYEE_CODE` 例外、第一人不受影响、重跑结果相同；另加「同号并发」用例期望该次同步 `FAILED` 而非静默。

**② 有 A8 数据的环境**：同步默认关闭，ADR-0055 下生产不启用。结论：**PR-B 仍须带上面的隔离路径**（SourceIdMatchingIT 本来就走这条路径，且一次 23505 会让整次同步失败）；同时**任何环境不得在隔离路径合入前把 `WORKFORCE_SYNC_ENABLED` 设为 true**——请 one-1a-par 与 one-ops 核各环境配置没有打开它，结果回 one-coordinator。

## 补：签发 issue_kind `EMPLOYEE_CODE_CHANGE_IGNORED`（2026-10-23，答 RULING-COORD-20261023-08，PR-B2）

**① 类型**：`sync_diffs.issue_kind = EMPLOYEE_CODE_CHANGE_IGNORED`，与 `DUPLICATE_EMPLOYEE_CODE` 并列；`entity_kind = EMPLOYEE`、`change_kind = ISSUE`、`subject_key` 用来源对照键。含义：已发号员工的源端工号与本系统已发工号不同，同步只记例外、**不改工号**（工号一经发出不随源端变动，与上节「不自动改号」同源）。
- `detail` 只写命中员工的自有 ID 与来源对照键，**不写新旧工号值、姓名等个人信息**（要看值去源端与员工档案各自核对）。
- 计入 `issue_count`，不计 `isolated_row_count`（行本身没被隔离，该员工其余字段照常同步）。
- 若 `issue_kind` 有枚举 CHECK 或应用枚举，随本类型一起加值（基线直改，同 `DUPLICATE_EMPLOYEE_CODE` 的做法）；`issue_kind` 是枚举类标记，不是错误码，不进错误码表。

**② 重复同步的口径：每次同步各记一条，不跨次去重。**
- 理由：`sync_diffs` 是 `sync_runs` 的子记录，语义是「这一次同步看到了什么」；跨次去重要查历史或加状态列，等于新造一套「例外是否已处理」的生命周期，同步已定退役（ADR-0055），不值得。组织管理员看的是最近一次同步的 `issue_count`，源端工号仍不一致则每次都应看到。
- 约束：同一次同步内同一员工至多一条（按员工自有 ID 唯一，重跑同一 run 不重复插入）；源端工号改回与已发号一致后下次同步不再产生。
- 噪声界限：条数 ≤ 每次同步中工号不一致的员工数，随 `sync_runs` 保留期清理，同步开关默认 false；若日后同步长期保留再起去重设计，不在本批做。
- 测试要求：①已发号员工源端改号 → 工号不变、写一条该类型例外、其他字段仍同步；②连续两次同步 → 各一条；③源端改回 → 第三次无例外；④`detail` 不含工号值（守护断言）。
