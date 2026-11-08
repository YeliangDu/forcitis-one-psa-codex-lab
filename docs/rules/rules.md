# Rules

Rules live in the repository baseline, this file and `roles/`; numbers live in `policy.json`. The personal Lab's runner and session path is [the Codex workflow](../ops/codex-lab-workflow.md). Numbering is kept (⑦⑪⑫ deleted).

## ① Baseline
Enforced by: gitleaks, prod_write_gate, new_intent, main_checkout_gate, backend; manual: first-round review / every PR

See `CLAUDE.md`.

## ② Cards and handoff
Enforced by: manual: lead / every card and restart

One card = one acceptable result, one existing resident owner, one bc and one Issue. Before coding, verify the signed prerequisites, affected readers and exact base; ask lead for the applicable hosted red or baseline evidence. Save the assignment and current state before sending work. A session resumes the same assignment after restart. When GitHub confirms a merge, record the result, clear the assignment and check the next eligible card. A business hold blocks only the affected slice.

## ③ Branches and worktrees
Enforced by: main_checkout_gate

Use a named Codex resident in `policy.json` and verify its physical worktree's Lab Git common directory and sole origin. A new card can use a separate managed worktree; keep the resident session ID. Use the current Lab baseline and serialise cards that change the same file.

## ④ Commits and pushes
Enforced by: manual: everyone / every time

One item per commit, with traceability to the Issue and Codex resident. GitHub-hosted Actions runs all compilation and checks for that exact commit. Report actual status and counts; keep large logs in the hosted artifacts.

## ⑤ PRs and review
Enforced by: post_review; manual: first-round review / every PR

A PR merges and reverts on its own and follows the template. The first line is `Closes #<card>` only on its completion PR; earlier PRs use `Refs #<card>`. Lead assigns an independent existing Codex reviewer per `scripts/review/PROMPT.md`; guarded paths also require a different final reviewer. Only current-head verdicts count. Fix concrete must-fixes and review the resulting head.

## ⑥ Queue and merge
Enforced by: mq, relevant_change; manual: lead / every PR

Lead admits a PR only through `mq.py` with current-head independent review and required hosted evidence. Do not push after admission. The canonical queue verifies current base and records independent GitHub readback before clearing the card. Labels describe status; they cannot establish approval.

## ⑧ What goes into git
Enforced by: scan_aggregates, build_asyncapi, gitleaks

What CI or another session reads goes in; what the repo can generate, or is one-off, stays out; personal tooling lives in the tool library; secrets and personal data never go in — scan the full history before reporting. One source per kind, owned per the role files; edit sources, never generated output. Memory holds only user preferences and pointers; rules go back to the repo, and the repo wins on conflict. A standing task, persistent config or cloud resource is registered in the resource list and cleaned up after use.

## ⑨ Decision boundary
Enforced by: new_intent; manual: lead / every conversation

The user decides (intent 2026-10-28-215645): business scope and order; business rules, wording, accounting treatment (via finance); which legacy behaviours to reproduce or change; functional acceptance; resources (session count, budget, purchases, external accounts); red lines: deployment and production changes, credentials, roles that see sensitive data, outside calls, corrections after close, reopening periods, and other irreversible acts.

Lead and sessions decide within the baseline, lead reports results to the user daily: code, architecture and ADRs, tests, scripts, CI, process, tools, rules and harness, and other reversible technical choices; use this repository's playbooks.

Only lead asks the user, in the conversation, with options and a recommendation; record the user's words verbatim as `docs/rules/roles/lead.md` rule 4 sorts them (business rules via new_intent, batched; go-aheads as comments). Anything that contradicts a recorded decision word for word: stop and tell lead. Pushing, opening PRs and triggering CI are standing permissions unless the card limits them; authors may turn on Auto-fix for their own PR, never auto-merge; app-level standing permissions and gaps left by hooks are not user authorization.

## ⑩ Models
Enforced by: manual: lead / every dispatch

Session runtime and roles are in `policy.json` `sessions`. Use the assigned Codex resident and its configured model, including independent acceptance and review. Do not create a duplicate chat to deliver or review a card. GitHub-hosted Actions is the check runner.
