#!/usr/bin/env bash
# FORCITIS one · INFRA_RUNBOOK §8 · 节点时间同步检查（只读）。由 one-time-sync-check.timer 每 5 分钟以 root 运行。
# 结果写一行 JSON 到 journal（标签 one-time-sync-check）与 /data/forcitis-one/logs/ops/time-sync.json（Logtail 采集）。
# 告警时经 one_alert_send 推送；ONE_TIME_SYNC_DRILL=1 时消息标「演练」。
# 退出码：0 正常；1 告警；2 检查本身失败（chronyc 不可用或输出无法解析），按 P1 处理。
set -uo pipefail

WARN_MS="${ONE_TIME_WARN_MS:-100}"
CRIT_MS="${ONE_TIME_CRIT_MS:-1000}"
MAX_REF_AGE_S="${ONE_TIME_MAX_REF_AGE_S:-7200}"
LOG_DIR="${ONE_OPS_LOG_DIR:-/data/forcitis-one/logs/ops}"
ALERT_ENV=/etc/forcitis-one/ops-alert.env
ALERT_SENDER="${ONE_ALERT_SENDER:-/usr/local/sbin/one_alert_send}"
TAG=""; [[ "${ONE_TIME_SYNC_DRILL:-0}" == 1 ]] && TAG=" [演练]"

tracking=$(chronyc -n tracking 2>/dev/null) || tracking=""
offset_ms=$(awk -F': ' '/^System time/{split($2,a," "); v=a[1]*1000; if (a[3]=="slow") v=-v; printf "%.3f", v}' <<<"$tracking")
leap=$(awk -F': ' '/^Leap status/{print $2}' <<<"$tracking")
ref_time=$(awk -F': ' '/^Ref time \(UTC\)/{print $2}' <<<"$tracking")
ref_age=""; [[ -n "$ref_time" ]] && ref_epoch=$(date -u -d "$ref_time" +%s 2>/dev/null) && ref_age=$(( $(date +%s) - ref_epoch ))

level=OK; failed=0; reasons=()
if [[ -z "$offset_ms" || -z "$leap" || -z "$ref_age" ]]; then
  level=P1; failed=1; reasons+=("cannot read chronyc tracking")
else
  abs_ms=${offset_ms#-}
  if awk -v a="$abs_ms" -v c="$CRIT_MS" 'BEGIN{exit !(a>c)}'; then level=P1; reasons+=("clock offset ${offset_ms} ms (> ${CRIT_MS})")
  elif awk -v a="$abs_ms" -v w="$WARN_MS" 'BEGIN{exit !(a>w)}'; then level=P2; reasons+=("clock offset ${offset_ms} ms (> ${WARN_MS})")
  fi
  [[ "$leap" != Normal ]] && { [[ $level == OK ]] && level=P2; reasons+=("leap status: ${leap}"); }
  (( ref_age > MAX_REF_AGE_S )) && { [[ $level == OK ]] && level=P2; reasons+=("last sync ${ref_age} s ago (> ${MAX_REF_AGE_S})"); }
fi

if [[ $level == OK ]]; then message="time sync ok: offset ${offset_ms} ms"
else message="ALERT ${level} one.time${TAG}: $(hostname -s): $(IFS='; '; echo "${reasons[*]}")"; fi

json=$(printf '{"time":"%s","logger":"one-time-sync-check","level":"%s","message":"%s","offsetMs":%s,"leapStatus":"%s","refAgeSeconds":%s}' \
  "$(date -Is)" "$([[ $level == OK ]] && echo INFO || echo ERROR)" "$message" "${offset_ms:-null}" "$leap" "${ref_age:-null}")
logger -t one-time-sync-check -- "$json"
install -d -o root -g root -m 0750 "$LOG_DIR" && echo "$json" >> "$LOG_DIR/time-sync.json"
echo "$json"

[[ $level == OK ]] && exit 0
if [[ -x "$ALERT_SENDER" && -r "$ALERT_ENV" ]]; then
  "$ALERT_SENDER" "$level" "$message" >/dev/null || logger -t one-time-sync-check -- "ALERT P2 one.time: alert delivery failed"
fi
(( failed )) && exit 2
exit 1
