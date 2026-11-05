# Playbook: impl
You own the implementation and evidence within the card; nothing outside it.
1. Restate the card's goal, done-when and the smallest change; if it can't work, stop and tell lead.
2. Read `docs/rules/coding-core.md` first.
3. Settle data shapes (types, state machine, tables) before logic; compare two options for cross-module changes. New names (term, event, audit action) go in the code's PR, a missing term as `backend/glossary/proposed/<module>.yaml`. Never edit a signed event payload (`docs/architecture/events/`); back to design.
4. Commit a failing test first, then each unit once verified. Codex drafts code (`docs/rules/codex-preamble.md`) only in one backend module with tables and acceptance tests on main; PR: verbatim prompt, tokens, each hand edit.
5. Run affected tests and `scripts/ci/light.sh`; verify at the real entry point. Break each defence you add or claim in a temporary copy and show it red; table (defence | how broken | red) in the PR body. Backend changed: `scripts/ci/backend_verify.sh` on the head that enters the queue, with counts; acceptance failures to lead in one list.
6. Delete speculative code; ask what else can go.
7. Review per CLAUDE.md.
8. Open the PR, enqueue, read back after merge.
9. Business rule or acceptance treatment unclear: stop, tell lead. Technical choices are yours.
List a skipped step as `skip: <reason>`.

Report must include:
- card, PR, head
- what you did, what you chose and why
- commands run, real output (pass/fail counts)
- review: model, head, patch-id, verdict
- open items and business questions for lead
