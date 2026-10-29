# Review subagent prompt

When dispatching a review, put this text into the prompt verbatim, followed by "This review's input".
For the dispatcher:
- The development session (the author) dispatches a fresh review subagent itself; no fork.
- Model: sonnet for normal cards; opus for permission, security and data-scope cards, and for changes to guarded paths (policy.json `review.final_paths`).
- Permission, security and data-scope cards get one more adversarial round: another fresh opus, given only the diff and the card, tasked to "find a way to break it"; keep only findings it proved by running them.
- This review's input holds only the head sha, the Issue text or link, file paths and affected modules; never the author's conclusions, self-assessment or session excerpts.
- Must-fix fixes are not re-reviewed; lead reviews the fix diff and releases it. On lead's own PRs a fresh reviewer checks the fix instead; lead never releases its own. A pure rebase with an unchanged patch-id keeps the earlier verdict.

## Input and limits
- Use only this review's input; inherit nothing from the author's session.
- Confirm the commit exists with `git cat-file -e <sha>^{commit}`; take the diff yourself with `git diff <base>...<sha>`, never from the working tree. With a PR, check its head equals the sha; stop if not.
- Review only this diff; old problems outside it get at most one line under suggestions.
- Read-only: no code changes, commits or pushes; never print secrets, never touch production. The one exception is submitting the verdict with post_review.py.
- Run tests only for the affected modules; CI runs everything.

## Check every time
- Secrets are not committed or printed; production is read-only; row-level data and personal information stay out of the repository.
- No assertion was loosened or deleted; the implementer did not change acceptance tests; hooks and CI were not bypassed.
- Tests test behaviour, not implementation details; a claimed fix has a test that goes red.
- Flag defensive code that hides symptoms (swallowed exceptions, fallback defaults, needless null checks).
- Coding rules no test catches: check backend diffs against `docs/rules/coding-core.md` §B and frontend diffs against `docs/rules/coding-core-frontend.md` item by item.
- Module boundaries: no reading or writing another module's tables or internal classes; the legacy system is evidence only, its structure and names not copied.
- Deletions: `git grep` confirms no reader is left behind.
- Any code file over 1000 lines is a must-fix (code files as `scripts/guards/file_lines.py` defines them; its OVER list holds the known ones, shrink only).
- A comment or docstring that contradicts the code it describes (stale claims, paths that no longer exist) is a must-fix, filed as correctness (agents follow comments); a comment that only restates the code is a suggestion.
- Defect cards: one minimal change plus one regression test that fails on the baseline (a compile failure counts); no regression test, or one that also passes on the baseline, is a must-fix (the test does not prove the claim).
- Table or migration changes: the actual schema diff matches the expected diff on the card or PR, table by table and column by column; merged migrations are not edited, deleted or renamed; a new migration's version is above the highest on the baseline (while `backend/architecture/migration-baseline-open` exists this only warns and the baseline may change; once the marker is removed it blocks hard; ADR-0038, ADR-0054, `scripts/guards/migrations_immutable.py`).
- Where an ADR or rule clause is cited, open the original and check item by item (numbers, enums, exceptions); do not trust the card's paraphrase. Before concluding "missing" or "already implemented", grep or open the file yourself.
- What else can this change delete.

## Severity
- Must-fix: makes wrong behaviour happen, fails open, loses a line of defence, or a test cannot prove what it claims. Finish the investigation before reporting; for each: file:line, problem, reproduction or evidence, fix. State severity honestly, neither raised nor lowered.
- Suggestion: does not affect correctness.
- Break verification: for every line of defence added or claimed, break it in a temporary copy and run its self-test, without touching existing worktrees; list it (defence | how broken | red / not red). Not red is a must-fix.

## Output
Write the whole text to the file the dispatcher names. The first three lines are the machine-readable header (parsed by post_review.py; keep these literals exactly, they are not translated):
```
复审结论：通过｜必改
commit：<40 位 sha>
复审者：<模型>-<首轮|终审>-<标识>
```
- After one blank line, the body's first line is `patch-id：<first column of git diff <base>...<sha> | git patch-id --verbatim>`, for humans only; the merge queue computes the patch-id itself and does not read this line.
- Then in order: impact (one line: who this change affects, and how many caller layers you proved), must-fix, suggestions, items checked and fine (each with evidence: command or file:line, including the break-verification table).
- With a PR, submit: `python3 scripts/merge/post_review.py --pr N --body-file <file> [--guard]`; on a non-zero exit, follow its message and report.
