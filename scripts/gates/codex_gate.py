#!/usr/bin/python3
"""codex 门（PreToolUse：Bash、Monitor、终端面板、preview_start 启动的命令；#1180）：codex 只放一种用法，其余一律拒（白名单，不是黑名单）。

放行的唯一形状（整条命令就是它，没有别的命令、管道、链、包装、替换）：
    codex exec --sandbox workspace-write   （也写 -s workspace-write、--sandbox=workspace-write）
      [-m|--model <名>]  [-c|--config model_reasoning_effort=<low|medium|high|xhigh>]  <一个提示词>  [< 输入文件]  [> 输出文件]  [2>&1]
且当前目录在一个会话 worktree 里（钩子输入的 cwd 的 git 顶层须在 .claude/worktrees/ 下、不是主检出；不比对 CLAUDE_PROJECT_DIR：app 起的 worktree 会话里它指向主检出，钩子分不出哪个 worktree 是调用会话自己的）；
输入、输出重定向的目标也须在这个 worktree 里（/dev/null 除外；暂存目录不开例外：提示词写进 worktree 里的文件，或直接作参数）。
其他一切拒：不带沙箱、别的沙箱值、绕过沙箱的开关、--full-auto、--oss、-p、-C、--add-dir、--enable、别的 -c 键、CODEX_HOME=、resume、别的子命令、裸 codex；
也拒 codex 出现在包装（timeout、time、nice、nohup、stdbuf、command、builtin、noglob、env、sudo、xargs、find -exec…）、前置变量赋值、&& ; | 链、$(…) 与反引号、
bash -c、sh -c、zsh -c、eval、here-document 喂给解释器、绝对或相对路径、npx 之下的任何地方。
命令文本上看不到的（脚本文件里写的、拼接出来的名字）门放行，与生产写门同一立场；解析不了的命令：文本里有 codex 就拒，没有就放行。
门本身出错（导入失败、崩溃、超时）由 pre_tool_dispatch 退出码 3 交给 settings.json 的预筛，预筛见 codex 字样就拒（失败关闭）。
用法：codex_gate.py --self-test（自测在 gates_selftest.py 的 codex_gate 段）
"""
import fnmatch
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
TOKEN = re.compile(r"(?<![A-Za-z0-9_.])codex(?![A-Za-z0-9_])", re.IGNORECASE)   # codex as a word: codex, @openai/codex, ./codex, codex-cli; not CODEX_HOME, .codex
ASSIGN = re.compile(r"[A-Za-z_][A-Za-z0-9_]*\+?=")
RESERVED = {"if", "then", "elif", "else", "while", "until", "do", "done", "fi", "esac", "{", "}", "!"}
RUNNERS = {  # programs that run what their arguments name (wrappers, shells, interpreters, launchers): codex anywhere in their text is a wrapped use
    "timeout", "gtimeout", "time", "nice", "nohup", "stdbuf", "command", "builtin", "noglob", "env", "sudo", "doas", "su", "runuser", "pkexec", "exec", "setsid", "caffeinate",
    "xargs", "find", "watch", "parallel", "script", "ssh", "flock", "lockf", "chroot", "ionice", "taskpolicy", "arch", "unbuffer", "strace", "ltrace", "dtruss", "sandbox-exec",
    "unshare", "nsenter", "tmux", "screen", "expect", "at", "batch", "launchctl", "open", "osascript", "make", "gmake", "just", "npx", "bunx", "pnpx", "pnpm", "npm", "yarn", "uvx", "pipx",
    "sh", "bash", "zsh", "dash", "ksh", "fish", "csh", "tcsh", "eval", "source", ".", "python", "python3", "python2", "node", "nodejs", "deno", "bun", "perl", "ruby", "php", "lua", "awk", "gawk",
}
DYNAMIC = {  # programs that execute text they are handed (a shell, eval, an interpreter, xargs...): with a program word that expands, a script holding one runs what it names only at run time
    "sh", "bash", "zsh", "dash", "ksh", "fish", "csh", "tcsh", "eval", "source", ".", "exec", "xargs", "watch", "parallel", "python", "python3", "python2", "node", "nodejs", "deno", "bun",
    "perl", "ruby", "php", "lua", "awk", "gawk", "osascript", "script", "expect", "tmux", "screen", "at", "batch", "ssh", "su", "sudo", "doas", "runuser", "pkexec", "env", "find", "timeout", "gtimeout",
}
DATA = {  # programs that only read their arguments as data (names, patterns, messages): codex named in their arguments is no use of it. Every other program naming codex is refused (an allow-list, not a list of known runners)
    "echo", "printf", "which", "type", "whereis", "grep", "egrep", "fgrep", "rg", "ag", "git", "gh", "ls", "cat", "head", "tail", "wc", "less", "more", "sort", "uniq", "cut", "tr", "sed", "diff", "cmp",
    "stat", "file", "test", "[", "jq", "yq", "touch", "mkdir", "rm", "cp", "mv", "ln", "cd", "pwd", "date", "basename", "dirname", "realpath", "readlink", "du", "df", "ps", "pgrep", "true", "false", "sleep",
    "export", "unset", "local", "declare", "readonly", "printenv", "tee",
}
INTERPRETERS = {"python", "python3", "python2", "node", "nodejs", "perl", "ruby", "php", "lua", "sh", "bash", "zsh", "dash", "ksh"}
SANDBOX_OK = "workspace-write"
EFFORT = re.compile(r"""model_reasoning_effort=("?)(low|medium|high|xhigh)\1""")
MODEL = re.compile(r"[A-Za-z0-9][A-Za-z0-9._:/@-]*")
DEPTH = 5
SAFE_NULL = "/dev/null"

WRAPPED = ("拒绝：codex 只放一种用法：整条命令就是 `codex exec --sandbox workspace-write [-m 模型] [-c model_reasoning_effort=…] <提示词>`（可带 < 输入文件、> 输出文件）。"
           "这条命令里 codex 出现在包装、链、替换、变量赋值、路径或别的位置，按拒绝处理。")
UNPARSED = "拒绝：命令里有 codex，但这条命令的写法解析不了，判断不了它跑什么，按拒绝处理（只放 `codex exec --sandbox workspace-write …` 一种形状）。"
SHAPE = "拒绝：codex 只放 `codex exec --sandbox workspace-write [-m 模型] [-c model_reasoning_effort=low|medium|high|xhigh] <一个提示词>`（可带 < 输入文件、> 输出文件）：{why}。"
PLACE = "拒绝：codex 只在会话 worktree（.claude/worktrees/ 下）里跑：{why}。"


class ParseError(Exception):
    pass


class Word:
    """One shell word with its quotes removed. unquoted: an expansion or glob outside quotes (the shell may split it into several words); expands: any expansion at all."""
    def __init__(self):
        self.text, self.unquoted, self.expands = "", False, False


def match_close(s, i, open_, close):
    """Index of the bracket closing the one just before s[i]; quotes inside are skipped."""
    depth = 1
    while i < len(s):
        c = s[i]
        if c == "\\":
            i += 2
            continue
        if c == "'":
            i = s.find("'", i + 1)
            if i < 0:
                break
        elif c == '"':
            i = skip_double(s, i + 1)
        elif c == open_:
            depth += 1
        elif c == close:
            depth -= 1
            if depth == 0:
                return i
        i += 1
    raise ParseError("unterminated " + open_)


def skip_double(s, i):
    """Index of the quote closing the double-quoted string that starts at s[i]."""
    while i < len(s):
        c = s[i]
        if c == "\\":
            i += 2
            continue
        if c == '"':
            return i
        if c == "$" and s[i + 1:i + 2] == "(":
            i = match_close(s, i + 2, "(", ")")
        elif c == "$" and s[i + 1:i + 2] == "{":
            i = match_close(s, i + 2, "{", "}")
        elif c == "`":
            i = s.find("`", i + 1)
            if i < 0:
                break
        i += 1
    raise ParseError("unterminated double quote")


ESCAPES = {"n": "\n", "t": "\t", "r": "\r", "a": "\a", "b": "\b", "e": "\x1b", "E": "\x1b", "f": "\f", "v": "\v", "\\": "\\", "'": "'", '"': '"', "?": "?"}


def ansi_c(body):
    out, i = "", 0
    while i < len(body):
        c = body[i]
        if c != "\\" or i + 1 >= len(body):
            out += c
            i += 1
            continue
        n = body[i + 1]
        m = re.match(r"([xuU])([0-9A-Fa-f]{1,8})|([0-7]{1,3})", body[i + 1:])
        if m:
            number = int(m.group(2), 16) if m.group(1) else int(m.group(3), 8)
            out += chr(number) if 0 < number < 0x110000 else ""
            i += 1 + len(m.group())
        else:
            out += ESCAPES.get(n, "\\" + n)
            i += 2
    return out


def read_word(s, i, nested):
    """(Word, index after it) for the word starting at s[i]; substitutions found are appended to `nested`."""
    w, n = Word(), len(s)
    while i < n:
        c = s[i]
        if c in " \t\n;&|()" or (c in "<>" and not (s[i + 1:i + 2] == "(" and not w.text and not w.expands)):
            break
        if c == "\\":
            if i + 1 >= n:
                raise ParseError("trailing backslash")
            if s[i + 1] != "\n":
                w.text += s[i + 1]
            i += 2
        elif c == "'":
            j = s.find("'", i + 1)
            if j < 0:
                raise ParseError("unterminated single quote")
            w.text += s[i + 1:j]
            i = j + 1
        elif c == '"':
            j = skip_double(s, i + 1)
            i = read_double(s, i + 1, j, w, nested) + 1
        elif c == "$" and s[i + 1:i + 2] == "'":
            j = i + 2
            while j < n and s[j] != "'":
                j += 2 if s[j] == "\\" else 1
            if j >= n:
                raise ParseError("unterminated $'")
            w.text += ansi_c(s[i + 2:j])
            i = j + 1
        elif c == "$" and s[i + 1:i + 2] == "(":
            j = match_close(s, i + 2, "(", ")")
            nested.append(s[i + 2:j])
            w.text += "\0"; w.unquoted = w.expands = True
            i = j + 1
        elif c == "$" and s[i + 1:i + 2] == "{":
            j = match_close(s, i + 2, "{", "}")
            nested.append(s[i + 2:j])
            w.text += "\0"; w.unquoted = w.expands = True
            i = j + 1
        elif c == "$" and re.match(r"[A-Za-z_0-9@*#?!$-]", s[i + 1:i + 2] or " "):
            m = re.match(r"\$(?:[A-Za-z_][A-Za-z0-9_]*|.)", s[i:])
            w.text += "\0"; w.unquoted = w.expands = True
            i += len(m.group())
        elif c == "`":
            j = s.find("`", i + 1)
            if j < 0:
                raise ParseError("unterminated backtick")
            nested.append(s[i + 1:j])
            w.text += "\0"; w.unquoted = w.expands = True
            i = j + 1
        elif c in "<>" and s[i + 1:i + 2] == "(":
            j = match_close(s, i + 2, "(", ")")
            nested.append(s[i + 2:j])
            w.text += "\0"; w.unquoted = w.expands = True
            i = j + 1
        else:
            if c in "*?[{" or (c == "~" and not w.text):
                w.unquoted = w.expands = True
            w.text += c
            i += 1
    if w.text in ("{", "}"):
        w.unquoted = w.expands = False
    return w, i


def read_double(s, i, end, w, nested):
    """Add the double-quoted text s[i:end] to `w` (expansions recorded, not unquoted); returns end."""
    while i < end:
        c = s[i]
        if c == "\\" and i + 1 < end and s[i + 1] in '$`"\\\n':
            if s[i + 1] != "\n":
                w.text += s[i + 1]
            i += 2
        elif c == "$" and s[i + 1:i + 2] == "(":
            j = match_close(s, i + 2, "(", ")")
            nested.append(s[i + 2:j]); w.text += "\0"; w.expands = True
            i = j + 1
        elif c == "$" and s[i + 1:i + 2] == "{":
            j = match_close(s, i + 2, "{", "}")
            nested.append(s[i + 2:j]); w.text += "\0"; w.expands = True
            i = j + 1
        elif c == "`":
            j = s.find("`", i + 1)
            if j < 0 or j >= end:
                raise ParseError("unterminated backtick")
            nested.append(s[i + 1:j]); w.text += "\0"; w.expands = True
            i = j + 1
        elif c == "$" and re.match(r"[A-Za-z_0-9@*#?!$-]", s[i + 1:i + 2] or " "):
            m = re.match(r"\$(?:[A-Za-z_][A-Za-z0-9_]*|.)", s[i:])
            w.text += "\0"; w.expands = True
            i += len(m.group())
        else:
            w.text += c
            i += 1
    return end


REDIRECT = re.compile(r"(\d*)(<<<|<<-|<<|<&|<>|<|>>|>&|>\||>)")
BOTH = re.compile(r"(&)(>>|>)")   # &> file and &>> file: stdout and stderr


def tokenize(s):
    """(tokens, nested script texts): tokens are ("w", Word), ("op", text) or ("r", fd, op, heredoc body or None); a redirect's target is the word after it."""
    toks, nested, pending, i, n = [], [], [], 0, len(s)
    while i < n:
        c = s[i]
        if c in " \t":
            i += 1
        elif c == "\n":
            toks.append(("op", "\n"))
            i += 1
            for tok, delim, dash, quoted in pending:
                body, i = read_heredoc(s, i, delim, dash)
                tok[3] = body
                if not quoted:
                    read_double(body, 0, len(body), Word(), nested)   # an unquoted here-document expands: its substitutions run
            pending = []
        elif c == "#":
            j = s.find("\n", i)
            i = n if j < 0 else j
        elif c in ";&|()" and not (c == "&" and s[i + 1:i + 2] == ">"):
            two = s[i:i + 2]
            op = two if two in ("&&", "||", ";;", "|&") else c
            toks.append(("op", op))
            i += len(op)
        else:
            m = None if c in "<>" and s[i + 1:i + 2] == "(" else (REDIRECT.match(s, i) or BOTH.match(s, i))
            if m:
                tok = ["r", m.group(1), m.group(2), None]
                toks.append(tok)
                i = m.end()
                if m.group(2) in ("<<", "<<-"):
                    while i < n and s[i] in " \t":
                        i += 1
                    start = i
                    w, i = read_word(s, i, nested)
                    toks.append(("w", w))
                    pending.append((tok, re.sub(r"['\"\\]", "", s[start:i]), m.group(2) == "<<-", bool(re.search(r"['\"\\]", s[start:i]))))   # the delimiter as written: never expanded
                continue
            w, i = read_word(s, i, nested)
            toks.append(("w", w))
    return toks, nested   # a here-document without its closing line has no body: the shell warns and reads to the end


def read_heredoc(s, i, delim, dash):
    """(body, index after the closing line); a missing closing line makes the rest of the text the body, as in the shell."""
    lines, n = [], len(s)
    while i < n:
        j = s.find("\n", i)
        line = s[i:] if j < 0 else s[i:j]
        i = n if j < 0 else j + 1
        if (line.lstrip("\t") if dash else line) == delim:
            return "\n".join(lines), i
        lines.append(line)
    return "\n".join(lines), n


class Command:
    def __init__(self, words, redirs, bodies):
        self.words, self.redirs, self.bodies = words, redirs, bodies   # redirs: [(fd, op, target Word or None)]
        self.assigns = 0


def commands(toks):
    """The simple commands between operators, with leading reserved words and variable assignments skipped; plus whether there was nothing but one simple command."""
    out, cur, ops = [], [], 0
    def flush():
        if cur:
            words, redirs, bodies, k = [], [], [], 0
            while k < len(cur):
                t = cur[k]
                if t[0] == "r":
                    target = cur[k + 1][1] if k + 1 < len(cur) and cur[k + 1][0] == "w" else None
                    redirs.append((t[1], t[2], target))
                    if t[3] is not None:
                        bodies.append(t[3])
                    k += 2 if target is not None else 1
                else:
                    words.append(t[1])
                    k += 1
            out.append(Command(words, redirs, bodies))
        cur.clear()
    for t in toks:
        if t[0] == "op":
            ops += t[1] != "\n"
            flush()
        else:
            cur.append(t)
    flush()
    return out, ops == 0 and len(out) == 1


def program(cmd):
    """(program Word or None, number of leading words skipped as reserved words or assignments)."""
    k = 0
    while k < len(cmd.words) and cmd.words[k].text in RESERVED:
        k += 1
    while k < len(cmd.words) and ASSIGN.match(cmd.words[k].text):
        k += 1
        cmd.assigns += 1
    return (cmd.words[k], k + 1) if k < len(cmd.words) else (None, k)


class Use:
    def __init__(self, kind, cmd=None, start=0):
        self.kind, self.cmd, self.start = kind, cmd, start   # kind: "direct" (the program word is exactly `codex`, nothing before it) or "indirect"


def bare(text):
    """The text with $'...' escapes decoded and quotes and backslashes removed: what is left to look for codex in when the text does not parse."""
    text = re.sub(r"\$'((?:[^'\\]|\\.)*)'", lambda m: ansi_c(m.group(1)), text)
    return re.sub(r"""['"\\]""", "", text)


def braces(word):
    """The words a brace expansion makes of `word` (comma lists and letter ranges, capped)."""
    m = re.search(r"\{([^{}]*)\}", word)
    if not m:
        return [word]
    body, span = m.group(1), re.fullmatch(r"([A-Za-z])\.\.([A-Za-z])", m.group(1))
    if span:
        low, high = sorted((ord(span.group(1)), ord(span.group(2))))
        alts = [chr(n) for n in range(low, high + 1)]
    elif "," in body:
        alts = body.split(",")
    else:
        return [word]
    return [w for alt in alts for w in braces(word[:m.start()] + alt + word[m.end():])][:64]


BRACKET = re.compile(r"\[[!^]?\]?[^\]]*\]")   # a shell bracket expression: [abc], [!a-c], [^x], []x]


def glob_matches_codex(base):
    """fnmatch of "codex" against the word with every bracket expression read as `?` (any one character, a superset of what the bracket matches). Python reads brackets differently from bash and zsh (`[^-+]` negates there, `[z-ac]` keeps `c`) and may fail to compile them, so the bracket's own meaning is not trusted; a superset can only refuse more, and it always compiles (#1205)."""
    return fnmatch.fnmatchcase("codex", BRACKET.sub("?", base))


def could_be_codex(word):
    """True when the shell may expand this word to a path ending in codex: co{dex,}, cod?x, c*x, /x/c[o]dex (a bare variable cannot be judged and is the accepted gap)."""
    for w in braces(word):
        base = os.path.basename(w.rstrip("/")).replace("\0", "*")
        if base.strip("*") and glob_matches_codex(base.lower()):
            return True
    return False


def runs_file(name, cmd):
    """The script file an interpreter is run on (`python3 scripts/x.py ...`, `bash ./x.sh ...`), else None: not text from its command line or stdin. The script is not read (a script that itself runs codex is the accepted gap), its arguments are data."""
    if name not in INTERPRETERS:
        return None
    for w in cmd.words[[i for i, x in enumerate(cmd.words) if os.path.basename(x.text) == name][0] + 1:]:
        t = w.text
        if t.startswith("--") and len(t) > 2:
            if t in ("--eval", "--print", "--command", "--module"):
                return None
        elif t.startswith("-") and t != "-":
            if set(t[1:]) & set("cemprE"):
                return None
        else:
            return t if t != "-" and not w.expands and re.search(r"[./]", t) else None
    return None


def written_files(cmds):
    """The files a script writes itself (> >> &> targets, tee arguments): running one of them is running text this very command made."""
    out = set()
    for c in cmds:
        out |= {t.text for _, op, t in c.redirs if t is not None and op in (">", ">>", ">|") or (_ == "&" and t is not None)}
        if c.words and os.path.basename(c.words[0].text) == "tee":
            out |= {w.text for w in c.words[1:] if not w.text.startswith("-")}
    return out


def nested_uses(text, depth):
    """(uses, dynamic) of a script held in a string; a string that does not parse counts as a use when it names codex once its quotes are gone, and as dynamic."""
    try:
        found, _, _, dynamic = uses_in(text, depth)
        return found, dynamic
    except (ParseError, RecursionError):
        return ([Use("indirect")] if TOKEN.search(bare(text)) else []), True


def uses_in(text, depth=0):
    """(list of Use, simple commands, whether the text is nothing but one simple command, whether anything in it runs text only known at run time)."""
    if depth > DEPTH:
        if TOKEN.search(bare(text)):
            raise ParseError("nested too deep")
        return [], [], False, True
    toks, nested = tokenize(text)
    cmds, single = commands(toks)
    found, dynamic, written = [], False, written_files(cmds)
    for script in nested:
        inner, inner_dynamic = nested_uses(script, depth + 1)
        found += [Use("indirect") for _ in inner]
        dynamic = dynamic or inner_dynamic
    for cmd in cmds:
        prog, nxt = program(cmd)
        if prog is None:
            continue
        name = os.path.basename(prog.text.rstrip("/")).lower()
        script = runs_file(name, cmd) or (prog.text if "/" in prog.text and not prog.expands and name not in RUNNERS and name not in DYNAMIC else None)   # a script run by path or by an interpreter: its arguments are data
        file_run = script is not None and script not in written and os.path.normpath(script) not in {os.path.normpath(w) for w in written}   # unless this command wrote it
        dynamic = dynamic or prog.expands or (name in DYNAMIC and not file_run) or (script is not None and not file_run)
        texts = [w.text for w in cmd.words] + cmd.bodies
        if name == "codex" and prog.text == "codex" and not prog.expands and cmd.assigns == 0 and cmd.words[0] is prog:
            found.append(Use("direct", cmd, nxt))
        elif name == "codex" or TOKEN.search(prog.text) or could_be_codex(prog.text):
            found.append(Use("indirect"))
        elif name not in DATA and not file_run:
            if any(could_be_codex(t) or ("=" in t and could_be_codex(t.split("=", 1)[1])) for t in texts):   # codex (or a brace or glob spelling of it) as a word of its own, or as the value of x=codex
                found.append(Use("indirect"))
            else:
                for t in texts[1:]:   # a shell string, an eval or trap argument, a script on stdin: read as a script of its own
                    if TOKEN.search(bare(t)):
                        inner, inner_dynamic = nested_uses(t, depth + 1)
                        found += [Use("indirect") for _ in inner]
                        dynamic = dynamic or inner_dynamic
    return found, cmds, single, dynamic


def own_worktree(cwd):
    """(top level of the session worktree the command runs in, None) or (None, why not)."""
    if not (isinstance(cwd, str) and os.path.isabs(cwd) and os.path.isdir(cwd)):
        return None, "钩子输入里没有可用的当前目录（cwd）"
    sys.path.insert(0, HERE)
    import main_checkout_gate as m
    try:
        top, is_main, _ = m.where(cwd)
    except Exception as error:
        return None, f"git 说不出当前目录属于哪个仓库（{error}）"
    if is_main or os.path.realpath(top).split(os.sep)[-3:-1] != [".claude", "worktrees"]:
        return None, f"{top} 不是 .claude/worktrees/ 下的 worktree（主检出或别处不放行）"
    if not inside(cwd, os.path.realpath(top), cwd):   # git's top level can be redirected (core.worktree in a linked worktree's config): the directory itself must be below it
        return None, f"当前目录 {cwd} 不在 git 报的顶层 {top} 之下"
    return os.path.realpath(top), None


def inside(path, top, cwd):
    full = os.path.realpath(os.path.join(cwd, path))
    return full == top or full.startswith(top + os.sep)


def shape_problem(cmd, start):
    """Why the arguments are not the one allowed shape, or None."""
    args = cmd.words[start:]
    if cmd.bodies:
        return "带 here-document"
    if any(w.unquoted for w in cmd.words):
        return "参数里有引号外的变量、通配符、花括号或波浪号展开（会被 shell 拆成别的参数）"
    if not args or args[0].text != "exec" or args[0].expands:
        return "第一个参数必须是 exec（裸 codex 与别的子命令都拒）"
    sandbox = model = effort = None
    prompt, flags_done, i = [], False, 1
    def value():
        nonlocal i
        i += 1
        return None if i >= len(args) or args[i].expands else args[i].text
    while i < len(args):
        a = args[i].text
        if flags_done or not a.startswith("-") or a == "-":
            prompt.append(args[i])
        elif a == "--":
            flags_done = True
        elif a in ("-s", "--sandbox") or a.startswith("--sandbox="):
            if sandbox is not None:
                return "沙箱开关重复"
            sandbox = a.split("=", 1)[1] if a.startswith("--sandbox=") else value()
        elif a in ("-m", "--model") or a.startswith("--model="):
            if model is not None:
                return "模型开关重复"
            model = a.split("=", 1)[1] if a.startswith("--model=") else value()
            if model is None or not MODEL.fullmatch(model):
                return "模型名只许字母数字与 . _ : / @ -，且不以 - 开头"
        elif a in ("-c", "--config") or a.startswith("--config="):
            if effort is not None:
                return "-c 只放一个，且只能是 model_reasoning_effort"
            effort = a.split("=", 1)[1] if a.startswith("--config=") else value()
            if effort is None or not EFFORT.fullmatch(effort):
                return "-c 只放 model_reasoning_effort=low|medium|high|xhigh，别的配置键一概不放（含 sandbox_workspace_write.network_access）"
        else:
            return f"不放行的开关 {a}"
        i += 1
    if sandbox != SANDBOX_OK:
        return f"须带 --sandbox workspace-write（现为 {sandbox!r}）"
    if len(prompt) != 1:
        return "须恰有一个提示词参数"
    if prompt[0].text != "-" and not re.search(r"\s", prompt[0].text):
        return "提示词须是一句话（含空白）或 -（读标准输入）：单个词会被 codex 当子命令（resume、review 等）"
    return None


def redirect_problem(cmd, cwd, top):
    """Why a redirection is not allowed (stdin from, stdout and stderr to a file inside the worktree, 2>&1), or None."""
    for fd, op, target in cmd.redirs:
        if op == ">&" and fd == "2" and target is not None and target.text == "1" and not target.expands:
            continue
        if (fd, op) not in {("", "<"), ("0", "<"), ("", ">"), ("1", ">"), ("2", ">"), ("", ">>"), ("1", ">>"), ("2", ">>"), ("&", ">"), ("&", ">>")}:
            return f"不放行的重定向 {fd}{op}"
        if target is None or target.unquoted or target.expands:
            return "重定向目标须是写死的路径"
        if target.text != SAFE_NULL and not inside(target.text, top, cwd):
            return f"重定向目标 {target.text} 不在 worktree 里"
    return None


def check(command, cwd):
    """Refusal text for a shell command, or None when it runs no codex or runs it in the one allowed shape."""
    try:
        found, cmds, single, dynamic = uses_in(command)
        named = bool(TOKEN.search(bare(command))) or any(could_be_codex(w.text) for c in cmds for w in c.words)
    except (ParseError, RecursionError):   # a text nested deeper than the interpreter reads is not a text this gate can judge
        return UNPARSED if TOKEN.search(bare(command)) or len(command) > 20000 else None
    if not found:   # codex only named, not run by anything the text shows; but named in a script that also runs text built at run time (`echo codex ... | bash`, `$(which codex) ...`, `X=codex; $X ...`) is refused
        return WRAPPED if named and dynamic else None
    if len(found) != 1 or found[0].kind != "direct" or not single or dynamic:
        return WRAPPED
    use = found[0]
    problem = shape_problem(use.cmd, use.start)
    if problem:
        return SHAPE.format(why=problem)
    top, why = own_worktree(cwd)
    if top is None:
        return PLACE.format(why=why)
    problem = redirect_problem(use.cmd, cwd, top)
    return SHAPE.format(why=problem) if problem else None


def decide(payload, command):
    """(exit code, stdout, stderr) for a command the production gate has already read out of the hook input."""
    reason = check(command, payload.get("cwd"))
    return (2, "", reason + "\n") if reason else (0, "", "")


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        sys.path.insert(0, HERE)
        from gates_selftest import codex_gate_test as self_test
        sys.exit(self_test())
    print("用法：codex_gate.py --self-test", file=sys.stderr)
    sys.exit(1)
