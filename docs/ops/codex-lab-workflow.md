# Personal Codex Lab workflow

Codex is the development, test authoring and review runtime for this repository. GitHub-hosted Actions executes the checks. The one-lead coordinator and the shared queue can run on the Mac; they send work, read GitHub results and control admission without compiling or testing locally.

- **Open and assign.** Choose the next signed and unblocked slice from the Lab plan, record the Issue and base, then use the assigned resident's existing session ID. Save the role, worktree and assignment before moving the card. If a role is unavailable, report or resume that role; don't create a replacement.
- **Prove the change.** Follow the [Codex preamble](../rules/codex-preamble.md). The hosted PR workflow is [Lab PR CI](../../.github/workflows/lab-pr-ci.yml). Preserve the exact hosted red, green and integration evidence. Check the current head and base at readback; a label or older green run is insufficient.
- **Review and release.** Use the repository's title, card link and independent review rules. Keep one queue via [mq.py](../../scripts/merge/mq.py), using the Lab Git common directory. A waiting label describes queue state; the PR event starts CI. The queue reads hosted evidence, checks current admission and records GitHub merge readback before clearing a card. Keep writes to main disabled while the production adapter or exact-head main signal is unavailable.
- **Recover.** Resume the saved session and assignment after restart. A recorded but uncertain merge request is reconciled against GitHub, not resent. A successful readback makes dependent slices eligible for the next scan.

The old self-hosted CI, production deploy and release workflows are outside this Lab migration. Keep their Lab copies disabled. See the [runner reference](ci-runners.md) only when investigating legacy runner behavior.


## Branch lifecycle and V2 confirmed handoff (#45)

The formal entry is `scripts/session/codex_lab/lab_branch_live.py`. Its CLI and
`ProductionPorts.branch_command` both use `native_command` -> the same
`run_branch_command` dispatcher for dispatch, publish, update, ensure-pr,
read-ci, rerun-ci and readonly post-merge. There is no separate test-only
production callback. `--help` constructs no native ports.

Native assignments use the owned0700 `.codex-lab/branch-lifecycle/` directory,
owned0600 `policy.json` and `state.json`, and the Lab resident registry. Each card
contract fixes role/session/worktree/business branch, explicit allowed actions,
reviewed CI workflow and PR metadata. Mutating dispatch/publication/PR creation
or CI rerun requires explicit `--execute` and the corresponding installed card
contract; missing credentials or incomplete native inventories HOLD. Existing
branch/PR adoption must preserve the exact recorded identity. Unknown publish,
PR-create or rerun outcomes are reconciled from actual remote/native readbacks,
never blindly sent again. A verified CI identity/outcome is not merge authority.

After ordinary `run_once` returns confirmed (or cleans an already confirmed
record), the operator rechecks existing V2 and absence of that PR from canonical
FIFO/head under its merge lock. It then releases that lock before invoking
`ProductionPorts.after_confirmed_merge`. That method locates exactly one existing
assigned binding and invokes the same native `post-merge` dispatcher. Post-merge
reopens strict V2 readonly storage under original existing merge/queue/head lock
inodes, checks exact durable subject/merge and cleared admission, collects actual
native merged parents/target ancestry plus occupation/proof index, and rechecks
canonical completion after native reads. No branch state save, journal migration,
permission repair, writer call, archive or deletion occurs. Missing state/locks,
V1/unknown schema, unadopted protected record or inconsistent readback HOLD.

The resulting `branch_retirement` is a readonly `retirement-candidate` with
`allows_delete=false` and `release_authority=false`. Any lifecycle failure is an
independent nested HOLD: the original confirmed action/reason/merge SHA remains
unchanged and the main writer is never retried. No ordinary admission, protected
upgrade adoption or `require_new_write_ready` rule is relaxed. `record-confirmed`
is a compatibility alias through the same V2/native checks, not permission from
a caller's queue_completed flag.

The native `retire` entry is disabled in this delivery. The archive/exact-lease
retirement algorithm remains available only to explicitly injected isolated
acceptance ports. Actual retirement still requires independently verified native
assignment, PR/CI/queue/merged proof, complete proof-reference inventory, actual
archive namespace/server support, retention and separate deletion authorization.
No scratch green or readonly candidate substitutes for those proofs. No worktree,
unrelated branch, recovery ref or main is removed here.

Run the original frozen8 and additive V2 handoff6, all53 affected canonical queue
regressions and applicable gates on the same GitHub-hosted candidate. The operator
regression fixture now explicitly creates private V2 storage and reads it with
ReleaseJournalV2; its test methods/assertions remain byte-identical. No local
builds/tests or production workflow operations are part of this handoff.
