"""Bounded FIFO polling through the existing canonical operator only.

Importing this module starts nothing. No scheduler, heartbeat, admission or
direct writer is provided; an explicit verified mq.py run owns the worker lock.
"""
from contextlib import contextmanager
import fcntl
import json
import math
import os
from pathlib import Path
import signal
import stat
import threading


def run_worker(read_queue, once, emit, sleep, stop, *, interval=10):
    """Reread FIFO every cycle; emit changes and never directly retry writes."""
    if type(interval) not in (int, float) or not math.isfinite(interval) or interval <= 0:
        raise ValueError("Expected a finite positive polling interval")
    interval = min(interval, 10)
    previous = None
    while not stop():
        present, numbers, number = None, None, None
        event = dict(number=None, action="hold", reason="canonical-queue-read-failed")
        try:
            state = read_queue()
            if (not isinstance(state, (tuple, list)) or len(state) != 2
                    or type(state[0]) is not bool or not isinstance(state[1], list)
                    or not all(type(n) is int and n > 0 for n in state[1])
                    or len(set(state[1])) != len(state[1])):
                event["reason"] = "invalid-canonical-queue"
            else:
                present, numbers = state[0], list(state[1])
                if not present:
                    event["reason"] = "canonical-queue-missing"
                elif not numbers:
                    event.update(action="idle", reason="canonical-queue-empty")
                else:
                    number = numbers[0]
                    if stop():
                        return
                    event = dict(number=number, action="hold", reason="canonical-operator-failed")
                    answer = once(number)
                    if (isinstance(answer, dict) and answer.get("action") in ("hold", "confirmed", "skip")
                            and isinstance(answer.get("reason"), str) and answer["reason"].strip()
                            and ("number" not in answer or type(answer["number"]) is int and answer["number"] == number)):
                        event.update(action=answer["action"], reason=answer["reason"])
                        if answer["action"] in ("confirmed", "skip"):
                            merge = answer.get("merge_sha")
                            if isinstance(merge, str) and len(merge) == 40 and all(c in "0123456789abcdef" for c in merge):
                                event["merge_sha"] = merge
                            else:
                                event.update(action="hold", reason="operator-confirmation-missing-merge-proof")
        except Exception:
            # The next bounded cycle rereads the queue and uses once again.
            # Its durable journal alone decides recovery; this loop never sends.
            pass
        if stop():
            return
        event["queued"] = numbers if numbers is not None else []
        fingerprint = (present, tuple(numbers) if numbers is not None else None,
                       event["number"], event["action"], event["reason"], event.get("merge_sha"))
        if fingerprint != previous:
            emit(dict(event))
            previous = fingerprint
        if stop():
            return
        sleep(interval)


@contextmanager
def _runner_lock(directory):
    """Same lock name as mq.py's existing runner, with safe file ownership."""
    directory = Path(directory)
    if directory.resolve(strict=True) != directory or not directory.is_dir():
        raise ValueError("Unverified canonical worker directory")
    info = directory.stat()
    if info.st_uid != os.geteuid() or stat.S_IMODE(info.st_mode) & 0o022:
        raise ValueError("Unsafe canonical worker directory ownership")
    fd = os.open(directory / "mq.runner.lock", os.O_RDWR | os.O_CREAT | os.O_NOFOLLOW | os.O_NONBLOCK, 0o600)
    try:
        info = os.fstat(fd)
        if (not stat.S_ISREG(info.st_mode) or info.st_uid != os.geteuid()
                or info.st_nlink != 1 or stat.S_IMODE(info.st_mode) & 0o022):
            raise ValueError("Unsafe canonical runner lock")
        fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        try:
            yield
        finally:
            fcntl.flock(fd, fcntl.LOCK_UN)
    finally:
        os.close(fd)


def run_live(common_dir, sh):
    """Explicit CLI binding after physical Lab/sole-origin profile validation."""
    from mq_lab import _path, _state
    from mq_lab_operator import ProductionPorts, run_command

    handlers = {}
    stopped = threading.Event()
    try:
        path = _path(common_dir)
        # Missing canonical state is not permission to initialize a queue or
        # create its parent. An initialized empty queue may be watched normally.
        if not _state(path)[0]:
            return dict(action="hold", reason="canonical-queue-missing")
        with _runner_lock(path.parent):
            for signum in (signal.SIGINT, signal.SIGTERM):
                handlers[signum] = signal.signal(signum, lambda _signum, _frame: stopped.set())
            installed = sh(["git", "rev-parse", "HEAD"])
            if installed.returncode != 0:
                raise ValueError("Cannot pin loaded worker source")
            loaded_head = installed.stdout.strip()
            if len(loaded_head) != 40 or any(c not in "0123456789abcdef" for c in loaded_head):
                raise ValueError("Invalid loaded worker head")
            ports = ProductionPorts(common_dir, sh)

            def once(number):
                current = sh(["git", "rev-parse", "HEAD"])
                if current.returncode != 0 or current.stdout.strip() != loaded_head:
                    return dict(action="hold", reason="worker-source-moved-restart-required")
                return run_command("once", number, common_dir, ports)

            run_worker(lambda: _state(_path(common_dir)),
                       once,
                       lambda event: print(json.dumps(event, ensure_ascii=False, sort_keys=True), flush=True),
                       stopped.wait, stopped.is_set)
        return dict(action="idle", reason="canonical-worker-stopped")
    except Exception:
        return dict(action="hold", reason="canonical-worker-unavailable-or-already-running")
    finally:
        for signum, handler in handlers.items():
            signal.signal(signum, handler)
