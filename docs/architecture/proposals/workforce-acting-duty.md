# Workforce: acting duty (someone temporarily takes another post's duties) — design (#1525)

- Status: proposal for review. Nothing here is decided for the user; section 9 lists what only the user can answer.
- Origin: set-A S2 node LIFE-025 had no model. Lead decided (#1519 Q1, overnight authorization, pending user review) that acting duty is a dated workforce fact and identity subscribes to it. This doc designs on that decision; it does not reopen it.
- Evidence tags: [V] = checked in this repository on 2026-11-06; [U] = unverified, to be checked by the implementing card; [P] = golden pack R002 node LIFE-025 (outside the repo, evidence only, its words and structure not adopted).

## 1. The one thing to decide

How workforce records "person A acts for post P from date X to date Y", how it is started, ended early and extended, and what identity hears, so that a dev card can follow without further design.

## 2. Verified facts

1. Personnel changes are commands with a lifecycle `PENDING` → `EFFECTIVE` / `WITHDRAWN`; kinds are an enum with a table CHECK (`PersonnelCommandKind`: HIRE … UNPAID_LEAVE_START, UNPAID_LEAVE_END, START_CONCURRENT, END_CONCURRENT) [V: `PersonnelCommandKind.java`, baseline `ck_personnel_commands_command_kind`].
2. `PersonnelCommandDueJob` runs daily at 00:05 business time and applies future-dated pending commands, one transaction per command, failed ones retried next run [V: `PersonnelCommandDueJob.java`]. A new job is therefore not needed to end a duty on its date (section 4).
3. A person has one primary assignment and any number of concurrent assignments (兼职); both are dated, same-assignment versions may not overlap, different assignments may [V: `V20261103130000__organization_workforce_employee_assignments.sql`]. A concurrent assignment is the person's own standing post. An acting duty is not that: the person keeps their own posts and covers another's for a term [P: "代理有独立期限；不是第二项重叠主职"].
4. Date columns use `effective_from_date` inclusive and `effective_to_date` exclusive, nullable for open-ended [V: baseline comments `失效日（不含）`].
5. Identity follows ADR-0032: sensitive permissions are granted to people, never by post; a transfer opens a "pending permission adjustment" item for a human and grants or revokes nothing itself [V: ADR-0032 items 1 and 4; `HandleEmployeeTransferred.java`]. Position-based granting (ADR-0029) is shelved [V: ADR-0029 status 搁置].
6. Identity role grants today have no scheduled end: a grant is current while `effective_to_at is null`, and a set end requires a revocation cause and audit entry [V: `account_role_grants` checks `ck_account_role_grants_revocation`; `Validity.CURRENT`]. There is a precedent for an event opening a grant: REINSTATE (`handled_personnel_commands`, outcome `GRANT_OPENED`) [V].
7. Approval routing resolves candidates from org-unit heads and the reporting line and skips separated or unpaid-leave people on the business day [V: `AssigneeResolver`]. Its constructor and `resolve` take no substitute source [V: first 70 lines read; U: no other lookup of one elsewhere in approval].
8. Event contracts live in `docs/architecture/events/workforce.yaml`; consumers subscribe with `@ApplicationModuleListener` and dedupe by event id [V].

## 3. Options (how to hold the fact)

| | Option | For | Against |
|---|---|---|---|
| A | A third assignment kind `ACTING` in `employee_assignments` / `assignment_versions` | reuses dated versions and overlap constraints | versions carry reporting line, fee-earner flag and are read as "where the person works"; acting would leak into roster, org-unit headcount and legal-entity derivation; needs "for whom" and reason columns the table has no place for |
| B | Own table `acting_duties` (one row per duty) plus three personnel command kinds | acting never mixes with assignments; reuses command lifecycle, withdraw, audit, due job; mirrors UNPAID_LEAVE_START/END | one new table and three enum values |
| C | Identity-only delegation (rejected by lead, #1519 Q1) | no workforce change | not employment history, no audit as one; listed only so the record shows it was weighed |

**Choice: B.** Option A is cheaper in tables but wrong in meaning: every reader of assignments would have to learn to exclude acting rows.

## 4. The model (option B)

**Acting duty** (行内: 代理职务) — fields:

| Field | Meaning |
|---|---|
| `acting_duty_id` | UUIDv7 |
| `employee_id` | who acts (the stand-in) |
| `covered_org_unit_id` | required: the org unit whose duties are taken |
| `covered_position_id` | optional: a post in that unit; empty = the unit head's duties |
| `covered_employee_id` | optional: the person being covered (leave, travel); informational and the basis for copying permissions if the user chooses so (Q-A1) |
| `starts_on` | first day, inclusive |
| `ends_before` | day after the last day, exclusive (screens show the last day = `ends_before` − 1, in the same words as other dated facts) |
| `reason` | free text, required, same limits as other command reasons |
| `ended_on` | empty while open; set when ended early or expired (the first day it no longer holds) |
| `end_cause` | `EXPIRED` / `ENDED_EARLY` / `SEPARATION`; empty while open |

State is derived, not stored: **SCHEDULED** (today < `starts_on`), **ACTIVE** (`starts_on` ≤ today < effective end), **ENDED** (today ≥ effective end). The effective end is `ended_on` if set, else `ends_before`. A duty withdrawn before it starts keeps its command row as `WITHDRAWN` and never gets a duty row (the row is written when the start command becomes effective, like other facts).

**Commands** (new `PersonnelCommandKind` values; same lifecycle, same withdraw, same audit):

| Command | Effective date means | Creates / changes | Event on effect |
|---|---|---|---|
| `ACTING_START` | `starts_on` | writes the duty row; at the same time schedules one system `ACTING_END` command due on `ends_before` (pending) | `EmployeeActingDutyStarted` |
| `ACTING_END` (by a person) | first day the duty no longer holds | sets `ended_on`, `end_cause = ENDED_EARLY`; withdraws the scheduled expiry command | `EmployeeActingDutyEnded` |
| `ACTING_EXTEND` | not a date; carries `new_ends_before` | moves `ends_before`; withdraws the scheduled expiry command and schedules a new one | none (identity holds no end date; see section 6) |

Expiry reuses `PersonnelCommandDueJob`: the scheduled `ACTING_END` is created by the system actor when the duty starts and is applied by the existing daily job with `end_cause = EXPIRED`. No new job. [U: confirm a system-created command is allowed by `created_by` and the audit rules; if not, the due job gets one extra query instead.]

**Rules** (all technical or integrity rules; none is a business rule):

1. Period edges: `starts_on` < `ends_before`; one-day duty is valid (`ends_before = starts_on + 1`). Two duties of the same person for the same covered post may be back to back (`[)` ranges touch without overlap).
2. Same post twice: for one `employee_id` and the same (`covered_org_unit_id`, `covered_position_id`), periods may not overlap (exclusion constraint on `daterange(starts_on, effective end)` for open rows). To lengthen a duty, use `ACTING_EXTEND`; a second overlapping `ACTING_START` is refused.
3. Overlap with the person's own assignment: acting is not an assignment, so it does not conflict with the person's primary or concurrent assignment. One refusal: a duty whose covered (unit, post) equals one of the person's own assignments on any day of the term is refused as a no-op.
4. Acting for oneself: `covered_employee_id` equal to `employee_id` is refused.
5. Several people may act for the same post at once. The data allows it; whether the business wants only one stand-in at a time is Q-A3.
6. Separation of the acting person: the separation command ends every open or scheduled duty of that person on the separation date (`end_cause = SEPARATION`) in the same transaction; scheduled duties not yet started are withdrawn. Separation or transfer of the covered person does not change the duty.
7. Past start dates: the same floor as other ordinary personnel commands applies [U: take it from `PersonnelCommandService` as UNPAID_LEAVE_START does, not defined here].
8. `ACTING_END` on or before `starts_on` of a started duty is refused; withdraw the start command instead if it is still pending.
9. Unpaid leave of the stand-in during the term does not end the duty; consumers already skip unpaid-leave people on their own (fact 7).

**Tables** (shapes only; the DDL is drafted in the schema PR under `docs/data/data-design-gate.md`):

- New `organization_workforce.acting_duties`: columns above, `created_at`/`created_by`, `source_personnel_command_id` (FK to the start command); exclusion constraint of rule 2; CHECK `starts_on < ends_before`; CHECK `(ended_on IS NULL) = (end_cause IS NULL)`; grain: one row per duty; append-only except the closing columns (`ended_on`, `end_cause`, `ends_before`), each change tied to a command id — [U: confirm against workforce's append-only triggers; if rewrites are refused, close by inserting a version row instead and keep the same fields].
- `personnel_commands`: add nullable `acting_covered_org_unit_id`, `acting_covered_position_id`, `acting_covered_employee_id`, `acting_ends_before`, `target_acting_duty_id`, `reason` if the table has none; extend the command-kind CHECK; per-kind CHECKs say which are required.
- Data-access registry and classification entries for the new columns (personal link: employee ids only, no names).
- No identity table change in this slice; identity's change is in section 6.

## 5. How it shows on employee detail

A section "代理职务" (name subject to the glossary check, #1516 H1) on the employee detail page beneath assignments, listing the person's duties newest first: covered unit and post, covered person (name only if the reader may see it), term as first day and last day, state (scheduled / active / ended with cause), reason; row actions "提前结束" and "延期" for the personnel-command permission holders on SCHEDULED and ACTIVE rows. The same person's detail also shows, on the covered person's page, a read-only line "currently covered by …" when a duty names them. The personnel-command list and detail pages show the three new kinds like the existing ones. Rosters and org-unit headcount do not change.

## 6. What identity hears

Events (payloads go to `docs/architecture/events/workforce.yaml` in the schema slice; the card forbids touching the yaml here):

| Event | When | Payload (ids and dates only) | Consumers |
|---|---|---|---|
| `EmployeeActingDutyStarted` | `ACTING_START` takes effect | `personnelCommandId`, `employeeId`, `actingDutyId`, `coveredOrgUnitId`, `coveredPositionId?`, `coveredEmployeeId?`, `startsOn`, `endsBefore`, `employmentRelationshipId` | platform.identity, platform.audit |
| `EmployeeActingDutyEnded` | duty ends by any cause | the same ids, `endedOn`, `endCause` (`EXPIRED` / `ENDED_EARLY` / `SEPARATION`) | platform.identity, platform.audit |

Envelope `businessDate` = `startsOn` / `endedOn`; `aggregateId` = employee; legal entity as in the existing events.

**Identity's response is the one place this design touches a decision that is not mine** (fact 5: sensitive rights go to people, and fact 6: grants have no scheduled end). The technical frame, whichever answer the user gives to Q-A1:

1. On `Started`, identity records the event as handled (by event id) and acts per the user's choice in Q-A1.
2. On `Ended`, identity closes whatever the duty opened: revokes grants it opened with a new revocation cause `ACTING_DUTY_END` and a new grant channel `ACTING_DUTY` (CHECK values added in an identity schema PR), and closes open review items. Because the end arrives as an event on the real end day (the due job applies it), grants need no scheduled end date. This is why `ACTING_EXTEND` publishes nothing: identity never held an end date.
3. A grant opened by an acting duty never outlives its duty; separation of the person already revokes all grants (existing cause `SEPARATION`), and the `SEPARATION` end cause only closes duty rows.
4. Sensitive roles are never copied or auto-granted by this path (ADR-0032 kept as is); at most identity opens a review item for the people who administer permissions.

Late job risk: if the due job is late, a grant outlives its duty by the delay. The job is retried daily with a manual run-once door like other cycle jobs (#1519 section 5); a monitor on "open acting duty past its end" is the safety net [U: monitor design belongs to ops, not here].

## 7. Implementation slicing (one behaviour each; file scope per PR)

Sessions per `policy.json`: org = one-dev-org, identity = one-dev-identity, test = one-test, frontend = the session that owns `frontend/apps/web/src/modules/workforce`. [U: confirm the frontend session's name with lead.]

| # | PR | Scope | Owner | Needs |
|---|---|---|---|---|
| 1 | Schema and event contract: `acting_duties`, command columns, kind CHECK, yaml entries for both events, registry rows | `backend/src/main/resources/db/migration/organization_workforce/`, `docs/architecture/events/workforce.yaml`, `docs/data/` registry files | one-dev-org | data-design-gate signature; user answers not needed |
| 2 | Acceptance tests for slices 3 to 6 | `backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/` | one-test | slice 1 merged |
| 3 | `ACTING_START` + `Started` event + scheduled expiry command | `organization/workforce/{api,application,domain,infrastructure}` | one-dev-org | 1, 2 |
| 4 | `ACTING_END` (early) + `Ended` event | same | one-dev-org | 3 |
| 5 | `ACTING_EXTEND` | same | one-dev-org | 3 |
| 6 | Separation closes duties (`SEPARATION` cause) | `PersonnelCommandService`, separation effects | one-dev-org | 4 |
| 7 | Read API: duties of a person, duties covering a person | `workforce/api` | one-dev-org | 3 |
| 8 | Employee detail section and three command forms | `frontend/apps/web/src/modules/workforce/` | frontend | 7 |
| 9 | Identity schema: grant channel `ACTING_DUTY`, revocation cause `ACTING_DUTY_END`, review item type | `db/migration/platform_identity/` | one-dev-identity | user's answer to Q-A1 |
| 10 | Identity handles `Started` (per Q-A1) and `Ended` (revoke, close items) | `platform/identity/application/` | one-dev-identity | 3, 4, 9 |
| 11 | Approval routing sees the substitute (only if Q-A2 says yes) | `platform/approval/domain/AssigneeResolver` + facade | approval owner | 7, Q-A2 |

Slices 1 to 8 need no user answer. Slices 9 to 11 wait for Q-A1 and Q-A2.

## 8. Done-when (for the test engineer; one checkable line each)

1. Starting a duty with `starts_on` in the future leaves state SCHEDULED, writes no duty row until the date, and publishes nothing until then.
2. On `starts_on` the daily job writes the duty row, publishes `EmployeeActingDutyStarted` once (redelivery does not duplicate), and creates exactly one pending expiry command due on `ends_before`.
3. A duty with `starts_on` ≥ `ends_before` is refused; a one-day duty is accepted.
4. Two overlapping duties for the same person and same covered post: the second start is refused; back-to-back duties are accepted.
5. A duty covering the person's own current assignment (same unit and post) is refused; a duty covering another unit is accepted while the person keeps both assignments unchanged.
6. `covered_employee_id` equal to the acting person is refused.
7. `ACTING_END` on an active duty sets `ended_on`, `end_cause = ENDED_EARLY`, withdraws the expiry command and publishes `Ended` once; the same command on `starts_on` or earlier is refused.
8. `ACTING_EXTEND` moves `ends_before`, replaces the expiry command (old one `WITHDRAWN`, one new pending), publishes no event; extending an ENDED duty is refused.
9. At `ends_before` the due job closes the duty with `EXPIRED` and publishes `Ended`; running the job twice the same day changes nothing the second time.
10. Separating the person ends open duties on the separation date with `SEPARATION` and withdraws scheduled ones, in the same transaction as the separation.
11. Employee detail shows state, term (first and last day), cause and reason; the covered person's page shows the cover line; roster and headcount counts are unchanged by a duty.
12. After Q-A1 is answered: `Ended` leaves no grant opened by the duty current, and no review item open for it; sensitive roles are never granted by the path.

## 9. Questions only the user can answer (none decided here)

| # | Question | Options | Recommendation |
|---|---|---|---|
| Q-A1 | What does a stand-in get while acting? | (a) nothing automatic: identity opens a review item for the permission administrators, who grant through the normal flow, and the item closes at the end; (b) identity copies the covered person's non-sensitive roles for the term, sensitive roles still go through the review item; (c) data scope only: the stand-in sees the covered unit's data as its head would, no roles | (a) — least change from ADR-0032, cannot over-grant; (b) once the business confirms the covered person's non-sensitive roles are safe to lend. [security/permission rule: the user's] |
| Q-A2 | Do approvals route to the stand-in while a duty is active? | yes: the stand-in becomes a candidate wherever the covered unit head or post is resolved; no: approvals keep their current candidates and the stand-in is added by hand | no for now (slice 11 not built); decide when the first approval flow needs it, because "both candidates" and "stand-in instead of head" give different outcomes on reject/escalate |
| Q-A3 | May several people act for the same post at the same time? | yes (data allows it); no, one at a time per covered post | yes; add the limit only if the business names a case where two stand-ins cause harm |
| Q-A4 | Who may start, end and extend a duty? | the personnel-command permission holders as for transfers; additionally the covered person's supervisor | personnel-command permission holders, because the duty is employment history and audited like a transfer [U: confirm the permission code in `PersonnelCommandService`] |
| Q-A5 | Should a duty need an approval before it takes effect (the pack says "代理权限批准两周") [P]? | no, the command is the record; yes, through an approval process | no; approval of lending permissions is Q-A1's review item, which already has a human decision |

## 10. Out of scope

- Any change under `src/main`, tables or events in this PR (slice 1 does them).
- Legacy-system or pack structures and names; the pack is quoted only as evidence in section 2 and Q-A5.
- Delegation of a single approval task (approval's reassign and escalate stay as they are).
- Pay effects of acting (an acting allowance): not asked, not designed.
- The wording of screens beyond the section titles; glossary check under #1516 H1.
