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
