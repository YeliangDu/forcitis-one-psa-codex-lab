#!/usr/bin/env bash
# Batch-one and batch two-B items of acceptance list V01-V18 that can be scripted (redesign-1023 proposal section 5; V07, V09, V17b are not written here).
# An item whose dependency is not merged yet is written as the check the final table asks for and reports red or unmet (never loosened to go green).
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
GH=${ACCEPT_GH:-gh}   # V06 label query; ACCEPT_ARCHIVE_BRANCH_B2B (default archive/2026-10-b2b) is the V14b branch
ARCHIVE_BRANCH_B2B=${ACCEPT_ARCHIVE_BRANCH_B2B:-archive/2026-10-b2b}
ALL="V01 V02 V03 V04 V05 V06 V07 V08 V10 V11 V12 V13 V14 V14b V15 V16 V17a"
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

# run a command from the checkout root (the tools take no directory argument); a subshell, so the caller's directory is untouched
in_root() { ( cd "$ROOT" && "$@" ); }

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
      case "$f" in *.py) if grep -qE "[\"']--self-test[\"']" "$f"; then run_ok '' python3 -I "$f" --self-test; else run_ok '' python3 -I "$f"; fi ;; *) run_ok '' bash "$f" ;; esac || { report V02 fail "$d/$(basename "$f") 退出非 0 或无输出"; tail_out; st=1; }
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
  for d in mailbox outbox; do [ -e "$PSA_DIR/$d" ] && left="${left:+${left}、}$d"; done
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

# V14: D-class files are out of main with a trail: ARCHIVE_INDEX.md is tracked and non-empty, every file path it registers (table rows: directory + file names; and the full
# list under the per-file section "## 逐文件") is no longer tracked by this checkout and does exist on the archive branch (git ls-tree -r). Table rows whose file list is cut
# with "…" only name part of their files: those are checked as listed (the per-file section covers the rest) and the result says so. An index that yields no path at all fails.
# A checkout without the archive branch (a shallow CI clone) cannot say: skip (strict mode fails).
v14() {
  local idx=docs/modernization/ARCHIVE_INDEX.md t r ref=""
  is_git || { report V14 env '检出不是 git 仓库，查不了 git ls-files'; return; }
  t=$(tracked "$idx") || { report V14 fail 'git ls-files 出错'; return; }
  { [ -n "$t" ] && [ -s "$ROOT/$idx" ]; } || { report V14 fail "$idx 不存在、未跟踪或为空"; return; }
  if git -C "$ROOT" rev-parse --verify -q "refs/heads/${ARCHIVE_BRANCH}^{commit}" >/dev/null; then ref="refs/heads/${ARCHIVE_BRANCH}"
  elif git -C "$ROOT" rev-parse --verify -q "refs/remotes/origin/${ARCHIVE_BRANCH}^{commit}" >/dev/null; then ref="refs/remotes/origin/${ARCHIVE_BRANCH}"; fi
  r=$(python3 -I - "$ROOT" "$idx" "$ref" <<'EOF' 2>&1
import re, subprocess, sys
root, idx, ref = sys.argv[1:4]
try:
    paths, cut, sect, listed = [], 0, False, 0
    text = open(f"{root}/{idx}", encoding="utf-8").read()
    for line in text.splitlines(True):
        if line.startswith("## "):
            sect = line.strip() == "## 逐文件"
            continue
        m = re.fullmatch(r"- \x60([^\x60]+)\x60", line.strip())
        if sect and m:
            paths.append(m.group(1))
            listed += 1
            continue
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
    paths = sorted(set(paths))
    if not paths:
        print("bad:索引里解析不出任何登记的文件路径")
        sys.exit()
    total = re.search(r"合计 (\d+) 个文件", text)
    if not total or int(total.group(1)) != len(paths) or (cut and not listed):
        print("bad:索引写明的合计与解析出的 %d 个文件对不上（合计行%s，逐文件清单 %d 条，截断行 %d）" % (len(paths), total.group(1) if total else "缺失", listed, cut))
        sys.exit()
    ls = subprocess.run(["git", "-C", root, "ls-files", "-z"], capture_output=True, check=True).stdout.decode("utf-8").split("\0")
    tracked = set(ls)
    still = [p for p in paths if p in tracked]
    if still:
        print("bad:索引登记的 %d 个文件仍被跟踪，如 %s" % (len(still), "、".join(still[:3])))
        sys.exit()
    if ref:
        out = subprocess.run(["git", "-C", root, "ls-tree", "-r", "--name-only", "-z", ref], capture_output=True, check=True).stdout.decode("utf-8").split("\0")
        onbr = set(out)
        gone = [p for p in paths if p not in onbr]
        if gone:
            print("bad:索引登记的 %d 个文件在归档分支上不存在，如 %s" % (len(gone), "、".join(gone[:3])))
            sys.exit()
    print("ok:索引登记的 %d 个文件都已不在 main 里%s" % (len(paths), "；%d 行表格文件列表被省略号截断，以逐文件清单补全" % cut if cut else ""))
except SystemExit:
    raise
except Exception as e:
    print("bad:读索引或 git 出错 " + type(e).__name__)
EOF
)
  case "$r" in ok:*) ;; *) report V14 fail "$(printf '%s' "${r#bad:}" | head -n 1)"; return ;; esac
  if [ -n "$ref" ]; then report V14 pass "$idx 在，${r#ok:}，且都在归档分支 $ARCHIVE_BRANCH 上；E 类见 V14b"
  else report V14 env "本检出查不到归档分支 ${ARCHIVE_BRANCH}（浅克隆或未取回）"; fi
}

# budget_level self-test shared by V12 and V17a: sets BL_VERDICT (ok|env|fail) and BL_MSG
budget_level_selftest() {
  local f="$TOOLS/budget_level/budget_level_selftest.py"
  [ -f "$f" ] || { BL_VERDICT=env; BL_MSG="工具库缺 $f"; return; }
  if run_ok '绿：0 项失败' python3 -I "$f"; then BL_VERDICT=ok; else BL_VERDICT=fail; BL_MSG='budget_level 自测退出非 0 或无通过行'; fi
}

# V12: policy.json has the budget line and ladder (the ladder logic is the budget_level self-test), and the session config list sessions.config (b2b-plan 10):
# a non-empty object, every value a non-empty object, and one entry for each session profile docs/modernization/sessions/*.md (names starting "_" are not sessions);
# the start sheet that shows level and config difference is the start_sheet self-test of the tool library (missing library: skip, a failure under ACCEPT_STRICT=1)
v12() {
  local missing cfg ss="$TOOLS/start_sheet/start_sheet.py" py=/usr/bin/python3
  [ -x "$py" ] || py=python3
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
  cfg=$(python3 -I - "$ROOT/policy.json" "$ROOT/docs/modernization/sessions" <<'EOF' 2>&1
import json, os, sys
try:
    s = json.load(open(sys.argv[1])).get("sessions")
    c = s.get("config") if isinstance(s, dict) else None
    if not isinstance(c, dict) or not c:
        print("bad:sessions.config 为空或不是对象（清单 10 的会话配置清单还没填）")
        sys.exit()
    empty = [k for k, v in c.items() if not isinstance(v, dict) or not v]
    names = sorted(f[:-3] for f in os.listdir(sys.argv[2]) if f.endswith(".md") and not f.startswith("_")) if os.path.isdir(sys.argv[2]) else []
    miss = [n for n in names if n not in c]
    if empty or miss:
        print("bad:sessions.config" + (" 里条目不是非空对象：" + "、".join(empty[:5]) if empty else "") + (" 缺会话画像对应的条目：" + "、".join(miss[:5]) if miss else ""))
        sys.exit()
    print("ok:%d 个会话" % len(c))
except SystemExit:
    raise
except Exception as e:
    print("bad:读 policy.json 或会话画像目录出错 " + type(e).__name__)
EOF
)
  case "$cfg" in ok:*) ;; *) report V12 fail "$(printf '%s' "${cfg#bad:}" | head -n 1)"; return ;; esac
  budget_level_selftest
  case "$BL_VERDICT" in env) report V12 env "$BL_MSG"; return ;; fail) report V12 fail "$BL_MSG"; tail_out; return ;; esac
  [ -f "$ss" ] || { report V12 env "工具库缺 ${ss}，开工单的档位与配置差异未核"; return; }
  run_ok "start_sheet self-test: ok" "$py" -I "$ss" --self-test || { report V12 fail 'start_sheet --self-test 退出非 0 或无通过行'; tail_out; return; }
  report V12 pass "policy.json 有预算线与梯子、会话配置清单（${cfg#ok:}），budget_level 与 start_sheet 自测通过"
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

# V04: the start sheet (injection <= 8 KB, required reading <= 15 KB) stays within its limits for every session profile, worst case: the tool's own `--check --all`,
# run from the checkout root with a fixed system python as the SessionStart hook does. It must say it checked at least one profile (an empty run is not a pass).
v04() {
  local ss="$TOOLS/start_sheet/start_sheet.py" py=/usr/bin/python3
  [ -x "$py" ] || py=python3
  [ -f "$ss" ] || { report V04 env "工具库缺 $ss"; return; }
  is_git || { report V04 env '检出不是 git 仓库，start_sheet --check 要在仓库里跑'; return; }
  run_ok 'start_sheet: [1-9][0-9]* 份画像的开工单都在上限内' in_root "$py" -I "$ss" --check --all || { report V04 fail 'start_sheet --check --all 退出非 0 或没有「N 份画像都在上限内」的结果行'; tail_out; return; }
  report V04 pass "$(printf '%s\n' "$OUT" | tail -n 1)"
}

# V06 (b2b-plan 4 and 23): cards live only in Issues, a card's labels are only the three kinds. Files: docs/modernization/tasks/, check_tasks.py and the WORK_CARD template are gone
# from the tracked tree and the Issue template exists; labels: every label on an Issue (gh issue list --state all, read-only; PRs are not listed) is "会话:<name>", "挂起" or "hold",
# or the one named exception "queued" (user decision, intent 2026-10-27-113802: ci.yml and scripts/ci/heavy_gate.py start the heavy steps on it, the current merge queue uses it),
# or one of the values of policy.json "labels" (read from the key, e.g. the review-passed and integration labels the new chain sets); at least one "会话:" label among them.
# Repository label definitions are not judged (the final text's "three kinds" is about cards). No wildcard: a card carrying any other label fails.
# A result as long as the query limit may be cut off and fails. Both halves are judged and both are reported. gh missing, not signed in or offline: the label half cannot be judged -
# a skip if the file half is fine (a failure under ACCEPT_STRICT=1).
v06() {
  local t bad="" labels r
  is_git || { report V06 env '检出不是 git 仓库，查不了 git ls-files'; return; }
  t=$(tracked docs/modernization/tasks docs/modernization/check_tasks.py docs/modernization/templates/WORK_CARD.md) || { report V06 fail 'git ls-files 出错'; return; }
  [ -z "$t" ] || bad="卡的文件还在仓库里 $(printf '%s\n' "$t" | wc -l | tr -d ' ') 个（tasks/、check_tasks.py、WORK_CARD 模板应已删，清单 23），如 $(printf '%s\n' "$t" | head -n 1)"
  t=$(tracked .github/ISSUE_TEMPLATE) || { report V06 fail 'git ls-files 出错'; return; }
  [ -n "$t" ] || bad="${bad:+${bad}；}没有 Issue 模板（.github/ISSUE_TEMPLATE/ 下无跟踪文件）"
  if labels=$(cd "$ROOT" && "$GH" issue list --state all --limit 1000 --json labels 2>/dev/null); then
    r=$(printf '%s' "$labels" | python3 -I -c '
import json, re, sys
LIMIT = 1000
try:
    try:
        pl = json.load(open(sys.argv[1] + "/policy.json", encoding="utf-8")).get("labels")
        allowed = {v for v in pl.values() if isinstance(v, str) and v} if isinstance(pl, dict) else set()
    except (OSError, ValueError):
        allowed = set()
    issues = json.load(sys.stdin)
    names = sorted({x["name"] for i in issues for x in i["labels"]})
    extra = [n for n in names if n not in ("挂起", "hold", "queued") and n not in allowed and not re.fullmatch("会话:.+", n)]
    if not allowed:
        print("bad:policy.json 读不到或没有 labels 段，允许的标签不全，不能判")
    elif len(issues) >= LIMIT:
        print("bad:卡的查询结果有 %d 条，达到上限，可能被截断" % len(issues))
    elif not any(n.startswith("会话:") for n in names):
        print("bad:卡上查不到任何「会话:<名>」标签，查询结果不可信")
    elif extra:
        print("bad:卡上的标签有三类、queued 与 policy.json labels 之外的 %d 个：%s" % (len(extra), "、".join(extra[:6])))
    else:
        print("ok:%d 张卡用到的 %d 个标签都属三类、queued 或 policy.json labels" % (len(issues), len(names)))
except Exception as e:
    print("bad:标签查询结果解析失败 " + type(e).__name__)
' "$ROOT" 2>&1)
    case "$r" in ok:*) ;; *) bad="${bad:+${bad}；}$(printf '%s' "${r#bad:}" | head -n 1)" ;; esac
  elif [ -z "$bad" ]; then report V06 env 'gh 查询标签失败（gh 缺失、未登录或无网络），三类标签未核'; return
  else r="bad:"; bad="${bad}；gh 查询标签失败，标签未核"; fi
  if [ -n "$bad" ]; then report V06 fail "$bad"; else report V06 pass "卡的文件已删、有 Issue 模板；${r#ok:}"; fi
}

# V14b (second half of V14, E class, b2b-plan 34, 35, 37): the list below is fixed here (an independent source; reading only the index would let the index check itself) and must equal,
# item by item, what ARCHIVE_INDEX.md registers in its batch two-B section ("## 二乙批…" up to the next "## "; table row: directory + file name, or a "- `path`" line) - both differences
# are reported; that section names the archive branch; none of them is tracked here; every one exists on the archive branch (git ls-tree). The list: E1 11, E2 23 (guardrails 4,
# closeout 10, fixtures 5, the two b31-rules files, packets 1, build_oa_inventory 1), E4 reconcile-decided.json 1 = 35 E-class files (b2b-plan 1.5, table 4 of the archive list), plus
# build_records.py deleted by b2b-plan 40, not E class, checked too because the index registers it in the same section: 36 in all. The branch may be missing (CI): the first halves are
# judged and then it skips (a failure under ACCEPT_STRICT=1), it never passes on the branch half unseen.
v14b() {
  local idx=docs/modernization/ARCHIVE_INDEX.md ref="" r
  is_git || { report V14b env '检出不是 git 仓库，查不了 git ls-files'; return; }
  if git -C "$ROOT" rev-parse --verify -q "refs/heads/${ARCHIVE_BRANCH_B2B}^{commit}" >/dev/null; then ref="refs/heads/${ARCHIVE_BRANCH_B2B}"
  elif git -C "$ROOT" rev-parse --verify -q "refs/remotes/origin/${ARCHIVE_BRANCH_B2B}^{commit}" >/dev/null; then ref="refs/remotes/origin/${ARCHIVE_BRANCH_B2B}"; fi
  r=$(python3 -I - "$ROOT" "$idx" "$ref" "$ARCHIVE_BRANCH_B2B" <<'EOF' 2>&1
import re, subprocess, sys
root, idx, ref, branch = sys.argv[1:5]
D = idx.rsplit("/", 1)[0] + "/"
C = D + "architecture/proposals/closeout/"
paths = [D + p for p in ("describe_nodes.py", "extract_topology.py", "live_code.py", "topology.json", "map/descriptions.json", "rules/data_objects.py", "rules/merge_rules.py",
        "rules/pull_result.py", "glossary/migrate_to_concepts.py", "glossary/test_checkers_on_concepts.py", "glossary/test_migrate_to_concepts.py",
        "architecture-guardrails/backend/coverage.csv", "architecture-guardrails/backend/negative-test.log",
        "architecture-guardrails/backend/src/test/java/com/forcitis/one/SecurityInvariantsTest.java", "architecture-guardrails/frontend/negative-lint.log",
        "map/packets/fst_infrastructure_cost.txt", "build_oa_inventory.py", "glossary/arch-naming/reconcile-decided.json")]
paths += [C + p for p in ("ARCH_VS14.md", "BPMN_STYLE.md", "CONVENTIONS.md", "PROCESS_METHOD_RESEARCH.md", "PROCESS_METHOD_REVIEW.md", "PROCESS_QUALITY_CHECKLIST.md",
        "RESPONSIBILITY_MATRIX_SPEC.md", "bpmn_check.py", "bpmn_gen.py", "converge_check.py",
        "people/b31-rules/B31_ALIGNMENT_AUDIT.md", "people/b31-rules/B31_CONSTRUCTION_DESIGN.md")]
paths += [C + "bpmn_gen_fixtures/%s.yaml" % n for n in ("activity_cancel", "entry", "exits", "mini", "timeout")]
E_COUNT = len(paths)
paths += [D + "build_records.py"]
def git(*a):
    return subprocess.run(["git", "-C", root, *a], capture_output=True, check=True).stdout.decode("utf-8").split("\0")
try:
    if E_COUNT != 35 or len(set(paths)) != 36:
        print("bad:脚本内的二乙批清单不是 E 类 35 个＋#40 1 个（E 类 %d，去重后共 %d）" % (E_COUNT, len(set(paths))))
        sys.exit()
    tracked = set(git("ls-files", "-z"))
    still = [p for p in paths if p in tracked]
    if still:
        print("bad:二乙批 %d 个文件仍被跟踪（归档未做），如 %s" % (len(still), still[0]))
        sys.exit()
    try:
        text = open(f"{root}/{idx}", encoding="utf-8").read()
    except OSError:
        print("bad:%s 读不到" % idx)
        sys.exit()
    reg, sect, body = set(), False, []
    for line in text.splitlines():
        if line.startswith("## "):
            sect = line.startswith("## 二乙批")
            continue
        if not sect:
            continue
        body.append(line)
        m = re.fullmatch(r"- \x60([^\x60]+)\x60", line.strip())
        if m:
            reg.add(m.group(1))
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if len(cells) == 4 and re.fullmatch(r"`[^`]+/`", cells[0]):
            reg |= {cells[0].strip("`") + n.strip() for n in cells[3].split("、") if n.strip() and n.strip() != "…"}
    if not body:
        print("bad:%s 没有「## 二乙批」一节或该节为空" % idx)
        sys.exit()
    if branch not in "\n".join(body):
        print("bad:%s 的二乙批一节没有写到归档分支 %s" % (idx, branch))
        sys.exit()
    unreg = sorted(set(paths) - reg)
    more = sorted(reg - set(paths))
    if unreg or more:
        print("bad:清单与索引二乙批不逐项相等：索引漏登记 %d 个%s；索引多登记 %d 个%s" % (len(unreg), "（%s）" % "、".join(unreg[:3]) if unreg else "",
              len(more), "（%s）" % "、".join(more[:3]) if more else ""))
        sys.exit()
    if not ref:
        print("env:本检出查不到归档分支 %s（本机分支，浅克隆或 CI 没有）；二乙批 %d 个文件已不在 main、与索引逐项相等，在分支上的部分未核" % (branch, len(paths)))
        sys.exit()
    onbr = set(git("ls-tree", "-r", "--name-only", "-z", ref))
    gone = [p for p in paths if p not in onbr]
    if gone:
        print("bad:二乙批 %d 个文件在归档分支上不存在，如 %s" % (len(gone), gone[0]))
        sys.exit()
    print("ok:二乙批 %d 个文件（E 类 %d、#40 删除 1）与索引逐项相等，都已不在 main，且都在归档分支 %s 上" % (len(paths), E_COUNT, branch))
except SystemExit:
    raise
except Exception as e:
    print("bad:读索引或 git 出错 " + type(e).__name__)
EOF
)
  case "$r" in
    ok:*) report V14b pass "${r#ok:}" ;;
    env:*) report V14b env "${r#env:}" ;;
    *) report V14b fail "$(printf '%s' "${r#bad:}" | head -n 1)" ;;
  esac
}

# V15 (cut down by b2b-plan 0.1): a decision page, one question answered -> an intent file is generated; its drill is the G3 part of new_intent_selftest.py (decide_sync.py -> new_intent,
# owner from the page register, bad registers refused). The drill must still be in the self-test (a file without it never passes). Two parts are not scripted and are reported as a skip:
# the PR being opened (writes to GitHub) and "merged into main without the dispatcher" (waits for the merge-queue rework).
v15() {
  local f=docs/modernization/new_intent_selftest.py
  [ -f "$ROOT/$f" ] && [ -f "$ROOT/docs/modernization/rules/decide/decide_sync.py" ] || { report V15 fail "$f 或 rules/decide/decide_sync.py 不存在"; return; }
  [ "$(grep -c 'decide_drill(' "$ROOT/$f")" -ge 2 ] && grep -q 'decide_sync.py' "$ROOT/$f" || { report V15 fail "$f 里没有 decide_sync 的演练（decide_drill 的定义与调用）"; return; }
  run_ok '^ok$' in_root python3 -I "$f" || { report V15 fail "$f 退出非 0 或没有 ok 行"; tail_out; return; }
  report V15 pass '决定页一题作答到生成 intent 文件的演练通过（new_intent 与 decide_sync 自测）'
  report "V15(PR 开出、不经调度合入 main)" skip '未验：开 PR 要写 GitHub 不在脚本里做；不经调度合入待合入队列改造（b2b-plan 0.1）'
}

# V16: delegate runs a fake card through codex and deepseek, forbidden paths are refused, the ledger has records (all in the delegate self-test, stubs, no external model);
# a direct call of codex or pi is refused: .claude/settings.json permissions.deny holds Bash(codex *) and Bash(pi *) (b2b-plan 4: deny, not a gate; delete a rule and this item fails).
# The self-test must say it passed and carry the four lines that stand for the clauses.
v16() {
  local f=scripts/delegate_selftest.py s="$ROOT/.claude/settings.json" r l
  r=$(python3 -I - "$s" <<'EOF' 2>&1
import json, sys
try:
    deny = json.load(open(sys.argv[1]))["permissions"]["deny"]
    miss = [r for r in ("Bash(codex *)", "Bash(pi *)") if not isinstance(deny, list) or r not in deny]
    print("bad:permissions.deny 缺 " + "、".join(miss) if miss else "ok")
except Exception as e:
    print("bad:读 .claude/settings.json 的 permissions.deny 出错 " + type(e).__name__)
EOF
)
  [ "$r" = ok ] || { report V16 fail "${r#bad:}"; return; }
  [ -f "$ROOT/$f" ] && [ -f "$ROOT/scripts/delegate.py" ] || { report V16 fail "$f 或 scripts/delegate.py 不存在"; return; }
  run_ok '^delegate 自测：全过$' in_root python3 -I "$f" || { report V16 fail 'delegate 自测退出非 0 或没有「全过」行'; tail_out; return; }
  for l in 'PASS codex 两路之一跑通' 'PASS deepseek 两路之一跑通' 'PASS 范围是禁区文件：拒绝' 'PASS codex 台账有记录'; do
    printf '%s\n' "$OUT" | grep -q "^$l" || { report V16 fail "delegate 自测输出缺「${l}」"; return; }
  done
  report V16 pass 'delegate 自测全过（两路跑通、禁区被拒、台账有记录），permissions.deny 挡住直接调用 codex 与 pi'
}

for item in $ONLY; do
  case "$item" in
    V01) v01 ;; V02) v02 ;; V03) v03 ;; V04) v04 ;; V05) v05 ;; V06) v06 ;; V07) v07 ;; V08) v08 ;; V10) v10 ;; V11) v11 ;; V12) v12 ;; V13) v13 ;; V14) v14 ;; V14b) v14b ;; V15) v15 ;; V16) v16 ;; V17a) v17a ;;
    *) report "$item" fail '未知项' ;;
  esac
done
echo "acceptance_v2：通过 ${passes}、跳过 ${skips}、不通过 $fails"
[ $((passes + skips + fails)) -gt 0 ] || { echo "一项都没有跑（ACCEPT_ONLY 为空？）"; exit 1; }
[ "$fails" -eq 0 ]
