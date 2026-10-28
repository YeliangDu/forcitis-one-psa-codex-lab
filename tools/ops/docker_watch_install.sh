#!/usr/bin/env bash
# 安装/卸载 Docker 常驻检查（OPS-1022-OPS-02）。用法：docker_watch_install.sh [--dry-run] [--uninstall]
# 安装：复制 docker_ensure.sh 到 ~/.modernize/psa/ops/bin/（cmp 核对），把 plist 模板的 __HOME__ 换成主目录，plutil -lint，
#       0644 放进 ~/Library/LaunchAgents/，先 bootout 旧的再 bootstrap。卸载：bootout 并删 plist 与副本（状态目录保留）。
set -eu
dry=0; uninstall=0
for a in "$@"; do case "$a" in --dry-run) dry=1 ;; --uninstall) uninstall=1 ;; *) echo "未知参数 $a" >&2; exit 2 ;; esac; done
here="$(cd "$(dirname "$0")" && pwd)"
src="$(cd "$here/../../scripts/release" && pwd)"
label=com.forcitis.docker-watch
bin_dir="$HOME/.modernize/psa/ops/bin"
state_dir="$HOME/.modernize/psa/ops/docker-watch"
plist="$HOME/Library/LaunchAgents/$label.plist"
domain="gui/$(id -u)"
run() { if [ "$dry" -eq 1 ]; then echo "[dry-run] $*"; else "$@"; fi; }

if [ "$uninstall" -eq 1 ]; then
  run launchctl bootout "$domain/$label" 2>/dev/null || true
  run rm -f "$plist" "$bin_dir/docker_ensure.sh"
  if [ "$dry" -eq 0 ] && launchctl print "$domain/$label" >/dev/null 2>&1; then
    echo "卸载失败：${label} 仍在 launchd 中" >&2; exit 1
  fi
  echo "已卸载 ${label}（状态目录 ${state_dir} 保留）"
  exit 0
fi

run mkdir -p "$bin_dir" "$state_dir" "$HOME/Library/LaunchAgents"
run cp "$src/docker_ensure.sh" "$bin_dir/docker_ensure.sh"
run chmod 755 "$bin_dir/docker_ensure.sh"
tmp="$(mktemp)"; trap 'rm -f "$tmp"' EXIT
sed "s|__HOME__|$HOME|g" "$here/$label.plist" > "$tmp"
plutil -lint "$tmp" >/dev/null
run cp "$tmp" "$plist"
run chmod 644 "$plist"
run launchctl bootout "$domain/$label" 2>/dev/null || true
run launchctl bootstrap "$domain" "$plist"
if [ "$dry" -eq 0 ]; then
  cmp "$src/docker_ensure.sh" "$bin_dir/docker_ensure.sh"
  launchctl print "$domain/$label" >/dev/null
  echo "已安装并加载 $label"
fi
