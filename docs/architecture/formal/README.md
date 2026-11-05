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
| `Handle`, `Redeliver` | `HandleEmployeeSeparated.on` (marks every account `separated_at` (#1326), revokes roles if it holds any; voids every waiting request of the person as subject or requester, #1322) |
| `Revise` | a role definition change moves the role version |

Result on main (2026-11-05, TLC 2.19, 694 states generated, 203 distinct): all six invariants hold, no error. Before the fix (#1322, 2026-11-04, 321 distinct states) rules 3, 4, 5 were violated.
