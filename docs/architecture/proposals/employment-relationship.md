# Employment relationship and employer entity (design, #1308)

Status: proposal for the user's adoption through ADR-0086 (proposed). Author: one-design, 2026-11-04. Combines follow-ups F1 and F2 of [hr-boundary-stability-review.md](hr-boundary-stability-review.md) section 8a. No code, no migration here: table shapes below are the data design to be signed under `docs/data/data-design-gate.md` before the schema PR.

Evidence marks: **[V]** verified by me in the repo or against the cited source on 2026-11-04; **[U]** unverified or only secondary sources; **[B]** a business question, not mine to decide (section 9).

## 0. What is decided here

| # | Question | Ruling (recommendation to the user) | Cost of changing later |
|---|---|---|---|
| 1 | Is there an aggregate between the person and the versions? | Yes: **EmploymentRelationship**, owned by workforce, identity = one person employed by one employer entity; many per person, overlap across employers allowed | Highest in the HR model; today nothing but the versions depends on it, so now is the cheap time |
| 2 | Is the employer entity a dated dimension? | No: the employer is **immutable on the relationship**. Changing employer inside continuous service is a new relationship that names its predecessor. This partly supersedes ADR-0084 decision 2 ("dated dimension") | A dated employer would need a second timeline per relationship that every consumer must slice by date |
| 3 | Where does "in service" live? | Still one place, workforce. Person in service on D = at least one relationship in service on D. Existing person-level api and events keep their meaning | Consumers (identity 20, approval 9, timesheet 1 files [V]) stay untouched |
| 4 | What attaches per relationship? | Leave entitlement, social-insurance enrolment, IIT withholding, probation and unpaid-leave state, labour contract. Data scope and cost entity stay per person (ADR-0073) | Payroll and leave are unbuilt; keying by relationship id from day one costs nothing |
| 5 | Migration | One relationship per existing employee, employer backfilled from the person's attribution entity and **flagged for HR confirmation** [B] | Reversible: the flag column |

## 1. Facts [V]

- `employees`: one row per natural person for life (ADR-0078); `employee_versions` holds EMPLOYED/SEPARATED, PROBATION/REGULAR, unpaid-leave flag, employment start date; exclusion `ex_employee_versions_overlap` is **per employee** (`V20261014131000…baseline.sql:700`).
- `personnel_commands` (HIRE, REGULARIZE, TRANSFER, SEPARATE, REINSTATE, CORRECTION, UNPAID_LEAVE_*) are keyed by `employee_id`; `employee_assignments` / `assignment_versions` have PRIMARY and CONCURRENT kinds with a per-employee exclusion (`…assignment_version_common_set.sql:29`).
- `employee_legal_entities` is the person's data-scope and cost entity timeline (ADR-0073 item 3.7), non-overlapping per employee. It is **not** an employer: nothing in workforce stores an employer today (ADR-0079 invariant 4 names three legal-entity meanings; only attribution is built).
- Consumers of the in-service verdict outside workforce: `platform.identity` 20 files, `platform.approval` 9, `cost.timesheet` 1 (grep of `EmploymentStatus|EmploymentVerdict|EmployeeProfiles|OrgDirectory|EmployeeLegalEntities`, 2026-11-04).
- Published workforce events: EmployeeHired, EmployeeRegularized, EmployeeTransferred, EmployeeSeparated, EmployeeReinstated, EmployeeCorrected, EmployeeLegalEntityChanged. `platform.identity` subscribes to Hired and Separated (account opening and release).
- `backend/architecture/migration-baseline-open` exists, so the migration guard only warns while the baseline is open; this design still uses additive migrations.
- The user's facts (issue #1295, comment 6638890281 and 6639148601): one person is employed by two legal entities at once, today; the second entity keeps its enterprise qualification; social insurance, IIT withholding and leave entitlement are computed per relationship.

## 2. Options

**Relationship identity.** (A) *employee x employer, rehire reuses it* (recommended). (B) one relationship per employment period (rehire = new). (C) person-level only plus a dated employer attribute (ADR-0084 decision 2 as written).
- A: rehire by the same employer keeps today's behaviour exactly (ADR-0078: same employee, same code; versions continue with a SEPARATED gap), so migration is one row per employee and the versions need only a new key. Changing employer is a new relationship with `predecessor_relationship_id`.
- B: every rehire needs a new relationship and the leave/probation carry-over rules per rehire. More rows, no benefit seen in evidence.
- C: cannot hold two simultaneous employers on one timeline, which is the user's fact. Rejected.

**Where the status versions live.** (A) add `employment_relationship_id` to `employee_versions` and move the non-overlap exclusion from per employee to per relationship (recommended: versions are already append-only with the full state; the table name then lags its meaning, rename in the same schema PR). (B) a new relationship-versions table and keep `employee_versions` as a person-level summary: two writers of one fact, rejected.

**Employer change.** (A) immutable employer, successor link (recommended). (B) dated employer on the relationship (ADR-0084 decision 2). A fixes the key of leave quota and social-insurance enrolment to one employer, so those facts never straddle two employers. Legal source for continuity of leave years: section 8.

## 3. Answers to (i)-(vi) of review note 7.3

(i) **In-service judgement.** Stays one function in workforce: person in service on D iff some relationship has an EMPLOYED version on D. `EmploymentVerdict`, `EmployeeProfiles`, `OrgDirectory` do not change. New api `EmploymentRelationships` answers per relationship; identity, approval, timesheet keep reading the person level.
(ii) **Primary relationship for data scope and cost.** None needed: attribution (`employee_legal_entities`) stays person-level and independent, as ADR-0079 invariant 4 already says ("changing one does not change the other two"). Payroll's withholding entity and social-insurance entity come from the relationship's employer.
(iii) **Per relationship or per person.** User ruled per relationship for social insurance, IIT withholding, leave entitlement. This design keys them by `employment_relationship_id`. Legal check: section 8.
(iv) **Employee id and code.** Stay per person for life (ADR-0078).
(v) **Employer attaches** as `employer_legal_entity_id` on the relationship, immutable, referencing `legal_entities` (same schema, FK allowed). The labour contract zone (C6) later references the relationship id and carries no employer of its own, so the contract's employer cannot disagree with the relationship's.
(vi) **ADR**: ADR-0086 (section 10).

## 4. Model

Aggregate **EmploymentRelationship** (root) in `organization.workforce`.

```
employment_relationships                 -- append-only except nothing is updated
  employment_relationship_id  uuid PK
  employee_id                 uuid NOT NULL FK employees
  employer_legal_entity_id    uuid NOT NULL FK legal_entities
  predecessor_relationship_id uuid NULL FK employment_relationships   -- same employee; employer change in continuous service
  employer_source_kind        text NOT NULL CHECK ('DECLARED','MIGRATION_ATTRIBUTION')  -- MIGRATION_ATTRIBUTION = backfilled, awaiting HR confirmation
  created_at, updated_at      timestamptz
  UNIQUE (employee_id, employer_legal_entity_id)
  UNIQUE (employment_relationship_id, employee_id)     -- composite FK target
  CHECK predecessor is a different relationship of the same employee (trigger or composite FK)
```

`employee_versions` (renamed `employment_relationship_versions` in the schema PR, name to be proposed in the glossary): add `employment_relationship_id NOT NULL`, composite FK `(employment_relationship_id, employee_id)`; exclusion `EXCLUDE (employment_relationship_id WITH =, daterange(effective_from_date, effective_to_date) WITH &&) WHERE superseded_at IS NULL` replaces the per-employee one. All other columns and checks stay.

`personnel_commands`: add `employment_relationship_id` (NOT NULL after apply; for HIRE the id is allocated at entry and the relationship row is created when the command takes effect, so the FK is nullable only while HIRE is PENDING/HELD) and `target_employer_legal_entity_id` (HIRE only, NOT NULL for HIRE). Same-employee consistency by composite FK.

`assignment_versions`: add `employment_relationship_id NOT NULL`. The PRIMARY non-overlap rule stays **per person** (ADR-0079 invariant 2 unchanged): one person has at most one primary assignment on a day across all relationships. A relationship may have no assignment at all (see B-A).

Unchanged: `employee_legal_entities` (data scope and cost), `employees` (snapshot columns stay derived from the person-level verdict).

Invariants:
1. One person x one employer = one relationship; two relationships of one person may overlap in time; versions of one relationship never overlap.
2. The employer of a relationship never changes; a different employer is a new relationship.
3. A person is in service on D iff some relationship is in service on D (the single judgement, ADR-0079 invariant 1, restated).
4. Social-insurance entity and withholding entity of a pay run come from the relationship's employer (the pay agreement may name another paying entity, which payroll records as the third meaning); attribution is unchanged.
5. No relationship, no employer: HIRE always names the employer; there is no default employer.

## 5. Existing commands bind to a relationship

| Command | Binding |
|---|---|
| HIRE | New person: creates employee and relationship. Existing person and a new employer: creates a second relationship (an "additional employer" hire; not a rehire). Existing relationship ended: this is REINSTATE |
| REGULARIZE, UNPAID_LEAVE_START/END | Act on one relationship |
| TRANSFER | Org-unit/position change on the relationship's assignment; if the target belongs to another employer entity this is not a transfer: it is SEPARATE on the old relationship plus HIRE with `predecessor_relationship_id`, same effective date |
| SEPARATE, REINSTATE | One relationship. Person-level separation (events `EmployeeSeparated`, account release) happens only when the last in-service relationship ends |
| CORRECTION | Corrects versions of one relationship |
| Concurrent assignment (CONCURRENT kind) | Unchanged: within the person; binds to the relationship that employs the person in that unit's entity |

## 6. Events and reads

- Existing events keep their classes and meaning at **person level**: EmployeeHired fires when the person goes from not in service to in service, EmployeeSeparated when the last relationship ends. For a one-relationship person nothing changes (migration case). Payload gains `employmentRelationshipId` (additive).
- New relationship-level events, proposed names in `backend/glossary/proposed/employment-relationship.yaml`: EmploymentRelationshipStarted, EmploymentRelationshipEnded, plus phase and unpaid-leave changes carried by the same envelope. Leave and payroll subscribe to these, not to the person-level ones.
- New api `EmploymentRelationships` (workforce): `on(employee, date)` returns the relationships in service that day (id, employer legal entity, phase kind, unpaid-leave flag); `get(relationshipId)`; `employerOn(relationshipId)` is a plain getter because the employer is immutable.
- **Leave slice L3 / RULE-515**: leave entitlement and quota are keyed by `(employmentRelationshipId, leaveYear)`. A request names the relationship it is taken from (a person with one relationship in service gets it defaulted, a person with two must choose; choosing is not guessed). `employerOf(employee, date)` as a scalar is **not offered**: with two relationships it would be ambiguous. Leave days that fall on a day where the relationship is not in service are refused (RULE-510 split uses the relationship's employer's standard hours: `StandardHours.minutesOn(employerLegalEntityId, date)`, #1310).

## 7. Migration of today's rows

One relationship per employee; `employer_legal_entity_id` = the person's attribution entity at the first version date, `employer_source_kind = MIGRATION_ATTRIBUTION`. Employees with no attribution timeline cannot be backfilled: they go to a reject list for HR (the attribution already refuses to guess, `EmployeeLegalEntities.on`). Existing versions, commands and assignments gain the id by join on employee. Counts and the reject list are aggregates only, produced in the migration card [U: not run]. HR confirms or corrects the backfilled employer before payroll or leave use it [B-B].

## 8. Legal check

| Claim | Source | Mark |
|---|---|---|
| IIT on wages is withheld by each paying unit separately, using the cumulative method over the taxpayer's months **in that unit** (cumulative deduction = 5,000 x months in this unit) | 个人所得税扣缴申报管理办法（试行）(国家税务总局公告2018年第61号) Art. 6, read at https://www.gov.cn/zhengce/zhengceku/2019-10/28/content_5445955.htm | **[V]** quoted from the official text |
| Income from two units is combined at annual reconciliation (综合所得汇算清缴), not at withholding | secondary summary of the same period's 自行纳税申报 notice; not read in the original | **[U]** |
| Statutory annual leave days depend on **cumulative** working time, counting time at the same or different employers; entitlement requires 12 months of continuous work; new-employer year is prorated by remaining calendar days | 企业职工带薪年休假实施办法 (人社部令第1号) Arts. 3, 4, 5, https://www.gov.cn/zhengce/2022-08/31/content_5711300.htm | **[V]** quoted; the text says nothing about two simultaneous employers (read as: each employer owes leave by the person's cumulative time; whether the two entitlements stack is open, B-D) |
| Dual labour relations are not forbidden by statute; the first employer may dismiss if the second seriously affects the work (劳动合同法第三十九条第四项); part-time (非全日制) work may be with several employers | law-firm articles | **[U]** statute text not read |
| Social insurance is, in practice, enrolled by one unit per person; regional systems are networked and refuse a second enrolment; the second unit has trouble covering work injury | law-firm articles only | **[U]**, and it **conflicts with** the user's ruling that social insurance is computed per relationship if the second entity cannot enrol the person (B-C) |

## 9. Business questions for lead (none decided here)

- **B-A** Does the second-entity relationship have an org unit, position and reporting line in that entity, or none? Design supports both (assignment optional per relationship). Recommendation: none unless HR needs the person in that entity's org tree; then CONCURRENT.
- **B-B** Who confirms the backfilled employer of each existing employee, and by when? Recommendation: HR, before payroll or leave read it.
- **B-C** Does the second entity actually enrol the person in social insurance and housing fund, or is that done only by the first? The user ruled "per relationship"; the practice evidence [U] says one enrolment per person. The model holds an enrolment state per relationship including "not enrolled here"; the rule needs the user (and HR/legal) before payroll design.
- **B-D** Do the two employers' statutory leave entitlements stack, and does company-granted leave follow each employer's own rule? Statute is silent on simultaneity [V]; recommendation: each relationship earns its own quota by the person's cumulative working time and the employer's own company rules, so they stack unless legal advises otherwise.
- **B-E** Does an employer change inside continuous service (transfer between group entities) carry service years, unused leave and probation? Recommendation: carry service years (statute counts different employers), reset probation unless the contract says otherwise; the user decides carry-over of unused leave balance.
- **B-F** Per-relationship asset handover and account access: when one relationship ends and another continues, which handovers apply? Recommendation: handover only on person-level separation; per-relationship handover only if the user names an asset class.

## 10. ADR-0086 (proposed) and what it supersedes

One decision: employment relationship as aggregate, immutable employer per relationship. Partial supersessions: ADR-0079 decision 3 item 1 (employee lifecycle sentence "在职由按生效日排列的员工版本判定"), decision 5 invariant 1 (single in-service judgement, restated over relationships), invariant 4 (social insurance and withholding entity come from the relationship, not the labour contract's employer), decision 4 table row for labour contract events ("含用人单位变更"), and ADR-0084 decision 2 (employer as a dated dimension). Exact scope phrases are in the ADR header. Nothing in ADR-0073 changes.

## 11. Done-when (one checkable line each)

1. Glossary entries for the new names merged as `proposed` (`backend/glossary/proposed/employment-relationship.yaml`).
2. Schema PR matches section 4 table by table; `uk_employment_relationships_employee_employer` rejects a second relationship for the same pair; the per-relationship exclusion rejects overlapping versions of one relationship but accepts overlapping versions of two relationships of one person.
3. Migration test: every existing employee has exactly one relationship, no version or command lacks one, employees without attribution appear in the reject list (aggregate count).
4. `EmploymentVerdict` for a person with two relationships, one ended: EMPLOYED; both ended: SEPARATED; `EmployeeSeparated` fires once, on the last end only.
5. A second-employer HIRE for an in-service person creates a second relationship without touching the first and without firing `EmployeeHired` again.
6. Employer change by SEPARATE + HIRE with predecessor on the same date: person stays in service, no `EmployeeSeparated`.
7. `EmploymentRelationships.on(employee, date)` returns both relationships in a dual-employment test; no scalar `employerOf(employee, date)` exists (grep).
8. The leave read for L3 is exactly `on` and `get` above; RULE-515 is satisfied per relationship (leave card).
9. Existing guards green; identity, approval, timesheet code unchanged in the schema PR (diff touches none).

Out of scope: the labour contract zone, employment-type values (that card adds the type to the relationship versions), payroll's enrolment and withholding tables, any code.

## 12. Evidence register

[V] sections 1, 8 rows 1 and 3, DDL line references; [U] section 8 rows 2, 4, 5, migration counts. Sources: repository files cited inline; the two official URLs in section 8; user words on issue #1295.
