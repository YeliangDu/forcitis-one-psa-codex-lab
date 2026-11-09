# #60 owned-runtime-integration independent acceptance

FROZEN, STATIC ONLY, NOT RUN. Entry run_acceptance.py; exactly5 unittest methods. This is a new test package only. Baseline5094eb8b0aa25e6dae12b3f658a74a5f865c29c14c9c02e427800378a0fa6287 is bundled as public source; implementation is not changed. CONTRACT.json enumerates exact installation/request keys, response actions/state names and lifecycle rules. Candidate must extend the same portable_broker.py with serve-owned/call-owned and existing source-closure flags; no alternate runtime entry or old NativeProxy.

## Fixture and authority

Each case starts in fresh private RUNNER_TEMP/owned-test-*; installation0600, directories0700, real SQLite owner ledger. The unchanged public base runtime_store.Store from b9ea6841… creates its actual8-table scratch schema; test setup installs synthetic owner/generation/token hash/paused0, verifies actual Store.owner, then closes it. portable_config.PrivateRuntimeBinding is used only as constructor data; no NativeProxy, ResumeStore, migration, role transfer or actual native registration is imported/executed. Production handler must use existing owner/generation/pause semantics, not config-supplied auth or cached credentials. All tokens are fresh synthetic test values; no actual owner/auth/account files.

stdio_fixture.py is a restricted actual OS child, never a mocked subprocess. Allowed requests initialize/config/read/thread/list only; no task/login/model/network. It mints its own instance/PID and audits actual wire. Private test control data selects wrong/unexpected response ID, real exit, real stdout disconnection or replays an exact old process response during a later epoch. Those are fixture-only faults selected by test-owned private argv, never production permissions. Child/source/interpreter byte pins are part of the installation. Fixture kind must not be silently treated as production. Native ID means real JSON-RPC wire id, NOT an invented App Server thread ID.

## Five predicates and baseline red

1. Actual public CLI declares serve-owned/call-owned; fresh owned control starts no child implicitly. Explicit start creates real child; config/thread responses correlate request_id/epoch/native_id to audited stdin/stdout; stop waits terminal, status/stop observe stopped.
2. Wrong/unexpected wire ID => hold/no native result; runtime becomes faulted/disconnected/stopped, no stale result accepted.
3. Actual child exit or stdout disconnect => hold, no cached successful config, owned stop remains possible.
4. Current owner/generation gates all operations. Pause persists in real SQLite; start/config/thread denied while paused but status/stop observable. After child stop, actual controlled Broker process restart with same installation/DB preserves pause and creates no child. Stale generation rejects start/status/read/stop/pause; new current owner can explicitly clear pause.
5. Same Broker stop/start produces a real fresh child instance/epoch; active duplicate start cannot create another child. Old epoch read/thread/stop never reach new stdin. An audited old response replay cannot satisfy current pending request or reuse old native wire ID.

Baseline preflight verifies all source/tool inputs and runs actual portable_broker --help (exit0) before suite. Its five setUp behavior assertions are exactly OWNED_RUNTIME_PUBLIC_MODES_MISSING:<method-name>. On frozen baseline, expected5 failures,0 errors/skips, exit1; fixtures/SQLite/owned lifecycle have NOT started. This is missing required public behavior, not an argparse usage error or missing-file fixture failure. Do not relabel it native/process behavior coverage. Once modes exist, all five bodies must run and green requires5 tests/0 failures/errors/skips plus per-case actual lifecycle evidence. Missing source/tool/help/fixture or unexpected errors => HOLD86, never expected red. No result has run here.

## Actual wrapper parameters

Matches lead wrapper:
python3 -I -B <package>/run_acceptance.py --runner-temp "$1" --source-root "$2/source" --evidence-dir "$3" --parent-netns "$4" --source-closure-sha256 "$5"

Entry defaults to bundled baseline-source if omitted. For later implementation use externally pinned complete source closure, including new modules. Driver itself remains frozen. Export acceptance runtime dependencies from DEPENDENCIES.json separately from source (no test files in source inventory). Default hosted Ubuntu24 non-root, Python3.12+, fresh private mount/net/PID namespace only lo, no-new-privileges/env-i, permissions{}, no token/agent/auth HOME/formal mounts. Real stdio fixture requires no actual App Server/binary/login/network, only Python. Fixed source pin belongs to trusted launcher, not candidate response. Outer timeout10minutes/namespace kill on cancellation.

Upload ONLY verdict.json and case-observations.json. Evidence distinguishes preflight from each actual case/fixture/control start, pass/failure/error and actual process/wire/epoch summaries. Red has fixture_started=false; successful bodies produce actual counts, not a static false flag. Owner secrets/private requests/logs are not uploaded. Author receipt is not a runtime dependency and must not enter public hosted repository. Existing owner packets/oracle/candidate/old state unchanged; no OpenSSH, Goal, writer or unrelated scope advanced. All preparation consisted of file writes, byte hashes and AST; no tests/CI, real hosts, services, App Server or owners were operated.
