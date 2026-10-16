#!/usr/bin/env python3
"""PreToolUse 钩子（Bash）：`git push` 之前运行 `scripts/precheck.sh --pre-push`（全部不起容器的单元测试与守护），没以「ready」收尾就拒绝推送。

用户 2026-10-16 在待决定收件箱选「启用」（20261016-one-quality-pre-push-hook）；起因：一个分支连续 3 次在 CI 里挂同一个源码扫描测试，本地从没跑到。
绕过：`git push --no-verify` 不会触发 git 钩子，所以留痕放在这里——命令含 --no-verify 时必须同时带 `PUSH_BYPASS_REASON="<理由>"`，否则拒绝；
放行时往 <仓库>/.claude/push-bypass.log（不入仓）追加一行（时间、分支、理由），推送者要在 PR 说明「绕过推送前钩子」一栏写明。
退出码 2 + 标准错误 = 拒绝，0 = 放行；出错一律拒绝（崩溃的退出码 1 对钩子来说是「不拦截」）。删除分支、--dry-run 的推送不拦。
注册到 .claude/settings.json 时必须写 "timeout": 1500 以上。
用法：pre_push_gate.py  |  pre_push_gate.py --self-test
"""
import datetime
import json
import os
import re
import subprocess
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import precheck_before_pr as pr  # noqa: E402

PUSH = re.compile(r"(?:^|[;&|(\s\"'`/])git(?:\s+-C\s+\S+|\s+-c\s+\S+)*\s+push(?![\w-])")
SKIP = re.compile(r"--delete\b|\s-d\s|--dry-run\b|\s-n\s|\s:\S+")
REASON = re.compile(r"PUSH_BYPASS_REASON=(?:\"([^\"]+)\"|'([^']+)'|(\S+))")
TIMEOUT_SECONDS = 1500


def decide(command):
    """('allow', None) | ('bypass', reason) | ('deny', message) | ('run', None)."""
    if not command or not PUSH.search(command) or SKIP.search(command):
        return "allow", None
    if "--no-verify" in command:
        m = REASON.search(command)
        reason = next((g for g in m.groups() if g), "").strip() if m else ""
        if not reason:
            return "deny", "git push --no-verify 要绕过推送前钩子，必须同时在命令里写理由：PUSH_BYPASS_REASON=\"<理由>\" git push --no-verify …（理由会记入 .claude/push-bypass.log，并要在 PR 说明「绕过推送前钩子」一栏写明）"
        return "bypass", reason
    return "run", None


def log_bypass(root, branch, reason, now=None):
    path = os.path.join(root, ".claude", "push-bypass.log")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    stamp = (now or datetime.datetime.now()).strftime("%Y-%m-%d %H:%M:%S")
    with open(path, "a", encoding="utf-8") as f:
        f.write(f"{stamp}\t{branch}\t{reason}\n")
    return path


def branch_of(root):
    r = subprocess.run(["git", "-C", root, "rev-parse", "--abbrev-ref", "HEAD"], capture_output=True, text=True)
    return r.stdout.strip() or "?"


def main():
    try:
        payload = json.load(sys.stdin)
    except ValueError:
        return 0
    if not isinstance(payload, dict) or payload.get("tool_name", "Bash") != "Bash":
        return 0
    action, info = decide((payload.get("tool_input") or {}).get("command"))
    if action == "allow":
        return 0
    if action == "deny":
        print(info, file=sys.stderr)
        return 2
    root = pr.repository_of(payload.get("cwd") or os.getcwd())
    if action == "bypass":
        path = log_bypass(root, branch_of(root), info)
        print(f"推送前钩子已按理由绕过并留痕（{path}）：{info}", file=sys.stderr)
        return 0
    try:
        run = subprocess.run([os.path.join(root, "scripts", "precheck.sh"), "--pre-push"], cwd=root, capture_output=True, text=True,
                             encoding="utf-8", errors="replace", timeout=TIMEOUT_SECONDS)
    except subprocess.TimeoutExpired:
        print(f"precheck.sh --pre-push 超过 {TIMEOUT_SECONDS} 秒未结束，不能推送", file=sys.stderr)
        return 2
    reason = pr.verdict(run.stdout + run.stderr, run.returncode, "推送", "git push")
    if reason:
        print(reason + "\n（确需绕过：PUSH_BYPASS_REASON=\"<理由>\" git push --no-verify …，会留痕）", file=sys.stderr)
        return 2
    return 0


def guarded_main():
    try:
        return main()
    except Exception as error:
        print(f"pre_push_gate.py 出错（{error.__class__.__name__}: {error}），按失败处理，不能推送", file=sys.stderr)
        return 2


def self_test():
    for command in ("git push", "git push origin HEAD", "cd x && git push -u origin b", "git -C /a/b push origin c", "git -c core.x=y push", "(git push)", "bash -c \"git push\"", "git push --force-with-lease"):
        assert decide(command)[0] == "run", command
    for command in ("git pushd", "git status", "git log --oneline", "echo hi", "", None, "git push origin --delete old", "git push --dry-run", "git push origin :old", "git commit -m 'x'", "gh pr create"):
        assert decide(command)[0] == "allow", command
    assert decide("git push --no-verify")[0] == "deny", "bypass without a reason is refused"
    assert decide("PUSH_BYPASS_REASON= git push --no-verify")[0] == "deny", "an empty reason is no reason"
    assert decide('PUSH_BYPASS_REASON="CI 已挂在 runner，先推送看日志" git push --no-verify origin b') == ("bypass", "CI 已挂在 runner，先推送看日志")
    assert decide("PUSH_BYPASS_REASON='a b' git push --no-verify")[1] == "a b"
    assert decide("PUSH_BYPASS_REASON=x git push --no-verify")[1] == "x"
    assert decide("PUSH_BYPASS_REASON=x git push")[0] == "run", "a reason without --no-verify bypasses nothing"
    with tempfile.TemporaryDirectory() as root:
        path = log_bypass(root, "claude/b", "理由", datetime.datetime(2026, 10, 16, 12, 0, 0))
        log_bypass(root, "claude/c", "再一次", datetime.datetime(2026, 10, 16, 12, 5, 0))
        assert open(path, encoding="utf-8").read() == "2026-10-16 12:00:00\tclaude/b\t理由\n2026-10-16 12:05:00\tclaude/c\t再一次\n"
    assert pr.verdict("SUMMARY: 1 PASS, 1 FAIL, 0 SKIP (base origin/main) — not ready\n", 1, "推送", "git push").count("git push") == 1
    print("pre_push_gate self-test: ok")
    return 0


if __name__ == "__main__":
    sys.exit(self_test() if sys.argv[1:] == ["--self-test"] else guarded_main())
