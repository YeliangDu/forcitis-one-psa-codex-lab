#!/usr/bin/env bash
# 轻检查的同一入口（GOV-1023-FV2 O9，定稿 A39）：CI 轻作业跑它，本机交付前也可以选跑它；取代 scripts/precheck.sh。
# 用法：scripts/ci/light.sh [<基线>]   基线默认 origin/main（本脚本不 fetch）；传空串或 40 个 0（新分支的推送）时只跑自测，不比基线。
#       scripts/ci/light.sh --self-test  只核对「按改动触发」的路径模式
# 每次全量跑下面每一项，只有「按改动触发」一节沿用 precheck.sh 的路径模式（改动＝相对基线的提交、未提交与未跟踪文件；没有基线时全跑）；
# 一项失败不中断，最后汇总，有 FAIL 退出 1，基线认不出退出 2。
# CI 里由 .github/workflows/ci.yml 的 backend 作业调用（每个事件都跑）。不在这里的：
# - 后端与前端测试、SQL Server 集成测试：backend、frontend 作业的重步骤（本机要跑就用 scripts/ci/backend_verify.sh、legacy_it.sh，起容器先问用户）；
# - gitleaks 全史扫描、发布脚本自测：secret scan 作业；组织接口通知要 PR 正文，判档（relevant_change.py 输出）要写 $GITHUB_OUTPUT：都只在 CI 里；
# - budget_check（one-quality 的 scripts/ci/budget_check.py，用法 `budget_check.py [--root DIR]`）：文件在就跑，还没合入时记 SKIP。
# Bash 3.2（macOS）可用：不用关联数组与 mapfile。
set -uo pipefail

# 按改动触发的路径模式（ERE，对仓库相对路径；原 precheck.sh 的 touches 模式，照搬）
t_decision_gen='rules/decide/(build_decision_page\.py|page-template\.html)$'
t_decision_pages='architecture/decision-page/[^/]+\.json$|rules/decide/sheets/[^/]+\.json$|architecture/decision-page/check_pages\.py$'
t_j_checks='architecture/proposals/(j_checks|aggregates_dep_check|vs_links_gen)\.py$'
t_shared_mock='src/test/.*\.java$|scripts/ci/shared_mock_verify_scan\.py$'
t_weak_registration='src/(test|main)/.*\.java$|scripts/ci/weak_registration_scan\.py$'
t_shell='\.sh$'

touches() {  # touches <模式>：没有基线时一律算改到；否则看 $changed 里有没有匹配的路径
  [ -z "$base" ] || grep -Eq "$1" "$changed"
}

if [ "${1-}" = --self-test ]; then
  changed=$(mktemp "${TMPDIR:-/tmp}/light-self.XXXXXX") || exit 2
  trap 'rm -f "$changed"' EXIT
  bad=0
  expect() {  # expect <路径> <应触发的模式名…>：每个模式逐个核对，应触发的触发、其余不触发
    local path=$1 key want got
    shift
    printf '%s\n' "$path" >"$changed"
    for key in t_decision_gen t_decision_pages t_j_checks t_shared_mock t_weak_registration t_shell; do
      case " $* " in *" $key "*) want=1 ;; *) want=0 ;; esac
      if touches "${!key}"; then got=1; else got=0; fi
      [ "$want" = "$got" ] || { echo "不对：$path 对 $key 应为 $want，实为 $got"; bad=1; }
    done
  }
  base=x
  # 示例路径用 x/ 代替文档根目录前缀：写全了会被 relevant_change 当成本脚本读文档（模式本身不锚定前缀）
  expect x/rules/decide/build_decision_page.py t_decision_gen
  expect x/rules/decide/page-template.html t_decision_gen
  expect x/architecture/decision-page/D-01.json t_decision_pages
  expect x/rules/decide/sheets/q.json t_decision_pages
  expect x/architecture/decision-page/check_pages.py t_decision_pages
  expect x/architecture/decision-page/old/D-01.json
  expect x/architecture/proposals/j_checks.py t_j_checks
  expect x/architecture/proposals/vs_links_gen.py t_j_checks
  expect backend/src/test/java/com/forcitis/one/a/FooTest.java t_shared_mock t_weak_registration
  expect backend/src/main/java/com/forcitis/one/a/Foo.java t_weak_registration
  expect scripts/ci/shared_mock_verify_scan.py t_shared_mock
  expect scripts/ci/weak_registration_scan.py t_weak_registration
  expect scripts/gates/install.sh t_shell
  expect x/notes.txt
  : >"$changed"
  ! touches "$t_weak_registration" || { echo "不对：有基线而没有改动时不该触发"; bad=1; }
  base=""
  for key in t_decision_gen t_decision_pages t_j_checks t_shared_mock t_weak_registration t_shell; do
    touches "${!key}" || { echo "不对：没有基线时 $key 应触发"; bad=1; }
  done
  [ $bad = 0 ] && echo "light.sh self-test: ok"
  exit $bad
fi

root=$(git rev-parse --show-toplevel 2>/dev/null) || { echo "light.sh：不在 git 仓库里" >&2; exit 2; }
cd "$root" || exit 2
base=${1-origin/main}
[ "$base" = 0000000000000000000000000000000000000000 ] && base=""
if [ -n "$base" ] && ! git rev-parse --verify -q "$base^{commit}" >/dev/null; then
  echo "light.sh：基线 $base 不是本机的提交（先 fetch，或传别的基线）" >&2
  exit 2
fi
work=$(mktemp -d "${TMPDIR:-/tmp}/light.XXXXXX") || exit 2
trap 'rm -rf "$work"' EXIT
changed=$work/changed
if [ -n "$base" ]; then  # 同 precheck.sh：基线以来的提交、未提交、未跟踪
  {
    git -c core.quotepath=off diff --name-only --no-renames "$base...HEAD"
    git -c core.quotepath=off diff --name-only --no-renames HEAD
    git -c core.quotepath=off ls-files --others --exclude-standard
  } | grep . | sort -u >"$changed"
fi

passed=0
failed=0
skipped=0
started_all=$SECONDS

run() {  # run <名字> <命令…>：PASS 或 FAIL 加用时；输出只在失败时显示最后 30 行
  local name=$1 log="$work/$((passed + failed)).log" started=$SECONDS
  shift
  if "$@" >"$log" 2>&1; then
    printf 'PASS  %-46s %4d s\n' "$name" $((SECONDS - started))
    passed=$((passed + 1))
  else
    printf 'FAIL  %-46s %4d s（退出 %s）\n' "$name" $((SECONDS - started)) "$?"
    tail -n 30 "$log" | sed 's/^/      | /'
    failed=$((failed + 1))
  fi
}

skip() {
  printf 'SKIP  %-46s %s\n' "$1" "$2"
  skipped=$((skipped + 1))
}

against_base() {  # against_base <名字> <命令…>：命令后面接上基线；没有基线时 SKIP
  local name=$1
  shift
  if [ -n "$base" ]; then
    run "$name" "$@" "$base"
  else
    skip "$name" "没有基线（新分支的推送）：只跑了自测"
  fi
}

pyyaml() {  # SUPPLY_CHAIN S1：生成文档与若干守护读 YAML，版本钉死
  python3 -c 'import sys, yaml; sys.exit(None if yaml.__version__ == "6.0.3" else f"PyYAML {yaml.__version__} != 6.0.3（SUPPLY_CHAIN S1，CI_RUNNERS.md）")'
}

echo "light.sh：基线 ${base:-无}，HEAD $(git rev-parse --short HEAD)"
command -v python3 >/dev/null || { echo "FAIL  找不到 python3" >&2; exit 1; }

# 供应链与生成文档（原 secret scan 作业里的步骤）
run "python3 has PyYAML 6.0.3" pyyaml
run "generated docs match fragments" python3 docs/modernization/build_all.py --check
run "no legacy system name in docs" python3 docs/modernization/check_legacy_name.py
run "rule review revisions (self-test)" python3 docs/modernization/rules/review_revisions.py --self-test
against_base "rule review revisions are append-only" python3 docs/modernization/rules/review_revisions.py --base
run "database scripts aligned (self-test)" python3 -I scripts/ci/db_scripts_aligned.py --self-test
run "database scripts aligned" python3 -I scripts/ci/db_scripts_aligned.py

# 只增不改的登记与验收守护（原 backend 作业里的步骤）
run "migrations immutable (self-test)" python3 scripts/ci/migrations_immutable.py --self-test
against_base "migrations immutable" python3 scripts/ci/migrations_immutable.py
run "system actors append-only (self-test)" python3 scripts/ci/system_actors_append_only.py --self-test
against_base "system actors append-only" python3 scripts/ci/system_actors_append_only.py
for script in acceptance_guard acceptance_reports acceptance_rulings; do
  run "$script (self-test)" python3 "scripts/ci/$script.py" --self-test
done
against_base "acceptance guard" python3 scripts/ci/acceptance_guard.py
against_base "acceptance rulings" python3 scripts/ci/acceptance_rulings.py

# 门、钩子与 CI 脚本的自测
run "gates (gates_selftest.py)" python3 scripts/gates/gates_selftest.py
for script in scripts/ci/relevant_change.py scripts/hooks/codex_stdin_gate.py scripts/ci/light_unit_classes.py scripts/ci/heavy_gate.py scripts/ci/docker_diagnostics.py \
  scripts/ci/failure_summary.py scripts/ci/repeat_new_tests.py scripts/ci/heavy_slot.py scripts/hooks/session_context.py scripts/hooks/start_sheet.py scripts/coord/mailbox.py; do
  run "$(basename "$script" .py) (self-test)" python3 "$script" --self-test
done
run "light.sh triggers (self-test)" bash scripts/ci/light.sh --self-test

# 按改动触发（原 precheck.sh 的同名步骤；旧 ci.yml 不跑，不加进每次都跑）
by_change() {  # by_change <名字> <模式> <命令…>
  local name=$1 pattern=$2
  shift 2
  if touches "$pattern"; then run "$name" "$@"; else skip "$name" "相关路径没改（按改动触发）"; fi
}
by_change "decision page generator (self-test)" "$t_decision_gen" python3 docs/modernization/rules/decide/build_decision_page.py --self-test
by_change "decision pages: question rules (self-test)" "$t_decision_pages" python3 docs/modernization/architecture/decision-page/check_pages.py --self-test
if touches "$t_decision_pages"; then
  against_base "decision pages: question rules" python3 docs/modernization/architecture/decision-page/check_pages.py --changed
fi
by_change "j_checks (self-test)" "$t_j_checks" python3 docs/modernization/architecture/proposals/j_checks.py --self-test
by_change "shared-mock verify scan (self-test)" "$t_shared_mock" python3 scripts/ci/shared_mock_verify_scan.py --self-test
by_change "shared-mock verify scan (report only)" "$t_shared_mock" python3 scripts/ci/shared_mock_verify_scan.py
by_change "weak-registration scan (self-test)" "$t_weak_registration" python3 scripts/ci/weak_registration_scan.py --self-test
by_change "weak-registration scan (report only)" "$t_weak_registration" python3 scripts/ci/weak_registration_scan.py
shell_syntax() {  # 改到的 .sh 逐个 bash -n（没有基线时全部已跟踪的 .sh）；已删除的跳过
  local file
  while IFS= read -r file; do
    [ ! -f "$file" ] || bash -n "$file" || return 1
  done < <(if [ -n "$base" ]; then grep -E "$t_shell" "$changed"; else git ls-files '*.sh'; fi)
}
by_change "shell syntax (bash -n)" "$t_shell" shell_syntax

# 体量与单一出处（one-quality）
if [ -f scripts/ci/budget_check.py ]; then
  run "budget_check" python3 scripts/ci/budget_check.py
else
  skip "budget_check" "scripts/ci/budget_check.py 还没合入（one-quality 的 claude/one-quality-flow-v2-b1）"
fi

echo
echo "light.sh：$passed PASS，$failed FAIL，$skipped SKIP，共 $((SECONDS - started_all)) 秒（基线 ${base:-无}）"
[ "$failed" -eq 0 ]
