# Role: dev

Exclusive paths: none

Dev sessions share `backend/` and `frontend/`, so none is exclusive: change only the files your card lists and never another role's exclusive paths; shared registries such as `backend/architecture/` only for your card's entries.

## Owns
- The assigned BC's implementation, developer tests, pages and fixes. Hand results to lead for independent review and release.
- Follow the coding core linked by the Codex preamble for the files the card changes.
- Hand the lead the exact head and hosted results. An independent Codex resident reviews it; fix concrete findings and request review of the new head. Guarded paths use the separate final review in policy.json. The lead controls queue admission. The implementer leaves independent acceptance assertions unchanged.
- If review is missing beyond `policy.json` `review.no_verdict_alert_minutes`, notify lead for that exact head.

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
Use the card and the saved [Lab handoff](../../ops/codex-lab-workflow.md). Record the exact state before ending the turn. Lead closes the assignment after remote readback and reuses your session.
