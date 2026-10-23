#!/usr/bin/env python3
"""commit_msg_gate 的自测（临时独立文件，门本体须 ≤60 行；O8 合并进统一自测）。由 `commit_msg_gate.py --self-test` 调起，也可直接运行。
全部在临时目录里新建 git 仓库与 worktree，经 core.hooksPath 的 commit-msg 端到端提交。"""
import json
import os
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
GATE = os.path.join(HERE, "commit_msg_gate.py")
PYTHON = "/usr/bin/python3" if os.path.exists("/usr/bin/python3") else sys.executable


def env(**extra):
    base = {k: v for k, v in os.environ.items() if not k.startswith("GIT_")}
    return {**base, "GIT_CONFIG_GLOBAL": "/dev/null", "GIT_CONFIG_NOSYSTEM": "1", "GIT_AUTHOR_NAME": "t", "GIT_AUTHOR_EMAIL": "t@t",
            "GIT_COMMITTER_NAME": "t", "GIT_COMMITTER_EMAIL": "t@t", **extra}


def git(cwd, *args, ok=True, **extra):
    r = subprocess.run(["git", *args], cwd=cwd, capture_output=True, text=True, encoding="utf-8", env=env(**extra))
    assert not ok or r.returncode == 0, f"git {args}: {r.stderr}"
    return r


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)


def commit(cwd, message, name="a.py", **extra):
    """Commit one changed file with the hook installed; returns (exit code, stderr, final message)."""
    with open(os.path.join(cwd, name), "a", encoding="utf-8") as f:
        f.write("x\n")
    git(cwd, "add", name)
    r = git(cwd, "commit", "-q", "-m", message, ok=False, **extra)
    return r.returncode, r.stderr, git(cwd, "log", "-1", "--format=%B").stdout


def self_test():
    with tempfile.TemporaryDirectory() as raw:
        tmp = os.path.realpath(raw)
        repo = os.path.join(tmp, "repo")
        hooks = os.path.join(tmp, "hooks")
        write(os.path.join(hooks, "commit-msg"), f'#!/bin/sh\nexec {PYTHON} -I {GATE} "$1"\n')
        os.chmod(os.path.join(hooks, "commit-msg"), 0o755)
        os.makedirs(repo)
        git(repo, "init", "-q", "-b", "main")
        git(repo, "config", "core.hooksPath", hooks)
        for name in ("one-ops", "one-1a", "one-1a-next-3", "_retired"):
            write(os.path.join(repo, "docs/modernization/sessions", name + ".md"), "x\n")
        write(os.path.join(repo, "docs/modernization/roles/one-platform.md"), "x\n")
        write(os.path.join(repo, ".claude/session-name"), "one-ops\n")
        git(repo, "add", "-A")
        git(repo, "commit", "-q", "-m", "init", "--no-verify")

        code, err, msg = commit(repo, "feat: 改脚本，正文没有任务号也没有 PR 号")
        assert code == 0 and msg.rstrip().endswith("Session: one-ops"), "no task id or PR is accepted and gets the trailer: " + err + msg
        code, err, msg = commit(repo, "feat: x\n\n为什么：y\n\nCo-Authored-By: A <a@b>")
        assert code == 0 and "Co-Authored-By: A <a@b>\nSession: one-ops" in msg and msg.count("Session:") == 1, msg
        code, err, msg = commit(repo, "feat: x\n\nSession: one-1a")
        assert code == 0 and msg.count("Session:") == 1 and "Session: one-1a" in msg, "an existing Session is kept: " + msg
        code, err, msg = commit(repo, "fixup! feat: x")
        assert code == 0 and "Session:" not in msg, "fixup titles are left alone: " + msg
        code, err, msg = commit(repo, "#593 修复钩子")
        assert code == 0 and "Session: one-ops" in msg, "a title starting with #<number> is not a comment: " + msg

        write(os.path.join(repo, ".claude/session-name"), "one-nope\n")
        code, err, msg = commit(repo, "feat: 标记不是已知会话")
        assert code == 0 and "Session:" not in msg and "未识别" in err, "an unknown marker warns, never guesses: " + err + msg
        os.remove(os.path.join(repo, ".claude/session-name"))
        git(repo, "add", "-A")
        code, err, msg = commit(repo, "feat: 目录名 repo 认不出")
        assert code == 0 and "Session:" not in msg and "未识别" in err, err + msg

        wt = os.path.join(tmp, "one-1a-next-3-abc")
        git(repo, "worktree", "add", "-q", "-b", "claude/zzz", wt)
        code, err, msg = commit(wt, "feat: 按工作区目录名认", name="b.py")
        assert code == 0 and msg.rstrip().endswith("Session: one-1a-next-3"), "longest known prefix of the worktree directory: " + msg
        wt2 = os.path.join(tmp, "plain")
        git(repo, "worktree", "add", "-q", "-b", "claude/one-platform-r2", wt2)
        code, err, msg = commit(wt2, "feat: 按分支名认（roles 目录）", name="c.py")
        assert code == 0 and msg.rstrip().endswith("Session: one-platform"), "branch without claude/ prefix, names from roles/: " + msg

        git(repo, "checkout", "-q", "-b", "side")
        commit(repo, "side", name="d.py")
        git(repo, "checkout", "-q", "main")
        commit(repo, "main", name="e.py")
        write(os.path.join(repo, ".claude/session-name"), "one-ops\n")
        git(repo, "merge", "-q", "--no-ff", "side", "-m", "合并 side（标题不以 Merge 开头）")
        assert "Session:" not in git(repo, "log", "-1", "--format=%B").stdout, "a merge in progress is left alone"

        broken = os.path.join(tmp, "notgit")
        os.makedirs(broken)
        write(os.path.join(broken, "MSG"), "feat: x\n")
        r = subprocess.run([PYTHON, "-I", GATE, os.path.join(broken, "MSG")], cwd=broken, capture_output=True, text=True, env=env())
        assert r.returncode == 0 and "出错" in r.stderr, "an error warns and lets the commit through: " + r.stderr
        r = subprocess.run([PYTHON, "-I", GATE], cwd=broken, capture_output=True, text=True, env=env())
        assert r.returncode == 0 and "用法" in r.stderr, r.stderr

    with open(GATE, encoding="utf-8") as f:
        lines = len(f.read().splitlines())
    with open(os.path.join(ROOT, "policy.json"), encoding="utf-8") as f:
        limit = json.load(f)["gates"]["max_lines"]["commit_msg_gate"]
    assert lines <= limit, f"commit_msg_gate.py {lines} 行，超过 policy.json 的 {limit}"
    with open(os.path.join(ROOT, ".pre-commit-config.yaml"), encoding="utf-8") as f:
        config = f.read()
    assert "entry: python3 -I scripts/gates/commit_msg_gate.py" in config, ".pre-commit-config.yaml points at the new path"
    for gone in ("scripts/hooks/commit_msg_gate.py", "scripts/hooks/task_ids.py"):
        assert not os.path.exists(os.path.join(ROOT, gone)), gone + " is deleted"
    print("commit_msg_gate self-test: ok")
    return 0


if __name__ == "__main__":
    sys.exit(self_test())
