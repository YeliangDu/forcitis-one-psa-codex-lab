#!/usr/bin/env bash
# Self-test of backend_verify.sh's OpenAPI branch (#783): the export is skipped only when OPENAPI_EXPORTED_FOR names the
# checked-out commit; unset, empty or another commit export and compare, and a contract that differs stops the run
# before any test. Runs the real script in a throwaway repository with a stub mvnw; needs no Maven, Java or Docker.
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

mkdir -p "$tmp/scripts/ci" "$tmp/scripts/codegen" "$tmp/backend" "$tmp/frontend/packages/api"
cp "$here/backend_verify.sh" "$tmp/scripts/ci/"
printf '#!/usr/bin/env bash\nexit 0\n' >"$tmp/scripts/codegen/generate_api.sh"
# The stub logs every call; the export call writes $EXPORTED as the fresh contract.
cat >"$tmp/backend/mvnw" <<'EOF'
#!/usr/bin/env bash
echo "$*" >>"$STUB_LOG"
case "$*" in *export-openapi*) printf '%s' "$EXPORTED" >../frontend/packages/api/openapi.json ;; esac
exit 0
EOF
chmod +x "$tmp/scripts/ci/backend_verify.sh" "$tmp/scripts/codegen/generate_api.sh" "$tmp/backend/mvnw"
git -C "$tmp" init -q
git -C "$tmp" -c user.name=t -c user.email=t@example.invalid -c core.hooksPath= -c commit.gpgsign=false commit -q --allow-empty -m x
head=$(git -C "$tmp" rev-parse HEAD)

failures=0
case_() {  # case_ <name> <OPENAPI_EXPORTED_FOR or "-" for unset> <exported contract> <expected rc> <export expected: yes|no> <tests expected: yes|no>
  local name=$1 value=$2 exported=$3 want_rc=$4 want_export=$5 want_tests=$6 rc=0 log="$tmp/calls.log" out="$tmp/out.log"
  printf '{"same":1}' >"$tmp/frontend/packages/api/openapi.json"
  : >"$log"
  if [ "$value" = - ]; then
    env -u OPENAPI_EXPORTED_FOR STUB_LOG="$log" EXPORTED="$exported" BACKEND_WATCHDOG_SECONDS=30 bash "$tmp/scripts/ci/backend_verify.sh" >"$out" 2>&1 || rc=$?
  else
    OPENAPI_EXPORTED_FOR="$value" STUB_LOG="$log" EXPORTED="$exported" BACKEND_WATCHDOG_SECONDS=30 bash "$tmp/scripts/ci/backend_verify.sh" >"$out" 2>&1 || rc=$?
  fi
  local exported_seen=no tests_seen=no
  grep -q 'export-openapi' "$log" && exported_seen=yes
  grep -qE '(^|[[:space:]])(surefire:test|failsafe:integration-test)([[:space:]]|$)' "$log" && tests_seen=yes
  if [ "$rc" != "$want_rc" ] || [ "$exported_seen" != "$want_export" ] || [ "$tests_seen" != "$want_tests" ]; then
    echo "FAIL $name: rc=$rc (want $want_rc), export=$exported_seen (want $want_export), tests=$tests_seen (want $want_tests)"; sed 's/^/  | /' "$out" | tail -5
    failures=$((failures + 1))
  fi
}

case_ "this run's commit: no second export"          "$head"                                     '{"same":1}'   0 no  yes
case_ "this run's commit, contract would differ"      "$head"                                     '{"other":1}'  0 no  yes
case_ "unset: export and compare"                     -                                           '{"same":1}'   0 yes yes
case_ "empty: export and compare"                     ""                                          '{"same":1}'   0 yes yes
case_ "another commit: export and compare"            0000000000000000000000000000000000000000    '{"same":1}'   0 yes yes
case_ "unset, contract differs: stop before any test" -                                           '{"other":1}'  1 yes no
case_ "another commit, contract differs: stop"        0000000000000000000000000000000000000000    '{"other":1}'  1 yes no

[ "$failures" -eq 0 ] && echo "backend_verify_selftest: ok" || { echo "backend_verify_selftest: $failures failed"; exit 1; }
