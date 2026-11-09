#!/usr/bin/env bash
# Exercises `forcitis-one-release pull` (remote_install.sh: cmd_pull up to fetch_release; placement, migration and activation are stubbed):
# a local stand-in for the ECS metadata service and OOS serves the request parameters, records the result parameter and checks the
# session-token flow and every request signature; release assets are built and signed here with keys generated for the run and served by
# a stand-in curl; alerts go to a stand-in one_alert_send. Only 127.0.0.1 is contacted. Runs on macOS and Linux without root.
#
#   scripts/release/install_request_selftest.sh
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
scratch="$(mktemp -d)"
server_pid=""
stop_server() { [ -z "$server_pid" ] || { kill "$server_pid" 2>/dev/null; wait "$server_pid" 2>/dev/null || true; }; }
trap 'stop_server; rm -rf "$scratch"' EXIT
COMMIT=0123456789abcdef0123456789abcdef01234567
WORKFLOW=FORCITIS/forcitis-one-psa/.github/workflows/release.yml@refs/heads/main

mkdir -p "$scratch/bin"
cat > "$scratch/bin/curl" <<'CURL'
#!/usr/bin/env bash
# Stand-in for curl. Local health and metrics: answers as configured, metrics only while current points at $FAKE_METRICS_FOR (or always
# when unset). Release assets: the link must arrive as a config on stdin, never as an argument; serves $FAKE_ASSETS/<last path segment>
for arg in "$@"; do
  case "$arg" in
    http://127.0.0.1:*/actuator/health) echo '{"status":"UP"}'; exit 0 ;;
    http://127.0.0.1:*/actuator/prometheus)
      echo '# TYPE jvm_threads_live_threads gauge'; echo 'jvm_threads_live_threads 20'
      current="$(basename "$(readlink "$FAKE_CURRENT" 2>/dev/null)")"
      if [ -z "${FAKE_METRICS_FOR:-}" ] || [ "$FAKE_METRICS_FOR" = "$current" ]; then echo 'forcitis_outbox_pending 0'; fi
      exit 0 ;;
  esac
done
[ "${1:-}" = -q ] || { echo "UNSAFE curl: -q is not the first argument" >&2; exit 90; }
out="" seen=" "
while [ "$#" -gt 0 ]; do
  case "$1" in
    -o) out="$2"; shift ;;
    -H|--header|-u|--user) echo "UNSAFE curl: credential option $1" >&2; exit 91 ;;
    --proto|--proto-redir|--max-redirs|-K) seen="$seen$1=$2 "; shift ;;
    *://*) echo "UNSAFE curl: URL on the command line" >&2; exit 92 ;;
    *) seen="$seen$1 " ;;
  esac
  shift
done
for want in "-g" "-L" "--proto==https" "--proto-redir==https" "--max-redirs=0" "-K=-"; do
  case "$seen" in *" $want "*) ;; *) echo "UNSAFE curl: missing $want" >&2; exit 93 ;; esac
done
config="$(cat)"
url="${config#url = \"}"; url="${url%\"}"
path="${url%%\?*}"
file="$FAKE_ASSETS/${path##*/}"
[ -f "$file" ] || exit 22
cp "$file" "$out"
CURL
if ! sha256sum --version 2>/dev/null | grep -q GNU; then
  printf '#!/bin/sh\nexec shasum -a 256 "$@"\n' > "$scratch/bin/sha256sum"
fi
if ! command -v flock >/dev/null 2>&1; then
  # flock(1) for macOS: locks the inherited descriptor, which keeps the lock after this process exits, as flock(1) does
  cat > "$scratch/bin/flock" <<'FLOCK'
#!/usr/bin/env bash
[ "$1" = -n ] || exit 64
exec python3 -I -c 'import fcntl, sys
try:
    fcntl.flock(int(sys.argv[1]), fcntl.LOCK_EX | fcntl.LOCK_NB)
except OSError:
    sys.exit(1)' "$2"
FLOCK
fi
cat > "$scratch/bin/systemctl" <<'SYSTEMCTL'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$FAKE_SYSTEMCTL_LOG"
case "$1" in
  is-active) exit 0 ;;
  is-failed) exit 1 ;;
  is-enabled) echo enabled ;;
esac
SYSTEMCTL
printf '#!/bin/sh\nexit 0\n' > "$scratch/bin/sleep"
cat > "$scratch/bin/one_alert_send" <<'ALERT'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$FAKE_ALERTS"
[ "${FAKE_ALERT_FAILS:-no}" = no ]
ALERT
command -v timeout >/dev/null 2>&1 || printf '#!/bin/sh\nshift\nexec "$@"\n' > "$scratch/bin/timeout"
chmod +x "$scratch/bin"/*

# Stand-in for the metadata service (hardened mode) and OOS Get/UpdateParameter; answers from the state file, checks token and signature,
# logs each OOS call and appends every written result
cat > "$scratch/server.py" <<'PY'
import base64, hashlib, hmac, json, os, sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qsl, quote, urlsplit
state_file, port_file = sys.argv[1], sys.argv[2]
def encode(value):
    return quote(value, safe="-_.~")
class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass
    def reply(self, status, body):
        data = body if isinstance(body, bytes) else json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)
    def do_PUT(self):
        state = json.load(open(state_file))
        if self.path != "/latest/api/token" or not self.headers.get("X-aliyun-ecs-metadata-token-ttl-seconds"):
            return self.reply(400, b"bad token request")
        if state["break"] == "token":
            return self.reply(500, b"")
        self.reply(200, state["token"].encode())
    def do_GET(self):
        state = json.load(open(state_file))
        parts = urlsplit(self.path)
        if parts.path.startswith("/latest/"):
            if self.headers.get("X-aliyun-ecs-metadata-token") != state["token"]:
                return self.reply(401, b"no session token")
            if parts.path == "/latest/meta-data/region-id":
                return self.reply(200, b"cn-hangzhou")
            if parts.path == "/latest/meta-data/ram/security-credentials/":
                return self.reply(200, b"role-a\nrole-b" if state["break"] == "two-roles" else b"test-role")
            if parts.path == "/latest/meta-data/ram/security-credentials/test-role":
                if state["break"] == "credentials-list":
                    return self.reply(200, b"[]")
                answer = dict(state["credentials"], Code="Failed" if state["break"] == "credentials" else "Success")
                return self.reply(200, answer)
            return self.reply(404, b"")
        if parts.path.rstrip("/") != "/oos-cn-hangzhou":
            return self.reply(404, b"")
        query = dict(parse_qsl(parts.query, keep_blank_values=True))
        signature = query.pop("Signature", "")
        canonical = "&".join(f"{encode(k)}={encode(v)}" for k, v in sorted(query.items()))
        expected = base64.b64encode(hmac.new((state["credentials"]["AccessKeySecret"] + "&").encode(),
                                             ("GET&%2F&" + encode(canonical)).encode(), hashlib.sha1).digest()).decode()
        if (signature != expected or query.get("AccessKeyId") != state["credentials"]["AccessKeyId"]
                or query.get("SecurityToken") != state["credentials"]["SecurityToken"]):
            return self.reply(400, {"Code": "SignatureDoesNotMatch", "RequestId": "r-1"})
        action, name = query.get("Action"), query.get("Name")
        with open(state["log"], "a") as log:
            log.write(f"{action} {name}\n")
        if action == "GetParameter":
            if name not in state["params"]:
                return self.reply(404, {"Code": "EntityNotExists.Parameter", "RequestId": "r-2"})
            if state["break"] == "oos-list":
                return self.reply(200, b"[1]")
            return self.reply(200, {"Parameter": {"Name": name, "Value": state["params"][name]}, "RequestId": "r-3"})
        if action == "UpdateParameter" and name == state["result_parameter"] and state["break"] != "result-write":
            with open(state["results"], "a") as results:
                results.write(query.get("Value", "") + "\n")
            return self.reply(200, {"Parameter": {"Name": name}, "RequestId": "r-4"})
        self.reply(403, {"Code": "Forbidden.RAM", "RequestId": "r-5"})
server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
with open(port_file + ".tmp", "w") as f:
    f.write(str(server.server_address[1]))
os.rename(port_file + ".tmp", port_file)
server.serve_forever()
PY

python3 -I - "$scratch/secrets.json" <<'PY'
import json, secrets, sys
json.dump({"token": secrets.token_urlsafe(24),
           "credentials": {"AccessKeyId": "STS." + secrets.token_hex(8), "AccessKeySecret": secrets.token_urlsafe(24),
                           "SecurityToken": secrets.token_urlsafe(48)}}, open(sys.argv[1], "w"))
PY
echo '{"token": "", "credentials": {}, "params": {}, "break": "", "log": "/dev/null", "results": "/dev/null", "result_parameter": ""}' \
  > "$scratch/state.json"
python3 -I "$scratch/server.py" "$scratch/state.json" "$scratch/port" &
server_pid=$!
for _ in $(seq 1 100); do [ -s "$scratch/port" ] && break; sleep 0.1; done
[ -s "$scratch/port" ] || { echo "stand-in server did not start" >&2; exit 1; }
port="$(cat "$scratch/port")"


ssh-keygen -q -t ed25519 -N '' -C install-request-test -f "$scratch/release_key"
ssh-keygen -q -t ed25519 -N '' -C install-request-test-other -f "$scratch/other_key"
printf 'forcitis-one-release namespaces="forcitis-one-release" %s\n' "$(cut -d' ' -f1,2 "$scratch/release_key.pub")" > "$scratch/signers"

# Builds the four release assets of <version> into <dir>, applying <tamper>; the statement is signed with <key>
build_release() {
  local dir="$1" version="$2" manifest_version="$3" statement_version="$4" key="$5" tamper="$6"
  python3 -I - "$dir" "$version" "$manifest_version" "$statement_version" "$tamper" "$COMMIT" "$WORKFLOW" <<'PY'
import hashlib, io, json, os, sys, tarfile
out, version, manifest_version, statement_version, tamper, commit, workflow = sys.argv[1:]
top = f"forcitis-one-{version}"
sha = lambda data: hashlib.sha256(data).hexdigest()
manifest = json.dumps({"version": manifest_version, "gitSha": "f" * 40 if tamper == "manifest-commit" else commit}).encode() + b"\n"
files = {"manifest.json": manifest, "web-dev/index.html": b"<!doctype html>\n", "deploy/remote_install.sh": b"echo installer\n"}
sums = "".join(f"{sha(data)}  {path}\n" for path, data in sorted(files.items())).encode()
files["SHA256SUMS"] = sums
if tamper == "extra-file":
    files["extra.txt"] = b"not listed\n"
buffer = io.BytesIO()
with tarfile.open(fileobj=buffer, mode="w:gz") as tar:
    def add(name, data=None, kind=tarfile.REGTYPE, target=""):
        info = tarfile.TarInfo(name)
        info.type, info.mode, info.mtime = kind, 0o755 if kind == tarfile.DIRTYPE else 0o644, 0
        if kind == tarfile.SYMTYPE:
            info.linkname = target
        if data is not None:
            info.size = len(data)
        tar.addfile(info, io.BytesIO(data) if data is not None else None)
    for directory in (top, f"{top}/web-dev", f"{top}/deploy"):
        add(directory, kind=tarfile.DIRTYPE)
    for path, data in sorted(files.items()):
        add(f"{top}/{path}", data)
    if tamper == "link":
        add(f"{top}/web-dev/passwd", kind=tarfile.SYMTYPE, target="/etc/passwd")
    if tamper == "dotdot":
        add(f"{top}/../escaped.txt", b"outside\n")
bundle = buffer.getvalue()
open(os.path.join(out, f"{top}.tar.gz"), "wb").write(bundle)
listed = "0" * 64 if tamper == "sha256-asset" else sha(bundle)
open(os.path.join(out, f"{top}.tar.gz.sha256"), "w").write(f"{listed}  {top}.tar.gz\n")
statement = {
    "format": "forcitis-one-release-statement/1",
    "version": statement_version,
    "commit": commit,
    "bundle": "forcitis-one-9.9.9.tar.gz" if tamper == "statement-bundle" else f"{top}.tar.gz",
    "bundle-sha256": "0" * 64 if tamper == "statement-bundle-sha" else sha(bundle),
    "manifest-sha256": sha(manifest),
    "sha256sums-sha256": sha(sums),
    "workflow": "FORCITIS/forcitis-one-psa/.github/workflows/release.yml@refs/heads/feature" if tamper == "statement-workflow" else workflow,
    "run": "77",
}
open(os.path.join(out, f"{top}.release"), "w").write("".join(f"{k}: {v}\n" for k, v in statement.items()))
PY
  ssh-keygen -q -Y sign -f "$key" -n forcitis-one-release "$dir/forcitis-one-$version.release" >/dev/null 2>&1
}

# Writes the stand-in state for <case dir>: the request parameters of <env> for 0.1.0, run 4242 attempt 1, with the named change applied
write_state() {
  python3 -I - "$scratch/state.json" "$scratch/secrets.json" "$1" "$2" "$3" "$4" <<'PY'
import hashlib, json, sys
path, secrets_path, env, change, broken, case_dir = sys.argv[1:]
state = json.load(open(secrets_path))
prefix = f"forcitis-one-deploy-request-{env}"
def links(version, host="release-assets.githubusercontent.com", scheme="https", userinfo="", signature="x"):
    top = f"forcitis-one-{version}"
    return {key: f"{scheme}://{userinfo}{host}/github-production-release-asset/1000/{top}{suffix}?sp=r&se=2026-10-15T00%3A05%3A00Z&sig={signature}"
            for key, suffix in (("bundle", ".tar.gz"), ("bundle-sha256", ".tar.gz.sha256"), ("statement", ".release"),
                                ("statement-sig", ".release.sig"))}
def sha(value):
    return hashlib.sha256(value.encode("utf-8")).hexdigest()
def complete(request, assets):
    return dict(request, request_id=f"{request['run_id']}:{request['run_attempt']}",
                asset_url_sha256={key: sha(value) for key, value in assets.items()})
request = {"version": "0.1.0", "environment": env, "run_id": 4242, "run_attempt": 1}
assets = links("0.1.0")
after = None
raw = None
if change == "objects-host":
    assets = links("0.1.0", host="objects.githubusercontent.com")
elif change == "rc-version":
    request["version"] = "0.1.0-rc.1"; assets = links("0.1.0-rc.1")
elif change == "placeholder":
    raw = "{}"
elif change == "extra-key":
    after = lambda r: dict(r, assets={})
elif change == "missing-key":
    after = lambda r: {k: v for k, v in r.items() if k != "run_attempt"}
elif change == "duplicate-key":
    after = lambda r: json.dumps(r)[:-1] + ', "run_id": 4243}'
elif change == "not-object":
    after = lambda r: json.dumps([r])
elif change == "not-json":
    raw = "version=0.1.0"
elif change == "non-ascii":
    after = lambda r: json.dumps(r)[:-1] + ', "note": "é"}'
elif change == "nan":
    after = lambda r: json.dumps(r)[:-1] + ', "x": NaN}'
elif change == "too-long":
    after = lambda r: json.dumps(r)[:-1] + ', "pad": "' + "a" * 4096 + '"}'
elif change == "environment":
    request["environment"] = "prod" if env == "dev" else "dev"
elif change == "environment-unknown":
    request["environment"] = "staging"
elif change.startswith("version="):
    request["version"] = change.split("=", 1)[1]
elif change == "run-id-zero":
    request["run_id"] = 0
elif change == "run-id-string":
    request["run_id"] = "4242"
elif change == "run-id-float":
    request["run_id"] = 4242.0
elif change == "run-attempt-bool":
    request["run_attempt"] = True
elif change == "request-id-other-attempt":
    after = lambda r: dict(r, request_id="4242:2")
elif change == "request-id-number":
    after = lambda r: dict(r, request_id=4242)
elif change == "hashes-missing":
    after = lambda r: {k: v for k, v in r.items() if k != "asset_url_sha256"}
elif change == "hash-key-missing":
    after = lambda r: dict(r, asset_url_sha256={k: v for k, v in r["asset_url_sha256"].items() if k != "statement-sig"})
elif change == "hash-key-extra":
    after = lambda r: dict(r, asset_url_sha256=dict(r["asset_url_sha256"], manifest="0" * 64))
elif change == "hash-upper-case":
    after = lambda r: dict(r, asset_url_sha256=dict(r["asset_url_sha256"], bundle=r["asset_url_sha256"]["bundle"].upper()))
elif change == "hash-short":
    after = lambda r: dict(r, asset_url_sha256=dict(r["asset_url_sha256"], statement=r["asset_url_sha256"]["statement"][:63]))
elif change == "hashes-not-object":
    after = lambda r: dict(r, asset_url_sha256=sorted(r["asset_url_sha256"].values()))
elif change == "new-request-old-link":
    # Run 4243 wrote its request, but the bundle parameter still holds the link written by run 4242
    request["run_id"] = 4243
    request = complete(request, assets)
    assets["bundle"] = links("0.1.0", signature="old")["bundle"]
elif change == "old-request-new-link":
    # The request of run 4242 meets a statement link that run 4243 has already written
    request = complete(request, assets)
    assets["statement"] = links("0.1.0", signature="new")["statement"]
elif change == "new-version-old-links":
    # Run 4243 deploys 0.2.0 and wrote its request; the four link parameters still hold the 0.1.0 links of run 4242
    request = complete({"version": "0.2.0", "environment": env, "run_id": 4243, "run_attempt": 1}, links("0.2.0"))
elif change.startswith("host="):
    assets["bundle"] = links("0.1.0", host=change.split("=", 1)[1])["bundle"]
elif change == "http":
    assets["statement"] = links("0.1.0", scheme="http")["statement"]
elif change == "userinfo":
    assets["statement-sig"] = links("0.1.0", userinfo="user:pass@")["statement-sig"]
elif change.startswith("url-append="):
    assets["bundle"] += change.split("=", 1)[1]
elif change == "missing-asset":
    del assets["bundle-sha256"]
elif change == "missing-request":
    request = None
elif change != "none":
    sys.exit(f"unknown change {change}")
params = {f"{prefix}-{key}": value for key, value in assets.items()}
if request is not None:
    if "request_id" not in request:
        request = complete(request, links("0.1.0") if change == "missing-asset" else assets)
    if after:
        request = after(request)
    params[prefix] = raw if raw is not None else request if isinstance(request, str) else json.dumps(request, separators=(",", ":"))
state.update(params=params, log=f"{case_dir}/oos.log", results=f"{case_dir}/results", result_parameter=f"forcitis-one-deploy-result-{env}",
             **{"break": broken})
json.dump(state, open(path, "w"))
PY
}

# Runs one `pull` (or the command in COMMAND) in a subshell (output in <case dir>/<out>); unpacking into releases, migration, the link
# switch itself and pruning are stubbed (systemd and GNU mv are not available here), the rest is the installer's, health checks included.
# TERM_AT=migration | health sends the run a TERM there.
attempt() {
  local case_dir="$1" server_env="$2" running="$3" out="$4"
  shift 4
  (
    # shellcheck disable=SC2030
    PATH="$scratch/bin:$PATH"
    # shellcheck disable=SC2030
    export FAKE_ASSETS="$case_dir/assets" FAKE_ALERTS="$case_dir/alerts" FAKE_CURRENT="$case_dir/current" \
      FAKE_SYSTEMCTL_LOG="$case_dir/systemctl"
    # shellcheck source=scripts/release/remote_install.sh
    source "$here/remote_install.sh"
    if stat -c %a / >/dev/null 2>&1; then
      owner_mode() { stat -c '%U %a' "$1" | sed "s/^$(id -un) /root /"; }
    else
      owner_mode() { stat -f '%Su %Lp' "$1" | sed "s/^$(id -un) /root /"; }
    fi
    assert_root_owned() { :; }
    place() { fetch_release "$1"; echo "ACCEPTED version=$version run=$request_id commit=$commit staged=$(basename "$staged")"; }
    new_migrations() { :; }
    # The subshell running the installer is the parent of this sh (bash 3.2 has no BASHPID)
    migrate() { [ "${TERM_AT:-}" != migration ] || sh -c 'kill -TERM $PPID'; }
    switch_to() { echo "$1" >> "$case_dir/switches"; ln -sfn "releases/$1" "$current_link"; }
    if [ "${TERM_AT:-}" = health ]; then
      eval "real_$(declare -f health_ok)"
      health_ok() { if [ ! -e "$case_dir/termed" ]; then : > "$case_dir/termed"; sh -c 'kill -TERM $PPID'; fi; real_health_ok; }
    fi
    prune() { :; }
    HEALTH_TIMEOUT_SECONDS=1
    METADATA_URL="http://127.0.0.1:$port"
    OOS_ENDPOINT="http://127.0.0.1:$port/oos-{region}"
    ALERT_SENDER="$scratch/bin/one_alert_send"
    ETC="$case_dir/etc"
    RELEASE_SOURCE="$ETC/release-source"
    RELEASE_SIGNERS="$ETC/release-signers"
    STATE_DIR="$case_dir/state"
    [ -z "${STATE_BLOCKED:-}" ] || STATE_DIR="$case_dir/read-only/state"
    PROCESSED="$STATE_DIR/processed-requests"
    ALERTS="$STATE_DIR/alerts-sent"
    ACTIVATION="$STATE_DIR/activation"
    RUN_DIR="$case_dir/run"
    STATE_ALERT_MARK="$RUN_DIR/state-alert"
    LOCK="$RUN_DIR/lock"
    SELF="$here/remote_install.sh"
    BASE="$case_dir"
    releases="$case_dir/releases"
    current_link="$case_dir/current"
    previous_link="$case_dir/previous"
    if [ -n "${COMMAND:-}" ]; then "$COMMAND" "$@"; else cmd_pull "$@"; fi
  ) > "$case_dir/$out" 2>&1
}

# Sets up <case dir> for one case: configuration, assets, request parameters, state
prepare_case() {
  local dir="$1" server_env="$2" running="$3" processed_before="$4"
  mkdir -p "$dir/assets" "$dir/etc" "$dir/releases"
  printf 'ENVIRONMENT=%s\n' "$server_env" > "$dir/etc/release-source" && chmod 0644 "$dir/etc/release-source"
  printf "SPRING_PROFILES_ACTIVE='%s'\n" "$server_env" > "$dir/etc/one.env"
  cp "$scratch/signers" "$dir/etc/release-signers" && chmod 0644 "$dir/etc/release-signers"
  if [ -n "$running" ]; then
    mkdir -p "$dir/releases/$running" && ln -s "releases/$running" "$dir/current"
  fi
  [ -z "${PREVIOUS:-}" ] || { mkdir -p "$dir/releases/$PREVIOUS" && ln -s "releases/$PREVIOUS" "$dir/previous"; }
  case "$processed_before" in
    yes) mkdir -m 0700 "$dir/state" && (umask 077 && printf '4242:1\t2026-10-14T00:00:00Z\tsucceeded\tv0.1.0 x\n' > "$dir/state/processed-requests") ;;
    failed) mkdir -m 0700 "$dir/state" && (umask 077 && printf '4242:1\t2026-10-14T00:00:00Z\tfailed\tdownload: x\n' > "$dir/state/processed-requests") ;;
    unwritable) mkdir -m 0700 "$dir/state" && mkdir -m 0600 "$dir/state/processed-requests" ;;
    earlier) mkdir -m 0700 "$dir/state" && (umask 077 && printf '4100:1\t2026-10-14T00:00:00Z\tsucceeded\tv0.0.9 x\n' > "$dir/state/processed-requests") ;;
    interrupted) mkdir -m 0700 "$dir/state" && (umask 077 && printf '4100:1\t2026-10-14T00:00:00Z\tsucceeded\tv0.0.9 x\n4242:1\t2026-10-15T00:00:00Z\tstarted\tv0.1.0\n' > "$dir/state/processed-requests") \
      && (umask 077 && printf '0.0.9 0.1.0 4242:1\n' > "$dir/state/activation") && ln -sfn releases/0.1.0 "$dir/current" ;;
    alerts-unwritable) mkdir -m 0700 "$dir/state" && mkdir -m 0600 "$dir/state/alerts-sent" ;;
    interrupted-first) mkdir -m 0700 "$dir/state" && (umask 077 && printf '4242:1\t2026-10-15T00:00:00Z\tstarted\tv0.1.0\n' > "$dir/state/processed-requests") \
      && (umask 077 && printf -- '- 0.1.0 4242:1\n' > "$dir/state/activation") && ln -sfn releases/0.1.0 "$dir/current" ;;
    activation-unwritable) mkdir -m 0700 "$dir/state" && mkdir "$dir/state/activation" ;;
    marker-succeeded) mkdir -m 0700 "$dir/state" && (umask 077 && printf '4242:1\t2026-10-15T00:00:00Z\tstarted\tv0.1.0\n4242:1\t2026-10-15T00:01:00Z\tsucceeded\tv0.1.0 x\n' > "$dir/state/processed-requests") \
      && (umask 077 && printf '0.0.9 0.1.0 4242:1\n' > "$dir/state/activation") && ln -sfn releases/0.1.0 "$dir/current" ;;
    reinstall-interrupted) mkdir -m 0700 "$dir/state" && (umask 077 && printf '4242:1\t2026-10-15T00:00:00Z\tstarted\tv0.1.0\n' > "$dir/state/processed-requests") \
      && (umask 077 && printf '0.1.0 0.1.0 4242:1\n' > "$dir/state/activation") ;;
    alerted) mkdir -m 0700 "$dir/state" && (umask 077 && printf '4242:1 activation\t%s\n' "$(date +%s)" > "$dir/state/alerts-sent") ;;
  esac
  [ -z "${STALE_INCOMING:-}" ] || mkdir -p "$dir/releases/.incoming.Ab12Cd"
  [ -z "${STATE_BLOCKED:-}" ] || { mkdir -p "$dir/read-only" && chmod 0500 "$dir/read-only"; }
}

test_flock() { if [ -x "$scratch/bin/flock" ]; then "$scratch/bin/flock" "$@"; else flock "$@"; fi; }

passed=0 failed=0
pass() { passed=$((passed + 1)); echo "PASS  $1"; }
flunk() { failed=$((failed + 1)); echo "FAIL  $1"; [ -z "${2:-}" ] || sed 's/^/      /' "$2"; }

# Nothing secret, signed or link-like may reach the log, the alerts or the result parameter
leaks() {
  python3 -I - "$scratch/secrets.json" "$1" <<'PY'
import glob, json, os, sys
s = json.load(open(sys.argv[1]))
text = "".join(open(f).read() for name in ("out", "out2", "alerts", "results") for f in glob.glob(os.path.join(sys.argv[2], name)))
found = [k for k, v in (("metadata token", s["token"]), ("access key secret", s["credentials"]["AccessKeySecret"]),
                        ("security token", s["credentials"]["SecurityToken"]), ("signed link", "sig="),
                        ("link host", "githubusercontent.com/")) if v in text]
print(", ".join(found))
PY
}

# check <name> <expect: accept | reject:text | quiet | ok (exit 0 with output)> [pull arguments...]; set before the name through variables:
# REQUEST (change, default none), BREAK (stand-in failure), SERVER_ENV (dev), RUNNING, PROCESSED_BEFORE (no | yes | failed | unwritable |
# earlier), RELEASE ("version manifest-version statement-version key tamper"), ALSO_RELEASE (a second clean version), TWICE (run pull again)
check() {
  local name="$1" expect="$2" dir status rc shown leak want
  shift 2
  dir="$scratch/$name"
  prepare_case "$dir" "${SERVER_ENV:-dev}" "${RUNNING:-}" "${PROCESSED_BEFORE:-no}"
  # shellcheck disable=SC2086
  build_release "$dir/assets" ${RELEASE:-0.1.0 0.1.0 0.1.0 $scratch/release_key none}
  [ -z "${ALSO_RELEASE:-}" ] || build_release "$dir/assets" "$ALSO_RELEASE" "$ALSO_RELEASE" "$ALSO_RELEASE" "$scratch/release_key" none
  write_state "${SERVER_ENV:-dev}" "${REQUEST:-none}" "${BREAK:-}" "$dir"
  [ ! -f "$dir/state/processed-requests" ] || cp "$dir/state/processed-requests" "$dir/processed-before"
  # Not in an if: errexit, which the sourced installer sets, must stay in force inside the attempt
  # Another pull holds the lock for the whole attempt
  [ -z "${LOCK_HELD:-}" ] || { mkdir -p "$dir/run"; exec 8>"$dir/run/lock"; test_flock -n 8; }
  set +e
  attempt "$dir" "${SERVER_ENV:-dev}" "${RUNNING:-}" out "$@"
  rc=$?
  [ -z "${LOCK_HELD:-}" ] || exec 8>&-
  [ -z "${TWICE:-}" ] || { attempt "$dir" "${SERVER_ENV:-dev}" "${RUNNING:-}" out2 "$@"; echo $? > "$dir/rc2"; }
  set -e
  if [ "$rc" -eq 0 ] && grep -q ACCEPTED "$dir/out"; then status=accepted
  elif [ "$rc" -eq 0 ] && [ ! -s "$dir/out" ]; then status=quiet
  elif [ "$rc" -eq 0 ]; then status="exit 0 with output"
  else status=rejected; fi
  grep -q 'UNSAFE curl' "$dir/out" && status="unsafe download"
  leak="$(leaks "$dir")"
  [ -z "$leak" ] || status="leaked $leak"
  case "$expect" in accept) want=accepted ;; quiet) want=quiet ;; ok) want="exit 0 with output" ;; *) want=rejected ;; esac
  if [ "$status" = "$want" ] && { [ "$want" != rejected ] || grep -qF -- "${expect#reject:}" "$dir/out"; }; then
    shown="ACCEPTED"; [ "${expect%%:*}" != reject ] || shown="${expect#reject:}"; [ "$expect" != ok ] || shown="nothing to do"
    pass "$name: $status — $(grep -m1 -F -- "$shown" "$dir/out" | cut -c1-170)"
  else
    flunk "$name: $status, expected $expect" "$dir/out"
  fi
}

# Effects beyond the outcome, checked after a case ran
expect() {
  local name="$1" what="$2" condition="$3"
  if ("$condition" "$scratch/$name") >/dev/null 2>&1; then pass "$name: $what"; else flunk "$name: $what"; fi
}
alerts_count() { if [ -f "$1/alerts" ]; then wc -l < "$1/alerts" | tr -d ' '; else echo 0; fi; }
no_alert() { [ "$(alerts_count "$1")" = 0 ]; }
one_alert() { [ "$(alerts_count "$1")" = 1 ]; }
second_run_quiet() { [ "$(cat "$1/rc2")" = 0 ] && [ ! -s "$1/out2" ]; }
replay_unchanged() { if [ -f "$1/processed-before" ]; then cmp -s "$1/processed-before" "$1/state/processed-requests"; else [ ! -s "$1/state/processed-requests" ]; fi; }
only_request_read() { [ -s "$1/oos.log" ] && ! grep -vqx "GetParameter forcitis-one-deploy-request-dev" "$1/oos.log"; }
no_oos_call() { [ ! -e "$1/oos.log" ]; }
no_result() { [ ! -e "$1/results" ]; }
nothing_fetched() { ! grep -q 'fetching' "$1/out"; }
result_is() {
  python3 -I - "$1/results" "$2" <<'PY'
import json, sys
rows = [json.loads(line) for line in open(sys.argv[1])]
want = [tuple(item.split(",")) for item in sys.argv[2].split()]
got = [(r["request_id"], r["status"], r["reason"] or "-") for r in rows]
ok = got == want and all(set(r) == {"request_id", "status", "reason", "time"} and len(r["time"]) == 20 for r in rows)
sys.exit(0 if ok else 1)
PY
}
results_started_succeeded() { result_is "$1" "4242:1,started,- 4242:1,succeeded,-"; }
result_link_mismatch_4243() { result_is "$1" "4243:1,retrying,link-mismatch"; }
result_link_mismatch_4242() { result_is "$1" "4242:1,retrying,link-mismatch"; }
alert_names_mismatch() { grep -q '^P2 one.release-pull dev: request 42[0-9][0-9]:1 link-mismatch: link parameters forcitis-one-deploy-request-dev-' "$1/alerts"; }
alert_global_token() { grep -q '^P2 one.release-pull dev: read-parameters: cannot read the request parameter' "$1/alerts"; }
alert_result_write() { grep -q '^P2 one.release-pull dev: request 4242:1 result-write:' "$1/alerts"; }
alert_delivery_logged() { grep -q 'alert delivery through' "$1/out" && one_alert "$1"; }

check valid-request accept
expect valid-request "result parameter: started, then succeeded, for 4242:1" results_started_succeeded
expect valid-request "no alert for an accepted request" no_alert
REQUEST=objects-host check valid-objects-host accept
REQUEST=rc-version RELEASE="0.1.0-rc.1 0.1.0-rc.1 0.1.0-rc.1 $scratch/release_key none" check valid-rc-on-dev accept
REQUEST=rc-version RELEASE="0.1.0-rc.1 0.1.0-rc.1 0.1.0-rc.1 $scratch/release_key none" SERVER_ENV=prod \
  check prod-refuses-rc "reject:prod takes released versions only"
check pull-refuses-arguments "reject:pull takes no arguments" --request e30=

PROCESSED_BEFORE=yes TWICE=yes check processed-succeeded-skipped quiet
expect processed-succeeded-skipped "only the request parameter was read; no result, no alert" only_request_read
expect processed-succeeded-skipped "replay record unchanged" replay_unchanged
PROCESSED_BEFORE=failed check processed-failed-skipped quiet
expect processed-failed-skipped "a request that failed before is not retried and not reported again" no_alert
REQUEST=placeholder TWICE=yes check placeholder-skipped quiet
expect placeholder-skipped "no result for the placeholder" no_result
LOCK_HELD=yes check concurrent-pull-exits quiet
expect concurrent-pull-exits "the second instance made no OOS call" no_oos_call

REQUEST=new-request-old-link PROCESSED_BEFORE=earlier TWICE=yes check mixed-new-request-old-link \
  "reject:link parameters forcitis-one-deploy-request-dev-bundle do not match the request's asset_url_sha256"
expect mixed-new-request-old-link "one P2 alert naming request and parameter" alert_names_mismatch
expect mixed-new-request-old-link "the same cause is alerted once; the next run is quiet" second_run_quiet
expect mixed-new-request-old-link "alerts stay at one" one_alert
expect mixed-new-request-old-link "replay record unchanged" replay_unchanged
expect mixed-new-request-old-link "result parameter: retrying (not an end state), link-mismatch, for 4243:1" result_link_mismatch_4243
REQUEST=old-request-new-link PROCESSED_BEFORE=earlier check mixed-old-request-new-link \
  "reject:link parameters forcitis-one-deploy-request-dev-statement do not match"
expect mixed-old-request-new-link "one P2 alert naming request and parameter" alert_names_mismatch
expect mixed-old-request-new-link "replay record unchanged" replay_unchanged
expect mixed-old-request-new-link "result parameter: retrying (not an end state), link-mismatch, for 4242:1" result_link_mismatch_4242
REQUEST=new-version-old-links ALSO_RELEASE=0.2.0 PROCESSED_BEFORE=earlier check mixed-new-version-old-assets \
  "reject:link parameters forcitis-one-deploy-request-dev-bundle forcitis-one-deploy-request-dev-bundle-sha256 forcitis-one-deploy-request-dev-statement forcitis-one-deploy-request-dev-statement-sig do not match"
expect mixed-new-version-old-assets "nothing fetched: the 0.1.0 assets beside the 0.2.0 request are never downloaded" nothing_fetched
expect mixed-new-version-old-assets "replay record unchanged" replay_unchanged
REQUEST=new-request-old-link FAKE_ALERT_FAILS=yes check mixed-alert-undeliverable "reject:link parameters"
expect mixed-alert-undeliverable "an undeliverable alert is logged; the request is still refused" alert_delivery_logged

BREAK=token TWICE=yes check metadata-token-unavailable "reject:metadata service gave no session token (HTTP 500)"
expect metadata-token-unavailable "one P2 alert without a request" alert_global_token
expect metadata-token-unavailable "a failure without a request is alerted at most hourly; the next run is quiet" second_run_quiet
BREAK=two-roles check instance-two-roles "reject:this instance must have exactly one RAM role, found 2"
BREAK=credentials check role-credentials-failed "reject:role credentials unavailable (Code 'Failed')"
BREAK=credentials-list check role-credentials-not-object "reject:role credentials is not a JSON object"
BREAK=oos-list check oos-answer-not-object "reject:is not a JSON object"
REQUEST=missing-request check request-parameter-missing "reject:OOS GetParameter forcitis-one-deploy-request-dev: HTTP 404 EntityNotExists.Parameter"
REQUEST=missing-asset check asset-parameter-missing "reject:OOS GetParameter forcitis-one-deploy-request-dev-bundle-sha256: HTTP 404"
BREAK=result-write check result-write-refused accept
expect result-write-refused "a refused result write is alerted and changes nothing else" alert_result_write

REQUEST=not-json TWICE=yes check request-not-json "reject:install request: is not JSON"
expect request-not-json "an invalid request is alerted once; the next run is quiet" second_run_quiet
expect request-not-json "alerts stay at one" one_alert
REQUEST=non-ascii check request-non-ascii         "reject:is not 1..4096 printable ASCII characters"
REQUEST=too-long check request-too-long           "reject:is not 1..4096 printable ASCII characters"
REQUEST=nan check request-nan                     "reject:has the non-JSON constant NaN"
REQUEST=not-object check request-not-object       "reject:must be an object with exactly the keys"
REQUEST=extra-key check request-extra-key         "reject:must be an object with exactly the keys"
REQUEST=missing-key check request-missing-key     "reject:must be an object with exactly the keys"
REQUEST=duplicate-key check request-duplicate-key "reject:has a duplicate key"
REQUEST=environment check environment-mismatch    "reject:is for environment prod, this server is dev"
REQUEST=environment-unknown check environment-unknown "reject:environment 'staging' is not dev or prod"
REQUEST=version=0.1 check version-two-parts       "reject:version '0.1' is not MAJOR.MINOR.PATCH"
REQUEST=version=v0.1.0 check version-with-v       "reject:version 'v0.1.0' is not MAJOR.MINOR.PATCH"
REQUEST=version=../0.1.0 check version-path       "reject:is not MAJOR.MINOR.PATCH"
REQUEST=run-id-zero check run-id-zero             "reject:run_id must be a positive integer"
REQUEST=run-id-string check run-id-string         "reject:run_id must be a positive integer"
REQUEST=run-id-float check run-id-float           "reject:run_id must be a positive integer"
REQUEST=run-attempt-bool check run-attempt-bool   "reject:run_attempt must be a positive integer"
REQUEST=request-id-other-attempt check request-id-differs "reject:request_id must be the string '4242:1' (run_id:run_attempt)"
REQUEST=request-id-number check request-id-not-string "reject:request_id must be the string '4242:1'"
REQUEST=hashes-missing check hashes-missing "reject:must be an object with exactly the keys"
REQUEST=hash-key-missing check hash-key-missing "reject:asset_url_sha256 must have exactly the keys"
REQUEST=hash-key-extra check hash-key-extra "reject:asset_url_sha256 must have exactly the keys"
REQUEST=hashes-not-object check hashes-not-object "reject:asset_url_sha256 must have exactly the keys"
REQUEST=hash-upper-case check hash-upper-case "reject:asset_url_sha256.bundle is not 64 lowercase hexadecimal characters"
REQUEST=hash-short check hash-short "reject:asset_url_sha256.statement is not 64 lowercase hexadecimal characters"

REQUEST=host=evil.example TWICE=yes check host-evil "reject:host 'evil.example'"
invalid_link_final() {
  result_is "$1" "4242:1,failed,invalid-link" && grep -q $'^4242:1\t.*\tfailed\tinvalid-link: ' "$1/state/processed-requests" && second_run_quiet "$1"
}
expect host-evil "a link that is not acceptable ends failed (not retrying) and is not taken up again" invalid_link_final
REQUEST=host=release-assets.githubusercontent.com.evil.com check host-suffix-trick "reject:host 'release-assets.githubusercontent.com.evil.com'"
REQUEST=host=githubusercontent.com check host-parent-domain "reject:host 'githubusercontent.com'"
REQUEST=host=objects.githubusercontent.com. check host-trailing-dot "reject:host 'objects.githubusercontent.com.'"
REQUEST=host=Release-Assets.githubusercontent.com check host-upper-case "reject:host 'Release-Assets.githubusercontent.com'"
REQUEST=host=release-assets.githubusercontent.com:443 check host-port-443 "reject:host 'release-assets.githubusercontent.com:443'"
REQUEST=host=release-assets.githubusercontent.com:8443 check host-port-8443 "reject:host 'release-assets.githubusercontent.com:8443'"
REQUEST=http check http-scheme "reject:got scheme 'http'"
REQUEST=userinfo check userinfo "reject:host 'user:pass@release-assets.githubusercontent.com'"
REQUEST="url-append= x" check url-with-space "reject:URL holds whitespace or one of the characters"
REQUEST="url-append=&f={a}" check url-with-braces "reject:URL holds whitespace or one of the characters"
REQUEST="url-append=&f=[1-9]" check url-with-brackets "reject:URL holds whitespace or one of the characters"
REQUEST='url-append="' check url-with-quote "reject:URL holds whitespace or one of the characters"
REQUEST="url-append=#frag" check url-with-fragment "reject:carries a fragment"

RELEASE="0.1.0 0.1.0 0.2.0 $scratch/release_key none" check statement-version-differs "reject:signed release statement is for version '0.2.0', the request names 0.1.0"
RELEASE="0.1.0 0.2.0 0.1.0 $scratch/release_key none" check manifest-version-differs "reject:manifest.json is not for the requested version 0.1.0"
RELEASE="0.1.0 0.1.0 0.1.0 $scratch/other_key none" check statement-other-signer "reject:signature does not verify"
RELEASE="0.1.0 0.1.0 0.1.0 $scratch/release_key sha256-asset" check bundle-differs-from-sha256-asset "reject:bundle sha256 differs from the release's .sha256 asset"
RELEASE="0.1.0 0.1.0 0.1.0 $scratch/release_key statement-workflow" check statement-other-workflow "reject:not produced by the release workflow on main"
RELEASE="0.1.0 0.1.0 0.1.0 $scratch/release_key statement-bundle" check statement-other-bundle-name "reject:release statement names another bundle"
RELEASE="0.1.0 0.1.0 0.1.0 $scratch/release_key statement-bundle-sha" check statement-other-bundle-sha256 "reject:bundle differs from the signed release statement"
RELEASE="0.1.0 0.1.0 0.1.0 $scratch/release_key link" check archive-with-link "reject:bundle contains links or special files"
RELEASE="0.1.0 0.1.0 0.1.0 $scratch/release_key dotdot" check archive-with-dotdot "reject:bundle entry with ..: forcitis-one-0.1.0/../escaped.txt"
RELEASE="0.1.0 0.1.0 0.1.0 $scratch/release_key extra-file" check archive-file-not-in-sums "reject:bundle has files not covered by SHA256SUMS"
RELEASE="0.1.0 0.1.0 0.1.0 $scratch/release_key manifest-commit" check manifest-other-commit "reject:manifest.json is not for commit $COMMIT named by the signed statement"
result_verification_failed() { result_is "$1" "4242:1,started,- 4242:1,failed,verification"; }
expect manifest-other-commit "result parameter: started, then failed with cause verification" result_verification_failed

RUNNING=0.2.0 TWICE=yes check downgrade "reject:0.1.0 is lower than the running 0.2.0"
failure_recorded() { grep -q $'^4242:1\t.*\tfailed\tversion-rejected: ' "$1/state/processed-requests"; }
expect downgrade "the refused downgrade is recorded as failed (version-rejected)" failure_recorded
expect downgrade "a recorded request is skipped quietly on the next run" second_run_quiet
RUNNING=0.1.0-rc.1 check upgrade-from-rc accept
RUNNING=current-build check running-name-malformed "reject:cannot compare versions '0.1.0' and 'current-build'"
PROCESSED_BEFORE=unwritable check started-not-recordable "reject:could not record request 4242:1 as started"
expect started-not-recordable "nothing fetched without a start record" nothing_fetched
started_recorded() { grep -q $'^4242:1\t.*\tstarted\tv0.1.0$' "$1/state/processed-requests"; }
expect valid-request "processed-requests holds 4242:1 started" started_recorded


# Health after the switch: health UP, metrics with forcitis_ names, pinned files
switched_once() { [ "$(cat "$1/switches")" = "0.1.0" ]; }
expect valid-request "switched once to 0.1.0 after a healthy check with the application's metrics" switched_once
RUNNING=0.0.9 FAKE_METRICS_FOR=0.0.9 check metrics-missing "reject:release 0.1.0 was unhealthy; 0.0.9 is running again"
switched_back() { [ "$(tr '\n' ' ' < "$1/switches")" = "0.1.0 0.0.9 " ] && [ "$(readlink "$1/current")" = releases/0.0.9 ]; }
result_activation_failed() { result_is "$1" "4242:1,started,- 4242:1,failed,activation"; }
metrics_not_printed() { ! grep -q 'jvm_threads\|forcitis_outbox' "$1/out"; }
expect metrics-missing "without forcitis_ metrics the switch is undone" switched_back
expect metrics-missing "result: started, then failed (activation)" result_activation_failed
expect metrics-missing "the metrics text is never printed" metrics_not_printed
marker_removed() { [ ! -e "$1/state/activation" ]; }
expect valid-request "the switch marker is removed after succeeded is recorded" marker_removed
expect metrics-missing "the switch marker is removed after switching back" marker_removed
RUNNING=0.1.0 check same-version-healthy ok
same_version_untouched() {
  [ ! -e "$1/switches" ] && ! grep -q 'fetching' "$1/out" && ! grep -q '^restart' "$1/systemctl" \
    && result_is "$1" "4242:1,started,- 4242:1,succeeded,-" && grep -q $'^4242:1\t.*\tsucceeded\tv0.1.0 already current$' "$1/state/processed-requests"
}
expect same-version-healthy "already current and healthy: succeeded without download, migration, switch or restart" same_version_untouched

# Stopped by a signal
TERM_AT=migration check term-during-migration "reject:stopped by a signal during migration"
result_terminated() { result_is "$1" "4242:1,started,- 4242:1,failed,terminated" && grep -q $'^4242:1\t.*\tfailed\tterminated: ' "$1/state/processed-requests"; }
alert_terminated() { grep -q '^P2 one.release-pull dev: request 4242:1 terminated: stopped by a signal during migration' "$1/alerts"; }
expect term-during-migration "recorded failed (terminated), result failed, one alert" result_terminated
expect term-during-migration "alert names the request and the interrupted step" alert_terminated
orphan_migrations_stopped() { grep -qx "stop forcitis-one-migrate-\*" "$1/systemctl"; }
expect term-during-migration "leftover migration units are stopped" orphan_migrations_stopped
RUNNING=0.0.9 TERM_AT=health check term-after-switch "reject:stopped by a signal during activation"
activation_cleared() { switched_back "$1" && [ ! -e "$1/state/activation" ]; }
expect term-after-switch "a switch not yet confirmed is undone, the marker removed" activation_cleared
expect term-after-switch "recorded failed (terminated), result failed" result_terminated

# ExecStopPost after a run that could not handle the signal (SIGKILL)
PROCESSED_BEFORE=interrupted RUNNING=0.0.9 COMMAND=cmd_finish_interrupted check finish-interrupted ok
finished_interrupted() {
  [ "$(cat "$1/switches")" = "0.0.9" ] && [ ! -e "$1/state/activation" ] && result_is "$1" "4242:1,failed,terminated" \
    && grep -q $'^4242:1\t.*\tfailed\tterminated: ' "$1/state/processed-requests" \
    && grep -q '^P2 one.release-pull dev: request 4242:1 terminated: ' "$1/alerts"
}
expect finish-interrupted "switched back to 0.0.9, request 4242:1 ended failed (terminated) with result and alert" finished_interrupted
expect finish-interrupted "leftover migration units are stopped" orphan_migrations_stopped

# An operator's rollback interrupted half-way: its own trap leaves the marker, and the pull unit's ExecStopPost does not undo it
RUNNING=0.1.0 TERM_AT=health COMMAND=cmd_rollback check rollback-interrupted "reject:rollback stopped by a signal before its health check finished" 0.0.9
set +e
COMMAND=cmd_finish_interrupted attempt "$scratch/rollback-interrupted" dev 0.1.0 out-finish
set -e
rollback_left_alone() {
  [ "$(cat "$1/switches")" = "0.0.9" ] && [ "$(readlink "$1/current")" = releases/0.0.9 ] && [ "$(cat "$1/state/activation")" = "0.1.0 0.0.9 -" ] \
    && grep -q 'unconfirmed-switch-marker: found an unconfirmed switch marker (0.1.0 -> 0.0.9, request -) and left it as it is' "$1/alerts" \
    && ! grep -q 'switching back' "$1/out-finish"
}
expect rollback-interrupted "finish-interrupted keeps the rollback: no switch back, marker kept, one alert" rollback_left_alone

PROCESSED_BEFORE=interrupted-first COMMAND=cmd_finish_interrupted check first-install-interrupted ok
set +e
COMMAND=cmd_status attempt "$scratch/first-install-interrupted" dev "" out-status
set -e
first_install_reported() {
  [ ! -e "$1/switches" ] && [ -e "$1/state/activation" ] && result_is "$1" "4242:1,failed,terminated" \
    && grep -q 'first installation: the switch to 0.1.0 was not confirmed and the service is still running' "$1/alerts" \
    && grep -q '^UNCONFIRMED SWITCH (before target request; - = none / manual rollback): - 0.1.0 4242:1$' "$1/out-status"
}
expect first-install-interrupted "alert says the first installation is unconfirmed and running; status shows the marker" first_install_reported

# A marker whose request already ended succeeded belongs to no interrupted pull: not undone, one alert
PROCESSED_BEFORE=marker-succeeded RUNNING=0.0.9 COMMAND=cmd_finish_interrupted TWICE=yes check marker-of-finished-request ok
marker_of_finished_left() {
  [ ! -e "$1/switches" ] && [ "$(cat "$1/state/activation")" = "0.0.9 0.1.0 4242:1" ] && [ ! -e "$1/results" ] && one_alert "$1" \
    && grep -q 'unconfirmed-switch-marker: found an unconfirmed switch marker (0.0.9 -> 0.1.0, request 4242:1) and left it as it is' "$1/alerts"
}
expect marker-of-finished-request "no switch back, marker kept, one alert across two runs, no result" marker_of_finished_left

# Reinstalling the running version interrupted: nothing to switch back to; said so, and the marker is cleared (no repeated alerts)
PROCESSED_BEFORE=reinstall-interrupted RUNNING=0.1.0 COMMAND=cmd_finish_interrupted check reinstall-interrupted ok
reinstall_reported() {
  [ ! -e "$1/switches" ] && [ ! -e "$1/state/activation" ] && result_is "$1" "4242:1,failed,terminated" \
    && grep -q 'reinstalling the running release 0.1.0 was not confirmed; it is still current and running' "$1/alerts" \
    && ! grep -q 'first installation' "$1/alerts"
}
expect reinstall-interrupted "same-version reinstall: its own wording, no switch, marker removed, request failed" reinstall_reported

PROCESSED_BEFORE=activation-unwritable check activation-unwritable "reject:could not write"
not_switched() { [ ! -e "$1/switches" ] && result_is "$1" "4242:1,started,- 4242:1,failed,activation"; }
expect activation-unwritable "no switch without the marker; the request ends failed" not_switched

RUNNING=0.0.9 FAKE_METRICS_FOR=0.0.9 PROCESSED_BEFORE=alerted check alert-limited-still-recorded "reject:release 0.1.0 was unhealthy"
recorded_without_alert() {
  [ ! -e "$1/alerts" ] && result_activation_failed "$1" && grep -q $'^4242:1\t.*\tfailed\tactivation: ' "$1/state/processed-requests"
}
expect alert-limited-still-recorded "an alert already sent is not repeated, but failed and the result are still written" recorded_without_alert

RUNNING=0.1.0 PREVIOUS=0.0.9 FAKE_METRICS_FOR=0.0.9 check same-version-unhealthy "reject:release 0.1.0 was unhealthy; 0.0.9 is running again"
back_to_previous() { [ "$(tr '\n' ' ' < "$1/switches")" = "0.1.0 0.0.9 " ] && ! grep -qx 'stop forcitis-one' "$1/systemctl"; }
expect same-version-unhealthy "same version unhealthy: back to previous, the service is never stopped" back_to_previous
RUNNING=0.1.0 FAKE_METRICS_FOR=none check same-version-unhealthy-alone "reject:there is no previous release; the service is left running"
never_stopped() { ! grep -qx 'stop forcitis-one' "$1/systemctl"; }
expect same-version-unhealthy-alone "no previous release: error only, the service is not stopped" never_stopped

# Both migration paths run under systemd-run with the same run time limit (stand-ins for systemd-run, stat and install)
mig="$scratch/migration"
mkdir -p "$mig/bin" "$mig/etc/migrate" "$mig/releases/0.1.0" "$mig/logs"
cat > "$mig/bin/systemd-run" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$MIG_LOG"
for arg in "$@"; do
  case "$arg" in StandardOutput=append:*) echo "Started OneApplication in 9.9 seconds" >> "${arg#StandardOutput=append:}" ;; esac
done
EOF
cat > "$mig/bin/stat" <<'EOF'
#!/usr/bin/env bash
if [ "$1" = -c ]; then
  format="${2//%U/%Su}"; format="${format//%a/%Lp}"
  /usr/bin/stat -f "$format" "$3" | sed "s/^$(id -un)\([ :]\)/root\1/"
else
  exec /usr/bin/stat "$@"
fi
EOF
cat > "$mig/bin/install" <<'EOF'
#!/usr/bin/env bash
# install -d [-o -g -m ...] <dir>: only the directory matters here
exec mkdir -p "${!#}"
EOF
chmod +x "$mig/bin"/*
for property in spring.flyway.user spring.flyway.password; do (umask 077 && echo "value-$property" > "$mig/etc/migrate/$property"); done
chmod 0700 "$mig/etc/migrate"
: > "$mig/etc/one.env"
run_migration() {
  python3 -I - "$mig/releases/0.1.0/forcitis-one.jar" "$1" <<'PY'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1], "w") as jar:
    jar.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n")
    if sys.argv[2] == "migrate-mode":
        jar.writestr("BOOT-INF/classes/application-migrate.properties", "spring.main.web-application-type=none\n")
PY
  (
    # shellcheck disable=SC2030,SC2031
    PATH="$mig/bin:$scratch/bin:$PATH"
    # shellcheck disable=SC2030,SC2031
    export MIG_LOG="$mig/systemd-run.log" FAKE_SYSTEMCTL_LOG="$mig/systemctl"
    # shellcheck source=scripts/release/remote_install.sh
    source "$here/remote_install.sh"
    assert_root_owned() { :; }
    ETC="$mig/etc" MIGRATION_CREDENTIALS="$mig/etc/migrate" RELEASE_LOGS="$mig/logs" releases="$mig/releases"
    migrate 0.1.0 false
  ) > "$mig/out-$1" 2>&1
}
if [ "$(uname -s)" = Darwin ]; then
  run_migration migrate-mode && run_migration fallback
  if [ "$(wc -l < "$mig/systemd-run.log" | tr -d ' ')" = 2 ] && [ "$(grep -c 'RuntimeMaxSec=900' "$mig/systemd-run.log")" = 2 ] \
     && head -1 "$mig/systemd-run.log" | grep -q -- '--wait' && ! tail -1 "$mig/systemd-run.log" | grep -q -- '--wait'; then
    pass "migration: both paths (migrate mode with --wait, startup fallback) run with RuntimeMaxSec=900"
  else
    flunk "migration: a path lacks RuntimeMaxSec=900" "$mig/systemd-run.log"
  fi
else
  # The stat stand-in translates GNU format strings for macOS; on Linux the real stat needs root-owned files
  pass "migration: RuntimeMaxSec check runs on the macOS CI runner only"
fi

exposure_case() {
  (
    # shellcheck source=scripts/release/remote_install.sh
    source "$here/remote_install.sh"
    ETC="$scratch/exposure"
    mkdir -p "$ETC"
    printf 'MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=%s\n' "$1" > "$ETC/one.env"
    prometheus_exposed
  )
}
if exposure_case 'health,prometheus' && exposure_case "'health, prometheus'" && ! exposure_case health && ! exposure_case 'health,prometheusx'; then
  pass "check_server_files: one.env must expose prometheus (health,prometheus passes; health alone does not)"
else
  flunk "check_server_files: prometheus exposure check is wrong"
fi

jobs_switch_case() {
  (
    # shellcheck source=scripts/release/remote_install.sh
    source "$here/remote_install.sh"
    ETC="$scratch/jobs-switch"
    mkdir -p "$ETC"
    printf 'FORCITIS_JOBS_ENABLED=%s\n' "$1" > "$ETC/one.env"
    jobs_switch_set
  )
}
if jobs_switch_case true && jobs_switch_case false && jobs_switch_case "'true'" && ! jobs_switch_case '' && ! jobs_switch_case yes && ! jobs_switch_case TRUE \
   && ! jobs_switch_case '"true"' && ! jobs_switch_case 'true '; then
  pass "check_server_files: FORCITIS_JOBS_ENABLED must be true or false (empty, yes, TRUE, double-quoted, trailing space are refused)"
else
  flunk "check_server_files: FORCITIS_JOBS_ENABLED check is wrong"
fi

exclude_case() {
  (
    # shellcheck source=scripts/release/remote_install.sh
    source "$here/remote_install.sh"
    ETC="$scratch/autoconfigure"
    mkdir -p "$ETC"
    printf '%s\n' "$1" > "$ETC/one.env"
    autoconfigure_overridden
  )
}
if exclude_case 'SPRING_AUTOCONFIGURE_EXCLUDE=x' && exclude_case 'SPRING_AUTOCONFIGURE_EXCLUDE_0_=x' && exclude_case 'SPRING_AUTOCONFIGURE_EXCLUDE_0=x' \
   && exclude_case 'SPRING_APPLICATION_JSON={}' && ! exclude_case 'FORCITIS_JOBS_ENABLED=true' && ! exclude_case 'SPRING_AUTOCONFIGUREX=x' \
   && ! exclude_case '# SPRING_AUTOCONFIGURE_EXCLUDE=x'; then
  pass "check_server_files: SPRING_AUTOCONFIGURE_EXCLUDE (plain or indexed) and SPRING_APPLICATION_JSON are refused"
else
  flunk "check_server_files: autoconfigure exclusion check is wrong"
fi

STALE_INCOMING=yes check stale-download-removed accept
stale_removed() { [ -z "$(find "$1/releases" -maxdepth 1 -name '.incoming.*')" ] && grep -q 'removed 1 incomplete download' "$1/out"; }
expect stale-download-removed "a .incoming.* left by a killed run is removed first" stale_removed

PROCESSED_BEFORE=alerts-unwritable REQUEST=not-json check alerts-unwritable "reject:install request: is not JSON"
alert_not_sent_logged() { [ ! -e "$1/alerts" ] && grep -q 'alert for invalid-request not sent' "$1/out"; }
expect alerts-unwritable "an unrecordable alert is not sent, and that is logged" alert_not_sent_logged
STATE_BLOCKED=yes TWICE=yes check state-dir-uncreatable "reject:cannot create"
state_alert_once() { one_alert "$1" && grep -q 'configuration: cannot create' "$1/alerts" && second_run_quiet "$1"; }
expect state-dir-uncreatable "one alert through the /run marker; the next run is quiet" state_alert_once

# At most six alerts an hour, whatever the request_ids; the rest are counted and reported with the next alert
cap="$scratch/alert-cap"
prepare_case "$cap" dev "" no
build_release "$cap/assets" 0.1.0 0.1.0 0.1.0 "$scratch/release_key" none
write_state dev none "" "$cap"
set +e
for n in 1 2 3 4 5 6 7 8; do
  python3 -I - "$scratch/state.json" "$n" <<'PY'
import json, sys
state = json.load(open(sys.argv[1]))
state["params"]["forcitis-one-deploy-request-dev"] = f"not json {sys.argv[2]}"
json.dump(state, open(sys.argv[1], "w"))
PY
  attempt "$cap" dev "" "out-$n"
done
set -e
if [ "$(wc -l < "$cap/alerts" | tr -d ' ')" = 6 ] && [ "$(grep -c '^#suppressed' "$cap/state/alerts-sent")" = 2 ] \
   && grep -q 'alert suppressed (6 already sent within the hour)' "$cap/out-7"; then
  pass "alert-cap: eight different invalid requests send six alerts and count two as suppressed"
else
  flunk "alert-cap: expected six alerts and two suppressed" "$cap/out-8"
fi
old=$(( $(date +%s) - 4000 ))
for n in 1 2 3 4 5 6; do printf '#sent\t%s\n' "$old" >> "$cap/state/alerts-sent"; done
sed -i.bak "s/^#sent\t[0-9]*/#sent\t$old/" "$cap/state/alerts-sent"
python3 -I - "$scratch/state.json" <<'PY'
import json, sys
state = json.load(open(sys.argv[1]))
state["params"]["forcitis-one-deploy-request-dev"] = "not json 9"
json.dump(state, open(sys.argv[1], "w"))
PY
set +e
attempt "$cap" dev "" out-9
set -e
if tail -1 "$cap/alerts" | grep -q '(and 2 more alerts suppressed by the hourly limit)$'; then
  pass "alert-cap: the next alert after the hour reports the two suppressed ones"
else
  flunk "alert-cap: the next alert does not report the suppressed ones" "$cap/out-9"
fi

signer_known_answer() {
  # shellcheck source=scripts/release/remote_install.sh
  (source "$here/remote_install.sh"; oos_call self-test)
}
if signer_known_answer | grep -qx 'oos signer self-test passed'; then
  pass "oos-signer: reproduces the RPC signature example of the Aliyun SDK documentation"
else
  flunk "oos-signer: does not reproduce the documented RPC signature example"
fi

echo "$passed passed, $failed failed"
[ "$failed" -eq 0 ]
