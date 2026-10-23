#!/bin/bash
# 钩子安装与切换（D26，GOV-1023-FV2 O7）：.claude/settings.json 的钩子直接调用 <install_dir>/current/scripts/{gates,hooks}/ 下的文件。
# 用法：install.sh [<提交>]              把该提交（默认 HEAD）的 scripts/gates、scripts/hooks 装到 <install_dir>/<完整 sha>/，自检通过后把 current 切过去
#       install.sh --switch <sha|目录名>  把 current 切回已装的版本（先自检）
# install_dir 与链接名取本仓库 policy.json 的 gates.install_dir、gates.install_current_link；环境变量 HOOKS_INSTALL_DIR 优先（自测用临时目录）。
# 先装进临时目录再 rename；current 用「新建临时链接再 rename 覆盖」原子切换。任何一步失败都不动 current、不留半成品，退出码 1。自测：install_selftest.py。
set -u -o pipefail
py=/usr/bin/python3
fail() { echo "install.sh：$1；current 未改动。" >&2; [ -n "${tmp:-}" ] && rm -rf "$tmp"; exit 1; }
repo=$(git -C "$(dirname "$0")" rev-parse --show-toplevel 2>/dev/null) || fail "找不到 install.sh 所在的仓库"
conf=$("$py" -c 'import json,os,sys;g=json.load(open(sys.argv[1]))["gates"];print(os.path.expanduser(g["install_dir"]));print(g["install_current_link"])' "$repo/policy.json") \
  || fail "policy.json 读不出 gates.install_dir、gates.install_current_link"
dir=${HOOKS_INSTALL_DIR:-$(printf '%s\n' "$conf" | sed -n 1p)}
link=$(printf '%s\n' "$conf" | sed -n 2p)

check() {  # 装好的目录能按 settings.json 的挂载方式用：三个入口可执行；分发器放行 ls，拒绝读不懂的输入和碰生产特征表第一项的写
  local d="$1/scripts/gates/pre_tool_dispatch.py" f
  for f in "$d" "$1/scripts/hooks/git_ceiling_env.sh" "$1/scripts/hooks/start_sheet.py"; do
    [ -x "$f" ] || return 1
  done
  printf '%s' '{"tool_name":"Bash","tool_input":{"command":"ls"}}' | (cd "$repo" && CLAUDE_PROJECT_DIR="$repo" "$d") >/dev/null 2>&1 || return 1
  printf 'x' | "$d" >/dev/null 2>&1
  [ $? = 2 ] || return 1
  "$py" -c 'import json,sys;p=json.load(open(sys.argv[1]))["targets"][0]["patterns"][0];print(json.dumps({"tool_name":"Bash","tool_input":{"command":"psql -h "+p+" -c \"delete from t\""}}))' \
    "$1/scripts/gates/prod_targets.json" | (cd "$repo" && CLAUDE_PROJECT_DIR="$repo" "$d") >/dev/null 2>&1
  [ $? = 2 ]
}

switch() {  # $1 = install_dir 下的版本目录名
  check "$dir/$1" || fail "版本 $1 自检不过（入口缺失、分发器不放行 ls、或不拦生产写）"
  # shellcheck disable=SC2015
  ln -s "$1" "$dir/.$link.$$" && "$py" -c 'import os,sys;os.replace(sys.argv[1],sys.argv[2])' "$dir/.$link.$$" "$dir/$link" \
    || { rm -f "$dir/.$link.$$"; fail "切换 $dir/$link 失败"; }
  echo "install.sh：$dir/$link → $1"
}

mkdir -p "$dir" || fail "建不了 $dir"
if [ "${1:-}" = --switch ]; then
  [ $# = 2 ] || fail "用法：install.sh --switch <sha 或目录名>"
  case "$2" in ""|*/*|.*) fail "版本名无效：'$2'" ;; esac
  name=$2
  [ -d "$dir/$name" ] || name=$(git -C "$repo" rev-parse --verify -q "$2^{commit}") || fail "没有已装的版本 $2"
  [ -d "$dir/$name" ] || fail "没有已装的版本 $2（$dir/$name）"
  switch "$name"
  exit 0
fi
[ $# -le 1 ] || fail "用法：install.sh [<提交>] | install.sh --switch <sha 或目录名>"
sha=$(git -C "$repo" rev-parse --verify -q "${1:-HEAD}^{commit}") || fail "认不出提交 ${1:-HEAD}"
if [ ! -d "$dir/$sha" ]; then
  tmp=$(mktemp -d "$dir/.tmp.XXXXXX") || fail "建不了临时目录"
  paths=$(git -C "$repo" ls-tree -d --name-only "$sha" scripts/gates scripts/hooks)
  [ -n "$paths" ] || fail "提交 $sha 没有 scripts/gates、scripts/hooks"
  # shellcheck disable=SC2086
  git -C "$repo" archive "$sha" $paths | tar -x -C "$tmp" || fail "取 $sha 的文件失败"
  check "$tmp" || fail "版本 $sha 自检不过（入口缺失、分发器不放行 ls、或不拦生产写）"
  "$py" -c 'import os,sys;os.rename(sys.argv[1],sys.argv[2])' "$tmp" "$dir/$sha" || fail "放进 $dir/$sha 失败"
  tmp=""
fi
switch "$sha"
