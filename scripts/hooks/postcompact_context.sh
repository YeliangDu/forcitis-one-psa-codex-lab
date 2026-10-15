#!/bin/bash
# SessionStart(compact|clear) hook: after compaction or /clear, re-inject the long-lived session's context document
# and its latest pre-compact snapshot, so key state survives beyond the compaction summary (SESSION_PLAYBOOK §2.1).
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

doc=""
while IFS=$'\t' read -r prefix path; do
  case "$prefix" in ''|'#'*) continue ;; esac
  case "$name" in "$prefix"*) doc="$path"; break ;; esac
done < "$map"

snapshot=$(ls -t "$HOME/.modernize/psa/handoff/"*"-${session_id}.md" 2>/dev/null | head -1)

if [ -n "$doc" ] && [ -f "$root/$doc" ]; then
  echo "【压缩或清空后恢复上下文】本会话的长期上下文文档 $doc 全文如下，以它为准接续工作；完成点后继续更新它。"
  echo
  cat "$root/$doc"
fi
if [ -n "$snapshot" ]; then
  echo
  echo "【压缩前快照】$snapshot"
  cat "$snapshot"
fi
exit 0
