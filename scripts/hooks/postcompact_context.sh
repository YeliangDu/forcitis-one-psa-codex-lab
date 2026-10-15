#!/bin/bash
# SessionStart(startup|clear|compact) hook: on a new session (the desktop app's /clear starts one), /clear or compaction, re-inject the session's profile and the context documents
# it lists (session_context.py), plus its latest pre-compact snapshot, so key state survives beyond the compaction summary
# (SESSION_PLAYBOOK §2.1 #13, #16).
set -u
input=$(cat)
field() { printf '%s' "$input" | python3 -I -c "import json,sys; print(json.load(sys.stdin).get('$1',''))" 2>/dev/null; }
session_id=$(field session_id)
cwd=$(field cwd)
[ -n "$cwd" ] || cwd=$PWD
root=$(git -C "$cwd" rev-parse --show-toplevel 2>/dev/null) || exit 0

if [ -f "$root/scripts/hooks/session_context.py" ]; then
  python3 -I "$root/scripts/hooks/session_context.py" "$root"
fi

snapshot=$(ls -t "$HOME/.modernize/psa/handoff/"*"-${session_id}.md" 2>/dev/null | head -1)
if [ -n "$snapshot" ]; then
  echo
  echo "【压缩前快照】${snapshot}"
  cat "$snapshot"
fi
exit 0
