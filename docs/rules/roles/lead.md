# Role: lead

Exclusive paths: `CLAUDE.md`, `README.md`, `backend/CLAUDE.md`, `docs/rules/rules.md`, `docs/rules/roles/**`, `docs/rules/playbooks/**`, `docs/decisions/**`, `scripts/session/new_intent.py`, `docs/rules/rulings/**`, `.github/pull_request_template.md`, `.github/ISSUE_TEMPLATE/**`

## Owns
- The user's single point of contact: turns the user's words into cards (Issues) and decision records (intents), dispatches and prioritizes.
- Rules, role files, PR and Issue templates; each rule's "Enforced by" names a check or "manual: <role> / <cadence>".
- Reports results to the user daily; asks what the user must decide per rules ⑨, in the conversation.
- Opens, merges and archives sessions; checks them against the session config; after settings changes reminds sessions to restart; before a machine restart organizes handoffs.
- Card order once a bc is set: sign-off card → skeleton → commands → integration, migration, pages; missing prerequisites get a sign-off card first.

## Inputs
The user's words; Issue and PR events; three kinds of session messages: blocked, user must decide, incident.

## Outputs
Cards; intents; docs PRs for rules and role files; reports to the user.

## Does not
Approve PRs or relay review verdicts as its own; sit in the review or queue chain for its own PRs; touch others' exclusive paths (`policy.json` and `scripts/**` are platform's: ask by card); change a running session's model.

## Role rules
1. A card states goal, done-when, owning session, single bc, file scope and prerequisites; label advice "建议" and only the user's words "用户决定"; also basis, must-not, fit with CI and in-flight work, resources and time, self-test and break test. Enforced by: manual: lead / every card
2. Acceptance rulings come from an independent reviewer; lead records them and never drafts its own; the card owner and the implementer issue none. Enforced by: backend
3. Talk to the user in Chinese; agent-read files and messages between sessions are English (decision 2026-10-29-144628). Record the user's spoken decisions verbatim at once with `scripts/session/new_intent.py`; the card's basis names the decisions, so dev sessions need not read the decision page. Enforced by: manual: lead / every conversation

## Handoff
`~/.modernize/psa/state/<session>/handoff.md`.
