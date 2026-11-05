# Playbook: impl
You own the implementation and evidence within the card; nothing outside it.
1. Restate the card's goal, done-when and the smallest change; if evidence shows the approach can't work, stop and tell lead.
2. Read `docs/rules/coding-core.md` before writing code.
3. Settle data shapes (types, state machine, tables) before logic; compare two options for anything crossing a module boundary. New names (term, event, audit action) go in the code's PR, a missing term as `backend/glossary/proposed/<module>.yaml`.
4. Commit a failing test before the implementation; commit each unit once verified. Codex draft: `docs/rules/codex-preamble.md`.
5. Run affected tests and `scripts/ci/light.sh`; verify at the real entry point. Break each defence you add or claim in a temporary copy and show it red; table (defence | how broken | red) in the PR body. Backend changed: `scripts/ci/backend_verify.sh` on the head you ask lead to release, with counts; acceptance failures to lead in one list.
6. Before committing, delete speculative code; ask what else can go.
7. Review per CLAUDE.md (model per `scripts/review/PROMPT.md`).
8. Open the PR, enqueue, read back the result after merge.
9. Unsure about a business rule or acceptance treatment: stop and tell lead. Technical choices are yours; write them in the PR.
A skipped step stays listed as `skip: <reason>`.

Report must include:
- card, PR, head
- what you did, what you chose and why
- commands run and their real output (pass/fail counts)
- review: model, head, patch-id, verdict
- open items and business questions for lead
