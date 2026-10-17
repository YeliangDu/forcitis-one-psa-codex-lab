# Correcting a legal-entity attribution period — RULE-416

Scope: `organization` (legal-entity attribution periods) and the cost postings made while a wrong attribution was in force. Basis: user decisions N1-07 and N1-08, 2026-10-17 (intent `2026-10-17-085044-one-rules-N1-页提交-N1-202610.md`, preselected recommendations unchanged). The correction of the attribution itself is DATA-RULE-07 (one-data's card, to be revised to this form); this rule covers what happens to postings already made.

| ID | Domain | Title | Priority | Review |
|---|---|---|---|---|
| RULE-416 | organization | After an attribution period is corrected, costs already posted in the corrected span are adjusted in the open accounting period and carry the original period as a mark | P1 | No |

### RULE-416: After an attribution period is corrected, costs already posted in the corrected span are adjusted in the open accounting period and carry the original period as a mark
**Category:** Policy
**Priority:** P1
**Source:** user decisions N1-07 and N1-08, 2026-10-17; ADR-0045 decision 2 (a business date in a closed period is booked into the open period with the original period as a mark)
**Plain English:** A legal-entity attribution period is the span in which a person or department belongs to a legal entity; it is not an accounting period. When it was ended too late, the correction is an appended correction version with a reason, handled by the organization data administrator; the old row is never changed. Costs posted in the corrected span under the wrong legal entity are not rewritten in closed periods. They are adjusted by entries in the currently open accounting period, each marked with the original period.
**Specification:**
- Given an attribution ended too late and corrected by an appended correction version, Then the query reads the corrected attribution from then on, and the old row stays.
- Given costs already posted in the corrected span under the old legal entity in a closed period, Then adjustment entries are made in the open period, marked with the original period; the closed period is not changed.
- Given the correction has no reason, Then it is refused.
**Open:** whether the correction needs a second person's confirmation was offered and not chosen (the user kept the administrator alone); who creates the adjustment entries (system or finance) is for the finance design to settle.
**Tests:** to be written with the organization and ledger cards.
**Confidence:** Medium.
