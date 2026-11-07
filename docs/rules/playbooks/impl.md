# Playbook: impl
You own the implementation and evidence within the card; nothing outside it.
1. Restate the card's goal, done-when and the smallest change; if the approach can't work, stop and tell lead.
2. Settle data shapes (types, state machine, tables) before logic; compare two options for cross-module changes. New names (term, event, audit action) go in the code's PR, a missing term as `backend/glossary/proposed/<module>.yaml`.
3. The Codex implementer starts from the frozen acceptance and hosted red. Preserve the independent assertions and record the implementation session on the PR.
4. Obtain hosted verification of the exact head and real entry point. For a new defense, show a hosted negative case and retain the results. Include the full backend lane when applicable.
5. Delete speculative code; ask what else can go.
6. Hand the exact commit to lead for PR, independent Codex review and queue admission. Merge readback closes the card.
7. Business rule or acceptance treatment unclear: stop, tell lead. Technical choices are yours; write them in the PR.
List a skipped step as `skip: <reason>`.

Report must include:
- card, PR, head
- what you did, what you chose and why
- commands run, real output (pass/fail counts)
- review: model, head, patch-id, verdict
- open items and business questions for lead
