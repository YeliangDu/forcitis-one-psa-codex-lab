#!/usr/bin/env bash
# Batch-one items of acceptance list V01-V18 that can be scripted (redesign-1023 proposal section 5).
# Prints "Vxx 通过|不通过|跳过(原因)" per item; exit 0 only when no item failed. A skip never counts as a failure, and an item whose
# environment is missing says so instead of passing; so does an object that does not exist yet or has no data (nothing to check is never a pass).
# Env: ACCEPT_ROOT (checkout), ONE_SESSION_TOOLS (tool library), ACCEPT_BUDGET_DIR, ACCEPT_BOARD (board.md), ACCEPT_JANITOR_DIR, ACCEPT_MEMORY_DIR,
# ACCEPT_PSA_DIR (local psa root, default ~/.modernize/psa), ACCEPT_ARCHIVE_BRANCH (default archive/2026-10), ACCEPT_ONLY (items to run),
# ACCEPT_STRICT=1 (sign-off: a skip because the environment lacks something - library, gates, RULES.md, roles - counts as a failure; "no data" skips stay skips;
# it only accepts the full item list; ACCEPT_SELFTEST is not honoured here). Any other ACCEPT_STRICT value than 0 or 1 is an error.
set -uo pipefail
ROOT=${ACCEPT_ROOT:-$(cd "$(dirname "$0")/../.." && pwd)}
TOOLS=${ONE_SESSION_TOOLS:-$HOME/.modernize/psa/tools/one-session}
BUDGET_DIR=${ACCEPT_BUDGET_DIR:-$HOME/.modernize/psa/budget}
BOARD=${ACCEPT_BOARD:-$HOME/.modernize/psa/board/board.md}
PSA_DIR=${ACCEPT_PSA_DIR:-$HOME/.modernize/psa}
JANITOR_DIR=${ACCEPT_JANITOR_DIR:-$HOME/.modernize/psa/janitor}
# V13: the newest janitor report may be at most this many days old. policy.json has no key for the recycle period yet (janitor only has daily_at),
# so 2 days stands in (daily run plus a day of slack); to be replaced by a policy key once it exists (待 policy 加键).
JANITOR_MAX_DAYS=2
MEMORY_DIR=${ACCEPT_MEMORY_DIR:-$HOME/.claude/projects/-Users-yeliang-Projects-FORCITIS-forcitis-one-psa/memory}
ARCHIVE_BRANCH=${ACCEPT_ARCHIVE_BRANCH:-archive/2026-10}
ALL="V01 V02 V03 V05 V07 V08 V10 V11 V12 V13 V14 V17a"
ONLY=${ACCEPT_ONLY-$ALL}
fails=0; passes=0; skips=0
OUT=""
STRICT=${ACCEPT_STRICT-0}
case "$STRICT" in 0|1) ;; *) echo "ACCEPT_STRICT 只认 0 或 1，收到「${STRICT}」"; exit 2 ;; esac
if [ "$STRICT" = 1 ] && [ "$(echo $ONLY)" != "$ALL" ]; then
  echo "严格模式必须跑全部批一项（${ALL}），不接受 ACCEPT_ONLY 缩小范围"; exit 2
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
  grep -q '^## ' "$ROOT/$RULES_MD" 2>/dev/null || m="${RULES_MD}（缺失或无规则内容）"
  has_roles || m="${m:+${m}、}$ROLES_DIR/*.md（缺失或为空）"
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
  local m=""; [ -f "$h" ] || m="handoff.py"; [ -f "$s" ] || m="${m:+${m}、}stop_check_selftest.py"
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

# tracked files of the checkout matching the given pathspecs: prints them; returns non-0 when git cannot say (never read "nothing printed" as "nothing tracked" then)
tracked() { git -C "$ROOT" ls-files -- "$@" 2>/dev/null; }
is_git() { git -C "$ROOT" rev-parse --git-dir >/dev/null 2>&1; }

# V10: OpenAPI and the frontend generated files are built, not tracked (O11)
v10() {
  local t
  is_git || { report V10 env '检出不是 git 仓库，查不了 git ls-files'; return; }
  t=$(tracked frontend/packages/api/openapi.json frontend/packages/api/src/generated) || { report V10 fail 'git ls-files 出错'; return; }
  if [ -n "$t" ]; then report V10 fail "生成物仍被跟踪 $(printf '%s\n' "$t" | wc -l | tr -d ' ') 个"; printf '%s\n' "$t" | head -n 5 | sed 's/^/    /'
  else report V10 pass 'openapi.json 与 src/generated 都不在 git ls-files 里'; fi
}

# V11 as built in batch two-A: the tools O15 deleted are gone from the tracked tree, the local mailbox and outbox directories are gone too (final 1.12, directory deletion;
# only reported here, never deleted from here), and the board stamp (first line "生成时间：<UTC ISO>") is under 20 minutes old.
# gapscan.py and session_map.json are still imported by the merge queue, whose rework is postponed (intent 2026-10-23-222945), so they are not checked here.
v11() {
  local t age
  is_git || { report V11 env '检出不是 git 仓库，查不了 git ls-files'; return; }
  t=$(tracked scripts/coord/mailbox.py scripts/ops/session_daily.py) || { report V11 fail 'git ls-files 出错'; return; }
  [ -z "$t" ] || { report V11 fail "应已删除的工具仍被跟踪：$(printf '%s' "$t" | tr '\n' ' ')"; return; }
  [ -d "$PSA_DIR" ] || { report V11 env "本机目录 $PSA_DIR 不存在（CI 上没有），mailbox、outbox 目录与看板未核"; return; }
  local d left=""
  for d in mailbox outbox; do [ -e "$PSA_DIR/$d" ] && left="${left:+$left、}$d"; done
  [ -z "$left" ] || { report V11 fail "本机 $PSA_DIR 下应已删除的目录仍在：${left}（只报告，未删）"; return; }
  [ -f "$BOARD" ] || { report V11 env "看板 $BOARD 不存在（本机文件），时间戳未核"; return; }
  age=$(python3 -I - "$BOARD" <<'EOF' 2>&1
import datetime, re, sys
try:
    first = open(sys.argv[1], encoding="utf-8").readline()
    m = re.match(r"生成时间：(20\d\d-\d\d-\d\dT\d\d:\d\d:\d\dZ)\s*$", first)
    if not m:
        print("bad:看板第一行不是「生成时间：<UTC 时间>」")
        sys.exit()
    t = datetime.datetime.strptime(m.group(1), "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=datetime.timezone.utc)
    print("ok:%d" % round((datetime.datetime.now(datetime.timezone.utc) - t).total_seconds() / 60))
except SystemExit:
    raise
except Exception as e:
    print("bad:读看板出错 " + type(e).__name__)
EOF
)
  case "$age" in
    ok:*) age=${age#ok:}
      if [ "$age" -lt -1 ]; then report V11 fail '看板时间在未来'
      elif [ "$age" -gt 20 ]; then report V11 fail "看板时间戳已过 ${age} 分钟（上限 20）"
      else report V11 pass "mailbox.py、session_daily.py 已删，本机 mailbox、outbox 目录已无；看板 ${age} 分钟前刷新"; fi ;;
    *) report V11 fail "$(printf '%s' "${age#bad:}" | head -n 1)" ;;
  esac
}

# V13: the janitor has written a report within JANITOR_MAX_DAYS (alerts only in batch two-A, nothing deleted); the memory directory totals <= policy.json memory_max_kb.
# Both live on this machine: a missing directory is a skip (a failure under ACCEPT_STRICT=1); an oversized memory is a failure - never trim it from here.
v13() {
  local bad="" miss="" f n=0 fresh r mem_note=""
  if [ -d "$JANITOR_DIR" ]; then
    for f in "$JANITOR_DIR"/report-*.md; do [ -f "$f" ] && [ -s "$f" ] && n=$((n + 1)); done
    if [ "$n" -eq 0 ]; then bad="回收器目录 $JANITOR_DIR 里没有非空的 report-*.md"
    else
      fresh=$(find "$JANITOR_DIR" -maxdepth 1 -type f -name 'report-*.md' -size +0c -mtime "-$JANITOR_MAX_DAYS" 2>/dev/null | wc -l | tr -d ' ')
      [ "${fresh:-0}" -gt 0 ] || bad="回收器报告都已陈旧（最新一份也超过 ${JANITOR_MAX_DAYS} 天，回收器可能停摆）"
    fi
  else miss="回收器目录 $JANITOR_DIR 不存在"; fi
  if [ -d "$MEMORY_DIR" ]; then
    r=$(python3 -I - "$MEMORY_DIR" "$ROOT/policy.json" <<'EOF' 2>&1
import json, math, os, sys
try:
    cap = json.load(open(sys.argv[2]))["memory_max_kb"]
    if isinstance(cap, bool) or not isinstance(cap, (int, float)) or not math.isfinite(cap) or cap <= 0:
        print("bad:policy.json memory_max_kb 须是正数")
        sys.exit()
    total = sum(os.path.getsize(os.path.join(d, f)) for d, _, fs in os.walk(sys.argv[1]) for f in fs)
    print(("bad:" if total > cap * 1024 else "ok:") + "记忆合计 %d 字节，上限 %g KB" % (total, cap))
except SystemExit:
    raise
except Exception as e:
    print("bad:读记忆目录或 policy.json 出错 " + type(e).__name__)
EOF
)
    case "$r" in ok:*) mem_note=${r#ok:} ;; *) bad="${bad:+${bad}；}$(printf '%s' "${r#bad:}" | head -n 1)" ;; esac
  else miss="${miss:+${miss}、}记忆目录 $MEMORY_DIR 不存在"; fi
  if [ -n "$bad" ]; then report V13 fail "$bad"
  elif [ -n "$miss" ]; then report V13 env "${miss}（本机目录），未核"
  else report V13 pass "回收器报告 ${n} 份，${JANITOR_MAX_DAYS} 天内有新报告；${mem_note}"; fi
}

# V14: D-class files are out of main with a trail: ARCHIVE_INDEX.md is tracked and non-empty, every file path it registers (table row: directory + file names) is no longer
# tracked by this checkout, and the archive branch can be looked up (E class is batch two-B). Rows whose file list is cut with "…" only name part of their files: those are
# checked as listed and the result says it was a partial check. An index that yields no path at all fails. A checkout without that branch (a shallow CI clone) cannot say: skip.
v14() {
  local idx=docs/modernization/ARCHIVE_INDEX.md t r
  is_git || { report V14 env '检出不是 git 仓库，查不了 git ls-files'; return; }
  t=$(tracked "$idx") || { report V14 fail 'git ls-files 出错'; return; }
  { [ -n "$t" ] && [ -s "$ROOT/$idx" ]; } || { report V14 fail "$idx 不存在、未跟踪或为空"; return; }
  r=$(python3 -I - "$ROOT" "$idx" <<'EOF' 2>&1
import re, subprocess, sys
root, idx = sys.argv[1:3]
try:
    paths, cut = [], 0
    for line in open(f"{root}/{idx}", encoding="utf-8"):
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if len(cells) != 4 or not re.fullmatch(r"`[^`]+/`", cells[0]):
            continue
        d = cells[0].strip("`")
        for name in cells[3].split("、"):
            name = name.strip()
            if name == "…":
                cut += 1
            elif name:
                paths.append(d + name)
    if not paths:
        print("bad:索引里解析不出任何登记的文件路径")
        sys.exit()
    ls = subprocess.run(["git", "-C", root, "ls-files", "-z"], capture_output=True, check=True).stdout.decode("utf-8").split("\0")
    tracked = set(ls)
    still = [p for p in paths if p in tracked]
    if still:
        print("bad:索引登记的 %d 个文件仍被跟踪，如 %s" % (len(still), "、".join(still[:3])))
    else:
        print("ok:索引登记的 %d 个文件都已不在 main 里%s" % (len(paths), "；%d 行文件列表被省略号截断，只核了所列部分" % cut if cut else ""))
except SystemExit:
    raise
except Exception as e:
    print("bad:读索引或 git ls-files 出错 " + type(e).__name__)
EOF
)
  case "$r" in ok:*) ;; *) report V14 fail "$(printf '%s' "${r#bad:}" | head -n 1)"; return ;; esac
  if git -C "$ROOT" rev-parse --verify -q "refs/heads/$ARCHIVE_BRANCH^{commit}" >/dev/null || git -C "$ROOT" rev-parse --verify -q "refs/remotes/origin/$ARCHIVE_BRANCH^{commit}" >/dev/null; then
    report V14 pass "$idx 在，${r#ok:}，归档分支 $ARCHIVE_BRANCH 可查；E 类待二乙"
  else report V14 env "本检出查不到归档分支 ${ARCHIVE_BRANCH}（浅克隆或未取回）"; fi
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
    V01) v01 ;; V02) v02 ;; V03) v03 ;; V05) v05 ;; V07) v07 ;; V08) v08 ;; V10) v10 ;; V11) v11 ;; V12) v12 ;; V13) v13 ;; V14) v14 ;; V17a) v17a ;;
    *) report "$item" fail '未知项' ;;
  esac
done
echo "acceptance_v2：通过 ${passes}、跳过 ${skips}、不通过 $fails"
[ $((passes + skips + fails)) -gt 0 ] || { echo "一项都没有跑（ACCEPT_ONLY 为空？）"; exit 1; }
[ "$fails" -eq 0 ]
