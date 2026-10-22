#!/usr/bin/env bash
# 本机 Docker Desktop 停了就拉起（OPS-1022-OPS-02）。launchd 每 90 秒跑一次；CI 后端作业在检查测试镜像前加 --ci 跑一次。
# 用法：docker_ensure.sh [--ci]
#   默认（launchd）：docker info 不通就 open -a Docker 拉起；$state/pause 文件在时什么都不做（想临时停 Docker 时 touch 它，用完删）。
#   --ci：不自己 open（runner 的会话不是图形登录会话，拉起的 Docker 碰不到登录钥匙串），改为请求 launchd 里的 com.forcitis.docker-watch 立即跑一轮，然后只等 docker info；忽略 pause。
# 退出码：0 = docker 已应答（含刚拉起成功）；1 = 拉起失败，stderr 写「Docker 未运行」。
# 拉起失败、等锁超时，或一小时内拉起超过 2 次：写 alert.log 并追加到 one-coordinator 的 outbox（由调度代转）；同一类告警一小时只写一次。
# 不改 ~/.docker/config.json，不起容器。
# 环境变量（自测与演练用）：DOCKER_BIN、OPEN_BIN、PGREP_BIN、LAUNCHCTL_BIN、DOCKER_ENSURE_STATE、DOCKER_ENSURE_OUTBOX、
#   DOCKER_ENSURE_WAIT_SECONDS（拉起后最长等待，默认 120）、DOCKER_ENSURE_POLL_SECONDS（默认 3）、DOCKER_ENSURE_PROBE_SECONDS（单次 docker info 上限，默认 10）、
#   DOCKER_ENSURE_LOCK_STALE_SECONDS（锁超过此时长即视为过期，默认 600）、DOCKER_ENSURE_LOCK_EXTRA_SECONDS（默认 30）
set -u

ci=0
[ "${1:-}" = "--ci" ] && ci=1

docker_bin="${DOCKER_BIN:-docker}"
open_bin="${OPEN_BIN:-open}"
pgrep_bin="${PGREP_BIN:-pgrep}"
launchctl_bin="${LAUNCHCTL_BIN:-launchctl}"
state="${DOCKER_ENSURE_STATE:-$HOME/.modernize/psa/ops/docker-watch}"
outbox="${DOCKER_ENSURE_OUTBOX:-$HOME/.modernize/psa/outbox/one-coordinator.md}"
wait_seconds="${DOCKER_ENSURE_WAIT_SECONDS:-120}"
poll_seconds="${DOCKER_ENSURE_POLL_SECONDS:-3}"
probe_seconds="${DOCKER_ENSURE_PROBE_SECONDS:-10}"
lock_stale_seconds="${DOCKER_ENSURE_LOCK_STALE_SECONDS:-600}"
lock_extra_seconds="${DOCKER_ENSURE_LOCK_EXTRA_SECONDS:-30}"   # 等别的实例拉起，比自己的等待上限多给这么久
max_launches_per_hour=2

mkdir -p "$state" "$(dirname "$outbox")"
launches="$state/launches.log"
alert_log="$state/alert.log"
lock="$state/lock.d"

# docker info 在引擎半死时会挂住：放后台，超过上限就杀
docker_ok() {
  "$docker_bin" info >/dev/null 2>&1 &
  local pid=$! waited=0
  while kill -0 "$pid" 2>/dev/null; do
    if [ "$waited" -ge "$probe_seconds" ]; then kill "$pid" 2>/dev/null; wait "$pid" 2>/dev/null; return 1; fi
    sleep 1; waited=$((waited + 1))
  done
  wait "$pid"
}

# alert <类别> <内容>：同一类别一小时内只写一次（Docker 长时间拉不起来时不刷屏）
alert() {
  local text="$2" mark="$state/alerted-$1" now last line
  now="$(date +%s)"
  last="$(cat "$mark" 2>/dev/null || echo 0)"
  [ $((now - last)) -lt 3600 ] && return 0
  echo "$now" > "$mark"
  line="$(date '+%F %H:%M') → one-coordinator：本机 Docker 告警：$text（来自 docker_ensure.sh，日志 $alert_log）"
  printf '%s\n' "$line" >> "$alert_log"
  printf '%s\n' "- $line" >> "$outbox"
  printf '%s\n' "$line" >&2
}

mtime() { stat -f %m "$1" 2>/dev/null || stat -c %Y "$1" 2>/dev/null || echo 0; }

# 清过期锁：持有者已死，或锁太旧（含 mkdir 与写 pid 之间被杀、没有 pid 文件的情况）。先改名再删，免得两个实例互删对方刚拿到的新锁
break_stale_lock() {
  local holder age stale="$lock.stale.$$"
  holder="$(cat "$lock/pid" 2>/dev/null || true)"
  age=$(( $(date +%s) - $(mtime "$lock") ))
  if { [ -n "$holder" ] && ! kill -0 "$holder" 2>/dev/null; } || [ "$age" -ge "$lock_stale_seconds" ]; then
    mv "$lock" "$stale" 2>/dev/null && rm -rf "$stale"
  fi
}

if [ "$ci" -eq 0 ] && [ -e "$state/pause" ]; then exit 0; fi
docker_ok && exit 0

# 同时只让一个实例拉起；其余等它做完再复查
deadline=$(( $(date +%s) + wait_seconds + lock_extra_seconds ))
got_lock=0
while [ "$(date +%s)" -lt "$deadline" ]; do
  if mkdir "$lock" 2>/dev/null; then got_lock=1; echo $$ > "$lock/pid"; break; fi
  break_stale_lock
  sleep 2
  docker_ok && exit 0
done
if [ "$got_lock" -ne 1 ]; then
  alert lock "等其他实例拉起 $((wait_seconds + lock_extra_seconds)) 秒仍未成功，且拿不到锁 $lock"
  echo "Docker 未运行（等其他实例拉起超时）" >&2
  exit 1
fi
trap 'rm -rf "$lock"' EXIT

docker_ok && exit 0

now="$(date +%s)"
touch "$launches"
recent="$(awk -v cutoff=$((now - 3600)) '$1 > cutoff' "$launches")"
count="$(printf '%s\n' "$recent" | grep -c .)"

if [ "$ci" -eq 1 ]; then
  # 请求 launchd 里的那一份立即跑（它在图形登录会话里，由它 open）；没装或失败就算了，只等
  "$launchctl_bin" kickstart "gui/$(id -u)/com.forcitis.docker-watch" >/dev/null 2>&1 || true
else
  { [ -n "$recent" ] && printf '%s\n' "$recent"; printf '%s\n' "$now"; } > "$launches.tmp" && mv "$launches.tmp" "$launches"
  count=$((count + 1))
  # Docker Desktop 进程还在（正在退出、更新、自重启）时先不 open，免得激活一个快退出的实例；等到一半仍不通再 open 一次
  if ! "$pgrep_bin" -x "Docker Desktop" >/dev/null 2>&1; then "$open_bin" -a Docker >/dev/null 2>&1 || true; fi
fi

start="$(date +%s)"; opened_late=0
while [ $(( $(date +%s) - start )) -lt "$wait_seconds" ]; do
  sleep "$poll_seconds"
  if docker_ok; then
    echo "Docker 已拉起（等待 $(( $(date +%s) - start )) 秒，近一小时第 ${count} 次）" >&2
    if [ "$ci" -eq 0 ] && [ "$count" -gt "$max_launches_per_hour" ]; then
      alert frequent "一小时内已拉起 ${count} 次（超过 ${max_launches_per_hour} 次），Docker 可能在反复退出"
    fi
    exit 0
  fi
  if [ "$ci" -eq 0 ] && [ "$opened_late" -eq 0 ] && [ $(( $(date +%s) - start )) -ge $((wait_seconds / 2)) ]; then
    opened_late=1; "$open_bin" -a Docker >/dev/null 2>&1 || true
  fi
done

alert fail "拉起失败：等了 ${wait_seconds} 秒 docker info 仍不通（近一小时第 ${count} 次）"
echo "Docker 未运行" >&2
exit 1
