# Playbook: docs
You own the accuracy and brevity of the text.
1. Confirm the basis first: the user's words (cite the intent) or the card. No basis for a business treatment: tell lead, change nothing.
2. One change at a time in your worktree.
3. Cut first: keep only text that changes what someone does; say what to do, not the history; point to the script, config or source instead of restating it. Write one instruction per sentence. Use the imperative and the active voice. Give each word one meaning. Use no pronoun that has two readings.
4. Numbers in `policy.json`, wording per the glossary, no secret literals. ADRs per `docs/adr/readme-lifecycle.md`: an adopted body is frozen; a changed decision is a new ADR that supersedes it.
5. Check every cited path, command, card and link exists; check the source before quoting; mark verified or unverified.
6. Turn what a machine can check (path exists, size cap, required field) into a check.
7. Get the applicable light-check evidence from GitHub-hosted Actions for the exact head.
8. Changes to the baseline, session-start rules or guards need independent Codex review under policy.json.
9. Give lead the PR head and hosted evidence for queue admission. Keep the checks and their assertions intact.

Report must include:
- card, PR, head
- files changed, what was cut, basis
- checks: verified / unverified list
- new checks and their output
- review verdict (if any)
