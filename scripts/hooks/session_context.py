#!/usr/bin/env python3
"""Which session this checkout is, and the paths of its profile and context documents; start_sheet.py builds the start-up page from them (SESSION_PLAYBOOK TEAM-03、08, GOV-1022-COO-13).

Session name, first hit wins: the untracked marker <root>/.claude/session-name; otherwise the longest profile name that the
worktree directory name or the current branch without "claude/" equals or starts with followed by "-"; ended sessions listed
in sessions/_retired.md take part in the match, so an ended session whose name extends a live one is not mistaken for it.
Profiles are read from origin/main first (the worktree may be stale), then from the worktree; context documents are the
backticked paths in the profile's 上下文文档 section, read from the worktree first, then origin/main. A context path must be
relative, free of "..", and stay under the repository root after resolving links.
A line `交接源: <path>#<heading fragment>` in that section names where the session's handover lives (handoff_source()); start_sheet.py, the handover-node
instruction and the stop gate read it (build_sessions.py, which checked it, was deleted in O15).
Usage: session_context.py --self-test
"""
import os, re, subprocess, sys, tempfile

SESSIONS = "docs/modernization/sessions"
MARKER = ".claude/session-name"


def context_docs(profile_text):
    m = re.search(r"(?ms)^## 上下文文档\n(.*?)(?=^## |\Z)", profile_text)
    return re.findall(r"`([^`\s]+\.md)`", m.group(1)) if m else []


def handoff_source(profile_text):
    """(path, heading fragment) of the line `交接源: <repo-relative .md>#<heading fragment>` in the profile's 上下文文档 section, or None."""
    m = re.search(r"(?ms)^## 上下文文档\n(.*?)(?=^## |\Z)", profile_text)
    found = re.search(r"(?m)^[-*\s]*交接源[:：]\s*`?([^`\s#]+\.md)#([^`\n]+?)`?\s*$", m.group(1)) if m else None
    return (found.group(1), found.group(2).strip()) if found else None


def unsafe_reason(root, path):
    if os.path.isabs(path):
        return "是绝对路径"
    if ".." in path.replace("\\", "/").split("/"):
        return "含 .."
    base = os.path.realpath(root)
    if os.path.commonpath([base, os.path.realpath(os.path.join(base, path))]) != base:
        return "规范化后不在仓库根下"
    return None


def match(names, marker, candidates):
    if marker:
        return marker if marker in names else None
    best = None
    for c in candidates:
        for n in names:
            if (c == n or c.startswith(n + "-")) and (best is None or len(n) > len(best)):
                best = n
    return best


def retired_names(text):
    return {m.group(1) for m in re.finditer(r"(?m)^- (\S+)：", text or "")}


def git(root, *args):
    try:
        r = subprocess.run(["git", "-C", root, *args], capture_output=True, text=True, timeout=10)
    except subprocess.TimeoutExpired:
        print(f"session_context: git {' '.join(args[:2])} 超过 10 秒，按读不到处理", file=sys.stderr)
        return None
    return r.stdout if r.returncode == 0 else None


def from_main(root, path):
    return git(root, "show", f"origin/main:{path}")


def from_tree(root, path):
    full = os.path.join(root, path)
    return open(full, encoding="utf-8").read() if os.path.isfile(full) else None


def identify(root):
    """(name or None, retired names, marker text, branch) by the rules in the module docstring."""
    names = set()
    listed = git(root, "ls-tree", "--name-only", f"origin/main:{SESSIONS}")
    for f in (listed or "").split() + (os.listdir(os.path.join(root, SESSIONS)) if os.path.isdir(os.path.join(root, SESSIONS)) else []):
        if f.endswith(".md") and not f.startswith("_"):
            names.add(f[:-3])
    retired_path = f"{SESSIONS}/_retired.md"
    retired = retired_names(from_main(root, retired_path) or from_tree(root, retired_path)) - names
    marker_path = os.path.join(root, MARKER)
    marker = open(marker_path, encoding="utf-8").read().strip() if os.path.isfile(marker_path) else ""
    branch = (git(root, "rev-parse", "--abbrev-ref", "HEAD") or "").strip().removeprefix("claude/")
    return match(names | retired, marker, [os.path.basename(root), branch]), retired, marker, branch


def self_test():
    profile = "# one-x\n\n## 上下文文档\n\n- `docs/a.md`（速查）、`docs/b.md`\n- 不是路径 `foo`\n\n## 交接条件\n\n`docs/c.md`\n"
    assert context_docs(profile) == ["docs/a.md", "docs/b.md"], context_docs(profile)
    assert context_docs("# one-x\n") == []
    assert handoff_source("# one-x\n\n## 上下文文档\n\n- `docs/a.md`\n- 交接源: docs/a.md#交接\n\n## 交接条件\n") == ("docs/a.md", "交接")
    assert handoff_source("## 上下文文档\n交接源：`docs/led.md#2026 交接`\n") == ("docs/led.md", "2026 交接")
    assert handoff_source("## 上下文文档\n\n- `docs/a.md`\n\n## 其他\n交接源: docs/b.md#交接\n") is None, "only inside the 上下文文档 section"
    assert handoff_source("# one-x\n") is None
    names = {"one-1a-next-3", "one-1a", "one-ops"}
    assert match(names, "", ["one-1a-next-3-abc", "x"]) == "one-1a-next-3"
    assert match(names, "", ["agent-a1", "one-ops-r40"]) == "one-ops"
    assert match(names, "", ["one-opsx", "main"]) is None
    assert match(names, "one-ops", ["one-1a-next-3-abc"]) == "one-ops"
    assert match(names, "one-nope", ["one-ops"]) is None
    assert match({"one-glossary", "one-glossary-gen"}, "", ["one-glossary-gen-x"]) == "one-glossary-gen"
    assert match({"one-glossary", "one-ops"}, "", ["modest-torvalds-293503", "one-glossary-round3"]) == "one-glossary"
    assert retired_names("<!-- x -->\n- one-glossary-gen：一次性\n") == {"one-glossary-gen"}
    with tempfile.TemporaryDirectory() as tmp:
        root = os.path.join(tmp, "one-x-r1")
        outside = os.path.join(tmp, "secret.md")
        open(outside, "w").write("outside")
        os.makedirs(os.path.join(root, "docs"))
        os.symlink(outside, os.path.join(root, "docs", "link.md"))
        open(os.path.join(root, "docs", "a.md"), "w").write("CONTEXT-A")
        assert unsafe_reason(root, "docs/a.md") is None
        assert unsafe_reason(root, "/etc/hosts.md") == "是绝对路径"
        assert unsafe_reason(root, "docs/../../secret.md") == "含 .."
        assert unsafe_reason(root, "docs/link.md") == "规范化后不在仓库根下"
        os.makedirs(os.path.join(root, SESSIONS))
        open(os.path.join(root, SESSIONS, "one-x.md"), "w").write("# one-x\n")
        assert identify(root)[0] == "one-x"
    print("self-test: ok")


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        self_test()
    else:
        sys.exit(__doc__)
