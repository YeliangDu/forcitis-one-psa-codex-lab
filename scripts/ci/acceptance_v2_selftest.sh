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
  out=$(env ACCEPT_ROOT="$tmp/root" ONE_SESSION_TOOLS="$tmp/tools" ACCEPT_BUDGET_DIR="$tmp/budget" ACCEPT_BOARD="$tmp/board/board.md" ACCEPT_PSA_DIR="$tmp/psa" ACCEPT_JANITOR_DIR="$tmp/janitor" ACCEPT_MEMORY_DIR="$tmp/memory" ACCEPT_ONLY="$items" "$@" bash "$here/acceptance_v2.sh" 2>&1); rc=$?
  if { [ "$want" = 0 ] && [ "$rc" = 0 ]; } || { [ "$want" = nz ] && [ "$rc" != 0 ]; }; then :; else echo "失败 ${name}：退出码 $rc"; bad=1; fi
  printf '%s\n' "$out" | grep -Eq "$re" || { echo "失败 ${name}：输出不含 /$re/"; printf '%s\n' "$out" | sed 's/^/    /'; bad=1; }
}
stub_budget() { printf 'print(%s)\n' "$1" >"$tmp/root/scripts/ci/budget_check.py"; }
ok_py() { printf 'print("%s")\n' "${2:-ok}" >"$1"; }   # a self-test that passes and prints its pass line
bad_py() { printf 'import sys\nsys.exit(1)\n' >"$1"; }
ALL="V01 V02 V03 V04 V05 V06 V07 V08 V10 V11 V12 V13 V14 V14b V15 V16 V17a"
SUMMARY0="budget_check：违例 0 条，模式 warn"

# V01: info lines and other rules do not count, an H8 violation does, an unreadable result fails closed;
# RULES.md or roles missing or empty is never a pass (skip, or a failure under ACCEPT_STRICT=1)
stub_budget "'信息 H7 docs/modernization/RULES.md 尚无\\nH23 docs/x.sh 运维脚本\\nbudget_check：违例 1 条，模式 warn'"
expect "V01 缺 RULES 与 roles 不得通过" 0 '^V01 跳过\(.*RULES.md' V01
# 变量紧跟全角字符：macOS bash 3.2 在 C.UTF-8 下会读错变量名，RULES 缺失时 V01 曾打印「通过」；显式用 /bin/bash 加 LC_ALL=C.UTF-8 再跑一遍
out=$(env ACCEPT_ROOT="$tmp/root" ONE_SESSION_TOOLS="$tmp/tools" ACCEPT_BUDGET_DIR="$tmp/budget" ACCEPT_ONLY=V01 LC_ALL=C.UTF-8 /bin/bash "$here/acceptance_v2.sh" 2>&1)
printf '%s\n' "$out" | grep -Eq '^V01 跳过\(.*RULES.md' || { echo "失败 V01 缺 RULES 在 /bin/bash＋C.UTF-8 下"; printf '%s\n' "$out" | sed 's/^/    /'; bad=1; }
expect "V01 缺文件严格模式失败" nz '^V01 不通过' "$ALL" ACCEPT_STRICT=1
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
expect "V03 无角色文件严格模式失败" nz '^V03 不通过' "$ALL" ACCEPT_STRICT=1
mv "$ROLE.off" "$ROLE"

# V02: neither gates nor hooks is a skip; H1 on either directory is a fail; a failing or silent self-test is a fail; p99/version always a skip
expect "V02 无 gates 与 hooks 跳过" 0 '^V02 跳过\(scripts/gates、scripts/hooks 都不存在\)' V02
mkdir -p "$tmp/root/scripts/hooks"
expect "V02 只有 hooks 也要查：无自测" nz '^V02 不通过.*没有自测' V02
ok_py "$tmp/root/scripts/hooks/a_selftest.py"
expect "V02 hooks 自测通过" 0 '^V02 通过' V02
printf 'import sys\nif sys.argv[1:] != ["--self-test"]:\n    sys.exit("usage: b_selftest.py --self-test")\nprint("ok")\n' >"$tmp/root/scripts/hooks/b_selftest.py"
expect "V02 自测要求 --self-test 参数：带参数跑才通过" 0 '^V02 通过' V02
printf 'import sys\nprint("ok")\nsys.exit(0 if sys.argv[1:] == [] else 1)\n' >"$tmp/root/scripts/hooks/b_selftest.py"
expect "V02 自测不收参数：不带参数跑" 0 '^V02 通过' V02
printf '"""doc mentions --self-test only in prose"""\nimport sys\nif sys.argv[1:]:\n    sys.exit("usage: only segments")\nprint("ok")\n' >"$tmp/root/scripts/hooks/b_selftest.py"
expect "V02 --self-test 只出现在文档字符串里：不带参数跑（gates_selftest 的写法）" 0 '^V02 通过' V02
rm "$tmp/root/scripts/hooks/b_selftest.py"
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
expect "V05 缺库严格模式失败" nz '^V05 不通过' "$ALL" ACCEPT_STRICT=1
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

# V12: needs the budget keys (fail), non-empty ladder levels, the budget_level self-test, the session config list sessions.config (one entry per session profile) and the start_sheet self-test.
# (Rewritten for #39: the old fixture had no sessions.config and the old pass line did not need the start_sheet tool; the old cases for the ladder keys below are unchanged.)
LADDER='"budget": {"line": {"from_pct": 0}, "ladder": {"L0": {"a": 1}, "L1": {"a": 1}, "L2": {"a": 1}, "L3": {"a": 1}}}'
echo "{$LADDER, \"sessions\": {\"config\": {\"s1\": {\"role\": \"dev\"}}}}" >"$POLICY"
expect "V12 缺工具库跳过" 0 '^V12 跳过' V12
ok_py "$tmp/tools/budget_level/budget_level_selftest.py" "绿：0 项失败"
expect "V12 缺开工单工具跳过" 0 '^V12 跳过\(工具库缺 .*start_sheet.py' V12
mkdir -p "$tmp/tools/start_sheet"
cat >"$tmp/tools/start_sheet/start_sheet.py" <<'PYEOF'
import os, sys
a, mode = sys.argv[1:], os.environ.get("SS_MODE", "ok")
if mode == "fail":
    print("开工单超限：x")
    sys.exit(1)
if mode == "silent":
    sys.exit(0)
if a == ["--self-test"]:
    print("start_sheet self-test: ok")
elif a == ["--check", "--all"] and os.path.basename(os.getcwd()) == "root":
    print("start_sheet: %s 份画像的开工单都在上限内（p99 0.01 秒）" % ("0" if mode == "zero" else "10"))
else:
    sys.exit(2)
PYEOF
expect "V12 通过" 0 '^V12 通过.*会话配置清单（1 个会话）' V12
expect "V12 开工单自测失败" nz '^V12 不通过：start_sheet --self-test' V12 SS_MODE=fail
expect "V12 开工单自测无通过行" nz '^V12 不通过：start_sheet --self-test' V12 SS_MODE=silent
: >"$tmp/tools/budget_level/budget_level_selftest.py"
expect "V12 自测 0 字节不得通过" nz '^V12 不通过' V12
bad_py "$tmp/tools/budget_level/budget_level_selftest.py"
expect "V12 自测失败" nz '^V12 不通过' V12
ok_py "$tmp/tools/budget_level/budget_level_selftest.py" "绿：0 项失败"
mkdir -p "$tmp/root/docs/modernization/sessions"; : >"$tmp/root/docs/modernization/sessions/a.md"; : >"$tmp/root/docs/modernization/sessions/_retired.md"
expect "V12 会话画像 a 没有配置条目" nz '^V12 不通过：sessions.config 缺会话画像对应的条目：a$' V12
echo "{$LADDER, \"sessions\": {\"config\": {\"a\": {\"role\": \"dev\"}}}}" >"$POLICY"
expect "V12 每个画像都有条目通过（_ 开头的不算会话）" 0 '^V12 通过' V12
rm -r "$tmp/root/docs/modernization/sessions"
for cfg in '{}' 'null' '"x"' '{"s1": {}}' '{"s1": "dev"}'; do
  echo "{$LADDER, \"sessions\": {\"config\": $cfg}}" >"$POLICY"
  expect "V12 sessions.config 为 $cfg 不通过" nz '^V12 不通过：sessions.config' V12
done
echo "{$LADDER}" >"$POLICY"
expect "V12 没有 sessions 段不通过" nz '^V12 不通过：sessions.config 为空' V12
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
pol 30 2; rows "$WEEK"; good_level; touch "$tmp/budget/level.json"
pol 'NaN' 2
expect "V17a 间隔为 NaN（且已过期）" nz '^V17a 不通过.*正数' V17a
pol 30 NaN
expect "V17a 偏差阈值为 NaN" nz '^V17a 不通过.*有限数' V17a
pol 30 2; rows $'| 2026-10-01 10:00 | 30 | 31 | nan | 5 | 0 | |\n| 2026-10-09 10:00 | 40 | 41 | -1.5 | 5 | 0 | |\n'
expect "V17a verify.md 偏差列为 nan" nz '^V17a 不通过.*格式不对' V17a
rows "$WEEK"

# V10, V11, V14 read git: not a repository is a skip; V10 fails while a generated file is tracked, passes once none is
R="$tmp/root"
expect "V10 非 git 仓库跳过" 0 '^V10 跳过\(检出不是 git' V10
expect "V10 非 git 严格模式失败" nz '^V10 不通过' "$ALL" ACCEPT_STRICT=1
git -C "$R" init -q
mkdir -p "$R/frontend/packages/api/src/generated"
echo '{}' >"$R/frontend/packages/api/openapi.json"; echo 'x' >"$R/frontend/packages/api/src/generated/schema.ts"
git -C "$R" add -f frontend/packages/api
expect "V10 生成物被跟踪" nz '^V10 不通过：生成物仍被跟踪 2 个' V10
git -C "$R" rm -q --cached -r frontend/packages/api
expect "V10 不再跟踪" 0 '^V10 通过' V10
git -C "$R" add -f frontend/packages/api/src/generated/schema.ts
expect "V10 只剩 generated 被跟踪也不通过" nz '^V10 不通过：生成物仍被跟踪 1 个' V10
git -C "$R" rm -q --cached -r frontend/packages/api

# V11: a tracked mailbox.py or session_daily.py fails; no board file is a skip; the first-line stamp must be under 20 minutes old and parse
stamp() { date -u -v"$1" +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -d "$2" +%Y-%m-%dT%H:%M:%SZ; }
mkdir -p "$R/scripts/coord" "$R/scripts/ops" "$tmp/board"
echo x >"$R/scripts/coord/mailbox.py"; git -C "$R" add -f scripts/coord/mailbox.py
expect "V11 mailbox.py 仍被跟踪" nz '^V11 不通过：应已删除.*mailbox.py' V11
git -C "$R" rm -q --cached scripts/coord/mailbox.py
echo x >"$R/scripts/ops/session_daily.py"; git -C "$R" add -f scripts/ops/session_daily.py
expect "V11 session_daily.py 仍被跟踪" nz '^V11 不通过：应已删除.*session_daily.py' V11
git -C "$R" rm -q --cached scripts/ops/session_daily.py
expect "V11 本机 psa 目录不存在跳过" 0 '^V11 跳过\(本机目录 .* 不存在' V11
expect "V11 本机 psa 目录不存在严格模式失败" nz '^V11 不通过' "$ALL" ACCEPT_STRICT=1
mkdir -p "$tmp/psa/mailbox"
expect "V11 本机 mailbox 目录仍在不通过" nz '^V11 不通过：本机 .* 下应已删除的目录仍在：mailbox' V11
rmdir "$tmp/psa/mailbox"; mkdir "$tmp/psa/outbox"
expect "V11 本机 outbox 目录仍在不通过" nz '^V11 不通过：本机 .* 下应已删除的目录仍在：outbox' V11
[ -d "$tmp/psa/outbox" ] || { echo "失败 V11 不得删除本机目录"; bad=1; }
rmdir "$tmp/psa/outbox"
expect "V11 无看板文件跳过" 0 '^V11 跳过\(看板 .* 不存在' V11
expect "V11 无看板严格模式失败" nz '^V11 不通过' "$ALL" ACCEPT_STRICT=1
printf '生成时间：%s\n\n# 看板\n' "$(stamp -5M '5 minutes ago')" >"$tmp/board/board.md"
expect "V11 看板 5 分钟前通过" 0 '^V11 通过' V11
printf '生成时间：%s\n' "$(stamp -30M '30 minutes ago')" >"$tmp/board/board.md"
expect "V11 看板 30 分钟前不通过" nz '^V11 不通过：看板时间戳已过' V11
printf '生成时间：%s\n' "$(stamp +2H '2 hours')" >"$tmp/board/board.md"
expect "V11 看板时间在未来不通过" nz '^V11 不通过：看板时间在未来' V11
printf '# 看板\n生成时间：2026-10-26T00:00:00Z\n' >"$tmp/board/board.md"
expect "V11 第一行不是时间戳不通过" nz '^V11 不通过：看板第一行' V11
: >"$tmp/board/board.md"
expect "V11 看板空文件不通过" nz '^V11 不通过' V11

# V13: no janitor or memory directory is a skip; an empty janitor directory or an oversized memory fails; both fine passes. Memory limit is policy.json memory_max_kb
echo '{"memory_max_kb": 2}' >"$POLICY"
expect "V13 两个目录都没有跳过" 0 '^V13 跳过\(.*回收器目录.*记忆目录' V13
expect "V13 缺目录严格模式失败" nz '^V13 不通过' "$ALL" ACCEPT_STRICT=1
mkdir -p "$tmp/janitor" "$tmp/memory"
expect "V13 回收器目录无报告不通过" nz '^V13 不通过：回收器目录 .*没有非空的 report' V13
: >"$tmp/janitor/report-2026-10-26.md"
expect "V13 报告 0 字节不通过" nz '^V13 不通过' V13
echo x >"$tmp/janitor/report-2026-10-26.md"
touch -t 202501010000 "$tmp/janitor/report-2026-10-26.md"
expect "V13 只有陈旧报告不通过" nz '^V13 不通过：回收器报告都已陈旧' V13
touch "$tmp/janitor/report-2026-10-26.md"
printf 'a%.0s' $(seq 1 1500) >"$tmp/memory/a.md"
expect "V13 记忆 1500 字节通过" 0 '^V13 通过.*回收器报告 1 份，2 天内有新报告；记忆合计 1500 字节' V13
printf 'b%.0s' $(seq 1 600) >"$tmp/memory/b.md"
expect "V13 记忆合计 2100 字节不通过" nz '^V13 不通过：记忆合计 2100 字节，上限 2 KB' V13
rm "$tmp/memory/b.md"; mkdir "$tmp/memory/sub"; printf 'c%.0s' $(seq 1 600) >"$tmp/memory/sub/c.md"
expect "V13 子目录也计入" nz '^V13 不通过：记忆合计 2100 字节' V13
rm -r "$tmp/memory/sub"
echo '{"x": 1}' >"$POLICY"
expect "V13 policy 缺 memory_max_kb 不通过" nz '^V13 不通过：读记忆目录或 policy.json 出错' V13
echo '{"memory_max_kb": 2}' >"$POLICY"
rm -r "$tmp/memory"
expect "V13 只缺记忆目录跳过" 0 '^V13 跳过\(记忆目录' V13
mkdir "$tmp/memory"; rm "$tmp/janitor/report-2026-10-26.md"
expect "V13 记忆在而回收器目录空仍不通过" nz '^V13 不通过' V13

# V14: ARCHIVE_INDEX.md must be tracked and non-empty, parse to at least one registered path, and none of those paths may still be tracked (fail);
# the per-file section of the index counts too; every path must exist on the archive branch (missing: fail); an unknown archive branch is a skip, a known one passes
IDX=docs/modernization/ARCHIVE_INDEX.md
ID=$(dirname "$IDX")
index_rows() { printf '# 归档索引\n\n合计 %s 个文件。\n\n| 原目录 | 文件数 | 大小 KB | 文件 |\n|---|---:|---:|---|\n%s\n' "${2:-?}" "$1" >"$R/$IDX"; }
ROW=$(printf '| `%s/` | 2 | 1.0 | A.html、B.json |' "$ID")
expect "V14 无索引不通过" nz '^V14 不通过' V14
mkdir -p "$R/$ID"; : >"$R/$IDX"; git -C "$R" add "$IDX"
expect "V14 索引 0 字节不通过" nz '^V14 不通过' V14
echo '# 归档索引，没有表' >"$R/$IDX"
expect "V14 索引解析不出路径不通过" nz '^V14 不通过：索引里解析不出任何登记的文件路径' V14
index_rows '| `x/` | 1 | 1.0 | … |'
expect "V14 只有省略号行也解析不出路径" nz '^V14 不通过：索引里解析不出' V14
index_rows "$ROW" 2
expect "V14 有索引无归档分支跳过" 0 '^V14 跳过\(本检出查不到归档分支' V14
expect "V14 无分支严格模式失败" nz '^V14 不通过' "$ALL" ACCEPT_STRICT=1
for f in A.html B.json C.md D.md E.md F.md; do echo x >"$R/$ID/$f"; done
git -C "$R" add "$ID"
git -C "$R" -c user.name=t -c user.email=t@t -c commit.gpgsign=false commit -q -m x
git -C "$R" branch archive/2026-10
for f in A.html B.json C.md D.md E.md F.md; do git -C "$R" rm -q --cached "$ID/$f"; done
expect "V14 索引与归档分支齐全通过" 0 '^V14 通过.*登记的 2 个文件都已不在 main.*且都在归档分支' V14
index_rows "$ROW" 3
expect "V14 合计与解析条数对不上不通过" nz '^V14 不通过：索引写明的合计与解析出的 2 个文件对不上' V14
CUT=$(printf '| `%s/` | 9 | 1.0 | C.md、D.md、… |' "$ID")
index_rows "$ROW
$CUT" 4
expect "V14 有截断行却没有逐文件清单不通过" nz '^V14 不通过：索引写明的合计.*逐文件清单 0 条，截断行 1' V14
index_rows "$ROW
$CUT" 6
printf '\n## 逐文件\n\n- `%s/E.md`\n- `%s/F.md`\n' "$ID" "$ID" >>"$R/$IDX"
expect "V14 逐文件清单补全截断行并通过" 0 '^V14 通过.*登记的 6 个文件.*1 行表格文件列表被省略号截断' V14
git -C "$R" add "$ID/F.md"
expect "V14 逐文件清单里的文件仍被跟踪不通过" nz '^V14 不通过：索引登记的 1 个文件仍被跟踪.*F.md' V14
git -C "$R" rm -q --cached "$ID/F.md"
index_rows "$ROW
$CUT" 7
printf '\n## 逐文件\n\n- `%s/E.md`\n- `%s/F.md`\n- `%s/G.md`\n' "$ID" "$ID" "$ID" >>"$R/$IDX"
expect "V14 归档分支上缺一个文件不通过" nz '^V14 不通过：索引登记的 1 个文件在归档分支上不存在.*G.md' V14
index_rows "$ROW" 2
echo x >"$R/$ID/A.html"; git -C "$R" add "$ID/A.html"
expect "V14 索引登记的文件仍被跟踪不通过" nz '^V14 不通过：索引登记的 1 个文件仍被跟踪.*A.html' V14
git -C "$R" rm -q --cached "$ID/A.html"
index_rows "$CUT" 3; printf '\n## 逐文件\n\n- `%s/E.md`\n' "$ID" >>"$R/$IDX"
echo x >"$R/$ID/D.md"; git -C "$R" add "$ID/D.md"
expect "V14 省略号行所列文件仍被跟踪不通过" nz '^V14 不通过：索引登记的 1 个文件仍被跟踪.*D.md' V14
git -C "$R" rm -q --cached "$ID/D.md"
git -C "$R" branch -q -m archive/2026-10 other
expect "V14 分支名不符跳过" 0 '^V14 跳过' V14
git -C "$R" rm -q --cached "$IDX"
expect "V14 索引未跟踪不通过" nz '^V14 不通过' V14

# V04: the tool library's start_sheet --check --all must run from the checkout root, exit 0 and report at least one profile; missing tool or not a repository is a skip
mkdir -p "$tmp/nogit"
expect "V04 非 git 仓库跳过" 0 '^V04 跳过\(检出不是 git' V04 ACCEPT_ROOT="$tmp/nogit"
expect "V04 通过" 0 '^V04 通过.*10 份画像的开工单都在上限内' V04
expect "V04 开工单超限" nz '^V04 不通过' V04 SS_MODE=fail
expect "V04 0 份画像不得通过" nz '^V04 不通过' V04 SS_MODE=zero
expect "V04 无输出不得通过" nz '^V04 不通过' V04 SS_MODE=silent
expect "V04 缺工具库跳过" 0 '^V04 跳过\(工具库缺' V04 ONE_SESSION_TOOLS="$tmp/notools"
expect "V04 缺工具库严格模式失败" nz '^V04 不通过' "$ALL" ACCEPT_STRICT=1 ONE_SESSION_TOOLS="$tmp/notools"

# V06: no card files (tasks/, check_tasks.py, WORK_CARD template), an Issue template exists, the labels on cards (fake gh issue list) are only 会话:<name>, 挂起, hold
# or a value of policy.json "labels" (read from the file, not written into the check)
R3="$tmp/root3"; mkdir -p "$R3"; git -C "$R3" init -q
cat >"$tmp/gh" <<'GHEOF'
#!/usr/bin/env bash
[ "$1 $2" = "issue list" ] && [[ " $* " == *" --json labels "* ]] && [[ " $* " == *" --state all "* ]] || exit 9
[ -z "${GH_FAIL:-}" ] || exit 1
cat "$GH_DATA"
GHEOF
chmod +x "$tmp/gh"
labels() { printf '[{"number":1,"labels":[%s]}]' "$1" >"$tmp/cards.json"; }   # one card carrying the given labels
pol06() { printf '{"labels": {%s}}\n' "$1" >"$R3/policy.json"; }
G=(ACCEPT_ROOT="$R3" ACCEPT_GH="$tmp/gh" GH_DATA="$tmp/cards.json")
pol06 '"review_passed": "复审通过", "hold": "hold", "integration": "整合"'
mkdir -p "$R3/docs/modernization/tasks"; echo x >"$R3/docs/modernization/tasks/A.md"; git -C "$R3" add docs
labels '{"name":"会话:a"},{"name":"挂起"},{"name":"hold"}'
expect "V06 tasks/ 仍在仓库里不通过" nz '^V06 不通过：卡的文件还在仓库里 1 个.*tasks/A.md' V06 "${G[@]}"
git -C "$R3" rm -q -r --cached docs
expect "V06 没有 Issue 模板不通过" nz '^V06 不通过：没有 Issue 模板' V06 "${G[@]}"
mkdir -p "$R3/.github/ISSUE_TEMPLATE"; echo x >"$R3/.github/ISSUE_TEMPLATE/card.md"; git -C "$R3" add .github
expect "V06 文件齐、标签只有三类通过" 0 '^V06 通过.*1 张卡用到的 3 个标签都属三类' V06 "${G[@]}"
labels '{"name":"会话:a"},{"name":"复审通过"},{"name":"整合"}'
expect "V06 卡上有 policy labels 的复审通过、整合仍通过" 0 '^V06 通过.*1 张卡用到的 3 个标签' V06 "${G[@]}"
printf '[{"number":1,"labels":[{"name":"会话:a"}]},{"number":2,"labels":[{"name":"会话:b"},{"name":"整合"}]},{"number":3,"labels":[]}]' >"$tmp/cards.json"
expect "V06 多张卡、无标签的卡也照判通过" 0 '^V06 通过.*3 张卡用到的 3 个标签' V06 "${G[@]}"
labels '{"name":"会话:a"},{"name":"queued"}'
expect "V06 卡上有 queued 不通过" nz '^V06 不通过：卡上的标签有三类与 policy.json labels 之外的 1 个：queued' V06 "${G[@]}"
labels '{"name":"会话:a"},{"name":"复审通过"}'
pol06 '"review_passed": "已复审", "hold": "hold", "integration": "整合"'
expect "V06 允许集合读 policy：改名后旧名不通过" nz '^V06 不通过：卡上的标签有三类与 policy.json labels 之外的 1 个：复审通过' V06 "${G[@]}"
pol06 ''
expect "V06 policy 没有 labels 值不得通过" nz '^V06 不通过：policy.json 读不到或没有 labels 段' V06 "${G[@]}"
rm "$R3/policy.json"
expect "V06 policy 缺失不得通过" nz '^V06 不通过：policy.json 读不到或没有 labels 段' V06 "${G[@]}"
pol06 '"review_passed": "复审通过", "hold": "hold", "integration": "整合"'
python3 -I -c 'import json; print(json.dumps([{"number": i, "labels": [{"name": "会话:a"}]} for i in range(1000)]))' >"$tmp/cards.json"
expect "V06 卡的结果达到查询上限不得通过" nz '^V06 不通过：卡的查询结果有 1000 条，达到上限' V06 "${G[@]}"
labels '{"name":"会话:a"},{"name":"bug"},{"name":"hold"},{"name":"急"}'
expect "V06 有三类之外的标签不通过" nz '^V06 不通过：卡上的标签有三类与 policy.json labels 之外的 2 个：bug、急' V06 "${G[@]}"
labels '{"name":"挂起"},{"name":"hold"}'
expect "V06 没有会话标签不得通过" nz '^V06 不通过：卡上查不到任何「会话:' V06 "${G[@]}"
labels '{"name":"会话:a"},{"name":"Hold"}'
expect "V06 标签大小写不符不通过" nz '^V06 不通过：卡上的标签有三类与 policy.json labels 之外的 1 个：Hold' V06 "${G[@]}"
labels '{"name":"会话:"}'
expect "V06 会话: 后面没有名字不通过" nz '^V06 不通过' V06 "${G[@]}"
echo 'not json' >"$tmp/cards.json"
expect "V06 查询结果不是 JSON 不通过" nz '^V06 不通过：标签查询结果解析失败' V06 "${G[@]}"
labels '{"name":"会话:a"}'
expect "V06 gh 失败且文件都对跳过" 0 '^V06 跳过\(gh 查询标签失败' V06 "${G[@]}" GH_FAIL=1
expect "V06 gh 不存在跳过" 0 '^V06 跳过\(gh 查询标签失败' V06 "${G[@]}" ACCEPT_GH="$tmp/nogh"
expect "V06 gh 失败严格模式失败" nz '^V06 不通过' "$ALL" ACCEPT_STRICT=1 "${G[@]}" GH_FAIL=1
mkdir -p "$R3/docs/modernization"; echo x >"$R3/docs/modernization/check_tasks.py"; git -C "$R3" add docs
expect "V06 gh 失败而文件不对仍不通过" nz '^V06 不通过：卡的文件还在仓库里.*标签未核' V06 "${G[@]}" GH_FAIL=1
git -C "$R3" rm -q -r --cached docs
expect "V06 非 git 仓库跳过" 0 '^V06 跳过\(检出不是 git' V06 ACCEPT_ROOT="$tmp/nogit"

# V14b: E-class files out of main (not tracked), the index names the archive branch and registers each, each exists on the branch; the branch missing is a skip (strict: failure)
R2="$tmp/root2"; mkdir -p "$R2"; git -C "$R2" init -q
Dm=docs/modernization; Cl=$Dm/architecture/proposals/closeout
EP=($Dm/{describe_nodes.py,extract_topology.py,live_code.py,topology.json,map/descriptions.json,rules/data_objects.py,rules/merge_rules.py,rules/pull_result.py}
    $Dm/glossary/{migrate_to_concepts.py,test_checkers_on_concepts.py,test_migrate_to_concepts.py,arch-naming/reconcile-decided.json}
    $Dm/architecture-guardrails/backend/{coverage.csv,negative-test.log,src/test/java/com/forcitis/one/SecurityInvariantsTest.java}
    $Dm/architecture-guardrails/frontend/negative-lint.log $Dm/map/packets/fst_infrastructure_cost.txt $Dm/build_oa_inventory.py
    $Cl/{ARCH_VS14.md,BPMN_STYLE.md,CONVENTIONS.md,PROCESS_METHOD_RESEARCH.md,PROCESS_METHOD_REVIEW.md,PROCESS_QUALITY_CHECKLIST.md,RESPONSIBILITY_MATRIX_SPEC.md,bpmn_check.py,bpmn_gen.py,converge_check.py}
    $Cl/people/b31-rules/{B31_ALIGNMENT_AUDIT.md,B31_CONSTRUCTION_DESIGN.md}
    $Cl/bpmn_gen_fixtures/{activity_cancel,entry,exits,mini,timeout}.yaml)
[ "${#EP[@]}" = 35 ] || { echo "失败 V14b 自测的 E 类清单不是 35 个：${#EP[@]}"; bad=1; }
EP+=($Dm/build_records.py)   # deleted by b2b-plan 40, registered in the same index section, checked too: 36 in all
for p in "${EP[@]}"; do mkdir -p "$R2/$(dirname "$p")"; echo x >"$R2/$p"; done
git -C "$R2" add -f docs
git -C "$R2" -c user.name=t -c user.email=t@t -c commit.gpgsign=false commit -q -m x
OB=$(git -C "$R2" symbolic-ref --short HEAD)
B2=archive/2026-10-b2b
e_index() {  # a D-class section, then the batch two-B section: the branch line and every path except those matching $1 (a grep -v pattern)
  { echo '# 归档索引'; echo '## 逐文件'; echo '- `docs/modernization/old_d.md`'; echo '## 二乙批（E 类，GOV-1023-FV2）'; echo "二乙批归档分支 $B2"
    printf -- '- `%s`\n' "${EP[@]}" | grep -v -- "${1:-NOTHING-MATCHES}"; } >"$R2/$Dm/ARCHIVE_INDEX.md"
}
E=(ACCEPT_ROOT="$R2")
e_index
expect "V14b 二乙批文件仍被跟踪不通过" nz '^V14b 不通过：二乙批 36 个文件仍被跟踪' V14b "${E[@]}"
git -C "$R2" checkout -q -b "$B2"
git -C "$R2" rm -q --cached "$Dm/describe_nodes.py"
git -C "$R2" -c user.name=t -c user.email=t@t -c commit.gpgsign=false commit -q -m y
git -C "$R2" checkout -q -f "$OB"
git -C "$R2" rm -q -r --cached docs
rm "$R2/$Dm/ARCHIVE_INDEX.md"
expect "V14b 没有索引不通过" nz '^V14b 不通过：.*读不到' V14b "${E[@]}"
e_index; sed -i.bak "s#$B2#别处#" "$R2/$Dm/ARCHIVE_INDEX.md"; rm "$R2/$Dm/ARCHIVE_INDEX.md.bak"
expect "V14b 索引没写归档分支不通过" nz '^V14b 不通过：.*没有写到归档分支' V14b "${E[@]}"
{ echo '# 归档索引'; echo "文首提到 $B2"; echo '## 二乙批（E 类，GOV-1023-FV2）'; printf -- '- `%s`\n' "${EP[@]}"; } >"$R2/$Dm/ARCHIVE_INDEX.md"
expect "V14b 归档分支只写在二乙批一节之外不算" nz '^V14b 不通过：.*二乙批一节没有写到归档分支' V14b "${E[@]}"
e_index 'bpmn_gen.py'
expect "V14b 索引漏登记一个不通过" nz '^V14b 不通过：清单与索引二乙批不逐项相等：索引漏登记 1 个（.*bpmn_gen.py）；索引多登记 0 个' V14b "${E[@]}"
e_index 'B31_ALIGNMENT_AUDIT'
expect "V14b 索引漏登记 b31 一份不通过" nz '^V14b 不通过：清单与索引二乙批不逐项相等：索引漏登记 1 个（.*B31_ALIGNMENT_AUDIT.md）' V14b "${E[@]}"
e_index 'build_records'
expect "V14b 索引漏登记 build_records.py 不通过" nz '^V14b 不通过：清单与索引二乙批不逐项相等：索引漏登记 1 个（.*build_records.py）' V14b "${E[@]}"
e_index; echo '- `docs/modernization/extra.md`' >>"$R2/$Dm/ARCHIVE_INDEX.md"
expect "V14b 索引多登记一个不通过" nz '^V14b 不通过：清单与索引二乙批不逐项相等：索引漏登记 0 个；索引多登记 1 个（docs/modernization/extra.md）' V14b "${E[@]}"
e_index 'converge_check'; sed -i.bak "s#^- .docs/modernization/old_d.md.#- \`$Cl/converge_check.py\`#" "$R2/$Dm/ARCHIVE_INDEX.md"; rm "$R2/$Dm/ARCHIVE_INDEX.md.bak"
expect "V14b 只登记在二乙批一节之外不算" nz '^V14b 不通过：清单与索引二乙批不逐项相等：索引漏登记 1 个（.*converge_check.py）' V14b "${E[@]}"
printf '# 归档索引\n## 逐文件\n- `x`\n' >"$R2/$Dm/ARCHIVE_INDEX.md"
expect "V14b 索引没有二乙批一节不通过" nz '^V14b 不通过：.*没有「## 二乙批」一节' V14b "${E[@]}"
e_index
expect "V14b 归档分支上缺一个文件不通过" nz '^V14b 不通过：二乙批 1 个文件在归档分支上不存在.*describe_nodes.py' V14b "${E[@]}"
git -C "$R2" branch -q -D "$B2"; git -C "$R2" branch -q "$B2" "$OB"
expect "V14b 索引与归档分支齐全通过" 0 '^V14b 通过（二乙批 36 个文件（E 类 35、#40 删除 1）与索引逐项相等，都已不在 main，且都在归档分支' V14b "${E[@]}"
e_index 'bpmn_gen_fixtures/e'
printf '| `%s/bpmn_gen_fixtures/` | 2 | 1.0 | entry.yaml、exits.yaml |\n' "$Cl" >>"$R2/$Dm/ARCHIVE_INDEX.md"
expect "V14b 索引用表格行登记也认" 0 '^V14b 通过' V14b "${E[@]}"
git -C "$R2" add -f "$Cl/people/b31-rules/B31_CONSTRUCTION_DESIGN.md"
expect "V14b b31 一份仍在 main 不通过" nz '^V14b 不通过：二乙批 1 个文件仍被跟踪.*B31_CONSTRUCTION_DESIGN.md' V14b "${E[@]}"
git -C "$R2" rm -q --cached "$Cl/people/b31-rules/B31_CONSTRUCTION_DESIGN.md"
git -C "$R2" add -f "$Dm/build_records.py"
expect "V14b build_records.py 仍在 main 不通过" nz '^V14b 不通过：二乙批 1 个文件仍被跟踪.*build_records.py' V14b "${E[@]}"
git -C "$R2" rm -q --cached "$Dm/build_records.py"
git -C "$R2" branch -q -m "$B2" elsewhere
expect "V14b 没有归档分支跳过，不当通过" 0 '^V14b 跳过\(本检出查不到归档分支 archive/2026-10-b2b.*在分支上的部分未核' V14b "${E[@]}"
expect "V14b 没有归档分支严格模式失败" nz '^V14b 不通过' "$ALL" ACCEPT_STRICT=1 "${E[@]}"
e_index 'timeout'
expect "V14b 没有归档分支时索引漏登记仍不通过" nz '^V14b 不通过：清单与索引二乙批不逐项相等' V14b "${E[@]}"
e_index
git -C "$R2" add -f "$Dm/describe_nodes.py"
expect "V14b 没有归档分支时仍在 main 的文件也不通过" nz '^V14b 不通过：二乙批 1 个文件仍被跟踪' V14b "${E[@]}"
expect "V14b 非 git 仓库跳过" 0 '^V14b 跳过\(检出不是 git' V14b ACCEPT_ROOT="$tmp/nogit"

# V15: the drill (decide_drill in new_intent_selftest.py, calling decide_sync.py) must exist and pass with an ok line; the PR-opened and merged-without-dispatcher parts are always a separate skip
D15=$tmp/root/docs/modernization
expect "V15 缺自测不通过" nz '^V15 不通过' V15
mkdir -p "$D15/rules/decide"; echo x >"$D15/rules/decide/decide_sync.py"
printf 'print("ok")\n' >"$D15/new_intent_selftest.py"
expect "V15 自测里没有 decide 演练不通过" nz '^V15 不通过：.*没有 decide_sync 的演练' V15
printf '# decide_sync.py\n# def decide_drill(tree):\n# decide_drill(tree)\nprint("ok")\n' >"$D15/new_intent_selftest.py"
expect "V15 演练通过" 0 '^V15 通过（决定页一题作答' V15
expect "V15 不经调度合入与 PR 开出单列跳过" 0 'V15\(PR 开出、不经调度合入 main\) 跳过\(未验' V15
printf '# decide_sync.py\n# def decide_drill(tree):\n# decide_drill(tree)\nprint("ok")\nimport sys\nsys.exit(1)\n' >"$D15/new_intent_selftest.py"
expect "V15 自测失败不通过" nz '^V15 不通过' V15
printf '# decide_sync.py\n# def decide_drill(tree):\n# decide_drill(tree)\n' >"$D15/new_intent_selftest.py"
expect "V15 自测没有 ok 行不通过" nz '^V15 不通过' V15
printf '# decide_sync.py\n# decide_drill(tree)\nprint("ok")\n' >"$D15/new_intent_selftest.py"
expect "V15 演练只剩一处引用（定义被删）不通过" nz '^V15 不通过：.*没有 decide_sync 的演练' V15
rm "$D15/rules/decide/decide_sync.py"
printf '# decide_sync.py\n# def decide_drill(tree):\n# decide_drill(tree)\nprint("ok")\n' >"$D15/new_intent_selftest.py"
expect "V15 缺 decide_sync.py 不通过" nz '^V15 不通过：.*decide_sync.py 不存在' V15

# V16: .claude/settings.json permissions.deny holds Bash(codex *) and Bash(pi *); the delegate self-test passes and carries the lines for fake card on both engines, forbidden path refused, ledger
mkdir -p "$tmp/root/.claude"
S16="$tmp/root/.claude/settings.json"
cat >"$tmp/root/scripts/delegate_selftest.py" <<'PYEOF'
import os, sys
drop, mode = os.environ.get("DS_DROP", ""), os.environ.get("DS_MODE", "ok")
for line in ("PASS codex 两路之一跑通（退出 0）", "PASS deepseek 两路之一跑通（退出 0）", "PASS 范围是禁区文件：拒绝且不起子进程（退出 2）", "PASS codex 台账有记录且通过"):
    if not (drop and line.startswith(drop)):
        print(line)
if mode == "fail":
    sys.exit(1)
if mode != "nosummary":
    print("delegate 自测：全过")
PYEOF
expect "V16 缺 settings.json 不通过" nz '^V16 不通过：读 .claude/settings.json' V16
echo '{"permissions": {"deny": ["Bash(codex *)", "Bash(pi *)"]}}' >"$S16"
expect "V16 缺 delegate 自测不通过" nz '^V16 不通过：.*delegate_selftest.py 或 scripts/delegate.py 不存在' V16
echo x >"$tmp/root/scripts/delegate.py"
expect "V16 deny 与自测都齐通过" 0 '^V16 通过' V16
echo '{"permissions": {"deny": ["Bash(codex *)"]}}' >"$S16"
expect "V16 deny 缺 pi 不通过" nz '^V16 不通过：permissions.deny 缺 Bash\(pi \*\)' V16
echo '{"permissions": {"deny": ["Bash(pi *)"]}}' >"$S16"
expect "V16 deny 缺 codex 不通过" nz '^V16 不通过：permissions.deny 缺 Bash\(codex \*\)' V16
echo '{"permissions": {"deny": "Bash(codex *) Bash(pi *)"}}' >"$S16"
expect "V16 deny 不是列表不通过" nz '^V16 不通过：permissions.deny 缺' V16
echo '{"permissions": {}}' >"$S16"
expect "V16 没有 deny 不通过" nz '^V16 不通过：读 .claude/settings.json' V16
echo '{"permissions": {"deny": ["Bash(codex *)", "Bash(pi *)"]}}' >"$S16"
expect "V16 delegate 自测失败不通过" nz '^V16 不通过：delegate 自测退出非 0' V16 DS_MODE=fail
expect "V16 delegate 自测没有全过行不通过" nz '^V16 不通过：delegate 自测退出非 0 或没有' V16 DS_MODE=nosummary
for d in 'PASS codex 两路' 'PASS deepseek 两路' 'PASS 范围是禁区' 'PASS codex 台账'; do
  expect "V16 自测输出缺「$d」不通过" nz "^V16 不通过：delegate 自测输出缺「$d" V16 DS_DROP="$d"
done

# modes: blank item list or strict with a subset never exits 0; ACCEPT_STRICT only takes 0 or 1; summary counts
expect "ACCEPT_ONLY 空白一项不跑" nz '一项都没有跑' " "
expect "ACCEPT_ONLY 空串" nz '一项都没有跑' ""
expect "严格模式缩小范围" nz '严格模式必须跑全部' "V08" ACCEPT_STRICT=1
expect "严格模式不认 ACCEPT_SELFTEST" nz '严格模式必须跑全部' "V08" ACCEPT_STRICT=1 ACCEPT_SELFTEST=1
expect "ACCEPT_STRICT=true" nz 'ACCEPT_STRICT 只认' "V08" ACCEPT_STRICT=true
expect "ACCEPT_STRICT=空格1" nz 'ACCEPT_STRICT 只认' "V08" "ACCEPT_STRICT= 1"
expect "未知项" nz '^V99 不通过' "V99"
expect "跳过加通过" 0 '通过 1、跳过 1、不通过 0' "V05 V08"
mv "$tmp/budget/verify.md" "$tmp/budget/v.off"
expect "失败加通过" nz '通过 1、跳过 0、不通过 1' "V05 V17a"

[ "$bad" = 0 ] && echo "acceptance_v2_selftest：通过" || { echo "acceptance_v2_selftest：失败"; exit 1; }
