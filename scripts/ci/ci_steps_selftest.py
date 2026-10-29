#!/usr/bin/env python3
"""ci.yml 的「stop the docker events recorder」一步不得波及运行器自己（卡 #736 第 9 项）。
从 ci.yml 取这一步的 run 原文，在一个新进程组里与一个「替身运行器」进程同组运行：DOCKER_EVENTS_PID 为空、为 0、为 1、不是数字时，替身必须活着；
是记录器的真实 pid 时，记录器被停、替身活着。另跑一遍改前的写法 kill "${DOCKER_EVENTS_PID:-0}"，证明它确实会停掉同组的替身（10-26～10-28 mac-one-1～4 离线的原因）。
用法：ci_steps_selftest.py（无参数，退出 0 为通过）"""
import os
import re
import signal
import subprocess
import sys
import time
from pathlib import Path

import yaml

CI = Path(__file__).resolve().parents[2] / ".github/workflows/ci.yml"
OLD = 'kill "${DOCKER_EVENTS_PID:-0}" 2>/dev/null || true'


def step_run(name):
    lines = CI.read_text(encoding="utf-8").splitlines()
    at = next(i for i, l in enumerate(lines) if l.strip() == f"- name: {name}")
    return next(re.sub(r"^\s*run:\s*", "", l) for l in lines[at + 1:at + 5] if l.strip().startswith("run:"))


def survives(command, pid_value, extra=None):
    """(替身是否还活着, 记录器是否还活着)：替身与 bash 同在新进程组里（同 runsvc.sh 与作业步骤），记录器单独一组。"""
    recorder = subprocess.Popen(["sleep", "60"], start_new_session=True)
    value = str(recorder.pid) if pid_value == "recorder" else pid_value
    script = f"sleep 60 & echo $! > {extra}; sleep 0.2; {command}"
    env = {"PATH": os.environ.get("PATH", "/usr/bin:/bin"), **({} if value is None else {"DOCKER_EVENTS_PID": value})}
    runner = subprocess.Popen(["bash", "-c", script], start_new_session=True, env=env)
    runner.wait(timeout=10)
    time.sleep(0.3)
    stand_in = int(Path(extra).read_text())
    alive = lambda p: subprocess.run(["kill", "-0", str(p)], capture_output=True).returncode == 0 and "Z" not in subprocess.run(["ps", "-o", "stat=", "-p", str(p)], capture_output=True, text=True).stdout  # noqa: E731
    result = (alive(stand_in), alive(recorder.pid))
    for p in (stand_in, recorder.pid):
        try:
            os.kill(p, signal.SIGKILL)
        except OSError:
            pass
    recorder.wait(timeout=5)
    return result


def main():
    text = CI.read_text(encoding="utf-8")
    assert "LIGHT_ALL: ${{ github.event_name == 'push' && '1' || '' }}" in text, "light 步骤推到 main 时须设 LIGHT_ALL=1：按改动挑自测的唯一兜底"
    workflow = yaml.safe_load(text)
    other = "github.event.action == 'labeled' && github.event.label.name != 'queued'"
    assert workflow[True]["pull_request"]["types"] == ["opened", "synchronize", "reopened", "labeled"], "pull_request 只在开、推、重开与贴标签时跑（edited 已无读者）"
    for job, spec in workflow["jobs"].items():
        assert other in str(spec.get("if", "")), f"{job}：非 queued 标签的事件不跑任何作业"
    for job in ("backend", "frontend", "gitleaks"):
        assert other in workflow["jobs"][job]["name"], f"{job}：跳过的必需检查算通过，非 queued 标签的事件须换名报告"
    run = step_run("stop the docker events recorder")
    assert "${DOCKER_EVENTS_PID:-0}" not in run, run
    mark = Path(os.environ.get("TMPDIR", "/tmp")) / f"ci_steps_selftest.{os.getpid()}"
    try:
        for value in (None, "", "0", "1", "abc", "-1"):
            assert survives(run, value, mark)[0], f"DOCKER_EVENTS_PID={value!r}：同组的运行器被停了"
        stand_in, recorder = survives(run, "recorder", mark)
        assert stand_in and not recorder, ("记录器的真实 pid：只停记录器", stand_in, recorder)
        assert not survives(OLD, None, mark)[0], "对照：改前写法在变量为空时停掉同组进程（若此条不红，说明自测复现不了事故）"
    finally:
        mark.unlink(missing_ok=True)
    print("ci_steps self-test: ok（空、0、1、非数字都不波及同组进程；真实 pid 只停记录器；改前写法复现停机）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
