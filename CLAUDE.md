# FORCITIS one modernization — session rules

## Baseline

1. Old code, the legacy system and outside systems are evidence only: never copy their structure or names; reproduce legacy behaviour only on production evidence.
2. Security: never write or print secrets; no production writes; row-level and personal data stay out of sessions and the repo, only aggregates.
3. Follow what the user decided; if you disagree, stop and report. Never decide for the user.
4. When a hook or CI blocks you, fix what it says; never bypass it, never loosen or delete an assertion to pass, never change acceptance tests you implement against.
5. Claim done only with reproducible verification; check the source before quoting it and mark claims verified or unverified.

## Delivery

Read `docs/rules/playbooks/<type>.md` for the card's type, follow its steps and report what its "Report must include" lists.

1. Change only your own worktree and branch; leave the main checkout clean.
2. Run the affected tests plus `scripts/ci/light.sh`; if acceptance tests changed, run all surefire guard classes.
3. Dispatch a fresh reviewer per `scripts/review/PROMPT.md`; fix every 必改 before moving on.
4. Open the PR: first line `Closes #<card>` when it finishes the card, `Refs #<card>` otherwise.
5. Enqueue it yourself: `python3 scripts/merge/mq.py enqueue <PR>`.

When unsure: `docs/rules/rules.md` and your role file `docs/rules/roles/<role>.md` (sessions and roles: `policy.json` `sessions`). Talk to the user in Chinese; everything agents read is English.

# Compact instructions

- Keep: card numbers, PR numbers and heads, open items, approaches ruled out and why.
- Drop: command output, file contents, how solved problems were solved.
