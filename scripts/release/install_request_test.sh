#!/usr/bin/env bash
# Exercises the installer's request handling (remote_install.sh: accept_request, fetch_release): a local stand-in for the ECS metadata
# service and OOS serves the request parameters and checks the session-token flow and the request signature; release assets are built and
# signed here with keys generated for the run and served by a stand-in curl. Only 127.0.0.1 is contacted. Runs on macOS and Linux without root.
#
#   scripts/release/install_request_test.sh
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
# Stand-in for curl: the link must arrive as a config on stdin, never as an argument; serves $FAKE_ASSETS/<last path segment>
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
command -v timeout >/dev/null 2>&1 || printf '#!/bin/sh\nshift\nexec "$@"\n' > "$scratch/bin/timeout"
chmod +x "$scratch/bin"/*

# Stand-in for the metadata service (hardened mode) and OOS GetParameter; answers from the state file, checks token and signature
cat > "$scratch/server.py" <<'PY'
import base64, hashlib, hmac, json, sys
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
                or query.get("SecurityToken") != state["credentials"]["SecurityToken"] or query.get("Action") != "GetParameter"):
            return self.reply(400, {"Code": "SignatureDoesNotMatch", "RequestId": "r-1"})
        if query.get("Name") not in state["params"]:
            return self.reply(404, {"Code": "EntityNotExists.Parameter", "RequestId": "r-2"})
        self.reply(200, {"Parameter": {"Name": query["Name"], "Value": state["params"][query["Name"]]}, "RequestId": "r-3"})
server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
with open(port_file + ".tmp", "w") as f:
    f.write(str(server.server_address[1]))
import os
os.rename(port_file + ".tmp", port_file)
server.serve_forever()
PY

python3 -I - "$scratch/secrets.json" <<'PY'
import json, secrets, sys
json.dump({"token": secrets.token_urlsafe(24),
           "credentials": {"AccessKeyId": "STS." + secrets.token_hex(8), "AccessKeySecret": secrets.token_urlsafe(24),
                           "SecurityToken": secrets.token_urlsafe(48)}}, open(sys.argv[1], "w"))
PY
echo '{"token": "", "credentials": {}, "params": {}, "break": ""}' > "$scratch/state.json"
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

# Writes the stand-in state: the request parameters of <env> for 0.1.0, run 4242 attempt 1, with the named change applied
write_state() {
  python3 -I - "$scratch/state.json" "$scratch/secrets.json" "$1" "$2" "$3" <<'PY'
import json, sys
path, secrets_path, env, change, broken = sys.argv[1:]
state = json.load(open(secrets_path))
prefix = f"forcitis-one-deploy-request-{env}"
def links(version, host="release-assets.githubusercontent.com", scheme="https", userinfo=""):
    top = f"forcitis-one-{version}"
    return {key: f"{scheme}://{userinfo}{host}/github-production-release-asset/1000/{top}{suffix}?sp=r&se=2026-10-15T00%3A05%3A00Z&sig=x"
            for key, suffix in (("bundle", ".tar.gz"), ("bundle-sha256", ".tar.gz.sha256"), ("statement", ".release"),
                                ("statement-sig", ".release.sig"))}
request = {"version": "0.1.0", "environment": env, "run_id": 4242, "run_attempt": 1}
assets = links("0.1.0")
raw = None
if change == "objects-host":
    assets = links("0.1.0", host="objects.githubusercontent.com")
elif change == "rc-version":
    request["version"] = "0.1.0-rc.1"; assets = links("0.1.0-rc.1")
elif change == "extra-key":
    request["assets"] = {}
elif change == "missing-key":
    del request["run_attempt"]
elif change == "duplicate-key":
    raw = json.dumps(request)[:-1] + ', "run_id": 4243}'
elif change == "not-object":
    raw = json.dumps([request])
elif change == "not-json":
    raw = "version=0.1.0"
elif change == "non-ascii":
    raw = json.dumps(request)[:-1] + ', "note": "é"}'
elif change == "nan":
    raw = json.dumps(request)[:-1] + ', "x": NaN}'
elif change == "too-long":
    raw = json.dumps(request)[:-1] + ', "pad": "' + "a" * 4096 + '"}'
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
    params[prefix] = raw if raw is not None else json.dumps(request, separators=(",", ":"))
state.update(params=params, **{"break": broken})
json.dump(state, open(path, "w"))
PY
}

# Runs the request handling in a subshell; prints the installer's log and returns its status
attempt() {
  local case_dir="$1" server_env="$2" running="$3" processed_before="$4"
  shift 4
  (
    PATH="$scratch/bin:$PATH"
    export FAKE_ASSETS="$case_dir/assets"
    # shellcheck source=scripts/release/remote_install.sh
    source "$here/remote_install.sh"
    if stat -c %a / >/dev/null 2>&1; then
      owner_mode() { stat -c '%U %a' "$1" | sed "s/^$(id -un) /root /"; }
    else
      owner_mode() { stat -f '%Su %Lp' "$1" | sed "s/^$(id -un) /root /"; }
    fi
    METADATA_URL="http://127.0.0.1:$port"
    OOS_ENDPOINT="http://127.0.0.1:$port/oos-{region}"
    RELEASE_SIGNERS="$case_dir/signers"
    STATE_DIR="$case_dir/state"
    PROCESSED="$STATE_DIR/processed-requests"
    releases="$case_dir/releases"
    current_link="$case_dir/current"
    cp "$scratch/signers" "$RELEASE_SIGNERS" && chmod 0644 "$RELEASE_SIGNERS"
    mkdir -p "$releases"
    if [ -n "$running" ]; then
      mkdir -p "$releases/$running" && ln -s "releases/$running" "$current_link"
    fi
    case "$processed_before" in
      yes) mkdir -m 0700 "$STATE_DIR" && (umask 077 && printf '4242:1\t2026-10-14T00:00:00Z\tstarted\tv0.1.0\n' > "$PROCESSED") ;;
      unwritable) mkdir -m 0700 "$STATE_DIR" && mkdir -m 0600 "$PROCESSED" ;;
    esac
    target_env="$server_env"
    accept_request "$@"
    fetch_release "$version"
    echo "ACCEPTED version=$version run=$run_id:$run_attempt commit=$commit staged=$(basename "$staged")"
  ) > "$case_dir/out" 2>&1
}

passed=0 failed=0

# check <name> <expect: accept | reject:text> [install-pending arguments...]; set before the name through variables:
# REQUEST (change, default none), BREAK (stand-in failure), SERVER_ENV (dev), RUNNING, PROCESSED_BEFORE (no | yes | unwritable),
# RELEASE ("version manifest-version statement-version key tamper")
check() {
  local name="$1" expect="$2" dir status rc shown leak
  shift 2
  dir="$scratch/$name"
  mkdir -p "$dir/assets"
  # shellcheck disable=SC2086
  build_release "$dir/assets" ${RELEASE:-0.1.0 0.1.0 0.1.0 $scratch/release_key none}
  write_state "${SERVER_ENV:-dev}" "${REQUEST:-none}" "${BREAK:-}"
  # Not in an if: errexit, which the sourced installer sets, must stay in force inside the attempt
  set +e
  attempt "$dir" "${SERVER_ENV:-dev}" "${RUNNING:-}" "${PROCESSED_BEFORE:-no}" "$@"
  rc=$?
  set -e
  if [ "$rc" -eq 0 ]; then status=accepted; else status=rejected; fi
  grep -q 'UNSAFE curl' "$dir/out" && status="unsafe download"
  # Nothing secret or signed may reach the log
  leak="$(python3 -I - "$scratch/secrets.json" "$dir/out" <<'PY'
import json, sys
s = json.load(open(sys.argv[1])); log = open(sys.argv[2]).read()
found = [k for k, v in (("metadata token", s["token"]), ("access key secret", s["credentials"]["AccessKeySecret"]),
                        ("security token", s["credentials"]["SecurityToken"]), ("signed link", "sig=")) if v in log]
print(", ".join(found))
PY
)"
  [ -z "$leak" ] || status="leaked $leak"
  if { [ "$expect" = accept ] && [ "$status" = accepted ]; } \
     || { [ "$expect" != accept ] && [ "$status" = rejected ] && grep -qF -- "${expect#reject:}" "$dir/out"; }; then
    shown="ACCEPTED"; [ "$expect" = accept ] || shown="${expect#reject:}"
    passed=$((passed + 1)); echo "PASS  $name: $status — $(grep -m1 -F -- "$shown" "$dir/out" | cut -c1-170)"
  else
    failed=$((failed + 1)); echo "FAIL  $name: $status, expected $expect"; sed 's/^/      /' "$dir/out"
  fi
}

check valid-request                 accept
REQUEST=objects-host check valid-objects-host accept
REQUEST=rc-version RELEASE="0.1.0-rc.1 0.1.0-rc.1 0.1.0-rc.1 $scratch/release_key none" \
  check valid-rc-on-dev accept
REQUEST=rc-version RELEASE="0.1.0-rc.1 0.1.0-rc.1 0.1.0-rc.1 $scratch/release_key none" SERVER_ENV=prod \
  check prod-refuses-rc "reject:prod takes released versions only"
check request-option-refused        "reject:install-pending takes no arguments" --request e30=
check extra-argument-refused        "reject:install-pending takes no arguments" now

BREAK=token check metadata-token-unavailable "reject:metadata service gave no session token (HTTP 500)"
BREAK=two-roles check instance-two-roles "reject:this instance must have exactly one RAM role, found 2"
BREAK=credentials check role-credentials-failed "reject:role credentials unavailable (Code 'Failed')"
REQUEST=missing-request check request-parameter-missing "reject:cannot read OOS parameter forcitis-one-deploy-request-dev: HTTP 404 EntityNotExists.Parameter"
REQUEST=missing-asset check asset-parameter-missing "reject:cannot read OOS parameter forcitis-one-deploy-request-dev-bundle-sha256: HTTP 404"

REQUEST=not-json check request-not-json           "reject:install request: is not JSON"
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

REQUEST=host=evil.example check host-evil "reject:host 'evil.example'"
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

RUNNING=0.2.0 check downgrade "reject:0.1.0 is lower than the running 0.2.0"
RUNNING=0.1.0-rc.1 check upgrade-from-rc accept
RUNNING=current-build check running-name-malformed "reject:cannot compare versions '0.1.0' and 'current-build'"
PROCESSED_BEFORE=yes check already-processed "reject:request 4242:1 (deploy run:attempt) was already taken up"
PROCESSED_BEFORE=unwritable check started-not-recordable "reject:could not record request 4242:1 as started"

if grep -q $'^4242:1\t.*\tstarted\tv0.1.0$' "$scratch/valid-request/state/processed-requests"; then
  passed=$((passed + 1)); echo "PASS  started-recorded: processed-requests holds 4242:1 started"
else
  failed=$((failed + 1)); echo "FAIL  started-recorded: processed-requests lacks 4242:1 started"
fi
if grep -q $'^4242:1\t.*\tfailed\t' "$scratch/downgrade/state/processed-requests"; then
  passed=$((passed + 1)); echo "PASS  failure-recorded: a refused downgrade is recorded as failed"
else
  failed=$((failed + 1)); echo "FAIL  failure-recorded: processed-requests lacks the failed downgrade"
fi
if ! grep -q 'ACCEPTED' "$scratch/started-not-recordable/out" && ! grep -q 'fetching' "$scratch/started-not-recordable/out"; then
  passed=$((passed + 1)); echo "PASS  no-download-without-record: nothing fetched when the start could not be recorded"
else
  failed=$((failed + 1)); echo "FAIL  no-download-without-record: the release was fetched without a start record"
fi

echo "$passed passed, $failed failed"
[ "$failed" -eq 0 ]
