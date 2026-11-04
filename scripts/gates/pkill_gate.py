#!/usr/bin/python3
"""pkill 门（PreToolUse：Bash、Monitor、终端面板、preview_start 启动的命令；#1311）：共享构建机上，会话不得用没有范围的名字去杀进程。

起因：PR #1288 的 CI 因别的会话跑了 `pkill -f failsafe`，把自托管 runner 上的 failsafe JVM 一并杀了（退出码 143，没有测试失败）。
拒绝：
  · pkill、killall 的模式不含本会话 worktree 的绝对路径（killall 只认进程名，永远拒；pkill -x 同理）；模式里有 | 或 ( 的拒（`failsafe|/本路径` 会放过别人的进程）；
  · kill 0、kill -1、kill -- -<pgid>（整个进程组或用户的全部进程）；
  · kill（含 xargs kill）所用的进程号来自没有范围的 pgrep / ps / pidof / jps：同一段脚本（含 $(…)、反引号、管道另一端）里的这类命令没有一个词含本 worktree 路径。
放行：模式含本 worktree 绝对路径的 pkill -f / pgrep -f（$PWD、${PWD}、$(pwd) 先换成钩子输入的 cwd；命令里有 cd、pushd 时不换）；pkill -P <pid>、pkill -F <文件>；
kill 一个字面 pid、$变量、$(cat 文件)（自己记录的 pid）；只在文本里出现的字样（提交信息、grep 的参数、echo）。
本 worktree = 钩子输入 cwd 的 git 顶层，须在 .claude/worktrees/ 下（codex_gate.own_worktree；不用 CLAUDE_PROJECT_DIR：app 起的 worktree 会话里它指向主检出）；cwd 在主检出或别处时没有本路径，任何 pkill 都拒。
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
WRAPPERS = {"sudo", "env", "nohup", "time", "timeout", "gtimeout", "nice", "command", "builtin", "exec", "stdbuf", "caffeinate", "setsid", "ionice", "taskpolicy", "xargs"}
SHELLS = {"sh", "bash", "zsh", "dash", "ksh"}
DURATION = re.compile(r"\d+(\.\d+)?[smhd]?")
SIGNAL = re.compile(r"-([0-9]+|[A-Z][A-Z0-9]+)")
ARG_FLAGS = set("PUugGstFc")                                    # pkill short flags that take a value

HOW = ("；限定到本会话 worktree：`pkill -f \"$PWD/backend/target\"`（模式须含本 worktree 的绝对路径、不含 | 和 (），或只杀自己记录的 pid（`kill $PID`、`pkill -P $PID`、`pkill -F pidfile`）。"
       "原因：这台共享构建机上的 CI runner 与别的会话在同一个用户下，没有范围的名字会杀到它们（#1311：`pkill -f failsafe` 杀了 #1288 的 CI）")
NAME = "拒绝：{what}按名字杀进程，没有本 worktree 的路径作范围" + HOW
PIDS = "拒绝：kill 的进程号来自没有范围的 {src}（同一段命令里没有含本 worktree 路径的词）" + HOW
GROUP = "拒绝：kill 0、kill -1、kill -- -<进程组号> 会杀掉整个进程组或本用户的所有进程（含这台共享构建机上的 CI runner）" + HOW
PLACE = "拒绝：{what}，而当前目录不在会话 worktree（.claude/worktrees/ 下）里，没有可作范围的路径（{why}）" + HOW


def inner_program(words, k):
    """Index of the program a wrapper runs (options, assignments and a timeout duration skipped), or None."""
    while k < len(words):
        t = words[k].text
        if t.startswith("-") or cg.ASSIGN.match(t) or DURATION.fullmatch(t):
            k += 1
        else:
            return k
    return None


def program_of(cmd):
    """(lower-case program name, index of its first argument, via xargs) after wrappers; name None when it expands or is empty."""
    prog, k = cg.program(cmd)
    via_xargs = False
    for _ in range(6):
        if prog is None or prog.expands:
            return None, k, via_xargs
        name = os.path.basename(prog.text.rstrip("/")).lower()
        if name not in WRAPPERS:
            return name, k, via_xargs
        via_xargs = via_xargs or name == "xargs"
        j = inner_program(cmd.words, k)
        if j is None:
            return None, k, via_xargs
        prog, k = cmd.words[j], j + 1
    return None, k, via_xargs


def scoped(text, top):
    return bool(top) and re.search(re.escape(top) + r"(/|$)", text) is not None and "|" not in text and "(" not in text   # not a sibling that shares the prefix (…/w1 vs …/w10)


def pkill_problem(cmd, args, top, why, name):
    """Refusal text for one pkill/killall/pgrep command, or None. pgrep is judged by its caller (only when it feeds a kill)."""
    what = f"`{name}` "
    if name == "killall":
        return NAME.format(what=what)
    pattern, pid_scoped, k = None, False, 0
    while k < len(args):
        t = args[k].text
        if t == "--":
            pattern = args[k + 1].text if k + 1 < len(args) else pattern
            break
        if t.startswith("--"):
            pid_scoped = pid_scoped or t in ("--parent", "--pidfile")
            k += 2 if t in ("--parent", "--pidfile") else 1
        elif SIGNAL.fullmatch(t):   # -9, -KILL, -HUP: a signal, not a flag cluster
            k += 1
        elif t.startswith("-") and len(t) > 1:
            for i, c in enumerate(t[1:], 1):
                if c in ARG_FLAGS:
                    pid_scoped = pid_scoped or c in "PF"
                    if i == len(t) - 1:
                        k += 1
                    break
            k += 1
        else:
            pattern = t
            k += 1
    if pattern is None:
        return None if pid_scoped else NAME.format(what=what)
    if top is None:
        return PLACE.format(what=what, why=why)
    return None if scoped(pattern, top) else NAME.format(what=what)


def scan(text, top, why, cwd, depth=0):
    """Refusal text for the first problem in a shell script text, or None."""
    toks, nested = cg.tokenize(text)
    cmds, _ = cg.commands(toks)
    for script in nested:   # $(…) and backticks: what they run is judged on its own, and below as a pid source for a kill
        if depth < DEPTH:
            problem = scan(script, top, why, cwd, depth + 1)
            if problem:
                return problem
    kills, sources = False, []
    sub = [(n, c) for script in nested for c, n in sub_sources(script)]
    for cmd in cmds:
        name, nxt, via_xargs = program_of(cmd)
        if name is None:
            continue
        args = cmd.words[nxt:]
        if name in ("pkill", "killall"):
            problem = pkill_problem(cmd, args, top, why, name)
            if problem:
                return problem
        elif name == "kill":
            kills = True
            texts = [w.text for w in args]
            tail = texts[texts.index("--") + 1:] if "--" in texts else []
            if "0" in texts or "-1" in texts or any(t.startswith("-") and len(t) > 1 for t in tail):   # kill 0 (the process group), kill -1 (every process), kill -- -<pgid> (a group)
                return GROUP
        elif name in SHELLS or name == "eval":
            for w in args:   # a -c string, an eval argument: read as a script of its own
                if (name == "eval" or " " in w.text or "\n" in w.text) and depth < DEPTH and not w.text.startswith("-"):
                    problem = scan(w.text, top, why, cwd, depth + 1)
                    if problem:
                        return problem
            for body in cmd.bodies:
                problem = scan(body, top, why, cwd, depth + 1) if depth < DEPTH else None
                if problem:
                    return problem
        if name in SOURCES:
            sources.append((name, cmd))
    if kills and sources + sub:   # a pid list from a pid source in this script (or in a substitution of it) needs some word carrying the worktree path (a pgrep -f pattern, a grep after ps)
        words = [w for cmd in cmds for w in cmd.words] + [w for script in nested for c in cg.commands(cg.tokenize(script)[0])[0] for w in c.words]
        if not any(scoped(w.text, top) for w in words):
            return PIDS.format(src=f"`{(sources + sub)[0][0]}`")
    return None


def sub_sources(script):
    toks, _ = cg.tokenize(script)
    out = []
    for cmd in cg.commands(toks)[0]:
        name, _, _ = program_of(cmd)
        if name in SOURCES:
            out.append((cmd, name))
    return out


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
        return scan(command, top, why, cwd)
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
