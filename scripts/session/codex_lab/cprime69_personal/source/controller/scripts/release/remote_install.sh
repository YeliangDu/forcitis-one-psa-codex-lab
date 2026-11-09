#!/usr/bin/env bash
# FORCITIS one release installer on the ECS. Installed as /usr/local/sbin/forcitis-one-release (root:root 0755) by the one-time setup in
# RELEASE.md (Phase 1a infra runbooks). Pull model: forcitis-one-release-pull.timer runs `pull` as root every minute. Nothing outside
# this server can make it execute anything: the deploy job (only deploy.yml on main for this environment can assume its RAM role, RELEASE.md
# §8.2) can only write this environment's OOS request parameters and read the result parameter. `pull` reads the request with the instance
# RAM role, checks it strictly, installs only a release whose signed statement verifies against the signer key on this server and names the
# requested version, and writes the outcome to the result parameter, which only reports and is never trusted by anyone.
#
#   pull                         take up this environment's request in OOS, if it is new (contract: RELEASE.md §3)
#   finish-interrupted           ExecStopPost of the pull unit: undo an unconfirmed switch, end a request left `started`
#   rollback [<version>]         back to the previous release, or to <version> (local only, no network)
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
# root 0644: ENVIRONMENT (dev|prod), optional WECOM_VERIFY_FILE (a pinned file that must exist)
RELEASE_SOURCE="$ETC/release-source"
# OOS parameters <prefix>-<env> (the request) and <prefix>-<env>-<asset> (one signed link each; four links exceed one 4096-character value)
REQUEST_PARAMETER_PREFIX=forcitis-one-deploy-request
REQUEST_ASSET_PARAMETERS='bundle bundle-sha256 statement statement-sig'
RESULT_PARAMETER_PREFIX=forcitis-one-deploy-result
REQUEST_PLACEHOLDER='{}'
MAX_PARAMETER_CHARS=4096
METADATA_URL=http://100.100.100.200
OOS_ENDPOINT='https://oos.{region}.aliyuncs.com'
ALERT_SENDER=/usr/local/sbin/one_alert_send
# Failures that belong to no request (configuration, metadata, OOS) alert at most once per this many seconds per cause
GLOBAL_ALERT_SECONDS=3600
ALERTS_PER_HOUR=6
# Remembers the last "state directory unavailable" alert when the state directory itself is missing
STATE_ALERT_MARK=/run/forcitis-one-release/state-alert
# GitHub serves release assets from these hosts as short-lived signed URLs; the deploy job resolves them, nothing else is fetched
ASSET_HOSTS='objects.githubusercontent.com release-assets.githubusercontent.com'
# Every request taken up, keyed <run_id>:<run_attempt>, with its outcome (root 0600, appended only); a key listed here is never installed again
STATE_DIR=/var/lib/forcitis-one-release
PROCESSED="$STATE_DIR/processed-requests"
# Alerts already sent: "<request_id or global> <cause>" and the time (root 0600, appended only)
ALERTS="$STATE_DIR/alerts-sent"
# "<version before> <version switched to> <request_id>" while a switch is not yet confirmed healthy; finish-interrupted switches back
ACTIVATION="$STATE_DIR/activation"
# Files nginx serves outside the releases, untouched by any deployment (WeCom trusted-domain verification, C-4 E-10)
PINNED="$BASE/static-pinned"
PINNED_PATTERN='^WW_verify_[A-Za-z0-9]{1,64}\.txt$'
PUBLIC_HOST=one.forcitis.com
STATEMENT_NAMESPACE=forcitis-one-release
STATEMENT_FORMAT=forcitis-one-release-statement/1
RELEASE_WORKFLOW=FORCITIS/forcitis-one-psa/.github/workflows/release.yml@refs/heads/main
RELEASE_LOGS=/var/log/forcitis-one-release
# RuntimeDirectory of the pull unit (kept between runs); rollback outside the unit creates it if missing
RUN_DIR=/run/forcitis-one-release
LOCK="$RUN_DIR/lock"
UNIT=forcitis-one
APP_USER=forcitis
MIGRATION_USER=forcitis-migrate
APPLICATION_CLASS=OneApplication
MIGRATE_PROFILE_RESOURCE=BOOT-INF/classes/application-migrate.properties
VERSION_PATTERN='^[0-9]+\.[0-9]+\.[0-9]+(-rc\.[0-9]+)?$'
MAX_BUNDLE_BYTES=$((1024 * 1024 * 1024))
# Time budget of one `pull` (forcitis-one-release-pull.service allows 3000 s): no step starts after PULL_BUDGET_SECONDS. The switch needs
# SWITCH_RESERVE_SECONDS: two restarts with their health checks (new release, then the one switched back to) plus OOS calls; the migration
# and the bundle download only start when the time they may take still leaves that reserve.
SMALL_ASSET_TIMEOUT_SECONDS=60
DOWNLOAD_TIMEOUT_SECONDS=1200
HEALTH_TIMEOUT_SECONDS=180
RESTART_SECONDS=60
MIGRATION_TIMEOUT_SECONDS=900
SWITCH_RESERVE_SECONDS=$((2 * (HEALTH_TIMEOUT_SECONDS + RESTART_SECONDS) + 90))
PULL_BUDGET_SECONDS=2700
# The application refuses to start unless the JVM default zone is UTC (kernel.UtcJvm); every java launch here passes it
JVM_TIMEZONE=-Duser.timezone=UTC
KEEP_RELEASES=5
# Property files a migration may only see on the very first installation (Flowable engine tables, see RELEASE.md §4)
FIRST_INSTALL_ONLY='^(spring\.datasource\..+|flowable\.database-schema-update)$'

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" >&2; }
# In `pull`, stage names the cause. A failure before the request is recorded as started can heal by itself (the next round reads the
# parameters again), so it reports `retrying`; after that, and for a link that is not acceptable at all, the request ends `failed`. A cause already reported for this request (or,
# without a request, within the hour) ends quietly, so the timer does not repeat the same alert and log every minute.
fail() {
  local cause="${stage:-install}" status=retrying due=0
  [ -z "${request_in_progress:-}" ] || status=failed
  if [ -n "${pulling:-}" ]; then
    alert_due "$cause" || due=$?
    # Already reported: stay quiet, unless a request is in progress, which must still end recorded as failed
    [ "$due" -ne 1 ] || [ -n "${request_in_progress:-}" ] || exit 0
  fi
  log "FAILED: $*"
  [ -z "${request_in_progress:-}" ] || record_request "$request_in_progress" failed "$cause: $*" || log "could not record request $request_in_progress as failed"
  request_in_progress=""
  if [ -n "${pulling:-}" ]; then
    [ -z "${request_id:-}" ] || write_result "$status" "$cause"
    case "$due" in
      0) send_alert "$cause" "$*" ;;
      2) log "alert for $cause not sent: $ALERTS cannot record it" ;;
    esac
  fi
  exit 1
}

# 0: an alert for <cause> should go out now (remembered); 1: already reported, stay quiet; 2: cannot be remembered, so not sent
alert_due() {
  local cause="$1" key last
  [ -d "$STATE_DIR" ] || return 2
  if [ -n "${alert_key:-}" ]; then
    key="$alert_key $cause"
    if [ -f "$ALERTS" ] && awk -F'\t' -v key="$key" '$1 == key { found = 1 } END { exit !found }' "$ALERTS"; then return 1; fi
  else
    key="global $cause"
    last=0
    [ ! -f "$ALERTS" ] || last="$(awk -F'\t' -v key="$key" '$1 == key { t = $2 } END { print t + 0 }' "$ALERTS")"
    [ $(($(date +%s) - last)) -ge "$GLOBAL_ALERT_SECONDS" ] || return 1
  fi
  (umask 077 && printf '%s\t%s\n' "$key" "$(date +%s)" >> "$ALERTS") 2>/dev/null || return 2
  return 0
}

# Alerts carry the cause and a message built here, never a link or a credential. At most ALERTS_PER_HOUR go out per hour, however many
# request_ids are written; the rest are counted and reported with the next alert that goes out.
send_alert() {
  local message="one.release-pull ${target_env:-?}: ${request_id:+request $request_id }$1: $2" now sent suppressed
  now="$(date +%s)"
  sent="$(awk -F'\t' -v since=$((now - 3600)) '$1 == "#sent" && $2 > since { n++ } END { print n + 0 }' "$ALERTS" 2>/dev/null || echo 0)"
  if [ "$sent" -ge "$ALERTS_PER_HOUR" ]; then
    (umask 077 && printf '#suppressed\t%s\n' "$now" >> "$ALERTS") 2>/dev/null || log "could not record the suppressed alert in $ALERTS"
    log "alert suppressed ($ALERTS_PER_HOUR already sent within the hour): $1"
    return 0
  fi
  suppressed="$(awk -F'\t' '$1 == "#reported" { n = 0 } $1 == "#suppressed" { n++ } END { print n + 0 }' "$ALERTS" 2>/dev/null || echo 0)"
  [ "$suppressed" -eq 0 ] || message="$message (and $suppressed more alerts suppressed by the hourly limit)"
  (umask 077 && printf '#sent\t%s\n#reported\t%s\n' "$now" "$now" >> "$ALERTS") 2>/dev/null || log "could not count the alert in $ALERTS"
  "$ALERT_SENDER" P2 "$message" >/dev/null 2>&1 || log "alert delivery through $ALERT_SENDER failed"
}

# The state directory cannot be created: nothing can be remembered there, so a marker under /run limits the alert to once per hour
state_unavailable() {
  local now last=0
  now="$(date +%s)"
  [ ! -f "$STATE_ALERT_MARK" ] || last="$(cat "$STATE_ALERT_MARK" 2>/dev/null || echo 0)"
  [[ "$last" =~ ^[0-9]+$ ]] || last=0
  [ $((now - last)) -ge "$GLOBAL_ALERT_SECONDS" ] || exit 0
  log "FAILED: cannot create $STATE_DIR; no request is taken up"
  (umask 077 && printf '%s\n' "$now" > "$STATE_ALERT_MARK") 2>/dev/null || log "could not write $STATE_ALERT_MARK"
  "$ALERT_SENDER" P2 "one.release-pull ${target_env:-?}: configuration: cannot create $STATE_DIR; no request is taken up" >/dev/null 2>&1 \
    || log "alert delivery through $ALERT_SENDER failed"
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
# True when $1 is a lower version than $2 (MAJOR.MINOR.PATCH, a -rc.N pre-release before its release); a malformed name fails
version_lower() {
  local rc=0
  python3 -I - "$1" "$2" <<'PY' || rc=$?
import re, sys
def key(v):
    m = re.fullmatch(r"([0-9]+)\.([0-9]+)\.([0-9]+)(?:-rc\.([0-9]+))?", v)
    if not m:
        print(f"cannot compare the version name {v!r}", file=sys.stderr)
        sys.exit(2)
    major, minor, patch, rc = m.groups()
    return (int(major), int(minor), int(patch), int(rc) if rc else float("inf"))
sys.exit(0 if key(sys.argv[1]) < key(sys.argv[2]) else 1)
PY
  case "$rc" in
    0) return 0 ;;
    1) return 1 ;;
    *) fail "cannot compare versions '$1' and '$2' (see $releases and the current link)" ;;
  esac
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

management_port() {
  local port
  port="$(env_value MANAGEMENT_SERVER_PORT)"
  printf '%s' "${port:-8081}"
}

# The application exports its own metrics (names starting with forcitis_); without them the release is not counted as healthy. The
# metrics text is only searched, never printed.
metrics_ok() {
  curl -fsS --max-time 5 "http://127.0.0.1:$1/actuator/prometheus" 2>/dev/null | grep '^forcitis_' >/dev/null
}

# One look: the unit is active, health is UP, the application's metrics are exported and every pinned file is served
serving_now() {
  local port body
  port="$(management_port)"
  systemctl is-active --quiet "$UNIT" || return 1
  body="$(curl -fsS --max-time 5 "http://127.0.0.1:${port}/actuator/health" 2>/dev/null || true)"
  [ -n "$body" ] && printf '%s' "$body" | python3 -I -c 'import json,sys; sys.exit(0 if json.load(sys.stdin).get("status") == "UP" else 1)' 2>/dev/null \
    && metrics_ok "$port" && pinned_served
}

health_ok() {
  local deadline=$((SECONDS + HEALTH_TIMEOUT_SECONDS))
  while [ "$SECONDS" -lt "$deadline" ]; do
    serving_now && return 0
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

# While a switch is unconfirmed, ACTIVATION names the release to return to, so a run killed half-way can still be switched back
# While a switch is unconfirmed, ACTIVATION names the release to return to; the caller removes it once the outcome is recorded. Without
# it a run killed half-way could not be switched back, so no switch happens unless it is written.
activate() {
  local version="$1" before fallback
  before="$(linked_version "$current_link")"
  log "switching current: ${before:-none} -> $version"
  [ -d "$STATE_DIR" ] || mkdir -m 0700 "$STATE_DIR" 2>/dev/null || fail "cannot create $STATE_DIR; not switching without a way back"
  (umask 077 && printf '%s %s %s\n' "${before:--}" "$version" "${request_id:--}" > "$ACTIVATION") 2>/dev/null \
    || fail "could not write $ACTIVATION; not switching without a way back"
  switch_to "$version"
  if health_ok; then
    [ -z "$before" ] || [ "$before" = "$version" ] || ln -sfn "releases/$before" "$previous_link"
    record "$version" "active"
    log "release $version is healthy"
    return 0
  fi
  record "$version" "unhealthy"
  fallback="$before"
  if [ "$before" = "$version" ]; then
    # Reinstalling the running version: go back to the previous release if there is one, never stop the service for it
    fallback="$(linked_version "$previous_link")"
    [ "$fallback" != "$version" ] || fallback=""
    if [ -z "$fallback" ]; then
      rm -f "$ACTIVATION"
      fail "release $version is current but did not become healthy, and there is no previous release; the service is left running"
    fi
  elif [ -z "$before" ]; then
    systemctl stop "$UNIT" || true
    rm -f "$ACTIVATION"
    fail "release $version did not become healthy and there is no earlier release to return to; the service is stopped"
  fi
  log "release $version did not become healthy within ${HEALTH_TIMEOUT_SECONDS}s; switching back to $fallback"
  switch_back "$fallback"
  fail "release $version was unhealthy; $switch_back_outcome"
}

# Switches back to <before> and checks it; sets switch_back_outcome
switch_back() {
  local before="$1"
  switch_to "$before"
  if health_ok; then
    record "$before" "restored"
    switch_back_outcome="$before is running again (see journalctl -u $UNIT and $BASE/logs)"
  else
    record "$before" "unhealthy"
    switch_back_outcome="$before did not recover either; manual action needed (RELEASE.md §3, half-switched state)"
  fi
  rm -f "$ACTIVATION"
  log "$switch_back_outcome"
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

# Same checks as scripts/release/statement.sh verify, against the signer key installed on this server (never one from the bundle); the
# commit is taken from the verified statement and sets commit
verify_statement() {
  local archive="$1" statement="$2" version="$3" name="forcitis-one-$3" signed_version
  [ -s "$RELEASE_SIGNERS" ] && [ "$(owner_mode "$RELEASE_SIGNERS")" = "root 644" ] \
    || fail "$RELEASE_SIGNERS must exist, root-owned 0644 (RELEASE.md §7)"
  ssh-keygen -Y verify -f "$RELEASE_SIGNERS" -I "$STATEMENT_NAMESPACE" -n "$STATEMENT_NAMESPACE" -s "$statement.sig" < "$statement" >/dev/null 2>&1 \
    || fail "the release statement's signature does not verify"
  [ "$(statement_field format "$statement")" = "$STATEMENT_FORMAT" ] || fail "unknown release statement format"
  signed_version="$(statement_field version "$statement" || true)"
  [ "$signed_version" = "$version" ] || fail "the signed release statement is for version '$signed_version', the request names $version"
  [ "$(statement_field bundle "$statement")" = "$name.tar.gz" ] || fail "release statement names another bundle"
  commit="$(statement_field commit "$statement" || true)"
  [[ "$commit" =~ ^[0-9a-f]{40}$ ]] || fail "release statement names no full commit sha"
  [ "$(statement_field workflow "$statement")" = "$RELEASE_WORKFLOW" ] || fail "release statement was not produced by the release workflow on main"
  [ "$(statement_field bundle-sha256 "$statement")" = "$(sha256sum < "$archive" | cut -d' ' -f1)" ] || fail "bundle differs from the signed release statement"
  log "bundle matches the release statement for $commit signed by release run $(statement_field run "$statement")"
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
  cmp -s "$dir/deploy/forcitis-one-release-pull.service" /etc/systemd/system/forcitis-one-release-pull.service \
    || drift+=(/etc/systemd/system/forcitis-one-release-pull.service)
  cmp -s "$dir/deploy/forcitis-one-release-pull.timer" /etc/systemd/system/forcitis-one-release-pull.timer \
    || drift+=(/etc/systemd/system/forcitis-one-release-pull.timer)
  [ "${#drift[@]}" -eq 0 ] || fail "server files differ from this release; apply them as in RELEASE.md §5 first: ${drift[*]}"
  grep -Eq "^ExecStart=/usr/bin/java .*${JVM_TIMEZONE}( |$)" /etc/systemd/system/forcitis-one.service \
    || fail "forcitis-one.service does not start the JVM with ${JVM_TIMEZONE}; the application would refuse to start"

  local missing=() key
  while IFS= read -r key; do
    grep -qE "^${key}=" "$ETC/one.env" || missing+=("$key")
  done < <(grep -oE '^[A-Z][A-Z0-9_]*=' "$dir/deploy/one.env.template" | tr -d '=')
  [ "${#missing[@]}" -eq 0 ] || fail "$ETC/one.env lacks keys this release expects (add them, empty if unused): ${missing[*]}"
  prometheus_exposed || fail "$ETC/one.env MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE lacks prometheus; the post-switch metrics check would fail"
  jobs_switch_set || fail "$ETC/one.env FORCITIS_JOBS_ENABLED must be true or false; the application has no default and would not start"
  ! autoconfigure_overridden \
    || fail "$ETC/one.env sets SPRING_AUTOCONFIGURE_EXCLUDE (or an indexed form) or SPRING_APPLICATION_JSON; either replaces the application's own exclusions (INFRA_RUNBOOK §9)"
}

# The health check after a switch reads /actuator/prometheus, so one.env must expose it
prometheus_exposed() {
  env_value MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE | tr ',' '\n' | tr -d ' ' | grep -qx -e prometheus -e '\*'
}

# Relaxed binding maps SPRING_AUTOCONFIGURE_EXCLUDE, SPRING_AUTOCONFIGURE_EXCLUDE_0_ and SPRING_APPLICATION_JSON onto spring.autoconfigure.exclude
autoconfigure_overridden() {
  grep -qE '^(SPRING_AUTOCONFIGURE_EXCLUDE(_|=)|SPRING_APPLICATION_JSON=)' "$ETC/one.env"
}

jobs_switch_set() {
  case "$(env_value FORCITIS_JOBS_ENABLED)" in true|false) return 0 ;; *) return 1 ;; esac
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

# Sets target_env and pinned_expected from this server's release configuration
read_release_source() {
  [ -s "$RELEASE_SOURCE" ] && [ "$(owner_mode "$RELEASE_SOURCE")" = "root 644" ] \
    || fail "$RELEASE_SOURCE must exist, root-owned 0644 (RELEASE.md §7 R-7)"
  target_env="$(source_value ENVIRONMENT)"
  pinned_expected="$(source_value WECOM_VERIFY_FILE)"
  case "$target_env" in dev|prod) ;; *) fail "$RELEASE_SOURCE must set ENVIRONMENT to dev or prod" ;; esac
  [[ -z "$pinned_expected" || "$pinned_expected" =~ $PINNED_PATTERN ]] \
    || fail "$RELEASE_SOURCE: WECOM_VERIFY_FILE must name a WW_verify_*.txt file or be absent"
}

# OOS with the instance RAM role: `get <name>...` prints each value on a line, `put <name>` writes the value read from stdin, `self-test`
# checks the signer against Aliyun's published example. The metadata service is used in hardened mode (a session token first), with no
# proxy and no redirects. Credentials and values stay inside this process and its pipes: never printed, written to disk or put on a command line.
read -r -d '' OOS_PY <<'PY' || true
import base64, datetime, hashlib, hmac, json, re, sys, uuid
from urllib.error import HTTPError, URLError
from urllib.parse import quote
from urllib.request import HTTPRedirectHandler, ProxyHandler, Request, build_opener

def die(message):
    sys.exit("oos: " + message)
def encode(value):
    return quote(value, safe="-_.~")
def signature(query, secret):
    canonical = "&".join(f"{encode(k)}={encode(v)}" for k, v in sorted(query.items()))
    digest = hmac.new((secret + "&").encode(), ("GET&%2F&" + encode(canonical)).encode(), hashlib.sha1).digest()
    return canonical, base64.b64encode(digest).decode()

metadata, endpoint, max_chars, mode, names = sys.argv[1], sys.argv[2], int(sys.argv[3]), sys.argv[4], sys.argv[5:]
if mode == "self-test":
    # RPC signature example of the Aliyun SDK documentation (help.aliyun.com/zh/sdk/product-overview/rpc-mechanism)
    example = {"Timestamp": "2016-02-23T12:46:24Z", "Format": "XML", "AccessKeyId": "testid", "Action": "DescribeRegions",
               "SignatureMethod": "HMAC-SHA1", "SignatureNonce": "3ee8c1b8-83d3-44af-a94f-4e0ad82fd6cf", "Version": "2014-05-26",
               "SignatureVersion": "1.0"}
    if signature(example, "testsecret")[1] != "OLeaidS1JvxuMvnyHOwuJ+uX5qY=":
        die("signer does not reproduce the documented example")
    print("oos signer self-test passed")
    sys.exit(0)

class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, *args):
        return None
opener = build_opener(ProxyHandler({}), NoRedirect())
def call(method, url, headers, what):
    try:
        with opener.open(Request(url, method=method, headers=headers), timeout=15) as response:
            return response.status, response.read(65536 + 1)
    except HTTPError as error:
        return error.code, error.read(65536 + 1)
    except (URLError, OSError) as error:
        die(f"{what} unreachable ({type(error).__name__})")
def load(body, what):
    try:
        value = json.loads(body)
    except ValueError:
        die(f"{what} is not JSON")
    if not isinstance(value, dict):
        die(f"{what} is not a JSON object")
    return value

status, token = call("PUT", metadata + "/latest/api/token", {"X-aliyun-ecs-metadata-token-ttl-seconds": "60"}, "metadata token")
if status != 200 or not re.fullmatch(rb"[!-~]{1,1024}", token):
    die(f"metadata service gave no session token (HTTP {status})")
token_header = {"X-aliyun-ecs-metadata-token": token.decode()}
def metadata_get(path, what):
    status, body = call("GET", metadata + path, token_header, what)
    if status != 200:
        die(f"metadata service refused {what} (HTTP {status})")
    return body.decode("utf-8", "replace")
region = metadata_get("/latest/meta-data/region-id", "the region").strip()
if not re.fullmatch(r"[a-z0-9-]{1,64}", region):
    die("metadata service returned no region")
roles = metadata_get("/latest/meta-data/ram/security-credentials/", "the instance role").split()
if len(roles) != 1 or not re.fullmatch(r"[A-Za-z0-9._-]{1,64}", roles[0]):
    die(f"this instance must have exactly one RAM role, found {len(roles)}")
credentials = load(metadata_get("/latest/meta-data/ram/security-credentials/" + roles[0], "the role credentials"), "role credentials")
if credentials.get("Code") != "Success" or not all(isinstance(credentials.get(k), str) and credentials[k]
                                                    for k in ("AccessKeyId", "AccessKeySecret", "SecurityToken")):
    die(f"role credentials unavailable (Code {credentials.get('Code')!r})")

def rpc(action, params):
    query = dict(params, Action=action, Version="2019-06-01", Format="JSON", RegionId=region,
                 AccessKeyId=credentials["AccessKeyId"], SecurityToken=credentials["SecurityToken"],
                 SignatureMethod="HMAC-SHA1", SignatureVersion="1.0", SignatureNonce=uuid.uuid4().hex,
                 Timestamp=datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"))
    canonical, signed = signature(query, credentials["AccessKeySecret"])
    status, body = call("GET", endpoint.format(region=region) + "/?" + canonical + "&Signature=" + encode(signed), {}, "OOS")
    answer = load(body, f"OOS answer to {action} {params['Name']} (HTTP {status})")
    parameter = answer.get("Parameter") if status == 200 else None
    if not isinstance(parameter, dict):
        die(f"OOS {action} {params['Name']}: HTTP {status} {answer.get('Code')} (request {answer.get('RequestId')})")
    return parameter
def acceptable(name, value):
    if not isinstance(value, str) or not 0 < len(value) <= max_chars or not re.fullmatch(r"[ -~]+", value):
        die(f"OOS parameter {name} is not 1..{max_chars} printable ASCII characters")
    return value

if mode == "get":
    print("\n".join(acceptable(name, rpc("GetParameter", {"Name": name}).get("Value")) for name in names))
elif mode == "put" and len(names) == 1:
    rpc("UpdateParameter", {"Name": names[0], "Value": acceptable(names[0], sys.stdin.read())})
else:
    die(f"unknown mode {mode}")
PY

# Strict check of the request JSON (stdin) for this environment; prints "<version> <run_id> <run_attempt> <request_id> <hash>..."
read -r -d '' REQUEST_CHECK_PY <<'PY' || true
import json, re, sys
env, version_pattern, asset_keys = sys.argv[1], sys.argv[2], sys.argv[3:]
def die(message):
    sys.exit("install request: " + message)
def no_duplicates(pairs):
    keys = [k for k, _ in pairs]
    if len(keys) != len(set(keys)):
        die("has a duplicate key")
    return dict(pairs)
def no_constants(name):
    die(f"has the non-JSON constant {name}")
try:
    request = json.loads(sys.stdin.read(), object_pairs_hook=no_duplicates, parse_constant=no_constants)
except ValueError:
    die("is not JSON")
expected = {"version", "environment", "run_id", "run_attempt", "request_id", "asset_url_sha256"}
if not isinstance(request, dict) or set(request) != expected:
    keys = sorted(request) if isinstance(request, dict) else type(request).__name__
    die(f"must be an object with exactly the keys {sorted(expected)}, got {keys}")
version = request["version"]
if not isinstance(version, str) or not re.fullmatch(version_pattern.lstrip("^").rstrip("$"), version):
    die(f"version {version!r} is not MAJOR.MINOR.PATCH[-rc.N]")
if request["environment"] not in ("dev", "prod"):
    die(f"environment {request['environment']!r} is not dev or prod")
if request["environment"] != env:
    die(f"is for environment {request['environment']}, this server is {env}")
for key in ("run_id", "run_attempt"):
    value = request[key]
    if type(value) is not int or not 0 < value < 2**63:
        die(f"{key} must be a positive integer")
request_id = f"{request['run_id']}:{request['run_attempt']}"
if request["request_id"] != request_id:
    die(f"request_id must be the string {request_id!r} (run_id:run_attempt)")
hashes = request["asset_url_sha256"]
if not isinstance(hashes, dict) or set(hashes) != set(asset_keys):
    keys = sorted(hashes) if isinstance(hashes, dict) else type(hashes).__name__
    die(f"asset_url_sha256 must have exactly the keys {sorted(asset_keys)}, got {keys}")
for key in asset_keys:
    if not isinstance(hashes[key], str) or not re.fullmatch(r"[0-9a-f]{64}", hashes[key]):
        die(f"asset_url_sha256.{key} is not 64 lowercase hexadecimal characters")
print(version, request["run_id"], request["run_attempt"], request_id, *(hashes[key] for key in asset_keys))
PY

# The four links (stdin, one per line, in parameter order) against the request's hashes, then each on its own. A hash mismatch prints the
# parameter names and exits 3: the five parameters are written one by one, so a reader can meet links that another run wrote.
read -r -d '' LINKS_PY <<'PY' || true
import hashlib, re, sys
from urllib.parse import urlsplit
hosts, rest = sys.argv[1].split(), sys.argv[2:]
names, hashes, assets = rest[0:4], rest[4:8], rest[8:12]
links = sys.stdin.read().split("\n")[:4]
def die(message):
    sys.exit("install request: " + message)
if len(links) != 4:
    die("four link parameters expected")
mismatched = [name for name, link, digest in zip(names, links, hashes) if hashlib.sha256(link.encode("utf-8")).hexdigest() != digest]
if mismatched:
    print(" ".join(mismatched))
    sys.exit(3)
for asset, url in zip(assets, links):
    # No quotes, backslashes, braces or brackets: the link goes into a curl config line, and curl must not glob it
    if not re.fullmatch(r"[!-~]+", url) or re.search(r'["\\{}\[\]]', url):
        die(f"asset {asset}: URL holds whitespace or one of the characters \" \\ {{ }} [ ]")
    try:
        parts = urlsplit(url)
        port = parts.port
    except ValueError:
        die(f"asset {asset}: URL cannot be parsed")
    # The whole netloc must equal an allowed host: this rules out userinfo, any explicit port (even :443), a trailing dot or other spellings
    if parts.scheme != "https" or parts.netloc not in hosts or port is not None or parts.username is not None:
        die(f"asset {asset}: URL must be https on {' or '.join(hosts)} without user or port; got scheme {parts.scheme!r}, host {parts.netloc!r}")
    if not parts.path.startswith("/") or parts.fragment:
        die(f"asset {asset}: URL has no path or carries a fragment")
PY

oos_call() { python3 -I -c "$OOS_PY" "$METADATA_URL" "$OOS_ENDPOINT" "$MAX_PARAMETER_CHARS" "$@"; }

prepare_state() {
  [ -d "$STATE_DIR" ] || mkdir -m 0700 "$STATE_DIR" 2>/dev/null || state_unavailable
  [ "$(owner_mode "$STATE_DIR")" = "root 700" ] || fail "$STATE_DIR must be root-owned 0700"
  if [ -e "$PROCESSED" ]; then
    [ "$(owner_mode "$PROCESSED")" = "root 600" ] || fail "$PROCESSED must be root-owned 0600"
  else
    (umask 077 && : > "$PROCESSED")
  fi
  if [ -e "$ALERTS" ]; then
    [ "$(owner_mode "$ALERTS")" = "root 600" ] || fail "$ALERTS must be root-owned 0600"
  fi
}

# True when the request was taken up before, whatever its outcome
processed_has() { awk -F'\t' -v key="$1" '$1 == key { found = 1 } END { exit !found }' "$PROCESSED" 2>/dev/null; }

record_request() {
  printf '%s\t%s\t%s\t%s\n' "$1" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$2" "${3:-}" >> "$PROCESSED"
}

# The result parameter only reports to the deploy job; a failed write is logged and alerted (hourly), it changes nothing here
write_result() {
  local status="$1" cause="${2:-}" alert_key=""
  python3 -I -c '
import datetime, json, sys
print(json.dumps({"request_id": sys.argv[1], "status": sys.argv[2], "reason": sys.argv[3] or None,
                  "time": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")}, separators=(",", ":")), end="")' \
    "$request_id" "$status" "$cause" | oos_call put "$RESULT_PARAMETER_PREFIX-$target_env" && return 0
  log "could not write $status for request $request_id to $RESULT_PARAMETER_PREFIX-$target_env"
  if alert_due result-write; then send_alert result-write "could not write $status to $RESULT_PARAMETER_PREFIX-$target_env"; fi
}

# No redirect is followed and no credential is sent: the URL already is GitHub's final signed download link. It reaches curl through a
# config on stdin (-K -), not the command line; -q first so no curlrc applies, -g so nothing in it is globbed.
download_asset() {
  local name="$1" out="$2" seconds="$3" i url=""
  for i in "${!asset_names[@]}"; do
    [ "${asset_names[$i]}" != "$name" ] || url="${asset_links[$i]}"
  done
  [ -n "$url" ] || fail "install request has no URL for $name"
  printf 'url = "%s"\n' "$url" | timeout "$seconds" curl -q -g -fsS --proto '=https' --proto-redir '=https' --tlsv1.2 \
    -L --max-redirs 0 --max-filesize "$MAX_BUNDLE_BYTES" -o "$out" -K - \
    || fail "could not download release asset $name (the signed link may have expired; start a new deploy run)"
}

# Downloads the requested release into a new directory under releases/ and verifies it; sets staged (the unpacked bundle) and commit
fetch_release() {
  local version="$1" incoming archive top expected_sha seconds
  incoming="$(mktemp -d "$releases/.incoming.XXXXXX")"
  cleanup_paths+=("$incoming")
  top="forcitis-one-$version"
  archive="$incoming/bundle.tar.gz"
  log "fetching $top from the GitHub release v$version"
  stage=download
  download_asset "$top.tar.gz.sha256" "$incoming/bundle.sha256" "$SMALL_ASSET_TIMEOUT_SECONDS"
  download_asset "$top.release" "$incoming/statement" "$SMALL_ASSET_TIMEOUT_SECONDS"
  download_asset "$top.release.sig" "$incoming/statement.sig" "$SMALL_ASSET_TIMEOUT_SECONDS"
  # The bundle gets what is left once the migration and two health checks are reserved, at most DOWNLOAD_TIMEOUT_SECONDS
  seconds=$(($(remaining) - MIGRATION_TIMEOUT_SECONDS - SWITCH_RESERVE_SECONDS))
  [ "$seconds" -le "$DOWNLOAD_TIMEOUT_SECONDS" ] || seconds="$DOWNLOAD_TIMEOUT_SECONDS"
  [ "$seconds" -gt 0 ] || fail "no time left to download the bundle within this run's budget"
  download_asset "$top.tar.gz" "$archive" "$seconds"
  [ "$(wc -c < "$archive")" -le "$MAX_BUNDLE_BYTES" ] || fail "bundle larger than $MAX_BUNDLE_BYTES bytes"
  log "fetched $(wc -c < "$archive" | tr -d ' ') bytes"
  stage=verification
  expected_sha="$(cut -d' ' -f1 "$incoming/bundle.sha256")"
  [[ "$expected_sha" =~ ^[0-9a-f]{64}$ ]] || fail "cannot read the bundle's .sha256 asset"
  [ "$(sha256sum < "$archive" | cut -d' ' -f1)" = "$expected_sha" ] || fail "bundle sha256 differs from the release's .sha256 asset"

  verify_statement "$archive" "$incoming/statement" "$version"
  check_tar_entries "$archive" "$top"
  verify_statement_members "$archive" "$incoming/statement" "$version"
  tar -xzf "$archive" -C "$incoming" --no-same-owner --no-same-permissions
  staged="$incoming/$top"
  verify_bundle_dir "$staged"
  [ "$(manifest_field "$staged/manifest.json" version)" = "$version" ] || fail "manifest.json is not for the requested version $version"
  [ "$(manifest_field "$staged/manifest.json" gitSha)" = "$commit" ] || fail "manifest.json is not for commit $commit named by the signed statement"
}

place() {
  local version="$1" env="$2" existing
  fetch_release "$version"
  stage=placement
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
  rm -rf "$(dirname "$staged")"
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
  args+=(--WORKFORCE_SHORT_NAME_ENABLED=false --WECOM_ENABLED=false --APPROVAL_DEADLINE_ENABLED=false
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
    -p RuntimeMaxSec="$MIGRATION_TIMEOUT_SECONDS" -p StandardOutput="append:$log_file" -p StandardError="append:$log_file" \
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
  open_lock
  flock -n 9 || fail "another release operation is running"
}

# Reads and checks this environment's request; sets version, request_id and the asset links. Sets nothing_new, and
# says nothing, for the placeholder or a request taken up before (whatever its outcome).
read_request() {
  local raw header links mismatched rc=0 i key h0 h1 h2 h3
  local -a hashes names assets
  stage="read-parameters"
  # Error text is captured, not printed: a cause already reported must not reach the log again on the next round
  raw="$(oos_call get "$REQUEST_PARAMETER_PREFIX-$target_env" 2>&1)" \
    || fail "cannot read the request parameter $REQUEST_PARAMETER_PREFIX-$target_env: $raw"
  [ "$raw" != "$REQUEST_PLACEHOLDER" ] || { nothing_new=1; return 0; }
  stage="invalid-request"
  alert_key="value-$(printf '%s' "$raw" | sha256sum | cut -c1-16)"
  # shellcheck disable=SC2086
  header="$(printf '%s' "$raw" | python3 -I -c "$REQUEST_CHECK_PY" "$target_env" "$VERSION_PATTERN" $REQUEST_ASSET_PARAMETERS 2>&1)" \
    || fail "$header"
  read -r version _ _ request_id h0 h1 h2 h3 <<< "$header"
  hashes=("$h0" "$h1" "$h2" "$h3")
  alert_key="$request_id"
  ! processed_has "$request_id" || { nothing_new=1; return 0; }

  names=() assets=()
  for key in $REQUEST_ASSET_PARAMETERS; do names+=("$REQUEST_PARAMETER_PREFIX-$target_env-$key"); done
  assets=("forcitis-one-$version.tar.gz" "forcitis-one-$version.tar.gz.sha256" "forcitis-one-$version.release" "forcitis-one-$version.release.sig")
  stage="read-parameters"
  links="$(oos_call get "${names[@]}" 2>&1)" || fail "cannot read the link parameters: $links"
  stage="link-mismatch"
  mismatched="$(printf '%s\n' "$links" | python3 -I -c "$LINKS_PY" "$ASSET_HOSTS" "${names[@]}" "${hashes[@]}" "${assets[@]}" 2>&1)" || rc=$?
  # Not a replay but a write race between two runs: nothing is recorded, so the same request is taken up once the parameters agree
  [ "$rc" -ne 3 ] || fail "link parameters $mismatched do not match the request's asset_url_sha256 (written by different deploy runs); rewrite them and resend"
  stage="invalid-link"
  # Links that match the request's own hashes but are not acceptable will not get better: the request ends failed and is not retried
  if [ "$rc" -ne 0 ]; then
    request_in_progress="$request_id"
    fail "$mismatched"
  fi

  # Split in memory: a here-string could be backed by a temporary file, and these lines hold the signed links
  asset_names=("${assets[@]}") asset_links=()
  for i in 0 1 2 3; do
    asset_links+=("${links%%$'\n'*}")
    links="${links#*$'\n'}"
  done
  log "install request $request_id: v$version for $target_env"
}

# Seconds left of this run's budget (PULL_BUDGET_SECONDS)
remaining() { echo $((pull_deadline - SECONDS)); }

# Stopped by a signal (the unit timing out or being stopped): a switch not yet confirmed is undone, then the request ends `failed`
# (cause terminated) with its result and an alert. forcitis-one-release finish-interrupted (ExecStopPost) covers the cases a signal
# handler cannot, such as SIGKILL.
on_term() {
  local interrupted="$stage" before target
  trap - TERM INT
  stage="terminated"
  stop_orphan_migrations
  if [ -f "$ACTIVATION" ] && read -r before target _ < "$ACTIVATION"; then
    if [ "$before" != "-" ] && [ "$before" != "$target" ]; then
      log "stopped while switching to $target; switching back to $before"
      switch_back "$before"
    elif [ "$before" = "-" ]; then
      fail "stopped by a signal during $interrupted; first installation: the switch to $target was not confirmed and the service is still running; check it by hand (RELEASE.md §3, half-switched state)"
    fi
    rm -f "$ACTIVATION"
  fi
  fail "stopped by a signal during $interrupted"
}

# A migration started by an interrupted run would otherwise keep its JVM running
stop_orphan_migrations() {
  systemctl stop 'forcitis-one-migrate-*' 2>/dev/null || log "could not stop leftover migration units (systemctl list-units 'forcitis-one-migrate-*')"
}

# An operator's rollback stopped by a signal is not undone automatically: the marker stays for the operator (RELEASE.md §3)
on_rollback_term() {
  trap - TERM INT
  fail "rollback stopped by a signal before its health check finished; $ACTIVATION names the releases involved; check with forcitis-one-release status (RELEASE.md §3, half-switched state)"
}

cmd_pull() {
  [ "$#" -eq 0 ] || fail "pull takes no arguments; the request is read from OOS (RELEASE.md §3)"
  local target_env pinned_expected profile running new first_install=false version commit staged stale
  request_id="" alert_key="" nothing_new="" pulling=1
  pull_deadline=$((SECONDS + PULL_BUDGET_SECONDS))
  open_lock
  # Another pull or an operator's rollback is running: this round has nothing to do
  flock -n 9 || exit 0
  trap on_term TERM INT
  stage=configuration
  prepare_state
  read_release_source
  profile="$(env_value SPRING_PROFILES_ACTIVE)"
  [ "$profile" = "$target_env" ] || fail "$RELEASE_SOURCE says '$target_env', but one.env SPRING_PROFILES_ACTIVE is '$profile'"
  # An older manual setup kept static files in a real current/ directory; switching would discard them
  [[ ! -e "$current_link" || -L "$current_link" ]] \
    || fail "$current_link is a directory, not the release link; move pinned files to $PINNED and remove it first (C-4 E-10)"
  assert_root_owned "$BASE" "$releases"
  # Left by a run that was killed; with the lock held no other run can be using one
  stale="$(find "$releases" -mindepth 1 -maxdepth 1 -name '.incoming.*' | wc -l | tr -d ' ')"
  if [ "$stale" -gt 0 ]; then
    find "$releases" -mindepth 1 -maxdepth 1 -name '.incoming.*' -exec rm -rf {} +
    log "removed $stale incomplete download(s) left by an interrupted run"
  fi
  read_request
  [ -z "$nothing_new" ] || exit 0

  stage="replay-record"
  # Taken up before anything is installed, so a crash half-way is never replayed by the same request; without that record, no install
  record_request "$request_id" started "v$version" \
    || fail "could not record request $request_id as started in $PROCESSED; not installing without replay protection"
  request_in_progress="$request_id"
  write_result started

  stage="version-rejected"
  [[ "$target_env" != prod || "$version" != *-* ]] || fail "prod takes released versions only, not the pre-release $version"
  running="$(linked_version "$current_link")"
  if [ -n "$running" ] && version_lower "$version" "$running"; then
    fail "$version is lower than the running $running; going back is an explicit operator rollback (forcitis-one-release rollback), never a deployment"
  fi
  # The same version already running and healthy: nothing to install, nothing to restart
  if [ -n "$running" ] && [ "$running" = "$version" ] && serving_now; then
    record_request "$request_id" succeeded "v$version already current" || log "could not record request $request_id as succeeded"
    request_in_progress=""
    log "release $version is already current and healthy; nothing to do"
    write_result succeeded
    return 0
  fi
  stage=configuration
  if [ -n "$pinned_expected" ]; then
    [ -s "$PINNED/$pinned_expected" ] || fail "WECOM_VERIFY_FILE names $pinned_expected, missing in $PINNED; WeCom would drop the trusted domain (C-4 E-10)"
  fi
  place "$version" "$target_env"
  stage=migration
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
  stage=budget
  [ "$(remaining)" -ge $((MIGRATION_TIMEOUT_SECONDS + SWITCH_RESERVE_SECONDS)) ] \
    || fail "only $(remaining)s left of this run's budget; not starting the migration and the switch"
  stage=migration
  migrate "$version" "$first_install"
  stage=budget
  [ "$(remaining)" -ge "$SWITCH_RESERVE_SECONDS" ] || fail "only $(remaining)s left of this run's budget; not switching"
  stage=activation
  activate "$version"
  record_request "$request_id" succeeded "v$version $commit" || log "could not record request $request_id as succeeded"
  request_in_progress=""
  write_result succeeded
  rm -f "$ACTIVATION"
  prune
}

# ExecStopPost of forcitis-one-release-pull.service: whatever stopped the run, an unconfirmed switch is undone and a request left
# `started` ends `failed` (cause terminated), with its result and an alert
cmd_finish_interrupted() {
  [ "$#" -eq 0 ] || fail "finish-interrupted takes no arguments"
  local target_env pinned_expected before="" target="" owner="" ids id note
  request_id="" alert_key="" pulling=1 stage="terminated"
  open_lock
  flock -n 9 || exit 0
  [ -d "$STATE_DIR" ] || exit 0
  read_release_source
  stop_orphan_migrations
  ids="$(awk -F'\t' '{ state[$1] = $3 } END { for (id in state) if (state[id] == "started") print id }' "$PROCESSED" 2>/dev/null || true)"
  [ ! -f "$ACTIVATION" ] || read -r before target owner < "$ACTIVATION" || true
  # Only a switch made by a pull whose request is still `started` is undone here. Any other marker (an operator's rollback, or a request
  # already recorded) stays for an operator; this runs after every pull, so the alert is keyed by the marker's content and sent once.
  if [ -n "$target" ] && { [ "$owner" = "-" ] || ! printf '%s\n' "$ids" | grep -qx -- "$owner"; }; then
    alert_key="activation-$(sha256sum < "$ACTIVATION" | cut -c1-16)"
    log "found an unconfirmed switch marker (${before} -> ${target}, request ${owner}) that no interrupted pull owns; left for an operator"
    if alert_due unconfirmed-switch-marker; then
      send_alert unconfirmed-switch-marker "found an unconfirmed switch marker (${before} -> ${target}, request ${owner}) and left it as it is; check with forcitis-one-release status (RELEASE.md §3, half-switched state)"
    fi
    alert_key=""
    target=""
  fi
  note="the run stopped before it finished (${SERVICE_RESULT:-unknown})"
  if [ -n "$target" ]; then
    if [ "$before" = "-" ]; then
      # No release to return to; the marker stays so that status shows the unconfirmed first installation
      log "first installation: the switch to $target was not confirmed and the service is still running; check it by hand (RELEASE.md §3, half-switched state)"
      note="$note; first installation: the switch to $target was not confirmed and the service is still running"
    elif [ "$before" = "$target" ]; then
      log "reinstalling the running release $target was not confirmed; $target is still current and running"
      note="$note; reinstalling the running release $target was not confirmed; it is still current and running"
      rm -f "$ACTIVATION"
    else
      log "the run stopped while switching to $target; switching back to $before"
      switch_back "$before"
      note="$note; switched back: $switch_back_outcome"
    fi
  fi
  for id in $ids; do
    request_id="$id" alert_key="$id"
    record_request "$id" failed "terminated: the run stopped before it finished" || log "could not record request $id as failed"
    log "FAILED: request $id: $note"
    write_result failed terminated
    send_alert terminated "$note"
  done
}

open_lock() {
  [ -d "$RUN_DIR" ] || mkdir -m 0700 "$RUN_DIR" 2>/dev/null || true
  exec 9>"$LOCK"
}

cmd_rollback() {
  [ "$#" -le 1 ] || fail "rollback takes at most one version"
  local target="${1:-}"
  assert_root_owned "$BASE" "$releases"
  lock
  if [ -z "$target" ]; then
    target="$(linked_version "$previous_link")"
    [ -n "$target" ] || fail "no previous release recorded; name a version"
  fi
  valid_version "$target" || fail "invalid version '$target'"
  [ "$target" != "$(linked_version "$current_link")" ] || fail "$target is already current"
  trap on_rollback_term TERM INT
  activate "$target"
  rm -f "$ACTIVATION"
}

cmd_status() {
  echo "current:  $(linked_version "$current_link")"
  echo "previous: $(linked_version "$previous_link")"
  echo "installer sha256: $(sha256sum "$SELF" | cut -d' ' -f1)"
  echo "installed releases:"
  local dir
  for dir in "$releases"/*; do
    [ ! -d "$dir" ] || printf '  %s\n' "$(basename "$dir")"
  done
  [ ! -f "$releases/HISTORY" ] || { echo "recent history:"; tail -5 "$releases/HISTORY"; }
  [ ! -f "$PROCESSED" ] || { echo "recent install requests:"; tail -5 "$PROCESSED"; }
  [ ! -f "$ALERTS" ] || { echo "recent alerts:"; tail -5 "$ALERTS"; }
  if [ -f "$ACTIVATION" ]; then
    echo "UNCONFIRMED SWITCH (before target request; - = none / manual rollback): $(cat "$ACTIVATION")"
    echo "  see RELEASE.md §3, half-switched state"
  fi
}

main() {
  [ "$(id -u)" -eq 0 ] || fail "run as root (forcitis-one-release-pull.service runs as root)"
  local command="${1:-}"
  shift || true
  case "$command" in
    pull) cmd_pull "$@" ;;
    finish-interrupted) cmd_finish_interrupted "$@" ;;
    rollback) cmd_rollback "$@" ;;
    status) [ "$#" -eq 0 ] || fail "status takes no arguments"; cmd_status ;;
    *) fail "usage: pull | finish-interrupted | rollback [<version>] | status" ;;
  esac
}

# Sourcing defines the functions only (the request checks are exercised that way); running the file acts
if [ "${BASH_SOURCE[0]}" = "$0" ]; then
  main "$@"
fi
