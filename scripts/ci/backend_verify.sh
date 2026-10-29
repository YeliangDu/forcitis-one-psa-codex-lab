#!/usr/bin/env bash
# CI form of `./mvnw verify`: compile and package once (PackagedJarIT checks the executable jar), then surefire (*Test) and failsafe (*IT) run side by side;
# same tests, without the jacoco agent and report (nothing in CI reads coverage).
set -uo pipefail
cd "$(dirname "$0")/../../backend" || exit 1

# Both plugins read JSON and generated TS. CI downloads this run's api-contract artifact; local/nightly runs bootstrap first.
bash ../scripts/codegen/generate-api.sh --check || exit 1

./mvnw -B package -DskipTests -Djacoco.skip=true "$@" || exit 1

# Self-hosted checkouts can retain reports; only this invocation may vouch for its selected tier.
rm -rf target/surefire-reports target/failsafe-reports
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
  echo "OpenAPI 与本次前端生成输入不一致；请在仓库根重新运行 bash scripts/codegen/generate-api.sh 后重试。" >&2
  rm -f "$contract" "$export_log"
  exit 1
fi
rm -f "$contract" "$export_log"
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
