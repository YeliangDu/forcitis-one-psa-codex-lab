# Data design review: accounting spine slice F1 (posting facts, three tables)

Card: #1434 (design, widened by lead to the F1 facts tables; Refs #1402). Slice F1 of `docs/architecture/proposals/accounting-spine.md` section 10 (dev card not opened yet). Basis: that spec sections 3 C-1 to C-4, 4.2, 5, 8, 9 (Q-8 answered) at origin/main `a22fdd7f4`; ADR-0016 (posting pipeline), ADR-0022 decision 3 (dimensions), ADR-0045 and ADR-0046 decision 8.3 (original period), payroll spec C-4 (money in CNY); lead's ruling Q-12 (a), intent fragment Q-12-a, relayed by lead (not in the repo as a decision page: **unverified** there) (the user's word CONFIDENTIAL is register level `SENSITIVE`); gate `docs/data/data-design-gate.md` Appendix A. Signed by one-design. Prerequisite: P1 merged (spec section 10); this record does not depend on P1's table, because the period columns here are plain integers with no foreign key.

## Scope

Schema `operations_facts` (this migration creates it), three tables and one sequence: `posting_facts`, `posting_fact_lines`, `posting_components`, sequence `posting_fact_post_seq`. The migration inserts no rows.

Not signed here, and not to be built by the F1 card:

- `posting_rejections`, `posting_rejection_dispositions` (slice F2): own record.
- **`posting_components` rows**: the structure is signed; the rows (which component keys exist for payroll) wait for #1373 and are written by the producer's slice (spec C-3). A row set is data, not schema.
- Dimension columns other than `department_id` (spec C-4): each is added, additively, by the first slice that needs it, with its own record.
- Any `accounting_period` or `accounting_ledger` table.

## Table to object source (B1, B2)

| Table | Source | Aggregate | Registry role |
|---|---|---|---|
| `posting_facts` | AGG-77 Posting (spec 4.2) | PostingFact (one fact = one aggregate with its lines) | `AGGREGATE_ROOT`, `APPEND_ONLY` |
| `posting_fact_lines` | AGG-77, lines of a fact | PostingFact | `AGGREGATE_CHILD`, `APPEND_ONLY` |
| `posting_components` | spec C-1/C-3: registered component keys per fact type | none (configuration) | `CONFIG`, `MIGRATION_ONLY` (see registry) |

Names follow `naming-standard.md` 2.4/2.4a. Keys: UUIDv7 `id`, primary key `pk_<table>`. `legal_entity_id`, `source_id` and `department_id` belong to other contexts: `uuid`, no foreign key (cross-BC rule B4, ADR-0086).

## Columns and constraints

### posting_facts

| Column | Type | Rule |
|---|---|---|
| `id` | uuid | pk |
| `post_number` | bigint NOT NULL DEFAULT `nextval('operations_facts.posting_fact_post_seq')` | strictly increasing, gaps allowed (spec 4.2 `PostSeq`). Renamed from the sketch's `post_seq`: `_seq` is not a class word, `_number` is (`DdlNamingTest`) |
| `legal_entity_id` | uuid NOT NULL | the entity; no FK (B4); the pipeline refuses an unknown entity before writing |
| `fact_type` | text NOT NULL | `ck_posting_facts_fact_type`: `PAYROLL` (the only variant the spine ships, spec 4.2). A further value is a lossless widening of the CHECK by the producer's slice |
| `business_date` | date NOT NULL | the date the fact is about |
| `posted_fiscal_year_number`, `posted_period_number` | integer NOT NULL | the period the fact landed in; `ck_posting_facts_posted_period_number` `BETWEEN 1 AND 12` (a posting never reaches period 13, ADR-0046 decision 3.1); `fiscal_year_number` range CHECK as in P1. No FK (other BC; the period gate decides before the write) |
| `original_fiscal_year_number`, `original_period_number` | integer null | 为空表示 the fact was not routed from a closed period (ADR-0046 decision 8.3); `ck_posting_facts_original_pair` both set or both null, and `original_period_number BETWEEN 1 AND 12` |
| `source_context_code` | text NOT NULL | the producing context, for example `organization.payroll`; format CHECK `~ '^[a-z]+(\.[a-z]+)+$'`. Renamed from the sketch's `source_bc` (`_bc` is not a class word; `_code` is) |
| `source_id` | uuid NOT NULL | the producer's object (the payroll run); not checked against the producer (spec B-06) |
| `idempotency_key` | text NOT NULL | the producer's replay key; `char_length` 1 to 200 and `btrim` CHECKs |
| `reverses_fact_id` | uuid null | 为空表示 this fact is not a reversal; `fk_posting_facts_reverses_fact_id` to `posting_facts(id)` RESTRICT; `ck_posting_facts_reverses_not_self` `reverses_fact_id <> id` |
| `created_at`, `updated_at` | timestamptz NOT NULL | audit; `updated_at` is required by `DdlNamingTest` and never differs from `created_at` (no update path). No `version` column: nothing updates the row |

Constraints:

- `uk_posting_facts_post_number` (INV-95 order; consumers sort by it within one entity).
- `uk_posting_facts_replay_key` on `(legal_entity_id, source_context_code, idempotency_key)` (INV-94; the full column-based name would be 68 bytes, over the 63-byte limit of naming standard N3-L1, so the name is shortened, not truncated): a replay returns the original fact and writes nothing; the key is scoped by entity and producer so two producers cannot collide.
- `uk_posting_facts_reverses_fact_id`: one reversal per fact; it also indexes the foreign key (G-DDL2).
- Trigger `tg_posting_facts_append_only` (and `tg_posting_facts_truncate`): insert only; every update and delete is refused (INV-95: a correction is a reversing fact plus a new fact).
- Indexes: `ix_posting_facts_legal_entity_id_post_number` (per-entity order; the closing batch and reconcile read in this order), `ix_posting_facts_legal_entity_id_posted_period` on `(legal_entity_id, posted_fiscal_year_number, posted_period_number, post_number)` (a period's facts), `ix_posting_facts_source` on `(source_context_code, source_id)` (find the fact of a producer object).

### posting_fact_lines

| Column | Type | Rule |
|---|---|---|
| `id` | uuid | pk |
| `fact_id` | uuid NOT NULL | `fk_posting_fact_lines_fact_id` to `posting_facts(id)` RESTRICT |
| `line_number` | integer NOT NULL | position inside the fact; `>= 1`. Renamed from the sketch's `line_no` (`_no` is not a class word) |
| `side_kind` | text NOT NULL | `ck_posting_fact_lines_side_kind`: `DEBIT`, `CREDIT`. Renamed from the sketch's `side` (an enum needs a class word) |
| `component_key` | text NOT NULL | a registered component of the fact's type (rule below); format CHECK `~ '^[A-Z][A-Z0-9_]{1,59}$'` |
| `line_amount`, `line_currency` | numeric(19,2) NOT NULL, text NOT NULL | `ck_posting_fact_lines_line_amount_positive` `line_amount > 0` (direction is the side, not the sign); `ck_posting_fact_lines_line_currency` `= 'CNY'` (C-4, payroll spec C-4 (`organization-payroll.md`, CNY `numeric(19,2)`); CNY-only for facts is my narrowing for payroll facts, the accounting spec section 3 does not decide it; a second currency is a CHECK widening later). Naming standard 2.4 pairs `_amount` with a same-named `_currency`; the sketch's `amount`/`currency` are renamed to carry the class words |
| `department_id` | uuid null | 为空表示 the line has no department dimension; no FK (B4); whether the department belongs to the fact's entity is the producer's check (spec 4.2) |
| `created_at`, `updated_at` | timestamptz NOT NULL | audit |

Constraints:

- `uk_posting_fact_lines_fact_id_line_number` on `(fact_id, line_number)`; leads with `fact_id`, so it also indexes the foreign key (G-DDL2).
- Trigger `tg_posting_fact_lines_append_only` (and `tg_posting_fact_lines_truncate`): insert only, no update, no delete.
- **Balance (INV-93).** A deferred constraint trigger `tg_posting_fact_lines_balance` (`CONSTRAINT TRIGGER … DEFERRABLE INITIALLY DEFERRED`, after insert) checks, for each fact touched in the transaction, at commit: at least one debit and one credit line; Σ debit = Σ credit; one `line_currency` for all lines of the fact (spec 4.2: one currency per candidate). No single-row CHECK can state it.
- The same check also runs from a deferred constraint trigger `tg_posting_facts_balance` on `posting_facts` (after insert, initially deferred, one shared function), so a fact header inserted with no lines is refused at commit. Test for the dev card: insert a fact without lines, commit, expect refusal.
- **A fact's lines are written in the fact's own transaction.** A line insert whose fact was committed by an earlier transaction is refused (otherwise a balanced extra pair could be appended to an existing fact and the append-only rule would be hollow). The rule is signed; the mechanism (for example comparing the parent row's `xmin` with the current transaction; that breaks under savepoints, where the row carries the subtransaction's id, and `xmin` is 32 bit, so the dev card tests a fact and its lines inserted across a savepoint) is the dev card's, and its integration test inserts a line in a second transaction and expects the refusal.
- **Component registered (S-R3).** `tg_posting_fact_lines_component` (before insert): the line's `component_key` exists in `posting_components` for the parent fact's `fact_type` and the row is active. Cross-table, so a trigger, not a CHECK; the pipeline also checks it first so the caller gets the named outcome `UNKNOWN_COMPONENT` instead of a database error. A component deactivated later does not touch old lines.
- Index: the unique key above.

### posting_components

| Column | Type | Rule |
|---|---|---|
| `id` | uuid | pk |
| `fact_type` | text NOT NULL | same CHECK as `posting_facts.fact_type` (`ck_posting_components_fact_type`) |
| `component_key` | text NOT NULL | same format CHECK as the lines |
| `side_hint_kind` | text null | 为空表示 either side is allowed; `DEBIT`, `CREDIT` (`ck_posting_components_side_hint_kind`). The sketch's `side_hint` is renamed to carry a class word |
| `is_active` | boolean NOT NULL DEFAULT true | a retired key stays so old lines keep a meaning |
| `created_at`, `updated_at` | timestamptz NOT NULL | audit |

Constraint: `uk_posting_components_fact_type_component_key`. **The migration inserts no row**; rows come with the producer's slice (payroll, #1373) in a migration, so the registry entry is `MIGRATION_ONLY` as for `platform_audit.audit_subject_kinds`. If the user later wants finance to maintain components online, that is a new design with a role and a command, not a column change.

## Differences from the section 8 sketch

1. Renames for class words: `post_seq` to `post_number`, `source_bc` to `source_context_code`, `line_no` to `line_number`, `side` to `side_kind`, `amount`/`currency` to `line_amount`/`line_currency`, `side_hint` to `side_hint_kind`, `*_fiscal_year` to `*_fiscal_year_number`; period columns are `integer` (`DdlNamingTest`: `_number` accepts text, integer, bigint).
2. Added: `updated_at` on the three tables (naming test), the `is_` flag stays `is_active`, the single-currency rule in the balance trigger, the same-transaction rule for lines, the component trigger (S-R3 in the database), `fact_type` and `side` CHECKs, range CHECKs, three indexes.
3. `posting_rejections` and `posting_rejection_dispositions` are out of this record (F2).

None of these changes a command or an event. The event `BusinessFactAttributed` (ids only, no amounts) is written in `docs/architecture/events/` by the implementing slice, as in the PY0 record (a catalogue entry of a code-tier event without a publisher fails `EventCatalogueTest`).

## Invariants to constraints (B5, O4)

| Invariant | Carrier | Reason when no constraint |
|---|---|---|
| A fact balances: Σ debit = Σ credit, one currency, at least one line each side (INV-93), including a fact with no lines | `tg_posting_fact_lines_balance` and `tg_posting_facts_balance` (both deferred, one function) | |
| One fact per entity, producer and idempotency key (INV-94) | `uk_posting_facts_replay_key` | |
| Facts and lines are append only (INV-95) | the two append-only triggers, two truncate triggers | |
| One reversal per fact; not its own reversal | `uk_posting_facts_reverses_fact_id`, `ck_posting_facts_reverses_not_self` | |
| `PostSeq` increases | `uk_posting_facts_post_number` and the sequence | gaps are allowed by design (a rolled-back transaction burns a value) |
| Original period pair is both or none | `ck_posting_facts_original_pair` | |
| A line's component is registered for the fact type (S-R3) | `tg_posting_fact_lines_component` | |
| Lines only in the fact's own transaction | trigger (mechanism: dev card) | |
| A reversing fact copies the original's lines with opposite sides | | cross-row semantics; `ReverseAndRepost` builds it, covered by the done-when tests; a wrong copy still balances, so only a test finds it |
| The reversed fact is of the same entity | | cross-row; the command (a composite foreign key would need an extra unique key; not worth it for a command-only path) |
| The posting period is the one the gate returned (INV-92) | | other context's state; the pipeline calls `PeriodGate` with a shared lock in the same transaction |
| The entity exists; the department belongs to the entity | | other contexts; pipeline and producer |

## Business key, class, time (O3, O6)

Business statements (O3): one fact exists per legal entity, producer and idempotency key (INV-94); one line per fact and line number; one component per fact type and key.

| Table | Business key | D17 class | Versioning (D17 rule 2) |
|---|---|---|---|
| `posting_facts` | (`legal_entity_id`, `source_context_code`, `idempotency_key`); order key `post_number` | fact | D17 versions master data, reference and configuration; a fact is append only and a correction is a reversing fact, so there is nothing to version |
| `posting_fact_lines` | (`fact_id`, `line_number`) | fact (child) | as above |
| `posting_components` | (`fact_type`, `component_key`) | configuration | **not needed**, reason "maintained only by migrations (git is the version)" (one of the four reasons of D17 rule 2); a retired key is deactivated by migration, never deleted, and a line records the component key it used |

## Query paths (O10)

| Path | Filter | Sort | Expected rows | Page | Index |
|---|---|---|---|---|---|
| Replay check | entity, producer, key | none | 0 or 1 | none | the idempotency unique key |
| A producer object's fact | `source_context_code`, `source_id` | `post_number` | 1 to 2 | none | `ix_posting_facts_source` |
| Facts of a period (finance list, reconcile) | entity, posted year, posted period | `post_number` | tens per month in the HR batch | cursor | `ix_posting_facts_legal_entity_id_posted_period` |
| Facts after a point, per entity (closing batch) | entity, `post_number >` x | `post_number` | tens | cursor | `ix_posting_facts_legal_entity_id_post_number` |
| Lines of a fact | `fact_id` | `line_number` | tens | none | `uk_posting_fact_lines_fact_id_line_number` |
| Component check | `fact_type`, `component_key` | none | 1 | none | `uk_posting_components_fact_type_component_key` |

Volume in the HR batch: one fact per entity and payroll month, about five entities: hundreds of facts and thousands of lines per year. Vouchers later multiply this; no partition now (the spec's grain is one fact per entity and month), revisited with the voucher slice if the grain changes.

## Cross-cutting, ADR clauses, value objects (O13 to O16)

- Legal entity column: on `posting_facts` (O13); lines inherit through the fact; components are configuration without one. Money: `numeric(19,2)` CNY with the paired currency column (payroll spec C-4); time zone: dates and `timestamptz` audit only. `jsonb`: none. Free text: none (the digest-only rule for payloads belongs to F2).
- Data scope, fail-closed: reads of facts are filtered by the caller's entity scope; lines are read through the fact and inherit its scope (spec 5); components by permission only.
- Analytical facts (A1 to A8): none in the spine. The events carry ids only, so no pay amount leaves the schema through an event.
- ADR clauses: ADR-0016 decisions 1 to 3 (single pipeline, original period, account-free facts), ADR-0045, ADR-0046 decision 8.3, ADR-0087 decision 3. ADR-0016 decision 2 says append-only is trigger plus privilege revocation: the triggers are signed here; the revocation is the role set-up (the dev card names where, **unverified**).

## Registry drafts and classification (C1 to C5)

| Table | Registry |
|---|---|
| `posting_facts` | role `AGGREGATE_ROOT`, aggregate `PostingFact`, business key `[legal_entity_id, source_context_code, idempotency_key]`, mutability `APPEND_ONLY`, retention `PERMANENT`, lineage `NEW`, writers `operations.facts` |
| `posting_fact_lines` | role `AGGREGATE_CHILD`, aggregate `PostingFact`, mutability `APPEND_ONLY`, retention `PERMANENT`, lineage `NEW`, writers `operations.facts` |
| `posting_components` | role `CONFIG`, mutability `MIGRATION_ONLY`, retention `PERMANENT`, lineage `NEW`, readers `operations.facts` |

The sequence `posting_fact_post_seq` belongs to `posting_facts`: the registry has no sequence entry form today (grep found none), so the dev card names it in the table's comment line in the registry. **Unverified** whether any guard needs more; the dev card finds out with `DataArchitectureTest`.

**Retention.** `PERMANENT`: spec Q-8, answered by the user 2026-11-05 (accounting records; statutory minimum 30 years under `会计档案管理办法`, ADR-0022 decision 9). The tables hold no personal identifier (no employee id on facts, spec C-2), so C3's ban on permanent retention for `PI` tables does not apply. **Caveat to carry:** a department with one person turns a department-level pay line into one person's pay. The access level below already covers pay as `SENSITIVE`; whether such a line is personal information (PI) is a legal call that waits with the payroll PI/SPI question (payroll spec P-9 note), so the mark stays `NONE` until legal answers and the dev card does not widen it.

**Classification** (Q-8 and Q-12 (a): the user's word `CONFIDENTIAL` is register level `SENSITIVE`, L3). Register lines to add (format `<schema.table.column> | level | mark | reason`):

| Column | Access level | PI | Reason |
|---|---|---|---|
| `operations_facts.posting_fact_lines.line_amount` | `SENSITIVE` | `NONE` | 用途：payroll fact amounts per entity, department and component (user word CONFIDENTIAL, Q-8, Q-12 a); never exposed raw in web/api output or event payloads |
| `operations_facts.posting_fact_lines.component_key` | `SENSITIVE` | `NONE` | which pay component the amount belongs to, same sensitivity as the amount (Q-8: "fact amounts and `component_key` of payroll facts") |
| all other columns of the three tables | `INTERNAL` (the unregistered default) | `NONE` | entity ids, dates, keys, periods, structure |

`line_currency` stays `INTERNAL` (a constant `CNY`). A later non-payroll fact type with non-sensitive amounts would split the register by fact type or lower nothing silently: it needs its own record.

## Comments (C1)

Table comments: `posting_facts` 「过账事实：粒度：每个法人 × 业务来源 × 幂等键一行（一次成功过账或冲销），只增不改不删」; `posting_fact_lines` 「过账事实行：粒度：每个事实 × 行号一行，与事实同事务写入，只增不改不删」; `posting_components` 「事实组成项注册表：粒度：每个事实类型 × 组成项键一行，只由迁移写入」. Every column has a comment; each nullable column states 「为空表示」 as in the tables above.

## Baseline, target, transition, rollback (T1 to T4)

Baseline: no `operations_facts` schema; no legacy table of these names (`LegacyShapeTest`). Target: the three tables, the sequence, the triggers and indexes above. Transition: none (no data; the legacy system's vouchers are not imported). Rollback: drop the schema while empty (nothing references it until the payroll slice PAY). Expected diff for the schema PR: `operations_facts.posting_facts`, `posting_fact_lines`, `posting_components`, sequence `posting_fact_post_seq`, their CHECKs, unique keys, foreign keys, indexes, the append-only and truncate triggers with functions, the two deferred balance triggers, the component trigger and the same-transaction rule, comments, registry entries, the two classification lines. No `btree_gist`.

## Events and read models (E1 to E3)

`BusinessFactAttributed` and `BusinessFactRestated` are raised by the pipeline (spec 4.2; payload ids only: fact id, `post_number`, entity, fact type, source, keys, posted and original period; no amount). Their payloads are written in `docs/architecture/events/` with the implementing slice. No read model, no migration mapping.

## Open items for the dev card (not blocking the signature)

- B8: glossary terms `posting_fact` and `account_mapping_version` are proposed (`backend/glossary/proposed/accounting-spine.yaml`); the dev card runs `GlossaryTest` over the new column names.
- The mechanism of the same-transaction rule and the sequence's registry line (above).
- Module edges (spec section 6) arrive with the first code.

## Result

数据设计审查
- 审查：one-design，2026-11-05，依据 origin/main `a22fdd7f4`
- 判定：B1、B2、B3、B4（跨 BC 无外键）、B5、B6、B7（枚举最小集，扩展为无损加宽）、O1、O2（UUIDv7 主键）、O3、O4、O5、O6、O7（只增不改）、O8（金额与币种成对）、O9、O10、O10a、O11（量级小，无需分区，已写明复查时点）、O12、O13、O14、O15、O16、C1 至 C5、T1 至 T4、E1 通过，证据见上文各节；A1 至 A8 不适用（事件只带 id，无分析事实）；B8（术语表）按请假 L1a 先例记为实现卡前置
- 豁免与待用户决定：保留期 `PERMANENT` 与分级（`line_amount`、`component_key` 为 SENSITIVE，即用户所说 CONFIDENTIAL）按 spec Q-8 与 Q-12 (a)，用户已答；一人部门的部门级工资行是否个人信息，待法务（随薪酬 P-9 的 PI/SPI 一并），标记暂为 NONE
- 范围：仅 `posting_facts`、`posting_fact_lines`、`posting_components`（结构）；`posting_components` 的行、`posting_rejections`、`posting_rejection_dispositions` 不在本记录
- 结论：通过（仅上述三张表的结构）
- 数据设计：通过（one-design，2026-11-05，依据 a22fdd7f4）
