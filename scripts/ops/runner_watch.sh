#!/usr/bin/env bash
# CI 运行器巡检（卡 #736 第 9 项，用户「批准（推荐）」10-28）：launchd 每 5 分钟跑一次（com.forcitis.runner-watch）。
# 每个运行器服务不在运行就 kickstart 一次，等 RUNNER_WATCH_WAIT 秒（默认 60）再查；还没起来就往 alert.log 追加一行并弹 macOS 通知。
# 运行器 plist 已加 KeepAlive，正常情况下 launchd 自己会拉起；本脚本兜的是 KeepAlive 也拉不起来（反复崩溃被节流、plist 被 svc.sh 重装覆盖等）。
# 临时停巡检：touch ~/.modernize/psa/ops/runner-watch/pause，用完删。
# 环境变量（自测用）：RUNNER_LABELS（空格分隔）、LAUNCHCTL_BIN、OSASCRIPT_BIN、RUNNER_WATCH_STATE、RUNNER_WATCH_WAIT
set -u
labels="${RUNNER_LABELS:-actions.runner.FORCITIS-forcitis-one-psa.mac-one-1 actions.runner.FORCITIS-forcitis-one-psa.mac-one-2 actions.runner.FORCITIS-forcitis-one-psa.mac-one-3 actions.runner.FORCITIS-forcitis-one-psa.mac-one-4}"
launchctl_bin="${LAUNCHCTL_BIN:-launchctl}"
osascript_bin="${OSASCRIPT_BIN:-osascript}"
state="${RUNNER_WATCH_STATE:-$HOME/.modernize/psa/ops/runner-watch}"
wait_s="${RUNNER_WATCH_WAIT:-60}"
domain="gui/$(id -u)"
mkdir -p "$state"
[ -e "$state/pause" ] && exit 0

running() { "$launchctl_bin" print "$domain/$1" 2>/dev/null | grep -qE '^[[:space:]]*state = running'; }
now() { date -u +%Y-%m-%dT%H:%M:%SZ; }

down=""
for label in $labels; do
  running "$label" && continue
  echo "$(now) $label 不在运行，kickstart" >> "$state/watch.log"
  "$launchctl_bin" kickstart "$domain/$label" >/dev/null 2>&1 || true
  down="$down $label"
done
[ -z "$down" ] && exit 0
sleep "$wait_s"
failed=""
for label in $down; do
  running "$label" && { echo "$(now) $label 已拉起" >> "$state/watch.log"; continue; }
  failed="$failed ${label##*.}"
  echo "$(now) 告警：$label kickstart 后 ${wait_s} 秒仍未运行；看 ~/Library/Logs/$label/stdout.log，修好后 launchctl kickstart $domain/$label" >> "$state/alert.log"
done
if [ -n "$failed" ]; then
  "$osascript_bin" -e "display notification \"拉不起来：${failed}（详见 ${state}/alert.log）\" with title \"CI 运行器离线\"" >/dev/null 2>&1 || true
  exit 1
fi
exit 0
