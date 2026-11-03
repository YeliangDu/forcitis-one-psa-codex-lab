# F-1 #730 S3 acceptance notes: HTTP API of the permission adjustments (and 7.3)

Sources: f-1.md invariants 19, 22, 23, 27, 28, 31-33; ADR-0044 §2; ADR-0026 §6 item 5; one-design rulings on #730: 6602101721 (audit action names) and 6602368110 (API contract, authorizationRequestId path, SCOPED read denial). Items are written straight into the table (generation is S1); S2 covers the jobs.

| Done-when | Test |
|---|---|
| List `{items,nextCursor}`, fields, role items carry roleId/orgUnitId, both readers see every kind, no permission codes or scope | `ReadApiIT.bothReadersSeeEveryKindAndNoPermissionCodeOrScope` |
| Unrelated callers: list 200 and empty, detail 404 with the same errorCode/title as a missing id, plus the `access.denied` audit | `ReadApiIT.callersOutOfScopeSeeAnEmptyListAndNotFoundAlike` |
| Own items left out of the caller's list; own detail 404 | `ReadApiIT.aListLeavesOutTheCallersOwnItems` |
| Code holder whose only item is its own: 200, empty, no cursor | `PermissionAdjustmentEmptyScopeIT` (own database) |
| Recipient head sees only items addressed to them; others 404; old head sees nothing | `ReadApiIT.theNewHeadSeesOnlyTheItemsAddressedToThem` |
| No recipient: administrator reads; roles add up | `ReadApiIT.anItemWithoutARecipientIsReadByTheAdministratorAndTwoRolesAddUp` |
| Detail ETag, 404, 400; paging; `viewed` audit only for allowed reads | `ReadApiIT.aDetailCarriesAVersionAndAbsentIdsAreRefused`, `pagingWalksEveryItemOnceAndEndsWithoutACursor`, `readingAnItemIsAuditedAsViewed` |
| Uphold sensitive review: stored values, grants unchanged | `ActionApiIT.anAdministratorUpholdsASensitiveReview` |
| Expiry = next natural quarter start 00:00 Asia/Shanghai (four boundaries); handler cannot choose it; owner may be the handler | `theExpiryIsTheNextQuarterStartInShanghai`, `theHandlerCannotChooseTheExpiry`, `theBusinessOwnerMayBeTheHandler` |
| Ten invalid requests 400 untouched; reason limit counts characters (500/501 CJK); If-Match 428/412 | `invalidUpholdRequestsAreRefusedAndChangeNothing`, `theReasonLimitCountsCharactersNotBytes`, `upholdNeedsTheCurrentVersion` |
| Eligibility: auditor 403, out of scope 404, own item 403 with PLATFORM_IDENTITY_OWN_PERMISSION_ADJUSTMENT_FORBIDDEN, another administrator may uphold the item about an administrator | `onlyAnotherAdministratorMayUpholdASensitiveReview` |
| Audit `PermissionAdjustmentUpheld` / `PermissionAdjustmentAdjusted` (event type names) | `upholdingIsAudited`, `anAdministratorMarksAnItemAdjusted` |
| Non-OPEN item takes no further command: 409, row unchanged | `anUpheldItemTakesNoFurtherCommand` |
| Scope review: recipient only; no recipient to the administrator; inactive-unit item to the administrator only | `onlyTheRecipientUpholdsATransferScopeReview`, `anItemWithoutARecipientGoesToTheAdministratorAndTwoRolesAddUp`, `anInactiveUnitItemIsHandledByTheAdministratorOnly` |
| Mark adjusted: audit entry / authorization request each set only their column; exactly one reference; unknown reference 400; auditor 403; out of scope 404; own item 403 | `anAdministratorMarksAnItemAdjusted`, `eachReferenceKindSetsOnlyItsOwnColumn`, `markAdjustedNeedsExactlyOneReference`, `anUnknownReferenceIsABadRequest`, `markAdjustedIsRefusedOutOfScopeAndOnTheCallersOwnItem` |
| 7.3: delivery subject kind PERMISSION_ADJUSTMENT, purpose PERMISSION_ADJUSTMENT_ASSIGNED; existing values stay; near-misses refused | `platform/integration/OutboundDeliveryPermissionAdjustmentRowRulesIT` (3) |

## Red / green
Red on main (no implementation): the API classes fail on 404 (no route), the fixtures insert cleanly; the 7.3 accept case is red on `ck_outbound_deliveries_purpose_kind`, the other two 7.3 cases are green on the baseline by design. Green: 7.3 with a scratch migration adding the two values (3/3), not committed. The API cases have no green proof (unverified until the implementation exists).

## Not covered / doubts
- Whether the subject without any code (mover) gets the own-item error code or a plain 403 on a command: asserted 403 only.
- 409 error code; mark-adjusted by the recipient head or on source-1 items (invariant 22 says the recipient "only upholds"; ambiguous for marking adjusted); 428/412 for mark-adjusted (tested for uphold only).
- Own-item detail 404 compares status only, not the body with a missing id's body.
- Escalation and "no administrator to handle" delivery (R-4) are not asserted.
