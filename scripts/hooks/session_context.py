#!/usr/bin/env python3
"""Print the session profile and its context documents for postcompact_context.sh (SESSION_PLAYBOOK §2.1 #13, #16).

Session name, first hit wins: the untracked marker <root>/.claude/session-name; otherwise the longest profile name that the
worktree directory name or the current branch without "claude/" equals or starts with followed by "-"; ended sessions listed
in sessions/_retired.md take part in the match, so an ended session whose name extends a live one is not mistaken for it.
Profiles are read from origin/main first (the worktree may be stale), then from the worktree; context documents are the
backticked paths in the profile's 上下文文档 section, read from the worktree first, then origin/main. A context path must be
relative, free of "..", and stay under the repository root after resolving links; any other path is refused with a warning.
build_sessions.py imports context_docs() and unsafe_reason() so the hook and the build check judge paths the same way.
Every failure prints one 【会话画像】 line to stdout (the hook's output reaches the model) and the hook still exits 0.
Usage: session_context.py <repo-root>  |  session_context.py --self-test
"""
import contextlib, io, json, os, re, subprocess, sys, tempfile

SESSIONS = "docs/modernization/sessions"
MARKER = ".claude/session-name"


def context_docs(profile_text):
    m = re.search(r"(?ms)^## 上下文文档\n(.*?)(?=^## |\Z)", profile_text)
    return re.findall(r"`([^`\s]+\.md)`", m.group(1)) if m else []


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
    r = subprocess.run(["git", "-C", root, *args], capture_output=True, text=True)
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


def main(root):
    name, retired, marker, branch = identify(root)
    retired_path = f"{SESSIONS}/_retired.md"
    if name in retired:
        print(f"【会话画像】本会话识别为已结束的 {name}（{retired_path}），没有画像；若不对，在 {MARKER}（不入仓）写入会话名。")
        return
    if not name:
        how = f"标记 {MARKER} 写的「{marker}」没有对应画像" if marker else f"目录名「{os.path.basename(root)}」与分支名「{branch}」都没有对应画像"
        print(f"【会话画像】未识别本会话：{how}；请手动读 {SESSIONS}/<会话名>.md，并可在 {MARKER}（不入仓）写入会话名。")
        return
    profile_path = f"{SESSIONS}/{name}.md"
    profile = from_main(root, profile_path)
    if profile is None:
        print("【会话画像】未能读取 origin/main，改用工作区画像。")
        profile = from_tree(root, profile_path) or f"（缺文件：{profile_path}）"
    print(f"【新开、清空或压缩后恢复上下文】本会话（{name}）的画像与上下文文档全文如下，以它们为准接续工作；完成点后继续更新。")
    print(f"\n===== {profile_path} =====")
    print(profile.rstrip("\n"))
    for doc in context_docs(profile):
        if doc == profile_path:
            continue
        reason = unsafe_reason(root, doc)
        if reason:
            print(f"\n【会话画像】拒绝注入上下文文档 {doc}：{reason}；画像里只能写仓库根相对路径。")
            continue
        text = from_tree(root, doc) or from_main(root, doc)
        print(f"\n===== {doc} =====")
        print(text.rstrip("\n") if text else f"（缺文件：{doc}，按 SESSION_PLAYBOOK §2.1 第 16 条补齐）")


def state_of(profile_text):
    m = re.search(r"(?ms)^## 当前状态\n+(.*?)(?=^## |\Z)", profile_text)
    return " ".join(m.group(1).split()) if m else ""


def brief(root):
    """JSON-able summary for the start-up head: session name, profile path, 当前状态 text and the context document paths (handoff_snapshot.py --start)."""
    name, retired, marker, branch = identify(root)
    if not name or name in retired:
        return {"name": None, "reason": "已结束会话，没有画像" if name else "未识别本会话", "profile": None, "state": "", "docs": []}
    profile_path = f"{SESSIONS}/{name}.md"
    profile = from_main(root, profile_path) or from_tree(root, profile_path) or ""
    docs = [doc for doc in context_docs(profile) if doc != profile_path and not unsafe_reason(root, doc)]
    return {"name": name, "reason": "", "profile": profile_path, "state": state_of(profile), "docs": docs}


def run(root):
    try:
        main(root)
    except Exception as e:
        print(f"【会话画像】注入失败（{type(e).__name__}: {e}）；请手动读 {SESSIONS}/<会话名>.md。")


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
    assert match({"one-glossary", "one-ops"}, "", ["modest-torvalds-293503", "one-glossary-round3"]) == "one-glossary"
    assert retired_names("<!-- x -->\n- one-glossary-gen：一次性\n") == {"one-glossary-gen"}
    with tempfile.TemporaryDirectory() as tmp:
        root = os.path.join(tmp, "one-x-r1")
        outside = os.path.join(tmp, "secret.md")
        open(outside, "w").write("outside")
        os.makedirs(os.path.join(root, SESSIONS))
        open(os.path.join(root, "docs", "a.md"), "w").write("CONTEXT-A")
        os.symlink(outside, os.path.join(root, "docs", "link.md"))
        assert unsafe_reason(root, "docs/a.md") is None
        assert unsafe_reason(root, "/etc/hosts.md") == "是绝对路径"
        assert unsafe_reason(root, "docs/../../secret.md") == "含 .."
        assert unsafe_reason(root, "docs/link.md") == "规范化后不在仓库根下"
        open(os.path.join(root, SESSIONS, "one-x.md"), "w").write(
            "# one-x\n\n## 上下文文档\n\n- `docs/a.md`、`/etc/x.md`、`../secret.md`、`docs/link.md`\n")
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            run(root)
        text = out.getvalue()
        assert "未能读取 origin/main，改用工作区画像" in text, text
        assert "CONTEXT-A" in text and "outside" not in text, text
        assert text.count("拒绝注入上下文文档") == 3, text
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            run(None)
        assert out.getvalue().startswith("【会话画像】注入失败（TypeError"), out.getvalue()
        with open(os.path.join(root, SESSIONS, "one-x.md"), "w") as profile_file:
            profile_file.write("# one-x\n\n## 上下文文档\n\n- `docs/a.md`、`/etc/x.md`\n\n## 当前状态\n\n第一行\n第二行\n\n## 权限边界\n\n无\n")
        summary = brief(root)
        assert summary == {"name": "one-x", "reason": "", "profile": f"{SESSIONS}/one-x.md", "state": "第一行 第二行", "docs": ["docs/a.md"]}, summary
        stranger = os.path.join(tmp, "somewhere")
        os.makedirs(stranger)
        assert brief(stranger)["name"] is None and brief(stranger)["reason"] == "未识别本会话"
    print("self-test: ok")


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        self_test()
    elif len(sys.argv) == 3 and sys.argv[1] == "--brief":
        print(json.dumps(brief(sys.argv[2]), ensure_ascii=False))
    elif len(sys.argv) == 2:
        run(sys.argv[1])
    else:
        sys.exit(__doc__)
