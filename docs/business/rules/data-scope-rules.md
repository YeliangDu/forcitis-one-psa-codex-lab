# Data scope business rules — RULE-387, RULE-393..RULE-417

Scope: Phase 1a `platform.identity`, data scope of roles whose access scope is `CUSTOM_ORG_UNITS` (ADR-0026 「1a 交付范围」, ADR-0032 第 4–5 项, ADR-0034 第 5 项). These are rules of FORCITIS one itself; the old system had no custom-department scope that survived an org change. Origin: F-1 AEC practice check item 4 (`phase1a/cards/F-1-aec-check.md`, one-1a-par), handed to one-rules by one-coordinator on 2026-10-15; the gap was that a revoked or merged department silently narrowed the scope.

| ID | Domain | Title | Priority | Review |
|---|---|---|---|---|
| RULE-387 | identity | A revoked or merged department in a custom scope raises an alert and a scope review; the reference is kept, never replaced | P0 | No |
| RULE-393 | identity | 「本人及直接下属」 covers one level of reports only; a whole reporting line is given through a department scope | P1 | No |
| RULE-394 | identity | An overdue permission adjustment is escalated, never revoked automatically | P0 | No |
| RULE-395 | identity | Sensitive roles are suspended during leave or secondment of more than 30 days and restored on return | P1 | No |
| RULE-396 | identity | From 1b, a quote is visible by department scope or to its owner and team members | P1 | No |
| RULE-417 | identity | A transfer scope review whose new department has no head goes to the organization data administrator, who only forwards it; no business owner field, no expiry | P1 | No |

### RULE-387: A revoked or merged department in a custom scope raises an alert and a scope review; the reference is kept, never replaced
**Category:** Policy
**Priority:** P0
**Source:** one-coordinator 2026-10-15 (F-1 AEC check item 4); user decision F1-01 (intent `2026-10-15-221431-one-rules-数据访问范围决策页-F-1-第一.md`); ADR-0032 第 1 项第 3 条; ADR-0032 第 5 项 (generic permission-adjustment model: kind + dedupe key); ADR-0034 第 5 项 (custom departments live on the role version)
**Plain English:** After a successful org sync, every role version in use whose custom departments name a department that the sync made inactive or removed (a merge in A8 shows up as the old department going inactive while its people move) gets one 「范围待复核」 (`SCOPE_REVIEW`) item per role and department, handled by the organization data administrator and readable (only) by the security auditor, and an ALERT is logged with the role, the department and the sync run. The role version keeps the reference exactly as it was: the system never swaps in the successor department, never widens the scope to it, and never edits the role. While the reference points at an inactive department, that department contributes no one to the scope, and with 含下级 only its still-active descendants under the current snapshot count (ADR-0026 第 1 项第 3 条), so the scope can only shrink until a person acts. Acting follows ADR-0032 第 1 项第 3 条: dropping the department from the role is a narrowing and goes through the normal role-version flow; covering the successor department is a widening, so for a role at the two-person, per-person level that has holders it is refused (409) and a new role is created and granted person by person, and without holders it goes through the access request. For roles at that level the custom departments are 「仅本部门」 only (user decision F1-01, 2026-10-15). The item closes when the role gets a new version that no longer names the department, or when the organization data administrator upholds it with a reason. **Pending (ADR-0044 Q1):** whether the uphold must also name the business owner who confirmed, and whether it lasts only until the next quarterly review. User decision F1-02 covers the sensitive-role-holder items of ADR-0032 第 4 项第 3 条; whether it extends to this source is not decided. A later sync that changes nothing raises no new item (dedupe key: role, department); the department coming back to active closes the open item with the reason recorded.
**Specification:**
- Given role R (`CUSTOM_ORG_UNITS`) naming department D, When a sync makes D inactive, Then one `SCOPE_REVIEW` item for (R, D) is open, an ALERT is logged once, and R's current version still names D.
- Given the item above is open, When the next sync changes nothing about D, Then no second item and no second ALERT appear.
- Given D inactive and named with 含下级, When the scope of a holder of R is computed, Then people of D are out of scope and people of D's still-active descendants are in scope; nobody from D's successor is in scope.
- Given D merged into E (D inactive, its people now in E), When the scope is computed, Then the people now in E are out of scope unless R also names E.
- Given the item open, When R gets a new version without D, Then the item closes as adjusted with the role-version audit id.
- Given the item open, When the sync makes D active again, Then the item closes with that reason and the scope is as before.
**Tests:** to be written by the test agent with F-1 (one-1a-par card 4).
**Confidence:** Medium — business owner and validity of an uphold are open (ADR-0044 Q1); the item's subject is a role, not a person, so the permission-adjustment model gains a role-subject kind; to be confirmed in the F-1 implementation card.

### RULE-393: 「本人及直接下属」 covers one level of reports only; a whole reporting line is given through a department scope
**Category:** Policy
**Priority:** P1
**Source:** user decision F1-03, 2026-10-15 (data-scope decision page)
**Plain English:** `SELF_AND_DIRECT_REPORTS` is the person plus those whose supervisor is the person, one level deep. No scope type for the whole reporting line is added; someone who needs it (a branch head) gets 「本部门含下级」 or custom departments, granted explicitly. Least privilege by default (ISO/IEC 27002 5.15, 5.18).
**Specification:**
- Given A supervises B and B supervises C, When A's scope is `SELF_AND_DIRECT_REPORTS`, Then A and B are in scope and C is not.
**Tests:** F-1 acceptance (one-1a-par).
**Confidence:** High.

### RULE-394: An overdue permission adjustment is escalated, never revoked automatically
**Category:** Policy
**Priority:** P0
**Source:** user decision F1-04, 2026-10-15, confirming ADR-0032 0032-Q2 and Q3
**Plain English:** A permission-adjustment item still open after 5 workdays is escalated to the holders of `account-role:update`; nothing is granted or revoked by the deadline, so a wrong org edit from A8 cannot strip a key role. This is also the default of common access-review products (SailPoint, Microsoft Entra keep access when reviewers do not answer).
**Specification:**
- Given an item open for more than 5 workdays, When the deadline job runs, Then the item is escalated once and every grant stays as it was.
**Tests:** F-1 acceptance (one-1a-par).
**Confidence:** High.

### RULE-395: Sensitive roles are suspended during leave or secondment of more than 30 days and restored on return
**Category:** Policy
**Priority:** P1
**Source:** user decision F1-06, 2026-10-15; delivered with the phase that brings leave data from attendance (not 1a)
**Plain English:** Only roles granted person by person with a second confirmation are affected. When a holder starts continuous leave or a secondment longer than 30 days, those roles stop working from the first day and work again on the return day; the grants are not deleted, and both changes are audited. Other roles and ordinary accounts are not suspended.
**Specification:**
- Given a holder of a sensitive role on 45 days of leave, When the leave starts, Then the role is suspended and audited; When the holder returns, Then it works again and is audited.
- Given a 20-day leave, Then nothing is suspended.
**Tests:** with the attendance phase.
**Confidence:** Medium — the leave data source is fixed in that phase.

### RULE-396: From 1b, a quote is visible by department scope or to its owner and team members
**Category:** Policy
**Priority:** P1
**Source:** user decision F1-07, 2026-10-15
**Plain English:** A quote is visible to a person whose data scope covers the quote's department, or who is its owner or a member of its team (union of the two). People working on a quote from another department need no wider department scope. Same shape as project-role access (project line and functional line combined).
**Specification:**
- Given a quote of department D with owner O and team member T from department E, When T has a scope of E only, Then T sees the quote; a colleague of T in E who is not on the team does not.
**Tests:** with the 1b quoting card.
**Confidence:** High.

### RULE-417: A transfer scope review whose new department has no head goes to the organization data administrator, who only forwards it; no business owner field, no expiry
**Category:** Policy
**Priority:** P1
**Source:** user decision N1-09, 2026-10-17 (intent `2026-10-17-085044-one-rules-N1-页提交-N1-202610.md`, preselected recommendation unchanged); fills a gap in ADR-0044 item 2 point 6, which only says that a scope review from a transfer is handled by the new department's head (one-architecture: not an ADR change, a business detail kept in this card)
**Plain English:** When a person with a custom-department or listed-legal-entity scope transfers, the new department's head normally decides whether to keep or adjust the scope. If the new department has no head, the entry goes to the organization data administrator, the same path as a scope review caused by a revoked or merged department (RULE-387). The administrator only forwards the entry; keeping or adjusting is still confirmed by the person who needs the scope, and the administrator may name that person in the reason. The entry has no business owner field and no expiry. Generating such an entry is logged as an alert once. If a head is appointed later the entry is not reassigned.
**Specification:**
- Given a transfer into a department with no head, When the scope review is generated, Then the recipient is empty, the entry is handled by the organization data administrator, and one alert is logged.
- Given a head appointed afterwards, Then the entry stays with the administrator.
- Given the entry is kept, Then no business owner and no expiry are recorded; the reason may name who confirmed.
**Tests:** with the F-1 card.
**Confidence:** High.
