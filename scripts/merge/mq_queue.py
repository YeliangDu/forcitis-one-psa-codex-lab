"""The queue file and the log of the merge queue (mq.py). Every change to the queue file goes through flock and an atomic replace."""
import contextlib
import datetime
import fcntl
import os
import re
import tempfile
import threading
import time

LINE = re.compile(r"^\s*(\d+)(?:\s+after=([\d,]+))?\s*$")   # "<n>" or "<n> after=<m>[,<m>...]": n waits until every m is merged


class Log:
    def __init__(self, path=None, echo=True, now=None):
        self.path, self.echo = path, echo
        self.now = now or (lambda: datetime.datetime.now().astimezone())
        self.lines = []
        self.lock = threading.Lock()
        self.lanes = {}   # pull request -> lane, set when a worker takes it; its events then say which lane they belong to

    def event(self, ev, pr=None, run=None, since=None, detail=""):
        started = since if since is not None else time.monotonic()
        line = f"{self.now().isoformat(timespec='seconds')} {ev} pr=#{pr if pr is not None else '-'} run={run if run else '-'} elapsed={max(0, int(time.monotonic() - started))}s"
        if pr in self.lanes:
            line += f" lane={self.lanes[pr]}"
        line += f" {' '.join(str(detail).split())[:400]}" if detail else ""
        with self.lock:
            self.lines.append(line)
            if self.path:
                with open(self.path, "a", encoding="utf-8") as f:
                    f.write(line + "\n")
            if self.echo:
                print(line, flush=True)


class Queue:
    def __init__(self, path):
        self.path = path
        os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)

    @contextlib.contextmanager
    def locked(self, suffix=".lock"):
        with open(self.path + suffix, "a") as lock:
            fcntl.flock(lock, fcntl.LOCK_EX)
            try:
                yield
            finally:
                fcntl.flock(lock, fcntl.LOCK_UN)

    def merge_lock(self):
        """Held while a merge is decided and done: one merge at a time, whatever lane and process."""
        return self.locked(".merge-lock")

    def _read(self):
        try:
            return open(self.path, encoding="utf-8").read().splitlines()
        except FileNotFoundError:
            return []

    def _write(self, lines):
        fd, tmp = tempfile.mkstemp(dir=os.path.dirname(os.path.abspath(self.path)), prefix=".queue-")
        try:
            with os.fdopen(fd, "w", encoding="utf-8") as f:
                f.write("".join(l + "\n" for l in lines))
                f.flush()
                os.fsync(f.fileno())
            os.replace(tmp, self.path)
        except BaseException:
            with contextlib.suppress(OSError):
                os.unlink(tmp)
            raise

    def numbers(self):
        with self.locked():
            return [int(m.group(1)) for l in self._read() if (m := LINE.match(l))]

    def deps(self, n):
        """The pull requests that must be merged before n is started."""
        with self.locked():
            for l in self._read():
                if (m := LINE.match(l)) and int(m.group(1)) == n:
                    return {int(x) for x in (m.group(2) or "").split(",") if x}
        return set()

    @staticmethod
    def _entry(n, after):
        return f"{n} after={','.join(str(x) for x in sorted(after))}" if after else str(n)

    def enqueue(self, *prs, after=()):
        """Queue prs (once each); `after`: pull requests each of them waits for. Already queued with a new dependency: the dependencies are added. A self-dependency or a cycle is refused (ValueError)."""
        after = set(after)
        added = []
        with self.locked():
            lines = self._read()
            graph = {int(m.group(1)): {int(x) for x in (m.group(2) or "").split(",") if x} for l in lines if (m := LINE.match(l))}
            for n in prs:
                if n in after:
                    raise ValueError(f"#{n} cannot wait for itself")
                merged = graph.get(n, set()) | after
                if any(self._reaches(graph, m, n) for m in merged):
                    raise ValueError(f"#{n} after {sorted(after)} would make a dependency cycle")
                graph[n] = merged
            for n in prs:
                at = next((i for i, l in enumerate(lines) if (m := LINE.match(l)) and int(m.group(1)) == n), None)
                if at is None:
                    lines.append(self._entry(n, after))
                    added.append(n)
                elif after:
                    lines[at] = self._entry(n, graph[n])
            self._write(lines)
        return added

    @staticmethod
    def _reaches(graph, start, target):
        seen, todo = set(), [start]
        while todo:
            cur = todo.pop()
            if cur == target:
                return True
            if cur not in seen:
                seen.add(cur)
                todo.extend(graph.get(cur, ()))
        return False

    # Pull requests a runner is working on right now ("<n> <runner pid>" lines; a dead pid is a stale line) and cancel requests for them: `remove` of a pull request in the middle of its processing must not leave the worker running on silently.
    def _lines(self, suffix):
        try:
            return open(self.path + suffix, encoding="utf-8").read().split("\n")
        except FileNotFoundError:
            return []

    def _store(self, suffix, lines):
        path = os.path.abspath(self.path)
        directory = os.path.dirname(path)
        common = os.path.dirname(directory)
        # Limit this repair to the personal Lab canonical head sidefile. Other
        # queues/working/cancel state retain their existing behavior.
        lab_heads = (suffix == ".heads" and os.path.basename(path) == "merge.queue"
                     and os.path.basename(directory) == "merge-queue" and os.path.basename(common) == ".git"
                     and os.path.basename(os.path.dirname(common)) == "forcitis-one-psa-codex-lab")
        if not lab_heads:
            with open(self.path + suffix, "w", encoding="utf-8") as f:
                f.write("".join(l + "\n" for l in lines if l))
            return
        import stat
        parent = os.stat(directory, follow_symlinks=False)
        if (not stat.S_ISDIR(parent.st_mode) or parent.st_uid != os.geteuid() or parent.st_mode & 0o022
                or os.path.realpath(directory) != directory):
            raise ValueError("unsafe canonical heads parent")
        target = path + suffix
        try:
            before = os.lstat(target)
        except FileNotFoundError:
            before = None
        if before is not None and (not stat.S_ISREG(before.st_mode) or before.st_uid != os.geteuid()
                                   or before.st_nlink != 1 or before.st_mode & 0o022):
            raise ValueError("unsafe canonical heads file")
        fd, temporary = tempfile.mkstemp(dir=directory, prefix=".heads-")
        try:
            with os.fdopen(fd, "w", encoding="utf-8") as stream:
                stream.write("".join(line + "\n" for line in lines if line))
                stream.flush()
                os.fsync(stream.fileno())
            # Caller holds the original heads-lock; publish only complete bytes.
            current = os.lstat(target) if os.path.lexists(target) else None
            if (before is None) != (current is None) or (before is not None and
                    (before.st_dev, before.st_ino, before.st_mtime_ns, before.st_ctime_ns) !=
                    (current.st_dev, current.st_ino, current.st_mtime_ns, current.st_ctime_ns)):
                raise ValueError("canonical heads moved before atomic publication")
            current_parent = os.stat(directory, follow_symlinks=False)
            if (current_parent.st_dev, current_parent.st_ino) != (parent.st_dev, parent.st_ino):
                raise ValueError("canonical heads parent moved before atomic publication")
            os.replace(temporary, target)
            parent_fd = os.open(directory, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
            try:
                if (os.fstat(parent_fd).st_dev, os.fstat(parent_fd).st_ino) != (parent.st_dev, parent.st_ino):
                    raise ValueError("canonical heads parent moved")
                os.fsync(parent_fd)
            finally:
                os.close(parent_fd)
        finally:
            if os.path.exists(temporary):
                os.unlink(temporary)

    # The head each queued pull request had when it was queued (= approved: the coordinator approves a head and the pull request is queued on that head): "<n> <sha>" lines in <queue>.heads.
    # Without an entry (queued before this existed, gh could not say, `once`) nothing is compared.
    def set_head(self, n, head):
        with self.locked(".heads-lock"):
            self._store(".heads", [l for l in self._lines(".heads") if not l.startswith(f"{n} ")] + [f"{n} {head}"])

    def head_of(self, n):
        with self.locked(".heads-lock"):
            for l in self._lines(".heads"):
                if l.startswith(f"{n} "):
                    return l.split()[1]
        return None

    def drop_head(self, n):
        with self.locked(".heads-lock"):
            self._store(".heads", [l for l in self._lines(".heads") if not l.startswith(f"{n} ")])

    def _mark(self, n):
        self._store(".working", [l for l in self._lines(".working") if not l.startswith(f"{n} ")] + [f"{n} {os.getpid()}"])
        self._store(".cancel", [l for l in self._lines(".cancel") if l != str(n)])   # a request from an earlier round is not for this one

    def mark_working(self, n):
        with self.locked(".work-lock"):
            self._mark(n)

    def claim(self, n):
        """A worker takes pull request n: marked as being processed only if it is still queued, in one step with `remove_checked` (lock order: queue lock, then work lock)."""
        with self.locked(), self.locked(".work-lock"):
            if not any((m := LINE.match(l)) and int(m.group(1)) == n for l in self._read()):
                return False
            self._mark(n)
            return True

    def remove_checked(self, prs, cancel):
        """Take prs out of the queue. A pull request a worker is processing is refused (returned, and nothing at all is removed), or, with `cancel`, a cancel request is written for it; the check and the removal are one step."""
        with self.locked(), self.locked(".work-lock"):
            busy = {}
            for line in self._lines(".working"):
                number, _, pid = line.partition(" ")
                if number.isdigit() and int(number) in prs and pid.isdigit() and self._alive(int(pid)):
                    busy[int(number)] = int(pid)
            if busy and not cancel:
                return busy
            for n in busy:
                self._store(".cancel", [l for l in self._lines(".cancel") if l != str(n)] + [str(n)])
            self._write([l for l in self._read() if not ((m := LINE.match(l)) and int(m.group(1)) in prs)])
            return busy

    def reset_working(self):
        """At the start of the runner (there is only one): whatever was marked belongs to a runner that is gone."""
        with self.locked(".work-lock"):
            self._store(".working", [])
            self._store(".cancel", [])

    @staticmethod
    def _alive(pid):
        try:
            os.kill(pid, 0)
        except ProcessLookupError:
            return False
        except PermissionError:
            return True
        return True

    def unmark_working(self, n):
        with self.locked(".work-lock"):
            self._store(".working", [l for l in self._lines(".working") if not l.startswith(f"{n} ")])
            self._store(".cancel", [l for l in self._lines(".cancel") if l != str(n)])

    def working(self):
        """{pull request: pid of the runner working on it}; a line whose process is gone (the runner was killed) does not count."""
        out = {}
        with self.locked(".work-lock"):
            for line in self._lines(".working"):
                number, _, pid = line.partition(" ")
                if number.isdigit() and pid.isdigit() and self._alive(int(pid)):
                    out[int(number)] = int(pid)
        return out

    def request_cancel(self, n):
        """Only for a pull request that is marked: a request for one nobody is processing would cancel its next round."""
        with self.locked(".work-lock"):
            if any(l.startswith(f"{n} ") for l in self._lines(".working")):
                self._store(".cancel", [l for l in self._lines(".cancel") if l != str(n)] + [str(n)])

    def cancel_requested(self, n):
        with self.locked(".work-lock"):
            return str(n) in self._lines(".cancel")

    def remove(self, *prs):
        with self.locked():
            self._write([l for l in self._read() if not ((m := LINE.match(l)) and int(m.group(1)) in prs)])

    def before(self, pr, other):
        """Move a queued pull request in front of another; False (nothing written) when pr is not queued: only enqueue admits, and it runs the checks."""
        with self.locked():
            lines = self._read()
            own = next((l.strip() for l in lines if (m := LINE.match(l)) and int(m.group(1)) == pr), None)
            if own is None:
                return False
            lines = [l for l in lines if not ((m := LINE.match(l)) and int(m.group(1)) == pr)]
            lines.insert(next((i for i, l in enumerate(lines) if (m := LINE.match(l)) and int(m.group(1)) == other), len(lines)), own)
            self._write(lines)
            return True
