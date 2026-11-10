#!/bin/bash
# Pre-job hook of the dedicated runner forcitis-one-deploy, set as ACTIONS_RUNNER_HOOK_JOB_STARTED in the root-owned LaunchDaemon plist.
# It admits only the deploy workflow of main, started by hand (the release build runs on GitHub-hosted machines); any other job, from any
# branch or workflow file that names the runner's label, fails before its first step. On the GitHub Team plan a runner group cannot be limited to selected workflows
# (RELEASE.md §11), so the runner enforces it. Installed root-owned outside the runner directory; it runs only root-owned system tools.
#
# The variables are cross-checked with the event payload the runner wrote for this job, which must lie in this runner's own _temp, so a
# workflow that sets GITHUB_REF or GITHUB_WORKFLOW_REF in its env still meets the payload of what really triggered it.
set -euo pipefail
export PATH=/usr/bin:/bin:/usr/sbin:/sbin

RUNNER_WORK=/opt/forcitis-one-runner/_work
REPOSITORY=FORCITIS/forcitis-one-psa
ALLOWED_WORKFLOW_FILES=(.github/workflows/deploy.yml)

refuse() { echo "forcitis-one-deploy runner: refusing this job: $*" >&2; exit 1; }

event_path="$(/bin/realpath "${GITHUB_EVENT_PATH:-/nonexistent}" 2>/dev/null)" || refuse "no event payload"
case "$event_path" in "$RUNNER_WORK"/_temp/*) ;; *) refuse "event payload outside the runner's _temp" ;; esac

workflow_file=""
for file in "${ALLOWED_WORKFLOW_FILES[@]}"; do
  [ "${GITHUB_WORKFLOW_REF:-}" = "$REPOSITORY/$file@refs/heads/main" ] && workflow_file="$file"
done
[ -n "$workflow_file" ] || refuse "workflow '${GITHUB_WORKFLOW_REF:-unset}' is not deploy.yml on main"
[ "${GITHUB_REPOSITORY:-}" = "$REPOSITORY" ] || refuse "repository '${GITHUB_REPOSITORY:-unset}'"
[ "${GITHUB_EVENT_NAME:-}" = workflow_dispatch ] || refuse "event '${GITHUB_EVENT_NAME:-unset}'; only manual runs are allowed"
[ "${GITHUB_REF:-}" = refs/heads/main ] || refuse "ref '${GITHUB_REF:-unset}'; only main is allowed"

/usr/bin/python3 -I - "$event_path" "$REPOSITORY" "$workflow_file" <<'PY' || refuse "the event payload does not match the job's variables"
import json, sys
event = json.load(open(sys.argv[1]))
ok = (event.get("ref") == "refs/heads/main" and event.get("workflow") == sys.argv[3]
      and event.get("repository", {}).get("full_name") == sys.argv[2] and "inputs" in event)
sys.exit(0 if ok else 1)
PY

echo "forcitis-one-deploy runner: admitted $GITHUB_WORKFLOW_REF run ${GITHUB_RUN_ID:-?}"
