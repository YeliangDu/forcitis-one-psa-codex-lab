#!/usr/bin/env bash
# docker_ensure.sh 自测：Docker 在时不动作；停了能拉起；拉起超时报错；一小时内频繁拉起告警；引擎挂住时探测有上限。不碰真实 Docker。
set -u
here="$(cd "$(dirname "$0")" && pwd)"
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
fail=0
check() { if [ "$2" = "$3" ]; then echo "PASS  $1"; else echo "FAIL  $1（期望 $3，实际 $2）"; fail=1; fi; }

# 假 docker：flag 文件在就应答；hang 文件在就挂住
cat > "$tmp/docker" <<'F'
#!/usr/bin/env bash
[ -e "$FAKE/hang" ] && sleep 60
[ -e "$FAKE/up" ]
F
# 假 open：记一次调用；mode=start 时拉起（建 up），mode=never 时不动
cat > "$tmp/open" <<'F'
#!/usr/bin/env bash
echo x >> "$FAKE/open-calls"
[ "$(cat "$FAKE/mode" 2>/dev/null)" = start ] && touch "$FAKE/up"
exit 0
F
chmod +x "$tmp/docker" "$tmp/open"

run() { # 每个用例独立目录：run <名> <up?> <mode> [预置的拉起次数]
  local d="$tmp/$1"; mkdir -p "$d/state"
  [ "$2" = yes ] && touch "$d/up"
  echo "$3" > "$d/mode"
  local n="${4:-0}"; while [ "$n" -gt 0 ]; do date +%s >> "$d/state/launches.log"; n=$((n - 1)); done
  FAKE="$d" DOCKER_BIN="$tmp/docker" OPEN_BIN="$tmp/open" DOCKER_ENSURE_STATE="$d/state" DOCKER_ENSURE_OUTBOX="$d/outbox.md" \
    DOCKER_ENSURE_WAIT_SECONDS=4 DOCKER_ENSURE_POLL_SECONDS=1 DOCKER_ENSURE_PROBE_SECONDS=2 \
    "$here/docker_ensure.sh" >"$d/out" 2>"$d/err"
  echo $? > "$d/rc"
}
rc() { cat "$tmp/$1/rc"; }
calls() { [ -e "$tmp/$1/open-calls" ] && wc -l < "$tmp/$1/open-calls" | tr -d ' ' || echo 0; }
has() { grep -q "$2" "$tmp/$1/$3" 2>/dev/null && echo yes || echo no; }

run up yes start
check "Docker 在时退出码 0" "$(rc up)" 0
check "Docker 在时不动作（不调 open）" "$(calls up)" 0

run down no start
check "停了能拉起，退出码 0" "$(rc down)" 0
check "停了调用一次 open" "$(calls down)" 1
check "一次拉起不告警" "$([ -e "$tmp/down/outbox.md" ] && echo yes || echo no)" no

run timeout no never
check "拉起超时退出码 1" "$(rc timeout)" 1
check "拉起超时报「Docker 未运行」" "$(has timeout 'Docker 未运行' err)" yes
check "拉起超时写 outbox 告警" "$(has timeout '拉起失败' outbox.md)" yes

run frequent no start 2
check "频繁拉起仍成功" "$(rc frequent)" 0
check "一小时内第 3 次拉起告警" "$(has frequent '已拉起 3 次' outbox.md)" yes

# 引擎挂住：探测有上限，不会卡死；挂住时拉起不会成功，按超时失败
mkdir -p "$tmp/hang"; touch "$tmp/hang/hang"
start="$(date +%s)"
run hang no never
check "引擎挂住时按超时失败而不是卡死" "$(rc hang)" 1
check "引擎挂住时耗时有限（< 30 秒）" "$([ $(( $(date +%s) - start )) -lt 30 ] && echo yes || echo no)" yes

if [ "$fail" -eq 0 ]; then echo "docker_ensure selftest passed"; else echo "docker_ensure selftest FAILED"; exit 1; fi
