#!/bin/bash
# 钩子安装与切换（D26，GOV-1023-FV2 O7）：.claude/settings.json 的钩子直接调用 <install_dir>/current/scripts/{gates,hooks}/ 下的文件。
# 用法：install.sh [<提交>]              把该提交（默认 HEAD）的 scripts/gates、scripts/hooks 解包成 <install_dir>/<完整 sha>/ 并把 current 切过去
#       install.sh --switch <sha|目录名>  切到已装过的 flow-v2 版本；只用于 flow-v2 各版本之间。
#   每次都从 git 重新解包，已装目录被改坏了也就此修好（O7 终审 M1）。回退到 flow-v2 之前不用本脚本：用 git 把 .claude/settings.json
#   恢复成 pre-flow-v2 的挂载（旧 run_from_main 从主检出运行），见定稿 4.5 回退第 ② 步。
# install_dir 与链接名取本仓库 policy.json 的 gates.install_dir、gates.install_current_link；环境变量 HOOKS_INSTALL_DIR 优先（自测用临时目录）。
# 解包进临时目录，写校验和清单（hooks_manifest.sh，SessionStart 据此核对），自检，chmod -R a-w 挡误改，旧目录改名后放入新目录再删旧的；
# current 用「新建临时链接再 rename 覆盖」原子切换。任何一步失败都不动 current、不留半成品，退出码 1。自测：install_selftest.py。
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

check() {  # 装好的目录能按 settings.json 的挂载方式用：三个入口可执行；分发器放行 ls，拒绝读不懂的输入，拒绝对特征表每个 target 第一项的写
  local d="$1/scripts/gates/pre_tool_dispatch.py" f line
  for f in "$d" "$1/scripts/hooks/git_ceiling_env.sh" "$1/scripts/hooks/start_sheet.py"; do
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

mkdir -p "$dir" || fail "建不了 $dir"
if [ "${1:-}" = --switch ]; then
  [ $# = 2 ] || fail "用法：install.sh --switch <sha 或目录名>"
  case "$2" in ""|*/*|.*) fail "版本名无效：'$2'" ;; esac
  name=$2
  [ -d "$dir/$name" ] || name=$(git -C "$repo" rev-parse --verify -q "$2^{commit}") || fail "没有已装的版本 $2"
  [ -d "$dir/$name" ] || fail "没有已装的版本 $2（$dir/$name）"
  set -- "$name"
fi
[ $# -le 1 ] || fail "用法：install.sh [<提交>] | install.sh --switch <sha 或目录名>"
sha=$(git -C "$repo" rev-parse --verify -q "${1:-HEAD}^{commit}") || fail "认不出提交 ${1:-HEAD}"
tmp=$(mktemp -d "$dir/.tmp.XXXXXX") || fail "建不了临时目录"
paths=$(git -C "$repo" ls-tree -d --name-only "$sha" scripts/gates scripts/hooks)
[ -n "$paths" ] || fail "提交 $sha 没有 scripts/gates、scripts/hooks"
# shellcheck disable=SC2086
git -C "$repo" archive "$sha" $paths | tar -x -C "$tmp" && "$repo/scripts/gates/hooks_manifest.sh" "$tmp" >"$tmp/.manifest" || fail "取 $sha 的文件或写清单失败"
check "$tmp" || fail "版本 $sha 自检不过（入口缺失、分发器不放行 ls、或有 target 的生产写不拦）"
old="$tmp.old"
chmod -R a-w "$tmp" && { [ ! -e "$dir/$sha" ] || rename "$dir/$sha" "$old"; } || fail "挪不开旧的 $dir/$sha"
rename "$tmp" "$dir/$sha" || { [ -e "$old" ] && rename "$old" "$dir/$sha"; fail "放不进 $dir/$sha"; }
tmp=""
[ ! -e "$old" ] || drop "$old"
ln -s "$sha" "$dir/.$link.$$" && "$py" -c 'import os,sys;os.replace(sys.argv[1],sys.argv[2])' "$dir/.$link.$$" "$dir/$link" || { rm -f "$dir/.$link.$$"; fail "切换 $dir/$link 失败"; }
echo "install.sh：$dir/$link → $sha"
