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
employment_relationships                 -- entity in the Employee aggregate; rows never updated
  employment_relationship_id  uuid PK
  employee_id                 uuid NOT NULL FK employees
  employer_legal_entity_id    uuid NOT NULL FK legal_entities
  predecessor_relationship_id uuid NULL                       -- same employee, different employer
  employer_source_kind        text NOT NULL CHECK ('DECLARED','MIGRATION_ATTRIBUTION')
  created_at                  timestamptz NOT NULL
  UNIQUE (employee_id, employer_legal_entity_id)
  UNIQUE (employment_relationship_id, employee_id)            -- composite FK target
  FK (predecessor_relationship_id, employee_id) -> (employment_relationship_id, employee_id)
  CHECK predecessor_relationship_id <> employment_relationship_id
```

`employee_versions` (renamed in the schema PR; name proposed in the glossary file as `employment_relationship_versions`): add `employment_relationship_id NOT NULL`, composite FK `(employment_relationship_id, employee_id)`; exclusion `EXCLUDE (employment_relationship_id WITH =, daterange(...) WITH &&) WHERE superseded_at IS NULL` replaces the per-employee one.

`personnel_commands`: add `employment_relationship_id` **nullable with a per-kind CHECK**: NOT NULL for HIRE, REGULARIZE, TRANSFER, SEPARATE, REINSTATE, UNPAID_LEAVE_START/END, CORRECTION (names the relationship corrected), START_CONCURRENT, END_CONCURRENT; NULL for CHANGE_LEGAL_ENTITY (person-level attribution). HIRE also carries `target_employer_legal_entity_id` NOT NULL (HIRE only). Composite FK `(employment_relationship_id, employee_id)`.

`assignment_versions` / `employee_assignments`: add `employment_relationship_id NOT NULL` on the assignment root (an assignment belongs to one relationship). The one-PRIMARY rule stays **per person** (`uk_employee_assignments_primary` unchanged; ADR-0079 invariant 2): one person has one primary assignment root across all relationships. A relationship may have only CONCURRENT assignments or none.

Unchanged: `employee_legal_entities`, `employees` (snapshot columns stay derived from the person-level verdict).

### 4.2 Invariants

1. One person x one employer = one relationship; two relationships of one person may overlap in time; versions of one relationship never overlap.
2. The employer of a relationship never changes except by succession (4.3).
3. A person is in service on D iff some relationship is in service on D (ADR-0079 invariant 1, restated).
4. Of the three legal-entity meanings (ADR-0079 invariant 4): meaning 1, IIT withholding and payroll run, by the **paying** entity (default: the relationship's employer; recorded by payroll if different); meaning 2, social insurance and housing fund, by the **relationship's employer** (this replaces "劳动合同的用人单位"); meaning 3, data scope and cost, by the attribution entity (unchanged). Changing one does not change the others.
5. No relationship, no employer: HIRE always names the employer; there is no default employer.
6. A person in service on D has exactly one PRIMARY assignment in service on D (as today); a command that would leave an in-service person without one is refused (6.3).

### 4.3 Employer succession (ADR-0079 invariant 14)

Merger or split of an employer: the contract continues with the successor and is not a cross-entity move. Rule: the only way an employer changes inside one relationship is a **succession record** (relationship id, from entity, to entity, effective date, legal basis reference), append-only, written by a SUCCEED_EMPLOYER command. `employerOn(relationshipId, date)` reads it; a relationship without records has its base employer. Consumers that need the employer on a date use that read; leave quota and enrolment keep their relationship key, so nothing re-keys. **Not built now**: no case is known; the table and command wait for the first real merger or split. Until then re-pointing is refused. Whether to build it in the first schema PR is B-G.

## 5. Command binding

Every command names its relationship explicitly. Nothing infers a relationship from an org unit, because an org unit's entity is the *attribution* entity (meaning 3), not an employer.

| Command | Binding |
|---|---|
| HIRE | Names `target_employer_legal_entity_id`. New person: employee and relationship are created **at command entry** (versions are written at entry, including future dates, ADR-0068 item 8, so the relationship row must exist then). Existing person, an employer with no relationship yet: second relationship ("additional employer", not a rehire). Existing person, same employer: this is REINSTATE. HIRE with `predecessor_relationship_id`: employer change in continuous service (below) |
| REGULARIZE, UNPAID_LEAVE_START/END | One relationship |
| TRANSFER | Org-unit/position change on one assignment of one relationship. It **never changes the employer**. If the target unit's attribution entity differs from the person's, ADR-0073 applies unchanged (attribution change, countersign, `AWAITING_ATTRIBUTION_COUNTERSIGN`). If the target unit's attribution entity differs from the *relationship's employer*, allowed or refused is B-H (recommendation: allowed, since attribution and employer are independent meanings) |
| SEPARATE | One relationship; HELD (separation hold for administrators) applies only to SEPARATE. Refused if the relationship carries the person's PRIMARY assignment and another relationship stays in service, unless the same entry moves the primary to the other relationship (6.3) |
| REINSTATE | One relationship that has ended |
| CORRECTION | Names the relationship whose versions it corrects |
| START_CONCURRENT, END_CONCURRENT | Name the relationship that employs the person in that role; the unit's attribution entity need not equal the employer (B-H) |
| CHANGE_LEGAL_ENTITY | Person level (attribution), relationship id NULL |
| Employer change in continuous service | One entry of two commands on the same effective date: SEPARATE on the old relationship, HIRE (with `predecessor_relationship_id`) on the new employer. The person stays in service; neither `EmployeeSeparated` nor `EmployeeHired` fires (6.1) |
| Withdraw a pending HIRE | The command is withdrawn as today (ADR-0068 item 6); its versions are voided by the existing void-and-append mechanism. A relationship row left with no live version is "void": invisible to reads, and re-used by a later HIRE for the same pair (UNIQUE). Rows are never deleted |

**Same-day and after-separation rules** (ADR-0068 item 5) become **per relationship** for relationship-bound kinds: at most one open command per (relationship, effective date); after an unreversed SEPARATE on a relationship only REINSTATE may be entered later on *that relationship*. Person-level kinds (CHANGE_LEGAL_ENTITY) keep the per-employee rule with a NULL relationship id. This is what lets SEPARATE (old) and HIRE (new) share a date. Concurrency is unchanged: all commands lock the employee root row.

## 6. Events and reads

### 6.1 Events: firing rules

Event envelope stays the employee (aggregate) with `businessDate`; every event gains `employmentRelationshipId` additively. Person-level events:

| Event | Fires when |
|---|---|
| EmployeeHired | the person goes from not in service to in service by a HIRE (first or additional or predecessor-less) |
| EmployeeSeparated | the person goes from in service to not in service (last in-service relationship ends); not on employer change (same date successor) |
| EmployeeReinstated | the person goes from not in service to in service by a REINSTATE |
| EmployeeRegularized | each relationship's regularization (payload names the relationship) |
| EmployeeTransferred | each TRANSFER (payload names the relationship and assignment) |
| EmployeeCorrected | each CORRECTION (payload names the relationship) |
| EmployeeUnpaidLeaveStarted | each UNPAID_LEAVE_START takes effect (built by PR #1287; payload names the relationship once ADR-0086 is built); identity's sensitive-role review reads it |
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
| `availabilityOn.leaving` | true only if **all** in-service relationships have an effective SEPARATE ahead |
| `feeEarner` | read from the person's PRIMARY assignment's position, so unchanged |
| `EmployeeProfile.orgUnitId` | the PRIMARY assignment's unit; non-null stays true because of invariant 6 |
| Timesheet submission, approver resolution | unchanged person-level; whether a person on unpaid leave in one entity but working in another may submit time, and whose approver applies, are B-I |

Leave (slice L3, RULE-515): entitlement and quota keyed by `(employmentRelationshipId, leaveYear)`; a request names its relationship (a person with one relationship in service gets it defaulted; with two, the person chooses, never guessed); days on which the relationship is not in service are refused; RULE-510 uses `StandardHours.minutesOn(employerLegalEntityId, date)` (#1310) of the relationship's employer.

### 6.3 Primary assignment across relationships

Invariant 6 is checked at command entry under the employee root lock. SEPARATE of the relationship holding the PRIMARY assignment while another relationship stays in service is refused unless the entry also designates a new PRIMARY in the other relationship (a TRANSFER-style move); a person's only in-service relationship may end without that (the person leaves service). HIRE of a person's first relationship needs a PRIMARY assignment as today; a second-employer HIRE does not (B-A).

## 7. Migration of today's rows

One relationship per employee; `employer_legal_entity_id` = the attribution entity at the **first version date** (option 1) or the **current attribution** (option 2). Recommendation: current attribution at the migration date, because ADR-0073 attributes by day and the legacy single entity per person (A8, ADR-0073:67) means the early attribution may be a later correction; either way the value is `MIGRATION_ATTRIBUTION`, awaiting HR confirmation (B-B). Persons already employed by two entities today have only one relationship after migration; the second is entered afterwards as a back-dated HIRE (back-dating is allowed, ADR-0068 item 8) once HR lists them. Employees with no attribution timeline cannot be backfilled: reject list for HR, no guessing. Existing versions, commands and assignments gain the id by join on employee. Counts are aggregates only [U: not run].

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

One decision: employment relationship as an entity of the Employee aggregate with an immutable employer. Partial supersessions, listed in the ADR header (exact scope phrases there): ADR-0079 decision 3 item 1 (in-service sentence), decision 4 table row 1 (subscribers of person events) and the labour-contract row (employer change), decision 5 invariant 1, invariant 4 (social-insurance clause only; withholding stays with the paying entity), invariant 14 (succession rule), ADR-0068 item 5 (same-day and after-separation per relationship) and the item-1 sentence on commands and versions (now per relationship inside the aggregate), ADR-0084 decision 2 (whole item rewritten: employer is an attribute of the relationship). ADR-0073 is **not** superseded: TRANSFER never changes the employer, so decision 3 item 4 (cross-entity transfer countersign) keeps its meaning, which the retrieval record in the ADR confirms by search.

## 11. Done-when (one checkable line each)

1. Glossary entries for the new names merged as `proposed` (`backend/glossary/proposed/employment-relationship.yaml`).
2. Schema PR matches appendix A table by table; the UNIQUE rejects a second relationship for the same pair; the per-relationship exclusion rejects overlapping versions of one relationship but accepts overlapping versions of two relationships of one person; the per-kind CHECK rejects a NULL relationship id for HIRE and accepts it for CHANGE_LEGAL_ENTITY.
3. Migration test: every existing employee has exactly one relationship, no version, command or assignment lacks one, employees without attribution appear in the reject list (aggregate count).
4. Person with two relationships, one ended: in service; both ended: SEPARATED; `EmployeeSeparated` fires once, on the last end only.
5. Second-employer HIRE for an in-service person creates a second relationship without touching the first and without firing `EmployeeHired`.
6. SEPARATE (old) + HIRE with predecessor on one date: person stays in service, neither `EmployeeSeparated` nor `EmployeeHired`, the same-day rule accepts both because it is per relationship.
7. `EmploymentRelationships.on` returns both relationships in a dual-employment test; no scalar `employerOf(employee, date)` exists (grep).
8. SEPARATE of the relationship holding the PRIMARY assignment while another stays in service is refused.
9. The composition rules of 6.2 each have a test (unpaid leave all/any, separation hold, leaving).
10. The leave read for L3 is exactly `on`, `get`, `employerOn`; RULE-515 triggers are covered by the five relationship events.
11. Existing guards green; identity, approval, timesheet code unchanged in the schema PR.

Out of scope: the labour contract zone, employment-type values (that card adds the type to the relationship versions), payroll's enrolment and withholding tables, the succession table (B-G), any code.

## 12. Evidence register

[V] section 1, section 8 rows 1 and 3, DDL line references; [U] section 8 rows 2, 4, 5, migration counts. Sources: repository files cited inline; the two official URLs in section 8; user words on issue #1295.

## Appendix A. 数据设计

- 涉数据：是（新表 `employment_relationships`；改 `employee_versions`、`personnel_commands`、`employee_assignments`）。
- 表 → 出处（B1、B2）：| `employment_relationships` | 信息图员工对象下的子实体；**请求 one-architecture 在信息图补一个雇佣关系实体或把它挂到员工对象（B1 的前置，未获答复前本卡不过门禁）** | 聚合 Employee（`built.yaml` 的 workforce 员工聚合；同 schema `organization_workforce`） | role 暂拟 `REFERENCE_HISTORY`，由 one-data 定 |
- 状态、关系、基数（B3、B3a、B4）：本表无状态列（来源列 `employer_source_kind` 为二值 CHECK，来由见设计稿 4.1）；员工 1—N 关系（`employee_id` NOT NULL 外键）；关系 1—N 版本（复合外键）；关系 0..1 前任（复合外键，列注释「为空表示：非换用人单位产生」）；用人单位 N—1 `legal_entities`（同 schema 外键）。
- 不变量 → 约束（B5、O4）：

| 不变量 | 承担者 | 无法落约束时的理由 |
|---|---|---|
| 4.2-1 一人一用人单位一段 | `UNIQUE (employee_id, employer_legal_entity_id)` | — |
| 4.2-1 同一关系版本不重叠 | `EXCLUDE (employment_relationship_id =, daterange &&) WHERE superseded_at IS NULL` | — |
| 前任属同一员工且不是自己 | 复合外键 + `CHECK <>` | — |
| 命令的关系属本员工 | 复合外键 `(employment_relationship_id, employee_id)` | — |
| 按种类关系 ID 是否可空 | `CHECK` 按种类 | — |
| 4.2-2 用人单位不变 | 触发器拒绝改写（同 ADR-0068 第 7 项只追加） | 无法用静态约束 |
| 4.2-3 在职判断 | 应用（读合成） | 跨行时间合成，非约束可表 |
| 4.2-6 在职者有且仅有一个主任职 | `uk_employee_assignments_primary`（一根）+ 命令入口在锁住员工根后校验在职日有效 | 日期覆盖无法落约束 |
| 同日一条、离职之后（按关系） | 部分唯一索引改为 `(employment_relationship_id, effective_date…)`（关系绑定种类）+ 应用校验 | 离职之后规则跨行，沿 ADR-0068 第 5 项，由应用校验 |

- 用例 → 聚合 → 表（O1）：全部人事命令写 Employee 聚合：员工根（锁）、`employment_relationships`（仅 HIRE 入口）、版本表、命令表、任职表；一个事务，无多聚合例外。
- 业务键、类别、时态（O3、O6）：| `employment_relationships` | `(employee_id, employer_legal_entity_id)` | 主数据 | 不需要版本：只追加、用人单位不变（继任另表，设计稿 4.3，暂不建） |
- 查询路径（O10）：| 路径 | 过滤列 | 排序列 | 预期行数 | 分页 | 索引 |
  - `on(employee, date)`：`employee_id` + 版本表 `(employment_relationship_id, 生效区间)` | 无 | 每人 ≤ 数条 | 无需分页（≤ 数条） | `ix_employment_relationships_employee (employee_id)`；版本表既有索引加 `employment_relationship_id` 前缀
  - `get(id)`：主键
  - 外键列索引：`predecessor` 复合外键列 `ix_employment_relationships_predecessor`；命令表与任职表新列各一个以它开头的索引。
- 并发（O5）：沿用员工根行锁与员工 `version`（ADR-0068 第 1 项）；`employment_relationships` 不可变，无需 `version`，注册表 mutability 记 `APPEND_ONLY`。
- 横切口径（O14）、法人与期间（O13）：日期按业务时区生效日；本表不带会计期间，用人单位法人 ID 即法人维度（不归「法人 × 期间」闸门，因不过账）。
- ADR 条款对照（O15）：ADR-0033 第 1、2 项（UUIDv7 主键，已含）；ADR-0068 第 5、7 项（只追加，已含；同日一条改按关系）；ADR-0043 两维登记（本表无个人信息）；ADR-0079 不变量 2（主任职按人，已含）。
- 值对象（O16）、自由文本：无自由文本、无 `jsonb`；继任表的「法律依据引用」届时按 C4 清单。
- 注册表条目草稿（C2、C3、C5）：role 待 one-data；mutability `APPEND_ONLY`；retention 随员工历史（离职满十年清除，沿 ADR-0068 第 7 项的清除函数，须把本表加入清除函数）；lineage 来自 HIRE 命令；writers workforce 人事命令用例；readers workforce、经 `EmploymentRelationships` api 读的 leave 与 payroll。
- 分级表（C4）：| `employer_legal_entity_id` | 内部 | 非个人信息 | 用途：定社保与请假口径 |；`employee_id` 沿 `employees` 既有级别；`employer_source_kind` 内部。
- 表注释与列注释草稿（C1）：表「一个自然人与一个用人单位之间的雇佣关系，终身不删」；`predecessor_relationship_id` 「为空表示非连续任职换用人单位产生」；`employer_source_kind` 「MIGRATION_ATTRIBUTION 表示由迁入按归属法人回填、待人事确认」。
- 分析事实（A1–A8）：无分析事实（本卡不增指标）。
- 基线 / 目标 / 过渡 / 回滚（T1–T4）：基线 = 每员工一段（第 7 节）；回滚见 ADR-0086：落地后不可回滚。
- 专项（E1–E3）：事件载荷各加 `employmentRelationshipId`（向后兼容）；读模型 `EmploymentRelationships`；迁入映射第 7 节。
- 预期表结构 diff：见 one-data 签发后的架构 PR；形状即 4.1，预期新增 1 表、3 表各加 1 列、1 个排他约束改键、1 个部分唯一索引改键、若干 CHECK。
- 签发：待 one-data（审查记录写入 docs/data/reviews 下以卡号命名的文件）。
