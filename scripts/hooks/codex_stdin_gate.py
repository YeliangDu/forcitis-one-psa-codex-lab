#!/usr/bin/env python3
"""PreToolUse 钩子（Bash）：`codex exec` 未接管标准输入就拒绝，未显式禁网则告警。

起因：不带 `< /dev/null` 的 codex exec 会停在「Reading additional input from stdin」干等（one-coordinator 2026-10-17，SESSION_PLAYBOOK TEAM-34、35）。
放行：同一段命令里有输入重定向（`<`、`<<`、`<<<`）、或前面接着管道（`x | codex exec -`）。
只看命令位置的 codex（echo、grep 里写的不算），`bash -c '…'` 的引号内容递归检查；读不懂的命令行含 `codex exec` 时按拒绝。
退出码 2 + 标准错误 = 拒绝；0 + JSON additionalContext = 告警；0 + 无输出 = 放行。拒绝不叠加告警。
出错一律拒绝（崩溃的退出码 1 对钩子来说是「不拦截」）。
用法：codex_stdin_gate.py  |  codex_stdin_gate.py --self-test
"""
import json
import os
import re
import shlex
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from command_words import ENV, KEYWORDS, SEPARATORS, SHELLS, WRAPPERS, name_of, plain, tokens_of, unquote_dollar  # noqa: E402,F401

CODEX_VALUE_OPTIONS = {"-c", "--config", "-m", "--model", "-p", "--profile", "-C", "--cd", "-s", "--sandbox", "-a", "--ask-for-approval", "--enable", "--disable", "-i", "--image"}
REDIRECTS = {"<", "<<", "<<-", "<<<", "<&"}
MESSAGE = ("codex exec / pi 没有关闭标准输入，会一直读标准输入干等（codex 停在「Reading additional input from stdin」，pi 在没有终端时先读完标准输入才开始）。"
           "在命令末尾加 `< /dev/null`（SESSION_PLAYBOOK TEAM-34）；确需从管道或文件喂提示词时用 `| codex exec -`、`| pi -p` 或 `< 文件`。")
NETWORK_WARNING = ("告警：codex exec 未显式禁网，或使用了 danger-full-access / --dangerously-bypass-approvals-and-sandbox。"
                   "按 SESSION_PLAYBOOK TEAM-34 使用 `-c sandbox_workspace_write.network_access=false` 与受限沙箱（或 `-s read-only`）；"
                   "前端命令使用 `pnpm --offline --config.verify-deps-before-run=false`。本告警不拦截调用。")


def stdin_open(seg, piped_in):
    return not piped_in and not any(t in REDIRECTS for t in seg)


AGENT = re.compile(r"\b(?:codex|pi)\b")


PI_SUBCOMMANDS = {"install", "remove", "uninstall", "update", "list", "config", "auth", "mcp"}
PI_META = {"-h", "--help", "-v", "--version", "--list-models", "--export"}


def is_pi_run(seg, i):
    """True when seg[i] is pi started on the agent path. Without a TTY pi reads all of standard input before it starts (pi 1.3.0, dist/main.js readPipedStdin) whatever the mode,
    so every pi run waits on it except the package commands (install, list, auth, ...) and the meta options (--help, --version, ...)."""
    rest = seg[i + 1:]
    if name_of(seg[i]) != "pi" or any(t in PI_META for t in rest):
        return False
    first = next((t for t in rest if not t.startswith("-")), None)
    return not (first in PI_SUBCOMMANDS and rest.index(first) == 0)


def is_codex_exec(seg, i):
    """True when seg[i] is codex and its first sub-command is exec (not --help)."""
    if name_of(seg[i]) != "codex":
        return False
    j = i + 1
    while j < len(seg) and seg[j].startswith("-"):
        j += 2 if seg[j] in CODEX_VALUE_OPTIONS else 1
    return j < len(seg) and seg[j] in ("exec", "e") and not any(t in ("--help", "-h") for t in seg[j + 1:])


def codex_invocations(command, depth=0, closed=False):
    if not AGENT.search(plain(command)):
        return
    try:
        tokens = tokens_of(command)
    except ValueError:
        if (re.search(r"\bcodex\b", plain(command)) and re.search(r"\bexec\b", plain(command))) or re.search(r"(?<![\w./-])pi(?![\w-])", plain(command)):
            yield [], True
        return
    segments, cur, piped = [], [], False
    for token in tokens:
        if token in SEPARATORS or set(token) <= set(";&|()"):
            if cur:
                segments.append((cur, piped))
            cur, piped = [], token in ("|", "|&")
        else:
            cur.append(token)
    if cur:
        segments.append((cur, piped))
    for seg, piped_in in segments:
        if depth < 3:
            for token in seg:
                if "$(" in token and AGENT.search(plain(token)):
                    yield from codex_invocations(token.replace("$(", " ; ").replace(")", " ; "), depth + 1, piped_in or closed)
        i = 0
        while i < len(seg):
            tok = seg[i]
            if ENV.match(tok) or tok in KEYWORDS:
                i += 1
            elif tok.lower() in WRAPPERS:
                valued = WRAPPERS[tok.lower()]
                i += 1
                while i < len(seg) and (seg[i].startswith("-") or ENV.match(seg[i]) or (tok.lower() == "timeout" and re.fullmatch(r"[0-9.]+[smhd]?", seg[i]))):
                    i += 2 if seg[i] in valued else 1
            else:
                break
        if i >= len(seg):
            continue
        if is_codex_exec(seg, i):
            yield seg[i + 1:], stdin_open(seg, piped_in or closed)
        elif is_pi_run(seg, i):
            yield None, stdin_open(seg, piped_in or closed)
        elif depth < 3 and name_of(seg[i]) in SHELLS:
            for token in seg[i + 1:]:
                if " " in token and AGENT.search(plain(token)):
                    yield from codex_invocations(token, depth + 1, piped_in or closed or any(t in REDIRECTS for t in seg))


def unclosed(command):
    return any(open_stdin for _, open_stdin in codex_invocations(command))


def network_disabled(args):
    """True only when the sandbox mode is given explicitly and cannot reach the network: read-only, or workspace-write with network_access=false (the local default mode is danger-full-access)."""
    explicit, mode, unsafe, i = False, None, False, 0
    while i < len(args):
        token = args[i]
        option, _, inline = token.partition("=")
        if token == "--":
            break
        if option in ("-c", "--config", "-s", "--sandbox"):
            value = inline if "=" in token else (args[i + 1] if i + 1 < len(args) else "")
            if option in ("-c", "--config"):
                if re.fullmatch(r"sandbox_workspace_write\.network_access\s*=\s*false", value):
                    explicit = True
                found = re.fullmatch(r"sandbox_mode\s*=\s*[\"']?([a-z-]+)[\"']?", value)
                if found:
                    mode = found.group(1)
            else:
                mode = value
            i += 1 if "=" in token else 2
        else:
            unsafe |= token in ("--dangerously-bypass-approvals-and-sandbox", "--yolo")
            i += 2 if token in CODEX_VALUE_OPTIONS else 1
    return not unsafe and (mode == "read-only" or (mode == "workspace-write" and explicit))


def main():
    try:
        payload = json.load(sys.stdin)
    except ValueError:
        print("codex_stdin_gate.py 读不懂钩子输入（不是 JSON），按失败处理", file=sys.stderr)
        return 2
    if not isinstance(payload, dict):
        print("codex_stdin_gate.py 读不懂钩子输入（不是对象），按失败处理", file=sys.stderr)
        return 2
    if payload.get("tool_name", "Bash") != "Bash":
        return 0
    tool_input = payload.get("tool_input")
    command = tool_input.get("command") if isinstance(tool_input, dict) else None
    if isinstance(command, str):
        invocations = list(codex_invocations(command))
        if any(open_stdin for _, open_stdin in invocations):
            print(MESSAGE, file=sys.stderr)
            return 2
        if any(args is not None and not network_disabled(args) for args, _ in invocations):
            print(json.dumps({"hookSpecificOutput": {"hookEventName": "PreToolUse", "additionalContext": NETWORK_WARNING}}))
    return 0


def guarded_main():
    try:
        return main()
    except Exception as error:
        print(f"codex_stdin_gate.py 出错（{error.__class__.__name__}: {error}），按失败处理", file=sys.stderr)
        return 2


def hook_cases():
    allow = (
        "pi -p --no-session --model deepseek/deepseek-flash --tools read,ls,grep,find 'x' < /dev/null",
        "pi -p x < /dev/null",
        "pi list",
        "pi --version",
        "pi install npm:foo",
        "/x/bin/pix -p x",
        "codex exec -s workspace-write -c sandbox_workspace_write.network_access=false p < /dev/null",
        "codex -s workspace-write -c 'sandbox_workspace_write.network_access=false' exec p < /dev/null",
        "codex exec --sandbox=workspace-write --config=sandbox_workspace_write.network_access=false p < /dev/null",
        "codex exec p -c sandbox_mode=workspace-write -c sandbox_workspace_write.network_access=false < /dev/null",
        "codex exec -c sandbox_mode=read-only p < /dev/null",
        "codex exec -s read-only p < /dev/null",
        "codex --sandbox read-only exec p < /dev/null",
        "codex exec --sandbox=read-only p < /dev/null",
        "cat card.md | codex exec -s workspace-write -c sandbox_workspace_write.network_access=false -",
        "bash -c 'codex exec -s read-only p' < /dev/null",
        "cat > card.md <<'EOF'\ncodex exec p\nEOF",
        "echo 'codex exec p'", "grep -n 'codex exec' file",
        "git commit -m 'codex exec p'",
        "git commit -m \"$(cat <<'EOF'\ncodex exec p\nEOF\n)\"",
        "git status", "codex --version", "codex exec --help",
    )
    warn = (
        "codex exec p < /dev/null",
        "codex exec -s workspace-write p < /dev/null",
        "codex exec -c sandbox_workspace_write.network_access=true p < /dev/null",
        "codex exec -s danger-full-access p < /dev/null",
        "codex exec --sandbox danger-full-access -c sandbox_workspace_write.network_access=false p < /dev/null",
        "codex exec --dangerously-bypass-approvals-and-sandbox p < /dev/null",
        "codex exec -s read-only p --yolo < /dev/null",
        "codex exec -c sandbox_workspace_write.network_access=false p < /dev/null",
        "codex -c sandbox_workspace_write.network_access=false exec p < /dev/null",
        "codex exec -s workspace-write p < /dev/null",
        "codex exec p < /dev/null",
        "codex exec -c sandbox_workspace_write.network_access=false -c sandbox_mode=danger-full-access p < /dev/null",
        "codex exec -c sandbox_workspace_write.network_access=false -c 'sandbox_mode=\"danger-full-access\"' p < /dev/null",
        "codex exec -c foo.sandbox_workspace_write.network_access=false p < /dev/null",
        "codex exec -c sandbox_workspace_write.network_access=falsey p < /dev/null",
        "codex --dangerously-bypass-approvals-and-sandbox exec -s read-only p < /dev/null",
        "codex exec -s read-only p --dangerously-bypass-approvals-and-sandbox < /dev/null",
        "codex exec -c sandbox_workspace_write.network_access=false p -s danger-full-access < /dev/null",
        "codex exec 'network_access=false' < /dev/null",
        "echo 'network_access=false'; codex exec p < /dev/null",
        "codex exec -s read-only p < /dev/null; codex exec q < /dev/null",
        "env A=1 timeout 60 codex exec p < /dev/null",
        "bash -c 'codex exec p' < /dev/null",
        "echo \"$(codex exec p < /dev/null)\"",
        "cat card.md | codex exec -",
        "codex exec - <<'EOF'\nnetwork_access=false\nEOF",
    )
    deny = (
        "pi -p --no-session --model deepseek/deepseek-flash --tools read,ls 'x'",
        "pi 'x'",
        "pi --mode json 'x'",
        "/opt/bin/pi -p x",
        "pi --print 'x' | tail -n 5",
        "timeout 600 pi -p x",
        "cd w && pi -p x; echo done",
        "bash -c \"pi -p x\"",
        "out=$(pi -p x)",
        "codex exec p", "codex exec -s read-only p",
        "codex exec -c sandbox_workspace_write.network_access=false p",
        "codex exec --dangerously-bypass-approvals-and-sandbox p",
        "codex exec p < /dev/null; codex exec q",
    )
    return ([(command, 0, "allow") for command in allow]
            + [(command, 0, "warn") for command in warn]
            + [(command, 2, "deny") for command in deny])


def hook_self_test():
    script = Path(__file__).resolve()
    for runner in ([sys.executable, str(script)], [sys.executable, "-I", str(script)]):
        for command, status, kind in hook_cases():
            payload = {"tool_name": "Bash", "tool_input": {"command": command}}
            result = subprocess.run(["/usr/bin/env", "-i", "LC_ALL=C", *runner], input=json.dumps(payload),
                                    capture_output=True, text=True, encoding="utf-8", timeout=10)
            assert result.returncode == status, (runner, command, result)
            if kind == "allow":
                assert not result.stdout and not result.stderr, (runner, command, result)
            elif kind == "warn":
                assert result.stdout, (runner, command, result)
                output = json.loads(result.stdout)
                assert set(output) == {"hookSpecificOutput"}, (command, output)
                specific = output["hookSpecificOutput"]
                assert set(specific) == {"hookEventName", "additionalContext"}, (command, output)
                assert specific["hookEventName"] == "PreToolUse" and specific["additionalContext"], (command, output)
                assert "sandbox_workspace_write.network_access=false" in specific["additionalContext"], (command, output)
                assert not result.stderr, (command, result)
            else:
                assert not result.stdout and result.stderr.strip() == MESSAGE, (runner, command, result)


def self_test():
    bad = ("codex exec -m x -o out.md 'do it'", "cd w && codex exec -s read-only 'p'", "env A=1 codex exec p", "timeout 600 codex exec p", "/usr/local/bin/codex exec p",
           "codex exec p > out.txt 2>&1", "bash -c \"codex exec 'p'\"", "codex exec p; echo done", "codex exec 'unbalanced", "codex -c a=b exec p", "codex exec p 2>&1 | tail -n 5")
    ok = ("codex exec p < /dev/null", "codex exec p </dev/null 2>&1", "codex exec -o out.md 'p' < /dev/null", "cat card.md | codex exec -", "codex exec - < card.md", "codex exec p <<EOF\nx\nEOF",
          "codex exec p <<< 'x'", "bash -c \"codex exec p < /dev/null\"", "echo 'codex exec p'", "grep -n 'codex exec' file", "codex --version", "codex login status", "git status", "", "ls codex")
    for command in bad:
        assert unclosed(command), command
    for command in ok:
        assert not unclosed(command), command
    for command in ("codex exec - <<'EOF'\nit's fine\nEOF", "cat > card.md <<'EOF'\ncodex exec p\nEOF", "cat > card.md <<'EOF'\n用 `codex exec` 派工\nEOF", "codex exec \\\n  -o o.md \\\n  'p' \\\n  < /dev/null",
                    "codex exec --help", "codex exec p 0</dev/null", "codex exec p <&-", "codex exec '<p>x</p>' < /dev/null", "git commit -m \"$(cat <<'EOF'\ncodex exec x\nEOF\n)\""):
        assert not unclosed(command), command
    for command in ("# 派工\ncodex exec p", "ls # 先看\ncodex exec p", "for f in a b; do codex exec $f; done", "if true; then codex exec p; fi", "{ codex exec p; }", "! codex exec p", "timeout -s KILL 600 codex exec p",
                    "sudo -u me codex exec p", "env -u FOO codex exec p", "eval 'codex exec p'", "codex -c a=b -c c=d -c e=f -c g=h exec p", "codex exec '<p>请改</p>'", "codex exec p <(cat f)", "codex\nexec p 'x"):
        assert unclosed(command), command
    started = time.monotonic()
    assert unclosed("codex " * 50000 + "' exec")
    assert time.monotonic() - started < 5, "the fallback search must not backtrack quadratically"
    assert unclosed("codex e p"), "e is the alias of exec"
    assert not unclosed("cat card.md |\n  codex exec -"), "a pipe at the end of a line continues on the next"
    assert not unclosed("bash -c 'codex exec p' < /dev/null"), "the outer redirection closes stdin of the inner command"
    assert unclosed('out="$(codex exec p)"') and unclosed('echo "$(codex exec p)"'), "command substitutions inside double quotes"
    assert not unclosed('echo "$(codex exec p < /dev/null)"')
    assert unclosed("codex exec a < /dev/null; codex exec b"), "a second codex exec without redirection is still caught"
    for command in ("pi", 'pi "x"', "pi --mode json x", "pi --model m x", "pi -c", "pi --offline list", "pi --print=true x", "/x/bin/pi -p x", "pi -- -p", "pi -p x", "pi --print x", "timeout 600 pi -p x", "bash -c 'pi -p x'", "out=$(pi -p x)", "pi -p 'unbalanced", "pi --model m -p x | tail", 'echo "$(pi -p x)"'):
        assert unclosed(command), command
    for command in ("pi -p x < /dev/null", "pi < /dev/null", "pi --version", "pi -p --help", "pi -v", "pi --list-models", "pi install foo", "pi list", "pi update self", "pi auth check --provider deepseek", "pi mcp list", "pi config -l", "/x/bin/pix -p x", "mkdir pi", "cd pi -p", "/opt/pi/bin/x -p", "cat card.md | pi -p", "pip install x", "echo 'pi -p x'", "grep -p pi file", "pi -p x <<'EOF'\ntext\nEOF",
                    "bash -c 'pi -p x < /dev/null'", "git commit -m \"$(cat <<'EOF'\npi -p x\nEOF\n)\"", "ls pi", "api -p x", "pid -p x", "pi -p x 0</dev/null"):
        assert not unclosed(command), command
    assert not any(args is not None for args, _ in codex_invocations("pi -p x < /dev/null")), "pi has no sandbox settings: no network warning"
    hook_self_test()
    print("codex_stdin_gate self-test: ok")
    return 0


if __name__ == "__main__":
    sys.exit(self_test() if sys.argv[1:] == ["--self-test"] else guarded_main())
