# Flaky tests and main reds: root-cause classes and structural fixes

Card #1597 (investigation, read only). Status: proposal, 2026-11-06, counts taken on `origin/main` 0105521bf. Claims are marked [V] checked (source or command named) or [U] not checked (what is missing).
Window: 2026-10-30 00:00 to 2026-11-06 about 16:30 (Europe/Berlin local time).

## 1. Conclusion

- **Time is still the main cause of red main, but not wall-clock dates: it is elapsed-time budgets in script self-tests.** 8 of the 19 main runs that went red in the window died on a self-test that measures or races a real duration (`stop_language` 3 runs, `card_handover` 3, `acceptance_reports` 1) or on a Java test with a real 200 ms deadline (1) [V, table 3]. Date dependence is 2 more runs. Java wall-clock reads are already locked by #1586 (`TimeSourceGuardTest`); Python `card_handover` was fixed by #1505. **Left out by both: the other self-tests with real budgets, three of which have no open card** (`stop_language` 0.05 s, `acceptance_reports` 0.3 s, `backend_verify_selftest` 30 s / 2 s).
- **The merge queue's reds are a different mix: shared event processing (class B) and shared state (class C) dominate the 13 reruns that turned green on the same run id** (B 5, C 4, other 4) [V].
- **Proposal: two follow-up cards** (section 5). Three classes get none (reasons in section 4).
- Premise check, user counts "12 related issues; 8 of 10 main reds; 5 time-related, 3 load-related" [V against the table]: 11 flake issues exist (#879 #970 #1044 #1071 #1104 #1183 #1340 #1343 #1464 #1503 #1550; 12 only with #1465, a hardening card); main went red 19 times, not 10 (`gh run list --status failure` shows only the last attempt, 9 runs were re-run green and are invisible there); of the 10 final failures 6 are time (2 date, 4 elapsed or deadline), 3 are other (two install races in `gates_selftest`, one missing CI trigger), 1 unknown. By root cause "load-related" is not a class: load only exposes A2 budgets and two script races.

## 2. Method and limits

- Sources: `.git/merge-queue/merge.log` (62 RERUN-FAILED events in the window, 61 run ids), `gh run list --branch main` (442 runs in the window; red = attempt 1 failed), `gh run view <id> --attempt N --log-failed`, the issues and their closing PRs (diffs read), thread dumps of two timed-out queue runs (artifacts downloaded and read).
- Raw table with every queue red: scratchpad file of the card session, not committed. Only rows that carry a cause are below.
- Limits: log text names the failing test but not always its cause; where the PR or issue gives a mechanism the row says so, otherwise the cause is marked UNKNOWN. Same-class counts are regex proxies: a hit shows the pattern exists, not that it is a bug [U].

## 3. Table (one row per cause)

Class legend: **A1** date dependence; **A2** elapsed-time budget or real deadline; **B** async wait on shared event processing; **C** shared state between tests; **D** runner / host; **E** other; **U** unknown.

| ID | Date | Test, line | Class | Fix that landed | Same class on main now |
|---|---|---|---|---|---|
| F01 | 11-01/02, 2 main runs | `ApplicationIntegrationTest:276` hard-coded date | A1 | #972 date derived | locked by #1586 [V] |
| F02 | 11-05 queue + main | `CountersignDecisionHttpIT:369` holidays on weekdays | A1 | #1463, #1468 | acceptance dir, #1586 follow-up by one-test |
| F03 | 10-30, 11-05, 11-06, 3 main runs | `stop_language` self-test, 0.05 s limit (took 0.177 / 0.068 / 0.100 s) | A2 | #923 best of 7 (same bound, recurred twice) | **open, no card** |
| F04 | 11-05, 3 main runs | `card_handover` self-test:336, 0.01 s budget | A2 | #1505 injected clock | fixed |
| F05 | 11-02 main | `ClamdScannerTest:136`, 200 ms deadline spent by a JVM stall | A2 | #1046 `Clock.fixed` | machine clock now banned in tests by #1586 [V] |
| F24 | 11-06 16:17 main run 42240696656 | `acceptance_reports.py:223` `legacy_script_self_test`: watchdog `BACKEND_WATCHDOG_SECONDS=0.3` must cover bash start, package stub and failsafe start; only 1 call recorded | A2 | rerun green; file unchanged since #1034 | **open, no card** |
| F06 | 11-03, 11-05, 11-06, 3 main runs | `backend_verify_selftest` real 30 s / 2 s budgets, `sleep` polling | A2 likely, U | none | **open, no card**; cause of two cases not in the log |
| F07 | 11-03, 3 queue runs | `ReinstatementOpensBaseRoleIT:265` | B | #1186 | see section 4 |
| F08 | 11-03 queue | `EmployeeSeparatedClosesAccessIntegrationTest` waited for any identity listener | B | #1106 | |
| F09 | 11-06, 2 queue runs | `CorrectionUndoesSeparationReviewIT` (same mechanism as F07) | B | #1553 | |
| F10 | 11-05 local | `UnpaidLeaveInServiceChecksIT:204` read right after an async listener | B | #1345 | |
| F12 | 11-02..05, 3 queue runs | `CachedBodyTest` counted files in the shared `java.io.tmpdir` | C | #1346 `@TempDir` | 0 hits of `java.io.tmpdir` outside acceptance [V] |
| F13 | 11-02, 2 queue runs | three ITs seeded `legal_entities` code 'ABC' | C | #1038 own code per class | no card, see section 4 |
| F14 F15 F16 | 10-30, 11-03, 3 main runs | `gates_selftest` install: rename race; EACCES race in the self-test; SIGPIPE from `git archive \| tar` | E (three separate causes, load only exposes) | #912, #1073, #1188 | all fixed, no common pattern left |
| F17 | 11-04 main | `card_handover` imports `issue_gate`: CI trigger missing, deterministic | E (not a flake) | #1258 | |
| F18 | 11-04..05, 3 queue + 1 nightly | "backend tests exceeded 1500 s", no test failed | D by symptom, U | none | see below |
| F20 | 11-03..06, 9 queue runs | exact-count assertions on shared catalogues (`NumberingRulesIT` 4 to 5 rules, `WorkforceHttpIntegrationTest`, `SystemRoleRaisedRevisionIT`, `ApplicationIntegrationTest.sample()`) break when another PR adds a seed | E, deterministic | one-off edits per PR | no card, see section 4 |
| F23 | 11-04, 11-06 | queue host ssh "Permission denied (publickey)", label removal failed | D | none | not a test |
| F11 F21 F22 | 11-05, 11-06 | `EventPublicationOperationsIT` "expected 1 but was 2" once; Mockito `ClassCastException`; two docs-lane reds | U | none | logs do not show the cause |

Counts [V]: main red runs 19 = A 10 (2 date, 8 elapsed or deadline), E 5, U 4 (F06 x3, F18 nightly). Queue RERUN-FAILED 62 events, of which 13 went green on the same run id after a rerun: B 5 (F07 x3, F09 x2), C 4 (F12 x3, F13 x1), other 4. The rest of the 62 are plain defects in the PR (acceptance guard, typecheck, glossary, scorecard, OpenAPI export) and are not flakes.

**F18 (thread dumps of runs 41947346855 and 41953313266, [V] files read):** all 8 JVMs were alive and busy at the kill: Argon2 hashing inside `SingleOperatorEmergencyMinimumIT` (RUNNABLE), socket reads with a timeout in `RevokeDisableLockOrderIT` and in `SingleOperatorModeStartupIT`, none parked on a lock in test code. That is not a deadlock; it fits a runner too slow for a fixed 1500 s total. It happened 4 times in 5 hours and not before or after, so there is no pattern to guard. No card; [U] whether the runner was shared with another job (no runner load data was collected).

## 4. Classes without a card

- **A1 / A2 in Java:** #1586 bans machine-clock reads in tests; a test can no longer pass a real clock to a deadline.
- **C (shared state):** F12 is gone (0 files use `java.io.tmpdir` outside acceptance; 3 non-acceptance files call `createTemp*` for their own files [V: `git grep`]). F13's shape (fixed natural key with `on conflict do nothing` into a shared table) exists in 32 test files [V], but only one cause in the window and the fix is local; a guard would need a list of shared tables and would fire on legitimate seeds. One occurrence, no guard without evidence.
- **E, gates install races:** three different causes in one self-test, all fixed; no repeated pattern.
- **F20 (catalogue counts):** 9 queue runs lost, but every case differs and the assertions that matter (`items.length()` on the user's own data) are the majority of 7+ files; a text guard cannot tell a catalogue from own data. Cost of a guard (a register to maintain) exceeds the value; the per-PR fix stays. Lead may revisit if a fifth signature appears.
- **D:** F18 and F23 above.

## 5. Proposed follow-up cards (ranked)

**1. Self-tests must not assert on or race a real duration (class A2, scripts and hooks).**
- Evidence: 8 of 19 main reds; two recurred after a one-off fix (#923); three self-tests are still unfixed (`stop_language`, `acceptance_reports`, `backend_verify_selftest`) [V].
- Fix: a self-test controls when a budget expires (injected clock, or the stub signals through a file and the test releases the watchdog) instead of waiting for a real 0.05 s / 0.3 s / 2 s; the `stop_language` performance check becomes a deterministic work count or is dropped. The three files are fixed in the same card, so the guard starts with an empty register.
- Guard: a light.sh check over the 70 self-test files fails on (a) a budget below 5 s set for the code under test (`*_SECONDS`, `*_WAIT`, `timeout=`) in a self-test, and (b) an assertion comparing a `monotonic` / `perf_counter` / `time.time` difference. Proxy hit list today: 18 files match a time pattern, of which 4 are the real offenders [V: regex proxy, hits are not all bugs].
- Cost: three self-tests plus one small check, no new register. Break each in a temporary copy.
- Not proposed: retry on failure, raised timeouts, loosened assertions.

**2. One wait helper for event-driven tests, bound to the case's own event ids (class B).**
- Evidence: 4 causes, 3 main-affecting reds in the queue, 5 of the 13 green-on-rerun runs; F07 and F09 had one mechanism and the sibling test was fixed 3 days later because nobody searched for it [V]. Mechanism per #1104: two concurrent `AuditDomainEvents` deliveries of one event id collide on `event_publication_archive_pkey` and the loser stays PROCESSING (60-run local repro in the issue).
- Fix: generalise `identityHandled(event)` into a shared helper that waits for every listener of that event id and refuses to publish the same id again before the previous delivery is handled; tests read `event_publication*` only through it.
- Guard: a test scan failing when a test class outside the helper reads `event_publication` / `event_publication_archive` or combines Awaitility with `completion_date`. Candidates today: 11 non-acceptance and 11 acceptance files [V: grep], 41 test files mention the table. Acceptance files go on a register that one-test empties (acceptance tests are not edited by the platform session).
- Cost: medium (22 files to migrate, the register keeps the guard green meanwhile).
- Open question for design, [U]: whether production can deliver one event id concurrently (the republish job is off in ITs); if it can, the archive copy is a defect beyond the tests.

## 6. Verified / unverified summary

- Verified: counts in section 3 and 5 (commands in this document and the card session's raw table), the F18 dump contents, the F24 log line (`acceptance_reports.py:223`, 1 call recorded instead of 2) and the stub budget 0.3 s in the code.
- Unverified: that load caused F24 and F06 (the logs show no timings), the cause of F11 / F21 / F22 / F06's lock cases, the runner load behind F18, and whether a regex hit is the same bug.
