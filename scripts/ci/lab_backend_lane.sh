#!/usr/bin/env bash
# Execute one exact-head backend slot on a fresh hosted runner.
set -euo pipefail
slot=$1
input=$2
output=$3
selector=${4:-}
: "${LAB_HEAD:?exact PR head required}"
: "${GITHUB_RUN_ID:?GitHub workflow required}"
: "${GITHUB_RUN_ATTEMPT:?GitHub attempt required}"
test "$(git rev-parse HEAD)" = "$LAB_HEAD"
test "$(cat "$input/head")" = "$LAB_HEAD"
test "$(cat "$input/run")" = "$GITHUB_RUN_ID"
test "$(cat "$input/attempt")" = "$GITHUB_RUN_ATTEMPT"
mkdir -p "$output" frontend/packages/api/src
cp "$input/api/openapi.json" frontend/packages/api/
cp -R "$input/api/generated" frontend/packages/api/src/
docker info > "$output/docker.log"
verify_script="$PWD/scripts/ci/lab_backend_shards.py"
if [[ "$slot" = legacy ]]; then
  test -z "$selector"
  BACKEND_WATCHDOG_SECONDS=2700 bash scripts/ci/legacy_it.sh -C -Dit.forks=1 -Djacoco.skip=true \
    2>&1 | tee "$output/legacy.log"
  reports=backend/target/legacy-it-reports
else
  case "$slot" in
    unit) test -z "$selector"; reports=target/surefire-reports ;;
    1|2|3|4) test -n "$selector"; reports=target/failsafe-reports ;;
    *) exit 2 ;;
  esac
  cd backend
  ./mvnw -B -C package -DskipTests -Djacoco.skip=true 2>&1 | tee "$output/package.log"
  if [[ "$slot" = unit ]]; then
    ./mvnw -B -C surefire:test -Dtest.forks=1 -Djacoco.skip=true -Dtest.fork.timeout=1500 \
      2>&1 | tee "$output/surefire.log"
  else
    ./mvnw -B -C failsafe:integration-test failsafe:verify "-Dit.test=$selector" \
      -Dit.forks=1 -Djacoco.skip=true -Dtest.fork.timeout=2700 2>&1 | tee "$output/failsafe.log"
  fi
fi
verify_args=()
if [[ -n "$selector" ]]; then verify_args=(--selector "$selector"); fi
python3 -B "$verify_script" verify "$reports" "${verify_args[@]}" > "$output/reconciled.json"
jq --arg head "$LAB_HEAD" --arg run "$GITHUB_RUN_ID" --arg attempt "$GITHUB_RUN_ATTEMPT" --arg slot "$slot" \
  '. + {head:$head,run:$run,attempt:$attempt,slot:$slot}' "$output/reconciled.json" > "$output/result.json"
