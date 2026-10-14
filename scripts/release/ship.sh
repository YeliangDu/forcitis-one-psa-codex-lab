#!/usr/bin/env bash
# Deploy job, after statement.sh verify and verify_bundle.sh: stages the release in the Frankfurt OSS bucket, writes the deploy request
# for the environment, and has the ECS install it: by invoking the fixed, parameterless Cloud Assistant command of that environment
# (DEPLOY_TRIGGER=cloud-assistant) or through the restricted SSH key (DEPLOY_TRIGGER=ssh). The ECS reads the request and the release
# from OSS with its own RAM role and verifies the signed statement itself. Runbook: RELEASE.md §3, §8 among the Phase 1a infra runbooks.
#
#   scripts/release/ship.sh <dir>/forcitis-one-<version>.tar.gz <dev|prod> <commit>   (.sha256, .release, .release.sig next to it)
#
# Inputs (environment): ALIBABA_CLOUD_ACCESS_KEY_ID / _SECRET (environment secrets), ALIYUN_CLI_VERSION (toolchain.sh), OSS_BUCKET,
# OSS_REGION, OSS_PREFIX, optional WECOM_VERIFY_FILE; cloud-assistant: ECS_REGION, ECS_INSTANCE_ID, ECS_COMMAND_ID; ssh: DEPLOY_HOST,
# DEPLOY_USER, optional DEPLOY_PORT, DEPLOY_SSH_KEY, DEPLOY_KNOWN_HOSTS (secrets).
set -euo pipefail

archive="${1:?usage: ship.sh <forcitis-one-version.tar.gz> <dev|prod> <commit>}"
env="${2:?usage: ship.sh <forcitis-one-version.tar.gz> <dev|prod> <commit>}"
commit="${3:?usage: ship.sh <forcitis-one-version.tar.gz> <dev|prod> <commit>}"
fail() { echo "ship.sh: $*" >&2; exit 1; }
need() {
  local name missing=()
  for name in "$@"; do [ -n "${!name:-}" ] || missing+=("$name"); done
  [ "${#missing[@]}" -eq 0 ] || fail "environment $env lacks ${missing[*]} (RELEASE.md §7)"
}

INVOCATION_TIMEOUT_SECONDS=2700

case "$env" in dev|prod) ;; *) fail "environment must be dev or prod" ;; esac
[[ "$commit" =~ ^[0-9a-f]{40}$ ]] || fail "commit must be a full sha"
trigger="${DEPLOY_TRIGGER:-}"
case "$trigger" in cloud-assistant|ssh) ;; *) fail "DEPLOY_TRIGGER must be cloud-assistant or ssh (got '${trigger}')" ;; esac
need ALIBABA_CLOUD_ACCESS_KEY_ID ALIBABA_CLOUD_ACCESS_KEY_SECRET ALIYUN_CLI_VERSION OSS_BUCKET OSS_REGION OSS_PREFIX
[[ "$OSS_PREFIX" =~ ^[a-z0-9][a-z0-9/_-]*[a-z0-9]$ ]] || fail "OSS_PREFIX must be a plain path without leading or trailing slash"
pinned="${WECOM_VERIFY_FILE:--}"
[[ "$pinned" = "-" || "$pinned" =~ ^WW_verify_[A-Za-z0-9]{1,64}\.txt$ ]] || fail "WECOM_VERIFY_FILE must be a WW_verify_*.txt name"

installed_cli="$(aliyun version 2>/dev/null || true)"
[ "$installed_cli" = "$ALIYUN_CLI_VERSION" ] || fail "aliyun CLI $ALIYUN_CLI_VERSION required, found '${installed_cli:-none}'"

name="$(basename "$archive" .tar.gz)"
version="${name#forcitis-one-}"
dir="$(dirname "$archive")"
for file in "$archive.sha256" "$dir/$name.release" "$dir/$name.release.sig"; do
  [ -s "$file" ] || fail "$file must lie next to the bundle"
done
work="$(mktemp -d "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/forcitis-one-ship.XXXXXX")"
trap 'rm -rf "$work"' EXIT
tar -xzf "$archive" -C "$work" "$name/manifest.json" "$name/SHA256SUMS"
sha="$(cut -d' ' -f1 "$archive.sha256")"
[[ "$sha" =~ ^[0-9a-f]{64}$ ]] || fail "cannot read the bundle sha256"

# Credentials only through the environment; never `aliyun configure`, which would leave them in the user's home on the runner
export ALIBABA_CLOUD_ACCESS_KEY_ID ALIBABA_CLOUD_ACCESS_KEY_SECRET
oss_endpoint="oss-${OSS_REGION}.aliyuncs.com"
root="oss://${OSS_BUCKET}/${OSS_PREFIX}"

echo "== staging $name in $root/$version/"
for file in "$archive" "$archive.sha256" "$dir/$name.release" "$dir/$name.release.sig" "$work/$name/manifest.json" "$work/$name/SHA256SUMS"; do
  aliyun oss cp "$file" "$root/$version/$(basename "$file")" --endpoint "$oss_endpoint" -f >/dev/null
done
python3 -c 'import json,sys; json.dump({"version": sys.argv[1], "bundleSha256": sys.argv[2], "commit": sys.argv[3],
           "wecomVerifyFile": sys.argv[4]}, open(sys.argv[5], "w"))' "$version" "$sha" "$commit" "$pinned" "$work/request.json"
aliyun oss cp "$work/request.json" "$root/requests/$env.json" --endpoint "$oss_endpoint" -f >/dev/null
echo "== deploy request for $env: $version ($commit)"

case "$trigger" in
  cloud-assistant)
    need ECS_REGION ECS_INSTANCE_ID ECS_COMMAND_ID
    invoke_id="$(aliyun ecs InvokeCommand --RegionId "$ECS_REGION" --CommandId "$ECS_COMMAND_ID" --InstanceId.1 "$ECS_INSTANCE_ID" \
      | python3 -c 'import json,sys; print(json.load(sys.stdin)["InvokeId"])')"
    echo "Cloud Assistant invocation $invoke_id of $ECS_COMMAND_ID"
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
    ssh -F /dev/null -i "$work/key" -p "${DEPLOY_PORT:-22}" -l "$DEPLOY_USER" \
      -o BatchMode=yes -o IdentitiesOnly=yes -o IdentityAgent=none \
      -o StrictHostKeyChecking=yes -o UserKnownHostsFile="$work/known_hosts" -o GlobalKnownHostsFile=/dev/null \
      -o ConnectTimeout=20 -o ServerAliveInterval=30 \
      -- "$DEPLOY_HOST" install-pending "$env" < /dev/null
    ;;
esac
