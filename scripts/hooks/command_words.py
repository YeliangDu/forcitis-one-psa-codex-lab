"""Shared by the Bash PreToolUse gates: the shell resolves `GIT`, `gi""t` and a backslash-split name to the git command (macOS file systems ignore case), so a gate must compare command names after that normalisation.
GOV-1022-QUA-24 round 2 (opus review of #653): `$'\\x73qlcmd'` is decoded as bash does, a backslash-newline is deleted (not turned into a space), backticks are command substitutions (structured mode),
`bash -lc`, multi-word `eval`, `env -S`, here-documents / here-strings / pipes feeding a shell are looked into, and a `$` inside single quotes is a plain character."""
import collections
import os
import re
import shlex

ANSI_QUOTE = re.compile(r"\$'((?:[^'\\]|\\.)*)'", re.S)
DOLLAR_DOUBLE = re.compile(r'\$(?=")')
QUOTES = re.compile(r"[\\'\"]")
PUA_DOLLAR, PUA_TICK = "\ue000", "\ue001"  # `$` and a backtick that sit inside single quotes: plain characters, not expansions
SIMPLE_ESCAPES = {"a": "\a", "b": "\b", "e": "\x1b", "E": "\x1b", "f": "\f", "n": "\n", "r": "\r", "t": "\t", "v": "\v", "\\": "\\", "'": "'", '"': '"', "?": "?"}


def decode_ansi_c(body):
    """The text of a bash `$'...'` word: \\xHH, \\NNN, \\uHHHH, \\UHHHHHHHH, \\cX and the one-letter escapes decoded."""
    out, i = [], 0
    while i < len(body):
        char = body[i]
        if char != "\\" or i + 1 >= len(body):
            out.append(char)
            i += 1
            continue
        nxt = body[i + 1]
        if nxt in SIMPLE_ESCAPES:
            out.append(SIMPLE_ESCAPES[nxt])
            i += 2
            continue
        found = None
        if nxt == "x":
            found = re.match(r"[0-9a-fA-F]{1,2}", body[i + 2:])
            skip, base = 2, 16
        elif nxt in "01234567":
            found = re.match(r"[0-7]{1,3}", body[i + 1:])
            skip, base = 1, 8
        elif nxt in "uU":
            found = re.match(r"[0-9a-fA-F]{1,%d}" % (4 if nxt == "u" else 8), body[i + 2:])
            skip, base = 2, 16
        if found:
            try:
                out.append(chr(int(found.group(), base)))
            except (ValueError, OverflowError):
                out.append("\\" + nxt)
                i += 2
                continue
            i += skip + len(found.group())
        elif nxt == "c" and i + 2 < len(body):
            out.append(chr(ord(body[i + 2]) & 0x1f))
            i += 3
        else:
            out.append("\\" + nxt)
            i += 2
    return "".join(out).split("\x00")[0]


def unquote_dollar(command):
    """`$'git'`, `$'\\x67it'` and `$"git"` run git in bash and zsh, but shlex reads them as the word `$git`: decode the ANSI-C form into a plain single-quoted word and drop the `$` in front of a double quote."""
    command = ANSI_QUOTE.sub(lambda m: "'" + decode_ansi_c(m.group(1)).replace("'", "'\\''") + "'", command)
    return DOLLAR_DOUBLE.sub("", command)


def plain(command):
    """Text for pre-filters: ANSI-C words decoded, line continuations removed, quotes and backslashes removed, lower-cased. Only ever used to decide whether a gate looks closer, never to run anything."""
    return QUOTES.sub("", unquote_dollar(command.replace("\\\n", ""))).lower() if isinstance(command, str) else ""


def neutralize(text, backticks=True):
    """`text` with a `$` or backtick inside single quotes replaced by a private character (so it is never read as an expansion) and, when `backticks`,
    a backtick substitution outside single quotes rewritten as `$(...)`. Comments are skipped, so an apostrophe in `# don't` does not open a quote."""
    out, i, single, double = [], 0, False, False
    while i < len(text):
        char = text[i]
        if single:
            out.append(PUA_DOLLAR if char == "$" else PUA_TICK if char == "`" else char)
            single = char != "'"
        elif char == "\\" and i + 1 < len(text):
            out.append(text[i:i + 2])
            i += 1
        elif char == "'" and not double:
            out.append(char)
            single = True
        elif char == '"':
            out.append(char)
            double = not double
        elif char == "`" and backticks:
            end = i + 1
            while end < len(text) and text[end] != "`":
                end += 2 if text[end] == "\\" else 1
            if end < len(text):
                out.append("$(" + text[i + 1:end] + ")")
                i = end
            else:
                out.append(char)
        elif char == "#" and not double and (i == 0 or text[i - 1] in " \t\n;|&("):
            end = text.find("\n", i)
            end = len(text) if end < 0 else end
            out.append(text[i:end])
            i = end - 1
        else:
            out.append(char)
        i += 1
    return "".join(out)


def restore(token):
    return token.replace(PUA_DOLLAR, "$").replace(PUA_TICK, "`")


def name_of(token):
    """Command name of a token as the shell finds it: directory dropped, lower-cased."""
    return os.path.basename(token).lower()


SEPARATORS = {";", "&&", "||", "|", "&", "(", ")", "|&"}
KEYWORDS = {"if", "then", "do", "else", "elif", "while", "until", "!", "{", "}", "fi", "done", "esac"}
# wrapper -> options that take a value
WRAPPERS = {"command": set(), "env": {"-u", "-C", "-S"}, "time": {"-f", "-o"}, "exec": {"-a"}, "nohup": set(), "sudo": {"-u", "-g", "-h", "-p", "-C", "-D", "-R", "-T", "-U"},
            "builtin": set(), "timeout": {"-s", "-k"}, "nice": {"-n"}, "stdbuf": {"-i", "-o", "-e"}}
SHELLS = {"bash", "sh", "zsh", "dash", "ksh", "eval"}
ENV = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*=")
HEREDOC = re.compile(r"<<-?\s*(?:'([^']+)'|\"([^\"]+)\"|\\?([A-Za-z_][A-Za-z0-9_]*))")


def without_heredoc_bodies(command):
    """The lines of a command line, with here-document bodies dropped (a body is text, not commands)."""
    lines, out, end = command.replace("\\\n", "").split("\n"), [], None
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


def heredoc_bodies(command):
    """The here-document bodies of a command line (a shell fed by one runs them)."""
    lines, bodies, end, cur = command.replace("\\\n", "").split("\n"), [], None, []
    for line in lines:
        if end is not None:
            if line.strip() == end:
                bodies.append("\n".join(cur))
                end, cur = None, []
            else:
                cur.append(line)
            continue
        found = HEREDOC.search(line)
        if found and "<<<" not in line[found.start():found.start() + 3]:
            end = next(g for g in found.groups() if g)
    if end is not None:
        bodies.append("\n".join(cur))
    return bodies


def tokens_of(command, structured=False):
    """Shell tokens of a command line; lines are tokenised on their own so a # comment ends with its line, and a quote left open continues onto the next line.
    `structured` (simple_commands): backticks are `$(...)` substitutions and a `$` in single quotes is neutralised; the other gates keep the old reading (a backtick starts a new command)."""
    tokens, pending = [], ""
    lines = without_heredoc_bodies(unquote_dollar(command))
    if structured:
        lines = neutralize("\n".join(lines)).split("\n")
    for line in lines:
        pending = pending + "\n" + line if pending else line
        lexer = shlex.shlex(pending if structured else pending.replace("`", " ; "), posix=True, punctuation_chars=True)
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


Command = collections.namedtuple("Command", "argv env unresolved redirected piped", defaults=(False,))
VARIABLE = re.compile(r"\$\{([A-Za-z_][A-Za-z0-9_]*)\}|\$([A-Za-z_][A-Za-z0-9_]*)")
SUBSTITUTION = re.compile(r"\$\(([^()]*)\)")


def expand(token, variables):
    """(token with the known variables substituted, True when a `$` expansion is left that cannot be resolved here)."""
    for _ in range(5):
        new = VARIABLE.sub(lambda m: variables.get(m.group(1) or m.group(2), m.group(0)), token)
        if new == token:
            break
        token = new
    return token, "$" in token


SHORT_C = re.compile(r"-[A-Za-z]*c[A-Za-z]*")
ECHOES = {"echo", "printf"}


def simple_commands(command, depth=0, variables=None):
    """The simple commands of a command line, in order, as Command(argv, env, unresolved, redirected, piped): separators, keywords, leading VAR=value words and wrappers (env, nohup, sudo, ...)
    are stripped; `NAME=value` words earlier in the line (and a leading `NAME=value cmd`) are substituted into later words, so `h=a; psql -h ${h}b` reads `psql -h ab`;
    `$(...)` and backticks, `bash -c '...'` / `bash -lc` / `eval ...` bodies, here-documents, here-strings and `echo ... | bash` fed to a shell are returned as commands of their own, and `env -S 'cmd args'` is split.
    `unresolved` is True when a word still holds a `$` expansion or a command substitution (a `$` inside single quotes does not count); `piped` is True when the command reads a pipe.
    A command line that cannot be tokenised raises ValueError (callers treat that as unreadable and decide for themselves)."""
    variables = {} if variables is None else variables
    out, segments, cur, piped_next = [], [], [], False
    for token in tokens_of(command, structured=True):
        if token in SEPARATORS or set(token) <= set(";&|()"):
            if cur:
                segments.append((cur, piped_next))
            cur, piped_next = [], token in ("|", "|&")
        else:
            cur.append(token)
    if cur:
        segments.append((cur, piped_next))
    previous = None
    for seg, piped in segments:
        env, i = {}, 0
        while i < len(seg):
            tok = seg[i]
            if ENV.match(tok):
                name, _, value = tok.partition("=")
                env[name] = expand(restore(value), variables)[0]
                i += 1
            elif tok in KEYWORDS:
                i += 1
            elif tok.lower() in WRAPPERS:
                valued = WRAPPERS[tok.lower()]
                i += 1
                while i < len(seg) and (seg[i].startswith("-") or ENV.match(seg[i]) or (tok.lower() == "timeout" and re.fullmatch(r"[0-9.]+[smhd]?", seg[i]))):
                    if tok.lower() == "env" and (seg[i] in ("-S", "--split-string") or (seg[i].startswith("-S") and len(seg[i]) > 2) or seg[i].startswith("--split-string=")):
                        piece = seg[i + 1] if seg[i] in ("-S", "--split-string") and i + 1 < len(seg) else seg[i].partition("=")[2] if seg[i].startswith("--") else seg[i][2:]
                        used = 2 if seg[i] in ("-S", "--split-string") else 1
                        try:
                            seg[i:i + used] = shlex.split(restore(piece))
                        except ValueError:
                            seg[i:i + used] = [piece]
                        continue
                    if ENV.match(seg[i]):
                        name, _, value = seg[i].partition("=")
                        env[name] = expand(restore(value), variables)[0]
                    i += 2 if seg[i] in valued else 1
            else:
                break
        rest = seg[i:]
        if not rest:
            variables.update(env)  # a bare `NAME=value` line stays in effect for the following commands
            continue
        unresolved, argv = False, []
        for token in rest:
            if depth < 3:
                for inner in SUBSTITUTION.findall(token):
                    out += simple_commands(restore(inner), depth + 1, dict(variables))
            if "$(" in token:
                unresolved = True
            expanded, left = expand(token, {**variables, **env})
            unresolved |= left
            argv.append(restore(expanded))
        redirected = any(t in ("<", "<<", "<<<", ">", ">>", ">&", "&>") for t in argv)
        out.append(Command(argv, env, unresolved, redirected, piped))
        name = name_of(argv[0])
        if depth < 3 and name in SHELLS:
            bodies = []
            if name == "eval":
                bodies = [" ".join(argv[1:])] if len(argv) > 1 else []
            else:
                body_at = next((n for n, t in enumerate(argv[1:], 1) if SHORT_C.fullmatch(t)), None)
                if body_at is not None and body_at + 1 < len(argv):
                    bodies = [argv[body_at + 1]]
                if any(t.startswith("<<") and t != "<<<" for t in argv):
                    bodies += heredoc_bodies(command)
                if "<<<" in argv and argv.index("<<<") + 1 < len(argv):
                    bodies.append(argv[argv.index("<<<") + 1])
                if piped and previous is not None and name_of(previous[0]) in ECHOES:
                    bodies.append(" ".join(previous[1:]))
            for body in bodies:
                if body.strip():
                    out += simple_commands(body, depth + 1, dict(variables))
        previous = argv
    return out


def self_test():
    names = lambda text: [name_of(c.argv[0]) for c in simple_commands(text)]  # noqa: E731
    argvs = lambda text: [c.argv for c in simple_commands(text)]  # noqa: E731
    # ANSI-C words are decoded the way bash does
    assert decode_ansi_c(r"\x67it \147it git \U00000067it\n") == "git git git git\n" and decode_ansi_c(r"a\x00b") == "a"
    assert plain("$'\\x67it' push") == "git push" and plain("$'\\147it' push") == "git push" and plain("$'git' push") == "git push" and plain('$"git" push') == "git push"
    assert argvs("$'\\x73qlcmd' -S $'\\x68ost' -Q x") == [["sqlcmd", "-S", "host", "-Q", "x"]]
    # a backslash-newline is deleted, also in the middle of a word
    assert plain("gi\\\nt push") == "git push" and argvs("sqlc\\\nmd -S h") == [["sqlcmd", "-S", "h"]] and argvs("a \\\n  b") == [["a", "b"]]
    # backticks are substitutions: the inner command is a command of its own and the outer word is unresolved; in single quotes they are text
    found = simple_commands("sqlcmd -S `echo rm-x` -Q q")
    assert "echo" in names("sqlcmd -S `echo rm-x` -Q q") and found[0].unresolved
    assert names("git commit -m 'use `foo` and $x'") == ["git"] and not simple_commands("git commit -m 'use `foo` and $x'")[0].unresolved
    assert simple_commands('echo "$X"')[0].unresolved and not simple_commands("echo '$X'")[0].unresolved
    assert "git" in names("echo `git push`") and any(t == ";" for t in tokens_of("echo `git push`")), "the other gates keep reading a backtick as a new command"
    # shells: bundled -c options, multi-word eval, heredoc / here-string / echo-pipe bodies
    for text in ("bash -lc 'psql -h h'", "sh -ec 'psql -h h'", "bash -xc \"psql -h h\"", "eval psql -h h", "eval 'psql' '-h' h", "bash <<'EOF'\npsql -h h\nEOF", "bash <<< 'psql -h h'", "echo 'psql -h h' | bash", "zsh -c \"bash -c 'psql -h h'\"",
                 "env -S 'psql -h h'", "env -S'psql -h h'", "env --split-string='psql -h h'", "env A=1 -S 'psql -h h'"):
        assert "psql" in names(text), text
    assert ["psql", "-h", "h"] in argvs("eval psql -h h") and ["psql", "-h", "h"] in argvs("env -S 'psql -h h'")
    pick = lambda text: next(c for c in simple_commands(text) if c.argv[0] == "bash")  # noqa: E731
    assert pick("echo hi | bash").piped and not pick("echo hi; bash").piped
    # variables and wrappers still work
    assert argvs("h=a; psql -h ${h}b") == [["psql", "-h", "ab"]] and argvs("FOO=1 nohup timeout 9 psql x") == [["psql", "x"]]
    # unreadable input
    try:
        simple_commands("echo 'unterminated")
        raise AssertionError("must raise")
    except ValueError:
        pass
    print("command_words self-test: ok")
    return 0


if __name__ == "__main__":
    import sys
    sys.exit(self_test() if sys.argv[1:] == ["--self-test"] else 0)
