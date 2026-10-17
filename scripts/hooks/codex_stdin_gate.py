#!/usr/bin/env python3
"""PreToolUse 钩子（Bash）：命令里的 `codex exec` 没有关闭或接管标准输入就拒绝。

起因：不带 `< /dev/null` 的 codex exec 会停在「Reading additional input from stdin」干等（one-coordinator 2026-10-17，SESSION_PLAYBOOK §5）。
放行：同一段命令里有输入重定向（`<`、`<<`、`<<<`）、或前面接着管道（`x | codex exec -`）。
只看命令位置的 codex（echo、grep 里写的不算），`bash -c '…'` 的引号内容递归检查；读不懂的命令行含 `codex exec` 时按拒绝。
退出码 2 + 标准错误 = 拒绝，0 = 放行；出错一律拒绝（崩溃的退出码 1 对钩子来说是「不拦截」）。
用法：codex_stdin_gate.py  |  codex_stdin_gate.py --self-test
"""
import json
import os
import re
import shlex
import sys

SEPARATORS = {";", "&&", "||", "|", "&", "(", ")", "|&"}
WRAPPERS = {"env", "command", "time", "nohup", "exec", "timeout", "nice", "sudo", "stdbuf", "builtin"}
SHELLS = {"bash", "sh", "zsh", "dash", "ksh"}
ENV = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*=")
MESSAGE = ("codex exec 没有关闭标准输入，会停在「Reading additional input from stdin」干等。"
           "在命令末尾加 `< /dev/null`（SESSION_PLAYBOOK §5）；确需从管道或文件喂提示词时用 `| codex exec -` 或 `< 文件`。")


def tokens_of(command):
    lexer = shlex.shlex(command.replace("`", " ; ").replace("\n", " ; "), posix=True, punctuation_chars=True)
    lexer.whitespace_split = True
    lexer.commenters = "#"
    return list(lexer)


def unclosed(command, depth=0):
    """True when some `codex exec` in the command line has its standard input left open."""
    if "codex" not in command:
        return False
    try:
        tokens = tokens_of(command)
    except ValueError:
        return re.search(r"\bcodex\b.*\bexec\b", command) is not None
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
        i = 0
        while i < len(seg) and (ENV.match(seg[i]) or seg[i] in WRAPPERS or seg[i].startswith("-") or re.fullmatch(r"[0-9.]+[smhd]?", seg[i])):
            i += 1
        if i < len(seg) and os.path.basename(seg[i]) == "codex" and "exec" in seg[i + 1:i + 5]:
            if not piped_in and not any(t.startswith("<") for t in seg):
                return True
        elif depth < 3 and i < len(seg) and os.path.basename(seg[i]) in SHELLS:
            for token in seg:
                if " " in token and "codex" in token and unclosed(token, depth + 1):
                    return True
    return False


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
    command = (payload.get("tool_input") or {}).get("command")
    if isinstance(command, str) and unclosed(command):
        print(MESSAGE, file=sys.stderr)
        return 2
    return 0


def guarded_main():
    try:
        return main()
    except Exception as error:
        print(f"codex_stdin_gate.py 出错（{error.__class__.__name__}: {error}），按失败处理", file=sys.stderr)
        return 2


def self_test():
    bad = ("codex exec -m x -o out.md 'do it'", "cd w && codex exec -s read-only 'p'", "env A=1 codex exec p", "timeout 600 codex exec p", "/usr/local/bin/codex exec p",
           "codex exec p > out.txt 2>&1", "bash -c \"codex exec 'p'\"", "codex exec p; echo done", "codex exec 'unbalanced", "codex -c a=b exec p", "codex exec p 2>&1 | tail -n 5")
    ok = ("codex exec p < /dev/null", "codex exec p </dev/null 2>&1", "codex exec -o out.md 'p' < /dev/null", "cat card.md | codex exec -", "codex exec - < card.md", "codex exec p <<EOF\nx\nEOF",
          "codex exec p <<< 'x'", "bash -c \"codex exec p < /dev/null\"", "echo 'codex exec p'", "grep -n 'codex exec' file", "codex --version", "codex login status", "git status", "", "ls codex")
    for command in bad:
        assert unclosed(command), command
    for command in ok:
        assert not unclosed(command), command
    assert unclosed("codex exec a < /dev/null; codex exec b"), "a second codex exec without redirection is still caught"
    print("codex_stdin_gate self-test: ok")
    return 0


if __name__ == "__main__":
    sys.exit(self_test() if sys.argv[1:] == ["--self-test"] else guarded_main())
