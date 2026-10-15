#!/bin/bash
# PreCompact hook: before a session auto-compacts, save the mechanical state a successor needs
# (SESSION_PLAYBOOK §2.1). Writes outside the repo so nothing private lands in git; never prints secrets.
set -u
input=$(cat)
field() { printf '%s' "$input" | python3 -I -c "import json,sys; print(json.load(sys.stdin).get('$1',''))" 2>/dev/null; }
session_id=$(field session_id)
transcript=$(field transcript_path)
trigger=$(field trigger)
cwd=$(field cwd)
[ -n "$cwd" ] || cwd=$PWD

dir="$HOME/.modernize/psa/handoff"
mkdir -p "$dir" && chmod 700 "$dir"
out="$dir/$(date +%Y%m%d-%H%M%S)-${session_id:-unknown}.md"

{
  echo "# 压缩前快照（$trigger）"
  echo
  echo "- 时间：$(date '+%Y-%m-%d %H:%M:%S %z')"
  echo "- 会话：${session_id:-未知}"
  echo "- 会话记录：${transcript:-未知}"
  echo "- 工作目录：$cwd"
  if git -C "$cwd" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    branch=$(git -C "$cwd" rev-parse --abbrev-ref HEAD)
    echo "- 分支：$branch"
    echo
    echo "## 未提交改动"
    git -C "$cwd" -c core.quotepath=off status --short | head -50
    echo
    echo "## 最近提交"
    git -C "$cwd" log --oneline -10
    if command -v gh >/dev/null 2>&1; then
      echo
      echo "## 本分支 PR"
      (cd "$cwd" && gh pr list --head "$branch" --state all --json number,title,state \
        --jq '.[] | "#\(.number) [\(.state)] \(.title)"' 2>/dev/null)
    fi
  fi
} > "$out" 2>/dev/null
chmod 600 "$out"
echo "压缩前快照已写入 $out；压缩后先读它，并按 SESSION_PLAYBOOK §2.1 判断是否交接。"
exit 0
