#!/bin/bash
hook_dir=$(CDPATH= cd -- "${BASH_SOURCE[0]%/*}" 2>/dev/null && pwd) || {
  printf '%s\n' '【交接快照】告警：找不到钩子目录；请手动交接。'
  exit 0
}
python3 -I "$hook_dir/handoff_snapshot.py" --start 2>/dev/null || printf '%s\n' '【交接快照】告警：恢复脚本不可用；请手动读取上下文。'
# git must not walk up from .claude/worktrees/ to the main checkout (`cd ..` out of a worktree, then `git commit`: 2026-10-21): ceiling at <main checkout>/.claude for every Bash command of the session
if [ -n "${CLAUDE_ENV_FILE:-}" ] && [ -n "${CLAUDE_PROJECT_DIR:-}" ]; then
  main_checkout=$(git -C "$CLAUDE_PROJECT_DIR" worktree list --porcelain 2>/dev/null | sed -n '1s/^worktree //p')
  if [ -n "$main_checkout" ] && [ -d "$main_checkout/.claude/worktrees" ]; then
    printf 'export GIT_CEILING_DIRECTORIES=%q\n' "$main_checkout/.claude${GIT_CEILING_DIRECTORIES:+:$GIT_CEILING_DIRECTORIES}" >>"$CLAUDE_ENV_FILE"
  fi
fi
exit 0
