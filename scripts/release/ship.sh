#!/usr/bin/env bash
# Deploy job, after verify_bundle.sh and statement.sh verify: stages the verified bundle in the Frankfurt OSS bucket, presigns a short-lived download URL and has
# the ECS install it, by Cloud Assistant (DEPLOY_TRIGGER=cloud-assistant) or by the restricted SSH key (DEPLOY_TRIGGER=ssh).
# Runbook: RELEASE.md §3, §8 among the Phase 1a infra runbooks
#
#   scripts/release/ship.sh <dir>/forcitis-one-<version>.tar.gz <dev|prod>    (<archive>.sha256, .release and .release.sig next to it)
#
# Inputs (environment): ALIBABA_CLOUD_ACCESS_KEY_ID / _SECRET (environment secrets), ALIYUN_CLI_VERSION, OSS_BUCKET, OSS_REGION,
# OSS_PREFIX, optional OSS_DOWNLOAD_ENDPOINT; cloud-assistant: ECS_REGION, ECS_INSTANCE_ID; ssh: DEPLOY_HOST, DEPLOY_USER, optional
# DEPLOY_PORT, DEPLOY_SSH_KEY, DEPLOY_KNOWN_HOSTS (secrets).
set -euo pipefail

archive="${1:?usage: ship.sh <forcitis-one-version.tar.gz> <dev|prod>}"
env="${2:?usage: ship.sh <forcitis-one-version.tar.gz> <dev|prod>}"
fail() { echo "ship.sh: $*" >&2; exit 1; }
need() {
  local name missing=()
  for name in "$@"; do [ -n "${!name:-}" ] || missing+=("$name"); done
  [ "${#missing[@]}" -eq 0 ] || fail "environment $env lacks ${missing[*]} (RELEASE.md §7)"
}

PRESIGN_SECONDS=1800
INVOCATION_TIMEOUT_SECONDS=2700

case "$env" in dev|prod) ;; *) fail "environment must be dev or prod" ;; esac
trigger="${DEPLOY_TRIGGER:-}"
case "$trigger" in cloud-assistant|ssh) ;; *) fail "DEPLOY_TRIGGER must be cloud-assistant or ssh (got '${trigger}')" ;; esac
need ALIBABA_CLOUD_ACCESS_KEY_ID ALIBABA_CLOUD_ACCESS_KEY_SECRET ALIYUN_CLI_VERSION OSS_BUCKET OSS_REGION OSS_PREFIX
[[ "$OSS_PREFIX" =~ ^[a-z0-9][a-z0-9/_-]*[a-z0-9]$ ]] || fail "OSS_PREFIX must be a plain path without leading or trailing slash"

installed_cli="$(aliyun version 2>/dev/null || true)"
[ "$installed_cli" = "$ALIYUN_CLI_VERSION" ] || fail "aliyun CLI $ALIYUN_CLI_VERSION required, found '${installed_cli:-none}'"

name="$(basename "$archive" .tar.gz)"
version="${name#forcitis-one-}"
work="$(mktemp -d "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/forcitis-one-ship.XXXXXX")"
trap 'rm -rf "$work"' EXIT
tar -xzf "$archive" -C "$work" "$name/manifest.json" "$name/SHA256SUMS"
sha="$(cut -d' ' -f1 "$archive.sha256")"
statement="$(dirname "$archive")/$name.release"
[ -s "$statement" ] && [ -s "$statement.sig" ] || fail "the signed release statement must lie next to the bundle"
statement_b64="$(base64 < "$statement" | tr -d '\n')"
signature_b64="$(base64 < "$statement.sig" | tr -d '\n')"
commit="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["gitSha"])' "$work/$name/manifest.json")"
[[ "$sha" =~ ^[0-9a-f]{64}$ && "$commit" =~ ^[0-9a-f]{40}$ ]] || fail "cannot read the bundle sha256 or commit"

# Credentials only through the environment; never `aliyun configure`, which would leave them in ~/.aliyun on the runner
export ALIBABA_CLOUD_ACCESS_KEY_ID ALIBABA_CLOUD_ACCESS_KEY_SECRET
oss_endpoint="oss-${OSS_REGION}.aliyuncs.com"
base="oss://${OSS_BUCKET}/${OSS_PREFIX}/${version}"

echo "== staging $name in ${base}/"
for file in "$archive" "$archive.sha256" "$statement" "$statement.sig" "$work/$name/manifest.json" "$work/$name/SHA256SUMS"; do
  aliyun oss cp "$file" "$base/$(basename "$file")" --endpoint "$oss_endpoint" -f >/dev/null
done
staged_sha="$(aliyun oss cat "$base/$name.tar.gz.sha256" --endpoint "$oss_endpoint" | cut -d' ' -f1)"
[ "$staged_sha" = "$sha" ] || fail "the staged .sha256 does not match the verified bundle"

url="$(aliyun oss sign "$base/$name.tar.gz" --timeout "$PRESIGN_SECONDS" --endpoint "${OSS_DOWNLOAD_ENDPOINT:-$oss_endpoint}" | grep -m1 -E '^https?://')"
case "$url" in http://*) url="https://${url#http://}" ;; esac
[[ "$url" =~ ^https://[a-z0-9.-]+\.aliyuncs\.com/[^[:space:]\']+$ ]] || fail "presigning returned no usable URL"
[ -z "${GITHUB_ACTIONS:-}" ] || echo "::add-mask::$url"

echo "== installing $version on $env through $trigger"
case "$trigger" in
  cloud-assistant)
    need ECS_REGION ECS_INSTANCE_ID
    script="set -euo pipefail
printf '%s\\n' '$url' '$statement_b64' '$signature_b64' | /usr/local/sbin/forcitis-one-release install $version $env $sha $commit"
    invoke_id="$(aliyun ecs RunCommand --RegionId "$ECS_REGION" --InstanceId.1 "$ECS_INSTANCE_ID" --Type RunShellScript \
      --Name "forcitis-one-deploy-$version" --ContentEncoding Base64 --CommandContent "$(printf '%s' "$script" | base64 | tr -d '\n')" \
      --Timeout "$INVOCATION_TIMEOUT_SECONDS" | python3 -c 'import json,sys; print(json.load(sys.stdin)["InvokeId"])')"
    echo "Cloud Assistant invocation $invoke_id"
    deadline=$((SECONDS + INVOCATION_TIMEOUT_SECONDS + 120))
    while :; do
      result="$(aliyun ecs DescribeInvocationResults --RegionId "$ECS_REGION" --InvokeId "$invoke_id" --InstanceId "$ECS_INSTANCE_ID")"
      status="$(printf '%s' "$result" | python3 -c 'import json,sys; r=json.load(sys.stdin)["Invocation"]["InvocationResults"]["InvocationResult"]; print(r[0]["InvocationStatus"] if r else "Pending")')"
      case "$status" in
        Success|Failed|Error|Timeout|Cancelled|Terminated|Invalid|Aborted) break ;;
      esac
      [ "$SECONDS" -lt "$deadline" ] || fail "invocation $invoke_id still $status after the timeout"
      sleep 10
    done
    printf '%s' "$result" | python3 -c '
import base64, json, sys
r = json.load(sys.stdin)["Invocation"]["InvocationResults"]["InvocationResult"][0]
print(base64.b64decode(r.get("Output") or "").decode("utf-8", "replace"))
print("invocation status %s, exit code %s, error %s" % (r.get("InvocationStatus"), r.get("ExitCode"), r.get("ErrorCode") or "-"))
sys.exit(0 if r.get("InvocationStatus") == "Success" and r.get("ExitCode") == 0 else 1)'
    ;;
  ssh)
    need DEPLOY_HOST DEPLOY_USER DEPLOY_SSH_KEY DEPLOY_KNOWN_HOSTS
    (umask 077 && printf '%s\n' "$DEPLOY_SSH_KEY" > "$work/key" && printf '%s\n' "$DEPLOY_KNOWN_HOSTS" > "$work/known_hosts")
    printf '%s\n' "$url" "$statement_b64" "$signature_b64" | ssh -F /dev/null -i "$work/key" -p "${DEPLOY_PORT:-22}" -l "$DEPLOY_USER" \
      -o BatchMode=yes -o IdentitiesOnly=yes -o IdentityAgent=none \
      -o StrictHostKeyChecking=yes -o UserKnownHostsFile="$work/known_hosts" -o GlobalKnownHostsFile=/dev/null \
      -o ConnectTimeout=20 -o ServerAliveInterval=30 \
      -- "$DEPLOY_HOST" install "$version" "$env" "$sha" "$commit"
    ;;
esac
