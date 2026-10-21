# Identity, authorization and single-operator business rules — RULE-418..RULE-436

Scope: `platform.identity` behaviour of FORCITIS one that acceptance tests assert (`acceptance/platform/identity`) and that had no rule card: account identity, sensitive authorization confirmation, own-role ban, emergency login and emergency accounts, operations commands (operator and witness), session expiry, role revocation by command, held separation of administrators, and the non-production single-operator mode. Basis: ADR-0026, ADR-0027, ADR-0028, ADR-0030, ADR-0033, ADR-0034, ADR-0055 (the retired synchronisation inputs of ADR-0028 and ADR-0033 are read as the local separation command and local workforce record, ADR-0055 decision 3), `phase1a/SECURITY_INVARIANTS.md` I-11 and I-12, and the rulings and data reviews named per card. These are rules of the new system; no card copies a behaviour of the old system. Where a test pins a value that no ADR, specification or intent states, the card says so under **Needs decision** and its Review column is Yes.

| ID | Domain | Title | Priority | Review |
|---|---|---|---|---|
| RULE-418 | identity | An employee has exactly one user account, even when two first logins happen at once; the account has its own id and is addressed by it, never by the employee id | P0 | No |
| RULE-419 | identity | A sensitive authorization request is confirmed by a third person: neither the requester, nor the person who would receive the roles, nor a holder of the changed role may confirm | P0 | No |
| RULE-420 | identity | An emergency login is admitted only when the bound person is on the workforce record and employed; otherwise it is refused, no session starts and an alert is raised | P0 | No |
| RULE-421 | identity | An operations command names the employee by canonical id; the employee must exist; the account is opened or reused | P1 | No |
| RULE-422 | identity | An operations command needs an operator and a witness who are two different existing employees; without single-operator mode a missing witness refuses the command and writes nothing | P0 | No |
| RULE-423 | identity | An employee exists for the operator and witness check when the workforce record holds the employee, with or without an assignment and whether employed or separated | P1 | Yes |
| RULE-424 | identity | Nobody changes their own roles; the refusal is audited, other people's roles are unaffected | P0 | No |
| RULE-425 | identity | A session idle for more than 10 hours is ended and purged; a request with it is answered 401 | P1 | No |
| RULE-426 | identity | With single-operator mode on, an operations command may omit the witness; the operator stays required and a relaxation mark is written with the normal audit entry | P1 | No |
| RULE-427 | identity | With single-operator mode off, the emergency-account minimum is two and the warning says so | P0 | No |
| RULE-428 | identity | With single-operator mode on, one usable emergency account is enough; the reconciliation and the registration prompt use a minimum of one | P1 | No |
| RULE-429 | identity | An emergency account is usable only while the account, its bound person's account and the bound person's super_admin role are all in force; the mode never relaxes this | P0 | No |
| RULE-430 | identity | An emergency account can be registered or enabled only for a person who holds super_admin and has an enabled account, in every mode | P0 | No |
| RULE-431 | identity | Each time the single-operator relaxation of the emergency-account minimum applies, exactly one mark is written; when it does not apply, none | P1 | No |
| RULE-432 | identity | revoke-role takes a held role away, ends all sessions of the person, clears the permission cache and is audited | P1 | No |
| RULE-433 | identity | revoke-role refuses an unknown role or a person without an account and opens no account; a role that is not held is reported as nothing changed | P1 | Yes |
| RULE-434 | identity | A sensitive role is taken back by the operations command in either mode, with no authorization request | P1 | No |
| RULE-435 | identity | The separation of a super_admin or security auditor holder is held until a retained person releases it; the person leaving cannot; ordinary separations are never held | P0 | No |
| RULE-436 | identity | The held-separation reason states a minimum of two usable super_admin holders, or one with single-operator mode on; nothing else about the verdict changes | P1 | No |

### RULE-418: An employee has exactly one user account, even when two first logins happen at once; the account has its own id and is addressed by it, never by the employee id
**Category:** Validation
**Priority:** P0
**Source:** ADR-0033 decision 3 item 1 (account by `employee_id` unique, own UUID, ensure-account insert-and-read-back) and item 6 (commands take the employee id); ADR-0055 decision 2 (employee and account each keep their own UUIDv7, ids kept apart, one account per employee); `SECURITY_INVARIANTS.md` I-12 (grant paths compare accounts). Tests: `AccountEnsureConcurrencyIT#twoFirstLoginsAtOnceOpenOneAccount`, `OwnRolesIT#theAccountPathTakesTheUserIdNotTheEmployeeId`
**Plain English:** A person has one account however many logins arrive together, and both logins get the same account. The account identifier is the account's own, never equal to the employee identifier, and every account path and audit subject uses the account identifier; an employee identifier in an account path finds nothing.
**Specification:**
- Given an employee with no account, When two first logins of that person run at the same moment (checked for 5 people in turn), Then both answer the same user id, exactly that one account exists for the employee, and the user id text differs from the employee id text.
- Given an administrator in administrator mode and a colleague with an account, When the roles are replaced through the account path carrying the colleague's employee id, Then the answer is 404, the colleague's roles are unchanged and no grant audit entry is written.
- Given the same replacement through the path carrying the colleague's user id, Then it is accepted (204) and exactly one role-replacement audit entry exists for that administrator, with subject kind USER_ACCOUNT and the user id as subject, not the employee id; no refusal is recorded for the earlier 404.
**Confidence:** High

### RULE-419: A sensitive authorization request is confirmed by a third person: neither the requester, nor the person who would receive the roles, nor a holder of the changed role may confirm
**Category:** Validation
**Priority:** P0
**Source:** ADR-0026 decision 6 item 2 (sensitive grants wait for another holder of the account-role permission, administrators included) and the guard `SensitiveGrantTwoPersonIT`; ADR-0030 decision 4 item 1 (the in-application two-person confirmation is not relaxed); `SECURITY_INVARIANTS.md` I-12 (a request to change a role's definition cannot be confirmed by a holder of that role); ADR-0033 decision 3 item 3 (compared as accounts). Tests: `AuthorizationConfirmerAccountsIT#aHolderOfTheChangedRoleCannotConfirm`, `AuthorizationConfirmerAccountsIT#theRequesterAndTheBeneficiaryCannotConfirm`
**Plain English:** Adding a sensitive permission to a role, or giving a person a sensitive role, waits for a second person. The person who asked, the person who would benefit, and anyone holding the role being changed are all refused as confirmers with error code PLATFORM_IDENTITY_AUTHORIZATION_CONFIRMER_NOT_ALLOWED; a third person may confirm and the change then takes effect.
**Specification:**
- Given a custom role and a request by administrator A to add the numbering-rule approve permission to it, with administrator B holding that role and administrator C holding neither, When A confirms, Then 403 PLATFORM_IDENTITY_AUTHORIZATION_CONFIRMER_NOT_ALLOWED; When B confirms, Then the same 403; When C confirms, Then 204.
- Given a request by A to replace the roles of B with employee, super_admin and security_auditor (accepted with 202), Then the request lists A as requester and B as subject, both by user id; When A confirms, Then 403 with that code; When B confirms, Then 403 with that code and B still lacks security_auditor; When C confirms, Then 204 and within 15 seconds B holds security_auditor.
**Confidence:** High

### RULE-420: An emergency login is admitted only when the bound person is on the workforce record and employed; otherwise it is refused, no session starts and an alert is raised
**Category:** Validation
**Priority:** P0
**Source:** ADR-0055 decision 3 item 1 (the employment check reads the local workforce record; a missing or invalid record is refused with an alert, no external-failure branch); ADR-0033 decision 3 item 7 (answer of U-4: refuse and alert; the check is made on the employee behind the bound account); ADR-0027 decision 1 items 2 and 5 (emergency login enters administrator mode, every login alerts). Tests: `EmergencyLoginEmploymentIT#aWorkingSnapshotWithoutTheBoundEmployeeRefuses`, `EmergencyLoginEmploymentIT#aWorkingSnapshotWithTheBoundEmployeeEmployedLetsThrough`
**Plain English:** The emergency account is bound to an account whose id differs from its employee id, so the check goes through the account to the employee and then to the local workforce record. If the employee is not found the login fails, no session is created, a login-failed audit entry is written and an alert is raised. If the employee is found and employed the login succeeds and enters administrator mode.
**Specification:**
- Given a bound super_admin whose employee is removed from the workforce record, When the emergency login is made with the right credentials, Then the answer is 401 or 403, no SESSION cookie is set, at least one alert is raised and a `platform.identity.login.failed` audit entry exists.
- Given the same account with the employee on the record and employed, Then the answer is 200, a session starts and the body carries `administratorModeExpiresAt`.
**Needs follow-up (not a decision):** the tests still build a synchronised snapshot as fixture (a successful run a minute ago); under ADR-0055 the fixture becomes the local workforce record, and the tests for a missing or stale snapshot in the same class are retired with the external-failure branch.
**Confidence:** Medium

### RULE-421: An operations command names the employee by canonical id; the employee must exist; the account is opened or reused
**Category:** Validation
**Priority:** P1
**Source:** ADR-0033 decision 3 item 6 (grant and register commands take `--employee-id`, get or open the account through ensure-account; a malformed id or a missing employee gives a non-zero exit code and writes nothing); ADR-0034 decision 2 item 3 (`OPERATIONS` source of a grant). Tests: `OperationsCommandEmployeeIdIT#aValidEmployeeIdOpensOrReusesTheAccount`, `OperationsCommandEmployeeIdIT#anEmployeeThatDoesNotExistIsRefused`
**Plain English:** grant-role works on an employee that exists. For the first grant it opens the employee's account; a second grant reuses it. A well-formed id of an employee that does not exist is refused and nothing is written.
**Specification:**
- Given an existing employee and a grant-role with the employee's canonical uuid, Then the exit code is 0, one account exists for the employee and its id differs from the employee id; a second grant-role of org_data_admin ends with exit code 0 and the same single account.
- Given a canonical uuid that belongs to no employee, Then the exit code is non-zero and the identity tables hold the same rows as before.
**Confidence:** High

### RULE-422: An operations command needs an operator and a witness who are two different existing employees; without single-operator mode a missing witness refuses the command and writes nothing
**Category:** Validation
**Priority:** P0
**Source:** ADR-0027 decision 1 item 1 (operations commands are done with two people present and audited); ADR-0030 decision 2 table row 1 (witness may be omitted only in single-operator mode; the operator is required, operator and witness of the same name are refused, and with the mode off a missing witness is refused) and decision 4 guard 6; ADR-0026 decision 5 item 5; DR-TB01-FU1 (data review `data/reviews/DR-TB01-FU1-1.md` and `data/repair-cards/DATA-REPAIR-timesheet-audit.md`: operator and witness must be existing employees, exit code 1, error code PLATFORM_IDENTITY_OPERATION_WITNESS_REQUIRED). Tests: `OperationsCommandEmployeeIdIT#fu1AnOperatorWhoDoesNotExistIsRefused`, `OperationsCommandEmployeeIdIT#fu1AWitnessWhoDoesNotExistIsRefused`, `OperationsCommandEmployeeIdIT#fu1NeitherExistsIsRefused`, `OperationsCommandEmployeeIdIT#fu1MissingSameOrNotAnIdIsStillRefused`, `OperationsCommandEmployeeIdIT#fu1TwoDifferentExistingEmployeesProceed`, `SingleOperatorEndToEndIT#p222_theSameCommandWithoutTheKeyIsRefused`, `SingleOperatorMarkTransactionIT#p202_grantWithoutWitnessIsRefusedWithTheModeOff`, `SingleOperatorMarkTransactionIT#p202_revokeWithoutWitnessIsRefusedWithTheModeOff`, `SingleOperatorMarkTransactionIT#p202_disableWithoutWitnessIsRefusedWithTheModeOff`, `SingleOperatorRevokeRoleIT#p219_witnessIsRequiredWithTheModeOff`
**Plain English:** The two people on an operations command are named by employee id and both must be real employees, different from each other. Any other combination is refused with exit code 1 and error code PLATFORM_IDENTITY_OPERATION_WITNESS_REQUIRED before anything is written or audited. Two valid, different employees proceed, and the audit entry names both by employee id. This holds for every command (grant-role, revoke-role, disable-emergency-admin), also when it is called directly in code, and the command line without the single-operator key is refused for a missing witness.
**Specification:**
- Given a grant-role for an existing employee, When the operator id is a canonical uuid that matches no employee, Then exit code 1, the error contains PLATFORM_IDENTITY_OPERATION_WITNESS_REQUIRED, no identity row changes, no account is opened and no audit entry of any kind is added; the same when the witness matches no employee and when neither does.
- Given witness missing, operator missing, operator and witness the same id, or the witness text `not-an-employee-id`, Then each is refused in the same way with nothing written or audited.
- Given two different existing employees, Then exit code 0, one account for the person, and exactly one more audit entry whose detail is `operatorId=<operator> witnessId=<witness> role=business_admin`.
- Given single-operator mode off (or the key not set on the command line), When grant-role, revoke-role (role still held afterwards) or disable-emergency-admin (account still enabled afterwards) is called with an operator and no witness, Then it is refused (exit code 1 with the witness-required code, or `IllegalStateException` when the application service is called directly), the account is not opened or the role is not touched, no operation audit entry and no relaxation mark are written, and the transaction rolls back as a whole; the process output contains no ADR-0030 start-up warning.
**Confidence:** High

### RULE-423: An employee exists for the operator and witness check when the workforce record holds the employee, with or without an assignment and whether employed or separated
**Category:** Policy
**Priority:** P1
**Source:** data design ruling `data/reviews/DR-TB01-FU1-1.md` (one-data, 2026-10-17: exists = the workforce record holds a row for the id; neither an assignment nor employment is required, so the emergency channel works while organisation data is still incomplete and the audit can resolve the name later); `rulings/COORD-2026-10-17.md` RULING-COORD-20261017-03; `data/reviews/DATA-REPAIR-timesheet-audit-PR1.md` (whether to require employment is a later policy tightening, not part of the card). Tests: `OperationsCommandEmployeeIdIT#fu1ASeparatedEmployeeStillCounts`, `OperationsCommandEmployeeRowOnlyIT#nits1AnOperatorAndWitnessWithoutAssignmentsAreAccepted`
**Plain English:** The operator and the witness need only be known people: a row on the workforce record. A person who has left, and a person with no assignment yet, both still count.
**Specification:**
- Given an operator and a witness whose workforce rows exist and who have no assignment at all, When grant-role is run for an existing employee, Then the exit code is 0 and the employee has one account.
- Given an employee marked SEPARATED with a separation time, as operator, and a valid witness, When grant-role of business_admin is run, Then the exit code is 0.
**Needs decision:** may a separated employee act as the operator or the witness of an operations command, or must a policy tightening require them to be employed (the data review records this as an open later item)?
**Confidence:** Medium

### RULE-424: Nobody changes their own roles; the refusal is audited, other people's roles are unaffected
**Category:** Validation
**Priority:** P0
**Source:** ADR-0026 decision 6 item 2 and guard 7 (`SelfGrantForbiddenIT`: replacing one's own roles returns 403 and writes audit); `SECURITY_INVARIANTS.md` I-12 first half; ADR-0030 decision 4 item 5 (not relaxed); ADR-0033 decision 3 item 3 (both sides compared as user ids). Tests: `OwnRolesIT#nobodyChangesTheirOwnRoles`
**Plain English:** An administrator who tries to replace the roles of their own account is refused with PLATFORM_IDENTITY_OWN_ROLES_FORBIDDEN, even though they may change other people's. The roles stay as they were and the refusal is written to the audit with the administrator as actor.
**Specification:**
- Given an administrator in administrator mode and a new custom role `synthetic_own_roles`, When the administrator replaces the roles of their own account with employee and that role, Then 403 PLATFORM_IDENTITY_OWN_ROLES_FORBIDDEN, the roles are as before, and every `platform.identity.grant.refused` entry of this administrator has the administrator as actor and at least one exists.
- Given the same change for a colleague's account, Then 204 and the colleague holds the role.
**Confidence:** High

### RULE-425: A session idle for more than 10 hours is ended and purged; a request with it is answered 401
**Category:** Lifecycle
**Priority:** P1
**Source:** user answer "空闲10h+上限24h" (intent `2026-10-14-050-规则评审第三批-identity.md`, RULE-331/332); `process/1a/登录与会话.md` T8 and D-1a-41 (server session, idle 10 hours, absolute 24 hours, both configurable); glossary `session`. Tests: `SessionPurgeClockIT#anIdleSessionIsStillPurged`
**Plain English:** A session whose last use was more than 10 hours ago is removed by the purge job and can no longer be used. The purge judges expiry on the session store's own time basis, not the application clock.
**Specification:**
- Given a session created and last used 11 hours ago whose expiry was 1 hour ago, When the purge runs once, Then it removes 1 session, a request with that session to `/api/v1/platform/sessions:current` answers 401, and a `platform.identity.session.expired` audit entry for the account carries `cause=IDLE`.
**Note:** the audit action name and detail text are an implementation contract and are not part of this rule.
**Confidence:** High

### RULE-426: With single-operator mode on, an operations command may omit the witness; the operator stays required and a relaxation mark is written with the normal audit entry
**Category:** Policy
**Priority:** P1
**Source:** ADR-0030 decision 1 item 7 (start-up WARN naming ADR-0030, `single-operator-mode.enabled` audit with no actor, a mark per relaxation) and decision 2 table row 1 (witness omitted, audit records `witness=none`, the mark written in the same transaction with the same subject); ADR-0030 decision 3 row 4 (revoke-role). Tests: `SingleOperatorEndToEndIT#p222_grantRoleWithoutWitnessEndToEnd`, `SingleOperatorMarkTransactionIT#p202_committedTogether`, `SingleOperatorRevokeRoleIT#p219_witnessMayBeLeftOutWithTheModeOn`
**Plain English:** On a development environment started with the mode on, grant-role and revoke-role run with only an operator. The normal audit entry says `witnessId=none`, and a second, mark entry records that the witness rule was relaxed; both belong to the same subject and are committed together.
**Specification:**
- Given the mode on (profiles identity-operations and dev, database forcitis_one_dev, user one_dev_app), When a child process runs grant-role of business_admin with `--operator` and no witness, Then it exits 0 by itself, prints `role granted`, the person holds business_admin, exactly one start-up WARN line names ADR-0030, and one `single-operator-mode.enabled` audit entry with no actor exists for this start.
- Given the same grant, Then the `operations.account.role.granted` entry has detail `operatorId=<operator> witnessId=none role=business_admin` with the account as subject, and exactly one `single-operator-mode.applied` entry for that account has detail `relaxation=WITNESS_OMITTED` and the same subject kind.
- Given the mode on and a revoke-role without a witness for a held business_admin, Then the exit code is 0, the role is gone, the revoked entry has the same detail format with `witnessId=none`, and the marks (2 in total with the grant) share the subject of the revoked entry.
**Confidence:** High

### RULE-427: With single-operator mode off, the emergency-account minimum is two and the warning says so
**Category:** Validation
**Priority:** P0
**Source:** ADR-0027 decision 1 items 1 and 8 (at least two emergency accounts bound to different super_admin holders; daily reconciliation alerts below two); ADR-0030 decision 2 table row 2 (relaxed only with the mode on, otherwise unchanged). Tests: `SingleOperatorEmergencyMinimumIT#p212_oneUsableAccountStillAlertsWithTheModeOff`, `SingleOperatorEmergencyMinimumIT#p212_noneAlertsAndTwoPassWithTheModeOff`, `SingleOperatorEmergencyMinimumIT#p213_theWarningTextIsKeptWithTheModeOff`
**Plain English:** In normal operation one usable emergency account is a problem. The daily check reports it, and registering an account prints a warning that at least two, bound to different super_admin holders, are required.
**Specification:**
- Given mode off and one usable emergency account, When the daily reconciliation runs, Then it returns one problem containing `only 1 usable emergency account(s)` and `at least 2 are required`, and one check-failed audit entry is written.
- Given mode off and no emergency account, Then the one problem says `only 0 usable` and `at least 2 are required`; given two usable accounts, Then there is no problem.
- Given mode off, When an emergency account is registered (and when the same login name is registered again), Then the output contains the line `WARNING: only 1 usable emergency account(s); at least 2, bound to different super_admin holders, are required`; with the mode on no WARNING line contains `at least 2`.
**Confidence:** High

### RULE-428: With single-operator mode on, one usable emergency account is enough; the reconciliation and the registration prompt use a minimum of one
**Category:** Policy
**Priority:** P1
**Source:** ADR-0030 decision 2 table row 2 (daily reconciliation and registration prompt floor lowered to 1; zero usable still alerts). Tests: `SingleOperatorEmergencyMinimumIT#p211_oneUsableAccountIsEnoughWithTheModeOn`, `SingleOperatorEmergencyMinimumIT#p211_noneUsableAlertsWithTheModeOn`, `SingleOperatorEmergencyMinimumIT#p213_registrationPromptFollowsTheMode`, `SingleOperatorEmergencyMinimumIT#p213_alreadyRegisteredPromptFollowsTheMode`
**Plain English:** On the development environment with the mode on, a single usable emergency account passes the daily check and the registration output no longer asks for two; having none still alerts and says at least 1 is required.
**Specification:**
- Given mode on and one usable emergency account, When the daily reconciliation runs, Then no problem is returned and no check-failed entry is written.
- Given mode on and no emergency account, Then one problem containing `only 0 usable emergency account(s)` and `at least 1 are required`, not `at least 2`, and one check-failed entry.
- Given two super_admin holders, one registered with the mode off and one with the mode on without a witness, Then the first output contains `WARNING: only 1 usable emergency account(s)` and `at least 2`, the second does not contain `at least 2`, and apart from the warning and the credentials shown the two outputs are identical.
- Given an already registered login name registered again, Then the output says `already registered` and contains `at least 2` with the mode off and not with the mode on.
**Confidence:** High

### RULE-429: An emergency account is usable only while the account, its bound person's account and the bound person's super_admin role are all in force; the mode never relaxes this
**Category:** Validation
**Priority:** P0
**Source:** ADR-0027 decision 1 items 2 and 8 (login requires the emergency account enabled and the bound account enabled and currently holding super_admin; the daily reconciliation alerts when an account's bound person no longer holds super_admin); ADR-0030 decision 2 table row 2 (zero usable, or a bound person who no longer holds super_admin, or a disabled account, still alerts). Tests: `SingleOperatorEmergencyMinimumIT#p211_boundPersonLostSuperAdminAlerts`, `SingleOperatorEmergencyMinimumIT#p211_boundPersonAccountDisabledAlerts`, `SingleOperatorEmergencyMinimumIT#p211_theAccountItselfDisabledAlerts`, `SingleOperatorEmergencyMinimumIT#p211_aBrokenSecondAccountStillAlertsWithTheModeOn`
**Plain English:** An emergency account that cannot actually be used is not counted. Losing super_admin, a disabled bound account and a disabled emergency account each leave zero usable accounts and raise the alert even with the mode on; a broken second account still raises its own alert naming the account's user.
**Specification:**
- Given mode on and the only emergency account's bound person has lost super_admin, Then the problems include `no longer holds super_admin` and `only 0 usable`, and one check-failed entry is written.
- Given the bound person's account is DISABLED, or the emergency account itself is disabled by disable-emergency-admin, Then the reconciliation reports a problem (for the disabled emergency account: `only 0 usable` and `at least 1 are required`).
- Given mode on, two emergency accounts and the second's bound person lost super_admin, Then exactly one problem is reported, it contains `no longer holds super_admin` and names the second account's user id.
**Confidence:** High

### RULE-430: An emergency account can be registered or enabled only for a person who holds super_admin and has an enabled account, in every mode
**Category:** Validation
**Priority:** P0
**Source:** ADR-0027 decision 1 item 1 (each emergency account is bound to an account that currently holds super_admin); ADR-0030 decision 2 table row 2 ("remains": an emergency account can only be bound to the current super_admin holder). Tests: `SingleOperatorEmergencyMinimumIT#p214_bindingStaysRestrictedToSuperAdminHolders`, `SingleOperatorEmergencyMinimumIT#p214_enableStillNeedsTheBinding`
**Plain English:** Single-operator mode lowers numbers, not eligibility. Registering an emergency account for someone without super_admin, without an account, or with a disabled account is refused with PLATFORM_IDENTITY_EMERGENCY_BINDING_INVALID, and enabling an emergency account is refused when its bound person no longer holds super_admin.
**Specification:**
- Given mode on, an employee holding only the employee role, an employee holding super_admin whose account is DISABLED, and an employee id with no record (404), When register-emergency-admin is run for each without a witness, Then each exits 1 with PLATFORM_IDENTITY_EMERGENCY_BINDING_INVALID, no identity row changes and no relaxation mark is written.
- Given a registered then disabled emergency account whose bound person lost super_admin and mode on, When enable-emergency-admin is run, Then exit code 1 with that error code and no mark.
**Confidence:** High

### RULE-431: Each time the single-operator relaxation of the emergency-account minimum applies, exactly one mark is written; when it does not apply, none
**Category:** Policy
**Priority:** P1
**Source:** ADR-0030 decision 2 (every relaxation writes a separate mark audit entry `platform.identity.single-operator-mode.applied` whose detail is the relaxation code; marks are added only when a rule was actually relaxed). Tests: `SingleOperatorEmergencyMinimumIT#p211_oneUsableWithTheModeOnWritesExactlyOneMark`, `SingleOperatorEmergencyMinimumIT#p211_otherCasesWriteNoMark`
**Plain English:** The mark says "this check passed only because of the relaxation". It is written when the mode is on and exactly one account is usable, and never in other cases.
**Specification:**
- Given mode on and exactly one usable emergency account, When the daily reconciliation runs, Then no problem is returned and exactly one `single-operator-mode.applied` entry exists whose detail is `relaxation=EMERGENCY_ACCOUNT_MINIMUM_ONE`.
- Given mode on with zero usable accounts, or with two, Then no mark is written; given mode off with two, one, or no usable accounts, Then no mark is written.
**Confidence:** High

### RULE-432: revoke-role takes a held role away, ends all sessions of the person, clears the permission cache and is audited
**Category:** Lifecycle
**Priority:** P1
**Source:** ADR-0030 decision 3 row 4 and the implementation list ("revoke-role: take the role back, revoke sessions, write audit"); ADR-0034 decision 2 item 4 (`OPERATIONS` cause of a closed grant); ADR-0027 impact section (a grant taken back by an application command also revokes sessions and clears the cache); ADR-0026 decision 6 item 6 (session revocation immediate). Tests: `SingleOperatorRevokeRoleIT#p217_revokingAHeldRole`
**Plain English:** Taking a role back by command leaves the person's other roles alone, ends every session of that person but not other people's, evicts the permission cache of the account, publishes the role-change event and writes an audit entry with the same detail format as grant-role.
**Specification:**
- Given a person holding employee and business_admin with sessions, and another person with sessions, When revoke-role business_admin is run with operator and witness, Then exit code 0, output `role revoked`, the person no longer holds business_admin but still holds employee, the person's sessions number 0, the other person's sessions are unchanged, the cache of that account is evicted, an account-roles-replaced event without that role is published, and one `operations.account.role.revoked` entry has detail `operatorId=<operator> witnessId=<witness> role=business_admin` with subject kind USER_ACCOUNT and the account id; no relaxation mark is written.
**Confidence:** High

### RULE-433: revoke-role refuses an unknown role or a person without an account and opens no account; a role that is not held is reported as nothing changed
**Category:** Validation
**Priority:** P1
**Source:** ADR-0030 decision 3 row 4 (revoke-role is the counterpart of grant-role); ADR-0033 decision 3 item 6 (only grant and register commands open an account; ids are canonical). No ADR states the outcome for a role that is not held, so this card asks. Tests: `SingleOperatorRevokeRoleIT#p218_revokingARoleThatIsNotHeldChangesNothing`, `SingleOperatorRevokeRoleIT#p218_unknownRoleCodeIsRefused`, `SingleOperatorRevokeRoleIT#p218_noAccountIsRefusedAndNoneIsOpened`
**Plain English:** The command never creates anything. Unknown role code and no account are refused; a role the person does not hold is a harmless no-op that says nothing changed and leaves no audit, no mark and no event.
**Specification:**
- Given mode on and a person holding org_data_admin, When revoke-role business_admin is run without a witness, Then exit code 0, the output contains `nothing changed`, no revoked audit entry, no mark and no role-change event is added, the total audit count is unchanged and org_data_admin is still held.
- Given a role code that does not exist, Then exit code 1 with PLATFORM_IDENTITY_ROLE_NOT_FOUND and no identity row changes.
- Given an employed employee with no account, or an employee that does not exist, Then exit code 1 and no account is opened (the user-accounts table stays empty); a missing `--role-code` is refused with a non-zero exit and a missing `--employee-id` is wrong usage (exit code 2).
**Needs decision:** when a role is not held, should revoke-role end silently with exit code 0 and write nothing (as pinned), or refuse like an unknown role so that an operator notices a wrong target?
**Confidence:** Medium

### RULE-434: A sensitive role is taken back by the operations command in either mode, with no authorization request
**Category:** Policy
**Priority:** P1
**Source:** ADR-0030 decision 3 row 4 (taking back a sensitive role normally needs an authorization request in the application; the operations command is the dev single-operator route and the witnessed route in production); ADR-0026 decision 5 item 5 (grant or take back sensitive permission through the operations command). Tests: `SingleOperatorRevokeRoleIT#p219_sensitiveRolesCanBeRevokedByTheCommand`
**Plain English:** security_auditor and super_admin can be taken back with revoke-role, with a witness in normal mode and without one in single-operator mode; no authorization request is created.
**Specification:**
- Given two people holding security_auditor and super_admin, When revoke-role security_auditor is run for the first with operator and witness (mode off), and revoke-role super_admin for the second without a witness (mode on), Then both exit 0, the first no longer holds security_auditor but still holds super_admin, the second no longer holds super_admin but still holds security_auditor, and the authorization-requests table has 0 rows.
**Confidence:** High

### RULE-435: The separation of a super_admin or security auditor holder is held until a retained person releases it; the person leaving cannot; ordinary separations are never held
**Category:** Policy
**Priority:** P0
**Source:** ADR-0055 decision 3 item 3 (the administrator-separation hold stays, carried by the local separation command; release handles only that separation) and item 4 (retained: employment on the local record and not in a pending separation; account enabled, current role); ADR-0027 decision 1 item 7 and Q-3 (hold and ask a person to confirm when the separation involves a super_admin or security auditor holder); ADR-0028 decision 4 (the releaser must be retained and not among those leaving; one person is enough); ADR-0030 decision 2 table row 3 (the hold condition and releaser conditions do not change with the mode). Tests: `SingleOperatorSeparationReasonIT#p216_theSoleSuperAdministratorSeparatingIsHeldInBothModes`, `SingleOperatorSeparationReasonIT#p216_ordinarySeparationsAreNotHeldInEitherMode`
**Plain English:** When the only super_admin leaves, the separation waits for someone who stays. A scheduled run has no releaser; the leaver cannot release their own separation; a retained security auditor can. Separating an ordinary employee is not held, and the answer is the same in both modes.
**Specification:**
- Given the only super_admin (employee 1) and a security auditor (employee 5), When employee 1 separates, in each mode, Then the verdict lists employee 1 as a separated administrator; with no releaser the actor is not retained; with employee 1 as releaser the actor is not retained; with employee 5 as releaser the actor is retained and the remaining administrators number 0.
- Given a super_admin and an ordinary employee, When the ordinary employee separates, in each mode, Then the separated administrators are empty and the reason is empty.
**Confidence:** High

### RULE-436: The held-separation reason states a minimum of two usable super_admin holders, or one with single-operator mode on; nothing else about the verdict changes
**Category:** Calculation
**Priority:** P1
**Source:** ADR-0055 decision 3 item 4 (fewer than 2 usable super_admin holders keeps the hold; 0 after release only through a two-person operations command, 1 needs an alert; the non-production exception of ADR-0030 is not extended to production); ADR-0030 decision 2 table row 3 (only the minimum in the reason text is lowered to 1; the hold condition, the releaser and the release result are unchanged) and guard 6 (`AdministratorSeparationReviewTest`). Tests: `SingleOperatorSeparationReasonIT#p215_onlyTheMinimumInTheReasonDiffers`, `SingleOperatorSeparationReasonIT#p215_oneRemainingIsAShortageOnlyWithTheModeOff`, `SingleOperatorSeparationReasonIT#p215_enoughRemainingGivesTheSameReason`
**Plain English:** The reason shown to the releaser names the shortage against the minimum in force: two normally, one in single-operator mode. Who is separating, whether the releaser is retained, the remaining count and the session mode are identical in both modes.
**Specification:**
- Given two super_admin holders both separating (0 remain), Then with the mode off the reason contains `only 0 usable super_admin holder(s) would remain, at least 2 are needed`; with the mode on it contains the same sentence with `at least 1 are needed` and not `at least 2`; replacing `at least 1` by `at least 2` in the on-text gives the off-text exactly.
- Given two holders and one separating (1 remains), Then with the mode off the reason contains `only 1 usable super_admin holder(s) would remain, at least 2 are needed`; with the mode on it does not contain `at least 2`, contains `another holder must confirm`, and the separation is still held for a person.
- Given three holders and one separating, Then the verdicts of the two modes are equal and the reason contains `1 separated person(s)` and `another holder must confirm` but not `would remain`.
**Confidence:** High

**New terms:** operations command (运维命令, the command-line operation family grant-role, revoke-role, register-emergency-admin and so on, domain, `platform.identity`); operator (操作人 of an operations command, distinct from `actor` in `platform.audit`, domain, `platform.identity`); usable emergency account (可用应急管理员, an emergency account that is enabled, whose bound account is enabled and still holds super_admin, domain, `platform.identity`); relaxation mark (放宽标注, the `single-operator-mode.applied` audit entry, domain, `platform.identity`; the glossary has the relaxation but not the mark); administrator-separation hold (管理员离职暂停, ADR-0028 `separation-hold`, domain, `platform.identity` / `organization.workforce`; glossary has only `sync_run_held` and `retained`); canonical employee id (规范员工标识, uuid text form accepted by `--employee-id`, shared kernel). Glossary note: `single_operator_relaxation` lists four values while ADR-0072 makes five, and the retired synchronisation terms (`org_snapshot`, `sync_run_held`) still appear in the sources of RULE-420 and RULE-435; for one-glossary.
