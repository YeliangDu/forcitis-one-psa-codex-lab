#!/usr/bin/env bash
# Middle CI tier (scripts/ci/relevant_change.py, tier=guarded): run only the container-free backend test classes that read the changed docs.
# A class that produced no report (typo, moved, deleted) fails instead of passing vacuously.
# Usage: guarded_tests.sh Class1,Class2
set -euo pipefail
classes=${1:?usage: guarded_tests.sh Class1,Class2}
cd "$(dirname "$0")/../../backend"
bash ../scripts/generate-api.sh --check
for class in ${classes//,/ }; do
  rm -f target/surefire-reports/*"$class".* "target/surefire-reports/TEST-"*".$class.xml"
done
# surefire:test after test-compile, not the test phase: the export-openapi execution is bound to process-test-classes and starts a PostgreSQL container
./mvnw -q test-compile surefire:test -Dtest="$classes"
for class in ${classes//,/ }; do
  ls target/surefire-reports/TEST-*."$class".xml >/dev/null 2>&1 || { echo "$class produced no report: it did not run" >&2; exit 1; }
done
echo "guarded tests ran: $classes"
