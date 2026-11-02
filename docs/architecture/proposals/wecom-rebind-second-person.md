# WeCom user id rebind with a second person — design (#980)

- Card: #980, parent #738. Author: one-design, 2026-11-02. Base: origin/main.
- Basis: user intent `docs/decisions/2026-11-02-124650-one-lead-企业微信账号改绑防冒用-三道都加.md` (three guards: no change of one's own record; rebind or clear of a set WeCom user id on an account with a role beyond `employee` needs a second person; any change revokes the employee's sessions and browser identifiers; fill-in from empty stays one person). #959 (PR #974) shipped guard ② fail-closed as a refusal.
- Status: proposal. One business question (Q1, section 7) goes to the user through lead; the shape below holds for either answer.
- Data design: not signed in this note; the table in section 4 is signed on the impl card (`docs/data/data-design-gate.md` §1.3).

## 1. The one thing to decide

How a profile change that rebinds or clears a set WeCom user id on a privileged account waits for, receives or loses a second person's confirmation, replacing today's refusal `ORGANIZATION_WORKFORCE_WECOM_USER_ID_CHANGE_NOT_ALLOWED`. The rules are the user's; this note gives the shape.

## 2. Facts (verified on origin/main 76da4744a)

| Fact | Source |
|---|---|
| The profile change is one use case, `EmployeeProfileService.change`, behind `POST /api/v1/organization/employees/{employeeId}:change-profile`, permission `organization.workforce:personnel-command:create` (人事专员) | `EmployeeProfileService.java`:33, :54-96; `PersonnelCommandService.java`:57; `EmployeeProfileController.java`:22 |
| It locks the employee row, compares the whole `PersonalProfile` (name, gender, email, WeCom user id), refuses one's own record, refuses a WeCom user id held by someone else, writes, revokes sessions when the WeCom id moved, audits `organization.workforce.employee-profile.changed` with before/after text — all in one transaction | same file :59-95 |
| "Privileged" is asked of identity through the kernel port `EmployeeAccountAccess.holdsOnlyEmployeeRole`; revocation through `EmployeeAccountAccess.revokeSessions` (sessions and browser identifiers) | `kernel/EmployeeAccountAccess.java`:14, :17; `EmployeeAccountAccessService.java`:34, :41 |
| I-12 two-person pattern in identity: a request row holds the whole proposed state and its base; a named CHECK forbids the requester (and the beneficiary) as confirmer; confirmation by another holder of the permission; a base that moved before applying voids the request (#938) | `V20261014130000__platform_identity_baseline.sql` `authorization_requests`; `docs/ops/security-invariants.md` I-12; ADR-0034 decision 2 item 5 |
| #959 review carry-overs: role check vs login is a narrow race; org sync still writes `wecom_user_id` without revocation (goes with #961); fill-in from empty stays single-person | #980 body |

## 3. Options and choice

| | A. Reuse identity `authorization_requests` (new kind) | B. A workforce request table in the I-12 shape (chosen) | C. `platform.approval` workflow |
|---|---|---|---|
| Who owns the data | identity would hold and apply a workforce field: identity → workforce write, a reverse dependency | workforce owns field, request and apply; identity only answers "privileged?" and revokes, as today | approval engine plus a callback into workforce |
| ③ revocation in the same transaction as the write | needs a cross-module write in identity's listener | yes: the confirm use case writes the profile and calls `revokeSessions` in one transaction, exactly as the one-person path does now | callback is after-commit; the write and revocation split from the decision |
| Cost | changes identity's table, CHECKs and events for one foreign field | one table, three use cases, two endpoints | process definition, task UI, callback for a one-step check |

Choice: **B**. It keeps the field and its guards in one module and one transaction (guard ③), and copies I-12's shape (whole proposed state, base, two-people CHECK, VOIDED on a moved base) rather than its table. A breaks module ownership; C is a multi-step engine for a single confirmation and splits ③ from the write.

**Whole profile, not the WeCom part only.** When a change needs a second person, the whole proposed profile waits (all four fields), and applies at once on confirmation. Splitting it (other fields now, WeCom later) gives a half-applied change and two audit entries for one edit; holding it whole mirrors I-12 `REPLACE_ROLES` (whole role set).

## 4. Shape (proposal for the impl card; signed there)

Table `organization_workforce.employee_profile_change_requests`, one row per request:

| Column | Type | Note |
|---|---|---|
| `employee_profile_change_request_id` | uuid PK | UUIDv7 from `IdGenerator` |
| `employee_id` | uuid NOT NULL | FK employees; subject |
| `requested_by` | uuid NOT NULL | user id of the requester |
| `requested_at` | timestamptz NOT NULL | |
| `base_display_name`, `base_gender`, `base_email`, `base_wecom_user_id` | as the profile columns | profile at request time (the base); `base_wecom_user_id` NOT NULL (only a set id needs a second person) |
| `display_name`, `gender`, `email`, `wecom_user_id` | as the profile columns | proposed profile; `wecom_user_id` NULL = clear |
| `request_status` | text NOT NULL | named CHECK `PENDING / CONFIRMED / REJECTED / VOIDED` |
| `decided_by`, `decided_at` | uuid, timestamptz | |
| `void_reason_kind` | text | `BASE_CHANGED / WECOM_USER_ID_TAKEN / EMPLOYEE_GONE`, only when VOIDED |
| `version`, `created_at`, `updated_at` | standard | |

Named constraints: `ck_..._two_people` (`decided_by IS NULL OR decided_by <> requested_by`); `ck_..._decision` (`decided_by`/`decided_at` set exactly when status ≠ PENDING); `ck_..._wecom_moves` (`wecom_user_id IS DISTINCT FROM base_wecom_user_id`); `ck_..._void_reason` (set exactly when VOIDED); partial unique index: one `PENDING` per `employee_id`. Retention as the audit-relevant workforce tables. No new event: nothing consumes one (the existing profile change publishes none).

State machine: `PENDING → CONFIRMED` (applied), `PENDING → REJECTED`, `PENDING → VOIDED`. No expiry (I-12 requests have none).

## 5. Behaviour

1. **Change** (`:change-profile`, unchanged path and permission). All existing checks run first (own record, lock, no-op, text, WeCom id taken). If the WeCom id moves from a set value (rebind or clear) and the account holds a role beyond `employee`: no write, no revocation; a `PENDING` request is stored and the call answers **202** with `{requestId}`; audit `organization.workforce.employee-profile.change-requested`. A second change while one is pending answers 409 `ORGANIZATION_WORKFORCE_PROFILE_CHANGE_PENDING`. Otherwise (no WeCom move, fill-in from empty, or employee-only account) the one-person path stays as today (200/204).
2. **Confirm** (`POST /api/v1/organization/employee-profile-change-requests/{id}:confirm`, same permission). Refused 403 `ORGANIZATION_WORKFORCE_PROFILE_CHANGE_CONFIRMER_NOT_ALLOWED` when the caller is the requester or the subject employee's own account. In one transaction: lock the employee, then the request; if the current profile ≠ base → `VOIDED BASE_CHANGED`; if the proposed WeCom id is now held by another employee → `VOIDED WECOM_USER_ID_TAKEN`; if the employee is gone → `VOIDED EMPLOYEE_GONE`; a voided request answers 409 with its reason and writes `...change-voided`. Otherwise write the profile, `revokeSessions` (③), status `CONFIRMED`, audit `organization.workforce.employee-profile.changed` naming both people (requester as actor, confirmer in detail) with before/after text.
3. **Reject** (`...:reject`, same permission, same confirmer rule, reason required): `REJECTED`, audit `...change-rejected`; profile and sessions untouched.
4. **List** (`GET /api/v1/organization/employee-profile-change-requests?status=PENDING`): cursor page, data scope of the caller's employee access (out of scope = not listed; reading one out of scope = 404, I-10).
5. **Privilege is judged at request time.** If the account gains a role while a one-person change is in flight, the narrow race of the #959 review stays; this card does not widen it. At confirmation there is already a second person, so no re-check.
6. **Sync path**: after #961 stops `OrgSnapshotSyncJob`, nothing else writes `wecom_user_id`; the impl card checks with `git grep` that no writer other than these use cases remains.

## 6. Done-when (for the independent test engineer)

1. Rebind of a set WeCom id on an account with a role beyond `employee` answers 202 with a request id; profile, sessions and browser identifiers unchanged; one PENDING request.
2. Clear (to empty) of a set WeCom id on such an account: same as 1.
3. Employee-only account, and fill-in from empty on any account: one person as today (applied at once, sessions revoked) — regression of #959.
4. A change that does not move the WeCom id on a privileged account applies at once without a request.
5. The requester cannot confirm or reject (403 with the confirmer code); the subject employee's own account cannot confirm (403); a third holder of the permission can.
6. Confirmation applies the whole proposed profile, revokes the subject's sessions and browser identifiers in the same transaction (a failure in revocation leaves the profile unchanged), and writes one changed entry naming both people.
7. Profile changed between request and confirmation (any of the four fields) → VOIDED `BASE_CHANGED`, nothing applied, sessions kept.
8. Proposed WeCom id taken by another employee before confirmation → VOIDED `WECOM_USER_ID_TAKEN`.
9. Reject needs a reason; afterwards nothing applied, sessions kept; the request cannot then be confirmed.
10. A second change while one is pending → 409 `..._PROFILE_CHANGE_PENDING`, no second row.
11. Escalation and scope: a caller without the permission gets 403 on all three endpoints; a request about an employee outside the caller's scope is not listed and its confirm answers 404; a caller who holds several roles is judged by user id, not role.
12. Schema: the named CHECKs and the one-PENDING-per-employee index refuse the bad rows.

## 7. Business question for the user (via lead)

**Q1. Who may be the second person?** (a) Any other holder of the personnel-command permission (人事专员) with the employee in scope — **recommended**: same people who make the change today, mirrors I-12 (any other administrator); (b) only a super administrator — stronger, but a super administrator is rarely online and the WeCom id is how people log in, so a lockout waits on them; (c) the person's line manager — knows the person, but does not hold the permission and is the person most easily asked by the requester. Either answer keeps the shape; only the confirmer check in 5.2 changes.

## 8. Out of scope

- Session and browser-identifier revocation logic itself (identity, unchanged).
- Stopping the org sync (#961).
- Fill-in from empty on a privileged account (user decision: one person).
- Any change to identity's `authorization_requests`.

## 9. Cards after this note

- Impl (one-dev-org): table, three use cases, two endpoints, error codes, audit registrations, OpenAPI; DD signed on that card before code.
- Acceptance (one-design test agent): section 6 lines, after the schema PR merges.
