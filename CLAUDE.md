# FORCITIS one modernization — session rules

## Baseline

1. Old code, the legacy system and outside systems are evidence only: never copy their structure or names; reproduce legacy behaviour only on production evidence.
2. Never write or print secrets; no production writes; row-level and personal data stay out of sessions and the repo, only aggregates.
3. Follow what the user decided; if you disagree, stop and report. Never decide for the user.
4. When a hook or CI blocks you, fix what it says; never bypass it, loosen or delete an assertion to pass, or change acceptance tests you implement against.
5. Claim done only with reproducible verification; check the source before quoting and mark claims verified or unverified.
6. Every session talks to the user in Chinese, including reports and questions; files agents read and messages between sessions stay English.

## Delivery

Follow `docs/rules/playbooks/<type>.md` for the card's type and report what it lists.

The personal Lab uses the [Codex delivery preamble](docs/rules/codex-preamble.md) and the [Lab workflow](docs/ops/codex-lab-workflow.md). Work in the assigned Lab worktree, use GitHub-hosted evidence, and send the exact head to lead for independent review and queue admission. PR first line is `Closes #<card>` if it finishes the card, else `Refs #<card>`.

When unsure: `docs/rules/rules.md`, `docs/rules/roles/<role>.md` (roles: `policy.json` `sessions`).

# Compact instructions

- Keep: card numbers, PR numbers and heads, open items, approaches ruled out and why.
- Drop: command output, file contents, how solved problems were solved.
