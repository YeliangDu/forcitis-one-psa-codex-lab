# Data design review: organization.payroll slice PY0 (rule-version structure, two tables)

Card: PY0 of `docs/architecture/proposals/organization-payroll.md` section 10 (design card #1403; implementation card not opened yet). Basis: that spec sections 3 C-4/C-5, 4.1, 7, 8, 9 at origin/main `a61c6c42c`, state machine `payroll_rule_version` in `docs/architecture/proposals/closeout/people/state-machines.yaml`, gate `docs/data/data-design-gate.md` appendix A. Signed by one-design (same form as `organization-leave-l1a.md`).

## Scope

Schema `organization_payroll` (this migration creates it), two tables: `payroll_rule_versions`, `payroll_rule_rows`. **Structure only: the migration inserts no rows.** The statutory and legacy values (tax bands, ratios, base limits, rounding) are rows written later through `PublishPayrollRuleVersion`; they wait for statute and #1373 and need no schema change (C-5).

Not signed here, and not to be built by the PY0 card:

- `bonus_inputs` (PY3): it holds one person's pay amount per month. Its retention and classification are the user's items under the gate (spec P-9 is unanswered), so it is **not** signed. See "Why PY3 is not signed" below.
- Every other `organization_payroll` table (profiles, enrolments, settlements, lines, disbursements, submissions, filings, special deductions): later slices, each with its own record.

## Table to object source (B1, B2)

| Table | Source | Aggregate | Registry role |
|---|---|---|---|
| `payroll_rule_versions` | AGG-119 (spec 4.1) | PayrollRuleVersion | `CONFIG_VERSIONED`-style root, `CONDITIONAL_UPDATE` (see invariants) |
| `payroll_rule_rows` | AGG-119 content (spec 4.1) | PayrollRuleVersion | `AGGREGATE_CHILD`, `REPLACE_SET` while the version is `DRAFT`, frozen afterwards |

One version is one aggregate (state machine note: 每版本一个聚合). Class words and names follow `naming-standard.md` 2.4/2.4a; audit columns `created_at`, `updated_at` on both tables, `version bigint NOT NULL DEFAULT 0` on the root. Keys are UUIDv7 `id`, primary key `pk_<table>`. User ids (`drafted_by_user_id`, `approved_by_user_id`) belong to `platform.identity`: `uuid`, no foreign key (cross-BC rule B4).

## Columns and constraints

### payroll_rule_versions

| Column | Type | Rule |
|---|---|---|
| `id` | uuid | pk |
| `rule_kind` | text NOT NULL | `ck_payroll_rule_versions_rule_kind`: `INCOME_TAX`, `SOCIAL_INSURANCE`, `HOUSING_FUND`, `ROUNDING`. The category of the state machine note ("同类别同适用范围") |
| `jurisdiction_key` | text NOT NULL | scope of the rule: `CN` for the national IIT rule, a locality key for insurance and fund rules; format CHECK `~ '^[A-Z][A-Z0-9_]{1,31}$'`. The value list is data, not a CHECK |
| `effective_from_date` | date NOT NULL | first day the version applies |
| `effective_to_date` | date null | 为空表示 open-ended (no successor yet); `ck_payroll_rule_versions_effective_interval` `effective_to_date IS NULL OR effective_to_date >= effective_from_date` |
| `status` | text NOT NULL | `DRAFT`, `EFFECTIVE`, `SUPERSEDED` (state machine; the repo's enum word is `status`, not the sketch's `version_status`) |
| `drafted_by_user_id` | uuid NOT NULL | author |
| `approved_by_user_id` | uuid null | 为空表示 not published yet; `ck_payroll_rule_versions_approver_columns` `(status = 'DRAFT') = (approved_by_user_id IS NULL)` |
| `published_at` | timestamptz null | 为空表示 not published yet; same pairing CHECK as above |
| `source_note` | text NOT NULL | where the values come from (statute article, local notice, or legacy evidence reference); `char_length` 1 to 500 and `btrim` CHECKs. Free text but never personal data (allowed content: citations only) |
| `created_at`, `updated_at`, `version` | | audit |

Constraints:

- `ck_payroll_rule_versions_drafter_not_approver` `approved_by_user_id IS NULL OR approved_by_user_id <> drafted_by_user_id` (U7(3), drafter and approver differ).
- `ex_payroll_rule_versions_effective_interval_overlap` `EXCLUDE USING gist (rule_kind WITH =, jurisdiction_key WITH =, daterange(effective_from_date, effective_to_date, '[]') WITH &&) WHERE (status IN ('EFFECTIVE','SUPERSEDED'))` (INV-139; needs `btree_gist`, which other baselines already create). A `DRAFT` may overlap; publishing is the check. `SUPERSEDED` rows stay in the exclusion so history never overlaps either.
- `ck_payroll_rule_versions_supersede_has_end` `status <> 'SUPERSEDED' OR effective_to_date IS NOT NULL`.
- Trigger `guard_payroll_rule_version_change`: a row that is `EFFECTIVE` or `SUPERSEDED` cannot be deleted; its only legal updates are `EFFECTIVE` to `SUPERSEDED` with `effective_to_date` set in the same statement (the successor's publish closes the interval, D15: a correction is a new version, never an edit). `DRAFT` to `EFFECTIVE` sets `approved_by_user_id`, `published_at` and nothing else. Same condition form as `work_calendar_versions` (`CONDITIONAL_UPDATE`).
- Index `ix_payroll_rule_versions_lookup` `(rule_kind, jurisdiction_key, effective_from_date)` (the calculation looks up the version in force for a month; at most hundreds of rows).

### payroll_rule_rows

| Column | Type | Rule |
|---|---|---|
| `id` | uuid | pk |
| `rule_version_id` | uuid NOT NULL | `fk_payroll_rule_rows_rule_version_id` RESTRICT; index starts with it (G-DDL2) via the unique key below |
| `row_kind` | text NOT NULL | `ck_payroll_rule_rows_row_kind`: `INCOME_TAX_BAND`, `INCOME_TAX_MONTHLY_BASIC_DEDUCTION`, `SOCIAL_INSURANCE_RATE`, `SOCIAL_INSURANCE_BASE_LIMIT`, `HOUSING_FUND_RATE`, `HOUSING_FUND_BASE_LIMIT`, `ROUNDING_MODE` |
| `row_key` | text NOT NULL | insurance kind key for the insurance rows (for example a pension or medical key from the registered component list), band number text for `INCOME_TAX_BAND`, `-` where the kind has one row; format CHECK `~ '^[A-Za-z0-9_-]{1,40}$'`. Insurance kind values are data (registered later with #1373), not a CHECK |
| `party_kind` | text null | `EMPLOYER`, `EMPLOYEE`; 为空表示 the row has no side (bands, deduction, base limits, rounding) |
| `lower_bound_amount` | numeric(19,2) null | 为空表示 the kind has no lower bound; `>= 0` |
| `upper_bound_amount` | numeric(19,2) null | 为空表示 no upper bound (top band, or the kind has none); `> lower_bound_amount` when both are set |
| `rate_ratio` | numeric(9,6) null | 为空表示 the kind has no ratio; `0 <= rate_ratio <= 1` |
| `quick_deduction_amount` | numeric(19,2) null | 为空表示 not a tax band; `>= 0` |
| `amount` | numeric(19,2) null | 为空表示 the kind has no flat amount (used by the monthly basic deduction); `>= 0` |
| `rounding_mode` | text null | 为空表示 not a rounding row; `HALF_UP`, `HALF_EVEN`, `DOWN` (numeric precision itself is C-4: `numeric(19,2)` CNY) |
| `created_at`, `updated_at` | | audit |

Constraints: `ck_payroll_rule_rows_kind_columns`, one CHECK per kind giving exactly which of the six nullable columns must be set and which must be null (`INCOME_TAX_BAND`: lower, rate and quick deduction set; `INCOME_TAX_MONTHLY_BASIC_DEDUCTION`: amount only; `*_RATE`: rate and party; `*_BASE_LIMIT`: lower and upper; `ROUNDING_MODE`: rounding mode only); `ck_payroll_rule_rows_bounds_order` (upper above lower); `uk_payroll_rule_rows_version_kind_key_party` unique on `(rule_version_id, row_kind, row_key, COALESCE(party_kind, '-'))` (one row per version, kind, key and side; band numbers make band rows distinct). Trigger `guard_payroll_rule_row_change`: insert, update and delete only while the parent version is `DRAFT` (published rows are frozen, spec section 8).

## Differences from the section 8 sketch

1. `version_status` becomes `status`; `drafted_by`/`approved_by` become `drafted_by_user_id`/`approved_by_user_id` (`_id` is `uuid`, naming standard 2.4).
2. Added `rule_kind` (the state machine's "同类别") so the overlap constraint is per category and jurisdiction, `published_at`, `source_note` (every value needs a citation, spec 4.1) and the `SUPERSEDED` state of the state machine.
3. The sketch's generic `key, value` row becomes typed columns with a per-kind CHECK, so no `jsonb` or free value column is needed and a wrong shape is refused by the database.

None of these changes a command or an event.

## Invariants to constraints (B5, O4)

| Invariant | Carrier | Reason when no constraint |
|---|---|---|
| Effective intervals do not overlap per category and jurisdiction (INV-139) | `ex_payroll_rule_versions_effective_interval_overlap` | |
| Drafter differs from approver | `ck_payroll_rule_versions_drafter_not_approver` | |
| Published versions and their rows never change (D15) | `guard_payroll_rule_version_change`, `guard_payroll_rule_row_change` | |
| Only the successor's publish closes an interval | trigger (legal update set above) | |
| Status matches approval columns | `ck_payroll_rule_versions_approver_columns` | |
| Row shape per kind | `ck_payroll_rule_rows_kind_columns` | |
| One row per version, kind, key and side | `uk_payroll_rule_rows_version_kind_key_party` | |
| A lookup that finds no version fails with a named error | | read-side behaviour of `CalculateMonthlyPayroll` (spec done-when 3), not storable |
| Bands are contiguous, start at zero, the top band is open; every kind a category needs is present | | cross-row; checked by `PublishPayrollRuleVersion` before `EFFECTIVE` |
| Row kind belongs to the version's `rule_kind` | | cross-table; checked by the publish command (a trigger on the rows could, but the publish check already refuses the version) |
| Approver permission and four-eyes beyond the column pair | | permission catalogue and the command |
| State machine transitions `DRAFT` to `EFFECTIVE` to `SUPERSEDED` | | the enum CHECK limits values; the trigger limits the legal updates; the command records `state_transitions` |

## Business key, class, time (O3, O6)

| Table | Business key | Class | Versioning |
|---|---|---|---|
| `payroll_rule_versions` | (`rule_kind`, `jurisdiction_key`, `effective_from_date`) among non-draft rows (enforced by the overlap exclusion) | configuration, declared | effective-interval versions, append only after publish |
| `payroll_rule_rows` | (`rule_version_id`, `row_kind`, `row_key`, side) | configuration, declared | versioned by the parent |

## Query paths (O10)

| Path | Filter | Sort | Expected rows | Page | Index |
|---|---|---|---|---|---|
| Version in force for a category, jurisdiction and month | `rule_kind`, `jurisdiction_key`, month inside interval, `status = 'EFFECTIVE'` | none | 1 | none | `ix_payroll_rule_versions_lookup` and the exclusion's gist index |
| Version list for the rule editor | `rule_kind`, optional `jurisdiction_key` | `effective_from_date` desc, `id` | tens | cursor | `ix_payroll_rule_versions_lookup` |
| Rows of a version | `rule_version_id` | `row_kind`, `row_key` | tens | none | `uk_payroll_rule_rows_version_kind_key_party` |

Row counts stay in the hundreds for years; no partition, no `fillfactor`.

## Cross-cutting, ADR clauses, value objects (O13, O14, O15, O16)

- Money and ratios: `numeric(19,2)` CNY amounts; `numeric(9,6)` ratios; rounding is a row (`ROUNDING_MODE`), not code (C-4, C-5). Time zone: dates only. No legal entity column: rules are global statute and locality data, not entity data (O13: a table carries a legal entity id only when it belongs to one entity). No analytical fact (A1 to A8: none).
- ADR clauses: ADR-0087 (payroll boundary, values are data) and ADR-0079 section 4 apply to the structure; no ADR column rule applies beyond the naming standard. `jsonb`: none. Free text: only `source_note`, content limited to citations (C4 list).

## Registry drafts and classification (C1 to C5)

| Table | Registry |
|---|---|
| `payroll_rule_versions` | role `CONFIG_VERSIONED`, aggregate `PayrollRuleVersion`, mutability `CONDITIONAL_UPDATE` with condition "`DRAFT` to `EFFECTIVE` (publish), and `EFFECTIVE` to `SUPERSEDED` closing `effective_to_date` when the successor is published (`guard_payroll_rule_version_change`)", retention `PERMANENT`, lineage `NEW` (values entered by authors; no legacy import), writers `organization.payroll` |
| `payroll_rule_rows` | role `AGGREGATE_CHILD`, aggregate `PayrollRuleVersion`, mutability `REPLACE_SET` while the version is `DRAFT`, retention `PERMANENT`, lineage `NEW`, writers `organization.payroll` |

**Retention basis.** `PERMANENT` is the user-approved design口径 for non-personal configuration version tables (`work_calendar_*`: 「维持」, `docs/data/retention-proposal.md`, the same registry roles); these two tables hold public statute values and a citation, no personal information, so C3's ban on permanent retention (it applies to `PI` tables) does not bite, and permanence is also what lets any past month be recomputed exactly. If the user wants a limit, it is a one-line registry change later.

| Column | Access level | PI | Reason |
|---|---|---|---|
| all columns of both tables | `INTERNAL` (the unregistered default) | `NONE` | 用途：public statute and locality rule values; `drafted_by_user_id` and `approved_by_user_id` are user ids of staff acting in their role and follow the platform default for audit identities; `source_note` carries citations only |

## Comments (C1)

Table comments: `payroll_rule_versions`: 「粒度：每个薪酬规则类别 × 适用地区 × 生效区间一行，已生效的行不可改，只能被后继版本关闭区间」; `payroll_rule_rows`: 「粒度：每个规则版本 × 行类别 × 键 × 一方一行，版本生效后冻结」. Every column has a comment; each nullable column's states 「为空表示」 as in the tables above.

## Baseline, target, transition, rollback (T1 to T4)

New schema, new tables, no legacy table of this name (`LegacyShapeTest`: names are the new system's own). Transition: none (no data). Rollback: drop the two tables and the schema, nothing else references them until PY4. Expected diff for the schema PR: `organization_payroll.payroll_rule_versions`, `organization_payroll.payroll_rule_rows`, their constraints, indexes, the two guard triggers and functions, and `CREATE EXTENSION IF NOT EXISTS btree_gist`.

## Events and read models (E1 to E3)

`PayrollRuleVersionEffective` (analytical, ids only) is raised by `PublishPayrollRuleVersion`; its payload is written in `docs/architecture/events/` with the implementing slice, because a catalogue entry of a code-tier event without its publisher fails `EventCatalogueTest`. No read model, no migration mapping.

## Why PY3 is not signed

`bonus_inputs` holds, per relationship and month, an amount a person is paid. Gate item C3/C4 (retention and classification) is the user's item for pay amounts (spec P-9, recommendation `CONFIDENTIAL`, limited retention as wage records; unanswered). The gate gives no conditional pass (1.3), so the table is carved out until the user answers P-9, and the same answer unblocks every other per-person payroll table (profiles, enrolments, settlements, lines).

## Result

数据设计审查
- 审查：one-design，2026-11-05，依据 origin/main `a61c6c42c`
- 判定：B1、B2、B3a、B5、O3、O4、O6、O10、O13、O14、O15、C1 至 C5、T1 至 T4、E1 通过（两张表）；`bonus_inputs` 与其余个人薪酬表不在本次范围，C3/C4 待用户（P-9）
- 豁免与待用户决定：薪酬个人数据表的保留期与分级（spec P-9）；规则表 `PERMANENT` 依据 `work_calendar_*` 先例，用户可随时改
- 结论：通过（仅 `payroll_rule_versions`、`payroll_rule_rows`）
- 数据设计：通过（one-design，2026-11-05，依据 a61c6c42c）
