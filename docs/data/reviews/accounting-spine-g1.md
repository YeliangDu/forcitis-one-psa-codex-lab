# Data design review: accounting spine slice G1 (books, one table)

Card: #1434 (design; Refs #1402). Slice G1 of `docs/architecture/proposals/accounting-spine.md` section 10 (dev card not opened yet). Basis: that spec sections 3 C-7, 4.3, 5, 8, 9 (Q-8 answered) at origin/main `a22fdd7f4`; ADR-0022 decision 2 (two books), ADR-0087 decisions 2 and 3; gate `docs/data/data-design-gate.md` Appendix A. Signed by one-design (same form as `organization-payroll-py0.md`).

## Scope

Schema `accounting_ledger` (this migration creates it), one table: `ledgers`. The migration inserts no rows: books are created by a handler of `LegalEntityRegistered` and by a backfill for entities that already exist (both application code through the workforce api; **not SQL**, because a migration reading `organization_workforce.legal_entities` would cross a context boundary).

Not signed here, and not to be built by the G1 card:

- `chart_versions`, `chart_accounts`, `mapping_versions`, `account_mappings` (slices G2 and G3): later records. `chart_accounts` and `account_mappings` **rows** wait for #1373 in any case.
- Account or mapping rows (#1373); dimensions other than `department` (spec C-4).
- Any `accounting_period` or `operations_facts` table (P1 and F1 records).

## Table to object source (B1, B2)

| Table | Source | Aggregate | Registry role |
|---|---|---|---|
| `ledgers` | AGG-83 Ledger (账簿; glossary term `ledger`, table `ledgers`, "不计，参照数据") | Ledger, one row = one aggregate | `AGGREGATE_ROOT`, `APPEND_ONLY` |

Names follow `naming-standard.md` 2.4/2.4a. Key: UUIDv7 `id`, primary key `pk_ledgers`. `legal_entity_id` belongs to `organization.workforce`: `uuid`, no foreign key (B4, ADR-0086).

## Columns and constraints

| Column | Type | Rule |
|---|---|---|
| `id` | uuid | pk |
| `legal_entity_id` | uuid NOT NULL | the owning entity; no FK (B4) |
| `basis_kind` | text NOT NULL | `ck_ledgers_basis_kind`: `ACCRUAL` (权责), `CASH` (收付) — the two books of ADR-0022 decision 2 |
| `created_at`, `updated_at` | timestamptz NOT NULL | audit (`updated_at` is required on every table by `DdlNamingTest`; it never differs from `created_at` here) |

The sketch has no `version` column and none is added: the row has no update path, so there is nothing for optimistic locking to guard.

Constraints:

- `uk_ledgers_legal_entity_id_basis_kind` on `(legal_entity_id, basis_kind)`; leads with `legal_entity_id`, so it is also the index of every entity-scoped read.
- Trigger `tg_ledgers_append_only` (and `tg_ledgers_truncate`): insert only; every update and every delete is refused. A book is created once and never changed or removed (it is referenced by chart and mapping versions from G2 on).

## Differences from the section 8 sketch

1. Added `updated_at` (naming test) and the explicit append-only trigger (the sketch said "reference data").
2. Constraint names follow `uk_<table>_<columns>`.

None of these changes a command or an event. There is no command; no event is raised by G1 (`ChartVersionPublished`/`MappingVersionPublished` belong to G2/G3).

## Invariants to constraints (B5, O4)

| Invariant | Carrier | Reason when no constraint |
|---|---|---|
| At most one book per entity and basis | `uk_ledgers_legal_entity_id_basis_kind` | |
| Basis is one of two | `ck_ledgers_basis_kind` | |
| A book never changes or goes away | `tg_ledgers_append_only`, `tg_ledgers_truncate` | |
| A legal entity has exactly one `ACCRUAL` and one `CASH` book (S-R2) | | "exactly" is cross-row and cross-context (an entity without books has no row to check); the handler creates both in one transaction, and the backfill is a reconcile that creates the missing one; a registered entity without books is "a defect found by a check, not by a user action" (spec 4.3), so the dev card carries that check as a test |
| The entity exists | | cross-BC; the handler runs on its event, the backfill reads the workforce api |

## Business key, class, time (O3, O6)

Business statement (O3): a legal entity has at most one book per accounting basis (ADR-0022 decision 2; spec S-R2 says exactly one of each).

| Table | Business key | D17 class | Versioning (D17 rule 2) |
|---|---|---|---|
| `ledgers` | (`legal_entity_id`, `basis_kind`) | master data (a business entity referenced by id from chart and mapping versions and, later, vouchers) | **versioned through its children, not by an exemption**: D17 rule 5 says a root that owns data which takes effect over time holds no versionable column and reads take the version valid on the business date. `ledgers` is that root: identity only (entity, basis), no attribute that can change; everything that changes over time is a version row of a child table (`chart_versions` in G2, `mapping_versions` in G3, with effective periods and approval). Precedent: `organization_attendance.work_calendar_years` (root without versionable columns, versions elsewhere). No new exemption entry is registered, so D17 rule 2's "new exemption cites an intent or an ADR" does not arise |

## Query paths (O10)

| Path | Filter | Sort | Expected rows | Page | Index |
|---|---|---|---|---|---|
| Books of an entity | `legal_entity_id` | `basis_kind` | 2 | none | `uk_ledgers_legal_entity_id_basis_kind` |
| Existence check (the defect check; the backfill) | all rows | none | about 10 (five entities) | none | table scan; revisit if the entity count grows beyond hundreds |

No partition, no `fillfactor`.

## Cross-cutting, ADR clauses, value objects (O13 to O16)

- Legal entity column: present (a book belongs to one entity, O13). No money, no time zone, no `jsonb`, no free text. No analytical fact (A1 to A8: none). Data scope, fail-closed: reads are filtered by the caller's entity scope (spec 5); chart and mapping versions inherit the book's entity from G2.
- ADR clauses: ADR-0022 decision 2, ADR-0087 decisions 2 and 3. The creation handler is a consumer of an existing event (`events/workforce.yaml`); no new event payload.

## Registry drafts and classification (C1 to C5)

| Table | Registry |
|---|---|
| `ledgers` | role `AGGREGATE_ROOT`, aggregate `Ledger`, business key `[legal_entity_id, basis_kind]`, mutability `APPEND_ONLY`, retention `PERMANENT`, lineage `NEW`, writers `accounting.ledger` |

`CONFIG` with `MIGRATION_ONLY` (used by `platform_audit.audit_subject_kinds`) does not fit: those rows are inserted by migrations only, and these rows are inserted by a handler at run time.

**Retention.** `PERMANENT`: the spec's Q-8, answered by the user 2026-11-05 (accounting records, statutory minimum 30 years, ADR-0022 decision 9). No personal information.

| Column | Access level | PI | Reason |
|---|---|---|---|
| all columns | `INTERNAL` (the unregistered default) | `NONE` | 用途：which books a legal entity keeps; no personal data, no amounts. `CONFIDENTIAL` (user word) = level `SENSITIVE` (Q-12 a) applies to payroll fact amounts in F1, not here |

## Comments (C1)

Table comment: 「账簿：粒度：每个法人 × 记账基础（权责或收付）一行，创建后不可改、不删」. Every column has a comment; there is no nullable column.

## Baseline, target, transition, rollback (T1 to T4)

Baseline: no `accounting_ledger` schema; the legacy system's books are not imported (names are the new system's own, `LegacyShapeTest`). Target: the table above. Transition: none in SQL; books for existing entities come from the backfill in code after the migration (idempotent: it creates only what is missing). Rollback: drop the table and the schema while empty (nothing references it until G2). Expected diff for the schema PR: `accounting_ledger.ledgers`, its CHECK, unique key, the append-only trigger and the truncate trigger with their functions, comments; registry entry. No `btree_gist`.

## Events and read models (E1 to E3)

No event published, no read model, no migration mapping. G1 subscribes to `LegalEntityRegistered` (exists, `events/workforce.yaml`); the module edge `accounting.ledger -> organization.workforce` is added by the dev card with the first code (spec section 6).

## Open items for the dev card (not blocking the signature)

- B8: glossary terms `ledger` and `accounting_ledger`-related names are registered; the dev card runs `GlossaryTest` on `ledgers` and `basis_kind`.
- Spec Open point S3 (an accrual-only account has no cash code) concerns `account_mappings` and is decided with G3, not here.

## Result

数据设计审查
- 审查：one-design，2026-11-05，依据 origin/main `a22fdd7f4`
- 判定：B1、B2、B3、B4（跨 BC 无外键）、B5、B6、B7、O1、O2（UUIDv7 主键）、O3、O4、O5、O6、O7（创建后不可改）、O9、O10、O10a、O11、O12、O13、O14、O15、O16、C1 至 C5、T1 至 T4、E1 通过，证据见上文各节；A1 至 A8 不适用（无分析事实）；B8（术语表）按请假 L1a 先例记为实现卡前置
- 豁免与待用户决定：D17：账簿表是只含身份列的根，随时间变化的内容在 G2/G3 的版本子表（D17 细则 5，先例 `work_calendar_years`），不登记新豁免；保留期 `PERMANENT` 与分级按 spec Q-8（用户 2026-11-05 已答：采纳建议）
- 范围：仅 `ledgers`；`chart_versions`、`chart_accounts`、`mapping_versions`、`account_mappings` 属 G2/G3，不在本记录
- 结论：通过（仅 `ledgers`）
- 数据设计：通过（one-design，2026-11-05，依据 a22fdd7f4）
