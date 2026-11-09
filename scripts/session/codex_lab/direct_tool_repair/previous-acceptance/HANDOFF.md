# #60 single-role readonly task independent acceptance

FROZEN-STATIC-NOT-RUN. Entry run_acceptance.py, five executable unittest methods. Frozen baseline source is owned-runtime H25ddbb11df28c9a2c665d94ab7ed26cb05d3f0d5 supplied by lead; its copied actual SOURCE-CLOSURE SHA is c93b672988d75b3cfcbac458d6e4ae4fb8e5d26f2cd010c3596db83215d00df1. No candidate or old state changed. CONTRACT.json gives exact private JSON/CLI/request/response semantics; no wait for a separate design choice is required.

## Contract and fixtures

Public serve-owned/call-owned retain owned/source installation SHA flags and explicitly add --single-role-installation <absolute private file> plus --single-role-installation-sha256. New file schema lab-single-role-readonly-installation/1, kind fixture-single-role-readonly, exact keys schema/kind/owned_installation_sha256/role/assignment_file/max_assignment_bytes/dynamic_tool. Owned installation child kind fixture-single-role-stdio-only explicitly selects the new pinned worker; neither old owned fixture nor official production kind silently accepts this role fixture. Tool lab_read_assignment accepts only {} and fixed max4096-byte file; no arbitrary path/tool/model/login/network.

Owned driver base and actual existing b9ea6841 Store/private data classes are copied unchanged; actual native_role.RoleStore adds real scratch role tables without ResumeStore or migration. Initial registry/tasks/turns are empty; no synthetic registered thread or authorized tool result is pre-seeded. Private owner generation/pause/hash are genuine Store rows. Actual stdio child creates thread/turn UUIDs, preserves native projection across its own normal process restart and emits real item/started + item/tool/call; only Broker can read/authorize owner/current task/fixed file and reply. Worker does not maintain a parallel task/owner state machine or decide revision authorization. Model/account/auth never enter hosted fixture.

Role requests carry current owned epoch; dispatch/readback also exact revision. Registration action registered, resume resumed, dispatch dispatched, readback observed, denial hold with no read. Readback read={revision,sha256,assignment} equals actual file bytes/JSON. Actual SQL op must match thread/turn/call/generation/revision with ordered authorization_seq<execution_seq and its saved result equals the real native tool reply. Full native IDs and wire are correlated; fake fixture IDs do not prove actual App Server/native authentication.

## Five cases

1. Actual owner/revision/physical cwd gates: wrong cwd/stale owner publish cannot change real Store; exact publish works; wrong revision cannot issue native turn/start.
2. Real stdio thread/turn/call IDs correlate with durable registry/turn/tool rows and actual file read digest, not a candidate summary.
3. Same Broker runtime.stop/start, same private state, new epoch/child, actual resume same thread. Old epoch refuses; readonly readback returns the completed original turn/result. Same revision dispatch refuses and no second turn/start. This deliberately does NOT reissue an already consumed revision.
4. Unpaused revoke refuses. Actual pause -> revoke/fence -> publish revision2; old revision and still-paused revision2 dispatch refuse. Explicit pause(false) then first authorized new read must return revision2 file/digest.
5. Separate real control Broker stop/restart after paused child stop. Pause is still actual SQLite1; start/resume/dispatch/readback refuse before explicit resume and zero turn/start. After explicit pause(false), new child epoch, same thread resume and exactly one first turn/read. No invisible automatic turn/start.

Each case has actual per-case calls/native request IDs/process count/durable count evidence. Baseline preflight verifies actual source bytes and invokes real public portable_broker --help successfully. Five setUp assertions fail exactly SINGLE_ROLE_READONLY_PUBLIC_GATE_MISSING:<method-name> when the two required flags are absent. Expected baseline5 failures/0 errors/0 skips/exit1; no fixture/SQLite/child starts. This is required missing public behavior, NOT argparse usage failure/missing file/fixture crash. After implementation, all five full bodies must pass. Missing facilities/imports/fixture native completion -> HOLD86, never expected red.

## Actual launcher parameters and export

Matches prior wrapper unchanged:
python3 -I -B <package>/run_acceptance.py --runner-temp "$1" --source-root "$2/source" --evidence-dir "$3" --parent-netns "$4" --source-closure-sha256 "$5"

Bundled baseline-source default may be used. Source candidate/implementation must be a complete externally pinned closure; fixture driver remains independent. Export whitelist DEPENDENCIES.json outside source inventory; no author receipt in public hosted repository. Use non-root ubuntu-24.04/Python3.12+, private mount/net/PID only lo/no-new-privileges, env-i, permissions{}, no token/agent/account HOME/formal mounts. No actual App Server/old daemon/proxy/native role resources. Outer job timeout10minutes/namespace cleanup required. Upload only verdict.json and case-observations.json. Private scratch requests/owner secrets/raw child state are not uploaded.

Everything is unrun. No local tests or CI, candidate change, actual host operation, push, migration, Goal, writer or unrelated work. Original acceptance/old state unchanged. Author JSON is static provenance, not self-authentication; lead captures this final native reply/actual turn externally without inventing a turn ID. Production real-native/full R2 acceptance remains unproved.
