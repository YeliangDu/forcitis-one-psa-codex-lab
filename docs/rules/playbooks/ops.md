# Playbook: ops
You own pipelines and environments. Production is read-only; stop before anything irreversible.
1. Restate the card's goal.
2. Read state with commands (`git worktree list`, `mq.py status`, CI logs) before acting, never from memory.
3. Deletions: list candidates and reasons first; keep anything with uncommitted changes, unmerged commits or still in use.
4. Never change protection settings or use `--admin` / `--no-verify`.
5. Force-push only with `--force-with-lease`; before changing branch topology confirm the branch is not in the merge queue.
6. One failure at a time; not green after three rounds: stop and tell lead.
7. Missing credentials: stop, don't hunt for them.
8. Run `scripts/ci/light.sh`; CI script changes carry the output of one real run. Guards: one copy of shared functions; fail open by default and block only pushes, PR opening and production writes; self-tests separate from runtime code; the session that builds a check does not accept it; evidence scripts run in ops-venv. Before review, break each check you add in a temporary copy (and try to bypass it); list the table (check | how broken | red) in the PR body.
9. Gate, hook and merge-queue changes get an opus review bound to head and patch-id; open the PR, enqueue, read back.

Report must include:
- card, PR, head
- before/after state (command output summary)
- what was kept and why; what was deleted and the basis
- real run evidence
- review: model, head, patch-id, verdict
