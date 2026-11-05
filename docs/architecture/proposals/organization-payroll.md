# organization.payroll (薪酬，止于过账) — sign-off spec (#1403)

- Card: #1403. Author: one-design, 2026-11-05. Base: origin/main.
- Status: proposal. Basis: ADR-0087 decisions 1 to 5 (adopted), ADR-0086 decision 5 (per relationship), ADR-0051 decision 3 (incentive merged into payroll), ADR-0079 sections 2, 3.5, 4 and 5 (payroll boundary, monthly payroll lifecycle, events), ADR-0016 (posting), `organization-leave.md` section 9a (the payroll seam), `accounting-spine.md` (PR #1407; posting facts, periods).
- Evidence marks: **verified** = read on origin/main today. **unverified** = from memory or waiting on evidence. **#1373** = legacy payroll distillation, open: the legacy payroll formulas, the manual steps and the U8C posting accounts are NOT known yet, so this spec names every value that depends on them as **open** and invents none.
- Rule from the user (2026-11-05): where an explicit regulation differs from company practice the user decides; otherwise follow the explicit regulation; where none is explicit follow the current legacy practice.

## 1. What this spec decides

Payroll up to posting, per ADR-0087 decision 3: a monthly payroll per **paying legal entity**, with social insurance and housing fund per **employment relationship**, individual income tax and filing through `platform.integration` ports, bonus input through an approved manual port, the per-entity monthly payables submission as an outbound port, and the **posting fact** written to `operations.facts` when a month is approved. Not here (ADR-0087): vouchers, closing, U8C push, any payables aggregate, performance and recruitment.

## 2. Verified facts

| Fact | Where |
|---|---|
| `organization.payroll` is `planned`; no payroll package exists | `contexts.yaml`; `ls backend/src/main/java/com/forcitis/one/organization` |
| Payroll aggregates (planned): AGG-73 monthly settlement, 74 enrolment, 75 incentive plan version, 76 bonus determination, 93 pay profile, 95 withholding and filing, 119 rule version, 120 special-deduction declaration | `planned.yaml` |
| Settlement lifecycle (planned): draft → calculated → in review → approved → paid → posted; "posted" is set by `BusinessFactAttributed`; approval submits one candidate | `planned.yaml:1136`; ADR-0079 section 3 item 5 |
| After approval errors are corrected only by adjustment items in later months (CS-3.9) | ADR-0079 section 3 item 5 |
| Payroll → payables: one payables submission per legal entity per month, detail stays in payroll; "paid" follows the payment result; a returned or failed payment puts the person on a re-pay list and does not rewrite the approved payroll; finance does not read payroll | ADR-0079 section 2; ADR-0051 decision 7 item 2.7 |
| IIT withholding and payroll run by the **paying entity** (default: the relationship's employer); social insurance and housing fund by the **relationship's employer** | ADR-0086 decision 5; `employment-relationship.md` section 6.1 item 4 |
| Payroll reads, from leave: locked monthly confirmation lines (minutes by leave type with `is_paid` copied, overtime by compensation mode and day kind), the comp-time expiry event, unused leave on separation | `organization-leave.md` section 9a |
| The legacy payroll runs in A8 (own calculation forms `formmain_0800/0858/0864/0834/0841/1318`), not in U8C `hrwa`; a considerable part is manual | user fact relayed in #1373 (evidence about the legacy system, not a requirement) |
| Which U8C accounts and dimensions the payroll vouchers use; the calculation formulas; the manual steps | **unverified**: #1373 |

## 3. Options and choices (technical; mine to choose)

| # | Choice | Decision |
|---|---|---|
| C-1 | Settlement states | The planned chain "approved → paid → posted" is not linear once payables is an outbound port: posting happens at approval, payment follows a payables result that cannot exist yet. `status`: `DRAFT`, `CALCULATED`, `IN_REVIEW`, `APPROVED`, `CANCELLED`. Two orthogonal columns after approval: `posting_status` (`NOT_SUBMITTED`, `SUBMITTED`, `POSTED`) set by the posting receipt, and `payment_status` (`NOT_REQUESTED`, `REQUESTED`, `PAID`, `PARTLY_FAILED`) set by the payables port result. Deviation from the planned chain, deliberate; the wording "已发放/已过账" stays as display words. |
| C-2 | Posting grain | One posting fact per settlement (paying entity × month), lines per component and department, **no employee id** (spine C-2). Idempotency key `payroll:{legalEntityId}:{payrollMonth}:{settlementId}`. One submission per approved settlement; a later correction is an adjustment item in the next month's settlement, never a second post for the same month (CS-3.9). |
| C-3 | Business date of the fact | Last day of the payroll month (spine Q-3 recommendation; the user's answer there decides). |
| C-4 | Money | `numeric(19,2)` in CNY; rounding is a rule-version value (open, section 9). |
| C-5 | Values are data | Tax tables, contribution ratios, base limits, deduction standards and component keys live in rule-version rows, never in code; so the structure slices do not wait for the values. |
| C-6 | Where payroll personal data is exposed | Only payroll roles and the person's own payslip; the posting fact, the payables submission and events carry totals and ids, never a person's pay (ADR-0079). Events carry ids only. |
| C-7 | Tax calculation location | Two seams, one chosen by evidence: (A) payroll computes the withholding from the rule version (cumulative method), the port only files; (B) the tax channel returns the calculation through the port. The interface `IncomeTaxWithholding.calculate(...)` is the same either way; which side implements it is **open** (#1373, Q-2). |
| C-8 | Adjustments | A settlement line has an `adjustment_of_month` tag for corrections of earlier months; locked leave corrections arrive as the next month's correction rows (leave INV-105) and flow through the same field. |

## 4. Aggregates

All keyed by `employment_relationship_id` [R] where per relationship, and by `paying_legal_entity_id` where per paying entity. English names are the registered glossary terms (`docs/glossary/terms.yaml`), so no new proposed file is needed.

### 4.1 Payroll rule version (AGG-119, `payroll_rule_version`)

Configuration aggregate, one per version, effective interval not overlapping (INV-139; a lookup that finds none fails and alerts). Content (**structure only; values open**): IIT rate table and the monthly basic deduction, social-insurance and housing-fund ratios per insurance kind and jurisdiction, contribution base lower and upper limits, rounding rule. Command `PublishPayrollRuleVersion` (→ `PayrollRuleVersionEffective`, analytical; PRC-180); drafter ≠ approver (INV-131 family; finance-HR approver is the permission catalogue's call). Statutory sources to cite when values are filled (**unverified**, from memory): the IIT law and its implementing regulation (cumulative withholding), the social-insurance law and local base rules, the housing-fund regulation (5% to 12%). Values taken from statute are marked as such; where legacy practice differs from an explicit rule, section 9 P-1.

### 4.2 Employee compensation profile (AGG-93, `employee_compensation_profile`)

Append-only versions with effective intervals, per **employment relationship**; holds the agreed pay (the authoritative value; the labour contract refers to a profile version, ADR-0079 section 2), the **paying legal entity** (default the relationship's employer; an override is recorded here with a reason), and pending adjustments (with source and idempotency basis). Commands: `CreateCompensationProfile`, `AdjustCompensationProfile`, `TerminateCompensationProfile` (subscribes `EmploymentRelationshipEnded`; explicit rather than inferred from person state, S-32). Event `CompensationProfileChanged` (analytical). Component structure (which pay items exist: base, allowances, subsidies) is the shape of A8 `formmain_0858`/`0841`: **open** until #1373; modelled as profile lines keyed by registered component keys.

### 4.3 Social-insurance and housing-fund enrolment (AGG-74, `social_insurance_enrollment_and_housing_fund_contribution_record`)

Per employment relationship (ADR-0086 decision 5): insurance kinds, enrolment state including **"not enrolled here"**, and contribution base versions (annual adjustment makes a new version, S-32); a re-hire creates a new enrolment record. Commands `EnrolInsurance`, `StopInsurance`, `AdjustContributionBase` (→ `InsuranceEnrolled`/`InsuranceStopped`, analytical). Subscribes `EmploymentRelationshipStarted`/`Ended` (never infers from person state). Whether a second employer enrols the person is ADR-0086 B-C: **open with HR and legal** (section 9 P-5); until answered an enrolment exists only when HR records it.

### 4.4 Special-deduction declaration (AGG-120)

Annual declaration per person and tax year, made by the person (HR may enter on their behalf, source recorded). Contains beneficiary information including minors: that part is sensitive and may need its own table and access level (U-11). **This aggregate waits for U-11 and a legal confirmation**: no slice builds it until then, and settlements in the meantime compute without special deductions only if the user so decides (P-6). Event `SpecialDeductionUpdated` (analytical).

### 4.5 Bonus determination (AGG-76) and the manual port

Until performance (AGG-106) exists the input is a **manual, approved port** (ADR-0087 5.3): a `BonusInput` row per relationship and payroll month with amount, source `MANUAL_APPROVED`, the approval number (via `platform.approval`) and a reason key from a selection list; the aggregate `DetermineBonus` (→ `BonusDetermined`, analytical) references the input. When performance is built the same port reads AGG-106 and the aggregate does not change. The same pattern carries shareholder dividends into the withholding input (ADR-0087 5.4) until AGG-109 exists. Incentive-plan versions (AGG-75) are **not** in this spec: the issue's scope does not list them and no input needs them yet; their slice is opened when the user names the first incentive rule.

### 4.6 Monthly payroll settlement (AGG-73, `monthly_payroll_settlement_statement`)

One per paying legal entity and payroll month. Contains `payroll_settlement_line`s (per employment relationship), the rule version used, and the posting and payment markers.

- States: `DRAFT` → `CALCULATED` → `IN_REVIEW` → `APPROVED`; `CANCELLED` from `DRAFT`/`CALCULATED` only (C-1). `APPROVED` is final for amounts.
- Commands (→ events; preconditions):
  - `CalculateMonthlyPayroll` (PRC-178; → internal): **only when every in-service person of the paying entity has a `LOCKED` monthly confirmation** (leave 9a), otherwise the result is "pending lock" with the list of persons not locked; reads the effective rule version for the month (none → fail, INV-139), the compensation profile in force each day, the enrolments and bases, the bonus inputs, the special deductions, unpaid-leave and overtime lines, comp-time conversions and, for leavers, the unused-leave settlement (T-10, section 9 P-7). Idempotent: recalculation of a `DRAFT`/`CALCULATED` settlement replaces its lines.
  - `SubmitPayrollForReview` (→ internal): starts the approval (`platform.approval`).
  - `ApproveMonthlyPayroll` (→ `PayrollApproved`; INV-70: only statutory deductions, paid in legal tender): called by the approval result; **submits the posting candidate once** and the payables submission once, in the same transaction through the outbox (at least once, consumers idempotent).
  - `MarkPayrollPosted` (→ internal): set from `BusinessFactAttributed` by `sourceId`; the facts event carries ids only.
  - `RegisterDisbursementResult` / `InitiateRedisbursement` (→ `PayrollDisbursementResultRegistered`, `PayrollRedisbursementInitiated`; glossary terms exist): per person result from the payables port; a failure puts the person on the re-pay list and never rewrites the approved settlement.
- Events: `PayrollApproved` (payload: settlementId, paying legal entity, payroll month, totals only; consumers: filing, audit), `PayrollPaid` (analytical). Ids and totals only.
- Corrections after approval: adjustment items in the next month's settlement lines (C-8).

### 4.7 Withholding and filing (AGG-95, `payroll_statutory_filing_record`)

Per paying legal entity and tax period: merge dividends into the withholding (manual port until AGG-109), generate the filing, submit it (→ `WithholdingFilingSubmitted`, analytical), register payment, correct. Two `platform.integration` ports: withholding calculation/submission and filing (ADR-0087 5.4); **stub adapters** until the channels are ready (the adapters belong to the platform, the contract to payroll). Social-insurance and housing-fund filings are separate records per relationship's employer. Posting of the tax and insurance payment is a later fact type (AGG-95 command "submit posting candidate"): **not in this spec's slices**; it joins when the user confirms payments are booked from payroll (P-8).

## 5. Permissions and data scope

Proposed permission codes follow the catalogue shape; the exact entries are fixed per slice. Separation of duties: calculate, review, approve and publish-rule are different holders; an approver may not be the one who calculated or who entered a bonus input (**mine**, SoD; confirm with finance). Employees read only their own payslip. HR operators see pay only with a payroll role; finance does not read payroll (ADR-0051 decision 7 item 2.7). Scope: every table carries the **paying** `legal_entity_id` or inherits it from its parent; reads are filtered by the caller's entity scope, fail-closed. Classification: pay amounts `CONFIDENTIAL`; special-deduction beneficiary data sensitive (U-11); `employee_id` personal.

## 6. Module edges

`organization.payroll → organization.workforce` (api: relationships, employer, paying entity; events), `→ organization.attendance` (api: standard hours), `→ organization.leave` (api: locked confirmation lines, unused leave; events: `MonthlyConfirmationLocked`, `LeaveCompTimeExpired`), `→ platform.approval`, `→ platform.integration` (ports), `→ operations.facts` (api: submit; event `BusinessFactAttributed`), `→ payables.payment` **not yet**: the outbound port interface lives in payroll and a stub adapter returns "not available" until payables exists (ADR-0087 decision 4). Forbidden: payroll writing fact tables directly; any BC other than payroll reading payroll tables; payroll reading leave tables (only the api).

## 7. RULE ids

Reused: INV-70 (statutory deductions only), INV-105 (lock; corrections next month), INV-139 (rule versions do not overlap), INV-92 to 95 (posting, via the spine). New, to be numbered by the SSOT card: P-R1 a settlement for a paying entity and month is unique; P-R2 calculation requires every in-service person locked; P-R3 an approved settlement's amounts never change; P-R4 the posting candidate is submitted once per approved settlement and balanced; P-R5 a person's pay never appears in an event, a fact or a payables submission detail beyond the entity totals.

## 8. Data design (a design draft, not a sign-off; the gate applies per schema card, signed by design)

Schema `organization_payroll`. Sketches (class words and constraint names per naming standard 2.4/2.4a; every nullable column states 「为空表示」 in its comment; cross-BC ids have no foreign key):

| Table | Key columns | Constraints and notes | Signable |
|---|---|---|---|
| `payroll_rule_versions` | id, effective_from_date, effective_to_date null (为空表示 open-ended), version_status, drafted_by, approved_by, jurisdiction_key | `ex_…_effective_interval_overlap` gist per jurisdiction (INV-139); drafter ≠ approver; published rows frozen | structure now; **rows wait** (statute / #1373) |
| `payroll_rule_rows` | rule_version_id, row_kind, key, value, … | frozen when published | structure now |
| `compensation_profile_versions` + `…_lines` | employment_relationship_id, effective_from_date, paying_legal_entity_id, paying_entity_reason_key null (为空表示 default employer) ; lines: component_key, amount | append-only; no overlap per relationship | **waits** for component structure (#1373) |
| `insurance_enrolments` + `contribution_bases` | employment_relationship_id, insurance_kind, enrolment_status, base_amount, effective_from_date | per relationship; unique open enrolment per (relationship, kind) | structure now; B-C open |
| `bonus_inputs` | employment_relationship_id, payroll_month_start_date, amount, source_kind, approval_instance_id, reason_key | one approved input per id; approval number mandatory | yes |
| `payroll_settlements` | id, paying_legal_entity_id, payroll_month_start_date, status, posting_status, payment_status, rule_version_id, totals, version | `uk_…_entity_month`; status ⇔ markers CHECKs; approved rows frozen (trigger) | yes |
| `payroll_settlement_lines` | settlement_id, employment_relationship_id, employee_id, amounts by component, adjustment_of_month_start_date null (为空表示 not an adjustment) | `uk_…_settlement_relationship_adjustment`; frozen when approved | structure now; component columns wait (#1373) |
| `payroll_disbursements` | settlement_line_id, disbursement_status, failure_reason_key null, redisbursement_of_id null | status via payables port result | yes |
| `posting_submissions`, `payables_submissions` | settlement_id, idempotency_key, submission_status, receipt ids | one per settlement; ids only | yes |
| `withholding_filings` (+ payments) | paying_legal_entity_id, tax_period, filing_status | per entity and period | **waits** for the ports |
| `special_deduction_declarations` (+ items, beneficiary table) | employee_id, tax_year, … | beneficiary data separate | **waits** for U-11 |

Classification and retention are user items under the gate (section 9 P-9 with a recommendation). Registry roles, lineage ("new"; legacy payroll imported by the migration slice) are drafted per schema card. No analytical fact in this spec (the totals events feed the later reporting batch).

## 9. Business questions (answered from evidence, or options plus recommendation)

Nothing below is decided by me. Items marked **pending #1373** are answered by the legacy distillation; **user** items go to the user only if an explicit statute differs from current practice (the user's rule).

| # | Question | Source and state | Options and recommendation |
|---|---|---|---|
| P-1 | IIT withholding method and the monthly basic deduction | Statute is explicit (cumulative method, standard deduction; **unverified**). Practice: **pending #1373** | If legacy follows the statute: no question. If legacy differs: the user decides. Recommendation: follow the statute. |
| P-2 | Social-insurance and housing-fund bases and ratios | Statute sets bands (**unverified**); local rules; practice **pending #1373** | Same rule as P-1; values live in rule versions. |
| P-3 | Paying entity when it differs from the employer | ADR-0086 decision 5 gave the default and an override (answered 「按建议」, issue #1308) | Answered; override is a profile field. |
| P-4 | Payment date and payroll month cut-off | Statute: wages paid at least monthly on the agreed date (**unverified**); practice **pending #1373** | Follow statute unless legacy is stricter; HR names the pay date per entity as a rule value. |
| P-5 | Does a second employer enrol the person in social insurance and housing fund (ADR-0086 B-C) | Open "with the user and HR/legal before payroll design" | Options: (a) enrol only where HR records an enrolment (recommended: the model holds "not enrolled here"); (b) always enrol both. This one is **a user/legal question**; carry it to the user with the options. |
| P-6 | Special deductions before U-11 is settled | AGG-120 waits | Options: (a) hold the whole payroll go-live until AGG-120 is built (recommended: omitting deductions overstates tax); (b) interim manual entry by HR with the same table. User/legal. |
| P-7 | Unused annual leave and comp time paid at separation (T-10) | Principle decided: follow legacy result; value **pending #1373 / forensic** | Waits; payroll takes the settlement line from the leave read API. |
| P-8 | Who books the tax and insurance payment | Not decided | Out of this spec; user later. |
| P-9 | Retention and classification of payroll tables | The gate lists them as user items | Recommendation: pay data `CONFIDENTIAL`, retained as accounting/labour records (wage payment records at least 2 years by statute — **unverified**; longer for accounting vouchers); user confirms. |
| P-10 | Manual steps in the legacy calculation | #1373 Q2 produces questions for HR/finance | Becomes explicit commands or input ports once known; this spec only provides the bonus/dividend manual ports. |
| P-11 | Rounding rule and component set | **pending #1373** | Rule-version values; no code change. |

## 10. Slice plan (cards for lead to open; each names its prerequisites)

| Slice | Content | Prerequisites | Waits on |
|---|---|---|---|
| PY0 | Rule-version structure + publish command + INV-139 | this spec; data-design sign-off | nothing for structure; **values: statute/#1373** |
| PY1 | Compensation profile versions + paying entity + terminate on `Ended` | PY0; workforce relationships (ADR-0086 slice R) | **component structure: #1373** |
| PY2 | Enrolments and bases per relationship | PY0; workforce relationship events | P-5 (user/legal) for the second-employer rule |
| PY3 | Bonus input manual port | platform.approval (built) | nothing |
| PY4 | Calculate + review + approve a settlement (no posting) | PY0 to PY3; leave: locked monthly confirmation lines and the unpaid/overtime lines (leave spec section 10); attendance standard hours (AGG-108); Q-1.. lock rule P-R2 | formulas **#1373**, P-1/P-2/P-6 |
| PY5 | Posting submission + `MarkPayrollPosted` | PY4; spine P1 and F1 (PR #1407 → cards); component rows for payroll facts seeded from #1373 | #1373 (components) |
| PY6 | Payables outbound port with stub adapter + disbursement results + re-pay list | PY4; ADR-0087 decision 4 | nothing (stub) |
| PY7 | IIT withholding and filing ports + `withholding_filings` | PY4; platform.integration (built) | C-7 choice (#1373); channels for real adapters |
| PY8 | Special deductions | PY0; U-11 | P-6, U-11, legal |
| PY9 | Payslips (own view) and HR/payroll views | PY4 | nothing |
| PY10 | Legacy import (profiles, enrolments, history) | PY1, PY2 and the migration design | forensic reads |

Order: PY0 → (PY1, PY2, PY3 in parallel) → PY4 → (PY5, PY6, PY7) → PY9; PY8 and PY10 as their evidence arrives. Design cards still owed: data-design sign-off per slice (design); SSOT card to number P-R1..P-R5 (lead); permission catalogue entries per slice.

## 11. Done-when (for the test engineer)

1. A settlement is unique per paying entity and month; a second create for the pair fails.
2. `CalculateMonthlyPayroll` returns "pending lock" with the unlocked persons while any in-service person of the entity lacks a `LOCKED` confirmation, and computes once all are locked.
3. With no effective rule version for the month the calculation fails and raises the alert (INV-139); two overlapping effective versions cannot be published.
4. A person with two relationships paid by two entities appears in each entity's settlement with only that relationship's lines; social insurance follows the relationship's employer and withholding follows the paying entity.
5. Approval submits exactly one balanced posting candidate and one payables submission; replaying the approval result submits nothing new (idempotency key); the candidate contains no employee id.
6. `BusinessFactAttributed` with the settlement's `sourceId` sets `posting_status = POSTED`; a replayed event changes nothing; an event for an unknown source is ignored and logged.
7. An approved settlement's amounts cannot be updated or deleted at database level; a correction appears as an adjustment line in the next month's settlement, tagged with the original month.
8. A failed payment result puts the person on the re-pay list and leaves the approved settlement unchanged; a re-disbursement links to the failed one.
9. An employee reads only their own payslip; HR without a payroll role and finance cannot read any settlement line (403); a caller outside the entity scope gets 404-equivalent for another entity's settlement.
10. Events and the payables submission contain ids and entity totals only; no per-person amount appears (architecture/contract test over the event schemas).
11. The approver of a settlement cannot be the person who calculated it, and cannot approve a bonus input they entered (SoD).
12. Deductions outside the statutory list are refused (INV-70).
13. Module edges: payroll does not read leave tables or write fact tables; no BC but payroll reads payroll tables (architecture tests).

## 12. Counter-examples invited

If #1373 shows that the legacy calculation depends on inputs this design does not model (for example per-project allowances or pay components that vary by department), the compensation-profile shape (4.2) and the posting components (spine) are revised before PY1 and PY5 are built. If U8C salary vouchers need the person dimension, the account-free posting fact (spine C-2) is revisited with the user.
