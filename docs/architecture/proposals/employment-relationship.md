# Employment relationship and employer entity (design, #1308)

Status: adopted through ADR-0086 (user 「采纳 ADR-0086」, issue #1308 comment 6640981419). Author: one-design, 2026-11-04 (revised after the opus review of #1315). Combines follow-ups F1 and F2 of [hr-boundary-stability-review.md](hr-boundary-stability-review.md) section 8a. No code, no migration here: table shapes below are the data design to be signed under `docs/data/data-design-gate.md` (appendix A) before the schema PR.

Evidence marks: **[V]** verified by me in the repo or against the cited source on 2026-11-04; **[U]** unverified or only secondary sources; **[B]** a business question, not mine to decide (section 9).

## 0. What is decided here

| # | Question | Ruling (recommendation to the user) | Cost of changing later |
|---|---|---|---|
| 1 | Is there an entity between the person and the versions? | Yes: **EmploymentRelationship**, an entity **inside the Employee aggregate** (root = employee, which stays the lock boundary and the event envelope). Identity = one person employed by one employer entity; many per person, overlap across employers allowed | Highest in the HR model; today only the versions depend on it, so now is cheap |
| 2 | Is the employer a dated dimension? | No: **immutable on the relationship**. A different employer in continuous service is a new relationship naming its predecessor. Only legal succession (ADR-0079 invariant 14) may re-point, by the rule in 4.3 | A dated employer needs a second timeline that every consumer must slice by date |
| 3 | Where does "in service" live? | One place, workforce. Person in service on D = some relationship in service on D. Person-level reads compose by the rules in 6.2 | Consumers (identity 20, approval 9, timesheet 1 files [V]) stay untouched |
| 4 | What attaches per relationship? | Leave entitlement, social-insurance enrolment (the employer is the social-insurance entity), probation and unpaid-leave state, labour contract. **IIT withholding and payroll stay keyed by the paying entity** (ADR-0079 invariant 4 meaning 1), which defaults to the employer; data scope and cost stay per person (meaning 3) | Payroll and leave are unbuilt; keying from day one costs nothing |
| 5 | Migration | One relationship per employee, employer backfilled from the attribution entity and **flagged for HR confirmation** [B] | Reversible: the flag column |

## 1. Facts [V]

- `employees`: one row per natural person for life (ADR-0078). `employee_versions` holds EMPLOYED/SEPARATED, PROBATION/REGULAR, unpaid-leave flag, employment start date; exclusion `ex_employee_versions_overlap` is **per employee** (`V20261014131000…baseline.sql:700`).
- `personnel_commands` kinds: HIRE, REGULARIZE, TRANSFER, SEPARATE, REINSTATE, CORRECTION, UNPAID_LEAVE_START/END, CHANGE_LEGAL_ENTITY, START_CONCURRENT, END_CONCURRENT. Open commands: `uk_personnel_commands_open_per_day (employee_id, effective_date…)` (replaced in `V20261103130000…employee_assignments.sql:127-128`); ADR-0068 item 5 "同日一条" and "离职之后" are per employee.
- `employee_assignments` / `assignment_versions` have PRIMARY and CONCURRENT kinds; exactly one PRIMARY root per employee (`uk_employee_assignments_primary`, `V20261103130000…employee_assignments.sql:16`); the assignment-version exclusion is now **per `assignment_id`** (`:82-84`), so non-overlap of two assignments is not enforced there.
- `employee_legal_entities` is the person's data-scope and cost entity timeline (ADR-0073), non-overlapping per employee. It is **not** an employer: nothing in workforce stores an employer today (ADR-0079 invariant 4 names three legal-entity meanings; only attribution is built).
- Consumers of the in-service verdict outside workforce: `platform.identity` 20 files, `platform.approval` 9, `cost.timesheet` 1 (grep of `EmploymentStatus|EmploymentVerdict|EmployeeProfiles|OrgDirectory|EmployeeLegalEntities`, 2026-11-04). Person-level reads with several consumers: `availabilityOn` (onUnpaidLeave, separationHeld, leaving) read by `SubmitTimesheet:106,113`, `AssigneeResolver:205`, `AdministratorSeparationReview:92,95`, `PermissionAdjustmentTransitions:76,94`, `JdbcAdministratorFloor:65`; `feeEarner`; `EmployeeProfile.orgUnitId` is non-null.
- Published workforce events: EmployeeHired, EmployeeRegularized, EmployeeTransferred, EmployeeSeparated, EmployeeReinstated, EmployeeCorrected, EmployeeLegalEntityChanged. `platform.identity` subscribes to Hired, Separated, Reinstated, Transferred, Corrected.
- `backend/architecture/migration-baseline-open` exists, so the baseline is still edited rather than appended; the expected diff for the schema PR is written either way.
- The user's facts (issue #1295, comments 6638890281 and 6639148601): one person is employed by two legal entities at once, today; the second entity keeps its enterprise qualification; social insurance, IIT withholding and leave entitlement are computed per relationship.

## 2. Options

**Relationship identity.** (A) *employee x employer, rehire reuses it* (recommended). (B) one relationship per employment period. (C) person-level only plus a dated employer attribute (ADR-0084 decision 2 as written).
- A: rehire by the same employer keeps today's behaviour (ADR-0078: same employee, same code; versions continue with a SEPARATED gap), so migration is one row per employee. Changing employer is a new relationship with `predecessor_relationship_id`. Limits: A to B to A (back to the first employer) reuses the first relationship; the chain of predecessors is then not a line, and service continuity is derived from versions, not from the chain (service continuity is B-E, not a model fact). A second simultaneous arrangement with the *same* entity is out of scope (UNIQUE rejects it).
- B: every rehire needs a new relationship and carry-over rules per rehire; no benefit seen.
- C: cannot hold two simultaneous employers on one timeline. Rejected.

**Where the status versions live.** (A) add `employment_relationship_id` to `employee_versions`, exclusion per relationship (recommended: already append-only with full state). (B) new relationship-versions table beside `employee_versions`: two writers of one fact, rejected.

**Employer change.** (A) immutable employer, successor link (recommended). (B) dated employer. A fixes leave quota and social-insurance enrolment to one employer.

**Aggregate boundary.** (A) relationship is an entity inside Employee, one transaction, employee root row lock (recommended: keeps ADR-0068 item 1 "员工根、各版本和命令本身在同一个聚合、同一个事务里" true). (B) relationship its own aggregate: every command would write two aggregates, needs a registered multi-aggregate exception per command. Rejected.

## 3. Answers to (i)-(vi) of review note 7.3

(i) **In-service judgement.** One function in workforce: person in service on D iff some relationship has an EMPLOYED version on D. `EmploymentVerdict`, `EmployeeProfiles`, `OrgDirectory` do not change their signatures; their composition rules are 6.2.
(ii) **Primary relationship for data scope and cost.** None needed: attribution stays person-level (ADR-0079 invariant 4: changing one meaning does not change the others).
(iii) **Per relationship or per person.** User ruled per relationship for social insurance, IIT withholding, leave. Design: leave and social insurance are keyed by `employment_relationship_id`; IIT withholding is keyed by the **paying entity** (invariant 4 meaning 1) because the statute binds the paying unit (section 8). The paying entity defaults to the relationship's employer; a pay agreement may name another, recorded by payroll. Where the two differ, the cumulative IIT run follows the paying entity.
(iv) **Employee id and code.** Per person for life (ADR-0078).
(v) **Employer attaches** as `employer_legal_entity_id` on the relationship, immutable (4.3), FK to `legal_entities`. The labour contract zone later references the relationship id and carries no employer of its own.
(vi) **ADR**: ADR-0086 (section 10).

## 4. Model

### 4.1 Tables

```
employment_relationships                 -- entity in the Employee aggregate; append-only except the one confirmation step (7)
  employment_relationship_id  uuid PK
  employee_id                 uuid NOT NULL FK employees
  employer_legal_entity_id    uuid NOT NULL FK legal_entities
  predecessor_relationship_id uuid NULL                       -- same employee, different employer
  employer_source_kind        text NOT NULL CHECK ('DECLARED','MIGRATION_ATTRIBUTION')
  employer_confirmed_at       timestamptz NULL                -- only for MIGRATION_ATTRIBUTION rows, set once by HR (7)
  created_at                  timestamptz NOT NULL
  UNIQUE (employee_id, employer_legal_entity_id)
  UNIQUE (employment_relationship_id, employee_id)            -- composite FK target
  FK (predecessor_relationship_id, employee_id) -> (employment_relationship_id, employee_id)
  CHECK predecessor_relationship_id <> employment_relationship_id
  CHECK employer_source_kind = 'MIGRATION_ATTRIBUTION' OR employer_confirmed_at IS NULL
```

`employee_versions` (renamed in the schema PR to `employment_relationship_versions`; glossary name in the proposed file): add `employment_relationship_id NOT NULL`, composite FK `(employment_relationship_id, employee_id)` to the relationship. Per-relationship objects replace the per-employee ones: exclusion `EXCLUDE (employment_relationship_id WITH =, daterange(...) WITH &&) WHERE superseded_at IS NULL`; business key `UNIQUE (employment_relationship_id, effective_from_date, revision_number)` (revision numbers are counted **per relationship**, so two relationships may both have revision 0 on one day); `corrected_from_version_id` becomes a composite FK `(corrected_from_version_id, employment_relationship_id)` so a correction stays inside its relationship (as #711 did for assignments). `is_fee_earner` stays a column of this table; the person-level value is composed (6.2).

`personnel_commands`: add `employment_relationship_id` **nullable with a per-kind CHECK** (`ck_personnel_commands_employment_relationship`): NOT NULL for HIRE, REGULARIZE, TRANSFER, SEPARATE, REINSTATE, UNPAID_LEAVE_START/END, CORRECTION (names the relationship corrected), START_CONCURRENT, END_CONCURRENT; NULL for CHANGE_LEGAL_ENTITY (person-level attribution). Composite FK `(employment_relationship_id, employee_id)`. **No `target_employer_legal_entity_id` column**: HIRE names the employer in the request, the relationship row is created at entry and the employer is read from it (one place, no duplicate to keep consistent). For TRANSFER the id is the relationship the resulting assignment version belongs to (so a TRANSFER that moves the PRIMARY assignment to another relationship names the new one). `ck_personnel_commands_hire_columns` and `ck_personnel_commands_reinstate_columns` change: `target_org_unit_id` and `target_position_id` must be **both set or both NULL** (`... IS NULL) = (... IS NULL)`), `is_target_fee_earner` and `target_employment_phase_kind` stay NOT NULL. Both set = the entry also creates or continues the person's PRIMARY assignment; both NULL = an additional-employer HIRE/REINSTATE without an assignment (B-A), allowed only when the person already has a PRIMARY assignment in service on that day (application check under the root lock; invariant 6).

`assignment_versions` (not the assignment root): add `employment_relationship_id NOT NULL`, composite FK `(employment_relationship_id, employee_id)`. This follows the ADR-0086 consequence ("each version table gains the id"). Reason it is not on the root: the root is append-only and the PRIMARY root exists once per person for life (`uk_employee_assignments_primary`, ADR-0079 invariant 2), so a relationship id on the root could never follow the primary assignment to another relationship. On the version it can: moving the PRIMARY assignment (employer change in continuous service, or the primary relationship ends while another continues) closes the old PRIMARY version and appends a new version of the **same root** carrying the other relationship. The per-assignment exclusion (`ex_assignment_versions_overlap`, per `assignment_id`) is unchanged, so "one PRIMARY in service" still holds. A CONCURRENT root stays in one relationship for life (application check; ending it and starting a new one is the way to change). A command's relationship equals the relationship of the assignment version it produces, except for a PRIMARY TRANSFER that moves the primary (invariant 8); a date-dependent attribute of a version row, so an application check under the root lock.

Assignment-version revision numbers stay per employee (`uk_assignment_versions_revision` is unchanged; a primary moved to another relationship appends one row). Unchanged: `employee_assignments` (the root), `employee_legal_entities`, and the `employees` snapshot table (its derived columns `employment_status`, `is_fee_earner`, `separated_at` are composed by the rules in 6.2).

### 4.2 Invariants

1. One person x one employer = one relationship; two relationships of one person may overlap in time; versions of one relationship never overlap.
2. The employer of a relationship never changes except by succession (4.3).
3. A person is in service on D iff some relationship is in service on D (ADR-0079 invariant 1, restated).
4. Of the three legal-entity meanings (ADR-0079 invariant 4): meaning 1, IIT withholding and payroll run, by the **paying** entity (default: the relationship's employer; recorded by payroll if different); meaning 2, social insurance and housing fund, by the **relationship's employer** (this replaces "劳动合同的用人单位"); meaning 3, data scope and cost, by the attribution entity (unchanged). Changing one does not change the others.
5. No relationship, no employer: HIRE always names the employer; there is no default employer.
6. A person in service on D has exactly one PRIMARY assignment in service on D (as today); a command that would leave an in-service person without one is refused (6.3).
7. SEPARATE takes effect on one relationship: the same transaction closes the open assignment versions **of that relationship only** (ADR-0068 part 3 item 3, narrowed); the person's other relationships and their assignments are untouched.
8. A command's relationship equals the relationship of the assignment version **it produces**. Only a TRANSFER of the PRIMARY assignment may name a relationship different from the one of the version it acts on (moving the primary, 6.3), and then the target relationship must be in service on the effective day. A CONCURRENT root stays in one relationship.
9. A void relationship (all its versions void, 5) is re-used by a later HIRE only if that HIRE has the same predecessor (both none, or the same); otherwise the HIRE is refused with a message naming the void relationship, because predecessor and source are immutable. Accepted limit: rare, and a refusal is safer than a wrong predecessor.

### 4.3 Employer succession (ADR-0079 invariant 14)

Merger or split of an employer: the contract continues with the successor and is not a cross-entity move. Rule: the only way an employer changes inside one relationship is a **succession record** (relationship id, from entity, to entity, effective date, legal basis reference), append-only, written by a SUCCEED_EMPLOYER command. `employerOn(relationshipId, date)` reads it; a relationship without records has its base employer. Consumers that need the employer on a date use that read; leave quota and enrolment keep their relationship key, so nothing re-keys. **Not built now**: no case is known; the table and command wait for the first real merger or split. Until then re-pointing is refused. Whether to build it in the first schema PR is B-G.

## 5. Command binding

Every command names its relationship explicitly. Nothing infers a relationship from an org unit, because an org unit's entity is the *attribution* entity (meaning 3), not an employer.

| Command | Binding |
|---|---|
| HIRE | Names the employer entity in the request (stored once, on the relationship row, not on the command). New person: employee and relationship are created **at command entry** (versions are written at entry, including future dates, ADR-0068 item 8, so the relationship row must exist then). Existing person, an employer with no relationship yet: second relationship ("additional employer", not a rehire). Existing person, same employer: this is REINSTATE. HIRE with `predecessor_relationship_id`: employer change in continuous service (below) |
| REGULARIZE, UNPAID_LEAVE_START/END | One relationship |
| TRANSFER | Org-unit/position change on one assignment of one relationship. It **never changes the employer**. If the target unit's attribution entity differs from the person's, ADR-0073 applies unchanged (attribution change, countersign, `AWAITING_ATTRIBUTION_COUNTERSIGN`). If the target unit's attribution entity differs from the *relationship's employer*, allowed or refused is B-H (recommendation: allowed, since attribution and employer are independent meanings) |
| SEPARATE | One relationship; HELD (separation hold for administrators) applies only to SEPARATE. Refused if the relationship carries the person's PRIMARY assignment and another relationship stays in service, unless the same entry moves the primary to the other relationship (6.3) |
| REINSTATE | One relationship that has ended |
| CORRECTION | Names the relationship whose versions it corrects |
| START_CONCURRENT, END_CONCURRENT | Name the relationship that employs the person in that role; the unit's attribution entity need not equal the employer (B-H) |
| CHANGE_LEGAL_ENTITY | Person level (attribution), relationship id NULL |
| Employer change in continuous service | One entry of two commands on the same effective date: SEPARATE on the old relationship, HIRE (with `predecessor_relationship_id`) on the new employer. The person stays in service; neither `EmployeeSeparated` nor `EmployeeHired` fires (6.1) |
| Withdraw a pending HIRE | The command is withdrawn as today (ADR-0068 item 6); its versions are voided by the existing void-and-append mechanism. A relationship row left with no live version is "void": invisible to reads, and re-used by a later HIRE for the same pair (UNIQUE) under invariant 9. Rows are never deleted |

**Same-day and after-separation rules** (ADR-0068 item 5) become **per relationship** for relationship-bound kinds: at most one open command per (relationship, effective date); after an unreversed SEPARATE on a relationship only REINSTATE may be entered later on *that relationship*. Person-level kinds (CHANGE_LEGAL_ENTITY) keep the per-employee rule with a NULL relationship id. A person-level command and a relationship-level command on the same day stay **refused** as today (application check under the root lock, because no single index spans both); the check excludes concurrent-post kinds the way today's per-employee index does (START_CONCURRENT, commands on a CONCURRENT assignment, and CHANGE_LEGAL_ENTITY is the person-level side), so it is no stricter than today. START_CONCURRENT stays outside the per-relationship rule and commands that act on an existing assignment (primary or concurrent) get a per-assignment rule (`uk_personnel_commands_open_per_assignment_day`, predicate widened from concurrent-only to every `target_assignment_id IS NOT NULL`), which also keeps "one open command per primary assignment per day" now that the per-employee index is gone, so today's allowance of a concurrent-post command beside a primary-assignment command on one day is kept. This is what lets SEPARATE (old) and HIRE (new) share a date. Concurrency is unchanged: all commands lock the employee root row.

## 6. Events and reads

### 6.1 Events: firing rules

Event envelope stays the employee (aggregate) with `businessDate`; every person-level event except `EmployeeLegalEntityChanged` (attribution, person level) gains `employmentRelationshipId`, NOT NULL, additively (the relationship that triggered it; for `EmployeeSeparated` the last relationship to end). Person-level events:

| Event | Fires when |
|---|---|
| EmployeeHired | the person goes from not in service to in service by a HIRE (first or additional or predecessor-less) |
| EmployeeSeparated | the person goes from in service to not in service (last in-service relationship ends); not on employer change (same date successor) |
| EmployeeReinstated | the person goes from not in service to in service by a REINSTATE |
| EmployeeRegularized | each relationship's regularization (payload names the relationship) |
| EmployeeTransferred | each TRANSFER (payload names the relationship and assignment) |
| EmployeeCorrected | each CORRECTION (payload names the relationship) |
| EmployeeUnpaidLeaveStarted | each UNPAID_LEAVE_START takes effect, per relationship (built by PR #1287; payload names the relationship once ADR-0086 is built); identity's sensitive-role review reads it. **Deliberately conservative**: the review runs even if the person keeps working in another relationship, which differs from the all-relationships rule for `onUnpaidLeave` in 6.2 (that rule is about availability for time and approval, this event is about account risk) |
| EmployeeLegalEntityChanged | unchanged (attribution) |

Relationship-level events (new; glossary names in the proposed file): **EmploymentRelationshipStarted** (HIRE or REINSTATE takes effect, or a second relationship begins), **EmploymentRelationshipEnded** (SEPARATE takes effect), **EmploymentRelationshipPhaseChanged** (regularization; later also type changes), **EmploymentRelationshipUnpaidLeaveChanged** (start and end), **EmploymentRelationshipCorrected**. Leave (RULE-515 triggers: start, regularization, unpaid-leave change, end, correction) and payroll subscribe to these, not to the person-level ones; identity keeps its existing subscriptions unchanged (the person-level events, including EmployeeUnpaidLeaveStarted).

### 6.2 Person-level reads over several relationships

New api `EmploymentRelationships` (workforce): `on(employee, date)` returns the relationships in service that day (id, employer, phase kind, unpaid-leave flag); `get(id)`; `employerOn(id, date)`. No scalar `employerOf(employee, date)`.

Composition for existing person-level reads (these are the recommended rules; the ones that change a business behaviour are B-I):

| Read | Rule over relationships in service on D |
|---|---|
| `EmploymentVerdict` / in service | any relationship in service |
| `availabilityOn.onUnpaidLeave` | true only if **all** in-service relationships are on unpaid leave |
| `availabilityOn.separationHeld` | true if **any** in-service relationship has a held SEPARATE (conservative: the administrator floor and approver choices care about the person) |
| `availabilityOn.leaving` | true only if **all** in-service relationships are leaving, a relationship being leaving by today's predicate, evaluated per relationship (`JdbcPersonnelCommandRepository.isLeaving`): a SEPARATE that carries a separation hold (HELD, or RELEASED while the command is still PENDING), or a PENDING UNPAID_LEAVE_START. A plain entered SEPARATE without a hold is not leaving today and stays so |
| `feeEarner` | the `is_fee_earner` value of the version of the relationship that holds the PRIMARY assignment on D (the flag lives on the relationship version, written by HIRE, REINSTATE, TRANSFER; there is no "position decides" rule) |
| `employees` snapshot columns | `employment_status` = EMPLOYED iff some relationship is in service on the snapshot day; `is_fee_earner` as in the row above; `separated_at` = NULL while any relationship is in service, else the latest end date over the person's relationships |
| `isHeld`, `heldEmployees` (separation holds, pending-release reviews) | unchanged in meaning: HELD still applies to every SEPARATE command (a hold on a non-last relationship counts, B-I c); person-level answer = **any** in-service relationship held, per `separationHeld` above; the queries read `separation_holds` and `personnel_commands` and now also return the relationship id |
| `isLeaving`, `excludedEmployees` (the leaving set; same predicate as `isLeaving`, callers name it `leaving`: administrator floor, unpaid-leave floor, assignee resolution) | person-level answer = **all** in-service relationships are leaving (ADR-0086 decision 4), exactly the `availabilityOn.leaving` rule; a person leaving on one relationship only is not in the set |
| Command list visibility (data scope, ADR-0026 decision 1 item 9) | **unchanged**: HIRE and REINSTATE are visible by `target_org_unit_id`; every other kind by the person's assignment intervals (primary and concurrent) on the day before the effective date, as `JdbcCommandReadRepository` does today. An additional-employer HIRE or REINSTATE without a target org unit stays visible to the all-scope user only, as today; widening it to the primary assignment's unit would be a new data-scope rule and is not part of this card |
| `employedIn`, `postsIn`, `hasEmployedFrom` (`JdbcOrgStructureCommandRepository`) | join assignment versions to relationship versions by `employment_relationship_id` (same employee guaranteed by the composite FK) |
| Anniversary query (employment start month and day) | one row per relationship in service, from that relationship's version; reminders are per relationship |
| `EmployeeProfile.orgUnitId` | the PRIMARY assignment's unit; non-null stays true because of invariant 6 |
| Timesheet submission, approver resolution | unchanged person-level; whether a person on unpaid leave in one entity but working in another may submit time, and whose approver applies, are B-I |

Leave (slice L3, RULE-515): entitlement and quota keyed by `(employmentRelationshipId, leaveYear)`; a request names its relationship (a person with one relationship in service gets it defaulted; with two, the person chooses, never guessed); days on which the relationship is not in service are refused; RULE-510 uses `StandardHours.minutesOn(employerLegalEntityId, date)` (#1310) of the relationship's employer.

### 6.3 Primary assignment across relationships

Invariant 6 is checked at command entry under the employee root lock. SEPARATE of the relationship holding the PRIMARY assignment while another relationship stays in service is refused unless the entry also designates a new PRIMARY in the other relationship (a TRANSFER-style move); a person's only in-service relationship may end without that (the person leaves service). HIRE of a person's first relationship needs a PRIMARY assignment as today; a second-employer HIRE does not (B-A).

## 7. Migration of today's rows

One relationship per employee. `employer_legal_entity_id` = the person's effective attribution entity at the **first version date** (the rule in the adopted ADR-0086 migration plan; the earlier draft's "current attribution" is withdrawn so that design and ADR agree). "Effective attribution" is what `LegalEntityResolution.resolve` computes today (`domain/LegalEntityResolution`): the employee's own periods first, otherwise the org unit's periods inherited up the org tree (`parentUnit` in `JdbcLegalEntityResolutionSources`); `EffectiveAttributionPeriods` only builds the periods of one subject, with corrections applied. A test compares the SQL backfill with `LegalEntityResolution.resolve` on a sample. `employer_source_kind = MIGRATION_ATTRIBUTION`, `employer_confirmed_at` NULL, awaiting HR confirmation (B-B). Persons already employed by two entities today have one relationship after migration; the second is entered afterwards as a back-dated HIRE (ADR-0068 item 8) once HR lists them. Employees with no attribution at the first version date are not backfilled: they appear on a reject list for HR, no guessing. **Who gets a relationship in the structural migration**: every employee who has a row in any of `employee_versions`, `assignment_versions` or a relationship-bound `personnel_commands` (a person with assignment versions but no employee version exists today, see `JdbcInitialVersionMigrationRepository`); the first version date is the earliest effective date among those rows. **`MigrateInitialVersions`** (an operations command that runs in production) only concerns employees with no row in any of the three: it reuses an existing relationship if there is one, otherwise it creates one from HR's confirmed list (employer taken from the list, `DECLARED`; the list is its input, not the attribution), and an employee not on the list is refused and reported, never guessed. It never writes a version without a relationship.

Two separate things:
1. **Dev databases (baseline open).** One appended migration in `organization_workforce` (the open baseline allows editing the baseline, but the append-only tables need the drop-trigger, backfill, recreate-trigger sequence of `V20261103130000…employee_assignments.sql` section 2, and an appended step keeps that sequence testable). It adds the new table and columns nullable, backfills by join on employee (commands of every kind except CHANGE_LEGAL_ENTITY, which keeps a NULL relationship id), sets NOT NULL only if no NULL remains (on the version tables only), and **fails loudly with an aggregate count** otherwise. Dev databases are rebuilt per `docs/ops/runbooks/dev-db-rebuild.md`; no dev data needs preserving, but the backfill still runs, on whatever rows a freshly migrated database holds at that moment (integration-test seed rows, and any dev database not yet rebuilt); the migration has no effect on `legacy_numbers`.
2. **Production history (ADR-0036).** Source-to-target mapping and the M6 reject register for rows without attribution are part of the migration mapping design, written when production data is brought in; this card does not decide them.

Confirmation by HR is the only change a relationship row ever sees: it sets `employer_confirmed_at` once and changes nothing else, the employer included (4.1, trigger rule in appendix A). A wrong backfilled employer is **not** corrected in place (ADR-0086 decision 2: only legal succession re-points). In dev databases the baseline is rebuilt; for production history the employer per employee comes from the confirmed list before load (ADR-0036 mapping, written when production data is brought in), so no post-load correction path is needed; a wrong value found after confirmation is a business change of employer and is entered as a new relationship with a predecessor. Counts are aggregates only [U: not run].

## 8. Legal check

| Claim | Source | Mark |
|---|---|---|
| IIT on wages is withheld by each paying unit separately, cumulative method over the taxpayer's months **in that unit** | 个人所得税扣缴申报管理办法（试行）(国家税务总局公告2018年第61号) Art. 6, https://www.gov.cn/zhengce/zhengceku/2019-10/28/content_5445955.htm | **[V]** quoted from the official text. Consequence used in this design: withholding is keyed by the paying unit, not by the employer |
| Income from two units is combined at annual reconciliation | secondary summary | **[U]** |
| Statutory annual leave depends on **cumulative** working time at the same or different employers; entitlement requires 12 months continuous work; the new employer's first year is prorated | 企业职工带薪年休假实施办法 (人社部令第1号) Arts. 3, 4, 5, https://www.gov.cn/zhengce/2022-08/31/content_5711300.htm | **[V]** quoted; silent on two simultaneous employers (B-D) |
| Dual labour relations are not forbidden; the first employer may dismiss if the second seriously affects the work (劳动合同法第三十九条第四项); part-time work may be with several employers | law-firm articles | **[U]** statute text not read |
| Social insurance is in practice enrolled by one unit per person; the second unit has trouble covering work injury | law-firm articles | **[U]**, and it **conflicts with** the user's ruling that social insurance is per relationship (B-C) |

## 9. Business questions: answered by the user 2026-11-04

The user answered all of B-A to B-I and the withholding point with 「按建议」 (issue #1308 comment 6640858641): each recommendation below is the ruling. Still open: B-C's rule with HR/legal before payroll design, and the carry-over of unused leave in B-E (the user's, T-6 in the leave spec).

- **B-A** [answered] Does the second-entity relationship have an org unit, position and reporting line there, or none? Both supported. Recommendation: none unless HR needs the person in that entity's org tree; then CONCURRENT.
- **B-B** [answered] Who confirms the backfilled employer of each existing employee, and when? Recommendation: HR, before payroll or leave read it. Also who lists persons already employed twice.
- **B-C** [answered] Does the second entity actually enrol the person in social insurance and housing fund? The model holds an enrolment state per relationship including "not enrolled here"; the rule needs the user and HR/legal before payroll design.
- **B-D** [answered] Do the two employers' statutory leave entitlements stack, and does company-granted leave follow each employer's own rule? Recommendation: each relationship earns its own quota by cumulative working time and its employer's rules, so they stack unless legal advises otherwise.
- **B-E** [answered] Employer change in continuous service: does it carry service years and unused leave balance? Probation: ADR-0079 invariant 13 already says a cross-entity move has no new probation (lead default); a HIRE with a predecessor therefore defaults to no probation, the user may override. Recommendation for service years: carry (statute counts different employers); unused-leave carry-over is the user's.
- **B-F** [answered] Handover and account access when one relationship ends and another continues. Recommendation: only on person-level separation, unless the user names an asset class.
- **B-G** [answered] Build the employer-succession record (4.3) in the first schema PR or when the first merger arises? Recommendation: later; refuse until then.
- **B-H** [answered] May a TRANSFER or concurrent assignment place a person in an org unit whose attribution entity is not the relationship's employer? Recommendation: allowed (independent meanings); HR chooses employer change explicitly, the system never infers it.
- **B-I** [answered] Person-level behaviours with two relationships: (a) unpaid leave in one entity, working in the other: may the person submit time? (b) whose approver applies to hours worked for the second entity? (c) separation hold: one held separation blocks which decisions? Recommendation: the rules in 6.2 (conservative); timesheet and approver follow the person's PRIMARY assignment until the user names another.

## 10. ADR-0086 (proposed) and what it supersedes

One decision: employment relationship as an entity of the Employee aggregate with an immutable employer. Partial supersessions, listed in the ADR header (exact scope phrases there): ADR-0079 decision 3 item 1 (in-service sentence), decision 4 table row 1 (subscribers of person events) and the labour-contract row (employer change), decision 5 invariant 1, invariant 4 (social-insurance clause only; withholding stays with the paying entity; invariant 14 is not superseded: the succession rule 4.3 applies it), ADR-0068 item 5 (same-day and after-separation per relationship) and the item-1 sentence on commands and versions (now per relationship inside the aggregate), ADR-0084 decision 2 (whole item rewritten: employer is an attribute of the relationship). ADR-0073 is **not** superseded: TRANSFER never changes the employer, so decision 3 item 4 (cross-entity transfer countersign) keeps its meaning, which the retrieval record in the ADR confirms by search.

## 11. Done-when (one checkable line each)

1. Glossary entries for the new names merged as `proposed` (`backend/glossary/proposed/employment-relationship.yaml`).
2. Schema PR matches appendix A table by table; the UNIQUE rejects a second relationship for the same pair; the per-relationship exclusion rejects overlapping versions of one relationship but accepts overlapping versions of two relationships of one person; the per-kind CHECK rejects a NULL relationship id for HIRE and accepts it for CHANGE_LEGAL_ENTITY.
3. Migration test: every existing employee with a row in the version, assignment-version or command tables has exactly one relationship, no version, no assignment version and no relationship-bound command (every kind except CHANGE_LEGAL_ENTITY) lacks one, `MigrateInitialVersions` reuses an existing relationship, creates a `DECLARED` one from the list for employees without any row, and refuses employees not on the list, employees without attribution appear in the reject list (aggregate count).
4. Person with two relationships, one ended: in service; both ended: SEPARATED; `EmployeeSeparated` fires once, on the last end only.
5. Second-employer HIRE for an in-service person creates a second relationship without touching the first and without firing `EmployeeHired`.
6. SEPARATE (old) + HIRE with predecessor on one date: person stays in service, neither `EmployeeSeparated` nor `EmployeeHired`, the same-day rule accepts both because it is per relationship.
7. `EmploymentRelationships.on` returns both relationships in a dual-employment test; no scalar `employerOf(employee, date)` exists (grep).
8. SEPARATE of the relationship holding the PRIMARY assignment while another stays in service is refused.
9. The composition rules of 6.2 each have a test (unpaid leave all/any, separation hold, leaving), including: a person leaving on one relationship only is not in `excludedEmployees` and not `leaving`; a plain SEPARATE without a hold is not leaving.
10. The leave read for L3 is exactly `on`, `get`, `employerOn`; RULE-515 triggers are covered by the five relationship events.
11. Existing guards green; identity, approval, timesheet code unchanged in the schema PR.
12a. A HIRE or REINSTATE that continues the primary assignment (both target columns set) and a command on the primary assignment (TRANSFER) on the same effective day: the second is refused by the application check under the root lock.
12. Moving the PRIMARY assignment to another relationship appends a new version of the same root carrying the other relationship id; the old version closes; invariant 6 holds on every day (test with SEPARATE old + HIRE-with-predecessor on one date).
13. A command whose relationship differs from the relationship of the assignment version it produces is refused, except a PRIMARY TRANSFER that moves the primary to a relationship in service that day; SEPARATE closes only the separated relationship's assignment versions.
14. Revision numbers are per relationship: two relationships of one person each hold revision 0 on the same day.
15. Confirmation: a MIGRATION_ATTRIBUTION row accepts exactly one update, `employer_confirmed_at` from NULL to a value with every other column unchanged; a second update, an update of the employer, or any update to a DECLARED row, is refused by the trigger.

Out of scope: the labour contract zone, employment-type values (that card adds the type to the relationship versions), payroll's enrolment and withholding tables, the succession table (B-G), any code.

## 12. Evidence register

[V] section 1, section 8 rows 1 and 3, DDL line references; [U] section 8 rows 2, 4, 5, migration counts. Sources: repository files cited inline; the two official URLs in section 8; user words on issue #1295.

## Appendix A. 数据设计

本附录是数据设计门禁（`docs/data/data-design-gate.md` 附录 A）的材料；4.1 节是形状，本附录是逐表、逐列、逐约束、逐索引的预期 diff 与各项判据。基线按卡版本 bfc8e6f6a 之后的修订（评审记录写入 docs/data/reviews 下以卡号命名的文件）。

- 涉数据：是。新表 `employment_relationships`；改表三张：`employee_versions`（改名为 `employment_relationship_versions`）、`personnel_commands`、`assignment_versions`。`employee_assignments`（任职根）与 `employees` 不改结构。
- 阶段（T2）：表属 `organization.workforce`，落在 W0（`docs/architecture/ssot/transitions/transitions.yaml` 的 W0，delivers 含 organization.workforce）；请假、薪资的读口径随各自的 W2、W4 建造（ADR-0086 头部阶段）。
- 表 → 出处（B1、B2）：| `employment_relationships` | 信息图 OBJ-127 雇佣关系（`docs/architecture/ssot/objects/enterprise-info-map.md`，次级于 OBJ-56 人员，聚合内实体） | 聚合 Employee（注册表 `aggregate: Employee`，与兄弟表一致；同 schema `organization_workforce`） | role `AGGREGATE_CHILD` |
- 状态、关系、基数（B3、B3a、B4）：
  - 本表无状态列（`employer_source_kind` 二值 CHECK，来由见 4.1）；状态仍在版本表，取值集不变；命令种类 CHECK 不变。
  - 员工 1—N 关系（`employee_id` NOT NULL 外键）；关系 1—N 版本（复合外键 `(employment_relationship_id, employee_id)`）；关系 1—N 任职版本（同形复合外键，关系 ID 在 `assignment_versions` 上，不在任职根上，理由见 4.1）；关系 1—N 命令（同形复合外键，命令的关系 ID 可空，见下）；关系 0..1 前任（复合外键 `(predecessor_relationship_id, employee_id)`，列注释「为空表示：非换用人单位产生」）；用人单位 N—1 `legal_entities`（同 schema 外键）。
  - 版本更正限于同一关系：`corrected_from_version_id` 改复合外键 `(corrected_from_version_id, employment_relationship_id)`（参照 #711 对任职版本的做法）。
- 不变量 → 约束（B5、O4）：

| 不变量 | 承担者 | 无法落约束时的理由 |
|---|---|---|
| 4.2-1 一人一用人单位一段 | `uk_employment_relationships_employee_employer UNIQUE (employee_id, employer_legal_entity_id)` | — |
| 4.2-1 同一关系版本不重叠 | `ex_employment_relationship_versions_overlap EXCLUDE (employment_relationship_id =, daterange &&) WHERE superseded_at IS NULL` | — |
| 修订号按关系计 | `uk_employment_relationship_versions_revision UNIQUE (employment_relationship_id, effective_from_date, revision_number)` | — |
| 更正留在本关系 | 复合外键 `(corrected_from_version_id, employment_relationship_id)` → 版本表 `UNIQUE (employment_relationship_version_id, employment_relationship_id)` | — |
| 前任属同一员工且不是自己 | 复合外键 `(predecessor_relationship_id, employee_id)` + `ck_employment_relationships_predecessor_not_self` | — |
| 关系属本员工（版本、任职版本、命令） | 三处复合外键 `(employment_relationship_id, employee_id)` → `employment_relationships UNIQUE (employment_relationship_id, employee_id)` | — |
| 按种类关系 ID 是否可空 | `ck_personnel_commands_employment_relationship`（见 4.1） | — |
| 4.2-2 用人单位不变 | 触发器 `tg_employment_relationships_append_only`：UPDATE 只放行一种——`employer_source_kind = 'MIGRATION_ATTRIBUTION'` 且旧值 `employer_confirmed_at IS NULL` 的行，把 `employer_confirmed_at` 由空变非空一次，其余列（含用人单位）必须逐列不变；之后与其余行一样冻结。用人单位从不原地改写（ADR-0086 决定 2）；DELETE 只放行表属主（ADR-0068 第 7 项）；TRUNCATE 拒绝 | 无法用静态约束 |
| 确认列只对回填行有值 | `ck_employment_relationships_confirmed_only_migration` | — |
| 4.2-3 在职判断 | 应用（读合成，6.2） | 跨行时间合成，非约束可表 |
| 4.2-6 在职者有且仅有一个主任职 | `uk_employee_assignments_primary`（每人一根）+ `ex_assignment_versions_overlap`（按任职根不重叠，不改）+ 命令入口在锁住员工根后校验在职日有效 | 日期覆盖无法落约束 |
| 4.2-7 离职只关本关系的任职版本 | 应用（同事务，沿 ADR-0068 第 3 项第 3 条，改为按关系） | 跨表同事务动作，非约束可表 |
| 4.2-8 命令的关系 = 命令所产生任职版本的关系（只有移动主任职的 TRANSFER 可不同于所作用版本的关系，且目标关系当日在职）；兼职根终身在一个关系 | 应用，命令入口在员工根锁内校验 | 关系 ID 是版本行的、随日期变化的属性，外键只能指向根 |
| 4.2-9 空关系复用条件 | 应用（命令入口校验前任相同） | 条件含命令里的前任，非静态约束可表 |
| 同日一条（按关系） | `uk_personnel_commands_open_per_relationship_day (employment_relationship_id, effective_date) WHERE status IN ('PENDING','HELD','AWAITING_ATTRIBUTION_COUNTERSIGN') AND employment_relationship_id IS NOT NULL AND command_kind <> 'START_CONCURRENT' AND target_assignment_kind IS DISTINCT FROM 'CONCURRENT'`（谓词沿旧索引，只换键） | — |
| 同日一条（人员层） | `uk_personnel_commands_open_person_day (employee_id, effective_date) WHERE status IN (...) AND employment_relationship_id IS NULL` | — |
| 人员层与关系层命令同日仍拒绝（与现行为一致） | 应用（员工根锁内校验） | 两条索引键不同，无法由单个索引表达 |
| 同一任职（主任职与兼职）同日只有一条未生效命令 | `uk_personnel_commands_open_per_assignment_day (target_assignment_id, effective_date) WHERE status IN (...) AND target_assignment_id IS NOT NULL`（谓词由「仅兼职」放宽到全部任职；原按人索引拆开后，移动主任职的两条同日 TRANSFER 写不同关系 ID 会漏过按关系索引，实测复现于第二轮审查）。继续主任职的 HIRE/REINSTATE 的 `target_assignment_id` 必空（`ck_personnel_commands_target_assignment`），按关系索引也覆盖不到它与「移动主任职的 TRANSFER」同日的情形（两条在不同关系上，实测复现于第三轮审查） | — |
| 继续主任职的 HIRE/REINSTATE 与主任职上的命令同日仍拒绝 | 应用（员工根锁内校验） | 两条命令键不同（关系不同、任职列为空），无法由单个索引表达；第 11 节加拒绝用例 |
| 合并一员一条 | `uk_personnel_commands_merge_assignment` 不改；合并生成的逐人 TRANSFER 带关系 ID，取自所作用任职版本 | — |
| 带前任的 HIRE 须与前任关系的 SEPARATE 同日（连续任职） | 应用，员工根锁内校验（前任属同一员工由复合外键保证；前任的用人单位必然不同，由 `UNIQUE (employee_id, employer_legal_entity_id)` 与前任不是自己的 CHECK 隐含） | 跨命令、跨行的日期关系，非约束可表 |
| 离职之后只能复职（按关系） | 应用（沿 ADR-0068 第 5 项，改为按关系） | 规则跨行，沿旧做法 |
| HIRE/REINSTATE 带不带任职 | `ck_personnel_commands_hire_columns`、`ck_personnel_commands_reinstate_columns` 新写法（见 4.1）；不带任职只允许人员已有在职主任职（应用校验） | 「人员已有在职主任职」跨行日期覆盖 |

- 用例 → 聚合 → 表（O1）：全部人事命令写 Employee 聚合：员工根（锁）、`employment_relationships`（HIRE 入口建；运维命令 `MigrateInitialVersions` 只处理三处都无行的员工，沿用已有关系或按人事确认清单建 `DECLARED` 关系，再写版本；HR 确认回填关系是唯一的更新）、版本表、命令表、任职表；一个事务，无多聚合例外。
- 业务键、类别、时态（O3、O6）：

| 表 | 业务键 | 类别 | 时态 |
|---|---|---|---|
| `employment_relationships` | `(employee_id, employer_legal_entity_id)` | 维度型：根表只存不变的身份列（D17 细则 5），生效区间在版本表；除确认回填外不改写 | 无区间（身份不随日期变）；继任另表，4.3，暂不建 |
| `employment_relationship_versions`（原 `employee_versions`） | `(employment_relationship_id, effective_from_date, revision_number)` | `DIMENSION_VERSIONED`（不变），生效依据 `DECLARED` | 区间 + 修订；粒度：每个雇佣关系的每个生效区间每个修订一行；列覆盖不变（既有列全部保留，新增关系 ID） |
| `assignment_versions` | 不变（`(assignment_id, ...)`） | 不变 | 不变；新增关系 ID 列 |
| `personnel_commands` | `personnel_command_id` | 不变 | 不变 |

- 查询路径与预算（O10、O10a）：预算类别按 guiding-principles §7a R-P1（详情 200 ms、列表 300 ms、命令 500 ms）。多段关系下语义变化的既有路径一并列出，写法按关系连接，或先按关系读再按人合成（6.2）。

| 路径 | 过滤列 | 排序列 | 预期行数 | 分页 | 索引 | 预算 |
|---|---|---|---|---|---|---|
| `EmploymentRelationships.on(employee, date)` | `employee_id`、日期 ∈ 区间、`superseded_at IS NULL` | 无 | 每人 ≤ 数条 | 无需 | `ix_employment_relationship_versions_employee (employee_id, effective_from_date) WHERE superseded_at IS NULL` | 详情 |
| `get(id)`、`employerOn(id, date)`（继任表未建，等于 `get`） | 主键 | — | 1 | — | 主键 | 详情 |
| `employmentOn`、`isOnLeave`（`JdbcEmployeeVersionQueries`）：由 `.optional()` 改为读多行后按 6.2 合成；`availabilityOn` 的在职与停薪留职部分来自这里，待放行与将离职部分来自下一行 | 同 `on` | 无 | ≤ 数条 | 无需 | 同上 | 详情 |
| 在职名册与 `SELECT_STAFF_ON_DAY`（`OrgSnapshotRows`、`JdbcEmployeeRosterQueries`）：现按 `employee_id` 左连接版本，两段在职会连出两行 | 日期、`employment_status = 'EMPLOYED'` | `employee_id`（键集） | 全员数量级，千级 | 键集分页，页大小受限 | 新索引 `ix_employment_relationship_versions_employee`（排他约束的 gist 改为按关系后，按员工与日期的访问靠它），连接后按 `employee_id` 去重（`EXISTS`，不用 `DISTINCT` 外连）；人员层字段 `employment_status`、`is_fee_earner`、`hire_date` 取日上含主任职的那段关系，无主任职则取起始日最早的在职关系，`posts_day` 同理 | 列表 |
| `OPEN_HOLDERS`（`JdbcOrgStructureCommandRepository`）：任职版本与员工版本现按 `employee_id` 连接 | 组织单元、日期 | — | 每单元 ≤ 百级 | 无需 | 任职版本既有索引 + 连接条件改为 `employment_relationship_id`（两表同值，复合外键保证同员工） | 列表 |
| 满周年查询（`ix_employee_versions_employment_start_month_day`） | 月日、`employment_status = 'EMPLOYED'` | — | 当月周年者 | 批处理按键集 | 同名索引随改名重建（`ix_employment_relationship_versions_employment_start_month_day`），结果按关系计（6.2） | 批处理（无请求预算） |
| 迁入拒收清单 | 无归属员工 | — | 聚合计数 | — | 一次性运维查询，不建索引 | 无请求预算 |
| 待放行离职集合 `isHeld`、`heldEmployees`（任一段即是）与将离职集合 `isLeaving`、`excludedEmployees`（同一谓词：带 hold 的 SEPARATE 或 PENDING 的 UNPAID_LEAVE_START；全部在职关系都是才算）（`JdbcPersonnelCommandRepository`；名下属 `separation_holds` 加命令表，不是版本表；`availabilityOn` 经 `OrgDirectoryService` 调用它们） | `employee_id`、状态、生效日 | 无 | 每人 ≤ 数条；集合查询千级 | 集合查询键集分页 | 命令表既有状态、日期索引 + `ix_personnel_commands_relationship`；按关系求值再按 6.2 合成 | 详情（单人）/ 列表（集合） |
| 命令列表（`JdbcCommandReadRepository`，数据范围，规则不变：HIRE/REINSTATE 按目标部门，其余按生效日前一天的任职区间） | 数据范围单位、状态 | 生效日、命令 ID（键集） | 页大小受限 | 键集 | 既有索引；任职区间连接不变 | 列表 |
| `employedIn`、`postsIn`、`hasEmployedFrom`（`JdbcOrgStructureCommandRepository`，按 `employee_id` 连版本） | 部门/岗位、日期 | — | 每单元 ≤ 百级 | 无需 | 连接条件改为 `employment_relationship_id` | 列表 |
| 外键列索引 | — | — | — | — | `ix_employment_relationships_employer_legal_entity_id (employer_legal_entity_id)`；前任复合外键 `ix_employment_relationships_predecessor (predecessor_relationship_id, employee_id) WHERE predecessor_relationship_id IS NOT NULL`；版本表 `ix_employment_relationship_versions_relationship_employee (employment_relationship_id, employee_id)` 覆盖复合外键；任职版本 `ix_assignment_versions_relationship (employment_relationship_id, employee_id)`；命令表 `ix_personnel_commands_relationship (employment_relationship_id, employee_id) WHERE employment_relationship_id IS NOT NULL` | — |

  不建冗余索引：`employment_relationships` 的 `employee_id` 查询由 `UNIQUE (employee_id, employer_legal_entity_id)` 的前缀承担，不另建 `ix_employment_relationships_employee`。

- 并发（O5）：沿用员工根行锁与员工 `version`（ADR-0068 第 1 项，`select … for update`）；`employment_relationships` 除确认回填外不可变，确认更新发生在员工根锁内；注册表 mutability 记 `CONDITIONAL_UPDATE`（见下）。
- 横切口径（O14）、法人与期间（O13）：日期按业务时区的生效日；关系不是过账单据，不归「法人 × 期间」闸门，用人单位法人 ID 就是法人维度。
- 迁移形态（O12、T3、T4）：见第 7 节。开放期内：追加一个迁移，加表加列先可空、回填、有空则整体失败并报聚合数、无空才设 NOT NULL；三张只追加表（版本、命令、任职版本）的回填要先拆各自的追加触发器、补写、再重建（`V20261103130000…employee_assignments.sql` 第 2 节同一做法）；版本表改名同迁移内完成，同步重命名下列对象。回滚：随库重建（`docs/ops/runbooks/dev-db-rebuild.md`）。生产历史迁入（ADR-0036）的映射与 M6 拒收登记另写。
- ADR 条款对照（O15）：
  - ADR-0086 决定 1（实体在员工聚合内）：本表同 schema、锁员工根。决定 2（用人单位不变）：触发器；回填确认只置确认时刻，不改用人单位，所以没有偏离决定 2。决定 3（按关系排他、种类 CHECK、主任职按人）：同日索引、种类 CHECK、主任职一根加任职版本上的关系 ID。决定 4（人员层合成）：6.2 的读路径行。决定 5、6（迁移与事件）：第 7 节、6.1 与下文事件目录。ADR-0086 影响面的「任职版本表增列」：本设计放在 `assignment_versions` 上，与之一致。
  - ADR-0033 第 1、2 项（UUIDv7 主键）；ADR-0068 第 5 项（同日一条、离职之后改为按关系）、第 3 项第 3 条（离职关任职改为按关系）、第 7 项（只追加；DELETE 只放行表属主；新表触发器照此写）；ADR-0043 两维登记（本表含个人信息，见分级）；ADR-0079 不变量 2（主任职按人，已含）。
- 值对象（O16）、自由文本：无自由文本、无 `jsonb`；api 里关系 ID 用强类型 `EmploymentRelationshipId`；继任表的「法律依据引用」届时按 C4 清单。
- 注册表条目草稿（C2、C3、C5）：
  - `organization_workforce.employment_relationships`：`role: AGGREGATE_CHILD`；`aggregate: Employee`；`mutability: CONDITIONAL_UPDATE`，`condition: 只有 employer_source_kind 为 MIGRATION_ATTRIBUTION 且 employer_confirmed_at 为空的行可更新一次（只置确认时刻，用人单位不变），之后冻结（触发器 tg_employment_relationships_append_only，ADR-0086 决定 2 的回填确认）`；`business_key: [employee_id, employer_legal_entity_id]`；`retention: {policy: PERMANENT}` 加注释「个人任职史随员工清除：离职满 10 年删除，沿 `retention-proposal.md:18`；清除次序：保全 → 版本 → 命令 → 任职根 → 雇佣关系（关系最后删，其余三表对它有 RESTRICT 外键），须把本表加入清除函数」；`lineage: {source: NEW}`（回填行参照 `employee_assignments` 的注释写法）；`references: {predecessor_relationship_id: organization_workforce.employment_relationships, employer_legal_entity_id: organization_workforce.legal_entities}`；`writers: [organization.workforce]`；readers 不登记 leave、payroll（它们经 `EmploymentRelationships` api 读，不直接读表）。
  - `organization_workforce.employment_relationship_versions`：沿原 `employee_versions` 条目改名，`references` 加 `employment_relationship_id`，`corrected_from_version_id` 指向改名后的表。`data-classification.txt` 三行（`employment_status`、`is_fee_earner`、`employment_start_date`）与 `unversioned-table-writes.txt` 两行（`JdbcPersonnelCommandRepository`、`JdbcInitialVersionMigrationRepository`）的键名跟着改名；`unversioned-table-writes.txt` 新增 `employment_relationships` 的写入方行（HIRE 入口、`MigrateInitialVersions`、迁移回填、确认四处）。
  - 改名同迁移内重命名的对象：主键 `pk_`、外键 `fk_` 四条、`uk_employee_versions_revision`、`ex_employee_versions_overlap`、全部 `ck_employee_versions_*`、索引 `ix_employee_versions_*`、触发器 `tg_employee_versions_append_only(_truncate)`、函数 `refuse_employee_version_rewrite`，一律换 `employment_relationship_versions` 前缀。
- 分级表（C4）：| 列 | 访问级别 | 个人信息 | 理由 |
  - `employment_relationships.employee_id`｜INTERNAL｜PI｜用途：关联自然人与其用人单位；个人信息 PI（沿员工任职史）
  - `employment_relationships.employer_legal_entity_id`｜INTERNAL｜PI｜用途：某自然人受雇于哪个法人，属就业信息，社保与请假口径据此定；个人信息 PI（与 `employee_versions.employment_status` 同判）
  - `employment_relationships.predecessor_relationship_id`｜INTERNAL｜PI｜用途：换用人单位的前后关系，属就业历史；个人信息 PI
  - `employment_relationships.employer_source_kind`、`employer_confirmed_at`｜INTERNAL｜PI｜用途：就业信息来源与确认记录；个人信息 PI
  - `personnel_commands.employment_relationship_id`｜INTERNAL｜PI｜用途：命令所涉的雇佣关系；个人信息 PI
  - `assignment_versions.employment_relationship_id`、`employment_relationship_versions.employment_relationship_id`｜INTERNAL｜PI｜用途：任职与状态历史所属的雇佣关系；个人信息 PI
- 表注释与列注释草稿（C1）：
  - 表 `employment_relationships`：「雇佣关系：一个自然人与一个用人单位之间的在职关系，员工聚合内的实体；终身不删；除迁入回填行的一次确认外只追加；粒度：每个员工与每个用人单位一行」。
  - 列：`employment_relationship_id`「雇佣关系 ID（UUIDv7）」；`employee_id`「员工 ID」；`employer_legal_entity_id`「用人单位（法人 ID），决定社保公积金与请假口径；建立后不变」；`predecessor_relationship_id`「为空表示非连续任职换用人单位产生」；`employer_source_kind`「DECLARED 人事申报 / MIGRATION_ATTRIBUTION 迁入按归属法人回填、待人事确认」；`employer_confirmed_at`「为空表示回填行尚未确认或非回填行；回填行确认后置值，之后冻结；确认不改用人单位」；`created_at`「创建时间」。
  - 改名后版本表表注释：在原注释上改为「粒度：每个雇佣关系的每个生效区间每个修订一行」；`employment_relationship_id`「所属雇佣关系，复合外键保证同一员工」。
  - 命令表 `employment_relationship_id`「命令所属的雇佣关系；为空表示人员层命令（变更法人归属），其余种类必填」；任职版本 `employment_relationship_id`「本任职版本所属的雇佣关系；主任职换关系时追加新版本，同一任职根不变」。
  - 新 CHECK 名：`ck_employment_relationships_predecessor_not_self`、`ck_employment_relationships_employer_source_kind`、`ck_employment_relationships_confirmed_only_migration`、`ck_personnel_commands_employment_relationship`。
- 分析事实（A1–A8）：无分析事实（本卡不增指标）；A6 不适用。
- 基线 / 目标 / 过渡 / 回滚（T1–T4）：基线 = 每员工一段（第 7 节）；过渡 = 追加迁移加回填，有拒收则整体失败；回滚 = 开放期随库重建，生产前不涉及；ADR-0086「落地后不可回滚」指推翻决定，不指表结构。
- 专项（E1–E3）：
  - 事件：`EmployeeHired / Regularized / Transferred / Separated / Reinstated / Corrected / UnpaidLeaveStarted` 各加 `employmentRelationshipId`（uuid，非空，向后兼容，ADR-0035 第 3 条）；`EmployeeLegalEntityChanged` **不加**（人员层归属）。
  - 新增五个事件的目录条目草稿（格式按 `docs/architecture/events/workforce.yaml` 现有条目；发布方 `organization.workforce`；信封 aggregate Employee，`aggregateId` 员工 ID，信封 `legalEntityId` 取员工在生效日的归属法人，与现有事件一致；用人单位在负载 `employerLegalEntityId`）：

| 事件 | 触发 | 负载（均 uuid / LocalDate，不含个人信息） | businessDate | 消费方 | publisherTest |
|---|---|---|---|---|---|
| EmploymentRelationshipStarted | HIRE 或 REINSTATE 生效，或第二段关系开始 | personnelCommandId、employeeId、employmentRelationshipId、employerLegalEntityId、predecessorRelationshipId（可空）、effectiveDate | effectiveDate | `platform.audit`；`noConsumer`（leave、payroll 尚未建，按关系订阅，现在发布是因为补不回） | `…workforce.domain.EmploymentRelationshipEventsTest` |
| EmploymentRelationshipEnded | SEPARATE 生效 | personnelCommandId、employeeId、employmentRelationshipId、effectiveDate | effectiveDate | 同上 | 同上 |
| EmploymentRelationshipPhaseChanged | REGULARIZE 生效（以后含用工类型变化） | personnelCommandId、employeeId、employmentRelationshipId、phaseKind（PROBATION/REGULAR）、effectiveDate | effectiveDate | 同上 | 同上 |
| EmploymentRelationshipUnpaidLeaveChanged | UNPAID_LEAVE_START / END 生效 | personnelCommandId、employeeId、employmentRelationshipId、onUnpaidLeave（boolean）、effectiveDate | effectiveDate | 同上 | 同上 |
| EmploymentRelationshipCorrected | CORRECTION 生效 | personnelCommandId、employeeId、employmentRelationshipId、correctedFromEffectiveDate、effectiveDate | 更正后的生效日 | 同上 | 同上 |

  - 读模型：`EmploymentRelationships` 是对自有表的只读 api，不是投影表，不涉及重算与对账（E2）；迁入映射见第 7 节（E3）。
- 预期表结构 diff（逐表）：
  1. 新表 `organization_workforce.employment_relationships`：列、主键、`UNIQUE (employee_id, employer_legal_entity_id)`、`UNIQUE (employment_relationship_id, employee_id)`、外键（员工、法人、前任复合）、4 条 CHECK、索引 `ix_employment_relationships_employer_legal_entity_id`、`ix_employment_relationships_predecessor`、触发器与函数 `tg_employment_relationships_append_only(_truncate)` / `refuse_employment_relationship_rewrite`、表与列注释。
  2. `employee_versions` → `employment_relationship_versions`：**主键列 `employee_version_id` 改名 `employment_relationship_version_id`**（连同引用它的自引用外键 `corrected_from_version_id`、列注释与代码别名）；加 `employment_relationship_id`；复合外键；`uk_…_revision` 改键；`ex_…_overlap` 改键；`corrected_from_version_id` 外键改复合并加 `UNIQUE (employment_relationship_version_id, employment_relationship_id)`；索引 `ix_employment_relationship_versions_employee`、`…_relationship_employee`；对象改名（见上）；注释。
  3. `personnel_commands`：加 `employment_relationship_id`（可空）；复合外键；`ck_personnel_commands_employment_relationship`；`ck_personnel_commands_hire_columns`、`ck_personnel_commands_reinstate_columns` 改写；旧 `uk_personnel_commands_open_per_day` 删，换 `uk_personnel_commands_open_per_relationship_day` 与 `uk_personnel_commands_open_person_day`；`uk_personnel_commands_open_per_assignment_day` 谓词放宽到全部任职；`ix_personnel_commands_relationship`；注释。**不加** `target_employer_legal_entity_id`。
  4. `assignment_versions`：加 `employment_relationship_id`；复合外键；`ix_assignment_versions_relationship`；注释。
  5. 计数：新增 1 表；改 3 表（版本表改名并加 1 列、命令表加 1 列、任职版本加 1 列）；排他约束 1 个改键；唯一键 1 个改键（`uk_…_revision`）；部分唯一索引 1 个拆为 2 个、1 个放宽谓词；新增 UNIQUE 3 条（关系表 2 条、版本表 1 条）；CHECK 新增 4 条、改写 2 条；外键新增 6 条（其中复合 4 条：前任、版本、命令、任职版本；简单 2 条：员工、法人）、改写 1 条（`corrected_from_version_id` 改复合）。
  6. 注册表、`data-classification.txt`、`unversioned-table-writes.txt` 的键名与新条目如上。
- 签发：数据设计签发由 one-design 承担（`docs/rules/roles/design.md`）；本附录作者即 one-design，故由独立审查员按门禁评审，记录写入 docs/data/reviews 下以卡号命名的文件，签发行格式见门禁附录 B。
