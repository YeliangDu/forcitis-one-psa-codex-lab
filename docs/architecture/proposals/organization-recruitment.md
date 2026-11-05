# organization.recruitment — first spec: the recruitment requisition (AGG-65, #1432)

- Card: #1432. Author: one-design, 2026-11-05. Base: origin/main.
- Status: proposal. Basis: ADR-0085 (recruitment becomes its own context; adopted 2026-11-04), ADR-0087 (HR batch order), ADR-0051 decision 4 item 5 and decision 7 item 4 as replaced by ADR-0085 decision 6, ADR-0043 (classification and retention), planned.yaml AGG-65, closeout `people/commands.yaml` and `state-machines.yaml` (AGG-65), Q26 (no headcount check; intent 134134 Q-08).
- Evidence marks: **verified** = read on origin/main today; **unverified** = not checked or waiting on evidence.
- Nothing here is code. No applicant, interview or offer aggregate (next card).

## 1. The one thing to decide

What a requisition is, how it moves, and where it is stored, so that `organization.recruitment` has a first slice and golden workforce node 001 (requisition) has a home. Technical choices are mine and marked; business items are in section 9.

**Finding for lead, before the plan (section 9, B-1).** The card text and `planned.yaml` AGG-65 say the approval is "inside the aggregate, not `platform.approval`, because the edge would form a cycle". That reason belonged to `organization.workforce` and is gone: ADR-0085 (adopted 2026-11-04) moved the requisition out of workforce and its decision 4 says the single-level approval is **one node through `platform.approval`**. Checked: `module-dependencies.txt` has `platform.approval -> organization.workforce` and nothing that points from workforce or approval to recruitment, so `organization.recruitment -> platform.approval, organization.workforce` closes no cycle. This spec follows ADR-0085 (the user's later and explicit decision); the card sentence and the `planned.yaml` / `commands.yaml` wording ("不经 platform.approval") are stale and owed a one-line update by the SSOT card.

## 2. Verified facts

| Fact | Where |
|---|---|
| Context `organization.recruitment` is registered, `planned`, schema `organization_recruitment`; build slot is the late HR batch; HIRE goes through the workforce personnel command api | `contexts.yaml:95-108` |
| ADR-0085 decisions: dependencies `-> organization.workforce (api), platform.approval, platform.file, platform.numbering, platform.audit`; workforce does not depend on recruitment; single-level approval through one `platform.approval` node; applicant data retention value is the user's | `ADR-0085.md:40-47` |
| AGG-65 commands: submit, approve, close; events: requisition approved, closed (analytical only); references AGG-64 position, AGG-61 org unit, AGG-62 candidate person (api); no invariant; Q26: no overstaffing check | `planned.yaml:976-987` |
| State machine in the closeout: `DRAFT`, `SUBMITTED`, `APPROVED`, `CLOSED` (terminal). The aggregate draft's `REJECTED` and `RECRUITING` are **not** set: v1 closes instead of rejecting, and no command enters "recruiting" (PG-08) | `state-machines.yaml:157-198` |
| Command parameters (submit): requisition id (client-generated), position id, org unit id, requested headcount, expected start date, description; precondition R-01: position enabled, org unit valid; approve: requester is not the approver; approver is the org unit head, one level up when the requester is that head (AR-11), the head's superior when the head is absent (AR-12, "待 V-18"); close reasons: rejected, withdrawn, cancelled, filled (filled has no trigger, PG-08) | `commands.yaml:369-457` |
| `organization_workforce.positions` exists (`status` ACTIVE / DISABLED / DELETED); the workforce api offers `OrgDirectory.findOrgUnit` and `findOrgUnitHead`, **no position lookup** | baseline migration; `OrgDirectory.java:34` and following |
| `ApprovalProcesses.start` is idempotent per (process type, document, round); the result arrives as an approval-completed event; `withdraw` and `cancel` exist; leave and timesheet use this pattern | `platform/approval/api/ApprovalProcesses.java`; `organization-leave.md` section 2 |
| Glossary: `recruitment_requisition` is registered with `bc: organization.workforce` (stale after ADR-0085) | `terms.yaml:13569` |
| Legacy: A8 form 用人申请表, 211 instances, 17 fields, last used 2026-09-21; the field list is **unverified** here (not read; see B-4) | `a8-templates.tsv:83`; `process-inventory.md:129`; ADR-0085 line 76 |
| Golden pack node 001 (requisition): no design existed (one-test gap list, #1410); the pack itself is not in the repo, so its expected values are **unverified** | card text |

## 3. Options and choices (technical; mine to choose)

| # | Choice | Options | Decision |
|---|---|---|---|
| C-1 | Where approval happens | (a) inside the aggregate (card text, `planned.yaml`); (b) one `platform.approval` node (ADR-0085 decision 4) | (b). (a) rested on the workforce cycle that no longer exists; (b) is the adopted ADR, reuses the platform's task, withdraw and audit, and matches leave and timesheet. Cost of (b): the approver rule must be expressible in the approval template (see C-2). |
| C-2 | Who the approver is | resolve inside recruitment and pass the user to the process; or a template rule | Recruitment resolves the approver at submit with the workforce api (`findOrgUnitHead`; one level up when the requester is the head; the head's superior when absent) and starts the process for that user. The template carries one node. Whether `ApprovalStartRequest` accepts a resolved approver today is **unverified**; if not, the R2 card adds the smallest change to the approval api (a platform card, not mine). |
| C-3 | States | the draft's six; the closeout's four | Four: `DRAFT`, `SUBMITTED`, `APPROVED`, `CLOSED`, with `close_reason_kind` `REJECTED`, `WITHDRAWN`, `CANCELLED`, `FILLED`. `RECRUITING` is not stored: no command enters it. The applicant slice (next card) decides whether "recruiting" is a state or a read of "approved with open offers"; adding a value is a lossless widening of the CHECK. |
| C-4 | Draft saving | domain command or plain save | Plain save of a `DRAFT` row (state-machines note: saving a draft is not a domain command); `SubmitRequisition` is the first domain command. |
| C-5 | Headcount | store and check; store only | Store `requested_count` as information; **no check** against any establishment (Q26). It stays a number the requester states. |
| C-6 | Document number | none; platform.numbering | `platform.numbering` (ADR-0085 decision 2 lists it); document type code registered by the R1 card. |
| C-7 | Reference to position and org unit | foreign key; plain id | Plain `uuid` ids, no foreign key (other context, rule B4); validity is checked at submit through the workforce api and not re-checked later (a position disabled after submission does not void the requisition). |

## 4. Aggregate

### 4.1 RecruitmentRequisition (AGG-65, `organization.recruitment`)

One row per requisition, one aggregate. A requisition states: which position, in which org unit, how many people, from when, and why.

- States: `DRAFT` → `SUBMITTED` → `APPROVED` → `CLOSED`; `SUBMITTED` → `CLOSED` (rejected by the approver, or withdrawn by the requester); `APPROVED` → `CLOSED` (cancelled, or filled by hand: no trigger exists, PG-08). `CLOSED` is final; a new need is a new requisition.
- Commands (names are the aggregate list's; wording is one-glossary's):
  - `SubmitRequisition(requisitionId, positionId, orgUnitId, requestedCount, expectedStartDate, descriptionText)`: from `DRAFT` (or creates the row directly in `SUBMITTED` when the client sends the full form: same effect); preconditions R-01 (position `ACTIVE`, org unit valid, through the workforce api) and `requestedCount >= 1`; resolves the approver (C-2); starts the approval process; idempotent by requisition id. Event: none (internal).
  - `ApproveRequisition`: **not an endpoint**; run by the approval-result handler when the process completes approved (same shape as leave's `ApplyApprovalResult`: idempotent, race-safe with withdraw). `SUBMITTED` → `APPROVED`; the requester is never the approver (precondition of the process start). Event: `RequisitionApproved` (analytical).
  - `RejectRequisition`: same handler, process completes rejected: `SUBMITTED` → `CLOSED` with `REJECTED`. Event: `RequisitionClosed`.
  - `WithdrawRequisition(requisitionId, reasonText)`: requester only, `SUBMITTED` → `CLOSED` with `WITHDRAWN`; cancels the approval process first (`ApprovalProcesses.cancel`), same transaction. Event: `RequisitionClosed`.
  - `CloseRequisition(requisitionId, closeReasonKind)`: `APPROVED` → `CLOSED` with `CANCELLED` or `FILLED`; by the requester or HR (RACI R of the closeout). Idempotent: closing a closed requisition changes nothing and returns the original. Event: `RequisitionClosed`.
- Events (ids only, no free text): `RequisitionApproved` (requisition id, org unit id, position id, approved at); `RequisitionClosed` (requisition id, close reason kind, closed at). Consumers: audit and analytics only. Payloads are written by the R2 card in the events catalogue of this context (a new file under the events folder), because a catalogue entry of a code-tier event without a publisher fails `EventCatalogueTest` (same as the payroll PY0 record).
- Invariants: none from `planned.yaml` (Q26). Rules carried by the aggregate (numbers by the SSOT card, none invented here): R-01 at submit; requester differs from approver; a `CLOSED` requisition never changes; one active approval process per requisition.

### 4.2 What is not here

Applicants, applications, interviews, offers, the HIRE hand-off and the "recruiting" state are the next card. **Hand-off, described only:** when an offer takes effect, `organization.recruitment` calls the workforce personnel command api to issue HIRE (ADR-0085 decision 3; entry, employee number and account opening stay as ADR-0078 and ADR-0055 say). The offer, not the requisition, carries the requisition id; workforce gets no pointer back and does not depend on recruitment.

## 5. Permissions and data scope

Codes follow `{subdomain}.{BC}:{resource}:{action}`; final catalogue entries are fixed by the slice that adds each endpoint. Proposed:

| Code | Holder | Notes |
|---|---|---|
| `organization.recruitment:requisition:submit` | any employee with a position in the org unit's scope (requester) | also `withdraw` by the requester |
| `organization.recruitment:requisition:read` | requester, approver, HR | list and detail |
| `organization.recruitment:requisition:close` | requester, HR | `CloseRequisition` |

Approving is a task in `platform.approval`, not a code of this context. Data scope, fail-closed: a requisition belongs to its org unit; reads are filtered by the caller's org-unit scope (requester and approver see their own through the task); an unknown scope sees nothing (the S4 pattern of the workforce spec: empty scope lists zero rows, a detail answers 404).

## 6. Module edges

Lines for `backend/architecture/module-dependencies.txt`, added by the R1 card with the first code:

- `organization.recruitment -> organization.workforce, platform.approval, platform.audit, platform.numbering` (`platform.file` joins with the applicant slice, ADR-0085 decision 2).
- Nothing points to recruitment from workforce, approval or numbering. Guard to add: no other context writes `organization_recruitment` tables.
- The `NamingSurfaceTest` and `bcs.yaml` registration of the new context (ADR-0085, "需同步修改的守护规则") is the R1 card's; `contexts.yaml` already has the row.

## 7. Glossary

`recruitment_requisition` is registered but with `bc: organization.workforce`; the glossary card sets it to `organization.recruitment`. Table `recruitment_requisitions` (the closeout's entity name) and the event names `RequisitionApproved`, `RequisitionClosed` need glossary terms before the R2 card; the naming of columns is checked by `GlossaryTest` in the R1 card (B8).

## 8. Data design (a draft for sign-off, not a sign-off; the gate in `docs/data/data-design-gate.md` applies to the R1 schema card, which brings Appendix A and a record in `docs/data/reviews/`)

Schema `organization_recruitment`, one table.

| Column | Type | Rule |
|---|---|---|
| `id` | uuid | pk (UUIDv7, client-generated for idempotency) |
| `requisition_number` | text null | 为空表示 still a draft (no number yet); issued by `platform.numbering` at submit; unique when set |
| `position_id` | uuid NOT NULL | no FK (B4) |
| `org_unit_id` | uuid NOT NULL | no FK (B4); the scope key of reads |
| `requested_by_employee_id` | uuid NOT NULL | the requester; no FK (B4) |
| `requested_count` | integer NOT NULL | `ck_…_requested_count` `BETWEEN 1 AND 999`; information only, no check against any establishment (Q26) |
| `expected_start_date` | date null | 为空表示 not stated; `>=` the creation date at submit is a command check, not a CHECK |
| `description_text` | text null | 为空表示 no description; `char_length` ≤ 500, `btrim` check; the form must say it holds no applicant names or personal data |
| `status` | text NOT NULL | `ck_…_status`: `DRAFT`, `SUBMITTED`, `APPROVED`, `CLOSED` |
| `close_reason_kind` | text null | 为空表示 not closed; `ck_…_close_reason_kind`: `REJECTED`, `WITHDRAWN`, `CANCELLED`, `FILLED`; set exactly when `status = 'CLOSED'` (`ck_…_closed_columns`) |
| `approval_instance_id` | uuid null | 为空表示 no approval started; the process in `platform.approval`; no FK; set exactly when `status` is not `DRAFT` |
| `submitted_at`, `approved_at`, `closed_at` | timestamptz null | 为空表示 not reached; set together with the state |
| `created_at`, `updated_at`, `version` | | audit; `version bigint NOT NULL DEFAULT 0` |

Constraints and notes: `uk_recruitment_requisitions_number` partial on `requisition_number IS NOT NULL`; index `(org_unit_id, status)` for the scope read, `(requested_by_employee_id)` for "mine"; trigger `tg_recruitment_requisitions_change`: legal edges only (`DRAFT`→`SUBMITTED`, `SUBMITTED`→`APPROVED`, `SUBMITTED`→`CLOSED`, `APPROVED`→`CLOSED`), the descriptive columns change only while `DRAFT`, a `CLOSED` row never changes, delete only a `DRAFT` (registry mutability `CONDITIONAL_UPDATE`). Names are checked against the 63-byte limit by the R1 record.

- **Volume:** 211 legacy instances in about five years: tens per year. No partition.
- **Classification (drafts; Q-12: `CONFIDENTIAL` is register level `SENSITIVE`, L3, where pay appears):** no pay figure is stored (section 9 B-3), so every column is `INTERNAL` with mark `NONE`; the requester's employee id is a surrogate id like in other workforce-owned tables, not a name; `description_text` is free text and limited to a job description (rule on the form). If B-3 adds a salary range or budget, that column is `SENSITIVE` and PI-marked per the payroll record's pattern and this table gets a new sign-off.
- **Retention (the user's item, B-2):** recommendation `PERMANENT` as a business record without personal data; the data-design gate cannot pass the R1 schema until the user answers.
- **Lineage:** `NEW`. Whether the 211 legacy requisitions are imported is B-4; ADR-0085 says old applicant data follow ADR-0036 and ADR-0037 and need retention first.
- **Registry (drafts):** role `AGGREGATE_ROOT`, aggregate `RecruitmentRequisition`, mutability `CONDITIONAL_UPDATE`, writers `organization.recruitment`.
- **Analytical facts:** none (the two events carry ids only).
- **Baseline, target, transition, rollback:** baseline = no recruitment schema, no table, no code (ADR-0085, verified 2026-11-04); target = the table above; transition = migration only, no data; rollback = drop the schema while empty.

## 9. Business questions (options and recommendation)

Rule from the user (2026-11-05): where a regulation is explicit and company practice differs, the user decides; otherwise follow the explicit regulation, else current legacy practice. Statute is silent on internal requisitions; the items below are company practice.

| # | Question | Options | Recommendation and rule applied |
|---|---|---|---|
| B-1 | The card and `planned.yaml` say the approval is inside the aggregate; ADR-0085 says one `platform.approval` node | (a) follow ADR-0085; (b) keep the stale wording and build a private approval | (a). Not a business question: the user's own later decision wins, the cycle reason is gone (section 1). Lead only needs to confirm that the SSOT card fixes the wording. |
| B-2 | Retention and classification of the requisition table (gate: the user's item) | (a) `PERMANENT`, `INTERNAL`; (b) a fixed number of years | (a): a business record without personal data, tens of rows a year; ADR-0043 R-2 bans permanence only for personal-information tables. Needs the user's answer before the R1 schema can pass. |
| B-3 | Does a requisition carry a salary range or budget? | (a) no; (b) yes | (a) for v1: the closeout command parameters list none, and pay would make the table `SENSITIVE`. **Legacy form fields unverified** (B-4): if the 17-field form has a salary field and the user wants it kept, the user decides (pay data). |
| B-4 | Import the 211 legacy requisitions? Read the legacy form's field list first? | (a) no import, history stays in the legacy archive; (b) import closed history as `CLOSED` rows | (a): a requisition is closed business history with no consumer in the new system; practice (ADR-0036/0037) decides. For B-3 a **field-name and count only** read through the registered forensic entry (`docs/ops/forensic-entry.md`; no row values) is enough; I have not run it here (it needs the user's local config, and nothing in this spec depends on it). |
| B-5 | The approver when the org unit has no head (AR-12, closeout "待 V-18") | (a) the head's superior; (b) HR | (a) as the closeout rule says; the question V-18 is open in the closeout and the user has not answered it (**unverified** whether it was). Default stays (a) until answered. |

## 10. Slice plan (cards for lead to open; each names its prerequisites)

| Slice | Content | Prerequisites |
|---|---|---|
| R0 | Workforce api: a position lookup (`ACTIVE` check) beside `OrgDirectory`; glossary and SSOT wording updates (AGG-65 BC and approval line, term `bc`) | this spec merged; ADR-0085 is adopted |
| R1 | Schema `organization_recruitment` + the one table + registry + classification + module edges + `NamingSurfaceTest`/`bcs.yaml` registration + document type in `platform.numbering` | data-design sign-off record (one-design, after B-2 is answered); R0 position lookup not needed for the schema |
| R2 | `SubmitRequisition`, approval start and result handler, `WithdrawRequisition`, `CloseRequisition`, events, permissions, read API with org-unit scope | R1 merged; R0; whether `ApprovalStartRequest` takes a resolved approver (C-2) |
| R3 | List and detail endpoints, "mine" and "to approve" views | R2 |
| Next card (not this one) | applicants, applications, interviews, offers, HIRE hand-off, the "recruiting" state | R2; applicant retention value from the user (ADR-0085 decision 5) |

Order: R0 and R1 in parallel (R1's sign-off waits for B-2), then R2, R3.

## 11. Done-when (for the test engineer)

One test each (names are intents). Node 001 of the golden pack is covered by items 1 to 4 once the pack's expected values are reviewed (**unverified**: the pack is not in the repo).

1. A requester submits a requisition for an `ACTIVE` position and a valid org unit: it becomes `SUBMITTED`, gets a number, and one approval process exists for the org unit head.
2. Submitting with a disabled or deleted position, an unknown org unit or `requestedCount` 0 is refused and nothing is written; the same requisition id submitted twice returns the first result.
3. The approver approves: the requisition is `APPROVED` and `RequisitionApproved` is raised once; the approval result delivered twice changes nothing; if the requester is the head the process goes one level up (AR-11).
4. The approver rejects: `CLOSED` with `REJECTED`; the requester withdraws a `SUBMITTED` requisition: the process is cancelled and the requisition is `CLOSED` with `WITHDRAWN`; withdrawing after approval is refused.
5. `CloseRequisition` on an `APPROVED` requisition gives `CLOSED` with the given reason; closing a `CLOSED` one changes nothing; closing a `SUBMITTED` one by this command is refused (it uses withdraw or the approval result).
6. A requisition for a headcount larger than any establishment is accepted (Q26: no headcount check).
7. The database refuses a `CLOSED` row's update and a delete of a non-draft row (trigger), and an update of descriptive columns after submit.
8. A caller outside the org-unit scope lists zero rows and gets 404 on the detail; a caller with no resolved scope sees nothing.
9. Module guard: `organization.recruitment` depends only on the listed contexts, and workforce does not depend on it.
10. Both events carry ids only and no description text.

## 12. Counter-examples invited

- A case where the approver cannot be resolved with the workforce api alone (a requisition for a position in an org unit that is being merged or closed).
- A flow where the approver must be chosen by the requester (not by the org structure); that changes C-2.
- A reason to keep the approval inside the aggregate other than the workforce cycle (B-1).
- Any legacy requisition field that drives a downstream step (budget, headcount source, channel); it would move B-3 and the table.
