#!/usr/bin/python3
"""PreToolUse 分发（Bash）：只挂生产写门 prod_write_gate（底线 4，生产只读）。

只看 Bash；其他工具（Agent、Read…）直接放行。先用门自己的保守预筛 could_concern（只在门必然放行时才跳过），命中再在本进程里调门的 decide。
安全门失败关闭：钩子输入读不懂、门导入失败或运行出错，一律退出码 2 并给一句中文原因（崩溃的退出码 1 对 PreToolUse 是「不拦」）。
退出码 2 + 标准错误 = 拒绝；0 = 放行（门的告警以 JSON additionalContext 写标准输出）。
用法：pre_tool_dispatch.py  |  pre_tool_dispatch.py --self-test（自测在 pre_tool_dispatch_selftest.py，O8 合并）
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
FAIL = "生产写门出错（{}），无法确认这条命令不碰生产，按拒绝处理；请把报错转 one-ops。"


def load_gate():
    sys.path.insert(0, HERE)
    import prod_write_gate
    return prod_write_gate


def dispatch(raw, gate_loader=load_gate):
    """(exit code, stdout, stderr) for the hook input text."""
    try:
        payload = json.loads(raw)
    except ValueError:
        payload = None
    if not isinstance(payload, dict):
        return 2, "", "pre_tool_dispatch：钩子输入不是 JSON 对象，生产写门无法判断，按拒绝处理。\n"
    if payload.get("tool_name", "Bash") != "Bash":
        return 0, "", ""
    tool_input = payload.get("tool_input")
    command = tool_input.get("command") if isinstance(tool_input, dict) else None
    if not isinstance(command, str):
        return 0, "", ""
    gate = gate_loader()
    if not gate.could_concern(gate.plain(command)):
        return 0, "", ""
    return gate.decide(payload)


def main(gate_loader=load_gate):
    try:
        code, out, err = dispatch(sys.stdin.read(), gate_loader)
    except Exception as error:
        print(FAIL.format(f"{error.__class__.__name__}: {error}"), file=sys.stderr)
        return 2
    sys.stdout.write(out)
    sys.stderr.write(err)
    return code


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        sys.path.insert(0, HERE)
        from pre_tool_dispatch_selftest import self_test
        sys.exit(self_test())
    sys.exit(main())
