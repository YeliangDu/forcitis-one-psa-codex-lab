#!/bin/bash
# 已装钩子的校验和清单（D26，O7 终审 M2）。威胁模型是误改与漂移，不防有意篡改（会话本来就能改安装目录与这个脚本）。
# 用法：hooks_manifest.sh <版本目录>         打印目录下除 .manifest 外每个文件的 sha256；install.sh 把它写成 <版本目录>/.manifest
#       hooks_manifest.sh --check <current>  SessionStart 用（挂在 git_ceiling_env 那条挂载前面，从会话仓库运行，不从 current 运行，
#         所以 current 指到别处也查得出）：current 须是指向同目录下 40 位 sha 目录的相对链接，该目录是真实目录（不是链接）、内容与清单一致。
#         不一致只输出一行【钩子告警】并提示 install.sh <sha>（从 git 重新解包修复；--switch 拒链接与缺失的目录，修不好这两种；current 不是 sha 链接时不带参数，装 HEAD）；不自动修，总是退出 0。PreToolUse 不调用。
set -u -o pipefail
list() {
  (cd "$1" && find . ! -type d ! -name .manifest -exec shasum -a 256 {} + | LC_ALL=C sort -k 2)
}
if [ "${1:-}" != --check ]; then
  [ -d "${1:-}" ] && list "$1"
  exit
fi
link=${2:-}
target=$(readlink "$link" 2>/dev/null)
version="$(dirname "$link")/$target"
why=""
if ! [[ "$target" =~ ^[0-9a-f]{40}$ ]]; then
  why="current（${link}）不是指向安装目录下 40 位 sha 目录的相对链接（指向：${target:-无}）"
  target=""
elif [ -L "$version" ]; then
  why="已装版本 $target 是符号链接（指向：$(readlink "$version")），不是安装根下的真实目录"
elif [ ! -d "$version" ]; then
  why="current 指向的目录 $target 不存在"
elif [ ! -f "$version/.manifest" ]; then
  why="已装版本 $target 没有校验和清单"
elif ! list "$version" | cmp -s - "$version/.manifest"; then
  why="已装版本 $target 的文件与安装时的清单不一致（被改过、多了或少了文件）"
fi
[ -z "$why" ] || echo "【钩子告警】${why}，生产写门可能不是合入的那一版；请 one-ops 执行 scripts/gates/install.sh${target:+ $target}（从 git 重新解包修复${target:-；不带参数装本工作区的 HEAD，或填 main 上的 sha}）"
exit 0
