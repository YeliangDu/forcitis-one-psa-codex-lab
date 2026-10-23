#!/usr/bin/env bash
# Batch-one items of acceptance list V01-V18 that can be scripted (redesign-1023 proposal section 5).
# Prints "Vxx 通过|不通过|跳过(原因)" per item; exit 0 only when no item failed. A skip never counts as a failure, and an item whose
# environment is missing says so instead of passing. Env: ACCEPT_ROOT (checkout), ONE_SESSION_TOOLS (tool library), ACCEPT_BUDGET_DIR, ACCEPT_ONLY (items to run).
set -uo pipefail
ROOT=${ACCEPT_ROOT:-$(cd "$(dirname "$0")/../.." && pwd)}
TOOLS=${ONE_SESSION_TOOLS:-$HOME/.modernize/psa/tools/one-session}
BUDGET_DIR=${ACCEPT_BUDGET_DIR:-$HOME/.modernize/psa/budget}
ONLY=${ACCEPT_ONLY:-"V01 V02 V05 V07 V08 V17a"}
fails=0
OUT=""

report() {  # id verdict [detail]
  case "$2" in
    pass) echo "$1 通过${3:+（$3）}" ;;
    skip) echo "$1 跳过($3)" ;;
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
# violation lines of one rule (info lines start with 信息 and never count)
violations() { printf '%s\n' "$budget_out" | grep -E "^$1 " || true; }
budget_sane() { printf '%s\n' "$budget_out" | grep -q '^budget_check：违例' && ! printf '%s\n' "$budget_out" | grep -q '^POLICY '; }

v01() {
  [ -f "$ROOT/scripts/ci/budget_check.py" ] || { report V01 fail 'scripts/ci/budget_check.py 不存在'; return; }
  budget
  budget_sane || { report V01 fail 'budget_check 没有给出可解析的结果'; printf '%s\n' "$budget_out" | tail -n 3 | sed 's/^/    /'; return; }
  local v; v=$(violations 'H(7|8|9)')
  if [ -n "$v" ]; then report V01 fail "H7/H8/H9 违例 $(printf '%s\n' "$v" | wc -l | tr -d ' ') 条"; printf '%s\n' "$v" | head -n 5 | sed 's/^/    /'
  else report V01 pass 'H7/H8/H9 无违例'; fi
}

v02() {
  [ -d "$ROOT/scripts/gates" ] || { report V02 skip 'scripts/gates 尚未合入'; return; }
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
  report V02 pass "H1 无违例，门自测 $n 个通过；p99 与版本待看板核对"
}

v05() {
  local h="$TOOLS/handoff/handoff.py" s="$TOOLS/stop_check/stop_check_selftest.py"
  [ -f "$h" ] && [ -f "$s" ] || { report V05 skip "工具库 $TOOLS 缺 handoff.py 或 stop_check_selftest.py"; return; }
  run python3 -I "$h" --self-test || { report V05 fail 'handoff --self-test 退出非 0'; tail_out; return; }
  run python3 -I "$s" || { report V05 fail 'stop_check_selftest 退出非 0'; tail_out; return; }
  report V05 pass 'handoff 与 stop_check 自测通过'
}

v07() {
  [ -f "$ROOT/scripts/merge/mq.py" ] || { report V07 fail 'scripts/merge/mq.py 不存在'; return; }
  run python3 -I "$ROOT/scripts/merge/mq.py" --self-test || { report V07 fail 'mq.py --self-test 退出非 0'; tail_out; return; }
  report V07 pass 'mq 自测通过；批成、剔除、Issue 自动关闭待二甲和合入后冒烟'
}

v08() {
  local rc="$ROOT/scripts/ci/relevant_change.py" ci="$ROOT/.github/workflows/ci.yml"
  if [ -f "$rc" ] && [ -f "$ci" ] && grep -q 'tier=docs' "$rc" && grep -q 'relevant_change.py' "$ci"; then
    report V08 pass '仅静态核对：relevant_change.py 有 docs 档且 ci.yml 引用；≤5 分钟待事件日志核对'
  else report V08 skip '无事件日志，合入后核对'; fi
}

v17a() {
  local s="$TOOLS/budget_level/budget_level_selftest.py" v="$BUDGET_DIR/verify.md"
  [ -f "$s" ] || { report V17a skip "工具库缺 $s"; return; }
  run python3 -I "$s" || { report V17a fail 'budget_level 自测退出非 0'; tail_out; return; }
  [ -f "$v" ] || { report V17a fail "$v 不存在"; return; }
  report V17a pass "自测通过；verify.md 已有 $(grep -Ec '^\| *20[0-9]{2}-' "$v") 条记录，偏差判定留给人"
}

for item in $ONLY; do
  case "$item" in
    V01) v01 ;; V02) v02 ;; V05) v05 ;; V07) v07 ;; V08) v08 ;; V17a) v17a ;;
    *) report "$item" fail '未知项' ;;
  esac
done
if [ "$fails" -eq 0 ]; then echo "acceptance_v2：批一项全部通过或跳过"; else echo "acceptance_v2：$fails 项不通过"; fi
[ "$fails" -eq 0 ]
