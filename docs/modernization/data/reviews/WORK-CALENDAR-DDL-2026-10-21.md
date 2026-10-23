# 工作日历 DDL 审（`organization_attendance` 基线 V20261014141000）

- 日期：2026-10-21；审者：one-data；对象：origin/main 上 `work_calendar_years`、`work_calendar_days`（注册表 `data-access-registry.yaml` 37–50 行：AGGREGATE_ROOT / MUTABLE / PERMANENT 与 AGGREGATE_CHILD / REPLACE_SET / PERMANENT）。
- 依据：DATA_DESIGN_CHECKLIST §14/§14.1 D6、D8、D12、D15、D17、D19；ADR-0033 第 9 项第 3 点（日历版本表 `work_calendar_versions` / `work_calendar_version_days`、`calendar_scope_kind`、部分唯一索引）；用户 2026-10-15「配置类要四眼确认」（intent 2026-10-15-012200）、2026-10-16「calendar_scope_kind」（intent 2026-10-16-085332）；NAMING_STANDARD；DATA_DESIGN_GATE。

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

## 更正三（2026-10-21，与 one-architecture 对齐的依据列定稿）

1. 版本行两列并存：`source_kind`（`AUTHORED`、`OPERATIONS_DRAFT`、`MIGRATION`，具名 CHECK）说明这一版怎么产生，决定作者、确认人规则（更正二第 3 点）；`adjustment_basis_kind`（`OFFICIAL_NOTICE`、`MANUAL`，具名 CHECK，`NOT NULL`，迁入行也不例外）说明调整依据。前文 `source_kind` 的 `OFFICIAL_NOTICE`/`MANUAL` 取值作废。
2. `official_notice_number`（暂名，词表登记后定名）非空当且仅当 `adjustment_basis_kind = 'OFFICIAL_NOTICE'`（具名 CHECK），长度上限。
3. 依据列落在版本行，不落例外日行。迁入版本缺文号的，按 MQ-C-01 拒收，由人事补文号或如实记 `MANUAL`，不得默认填值。

## 更正四（2026-10-21，用户新要求：日历日期带依据、版本按 K-2 形状、确认须挂依据；PR #506 评论 6314490252、批准页 C1，intent @52bc173f、@fb236db8）

**① 日期行依据列**：`work_calendar_version_days.basis_kind text NOT NULL`，具名 CHECK IN (`OFFICIAL` 官方通知、`COMPANY` 公司加休、`MANUAL` 手工更正)。**不可空**：可空等于「不知道为什么休息」，正是这次审计要消灭的状态；表是新建的，没有存量行要兼容。迁入行按来源填，来源说不清的按 MQ-C-01 拒收，由人事补依据或如实记 `MANUAL`。`MANUAL` 行另须 `basis_note text`（依据说明，非空、有长度上限，具名 CHECK：`basis_kind = 'MANUAL'` 当且仅当 `basis_note` 非空）；批准人即该版本的确认人（四眼）。更正三的版本行 `adjustment_basis_kind` 取消（依据已细到日），`official_notice_number` 的「当且仅当」条件改为下条。

**② 版本表按 K-2 形状**（D6 / ADR-0034 的区间约定，暂不接 K-2 基础设施，只让形状一致，将来迁移是搬数据不是改结构）：
- 版本行：`version_number`（≥1，每 `work_calendar_id + year_number` 从 1 递增）、`effective_from_date`、`effective_to_date`（空表示开放；只能把开放版本关闭一次，其余列不可改；具名 CHECK `effective_to_date IS NULL OR effective_to_date >= effective_from_date`，允许零长，同 ADR-0034）；确认人 `confirmed_by`、确认时刻 `confirmed_at`、依据 `decision_ref`（确认操作的审计记录 ID）。
- 同一日历同一年**已确认**版本的生效区间不重叠：`EXCLUDE USING gist (work_calendar_id WITH =, year_number WITH =, daterange(effective_from_date, effective_to_date, '[)') WITH &&) WHERE (confirmation_status = 'CONFIRMED')`（沿用 workforce 的做法与 btree_gist 扩展，实现时核扩展已建）。待确认版本不占区间。
- 确认新版本（`version_number > 1`）时，触发器把上一已确认版本的 `effective_to_date` 关为新版本的 `effective_from_date`（唯一允许的二次改动，形同 `refuse_version_rewrite`）；禁删禁清空。其余同更正二、三（待确认状态、四眼空当且仅当迁入、首版豁免生效日检查）。

**③ 确认必须挂依据，库层约束**：版本行加 `official_notice_url text`、`official_notice_number text`（暂名，同更正三）、`company_leave_register_ref text`（公司加休登记的引用；登记本身是不是表、是什么形状由 one-architecture 定，库里先存引用文本，定了形状再加外键）。`PENDING → CONFIRMED` 的触发器在确认时检查本版本日期行：
- 有 `OFFICIAL` 日期行 → `official_notice_url` 与 `official_notice_number` 都非空；
- 有 `COMPANY` 日期行 → `company_leave_register_ref` 非空；
- 有 `MANUAL` 日期行 → 每行 `basis_note` 非空（CHECK 已保证）；
- 不满足即拒绝确认，错误码 `check_violation`；日期行只能在版本待确认时插入，所以确认时看到的就是最终集合。
- 三个依据列都有格式 CHECK（链接 `^https://` 且长度上限；其余长度上限）；MIGRATION 来源的版本同受此约束（迁入缺依据按 MQ-C-01 拒收）。
**待用户认定的一点**：「缺一不许确认」我按「用到哪类依据就必须挂哪类」实现。若用户意思是「官方通知与公司加休登记每个版本都必须同时挂」，则去掉上面的条件、两列恒为必填；这样没有加休的年份也得挂一份「无加休」登记。请 one-1a-par / one-coordinator 向用户确认后告诉我。

## 更正五（2026-10-21，ADR-0072 已采纳后的重签；用户 A72、C1，PR #506 @ba5c5c00）

1. **更正四「待用户认定」一点已有答案：恒必填，不是按用途**。凡 `CONFIRMED` 版本：`official_notice_number`、`official_notice_url`、加休登记三者都非空；没有加休也要显式登记「无」。库层写法：
   - `company_leave_register_kind text`，具名 CHECK IN (`REGISTERED`、`NONE`)；`company_leave_register_ref text`，`REGISTERED` 时非空、`NONE` 时为空（具名 CHECK）；确认前三列可空，`CONFIRMED` 时 `official_notice_number`、`official_notice_url`、`company_leave_register_kind` 都非空（具名 CHECK 与确认状态联动，加上确认触发器双保险）；
   - 一致性：有 `COMPANY` 日期行的版本，`company_leave_register_kind` 必须是 `REGISTERED`（`NONE` 与 `COMPANY` 日期行矛盾，确认触发器拒绝）；
   - 更正三「`official_notice_number` 非空当且仅当 OFFICIAL_NOTICE」作废，更正四③「用到哪类才必挂」作废，其余条目（链接 `^https://` 与长度上限等）不变。
2. **`adjustment_basis_kind` 保留**在版本行（NOT NULL，`OFFICIAL_NOTICE`/`MANUAL`），只表示这一版改动的依据；日期行 `basis_kind`（OFFICIAL/COMPANY/MANUAL）说明每个例外日为什么是例外，两者不冲突。
3. **K-2 形状**：同更正四②，已含生效区间（起止日）、版本号、作者与确认人、确认时刻、依据（`decision_ref`）。
4. **「次年是否已确认」查询**：按 (日历, 年份) 取已确认版本，`uk_…(work_calendar_id, year_number, version_number)` 加 `EXCLUDE` 的索引已覆盖，另加部分索引 `ix_work_calendar_versions_confirmed ON (work_calendar_id, year_number) WHERE confirmation_status = 'CONFIRMED'`，让 11 月 15 日起的每日告警和 12 月 1 日起的阻断检查走索引；不需要新列。告警与阻断是应用行为，不在表结构里。

## 更正六（2026-10-21，答 one-coordinator：公司取消官方调休的那几天要记一行）

**结论：要记，`day_kind` 加第三种取值 `MAKE_UP_WORKDAY_CANCELLED`（官方调休上班、公司取消；暂名，交 one-glossary 登记），不用「`basis_kind = COMPANY` 的休息行」。**

理由：日期行的含义是「相对周一至周五上班、周末休息这条默认规则的例外」；`HOLIDAY` 记在周末上语义矛盾（周末本来就休息），而且读取方会把它误当作多放的假；取消调休的周末对有效日历来说就是普通周末，不是例外。单列一种取值，读取方一条规则处理：`MAKE_UP_WORKDAY_CANCELLED` 按普通周末休息计，行本身只承担「查得到为什么不按官方上班」。

表结构：
- `day_kind` 具名 CHECK 取值 `HOLIDAY`、`MAKE_UP_WORKDAY`、`MAKE_UP_WORKDAY_CANCELLED`；
- 调休必在周末的 CHECK 扩到两种调休取值（`ck_…_make_up_weekend`：`day_kind NOT IN ('MAKE_UP_WORKDAY', 'MAKE_UP_WORKDAY_CANCELLED') OR ISODOW IN (6, 7)`）；
- 具名 CHECK：`day_kind = 'MAKE_UP_WORKDAY_CANCELLED'` 时 `basis_kind IN ('COMPANY', 'MANUAL')`（取消是公司或手工更正的决定，不是官方的）且 `basis_note` 非空，`basis_note` 写明取消的是哪份官方通知里的哪个调休；
- 同一版本同一日期仍只有一行（`UNIQUE (work_calendar_version_id, calendar_date)`），所以该日在这一版里不再出现 `MAKE_UP_WORKDAY` 行；官方原本的调休安排只留在 `basis_note` 与版本行的官方通知文号、链接里。
- 确认触发器：有 `MAKE_UP_WORKDAY_CANCELLED` 行的版本，`company_leave_register_kind` 必须是 `REGISTERED`（取消调休算公司加休登记的一部分，与 COMPANY 日期行同规则）。
- 迁入：one-ops 投产表单列的 5 天（2022-01-30、2023-01-28/29、2025-01-26、2026-02-14）按此取值迁入，依据说明引用其依据文件；2023-04-23 与 2025-01-25 两条定性未决，未定前按 MQ-C-01 拒收。

## 更正七（2026-10-21，ADR-0072 @545c338f 对齐；intent 2026-10-21-131415、-132249）

1. **版本级 `adjustment_basis_kind` 取消**（更正五第 2 点作废）：依据只按天记，`work_calendar_version_days.basis_kind`（`OFFICIAL`/`COMPANY`/`MANUAL`，不可空，迁入行也不例外），版本行不再有 `adjustment_basis_kind`。词表「调整依据类别」改为按天的「依据」，增 `COMPANY` 取值，由 one-glossary 登记。
2. **`MANUAL` 要有依据和批准人**：依据 = `basis_note`（更正四）；批准人 = 日期行新增列 `approved_by uuid`，具名 CHECK `basis_kind = 'MANUAL'` 当且仅当 `approved_by` 非空，且 `approved_by` 不得等于版本作者（作者非空时）。不能只靠版本确认人：迁入版本的确认人按规则为空，而迁入的 `MANUAL` 行也必须能追到批准人（`MANUAL` 行缺批准人按 MQ-C-01 拒收）。
3. 确认前同时挂官方通知（文号 + 链接）和公司加休登记（可显式「无」）：同更正五，不变。
4. 投产首次写入「官方 + 公司加休表」，`source_kind = MIGRATION`：同更正二、三；其中公司取消官方调休的日子用 `MAKE_UP_WORKDAY_CANCELLED`（更正六）。

## 签发（设计层）

数据设计：通过（one-data，2026-10-21，依据 DATA_DESIGN_GATE、DATA_DESIGN_CHECKLIST §14.1 D6/D15/D17、ADR-0033 第 9 项、ADR-0072 @545c338f 与更正一至七）。

范围：日历表结构的设计条件（根表、版本表按 K-2 形状、按天依据、确认须挂官方通知与加休登记、取消调休取值、四眼与迁入例外）。实际迁移与表结构 diff 出来后，我再复核实际 diff；那一步没做之前，不签表结构 PR。

## 更正八（2026-10-21，列名按命名守护）

日历版本表的 `decision_ref`（确认操作的审计记录 ID）改名 **`decision_audit_entry_id uuid`**：存的是审计记录的 uuid，`_key` 按 NAMING_STANDARD 只用于外部系统的文本标识，uuid 引用用 `_id`；命名守护也不允许裸 `decision_ref`。跨 schema 不建外键，确认后非空（迁入版本指核对签字的审计记录）。同一概念在别处存的若是 intent、法务答复、ADR 编号这类文本出处（如文件服务的保存类别版本），列名用 `decision_ref_key text`，两种写法在 D15 都算「决定出处」，扫描脚本 M6 都认。

## 更正九（2026-10-21，对齐 SEC-1021-COO-01，见 IMP-1021-NX5-03 复核）

作者为空会绕过「确认人不得是作者」。更正二、三里「运维起草单人路径作者可空」撤销：`source_kind` 取值改为 `AUTHORED`（界面起草与运维起草都算，作者必填，确认人不得是作者）与 `MIGRATION`（作者与确认人为空，只由属主身份或约定的迁入角色写入，插入触发器拦运行账号）；`OPERATIONS_DRAFT` 取值取消。其余不变。

## 更正十（2026-10-21，复核表结构 PR #547 @ad1c1f369 的实际 diff 后的裁定）

**对 PR 正文列出的 9 处与签发稿不同的点**：1 三表加 `updated_at`（命名守护强制，不带默认值）、2 `company_leave_register_key`（`_ref` 改 `_key`）、3 `basis_note` 取并集规则、4 不追溯在确认时检查、5 确认时一并写依据列、6 迁入版本写入身份取属主角色成员、7 关闭上一版本只在触发器内、8 同年至多一个待确认且版本号逐个加 1、9 注册表 `optional`+到期日：**全部认可**（5 的列写入范围合理，确认前起草者可留空的依据列由确认者补；6 与编号版本表同款，迁入专用角色的写入通道仍是开放项）。

**三项裁定（均须在本 PR 落实，落实后我只看这三处 diff 即签）**：

1. **待确认版本的撤回出路——加 `WITHDRAWN` 状态**。`confirmation_status` 取值 `PENDING_CONFIRMATION`、`CONFIRMED`、`WITHDRAWN`；`PENDING_CONFIRMATION → WITHDRAWN` 是第二条唯一允许的流转，`WITHDRAWN` 为终态；加列 `withdrawn_by uuid`、`withdrawn_at timestamptz`、`withdraw_reason_text`（非空、≤500 字、前后无空白），具名 CHECK：状态为 `WITHDRAWN` 当且仅当三列非空，且此时确认三列与依据列可以为空；不占 `uk_work_calendar_versions_pending`（索引条件本就是待确认）；版本号已用掉不回收，下一版本仍是最大号加 1。日期行插入守护已限定只能加进待确认版本，撤回后自然不能再加。迁入版本（`MIGRATION`）只有属主角色可撤回。连带改：确认时「上一版本须已确认」改为取**同日历同年中版本号最大的已确认版本**作为被关闭的上一版（且必须仍是开放的）；关闭上一版本的触发器里找后继版本的条件，由「版本号 = 本版 + 1」改为「同日历同年的待确认版本且起算日 = 截止日」。
2. **倒填绕过不追溯——用事务时间做完整性判定**。「生效日不早于确认日」与「确认日期之前的例外日集合须与上一版一致」两项检查，确认日取 `(transaction_timestamp() AT TIME ZONE 'Asia/Shanghai')::date`，不取应用写入的 `confirmed_at`；`confirmed_at` 仍由应用注入、仍是记录的确认时刻（与注入时钟的约定不冲突），只是不再参与这两项完整性判定。不另加 `confirmed_at` 与事务时间的偏差上限（会与测试固定时钟冲突）。测试里的「确认日」相关用例用库里的 `current_date` 构造日期，不用固定时钟。
3. **首版生效日不得晚于该年 1 月 1 日**：具名 CHECK `ck_work_calendar_versions_first_version_covers_year`：`version_number <> 1 OR effective_from_date <= make_date(year_number, 1, 1)`；否则首版之前的日子没有版本可取，留下无法补的年初空洞。

**其余复审项**：
- TEMP 权限：在 c3/c9 核验脚本加 `has_database_privilege(应用账号, 'TEMP') = false`，并把三张新表加进 `one_db_roles_schemas.sql` 撤销 `UPDATE, DELETE, TRUNCATE` 的清单（one-ops 的文件，可同 PR 或 one-ops 另 PR，**首次部署前**必须落地，不阻断本 PR 合并）。
- REPEATABLE READ 下确认与加日期之间可能漏检：应用只用 READ COMMITTED，记录在迁移注释里即可。
- `DataArchitectureTest` 的 TRUNCATE/CASCADE 改动与 #521 同类，由 one-coordinator 的 opus 复审统一看（两个 PR 的该处改动须一致）。
