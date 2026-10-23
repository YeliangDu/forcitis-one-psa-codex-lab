#!/usr/bin/env python3
"""main_checkout_gate 的自测（临时独立文件，门本体须 ≤120 行；O8 合并进统一自测）。由 `main_checkout_gate.py --self-test` 调起，也可直接运行。
全部在临时目录里新建 git 仓库与 worktree，不碰真实主检出。另含原 scripts/hooks/main_checkout_gate.py 里 git_ceiling_env.sh 的自测（随旧文件删除搬来）。"""
import json
import os
import re
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
GATE = os.path.join(HERE, "main_checkout_gate.py")
REFUSED = "拒绝：这是在主检出"
FAILED = "主检出提交保护出错"


def env(**extra):
    base = {k: v for k, v in os.environ.items() if not k.startswith("GIT_")}
    return {**base, "GIT_CONFIG_GLOBAL": "/dev/null", "GIT_CONFIG_NOSYSTEM": "1", "GIT_AUTHOR_NAME": "t", "GIT_AUTHOR_EMAIL": "t@t",
            "GIT_COMMITTER_NAME": "t", "GIT_COMMITTER_EMAIL": "t@t", **extra}


def run(*args, cwd, **extra):
    return subprocess.run(args, cwd=cwd, capture_output=True, text=True, env=env(**extra))


def git(cwd, *args):
    r = run("git", *args, cwd=cwd)
    assert r.returncode == 0, f"git {args}: {r.stderr}"
    return r


def gate(cwd, **extra):
    r = run("/usr/bin/python3" if os.path.exists("/usr/bin/python3") else sys.executable, "-I", GATE, "--pre-commit", cwd=cwd, **extra)
    return r.returncode, r.stderr


def self_test():
    with tempfile.TemporaryDirectory() as raw:
        d = os.path.realpath(raw)
        main = os.path.join(d, "repo")
        os.makedirs(main)
        git(main, "init", "-q", "-b", "main")
        git(main, "commit", "-q", "--allow-empty", "-m", "one")
        between = os.path.join(main, ".claude", "worktrees")
        work = os.path.join(between, "w1")
        git(main, "worktree", "add", "-q", work, "-b", "side")

        # 1. the verdicts, asked directly
        code, message = gate(main)
        assert code == 1 and REFUSED in message and main in message, f"main checkout on main must be refused: {code} {message}"
        code, message = gate(between)
        assert code == 1 and REFUSED in message, "from .claude/worktrees/ itself git finds the main checkout (the 2026-10-21 incident)"
        assert gate(os.path.join(main, ".claude"))[0] == 1, "a subdirectory of the main checkout is the main checkout"
        assert gate(work) == (0, ""), "a commit in a linked worktree goes ahead"
        git(main, "checkout", "-q", "-b", "elsewhere")
        code, message = gate(main)
        assert code == 1 and REFUSED in message and "refs/heads/elsewhere" in message, "the main checkout on another branch is refused too (intent 225957)"
        w2 = os.path.join(between, "w2")
        git(main, "worktree", "add", "-q", w2, "main")
        assert gate(w2)[0] == 0, "a linked worktree that has main checked out is not the main checkout"
        git(main, "worktree", "remove", "--force", w2)
        git(main, "checkout", "-q", "--detach", "main")
        assert gate(main)[0] == 1, "detached HEAD in the main checkout is refused: commit, then update-ref main, moves main in two steps"
        git(main, "checkout", "-q", "main")

        # 2. fails closed: no repository, no git, git that says nonsense or fails
        lonely = os.path.join(d, "lonely")
        os.makedirs(lonely)
        code, message = gate(lonely, GIT_CEILING_DIRECTORIES=d)
        assert code == 1 and FAILED in message, f"no repository: {code} {message}"
        empty = os.path.join(d, "empty-bin")
        os.makedirs(empty)
        code, message = gate(main, PATH=empty)
        assert code == 1 and FAILED in message and "git 跑不起来" in message, f"git not found: {code} {message}"
        fake = os.path.join(d, "fake-bin")
        os.makedirs(fake)
        for name, body in (("garbage", "echo only-one-line"), ("broken", "echo boom >&2; exit 128")):
            with open(os.path.join(fake, "git"), "w", encoding="utf-8") as f:
                f.write(f"#!/bin/sh\n{body}\n")
            os.chmod(os.path.join(fake, "git"), 0o755)
            code, message = gate(main, PATH=fake + os.pathsep + "/bin:/usr/bin")
            assert code == 1 and FAILED in message, f"fake git ({name}) must be refused: {code} {message}"

        # 3. end to end: a real `git commit` through a pre-commit hook (git sets GIT_DIR / GIT_INDEX_FILE for hooks)
        hooks = os.path.join(d, "hooks")
        os.makedirs(hooks)
        with open(os.path.join(hooks, "pre-commit"), "w", encoding="utf-8") as f:
            f.write(f"#!/bin/sh\nexec python3 -I '{GATE}' --pre-commit\n")
        os.chmod(os.path.join(hooks, "pre-commit"), 0o755)
        git(main, "config", "core.hooksPath", hooks)
        before = git(main, "rev-parse", "main").stdout
        r = run("git", "commit", "-q", "--allow-empty", "-m", "x", cwd=main)
        assert r.returncode != 0 and REFUSED in r.stderr and git(main, "rev-parse", "main").stdout == before, f"real commit on the main checkout's main must fail: {r.stderr}"
        r = run("git", "commit", "-q", "--allow-empty", "-m", "x", cwd=between)
        assert r.returncode != 0 and REFUSED in r.stderr, "real commit from .claude/worktrees/ must fail"
        r = run("git", "commit", "-q", "--allow-empty", "-m", "x", cwd=work)
        assert r.returncode == 0, f"real commit in the worktree must go ahead: {r.stderr}"
        os.makedirs(os.path.join(work, "sub"))
        r = run("git", "commit", "-q", "--allow-empty", "-m", "x", cwd=os.path.join(work, "sub"))
        assert r.returncode == 0, f"a commit from a worktree subdirectory (git sets GIT_PREFIX) goes ahead: {r.stderr}"
        # the repository is named from the worktree: git hands the hook the main checkout's git-dir (O4 review item 1)
        main_git = os.path.join(main, ".git")
        for argv, extra in ((("git", "commit", "-q", "--allow-empty", "-m", "d"), {"GIT_DIR": main_git, "GIT_WORK_TREE": work}),
                            (("git", "--git-dir=" + main_git, "--work-tree=" + work, "commit", "-q", "--allow-empty", "-m", "e"), {})):
            r = run(*argv, cwd=work, **extra)
            assert r.returncode != 0 and REFUSED in r.stderr and git(main, "rev-parse", "main").stdout == before, (argv, extra, r.returncode, r.stderr)

        # 4. the session-start script: ceiling at <main checkout>/.claude, once however often it runs
        env_file = os.path.join(d, "claude.env")
        script = os.path.join(ROOT, "scripts", "hooks", "git_ceiling_env.sh")
        for _ in range(2):
            r = subprocess.run(["bash", script], cwd=work, capture_output=True, text=True, env=env(CLAUDE_ENV_FILE=env_file, CLAUDE_PROJECT_DIR=work))
            assert r.returncode == 0, r.stderr
        assert open(env_file, encoding="utf-8").read().splitlines() == [f"export GIT_CEILING_DIRECTORIES={main}/.claude"]
        ceiling = env(GIT_CEILING_DIRECTORIES=os.path.join(main, ".claude"))
        assert subprocess.run(["git", "rev-parse", "--show-toplevel"], cwd=between, capture_output=True, env=ceiling).returncode != 0, "with the ceiling, git finds nothing from .claude/worktrees/"
        assert subprocess.run(["git", "rev-parse", "--show-toplevel"], cwd=work, capture_output=True, env=ceiling).returncode == 0, "the worktree itself is found"
        assert subprocess.run(["bash", script], cwd=work, capture_output=True, env=env()).returncode == 0, "without CLAUDE_ENV_FILE it does nothing"

    # 5. size limit and wiring
    with open(os.path.join(ROOT, "policy.json"), encoding="utf-8") as f:
        limit = json.load(f)["gates"]["max_lines"]["main_checkout_gate"]
    with open(GATE, encoding="utf-8") as f:
        lines = len(f.read().splitlines())
    assert lines <= limit, f"main_checkout_gate.py has {lines} lines, policy.json allows {limit}"
    with open(os.path.join(ROOT, ".pre-commit-config.yaml"), encoding="utf-8") as f:
        config = f.read()
    block = re.search(r"- id: main-checkout-gate\n(?:\s{8,}.*\n)+", config)
    assert block and "entry: python3 -I scripts/gates/main_checkout_gate.py --pre-commit" in block.group(0) and "stages: [pre-commit, pre-merge-commit]" in block.group(0), "pre-commit wiring"
    assert not os.path.exists(os.path.join(ROOT, "scripts", "hooks", "main_checkout_gate.py")), "the old gate is gone"
    print("main_checkout_gate self-test: ok")
    return 0


if __name__ == "__main__":
    sys.exit(self_test())
