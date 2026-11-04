# HR boundary stability review (card #1295)

Status: draft for review, 2026-11-04, one-design. Card #1295 (user request of 2026-11-04: "is performance in organization.workforce stable", plus the other HR zones). Companions: `hr-boundary-independent-answer.md` (the independent answer to Q1–2, verbatim), draft `docs/adr/ADR-0084.md` (status proposed).

Evidence marks: **[V]** I opened the file or ran the command on origin/main, 2026-11-04. **[A]** verified only by the independent agent, I did not re-open it. **[U]** unverified: inference or missing evidence. Business treatments are not decided here; they are in section 8 for lead.

External input: user-supplied plan R001, 2026-11-04 (six contexts, 21 aggregates G01–G21). It is evidence only, never a structure to copy (CLAUDE.md baseline 1).

## 0. Rulings at a glance

| Q | Question | Ruling | Route | Cost of changing later |
|---|---|---|---|---|
| 1 | Performance (AGG-106) | **Own bounded context**, not workforce, not payroll | New ADR-0084 (proposed) partly supersedes ADR-0079 | Free today (no code). Built inside workforce first: items 1–8 of section 2.3 |
| 2a | Recruitment | **Own bounded context**; candidates never enter `employees` | ADR-0084 | Free today; highest of all zones once built inside workforce |
| 2b | Labour contract + employer entity | **Stays in workforce**; employer entity is its own dated dimension with its own names | ADR-0079 stands | Low if the names are kept apart now |
| 2c | Practice certificates | **Stays in workforce** under split-ready conditions | ADR-0084 (conditions only) | Moderate; low if the conditions hold |
| 2d | Skills | Placement **not decided now**; whether and when is business scope (B3) | ADR-0079 stands as default; ADR-0084 conditions apply at build | Zero code, zero A8 evidence |
| 2e | "Split later = move packages and tables" | **False** for this code base | ADR-0084 replaces the sentence | See section 2.3 |
| 3 | Standard hours owner | `organization.attendance`, AGG-108; timesheet holds no parameter today, so there is nothing to switch | ADR-0079 stands | Low; high only if timesheet ships its own parameter first |
| 4 | Overtime/comp-time seam vs RULE-412 | RULE-412 and FD2-05 win; ADR-0079 decision 4 row 6 is read per event meaning | ADR-0079 stands + erratum E-012 | Low |
| 5 | Leave→payroll seam | Owners fixed in section 6; seven open items must close before payroll design | ADR-0079 stands | Low |
| 6 | Plan R001 mapping, G04 | Every G-aggregate has a home; four have no aggregate in `planned.yaml`; G04 is **not** an own aggregate now, with a named trigger | No new ADR | G04 retrofit is the most expensive change in this note (section 7.3) |

## 1. Method and independence

1. A fresh opus agent answered Q1–2 first. It was told not to open `docs/adr/`, `docs/architecture/proposals/` or `docs/decisions/`. Its file header lists what it opened: none of those directories; three allowed files cite ADR-0079 by name only and it did not follow them [A]. Its answer is attached unchanged: `hr-boundary-independent-answer.md`.
2. I read ADR-0079 and its rationale only after that answer was in. Disagreements are in section 2.2.
3. ADR-0079 cites attachments (`hr-blueprint-gaps.md`, `hr-blueprint-scope.md`, `hr-blueprint-questions.md`, `hr-blueprint-review-r1..r6.md`) that PR #1219 deleted from `docs/architecture/proposals/`. They are recoverable: `git show fb798a0d1^:docs/architecture/proposals/<name>` [V]. Proposed erratum E-013 (section 9).

## 2. Q1 and Q2: where each HR zone lives

### 2.1 Facts

- [V] `backend/src/main/java/com/forcitis/one/organization/` holds only `administration`, `attendance`, `workforce`. No table, class, event or permission code exists for recruitment, labour contract, employer entity, performance, certificates or skills (the independent agent's grep over main code found none either [A]). **Every placement is free today; none stays free once a card builds it.**
- [V] ADR-0079 decision 2 puts recruitment, labour contract, certificates, skills catalogue and assessment, and performance results in workforce, as six internal zones, "日后拆分只搬包与表". Its capability table gives C2 (recruitment), C6 (contract), C7 (certificates), C8 (performance), C22 (skills) to workforce. Invariant 9: recruitment, contract, separation request and performance use single-level approval inside workforce. ADR-0051 decision 4 item 5 and consequence 4 are the source: workforce may not depend on `platform.approval`.
- [V] `module-dependencies.txt`: `platform.approval -> organization.workforce`, `platform.identity -> organization.workforce`, `platform.numbering -> organization.workforce`, `cost.timesheet -> organization.workforce`; `organization.workforce -> platform.audit, platform.integration, organization.attendance`. Workforce sits below approval, identity and numbering. ADR-0051 guard 3 states "workforce, attendance may not depend on platform.approval" [V, ADR-0051:208].
- [V] The downstream pattern already exists: `organization.administration -> organization.workforce, platform.approval, platform.audit, platform.file, platform.numbering`; `organization.leave` is planned the same way.
- [V] A8 usage (`docs/legacy/runtime-usage/a8-templates.tsv`, production read-only counts of 2026-10-14):

| Template | total | last 180d | last 365d | last use |
|---|---|---|---|---|
| Monthly evaluation, delivery staff | 2,516 | 227 | 448 | 2026-09-30 [A] |
| Monthly evaluation, BU support staff | 719 | 114 | 242 [A] | 2026-09-30 [A] |
| Monthly evaluation, headquarters staff | 244 | 53 | 92 | 2026-09-30 [A] |
| Management one-to-one | 1,738 | 0 | 0 | 2025-07 (retired) |
| Growth record | 23 | 0 | 2 | 2025-12-18 |
| Hiring approval (录用审批表) | 450 | 62 | 99 | 2026-10-10 |
| Staffing request (用人申请表) | 211 | 17 | 38 | 2026-09-21 |
| Interview evaluation (面试评估表) | 1,108 | 0 | 0 | 2021-12-06 (replaced from 2021-12-07) |

  Evaluation forms were added in 2022-01, 2023-07, 2023-08 and 2025-06 and one was retired in 2025-07 [A for the dates]: a new or retired form roughly every year. The link "evaluation result feeds the bonus" is a label in `process-inventory.md` only; no field-level link to the bonus forms (created 2026-08) exists in the repo [A, U].
- [V] Today's employee root contradicts candidates-as-employees: `employees.employee_code text NOT NULL` (`V20261014131000__organization_workforce_baseline.sql:70`), `EmployeeProfile` requires non-null `orgUnitId` (`EmployeeProfile.java:13`), `employment_status` is EMPLOYED or SEPARATED. `EmploymentStatus`, `EmployeeProfile` are imported outside workforce by identity, approval, timesheet and numbering [A for the counts].
- [V] `planned.yaml` AGG-62 still carries "状态（含候选阶段、待入职）" (candidate stage inside the person). ADR-0079 decision 2 already says "应聘者不是员工，录用生效前不建员工", so the SSOT text is stale, not a conflict with the ADR. Follow-up in section 9.

### 2.2 Disagreements between the independent answer and ADR-0079, each addressed

| # | Independent agent | ADR-0079 | Ruling here and why |
|---|---|---|---|
| D1 | Performance: own context | Performance results in workforce | **Independent wins.** Change driver (management templates, changed about yearly [A]), actors (line managers as evaluators, about 780 monthly evaluations a year [A]), lifecycle (periodic documents, about 12 a year per person) and sensitivity (ratings) differ from the employee record. The shared key is the person id. A downstream placement also lets it use `platform.approval` instead of a bespoke single-level command. |
| D2 | "Single-level approval is a placement artifact, not a domain property" | Single-level approval inside workforce (invariant 9) | **Partly agree.** True that the dependency direction forces it. But the depth itself is a user answer (Q9 A, Q10 A: single level) and stays; a downstream BC can model it as one approval node. So approval does not decide the boundary; it only removes ADR-0079's stated reason for keeping the zones inside. |
| D3 | Regularization reads performance, so a workforce-to-performance read would cycle | AGG-62 reads AGG-106 before regularizing (P-46) | **Resolved by direction.** P-46 itself says the result is "an input of regularization, not a refusal condition". So the regularize command does not read performance. The warning "no confirmed probation evaluation before probation ends" is raised by performance, reading probation end dates from workforce through an api. The screen shows both through two api calls. `performance -> workforce` only; no cycle. |
| D4 | (not raised) | Unfit-for-work chain (labour contract law art. 40(2)) must back a separation basis in workforce (Q10) | **Addressed.** A separation record stores a reference to the evaluation records by id, not validated ("不校验", the repo's existing reference mode, e.g. AGG-12 to AGG-104). Cost: the system does not prove the referenced chain exists; HR confirms it, as it does today. |
| D5 | Recruitment: own context | Recruitment is a workforce zone; candidates are not employees; hire is called into the core zone | **Independent wins, but not on its main argument.** Its argument "candidates inside AGG-62 break the schema" fights the stale SSOT text, not ADR-0079. The real reasons: external persons whose data needs a short retention versus permanent employee history; the zone needs `platform.approval`, `platform.file` and numbering that workforce cannot depend on; and the zone sits under the same persisted names and permission codes as the employee core. |
| D6 | Labour contract and employer entity stay in workforce | Same | Agree. Same actors (HR), same lifecycle (the contract gates the employment), no A8 approval flow in use (contract approval form: 0 rows [A]; only a renewal-intention form, 42 in 365 days [A]). |
| D7 | Rename `EmployeeLegalEntities`, `EmployeeLegalEntityChanged`, `CHANGE_LEGAL_ENTITY` before any employer dimension exists | Invariant 4 keeps three legal-entity meanings apart; section 4: "已建事件保留名称" | **Disagree on the rename.** ADR-0073 item 3.7 defines the existing event as the cost/data-scope entity (the javadoc of `EmployeeLegalEntityChanged` says "cost legal entity (ADR-0073 item 3.7)" [V]), so the name is not unqualified, it is ADR-0073's. A rename hits persisted event class names (the agent's own item 1). **Agree on the substance:** the employer entity gets its own table, api query and event with distinct names (glossary decides the words), never an extra meaning on the existing event. |
| D8 | Certificates: workforce with guardrails, build after the dat1020 result | C7 in workforce | Agree on placement with the guardrails. Timing and the missing dat1020 result go to lead (B4). |
| D9 | Skills: not workforce; defer to its consumer | C22 in workforce (user Q13 C put skills in scope) | **Not decided here.** Skills have no aggregate in `planned.yaml` [V grep], no A8 form [A], no code. The user put C22 in scope (Q13 C), so whether and when is business. With zero code, the placement costs nothing at the build card. ADR-0084's conditions bind it then. |
| D10 | Fallback: one shared "talent" context for performance and recruitment | none | **Reject the fallback.** External pre-employment candidates and employed evaluatees share nothing but the word HR; one context would mix two retention regimes and two actor sets. If lead wants fewer modules, performance alone is the better one to keep out of workforce. |
| D11 | "Split later = move packages and tables" is false | Stated as true | **Independent wins** (2.3). |
| D12 | Side finding: personal data in `a8-templates.tsv` | — | Section 8, item R1. |

### 2.3 Is "split later = move packages and tables" true? No

Each item is a cost of splitting a zone that was first built inside workforce.

| # | Cost | Evidence | Mark |
|---|---|---|---|
| 1 | Persisted event class names: `event_type` is "事件类的全名", `listener_id` the method's full name; archive table likewise. Moving an event class orphans open and archived publications | `V20261014133000__platform_integration_baseline.sql:25,115` | V |
| 2 | Permission codes carry the module name `{subdomain}.{bc}:{resource}:{action}`, enforced by `NamingSurfaceTest` G43; codes live in identity menu migrations | `NamingSurfaceTest.java:197`; identity migrations | V (G43), A (menu rows) |
| 3 | The shared append-only `state_transitions` has `CHECK aggregate_type_code IN ('PersonnelCommand','SeparationHold','OrgStructureCommand','AttributionCountersign')`; update, delete and truncate are refused by trigger; retention is permanent | `V20261102160000__...attribution_countersigns.sql:179`; baseline triggers | V (check), A (triggers) |
| 4 | Dated rows written by personnel commands carry FKs to `personnel_commands` and are written in the same transaction; splitting turns that into a cross-module protocol | `employee_legal_entities`, `employee_versions` common set | A |
| 5 | Audit subject kinds record their owning module | `V20261014134000__platform_audit_baseline.sql` | A |
| 6 | Shared api types are the coupling surface; pushing a field into `EmployeeProfile` or `OrgDirectory` changes every consumer again at split time | imports in identity, approval, timesheet, numbering | A |
| 7 | Error codes `ORGANIZATION_WORKFORCE_*` are visible to clients | `WorkforceError` | A |
| 8 | Acceptance tests of workforce are governed (an implementer may not change them); the three registries (`module-dependencies.txt`, `data-access-registry.yaml`, `NamingSurfaceTest`) change in the same PR | CLAUDE.md baseline 4 | V |

Not verified by anyone: whether HTTP paths include the module name (G41) [U]. Encapsulation today is clean: no cross-schema FK points into `organization_workforce`, no other module reads its tables, events carry ids and dates only [A]. A zone that, from its first card, has its own tables without FK to `personnel_commands`, no rows in `state_transitions`, its own permission resource, its own api types and its own event classes is a separate context in all but package name. ADR-0084 turns that list into conditions for the zones that stay.

### 2.4 Q1 Performance: options, ruling

| Option | For | Against |
|---|---|---|
| (a) stay in workforce | no new module; ADR-0079 as written | adds the most volatile HR zone to the most depended-on business module (about 16.6k lines [A], imported by identity, approval, timesheet, numbering); bespoke single-level command; rating access mixed with roster access under one permission prefix; invites the `state_transitions` CHECK extension and FKs to `personnel_commands`; every item of 2.3 later |
| (b) inside payroll | payroll is the main consumer | evaluators (all line managers) would need write access inside the salary context, the most sensitive data in HR; probation evaluations feed regularization, not pay; the bonus link is unproven (2.1) |
| (c) own context | drivers, actors, cadence, sensitivity separate; downstream placement uses `platform.approval` | one more module (registry rows, schema, permission prefix) |

**Ruling: (c).** Edges: `performance -> workforce (api), platform.approval, platform.audit`; `payroll -> performance (api)` for the bonus basis, which replaces the planned subscription `workforce -> payroll` of "绩效评估已确认" in ADR-0079 decision 4. The BC name is for one-glossary (working name `organization.performance`). Cost today: an ADR and registry rows. Cost later if built in workforce: all of 2.3 plus the event the payroll bonus subscribes to.

### 2.5 Q2 recruitment, contract, certificates, skills

- **Recruitment: own context** (working name `organization.recruitment`). Candidates are external persons; their records have their own retention (the period is a question for the user per ADR-0079 Q9, not set here); requisition and offer approval use `platform.approval` (still single level per Q9 A, one node). Hand-off: on acceptance recruitment issues a HIRE personnel command through the workforce api; workforce's hire, numbering and account opening stay untouched. Edge `recruitment -> workforce`, acyclic. Candidates are never rows of `employees`.
- **Labour contract and employer entity: stay.** Condition (ruling, no ADR needed): the employer entity is a separate dated dimension with its own table, api query and event; leave RULE-515 and payroll social insurance subscribe to that, never to `EmployeeLegalEntityChanged`. A later multi-level contract approval moves only the document workflow into a downstream context, which then enters employer-change personnel commands; the employer dimension stays in workforce. Cost of getting it wrong later: high if the employer entity is built in payroll or a contract context first (cycle), or merged into the data-scope attribution (rename of a persisted event and an api type with identity and timesheet as consumers).
- **Certificates: stay**, under the ADR-0084 conditions, plus: the registered unit is its own column referencing `legal_entities` (not the attribution); one narrow read api (qualification by person, type and date) for the delivery sign-off (INV-47, AGG-27 in `delivery.acceptance`); nothing added to `EmployeeProfile` or `OrgDirectory`. Where certificates live today is missing evidence: the discovery query `docs/data/evidence/dba-handoff/dat1020-discover.sql` exists, its result is not in the repo [A, U].
- **Skills:** see D9. No ruling on placement or timing.

## 3. Q3: standard hours, single owner and switch path

- Owner: `organization.attendance`, AGG-108 standard-hours version. Sources: ADR-0051 decision 4 item 5, ADR-0079 C9, `planned.yaml` AGG-108 [V]. It is planned, not built: `organization/attendance/api` holds only WorkCalendar, DayKind, DayException, LockedDayChange, AttendanceError [V].
- The card text assumed timesheet holds its own minimum-hours parameter. **It does not.** `grep -riE "standard.?(hours|minutes)|minimum.?(hours|minutes)|min_minutes|dailyMinutes"` over `cost/timesheet` returns nothing [V]; `timesheet-filing-rules.md` says "the old system's minimum-hours check is not implemented until Phase 2" [V]. RULE-398 says "the standard day is the per-entity parameter (7.5 by default)" from FD-10 [V].
- So there is nothing to switch. The risk is the RULE-398 implementation card inventing a timesheet parameter with a default of 7.5, which creates a second owner and breaks INV-116 ("no default value").
- Ruling, ADR-0079 stands: leave (RULE-510), timesheet (RULE-398) and payroll (hourly rate) read `StandardHours.minutesOn(legalEntityId, date)`; no literal 450/480/7.5 in main code. The values to seed are the user's (FD-10 is the only value on record, 7.5, configurable per legal entity). The prerequisite card is the draft "card A" already in lead's notes (L0' in the leave slice plan). A stop-gap parameter in timesheet before AGG-108 exists is the option to refuse: it costs a second owner plus a value migration for no earlier delivery, because RULE-398 also needs the leave read api that does not exist yet.
- Cost of changing later: low now; if timesheet ships its own parameter first, two readers plus a migration of per-entity values.

## 4. Q4: overtime approval and comp-time balance, ADR-0079 decision 4 vs RULE-412 / FD2-05

- [V] ADR-0079 decision 4, row 6: "请假批准与撤销、加班批准、月度考勤锁定 | leave | payroll、cost.timesheet". Read literally, timesheet subscribes to overtime approval.
- [V] RULE-412 (P0, source user decision FD2-05, 2026-10-16): the timesheet context never reads an overtime approval or a comp-time balance; the user's words are "不关联 … 加班审批与调休交假勤，之后再对接". RULE-413: comp time is a leave type, and the timesheet reads only the leave hours of the day (api `LeaveHours.forDay`), never the balance. `TimesheetOvertimeSeamGuardTest` (`timesheetClassesDoNotDependOnLeave`, `moduleGraphHasNoEdgeToLeave`) forbids any edge today [V].
- The ADR's approval evidence (intents of 2026-11-02) does not cite FD2-05 [V, header of ADR-0079], and the ADR states that it keeps user decisions.

| Option | Cost |
|---|---|
| (a) Read row 6 per event meaning: the subscriber list names every subscriber of any event in the row; timesheet subscribes only to leave approval, rejection, withdrawal and recall; overtime approval and the monthly lock go to payroll only. Record it as erratum E-012 | One line in `readme-deviations.md`; no ADR. If one-architecture judges it changes meaning, it moves into a new ADR |
| (b) A new ADR amending one table cell | An ADR for one cell: refuse (entropy) unless (a) is rejected |
| (c) Accept the subscription and amend RULE-412 | Contradicts a user decision; only the user can do it |

**Ruling: (a), ADR-0079 stands.** What the leave→timesheet edge may carry is fixed by "card B": the api `LeaveHours.forDay` and the four leave events; the guard stays red for any overtime or comp-time type. If timesheet later needs the monthly lock or overtime, that is a new user decision (RULE-412 says "之后再对接"). Cost of changing later: low (one guard and the event list).

## 5. What ADR-0084 changes, and what it leaves

| Clause | Today | ADR-0084 (proposed) |
|---|---|---|
| ADR-0079 decision 1, rows C2 and C8 | owner workforce | recruitment and performance contexts |
| ADR-0079 decision 2, "沿用 ADR-0051，不新增人事上下文" | no new HR context | two new contexts |
| ADR-0079 decision 2, workforce row (recruitment, performance results) | owned by workforce | removed from that row |
| ADR-0079 decision 2, "日后拆分只搬包与表" | stated as true | replaced by the split-ready conditions (2.3) |
| ADR-0079 decision 4, row "绩效结果确认" | workforce publishes, payroll subscribes | performance owns it, payroll reads by api |
| ADR-0079 invariant 9 | recruitment, contract, separation request, performance single-level inside workforce | contract and separation request stay; recruitment and performance use `platform.approval` with one node |
| ADR-0051 decision 3 BC list; decision 4 item 5, recruitment sentence | 28 BCs; recruitment approved inside workforce | two more BCs; recruitment approval via `platform.approval` |
| Left as is | C6, C7, C22 placement; invariants 1–4; decision 4 row 6 (E-012); ADR-0079 decision 5 | |

## 6. Q5: the leave→payroll seam, owners before payroll is designed

Full payroll design stays the W4 card (B-15, AGG-73, 93, 119, with AGG-95 and AGG-120). Owners of every fact payroll needs, with what is built (the leave side is detailed in `organization-leave.md` section 9a):

| Fact payroll needs | Writer | Reader | Built? | Open |
|---|---|---|---|---|
| Employment dates, phase, unpaid-leave start, separation date | workforce | payroll, leave | yes (unpaid-leave end and prior service: no, card C of lead's notes) | end event or as-of read |
| Employer entity (basis of social insurance, RULE-515) | workforce, labour-contract zone | payroll, leave | **no** | in scope now? (B6) |
| Withholding entity (扣缴义务人; key of AGG-73) | payroll | payroll | no | none: ADR-0079 invariant 4 |
| Cost / data-scope entity | workforce (AGG-117, `EmployeeLegalEntityChanged`) | payroll (mismatch warning only), cost | yes | none |
| Standard hours | attendance (AGG-108) | payroll hourly rate, leave, timesheet | **no** | seed values: the user's |
| Leave minutes by type, overtime minutes by compensation mode, attendance days; the month lock | leave (AGG-97 snapshot) | payroll reads snapshots only | no | which leave types deduct and at what rate: payroll W4 rule |
| Comp-time expiry and conversion | leave event `LeaveCompTimeExpired`; payroll registers one conversion per ledger line | payroll | no | none |
| Unused annual leave at separation | leave read api on `EmployeeSeparated` | payroll | no | whether it is paid: T-10, the user's |
| Evaluation result as bonus basis | performance (ADR-0084) | payroll AGG-76 | no | does it exist in the legacy flow? (B2) |
| Salary agreement | payroll AGG-93 only; the contract cites a version, never a value (ADR-0079 decision 2) | — | no | none |
| Payment | `payables.payment`; payroll submits one payable per legal entity and month | — | no | none |

Direction check: `payroll -> workforce, attendance, leave, performance, platform.approval, payables.payment`; no module among them depends back on payroll. **Ruling, ADR-0079 stands** (one change: the bonus basis comes from performance by api, section 2.4). **Open items before the payroll design card:** employer entity fact (B6), standard-hours values, deduction rates per leave type, T-10, the bonus link (B2), AGG-97 grain (minutes by type and compensation mode, `is_paid` copied at generation), unpaid-leave end read. Cost of changing later: low while payroll has no code.

## 7. Q6: plan R001 against our contexts

### 7.1 Mapping of G01–G21

| Plan | Home today (`planned.yaml` aggregate, context) | Built? | Note |
|---|---|---|---|
| G01 person | AGG-62 person, workforce | employee: yes | ours is "a natural person who was ever employed", one employee for life (ADR-0079 decision 3 item 1, ADR-0078); a candidate or an external person without employment is not one; duplicate-person merge is left to a card (ADR-0079 scope) |
| G02 position | AGG-64 position catalogue and grade, workforce | yes (positions) | no headcount (Q-08) |
| G03 employment contract | AGG-67 labour contract, workforce | no | only for regular staff; interns, retirees, dispatched staff have none (ADR-0079 decision 3 item 3) |
| G04 employment relationship | **no own aggregate**: employee versions of AGG-62 + AGG-67 + planned employer entity | partly | section 7.3 |
| G05 assignment | AGG-63 assignment, workforce | yes | primary assignments do not overlap, part-time unlimited (ADR-0079 invariant 2) |
| G06 requisition | AGG-65, recruitment (ADR-0084) | no | |
| G07 application | **no aggregate** (AGG-62 candidate stage is stale, 2.1); recruitment | no | name it when recruitment is scheduled |
| G08 appraisal | AGG-106, performance (ADR-0084) | no | |
| G09 work calendar | AGG-68, attendance | yes | |
| G10 attendance record | AGG-69, leave | no | plan "timekeeping" = our attendance + leave |
| G11 leave entitlement | AGG-71 balance account (ledger, see `organization-leave.md`), AGG-118 rules, leave | no | |
| G12 leave/attendance item | AGG-70 leave request, AGG-72 overtime, AGG-97 monthly confirmation, leave | no | |
| G13 pay agreement | AGG-93, payroll | no | |
| G14 insurance and fund | AGG-74 enrolment, AGG-95 filings, payroll | no | |
| G15 special additional deductions | AGG-120, payroll | no | |
| G16 payroll run | AGG-73 monthly payroll (withholding entity x month), payroll | no | partial payment, failure, re-pay: payment side is `payables.payment` (ADR-0079 decision 2) |
| G17 gross pay | **inside AGG-73** as person pay lines, not an aggregate | no | plan models it as a root; we keep lines under the run |
| G18 skill catalogue | **no aggregate**; ADR-0079 C22, workforce competency zone | no | D9 |
| G19 qualification catalogue | **no aggregate**; AGG-66 is the held certificate only | no | the catalogue of qualification types is missing |
| G20 person's skill | **no aggregate** (C22 "skill assessment result") | no | |
| G21 person's qualification | AGG-66 practice certificate, workforce | no | |

**No aggregate in `planned.yaml` for G07, G18, G19, G20** (all have a capability or zone home, none has a root yet). Ours without a plan counterpart: AGG-117 legal-entity attribution, AGG-75/76 incentive and bonus, AGG-108 standard hours, AGG-118 leave rules, AGG-97 monthly confirmation, separation request (a command of AGG-62), and the withholding and cost entity meanings. Where the plan differs structurally: its "timekeeping" is two contexts for us; its "competency" is a workforce zone; its resource planning is outside both (ADR-0079: delivery reads employment, leave, certificates and skills by api).

### 7.2 Plan claims checked

- "External professionals' pay does not default into payroll": consistent with ADR-0079 invariant 6 (dispatched staff are not enrolled or paid until the user confirms) and AGG-95 covering non-employee income [V].
- "Qualification change does not stop allowances automatically": no allowance object exists in our SSOT; no conflict [U].

### 7.3 G04 against the employee-version model

Facts:
- [V] One employee per natural person for life, id and code never change, re-hire reuses both (ADR-0079 decision 3 item 1, ADR-0078). Employment is the run of `employee_versions` with `employment_status` EMPLOYED/SEPARATED, `employment_phase_kind` PROBATION/REGULAR, `is_on_unpaid_leave`, `employment_start_date`; versions of one employee never overlap (`ex_employee_versions_overlap`, `V20261014131000...baseline.sql:700`). The employee's legal entity is a dated single value (`ex_employee_legal_entities_overlap`).
- [V] The employment type (regular, intern, retiree re-hire, dispatched; ADR-0079 C3) is **not built**: no column, no api type, no class (grep over workforce main code and migrations is empty).
- [V] Contracts: one effective contract per person and employer and day; several employers allowed for part-time (labour contract law art. 69; ADR-0079 decision 3 item 3). The employer dimension is planned (C5, C6), not built.

What the plan's G04 adds, case by case:

| Plan case | Our model |
|---|---|
| Single | One run of versions |
| Adjacent (intern then regular, leave then re-hire) | Supported: type change is a new version, re-hire is `REINSTATE` on the same employee |
| Multiple, sequential | Supported as above |
| **Overlapping** (one person employed by two employers at once, or employee plus an externally settled professional) | **Not supported**: versions cannot overlap, the employer is one value per day. Only contracts and assignments can overlap |

Options:
- (a) **Keep the employee-version model**; put the employment type on the version, the employer as a dated dimension (Q2b). Overlapping employment is excluded until the business needs it.
- (b) Add the plan's relationship aggregate between employee and versions: `employee -> relationship(period, employer, type) -> versions`. It gives a stable id per stint and allows overlap.
- (c) Put employer and type on the contract. Fails: interns, retirees and dispatched staff have no labour contract.

**Ruling: (a) now, with a trigger.** (b) buys exactly one thing, overlap (a stint id is derivable from a run of versions), and no evidence of overlapping employment exists in our population [U]. Cost of (b) later is the highest in this note: `employee_versions` gains a relationship key, personnel commands and events target a relationship, and the single in-service judgement (ADR-0079 invariant 1, consumed by identity, approval, timesheet) must be rewritten around it. **Trigger:** the user says one person can be employed by two employers (for example two group entities) at once, or an externally settled professional is to be modelled as an employee. It must be answered **before the card that builds the employment type** (B5); that card is the last cheap point to choose. A read-only aggregate fact fork exists if lead wants evidence first (B5, option 2).

## 8. Questions and findings for lead (none decided here)

Business questions, with options and recommendation:

| # | Question | Options | Recommendation |
|---|---|---|---|
| B1 | Is recruitment built at all, and in which stage? ADR-0079 lists it as capability C2, the stage is unset | (a) not in W0–W4; (b) after W4; (c) earlier | No preference: scope is the user's. Technical default if built: own context (Q2a) |
| B2 | Do evaluation results decide bonus amounts, and how? The repo has only a label, no field-level link | (a) yes, by a rule the user states; (b) results are records only, bonus stays manual; (c) read the A8 forms' definitions (aggregate, read-only, same shape as `leave-quota-l0.sql`) to find the link | (c) first, then ask |
| B3 | Skills catalogue and assessment (C22, user Q13 C): when, and who consumes it (delivery resource plan)? | (a) with the delivery resource plan; (b) after W4; (c) drop | (a): build with its consumer |
| B4 | Practice certificates: where do they live today and at what volume? The dat1020 result was never committed | (a) rerun `dat1020-discover.sql`, aggregates only; (b) defer certificates until delivery sign-off (INV-47) needs them | (a) |
| B5 | Can one person be employed by two employers at once, or do externally settled professionals count as employees? Decides G04 (7.3) | (1) no, ever: (a) stays; (2) unknown: aggregate query over the legacy employee archive (count of people with two concurrent employers, no names); (3) yes: open a design card for (b) before the employment-type card | (2), then decide |
| B6 | Is the employer entity in scope now (labour contract, ADR-0079 C5/C6)? Leave RULE-515 and payroll social insurance wait for it | (a) now, with the leave slices; (b) with payroll W4; (c) leave scope stays "all entities" | (a) or (b); the user decides |

Findings and actions that are not business questions:

| # | Finding | Action |
|---|---|---|
| R1 | `docs/legacy/runtime-usage/a8-templates.tsv`: 3 rows of the application-form template (应聘登记表) have names containing gender or age words [V count; I did not print them]; the independent agent read them as candidates' names, gender and age [A]. If so, it breaks baseline 2 (no personal data in the repo) | A repository-hygiene card (not mine, file scope): redact the 3 template names, check the other `runtime-usage` files and git history policy |
| R2 | `planned.yaml` AGG-62 candidate stage and `contexts.yaml` notes of payroll/incentive contradict ADR-0079 decision 2 and, after adoption, ADR-0084 | SSOT follow-up card after ADR-0084 is adopted (the ADR must not be cited as decided before) |
| R3 | ADR-0079 cites deleted attachments | Erratum E-013 (section 9) |
| R4 | The timesheet seam guard card ("card B") and the standard-hours card ("card A") are drafted in lead's notes; neither opens before PR #1283 merges | no new action |

## 9. Rulings and errata proposed in this PR

- `docs/adr/ADR-0084.md` (proposed): performance and recruitment leave workforce; the split-ready conditions replace "日后拆分只搬包与表". It is a proposal: other documents must not cite it as decided (`readme-lifecycle.md` section 8.4). Adoption needs the user.
- `docs/adr/readme-deviations.md`, errata table (one-architecture decides if either changes meaning; if so E-012 moves into a new ADR):
  - E-012: ADR-0079 decision 4 row 6 subscriber list read per event meaning, source RULE-412 and FD2-05.
  - E-013: ADR-0079 attachments removed by PR #1219; read them at `fb798a0d1^`.
- Not changed: any adopted ADR body, any rule, any code, `contexts.yaml`, `planned.yaml`.

## 10. Done-when (for the independent test engineer; spec only, no code in this PR)

1. This note answers Q1–6, each with evidence marks, options, a recommendation, the cost of changing later; the independent answer is attached unchanged and every disagreement is in 2.2.
2. For each question the route is stated: "ADR-0079 stands" with a reason, or ADR-0084 with the superseded clauses listed in its `取代` field and each clause present in ADR-0079 (the test engineer opens ADR-0079 and checks the section and item numbers).
3. Business questions are only in section 8; no sentence decides one.
4. Every [V] mark holds: spot-check any five by re-running the command or opening the cited file line.
5. Standard hours: a later implementation card is correct only if `grep -rniE "7\.5|450|480"` over main code finds no standard-day default and the missing-version case returns an error (INV-116).
6. Seam: the later guard card is correct only if the guard stays red when a test copy of timesheet imports any `organization.leave..` type other than the allowed api and the four events (break test), and RULE-412 types stay unreadable.
7. Split-ready conditions (ADR-0084): the first card of any zone that stays in workforce lists the six conditions in its done-when, and its migration passes the data-design gate (`docs/data/data-design-gate.md`).
8. `scripts/ci/light.sh` is green on the PR head; the opus review verdict names the same head.

## 11. Evidence register

[V] commands used: `ls backend/src/main/java/com/forcitis/one/organization`; `module-dependencies.txt` lines 9–24; migrations `V20261014131000` (employees, `employee_versions`, exclusion constraints), `V20261102160000` (`state_transitions` CHECK), `V20261014133000` (event_publication comments); `NamingSurfaceTest.java:197`; `EmployeeProfile.java:13`; grep for standard/minimum hours over `cost/timesheet` (empty); grep for employment types over workforce main code (empty); `a8-templates.tsv` rows; ADR-0079 lines 77–135, ADR-0051:96,154,208; `overtime-seam-rules.md`; `timesheet-filing-rules.md` RULE-398; `git show fb798a0d1^`.
[A] everything marked A relies on the independent agent's file. [U] items are marked inline.
