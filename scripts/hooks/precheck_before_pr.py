#!/usr/bin/env python3
"""PreToolUse 钩子（Bash）：`gh pr create` 之前运行 scripts/precheck.sh，没有以「ready」收尾就拒绝提 PR（SESSION_PLAYBOOK TEAM-44）。

标准输入是钩子的 JSON（tool_input.command）；退出码 2 + 标准错误 = 拒绝，0 = 放行。命令不是 `gh pr create` 时立刻放行。
precheck 本身失败（FAIL 或前端依赖缺失需先问用户）时，把它的输出末尾带回给会话。
注册到 .claude/settings.json 时必须显式写 "timeout": 1500 以上：命令钩子默认 600 秒就被终止，终止不拦截。
推送前钩子（pre_push_gate.py）在同一提交、同一 origin/main、干净工作区上通过，或手动 `scripts/precheck.sh`（默认基线、含不起容器的单元测试，全过）通过时，记一个通过标记（<git 目录>/forcitis-precheck-pass.json，
"covers" 写明检查集合：pre-push 或 full，full 包含 pre-push 的全部检查）；推送前钩子与本钩子见到有效标记（提交、基线相同，工作区干净，不超过 PASS_MARK_SECONDS，检查集合不小于 pre-push）就不再重跑 precheck.sh。
推送前的检查没有全历史 gitleaks 与前端完整验证，这两项由 CI 兜底（TEAM-106、GOV-1023-QUA-04）。
precheck.sh 开头 `--snapshot` 取工作树状态，通过后 `--record <快照文件>` 写标记：快照为空（开头就不干净）或之后提交、基线变了，都不写。
用法：precheck_before_pr.py  |  precheck_before_pr.py --self-test  |  --snapshot  |  --record <快照文件>
"""
import json, os, re, subprocess, sys, tempfile, time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from command_words import plain  # noqa: E402

PR_CREATE = re.compile(r"(?:^|[;&|(\s\"'`/])gh\s+pr\s+(?:create|new)(?![\w-])")
TIMEOUT_SECONDS = 1500
PASS_MARK_SECONDS = 2 * 3600
PASS_MARK = "forcitis-precheck-pass.json"
COVERS = ("pre-push", "full")  # check sets that cover everything the push gate runs


# 只看命令字面：写在 echo / commit 信息里的 gh pr create 也会拦（宁可多拦一次，不漏拦）
def is_pr_create(command):
    return bool(PR_CREATE.search(plain(command)))


def verdict(output, returncode, action="提 PR", retry="gh pr create"):
    last = [line for line in output.splitlines() if line.startswith("SUMMARY:")]
    if returncode == 0 and last and last[-1].endswith("— ready"):
        return None
    tail = "\n".join(output.splitlines()[-40:])
    ask = "\n前端依赖未装：先用 pnpm install --frozen-lockfile --offline（只用本机缓存、不下载）；缓存缺包而失败时，再问用户是否联网安装。" if "前端依赖未安装" in output else ""
    return f"precheck.sh 未全过（退出码 {returncode}），不能{action}。先修好或按提示问用户，再重新 {retry}：{ask}\n{tail}"


def repository_of(cwd):
    """The checkout the session works in (a worktree is not the main checkout), so precheck judges that branch."""
    top = subprocess.run(["git", "-C", cwd, "rev-parse", "--show-toplevel"], capture_output=True, text=True)
    return top.stdout.strip() if top.returncode == 0 and top.stdout.strip() else cwd


def git_out(root, *args):
    run = subprocess.run(["git", "-C", root, *args], capture_output=True, text=True)
    return run.stdout.strip() if run.returncode == 0 else None


def tree_state(root):
    """(HEAD, origin/main, git directory) when the working tree is clean and all three are known, else None: only a clean tree can be vouched for by a mark."""
    head, base, gitdir = git_out(root, "rev-parse", "HEAD"), git_out(root, "rev-parse", "origin/main"), git_out(root, "rev-parse", "--absolute-git-dir")
    dirty = git_out(root, "status", "--porcelain", "--untracked-files=normal")
    return (head, base, gitdir) if head and base and gitdir and dirty == "" else None


def record_pass(root, now=None, checked=None, covers="pre-push"):
    """Called by the pre-push gate after precheck --pre-push ended ready. `checked` is tree_state(root) taken before the checks started: the mark is written only when the tree is still exactly that,
    so a commit or fetch made while the checks ran never gets vouched for."""
    state = tree_state(root)
    if state is None or (checked is not None and state != checked):
        return False
    head, base, gitdir = state
    target = os.path.join(gitdir, PASS_MARK)
    with open(target + ".tmp", "w", encoding="utf-8") as mark:
        json.dump({"head": head, "base": base, "at": now if now is not None else time.time(), "covers": covers}, mark)
    os.replace(target + ".tmp", target)
    return True


def passed_recently(root, now=None):
    state = tree_state(root)
    if not state:
        return False
    head, base, gitdir = state
    try:
        with open(os.path.join(gitdir, PASS_MARK), encoding="utf-8") as mark:
            data = json.load(mark)
        return data["covers"] in COVERS and data["head"] == head and data["base"] == base and 0 <= (now if now is not None else time.time()) - float(data["at"]) <= PASS_MARK_SECONDS
    except (OSError, ValueError, KeyError, TypeError):
        return False


def snapshot_main():
    """precheck.sh start: the tree state as JSON, `null` when it cannot be vouched for (dirty, or origin/main unknown)."""
    print(json.dumps(tree_state(repository_of(os.getcwd()))))
    return 0


def record_main(snapshot_file):
    """precheck.sh end (ready, default base, pre-push checks included): leave a `full` mark when the tree is still the one snapshotted."""
    root = repository_of(os.getcwd())
    try:
        with open(snapshot_file, encoding="utf-8") as f:
            checked = json.load(f)
    except (OSError, ValueError):
        checked = None
    if not isinstance(checked, list):
        print("通过标记未写：预检开始时工作区不干净或 origin/main 未知，推送前仍会重跑检查")
        return 0
    if record_pass(root, checked=tuple(checked), covers="full"):
        print(f"通过标记已写（{PASS_MARK}，检查集合 full）：{PASS_MARK_SECONDS // 3600} 小时内、提交与 origin/main 不变、工作区干净时，推送前钩子与开 PR 钩子不重跑")
    else:
        print("通过标记未写：预检期间提交、origin/main 或工作区变了，推送前仍会重跑检查")
    return 0


def main():
    try:
        payload = json.load(sys.stdin)
    except ValueError:
        return 0
    if not isinstance(payload, dict) or payload.get("tool_name", "Bash") != "Bash" or not is_pr_create((payload.get("tool_input") or {}).get("command")):
        return 0
    root = repository_of(payload.get("cwd") or os.getcwd())
    if passed_recently(root):
        print("precheck.sh 不重跑：推送前检查刚在同一提交、同一 origin/main、干净工作区上通过（全历史 gitleaks 与前端完整验证由 CI 跑）", file=sys.stderr)
        return 0
    try:
        run = subprocess.run([os.path.join(root, "scripts", "precheck.sh")], cwd=root, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=TIMEOUT_SECONDS)
    except subprocess.TimeoutExpired:
        print(f"precheck.sh 超过 {TIMEOUT_SECONDS} 秒未结束，不能提 PR", file=sys.stderr)
        return 2
    reason = verdict(run.stdout + run.stderr, run.returncode)
    if reason:
        print(reason, file=sys.stderr)
        return 2
    return 0


def self_test():
    for command in ("gh pr create --title x", "gh pr new", "bash -c \"gh pr create\"", "/opt/homebrew/bin/gh pr create", "cd a && gh pr create", "git push; gh  pr  create --base main", "(gh pr create)"):
        assert is_pr_create(command), command
    for command in ("gh pr view 3", "gh pr merge 3 --squash", "gh pr checks 3", "gh pr comment 3", "gh pr ready 3", "gh api repos/o/r/pulls", "gh pr create-not", "gh pr created", "", None):
        assert not is_pr_create(command), command
    assert guarded_main.__doc__, "guarded_main refuses on error"
    assert verdict("x\nSUMMARY: 7 PASS, 0 FAIL, 7 SKIP (base origin/main) — ready\n", 0) is None
    assert verdict("SUMMARY: 6 PASS, 1 FAIL, 7 SKIP (base origin/main) — not ready\n", 1)
    assert verdict("SUMMARY: 7 PASS, 0 FAIL, 7 SKIP (base origin/main) — ready\n", 1), "退出码非 0 即拒绝"
    assert verdict("没有 SUMMARY 行\n", 0), "没有 SUMMARY 行即拒绝"
    assert "pnpm install --frozen-lockfile --offline" in verdict("FAIL frontend verify 前端依赖未安装：先在 frontend/ 运行 pnpm install --frozen-lockfile --offline\nSUMMARY: 1 PASS, 1 FAIL, 0 SKIP (base origin/main) — not ready\n", 1)
    from unittest import mock
    import io
    with tempfile.TemporaryDirectory() as root:
        def git(*args):
            subprocess.run(["git", "-C", root, "-c", "user.name=t", "-c", "user.email=t@t", *args], check=True, capture_output=True)
        git("init", "-q", "-b", "main")
        open(os.path.join(root, "f"), "w").write("1")
        git("add", "f"); git("commit", "-q", "-m", "one")
        git("update-ref", "refs/remotes/origin/main", "HEAD")
        assert not passed_recently(root), "no mark yet"
        assert record_pass(root, now=1000) and passed_recently(root, now=1000 + 60)
        assert not passed_recently(root, now=1000 + PASS_MARK_SECONDS + 1) and not passed_recently(root, now=500), "too old, or from the future"
        open(os.path.join(root, "f"), "w").write("2")
        assert not passed_recently(root, now=1060) and not record_pass(root), "a dirty tree is neither recorded nor vouched for"
        git("commit", "-q", "-a", "-m", "two")
        assert not passed_recently(root, now=1060), "a new commit needs its own pass"
        git("update-ref", "refs/remotes/origin/main", "HEAD")
        assert record_pass(root, now=2000) and passed_recently(root, now=2060)
        git("update-ref", "refs/remotes/origin/main", "HEAD~1")
        assert not passed_recently(root, now=2060), "origin/main moved"
        git("update-ref", "refs/remotes/origin/main", "HEAD")
        open(os.path.join(root, "untracked"), "w").write("x")
        assert not passed_recently(root, now=2060), "an untracked file is a change too"
        os.remove(os.path.join(root, "untracked"))
        # a commit made while the checks ran: the tree checked is not the tree now, so no mark
        checked = tree_state(root)
        open(os.path.join(root, "f"), "w").write("3")
        git("commit", "-q", "-a", "-m", "three")
        assert not record_pass(root, now=3000, checked=checked) and not passed_recently(root, now=3000)
        assert record_pass(root, now=3000, checked=tree_state(root)) and passed_recently(root, now=3000)
        # a manual full run leaves a `full` mark that counts; a mark of an unknown check set, or one without a set, does not
        assert record_pass(root, now=3500, checked=tree_state(root), covers="full") and passed_recently(root, now=3500)
        for covers in ("backend-only", None):
            assert record_pass(root, now=3500, covers=covers) and not passed_recently(root, now=3500), covers
        gitdir = tree_state(root)[2]
        json.dump({"head": tree_state(root)[0], "base": tree_state(root)[1], "at": 3500}, open(os.path.join(gitdir, PASS_MARK), "w"))
        assert not passed_recently(root, now=3500), "a mark without a check set is not trusted"
        # --snapshot / --record: unchanged tree leaves a mark, a tree that moved or a dirty start leaves none
        cwd = os.getcwd()
        os.chdir(root)
        try:
            snap = os.path.join(root, ".git", "snap.json")
            json.dump(list(tree_state(root)), open(snap, "w"))
            with mock.patch.object(sys, "stdout", io.StringIO()):
                assert record_main(snap) == 0
            assert passed_recently(root, now=time.time()) and json.load(open(os.path.join(gitdir, PASS_MARK)))["covers"] == "full"
            os.remove(os.path.join(gitdir, PASS_MARK))
            open(os.path.join(root, "f"), "w").write("4")
            git("commit", "-q", "-a", "-m", "four")
            with mock.patch.object(sys, "stdout", io.StringIO()):
                assert record_main(snap) == 0
            assert not os.path.exists(os.path.join(gitdir, PASS_MARK)), "the commit made after the snapshot is not vouched for"
            json.dump(None, open(snap, "w"))
            with mock.patch.object(sys, "stdout", io.StringIO()):
                assert record_main(snap) == 0 and record_main(snap + ".missing") == 0
            assert not os.path.exists(os.path.join(gitdir, PASS_MARK)), "a dirty start leaves no mark"
            out = io.StringIO()
            with mock.patch.object(sys, "stdout", out):
                snapshot_main()
            assert json.loads(out.getvalue()) == list(tree_state(root))
        finally:
            os.chdir(cwd)
        assert record_pass(root, now=3000, checked=tree_state(root)) and passed_recently(root, now=3000)
        # main(): no valid mark -> precheck.sh runs; a valid mark -> it does not
        raw = json.dumps({"tool_name": "Bash", "tool_input": {"command": "gh pr create"}, "cwd": root})
        ready = subprocess.CompletedProcess([], 0, "SUMMARY: 1 PASS, 0 FAIL, 0 SKIP (base origin/main) — ready\n", "")
        here = sys.modules[__name__]
        real_run = subprocess.run
        for now, runs in ((3000, 0), (3000 + PASS_MARK_SECONDS + 5, 1)):
            calls = []

            def fake_run(command, *args, **kwargs):
                if str(command[0]).endswith("precheck.sh"):
                    calls.append(command)
                    return ready
                return real_run(command, *args, **kwargs)
            with mock.patch.object(time, "time", return_value=now), mock.patch.object(subprocess, "run", side_effect=fake_run), mock.patch.object(sys, "stdin", io.StringIO(raw)), \
                    mock.patch.object(sys, "stderr", io.StringIO()), mock.patch.object(here, "repository_of", return_value=root):
                assert main() == 0 and len(calls) == runs, (now, calls)
    print("precheck_before_pr self-test: ok")
    return 0


def guarded_main():
    """A crash exits 1, which Claude Code treats as 'do not block': any error here refuses the PR instead."""
    try:
        return main()
    except Exception as error:
        print(f"precheck_before_pr.py 出错（{error.__class__.__name__}: {error}），按失败处理，不能提 PR", file=sys.stderr)
        return 2


if __name__ == "__main__":
    args = sys.argv[1:]
    if args == ["--self-test"]:
        sys.exit(self_test())
    if args == ["--snapshot"]:
        sys.exit(snapshot_main())
    if len(args) == 2 and args[0] == "--record":
        sys.exit(record_main(args[1]))
    sys.exit(guarded_main())
