#!/usr/bin/env bash
# Exercises the installer's request handling (remote_install.sh: accept_request, fetch_release) against release assets built and signed
# here with a key generated for the run, served by a stand-in curl; nothing reaches the network. Runs on macOS and Linux without root.
#
#   scripts/release/install_request_test.sh
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
scratch="$(mktemp -d)"
trap 'rm -rf "$scratch"' EXIT
COMMIT=0123456789abcdef0123456789abcdef01234567
WORKFLOW=FORCITIS/forcitis-one-psa/.github/workflows/release.yml@refs/heads/main
LINK_BASE=https://release-assets.githubusercontent.com/github-production-release-asset/1000

mkdir -p "$scratch/bin"
cat > "$scratch/bin/curl" <<'CURL'
#!/usr/bin/env bash
# Stand-in for curl: serves $FAKE_ASSETS/<last path segment>; refuses credentials and any call that could follow a redirect
out="" url="" no_redirects=""
while [ "$#" -gt 0 ]; do
  case "$1" in
    -o) out="$2"; shift ;;
    -H) echo "header sent: $2" >&2; exit 97 ;;
    --max-redirs) [ "$2" = 0 ] && no_redirects=yes; shift ;;
    https://*|http://*) url="$1" ;;
  esac
  shift
done
[ -n "$no_redirects" ] || { echo "curl called without --max-redirs 0" >&2; exit 96; }
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

ssh-keygen -q -t ed25519 -N '' -C install-request-test -f "$scratch/release_key"
ssh-keygen -q -t ed25519 -N '' -C install-request-test-other -f "$scratch/other_key"
printf 'forcitis-one-release namespaces="forcitis-one-release" %s\n' "$(cut -d' ' -f1,2 "$scratch/release_key.pub")" > "$scratch/signers"

sha() { shasum -a 256 | cut -d' ' -f1; }

# Builds the four release assets of <version> into <dir>; the manifest and the signed statement may name other versions
build_release() {
  local dir="$1" version="$2" manifest_version="$3" statement_version="$4" key="$5" top work
  top="forcitis-one-$version"
  work="$dir/build"
  mkdir -p "$work/$top/web-dev" "$work/$top/deploy"
  echo '<!doctype html>' > "$work/$top/web-dev/index.html"
  echo 'echo installer' > "$work/$top/deploy/remote_install.sh"
  printf '{"version": "%s", "gitSha": "%s"}\n' "$manifest_version" "$COMMIT" > "$work/$top/manifest.json"
  (cd "$work/$top" && find . -type f | sed 's|^\./||' | LC_ALL=C sort | while IFS= read -r f; do
    printf '%s  %s\n' "$(sha < "$f")" "$f"; done > ../sums && mv ../sums SHA256SUMS)
  COPYFILE_DISABLE=1 tar -czf "$dir/$top.tar.gz" -C "$work" "$top"
  printf '%s  %s\n' "$(sha < "$dir/$top.tar.gz")" "$top.tar.gz" > "$dir/$top.tar.gz.sha256"
  {
    echo "format: forcitis-one-release-statement/1"
    echo "version: $statement_version"
    echo "commit: $COMMIT"
    echo "bundle: $top.tar.gz"
    echo "bundle-sha256: $(sha < "$dir/$top.tar.gz")"
    echo "manifest-sha256: $(sha < "$work/$top/manifest.json")"
    echo "sha256sums-sha256: $(sha < "$work/$top/SHA256SUMS")"
    echo "workflow: $WORKFLOW"
    echo "run: 77"
  } > "$dir/$top.release"
  ssh-keygen -q -Y sign -f "$key" -n forcitis-one-release "$dir/$top.release" >/dev/null 2>&1
  rm -rf "$work"
}

# Prints the base64 install request for 0.1.0 to dev, run 4242 attempt 1, with the named change applied
request() {
  python3 -I - "$1" "$LINK_BASE" <<'PY'
import base64, json, sys
change, base = sys.argv[1], sys.argv[2]
def links(version, host="release-assets.githubusercontent.com", scheme="https", prefix=""):
    top = f"forcitis-one-{version}"
    return {n: f"{scheme}://{prefix}{host}/github-production-release-asset/1000/{n}?sp=r&se=2026-10-15T00%3A05%3A00Z&sig=x"
            for n in (f"{top}.tar.gz", f"{top}.tar.gz.sha256", f"{top}.release", f"{top}.release.sig")}
r = {"version": "0.1.0", "environment": "dev", "run_id": 4242, "run_attempt": 1, "assets": links("0.1.0")}
raw = None
if change == "objects-host":
    r["assets"] = links("0.1.0", host="objects.githubusercontent.com")
elif change == "rc-version":
    r["version"] = "0.1.0-rc.1"; r["assets"] = links("0.1.0-rc.1")
elif change == "prod-rc":
    r["version"] = "0.1.0-rc.1"; r["assets"] = links("0.1.0-rc.1"); r["environment"] = "prod"
elif change == "extra-key":
    r["commit"] = "0" * 40
elif change == "missing-key":
    del r["run_attempt"]
elif change == "duplicate-key":
    raw = json.dumps(r)[:-1] + ', "run_id": 4243}'
elif change == "not-object":
    r = [r]
elif change == "environment":
    r["environment"] = "prod"
elif change == "environment-unknown":
    r["environment"] = "staging"
elif change.startswith("version="):
    r["version"] = change.split("=", 1)[1]
elif change == "run-id-zero":
    r["run_id"] = 0
elif change == "run-id-string":
    r["run_id"] = "4242"
elif change == "run-attempt-bool":
    r["run_attempt"] = True
elif change == "run-id-float":
    r["run_id"] = 4242.0
elif change == "extra-asset":
    r["assets"]["forcitis-one-0.1.0.extra"] = r["assets"]["forcitis-one-0.1.0.tar.gz"]
elif change == "asset-other-version":
    r["assets"] = links("0.2.0")
elif change.startswith("host="):
    r["assets"]["forcitis-one-0.1.0.tar.gz"] = links("0.1.0", host=change.split("=", 1)[1])["forcitis-one-0.1.0.tar.gz"]
elif change == "http":
    r["assets"]["forcitis-one-0.1.0.release"] = links("0.1.0", scheme="http")["forcitis-one-0.1.0.release"]
elif change == "userinfo":
    r["assets"]["forcitis-one-0.1.0.release.sig"] = links("0.1.0", prefix="user:pass@")["forcitis-one-0.1.0.release.sig"]
elif change == "url-space":
    r["assets"]["forcitis-one-0.1.0.tar.gz"] += " x"
elif change == "url-fragment":
    r["assets"]["forcitis-one-0.1.0.tar.gz"] += "#frag"
elif change != "none":
    sys.exit(f"unknown change {change}")
raw = raw or json.dumps(r)
print(base64.b64encode(raw.encode()).decode())
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
    if [ "$processed_before" = yes ]; then
      mkdir -m 0700 "$STATE_DIR" && (umask 077 && printf '4242:1\t2026-10-14T00:00:00Z\tstarted\tv0.1.0\n' > "$PROCESSED")
    fi
    target_env="$server_env"
    accept_request "$@"
    fetch_release "$version"
    echo "ACCEPTED version=$version run=$run_id:$run_attempt commit=$commit staged=$(basename "$staged")"
  ) > "$case_dir/out" 2>&1
}

passed=0 failed=0

# check <name> <expect: accept | reject:text> <arguments...>; options before the name through variables:
# SERVER_ENV (dev), RUNNING (none), PROCESSED_BEFORE (no), RELEASE (version manifest-version statement-version key)
check() {
  local name="$1" expect="$2" dir status rc shown
  shift 2
  dir="$scratch/$name"
  mkdir -p "$dir/assets"
  # shellcheck disable=SC2086
  build_release "$dir/assets" ${RELEASE:-0.1.0 0.1.0 0.1.0 $scratch/release_key}
  # Not in an if: errexit, which the sourced installer sets, must stay in force inside the attempt
  set +e
  attempt "$dir" "${SERVER_ENV:-dev}" "${RUNNING:-}" "${PROCESSED_BEFORE:-no}" "$@"
  rc=$?
  set -e
  if [ "$rc" -eq 0 ]; then status=accepted; else status=rejected; fi
  if grep -qE 'header sent|without --max-redirs 0' "$dir/out"; then
    status="unsafe download"
  fi
  if { [ "$expect" = accept ] && [ "$status" = accepted ]; } \
     || { [ "$expect" != accept ] && [ "$status" = rejected ] && grep -qF -- "${expect#reject:}" "$dir/out"; }; then
    shown="ACCEPTED"; [ "$expect" = accept ] || shown="${expect#reject:}"
    passed=$((passed + 1)); echo "PASS  $name: $status — $(grep -m1 -F -- "$shown" "$dir/out" | cut -c1-170)"
  else
    failed=$((failed + 1)); echo "FAIL  $name: $status, expected $expect"; sed 's/^/      /' "$dir/out"
  fi
}

check valid-request                 accept --request "$(request none)"
check valid-objects-host            accept --request "$(request objects-host)"
RELEASE="0.1.0-rc.1 0.1.0-rc.1 0.1.0-rc.1 $scratch/release_key" \
check valid-rc-on-dev               accept --request "$(request rc-version)"
RELEASE="0.1.0-rc.1 0.1.0-rc.1 0.1.0-rc.1 $scratch/release_key" SERVER_ENV=prod \
check prod-refuses-rc               "reject:prod takes released versions only" --request "$(request prod-rc)"
check no-arguments                  "reject:install-pending takes exactly: --request"
check extra-cli-argument            "reject:install-pending takes exactly: --request" --request "$(request none)" extra
check request-equals-form           "reject:install-pending takes exactly: --request" "--request=$(request none)"
check other-option-name             "reject:install-pending takes exactly: --request" --req "$(request none)"
check base64-garbage                "reject:is not standard base64" --request '!!not-base64!!'
check base64-with-newline           "reject:is not standard base64" --request "$(request none | fold -w 60)"
check base64-bad-padding            "reject:is not standard base64" --request "$(request none)A"
check base64-not-utf8               "reject:is not base64 of UTF-8 text" --request "$(printf '\377\376\375' | base64)"
check base64-not-json               "reject:is not JSON" --request "$(printf 'version=0.1.0' | base64)"
check empty-request                 "reject:must be 1..16384 characters" --request ""
check too-long-request              "reject:must be 1..16384 characters" --request "$(python3 -I -c 'print("A" * 16388)')"
check not-an-object                 "reject:must be an object with exactly the keys" --request "$(request not-object)"
check extra-json-key                "reject:must be an object with exactly the keys" --request "$(request extra-key)"
check missing-json-key              "reject:must be an object with exactly the keys" --request "$(request missing-key)"
check duplicate-json-key            "reject:has a duplicate key" --request "$(request duplicate-key)"
check environment-mismatch          "reject:is for environment prod, this server is dev" --request "$(request environment)"
check environment-unknown           "reject:environment 'staging' is not dev or prod" --request "$(request environment-unknown)"
check version-two-parts             "reject:version '0.1' is not MAJOR.MINOR.PATCH" --request "$(request version=0.1)"
check version-with-v                "reject:version 'v0.1.0' is not MAJOR.MINOR.PATCH" --request "$(request version=v0.1.0)"
check version-newline               "reject:is not MAJOR.MINOR.PATCH" --request "$(request "version=0.1.0
")"
check version-path                  "reject:is not MAJOR.MINOR.PATCH" --request "$(request version=../0.1.0)"
check run-id-zero                   "reject:run_id must be a positive integer" --request "$(request run-id-zero)"
check run-id-string                 "reject:run_id must be a positive integer" --request "$(request run-id-string)"
check run-id-float                  "reject:run_id must be a positive integer" --request "$(request run-id-float)"
check run-attempt-bool              "reject:run_attempt must be a positive integer" --request "$(request run-attempt-bool)"
check extra-asset                   "reject:assets must name exactly" --request "$(request extra-asset)"
check assets-of-other-version       "reject:assets must name exactly" --request "$(request asset-other-version)"
check host-evil                     "reject:host 'evil.example'" --request "$(request host=evil.example)"
check host-suffix-trick             "reject:host 'release-assets.githubusercontent.com.evil.com'" --request "$(request host=release-assets.githubusercontent.com.evil.com)"
check host-parent-domain            "reject:host 'githubusercontent.com'" --request "$(request host=githubusercontent.com)"
check host-trailing-dot             "reject:host 'objects.githubusercontent.com.'" --request "$(request host=objects.githubusercontent.com.)"
check host-upper-case               "reject:host 'Release-Assets.githubusercontent.com'" --request "$(request host=Release-Assets.githubusercontent.com)"
check host-explicit-port-443        "reject:host 'release-assets.githubusercontent.com:443'" --request "$(request host=release-assets.githubusercontent.com:443)"
check host-explicit-port-8443       "reject:host 'release-assets.githubusercontent.com:8443'" --request "$(request host=release-assets.githubusercontent.com:8443)"
check http-scheme                   "reject:got scheme 'http'" --request "$(request http)"
check userinfo                      "reject:host 'user:pass@release-assets.githubusercontent.com'" --request "$(request userinfo)"
check url-with-space                "reject:URL is not one printable ASCII string" --request "$(request url-space)"
check url-with-fragment             "reject:carries a fragment" --request "$(request url-fragment)"
RELEASE="0.1.0 0.1.0 0.2.0 $scratch/release_key" \
check statement-version-differs     "reject:signed release statement is for version '0.2.0', the request names 0.1.0" --request "$(request none)"
RELEASE="0.1.0 0.2.0 0.1.0 $scratch/release_key" \
check manifest-version-differs      "reject:manifest.json is not for the requested version 0.1.0" --request "$(request none)"
RELEASE="0.1.0 0.1.0 0.1.0 $scratch/other_key" \
check statement-other-signer        "reject:signature does not verify" --request "$(request none)"
RUNNING=0.2.0 \
check downgrade                     "reject:0.1.0 is lower than the running 0.2.0" --request "$(request none)"
RUNNING=0.1.0-rc.1 \
check upgrade-from-rc               accept --request "$(request none)"
PROCESSED_BEFORE=yes \
check already-processed             "reject:request 4242:1 (deploy run:attempt) was already taken up" --request "$(request none)"

# A recorded request stays recorded: the accepted request above wrote 'started' before downloading
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

echo "$passed passed, $failed failed"
[ "$failed" -eq 0 ]
