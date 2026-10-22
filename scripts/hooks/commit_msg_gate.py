#!/usr/bin/env python3
"""commit-msg hook (pre-commit stage commit-msg): append `Session: <name>`, require a task id or PR number on code commits, warn on a missing why-line.

SESSION_PLAYBOOK §5 任务命名与登记「提交信息」(intent 2026-10-21-212849):
  1. `Session: <会话名>` is appended as a trailer when absent. The name comes from session_context.identify() (marker file, then worktree/branch);
     an unidentified session gets a warning and no trailer, never a guessed one.
  2. A commit that stages a code path (backend/, frontend/, scripts/, .github/, .pre-commit-config.yaml) must name a task id (XXX-MMDD-XXX-NN) or a PR (#123)
     in the message, else the commit is refused (exit 1). Documentation-only commits and merge/fixup/squash commits are not checked.
  3. Warnings only (exit 0): no body line beyond the title and trailers (the why), or a code commit whose body cites no basis (ADR-NNNN, intent file, #PR, task id).
`git commit --amend` only sees the newly staged paths, so an amend that adds code to a docs-only commit is not caught there; the push gate still runs precheck.
Usage: commit_msg_gate.py <message-file>  |  commit_msg_gate.py --self-test
"""
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

CODE_PATHS = re.compile(r"^(backend/|frontend/|scripts/|\.github/|\.pre-commit-config\.yaml$|\.claude/settings\.json$)")
TASK_ID = re.compile(r"\b[A-Z]{2,3}-\d{4}-[A-Z0-9]{3}-\d{2}\b")
PR_REF = re.compile(r"(?<![\w/])#\d+\b|/pull/\d+")
BASIS = re.compile(r"ADR-\d{4}|\b20\d\d-\d\d-\d\d-\d{6}|intent|" + TASK_ID.pattern + r"|" + PR_REF.pattern)
SKIP_TITLE = re.compile(r"^(Merge |fixup! |squash! |amend! )")
TRAILER = re.compile(r"^[A-Z][A-Za-z0-9-]*:\s")
NO_ID = ("代码提交（改了 backend/、frontend/、scripts/ 或 .github/）须在提交信息里带任务号（如 CI-1022-QUA-03）或 PR 号（如 #589）；"
         "文档提交不强制（SESSION_PLAYBOOK §5 任务命名与登记）。请补上后重新提交。")


def git(*args, cwd=None):
    result = subprocess.run(["git", *args], cwd=cwd, capture_output=True, text=True, encoding="utf-8")
    if result.returncode:
        raise RuntimeError(f"git {' '.join(args)}: {result.stderr.strip()}")
    return result.stdout


def message_lines(text):
    """Lines of the message without git's # comment lines and everything below a scissors line."""
    out = []
    for line in text.split("\n"):
        if line.startswith("# ------------------------ >8"):
            break
        if not line.startswith("#"):
            out.append(line)
    return out


def split_message(text):
    """(title, body lines, trailer lines): the trailer block is the last paragraph when every line is `Key: value` and it is not the title."""
    lines = message_lines(text)
    while lines and not lines[-1].strip():
        lines.pop()
    paragraphs, current = [], []
    for line in lines:
        if line.strip():
            current.append(line)
        elif current:
            paragraphs.append(current)
            current = []
    if current:
        paragraphs.append(current)
    if not paragraphs:
        return "", [], []
    title = paragraphs[0][0]
    rest = ([paragraphs[0][1:]] if len(paragraphs[0]) > 1 else []) + paragraphs[1:]
    trailers = []
    if len(paragraphs) > 1 and all(TRAILER.match(line) for line in paragraphs[-1]):
        trailers = paragraphs[-1]
        rest = rest[:-1]
    return title, [line for paragraph in rest for line in paragraph], trailers


def staged_paths(cwd):
    return [p for p in git("diff", "--cached", "--name-only", "-z", cwd=cwd).split("\0") if p]


def session_name(root):
    import session_context
    name = session_context.identify(root)[0]
    return name


def merge_in_progress(root):
    git_dir = Path(git("rev-parse", "--absolute-git-dir", cwd=root).strip())
    return (git_dir / "MERGE_HEAD").exists()


def check(text, paths, name, merging=False):
    """(new text, errors, warnings)."""
    title, body, trailers = split_message(text)
    if not title:
        return text, [], []
    if SKIP_TITLE.match(title) or merging:
        return text, [], []
    errors, warnings = [], []
    code = [p for p in paths if CODE_PATHS.match(p)]
    visible = "\n".join(message_lines(text))
    if code and not (TASK_ID.search(visible) or PR_REF.search(visible)):
        errors.append(NO_ID)
    if not body:
        warnings.append("提交信息只有标题：正文请写一行为什么这样改，并引用依据（intent 文件名、ADR 编号、PR 号或任务号）。")
    elif code and not BASIS.search("\n".join(body)):
        warnings.append("代码提交正文没有引用依据：请在正文引用 intent 文件名、ADR 编号、PR 号或任务号。")
    if any(line.startswith("Session:") for line in trailers):
        return text, errors, warnings
    if not name:
        warnings.append("本会话未识别，没有追加 `Session:`：在 .claude/session-name 写入会话名（SESSION_PLAYBOOK §2.1）。")
        return text, errors, warnings
    return text, errors, warnings


def append_trailer(path, name, root):
    result = subprocess.run(["git", "interpret-trailers", "--in-place", "--if-exists", "doNothing", "--trailer", f"Session: {name}", str(path)],
                            cwd=root, capture_output=True, text=True, encoding="utf-8")
    if result.returncode:
        raise RuntimeError(f"git interpret-trailers: {result.stderr.strip()}")


def main(argv):
    if len(argv) != 1:
        print("用法：commit_msg_gate.py <提交信息文件>", file=sys.stderr)
        return 1
    path = Path(argv[0])
    text = path.read_text(encoding="utf-8")
    root = git("rev-parse", "--show-toplevel").strip()
    paths = staged_paths(root)
    name = session_name(root)
    _, errors, warnings = check(text, paths, name, merge_in_progress(root))
    for warning in warnings:
        print(f"commit-msg 告警：{warning}", file=sys.stderr)
    if errors:
        for error in errors:
            print(f"commit-msg 拒绝：{error}", file=sys.stderr)
        return 1
    title, _, trailers = split_message(text)
    if name and title and not SKIP_TITLE.match(title) and not merge_in_progress(root) and not any(l.startswith("Session:") for l in trailers):
        append_trailer(path, name, root)
    return 0


def guarded_main(argv):
    try:
        return main(argv)
    except Exception as error:
        print(f"commit_msg_gate.py 出错（{error.__class__.__name__}: {error}），按失败处理", file=sys.stderr)
        return 1


def self_test():
    docs = ["NOTES.md", "README.md"]
    code = ["scripts/hooks/x.py"]
    ok = "feat(x): 做某事 (CI-1022-QUA-03)\n\n为什么：见 ADR-0038。\n"
    _, e, w = check(ok, code, "one-quality")
    assert not e and not w, (e, w)
    _, e, _ = check("feat(x): 改脚本\n\n为什么：ADR-0038。\n", code, "one-quality")
    assert e, "code commit without task id or PR is refused"
    _, e, _ = check("fix: 改 #593 的钩子\n\n为什么：intent。\n", code, "one-quality")
    assert not e, "a PR number is accepted"
    _, e, w = check("docs: 改说明\n\n为什么：y。\n", docs, "one-quality")
    assert not e and not w, "docs-only commits need no id"
    _, e, w = check("docs: 改说明\n", docs, "one-quality")
    assert not e and w, "no body is a warning, not an error"
    _, e, w = check("feat: x (CI-1022-QUA-03)\n\n随便写了点。\n", code, "one-quality")
    assert not e and w, "code commit without a cited basis warns"
    for title in ("Merge branch 'main' into x", "fixup! feat: x", "squash! feat: x"):
        _, e, w = check(title + "\n", code, "one-quality")
        assert not e and not w, title
    _, e, w = check("feat: x\n", code, "one-quality", merging=True)
    assert not e and not w, "a commit during a merge is not checked"
    _, e, w = check("feat: x (CI-1022-QUA-03)\n\n为什么：ADR-0038。\n", code, None)
    assert not e and any("未识别" in x for x in w), "an unidentified session warns"
    _, e, w = check("# comment only\n", code, "one-quality")
    assert not e and not w, "an empty message is git's business"
    _, e, w = check("feat: x\n\n为什么：y\n# 注释里的 CI-1022-QUA-03 不算\n", code, "one-quality")
    assert e, "ids in # comment lines do not count"
    _, body, trailers = split_message("t\n\nwhy\n\nCo-Authored-By: A <a@b>\nSession: s\n")
    assert body == ["why"] and trailers == ["Co-Authored-By: A <a@b>", "Session: s"], (body, trailers)
    _, body, trailers = split_message("t\n\nwhy: because\n")
    assert body == ["why: because"] and not trailers, "the single body paragraph is not mistaken for a trailer block"
    with tempfile.TemporaryDirectory() as tmp:
        env = {**os.environ, "GIT_CONFIG_GLOBAL": "/dev/null", "GIT_CONFIG_SYSTEM": "/dev/null"}
        def run(*a, check_=True):
            r = subprocess.run(["git", *a], cwd=tmp, env=env, capture_output=True, text=True, encoding="utf-8")
            assert not check_ or r.returncode == 0, r
            return r
        run("init", "-q"); run("config", "user.email", "t@t"); run("config", "user.name", "t")
        os.makedirs(f"{tmp}/.claude"); Path(f"{tmp}/.claude/session-name").write_text("one-quality\n")
        import session_context
        os.makedirs(f"{tmp}/{session_context.SESSIONS}"); Path(f"{tmp}/{session_context.SESSIONS}/one-quality.md").write_text("x")
        os.makedirs(f"{tmp}/scripts"); Path(f"{tmp}/scripts/a.py").write_text("x"); Path(f"{tmp}/n.md").write_text("x")
        run("add", "-A")
        msg = Path(tmp) / "MSG"
        script = str(Path(__file__).resolve())
        def hook(text):
            msg.write_text(text, encoding="utf-8")
            r = subprocess.run([sys.executable, "-I", script, str(msg)], cwd=tmp, env=env, capture_output=True, text=True, encoding="utf-8")
            return r, msg.read_text(encoding="utf-8")
        r, out = hook("feat: x (CI-1022-QUA-03)\n\n为什么：ADR-0038。\n\nCo-Authored-By: A <a@b>\n")
        assert r.returncode == 0 and out.rstrip().endswith("Session: one-quality"), (r, out)
        assert out.count("Session:") == 1 and "Co-Authored-By: A <a@b>\nSession: one-quality" in out, out
        r, out2 = hook(out)
        assert r.returncode == 0 and out2.count("Session:") == 1, "rerunning is idempotent"
        r, out = hook("feat: x\n\n为什么：ADR-0038。\n")
        assert r.returncode == 1 and "拒绝" in r.stderr and "Session:" not in out, (r, out)
        run("reset", "-q"); run("add", "n.md")
        r, out = hook("docs: x\n\n为什么：y。\n")
        assert r.returncode == 0 and out.rstrip().endswith("Session: one-quality"), (r, out)
    print("commit_msg_gate self-test: ok")
    return 0


if __name__ == "__main__":
    sys.exit(self_test() if sys.argv[1:] == ["--self-test"] else guarded_main(sys.argv[1:]))
