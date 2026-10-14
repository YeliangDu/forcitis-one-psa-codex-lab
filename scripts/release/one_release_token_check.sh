#!/usr/bin/env bash
# FORCITIS one · RELEASE.md §7 R-13 · expiry check of the read-only GitHub token the release installer uses (read-only check).
# Run daily as root by one-release-token-check.timer; installed as /usr/local/sbin/one_release_token_check.sh.
# One JSON line goes to the journal (tag one-release-token-check) and /data/forcitis-one/logs/ops/release-token-check.json.
# Alerts through /usr/local/sbin/one_alert_send: P2 under 14 days left, P1 under 3 days, expired, or token / date file missing.
# Exit codes: 0 fine; 1 alert; 2 the check itself failed (file missing or unreadable), treated as an alert.
set -uo pipefail

TOKEN_FILE="${ONE_RELEASE_TOKEN_FILE:-/etc/forcitis-one/github-release-token}"
EXPIRES_FILE="${ONE_RELEASE_TOKEN_EXPIRES:-/etc/forcitis-one/github-release-token.expires}"
WARN_DAYS="${ONE_RELEASE_TOKEN_WARN_DAYS:-14}"
CRIT_DAYS="${ONE_RELEASE_TOKEN_CRIT_DAYS:-3}"
LOG_DIR="${ONE_OPS_LOG_DIR:-/data/forcitis-one/logs/ops}"
ALERT_SENDER="${ONE_ALERT_SENDER:-/usr/local/sbin/one_alert_send}"

level=OK; reasons=(); days=""; broken=0
if [[ ! -s "$TOKEN_FILE" ]]; then
  level=P1; broken=1; reasons+=("token file $TOKEN_FILE missing or empty")
fi
expires="$(tr -d '[:space:]' < "$EXPIRES_FILE" 2>/dev/null)" || expires=""
if [[ ! "$expires" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]] || ! end=$(date -u -d "$expires" +%s 2>/dev/null); then
  level=P1; broken=1; reasons+=("expiry file $EXPIRES_FILE missing or not YYYY-MM-DD")
else
  today=$(date -u -d "$(date -u +%Y-%m-%d)" +%s)
  days=$(( (end - today) / 86400 ))
  # The installer treats the token as expired from its expiry date on
  if (( days <= 0 )); then level=P1; reasons+=("GitHub release token expired on ${expires}; deployments fail until it is rotated")
  elif (( days < CRIT_DAYS )); then level=P1; reasons+=("GitHub release token expires on ${expires} (${days} days left, < ${CRIT_DAYS})")
  elif (( days < WARN_DAYS )); then [[ $level == OK ]] && level=P2; reasons+=("GitHub release token expires on ${expires} (${days} days left, < ${WARN_DAYS})")
  fi
fi

if [[ $level == OK ]]; then message="release token ok: expires ${expires}, ${days} days left"
else message="ALERT ${level} one.release-token: $(IFS='; '; echo "${reasons[*]}"); rotate per RELEASE.md R-13"; fi

json=$(printf '{"time":"%s","logger":"one-release-token-check","level":"%s","message":"%s","expires":"%s","daysLeft":%s}' \
  "$(date -Is)" "$([[ $level == OK ]] && echo INFO || echo ERROR)" "$message" "${expires:-}" "${days:-null}")
logger -t one-release-token-check -- "$json"
install -d -o root -g root -m 0750 "$LOG_DIR" 2>/dev/null && echo "$json" >> "$LOG_DIR/release-token-check.json"
echo "$json"

[[ $level == OK ]] && exit 0

if [[ -x "$ALERT_SENDER" ]]; then
  "$ALERT_SENDER" "$level" "$message" >/dev/null || logger -t one-release-token-check -- "ALERT P2 one.release-token: alert delivery failed"
fi
(( broken )) && exit 2
exit 1
