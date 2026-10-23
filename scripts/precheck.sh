#!/usr/bin/env bash
# Pre-delivery self-check (CODING_STANDARD CS-7.1). Runs the checks that apply to what changed since the base, offline.
# Usage: scripts/precheck.sh [base-ref] [--run-backend] [--pre-push]
#   base-ref       defaults to origin/main; the script never fetches, so fetch first if the base may be stale
#   --run-backend  also run the suggested backend tests (Testcontainers starts containers: ask the user first, CLAUDE.md)
#   --pre-push     what scripts/hooks/pre_push_gate.py runs before `git push`: the checks above, plus the unit test classes that start no container and that the change can affect (all of them when backend/ changed outside module sources); no gitleaks history scan, no full frontend verify (CI does both)
# A check the change requires but this machine cannot run is a FAIL, not a SKIP (SESSION_PLAYBOOK TEAM-44): the term tests when the glossary changed, the frontend
# when frontend/ or the glossary changed. scripts/hooks/precheck_before_pr.py runs this before `gh pr create` and blocks the PR unless it ends "ready".
# Reviewers do not run this on a branch they do not trust: it executes the branch's own scripts. CI is the gate there.
# Bash 3.2 compatible (macOS): no associative arrays, no mapfile.
set -uo pipefail

base="origin/main"
base_given=false
run_backend=false
pre_push=false
for argument in "$@"; do
  case "$argument" in
    --run-backend) run_backend=true ;;
    --pre-push) pre_push=true ;;
    -h|--help) sed -n '2,8p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    -*) echo "unknown option: $argument" >&2; exit 2 ;;
    *) base="$argument"; base_given=true ;;
  esac
done

root=$(git rev-parse --show-toplevel 2>/dev/null) || { echo "FAIL  not inside a git repository"; exit 1; }
cd "$root" || exit 1
# Heavy builds and test runs queue for one of two machine-wide slots and run at background priority (scripts/ci/heavy_slot.py)
heavy="$root/scripts/ci/heavy_slot.py"
# the push hook gives precheck 1400 s: a push that waits or runs longer than 600 s each fails here, the wrapper stopping its command, instead of being killed from outside with a half-run command left holding the slot
$pre_push && export FORCITIS_HEAVY_WAIT_SECONDS=${FORCITIS_HEAVY_WAIT_SECONDS:-600} FORCITIS_HEAVY_RUN_SECONDS=${FORCITIS_HEAVY_RUN_SECONDS:-600}

work=$(mktemp -d "${TMPDIR:-/tmp}/precheck.XXXXXX")
trap 'rm -rf "$work"' EXIT

gitq() { git -c core.quotepath=off "$@"; }

# -I keeps the caller's environment, user site and working directory off sys.path; the script's own directory is put back
# explicitly so that scripts/ci modules can import ci_git.
py() {
  python3 -I -c 'import runpy, sys, os; script = sys.argv[1]; sys.path.insert(0, os.path.dirname(os.path.abspath(script))); sys.argv = sys.argv[1:]; runpy.run_path(script, run_name="__main__")' "$@"
}

passed=0; failed=0; skipped=0
report() {
  local status=$1 name=$2 reason=$3
  printf '%-5s %-38s %s\n' "$status" "$name" "$reason"
  case "$status" in PASS) passed=$((passed + 1)) ;; FAIL) failed=$((failed + 1)) ;; SKIP) skipped=$((skipped + 1)) ;; esac
}

show_tail() {
  grep -v '^警告：' "$1" | sed 's/^/      | /' | tail -n "${2:-20}"
}

# corepack picks pnpm from the nearest package.json at start-up, and only frontend/ names it; `pnpm -C frontend` would start at the root
in_frontend() { (cd frontend && "$@"); }

# Empty when the frontend can run here; otherwise the reason. Installing from the local pnpm cache (--offline) downloads nothing and needs no asking; anything that downloads does (CLAUDE.md 9)
frontend_missing() {
  if ! command -v pnpm >/dev/null; then echo "pnpm 未安装：需先问用户安装"; return; fi
  if [ ! -d frontend/node_modules ]; then echo "前端依赖未安装：先在 frontend/ 运行 pnpm install --frozen-lockfile --offline（只用本机缓存、不下载）；缓存缺包而失败时，再问用户是否联网安装"; return; fi
  if ! node --version 2>/dev/null | grep -q '^v24\.'; then echo "Node $(node --version 2>/dev/null) found, CI uses Node 24：需先问用户安装"; fi
}

# Backend test classes that start no container: run by name; a class that did not run (typo, moved) fails instead of passing vacuously
backend_classes() {
  local classes=$1 class
  for class in $(printf '%s' "$classes" | tr ',' ' '); do rm -f backend/target/surefire-reports/*"$class".* backend/target/surefire-reports/TEST-*."$class".xml; done
  # surefire:test after test-compile, not the test phase: pom.xml binds the export-openapi execution (OpenApiExportTest: a PostgreSQL container, rewrites openapi.json) to process-test-classes, and -Dtest cannot switch it off
  (cd backend && python3 -I "$heavy" -- ./mvnw -q test-compile surefire:test -Dtest="$classes") || return 1
  for class in $(printf '%s' "$classes" | tr ',' ' '); do
    ls backend/target/surefire-reports/TEST-*."$class".xml >/dev/null 2>&1 || { echo "$class produced no report: it did not run"; return 1; }
  done
}

# The unit test classes that start no container and no Spring context and that the change can affect (scripts/ci/light_unit_classes.py reads the sources, so a new scan or guard test is included without anyone listing it;
# --changed drops the tests of modules the change did not touch, and anything in backend/ that is not module source brings them all back)
light_unit_tests() {
  local classes
  echo "scope: $(py scripts/ci/light_unit_classes.py --changed "$work/changed" --explain)"
  classes=$(py scripts/ci/light_unit_classes.py --changed "$work/changed") || return 1
  [ -n "$classes" ] || { echo "no light unit test classes found"; return 1; }
  backend_classes "$classes"
}

run_step() {
  local name=$1; shift
  local log
  log="$work/$(printf '%s' "$name" | tr -c 'A-Za-z0-9' '_').log"
  local status
  if "$@" >"$log" 2>&1; then
    local last
    last=$(grep -v '^警告：' "$log" | grep . | tail -n 1 | cut -c1-90)
    report PASS "$name" "${last:-ok}"
  else
    status=$?
    report FAIL "$name" "exit $status — last lines:"
    show_tail "$log"
  fi
}

command -v python3 >/dev/null || { echo "FAIL  python3 not found; every check needs it"; exit 1; }

if ! git rev-parse --verify --quiet "$base^{commit}" >/dev/null; then
  report FAIL "base" "$base is not a commit here (fetch it, or pass a base ref)"
  echo "SUMMARY: 0 PASS, 1 FAIL, 0 SKIP (base $base unresolved)"
  exit 1
fi

{
  gitq diff --name-only --no-renames "$base...HEAD"
  gitq diff --name-only --no-renames HEAD
  gitq ls-files --others --exclude-standard
} | grep . | sort -u >"$work/changed"
{ gitq diff --name-only --no-renames HEAD; gitq ls-files --others --exclude-standard; } | grep . | sort -u >"$work/uncommitted"
changed_count=$(grep -c . "$work/changed")
uncommitted=$(grep -c . "$work/uncommitted")

if ! $pre_push && ! $base_given; then py scripts/hooks/precheck_before_pr.py --snapshot >"$work/snapshot.json" 2>/dev/null || echo null >"$work/snapshot.json"; fi
echo "precheck: base $base ($(git rev-parse --short "$base")), HEAD $(git rev-parse --short HEAD), $changed_count changed files ($uncommitted uncommitted)"
echo

touches() { grep -Eq "$1" "$work/changed"; }

builds_relevant=false
relevant_output=$(py scripts/ci/relevant_change.py --paths-from "$work/changed" 2>&1)
relevant_status=$?
if [ "$relevant_status" -ne 0 ]; then
  report FAIL "relevant_change.py" "exit $relevant_status — treated as relevant, not as no change:"
  printf '%s\n' "$relevant_output" | sed 's/^/      | /' | tail -n 20
  builds_relevant=true
elif printf '%s\n' "$relevant_output" | grep -qx 'relevant=true'; then
  builds_relevant=true
fi
legacy_it_relevant=false
if [ "$relevant_status" -ne 0 ] || printf '%s\n' "$relevant_output" | grep -qx 'legacy_it=true'; then
  legacy_it_relevant=true
fi

run_step "generated docs (build_all --check)" py docs/modernization/build_all.py --check
run_step "legacy system name" py docs/modernization/check_legacy_name.py
if touches 'rules/decide/(build_decision_page\.py|page-template\.html)$'; then
  run_step "decision page generator (self-test)" py docs/modernization/rules/decide/build_decision_page.py --self-test
fi
if touches 'architecture/decision-page/[^/]+\.json$|rules/decide/sheets/[^/]+\.json$|architecture/decision-page/check_pages\.py$'; then
  run_step "decision pages: question rules (self-test)" py docs/modernization/architecture/decision-page/check_pages.py --self-test
  run_step "decision pages: question rules" py docs/modernization/architecture/decision-page/check_pages.py --changed "$base"
fi

if touches '^(backend/src/(main/resources/db/migration|test/resources/db/testdata)/|backend/architecture/migration-baseline-open|backend/src/main/resources/application.*\.(properties|ya?ml)$|scripts/ci/(migrations_immutable|ci_git)\.py$)'; then
  run_step "migrations immutable (self-test)" py scripts/ci/migrations_immutable.py --self-test
  run_step "migrations immutable" py scripts/ci/migrations_immutable.py "$base"
else
  report SKIP "migrations immutable" "no migration, marker or Flyway setting changed"
fi

if touches '^(backend/architecture/system-actors\.txt$|scripts/ci/(system_actors_append_only|ci_git)\.py$)'; then
  run_step "system actors append-only (self-test)" py scripts/ci/system_actors_append_only.py --self-test
  run_step "system actors append-only" py scripts/ci/system_actors_append_only.py "$base"
else
  report SKIP "system actors append-only" "system-actors.txt and its script did not change"
fi

if touches '^(backend/src/test/java/com/forcitis/one/acceptance/|backend/src/test/resources/acceptance/|frontend/.*__acceptance__/|docs/modernization/rulings/|scripts/ci/(acceptance_|ci_git))'; then
  run_step "acceptance guard (self-test)" py scripts/ci/acceptance_guard.py --self-test
  run_step "acceptance reports (self-test)" py scripts/ci/acceptance_reports.py --self-test
  run_step "acceptance rulings (self-test)" py scripts/ci/acceptance_rulings.py --self-test
  if grep -Eq '__acceptance__/|/acceptance/' "$work/uncommitted"; then
    echo "      note: uncommitted acceptance changes are judged only once committed (trailers live in commit messages)"
  fi
  run_step "acceptance guard" py scripts/ci/acceptance_guard.py "$base"
  run_step "acceptance rulings" py scripts/ci/acceptance_rulings.py "$base"
else
  report SKIP "acceptance guard" "no acceptance test changed"
fi

if touches '^(docs/modernization/phase1a/infra/one_db_roles_schemas.sql|docs/modernization/phase1a/infra/runbooks/sql/|backend/src/main/resources/db/migration/|scripts/ci/db_scripts_aligned\.py$)'; then
  run_step "database scripts aligned (self-test)" py scripts/ci/db_scripts_aligned.py --self-test
  run_step "database scripts aligned" py scripts/ci/db_scripts_aligned.py
else
  report SKIP "database scripts aligned" "no database script or migration changed"
fi

if touches '^(backend/|frontend/|scripts/)'; then
  run_step "docs read by builds are registered" py scripts/ci/relevant_change.py --self-test
else
  report SKIP "docs read by builds are registered" "no backend, frontend or script changed"
fi

if touches '(^(backend/src/main/resources/db/migration/|backend/architecture/(data-classification\.txt|module-dependencies\.txt)|scripts/ci/baseline_ddl_scan)|/togaf/transitions\.yaml$)'; then
  run_step "baseline DDL scan (self-test)" py scripts/ci/baseline_ddl_scan.py --self-test
  ddl_scan=$(py scripts/ci/baseline_ddl_scan.py 2>&1) && ddl_counts=$(printf '%s\n' "$ddl_scan" | grep '^== [1-8]' | sed -E 's/^== ([1-8]) [^:]*: ([0-9]+).*/\1=\2/' | paste -sd' ' -)
  if [ -n "${ddl_counts:-}" ]; then
    report PASS "baseline DDL scan (report only)" "${ddl_counts} (class=count; 8 must stay 0; details: python3 scripts/ci/baseline_ddl_scan.py)"
  else
    report FAIL "baseline DDL scan (report only)" "the scan itself failed:"
    printf '%s\n' "$ddl_scan" | tail -n 8 | sed 's/^/      | /'
  fi
fi
if touches 'architecture/proposals/(j_checks|aggregates_dep_check|vs_links_gen)\.py$'; then
  run_step "j_checks (self-test)" py docs/modernization/architecture/proposals/j_checks.py --self-test
fi
if touches '^scripts/ci/(assertion_drift|ci_git)\.py$'; then
  run_step "assertion drift (self-test)" py scripts/ci/assertion_drift.py --self-test
fi
if touches '(src/test/.*\.java|\.test\.tsx?|\.spec\.tsx?)$'; then
  if py scripts/ci/assertion_drift.py "$base" >"$work/drift.log" 2>&1; then
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
if touches 'src/test/.*\.java$|scripts/ci/shared_mock_verify_scan\.py$'; then
  run_step "shared-mock verify scan (self-test)" py scripts/ci/shared_mock_verify_scan.py --self-test
  if mock_scan=$(py scripts/ci/shared_mock_verify_scan.py 2>&1); then
    report PASS "shared-mock verify scan (report only)" "$(printf '%s\n' "$mock_scan" | tail -n 1)"
  else
    report FAIL "shared-mock verify scan (report only)" "the scan itself failed:"
    printf '%s\n' "$mock_scan" | tail -n 6 | sed 's/^/      | /'
  fi
fi
if touches 'src/(test|main)/.*\.java$|scripts/ci/weak_registration_scan\.py$'; then
  run_step "weak-registration scan (self-test)" py scripts/ci/weak_registration_scan.py --self-test
  if weak_scan=$(py scripts/ci/weak_registration_scan.py 2>&1); then
    report PASS "weak-registration scan (report only)" "$(printf '%s\n' "$weak_scan" | tail -n 1)"
    printf '%s\n' "$weak_scan" | head -n 6 | grep -v '^weak-registration scan' | sed 's/^/      | /' || true
  else
    report FAIL "weak-registration scan (report only)" "the scan itself failed:"
    printf '%s\n' "$weak_scan" | tail -n 6 | sed 's/^/      | /'
  fi
fi

grep -E '\.sh$' "$work/changed" >"$work/shell" || true
: >"$work/shell.present"
while IFS= read -r file; do
  if [ -f "$file" ]; then printf '%s\n' "$file" >>"$work/shell.present"; fi
done <"$work/shell"
if [ -s "$work/shell.present" ]; then
  syntax_all() {
    local file
    while IFS= read -r file; do bash -n "$file" || return 1; done <"$work/shell.present"
  }
  run_step "shell syntax (bash -n)" syntax_all
fi

if touches '^(docs/modernization/glossary/(terms|bcs)\.yaml|backend/glossary/)'; then
  missing=$(frontend_missing)
  if [ -n "$missing" ]; then
    report FAIL "glossary: frontend check:copy, test:scripts" "$missing"
  else
    run_step "glossary: frontend check:copy" in_frontend pnpm run check:copy
    run_step "glossary: frontend test:scripts" in_frontend pnpm run test:scripts
  fi
  run_step "glossary: backend term tests" backend_classes GlossaryTest,ErrorCodeGlossaryTest,NamingSurfaceTest,DocumentTypeCodeTest
else
  report SKIP "glossary: check:copy, test:scripts, term tests" "terms.yaml, bcs.yaml and backend/glossary unchanged"
fi

guard_pattern='^backend/src/test/java/.*(Guard|ArchitectureRules|Modularity|ModuleDependency|NamingSurface|Glossary|DdlNaming|LegacyShape)[A-Za-z]*Test\.java$'
guard_classes="ArchitectureRulesTest,ModularityTest,ModuleDependencyTest,NamingSurfaceTest,GlossaryTest,ErrorCodeGlossaryTest,DdlNamingTest,LegacyShapeTest,TimeSourceGuardTest,TestIsolationGuardTest,TestContainerGuardTest"
guard_container_note=
if touches '^backend/architecture/'; then
  # every light class that reads backend/architecture, found in the sources (a hand-kept list left out PrimaryKeyTypeGuardTest and about twenty more), plus the fixed guards
  if readers=$(py scripts/ci/light_unit_classes.py --reading architecture) && [ -n "$readers" ]; then
    guard_run="$guard_classes,$readers"
  else
    report FAIL "guards: architecture readers" "scripts/ci/light_unit_classes.py --reading architecture failed or found no class: only the fixed guards would run"
    guard_run="$guard_classes"
  fi
  guard_container_note=$(py scripts/ci/light_unit_classes.py --reading architecture --heavy) || guard_container_note=""
else
  # present files only (a deleted guard has nothing to run) and no container-starting classes (*IntegrationTest, *IT): CI runs those
  guard_run=$(grep -E "$guard_pattern" "$work/changed" | grep -vE '(IntegrationTest|IT)\.java$' | while IFS= read -r file; do [ -f "$file" ] && printf '%s\n' "$file"; done \
    | sed 's#.*/##; s#\.java$##' | sort -u | paste -sd, -)
fi
if [ -n "$guard_run" ]; then
  run_step "guards: backend guard tests" backend_classes "$guard_run"
  if [ -n "${guard_container_note:-}" ]; then
    echo "      note: these tests also read backend/architecture but start a container (CI runs them): $guard_container_note"
  fi
else
  report SKIP "guards: backend guard tests" "no guard test or architecture list changed"
fi
if touches '^(\.github/workflows/ci\.yml|scripts/precheck\.sh|scripts/hooks/(precheck_before_pr|pre_push_gate|codex_stdin_gate|main_checkout_gate|commit_msg_gate|bg_task_name_gate|task_ids|pre_tool_dispatch)\.py|scripts/ci/(heavy_slot|light_unit_classes|heavy_gate|docker_diagnostics|failure_summary|repeat_new_tests)\.py|\.claude/settings\.json)$'; then
  for guard in migrations_immutable acceptance_guard acceptance_reports db_scripts_aligned relevant_change assertion_drift; do
    run_step "guards: $guard (self-test)" py "scripts/ci/$guard.py" --self-test
  done
  run_step "guards: precheck_before_pr (self-test)" py scripts/hooks/precheck_before_pr.py --self-test
  run_step "guards: pre_push_gate (self-test)" py scripts/hooks/pre_push_gate.py --self-test
  run_step "guards: codex_stdin_gate (self-test)" py scripts/hooks/codex_stdin_gate.py --self-test
  run_step "guards: main_checkout_gate (self-test)" py scripts/hooks/main_checkout_gate.py --self-test
  run_step "guards: commit_msg_gate (self-test)" py scripts/hooks/commit_msg_gate.py --self-test
  run_step "guards: bg_task_name_gate (self-test)" py scripts/hooks/bg_task_name_gate.py --self-test
  run_step "guards: pre_tool_dispatch (self-test)" py scripts/hooks/pre_tool_dispatch.py --self-test
  run_step "guards: light_unit_classes (self-test)" py scripts/ci/light_unit_classes.py --self-test
  run_step "guards: heavy_gate (self-test)" py scripts/ci/heavy_gate.py --self-test
  run_step "guards: heavy_slot (self-test)" py scripts/ci/heavy_slot.py --self-test
  run_step "guards: docker_diagnostics (self-test)" py scripts/ci/docker_diagnostics.py --self-test
  run_step "guards: failure_summary (self-test)" py scripts/ci/failure_summary.py --self-test
  run_step "guards: repeat_new_tests (self-test)" py scripts/ci/repeat_new_tests.py --self-test
fi
if touches '^(scripts/hooks/((postcompact_context|sessionend_snapshot|precompact_snapshot)\.sh|(handoff_snapshot|handoff_selftest|handoff_safe_selftest|handoff_storage_selftest|session_context|start_sheet|context_handoff|stop_status_gate)\.py)|scripts/coord/mailbox\.py|scripts/hooks/context_windows\.json|scripts/precheck\.sh|.*build_all\.py|\.claude/settings\.json)$'; then
  run_step "guards: handoff (self-test)" py scripts/hooks/handoff_selftest.py --self-test
  run_step "guards: session_context (self-test)" py scripts/hooks/session_context.py --self-test
  run_step "guards: start_sheet (self-test)" py scripts/hooks/start_sheet.py --self-test
  run_step "guards: context_handoff (self-test)" py scripts/hooks/context_handoff.py --self-test
  run_step "guards: stop_status_gate (self-test)" py scripts/hooks/stop_status_gate.py --self-test
  run_step "guards: mailbox (self-test)" py scripts/coord/mailbox.py --self-test
fi

if [ "$builds_relevant" = true ]; then
  release_self_tests() {
    py scripts/release/ci_passed.py --self-test && py scripts/release/manifest.py --self-test && py scripts/release/deploy_gate.py --self-test
  }
  run_step "release scripts self-test" release_self_tests
  if command -v shellcheck >/dev/null; then
    run_step "shellcheck scripts/release" sh -c 'shellcheck -x scripts/release/*.sh'
  else
    report SKIP "shellcheck scripts/release" "shellcheck not installed (CI uses 0.11.0)"
  fi
else
  report SKIP "release scripts self-test, shellcheck" "relevant_change.py: nothing the release scripts or the builds read changed"
fi

if $pre_push; then
  report SKIP "gitleaks (full history)" "pre-push mode: CI scans the history"
elif ! command -v gitleaks >/dev/null; then
  report SKIP "gitleaks (full history)" "gitleaks not installed; install 8.30.1 (CLAUDE.md) — CI will still scan"
elif [ "$(gitleaks version 2>/dev/null)" != "8.30.1" ]; then
  report SKIP "gitleaks (full history)" "gitleaks $(gitleaks version 2>/dev/null) installed, CI uses 8.30.1"
else
  if gitleaks git --redact --no-banner --exit-code 1 --report-format json --report-path "$work/leaks.json" . >"$work/leaks.log" 2>&1; then
    report PASS "gitleaks (full history)" "$(grep -Eo '[0-9]+ commits scanned' "$work/leaks.log" | tail -n 1); no leaks"
  else
    report FAIL "gitleaks (full history)" "findings (rule, file:line, commit; values never printed):"
    python3 -I - "$work/leaks.json" <<'PY' | sed 's/^/      | /'
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

if $builds_relevant; then
  modules=$(grep -E '^backend/src/(main|test)/java/com/forcitis/one/[a-z]+/[a-z]+/' "$work/changed" \
    | sed -E 's#^backend/src/(main|test)/java/com/forcitis/one/([a-z]+)/([a-z]+)/.*#\2.\3#' | sort -u)
  guards="ArchitectureRulesTest,ModularityTest,ModuleDependencyTest,NamingSurfaceTest,GlossaryTest,DdlNamingTest,LegacyShapeTest,TimeSourceGuardTest,TestIsolationGuardTest"
  if touches '^backend/src/main/java/com/forcitis/one/kernel/'; then
    tests=""
    scope="kernel changed: every module depends on it"
  elif [ -n "$modules" ]; then
    tests="$(printf '%s\n' "$modules" | sed 's#^#com.forcitis.one.#; s#$#.**#' | paste -sd, -),$guards"
    scope="modules: $(printf '%s\n' "$modules" | paste -sd' ' -)"
  else
    tests="$guards"
    scope="no module code changed (shared inputs, resources, build or architecture lists)"
  fi
  # the default run covers everything --pre-push runs (these unit tests included), so a ready default run can leave the pass mark the push gate accepts (GOV-1023-QUA-04)
  if ! touches '^(backend/|docs/|frontend/)'; then
    $pre_push && report SKIP "pre-push: unit tests" "nothing the backend tests read changed; CI runs them"
  else
    run_step "pre-push: unit tests that start no container" light_unit_tests
  fi
  if $pre_push; then
    :
  elif [ -z "$tests" ]; then
    if $run_backend; then
      run_step "backend verify (full)" python3 -I "$heavy" -- scripts/ci/backend_verify.sh
    else
      report SKIP "backend tests" "not run by default; $scope"
      echo "      run the full suite: scripts/ci/backend_verify.sh (Testcontainers: ask the user before starting containers)"
    fi
  elif $run_backend; then
    run_step "backend tests (affected + guards)" sh -c 'cd backend && python3 -I "$1" -- ./mvnw -q test-compile surefire:test -Dtest="$2" -Dsurefire.failIfNoSpecifiedTests=false' precheck "$heavy" "$tests"
  else
    report SKIP "backend tests" "not run by default; $scope"
    echo "      run: (cd backend && ./mvnw -q test-compile surefire:test -Dtest='$tests' -Dsurefire.failIfNoSpecifiedTests=false)"
    echo "      before delivery run everything: scripts/ci/backend_verify.sh (Testcontainers: ask the user before starting containers)"
  fi
  if touches '^backend/src/main/java/.*/(web|api)/' && ! touches '^frontend/packages/api/openapi\.json$'; then
    echo "      note: web or api code changed but openapi.json did not; the export test rewrites it — commit the result (CS-1.10)"
  fi

  missing=$(frontend_missing)
  if $pre_push && [ -z "$missing" ]; then
    report SKIP "frontend verify" "pre-push mode: CI runs the full verify"
  elif [ -z "$missing" ]; then
    run_step "frontend verify (pnpm verify)" in_frontend pnpm verify
  elif touches '^frontend/'; then
    report FAIL "frontend verify" "frontend changed: $missing"
  else
    report SKIP "frontend verify" "${missing}（本次未改 frontend/，CI 仍会跑）"
  fi
else
  report SKIP "backend tests" "relevant_change.py: nothing the builds read changed"
  report SKIP "frontend verify" "relevant_change.py: nothing the builds read changed"
fi

if $legacy_it_relevant; then
  if ! $run_backend; then
    report SKIP "SQL Server integration tests" "not run by default; the change touches what the *MssqlIT tests exercise (profile legacy-it)"
    echo "      run: scripts/ci/legacy_it.sh (Testcontainers: ask the user before starting containers)"
  elif ! command -v docker >/dev/null || ! docker info >/dev/null 2>&1; then
    report SKIP "SQL Server integration tests" "Docker is not installed or not running; CI runs scripts/ci/legacy_it.sh"
  else
    run_step "SQL Server integration tests (legacy-it)" python3 -I "$heavy" -- scripts/ci/legacy_it.sh
  fi
else
  report SKIP "SQL Server integration tests" "relevant_change.py: nothing the *MssqlIT tests exercise changed"
fi

echo
summary="SUMMARY: $passed PASS, $failed FAIL, $skipped SKIP (base $base)"
if [ "$failed" -gt 0 ]; then
  echo "$summary — not ready"
  exit 1
fi
echo "$summary — ready"
# A ready run of the full set on the default base leaves the pass mark (precheck_before_pr.py): the push gate and the PR hook then do not run it again while commit, origin/main and a clean tree stay the same.
# Not for --pre-push (the gate writes its own), a base other than origin/main, or a tree that was dirty when the run began.
if ! $pre_push && ! $base_given; then
  py scripts/hooks/precheck_before_pr.py --record "$work/snapshot.json" || echo "note: pass mark not written"
fi
