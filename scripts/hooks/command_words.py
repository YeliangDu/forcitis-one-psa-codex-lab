"""Shared by the Bash PreToolUse gates: the shell resolves `GIT`, `gi""t` and a backslash-split name to the git command (macOS file systems ignore case), so a gate must compare command names after that normalisation."""
import collections
import os
import re
import shlex

ANSI_QUOTE = re.compile(r"\$(?=['\"])")
QUOTES = re.compile(r"[\\'\"]")


def unquote_dollar(command):
    """`$'git'` and `$"git"` run git in bash and zsh, but shlex reads them as the word `$git`: drop the `$` in front of a quote before tokenising."""
    return ANSI_QUOTE.sub("", command)


def plain(command):
    """Text for pre-filters: quotes and backslashes removed, lower-cased. Only ever used to decide whether a gate looks closer, never to run anything."""
    return QUOTES.sub("", ANSI_QUOTE.sub("", command)).lower() if isinstance(command, str) else ""


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
    for line in without_heredoc_bodies(unquote_dollar(command)):
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


Command = collections.namedtuple("Command", "argv env unresolved redirected")
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


def simple_commands(command, depth=0, variables=None):
    """The simple commands of a command line, in order, as Command(argv, env, unresolved, redirected): separators, keywords, leading VAR=value words and wrappers (env, nohup, sudo, ...)
    are stripped; `NAME=value` words earlier in the line (and a leading `NAME=value cmd`) are substituted into later words, so `h=a; psql -h ${h}b` reads `psql -h ab`;
    `$(...)`, `bash -c '...'` and `eval ...` bodies are returned as commands of their own. `unresolved` is True when a word still holds a `$` expansion or a command substitution.
    A command line that cannot be tokenised raises ValueError (callers treat that as unreadable and decide for themselves)."""
    variables = {} if variables is None else variables
    out, segments, cur = [], [], []
    for token in tokens_of(command):
        if token in SEPARATORS or set(token) <= set(";&|()"):
            if cur:
                segments.append(cur)
            cur = []
        else:
            cur.append(token)
    if cur:
        segments.append(cur)
    for seg in segments:
        env, i = {}, 0
        while i < len(seg):
            tok = seg[i]
            if ENV.match(tok):
                name, _, value = tok.partition("=")
                env[name] = expand(value, variables)[0]
                i += 1
            elif tok in KEYWORDS:
                i += 1
            elif tok.lower() in WRAPPERS:
                valued = WRAPPERS[tok.lower()]
                i += 1
                while i < len(seg) and (seg[i].startswith("-") or ENV.match(seg[i]) or (tok.lower() == "timeout" and re.fullmatch(r"[0-9.]+[smhd]?", seg[i]))):
                    if ENV.match(seg[i]):
                        name, _, value = seg[i].partition("=")
                        env[name] = expand(value, variables)[0]
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
                    out += simple_commands(inner, depth + 1, dict(variables))
            if "$(" in token:
                unresolved = True
            expanded, left = expand(token, {**variables, **env})
            unresolved |= left
            argv.append(expanded)
        redirected = any(t in ("<", "<<", "<<<", ">", ">>", ">&", "&>") for t in argv)
        out.append(Command(argv, env, unresolved, redirected))
        if depth < 3 and name_of(argv[0]) in SHELLS:
            bodies = [argv[argv.index("-c") + 1]] if "-c" in argv[:-1] else (argv[1:] if name_of(argv[0]) == "eval" else [])
            for body in bodies:
                if " " in body:
                    out += simple_commands(body, depth + 1, dict(variables))
    return out
