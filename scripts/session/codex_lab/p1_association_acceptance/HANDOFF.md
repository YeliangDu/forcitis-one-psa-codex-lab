# #62 revision3 bounded association — independent isolated A

Exact B77e872e8ba9661d3294d3a9337c8e5c956b70641. Prior checkpoint A/green assertions remain untouched. Only this ignored association package was authored. No local tests, network/native sends, original repo, tracked/Git/ref/worktree/remote, hook/daemon/settings/activation/canonical changes.

## Existing executable comparison

Baseline is byte-identical existing archived #28 resident_app_matcher.py, SHA2566faec4f779cf74c43b2e05a3dd318862be7c88433c4ffb2345baa6587cc53340. Its actual match_dispatch_ack(claim,prompt_bytes,pages) API runs in baseline mode with exactly its existing schema: no extra keys/args, missing-interface fallback or new matcher stub. Before assertions a separate truly exhausted synthetic fixture must match through this real API, proving execution/schema/fixture capability.

Bounded positive fixtures retain two newest-first pages, exact predecessor boundary and unique full exact agent ACK. The final native hasMore remains true and its opaque JSON-shaped cursor points to older history; the matching legacy page retains that same non-null continuation. Every legacy field is valid, including first request/claim anchor, next-page continuity, canonical ACK, identities/status and prompt digest. Existing code rejects at its last-page nextCursor-is-None guard: genuine behavior red, not an incompatible input/API red.

The truly exhausted control is independently authored with hasMore=false/nextCursor=null. It is not a mutation/repair of the bounded positive or a claim that native older history was exhausted. Fixtures are synthetic only, including UUIDs, paths and opaque tokens. They make no claim about private cursor contents or actual SDK cursor semantics; never parse ordinal/anchor internals. Readonly normalization design and previous witness metadata informed the contract but no private window/IDs/user paths were copied.

## Candidate interface and exact checks

Candidate is separately selected by external reviewed SHA, never active C. It must expose the actual same named callable with explicit keyword:

```
match_dispatch_ack(claim, prompt_bytes, legacy_pages, window_proof=proof)
```

The keyword carries complete synthetic native records/pre-send predecessor/newest-top/final-top observations; CONTRACT.json freezes its fields. Worker only invokes the selected source, never normalizes or validates the window for it. Candidate missing callable/signature/closure is HOLD86, not expected red. Selecting the old three-arg module as a candidate is therefore HOLD; baseline comparison explicitly remains the compatible old call. This API extension is frozen for review, not claimed as already implemented.

11 methods/12 scenarios: bounded initial/resume positives, truly exhausted source control, missing predecessor, broken cursor continuity, wrong thread, wrong whole claim, duplicate ACK, tool-output ACK, changed final top, quote/suffix. Whole-message match returns authoritative envelope id only. Any HOLD has reason but no tentative turn/claim. Raw request/next cursor strings, hasMore/order, turn and item IDs/type/phase must remain exact in returned audit_index. No rewriting final continuation or input objects. Full history must NOT be reported exhausted on the bounded positive. Candidate ignoring stability/boundary and simply nulling a cursor cannot pass all cases. Match is correlation only, not send/resume/permission.

Baseline refusals on negative cases are preserved controls; they do not individually prove newly required proof/moved-top logic while its bounded positives remain red. Those become meaningful candidate coverage only after the actual candidate positive is green. The proof is caller-supplied synthetic data; actual native transport authentication/stable collection remains separate.

## Hosted containment / selector

New tests-only prefix: scripts/session/codex_lab/p1_association_acceptance/. Use exact patch, not author RESUME/output ledgers. Trusted workflow must verify external manifest SHA and every frozen file before executing any package script. Standard GitHub-hosted Ubuntu/Python3.12, read-only repository token, persist-credentials=false, no secrets.

Baseline:

```
python3 -B "$PACKAGE/run.py" --manifest-sha256 "$EXTERNAL_A_MANIFEST_SHA256" --report "$RUNNER_TEMP/association-baseline.json"
```

Candidate, unchanged A:

```
python3 -B "$PACKAGE/run.py" --manifest-sha256 "$EXTERNAL_A_MANIFEST_SHA256" --target "$REVIEWED_MATCHER_SOURCE" --target-sha256 "$EXTERNAL_CANDIDATE_SHA256" --report "$RUNNER_TEMP/association-candidate.json"
```

run.py uses actual /usr/bin/unshare --user --map-current-user --net. UID remains non-root, namespace inode differs from outer parent, actual interfaces only lo and no external route. No sudo/sysctl/AppArmor/settings/container/fallback. If unavailable, HOLD86; never count missing containment as red. Actual selected API executes read-only in a further standard-library audit fixture with minimal environment: no file mutation, process/network/SQLite/chdir. This is test containment, not an operational broker or universal hostile native-code sandbox. Source copies/inputs unchanged and no sender port exists; no native send can occur.

Source-derived expectation only: tests11/failures2/errors0/skips0. Exact assertion markers:

- P1_ASSOC_BOUNDED_INITIAL_REJECTED_AS_TRUNCATED
- P1_ASSOC_BOUNDED_RESUME_REJECTED_AS_TRUNCATED

EXPECTED.json pins full IDs. Raw baseline red stays exit1. Wrong pins/import/tool/fixture/API/namespace, errors/skips/zero discovery/unexpected baseline green/other assertions are HOLD86. Candidate needs all11 green, zero errors/skips; no output inversion. Preserve actual A/selected target hash/B/source bytes, selectors/log/result/native job/run/attempt/artifact provenance. Environment fields alone are not native hosted verification.

## Remaining native gaps

No real window-v2 collector, current send boundary/cursor authentication, original-session permission/CWD/active C or actual native continuation was exercised. Previous native witness remains private evidence for basic read-only connection; it is not an active adapter or hook proof. This slice sends nothing and cannot activate dispatch, hooks, daemon, canonical queue/journal or writer. P0/P1 refinement leaves those operational gates intact.
