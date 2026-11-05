# Formal specs

TLA+ models of flows where asynchronous events interleave and ordinary tests miss orderings. Run TLC by hand when the modelled flow changes; turn every violation into a failing test and a card. Not a CI gate.

## Run

One folder per flow, each with `spec.tla` (module `spec`: TLA+ needs the module name to equal the file name, and `docs/` names are lower-kebab-case) and `spec.cfg`. Download `tla2tools.jar` (TLA+ release v1.7.4 or later; keep it out of the repo), then from the flow's folder:

```bash
java -cp /path/to/tla2tools.jar tlc2.TLC -deadlock -config spec.cfg spec.tla
```

TLC stops at the first violated invariant (here rule 4); to see each rule's result, run with a config that lists one invariant.

## authorization-separation

Two-person authorization (`platform.identity`) against separation (`organization.workforce` command, then `HandleEmployeeSeparated`). One request, three people, one administrator role.

| Action | Code on main |
|---|---|
| `Request` | `AuthorizationGate.openReplaceRoles`, subject employed (`ReplaceAccountRoles.execute`) |
| `Confirm` | `ConfirmAuthorizationRequest.execute` (confirmer, requester and subject employed), voiding by `AuthorizationVoiding` |
| `Reject` | `RejectAuthorizationRequest.execute` |
| `Separate` | separation command in effect, administrator floor as a guard |
| `Handle`, `Redeliver` | `HandleEmployeeSeparated.on` (revokes only if the account holds roles; voids every waiting request of the person as subject or requester, #1322) |
| `Revise` | a role definition change moves the role version |

Result on main (2026-11-05, TLC 2.19, 694 states generated, 203 distinct): all six invariants hold, no error. Before the fix (#1322, 2026-11-04, 321 distinct states) rules 3, 4, 5 were violated.


## leave-ledger

The balance ledger of `organization.leave` (`docs/architecture/proposals/organization-leave.md` sections 3.5, 4.2, 4.3, 9b; golden sample beside it) against the relationship events of ADR-0086 and the approval platform's results. Card #1325. The model is bounded: days 1..4 (leave year 1 = days 1-2, year 2 = days 3-4), at most two relationships of one person (`r1` from day 1, `r2` from day 2), one DAY-type year account per relationship and year, one comp-time rolling account with two lots (`l1`, `l2`), at most two leave requests, one HR adjustment, ledger lines capped by `MaxLines`. Quantities are whole units (leave days, comp-time buckets).

Run from the folder of `docs/architecture/formal/leave-ledger/spec.tla`, one config at a time, `-workers 4`, a wall-clock cap in front (`timeout`), heap 6 GB (the biggest config below needs about 15 minutes; none exceeds 30):

```bash
timeout 1500 java -XX:+UseParallelGC -Xmx6g -cp /path/to/tla2tools.jar tlc2.TLC -workers 4 -deadlock -config spec.cfg spec.tla
```

`spec.cfg` is the base config: the gap options `EndedMode`, `WithdrawMode`, `CarryMode`, `AdjustGuard` at the spec as written (`literal`), `SealMode = redirect` (the only reading of the sealed-year rule that can be written down; both readings reach G-3) and `RetryKey = account` (as written), all fifteen invariants. Each run below is `spec.cfg` with the constants of its row changed and one invariant (or the full list where marked) switched on; the per-run `.cfg` files and TLC's `states/` directory are temporary and not committed. TLC 2.19, `-deadlock` (the model has terminal states by design).

### Actions and the clauses they model

| Action | Clause (`organization-leave.md`) |
|---|---|
| `Submit` | 4.3 `SubmitLeave` + 4.2 `HoldLeaveBalance`: in-service check, balance check (T-4), open year account required, year-end split, comp-time lots earliest expiry first (RULE-516), T-11 refusal |
| `Decide`, `ApplyResult` | 4.3 `ApproveLeave` / `RejectLeave` from `ApprovalCompleted` (`ConsumeLeaveHold`, `ReleaseLeaveHold`) |
| `Withdraw` | 4.3 `WithdrawLeave`: in approval, and approved before the start (gap G-2) |
| `Recall` | 4.3 `RecallLeave` (销假): reverses the used minutes; sealed year (gap G-3) |
| `Redo` | 4.2 retry safety: the same command delivered again, unique key |
| `Separate`, `Unpaid`, `Correct`, `Deliver` | 3.5 and 4.2 Consumes: the relationship events `Ended`, `UnpaidLeaveChanged`, `Corrected`, `Started` reach the leave side at least once, in any order; entitlement recompute as an `ADJUST` line; gap G-1 |
| `OpenYear` | 4.2 `OpenLeaveYearAccount` for the next year |
| `GrantComp`, `ExpireComp` | 4.2 `GrantCompTime`, `ExpireCompTime` (T-8, RULE-516) |
| `Carry`, `Close` | 4.2 carry-over (T-7) and `CloseLeaveYear` (refuses while a hold is active; gap G-4) |
| `AdjustHR` | 4.2 `AdjustLeaveBalance` (gap G-5) |
| `Succ` | T-6: the successor relationship receives the predecessor's balance |
| `Tick` | time |

Not modelled, on purpose: the `PhaseChanged` event (four of the five relationship events are modelled); T-2 (band change date) and T-9 (overtime approval) have no effect on ledger ordering; T-10 (unused leave at separation) is a payroll settlement outside the ledger; T-12 (stacking of two relationships' statutory entitlements) only changes the entitlement number, which is the constant `Ent`; T-1 and T-3 appear as the numbers `Ent` and `EndCut`; the carry-in-with-expiry variant of T-7 (a carried lot in the rolling account) is not modelled; unlimited carry-over is `CarryCap` above the balance. Money, overtime, attendance records and the monthly lock are outside the model.

### Gap options

Where the spec as written leaves a hole, the gap is a constant with the spec's own reading (`literal`) and a proposed reading; every check below runs both.

| Constant | `literal` | proposed | Finding |
|---|---|---|---|
| `EndedMode` | on `Ended`, release the holds of requests still in approval | `cancelOutside`: cancel every in-approval or approved request with a day outside the service period, also on a correction that moves the start later | G-1, G-1b |
| `WithdrawMode` | withdraw of an approved leave = `ReleaseLeaveHold` | `reverse`: `ReverseLeaveUse` | G-2 |
| `SealMode` | the sealed year refuses the line | `redirect`: `ADJUST` in the open year (RULE-514) | G-3 |
| `CarryMode` | carry-over is a command of its own | `atClose`: inside `CloseLeaveYear` | G-4 |
| `AdjustGuard` | none | HR adjustment refuses a result below zero unless T-4 allows | G-5 |
| `RetryKey` | `account`: the idempotency key of an event-driven `ADJUST` contains the target year account | `event`: the key is (relationship, event, line kind) whatever year account the line lands in | G-6 |

### Results

All counts are distinct states; time is wall clock with 4 workers.

| Run | Reading and changed constants | Invariant | Bound | Result | Distinct states | Time |
|---|---|---|---|---|---|---|
| g1_end | literal | ApprovalResultApplicable | relationships r1; requests q1; types DAY; spans s12, s23; end day 2; unpaid-year 0; correction 0; HR adjust FALSE; CarryCap 0; AllowNeg FALSE; MaxLines 6 | violated | 652 | 1s |
| g1_end_fix | fixed | ApprovalResultApplicable | relationships r1; requests q1; types DAY; spans s12, s23; end day 2; unpaid-year 0; correction 0; HR adjust FALSE; CarryCap 0; AllowNeg FALSE; MaxLines 6 | held | 267147 | 5s |
| g1b_corr | literal | NoHoldOutsideService | relationships r2; requests q1; types DAY; spans s23; end day 0; unpaid-year 0; correction 1; HR adjust FALSE; CarryCap 0; AllowNeg FALSE; MaxLines 6 | violated | 693 | 1s |
| g1b_corr_fix | fixed | NoHoldOutsideService | relationships r2; requests q1; types DAY; spans s23; end day 0; unpaid-year 0; correction 1; HR adjust FALSE; CarryCap 0; AllowNeg FALSE; MaxLines 6 | held | 23890 | 1s |
| g1c_appr | literal | TerminalGivesBack | relationships r1; requests q1; types DAY; spans s12, s23; end day 3; unpaid-year 0; correction 0; HR adjust FALSE; CarryCap 0; AllowNeg FALSE; MaxLines 6 | held | 213023 | 4s |
| g2b_wd | literal | TerminalGivesBack | relationships r1; requests q1; types DAY; spans s22; end day 0; unpaid-year 0; correction 0; HR adjust FALSE; CarryCap 0; AllowNeg FALSE; MaxLines 6 | violated | 871 | 1s |
| g2b_wd_fix | fixed | TerminalGivesBack | relationships r1; requests q1; types DAY; spans s22; end day 0; unpaid-year 0; correction 0; HR adjust FALSE; CarryCap 0; AllowNeg FALSE; MaxLines 6 | held | 87994 | 2s |
| g3_recall | literal; SealMode refuse | NotRecallBlocked | relationships r1; requests q1; types DAY; spans s12; end day 0; unpaid-year 0; correction 0; HR adjust FALSE; CarryCap 1; AllowNeg FALSE; MaxLines 8 | violated | 854 | 1s |
| g4_carry | literal | NoStrandedCarry | relationships r1; requests q1; types DAY; spans s12; end day 0; unpaid-year 0; correction 0; HR adjust FALSE; CarryCap 1; AllowNeg FALSE; MaxLines 8 | violated | 51 | 1s |
| g4_carry_fix | fixed | NoStrandedCarry | relationships r1; requests q1; types DAY; spans s12; end day 0; unpaid-year 0; correction 0; HR adjust FALSE; CarryCap 1; AllowNeg FALSE; MaxLines 8 | held | 122651 | 3s |
| g5_neg | literal | NotBelowZero | relationships r1; requests q1; types DAY; spans s12; end day 3; unpaid-year 0; correction 0; HR adjust TRUE; CarryCap 0; AllowNeg FALSE; MaxLines 6 | violated | 122 | 1s |
| g5_neg_hr | fixed | NotBelowZero | relationships r1; requests q1; types DAY; spans s12; end day 0; unpaid-year 0; correction 0; HR adjust TRUE; CarryCap 0; AllowNeg FALSE; MaxLines 6 | held | 344237 | 12s |
| u_line | literal; Unique FALSE | LineKeyOnce | relationships r1; requests q1; types DAY; spans s12, s23; end day 0; unpaid-year 0; correction 0; HR adjust FALSE; CarryCap 1; AllowNeg TRUE; MaxLines 8 | violated | 119 | 1s |
| u_sealed | literal; Unique FALSE | NoWriteToSealed | relationships r1; requests q1; types DAY; spans s12, s23; end day 0; unpaid-year 0; correction 0; HR adjust FALSE; CarryCap 1; AllowNeg TRUE; MaxLines 8 | violated | 1515 | 1s |
| comp_dead | fixed; ExpL1 2 | DeadLotReturnExpires | relationships r1; requests q1, q2; types COMP; spans s12, s23; end day 0; unpaid-year 0; correction 0; HR adjust FALSE; CarryCap 0; AllowNeg FALSE; MaxLines 8 | held | 12856931 | 313s |
| comp_neg | fixed | NotBelowZero | relationships r1; requests q1, q2; types COMP; spans s12, s23; end day 0; unpaid-year 0; correction 0; HR adjust FALSE; CarryCap 0; AllowNeg FALSE; MaxLines 8 | held | 12856931 | 515s |
| two_own | fixed | OwnRelationshipOnly | relationships r1, r2; requests q1, q2; types DAY, COMP; spans s23; end day 0; unpaid-year 0; correction 0; HR adjust FALSE; CarryCap 0; AllowNeg FALSE; MaxLines 8 | held | 24343356 | 877s |
| two_ovl | fixed; OverlapRefuse TRUE | NoCrossOverlap | relationships r1, r2; requests q1, q2; types DAY; spans s23; end day 0; unpaid-year 0; correction 0; HR adjust FALSE; CarryCap 0; AllowNeg FALSE; MaxLines 8 | held | 853574 | 42s |
| succ | fixed; SuccCarry TRUE | CarryBalanced | relationships r1, r2; requests q1; types DAY; spans s12; end day 2; unpaid-year 0; correction 0; HR adjust FALSE; CarryCap 1; AllowNeg TRUE; MaxLines 8 | held | 28478493 | 801s |
| sealhold | fixed | NoHoldAtSeal | relationships r1; requests q1, q2; types DAY; spans s12, s23; end day 0; unpaid-year 0; correction 0; HR adjust FALSE; CarryCap 1; AllowNeg TRUE; MaxLines 8 | held | 4695716 | 153s |
| ledger_ok | fixed | LedgerSumsConsistent | relationships r1; requests q1, q2; types DAY; spans s12, s23; end day 0; unpaid-year 0; correction 0; HR adjust FALSE; CarryCap 1; AllowNeg TRUE; MaxLines 8 | held | 4695716 | 431s |
| entl | fixed; defaults | EntitlementFollowsEvents | relationships r1; requests q1; types DAY; spans s12; end day 3; unpaid-year 1; correction 1; HR adjust FALSE; CarryCap 0; AllowNeg TRUE; MaxLines 6 | held | 2016878 | 43s |
| entl | fixed; Ent 1 | EntitlementFollowsEvents | relationships r1; requests q1; types DAY; spans s12; end day 3; unpaid-year 1; correction 1; HR adjust FALSE; CarryCap 0; AllowNeg TRUE; MaxLines 6 | held | 2016878 | 44s |
| entl | fixed; Ent 3 | EntitlementFollowsEvents | relationships r1; requests q1; types DAY; spans s12; end day 3; unpaid-year 1; correction 1; HR adjust FALSE; CarryCap 0; AllowNeg TRUE; MaxLines 6 | held | 2016878 | 47s |
| entl | fixed; EndCut 0 | EntitlementFollowsEvents | relationships r1; requests q1; types DAY; spans s12; end day 3; unpaid-year 1; correction 1; HR adjust FALSE; CarryCap 0; AllowNeg TRUE; MaxLines 6 | held | 2866115 | 83s |
| entl | fixed; EndCut 2 | EntitlementFollowsEvents | relationships r1; requests q1; types DAY; spans s12; end day 3; unpaid-year 1; correction 1; HR adjust FALSE; CarryCap 0; AllowNeg TRUE; MaxLines 6 | held | 2016878 | 50s |
| entl | fixed; UnpaidCut 0 | EntitlementFollowsEvents | relationships r1; requests q1; types DAY; spans s12; end day 3; unpaid-year 1; correction 1; HR adjust FALSE; CarryCap 0; AllowNeg TRUE; MaxLines 6 | held | 1766316 | 41s |
| entl | fixed; UnpaidCut 2 | EntitlementFollowsEvents | relationships r1; requests q1; types DAY; spans s12; end day 3; unpaid-year 1; correction 1; HR adjust FALSE; CarryCap 0; AllowNeg TRUE; MaxLines 6 | held | 2016878 | 51s |
| g6_event | fixed; RetryKey event | EntitlementFollowsEvents | relationships r1; requests q1; types DAY; spans s11; end day 0; unpaid-year 1; correction 0; HR adjust FALSE; CarryCap 0; AllowNeg TRUE; MaxLines 6 | held | 326531 | 7s |
| carry_cap0 | fixed; CarryCap 0 | NoStrandedCarry | relationships r1; requests q1; types DAY; spans s12; end day 0; unpaid-year 0; correction 0; HR adjust FALSE; CarryCap 0; AllowNeg FALSE; MaxLines 8 | held | 161752 | 5s |
| carry_cap2 | fixed; CarryCap 2 | NoStrandedCarry | relationships r1; requests q1; types DAY; spans s12; end day 0; unpaid-year 0; correction 0; HR adjust FALSE; CarryCap 2; AllowNeg FALSE; MaxLines 8 | held | 122651 | 6s |
| succ0 | fixed; SuccCarry FALSE | CarryBalanced | relationships r1, r2; requests q1; types DAY; spans s12; end day 2; unpaid-year 0; correction 0; HR adjust FALSE; CarryCap 1; AllowNeg TRUE; MaxLines 6 | held | 3425801 | 82s |
| succ1 | fixed; SuccCarry TRUE | CarryBalanced | relationships r1, r2; requests q1; types DAY; spans s12; end day 2; unpaid-year 0; correction 0; HR adjust FALSE; CarryCap 1; AllowNeg TRUE; MaxLines 6 | held | 3444420 | 71s |

Reading the table. `literal` rows are the spec as written; a *violated* result there is a finding (section Findings), with the TLC trace summarised in the PR. `fixed` rows switch the gap options to the proposed reading and are expected to hold; they do. The gap options switched by `fixed` include `RetryKey = event`; every `fixed` row, the `entl` rows among them, ran with it. The `literal` G-6 check is the only run with `RetryKey = account`. The T-constants (`AllowNeg`, `Ent`, `CarryCap`, `ExpL1`/`ExpL2`, `EndCut`, `UnpaidCut`, `OverlapRefuse`, `SuccCarry`) are never chosen by the model: each is a CONSTANT; the table runs the alternatives listed in its rows, not all of them (`ExpL2`, `LotQty`, `LotOwner` and the start balance are fixed, comp-time lots never go negative in the model, and `Close` is modelled narrower than the spec text: it requires the next year's account to be open). The largest runs (`two_own`, `succ`, `comp_neg`) took 5 to 15 minutes; none exceeded 30.

A first sweep of the T-constants on a larger scenario (one relationship, two spans, seven lines) did not finish within a 240 s cap for twelve of its fifteen options. Those runs are **not counted** as results; the sweep above replaced them with smaller scenarios per constant and one invariant each, all of which completed.

Observations from the sweep that are not defects: `NoCrossOverlap` is vacuous with `OverlapRefuse` FALSE and models overlap of leave on both relationships, not the section 9 text (overlap with the other relationship's work, not modelled), so **the model shows nothing about T-11**; it stays a business question. `Ent` 1/2/3 and `EndCut` 1/2 give the same state count, because those constants change values, not the shape of the state space.

### Findings (violations and reachable stuck states of the spec as written)

The action sequences are TLC's shortest traces (`Tick` advances time; `Deliver` hands one relationship event to the leave side).

| Gap | Check | Shortest trace | Proposed reading, checked |
|---|---|---|---|
| G-1 | `ApprovalResultApplicable` violated | 3 x `Tick`, `Separate`, `Deliver`, `Submit`, `Deliver`, `Decide`: the approval result arrives for a request whose hold the `Ended` handling already released | `EndedMode = cancelOutside`: holds |
| G-1b | `NoHoldOutsideService` violated | 2 x `Tick`, `Deliver`, `OpenYear`, `Submit`, `Correct`, `Deliver`: a correction that moves the start later leaves a hold on days outside service | `EndedMode = cancelOutside`: holds |
| G-2 | `TerminalGivesBack` violated | `Deliver`, `Submit`, `Decide`, `ApplyResult`, `Withdraw`: withdraw of an approved leave runs `ReleaseLeaveHold`, but the minutes are already used | `WithdrawMode = reverse`: holds |
| G-3 | `NotRecallBlocked` (reachability check) reached | 3 x `Tick`, `Deliver`, `Submit`, `Decide`, `ApplyResult`, `Close`: an approved leave whose year is sealed and for which no open account exists to take the reversing `ADJUST`; `Recall` cannot run. Reached with `SealMode = refuse` and also with `redirect`: RULE-514 redirect needs an open account | `organization-leave.md` section 4.3 now states the rule: wait for the next year's account and alert. Not model-checked as holding. **Open sub-case for the user (confirmation row T-13):** if the relationship ended in the sealed year there is no next account, the wait never finishes; option (c) hands the returned days to payroll |
| G-4 | `NoStrandedCarry` violated | 3 x `Tick`, `Deliver`, `Close`: the year is sealed with a positive balance and no carry-over line | `CarryMode = atClose`: holds |
| G-5 | `NotBelowZero` violated (T-4) | `Separate`, `Deliver`, `Submit`, `AdjustHR`: an HR `AdjustLeaveBalance` after a hold takes the account below zero; 4.2 has no guard | `AdjustGuard`: holds. Event-driven cuts (separation, unpaid leave) can still take the balance below zero in the spec text (`organization-leave.md`, section on sealed years); T-4 must answer whether that is allowed. `g5_neg_ev` is withdrawn: its end day cannot cut the span, so it could not fail |
| G-6 | `EntitlementFollowsEvents` (now exactly one entitlement `ADJUST` per handled event) violated with `RetryKey = account` | (action order as found by the review; TLC reaches the violation in 1731 states) `Tick`, `Unpaid`, `Tick`, `Deliver(started)`, `Deliver(unpaid)`: `ADJUST -1` on year 1; `OpenYear`, `Close`; `Deliver(unpaid)` again: the redelivery is redirected to year 2 and writes a second `ADJUST -1`, because the key contains the target account | `RetryKey = event`: holds (`g6_event`, 326531 states) |

`G-6` is a spec-text defect (organization-leave.md line 69 now keys event-driven lines by relationship, leave type, source event, original year, line kind); it needs no business answer.

The model does not time-guard `Separate` and `Submit` in the G-1 trace; the shortest trace is a sequence of enabled actions, not a calendar story.

`LineKeyOnce` and `NoWriteToSealed` with `Unique = FALSE` are negative controls: they are violated, which shows the invariants can fail.

Two earlier rows are not in the table: `g2_wd` and its fixed twin used a span that makes withdraw impossible (withdraw needs `today` before the start), so they held vacuously; `g2b_wd` (span `s22`) replaces them. `g1c_appr` (approval after withdraw) holds in the literal reading.

## employment-relationship

The lifecycle of an employment relationship (`docs/architecture/proposals/employment-relationship.md`, ADR-0086) and the three consumers that act on its events: leave (account opening and closing, carry-over from a predecessor), the identity separation flag, and timesheet attribution. Card #1359. Bounded model: one person, two employing entities, at most two relationships, three days, at most five emitted events (six for the rows marked MaxEv 6). Events are delivered at least once and in any order: every emitted event stays available, so any of them can arrive again. Commands take effect on a day not later than today; future-dated entry and corrections that move a date to today or later are not modelled (the ordering problem does not depend on them, but see the finding on identity).

Run from `docs/architecture/formal/employment-relationship/`, `-workers 4`, a wall-clock cap in front, heap 6 GB (the biggest run below needs about 26 minutes):

```bash
timeout 1700 java -XX:+UseParallelGC -Xmx6g -cp /path/to/tla2tools.jar tlc2.TLC -workers 4 -deadlock -config spec.cfg spec.tla
```

`spec.cfg` is the passing reading: `Payload = read`, `UnseenPolicy = defer`, `IdentityCorrection = recompute`, `IdentityHires = TRUE`, `CarryTrigger = both`. It lists the eight invariants; TLC stops at the first violated one, so the results below come from running the invariants one after another (each violated one dropped and the rest rerun).

### Actions and the clauses they model

| Action | Clause (`employment-relationship.md`) |
|---|---|
| `Hire` | 5 HIRE: names the employer; first or additional relationship; `EmployeeHired` fires when the person goes from not in service to in service (6.1); 4.2 item 1 (`SameEmployer`) |
| `Reinstate` | 5 REINSTATE of an ended relationship; `EmployeeReinstated` (6.1) |
| `Separate`, `ReleaseSeparate`, `WithdrawSeparate` | 5 SEPARATE on one relationship, 4.2 item 7, 6.2; the separation hold (`HoldSeparate`): entered, not in effect, no event until released; `EmployeeSeparated` only for the last relationship (6.1) |
| `ChangeEmployer` | 5 "employer change in continuous service": SEPARATE on the old and HIRE with predecessor on the new, one date; neither `EmployeeSeparated` nor `EmployeeHired` fires (6.1) |
| `Regularize` | 6.1 `EmploymentRelationshipPhaseChanged` |
| `UnpaidChange` | 6.1 `EmploymentRelationshipUnpaidLeaveChanged` |
| `Correct` | 5 CORRECTION: names the relationship; `EmploymentRelationshipCorrected` and `EmployeeCorrected` (6.1); moves the start or the end within the past |
| `DeliverRel` (leave, timesheet) | 6.1: leave and payroll subscribe to the relationship-level events, in any order, at least once; leave opens and closes the year account and carries a predecessor's balance (T-6) |
| `DeliverIdn` | 6.1: identity keeps its person-level subscriptions (`EmployeeHired`, `EmployeeSeparated`, `EmployeeReinstated`, `EmployeeCorrected`); sets the separated flag on `EmployeeSeparated`, clears it on `EmployeeReinstated` |
| `Tick` | time |

Not modelled, on purpose: `EmployeeTransferred` and `EmployeeUnpaidLeaveStarted` (identity's review reads them; no effect on the flag), assignments and the primary-assignment rule (4.2 items 6 to 8), employer succession records (4.3), void relationships (4.2 item 9), the paying entity and attribution entity (4.2 item 4), the amounts of leave entitlement (the leave ledger is `leave-ledger`).

### Invariants

| Invariant | Statement | Clause |
|---|---|---|
| `OneInServicePerEmployer` | at most one relationship in service per (person, employer) | 4.2 item 1 |
| `Converged` | once every event has reached every subscriber, each consumer holds what the producer holds, whatever the delivery order | 6.1 |
| `NoActOnUnseenStrict` | no consumer holds service days of a relationship whose start it has not handled | 6.1 (leave opens the account on `Started`) |
| `ReinstatedEverywhere` | a person back in service is not flagged separated and every consumer shows the relationship open | 5 REINSTATE, 6.1 |
| `PredecessorCarried` | a predecessor's balance reaches the successor whatever the order of `Ended(old)` and `Started(new)` | 5 employer change, T-6 |
| `NoSeparatedStranger` | the flag never says separated for a person never hired | 6.1 |
| `NoRegression`, `RedeliveryHarmless` | action level: a delivery never takes a consumer back in time; a redelivery changes nothing or moves toward the truth | 6.1 at-least-once |
| `NotBothInService`, `NotChangedEmployer`, `NotHeld`, `NotReinstatedAfterEnd` | reachability checks: each is violated on purpose, so the states they name are reachable (3 to 4 states deep) | all four reached |

### Options

Open business treatments and design gaps are constants; the model chooses none. Every option below was checked.

| Constant | Values | What it stands for | Source |
|---|---|---|---|
| `Payload` | `read`, `snapshot`, `versioned` | what an event gives its consumer: a notice (the consumer reads the relationship now), the state at emission applied as it arrives, or that state applied only if newer | gap G-1 (6.1 lists payload fields, not what a consumer does with them) |
| `UnseenPolicy` | `act`, `defer` | an event of a relationship the consumer has not seen start | gap G-2 |
| `IdentityCorrection` | `recompute`, `none` | identity recomputes its flag on `EmployeeCorrected` (the code reviews org unit and position only) | gap G-4 |
| `IdentityHires` | TRUE, FALSE | identity clears the flag on `EmployeeHired` (a HIRE by another employer after a separation) | gap G-4 |
| `CarryTrigger` | `started`, `ended`, `both` | leave carries a predecessor's balance when the successor's start, the predecessor's end, or whichever is handled second | gap G-3 |
| `SameEmployer` | `unique`, `newRow` | a second HIRE by the same employer is refused (REINSTATE is used), or makes a second row; `newRow` is a negative control | 4.2 item 1 |
| `HoldSeparate` | TRUE, FALSE | business B-I (c) separation hold | 5 |
| `CarryFromPred` | TRUE, FALSE | business T-6 (a): the unused balance follows a predecessor | T-6 |
| `PredNoProbation` | TRUE, FALSE | business B-E: a HIRE with predecessor starts after probation | B-E |

### Results

All counts are distinct states of the last run of a row (the one that passed everything not listed as violated); time is wall clock with 4 workers. Bound: relationships r1 r2; employers e1 e2; days 1 to 3; MaxEv 5 unless said.

| Run | Options (changed from `spec.cfg`) | Violated | Held | Distinct states | Time |
|---|---|---|---|---|---|
| good | none | none | all eight | 18450071 | 254 s |
| versioned | `Payload = versioned` | none | all eight | 7672935 | 338 s |
| snapshot | `Payload = snapshot` | `NoRegression`, `RedeliveryHarmless`, `Converged`, `PredecessorCarried` | the other four | 33302735 | 1275 s |
| act | `UnseenPolicy = act` | `NoActOnUnseenStrict` | the other seven | 41462327 | 1535 s |
| carry_started | `CarryTrigger = started` | `PredecessorCarried` | the other seven | 18642779 | 600 s |
| carry_ended | `CarryTrigger = ended` | `PredecessorCarried` | the other seven | 18443999 | 584 s |
| newRow | `SameEmployer = newRow` (negative control) | `OneInServicePerEmployer` | the other seven | 34709571 | 1160 s |
| nohold | `HoldSeparate = FALSE` | none | all eight | 4915187 | 121 s |
| nocarry | `CarryFromPred = FALSE` | none (`PredecessorCarried` is vacuous) | all eight | 18322143 | 488 s |
| probation | `PredNoProbation = FALSE` | none (phase converges under `Converged`) | all eight | 18578147 | 438 s |
| idc_none | `IdentityCorrection = none` | none at MaxEv 5, see Findings F-5 | all eight | 18641127 | 650 s |
| idh_false | `IdentityHires = FALSE` | none at MaxEv 5: the HIRE after a separation needs six events, out of bound | all eight | 18268059 | 722 s |
| idh_false6 | `IdentityHires = FALSE`; MaxEv 6, days 1 to 2, only `ReinstatedEverywhere` and `Converged` | `ReinstatedEverywhere` | `Converged` not reached (TLC stops at the first violation) | 23849583 (at the stop) | 374 s |
@@GOOD2@@

The state-space counts differ between rows because the options change which replicas exist, not only which transitions are enabled.

### Findings

Sequences are TLC's shortest traces (`Tick` advances time; `Deliver` hands one event to one consumer).

| ID | Check (option) | Shortest trace | Disposition |
|---|---|---|---|
| F-1 | `NoRegression`, `RedeliveryHarmless`, `Converged`, `PredecessorCarried` violated (`Payload = snapshot`) | `Tick`, `Hire r1` (emits Started, Hired), `Separate r1` (Ended, Separated), `DeliverIdn(Separated)`, `DeliverIdn(Hired)`: the older payload is applied after the newer one and the flag goes back to "in service". Redelivering `Started` after `PhaseChanged` likewise puts the old phase back | Spec-change PR: `employment-relationship.md` 6.1 gains the delivery contract (events are notices; a consumer reads the relationship through the `EmploymentRelationships` api when it handles an event, or applies a payload only if its version is newer). `read` and `versioned` hold all eight; no business answer needed |
| F-2 | `NoActOnUnseenStrict` violated (`UnseenPolicy = act`) | `Hire r1`, `Regularize r1`, `DeliverRel(leave, PhaseChanged)`: leave handles the regularization of a relationship whose `Started` it has not handled and holds service days with no account opened | Spec-change PR, same paragraph: a consumer that has not handled `Started` of the relationship defers (or reconciles from a read, F-1). `defer` holds |
| F-3 | `PredecessorCarried` violated (`CarryTrigger = started` and `= ended`) | `Tick`, `Hire r1`, `ChangeEmployer r1 -> r2` (Ended, Started), then the two events handled in the order the trigger does not look at: with `started` the successor's start is handled before the predecessor's account is closed; with `ended` the predecessor's end is handled before the successor is seen | Counter-example for lead: the carry rule is leave's (`organization-leave.md` T-6), not ADR-0086's, so the rule text there is lead's to place. Proposed reading: carry at whichever of `Started(successor)` and `Ended(predecessor)` is handled second; `both` holds. The order is stated once in the delivery contract of 6.1 (item 4). No change to ADR-0086 |
| F-4 | `ReinstatedEverywhere` violated (`IdentityHires = FALSE`, MaxEv 6) | `Tick`, `Hire r1`, `Separate r1`, `DeliverIdn(Separated)`, `Hire r2` (another employer, 6.1: `EmployeeHired` fires), every event delivered: the person is in service on r2 and identity still holds the separated flag | Spec-change PR: 6.1 states that identity recomputes the person's separated state on `EmployeeHired` and `EmployeeCorrected`, not only on `EmployeeSeparated` and `EmployeeReinstated`. Code evidence: `HandleEmployeeHired` (32 lines) opens an account entry only and never reads the separated state. The identity code change is for `one-dev-identity`, outside this card |
| F-5 | `IdentityCorrection = none` holds in the model | none | Not a defect found: corrections are limited to dates before today, so a correction never changes whether the person is in service today and the flag cannot go stale. A correction that moves an end to today or later is outside the model; the sentence added for F-4 covers it by design. `HandleEmployeeCorrected` today reviews org unit and position only |
| F-6 | `OneInServicePerEmployer` violated (`SameEmployer = newRow`, negative control) | `Hire r1 (e1)`, `Hire r2 (e1)`: two rows for one pair overlap | Shows the invariant can fail; `UNIQUE (employee, employer)` (4.2 item 1) is what keeps it, and `unique` holds |

Business options. `HoldSeparate` (B-I c), `CarryFromPred` (T-6 a) and `PredNoProbation` (B-E) hold both ways for every invariant: whichever way the user decides, none of the eight invariants changes. The model chooses none of them.

Not found: any violation of `OneInServicePerEmployer` under `unique`, of `NoSeparatedStranger`, or of `ReinstatedEverywhere` under the passing options; no ADR-0086 clause needs a new ADR.
