#!/usr/bin/env bash
# FORCITIS one · C-3 · 在本机临时 postgres:18 容器里验证 c3_dev_db.sql：建库建角色 → 以迁移角色按版本执行全部基线迁移 →
# c3_dev_db_after_migrations.sql → 以运行角色检查各模块可用、版本表与审计表只追加。容器用完即删；口令运行时生成。
# 用法：c3_dev_db_check.sh   （需要本机 docker；退出码 0 全部通过，1 有检查未过）
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
repo="$(cd "$here/../../../../../.." && pwd)"
migrations="$repo/backend/src/main/resources/db/migration"
name="one-c3-check-$$"
superpw="$(openssl rand -hex 16)"; migpw="$(openssl rand -hex 16)"; apppw="$(openssl rand -hex 16)"
trap 'docker rm -f "$name" >/dev/null 2>&1 || true' EXIT

docker run -d --rm --name "$name" -e POSTGRES_PASSWORD="$superpw" postgres:18 >/dev/null
until docker exec "$name" pg_isready -U postgres -q 2>/dev/null; do sleep 1; done
sleep 2
q() { local user="$1" pw="$2" db="$3"; shift 3; docker exec -i -e PGPASSWORD="$pw" "$name" psql -X -q -v ON_ERROR_STOP=1 -h 127.0.0.1 -U "$user" -d "$db" "$@"; }

# RDS's high-privilege account is a member of rds_superuser and may grant on schemas it does not own; a plain CREATEROLE role
# may not, so the container runs the script as its superuser (the script itself ran on RDS as one_admin on 2026-10-15)
q postgres "$superpw" postgres -f - < "$here/c3_dev_db.sql" >/dev/null
q postgres "$superpw" postgres -c "ALTER ROLE one_dev_migrator PASSWORD '$migpw'" -c "ALTER ROLE one_dev_app PASSWORD '$apppw'"

while IFS= read -r file; do
  q one_dev_migrator "$migpw" forcitis_one_dev -f - < "$file" >/dev/null
done < <(find "$migrations" -name 'V*__*.sql' | awk -F/ '{print $NF "\t" $0}' | sort | cut -f2)
q postgres "$superpw" forcitis_one_dev -f - < "$here/c3_dev_db_after_migrations.sql" >/dev/null

failed=0
check() {
  local label="$1" sql="$2" expect="$3" out
  if out="$(q one_dev_app "$apppw" forcitis_one_dev -At -c "$sql" 2>&1)"; then out="ok"; else out="denied"; fi
  if [ "$out" = "$expect" ]; then echo "PASS  $label"; else echo "FAIL  $label (expected $expect, got $out)"; failed=1; fi
}
check "idempotency table readable"           "SELECT count(*) FROM platform_idempotency.idempotent_responses" ok
check "idempotency table writable"           "BEGIN; DELETE FROM platform_idempotency.idempotent_responses WHERE false; ROLLBACK" ok
check "approval schema readable"             "SELECT count(*) FROM platform_approval.approval_requests" ok
check "timesheet schema readable"            "SELECT count(*) FROM cost_timesheet.timesheets" ok
check "no table creation in module schemas"  "CREATE TABLE platform_idempotency.not_allowed (id bigint)" denied
check "version table delete refused"         "DELETE FROM organization_workforce.legal_entity_versions WHERE false" denied
check "version table truncate refused"       "TRUNCATE organization_workforce.org_unit_versions" denied
check "audit delete refused"                 "DELETE FROM platform_audit.audit_entries WHERE false" denied
exit "$failed"
