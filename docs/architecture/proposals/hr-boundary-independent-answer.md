# Independent review: HR zone boundaries around organization.workforce

Reviewer: independent architecture reviewer, read-only, worktree one-design-23b770 (origin/main, 2026-11-04).

## Files opened in docs/adr and docs/architecture/proposals

- docs/adr: **none opened.**
- docs/architecture/proposals: **none opened.**
- Disclosure: allowed files cite the forbidden rationales by name only. `docs/architecture/ssot/domain/contexts.yaml` (notes on organization.incentive and organization.payroll cite "ADR-0079 决定 2"), `docs/architecture/ssot/aggregates/planned.yaml` (every B-13 `bc_heading` says "AGG-106 绩效评估的归属论证见 §9 续四"; it names the draft as `meta.source`) and `docs/business/rules/leave-rules.md` (cites "ADR-0079:99"). I did not follow any of these references. `docs/legacy/seeyon-oa/process-inventory.md` cites ADR-0021; I did not open that either.
- Nothing under docs/decisions was opened.

Legend: **[V]** means I opened the file or ran the query myself. **[U]** means unverified: an inference, or a claim taken from a file that I did not check against code.

---

## 0. Baseline facts (all zones)

### 0.1 What exists in code today
- [V] `backend/src/main/java/com/forcitis/one/organization/` contains only `administration`, `attendance`, `workforce` (and package-info). There is no `leave`, `payroll`, `performance`, `recruitment` or `contract` package.
- [V] `organization.administration` is a scaffold of 6 lines (a package-info only), but it already has an approved module edge to workforce, approval, audit, file and numbering (`backend/architecture/module-dependencies.txt` line 11). So creating a new BC is routine here.
- [V] workforce is about 16,585 lines of main code, with 190 test files across `src/test/.../organization/workforce` and `src/test/.../acceptance/organization/workforce`. That makes it the largest business module (attendance has 878 lines).
- [V] Tables in `organization_workforce` (migrations under `backend/src/main/resources/db/migration/organization_workforce/`): employees, employee_versions, employee_legal_entities, legal_entities, legal_entity_versions, legal_entity_attributions, legal_entity_attribution_corrections, org_units, org_unit_versions, org_unit_legal_entities, positions, position_versions, assignments, assignment_versions, employee_assignments, personnel_commands, org_structure_commands, separation_holds, attribution_countersigns, attribution_countersign_bases, employee_profile_change_requests, state_transitions. The sync_* tables have been dropped.
- [V] **No table, column, class, event or permission code exists for recruitment, labour contract, employer entity, performance, practice certificates or skills.** I grepped backend/src/main for employer, contract, performance, certificate, recruit, candidate, skill and their Chinese terms. The only hits were "candidate" in approval and ReportingLines code, with unrelated meanings. So none of the four zones has any code. **Today the boundary decision is free. Every later option costs more.**

### 0.2 Dependency direction (the most important structural fact)
- [V] `module-dependencies.txt`:
  - `organization.workforce -> platform.audit, platform.integration, organization.attendance`
  - `platform.approval -> platform.identity, organization.workforce, ...`
  - `platform.identity -> organization.workforce, ...`
  - `platform.numbering -> platform.identity, organization.workforce, ...`
  - `cost.timesheet -> organization.workforce, platform.approval, ...`
  - `organization.administration -> organization.workforce, platform.approval, platform.audit, platform.file, platform.numbering`
- [V] workforce sits **below** approval, identity and numbering. It cannot use platform.approval, platform.identity or platform.numbering without closing a cycle. The repo already works around this with three kernel ports (header comments of module-dependencies.txt):
  - `kernel.SeparationReview`, implemented by identity;
  - `kernel.GlobalDocumentNumberSource`, implemented by numbering;
  - `kernel.ExternalSubjectEmployees`.
- [V] It also copies the permission codes locally: `web/WorkforcePermissions.java` says "Copies of platform.identity Permission.Codes (workforce may not depend on identity)".
- [V] Workforce has grown its own approval-like mechanisms instead of using platform.approval:
  - attribution countersigns (`attribution_countersigns`, `CountersignDecisionController`, deadline and escalation jobs);
  - separation holds with approve permission `organization.workforce:separation-hold:approve`;
  - second-person profile change requests (`employee_profile_change_requests`, CHECK `ck_..._two_people`).
- [V] `planned.yaml` says the same thing in its own words. AGG-65 (recruitment requisition), AGG-67 (labour contract) and AGG-62 (resignation approval) each say approval is "本聚合命令，单级，不经 platform.approval", because "已建边 platform.approval → organization.workforce，本 BC 实现审批钩子即成环".
- **Conclusion [V for the facts, my judgement for the reading]:** "needs single-level approval" is **not a domain property of these zones**. It follows from where the zones were placed, given the dependency direction. Put a workflow-shaped zone inside workforce and it either gets a bespoke single-level approval or adds a fourth kernel port. Put it in a downstream BC (the administration pattern) and it uses platform.approval normally. So the approval argument does not decide the boundary. It is evidence that workflow-shaped zones do not fit inside the upstream master-data module.

### 0.3 Who consumes workforce today
- [V] Imports of workforce types from outside the module (main code), by module:
  - platform.identity: api.Availability, EmployeeLabel(s), EmployeeLegalEntities, EmployeeProfile, EmploymentStatus, EmploymentVerdict, LoginLookup, OrgDirectory, OrgUnitId, OrgUnitProfile, PositionId, PersonnelCommandId, OrgStructureCommandId, TriggerFieldKind, WorkforceError. Events consumed: EmployeeHired, EmployeeSeparated, EmployeeTransferred, EmployeeCorrected, EmployeeReinstated, OrgUnitDissolved, OrgUnitMerged.
  - platform.approval: EmployeeProfile, EmploymentStatus, EmploymentVerdict, LoginLookup, OrgDirectory, OrgUnitId, OrgUnitProfile.
  - cost.timesheet: EmployeeLegalEntities, EmploymentStatus, OrgDirectory, OrgUnitId.
  - platform.numbering: LegalEntityCode, LegalEntityDirectory, LegalEntityNumberUsage.
  - No other module imports workforce `domain` or `infrastructure` types.
- [V] No foreign key from any other schema references `organization_workforce` (grep over all migrations). `data-access-registry.yaml` lists only `organization.workforce` as reader or writer of the workforce tables, and `external-read-registry.yaml` has no workforce entry. Encapsulation at table level is clean today.
- [V] Events are thin. They carry the envelope, the command id, the employee id and a date, plus for transfer and correction the trigger field kinds and previous/new OrgUnitId and PositionId. No personal data or zone data travels in payloads (`events/EmployeeHired.java`, `EmployeeSeparated.java`, `EmployeeTransferred.java`, `EmployeeLegalEntityChanged.java`, `EmployeeRegularized.java`, `EmployeeCorrected.java`).

### 0.4 Infrastructure that turns a later split into more than moving packages
These apply to any zone built inside workforce now and split later:

1. [V] **Persisted event class names.** `platform_integration.event_publication.event_type` is commented "事件类型（事件类的全名）" and `listener_id` is "监听方法的全名"; the archive table is the same. Moving an event class to another package orphans incomplete and archived publications. A split needs a rename migration of those rows, or a drain-then-deploy step.
2. [V] **Permission codes carry the module name.** The form is `{subdomain}.{bc}:{resource}:{action}`, enforced by NamingSurfaceTest G43 (`src/test/java/com/forcitis/one/NamingSurfaceTest.java:197`). The codes live in `platform/identity/api/Permission.java` and in identity migration data: menu rows in `V20261014130000__platform_identity_baseline.sql` lines 604-605 and `V20261104190000__platform_identity_employee_roster_menu.sql` line 4 hold `organization.workforce:*` codes. A zone created as `organization.workforce:performance-review:*` and split later needs a code rename, an identity data migration of menus and role grants, a WorkforcePermissions copy update and a change to every `@PreAuthorize`. [U] I did not open the role-grant table itself.
3. [V] **The shared append-only `state_transitions` table** has a CHECK over aggregate types. It currently reads `('PersonnelCommand','SeparationHold','OrgStructureCommand','AttributionCountersign')` (`V20261102160000...attribution_countersigns.sql:179`). Its append-only triggers refuse UPDATE, DELETE and TRUNCATE (`baseline.sql:945-947`), and its retention is PERMANENT (`data-access-registry.yaml` line 71ff). New zone aggregates written into it cannot be moved out later without a migration-only exemption to the append-only rule. Moving rows across schemas is exactly what the guards are built to refuse.
4. [V] **Intra-schema FKs and the version common set.** Dated rows written by personnel commands must carry `change_channel_kind`, `change_command_id` and `superseding_command_id`, with FKs to `personnel_commands` and CHECK `(change_channel_kind='COMMAND') = (change_command_id IS NOT NULL)`. Examples are `employee_legal_entities` (`V20261102140000...common_set.sql`) and `employee_versions`. Every workforce table also FKs `employees(id)`. The repo rule across schemas is "只存 ID" (seen in column comments). A table built this way and split later loses its FKs, and its writes stop being in the same transaction as the personnel command. That is the **real** cost: transactional coupling becomes a cross-module protocol.
5. [V] **Audit subject kinds have an owning module.** `platform_audit.audit_subject_kinds` rows carry a module column (`V20261014134000__platform_audit_baseline.sql` ~line 70). `audit_entries` is partitioned, append-only and FKs `subject_kind`. A split needs the kinds' owner changed, and old entries keep the old kind for 3 years online.
6. [V] **The shared api types are the coupling surface.** EmployeeProfile, EmploymentStatus and OrgDirectory are imported by identity (47 files), approval (10), timesheet (3) and numbering (3). If any zone pushes a field into them (a rating, a candidate status, an employer entity), every consumer changes again at split time.
7. [V] **Error codes** are `ORGANIZATION_WORKFORCE_*` (for example in the `api/EmployeeLegalEntities.java` javadoc; `WorkforceError` has 52 import sites). These are HTTP problem codes the client sees.
8. [V] **Governed tests.** The acceptance tests for workforce (`src/test/java/.../acceptance/organization/workforce/*IT.java`, plus `F1_730_S4_ACCEPTANCE.md`) may not be changed by an implementer (CLAUDE.md baseline 4). Moving zone acceptance tests to a new package is a governed change. The guard registries (ModuleDependencyTest via module-dependencies.txt, DataArchitectureTest via data-access-registry.yaml, NamingSurfaceTest) must change in the same PR.
9. [U] HTTP paths: I did not verify whether G41 puts the module name in paths. If it does, a split also breaks URLs.

**Answer to "split later = move packages and tables":** false for this codebase. It is true only for a zone that, from day one, uses its own tables with no FK to personnel_commands, its own state-transition log, its own permission prefix, its own api interfaces and its own event classes. A zone built to those rules is a separate BC in all but name. The decision is therefore not really "workforce now, split later"; it is "workforce now and pay items 1-8 later".

---

## 1. Q1 Performance results (AGG-106)

### 1.1 Facts
- [V] `planned.yaml` AGG-106 "绩效评估":
  - contents: type (monthly / probation), evaluation period, evaluator, conclusion; documents: management one-to-one, growth record;
  - commands: compile, submit, confirm, compile probation evaluation, register correction;
  - event "绩效评估已确认", subscribed by B-15 (payroll) as the bonus basis (P-46);
  - references AGG-62 (person and evaluator) and AGG-63 (assignment at evaluation time, optional);
  - INV-114: one evaluation per person, period and type; immutable after confirmation; corrections recorded separately.
  - AGG-76 "奖金核定" (payroll) reads AGG-106 via api.
- [V] INV-114 owner_bc is organization.workforce (`docs/architecture/ssot/constraint-rules/rules.yaml:1105`). That is the claim under test.
- [V] contexts.yaml: organization.incentive is retired, its incentive part merged into organization.payroll, with a note "绩效结果归 organization.workforce". The organization.payroll responsibility includes 激励奖金.
- [V] A8 usage (`docs/legacy/runtime-usage/a8-templates.tsv`, columns total / last_180d / last_365d / 2026 / running / first_use / last_use):

| Template | total | last 365d | 2026 | running | first use | last use |
|---|---|---|---|---|---|---|
| 月度评估表-交付人员 | 2,516 | 448 | 338 | 116 | 2022-01-20 | 2026-09-30 |
| 月度评估表-BU支持人员 | 719 | 242 | 172 | 20 | 2023-08-08 | 2026-09-30 |
| 月度评估表-总部人员 | 244 | 92 | 74 | 20 | 2023-07-31 | 2026-09-30 |
| 试用期评估流程(第一个月) | 113 | 74 | 61 | 6 | 2025-06-07 | 2026-10-10 |
| 试用期评估流程(第二个月) | 96 | 65 | 48 | 8 | 2025-06-07 | 2026-10-11 |
| 试用期员工转正申请 | 99 | 65 | 45 | 3 | 2025-06-02 | 2026-09-28 |
| 管理层一对一 | 1,738 | **0** | 0 | 0 | 2022-01-14 | **2025-07-22** |
| 成长记录维护 | 23 | 2 | 0 | 0 | 2021-12-01 | 2025-12-18 |

- [V] `a8-forms.tsv`, the matching archive and config forms: 月度评估表档案 2,325 rows; 月度评估表设置 65 rows (config, form_type 6); 管理层一对一档案 458; 试用期评估档案 188. Note that a8-forms `modified` is the form-definition date, not last usage. I used `last_use` from a8-templates for usage.
- [V] Bonus-side A8 forms are new and tiny: 组织奖金计提 7 rows and 个人奖金补录 6 rows, both created 2026-08-03 (form_type 6).
- [V] `process-inventory.md` lines 114, 116 and 120 label the monthly evaluations "绩效，奖金计提输入" and assign them to `organization.incentive（HRM）`. Lines 121 and 123 assign the probation evaluations to `organization.workforce`.
- [U] The claim that evaluation results feed the bonus is a **label only**. There is no field-level evidence in the repo linking formmain_1140/1275/1270 to the bonus forms. The bonus forms date from 2026-08, so the link may not exist in A8 at all.
- **Missing:** who fills the forms (initiator role, number of nodes, self-assessment or not). The repo has no A8 node or initiator data for these templates. The 116 running delivery evaluations against 227 started in 180 days suggest multi-step or long-open instances [U].

### 1.2 Test
- **Change drivers:**
  - Management and HR policy: evaluation templates per population, scales, periods.
  - Evidence of volatility [V]: three population-specific forms introduced 2022-01, 2023-07 and 2023-08; probation evaluation introduced 2025-05/06; one-to-one retired 2025-07; growth record dormant.
  - That is a new or retired evaluation form roughly every year. The employee master changes for a different reason: personnel events and data-scope rules.
- **Actors:** evaluators (line managers), the evaluated employee and HR confirming. Personnel commands are entered by HR staff. Evaluators are a much wider and different population.
- **Lifecycle:**
  - An evaluation is a periodic document: about 12 or more per person per year. Its states are draft, submitted, confirmed, plus a correction. Old ones are frozen.
  - The employee record is one long-lived, effective-dated record. The two share nothing except the person id and an optional assignment reference.
- **Data sensitivity:** ratings and conclusions are personal data whose readers (evaluator chain, HR, payroll) are not the readers of the roster (`organization.workforce:employee:read`). [U] A rating access level is not yet classified in `data-classification.txt`, because no column exists.
- **Consumers:**
  1. payroll bonus (AGG-76 reads by id, api);
  2. regularization: the probation evaluation leads to the workforce REGULARIZE personnel command, which exists [V] in `api/PersonnelCommandKind.java`;
  3. [U] management analytics.
  - Nobody needs performance in the same transaction as an employee write.
- **Needs:** probably platform.approval (submit, review, confirm chain; the A8 running backlog), and possibly numbering and file. Workforce cannot use approval (§0.2).

### 1.3 Options
- **(a) Stay in workforce.**
  - Pros: no new module.
  - Cons:
    - It adds the most volatile HR zone to the most depended-upon business module, which already holds 16.6k lines and 190 tests.
    - It forces either bespoke single-level confirmation or a fourth kernel port.
    - It mixes rating access with roster access under one permission prefix.
    - It invites the state_transitions CHECK extension and personnel_commands FKs.
  - Each of §0.4 items 1-8 becomes a later split cost.
- **(b) With payroll.**
  - Pros: payroll is the main consumer.
  - Cons:
    - Payroll's drivers are tax and social-insurance law and its cadence is monthly closing.
    - Evaluators (all line managers) would need write access inside the salary context, which is the most sensitive data in HR. That is a privilege-design problem.
    - Probation evaluations feed regularization, not pay.
    - The bonus link itself is unproven (§1.1).
  - Wrong.
- **(c) Own BC** (name for one-glossary; for example `organization.performance`):
  - dependencies: `-> organization.workforce (api), platform.approval, platform.audit` (+ numbering/file if needed);
  - it publishes "evaluation confirmed" and payroll reads it by api;
  - for regularization, HR acts in workforce, or performance calls `PersonnelCommands` api; either direction is acyclic.

### 1.4 Recommendation
**Own bounded context.** "Performance results belong to organization.workforce" is not a stable long-term boundary:
- the change driver, actors, cadence, lifecycle and sensitivity all differ from the employee record;
- its only shared key is the person id;
- its natural workflow needs exactly the platform module workforce cannot depend on.

"Needs single-level approval" is neither the right reason nor the only one. It is a constraint caused by the placement, not by the domain. It would also be wrong on the evidence that A8 monthly evaluations are long-running multi-step instances [U]. Merging into payroll is worse than either.

If the user wants fewer modules, the acceptable fallback is a single downstream "talent" BC shared with recruitment (§2.1). Keeping it out of workforce matters more than how many modules there are.

**Cost of changing later if built in workforce:** §0.4 items 1-8. Specifically:
- an event class rename in `event_publication` (the "confirmed" event is subscribed by payroll, so in-flight publications matter);
- a permission code rename plus identity menu and role-grant data;
- rows in the shared append-only `state_transitions`;
- an audit subject kind owner;
- any FK from evaluation rows to `employees`/`assignments`;
- moving governed acceptance tests.

---

## 2. Q2 The other zones

### 2.1 Recruitment (requisition, candidate, interview, offer)
**Facts**
- [V] planned.yaml AGG-65 is "招聘需求": draft, submitted, approved/rejected, recruiting, closed. Approval is a single-level aggregate command, with the cycle reason quoted above.
- [V] AGG-62 (person) **absorbs the candidate stage**: "状态（含候选阶段、待入职）". Its commands are 登记候选人, 记录面试, 作出录用决定, 标记未录用, then 登记入职 and so on.
- [V] Today's employee root contradicts candidates-as-employees:
  - `employees.employee_code text NOT NULL` with a UNIQUE constraint; the code is issued at hire through kernel.GlobalDocumentNumberSource;
  - `employment_status CHECK IN ('EMPLOYED','SEPARATED')`;
  - `employee_versions` CHECKs tie status to phase and start date;
  - `api/EmploymentStatus` has only EMPLOYED and SEPARATED, and identity, approval and timesheet import it;
  - `api/EmployeeProfile` requires a non-null `orgUnitId`, which a candidate does not have;
  - identity consumes EmployeeHired to open accounts (`HireOpensAccountIT`).
- [V] A8 usage (templates):

| Template | total | last 365d | running | last use |
|---|---|---|---|---|
| 应聘登记表 | 2,360 | 170 | 58 | 2026-10-14 |
| 录用审批表 (process form) | 450 | 99 | — | 2026-10-10 |
| 用人申请表 | 211 | 38 | — | — |
| 员工入职 | 754 | 91 | — | — |
| 面试评估表 | 1,108 | 0 | — | 2021-12-06 (retired, replaced by 录用审批表 from 2021-12-07) |

- [V] `a8-forms.tsv`: 面试评估表 and 人才档案表 share main table formmain_0685 (2,052 rows). In legacy, the talent pool and interview are one store, which is evidence only. `b31p-r4-result.md:1079` shows a flow permission policy "招聘面试审批".
- **Data-hygiene finding [V]:** `docs/legacy/runtime-usage/a8-templates.tsv` contains A8 template names that embed what look like candidates' names, gender and age (rows for 应聘登记表 variants). That is row-level personal data in the repo (CLAUDE.md baseline 2). I did not reproduce them here. Recommend redacting that file.

**Test**
- Drivers: hiring demand and recruiting practice.
- Actors: hiring managers, recruiters and **external persons** (candidates).
- Lifecycle: pre-employment. It ends in a hire handoff or a rejection, and a rejected candidate's personal data should have a short retention. Workforce tables are PERMANENT or business-record retention.
- Consumers: workforce (the hire) and analytics.
- Needs: approval (requisition, offer), file (CVs), numbering.

**Options and recommendation:** **own BC** (or the shared "talent" BC from §1.4).
- **Do not** model candidates as AGG-62 persons. It would:
  - loosen `employee_code NOT NULL`, the status CHECKs and `EmployeeProfile.orgUnitId`;
  - leak a non-employee status into every consumer of EmploymentStatus/OrgDirectory (identity login release, approval approver resolution, timesheet);
  - mix candidate retention with permanent employee history.
- Handoff: on offer acceptance, recruitment enters a HIRE personnel command through the `PersonnelCommands` api. The edge recruitment → workforce is acyclic, and workforce's existing hire, numbering and account flow is untouched.

**Cost later if built per planned.yaml (candidates inside employees):** the highest of all zones.
- Purging candidates from append-only, versioned `employees`/`employee_versions`/`state_transitions` rows.
- Reverting the CHECKs and the api types imported by about 63 files in identity, approval, timesheet and numbering.
- Re-splitting events: 人员已登记 would have been published under workforce.
- Permission codes.
- **Decide now.**

### 2.2 Labour contract and employer entity
**Facts**
- [V] planned.yaml AGG-67 "劳动合同（用人单位口径）": probation, term; states draft, under approval, approved, signed and in force, terminated. INV-14 says the probation period may not exceed the legal limit. It references AGG-62 and AGG-60 (legal entity).
- [V] AGG-62 says "法人三口径分别记录". AGG-117 (built `LegalEntityAttribution`) is "数据范围法人". The payroll AGG-73/AGG-95 carry "扣缴义务人口径".
- [V] **Today there is one, unqualified "employee legal entity"**:
  - `api/EmployeeLegalEntities.on(employee, date)` (own attribution first, else up the department chain), table `employee_legal_entities`, event `EmployeeLegalEntityChanged`, command kind `CHANGE_LEGAL_ENTITY`;
  - it is used for data scope by identity (`platform/identity/application/LegalEntityOnDay.java`, `SessionCurrentUser`);
  - it is used by timesheet, which stamps it on a timesheet at submit (`cost/timesheet/application/SubmitTimesheet.java:89`);
  - so data-scope entity and the timesheet's entity are the same value today; [U] whether the timesheet use means "cost entity" was not checked.
- [V] `leave-rules.md` RULE-515 expects a workforce fact "a change of the employer entity" to drive leave entitlement lines. No such event or column exists.
- [V] A8: 劳动合同续签意向征询单 had 360 total, 42 in the last 365 days, 12 running, last use 2026-08-31. 合同审批单 had **0 rows** (form from 2020-04-09). **No labour-contract approval process is in use in A8.** The contract appears to be kept outside A8, and where is **missing** from the evidence.

**Test**
- Drivers: labour law and HR.
- Actors: HR, the same people who enter personnel commands.
- Lifecycle: inside the employment, and it gates it. Hire requires a contract, separation ends it, and contract probation relates to `employment_phase_kind = PROBATION` in `employee_versions`.
- Employer entity: a dated attribute of the employment that changes only with personnel events (hire, transfer between entities, re-signing).
- Consumers of the employer entity: payroll, social insurance, tax withholding and leave (RULE-515). They need it as **master data by date**, which is the same shape as `EmployeeLegalEntities`.
- Approval need: not evidenced (A8 shows none). Signing can use platform.signing, an acyclic edge since signing depends on nothing. Numbering is reachable through the existing kernel port.

**Recommendation:** **stay in workforce**, both the employer entity and the contract record. This is where my answer differs from §1 and §2.1: the zone shares the employee record's actor, driver and lifecycle, and there is no evidence that it needs platform.approval. Industry core-HR models also keep legal employer and contract in the employment record [U: external knowledge, not repo evidence].

Two conditions, which are cheap now and expensive later:
1. **Qualify the existing legal-entity names before any employer dimension or downstream subscriber exists.**
   - `EmployeeLegalEntities`, `employee_legal_entities`, `EmployeeLegalEntityChanged` and `CHANGE_LEGAL_ENTITY` currently mean the data-scope (and timesheet) entity.
   - Add the employer entity as a separate dated dimension with its own api query and its own event, for leave RULE-515 and payroll.
   - Do not overload `EmployeeLegalEntityChanged`. If RULE-515 or payroll subscribe to the existing event, the three meanings get welded together, and renaming a persisted event class later hits §0.4 item 1.
2. Keep contract approval out unless the business shows a need. If multi-level approval becomes real, move only the contract **document workflow** into a downstream BC. That BC enters CHANGE_EMPLOYER-type personnel commands, and the employer entity stays in workforce.

**Cost later:**
- Low, if done as above.
- High, if the employer entity is built in payroll or in a contract BC first and workforce then needs it (cycle), or if it is merged into the data-scope attribution (a rename of the persisted event, api type and table, with identity and timesheet as consumers).

### 2.3 Practice certificates and skills
**Facts**
- [V] planned.yaml AGG-66 "注册执业证书": continuing-education hours per registration period, renewal, expiry, and the event 执业证书已到期. Its invariant note says "被 INV-47 读取".
- [V] INV-47 is "签发人须具备约定资格", owned by AGG-27 (deliverable) in `delivery.acceptance` (`rules.yaml:484`). The consumer is the delivery sign-off.
- [V] No A8 form or template for certificates, continuing education or skills. 外训申请表 had 2 and 员工外训反馈表 3 instances, last used 2022.
- [V] `docs/data/positions/job-mapping.md:15` excludes 注册执业证书 from the position mapping.
- [V] A discovery query exists (`docs/data/evidence/dba-handoff/dat1020-discover.sql`) for certificate and registration columns in A8 formmain_0307 (员工档案表, 1,251 rows). **Its result is not in the repo, so where certificates live today and in what volume is missing.**
- Skills: **no evidence at all**: no aggregate in planned.yaml, no A8 form, no consumer.

**Test**
- Drivers: professional regulators (registration periods, continuing-education rules), not personnel events.
- Lifecycle: belongs to the person. [U] The registration is attached to a registered unit, which is the "registered entity" (a further legal-entity meaning), and it can transfer between firms. It does not follow the employment one-to-one.
- Actors: the employee reports, and a qualification administrator verifies.
- Consumers: delivery.acceptance (an issuer-qualification check by date), probably bids and staffing [U].
- Approval: none evidenced.

**Recommendation:**
- **Certificates: workforce is acceptable.** It is a low-volume register attached to the person, has no approval need, and has one narrow read consumer. Guardrails:
  - its own tables and its own api interface (a qualification query by person, type and date), nothing added to EmployeeProfile/OrgDirectory;
  - the registered entity as its own column referencing `legal_entities`, not reusing attribution;
  - no FK to `personnel_commands`;
  - not written into `state_transitions`;
  - a permission resource of its own.
  - With these guardrails a later move into a "qualification" BC is cheap: a rename of permission codes, events and the audit kind. Without them it costs as much as §0.4.
- Do not build it until the dat1020 result shows where the data is.
- **Skills: not workforce, and not now.** A skills taxonomy is driven by delivery and staffing, not HR master data. Build it with its consumer (resource planning) when one exists.

### 2.4 Leave and overtime (organization.leave), payroll (organization.payroll): consistency check
- [V] organization.leave is planned (contexts.yaml, status planned, no code). The seam is already right: unpaid leave is a workforce personnel command (`UNPAID_LEAVE_START/END`, `employee_versions.is_on_unpaid_leave`), and leave consumes workforce facts (RULE-515) and uses approval (RULE-517). This is the pattern §1 and §2.1 should copy: a downstream BC that depends on workforce and approval.
- [V] organization.payroll is planned and consumes AGG-62, AGG-76, AGG-93 and others. It should read performance and the employer entity by api, and own neither.

---

## 3. Verdict table

| Zone | Change drivers / actors vs employee record | Needs approval (blocked in workforce) | Recommendation | Later-split cost if built in workforce |
|---|---|---|---|---|
| Performance | different (management policy, evaluators, monthly) | likely | **own BC** (or shared talent BC); not payroll | high (§0.4 1-8) |
| Recruitment | different (external candidates, pre-employment, short retention) | yes (offer, requisition) | **own BC**; never candidates in `employees` | highest (CHECKs, api types, permanent history) |
| Labour contract + employer entity | same (HR, labour law, inside employment) | not evidenced | **workforce**; qualify legal-entity names now; separate employer dimension and event | low if qualified now, high if overloaded |
| Certificates | different driver, narrow consumer | no | workforce with guardrails; build after data evidence | moderate, low with guardrails |
| Skills | delivery/staffing | — | not workforce; defer to consumer | n/a |
| Leave | — | yes | own BC (as planned) | — |

## 4. Summary (10 lines)
1. Nothing for recruitment, contract, performance, certificates or skills exists in code yet [V]. The boundary decision is free now and only gets dearer.
2. workforce sits below approval, identity and numbering [V]. Every zone placed in it loses platform.approval. "Single-level approval" is a result of that placement, not a reason to merge or split.
3. Performance: different drivers, actors, cadence (forms changed yearly in A8 [V]), lifecycle and sensitivity. Its own BC; not payroll. The bonus link is a label only [U].
4. Recruitment: its own BC. Putting candidates into AGG-62 conflicts with employees.employee_code NOT NULL, the EMPLOYED/SEPARATED CHECKs and EmployeeProfile.orgUnitId [V].
5. Labour contract and employer entity: stay in workforce. Same actors and lifecycle; A8 has no contract approval in use (合同审批单 0 rows [V]).
6. Before payroll or leave subscribe, qualify the unqualified EmployeeLegalEntities/EmployeeLegalEntityChanged (today used for data scope and timesheet [V]) and add a separate employer dimension and event.
7. Certificates: workforce is acceptable as a narrow register with guardrails. Usage evidence is missing (the dat1020 result is not in the repo). Skills: not workforce; defer.
8. "Split later = move packages and tables" is false. Persisted event class names, module-prefixed permission codes in identity data, the shared append-only state_transitions CHECK, FKs to personnel_commands and employees, audit subject-kind owners, shared api types and governed acceptance tests all have to change [V].
9. Today's encapsulation is clean (no cross-schema FKs, no foreign readers, thin events [V]). Keep it that way by putting workflow zones downstream, as organization.administration and the planned organization.leave do.
10. Side finding: a8-templates.tsv contains what look like candidates' names, gender and age in template names. Personal data in the repo; it should be redacted.
