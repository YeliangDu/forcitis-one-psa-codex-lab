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

`spec.cfg` is the base config: every gap option at the spec as written (`literal`), all fifteen invariants. Each run below is `spec.cfg` with the constants of its row changed and one invariant (or the full list where marked) switched on; the per-run `.cfg` files and TLC's `states/` directory are temporary and not committed. TLC 2.19, `-deadlock` (the model has terminal states by design).

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

Not modelled, on purpose: T-2 (band change date) and T-9 (overtime approval) have no effect on ledger ordering; T-10 (unused leave at separation) is a payroll settlement outside the ledger; T-12 (stacking of two relationships' statutory entitlements) only changes the entitlement number, which is the constant `Ent`; T-1 and T-3 appear as the numbers `Ent` and `EndCut`; the carry-in-with-expiry variant of T-7 (a carried lot in the rolling account) is not modelled; unlimited carry-over is `CarryCap` above the balance. Money, overtime, attendance records and the monthly lock are outside the model.

### Gap options

Where the spec as written leaves a hole, the gap is a constant with the spec's own reading (`literal`) and a proposed reading; every check below runs both.

| Constant | `literal` | proposed | Finding |
|---|---|---|---|
| `EndedMode` | on `Ended`, release the holds of requests still in approval | `cancelOutside`: cancel every in-approval or approved request with a day outside the service period, also on a correction that moves the start later | G-1, G-1b |
| `WithdrawMode` | withdraw of an approved leave = `ReleaseLeaveHold` | `reverse`: `ReverseLeaveUse` | G-2 |
| `SealMode` | the sealed year refuses the line | `redirect`: `ADJUST` in the open year (RULE-514) | G-3 |
| `CarryMode` | carry-over is a command of its own | `atClose`: inside `CloseLeaveYear` | G-4 |
| `AdjustGuard` | none | HR adjustment refuses a result below zero unless T-4 allows | G-5 |

### Results

All counts are distinct states; time is wall clock with 4 workers.

| Run | Reading | Invariant | Result | Distinct states | Time |
|---|---|---|---|---|---|
| g1_end | literal | ApprovalResultApplicable | violated | 652 | 1s |
| g1_end_fix | fixed | ApprovalResultApplicable | held | 267147 | 5s |
| g1b_corr | literal | NoHoldOutsideService | violated | 693 | 1s |
| g1b_corr_fix | fixed | NoHoldOutsideService | held | 23890 | 1s |
| g1c_appr | literal | TerminalGivesBack | held | 213023 | 4s |
| g2_wd | literal | TerminalGivesBack | held | 5780 | 1s |
| g2_wd_fix | fixed | TerminalGivesBack | held | 5780 | 1s |
| g3_recall | literal | NotRecallBlocked | violated | 854 | 1s |
| g4_carry | literal | NoStrandedCarry | violated | 51 | 1s |
| g4_carry_fix | fixed | NoStrandedCarry | held | 122651 | 3s |
| g5_neg | literal | NotBelowZero | violated | 122 | 1s |
| g5_neg_hr | fixed | NotBelowZero | held | 344237 | 12s |
| g5_neg_ev | fixed | NotBelowZero | held | 206246 | 7s |
| u_line | literal | LineKeyOnce | violated | 119 | 1s |
| u_sealed | literal | NoWriteToSealed | violated | 1515 | 1s |
| comp_dead | fixed | DeadLotReturnExpires | held | 12856931 | 313s |
| comp_neg | fixed | NotBelowZero | held | 12856931 | 515s |
| two_own | fixed | OwnRelationshipOnly | held | 24343356 | 877s |
| two_ovl | fixed | NoCrossOverlap | held | 853574 | 42s |
| succ | fixed | CarryBalanced | held | 29640059 | 806s |
| sealhold | fixed | NoHoldAtSeal | held | 4695716 | 153s |
| ledger_ok | fixed | LedgerSumsConsistent | held | 4695716 | 431s |
| entl | fixed | EntitlementFollowsEvents | held | 2081148 | 39s |
| g2b_wd | literal | TerminalGivesBack | TerminalGivesBack | Error: Invariant TerminalGivesBack is violated. | distinct=871 |
| g2b_wd_fix | fixed | TerminalGivesBack | TerminalGivesBack | Model checking completed. No error has been found. | distinct=87994 |
| entl | fixed, Ent =  Ent = 1 | EntitlementFollowsEvents | held | 2081148 | 72s |
| entl | fixed, Ent =  Ent = 3 | EntitlementFollowsEvents | held | 2081148 | 57s |
| entl | fixed, EndCut =  EndCut = 0 | EntitlementFollowsEvents | held | 2954705 | 66s |
| entl | fixed, EndCut =  EndCut = 2 | EntitlementFollowsEvents | held | 2081148 | 50s |
| entl | fixed, UnpaidCut =  UnpaidCut = 0 | EntitlementFollowsEvents | held | 1796250 | 45s |
| entl | fixed, UnpaidCut =  UnpaidCut = 2 | EntitlementFollowsEvents | held | 2081148 | 55s |
| ovl_off | fixed, OverlapRefuse =  OverlapRefuse = FALSE | NoCrossOverlap | held | 864248 | 57s |
| carry_cap0 | fixed, base knobs | NoStrandedCarry | held | 161752 | 5s |
| carry_cap2 | fixed, base knobs | NoStrandedCarry | held | 122651 | 6s |
| succ0 | fixed, SuccCarry =  SuccCarry = FALSE | CarryBalanced | held | 3453715 | 92s |
| succ1 | fixed, SuccCarry =  SuccCarry = TRUE | CarryBalanced | held | 3472334 | 109s |

Reading the table. `literal` rows are the spec as written; a *violated* result there is a finding (section Findings), with the TLC trace summarised in the PR. `fixed` rows switch the gap options to the proposed reading and are expected to hold; they do. The T-constants (`AllowNeg`, `Ent`, `CarryCap`, `ExpL1`/`ExpL2`, `EndCut`, `UnpaidCut`, `OverlapRefuse`, `SuccCarry`) are never chosen by the model: each is a CONSTANT and the table above runs the alternatives. The largest runs (`two_own`, `succ`, `comp_neg`) took 13 to 15 minutes; none exceeded 30.

A first sweep of the T-constants on a larger scenario (one relationship, two spans, seven lines) did not finish within a 240 s cap for twelve of its fifteen options. Those runs are **not counted** as results; the sweep above replaced them with smaller scenarios per constant and one invariant each, all of which completed.

Two observations from the sweep that are not defects: `OverlapRefuse` FALSE still satisfies `NoCrossOverlap` at this bound, so the refusal of overlapping days across two relationships (T-11) is a business choice, not something the ledger needs for safety; and `Ent` 1/2/3 and `EndCut` 1/2 give the same state count, because those constants change values, not the shape of the state space.

### Findings (violations and reachable stuck states of the spec as written)

The action sequences are TLC's shortest traces (`Tick` advances time; `Deliver` hands one relationship event to the leave side).

| Gap | Check | Shortest trace | Proposed reading, checked |
|---|---|---|---|
| G-1 | `ApprovalResultApplicable` violated | 3 x `Tick`, `Separate`, `Deliver`, `Submit`, `Deliver`, `Decide`: the approval result arrives for a request whose hold the `Ended` handling already released | `EndedMode = cancelOutside`: holds |
| G-1b | `NoHoldOutsideService` violated | 2 x `Tick`, `Deliver`, `OpenYear`, `Submit`, `Correct`, `Deliver`: a correction that moves the start later leaves a hold on days outside service | `EndedMode = cancelOutside`: holds |
| G-2 | `TerminalGivesBack` violated | `Deliver`, `Submit`, `Decide`, `ApplyResult`, `Withdraw`: withdraw of an approved leave runs `ReleaseLeaveHold`, but the minutes are already used | `WithdrawMode = reverse`: holds |
| G-3 | `NotRecallBlocked` (reachability check) reached | 3 x `Tick`, `Deliver`, `Submit`, `Decide`, `ApplyResult`, `Close`: an approved leave whose year is sealed and for which no open account exists to take the reversing `ADJUST`; `Recall` cannot run. Reached with `SealMode = refuse` and also with `redirect`: RULE-514 redirect needs an open account | **no proposed reading yet; for lead (spec needs a rule: open the next year's account on demand, or hold the recall until it exists)** |
| G-4 | `NoStrandedCarry` violated | 3 x `Tick`, `Deliver`, `Close`: the year is sealed with a positive balance and no carry-over line | `CarryMode = atClose`: holds |
| G-5 | `NotBelowZero` violated (T-4) | `Separate`, `Deliver`, `Submit`, `AdjustHR`: an HR `AdjustLeaveBalance` after a hold takes the account below zero; 4.2 has no guard | `AdjustGuard`: holds. The event-driven recompute path (separation cut) alone never goes below zero at this bound (`g5_neg_ev` holds) |

`LineKeyOnce` and `NoWriteToSealed` with `Unique = FALSE` are negative controls: they are violated, which shows the invariants can fail.
