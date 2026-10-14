#!/usr/bin/env bash
# FORCITIS one · C-7 · 证书到期与续期检查（只读）。由 one-cert-check.timer 每日以 root 运行。
# 结果写一行 JSON 到 journal（标签 one-cert-check）与 /data/forcitis-one/logs/ops/cert-check.json（Logtail 采集）。
# /etc/forcitis-one/ops-alert.env（root 0600）与 one_alert_send 都在时，告警同时以企业微信应用消息发给平台负责人；否则只记日志。
# 退出码：0 正常；1 告警；2 检查本身失败（文件缺失、命令失败），按告警处理。
set -uo pipefail

DOMAIN="${ONE_DOMAIN:-one.forcitis.com}"
CERT_FILE="${ONE_CERT_FILE:-/etc/letsencrypt/live/${DOMAIN}/fullchain.pem}"
WARN_DAYS="${ONE_CERT_WARN_DAYS:-20}"
CRIT_DAYS="${ONE_CERT_CRIT_DAYS:-7}"
LOG_DIR="${ONE_OPS_LOG_DIR:-/data/forcitis-one/logs/ops}"
ALERT_ENV=/etc/forcitis-one/ops-alert.env
ALERT_SENDER=/usr/local/sbin/one_alert_send

now=$(date +%s)
days_left() { local end; end=$(openssl x509 -noout -enddate 2>/dev/null | cut -d= -f2) || return 1; [[ -n "$end" ]] || return 1; echo $(( ($(date -d "$end" +%s) - now) / 86400 )); }

file_days=$(days_left < "$CERT_FILE") || file_days=""
# Read the served certificate too: renewal can succeed while nginx keeps serving the old one if the reload failed.
served_days=$(echo | openssl s_client -connect 127.0.0.1:443 -servername "$DOMAIN" 2>/dev/null | days_left) || served_days=""
renew_result=$(systemctl show -p Result --value certbot-renew.service 2>/dev/null)
timer_active=$(systemctl is-active certbot-renew.timer 2>/dev/null)

level=OK; reasons=()
[[ -z "$file_days" ]]   && { level=P1; reasons+=("cannot read $CERT_FILE"); }
[[ -z "$served_days" ]] && { level=P1; reasons+=("cannot read served certificate on 127.0.0.1:443"); }
min_days=$(printf '%s\n' "$file_days" "$served_days" | grep -E '^-?[0-9]+$' | sort -n | head -1)
if [[ -n "$min_days" ]]; then
  if   (( min_days < CRIT_DAYS )); then level=P1; reasons+=("certificate expires in ${min_days} days (< ${CRIT_DAYS})")
  elif (( min_days < WARN_DAYS )); then [[ $level == OK ]] && level=P2; reasons+=("certificate expires in ${min_days} days (< ${WARN_DAYS})")
  fi
fi
[[ -n "$file_days" && -n "$served_days" && "$file_days" != "$served_days" ]] && { [[ $level == OK ]] && level=P2; reasons+=("served certificate differs from renewed file; nginx not reloaded"); }
[[ "$renew_result" != success ]] && { [[ $level == OK ]] && level=P2; reasons+=("certbot-renew.service last result: ${renew_result:-unknown}"); }
[[ "$timer_active" != active ]] && { [[ $level == OK ]] && level=P2; reasons+=("certbot-renew.timer is ${timer_active:-unknown}"); }

if [[ $level == OK ]]; then message="cert ok: ${DOMAIN} ${min_days} days left"
else message="ALERT ${level} one.cert: ${DOMAIN}: $(IFS='; '; echo "${reasons[*]}")"; fi

json=$(printf '{"time":"%s","logger":"one-cert-check","level":"%s","message":"%s","domain":"%s","daysLeftFile":%s,"daysLeftServed":%s,"renewResult":"%s","timer":"%s"}' \
  "$(date -Is)" "$([[ $level == OK ]] && echo INFO || echo ERROR)" "$message" "$DOMAIN" "${file_days:-null}" "${served_days:-null}" "$renew_result" "$timer_active")
logger -t one-cert-check -- "$json"
install -d -o root -g root -m 0750 "$LOG_DIR" && echo "$json" >> "$LOG_DIR/cert-check.json"
echo "$json"

[[ $level == OK ]] && exit 0

# Delivery to the platform owner as a WeCom application message (one_alert_send); without it the alert stays in the logs only.
if [[ -x "$ALERT_SENDER" && -r "$ALERT_ENV" ]]; then
  "$ALERT_SENDER" "$level" "$message" >/dev/null || logger -t one-cert-check -- "ALERT P2 one.cert: alert delivery failed"
fi
[[ -z "$file_days" || -z "$served_days" ]] && exit 2
exit 1
