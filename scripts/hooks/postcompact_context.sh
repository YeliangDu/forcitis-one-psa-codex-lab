#!/bin/bash
# SessionStart(compact|clear) hook: after compaction or /clear, re-inject the session's profile and (for long-lived
# sessions) its context document, plus its latest pre-compact snapshot, so key state survives beyond the compaction summary (SESSION_PLAYBOOK §2.1 #13, #16).
set -u
input=$(cat)
field() { printf '%s' "$input" | python3 -I -c "import json,sys; print(json.load(sys.stdin).get('$1',''))" 2>/dev/null; }
session_id=$(field session_id)
cwd=$(field cwd)
[ -n "$cwd" ] || cwd=$PWD
root=$(git -C "$cwd" rev-parse --show-toplevel 2>/dev/null) || exit 0
name=$(basename "$root")
map="$root/scripts/hooks/session_context.map"
[ -f "$map" ] || exit 0

# 取与 worktree 目录名匹配的最长会话名（目录名等于会话名或以「会话名-」开头），注入它在映射中的全部文档（画像与上下文文档）
best=""
docs=""
while IFS=$'\t' read -r prefix path; do
  case "$prefix" in ''|'#'*) continue ;; esac
  case "$name" in "$prefix"|"$prefix"-*) ;; *) continue ;; esac
  if [ ${#prefix} -gt ${#best} ]; then
    best=$prefix
    docs=$path
  elif [ "$prefix" = "$best" ]; then
    docs=$docs$'\n'$path
  fi
done < "$map"

snapshot=$(ls -t "$HOME/.modernize/psa/handoff/"*"-${session_id}.md" 2>/dev/null | head -1)

if [ -n "$best" ]; then
  echo "【压缩或清空后恢复上下文】本会话（${best}）的画像与上下文文档全文如下，以它们为准接续工作；完成点后继续更新。"
  while IFS= read -r doc; do
    [ -f "$root/$doc" ] || { echo; echo "（缺文件：${doc}，按 SESSION_PLAYBOOK §2.1 第 16 条补齐）"; continue; }
    echo
    echo "===== ${doc} ====="
    cat "$root/$doc"
  done <<< "$docs"
fi
if [ -n "$snapshot" ]; then
  echo
  echo "【压缩前快照】$snapshot"
  cat "$snapshot"
fi
exit 0
