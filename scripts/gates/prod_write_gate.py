#!/usr/bin/env python3
"""生产写门（PreToolUse：Bash、Monitor、终端面板，跑 tool_input.command 的工具）：命令文本里见生产特征即拒（卡 #736，用户 10-28「见生产特征即拒，其余靠只读账号（推荐）」）。防底线 4（生产只读）；安全门，出错失败关闭（退出码 2）。

生产特征 = prod_targets.json 的 patterns（生产主机名或 IP、生产库名、生产凭据名；与 origin/main 上那份取并集，钩子调用时再并上 cwd 与 CLAUDE_PROJECT_DIR 所在仓库的），
出现在原文、去引号与反斜杠后的整行，或变量、export、花括号展开后的词与环境值里即拒，不分是什么程序（远程工具、解释器、构建工具、git、grep 都一样）；会话环境里的目标变量（PGHOST 等）指向生产也拒。
不按 shell 写法拒绝（换目录加运行、注释、here-document、括号、反斜杠、变量、通配、zsh 选项等一概不看），不维护远程工具名单。特征表缺失、不合格或并集为空：一律拒（失败关闭）。
唯一例外是阿里云命令行（卡 #736，用户「先不动，门补一条」）：本机默认 profile 是生产，命令里出现 aliyun 就拒，除非段首裸写 aliyun、参数里没有任何 shell 元字符、且显式给一个不在特征表的 --profile；
程序名不是字面写出（通配、花括号、会话环境变量、带参数的命令替换）而可能是 aliyun 的也拒（cloud_cli_refusal，#736 首轮 M2）。
已知不管、复审不据此判必改（用户 10-28 定，由「本机只有生产只读账号」兜底）：被运行的脚本文件里写着生产主机、解释器内联代码在运行时拼出目标、
用会话环境里的非目标变量、命令替换、base64、printf 转义等编码藏起来的主机名——命令文本上看不到生产特征的，门放行。
门出错时 .claude/settings.json 的挂载入口用同一张特征表做保守预筛（去引号与反斜杠后命中即拒，见 aliyun 即拒，读不到表一律拒）。
命令解析（原 command_words）也在本文件，其他钩子经 scripts/hooks/command_words.py 兼容层导入。自测在 gates_selftest.py 的 prod_write_gate 段（O8 合一）。
退出码 2 + 标准错误 = 拒绝；0 = 放行。用法：prod_write_gate.py  |  prod_write_gate.py --self-test
"""
import collections
import fnmatch
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
ENV = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*=")
VARIABLE = re.compile(r"\$\{([A-Za-z_][A-Za-z0-9_]*)\}|\$([A-Za-z_][A-Za-z0-9_]*)")
SUBSTITUTION = re.compile(r"\$\(([^()]*)\)")
SHORT_C = re.compile(r"-[A-Za-z]*c[A-Za-z]*")
ECHOES = {"echo", "printf"}
Command = collections.namedtuple("Command", "argv env unresolved redirected piped chdir", defaults=(False, False))  # chdir: a wrapper option (`env -C`, `sudo -D`) changes its directory


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
if not os.path.exists(os.path.join(REPO_ROOT, ".git")):  # installed copy (install.sh, D26): the session's repository is CLAUDE_PROJECT_DIR, never the cwd (O7 final M3); none: os.devnull, git reads fail
    REPO_ROOT = os.environ.get("CLAUDE_PROJECT_DIR") or os.devnull
ROOTS = ()  # (hook cwd, CLAUDE_PROJECT_DIR, process cwd) whose origin/main tables are unioned in; decide() sets them per call
TABLE = os.environ.get("PROD_TARGETS_FILE") or str(Path(HERE) / "prod_targets.json")  # unioned with origin/main's all the same (load_table)
MAIN_TABLE_PATH = "scripts/gates/prod_targets.json"
REDIRECTION = re.compile(r"&?[<>]+[&|]?")
ENV_TARGET_VARS = ("PGHOST", "PGSERVICE", "PGDATABASE", "PGUSER", "PGHOSTADDR", "SQLCMDSERVER", "SQLCMDDBNAME", "SQLCMDUSER", "MYSQL_HOST", "DATABASE_URL", "MONGODB_URI", "REDIS_URL", "ALIBABA_CLOUD_PROFILE")
COMMAND_TOOLS = ("Bash", "Monitor", "mcp__terminal__run_in_terminal")  # tools whose tool_input.command runs in a shell (intent 2026-10-24-160437); settings.json's matcher and pre-filter name these and LAUNCH_TOOL
LAUNCH_TOOL = "mcp__Claude_Browser__preview_start"  # starts runtimeExecutable + runtimeArgs of the named .claude/launch.json configuration (cfcbcf473 final M4)
REFUSED = ("拒绝：命令里有生产特征（生产主机名或 IP、生产库名、生产凭据名，见 prod_targets.json；去引号、去反斜杠、变量与花括号展开后也算），"
           "或会话环境里的目标变量指向生产。CLAUDE.md 底线 4：不做生产写操作；生产只读取证走 ADR-0037 专用只读账号和已登记的取证入口。")
CLOUD_CLI = "aliyun"  # Alibaba Cloud CLI: its default profile on this machine is production (aliyun-prod-profile in prod_targets.json)
CLOUD_PIECE = re.compile(r"aliyun(?:[ \t]+[A-Za-z0-9_./:=,@+-]+)*")  # a bare call whose words the shell passes on unchanged
CLOUD_REFUSED = ("拒绝：阿里云命令行的默认 profile 是生产。aliyun 只认段首裸写、参数只用字母数字与 _./:=,@+-（不用引号、反斜杠、注释、重定向、$、通配、花括号、~）、"
                 "显式给一个 --profile <名>（或 --profile=<名>、-p <名>）且名不在 prod_targets.json 的写法；没给 profile、名是生产 profile、用别名、路径、包装程序（env、sudo、xargs、bash -c 等）、"
                 "变量、通配或命令替换调用都拒（卡 #736）。CLAUDE.md 底线 4。")


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


def could_concern(text):  # Pre-filter (text already `plain`): False only when the gate would certainly allow: an empty line.
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


def texts_of(command_text):  # The raw and quote-free line, plus every word and environment value after variables, export and substitutions are expanded (unreadable: the first two only).
    texts, assigned = [command_text, plain(command_text)], []
    try:
        texts += [w for c in simple_commands(command_text, assigned=assigned) for w in c.argv + list(c.env.values())] + assigned
    except ValueError:
        pass
    return texts


def cloud_cli_refusal(command_text, patterns, environ=None):
    """Refusal text or None for the Alibaba Cloud CLI (卡 #736，用户「先不动，门补一条」): its default profile is production and no read-only account stands behind it,
    so only what the gate can read exactly is allowed. Named on the line (also once quotes and backslashes are dropped): each line piece between `;`, `&`, `|` and newlines
    that names it must be a bare `aliyun` followed by plain words only (letters, digits, `_./:=,@+-`: no quote, backslash, comment, redirection, `$`, glob, brace or tilde,
    which could change what it receives), with exactly one `--profile <name>` (`--profile=<name>`, `-p <name>`) matching no pattern, and no ALIBABA_CLOUD_ variable on the line.
    Not named: a program word (also after xargs or find -exec) that a glob, a brace, the session's environment or a command substitution with arguments may turn into it: refused."""
    environ = os.environ if environ is None else environ
    bare = 0  # line pieces that are a bare call the gate reads exactly
    if CLOUD_CLI in plain(command_text) or CLOUD_CLI in command_text.lower():
        if "alibaba_cloud" in plain(command_text):
            return CLOUD_REFUSED
        for piece in re.split(r"[;&|\n]", command_text):
            if CLOUD_CLI not in piece.lower() and CLOUD_CLI not in plain(piece):
                continue
            words = piece.split()
            if not CLOUD_PIECE.fullmatch(piece.strip()) or words[0] != CLOUD_CLI:
                return CLOUD_REFUSED
            names = [words[at + 1] if at + 1 < len(words) else "" for at, w in enumerate(words) if w in ("--profile", "-p")] + [w.partition("=")[2] for w in words if w.startswith("--profile=")]
            odd = [w for w in words if (w.startswith("--profile") or w.startswith("-p")) and w not in ("--profile", "-p") and not w.startswith("--profile=")]
            if len(names) != 1 or odd or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.-]*", names[0]) or text_hits(names, patterns):
                return CLOUD_REFUSED
            bare += 1
    try:
        commands = simple_commands(command_text)
    except ValueError:
        return None
    bodies = [command_text]
    for c in commands:  # eval's words, a shell's -c body: read again, as simple_commands does
        if name_of(c.argv[0]) == "eval":
            bodies.append(" ".join(c.argv[1:]))
        elif name_of(c.argv[0]) in SHELLS:
            bodies += [c.argv[at + 1] for at, a in enumerate(c.argv[:-1]) if SHORT_C.fullmatch(a)]
    if any(substituted_program(body) for body in bodies):
        return CLOUD_REFUSED
    named = 0  # program words that are the CLI's name as written (each must be one of the bare pieces)
    for command in commands:
        for word, has_args in program_words(command.argv):
            if word == CLOUD_CLI:
                named += 1
            elif may_be_cloud_cli(word, has_args, environ):
                return CLOUD_REFUSED
    return CLOUD_REFUSED if named > bare else None


def substituted_program(text):
    """True when a command substitution (or backticks) makes up part of a program word that is given arguments: `$(printf ali)yun ecs X`. Read on the raw text, since the
    tokenizer splits an unquoted `$(` apart. Program position: only assignments and keywords, or a wrapper or xargs (whatever follows it), or find's -exec before it
    since the last `;`, `&`, `|`, `(` or newline. With no argument it is left alone, because 用户 10-28 listed `eval "$(printf x)"` as a misblock to allow; that leaves a known gap
    (eval, or an unquoted substitution, can make its output a whole command line), put to the user (#736 首轮 M2)."""
    text = neutralize(text)
    for start in [m.start() for m in re.finditer(r"\$\(", text)]:
        depth, end = 0, start + 1
        while end < len(text):
            depth += {"(": 1, ")": -1}.get(text[end], 0)
            if depth == 0:
                break
            end += 1
        left = start
        while left > 0 and not text[left - 1].isspace() and text[left - 1] not in ";&|()\n":
            left -= 1
        right = end + 1
        while right < len(text) and not text[right].isspace() and text[right] not in ";&|)\n":
            right += 1
        if ENV.match(text[left:right]):
            continue  # `X=$(date) make`: an assignment, not a program
        before = re.split(r"[;&|(\n]", text[:left])[-1].split()
        after = re.split(r"[;&|)\n]", text[right:])[0].strip()
        rest = [w for w in before if not (ENV.match(w) or w in KEYWORDS)]  # after a wrapper or xargs its options and their values are not told apart: still program position
        prefix_ok = not rest or name_of(rest[0]) in WRAPPERS or name_of(rest[0]) == "xargs"
        if (prefix_ok or (before and before[-1] in ("-exec", "-execdir", "-ok", "-okdir"))) and after:
            return True
    return False


def program_words(argv):  # (word, has arguments) for each word of a simple command that names a program: the first, the first non-option word after xargs, the word after find's -exec.
    out = [(argv[0], len(argv) > 1)] if argv else []
    if argv and name_of(argv[0]) == "xargs":
        rest = [a for a in argv[1:] if not a.startswith("-")]
        out += [(rest[0], len(rest) > 1)] if rest else []
    if argv and name_of(argv[0]) == "find":
        out += [(argv[at + 1], at + 2 < len(argv)) for at, a in enumerate(argv[:-1]) if a in ("-exec", "-execdir", "-ok", "-okdir")]
    return out


def may_be_cloud_cli(word, has_args, environ):  # True when the program a word names may be the cloud CLI although the line does not spell it.
    if "$(" in word or "`" in word:
        return has_args  # unknown program; one with no argument stays allowed because 用户 10-28 listed `eval "$(printf x)"` as a misblock — a known gap: eval or an unquoted substitution can turn its output into a whole command
    word = VARIABLE.sub(lambda m: str(environ.get(m.group(1) or m.group(2), "")), word)  # the session's environment: what is left after the line's own variables
    for variant in brace_variants(word.lstrip("=")):
        name = os.path.basename(variant).lower()
        if CLOUD_CLI in name or (re.search(r"[*?\[]", name) and fnmatch.fnmatchcase(CLOUD_CLI, name)):
            return True
    return False


def judge(command_text, patterns, problem=None, cwd=None, environ=None):
    """Refusal text or None. Refused when production shows, whatever the program (卡 #736，用户「见生产特征即拒，其余靠只读账号」): a pattern in the raw line, the line with quotes and
    backslashes removed, a word or environment value after variables, export and braces are expanded, or a target variable of the session environment pointing at production.
    No table: refused (fail closed). Shell syntax alone refuses nothing; what a script or inline code does at run time is the read-only account's to stop."""
    if patterns is None:
        return f"{problem}：判断不了命令有没有生产特征，拒绝；修好 scripts/gates/prod_targets.json 后再做。"
    environ = os.environ if environ is None else environ
    if text_hits(texts_of(command_text), patterns) or any(text_hits([str(environ.get(v, ""))], patterns) for v in ENV_TARGET_VARS):
        return REFUSED
    return cloud_cli_refusal(command_text, patterns, environ)


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
    def line(root):  # runtimeExecutable runtimeArgs program args, else node program args; env before, its cwd as a `cd` first
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
    global ROOTS
    ROOTS = (cwd, os.environ.get("CLAUDE_PROJECT_DIR"), os.getcwd())
    try:
        patterns, problem, warning = load_table()
        reason = judge(command, patterns, problem, cwd)
    finally:
        ROOTS = ()
    if reason:
        return 2, "", reason + "\n"
    out = json.dumps({"hookSpecificOutput": {"hookEventName": "PreToolUse", "additionalContext": f"告警：{warning}"}}, ensure_ascii=False) + "\n" if warning else ""
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
