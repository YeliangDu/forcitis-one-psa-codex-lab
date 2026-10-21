#!/usr/bin/env python3
"""主检出守护：主检出（仓库根，不是 .claude/worktrees 下的工作区）停在 main 时，禁止在其中 commit / reset / checkout 等改动。

起因：one-platform-file 2026-10-21 18:35 在自己的工作区根执行 `cd ..` 落到 .claude/worktrees/（那里不是仓库，git 向上找到主检出），
随后 `git reset --soft … && git commit` 改了主检出的 main，钩子因此跑旧版本近 3 小时（one-coordinator 2026-10-21）。
两种用法：
  PreToolUse 钩子（Bash，读 JSON）：按命令里的 `cd` 与 `git -C` 推算每条 git 命令落在哪个目录，落在「主检出且当前分支 main」且是改动类子命令就拒绝（退出码 2 + 标准错误）。
  pre-commit 钩子（--pre-commit）：当前目录所在的仓库是主检出且在 main 上，提交就拒绝（退出码 1）。
放行：`merge --ff-only`、`pull --ff-only`（one-merge 的快进）、只读命令、主检出不在 main 上、落在工作区里的命令、推算不出目录的命令。
出错一律拒绝（崩溃的退出码 1 对 PreToolUse 钩子来说是「不拦截」）。
用法：main_checkout_gate.py  |  main_checkout_gate.py --pre-commit  |  main_checkout_gate.py --self-test
"""
import json
import os
import re
import shlex
import subprocess
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
try:
    from codex_stdin_gate import ENV, KEYWORDS, SEPARATORS, SHELLS, WRAPPERS, tokens_of
except Exception as error:   # exit code 1 would mean "not blocked" to a PreToolUse hook
    print(f"main_checkout_gate.py 读不到 codex_stdin_gate.py（{error.__class__.__name__}: {error}），按失败处理", file=sys.stderr)
    sys.exit(2)

MUTATING = {"commit", "reset", "checkout", "switch", "merge", "rebase", "cherry-pick", "revert", "pull", "am", "restore", "clean", "update-ref",
            "add", "rm", "mv", "apply", "read-tree", "checkout-index", "symbolic-ref", "sparse-checkout", "bisect"}
STASH_MUTATING = {"", "push", "pop", "apply", "drop", "clear", "save", "create", "store", "branch"}
RECOVERY = {"merge", "rebase", "cherry-pick", "revert", "am"}   # --abort / --quit / --skip get a stuck main checkout out of the middle of an operation
REF_WRITERS = {"update-ref", "symbolic-ref", "fetch", "branch", "push", "tag"}   # they can move refs/heads/main from any worktree: the main checkout's working tree no longer matches
BRANCH_MUTATING = {"-f", "--force", "-d", "-D", "--delete", "-m", "-M", "--move", "-c", "-C", "--copy", "-u", "--set-upstream-to", "--unset-upstream"}
GIT_VALUE_OPTIONS = {"-C", "-c", "--git-dir", "--work-tree", "--namespace", "--exec-path", "--super-prefix", "--config-env"}
MESSAGE = ("拒绝：这条 git 命令会改动主检出（{top}）里的 main（{why}）。主检出只由 one-merge 经队列快进（merge --ff-only origin/main），"
           "不在其中提交、reset 或 checkout。到自己的工作区（.claude/worktrees/ 下的某个目录，不是 .claude/worktrees/ 本身）里做；"
           "若是 `cd ..` 落到了 .claude/worktrees/，git 会向上找到主检出，先 cd 回自己的工作区。")


def clean_env():
    return {k: v for k, v in os.environ.items() if not k.startswith("GIT_")}


def git(directory, *args):
    try:
        return subprocess.run(["git", "-C", directory, *args], capture_output=True, text=True, timeout=5, env=clean_env())
    except (OSError, subprocess.SubprocessError):
        return None


def main_checkout_of(directory):
    """(top level of the repository at `directory`, main checkout, branch) with real paths; None when `directory` is no repository or git cannot say."""
    top = git(directory, "rev-parse", "--show-toplevel")
    listing = git(directory, "worktree", "list", "--porcelain")
    if not top or top.returncode or not listing or listing.returncode:
        return None
    first = listing.stdout.split("\n\n")[0].splitlines()
    if not first or not first[0].startswith("worktree ") or "bare" in first:
        return None
    branch = git(directory, "symbolic-ref", "--short", "-q", "HEAD")
    return os.path.realpath(top.stdout.strip()), os.path.realpath(first[0][len("worktree "):]), (branch.stdout.strip() if branch and branch.returncode == 0 else "")


def hits_main(directory):
    """The reason when `directory` is in the main checkout and that is on main, else None."""
    found = main_checkout_of(directory)
    if found and found[0] == found[1] and found[2] == "main":
        return found[0], "当前分支 main"
    return None


def command_start(seg):
    i = 0
    while i < len(seg):
        tok = seg[i]
        if ENV.match(tok) or tok in KEYWORDS:
            i += 1
        elif tok in WRAPPERS:
            valued = WRAPPERS[tok]
            i += 1
            while i < len(seg) and (seg[i].startswith("-") or ENV.match(seg[i]) or (tok == "timeout" and re.fullmatch(r"[0-9.]+[smhd]?", seg[i]))):
                i += 2 if seg[i] in valued else 1
        else:
            break
    return i


def git_call(seg, i):
    """(directory options, sub-command, its arguments) of the git command at seg[i]: -C paths in order, the first non-option word after the global options."""
    paths, j = [], i + 1
    while j < len(seg) and seg[j].startswith("-"):
        option, _, inline = seg[j].partition("=")
        value = inline or (seg[j + 1] if j + 1 < len(seg) else "")
        if seg[j] == "-C" and j + 1 < len(seg):
            paths.append(seg[j + 1])
        elif option == "--work-tree":
            paths.append(value)
        elif option == "--git-dir":
            paths.append(os.path.dirname(value.rstrip("/")) if os.path.basename(value.rstrip("/")) == ".git" else None)
        j += 2 if seg[j] in GIT_VALUE_OPTIONS else 1
    return paths, (seg[j] if j < len(seg) else ""), seg[j + 1:]


def positionals(args):
    return [a for a in args if not a.startswith("-")]


def is_mutating(sub, args):
    if any(a in ("-h", "--help") for a in args):
        return False
    if sub in RECOVERY and any(a in ("--abort", "--quit", "--skip") for a in args):
        return False
    if sub == "clean" and any(a in ("-n", "--dry-run") for a in args):
        return False
    if any(a == "-r" or a.startswith("--rebase") for a in args) and sub == "pull":
        return True
    if sub == "merge" and "--ff-only" in args and positionals(args) == ["origin/main"] and not any(a in ("--no-ff", "--squash") for a in args):
        return False   # one-merge's fast-forward of the main checkout, and only that
    if sub == "pull" and "--ff-only" in args and positionals(args) in ([], ["origin"], ["origin", "main"]):
        return False
    if sub == "stash":
        return (positionals(args) or [""])[0] in STASH_MUTATING
    if sub == "branch":
        return any(a in BRANCH_MUTATING or a.startswith("--set-upstream-to") for a in args)
    return sub in MUTATING


def moves_main_ref(sub, args):
    """True when the command can move refs/heads/main (from any worktree: they share refs): update-ref, symbolic-ref, fetch --update-head-ok, branch -f/-D/-m main, push to this repository, tag -f of nothing relevant."""
    names = [a for a in args if a in ("main", "refs/heads/main") or a.endswith(":main") or a.endswith(":refs/heads/main")]
    if sub in ("update-ref", "symbolic-ref"):
        return any(a in ("refs/heads/main", "main") for a in args)
    if sub == "fetch":
        return "--update-head-ok" in args and bool(names)
    if sub == "branch":
        return any(a in BRANCH_MUTATING for a in args) and bool(names)
    if sub == "push":
        return bool(names) and any(a in (".", "..") or a.startswith("/") or a.startswith("file:") for a in positionals(args))
    return False


def segments_of(command):
    segments, cur = [], []
    for token in tokens_of(command):
        if token in SEPARATORS or set(token) <= set(";&|()"):
            if cur:
                segments.append(cur)
            cur = []
        else:
            cur.append(token)
    if cur:
        segments.append(cur)
    return segments


def resolve(base, path):
    if path is None or "$" in path or "`" in path or path == "-":
        return None
    return os.path.normpath(os.path.join(base, os.path.expanduser(path)))


EXEC_WRAPPERS = {"xargs", "find", "parallel", "watch", "entr"}
UNPARSED = re.compile(r"\bgit\b.*\b(commit|reset|checkout|switch|merge|rebase|cherry-pick|revert|pull|am|restore|clean|stash|update-ref|add|rm|mv|apply)\b")


def env_directory(seg, i, here):
    """Where the command at seg[i] runs when `env -C dir` or GIT_WORK_TREE=/GIT_DIR= in front of it say so (here otherwise)."""
    for k in range(i):
        if seg[k] == "-C" and k + 1 < len(seg) and os.path.basename(seg[0]) == "env":
            here = resolve(here, seg[k + 1]) if here else None
        elif seg[k].startswith("GIT_WORK_TREE="):
            here = resolve(here, seg[k].partition("=")[2]) if here else None
        elif seg[k].startswith("GIT_DIR="):
            value = seg[k].partition("=")[2].rstrip("/")
            here = resolve(here, os.path.dirname(value)) if here and os.path.basename(value) == ".git" else here
    return here


def git_verdict(seg, j, here):
    paths, sub, args = git_call(seg, j)
    target = here
    for path in paths:
        target = resolve(target, path) if target else None
    if not target or not os.path.isdir(target):
        return None
    if moves_main_ref(sub, args):
        found = main_checkout_of(target)
        branch = git(found[1], "symbolic-ref", "--short", "-q", "HEAD") if found else None
        if branch and branch.returncode == 0 and branch.stdout.strip() == "main":
            return found[1], "当前分支 main；这条命令会移动共享的 refs/heads/main，主检出的文件跟不上"
    if is_mutating(sub, args):
        return hits_main(target)
    return None


def verdict(command, cwd, depth=0):
    """(top, why) for the first git command in `command` that changes the main checkout while it is on main, else None. `cd`, `pushd`/`popd`, `git -C`, `--work-tree`, `env -C` and GIT_DIR/GIT_WORK_TREE in front are followed;
    a directory that cannot be worked out is let through."""
    try:
        segments = segments_of(command)
    except ValueError:
        return hits_main(cwd) if UNPARSED.search(command) and "merge --ff-only origin/main" not in command else None
    here, stack = cwd, []
    for seg in segments:
        i = command_start(seg)
        if i >= len(seg):
            continue
        name = os.path.basename(seg[i])
        if name in ("cd", "pushd", "popd"):
            if name == "popd":
                here = stack.pop() if stack else None
                continue
            if name == "pushd":
                stack.append(here)
            target = next((t for t in seg[i + 1:] if not t.startswith("-")), None)
            here = resolve(here, target) if here and target else (os.path.expanduser("~") if here and not target else None)
        elif name == "git" and here:
            found = git_verdict(seg, i, env_directory(seg, i, here))
            if found:
                return found
        elif name in EXEC_WRAPPERS and here:
            for j in range(i + 1, len(seg)):
                if os.path.basename(seg[j]) == "git":
                    found = git_verdict(seg, j, here)
                    if found:
                        return found
        elif name in SHELLS and depth < 3 and here:
            for token in seg[i + 1:]:
                if " " in token or "git" in token:
                    found = verdict(token, here, depth + 1)
                    if found:
                        return found
    return None


def pre_tool_use():
    try:
        payload = json.load(sys.stdin)
    except ValueError:
        print("main_checkout_gate.py 读不懂钩子输入（不是 JSON），按失败处理", file=sys.stderr)
        return 2
    if not isinstance(payload, dict):
        print("main_checkout_gate.py 读不懂钩子输入（不是对象），按失败处理", file=sys.stderr)
        return 2
    if payload.get("tool_name", "Bash") != "Bash":
        return 0
    tool_input = payload.get("tool_input")
    command = tool_input.get("command") if isinstance(tool_input, dict) else None
    if not isinstance(command, str) or "git" not in command:
        return 0
    cwd = payload.get("cwd") if isinstance(payload.get("cwd"), str) else os.getcwd()
    found = verdict(command, cwd)
    if found:
        print(MESSAGE.format(top=found[0], why=found[1]), file=sys.stderr)
        return 2
    return 0


def pre_commit():
    found = hits_main(os.getcwd())
    if found:
        print(MESSAGE.format(top=found[0], why=found[1]), file=sys.stderr)
        return 1
    return 0


def guarded(function):
    try:
        return function()
    except Exception as error:
        print(f"main_checkout_gate.py 出错（{error.__class__.__name__}: {error}），按失败处理", file=sys.stderr)
        return 2


def self_test():
    def run(*args, cwd, stdin=None):
        return subprocess.run(args, cwd=cwd, input=stdin, capture_output=True, text=True, env={**clean_env(), "GIT_CONFIG_GLOBAL": "/dev/null", "GIT_CONFIG_NOSYSTEM": "1", "GIT_AUTHOR_NAME": "t", "GIT_AUTHOR_EMAIL": "t@t", "GIT_COMMITTER_NAME": "t", "GIT_COMMITTER_EMAIL": "t@t"})

    with tempfile.TemporaryDirectory() as raw:
        d = os.path.realpath(raw)
        main = os.path.join(d, "repo")
        os.makedirs(main)
        for args in (["init", "-q", "-b", "main"], ["commit", "-q", "--allow-empty", "-m", "one"]):
            assert run("git", *args, cwd=main).returncode == 0
        work = os.path.join(main, ".claude", "worktrees", "w1")
        assert run("git", "worktree", "add", "-q", work, "-b", "side", cwd=main).returncode == 0
        between = os.path.dirname(work)

        def gate(command, cwd):
            payload = json.dumps({"tool_name": "Bash", "tool_input": {"command": command}, "cwd": cwd})
            r = run(sys.executable, "-I", os.path.abspath(__file__), cwd=cwd, stdin=payload)
            return r.returncode, r.stderr

        deny = [
            ("git commit -m x", main),
            ("git reset --soft HEAD~1", main),
            ("git checkout -b feature", main),
            ("git switch side", main),
            ("git merge side", main),
            ("git rebase side", main),
            ("git cherry-pick HEAD", main),
            ("git pull", main),
            ("git restore a", main),
            ("git stash", main),
            ("git branch -D side", main),
            ("git -c user.name=x commit -m x", main),
            ("git reset --soft HEAD~1 && git commit -m x", between + "/../.."),
            (f"git -C {main} commit -m x", work),
            (f"cd {main} && git commit -m x", work),
            ("cd ../.. && git commit -m x", work),
            ("true; cd ..; cd ..; cd ..; git reset --hard", work),
            ("env GIT_AUTHOR_NAME=x git commit -m x", main),
            ("bash -c 'cd ../../.. && git commit -m x'", work),
            ("git pull --rebase", main),
            ("git merge --no-ff side", main),
            ("git commit -m x", between),   # .claude/worktrees/ itself: git walks up to the main checkout (the 2026-10-21 incident)
            ("git merge --ff-only side", main),
            ("git pull --ff-only origin side", main),
            ("git pull --ff-only --rebase=true", main),
            ("git add a", main),
            ("git stash push", main),
            ("echo x | xargs git commit", main),
            ("find . -name a -exec git commit -m x \\;", main),
            (f"GIT_DIR={main}/.git GIT_WORK_TREE={main} git commit -m x", work),
            (f"git --git-dir={main}/.git --work-tree={main} commit -m x", work),
            (f"GIT_WORK_TREE={main} git reset --hard", work),
            (f"git --work-tree {main} reset --hard", work),
            (f"env -C {main} git commit -m x", work),
            (f"pushd {main}; git commit -m x", work),
            (f"pushd {main}; pushd {work}; popd; git commit -m x", work),
            ("git update-ref refs/heads/main refs/heads/side", work),   # refs are shared between the worktrees
            ("git fetch --update-head-ok . side:main", work),
            ("git branch -f main side", work),
            ("git symbolic-ref refs/heads/main refs/heads/side", work),
            ("git commit -m x \"unterminated", main),
        ]
        allow = [
            ("git status", main),
            ("git log --oneline", main),
            ("git fetch origin", main),
            ("git merge --ff-only origin/main", main),
            ("git pull --ff-only", main),
            ("git -C " + main + " merge --ff-only origin/main", work),
            ("git commit -m x", work),
            ("git reset --soft HEAD~1", work),
            ("git checkout -b feature", work),
            ("cd " + work + " && git commit -m x", main),
            ("git branch", main),
            ("git branch -a", main),
            ("echo git commit", main),
            ("ls && cd $SOMEWHERE && git commit -m x", main),   # a directory that cannot be worked out is let through
            ("git diff", main),
            ("git stash list --format='%H %gs'", main),
            ("git stash show", main),
            ("git checkout --help", main),
            ("git clean -n", main),
            ("git merge --abort", main),
            ("git rebase --abort", main),
            ("git fetch origin", work),
            ("git update-ref refs/heads/side HEAD", work),
            ("git branch -f side HEAD", work),
            (f"env -C {work} git commit -m x", main),
            (f"git --work-tree={work} commit -m x", main),
            (f"cd {main}; popd; git commit -m x", work),
            ("(cd " + main + " && git log) && echo done", work),
        ]
        for command, cwd in deny:
            code, message = gate(command, os.path.normpath(cwd))
            assert code == 2 and "拒绝：这条 git 命令会改动主检出" in message, f"must deny (and say why, not crash): {command!r} in {cwd}: {code} {message[:80]}"
        for command, cwd in allow:
            assert os.path.isdir(cwd), cwd
            assert gate(command, cwd)[0] == 0, f"must allow: {command!r} in {cwd}"

        # the main checkout on another branch is not guarded
        assert run("git", "checkout", "-q", "-b", "elsewhere", cwd=main).returncode == 0
        assert gate("git commit -m x", main)[0] == 0, "main checkout not on main"
        w2 = os.path.join(between, "w2")
        assert run("git", "worktree", "add", "-q", w2, "main", cwd=main).returncode == 0
        assert gate("git commit -m x", w2)[0] == 0, "a worktree that has main checked out is not the main checkout"
        assert run(sys.executable, "-I", os.path.abspath(__file__), "--pre-commit", cwd=w2).returncode == 0
        assert run("git", "worktree", "remove", "--force", w2, cwd=main).returncode == 0
        assert run("git", "checkout", "-q", "main", cwd=main).returncode == 0

        # unreadable input fails closed
        assert run(sys.executable, "-I", os.path.abspath(__file__), cwd=main, stdin="not json").returncode == 2
        assert run(sys.executable, "-I", os.path.abspath(__file__), cwd=main, stdin="[]").returncode == 2

        # a gate whose helper module is gone fails closed (exit 1 would let everything through)
        lonely = os.path.join(d, "lonely")
        os.makedirs(lonely)
        with open(os.path.abspath(__file__), encoding="utf-8") as source, open(os.path.join(lonely, "main_checkout_gate.py"), "w", encoding="utf-8") as copy:
            copy.write(source.read())
        assert run(sys.executable, "-I", os.path.join(lonely, "main_checkout_gate.py"), cwd=main, stdin="{}").returncode == 2

        # the session-start script: ceiling at <main checkout>/.claude, once however often it runs
        env_file = os.path.join(d, "claude.env")
        script = os.path.join(os.path.dirname(os.path.abspath(__file__)), "git_ceiling_env.sh")
        for _ in range(2):
            r = subprocess.run(["bash", script], cwd=work, capture_output=True, text=True, env={**clean_env(), "CLAUDE_ENV_FILE": env_file, "CLAUDE_PROJECT_DIR": work})
            assert r.returncode == 0, r.stderr
        assert open(env_file, encoding="utf-8").read().splitlines() == [f"export GIT_CEILING_DIRECTORIES={main}/.claude"]
        ceiling = {**clean_env(), "GIT_CEILING_DIRECTORIES": os.path.join(main, ".claude")}
        assert subprocess.run(["git", "rev-parse", "--show-toplevel"], cwd=between, capture_output=True, env=ceiling).returncode != 0, "with the ceiling, git finds nothing from .claude/worktrees/"
        assert subprocess.run(["git", "rev-parse", "--show-toplevel"], cwd=work, capture_output=True, env=ceiling).returncode == 0, "the worktree itself is found"
        assert subprocess.run(["bash", script], cwd=work, capture_output=True, env={**clean_env()}).returncode == 0, "without CLAUDE_ENV_FILE it does nothing"

        # pre-commit mode
        assert run(sys.executable, "-I", os.path.abspath(__file__), "--pre-commit", cwd=main).returncode == 1, "commit in the main checkout on main"
        assert run(sys.executable, "-I", os.path.abspath(__file__), "--pre-commit", cwd=work).returncode == 0, "commit in a worktree"
        assert run(sys.executable, "-I", os.path.abspath(__file__), "--pre-commit", cwd=between).returncode == 1, "from .claude/worktrees/ git finds the main checkout"
    print("main_checkout_gate self-test: ok")


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        self_test()
    elif sys.argv[1:] == ["--pre-commit"]:
        sys.exit(guarded(pre_commit))
    else:
        sys.exit(guarded(pre_tool_use))
