# 工作日历 DDL 审（`organization_attendance` 基线 V20261014141000）

- 日期：2026-10-21；审者：one-data；对象：origin/main 上 `work_calendar_years`、`work_calendar_days`（注册表 `data-access-registry.yaml` 37–50 行：AGGREGATE_ROOT / MUTABLE / PERMANENT 与 AGGREGATE_CHILD / REPLACE_SET / PERMANENT）。
- 依据：SESSION_PLAYBOOK §14/§14.1 D6、D8、D12、D15、D17、D19；ADR-0033 第 9 项第 3 点（日历版本表 `work_calendar_versions` / `work_calendar_version_days`、`calendar_scope_kind`、部分唯一索引）；用户 2026-10-15「配置类要四眼确认」（intent 2026-10-15-012200）、2026-10-16「calendar_scope_kind」（intent 2026-10-16-085332）；NAMING_STANDARD；DATA_DESIGN_GATE。

## 结论

**不满足，按「需修改」处理。** 基线是 ADR-0033 之前的形状（可变聚合 + 整年替换），没有跟上同一 ADR 第 9 项已采纳的版本化，也不满足 D15/D17 的配置型要求。属于 D17 逐表盘点里「应一次性改到位」的表，不能当作豁免（豁免须有 intent 或 ADR，查无）。

## 逐项对照

| # | 要求 | 现状 | 判定 | 级别 |
|---|---|---|---|---|
| 1 | D15/D17 配置型：版本只追加 | 年度行可变（`version` 乐观锁、`updated_at`），日期行整年替换并可删 | 不符 | 高 |
| 2 | 生效日、不追溯 | 无；改动立即对全年生效，包括已过去的日期，与「已维护年份今天以前不可改」只靠应用代码保证（`MaintainLockedWorkCalendarDays`），库内无护栏 | 不符 | 高 |
| 3 | 作者、确认人、确认时间、`decision_ref`；四眼 | 全无；也无法表达「确认人 ≠ 作者」 | 不符 | 高 |
| 4 | `ON DELETE CASCADE` | 删年度行级联删光日期，违反追加语义（D8），也能绕过第 2 项 | 不符 | 高 |
| 5 | 年份一致 | `calendar_date` 的年份与 `year_number` 无约束，日期可挂到别的年份 | 不符 | 中 |
| 6 | ADR-0033 第 9 项：`calendar_scope_kind` + 部分唯一索引，不用哨兵 | 用 `legal_entity_id` 可空 + `UNIQUE NULLS NOT DISTINCT` 表示默认日历 | 不符 | 中 |
| 7 | 来源可追（官方放假通知） | 无来源类别与通知文号 | 不符 | 中 |
| 8 | 内联 CHECK 具名 | `day_kind` CHECK 未命名 | 不符 | 低 |
| 9 | 时间由应用注入 | `created_at`/`updated_at` 带 `DEFAULT now()` | 不符 | 低 |
| 10 | 注册表分类 | 未标 `data_category`（配置）；MUTABLE/REPLACE_SET 与追加矛盾 | 不符 | 中 |
| 11 | 主键 UUIDv7、命名、表列注释、保留期 | 主键 uuid、命名与注释合格；保留期 PERMANENT 对非个人信息的配置合理 | 符合 | — |
| 12 | 周末调休约束 | `ck_…_make_up_weekend` 在库内，合格 | 符合 | — |
| 13 | D19 查询路径 | 按 (年, 日期) 取，已被唯一索引覆盖 | 符合 | — |

## 改法清单（预期表结构）

取代两张表为版本表，沿用 ADR-0033 第 9 项的名字：

**`work_calendar_versions`**（日历 + 年份的一个确认版本；只追加）

- `id uuid` 主键；`calendar_scope_kind text` 具名 CHECK IN ('DEFAULT','LEGAL_ENTITY')；`legal_entity_id uuid`，具名 CHECK：DEFAULT 时为空、LEGAL_ENTITY 时非空；`year_number integer`（沿用 2000–2100 范围 CHECK）；`version_number integer`（≥1）。
- 自然键：两条部分唯一索引（DEFAULT：`(year_number, version_number)`；LEGAL_ENTITY：`(legal_entity_id, year_number, version_number)`），不用哨兵、不用 `NULLS NOT DISTINCT`。
- `UNIQUE (id, year_number)`，供日期表复合外键。
- `effective_from_date date NOT NULL`：本版本起算日，必须不早于确认日（具名 CHECK 或触发器，见下）；此日之前的日期取前一版本，实现「不追溯」。
- 来源：`source_kind text`（具名 CHECK，OFFICIAL_NOTICE / MANUAL / MIGRATION）、`source_reference text NULL`（通知文号；`source_kind=OFFICIAL_NOTICE` 时必填，具名 CHECK）。
- 四眼：`authored_by uuid NOT NULL`、`authored_at`、`confirmed_by uuid NOT NULL`、`confirmed_at`、`decision_ref`（审批任务引用）；具名 CHECK `confirmed_by <> authored_by`。草稿与待确认状态留在审批流程，版本行只在确认通过时插入，所以不需要可变状态列。
- 无 `updated_at`、无 `version` 乐观锁、无 `event_count`（事件序号改记在审批/事件通道，如仍需聚合事件序号须在表结构 PR 中说明理由）。时间列不带 `DEFAULT now()`。
- 具名 UPDATE/DELETE 禁止触发器（owner 例外，沿用其他追加表做法）。

**`work_calendar_version_days`**（版本的例外日成员行；只追加）

- `id uuid` 主键；`work_calendar_version_id uuid`、`year_number integer`，复合外键 `(work_calendar_version_id, year_number)` → `work_calendar_versions (id, year_number)`，**不带 CASCADE**（`ON DELETE RESTRICT`）。
- `calendar_date date`，具名 CHECK `EXTRACT(YEAR FROM calendar_date) = year_number`（解决第 5 项）。
- `day_kind text`，具名 CHECK `ck_work_calendar_version_days_day_kind`；保留调休必为周末的 CHECK。
- `UNIQUE (work_calendar_version_id, calendar_date)`；同样的禁止更新/删除触发器。
- 不追溯护栏（解决第 2 项）：插入触发器（或约束触发器）校验新版本中早于 `effective_from_date` 的日期集合与上一版本一致；第一个版本不受限。这一条必须在库内，不能只靠应用。

**登记与读取**

- 注册表：两表 `data_category: 配置`，访问方式改 APPEND_ONLY，保留期 PERMANENT（不含个人信息，沿用）；写入方 `organization.attendance` 不变。
- 读取：按 (日历范围, 年份, 日期) 取 `effective_from_date <= 日期` 的最大 `version_number`；使用方（审批时限计算等）记所用的 `work_calendar_version_id`（ADR-0033 第 9 项）。
- 迁入：老系统的工作日历按 `source_kind = MIGRATION` 生成第 1 版，不经四眼但须有迁入批次引用（`source_reference`），并列入迁入质量检查（见 MIGRATION_QUALITY_CHECKS）。

## 签发条件与负责

- 表结构 PR 须带预期表结构 diff；我复核实际 diff 后按 DD 门禁签发「数据设计：通过」，未见实际 diff 前不签。
- **谁负责保障**：设计与签发 one-data；实现 one-1a-par（Flyway 追加迁移，注册表、守护测试随之改）；库内护栏由 `measurable_ddl_scan.py` 的 M4（配置型须有版本、作者、确认人、`decision_ref`）和现有基线扫描守护，不靠人工记忆；不追溯触发器与四眼 CHECK 由实现方写集成测试（插入早于生效日的差异应被拒绝）。基线 V20261014141000 属已合并的开放基线（ADR-0038）：开放期内可直接改基线文件，否则追加迁移，由 one-1a-par 与 one-coordinator 按 ADR-0038 选择，本审不限定。
- 业务边界（谁有权确认日历、官方通知如何登记）归 one-architecture/用户，与本审的结构要求分开。
