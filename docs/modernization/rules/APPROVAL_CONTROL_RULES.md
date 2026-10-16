# Approval control rules replacing manual approval steps — RULE-406..RULE-411

Scope: Phase 2 documents that go through `platform.approval` (reimbursement, invoicing, payment, procurement, receipts). These are rules of FORCITIS one. Basis: user principle 「能通过规则和不变量守卫的就不要人来守」 (relayed by one-coordinator, 2026-10-16) and the approval escalation table `architecture/proposals/closeout/APPROVAL_RULES.yaml` (branch one-architecture-adr-governance), where AR-03, AR-04, AR-05, AR-08, AR-09, AR-10 no longer add an approval node (`guard: rule`). Where the user has not decided a scope, the rule says so and is marked Review = Yes; nothing is extended from a neighbouring decision.

Each rule is checked by the system when the document is submitted and again when it is committed; a failed check refuses with its own error code (to be allocated in the kernel/ module catalog) or, for RULE-406, only warns. None of them approves, rejects on behalf of a person, or skips the base approver.

| ID | Domain | Title | Priority | Review |
|---|---|---|---|---|
| RULE-406 | approval | A reimbursement that exceeds its budget only warns; it never blocks or adds an approval node | P1 | Yes |
| RULE-407 | approval | A document that needs a contract or agreement is refused when it cites none that is in force | P0 | Yes |
| RULE-408 | approval | A business date in a closed period is booked into the open period and keeps the original period as a mark | P0 | No |
| RULE-409 | approval | A first-time supplier or subcontractor must have passed counterparty admission; a customer needs a verified record only | P0 | No |
| RULE-410 | approval | A payee or receipt party that is not the contract counterparty's registered account is refused | P0 | Yes |
| RULE-411 | approval | An invoice before the agreement is countersigned is allowed only for the advance or first-payment type with commission or award evidence | P0 | No |

### RULE-406: A reimbursement that exceeds its budget only warns; it never blocks or adds an approval node
**Category:** Policy
**Priority:** P1
**Source:** user decision F-Q2, 2026-10-16 (intent `2026-10-16-162821-one-architecture-流程样板页提交-三组整体均-符合.md`, 「只预警」; INV-102); AR-03
**Plain English:** When the budget balance after reserving the document is below zero, the system shows a warning to the submitter, the approver and the budget owner and records it. It does not refuse and does not add an approval level.
**Specification:**
- Given a reimbursement whose reserved amount makes the budget balance negative, When it is submitted, Then it is accepted with a warning event and the usual base approval path.
- Given the budget owner, Then they receive the warning; no approval task is created for them.
**Open (Review):** the user answered for reimbursement. Whether other documents (procurement, payment) also only warn is not decided; until then they are out of this rule.
**Tests:** to be written with the Phase 2 reimbursement card.
**Confidence:** High for reimbursement; open for other documents.

### RULE-407: A document that needs a contract or agreement is refused when it cites none that is in force
**Category:** Validation
**Priority:** P0
**Source:** AR-04; user decision P-1 (intent `2026-10-16-074053-one-architecture-知识文档与回签前开票确认页提交.md`: revenue recognition requires an agreement in force); ADR-0047
**Plain English:** A document that depends on a contract (invoice request, revenue recognition, and, if confirmed, payment and procurement) must reference an agreement whose status is in force on the business date. Otherwise it is refused, with the exceptions of RULE-411 (invoice before countersigning) and the 31 migrated contracts that are marked as exceptions.
**Specification:**
- Given a revenue recognition citing an agreement that is not yet in force, Then it is refused.
- Given one of the 31 migrated historical contracts marked as an exception, Then the check passes and the exception mark is kept on the document.
- Given an invoice request of the advance or first-payment type with the evidence of RULE-411, Then RULE-407 does not refuse it.
**Open (Review):** the user decided revenue recognition and invoicing only. Payment and procurement needing an agreement in force is a proposal from the approval table; ask the user before it is enforced.
**Tests:** to be written with the invoicing and revenue card.
**Confidence:** High for revenue and invoicing; medium for payment and procurement.

### RULE-408: A business date in a closed period is booked into the open period and keeps the original period as a mark
**Category:** Policy
**Priority:** P0
**Source:** AR-05; ADR-0045 decision 2 (scenario S7)
**Plain English:** When the business date falls in a closed period, the system books the document into the current open period and marks the original period. No approval node is added. Documents across a year end are not covered: ADR-0045 U-2 is undecided, so the system refuses them and alerts until it is decided.
**Specification:**
- Given a business date in a closed month of the same year, Then the booking period is the open period and the document carries the original period.
- Given a business date in a previous year, Then the document is refused with an alert (ADR-0045 U-2 undecided).
**Tests:** to be written with the period-closing card.
**Confidence:** High.

### RULE-409: A first-time supplier or subcontractor must have passed counterparty admission; a customer needs a verified record only
**Category:** Validation
**Priority:** P0
**Source:** AR-08; user decision P-3 (intent `2026-10-16-120544-one-architecture-售前回溯确认页提交-P-1-P.md`: the customer role is not admitted, a verified record is enough; suppliers and subcontractors go through admission; INV-26 「正式往来单位」 = record verified)
**Plain English:** The first document with a counterparty does not need an extra approver. The system checks the counterparty status instead: a supplier or subcontractor without completed admission is refused; a customer with a verified record passes.
**Specification:**
- Given a first payment to a supplier whose admission is not completed, Then it is refused.
- Given a first invoice to a customer whose record is verified, Then it passes with the base approval path.
**Tests:** to be written with the counterparty card.
**Confidence:** High.

### RULE-410: A payee or receipt party that is not the contract counterparty's registered account is refused
**Category:** Validation
**Priority:** P0
**Source:** AR-09 (proposed from the user principle; no separate user decision)
**Plain English:** The payee of a payment, or the party named on a receipt, must be the counterparty of the cited agreement or a bank account registered for that counterparty. Anything else is refused. Correcting it means registering the account through the counterparty record, which has its own control.
**Specification:**
- Given a payment whose account is not registered for the agreement's counterparty, Then it is refused.
- Given a payment to a registered account of the counterparty, Then it passes.
**Open (Review):** not decided by the user. Cases that need a third party payee (assignment of claims, payment on behalf) are not described; ask before enforcing.
**Tests:** to be written with the payment card.
**Confidence:** Medium.

### RULE-411: An invoice before the agreement is countersigned is allowed only for the advance or first-payment type with commission or award evidence
**Category:** Policy
**Priority:** P0
**Source:** user decision P-1 (intent `2026-10-16-074053-one-architecture-知识文档与回签前开票确认页提交.md`); AR-10
**Plain English:** Revenue recognition always needs an agreement in force. An invoice may come before countersigning only if its type is advance payment or first payment and it attaches a commission or an award notice as evidence. Every other invoice before countersigning is refused. The 31 historical contracts are migrated with an exception mark.
**Specification:**
- Given an advance-payment invoice request with an award notice attached and the agreement not yet in force, Then it passes.
- Given a progress-payment invoice request and the agreement not yet in force, Then it is refused.
- Given an advance-payment request without commission or award evidence, Then it is refused.
**Tests:** to be written with the invoicing card.
**Confidence:** High.
