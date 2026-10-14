#!/usr/bin/env bash
# Deploy job, after the release was verified: resolves the four release assets of v<version> to GitHub's short-lived signed download
# links, takes temporary Aliyun credentials for this job through GitHub OIDC (STS AssumeRoleWithOIDC), invokes the fixed Cloud Assistant
# command with the install request as its only parameter, and waits for the installer's result.
# Runbook: RELEASE.md §3, §8 among the Phase 1a infra runbooks.
#
#   scripts/release/ship.sh <dev|prod> <version>
#
# Inputs (environment): GH_TOKEN (the job's GITHUB_TOKEN, contents: read), GITHUB_REPOSITORY, GITHUB_RUN_ID, GITHUB_RUN_ATTEMPT,
# ACTIONS_ID_TOKEN_REQUEST_URL / _TOKEN (id-token: write), ALIYUN_CLI_VERSION (toolchain.sh), and the environment variables
# ALIBABA_CLOUD_ROLE_ARN, ALIBABA_CLOUD_OIDC_PROVIDER_ARN, ECS_REGION, ECS_INSTANCE_ID, ECS_COMMAND_ID.
set -euo pipefail

env="${1:?usage: ship.sh <dev|prod> <version>}"
version="${2:?usage: ship.sh <dev|prod> <version>}"
fail() { echo "ship.sh: $*" >&2; exit 1; }
need() {
  local name missing=()
  for name in "$@"; do [ -n "${!name:-}" ] || missing+=("$name"); done
  [ "${#missing[@]}" -eq 0 ] || fail "environment $env lacks ${missing[*]} (RELEASE.md §7)"
}

INVOCATION_TIMEOUT_SECONDS=2700
# Same limit and hosts as the installer (remote_install.sh)
MAX_REQUEST_BYTES=16384
ASSET_HOSTS='objects.githubusercontent.com release-assets.githubusercontent.com'
OIDC_AUDIENCE=sts.aliyuncs.com
STS_ENDPOINT=https://sts.aliyuncs.com
# Covers the invocation timeout and polling; AssumeRoleWithOIDC allows up to the role's maximum session duration (default 3600)
SESSION_SECONDS=3600

case "$env" in dev|prod) ;; *) fail "environment must be dev or prod" ;; esac
# shellcheck source=scripts/release/lib.sh
source "$(dirname "$0")/lib.sh"
release_version_valid "$version" || fail "version '$version' is not MAJOR.MINOR.PATCH[-rc.N]"
need GH_TOKEN GITHUB_REPOSITORY GITHUB_RUN_ID GITHUB_RUN_ATTEMPT ACTIONS_ID_TOKEN_REQUEST_URL ACTIONS_ID_TOKEN_REQUEST_TOKEN \
  ALIYUN_CLI_VERSION ALIBABA_CLOUD_ROLE_ARN ALIBABA_CLOUD_OIDC_PROVIDER_ARN ECS_REGION ECS_INSTANCE_ID ECS_COMMAND_ID
installed_cli="$(aliyun version 2>/dev/null || true)"
[ "$installed_cli" = "$ALIYUN_CLI_VERSION" ] || fail "aliyun CLI $ALIYUN_CLI_VERSION required, found '${installed_cli:-none}'"

work="$(mktemp -d "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/forcitis-one-ship.XXXXXX")"
chmod 700 "$work"
trap 'rm -rf "$work"' EXIT

# Every token and the signed links go through files in $work, never onto a command line
(umask 077 && printf 'Authorization: Bearer %s\n' "$GH_TOKEN" > "$work/github-header")

top="forcitis-one-$version"
asset_ids="$(gh api "repos/$GITHUB_REPOSITORY/releases/tags/v$version" | python3 -I -c '
import json, sys
release, top = json.load(sys.stdin), sys.argv[1]
if release.get("draft") is not False:
    sys.exit("the release is a draft or missing")
for name in (f"{top}.tar.gz", f"{top}.tar.gz.sha256", f"{top}.release", f"{top}.release.sig"):
    found = [a for a in release.get("assets", []) if a.get("name") == name and a.get("state") == "uploaded"]
    if len(found) != 1 or type(found[0].get("id")) is not int:
        sys.exit(f"release has {len(found)} uploaded assets named {name}, expected exactly one")
    print(name, found[0]["id"])' "$top")" || fail "release v$version does not have the four assets"

# The asset API answers 302 with the signed link; it is read from Location and never followed here
: > "$work/links"
while read -r name id; do
  status="$(curl -sS --proto '=https' --tlsv1.2 --max-time 60 --max-redirs 0 -H @"$work/github-header" \
    -H 'Accept: application/octet-stream' -H 'X-GitHub-Api-Version: 2022-11-28' -o /dev/null -D "$work/headers" -w '%{http_code}' \
    "https://api.github.com/repos/$GITHUB_REPOSITORY/releases/assets/$id")" || fail "asset API request for $name failed"
  [ "$status" = 302 ] || fail "asset API answered $status for $name, expected a redirect to a signed link"
  python3 -I - "$work/headers" "$name" "$ASSET_HOSTS" >> "$work/links" <<'PY' || fail "no acceptable signed link for $name"
import sys
from urllib.parse import parse_qs, urlsplit
headers, name, hosts = sys.argv[1], sys.argv[2], sys.argv[3].split()
locations = [line.split(":", 1)[1].strip() for line in open(headers, encoding="latin-1") if line.lower().startswith("location:")]
if len(locations) != 1:
    sys.exit(f"{name}: {len(locations)} Location headers")
url = locations[0]
parts = urlsplit(url)
if parts.scheme != "https" or parts.netloc not in hosts or parts.port is not None or parts.username is not None:
    sys.exit(f"{name}: signed link is not https on {' or '.join(hosts)} (host {parts.netloc!r})")
expires = parse_qs(parts.query).get("se", ["not stated"])[0]
print(f"{name}: signed link on {parts.netloc}, valid until {expires}", file=sys.stderr)
print(name, url)
PY
done <<< "$asset_ids"
rm -f "$work/github-header" "$work/headers"

python3 -I - "$work/links" "$version" "$env" "$GITHUB_RUN_ID" "$GITHUB_RUN_ATTEMPT" "$MAX_REQUEST_BYTES" > "$work/parameters" <<'PY' \
  || fail "could not build the install request"
import base64, json, sys
links, version, env, run_id, run_attempt, max_bytes = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4]), int(sys.argv[5]), int(sys.argv[6])
assets = dict(line.split(" ", 1) for line in open(links).read().splitlines())
request = {"version": version, "environment": env, "run_id": run_id, "run_attempt": run_attempt, "assets": assets}
encoded = base64.b64encode(json.dumps(request, separators=(",", ":")).encode("utf-8")).decode("ascii")
if len(encoded) > max_bytes:
    sys.exit(f"install request is {len(encoded)} characters, the installer takes at most {max_bytes}")
print(f"install request: {len(encoded)} characters", file=sys.stderr)
json.dump({"request": encoded}, sys.stdout)
PY

# GitHub OIDC token for this job (sub names repository, environment and deploy.yml on main), exchanged for temporary STS credentials
(umask 077 && printf 'Authorization: Bearer %s\n' "$ACTIONS_ID_TOKEN_REQUEST_TOKEN" > "$work/oidc-header")
curl -fsS --proto '=https' --tlsv1.2 --max-time 60 -H @"$work/oidc-header" "$ACTIONS_ID_TOKEN_REQUEST_URL&audience=$OIDC_AUDIENCE" \
  | python3 -I -c 'import json,sys; t=json.load(sys.stdin)["value"]; print("::add-mask::" + t); open(sys.argv[1], "w").write(t)' "$work/oidc-token" \
  || fail "could not obtain the GitHub OIDC token (the job needs permissions: id-token: write)"
rm -f "$work/oidc-header"
curl -sS --proto '=https' --tlsv1.2 --max-time 60 -o "$work/sts.json" -w '%{http_code}' \
  "$STS_ENDPOINT/?Action=AssumeRoleWithOIDC&Version=2015-04-01&Format=JSON&Timestamp=$(date -u +%Y-%m-%dT%H%%3A%M%%3A%SZ)" \
  --data-urlencode "RoleArn=$ALIBABA_CLOUD_ROLE_ARN" --data-urlencode "OIDCProviderArn=$ALIBABA_CLOUD_OIDC_PROVIDER_ARN" \
  --data-urlencode "OIDCToken@$work/oidc-token" --data-urlencode "RoleSessionName=deploy-$env-$GITHUB_RUN_ID-$GITHUB_RUN_ATTEMPT" \
  --data-urlencode "DurationSeconds=$SESSION_SECONDS" > "$work/sts.status" || fail "STS AssumeRoleWithOIDC request failed"
rm -f "$work/oidc-token"
# Only the error code and message of a refusal are shown; a successful answer holds credentials and is never printed
python3 -I - "$work/sts.json" "$(cat "$work/sts.status")" "$work/sts.env" <<'PY' || fail "STS refused the OIDC token (check the RAM role's trust policy, RELEASE.md §8.2)"
import json, sys
path, status, out = sys.argv[1], sys.argv[2], sys.argv[3]
try:
    answer = json.load(open(path))
except ValueError:
    sys.exit(f"STS answered HTTP {status} without JSON")
credentials = answer.get("Credentials") if status == "200" else None
if not isinstance(credentials, dict):
    sys.exit(f"STS answered HTTP {status}: {answer.get('Code')} {answer.get('Message')} (request {answer.get('RequestId')})")
values = [credentials.get(k) for k in ("AccessKeyId", "AccessKeySecret", "SecurityToken")]
if not all(isinstance(v, str) and v and "\n" not in v for v in values):
    sys.exit("STS answer lacks credentials")
for value in values:
    print("::add-mask::" + value)
with open(out, "w") as f:
    f.write("\n".join(values) + "\n")
print(f"STS session for {answer.get('AssumedRoleUser', {}).get('Arn')} until {credentials.get('Expiration')}", file=sys.stderr)
PY
rm -f "$work/sts.json"
{ read -r ALIBABA_CLOUD_ACCESS_KEY_ID; read -r ALIBABA_CLOUD_ACCESS_KEY_SECRET; read -r ALIBABA_CLOUD_SECURITY_TOKEN; } < "$work/sts.env"
rm -f "$work/sts.env"
# Credentials reach aliyun only through this process's environment; never `aliyun configure`, which would write them to disk
unset ALIBABA_CLOUD_ROLE_ARN ALIBABA_CLOUD_OIDC_PROVIDER_ARN ALIBABA_CLOUD_OIDC_TOKEN_FILE
export ALIBABA_CLOUD_ACCESS_KEY_ID ALIBABA_CLOUD_ACCESS_KEY_SECRET ALIBABA_CLOUD_SECURITY_TOKEN

invoke_id="$(aliyun ecs InvokeCommand --RegionId "$ECS_REGION" --CommandId "$ECS_COMMAND_ID" --InstanceId.1 "$ECS_INSTANCE_ID" \
  --Parameters "$(cat "$work/parameters")" | python3 -I -c 'import json,sys; print(json.load(sys.stdin)["InvokeId"])')" \
  || fail "InvokeCommand failed"
rm -f "$work/parameters"
echo "Cloud Assistant invocation $invoke_id of $ECS_COMMAND_ID"
deadline=$((SECONDS + INVOCATION_TIMEOUT_SECONDS + 120))
while :; do
  result="$(aliyun ecs DescribeInvocationResults --RegionId "$ECS_REGION" --InvokeId "$invoke_id" --InstanceId "$ECS_INSTANCE_ID")"
  status="$(printf '%s' "$result" | python3 -I -c 'import json,sys; r=json.load(sys.stdin)["Invocation"]["InvocationResults"]["InvocationResult"]; print(r[0]["InvocationStatus"] if r else "Pending")')"
  case "$status" in
    Success|Failed|Error|Timeout|Cancelled|Terminated|Invalid|Aborted) break ;;
  esac
  [ "$SECONDS" -lt "$deadline" ] || fail "invocation $invoke_id still $status after the timeout"
  sleep 10
done
printf '%s' "$result" | python3 -I -c '
import base64, json, sys
r = json.load(sys.stdin)["Invocation"]["InvocationResults"]["InvocationResult"][0]
print(base64.b64decode(r.get("Output") or "").decode("utf-8", "replace"))
print("invocation status %s, exit code %s, error %s" % (r.get("InvocationStatus"), r.get("ExitCode"), r.get("ErrorCode") or "-"))
sys.exit(0 if r.get("InvocationStatus") == "Success" and r.get("ExitCode") == 0 else 1)'
