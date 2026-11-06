# Playbook: impl
You own the implementation and evidence within the card; nothing outside it.
1. Restate the card's goal, done-when and the smallest change; if the approach can't work, stop and tell lead.
2. Settle data shapes (types, state machine, tables) before logic; compare two options for cross-module changes. New names (term, event, audit action) go in the code's PR, a missing term as `backend/glossary/proposed/<module>.yaml`.
3. Commit a failing test first, then each unit once verified. Backend: Codex must draft code and tests, PR line and skips per `docs/rules/rulings/codex-required.md`.
4. Run affected tests and `scripts/ci/light.sh`; verify at the real entry point. Break each defence you add or claim in a temporary copy and show it red; table (defence | how broken | red) in the PR body. Backend: the full suite runs only in queue CI, not locally (user 2026-11-05); queue acceptance failures to lead in one list.
5. Delete speculative code; ask what else can go.
6. Open the PR, enqueue, read back after merge.
7. Business rule or acceptance treatment unclear: stop, tell lead. Technical choices are yours; write them in the PR.
List a skipped step as `skip: <reason>`.

Report must include:
- card, PR, head
- what you did, what you chose and why
- commands run, real output (pass/fail counts)
- review: model, head, patch-id, verdict
- open items and business questions for lead
