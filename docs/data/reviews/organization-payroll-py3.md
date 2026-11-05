# Data design review: organization.payroll slice PY3 (bonus input manual port, one table)

Card: PY3 of `docs/architecture/proposals/organization-payroll.md` section 10 (design card #1403). Basis: that spec sections 4.5, 5, 7, 8, 9 (P-9 answered) and done-when 11, ADR-0087 decision 5.3, ADR-0043 R-2, gate `docs/data/data-design-gate.md` appendix A, at origin/main `793c4dd02`. Signed by one-design (same form as `organization-payroll-py0.md`). The schema `organization_payroll` is created by PY0's migration; PY3 adds its table to it.

## Scope

One table: `organization_payroll.bonus_inputs`. Not signed here: the other per-person payroll tables (profiles, enrolments, settlements and lines, disbursements), each in its own record now that P-9 is answered; `incentive_plan_versions` (not in the spec).

## What it is

The manual, approved port of ADR-0087 decision 5.3 / spec 4.5: until performance exists a bonus enters payroll as one approved input per relationship, month and reason. A row is written when the approval (`platform.approval`) of that input completes, so **a row is always an approved input**; there is no pending state. `DetermineBonus` references it; the settlement calculation reads it. When performance (AGG-106) is built the same port reads that source and this table does not change (a new `source_kind` value widens the CHECK).

## Table to object source (B1, B2)

| Table | Source | Aggregate | Registry role |
|---|---|---|---|
| `bonus_inputs` | AGG-76 bonus determination input (spec 4.5) | BonusDetermination | `AGGREGATE_ROOT`, `CONDITIONAL_UPDATE` (void only) |

Keys: UUIDv7 `id`, primary key `pk_bonus_inputs`. Audit columns `created_at`, `updated_at`; no `version` (one legal update, the void, guarded by a trigger). Cross-BC ids (`employment_relationship_id`, `paying_legal_entity_id`, `entered_by_user_id`, `voided_by_user_id`, `approval_instance_id`): `uuid`, no foreign key (B4).

## Columns and constraints

| Column | Type | Rule |
|---|---|---|
| `id` | uuid | pk |
| `employment_relationship_id` | uuid NOT NULL | the relationship the bonus is paid for [R] (ADR-0086: per relationship); no foreign key |
| `paying_legal_entity_id` | uuid NOT NULL | the entity whose settlement carries it (the entity scope of every read, spec section 5) |
| `payroll_month_start_date` | date NOT NULL | `ck_bonus_inputs_payroll_month_start_date` `EXTRACT(DAY FROM payroll_month_start_date) = 1` |
| `bonus_amount` | numeric(19,2) NOT NULL | `ck_bonus_inputs_bonus_amount` `> 0` (a clawback is an adjustment line of a later settlement, C-8, not a negative input) |
| `bonus_currency` | text NOT NULL | `ck_bonus_inputs_bonus_currency` `= 'CNY'` (C-4) |
| `source_kind` | text NOT NULL | `ck_bonus_inputs_source_kind` `IN ('MANUAL_APPROVED')`; widening to a performance source is a lossless CHECK change |
| `reason_key` | text NOT NULL | key from the selection list (data, not a CHECK); format `~ '^[A-Z][A-Z0-9_]{1,39}$'` |
| `approval_instance_id` | uuid NOT NULL | the approval of this input (the "approval number" is mandatory); `uk_bonus_inputs_approval_instance_id` unique, so a replayed `ApprovalCompleted` finds one row |
| `entered_by_user_id` | uuid NOT NULL | who entered the input (separation of duties: the settlement approver cannot be this user, done-when 11) |
| `status` | text NOT NULL | `ACTIVE`, `VOIDED` |
| `voided_by_user_id` | uuid null | 为空表示 not voided |
| `voided_at` | timestamptz null | 为空表示 not voided |
| `created_at`, `updated_at` | | audit |

Constraints:

- `ck_bonus_inputs_void_columns` `(status = 'VOIDED') = (voided_at IS NOT NULL) AND (voided_at IS NULL) = (voided_by_user_id IS NULL)`.
- `uk_bonus_inputs_relationship_month_reason` partial unique `(employment_relationship_id, payroll_month_start_date, reason_key) WHERE status = 'ACTIVE'` (a voided input frees the key for a corrected one). **Difference from the sketch:** the sketch says one row per relationship and month; this key allows several reasons in the same month (for example a year-end and a project bonus) and refuses the same reason twice. The settlement sums the active rows of a month.
- Trigger `tg_bonus_inputs_change` (and `tg_bonus_inputs_truncate`): insert only as `ACTIVE`; the only update is `ACTIVE` to `VOIDED` setting `status`, `voided_by_user_id`, `voided_at`, `updated_at` and nothing else; no delete (the purge function of P-9 is the only delete path, as the owner role, like W2's `purge_employee_history`). No other write.
- Index `ix_bonus_inputs_entity_month` `(paying_legal_entity_id, payroll_month_start_date)`; the unique key above leads with `employment_relationship_id`.

## Differences from the section 8 sketch

1. `amount` becomes `bonus_amount` plus `bonus_currency` (naming standard 2.4: every `_amount` pairs with a `_currency`); `reason_key`, `source_kind`, `approval_instance_id` as sketched.
2. Added `entered_by_user_id` (the SoD rule of done-when 11 needs the enterer), `status` with the void pair, and the key above.

None of these changes a command or an event.

## Invariants to constraints (B5, O4)

| Invariant | Carrier | Reason when no constraint |
|---|---|---|
| One approved input per approval | `uk_bonus_inputs_approval_instance_id` | |
| The same reason is not entered twice for a relationship and month | `uk_bonus_inputs_relationship_month_reason` | |
| Month column holds the first day | `ck_bonus_inputs_payroll_month_start_date` | |
| Amount positive, CNY | `ck_bonus_inputs_bonus_amount`, `ck_bonus_inputs_bonus_currency` | |
| A row is never edited, only voided | `tg_bonus_inputs_change` | |
| Settlement approver is not the input's enterer (done-when 11) | | cross-aggregate; checked by `ApproveMonthlyPayroll` against `entered_by_user_id` |
| An input is voided only while no `APPROVED` settlement of its entity and month exists (P-R3: approved amounts never change) | | cross-aggregate; checked by the void command |
| The relationship is in service and `paying_legal_entity_id` is its paying entity in that month | | another context's data; checked by the entry command |
| Input rows carry no event payload with the amount (P-R5) | | event schema contract test, not a table rule |

## Business key, class, time (O3, O6)

Business key: (`employment_relationship_id`, `payroll_month_start_date`, `reason_key`) among active rows. Class: business record, declared (an approved input is a fact). Time: a month, not an interval; no versioning (correction = void and enter again, which the approval trail records).

## Query paths (O10)

| Path | Filter | Sort | Expected rows | Page | Index |
|---|---|---|---|---|---|
| Settlement calculation: inputs of an entity and month | `paying_legal_entity_id`, `payroll_month_start_date`, `status = 'ACTIVE'` | none | tens to hundreds | none | `ix_bonus_inputs_entity_month` |
| A person's inputs (payroll view) | `employment_relationship_id`, month range | `payroll_month_start_date` desc, `id` | a few per year | cursor | `uk_bonus_inputs_relationship_month_reason` (leading column) |
| Replay lookup | `approval_instance_id` | none | 1 | none | `uk_bonus_inputs_approval_instance_id` |

Thousands of rows a year; no partition, no hot-state partial index (almost all rows are `ACTIVE`).

## Cross-cutting, ADR clauses, value objects (O13, O14, O15, O16)

- Money `numeric(19,2)` CNY (C-4); dates only; idempotency by `approval_instance_id`; legal entity id present (the paying entity, O13); no analytical fact (A1 to A8: none; the totals event feeds reporting later).
- ADR-0043 R-2: the table holds personal information, so its retention is not `PERMANENT`; the user's answer fixes the period (below). Free text: none (a key, not text). `jsonb`: none.

## Registry drafts and classification (C1 to C5)

Registry: role `AGGREGATE_ROOT`, aggregate `BonusDetermination`, business key `[employment_relationship_id, payroll_month_start_date, reason_key]`, mutability `CONDITIONAL_UPDATE` with condition "`ACTIVE` to `VOIDED` once, setting the void pair (`tg_bonus_inputs_change`); no delete except the purge function", **retention: ten years after the person's last separation, removed by the purge route** (user 2026-11-05 「P-9 a」, lead fragment `2026-11-05-154025-one-lead-P-9-a`), lineage `NEW`, writers `organization.payroll`. Until the purge function covers payroll tables the registry line uses the same marker as the other per-person tables (`{policy: PERMANENT}` with the comment that the purge extension is owed, `data-access-registry.yaml` line 78 pattern); **owed**: extend the employee-history purge to `bonus_inputs` by `employment_relationship_id` (a later purge card; the PY3 implementation card must not claim the retention is enforced before then).

Classification (`data-classification.txt`, access level `CONFIDENTIAL` per the user's answer):

| Column | Level | PI | Reason |
|---|---|---|---|
| `bonus_amount` | `CONFIDENTIAL` | `PI` | 用途：one person's bonus, the pay figure of a relationship |
| `reason_key` | `CONFIDENTIAL` | `PI` | 用途：why a person received a bonus, tied to one relationship |
| `bonus_currency`, `source_kind`, `payroll_month_start_date`, `status` | `CONFIDENTIAL` | `NONE` | 用途：qualify the pay figure; not identifying alone |
| `entered_by_user_id`, `voided_by_user_id`, `voided_at` | `CONFIDENTIAL` | `NONE` | 用途：separation-of-duties and audit identities of staff acting in their role |

Surrogate and reference ids (`id`, `employment_relationship_id`, `paying_legal_entity_id`, `approval_instance_id`) are unregistered (default `INTERNAL`); reads are still restricted to payroll roles and the person themselves by the scope in spec section 5, not by the column level. Bonus amounts are `PI`, not `SPI`: legal's answer on pay as SPI (ADR-0043 U-5, U-11) is not on record here; if legal says SPI, `bonus_amount` changes to `SPI` in one line.

## Comments (C1)

Table: 「粒度：每个任职关系 × 薪酬月份 × 奖金原因一行（有效行），一行就是一条已审批的奖金输入，只能作废不能改」. Every column has a comment; nullable columns state 「为空表示」 as above.

## Baseline, target, transition, rollback (T1 to T4)

New table, no legacy table of this name, no data at creation (inputs come from the approval flow; legacy history comes with the PY10 import slice, its own mapping). Rollback: drop the table (nothing references it until PY4). Expected diff for the schema PR: `organization_payroll.bonus_inputs`, its constraints, the two triggers and functions, the index, and the classification lines.

## Events and read models (E1 to E3)

`BonusDetermined` (analytical): ids only, no amount or reason (P-R5); written in `docs/architecture/events/` with the implementing slice (catalogue rule for code-tier events). No read model, no migration mapping.

## Result

数据设计审查
- 审查：one-design，2026-11-05，依据 origin/main `793c4dd02`
- 判定：B1、B2、B3a、B4、B5、O3、O4、O6、O10、O11（千行级，无需分区）、O13、O14、O15、C1 至 C5、T1 至 T4、E1 通过；A1 至 A8 不适用（无分析事实）
- 豁免与待用户决定：保留期与分级已由用户答复（P-9 a）；`PI` 还是 `SPI` 待法务（ADR-0043 U-5、U-11），现按 `PI`，改标记为一行改动；清除路径扩到薪酬表是后续清除卡的欠项
- 结论：通过（仅 `bonus_inputs`）
- 数据设计：通过（one-design，2026-11-05，依据 793c4dd02）
