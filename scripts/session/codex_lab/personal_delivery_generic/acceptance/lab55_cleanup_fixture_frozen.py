"""Hosted #55 child fixture: actual mq CLI/storage; local-only proof/write ports.

This file is a test helper, never an operational queue command. Only its new
TemporaryDirectory Lab-shaped checkout is writable. No remote call is made.
"""
import builtins
import fcntl
import hashlib
import io
import json
import os
from pathlib import Path
import sqlite3
import subprocess
import sys
import types

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import mq
import mq_lab_operator
import mq_queue


def read_json(path):
    return json.loads(Path(path).read_text())


def git(repository, *args):
    return subprocess.check_output(["git", "-C", str(repository), *args], text=True,
                                   stderr=subprocess.PIPE, timeout=20).strip()


def append(path, row):
    with Path(path).open("a", encoding="utf-8") as handle:
        handle.write(json.dumps(row, sort_keys=True) + "\n")
        handle.flush()
        os.fsync(handle.fileno())


def snapshot(root):
    root = Path(root)
    settings = read_json(root / "fixture.json")
    repository = Path(settings["repository"])
    directory = repository / ".git/merge-queue"
    path = directory / "releases.sqlite3"
    rows = None
    if path.is_file():
        try:
            with sqlite3.connect(path.as_uri() + "?mode=ro", uri=True) as db:
                rows = db.execute("select * from releases order by number").fetchall()
        except sqlite3.DatabaseError:
            rows = "corrupt"
    queue_bytes = (directory / "merge.queue").read_bytes()
    heads_bytes = (directory / "merge.queue.heads").read_bytes()
    return dict(queue=queue_bytes.decode(), heads=heads_bytes.decode(), rows=rows,
                queue_sha256=hashlib.sha256(queue_bytes).hexdigest(),
                heads_sha256=hashlib.sha256(heads_bytes).hexdigest(),
                database_sha256=hashlib.sha256(path.read_bytes()).hexdigest() if path.exists() else None,
                main=git(repository, "rev-parse", "refs/heads/main"),
                refs=git(repository, "for-each-ref", "--format=%(refname) %(objectname)"))


def assert_merge_lock(directory):
    with (directory / "merge.queue.merge-lock").open("a") as handle:
        try:
            fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            return
        raise RuntimeError("LAB55_CAPABILITY: canonical merge lock not held")


class OfflinePorts:
    def __init__(self, root, mode):
        self.root, self.mode = Path(root), mode
        self.settings = read_json(self.root / "fixture.json")
        self.repository = Path(self.settings["repository"])
        self.directory = self.repository / ".git/merge-queue"

    def note(self, operation, **fields):
        append(self.root / "ports.jsonl", dict(operation=operation, **fields))

    def load(self, number):
        self.note("load", number=number)
        if self.mode == "recover":
            raise RuntimeError("LAB55: no old approval may grant new recovery authority")
        return dict(self.settings["intents"][str(number)], repo_id=1449415666, mode="normal")

    def verify(self, intent, approval):
        self.note("verify", intent=intent)
        if self.mode == "recover":
            raise RuntimeError("LAB55: recovery must not re-run release authorization")
        expected = self.settings["intents"][str(intent["number"])]
        if any(expected.get(k) != v for k, v in intent.items()):
            raise RuntimeError("LAB55: wrong synthetic intent")
        if git(self.repository, "rev-parse", "refs/heads/main") != expected["base"]:
            raise RuntimeError("LAB55: synthetic current base moved")
        git(self.repository, "cat-file", "-e", expected["head"] + "^{commit}")
        return dict(expected, action="eligible", reason="isolated-local-Git-proof-not-GitHub-admission",
                    release_key="lab55-ordinary-" + str(intent["number"]))

    def writer(self, authorize):
        self.note("writer")

        def send(intent):
            self.note("send-attempt", intent=intent)
            if self.mode == "recover" or authorize(dict(intent)) is not True:
                raise RuntimeError("LAB55: forbidden new recovery send")
            assert_merge_lock(self.directory)
            with sqlite3.connect((self.directory / "releases.sqlite3").as_uri() + "?mode=ro", uri=True) as db:
                actual = db.execute("select status,head,base,target,mode,merge_sha from releases where number=?",
                                    (intent["number"],)).fetchone()
            if actual != ("reserved", intent["head"], intent["base"], "main", "ordinary", None):
                raise RuntimeError("LAB55_CAPABILITY: reservation not independently committed before write")
            if git(self.repository, "rev-parse", "refs/heads/main") != intent["base"]:
                raise RuntimeError("LAB55: expected-base mismatch")
            # This is the sole write-port replacement: actual local Git merge,
            # never GitHub or production push. Readback verifies its real graph.
            git(self.repository, "merge", "--no-ff", "--no-edit", intent["head"],
                "-m", "Lab55 isolated merge " + str(intent["number"]))
            self.note("send-completed", merge_sha=git(self.repository, "rev-parse", "HEAD"))

        def readback(intent):
            self.note("readback", intent=intent)
            main = git(self.repository, "rev-parse", "refs/heads/main")
            parents = git(self.repository, "show", "-s", "--format=%P", main).split()
            exact = parents == [intent["base"], intent["head"]]
            return dict(intent, merged=exact, target_contains_merge=exact, merge_sha=main)

        return types.SimpleNamespace(send=send, readback=readback)


def main():
    root = Path(sys.argv[1]).resolve(strict=True)
    command, number, mode, fault = sys.argv[2], int(sys.argv[3]), sys.argv[4], sys.argv[5]
    settings = read_json(root / "fixture.json")
    repository = Path(settings["repository"]).resolve(strict=True)
    if (repository.parent != root or repository.name != "forcitis-one-psa-codex-lab"
            or root.name[:6] != "lab55-" or not (repository / ".git").is_dir()):
        raise RuntimeError("LAB55_CAPABILITY: only the disposable Lab-shaped Git repository is allowed")
    os.chdir(repository)
    directory = repository / ".git/merge-queue"
    state = dict(drop=False, fired=False)
    original_open, original_io_open, original_fdopen, original_write = builtins.open, io.open, os.fdopen, os.write

    def checkpoint(kind):
        assert_merge_lock(directory)
        def held(suffix):
            with (directory / ("merge.queue" + suffix)).open("a") as stream:
                try:
                    fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
                except BlockingIOError:
                    return True
                fcntl.flock(stream, fcntl.LOCK_UN)
                return False
        fifo_locked, heads_locked = held(".lock"), held(".heads-lock")
        if fifo_locked or heads_locked != (kind == "partial-heads"):
            raise RuntimeError("LAB55_CAPABILITY: cut point has wrong inner lock state")
        value = snapshot(root)
        record = next((r for r in value["rows"] if r[0] == number), None)
        if record is None or record[1] != "confirmed" or record[6] != value["main"]:
            raise RuntimeError("LAB55_CAPABILITY: cut point is not after durable confirmation")
        parents = git(repository, "show", "-s", "--format=%P", value["main"]).split()
        if parents != [record[3], record[2]]:
            raise RuntimeError("LAB55_CAPABILITY: confirmed record lacks exact local merge graph")
        if str(number) in value["queue"].splitlines():
            raise RuntimeError("LAB55_CAPABILITY: FIFO remove not completed")
        marker = root / (kind + ".json")
        with original_open(marker, "w", encoding="utf-8") as handle:
            json.dump(dict(kind=kind, lock_held=True, fifo_lock_held=fifo_locked,
                           heads_lock_held=heads_locked, snapshot=value), handle, indent=2)
            handle.flush()
            os.fsync(handle.fileno())
        descriptor = os.open(root, os.O_RDONLY | os.O_DIRECTORY)
        os.fsync(descriptor)
        os.close(descriptor)

    def trace(frame, event, arg):
        if frame.f_code is mq_queue.Queue.drop_head.__code__ and frame.f_locals.get("n") == number:
            if event == "call":
                state["drop"] = True
            elif event == "return":
                state["drop"] = False
        if (fault == "after-remove" and event == "return"
                and frame.f_code is mq_queue.Queue.remove.__code__
                and number in frame.f_locals.get("prs", ())):
            checkpoint("after-remove")
            os._exit(73)
        return trace

    def in_queue_directory(handle):
        try:
            return Path(os.readlink("/proc/self/fd/" + str(handle.fileno()))).parent == directory
        except (OSError, AttributeError):
            return False

    def partial_write(handle, data, write):
        if fault == "partial-heads" and state["drop"] and not state["fired"] and in_queue_directory(handle):
            state["fired"] = True
            # Real partial bytes, flush/fsync, immediate process death. For an
            # atomic replacement this damages only its new temporary file.
            write(data[:max(1, len(data) // 3)])
            handle.flush()
            os.fsync(handle.fileno())
            checkpoint("partial-heads")
            os._exit(74)
        return write(data)

    class WriteObserver:
        def __init__(self, raw):
            self.raw = raw

        def __getattr__(self, name):
            return getattr(self.raw, name)

        def __enter__(self):
            self.raw.__enter__()
            return self

        def __exit__(self, *args):
            return self.raw.__exit__(*args)

        def write(self, data):
            return partial_write(self.raw, data, self.raw.write)

    def observe(opener):
        def wrapped(*args, **kwargs):
            raw = opener(*args, **kwargs)
            mode_value = kwargs.get("mode", args[1] if len(args) > 1 else "r")
            return WriteObserver(raw) if state["drop"] and "w" in mode_value else raw
        return wrapped

    def observed_write(fd, data):
        class Descriptor:
            def fileno(self):
                return fd

            def flush(self):
                pass
        return partial_write(Descriptor(), data, lambda part: original_write(fd, part))

    # No queue/journal/runtime result is replaced. Fault observation only.
    builtins.open = observe(original_open)
    io.open = observe(original_io_open)
    os.fdopen = observe(original_fdopen)
    os.write = observed_write
    sys.settrace(trace)
    mq_lab_operator.ProductionPorts = lambda common, sh: OfflinePorts(root, mode)
    mq.main([command, str(number)])


if __name__ == "__main__":
    main()
