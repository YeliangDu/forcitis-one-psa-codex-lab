# Playbook: bugfix
You own root cause and fix. Every changed line needs run evidence; no "might help" changes.
1. Reproduce from the same entry point first; if you can't, narrow the conditions or add observation, never guess.
2. List hypotheses and eliminate them with run evidence until one remains; production is read-only, aggregates only.
3. Change code only once the mechanism is confirmed; if evidence overturns a hypothesis, revert what it led to.
4. Commit the failing reproduction test before the fix; no symptom-masking defensive code. Codex must draft test and fix (`docs/rules/rulings/codex-required.md`).
5. Verify the original reproduction passes from the same entry on GitHub-hosted Actions. Include scoped guards and full backend verification when applicable. Report any failure with the run and head.
6. Ship only the minimal change the evidence supports, only in the card's files; other bugs you notice go to lead as new cards.
7. If the same kind of bug can recur, propose a machine check (light.sh or ArchUnit) and show it goes red on this bug.
8. Give lead the exact head for independent Codex review and queue admission. GitHub merge readback confirms the result.
9. If the fix needs an acceptance test changed or a business trade-off: stop and tell lead.

Report must include:
- card, PR, head
- what broke, root cause, fix
- the decisive failing and passing output (assertions and counts only)
- hypotheses ruled out and the evidence
- review: model, head, patch-id, verdict
- open items
