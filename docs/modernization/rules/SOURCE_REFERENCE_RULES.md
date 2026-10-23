# Accounting organization code registration, source references and ticket subject business rules — RULE-460..RULE-472

Scope: platform.integration source references (`source_references`) as the new system behaves today: the manually registered accounting organization code of a legal entity (registration, four-eyes confirmation, correction, rejection, query, audit), the integrity of source reference rows, and how a signed ticket subject is resolved to a user. Basis: ADR-0033 (sections 2, 3, 5), ADR-0033 U-2 and U-3 answers (user, 2026-10-15), and the user decision to allow same-day correction (intent `2026-10-15-081242-one-coordinator-核算组织编码确认后填错是否允许同.md`). No existing rule card covers these behaviours (searched `rules/*.md`, `BUSINESS_RULES.md`). Review is Yes only where a test asserts something the ADR text does not carry.

| ID | Domain | Title | Priority | Review |
|---|---|---|---|---|
| RULE-460 | integration | Only the accounting organization code of a legal entity can be registered by hand; other categories are refused and audited | P0 | No |
| RULE-461 | integration | Registering needs the source-reference update permission, confirming needs the approve permission; the two roles are split | P0 | No |
| RULE-462 | integration | A registered accounting organization code must be a positive integer, name an existing legal entity and carry an effective date | P1 | No |
| RULE-463 | integration | The person who registered a code can never confirm it, same-day corrections included | P0 | No |
| RULE-464 | integration | Outbound delivery uses only confirmed codes; a legal entity with no confirmed code fails and alerts | P0 | No |
| RULE-465 | integration | Confirming a later-dated correction closes the old row at the new effective date in the same transaction | P0 | No |
| RULE-466 | integration | A correction effective the same day as the wrong code replaces it; one effective earlier is refused | P1 | No |
| RULE-467 | integration | A rejected registration changes nothing else and cannot be decided again | P1 | No |
| RULE-468 | integration | Confirmed rows are unique at every point in time; pending and rejected rows take no key; a taken code cannot be confirmed | P0 | No |
| RULE-469 | integration | Source references are closed, never deleted, and their source id and own id are never rewritten | P0 | No |
| RULE-470 | integration | Registration, confirmation and rejection are audited | P1 | No |
| RULE-471 | integration | The pending list and the per-legal-entity history are readable by registrars and approvers | P1 | No |
| RULE-472 | integration | A signed ticket subject is resolved to a user only through an existing source reference; without one, login is refused and audited | P0 | No |

### RULE-460: Only the accounting organization code of a legal entity can be registered by hand; other categories are refused and audited
**Category:** Policy
**Priority:** P0
**Source:** ADR-0033 section 5 item 4 (first bullet); section 2 item 1 (synchronised rows are written by sync). Tests: `com.forcitis.one.acceptance.platform.integration.SourceReferenceManualEditIT#onlyTheAccountingCodeOfALegalEntityIsEditable`, `com.forcitis.one.acceptance.platform.integration.SourceReferenceConstraintIT#accountingCodeCannotPassAsASynchronisedRow`, `com.forcitis.one.acceptance.platform.integration.SourceReferenceQueryIT#onlyTheManualCategoryIsListed`
**Plain English:** The accounting code decides which accounting-system organization a month of vouchers goes to, so only that one category (accounting system, legal entity) may be written or read through the manual interface. Every other category is written only by synchronisation or one-off import. A confirmed accounting row with no author, as a synchronisation would write it, is refused by the database.
**Specification:**
- Given the pairs (ORG_SOURCE, EMPLOYEE), (ORG_SOURCE, ORG_UNIT), (ORG_SOURCE, POSITION), (LEGACY_SYSTEM, EMPLOYEE), (LEGACY_SYSTEM, ORG_UNIT), (ACCOUNTING_SYSTEM, EMPLOYEE), (ORG_SOURCE, LEGAL_ENTITY), When a holder of both permissions registers one with source id -42, Then 422 `PLATFORM_INTEGRATION_SOURCE_OBJECT_KIND_NOT_ALLOWED`, no row is written, and each refusal is audited.
- Given the same category list, When the list interface is queried, Then 422 with the same error code; with no category given, 4xx.
- Given a row (accounting system, legal entity, code 1021) inserted directly as CONFIRMED with no author, Then a CHECK violation.
**Confidence:** High

### RULE-461: Registering needs the source-reference update permission, confirming needs the approve permission; the two roles are split
**Category:** Policy
**Priority:** P0
**Source:** ADR-0033 section 5 item 3, U-2 (user, 2026-10-15: new permission granted to the organization data administrator), U-3 (approve granted to the business administrator). Tests: `com.forcitis.one.acceptance.platform.integration.SourceReferenceManualEditIT#registeringNeedsThePermission`, `com.forcitis.one.acceptance.platform.integration.SourceReferenceManualEditIT#thePermissionsAndWhoHoldsThem`, `com.forcitis.one.acceptance.platform.integration.SourceReferenceQueryIT#listingNeedsUpdateOrApprove`
**Plain English:** `platform.integration:source-reference:update` registers, `:approve` confirms or rejects. Maintaining legal entities is not maintaining their codes. The organization data administrator role holds update; the business administrator role holds approve. Listing pending rows needs either permission.
**Specification:**
- Given a registration request, When not signed in, Then 401; When the caller holds only legal-entity update, or only approve, Then 403 and `referencesOf(legalEntity)` is 0; When the caller holds update, Then 201 and one row.
- Given the role listing, Then `org_data_admin` includes the update code and `business_admin` includes the approve code.
- Given the pending list, When unauthenticated Then 401; When the caller holds only legal-entity update Then 403; When the caller holds approve, or update, Then 200 and the waiting row is listed.
**Confidence:** High

### RULE-462: A registered accounting organization code must be a positive integer, name an existing legal entity and carry an effective date
**Category:** Validation
**Priority:** P1
**Source:** ADR-0033 section 2 item 1 (accounting organization code > 0 as a category CHECK; source id signed 64-bit for other categories), section 5 item 4. Tests: `com.forcitis.one.acceptance.platform.integration.SourceReferenceManualEditIT#invalidInputIsRefused`, `com.forcitis.one.acceptance.platform.integration.SourceReferenceConstraintIT#valueChecks`
**Plain English:** Codes 0 and negatives, non-numbers, unknown legal entities and a missing effective date are refused with no write. The database also rejects unknown source systems, categories and confirmation statuses; only the accounting-system legal-entity category is limited to positive codes, other categories may use any signed 64-bit id.
**Specification:**
- Given codes "0" and "-5", When registered for an existing legal entity, Then 422; code "twelve" gives 4xx; a non-existent legal entity gives 4xx; a missing effective date gives 422; the row count is unchanged.
- Given direct inserts, Then code 0 and -1 for (accounting system, legal entity) are CHECK violations, 1021 is accepted, an unknown source system, an unknown category and status "MAYBE" are CHECK violations, and a negative organization-source person id is accepted.
**Confidence:** High

### RULE-463: The person who registered a code can never confirm it, same-day corrections included
**Category:** Policy
**Priority:** P0
**Source:** ADR-0033 section 5 item 4 (four eyes, U-3), section 3 item 3 (the "本人" table: confirmer vs recorder compared as user ids); intent `2026-10-15-081242-one-coordinator-核算组织编码确认后填错是否允许同.md` (same-day correction keeps four eyes). Tests: `com.forcitis.one.acceptance.platform.integration.SourceReferenceConfirmationIT#fourEyes`, `com.forcitis.one.acceptance.platform.integration.SourceReferenceConfirmationIT#aSameDayCorrectionStillNeedsASecondPerson`, `com.forcitis.one.acceptance.platform.integration.AccountingCodeConfirmerAccountsIT#theRecorderCannotConfirmTheirOwnAccountingCode`, `com.forcitis.one.acceptance.platform.integration.SourceReferenceConstraintIT#manualRowIsNeverDecidedWithoutADecider`
**Plain English:** A registration is PENDING until a different person with the approve permission confirms it; holding both roles does not let one person confirm their own entry. Recorder and confirmer are compared as user ids and stored as two different users. The database also refuses to mark a manual row confirmed or rejected without a decider.
**Specification:**
- Given code 1021 registered (PENDING, from today, open-ended) by a person holding both permissions, When that person confirms, Then 403 `PLATFORM_INTEGRATION_CONFIRMER_IS_AUTHOR` and the row stays PENDING; When a person with update only confirms, Then 403; When another approver confirms, Then 2xx, the row is CONFIRMED and outbound returns 1021.
- Given a confirmed 9501 and a same-day correction 9502 registered by the same holder, When the holder confirms, Then 403 and outbound still returns 9501; When another approver confirms, Then outbound returns 9502.
- Given a CONFIRMED row, Then `created_by` is the recorder user id and `decided_by` the approver user id.
- Given a manual row, When updated directly to CONFIRMED or REJECTED with no decider (or only a time), Then refused; with another user as decider, accepted.
**Confidence:** High

### RULE-464: Outbound delivery uses only confirmed codes; a legal entity with no confirmed code fails and alerts
**Category:** Policy
**Priority:** P0
**Source:** ADR-0033 section 5 items 2 and 4, section 2 item 5 (outbound lookup, failing with an alert). Tests: `com.forcitis.one.acceptance.platform.integration.SourceReferenceConfirmationIT#neverConfirmedMeansNoOutbound`
**Plain English:** A legal entity with no code, or only a PENDING one, has no usable code: the lookup fails and raises an alert, so no voucher goes out with a missing or unapproved code.
**Specification:**
- Given one legal entity with no code and one with only a pending code 1031, When the outbound code is looked up for today, Then both lookups fail and at least two `ALERT platform.integration` lines are logged.
**Confidence:** High

### RULE-465: Confirming a later-dated correction closes the old row at the new effective date in the same transaction
**Category:** Lifecycle
**Priority:** P0
**Source:** ADR-0033 section 2 item 1 (confirmation closes the old confirmed row, `effective_to_date` = new row's effective date), section 5 item 4. Tests: `com.forcitis.one.acceptance.platform.integration.SourceReferenceConfirmationIT#correctionClosesTheOldRowOnConfirmation`
**Plain English:** While a correction waits, the confirmed code stays in use. On confirmation the old row is closed at the new row's effective date and the new row starts; source ids and own ids of both rows are unchanged.
**Specification:**
- Given 2101 confirmed from today and a correction 2102 effective in 14 days, When 14 days have passed and it is pending, Then outbound returns 2101; When another approver confirms, Then 2101 is `[today, later)`, 2102 is `[later, open)`, and outbound returns 2102.
**Confidence:** High

### RULE-466: A correction effective the same day as the wrong code replaces it; one effective earlier is refused
**Category:** Lifecycle
**Priority:** P1
**Source:** user decision "允许同日更正" (intent `2026-10-15-081242-one-coordinator-核算组织编码确认后填错是否允许同.md`; decision digest line: old row closed as an empty period, new row effective the same day, four eyes, audit kept); ADR-0033 section 2 item 1. Tests: `com.forcitis.one.acceptance.platform.integration.SourceReferenceConfirmationIT#aCorrectionFromTheSameDayReplacesAWrongCode`, `com.forcitis.one.acceptance.platform.integration.SourceReferenceConfirmationIT#aCorrectionBeforeTheCodeStartsIsStillRefused`, `com.forcitis.one.acceptance.platform.integration.SourceReferenceConstraintIT#anEmptyPeriodTakesNoKey`
**Plain English:** A wrong confirmed code can be corrected from the same day it started: on confirmation the old row stays but its period becomes empty `[D, D)`, and the new code applies from D. A correction dated before the code in effect started is refused. Empty periods take no uniqueness key; a period ending before it starts is rejected.
**Specification:**
- Given 9401 confirmed from D and correction 9402 from D, When another approver confirms, Then 9402 is CONFIRMED `[D, open)`, outbound returns 9402 on D and D+30, the 9401 row remains with period `[D, D)`, source id "9401" and own id unchanged, and a confirmation audit entry is written.
- Given 9601 in effect from D (today = D), When a correction dated D-1 is registered or confirmed, Then 4xx and 9601 stays unchanged; When a correction 9603 dated D+5 is confirmed, Then 9601 is `[D, D+5)`, 9603 is `[D+5, open)`.
- Given an empty period, Then another confirmed row from the same day may coexist, and an end date before the start date is refused.
**Confidence:** High

### RULE-467: A rejected registration changes nothing else and cannot be decided again
**Category:** Lifecycle
**Priority:** P1
**Source:** ADR-0033 section 2 item 1, section 5 item 4 (rejected rows set to REJECTED). Tests: `com.forcitis.one.acceptance.platform.integration.SourceReferenceConfirmationIT#rejectionLeavesEverythingElseAlone`
**Plain English:** Rejecting a wrong registration leaves the confirmed row and outbound code as they were, does not block a later correct registration, and the rejected row cannot afterwards be confirmed.
**Specification:**
- Given 3101 confirmed and 3102 pending for tomorrow, When 3102 is rejected, Then it is REJECTED, 3101 stays CONFIRMED and outbound still returns 3101; When 3103 is registered and confirmed, Then outbound returns 3103; When 3102 is confirmed again, Then 4xx and it stays REJECTED.
**Confidence:** High

### RULE-468: Confirmed rows are unique at every point in time; pending and rejected rows take no key; a taken code cannot be confirmed
**Category:** Validation
**Priority:** P0
**Source:** ADR-0033 section 2 item 1 (exclusion constraints limited to CONFIRMED rows, on both natural keys), section 5 item 4; guard 16. Tests: `com.forcitis.one.acceptance.platform.integration.SourceReferenceConstraintIT#confirmedRowsAreUniqueAtEveryPointInTime`, `com.forcitis.one.acceptance.platform.integration.SourceReferenceConstraintIT#pendingAndRejectedRowsTakeNoKey`, `com.forcitis.one.acceptance.platform.integration.SourceReferenceConfirmationIT#confirmingATakenCodeFails`, `com.forcitis.one.acceptance.platform.integration.SourceReferenceConfirmationIT#concurrentCorrectionsConfirmOnlyOne`
**Plain English:** For a source (system, category, source id) and for an own id, no two confirmed rows cover the same day; non-overlapping periods may coexist. Pending and rejected rows can overlap and may be registered even when the code is already confirmed for another legal entity, but confirming it then fails with 409 and nothing is silently overwritten. When two corrections for one legal entity are confirmed concurrently, exactly one succeeds and the other gets 409 and stays pending.
**Specification:**
- Given 1021 confirmed for entity A from 2026-11-02, Then a confirmed 1021 for entity B from 2026-11-20 and a confirmed 1099 for A from 2026-11-20 are refused (exclusion or unique violation); the same with period ending 2026-11-02 are accepted.
- Given the same, Then PENDING or REJECTED 1021 for entity B and PENDING 1099 for A overlapping are accepted.
- Given 4101 confirmed for holder and a pending 4101 for another entity, When confirmed, Then 409, the row stays PENDING, the holder's row and outbound are unchanged.
- Given confirmed 9301 from today and two pending corrections 9302 (+14 days) and 9303 (+21 days) confirmed by two approvers at once, Then one answer is 2xx and one is 409, the loser stays PENDING, the winner is CONFIRMED, 9301 is closed at the winner's date, and no day is covered by two confirmed rows.
**Confidence:** High

### RULE-469: Source references are closed, never deleted, and their source id and own id are never rewritten
**Category:** Policy
**Priority:** P0
**Source:** ADR-0033 section 2 item 1 (last paragraph: in no case rewrite `source_id` or `own_id`), DATA_DESIGN_CHECKLIST §14.1 D17 (close, keep history). Tests: `com.forcitis.one.acceptance.platform.integration.SourceReferenceConstraintIT#referencesAreNeverDeleted`, `com.forcitis.one.acceptance.platform.integration.SourceReferenceConstraintIT#sourceIdAndOwnIdAreNeverRewritten`
**Plain English:** A reference can change status or be closed, but not removed or re-pointed: DELETE and TRUNCATE are refused, and updating the source id or own id is refused.
**Specification:**
- Given a synchronised reference and a pending registration, When deleted or the table truncated, Then refused and both rows remain.
- Given an existing row, When `source_id` is changed by 1 or `own_id` is changed to another uuid, Then refused.
**Confidence:** High

### RULE-470: Registration, confirmation and rejection are audited
**Category:** Policy
**Priority:** P1
**Source:** ADR-0033 section 5 item 4 (third bullet: all three write an audit entry; refusals also audited per first bullet). Tests: `com.forcitis.one.acceptance.platform.integration.SourceReferenceConfirmationIT#everyStepIsAudited`
**Plain English:** Each of the three steps adds one audit entry to the integration audit.
**Specification:**
- Given a legal entity, When 5101 is registered, then confirmed, then 5102 is registered and rejected, Then the integration audit grows after each of those four steps.
**Confidence:** High

### RULE-471: The pending list and the per-legal-entity history are readable by registrars and approvers
**Category:** Policy
**Priority:** P1
**Source:** ADR-0033 section 5 items 3 and 4 (approver finds the row to confirm; current code and history shown on the legal entity page). Tests: `com.forcitis.one.acceptance.platform.integration.SourceReferenceQueryIT#theApproverFindsWhatWaits`, `com.forcitis.one.acceptance.platform.integration.SourceReferenceQueryIT#oneLegalEntityShowsItsCurrentCodeAndHistory`
**Plain English:** An approver gets pending rows of accounting codes (cursor paged) with their ids, and decided rows leave the list. For one legal entity the interface shows the current confirmed code and all history (closed, pending, rejected), nothing of other entities, and what the page shows is what outbound uses.
**Specification:**
- Given pending rows and a rejected one, When the pending list is read, Then every item has status PENDING, includes the waiting rows, excludes the rejected one, paging one row per page by `nextCursor` returns the same set; a decided row no longer appears.
- Given a legal entity with a closed, a rejected and a current row, When listed by own id, Then all rows belong to that entity, the current row is CONFIRMED `[corrected, open)`, outbound for that date returns 8102, 200 for approver, 401 anonymous, 403 for legal-entity maintainer only.
**Confidence:** High

### RULE-472: A signed ticket subject is resolved to a user only through an existing source reference; without one, login is refused and audited
**Category:** Policy
**Priority:** P0
**Source:** ADR-0033 section 3 item 5 (subject resolved, unresolvable means refuse and audit), section 2 items 2-3 (login only looks up, allocation belongs to sync); RULE-368 as revised by ADR-0033 section 11. Tests: `com.forcitis.one.acceptance.platform.integration.SignedTicketSubjectIT#theSubjectIsResolvedThroughTheSourceReference`, `com.forcitis.one.acceptance.platform.integration.SignedTicketSubjectIT#aSubjectWithoutReferenceIsRefusedAndAudited`, `com.forcitis.one.acceptance.platform.integration.SignedTicketSubjectIT#theSubjectIsNotTheEmployeeId`
**Plain English:** The ticket's subject is an organization-source person id (signed 64-bit, possibly negative) that is looked up in (ORG_SOURCE, EMPLOYEE) source references to find the employee and account. A subject with no reference is a 401, no session cookie, a `platform.identity.login.failed` audit entry, and no reference is created; a subject numerically equal to an employee's old-form id but without a reference is refused too.
**Transition:** the target owner is `platform_integration.external_identities`, looked up by (provider kind = the ticket issuer, external subject) to find the `UserId` (ADR-0033 section 3 item 5; ADR-0039 item 4 clause 1 moves the table from identity to integration). Until DA-3 lands the subject is resolved through the (ORG_SOURCE, EMPLOYEE) source reference, which section 3 item 5 allows ("DA-3 落地前沿用现行解析"); the tests assert this transitional behaviour. This card and `SignedTicketSubjectIT` are rewritten together when DA-3 lands (ruling by one-architecture, 2026-10-21).
**Specification:**
- Given a reference (ORG_SOURCE, EMPLOYEE, subject) for an employee with an account, When the ticket-login is called with that subject, Then 200 with that account's user id, and the number of references for the subject is still 1.
- Given a subject with no reference, When ticket-login is called, Then 401, no `SESSION=` cookie, a `platform.identity.login.failed` audit entry, and zero references for it.
- Given a subject equal to an employee's former numeric id but with no reference, Then 401 and zero references.
**Confidence:** Medium

**New terms:** Accounting Organization Code (核算组织编码), Accounting System (核算系统), Registrar / Recorder (录入人), Confirmer (确认人), Pending State (待确认), Confirmed State (已确认) — not found as terms.yaml entries of their own (ConfirmationStatus, RejectedState, SourceReference, SourceSystem, SourceObjectKind, Registration exist); Ticket Subject (票据 subject).
