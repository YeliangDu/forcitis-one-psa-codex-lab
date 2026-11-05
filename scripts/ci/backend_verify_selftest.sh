#!/usr/bin/env bash
# Self-test of backend_verify.sh's OpenAPI branch (#783): the export is skipped only when OPENAPI_EXPORTED_FOR names the
# checked-out commit; unset, empty or another commit export and compare, and a contract that differs stops the run
# before any test. Runs the real script in a throwaway repository with a stub mvnw; needs no Maven, Java or Docker.
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

mkdir -p "$tmp/scripts/ci" "$tmp/scripts/codegen" "$tmp/backend" "$tmp/frontend/packages/api"
cp "$here/backend_verify.sh" "$tmp/scripts/ci/"
printf '#!/usr/bin/env bash\nexit 0\n' >"$tmp/scripts/codegen/generate_api.sh"
# The stub logs every call; the export call writes $EXPORTED as the fresh contract.
cat >"$tmp/backend/mvnw" <<'EOF'
#!/usr/bin/env bash
echo "$*" >>"$STUB_LOG"
echo $$ >>"${STUB_PIDS:-/dev/null}"
[ -n "${STUB_HANG:-}" ] && case "$*" in *export-openapi*) ;; *surefire:test*|*failsafe:*) echo "$*" >>"$STUB_LOG"; (exec -a "java fork $PWD/target/surefire/booter" sleep 60) & echo $! >>"$STUB_PIDS"; exec sleep 60 ;; esac
case "$*" in *export-openapi*) ;; *surefire:test*) if [ -n "${STUB_ID:-}" ]; then echo "enter $STUB_ID" >>"$STUB_LOG"; sleep 2; echo "leave $STUB_ID" >>"$STUB_LOG"; fi ;; esac
case "$*" in *export-openapi*) printf '%s' "$EXPORTED" >../frontend/packages/api/openapi.json ;; esac
exit 0
EOF
chmod +x "$tmp/scripts/ci/backend_verify.sh" "$tmp/scripts/codegen/generate_api.sh" "$tmp/backend/mvnw"
git -C "$tmp" init -q
git -C "$tmp" -c user.name=t -c user.email=t@example.invalid -c core.hooksPath= -c commit.gpgsign=false commit -q --allow-empty -m x
head=$(git -C "$tmp" rev-parse HEAD)
export BACKEND_VERIFY_LOCK="$tmp/lock" BACKEND_VERIFY_LOCK_POLL=1 BACKEND_VERIFY_LOCK_WAIT=20
unset GITHUB_ACTIONS

failures=0
case_() {  # case_ <name> <OPENAPI_EXPORTED_FOR or "-" for unset> <exported contract> <expected rc> <export expected: yes|no> <tests expected: yes|no>
  local name=$1 value=$2 exported=$3 want_rc=$4 want_export=$5 want_tests=$6 rc=0 log="$tmp/calls.log" out="$tmp/out.log"
  printf '{"same":1}' >"$tmp/frontend/packages/api/openapi.json"
  : >"$log"
  if [ "$value" = - ]; then
    env -u OPENAPI_EXPORTED_FOR STUB_LOG="$log" EXPORTED="$exported" BACKEND_WATCHDOG_SECONDS=30 bash "$tmp/scripts/ci/backend_verify.sh" >"$out" 2>&1 || rc=$?
  else
    OPENAPI_EXPORTED_FOR="$value" STUB_LOG="$log" EXPORTED="$exported" BACKEND_WATCHDOG_SECONDS=30 bash "$tmp/scripts/ci/backend_verify.sh" >"$out" 2>&1 || rc=$?
  fi
  local exported_seen=no tests_seen=no
  grep -q 'export-openapi' "$log" && exported_seen=yes
  grep -qE '(^|[[:space:]])(surefire:test|failsafe:integration-test)([[:space:]]|$)' "$log" && tests_seen=yes
  if [ "$rc" != "$want_rc" ] || [ "$exported_seen" != "$want_export" ] || [ "$tests_seen" != "$want_tests" ]; then
    echo "FAIL $name: rc=$rc (want $want_rc), export=$exported_seen (want $want_export), tests=$tests_seen (want $want_tests)"; sed 's/^/  | /' "$out" | tail -5
    failures=$((failures + 1))
  fi
}

case_ "this run's commit: no second export"          "$head"                                     '{"same":1}'   0 no  yes
case_ "this run's commit, contract would differ"      "$head"                                     '{"other":1}'  0 no  yes
case_ "unset: export and compare"                     -                                           '{"same":1}'   0 yes yes
case_ "empty: export and compare"                     ""                                          '{"same":1}'   0 yes yes
case_ "another commit: export and compare"            0000000000000000000000000000000000000000    '{"same":1}'   0 yes yes
case_ "unset, contract differs: stop before any test" -                                           '{"other":1}'  1 yes no
case_ "another commit, contract differs: stop"        0000000000000000000000000000000000000000    '{"other":1}'  1 yes no

# Local runs (#1341): one at a time machine-wide, fewer forks than CI; CI (GITHUB_ACTIONS set) takes no lock and keeps its options.
run_() {  # run_ <log> [env assignments...] -- runs the script with the stub
  local log=$1; shift; : >"$log"; printf '{"same":1}' >"$tmp/frontend/packages/api/openapi.json"
  env "$@" STUB_LOG="$log" EXPORTED='{"same":1}' BACKEND_WATCHDOG_SECONDS=30 bash "$tmp/scripts/ci/backend_verify.sh" >"$tmp/out.log" 2>&1 &
  local pid=$! n=0   # a run still going after 25 s is a hang (a lock that never refuses): stop it and fail
  while kill -0 "$pid" 2>/dev/null && [ "$n" -lt 25 ]; do sleep 1; n=$((n + 1)); done
  if kill -0 "$pid" 2>/dev/null; then kill "$pid" 2>/dev/null; wait "$pid" 2>/dev/null; return 124; fi
  wait "$pid"
}
check_() {  # check_ <name> <condition result 0|1>
  [ "$2" -eq 0 ] || { echo "FAIL $1"; sed 's/^/  | /' "$tmp/out.log" | tail -5; failures=$((failures + 1)); }
}
rm -rf "$tmp/lock"
run_ "$tmp/l1.log" A=1; if grep -q -- '-Dtest.forks=1 -Dit.forks=2' "$tmp/l1.log"; then check_ "local run defaults to fewer forks" 0; else check_ "local run defaults to fewer forks" 1; fi
if [ ! -e "$tmp/lock" ]; then check_ "local run releases the lock" 0; else check_ "local run releases the lock" 1; fi
printf '{"same":1}' >"$tmp/frontend/packages/api/openapi.json"; : >"$tmp/l3.log"
if STUB_LOG="$tmp/l3.log" EXPORTED='{"same":1}' BACKEND_WATCHDOG_SECONDS=30 bash "$tmp/scripts/ci/backend_verify.sh" -Dtest.forks=3 >"$tmp/out.log" 2>&1; grep -q -- '-Dtest.forks=1 -Dit.forks=2 -Dtest.forks=3' "$tmp/l3.log"; then check_ "caller's -D options come last and win" 0; else check_ "caller's -D options come last and win" 1; fi
run_ "$tmp/l4.log" GITHUB_ACTIONS=true; if ! grep -q -- 'forks=' "$tmp/l4.log"; then check_ "CI: options unchanged" 0; else check_ "CI: options unchanged" 1; fi
mkdir "$tmp/lock"; echo $$ >"$tmp/lock/pid"   # held by a live process (this one)
rc=0; run_ "$tmp/l5.log" BACKEND_VERIFY_LOCK_WAIT=2 || rc=$?; if [ "$rc" -eq 1 ] && ! [ -s "$tmp/l5.log" ]; then check_ "second local run waits, then refuses without running anything" 0; else check_ "second local run waits, then refuses without running anything" 1; fi
rc=0; run_ "$tmp/l6.log" GITHUB_ACTIONS=true || rc=$?; if [ "$rc" -eq 0 ] && [ -s "$tmp/l6.log" ]; then check_ "CI ignores a held lock" 0; else check_ "CI ignores a held lock" 1; fi
echo 99999999 >"$tmp/lock/pid"   # holder gone
rc=0; run_ "$tmp/l7.log" BACKEND_VERIFY_LOCK_WAIT=2 || rc=$?; if [ "$rc" -eq 0 ] && [ ! -e "$tmp/lock" ]; then check_ "a lock whose holder is gone is taken over" 0; else check_ "a lock whose holder is gone is taken over" 1; fi
mkdir "$tmp/ro" && chmod 500 "$tmp/ro"   # a lock that cannot be created (read-only directory) refuses at once instead of waiting
rc=0; run_ "$tmp/l8.log" BACKEND_VERIFY_LOCK="$tmp/ro/lock" || rc=$?; if [ "$rc" -eq 1 ] && ! [ -s "$tmp/l8.log" ] && grep -q 'cannot create the lock' "$tmp/out.log"; then check_ "an uncreatable lock refuses at once" 0; else check_ "an uncreatable lock refuses at once" 1; fi
chmod 700 "$tmp/ro"
rm -rf "$tmp/lock"
: >"$tmp/s1.log"; : >"$tmp/s2.log"; printf '{"same":1}' >"$tmp/frontend/packages/api/openapi.json"
(STUB_ID=A STUB_LOG="$tmp/s.log" EXPORTED='{"same":1}' BACKEND_WATCHDOG_SECONDS=60 bash "$tmp/scripts/ci/backend_verify.sh" >"$tmp/o1.log" 2>&1 &
 STUB_ID=B STUB_LOG="$tmp/s.log" EXPORTED='{"same":1}' BACKEND_WATCHDOG_SECONDS=60 bash "$tmp/scripts/ci/backend_verify.sh" >"$tmp/o2.log" 2>&1 &
 wait)
seq=$(grep -E '^(enter|leave) ' "$tmp/s.log" | tr '\n' ' ')
case "$seq" in "enter A leave A enter B leave B "|"enter B leave B enter A leave A ") r=0 ;; *) r=1; echo "  sequence: $seq" ;; esac
check_ "two local runs started together run one after the other" $r

# Watchdog (#1385): a local run defaults to 2400 s, CI keeps 1500 s; a stub sleep on PATH records the watchdog's argument (STUB_ID keeps the tests running long enough for the watchdog to start).
mkdir -p "$tmp/bin"
# shellcheck disable=SC2016  # the stub's $1, $SLEEP_LOG and $@ expand when the stub runs, not here
printf '#!/usr/bin/env bash\necho "$1" >>"$SLEEP_LOG"\nexec /bin/sleep "$@"\n' >"$tmp/bin/sleep"
chmod +x "$tmp/bin/sleep"
watchdog_() {  # watchdog_ <name> <expected seconds> [env assignments...]
  local name=$1 want=$2; shift 2; : >"$tmp/w.log"; : >"$tmp/sl.log"; printf '{"same":1}' >"$tmp/frontend/packages/api/openapi.json"
  env -u BACKEND_WATCHDOG_SECONDS "$@" STUB_ID=W PATH="$tmp/bin:$PATH" SLEEP_LOG="$tmp/sl.log" STUB_LOG="$tmp/w.log" EXPORTED='{"same":1}' bash "$tmp/scripts/ci/backend_verify.sh" >"$tmp/out.log" 2>&1 || true
  if grep -qx "$want" "$tmp/sl.log"; then check_ "$name" 0; else echo "  watchdog sleeps: $(tr '\n' ' ' <"$tmp/sl.log")"; check_ "$name" 1; fi
}
rm -rf "$tmp/lock"
watchdog_ "local run: watchdog defaults to 2400 s" 2400 A=1
watchdog_ "CI: watchdog stays 1500 s" 1500 GITHUB_ACTIONS=true

# Stopped from outside (#1385): TERM to a local run stops its test runs and their forks (the stub starts a detached fake fork whose command line
# matches the script's fork pattern), and the lock names the holder's checkout.
rm -rf "$tmp/lock"; : >"$tmp/pids"; : >"$tmp/k.log"; printf '{"same":1}' >"$tmp/frontend/packages/api/openapi.json"
STUB_HANG=1 STUB_PIDS="$tmp/pids" STUB_LOG="$tmp/k.log" EXPORTED='{"same":1}' BACKEND_WATCHDOG_SECONDS=60 bash "$tmp/scripts/ci/backend_verify.sh" >"$tmp/out.log" 2>&1 &
victim=$! n=0
while [ "$(grep -cE 'surefire:test|failsafe:integration-test' "$tmp/k.log" 2>/dev/null)" -lt 2 ] && [ "$n" -lt 20 ]; do sleep 1; n=$((n + 1)); done
where=$(cat "$tmp/lock/where" 2>/dev/null || true)
if [ "$where" = "$tmp/backend" ]; then check_ "the lock names the holder's checkout" 0; else echo "  where: $where"; check_ "the lock names the holder's checkout" 1; fi
kill -TERM "$victim"; wait "$victim" 2>/dev/null || true; sleep 1
alive=0; while read -r p; do kill -0 "$p" 2>/dev/null && alive=$((alive + 1)); done <"$tmp/pids"
if [ "$alive" -eq 0 ] && [ ! -e "$tmp/lock" ]; then check_ "a stopped local run stops its test runs and releases the lock" 0; else check_ "a stopped local run stops its test runs and releases the lock" 1; fi
while read -r p; do kill "$p" 2>/dev/null || true; done <"$tmp/pids"

# shellcheck disable=SC2015  # echo cannot fail, so the || branch runs only when there are failures
[ "$failures" -eq 0 ] && echo "backend_verify_selftest: ok" || { echo "backend_verify_selftest: $failures failed"; exit 1; }
