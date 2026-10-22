#!/usr/bin/env bash
# 本机 Docker Desktop 停了就拉起（OPS-1022-OPS-02）。launchd 每 90 秒跑一次，CI 后端作业在检查测试镜像前也跑一次。
# 退出码：0 = docker 已应答（含刚拉起成功）；1 = 拉起失败，stderr 写「Docker 未运行」。
# 拉起失败，或一小时内拉起超过 2 次，向 stderr、告警日志写一行，并追加到 one-coordinator 的 outbox（由调度代转）。
# 不改 ~/.docker/config.json，不起容器。
# 环境变量（自测与演练用）：DOCKER_BIN、OPEN_BIN、DOCKER_ENSURE_STATE、DOCKER_ENSURE_OUTBOX、
#   DOCKER_ENSURE_WAIT_SECONDS（拉起后最长等待，默认 120）、DOCKER_ENSURE_POLL_SECONDS（默认 3）、DOCKER_ENSURE_PROBE_SECONDS（单次 docker info 上限，默认 10）
set -u

docker_bin="${DOCKER_BIN:-docker}"
open_bin="${OPEN_BIN:-open}"
state="${DOCKER_ENSURE_STATE:-$HOME/.modernize/psa/ops/docker-watch}"
outbox="${DOCKER_ENSURE_OUTBOX:-$HOME/.modernize/psa/outbox/one-coordinator.md}"
wait_seconds="${DOCKER_ENSURE_WAIT_SECONDS:-120}"
poll_seconds="${DOCKER_ENSURE_POLL_SECONDS:-3}"
probe_seconds="${DOCKER_ENSURE_PROBE_SECONDS:-10}"
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

alert() {
  local line
  line="$(date '+%F %H:%M') → one-coordinator：本机 Docker 告警：$1（来自 docker_ensure.sh，日志 $alert_log）"
  printf '%s\n' "$line" >> "$alert_log"
  printf '%s\n' "- $line" >> "$outbox"
  printf '%s\n' "$line" >&2
}

docker_ok && exit 0

# 同时只让一个实例拉起；其余等它做完再复查
got_lock=0
for _ in $(seq 1 $(( (wait_seconds + 30) / 2 + 1 ))); do
  if mkdir "$lock" 2>/dev/null; then got_lock=1; echo $$ > "$lock/pid"; break; fi
  holder="$(cat "$lock/pid" 2>/dev/null || true)"
  if [ -n "$holder" ] && ! kill -0 "$holder" 2>/dev/null; then rm -rf "$lock"; continue; fi
  sleep 2
  docker_ok && exit 0
done
if [ "$got_lock" -ne 1 ]; then
  echo "Docker 未运行（等其他实例拉起超时）" >&2
  exit 1
fi
trap 'rm -rf "$lock"' EXIT

docker_ok && exit 0

now="$(date +%s)"
touch "$launches"
recent="$(awk -v cutoff=$((now - 3600)) '$1 > cutoff' "$launches")"
{ [ -n "$recent" ] && printf '%s\n' "$recent"; printf '%s\n' "$now"; } > "$launches.tmp" && mv "$launches.tmp" "$launches"
count="$(wc -l < "$launches" | tr -d ' ')"

"$open_bin" -a Docker >/dev/null 2>&1 || true

waited=0
while [ "$waited" -lt "$wait_seconds" ]; do
  sleep "$poll_seconds"; waited=$((waited + poll_seconds))
  if docker_ok; then
    echo "Docker 已拉起（等待 ${waited} 秒，近一小时第 ${count} 次）" >&2
    if [ "$count" -gt "$max_launches_per_hour" ]; then alert "一小时内已拉起 ${count} 次（超过 ${max_launches_per_hour} 次），Docker 可能在反复退出"; fi
    exit 0
  fi
done

alert "拉起失败：open -a Docker 后 ${wait_seconds} 秒内 docker info 仍不通（近一小时第 ${count} 次）"
echo "Docker 未运行" >&2
exit 1
