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
import time

SEPARATORS = {";", "&&", "||", "|", "&", "(", ")", "|&"}
KEYWORDS = {"if", "then", "do", "else", "elif", "while", "until", "!", "{", "}", "fi", "done", "esac"}
# wrapper -> options that take a value
WRAPPERS = {"command": set(), "env": {"-u", "-C", "-S"}, "time": {"-f", "-o"}, "exec": {"-a"}, "nohup": set(), "sudo": {"-u", "-g", "-h", "-p", "-C", "-D", "-R", "-T", "-U"},
            "builtin": set(), "timeout": {"-s", "-k"}, "nice": {"-n"}, "stdbuf": {"-i", "-o", "-e"}}
SHELLS = {"bash", "sh", "zsh", "dash", "ksh", "eval"}
CODEX_VALUE_OPTIONS = {"-c", "--config", "-m", "--model", "-p", "--profile", "-C", "--cd", "-s", "--sandbox", "-a", "--ask-for-approval", "--enable", "--disable", "-i", "--image"}
REDIRECTS = {"<", "<<", "<<-", "<<<", "<&"}
ENV = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*=")
HEREDOC = re.compile(r"<<-?\s*(?:'([^']+)'|\"([^\"]+)\"|\\?([A-Za-z_][A-Za-z0-9_]*))")
MESSAGE = ("codex exec 没有关闭标准输入，会停在「Reading additional input from stdin」干等。"
           "在命令末尾加 `< /dev/null`（SESSION_PLAYBOOK §5）；确需从管道或文件喂提示词时用 `| codex exec -` 或 `< 文件`。")


def without_heredoc_bodies(command):
    """The lines of a command line, with here-document bodies dropped (a body is text, not commands)."""
    lines, out, end = command.replace("\\\n", " ").split("\n"), [], None
    for line in lines:
        if end is not None:
            if line.strip() == end:
                end = None
            continue
        out.append(line)
        found = HEREDOC.search(line)
        if found and "<<<" not in line[found.start():found.start() + 3]:
            end = next(g for g in found.groups() if g)
    return out


def tokens_of(command):
    """Shell tokens of a command line; lines are tokenised on their own so a # comment ends with its line, and a quote left open continues onto the next line."""
    tokens, pending = [], ""
    for line in without_heredoc_bodies(command):
        pending = pending + "\n" + line if pending else line
        lexer = shlex.shlex(pending.replace("`", " ; "), posix=True, punctuation_chars=True)
        lexer.whitespace_split = True
        lexer.commenters = "#"
        try:
            found = list(lexer)
        except ValueError:
            continue
        tokens += found if found and found[-1] in ("|", "&&", "||", "|&") else found + [";"]
        pending = ""
    if pending:
        raise ValueError("unterminated quote")
    return tokens


def stdin_open(seg, piped_in):
    return not piped_in and not any(t in REDIRECTS for t in seg)


def is_codex_exec(seg, i):
    """True when seg[i] is codex and its first sub-command is exec (not --help)."""
    if os.path.basename(seg[i]) != "codex":
        return False
    j = i + 1
    while j < len(seg) and seg[j].startswith("-"):
        j += 2 if seg[j] in CODEX_VALUE_OPTIONS else 1
    return j < len(seg) and seg[j] in ("exec", "e") and not any(t in ("--help", "-h") for t in seg[j + 1:])


def unclosed(command, depth=0, closed=False):
    """True when some `codex exec` in the command line has its standard input left open."""
    if "codex" not in command:
        return False
    try:
        tokens = tokens_of(command)
    except ValueError:
        return re.search(r"\bcodex\b", command) is not None and re.search(r"\bexec\b", command) is not None
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
                if "$(" in token and "codex" in token and unclosed(token.replace("$(", " ; ").replace(")", " ; "), depth + 1, piped_in or closed):
                    return True
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
        if i >= len(seg):
            continue
        if is_codex_exec(seg, i):
            if stdin_open(seg, piped_in or closed):
                return True
        elif depth < 3 and os.path.basename(seg[i]) in SHELLS:
            for token in seg[i + 1:]:
                if " " in token and "codex" in token and unclosed(token, depth + 1, piped_in or closed or any(t in REDIRECTS for t in seg)):
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
    tool_input = payload.get("tool_input")
    command = tool_input.get("command") if isinstance(tool_input, dict) else None
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
    print("codex_stdin_gate self-test: ok")
    return 0


if __name__ == "__main__":
    sys.exit(self_test() if sys.argv[1:] == ["--self-test"] else guarded_main())
