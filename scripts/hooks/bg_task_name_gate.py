#!/usr/bin/env python3
"""PreToolUse hook (Bash, Agent): a background task's description must read `<任务号或 #PR号> <任务名> (<执行者>)`.

SESSION_PLAYBOOK §5 任务命名与登记「后台任务显示」(intent 2026-10-21-212849): the sidebar and status bar show the description, so a background Bash
(`run_in_background: true`) or any Agent/Task call (sub-agents always run in the background) is refused when its description does not follow the format; the refusal carries examples.
Task id: XXX-MMDD-XXX-NN (two or three capital letters for the lane); non-card tasks (review, spot check, sampling) use the PR number they serve (#528) or the parent task id.
Foreground Bash calls are not checked; Workflow has no description and is not covered. Exit code 2 + stderr = refuse; 0 + no output = allow. Any failure is caught and refuses with exit 2 (an uncaught crash would exit 1, which a hook treats as "do not block").
Usage: bg_task_name_gate.py  |  bg_task_name_gate.py --self-test
"""
import json
import re
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from task_ids import TASK_ID_PATTERN  # noqa: E402

FOREGROUND_TOO = {"Agent", "Task"}  # the Agent tool has no run_in_background parameter: its sub-agents always run in the background
TOOLS = {"Bash"} | FOREGROUND_TOO
FORMAT = re.compile(r"^(?:" + TASK_ID_PATTERN + r"|#\d+) \S.* \(\S.*\)$")
MESSAGE = ("后台任务的 description 须写成 `<任务号或 #PR号> <任务名> (<执行者>)`，侧边栏和状态栏据此显示（SESSION_PLAYBOOK §5 任务命名与登记）。"
           "例：`NAM-1017-GLO-03 [命名·glossary] 登记 NM5 名词 (Codex gpt-6.1-sol)`、`#528 sonnet 复审 (sonnet)`、`ARC-1021-ARC-07 对象转换抽查 (sonnet)`。"
           "当前 description：{current!r}")


def main():
    try:
        payload = json.load(sys.stdin)
    except ValueError:
        print("bg_task_name_gate.py 读不懂钩子输入（不是 JSON），按失败处理", file=sys.stderr)
        return 2
    if not isinstance(payload, dict):
        print("bg_task_name_gate.py 读不懂钩子输入（不是对象），按失败处理", file=sys.stderr)
        return 2
    if payload.get("tool_name") not in TOOLS:
        return 0
    tool_input = payload.get("tool_input")
    if not isinstance(tool_input, dict):
        print("bg_task_name_gate.py 读不懂工具输入（不是对象），按失败处理", file=sys.stderr)
        return 2
    if payload["tool_name"] not in FOREGROUND_TOO and tool_input.get("run_in_background") is not True:
        return 0
    description = tool_input.get("description")
    if not isinstance(description, str) or not FORMAT.fullmatch(description.strip()):
        print(MESSAGE.format(current=description), file=sys.stderr)
        return 2
    return 0


def guarded_main():
    try:
        return main()
    except Exception as error:
        print(f"bg_task_name_gate.py 出错（{error.__class__.__name__}: {error}），按失败处理", file=sys.stderr)
        return 2


def self_test():
    allow = [
        ("Bash", {"command": "x", "description": "NAM-1017-GLO-03 [命名·glossary] 登记 NM5 名词 (Codex gpt-6.1-sol)", "run_in_background": True}),
        ("Bash", {"command": "x", "description": "#528 sonnet 复审 (sonnet)", "run_in_background": True}),
        ("Bash", {"command": "x", "description": "ARC-1021-ARC-07 对象转换抽查 (sonnet)", "run_in_background": True}),
        ("Bash", {"command": "x", "description": "CI-1022-QUA-03 [CI·跨域] 测 commit-msg (DeepSeek flash)", "run_in_background": True}),
        ("Bash", {"command": "x", "description": "随便写", "run_in_background": False}),
        ("Bash", {"command": "x", "description": "随便写"}),
        ("Agent", {"description": "#593 opus 首轮复审 (opus)", "prompt": "p"}),
        ("Task", {"description": "#593 opus 首轮复审 (opus)", "prompt": "p"}),
                ("Read", {"file_path": "x"}),
    ]
    refuse = [
        ("Bash", {"command": "x", "description": "跑测试", "run_in_background": True}),
        ("Bash", {"command": "x", "description": "NAM-1017-GLO-03 登记 NM5 名词", "run_in_background": True}),
        ("Bash", {"command": "x", "description": "NAM-1017-GLO-03 (Codex)", "run_in_background": True}),
        ("Bash", {"command": "x", "description": "NAM-1017-GLO-3 登记 (Codex)", "run_in_background": True}),
        ("Bash", {"command": "x", "description": "#528复审 (sonnet)", "run_in_background": True}),
        ("Bash", {"command": "x", "description": "#528 复审 ()", "run_in_background": True}),
        ("Bash", {"command": "x", "description": "NAM-1017-GLO-03 登记\n(Codex)", "run_in_background": True}),
        ("Bash", {"command": "x", "run_in_background": True}),
        ("Bash", {"command": "x", "description": 5, "run_in_background": True}),
        ("Agent", {"description": "复审 PR", "prompt": "p"}),
        ("Agent", {"description": "Review the diff", "prompt": "p"}),
        ("Agent", {"prompt": "p"}),
        ("Bash", {"command": "x", "description": "ABC-9999-ZZZ-99 假任务 (Codex)", "run_in_background": True}),
    ]
    script = str(Path(__file__).resolve())
    for runner in ([sys.executable, script], [sys.executable, "-I", script]):
        for cases, status in ((allow, 0), (refuse, 2)):
            for tool, tool_input in cases:
                result = subprocess.run(runner, input=json.dumps({"tool_name": tool, "tool_input": tool_input}), capture_output=True, text=True, encoding="utf-8", timeout=10)
                assert result.returncode == status, (runner, tool, tool_input, result)
                assert bool(result.stderr) == (status == 2) and not result.stdout, (tool, tool_input, result)
    for bad in ("not json", "[1]", json.dumps({"tool_name": "Bash", "tool_input": "x"})):
        result = subprocess.run([sys.executable, script], input=bad, capture_output=True, text=True, encoding="utf-8", timeout=10)
        assert result.returncode == 2 and result.stderr, (bad, result)
    print("bg_task_name_gate self-test: ok")
    return 0


if __name__ == "__main__":
    sys.exit(self_test() if sys.argv[1:] == ["--self-test"] else guarded_main())
