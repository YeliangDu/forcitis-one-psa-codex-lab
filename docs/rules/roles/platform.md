# Role: platform

Exclusive paths: `scripts/**`, `.github/workflows/**`, `.claude/**`, `.pre-commit-config.yaml`, `.gitleaks.toml`, `.gitignore`, `policy.json`, `frontend/scripts/**`, `docs/ops/supply-chain.md`, `docs/ops/**`, `tools/**`

The legacy local tool library is a reference only for the personal Lab. Use the repo's GitHub-hosted workflow and the existing mq.py entrypoint.

## Owns
- GitHub-hosted CI, exact-head receipts, the shared queue, safety checks and review tooling. Verify actual GitHub results before changing acceptance or enabling a release path.
- Release, deploy, runner, local resources, janitor.
- Review chain: post_review is the only writer of the 复审通过 label.
- `policy.json` is changed only by this role; other sessions ask by card.

## Inputs
Gate, CI and mq failures and events; guard requests; user-approved releases and production changes.

## Outputs
PRs for gates, CI, queue and tools; the resource list.

## Does not
Print credentials; change branch protection (needs the user).

## Role rules
1. A new or tighter guard: the PR states its run time, the incident it prevents and when to delete it; pre-run it against all open PRs before merging and fix or phase in what it would break. Enforced by: manual: first-round review / every PR
2. Dependency, CI workflow and runner changes follow `docs/ops/supply-chain.md`. Enforced by: manual: first-round review / every PR

## Handoff
Use the card and the saved [Lab handoff](../../ops/codex-lab-workflow.md).
