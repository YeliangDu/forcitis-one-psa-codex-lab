# organization.workforce: labour contract and separation request (design, #1417)

- Card: #1417. Author: one-design, 2026-11-05. Base: origin/main.
- Status: proposal. Basis: ADR-0079 (section 3 item 3; section 5 invariants 5, 9, 10, 11, 13; capabilities C6, C18), ADR-0086 and `employment-relationship.md` (relationship ids, per-relationship SEPARATE; #1372/#1401), ADR-0068 (article 1: personnel commands, item 5 last working day rule), ADR-0051 decision 7 item 4 (single-level approval in workforce) (single-level approval inside workforce), ADR-0046 section 8 (no derived date columns).
- Data design: signed for WC0 in `docs/data/reviews/workforce-contract-separation-wc0.md` (2026-11-05). Where that record differs from section 8 (no `uk_employment_contracts_probation_once`; the union and prohibited-circumstance check is four columns, not "three" in 4.2, Q-9 and 8a; added actor and source columns), the record governs.
- Evidence marks: **verified** = read on origin/main today. **unverified** = from memory of statute or waiting on a forensic read. Golden pack `HCM_Golden_Design_Pack_R002` (nodes 005, 034, 068) is evidence only, `NOT_REVIEWED`, not a requirement. Legacy values are not available yet (the forensic entry has not run); every place that needs them says so.
- Rule from the user (2026-11-05): where an explicit regulation differs from company practice the user decides; otherwise follow the explicit regulation; where none is explicit follow the current legacy practice.

## 1. The one thing decided here

How workforce records (a) the labour contract with its three separate dates and (b) a separation as a request that is approved before the SEPARATE command, with request, approval, last working day and relationship end kept as separate facts. Not here: recruitment, onboarding tasks, probation evaluation, talent (other gaps), payroll amounts of any compensation, handover as a workflow (ADR-0079 invariant 11), code.

## 2. Verified facts

| Fact | Where |
|---|---|
| Labour contract AGG-67 is planned: probation and term; states draft → in approval → approved → signed and effective → terminated (reason); single-level approval inside workforce; commands submit, approve, reject, sign, terminate; events `劳动合同已签订 / 已终止` analytical; INV-14 probation within the statutory cap | `planned.yaml` AGG-67; `rules.yaml` INV-14 |
| Glossary already has `employment_contract` (劳动合同), `separation_request` (离职申请, entity), `expected_separation_date` | `terms.yaml` |
| Separation request approval is an AGG-62 command, single level, not through `platform.approval`; commands apply for, approve, then SEPARATE ("办理离职") | `planned.yaml` AGG-62; ADR-0051 decision 7 item 4 (single-level approval in workforce) |
| Contract: formed before or after the working start, and when later, within one month; effect, renewal or change, termination; probation is a clause; at most one effective contract per person and employer on a day; unsigned for one month prompts HR; interns, returned retirees and dispatch are not subject to the prompt | ADR-0079 section 3 item 3, section 5 invariant 5 |
| One probation per person and employer (art. 19, 83). Not set: after unpaid leave or long sick leave, after a dismissal reversed by ruling, and on a cross-entity transfer (lead default, the user may change it). **Allowed with a warning, never blocked**: a re-hire after the separation was settled (the warning names the earlier service with the same employer and the local-practice risk) | ADR-0079 section 5 invariant 13 |
| Separation handover is **not** a process; only a "work handover completed on" date on the separation record, read by payroll as the payment time of the economic compensation | ADR-0079 section 5 invariant 11 (Q3 in section 8) |
| HR enters the **last working day**; the SEPARATED version takes effect the next day at business-zone midnight (user ruling 2026-10-21) | ADR-0068 article 1 item 5 |
| Already-registered, not yet effective separations are queried by downstream through the workforce read API; no new event | ADR-0079 section 2, events note |
| A relationship exists from HIRE command entry, including a future-dated HIRE; a withdrawn HIRE leaves a void relationship | `employment-relationship.md` section 5 |
| Contract signed, changed and terminated events wake payroll for the social insurance basis; **employer change is not a contract event** (the employer belongs to the relationship and never changes) | ADR-0079 section 4 events table, with the employer-change item removed by ADR-0086 decision 7 |
| Legacy: separation request is the A8 form `formmain_0348` (expected separation date `field0031`, `datetime` without zone; only finished and agreed requests are read) | `docs/business/rules/workforce-history-rules.md` (one-ops read, 2026-10-15) |
| What `field0031` means (last working day, or first day off), which separation reasons the A8 form offers, whether the legacy has a contract table at all | **unverified**, waits for the forensic read |
| **Employer-initiated termination:** the basis is recorded; a unilateral dismissal also needs confirmation that the seven prohibited circumstances of art. 42 were checked and the union was told beforehand, with the union's opinion and the result of the written notice recorded; the record holds no health or pregnancy information | ADR-0079 section 5 invariant 10 (user answer Q4) |
| Statute used below (Labour Contract Law articles 10, 14, 19, 35, 36, 37, 44, 46, 50, 82; Implementing Regulation 5 to 7, 10) | `docs/architecture/frameworks/notes/labor-contract.md` records the concepts; article numbers beyond that note and the probation tiers are **unverified** against the original text (public material is not in the repository) |

## 3. Options and choices

| # | Choice | Options | Decision |
|---|---|---|---|
| C-1 | When a contract is registered relative to the relationship | (a) the contract needs an existing relationship (created at HIRE entry, may be future-dated); (b) a contract may exist for a person who has no employee yet (candidate id); (c) a pre-employment offer object | **(a)**. Golden 005 needs a contract signed three days before the start while the employee count stays 0: with (a) the HIRE is entered with its future effective date, the relationship exists, and the in-service count is still 0 until the effective date (ADR-0068 item 8). (b) and (c) need the recruitment design, which is another card. A withdrawn HIRE voids its contract drafts. |
| C-2 | The three dates | store all three on the contract; or store two and read the third | **Store two, read one.** `signed_on_date` and `term_start_date` (the agreed start) are contract facts. The **actual start** is the relationship's first in-service day and is read (`docs/rules/data-design-checklist.md` D1 and the ADR-0046 section 8 precedent: no duplicated or derived date). The value already exists as `employee_versions.employment_start_date` (with `employment_start_basis_kind`); the read uses that column, not a second source. Golden 005's "three separate fields" holds as three separate readable values. |
| C-3 | Arrangement vs request | a new "separation arrangement" aggregate; or extend the registered `separation_request` | **Extend `separation_request`** (registered name). It becomes a per-relationship entity of the Employee aggregate that carries the basis, the four dates and the handover date. No second name. |
| C-4 | Link to SEPARATE | (a) SEPARATE requires an approved request; (b) optional link in the first release; (c) never linked | **(a), decided by the user 2026-11-05 (Q-3: intent fragments 151224 / 151312, lead's)**: every SEPARATE, from the first release, names an `APPROVED` separation request; there is no request-less direct-record path, and an HR back-dated entry needs a request too. Consequence for implementers: every existing caller and test of SEPARATE gains a request step, and the two system-originated paths are settled in Q-9 (a). |
| C-5 | Reason representation | free text; registered selection list | **Selection list** (`separation_basis_key`) plus an optional length-limited note; the key set is configuration (Q-1). Free text alone cannot feed the economic compensation decision or the proof certificate. |
| C-6 | Events | new events for requests; none | **None for the request** (ADR-0079 section 2: downstream reads). **Contract:** `LabourContractSigned`, `LabourContractTerminated` (analytical, ids only; payroll and social insurance read the contract through the api, the events only wake them). The events yaml entries come with the implementing slice (tier=code: a yaml change alone fails `EventCatalogueTest`). |
| C-7 | Contract amendments (Labour Contract Law art. 35) | row-level amendment history now; later | **Later slice.** An amendment is a new written document; the first release records renewal as a new contract linked by `renews_contract_id`, and a variation as terminate-and-renew only if HR confirms (Q-8). |
| C-8 | Probation checks | free field; computed rule | The cap (INV-14) and "once per person and employer" are enforced at signing; the cap table is a code-level statutory constant with the article cited, not a rule-version row (it changes only by legislation). |

## 4. Model

### 4.1 Labour contract (AGG-67, `employment_contract`) inside the Employee aggregate

Per employment relationship (the employer is the relationship's; ADR-0086 decision 5: social insurance follows the relationship's employer; the contract carries no employer column). Applies to formal employees only; interns, returned retirees and dispatched staff have no contract here (ADR-0079 section 3 item 3 and section 5 invariant 5).

Fields (column names are drafts for the data-design gate):

- `employment_contract_id`, `employment_relationship_id`, `employee_id` (composite FK as the other relationship-bound tables), `contract_kind` (`FIXED_TERM`, `OPEN_ENDED`, `TASK_BASED`).
- `term_start_date` (agreed start), `term_end_date` (inclusive; null = open-ended or task-based, no fixed end date), `signed_on_date` (null = not signed yet), `probation_months_count`, `renews_contract_id` (null = first contract), `contract_file_id` (null = no scan stored; `platform.file`), `status`, `terminated_on_date` and `termination_basis_key` (null until terminated), `version`.
- Wage terms are **not** stored: the contract refers to the compensation profile version in payroll (ADR-0079 section 2, line 106).

States: `DRAFT` → `IN_APPROVAL` → `APPROVED` → `SIGNED` → `TERMINATED`; `IN_APPROVAL` → `DRAFT` on reject; `VOID` when its HIRE is withdrawn or the draft is discarded. "Effective" is a day-level reading (`SIGNED` and the day is within the term and not after termination), not a state, so term expiry needs no job. Approval is single level inside workforce (ADR-0051 decision 7 item 4 (single-level approval in workforce)), by a holder other than the submitter (new rule, mine, SoD).

Commands (→ event; preconditions): `RegisterEmploymentContract` (draft; relationship exists; kind-specific terms), `SubmitEmploymentContract`, `ApproveEmploymentContract`, `RejectEmploymentContract`, `SignEmploymentContract` (→ `LabourContractSigned`; records `signed_on_date`; checks the probation cap and once-only probation; refuses a second `SIGNED` contract overlapping in time for the relationship), `RenewEmploymentContract` (creates the successor draft with `renews_contract_id`), `TerminateEmploymentContract` (→ `LabourContractTerminated`; basis key; date). **The contract ends when the SEPARATE takes effect**, in the same transaction as the effect step (as it closes assignment versions): at entry when the effective date is not in the future, otherwise by the daily effect job. `terminated_on_date` is the last working day (the effective date minus one day). A SEPARATE that is only entered (pending, held by the administrator hold, or withdrawn) leaves the contract untouched. The basis comes from the linked approved request, otherwise a mandatory basis in the command.

Invariants:

1. At most one `SIGNED` contract is in force for a relationship on a day (exclusion constraint over `daterange(term_start_date, coalesce(terminated_on_date, term_end_date), '[]')` where `status IN ('SIGNED','TERMINATED')`).
2. Probation: `probation_months_count` within the tier cap for the term (fixed term under 3 months or task-based: none; 3 months to under 1 year: at most 1; 1 year to under 3 years: at most 2; 3 years or more, or open-ended: at most 6; tiers **unverified** against the original article 19); a probation clause is accepted only on a first contract: `renews_contract_id` is null **and** the relationship has no `predecessor_relationship_id` (an employer change in continuous service sets one; lead default, user may change); a renewal with probation is refused. A first contract after REINSTATE (re-hire by the same employer after the separation was settled) is accepted **with a warning** and is not blocked (ADR-0079 invariant 13). Return from leave and a dismissal reversed by ruling create no new contract (the separation is corrected, not re-hired), so nothing is entered. This is an application check at signing (cross-row), not a unique index.
3. `contract_kind` and `term_end_date` agree: fixed-term has an end date, the other two have none.
4. `signed_on_date` is required from `SIGNED` on; it may be before or after `term_start_date`.
5. A contract of a relationship the person is not employed under (void relationship) is `VOID`.
6. Terminated after the relationship ended: `terminated_on_date` is not after the day before the SEPARATED version's date.

Reads (HR reminders, no events, jobs only if the user wants pushed notices):

- "Signed late or not signed": relationship in service for more than one month (from the **actual start**) with no `SIGNED` contract; after a year the list marks it (statute consequence is a payroll/legal matter, not computed here, Q-7).
- "Expiring": a fixed-term `SIGNED` contract whose term ends within N days with no successor; the remaining case is "lapsed": ended, no successor, relationship still in service.
- "Open-ended eligibility hint": ten years of continuous service with the employer, or two consecutive fixed terms (art. 14, details **unverified**); a hint only (ADR-0079 C6).
- N and the one-month threshold are configuration; N is Q-4.

### 4.2 Separation request (`separation_request`) as an entity of the Employee aggregate

One row per request, per **relationship** (ADR-0086). Several over time (a request may be withdrawn and filed again, or the person may leave twice, golden 068); at most one open per relationship.

Fields: `separation_request_id`, `employment_relationship_id`, `employee_id`, `initiator_kind` (`EMPLOYEE`, `EMPLOYER`, `BOTH`), `separation_basis_key` (selection list, Q-1), `requested_on_date`, `approved_on_date` (null = not decided), `decided_by` (null), `last_working_date` (the day HR fills, ADR-0068), `handover_completed_on_date` (null = not completed; ADR-0079 invariant 11), `agreement_file_id` (null; evidence of a mutual agreement or the written notice), `protected_circumstances_checked_on_date` (null = not an employer-initiated unilateral basis or not yet confirmed), `union_notified_on_date` (null, same meaning), `union_opinion_note` (null; length-limited; no health or pregnancy content), `union_notice_result_note` (null), `note` (length-limited), `personnel_command_id` (null = SEPARATE not entered yet), `status`, `version`.

- The **relationship end boundary** is not a column of the request: it is the SEPARATE command's effective date (`last_working_date` plus one day, ADR-0068 article 1 item 5) once the command is entered, and the SEPARATED version afterwards. This keeps the four facts of golden 034 separate without a derived date. `expected_separation_date` (registered term) remains the name of what the legacy read returns; how the legacy `field0031` maps (to `last_working_date` or to its next day) is **open** until the forensic read.
- States: `REQUESTED` → `APPROVED` → `SEPARATING` (SEPARATE entered, `personnel_command_id` set) → `COMPLETED` (the SEPARATED version took effect); `REQUESTED`/`APPROVED` → `WITHDRAWN`; `REQUESTED` → `REJECTED`. A HELD SEPARATE (administrator hold, ADR-0068) keeps the request in `SEPARATING`; a withdrawn SEPARATE command returns it to `APPROVED`.
- Commands: `RequestSeparation` (申请离职; by the employee through self-service, or by HR for them, source recorded), `ApproveSeparationRequest` (单级; approver is not the requester and is any other group user holding the approval permission and the employee's data scope, for example the parent entity's HR or the department head, so an entity whose only HR filed the request is not deadlocked (Q-10); records `approved_on_date`; for a unilateral employer basis it is refused until the three union/prohibited-circumstance fields are recorded, ADR-0079 invariant 10), `RejectSeparationRequest`, `WithdrawSeparationRequest`, `RecordHandoverCompleted` (sets the date once; a later correction is a new value with an audit row), and the existing SEPARATE, which always requires a linked `APPROVED` request and `effective_date = last_working_date + 1 day` (C-4, W-R4).
- Dates rule: `requested_on_date` ≤ `approved_on_date`; `last_working_date` ≥ `requested_on_date` unless HR records a back-dated separation (HR files the request on the person's behalf and an approver other than HR's filer approves it; the dates are the real ones, or the entry day when unknown, and the source says so; a back-dated separation needs a request too, Q-3); no date is derived from another one.
- Per-relationship effects: a request on one relationship leaves the other relationship in service (this is the concurrent-relationship case of `employment-relationship.md` acceptance 4, not golden 068, in which ER-001-A had ended on 2027-01-01 and ER-001-B is the only relationship in service); the person-level `EmployeeSeparated` still fires only when the last relationship ends (`employment-relationship.md` 6.1).
- What the request does **not** hold: any compensation amount, the certificate, the file transfer, the social insurance reduction (those belong to payroll and their own contexts, subscribed to the separation event, ADR-0079 invariant 11).
- Visibility: an unfinished request means "someone is leaving" and is not public; reads need the HR separation permission and are fail-closed by the same employee data scope as the personnel commands; the employee reads their own request.

### 4.3 How both relate to the existing commands

| Existing | Effect |
|---|---|
| HIRE (relationship created at entry) | Anchor of the contract draft and of the **actual start**; withdrawing it voids the drafts. |
| REGULARIZE | Probation end is read from the contract's `probation_months_count` for the reminder; REGULARIZE itself is unchanged. |
| REINSTATE (same employer, same relationship) | A new first contract (`renews_contract_id` null); a probation clause is accepted with a warning, not blocked (invariant 2). |
| SEPARATE | Always linked to an approved request (C-4, Q-3); ends the open contract in the same transaction. |
| CHANGE_LEGAL_ENTITY (attribution) | No effect on the contract: the employer is the relationship's. |

## 5. Permissions and data scope

New permission codes follow the catalogue shape and are fixed per slice: contract register/submit, contract approve (not the submitter), contract read (HR), separation request (self and for others), separation approve (not the requester), handover record, separation read. The employee reads their own contract and request. Scope: both tables carry the employee, so reads filter by the same employee data scope as personnel commands; an id outside scope is 404 (fail-closed). `contract_file_id` and `agreement_file_id` are read through `platform.file` with its own authorisation.

## 6. Module edges

`organization.workforce` owns both. `→ platform.file` (api, references only). Consumers (not built): `organization.payroll` reads the contract and the separation request through workforce read api (insurance basis, compensation timing); `organization.leave` is unchanged (it reads the relationship only); `platform.identity` keeps reacting to the separation event. No `platform.approval` edge (single-level approval is in workforce). Forbidden: payroll or leave reading workforce tables.

## 7. RULE ids

Reused: INV-14 (probation cap), the per-relationship rules of `employment-relationship.md`. New, to be numbered by the SSOT card: W-R1 one signed contract in force per relationship per day; W-R2 probation only on a first contract of a relationship without predecessor, warned (not blocked) after a re-hire by the same employer; W-R3 a separation request needs an approver other than its requester; W-R4 every SEPARATE needs an approved request and `effective_date = last_working_date + 1 day`; W-R5 at most one open separation request per relationship; W-R7 a unilateral employer separation is approved only with the art. 42 check and the union notice recorded (ADR-0079 invariant 10); W-R6 the open contract ends when the SEPARATE takes effect, in that transaction, and not before.

## 8. Data design (draft; the gate signs per schema card, signer one-design)

Schema `organization_workforce`. Cross-BC ids carry no foreign key; the relationship and employee ids use the composite FK already used by `assignment_versions`.

| Table | Key points |
|---|---|
| `employment_contracts` | the fields of 4.1; `ex_employment_contracts_in_force_period_overlap`; `uk_employment_contracts_probation_once` partial; CHECKs for kind/end agreement, status columns (`signed_on_date` not null from `SIGNED`; `terminated_*` both or neither); grain: one row per contract; append-only except status/version steps (no delete) |
| `separation_requests` | the fields of 4.2; partial unique one open request per relationship; CHECK dates order, `personnel_command_id` null unless `SEPARATING`/`COMPLETED`; grain: one row per request |
| configuration `separation_bases` | key, `initiator_kind` default, `is_employer_initiated`, display name, active flag; Q-1 is answered; values wait for the forensic read |

Classification and retention are decided (user 2026-11-05, Q-6 (b): retained ten years after the person's last separation; contract content `CONFIDENTIAL`, termination basis `SENSITIVE`): both tables hold personal information; termination basis can reveal disciplinary history (treat as sensitive). Registry drafts and lineage ("new"; legacy import is a later slice) go in the sign-off record per slice. Rows are never deleted by the application; the existing employee purge function after ten years is the only deletion route (ADR-0068 item on `purge_employee_history`, to be extended in the skeleton slice).

## 9. Golden nodes (evidence only) and what is not adopted

| Node | Command and observable result in this spec | Pack assumption not adopted, and why |
|---|---|---|
| 005 (sign 01-02, agreed start 01-05) | `RegisterEmploymentContract` → `SignEmploymentContract` on the pending relationship; the contract reads `signed_on_date` 2026-01-02 and `term_start_date` 2026-01-05; the in-service employee count on 01-02 is 0; after the HIRE effective 01-05 the relationship read gives the actual start 2026-01-05, three separate readable values | "Three fields on the contract": the actual start is not stored there (C-2, ADR-0046 section 8) |
| 034 (request 12-10, last working day 12-31) | `RequestSeparation` + `ApproveSeparationRequest` (both 12-10 is a fixture assumption, the pack only gives the last working day 12-31; basis resignation, `last_working_date` 2026-12-31); the request reads four separate facts: requested 12-10, approved 12-10, last working day 12-31, and, once SEPARATE is entered, its effective date 2027-01-01 | "启动交接": no handover workflow or task list (ADR-0079 invariant 11); only `handover_completed_on_date`, recorded when HR confirms |
| 068 (second round, mutual agreement, last working day 02-29) | `RequestSeparation` on ER-001-B with `separation_basis_key` = mutual agreement and `agreement_file_id`; own row, basis different from the first (ER-001-A); the first request is unchanged; the SEPARATE effective 2028-03-01 (leap day) | "金额及待遇仍需独立核定": no amount on the request; compensation belongs to payroll. "适用依据已确认": the system stores the basis key and evidence, not a legal opinion |

## 10. Business questions for the user (answered 2026-11-05 where marked; the spec decided none of them)

Per the user's rule, an item goes to the user only if the statute is silent in an unsettled way or practice may differ; each has a recommendation.

| # | Question | Options | Recommendation |
|---|---|---|---|
| Q-1 | The separation basis list and which are employer-initiated | (a) statute-based list: resignation with notice (art. 37), resignation in probation, employee terminates for employer fault (art. 38), mutual agreement (art. 36), employer fault dismissal (art. 39), no-fault dismissal (art. 40), economic layoff (art. 41), contract expiry (art. 44), death of the person or employer dissolution (art. 44 items, statutory, no one files them but a request is still created, Q-3), retirement, other statutory; (b) copy the legacy form's list | (a), HR checks it against the legacy form after the forensic read; only differences go to the user. **Answered by the user 2026-11-05: as recommended.** |
| Q-2 | Notice period (30 days; 3 days in probation, art. 37; **unverified**): refuse a shorter last working day, or warn | refuse / warn / ignore | warn only; HR may approve a shorter period by agreement. **Answered by the user 2026-11-05: as recommended.** |
| Q-3 | Must every SEPARATE have an approved request (C-4) | mandatory now / optional first, mandatory later / never | optional in the first release, mandatory after the legacy flow is read; HR keeps a "record directly" path that creates the request with requested = approved = entry day. **Answered by the user 2026-11-05: (a) mandatory from the first release**, not the recommendation: no request-less path, back-dated entries need a request too (C-4). |
| Q-4 | Reminder thresholds: days before expiry; the unsigned threshold | 60 / 30 days; the statute gives one month for unsigned | expiry reminders at 60 and 30 days; unsigned at one month (statute); legacy values replace these if the forensic read finds different practice and the statute is silent. **Answered by the user 2026-11-05: as recommended.** |
| Q-5 | Contract kinds modelled | fixed-term, open-ended, task-based only / also part-time contracts | the three (my modelling, matching the statute's three types); part-time and dispatch stay out (ADR-0079 section 5 invariant 5). **Answered by the user 2026-11-05: as recommended.** |
| Q-6 | Retention and classification of both tables | (a) PERMANENT / (b) ten years after the person's last separation, aligned with the employee purge / (c) only the statutory two years after termination for the terminated contract text (art. 50; same as ADR-0079 invariant 12) | (b); contract content `CONFIDENTIAL`, termination basis `SENSITIVE`. The statute fixes two years minimum for the proof (art. 50), so (b) is stricter. **Answered by the user 2026-11-05: (b)** (content `CONFIDENTIAL`, termination basis `SENSITIVE`). |
| Q-7 | Consequence of an unsigned contract beyond one month (double wage, open-ended after one year) | system computes and posts / only flags in the reminder list / nothing | flag only; payroll or legal acts on the list. **Answered by the user 2026-11-05: (b) flag only.** |
| Q-8 | Contract variation (art. 35): is a variation recorded as terminate-and-renew, or as an amendment record | terminate and renew / amendment table | amendment table in a later slice; until then terminate and renew. **Answered by the user 2026-11-05: as recommended.** |
| Q-9 | Do the two system-originated SEPARATE paths also need a request: the employer change in continuous service (SEPARATE on the old relationship plus HIRE with predecessor) and legacy-migrated separations | (a) both create a request automatically with a fixed basis (`EMPLOYER_CHANGE`, `MIGRATION`) and `requested_on_date = approved_on_date`, approver recorded as the system / (b) both require HR to file and approve one like any other / (c) both are exempt | Raised after Q-3 (a). **Answered by the user 2026-11-05: (a)** (lead's Q9/Q10 fragment of 2026-11-05): the employer-change SEPARATE gets a system-created request with basis `EMPLOYER_CHANGE`; migrated separations get one imported request per person with basis `MIGRATION`. A system-created request has source `SYSTEM`, is written directly in state `APPROVED` by the SEPARATE command or the import itself (no `RequestSeparation` / `ApproveSeparationRequest` call), has `requested_on_date = approved_on_date`, and records the system actor as requester and approver; W-R3 applies to human-filed requests only, so it is not violated. `EMPLOYER_CHANGE` and `MIGRATION` are system-only keys of `separation_bases` (HR cannot pick them; not employer-initiated for the art. 42 check; the three union fields are not required). They still pass W-R4 (`last_working_date` is given by the command or the import), and if an open request already exists on the relationship the system path is refused under W-R5 until HR finishes or withdraws it. Implementers add no other special case. |
| Q-10 | Who approves a separation request when the entity's only HR filed it | (a) any other group user holding the approval permission (for example the parent entity's HR or the department head) / (b) a mandatory second approver configured per entity | (a). **Answered by the user 2026-11-05: (a)**: never self-approval (W-R3), the approver still needs the employee's data scope, and no mandatory per-entity second approver. |

## 11. Slices (card order)

| Slice | Content | Waits on |
|---|---|---|
| WC0 | Data-design sign-off for `employment_contracts`, `separation_requests`, `separation_bases` (design). **Signed 2026-11-05**, `docs/data/reviews/workforce-contract-separation-wc0.md` | this spec merged; #1401 relationship ids (both done) |
| WC1 | Skeleton: migration, domain classes, repositories, registry, classification, purge extension | WC0 |
| WC2 | Contract commands, reads and reminder lists | WC1 |
| WC3 | Separation request commands and the mandatory SEPARATE link (every existing SEPARATE caller and test gains a request step) | WC1 |
| WC4 | Integration: SEPARATE ends the contract; `LabourContractSigned/Terminated` events; payroll read api; self-service views | WC2, WC3 |
| WC5 | Legacy import of requests and contracts | forensic reads (`formmain_0348` semantics, any contract store) |

## 12. Done-when (for the test engineer)

1. A contract registered on a future-dated HIRE's relationship reads `signed_on_date` and `term_start_date` as given while the in-service employee count on the signing day is 0; after the HIRE takes effect the relationship read returns the actual start, separately from both dates (golden 005).
2. Withdrawing the HIRE turns the contract in `DRAFT`, `IN_APPROVAL` or `APPROVED` to `VOID` (a status step, no row is deleted); a `SIGNED` contract blocks the withdrawal until HR terminates or voids it explicitly; a signed contract on a void relationship is refused.
3. Signing a second contract overlapping the first in force for the same relationship is refused (W-R1); a renewal starting the day after the first term ends is accepted.
4. Probation above the tier cap for the term is refused at signing; a renewal with probation is refused; a first contract with probation on a relationship with a predecessor is refused; a first contract after REINSTATE with probation is accepted and returns the warning (W-R2).
5. `FIXED_TERM` without an end date, or `OPEN_ENDED`/`TASK_BASED` with one, is refused.
6. A relationship in service for more than one month from its actual start with no `SIGNED` contract appears in the "not signed" list; a returned retiree, an intern or a dispatched person does not.
7. The approver of a contract or of a separation request cannot be its submitter or requester (W-R3).
8. `RequestSeparation` + `ApproveSeparationRequest` store requested, approved and last working day as three separate values; no column holds the relationship end boundary; once SEPARATE is entered the request links to the command whose effective date is `last_working_date + 1 day` (golden 034).
8a. Approving a request with a unilateral employer basis without the three recorded fields is refused; there are no health or pregnancy columns, and the free-text notes are length-limited with content governed by the HR entry rules (not testable as "never").
9. A SEPARATE with no linked request, a request that is not `APPROVED`, or an effective date other than `last_working_date + 1 day`, is refused (W-R4); a back-dated SEPARATE is accepted only through a filed and approved request; a second open request for the same relationship is refused (W-R5).
10. (Golden 068) A second-round request on the only in-service relationship ER-001-B carries its own basis, differs from the first round's request (which stays unchanged), and has last working day 2028-02-29 with the relationship valid until 2028-03-01 exclusive; `EmployeeSeparated` fires when it ends.
10a. (Concurrent relationships, per `employment-relationship.md` acceptance 4) A request on one of two in-service relationships leaves the other in service with its contract untouched; `EmployeeSeparated` fires only when the last one ends.
11. When a SEPARATE takes effect it terminates the relationship's open contract in the same transaction with `terminated_on_date` = last working day (W-R6); a failed effect step leaves the contract signed. A SEPARATE with a future date, a held one and a withdrawn one each leave the contract `SIGNED`.
12. `RecordHandoverCompleted` sets the date once; payroll can read it through the workforce api; no handover task or workflow object exists.
12a. A system-created request (`EMPLOYER_CHANGE`, `MIGRATION`) is written `APPROVED` with source `SYSTEM` and the system actor as requester and approver, passes W-R4, is refused under W-R5 when another request is open, and cannot be picked for a human-filed request; a human-filed request still obeys W-R3 (Q-9, Q-10).
13. A caller outside the employee data scope gets 404 for another person's contract or request; the employee reads only their own; HR without the separation permission cannot list unfinished requests.
14. Events carry ids only (no basis, no dates of a pending separation); no new event exists for the request.
15. Module edges: payroll and leave do not read workforce tables; workforce does not depend on `platform.approval` for these two flows (architecture tests).

## 13. Out of scope

Recruitment and offer objects, onboarding task lists, probation evaluation, handover workflow, certificate generation, file transfer and social insurance steps, any compensation amount, non-compete clauses (a separate agreement, later), dispatch and part-time contracts, code and migrations.

## 14. Counter-examples invited

If the forensic read shows the legacy keeps contracts for people who have no relationship record yet (for example signed before any HIRE entry exists), C-1 (a) is revisited and the candidate-bound option (b) is built with recruitment. If A8's `field0031` is the first day off, the mapping in 4.2 shifts by one day but the model does not change. If HR needs several simultaneous open requests on one relationship (for example employer and employee both initiating), W-R5 is relaxed to one per initiator.
