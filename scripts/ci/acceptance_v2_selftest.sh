#!/usr/bin/env bash
# Self-test of acceptance_v2.sh: stub checkout and tool library, so it proves skip, pass and fail stay apart (a skip exits 0, a fail exits non-0).
set -uo pipefail
here=$(cd "$(dirname "$0")" && pwd)
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
bad=0
mkdir -p "$tmp/root/scripts/ci" "$tmp/root/scripts/merge" "$tmp/tools/handoff" "$tmp/tools/stop_check" "$tmp/tools/budget_level" "$tmp/budget"

# expect <name> <want exit: 0|nz> <output regex> <items> [env...]
expect() {
  local name=$1 want=$2 re=$3 items=$4; shift 4
  local out rc
  out=$(env ACCEPT_ROOT="$tmp/root" ONE_SESSION_TOOLS="$tmp/tools" ACCEPT_BUDGET_DIR="$tmp/budget" ACCEPT_ONLY="$items" "$@" bash "$here/acceptance_v2.sh" 2>&1); rc=$?
  if { [ "$want" = 0 ] && [ "$rc" = 0 ]; } || { [ "$want" = nz ] && [ "$rc" != 0 ]; }; then :; else echo "失败 $name：退出码 $rc"; bad=1; fi
  printf '%s\n' "$out" | grep -Eq "$re" || { echo "失败 $name：输出不含 /$re/"; printf '%s\n' "$out" | sed 's/^/    /'; bad=1; }
}
stub_budget() { printf 'print(%s)\n' "$1" >"$tmp/root/scripts/ci/budget_check.py"; }
ok_py() { printf 'import sys\nsys.exit(0)\n' >"$1"; }
bad_py() { printf 'import sys\nsys.exit(1)\n' >"$1"; }

# V01: info lines and other rules do not count, an H8 violation does, an unreadable result fails closed;
# RULES.md or roles missing is never a pass (skip, or a failure under ACCEPT_STRICT=1)
stub_budget "'信息 H7 docs/modernization/RULES.md 尚无\\nH23 docs/x.sh 运维脚本\\nbudget_check：违例 1 条，模式 warn'"
expect "V01 缺 RULES 与 roles 不得通过" 0 '^V01 跳过\(.*RULES.md' V01
expect "V01 缺文件严格模式失败" nz '^V01 不通过' V01 ACCEPT_STRICT=1
mkdir -p "$tmp/root/docs/modernization/roles"; echo r >"$tmp/root/docs/modernization/RULES.md"
expect "V01 只缺 roles 不得通过" 0 '^V01 跳过\(.*roles' V01
echo r >"$tmp/root/docs/modernization/roles/a.md"
expect "V01 其它项与信息行不算" 0 '^V01 通过' V01
stub_budget "'H8 a.md:3 字面值\\nbudget_check：违例 1 条，模式 warn'"
expect "V01 H8 违例" nz '^V01 不通过' V01
stub_budget "'没有汇总行'"
expect "V01 结果不可解析" nz '^V01 不通过' V01
stub_budget "'POLICY policy.json 无法读取\\nbudget_check：违例 1 条'"
expect "V01 POLICY 行" nz '^V01 不通过' V01

# V03: only role-file H7 and H17 count; role files missing is not a pass
stub_budget "'H7 docs/modernization/RULES.md 超\\nbudget_check：违例 1 条'"
expect "V03 规则文件的 H7 不算" 0 '^V03 通过' V03
stub_budget "'H7 docs/modernization/roles/a.md 4 KB\\nbudget_check：违例 1 条'"
expect "V03 角色 H7" nz '^V03 不通过' V03
stub_budget "'H17 docs/modernization/roles/a.md 交叠\\nbudget_check：违例 1 条'"
expect "V03 H17" nz '^V03 不通过' V03
stub_budget "'budget_check：违例 0 条'"
rm "$tmp/root/docs/modernization/roles/a.md"
expect "V03 无角色文件不得通过" 0 '^V03 跳过' V03
expect "V03 无角色文件严格模式失败" nz '^V03 不通过' V03 ACCEPT_STRICT=1
echo r >"$tmp/root/docs/modernization/roles/a.md"

# V02: no gates directory is a skip; a failing gate self-test is a fail; p99/version always a skip
expect "V02 无 gates 跳过" 0 '^V02 跳过\(scripts/gates 尚未合入\)' V02
mkdir -p "$tmp/root/scripts/gates"
expect "V02 无自测" nz '^V02 不通过' V02
ok_py "$tmp/root/scripts/gates/a_selftest.py"
expect "V02 自测通过" 0 '^V02 通过' V02
expect "V02 p99 与版本单列跳过" 0 'V02\(p99 与版本\) 跳过\(无看板数据\)' V02
bad_py "$tmp/root/scripts/gates/b_selftest.py"
expect "V02 自测失败" nz '^V02 不通过' V02
rm -f "$tmp/root/scripts/gates/b_selftest.py"
stub_budget "'H1 scripts/gates 太多\\nbudget_check：违例 1 条，模式 warn'"
expect "V02 H1 违例" nz '^V02 不通过' V02

# V05: library missing (or only one file) is a skip, a failing self-test is a fail
expect "V05 缺库跳过" 0 '^V05 跳过' V05
expect "V05 缺库严格模式失败" nz '^V05 不通过' V05 ACCEPT_STRICT=1
ok_py "$tmp/tools/handoff/handoff.py"
expect "V05 只缺一个跳过" 0 '^V05 跳过\(.*stop_check_selftest' V05
rm "$tmp/tools/handoff/handoff.py"; ok_py "$tmp/tools/stop_check/stop_check_selftest.py"
expect "V05 只缺另一个跳过" 0 '^V05 跳过\(.*handoff.py' V05
ok_py "$tmp/tools/handoff/handoff.py"
expect "V05 通过" 0 '^V05 通过' V05
bad_py "$tmp/tools/stop_check/stop_check_selftest.py"
expect "V05 失败" nz '^V05 不通过' V05

# V07
bad_py "$tmp/root/scripts/merge/mq.py"
expect "V07 失败" nz '^V07 不通过' V07
ok_py "$tmp/root/scripts/merge/mq.py"
expect "V07 通过" 0 '^V07 通过' V07

# V08: no data source yet, always a skip even when the static pieces exist
touch "$tmp/root/scripts/ci/relevant_change.py"
expect "V08 恒跳过" 0 '^V08 跳过\(无数据' V08

# V12: needs the budget keys (fail) and the budget_level self-test
echo '{"budget": {"line": {"from_pct": 0}, "ladder": {"L0": {}, "L1": {}, "L2": {}, "L3": {}}}}' >"$tmp/root/policy.json"
expect "V12 缺工具库跳过" 0 '^V12 跳过' V12
ok_py "$tmp/tools/budget_level/budget_level_selftest.py"
expect "V12 通过" 0 '^V12 通过' V12
bad_py "$tmp/tools/budget_level/budget_level_selftest.py"
expect "V12 自测失败" nz '^V12 不通过' V12
ok_py "$tmp/tools/budget_level/budget_level_selftest.py"
echo '{"budget": {"line": {"from_pct": 0}, "ladder": {"L0": {}, "L1": {}}}}' >"$tmp/root/policy.json"
expect "V12 缺梯子档" nz '^V12 不通过：policy.json 缺 budget.ladder.L2' V12
echo '{"x": 1}' >"$tmp/root/policy.json"
expect "V12 缺 budget" nz '^V12 不通过' V12

# V17a: missing/stale level.json fails, zero records is "no data" (skip), never a pass
expect "V17a 缺 level.json" nz '^V17a 不通过.*level.json 不存在' V17a
echo '{}' >"$tmp/budget/level.json"
expect "V17a 缺 verify.md" nz '^V17a 不通过' V17a
printf '| 日期 |\n|---|\n' >"$tmp/budget/verify.md"
expect "V17a 0 条记录不得通过" 0 '^V17a 跳过\(无数据：抽检记录 0 条\)' V17a
printf '| 日期 |\n|---|\n| 2026-10-24 10:00 | 30 |\n| 2026-10-24 22:00 | 31 |\n' >"$tmp/budget/verify.md"
expect "V17a 记录数" 0 '已有 2 条记录' V17a
touch -t 202001010000 "$tmp/budget/level.json"
expect "V17a level.json 过期" nz '^V17a 不通过.*过期' V17a
touch "$tmp/budget/level.json"
bad_py "$tmp/tools/budget_level/budget_level_selftest.py"
expect "V17a 自测失败" nz '^V17a 不通过' V17a
ok_py "$tmp/tools/budget_level/budget_level_selftest.py"

ok_py "$tmp/tools/stop_check/stop_check_selftest.py"
# summary counts; a skip next to a pass stays 0, a fail next to a pass is non-0
expect "跳过加通过" 0 '通过 1、跳过 1、不通过 0' "V05 V08"
expect "失败加通过" nz '通过 1、跳过 0、不通过 1' "V05 V17a" ACCEPT_BUDGET_DIR="$tmp/none"

[ "$bad" = 0 ] && echo "acceptance_v2_selftest：通过" || { echo "acceptance_v2_selftest：失败"; exit 1; }
