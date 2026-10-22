#!/usr/bin/env bash
# FORCITIS one · C-7 · ClamAV 健康检查（OPS-1021-OPS-02 第 6 条，只读）。由 one-clamav-check.timer 每 5 分钟以 root 运行。
# 检查：clamd 能否应答（含经 socket 的 PING）、病毒库库龄（clamd 回报的库日期）、freshclam 服务是否运行。
# 结果写一行 JSON 到 journal（标签 one-clamav-check）与 /data/forcitis-one/logs/ops/clamav-check.json；告警行 message 以 ALERT P1 / ALERT P2 开头，可被 SLS 兜底规则捕获。
# 推送去重：级别变化时发；持续告警每 ONE_CLAMAV_REMIND_HOURS（默认 6）小时重发一次；恢复写日志不推送。
# 级别：clamd 不应答（检测不可用，上传一律拒收）→ P1；库龄 > ONE_CLAMAV_MAX_AGE_HOURS（默认 48）或读不到库日期、freshclam 未运行 → P2。
# 演练：ONE_CLAMAV_DRILL=1 时推送级别用 TEST、消息前缀「演练」。退出码：0 正常；1 告警。
set -uo pipefail

MAX_AGE_HOURS="${ONE_CLAMAV_MAX_AGE_HOURS:-48}"
REMIND_HOURS="${ONE_CLAMAV_REMIND_HOURS:-6}"
CONF=/etc/forcitis-one/clamd.conf
CLAMDSCAN=/usr/local/bin/clamdscan
LOG_DIR="${ONE_OPS_LOG_DIR:-/data/forcitis-one/logs/ops}"
STATE_DIR="${ONE_CLAMAV_STATE_DIR:-/var/lib/one-clamav-check}"
ALERT_ENV=/etc/forcitis-one/ops-alert.env
ALERT_SENDER=/usr/local/sbin/one_alert_send

now=$(date +%s)
level=OK; reasons=(); db_age_hours=null

if ! "$CLAMDSCAN" --config-file="$CONF" --ping 3:2 >/dev/null 2>&1; then
  level=P1; reasons+=("clamd not answering on its socket (detection unavailable)")
else
  # clamd reports "ClamAV <version>/<db-version>/<db-date>" for the database it has loaded.
  db_date=$("$CLAMDSCAN" --config-file="$CONF" --version 2>/dev/null | awk -F/ 'NR==1{print $3}')
  db_epoch=$(date -d "$db_date" +%s 2>/dev/null) || db_epoch=""
  if [[ -z "$db_epoch" ]]; then
    level=P2; reasons+=("cannot read signature database date from clamd")
  else
    db_age_hours=$(( (now - db_epoch) / 3600 ))
    if (( db_age_hours > MAX_AGE_HOURS )); then level=P2; reasons+=("signature database is ${db_age_hours} h old (> ${MAX_AGE_HOURS} h); freshclam not updating")
    fi
  fi
fi
fresh_state=$(systemctl is-active forcitis-one-freshclam 2>/dev/null)
if [[ "$fresh_state" != active ]]; then
  [[ $level == OK ]] && level=P2; reasons+=("forcitis-one-freshclam is ${fresh_state:-unknown}")
fi

if [[ $level == OK ]]; then message="clamav ok: signature database ${db_age_hours} h old"
else message="ALERT ${level} one.clamav: $(IFS='; '; echo "${reasons[*]}")"; fi
[[ -n "${ONE_CLAMAV_DRILL:-}" ]] && message="演练 ${message}"

json=$(printf '{"time":"%s","logger":"one-clamav-check","level":"%s","message":"%s","dbAgeHours":%s,"freshclam":"%s"}' \
  "$(date -Is)" "$([[ $level == OK ]] && echo INFO || echo ERROR)" "$message" "$db_age_hours" "$fresh_state")
logger -t one-clamav-check -- "$json"
install -d -o root -g root -m 0750 "$LOG_DIR" && echo "$json" >> "$LOG_DIR/clamav-check.json"
echo "$json"

install -d -o root -g root -m 0700 "$STATE_DIR"
prev_level=$(cut -d' ' -f1 "$STATE_DIR/last" 2>/dev/null); prev_sent=$(cut -d' ' -f2 "$STATE_DIR/last" 2>/dev/null)
prev_level=${prev_level:-OK}; prev_sent=${prev_sent:-0}
if [[ $level == OK ]]; then echo "OK 0" > "$STATE_DIR/last"; exit 0; fi

send=0
[[ $level != "$prev_level" ]] && send=1
(( now - prev_sent >= REMIND_HOURS * 3600 )) && send=1
if (( send )); then
  push_level=$level; [[ -n "${ONE_CLAMAV_DRILL:-}" ]] && push_level=TEST
  if [[ -x "$ALERT_SENDER" && -r "$ALERT_ENV" ]]; then
    "$ALERT_SENDER" "$push_level" "$message" >/dev/null || logger -t one-clamav-check -- "ALERT P2 one.clamav: alert delivery failed"
  fi
  echo "$level $now" > "$STATE_DIR/last"
else
  echo "$level $prev_sent" > "$STATE_DIR/last"
fi
exit 1
