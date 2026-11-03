复审结论：通过
commit：4bda252051c7d8b21a141cb9f7db26b47c7f17c7
复审者：opus-首轮-adv730s3

patch-id：a9a5049ae77bcd4d1b5c6e2596a7c11a7d295f59

Impact: the permission-adjustments HTTP API (list, detail, :uphold, :mark-adjusted) for every signed-in caller. Proven through 3 layers: controller -> use case / PermissionAdjustmentAccess -> JdbcPermissionAdjustmentRepository against a real Postgres (Testcontainers).

How it was run: a scratch IT `AdvPermissionAdjustmentIT extends PermissionAdjustmentApiSupport` (same fixture world as the S3 ITs), placed only in a temporary worktree at 4bda2520 under the scratchpad, run with `./mvnw -o -q verify -Dit.test='AdvPermissionAdjustmentIT' -Dtest=NONE ...` (exit 0). The temporary worktree was removed afterwards; nothing in the existing worktree was changed.

## Must-fix

None. No sequence I ran produced a wrong response on a reachable path, a leak, or a fail-open.

## Suggestions (proven by running, not blocking)

1. Self-addressed scope review is readable by its subject and can be handled by nobody. `PermissionAdjustmentReader.java:19-21` (`canRead`: `employee.equals(item.recipient()) || ...`) and the SQL in `JdbcPermissionAdjustmentRepository.findFor` (`recipient_employee_id = :employee or (...)`) do not exclude the caller's own items on the recipient branch.
   - Reproduction: insert a row with `scopeReviewAbout("headB", "headB")` (recipient = subject; the schema allows it). Results: headB `GET` -> 200 with the body; headB's list contains it; oda `:uphold` and `:mark-adjusted` -> 403 `PLATFORM_IDENTITY_PERMISSION_ADJUSTMENT_HANDLER_NOT_ALLOWED`; headB `:uphold` -> 403 `PLATFORM_IDENTITY_OWN_PERMISSION_ADJUSTMENT_FORBIDDEN`. The F-1 card says reading an item about oneself is 404 (invariant 19; readers table row "当事人本人").
   - Why this is not a must-fix: ruling #730 comment 6604945364 makes the generator (S1) store NULL as the recipient when the head is the moved person. `origin/main`'s `OpenAssignmentReviews.openScopeReviewFor` already filters `!candidate.equals(mover)`. This branch's merge base has the older generator without that filter, so the self-addressed row only becomes unreachable once main is merged in. I verified the main code by reading it, not by running it.
   - Suggested fix (defence in depth): `employee.equals(item.recipient()) && !isSubject(item)` in Java, and `(recipient_employee_id = :employee and user_id is distinct from :user)` in SQL. Optionally also a CHECK, or an application check, that the recipient is not the subject's employee.
2. A NUL character in `reason` returns 500. Body `{"reason":"a\u0000b","businessOwnerEmployeeId":"<owner>"}` -> 500 `PLATFORM_WEB_UNEXPECTED`. `UpholdReason` accepts it and Postgres text rejects 0x00. The transaction rolled back, so the row and version were unchanged; a later uphold with a fresh ETag succeeded. Reject `\u0000` in `UpholdReason` (400). This is probably a platform-wide gap in text inputs.
3. A reason made only of a no-break space is accepted. `{"reason":" ",...}` -> 200 UPHELD. `String.isBlank()` does not treat U+00A0 as whitespace and `btrim` only trims ' '. If "not blank" means visible text, strip `\p{Z}`/`\s` before the check (application and CHECK).
4. Latent: `PermissionAdjustment(Stored)` does not restore `upholdReason`, `adjustedBy` or `adjustedWith`, and `update()` writes all of those columns from the aggregate. Today only OPEN items are updated (`requireOpen`), so nothing is lost. A later slice that updates an UPHELD item through `update()` would write `uphold_reason = NULL` and violate `ck_permission_adjustments_uphold`. I found this by reading only; I did not run it.

## Attempts that did not break it

| Attack | How | Result |
|---|---|---|
| Concurrent double uphold, same If-Match | oda and oda2 `:uphold` in parallel through a latch, 5 rounds | Each round: exactly one 200 and one 409 `PLATFORM_IDENTITY_CONCURRENT_UPDATE`; row UPHELD once, version 1, event_count 1 |
| Stale / weak / odd If-Match | `W/"0"`, `*`, `"-0"` | 400 VERSION_MALFORMED, 400, 412; existing tests already cover missing (428) and stale (412) |
| Out-of-scope reads | plain user `GET` of oda-visible item; random id | Both 404 with identical body (`instance` = `urn:forcitis:error:not-found`, no id echoed) |
| Subject without codes | mover `GET` own item -> 404; mover `:uphold` -> 403 OWN_..._FORBIDDEN | Matches ruling 6602368110 (command 403 on own item) |
| Unrelated head | headA `:uphold` on item addressed to headB | 404 |
| Admin on addressed item | oda `:uphold` on item addressed to headB | 403 HANDLER_NOT_ALLOWED (invariant 22) |
| Recipient uphold with owner | headB `:uphold` with businessOwnerEmployeeId on a scope review | 400; without owner 200 UPHELD, no owner or expiry stored |
| Business owner edges | separated employee, the subject's employee, random UUID, upper-case (non-canonical) UUID | All 400, row unchanged |
| Body edges | `null`, empty body, both references, malformed UUID, numeric id | All 400 |
| List filters | `status=upheld`, `kind=X`, `limit=0` | 400 |
| Cursor tampering | cursor of another list; cursor naming a nonexistent id; cursor naming an existing item for a caller with no scope | 400 CURSOR_INVALID; 200 empty; 200 empty (scope is still applied in the query) |
| Java rule vs SQL | Compared `canRead` with the `findFor` WHERE clause, including null `user_id` (role items) | Equivalent: `user_id is distinct from :user` vs `!user.equals(null)`; recipient branch identical (suggestion 1 applies to both alike) |
| Quarter expiry | `UpholdExpiry` arithmetic for months 3/9/10/12; the IT boundaries 09-30T16:00Z, 12-31T15:59Z, 12-31T16:00Z | Correct; also `uphold_expires_at > upheld_at` always holds because expiry is the next quarter start |
| Reason length vs DB | `codePointCount` <= 500 vs `char_length` <= 500 | Consistent (Postgres counts code points) |
