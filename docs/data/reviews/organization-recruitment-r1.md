# Data design review: organization.recruitment slice R1 (one table)

Card: #1449 (sign-off; implementation card #1450, one-dev-org). Basis: `docs/architecture/proposals/organization-recruitment.md` sections 4.1, 8, 9, 10 at origin/main `ab1e6dc2a` (PR #1442 merged), gate `docs/data/data-design-gate.md` appendix A. Signed by one-design (same form as the leave L1a and payroll PY0 records).

## Scope

Schema `organization_recruitment`, one table: `recruitment_requisitions`. Not signed here and not to be built by #1450: applicants, applications, interviews, offers (the next spec; applicant retention is a separate user item, ADR-0085 decision 5).

**Carved out of this signature (not passed, not built):** any salary range or budget column. Spec B-3 asks whether a requisition carries one; the answer was to follow legacy practice once one-platform's read of the A8 form's field names is in, and that read is **not in yet** (verified: no comment on #1449, no record under `docs/`). Gate 1.3 allows no conditional pass, so the table is signed **without** such a column and without waiting. If the read shows a salary field the user wants kept, that column is `SENSITIVE` (spec section 8; Q-12) and the table gets a new sign-off; nothing in this signature pre-approves it. B-5 (approver when the org unit has no head) changes no column: the approver is resolved at command time and the table stores only `approval_instance_id`, so it is not a gate item here; it stays open for R2.

## Table to object source (B1, B2)

| Table | Source | Aggregate | Registry role |
|---|---|---|---|
| `recruitment_requisitions` | AGG-65 (spec 4.1) | RecruitmentRequisition | `AGGREGATE_ROOT`, `CONDITIONAL_UPDATE` |

## Columns and constraints

Every enum is `text` with a named `ck_<table>_<column>` CHECK; free text has `char_length` and `btrim` CHECKs; every nullable column's comment states 「为空表示」. Keys are UUIDv7 `id` (client-generated, so a replayed submit is idempotent), primary key `pk_recruitment_requisitions`. Other contexts' ids (`position_id`, `org_unit_id`, `legal_entity_id`, `requested_by_employee_id`, `approval_instance_id`) are `uuid`, no foreign key (cross-BC rule B4, ADR-0086).

| Column | Type | Rule |
|---|---|---|
| `id` | uuid | pk |
| `requisition_number` | text null | 为空表示 still a draft; issued by `platform.numbering` at submit; `ck_recruitment_requisitions_number_set` `(status = 'DRAFT') = (requisition_number IS NULL)`; `uk_recruitment_requisitions_requisition_number` unique where not null; length 1 to 40 |
| `position_id` | uuid NOT NULL | no FK |
| `org_unit_id` | uuid NOT NULL | no FK; scope key of reads |
| `legal_entity_id` | uuid NOT NULL | requester's cost entity on the creation day; no FK. Spec's choice, accepted: no org-unit-to-entity lookup exists, and `ApprovalStartRequest` needs an entity |
| `requested_by_employee_id` | uuid NOT NULL | requester; no FK |
| `requested_count` | integer NOT NULL | `ck_recruitment_requisitions_requested_count` `BETWEEN 1 AND 999` |
| `expected_start_date` | date null | 为空表示 not stated; `>=` creation date is a command check, not a CHECK |
| `description_text` | text null | 为空表示 no description; `char_length` 1 to 500, `btrim` CHECK; the form states it holds no applicant names or personal data |
| `status` | text NOT NULL | `ck_recruitment_requisitions_status`: `DRAFT`, `SUBMITTED`, `APPROVED`, `CLOSED` (spec 4.1; matches the closeout state machine, which the R1 card cross-checks with the Java enum, B3a) |
| `close_reason_kind` | text null | 为空表示 not closed; `ck_recruitment_requisitions_close_reason_kind`: `REJECTED`, `WITHDRAWN`, `CANCELLED`, `FILLED` |
| `approval_instance_id` | uuid null | 为空表示 no approval started; `uk_recruitment_requisitions_approval_instance_id` unique where not null (one requisition per process, so a replayed approval result finds one row) |
| `submitted_at`, `approved_at`, `closed_at` | timestamptz null | 为空表示 not reached |
| `created_at`, `updated_at`, `version` | | audit; `version bigint NOT NULL DEFAULT 0` |

One state-pairing CHECK, `ck_recruitment_requisitions_state_columns` (this is the spec's `closed_columns`, widened to cover the other pairs so the database refuses an impossible row):

- `(status = 'CLOSED') = (close_reason_kind IS NOT NULL AND closed_at IS NOT NULL)`, and `(close_reason_kind IS NULL) = (closed_at IS NULL)`;
- `(status = 'DRAFT') = (submitted_at IS NULL AND approval_instance_id IS NULL)`;
- `approved_at IS NOT NULL` only when `status IN ('APPROVED','CLOSED')`;
- `close_reason_kind IN ('CANCELLED','FILLED')` requires `approved_at IS NOT NULL`; `close_reason_kind IN ('REJECTED','WITHDRAWN')` requires `approved_at IS NULL` (rejection and withdrawal happen only from `SUBMITTED`).

The implementing card may split it into several named CHECKs; the four rules above are the signed content.

Trigger `tg_recruitment_requisitions_change` and `tg_recruitment_requisitions_truncate` (refuses TRUNCATE): the full list of legal writes, everything else refused. (1) Insert as `DRAFT`, or directly as `SUBMITTED` when the client sends the full form (spec 4.1: number and `approval_instance_id` are then set in the same insert, the approval process having been started with the client-generated id first). (2) `DRAFT` to `DRAFT`: descriptive columns (`position_id`, `org_unit_id`, `requested_count`, `expected_start_date`, `description_text`) plus `version`, `updated_at`. (3) Edges `DRAFT`→`SUBMITTED`, `SUBMITTED`→`APPROVED`, `SUBMITTED`→`CLOSED`, `APPROVED`→`CLOSED`: only the state columns of that edge change, never the descriptive ones. (4) A `CLOSED` row never changes. (5) Delete only a `DRAFT`. `legal_entity_id`, `requested_by_employee_id` and `created_at` never change.

Indexes: `ix_recruitment_requisitions_org_unit_id_status` `(org_unit_id, status)`; `ix_recruitment_requisitions_requested_by_employee_id` `(requested_by_employee_id)`. All names are at most 52 bytes (63-byte limit checked).

## Differences from the section 8 sketch

1. `ck_…_closed_columns` is widened to the state-pairing rules above (database-level carrier for B5; the spec had only the close pair).
2. Added `ck_…_number_set` (number null exactly while `DRAFT`) and `uk_…_approval_instance_id` (one process per requisition, spec 4.1 invariant).
3. Added the `TRUNCATE` trigger and the explicit legal-write list; the sketch named only the edges.

None changes a command or an event.

## Invariants to constraints (B5, O4)

| Invariant | Carrier | Reason when no constraint |
|---|---|---|
| Legal state edges; a `CLOSED` row never changes (spec 4.1) | `tg_recruitment_requisitions_change` | |
| Close columns set exactly when closed; rejection and withdrawal only before approval | `ck_recruitment_requisitions_state_columns` | |
| Number null exactly in `DRAFT`, unique when set | `ck_…_number_set`, `uk_…_requisition_number` | |
| One active approval process per requisition | `uk_…_approval_instance_id` | |
| Requester is never the approver | | cross-aggregate, checked at process start by the command (spec 4.1) |
| Position `ACTIVE`, org unit valid at submit (R-01) | | cross-context, command check through the workforce api |
| `expected_start_date` not before creation | | needs the clock, command check |
| Requester or HR closes; requester only withdraws | | permission and command |

## Business key, class, time (O3, O6)

| Table | Business key | Class | Versioning |
|---|---|---|---|
| `recruitment_requisitions` | `requisition_number` (when set) | transaction document (aggregate root) | not master data; state changes are the history, each recorded in `state_transitions` by the command; no effective interval needed (D17 reason: a requisition is a business event, not a reference dimension) |

## Query paths (O10)

| Path | Filter | Sort | Expected rows | Page | Index |
|---|---|---|---|---|---|
| Requisitions in my data scope (R3 list) | `org_unit_id` in scope, optional `status` | `id` desc | tens per unit, hundreds in all | cursor, `id` tiebreak | `ix_…_org_unit_id_status` |
| Mine | `requested_by_employee_id` | `id` desc | single digits | cursor | `ix_…_requested_by_employee_id` |
| To approve (by approval instance) | `approval_instance_id` | none | 1 | none | `uk_…_approval_instance_id` |
| By number | `requisition_number` | none | 1 | none | `uk_…_requisition_number` |

Row count: tens a year (211 legacy instances over about six years); no partition, no `fillfactor`, no hot-state partial index needed.

## Cross-cutting, ADR clauses, value objects (O13, O14, O15, O16)

- Legal entity: carried (`legal_entity_id`). Period: none (no accounting period; the document date is `submitted_at`, null while draft). Document number: `requisition_number` (A6). No money column, no time zone beyond `timestamptz`, no `jsonb`. Free text: only `description_text` (content limited to the position need; no applicant or personal data).
- ADR clauses: ADR-0085 (recruitment is one context; approval is one `platform.approval` node, so the table holds `approval_instance_id`, not approval steps), ADR-0043 (classification and R-2, below), ADR-0066 (document type registered in `platform.numbering` by R1), ADR-0086 and rule B4 (no cross-BC FK). No other ADR column rule applies.
- Value objects: requisition number and the count are checked at construction in the aggregate; the CHECKs above mirror them.

## Registry drafts and classification (C1 to C5)

| Table | Registry |
|---|---|
| `recruitment_requisitions` | role `AGGREGATE_ROOT`, aggregate `RecruitmentRequisition`, business key `[requisition_number]`, mutability `CONDITIONAL_UPDATE` with condition "legal state edges and `DRAFT` edits only (`tg_recruitment_requisitions_change`)", retention `PERMANENT` with the reason below, lineage `NEW`, writers `organization.recruitment`, readers none outside the context (R0's position lookup is a call into workforce, not a read of this table) |

**Retention (C3, the user's item, answered).** User answer "a", 2026-11-05, decision fragment `docs/decisions/2026-11-05-163008-one-lead-B-2-a.md` (PR #1445): `PERMANENT` with a registered ADR-0043 R-2 reason. R-2 bars `PERMANENT` for a table with `PI` columns unless the registry entry carries a testable reason and the decision; this table has `PI` columns, so the entry must carry both. **Reason to register (testable form: the text names the decision fragment file):** 「tens of rows a year; the requester id is staff identity that the audit trail and the employee history keep permanently in any case, so a shorter limit on this table removes nothing from those; the description is limited to the position need. User decision 2026-11-05-163008」. The registry has no reason field yet (the `emergency_admins` entry carries its reason as a comment pending DG-0, `data-access-registry.yaml` line 526); #1450 does the same, and moves the text into the field when it exists. A shorter limit stays the user's to set later; this is not a rule change.

| Column | Access level | PI | Reason |
|---|---|---|---|
| `requested_by_employee_id` | `INTERNAL` | `PI` | 用途：申请人，审批发起、本人查询与审计 |
| `description_text` | `INTERNAL` | `PI` | 用途：岗位需求说明，人工填写的自由文本，可能提到具体人 |
| `requisition_number` | `INTERNAL` | `NONE` | 用途：招聘需求单据编号，展示与检索 |
| `requested_count`, `expected_start_date`, `status`, `close_reason_kind`, `submitted_at`, `approved_at`, `closed_at` | `INTERNAL` | `NONE` | 用途：需求人数、期望到岗日与流程状态，不含个人信息 |
| `position_id`, `org_unit_id`, `legal_entity_id`, `approval_instance_id` | surrogate keys, not registered (default `INTERNAL`) | | |

`description_text` and `requested_by_employee_id` must not appear in event payloads or audit `detail` (ADR-0043 item 4); the two events (spec 4.1) carry ids, a reason kind and timestamps only. No `SENSITIVE`, `RESTRICTED` or `SPI` column; no pay figure.

## Comments (C1)

Table comment: 「招聘需求。粒度：每个组织单元每次用人申请一行；已关闭的行不再变化」. Every column has a comment; each nullable column's states 「为空表示」 as in the table above; the unit of `requested_count` is 人. Terms come from `terms.yaml` (`recruitment_requisition` exists, line 13569); further entries and the `bc` registration (`bcs.yaml`, `NamingSurfaceTest`) are #1450's checks.

## Analytical facts (A1 to A8)

No analytical fact: the table is a transaction document. The two events are `analytical` ids-only events; their payloads belong to R2 (events catalogue file for this context, with the publisher, because a catalogue entry without a publisher fails `EventCatalogueTest`). A6 is met by the legal entity, `submitted_at` and the number.

## Baseline, target, transition, rollback (T1 to T4)

Baseline: no recruitment schema, table or code (ADR-0085, verified 2026-11-04). Target: schema `organization_recruitment` with this table. Transition: migration only, no data; the 211 legacy requisitions are not imported (spec B-4 recommendation (a), not contested; if the user chooses import, it is a separate mapping card). Rollback: drop the schema while empty; after the first write, a new migration. No legacy name appears in the design (`LegacyShapeTest`).

## Expected schema diff (for #1450)

New schema `organization_recruitment`; the table, its constraints, two indexes, one row-change trigger function and trigger, one truncate trigger, the table and column comments, and the `platform.numbering` document type row; nothing else. Names are those above. The schema PR's actual diff is compared with this record column by column.

## Result

数据设计审查
- 审查：one-design，2026-11-05，依据 origin/main `ab1e6dc2a`
- 判定：B1、B2、B3、B3a、B4（外键 RESTRICT 不适用；跨 BC 无外键）、B5、B6、B7（枚举最小集）、O1 至 O16、C1 至 C5、A6、T1 至 T4 通过；A1 至 A5、A7、A8、E2、E3 不适用（无分析事实、读模型、迁入）；E1 事件载荷由 R2 随发布方登记。
- 豁免与待用户决定：保留期 `PERMANENT` 经 R-2 与用户决定登记（决定片段 163008）；工资区间或预算列未纳入本次签字（B-3，老系统读取未到，如出现则新签字）；B-5 不影响表，留给 R2。
- 结论：通过（仅 `recruitment_requisitions`，不含任何工资区间或预算列）
- 数据设计：通过（one-design，2026-11-05，依据 ab1e6dc2a）
