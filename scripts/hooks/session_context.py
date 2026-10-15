#!/usr/bin/env python3
"""Print the session profile and its context documents for postcompact_context.sh (SESSION_PLAYBOOK §2.1 #13, #16).

Session name, first hit wins: the untracked marker <root>/.claude/session-name; otherwise the longest profile name that the
worktree directory name or the current branch without "claude/" equals or starts with followed by "-"; ended sessions listed
in sessions/_retired.md take part in the match, so one-glossary-gen is not mistaken for one-glossary.
Profiles are read from origin/main first (the worktree may be stale), then from the worktree; context documents are the
backticked paths in the profile's 上下文文档 section, read from the worktree first, then origin/main.
build_sessions.py imports context_docs() so the hook and the build check parse the section the same way.
Usage: session_context.py <repo-root>  |  session_context.py --self-test
"""
import os, re, subprocess, sys

SESSIONS = "docs/modernization/sessions"
MARKER = ".claude/session-name"


def context_docs(profile_text):
    m = re.search(r"(?ms)^## 上下文文档\n(.*?)(?=^## |\Z)", profile_text)
    return re.findall(r"`([^`\s]+\.md)`", m.group(1)) if m else []


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
    r = subprocess.run(["git", "-C", root, *args], capture_output=True, text=True)
    return r.stdout if r.returncode == 0 else None


def read(root, path, main_first):
    def from_main():
        return git(root, "show", f"origin/main:{path}")

    def from_tree():
        full = os.path.join(root, path)
        return open(full, encoding="utf-8").read() if os.path.isfile(full) else None

    first, second = (from_main, from_tree) if main_first else (from_tree, from_main)
    return first() or second()


def main(root):
    names = set()
    listed = git(root, "ls-tree", "--name-only", f"origin/main:{SESSIONS}")
    for f in (listed or "").split() + (os.listdir(os.path.join(root, SESSIONS)) if os.path.isdir(os.path.join(root, SESSIONS)) else []):
        if f.endswith(".md") and not f.startswith("_"):
            names.add(f[:-3])
    retired = retired_names(read(root, f"{SESSIONS}/_retired.md", main_first=True)) - names
    marker_path = os.path.join(root, MARKER)
    marker = open(marker_path, encoding="utf-8").read().strip() if os.path.isfile(marker_path) else ""
    branch = (git(root, "rev-parse", "--abbrev-ref", "HEAD") or "").strip().removeprefix("claude/")
    name = match(names | retired, marker, [os.path.basename(root), branch])
    if name in retired:
        print(f"【会话画像】本会话识别为已结束的 {name}（{SESSIONS}/_retired.md），没有画像；若不对，在 {MARKER}（不入仓）写入会话名。")
        return
    if not name:
        how = f"标记 {MARKER} 写的「{marker}」没有对应画像" if marker else f"目录名「{os.path.basename(root)}」与分支名「{branch}」都没有对应画像"
        print(f"【会话画像】未识别本会话：{how}；请手动读 {SESSIONS}/<会话名>.md，并可在 {MARKER}（不入仓）写入会话名。")
        return
    profile_path = f"{SESSIONS}/{name}.md"
    profile = read(root, profile_path, main_first=True) or f"（缺文件：{profile_path}）"
    print(f"【压缩或清空后恢复上下文】本会话（{name}）的画像与上下文文档全文如下，以它们为准接续工作；完成点后继续更新。")
    print(f"\n===== {profile_path} =====")
    print(profile.rstrip("\n"))
    for doc in context_docs(profile):
        if doc == profile_path:
            continue
        text = read(root, doc, main_first=False)
        print(f"\n===== {doc} =====")
        print(text.rstrip("\n") if text else f"（缺文件：{doc}，按 SESSION_PLAYBOOK §2.1 第 16 条补齐）")


def self_test():
    profile = "# one-x\n\n## 上下文文档\n\n- `docs/a.md`（速查）、`docs/b.md`\n- 不是路径 `foo`\n\n## 交接条件\n\n`docs/c.md`\n"
    assert context_docs(profile) == ["docs/a.md", "docs/b.md"], context_docs(profile)
    assert context_docs("# one-x\n") == []
    names = {"one-1a-next-3", "one-1a", "one-ops"}
    assert match(names, "", ["one-1a-next-3-abc", "x"]) == "one-1a-next-3"
    assert match(names, "", ["agent-a1", "one-ops-r40"]) == "one-ops"
    assert match(names, "", ["one-opsx", "main"]) is None
    assert match(names, "one-ops", ["one-1a-next-3-abc"]) == "one-ops"
    assert match(names, "one-nope", ["one-ops"]) is None
    assert match({"one-glossary", "one-glossary-gen"}, "", ["one-glossary-gen-x"]) == "one-glossary-gen"
    assert retired_names("<!-- x -->\n- one-glossary-gen：一次性\n") == {"one-glossary-gen"}
    print("self-test: ok")


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        self_test()
    elif len(sys.argv) == 2:
        main(sys.argv[1])
    else:
        sys.exit(__doc__)
