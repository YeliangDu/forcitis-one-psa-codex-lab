#!/usr/bin/env python3
"""生产写门（PreToolUse，Bash）：命令碰到生产目标，除非是点名的只读用法，一律拒绝。防底线 4（生产只读）；安全门，出错失败关闭（退出码 2）。

生产目标 = prod_targets.json 的 patterns（与 origin/main 上那份取并集，钩子调用时再并上 cwd 与 CLAUDE_PROJECT_DIR 所在仓库的），出现在命令的词里（引号、ANSI-C、续行、花括号、变量与 export／set -a 拼接、反引号与 `$(…)` 展开后）或环境变量里即「碰到」。
每条简单命令（`bash -c`／`-lc`／`eval`／heredoc／here-string／`echo … | bash`／`env -S`／`<(…)` 递归，超过 3 层按读不懂）：
- 远程工具（SQL 客户端、bcp、aliyun、ssh、scp、rsync、sftp、curl、wget、httpie）碰到生产且写（或写效果看不出）拒绝；SQL 只认白名单，aliyun 只放 Describe/List/Get…，返回密钥的接口一律拒绝（底线 3）。
- 不连接的命令（git 本地子命令、gh、echo、grep、rg 无 --pre、sed 无 e/w…）碰到特征放行；其他命令（解释器、xargs、find -exec、git push…）碰到即拒绝，解释器的脚本文件读进来看。
- 特征表缺失、不合格，或与 origin/main 的并集为空：写类远程命令与解释器拒绝，只读放行并告警；origin/main 读取时 git 出错只告警。读不懂且含远程工具名或特征拒绝；写操作的目标含读不出的变量拒绝。
命令解析（原 command_words）也在本文件，其他钩子经 scripts/hooks/command_words.py 兼容层导入。自测在 prod_write_gate_selftest.py。
退出码 2 + 标准错误 = 拒绝；0 = 放行。用法：prod_write_gate.py  |  prod_write_gate.py --self-test
"""
import collections
import json
import os
import re
import shlex
import subprocess
import sys
from pathlib import Path

ANSI_QUOTE = re.compile(r"\$'((?:[^'\\]|\\.)*)'", re.S)
DOLLAR_DOUBLE = re.compile(r'\$(?=")')
QUOTES = re.compile(r"[\\'\"]")
PUA_DOLLAR, PUA_TICK = "\ue000", "\ue001"  # `$` and a backtick inside single quotes: plain characters, not expansions
SIMPLE_ESCAPES = {"a": "\a", "b": "\b", "e": "\x1b", "E": "\x1b", "f": "\f", "n": "\n", "r": "\r", "t": "\t", "v": "\v", "\\": "\\", "'": "'", '"': '"', "?": "?"}
SEPARATORS = {";", "&&", "||", "|", "&", "(", ")", "|&"}
KEYWORDS = {"if", "then", "do", "else", "elif", "while", "until", "!", "{", "}", "fi", "done", "esac"}
WRAPPERS = {"command": set(), "env": {"-u", "-C", "-S"}, "time": {"-f", "-o"}, "exec": {"-a"}, "nohup": set(), "sudo": {"-u", "-g", "-h", "-p", "-C", "-D", "-R", "-T", "-U"}, "builtin": set(), "timeout": {"-s", "-k"}, "nice": {"-n"}, "stdbuf": {"-i", "-o", "-e"}}  # wrapper -> options that take a value
SHELLS = {"bash", "sh", "zsh", "dash", "ksh", "eval"}
ENV = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*=")
HEREDOC = re.compile(r"<<(-?)[ \t]*(?:'([^']*)'|\"([^\"]*)\"|(\\?)([A-Za-z_][A-Za-z0-9_]*))")
VARIABLE = re.compile(r"\$\{([A-Za-z_][A-Za-z0-9_]*)\}|\$([A-Za-z_][A-Za-z0-9_]*)")
SUBSTITUTION = re.compile(r"\$\(([^()]*)\)")
SHORT_C = re.compile(r"-[A-Za-z]*c[A-Za-z]*")
ECHOES = {"echo", "printf"}
Command = collections.namedtuple("Command", "argv env unresolved redirected piped", defaults=(False,))


def decode_ansi_c(body):  # Text of a bash `$'...'` word, decoded byte-wise as bash does (\xHH, \NNN, \uHHHH, \UHHHHHHHH, \cX, one-letter escapes); a NUL ends it.
    out, i = bytearray(), 0
    while i < len(body):
        char = body[i]
        if char != "\\" or i + 1 >= len(body):
            out += char.encode("utf-8", "replace")
            i += 1
            continue
        nxt, found = body[i + 1], None
        if nxt in SIMPLE_ESCAPES:
            out += SIMPLE_ESCAPES[nxt].encode()
            i += 2
            continue
        if nxt == "x":
            found, skip, base = re.match(r"[0-9a-fA-F]{1,2}", body[i + 2:]), 2, 16
        elif nxt in "01234567":
            found, skip, base = re.match(r"[0-7]{1,3}", body[i + 1:]), 1, 8
        elif nxt in "uU":
            found, skip, base = re.match(r"[0-9a-fA-F]{1,%d}" % (4 if nxt == "u" else 8), body[i + 2:]), 2, 16
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


def unquote_dollar(command):  # `$'\x67it'` and `$"git"` run git; shlex would read `$git`: decode the ANSI-C form into a single-quoted word and drop `$` before a double quote.
    command = ANSI_QUOTE.sub(lambda m: "'" + decode_ansi_c(m.group(1)).replace("'", "'\\''") + "'", command)
    return DOLLAR_DOUBLE.sub("", command)


def plain(command):  # Pre-filter text: ANSI-C decoded, line continuations, quotes and backslashes removed, lower-cased. Only decides whether to look closer.
    return QUOTES.sub("", unquote_dollar(command.replace("\\\n", ""))).lower() if isinstance(command, str) else ""


def neutralize(text, backticks=True):  # `$`/backtick inside single quotes -> private characters; a backtick substitution outside them -> `$(...)` (when `backticks`). Comments are skipped (`# don't`).
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


def name_of(token):  # Command name as the shell finds it: directory dropped, lower-cased (macOS file systems ignore case).
    return os.path.basename(token).lower()


def split_heredocs(command):  # (text without here-document bodies, [(body, delimiter, quoted)]). `<<` counts only outside quotes and comments; an unquoted delimiter means the body is expanded.
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
        elif char in "'\"":
            out.append(char)
            single, double = char == "'", char == '"'
            i += 1
        elif char == "#" and (i == 0 or text[i - 1] in " \t\n;|&("):
            end = text.find("\n", i)
            end = n if end < 0 else end
            out.append(text[i:end])
            i = end
        elif char == "<" and text.startswith("<<", i) and not text.startswith("<<<", i) and not (i and text[i - 1] == "<") and HEREDOC.match(text, i):
            found = HEREDOC.match(text, i)
            pending.append((found.group(2) or found.group(3) or found.group(5), bool(found.group(2) or found.group(3) is not None or found.group(4)), found.group(1) == "-"))
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
    return split_heredocs(command)[0].split("\n")


def heredoc_bodies(command):
    return [body for body, _, _ in split_heredocs(command)[1]]


def tokens_of(command, structured=False):
    """Shell tokens, line by line (a # comment ends with its line, an open quote continues). `structured`: backticks are `$(...)`, `$` in single quotes neutralised;
    otherwise the older reading of the other hooks (a backtick starts a new command)."""
    tokens, pending = [], ""
    lines = without_heredoc_bodies(unquote_dollar(command))
    if structured:
        lines = neutralize("\n".join(lines)).split("\n")
    for line in lines:
        pending = pending + "\n" + line if pending else line
        lexer = shlex.shlex(pending if structured else pending.replace("`", " ; "), posix=True, punctuation_chars=True)
        lexer.whitespace_split, lexer.commenters = True, "#"
        try:
            found = list(lexer)
        except ValueError:
            continue
        tokens += found if found and found[-1] in ("|", "&&", "||", "|&") else found + [";"]
        pending = ""
    if pending:
        raise ValueError("unterminated quote")
    return tokens


def expand(token, variables):  # (token with known variables substituted, True when a `$` expansion is left).
    for _ in range(5):
        new = VARIABLE.sub(lambda m: variables.get(m.group(1) or m.group(2), m.group(0)), token)
        if new == token:
            break
        token = new
    return token, "$" in token


def strip_prefix(seg, variables):  # (env of leading `NAME=value` words and wrapper options, rest of the segment): keywords and wrappers (env, nohup, sudo, timeout …) dropped, `env -S` split.
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
            valued, wrapper = WRAPPERS[tok.lower()], tok.lower()
            i += 1
            while i < len(seg) and (seg[i].startswith("-") or ENV.match(seg[i]) or (wrapper == "timeout" and re.fullmatch(r"[0-9.]+[smhd]?", seg[i]))):
                if wrapper == "env" and (seg[i] in ("-S", "--split-string") or (seg[i].startswith("-S") and len(seg[i]) > 2) or seg[i].startswith("--split-string=")):
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
    return env, seg[i:]


def simple_commands(command, depth=0, variables=None, exported=None):
    """Simple commands of a command line as Command(argv, env, unresolved, redirected, piped), in order. `NAME=value` earlier in the line (and `export`) is substituted into later
    words; `$(...)`, backticks, `<(...)`, `bash -c`/`-lc`/`eval` bodies, here-documents, here-strings and `echo … | bash` fed to a shell are commands of their own; unquoted
    here-document bodies are searched for substitutions. `unresolved`: a word keeps a `$` expansion or substitution. Untokenisable input, or nesting deeper than 3, raises ValueError."""
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
    previous, allexport = None, False
    for seg, piped in segments:
        env, rest = strip_prefix(seg, variables)
        if not rest:
            variables.update(env)  # a bare `NAME=value` stays in effect for the following commands
            exported.update({k: v for k, v in env.items() if allexport or k in ENV_TARGET_VARS})  # exported under `set -a`; a target variable may already be exported by the environment or an rc file (O2 round 1 M1)
            continue
        unresolved, argv = False, []
        for token in rest:
            for inner in SUBSTITUTION.findall(token):
                out += simple_commands(restore(inner), depth + 1, dict(variables), dict(exported))
            expanded, left = expand(token, {**variables, **env})
            unresolved |= left or "$(" in token
            argv.append(restore(expanded))
        name = name_of(argv[0])
        if name in ("export", "declare", "local", "readonly", "typeset"):
            exports = name == "export" or any(re.fullmatch(r"-[A-Za-z]*x[A-Za-z]*", arg) for arg in argv[1:])  # `declare -x`, `typeset -x`
            for arg in argv[1:]:
                if ENV.match(arg):
                    key, _, value = arg.partition("=")
                    variables[key] = exported[key] = value
                elif exports and arg in variables:
                    exported[arg] = variables[arg]  # `NAME=value; export NAME`
        elif name == "set":
            for at, arg in enumerate(argv[1:], 1):
                if re.fullmatch(r"[-+][A-Za-z]*a[A-Za-z]*", arg) or (arg in ("-o", "+o") and argv[at + 1:at + 2] == ["allexport"]):
                    allexport = arg.startswith("-")
        elif name == "unset":
            for arg in argv[1:]:
                variables.pop(arg, None)
                exported.pop(arg, None)
        out.append(Command(argv, {**exported, **env}, unresolved, any(t in ("<", "<<", "<<<", ">", ">>", ">&", "&>") for t in argv), piped))
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


HERE = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(os.path.dirname(HERE))
if not os.path.exists(os.path.join(REPO_ROOT, ".git")):  # installed copy (install.sh, D26): the session's repository is CLAUDE_PROJECT_DIR, never the cwd (O7 final M3); none: os.devnull, git reads fail, no script is trusted
    REPO_ROOT = os.environ.get("CLAUDE_PROJECT_DIR") or os.devnull
ANCHOR, ROOTS = REPO_ROOT, ()  # whose origin/main says which scripts are trusted, (hook cwd, CLAUDE_PROJECT_DIR, process cwd) whose tables are unioned in; decide() sets both per call
DOCS_ROOT = "docs" + "/" + "modernization"  # built, not spelled: relevant_change.py scans sources for literal docs paths
TABLE = os.environ.get("PROD_TARGETS_FILE") or str(Path(HERE) / "prod_targets.json")  # unioned with origin/main's all the same (load_table)
MAIN_TABLE_PATHS = ("scripts/gates/prod_targets.json", "scripts/hooks/prod_targets.json")  # the second until origin/main has the first
GUARD_DIRS = ("scripts/gates/", "scripts/hooks/")
SQL_TOOLS = {"psql", "mysql", "mariadb", "sqlcmd", "osql", "mssql-cli", "mongosh", "mongo", "redis-cli", "usql", "tsql", "isql"}
WRITE_ONLY_TOOLS = {"sqlpackage", "sftp"}
HTTP_TOOLS = {"curl", "wget", "http", "https", "xh", "xhs"}
REMOTE_TOOLS = SQL_TOOLS | WRITE_ONLY_TOOLS | HTTP_TOOLS | {"bcp", "aliyun", "ssh", "scp", "rsync"}
LONG_REMOTE = {t for t in REMOTE_TOOLS if len(t) > 2}  # matched as substrings of an unreadable line; two-letter names (xh) only as whole words (SHORT_REMOTE, REMOTE_WORD): `xh` is inside exhibit, xhr…
SHORT_REMOTE = re.compile(r"(?<![a-z0-9_])(?:" + "|".join(sorted(re.escape(t) for t in REMOTE_TOOLS - LONG_REMOTE)) + r")(?![a-z0-9_-])")
INTERPRETERS = {"python", "node", "perl", "ruby", "php", "lua", "bash", "sh", "zsh", "dash", "ksh", "source", "."}  # "python" stands for python3, python3.12 …
RUNS_OTHERS = {"xargs", "find", "script", "parallel", "export", "source"}  # commands that run another command or carry its environment
INTERPRETER = re.compile(r"^(?:python[0-9.]*|" + "|".join(re.escape(n) for n in sorted(INTERPRETERS - {"python"})) + r")$")
REMOTE_WORD = re.compile(r"(?<![A-Za-z0-9_])(?:python[0-9.]*|" + "|".join(re.escape(n) for n in sorted((REMOTE_TOOLS | SHELLS | INTERPRETERS | RUNS_OTHERS) - {"python", "."}))
                         + r")(?![A-Za-z0-9_-])|(?:^|[\s;&|(])\.\s")  # the pre-filter's names come from these sets only, plus `. file`
SHELL_HEADS = {"for", "case", "select", "function", "in"}  # `for f in a b` is a loop head; substitutions inside it are commands of their own
NO_CONNECT = {"git", "gh", "echo", "printf", "grep", "egrep", "fgrep", "rg", "cat", "sed", "head", "tail", "ls", "jq", "wc", "sort", "uniq", "cut", "tr", "diff", "test", "[", "true", "false", "cd", "pwd", "export", "set", "unset", "read", "basename", "dirname", "mkdir", "touch", "cp", "mv", "stat", "file", "date", "tee", ":", "less", "more", "nl", "tac", "fold", "column", "comm", "join", "paste", "expr", "declare", "local", "readonly", "typeset"}
LOCAL_GIT = {"commit", "log", "show", "status", "add", "diff", "grep", "branch", "checkout", "switch", "restore", "rev-parse", "rev-list", "ls-files", "ls-tree", "cat-file", "stash", "merge", "worktree", "blame", "describe", "shortlog", "apply", "reset", "cherry-pick", "rm", "mv", "clean", "rebase", "show-ref", "for-each-ref", "name-rev", "merge-base", "diff-tree", "diff-index", "hash-object", "symbolic-ref", "count-objects", "check-ignore", "version", "help"}
GIT_RUNS_COMMANDS = ("exec", "ext-diff", "open-files-in-pager", "upload-pack", "receive-pack", "textconv")
NETWORK_GIT = {"push", "pull", "fetch", "clone", "ls-remote", "remote", "submodule", "archive", "daemon", "send-pack", "fetch-pack", "upload-pack", "receive-pack", "request-pack", "svn", "p4", "credential", "bundle", "maintenance", "instaweb", "difftool", "mergetool"}
DOC_SCRIPTS = {DOCS_ROOT + "/new_intent.py", DOCS_ROOT + "/new_decision.py", DOCS_ROOT + "/new_log.py", DOCS_ROOT + "/glossary/new_term_issue.py"}
ENV_TARGET_VARS = ("PGHOST", "PGSERVICE", "PGDATABASE", "PGUSER", "PGHOSTADDR", "SQLCMDSERVER", "SQLCMDDBNAME", "SQLCMDUSER", "MYSQL_HOST", "DATABASE_URL", "MONGODB_URI", "REDIS_URL", "ALIBABA_CLOUD_PROFILE")
MAX_SQL_FILE, MAX_SCRIPT_FILE = 65536, 262144
STATEMENT_OPTIONS = {"psql": {"-c", "--command"}, "mysql": {"-e", "--execute"}, "mariadb": {"-e", "--execute"}, "sqlcmd": {"-Q", "-q", "--query"}, "osql": {"-Q", "-q"}, "mssql-cli": {"-Q", "--query"}, "mongosh": {"--eval"}, "mongo": {"--eval"}, "usql": {"-c", "--command"}}
FILE_OPTIONS = {"psql": {"-f", "--file"}, "sqlcmd": {"-i"}, "osql": {"-i"}, "usql": {"-f", "--file"}, "mysql": set(), "mariadb": set()}
DANGEROUS_SQL_OPTIONS = {"sqlcmd": {"-z", "-Z", "-v"}, "osql": {"-z", "-Z"}, "mysql": {"--init-command", "--init_command"}, "mariadb": {"--init-command", "--init_command"}}
HELP_ONLY = {"--version", "--help", "-?"}
DATA_OPTIONS = {"-c", "--command", "-Q", "-q", "-e", "--execute", "--eval", "--query", "-d", "--data", "--data-raw", "--data-binary", "--data-urlencode", "--data-ascii", "-F", "--form", "--form-string", "-H", "--header", "-u", "--user", "-A", "--user-agent", "-b", "--cookie", "-o", "--output", "-w", "-T", "--upload-file", "--json", "-X", "--request", "-U", "-P", "-p", "--password", "--post-data", "--body-data", "-v"}
HOST_OPTIONS = {"-h", "--host", "-S", "--server", "--url"}
SQL_READ = re.compile(r"^(?:select|with|show|explain|describe|desc|values|table|go|" r"db\.\w+\.(?:find|count|countdocuments|estimateddocumentcount|aggregate|distinct)\b|db\.(?:stats|getcollectionnames)\b|" r"ping|dbsize|info|keys|scan|ttl|type|exists|get|mget|strlen|hget|hgetall|hkeys|hlen|lrange|llen|smembers|scard|zrange|zcard)\b", re.I)
SQL_SET = re.compile(r"\bset\s+(?:(?:nocount|ansi_\w+|quoted_identifier|arithabort|xact_abort)\s+(?:on|off)|(?:textsize|lock_timeout|language|dateformat)\s+[\w'-]+|transaction\s+isolation\s+level\s+read\s+(?:uncommitted|committed))\b", re.I)
SQL_WRITE = re.compile(r"\b(?:insert|update|delete|drop|alter|create|truncate|grant|revoke|merge|copy|call|exec|execute|vacuum|reindex|replace|rename|set|flushall|flushdb|del|expire|config|shutdown|into|do|source|load|" r"dbcc|kill|backup|restore|deny|reconfigure|checkpoint|disable|enable|bulk|openrowset|opendatasource|openquery|sp_\w+|xp_\w+|use|lock|unlock|analyze|cluster|refresh|reset|listen|notify|prepare|deallocate|" r"begin|commit|rollback|savepoint|release|declare|waitfor|comment\s+on|next\s+value\s+for|query_to_xml\w*|cursor_to_xml\w*|table_to_xml\w*|" r"pg_(?:terminate_backend|cancel_backend|reload_conf|rotate_logfile|switch_wal|create_\w+|drop_\w+|advisory_\w+|promote|start_backup|stop_backup|replication_\w+|sleep\w*|read_\w+|ls_dir|file_write|" r"logical_\w+|notify|stat_reset\w*|wal_replay_\w+|import_\w+|export_snapshot|backup_\w+)|" r"set_config|nextval|setval|lo_\w+|dblink\w*|for\s+(?:update|share|no\s+key\s+update|key\s+share))\b", re.I)
MONGO_WRITE = re.compile(r"\.(?:delete\w*|update\w*|insert\w*|replace\w*|drop\w*|remove\w*|save|bulkwrite|findandmodify|findoneand\w+|createindex\w*|createcollection|renamecollection|runcommand|eval|mapreduce)\b|\$(?:out|merge)\b", re.I)
META_READ = re.compile(r"^(?:d\w*\+?|l\+?|z|conninfo|timing|x|pset|echo|encoding)$")
REDIS_READ = {"get", "mget", "keys", "scan", "info", "ping", "dbsize", "ttl", "pttl", "type", "exists", "hget", "hgetall", "hkeys", "hlen", "hmget", "lrange", "llen", "smembers", "scard", "sismember", "zrange", "zcard", "strlen", "sscan", "hscan", "zscan"}
REDIS_READ_SUB = {"slowlog": {"get", "len"}, "client": {"list", "info", "getname"}, "memory": {"usage", "stats", "doctor"}, "config": {"get"}, "object": {"encoding", "freq", "idletime", "refcount"}, "xinfo": {"stream", "groups", "consumers"}}
REDIS_VALUE_OPTIONS = {"-h", "-p", "-a", "-n", "-u", "-s", "--user", "--pass", "--askpass", "--sni", "--cacert", "--cert", "--key", "-r", "-i", "-x", "-d", "-D"}
ALIYUN_READ = re.compile(r"^(?:describe|list|get|query|check|search|preview|verify|tag-?list)", re.I)
ALIYUN_SECRET = re.compile(r"^(?:describedbinstancessl|getsecretvalue|describedbinstancetde|getparametersbypath|getcredential\w*|createaccesskey|describeaccountsecret\w*)$", re.I)
SSH_VALUE_OPTIONS = set("bcDEeFIiJLlmOopQRSWw")
SSH_RISKY_SHORT = "LRDWwFI"  # tunnels; a configuration file (-F) or a PKCS#11 / security-key library (-I) can run or load anything
SSH_SAFE_OPTIONS = {"port", "user", "identityfile", "identitiesonly", "connecttimeout", "batchmode", "stricthostkeychecking", "userknownhostsfile", "serveraliveinterval", "serveralivecountmax", "loglevel", "addressfamily", "connectionattempts", "compression", "hostname", "forwardagent", "passwordauthentication", "preferredauthentications", "requesttty", "numberofpasswordprompts"}
SSH_READ = {"ls", "cat", "tail", "head", "grep", "egrep", "fgrep", "df", "du", "ps", "stat", "wc", "uptime", "hostname", "whoami", "id", "date", "free", "ss", "netstat", "journalctl", "echo", "pwd", "uname", "sort", "uniq", "cut", "top", "lsblk", "file", "readlink", "which", "test", "true", "find", "systemctl", "docker", "pg_dump"}
SSH_READ_SUBCOMMANDS = {"systemctl": {"status", "is-active", "is-enabled", "list-units", "list-unit-files", "show", "cat"}, "docker": {"ps", "logs", "inspect", "stats", "images", "top"}}
FIND_WRITE = {"-delete", "-exec", "-execdir", "-ok", "-okdir", "-fprint", "-fprint0", "-fprintf", "-fls"}
CURL_LONG_WRITE = {"data", "data-ascii", "data-binary", "data-raw", "data-urlencode", "form", "form-string", "upload-file", "json", "config", "next", "post301", "post302", "post303", "variable", "quote", "telnet-option", "mail-from", "mail-rcpt"}
CURL_VALUE_SHORT = set("oumAbcCeEHhKkPQrtwxyYzZ")
READ_METHODS = {"GET", "HEAD", "OPTIONS"}
SCP_VALUE_SHORT = set("PiFcloJSDX")
RSYNC_VALUE_SHORT = set("eBfTM@")
RSYNC_VALUE_LONG = {"--rsh", "--exclude", "--include", "--filter", "--exclude-from", "--include-from", "--files-from", "--rsync-path", "--port", "--bwlimit", "--timeout", "--block-size", "--compare-dest", "--copy-dest", "--link-dest", "--log-file", "--backup-dir", "--suffix", "--chmod", "--chown", "--usermap", "--groupmap", "--partial-dir", "--temp-dir", "--max-size", "--min-size", "--iconv", "--protocol", "--address", "--sockopts", "--out-format", "--password-file", "--remote-option", "--max-delete", "--modify-window", "--contimeout", "--read-batch", "--write-batch", "--only-write-batch"}
REMOTE_HOST = re.compile(r"^(?:[\w.-]+@)?[\w.-]+:")
SHELL_META = re.compile(r"[$`;|&\n(){}<>]")
PLAIN_RSH = re.compile(r"^ssh(?:\s+-[pi]\s*\S+|\s+-[46Cq])*$")
SED_EXEC = re.compile(r"(?:^|[;{}\n])\s*[ew]\b|s(.).*?\1.*?\1[a-zA-Z]*e[a-zA-Z]*\s*(?:[;}]|$)")
URL_SCHEME = re.compile(r"^[A-Za-z][A-Za-z0-9+.-]*://")
PATH_COMMAND = re.compile(r"(?:^|[;&|(\n])\s*(?:\w+=\S*\s+)*[^\s;&|()]*/")  # a command word with a slash: a script run by its path
EXTRA_READONLY = set()  # tests add tracked files here


def git(*args, root=None):  # stdout of a git command in `root` (default: the trust anchor), or None when it fails.
    try:
        run = subprocess.run(["git", "-C", root or ANCHOR, *args], capture_output=True, text=True, timeout=10)
    except (OSError, subprocess.SubprocessError):
        return None
    return run.stdout if run.returncode == 0 else None


def parse_table(text, name):  # (lower-cased patterns, problem or None) of one table's text.
    try:
        targets = json.loads(text)["targets"]
        if not isinstance(targets, list):
            raise ValueError("targets 必须是数组")
        patterns = []
        for target in targets:
            found = target["patterns"]
            if not (isinstance(found, list) and found and all(isinstance(p, str) and len(p.strip()) >= 6 for p in found)):
                raise ValueError(f"{target.get('id', '?')} 的 patterns 必须是非空字符串数组，每项至少 6 个字符（太短会误伤）")
            patterns += [p.strip().lower() for p in found]
        return patterns, None
    except (ValueError, KeyError, TypeError, AttributeError) as error:
        return None, f"生产特征表 {name} 缺失或不合格（{error.__class__.__name__}: {error}）"


def read_table_file(path):
    try:
        return parse_table(Path(path).read_text(encoding="utf-8"), os.path.basename(path))
    except OSError as error:
        return None, f"生产特征表 {os.path.basename(path)} 缺失或不合格（{error.__class__.__name__}: {error}）"


def main_table_text(root=None):  # (table text on origin/main or None, git failure or None): read by its full ref name (a tag called origin/main cannot shadow it); a missing ref or path is no failure.
    try:
        run = subprocess.run(["git", "-C", root or REPO_ROOT, "cat-file", "--batch"], input="".join(f"refs/remotes/origin/main:{rel}\n" for rel in MAIN_TABLE_PATHS).encode(), capture_output=True, timeout=10)
    except (OSError, subprocess.SubprocessError) as error:
        return None, f"{error.__class__.__name__}: {error}"
    if run.returncode != 0:
        return None, run.stderr.decode("utf-8", "replace").strip()[:200] or f"git 退出码 {run.returncode}"
    data, at = run.stdout, 0  # one process for every path (a hook's p99 budget): "<id> blob <size>\n<content>\n" or "<name> missing\n" per path, in order
    for _ in MAIN_TABLE_PATHS:
        end = data.find(b"\n", at)
        if end < 0:
            return None, "git cat-file 输出不完整"
        head = data[at:end].split()
        if len(head) == 3 and head[1] == b"blob":
            return data[end + 1:end + 1 + int(head[2])].decode("utf-8", "replace"), None
        at = end + 1 + (int(head[2]) + 1 if len(head) == 3 and head[2].isdigit() else 0)
    return None, None


def load_table(path=None):
    """(patterns, problem, warning). With `path` only that file is read (the self-test's format checks). Otherwise the local table (PROD_TARGETS_FILE, else the one next to
    this file) is unioned with origin/main's, also when PROD_TARGETS_FILE is set (an environment variable cannot replace the trust anchor); an empty union is a problem."""
    patterns, problem = read_table_file(path or TABLE)
    if problem:
        return None, problem, None
    if path is not None:
        return patterns, None, None if patterns else "生产特征表为空。"
    text, failure = main_table_text()
    warnings = [f"读不到 origin/main 上的生产特征表（git 出错：{failure}），门只按本工作区的表判断。"] if failure else []
    main = parse_table(text, "origin/main")[0] if text is not None else None
    main = (main or []) + [p for r in sorted({os.path.realpath(r) for r in ROOTS if r} - {os.path.realpath(REPO_ROOT)}) for p in parse_table(main_table_text(r)[0] or "{}", "")[0] or []]  # cwd's, CLAUDE_PROJECT_DIR's: only more
    missing = [p for p in main or [] if p not in patterns]
    if missing:
        warnings.append(f"本工作区的 prod_targets.json 缺少 origin/main 上的 {len(missing)} 项特征，门按两者并集判断；请把分支更新到 main。")
    patterns = patterns + missing
    if not patterns:
        return None, "生产特征表为空（本工作区与 origin/main 的并集里没有任何生产特征）", " ".join(warnings) or None
    return patterns, None, " ".join(warnings) or None


def readonly_tool_files():  # Repo-relative paths of the committed read-only evidence tools (table section readonly_tools): origin/main's table when it lists any, else the local one.
    def listed(text):
        try:
            tools = json.loads(text).get("readonly_tools", {})
            return {DOCS_ROOT + "/" + tools.get("base", "") + f for f in tools.get("files", []) if isinstance(f, str)}
        except (ValueError, AttributeError, TypeError):
            return set()
    found = listed(main_table_text(ANCHOR)[0] or "")
    if not found:
        found = listed(read_text(TABLE, None, 1 << 20) or "")
    return found | EXTRA_READONLY


def could_concern(text):  # Pre-filter (text already `plain`): False only when the gate would certainly allow; a remote tool's name as a substring, as judge reads an unreadable line.
    if REMOTE_WORD.search(text) or any(t in text for t in LONG_REMOTE) or "$" in text or "`" in text or "{" in text or PATH_COMMAND.search(text):
        return True
    patterns, problem, _ = load_table()
    return bool(problem) or any(p in text for p in patterns)


def brace_variants(word, limit=256):  # Words bash (and curl, on a quoted URL) makes from `a{1,2}b` / `{1..3}`; the word itself without braces; capped.
    match = re.search(r"\{([^{}]*)\}", word)
    if not match:
        return [word]
    inner = match.group(1)
    seq = re.fullmatch(r"(-?\d+)\.\.(-?\d+)", inner)
    if seq:
        a, b = int(seq.group(1)), int(seq.group(2))
        parts = [str(n) for n in (range(a, b + 1) if a <= b else range(a, b - 1, -1))][:limit]
    elif "," in inner:
        parts = inner.split(",")
    else:
        return [word]
    out = []
    for part in parts:
        for rest in brace_variants(word[:match.start()] + part + word[match.end():], limit):
            out.append(rest)
            if len(out) >= limit:
                return out
    return out


def text_hits(texts, patterns):  # True when a pattern occurs in the texts or in a brace expansion of one of their words.
    for text in texts:
        low = text.lower()
        if any(p in low for p in patterns):
            return True
        if "{" in low and any("{" in word and any(p in v for v in brace_variants(word) for p in patterns) for word in re.split(r"\s+", low)):
            return True
    return False


def has_explicit_target(argv):
    return any(t in HOST_OPTIONS or (t.startswith(("-h", "-S")) and len(t) > 2 and not t.startswith("--")) or t.startswith(("--host=", "--server=", "--url=")) or "://" in t for t in argv[1:])


def touches(command, patterns, env_hit=False):
    if text_hits(command.argv + list(command.env.values()), patterns):
        return True
    return env_hit and name_of(command.argv[0]) in SQL_TOOLS | {"aliyun"} and not has_explicit_target(command.argv)


def value_of(argv, i, eq, inline):  # Value of the option at argv[i]: the `=value` part, else the next word, else "".
    return inline if eq else argv[i + 1] if i + 1 < len(argv) else ""


def long_option(token, names):  # `--x` / `--x=v` is one of `names` or a prefix of one (GNU getopt_long takes a unique prefix: `sort --outp=f` is `--output=f`).
    if not token.startswith("--") or token == "--":
        return False
    given = token[2:].split("=", 1)[0]
    return bool(given) and any(name.startswith(given) for name in names)


def bundled(token, valued=""):  # Letters of a bundled short option up to the first one that takes a value.
    letters = []
    for c in token[1:]:
        if c in valued:
            break
        letters.append(c)
    return letters


def short_has(token, letter, valued=""):
    return token.startswith("-") and not token.startswith("--") and letter in bundled(token, valued)


def mask_sql(text):
    """Statement without comments and string literals, in one quote-aware pass (`'--'` cannot hide what follows); double-quoted identifiers keep their content
    (`"nextval"` is nextval), bracketed/backticked ones are emptied. None when it cannot be judged: unterminated string/comment/dollar quote, or a backslash in a string."""
    out, i, n = [], 0, len(text)
    while i < n:
        char, two = text[i], text[i:i + 2]
        if two == "--":
            end = text.find("\n", i)
            i = n if end < 0 else end
            out.append(" ")
        elif two == "/*":
            end = text.find("*/", i + 2)
            if end < 0:
                return None
            i = end + 2
            out.append(" ")
        elif char in "'\"":
            j = i + 1
            while True:
                end = text.find(char, j)
                if end < 0:
                    return None
                if text[end + 1:end + 2] != char:
                    break
                j = end + 2
            content = text[i + 1:end]
            if "\\" in content:
                return None
            out.append("''" if char == "'" else " " + content + " ")
            i = end + 1
        elif char in "[`":
            end = text.find("]" if char == "[" else "`", i + 1)
            if end < 0:
                return None
            out.append("[]" if char == "[" else "``")
            i = end + 1
        elif char == "$" and re.match(r"\$(?:[A-Za-z_]\w*)?\$", text[i:]):
            tag = re.match(r"\$(?:[A-Za-z_]\w*)?\$", text[i:]).group(0)
            end = text.find(tag, i + len(tag))
            if end < 0:
                return None
            out.append("''")
            i = end + len(tag)
        else:
            out.append(char)
            i += 1
    return "".join(out)


def statement_is_read(text, tool):
    masked = mask_sql(text)
    if masked is None or (tool in ("sqlcmd", "osql") and "$(" in masked):  # sqlcmd substitutes -v variables into the statement after the check
        return False
    for part in re.split(r";|\n\s*go\s*(?:\n|$)", SQL_SET.sub(";", masked), flags=re.I):
        stmt = part.strip()
        if not stmt:
            continue
        if any(not META_READ.match(meta.group(1)) for meta in re.finditer(r"\\([A-Za-z!]\w*\+?)", stmt)):
            return False
        if stmt.startswith("\\"):
            stmt = re.sub(r"^(?:\\\w+\+?\s*)+", "", stmt)
        if stmt and not SQL_READ.match(stmt):
            return False
        if stmt and not re.match(r"(?:show|describe|desc)\b", stmt, re.I) and SQL_WRITE.search(stmt):
            return False
        if tool in ("mongosh", "mongo") and MONGO_WRITE.search(stmt):
            return False
    return True


def read_text(path, cwd, limit):
    full = path if os.path.isabs(path) else os.path.join(cwd or os.getcwd(), path)
    try:
        if os.path.isfile(full) and os.path.getsize(full) <= limit:
            return Path(full).read_text(encoding="utf-8", errors="replace")
    except OSError:
        pass
    return None


def sql_sources(argv, tool, cwd, stdin_texts):  # Statement texts the command will run, or None when some cannot be seen (unreadable file, unseen stdin, a dangerous option).
    statements, files, i = [], [], 1
    opts, fopts, danger = STATEMENT_OPTIONS.get(tool, set()), FILE_OPTIONS.get(tool, set()), DANGEROUS_SQL_OPTIONS.get(tool, set())
    while i < len(argv):
        token = argv[i]
        option, eq, inline = token.partition("=")
        if token in danger or option in danger:
            return None
        if tool == "psql" and option in ("-o", "--output") and value_of(argv, i, eq, inline).startswith("|"):
            return None
        if token.startswith("<<"):
            if stdin_texts is None:
                return None
        elif token == "<":
            if i + 1 >= len(argv):
                return None
            files.append(argv[i + 1])
            i += 1
        elif option in opts | fopts and (inline or i + 1 < len(argv)):
            (statements if option in opts else files).append(value_of(argv, i, eq, inline))
            i += 0 if eq else 1
        elif not token.startswith("--") and len(token) > 2 and token[:2] in opts | fopts:  # -cSELECT, -ifix.sql
            (statements if token[:2] in opts else files).append(token[2:])
        elif tool in ("mongosh", "mongo") and token.endswith((".js", ".mongodb")) and not token.startswith("-"):
            files.append(token)
        i += 1
    for path in files:
        content = read_text(path, cwd, MAX_SQL_FILE)
        if content is None:
            return None
        statements.append(content)
    statements += stdin_texts or []
    return statements or None


def redis_writes(argv):
    positional, i = [], 1
    while i < len(argv):
        if argv[i].startswith("-") and len(argv[i]) > 1:
            i += 2 if argv[i] in REDIS_VALUE_OPTIONS else 1
            continue
        positional.append(argv[i])
        i += 1
    if not positional:
        return True
    verb = positional[0].lower()
    if verb in REDIS_READ_SUB:
        return (positional[1].lower() if len(positional) > 1 else "") not in REDIS_READ_SUB[verb]
    return verb not in REDIS_READ


def sql_writes(command, cwd, stdin_texts=None):
    argv, tool = command.argv, name_of(command.argv[0])
    if tool == "redis-cli":
        return redis_writes(argv)
    if any(t in HELP_ONLY or (tool == "psql" and t == "-V") for t in argv[1:]):
        return False
    if tool == "bcp":
        words = [t.lower() for t in argv[1:]]
        if "queryout" in words:
            return not statement_is_read(argv[words.index("queryout")], "sqlcmd")
        return not ("out" in words and "in" not in words)
    if command.piped and stdin_texts is None:
        return True
    sources = sql_sources(argv, tool, cwd, stdin_texts)
    return sources is None or not all(statement_is_read(s, tool) for s in sources)


def aliyun_words(argv):  # Non-option words of an aliyun command (product, API name or ROA method and path) with option values skipped.
    words, i = [], 1
    while i < len(argv):
        if argv[i].startswith("-"):
            i += 1 if "=" in argv[i] else 2
            continue
        words.append(argv[i])
        i += 1
    return words


def aliyun_writes(argv):
    words = aliyun_words(argv)
    if len(words) == 3 and words[1].upper() in ("GET", "HEAD") and words[2].startswith("/"):  # ROA style: product METHOD /path
        return False
    return not (len(words) == 2 and ALIYUN_READ.match(words[1]))


def aliyun_secret(argv):
    return any(ALIYUN_SECRET.match(w) for w in aliyun_words(argv))


def curl_config(argv):
    return any(t in ("-K", "--config") or t.startswith(("--config=", "-K")) for t in argv[1:])


def other_scheme(token):
    return bool(URL_SCHEME.match(token)) and not re.match(r"^https?://", token, re.I)


def curl_writes(argv):  # A method other than GET/HEAD/OPTIONS, data/upload options, config files, `--next`, `--quote`, a non-http(s) protocol (short options may carry their value).
    method, i = None, 1
    while i < len(argv):
        token = argv[i]
        if other_scheme(token):
            return True
        if token.startswith("--"):
            name, eq, inline = token[2:].partition("=")
            if name in CURL_LONG_WRITE:
                return True
            if name == "request":
                method = value_of(argv, i, eq, inline).upper()
                i += 0 if eq else 1
        elif token.startswith("-") and len(token) > 1:
            for j, char in enumerate(token[1:], 1):
                if char in "dFT:KQ":
                    return True
                if char == "X" or char in CURL_VALUE_SHORT:
                    if char == "X":
                        method = (token[j + 1:] or (argv[i + 1] if i + 1 < len(argv) else "")).upper()
                    i += 0 if token[j + 1:] else 1
                    break
        i += 1
    return method is not None and method not in READ_METHODS


def wget_writes(argv):
    for i, token in enumerate(argv):
        name, _, inline = token.partition("=")
        if long_option(name, ("post-data", "post-file", "body-data", "body-file")):
            return True
        if long_option(name, ("method",)) and (inline or (argv[i + 1] if i + 1 < len(argv) else "")).upper() not in READ_METHODS:
            return True
    return any(other_scheme(t) for t in argv[1:])


def httpie_writes(argv):
    words = [t for t in argv[1:] if not t.startswith("-")]
    if words and words[0].upper() in ("POST", "PUT", "PATCH", "DELETE"):
        return True
    return any(re.search(r"[^/:]=|:=|@", w) and not w.startswith(("http://", "https://")) for w in words[1:]) or any(t in argv for t in ("--form", "--json", "-f", "-j"))


def operands_of(argv, short_values, long_values):
    operands, i = [], 1
    while i < len(argv):
        token = argv[i]
        if token == "--":
            operands += argv[i + 1:]
            break
        if token.startswith("--"):
            i += 2 if token in long_values else 1
        elif token.startswith("-") and len(token) > 1:
            letters = token[1:]
            valued = next((j for j, char in enumerate(letters) if char in short_values), None)
            i += 2 if valued == len(letters) - 1 else 1
        else:
            operands.append(token)
            i += 1
    return operands


def option_value_list(argv, short, long_names):  # Values of the given short (`-e`) and long (`--rsh`) options, attached forms included.
    values = []
    for i, token in enumerate(argv[1:], 1):
        name, eq, inline = token.partition("=")
        if name in long_names:
            values.append(value_of(argv, i, eq, inline))
        elif token.startswith("-") and not token.startswith("--") and len(token) >= 2 and token[1] in short:
            values.append(token[2:] if len(token) > 2 else argv[i + 1] if i + 1 < len(argv) else "")
    return values


def copy_writes(argv, tool, patterns):
    if tool == "scp":
        operands = operands_of(argv, SCP_VALUE_SHORT, set())
        if any(re.search(r"(?i)proxycommand|localcommand|permitlocalcommand", v) for v in option_value_list(argv, "o", set())) or option_value_list(argv, "S", set()):
            return True
        if "-O" in argv and any(REMOTE_HOST.match(o) and SHELL_META.search(o.split(":", 1)[1]) for o in operands):
            return True
    else:
        operands = operands_of(argv, RSYNC_VALUE_SHORT, RSYNC_VALUE_LONG)
        if any(t.startswith(("--remove-source-files", "--rsync-path")) for t in argv[1:]) or any(not PLAIN_RSH.match(v.strip()) for v in option_value_list(argv, "e", {"--rsh"})):
            return True
    if len(operands) < 2:
        return True
    return REMOTE_HOST.match(operands[-1]) is not None or (bool(patterns) and text_hits([operands[-1]], patterns))


def ssh_remote_command(argv):  # (remote command words or None, risky option seen).
    i, risky = 1, False
    while i < len(argv):
        token = argv[i]
        if not (token.startswith("-") and len(token) > 1):
            return (argv[i + 1:] or None), risky
        letters = token[1:]
        valued = next((j for j, c in enumerate(letters) if c in SSH_VALUE_OPTIONS), None)
        risky |= any(c in SSH_RISKY_SHORT for c in letters[:None if valued is None else valued + 1])
        value = (letters[valued + 1:] or (argv[i + 1] if i + 1 < len(argv) else "")) if valued is not None else ""
        if valued is not None and letters[valued] == "o" and re.split(r"[=\s]", value.strip(), maxsplit=1)[0].lower() not in SSH_SAFE_OPTIONS:
            risky = True  # an allow-list: KnownHostsCommand, PKCS11Provider, ProxyCommand, LocalCommand, Include, Match exec … all run or load something
        i += 2 if valued is not None and not letters[valued + 1:] else 1
    return None, risky


def read_only_remote(command):
    argv, tool = command.argv, name_of(command.argv[0])
    sub, opts = [t for t in argv[1:] if not t.startswith("-")], argv[1:]
    return not ((tool == "find" and any(t in FIND_WRITE for t in argv))
                or (tool == "sort" and any(long_option(t, ("output", "compress-program")) or short_has(t, "o", "kStT") for t in opts))
                or (tool == "uniq" and len(sub) > 1)
                or (tool == "journalctl" and any(long_option(t, ("vacuum-size", "vacuum-time", "vacuum-files", "rotate", "flush", "relinquish-var", "smart-relinquish-var", "sync", "setup-keys", "update-catalog")) for t in argv))
                or (tool == "date" and any(long_option(t, ("set",)) or short_has(t, "s", "dfrIR") for t in opts))
                or (tool == "ss" and any(long_option(t, ("kill",)) or short_has(t, "K") for t in opts))
                or (tool == "hostname" and sub)
                or (tool == "file" and any(long_option(t, ("compile",)) or short_has(t, "C", "fmeP") for t in opts))
                or (tool == "pg_dump" and any(long_option(t, ("file",)) or t.startswith("-f") for t in opts))
                or (tool in SSH_READ_SUBCOMMANDS and (sub[:1] or [None])[0] not in SSH_READ_SUBCOMMANDS[tool]))


def ssh_writes(argv, patterns, cwd):
    remote, risky = ssh_remote_command(argv)
    if risky or not remote:  # an interactive session, a tunnel or a local-command option cannot be told from a write
        return True
    try:
        commands = simple_commands(" ".join(remote))
    except ValueError:
        return True
    for command in commands:
        tool = name_of(command.argv[0])
        if command.redirected or command.unresolved or any(t in (">", ">>") for t in command.argv):
            return True
        if (tool in SQL_TOOLS and sql_writes(command, cwd)) or (tool not in SQL_TOOLS and (tool not in SSH_READ or not read_only_remote(command))):
            return True
    return False


def is_write(command, patterns, cwd=None, stdin_texts=None):  # True when this remote-tool command writes (or its effect cannot be seen).
    argv, tool = command.argv, name_of(command.argv[0])
    if tool in SQL_TOOLS or tool == "bcp":
        return sql_writes(command, cwd, stdin_texts)
    if tool in ("scp", "rsync"):
        return copy_writes(argv, tool, patterns)
    if tool == "ssh":
        return ssh_writes(argv, patterns, cwd)
    judges = {"aliyun": aliyun_writes, "curl": curl_writes, "wget": wget_writes, "http": httpie_writes, "https": httpie_writes, "xh": httpie_writes, "xhs": httpie_writes}
    return tool in WRITE_ONLY_TOOLS or (tool in judges and judges[tool](argv))


def host_has_variable(token):  # `$` in the host part of a URL (a variable port is fine), or anywhere in a word that is not a URL.
    if "://" not in token:
        return "$" in token
    authority = token.split("://", 1)[1].split("/", 1)[0].split("?", 1)[0]
    hostport = authority.rpartition("@")[2]
    host = hostport.rsplit(":", 1)[0] if ":" in hostport and not hostport.startswith("[") else hostport
    return "$" in host or "$" in authority.rpartition("@")[0]


def target_unresolved(command):  # A word naming the target (host option value, URL, operand, aliyun id) still holds a `$` expansion; data and statement values do not count.
    argv, tool, skip = command.argv, name_of(command.argv[0]), False
    for i, token in enumerate(argv[1:], 1):
        if skip:
            skip = False
            continue
        if not token.startswith("-"):
            if host_has_variable(token):
                return True
            continue
        option, eq, inline = token.partition("=")
        if not token.startswith("--") and len(token) > 2 and token[:2] in HOST_OPTIONS and "$" in token[2:]:
            return True
        if tool == "aliyun" and (option.lower().endswith(("id", "ids", "name")) or option in ("--profile", "-p", "--endpoint")):
            if "$" in value_of(argv, i, eq, inline):
                return True
        elif option in HOST_OPTIONS and "$" in value_of(argv, i, eq, inline):
            return True
        skip = not eq and (option in DATA_OPTIONS or option in HOST_OPTIONS or tool == "aliyun")
    return False


def main_blob(rel):  # Object id of `rel` on origin/main, or None.
    return (git("rev-parse", "--verify", "-q", f"refs/remotes/origin/main:{rel}") or "").strip() or None


def same_as_main(path):  # Repo-relative path of `path` when its content is byte for byte the file of that path on origin/main (edited, staged or new files are not), else None.
    rel = os.path.relpath(os.path.realpath(path), os.path.realpath(ANCHOR))
    if rel.startswith(".."):
        return None
    theirs = main_blob(rel)
    return rel if theirs and (git("hash-object", "--no-filters", "--", os.path.realpath(path)) or "").strip() == theirs else None


def script_operand(argv):  # The script an interpreter runs (first non-option word), or None for inline code (-c, -m, -e, -p) or none.
    inline, i = name_of(argv[0]).startswith(("python", "node", "perl", "ruby", "php")), 1
    while i < len(argv):
        token = argv[i]
        if token in ("-c", "-m", "-e", "-p", "-E", "--eval", "--print") or (inline and token.startswith("-") and not token.startswith("--") and any(c in token[1:] for c in "cmep")):
            return None
        if token.startswith("-") and token != "-":
            i += 2 if token in ("-X", "-W", "-Q", "-r", "--require") else 1
            continue
        return token
    return None


def trusted_script(argv, cwd):  # A document script at its fixed path, a listed read-only tool, or the `--self-test` of a guard script, only while the file is exactly the one on origin/main.
    script = script_operand(argv) if INTERPRETER.match(name_of(argv[0])) else None
    if script is None:
        return False
    global ANCHOR
    ANCHOR = ANCHOR or trust_root(ROOTS[0]) or os.devnull
    full = script if os.path.isabs(script) else os.path.join(cwd or os.getcwd(), script)
    full = full if os.path.isfile(full) else os.path.join(ANCHOR, script)
    rel = same_as_main(full) if os.path.isfile(full) else None
    if rel is None:
        return False
    if rel in readonly_tool_files() or rel in DOC_SCRIPTS:
        return True
    return rel.startswith(GUARD_DIRS) and rel.endswith(".py") and argv[argv.index(script) + 1:] == ["--self-test"]


def body_is_judged_separately(command):  # A shell whose program is a `-c` string, an eval argument, a heredoc or a here-string: that body is a command of its own in the list.
    tool, argv = name_of(command.argv[0]), command.argv
    return tool in SHELLS and (tool == "eval" or any(SHORT_C.fullmatch(t) for t in argv[1:]) or any(t.startswith("<<") for t in argv))


def indirect_hit(command, raw_hit, patterns, cwd):  # An interpreter or shell reading its program from a file, heredoc or pipe that mentions production (an unreadable script counts when the line mentions it).
    tool = name_of(command.argv[0])
    direct = "/" in command.argv[0] and tool not in NO_CONNECT and not INTERPRETER.match(tool)  # ./x, /tmp/x: the file itself is the program
    if not (INTERPRETER.match(tool) or direct) or trusted_script(command.argv, cwd):
        return False
    script = command.argv[0] if direct else script_operand(command.argv)
    if script is not None:
        content = read_text(script, cwd, MAX_SCRIPT_FILE)
        if (content is None and raw_hit) or (content is not None and any(p in plain(content) for p in patterns)):
            return True
    return raw_hit and (command.piped or any(t.startswith("<") for t in command.argv))


def no_connect_ok(command, patterns=()):  # Tools that cannot connect on their own, used the way that cannot: local git subcommands, rg without --pre, sed without e/w, sort without --compress-program.
    argv, tool = command.argv, name_of(command.argv[0])
    if tool not in NO_CONNECT or (patterns and text_hits(list(command.env.values()), patterns)):  # GIT_EXTERNAL_DIFF, GIT_PAGER, PAGER, EDITOR, LESSOPEN … are programs handed to the tool
        return False
    if tool == "git":
        sub, i = None, 1
        while i < len(argv):
            token = argv[i]
            if token == "-c" or token.startswith(("--exec-path", "--upload-pack", "--receive-pack")) or (token.startswith("-c") and token != "-C" and not token.startswith("--")):
                return False
            if token.startswith("-"):
                i += 2 if token in ("-C", "--git-dir", "--work-tree", "--namespace") else 1
                continue
            sub = token
            break
        if sub not in LOCAL_GIT or sub in NETWORK_GIT:  # an unlisted sub-command (bisect run, filter-branch, submodule foreach, an alias …) can run any program
            return False
        return not any(long_option(t, GIT_RUNS_COMMANDS) or (sub == "rebase" and (t == "-x" or (t.startswith("-") and not t.startswith("--") and "x" in t[1:]))) or (sub == "grep" and t.startswith("-") and not t.startswith("--") and "O" in t[1:]) for t in argv)
    if tool == "rg":
        return not any(t.startswith(("--pre", "--hostname-bin")) for t in argv)
    if tool == "sed":
        return not any(SED_EXEC.search(t) for t in argv[1:] if not t.startswith("-") or t.startswith(("-e", "--expression")))
    return tool != "sort" or not any(long_option(t, ("compress-program",)) for t in argv)


def allowed_without_connecting(command, cwd=None, patterns=()):
    return no_connect_ok(command, patterns) or trusted_script(command.argv, cwd)


def stdin_texts_for(index, command, commands, text):  # Statement texts a command reads from a here-document, here-string or an echo pipe; None when its standard input cannot be seen.
    argv, texts = command.argv, []
    if any(t.startswith("<<") and t != "<<<" for t in argv):
        for body, _, quoted in split_heredocs(text)[1]:
            if not quoted and ("$(" in body or "`" in body):
                return None
            texts.append(body)
    if "<<<" in argv and argv.index("<<<") + 1 < len(argv):
        texts.append(argv[argv.index("<<<") + 1])
    if command.piped:
        previous = commands[index - 1] if index > 0 else None
        if previous is None or name_of(previous.argv[0]) != "echo" or any("\\" in t for t in previous.argv[1:]):
            return None
        texts.append(" ".join(previous.argv[1:]))
    return texts


def judge(command_text, patterns, problem=None, cwd=None, environ=None):  # Refusal text or None.
    environ = os.environ if environ is None else environ
    env_hit = bool(patterns) and any(text_hits([str(environ.get(v, ""))], patterns) for v in ENV_TARGET_VARS)
    raw_hit = bool(patterns) and text_hits([plain(command_text)], patterns)
    try:
        commands = simple_commands(command_text)
    except ValueError:
        if raw_hit or SHORT_REMOTE.search(plain(command_text)) or any(t in plain(command_text) for t in LONG_REMOTE):
            return "命令读不懂（引号未闭合、嵌套过深等）且含生产特征或 psql／ssh／aliyun 等远程工具，无法确认不是连生产的写操作，拒绝。"
        return None
    for index, command in enumerate(commands):
        tool = name_of(command.argv[0])
        if tool == "aliyun" and aliyun_secret(command.argv):
            return "拒绝：该 aliyun 接口会返回私钥或口令（如 DescribeDBInstanceSSL、GetSecretValue），底线 3 不调用返回密钥的接口。"
        if tool == "curl" and curl_config(command.argv):
            return "拒绝：curl 的 -K／--config 从文件或标准输入取目标与选项，看不到真实目标；把选项写在命令行上。"
        if tool not in REMOTE_TOOLS:
            if body_is_judged_separately(command) or tool in SHELL_HEADS:
                continue
            if patterns is None:
                if INTERPRETER.match(tool) and not trusted_script(command.argv, cwd):
                    return f"{problem}：解释器命令（{tool}）无法判断是否碰到生产，一律拒绝；修好 scripts/gates/prod_targets.json 后再做。"
                continue
            # a production target anywhere on the line makes every other command of the line answer for itself (`echo '…' > x && chmod +x x && ./x`)
            if (raw_hit or touches(command, patterns) or indirect_hit(command, raw_hit, patterns, cwd)) and not allowed_without_connecting(command, cwd, patterns):
                return (f"拒绝：{tool} 命令碰到生产目标，而它不是点名的只读工具（psql／sqlcmd／aliyun Describe／ssh 只读白名单等）。CLAUDE.md 底线 4：不做生产写操作；"
                        "生产只读取证走 ADR-0037 专用只读账号和已登记的只读入口（直接用 psql／sqlcmd／ssh 的只读用法）。")
            continue
        writes = is_write(command, patterns, cwd, stdin_texts_for(index, command, commands, command_text) if tool in SQL_TOOLS else None)
        if patterns is None:
            if writes:
                return f"{problem}：写操作类命令（{tool}）无法判断是否连生产，一律拒绝；修好 scripts/gates/prod_targets.json 后再做。"
            continue
        if writes and touches(command, patterns, env_hit):
            return (f"拒绝：{tool} 命令连到生产目标并带写操作（或写效果看不出来）。CLAUDE.md 底线 4：不做生产写操作；生产取证只用专用只读账号且只读（ADR-0037）。"
                    "确需写入的事项属须用户决定的事（底线 5）：写收件箱等答复，不在命令里绕。")
        if writes and command.unresolved and target_unresolved(command):
            return f"拒绝：{tool} 的写操作，目标（主机、URL、实例号）含读不出的变量或命令替换，无法确认不是生产；把目标写成字面值再执行。"
    return None


def trust_root(cwd):  # cwd's work tree when cwd and CLAUDE_PROJECT_DIR are in one repository (same real `--git-common-dir`; a worktree counts), else None: fail closed. Each side must print exactly two absolute paths (a newline in a path shifts them) and cwd must lie under the anchor.
    found = [(git("rev-parse", "--path-format=absolute", "--git-common-dir", "--show-toplevel", root=d) or "").split("\n") if d else [] for d in (cwd, os.environ.get("CLAUDE_PROJECT_DIR"))]
    sane = all(len(f) == 3 and f[2] == "" and all(os.path.isabs(p) for p in f[:2]) for f in found)
    return found[0][1] if sane and os.path.realpath(found[0][0]) == os.path.realpath(found[1][0]) and os.path.commonpath([os.path.realpath(cwd), os.path.realpath(found[0][1])]) == os.path.realpath(found[0][1]) else None


def decide(payload):  # (exit code, stdout, stderr) for one hook payload (a dict); the dispatcher calls this in-process.
    if payload.get("tool_name", "Bash") != "Bash":
        return 0, "", ""
    tool_input = payload.get("tool_input")
    command = tool_input.get("command") if isinstance(tool_input, dict) else None
    if not isinstance(command, str):
        return 2, "", "prod_write_gate.py：Bash 钩子输入里没有字符串形式的 command，无法判断，按拒绝处理。\n"
    cwd = payload.get("cwd") if isinstance(payload.get("cwd"), str) and payload.get("cwd") else None
    global ANCHOR, ROOTS
    ANCHOR, ROOTS = None, (cwd, os.environ.get("CLAUDE_PROJECT_DIR"), os.getcwd())  # the anchor is resolved by trusted_script, on first use
    try:
        patterns, problem, warning = load_table()
        reason = judge(command, patterns, problem, cwd)
    finally:
        ANCHOR, ROOTS = REPO_ROOT, ()
    if reason:
        return 2, "", reason + "\n"
    notes = [f"告警：{problem}，写操作类命令会被拒绝。"] if problem and (SHORT_REMOTE.search(plain(command)) or any(t in plain(command) for t in LONG_REMOTE)) else []
    if warning and (REMOTE_WORD.search(plain(command)) or "$" in command):
        notes.append(f"告警：{warning}")
    out = json.dumps({"hookSpecificOutput": {"hookEventName": "PreToolUse", "additionalContext": "\n".join(notes)}}, ensure_ascii=False) + "\n" if notes else ""
    return 0, out, ""


def guarded_main():  # unreadable hook input or any error fails closed
    try:
        payload = json.load(sys.stdin)
        code, out, err = decide(payload) if isinstance(payload, dict) else (2, "", "prod_write_gate.py 读不懂钩子输入（不是 JSON 对象），按失败处理\n")
    except Exception as error:
        code, out, err = 2, "", f"prod_write_gate.py 出错（{error.__class__.__name__}: {error}），按失败处理\n"
    sys.stdout.write(out)
    sys.stderr.write(err)
    return code


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        sys.path.insert(0, HERE)
        from prod_write_gate_selftest import self_test
        sys.exit(self_test())
    sys.exit(guarded_main())
