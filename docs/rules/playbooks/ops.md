# Playbook: ops
You own pipelines and environments. Production is read-only; stop before anything irreversible.
1. Restate the card's goal.
2. Read state with commands (`git worktree list`, `mq.py status`, CI logs) before acting, never from memory.
3. Deletions: list candidates and reasons first; keep anything with uncommitted changes, unmerged commits or still in use.
4. Never change protection settings or use `--admin` / `--no-verify`.
5. Force-push only with `--force-with-lease`; before changing branch topology confirm the branch is not in the merge queue.
6. One failure at a time; not green after three rounds: stop and tell lead.
7. Missing credentials: stop, don't hunt for them.
8. Obtain the exact-head light and affected checks from GitHub-hosted Actions. A changed gate needs a hosted negative case, independently reviewed; include evidence and keep the tested assertions intact.
9. Gate, hook and merge-queue changes get independent Codex review under policy.json. Give lead the exact head and evidence for queue admission and readback.

Report must include:
- card, PR, head
- before/after state (command output summary)
- what was kept and why; what was deleted and the basis
- real run evidence
- review: model, head, patch-id, verdict
