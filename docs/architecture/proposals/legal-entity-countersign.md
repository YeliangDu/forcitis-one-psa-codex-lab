# Legal-entity change with finance countersign — design (#878, ADR-0073 §3)

- Card: #878 (DES-1030-PAR-FC), parent #738. Author: one-design, 2026-10-30. Base: origin/main.
- Basis: ADR-0073 §3 and §4, ADR-0068 §1 and §15, W2 design (`docs/data/w2-employee-position-versioning-design.md`) §2 and §3.6, ADR-0027 item 2.2, ADR-0072 (work calendar), ADR-0022 (the five legal entities).
- Status: proposal; business questions Q1–Q4 **answered by the user 2026-10-30** (relayed by lead): Q1 (a), Q2 (a), Q3 (a), Q4 (a) — section 9. Cards A–G (section 8) are opened by lead.
- Data design: **not signed in this note**. The shapes in section 5 are the proposal for card B; one-design signs them on card B (`docs/data/data-design-gate.md` §1.3 line format). No conditional pass.

## 1. The one thing to decide

How a personnel command that changes an employee's **cost legal entity** waits for, receives, or is refused a finance countersign, and what it stores — so the impl and acceptance cards can be written. Business rules are already decided by the user (ADR-0073:18-30, §5 U1–U4); this note gives shape, not new rules.

Scope: the personnel-command side (`personnel_commands`, `employee_legal_entities`). The org-structure side (`ASSIGN_LEGAL_ENTITY`, merge exemption) uses the same mechanism but waits for the `org_structure_commands` table, which is not built (section 8, card F).

## 2. Facts (verified on origin/main unless marked)

| Fact | Source |
|---|---|
| `personnel_commands` has no `target_legal_entity_id`, no `CHANGE_LEGAL_ENTITY`, no `AWAITING_ATTRIBUTION_COUNTERSIGN` | baseline `V20261014131000__organization_workforce_baseline.sql`:573-616; contract A Q2 |
| `org_structure_commands` does not exist; `personnel_commands.org_structure_command_id` has no FK yet | baseline :585, :647; W2 §9 |
| `employee_legal_entities` has no W2 common set (no command FK, no `superseded_at`) | baseline :292-307 |
| Role 法人财务负责人 `legal_entity_finance_head` is per legal entity: "跨法人事项须另行写明由哪个法人的财务负责人承担" | `docs/glossary/terms.yaml` (id `legal_entity_finance_head`) |
| Permission actions are limited to `read create update delete submit approve reject export import execute` | `backend/glossary/terms.yaml` `standard_actions`; naming G43 |
| The glossary term 会签 `countersign` belongs to `platform.approval` and means "every approver of a node must pass" — a different meaning from ADR-0073's single finance sign-off | `docs/glossary/terms.yaml` (id `countersign`) |
| Cross-entity employee moves in the legacy data: 0 in 24 months; the user ruled "可发生、频率未知" | ADR-0073:62-68 |

## 3. Options and choice

**Where the countersign lives.**

| | A. Columns on the command row (ADR-0073 §4 literal: "加会签列与拒签列") | B. Child table, one row per countersign request (chosen) |
|---|---|---|
| Change countersign | 3 columns + reject reason | one row `CHANGE` |
| Withdrawal of a countersigned pending command, which also needs finance (ADR-0073:72) | a second column set on the same row | one more row `WITHDRAWAL` |
| If Q1 = both legal entities' heads sign | the column sets double again | one row per legal entity |
| Deadline and escalation job (ADR-0073:74) | scans command rows by status, and needs a second rule for withdrawal requests | scans open request rows only |
| Org-structure commands (card F) | the same columns repeated on a second table | the same table gains `org_structure_command_id` (exactly one of the two) |
| Invariant "status leaves AWAITING_ATTRIBUTION_COUNTERSIGN only with every required decision" | in-row CHECK | cross-row, so application guard + behaviour test (same pattern as ADR-0068 1.5 cross-row rules) |

Choice: **B**. The number of signatures per command varies with Q1 and with withdrawal, and the job needs one queue. A keeps one invariant in-row but grows columns for each variation. This differs from the wording of ADR-0073 §4; §4 defers the shape to the data sign-off ("由 one-data 签发"), which is this role now, so it is a shape choice inside the ADR, reported to lead as a deviation call.

**Who calculates the cost legal entity**: workforce only, at entry and again at countersign (ADR-0073:58, :73); the caller never passes it. No option here.

## 4. State machine (personnel command)

Statuses: `PENDING`, `HELD`, `EFFECTIVE`, `WITHDRAWN` (ADR-0068 1.4) plus `AWAITING_ATTRIBUTION_COUNTERSIGN`.

| From | To | When |
|---|---|---|
| (entry) | `AWAITING_ATTRIBUTION_COUNTERSIGN` | the employee's cost legal entity on the effective date differs before and after the command (ADR-0073:59), for any kind (`TRANSFER`, `CHANGE_LEGAL_ENTITY`, `CORRECTION`, ...). No version rows are written yet (ADR-0073 守护 1: "未会签不写版本"). In the same transaction the **required set** of `CHANGE` requests is created, computed from the basis and Q1 (one row per legal entity whose head must sign), never inferred from rows that happen to exist |
| (entry) | `PENDING` / `EFFECTIVE` / `HELD` | as ADR-0068 when the cost legal entity does not change |
| `AWAITING_ATTRIBUTION_COUNTERSIGN` | `PENDING` or `EFFECTIVE` | every row of the required set is `APPROVED`; in the transaction of the last approval: lock the employee root, re-check after-separation, one-per-day, effective-date (closed period) and cost-legal-entity rules (ADR-0073:73), then write all versions of the command (employee legal entity, and for `TRANSFER` the assignment versions); `EFFECTIVE` when the effective date ≤ today |
| `AWAITING_ATTRIBUTION_COUNTERSIGN` | `WITHDRAWN` | a `CHANGE` request is `REJECTED` with a reason; or an approval's re-check fails — then that request is recorded `REJECTED` with the re-check reason ("不满足即拒签", ADR-0073:73, and 拒签 → 已撤回, :72); or the drafter withdraws (:72). In the same transaction every other open request of the command becomes `LAPSED` |
| `PENDING` (countersigned) | `WITHDRAWN` | every row of the `WITHDRAWAL` request set (same legal entities as the `CHANGE` set) is `APPROVED`; versions superseded as ADR-0068 1.6 |
| `PENDING` (countersigned) | `PENDING` | a `WITHDRAWAL` request is `REJECTED`: the command stands, its other open `WITHDRAWAL` rows become `LAPSED`; a new withdrawal may be requested later (S1) |
| `PENDING` (countersigned) | `EFFECTIVE` | effective date reached; open `WITHDRAWAL` rows become `LAPSED` (user Q2 a); undo afterwards only by a countersigned `CORRECTION` |

**No later command while one awaits countersign** (user Q4 a, 2026-10-30): while an employee has an `AWAITING_ATTRIBUTION_COUNTERSIGN` command, entering a command for that employee with a later effective date is refused (same shape as "离职之后", ADR-0068 1.5). Reason: versions of the awaiting command are not written, so a later command would be judged without it; when the awaiting command is approved and back-written, the later command's re-appended version could switch the cost legal entity back with no countersign and no event (review rv887a M3). Earlier-dated commands are allowed; the approval re-check then catches a changed basis.

**Drafter** (ADR-0073:69 with the later user decision of 2026-10-30 named 人事专员): for personnel commands the drafter is the 人事专员 who entered the command; ADR-0073:69 names 组织数据管理员 for drafting, which still holds for org-structure commands (card F). Entry is refused when the drafter's own legal entity or their own org unit chain's legal entity would change (ADR-0027 2.2).

Unchanged: `HELD` is only for `SEPARATE`, which never changes a legal entity by itself. `CORRECTION` that changes a legal entity is exempt from "correction is immediate" (ADR-0073:75). The one-open-command-per-day index includes `AWAITING_ATTRIBUTION_COUNTERSIGN` (ADR-0073:73). Each transition writes workforce `state_transitions` (`PersonnelCommand`).

Countersign request: `OPEN → APPROVED | REJECTED | LAPSED`. `LAPSED` = closed without a decision because the command left the state that needed it (withdrawn, rejected through another row, or the effective date reached for `WITHDRAWAL`); any kind may lapse, so the deadline job never chases a request of a finished command. `state_transitions` aggregate type `AttributionCountersign`.

## 5. Data shapes (proposal for card B; not signed here)

The columns below hold for every answer to Q1–Q4: Q1 changes how many request rows a command gets, not the columns; Q2–Q4 change behaviour only.

### 5.1 `personnel_commands` (change in the baseline while it is open, else a new migration)

- `command_kind` adds `CHANGE_LEGAL_ENTITY`; `status` adds `AWAITING_ATTRIBUTION_COUNTERSIGN`; trigger transitions add the rows of section 4.
- `target_legal_entity_id uuid` → `legal_entities (id) ON DELETE RESTRICT`; partial FK index. CHECK: required for `CHANGE_LEGAL_ENTITY` (and no other target column); allowed for `TRANSFER` and `CORRECTION`; null for every other kind. The five-entity scope (ADR-0022) and ACTIVE status are application checks (the set lives in `legal_entities`).
- Countersign basis (ADR-0073:58), written at entry, never changed: `countersign_previous_legal_entity_id uuid`, `countersign_new_legal_entity_id uuid`, both FK to `legal_entities`. CHECKs: the two are null together; when set they differ; `status <> 'AWAITING_ATTRIBUTION_COUNTERSIGN' OR countersign_new_legal_entity_id IS NOT NULL`. They are content columns, so the append-only trigger already refuses changing them.
- `ck_personnel_commands_correction_immediate` becomes: `CORRECTION` is `EFFECTIVE` or (it carries a countersign basis and is `AWAITING_ATTRIBUTION_COUNTERSIGN` / `WITHDRAWN`).
- `uk_personnel_commands_open_per_day` predicate becomes `status IN ('PENDING', 'HELD', 'AWAITING_ATTRIBUTION_COUNTERSIGN')`.
- New `uk_personnel_commands_id_employee_drafter UNIQUE (personnel_command_id, employee_id, created_by)`, the target of the drafter FK below.

### 5.2 New `attribution_countersigns`

| Column | Rule |
|---|---|
| `attribution_countersign_id uuid` PK | UUIDv7, application |
| `personnel_command_id uuid NOT NULL`, `employee_id uuid NOT NULL`, `drafted_by uuid NOT NULL` | composite FK `fk_attribution_countersigns_command` → `personnel_commands (personnel_command_id, employee_id, created_by)` `ON DELETE RESTRICT`: the drafter is the command's own `created_by`, enforced by the database, not copied by the application; card F makes the command columns nullable and adds `org_structure_command_id` with an exactly-one CHECK |
| `request_kind text NOT NULL` | `CHANGE`, `WITHDRAWAL` |
| `request_sequence_number smallint NOT NULL` | 1 for `CHANGE`; 1, 2, ... for successive `WITHDRAWAL` requests (a rejected withdrawal may be asked again, S1) |
| `legal_entity_id uuid NOT NULL` | whose finance head decides: one row for the previous and one for the new cost legal entity (user Q1 a); FK `legal_entities` |
| `requested_at timestamptz NOT NULL`, `requested_by uuid NOT NULL` | equals `drafted_by` for `CHANGE`; the withdrawer for `WITHDRAWAL`; account id, no FK |
| `due_on_date date NOT NULL` | 3 working days per ADR-0072 calendar, Monday–Friday with an alert when the year is unconfirmed (ADR-0073:74) |
| `decision_kind text` | `APPROVED`, `REJECTED`, `LAPSED` |
| `decided_at timestamptz`, `decided_by uuid`, `decision_reason text`, `decision_audit_entry_id uuid` | set together once; `decided_by` null only for `LAPSED`; reason 1–500 non-blank, required for `REJECTED` |
| `escalation_level smallint NOT NULL DEFAULT 0` | 0–3, only increases (reminder, second level, third level, ADR-0073:74) |
| `created_at`, `updated_at`, `version` | as `separation_holds` |

Constraints: `uk_attribution_countersigns_request UNIQUE (personnel_command_id, request_kind, legal_entity_id, request_sequence_number)`; one open row per (command, kind, legal entity): partial unique index `WHERE decision_kind IS NULL`; `ck_attribution_countersigns_not_drafter CHECK (decided_by IS DISTINCT FROM drafted_by AND decided_by IS DISTINCT FROM requested_by)` (ADR-0073:71, named CHECK; bound to the command's drafter through the FK above); `ck_..._change_requester CHECK (request_kind <> 'CHANGE' OR (requested_by = drafted_by AND request_sequence_number = 1))`; decision columns paired, `decided_by` null exactly for `LAPSED`; reason required for `REJECTED`; partial index on open requests `(due_on_date) WHERE decision_kind IS NULL`. Trigger: only decision columns (null → set once), `escalation_level` (up only), `updated_at`, `version` change; DELETE only by the owner (10-year purge, W2 §8); TRUNCATE refused. Grants: `REVOKE DELETE, TRUNCATE` in the three ops SQL files. Classification: `decision_reason` INTERNAL + PI (as `withdrawal_reason`). Registry: entity inside the Employee aggregate, `APPEND_ONLY` with the trigger condition.

### 5.3 `employee_legal_entities`

Needs the W2 common set (command FK, superseded pair, `closed_at`, ...) before a command can write it — the same work #880 did for `assignment_versions`. Not in this note's tables; card A below.

## 6. Permission and holders

- Permission `organization.workforce:legal-entity-attribution:approve` (resource = the `LegalEntityAttribution` aggregate the change writes, ADR-0073:55; action `approve` from the standard set; covers approve and reject, as `separation-hold:approve` covers release). Held by `legal_entity_finance_head`.
- Second escalation level (ADR-0073:74: "两项授权同时写进权限矩阵"): confirmed by the user (Q3 a): the same permission granted to the migration business owner during migration and to `business_admin` after go-live — see Q3.
- Not allowed to decide: the drafter (database CHECK, section 5.2); anyone whose own legal entity, or whose org unit chain's, is being changed, and the employee themself (application checks, ADR-0073:69).
- One person who is the finance head of both legal entities (Q1 a) decides each row separately; the rows stay two decisions with two audit entries.
- **No deputies** (user 2026-10-30, 「采纳」 via lead): a finance head's absence is covered by escalation (section 4 levels 2–3), not by a deputy who may decide.
- Third escalation level "财务负责人" (ADR-0073:74) is read as the same `legal_entity_finance_head` of the row's legal entity, plus the monthly red light; there is no group-level finance role in the glossary.

## 7. Events and audit

- Event `EmployeeLegalEntityChanged` (暂名, 「法人归属已变更」, ADR-0073:100; name to one-glossary): published when a command takes effect and the employee's cost legal entity changes, whatever the kind (a `TRANSFER` publishes both `EmployeeTransferred` and this). Payload: `personnelCommandId`, `employeeId`, `effectiveDate`, `previousLegalEntityId`, `newLegalEntityId`; envelope as contract A (aggregate Employee, `businessDate` = effective date, `legalEntityId` = the new one).
- Countersign request, approve, reject, lapse: audit only (`organization.workforce.attribution-countersign.requested|approved|rejected|lapsed`, subject `EMPLOYEE`, detail: command id, request kind, legal entity, decider, reason), same rule as ADR-0068 1.9 for entry and withdrawal.

## 8. Cards to open (for lead)

| | Type | Content | Prerequisites |
|---|---|---|---|
| A | impl | `employee_legal_entities` W2 common set + command FKs (like #880) | DD on the card |
| B | impl | section 5.1 and 5.2 tables, `state_transitions` pairs, ops grants, registries | DD signed on card B |
| C | acceptance | cases of section 10 | B's DD |
| D | impl | entry decision, countersign / reject / withdraw use cases, permission seed, events, audit | A, B, C, #872 |
| E | impl | deadline, reminder and escalation job (`@Scheduled` + `@SchedulerLock`, ADR-0072 calendar) + its acceptance | D |
| F | design → impl | org-structure side: `ASSIGN_LEGAL_ENTITY`, merge exemption CHECK, `org_structure_command_id` on `attribution_countersigns` | `org_structure_commands` card (ADR-0068 §4) |
| G | glossary | GitHub Issue for the homonym 会签 (`platform.approval` countersign vs finance countersign); proposed terms: 待会签, 成本法人, 法人归属已变更, attribution countersign | none |

## 9. Business questions for the user (through lead) — answered 2026-10-30

User answers (relayed by lead, 2026-10-30): **Q1 (a)** both the previous and the new legal entity's finance heads countersign, either may reject; **Q2 (a)** an open withdrawal request lapses on the effective date, undo by a countersigned correction; **Q3 (a)** the escalated persons may countersign; **Q4 (a)** later-dated commands are refused while one awaits countersign. The options as asked:

- **Q1 Whose finance head countersigns a cross-entity change?** The role is per legal entity and the glossary says cross-entity matters must name which. (a) both the previous and the new legal entity's heads, either may reject — recommended: the cost leaves one set of books and enters another; (b) only the new entity's head; (c) only the previous entity's head.
- **Q2 A countersigned future-dated command whose withdrawal is awaiting finance when the effective date arrives.** Versions are already written, so by ADR-0068 1.8 the change is in force on that date whatever the job does. (a) the withdrawal request lapses on that date and the drafter uses a `CORRECTION` (itself countersigned) — recommended, no hidden pending state; (b) the change is held back until finance decides — needs superseding the versions on the effective date and contradicts "判断不依赖作业".
- **Q3 Escalation levels 2 and 3.** (a) the escalated person may countersign (the ADR's "两项授权…写进权限矩阵") — recommended reading; (b) reminders only, finance still decides.
- **Q4 While an employee's legal-entity change awaits countersign (3 to 9 working days), may later-dated personnel commands for that employee be entered?** (a) no, refused until the change is countersigned or withdrawn — recommended: otherwise the later command, judged without the pending change, can switch the cost legal entity back without countersign when the change is back-written; (b) yes, and the later command is judged as if the pending change were approved, re-judged if the change is rejected — more work for users and code, no gap.

## 10. Acceptance cases (for the independent test engineer, card C)

1. A `TRANSFER` across legal entities enters `AWAITING_ATTRIBUTION_COUNTERSIGN`; one inside a legal entity does not; a `TRANSFER` whose org unit chain changes the cost legal entity without `target_legal_entity_id` enters it too; the caller cannot pass a cost legal entity (ADR-0073:59).
2. No version row (employee legal entity or assignment) exists while awaiting; after approval all are written in the approval transaction, at the original effective date.
3. Approval with effective date ≤ today → `EFFECTIVE` and `EmployeeLegalEntityChanged` once; future → `PENDING`, no event yet.
4. Reject needs a reason → `WITHDRAWN`, no versions, no event, the other open rows `LAPSED`; drafter withdrawal while awaiting → `WITHDRAWN`, all rows `LAPSED`; the deadline job never reminds on a lapsed row.
5. Withdrawal of a countersigned `PENDING` command opens a `WITHDRAWAL` request set; approval of all supersedes the versions; a rejection keeps the command `PENDING` and a second withdrawal request is possible; the date passing lapses open rows (per Q2).
6. Decider = drafter refused by the database even when the application records another requester (named CHECK through the drafter FK); decider whose own or chain's legal entity changes refused; drafter whose own or chain's legal entity would change refused at entry; a holder without `legal-entity-attribution:approve` refused; the escalated role per Q3.
7. Approval re-checks under the employee lock: a second command entered for the same day meanwhile, a separation entered meanwhile, or a cost legal entity that changed meanwhile → that request `REJECTED` with the reason and the command `WITHDRAWN` (concurrency test, two threads). A later-dated command entered while one awaits countersign is refused (Q4 a), so no back-written approval can switch the legal entity back without countersign.
12. An `AWAITING_ATTRIBUTION_COUNTERSIGN` command always has its full required set at entry; with one row approved and another still open the command stays awaiting (no vacuous "all approved").
8. Effective date in a closed period of an affected legal entity refused at entry and at approval (ADR-0073:61, ADR-0016).
9. `CORRECTION` that changes a legal entity waits for countersign and is subject to one-per-day.
10. Target legal entity outside the five or inactive refused.
11. Due date skips non-working days of the confirmed calendar; with an unconfirmed year Monday–Friday plus an alert; reminder, level 2 after 2 more working days, level 3 after 2 more, no level 4, never auto-approve or auto-withdraw (card E, injected clock).

## 11. Out of scope

Org-structure commands (card F); unsettled contracts and projects after a legal-entity change (ADR-0073:99, other BCs); migration rows (`MIGRATION`, no countersign, ADR-0073 迁移方案 2); historical attribute differences (ADR-0073 §2.5).
