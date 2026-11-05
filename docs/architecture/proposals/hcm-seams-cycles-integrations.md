# HR subdomain: seams, periodic processes, external integrations — first sketch (#1519)

- Card: #1519. Author: one-design, 2026-11-06. Base: origin/main.
- **Status: hypothesis, revise after each slice.** A whole-picture sketch for slice ordering. It decides no rule, opens no card, changes no code.
- Evidence marks: **[V]** opened or counted on origin/main or in the pack today; **[U]** inference or not checked.
- Pack = the golden design pack R002 outside the repo (`.../cases/mainline.json`, `monthly_cycles.json`, `experience_branches.json`). Evidence only: its node ids (`HCM-LIFE-nnn`) are cited as pointers; its names and structure are not adopted. Context names below are this repository's (ADR-0079, ADR-0084, ADR-0085, ADR-0086, ADR-0087); where the pack uses another word the pack word is in quotes.

## 1. What the pack says about seams

1. The mainline has 78 nodes [V: count]. 14 nodes name two or more owners in the owner field (`a / b`) [V: count; the card said 7]. Consecutive nodes change owner 59 times in 77 steps [V: count], so most steps cross a seam.
2. The owner field uses 15 distinct single names [V: count]; main has code for five of them (workforce, attendance, payroll rule versions, identity, approval) and `leave` is only a package marker: workforce, attendance, leave (planned, code is only `package-info`), payroll (rule versions only), identity, approval [V: `backend/src/main/java/com/forcitis/one/`]. The rest are planned or named outside HR: recruitment (ADR-0085, spec R0 to R3), competency (proposal #1488), performance (ADR-0084), resource (delivery-resource plan), payment (`payables.payment`, planned), costing (`cost.timesheet` exists; labour-cost collection does not), expense, asset (`organization.administration` for IT return), privacy (workforce accepts requests, ADR-0079).
3. The 24 monthly cases are two employment relationships by 12 months, payment on the 10th of the next month in 22 of 24 cases (2026-12 on the 15th, B-2028-02 on the 8th) [V: `monthly_cycles.json`]. They give the monthly rhythm, not the amounts (the pack marks its inputs synthetic).

## 2. Seam table

Reading: "from" writes the fact, "to" reads it or reacts. "Exists" means a class or event on main today. Hand-over form is the ADR-0079 rule: another context's data is read through its `api` or by event, never by table.

| # | From | To | Handed over | First mainline node crossing it | Exists on main today |
|---|---|---|---|---|---|
| S1 | workforce | recruitment | approved position and headcount (the requisition checks the position is active) | LIFE-001 → 002 | position read: `workforce/api/PositionLookup.java` [V]; recruitment context: **MISSING** (spec only) |
| S2 | recruitment | workforce | accepted offer → hire command (a candidate is not an employee until the hire is effective) | LIFE-004 → 005 | **MISSING** (no recruitment code); workforce side `EmployeeHired` event and hire command exist [V] |
| S3 | workforce | payroll | employment dates, phase, paying entity, relationship end | LIFE-007 → 008 | events `EmployeeHired/Regularized/Transferred/Separated` published [V: `workforce/events`]; **payroll subscriber MISSING** (payroll holds rule versions only) |
| S4 | workforce | identity | hire opens an account; transfer re-grants by new duty; separation closes it | LIFE-008 → 009; 030 → 031; 036 → 037 | `identity/api/AccountOpeningRoute`, `HandleEmployeeSeparated` [V]; grant-by-source on transfer: [U] not checked |
| S5 | workforce | leave | start date, phase, unpaid-leave and separation dates (opening quota grant, end-of-employment quota handling) | LIFE-009 → 010; 037 → 038 | events exist; **leave MISSING** |
| S6 | attendance | leave, payroll | standard hours versions by date (read, not subscribed) | LIFE-057 → 058 | `attendance/api/StandardHours`, event `StandardHoursVersionPublished` [V]; consumers **MISSING** |
| S7 | leave | payroll | month-locked confirmation lines: leave minutes by type, overtime minutes by compensation mode, attendance days; unused-leave at separation | LIFE-013 → 014; 038 → 039 | **MISSING both sides** (leave spec section 9a, payroll spec PY4) |
| S8 | workforce | resource (delivery plan) | in-service, leave, qualification reads for staffing | LIFE-011 → 012 | workforce reads exist [V: `EmployeeProfiles`, `OrgDirectory`]; resource context **MISSING** |
| S9 | resource, leave, payroll | cost (labour cost) | project hours, paid cost per period | LIFE-031 → 032 | `cost/timesheet/api` [V] exists for hours; labour-cost collection **MISSING** |
| S10 | competency | workforce, payroll | verified certificate / skill; allowance handled apart from the certificate | LIFE-014 → 015; 028 → 029 | **MISSING** (proposal #1488, ADR-0089 proposed) |
| S11 | competency | resource | qualification valid / suspended / restored for assignment checks | LIFE-059 → 061 | **MISSING** |
| S12 | workforce | performance | goal setting at confirmation of employment | LIFE-016 → 017 | **MISSING** (ADR-0084 adopted, no code) |
| S13 | performance | payroll | confirmed evaluation or project-bonus basis (also after separation) | LIFE-043 → 044; 074 → 075 | **MISSING**; bonus input port is PY3 (manual, approval-driven, spec only) |
| S14 | workforce | identity | temporary acting duty and delegation | LIFE-024 → 025 | approval has reassign and escalation events [V: `TaskReassigned`, `ApprovalTaskEscalated`]; a dated acting-duty fact in workforce: **MISSING** [U] |
| S15 | workforce | administration, resource, approval | separation hold: pending asset return, project hand-over, open approvals | LIFE-034 → 035 | `workforce/api/SeparationHolds`, `ReleaseSeparationHold` [V]; asset return in administration: **MISSING** [U] |
| S16 | payroll | payment (`payables.payment`) | one payable per paying entity and month, details stay in payroll; bank-return and re-pay results come back | LIFE-039 → 040 → 043 | **MISSING** (`payables.payment` not in code; PY6 specs a stub port) |
| S17 | payroll | accounting (posting) | posting submission per period; the period gate | LIFE-014 (implied), 054 | `accounting/period/api/PeriodGate`, `PostingPeriod` [V]; posting pipeline and payroll caller: **MISSING** (PY5) |
| S18 | payroll | payroll (later month) | correction after seal: adjustment line in a later month, never an edit | LIFE-054, 074 | rule only (CS-3.9); **MISSING** in code |
| S19 | expense | payment | old expense claims continue after separation | LIFE-045 → 046 | outside HR; **MISSING** |
| S20 | workforce | all holders of the person's data | privacy request: access, copy, proof letter | LIFE-076 → 077 | **MISSING**; workforce accepts, each context executes on its own data (ADR-0079) |
| S21 | platform.approval | each business context | approval outcome arrives as a handler call; the task page shows the document (frontend document slot, #1516) | any approved node, first LIFE-017 or 018 | approval `ApprovalCompleted`, hook API exist [V]; document slot in the web: **MISSING** (#1516) |

Notes:
- Seams S3, S4, S5, S6 are the ones already half built: the publishing side exists, the subscriber does not. They are the cheapest to start from, and each fails visibly (a missing subscriber is a missing call) rather than silently.
- S13, S16, S10 cross a seam in **both** directions or in time after separation. They are where "who owns the fact" is hardest and where open questions below sit.
- 59 owner changes against 21 rows: the table groups repeats (for example S4 recurs at hire, transfer, separation).

## 3. Periodic processes

A periodic process is one that no single mainline node performs: it runs because a date or a period arrived.

| # | Process | Contexts | Pack evidence | Exists today | Proposed as its own slice after |
|---|---|---|---|---|---|
| C1 | Month-end payroll run: collect locked confirmations, calculate, review, approve, hand to payment and posting | payroll, leave, attendance, workforce, payment, accounting | 24 cases, 2 relationships × 12 months, payment on the 10th of the next month in 22 of 24 (`monthly_cycles.json`); nodes LIFE-014, 039, 054, 072 | **MISSING** (payroll rule versions only) | PY4 (calculate and approve); the trigger and the period-state handling as a separate slice after PY4 |
| C2 | Attendance month close: employee confirms the month, manager confirms, then it locks; later corrections go as adjustments | leave, workforce | LIFE-013, 074 (correction after the seal) | **MISSING** (leave) | the leave slice that carries the month confirmation (leave spec 9a), before PY4 |
| C3 | Yearly leave rollover: new-year grants, carry-over with the old source kept, expiry of the old source | leave | LIFE-064 to 067 (leave spanning New Year, old source retained) | **MISSING** (leave) | after the first leave grant and spend slice |
| C4 | Daily effective-date sweep: dated changes (promotion, pay change, standard-hours version, return from leave) become effective | workforce, payroll, attendance | LIFE-022 → 023, 057 → 058, 027 | workforce has due jobs (`PersonnelCommandDueJob`, `OrgStructureCommandDueJob`) [V]; `LocalDueJobs` runs them once locally [V]; payroll and attendance sweeps **MISSING** | with each slice that introduces a dated version (PY1 pay change, attendance standard hours already versioned) |
| C5 | Probation and contract end reminders | workforce | LIFE-016 (confirmation of employment), LIFE-005 (contract signed first) | **MISSING** [U: whether reminders are wanted is a business question] | after the contract zone |
| C6 | Certificate validity watch: expiry, suspension, restoration | competency, resource | LIFE-060 → 061 | **MISSING** | after the competency slice that holds certificate validity |
| C7 | Monthly individual-tax withholding filing and payment | payroll, integration | month cycle + payment node; IIT ports | **MISSING** (PY7 spec: ports and `withholding_filings`) | PY7 |
| C8 | Social-insurance monthly enrolment and base adjustment | payroll, integration | LIFE-008 (first scheme), 030 (transfer across cities) | **MISSING** (PY2 enrolments; adjustment cycle not specified) | PY2, then a separate adjustment-cycle slice [U] |
| C9 | Labour-cost period collection | cost, payroll, leave | LIFE-032 | **MISSING** | after PY4 and the labour-cost design |
| C10 | Retention purge of pay history (ten years) | payroll | none in the pack (comes from P-9 of the payroll spec) | **MISSING** (PY10 design) | PY10 |
| C11 | Approval overdue and escalation notices | approval | any approval node | exists: `ApprovalTaskBecameOverdue`, `ApprovalTaskEscalated`, jobs in identity and workforce [V] | done (keep as the pattern for the above) |
| C12 | Account and session housekeeping | identity | LIFE-037 (permissions end) | exists (`SessionPurgeJob`, `CredentialPurgeJob`, `ExpirePermissionAdjustmentsJob`) [V] | done |
| C13 | Yearly work calendar maintenance | attendance | none | event `WorkCalendarYearMaintained` exists [V]; the roll is a human command, no job [V] | no job needed unless the business wants a reminder [U] |

Pattern worth stating once (an observation, not a rule): every process that exists today is a daily job inside one module that reads its own table; the ones in the table marked MISSING that cross modules (C1, C2, C3, C9) have no existing pattern. The first of them to be built (likely C2) sets it; the sketch recommends the first one is designed with a stated trigger (date and period state) and a manual "run once" door like `LocalDueJobs` so it can be tested without waiting for a real month end.

## 4. External integrations

| # | System | Contexts | First mainline node needing it | Lead-time note | Exists today |
|---|---|---|---|---|---|
| E1 | WeCom (login, cards, handled marking) | identity, approval, integration | LIFE-009 (account for the new hire) and every approval | already live in the pilot | **exists**: `platform/integration/api/WecomGateway`, `WecomCard`, `WecomEntry` [V] |
| E2 | Bank payment (salary file or payment instruction, return and receipt files) | payroll → `payables.payment` | LIFE-040 (first payment), 041 to 043 (return, retry, duplicate receipt) | **long**: bank onboarding and file-format agreement are an outside party; start in parallel with PY4 [U] | **MISSING**; PY6 specifies a stub port so the rest can proceed |
| E3 | Individual income tax (withholding calculation, declaration, deduction information) | payroll | LIFE-014 (first monthly payroll) | **long**: channel choice (C-7 of the payroll spec) is a pending decision; statutory content is #1373 | **MISSING**; PY7 specifies ports |
| E4 | Social insurance and housing fund (enrolment, base, payment) | payroll, workforce (employer entity) | LIFE-008 (first scheme enrolment) | **long** and city-dependent: LIFE-030 crosses cities; P-5 of the payroll spec open | **MISSING**; PY2 specifies enrolments (no channel) |
| E5 | Finance system / general ledger (posting) | payroll, accounting | LIFE-014 (accrual at approval), LIFE-032 (cost) | medium: the posting spine is in-house (`accounting/period`), an outside ledger is not needed unless the business keeps one [U] | in-house period gate exists [V]; outside connector: none decided |
| E6 | Recruiting channels (job boards, resume parsing) | recruitment | LIFE-002 | low: spec holds candidates as manual entry [U: not checked in R0..R3] | **MISSING** |
| E7 | Electronic signature (contracts, certificates) | workforce (contract zone) | LIFE-005 (contract signed before the start date) | medium: `platform/signing` exists [V: directory] but its use for contracts is not specified | partial; contract use **MISSING** |
| E8 | Legacy system import (A8): employees, profiles, pay history | PY10, workforce import | go-live (no pack node) | one-off; read-only forensic entry already used for PY10 design | design in `organization-payroll.md` 8a, 8b; **import code MISSING** |

## 5. What the sketch suggests (hypothesis; lead decides ordering)

1. **Cheapest next seams** are S3 and S5 subscriptions plus S6: publishers exist, one subscriber each, and they unblock leave and payroll.
2. **Longest outside lead times** are E2, E3 and E4. They do not block the in-house slices (PY6 and PY7 specify stubs) but real adapters need contracts with outside parties; asking the user to start those conversations early costs nothing in code.
3. **Hardest ownership** is S13 (bonus basis after separation: performance writes the result, payroll pays, payment returns), S16 (re-pay after bank return) and S7 (unused leave at separation: leave computes, payroll pays, the rule is the user's T-10).
4. **First cross-module periodic process** is C2 (month close in leave); C1 depends on it. Designing C2 with an explicit trigger and a manual run-once door sets the pattern for C1, C3 and C9.
5. The frontend consumers of all of this are the document slot and the three areas in the #1516 design (PR #1521, `frontend-home-and-shared-ui.md`, not yet on main): every seam that ends in an approval needs one registered document component.

## 6. Open questions (for lead and the user; no rule decided here)

| # | Question | Options | Recommendation |
|---|---|---|---|
| Q1 | Does acting duty (temporary substitute, LIFE-025) belong to workforce as a dated fact or to identity as a delegation? | workforce fact + identity subscribes; identity delegation only | workforce fact, because it is dated and audited as employment history [U]. **lead 代定（用户授权 23:50），待用户确认**: workforce fact, identity subscribes; designed in `workforce-acting-duty.md` (#1525) |
| Q2 | Are probation and contract-end reminders wanted (C5)? | yes, a job; no, HR watches a list | list first, job only on the user's request. **lead 代定（用户授权 23:50），待用户确认**: list first, no job |
| Q3 | Which outside bank, tax channel and social-insurance channel does the business use (E2 to E4)? | user names them; defer | ask the user now so lead times start; no code depends on the answer |
| Q4 | Does the business keep an outside general ledger (E5)? | yes; no, in-house spine only | read the answer from the posting spine owner; not an HR question [U] |
| Q5 | Pack node LIFE-054 is dated 2026-12-01 but sits after LIFE-053 (2027-04-02): retroactive adjustment ordering | keep pack order; sort by effective date | evidence only; matters when the test engineer builds scenarios from the pack |

## 7. Done-when (for the test engineer)

1. Every row of sections 2 to 4 has a non-empty "exists / MISSING" cell and, where it says exists, a path that `git cat-file` / `ls` finds on origin/main.
2. The counts in section 1 (78 nodes, 14 multi-owner nodes, 59 owner changes in 77 steps, 24 monthly cases) reproduce from `mainline.json` and `monthly_cycles.json`.
3. The document carries the status line "hypothesis, revise after each slice" and contains no new rule, card or code.

## 8. Out of scope

Naming new aggregates, deciding business treatments (T-10, bonus after separation), choosing outside vendors, any code or card, the pack's own case structure.
