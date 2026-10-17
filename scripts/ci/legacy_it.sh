#!/usr/bin/env bash
# CI form of `./mvnw verify -Plegacy-it` for a pull request: compile the tests with the profile, then run only the SQL Server
# integration tests (the POM's single acceptance list plus *MssqlIT; failsafe fails when none matches). The full suite under the profile runs
# nightly (nightly-legacy-it.yml via backend_verify.sh -Plegacy-it).
set -euo pipefail
cd "$(dirname "$0")/../../backend"

tests=$(python3 ../scripts/ci/acceptance_reports.py --legacy-it-tests)
./mvnw -B test-compile -Djacoco.skip=true -Plegacy-it "$@"
rm -rf target/legacy-it-reports
./mvnw -B failsafe:integration-test failsafe:verify -Plegacy-it -Dit.test="$tests" -Dintegration.reports="$PWD/target/legacy-it-reports" "$@"
python3 ../scripts/ci/acceptance_reports.py --tier legacy-it --report-dir backend/target/legacy-it-reports
