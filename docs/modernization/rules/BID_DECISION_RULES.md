# Bid / no-bid decision rules — RULE-414..RULE-415

Scope: the independent flow 「作出参投决策」 on an opportunity (OBJ-18), VS-01.2, for every opportunity (tender, invited negotiation, direct commission); PRC-183 in the sales design. Basis: user decision VS01-10, 2026-10-16 (intent `2026-10-16-211806-one-coordinator-选定服务方这个价值流阶段-有一个.md`: 「按你的建议，做成独立流程挂在商机上」). The decision, with its outcomes 「决定参投」 or 「放弃」 (VS-01.2 exit 「丢单 / 放弃」), is made by rule, not by approval. What the user did not state (the margin floor, the capacity limit, the scale and weight of the scores, the person who adds the extra level) is left to configuration and is marked open; nothing is invented.

| ID | Domain | Title | Priority | Review |
|---|---|---|---|---|
| RULE-414 | opportunity | The owner of the opportunity decides to bid or not, with a checklist, a required reason and five scores, and the decision is kept as a trace; no approval | P0 | No |
| RULE-415 | opportunity | A decision to bid that falls below the margin floor, exceeds capacity or meets an independence conflict adds one approval level | P0 | No |

### RULE-414: The owner of the opportunity decides to bid or not, with a checklist, a required reason and five scores, and the decision is kept as a trace; no approval
**Category:** Policy
**Priority:** P0
**Source:** user decision VS01-10, 2026-10-16 (see above)
**Plain English:** Every opportunity gets one decision, 「决定参投」 or 「放弃」, made by the opportunity's owner. The system will not accept the decision until the checklist is completed, a reason is written, and the five scores are given: risk, capacity, win rate, profit, customer credit. The decision, its reason, the scores and who and when are kept as a trace and cannot be altered afterwards (a changed mind is a new decision that refers to the old one). In the normal case there is no approval.
**Specification:**
- Given an opportunity whose checklist is complete, a reason is written and five scores are given, When its owner chooses 「决定参投」, Then the decision is recorded and the opportunity moves on, with no approval task.
- Given a missing reason or a missing score, Then the decision is refused and names what is missing.
- Given 「放弃」, Then the opportunity ends as 「丢单 / 放弃」 and the reason is kept.
- Given a recorded decision, When the owner changes their mind, Then a new decision is recorded that refers to the earlier one; the earlier one is not edited.
**Open (configuration, not decided):** the checklist items, the scale of each score (for example 1 to 5) and any weighting or minimum total. They are versioned configuration (SESSION_PLAYBOOK 附 M3「规则调整三类」) and the user sets them on a later page.
**Tests:** to be written with the sales card.
**Confidence:** High for the shape; the checklist and scales are open.

### RULE-415: A decision to bid that falls below the margin floor, exceeds capacity or meets an independence conflict adds one approval level
**Category:** Policy
**Priority:** P0
**Source:** user decision VS01-10, 2026-10-16; approval escalation table (abnormal = one extra level)
**Plain English:** When the decision is 「决定参投」 and any of these holds, the decision does not take effect until one extra approver agrees: the expected profit is below the margin floor; the capacity needed exceeds the capacity available; the independence check shows a conflict. The system computes the three conditions from the scores and the data; no person judges whether the condition applies. This is an abnormal case, one level (not two).
**Specification:**
- Given a decision to bid with expected margin below the floor, Then it waits for one extra approver.
- Given a decision to bid with capacity exceeded, Then it waits for one extra approver.
- Given a decision to bid with an independence conflict, Then it waits for one extra approver.
- Given none of the three, Then RULE-414 applies and there is no approval.
- Given 「放弃」, Then no condition applies.
**Decided (N1-03 to N1-06, N1 resubmitted as N1-20261017-091846-15s, all preselected recommendations unchanged):** the margin floor is set per business line, as a contribution-margin percentage on the quoted price, the values given by finance as experience values and adjustable in configuration; capacity is compared as required person-days against available person-days per post, from the staffing schedule, the available limits given by each business line's resource owner; the extra approval role is named per trigger reason (not one role for all), and the roles are configuration items set at go-live, not written in this card (the user left the note empty); the triggers are the first three only, more can be added later in configuration (the earlier choice of two added triggers, a customer credit score and a transaction price, was replaced in the resubmission, so no threshold for them exists).
**Still open (configuration only):** the margin floor values, the capacity limits and the role for each trigger, all set at go-live; no further user decision is needed. Role names go to the glossary when configured.
**Tests:** to be written with the sales card.
**Confidence:** Medium.
