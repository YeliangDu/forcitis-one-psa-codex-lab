# Org-structure commands and their legal-entity countersign — design (#897)

- Card: #897 (DES-1030-FC-F), parent #878. Author: one-design, 2026-10-30. Base: origin/main a466dcfb0.
- Basis: ADR-0068 §4 (org unit and position commands, user H4) and its evidence attachment `docs/architecture/research/seeyon-sync-withdrawal/adr-0068-evidence-attachment.md` §「组织结构命令」(DDL signed by one-data, fourth round); ADR-0073 §2 (`SET_ATTRIBUTES`), §3 (`ASSIGN_LEGAL_ENTITY`, merge countersign), §4 (table changes); W2 design §2, §3.6, §9; the personnel-side design `legal-entity-countersign.md` (#878) and the user answers of 2026-10-30 (`docs/decisions/2026-10-30-084431-…`, `…-084432-…`).
- Status: proposal. One business question (QF1, §8). Data design is signed on each schema card, not here.

## 1. The one thing to decide

The shape of the org-structure command line — the base table that ADR-0068 §4 approved but nobody has built, and the countersign of org commands that change employees' cost legal entity (ADR-0073 §3.4, §3.6) — so the cards can be opened in order. Business rules are decided (ADR-0068 §4, ADR-0073 §2–§5, user 2026-10-30); this note fixes shape and order.

## 2. Facts (verified on origin/main a466dcfb0 unless marked)

| Fact | Source |
|---|---|
| No `org_structure_commands` table; `personnel_commands.org_structure_command_id` exists without FK | baseline :585, :647; W2 §9 |
| `org_unit_versions` has `parent_org_unit_id`, `head_employee_id`, `regional_receivables_manager_id`, `region_kind`, `is_delivery_unit`, status `ACTIVE/DISABLED/REVOKED`; no command FK, no superseded pair; trigger `refuse_version_rewrite` (close only) | baseline (table `org_unit_versions`) |
| `org_unit_legal_entities`: no W2 common set; unique `(org_unit_id, effective_from_date)`; range `>` | baseline (table `org_unit_legal_entities`) |
| `position_versions` does not exist (W2 §3.3 not built) | `git grep position_versions` → no table |
| `org_units` root still holds current values written by the sync (W2 §9: root narrowing pending) | W2 §9 |
| Attachment DDL for `org_structure_commands` was signed by one-data (fourth round) with kinds `CREATE, DISSOLVE, RENAME, MERGE, REPARENT, ASSIGN_HEAD, ASSIGN_RECEIVABLES_MANAGER` / `CREATE, DISABLE` and statuses `PENDING, IN_PROGRESS, EFFECTIVE, WITHDRAWN` | attachment §「组织结构命令」 |
| ADR-0073 §4 changes that DDL: drop `RENAME`, add `SET_ATTRIBUTES`, `ASSIGN_LEGAL_ENTITY`, `target_legal_entity_id`, the countersign status, merge exemption, superseding reference on unit attributions | ADR-0073:79-84 |

## 3. Options and choices

**3.1 Countersign basis of an org command (many employees).** ADR-0073:58-60: the basis lists every employee whose cost legal entity changes, with before and after; a merge's per-person transfer is exempt when it matches the basis.

| | A. Child table, one row per affected employee (chosen) | B. jsonb list on the command row | C. Recompute at execution, no stored basis |
|---|---|---|---|
| Per-person merge exemption ("与会签依据一致") | join by (command, employee) | parse jsonb | nothing to compare with — the exemption cannot be decided |
| D18 (no jsonb for business content) | meets | violates | — |
| Audit of what finance signed | rows | blob | lost |

**3.2 Merge exemption guard.** ADR-0073:60 asks for a CHECK "`org_structure_command_id` 非空且合并命令已会签". A CHECK cannot read another row. Options: (a) `BEFORE INSERT` trigger on `personnel_commands` (chosen); (b) application only. (a) keeps a database line of defence, as the ADR intends: a row that references an org command and does not itself await countersign is accepted only when the referenced command is a `MERGE` in `IN_PROGRESS` (reachable only after countersign). This is a deviation from the ADR's word "CHECK", same intent; reported to lead.

**3.3 Countersign request rows for org commands.** Reuse `attribution_countersigns` (#893) with a second parent column, as the personnel note §8 F planned — one queue for the deadline job (card E), one decider rule (#895).

**3.4 Order of work.** The org line has more prerequisites than the personnel line: `org_unit_versions` needs the W2 common set before any command can back-date (ADR-0073:43 「过去生效的按 W2 作废加追加处理」), and `position_versions` does not exist. So the base table and the version tables come first, then plain org commands, then their countersign.

## 4. Data shapes (proposals for the schema cards; DD signed there)

### 4.1 `org_structure_commands` (card F1)

The attachment DDL as signed, with ADR-0073 §4 applied:
- `ck_org_structure_commands_command_kind`: `ORG_UNIT` → `CREATE, DISSOLVE, SET_ATTRIBUTES, MERGE, REPARENT, ASSIGN_HEAD, ASSIGN_RECEIVABLES_MANAGER, ASSIGN_LEGAL_ENTITY`; `POSITION` → `CREATE, DISABLE`. `RENAME` is gone (merged into `SET_ATTRIBUTES`, ADR-0073:42).
- `ck_..._set_attributes_columns` (replaces `_rename_columns`): `SET_ATTRIBUTES` only for `ORG_UNIT`; `num_nonnulls(target_name, target_short_name, target_region_kind, target_is_delivery_unit) >= 1`; parent, head, receivables manager, merge target and legal entity null.
- `ck_..._create_only_columns`: `target_code` only on `CREATE`; `target_region_kind`, `target_is_delivery_unit` on `CREATE` or `SET_ATTRIBUTES`.
- `target_legal_entity_id uuid` → `legal_entities` RESTRICT, partial index; `ck_..._legal_entity_columns`: required for `ASSIGN_LEGAL_ENTITY` (and every other target column null); optional on `ORG_UNIT CREATE` (initial attribution, no countersign, ADR-0073:56); null otherwise.
- Status adds `AWAITING_ATTRIBUTION_COUNTERSIGN`; trigger transitions add `AWAITING_ATTRIBUTION_COUNTERSIGN → PENDING | EFFECTIVE | WITHDRAWN`; `PENDING → IN_PROGRESS` for `MERGE` unchanged; no transition into the awaiting status after entry.
- Open-per-day unique index includes `AWAITING_ATTRIBUTION_COUNTERSIGN`.
- `uk_org_structure_commands_id_drafter UNIQUE (org_structure_command_id, created_by)` (target of the drafter FK in §4.4).
- `personnel_commands`: add `fk_personnel_commands_org_structure_command_id` (W2 §9 promised it with this table).
- workforce `state_transitions`: aggregate type `OrgStructureCommand`, pairs per the trigger.
- Registry, grants (`REVOKE DELETE, TRUNCATE`), comments as the attachment lists.

### 4.2 Version tables (card F0, before F1's use cases)

- `org_unit_versions`: W2 §2 common set as #880 did for assignments, but the command FKs point to `org_structure_commands`: `change_org_structure_command_id`, `superseding_org_structure_command_id`; `ck_..._command`: `(change_channel_kind = 'COMMAND') = (change_org_structure_command_id IS NOT NULL)`. A superseding reference is needed after all (the attachment said none): ADR-0073:43 lets `SET_ATTRIBUTES` and :75 `ASSIGN_LEGAL_ENTITY` back-date with void-and-append. Sync writer `OrgHistory` adapted as in #880.
- `org_unit_legal_entities`: W2 common set as #892, FKs to `org_structure_commands` (`change_org_structure_command_id`, `superseding_org_structure_command_id`); partial unique `WHERE superseded_at IS NULL` under the old name (acceptance `OwnIdColumnsIT` row moves to the current-row list, one-design commit as for #892).
- `position_versions`: created per W2 §3.3 with the common set and `change_org_structure_command_id`; until it exists, position `CREATE` / `DISABLE` commands cannot write versions — card F0 includes it or F1's position kinds wait.
- `org_unit_versions.status` `DISABLED` (one-data fourth round question 3, owner one-data → now this role): out of scope here, stays an open item on the W2 line.

### 4.3 `org_structure_countersign_bases` (card F3)

One row per employee whose cost legal entity changes because of an org command (ADR-0073:58-60).

| Column | Rule |
|---|---|
| `org_structure_countersign_basis_id uuid` PK | UUIDv7 |
| `org_structure_command_id uuid NOT NULL` | FK → `org_structure_commands` RESTRICT |
| `employee_id uuid NOT NULL` | FK → `employees` RESTRICT |
| `previous_legal_entity_id`, `new_legal_entity_id uuid NOT NULL` | FK → `legal_entities`; CHECK distinct |
| `created_at timestamptz NOT NULL` | |

`uk_..._command_employee UNIQUE (org_structure_command_id, employee_id)`; append-only (no UPDATE at all; owner-only DELETE for the purge); registry entity inside the org-unit aggregate, `APPEND_ONLY`; no personal data beyond ids. The basis is written at entry and rewritten only by withdrawing and re-entering (approval re-check compares, never edits).

### 4.4 `attribution_countersigns` for org commands (card F3)

- `personnel_command_id`, `employee_id` become nullable; add `org_structure_command_id uuid`; `ck_attribution_countersigns_parent`: `num_nonnulls(personnel_command_id, org_structure_command_id) = 1` and `(personnel_command_id IS NULL) = (employee_id IS NULL)`.
- `fk_attribution_countersigns_org_command (org_structure_command_id, drafted_by)` → `org_structure_commands (org_structure_command_id, created_by)`; the personnel FK stays (MATCH SIMPLE: each FK checks only when its columns are all set). `drafted_by` stays NOT NULL, so the not-drafter CHECK covers both parents.
- Unique keys become two partial pairs (one per parent column) with the same shape as #893.
- Required set for an org command (user Q1 a): one `CHANGE` row per legal entity in `{previous} ∪ {new}` over all basis rows.

### 4.5 Merge-transfer guard (card F3)

`BEFORE INSERT` trigger on `personnel_commands`: when `NEW.org_structure_command_id IS NOT NULL` and `NEW.status <> 'AWAITING_ATTRIBUTION_COUNTERSIGN'`, the referenced row must be `command_kind = 'MERGE' AND status = 'IN_PROGRESS'`; otherwise refuse. Plus the existing `ck_personnel_commands_org_structure_command` (only `TRANSFER`).

## 5. Rules (behaviour)

1. **Countersign trigger** (ADR-0073:59, the only trigger): at entry of `ASSIGN_LEGAL_ENTITY`, `REPARENT`, `MERGE` (and `DISSOLVE` only if it moves people — it does not: dissolving needs an empty unit, attachment), workforce computes every affected employee's cost legal entity on the effective date before and after; any difference → `AWAITING_ATTRIBUTION_COUNTERSIGN`, basis rows and the required request set in the same transaction. No difference → normal path. Affected employees: those whose assignment on the effective date is in the target unit or its subtree (for `REPARENT` / `ASSIGN_LEGAL_ENTITY`: subtree units without their own attribution; for `MERGE`: the merged unit's people).
2. **No versions while awaiting**; on the last approval, lock the org-unit root row, recompute the basis; if it differs from the stored basis (set of employees or any before/after), the approval is a re-check failure → that request `REJECTED` with the reason, command `WITHDRAWN`, other rows `LAPSED` (same as personnel, ADR-0073:73). Else write versions (unit versions / unit attribution) and go `PENDING` or `EFFECTIVE`.
3. **Later-dated org commands on the same unit are refused** while one awaits countersign (user Q4 a applied to org units). Personnel commands for affected employees are not blocked; their effect is caught by rule 2's basis comparison at approval.
4. **Merge execution** (ADR-0073:60): `PENDING → IN_PROGRESS` with `affected_employee_count`; per person, one transaction: lock the employee root, recompute the person's before/after; equal to the basis row (or both unchanged and no basis row) → generate the `TRANSFER` with `org_structure_command_id`, exempt from countersign (guarded by §4.5), count it; different → the generated `TRANSFER` enters `AWAITING_ATTRIBUTION_COUNTERSIGN` on its own (personnel path, #895), not counted. The merge reaches `EFFECTIVE` only when every affected person is counted (attachment `ck_..._merge_progress`); what happens if such a person's own countersign is rejected is QF1.
5. **Drafter**: 组织数据管理员 (ADR-0073:69, ADR-0027 2.1); refused when the drafter's own legal entity or own org unit chain's legal entity would change; decider rules as #895 (holder of `legal-entity-attribution:approve` AND finance head of the row's entity in today's version; `business_admin` from escalation level 2).
6. **Events**: org command events per ADR-0068 §5 (names to the glossary card); when an org command takes effect and changes employees' cost legal entity, `EmployeeLegalEntityChanged` is published once per changed employee (consumers key by employee). A merge publishes it through each generated transfer, not twice.
7. **Closed periods** (ADR-0073:61): affected legal entities = basis before/after plus the unit's own department legal entity before/after; checked at entry and approval (no period module yet; same gap as #894).

## 6. Cards to open (for lead)

| | Type | Content | Prerequisites |
|---|---|---|---|
| F0 | impl | `org_unit_versions` and `org_unit_legal_entities` W2 common set with org-command FKs; `position_versions` per W2 §3.3; writers adapted | DD on card |
| F1 | impl | `org_structure_commands` (§4.1), personnel FK, `state_transitions` pairs | F0; DD on card |
| F2a | acceptance | plain org commands (ADR-0068 §4, ADR-0073 §2: create, dissolve with empty-unit rule, set attributes incl. back-dating, reparent, heads, merge preview and execution with progress and restart, position create/disable) | F1 DD |
| F2b | impl | org command use cases without countersign, permission `organization.workforce:org-structure-command:create` (G43) for 组织数据管理员, events | F1, F2a |
| F3 | impl | §4.3–§4.5 tables and trigger | F1; DD on card |
| F4a | acceptance | org countersign: ADR-0073 守护 1 merge counter-examples (chain change after countersign stops the person; consistent change is exempt; incomplete merge never shows done), peak scenario (≥ 100 transfers a day incl. one cross-entity, no deadlock, progress correct), rules 1–3, 5 | F3 DD; #902 names |
| F4b | impl | org countersign use cases, merge exemption, basis compare | F2b, F3, F4a, #895 |
| F5 | glossary | 调整属性 `SET_ATTRIBUTES`, 指定法人 `ASSIGN_LEGAL_ENTITY`, 会签依据, org command event names | none |

## 7. Out of scope

`org_units` root narrowing (W2 §9, owner now this role, separate card); `org_unit_versions.status DISABLED` decision; unsettled contracts and projects after a legal-entity change (ADR-0073:99); historical attribute differences (ADR-0073 §2.5); deputies of finance heads (open user question).

## 8. Business question for the user (through lead)

- **QF1 A person stopped during a merge whose own legal-entity countersign is rejected** (ADR-0073:60 says the person is stopped and not counted, but not what happens after a rejection; the merged unit cannot be dissolved while the person is still in it). (a) the merge stays in progress until HR moves that person out of the merged unit by any other effective command, which then counts as done for the merge — recommended: nobody is left in a dissolved unit, and finance keeps its refusal; (b) the merge is cancelled as a whole — people already transferred stay where they are and the unit is not dissolved; (c) the rejected person is transferred anyway without countersign — contradicts the user's "保留会签".
