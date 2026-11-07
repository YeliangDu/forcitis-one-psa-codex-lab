# Personal Codex Lab workflow

Codex is the development, test authoring and review runtime for this repository. GitHub-hosted Actions executes the checks. The one-lead coordinator and the shared queue can run on the Mac; they send work, read GitHub results and control admission without compiling or testing locally.

- **Open and assign.** Choose the next signed and unblocked slice from the Lab plan, record the Issue and base, then use the assigned resident's existing session ID. Save the role, worktree and assignment before moving the card. If a role is unavailable, report or resume that role; don't create a replacement.
- **Prove the change.** Follow the [Codex preamble](../rules/codex-preamble.md). The hosted PR workflow is [Lab PR CI](../../.github/workflows/lab-pr-ci.yml). Preserve the exact hosted red, green and integration evidence. Check the current head and base at readback; a label or older green run is insufficient.
- **Review and release.** Use the repository's title, card link and independent review rules. Keep one queue via [mq.py](../../scripts/merge/mq.py), using the Lab Git common directory. A waiting label describes queue state; the PR event starts CI. The queue reads hosted evidence, checks current admission and records GitHub merge readback before clearing a card. Keep writes to main disabled while the production adapter or exact-head main signal is unavailable.
- **Recover.** Resume the saved session and assignment after restart. A recorded but uncertain merge request is reconciled against GitHub, not resent. A successful readback makes dependent slices eligible for the next scan.

The old self-hosted CI, production deploy and release workflows are outside this Lab migration. Keep their Lab copies disabled. See the [runner reference](ci-runners.md) only when investigating legacy runner behavior.
