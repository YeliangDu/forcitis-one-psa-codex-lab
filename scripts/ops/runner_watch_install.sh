#!/usr/bin/env bash
# 运行器自动拉起与巡检的安装（卡 #736 第 9 项，用户「批准（推荐）」10-28）。用法：runner_watch_install.sh [--dry-run] [--uninstall]
# 1 给 mac-one-1～4 的 launchd plist 加 KeepAlive=true、ThrottleInterval=30（PlistBuddy 改、plutil -lint），逐台 bootout 再 bootstrap 生效。
#   正在跑作业的那台（有 Runner.Worker 进程）先跳过，最后列出，作业结束后重跑本脚本；改过的不再重载。
#   运行器自带的 svc.sh install 会重写 plist、丢掉这两项：重装运行器后重跑本脚本。
# 2 安装巡检 com.forcitis.runner-watch（runner_watch.sh 复制到 ~/.modernize/psa/ops/bin/，plist 模板 __HOME__ 换成主目录，bootstrap）。
# --uninstall 只卸巡检，运行器 plist 的两项保留。环境变量（自测用）：RUNNER_PLIST_DIR、RUNNER_NAMES、RUNNERS_HOME
set -eu
dry=0; uninstall=0
for a in "$@"; do case "$a" in --dry-run) dry=1 ;; --uninstall) uninstall=1 ;; *) echo "未知参数 $a" >&2; exit 2 ;; esac; done
here="$(cd "$(dirname "$0")" && pwd)"
label=com.forcitis.runner-watch
bin_dir="$HOME/.modernize/psa/ops/bin"
state_dir="$HOME/.modernize/psa/ops/runner-watch"
agents="${RUNNER_PLIST_DIR:-$HOME/Library/LaunchAgents}"
runners_home="${RUNNERS_HOME:-$HOME/actions-runners}"
names="${RUNNER_NAMES:-1 2 3 4}"
plist="$agents/$label.plist"
domain="gui/$(id -u)"
pb=/usr/libexec/PlistBuddy
run() { if [ "$dry" -eq 1 ]; then echo "[dry-run] $*"; else "$@"; fi; }
reload() {  # bootout is asynchronous: bootstrap right after it fails with 5 (Input/output error) and leaves the service unloaded (10-28, mac-one-1); wait until it is gone, then bootstrap, retried
  run launchctl bootout "$domain/$1" 2>/dev/null || true
  [ "$dry" -eq 1 ] && { echo "[dry-run] launchctl bootstrap $domain $2"; return 0; }
  for _ in $(seq 20); do launchctl print "$domain/$1" >/dev/null 2>&1 || break; sleep 0.5; done
  for _ in 1 2 3; do launchctl bootstrap "$domain" "$2" 2>/dev/null && return 0; sleep 2; done
  return 1
}

if [ "$uninstall" -eq 1 ]; then
  run launchctl bootout "$domain/$label" 2>/dev/null || true
  run rm -f "$plist" "$bin_dir/runner_watch.sh"
  echo "已卸载 ${label}（运行器 plist 的 KeepAlive、ThrottleInterval 保留；状态目录 ${state_dir} 保留）"
  exit 0
fi

busy=""
for n in $names; do
  rl="actions.runner.FORCITIS-forcitis-one-psa.mac-one-$n"
  rp="$agents/$rl.plist"
  [ -f "$rp" ] || { echo "缺 ${rp}，跳过 mac-one-${n}" >&2; continue; }
  if [ "$("$pb" -c 'Print :KeepAlive' "$rp" 2>/dev/null)" = true ] && [ "$("$pb" -c 'Print :ThrottleInterval' "$rp" 2>/dev/null)" = 30 ]; then
    echo "mac-one-$n 已有 KeepAlive、ThrottleInterval，不动"
    continue
  fi
  if pgrep -f "$runners_home/one-$n/bin/Runner.Worker" >/dev/null 2>&1; then
    busy="$busy mac-one-$n"
    continue
  fi
  tmp="$(mktemp)"
  cp "$rp" "$tmp"
  "$pb" -c 'Delete :KeepAlive' "$tmp" 2>/dev/null || true
  "$pb" -c 'Delete :ThrottleInterval' "$tmp" 2>/dev/null || true
  "$pb" -c 'Add :KeepAlive bool true' -c 'Add :ThrottleInterval integer 30' "$tmp"
  plutil -lint "$tmp" >/dev/null
  run cp "$tmp" "$rp"
  rm -f "$tmp"
  reload "$rl" "$rp" || { echo "mac-one-$n 重载失败：手动 launchctl bootstrap $domain $rp" >&2; exit 1; }
  echo "mac-one-$n 已加 KeepAlive、ThrottleInterval 并重载"
done

run mkdir -p "$bin_dir" "$state_dir" "$agents"
run cp "$here/runner_watch.sh" "$bin_dir/runner_watch.sh"
run chmod 755 "$bin_dir/runner_watch.sh"
tmp="$(mktemp)"; trap 'rm -f "$tmp"' EXIT
sed "s|__HOME__|$HOME|g" "$here/$label.plist" > "$tmp"
plutil -lint "$tmp" >/dev/null
run cp "$tmp" "$plist"
run chmod 644 "$plist"
reload "$label" "$plist" || { echo "$label 加载失败：手动 launchctl bootstrap $domain $plist" >&2; exit 1; }
[ "$dry" -eq 1 ] || { cmp "$here/runner_watch.sh" "$bin_dir/runner_watch.sh"; launchctl print "$domain/$label" >/dev/null; echo "已安装并加载 $label"; }
if [ -n "$busy" ]; then
  echo "正在跑作业、这次没动：${busy}；作业结束后重跑本脚本" >&2
  exit 3
fi
