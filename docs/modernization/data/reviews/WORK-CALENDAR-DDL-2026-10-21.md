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

取代两张表为「根表 + 版本表 + 版本日期表」三张（2026-10-21 答 IMP-1021-PAR-02 时增根表，见文末更正），沿用 ADR-0033 第 9 项的版本表名字：

**`work_calendars`**（日历身份；注册表 `CONFIG_VERSIONED`，`versions_table: …work_calendar_versions`，仿 `platform_numbering.numbering_rules`）

- `id uuid` 主键；`calendar_scope_kind text` 具名 CHECK IN ('DEFAULT','LEGAL_ENTITY')；`legal_entity_id uuid`，具名 CHECK 与 scope 一致；两条部分唯一索引（DEFAULT 至多一行；LEGAL_ENTITY 按 `legal_entity_id`），不用哨兵。无可变业务列，mutability 记 APPEND_ONLY，同样挂禁改触发器。

**`work_calendar_versions`**（日历 + 年份的一个确认版本；只追加）

- `id uuid` 主键；`work_calendar_id uuid` 外键 → `work_calendars`；`year_number integer`（沿用 2000–2100 范围 CHECK）；`version_number integer`（≥1）。
- 自然键：`UNIQUE (work_calendar_id, year_number, version_number)`，不用哨兵、不用 `NULLS NOT DISTINCT`。
- `UNIQUE (id, year_number)`，供日期表复合外键。
- `effective_from_date date NOT NULL`：本版本起算日；`version_number > 1` 时必须不早于确认日（插入触发器，见下）；此日之前的日期取前一版本，实现「不追溯」。**首版（`version_number = 1`）豁免**：该年份尚无前一版本，无可追溯之物（含补维护过去的年份）；迁入版本按下面约束恒为首版，同受豁免。
- 来源：`source_kind text`（具名 CHECK，OFFICIAL_NOTICE / MANUAL / MIGRATION）、`source_reference text NULL`（通知文号；`source_kind=OFFICIAL_NOTICE` 时必填，其余为空，具名 CHECK）。迁入批次引用**不记在本表**（M5：业务表不加批次列），迁入版本的老系统来源与批次走 `platform_integration.source_references`（ADR-0033）。
- 四眼：`authored_by uuid NOT NULL`、`authored_at`、`confirmed_by uuid NOT NULL`、`confirmed_at`、`decision_ref text NOT NULL`（审批任务引用；迁入版本为核对签字引用）；具名 CHECK `confirmed_by <> authored_by`，**对所有来源一律成立，无豁免**。迁入版本（`source_kind = MIGRATION`）的作者是迁入装载的系统主体，确认人是 ADR-0036 第 8 条核对签字的人（取自会话），所以两人本来就不同；具名 CHECK `source_kind <> 'MIGRATION' OR version_number = 1`。草稿与待确认状态留在审批流程，版本行只在确认通过时插入，所以不需要可变状态列。
- 无 `updated_at`、无 `version` 乐观锁、无 `event_count`（事件序号改记在审批/事件通道，如仍需聚合事件序号须在表结构 PR 中说明理由）。时间列不带 `DEFAULT now()`。
- 禁改触发器：BEFORE UPDATE OR DELETE 与 BEFORE TRUNCATE，拒绝一切改删，**无 owner 例外**；形状沿用 main 上 `organization_workforce.refuse_version_rewrite()`（版本表禁删禁清空），本表无「关闭开放版本」，所以更严：连 UPDATE 也拒。保留期 PERMANENT，无清理，不需要例外通道。

**`work_calendar_version_days`**（版本的例外日成员行；只追加）

- `id uuid` 主键；`work_calendar_version_id uuid`、`year_number integer`，复合外键 `(work_calendar_version_id, year_number)` → `work_calendar_versions (id, year_number)`，**不带 CASCADE**（`ON DELETE RESTRICT`）。
- `calendar_date date`，具名 CHECK `EXTRACT(YEAR FROM calendar_date) = year_number`（解决第 5 项）。
- `day_kind text`，具名 CHECK `ck_work_calendar_version_days_day_kind`；保留调休必为周末的 CHECK。
- `UNIQUE (work_calendar_version_id, calendar_date)`；同样的禁止更新/删除触发器。
- 不追溯护栏（解决第 2 项）：插入触发器（或约束触发器）校验新版本中早于 `effective_from_date` 的日期集合与上一版本一致；第一个版本不受限。这一条必须在库内，不能只靠应用。

**登记与读取**

- 注册表（main 上没有 `data_category` 字段，D1/D10 靠 `role` 判断）：`work_calendars` 记 `role: CONFIG_VERSIONED`、`versions_table: organization_attendance.work_calendar_versions`；版本表记 `CONFIG_VERSIONED` 的版本表角色，版本日期表记 `AGGREGATE_CHILD`；三表 mutability APPEND_ONLY，保留期 PERMANENT（不含个人信息），写入方 `organization.attendance` 不变。照 `platform_numbering.numbering_rules` / `numbering_rule_versions` 的登记方式。
- 读取：按 (日历范围, 年份, 日期) 取 `effective_from_date <= 日期` 的最大 `version_number`；使用方（审批时限计算等）记所用的 `work_calendar_version_id`（ADR-0033 第 9 项）。
- 迁入：老系统的工作日历按 `source_kind = MIGRATION` 生成第 1 版，作者为迁入装载主体、确认人为核对签字人（四眼 CHECK 照常成立），`decision_ref` 为核对签字引用，并列入迁入质量检查（见 MIGRATION_QUALITY_CHECKS）。

## 签发条件与负责

- 表结构 PR 须带预期表结构 diff；我复核实际 diff 后按 DD 门禁签发「数据设计：通过」，未见实际 diff 前不签。
- **谁负责保障**：设计与签发 one-data；实现 one-1a-par（Flyway 追加迁移，注册表、守护测试随之改）；库内护栏的守护**更正**（原稿说 M4 查作者、确认人、`decision_ref`，不实）：`measurable_ddl_scan.py` 不在 main，M4 只查「主数据须有版本表」，不查这些列。现在的守护是：实现方写集成测试，断言作者、确认人、`decision_ref` 列非空与四眼 CHECK、早于生效日的差异被拒、改删被拒；我在签发前逐条核对实际 diff；扫描脚本是否扩查这些列另议，不作为本次签发依据。基线 V20261014141000 属已合并的开放基线（ADR-0038）：开放期内可直接改基线文件，否则追加迁移，由 one-1a-par 与 one-coordinator 按 ADR-0038 选择，本审不限定。
- 业务边界（谁有权确认日历、官方通知如何登记）归 one-architecture/用户，与本审的结构要求分开。

## 更正二（2026-10-21，答 one-1a-par 与 one-architecture 裁定不一致）

1. **待确认状态留在版本表**：同意（`numbering_rule_versions` 先例，免 attendance→approval 依赖）。版本表加 `confirmation_status`（`PENDING_CONFIRMATION`、`CONFIRMED`），确认三列（`confirmed_by`、`confirmed_at`、`decision_ref`）确认前为空、确认后非空（具名 CHECK 联动）；触发器只放行 `PENDING_CONFIRMATION → CONFIRMED` 且只改这三列与状态，禁删禁清空；`state_transitions` 登记该流转（ADR-0058）。原稿「版本行只在确认通过时插入、无可变状态」作废；日期表仍全程只追加，且只能在版本为待确认时插入、确认后拒绝再插（触发器）。
2. **`decision_ref`** 取「确认操作的审计记录 ID」：同意（`text`，确认后非空）。
3. **迁入行的人（与 one-architecture 对齐后的定稿）**：`authored_by`、`confirmed_by` 为空当且仅当 `source_kind = MIGRATION`（具名 CHECK）；其余行 `confirmed_by` 必填，`authored_by` 仅运维起草的单人路径可空（`authored_by IS NULL OR confirmed_by IS NULL OR authored_by <> confirmed_by`，形同 `numbering_rule_versions`）；MIGRATION 行 `decision_ref` 非空，指向核对签字的审计记录，签字人由审计记录追溯。更正二第 3 点此前「作者、确认人可空、无签字人」的说法以本条为准，前稿「作者=装载主体」撤回。
4. **批次引用**：不进业务表（ADR-0036 第 3 条、§14.2 M5；one-architecture 已同意，作废「须带批次引用」）。迁入版本在 `platform_integration.source_references` 有对照行（带批次列），迁入测试断言每个 MIGRATION 版本都有对照行。
5. **官方通知文号列改名**：不用 `source_reference`（词表里它是来源对照，同名异义）；新列名交 one-glossary 登记，暂名 `official_notice_number`（`text`，`source_kind = OFFICIAL_NOTICE` 时必填，其余为空）。
