#!/usr/bin/env bash
# FORCITIS one release installer on the ECS. Installed as /usr/local/sbin/forcitis-one-release (root:root 0755) by the one-time setup in
# RELEASE.md (Phase 1a infra runbooks). Run as root by a fixed, parameterless Cloud Assistant command, or through a deploy key whose
# forced command is this script (the key never gets a shell). It takes nothing from its caller but the environment name: what to install
# is the deploy request the deploy job left in OSS, read with this instance's RAM role, and is only installed when the release statement
# it names verifies against the signer key on this server.
#
#   install-pending <dev|prod>   install the release named in <prefix>/requests/<env>.json
#   rollback [<version>]         back to the previous release, or to <version>
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
# Where the deploy job stages releases (root 0644, keys OSS_BUCKET, OSS_ENDPOINT, OSS_PREFIX, ALIYUN_PROFILE); the profile uses the
# instance RAM role, so no credential is stored on this server
RELEASE_SOURCE="$ETC/release-source"
ALIYUN=/usr/local/bin/aliyun
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
fail() { log "FAILED: $*"; exit 1; }

if [ "$(id -u)" -ne 0 ]; then
  [ -n "${SSH_ORIGINAL_COMMAND:-}" ] || fail "run as root, or through the deploy key"
  read -r -a words <<< "$SSH_ORIGINAL_COMMAND"
  [[ "${#words[@]}" -ge 1 && "${#words[@]}" -le 2 ]] || fail "unexpected command"
  for word in "${words[@]}"; do
    [[ "$word" =~ ^[A-Za-z0-9.+-]{1,64}$ ]] || fail "unexpected command"
  done
  exec sudo -n "$SELF" "${words[@]}"
fi

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

# Copies oss://<bucket>/<prefix>/<key> to <out> with the instance RAM role
oss_get() {
  local key="$1" out="$2" bucket endpoint prefix profile
  bucket="$(source_value OSS_BUCKET)"; endpoint="$(source_value OSS_ENDPOINT)"
  prefix="$(source_value OSS_PREFIX)"; profile="$(source_value ALIYUN_PROFILE)"
  [[ "$bucket" =~ ^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$ && "$endpoint" =~ ^[a-z0-9.-]+\.aliyuncs\.com$ \
     && "$prefix" =~ ^[a-z0-9][a-z0-9/_-]*[a-z0-9]$ && "$profile" =~ ^[a-z0-9-]+$ ]] \
    || fail "$RELEASE_SOURCE must set OSS_BUCKET, OSS_ENDPOINT, OSS_PREFIX and ALIYUN_PROFILE (RELEASE.md §7)"
  timeout "$DOWNLOAD_TIMEOUT_SECONDS" "$ALIYUN" --profile "$profile" oss cp "oss://$bucket/$prefix/$key" "$out" \
    --endpoint "$endpoint" -f >/dev/null || fail "could not fetch $prefix/$key from OSS"
}

place() {
  local version="$1" env="$2" expected_sha="$3" commit="$4" incoming archive top staged existing
  incoming="$(mktemp -d "$releases/.incoming.XXXXXX")"
  trap 'rm -rf "$incoming"' EXIT
  top="forcitis-one-$version"
  archive="$incoming/bundle.tar.gz"
  log "fetching $top from OSS"
  oss_get "$version/$top.tar.gz" "$archive"
  oss_get "$version/$top.release" "$incoming/statement"
  oss_get "$version/$top.release.sig" "$incoming/statement.sig"
  [ "$(stat -c %s "$archive")" -le "$MAX_BUNDLE_BYTES" ] || fail "bundle larger than $MAX_BUNDLE_BYTES bytes"
  log "fetched $(stat -c %s "$archive") bytes"
  [ "$(sha256sum "$archive" | cut -d' ' -f1)" = "$expected_sha" ] || fail "bundle sha256 differs from the deploy request"

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
  trap - EXIT
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
  local env="${1:-}" request version sha commit pinned_expected profile running new first_install=false
  case "$env" in dev|prod) ;; *) fail "environment must be dev or prod" ;; esac
  profile="$(env_value SPRING_PROFILES_ACTIVE)"
  [ "$profile" = "$env" ] || fail "this server is configured for '$profile' (SPRING_PROFILES_ACTIVE), not '$env'"
  [ -s "$RELEASE_SOURCE" ] && [ "$(stat -c '%U %a' "$RELEASE_SOURCE")" = "root 644" ] || fail "$RELEASE_SOURCE must exist, root-owned 0644"
  [ -x "$ALIYUN" ] || fail "$ALIYUN is missing (RELEASE.md §7)"
  request="$(mktemp)"
  oss_get "requests/$env.json" "$request"
  # Every field is checked here; the request only chooses among releases whose statements verify below
  read -r version sha commit pinned_expected < <(python3 -I - "$request" <<'PY'
import json, re, sys
r = json.load(open(sys.argv[1]))
checks = {"version": r"[0-9]+\.[0-9]+\.[0-9]+(-rc\.[0-9]+)?", "bundleSha256": r"[0-9a-f]{64}", "commit": r"[0-9a-f]{40}",
          "wecomVerifyFile": r"-|WW_verify_[A-Za-z0-9]{1,64}\.txt"}
for key, pattern in checks.items():
    if not isinstance(r.get(key), str) or not re.fullmatch(pattern, r[key]):
        sys.exit(f"deploy request field {key} is missing or malformed")
print(r["version"], r["bundleSha256"], r["commit"], r["wecomVerifyFile"])
PY
) || fail "the deploy request is not valid"
  rm -f "$request"
  valid_version "$version" || fail "invalid version '$version'"
  [[ "$env" != prod || "$version" != *-* ]] || fail "prod takes released versions only, not the pre-release $version"
  running="$(linked_version "$current_link")"
  if [ -n "$running" ] && version_lower "$version" "$running"; then
    fail "$version is lower than the running $running; going back is an explicit operator rollback (forcitis-one-release rollback), never a deploy request"
  fi
  log "deploy request: $version ($commit) for $env"
  if [ "$pinned_expected" != "-" ]; then
    [[ "$pinned_expected" =~ $PINNED_PATTERN ]] || fail "deploy request field wecomVerifyFile must name a WW_verify_*.txt file or be -"
    [ -s "$PINNED/$pinned_expected" ] || fail "deploy request field wecomVerifyFile names $pinned_expected, missing in $PINNED; WeCom would drop the trusted domain (C-4 E-10)"
  fi
  # An older manual setup kept static files in a real current/ directory; switching would discard them
  [[ ! -e "$current_link" || -L "$current_link" ]] \
    || fail "$current_link is a directory, not the release link; move pinned files to $PINNED and remove it first (C-4 E-10)"
  assert_root_owned "$BASE" "$releases"
  lock
  place "$version" "$env" "$sha" "$commit"
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
}

command="${1:-}"
shift || true
case "$command" in
  install-pending) cmd_install_pending "$@" ;;
  rollback) cmd_rollback "$@" ;;
  status) cmd_status ;;
  *) fail "usage: install-pending <dev|prod> | rollback [<version>] | status" ;;
esac
