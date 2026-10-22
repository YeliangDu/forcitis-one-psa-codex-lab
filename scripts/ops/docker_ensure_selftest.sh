#!/usr/bin/env bash
# docker_ensure.sh 自测，不碰真实 Docker：Docker 在时不动作；停了能拉起；拉起超时报错；频繁拉起告警；告警去重；
# 一小时窗口剔旧记录；Docker 进程还在时先不 open；暂停文件；过期锁（无 pid）与活锁；--ci 不自己 open；引擎挂住时探测有上限。
set -u
here="$(cd "$(dirname "$0")" && pwd)"
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
fail=0
check() { if [ "$2" = "$3" ]; then echo "PASS  $1"; else echo "FAIL  $1（期望 $3，实际 $2）"; fail=1; fi; }

# 假 docker：up 文件在就应答；hang 文件在就挂住
cat > "$tmp/docker" <<'F'
#!/usr/bin/env bash
[ -e "$FAKE/hang" ] && sleep 60
[ -e "$FAKE/up" ]
F
# 假 open / launchctl：记一次调用；mode=start 时拉起（建 up），mode=never 时不动
for tool in open launchctl; do
  cat > "$tmp/$tool" <<F
#!/usr/bin/env bash
echo x >> "\$FAKE/$tool-calls"
[ "\$(cat "\$FAKE/mode" 2>/dev/null)" = start ] && touch "\$FAKE/up"
exit 0
F
done
# 假 pgrep：proc 文件在就说「Docker Desktop 进程在」
cat > "$tmp/pgrep" <<'F'
#!/usr/bin/env bash
[ -e "$FAKE/proc" ]
F
chmod +x "$tmp/docker" "$tmp/open" "$tmp/launchctl" "$tmp/pgrep"

# newcase <名> <up|down> <start|never>：建独立目录
newcase() { mkdir -p "$tmp/$1/state"; [ "$2" = up ] && touch "$tmp/$1/up"; echo "$3" > "$tmp/$1/mode"; }
seed_launches() { local n="$2"; while [ "$n" -gt 0 ]; do echo "$3" >> "$tmp/$1/state/launches.log"; n=$((n - 1)); done; }
# run <名> [参数...]：跑一次，记退出码
run() {
  local d="$tmp/$1"; shift
  FAKE="$d" DOCKER_BIN="$tmp/docker" OPEN_BIN="$tmp/open" PGREP_BIN="$tmp/pgrep" LAUNCHCTL_BIN="$tmp/launchctl" \
    DOCKER_ENSURE_STATE="$d/state" DOCKER_ENSURE_OUTBOX="$d/outbox.md" \
    DOCKER_ENSURE_WAIT_SECONDS="${WAIT:-4}" DOCKER_ENSURE_POLL_SECONDS=1 DOCKER_ENSURE_PROBE_SECONDS=2 DOCKER_ENSURE_LOCK_EXTRA_SECONDS="${EXTRA:-30}" \
    "$here/docker_ensure.sh" "$@" >"$d/out" 2>"$d/err"
  echo $? > "$d/rc"
}
rc() { cat "$tmp/$1/rc"; }
calls() { [ -e "$tmp/$1/$2-calls" ] && wc -l < "$tmp/$1/$2-calls" | tr -d ' ' || echo 0; }
has() { grep -q "$2" "$tmp/$1/$3" 2>/dev/null && echo yes || echo no; }
lines() { [ -e "$tmp/$1/outbox.md" ] && wc -l < "$tmp/$1/outbox.md" | tr -d ' ' || echo 0; }
now="$(date +%s)"; old=$((now - 7200))

newcase up up start; run up
check "Docker 在时退出码 0" "$(rc up)" 0
check "Docker 在时不动作（不调 open）" "$(calls up open)" 0

newcase down down start; run down
check "停了能拉起，退出码 0" "$(rc down)" 0
check "停了调用一次 open" "$(calls down open)" 1
check "一次拉起不告警" "$(lines down)" 0

newcase timeout down never; run timeout
check "拉起超时退出码 1" "$(rc timeout)" 1
check "拉起超时报「Docker 未运行」" "$(has timeout 'Docker 未运行' err)" yes
check "拉起超时写 outbox 告警" "$(has timeout '拉起失败' outbox.md)" yes
check "拉起超时同时写 alert.log" "$(has timeout '拉起失败' state/alert.log)" yes
run timeout
check "同类告警一小时内只写一次" "$(lines timeout)" 1

newcase frequent down start; seed_launches frequent 2 "$now"; run frequent
check "频繁拉起仍成功" "$(rc frequent)" 0
check "一小时内第 3 次拉起告警" "$(has frequent '已拉起 3 次' outbox.md)" yes

newcase second down start; seed_launches second 1 "$now"; run second
check "一小时内第 2 次拉起不告警" "$(lines second)" 0

newcase pruned down start; seed_launches pruned 5 "$old"; run pruned
check "超过一小时的旧记录不计入，不告警" "$(lines pruned)" 0

newcase proc down start; touch "$tmp/proc/proc"; WAIT=6 run proc
check "Docker 进程还在时仍能恢复，退出码 0" "$(rc proc)" 0
check "Docker 进程还在时只在等到一半后 open 一次" "$(calls proc open)" 1

newcase pause down start; touch "$tmp/pause/state/pause"; run pause
check "暂停文件在时不动作，退出码 0" "$(rc pause)" 0
check "暂停文件在时不调 open" "$(calls pause open)" 0

newcase stale down start; mkdir "$tmp/stale/state/lock.d"; touch -t 200001010000 "$tmp/stale/state/lock.d"; run stale
check "没有 pid 的过期锁被清掉，仍能拉起" "$(rc stale)" 0

newcase live down start; mkdir "$tmp/live/state/lock.d"; echo $$ > "$tmp/live/state/lock.d/pid"; EXTRA=3 WAIT=1 run live
check "活锁不被抢，等不到就失败退出 1" "$(rc live)" 1
check "等锁超时报「Docker 未运行」" "$(has live 'Docker 未运行' err)" yes
check "等锁超时写告警" "$(has live '拿不到锁' outbox.md)" yes
check "活锁时没有调 open" "$(calls live open)" 0

newcase ci down start; run ci --ci
check "--ci 经 launchctl 请求拉起并成功" "$(rc ci)" 0
check "--ci 调一次 launchctl" "$(calls ci launchctl)" 1
check "--ci 不自己 open" "$(calls ci open)" 0

newcase cipause down never; touch "$tmp/cipause/state/pause"; run cipause --ci
check "--ci 忽略暂停文件，拉不起来退出 1" "$(rc cipause)" 1

# 引擎挂住：探测有上限，不会卡死
newcase hang down never; touch "$tmp/hang/hang"
start="$(date +%s)"; run hang
check "引擎挂住时按超时失败而不是卡死" "$(rc hang)" 1
check "引擎挂住时耗时有限（< 40 秒）" "$([ $(( $(date +%s) - start )) -lt 40 ] && echo yes || echo no)" yes
pkill -f "sleep 60" 2>/dev/null || true

if [ "$fail" -eq 0 ]; then echo "docker_ensure selftest passed"; else echo "docker_ensure selftest FAILED"; exit 1; fi
