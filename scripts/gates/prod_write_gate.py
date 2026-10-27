#!/usr/bin/env python3
"""生产写门（PreToolUse：Bash、Monitor、终端面板，跑 tool_input.command 的工具）：命令碰到生产目标，除非是点名的只读用法，一律拒绝。防底线 4（生产只读）；安全门，出错失败关闭（退出码 2）。

生产目标 = prod_targets.json 的 patterns（与 origin/main 上那份取并集，钩子调用时再并上 cwd 与 CLAUDE_PROJECT_DIR 所在仓库的），出现在命令的词里（引号、ANSI-C、续行、花括号、变量与 export／set -a 拼接、反引号与 `$(…)` 展开后）或环境变量里即「碰到」。
行里有可能起注释的 `#` 或 here-document，且含生产特征、远程工具或目标环境变量：整行拒绝，不模仿 shell 剥离（宁可误拦，intent 2026-10-26-140639）。每条简单命令（`bash -c`／`-lc`／`eval`／here-string／`echo … | bash`／`env -S`／`<(…)` 递归，超过 3 层按读不懂）：
- 远程工具（SQL 客户端、bcp、aliyun、ssh、scp、rsync、sftp、curl、wget、httpie）名字出现即拒，只认 origin/main 特征表 remote_entries 登记的整行写法，登记写法碰到生产特征照样拒（intent 2026-10-26-160810）。
- 行里任意位置有生产特征，或指向生产的目标环境变量（命令里设的、会话环境里的），整行拒绝，不再按「这个命令不连接」放行；变量拼接、花括号展开后才命中的同样拒绝（intent 2026-10-26-165321 A）。
- 命令参数里凡是存在的普通文件（含 `./x` 这样按路径运行的程序本身）都读：内容（≤256 KB 去引号读，更大的按字节读）不含生产特征才放行，读不了或超过 512 MB 按含特征算，解释器指向目录也按含特征算（同上 B）。
  只有 origin/main 特征表 no_exec_tools 登记的、已知不执行参数文件的程序免读位置参数（选项值、选项里嵌的词、环境变量照读）；解释器名单只作说明（intent 2026-10-26-164931 A′）。
  免读只给整行就是一条纯命令：没有 ; & | 换行、替换、括号花括号，没有赋值（NAME=、NAME+=、数组），没有 eval、alias、function、hash 等字样，第一个词就是登记程序；否则整行不免（调度 10-26 深夜定）。
- 行里有换目录（cd、pushd、popd，包装命令或程序自己的目录选项），又运行解释器、shell、启动器、按路径的程序或会运行别的命令的程序：整行拒，不推算目录（intent 2026-10-26-164931）。
- 唯一例外是可信入口：只认 origin/main 特征表 trusted_entries 登记的固定入口，整行须与登记的解释器、脚本、参数形式逐字匹配，脚本连同导入链、同目录可导入文件、trust_dirs 都须与 origin/main 一致（intent 2026-10-26-132048）。脚本在 docs 根下；唯一另一个可信前缀是字面的 scripts/evidence/（取证单入口，二乙 #6），按仓库根解析，真实路径须在其内，同目录按名字运行的 sheet_*.py 连同导入链一并跟进比对。
- 特征表缺失、不合格，或与 origin/main 的并集为空：远程工具与解释器拒绝，其余放行并告警；origin/main 读取时 git 出错只告警。读不懂且含远程工具名、特征或目标环境变量拒绝。
已知未覆盖（intent 2026-10-26-165321，复审不判必改）：脚本运行时做什么（运行时拼出或下载生产目标、从管道或标准输入读程序、解释器内联代码自己去连），门只看命令行与被运行的文件；兜底是生产只读账号（底线 4）。
命令解析（原 command_words）也在本文件，其他钩子经 scripts/hooks/command_words.py 兼容层导入。自测在 gates_selftest.py 的 prod_write_gate 段（O8 合一）。
退出码 2 + 标准错误 = 拒绝；0 = 放行。用法：prod_write_gate.py  |  prod_write_gate.py --self-test
"""
import ast
import collections
import importlib.util
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
CONTINUATION = re.compile(r"(?<!\\)((?:\\\\)*)\\\n")  # an unescaped backslash-newline: after `\\` the newline still ends the command (3eac19088 final M1)
PUA_DOLLAR, PUA_TICK = "\ue000", "\ue001"  # `$` and a backtick inside single quotes: plain characters, not expansions
SIMPLE_ESCAPES = {"a": "\a", "b": "\b", "e": "\x1b", "E": "\x1b", "f": "\f", "n": "\n", "r": "\r", "t": "\t", "v": "\v", "\\": "\\", "'": "'", '"': '"', "?": "?"}
SEPARATORS = {";", "&&", "||", "|", "&", "(", ")", "|&"}
KEYWORDS = {"if", "then", "do", "else", "elif", "while", "until", "!", "{", "}", "fi", "done", "esac"}
WRAPPERS = {"command": set(), "env": {"-u", "-C", "-S", "--chdir"}, "time": {"-f", "-o"}, "exec": {"-a"}, "nohup": set(), "sudo": {"-u", "-g", "-h", "-p", "-C", "-D", "-R", "-T", "-U", "--chdir"}, "builtin": set(), "timeout": {"-s", "-k"}, "nice": {"-n"}, "stdbuf": {"-i", "-o", "-e"}}  # wrapper -> options that take a value
SHELLS = {"bash", "sh", "zsh", "dash", "ksh", "csh", "tcsh", "fish", "eval"}
REEVAL_WORDS = SHELLS | {"shell", "source", "trap", "zstyle", "functions", "zsh_name", "zsh_argzero", "script", "tmux", "screen", "vi", "vim", "nvim", "zpty", "sched"}  # run a string, or a program that may be zsh, again (zpty, sched: fffa95968 final M2)
REEVAL_WORD = re.compile(r"(?<![\w.-])(?:" + "|".join(sorted(REEVAL_WORDS)) + r")(?:-?[0-9][0-9.]*)?(?![\w.-])|(?:^|[\s;&|(])\.(?=\s)")  # (text already `plain`) a shell, eval, source or `.`, trap … anywhere
REEVAL = re.compile(REEVAL_WORD.pattern + r"|\$[{(]?[A-Za-z0-9_]|[*?\[]")  # … or a variable, a substitution or a wildcard that may spell a program: the line is read again without quotes (fbd9bbb63 round 1 M2, final M3)
ZSH_MECHANISM = re.compile(r"\\[0-9xuUc]|\$'|(?<!\w)(?:print|echo)(?:\s+-\w+)*\s+-\w*[ebfp]|(?<!\w)printf(?!\w).*%b|(?<!\w)(?:setopt|unsetopt|emulate|zmodload|autoload)(?!\w)|(?<!\w)set(?:\s+[-+]\w*)*\s+[-+]\w*o|options\[|g_*l_*o_*b_*s_*u_*b_*s_*t|\$\{?[=^~#+]*~", re.S | re.I)  # (searched as written, with quotes removed, and with quotes and backslashes removed; a `-` before a word does not hide it: `${:-setopt}`, ae6567064 round 1 M1, M2); print/echo/printf decode escapes by default, so judge refuses any backslash as written when the word, a `$`, a backtick, a wildcard or a brace (which may spell the word) shows up with quotes and backslashes removed (6789c4a00 round 1 M1, fffa95968 round 1 M1, final M1); `o` in any of set's option clusters (6789c4a00 round 1 M1, M2) a character spelled as an escape, $'…', print/echo/printf reading escapes, zsh options or modules changed, a value globbed ($~x, ${^~x}): not decoded, the whole line counts (调度 10-27 晚)
ENV = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*=")
COMMENT_OR_HEREDOC = re.compile(r"(?:^|[^A-Za-z0-9_./:=@%+-])#|(?<!<)<<(?!<)")  # a `#` that may start a comment, a here-document: not mimicked, the whole line counts (intent 2026-10-26-140639)
VARIABLE = re.compile(r"\$\{([A-Za-z_][A-Za-z0-9_]*)\}|\$([A-Za-z_][A-Za-z0-9_]*)")
SUBSTITUTION = re.compile(r"\$\(([^()]*)\)")
SHORT_C = re.compile(r"-[A-Za-z]*c[A-Za-z]*")
ECHOES = {"echo", "printf"}
Command = collections.namedtuple("Command", "argv env unresolved redirected piped chdir", defaults=(False, False))  # chdir: a wrapper option (`env -C`, `sudo -D`) changes its directory
NOT_PURE = re.compile(r"[;&|\n\r`$(){}]|(?:^|\s)[A-Za-z_][A-Za-z0-9_]*(?:\[[^\]]*\])?\+?=|(?<![\w.-])(?:eval|alias|function|functions|hash|enable|aliases|commands)(?![\w.-])")  # a separator, a substitution, a group or function, an assignment, a word that renames: no tool is exempt


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
    return QUOTES.sub("", unquote_dollar(CONTINUATION.sub(r"\1", command))).lower() if isinstance(command, str) else ""


def neutralize(text, backticks=True):  # `$`/backtick inside single quotes -> private characters; a backtick substitution outside them -> `$(...)` (when `backticks`). Comments are not mimicked (judge).
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
        else:
            out.append(char)
        i += 1
    return "".join(out)


def restore(token):
    return token.replace(PUA_DOLLAR, "$").replace(PUA_TICK, "`")


def name_of(token):  # Command name as the shell finds it: directory dropped, lower-cased (macOS file systems ignore case).
    return os.path.basename(token).lower()


def tokens_of(command, structured=False):
    """Shell tokens, line by line (an open quote continues; `#` and here-documents are not read as the shell does: judge refuses such lines that may concern production). `structured`: backticks are `$(...)`, `$` in single quotes neutralised;
    otherwise the older reading of the other hooks (a backtick starts a new command)."""
    tokens, pending = [], ""
    lines = CONTINUATION.sub(r"\1", unquote_dollar(command)).split("\n")  # a backslash-newline is deleted, also inside a word
    if structured:
        lines = neutralize("\n".join(lines)).split("\n")
    for line in lines:
        pending = pending + "\n" + line if pending else line
        lexer = shlex.shlex(pending if structured else pending.replace("`", " ; "), posix=True, punctuation_chars=True)
        lexer.whitespace_split, lexer.commenters = True, ""
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


def strip_prefix(seg, variables, chdir=None):  # (env of leading `NAME=value` words and wrapper options, rest of the segment): keywords and wrappers (env, nohup, sudo, timeout …) dropped, `env -S` split; a directory option of theirs noted in `chdir`.
    env, i, moved, chdir = {}, 0, [], [] if chdir is None else chdir
    while i < len(seg):
        tok = seg[i]
        if ENV.match(tok):
            name, _, value = tok.partition("=")
            env[name] = expand(restore(value), variables)[0]
            i += 1
        elif tok in KEYWORDS:
            i += 1
        elif REDIRECTION.fullmatch(tok) or (tok.isdigit() and i + 1 < len(seg) and REDIRECTION.fullmatch(seg[i + 1])):
            step = 1 if tok.isdigit() else 2  # a redirection before the program (`>o ./deploy`): moved behind it, as bash reads it anywhere; its target still parsed (82f149d10 final M1)
            moved, i = moved + seg[i:i + step], i + step
        elif tok.lower() in WRAPPERS:  # a bare name only: a wrapper spelled by a path (`/usr/bin/env`, `./nohup`) is refused by judge, not read
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
                if wrapper in ("env", "sudo") and (seg[i].startswith("--chdir") or (not seg[i].startswith("--") and ("C" if wrapper == "env" else "D") in seg[i][1:].split("=")[0])):
                    chdir.append(True)  # `-C dir`, `-iC dir`, `--chdir=dir` (sudo: -D); where it leads is not worked out (intent 2026-10-26-164931)
                i += 2 if seg[i] in valued else 1
        else:
            break
    return env, seg[i:], moved


def simple_commands(command, depth=0, variables=None, exported=None, assigned=None):
    """Simple commands of a command line as Command(argv, env, unresolved, redirected, piped), in order. `NAME=value` earlier in the line (and `export`) is substituted into later
    words; `$(...)`, backticks, `<(...)`, `bash -c`/`-lc`/`eval` bodies, here-strings and `echo … | bash` fed to a shell are commands of their own
    (here-documents are not read: judge refuses such a line that may concern production). `unresolved`: a word keeps a `$` expansion or substitution. `assigned` collects the expanded values of bare assignments. Untokenisable input, or nesting deeper than 3, raises ValueError."""
    if depth > 3:
        raise ValueError("nested too deeply")
    variables = {} if variables is None else variables
    exported = {} if exported is None else exported
    assigned = [] if assigned is None else assigned
    out, segments, cur, piped_next = [], [], [], False
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
        env, rest, moved = strip_prefix(seg, variables, chdir := [])
        if not rest:
            variables.update(env)
            assigned += [*env.values(), *(expand(restore(t), variables)[0] for t in moved)]  # a bare `NAME=value` stays in effect for the following commands, a redirection beside it too (58d4d5d4d final M2)
            exported.update({k: v for k, v in env.items() if allexport or k in ENV_TARGET_VARS})  # exported under `set -a`; a target variable may already be exported by the environment or an rc file (O2 round 1 M1)
            for inner in (i for t in moved for i in SUBSTITUTION.findall(t)):
                out += simple_commands(restore(inner), depth + 1, dict(variables), dict(exported), assigned)
            continue
        rest = rest + moved
        unresolved, argv = False, []
        for token in rest:
            for inner in SUBSTITUTION.findall(token):
                out += simple_commands(restore(inner), depth + 1, dict(variables), dict(exported), assigned)
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
        out.append(Command(argv, {**exported, **env}, unresolved, any(REDIRECTION.fullmatch(t) for t in argv), piped, bool(chdir)))
        if name in SHELLS:
            bodies = []
            if name == "eval":
                bodies = [" ".join(argv[1:])] if len(argv) > 1 else []
            else:
                body_at = next((n for n, t in enumerate(argv[1:], 1) if SHORT_C.fullmatch(t)), None)
                if body_at is not None and body_at + 1 < len(argv):
                    bodies = [argv[body_at + 1]]
                if "<<<" in argv and argv.index("<<<") + 1 < len(argv):
                    bodies.append(argv[argv.index("<<<") + 1])
                if piped and previous is not None and name_of(previous[0]) in ECHOES:
                    bodies.append(" ".join(previous[1:]))
            for body in bodies:
                if body.strip():
                    out += simple_commands(body, depth + 1, dict(variables), dict(exported), assigned)
        previous = argv
    return out


HERE = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(os.path.dirname(HERE))
if not os.path.exists(os.path.join(REPO_ROOT, ".git")):  # installed copy (install.sh, D26): the session's repository is CLAUDE_PROJECT_DIR, never the cwd (O7 final M3); none: os.devnull, git reads fail, no script is trusted
    REPO_ROOT = os.environ.get("CLAUDE_PROJECT_DIR") or os.devnull
ANCHOR, ROOTS = REPO_ROOT, ()  # whose origin/main says which scripts are trusted, (hook cwd, CLAUDE_PROJECT_DIR, process cwd) whose tables are unioned in; decide() sets both per call
DOCS_ROOT = "docs" + "/" + "modernization"  # built, not spelled: relevant_change.py scans sources for literal docs paths
EVIDENCE_ROOT = "scripts/evidence/"  # the one other prefix a trusted entry may live under (二乙 #6 O22a), resolved against the repository root
TABLE = os.environ.get("PROD_TARGETS_FILE") or str(Path(HERE) / "prod_targets.json")  # unioned with origin/main's all the same (load_table)
MAIN_TABLE_PATH = "scripts/gates/prod_targets.json"  # on origin/main since #693 (the old scripts/hooks/ copy is gone: b1 8.1 C7)
SQL_TOOLS = {"psql", "mysql", "mariadb", "sqlcmd", "osql", "mssql-cli", "mongosh", "mongo", "redis-cli", "usql", "tsql", "isql"}
WRITE_ONLY_TOOLS = {"sqlpackage", "sftp"}
HTTP_TOOLS = {"curl", "wget", "http", "https", "xh", "xhs"}
REMOTE_TOOLS = SQL_TOOLS | WRITE_ONLY_TOOLS | HTTP_TOOLS | {"bcp", "aliyun", "ssh", "scp", "rsync", "pg_dump", "pg_dumpall", "pg_restore", "mysqldump"}  # built in; remote_entries.tools on origin/main adds to it
LONG_REMOTE = {t for t in REMOTE_TOOLS if len(t) > 2}  # matched as substrings of an unreadable line; two-letter names (xh) only as whole words (SHORT_REMOTE, REMOTE_WORD): `xh` is inside exhibit, xhr…
SHORT_REMOTE = re.compile(r"(?<![a-z0-9_])(?:" + "|".join(sorted(re.escape(t) for t in REMOTE_TOOLS - LONG_REMOTE)) + r")(?![a-z0-9_-])")
INTERPRETERS = {"python", "node", "perl", "ruby", "php", "lua", "bash", "sh", "zsh", "dash", "ksh", "csh", "tcsh", "fish", "source", ".", "awk", "gawk", "nawk", "make", "gmake", "deno", "bun", "tclsh", "wish", "expect",
                "osascript", "swift", "rscript", "r", "julia", "m4", "gm4", "bm4", "at", "batch", "sqlite3", "irb", "erb", "gnumake", "bmake", "bsdmake", "groovy", "pwsh", "elixir", "escript", "raku", "guile", "jshell", "kotlin", "scala", "ts-node", "tsx"}  # "python" stands for python3, python3.12 …
RUNS_OTHERS = {"xargs", "find", "script", "parallel", "export", "source", "watch", "uv", "uvx", "npx", "pipx", "poetry", "bundle", "go", "npm", "pnpm", "yarn", "cargo", "dotnet", "java"}  # commands that run another command or carry its environment
INTERPRETER = re.compile(r"^(?:python|pypy|" + "|".join(re.escape(n) for n in sorted(INTERPRETERS - {"python"})) + r")(?:-?[0-9][0-9.]*)?$")  # versioned names too: python3.12, perl5.34, tclsh8.6
REMOTE_WORD = re.compile(r"(?<![A-Za-z0-9_])(?:(?:python|pypy)[0-9.]*|" + "|".join(re.escape(n) for n in sorted((REMOTE_TOOLS | SHELLS | INTERPRETERS | RUNS_OTHERS) - {"python", "."}))
                         + r")(?:-?[0-9][0-9.]*)?(?![A-Za-z0-9_-])|(?:^|[\s;&|(])\.\s")  # the pre-filter's names come from these sets only, plus `. file`
SHELL_HEADS = {"for", "case", "select", "function", "in"}  # `for f in a b` is a loop head; substitutions inside it are commands of their own

ENV_TARGET_VARS = ("PGHOST", "PGSERVICE", "PGDATABASE", "PGUSER", "PGHOSTADDR", "SQLCMDSERVER", "SQLCMDDBNAME", "SQLCMDUSER", "MYSQL_HOST", "DATABASE_URL", "MONGODB_URI", "REDIS_URL", "ALIBABA_CLOUD_PROFILE")
MAX_SCRIPT_FILE, MAX_RUN_FILE, CHUNK = 262144, 1 << 29, 1 << 23  # a file run is read quote-free up to the first, by bytes (in chunks) up to the second
DIR_OPTION = re.compile(r"--(?:chdir|directory|dir|cwd|prefix|working-directory|manifest-path|project)(?:=|$)|-[A-Za-z]*C[A-Za-z]*$")  # a runner\'s own option to work in another directory (make -C, pnpm -C, npm --prefix, uv --directory …)
MAKEFILES = ("GNUmakefile", "makefile", "Makefile")
CHDIR = {"cd", "pushd", "popd"}
CONFIG_VARS = {"GIT_EXEC_PATH", "GIT_EXTERNAL_DIFF", "GIT_PAGER", "PAGER", "GIT_EDITOR", "EDITOR", "VISUAL", "GIT_SSH", "GIT_SSH_COMMAND", "GIT_ASKPASS", "GIT_SEQUENCE_EDITOR", "RIPGREP_CONFIG_PATH"}  # where git and rg take a program from
COMMAND_TOOLS = ("Bash", "Monitor", "mcp__terminal__run_in_terminal")  # tools whose tool_input.command runs in a shell (intent 2026-10-24-160437); settings.json's matcher and pre-filter name these and LAUNCH_TOOL
LAUNCH_TOOL = "mcp__Claude_Browser__preview_start"  # starts runtimeExecutable + runtimeArgs of the named .claude/launch.json configuration (cfcbcf473 final M4)
LOADABLE, MAX_CLOSURE = (".py", ".pyc", ".so", ".pyd"), 400  # what Python imports from a directory (any interpreter: mod.cpython-312-darwin.so); files a trusted script may load before it counts as unreadable
TRUSTED_LINE = None  # judge(): the argv of the line when it is exactly a registered entry (trusted_entry), else None
SHELL_META = re.compile(r"[$`;|&\n(){}<>]")
REDIRECTION = re.compile(r"&?[<>]+[&|]?")
NET_REDIRECT = re.compile(r"/dev/(?:tcp|udp)/")
RAW_PATH = re.compile(r"(?:^|[\s;&|()<>`'\"=])[^\s;&|()<>`'\"=]*/[^\s;&|()<>]")  # any word with a slash: ./run, scripts/deploy, ~/x, /usr/bin/x, anywhere in the line (only for a line with a comment or here-document)
PATH_COMMAND = re.compile(r"(?:^|[;&|(\n])\s*(?:(?:\w+=\S*|\d*&?[<>]+[&|]?\s*[^\s;&|()]+)\s+)*[^\s;&|()]*/")  # a command word with a slash: a script run by its path


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
        run = subprocess.run(["git", "-C", root or REPO_ROOT, "cat-file", "--batch"], input=f"refs/remotes/origin/main:{MAIN_TABLE_PATH}\n".encode(), capture_output=True, timeout=10)
    except (OSError, subprocess.SubprocessError) as error:
        return None, f"{error.__class__.__name__}: {error}"
    if run.returncode != 0:
        return None, run.stderr.decode("utf-8", "replace").strip()[:200] or f"git 退出码 {run.returncode}"
    data = run.stdout  # "<id> blob <size>\n<content>\n", or "<name> missing\n" (no failure)
    end = data.find(b"\n")
    if end < 0:
        return None, "git cat-file 输出不完整"
    head = data[:end].split()
    return (data[end + 1:end + 1 + int(head[2])].decode("utf-8", "replace"), None) if len(head) == 3 and head[1] == b"blob" and head[2].isdigit() else (None, None)


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


def trusted_entries():  # Entries of origin/main's table section trusted_entries (the local table never counts: a branch cannot register one), with their slot patterns.
    try:
        found = json.loads(main_table_text(ANCHOR)[0] or "{}").get("trusted_entries", {})
        slots = {k: re.compile(v) for k, v in found.get("slots", {}).items()}
        return [e for e in found.get("entries", []) if isinstance(e, dict) and isinstance(e.get("script"), str) and ".." not in e["script"]], slots
    except (ValueError, AttributeError, TypeError, re.error):
        return [], {}


def form_matches(words, form, slots):  # True when each word is the form's literal, or its `{slot}` / `--opt={slot}` with a value the slot's pattern fully matches.
    def one(word, part):
        head, _, slot = part.partition("{")
        return word == part if not slot else slot.endswith("}") and slot[:-1] in slots and word.startswith(head) and bool(slots[slot[:-1]].fullmatch(word[len(head):]))
    return len(words) == len(form) and all(one(w, p) for w, p in zip(words, form))


def remote_table():  # (remote tool names, entries, slot patterns) of origin/main's table section remote_entries; the local table never counts (intent 2026-10-26-160810).
    global ANCHOR
    ANCHOR = ANCHOR or trust_root(ROOTS[0]) or os.devnull
    try:
        found = json.loads(main_table_text(ANCHOR)[0] or "{}").get("remote_entries", {})
        tools = {t.lower() for t in found.get("tools", []) if isinstance(t, str) and re.fullmatch(r"[a-z0-9_.+-]+", t.lower())}
        slots = {k: re.compile(v) for k, v in found.get("slots", {}).items()}
        return REMOTE_TOOLS | tools, [e for e in found.get("entries", []) if isinstance(e, dict) and isinstance(e.get("tool"), str)], slots
    except (ValueError, AttributeError, TypeError, re.error):
        return set(REMOTE_TOOLS), [], {}


def no_exec_tools():  # (programs known not to run a file named on their command line, git sub-commands that run none, {program: its short options that take the next word}) from origin/main's table section no_exec_tools (the local table never counts: a branch cannot widen it); empty when absent.
    global ANCHOR
    ANCHOR = ANCHOR or trust_root(ROOTS[0]) or os.devnull
    try:
        found = json.loads(main_table_text(ANCHOR)[0] or "{}").get("no_exec_tools", {})
        valued = {t: v for t, v in found.get("short_valued", {}).items() if isinstance(t, str) and isinstance(v, str) and re.fullmatch(r"[A-Za-z]*", v)}
        return (*({t for t in found.get(k, []) if isinstance(t, str) and re.fullmatch(r"[a-z0-9_.+-]+", t)} for k in ("tools", "git_subcommands")), valued)
    except (ValueError, AttributeError, TypeError):
        return set(), set(), {}


def option_value(tool, prev, valued):  # True when the word after `prev` may be its value: any option, unless the table names the tool's short options that take one (a long option without `=` still may)
    if not prev.startswith("-") or prev == "--":
        return False
    return tool not in valued or (prev.startswith("--") and "=" not in prev) or bool(re.fullmatch(r"-[A-Za-z]+", prev) and prev[-1] in valued[tool])


def git_sub(argv):  # git's sub-command when only known global options come before it (`-C dir`, `-P`, `--no-pager`); None after any other, whose value it may take (--attr-source …): not exempt
    i = 1
    while i < len(argv) and argv[i] in ("-C", "-P", "--no-pager"):
        i += 2 if argv[i] == "-C" else 1
    return argv[i] if i < len(argv) else None  # an unknown option is returned as it is: no sub-command of the list


def remote_named(text, tools):  # A remote tool's name as a word anywhere in the raw or the quote-free line; `http://` and the like are URLs, not httpie.
    names = sorted(map(re.escape, tools - {"http", "https"}), key=len, reverse=True)
    pattern = re.compile(r"(?<![a-z0-9_])(?:" + "|".join(names) + r")(?![a-z0-9_])|(?<![a-z0-9_])https?(?![a-z0-9_]|:/)|" + NET_REDIRECT.pattern)  # only the URL schemes are excused; bash network redirections count
    return bool(pattern.search(text.lower()) or pattern.search(plain(text)))


def remote_entry(text, entries, slots):  # True when the whole line is exactly one registered form of a remote tool, named bare, with no shell syntax.
    try:
        argv = None if SHELL_META.search(text) or re.search(r"[#\r\n\\]", text) else shlex.split(text)
    except ValueError:
        return False
    return bool(argv) and any(argv[0] == e["tool"] and any(isinstance(f, list) and form_matches(argv[1:], f, slots) for f in e.get("forms", [])) for e in entries)


def may_run_or_expand(text):  # (text already `plain`) a program that runs others, a remote tool's name as a substring, an expansion or a path command: the line may reach production unseen
    return bool(REMOTE_WORD.search(text) or any(t in text for t in LONG_REMOTE) or "$" in text or "`" in text or "{" in text or PATH_COMMAND.search(text) or NET_REDIRECT.search(text))


def glob_qualifier(text, raw=False):  # True when an unquoted `(` may end a word, or opens `(#`: a zsh glob qualifier (`*(e:…:)`, `a1(e:…:)`) runs code while expanding; bash's extglob, a function definition and an array look the same and count too. Allowed only after an unescaped space, tab or newline (zsh splits on no other), a `$` that is not `$$`, `(`, `|`, `&`, `;`, or a `<`/`>` that itself starts a word (process substitution): anything else, escaped included, counts (40df9f9a5 round 1, final M1, M2; fbd9bbb63 round 1, final M1). Quotes are followed only while certain: a double-quoted part holding `$` or a backtick ends that, the rest is read raw. `raw` (the text may be evaluated again): no quotes followed, a backslash before `(` dropped. Nothing is decoded: judge refuses a ZSH_MECHANISM line as it stands (调度 10-27 晚).
    text = re.sub(r"\\+\(", "(", text) if raw else text
    i, n, follow, esc = 0, len(text), not raw, -9
    while i < n:
        c = text[i]
        if c == "\\":
            i, esc = i + 2, i + 1
            continue
        if follow and c in "'\"":  # `'…'` literal; `"…"` ends at an unescaped quote
            j = i + 1
            while j < n and text[j] != c:
                j += 2 if text[j] == "\\" and c != "'" else 1
            if j < n and (c != '"' or not re.search(r"[$`]", text[i + 1:j])):
                i = j + 1
                continue
            follow = False
        elif c == "(" and (text.startswith("(#", i) or i and (esc == i - 1 or text[i - 1] not in " \t\n$(|&;<>" or text[i - 2:i] == "$$" and esc != i - 2
                                                              or text[i - 1] in "<>" and (i > 1 and text[i - 2] not in " \t\n(|&;" or esc == i - 2))):
            return True
        i += 1
    return False


def could_concern(text):  # Pre-filter (text already `plain`): False only when the gate would certainly allow. Any word may name a file a program runs (A′, intent 2026-10-26-164931): only an empty line is skipped.
    return bool(text.strip())


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


def touches(command, patterns):  # A pattern in the words or environment of one command after variables and braces are expanded.
    return text_hits(command.argv + list(command.env.values()), patterns)


def file_hits(full, patterns):  # True when a file mentions production: quote-free text up to MAX_SCRIPT_FILE, bytes (in chunks) up to MAX_RUN_FILE; unreadable or larger counts.
    wanted, keep = [p.encode() for p in patterns], max(map(len, patterns), default=1)
    try:
        if os.path.getsize(full) > MAX_RUN_FILE:
            return True
        with open(full, "rb") as f:
            head, tail = f.read(MAX_SCRIPT_FILE + 1), b""
            if len(head) <= MAX_SCRIPT_FILE and any(p in plain(head.decode("utf-8", "replace")) for p in patterns):
                return True
            chunk = head
            while chunk:
                window = (tail + chunk).lower()
                if any(w in window for w in wanted):
                    return True
                tail, chunk = window[-keep:], f.read(CHUNK)
    except OSError:
        return True
    return False


def batch(args, lines):  # Output lines of one git process fed `lines` on standard input (one per line), or None when it fails or a line holds a newline.
    if any("\n" in line for line in lines):
        return None
    try:
        run = subprocess.run(["git", "-C", ANCHOR, *args], input="".join(line + "\n" for line in lines), capture_output=True, text=True, timeout=30)
    except (OSError, subprocess.SubprocessError):
        return None
    out = run.stdout.split("\n")[:len(lines)] if run.returncode == 0 else []
    return out if len(out) == len(lines) else None


def main_blobs(rels):  # {path: object id on origin/main} for repo-relative paths, missing ones left out; read by the full ref name.
    out = batch(["cat-file", "--batch-check"], [f"refs/remotes/origin/main:{rel}" for rel in rels]) or []
    return {rel: line.split()[0] for rel, line in zip(rels, out) if len(line.split()) == 3 and line.split()[1] == "blob"}


def rel_of(path):  # Path relative to the anchor: its directory resolved, the file itself not (a symlink is no file of the repository).
    return os.path.relpath(os.path.join(os.path.realpath(os.path.dirname(os.path.join(os.getcwd(), path))), os.path.basename(path)), os.path.realpath(ANCHOR))  # realpath resolves a link before its `..`, as the kernel does


def all_as_on_main(paths):  # True when every path is a regular file byte for byte origin/main's file at that path (edited, staged, new, symlinked or outside the anchor are not).
    rels = [rel_of(p) for p in paths]
    if any(r.startswith("..") or os.path.islink(p) or not os.path.isfile(p) for r, p in zip(rels, paths)):
        return False
    theirs, mine = main_blobs(rels), batch(["hash-object", "--no-filters", "--stdin-paths"], [os.path.realpath(p) for p in paths])
    return mine is not None and all(theirs.get(r) == m for r, m in zip(rels, mine))


def path_tails(nodes):  # Relative paths a file spells in literals: the trailing literal arguments of os.path.join (BASE, "adr", "x.py"), the trailing literals of a `/` chain, a literal ending in .py; every suffix of each.
    def parts(node):
        if isinstance(node, ast.BinOp) and isinstance(node.op, ast.Div):
            return parts(node.left) + parts(node.right)
        return [node.value if isinstance(node, ast.Constant) and isinstance(node.value, str) else None]
    joins = [n for n in nodes if isinstance(n, ast.Call) and getattr(n.func, "attr", getattr(n.func, "id", "")) == "join"]
    runs = [sum((parts(a) for a in n.args), []) for n in joins] + [parts(n) for n in nodes if isinstance(n, ast.BinOp) and isinstance(n.op, ast.Div)]
    runs += [[n.value] for n in nodes if isinstance(n, ast.Constant) and isinstance(n.value, str) and n.value.endswith(".py")]
    starts = [(run, next(i for i in range(len(run), -1, -1) if i == 0 or run[i - 1] is None)) for run in runs]
    tails = {os.path.join(*run[i:]) for run, start in starts for i in range(start, len(run))}  # every suffix: a leading literal may name a directory the script stands in
    return {t for t in tails if t != "." and len(t) < 256 and not set(t) & {"\0", "\n"}}


def imported(path, listing):  # Repository files a Python file can load: what its imports name (on its directory, each one above it up to the anchor, and any directory it spells: any may be first on sys.path), the .py files it spells by path (it may run them); None when it cannot be parsed.
    try:
        nodes = list(ast.walk(ast.parse(Path(path).read_bytes())))
    except (OSError, SyntaxError, ValueError, RecursionError):
        return None
    top, here, roots = os.path.realpath(ANCHOR), os.path.realpath(os.path.dirname(path)), []
    while os.path.commonpath([here, top]) == top and here not in roots:
        roots.append(here)
        here = os.path.dirname(here)
    inside = lambda c: os.path.commonpath([os.path.realpath(c), top]) == top  # noqa: E731
    spelled = {os.path.normpath(os.path.join(root, t)) for t in path_tails(nodes) for root in roots}
    found = [c for c in spelled if c.endswith(".py") and os.path.isfile(c) and inside(c)]
    extra = [c for c in spelled if os.path.isdir(c) and inside(c)]  # sys.path.insert(0, str(HERE.parents[3] / "runbooks" / "scripts"))
    names = [(a.name.split("."), roots + extra) for n in nodes if isinstance(n, ast.Import) for a in n.names]
    for n in (n for n in nodes if isinstance(n, ast.ImportFrom)):  # `from a import b`: b may be a module; a relative import starts `level` directories up
        where = [os.path.realpath(os.path.join(os.path.dirname(path), *[".."] * (n.level - 1)))] if n.level else roots + extra
        names += [((n.module or "").split(".") * bool(n.module) + a.name.split("."), where) for a in n.names]
    stems = {os.path.join(root, *parts[:i]) for parts, where in names for root in where for i in range(1, len(parts) + 1)}
    for stem in stems:  # a module or package: any loader suffix, __init__ included
        for where, name in ((os.path.dirname(stem), os.path.basename(stem)), (stem, "__init__")):
            if where not in listing:
                listing[where] = os.listdir(where) if os.path.isdir(where) else []
            found += [os.path.join(where, f) for f in listing[where] if f.split(".", 1)[0] == name and "." in f and f.endswith(LOADABLE)]
    return found


def closure(script, runs=()):  # Files running `script` may load: what its imports name and the .py files it spells by path, recursively (new_intent runs its builders, build_records runs adr/build_adrs.py), and, compared but not followed, everything importable beside each of them (sys.path[0]: the standard library's own imports may pick it up); `runs`: files it runs by a name built at run time, followed as it is; None when one cannot be read.
    listing, todo, followed, found = {}, [os.path.abspath(p) for p in (script, *runs)], set(), set()
    while todo:
        path = todo.pop()
        if path in followed:
            continue
        followed.add(path)
        loads = imported(path, listing) if os.path.isfile(path) else []  # a missing one fails the comparison with origin/main
        if loads is None or len(followed) > MAX_CLOSURE:
            return None
        found.update(loads)
        todo += [f for f in loads if f.endswith(".py")]  # .pyc and extension modules are compared, not read
    for here in {os.path.dirname(p) for p in followed}:
        if here not in listing:
            listing[here] = os.listdir(here) if os.path.isdir(here) else []
        found.update(os.path.join(here, n) for n in listing[here] if n.endswith(LOADABLE))
    return followed | found if bytecode_checked(followed | found) else None


def bytecode_checked(sources):  # False when Python could run a byte-code cache of a source without checking it: a hash-based .pyc that skips the source check (flags 0b01), beside it or under sys.pycache_prefix. A timestamp .pyc forged to the source's mtime and size is not caught (known gap).
    for src in sources:
        cache, stem = os.path.join(os.path.dirname(src), "__pycache__"), os.path.splitext(os.path.basename(src))[0]
        pycs = [os.path.join(cache, n) for n in (os.listdir(cache) if os.path.isdir(cache) else []) if n.startswith(stem + ".") and n.endswith(".pyc")]
        pycs += [importlib.util.cache_from_source(src)] if sys.pycache_prefix and src.endswith(".py") else []
        for pyc in (p for p in pycs if os.path.isfile(p)):
            try:
                with open(pyc, "rb") as f:
                    flags = int.from_bytes(f.read(8)[4:8], "little")
            except OSError:
                return False
            if flags & 1 and not flags & 2:
                return False
    return True


def tree_files(rel_dir):  # Every file under a directory of the anchor (__pycache__ skipped), or None when it holds a symlink.
    files = []
    for top, dirs, names in os.walk(os.path.join(ANCHOR, rel_dir)):
        if any(os.path.islink(os.path.join(top, n)) for n in dirs + names):
            return None
        dirs[:] = [d for d in dirs if d != "__pycache__"]
        files += [os.path.join(top, n) for n in names]
    return files


def entry_script(rel, root):  # A registered script's path: under the docs root, or, spelled with the literal prefix scripts/evidence/, under the repository root while its real path stays inside that directory (二乙 #6); None when it leaves it.
    if not rel.startswith(EVIDENCE_ROOT):
        return os.path.join(root, DOCS_ROOT, rel)
    path, inside = os.path.join(root, rel), os.path.join(os.path.realpath(root), EVIDENCE_ROOT.rstrip("/"))  # the directory itself, not where a link named so points
    return path if ".." not in rel and os.path.dirname(os.path.realpath(path)) == inside else None  # directly in it: no `..`, no subdirectory, no link out


def entry_closure(rel, script):  # closure() of a registered script; one under scripts/evidence/ runs the sheet_*.py beside it by name, so those and what they load are followed too (b39e3785 first round, final M1)
    here = os.path.dirname(script)
    return closure(script, sorted(os.path.join(here, n) for n in os.listdir(here) if n.startswith("sheet_") and n.endswith(".py")) if rel.startswith(EVIDENCE_ROOT) and os.path.isdir(here) else ())


def trusted_entry(text, cwd):  # The argv of a line that is exactly a registered entry (interpreter, script, one of its forms) whose script, closure and read directories are origin/main's, else None.
    try:
        argv = None if SHELL_META.search(text) or re.search(r"[#\r\\]", text) else shlex.split(text)
    except ValueError:
        return None
    global ANCHOR
    ANCHOR, words = ANCHOR or trust_root(ROOTS[0]) or os.devnull, argv or []  # never expanded: a quoted `~` is a directory named ~, an exported HOME another home
    entries, slots = trusted_entries()
    for entry in entries:
        script, n = entry_script(entry["script"], ANCHOR), len(entry.get("interpreter", []))  # the table's `~/` is this process's home; the line spells the absolute path
        if not script or not n or len(words) <= n or words[:n] != [os.path.expanduser(w) for w in entry["interpreter"]] or os.path.islink(os.path.join(cwd or os.getcwd(), words[n])) or rel_of(os.path.join(cwd or os.getcwd(), words[n])) != rel_of(script):  # a link to the script runs beside the link
            continue
        read = [tree_files(DOCS_ROOT + "/" + d) for d in entry.get("trust_dirs", []) if isinstance(d, str) and ".." not in d]
        loaded = entry_closure(entry["script"], script) if any(form_matches(words[n + 1:], f, slots) for f in entry.get("forms", [])) and None not in read else None
        if loaded is not None and all_as_on_main([script] + sorted(loaded) + [f for files in read for f in files]):
            return argv
    return None


def trusted_script(argv, cwd):  # The command is the line judge() found to be a registered entry.
    return TRUSTED_LINE is not None and argv == TRUSTED_LINE


def body_is_judged_separately(command):  # A shell whose program is a `-c` string, an eval argument or a here-string: that body is a command of its own in the list.
    tool, argv = name_of(command.argv[0]), command.argv
    return tool in SHELLS and (tool == "eval" or any(SHORT_C.fullmatch(t) for t in argv[1:]) or "<<<" in argv)


def candidates(argv):  # Words of a command that may name a file it runs: each word, an option's attached value (`--require=x`, `-rx`), a Python module as a path, make's default makefiles.
    tool, out = name_of(argv[0]), [argv[0]] if "/" in argv[0] else []
    for word in argv[1:]:
        out += [word] + ([word.split("=", 1)[1]] if word.startswith("-") and "=" in word else [word[2:]] if re.match(r"-[A-Za-z]", word) and len(word) > 2 else [])
    if tool.startswith(("python", "pypy")):
        out += [w.replace(".", "/") + ".py" for w in list(out) if re.fullmatch(r"[A-Za-z_][\w.]*", w)] + [w.replace(".", "/") for w in list(out) if re.fullmatch(r"[A-Za-z_][\w.]*", w)]
    return out + list(MAKEFILES) * tool.endswith("make")


def runs_file(command, patterns, cwd, safe=(frozenset(), frozenset(), {})):  # The command may run a file that mentions production: every existing regular file among its words (intent 2026-10-26-164931, A′), in the hook's directory; unreadable counts.
    argv, tool = command.argv, name_of(command.argv[0])  # a tool of `safe` (no_exec_tools) skips its positional words only: an option's value, a word inside one (`-c core.pager=…`), an environment value are still read
    words = candidates(argv) + list(command.env.values())  # an environment value may name a program the command runs (GIT_EXTERNAL_DIFF=…)
    if tool in safe[0] and "/" not in argv[0] and (tool != "git" or git_sub(argv) in safe[1]):  # git: only its sub-commands that run none (bisect run, submodule foreach do)
        words = [w for i, w in enumerate(argv[1:], 1) if option_value(tool, argv[i - 1], safe[2])] + list(command.env.values())
        words += [w[2:] for w in argv[1:] if re.match(r"-[A-Za-z].", w)]  # an attached short option value: -Ocmd
        words += [t for w in argv[1:] + list(command.env.values()) for t in re.split(r"[\s=!'\"]+", w) if t and t != w]
    for word in map(os.path.expanduser, words):  # `~/x` as the shell expands it unquoted (a quoted one is checked too: fail closed)
        full = os.path.join(cwd or os.getcwd(), word)  # no directory change on the line (judge refuses those): the hook's directory
        if os.path.isdir(full):
            if INTERPRETER.match(tool):  # python runs a package's __main__, make -C a directory's makefile
                return True
        elif os.path.isfile(full) and file_hits(full, patterns):  # a regular file only: a device or a pipe (/dev/null, /dev/urandom) holds no program to read
            return True
    return False


def judge(command_text, patterns, problem=None, cwd=None, environ=None):  # Refusal text or None.
    global TRUSTED_LINE
    environ, TRUSTED_LINE = os.environ if environ is None else environ, None
    env_hit = bool(patterns) and any(text_hits([str(environ.get(v, ""))], patterns) for v in ENV_TARGET_VARS)
    raw_hit = bool(patterns) and text_hits([plain(command_text)], patterns)
    flat = plain(command_text)  # the shell is zsh: what a qualifier runs is not read, the whole line counts (调度 10-27)
    if any(ZSH_MECHANISM.search(re.sub(drop, "", command_text)) for drop in ("(?!)", "['\"]", r"['\"\\]")) or "\\" in command_text and re.search(r"(?<!\w)(?:print|echo|printf)(?!\w)|[$`*?\[{]", re.sub(r"['\"\\]", "", command_text), re.I) or glob_qualifier(command_text) or (REEVAL.search(flat) and glob_qualifier(command_text, raw=True)) or ((REEVAL_WORD.search(flat) or re.search(r"\$(?!\()|[*?\[]", command_text)) and re.search(r"\$\(|`", command_text)):  # a line that may be read again: quotes not followed (40df9f9a5 final M3); a substitution with a shell, eval … or with a variable or wildcard that may spell one (`$0`, `/bin/z?h`) is not read at all (fbd9bbb63 final M2, ae6567064 final M2)
        return ("拒绝：命令里有紧跟在词后面、没加引号的左括号，或「(#」、$~；或有字符转义（反斜杠加数字或 x、u、U、c）、$'…'、print/echo 带 -e/-b、printf 带转义；或去掉引号与反斜杠后有改 zsh 选项或模块的写法（setopt、unsetopt、emulate、set -o、set +o、zmodload、autoload、globsubst）。"
                "zsh 通配限定符（如 *(e:…:)、a1(e:…:)）在展开文件名时就运行代码，bash 的 extglob、函数定义、数组写法相同；行里有 shell、eval、source、trap、变量或通配符时引号里的也算，有 shell、eval 等又有命令替换整行拒。门不解码、不模仿 zsh 去读，见机制即拒（调度 10-27）。把括号放进单引号并去掉变量与通配，或在括号前留一个未转义的普通空格；挑文件改用 find 或先列出再逐个写。")
    odd = bool(COMMENT_OR_HEREDOC.search(command_text)) or "\\\n" in command_text  # a comment, a here-document or a continuation anywhere: not read as the shell does
    if COMMENT_OR_HEREDOC.search(command_text) and CONTINUATION.search(command_text):  # a continuation the shell ends at a comment's newline, the gate would join (82f149d10 final M2)
        return "拒绝：命令里同时有注释或 here-document 与续行，门读不准下一行从哪里开始，宁可误拦（intent 2026-10-26-140639）。去掉注释或续行再执行。"
    if odd and (raw_hit or env_hit or may_run_or_expand(plain(command_text)) or may_run_or_expand(command_text.lower()) or RAW_PATH.search(command_text) or RAW_PATH.search(plain(command_text))):  # anywhere in the text, not where a command starts (intent 2026-10-26-140639)
        return "拒绝：命令里有注释、here-document 或续行，门不模仿 shell 去读它们；这一行任意位置又有生产特征、远程工具、解释器、变量展开、命令替换、路径或指向生产的环境变量，宁可误拦（intent 2026-10-26-140639）。去掉注释与 here-document 再执行；提交说明、PR 正文先写进文件（放会话 scratchpad 或 state 目录，不进仓库），再用 git commit -F <文件>、gh pr create --body-file <文件>（intent 2026-10-26-145156）。"
    tools, entries, slots = remote_table()
    remote_ok = remote_entry(command_text, entries, slots)
    if remote_named(command_text, tools) and not remote_ok:
        return ("拒绝：命令里出现远程工具名（ssh、scp、rsync、psql、mysql、curl、aliyun 等），只认 origin/main 特征表 remote_entries 里登记的写法、整行逐字匹配；"
                "门不再解析远程工具的参数判断目标（intent 2026-10-26-160810）。连 dev 或测试机的写法请发卡登记；生产只读取证走已登记的取证入口（ADR-0037）。")
    assigned = []
    try:
        commands = simple_commands(command_text, assigned=assigned)
        TRUSTED_LINE = trusted_entry(command_text, cwd) if len(commands) == 1 and INTERPRETER.match(name_of(commands[0].argv[0])) and could_concern(plain(command_text)) else None
    except ValueError:
        if odd or raw_hit or env_hit or remote_named(command_text, tools) or SHORT_REMOTE.search(plain(command_text)) or any(t in plain(command_text) for t in tools if len(t) > 2):  # unreadable: a name as a substring too (sshd_conf)
            return "命令读不懂（引号未闭合、嵌套过深等），且含生产特征、远程工具或注释／here-document，无法确认不是连生产的写操作，拒绝。"
        return None
    if any(NET_REDIRECT.search(w) for w in assigned):  # a redirection alone, `exec >…`, `{ …; } >…`: no command word (af2b97c22 round 1 M1, final M1)
        return "拒绝：变量展开后出现 /dev/tcp 或 /dev/udp 网络重定向，按远程工具处理，不是登记写法（intent 2026-10-26-160810）。"
    if (raw_hit or env_hit or (patterns and text_hits(assigned, patterns))) and TRUSTED_LINE is None:
        return ("拒绝：命令里有生产特征，或指向生产的目标环境变量（命令里设的或会话环境里的），整行拒绝，不论哪个命令（intent 2026-10-26-165321）。CLAUDE.md 底线 4：不做生产写操作；"
                "生产只读取证走 ADR-0037 专用只读账号和已登记的取证入口（单独一条命令、按登记的写法运行）。会话环境里的目标变量请清掉后重开会话。")
    runner = lambda c: bool(INTERPRETER.match(name_of(c.argv[0])) or "/" in c.argv[0] or any(INTERPRETER.match(name_of(w)) or name_of(w) in RUNS_OTHERS for w in c.argv))  # noqa: E731
    moves = any(name_of(c.argv[0]) in CHDIR or (runner(c) and any(DIR_OPTION.match(w) for w in c.argv[1:])) for c in commands)
    if any(c.chdir for c in commands) or (moves and any(map(runner, commands))):  # a wrapper's directory option always runs a program there  # where a file runs from cannot be told: no working out of directories (intent 2026-10-26-164931, 165321)
        return ("拒绝：同一行里既换目录（cd、pushd、popd，env -C、sudo -D 或程序自己的目录选项），又运行解释器、shell、启动器、按路径的程序或会运行别的命令的程序；"
                "门不推算目录，宁可误拦（intent 2026-10-26-164931）。去掉换目录，用绝对路径或仓库相对路径直接运行。")
    if any("/" in c.argv[0] and name_of(c.argv[0]) in WRAPPERS for c in commands):  # what it runs, from where, cannot be told: not read (76b7f7e81 round 1 M1, final M1)
        return "拒绝：包装命令（env、sudo、nohup、timeout 等）写成路径，门不解析它会运行什么、在哪个目录，宁可误拦（intent 2026-10-26-164931）。改用裸命令名。"
    safe = no_exec_tools()
    if len(commands) != 1 or NOT_PURE.search(command_text) or tokens_of(command_text, structured=True)[0] != commands[0].argv[0]:  # exempt only a line that is one plain command, its first word the program: no wrapper, prefix, separator, assignment or renaming (intent 2026-10-26-164931)
        safe = (set(), set(), {})
    for command in commands:
        tool = name_of(command.argv[0])
        if any(NET_REDIRECT.search(w) for w in command.argv):  # a bash network redirection spelled by variables (a14c80797 final M1)
            return "拒绝：变量展开后出现 /dev/tcp 或 /dev/udp 网络重定向，按远程工具处理，不是登记写法（intent 2026-10-26-160810）。"
        if tool in ("git", "rg") and (any(w in ("-c", "--config-env") or w.startswith(("--exec-path", "--config-env=")) for w in command.argv[1:]) or any(k in CONFIG_VARS or k.startswith("GIT_CONFIG") for k in command.env)):
            return f"拒绝：{tool} 带了配置来源或执行路径（-c、--config-env、--exec-path，或 {', '.join(sorted(CONFIG_VARS))} 等环境变量），它会运行哪个程序门读不到，宁可误拦（intent 2026-10-26-164931）。"
        if tool in tools:  # spelled by variables or substitutions, or nested in a shell body: not the registered line
            if not remote_ok or patterns is None:
                return f"拒绝：{tool} 由变量、命令替换或嵌套拼出，或特征表读不了（{problem or '—'}），不是整行登记写法，一律拒（intent 2026-10-26-160810）。"
            if touches(command, patterns):
                return f"拒绝：登记的 {tool} 写法拼接后碰到生产特征（两条规则叠加，intent 2026-10-26-160810）。"
            continue
        if body_is_judged_separately(command) or tool in SHELL_HEADS:
            continue
        if patterns is None:
            if INTERPRETER.match(tool) and not trusted_script(command.argv, cwd):
                return f"{problem}：解释器命令（{tool}）无法判断是否碰到生产，一律拒绝；修好 scripts/gates/prod_targets.json 后再做。"
            continue
        if trusted_script(command.argv, cwd):
            continue
        if touches(command, patterns) or runs_file(command, patterns, cwd, safe):  # spelled by variables or braces, or the file it runs names production
            return (f"拒绝：{tool} 命令拼接后碰到生产特征，或它运行的文件含生产特征（intent 2026-10-26-165321）。CLAUDE.md 底线 4：不做生产写操作；生产只读取证走 ADR-0037 专用只读账号和已登记的取证入口。"
                    "可信脚本须单独一条命令、按登记的解释器运行。")
    return None


def trust_root(cwd):  # cwd's work tree when cwd and CLAUDE_PROJECT_DIR are in one repository (same real `--git-common-dir`; a worktree counts), else None: fail closed. Each side must print exactly two absolute paths (a newline in a path shifts them) and cwd must lie under the anchor.
    found = [(git("rev-parse", "--path-format=absolute", "--git-common-dir", "--show-toplevel", root=d) or "").split("\n") if d else [] for d in (cwd, os.environ.get("CLAUDE_PROJECT_DIR"))]
    sane = all(len(f) == 3 and f[2] == "" and all(os.path.isabs(p) for p in f[:2]) for f in found)
    return found[0][1] if sane and os.path.realpath(found[0][0]) == os.path.realpath(found[1][0]) and os.path.commonpath([os.path.realpath(cwd), os.path.realpath(found[0][1])]) == os.path.realpath(found[0][1]) else None


def command_of(payload):  # (shell command of a hook payload or None, refusal or None): Bash, Monitor and the terminal panel run tool_input.command; Monitor's ws source runs none.
    tool, tool_input = payload.get("tool_name", "Bash"), payload.get("tool_input")
    command = tool_input.get("command") if isinstance(tool_input, dict) else None
    if tool == LAUNCH_TOOL:
        return launch_command(tool_input, payload.get("cwd"))
    if tool not in COMMAND_TOOLS or (tool == "Monitor" and command is None and isinstance(tool_input, dict)):
        return None, None
    return (command, None) if isinstance(command, str) else (None, f"生产写门：{tool} 钩子输入里没有字符串形式的 command，无法判断，按拒绝处理。\n")


def launch_command(tool_input, cwd):  # (the command preview_start runs for its `name`, refusal or None); a `url` alone starts nothing. Built as the app builds it, from the session's launch.json.
    name = tool_input.get("name") if isinstance(tool_input, dict) else None
    if not name:
        return None, None if isinstance(tool_input, dict) else "生产写门：preview_start 的钩子输入不是对象，无法判断，按拒绝处理。\n"
    def line(root):  # runtimeExecutable runtimeArgs program args, else node program args; env before, its cwd as a `cd` first (a script is then not trusted)
        text = Path(root, ".claude", "launch.json").read_text(encoding="utf-8").replace("${workspaceFolder}", json.dumps(str(root))[1:-1])  # the app's substitution
        configs = json.loads(text)["configurations"]
        matching = [c for c in configs if str(c.get("name") or "").lower() == str(name).lower()] if all(isinstance(c, dict) and isinstance(c.get("name"), str) and c["name"] for c in configs) else []  # the app matches names ignoring case and derives one for an unnamed entry: then which runs cannot be seen
        entry = matching[0] if len(matching) == 1 and "${" not in json.dumps(matching[0]) else {}  # two candidates, or a variable left: which one runs cannot be seen
        run = [str(entry["runtimeExecutable"]), *map(str, entry.get("runtimeArgs") or [])] + ([str(entry["program"])] if entry.get("program") else []) if entry.get("runtimeExecutable") else ["node", str(entry["program"])]
        run += [str(a) for a in entry.get("args") or []]
        command = shlex.join([f"{k}={v}" for k, v in (entry.get("env") or {}).items()] + run)
        return f"cd {shlex.quote(str(entry['cwd']))} && {command}" if entry.get("cwd") else command
    roots = {os.environ.get("CLAUDE_PROJECT_DIR") or cwd or os.getcwd()} | ({cwd} if isinstance(cwd, str) and os.path.isfile(os.path.join(cwd, ".claude", "launch.json")) else set())
    try:
        lines = {line(root) for root in roots}  # the app reads the session's working directory; a second launch.json at the hook's cwd must agree
    except (OSError, ValueError, KeyError, TypeError, AttributeError, StopIteration):
        return None, f"生产写门：读不出 .claude/launch.json 里 {name} 的启动命令，无法判断，按拒绝处理。\n"
    return (lines.pop(), None) if len(lines) == 1 else (None, f"生产写门：会话目录与当前目录的 .claude/launch.json 里 {name} 的启动命令不一致，按拒绝处理。\n")


def decide(payload):  # (exit code, stdout, stderr) for one hook payload (a dict); the dispatcher calls this in-process.
    command, refusal = command_of(payload)
    if command is None:
        return (2, "", refusal) if refusal else (0, "", "")
    cwd = payload.get("cwd") if isinstance(payload.get("cwd"), str) and payload.get("cwd") else None
    where = payload["tool_input"].get("cwd") if payload.get("tool_name") == COMMAND_TOOLS[2] else None  # the terminal panel's own start directory
    if isinstance(where, str) and where:
        cwd = os.path.join(cwd or os.getcwd(), os.path.expanduser(where))
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
        from gates_selftest import prod_write_gate_test as self_test
        sys.exit(self_test())
    sys.exit(guarded_main())
