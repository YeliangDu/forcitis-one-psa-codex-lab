#!/bin/bash
# 钩子启动器（GOV-1023-QUA-06）：从主检出运行 scripts/hooks/<名字>，而不是本工作区里可能落后几百个提交的副本。
# 用法：run_from_main.sh [--strict] [--stdout-warn] <钩子文件名> [参数…]；标准输入、环境和工作目录原样交给钩子。
#   --strict       钩子崩溃（退出码不是 0 或 2）按拦截处理（退出码 2）：PreToolUse 用，退出码 1 对它是「不拦」。不带时原样回传崩溃码并告警。
#   --stdout-warn  告警同时写标准输出：SessionStart 的标准输出会进模型上下文，别的事件的标准错误在退出码 0 时模型看不到。
# 主检出 = 启动器所在仓库的 git 公共目录的上级；定位只看 CLAUDE_PROJECT_DIR 与启动器自身位置，不看工作目录
#   （会话 cd 进别的仓库时，不能跑那个仓库里的同名钩子）。CLAUDE_PROJECT_DIR 指向别的仓库时，用启动器自己的副本并告警。
# 只在主检出处于 main 分支、scripts/hooks 没有未提交或未跟踪改动时才用它；否则退回本工作区的副本并告警（一处改坏不会让全员守护失败开放）。
# 所有退回、崩溃都追加到 <git 目录>/hooks-fallback.log，开工单（start_sheet.py）把当日的几行带给会话。
strict=0
warn_stdout=0
while [ $# -gt 0 ]; do
  case "$1" in
    --strict) strict=1; shift ;;
    --stdout-warn) warn_stdout=1; shift ;;
    *) break ;;
  esac
done
name="${1:-}"
[ $# -gt 0 ] && shift
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
failcode=1
[ "$strict" = 1 ] && failcode=2
case "$name" in
  ""|*/*|.*) echo "run_from_main.sh: 钩子名无效：'$name'" >&2; exit "$failcode" ;;
esac

own_gitdir="$(git -C "$here" rev-parse --absolute-git-dir 2>/dev/null)"
warn() {
  echo "run_from_main.sh: 告警：$1" >&2
  [ "$warn_stdout" = 1 ] && echo "【钩子告警】$name：$1"
  if [ -n "$own_gitdir" ] && [ -d "$own_gitdir" ]; then
    printf '%s %s %s\n' "$(date '+%Y-%m-%dT%H:%M:%S')" "$name" "$1" >>"$own_gitdir/hooks-fallback.log" 2>/dev/null
  fi
}

target="$here/$name"
own_common="$(git -C "$here" rev-parse --path-format=absolute --git-common-dir 2>/dev/null)"
anchor="${CLAUDE_PROJECT_DIR:-}"
anchor_common=""
[ -n "$anchor" ] && anchor_common="$(git -C "$anchor" rev-parse --path-format=absolute --git-common-dir 2>/dev/null)"
reason=""
if [ -z "$own_common" ] || [ "$(basename "$own_common")" != ".git" ]; then
  reason="找不到主检出（启动器所在 git 公共目录：'${own_common:-无}'）"
elif [ -n "$anchor" ] && [ "$anchor_common" != "$own_common" ]; then
  reason="CLAUDE_PROJECT_DIR（$anchor）不是启动器所在的仓库"
else
  main="$(dirname "$own_common")"
  head_ref="$(git -C "$main" symbolic-ref -q HEAD 2>/dev/null)"
  if [ "$head_ref" != "refs/heads/main" ]; then
    reason="主检出 $main 不在 main 分支（HEAD=${head_ref:-游离}）"
  elif [ -n "$(git -C "$main" status --porcelain -- scripts/hooks 2>/dev/null)" ] \
      || [ -n "$(git -C "$main" ls-files -v -- scripts/hooks 2>/dev/null | grep -v '^H ')" ] \
      || [ -n "$(git -C "$main" ls-files --others --ignored --exclude-standard -- scripts/hooks 2>/dev/null | grep -v '__pycache__')" ]; then
    reason="主检出 $main 的 scripts/hooks 有未提交或未跟踪改动"
  elif [ ! -x "$main/scripts/hooks/$name" ]; then
    reason="主检出 $main（$(git -C "$main" rev-parse --short HEAD 2>/dev/null)）没有可执行的 scripts/hooks/$name"
  else
    target="$main/scripts/hooks/$name"
  fi
fi
if [ -n "$reason" ]; then
  # the fallback is a workspace copy, never the main checkout's own unsafe copy: when this launcher lives in the main checkout (the desktop app points CLAUDE_PROJECT_DIR there),
  # the session's own worktree (the hook's working directory, accepted only when it belongs to the same repository) is the fallback, else the hook fails
  main_hooks="${main:+$main/scripts/hooks}"
  if [ -n "$main_hooks" ] && [ "$here" = "$main_hooks" ]; then
    target=""
    wt="$(git rev-parse --show-toplevel 2>/dev/null)"
    if [ -n "$wt" ] && [ "$wt" != "$main" ] && [ "$(git -C "$wt" rev-parse --path-format=absolute --git-common-dir 2>/dev/null)" = "$own_common" ] && [ -x "$wt/scripts/hooks/$name" ]; then
      target="$wt/scripts/hooks/$name"
      warn "$reason，退回会话自己的工作区 $wt 的副本（可能落后；主检出 HEAD $(git -C "$main" rev-parse --short HEAD 2>/dev/null)）"
    else
      warn "$reason，主检出的副本不安全且找不到会话自己的工作区副本，不执行钩子，按失败处理（主检出 HEAD $(git -C "$main" rev-parse --short HEAD 2>/dev/null)）"
      exit "$failcode"
    fi
  else
    warn "$reason，退回本工作区的副本（可能落后；主检出 HEAD $(git -C "${main:-.}" rev-parse --short HEAD 2>/dev/null || echo 无)）"
  fi
fi

if [ ! -x "$target" ]; then
  warn "钩子 $name 不存在或不可执行（$target），按失败处理"
  exit "$failcode"
fi
"$target" "$@"
code=$?
if [ "$code" -ne 0 ] && [ "$code" -ne 2 ]; then
  warn "钩子 $name 异常退出（退出码 $code，崩溃、语法错或解释器缺失），按失败处理"
  exit "$failcode"
fi
exit "$code"
