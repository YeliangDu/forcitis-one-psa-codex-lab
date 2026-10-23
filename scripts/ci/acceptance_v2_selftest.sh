#!/usr/bin/env bash
# Self-test of acceptance_v2.sh: stub checkout and tool library, so it proves skip, pass and fail stay apart (a skip exits 0, a fail exits non-0).
set -uo pipefail
here=$(cd "$(dirname "$0")" && pwd)
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
bad=0
mkdir -p "$tmp/root/scripts/ci" "$tmp/root/scripts/merge" "$tmp/tools/handoff" "$tmp/tools/stop_check" "$tmp/tools/budget_level" "$tmp/budget"
RULES="$tmp/root/docs/modernization/RULES.md" ROLE="$tmp/root/docs/modernization/roles/a.md" POLICY="$tmp/root/policy.json"

# expect <name> <want exit: 0|nz> <output regex> <items> [env...]
expect() {
  local name=$1 want=$2 re=$3 items=$4; shift 4
  local out rc
  out=$(env ACCEPT_ROOT="$tmp/root" ONE_SESSION_TOOLS="$tmp/tools" ACCEPT_BUDGET_DIR="$tmp/budget" ACCEPT_ONLY="$items" ACCEPT_SELFTEST=1 "$@" bash "$here/acceptance_v2.sh" 2>&1); rc=$?
  if { [ "$want" = 0 ] && [ "$rc" = 0 ]; } || { [ "$want" = nz ] && [ "$rc" != 0 ]; }; then :; else echo "失败 $name：退出码 $rc"; bad=1; fi
  printf '%s\n' "$out" | grep -Eq "$re" || { echo "失败 $name：输出不含 /$re/"; printf '%s\n' "$out" | sed 's/^/    /'; bad=1; }
}
stub_budget() { printf 'print(%s)\n' "$1" >"$tmp/root/scripts/ci/budget_check.py"; }
ok_py() { printf 'print("%s")\n' "${2:-ok}" >"$1"; }   # a self-test that passes and prints its pass line
bad_py() { printf 'import sys\nsys.exit(1)\n' >"$1"; }
SUMMARY0="budget_check：违例 0 条，模式 warn"

# V01: info lines and other rules do not count, an H8 violation does, an unreadable result fails closed;
# RULES.md or roles missing or empty is never a pass (skip, or a failure under ACCEPT_STRICT=1)
stub_budget "'信息 H7 docs/modernization/RULES.md 尚无\\nH23 docs/x.sh 运维脚本\\nbudget_check：违例 1 条，模式 warn'"
expect "V01 缺 RULES 与 roles 不得通过" 0 '^V01 跳过\(.*RULES.md' V01
expect "V01 缺文件严格模式失败" nz '^V01 不通过' V01 ACCEPT_STRICT=1
mkdir -p "$tmp/root/docs/modernization/roles"; : >"$RULES"
expect "V01 RULES.md 0 字节不得通过" 0 '^V01 跳过\(.*RULES.md' V01
printf '## 规则一\n执行：x\n' >"$RULES"
expect "V01 只缺 roles 不得通过" 0 '^V01 跳过\(.*roles' V01
: >"$ROLE"
expect "V01 角色文件 0 字节不得通过" 0 '^V01 跳过\(.*roles' V01
echo "独占路径：无" >"$ROLE"
expect "V01 其它项与信息行不算" 0 '^V01 通过' V01
stub_budget "'H8 a.md:3 字面值\\nbudget_check：违例 1 条，模式 warn'"
expect "V01 H8 违例" nz '^V01 不通过' V01
stub_budget "'没有汇总行'"
expect "V01 结果不可解析" nz '^V01 不通过' V01
stub_budget "'budget_check.py: error: unrecognized arguments'"
expect "V01 argparse 报错不算汇总" nz '^V01 不通过' V01
stub_budget "'POLICY policy.json 无法读取\\nbudget_check：违例 1 条'"
expect "V01 POLICY 行" nz '^V01 不通过' V01
stub_budget "'H8 policy.json 检查无法完成（缺键）\\nbudget_check：违例 1 条'"
expect "V01 检查中断" nz '^V01 不通过.*中断' V01
stub_budget "'H23 policy.json 检查无法完成（缺键）\\n$SUMMARY0'"
expect "V01 不依赖 H23，H23 中断不影响" 0 '^V01 通过' V01

# V03: only role-file H7 and H17 count; role files missing is not a pass; an interrupted H7 or H17 check fails
stub_budget "'H7 docs/modernization/RULES.md 超\\nbudget_check：违例 1 条'"
expect "V03 规则文件的 H7 不算" 0 '^V03 通过' V03
stub_budget "'H7 docs/modernization/roles/a.md 4 KB\\nbudget_check：违例 1 条'"
expect "V03 角色 H7" nz '^V03 不通过' V03
stub_budget "'H17 docs/modernization/roles/a.md 交叠\\nbudget_check：违例 1 条'"
expect "V03 H17" nz '^V03 不通过' V03
stub_budget "'H7 policy.json 检查无法完成（缺键 role_file_max_kb）\\nbudget_check：违例 1 条'"
expect "V03 H7 检查中断" nz '^V03 不通过.*中断' V03
stub_budget "'H17 policy.json 检查无法完成\\nbudget_check：违例 1 条'"
expect "V03 H17 检查中断" nz '^V03 不通过.*中断' V03
stub_budget "'$SUMMARY0'"
mv "$ROLE" "$ROLE.off"
expect "V03 无角色文件不得通过" 0 '^V03 跳过' V03
expect "V03 无角色文件严格模式失败" nz '^V03 不通过' V03 ACCEPT_STRICT=1
mv "$ROLE.off" "$ROLE"

# V02: neither gates nor hooks is a skip; H1 on either directory is a fail; a failing or silent self-test is a fail; p99/version always a skip
expect "V02 无 gates 与 hooks 跳过" 0 '^V02 跳过\(scripts/gates、scripts/hooks 都不存在\)' V02
mkdir -p "$tmp/root/scripts/hooks"
expect "V02 只有 hooks 也要查：无自测" nz '^V02 不通过.*没有自测' V02
ok_py "$tmp/root/scripts/hooks/a_selftest.py"
expect "V02 hooks 自测通过" 0 '^V02 通过' V02
expect "V02 p99 与版本单列跳过" 0 'V02\(p99、版本、17 条用例\) 跳过\(无看板数据' V02
: >"$tmp/root/scripts/hooks/a_selftest.py"
expect "V02 0 字节自测不得通过" nz '^V02 不通过' V02
bad_py "$tmp/root/scripts/hooks/a_selftest.py"
expect "V02 自测失败" nz '^V02 不通过' V02
ok_py "$tmp/root/scripts/hooks/a_selftest.py"
stub_budget "'H1 scripts/gates+hooks 运行代码 9999 行超过 gates.runtime_max_lines\\nbudget_check：违例 1 条，模式 warn'"
expect "V02 H1 违例（含 hooks 合计）" nz '^V02 不通过：H1 违例' V02
stub_budget "'H23 policy.json 检查无法完成（缺键 max_lines_total）\\nbudget_check：违例 1 条'"
expect "V02 行数检查中断" nz '^V02 不通过.*中断' V02
stub_budget "'$SUMMARY0'"

# V05: library missing (or only one file) is a skip, a failing, silent or empty self-test is a fail
expect "V05 缺库跳过" 0 '^V05 跳过' V05
expect "V05 缺库严格模式失败" nz '^V05 不通过' V05 ACCEPT_STRICT=1
ok_py "$tmp/tools/handoff/handoff.py" "handoff self-test: ok"
expect "V05 只缺一个跳过" 0 '^V05 跳过\(.*stop_check_selftest' V05
rm "$tmp/tools/handoff/handoff.py"; ok_py "$tmp/tools/stop_check/stop_check_selftest.py" "stop_check self-test: ok"
expect "V05 只缺另一个跳过" 0 '^V05 跳过\(.*handoff.py' V05
ok_py "$tmp/tools/handoff/handoff.py" "handoff self-test: ok"
expect "V05 通过" 0 '^V05 通过' V05
: >"$tmp/tools/handoff/handoff.py"
expect "V05 0 字节自测不得通过" nz '^V05 不通过' V05
ok_py "$tmp/tools/handoff/handoff.py" "handoff self-test: ok"
bad_py "$tmp/tools/stop_check/stop_check_selftest.py"
expect "V05 失败" nz '^V05 不通过' V05
ok_py "$tmp/tools/stop_check/stop_check_selftest.py" "stop_check self-test: ok"

# V07
bad_py "$tmp/root/scripts/merge/mq.py"
expect "V07 失败" nz '^V07 不通过' V07
: >"$tmp/root/scripts/merge/mq.py"
expect "V07 0 字节不得通过" nz '^V07 不通过' V07
ok_py "$tmp/root/scripts/merge/mq.py" "mq self-test: ok"
expect "V07 通过" 0 '^V07 通过' V07

# V08: no data source yet, always a skip - also when the static pieces that once passed it exist (relevant_change tier=docs, ci.yml using it)
mkdir -p "$tmp/root/.github/workflows"
echo 'out("tier=docs")' >"$tmp/root/scripts/ci/relevant_change.py"; echo 'run: python3 scripts/ci/relevant_change.py' >"$tmp/root/.github/workflows/ci.yml"
expect "V08 静态对应物齐全也只跳过" 0 '^V08 跳过\(无数据' V08

# V12: needs the budget keys (fail), non-empty ladder levels and the budget_level self-test
echo '{"budget": {"line": {"from_pct": 0}, "ladder": {"L0": {"a": 1}, "L1": {"a": 1}, "L2": {"a": 1}, "L3": {"a": 1}}}}' >"$POLICY"
expect "V12 缺工具库跳过" 0 '^V12 跳过' V12
ok_py "$tmp/tools/budget_level/budget_level_selftest.py" "绿：0 项失败"
expect "V12 通过" 0 '^V12 通过' V12
: >"$tmp/tools/budget_level/budget_level_selftest.py"
expect "V12 自测 0 字节不得通过" nz '^V12 不通过' V12
bad_py "$tmp/tools/budget_level/budget_level_selftest.py"
expect "V12 自测失败" nz '^V12 不通过' V12
ok_py "$tmp/tools/budget_level/budget_level_selftest.py" "绿：0 项失败"
echo '{"budget": {"line": {"from_pct": 0}, "ladder": {"L0": {"a": 1}, "L1": {"a": 1}}}}' >"$POLICY"
expect "V12 缺梯子档" nz '^V12 不通过：policy.json 缺 budget.ladder.L2' V12
echo '{"budget": {"line": {"from_pct": 0}, "ladder": {"L0": null, "L1": {}, "L2": {"a": 1}, "L3": {"a": 1}}}}' >"$POLICY"
expect "V12 档位为 null 或空对象" nz '^V12 不通过：policy.json 缺 budget.ladder.L0、budget.ladder.L1' V12
echo '{"x": 1}' >"$POLICY"
expect "V12 缺 budget" nz '^V12 不通过' V12

# V17a: level.json missing, stale, future, empty, garbage, a directory or without a reading fails; verify.md unreadable or with a broken row fails;
# no records, under a week or no deviation threshold is a skip; only a judged week passes
pol() { printf '{"budget": {"level_interval_minutes": %s, "reading_validation_days": 7, "reading_max_deviation_pts": %s}}' "$1" "$2" >"$POLICY"; }
good_level() { rm -rf "$tmp/budget/level.json"; echo '{"status": "正常", "level": "L0"}' >"$tmp/budget/level.json"; }
rows() { printf '| 日期 |\n|---|\n%s' "$1" >"$tmp/budget/verify.md"; }
WEEK=$'| 2026-10-01 10:00 | 30 | 31 | 1 | 5 | 0 | |\n| 2026-10-09 10:00 | 40 | 41 | -1.5 | 5 | 0 | |\n'
pol 30 2
expect "V17a 缺 level.json" nz '^V17a 不通过.*level.json 不存在' V17a
good_level; rows ""
expect "V17a 0 条记录不得通过" 0 '^V17a 跳过\(无数据：抽检记录 0 条\)' V17a
rows $'| 2026-10-01 10:00 | 30 | 31 | 1 | 5 | 0 | |\n| 2026-10-02 10:00 | 30 | 31 | 1 | 5 | 0 | |\n'
expect "V17a 未满一周不得通过" 0 '^V17a 跳过\(.*未满 7' V17a
rows "$WEEK"
expect "V17a 满一周偏差在内才通过" 0 '^V17a 通过.*2 条记录跨 8.0 天' V17a
pol 30 1
expect "V17a 偏差超限" nz '^V17a 不通过.*偏差超过' V17a
pol 30 null
expect "V17a 无阈值判不了不得通过" 0 '^V17a 跳过\(判不了' V17a
pol 30 2
rows $'| 2026-  |  |  |\n'
expect "V17a 模板残行" nz '^V17a 不通过.*格式不对' V17a
rows "$WEEK"; chmod 000 "$tmp/budget/verify.md"
expect "V17a verify.md 不可读" nz '^V17a 不通过' V17a
chmod 644 "$tmp/budget/verify.md"
touch -t 202001010000 "$tmp/budget/level.json"
expect "V17a level.json 过期" nz '^V17a 不通过.*过期' V17a
pol null 2
expect "V17a 间隔为 null（且已过期）" nz '^V17a 不通过.*正数' V17a
pol '"30"' 2
expect "V17a 间隔为字符串" nz '^V17a 不通过.*正数' V17a
pol 30 2; good_level; touch -t 203001010000 "$tmp/budget/level.json"
expect "V17a level.json 修改时间在未来" nz '^V17a 不通过.*未来' V17a
good_level; : >"$tmp/budget/level.json"
expect "V17a level.json 空文件" nz '^V17a 不通过' V17a
echo garbage >"$tmp/budget/level.json"
expect "V17a level.json 乱码" nz '^V17a 不通过' V17a
rm "$tmp/budget/level.json"; mkdir "$tmp/budget/level.json"
expect "V17a level.json 是目录" nz '^V17a 不通过' V17a
rm -rf "$tmp/budget/level.json"; echo '{"status": "无读数", "level": null}' >"$tmp/budget/level.json"
expect "V17a level.json 无读数" nz '^V17a 不通过.*正常读数' V17a
good_level; rm "$tmp/budget/verify.md"
expect "V17a 缺 verify.md" nz '^V17a 不通过' V17a
rows "$WEEK"; bad_py "$tmp/tools/budget_level/budget_level_selftest.py"
expect "V17a 自测失败" nz '^V17a 不通过' V17a
: >"$tmp/tools/budget_level/budget_level_selftest.py"
expect "V17a 自测 0 字节" nz '^V17a 不通过' V17a
ok_py "$tmp/tools/budget_level/budget_level_selftest.py" "绿：0 项失败"

# modes: blank item list or strict with a subset never exits 0; ACCEPT_STRICT only takes 0 or 1; summary counts
expect "ACCEPT_ONLY 空白一项不跑" nz '一项都没有跑' " "
expect "ACCEPT_ONLY 空串" nz '一项都没有跑' ""
expect "严格模式缩小范围" nz '严格模式必须跑全部' "V08" ACCEPT_STRICT=1 ACCEPT_SELFTEST=0
expect "ACCEPT_STRICT=true" nz 'ACCEPT_STRICT 只认' "V08" ACCEPT_STRICT=true
expect "ACCEPT_STRICT=空格1" nz 'ACCEPT_STRICT 只认' "V08" "ACCEPT_STRICT= 1"
expect "未知项" nz '^V99 不通过' "V99"
expect "跳过加通过" 0 '通过 1、跳过 1、不通过 0' "V05 V08"
mv "$tmp/budget/verify.md" "$tmp/budget/v.off"
expect "失败加通过" nz '通过 1、跳过 0、不通过 1' "V05 V17a"

[ "$bad" = 0 ] && echo "acceptance_v2_selftest：通过" || { echo "acceptance_v2_selftest：失败"; exit 1; }
