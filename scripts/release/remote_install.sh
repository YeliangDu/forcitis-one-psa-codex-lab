#!/usr/bin/env bash
# FORCITIS one release installer on the ECS. Installed as /usr/local/sbin/forcitis-one-release (root:root 0755) by the one-time setup in
# docs/modernization/phase1a/infra/runbooks/RELEASE.md; the deploy key's forced command runs it, so the key never gets a shell.
#
#   install  <version> <dev|prod> <sha256 of the bundle>   bundle tar.gz on stdin
#   rollback [<version>]                                   back to the previous release, or to <version>
#   status
#
# Every step fails loudly; a release that does not report healthy is switched back to the release that ran before.
set -euo pipefail
umask 027

SELF=/usr/local/sbin/forcitis-one-release
BASE=/data/forcitis-one
ETC=/etc/forcitis-one
UNIT=forcitis-one
APPLICATION_CLASS=OneApplication
VERSION_PATTERN='^[0-9]+\.[0-9]+\.[0-9]+(-rc\.[0-9]+)?$'
MAX_BUNDLE_BYTES=$((1024 * 1024 * 1024))
HEALTH_TIMEOUT_SECONDS=180
MIGRATION_TIMEOUT_SECONDS=900
KEEP_RELEASES=5

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" >&2; }
fail() { log "FAILED: $*"; exit 1; }

if [ "$(id -u)" -ne 0 ]; then
  [ -n "${SSH_ORIGINAL_COMMAND:-}" ] || fail "run as root, or through the deploy key"
  read -r -a words <<< "$SSH_ORIGINAL_COMMAND"
  [ "${#words[@]}" -ge 1 ] && [ "${#words[@]}" -le 4 ] || fail "unexpected command"
  for word in "${words[@]}"; do
    [[ "$word" =~ ^[A-Za-z0-9.+-]{1,64}$ ]] || fail "unexpected command"
  done
  exec sudo -n "$SELF" "${words[@]}"
fi

releases="$BASE/releases"
current_link="$BASE/current"
previous_link="$BASE/previous"

valid_version() { [[ "$1" =~ $VERSION_PATTERN ]]; }
env_value() {
  local key="$1" line
  line="$(grep -E "^${key}=" "$ETC/one.env" | tail -1 || true)"
  line="${line#*=}"
  line="${line#\'}"; line="${line%\'}"
  printf '%s' "$line"
}
linked_version() { [ -L "$1" ] && basename "$(readlink "$1")" || true; }

health_ok() {
  local port deadline
  port="$(env_value MANAGEMENT_SERVER_PORT)"
  port="${port:-8081}"
  deadline=$((SECONDS + HEALTH_TIMEOUT_SECONDS))
  while [ "$SECONDS" -lt "$deadline" ]; do
    if systemctl is-active --quiet "$UNIT" \
      && curl -fsS --max-time 5 "http://127.0.0.1:${port}/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; then
      return 0
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

check_server_files() {
  local dir="$1" drift=()
  cmp -s "$dir/deploy/remote_install.sh" "$SELF" || drift+=("$SELF")
  cmp -s "$dir/deploy/forcitis-one.service" /etc/systemd/system/forcitis-one.service || drift+=(/etc/systemd/system/forcitis-one.service)
  cmp -s "$dir/deploy/nginx-one.conf" /etc/nginx/conf.d/one.conf || drift+=(/etc/nginx/conf.d/one.conf)
  cmp -s "$dir/deploy/forcitis-one.logrotate" /etc/logrotate.d/forcitis-one || drift+=(/etc/logrotate.d/forcitis-one)
  [ "${#drift[@]}" -eq 0 ] || fail "server files differ from this release; apply them as in RELEASE.md (server file changes) first: ${drift[*]}"

  local missing=() key
  while IFS= read -r key; do
    grep -qE "^${key}=" "$ETC/one.env" || missing+=("$key")
  done < <(grep -oE '^[A-Z][A-Z0-9_]*=' "$dir/deploy/one.env.template" | tr -d '=')
  [ "${#missing[@]}" -eq 0 ] || fail "$ETC/one.env lacks keys this release expects (add them, empty if unused): ${missing[*]}"
}

grant_static_read() {
  local dir="$1"
  setfacl -m u:nginx:--x "$BASE" "$releases" "$dir"
  setfacl -R -m u:nginx:r-X "$dir"/web-*
}

place() {
  local version="$1" env="$2" expected_sha="$3" incoming archive top staged existing
  incoming="$(mktemp -d "$releases/.incoming.XXXXXX")"
  trap 'rm -rf "$incoming"' EXIT
  archive="$incoming/bundle.tar.gz"
  head -c $((MAX_BUNDLE_BYTES + 1)) > "$archive"
  [ "$(stat -c %s "$archive")" -le "$MAX_BUNDLE_BYTES" ] || fail "bundle larger than $MAX_BUNDLE_BYTES bytes"
  [ "$(sha256sum "$archive" | cut -d' ' -f1)" = "$expected_sha" ] || fail "bundle sha256 differs from the release's published sha256"

  top="forcitis-one-$version"
  check_tar_entries "$archive" "$top"
  tar -xzf "$archive" -C "$incoming" --no-same-owner --no-same-permissions
  staged="$incoming/$top"
  verify_bundle_dir "$staged"
  grep -q "\"version\": \"$version\"" "$staged/manifest.json" || fail "manifest.json is not for version $version"
  [ -d "$staged/web-$env" ] || fail "bundle has no static files for $env"

  existing="$releases/$version"
  if [ -d "$existing" ]; then
    cmp -s "$existing/manifest.json" "$staged/manifest.json" \
      || fail "release $version is already installed with different contents; publish a new version instead"
    # Server files were checked when this version was first installed; re-checking would block returning to an older release
    log "release $version already installed with identical contents"
  else
    check_server_files "$staged"
    chown -R root:forcitis "$staged"
    find "$staged" -type d -exec chmod 0750 {} +
    find "$staged" -type f -exec chmod 0640 {} +
    mv "$staged" "$existing"
  fi
  ln -sfn "web-$env" "$existing/web"
  grant_static_read "$existing"
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

migrate() {
  local version="$1" release="$releases/$1" unit started log_file deadline
  [ -f "$ETC/migrate.env" ] || fail "$ETC/migrate.env is missing; migrations run only with the migration account (RELEASE.md)"
  [ "$(stat -c '%U:%a' "$ETC/migrate.env")" = "root:600" ] || fail "$ETC/migrate.env must be root-owned 0600"
  unit="forcitis-one-migrate-$(date -u +%Y%m%d%H%M%S)"
  log_file="$BASE/logs/$unit-$version.json"
  log "running migrations of $version as transient unit $unit (log $log_file)"
  # Overrides go on the command line: systemd lets EnvironmentFile= win over Environment=, Spring lets arguments win over both
  systemd-run --unit="$unit" --uid=forcitis --gid=forcitis --working-directory="$release" \
    -p EnvironmentFile="$ETC/one.env" -p EnvironmentFile="$ETC/migrate.env" \
    -p StandardOutput="append:$log_file" -p StandardError="append:$log_file" \
    /usr/bin/java -XX:+ExitOnOutOfMemoryError -jar "$release/forcitis-one.jar" --spring.main.web-application-type=none \
    --spring.flyway.enabled=true --spring.flyway.default-schema=one_migration \
    --WORKFORCE_SYNC_ENABLED=false --WORKFORCE_SHORT_NAME_ENABLED=false --WECOM_ENABLED=false \
    --APPROVAL_DEADLINE_ENABLED=false --EVENT_PUBLICATION_JOBS_ENABLED=false --FLOWABLE_ASYNC_EXECUTOR=false
  # The application has no migrate-and-exit mode yet: Flyway runs during startup, so "Started" means every migration applied
  deadline=$((SECONDS + MIGRATION_TIMEOUT_SECONDS))
  started=false
  while [ "$SECONDS" -lt "$deadline" ]; do
    if grep -q "Started ${APPLICATION_CLASS} in" "$log_file" 2>/dev/null; then
      started=true
      break
    fi
    if ! systemctl is-active --quiet "$unit"; then
      ! grep -q "Started ${APPLICATION_CLASS} in" "$log_file" 2>/dev/null || started=true
      break
    fi
    sleep 3
  done
  systemctl stop "$unit" 2>/dev/null || true
  systemctl reset-failed "$unit" 2>/dev/null || true
  $started || fail "migrations of $version did not complete (see $log_file); the running release is unchanged"
  log "migrations of $version applied"
}

prune() {
  local keep_current keep_previous dir name count=0
  keep_current="$(linked_version "$current_link")"
  keep_previous="$(linked_version "$previous_link")"
  while IFS= read -r dir; do
    name="$(basename "$dir")"
    [ "$name" = "$keep_current" ] || [ "$name" = "$keep_previous" ] && continue
    count=$((count + 1))
    if [ "$count" -gt "$KEEP_RELEASES" ]; then
      log "removing old release $name"
      rm -rf "${releases:?}/$name"
    fi
  done < <(find "$releases" -mindepth 1 -maxdepth 1 -type d ! -name '.*' -printf '%T@ %p\n' | sort -rn | cut -d' ' -f2-)
}

lock() {
  exec 9>"$BASE/.release.lock"
  flock -n 9 || fail "another release operation is running"
}

cmd_install() {
  local version="${1:-}" env="${2:-}" sha="${3:-}" profile running new
  valid_version "$version" || fail "invalid version '$version'"
  case "$env" in dev|prod) ;; *) fail "environment must be dev or prod" ;; esac
  [[ "$sha" =~ ^[0-9a-f]{64}$ ]] || fail "sha256 of the bundle required"
  profile="$(env_value SPRING_PROFILES_ACTIVE)"
  [ "$profile" = "$env" ] || fail "this server is configured for '$profile' (SPRING_PROFILES_ACTIVE), not '$env'"
  lock
  place "$version" "$env" "$sha"
  running="$(linked_version "$current_link")"
  if [ -n "$running" ] && [ "$running" != "$version" ]; then
    if ! awk 'NR==FNR { mine[substr($0, 67)]; next } !(substr($0, 67) in mine) { exit 1 }' \
         "$releases/$version/migrations.txt" "$releases/$running/migrations.txt"; then
      log "WARNING: the database may hold migrations of $running that $version does not know; this is safe only because migrations are expand-only (RELEASE.md)"
    fi
  fi
  new="$(new_migrations "${running:+$releases/$running}" "$releases/$version")" || fail "a migration file changed between releases: $new"
  if [ -n "$new" ]; then
    log "new migrations: $(printf '%s' "$new" | tr '\n' ' ')"
    migrate "$version"
  else
    log "no new migrations"
  fi
  activate "$version"
  prune
}

cmd_rollback() {
  local target="${1:-}"
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
  install) cmd_install "$@" ;;
  rollback) cmd_rollback "$@" ;;
  status) cmd_status ;;
  *) fail "usage: install <version> <dev|prod> <sha256> | rollback [<version>] | status" ;;
esac
