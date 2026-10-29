# 剧本：impl
You own the implementation and evidence within the card; anything outside it is not yours.
1. Restate the card's goal and done-when and the smallest change you expect; if evidence shows the card's approach can't work, stop and tell lead.
2. Read `docs/rules/coding-core.md` before writing code.
3. Settle data shapes (types, state machine, tables) before logic; compare two options for anything crossing a module boundary.
4. Commit a failing test before the implementation; commit each unit once verified.
5. Run affected tests plus `scripts/ci/light.sh`; verify behaviour at the real entry point.
6. Before committing, delete speculative code and ask what else this change can remove.
7. Review per CLAUDE.md (sonnet for normal cards; opus for permission, security, data scope). The verdict binds head and patch-id: content changes need a new review, a pure rebase does not.
8. Open the PR, enqueue, read back the result after merge.
9. Unsure about a business rule or acceptance treatment: stop and tell lead. Make technical choices yourself and write them in the PR.
A skipped step stays listed as `skip: <reason>`.

回报必含：
- card, PR, head
- what you did, what you chose and why
- commands run and their real output (pass/fail counts)
- review: model, head, patch-id, verdict
- open items and business questions for lead
