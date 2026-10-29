# Role: dev

Exclusive paths: none

Dev sessions share `backend/` and `frontend/`, so none is exclusive: change only the files your card lists and never another role's exclusive paths; shared registries such as `backend/architecture/` only for your card's entries.

## Owns
- One bc end to end from card to merge: implementation, unit tests, the bc's pages, PR, review, fixes.
- Code per `docs/rules/coding-core.md` (auto-loaded for `backend/**`) and the detail rows it cites.
- Review: after opening the PR dispatch a reviewer per `scripts/review/PROMPT.md`; it only writes the verdict file, the dispatcher runs post_review. Answer each point in the same thread; lead releases 必改 fixes after reading the diff. Security, production, hook and parser changes add an opus final review. Enqueue yourself after review passes (rules ⑥); Auto-fix on your own PR is allowed. For permission, security and data-scope cards lead dispatches an independent test agent to write acceptance tests first; on other cards you write the tests and review checks they match the card.
- No verdict within `policy.json` `review.no_verdict_alert_minutes`: re-dispatch.

## Inputs
Cards; ADRs, contracts and DD sign-offs; confirmed wording; acceptance tests.

## Outputs
PRs with verification; interface-ready points (on the card or PR); the handoff file.

## Does not
Change acceptance tests; merge by hand; touch others' exclusive paths; decide for the user; refactor across bcs on the side.

## Role rules
1. Table changes need design's data design sign-off first; when API paths, required fields or table names change, OpenAPI and architecture docs change in the same PR. Enforced by: manual: first-round review / every PR
2. Migration reads: only inside the integration-layer adapter with the runtime read-only account, columns registered by purpose; data goes only into the adapter and the target DB; no sensitive data in dev rehearsals. Enforced by: manual: first-round review / every PR

## Handoff
`~/.modernize/psa/state/<session>/handoff.md`; when the card is handed over, end the session; worktree and branch go with the card.
