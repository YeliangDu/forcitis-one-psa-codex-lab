#!/bin/bash
# 钩子启动器（GOV-1023-QUA-06）：从主检出运行 scripts/hooks/<名字>，而不是本工作区里可能落后几百个提交的副本。
# 用法：run_from_main.sh <钩子文件名> [参数…]；标准输入、环境和工作目录原样交给钩子。
# 主检出 = git 公共目录的上级（git rev-parse --path-format=absolute --git-common-dir，在会话的工作目录里取）。
# 主检出里找不到该钩子时退回本工作区的副本并向标准错误告警；两处都没有则以退出码 2 失败（不静默放行）。
name="$1"
shift || true
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
case "$name" in
  ""|*/*|.*) echo "run_from_main.sh: 钩子名无效：'$name'" >&2; exit 2 ;;
esac
target="$here/$name"
common="$(git rev-parse --path-format=absolute --git-common-dir 2>/dev/null)"
if [ -z "$common" ] && [ -n "$CLAUDE_PROJECT_DIR" ]; then
  common="$(git -C "$CLAUDE_PROJECT_DIR" rev-parse --path-format=absolute --git-common-dir 2>/dev/null)"
fi
if [ -n "$common" ] && [ "$(basename "$common")" = ".git" ]; then
  main="$(dirname "$common")/scripts/hooks/$name"
  if [ -x "$main" ]; then
    target="$main"
  else
    echo "run_from_main.sh: 告警：主检出没有可执行的 scripts/hooks/$name，退回本工作区副本（可能落后）" >&2
  fi
else
  echo "run_from_main.sh: 告警：找不到主检出（git 公共目录：'${common:-无}'），退回本工作区副本（可能落后）" >&2
fi
if [ ! -x "$target" ]; then
  echo "run_from_main.sh: 钩子 $name 不存在或不可执行（$target），按失败处理" >&2
  exit 2
fi
exec "$target" "$@"
