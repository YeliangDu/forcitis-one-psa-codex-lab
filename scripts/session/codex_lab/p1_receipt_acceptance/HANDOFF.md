# #62 revision5 VerificationExecutor receipt provenance — independent A

Exact B77e872e8ba9661d3294d3a9337c8e5c956b70641. Prior checkpoint/association A and assertions remain unchanged. Only ignored receipt-one-test was authored. No local tests/build/containers/network/native sends, Git/ref/worktree/remote/settings/hooks/daemon/activation/canonical changes or original repository access.

## Actual existing behavior and separate candidate

baseline/lab_queue_live.py is exact B, SHA256543f992be547dda4d0dbe0d237788172440f3c7ee292a64ffabefa368c62dc54. Exact B lab_queue_evidence.py was also read and hash-recorded; its queue/admission function is NOT called. The actual read seam is GithubRead(request=fixture_get).artifacts(R,A), already exposed by B. A valid baseline ZIP/import/get positive runs first. No replacement reader/validator/stub exists in the harness.

B downloads/decode_receipt and adds row.receipt without comparing the raw ZIP to declared digest. The principal named red serves a valid decoded JSON ZIP but metadata digest of zeros; actual B still returns row.receipt. Another changes ZIP comment bytes with identical decoded JSON while declared/expected original digest remains fixed. Those are source behavior comparisons, not missing interface/import/native access. This does not establish live vulnerability or upstream impact; an external consumer may apply other checks.

Candidate is separately selected by external reviewed source SHA, never active C. Its explicit API is GithubRead(request=fixture_get,expected=independent_tuple).artifacts(R,A). The extra expected constructor keyword is not passed to baseline mode; missing/incompatible candidate API/closure is HOLD86. A reviewed separate self-contained standard-library module may implement that API; no candidate implementation is supplied.

## Frozen scope / cases

17 methods,14 expected baseline assertion failures,0errors/0skips,3 control methods green. EXPECTED.json freezes full IDs/markers. Positives preserve exact raw ZIP/native-shaped metadata correspondence and the original predecoded-metadata rejection. Missing/expired/old-name proof is refused. Negatives cover raw digest mismatch/tamper, foreign repo, wrong run/artifact ID/metadata head, external ZIP pin, receipt H/B/body run, missing digest, actual run/body stale attempt, and independently selected profile applicability.

For H/B/body-attempt/profile negatives, synthetic new raw bytes and their declared AND externally selected fixture digest are deliberately fixed together while the expected semantic tuple stays unchanged. Thus those cases cannot merely pass because every changed ZIP was rejected on an old hash. No real API field is fabricated as live fact: fixtures and GET tape are synthetic. Actual artifact metadata has no invented run_attempt/producer_job_id; A is tied through expected attempt, name, run snapshot and receipt environment IDs.

Worker invokes the real selected module/class/method, injecting only its documented read-only request port. It does no hash/tuple/profile validation on behalf of the target. It returns actual rows/refusal and GET-call trace; malformed/unprovided API/tool is HOLD. No queue, planner/admission, merge, approval or writer entry is used. B's original ZIP decoder/metadata receipt pop guards remain unchanged.

Expected profile is test-owner-selected name/workflow/applicability. This checks supported run/receipt fields only; it does not authenticate actual workflow source, selector version/plan, hosted job/upload producer or active control. These full C/authority seams are separately HOLD in CONTRACT.json. Fake/absent native capability cannot count as one of the red assertions.

## Hosted non-root/no-network runner

New tests-only prefix scripts/session/codex_lab/p1_receipt_acceptance/. Trusted independent standard Ubuntu/Python3.12 workflow checks external manifest SHA and all file hashes before executing any candidate/package code; readonly repository token, persist-credentials=false, no secrets. Apply exact patch only; omit author RESUME/output ledgers. A/source bytes, selectors and manifest remain immutable.

Baseline:

```
python3 -B "$PACKAGE/run.py" --manifest-sha256 "$EXTERNAL_A_MANIFEST_SHA256" --report "$RUNNER_TEMP/receipt-baseline.json"
```

Candidate, same A:

```
python3 -B "$PACKAGE/run.py" --manifest-sha256 "$EXTERNAL_A_MANIFEST_SHA256" --target "$REVIEWED_READER_SOURCE" --target-sha256 "$EXTERNAL_CANDIDATE_SHA256" --report "$RUNNER_TEMP/receipt-candidate.json"
```

Launcher uses actual unshare --user --map-current-user --net, non-root UID, distinct parent netns inode, actual interfaces only loopback and no external route. No sudo/sysctl/settings/container/network fallback; unavailable capability HOLD86. Fresh per-attempt report required. Inside, standard-library audit containment denies candidate writes/processes/sockets/SQLite/chdir and restricts reads to synthetic root/Python runtime. Reviewed self-contained Python only; not a universal hostile-native-extension sandbox or an operational broker. No actual Lab runtime/queue exists in the test process.

Baseline expected red remains raw exit1 with exactly14 named assertions. Pin/fixture/import/API/namespace failures, errors/skips/zero discovery/unexpected green are HOLD86, never expected red. Candidate must pass all17 zero errors/skips. Preserve full logs/result/source hashes, actual A/job/run/attempt/artifact/native hosted provenance by independent readback; env fields alone are not proof. No tests ran on this machine.

## Remaining HOLD boundary

No active C, actual hosted producer/workflow/job/upload authority, full current H/B/C/A/control-closure/profile-version verdict predicate, runtime admission or canonical side effect is proved. B is fixed source, synthetic H is input data, candidate is a separate source pin; none becomes active C. This package is a frozen expected-red deliverable, not ready/activation or live verification.
