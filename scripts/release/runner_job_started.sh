#!/usr/bin/env bash
# Pre-job hook of the dedicated runner forcitis-one-deploy (ACTIONS_RUNNER_HOOK_JOB_STARTED in the runner's .env). It admits only the
# release and deploy workflows of main, started by hand; any other job, from any branch or workflow file that names the runner's label,
# fails before its first step. On the GitHub Team plan a runner group cannot be limited to selected workflows (RELEASE.md §11), so the
# runner enforces it. Installed root-owned outside the runner directory, together with a root-owned .env, so no job can change it.
set -euo pipefail

ALLOWED_WORKFLOWS=(
  "FORCITIS/forcitis-one-psa/.github/workflows/release.yml@refs/heads/main"
  "FORCITIS/forcitis-one-psa/.github/workflows/deploy.yml@refs/heads/main"
)

refuse() { echo "forcitis-one-deploy runner: refusing this job: $*" >&2; exit 1; }

[ "${GITHUB_REPOSITORY:-}" = "FORCITIS/forcitis-one-psa" ] || refuse "repository '${GITHUB_REPOSITORY:-unset}'"
[ "${GITHUB_EVENT_NAME:-}" = "workflow_dispatch" ] || refuse "event '${GITHUB_EVENT_NAME:-unset}'; only manual runs are allowed"
[ "${GITHUB_REF:-}" = "refs/heads/main" ] || refuse "ref '${GITHUB_REF:-unset}'; only main is allowed"
allowed=false
for workflow in "${ALLOWED_WORKFLOWS[@]}"; do
  [ "${GITHUB_WORKFLOW_REF:-}" = "$workflow" ] && allowed=true
done
$allowed || refuse "workflow '${GITHUB_WORKFLOW_REF:-unset}' is not release.yml or deploy.yml on main"

# Processes of this user outside the runner are left over from an earlier job; report them (macOS starts some per-user services itself)
strays="$(ps -U "$(id -u)" -o pid=,comm= | awk -v self="$$" -v parent="$PPID" \
  '$1 != self && $1 != parent && $2 !~ /(Runner\.(Listener|Worker|PluginHost)|\/ps$|^ps$|awk|bash|cfprefsd|distnoted|trustd|secd|lsd|xpcproxy)/ { print }')"
if [ -n "$strays" ]; then
  echo "::warning title=forcitis-one-deploy runner::processes of $(id -un) left over from an earlier job: $(printf '%s' "$strays" | tr '\n' ';')"
fi
echo "forcitis-one-deploy runner: admitted $GITHUB_WORKFLOW_REF run $GITHUB_RUN_ID"
