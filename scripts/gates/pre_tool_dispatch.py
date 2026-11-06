#!/usr/bin/python3
"""PreToolUse 分发（Bash、Monitor、终端面板）：挂生产写门 prod_write_gate（底线 4，生产只读）、codex 门 codex_gate（只放 codex exec 工作区沙箱一种用法，#1180）、pkill 门 pkill_gate（不许无范围地杀进程，#1311）和开 PR 门 pr_open_gate（改后端运行代码的分支开 PR 前核验 Codex 初稿，#1561；它失败放行，不进失败关闭的预筛）。

只看跑 shell 命令的工具（门的 COMMAND_TOOLS）；其他工具（Agent、Read…）直接放行。先过 codex 门（它自己解析命令文本，不靠预筛），再过 pkill 门 pkill_gate（共享构建机上不许无范围地杀进程，#1311；同样自己解析，失败放行），再用生产写门自己的保守预筛 could_concern（只在门必然放行时才跳过），命中再在本进程里调门的 decide。
钩子输入读不懂（含 Bash 而 command 缺失或不是字符串）退出码 2（拒绝）。门导入失败、运行出错、DEADLINE 秒内没有结论，退出码 3 并给一句中文原因：
由 settings.json 的挂载入口接手，跑不依赖门的保守预筛，命中才拒、未命中放行并告警（定稿 H4 安全门失败关闭、其余放行；外部复核 C3）；预筛见 codex 的执行用法或看不出是否执行也拒，只出现在文本里（提交信息、grep 的参数…）的放行（codex 门坏了，codex 失败关闭，#1180；#1206）。
入口直接运行时 3 对 PreToolUse 是「不拦」，所以只经挂载入口用。挂载超时 120 秒在 Claude Code 里也算「不拦」，门内 git 卡住时靠时限先交给预筛（O7 终审建议 1）。
到点抛的 Overdue 是 BaseException 而不是 TimeoutError（OSError 子类）：门里 `except OSError`／`except Exception` 接不住它，不会吞掉时限（O7 修终审 M1）。
退出码 2 + 标准错误 = 拒绝；0 = 放行（门的告警以 JSON additionalContext 写标准输出）；3 = 门出错（见上）。
用法：pre_tool_dispatch.py  |  pre_tool_dispatch.py --self-test（自测在 gates_selftest.py 的 pre_tool_dispatch 段，O8 合一）
"""
import json
import os
import signal
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
FAIL = "生产写门出错（{}；codex 门出错同此处理），交挂载入口的保守预筛判定（codex 的执行用法与看不出是否执行的命令拒，文本里的字样放行）；请把报错转 one-ops。"
DEADLINE = 100


class Overdue(BaseException):
    pass


def overdue(*_):
    raise Overdue(f"超过 {DEADLINE} 秒没有结论")


def load_gate():
    sys.path.insert(0, HERE)
    import prod_write_gate
    return prod_write_gate


def load_codex():
    sys.path.insert(0, HERE)
    import codex_gate
    return codex_gate


def load_pkill():
    sys.path.insert(0, HERE)
    import pkill_gate
    return pkill_gate


def load_pr_open():
    sys.path.insert(0, HERE)
    import pr_open_gate
    return pr_open_gate


def dispatch(raw, gate_loader=load_gate, codex_loader=load_codex, pkill_loader=load_pkill, pr_open_loader=load_pr_open):
    """(exit code, stdout, stderr) for the hook input text."""
    try:
        payload = json.loads(raw)
    except ValueError:
        payload = None
    if not isinstance(payload, dict):
        return 2, "", "pre_tool_dispatch：钩子输入不是 JSON 对象，生产写门无法判断，按拒绝处理。\n"
    gate = gate_loader()
    command, refusal = gate.command_of(payload)
    if command is None:
        return (2, "", refusal) if refusal else (0, "", "")
    code, out, err = codex_loader().decide(payload, command)
    if code:
        return code, out, err
    code, out, err = pkill_loader().decide(payload, command)
    if code:
        return code, out, err
    try:   # the PR-opening gate fails open (#1561): its own failure never stops a command; the codex and production gates around it are the ones that fail closed
        code, warning, err = pr_open_loader().decide(payload, command)
    except Exception:
        code, warning, err = 0, "", ""
    if code:
        return code, "", err
    if not gate.could_concern(command):
        return 0, warning, ""
    code, out, err = gate.decide(payload)
    return code, out or warning, err


def main(gate_loader=load_gate, codex_loader=load_codex, pkill_loader=load_pkill, pr_open_loader=load_pr_open):
    signal.signal(signal.SIGALRM, overdue)
    signal.alarm(DEADLINE)
    try:
        code, out, err = dispatch(sys.stdin.read(), gate_loader, codex_loader, pkill_loader, pr_open_loader)
    except (Exception, Overdue) as error:
        print(FAIL.format(f"{error.__class__.__name__}: {error}"), file=sys.stderr)
        return 3
    finally:
        signal.alarm(0)
    sys.stdout.write(out)
    sys.stderr.write(err)
    return code


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        sys.path.insert(0, HERE)
        from gates_selftest import pre_tool_dispatch_test as self_test
        sys.exit(self_test())
    sys.exit(main())
