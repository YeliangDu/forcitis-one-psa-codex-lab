#!/usr/bin/env bash
# CI form of `./mvnw verify`: compile once, then surefire (*Test) and failsafe (*IT) run side by side; same goals and tests, no jacoco:report.
set -uo pipefail
cd "$(dirname "$0")/../../backend"

./mvnw -B test-compile "$@" || exit 1

unit_log=$(mktemp)
integration_log=$(mktemp)
./mvnw -B jacoco:prepare-agent surefire:test "$@" >"$unit_log" 2>&1 &
unit_pid=$!
./mvnw -B jacoco:prepare-agent -Djacoco.destFile=target/jacoco-it.exec failsafe:integration-test failsafe:verify "$@" >"$integration_log" 2>&1 &
integration_pid=$!

wait "$unit_pid"; unit_status=$?
wait "$integration_pid"; integration_status=$?

echo "::group::surefire (exit $unit_status)"; cat "$unit_log"; echo "::endgroup::"
echo "::group::failsafe (exit $integration_status)"; cat "$integration_log"; echo "::endgroup::"
grep -hE '^\[(INFO|WARNING|ERROR)\] Tests run: [0-9]+, Failures: [0-9]+, Errors: [0-9]+, Skipped: [0-9]+$' "$unit_log" "$integration_log"

rm -f "$unit_log" "$integration_log"
[ "$unit_status" -eq 0 ] && [ "$integration_status" -eq 0 ]
