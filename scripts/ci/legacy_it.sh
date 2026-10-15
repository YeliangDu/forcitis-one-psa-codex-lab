#!/usr/bin/env bash
# CI form of `./mvnw verify -Plegacy-it` for a pull request: compile the tests with the profile, then run only the SQL Server
# integration tests (*MssqlIT, the profile's own pattern; failsafe fails when none matches). The full suite under the profile runs
# nightly (nightly-legacy-it.yml via backend_verify.sh -Plegacy-it).
set -euo pipefail
cd "$(dirname "$0")/../../backend"

./mvnw -B test-compile -Djacoco.skip=true -Plegacy-it "$@"
./mvnw -B failsafe:integration-test failsafe:verify -Plegacy-it -Dit.test='*MssqlIT' "$@"
