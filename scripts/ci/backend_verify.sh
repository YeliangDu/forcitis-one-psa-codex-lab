#!/usr/bin/env bash
# CI form of `./mvnw verify`: compile and package once (PackagedJarIT checks the executable jar), then surefire (*Test) and failsafe (*IT) run side by side;
# same tests, without the jacoco agent and report (nothing in CI reads coverage).
set -uo pipefail
cd "$(dirname "$0")/../../backend" || exit 1

# A local run (no GITHUB_ACTIONS) is the only one on this machine at a time and starts fewer forks than CI (#1341): several local full runs
# next to the merge queue's CI runners pushed CI past its time budget. A later run waits for the holder (BACKEND_VERIFY_LOCK_WAIT seconds, then
# refuses); a lock whose holder is gone is taken over. -D options given by the caller come last and win. CI is unchanged.
if [ -z "${GITHUB_ACTIONS:-}" ]; then
  lock=${BACKEND_VERIFY_LOCK:-/tmp/forcitis-backend-verify.lock}
  waited=0
  until mkdir "$lock" 2>/dev/null; do
    [ -d "$lock" ] || { echo "backend_verify: cannot create the lock $lock (is its directory writable?); not starting" >&2; exit 1; }
    holder=$(cat "$lock/pid" 2>/dev/null || true)
    if { [ -n "$holder" ] && ! kill -0 "$holder" 2>/dev/null; } || { [ -z "$holder" ] && [ -n "$(find "$lock" -maxdepth 0 -mmin +1 2>/dev/null)" ]; }; then
      rm -rf "$lock"; continue
    fi
    if [ "$waited" -ge "${BACKEND_VERIFY_LOCK_WAIT:-10800}" ]; then
      echo "backend_verify: another local run (pid ${holder:-?}) still holds $lock after ${waited} s; not starting" >&2; exit 1
    fi
    [ $((waited % 60)) -eq 0 ] && echo "backend_verify: another local run (pid ${holder:-?}) holds $lock; waiting (${waited} s)" >&2
    sleep "${BACKEND_VERIFY_LOCK_POLL:-5}"; waited=$((waited + ${BACKEND_VERIFY_LOCK_POLL:-5}))
  done
  echo $$ >"$lock/pid"
  trap 'rm -rf "$lock"' EXIT
  set -- -Dtest.forks=1 -Dit.forks=2 "$@"
fi

# Both plugins read JSON and generated TS. CI downloads this run's api-contract artifact; local/nightly runs bootstrap first.
bash ../scripts/codegen/generate_api.sh --check || exit 1

./mvnw -B package -DskipTests -Djacoco.skip=true "$@" || exit 1

# Self-hosted checkouts can retain reports; only this invocation may vouch for its selected tier.
rm -rf target/surefire-reports target/failsafe-reports
# ci.yml's heavy step (queued pull request, its nightly schedule) sets OPENAPI_EXPORTED_FOR to the commit that run's api-contract
# job exported and asserted (OpenApiExportTest), so it skips a second export (#783). A local run, nightly-legacy-it.yml and any
# other commit export and compare as before.
if [ -n "${OPENAPI_EXPORTED_FOR:-}" ] && [ "$OPENAPI_EXPORTED_FOR" = "$(git rev-parse HEAD)" ]; then
  echo "OpenAPI: exported and asserted for $OPENAPI_EXPORTED_FOR by this run's api-contract job; not exported again"
else
  contract=$(mktemp)
  export_log=$(mktemp)
  cp ../frontend/packages/api/openapi.json "$contract"
  if ! ./mvnw -B surefire:test@export-openapi "$@" >"$export_log" 2>&1; then
    cat "$export_log"
    rm -f "$contract" "$export_log"
    exit 1
  fi
  cat "$export_log"
  if ! cmp -s "$contract" ../frontend/packages/api/openapi.json; then
    echo "OpenAPI 与本次前端生成输入不一致；请在仓库根重新运行 bash scripts/codegen/generate_api.sh 后重试。" >&2
    rm -f "$contract" "$export_log"
    exit 1
  fi
  rm -f "$contract" "$export_log"
fi
# Export must finish before failsafe too: its acceptance classes read the same contract.
unit_log=$(mktemp)
integration_log=$(mktemp)
./mvnw -B surefire:test "$@" >"$unit_log" 2>&1 &
unit_pid=$!
./mvnw -B failsafe:integration-test failsafe:verify "$@" >"$integration_log" 2>&1 &
integration_pid=$!

# Watchdog: a test run past BACKEND_WATCHDOG_SECONDS gets a thread dump of every forked test JVM (uploaded as an artifact), then fails.
# It fires before the forks' own limit (test.fork.timeout), which kills a fork without telling where it hung.
dumps=target/thread-dumps
watchdog_mark=$(mktemp -u)
# Only this checkout's forks (every runner on the machine runs the same plugins; booter jars live under each checkout's target/surefire).
# A fork closing its Spring contexts can ignore TERM for a while, hence the KILL after 20 s.
forks="$PWD/target/surefire/"
(
  # Stopped at a normal end by TERM: the trap leaves before the dump and the mark, so a passing run is never reported as timed out
  trap 'kill "$s" 2>/dev/null; exit 0' TERM
  sleep "${BACKEND_WATCHDOG_SECONDS:-1500}" & s=$!
  wait "$s"
  mkdir -p "$dumps"
  for pid in $(pgrep -f "java .*$forks" || true); do
    jcmd "$pid" Thread.print -l >"$dumps/$pid.txt" 2>&1 || true
  done
  touch "$watchdog_mark"
  kill "$unit_pid" "$integration_pid" 2>/dev/null
  pkill -f "java .*$forks" 2>/dev/null; sleep 20; pkill -9 -f "java .*$forks" 2>/dev/null || true
) &
watchdog_pid=$!

wait "$unit_pid"; unit_status=$?
wait "$integration_pid"; integration_status=$?
[ -e "$watchdog_mark" ] || kill "$watchdog_pid" 2>/dev/null
wait "$watchdog_pid" 2>/dev/null
if [ -e "$watchdog_mark" ]; then
  rm -f "$watchdog_mark"
  echo "::error::backend tests exceeded ${BACKEND_WATCHDOG_SECONDS:-1500} s; thread dumps of $(find "$dumps" -name '*.txt' | wc -l | tr -d ' ') test JVMs are in the test-reports artifact (backend/$dumps)"
  unit_status=124
fi

echo "::group::surefire (exit $unit_status)"; cat "$unit_log"; echo "::endgroup::"
echo "::group::failsafe (exit $integration_status)"; cat "$integration_log"; echo "::endgroup::"
grep -hE '^\[(INFO|WARNING|ERROR)\] Tests run: [0-9]+, Failures: [0-9]+, Errors: [0-9]+, Skipped: [0-9]+$' "$unit_log" "$integration_log"

rm -f "$unit_log" "$integration_log"
[ "$unit_status" -eq 0 ] && [ "$integration_status" -eq 0 ]
