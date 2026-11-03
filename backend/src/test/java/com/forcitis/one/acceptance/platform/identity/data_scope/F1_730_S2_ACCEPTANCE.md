# F-1 #730 S2 acceptance notes: the expiry and escalation jobs

Class: `PermissionAdjustmentJobsIT` (7 cases, MIGRATED database, injected clock, items written straight into the table).
Stacked on S3 (#1119): it reuses `PermissionAdjustmentApiSupport` (world, item seeders); retarget to main once #1119 has merged.

| Done-when | Case |
|---|---|
| ADR-0044 §2.3: expiry exactly at `uphold_expires_at` (one second before: nothing), `expired_at` = job clock, grants unchanged | `anUpheldItemExpiresExactlyAtItsExpiryAndGrantsStay` |
| Reading never expires an item (still UPHELD with the expiry shown until the job runs) | `readingDoesNotExpireAnItem` |
| Regeneration: one new OPEN SENSITIVE_ROLE_REVIEW, origin UPHOLD_EXPIRY, refers to the original, same person, own escalation due date after the job time; reruns add none (invariant 16) | `anExpiryRegeneratesOneItemAndRerunsDoNotAddMore` |
| No regeneration without a sensitive role or after separation; decided at once | `noRegenerationForAPersonWithoutASensitiveRoleOrWhoLeft` |
| HELD at expiry: EXPIRED, `is_held_at_expiry` true, undecided, nothing generated; still nothing while held; after release the next run generates one and records the decision; rerun adds none (invariant 26) | `aHeldPersonGetsTheRegenerationAfterTheRelease` |
| Audit `PermissionAdjustmentExpired` (original) and `PermissionAdjustmentOpened` (new item), per one-design ruling 6602101721 | `expiryAndRegenerationAreAudited` |
| Escalation (invariant 30): not one second before the due time; at it OPEN items of both kinds get `escalated_at` = job clock, stay OPEN; not-yet-due and UPHELD items untouched; once only; roles unchanged; one `PermissionAdjustmentEscalated` audit entry | `anOverdueOpenItemIsEscalatedOnceAndNothingIsRevoked` |

Red on baseline: all 9 cases (the two A-6 cases fail at the missing job entry). Six fail with "no bean of a type named ExpirePermissionAdjustments / EscalatePermissionAdjustments in platform.identity" after the fixtures ran, one (`readingDoesNotExpireAnItem`) fails on the detail 404 (no route). Green: not possible before the implementation.

## Assumed, not ruled (please confirm or tell me the real names)
- Job entries are beans of platform.identity whose type or interface is named `ExpirePermissionAdjustments` and `EscalatePermissionAdjustments`, public no-argument `execute()` (the first name is the one `DataScopeSupport.runUpholdExpiry` already assumes). The `@Scheduled` cron (00:05 Asia/Shanghai) is not tested.
- "Holds a sensitive role": business_admin (the dual-control per-person roles of the seeded set). "Held" is read from `separation_holds.status = 'HELD'`; the fixture also sets the command to PENDING on release; if availability reads the command status differently the release step must follow.
- Escalation does nothing to grants; the delivery of the escalation to the holders of `account-role:update` is NOT asserted (recipient and purpose of that delivery are not fixed in the card).

## Not covered
- Batch size 100 and a second page of due items; concurrent job runs (version lock, ShedLock).
- Regeneration of an item whose person lost the sensitive role between uphold and expiry by a role change (only "never had" via `plain`).
- Escalation due dates (generation time, S1).

- The job run id in the unresolvable-entity ALERT P3 line (ruling 6605327331 asks for the event or job run id plus the item id): the test calls the job's `execute()` directly and the run id is not observable there, so only the item id is asserted (and the absence of the person's name). Tell me where the run id can be read if it should be asserted.
