#!/usr/bin/env bash
# Exercises the installer's deployment checks (remote_install.sh: github_auth, validate_deployment, processed_check) against canned
# GitHub API answers served by a stand-in curl; nothing reaches the network. Runs on macOS and Linux without root.
#
#   scripts/release/install_request_test.sh
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
scratch="$(mktemp -d)"
trap 'rm -rf "$scratch"' EXIT
REPO=FORCITIS/forcitis-one-psa
COMMIT=0123456789abcdef0123456789abcdef01234567
TAG_OBJECT=89abcdef0123456789abcdef0123456789abcdef

mkdir -p "$scratch/bin"
cat > "$scratch/bin/curl" <<'CURL'
#!/usr/bin/env bash
# Stand-in for curl: answers GitHub API paths from $FAKE_GITHUB, insists on the token arriving through a header file only
out="" url="" headers=""
while [ "$#" -gt 0 ]; do
  case "$1" in
    -o) out="$2"; shift ;;
    -H) case "$2" in @*) headers="${2#@}" ;; esac; shift ;;
    https://*) url="$1" ;;
  esac
  case "$1" in *github_pat_*) echo "token on the command line" >&2; exit 99 ;; esac
  shift
done
grep -q '^Authorization: Bearer github_pat_' "$headers" 2>/dev/null || { echo "no token header file" >&2; exit 98; }
case "$url" in
  */deployments\?*) file=deployments.json ;;
  */statuses\?*) file=statuses.json ;;
  */actions/runs/*) file=run.json ;;
  */git/ref/tags/*) file=ref.json ;;
  */git/tags/*) file=tag.json ;;
  *) exit 22 ;;
esac
[ -f "$FAKE_GITHUB/$file" ] || exit 22
cp "$FAKE_GITHUB/$file" "$out"
CURL
chmod +x "$scratch/bin/curl"

# Writes the answers of a valid request into $1, then applies the named change
canned() {
  python3 -I - "$1" "$2" "$REPO" "$COMMIT" "$TAG_OBJECT" <<'PY'
import json, os, sys
out, change, repo, commit, tag_object = sys.argv[1:]
deployments = [
    {"id": 900, "ref": "main", "sha": "f" * 40, "environment": "dev", "creator": {"login": "github-actions[bot]", "type": "Bot"}, "payload": {}},
    {"id": 901, "ref": "v0.1.0", "sha": commit, "environment": "dev", "creator": {"login": "github-actions[bot]", "type": "Bot"},
     "payload": {"version": "0.1.0", "run_id": 4242, "commit": commit}},
]
statuses = []
run = {"id": 4242, "display_title": "deploy 0.1.0 to dev", "path": ".github/workflows/deploy.yml", "head_branch": "main", "event": "workflow_dispatch", "status": "in_progress",
       "repository": {"full_name": repo}, "head_repository": {"full_name": repo}}
ref = {"ref": "refs/tags/v0.1.0", "object": {"type": "commit", "sha": commit}}
tag = {"object": {"type": "commit", "sha": commit}}
newest = deployments[1]
if change == "annotated-tag":
    ref["object"] = {"type": "tag", "sha": tag_object}
elif change == "creator":
    newest["creator"] = {"login": "someone", "type": "User"}
elif change == "run-path":
    run["path"] = ".github/workflows/other.yml"
elif change == "run-branch":
    run["head_branch"] = "feature"
elif change == "run-status":
    run["status"] = "completed"
elif change == "run-event":
    run["event"] = "push"
elif change == "run-repository":
    run["repository"] = {"full_name": "someone/fork"}
elif change == "tag-commit":
    ref["object"]["sha"] = "e" * 40
elif change == "annotated-tag-commit":
    ref["object"] = {"type": "tag", "sha": tag_object}
    tag["object"]["sha"] = "e" * 40
elif change == "payload-version":
    newest["payload"]["version"] = "0.2.0"
elif change == "ref-not-release-tag":
    newest["ref"] = "v0.1.0\n"
    newest["payload"]["version"] = "0.1.0\n"
elif change == "environment":
    newest["environment"] = "prod"
elif change == "run-other-environment":
    run["display_title"] = "deploy 0.1.0 to prod"
elif change == "run-other-version":
    run["display_title"] = "deploy 0.2.0 to dev"
elif change == "second-deployment-same-run":
    deployments.insert(1, {"id": 902, "ref": "v0.1.0", "sha": commit, "environment": "dev",
                           "creator": {"login": "github-actions[bot]", "type": "Bot"},
                           "payload": {"version": "0.1.0", "run_id": 4242, "commit": commit}})
elif change == "already-done":
    statuses = [{"state": "success"}]
elif change != "none":
    sys.exit(f"unknown change {change}")
os.makedirs(out, exist_ok=True)
for name, value in (("deployments", deployments), ("statuses", statuses), ("run", run), ("ref", ref), ("tag", tag)):
    with open(os.path.join(out, name + ".json"), "w") as f:
        json.dump(value, f)
PY
}

# Runs the checks in a subshell; prints the installer's log and returns its status
attempt() {
  local case_dir="$1" token_mode="$2" expires="$3" processed_before="$4"
  (
    PATH="$scratch/bin:$PATH"
    export FAKE_GITHUB="$case_dir/github"
    # shellcheck source=scripts/release/remote_install.sh
    source "$here/remote_install.sh"
    if stat -c %a / >/dev/null 2>&1; then
      owner_mode() { stat -c '%U %a' "$1" | sed "s/^$(id -un) /root /"; }
    else
      owner_mode() { stat -f '%Su %Lp' "$1" | sed "s/^$(id -un) /root /"; }
    fi
    GITHUB_TOKEN_FILE="$case_dir/token"
    GITHUB_TOKEN_EXPIRES="$case_dir/token.expires"
    RUN_DIR="$case_dir/run"
    STATE_DIR="$case_dir/state"
    PROCESSED="$STATE_DIR/processed-deployments"
    (umask 077 && printf 'github_pat_%s\n' "$(python3 -I -c 'import secrets; print(secrets.token_hex(20))')" > "$GITHUB_TOKEN_FILE")
    chmod "$token_mode" "$GITHUB_TOKEN_FILE"
    echo "$expires" > "$GITHUB_TOKEN_EXPIRES"
    chmod 0644 "$GITHUB_TOKEN_EXPIRES"
    if [ "$processed_before" = yes ]; then
      mkdir -m 0700 "$STATE_DIR" && (umask 077 && printf '901\t2026-10-14T00:00:00Z\tstarted\tv0.1.0\n' > "$PROCESSED")
    fi
    target_env=dev
    repo="$REPO"
    github_auth
    validate_deployment
    processed_check "$deployment_id"
    echo "ACCEPTED deployment=$deployment_id version=$version run=$run_id commit=$commit"
  ) > "$case_dir/out" 2>&1
}

future="$(python3 -I -c 'import datetime; print(datetime.date.today() + datetime.timedelta(days=60))')"
past="$(python3 -I -c 'import datetime; print(datetime.date.today() - datetime.timedelta(days=1))')"
today="$(date -u +%Y-%m-%d)"
passed=0 failed=0

# expect: accept, or reject:<text the refusal must contain>
check() {
  local name="$1" change="$2" expect="$3" token_mode="${4:-0600}" expires="${5:-$future}" processed_before="${6:-no}" dir status rc
  dir="$scratch/$name"
  mkdir -p "$dir"
  canned "$dir/github" "$change"
  # Not in an if: errexit, which the sourced installer sets, must stay in force inside the attempt
  set +e
  attempt "$dir" "$token_mode" "$expires" "$processed_before"
  rc=$?
  if [ "$rc" -eq 0 ]; then status=accepted; else status=rejected; fi
  set -e
  if { [ "$expect" = accept ] && [ "$status" = accepted ]; } \
     || { [ "$expect" != accept ] && [ "$status" = rejected ] && grep -qF -- "${expect#reject:}" "$dir/out"; }; then
    passed=$((passed + 1)); echo "PASS  $name: $status — $(grep -E 'ACCEPTED|FAILED' "$dir/out" | tail -1 | cut -c1-160)"
  else
    failed=$((failed + 1)); echo "FAIL  $name: $status, expected $expect"; sed 's/^/      /' "$dir/out"
  fi
}

check valid-request                none                  accept
check valid-annotated-tag          annotated-tag         accept
check creator-not-actions-bot      creator               "reject:not by a workflow run"
check run-not-deploy-yml           run-path              "reject:path is '.github/workflows/other.yml'"
check run-not-on-main              run-branch            "reject:head_branch is 'feature'"
check run-not-in-progress          run-status            "reject:status is 'completed'"
check run-event-push               run-event             "reject:event is 'push'"
check run-for-other-environment    run-other-environment "reject:display_title is 'deploy 0.1.0 to prod'"
check run-for-other-version        run-other-version     "reject:display_title is 'deploy 0.2.0 to dev'"
check two-deployments-same-run     second-deployment-same-run "reject:all name run 4242"
check run-other-repository         run-repository        "reject:repository is not $REPO"
check tag-commit-mismatch          tag-commit            "reject:points at eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee, the deployment names"
check annotated-tag-commit-mismatch annotated-tag-commit "reject:points at eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee, the deployment names"
check payload-version-not-ref      payload-version       "reject:payload version '0.2.0' differs from its ref v0.1.0"
check ref-not-release-tag          ref-not-release-tag   "reject:not a release tag v<version>"
check environment-mismatch        environment           "reject:is for environment 'prod', this server is dev"
check newest-already-finished      already-done          "reject:nothing to deploy"
check token-expired                none                  "reject:expired on $past" 0600 "$past"
check token-expires-today          none                  "reject:expired on $today" 0600 "$today"
check token-wrong-mode             none                  "reject:must exist, root-owned 0600" 0644
check deployment-already-processed none                  "reject:deployment 901 was already taken up" 0600 "$future" yes

echo "$passed passed, $failed failed"
[ "$failed" -eq 0 ]
