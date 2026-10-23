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

# V01: info lines and other rules do not count, an H8 violation does, an unreadable result fails closed
stub_budget "'信息 H8 a.md 示例\\nH23 docs/x.sh 运维脚本\\nbudget_check：违例 1 条，模式 warn'"
expect "V01 其它项与信息行不算" 0 '^V01 通过' V01
stub_budget "'H8 a.md:3 字面值\\nbudget_check：违例 1 条，模式 warn'"
expect "V01 H8 违例" nz '^V01 不通过' V01
stub_budget "'没有汇总行'"
expect "V01 结果不可解析" nz '^V01 不通过' V01

# V02: no gates directory is a skip; a failing gate self-test is a fail
stub_budget "'budget_check：违例 0 条，模式 warn'"
expect "V02 无 gates 跳过" 0 '^V02 跳过\(scripts/gates 尚未合入\)' V02
mkdir -p "$tmp/root/scripts/gates"
expect "V02 无自测" nz '^V02 不通过' V02
ok_py "$tmp/root/scripts/gates/a_selftest.py"
expect "V02 自测通过" 0 '^V02 通过' V02
bad_py "$tmp/root/scripts/gates/b_selftest.py"
expect "V02 自测失败" nz '^V02 不通过' V02
rm -f "$tmp/root/scripts/gates/b_selftest.py"
stub_budget "'H1 scripts/gates 太多\\nbudget_check：违例 1 条，模式 warn'"
expect "V02 H1 违例" nz '^V02 不通过' V02

# V05: library missing is a skip, a failing self-test is a fail
expect "V05 缺库跳过" 0 '^V05 跳过' V05
ok_py "$tmp/tools/handoff/handoff.py"; ok_py "$tmp/tools/stop_check/stop_check_selftest.py"
expect "V05 通过" 0 '^V05 通过' V05
bad_py "$tmp/tools/stop_check/stop_check_selftest.py"
expect "V05 失败" nz '^V05 不通过' V05

# V07
bad_py "$tmp/root/scripts/merge/mq.py"
expect "V07 失败" nz '^V07 不通过' V07
ok_py "$tmp/root/scripts/merge/mq.py"
expect "V07 通过" 0 '^V07 通过' V07

# V08: no counterpart is a skip, never a pass
expect "V08 无对应物跳过" 0 '^V08 跳过' V08
mkdir -p "$tmp/root/.github/workflows"; echo 'tier=docs' > "$tmp/root/scripts/ci/relevant_change.py"; echo 'relevant_change.py' > "$tmp/root/.github/workflows/ci.yml"
expect "V08 静态对应物齐全也只跳过（无数据）" 0 '^V08 跳过.*无数据' V08

# V17a: record count is reported; a missing verify.md fails
ok_py "$tmp/tools/budget_level/budget_level_selftest.py"
expect "V17a 缺 verify.md" nz '^V17a 不通过' V17a
printf '| 日期 |\n|---|\n| 2026-10-24 10:00 | 30 |\n| 2026-10-24 22:00 | 31 |\n' >"$tmp/budget/verify.md"
expect "V17a 记录数" 0 '已有 2 条记录' V17a

# a skip next to a pass stays 0, a fail next to a pass is non-0
ok_py "$tmp/tools/stop_check/stop_check_selftest.py"
expect "跳过加通过" 0 '全部通过或跳过' "V05 V08"
expect "失败加通过" nz '1 项不通过' "V05 V17a" ACCEPT_BUDGET_DIR="$tmp/none"

[ "$bad" = 0 ] && echo "acceptance_v2_selftest：通过" || { echo "acceptance_v2_selftest：失败"; exit 1; }
