# Rules

Rules live only in the `CLAUDE.md` baseline, this file and `roles/`; numbers live only in `policy.json`. Numbering is kept, never renumbered (⑦⑪⑫ deleted).

## ① Baseline
Enforced by: gitleaks, prod_write_gate, new_intent, main_checkout_gate, backend; manual: first-round review / every PR

See `CLAUDE.md`.

## ② Cards and handoff
Enforced by: handoff, start_sheet, card_handover; manual: lead / weekly review

One card = one acceptable result, one owning session, one bc, one Issue; if entry conditions fail, stop and tell lead. When a card is handed over (PR merged or closed), overwrite the handoff file first (cap: `policy.json` `start.handoff_max_kb`), then end the session or `/clear`; correct records that contradict the facts; check the actual state when starting. Lead opens every new session with start_session. Session messages only wake someone up; content goes on the card or PR. Send a session message only when someone can now review, use, decide or unblock something (lead: only when blocked, when the user must decide, or on an incident); never repeat an unchanged status; while waiting on CI, the queue, a review or lead, end the turn and let a background task, Monitor or message wake you instead of polling. A dev session that finishes a card takes the next one in its handoff order without waiting for lead; lead changes the order in the handoff. A card with a prerequisite starts after it merges; to work in parallel, stack the branch on the prerequisite's PR branch and rebase after it merges.

## ③ Branches and worktrees
Enforced by: main_checkout_gate

Session name S is a key of `policy.json` `sessions.config`, set per worktree by its session marker (lead sets it when starting the session); dev sessions are `one-dev-<bc>`. Branch `claude/S-<card>-<short>`, cut from the latest main, living and dying with the card; cards that change the same file run in series; git writes use absolute paths.

## ④ Commits and pushes
Enforced by: manual: everyone / every time

One item per commit, with the Session trailer. Pushing runs no tests; tests run in CI. Long commands (builds, full test runs) run in the background, never polled in the foreground; report success only with the exit code. Edit only the needed span; filter long output to a file and read on demand, failures in full; hand bulk reading to a subagent with files and line numbers.

## ⑤ PRs and review
Enforced by: post_review; manual: first-round review / every PR

A PR merges and reverts on its own and follows the template. First line `Closes #<card>` only on the PR that completes the card; earlier PRs of the same card use `Refs`. Guards, CI, hooks, cross-bc interfaces, mechanical renames and deploy blockers each get their own PR, and main stays green after each. The author dispatches a fresh reviewer per `scripts/review/PROMPT.md` (model and scope there); it only writes the verdict file, the dispatcher posts it with post_review on the reviewed head before pushing anything else, since post_review refuses once the head has moved; security, production, hook and parser changes add an opus final review. 必改 is limited to correctness, hard constraints and the baseline; lead releases 必改 fixes after reading the diff; on lead's own PRs a fresh reviewer checks the fix instead.

## ⑥ Queue and merge
Enforced by: mq, relevant_change; manual: lead / every PR

After review passes the author runs `mq.py enqueue`; only the merge queue merges, and nothing is pushed after enqueueing. Docs changes go in small PRs; those relevant_change classes as docs need no review and are enqueued the same way.

## ⑧ What goes into git
Enforced by: scan_aggregates, build_asyncapi, gitleaks

What CI or another session reads goes in; what the repo can generate, or is one-off, stays out; personal tooling lives in the tool library; secrets and personal data never go in — scan the full history before reporting. One source per kind, owned per the role files; edit sources, never generated output. Memory holds only user preferences and pointers; rules go back to the repo, and the repo wins on conflict. A standing task, persistent config or cloud resource is registered in the resource list and cleaned up after use.

## ⑨ Decision boundary
Enforced by: new_intent; manual: lead / every conversation

The user decides (intent 2026-10-28-215645): business scope and order; business rules, wording, accounting treatment (via finance); which legacy behaviours to reproduce or change; functional acceptance; resources (session count, budget, purchases, external accounts); red lines: deployment and production changes, credentials, roles that see sensitive data, outside calls, corrections after close, reopening periods, and other irreversible acts.

Lead and sessions decide within the baseline, lead reports results to the user daily: code, architecture and ADRs, tests, scripts, CI, process, tools, rules and harness, and other reversible technical choices; check pstack's principles and playbooks first.

Only lead asks the user, in the conversation, with options and a recommendation; record the user's words verbatim as `docs/rules/roles/lead.md` rule 4 sorts them (business rules via new_intent, batched; go-aheads as comments). Anything that contradicts a recorded decision word for word: stop and tell lead. Pushing, opening PRs and triggering CI are standing permissions unless the card limits them; authors may turn on Auto-fix for their own PR, never auto-merge; app-level standing permissions and gaps left by hooks are not user authorization.

## ⑩ Models
Enforced by: manual: lead / every dispatch

Session models and config: `policy.json` `sessions`. opus only for first-round review, security, guard implementation and final review, and design judgement, and the standing acceptance-test session one-test (user 2026-11-02); everything else sonnet. A subagent brief names model, scope and output cap; never dispatch the same thing twice; Explore and Plan don't read CLAUDE.md, so their prompt carries the baseline.
