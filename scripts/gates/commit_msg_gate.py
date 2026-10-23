#!/usr/bin/python3
"""commit-msg hook: append `Session: <name>` when absent; never refuses a commit (D25, definitive 2.1).
Name: `.claude/session-name` if it is a known session, else the longest known name the worktree directory (or, when none, the branch without `claude/`)
equals or starts with plus `-`; known names are the *.md files in SESSION_DIRS. Unknown name, merge, fixup/squash or any error: warn, exit 0.
Usage: commit_msg_gate.py <message-file>  |  commit_msg_gate.py --self-test"""
import os
import re
import subprocess
import sys
from pathlib import Path

SESSION_DIRS = ("docs/modernization/sessions", "docs/modernization/roles")
MARKER = ".claude/session-name"
SKIP_TITLE = re.compile(r"^(Merge |fixup! |squash! |amend! )")


def git(*args):
    return subprocess.run(["git", *args], capture_output=True, text=True, encoding="utf-8", check=True).stdout.strip()


def session_name(root, branch):
    names = {f[:-3] for d in SESSION_DIRS if (root / d).is_dir() for f in os.listdir(root / d) if f.endswith(".md") and not f.startswith("_")}
    marker = (root / MARKER).read_text(encoding="utf-8").strip() if (root / MARKER).is_file() else ""
    if marker:
        return marker if marker in names else None
    for candidate in (root.name, branch.removeprefix("claude/")):   # the directory first: one string has at most one known prefix per length
        hits = [n for n in names if candidate == n or candidate.startswith(n + "-")]
        if hits:
            return max(hits, key=len)


def main(argv):
    if len(argv) != 1:
        raise ValueError("用法：commit_msg_gate.py <提交信息文件>")
    path = Path(argv[0])
    lines = [l for l in path.read_text(encoding="utf-8").split("\n") if not re.match(r"#($|[ \t])", l)]
    title = next((l for l in lines if l.strip()), "")
    if not title or SKIP_TITLE.match(title) or any(l.startswith("Session:") for l in lines):
        return 0
    root, git_dir = Path(git("rev-parse", "--show-toplevel")), Path(git("rev-parse", "--absolute-git-dir"))
    if any((git_dir / h).exists() for h in ("MERGE_HEAD", "REVERT_HEAD", "CHERRY_PICK_HEAD")):
        return 0
    name = session_name(root, git("rev-parse", "--abbrev-ref", "HEAD"))
    if not name:
        print(f"commit-msg 告警：本会话未识别，没有追加 Session（在 {MARKER} 写入会话名）", file=sys.stderr)
        return 0
    git("interpret-trailers", "--in-place", "--if-exists", "doNothing", "--trailer", f"Session: {name}", str(path))
    return 0


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
        from commit_msg_gate_selftest import self_test
        sys.exit(self_test())
    try:
        sys.exit(main(sys.argv[1:]))
    except Exception as error:
        print(f"commit-msg 告警：commit_msg_gate.py 出错（{error.__class__.__name__}: {error}），未追加 Session，提交照常", file=sys.stderr)
        sys.exit(0)
