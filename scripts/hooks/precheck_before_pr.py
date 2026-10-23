#!/usr/bin/env python3
"""PreToolUse 钩子（Bash）：`gh pr create` 之前运行 scripts/precheck.sh，没有以「ready」收尾就拒绝提 PR（SESSION_PLAYBOOK TEAM-44）。

标准输入是钩子的 JSON（tool_input.command）；退出码 2 + 标准错误 = 拒绝，0 = 放行。命令不是 `gh pr create` 时立刻放行。
precheck 本身失败（FAIL 或前端依赖缺失需先问用户）时，把它的输出末尾带回给会话。
注册到 .claude/settings.json 时必须显式写 "timeout": 1500 以上：命令钩子默认 600 秒就被终止，终止不拦截。
用法：precheck_before_pr.py  |  precheck_before_pr.py --self-test
"""
import json, os, re, subprocess, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from command_words import plain  # noqa: E402

PR_CREATE = re.compile(r"(?:^|[;&|(\s\"'`/])gh\s+pr\s+(?:create|new)(?![\w-])")
TIMEOUT_SECONDS = 1500


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


def main():
    try:
        payload = json.load(sys.stdin)
    except ValueError:
        return 0
    if not isinstance(payload, dict) or payload.get("tool_name", "Bash") != "Bash" or not is_pr_create((payload.get("tool_input") or {}).get("command")):
        return 0
    root = repository_of(payload.get("cwd") or os.getcwd())
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
    sys.exit(self_test() if sys.argv[1:] == ["--self-test"] else guarded_main())
