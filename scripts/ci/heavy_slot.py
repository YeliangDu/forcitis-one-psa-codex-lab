#!/usr/bin/env python3
"""Runs a heavy command (backend compile or tests) in one of a few machine-wide slots, at background priority.

Usage: heavy_slot.py -- command [args...]      |      heavy_slot.py --self-test
A slot is an advisory kernel lock (flock) on a file under FORCITIS_HEAVY_DIR (default ~/.cache/forcitis-one/heavy-slots); the command inherits the locked
descriptor, so the slot stays taken while any process of the command lives and is freed by the kernel when they all end, however they end.
Environment: FORCITIS_HEAVY_SLOTS (default 2), FORCITIS_HEAVY_WAIT_SECONDS (default 3600; exit 75 when no slot frees up in time), FORCITIS_HEAVY_DIR.
Not for frontend verify: its UI tests time out under background priority on a loaded machine (seen 2026-10-21, load 12 to 35).
"""
import fcntl
import os
import shutil
import signal
import subprocess
import sys
import tempfile
import time
from pathlib import Path

NO_SLOT = 75


def settings():
    try:
        slots = int(os.environ.get("FORCITIS_HEAVY_SLOTS", "2"))
        wait = int(os.environ.get("FORCITIS_HEAVY_WAIT_SECONDS", "3600"))
    except ValueError:
        slots = wait = 0
    if slots < 1 or wait < 0:
        sys.exit("heavy_slot.py: FORCITIS_HEAVY_SLOTS must be a whole number of at least 1 and FORCITIS_HEAVY_WAIT_SECONDS a whole number")
    folder = Path(os.environ.get("FORCITIS_HEAVY_DIR") or Path.home() / ".cache" / "forcitis-one" / "heavy-slots")
    return slots, wait, folder


def claim(slots, folder):
    for number in range(1, slots + 1):
        descriptor = os.open(folder / f"slot{number}", os.O_RDWR | os.O_CREAT, 0o600)
        try:
            fcntl.flock(descriptor, fcntl.LOCK_EX | fcntl.LOCK_NB)
            return descriptor
        except OSError:
            os.close(descriptor)
    return None


def run(command):
    slots, wait, folder = settings()
    try:
        folder.mkdir(parents=True, exist_ok=True)
    except OSError as error:
        sys.exit(f"heavy_slot.py: cannot create {folder}: {error}")
    waited = 0
    descriptor = claim(slots, folder)
    while descriptor is None:
        if waited >= wait:
            print(f"heavy_slot.py: no free slot after {waited}s (all {slots} held); not starting: {' '.join(command)}", file=sys.stderr)
            return NO_SLOT
        if waited % 60 == 0:
            print(f"heavy_slot.py: waiting for a heavy slot ({slots} in use machine-wide), {waited}s so far", file=sys.stderr)
        time.sleep(2)
        waited += 2
        descriptor = claim(slots, folder)
    prefix = ["taskpolicy", "-b"] if shutil.which("taskpolicy") else ["nice", "-n", "10"]
    # own process group: a signal for this script reaches the whole command, not only its first process
    child = subprocess.Popen(prefix + command, pass_fds=(descriptor,), start_new_session=True)

    def forward(number, _frame):
        try:
            os.killpg(child.pid, number)
        except ProcessLookupError:
            pass

    for number in (signal.SIGTERM, signal.SIGINT, signal.SIGHUP):
        signal.signal(number, forward)
    return child.wait()


def self_test():
    work = Path(tempfile.mkdtemp(prefix="heavy_slot_selftest."))
    base_env = {**os.environ, "FORCITIS_HEAVY_DIR": str(work / "slots"), "FORCITIS_HEAVY_SLOTS": "2", "FORCITIS_HEAVY_WAIT_SECONDS": "2"}
    me = [sys.executable, "-I", str(Path(__file__).resolve()), "--"]
    holders = []

    def await_file(path):
        for _ in range(200):
            if path.exists():
                return
            time.sleep(0.1)
        raise AssertionError(f"{path.name} never appeared")

    def hold(name):
        script = f"import os, time; open({str(work / name)!r}, 'w').write(str(os.getpid()))\nwhile os.path.isdir({str(work)!r}) and not os.path.exists({str(work / 'release')!r}): time.sleep(0.1)"
        wrapper = subprocess.Popen(me + [sys.executable, "-c", script], env=base_env)
        holders.append(wrapper)
        await_file(work / name)
        return wrapper

    def third():
        return subprocess.run(me + ["true"], env=base_env, capture_output=True).returncode

    def alive(pid):
        try:
            os.kill(pid, 0)
            return True
        except ProcessLookupError:
            return False

    try:
        assert subprocess.run(me + ["sh", "-c", "exit 7"], env=base_env).returncode == 7, "exit code is passed through"
        assert third() == 0, "a free slot runs the command"

        first, second = hold("a"), hold("b")
        assert third() == NO_SLOT, "a third caller gets no slot while two are held"

        first.kill()
        first.wait()
        assert third() == NO_SLOT, "a SIGKILLed wrapper keeps its slot while its command still runs (the command inherited the lock)"
        os.kill(int((work / "a").read_text()), signal.SIGKILL)
        for _ in range(100):
            if third() == 0:
                break
            time.sleep(0.1)
        else:
            raise AssertionError("the slot is freed once the command is gone")
        (work / "release").write_text("x")
        second.wait()
        holders.clear()

        grandchild = work / "grandchild"
        wrapper = subprocess.Popen(me + ["sh", "-c", f"sleep 60 & echo $! > {grandchild}; wait"], env=base_env)
        await_file(grandchild)
        for _ in range(50):
            if grandchild.read_text().strip():
                break
            time.sleep(0.1)
        pid = int(grandchild.read_text())
        wrapper.terminate()
        try:
            wrapper.wait(timeout=10)
        except subprocess.TimeoutExpired:
            os.kill(pid, signal.SIGKILL)
            raise AssertionError("TERM for the wrapper stops the command")
        for _ in range(100):
            if not alive(pid):
                break
            time.sleep(0.1)
        else:
            os.kill(pid, signal.SIGKILL)
            raise AssertionError("TERM for the wrapper stops the grandchild too")

        for bad in ("0", "x", "-1"):
            assert subprocess.run(me + ["true"], env={**base_env, "FORCITIS_HEAVY_SLOTS": bad}, capture_output=True).returncode != 0, f"slots={bad} is refused"

        stub = work / "bin"
        stub.mkdir()
        (stub / "taskpolicy").write_text(f'#!/bin/sh\necho "$1" > "{work / "policy"}"\nshift\nexec "$@"\n')
        (stub / "taskpolicy").chmod(0o755)
        assert subprocess.run(me + ["true"], env={**base_env, "PATH": f"{stub}:{os.environ['PATH']}"}).returncode == 0
        assert (work / "policy").read_text().strip() == "-b", "the command runs under taskpolicy -b"
    finally:
        (work / "release").write_text("x")
        for holder in holders:
            holder.kill()
        shutil.rmtree(work, ignore_errors=True)
    print("heavy_slot self-test: ok")


if __name__ == "__main__":
    arguments = sys.argv[1:]
    if arguments == ["--self-test"]:
        self_test()
    else:
        if arguments[:1] == ["--"]:
            arguments = arguments[1:]
        if not arguments:
            sys.exit("usage: heavy_slot.py -- command [args...]")
        sys.exit(run(arguments))
