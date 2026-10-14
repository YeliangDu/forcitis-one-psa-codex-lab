#!/usr/bin/env bash
# Pre-delivery self-check (CODING_STANDARD §7, CS-7.1). Runs the checks that apply to what changed since the base, offline.
# Usage: scripts/precheck.sh [base-ref] [--run-backend]
#   base-ref       defaults to origin/main; the script never fetches, so fetch first if the base may be stale
#   --run-backend  also run the suggested backend tests (Testcontainers starts containers: ask the user first, CLAUDE.md)
# Bash 3.2 compatible (macOS): no associative arrays, no mapfile.
set -uo pipefail

base="origin/main"
run_backend=false
for argument in "$@"; do
  case "$argument" in
    --run-backend) run_backend=true ;;
    -h|--help) sed -n '2,6p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    -*) echo "unknown option: $argument" >&2; exit 2 ;;
    *) base="$argument" ;;
  esac
done

root=$(git rev-parse --show-toplevel 2>/dev/null) || { echo "FAIL  not inside a git repository"; exit 1; }
cd "$root" || exit 1

work=$(mktemp -d "${TMPDIR:-/tmp}/precheck.XXXXXX")
trap 'rm -rf "$work"' EXIT

passed=0; failed=0; skipped=0
report() {
  local status=$1 name=$2 reason=$3
  printf '%-5s %-34s %s\n' "$status" "$name" "$reason"
  case "$status" in PASS) passed=$((passed + 1)) ;; FAIL) failed=$((failed + 1)) ;; SKIP) skipped=$((skipped + 1)) ;; esac
}

show_tail() {
  grep -v '^警告：' "$1" | sed 's/^/      | /' | tail -n "${2:-20}"
}

run_step() {
  local name=$1; shift
  local log="$work/$(echo "$name" | tr -c 'A-Za-z0-9' '_').log"
  if "$@" >"$log" 2>&1; then
    local last
    last=$(grep -v '^警告：' "$log" | grep . | tail -n 1 | cut -c1-90)
    report PASS "$name" "${last:-ok}"
  else
    report FAIL "$name" "exit $? — last lines:"
    show_tail "$log"
  fi
}

if ! git rev-parse --verify --quiet "$base^{commit}" >/dev/null; then
  report FAIL "base" "$base is not a commit here (fetch it, or pass a base ref)"
  echo "SUMMARY: 0 PASS, 1 FAIL, 0 SKIP (base $base unresolved)"
  exit 1
fi

{
  git diff --name-only "$base...HEAD"
  git diff --name-only HEAD
  git ls-files --others --exclude-standard
} | sort -u >"$work/changed"
changed_count=$(grep -c . "$work/changed" || true)
uncommitted=$( { git diff --name-only HEAD; git ls-files --others --exclude-standard; } | grep -c . || true)

echo "precheck: base $base ($(git rev-parse --short "$base")), HEAD $(git rev-parse --short HEAD), $changed_count changed files ($uncommitted uncommitted)"
echo

touches() { grep -Eq "$1" "$work/changed"; }

python=python3
command -v "$python" >/dev/null || { report FAIL "python3" "python3 not found; every check below needs it"; }

run_step "generated docs (build_all --check)" "$python" docs/modernization/build_all.py --check
run_step "legacy system name" "$python" docs/modernization/check_legacy_name.py

if touches '^(backend/src/(main/resources/db/migration|test/resources/db/testdata)/|backend/architecture/migration-baseline-open|backend/src/main/resources/application.*\.(properties|ya?ml)$|scripts/ci/migrations_immutable\.py$)'; then
  run_step "migrations immutable (self-test)" "$python" scripts/ci/migrations_immutable.py --self-test
  run_step "migrations immutable" "$python" scripts/ci/migrations_immutable.py "$base"
else
  report SKIP "migrations immutable" "no migration, marker or Flyway setting changed"
fi

if touches '^(backend/src/test/java/com/forcitis/one/acceptance/|backend/src/test/resources/acceptance/|frontend/.*__acceptance__/|scripts/ci/acceptance_)'; then
  run_step "acceptance guard (self-test)" "$python" scripts/ci/acceptance_guard.py --self-test
  if [ "$uncommitted" -gt 0 ] && touches '__acceptance__/|/acceptance/'; then
    echo "      note: uncommitted acceptance changes are judged only once committed (trailers live in commit messages)"
  fi
  run_step "acceptance guard" "$python" scripts/ci/acceptance_guard.py "$base"
else
  report SKIP "acceptance guard" "no acceptance test changed"
fi

if touches '^(docs/modernization/phase1a/infra/one_db_roles_schemas.sql|docs/modernization/phase1a/infra/runbooks/sql/|backend/src/main/resources/db/migration/|scripts/ci/db_scripts_aligned\.py$)'; then
  run_step "database scripts aligned (self-test)" "$python" -I scripts/ci/db_scripts_aligned.py --self-test
  run_step "database scripts aligned" "$python" -I scripts/ci/db_scripts_aligned.py
else
  report SKIP "database scripts aligned" "no database script or migration changed"
fi

if touches '^(backend/|frontend/|scripts/)'; then
  run_step "docs read by builds are registered" "$python" scripts/ci/relevant_change.py --self-test
else
  report SKIP "docs read by builds are registered" "no backend, frontend or script changed"
fi

if touches '(src/test/.*\.java|\.test\.tsx?|\.spec\.tsx?)$'; then
  if "$python" scripts/ci/assertion_drift.py "$base" >"$work/drift.log" 2>&1; then
    if ! grep -q '^assertion drift: 0 finding' "$work/drift.log"; then
      report PASS "assertion drift (warning only)" "notes below: list them under 「改动的断言 / 测试及理由」"
      show_tail "$work/drift.log" 15
    else
      report PASS "assertion drift (warning only)" "no assertion removed or weakened"
    fi
  else
    report FAIL "assertion drift (warning only)" "script error:"
    show_tail "$work/drift.log"
  fi
else
  report SKIP "assertion drift (warning only)" "no test file changed"
fi

if touches '^scripts/release/'; then
  release_syntax() { local script; for script in scripts/release/*.sh; do bash -n "$script" || return 1; done; }
  run_step "release scripts syntax" release_syntax
  run_step "release scripts self-test" sh -c "$python -I scripts/release/ci_passed.py --self-test && $python -I scripts/release/manifest.py --self-test && $python -I scripts/release/deploy_gate.py --self-test"
fi

if touches '\.sh$'; then
  shell_files=$(grep -E '\.sh$' "$work/changed" | while read -r file; do [ -f "$file" ] && printf '%s\n' "$file"; done)
  if [ -n "$shell_files" ]; then
    syntax_all() { local file; for file in $shell_files; do bash -n "$file" || return 1; done; }
    run_step "shell syntax (bash -n)" syntax_all
    if command -v shellcheck >/dev/null; then
      # shellcheck disable=SC2086
      run_step "shellcheck" shellcheck -x $shell_files
    else
      report SKIP "shellcheck" "shellcheck not installed (CI uses 0.11.0 on scripts/release)"
    fi
  fi
fi

if ! command -v gitleaks >/dev/null; then
  report SKIP "gitleaks (full history)" "gitleaks not installed; install 8.30.1 (CLAUDE.md) — CI will still scan"
elif [ "$(gitleaks version 2>/dev/null)" != "8.30.1" ]; then
  report SKIP "gitleaks (full history)" "gitleaks $(gitleaks version 2>/dev/null) installed, CI uses 8.30.1"
else
  if gitleaks git --redact --no-banner --exit-code 1 --report-format json --report-path "$work/leaks.json" . >"$work/leaks.log" 2>&1; then
    report PASS "gitleaks (full history)" "$(grep -Eo '[0-9]+ commits scanned' "$work/leaks.log" | tail -n 1); no leaks"
  else
    report FAIL "gitleaks (full history)" "findings (rule, file:line, commit; values never printed):"
    "$python" -I - "$work/leaks.json" <<'PY' | sed 's/^/      | /'
import json, sys
try:
    findings = json.load(open(sys.argv[1]))
except Exception as error:
    print(f"no readable report ({error.__class__.__name__}); rerun gitleaks with --redact yourself")
    sys.exit()
for f in findings:
    print(f"{f.get('RuleID')}  {f.get('File')}:{f.get('StartLine')}  {str(f.get('Commit', ''))[:10]}")
PY
  fi
  if [ "$uncommitted" -gt 0 ]; then
    echo "      note: uncommitted files are not in history; the pre-commit hook scans them on commit"
  fi
fi

if touches '^backend/'; then
  modules=$(grep -E '^backend/src/(main|test)/java/com/forcitis/one/[a-z]+/[a-z]+/' "$work/changed" \
    | sed -E 's#^backend/src/(main|test)/java/com/forcitis/one/([a-z]+)/([a-z]+)/.*#\2.\3#' | sort -u)
  if [ -n "$modules" ]; then
    selection=$(echo "$modules" | sed 's#^#com.forcitis.one.#; s#$#.**#' | paste -sd, -)
  else
    selection=""
  fi
  guards="ArchitectureRulesTest,ModularityTest,ModuleDependencyTest,NamingSurfaceTest,GlossaryTest,DdlNamingTest,LegacyShapeTest,TimeSourceGuardTest,TestIsolationGuardTest"
  if [ -n "$selection" ]; then
    tests="$selection,$guards"
    scope="modules: $(echo "$modules" | paste -sd' ' -)"
  else
    tests="$guards"
    scope="no module code changed (kernel, resources, build or architecture lists)"
  fi
  command_line="(cd backend && ./mvnw -q test -Dtest='$tests' -Dsurefire.failIfNoSpecifiedTests=false)"
  if $run_backend; then
    run_step "backend tests (affected + guards)" sh -c "cd backend && ./mvnw -q test -Dtest='$tests' -Dsurefire.failIfNoSpecifiedTests=false"
  else
    report SKIP "backend tests" "not run by default; $scope"
    echo "      run: $command_line"
    echo "      before delivery run everything: scripts/ci/backend_verify.sh (Testcontainers: ask the user before starting containers)"
  fi
  if touches '^backend/src/main/java/.*/(web|api)/' && ! touches '^frontend/packages/api/openapi\.json$'; then
    echo "      note: web or api code changed but openapi.json did not; the export test rewrites it — commit the result (CS-1.10)"
  fi
else
  report SKIP "backend tests" "backend not changed"
fi

if touches '^frontend/'; then
  if ! command -v pnpm >/dev/null; then
    report SKIP "frontend verify" "pnpm not installed"
  elif [ ! -d frontend/node_modules ]; then
    report SKIP "frontend verify" "dependencies not installed (pnpm install --frozen-lockfile downloads packages: ask first)"
  elif ! node --version 2>/dev/null | grep -q '^v24\.'; then
    report SKIP "frontend verify" "Node $(node --version 2>/dev/null) found, CI uses Node 24"
  else
    run_step "frontend verify (pnpm verify)" pnpm -C frontend verify
  fi
else
  report SKIP "frontend verify" "frontend not changed"
fi

echo
summary="SUMMARY: $passed PASS, $failed FAIL, $skipped SKIP (base $base)"
if [ "$failed" -gt 0 ]; then
  echo "$summary — not ready"
  exit 1
fi
echo "$summary — ready"
