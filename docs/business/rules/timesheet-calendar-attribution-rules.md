# Timesheet, work calendar, legal entity attribution, audit and numbering business rules — RULE-473..RULE-491

Scope: behaviors of the new system asserted by existing tests that had no rule card: filing and lifecycle of a timesheet (`cost.timesheet`), the make-up workday of the work calendar (`organization.attendance`), legal entity attribution and the organization unit list (`organization.workforce`), the audit trail and a person's own login records (`platform.audit`), and the numbering rule versions and legacy numbers (`platform.numbering`).
Basis: ADR-0010, ADR-0026, ADR-0027, ADR-0033, ADR-0036; the work cards `docs/architecture/implementation-cards/ts-uniq.md`, `NUM-VERSION-ID.md`, `K-3b-4.md`; the repair card `docs/data/repair-cards/data-repair-timesheet-audit.md` (TA-01, TA-02, TB-08) and `DATA-REPAIR-approval.md` (AP-09); RULE-385, RULE-388..RULE-392, RULE-397..RULE-405. No rule here comes from the legacy system's code.

| ID | Domain | Title | Priority | Review |
|---|---|---|---|---|
| RULE-473 | timesheet | The work date is the calendar day the employee entered, not after today in the Shanghai business day, and never rewritten | P0 | No |
| RULE-474 | timesheet | A new timesheet keeps the numbering rule version its number was issued under; a migrated timesheet has none and is still readable | P1 | No |
| RULE-475 | timesheet | Round number and submitted date are zero and empty together; a timesheet issued here is not in a submitted status with round zero | P1 | No |
| RULE-476 | timesheet | An approval outcome (approved, returned, rejected) never changes the round number or the submitted date | P1 | No |
| RULE-477 | timesheet | Cancelling the approval of a submitted timesheet turns it back into a draft and announces TimesheetApprovalWithdrawn | P0 | No |
| RULE-478 | timesheet | A timesheet whose approval round has ended but whose outcome is not yet applied cannot be submitted again | P1 | No |
| RULE-479 | timesheet | A timesheet belongs to the employee who filed it; anyone else gets the same "not found" as for a timesheet that does not exist | P0 | No |
| RULE-480 | timesheet | A submission naming an unknown or separated project manager is refused and nothing is stored | P1 | Yes |
| RULE-481 | organization | An employee's legal entity on a day is their own attribution from its start day, else the nearest attributed department up the tree | P0 | No |
| RULE-482 | organization | When no legal entity applies on the day the lookup fails and the submission is refused; a default is never answered | P0 | No |
| RULE-483 | organization | Legal entity attribution is maintained only by the maintainer; a start day before the existing period, an unknown unit or an unknown legal entity is refused | P1 | No |
| RULE-484 | organization | The organization unit list shows parents first and leaves out revoked units unless asked | P2 | Yes |
| RULE-485 | attendance | A make-up workday must fall on a Saturday or a Sunday, whatever the session time zone; a holiday may fall on any day | P1 | No |
| RULE-486 | audit | An audit entry carries a login method only together with an outcome | P2 | No |
| RULE-487 | audit | A person's own login records contain only their own login, failure and logout entries, with five fields, and no cursor reaches anyone else's | P0 | No |
| RULE-488 | numbering | A legacy number is unique within its source system and document type, and is never changed or deleted | P1 | No |
| RULE-489 | numbering | A confirmed numbering rule version always names its confirmer; a pending one need not | P0 | No |
| RULE-490 | numbering | The author of a numbering rule version cannot confirm it; another holder of the approve permission can | P0 | No |
| RULE-491 | numbering | Each business date is numbered under the confirmed rule version in force on that date | P0 | No |

### RULE-473: The work date is the calendar day the employee entered, not after today in the Shanghai business day, and never rewritten
**Category:** Validation
**Priority:** P0
**Source:** `docs/architecture/implementation-cards/ts-uniq.md` (time zone section, invariant table: work date after creation not changed, work date not after today); intent `2026-10-16-052923-one-coordinator-工时单唯一与填报义务-我的想法是.md` (one timesheet per employee per day, non-workdays included); CC-T01 business zone `Asia/Shanghai`. Tests: `TimesheetEmployeeDayUniqueIT#workDateIsACalendarDayAndTodayIsTheShanghaiDay`, `TimesheetApprovalScenarioIT#workDateAndTheNumberAndDepartmentAreStored`, `TimesheetStateMachineTest#theWorkDateIsEnteredAndKept`
**Plain English:** The work date is the day the employee says the hours belong to; it is stored as a calendar day with no time zone conversion. It may not be later than today, where today is the business day in Shanghai. Once the timesheet exists the work date is fixed: a revision or a late resubmission does not move the hours to another day, and the submission day is recorded separately.
**Specification:**
- Given the clock is 2026-11-02 15:59 UTC (still 2026-11-02 in Shanghai), When an employee submits a timesheet for 2026-11-03, Then it is refused with COST_TIMESHEET_WORK_DATE_IN_FUTURE.
- Given the clock is 2026-11-02 16:30 UTC (2026-11-03 in Shanghai), When the same submission is made, Then it is accepted and the stored work date is 2026-11-03; a work date of 2026-11-04 is still refused.
- Given a timesheet for tomorrow, Then it is refused and no row and no number is stored; given a timesheet for today, Then the work date is today.
- Given a returned timesheet, When its work date is revised to one day later, Then it is refused with COST_TIMESHEET_WORK_DATE_FIXED; When only the hours are revised (7.5) and it is submitted three days later, Then the work date is unchanged, the submitted day is that later day, and the accounting period is the period of the work date.
- Given a submitted timesheet, Then it keeps its number, the employee's department, the legal entity of the work date and the submitted day (today); its period is the accounting period containing the work date.
**Confidence:** High

### RULE-474: A new timesheet keeps the numbering rule version its number was issued under; a migrated timesheet has none and is still readable
**Category:** Policy
**Priority:** P1
**Source:** ADR-0033 item 1 point 5 (users of a number refer to the version row id); `phase1a/cards/NUM-VERSION-ID.md`; `docs/architecture/implementation-cards/ts-uniq.md` (`numbering_rule_version_id` nullable, kept for migrated rows; a new path that cannot get the version fails with an alert and writes no empty value). Tests: `TimesheetNumberingRuleVersionIT#aNewTimesheetKeepsTheRuleVersionItsNumberWasIssuedUnder`, `TimesheetNumberingRuleVersionIT#aMigratedRowWithoutARuleVersionCanStillBeRead`
**Plain English:** When a timesheet is created here, the numbering rule version in force that issued its number is recorded on the timesheet. A timesheet migrated in from elsewhere was not numbered by this system, so it has no rule version, and that is allowed; it is still read like any other.
**Specification:**
- Given an employee submits a new timesheet, When the numbering service issues one number, Then the stored rule version equals the confirmed version that issued it, and the timesheet is SUBMITTED.
- Given a migrated APPROVED timesheet with no rule version, When its owner reads it, Then it is returned with status APPROVED and no rule version.
**Confidence:** High

### RULE-475: Round number and submitted date are zero and empty together; a timesheet issued here is not in a submitted status with round zero
**Category:** Validation
**Priority:** P1
**Source:** `docs/data/repair-cards/data-repair-timesheet-audit.md` TA-01 (round zero if and only if no submitted date; a submitted status needs round of at least 1); `docs/architecture/implementation-cards/ts-uniq.md` (status set; a cancelled approval returns to DRAFT and keeps round and submitted date). Tests: `TimesheetRoundSubmittedCheckIT#roundAndSubmittedDateMustBeZeroAndEmptyTogether`, `TimesheetRoundSubmittedCheckIT#aNewNonDraftTimesheetWithRoundZeroIsRefused`, `TimesheetRoundSubmittedCheckIT#draftsInBothShapesCanBeInserted`, `TimesheetRoundSubmittedCheckIT#aMigratedTimesheetWithoutARuleVersionCanBeInsertedButOneWithAVersionCannot`
**Plain English:** A timesheet has been submitted exactly when it has a round of at least 1 and a submitted date. A draft may be in either shape: never submitted (round 0, no date) or sent back to draft after its approval was cancelled (round of 1 or more, with a date). A timesheet issued by this system cannot be in SUBMITTED, RETURNED, APPROVED or REJECTED with round 0; a migrated one, which has no rule version, is exempt.
**Specification:**
- Given round 1 and no submitted date, or round 3 and no date, or round 0 with a date, When a row is inserted, Then it is refused by ck_timesheets_round_submitted and no row is stored.
- Given a DRAFT with round 0 and no date, or a DRAFT with round 2 and a submitted date, Then both are accepted.
- Given status SUBMITTED, RETURNED, APPROVED or REJECTED with round 0, no date and a rule version, Then it is refused by ck_timesheets_round_after_submit (SQLState 23514).
- Given APPROVED with round 0, no date and no rule version (migrated), Then it is accepted; with a rule version present, Then it is refused.
**Confidence:** High

### RULE-476: An approval outcome never changes the round number or the submitted date
**Category:** Lifecycle
**Priority:** P1
**Source:** `docs/data/repair-cards/data-repair-timesheet-audit.md` TA-01 (acceptance: submit, return, withdraw and re-submit after rejection all pass); `docs/architecture/implementation-cards/ts-uniq.md` (state table, rejected timesheet can be filed again). Tests: `TimesheetRoundSubmittedCheckIT#approvalResultsDoNotTouchRoundOrSubmittedDate`
**Plain English:** The round and the submitted date are set when the employee submits. When the approval ends, whether approved, returned or rejected, the outcome is written to the status only and both facts stay as they were.
**Specification:**
- Given a timesheet submitted once (round 1) whose submitted date is then set to an earlier day, When the approval is approved, Then the status is APPROVED, the round is 1 and the submitted date is the earlier day.
- Given the same setup, When the approval is rejected under the default process, Then the status is RETURNED with round 1 and the same date; under the variant with a cut-off, Then the status is REJECTED with round 1 and the same date.
**Confidence:** High

### RULE-477: Cancelling the approval of a submitted timesheet turns it back into a draft and announces TimesheetApprovalWithdrawn
**Category:** Lifecycle
**Priority:** P0
**Source:** `docs/architecture/implementation-cards/ts-uniq.md` (state table: SUBMITTED to DRAFT by cancelling the approval; no cancelled status; event renamed from TimesheetCancelled); `glossary/b2/GL_RENAME_CARDS.md` B2-B07; intent `2026-10-16-052923-one-coordinator-工时单唯一与填报义务-我的想法是.md` (after a rejection or withdrawal the original is edited and submitted again, no terminal cancel). Tests: `TimesheetRejectedResubmitIT#cancellingTheApprovalTurnsASubmittedTimesheetBackIntoADraft`
**Plain English:** The filer can cancel the approval of a timesheet that is under approval. The timesheet becomes a draft again, the event published is cost.timesheet.TimesheetApprovalWithdrawn, and no event with the old name is published.
**Specification:**
- Given a timesheet submitted and in approval round 1, When its filer cancels the approval with a reason, Then the status becomes DRAFT and one event of type cost.timesheet.TimesheetApprovalWithdrawn for that document is published; its type does not contain "TimesheetCancelled".
**Confidence:** High

### RULE-478: A timesheet whose approval round has ended but whose outcome is not yet applied cannot be submitted again
**Category:** Lifecycle
**Priority:** P1
**Source:** `phase1a/review/REVIEW_b12f1df.md` finding M-2 (answer 409 COST_TIMESHEET_RESULT_PENDING when the approval status and the timesheet disagree), closed in `phase1a/review/REVIEW_integration.md`; `docs/architecture/implementation-cards/ts-uniq.md` (state table). Tests: `TimesheetApprovalScenarioIT#resubmittingBeforeTheResultArrivedIsRefused`
**Plain English:** If the approval of a timesheet has already ended but the timesheet has not yet received the outcome, the filer cannot submit it again; the answer says the result is pending (409).
**Specification:**
- Given a timesheet submitted and its approval round ended (4 hours), When the filer withdraws it and submits again before the outcome has been applied to the timesheet, Then the submission is refused with COST_TIMESHEET_RESULT_PENDING.
**Confidence:** Medium

### RULE-479: A timesheet belongs to the employee who filed it; anyone else gets the same "not found" as for a timesheet that does not exist
**Category:** Policy
**Priority:** P0
**Source:** `docs/architecture/implementation-cards/ts-uniq.md` (only the filer can change, submit, withdraw and cancel; the employee id is the signed-in employee); `phase1a/review/REVIEW_integration.md` M-12 (a non-participant gets a uniform 404 and fixed error text); `phase1a/review/REVIEW_fix-b3.md` (out of scope reads answer 404). Tests: `TimesheetOwnerAccountsIT#aNewTimesheetIsFiledUnderTheSignedInEmployee`, `TimesheetApprovalScenarioIT#foreignAndMissingTimesheetsAreIndistinguishable`
**Plain English:** A timesheet is filed under the signed-in employee. Its filer can read it. Another employee reading or cancelling it receives exactly the answer given for an id that does not exist, so its existence is not revealed.
**Specification:**
- Given employee A submits a new timesheet, When A reads it, Then it is returned with status SUBMITTED; When employee B reads it, Then B gets COST_TIMESHEET_NOT_FOUND.
- Given a stranger who is not the filer, When they read or cancel A's timesheet and, separately, a timesheet that does not exist, Then all four answers carry COST_TIMESHEET_NOT_FOUND and the same message text.
**Confidence:** Medium

### RULE-480: A submission naming an unknown or separated project manager is refused and nothing is stored
**Category:** Validation
**Priority:** P1
**Source:** RULE-388 (a project line is approved by the project manager of that line's project); no spec found for the refusal itself. Tests: `TimesheetApprovalScenarioIT#unknownProjectManagerIsRefused`
**Plain English:** The project manager named on a submission must be a known employee still in service; otherwise the submission is refused with its own error code and no timesheet row or number is created.
**Specification:**
- Given the filer submits 1 hour and names a project manager who is not a known employee, When the submission is made, Then it is refused with COST_TIMESHEET_PROJECT_MANAGER_UNKNOWN and the row count for that timesheet id is 0.
**Needs decision:** Is a separated (left) project manager also refused at submission, or should the submission be routed to someone else, and is this the intended own error code?
**Confidence:** Low

### RULE-481: An employee's legal entity on a day is their own attribution from its start day, else the nearest attributed department up the tree
**Category:** Calculation
**Priority:** P0
**Source:** ADR-0026 item 2 point 5 (department to legal entity by default, children inherit and can be overridden on a subtree; an individual's own attribution takes precedence; value is taken by date; the current user's legal entity is resolved for today from the employee instead of a fixed value); RULE-385. Tests: `LegalEntityAttributionIntegrationTest#resolution`, `TimesheetOwnerAccountsIT#theCurrentUsersLegalEntityIsTheirEmployeesAttribution`
**Plain English:** The legal entity of an employee on a given day comes from the nearest department attributed on that day, going up the tree; a subtree attribution overrides the ancestor's. An attribution set for the employee takes precedence from its start day. For the signed-in user the employee is the one behind the account, and the legal entity is that employee's, not looked up by the account id.
**Specification:**
- Given the root department is attributed to legal entity 1 and employee 2 sits below it, Then employee 2 and an employee of the root both resolve to legal entity 1.
- Given a child subtree is then attributed to legal entity 2, Then employee 2 resolves to legal entity 2 and the root employee still to legal entity 1.
- Given employee 2 is given an own attribution to legal entity 1 starting tomorrow, Then today employee 2 still resolves to legal entity 2 and from tomorrow to legal entity 1; three legal entity attributed events were published in all.
- Given two signed-in accounts, Then each current user has the employee behind their account and the legal entity of that employee's attribution.
**Confidence:** High

### RULE-482: When no legal entity applies on the day the lookup fails and the submission is refused; a default is never answered
**Category:** Validation
**Priority:** P0
**Source:** `docs/architecture/implementation-cards/ts-uniq.md` (O13: the legal entity is resolved for the work date; when missing it fails, no default); ADR-0026 item 2 point 5 (a day with no legal entity resolves to none). Tests: `LegalEntityAttributionIntegrationTest#unresolvedFails`, `TimesheetApprovalScenarioIT#missingLegalEntityIsRefused`
**Plain English:** If no department or individual attribution applies on the day, or the employee is unknown, the lookup fails with ORGANIZATION_WORKFORCE_EMPLOYEE_LEGAL_ENTITY_UNRESOLVED; no default legal entity is substituted. A timesheet submission that gets this refusal stores nothing.
**Specification:**
- Given no attribution exists, When the legal entity of employee 2 is asked for today, Then the lookup fails with ORGANIZATION_WORKFORCE_EMPLOYEE_LEGAL_ENTITY_UNRESOLVED.
- Given a department attributed today, When the day before is asked, or an unknown employee is asked, Then the lookup fails; asked for today, Then it answers the attributed legal entity.
- Given the legal entity lookup for the work date fails, When the employee submits 1 hour, Then the refusal is the lookup's and no timesheet row is stored.
**Confidence:** High

### RULE-483: Legal entity attribution is maintained only by the maintainer; a start day before the existing period, an unknown unit or an unknown legal entity is refused
**Category:** Policy
**Priority:** P1
**Source:** ADR-0026 item 2 point 5 (maintained by the organization data administrator, periods do not overlap); RULE-385; `docs/business/rules/data-architecture-rules.md` DATA-RULE-03 (the legal entity version is as of the day of record, no backdating in 1a). Tests: `LegalEntityAttributionIntegrationTest#endpoints`
**Plain English:** A holder of org-unit read can read the attribution periods of a unit but not change them; changes need the legal entity maintainer. An unknown unit is not found, an unknown legal entity is unprocessable, a new period that starts before the existing one is unprocessable, and an attribution can be ended on a given day.
**Specification:**
- Given unit 1 attributed to legal entity 1 from today, When a reader asks, Then 200 with one period whose legal entity is legal entity 1 and which has no end day.
- When a reader tries to set legal entity 2 from tomorrow, Then 403; When the maintainer sets an attribution for unknown unit 404, Then 404; for an unknown legal entity, Then 422; for legal entity 2 starting 5 days ago, Then 422.
- When the maintainer ends the attribution with effectiveTo ten days from today, Then 200 and the period shows that end day.
**Confidence:** Medium

### RULE-484: The organization unit list shows parents first and leaves out revoked units unless asked
**Category:** Policy
**Priority:** P2
**Source:** none found for the default of hiding revoked units; ADR-0026 section 1 table (departments visible by scope). Tests: `WorkforceHttpIntegrationTest#orgTree`
**Plain English:** The list of organization units gives every parent before its children with the ids as text; units that are revoked are left out unless the caller asks for them, in which case they show with status REVOKED.
**Specification:**
- Given four units of which unit 4 is revoked, When a reader lists units, Then 3 are returned: unit 1 (no parent) first, unit 2 (parent unit 1, with its head) next, and the third ACTIVE.
- When the list is requested with includeRevoked=true, Then 4 are returned and unit 4 has status REVOKED.
**Needs decision:** Should revoked organization units be hidden from the default list, and is the listing order "parents first" a required behavior?
**Confidence:** Low

### RULE-485: A make-up workday must fall on a Saturday or a Sunday, whatever the session time zone; a holiday may fall on any day
**Category:** Validation
**Priority:** P1
**Source:** `docs/data/repair-cards/data-repair-timesheet-audit.md` TA-02 (a make-up workday must fall on Saturday or Sunday; Saturday and Sunday pass, a Wednesday is refused); glossary terms 调休上班日 (MakeUpWorkday), 节假日 (Holiday), 工作日历 (WorkCalendar). Tests: `WorkCalendarMakeUpWeekendIT#makeUpWorkdayOnSaturdayAndSunday`, `WorkCalendarMakeUpWeekendIT#makeUpWorkdayOnAWeekdayIsRefused`, `WorkCalendarMakeUpWeekendIT#holidaysOnWeekdaysAndWeekends`, `WorkCalendarMakeUpWeekendIT#sameVerdictInAnySessionTimeZone`, `WorkCalendarMakeUpWeekendIT#wednesdayMakeUpDayOverHttpIsA422`
**Plain English:** A day marked as a make-up workday must be a Saturday or a Sunday. A holiday is accepted on any day of the week. The verdict depends only on the date, not on the time zone of the database session. Over the interface a weekday make-up day is answered with 422 and ORGANIZATION_ATTENDANCE_MAKE_UP_WORKDAY_ON_WEEKDAY, never 500.
**Specification:**
- Given 2026-10-03 (Saturday) and 2026-10-04 (Sunday), When each is entered as a make-up workday, Then both are accepted.
- Given 2026-10-05 to 2026-10-09 (Monday to Friday), When each is entered as a make-up workday, Then each is refused with SQLState 23514 and constraint ck_work_calendar_days_make_up_weekend, and no row is stored.
- Given those same five days, When entered as holidays, Then all are accepted (October 3, 4, 5, 7, 9 2026).
- Given the session time zone is Asia/Shanghai, America/Los_Angeles or Pacific/Kiritimati, Then the same verdicts hold: the 3rd and 4th pass, the 5th, 7th and 9th are refused as make-up workdays.
- Given a request to enter 2027-01-06 (Wednesday) as MAKE_UP_WORKDAY, Then 422 with ORGANIZATION_ATTENDANCE_MAKE_UP_WORKDAY_ON_WEEKDAY and no row is stored.
**Confidence:** High

### RULE-486: An audit entry carries a login method only together with an outcome
**Category:** Validation
**Priority:** P2
**Source:** `docs/data/repair-cards/data-repair-timesheet-audit.md` TB-08 (conclusion: only the one-way rule can be stored, the login method has meaning only for an entry with an outcome; the other combinations occur in the 15 writing places). Tests: `AuditArchiveAndOutcomeConstraintsIT#methodWithoutOutcomeIsRefused`, `AuditArchiveAndOutcomeConstraintsIT#otherCombinationsOfOutcomeAndMethodAreAccepted`
**Plain English:** An audit entry that names how the person logged in must also say whether it succeeded or failed. An outcome without a method, or neither, is allowed.
**Specification:**
- Given a login method of WECOM_WORKBENCH, WECOM_MINI_PROGRAM, WECOM_QR_CODE_LOGIN, SSO, SIGNED_TICKET or EMERGENCY and no outcome, When the entry is written, Then it is refused (23514, ck_audit_entries_login_method_outcome).
- Given SUCCEEDED or FAILED with no method, or neither, or both (SUCCEEDED with SSO, FAILED with EMERGENCY), Then each entry is accepted.
**Confidence:** High

### RULE-487: A person's own login records contain only their own login, failure and logout entries, with five fields, and no cursor reaches anyone else's
**Category:** Policy
**Priority:** P0
**Source:** ADR-0027 item 3 point 3 (an employee reads own login records: subject is their own account, action whitelist of login success, failure, logout and session revocation, fields only time, action, outcome, source address and login method); ADR-0033 table of superseded sentences (looked up by kind and user id, semantics unchanged); `phase1a/cards/K-3b-4.md` (key-set cursor, not signed). Tests: `OwnLoginRecordsBySubjectIT#ownRecordsAreFoundByKindAndUserId`, `OwnLoginRecordsBySubjectIT#theFieldWhitelistIsUnchanged`, `K3b4AuditPagingIT#ownRecordsCursorCannotReachOtherPeoplesRows`
**Plain English:** A person with the own-login-record read permission sees the entries whose subject is their own user account: login succeeded, failed and logged out. They do not see entries about someone else, entries of another subject kind that merely have the same id, or non-login actions. Each record shows exactly five fields. A cursor cannot be forged to reach other people's rows.
**Specification:**
- Given three own entries (succeeded, failed, logout), one roles-replaced entry, one entry of another person's account, and two entries of subject kind employee with my user id, When I list my records, Then 200 and the actions are exactly SUCCEEDED, FAILED and LOGOUT and the source addresses exactly my three; the employee-kind entries are absent; and the response contains no subjectKind, subjectId, subjectKey, actorId or detail.
- Given two people each with one login entry, Then each record has exactly the keys occurredAt, action, outcome, sourceAddress, loginMethod, and each person sees only their own address.
- Given a cursor whose entry id is replaced by another person's entry id, When it is used with limit 200, Then the answer is 200 or 400 and contains no address belonging to others.
**Confidence:** High

### RULE-488: A legacy number is unique within its source system and document type, and is never changed or deleted
**Category:** Policy
**Priority:** P1
**Source:** ADR-0010 item 5 (the old number is kept as a read-only searchable field); ADR-0033 item 2 point 7 (DA-11: unique on source system, document type, legacy number; the same number from another system may exist); ADR-0036 item 3 and `NAMING_STANDARD.md` G-MG1 (append-only); ADR-0039 item 3 (table moved to `platform_integration`, shape unchanged). Tests: `LegacyNumberSourceIT#uniquePerSourceSystem`, `LegacyNumberSourceIT#appendOnly`
**Plain English:** A legacy number is recorded once per source system and document type. The same number from another source system is a different entry. Once recorded, a legacy number cannot be updated or deleted.
**Specification:**
- Given a legacy number Q-2020-0001 from LEGACY_SYSTEM is recorded, When the same triple is recorded again, Then it is a unique violation; When the same number comes from ACCOUNTING_SYSTEM, Then it is accepted.
- Given Q-2021-0001 is recorded, When its number or its migration batch is updated, or the row is deleted, Then each change is refused.
**Confidence:** High

### RULE-489: A confirmed numbering rule version always names its confirmer; a pending one need not
**Category:** Validation
**Priority:** P0
**Source:** ADR-0027 item 2 point 4 (versions are saved, immutable, confirmed by someone other than the author); `docs/data/repair-cards/data-repair-approval.md` AP-09 (a CONFIRMED version needs a confirmer; whether a pending version must have empty confirmation columns was left open, so the check is one-way). Tests: `NumberingRowRulesIT#confirmedWithoutConfirmerRefused`, `NumberingRowRulesIT#confirmedWithConfirmerAndPendingWithoutAccepted`, `NumberingRowRulesIT#businessConfirmationOnUnconfirmedStatusStillAccepted`
**Plain English:** A numbering rule version that is CONFIRMED must have a confirmer. A PENDING_CONFIRMATION version may have none. The rule is one way: a pending or superseded version that has already received the business confirmation (finance still to confirm) is accepted.
**Specification:**
- Given a CONFIRMED version with no confirmer, with or without a confirmation time, When it is inserted, Then it is refused by ck_numbering_rule_versions_confirmed.
- Given a CONFIRMED version with a confirmer, or a PENDING_CONFIRMATION version without one, Then both are accepted.
- Given a PENDING_CONFIRMATION or a SUPERSEDED version that already holds the business confirmation, Then it is accepted.
**Confidence:** High

### RULE-490: The author of a numbering rule version cannot confirm it; another holder of the approve permission can
**Category:** Policy
**Priority:** P0
**Source:** ADR-0027 item 2 point 4 (four eyes: the confirmer is not the version's author, even with two roles); ADR-0033 item on user id comparison (confirmer against author, by user id). Tests: `NumberingRuleConfirmerIT#theAuthorOfAVersionCannotConfirmIt`
**Plain English:** When a numbering template is changed it becomes a new pending version recording its author's user account. The author cannot confirm it, even holding the approving role; a different person who holds the approve permission can.
**Specification:**
- Given the author saves a new monthly template version effective tomorrow, Then a pending version exists whose author is the author's user id.
- When the author confirms it, Then 403 with PLATFORM_NUMBERING_CONFIRMER_IS_AUTHOR.
- When another business administrator confirms it, Then 200 and the stored author and confirmer are the two different user ids.
**Confidence:** High

### RULE-491: Each business date is numbered under the confirmed rule version in force on that date
**Category:** Calculation
**Priority:** P0
**Source:** ADR-0027 item 2 point 4 (a change makes a new version pending; the confirmed version keeps issuing until the new one is confirmed); `phase1a/cards/NUM-VERSION-ID.md` NV-02 (a new version returns its own id, an older date returns the older id). Tests: `NumberingRuleVersionIdIT#eachBusinessDateGetsTheIdOfTheVersionInForceThen`
**Plain English:** When a rule gets a second confirmed version with a later effective date, a number issued for a date before that gets the first version's id and a number issued for a date from then on gets the second version's id.
**Specification:**
- Given a second version effective tomorrow is saved and confirmed, When a number is issued for today, Then the returned rule version id is the first version's; When issued for a far later date, Then it is the second version's and the two ids differ.
**Confidence:** High

**New terms:** none new in `terms.yaml`; terms not found there and used in these cards: submitted date (提交日期, column `submitted_date`), own login records (本人登录记录), login method (登录方式), login outcome (登录结果), numbering rule version confirmation (编号规则版本确认), four eyes (四眼, used in ADR-0027 text).
