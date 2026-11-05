# Playbook: bugfix
You own root cause and fix. Every changed line needs run evidence; no "might help" changes.
1. Reproduce from the same entry point first; if you can't, narrow the conditions or add observation, never guess.
2. List hypotheses and eliminate them with run evidence until one remains; production is read-only, aggregates only.
3. Change code only once the mechanism is confirmed; if evidence overturns a hypothesis, revert what it led to.
4. Commit the failing reproduction test before the fix; no symptom-masking defensive code.
5. Verify the original reproduction now passes from the same entry; run affected tests plus `scripts/ci/light.sh`. Backend changed: `scripts/ci/backend_verify.sh` on the head you ask lead to release, with counts; acceptance failures to lead in one list.
6. Ship only the minimal change the evidence supports, only in the card's files; other bugs you notice go to lead as new cards.
7. If the same kind of bug can recur, propose a machine check (light.sh or ArchUnit) and show it goes red on this bug.
8. Review per CLAUDE.md; the verdict binds head and patch-id. Open the PR, enqueue, read back the result.
9. If the fix needs an acceptance test changed or a business trade-off: stop and tell lead.

Report must include:
- card, PR, head
- what broke, root cause, fix
- the decisive failing and passing output (assertions and counts only)
- hypotheses ruled out and the evidence
- review: model, head, patch-id, verdict
- open items
