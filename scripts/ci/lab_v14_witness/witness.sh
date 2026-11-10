#!/usr/bin/env bash
# DRAFT hosted-only; author has NOT executed Docker/SQL/shell/test.
set -euo pipefail
[[ ${GITHUB_ACTIONS:-} == true && ${GITHUB_REPOSITORY:-} == YeliangDu/forcitis-one-psa-codex-lab ]] || { echo 'HOLD: hosted personal Lab only' >&2; exit 2; }
[[ $# == 3 ]] || { echo 'usage: witness.sh <actual-v14.sql> <preloaded-pg18-image@sha256> <output-dir>' >&2; exit 2; }
v14_source=$1
fixture_image=$2
fixture_output=$3
[[ -f "$v14_source" && "$fixture_image" =~ ^postgres:18[^@]*@sha256:[0-9a-f]{64}$ ]] || { echo 'HOLD: exact verifier and pinned PG18 image required' >&2; exit 2; }
fixture_here=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
mkdir -p -- "$fixture_output"
fixture_output=$(cd -- "$fixture_output" && pwd -P)
fixture_container="lab-v14-${GITHUB_RUN_ID:-hosted}-$$"
cleanup() { docker rm -f "$fixture_container" >/dev/null 2>&1 || true; }
trap cleanup EXIT
# Image must already be pinned/preloaded by independently reviewed hosted setup.
# No pull, published port, host network, volume, credentials or actual setup SQL.
docker run --pull=never -d --rm --network none --name "$fixture_container" \
  -e POSTGRES_HOST_AUTH_METHOD=trust -e POSTGRES_USER=fixture_admin -e POSTGRES_DB=fixture_v14 \
  "$fixture_image" > "$fixture_output/container-id"
fixture_ready=false
for ((index=0; index<60; index++)); do
  if docker exec "$fixture_container" pg_isready -q -U fixture_admin -d fixture_v14; then fixture_ready=true; break; fi
  sleep 1
done
[[ $fixture_ready == true ]] || { echo 'SETUP HOLD: scratch PostgreSQL unavailable' >&2; exit 2; }
docker exec "$fixture_container" psql -X -U fixture_admin -d fixture_v14 -Atc 'select version()' > "$fixture_output/postgres-version.txt"
docker inspect "$fixture_container" --format '{{.HostConfig.NetworkMode}} {{.Image}}' > "$fixture_output/isolation.txt"
[[ $(docker inspect "$fixture_container" --format '{{.HostConfig.NetworkMode}}') == none ]]
sha256sum "$v14_source" > "$fixture_output/verifier.sha256"
[[ $(sha256sum "$v14_source" | cut -d' ' -f1) == 54608db1b27c49ff2ef44c177493b1af2de114442be0265486931f59b70d25da ]] || { echo 'HOLD: unexpected V14 owner/TEMP verifier bytes' >&2; exit 2; }
docker cp "$v14_source" "$fixture_container:/tmp/v14-actual.sql" >/dev/null
# Never rewrite the verifier. It is invoked with \i for every test.
psql_input() { docker exec -i "$fixture_container" psql -X -U fixture_admin -d fixture_v14 -f -; }
psql_input < "$fixture_here/bootstrap.sql" > "$fixture_output/setup.log" 2>&1
case_sql() {
  local label=$1 expected=$2 mutation=$3
  local input="$fixture_output/$label.sql" log="$fixture_output/$label.log"
  local stdout_file="$fixture_output/$label.stdout" stderr_file="$fixture_output/$label.stderr" status=0
  {
    printf '\\set VERBOSITY verbose\n\\set ON_ERROR_STOP on\nBEGIN;\n'
    printf '%s\n' "$mutation"
    printf '\\echo FIXTURE_MUTATION_READY\n\\set ON_ERROR_STOP off\n\\i /tmp/v14-actual.sql\n'
    printf '\\echo FIXTURE_VERIFIER_RETURNED\nROLLBACK;\n'
  } > "$input"
  psql_input < "$input" > "$stdout_file" 2> "$stderr_file" || status=$?
  # Separate streams are authoritative; concatenate only AFTER process completion
  # for a convenience artifact, never for marker/SQLSTATE matching.
  cat "$stdout_file" "$stderr_file" > "$log"
  if [[ $status != 0 ]]; then echo "SETUP HOLD: $label mutation/connection failed" >&2; return 2; fi
  grep -q '^FIXTURE_MUTATION_READY$' "$stdout_file" || { echo "SETUP HOLD: $label mutation not reached" >&2; return 2; }
  grep -q '^FIXTURE_VERIFIER_RETURNED$' "$stdout_file" || { echo "SETUP HOLD: $label verifier not returned" >&2; return 2; }
  if [[ $expected == PASS ]]; then
    if grep -q 'ERROR:' "$stderr_file"; then echo "FAIL: $label expected actual V14 pass" >&2; return 1; fi
  else
    grep -q 'ERROR: *P0001: *V14 failed:' "$stderr_file" || { echo "FAIL: $label lacks exact V14 exception" >&2; return 1; }
    grep -Fq "$expected" "$stderr_file" || { echo "FAIL: $label wrong V14 refusal" >&2; return 1; }
    # Actual verifier's informational SELECT follows its DO: aborted-transaction
    # 25P02 is allowed only as a consequence, never accepted as initial failure.
    if grep 'ERROR:' "$stderr_file" | grep -Ev 'ERROR: *(P0001: *V14 failed:|25P02:)' >/dev/null; then
      echo "FAIL: $label unrelated SQL/parser failure" >&2; return 1
    fi
  fi
  printf '%s\t%s\n' "$label" "$expected" >> "$fixture_output/results.tsv"
}
case_sql good-empty PASS '-- no mutation'
case_sql missing-owner-temp 'one_dev_owner lacks database TEMP prerequisite' 'REVOKE TEMPORARY ON DATABASE fixture_v14 FROM PUBLIC; REVOKE TEMPORARY ON DATABASE fixture_v14 FROM one_dev_owner;'
case_sql absent-schema 'operations_facts is missing or has the wrong owner' 'DROP SCHEMA operations_facts CASCADE;'
case_sql wrong-schema-owner 'operations_facts is missing or has the wrong owner' 'ALTER SCHEMA operations_facts OWNER TO one_dev_app;'
psql_input < "$fixture_here/objects.sql" > "$fixture_output/objects.log" 2>&1
case_sql good-after-owner-objects PASS '-- native invoker PUBLIC EXECUTE is informational; definer PUBLIC is revoked'
case_sql schema-public 'schema-only schema access' 'GRANT USAGE ON SCHEMA operations_facts TO PUBLIC;'
case_sql schema-create 'schema-only schema access' 'GRANT CREATE ON SCHEMA operations_facts TO one_dev_readonly;'
case_sql table-read 'on relation operations_facts.fixture_table' 'GRANT SELECT ON operations_facts.fixture_table TO one_dev_app;'
case_sql column-read 'has column SELECT on operations_facts.fixture_table' 'GRANT SELECT(value) ON operations_facts.fixture_table TO one_dev_readonly;'
case_sql sequence-use 'on sequence operations_facts.fixture_sequence' 'GRANT USAGE ON SEQUENCE operations_facts.fixture_sequence TO one_dev_app;'
case_sql global-default-table 'owner defaults can authorize runtime role' 'ALTER DEFAULT PRIVILEGES FOR ROLE one_dev_owner GRANT SELECT ON TABLES TO one_dev_readonly;'
case_sql scoped-default-sequence 'owner defaults can authorize runtime role' 'ALTER DEFAULT PRIVILEGES FOR ROLE one_dev_owner IN SCHEMA operations_facts GRANT USAGE ON SEQUENCES TO PUBLIC;'
case_sql inherited-table-read 'on relation operations_facts.fixture_table' 'GRANT SELECT ON operations_facts.fixture_table TO fixture_member; GRANT fixture_member TO one_dev_app WITH INHERIT TRUE;'
case_sql explicit-function-runtime 'explicit function ACL authorizes runtime role' 'GRANT EXECUTE ON FUNCTION operations_facts.fixture_invoker() TO one_dev_app;'
case_sql definer-public 'SECURITY DEFINER functions allow PUBLIC EXECUTE' 'GRANT EXECUTE ON FUNCTION operations_facts.fixture_definer() TO PUBLIC;'
# Ownership invariants are expected new verifier checks. Do not accept a
# different effective-privilege error as proof that wrong ownership was checked.
case_sql wrong-table-owner 'operations_facts.fixture_table (kind r) owner is one_dev_app, expected one_dev_owner' 'ALTER TABLE operations_facts.fixture_table OWNER TO one_dev_app;'
case_sql wrong-sequence-owner 'operations_facts.fixture_sequence (kind S) owner is one_dev_readonly, expected one_dev_owner' 'ALTER SEQUENCE operations_facts.fixture_sequence OWNER TO one_dev_readonly;'
case_sql wrong-function-owner 'routine operations_facts.fixture_invoker() owner is fixture_admin, expected one_dev_owner' 'ALTER FUNCTION operations_facts.fixture_invoker() OWNER TO fixture_admin;'
case_sql final-clean PASS '-- every mutation rolled back independently'
docker logs "$fixture_container" > "$fixture_output/postgres.log" 2>&1
printf 'PASS: actual V14 scratch-only role/ACL cases; no deployment claim\n'
