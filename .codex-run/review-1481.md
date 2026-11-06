复审结论：通过
commit：64e9322aab1b220e8ffced125799afbba02512d7
复审者：sonnet-首轮-one-reviewer-1481

patch-id：df29732a7cffdf083faa24d38db05761d56585cf

## Impact
Adds the new module accounting.ledger (one table, listener on LegalEntityRegistered, startup backfill). The only existing code touched is organization.workforce: one new api method LegalEntityDirectory.allIds(), implemented in LegalEntityDirectoryService. Callers proved to 2 layers: BackfillLedgers -> LedgerBackfillOnStart, and the listener -> EnsureLedgers -> JdbcLedgerRepository. Nothing else points to accounting.ledger.

## Must-fix
None.

## Suggestions
1. LegalEntityIntegrationTest.allIdsListsEveryLegalEntity: the DisplayName says "deactivated ones included", but the test never deactivates an entity. JdbcLegalEntityQueries.findAll has no status filter, so the behaviour is right, but the claim is unproven. Deactivate one entity in the test, or drop the words from the name.
2. BackfillLedgers javadoc says "one failure leaves the others' books in place". That is true, but a failure also propagates out of the ApplicationRunner, so startup fails and the entities after the failing one are only reconciled on the next start. This is fail-loud and acceptable; say it in the comment so no one reads it as "continues past failures".
3. registrationCreatesBothBooksOnce: the redelivery step is only indirectly proven (final state is still 2 rows). The unique key would reject a duplicate anyway, so the test is weak against a handler that fails on a redelivery but is not wrong.

## Checked and fine
- Head: git rev-parse HEAD and gh pr view 1481 both give 64e9322aab1b220e8ffced125799afbba02512d7.
- Schema diff against the signed record docs/data/reviews/accounting-spine-g1.md: the migration has exactly ledgers (id, legal_entity_id, basis_kind, created_at, updated_at), pk_ledgers, uk_ledgers_legal_entity_id_basis_kind, ck_ledgers_basis_kind, append-only and truncate triggers with one function, table and column comments, and no rows. Registry entry matches the record (AGGREGATE_ROOT, Ledger, APPEND_ONLY, PERMANENT, NEW, writers accounting.ledger). Trigger function sets search_path.
- Migration V20261105190000 is above the highest baseline version (V20261105160000); only an added file, no merged migration edited.
- The ArchitectureRulesTest entry is exactly what it cites: organization.workforce max 13 (12 + allIds), source and one-lead ruling cited, MAX_API_METHODS_PER_MODULE still 12, no other entry changed. A note for the human: the ruling text is a stand-in for one-architecture, which the source says has no session.
- Module boundary: LedgerModuleBoundaryTest and the ArchUnit check pass; main code imports only kernel plus workforce api and events; edge accounting.ledger -> organization.workforce declared in module-dependencies.txt; signatures file, system-actors.txt (the new actor in docs and backend files agree), events yaml, asyncapi, unversioned-table-writes, unbuilt-roots and aggregates built.yaml entries have reasons; ops SQL for the schema is added in dev and prod.
- Coding rules: package-private listener and repository, constructor injection, jspecify NullMarked on new packages, Clock injected, idempotent insert with ON CONFLICT DO NOTHING (safe across instances), no swallowed exceptions or fallback defaults. No file near 1000 lines.
- No assertion loosened or deleted: the only test edit outside new files is the SCHEMAS list in DataArchitectureTest (adds accounting_ledger) and one added test.
- Run in the review worktree: `./mvnw -o test -Dtest=LedgerModuleBoundaryTest,LedgerTableIT,LedgerBooksIT,ArchitectureRulesTest,DataArchitectureTest` gives 3/2/2/38/13 tests, 0 failures.

### Break verification (temporary worktree, deleted afterwards)
| Defence | How broken | Result |
|---|---|---|
| Truncate trigger tg_ledgers_truncate | removed from the migration | red (LedgerTableIT, 1 failure) |
| Idempotent insert (ON CONFLICT DO NOTHING) | clause removed from JdbcLedgerRepository | red (LedgerBooksIT error; LedgerTableIT's failure came from the trigger break) |
| API surface exception for workforce | max lowered to 12 | red (ArchitectureRulesTest, 1 failure) |
