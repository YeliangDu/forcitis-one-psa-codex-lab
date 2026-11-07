# Codex card delivery in the personal Lab

1. Read the card, signed scope and affected repository rules. Confirm the assigned resident session, actual Lab worktree, and exact base. Reuse the session ID; use a separate assigned worktree for a different card. Keep an explicit business hold scoped to that card.
2. For a behavior change, one-test freezes independent acceptance first. The lead submits that exact tests-only commit to GitHub-hosted Actions. Start implementation once its expected assertion fails; a missing tool, dependency or fixture is a blocked red, not acceptance proof.
3. Implement against the frozen acceptance, keep assertions intact, and hand back the exact commit, changed files, and any unresolved questions. A read-only resident hands off an exact patch; the lead verifies and applies it in the assigned Lab worktree and records the original authoring session.
4. The lead submits the exact implementation head to GitHub-hosted Actions, including applicable integration and guard checks. Report the run URL, head, base and real counts; if a check did not run or pass, report it as missing or red. Local Git inspection does not count as CI.
5. The lead opens or updates the PR with the card link and `Codex: <session-id>`. An independent resident reviews that head. Any head change invalidates the prior admission. Send the PR to the shared merge queue only after the required evidence and reviews are present.

Do all builds, compilation, OpenAPI generation, tests and integration containers on the GitHub runner. Keep developer tools on this machine limited to editing, static inspection, and the Lab's authorized Git and GitHub coordination.
