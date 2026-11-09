# #63 rev9 fixture-only local ancestry repair

Rev8 is preserved byte-for-byte in its original directory. Hosted 42561762501/1 readback records both B/H reaching real quarantine parent/tree validation and then failing the fixture PGID assertion (0 != 73), before the intended lock assertion. This is not the expected baseline red, nor integrated green.

Only local ancestor selection changes: snapshot live expected owner identity; truncate SSH ancestry strictly at that owner inclusive; validate owner start identity and parent links, then require ordered SSH -> optional intermediate processes -> unique real Git -> owner, with every segment identity live. Existing unique real executable checks, per-row PGID/session assertions and pidfd signaling remain unchanged. Ancestors above owner are no longer incorrectly included.

Static proof: every self.assert* call and every stop_exact/pidfd call is AST-identical and in the same traversal order. All other source lines differ only by the recorded additive local ancestry checks/truncation (see STATIC-DIFF.patch). Namespace launcher bytes are identical to rev8. Quarantine validation (no object pre-staging), V2 migration/run_once, invocation counts, unknown/wrong-proof rejection, actual remote ref/parents/tree reconciliation, final historical lock assertion and B/H pins are unchanged.

Expected fresh hosted comparison remains one test each: B 1 failure at final named lock-lifetime assertion, H 0 failures, both 0 errors/skips, conditional on all controls passing. Do not treat fixture or capability failures as this expected red. This repaired package has not run locally or hosted; old component runs remain component-only evidence.

Use the identical rev8 namespace.sh and the exact hosted launcher instructions reproduced below, substituting this rev9 package directory only. No changes to B/H, prior acceptance, products, formal queue or writer authority; no local tests/build/network/push/activation.

---

# #63 rev8 real OpenSSH + V2 independent integration

One executable test, two sequential gates in one fixture. Expected B: 1 test / 1 assertion failure / 0 errors / 0 skips. Expected H: 1 test / 0 failures / 0 errors / 0 skips, conditional on all capability controls. These are predictions; this integrated package has not run. Historical component runs 42561093526/1 and 42560680809/1 are not integrated proof.

The test invokes unchanged ProductionPorts.git -> mq_github.sh -> real Git -> real OpenSSH -> disposable loopback sshd -> real receive-pack. It uses actual V1 creation, supported migrate_v1 with backup and snapshot digest under fixture runner/merge locks, ReleaseJournalV2 and mq_lab_active.run_once. Constructor/live verifier and production repository authorization are deliberately not instantiated; no live writer authority exists.

At pre-receive, actual incoming objects remain in quarantine. The hook verifies GIT_QUARANTINE_PATH plus merge type, ordered parents, expected tree and head object in its inherited Git environment, then publishes its barrier. No new objects are pre-staged in receiver; external new-object verification occurs only after real receive-pack finishes. Only base is initialized before network push.

Gate 1: verify real client SSH -> optional intermediate shell -> Git -> owner and independent remote hook/receive-pack/sshd ancestry. The private root launcher pins listener PID/start identity/exe; non-root test associates that listener identity without requiring access to root sshd /proc/exe. Kill owner via pidfd and sample same merge-lock inode while real Git/SSH are still live before and after sample. Save observation. Final historical lock assertion is delayed until all other recovery and cleanup controls finish.

Gate 2: pidfd-stop all remaining client group processes and require local quiescence. Remote hook and receive-pack must still be live. V2 unknown and wrong-head readback retain reservation without verifier or new sends. Release real remote barrier, require actual receiver ref advances, verify exact parents/tree/ancestry, reject wrong-head evidence again, confirm exactly once, repeat recovery idle. Do not assert local flock covers remote lifetime after all clients die. All positive and negative transport controls use real OpenSSH: pinned key read succeeds, wrong key fails authentication (255), allowed key with unauthorized command is rejected (77).

## Exact hosted launcher

Use ubuntu-24.04 GitHub-hosted with sudo and OpenSSH server installed as in verified wire run. Freeze this package identically in both jobs. Independently export B=77e872e8ba9661d3294d3a9337c8e5c956b70641 or H=47e263825afb62a1b860915f8293da79572d12f6 plus full verified actual source/import closure to a disposable source directory, not a live Lab checkout. Check package hashes and tool identities externally. No credentials/secrets or persistent Git credentials; workflow permissions {} and no outbound network inside namespace.

Create private work root with mktemp under RUNNER_TEMP (no spaces), owned by runner user mode 0700. Capture host net namespace identity. Invoke:

sudo -n /usr/bin/unshare --mount --propagation private --net --pid --fork --kill-child --mount-proc -- /usr/bin/env -i PATH=/usr/sbin:/usr/bin:/bin /bin/bash <frozen-package>/namespace.sh <work> <runner-uid> <runner-gid> <runner-user> <host-netns-identity> <disposable-source-root> <frozen-package>/test_openssh_v2_integrated.py

namespace.sh derives from exact verified workflow d2821360cc77f725f58b742d4b97dea72249c3cf, SHA256 0eac9975f5c82dc89d323b7e06c8263f5b71022b8f31ca3a4d48f1669de4bc1b. It sets up only loopback on port 40363, mount-private /run and passwd/shadow, ephemeral host/client keys, pinned known_hosts, forced disposable receiver command, disabled passwords/PAM/forwarding/PTY and non-root/no-new-privs test invocation. It adds listener identity attestation and preserves unittest exit status. Run baseline and candidate in distinct namespaces/work roots. Outer job timeout max 5 minutes; terminate namespace on cancellation. Namespace PID1 exit and --kill-child destroy remaining sshd/session processes; trap stops listener. Outer trap removes work root only after namespace termination.

Upload only sanitized unittest stdout/stderr/identity and exact counts; never upload work root, keys, passwd/shadow or raw environment. Key material stays only in private fixture files and disappears at cleanup. Do not treat exit 1 alone as expected red: require one discovered test, final named lock assertion, 0 errors/skips and real-openssh-v2-reconciled event. Namespace/sshd/auth/migration/identity/quarantine/remote-lifetime/cleanup failures are capability HOLD or unexpected failures, never the intended red. Retain raw failed verdict when controls fail; do not loosen assertions.

Only AST and immutable-object hash checks were performed locally. No tests, builds, network connections, Git mutations, native sends, settings, canonical writes or activation were performed. Prior A/A3/rev6/rev7 files and B/H remain unchanged.
