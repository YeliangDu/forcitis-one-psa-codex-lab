# Identity: single operator mode, authorization history, emergency login, sessions and tickets business rules — RULE-437..RULE-459

Scope: `platform.identity` (Phase 1a). Rules of FORCITIS one, written from the behaviour the existing tests assert and from the documents named per card. Basis: ADR-0030 (single operator mode in non-production) with its work card `phase1a/cards/SINGLE-OP.md`; ADR-0034 (authorization history, role and grant versions) with DATA_DESIGN_CHECKLIST §14.1 D17; ADR-0027 section 1 (emergency admin) as amended by ADR-0028 and ADR-0033 section 3 item 7 (U-4); `docs/ops/security-invariants.md` I-11, I-12, I-13; `docs/business/processes/login-and-session.md` (T5, T7, T8, T9, D-1a-41, D-1a-45); RULE_REVIEWS conclusions of RULE-332 and RULE-349. No legacy code is used as evidence. Existing legacy-behaviour cards (RULE-316, RULE-332, RULE-349) describe the old system and are cited only through their review conclusions; none of them covers the behaviour below.

| ID | Domain | Title | Priority | Review |
|---|---|---|---|---|
| RULE-437 | identity | In single operator mode an operations command may leave out the witness; the omission is marked in the same transaction as the change | P0 | No |
| RULE-438 | identity | The omission mark is written once per real change; a command that changes nothing writes neither a mark nor a normal audit entry | P1 | No |
| RULE-439 | identity | The operator is always required and must be a known employee, in single operator mode too | P0 | No |
| RULE-440 | identity | A witness that is given must be a valid employee identifier different from the operator; an invalid witness is never read as an omission | P0 | Yes |
| RULE-441 | identity | With the mode off the witness is required for every operations command; with a valid witness the mode on behaves exactly as the mode off | P0 | No |
| RULE-442 | identity | Single operator mode relaxes none of the in-application second-person rules, nor the database constraint behind them | P0 | No |
| RULE-443 | identity | Role version and grant history is never deleted, and a closed row never changes | P0 | No |
| RULE-444 | identity | An open role version or grant can be closed once, by writing the closing fields only, and a revocation always carries its cause and audit entry | P0 | No |
| RULE-445 | identity | A role without a current version cannot be granted | P1 | No |
| RULE-446 | identity | Role version periods are finite, not inverted and never overlap for one role; a role name is unique at each moment | P1 | No |
| RULE-447 | identity | Grant periods are finite and not inverted; a person holds a role at most once at any moment | P0 | No |
| RULE-448 | identity | Emergency login is accepted only from the internal network, judged before any credential is looked at | P0 | No |
| RULE-449 | identity | An emergency one-time code works once | P0 | No |
| RULE-450 | identity | An emergency account works only for a bound person who is enabled, holds super_admin now, and is not separated | P0 | No |
| RULE-451 | identity | An emergency login starts in administrator mode for one hour, audited and alerted, and the hour is not extended | P0 | No |
| RULE-452 | identity | An emergency session ends within 60 seconds of the bound person losing super_admin or the emergency account being disabled | P0 | No |
| RULE-453 | identity | Replacing an account's roles by the set it already holds changes nothing | P2 | No |
| RULE-454 | identity | A custom role cannot carry administrator-mode permissions, and one that already does gives none in an ordinary session | P0 | No |
| RULE-455 | identity | An expired session is audited with its cause, idle or absolute, and then removed | P1 | No |
| RULE-456 | identity | A signed ticket never admits an administrator and admits only people holding a role in the issuer's scope | P0 | No |
| RULE-457 | identity | A signed ticket whose subject has no organisation source reference is refused and the refusal is audited | P1 | Yes |
| RULE-458 | identity | A session started from an external ticket holds no permission until the person confirms the identity | P0 | No |
| RULE-459 | identity | The legal entity and the project role are dimensions of a data scope of their own | P0 | No |

### RULE-437: In single operator mode an operations command may leave out the witness; the omission is marked in the same transaction as the change
**Category:** Policy
**Priority:** P0
**Source:** ADR-0030 section 2 row 1 (witness omission; user words in intent `2026-10-15-000912-one-coordinator-ADR-0030-dev-单人操.md`, approved 2026-10-15) as worded by `docs/adr/readme-deviations.md` E-004 (`witnessId=none`); `phase1a/cards/SINGLE-OP.md` P2-01, P2-02, P2-03. Tests: SingleOperatorWitnessOmissionIT#p201_grantRoleWithoutWitnessSucceeds, SingleOperatorWitnessOmissionIT#p202_markIsWrittenInTheSameTransaction, SingleOperatorWitnessOmissionIT#p203_resetWithoutWitness, SingleOperatorWitnessOmissionIT#p203_revokeWithoutWitness
**Plain English:** Where one person alone runs the development environment, the six operations commands (grant a role, revoke a role, register, reset, disable and enable an emergency admin) may be run without a witness. The normal audit entry is still written, naming the operator and recording that there is no witness, and a second entry in the same transaction marks that the witness requirement was relaxed.
**Specification:**
- Given the single operator mode is on and an employed, known employee, When the operations command grant-role runs with an operator and no witness for role business_admin, Then it exits with 0 and prints "role granted", the employee holds business_admin, and the normal entry has detail "operatorId=<operator> witnessId=none role=business_admin" on the user account.
- Given that command, Then exactly one more entry exists, action `platform.identity.single-operator-mode.applied`, detail exactly "relaxation=WITNESS_OMITTED", with the same subject as the normal entry; both entries are written inside the command's transaction and neither is written independently of it.
- Given an emergency admin registered with a witness (no mark written), When reset-emergency-admin-credentials runs without a witness, Then it succeeds and one mark is added.
- Given a business_admin role granted with a witness (no mark), When revoke-role runs without a witness, Then the role is no longer held and one mark is added.
**Confidence:** High

### RULE-438: The omission mark is written once per real change; a command that changes nothing writes neither a mark nor a normal audit entry
**Category:** Policy
**Priority:** P1
**Source:** ADR-0030 section 3 note on the audit column (the mark means "something was relaxed here"; an unchanged state is not marked); `phase1a/cards/SINGLE-OP.md` P2-03 (no-change case writes no mark). Tests: SingleOperatorWitnessOmissionIT#p203_registerWithoutWitness, SingleOperatorWitnessOmissionIT#p203_disableAndEnableWithoutWitness, SingleOperatorWitnessOmissionIT#p203_grantAlreadyHeldIsNotMarked
**Plain English:** The mark exists to show where a rule was relaxed. Running the same command again when the target state already holds changes nothing, so it leaves no mark and no further normal entry.
**Specification:**
- Given mode on, When register-emergency-admin for login name breakglass runs without a witness, Then it succeeds with one mark and one registered entry whose subject kind is emergency admin; When it runs again, Then the output contains "already registered" and the mark count is still 1.
- Given a registered emergency admin, When disable runs without a witness, Then output "disabled" and one mark; When disable runs again, Then "already disabled" and still 1 mark; When enable runs, Then output "enabled" and 2 marks; When enable runs again, Then "already enabled" and still 2 marks.
- Given business_admin granted without a witness, When the same grant-role runs again, Then exit 0, output contains "already held", the mark count is 1 and there is 1 role-granted entry.
**Confidence:** High

### RULE-439: The operator is always required and must be a known employee, in single operator mode too
**Category:** Validation
**Priority:** P0
**Source:** ADR-0030 section 2 row 1, "仍然保留" column (the operator is mandatory); `phase1a/cards/SINGLE-OP.md` P2-04; RULING-COORD-20261017-01 (`docs/rules/rulings/coord-2026-10-17.md`: with no witness the operator must exist as an employee; with a witness, the witness is checked); ADR-0069 decision on operations commands (operator and witness are employee identifiers). Tests: SingleOperatorWitnessOmissionIT#p204_operatorIsStillRequired, SingleOperatorWitnessOmissionIT#rulingCoord20261017_01_unknownOperatorWithoutWitnessIsRefused
**Plain English:** Leaving out the witness does not leave out the person responsible. A command with no operator, with an operator that is not a valid identifier, or with an operator who is not an employee in the organisation snapshot is refused with the witness-required error, and nothing is changed or marked.
**Specification:**
- Given mode on, When grant-role runs with no `--operator`, or with only `--witness`, or with the operator in upper case, or with operator "alice", Then the exit code is 1, the error contains PLATFORM_IDENTITY_OPERATION_WITNESS_REQUIRED, the identity tables are unchanged and no role-granted entry exists.
- Given mode on and an operator identifier that is a well-formed UUID but not an employee in the organisation snapshot, with no witness, Then exit code 1 with the same error, the role is not granted, no role-granted entry and no relaxation mark is written.
**Confidence:** High

### RULE-440: A witness that is given must be a valid employee identifier different from the operator; an invalid witness is never read as an omission
**Category:** Validation
**Priority:** P0
**Source:** ADR-0030 section 2 row 1 (operator and witness of the same name remain refused); `phase1a/cards/SINGLE-OP.md` P2-05, P2-06 (a given witness that is not a lower-case canonical UUID is refused, not treated as omitted); CLAUDE.md rule 6 (invalid input fails and alerts). Tests: SingleOperatorWitnessOmissionIT#p205_sameEmployeeIsStillRefused, SingleOperatorWitnessOmissionIT#p206_aWitnessThatIsGivenButInvalidIsRefused
**Plain English:** Only a witness that is entirely absent counts as an omission. If a witness is given it must be a lower-case canonical employee identifier and must not be the operator; anything else is refused instead of being quietly treated as "no witness".
**Specification:**
- Given mode on, When operator and witness are the same employee identifier, Then exit code 1 with the witness-required error and nothing changes.
- Given mode on, When the witness is given as an upper-case UUID, the name "alice", a truncated UUID, a UUID with a leading or trailing space, the all-zero UUID, or the digit "7", Then exit code 1 with the witness-required error, nothing changes and no role-granted entry is written.
**Needs decision:** The seven witness forms come from the test; the card source names only "upper case, name, truncated". Is the all-zero (nil) UUID, and a UUID with surrounding spaces, to be refused as a witness identifier as the test asserts?
**Confidence:** Medium

### RULE-441: With the mode off the witness is required for every operations command; with a valid witness the mode on behaves exactly as the mode off
**Category:** Policy
**Priority:** P0
**Source:** ADR-0030 section 2 row 1 and section 4 / "生产恢复" (production and the mode-off case are unchanged: two people present); `phase1a/cards/SINGLE-OP.md` P2-08, P2-09. Tests: SingleOperatorWitnessOmissionIT#p208_aGivenWitnessBehavesAsWithTheModeOff, SingleOperatorWitnessOmissionIT#p209_withTheModeOffTheWitnessIsRequired, SingleOperatorWitnessOmissionIT#p209_everyOperationNeedsTheWitnessWithTheModeOff
**Plain English:** The relaxation exists only while the mode is on and only when the witness is left out. Without the mode, every operations command still needs a valid, different witness. When a witness is given in mode on, the result and the audit text are the same as with the mode off and no mark is written.
**Specification:**
- Given mode off, When grant-role runs without a witness, Then exit code 1 with the witness-required error; When it runs with operator and witness the same, Then exit code 1 with the same error; nothing changes and no role-granted entry or mark is written.
- Given mode off, When each of grant-role, revoke-role, register-emergency-admin, reset-emergency-admin-credentials, disable-emergency-admin and enable-emergency-admin runs without a witness, Then each exits 1 with the witness-required error and the identity tables are unchanged.
- Given a grant-role with a valid, different witness run once with the mode off and once with the mode on (another employee), Then the two results (exit code, output, error) are equal, both role-granted entries have detail "operatorId=<operator> witnessId=<witness> role=business_admin", and no mark exists.
**Confidence:** High

### RULE-442: Single operator mode relaxes none of the in-application second-person rules, nor the database constraint behind them
**Category:** Policy
**Priority:** P0
**Source:** ADR-0030 section 4 items 1 and 5 (in-application two-person confirmation and the ban on changing one's own roles are not relaxed; the database constraint is unchanged; U-4 answered "都不做"); ADR-0026 section 6 item 2; SECURITY_INVARIANTS I-12; `phase1a/cards/SINGLE-OP.md` P2-23. Tests: SingleOperatorTwoPersonRegressionIT#p223_sensitiveGrantStillNeedsASecondPerson, SingleOperatorTwoPersonRegressionIT#p223_noSelfGrantEither, SingleOperatorTwoPersonRegressionIT#p223_theDatabaseConstraintStillHolds
**Plain English:** A sensitive role granted through the application still waits for a person who is neither the requester nor the beneficiary, and nobody may change their own roles; turning the single operator mode on changes neither. The database also still refuses a confirmation by the requester or the beneficiary.
**Specification:**
- Given mode on and administrators A, B and C, When A replaces B's roles with employee, super_admin and security_auditor, Then the answer is 202 with a request identifier; When A confirms, and when B confirms, Then each gets 403 with PLATFORM_IDENTITY_AUTHORIZATION_CONFIRMER_NOT_ALLOWED and B does not yet hold security_auditor; When C confirms, Then 204 and within 15 seconds B holds security_auditor.
- Given mode on, When administrator A replaces A's own roles adding security_auditor, Then 403 with PLATFORM_IDENTITY_OWN_ROLES_FORBIDDEN and A's roles are unchanged.
- Given mode on and a waiting request, When the request row is updated directly to confirmed with the decider set to the requester, or to the beneficiary, Then the database rejects it (constraint ck_authorization_requests_two_people) and the request is still listed as waiting.
**Confidence:** High

### RULE-443: Role version and grant history is never deleted, and a closed row never changes
**Category:** Policy
**Priority:** P0
**Source:** ADR-0034 decision 1 item 2 and decision 2 item 2, and "需同步修改的守护规则" item 1 (delete and truncate refused; changing a closed row refused); DATA_DESIGN_CHECKLIST §14.1 D17 item 2 (dimension versions are append-only, closing only). Tests: GrantHistoryAppendOnlyIT#deleteAndTruncateAreRefusedOnBothTables, GrantHistoryAppendOnlyIT#aClosedRowNeverChanges
**Plain English:** Who held which role, and what each role contained, must stay reconstructable. The two history tables (role versions and role grants) refuse deleting any row, open or closed, and refuse emptying the table. A row that has been closed refuses any change at all, even writing the same value back.
**Specification:**
- Given a role with a closed version 1 and an open version 2, and an account with a revoked and an open grant, When any single row or the whole table of role versions or role grants is deleted, or either table is truncated, Then the database rejects each statement and the role still has 2 versions and the account still has 2 grants.
- Given a closed role version and a closed grant, When an update sets the end time to itself, to another time, sets only the updated-at time, sets the closing columns, changes the role name, or clears the end time, Then every statement is rejected.
**Confidence:** High

### RULE-444: An open role version or grant can be closed once, by writing the closing fields only, and a revocation always carries its cause and audit entry
**Category:** Lifecycle
**Priority:** P0
**Source:** ADR-0034 decision 1 item 2 (a trigger allows only the closing columns, each from empty to filled once; closing columns are filled together) and decision 2 items 1, 2, 3, 4 (revocation causes manual, separation, operations, migration rollback; the channel of the opening cannot be rewritten); ADR-0068 (separation command identifier replaces the sync run in the separation closure); ADR-0034 guard item 1. Tests: GrantHistoryAppendOnlyIT#anOpenVersionCanOnlyBeClosedOnce, GrantHistoryAppendOnlyIT#anOpenVersionIsClosedByDeletingTheRole, GrantHistoryAppendOnlyIT#anOpenGrantCanOnlyBeRevokedOnce, GrantHistoryAppendOnlyIT#anOpenGrantIsClosedBySeparationOrByHand, GrantHistoryAppendOnlyIT#revokingWithoutCauseAndAuditEntryIsRefusedByThePairedCheck
**Plain English:** The only change allowed on an open history row is closing it, once. Closing writes the end time and the closing fields together and nothing else; closing a grant writes its cause and the audit entry that recorded it. Deleting a role closes its last version, so the role then has no current version.
**Specification:**
- Given an open role version, When any of role, permission codes, name, status, scope kind, version number, start time, audit entry, created-at, or only updated-at is changed, or the close is combined with a rename, Then the database rejects it; When end time and updated-at are written, Then the version is closed at that time; When it is written again, Then it is rejected.
- Given an open role version of a role being deleted, When end time, closed-by, closure reason, closure audit entry and updated-at are written together, Then the role has no current version; a second closing attempt is rejected.
- Given an open grant, When role, channel, granted-by, person, start time, audit entry, created-at or only updated-at is changed, or a cause is written without an end time, or the revocation is combined with a channel change, Then it is rejected; When end time, cause OPERATIONS, revocation audit entry and updated-at are written together, Then the person holds no current grant; a second revocation is rejected.
- Given open grants, When one is closed with cause SEPARATION and a separation command identifier, and another with cause MANUAL and a revoked-by person, each with its audit entry, Then both closings succeed.
- Given an open grant, When only an end time is written, or an end time with only the cause, or an end time with only the audit entry, Then the database rejects each (check ck_account_role_grants_revocation) and the grant stays current.
**Confidence:** High

### RULE-445: A role without a current version cannot be granted
**Category:** Validation
**Priority:** P1
**Source:** ADR-0034 decision 2 item 1 (an insert of a grant for a role without an open version, that is a deleted role, is refused) and decision 1 item 5 (deleting a role closes its last version). Tests: GrantHistoryAppendOnlyIT#aRoleWithoutCurrentVersionCannotBeGranted
**Plain English:** A role that was never given a version, or whose only version was closed because the role was deleted, cannot be given to anyone. A role with an open version can.
**Specification:**
- Given a role with no version and a role whose only version was closed by deletion, When a grant for either is inserted, Then the database rejects it; Given a role with an open version, When a grant is inserted, Then it is accepted.
**Confidence:** High

### RULE-446: Role version periods are finite, not inverted and never overlap for one role; a role name is unique at each moment
**Category:** Validation
**Priority:** P1
**Source:** ADR-0034 decision 1 item 2 (exclusion on the role and the half-open period; on the lower-cased name and period, replacing the global name uniqueness; end not before start, so a zero-length period is allowed; finite times); DATA_DESIGN_CHECKLIST §14.1 D17 item 2; RULE_REVIEWS RULE-345 (unique role name, revised by ADR-0034). Tests: MomentVersionTablesIT#roleVersionPeriodsAreFiniteAndNotInverted, MomentVersionTablesIT#roleVersionsOfOneRoleDoNotOverlap, MomentVersionTablesIT#roleNamesAreUniqueAtEachMoment
**Plain English:** Each version of a role is valid for a real, ordered period; two versions of the same role never share a moment, though one may start where the other ends. Two roles cannot carry the same name (ignoring case) at the same moment, but a name may be reused after the earlier role stopped using it.
**Specification:**
- Given a role, When a version has no start time, Then it is rejected as null; When the start or the end is infinite, Then it is rejected (ck_role_versions_finite); When the end is before the start, Then it is rejected (ck_role_versions_period); When the start equals the end, Then it is accepted.
- Given version 1 of a role valid from T0 to T2, When version 2 starts at T1 with no end, Then it is rejected as overlapping; When version 2 runs from T1 to T1, Then it is accepted; When an open version starts at T2 and a further one starts at T3 with no end, Then the latter is rejected.
- Given role A named N valid T0 to T2, When role B takes N in upper case from T1, Then it is rejected; When role B takes the upper-case N from T2, Then it is accepted.
**Confidence:** High

### RULE-447: Grant periods are finite and not inverted; a person holds a role at most once at any moment
**Category:** Validation
**Priority:** P0
**Source:** ADR-0034 decision 2 item 1 (exclusion on person, role and period, so at most one open grant per person and role; end not before start; finite times) and item 2 (the next grant starts no earlier than the last end); DATA_DESIGN_CHECKLIST §14.1 D17 item 5 (authorization is taken from the open row). Tests: MomentVersionTablesIT#grantPeriodsAreFiniteNotInvertedAndDoNotOverlap
**Plain English:** A grant has a real start and a finite, ordered period. The same person cannot hold the same role twice at one moment, but a new grant may begin exactly when the earlier one ends, and the person may hold other roles at the same time.
**Specification:**
- Given a person and a role with an open version, When a grant has no start, Then it is rejected as null; When the start or end is infinite, Then rejected (ck_account_role_grants_finite); When the end is before the start, Then rejected (ck_account_role_grants_period).
- Given a grant for the person and the role from T0 to T2, When another starts at T1 with no end, Then it is rejected as overlapping; When another runs T1 to T1, Then accepted; When a grant of another role starts at T1, Then accepted; When an open grant starts at T2 and a further open one starts at T3, Then the further one is rejected and exactly one grant of that person and role is current.
**Confidence:** High

### RULE-448: Emergency login is accepted only from the internal network, judged before any credential is looked at
**Category:** Validation
**Priority:** P0
**Source:** ADR-0027 decision 1 items 2 and 9 (internal source first; internal network limit unchanged); `docs/business/processes/login-and-session.md` T7 (emergency account: internal network only). Tests: EmergencyLoginIntegrationTest#internalNetworkOnly, EmergencyLoginIntegrationTest#forwardedHeaderFromAnOutsiderIsIgnored
**Plain English:** The emergency endpoint answers only requests whose own network address is internal. A request from outside is refused before its password or code is examined, so an outsider cannot count failures against the account. A client cannot make itself look internal by sending forwarding headers.
**Specification:**
- Given an emergency admin and a valid password and code, When the request comes from address 203.0.113.9, Then 403 with PLATFORM_IDENTITY_NETWORK_DENIED and the account's failed-attempt counter is 0.
- Given the same request from 203.0.113.9 with headers X-Forwarded-For and X-Real-IP set to 10.1.2.3, Then 403.
**Confidence:** High

### RULE-449: An emergency one-time code works once
**Category:** Validation
**Priority:** P0
**Source:** ADR-0027 decision 1 item 2 (the one-time code is a single step); `docs/business/processes/login-and-session.md` T7 (TOTP with step replay protection). Tests: EmergencyLoginIntegrationTest#codeCannotBeReplayed
**Plain English:** Once a one-time code has been used for a successful login, the same code is refused even with the right password.
**Specification:**
- Given a valid password and a current code, When the login succeeds and the same request is repeated with the same code, Then the first answer is 200 and the second 401.
**Confidence:** High

### RULE-450: An emergency account works only for a bound person who is enabled, holds super_admin now, and is not separated
**Category:** Validation
**Priority:** P0
**Source:** ADR-0027 decision 1 items 1, 2, 3 (bound to a current super_admin holder; bound account enabled; a snapshot that lists the person as separated refuses, a missing or stale snapshot admits); ADR-0033 decision 3 item 7 and U-4 (user answer, intent `2026-10-15-020346-one-coordinator-ADR-0033-#119-与.md`: a current snapshot that does not list the bound employee refuses, an unavailable snapshot still admits); intent `2026-10-14-113-1a流程-登录-应急账号.md`. Tests: EmergencyLoginIntegrationTest#boundPersonMustHoldSuperAdmin, EmergencyLoginIntegrationTest#boundAccountMustBeEnabled, EmergencyLoginIntegrationTest#separatedPersonIsRefused, EmergencyLoginIntegrationTest#currentSnapshotWithoutTheBoundEmployeeIsRefused
**Plain English:** A valid password and code are not enough. At login the bound person must still hold super_admin, the bound account must be enabled, and the organisation snapshot must not show the person as separated or, when the snapshot is current, as unknown. These checks use FORCITIS one's own tables and the snapshot, not the organisation source itself.
**Specification:**
- Given the bound person's role grants are gone, When the emergency login runs with valid credentials, Then 401 PLATFORM_IDENTITY_CREDENTIALS_INVALID, no session cookie, and an independent failed-login audit entry whose detail says the person no longer holds super_admin.
- Given the bound account is disabled, Then 403 PLATFORM_IDENTITY_ACCOUNT_DISABLED.
- Given the snapshot lists the bound employee as separated, Then 403 PLATFORM_IDENTITY_EMPLOYEE_SEPARATED and no session cookie.
- Given the snapshot is current but does not list the bound employee, Then 403 PLATFORM_IDENTITY_EMPLOYEE_SEPARATED and no session cookie. (An unavailable snapshot still admits the login; that case is asserted by EmergencyLoginIntegrationTest#unavailableSnapshotIsAllowed, outside this batch.)
**Confidence:** High

### RULE-451: An emergency login starts in administrator mode for one hour, audited and alerted, and the hour is not extended
**Category:** Lifecycle
**Priority:** P0
**Source:** ADR-0027 decision 1 item 5 (administrator mode for 1 hour, no extension, alert and audit on every login); ADR-0026 decision 5 item 6; SECURITY_INVARIANTS I-11. Tests: EmergencyLoginIntegrationTest#administratorModeForOneHour, EmergencyLoginIntegrationTest#administratorModeIsNotExtended
**Plain English:** A successful emergency login goes straight into administrator mode, which lasts one hour from login, whatever the person does. Each login is audited and counted, and the later requests of the same session are not re-checked for employment.
**Specification:**
- Given a valid emergency login, Then 200, a session cookie is set, the administrator-mode expiry lies between login time plus 1 hour at the start and at the end of the request, the audit holds `platform.identity.login.succeeded` (method EMERGENCY) and `platform.identity.emergency.login`, and the emergency login counter rose by 1.
- Given that session, When the current-session profile is read, Then its mode is ADMINISTRATOR with the same expiry, and a second request still returns 200.
- Given a session 59 minutes after login, Then 200; When 2 further minutes pass, Then 401 and no session row remains.
**Confidence:** High

### RULE-452: An emergency session ends within 60 seconds of the bound person losing super_admin or the emergency account being disabled
**Category:** Lifecycle
**Priority:** P0
**Source:** ADR-0027 decision 1 item 4 (every request re-checks account enabled, emergency account enabled and current super_admin, cached for at most 60 seconds); ADR-0026 decision 6 item 6; SECURITY_INVARIANTS I-11. Tests: EmergencyLoginIntegrationTest#sessionEndsWhenSuperAdminIsTakenAway, EmergencyLoginIntegrationTest#sessionEndsWhenTheEmergencyAccountIsDisabled
**Plain English:** An open emergency session does not outlive its basis. Without any event, the next request after the check interval fails when the bound person no longer holds super_admin or the emergency account is disabled.
**Specification:**
- Given an emergency session answering 200, When the bound person's grants are removed and 61 seconds pass, Then the next request returns 401.
- Given an emergency session answering 200, When the emergency account is disabled and 61 seconds pass, Then the next request returns 401.
**Confidence:** High

### RULE-453: Replacing an account's roles by the set it already holds changes nothing
**Category:** Calculation
**Priority:** P2
**Source:** ADR-0034 decision 2 item 5 (the replacement is applied as a difference: what is no longer needed is closed, what is new is opened, what is unchanged is left untouched) and item 7 (the account version is the lock); decision 1 item 4 (an unchanged definition adds no version). Tests: RoleManagementIntegrationTest#sameRolesChangeNothing
**Plain English:** If an administrator submits the roles an account already has, nothing is closed or opened, so the account's version does not move.
**Specification:**
- Given an account holding only role analyst at version V, When replace-roles is submitted with ["analyst"] and the current version tag, Then the answer is 204 and the account version is still V.
**Confidence:** Medium

### RULE-454: A custom role cannot carry administrator-mode permissions, and one that already does gives none in an ordinary session
**Category:** Policy
**Priority:** P0
**Source:** SECURITY_INVARIANTS I-11 (platform permissions live in administrator mode only); ADR-0028 decision 5 (the five administrator-mode permissions; separation-hold approval may also be held outside it); ADR-0026 decision 5 items 1 and 2; `phase1a/review/REVIEW_integration.md` finding INT-SEC-H2. Tests: RoleManagementIntegrationTest#platformPermissionsStayInTheAdministratorMode, RoleTest#customRoleCannotCarryAdministratorModePermission
**Plain English:** Creating or revising a custom role with any of the platform permissions (read roles, change role definitions, change account roles, force logout) is refused. The permission to approve a separation hold is the exception. If such a role already exists, its holder still receives none of those permissions in an ordinary session.
**Specification:**
- Given administrator mode, When a custom role is created with role:update, Then 422 PLATFORM_IDENTITY_PERMISSION_ADMINISTRATOR_MODE_ONLY; When one is created with separation-hold:approve, Then the answer is not 422.
- Given a role that holds role:update, account-role:update, session:delete, role:read and approval task:read, and a person who holds it, When the person signs in, Then the permissions include task:read and none of the four platform permissions, and reading the role list returns 403.
- Given the domain, When a custom role is created, or an existing one revised, with role:read, role:update, account-role:update or session:delete, Then it is rejected as administrator-mode-only; with separation-hold:approve it is kept.
**Confidence:** High

### RULE-455: An expired session is audited with its cause, idle or absolute, and then removed
**Category:** Lifecycle
**Priority:** P1
**Source:** `docs/business/processes/login-and-session.md` T8 and D-1a-41 (session lifetime: 10 hours idle, 24 hours absolute; user answer recorded in intent `2026-10-14-050-规则评审第三批-identity.md`, RULE_REVIEWS RULE-332); D-1a-45 (the reason a session ended is recorded only in the audit). Tests: SessionLifecycleIntegrationTest#expiredSessionsAreAuditedThenRemoved
**Plain English:** Sessions have no status field, so when a session runs out the reason is kept in the audit. A background purge writes one entry per expired session saying whether it was idle too long or too old, and then deletes it. A live session is left alone.
**Specification:**
- Given three sessions: one whose expiry is already past, one created 24 hours and one second ago whose idle expiry is still ahead, and one live, When the purge runs, Then it removes 2, one session row remains, and the audit holds `platform.identity.session.expired` for the first person with detail "cause=IDLE" and for the second with "cause=ABSOLUTE", each on the user account subject.
**Confidence:** High

### RULE-456: A signed ticket never admits an administrator and admits only people holding a role in the issuer's scope
**Category:** Validation
**Priority:** P0
**Source:** `docs/business/processes/login-and-session.md` T5 and section 2A.7 (tickets cannot bring in an administrator role; an issuer has an allowed role scope; an empty scope lets nobody in; no super_admin in a scope); SECURITY_INVARIANTS I-3; ADR-0009 decision 3. Status: the ticket mechanism may be removed after the identity lifecycle study and T-13 (ADR-0055 decision 6, ADR-0069 H-2); the card holds while it exists. Tests: SignedTicketLoginIntegrationTest#administratorsAreRefused, SignedTicketLoginIntegrationTest#userScope
**Plain English:** An external system's signed ticket can never start a session for someone who holds super_admin, whatever the issuer is configured for, and an issuer cannot even be configured with that role. A person is admitted only if they hold at least one role the issuer is allowed to bring in; an issuer with an empty list admits nobody.
**Specification:**
- Given an account holding super_admin, When its valid ticket is redeemed, Then 403 PLATFORM_IDENTITY_ROLE_NOT_ALLOWED and no session cookie; Given an issuer record scoped to super_admin, When it is inserted, Then the database rejects it.
- Given an employed person with no role, When their ticket is redeemed, Then 403; Given an issuer whose allowed role list is empty, When a ticket for a person who holds a role is redeemed, Then 403.
**Confidence:** High

### RULE-457: A signed ticket whose subject has no organisation source reference is refused and the refusal is audited
**Category:** Validation
**Priority:** P1
**Source:** ADR-0033 decision 3 item 5 (the ticket subject is resolved to a user, and an unresolved subject refuses the login and is audited; the present resolution is kept until DA-3). Status: ADR-0055 decision 6 makes the survival of signed-ticket login depend on the identity lifecycle study and T-13; ADR-0069 H-2. Tests: SignedTicketLoginIntegrationTest#subjectWithoutSourceReference
**Plain English:** The ticket names a person by the organisation source's own person identifier. An employed person who has an account but no such source reference cannot be named, so the ticket is refused as invalid, no session starts, and a failed-login entry records why.
**Specification:**
- Given an employed employee with an account but no organisation source reference, When a ticket naming them is redeemed, Then 401 PLATFORM_IDENTITY_TICKET_INVALID, no session cookie, and an independent audit entry `platform.identity.login.failed` whose detail says the subject has no organisation source reference.
**Needs decision:** Does ticket login keep resolving the subject through the organisation source reference, move to the external identity table (ADR-0033 decision 4), or disappear with the ticket mechanism (ADR-0055 decision 6, ADR-0069 H-2)? This card stands or falls with that decision.
**Confidence:** Low

### RULE-458: A session started from an external ticket holds no permission until the person confirms the identity
**Category:** Policy
**Priority:** P0
**Source:** SECURITY_INVARIANTS I-13; intent `2026-10-14-191618-psa-1a-platform-bridge-reimagine-新浏览器才确认-Recommen.md` (C-M7, user answer 「新浏览器才确认」; the dispatch keeps confirmation on every ticket login); `phase1a/review/REVIEW_fix-b3.md` C-M7 (permissions are cleared on the server, not only hidden). Tests: SignedTicketLoginIntegrationTest#ticketSessionNeedsConfirmation
**Plain English:** A ticket proves only that another site sent someone here. Until the person confirms that the account is theirs, the session has no permission and shows no menu; confirming changes the session identifier, gives the browser an identifier cookie and is audited.
**Specification:**
- Given a valid ticket login, Then the answer says identity confirmation is required; the session reads the approval task list as 403, the current-session profile shows identityConfirmed false, an empty permission list and an empty navigation.
- When the person posts the identity confirmation, Then 204, a new session cookie different from the old one and a browser identifier cookie are issued, the approval task list is no longer 403, and the audit holds `platform.identity.session.identity-confirmed`.
**Confidence:** High

### RULE-459: The legal entity and the project role are dimensions of a data scope of their own
**Category:** Policy
**Priority:** P0
**Source:** ADR-0009 decision 4 (data scope by legal entity × department × project role, passed explicitly); AI_NATIVE_SPEC C1a-09; RULE_REVIEWS RULE-349 (revised: scope expressed as an explicit AccessScope over legal entity × department × project role); SECURITY_INVARIANTS I-10 (fail-closed); ADR-0026 decision 5 note on project-role access. Tests: AccessScopeTest#legalEntityDimension, AccessScopeTest#projectRoleDimension
**Plain English:** A department scope only reaches rows of the legal entity it was granted for, so the same department in another legal entity is outside it. A grant tied to a project role reaches only rows carrying that project role; rows with another or no project role are outside it.
**Specification:**
- Given a person in legal entity LE1, department 15, with an own-department-and-below scope, When a row of LE1 department 15 is checked, Then it is permitted; When a row of LE2 department 15 is checked, Then it is not.
- Given a grant for LE1, department 15 and below, project role project-manager, When a row under department 16 carries project-manager, Then it is permitted; When it carries role member, or no project role, Then it is not.
**Confidence:** High

**New terms:** ProjectRole (项目角色, domain, bc platform.identity; proposed, used in ADR-0009 decision 4 and the AccessGrant); IdentityConfirmation (身份确认, domain, bc platform.identity; the "this is my account" step of RULE-458); BrowserIdentifier (浏览器标识, domain, bc platform.identity; SECURITY_INVARIANTS I-13). Terms already in `terms.yaml`: SingleOperatorMode, SingleOperatorRelaxation, Witness, Actor (operator), EmergencyAdmin, AdministratorMode, SuperAdmin, AuthorizationRequest, AccessScope, AccessGrant, Ticket, TicketIssuer, Session, Separation, OrgSnapshot. Terms in `backend/glossary/proposed`: RoleVersion, AccountRoleGrant, RevocationCause.
