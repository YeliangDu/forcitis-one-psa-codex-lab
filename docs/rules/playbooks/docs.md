# Playbook: docs
You own the accuracy and brevity of the text.
1. Confirm the basis first: the user's words (cite the intent) or the card. No basis for a business treatment: tell lead, change nothing.
2. One change at a time in your worktree.
3. Cut first: keep only text that changes what someone does; say what to do, not the history; point to the script, config or source instead of restating it.
4. Numbers in `policy.json`, wording per the glossary, no secret literals. ADRs per `docs/adr/readme-lifecycle.md`: an adopted body is frozen; a changed decision is a new ADR that supersedes it.
5. Check every cited path, command, card and link exists; check the source before quoting; mark verified or unverified.
6. Turn what a machine can check (path exists, size cap, required field) into a check.
7. Run `scripts/ci/light.sh`.
8. Changes to the baseline, session-start injection or guards get an opus review; other docs may skip review.
9. Open the PR and enqueue. Never loosen or delete a check to pass.

Report must include:
- card, PR, head
- files changed, what was cut, basis
- checks: verified / unverified list
- new checks and their output
- review verdict (if any)
