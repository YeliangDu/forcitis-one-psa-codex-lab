#!/bin/bash
# SessionStart: git must not walk up from .claude/worktrees/ to the main checkout (`cd ..` out of a worktree, then `git commit`: 2026-10-21).
# Puts <main checkout>/.claude into GIT_CEILING_DIRECTORIES for every Bash command of the session, through CLAUDE_ENV_FILE. Idempotent: a line already there is not added again.
[ -n "${CLAUDE_ENV_FILE:-}" ] && [ -n "${CLAUDE_PROJECT_DIR:-}" ] || exit 0
main_checkout=$(git -C "$CLAUDE_PROJECT_DIR" worktree list --porcelain 2>/dev/null | sed -n '1s/^worktree //p')
[ -n "$main_checkout" ] && [ -d "$main_checkout/.claude/worktrees" ] || exit 0
line=$(printf 'export GIT_CEILING_DIRECTORIES=%q' "$main_checkout/.claude${GIT_CEILING_DIRECTORIES:+:$GIT_CEILING_DIRECTORIES}")
grep -qxF -- "$line" "$CLAUDE_ENV_FILE" 2>/dev/null || printf '%s\n' "$line" >>"$CLAUDE_ENV_FILE"
exit 0
