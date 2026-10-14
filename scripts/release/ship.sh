#!/usr/bin/env bash
# Deploy job, after the GitHub deployment for this run was created: has the ECS install it, by invoking the fixed, parameterless Cloud
# Assistant command (DEPLOY_TRIGGER=cloud-assistant) or through the restricted SSH key (DEPLOY_TRIGGER=ssh), and waits for the result.
# The ECS finds the deployment, checks it against the GitHub API and downloads the release from GitHub itself; nothing is passed to it.
# Runbook: RELEASE.md §3, §8 among the Phase 1a infra runbooks.
#
#   scripts/release/ship.sh <dev|prod>
#
# Inputs (environment): cloud-assistant: ALIBABA_CLOUD_ACCESS_KEY_ID / _SECRET (environment secrets), ALIYUN_CLI_VERSION (toolchain.sh),
# ECS_REGION, ECS_INSTANCE_ID, ECS_COMMAND_ID; ssh: DEPLOY_HOST, DEPLOY_USER, optional DEPLOY_PORT, DEPLOY_SSH_KEY, DEPLOY_KNOWN_HOSTS.
set -euo pipefail

env="${1:?usage: ship.sh <dev|prod>}"
fail() { echo "ship.sh: $*" >&2; exit 1; }
need() {
  local name missing=()
  for name in "$@"; do [ -n "${!name:-}" ] || missing+=("$name"); done
  [ "${#missing[@]}" -eq 0 ] || fail "environment $env lacks ${missing[*]} (RELEASE.md §7)"
}

INVOCATION_TIMEOUT_SECONDS=2700

case "$env" in dev|prod) ;; *) fail "environment must be dev or prod" ;; esac
trigger="${DEPLOY_TRIGGER:-}"
case "$trigger" in cloud-assistant|ssh) ;; *) fail "DEPLOY_TRIGGER must be cloud-assistant or ssh (got '${trigger}')" ;; esac

case "$trigger" in
  cloud-assistant)
    need ALIBABA_CLOUD_ACCESS_KEY_ID ALIBABA_CLOUD_ACCESS_KEY_SECRET ALIYUN_CLI_VERSION ECS_REGION ECS_INSTANCE_ID ECS_COMMAND_ID
    installed_cli="$(aliyun version 2>/dev/null || true)"
    [ "$installed_cli" = "$ALIYUN_CLI_VERSION" ] || fail "aliyun CLI $ALIYUN_CLI_VERSION required, found '${installed_cli:-none}'"
    # Credentials only through the environment; never `aliyun configure`, which would leave them in the user's home on the runner
    export ALIBABA_CLOUD_ACCESS_KEY_ID ALIBABA_CLOUD_ACCESS_KEY_SECRET
    invoke_id="$(aliyun ecs InvokeCommand --RegionId "$ECS_REGION" --CommandId "$ECS_COMMAND_ID" --InstanceId.1 "$ECS_INSTANCE_ID" \
      | python3 -I -c 'import json,sys; print(json.load(sys.stdin)["InvokeId"])')"
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
    ;;
  ssh)
    need DEPLOY_HOST DEPLOY_USER DEPLOY_SSH_KEY DEPLOY_KNOWN_HOSTS
    work="$(mktemp -d "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/forcitis-one-ship.XXXXXX")"
    trap 'rm -rf "$work"' EXIT
    (umask 077 && printf '%s\n' "$DEPLOY_SSH_KEY" > "$work/key" && printf '%s\n' "$DEPLOY_KNOWN_HOSTS" > "$work/known_hosts")
    ssh -F /dev/null -i "$work/key" -p "${DEPLOY_PORT:-22}" -l "$DEPLOY_USER" \
      -o BatchMode=yes -o IdentitiesOnly=yes -o IdentityAgent=none \
      -o StrictHostKeyChecking=yes -o UserKnownHostsFile="$work/known_hosts" -o GlobalKnownHostsFile=/dev/null \
      -o ConnectTimeout=20 -o ServerAliveInterval=30 \
      -- "$DEPLOY_HOST" install-pending < /dev/null
    ;;
esac
