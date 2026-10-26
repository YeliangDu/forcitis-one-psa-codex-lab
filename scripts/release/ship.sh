#!/usr/bin/env bash
# Deploy job, after the release was verified: resolves the four release assets of v<version> to GitHub's short-lived signed download
# links, takes temporary Aliyun credentials for this job through GitHub OIDC (STS AssumeRoleWithOIDC), writes the install request into
# this environment's fixed OOS parameters and waits for the ECS, which pulls them every minute, to report in the result parameter. This job
# has no way to make the ECS execute anything.
# Runbook: RELEASE.md §3, §8 among the Phase 1a infra runbooks.
#
#   scripts/release/ship.sh <dev|prod> <version>
#
# Inputs (environment): GH_TOKEN (the job's GITHUB_TOKEN, contents: read), GITHUB_REPOSITORY, GITHUB_RUN_ID, GITHUB_RUN_ATTEMPT,
# ACTIONS_ID_TOKEN_REQUEST_URL / _TOKEN (id-token: write), ALIYUN_CLI_VERSION (toolchain.sh), and the environment variables
# ALIBABA_CLOUD_ROLE_ARN, ALIBABA_CLOUD_OIDC_PROVIDER_ARN, ECS_REGION.
set -euo pipefail
umask 077
# aliyun reads credentials from this process's environment only, never from a profile in the job's home
export ALIBABA_CLOUD_IGNORE_PROFILE=TRUE
# Workflow commands (::add-mask::) must reach the job log even from inside command substitutions
exec 3>&1

env="${1:?usage: ship.sh <dev|prod> <version>}"
version="${2:?usage: ship.sh <dev|prod> <version>}"
fail() { echo "ship.sh: $*" >&2; exit 1; }
export SHIP_GH_TIMEOUT="${SHIP_GH_TIMEOUT:-120}"
# Runs a command for at most $SHIP_GH_TIMEOUT seconds: TERM at the deadline, KILL two seconds later (alarm alone would not stop a gh that ignores SIGALRM); exits 124 on a timeout
bounded() {
  perl -e 'my $pid = fork(); if (!$pid) { exec @ARGV or die "exec: $!" } $SIG{ALRM} = sub { kill "TERM", $pid; sleep 2; kill "KILL", $pid; waitpid($pid, 0); exit 124 }; alarm($ENV{SHIP_GH_TIMEOUT}); waitpid($pid, 0); exit($? >> 8)' "$@"
}
need() {
  local name missing=()
  for name in "$@"; do [ -n "${!name:-}" ] || missing+=("$name"); done
  [ "${#missing[@]}" -eq 0 ] || fail "environment $env lacks ${missing[*]} (RELEASE.md §7)"
}

# forcitis-one-release-pull.service TimeoutStartSec (3000) + timer period (60) + room for a run already in progress; deploy.yml allows
# 80 minutes for the whole job
WAIT_SECONDS=4320
POLL_SECONDS=15
MAX_POLL_FAILURES=6
# Same names, limit and hosts as the installer (remote_install.sh)
REQUEST_PARAMETER_PREFIX=forcitis-one-deploy-request
RESULT_PARAMETER_PREFIX=forcitis-one-deploy-result
MAX_PARAMETER_CHARS=4096
ASSET_HOSTS='objects.githubusercontent.com release-assets.githubusercontent.com'
OIDC_AUDIENCE=sts.aliyuncs.com
STS_ENDPOINT=https://sts.aliyuncs.com
# Covers the wait for the result; AssumeRoleWithOIDC allows up to the role's maximum session duration (default 3600)
SESSION_SECONDS=3600

case "$env" in dev|prod) ;; *) fail "environment must be dev or prod" ;; esac
# shellcheck source=scripts/release/lib.sh
source "$(dirname "$0")/lib.sh"
release_version_valid "$version" || fail "version '$version' is not MAJOR.MINOR.PATCH[-rc.N]"
need GH_TOKEN GITHUB_REPOSITORY GITHUB_RUN_ID GITHUB_RUN_ATTEMPT ACTIONS_ID_TOKEN_REQUEST_URL ACTIONS_ID_TOKEN_REQUEST_TOKEN \
  ALIYUN_CLI_VERSION ALIBABA_CLOUD_ROLE_ARN ALIBABA_CLOUD_OIDC_PROVIDER_ARN ECS_REGION
installed_cli="$(aliyun version 2>/dev/null || true)"
[ "$installed_cli" = "$ALIYUN_CLI_VERSION" ] || fail "aliyun CLI $ALIYUN_CLI_VERSION required, found '${installed_cli:-none}'"

# Tokens, credentials and signed links stay in pipes and shell variables: never in a file, and tokens never on a command line. The
# signed links do appear on the aliyun command line when written to OOS (RELEASE.md §10).
top="forcitis-one-$version"
asset_ids="$(bounded gh api "repos/$GITHUB_REPOSITORY/releases/tags/v$version" | python3 -I -c '
import json, sys
release, top = json.load(sys.stdin), sys.argv[1]
if release.get("draft") is not False:
    sys.exit("the release is a draft or missing")
for key, suffix in (("bundle", ".tar.gz"), ("bundle-sha256", ".tar.gz.sha256"), ("statement", ".release"), ("statement-sig", ".release.sig")):
    found = [a for a in release.get("assets", []) if a.get("name") == top + suffix and a.get("state") == "uploaded"]
    if len(found) != 1 or type(found[0].get("id")) is not int:
        sys.exit(f"release has {len(found)} uploaded assets named {top + suffix}, expected exactly one")
    print(key, found[0]["id"])' "$top")" || fail "release v$version does not have the four assets"

# The asset API answers 302 with the signed link; it is read from Location and never followed here
asset_keys=() asset_links=()
while read -r key id; do
  link="$(printf 'Authorization: Bearer %s\n' "$GH_TOKEN" \
    | curl -q -g -sS --proto '=https' --tlsv1.2 --max-time 60 --max-redirs 0 -H @- -H 'Accept: application/octet-stream' \
        -H 'X-GitHub-Api-Version: 2022-11-28' -o /dev/null -D - "https://api.github.com/repos/$GITHUB_REPOSITORY/releases/assets/$id" \
    | python3 -I -c '
import re, sys
from urllib.parse import parse_qs, urlsplit
key, hosts, max_chars = sys.argv[1], sys.argv[2].split(), int(sys.argv[3])
lines = sys.stdin.buffer.read().decode("latin-1").splitlines()
statuses = [l.split()[1] for l in lines if l.startswith("HTTP/") and len(l.split()) > 1]
answered = statuses[-1] if statuses else "nothing"
if answered != "302":
    sys.exit(f"{key}: asset API answered {answered}, expected a redirect to a signed link")
locations = [l.split(":", 1)[1].strip() for l in lines if l.lower().startswith("location:")]
if len(locations) != 1:
    sys.exit(f"{key}: {len(locations)} Location headers")
url = locations[0]
parts = urlsplit(url)
if parts.scheme != "https" or parts.netloc not in hosts or parts.port is not None or parts.username is not None:
    sys.exit(f"{key}: signed link is not https on {chr(32).join(hosts)} (host {parts.netloc!r})")
if len(url) > max_chars or not re.fullmatch(r"[!-~]+", url) or any(c in url for c in "\"\\{}[]"):
    sys.exit(f"{key}: signed link has {len(url)} characters or characters the installer refuses")
expires = parse_qs(parts.query).get("se", ["not stated"])[0]
print(f"{key}: signed link on {parts.netloc}, {len(url)} characters, valid until {expires}", file=sys.stderr)
print(url)' "$key" "$ASSET_HOSTS" "$MAX_PARAMETER_CHARS")" || fail "no acceptable signed link for $key"
  asset_keys+=("$key")
  asset_links+=("$link")
done <<< "$asset_ids"
[ "${#asset_keys[@]}" -eq 4 ] || fail "resolved ${#asset_keys[@]} signed links, expected 4"

# The parameters are written one at a time; the hashes let the installer refuse links that another run wrote in between
request="$(printf '%s\n' "${asset_links[@]}" | python3 -I -c '
import hashlib, json, sys
version, env, run_id, run_attempt, max_chars, keys = sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4]), int(sys.argv[5]), sys.argv[6:]
links = sys.stdin.read().split("\n")[:-1]
if len(links) != len(keys):
    sys.exit("link count differs from asset count")
request = json.dumps({"version": version, "environment": env, "run_id": run_id, "run_attempt": run_attempt,
                      "request_id": f"{run_id}:{run_attempt}",
                      "asset_url_sha256": {k: hashlib.sha256(l.encode("utf-8")).hexdigest() for k, l in zip(keys, links)}},
                     separators=(",", ":"))
if len(request) > max_chars:
    sys.exit(f"install request has {len(request)} characters, OOS takes at most {max_chars}")
print(request)' "$version" "$env" "$GITHUB_RUN_ID" "$GITHUB_RUN_ATTEMPT" "$MAX_PARAMETER_CHARS" "${asset_keys[@]}")" \
  || fail "could not build the install request"

# GitHub OIDC token for this job (sub names repository, environment and deploy.yml on main), exchanged for temporary STS credentials in
# one pipeline; only the error code and message of a refusal are shown
credentials="$(printf 'Authorization: Bearer %s\n' "$ACTIONS_ID_TOKEN_REQUEST_TOKEN" \
  | curl -q -g -fsS --proto '=https' --tlsv1.2 --max-time 60 -H @- "$ACTIONS_ID_TOKEN_REQUEST_URL&audience=$OIDC_AUDIENCE" \
  | python3 -I -c '
import json, os, sys
token = json.load(sys.stdin)["value"]
os.write(3, ("::add-mask::" + token + "\n").encode())
sys.stdout.write(token)' \
  | curl -q -g -sS --proto '=https' --tlsv1.2 --max-time 60 \
      "$STS_ENDPOINT/?Action=AssumeRoleWithOIDC&Version=2015-04-01&Format=JSON&Timestamp=$(date -u +%Y-%m-%dT%H%%3A%M%%3A%SZ)" \
      --data-urlencode "RoleArn=$ALIBABA_CLOUD_ROLE_ARN" --data-urlencode "OIDCProviderArn=$ALIBABA_CLOUD_OIDC_PROVIDER_ARN" \
      --data-urlencode "OIDCToken@-" --data-urlencode "RoleSessionName=deploy-$env-$GITHUB_RUN_ID-$GITHUB_RUN_ATTEMPT" \
      --data-urlencode "DurationSeconds=$SESSION_SECONDS" \
  | python3 -I -c '
import json, os, sys
try:
    answer = json.load(sys.stdin)
except ValueError:
    sys.exit("STS answered without JSON")
credentials = answer.get("Credentials")
if not isinstance(credentials, dict):
    sys.exit("STS refused: %s %s (request %s)" % (answer.get("Code"), answer.get("Message"), answer.get("RequestId")))
values = [credentials.get(k) for k in ("AccessKeyId", "AccessKeySecret", "SecurityToken")]
if not all(isinstance(v, str) and v and "\n" not in v for v in values):
    sys.exit("STS answer lacks credentials")
for value in values:
    os.write(3, ("::add-mask::" + value + "\n").encode())
role = (answer.get("AssumedRoleUser") or {}).get("Arn")
print("STS session for %s until %s" % (role, credentials.get("Expiration")), file=sys.stderr)
print("\n".join(values))')" \
  || fail "could not obtain temporary Aliyun credentials (id-token: write, the RAM role's trust policy, RELEASE.md §8.2)"
ALIBABA_CLOUD_ACCESS_KEY_ID="${credentials%%$'\n'*}"
credentials="${credentials#*$'\n'}"
ALIBABA_CLOUD_ACCESS_KEY_SECRET="${credentials%%$'\n'*}"
ALIBABA_CLOUD_SECURITY_TOKEN="${credentials#*$'\n'}"
unset credentials
# Credentials reach aliyun only through this process's environment; never `aliyun configure`, which would write them to disk
unset ALIBABA_CLOUD_ROLE_ARN ALIBABA_CLOUD_OIDC_PROVIDER_ARN ALIBABA_CLOUD_OIDC_TOKEN_FILE
export ALIBABA_CLOUD_ACCESS_KEY_ID ALIBABA_CLOUD_ACCESS_KEY_SECRET ALIBABA_CLOUD_SECURITY_TOKEN

# The links first and the request last, so the installer never meets a fresh request next to links of an earlier run
parameter="$REQUEST_PARAMETER_PREFIX-$env"
for i in 0 1 2 3; do
  aliyun oos UpdateParameter --region "$ECS_REGION" --RegionId "$ECS_REGION" --Name "$parameter-${asset_keys[$i]}" \
    --Value "${asset_links[$i]}" > /dev/null || fail "could not write OOS parameter $parameter-${asset_keys[$i]}"
done
aliyun oos UpdateParameter --region "$ECS_REGION" --RegionId "$ECS_REGION" --Name "$parameter" --Value "$request" > /dev/null \
  || fail "could not write OOS parameter $parameter"
request_id="$GITHUB_RUN_ID:$GITHUB_RUN_ATTEMPT"
echo "install request $request_id in OOS $parameter: $request"

# The result parameter only reports what the ECS did; nothing here or anywhere else trusts it for a security decision. A result naming
# another request_id means this request has not been taken up yet.
echo "waiting for the ECS (forcitis-one-release-pull.timer) to take up request $request_id"
deadline=$(($(date +%s) + WAIT_SECONDS))
failures=0
announced="" last_retry=""
stale_hint="the ECS may still take up request $request_id later: check the pull timer's log on the ECS (journalctl -u forcitis-one-release-pull.service) and forcitis-one-release status; to withdraw the request, set $parameter back to {}"
while :; do
  if answer="$(aliyun oos GetParameter --region "$ECS_REGION" --RegionId "$ECS_REGION" --Name "$RESULT_PARAMETER_PREFIX-$env")" \
     && state="$(printf '%s' "$answer" | python3 -I -c '
import json, sys
try:
    result = json.loads(json.load(sys.stdin)["Parameter"]["Value"])
except (ValueError, KeyError, TypeError):
    result = None
if not isinstance(result, dict) or not all(isinstance(result.get(k), str) for k in ("request_id", "status")):
    print("none - -")
else:
    print(result["request_id"], result["status"], result.get("reason") or "-")')"; then
    failures=0
    read -r seen status reason <<< "$state"
    if [ "$seen" = "$request_id" ]; then
      case "$status" in
        succeeded) echo "request $request_id: succeeded on the ECS"; exit 0 ;;
        failed) fail "request $request_id: the ECS reports failed ($reason); details in the pull timer's log on the ECS (journalctl -u forcitis-one-release-pull.service)" ;;
        started) [ -n "$announced" ] || { echo "request $request_id: the ECS started installing"; announced=yes; } ;;
        # Refused before the replay record (link parameters of another run, OOS unreachable): the ECS tries again every minute
        retrying) [ "$last_retry" = "$reason" ] || { echo "request $request_id: the ECS will retry ($reason)"; last_retry="$reason"; } ;;
      esac
    fi
  else
    failures=$((failures + 1))
    echo "ship.sh: reading $RESULT_PARAMETER_PREFIX-$env failed ($failures of $MAX_POLL_FAILURES in a row)" >&2
    [ "$failures" -lt "$MAX_POLL_FAILURES" ] || fail "cannot read the result of request $request_id; $stale_hint"
  fi
  [ "$(date +%s)" -lt "$deadline" ] || fail "no result for request $request_id after ${WAIT_SECONDS}s; $stale_hint"
  sleep "$POLL_SECONDS"
done
