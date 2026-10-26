#!/usr/bin/env bash
# CI form of `./mvnw verify -Plegacy-it` for a pull request: package with the profile, then run only the SQL Server
# integration tests (the POM's single acceptance list plus *MssqlIT; failsafe fails when none matches). The full suite under the profile runs
# nightly (nightly-legacy-it.yml via backend_verify.sh -Plegacy-it).
set -euo pipefail
cd "$(dirname "$0")/../../backend"
bash ../scripts/generate-api.sh --check

tests=$(python3 ../scripts/ci/acceptance_reports.py --legacy-it-tests)
watchdog_dir=$(mktemp -d)
watchdog_mark="$watchdog_dir/timed-out"
dumps=target/thread-dumps
forks="$PWD/target/surefire/"
(
  trap 'kill "$s" 2>/dev/null; exit 0' TERM
  sleep "${BACKEND_WATCHDOG_SECONDS:-1500}" & s=$!
  wait "$s"
  mkdir -p "$dumps"
  for pid in $(pgrep -f "java .*$forks" || true); do
    jcmd "$pid" Thread.print -l >"$dumps/$pid.txt" 2>&1 || true
  done
  touch "$watchdog_mark"
  if [ -s "$watchdog_dir/maven.pid" ]; then
    kill "$(cat "$watchdog_dir/maven.pid")" 2>/dev/null || true
  fi
  pkill -f "java .*$forks" 2>/dev/null || true
  sleep 20
  pkill -9 -f "java .*$forks" 2>/dev/null || true
) &
watchdog_pid=$!
cleanup() {
  kill "$watchdog_pid" 2>/dev/null || true
  wait "$watchdog_pid" 2>/dev/null || true
  rm -rf "$watchdog_dir"
}
trap cleanup EXIT
wait_maven() {
  local maven_pid=$1
  printf '%s\n' "$maven_pid" >"$watchdog_dir/maven.pid"
  if [ -e "$watchdog_mark" ]; then
    kill "$maven_pid" 2>/dev/null || true
  fi
  wait "$maven_pid"
}
status=0
./mvnw -B package -DskipTests -Djacoco.skip=true -Plegacy-it "$@" &
wait_maven "$!" || status=$?
if [ "$status" -eq 0 ] && [ ! -e "$watchdog_mark" ]; then
  rm -rf target/legacy-it-reports
  ./mvnw -B failsafe:integration-test failsafe:verify -Plegacy-it -Dit.test="$tests" -Dintegration.reports="$PWD/target/legacy-it-reports" "$@" &
  wait_maven "$!" || status=$?
fi
if [ -e "$watchdog_mark" ]; then
  wait "$watchdog_pid" 2>/dev/null || true
  echo "::error::SQL Server tests exceeded ${BACKEND_WATCHDOG_SECONDS:-1500} s; thread dumps are in backend/$dumps"
  status=124
fi
[ "$status" -eq 0 ] || exit "$status"
python3 ../scripts/ci/acceptance_reports.py --tier legacy-it --report-dir backend/target/legacy-it-reports
