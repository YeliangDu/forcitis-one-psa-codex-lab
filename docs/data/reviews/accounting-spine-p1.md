# Data design review: accounting spine slice P1 (fiscal periods, one table)

Card: #1434 (design; Refs #1402). Slice P1 of `docs/architecture/proposals/accounting-spine.md` section 10 (dev card not opened yet). Basis: that spec sections 3 C-5/C-6, 4.1, 5, 8, 9 (Q-8 answered) at origin/main `a22fdd7f4`; ADR-0045 (closed periods are final), ADR-0046 decisions 8.2/8.3, ADR-0087 5.2; gate `docs/data/data-design-gate.md` Appendix A. Signed by one-design (same form as `organization-leave-l1a.md` and `organization-payroll-py0.md`).

## Scope

Schema `accounting_period` (this migration creates it), one table: `fiscal_periods`. The migration inserts no rows: `OpenFiscalYear` writes them (a command, not a migration).

Not signed here, and not to be built by the P1 card:

- `posting_facts` and the other `operations_facts` tables (slice F1 and F2; F1 waits for P1 to merge): later records.
- Every `accounting_ledger` table (G1 has its own record `accounting-spine-g1.md`; G2/G3 later).
- Period 13 rows (AGG-96): the `period_number` CHECK allows 13 so AGG-96 needs no migration, but P1 creates no such row.

## Table to object source (B1, B2)

| Table | Source | Aggregate | Registry role |
|---|---|---|---|
| `fiscal_periods` | AGG-81 AccountingPeriod (spec 4.1) | AccountingPeriod, one row = one aggregate | `AGGREGATE_ROOT`, `CONDITIONAL_UPDATE` (see invariants) |

Names follow `naming-standard.md` 2.4/2.4a. Key: UUIDv7 `id`, primary key `pk_fiscal_periods`. `legal_entity_id` belongs to `organization.workforce`: `uuid`, no foreign key (cross-BC rule B4, ADR-0086).

## Columns and constraints

| Column | Type | Rule |
|---|---|---|
| `id` | uuid | pk |
| `legal_entity_id` | uuid NOT NULL | the entity whose books the period belongs to; no FK (B4); the command checks the entity exists through the workforce api |
| `fiscal_year_number` | integer NOT NULL | calendar year (spec Q-2); `ck_fiscal_periods_fiscal_year_number` `BETWEEN 2000 AND 2999` (a typo guard; the command also refuses a year more than one ahead, spec 4.1). The sketch's `fiscal_year` is renamed because `_number` is the class word for it (`DdlNamingTest`; same as `year_number` in `work_calendar_years`) |
| `period_number` | integer NOT NULL | `ck_fiscal_periods_period_number` `BETWEEN 1 AND 13`. The sketch says `smallint`; `DdlNamingTest` accepts `_number` only as `text`, `integer` or `bigint`, so `integer` |
| `status` | text NOT NULL | `ck_fiscal_periods_status`: `OPEN`, `CLOSING`, `CLOSED` (ADR-0045 decision 1) |
| `created_at`, `updated_at`, `version` | | audit; `version bigint NOT NULL DEFAULT 0` |

No date columns: start and end follow from year and number (ADR-0046 decision 8.2); period 13 has no dates, so a date pair would be null for it by design.

Constraints:

- `uk_fiscal_periods_legal_entity_id_fiscal_year_number_period_number` on `(legal_entity_id, fiscal_year_number, period_number)`. It leads with `legal_entity_id`, so it is also the index every entity-scoped read needs and `PeriodGate` looks a period up by it.
- Trigger `tg_fiscal_periods_change` (and `tg_fiscal_periods_truncate`, which refuses TRUNCATE): the full list of legal writes, everything else is refused. (1) Insert: any `status` (P1 writes `OPEN`; tests seed the other two with a repository fixture, spec C-6). (2) Update: only the edges `OPEN` to `CLOSING` and `CLOSING` to `CLOSED`, and only `status`, `updated_at`, `version` change. (3) A `CLOSED` row accepts no update (INV-99, ADR-0045: closed periods never reopen). (4) **No delete at all**: facts refer to a period by (entity, year, number) without a foreign key (other BC), so removing an `OPEN` row would leave facts pointing at nothing; a mistaken year is corrected by an ops migration, not by the application. The sketch said only "CLOSED accepts no delete"; refusing every delete is my narrowing, and any later need is a lossless widening of the trigger.
- The edge `CLOSING` to `OPEN` (a close that is abandoned) is **not** in the list: ADR-0045 and the spec leave it to the closing batch (section 4.1, "gaps … are for the closing batch to define"). The closing batch adds it with its own sign-off if it wants it. Carving it out is the gate's way (1.3: no conditional pass).
- Index: none beyond the unique key. The gate lookup is by the whole key; "lowest-numbered `OPEN` period of an entity" (the current open period) reads at most 13 rows per year through the same index.

## Differences from the section 8 sketch

1. `fiscal_year` becomes `fiscal_year_number`; `period_number` is `integer` (class-word types).
2. Added `ck_fiscal_periods_fiscal_year_number` and the no-delete rule; the allowed status edges are listed instead of "CLOSED is frozen" alone.
3. Constraint names follow the repo's `uk_<table>_<columns>` form.

None of these changes a command or an event. `OpenFiscalYear` raises no event (spec 4.1).

## Invariants to constraints (B5, O4)

| Invariant | Carrier | Reason when no constraint |
|---|---|---|
| One row per entity, year and number | `uk_fiscal_periods_legal_entity_id_fiscal_year_number_period_number` | |
| Status is one of three | `ck_fiscal_periods_status` | |
| Number is 1 to 13 | `ck_fiscal_periods_period_number` | |
| A closed period never changes and no period row is deleted (INV-99) | `tg_fiscal_periods_change`, `tg_fiscal_periods_truncate` | |
| Only the two forward edges are legal | trigger (legal update set above) | |
| `OpenFiscalYear` creates exactly twelve periods for one entity and year (S-R1) | | cross-row; one transaction in the command, covered by the done-when tests; a partial year cannot be left by a failure because the transaction rolls back |
| A second call for the same year fails | the unique key | |
| A year more than one ahead of the latest opened is refused | | depends on other rows; the command |
| Entity exists | | cross-BC; the command through the workforce api |
| A read that a close cannot slip past (INV-92) | | read-side: `PeriodGate` uses `SELECT … FOR SHARE` in the posting transaction |

## Business key, class, time (O3, O6)

| Table | Business key | Class | Versioning |
|---|---|---|---|
| `fiscal_periods` | (`legal_entity_id`, `fiscal_year_number`, `period_number`) | state-carrying aggregate row, declared | none; the row's own `status` moves forward only |

## Query paths (O10)

| Path | Filter | Sort | Expected rows | Page | Index |
|---|---|---|---|---|---|
| Gate: period of a date | entity, year, number (one row), shared lock | none | 1 | none | the unique key |
| Current open period of an entity | entity, `status = 'OPEN'` | `fiscal_year_number`, `period_number` | at most 24 rows read, take the first | none | the unique key (leading column) |
| Period list for finance | entity, optional year | year, number | 12 per year | none (a year is at most 13 rows) | the unique key |

Tens of rows per entity per decade, about five entities: no partition, no `fillfactor`.

## Cross-cutting, ADR clauses, value objects (O13 to O16)

- Legal entity column: present, because every period belongs to one entity (O13). Time zone: none (no timestamp beyond audit). No money. `jsonb`: none. Free text: none. No analytical fact (A1 to A8: none; `OpenFiscalYear` raises no event).
- ADR clauses: ADR-0045 (closed periods final), ADR-0046 decision 8.2 (no date columns), ADR-0087 5.2 (period 13 not enabled in the spine). Data scope, fail-closed: reads are filtered by the caller's entity scope (spec 5).

## Registry drafts and classification (C1 to C5)

| Table | Registry |
|---|---|
| `fiscal_periods` | role `AGGREGATE_ROOT`, aggregate `AccountingPeriod`, business key `[legal_entity_id, fiscal_year_number, period_number]`, mutability `CONDITIONAL_UPDATE` with condition "`OPEN` to `CLOSING` and `CLOSING` to `CLOSED`, status columns only; a `CLOSED` row never changes; no delete (`tg_fiscal_periods_change`)", retention `PERMANENT`, lineage `NEW`, writers `accounting.period` |

**Retention.** `PERMANENT`: the spec's Q-8, answered by the user 2026-11-05 (adopt the recommendation: accounting records, statutory minimum 30 years under `会计档案管理办法`, ADR-0022 decision 9). The table holds no personal information, so C3's ban on permanent retention for `PI` tables does not apply.

| Column | Access level | PI | Reason |
|---|---|---|---|
| all columns | `INTERNAL` (the unregistered default) | `NONE` | 用途：calendar structure of a legal entity's books; no personal data and no amounts. `CONFIDENTIAL` (user word) = level `SENSITIVE` in the register (Q-12 a) applies to the payroll fact tables, not to this table |

## Comments (C1)

Table comment: 「会计期间：粒度：每个法人 × 会计年度 × 期间序号一行，已关闭的期间不可再改，不删除」. Every column has a comment; every column is NOT NULL, so no 「为空表示」 line is needed.

## Baseline, target, transition, rollback (T1 to T4)

Baseline: no `accounting_period` schema, no table of this name in the legacy system (`LegacyShapeTest`: names are the new system's own). Target: the table above. Transition: none (no data; legal entities exist, and `OpenFiscalYear` is a command after the migration). Rollback: drop the table and the schema while empty (nothing references it until F1 reads it through `PeriodGate`). Expected diff for the schema PR: `accounting_period.fiscal_periods`, its CHECKs, unique key, the change trigger and the truncate trigger with their functions, comments; registry entry and classification file unchanged for `INTERNAL` (default); the glossary check of the new names is the dev card's B8 item (see below). No `btree_gist` needed.

## Events and read models (E1 to E3)

No event, no read model, no migration mapping. The read API `PeriodGate` is a code interface, not a read model.

## Open items for the dev card (not blocking the signature)

- B8: the glossary has `accounting_period`; the table name `fiscal_periods` and column names need their term check by `GlossaryTest` in the dev card (spec section 7 already lists the glossary registration as the schema card's job).
- Context `accounting.period` is `planned` in `contexts.yaml`; the dev card moves it to built with the first code, with the module edges of spec section 6.

## Result

数据设计审查
- 审查：one-design，2026-11-05，依据 origin/main `a22fdd7f4`
- 判定：B1、B2、B3、B4（跨 BC 无外键）、B5、B6、B7（枚举三值）、O1、O2（UUIDv7 主键）、O3、O4、O5、O6、O7、O9、O10、O10a、O11（行数少，无需分区）、O12、O13、O14、O15、O16、C1 至 C5、T1 至 T4、E1 通过，证据见上文各节；A1 至 A8 不适用（无分析事实）；B8（术语表）按请假 L1a 先例记为实现卡前置，不在本记录内
- 豁免与待用户决定：无；保留期 `PERMANENT` 与分级按 spec Q-8（用户 2026-11-05 已答：采纳建议）
- 收窄（非条件通过）：`CLOSING` 到 `OPEN` 的边不在触发器内，留给关账批次自己的签字；本表一律不删行
- 结论：通过（仅 `fiscal_periods`）
- 数据设计：通过（one-design，2026-11-05，依据 a22fdd7f4）
