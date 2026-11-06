# Codex first draft is required for backend code

User 2026-11-06: 「我同意，你必须保证这次不会被绕过，因为我的周额度还剩 6%。」 (approving lead's proposal; 11-05 night only 1 of 11 backend runtime PRs used Codex). Builds on user 2026-11-04 「我们首先是要充分发挥 codex 的价值，这个基础上拦得严，不能本末倒置」.

1. Scope: any PR whose diff touches `backend/src/main/**` (impl and bugfix). Never frontend, ops, docs or `scripts/`.
2. Codex (`docs/rules/codex-preamble.md`) drafts the code, the failing unit test first, unit tests and migrations, once the data design is signed and acceptance tests are on main or released. A change across modules is one Codex run per module. The session runs ITs and fixes what Codex left.
3. PR body: one line `Codex: <session-id>` per run (the id Codex prints), plus the verbatim prompt, tokens and each hand edit.
4. Skips, nothing else: `Codex: skip small` (under 20 changed lines in `backend/src/main/**`), or `Codex: skip <reason>` with a lead comment `lead 准许跳过 Codex` posted after it.
5. Enforced by: #1561 (check at PR opening and at `mq.py enqueue`); until #1561 merges, manual: lead / every backend PR release and merge.
