#!/usr/bin/env bash
# FORCITIS one release installer on the ECS. Installed as /usr/local/sbin/forcitis-one-release (root:root 0755) by the one-time setup in
# RELEASE.md (Phase 1a infra runbooks). Run as root by a fixed, parameterless Cloud Assistant command, or through a deploy key whose
# forced command is this script (the key never gets a shell). It takes nothing from its caller: the environment comes from this server's
# own configuration, what to install is the newest pending GitHub deployment that a deploy.yml run on main created for that environment,
# read with a read-only token, and the release is fetched from GitHub by this server and only installed when its signed statement
# verifies against the signer key on this server.
#
#   install-pending              install the release of the newest pending deployment for this server's environment
#   rollback [<version>]         back to the previous release, or to <version> (local only, no GitHub access)
#   status
#
# Every step fails loudly; a release that does not report healthy is switched back to the release that ran before.
set -euo pipefail
umask 027

SELF=/usr/local/sbin/forcitis-one-release
BASE=/data/forcitis-one
ETC=/etc/forcitis-one
MIGRATION_CREDENTIALS="$ETC/migrate"
RELEASE_SIGNERS="$ETC/release-signers"
# root 0644: ENVIRONMENT (dev|prod), GITHUB_REPOSITORY (owner/name), optional WECOM_VERIFY_FILE (a pinned file that must exist)
RELEASE_SOURCE="$ETC/release-source"
# Fine-grained read-only GitHub token (root 0600) and its expiry date YYYY-MM-DD (root 0644), RELEASE.md §7 R-13
GITHUB_TOKEN_FILE="$ETC/github-release-token"
GITHUB_TOKEN_EXPIRES="$ETC/github-release-token.expires"
GITHUB_API=https://api.github.com
DEPLOY_WORKFLOW_PATH=.github/workflows/deploy.yml
DEPLOYMENT_CREATOR='github-actions[bot]'
API_TIMEOUT_SECONDS=60
# Every deployment id taken up, with its outcome (root 0600, appended only); an id listed here is never installed again
STATE_DIR=/var/lib/forcitis-one-release
PROCESSED="$STATE_DIR/processed-deployments"
RUN_DIR=/run/forcitis-one-release
# Files nginx serves outside the releases, untouched by any deployment (WeCom trusted-domain verification, C-4 E-10)
PINNED="$BASE/static-pinned"
PINNED_PATTERN='^WW_verify_[A-Za-z0-9]{1,64}\.txt$'
PUBLIC_HOST=one.forcitis.com
STATEMENT_NAMESPACE=forcitis-one-release
STATEMENT_FORMAT=forcitis-one-release-statement/1
RELEASE_WORKFLOW=FORCITIS/forcitis-one-psa/.github/workflows/release.yml@refs/heads/main
RELEASE_LOGS=/var/log/forcitis-one-release
LOCK=/run/forcitis-one-release.lock
UNIT=forcitis-one
APP_USER=forcitis
MIGRATION_USER=forcitis-migrate
APPLICATION_CLASS=OneApplication
MIGRATE_PROFILE_RESOURCE=BOOT-INF/classes/application-migrate.properties
VERSION_PATTERN='^[0-9]+\.[0-9]+\.[0-9]+(-rc\.[0-9]+)?$'
MAX_BUNDLE_BYTES=$((1024 * 1024 * 1024))
DOWNLOAD_TIMEOUT_SECONDS=1200
HEALTH_TIMEOUT_SECONDS=180
MIGRATION_TIMEOUT_SECONDS=900
# The application refuses to start unless the JVM default zone is UTC (kernel.UtcJvm); every java launch here passes it
JVM_TIMEZONE=-Duser.timezone=UTC
KEEP_RELEASES=5
# Property files a migration may only see on the very first installation (Flowable engine tables, see RELEASE.md §4)
FIRST_INSTALL_ONLY='^(spring\.datasource\..+|flowable\.database-schema-update)$'

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" >&2; }
fail() {
  log "FAILED: $*"
  [ -z "${deployment_in_progress:-}" ] || record_deployment "$deployment_in_progress" failed "$*"
  exit 1
}

cleanup_paths=()
cleanup() { [ "${#cleanup_paths[@]}" -eq 0 ] || rm -rf "${cleanup_paths[@]}"; }
trap cleanup EXIT

owner_mode() { stat -c '%U %a' "$1"; }

releases="$BASE/releases"
current_link="$BASE/current"
previous_link="$BASE/previous"

valid_version() { [[ "$1" =~ $VERSION_PATTERN ]]; }
# True when $1 is a lower version than $2 (MAJOR.MINOR.PATCH, a -rc.N pre-release before its release)
version_lower() {
  python3 -I - "$1" "$2" <<'PY'
import re, sys
def key(v):
    m = re.fullmatch(r"(\d+)\.(\d+)\.(\d+)(?:-rc\.(\d+))?", v)
    major, minor, patch, rc = m.groups()
    return (int(major), int(minor), int(patch), int(rc) if rc else float("inf"))
sys.exit(0 if key(sys.argv[1]) < key(sys.argv[2]) else 1)
PY
}
env_value() {
  local key="$1" line
  line="$(grep -E "^${key}=" "$ETC/one.env" | tail -1 || true)"
  line="${line#*=}"
  line="${line#\'}"; line="${line%\'}"
  printf '%s' "$line"
}
linked_version() {
  if [ -L "$1" ]; then basename "$(readlink "$1")"; fi
}

# root acts on these paths; the application user must not be able to swap anything in them
assert_root_owned() {
  local path owner mode
  for path in "$@"; do
    [[ -d "$path" && ! -L "$path" ]] || fail "$path must be a real directory"
    owner="$(stat -c %U "$path")"; mode="$(stat -c %a "$path")"
    [ "$owner" = root ] || fail "$path must be owned by root (is $owner); see C-4 E-5"
    (( (8#$mode & 8#022) == 0 )) || fail "$path must not be writable by group or others (mode $mode); see C-4 E-5"
  done
}

# Every pinned verification file still answers 200 with its own content through nginx on this host
pinned_served() {
  local file name
  for file in "$PINNED"/WW_verify_*.txt; do
    [ -e "$file" ] || continue
    name="$(basename "$file")"
    curl -fsS --max-time 5 --resolve "$PUBLIC_HOST:443:127.0.0.1" "https://$PUBLIC_HOST/$name" 2>/dev/null | cmp -s - "$file" || {
      log "pinned file $name is not served as it is on disk"
      return 1
    }
  done
}

health_ok() {
  local port deadline body
  port="$(env_value MANAGEMENT_SERVER_PORT)"
  port="${port:-8081}"
  deadline=$((SECONDS + HEALTH_TIMEOUT_SECONDS))
  while [ "$SECONDS" -lt "$deadline" ]; do
    if systemctl is-active --quiet "$UNIT"; then
      body="$(curl -fsS --max-time 5 "http://127.0.0.1:${port}/actuator/health" 2>/dev/null || true)"
      if [ -n "$body" ] && printf '%s' "$body" | python3 -I -c 'import json,sys; sys.exit(0 if json.load(sys.stdin).get("status") == "UP" else 1)' 2>/dev/null \
         && pinned_served; then
        return 0
      fi
    fi
    if systemctl is-failed --quiet "$UNIT"; then
      return 1
    fi
    sleep 3
  done
  return 1
}

switch_to() {
  local version="$1"
  [ -d "$releases/$version" ] || fail "release $version is not installed"
  if [ "$(systemctl is-enabled "$UNIT" 2>/dev/null || true)" != enabled ]; then
    systemctl enable "$UNIT" || fail "could not enable $UNIT"
    log "enabled $UNIT"
  fi
  ln -sfn "releases/$version" "$BASE/.current.next"
  mv -T "$BASE/.current.next" "$current_link"
  systemctl restart "$UNIT"
}

record() { printf '%s\t%s\t%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$1" "$2" >> "$releases/HISTORY"; }

activate() {
  local version="$1" before
  before="$(linked_version "$current_link")"
  log "switching current: ${before:-none} -> $version"
  switch_to "$version"
  if health_ok; then
    [ -z "$before" ] || [ "$before" = "$version" ] || ln -sfn "releases/$before" "$previous_link"
    record "$version" "active"
    log "release $version is healthy"
    return 0
  fi
  record "$version" "unhealthy"
  if [ -z "$before" ] || [ "$before" = "$version" ]; then
    systemctl stop "$UNIT" || true
    fail "release $version did not become healthy and there is no earlier release to return to; the service is stopped"
  fi
  log "release $version did not become healthy within ${HEALTH_TIMEOUT_SECONDS}s; switching back to $before"
  switch_to "$before"
  if health_ok; then
    record "$before" "restored"
    fail "release $version was unhealthy; $before is running again (see journalctl -u $UNIT and $BASE/logs)"
  fi
  record "$before" "unhealthy"
  fail "release $version was unhealthy and $before did not recover either; manual action needed"
}

check_tar_entries() {
  local archive="$1" top="$2" entry
  while IFS= read -r entry; do
    case "$entry" in
      "$top"|"$top/"|"$top"/*) ;;
      *) fail "bundle entry outside $top/: $entry" ;;
    esac
    case "/$entry/" in */../*) fail "bundle entry with ..: $entry" ;; esac
  done < <(tar -tzf "$archive")
  if tar -tvzf "$archive" | grep -Eq '^[lhbcp]'; then
    fail "bundle contains links or special files"
  fi
}

verify_bundle_dir() {
  local dir="$1" listed actual
  (cd "$dir" && sha256sum --quiet --strict -c SHA256SUMS) || fail "SHA256SUMS does not match the bundle contents"
  listed="$(cut -c67- "$dir/SHA256SUMS" | LC_ALL=C sort)"
  actual="$(cd "$dir" && find . -type f ! -name SHA256SUMS | sed 's|^\./||' | LC_ALL=C sort)"
  [ "$listed" = "$actual" ] || fail "bundle has files not covered by SHA256SUMS"
}

statement_field() { awk -v key="$1:" '$1 == key { print $2; found = 1 } END { exit !found }' "$2"; }

# Same checks as scripts/release/statement.sh verify, against the signer key installed on this server (never one from the bundle)
verify_statement() {
  local archive="$1" statement="$2" version="$3" commit="$4" name="forcitis-one-$3"
  [ -s "$RELEASE_SIGNERS" ] && [ "$(stat -c '%U %a' "$RELEASE_SIGNERS")" = "root 644" ] \
    || fail "$RELEASE_SIGNERS must exist, root-owned 0644 (RELEASE.md §7)"
  ssh-keygen -Y verify -f "$RELEASE_SIGNERS" -I "$STATEMENT_NAMESPACE" -n "$STATEMENT_NAMESPACE" -s "$statement.sig" < "$statement" >/dev/null 2>&1 \
    || fail "the release statement's signature does not verify"
  [ "$(statement_field format "$statement")" = "$STATEMENT_FORMAT" ] || fail "unknown release statement format"
  [ "$(statement_field version "$statement")" = "$version" ] || fail "release statement is for another version"
  [ "$(statement_field bundle "$statement")" = "$name.tar.gz" ] || fail "release statement names another bundle"
  [ "$(statement_field commit "$statement")" = "$commit" ] || fail "release statement is for another commit"
  [ "$(statement_field workflow "$statement")" = "$RELEASE_WORKFLOW" ] || fail "release statement was not produced by the release workflow on main"
  [ "$(statement_field bundle-sha256 "$statement")" = "$(sha256sum "$archive" | cut -d' ' -f1)" ] || fail "bundle differs from the signed release statement"
  log "bundle matches the release statement signed by release run $(statement_field run "$statement")"
}

# After check_tar_entries: the members the statement names
verify_statement_members() {
  local archive="$1" statement="$2" name="forcitis-one-$3"
  [ "$(statement_field manifest-sha256 "$statement")" = "$(tar -xzOf "$archive" "$name/manifest.json" | sha256sum | cut -d' ' -f1)" ] \
    || fail "manifest.json differs from the signed release statement"
  [ "$(statement_field sha256sums-sha256 "$statement")" = "$(tar -xzOf "$archive" "$name/SHA256SUMS" | sha256sum | cut -d' ' -f1)" ] \
    || fail "SHA256SUMS differs from the signed release statement"
}

manifest_field() { python3 -I -c 'import json,sys; print(json.load(open(sys.argv[1]))[sys.argv[2]])' "$1" "$2"; }

check_server_files() {
  local dir="$1" drift=()
  cmp -s "$dir/deploy/remote_install.sh" "$SELF" || drift+=("$SELF")
  cmp -s "$dir/deploy/forcitis-one.service" /etc/systemd/system/forcitis-one.service || drift+=(/etc/systemd/system/forcitis-one.service)
  cmp -s "$dir/deploy/nginx-one.conf" /etc/nginx/conf.d/one.conf || drift+=(/etc/nginx/conf.d/one.conf)
  cmp -s "$dir/deploy/forcitis-one.logrotate" /etc/logrotate.d/forcitis-one || drift+=(/etc/logrotate.d/forcitis-one)
  [ "${#drift[@]}" -eq 0 ] || fail "server files differ from this release; apply them as in RELEASE.md §5 first: ${drift[*]}"
  grep -Eq "^ExecStart=/usr/bin/java .*${JVM_TIMEZONE}( |$)" /etc/systemd/system/forcitis-one.service \
    || fail "forcitis-one.service does not start the JVM with ${JVM_TIMEZONE}; the application would refuse to start"

  local missing=() key
  while IFS= read -r key; do
    grep -qE "^${key}=" "$ETC/one.env" || missing+=("$key")
  done < <(grep -oE '^[A-Z][A-Z0-9_]*=' "$dir/deploy/one.env.template" | tr -d '=')
  [ "${#missing[@]}" -eq 0 ] || fail "$ETC/one.env lacks keys this release expects (add them, empty if unused): ${missing[*]}"
}

grant_static_read() {
  local dir="$1"
  setfacl -m u:nginx:--x "$dir"
  setfacl -R -m u:nginx:r-X "$dir"/web-*
}

source_value() {
  local key="$1" line
  line="$(grep -E "^${key}=" "$RELEASE_SOURCE" | tail -1 || true)"
  printf '%s' "${line#*=}"
}

# Sets target_env, repo and pinned_expected from this server's release configuration
read_release_source() {
  [ -s "$RELEASE_SOURCE" ] && [ "$(owner_mode "$RELEASE_SOURCE")" = "root 644" ] \
    || fail "$RELEASE_SOURCE must exist, root-owned 0644 (RELEASE.md §7 R-7)"
  target_env="$(source_value ENVIRONMENT)"
  repo="$(source_value GITHUB_REPOSITORY)"
  pinned_expected="$(source_value WECOM_VERIFY_FILE)"
  case "$target_env" in dev|prod) ;; *) fail "$RELEASE_SOURCE must set ENVIRONMENT to dev or prod" ;; esac
  [ "$repo" = "${RELEASE_WORKFLOW%%/.github/*}" ] \
    || fail "$RELEASE_SOURCE must set GITHUB_REPOSITORY to ${RELEASE_WORKFLOW%%/.github/*}, the repository whose release workflow signs"
  [[ -z "$pinned_expected" || "$pinned_expected" =~ $PINNED_PATTERN ]] \
    || fail "$RELEASE_SOURCE: WECOM_VERIFY_FILE must name a WW_verify_*.txt file or be absent"
}

# Writes the Authorization header to a root-only file for curl -H @file, so the token never reaches a command line or the log
github_auth() {
  local expires token=""
  [ -f "$GITHUB_TOKEN_FILE" ] && [ "$(owner_mode "$GITHUB_TOKEN_FILE")" = "root 600" ] \
    || fail "$GITHUB_TOKEN_FILE must exist, root-owned 0600 (RELEASE.md §7 R-13)"
  [ -f "$GITHUB_TOKEN_EXPIRES" ] && [ "$(owner_mode "$GITHUB_TOKEN_EXPIRES")" = "root 644" ] \
    || fail "$GITHUB_TOKEN_EXPIRES must exist, root-owned 0644 (RELEASE.md §7 R-13)"
  expires="$(tr -d '[:space:]' < "$GITHUB_TOKEN_EXPIRES")"
  [[ "$expires" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]] || fail "$GITHUB_TOKEN_EXPIRES must hold the token's expiry date as YYYY-MM-DD"
  [[ "$(date -u +%Y-%m-%d)" < "$expires" ]] \
    || fail "the GitHub release token expired on $expires; rotate it (RELEASE.md §7 R-13)"
  IFS= read -r token < "$GITHUB_TOKEN_FILE" || true
  [[ "$token" =~ ^github_pat_[A-Za-z0-9_]{20,255}$ ]] || fail "$GITHUB_TOKEN_FILE does not hold a fine-grained token"
  [ -d "$RUN_DIR" ] || mkdir -m 0700 "$RUN_DIR"
  github_headers="$(mktemp "$RUN_DIR/headers.XXXXXX")"
  cleanup_paths+=("$github_headers")
  printf 'Authorization: Bearer %s\n' "$token" > "$github_headers"
}

github_get() {
  local path="$1" out="$2"
  curl -fsS --proto '=https' --tlsv1.2 --max-time "$API_TIMEOUT_SECONDS" -H @"$github_headers" \
    -H 'Accept: application/vnd.github+json' -H 'X-GitHub-Api-Version: 2022-11-28' -o "$out" "$GITHUB_API$path" \
    || fail "GitHub API GET $path failed"
}

# The asset API answers with a redirect to a short-lived signed URL; curl sends -H Authorization only to the original host
github_download() {
  local url="$1" out="$2"
  timeout "$DOWNLOAD_TIMEOUT_SECONDS" curl -fsS --proto '=https' --proto-redir '=https' --tlsv1.2 -L --max-redirs 3 \
    --max-filesize "$MAX_BUNDLE_BYTES" -H @"$github_headers" -H 'Accept: application/octet-stream' -o "$out" "$url" \
    || fail "could not download $url"
}

# Checks on GitHub API responses; each mode prints what the next step needs or exits non-zero with the reason
github_check() {
  python3 -I - "$@" <<'PY'
import json, re, sys
mode, args = sys.argv[1], sys.argv[2:]
def load(path):
    with open(path) as f:
        return json.load(f)
def die(message):
    sys.exit(message)

if mode == "newest":
    deployments = load(args[0])
    if not isinstance(deployments, list) or not deployments:
        die(f"no deployment for environment {args[1]}; deployments are created by deploy.yml")
    print(max(deployments, key=lambda d: d["id"])["id"])

elif mode == "deployment":
    deployments, statuses = load(args[0]), load(args[1])
    deployment_id, env, version_pattern, expected_creator = int(args[2]), args[3], args[4], args[5]
    d = next(d for d in deployments if d["id"] == deployment_id)
    if statuses:
        state = statuses[0].get("state")
        if state in ("success", "failure", "error", "inactive"):
            die(f"nothing to deploy: the newest deployment {deployment_id} for {env} is already {state}")
    if d.get("environment") != env:
        die(f"deployment {deployment_id} is for environment {d.get('environment')!r}, this server is {env}")
    creator = d.get("creator") or {}
    if creator.get("login") != expected_creator or creator.get("type") != "Bot":
        die(f"deployment {deployment_id} was created by {creator.get('login')!r}, not by a workflow run")
    ref = d.get("ref")
    if not isinstance(ref, str) or not ref.startswith("v") or not re.fullmatch(version_pattern.lstrip("^").rstrip("$"), ref[1:]):
        die(f"deployment {deployment_id} has ref {ref!r}, not a release tag v<version>")
    payload = d.get("payload")
    if not isinstance(payload, dict):
        die(f"deployment {deployment_id} has no payload object")
    version, run_id, commit = payload.get("version"), payload.get("run_id"), payload.get("commit")
    if version != ref[1:]:
        die(f"deployment {deployment_id}: payload version {version!r} differs from its ref {ref}")
    if type(run_id) is not int or run_id <= 0:
        die(f"deployment {deployment_id}: payload run_id is missing or not a run id")
    if not isinstance(commit, str) or not re.fullmatch(r"[0-9a-f]{40}", commit):
        die(f"deployment {deployment_id}: payload commit is missing or not a full sha")
    if d.get("sha") != commit:
        die(f"deployment {deployment_id}: payload commit {commit} differs from the deployment's commit {d.get('sha')}")
    # One deploy run creates exactly one deployment; a second one naming the same run was not made by that run's deploy job
    same_run = [o["id"] for o in deployments if isinstance(o.get("payload"), dict) and o["payload"].get("run_id") == run_id]
    if len(same_run) != 1:
        die(f"deployments {same_run} all name run {run_id}; a deploy run creates one, start a new run instead of re-running")
    print(deployment_id, version, run_id, commit)

elif mode == "run":
    run, run_id, repo, workflow_path, version, env = load(args[0]), int(args[1]), args[2], args[3], args[4], args[5]
    # display_title is deploy.yml's run-name, which carries the run's inputs: the run must be deploying this version to this environment
    expected = {"id": run_id, "path": workflow_path, "head_branch": "main", "event": "workflow_dispatch", "status": "in_progress",
                "display_title": f"deploy {version} to {env}"}
    for key, value in expected.items():
        if run.get(key) != value:
            die(f"workflow run {run_id}: {key} is {run.get(key)!r}, expected {value!r}")
    for key in ("repository", "head_repository"):
        if (run.get(key) or {}).get("full_name") != repo:
            die(f"workflow run {run_id}: {key} is not {repo}")

elif mode == "ref":
    ref, expected = load(args[0]), args[1]
    if ref.get("ref") != expected:
        die(f"{expected} does not exist")
    print(ref["object"]["type"], ref["object"]["sha"])

elif mode == "tag":
    tag = load(args[0])
    print(tag["object"]["type"], tag["object"]["sha"])

elif mode == "release":
    release, version, repo, max_bytes = load(args[0]), args[1], args[2], int(args[3])
    if release.get("tag_name") != f"v{version}" or release.get("draft") is not False:
        die(f"release v{version} is missing or still a draft")
    top = f"forcitis-one-{version}"
    asset_url = re.compile(rf"https://api\.github\.com/repos/{re.escape(repo)}/releases/assets/[0-9]+")
    for name in (f"{top}.tar.gz", f"{top}.tar.gz.sha256", f"{top}.release", f"{top}.release.sig"):
        found = [a for a in release.get("assets", []) if a.get("name") == name]
        if len(found) != 1:
            die(f"release v{version} has {len(found)} assets named {name}, expected exactly one")
        asset = found[0]
        if asset.get("state") != "uploaded" or not isinstance(asset.get("size"), int) or asset["size"] > max_bytes:
            die(f"release asset {name} is not uploaded or larger than {max_bytes} bytes")
        if not isinstance(asset.get("url"), str) or not asset_url.fullmatch(asset["url"]):
            die(f"release asset {name} has an unexpected url")
        print(name, asset["url"])

else:
    die(f"unknown check {mode}")
PY
}

# Sets deployment_id, version, run_id and commit from the newest deployment for target_env, after every check passed
validate_deployment() {
  local work out ref_type ref_sha depth
  work="$(mktemp -d)"
  cleanup_paths+=("$work")
  github_get "/repos/$repo/deployments?environment=$target_env&per_page=5" "$work/deployments.json"
  deployment_id="$(github_check newest "$work/deployments.json" "$target_env")" || fail "no deployment to install"
  github_get "/repos/$repo/deployments/$deployment_id/statuses?per_page=1" "$work/statuses.json"
  out="$(github_check deployment "$work/deployments.json" "$work/statuses.json" "$deployment_id" "$target_env" "$VERSION_PATTERN" "$DEPLOYMENT_CREATOR")" \
    || fail "deployment $deployment_id is not acceptable"
  read -r deployment_id version run_id commit <<< "$out"
  valid_version "$version" || fail "invalid version '$version'"

  github_get "/repos/$repo/actions/runs/$run_id" "$work/run.json"
  github_check run "$work/run.json" "$run_id" "$repo" "$DEPLOY_WORKFLOW_PATH" "$version" "$target_env" \
    || fail "deployment $deployment_id does not come from a running deploy.yml run on main"

  github_get "/repos/$repo/git/ref/tags/v$version" "$work/ref.json"
  out="$(github_check ref "$work/ref.json" "refs/tags/v$version")" || fail "tag v$version cannot be resolved"
  read -r ref_type ref_sha <<< "$out"
  depth=0
  while [ "$ref_type" = tag ]; do
    depth=$((depth + 1))
    [ "$depth" -le 3 ] || fail "tag v$version is nested too deeply"
    [[ "$ref_sha" =~ ^[0-9a-f]{40}$ ]] || fail "tag v$version points at a malformed object"
    github_get "/repos/$repo/git/tags/$ref_sha" "$work/tag.json"
    out="$(github_check tag "$work/tag.json")" || fail "tag object $ref_sha cannot be read"
    read -r ref_type ref_sha <<< "$out"
  done
  [ "$ref_type" = commit ] || fail "tag v$version does not point at a commit"
  [ "$ref_sha" = "$commit" ] || fail "tag v$version points at $ref_sha, the deployment names $commit"
  log "deployment $deployment_id: v$version ($commit) for $target_env from deploy run $run_id"
}

# Refuses a deployment id that was taken up before
processed_check() {
  local id="$1"
  [ -d "$STATE_DIR" ] || mkdir -m 0700 "$STATE_DIR"
  [ "$(owner_mode "$STATE_DIR")" = "root 700" ] || fail "$STATE_DIR must be root-owned 0700"
  if [ -e "$PROCESSED" ]; then
    [ "$(owner_mode "$PROCESSED")" = "root 600" ] || fail "$PROCESSED must be root-owned 0600"
    if awk -F'\t' -v id="$id" '$1 == id { found = 1 } END { exit !found }' "$PROCESSED"; then
      fail "deployment $id was already taken up (see $PROCESSED); every deploy run creates a new deployment"
    fi
  else
    (umask 077 && : > "$PROCESSED")
  fi
}

record_deployment() {
  printf '%s\t%s\t%s\t%s\n' "$1" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$2" "${3:-}" >> "$PROCESSED" || log "could not record deployment $1 as $2"
}

place() {
  local version="$1" env="$2" commit="$3" incoming archive top staged existing assets name url out expected_sha
  incoming="$(mktemp -d "$releases/.incoming.XXXXXX")"
  cleanup_paths+=("$incoming")
  top="forcitis-one-$version"
  archive="$incoming/bundle.tar.gz"
  log "fetching $top from the GitHub release v$version"
  github_get "/repos/$repo/releases/tags/v$version" "$incoming/release.json"
  assets="$(github_check release "$incoming/release.json" "$version" "$repo" "$MAX_BUNDLE_BYTES")" || fail "release v$version cannot be installed"
  while read -r name url; do
    case "$name" in
      "$top.tar.gz") out="$archive" ;;
      "$top.tar.gz.sha256") out="$incoming/bundle.sha256" ;;
      "$top.release") out="$incoming/statement" ;;
      "$top.release.sig") out="$incoming/statement.sig" ;;
      *) fail "unexpected release asset $name" ;;
    esac
    github_download "$url" "$out"
  done <<< "$assets"
  [ "$(stat -c %s "$archive")" -le "$MAX_BUNDLE_BYTES" ] || fail "bundle larger than $MAX_BUNDLE_BYTES bytes"
  log "fetched $(stat -c %s "$archive") bytes"
  expected_sha="$(cut -d' ' -f1 "$incoming/bundle.sha256")"
  [[ "$expected_sha" =~ ^[0-9a-f]{64}$ ]] || fail "cannot read the bundle's .sha256 asset"
  [ "$(sha256sum "$archive" | cut -d' ' -f1)" = "$expected_sha" ] || fail "bundle sha256 differs from the release's .sha256 asset"

  verify_statement "$archive" "$incoming/statement" "$version" "$commit"
  check_tar_entries "$archive" "$top"
  verify_statement_members "$archive" "$incoming/statement" "$version"
  tar -xzf "$archive" -C "$incoming" --no-same-owner --no-same-permissions
  staged="$incoming/$top"
  verify_bundle_dir "$staged"
  [ "$(manifest_field "$staged/manifest.json" version)" = "$version" ] || fail "manifest.json is not for version $version"
  [ "$(manifest_field "$staged/manifest.json" gitSha)" = "$commit" ] || fail "manifest.json is not for commit $commit"
  [ -d "$staged/web-$env" ] || fail "bundle has no static files for $env"

  existing="$releases/$version"
  if [ -d "$existing" ]; then
    cmp -s "$existing/manifest.json" "$staged/manifest.json" \
      || fail "release $version is already installed with different contents; publish a new version instead"
    [ "$(readlink "$existing/web")" = "web-$env" ] || fail "release $version is installed for another environment"
    # Server files were checked when this version was first installed; re-checking would block returning to an older release
    log "release $version already installed with identical contents"
  else
    check_server_files "$staged"
    chown -R root:"$APP_USER" "$staged"
    find "$staged" -type d -exec chmod 0750 {} +
    find "$staged" -type f -exec chmod 0640 {} +
    ln -s "web-$env" "$staged/web"
    mv "$staged" "$existing"
    grant_static_read "$existing"
  fi
  rm -rf "$incoming"
}

# Prints the migration files of <new> that <old> does not have; fails when a file both know changed (migrations are append-only).
new_migrations() {
  local old="$1" new="$2"
  [ -f "$old/migrations.txt" ] || { cut -c67- "$new/migrations.txt"; return; }
  awk 'NR==FNR { known[substr($0, 67)] = substr($0, 1, 64); next }
       { path = substr($0, 67); sum = substr($0, 1, 64)
         if (path in known && known[path] != sum) { print "CHANGED " path; bad = 1 }
         else if (!(path in known)) print path }
       END { exit bad }' "$old/migrations.txt" "$new/migrations.txt"
}

# Runs Flyway for <version> as its own user. Credentials are files in $MIGRATION_CREDENTIALS named after the Spring property they set;
# systemd hands them over with LoadCredential (never as environment variables) and Spring reads them as a config tree.
migrate() {
  local version="$1" first_install="$2" release="$releases/$1" unit log_file deadline started property env_name
  local -a credentials=() unset_env=() args=()
  assert_root_owned "$MIGRATION_CREDENTIALS"
  [ "$(stat -c %a "$MIGRATION_CREDENTIALS")" = 700 ] || fail "$MIGRATION_CREDENTIALS must be mode 700"
  [ -s "$MIGRATION_CREDENTIALS/spring.flyway.user" ] && [ -s "$MIGRATION_CREDENTIALS/spring.flyway.password" ] \
    || fail "$MIGRATION_CREDENTIALS needs spring.flyway.user and spring.flyway.password (RELEASE.md §4)"
  for property in "$MIGRATION_CREDENTIALS"/*; do
    property="$(basename "$property")"
    [[ "$property" =~ ^[a-z][a-z0-9.-]*$ ]] || fail "unexpected file in $MIGRATION_CREDENTIALS: $property"
    if [[ "$property" =~ $FIRST_INSTALL_ONLY ]] && [ "$first_install" != true ]; then
      fail "$MIGRATION_CREDENTIALS/$property is for the first installation only; remove it (RELEASE.md §4)"
    fi
    [ "$(stat -c '%U:%a' "$MIGRATION_CREDENTIALS/$property")" = root:600 ] || fail "$MIGRATION_CREDENTIALS/$property must be root 0600"
    credentials+=(-p "LoadCredential=$property:$MIGRATION_CREDENTIALS/$property")
    # A property given here wins over the same setting from one.env: EnvironmentFile values would otherwise outrank the config tree
    env_name="$(printf '%s' "$property" | tr 'a-z.-' 'A-Z__')"
    unset_env+=(-p "UnsetEnvironment=$env_name")
  done

  unit="forcitis-one-migrate-$(date -u +%Y%m%d%H%M%S)"
  install -d -o root -g root -m 0700 "$RELEASE_LOGS"
  log_file="$RELEASE_LOGS/$unit-$version.json"
  args=(--spring.main.web-application-type=none "--spring.config.import=configtree:/run/credentials/$unit.service/"
        --spring.flyway.enabled=true --spring.flyway.default-schema=one_migration)
  # Overrides go on the command line: Spring lets arguments win over one.env. Every scheduled job and outbound delivery stays off.
  args+=(--WORKFORCE_SYNC_ENABLED=false --WORKFORCE_SHORT_NAME_ENABLED=false --WECOM_ENABLED=false --APPROVAL_DEADLINE_ENABLED=false
         --EVENT_PUBLICATION_JOBS_ENABLED=false --FLOWABLE_ASYNC_EXECUTOR=false
         --spring.session.jdbc.cleanup-cron=- --forcitis.platform.identity.credential-purge.cron=-
         --forcitis.platform.numbering.capacity-check.cron=-)

  if python3 -I -c 'import sys, zipfile; sys.exit(0 if sys.argv[2] in zipfile.ZipFile(sys.argv[1]).namelist() else 1)' \
       "$release/forcitis-one.jar" "$MIGRATE_PROFILE_RESOURCE"; then
    log "running migrations of $version in the application's migrate mode as unit $unit (log $log_file)"
    systemd-run --wait --collect --unit="$unit" --uid="$MIGRATION_USER" --gid="$APP_USER" --working-directory="$release" \
      -p EnvironmentFile="$ETC/one.env" "${credentials[@]}" "${unset_env[@]}" -p PrivateTmp=yes -p NoNewPrivileges=yes \
      -p RuntimeMaxSec="$MIGRATION_TIMEOUT_SECONDS" -p StandardOutput="append:$log_file" -p StandardError="append:$log_file" \
      /usr/bin/java "$JVM_TIMEZONE" -XX:+ExitOnOutOfMemoryError -jar "$release/forcitis-one.jar" --spring.profiles.include=migrate "${args[@]}" \
      || fail "migrations of $version failed or timed out; result unknown, check one_migration.flyway_schema_history and $log_file"
    log "migrations of $version applied (migrate mode exited 0)"
    return
  fi

  # Until the application has a migrate-and-exit mode: Flyway runs during startup and aborts it on error, so reaching "Started"
  # means Flyway finished; anything else leaves the outcome open.
  log "running migrations of $version as unit $unit (log $log_file)"
  systemd-run --unit="$unit" --uid="$MIGRATION_USER" --gid="$APP_USER" --working-directory="$release" \
    -p EnvironmentFile="$ETC/one.env" "${credentials[@]}" "${unset_env[@]}" -p PrivateTmp=yes -p NoNewPrivileges=yes \
    -p StandardOutput="append:$log_file" -p StandardError="append:$log_file" \
    /usr/bin/java "$JVM_TIMEZONE" -XX:+ExitOnOutOfMemoryError -jar "$release/forcitis-one.jar" "${args[@]}"
  deadline=$((SECONDS + MIGRATION_TIMEOUT_SECONDS))
  started=false
  while [ "$SECONDS" -lt "$deadline" ]; do
    if grep -q "Started ${APPLICATION_CLASS} in" "$log_file" 2>/dev/null; then
      started=true
      break
    fi
    if ! systemctl is-active --quiet "$unit"; then
      if grep -q "Started ${APPLICATION_CLASS} in" "$log_file" 2>/dev/null; then started=true; fi
      break
    fi
    sleep 3
  done
  systemctl stop "$unit" 2>/dev/null || true
  systemctl reset-failed "$unit" 2>/dev/null || true
  $started || fail "migration run of $version ended without reaching startup; result unknown, check one_migration.flyway_schema_history and $log_file; the running release is unchanged"
  log "migration run of $version reached startup with Flyway enabled (Flyway aborts startup on error); confirm in one_migration.flyway_schema_history"
}

prune() {
  local keep_current keep_previous dir name count=0
  keep_current="$(linked_version "$current_link")"
  keep_previous="$(linked_version "$previous_link")"
  while IFS= read -r dir; do
    name="$(basename "$dir")"
    if [[ "$name" = "$keep_current" || "$name" = "$keep_previous" ]]; then continue; fi
    count=$((count + 1))
    if [ "$count" -gt "$KEEP_RELEASES" ]; then
      log "removing old release $name"
      rm -rf "${releases:?}/$name"
    fi
  done < <(find "$releases" -mindepth 1 -maxdepth 1 -type d ! -name '.*' -printf '%T@ %p\n' | sort -rn | cut -d' ' -f2-)
}

lock() {
  exec 9>"$LOCK"
  flock -n 9 || fail "another release operation is running"
}

cmd_install_pending() {
  [ "$#" -eq 0 ] || fail "install-pending takes no arguments; the environment comes from $RELEASE_SOURCE"
  local target_env repo pinned_expected profile running new first_install=false deployment_id version run_id commit
  read_release_source
  profile="$(env_value SPRING_PROFILES_ACTIVE)"
  [ "$profile" = "$target_env" ] || fail "$RELEASE_SOURCE says '$target_env', but one.env SPRING_PROFILES_ACTIVE is '$profile'"
  # An older manual setup kept static files in a real current/ directory; switching would discard them
  [[ ! -e "$current_link" || -L "$current_link" ]] \
    || fail "$current_link is a directory, not the release link; move pinned files to $PINNED and remove it first (C-4 E-10)"
  assert_root_owned "$BASE" "$releases"
  lock
  github_auth
  validate_deployment
  processed_check "$deployment_id"
  # Taken up before anything is installed, so a crash half-way is never replayed by the same deployment
  record_deployment "$deployment_id" started "v$version $commit run $run_id"
  deployment_in_progress="$deployment_id"

  [[ "$target_env" != prod || "$version" != *-* ]] || fail "prod takes released versions only, not the pre-release $version"
  running="$(linked_version "$current_link")"
  if [ -n "$running" ] && version_lower "$version" "$running"; then
    fail "$version is lower than the running $running; going back is an explicit operator rollback (forcitis-one-release rollback), never a deployment"
  fi
  if [ -n "$pinned_expected" ]; then
    [ -s "$PINNED/$pinned_expected" ] || fail "WECOM_VERIFY_FILE names $pinned_expected, missing in $PINNED; WeCom would drop the trusted domain (C-4 E-10)"
  fi
  place "$version" "$target_env" "$commit"
  running="$(linked_version "$current_link")"
  [ -n "$running" ] || first_install=true
  if [ -n "$running" ] && [ "$running" != "$version" ]; then
    if ! awk 'NR==FNR { mine[substr($0, 67)]; next } !(substr($0, 67) in mine) { exit 1 }' \
         "$releases/$version/migrations.txt" "$releases/$running/migrations.txt"; then
      log "WARNING: the database may hold migrations of $running that $version does not know; this is safe only because migrations are expand-only (RELEASE.md §4)"
    fi
  fi
  new="$(new_migrations "${running:+$releases/$running}" "$releases/$version")" || fail "a migration file changed between releases: $new"
  if [ -n "$new" ]; then
    log "new migrations: $(printf '%s' "$new" | tr '\n' ' ')"
  else
    log "no new migrations; the run still validates the applied ones against this release"
  fi
  migrate "$version" "$first_install"
  activate "$version"
  record_deployment "$deployment_id" succeeded "v$version"
  deployment_in_progress=""
  prune
}

cmd_rollback() {
  local target="${1:-}"
  assert_root_owned "$BASE" "$releases"
  lock
  if [ -z "$target" ]; then
    target="$(linked_version "$previous_link")"
    [ -n "$target" ] || fail "no previous release recorded; name a version"
  fi
  valid_version "$target" || fail "invalid version '$target'"
  [ "$target" != "$(linked_version "$current_link")" ] || fail "$target is already current"
  activate "$target"
}

cmd_status() {
  echo "current:  $(linked_version "$current_link")"
  echo "previous: $(linked_version "$previous_link")"
  echo "installer sha256: $(sha256sum "$SELF" | cut -d' ' -f1)"
  echo "installed releases:"
  find "$releases" -mindepth 1 -maxdepth 1 -type d ! -name '.*' -printf '  %f\n' | sort
  [ ! -f "$releases/HISTORY" ] || { echo "recent history:"; tail -5 "$releases/HISTORY"; }
  [ ! -f "$PROCESSED" ] || { echo "recent deployments:"; tail -5 "$PROCESSED"; }
}

main() {
  if [ "$(id -u)" -ne 0 ]; then
    [ -n "${SSH_ORIGINAL_COMMAND:-}" ] || fail "run as root, or through the deploy key"
    local -a words
    read -r -a words <<< "$SSH_ORIGINAL_COMMAND"
    [[ "${#words[@]}" -ge 1 && "${#words[@]}" -le 2 ]] || fail "unexpected command"
    for word in "${words[@]}"; do
      [[ "$word" =~ ^[A-Za-z0-9.+-]{1,64}$ ]] || fail "unexpected command"
    done
    exec sudo -n "$SELF" "${words[@]}"
  fi
  local command="${1:-}"
  shift || true
  case "$command" in
    install-pending) cmd_install_pending "$@" ;;
    rollback) cmd_rollback "$@" ;;
    status) cmd_status ;;
    *) fail "usage: install-pending | rollback [<version>] | status" ;;
  esac
}

# Sourcing defines the functions only (the request checks are exercised that way); running the file acts
if [ "${BASH_SOURCE[0]}" = "$0" ]; then
  main "$@"
fi
