#!/usr/bin/env bash
# 轻检查的同一入口（GOV-1023-FV2 O9，定稿 A39）：CI 轻作业跑它，本机交付前也可以选跑它；取代 scripts/precheck.sh。
# 用法：scripts/ci/light.sh [<基线>]   基线默认 origin/main（本脚本不 fetch）；传空串或 40 个 0（新分支的推送）时只跑自测，不比基线。
# 每次全量跑下面每一项，只有「按改动触发」一节按 light_triggers.sh 的路径模式挑（没有基线时全跑；自测 light_selftest.sh）；一项失败不中断，最后汇总，有 FAIL 退出 1，基线认不出退出 2。
# CI 里由 .github/workflows/ci.yml 的 backend 作业调用（每个事件都跑）。不在这里的：
# - 后端与前端测试、SQL Server 集成测试：backend、frontend 作业的重步骤（本机要跑就用 scripts/ci/backend_verify.sh、legacy_it.sh，起容器先问用户）；
# - gitleaks 全史扫描、发布脚本自测：secret scan 作业；组织接口通知要 PR 正文，判档（relevant_change.py 输出）要写 ${GITHUB_OUTPUT}：都只在 CI 里；
# - budget_check（one-quality 的 scripts/ci/budget_check.py，用法 `budget_check.py [--root DIR]`）：文件在就跑，还没合入时记 SKIP。
# OpenAPI 与前端 generated 不入仓（frontend/packages/api/README.md）：build_all.py --check（全景）要它们已生成。CI 由 api-contract 作业提供；本机没有时先 bash scripts/generate-api.sh（起容器导出，先问用户），或只看 build_all.py --check --without-api。
# Bash 3.2（macOS）可用：不用关联数组与 mapfile。
set -uo pipefail
export PYTHONDONTWRITEBYTECODE=1  # 不留 __pycache__：backend 作业随后在同一工作区跑测试，遍历目录的测试读到 .pyc 会崩（#693）；python3 -I 不认它，另由 rm_new_pyc 删
root=$(git rev-parse --show-toplevel 2>/dev/null) || { echo "light.sh：不在 git 仓库里" >&2; exit 2; }
cd "$root" || exit 2
base=${1-origin/main}
[ "$base" = 0000000000000000000000000000000000000000 ] && base=""
if [ -n "$base" ] && ! git rev-parse --verify -q "$base^{commit}" >/dev/null; then
  echo "light.sh：基线 $base 不是本机的提交（先 fetch，或传别的基线）" >&2
  exit 2
fi
work=$(mktemp -d "${TMPDIR:-/tmp}/light.XXXXXX") || exit 2
trap 'rm_new_pyc >/dev/null 2>&1; rm -rf "$work"' EXIT  # 中途退出也删本次新增的字节码；trap 不调 exit，退出码不变
changed=$work/changed
# shellcheck source=scripts/ci/light_triggers.sh
. scripts/ci/light_triggers.sh
pyc >"$work/pyc_before" || exit 2  # 跑前已有的被忽略字节码，只删这之后新增的
if [ -n "$base" ] && ! list_changed "$changed"; then
  echo "light.sh：列不出相对 $base 的改动（与 HEAD 没有共同祖先？）" >&2
  exit 2
fi

passed=0 failed=0 skipped=0
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
run "deploy.yml: checkout no global git config" python3 -I -c 'import sys,yaml; s=[x for x in yaml.safe_load(open(".github/workflows/deploy.yml"))["jobs"]["deploy"]["steps"] if str(x.get("uses","")).lower().startswith("actions/checkout@")]; w=[{str(k).lower(): v for k, v in (x.get("with") or {}).items()} for x in s]; sys.exit("deploy 作业找不到 actions/checkout 步" if not s else 0 if all(x.get("set-safe-directory") is False and not x.get("submodules") for x in w) else "deploy 作业的 checkout 须写 set-safe-directory: false、不开 submodules：否则全局 git 配置落进 $HOME/.gitconfig，toolchain.sh 按 R-0 拒绝（run 40387799615，569ed501f）")'

# 只增不改的登记与验收守护（原 backend 作业里的步骤）
for script in migrations_immutable system_actors_append_only acceptance_guard acceptance_reports acceptance_rulings; do
  run "$script (self-test)" python3 "scripts/ci/$script.py" --self-test
done
against_base "migrations immutable" python3 scripts/ci/migrations_immutable.py
against_base "system actors append-only" python3 scripts/ci/system_actors_append_only.py
against_base "acceptance guard" python3 scripts/ci/acceptance_guard.py
against_base "acceptance rulings" python3 scripts/ci/acceptance_rulings.py

# 门、钩子、CI 脚本与文档工具的自测（生成器名单取 build_all.py 的清单，O14 起不由 build_all 跑；列不出记一项 FAIL）
run "gates (gates_selftest.py)" python3 scripts/gates/gates_selftest.py
sheet=${ONE_SESSION_TOOLS:-$HOME/.modernize/psa/tools/one-session}/start_sheet/start_sheet_selftest.py  # 开工单固定由 /usr/bin/python3 跑（#47），3.10 以上语法在这里就红；工具库不在本机（CI）时同 acceptance_v2 V05 不判红
if [ -f "$sheet" ]; then run "start_sheet under /usr/bin/python3" /usr/bin/python3 -I "$sheet"; else skip "start_sheet under /usr/bin/python3" "本机没有工具库的 $sheet"; fi
listed=$(python3 docs/modernization/build_all.py --self-test-list) || listed="build_all-self-test-list-failed"
for script in docs/modernization/build_all.py $listed docs/modernization/adr/new_adr.py docs/modernization/glossary/term_rules.py scripts/queue/merge_train.py scripts/merge/mq.py \
  scripts/ci/ci_recurrence.py scripts/ci/relevant_change.py scripts/ci/light_unit_classes.py scripts/ci/heavy_gate.py scripts/ci/docker_diagnostics.py \
  scripts/ci/failure_summary.py scripts/ci/repeat_new_tests.py scripts/ci/heavy_slot.py docs/modernization/new_intent_selftest.py; do
  run "$(basename "$script" .py) (self-test)" python3 "$script" --self-test
done
run "light_selftest (by-change triggers)" bash scripts/ci/light_selftest.sh

# 按改动触发（原 precheck.sh 的同名步骤，检查表在 light_triggers.sh；命令按空格切分）
while IFS='|' read -r key name cmd; do
  # shellcheck disable=SC2086
  if touches "${!key}"; then run "$name" $cmd; else skip "$name" "相关路径没改（按改动触发）"; fi
done <<<"$TRIGGERED_CHECKS"
if touches "$t_decision_pages"; then
  against_base "decision pages: question rules" python3 docs/modernization/rules/decide/check_pages.py --changed
fi

# 体量与单一出处（one-quality）
if [ -f scripts/ci/budget_check.py ]; then
  run "budget_check" python3 scripts/ci/budget_check.py
else
  skip "budget_check" "scripts/ci/budget_check.py 还没合入（one-quality 的 claude/one-quality-flow-v2-b1）"
fi
run "remove __pycache__ left by this run" rm_new_pyc

echo
echo "light.sh：$passed PASS，$failed FAIL，$skipped SKIP，共 $((SECONDS - started_all)) 秒（基线 ${base:-无}）"
[ "$failed" -eq 0 ]
