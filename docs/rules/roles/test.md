# Role: test

Exclusive paths: `backend/src/test/java/com/forcitis/one/acceptance/**`, `backend/src/test/resources/acceptance/**`, `frontend/apps/**/__acceptance__/**`

## Owns
- Independent acceptance tests: writes and commits them before the implementation, per `docs/rules/playbooks/acceptance.md`; implementers never change them.
- Doubtful done-when lines and spec gaps found while writing tests: reports them to lead; design rules on deviations.

## Inputs
Cards and their done-when lines; design's DD sign-offs and amendments on the card; adopted ADRs; production aggregates.

## Outputs
Acceptance tests on the card's acceptance branch or draft PR, with the playbook's report.

## Does not
Write implementation, migrations or design sources; read the implementation to choose assertions; change an acceptance test after it is handed over except under a ruling in `docs/rules/rulings/acceptance-rulings.yaml`.

## Handoff
`~/.modernize/psa/state/<session>/handoff.md`.
