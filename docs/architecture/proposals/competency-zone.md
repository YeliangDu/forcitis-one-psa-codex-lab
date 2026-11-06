# Competency zone: skills and practice certificates

Card #1309 (follow-up F5 of `hr-boundary-stability-review.md` section 8a). Spec only: no code, no table is created, no data design is signed here (`docs/data/data-design-gate.md` applies before any table). The placement decision is carried by the proposed ADR-0089.

Status: proposal, 2026-11-05, basis `origin/main` a4226f813. Claims are marked [V] checked in the file named, or [U] not checked.

## 1. What is decided

One thing: **where skills and practice certificates live, and which aggregates they are**, so the delivery side can staff by skill and sign deliverables by certificate (INV-47). The user said skills are built now and certificates with them (「技能现在做；执业证书听你建议」, #1295 comment 6638890281; lead: certificates are built with skills, B4). A second legal entity employs people to keep its enterprise qualification, so certificates matter now (card text).

## 2. Placement (ADR-0084 decision 3 test)

The test (`ADR-0084.md:43` [V]): workforce carries only a zone with the **same change driver, users, retention and transaction** as the employee master record; if any one differs, the zone is its own context. The consumers row below is context, not one of the four criteria.

| Test | Employee master record | This zone | Same? |
|---|---|---|---|
| Change driver | hire, transfer, separation (HR commands) | delivery staffing needs, regulator registration periods, continuing education | no |
| Users | HR operators, line managers | qualification administrator, project managers and resource planners, the signer of a deliverable, the seal ledger | no |
| Retention | personnel record rules | open (section 9, Q3): likely the same for certificates (Q3 a), possibly shorter for skills | not decided; not needed for the result |
| Transaction | one employee aggregate, `personnel_commands` | none of the zone's commands writes an employee (ADR-0084 condition 1 would hold) | the zone needs no workforce write transaction |
| Consumers | everything keyed by employee | `delivery.acceptance` (INV-47), `delivery.execution` resource plan, the seal ledger (ADR-0064), payroll later | different set |

Of the four criteria, change driver, users and transaction differ, so the test says: own context (one difference is enough). Options compared:

| Option | What | For | Against |
|---|---|---|---|
| A (recommended) | New context `organization.competency` (zh 人员能力), own schema, tables, api, events, permissions | passes the test; no dependency of workforce on approval or delivery; the delivery side reads one narrow api; renames and splits cost nothing because nothing exists yet | one more module (registry row, schema, permission prefix); needs the dependency edge `organization.competency -> organization.workforce (api)` |
| B | Workforce zone under the six split-ready conditions of ADR-0084 decision 3 | no new module | the zone fails the test; certificate registration and the certificate expiry job would live in the module that owns employee commands, and every later split pays the cost ADR-0084 background item (2) lists |
| C | Skills with their consumer (delivery), certificates in workforce | skills sit next to the resource plan | splits one competency zone the user and lead asked to build as one; the INV-47 read would cross two modules; delivery would own a person attribute |

Choice: **A**, and why: it is the only option that satisfies the test; its price (one module) is paid once while nothing is built, which is the cheapest moment (ADR-0084 background). Dependencies: `organization.competency -> organization.workforce (api, events)` only (reads employees and the by-date employment relationships; subscribes to `EmploymentRelationshipEnded` and the transfer event); `-> platform.audit`; **no** `platform.approval` unless Q2 in section 9 is answered "verified by a second person". `delivery.acceptance`, `delivery.execution`, `organization.payroll` depend on competency, never the reverse.

## 3. Aggregates

Names are ours; the glossary fragment `backend/glossary/proposed/competency-zone.yaml` proposes them (the external plan's names are not used). Existing approved terms are reused: `registered_practice_certificate`, `continuing_education_hours` (both bc `organization.workforce` today: the ADR moves their bc to `organization.competency`). "Qualification" is avoided on purpose: `qualification_certificate` is the **company** qualification (AGG-02, `masterdata.counterparty`, `objects.yaml:1209` [V]); "credential" means authentication in our glossary.

| # | Aggregate (zh) | Root and children | Notes |
|---|---|---|---|
| 1 | `Skill` 技能 (catalogue entry) | root: skill code, zh name, category, `ACTIVE`/`RETIRED` | the skill catalogue (G18): a closed list maintained by the administrator; a retired skill stays on holders |
| 2 | `PracticeCertificateKind` 注册执业证书种类 (catalogue entry) | root: kind code, zh name, regulator, category, whether continuing education applies, `ACTIVE`/`RETIRED` | the qualification catalogue (G19); the hours rule (required and elective per registration period) lives on the kind as dated data, not in code |
| 3 | `EmployeeSkill` 员工技能 | root: employee id, skill id, level, `DECLARED`/`CONFIRMED` (proposal, Q1) | person's skill (G20); one row per employee and skill |
| 4 | `RegisteredPracticeCertificate` 注册执业证书 (AGG-66) | root: employee id, kind id, certificate number, registered legal entity id, status; children: registration periods, continuing education hours entries, scan reference | person's qualification (G21) |

### 3.1 Practice certificate (AGG-66)

State machine taken as it already stands in `state-machines-raw.yaml:3149–3198` [V]: `VALID` (initial) → `EXPIRED` (terminal), `VALID` → `REVOKED` (terminal). No new state.

| Command | From → to | Guard |
|---|---|---|
| RegisterPracticeCertificate | none → `VALID` | the employee exists and has an in-service relationship on the registration date with the registered entity as employer (lead ruling, section 4); the relationship is not a dispatched one [U: whether workforce carries a dispatched employment type is not checked]; at most one `VALID` certificate per employee and kind (partial unique: one registered unit per qualification); kind is `ACTIVE`; first registration period valid; a late registration after separation is Q5; migrated certificates start `VALID` and are not subject to the employer guard |
| RenewPracticeCertificate | `VALID` → `VALID` | adds a registration period that starts the day after the current one ends; continuing education shortfall **warns, does not refuse** (V-14) |
| RecordContinuingEducationHours | `VALID` → `VALID` | append only per registration period; no rewriting |
| ChangeRegisteredEntity (new command; no counterpart in the plan) | `VALID` → `VALID` | same employer guard as registration; clears the "registration no longer usable" flag (section 4) |
| RevokePracticeCertificate | `VALID` → `REVOKED` | reason required |
| MarkPracticeCertificateExpired | `VALID` → `EXPIRED` | system command of the daily job: the last registration period ended before today and no later period exists |

INV-47 (`rules.yaml:484` [V]) is **read** where a deliverable is issued, not a guard of registration: the existing preconditions on registering (`closeout/people/commands.yaml:~1237` and guard INV-47 at `state-machines-raw.yaml:3168–3169` [V]) look misplaced [U]; the build card should move them to the acceptance side and not guard registration with INV-47.

Events (all ids only, no certificate number): `PracticeCertificateRegistered`, `PracticeCertificateRenewed`, `PracticeCertificateEntityChanged`, `PracticeCertificateExpired`, `PracticeCertificateRevoked`. The plan has only the expiry event, analytics only, `planned.yaml:988–999` [V]; ADR-0079 decision 4 names expiry and entity change for the seal ledger (`ADR-0079.md:128` [V]); ADR-0064:71 says the seal ledger subscribes once AGG-66 publishes [V]. Payloads go into the events catalogue under `docs/architecture/events/` with the build card, not here.

### 3.2 Skill and employee skill

`Skill` has two commands (create or amend, retire). `EmployeeSkill`: `DeclareSkill` (employee or manager), `ConfirmSkill`, `RemoveSkill`; the level is a closed list whose values wait for the legacy result and Q1 (section 9). No event: the resource plan **reads** (ADR-0079 decision 2, `:109` [V]: "reads, does not subscribe"). The assessment result of ADR-0079 C22 is the level plus the `CONFIRMED` state (who may confirm: Q1); there is no separate assessment workflow and no approval node (ADR-0079 out of scope: training and learning platforms).

## 4. Registered legal entity and the employment relationship

- A certificate records `registered_legal_entity_id` as its **own column referencing `legal_entities`** (no foreign key across modules, B4). It is not the data-scope attribution (`EmployeeLegalEntityChanged`, ADR-0073) and not by itself the employer (`hr-boundary-stability-review.md:103`, `hr-boundary-independent-answer.md:~255` [V]).
- **Lead ruling on record** (`docs/decisions/2026-11-02-163040-one-lead-U-14-选-c-注册单位按-5.md` [V], resting on the one-legal report `legal-basis-2026-11-02.md`, cited there; not re-read here): ADR-0079 invariant 15 is adjusted to five points. (1) The registered unit is the current employing legal entity (a re-hired retiree: the hiring entity). (2) Each qualification is registered with one unit only, and dispatched staff are not registered here. (3) From the day of separation or a transfer between legal entities, the old registration may not be used for sealing, and the seal service checks it. (4) A change or cancellation is prompted, with an internal deadline of 30 days. (5) A warning when the social-insurance-paying entity differs from the registered unit (not for a re-hired retiree). This spec **builds on those five points** and does not replace them; the ruling never reached an ADR, so ADR-0089 carries it (decision 3) and names invariant 15 in the supersession list.
- Mapping onto the design:
  1. **Employer guard** on `RegisterPracticeCertificate` and `ChangeRegisteredEntity` (point 1): the zone asks workforce for the by-date relationships (ADR-0086 decision 6 [V]); the registered entity must be the employer of an in-service relationship on that date. Dual employment is allowed (user B5): a person registers each qualification with either employer, but with only one.
  2. **One unit per qualification and no dispatched staff** (point 2): partial unique index on `(employee_id, kind_id) WHERE status = 'VALID'`, and the dispatched check in section 3.1.
  3. **Sealing after leaving** (point 3): the zone subscribes to `EmploymentRelationshipEnded` and the transfer event and sets a "registration no longer usable" flag with `flagged_on_date` = the event's effective date. The read `usableForSealing` (section 6) answers false for dates on or after the flag date until the administrator changes the entity or revokes the certificate. The flag changes nothing else; whether INV-47 sign-off uses the stricter read is Q6.
  4. **Prompt and 30 days** (point 4): the flag carries `due_on_date` = flag date + 30 days (the 30 is lead's ruling, kept as configuration data); the zone reminds the administrator and reports overdue ones; it never revokes by itself.
  5. **Social-insurance mismatch** (point 5): needs the payroll enrolment, which this zone must not read; the warning is built on the payroll side when payroll reads `certificatesOf`, so it waits for payroll and is not in this zone's done-when.

## 5. Expiry and its downstream obligations

| What | Rule |
|---|---|
| Valid on a date | status is not `REVOKED` and a registration period contains the date; read by date, never from the status alone, so a certificate that expired yesterday still answers correctly for an earlier sign date (ADR-0064:75 [V]) |
| Daily expiry job | marks `EXPIRED` the day after the last period ends and publishes `PracticeCertificateExpired`; idempotent per certificate; dates only, no time zones |
| Near-expiry reminder | a timed reminder to the administrator and the holder N days before the last period ends (P-02, `enterprise-aggregates-draft.md:283` [V]); N is configuration data, default proposed in Q4 |
| Delivery sign-off (INV-47) | `delivery.acceptance` asks `hasValidCertificate(employee, kind, date)` at issue time and refuses without it; the zone never blocks anything itself |
| Seal ledger | subscribes to expired / revoked / entity-changed (ADR-0079 decision 4); the subscription is built with the seal ledger |
| Separation of the holder | certificate change is closing work after separation, not a precondition (ADR-0079 decision 5 item 11, `:145` [V]); the ruling of section 4 adds the sealing block and the 30-day prompt |
| Allowances | **not decided here** ("do not decide allowance rules"): payroll may later subscribe to expiry/renewal; the SSOT has no allowance object [U] |
| Retention and data class | certificate number and scan are personal information; the retention class waits for the user (Q3); not signed here |

## 6. Reads the consumers need (api)

Four methods, kept narrow (G4 limit of 12 methods per module, `ArchitectureRulesTest.apiSurfaceStaysNarrow`); signatures are a proposal for the build card:

| Method | Consumer | Returns |
|---|---|---|
| `hasValidCertificate(employee, kind, date)` | `delivery.acceptance` (INV-47) | boolean plus the covering period end (status and period only) |
| `usableForSealing(employee, kind, date)` | the seal service (ruling point 3) | `hasValidCertificate` and not flagged on or before the date |
| `certificatesOf(employee, date)` | profile views, the seal ledger, payroll later (ruling point 5) | kinds, registered entity, period end, flag and due date |
| `employeesWithSkill(skill, minimumLevel)` | resource plan (AGG-25/AGG-26 have no skill field today, `planned.yaml:284–306` [V]) | employee ids only; availability stays the resource plan's join with workforce and leave reads |
| `skillsOf(employee)` | resource plan, profile views | skill ids and level |

The resource plan stores no copy; it joins at read time. Consumers outside the person's own data scope get only what their scope allows (data scope rules of the roster read).

## 7. Shape of the data (for the data design gate, not signed)

Schema `organization_competency`; tables `skills`, `practice_certificate_kinds`, `employee_skills`, `practice_certificates`, `practice_certificate_periods`, `continuing_education_entries`; the zone's own transition record (not the shared `state_transitions`, ADR-0084 condition 2) and own permission resource names (`organization.competency:skill:read|update`, `:employee-skill:read|update`, `:practice-certificate:read|create|update`, `:practice-certificate-kind:update`; action words from `standard_actions`, `update` for revoke and renewal as in `organization.attendance:locked-day:update`). The employee id is stored without a foreign key; the registered legal entity id the same. Periods and hours are append only; a period never overlaps another of the same certificate (exclusion constraint on a `daterange`, the pattern of the contract sign-off). The gate sign-off is a separate record (`docs/data/reviews/`) before the build; this spec defers every column and constraint to it.

## 8. What waits for the legacy result

`dat1020-discover.sql` was run on 2026-10-20 (`docs/ops/legacy-read/…DAT-1020-COO-01.md` [V]) but its result was never committed, and it did not look for skills; ADR-0064:27 records only "the old system has no practice-certificate field" [V]. Review of the five `dat1020-*` scripts: none reads row values; `dat1020-discover2.sql` line 6 prints the top 60 certificate names without small-cell suppression, so it must **not** be rerun. Step 1 for the DBA is `docs/data/evidence/dba-handoff/dat1020-cert-skill-step1.sql` (new here, a manual DBA run in the style of `dat1020-discover.sql`, not `forensic_read.py`; metadata and whole-table row counts only; the small-cell rule `< 3` starts with the per-person counts of step 2; run with the read-only evidence account). Known already: the legacy PSA has `psa_pm_user_skill_tags` with 4,420 rows and a skill-tag API with 126 calls (`docs/legacy/runtime-usage/prod-usage.tsv:1349, 38, 381` [V]); the A8 has `HR_STAFF_CERTIFICATE` and `formmain_0307` fields about titles and majors, and external-training forms last used in 2022.

| Part of this spec | Waits for |
|---|---|
| Skill level list and whether levels exist at all | step 1 (tag columns) and Q1 |
| Seed of the skill catalogue | distinct tag count and tags per person; whether to import the legacy tags is a migration decision after the result |
| Certificate kind list and the hours rule per kind | distinct kinds with 3 or more holders; fill rate of the expiry and issue dates |
| Registered entity | whether a registered-unit column exists in `HR_STAFF_CERTIFICATE`; otherwise holders by A8 account (step 2) |
| Attachments (scan reference) | step 2 attachment counts |
| Migration mapping | not designed here; follows the result and the migration gate |

Step 2 (fill rates and year buckets per column, holders, per-person histogram, registered-unit match, attachment counts, skill-tag counts) is written as static queries when step 1 returns the real column names (the read-only runner refuses `EXEC`).

## 9. Questions for lead (business; options and recommendation)

| # | Question | Options | Recommendation |
|---|---|---|---|
| Q1 | Who may put a skill on a person, and is there a level? | (a) the person declares, the line manager confirms, levels 1 to 3; (b) the administrator only; (c) tag only, no level (what the legacy tags suggest [U]) | (a) states `DECLARED`/`CONFIRMED`, resource plan filters on `CONFIRMED`; decide the level list after the legacy result |
| Q2 | Is a certificate checked by a second person before INV-47 accepts it? | (a) the qualification administrator registers it from the scan, no approval (as the existing command list [V]); (b) a verification step through `platform.approval` | (a): INV-47 is a safety net at sign-off and the administrator is the controlled role; (b) adds a dependency and a state for no stated demand |
| Q3 | Retention of certificate and skill data | (a) as the personnel record (ten years after the last separation, the P-9 a pattern); (b) shorter for skills | (a) for certificates, (b) skills with the person's record; the user rules (ADR-0043 R-2) |
| Q4 | Days of warning before a registration period ends | 30, 60, 90 | 90, as configuration data |
| Q5 | Registering a certificate for a person already separated | (a) allowed to correct history, for a bounded time; (b) refused | (a) with the bound set at build time; low stakes |
| Q6 | Does INV-47 delivery sign-off also refuse a certificate whose holder has left the registered entity (the ruling states sealing only)? | (a) yes, use `usableForSealing`; (b) no, `hasValidCertificate` | (a): same legal reason, one rule; lead decides because the ruling is lead's |

None of these blocks the ADR, which decides placement, names and the supersessions. Q1, Q2, Q4 and Q5 are business rules that lead routes to the user; Q3 is the user's (ADR-0043 R-2); Q6 is within lead's own ruling; the allowance question comes with the payroll design.

## 10. Done-when (for the build cards, each checkable)

1. `contexts.yaml` registers `organization.competency` (planned) and `module-dependencies.txt` has only `-> organization.workforce` (api, events) and `-> platform.audit`; `ModuleDependencyTest` red if a delivery or payroll class is imported from it.
2. The zone's tables keep the isolation the six ADR-0084 conditions describe (the zone is its own context, so they are not binding as such): no foreign key to `employees` or `personnel_commands`, no shared `state_transitions` write, own permission resources, own api types, own event classes, own retention.
3. A certificate is valid on a date exactly when it is not `REVOKED` and a period contains the date: tests for the day before the first period, the last day of a period, the day after, a renewed chain without gap, and a gap.
4. The daily job expires a certificate once, the day after its last period, and re-running it publishes nothing more.
5. `hasValidCertificate` for a sign date earlier than an expiry still answers true after the expiry (the read is by date).
6. Registering or changing to an entity that is not the employer of an in-service relationship on the date is refused; a second `VALID` certificate of the same kind for one person is refused; ending the relationship with the registered entity (or a transfer) sets the flag with a 30-day due date, `usableForSealing` is false from the flag date and `hasValidCertificate` is unchanged; neither read changes the certificate.
7. No event or log line holds a certificate number or scan reference.
8. The api surface of `organization.competency` stays within G4 (limit 12 methods that have a caller outside the module).
9. The data design sign-off record exists and cites the retention answer (Q3) before any migration.

## 11. Out of scope

Code and tables; allowance rules; training courses and learning platforms (hours are recorded only); the company qualification (AGG-02); migration mapping of legacy certificates and tags; an approval flow for certificates (Q2 (a)); performance assessment (a separate context, ADR-0084).
