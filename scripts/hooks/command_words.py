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
    """The text of a bash `$'...'` word: \\xHH, \\NNN, \\uHHHH, \\UHHHHHHHH, \\cX and the one-letter escapes decoded. \\x and octal escapes are single bytes (a UTF-8 character is several of them), as in bash."""
    out, i = bytearray(), 0
    while i < len(body):
        char = body[i]
        if char != "\\" or i + 1 >= len(body):
            out += char.encode("utf-8", "replace")
            i += 1
            continue
        nxt = body[i + 1]
        if nxt in SIMPLE_ESCAPES:
            out += SIMPLE_ESCAPES[nxt].encode()
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
            value = int(found.group(), base)
            if nxt in "uU":
                try:
                    out += chr(value).encode("utf-8")
                except (ValueError, OverflowError):
                    out += ("\\" + nxt).encode()
                    i += 2
                    continue
            else:
                out.append(value & 0xff)
            i += skip + len(found.group())
        elif nxt == "c" and i + 2 < len(body):
            out.append(ord(body[i + 2]) & 0x1f)
            i += 3
        else:
            out += ("\\" + nxt).encode()
            i += 2
    return bytes(out).split(b"\x00")[0].decode("utf-8", "replace")


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
HEREDOC = re.compile(r"<<(-?)[ \t]*(?:'([^']*)'|\"([^\"]*)\"|(\\?)([A-Za-z_][A-Za-z0-9_]*))")


def split_heredocs(command):
    """(command text with the here-document bodies removed, [(body, delimiter, quoted)]). `<<` is an operator only outside quotes and comments (an apostrophe or `<<EOF` inside a string or after `#` is text);
    a body starts on the line after the operator and ends at the delimiter line. An unquoted delimiter means the body is expanded by the shell (commands in `$(...)` run)."""
    text = command.replace("\\\n", "")
    out, bodies, pending, i, single, double, n = [], [], [], 0, False, False, len(text)
    while i < n:
        char = text[i]
        if single:
            out.append(char)
            single = char != "'"
            i += 1
        elif char == "\\" and i + 1 < n:
            out.append(text[i:i + 2])
            i += 2
        elif double:
            out.append(char)
            double = char != '"'
            i += 1
        elif char == "'":
            out.append(char)
            single = True
            i += 1
        elif char == '"':
            out.append(char)
            double = True
            i += 1
        elif char == "#" and (i == 0 or text[i - 1] in " \t\n;|&("):
            end = text.find("\n", i)
            end = n if end < 0 else end
            out.append(text[i:end])
            i = end
        elif char == "<" and text.startswith("<<", i) and not text.startswith("<<<", i) and not (i and text[i - 1] == "<") and HEREDOC.match(text, i):
            found = HEREDOC.match(text, i)
            delimiter = found.group(2) or found.group(3) or found.group(5)
            pending.append((delimiter, bool(found.group(2) or found.group(3) is not None or found.group(4)), found.group(1) == "-"))
            out.append(found.group(0))
            i = found.end()
        elif char == "\n" and pending:
            out.append("\n")
            i += 1
            for delimiter, quoted, strip in pending:
                lines = []
                while i < n:
                    end = text.find("\n", i)
                    line = text[i:n if end < 0 else end]
                    i = n if end < 0 else end + 1
                    if (line.lstrip("\t") if strip else line).rstrip("\r") == delimiter:
                        break
                    lines.append(line)
                bodies.append(("\n".join(lines), delimiter, quoted))
            pending = []
        else:
            out.append(char)
            i += 1
    return "".join(out), bodies


def without_heredoc_bodies(command):
    """The lines of a command line, with here-document bodies dropped (a body is text, not commands)."""
    return split_heredocs(command)[0].split("\n")


def heredoc_bodies(command):
    """The here-document bodies of a command line (a shell fed by one runs them)."""
    return [body for body, _, _ in split_heredocs(command)[1]]


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


def simple_commands(command, depth=0, variables=None, exported=None):
    """The simple commands of a command line, in order, as Command(argv, env, unresolved, redirected, piped): separators, keywords, leading VAR=value words and wrappers (env, nohup, sudo, ...)
    are stripped; `NAME=value` words earlier in the line (and a leading `NAME=value cmd`) are substituted into later words, so `h=a; psql -h ${h}b` reads `psql -h ab`;
    `$(...)` and backticks, `bash -c '...'` / `bash -lc` / `eval ...` bodies, here-documents, here-strings and `echo ... | bash` fed to a shell are returned as commands of their own, and `env -S 'cmd args'` is split.
    `unresolved` is True when a word still holds a `$` expansion or a command substitution (a `$` inside single quotes does not count); `piped` is True when the command reads a pipe.
    `export NAME=value` is inherited by the later commands (their `env`); `<(...)` and `>(...)` run their inner command; the commands inside an unquoted here-document body (`$(...)`, backticks) are returned as well.
    A command line that cannot be tokenised, or that nests substitutions, shells and evals deeper than 3 levels, raises ValueError (callers treat that as unreadable and decide for themselves)."""
    if depth > 3:
        raise ValueError("nested too deeply")
    variables = {} if variables is None else variables
    exported = {} if exported is None else exported
    out, segments, cur, piped_next = [], [], [], False
    for body, _, quoted in split_heredocs(command)[1]:
        if not quoted:
            for inner in SUBSTITUTION.findall(re.sub(r"`([^`]*)`", r"$(\1)", body)):
                out += simple_commands(inner, depth + 1, dict(variables), dict(exported))
    for token in tokens_of(command, structured=True):
        if token in SEPARATORS or token in ("<(", ">(") or set(token) <= set(";&|()"):
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
            for inner in SUBSTITUTION.findall(token):
                out += simple_commands(restore(inner), depth + 1, dict(variables), dict(exported))
            if "$(" in token:
                unresolved = True
            expanded, left = expand(token, {**variables, **env})
            unresolved |= left
            argv.append(restore(expanded))
        redirected = any(t in ("<", "<<", "<<<", ">", ">>", ">&", "&>") for t in argv)
        name = name_of(argv[0])
        if name in ("export", "declare", "local", "readonly", "typeset"):
            for arg in argv[1:]:
                if ENV.match(arg):
                    key, _, value = arg.partition("=")
                    variables[key] = exported[key] = value
        elif name == "unset":
            for arg in argv[1:]:
                variables.pop(arg, None)
                exported.pop(arg, None)
        out.append(Command(argv, {**exported, **env}, unresolved, redirected, piped))
        if name in SHELLS:
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
                    out += simple_commands(body, depth + 1, dict(variables), dict(exported))
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
    # Codex review of #653: byte-wise ANSI-C, quote-aware here-documents, process substitution, nesting limit, export
    assert decode_ansi_c(r"\xe8\xb0\x83\xe5\xba\xa6") == "\u8c03\u5ea6" and decode_ansi_c(r"\u8c03") == "\u8c03"
    assert names("echo ok # <<EOF\nsqlcmd -S p -Q x") == ["echo", "sqlcmd"] and names('echo "<<EOF"; sqlcmd -S p -Q x') == ["echo", "sqlcmd"], "<<EOF in a comment or a string is not a here-document"
    assert "sqlcmd" in names("cat <<EOF\n$(sqlcmd -S p -Q x)\nEOF") and "sqlcmd" not in names("cat <<'EOF'\n$(sqlcmd -S p -Q x)\nEOF"), "an unquoted here-document body is expanded, a quoted one is text"
    assert names("psql -h x <<EOF\nselect 1\nEOF\necho after") == ["psql", "echo"] and heredoc_bodies("cat <<'A' <<B\none\nA\ntwo\nB\n") == ["one", "two"]
    assert names('printf "%s" <(sqlcmd -S p -Q x)') == ["printf", "sqlcmd"] and names("diff <(ls a) >(cat)") == ["diff", "ls", "cat"]
    try:
        simple_commands("eval eval eval eval ssh h touch x")
        raise AssertionError("nesting deeper than 3 must raise")
    except ValueError:
        pass
    assert simple_commands("export SQLCMDSERVER=prod; sqlcmd -Q x")[-1].env == {"SQLCMDSERVER": "prod"} and simple_commands("export A=1; unset A; ls")[-1].env == {}
    assert argvs("export H=prod; sqlcmd -S\"$H\" -Q x")[-1][:2] == ["sqlcmd", "-Sprod"]
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
