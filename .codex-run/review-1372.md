复审结论：必改
commit：55a183ae1702c10b45c8e26caf48dd997593dc42
复审者：opus-首轮-1372

patch-id：22f0a957cd67e023c607db664ea3fc2aad6dca87

Impact: organization.workforce (personnel command entry and effect, events, person-level reads, operations command, one appended migration renaming employee_versions) and, through its events and OrgDirectory, platform.identity; proven two caller layers (web/api -> PersonnelCommandService -> rules/repository; OrgDirectoryService -> JdbcPersonnelCommandRepository). Base for the diff: 592d7f70f5f5f2d37b8b1f457608872f6b4b7ac1.

## Must-fix

1. EmployeeUnpaidLeaveStarted is suppressed while the person keeps working in another relationship (correctness, design 6.1).
   - Where: backend/src/main/java/com/forcitis/one/organization/workforce/domain/PersonnelCommand.java:408 (`onLeave = ... history.personOnLeave(rel, day)`), PersonnelCommandEvents.java:89 (`if (person.onLeave())`).
   - Problem: design 6.1 says EmployeeUnpaidLeaveStarted fires on "each UNPAID_LEAVE_START takes effect, per relationship ... Deliberately conservative: the review runs even if the person keeps working in another relationship". The code fires it only when every other in-service relationship is also on leave, so identity's sensitive-role review (HandleEmployeeUnpaidLeaveStarted) never runs in the dual-employment case.
   - Evidence: reviewer probe IT (temporary copy, not committed): person employed by the world's entity, additional-employer HIRE for B, UNPAID_LEAVE_START on B effective today -> command EFFECTIVE, EmploymentRelationshipUnpaidLeaveChanged = 1, EmployeeUnpaidLeaveStarted = 0 (expected 1).
   - Fix: emit EmployeeUnpaidLeaveStarted for every UNPAID_LEAVE_START that takes effect (drop the `onLeave` condition, or the field); add a test with two relationships that asserts the event fires once.

2. A void relationship is never re-used; the employee can never again be hired by that employer (correctness, design 4.2-9 and section 5 "Withdraw a pending HIRE").
   - Where: backend/src/main/java/com/forcitis/one/organization/workforce/application/PersonnelCommandService.java:366-367 (`all.relationships().stream().anyMatch(r -> employer equals) -> "a return is a REINSTATE on it"`).
   - Problem: invariant 9 says a void relationship (all versions void after a withdrawn HIRE) is re-used by a later HIRE with the same predecessor and refused only with a different one. The code refuses every HIRE to an employer that has any relationship row, void or not; a REINSTATE on the void relationship is also refused (`requireStateOnTheDay`: "the employee has no version on ..."), so a mistyped and withdrawn additional-employer HIRE leaves a permanent dead end.
   - Evidence: probe IT: in-service person, HIRE(employer C, TODAY+5) entered, withdrawn, HIRE(employer C, TODAY+6) -> DomainException "the employee already has a relationship with this employer; a return is a REINSTATE on it" (PersonnelCommandService.java:367).
   - Fix: when the existing relationship with that employer has no live version, bind the HIRE to it if its predecessor equals the request's (both null or equal), else refuse with a message naming the void relationship; add a test for both branches.

3. The person-level vs relationship-level same-day check is stricter than today for concurrent-post commands (correctness, design section 5: "the check excludes concurrent-post kinds the way today's per-employee index does ... so it is no stricter than today").
   - Where: backend/src/main/java/com/forcitis/one/organization/workforce/domain/PersonnelCommandRules.java:49-51 (`requireOneCommandPerDay`): filters out only START_CONCURRENT and CORRECTION, not commands whose target_assignment_kind is CONCURRENT.
   - Evidence (verified both sides, same scenario): person with a concurrent post started today; END_CONCURRENT on that post entered for TODAY+8; CHANGE_LEGAL_ENTITY for TODAY+8. Baseline 592d7f70f (ReviewProbe1372BaseIT): accepted. Head: refused, "the employee already has an open command for 2026-11-10 that is about the person or about another relationship" (PersonnelCommandRules.java:53).
   - Also (unverified, from reading): CORRECTION is excluded on both sides, so an open CORRECTION (it can be AWAITING_ATTRIBUTION_COUNTERSIGN, see AttributionCountersignTablesIntegrationTest.correctionAndOpenPerDay) entered after an open CHANGE_LEGAL_ENTITY on the same day is no longer refused by the app check, while the old per-employee index refused it. Check this direction when fixing.
   - Fix: apply the old index predicate on both sides (skip START_CONCURRENT and target_assignment_kind = CONCURRENT; count CORRECTION as today); add the END_CONCURRENT + CHANGE_LEGAL_ENTITY accept case and the CORRECTION refuse case as tests.

4. Comments that contradict the code (filed as correctness per PROMPT.md).
   - PersonnelCommandRules.java:69: "Moving the primary first is a transfer, not built with this card." Moving the primary is built in this card (PersonnelCommandService.bindRelationship, `primaryTransfer && movesPrimary -> all.withPrimaryIn(named)`; LifecycleIT done-when 12).
   - PersonnelHistory.java javadoc of `personLeaves` (line 72-75): says "and no hire names this one as predecessor on the day", but the method checks only other in-service relationships; the predecessor check lives in PersonnelCommand.personTransition.
   - PersonnelCommandService.java:395: "Every kind but a hire is about a named employee" - a HIRE of an existing person (additional employer, successor) now goes through `existingEmployee` too.
   - Fix: correct the three comments.

5. Registry entries differ from the signed appendix A (C2, C4) (hard constraint: data registration, ADR-0043).
   - backend/architecture/data-access-registry.yaml: `organization_workforce.employment_relationships` is registered `mutability: APPEND_ONLY`, but the table accepts the one-time confirmation UPDATE (trigger `refuse_employment_relationship_rewrite`); appendix A C2 says `CONDITIONAL_UPDATE` with the condition text, `business_key: [employee_id, employer_legal_entity_id]`, and the purge-order note. The registry now declares a table append-only that is not.
   - backend/architecture/data-classification.txt: none of the nine PI rows of appendix A C4 were added (employment_relationships.employee_id, employer_legal_entity_id, predecessor_relationship_id, employer_source_kind, employer_confirmed_at; personnel_commands.employment_relationship_id; assignment_versions.employment_relationship_id; employment_relationship_versions.employment_relationship_id). Unregistered columns default to INTERNAL + NONE (file header), so these personal-information columns are marked non-personal.
   - Fix: register as appendix A says (DataArchitectureTest D1 requires the condition for CONDITIONAL_UPDATE).

6. The employer backfill's inherited-attribution branch has no test, and the design requires one (a line of defence missing; a wrong employer has no correction path, OI-1).
   - Where: the org-tree walk in V20261105120000 section 2 (DO block, WHILE loop over org_unit_legal_entities / parent_org_unit_id) and its Java twin JdbcInitialVersionMigrationRepository.employerOn (own period, else snapshot `assignments` unit up the parents).
   - Problem: design 7: "A test compares the SQL backfill with LegalEntityResolution.resolve on a sample." No such test exists (`grep -rl LegalEntityResolution backend/src/test` lists no migration test; EmploymentRelationshipUpgradeIT, EmploymentRelationshipBackfillIT, the MigrateDryRun/Versions ITs and MigrateInitialVersionsTest use only the employee's own attribution: `grep -n "org_unit_legal_entities|parent|inherit"` finds nothing). Two hand-written copies of the resolution rule, with a different unit source each (PRIMARY assignment version in SQL, snapshot `assignments` in Java), run unproven.
   - Fix: add the comparison test over a sample that includes inherited attribution through a parent unit and a corrected end date, for both the migration and MigrateInitialVersions.

## Suggestions

- Roster (JdbcEmployeeRosterQueries lateral `order by (employment_status = 'EMPLOYED') desc, employment_start_date asc`): appendix A says take the relationship that holds the primary post that day, else the earliest in-service one; with none in service the one with the latest end (posts_day). With two separated relationships the pick is arbitrary, so posts_day can be the earlier end.
- JdbcOrgStructureCommandRepository OPEN_HOLDERS / employedIn / postsIn still join assignment and relationship versions on employee_id; design 6.2 and appendix A say join on employment_relationship_id. `arrivalsAfter` (not grouped) returns a person once per in-service relationship. `heldSeparationsIn` uses a scalar subquery over separation_holds HELD per employee; now that two SEPARATEs of one person can both be held, it can return more than one row and fail (not run).
- Event payloads vs appendix A E1 table: EmploymentRelationshipStarted lacks employerLegalEntityId and predecessorRelationshipId; EmploymentRelationshipCorrected lacks correctedFromEffectiveDate; UnpaidLeaveChanged carries commandKind instead of onUnpaidLeave. The person-level events' employmentRelationshipId is nullable in code and catalogue, the design says NOT NULL. EmploymentRelationshipCorrected fires only when employee-level values change. Events cannot be re-published later, so settle these with one-design before merge.
- Section 5 says every command names its relationship explicitly; `defaultRelationship` silently uses the only relationship when none is named, and the EnterPersonnelCommand javadoc says the field is named. Either require it or record the ruling.
- MigrateInitialVersions dry run: design 7 asks for counts per employer entity too; the output reads "initial versions migrated: dry run, nothing written: ..." (misleading prefix); the usage line in WorkforceOperationsCommands does not mention --dry-run.
- Unused: PersonnelCommandRepository.relationshipExists (and its Jdbc implementation), PersonnelHistory.currentAssignmentRowsOf.
- PersonnelCommandService.java:377 message "the predecessor relationship is separated on D by an earlier command; enter that separation first" states the opposite of the failed condition; reword ("is not separated on D").
- DdlNamingTest went from 1004 to exactly 1000 lines by collapsing braces onto one line; it leaves file_lines' OVER list correctly, but the split the OVER entry promised is still owed.

## Checked and fine

- Commit exists (`git cat-file -e`); diff taken with `git diff 592d7f70f...55a183ae1`. No PR, so no PR-head check.
- Acceptance tests not altered by the implementation: `git diff --stat 2e23878cc 55a183ae1 -- backend/src/test/java/com/forcitis/one/acceptance` is empty; the only non-test-branch commits touching that path are merges of the test branch / main.
- Platform signatures: backend/architecture/platform-org-interfaces.signatures.txt has only added lines (one `-` line is the diff header); EmploymentVerdict, EmployeeProfiles, OrgDirectory unchanged.
- Migration: new version 20261105120000 is above the baseline's highest (20261105100000); no merged migration edited. Schema matches appendix A table by table (new table with 2 UNIQUE, 3 FK, 3 CHECK + not-self, 2 indexes, trigger pair; rename with column rename, object renames, per-relationship exclusion and revision key, composite corrected_from FK and its UNIQUE, the two new indexes; personnel_commands nullable id, composite FK, kind CHECK, rewritten hire/reinstate CHECKs, per-relationship and person-day indexes, widened per-assignment index, relationship index; assignment_versions id, composite FK, index). Only deviation: `updated_at` on employment_relationships, documented in the migration as lead-ruled for DdlNamingTest D2. Backfill fails loudly with aggregate counts only.
- Secrets/personal data: none in the diff; test fixtures use synthetic ids and names.
- DdlNamingTest RENAME TO handling (lead-ruled guarded change): it moves the table and its indexes in the model and renames primary-key columns; DdlNamingTest and DataArchitectureTest pass.
- Lead ruling items 1-9: field names, read api shape and records, five event classes, error code 409 (LifecycleIT done-when 8), reflection tests, all present.
- Tests run on head (temporary worktree): failsafe `**/organization/workforce/**/*IT,**/identity/**/*IT` 1281 tests, 0 failures, 1 error (LegalEntityAccountingCodeMovedIT: generated openapi.json missing in the temporary checkout, environment only); surefire `**/organization/workforce/**/*Test, DdlNamingTest, DataArchitectureTest, EventSequenceContinuityTest` 376 tests, 0 failures. `scripts/guards/file_lines.py`: 0 problems.
- Break verification (separate temporary copy, all four breaks applied together, targeted classes run; each failure is at the targeted assertion):

| Defence | How broken | Result |
|---|---|---|
| tg_employment_relationships_append_only (confirmation-only update) | `IF true OR OLD.employer_source_kind = ...` in refuse_employment_relationship_rewrite | red: ConfirmationTriggerIT 3 of 4 (second update, employer/other column, DECLARED row), SchemaIT.theEmployerNeverChanges |
| uk_personnel_commands_open_per_relationship_day | created as a non-unique index | red: ConcurrentAssignmentSchemaIT.oneOpenCommandPerPersonOrPrimaryAndPerConcurrentPostPerDay, AttributionCountersignTablesIntegrationTest.correctionAndOpenPerDay, PersonnelVersionTablesIntegrationTest.commandRowsFollowTheirKind |
| Primary-holding relationship cannot end while another stays (requirePrimaryNotOnEndingRelationship) | early return | red: LifecycleIT.thePrimaryHoldingRelationshipCannotEndWhileAnotherStays |
| 12a: one primary-producing open command per employee and day (producesPrimaryVersion) | always false | red: SameDayIT all 4 |

- Temporary worktrees under /private/tmp/claude-501/review-1372 removed after the run.
