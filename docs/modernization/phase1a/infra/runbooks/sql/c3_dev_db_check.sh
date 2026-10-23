#!/usr/bin/env bash
# FORCITIS one · C-3 · 在本机临时 postgres:18 容器里验证 c3_dev_db.sql：建库建角色 → 以迁移角色按版本执行全部基线迁移 →
# c3_dev_db_after_migrations.sql → 以运行角色与只读角色检查各模块可用、版本表与审计表只追加。容器用完即删；口令运行时生成。
# 用法：c3_dev_db_check.sh   （需要本机 docker；退出码 0 全部通过，1 有检查未过）
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
repo="$(cd "$here/../../../../../.." && pwd)"
migrations="$repo/backend/src/main/resources/db/migration"
name="one-c3-check-$$"
superpw="$(openssl rand -hex 16)"; migpw="$(openssl rand -hex 16)"; apppw="$(openssl rand -hex 16)"; ropw="$(openssl rand -hex 16)"; mtpw="$(openssl rand -hex 16)"
trap 'docker rm -f "$name" >/dev/null 2>&1 || true' EXIT

docker run -d --rm --name "$name" -e POSTGRES_PASSWORD="$superpw" postgres:18 >/dev/null
until docker exec "$name" pg_isready -U postgres -q 2>/dev/null; do sleep 1; done
sleep 2
q() { local user="$1" pw="$2" db="$3"; shift 3; docker exec -i -e PGPASSWORD="$pw" "$name" psql -X -q -v ON_ERROR_STOP=1 -h 127.0.0.1 -U "$user" -d "$db" "$@"; }

# RDS's high-privilege account is a member of rds_superuser and may grant on schemas it does not own; a plain CREATEROLE role
# may not, so the container runs the script as its superuser (the script itself ran on RDS as one_admin on 2026-10-15)
q postgres "$superpw" postgres -f - < "$here/c3_dev_db.sql" >/dev/null
q postgres "$superpw" postgres -c "ALTER ROLE one_dev_migrator PASSWORD '$migpw'" -c "ALTER ROLE one_dev_app PASSWORD '$apppw'" -c "ALTER ROLE one_dev_readonly PASSWORD '$ropw'" -c "ALTER ROLE one_dev_audit_maintainer PASSWORD '$mtpw'"

while IFS= read -r file; do
  q one_dev_migrator "$migpw" forcitis_one_dev -f - < "$file" >/dev/null
done < <(find "$migrations" -name 'V*__*.sql' | awk -F/ '{print $NF "\t" $0}' | sort | cut -f2)
q postgres "$superpw" forcitis_one_dev -f - < "$here/c3_dev_db_after_migrations.sql" >/dev/null

failed=0
# ok = the statement succeeded; denied = it failed with insufficient_privilege (42501); anything else (e.g. a missing table) is an error
check() {
  local label="$1" role="$2" pw="$3" sql="$4" expect="$5" out
  if out="$(q "$role" "$pw" forcitis_one_dev -At -v VERBOSITY=verbose -c "$sql" 2>&1)"; then out="ok"
  elif grep -q '42501' <<< "$out"; then out="denied"
  else out="error: $(head -1 <<< "$out")"; fi
  # rule = the call got past the privilege check and the function itself refused (an unarchived month), i.e. an error other than 42501
  if [ "$expect" = rule ] && [ "${out#error: }" != "$out" ]; then out="rule"; fi
  if [ "$out" = "$expect" ]; then echo "PASS  $label"; else echo "FAIL  $label (expected $expect, got $out)"; failed=1; fi
}
app() { check "$1" one_dev_app "$apppw" "$2" "$3"; }
mt() { check "$1" one_dev_audit_maintainer "$mtpw" "$2" "$3"; }
ro() { check "$1" one_dev_readonly "$ropw" "$2" "$3"; }
app "idempotency table readable"           "SELECT count(*) FROM platform_idempotency.idempotent_responses" ok
app "idempotency insert / update / delete" "BEGIN; INSERT INTO platform_idempotency.idempotent_responses (idempotent_response_id, user_id, idempotency_key, request_hash, expires_at) VALUES (gen_random_uuid(), '00000000-0000-7000-8000-000000000001', 'check', repeat('a', 64), now()); UPDATE platform_idempotency.idempotent_responses SET response_number = 200 WHERE user_id = '00000000-0000-7000-8000-000000000001'; DELETE FROM platform_idempotency.idempotent_responses WHERE user_id = '00000000-0000-7000-8000-000000000001'; ROLLBACK" ok
app "approval schema readable"             "SELECT count(*) FROM platform_approval.approval_requests" ok
app "timesheet schema readable"            "SELECT count(*) FROM cost_timesheet.timesheets" ok
app "no table creation in module schemas"  "CREATE TABLE platform_idempotency.not_allowed (id bigint)" denied
app "version table delete refused"         "DELETE FROM organization_workforce.legal_entity_versions WHERE false" denied
app "version table has no TRUNCATE grant"  "DO \$\$ BEGIN IF has_table_privilege('organization_workforce.org_unit_versions', 'TRUNCATE') THEN RAISE insufficient_privilege; END IF; END \$\$" ok
app "audit delete refused"                 "DELETE FROM platform_audit.audit_entries WHERE false" denied
# A month beyond the baseline's six pre-created ones, so the SECURITY DEFINER function really creates a partition (an existing month returns 0 without creating)
future=2030-01-01
app "audit partition pre-creation (creates)"   "BEGIN; SELECT platform_audit.create_audit_entry_partitions(date '$future', date '$future'); ROLLBACK" ok
app "app cannot drop an archived partition"    "SELECT platform_audit.drop_archived_audit_entry_partition(date '2026-10-01')" denied
ro  "readonly cannot pre-create partitions"    "SELECT platform_audit.create_audit_entry_partitions(date '2026-10-01', date '2026-10-01')" denied
mt  "maintainer cannot pre-create partitions"  "SELECT platform_audit.create_audit_entry_partitions(date '2026-10-01', date '2026-10-01')" denied
mt  "maintainer may call drop (refused by the archive rule, not by privilege)" "SELECT platform_audit.drop_archived_audit_entry_partition(date '2026-10-01')" rule
mt  "maintainer cannot read audit entries"       "SELECT count(*) FROM platform_audit.audit_entries" denied
ro  "readonly reads idempotency table"     "SELECT count(*) FROM platform_idempotency.idempotent_responses" ok
# The readonly role's sessions default to read-only; opening a read-write transaction tests the grant itself
ro  "readonly has no write grant"          "BEGIN READ WRITE; INSERT INTO platform_idempotency.idempotent_responses (idempotent_response_id, user_id, idempotency_key, request_hash, expires_at) VALUES (gen_random_uuid(), '00000000-0000-7000-8000-000000000001', 'x', repeat('a', 64), now()); ROLLBACK" denied
# The verify script runs without error after the migrations, and the maintainer role has nothing beyond platform_audit (V6b: empty result sets, can_temp f)
verify="$(q postgres "$superpw" forcitis_one_dev -At -f - < "$here/c3_dev_db_verify.sql" 2>&1)" || { echo "FAIL  c3_dev_db_verify.sql ran with an error: $(head -3 <<< "$verify")"; failed=1; }
# -At prints nothing for an empty result, so the row right after the V6 database ACL row is V6b's can_temp (f)
if [ "$(awk 'index($0, "forcitis_one_dev|") == 1 {getline; print; exit}' <<< "$verify")" = f ]; then echo "PASS  verify script runs; maintainer holds nothing outside platform_audit and no temp"; else echo "FAIL  verify output for the maintainer role"; failed=1; fi
# V6c: the runtime and readonly roles hold no TEMP and the runtime role's search_path starts with pg_catalog (two t rows right after the maintainer's can_temp)
if [ "$(awk 'index($0, "forcitis_one_dev|") == 1 {getline; getline a; getline b; print a b; exit}' <<< "$verify")" = tt ]; then echo "PASS  runtime and readonly roles hold no temp; runtime search_path starts with pg_catalog"; else echo "FAIL  verify output for V6c (runtime temp / search_path)"; failed=1; fi
exit "$failed"
