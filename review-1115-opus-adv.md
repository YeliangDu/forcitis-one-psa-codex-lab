复审结论：必改
commit：65e2656efe7381c7c0d257c4732276dc90ee8112
复审者：opus-终审-1115adv

patch-id：e193674d80ef55e6c4bffe64dbafe7f500162ad2

Impact: platform.identity gains two listeners (EmployeeCorrected -> PermissionAdjustment item, EmployeeTransferred -> refusal alert) and a new table writer; the kernel port WorkdayDeadlines widens WorkCalendar.endOfWorkdayAfter to a nullable legal entity. Caller layers proved: listener -> WorkdayDeadlines -> WorkCalendarService (1); the one other caller, platform.approval DeadlinePlanner.plan, still fails on a null entity because it calls isWorkday first (2).

Input checks: `git cat-file -e` ok; PR #1115 head = 65e2656efe7381c7c0d257c4732276dc90ee8112 (gh pr view); diff taken with `git diff origin/main...65e2656ef`. Acceptance branch origin/claude/one-test-730-acceptance is at a3eeb5641 now, not 3082aacbd; the only difference is F1_730_ACCEPTANCE.md (`git diff 3082aacbd a3eeb5641 --stat`), so the three test files I ran are the same ones.

## 必改

1. **The rule "a correction that moves an employed person between departments raises nothing" has no test that goes red when it is broken. Broken, it writes a wrong row that the relaxed CHECK accepts.**
   - Where: `backend/src/main/java/com/forcitis/one/platform/identity/application/HandleEmployeeCorrected.java:68`, the clause `event.previousOrgUnitId() == null`. The class Javadoc (line 33) claims the behaviour: "Corrections that move an employed person between departments are not handled here".
   - Break (temporary copy of the head): I deleted `&& event.previousOrgUnitId() == null` and ran HandleEmployeeCorrectedEscalationTest, PermissionAdjustmentTest, CorrectionUndoesSeparationReviewIT (all 4 cases) and PersonnelCorrectionEventIT. **Nothing went red.**
   - Proof of the consequence: I added a throw-away probe IT, built like CorrectionUndoesSeparationReviewIT. It has a sensitive-role holder (security_auditor) and publishes an EmployeeCorrected with `triggerFieldKinds=[ORG_UNIT]`, previous department X and new department Y.
     - On the head it passes: no item.
     - With the clause deleted it fails: one row `SENSITIVE_ROLE_REVIEW / ASSIGNMENT_CHANGE / ORG_UNIT / before_org_unit_id=null / after_org_unit_id=Y`. That row says "the person was not employed before", but they were. `ck_permission_adjustments_assignment_change` lets it through, because the database cannot tell a department move from an undone separation. So only this application clause stops it.
   - Severity: the head behaves correctly today. It is 必改 because PROMPT.md "Severity / Break verification" says a defence whose break is not red is a must-fix. A test also cannot prove the claim as things stand.
   - Fix: add one implementation-side test, without touching the acceptance files. In HandleEmployeeCorrectedEscalationTest, or a sibling unit test, take an EmployeeCorrected with `[ORG_UNIT]`, previous X and new Y, for a sensitive-role holder, and assert that `adjustments.insert` is never called. Then confirm it goes red when the clause is deleted.

## Suggestions

1. **A role that cannot be found counts as not sensitive.** At `HandleEmployeeCorrected.java:82`, `roles.findById(...).map(...).orElse(false)` would treat a missing role as not sensitive, which fails open. It is unreachable today, because `fk_account_role_grants_role_id` is ON DELETE RESTRICT (`V20261014130000__platform_identity_baseline.sql:426`) and `openGrants` already joins roles. Using `orElseThrow` would surface a broken invariant instead of hiding it. Another option is to use `HeldGrant.code()` with `roles.findByCode`, as ReplaceAccountRoles does.
2. **Duplicate import.** `import com.forcitis.one.platform.identity.api.PermissionAdjustmentKind;` appears twice in `HandleEmployeeCorrected.java:15,18` and twice in `JdbcPermissionAdjustmentRepository.java:6,8`. It compiles, but it is noise.
3. **AuditDetail allowed-content row not added.** `backend/architecture/audit-detail-implementations.txt` says a new AuditDetail implementation "needs a row of the allowed-content table on the card DATA-REPAIR-timesheet-audit … and design review". `docs/data/repair-cards/data-repair-timesheet-audit.md` row 9 still says "目前仅 WorkCalendarLockedDaysChanged", and this diff adds no row (`git diff --stat` does not touch that card). The content of `PermissionAdjustmentOpened.auditDetail()` is surrogate keys, enums and an instant, which is in the allowed class. Add the row: detail shape `item <UUID> <kind> origin <origin>; user <UUID>; trigger command <UUID> field <enum>[; before org <UUID>]; after org <UUID>; escalation due <Instant>`, PI column 代理键.
4. **Acceptance IT flake (for one-test, not this PR).** In my first combined run, A-U1 failed. The cause was AuditDomainEvents (platform.audit, outside this diff), which threw `DuplicateKeyException … event_publication_archive_pkey`. That happened because the test publishes the identical event object twice in a row, and the audit publication stayed in PROCESSING, so `identityHandled` timed out. Two reruns of the class alone were green. The identity listener itself completed. Consider making the redelivery wait for each publish to finish before the next one.
5. f-1.md:97–98 still list `platform.identity.permission-adjustment.opened/regenerated` as the audit action. Ruling 6602101721 says the event-type name wins, and the erratum E-011 is one-design's PR, so I only note it here.

## Checked and fine

- **Ruling 6599023412/6599341585 (shape):** one person item, no role_id, ORG_UNIT only, before department and position null, after taken from the event, no SCOPE_REVIEW. Evidence: `PermissionAdjustment.separationCorrectionReview` (PermissionAdjustment.java:63–70) and A-U1/A-U2 green.
- **Migration V20261103130000:** it is a new file above the main max (V20261103120200, from `git ls-tree origin/main`), and the merged V20261103120000 is unchanged. The CHECK is equivalent to the ruling's three lines plus the two additions (IS DISTINCT FROM; `before_org_unit_id IS NOT NULL OR before_position_id IS NULL`). The ruling's line `before IS NULL OR origin=ASSIGNMENT_CHANGE` is covered by the kept "non-assignment rows all four NULL" line. The column COMMENT is updated as the ruling asks. No other table changed.
- **Ruling 6600900803 (alert):** `HandleEmployeeTransferred` writes one ERROR line `ALERT P2 platform.identity` with the event id and type, no name or code, then returns normally. The acceptance case is green: completion_attempts 1, no row.
- **Ruling 6600968506 (A-6):** no legal-entity column. The legal entity comes from the envelope. A null entity uses the default calendar, still creates the item, and logs one `ALERT P3 platform.identity` line with the event id only (HandleEmployeeCorrected.java:88–93). WorkCalendarKey(null, y) is the default key (WorkCalendarKey.java:23–25), so null only repeats the default lookup. WorkCalendarServiceTest has a new case with an entity whose holidays differ from the default; it is green.
- **The null-entity change and other callers:** `git grep endOfWorkdayAfter` finds only DeadlinePlanner and the new listener. `DeadlinePlanner.plan` calls `calendar.isWorkday(created, legalEntityId)` first, and the public `isWorkday` now has an explicit `requireNonNull` (WorkCalendarService.java:45–47). So approval still fails on a null entity, as before.
- **Unmaintained year:** `endOfWorkdayAfter` throws (`ORGANIZATION_ATTENDANCE_YEAR_NOT_MAINTAINED` plus the existing alert). The listener then fails, and Spring Modulith resubmits the incomplete publication (workforce.yaml delivery). The item is delayed, not lost. This matches the WorkdayDeadlines contract "never assumes all workdays".
- **Repeated or concurrent delivery:** `accounts.findForUpdate` serialises events per account. `existsForTrigger` and `uk_permission_adjustments_assignment_trigger` back each other up. A-U1 (two redeliveries plus a second event of the same command) is green.
- **Listener WorkContext and actor:** the same pattern as HandleEmployeeSeparated and HandleEmployeeReinstated. The actor `platform.identity.permission-adjustment` is registered in system-actors.txt, and SystemActorCatalogueTest is green.
- **Audit:** `platform.identity.PermissionAdjustmentOpened` is registered in must-audit-actions.yaml under the event-type name (ruling 6602101721). The detail has no names, codes or free text (PermissionAdjustmentOpened.java:15–20). MustAuditActionsTest and AuditDetailGuardTest are green.
- **Module boundaries:** identity reads only its own tables. It depends on kernel.WorkdayDeadlines, not on organization.attendance. ModuleDependencyTest, ArchitectureRulesTest, PlatformOrgInterfacesTest and DataArchitectureTest are green.
- **Acceptance tests:** none are changed in this diff. No assertion was loosened. No file is over 1000 lines; the largest new main file has 126 lines.
- **Tests run in a temporary detached worktree of the head** (acceptance files copied from origin/claude/one-test-730-acceptance; the worktree is now removed):
  - unit: HandleEmployeeCorrectedEscalationTest 3, PermissionAdjustmentTest 1, WorkCalendarServiceTest 10, EventSequenceContinuityTest 16, AuditDetailGuardTest 4, MustAuditActionsTest 3; all green.
  - guards: ArchitectureRulesTest 36, DataArchitectureTest 13, EventBusinessDateGuardTest 6, EventCatalogue{Coverage,Policy,}Test 1/7/10, EventSequenceCoverageTest 1, ModuleDependencyTest 3, GlossaryTest 5, SystemActorCatalogueTest 9, PlatformOrgInterfacesTest 4; all green.
  - IT: PermissionAdjustmentShapeIT 12, PermissionAdjustmentSchemaIT 5, PermissionAdjustmentLifecycleIT 12, WorkCalendarIT 8, PersonnelCorrectionEventIT 2, all green. CorrectionUndoesSeparationReviewIT 4 was green on two runs and failed once (the audit flake, suggestion 4).

### Break verification (temporary copy; recompiled before each IT run; restored after each)

| Defence | How broken | Result |
|---|---|---|
| Only an undone separation (previous department null) raises an item | Deleted `&& event.previousOrgUnitId() == null` | **Not red** (unit tests, acceptance IT and PersonnelCorrectionEventIT all green). The probe IT goes red and shows the wrong row. See 必改 1 |
| Only a holder of a sensitive role gets an item | `if (!holdsSensitiveRole \|\| …)` changed to `if (…)` | Red: HandleEmployeeCorrectedEscalationTest and CorrectionUndoesSeparationReviewIT A-U2 |
| One item per (kind, user, command, field) | `existsForTrigger` always returns false | Red: A-U1 (the unique index refuses the second insert, the listener fails and never completes) |
| P3 alert when the legal entity is null | `LOG.error` changed to `LOG.info` | Red: HandleEmployeeCorrectedEscalationTest |
| The entity of the event picks the calendar | Passed `null` instead of the envelope entity | Red: HandleEmployeeCorrectedEscalationTest |
| P2 alert for a transfer with no department before it | `LOG.error` changed to `LOG.warn` in HandleEmployeeTransferred | Red: CorrectionUndoesSeparationReviewIT (transfer case) |
| CHECK: a null department before only for SENSITIVE_ROLE_REVIEW + ORG_UNIT | Replaced the condition with `OR TRUE` | Red: PermissionAdjustmentShapeIT.aNullBeforeUnitIsOnlyForAReinstatementOfASensitiveRoleReview |
| CHECK: no department before means no position before | Deleted the line | Red: PermissionAdjustmentShapeIT (same case) |
| Head, unbroken | — | Green: PermissionAdjustmentShapeIT and CorrectionUndoesSeparationReviewIT |
