#!/bin/bash
# 钩子安装与切换（D26，GOV-1023-FV2 O7）：.claude/settings.json 的钩子直接调用 <install_dir>/current/scripts/{gates,hooks}/ 下的文件。
# 用法：install.sh [<提交>]        把该提交（默认 HEAD）的 scripts/gates、scripts/hooks 解包成 <install_dir>/<完整 sha>/ 并把 current 切过去
#       install.sh --switch <sha>  切到已装过的 flow-v2 版本：只认安装目录下 40 位小写 sha 的真实目录（链接、current、.tmp.*、修订名都拒，不建目录）。
#   每次都从 git 重新解包，已装目录被改坏了也就此修好（O7 终审 M1）。回退不用本脚本（定稿 4.5，intent 2026-10-24-001330）：current 保持批一所装
#   版本不动，靠第 ④ 步 git revert 恢复旧 settings.json 与 run_from_main；演练里的「切回」是切回上一个 flow-v2 版本。
# install_dir 与链接名取本仓库 policy.json 的 gates.install_dir、gates.install_current_link；环境变量 HOOKS_INSTALL_DIR 优先（自测用临时目录）。
# git archive 末尾补的零块 tar 不读（读到结束标记就退出），不排空的话 git archive 在 tar 退出后再写会 SIGPIPE，pipefail 下整条管道退出 141，负载高时偶发（#1183）；所以 tar 之后把余下的读掉。
# 先校验参数再建目录；解包进临时目录，写校验和清单（hooks_manifest.sh），自检，chmod -R a-w，旧目录改名后放入新目录再删旧的；并发装同一提交时放入失败（短暂重试，别人正好把已装目录挪开的那一瞬间也算）
# 而目录已就绪（与自己的清单、也与刚解包的清单一致）照常切换。current 用「临时链接再 rename 覆盖」原子切换（目标只会是 40 位 sha，不会自环；current 是目录时先挪开）。失败不动 current、不留半成品，退出 1。
# shellcheck disable=SC2015  # `a && b || fail`: fail on either, on purpose
set -u -o pipefail
py=/usr/bin/python3
rename() { "$py" -c 'import os,sys;os.rename(sys.argv[1],sys.argv[2])' "$1" "$2"; }
drop() { chmod -R u+w "$1" 2>/dev/null; rm -rf "$1"; }
fail() { echo "install.sh：$1；current 未改动。" >&2; [ -n "${tmp:-}" ] && drop "$tmp"; exit 1; }
repo=$(git -C "$(dirname "$0")" rev-parse --show-toplevel 2>/dev/null) || fail "找不到 install.sh 所在的仓库"
conf=$("$py" -c 'import json,os,sys;g=json.load(open(sys.argv[1]))["gates"];print(os.path.expanduser(g["install_dir"]));print(g["install_current_link"])' "$repo/policy.json") \
  || fail "policy.json 读不出 gates.install_dir、gates.install_current_link"
dir=${HOOKS_INSTALL_DIR:-$(printf '%s\n' "$conf" | sed -n 1p)}
link=$(printf '%s\n' "$conf" | sed -n 2p)
check() {  # 装好的目录能按 settings.json 的挂载方式用：两个入口可执行（开工单挂在工具库，不在这里）；分发器放行 ls，拒绝读不懂的输入，拒绝对特征表每个 target 第一项的写
  local d="$1/scripts/gates/pre_tool_dispatch.py" f line
  for f in "$d" "$1/scripts/hooks/git_ceiling_env.sh"; do
    [ -x "$f" ] || return 1
  done
  printf '%s' '{"tool_name":"Bash","tool_input":{"command":"ls"}}' | (cd "$repo" && CLAUDE_PROJECT_DIR="$repo" "$d") >/dev/null 2>&1 || return 1
  printf 'x' | "$d" >/dev/null 2>&1
  [ $? = 2 ] || return 1
  "$py" -c 'import json,sys;[print(json.dumps({"tool_name":"Bash","tool_input":{"command":"psql -h "+t["patterns"][0]+" -c \"delete from t\""}})) for t in json.load(open(sys.argv[1]))["targets"]]' \
    "$1/scripts/gates/prod_targets.json" | while read -r line; do
    printf '%s' "$line" | (cd "$repo" && CLAUDE_PROJECT_DIR="$repo" "$d") >/dev/null 2>&1
    [ $? = 2 ] || exit 1
  done
}
ready() { "$repo/scripts/gates/hooks_manifest.sh" "$1" | cmp -s - "$1/.manifest" && cmp -s "$1/.manifest" "$tmp/.manifest"; }  # 已装目录与它的清单一致，清单又与刚解包的一致
if [ "${1:-}" = --switch ]; then
  [ $# = 2 ] || fail "用法：install.sh --switch <已装版本的 40 位 sha>"
  [[ "$2" =~ ^[0-9a-f]{40}$ ]] && [ -d "$dir/$2" ] && [ ! -L "$dir/$2" ] || fail "没有已装的版本 '$2'（--switch 只认 $dir 下 40 位小写 sha 的真实目录）"
  shift
fi
[ $# -le 1 ] || fail "用法：install.sh [<提交>] | install.sh --switch <sha>"
sha=$(git -C "$repo" rev-parse --verify -q "${1:-HEAD}^{commit}") || fail "认不出提交 ${1:-HEAD}"
mkdir -p "$dir" || fail "建不了 $dir"
tmp=$(mktemp -d "$dir/.tmp.XXXXXX") || fail "建不了临时目录"
paths=$(git -C "$repo" ls-tree -d --name-only "$sha" scripts/gates scripts/hooks)
[ -n "$paths" ] || fail "提交 $sha 没有 scripts/gates、scripts/hooks"
# shellcheck disable=SC2086
git -C "$repo" archive "$sha" $paths | { tar -x -C "$tmp"; st=$?; cat >/dev/null; (exit "$st"); } && "$repo/scripts/gates/hooks_manifest.sh" "$tmp" >"$tmp/.manifest" || fail "取 $sha 的文件或写清单失败"
check "$tmp" || fail "版本 $sha 自检不过（入口缺失、分发器不放行 ls、或有 target 的生产写不拦）"
chmod -R a-w "$tmp" || fail "设不了只读：$tmp"
old="$tmp.old"
[ ! -e "$dir/$sha" ] && [ ! -L "$dir/$sha" ] || rename "$dir/$sha" "$old" 2>/dev/null  # 悬空链接也挪开；并发装同一提交时可能已被另一次安装挪走：交给下一步判断
# 并发装同一提交时，另一次安装正好把已装目录挪开、还没放入新的：这一瞬间 rename 因目录存在而失败、ready 又因目录不存在而失败（#879）。短暂重试，目录总会回来或空出来
placed=""
for _ in $(seq 30); do
  rename "$tmp" "$dir/$sha" 2>/dev/null && placed=1 && break
  ready "$dir/$sha" && placed=1 && break
  sleep 0.1
done
[ -n "$placed" ] || { { [ -e "$old" ] || [ -L "$old" ]; } && rename "$old" "$dir/$sha"; fail "放不进 $dir/$sha"; }
drop "$tmp"
tmp=""
[ ! -e "$old" ] && [ ! -L "$old" ] || drop "$old"
[ ! -d "$dir/$link" ] || [ -L "$dir/$link" ] || rename "$dir/$link" "$dir/.$link.dir.$$" || fail "挪不开 $dir/${link}（是目录，不是链接）"
ln -s "$sha" "$dir/.$link.$$" && "$py" -c 'import os,sys;os.replace(sys.argv[1],sys.argv[2])' "$dir/.$link.$$" "$dir/$link" || { rm -f "$dir/.$link.$$"; fail "切换 $dir/$link 失败"; }
[ ! -e "$dir/.$link.dir.$$" ] || drop "$dir/.$link.dir.$$"
echo "install.sh：$dir/$link → $sha"
