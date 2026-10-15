# Data scope business rules — RULE-387

Scope: Phase 1a `platform.identity`, data scope of roles whose access scope is `CUSTOM_ORG_UNITS` (ADR-0026 「1a 交付范围」, ADR-0032 第 4–5 项, ADR-0034 第 5 项). These are rules of FORCITIS one itself; the old system had no custom-department scope that survived an org change. Origin: F-1 AEC practice check item 4 (`phase1a/cards/F-1-aec-check.md`, one-1a-par), handed to one-rules by one-coordinator on 2026-10-15; the gap was that a revoked or merged department silently narrowed the scope.

| ID | Domain | Title | Priority | Review |
|---|---|---|---|---|
| RULE-387 | identity | A revoked or merged department in a custom scope raises an alert and a scope review; the reference is kept, never replaced | P0 | No |

### RULE-387: A revoked or merged department in a custom scope raises an alert and a scope review; the reference is kept, never replaced
**Category:** Policy
**Priority:** P0
**Source:** one-coordinator 2026-10-15 (F-1 AEC check item 4); ADR-0032 第 5 项 (generic permission-adjustment model: kind + dedupe key); ADR-0034 第 5 项 (custom departments live on the role version)
**Plain English:** After a successful org sync, every role version in use whose custom departments name a department that the sync made inactive or removed (a merge in A8 shows up as the old department going inactive while its people move) gets one 「范围待复核」 (`SCOPE_REVIEW`) item per role and department, sent to the organization data administrator and the security auditor, and an ALERT is logged with the role, the department and the sync run. The role version keeps the reference exactly as it was: the system never swaps in the successor department, never widens the scope to it, and never edits the role. While the reference points at an inactive department, that department contributes no one to the scope, and with 含下级 only its still-active descendants under the current snapshot count (ADR-0026 第 1 项第 3 条), so the scope can only shrink until a person changes the role through the normal role-version flow (which itself needs the second person for sensitive roles). The item closes when the role gets a new version that no longer names the department, or when it is upheld with a reason. A later sync that changes nothing raises no new item (dedupe key: role, department); the department coming back to active closes the open item with the reason recorded.
**Specification:**
- Given role R (`CUSTOM_ORG_UNITS`) naming department D, When a sync makes D inactive, Then one `SCOPE_REVIEW` item for (R, D) is open, an ALERT is logged once, and R's current version still names D.
- Given the item above is open, When the next sync changes nothing about D, Then no second item and no second ALERT appear.
- Given D inactive and named with 含下级, When the scope of a holder of R is computed, Then people of D are out of scope and people of D's still-active descendants are in scope; nobody from D's successor is in scope.
- Given D merged into E (D inactive, its people now in E), When the scope is computed, Then the people now in E are out of scope unless R also names E.
- Given the item open, When R gets a new version without D, Then the item closes as adjusted with the role-version audit id.
- Given the item open, When the sync makes D active again, Then the item closes with that reason and the scope is as before.
**Tests:** to be written by the test agent with F-1 (one-1a-par card 4).
**Confidence:** Medium — the item's subject is a role, not a person, so the permission-adjustment model gains a role-subject kind; to be confirmed in the F-1 implementation card.
