# Thin accounting spine — sign-off spec (#1402)

- Card: #1402. Author: one-design, 2026-11-05. Base: origin/main.
- Status: proposal. Basis: ADR-0087 decisions 2, 3, 5.1 and 5.2 (adopted, PR #1383), ADR-0016 (posting pipeline), ADR-0022 decisions 2 and 3 (two books, two charts, mapping; dimension set), ADR-0045 (closed periods are final), ADR-0046 (period 13).
- Evidence marks: **verified** = read on origin/main today; **unverified** = not checked or waiting on evidence (#1373).
- Nothing here is code. Aggregate numbers are those of `planned.yaml` (AGG-77, 81, 82, 83, 85; AGG-96 stays out).

## 1. What this spec decides, and what it does not

Payroll ends at posting (ADR-0087 decision 3): each approved month writes **posting facts** into `operations.facts`. For that to work, five things must exist first (ADR-0087 decision 2): periods 1 to 12, the posting pipeline, and, in `accounting.ledger`, the books, the chart-of-accounts versions and the cash/accrual account-mapping versions, the last three limited to the accounts payroll uses.

In scope: those five. Out of scope (ADR-0087 decision 3): vouchers, closing commands, reports, U8C push, period 13 (AGG-96), accounts payroll does not use.

**Finding for lead, before the plan (section 9, Q-1).** The posting pipeline is account-free by decision (ADR-0016 decision 3: business BCs do not know accounts). The three ledger items are read only by voucher generation, which is outside the HR batch. So inside the HR batch **nothing reads** the books, the chart versions or the mapping versions at run time; they are reference data that must exist and be correct before payroll goes live and before voucher generation is built. This design builds them as ADR-0087 decided; whether they ship in the HR batch or one step later is the user's call, not mine. The slice plan makes them independent of the facts slices so either order works.

## 2. Verified facts

| Fact | Where |
|---|---|
| No `accounting` or `operations` package exists in the backend; contexts `operations.facts`, `accounting.period`, `accounting.ledger` are `planned`, schemas `operations_facts`, `accounting_period`, `accounting_ledger` | `contexts.yaml`, `ls backend/src/main/java/com/forcitis/one` |
| Legal entity (AGG-60) is built in `organization.workforce`; event `LegalEntityRegistered` exists | `events/workforce.yaml:4` |
| AGG-81 depends on AGG-96 and AGG-96 reads AGG-81; ADR-0087 5.2 breaks the loop: periods 1 to 12 only, period 13 "not enabled" | ADR-0087 |
| AGG-85 depends on AGG-82, AGG-82 on AGG-83; all `accounting.ledger` | `planned.yaml:1317-1365` |
| Payroll (AGG-73) approves once, submits one candidate, then waits for `BusinessFactAttributed` to become "posted" | `planned.yaml:1136-1142` |
| Closed periods never reopen; late items land in the current open period tagged with the original period | ADR-0045 decisions 1, 2 |
| U8C evidence: payroll liability account 2211 应付职工薪酬; 5201, 5301, 6601 and 6602 carry an employee-cost breakdown (**evidence only, not the scope list**); the cash-basis code shape 4-2-2-2-2-2 is ADR-0022 decision 2 | `docs/legacy/general-ledger/practice.md` lines 81, 90; ADR-0022 decision 2 |
| Which accounts and dimensions the payroll, social-insurance and tax vouchers use, per entity and month | **unverified**: #1373 is open; this spec invents no account list |

## 3. Options and choices (technical; mine to choose)

| # | Choice | Decision |
|---|---|---|
| C-1 | Does the fact carry accounts? | No (ADR-0016 decision 3). A fact line carries `side`, a **component key** and an amount; the component key is a closed, registered key per fact type (table `posting_components`), seeded by the producer's slice. Account choice per component is the voucher rule (D17), a later batch. |
| C-2 | Payroll grain | One fact per legal entity and payroll month (AGG-73 grain), lines per component and department. **No employee id on payroll facts**: personal pay stays in payroll; facts hold aggregates. Whether U8C salary vouchers use the person dimension is **unverified** (#1373); if it does, the user decides (personal data), see Q-5. |
| C-3 | Where the open account list lives | As data, not code: component keys in `posting_components` rows, accounts in `chart_accounts` rows. The pipeline slice therefore does not wait for #1373; only the seed rows do. |
| C-4 | Dimensions on fact lines | ADR-0022 decision 3 fixes the set (legal entity, department, project and contract, counterparty, person, R&D project, VAT input type) as a code enum. The spine's table signs `department_id` only; each further dimension column is added, additively, by the first slice that needs it (waiting on #1373 for payroll). |
| C-5 | Period rows | `OpenFiscalYear` creates the twelve monthly rows for one legal entity at once, all `OPEN`. Lazy creation per month was rejected: the gate would then have to create rows on a read path. |
| C-6 | Close commands | Not in the spine (ADR-0087 decision 3). The `status` column carries all three values and the facts gate implements all three outcomes (INV-92); tests put a period into `CLOSING` or `CLOSED` with a repository fixture, the way `standard_hours_versions` tests seed rows. |
| C-7 | Books | Two per legal entity, created when `LegalEntityRegistered` arrives, plus a backfill for entities that already exist. Reference data, no commands (AGG-83: "不计，参照数据"). |

## 4. Aggregates

### 4.1 AccountingPeriod (AGG-81, `accounting.period`)

One row per legal entity, fiscal year and period number. Spine scope: numbers 1 to 12.

- States: `OPEN`, `CLOSING`, `CLOSED` (ADR-0045 decision 1, INV-99). Spine writes only `OPEN`. A `CLOSED` row never returns to `OPEN`; the table has no path for it (trigger, section 8).
- Command: `OpenFiscalYear(legalEntityId, fiscalYear)` — creates 12 rows; refuses a year that already has rows; refuses a year more than one year ahead of the latest opened one (prevents a typo opening 2062) [**choice, mine**]. Event: none (internal).
- Read API (`PeriodGate`, the only way other BCs see periods; INV-92):
  - `PeriodGate.forPosting(legalEntityId, businessDate)` maps a business date to an ordinary period 1 to 12 only (ADR-0046 decision 3.1: a date never reaches period 13) and returns one of: `OPEN(periodId)`, `CLOSING` (caller retries), `CLOSED_ROUTE_TO(currentOpenPeriodId, originalPeriod)`, `NO_OPEN_PERIOD` (the date is in a closed period and the entity has no open period to route to: the candidate is rejected, a finance action opens the year), `NOT_OPENED` (the year has no rows: reject).
  - `PeriodGate.periodStatus(legalEntityId, fiscalYear, periodNumber)` is the "not enabled" lookup of ADR-0087 5.2: returns `OPEN`, `CLOSING`, `CLOSED`, `NOT_OPENED`, or `NOT_ENABLED` for number 13. The spine's pipeline never posts to an explicitly named period, so it does not call this; the closing batch's explicit channel (ADR-0046 decision 3.2) and AGG-96 do.
  - The read is a **same-transaction shared lock** on the period row (`SELECT … FOR SHARE`), so a close cannot slip between the check and the write (ADR-0016 decision 1, INV-92).
- Current open period of an entity = the lowest-numbered `OPEN` period; with no `OPEN` period the answer is `NO_OPEN_PERIOD`. Gaps (a later period open while an earlier one is `CLOSING`) are for the closing batch to define; the spine does not create them.
- Period 13: the `period_number` check allows 1 to 13 so AGG-96 needs no migration, but no row 13 is created in the spine; `periodStatus(…, 13)` answers `NOT_ENABLED`.

### 4.2 Posting (AGG-77, `operations.facts`)

Technical aggregate. Inside: typed posting candidates, fact lines, `PostSeq`, original period.

- Commands (AGG-77): `Post(candidate)` → `BusinessFactAttributed` (pre: INV-93, INV-94); `ReverseAndRepost` → `BusinessFactRestated` (pre: INV-95); `ResubmitRejectedCandidate`; `RecordRejectionDisposition`. The last two serve the closing check "rejections disposed" and are slice F2 (can wait).
- `PostingCandidate` is a **sealed** type in `operations.facts.api` (ADR-0016 decision 1). Spine ships one permitted variant, `PayrollPostingCandidate`, owned by the facts API and filled by the payroll card.
- Candidate: `legalEntityId`, `factType`, `businessDate`, `sourceBc`, `sourceId` (not checked, B-06), `idempotencyKey`, `lines[]` of (`side` DEBIT or CREDIT, `componentKey`, `amount` as `Money`, `departmentId` optional).
- Pipeline order (one transaction): idempotency check → entity known (the legal entity is registered in `organization.workforce`; `UNKNOWN_LEGAL_ENTITY` otherwise) → line validity (component registered for the fact type, amounts positive, currency one per candidate) → balance (Σ debit = Σ credit; INV-93) → `PeriodGate.forPosting` → assign `PostSeq` → write fact and lines → publish `BusinessFactAttributed`.
- Outcomes: `POSTED(factId, postSeq)`; `DUPLICATE(factId)` for a replayed idempotency key (returns the original, writes nothing; INV-94); `RETRY_PERIOD_CLOSING`; `REJECTED(reason)` writes a `posting_rejections` row (reasons: `UNBALANCED`, `UNKNOWN_LEGAL_ENTITY`, `UNKNOWN_COMPONENT`, `PERIOD_NOT_OPENED`, `NO_OPEN_PERIOD`, `INVALID_AMOUNT`). INV-93 "one legal entity" holds by construction: a candidate and all its lines carry one `legalEntityId`. Whether a department belongs to that entity is **not** checked in the spine (unverified whether the workforce model ties a department to one entity); the producer owns that. `RETRY_PERIOD_CLOSING` is never a rejection (INV-92).
- Facts are append-only; a correction is `ReverseAndRepost`: a reversing fact (every line with the opposite side, referencing the original, new `PostSeq`) and a new fact, both landing in the current open period with the original period set when the business date is in a closed period (INV-95, ADR-0045 decision 2). Reversal copies the original lines; it does not recompute anything.
- `PostSeq`: strictly increasing, gap-allowed (a rolled-back transaction may burn a value); a single PostgreSQL sequence is enough because order of commit visibility, not of allocation, is what consumers need, and consumers order by `PostSeq` only within one entity [**choice, mine**; the closing batch revisits it if it needs gap-free order].
- Events (names kept from the vocabulary, wording is one-design/glossary's): `BusinessFactAttributed` (payload: factId, postSeq, legalEntityId, factType, sourceBc, sourceId, idempotencyKey, postedFiscalYear, postedPeriodNumber, originalFiscalYear and originalPeriodNumber optional — ids only, no amounts, so payroll data does not fan out); `BusinessFactRestated` (reversal fact id, original fact id). Consumers in the HR batch: payroll (sets "posted", subscribes by `sourceId`), audit. Delivery at least once; consumers dedupe by `eventId`.

### 4.3 Books (AGG-83), chart versions (AGG-82), mapping versions (AGG-85) — `accounting.ledger`

All limited to the accounts payroll uses; other accounts are added later as new rows in new versions without touching published ones.

- **Book** (AGG-83): reference row, two per legal entity: `ACCRUAL` and `CASH` (ADR-0022 decision 2). No command; created from `LegalEntityRegistered`; a registered entity without books is a defect found by a check, not by a user action.
- **Chart version** (AGG-82; glossary term `chart_of_accounts_version`, already registered), one chain per book. States `DRAFT` → `PUBLISHED`. Commands: `DraftChartVersion`, `PublishChartVersion` (→ event `ChartVersionPublished`, analytical only). Rules: drafter ≠ approver (INV-131; ADR-0022 decision 2 names finance as approver); a published version never changes; the next version takes effect on the first day of a period and, when it touches an account with a balance, carries an opening reclassification (ADR-0022 decision 2; reclassification entries are voucher work, a later batch, so in the spine a version that touches an account **with a balance** is refused: no balances exist before vouchers, so the case cannot arise yet [**choice, mine**; revisit with the voucher slice]). An account referenced by a published mapping or fact component cannot be deleted (INV-103).
- **Account** inside a chart version: code, name, parent, which dimensions it enables (from the C-4 enum), `is_postable`. Cash-basis chart keeps the 4-2-2-2-2-2 code shape (ADR-0022 decision 2); the accrual chart reuses the cash code for common accounts and adds codes only for accrual-only accounts (ADR-0022 decision 2).
- **Mapping version** (AGG-85): rows pair a cash-basis account with an accrual account and a reconcile kind `SAME` or `DIFFERENT` (ADR-0022 decision 2). Commands `DraftMappingVersion`, `PublishMappingVersion` (→ `MappingVersionPublished`, analytical). Published versions add rows only, never edit or delete a row a posting has used (U-27 recommendation, see Q-4). Both account codes must exist in the effective chart versions of their book.

## 5. Permissions and data scope

New permission codes follow `{subdomain}.{BC}:{resource}:{action}` (naming standard); the final catalogue entries are fixed by the slice that adds each endpoint. Proposed:

| Code | Holder | Notes |
|---|---|---|
| `accounting.period:fiscal-year:open` | finance | `OpenFiscalYear` |
| `accounting.period:period:read` | finance, payroll service | gate and list |
| `accounting.ledger:chart-version:draft`, `:publish` | finance (draft), finance lead (publish) | two people (INV-131) |
| `accounting.ledger:mapping-version:draft`, `:publish` | same | same |
| `operations.facts:posting-fact:read` | finance | list and reconcile; aggregates only |
| `operations.facts:posting-rejection:read` | finance, support | |

Posting itself is not an endpoint: the producing BC calls the facts API in process (G36: no producer bypasses the pipeline). Data scope (fail-closed): `fiscal_periods`, `posting_facts`, `posting_rejections` and `ledgers` carry `legal_entity_id` and reads are filtered by the caller's entity scope; `posting_fact_lines` and `posting_rejection_dispositions` are read only through their parent and inherit its scope; chart and mapping versions belong to a ledger and inherit the ledger's entity; `posting_components` is configuration, read by permission only. A rejection for an unknown entity keeps the entity id the caller sent, so it is visible to no entity scope and only to a finance role with the all-entity scope. Payroll facts hold no employee id (C-2) and are `CONFIDENTIAL` (amounts of pay) in the classification file.

## 6. Module edges

Proposed lines for `backend/architecture/module-dependencies.txt` (each new slice adds its own line with the first code):

- `operations.facts -> accounting.period` (api: `PeriodGate`)
- `operations.facts -> organization.workforce` (api: legal-entity lookup)
- `accounting.period -> organization.workforce` (api: legal-entity lookup)
- `accounting.ledger -> organization.workforce` (event `LegalEntityRegistered`, api: lookup)
- `organization.payroll -> operations.facts` (api: submit; event: `BusinessFactAttributed`) — added by the payroll card
- Forbidden and to be guarded: any BC other than `accounting.ledger` reading chart or mapping tables (G35); any BC other than `operations.facts` writing fact tables (G36); `accounting.period` writing nothing but its own tables.

## 7. RULE ids

Existing ids are reused: INV-92 (period gate), INV-93 (balance, one entity), INV-94 (idempotency), INV-95 (append-only, reversal), INV-99 (closed period final), INV-103 (referenced account), INV-131 (drafter ≠ approver). New spine rules, numbered by the glossary/SSOT card (no number is invented here):

- S-R1: `OpenFiscalYear` creates exactly twelve periods for one entity and year.
- S-R2: a legal entity has exactly one `ACCRUAL` and one `CASH` book.
- S-R3: a fact line's component key is registered for the fact type.
- S-R4: a chart version that touches an account with a balance is refused until vouchers exist (section 4.3; temporary).

Glossary: `accounting_period`, `posting_candidate`, `ledger` (账簿; table `ledgers`) and `chart_of_accounts_version` are already registered; new proposed terms are `posting_fact` and `account_mapping_version` (`backend/glossary/proposed/accounting-spine.yaml`). Table names below are database names; the schema card checks them against the glossary (`GlossaryTest`).

## 8. Data design (a design draft, not a sign-off; the gate in `docs/data/data-design-gate.md` applies per schema card)

This section is the design the schema cards start from; it signs nothing. Each schema card brings the Appendix A material and a sign-off record (`docs/data/reviews/`). The gate names one-data as signer (`数据设计：通过（one-data，…）`, row DG-1); the leave L1a record was signed by one-design on lead's acceptance (PR #1404). Which signer applies to these schemas is lead's ruling.  tables below marked **waits** cannot be signed before the named evidence arrives (no conditional pass).

**Schema `accounting_period`**

| Table | Columns (sketch; class words per naming standard 2.4) | Constraints and notes | Signable now |
|---|---|---|---|
| `fiscal_periods` | id, legal_entity_id, fiscal_year (int), period_number (smallint), status, created_at, updated_at, version | `uk_fiscal_periods_entity_year_number`; `ck_…_period_number` 1..13; `ck_…_status` OPEN/CLOSING/CLOSED; no date columns (they follow from year and number, ADR-0046 decision 8.2; period 13 has no dates); trigger: a `CLOSED` row accepts no update or delete (INV-99); legal entity no FK (cross-BC id, B4) | yes |

**Schema `operations_facts`**

| Table | Columns (sketch) | Constraints and notes | Signable now |
|---|---|---|---|
| `posting_facts` | id, post_seq (bigint, from sequence), legal_entity_id, fact_type, business_date, posted_fiscal_year, posted_period_number, original_fiscal_year null, original_period_number null (为空表示 not routed from a closed period; two columns each, ADR-0046 decision 8.3), source_bc, source_id, idempotency_key, reverses_fact_id null (为空表示 not a reversal), created_at | append-only trigger (INV-95); `uk_posting_facts_post_seq`; `uk_posting_facts_idempotency_key` (scoped by entity + source_bc + key, INV-94); `uk_posting_facts_reverses_fact_id` (one reversal per fact); FK to its reversed fact RESTRICT; the period columns have no FK (other BC), `ck_…_original_pair` both original columns set or both null | yes |
| `posting_fact_lines` | id, fact_id, line_no, side, component_key, amount (numeric(19,2)), currency, department_id null (为空表示 no department dimension), created_at | append-only; `fk_…_fact_id` RESTRICT; `uk_…_fact_line_no`; `ck_…_side`; `ck_…_amount_positive`; balance Σ debit = Σ credit is a **deferred constraint trigger** at commit (INV-93), because no single-row CHECK can state it | yes |
| `posting_components` | id, fact_type, component_key, side_hint null, is_active, created_at | `uk_…_type_key`; rows seeded by the producer's slice | structure yes; **rows wait** for #1373 (payroll) |
| `posting_rejections` | id, legal_entity_id, source_bc, source_id, idempotency_key, reason_code, payload_digest, created_at | append-only; payload digest, not the payload (personal and pay data stay out) | yes (slice F2) |
| `posting_rejection_dispositions` | id, rejection_id, conclusion_kind, recorded_by, created_at | append-only; later conclusions supersede earlier ones | yes (slice F2) |

Sequence `posting_fact_post_seq` is registered in the data-access registry as part of `posting_facts`.

**Schema `accounting_ledger`**

| Table | Columns (sketch) | Constraints and notes | Signable now |
|---|---|---|---|
| `ledgers` | id, legal_entity_id, basis_kind (ACCRUAL/CASH), created_at | `uk_ledgers_entity_basis`; reference data | yes |
| `chart_versions` | id, ledger_id, version_no, status, effective_from_fiscal_year, effective_from_period_number, drafted_by, approved_by null (为空表示 not yet approved), approved_at null, created_at, updated_at, version | `uk_…_book_version_no`; `ck_…_drafter_not_approver` (INV-131); `ck_…_approval_columns` (status PUBLISHED ⇔ approved columns set); published rows frozen (trigger) | yes |
| `chart_accounts` | id, chart_version_id, account_code, name, parent_account_code null (为空表示 top level), is_postable, enables_department (boolean) | `uk_…_version_code`; code format CHECK per book basis; published-version rows frozen (trigger) | structure yes; **rows wait** for #1373 |
| `mapping_versions` | id, accrual_chart_version_id, cash_chart_version_id, version_no, status, drafted_by, approved_by null, approved_at null, effective_from_fiscal_year, effective_from_period_number, created_at | as chart versions | yes |
| `account_mappings` | id, mapping_version_id, cash_account_code, accrual_account_code, reconcile_kind (SAME/DIFFERENT) | `uk_…_version_cash_account`; frozen when published; codes exist in the referenced chart versions (checked by the publish command; cross-table) | structure yes; **rows wait** for #1373 |

Dimension columns other than `department` are not in these sketches by C-4. Each chart account's enabled-dimension flags are one boolean per dimension, added with the dimension.

**Classification** (drafts for the schema card): fact amounts and `component_key` of payroll facts `CONFIDENTIAL`; `legal_entity_id`, dates and ids `INTERNAL`; no table in the spine holds a personal identifier. **Retention**: facts, periods, versions are accounting records kept permanently by default (`PERMANENT`) — the statutory minimum for vouchers and books is 30 years (`会计档案管理办法`, ADR-0022 decision 9) and these tables feed them; the user can change it.

**Registry roles** (drafts): `fiscal_periods` `AGGREGATE_ROOT`/`MUTABLE`; `posting_facts` `AGGREGATE_ROOT`/`APPEND_ONLY`; `posting_fact_lines` `AGGREGATE_CHILD`/`APPEND_ONLY`; `posting_rejections` `AGGREGATE_CHILD`/`APPEND_ONLY`; `ledgers` reference; versions `AGGREGATE_ROOT`/`MUTABLE` until published, rows `AGGREGATE_CHILD`.

**Analytical facts**: none in the spine; `ChartVersionPublished` and `MappingVersionPublished` are analytical-only events (AGG-82, 85).

**Baseline, target, transition, rollback**: baseline = none of the three schemas; target = the tables above; transition = migrations only, no data (legal entities exist; `OpenFiscalYear` and book creation are commands/events after the migration); rollback = drop the schema while empty.

## 9. Business questions (options and recommendation)

Rule from the user (2026-11-05): where a regulation is explicit and company practice differs, the user decides; otherwise follow the explicit regulation, else current legacy practice.

| # | Question | Options | Recommendation and rule applied |
|---|---|---|---|
| Q-1 | Do the three ledger items (books, chart, mapping) ship inside the HR batch even though nothing in the batch reads them at run time (section 1)? | (a) ship them in the HR batch as ADR-0087 decided; (b) ship the period and facts slices now, the ledger trio with voucher generation | (a) is the adopted ADR, so (a) stands unless the user changes it. Cost of (a): three small slices with no consumer until vouchers; gain: payroll's account set is signed before payroll goes live. Needs the user only if they want (b) (a new ADR). |
| Q-2 | Fiscal year | calendar year; other | Calendar year: the Accounting Law (`会计法` article 11, quoted from memory, **unverified**: the text was not re-read) fixes the fiscal year as 1 January to 31 December. Explicit regulation; legacy practice is also calendar year (**unverified**: confirm with the U8C book calendar in forensic). No user decision unless legacy differs. |
| Q-3 | Date a payroll fact lands in | (a) last day of the payroll month; (b) payment date; (c) approval date | (a): accrual basis books the cost to the month the work was done (accrual book); the cash book is a mapping, not a second fact. Practice for payroll vouchers is **unverified** (#1373; practice.md section 3.5 says they are manual vouchers). If legacy books on payment date, the user decides between (a) and (b). |
| Q-4 | U-27: the effective date of a back-fill mapping version | (a) first day of the current open period; (b) first day of the period it fixes | Existing recommendation in AGG-85: only add rows, never change rows already used; with that, (a) is safe. Rule applied: practice (ADR-0022). |
| Q-5 | Do payroll facts carry a person dimension? | (a) no; department only; (b) yes, employee id on lines | (a) by default (C-2). If #1373 shows U8C salary vouchers use the person dimension, the legacy practice says (b), but pay per person is personal data (ADR-0080), so the user decides. Waits on #1373. |
| Q-6 | Which payroll accounts and dimensions are in scope | the list | **Not invented.** Taken from #1373 (U8C vouchers, aggregates only). Slices that need it are marked waiting. |
| Q-8 | Retention and classification of the spine tables | `PERMANENT` retention and `CONFIDENTIAL` for payroll facts (section 8), or other | Recommendation: as drafted (accounting records, 30-year statutory minimum, ADR-0022 decision 9). The data-design gate lists retention and classification as the user's items, so the schema card cannot pass until the user answers. |
| Q-7 | Who opens a fiscal year, drafts and publishes versions | finance roles (section 5) | Matches ADR-0022 decision 2 (finance approves, drafter ≠ approver). The role names are the permission catalogue's; no user decision. |

**Open point (S3):** `account_mappings` is keyed by the cash account; accrual-only accounts (for example 合同资产) have no cash account. Payroll does not need them; the W1 ledger work that adds them must make the cash column nullable with a second key, or key rows by pair. Decided then, not now.

## 10. Slice plan (cards for lead to open; each names its prerequisites)

| Slice | Content | Prerequisites | Waits on |
|---|---|---|---|
| P1 | Schema `accounting_period` + `OpenFiscalYear` + `PeriodGate` + permissions + module edges | LegalEntity (built); this spec merged; period table data-design sign-off (section 8) | nothing |
| F1 | Schema `operations_facts` (facts, lines, components) + pipeline + `PayrollPostingCandidate` variant + events `BusinessFactAttributed`/`Restated` + idempotency/balance/period-gate tests | P1 merged; facts data-design sign-off | nothing (component rows come with payroll) |
| F2 | Rejections, resubmit, disposition | F1 | nothing; may wait for the closing batch |
| G1 | Schema `accounting_ledger` books + creation on `LegalEntityRegistered` + backfill | LegalEntity events (built); books data-design sign-off | nothing |
| G2 | Chart versions + draft/publish + frozen-row triggers | G1 | structure: nothing; **account rows: #1373** |
| G3 | Mapping versions + draft/publish | G2 | structure: nothing; **mapping rows: #1373** |
| PAY | Payroll posts a month through F1 (payroll card, not this spec) | F1, P1; payroll aggregate AGG-73; component rows from #1373 | #1373 |

Order: P1 → F1 → (F2); G1 → G2 → G3 independent of P1/F1. Design cards still owed: data-design sign-off record for each slice (one-design), the SSOT card that numbers S-R1..S-R4 (lead).

## 11. Done-when (for the test engineer)

Observable behaviours, one test each (names are test intents):

1. `OpenFiscalYear` creates 12 `OPEN` periods for the entity and year; a second call for the same year fails; one entity has no duplicate (year, number).
2. `forPosting` returns `OPEN` for a date in an open period and `NOT_OPENED` for a year without rows; `NO_OPEN_PERIOD` when the date is in a closed period and nothing is open; `periodStatus(entity, year, 13)` returns `NOT_ENABLED`.
3. With a period set to `CLOSING`, a candidate gets `RETRY_PERIOD_CLOSING` and no rejection row is written (INV-92).
4. With a period set to `CLOSED`, a candidate dated in it posts in the current open period with the original period set (ADR-0045 decision 2); the closed row is unchanged.
5. A replayed idempotency key returns `DUPLICATE` with the original fact and writes nothing new (INV-94).
6. An unbalanced candidate, a candidate for an unregistered legal entity and a candidate with an unregistered component are each rejected with the stated reason and one rejection row (INV-93).
7. Updating or deleting a fact or a fact line fails at database level (INV-95); `ReverseAndRepost` writes a reversing fact that mirrors the lines and a new fact; a second reversal of the same fact fails.
8. A closed `fiscal_periods` row cannot be updated or deleted at database level (INV-99).
9. Concurrency: a close racing a post (period moved to `CLOSING` between check and write) never produces a fact in a `CLOSING` or `CLOSED` period (the shared lock).
10. `LegalEntityRegistered` creates exactly one `ACCRUAL` and one `CASH` book; replaying the event creates no second pair.
11. A chart version cannot be published by its drafter (INV-131); a published version's accounts cannot be changed; an account used by a published mapping cannot be deleted (INV-103).
12. A mapping row naming an account that is not in the referenced chart versions is refused at publish.
13. No module outside `accounting.ledger` reads chart or mapping tables, and none outside `operations.facts` writes fact tables (architecture tests).

## 12. Counter-examples invited

If payroll needs something the account-free fact cannot say (for example, a posting that needs the person), or if #1373 shows that U8C payroll vouchers cannot be reduced to entity × month × component × department, the fact's grain (C-2) is wrong and this spec is revised before F1 is built.
