#!/usr/bin/python3
"""pkill 门（PreToolUse：Bash、Monitor、终端面板、preview_start 启动的命令；#1311）：共享构建机上，会话不得用没有范围的名字去杀进程。

起因：PR #1288 的 CI 因别的会话跑了 `pkill -f failsafe`，把自托管 runner 上的 failsafe JVM 一并杀了（退出码 143，没有测试失败）。
拒绝：
  · pkill、killall：每一个模式（pkill 可带多个，按「或」匹配）都须含本会话 worktree 的绝对路径，路径后须是 / 或结尾（…/w1 不放过 …/w10），模式里不得有 | 或 (；
    killall 只认进程名，永远拒；pkill -x / --exact 只比进程名，永远拒；没有模式时只放 -P <ppid>、-F <文件>（ppid 为字面 0 或 1 的拒）；
  · kill 的操作数是 0 或负数（整个进程组、本用户的全部进程：kill 0、kill -9 -1、kill -- -<pgid>、kill -TERM -$PGID）；信号（-9、-TERM、-s TERM、-n 15）不算操作数；
  · kill 的进程号来自没有范围的 pgrep / ps / pidof / jps：来源须直达 kill（管道进 xargs kill 或 while read、写在 kill 操作数的 $(…) 或反引号里）；
    pgrep 的每个模式须同样带本路径（或 -P）；ps 要么 -p 只列已知进程号，要么同一管道里有不带 -v 的 grep/rg/awk/sed 且词里含本路径；pidof、jps 永远算没有范围。
    与 kill 无关的 ps/pgrep（杀完用 ps -p 看一眼）不拦。
放行：模式含本 worktree 绝对路径的 pkill -f / pgrep -f（$PWD、${PWD}、$(pwd) 先换成钩子输入的 cwd；命令里有 cd、pushd 时不换）；pkill -P <pid>、pkill -F <文件>；
kill 一个字面 pid、$变量、$(cat 文件)（自己记录的 pid）；只在文本里出现的字样（提交信息、grep 的参数、echo）。包装（sudo、env、nohup、timeout、nice、command、xargs…，连同它们带值的选项）、bash -c、eval、here-document、$(…)、反引号逐层读。
本 worktree = 钩子输入 cwd 的 git 顶层，须在 .claude/worktrees/ 下（codex_gate.own_worktree；不用 CLAUDE_PROJECT_DIR：app 起的 worktree 会话里它指向主检出）；cwd 在主检出或别处时没有本路径，任何 pkill 都拒。
已知读不到的（放行，和生产写门同一立场）：脚本文件里写的 pkill、程序名拼接或变量（`X=pkill; $X`、`pk''ill`）、python/osascript/parallel/find -exec/watch/fish 里的字符串、<(…) 进程替换、lsof -ti 取的 pid。
不碰 CI 看门狗和 backend_verify.sh 的 kill；不拦会话自己记录的 pid。门出错时 pre_tool_dispatch 退出码 3，settings.json 的预筛不含本门：失败放行（ops 剧本：守卫默认失败放行）。
用法：pkill_gate.py --self-test（自测在 gates_selftest.py 的 pkill_gate 段，用例在 pkill_cases.py）
"""
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import codex_gate as cg  # noqa: E402  (tokenizer and own_worktree)

DEPTH = 4
WORDS = re.compile(r"(?<![A-Za-z0-9_.-])(pkill|killall|kill)(?![A-Za-z0-9_-])")
SOURCES = {"pgrep", "ps", "pidof", "jps"}                      # programs whose output is a pid list
VALUE_OPTS = {                                                  # wrappers and the options of theirs that take a value (that value is not the program)
    "sudo": {"-u", "-g", "-C", "-h", "-p", "-U", "-D", "-R", "-r", "-t"}, "env": {"-u", "-C", "-S"}, "nohup": set(), "time": set(), "command": set(), "builtin": set(), "exec": set(), "setsid": set(),
    "timeout": {"-s", "-k"}, "gtimeout": {"-s", "-k"}, "nice": {"-n"}, "ionice": {"-c", "-n", "-p"}, "taskpolicy": {"-c", "-p", "-b", "-B"}, "stdbuf": {"-i", "-o", "-e"}, "caffeinate": {"-t", "-w"},
    "xargs": {"-I", "-n", "-P", "-L", "-s", "-E", "-a", "-d", "-J"},
}
LONG_VALUE = {"--parent", "--pidfile", "--uid", "--euid", "--group", "--pgroup", "--session", "--terminal", "--signal"}
SHELLS = {"sh", "bash", "zsh", "dash", "ksh"}
DURATION = re.compile(r"\d+(\.\d+)?[smhd]?")
SIGNAL = re.compile(r"-([0-9]+|[A-Z][A-Z0-9]+)")
ARG_FLAGS = set("PUugGstFc")                                    # pkill/pgrep short flags that take a value
FILTERS = {"grep", "egrep", "fgrep", "rg", "ag", "awk", "gawk", "sed"}   # programs whose words can narrow a ps listing to the worktree

HOW = ("；限定到本会话 worktree：`pkill -f \"$PWD/backend/target\"`（每个模式都须含本 worktree 的绝对路径、不含 | 和 (），或只杀自己记录的 pid（`kill $PID`、`pkill -P $PID`、`pkill -F pidfile`）。"
       "原因：这台共享构建机上的 CI runner 与别的会话在同一个用户下，没有范围的名字会杀到它们（#1311：`pkill -f failsafe` 杀了 #1288 的 CI）")
NAME = "拒绝：{what}按名字杀进程，没有本 worktree 的路径作范围" + HOW
PIDS = "拒绝：kill 的进程号来自没有范围的 {src}（它的输出直达 kill，而它的模式或同一管道里没有含本 worktree 路径的词）" + HOW
GROUP = "拒绝：kill 0、kill -1、kill -- -<进程组号> 会杀掉整个进程组或本用户的所有进程（含这台共享构建机上的 CI runner）" + HOW
PLACE = "拒绝：{what}，而当前目录不在会话 worktree（.claude/worktrees/ 下）里，没有可作范围的路径（{why}）" + HOW


def scoped(text, top):
    return bool(top) and re.search(re.escape(top) + r"(/|$)", text) is not None and "|" not in text and "(" not in text   # not a sibling that shares the prefix (…/w1 vs …/w10)


def inner_program(words, k, wrapper):
    """Index of the program a wrapper runs (its options, their values, assignments and a timeout duration skipped), or None."""
    values = VALUE_OPTS[wrapper]
    while k < len(words):
        t = words[k].text
        if t in values:
            k += 2
        elif t.startswith("-") or cg.ASSIGN.match(t) or (wrapper in ("timeout", "gtimeout") and DURATION.fullmatch(t)):
            k += 1
        else:
            return k
    return None


def program_of(cmd):
    """(lower-case program name, index of its first argument) after wrappers; name None when it expands or is empty."""
    prog, k = cg.program(cmd)
    for _ in range(6):
        if prog is None or prog.expands:
            return None, k
        name = os.path.basename(prog.text.rstrip("/")).lower()
        if name not in VALUE_OPTS:
            return name, k
        j = inner_program(cmd.words, k, name)
        if j is None:
            return None, k
        prog, k = cmd.words[j], j + 1
    return None, k


def pids_ok(value, flag):
    """Whether the value of -P/--parent (a ppid list) or -F/--pidfile (a file) names the session's own pids: no ppid 0 or 1 in the list (01 and 1,2 too), no device file such as /dev/stdin."""
    if flag == "F":
        return not value.startswith("/dev/")
    return not any(re.fullmatch(r"0*[01]", part) for part in value.split(","))


def patterns_of(args):
    """(every pattern word, whether -P/-F/--parent/--pidfile names the pids, whether -x/--exact is given) of a pkill/pgrep argument list."""
    patterns, pid_scoped, exact, k = [], False, False, 0
    while k < len(args):
        t = args[k].text
        if t == "--":
            patterns += [w.text for w in args[k + 1:]]
            break
        if t == "--exact":
            exact = True
            k += 1
        elif t.startswith("--"):
            if t.split("=")[0] in ("--parent", "--pidfile"):
                pid_scoped = pid_scoped or pids_ok(t.split("=", 1)[1] if "=" in t else args[k + 1].text if k + 1 < len(args) else "", "P" if t.startswith("--parent") else "F")
            k += 2 if t in LONG_VALUE else 1
        elif SIGNAL.fullmatch(t):   # -9, -KILL, -HUP: a signal, not a flag cluster
            k += 1
        elif t.startswith("-") and len(t) > 1:
            for i, c in enumerate(t[1:], 1):
                exact = exact or c == "x"
                if c in ARG_FLAGS:
                    attached = t[i + 1:]
                    value = attached or (args[k + 1].text if k + 1 < len(args) else "")
                    pid_scoped = pid_scoped or (c in "PF" and pids_ok(value, c))
                    k += 0 if attached else 1
                    break
            k += 1
        else:
            patterns.append(t)
            k += 1
    return patterns, pid_scoped, exact


def pkill_problem(args, top, why, name):
    """Refusal text for one pkill/killall command, or None."""
    what = f"`{name}` "
    if name == "killall":
        return NAME.format(what=what)
    patterns, pid_scoped, exact = patterns_of(args)
    if exact:
        return NAME.format(what=what)
    if not patterns:
        return None if pid_scoped else NAME.format(what=what)
    if top is None:
        return PLACE.format(what=what, why=why)
    return None if all(scoped(p, top) for p in patterns) else NAME.format(what=what)


def kill_operands(args):
    """The operands of a kill: the first -SIG, -s SIG or -n N is the signal; what follows (and everything after --) is an operand."""
    out, k, signal = [], 0, False
    while k < len(args):
        t = args[k].text
        if t == "--":
            return out + [w.text for w in args[k + 1:]]
        if t in ("-s", "-n") and not signal:
            signal, k = True, k + 2
        elif t.startswith("-") and len(t) > 1 and not signal:
            signal, k = True, k + 1
        else:
            out.append(t)
            k += 1
    return out


def pipelines(toks):
    """The simple commands of a token list grouped by pipeline: [[Command, …], …] (| and |& join commands; ; && || newline and parentheses separate pipelines)."""
    groups, group, part = [], [], []
    def close():
        if part:
            group.extend(cg.commands(list(part))[0])
            part.clear()
    for t in toks:
        if t[0] == "op" and t[1] in ("|", "|&"):
            close()
        elif t[0] == "op":
            close()
            groups.append(list(group))
            group.clear()
        else:
            part.append(t)
    close()
    groups.append(group)
    return [g for g in groups if g]


PS_PLAIN = {"-w", "-ww", "-c", "-f", "-l", "-j", "-v", "-m"}   # ps options that do not select processes


def lists_given_pids(args):
    """Whether a ps names its processes with -p and selects nothing else (`ps -A -p 1` lists everything, `ps -o pid= -p $PID` only the one)."""
    texts, k, named = [w.text for w in args], 0, False
    while k < len(texts):
        t = texts[k]
        if t == "-p":
            named, k = True, k + 2
        elif t == "-o":
            k += 2
        elif t in PS_PLAIN:
            k += 1
        else:
            return False
    return named


def source_ok(name, cmd, nxt, pipeline, top):
    """Whether one pid source is scoped to the worktree: a pgrep whose every pattern is (or -P), a ps that lists given pids or is narrowed by a positive filter in its pipeline."""
    args = cmd.words[nxt:]
    if name == "pgrep":
        patterns, pid_scoped, _ = patterns_of(args)
        return pid_scoped or (bool(patterns) and all(scoped(p, top) for p in patterns))
    if name == "ps":
        if lists_given_pids(args):
            return True
        for other in pipeline:
            oname, onxt = program_of(other)
            texts = [w.text for w in other.words[onxt:]]
            if oname in FILTERS and not any(t.startswith("-") and not t.startswith("--") and "v" in t[1:] for t in texts) and any(scoped(t, top) for t in texts):
                return True
    return False   # pidof, jps, or a ps nobody narrowed


def xargs_runs_kill(cmd):
    """Whether the command is (a wrapper around) xargs whose program kills or can run a kill (kill, pkill, a shell, env, eval)."""
    for i, w in enumerate(cmd.words):
        if os.path.basename(w.text) == "xargs":
            j = inner_program(cmd.words, i + 1, "xargs")
            return j is not None and os.path.basename(cmd.words[j].text) in ("kill", "pkill", "env", "eval") + tuple(SHELLS)
    return False


def scan(text, top, why, depth=0):
    """Refusal text for the first problem in a shell script text, or None."""
    toks, nested = cg.tokenize(text)
    groups = pipelines(toks)
    for script in nested:   # $(…) and backticks: what they run is judged on its own
        if depth < DEPTH:
            problem = scan(script, top, why, depth + 1)
            if problem:
                return problem
    operand_kill = False   # some kill takes its pids from an expansion ($PIDS, $(…)): whatever fills it is a pid source that reaches the kill
    feeders = []           # (name, command, index of its first argument, pipeline) of pid sources that reach a kill
    for group in groups:
        named = [(c,) + program_of(c) for c in group]
        for cmd, name, nxt in named:
            ws = cmd.words
            if depth < DEPTH and any(os.path.basename(w.text) == "env" for w in ws[:3]):   # env -S "cmd args" runs the string (wrapper option value, so program_of skips it)
                for i, w in enumerate(ws[1:], 1):
                    if ws[i - 1].text in ("-S", "--split-string") and not w.expands:
                        problem = scan(w.text, top, why, depth + 1)
                        if problem:
                            return problem
            if name is None:
                continue
            args = cmd.words[nxt:]
            if name in ("pkill", "killall"):
                problem = pkill_problem(args, top, why, name)
                if problem:
                    return problem
                operand_kill = operand_kill or any(w.expands for w in args)   # pkill -P $(pgrep …): the substitution fills a pid list that reaches a kill
            elif name == "kill":
                if any(re.fullmatch(r"[+-]?0+", o) or (o.startswith("-") and len(o) > 1) for o in kill_operands(args)):
                    return GROUP
                operand_kill = operand_kill or any(w.expands for w in args)
            elif name in SHELLS or name == "eval":
                for w in args:   # a -c string, an eval argument: read as a script of its own
                    if (name == "eval" or " " in w.text or "\n" in w.text) and depth < DEPTH and not w.text.startswith("-"):
                        problem = scan(w.text, top, why, depth + 1)
                        if problem:
                            return problem
                for body in cmd.bodies:
                    problem = scan(body, top, why, depth + 1) if depth < DEPTH else None
                    if problem:
                        return problem
        if any(xargs_runs_kill(c) for c, _, _ in named):   # `… | xargs kill`: every source in this pipeline feeds a kill
            feeders += [(n, c, nxt, group) for c, n, nxt in named if n in SOURCES]
        elif any(n == "read" for _, n, _ in named):         # `… | while read p; do kill $p; done`: counts once some kill takes an expansion
            feeders += [(n, c, nxt, group) for c, n, nxt in named if n in SOURCES]
    if operand_kill:
        for script in nested:
            for group in pipelines(cg.tokenize(script)[0]):
                feeders += [(n, c, nxt, group) for c, n, nxt in [(c,) + program_of(c) for c in group] if n in SOURCES]
    for name, cmd, nxt, group in feeders:
        if not source_ok(name, cmd, nxt, group, top):
            return PIDS.format(src=f"`{name}`")
    return None


def check(command, cwd):
    """Refusal text for a shell command, or None."""
    if not WORDS.search(command):
        return None
    top, why = cg.own_worktree(cwd)
    try:
        toks, _ = cg.tokenize(command)
        if top and not any(program_of(c)[0] in ("cd", "pushd", "popd") for c in cg.commands(toks)[0]):   # $PWD is the hook's cwd only when nothing in the command moves
            here = os.path.realpath(cwd)
            command = re.sub(r"\$\{PWD\}|\$PWD(?![A-Za-z0-9_])|\$\(\s*pwd\s*\)|`\s*pwd\s*`", lambda m: here, command)
        return scan(command, top, why)
    except (cg.ParseError, RecursionError):   # not readable: refuse only when it names the programs as words
        return NAME.format(what="（这条命令解析不了，其中有 pkill/killall/kill）") if WORDS.search(cg.bare(command)) else None


def decide(payload, command):
    """(exit code, stdout, stderr) for a command the production gate has already read out of the hook input."""
    reason = check(command, payload.get("cwd"))
    return (2, "", reason + "\n") if reason else (0, "", "")


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        from gates_selftest import pkill_gate_test as self_test
        sys.exit(self_test())
    print("用法：pkill_gate.py --self-test", file=sys.stderr)
    sys.exit(1)
