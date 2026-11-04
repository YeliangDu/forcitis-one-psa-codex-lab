# Leave and overtime business rules — RULE-509..RULE-520

Scope: new context `organization.leave` (bc tag `organization.leave`, B-27): leave requests, leave balance accounts, overtime and comp time, attendance records, monthly confirmation, leave rule versions. Spec: `docs/architecture/proposals/organization-leave.md` (#1279). Related and unchanged: RULE-398..405 (timesheet filing), RULE-412/413 (seam). Quota formulas, carry-over, rounding and negative-balance values are business treatments not yet decided by the user.

| ID | Domain | Title | Priority | Review |
|---|---|---|---|---|
| RULE-509 | leave | A leave request holds balance in the same transaction it is submitted; it is refused when the balance is not enough, unless the rule version allows it (ADR-0079:99 says never negative; an exception needs approval and an ADR note, T-4) | P0 | No |
| RULE-510 | leave | Leave minutes are split by the standard hours of each day; a non-workday counts zero; a missing standard-hours version refuses the request | P0 | No |
| RULE-511 | leave | A request inside quota, allowed by the rule version, under the exempt threshold and needing no proof is approved without a person; any other request goes to approval | P1 | No |
| RULE-512 | leave | A rejected or withdrawn request releases its hold; a recalled leave gives back its used minutes; nothing is deleted | P0 | No |
| RULE-513 | leave | A balance account is opened by leave year and the rule version in force, never implicitly; a missing version fails and alerts | P0 | No |
| RULE-514 | leave | A sealed leave year is never rewritten; corrections and carry-over are new lines in the open year | P0 | No |
| RULE-515 | leave | Entitlement changes caused by hiring, separation, correction of employment history, unpaid leave or a change of the employer entity are new ledger lines computed from the rule version | P0 | No |
| RULE-516 | leave | Comp time is granted per overtime record with its own expiry and used earliest-expiry first; at expiry the minutes are reported to payroll and nothing is paid here | P1 | No |
| RULE-517 | leave | Overtime is approved through the approval platform; statutory-holiday overtime can only be paid; overtime overlapping an approved leave is refused | P1 | No |
| RULE-518 | leave | Monthly confirmation is generated only when every day of the month has an import batch; after lock nothing changes and later changes go to the next month marked with the original month | P0 | No |
| RULE-519 | leave | Attendance records are append-only; a make-up punch is a separate record | P0 | No |
| RULE-520 | leave | The leave read API is the only way other modules see leave hours, balances, overtime approvals or comp time | P0 | No |

### RULE-509: A leave request holds balance in the same transaction it is submitted; it is refused when the balance is not enough, unless the rule version allows it (ADR-0079:99 says never negative; an exception needs approval and an ADR note, T-4)
**Category:** Calculation
**Priority:** P0
**Source:** INV-68; ADR-0079:99 (quota held first, never deducted into negative)
**Plain English:** Submitting leave holds the requested minutes on the account of that leave year in the same transaction. If the available balance is smaller the submit is refused, unless the leave rule version in force has the allow-negative flag for that leave type (business treatment T-4).
**Specification:**
- Given available 480 minutes, When 540 are requested for a type that does not allow negative, Then the submit fails and nothing is held.
**Tests:** to be written with the slice that implements it.
**Confidence:** Medium — the allow-negative treatment is not decided.

### RULE-510: Leave minutes are split by the standard hours of each day; a non-workday counts zero; a missing standard-hours version refuses the request
**Category:** Calculation
**Priority:** P0
**Source:** INV-116; planned.yaml AGG-70
**Plain English:** A request across days is stored as minutes per day, each day using the standard hours in force on that day for the person's legal entity and the work calendar. A day that is not a workday counts zero. If no standard-hours version is found the request is refused with an alert; there is no default.
**Specification:**
- Given a Friday-to-Monday request, When the weekend is non-workdays, Then only Friday and Monday carry minutes.
**Tests:** to be written with the slice that implements it.
**Confidence:** High.

### RULE-511: A request inside quota, allowed by the rule version, under the exempt threshold and needing no proof is approved without a person; any other request goes to approval
**Category:** Policy
**Priority:** P1
**Source:** closeout people commands, 提交请假 (R-a); threshold value is open (U3)
**Plain English:** The routing is decided by the leave rule version: quota, rule allowance, exempt threshold and proof flag. The auto path records the approval as automatic.
**Specification:**
- Given an exempt threshold of 240 minutes and a 120-minute request with no proof needed, Then it is approved at submit; with 300 minutes it goes to approval.
**Tests:** to be written with the slice that implements it.
**Confidence:** Medium — threshold values are open (U3).

### RULE-512: A rejected or withdrawn request releases its hold; a recalled leave gives back its used minutes; nothing is deleted
**Category:** Policy
**Priority:** P0
**Source:** ADR-0079:99 (can be withdrawn and recalled)
**Plain English:** Reject and withdraw release the hold with a ledger line. Recalling (销假) a started leave reverses the used minutes with a ledger line. The request keeps its record and final state.
**Specification:**
- Given an approved leave not yet started, When the applicant withdraws, Then the hold is released and the state is withdrawn.
**Tests:** to be written with the slice that implements it.
**Confidence:** High.

### RULE-513: A balance account is opened by leave year and the rule version in force, never implicitly; a missing version fails and alerts
**Category:** Policy
**Priority:** P0
**Source:** INV-123; PRC-160
**Plain English:** Accounts are opened by the open-account command (hire, year start). A hold on a missing account fails. A missing rule version at open fails with an alert; no default.
**Specification:**
- Given no rule version for a type on 1 Jan, When the year opens, Then the open fails and alerts.
**Tests:** to be written with the slice that implements it.
**Confidence:** High.

### RULE-514: A sealed leave year is never rewritten; corrections and carry-over are new lines in the open year
**Category:** Policy
**Priority:** P0
**Source:** ADR-0079:123
**Plain English:** After year close the account is sealed and refuses any new line. A correction to a sealed year is an adjust line in the open year with the reason and the year it concerns.
**Specification:**
- Given a sealed 2025 account, When HR corrects it, Then the line lands on the 2026 account.
**Tests:** to be written with the slice that implements it.
**Confidence:** High.

### RULE-515: Entitlement changes caused by hiring, separation, correction of employment history, unpaid leave or a change of the employer entity are new ledger lines computed from the rule version
**Category:** Calculation
**Priority:** P0
**Source:** ADR-0079:99; golden sample G2..G6
**Plain English:** Each workforce fact that changes entitlement produces a ledger line computed from the rule version in force; earlier lines are not edited. The formulas and rounding are business treatments T-1..T-6 and are not fixed by this rule.
**Specification:**
- Pending: the expected values come from the golden sample once the user decides.
**Tests:** to be written with the slice that implements it.
**Confidence:** Low — formulas pending the user and the legacy results.

### RULE-516: Comp time is granted per overtime record with its own expiry and used earliest-expiry first; at expiry the minutes are reported to payroll and nothing is paid here
**Category:** Calculation
**Priority:** P1
**Source:** closeout people commands, 计入补休额度 / 补休到期转计薪; PRC-178; U15
**Plain English:** Each comp-time grant is a ledger line with an expiry date. Use consumes the line expiring first. An expired remainder produces an expire line and an event with the minutes; pay is computed by payroll.
**Specification:**
- Given two grants expiring in June and September, When 60 minutes are used, Then the June grant is consumed first; a leave day after a lot's expiry date never draws from that lot; minutes returned to an expired lot are expired at once and reported.
**Tests:** to be written with the slice that implements it.
**Confidence:** Medium — validity period is open (U15).

### RULE-517: Overtime is approved through the approval platform; statutory-holiday overtime can only be paid; overtime overlapping an approved leave is refused
**Category:** Policy
**Priority:** P1
**Source:** ADR-0079:125; closeout 提交加班申请 (R-c); T-9
**Plain English:** Overtime requests start an approval. Compensation mode is limited by day type. A request overlapping approved leave is refused.
**Specification:**
- Given a statutory holiday, When comp time is requested, Then the submit is refused.
**Tests:** to be written with the slice that implements it.
**Confidence:** Medium — approval route versus punch check is open (T-9).

### RULE-518: Monthly confirmation is generated only when every day of the month has an import batch; after lock nothing changes and later changes go to the next month marked with the original month
**Category:** Policy
**Priority:** P0
**Source:** INV-105; PRC-118
**Plain English:** A missing import day fails the generate and alerts; it is never treated as full attendance. Locked means unchanged; a later make-up punch, leave or overtime change appears as a correction row of the next month with the original month.
**Specification:**
- Given a locked January, When a make-up punch for January arrives, Then February holds a correction row marked January.
**Tests:** to be written with the slice that implements it.
**Confidence:** High.

### RULE-519: Attendance records are append-only; a make-up punch is a separate record
**Category:** Policy
**Priority:** P0
**Source:** INV-69
**Plain English:** No update or delete of an attendance record. A make-up punch is a new record of kind make-up.
**Specification:**
- Given a recorded punch, When a make-up is filed, Then two records exist.
**Tests:** to be written with the slice that implements it.
**Confidence:** High.

### RULE-520: The leave read API is the only way other modules see leave hours, balances, overtime approvals or comp time
**Category:** Policy
**Priority:** P0
**Source:** RULE-412; module edge guard
**Plain English:** Other modules read leave through the context's api and published events only, never its tables. The consumers are the approved edge list: `cost.timesheet` through the seam card (spec 4.3) and, later, payroll and the delivery resource plan (ADR-0079:91).
**Specification:**
- An architecture test fails when a module outside the approved edge list depends on `organization.leave`, or when `cost.timesheet` depends on anything beyond the leave-hours api and the leave events.
**Tests:** to be written with the slice that implements it.
**Confidence:** High.
