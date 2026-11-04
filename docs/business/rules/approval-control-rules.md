# Approval control rules replacing manual approval steps — RULE-406..RULE-411

Scope: Phase 2 documents that go through `platform.approval` (reimbursement, invoicing, payment, procurement, receipts). These are rules of FORCITIS one. Basis: user principle 「能通过规则和不变量守卫的就不要人来守」 (relayed by one-coordinator, 2026-10-16) and the approval escalation table `docs/architecture/proposals/closeout/approval-rules.yaml` (removed 2026-11-04; read it at commit 1f6f97d1b) (branch one-architecture-adr-governance), where AR-03, AR-04, AR-05, AR-08, AR-09, AR-10 no longer add an approval node (`guard: rule`). Where the user has not decided a scope, the rule says so; nothing is extended from a neighbouring decision.

Each rule is checked by the system when the document is submitted and again when it is committed; a failed check refuses with its own error code (to be allocated in the kernel/ module catalog) or, for RULE-406, only warns. None of them approves, rejects on behalf of a person, or skips the base approver.

| ID | Domain | Title | Priority | Review |
|---|---|---|---|---|
| RULE-406 | approval | A document that exceeds its budget only warns; it never blocks or adds an approval node | P1 | No |
| RULE-407 | approval | A document that needs a contract or agreement is refused when it cites none that is in force; a tender deposit is the one exception, with its own payment approval | P0 | No |
| RULE-408 | approval | A business date in a closed period is booked into the open period and keeps the original period as a mark | P0 | No |
| RULE-409 | approval | A first-time supplier or subcontractor must have passed counterparty admission; a customer needs a verified record only | P0 | No |
| RULE-410 | approval | A payee or receipt party that is not the contract counterparty's registered account is refused | P0 | No |
| RULE-411 | approval | An invoice before the agreement is countersigned is allowed only for the advance or first-payment type with commission or award evidence | P0 | No |

### RULE-406: A document that exceeds its budget only warns; it never blocks or adds an approval node
**Category:** Policy
**Priority:** P1
**Source:** user decision F-Q2, 2026-10-16 (intent `2026-10-16-162821-one-architecture-流程样板页提交-三组整体均-符合.md`, 「只预警」; INV-102), widened to purchase, payment and subcontract by the threshold and scope page, intent `2026-10-16-171147` (relayed by one-architecture; the fragment file was not yet in any branch when this was written); AR-03
**Plain English:** When the budget balance after reserving the document is below zero, the system shows a warning to the submitter, the approver (who sees an over-budget notice on the task) and the budget owner and records it. It does not refuse and does not add an approval level.
**Specification:**
- Given a reimbursement whose reserved amount makes the budget balance negative, When it is submitted, Then it is accepted with a warning event and the usual base approval path.
- Given the budget owner, Then they receive the warning; no approval task is created for them.
**Scope:** reimbursement, purchase, payment and subcontract documents alike.
**Tests:** to be written with the Phase 2 reimbursement card.
**Confidence:** High.

### RULE-407: A document that needs a contract or agreement is refused when it cites none that is in force
**Category:** Validation
**Priority:** P0
**Source:** AR-04; user decision P-1 (intent `2026-10-16-074053-one-architecture-知识文档与回签前开票确认页提交.md`: revenue recognition requires an agreement in force); ADR-0047
**Plain English:** A document that depends on a contract (invoice request, revenue recognition, and, if confirmed, payment and procurement) must reference an agreement whose status is in force on the business date. Otherwise it is refused, with the exceptions of RULE-411 (invoice before countersigning) and the 31 migrated contracts that are marked as exceptions.
**Specification:**
- Given a revenue recognition citing an agreement that is not yet in force, Then it is refused.
- Given one of the 31 migrated historical contracts marked as an exception, Then the check passes and the exception mark is kept on the document.
- Given an invoice request of the advance or first-payment type with the evidence of RULE-411, Then RULE-407 does not refuse it.
**Exception, deposit for a tender (保证金):** a bid or performance deposit is a payment without an agreement in force. It is not refused by this rule but goes through its own payment approval, on the basis of the tender document (user decision VS01-10, intent `2026-10-16-211806-one-coordinator-选定服务方这个价值流阶段-有一个.md`). User decision N1-01 (intent `2026-10-17-085044-one-rules-N1-页提交-N1-202610.md` (N1-01, submitted 2026-10-17, preselected recommendation unchanged)): the payee name and the account must be the same as the tender document states (the payee need not be the tenderer in person, for example a public resource trading centre's deposit account); a performance deposit follows the same rule against the contract; a tender deposit is paid from the company's basic account. The person who handles the deposit enters the payee and account in the payment request and the system checks them against the tender document registered with the opportunity, refusing when they differ.
**Payment and purchase:** also require an agreement in force, decided on the threshold and scope page (intent `2026-10-16-171147` (relayed by one-architecture; the fragment file was not yet in any branch when this was written)). Small incidental purchases are exempt; the amount below which a purchase counts as small is **threshold to be decided** after one-ops has read the amount distribution (summary only, registered per ADR-0037). Until then no purchase is exempt.
**Tests:** to be written with the invoicing and revenue card.
**Confidence:** High; the small-purchase threshold is open.

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
**Source:** AR-09; user decision on the threshold and scope page, intent `2026-10-16-171147` (relayed by one-architecture; the fragment file was not yet in any branch when this was written); AR-15
**Plain English:** The payee of a payment, or the party named on a receipt, must be the counterparty of the cited agreement or a bank account registered for that counterparty. Anything else is refused. A third-party payee or receipt party (collection or payment on behalf) is allowed only with a three-party agreement or a power of attorney attached, and then the document adds two approval levels (special, approval-table rule AR-15, `guard: approval`); that approval is a person's judgement and is not part of this rule's automatic check. Otherwise correcting it means registering the account through the counterparty record, which has its own control. The tender deposit of RULE-407 is the one exception: its payee and account are those the tender document states (the contract for a performance deposit), not a contract counterparty.
**Specification:**
- Given a payment whose account is not registered for the agreement's counterparty, Then it is refused.
- Given a payment to a registered account of the counterparty, Then it passes.
- Given a payment to a third party with a three-party agreement attached, Then it is not refused by this rule and it goes to the two extra approval levels of AR-15.
- Given a payment to a third party with nothing attached, Then it is refused.
**Tests:** to be written with the payment card.
**Confidence:** High.

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
