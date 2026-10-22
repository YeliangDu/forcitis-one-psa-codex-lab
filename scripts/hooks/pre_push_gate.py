#!/usr/bin/env python3
"""PreToolUse 钩子（Bash）：`git push` 之前运行 `scripts/precheck.sh --pre-push`（全部不起容器的单元测试与守护），没以「ready」收尾就拒绝推送。

用户 2026-10-16 在待决定收件箱选「启用」（20261016-one-quality-pre-push-hook）；起因：一个分支连续 3 次在 CI 里挂同一个源码扫描测试，本地从没跑到。
绕过：`git push --no-verify` 不会触发 git 钩子，所以留痕放在这里——命令含 --no-verify 时必须同时带 `PUSH_BYPASS_REASON="<理由>"`，否则拒绝；
放行时往 <仓库>/.claude/push-bypass.log（不入仓）追加一行（时间、分支、理由），推送者要在 PR 说明「绕过推送前钩子」一栏写明。
退出码 2 + 标准错误 = 拒绝，0 = 放行；出错一律拒绝（崩溃的退出码 1 对钩子来说是「不拦截」）。删除分支、--dry-run 的推送不拦。\n命令按 shell 切段分词，只在 push 段内找选项，理由只认同一段前的环境变量赋值（写在 echo 或注释里的不算）；检查的是工作区对 origin/main 的差异，不严格等于实际推送的提交。
注册到 .claude/settings.json 时必须写 "timeout": 1500 以上。
用法：pre_push_gate.py  |  pre_push_gate.py --self-test
"""
import datetime
import io
import json
import os
import re
import shlex
import subprocess
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import precheck_before_pr as pr  # noqa: E402
from command_words import name_of, plain, unquote_dollar  # noqa: E402

SEPARATORS = {";", "&&", "||", "|", "&", "(", ")", "\n"}
KEYWORDS = {"if", "then", "do", "else", "elif", "while", "until", "!", "{", "}", "fi", "done", "esac"}
# wrapper -> options that take a value; its first plain argument (a duration) is skipped for timeout, and for xargs the options listed
WRAPPERS = {"command": set(), "env": {"-u", "-C", "-S"}, "time": {"-f", "-o"}, "exec": {"-a"}, "nohup": set(), "sudo": {"-u", "-g", "-h", "-p", "-C", "-D", "-R", "-T", "-U"},
            "builtin": set(), "timeout": {"-s", "-k"}, "nice": {"-n"}, "xargs": {"-n", "-I", "-P", "-L", "-d", "-a", "-E", "-s"}, "stdbuf": {"-i", "-o", "-e"}}
SHELLS = {"bash", "sh", "zsh", "dash", "ksh"}
PUSH_VALUE_OPTIONS = {"-o", "--push-option", "--repo", "--receive-pack", "--exec"}
GIT_VALUE_OPTIONS = {"-C", "-c", "--git-dir", "--work-tree", "--namespace", "--exec-path", "--super-prefix"}
ENV = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*=")
TIMEOUT_SECONDS = 1400


def segments(command):
    """The simple commands of a shell line as token lists; None when the line cannot be tokenised."""
    lexer = shlex.shlex(unquote_dollar(command).replace("`", " ; "), posix=True, punctuation_chars=True)
    lexer.whitespace_split = True
    lexer.commenters = "#"
    try:
        tokens = list(lexer)
    except ValueError:
        return None
    out, cur = [], []
    for token in tokens:
        if token in SEPARATORS or set(token) <= set(";&|()"):
            if cur:
                out.append(cur)
            cur = []
        else:
            cur.append(token)
    if cur:
        out.append(cur)
    return out


def pushes(command, cwd="."):
    """Every `git push` found in a command line: (directory, tokens after `push`, environment assignments of its segment). A line that cannot be tokenised but mentions `git ... push` counts as one push."""
    found, here = [], cwd
    segs = segments(command)
    if segs is None:
        return [(cwd, [], {})] if re.search(r"\bgit\b.*\bpush\b", plain(command)) else []
    for seg in segs:
        env, i = {}, 0
        while i < len(seg):
            tok = seg[i] if ENV.match(seg[i]) else seg[i].lower()
            if ENV.match(tok):
                k, v = tok.split("=", 1)
                env[k] = v
                i += 1
            elif tok in KEYWORDS:
                i += 1
            elif tok in WRAPPERS:
                valued = WRAPPERS[tok]
                i += 1
                while i < len(seg) and (seg[i].startswith("-") or ENV.match(seg[i]) and tok == "env" or (tok == "timeout" and re.fullmatch(r"[0-9.]+[smhd]?", seg[i]))):
                    if ENV.match(seg[i]) and not seg[i].startswith("-"):
                        k, v = seg[i].split("=", 1)
                        env[k] = v
                    i += 2 if seg[i] in valued else 1
            else:
                break
        rest = seg[i:]
        if not rest:
            continue
        if rest[0] == "cd" and len(rest) > 1:
            here = os.path.normpath(os.path.join(here, os.path.expanduser(rest[1])))
            continue
        if name_of(rest[0]) in SHELLS or rest[0].lower() == "eval":
            flag = next((k for k, t in enumerate(rest[1:], 1) if re.fullmatch(r"-[a-z]*c", t)), None) if rest[0].lower() != "eval" else 0
            if flag is not None and flag + 1 < len(rest):
                found += pushes(rest[flag + 1], here)
            continue
        if name_of(rest[0]) != "git":
            continue
        j, where = 1, here
        while j < len(rest) and rest[j].startswith("-"):
            opt = rest[j]
            if opt == "-C" and j + 1 < len(rest):
                where = os.path.normpath(os.path.join(where, rest[j + 1]))
            if opt in GIT_VALUE_OPTIONS:
                j += 1
            j += 1
        if j < len(rest) and rest[j] == "push":
            found.append((where, rest[j + 1:], env))
    return found


def judge(args, env):
    """One push: 'allow' (nothing to check), 'bypass', 'deny' or 'run'; the second value is the reason or the message."""
    options, positional, skip = [], [], False
    for index, a in enumerate(args):
        if skip:
            skip = False
        elif a == "--":
            positional += args[index + 1:]
            break
        elif a in PUSH_VALUE_OPTIONS:
            options.append(a)
            skip = True
        elif a.startswith("-"):
            options.append(a)
        else:
            positional.append(a)
    if any(a in ("--delete", "-d", "--dry-run", "-n") for a in options):
        return "allow", None
    refspecs = positional[1:]
    if refspecs and all(a.startswith(":") for a in refspecs):
        return "allow", None
    if "--no-verify" in options:
        reason = env.get("PUSH_BYPASS_REASON", "").strip()
        if not reason or reason.startswith("-"):
            return "deny", "git push --no-verify 要绕过推送前钩子，必须在同一条 push 命令前写理由：PUSH_BYPASS_REASON=\"<理由>\" git push --no-verify …（理由会记入 .claude/push-bypass.log，并要在 PR 说明「绕过推送前钩子」一栏写明）"
        return "bypass", reason
    return "run", None


def decide(command, cwd="."):
    """('allow'|'run'|'bypass'|'deny', info, directory) for a whole command line; deny beats run beats bypass beats allow."""
    verdicts = [(*judge(args, env), where) for where, args, env in pushes(command or "", cwd)]
    for kind in ("deny", "run", "bypass"):
        for v in verdicts:
            if v[0] == kind:
                return v
    return "allow", None, cwd


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
        print("pre_push_gate.py 读不懂钩子输入（不是 JSON），按失败处理", file=sys.stderr)
        return 2
    if not isinstance(payload, dict):
        print("pre_push_gate.py 读不懂钩子输入（不是对象），按失败处理", file=sys.stderr)
        return 2
    if payload.get("tool_name", "Bash") != "Bash":
        return 0
    action, info, where = decide((payload.get("tool_input") or {}).get("command"), payload.get("cwd") or os.getcwd())
    if action == "allow":
        return 0
    if action == "deny":
        print(info, file=sys.stderr)
        return 2
    root = pr.repository_of(where)
    if action == "bypass":
        path = log_bypass(root, branch_of(root), info)
        print(f"推送前钩子已按理由绕过并留痕（{path}）：{info}", file=sys.stderr)
        return 0
    checked = pr.tree_state(root)
    try:
        run = subprocess.run([os.path.join(root, "scripts", "precheck.sh"), "--pre-push"], cwd=root, capture_output=True, text=True,
                             encoding="utf-8", errors="replace", timeout=TIMEOUT_SECONDS)
    except subprocess.TimeoutExpired:
        print(f"precheck.sh --pre-push 超过 {TIMEOUT_SECONDS} 秒未结束，不能推送", file=sys.stderr)
        return 2
    if "unknown option: --pre-push" in run.stdout + run.stderr:
        print("这个检出的 scripts/precheck.sh 还没有 --pre-push：先把 origin/main 合进当前分支（git merge origin/main）再推送。", file=sys.stderr)
        return 2
    reason = pr.verdict(run.stdout + run.stderr, run.returncode, "推送", "git push")
    if reason:
        print(reason + "\n（确需绕过：PUSH_BYPASS_REASON=\"<理由>\" git push --no-verify …，会留痕）", file=sys.stderr)
        return 2
    pr.record_pass(root, checked=checked)
    return 0


def guarded_main():
    try:
        return main()
    except Exception as error:
        print(f"pre_push_gate.py 出错（{error.__class__.__name__}: {error}），按失败处理，不能推送", file=sys.stderr)
        return 2


def self_test():
    runs = ("git push", "git push origin HEAD", "cd x && git push -u origin b", "git -C /a/b push origin c", "git -c core.x=y push", "(git push)", "bash -c \"git push\"",
            "git push --force-with-lease", "git push -u origin HEAD 2>&1 | tail -n 20", "git push origin feat :old", "git push x && git push --delete y", "git push && date -d now",
            "git --no-pager push", "git --git-dir=x push", "git -C 'a b' push", "command git push", "env FOO=1 git push", "GIT_DIR=x git push", "git push origin HEAD:refs/heads/x",
            "git push 'unbalanced", "sh -c 'git push origin b'", "git push -f", "if true; then git push; fi", "for i in 1; do git push; done", "while :; do git push; break; done", "! git push", "{ git push; }", "time -p git push",
            "sudo -u x git push", "env -i git push", "env -u X git push", "command -p git push", "timeout 600 git push", "nice -n 5 git push", "xargs -n 1 git push", "eval 'git push'", "/usr/bin/git push", "`git push`",
            "bash -lc 'git push'", "echo a | xargs git push", "git push -- --delete", "git push -o -d origin b", "git push --push-option -n origin b", "git push origin +HEAD", "git push --mirror", "git push -ud", "git push origin HEAD:refs/heads/delete")
    for command in runs:
        assert decide(command)[0] == "run", command
    allows = ("git pushd", "git status", "git log --oneline", "echo hi", "", None, "git push origin --delete old", "git push --dry-run", "git push -n", "git push origin :old :older", "git commit -m 'x'",
              "gh pr create", "echo 'git push'", "git commit -m \"then git push\"", "grep -n push file", "git push --delete origin b | tail -n 3", "git push -o ci.skip -d origin b")
    for command in allows:
        assert decide(command)[0] == "allow", command
    assert decide("git push --no-verify")[0] == "deny", "bypass without a reason is refused"
    assert decide("git push --no-verify origin b | tail -n 3")[0] == "deny", "a pipe and -n of another command do not turn a push into a dry run"
    assert decide("PUSH_BYPASS_REASON= git push --no-verify")[0] == "deny", "an empty reason is no reason"
    assert decide("echo PUSH_BYPASS_REASON=x; git push --no-verify")[0] == "deny", "a reason written for another command is no reason"
    assert decide("git push --no-verify # PUSH_BYPASS_REASON=x")[0] == "deny", "a reason in a comment is no reason"
    assert decide("PUSH_BYPASS_REASON=\"--no-verify\" git push")[0] == "run", "an option-looking reason bypasses nothing"
    assert decide("PUSH_BYPASS_REASON=--x git push --no-verify")[0] == "deny"
    assert decide('PUSH_BYPASS_REASON="CI 已挂在 runner，先推送看日志" git push --no-verify origin b')[:2] == ("bypass", "CI 已挂在 runner，先推送看日志")
    assert decide("PUSH_BYPASS_REASON='a b' git push --no-verify")[1] == "a b"
    assert decide("PUSH_BYPASS_REASON=x git push --no-verify | tail -n 3")[:2] == ("bypass", "x"), "a reason on the push itself counts even when piped"
    assert decide("PUSH_BYPASS_REASON=x git push")[0] == "run", "a reason without --no-verify bypasses nothing"
    assert decide('PUSH_BYPASS_REASON=x git push --no-verify; git push')[0] == "run", "a second plain push still runs the checks"
    assert decide("git push origin b", "/r")[2] == "/r" and decide("cd /other && git push", "/r")[2] == "/other" and decide("git -C /w push", "/r")[2] == "/w", "the checks run where the push happens"
    with tempfile.TemporaryDirectory() as root:
        path = log_bypass(root, "claude/b", "理由", datetime.datetime(2026, 10, 16, 12, 0, 0))
        log_bypass(root, "claude/c", "再一次", datetime.datetime(2026, 10, 16, 12, 5, 0))
        assert open(path, encoding="utf-8").read() == "2026-10-16 12:00:00\tclaude/b\t理由\n2026-10-16 12:05:00\tclaude/c\t再一次\n"
    assert pr.verdict("SUMMARY: 1 PASS, 1 FAIL, 0 SKIP (base origin/main) — not ready\n", 1, "推送", "git push").count("git push") == 1
    from unittest import mock
    for output, code, marked in (("SUMMARY: 3 PASS, 0 FAIL, 0 SKIP (base origin/main) — ready\n", 0, 1), ("SUMMARY: 2 PASS, 1 FAIL, 0 SKIP (base origin/main) — not ready\n", 1, 0)):
        finished = subprocess.CompletedProcess([], code, output, "")
        with mock.patch.object(subprocess, "run", return_value=finished), mock.patch.object(pr, "record_pass") as record, mock.patch.object(pr, "repository_of", return_value="/r"), \
                mock.patch.object(sys, "stdin", io.StringIO(json.dumps({"tool_name": "Bash", "tool_input": {"command": "git push"}, "cwd": "/r"}))), mock.patch.object(sys, "stderr", io.StringIO()):
            assert main() == (0 if marked else 2) and record.call_count == marked and (not marked or "checked" in record.call_args.kwargs), "only a push the checks let through leaves a pass mark"
    print("pre_push_gate self-test: ok")
    return 0


if __name__ == "__main__":
    sys.exit(self_test() if sys.argv[1:] == ["--self-test"] else guarded_main())
