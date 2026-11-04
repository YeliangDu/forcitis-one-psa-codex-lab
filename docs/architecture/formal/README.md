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
| `Request` | `AuthorizationGate.openReplaceRoles` |
| `Confirm` | `ConfirmAuthorizationRequest.execute`, voiding by `AuthorizationVoiding` |
| `Reject` | `RejectAuthorizationRequest.execute` |
| `Separate` | separation command in effect, administrator floor as a guard |
| `Handle`, `Redeliver` | `HandleEmployeeSeparated.on` (revokes only if the account holds roles) |
| `Revise` | a role definition change moves the role version |

Result on main (2026-11-04, TLC 1.7.4, 321 distinct states): rules 1, 2, 6 hold; rules 3, 4, 5 are violated. Fix tracked by #1322; when it lands, update the actions to the fixed behaviour and all six invariants must hold.
