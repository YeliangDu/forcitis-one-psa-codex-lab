#!/usr/bin/env bash
# Batch-one items of acceptance list V01-V18 that can be scripted (redesign-1023 proposal section 5).
# Prints "Vxx 通过|不通过|跳过(原因)" per item; exit 0 only when no item failed. A skip never counts as a failure, and an item whose
# environment is missing says so instead of passing; so does an object that does not exist yet or has no data (nothing to check is never a pass).
# Env: ACCEPT_ROOT (checkout), ONE_SESSION_TOOLS (tool library), ACCEPT_BUDGET_DIR, ACCEPT_ONLY (items to run),
# ACCEPT_STRICT=1 (sign-off: a skip because the environment lacks something - library, gates, RULES.md, roles - counts as a failure; "no data" skips stay skips;
# it only accepts the full item list; ACCEPT_SELFTEST is not honoured here). Any other ACCEPT_STRICT value than 0 or 1 is an error.
set -uo pipefail
ROOT=${ACCEPT_ROOT:-$(cd "$(dirname "$0")/../.." && pwd)}
TOOLS=${ONE_SESSION_TOOLS:-$HOME/.modernize/psa/tools/one-session}
BUDGET_DIR=${ACCEPT_BUDGET_DIR:-$HOME/.modernize/psa/budget}
ALL="V01 V02 V03 V05 V07 V08 V12 V17a"
ONLY=${ACCEPT_ONLY-$ALL}
fails=0; passes=0; skips=0
OUT=""
STRICT=${ACCEPT_STRICT-0}
case "$STRICT" in 0|1) ;; *) echo "ACCEPT_STRICT 只认 0 或 1，收到「$STRICT」"; exit 2 ;; esac
if [ "$STRICT" = 1 ] && [ "$(echo $ONLY)" != "$ALL" ]; then
  echo "严格模式必须跑全部批一项（$ALL），不接受 ACCEPT_ONLY 缩小范围"; exit 2
fi

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

# run a self-test: exit 0 and (marker given: its pass line in the output; none: some output at all), so an empty file never passes; sets OUT
run_ok() {  # marker cmd...
  local marker=$1; shift
  run "$@" || return 1
  if [ -n "$marker" ]; then printf '%s\n' "$OUT" | grep -q -- "$marker"; else [ -n "$OUT" ]; fi
}

budget_out=""
budget() {
  [ -n "$budget_out" ] || budget_out=$(python3 -I "$ROOT/scripts/ci/budget_check.py" --root "$ROOT" 2>&1; echo "rc=$?")
}
# violation lines of one check code (a line is "<code> <path> <text>"; info lines start with 信息 and never match); optional 2nd arg: path must contain it
violations() { printf '%s\n' "$budget_out" | grep -E "^$1 ${2:+[^ ]*$2}" || true; }
budget_sane() { printf '%s\n' "$budget_out" | grep -Eq '^budget_check：违例 [0-9]+ 条' && ! printf '%s\n' "$budget_out" | grep -q '^POLICY '; }
# budget_check result usable for item $1: parsable summary, and none of the checks $2 (regex of codes) cut short ("<code> policy.json 检查无法完成").
# H23 is part of V02: the per-file line caps of the gates are checked there.
budget_gate() {
  budget
  budget_sane || { report "$1" fail 'budget_check 没有给出可解析的结果'; printf '%s\n' "$budget_out" | tail -n 3 | sed 's/^/    /'; return 1; }
  ! printf '%s\n' "$budget_out" | grep -Eq "^($2) .*检查无法完成" || { report "$1" fail "budget_check 的 $(printf '%s' "$2" | tr '|' '/') 检查中断（检查无法完成）"; return 1; }
}
# what is missing to check against: RULES.md must hold at least one "## " rule heading, a roles file must hold some text
RULES_MD=docs/modernization/RULES.md ROLES_DIR=docs/modernization/roles
has_roles() { local f; for f in "$ROOT/$ROLES_DIR"/*.md; do [ -f "$f" ] && grep -q '[^[:space:]]' "$f" && return 0; done; return 1; }
missing_docs() {
  local m=""
  grep -q '^## ' "$ROOT/$RULES_MD" 2>/dev/null || m="$RULES_MD（缺失或无规则内容）"
  has_roles || m="${m:+$m、}$ROLES_DIR/*.md（缺失或为空）"
  printf '%s' "$m"
}

v01() {
  [ -f "$ROOT/scripts/ci/budget_check.py" ] || { report V01 fail 'scripts/ci/budget_check.py 不存在'; return; }
  budget_gate V01 'H7|H8|H9' || return
  local v m; v=$(violations 'H(7|8|9)'); m=$(missing_docs)
  if [ -n "$v" ]; then report V01 fail "H7/H8/H9 违例 $(printf '%s\n' "$v" | wc -l | tr -d ' ') 条"; printf '%s\n' "$v" | head -n 5 | sed 's/^/    /'
  elif [ -n "$m" ]; then report V01 env "$m 无从核对"
  else report V01 pass 'H7/H8/H9 无违例'; fi
}

# V03 size and ownership part only (the session list is batch four): role files <= 3 KB (H7 on roles/) and no overlapping exclusive paths (H17)
v03() {
  [ -f "$ROOT/scripts/ci/budget_check.py" ] || { report V03 fail 'scripts/ci/budget_check.py 不存在'; return; }
  budget_gate V03 'H7|H17' || return
  local v; v="$(violations H7 roles)$(violations H17)"
  if [ -n "$v" ]; then report V03 fail "角色文件 H7/H17 违例"; printf '%s\n' "$v" | head -n 5 | sed 's/^/    /'
  elif ! has_roles; then report V03 env "$ROLES_DIR/*.md 缺失或为空，无从核对"
  else report V03 pass '角色文件大小与归属无违例；会话名单待第四批'; fi
}

# H1 counts scripts/gates and scripts/hooks together, so both are read here; only their top level (a self-test in a subdirectory is not found: "no self-test" fails closed)
v02() {
  local d f st=0 n=0 any=0 v
  for d in gates hooks; do [ -d "$ROOT/scripts/$d" ] && any=1; done
  [ "$any" = 1 ] || { report V02 env 'scripts/gates、scripts/hooks 都不存在'; return; }
  budget_gate V02 'H1|H23' || return
  v=$(violations H1)
  [ -z "$v" ] || { report V02 fail "H1 违例 $(printf '%s\n' "$v" | wc -l | tr -d ' ') 条"; printf '%s\n' "$v" | head -n 5 | sed 's/^/    /'; return; }
  for d in gates hooks; do
    for f in "$ROOT"/scripts/$d/*selftest* "$ROOT"/scripts/$d/test_*; do
      [ -f "$f" ] || continue
      n=$((n + 1))
      case "$f" in *.py) if grep -q -- '--self-test' "$f"; then run_ok '' python3 -I "$f" --self-test; else run_ok '' python3 -I "$f"; fi ;; *) run_ok '' bash "$f" ;; esac || { report V02 fail "$d/$(basename "$f") 退出非 0 或无输出"; tail_out; st=1; }
    done
  done
  [ "$st" = 0 ] || return
  [ "$n" -gt 0 ] || { report V02 fail 'scripts/gates、scripts/hooks 下没有自测'; return; }
  report V02 pass "H1 无违例，门自测 $n 个通过"
  report "V02(p99、版本、17 条用例)" skip '无看板数据，用例条数未核'
}

v05() {
  local h="$TOOLS/handoff/handoff.py" s="$TOOLS/stop_check/stop_check_selftest.py"
  local m=""; [ -f "$h" ] || m="handoff.py"; [ -f "$s" ] || m="${m:+$m、}stop_check_selftest.py"
  [ -z "$m" ] || { report V05 env "工具库 $TOOLS 缺 $m"; return; }
  run_ok "self-test: ok" python3 -I "$h" --self-test || { report V05 fail 'handoff --self-test 退出非 0 或无通过行'; tail_out; return; }
  run_ok "self-test: ok" python3 -I "$s" || { report V05 fail 'stop_check_selftest 退出非 0 或无通过行'; tail_out; return; }
  report V05 pass 'handoff 与 stop_check 自测通过'
}

v07() {
  [ -f "$ROOT/scripts/merge/mq.py" ] || { report V07 fail 'scripts/merge/mq.py 不存在'; return; }
  run_ok "self-test: ok" python3 -I "$ROOT/scripts/merge/mq.py" --self-test || { report V07 fail 'mq.py --self-test 退出非 0 或无通过行'; tail_out; return; }
  report V07 pass 'mq 自测通过；批成、剔除、Issue 自动关闭待二甲和合入后冒烟'
}

v08() {   # the data source (merge-queue event log) does not exist yet (intent 222945): never a pass from a static look
  report V08 skip '无数据：合入队列事件日志暂无，待改合入队列时启用'
}

# budget_level self-test shared by V12 and V17a: sets BL_VERDICT (ok|env|fail) and BL_MSG
budget_level_selftest() {
  local f="$TOOLS/budget_level/budget_level_selftest.py"
  [ -f "$f" ] || { BL_VERDICT=env; BL_MSG="工具库缺 $f"; return; }
  if run_ok '绿：0 项失败' python3 -I "$f"; then BL_VERDICT=ok; else BL_VERDICT=fail; BL_MSG='budget_level 自测退出非 0 或无通过行'; fi
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
            miss += [f"budget.ladder.{k}" for k in ("L0", "L1", "L2", "L3") if not isinstance(b["ladder"].get(k), dict) or not b["ladder"][k]]
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

# V17a: budget_level self-test, a fresh level.json that carries a reading, and the one-week spot check judged against policy budget.reading_max_deviation_pts.
# Whatever cannot be judged (no records, under a week, no threshold) is a skip with its reason, never a pass.
v17a() {
  local d="$BUDGET_DIR" r
  budget_level_selftest
  case "$BL_VERDICT" in
    env) report V17a env "$BL_MSG"; return ;;
    fail) report V17a fail "$BL_MSG"; tail_out; return ;;
  esac
  r=$(python3 -I - "$d/level.json" "$d/verify.md" "$ROOT/policy.json" <<'EOF' 2>&1
import datetime, json, math, os, re, sys, time
level, verify, policy = sys.argv[1:4]
def out(kind, msg): print(f"{kind}:{msg}"); sys.exit()
try:
    b = json.load(open(policy))["budget"]
    every, days, limit = b["level_interval_minutes"], b["reading_validation_days"], b["reading_max_deviation_pts"]
    num = lambda v: isinstance(v, (int, float)) and not isinstance(v, bool) and math.isfinite(v)
    if not num(every) or every <= 0 or not num(days) or days <= 0:
        out("fail", "policy.json budget.level_interval_minutes、reading_validation_days 须是正数")
    if not os.path.isfile(level):
        out("fail", f"{level} 不存在或不是文件（读数从未写出）")
    age = (time.time() - os.path.getmtime(level)) / 60
    if age < -1:
        out("fail", "level.json 的修改时间在未来")
    if age > every + 5:
        out("fail", "level.json 已过期（超过读数间隔加 5 分钟未更新）")
    rec = json.load(open(level))
    if not isinstance(rec, dict) or rec.get("status") != "正常" or not isinstance(rec.get("level"), str):
        out("fail", "level.json 不是一条正常读数（status 不是正常或没有 level）")
    if not os.path.isfile(verify) or not os.access(verify, os.R_OK):
        out("fail", f"{verify} 不存在或读不了")
    rows = []
    for line in open(verify, encoding="utf-8"):
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if not re.match(r"20\d\d-", cells[0]):
            continue
        try:
            when = datetime.datetime.strptime(cells[0], "%Y-%m-%d %H:%M" if " " in cells[0] else "%Y-%m-%d")
            dev = float(cells[3])
            if not math.isfinite(dev):
                raise ValueError
            rows.append((when, dev))
        except (ValueError, IndexError):
            out("fail", f"verify.md 有格式不对的记录行：{line.strip()[:40]}")
    if not rows:
        out("skip", "无数据：抽检记录 0 条")
    span = (max(r[0] for r in rows) - min(r[0] for r in rows)).total_seconds() / 86400
    if span < days:
        out("skip", f"抽检记录 {len(rows)} 条，只跨 {span:.1f} 天，未满 {days} 天")
    if limit is None:
        out("skip", "判不了：policy.json budget.reading_max_deviation_pts 未设")
    if not num(limit):
        out("fail", "policy.json budget.reading_max_deviation_pts 须是有限数")
    bad = [r for r in rows if abs(r[1]) > limit]
    out("fail" if bad else "pass", f"{len(bad)} 条偏差超过 {limit} 点" if bad else f"{len(rows)} 条记录跨 {span:.1f} 天，偏差均在 {limit} 点内")
except SystemExit:
    raise
except Exception as e:
    out("fail", f"检查出错：{type(e).__name__}")
EOF
)
  case "${r%%:*}" in
    pass) report V17a pass "自测通过，level.json 新鲜；${r#*:}" ;;
    skip) report V17a skip "${r#*:}" ;;
    *) report V17a fail "$(printf '%s' "${r#*:}" | head -n 1)" ;;
  esac
}

for item in $ONLY; do
  case "$item" in
    V01) v01 ;; V02) v02 ;; V03) v03 ;; V05) v05 ;; V07) v07 ;; V08) v08 ;; V12) v12 ;; V17a) v17a ;;
    *) report "$item" fail '未知项' ;;
  esac
done
echo "acceptance_v2：通过 $passes、跳过 $skips、不通过 $fails"
[ $((passes + skips + fails)) -gt 0 ] || { echo "一项都没有跑（ACCEPT_ONLY 为空？）"; exit 1; }
[ "$fails" -eq 0 ]
