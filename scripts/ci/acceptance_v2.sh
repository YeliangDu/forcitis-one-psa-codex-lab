#!/usr/bin/env bash
# Batch-one items of acceptance list V01-V18 that can be scripted (redesign-1023 proposal section 5).
# Prints "Vxx 通过|不通过|跳过(原因)" per item; exit 0 only when no item failed. A skip never counts as a failure, and an item whose
# environment is missing says so instead of passing; so does an object that does not exist yet or has no data (nothing to check is never a pass).
# Env: ACCEPT_ROOT (checkout), ONE_SESSION_TOOLS (tool library), ACCEPT_BUDGET_DIR, ACCEPT_ONLY (items to run),
# ACCEPT_STRICT=1 (sign-off: a skip because the environment lacks something - library, gates, RULES.md, roles - counts as a failure; "no data" skips stay skips).
set -uo pipefail
ROOT=${ACCEPT_ROOT:-$(cd "$(dirname "$0")/../.." && pwd)}
TOOLS=${ONE_SESSION_TOOLS:-$HOME/.modernize/psa/tools/one-session}
BUDGET_DIR=${ACCEPT_BUDGET_DIR:-$HOME/.modernize/psa/budget}
ONLY=${ACCEPT_ONLY:-"V01 V02 V03 V05 V07 V08 V12 V17a"}
fails=0; passes=0; skips=0
OUT=""
STRICT=${ACCEPT_STRICT:-0}

report() {  # id pass|skip|env|fail [detail]; env = skip because the environment lacks something (a failure under ACCEPT_STRICT=1)
  local v=$2
  [ "$v" = env ] && { [ "$STRICT" = 1 ] && v=fail || v=skip; }
  case "$v" in
    pass) echo "$1 通过${3:+（$3）}"; passes=$((passes + 1)) ;;
    skip) echo "$1 跳过($3)"; skips=$((skips + 1)) ;;
    *) echo "$1 不通过${3:+：$3}"; fails=$((fails + 1)) ;;
  esac
}
tail_out() { printf '%s\n' "$OUT" | tail -n 5 | sed 's/^/    /'; }
# run a command, keep its output in $OUT; returns its status
run() { OUT=$("$@" 2>&1); }

budget_out=""
budget() {
  [ -n "$budget_out" ] || budget_out=$(python3 -I "$ROOT/scripts/ci/budget_check.py" --root "$ROOT" 2>&1; echo "rc=$?")
}
# violation lines of one check code (a line is "<code> <path> <text>"; info lines start with 信息 and never match); optional 2nd arg: path must contain it
violations() { printf '%s\n' "$budget_out" | grep -E "^$1 ${2:+[^ ]*$2}" || true; }
budget_sane() { printf '%s\n' "$budget_out" | grep -q '^budget_check' && ! printf '%s\n' "$budget_out" | grep -q '^POLICY '; }
# which of the given files/dirs are missing (a roles dir counts only with at least one *.md in it)
RULES_MD=docs/modernization/RULES.md ROLES_DIR=docs/modernization/roles
missing_docs() {
  local m=""
  [ -f "$ROOT/$RULES_MD" ] || m="$RULES_MD"
  ls "$ROOT/$ROLES_DIR"/*.md >/dev/null 2>&1 || m="${m:+$m、}$ROLES_DIR/*.md"
  printf '%s' "$m"
}

v01() {
  [ -f "$ROOT/scripts/ci/budget_check.py" ] || { report V01 fail 'scripts/ci/budget_check.py 不存在'; return; }
  budget
  budget_sane || { report V01 fail 'budget_check 没有给出可解析的结果'; printf '%s\n' "$budget_out" | tail -n 3 | sed 's/^/    /'; return; }
  local v m; v=$(violations 'H(7|8|9)'); m=$(missing_docs)
  if [ -n "$v" ]; then report V01 fail "H7/H8/H9 违例 $(printf '%s\n' "$v" | wc -l | tr -d ' ') 条"; printf '%s\n' "$v" | head -n 5 | sed 's/^/    /'
  elif [ -n "$m" ]; then report V01 env "$m 尚未合入，无从核对"
  else report V01 pass 'H7/H8/H9 无违例'; fi
}

# V03 size and ownership part only (the session list is batch four): role files <= 3 KB (H7 on roles/) and no overlapping exclusive paths (H17)
v03() {
  [ -f "$ROOT/scripts/ci/budget_check.py" ] || { report V03 fail 'scripts/ci/budget_check.py 不存在'; return; }
  budget
  budget_sane || { report V03 fail 'budget_check 没有给出可解析的结果'; return; }
  local v; v="$(violations H7 roles)$(violations H17)"
  if [ -n "$v" ]; then report V03 fail "角色文件 H7/H17 违例"; printf '%s\n' "$v" | head -n 5 | sed 's/^/    /'
  elif ! ls "$ROOT/$ROLES_DIR"/*.md >/dev/null 2>&1; then report V03 env "$ROLES_DIR/*.md 尚未合入，无从核对"
  else report V03 pass '角色文件大小与归属无违例；会话名单待第四批'; fi
}

# only the top level of scripts/gates is read: a gate self-test kept in a subdirectory is not found (then "no self-test" fails closed)
v02() {
  [ -d "$ROOT/scripts/gates" ] || { report V02 env 'scripts/gates 尚未合入'; return; }
  budget
  budget_sane || { report V02 fail 'budget_check 没有给出可解析的结果'; return; }
  local v st=0 n=0 f; v=$(violations H1)
  [ -z "$v" ] || { report V02 fail "H1 违例：$(printf '%s\n' "$v" | head -n 3 | tr '\n' ' ')"; return; }
  for f in "$ROOT"/scripts/gates/*_selftest*; do
    [ -f "$f" ] || continue
    n=$((n + 1))
    case "$f" in *.py) run python3 -I "$f" ;; *) run bash "$f" ;; esac || { report V02 fail "$(basename "$f") 退出非 0"; tail_out; st=1; }
  done
  [ "$st" = 0 ] || return
  [ "$n" -gt 0 ] || { report V02 fail 'scripts/gates 下没有 *_selftest* 自测'; return; }
  report V02 pass "H1 无违例，门自测 $n 个通过（用例条数不核）"
  report "V02(p99 与版本)" skip '无看板数据'

}

v05() {
  local h="$TOOLS/handoff/handoff.py" s="$TOOLS/stop_check/stop_check_selftest.py"
  local m=""; [ -f "$h" ] || m="handoff.py"; [ -f "$s" ] || m="${m:+$m、}stop_check_selftest.py"
  [ -z "$m" ] || { report V05 env "工具库 $TOOLS 缺 $m"; return; }
  run python3 -I "$h" --self-test || { report V05 fail 'handoff --self-test 退出非 0'; tail_out; return; }
  run python3 -I "$s" || { report V05 fail 'stop_check_selftest 退出非 0'; tail_out; return; }
  report V05 pass 'handoff 与 stop_check 自测通过'
}

v07() {
  [ -f "$ROOT/scripts/merge/mq.py" ] || { report V07 fail 'scripts/merge/mq.py 不存在'; return; }
  run python3 -I "$ROOT/scripts/merge/mq.py" --self-test || { report V07 fail 'mq.py --self-test 退出非 0'; tail_out; return; }
  report V07 pass 'mq 自测通过；批成、剔除、Issue 自动关闭待二甲和合入后冒烟'
}

v08() {   # the data source (merge-queue event log) does not exist yet (intent 222945): never a pass from a static look
  report V08 skip '无数据：合入队列事件日志暂无，待改合入队列时启用'
}

# budget_level self-test shared by V12 and V17a: sets BL_VERDICT (ok|env|fail) and BL_MSG
budget_level_selftest() {
  local f="$TOOLS/budget_level/budget_level_selftest.py"
  [ -f "$f" ] || { BL_VERDICT=env; BL_MSG="工具库缺 $f"; return; }
  if run python3 -I "$f"; then BL_VERDICT=ok; else BL_VERDICT=fail; BL_MSG='budget_level 自测退出非 0'; fi
}

# V12 ladder part only (the session config list is batch two-B): policy.json has the budget line and ladder; the ladder logic is the budget_level self-test
v12() {
  local missing
  missing=$(python3 -I - "$ROOT/policy.json" <<'EOF' 2>&1
import json, sys
try:
    b = json.load(open(sys.argv[1])).get("budget")
    miss = [] if isinstance(b, dict) else ["budget"]
    if isinstance(b, dict):
        miss += [f"budget.{k}" for k in ("line", "ladder") if not isinstance(b.get(k), dict) or not b[k]]
        if isinstance(b.get("ladder"), dict):
            miss += [f"budget.ladder.{k}" for k in ("L0", "L1", "L2", "L3") if k not in b["ladder"]]
    print("、".join(miss))
except Exception as e:
    print(f"policy.json 无法读取（{e}）")
EOF
)
  [ -z "$missing" ] || { report V12 fail "policy.json 缺 $missing"; return; }
  budget_level_selftest
  case "$BL_VERDICT" in
    ok) report V12 pass 'policy.json 有预算线与梯子，budget_level 自测通过；会话配置清单待二乙' ;;
    env) report V12 env "$BL_MSG" ;;
    *) report V12 fail "$BL_MSG"; tail_out ;;
  esac
}

# V17a: reading side only. level.json must exist and be fresh (a failure: batch one delivers it); no spot-check records means no data, never a pass
v17a() {
  local d="$BUDGET_DIR" age n
  budget_level_selftest
  case "$BL_VERDICT" in
    env) report V17a env "$BL_MSG"; return ;;
    fail) report V17a fail "$BL_MSG"; tail_out; return ;;
  esac
  age=$(python3 -I - "$d/level.json" "$ROOT/policy.json" <<'EOF'
import json, os, sys, time
try:
    every = json.load(open(sys.argv[2]))["budget"]["level_interval_minutes"]
except Exception:
    every = 30
try:
    age = (time.time() - os.path.getmtime(sys.argv[1])) / 60
except OSError:
    print("missing"); sys.exit()
print("stale" if age > every + 5 else "fresh")
EOF
)
  case "$age" in
    missing) report V17a fail "$d/level.json 不存在（读数从未写出）"; return ;;
    stale) report V17a fail "$d/level.json 已过期（超过读数间隔加 5 分钟未更新）"; return ;;
  esac
  [ -f "$d/verify.md" ] || { report V17a fail "$d/verify.md 不存在"; return; }
  n=$(grep -Ec '^\| *20[0-9]{2}-' "$d/verify.md")
  if [ "$n" -eq 0 ]; then report V17a skip '无数据：抽检记录 0 条'
  else report V17a pass "自测通过，level.json 新鲜；verify.md 已有 $n 条记录，偏差判定留给人"; fi
}

for item in $ONLY; do
  case "$item" in
    V01) v01 ;; V02) v02 ;; V03) v03 ;; V05) v05 ;; V07) v07 ;; V08) v08 ;; V12) v12 ;; V17a) v17a ;;
    *) report "$item" fail '未知项' ;;
  esac
done
echo "acceptance_v2：通过 $passes、跳过 $skips、不通过 $fails"
[ "$fails" -eq 0 ]
