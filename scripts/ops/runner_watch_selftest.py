#!/usr/bin/env python3
"""runner_watch.sh 与 runner_watch_install.sh 的自测（卡 #736 第 9 项）。全在临时目录做：假 launchctl、osascript 放在 PATH 前面，HOME 指向临时目录，不碰真实服务。
- 巡检：都在运行不动；有一台不在运行就 kickstart，拉起了不告警；拉不起写 alert.log、弹通知、退出 1；有 pause 文件什么都不做。
- 安装：给运行器 plist 加 KeepAlive=true、ThrottleInterval=30，原有键不丢、plutil 合格，逐台 bootout 再 bootstrap；已加过的不重载；正在跑作业的跳过并退出 3；巡检 plist 装好、__HOME__ 已替换。
用法：runner_watch_selftest.py（无参数，退出 0 为通过）"""
import os
import plistlib
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
LABEL = "actions.runner.FORCITIS-forcitis-one-psa.mac-one-{}"
FAKE_LAUNCHCTL = """#!/bin/bash
echo "$*" >> "$FAKE_DIR/calls"
label="${2##*/}"; [ "$1" = bootstrap ] && label="$(basename "$3" .plist)"
mkdir -p "$FAKE_DIR/tearing" "$FAKE_DIR/unloaded"
case "$1" in
  print)  # bootout is asynchronous as in launchd: the first look after it still finds the service, the next one does not
    if [ -e "$FAKE_DIR/tearing/$label" ]; then rm "$FAKE_DIR/tearing/$label"; touch "$FAKE_DIR/unloaded/$label"
    elif [ -e "$FAKE_DIR/unloaded/$label" ]; then exit 113; fi
    [ -e "$FAKE_DIR/up/$label" ] && echo "	state = running" || echo "	state = not running"; exit 0 ;;
  bootout) touch "$FAKE_DIR/tearing/$label"; exit 0 ;;
  bootstrap) [ -e "$FAKE_DIR/tearing/$label" ] && { echo "Bootstrap failed: 5: Input/output error" >&2; exit 5; }; rm -f "$FAKE_DIR/unloaded/$label"; exit 0 ;;
  kickstart) [ -e "$FAKE_DIR/fixable/$label" ] && touch "$FAKE_DIR/up/$label"; exit 0 ;;
esac
exit 0
"""
RUNNER_PLIST = {"Label": "", "ProgramArguments": ["/x/runsvc.sh"], "RunAtLoad": True, "ProcessType": "Interactive", "SessionCreate": True}
FAILS = []


def check(name, ok, detail=""):
    print(("通过 " if ok else "失败 ") + name)
    if not ok:
        FAILS.append(name)
        print("    " + str(detail)[:600])


def world(tmp):
    fake, home = Path(tmp, "fake"), Path(tmp, "home")
    for d in (fake / "bin", fake / "up", fake / "fixable", home / "Library/LaunchAgents"):
        d.mkdir(parents=True, exist_ok=True)
    (fake / "bin/launchctl").write_text(FAKE_LAUNCHCTL)
    (fake / "bin/osascript").write_text('#!/bin/bash\necho "$*" >> "$FAKE_DIR/notified"\n')
    for f in ("launchctl", "osascript"):
        os.chmod(fake / "bin" / f, 0o755)
    env = {"PATH": f"{fake}/bin:/usr/bin:/bin:/usr/sbin:/sbin", "HOME": str(home), "FAKE_DIR": str(fake), "RUNNER_WATCH_WAIT": "0"}
    return fake, home, env


def watch_tests():
    with tempfile.TemporaryDirectory() as tmp:
        fake, home, env = world(tmp)
        labels = " ".join(LABEL.format(n) for n in (1, 2))
        env = {**env, "RUNNER_LABELS": labels}
        run = lambda: subprocess.run(["bash", str(HERE / "runner_watch.sh")], env=env, capture_output=True, text=True)  # noqa: E731
        state = home / ".modernize/psa/ops/runner-watch"
        for n in (1, 2):
            (fake / "up" / LABEL.format(n)).touch()
        r = run()
        check("都在运行：不 kickstart、不告警，退出 0", r.returncode == 0 and "kickstart" not in (fake / "calls").read_text() and not (state / "alert.log").exists(), r)
        (fake / "up" / LABEL.format(2)).unlink()
        (fake / "fixable" / LABEL.format(2)).touch()
        r = run()
        calls = (fake / "calls").read_text()
        check("一台停了、kickstart 拉起：不告警，记 watch.log，退出 0", r.returncode == 0 and f"kickstart gui/{os.getuid()}/{LABEL.format(2)}" in calls
              and not (state / "alert.log").exists() and "已拉起" in (state / "watch.log").read_text(), (r, calls))
        (fake / "up" / LABEL.format(2)).unlink()
        (fake / "fixable" / LABEL.format(2)).unlink()
        r = run()
        alert = (state / "alert.log").read_text() if (state / "alert.log").exists() else ""
        notified = (fake / "notified").read_text() if (fake / "notified").exists() else ""
        check("拉不起来：写 alert.log、弹通知、退出 1", r.returncode == 1 and LABEL.format(2) in alert and "mac-one-2" in notified and "CI 运行器离线" in notified, (r, alert, notified))
        (state / "pause").touch()
        (fake / "calls").unlink()
        r = run()
        check("有 pause 文件：什么都不做", r.returncode == 0 and not (fake / "calls").exists(), r)


def install_tests():
    with tempfile.TemporaryDirectory() as tmp:
        fake, home, env = world(tmp)
        agents = home / "Library/LaunchAgents"
        for n in (1, 2):
            with open(agents / f"{LABEL.format(n)}.plist", "wb") as f:
                plistlib.dump({**RUNNER_PLIST, "Label": LABEL.format(n)}, f)
        env = {**env, "RUNNER_NAMES": "1 2 3", "RUNNERS_HOME": str(Path(tmp, "runners"))}
        r = subprocess.run(["bash", str(HERE / "runner_watch_install.sh")], env=env, capture_output=True, text=True)
        ok = True
        for n in (1, 2):
            with open(agents / f"{LABEL.format(n)}.plist", "rb") as f:
                p = plistlib.load(f)
            ok &= p.get("KeepAlive") is True and p.get("ThrottleInterval") == 30 and all(p.get(k) == v for k, v in {**RUNNER_PLIST, "Label": LABEL.format(n)}.items())
        calls = (fake / "calls").read_text()
        uid = os.getuid()
        order = [c.split()[0] for c in calls.splitlines() if "mac-one-1" in c]
        check("运行器 plist 加 KeepAlive、ThrottleInterval，原有键不丢；缺 plist 的 mac-one-3 跳过", r.returncode == 0 and ok and "缺" in r.stderr and "mac-one-3" in r.stderr, (r, calls))
        check("逐台 bootout，等服务真卸下（异步）再 bootstrap，最后是加载状态", order == ["bootout", "print", "print", "bootstrap"] and not any((fake / "unloaded").iterdir()) and not any((fake / "tearing").iterdir()), order)
        watch = (agents / "com.forcitis.runner-watch.plist").read_text()
        check("巡检 plist 装好、__HOME__ 已替换、脚本已复制", "__HOME__" not in watch and f"{home}/.modernize/psa/ops/bin/runner_watch.sh" in watch
              and (home / ".modernize/psa/ops/bin/runner_watch.sh").read_bytes() == (HERE / "runner_watch.sh").read_bytes(), watch[:300])
        (fake / "calls").unlink()
        r = subprocess.run(["bash", str(HERE / "runner_watch_install.sh")], env={**env, "RUNNER_NAMES": "1 2"}, capture_output=True, text=True)
        calls = (fake / "calls").read_text()
        check("重跑：已加过的运行器不重载", r.returncode == 0 and "mac-one-1" not in calls and "mac-one-2" not in calls and "已有" in r.stdout, (r, calls))
        with open(agents / f"{LABEL.format(1)}.plist", "wb") as f:  # svc.sh reinstall drops the keys; this runner is running a job
            plistlib.dump({**RUNNER_PLIST, "Label": LABEL.format(1)}, f)
        busy = subprocess.Popen(["bash", "-c", "exec -a " + str(Path(tmp, "runners/one-1/bin/Runner.Worker")) + " sleep 30"])
        try:
            r = subprocess.run(["bash", str(HERE / "runner_watch_install.sh")], env={**env, "RUNNER_NAMES": "1"}, capture_output=True, text=True)
        finally:
            busy.kill()
        with open(agents / f"{LABEL.format(1)}.plist", "rb") as f:
            p = plistlib.load(f)
        check("正在跑作业的那台：不动、列出、退出 3", r.returncode == 3 and "mac-one-1" in r.stderr and "KeepAlive" not in p, r)


def main():
    watch_tests()
    install_tests()
    print(f"runner_watch 自测：{'全部通过' if not FAILS else f'{len(FAILS)} 项失败'}")
    return 1 if FAILS else 0


if __name__ == "__main__":
    sys.exit(main())
