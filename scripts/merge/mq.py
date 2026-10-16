#!/usr/bin/env python3
"""Serial merge queue (step 1 of the self-built queue): one pull request at a time is brought up to date, tested by the full CI and merged.

  mq.py run                       persistent runner: takes the head of the queue, processes it, removes it, waits when the queue is empty (start it inside the repository)
  mq.py once <pr>                 process one pull request (no queue)
  mq.py enqueue <pr>...           append (a number already queued is not added twice)
  mq.py remove <pr>...            take out
  mq.py before <pr> <other>       put <pr> in front of <other> (appends it when it is not queued)
  mq.py list                      the queue, one number per line
  mq.py train <merge_train.py args>   scripts/queue/merge_train.py: `train docs --branch B <session branch>@<sha>...` collects documentation commits (one by one, docs/ only, never an octopus merge)
  mq.py --self-test

Per pull request (the former merge_pr.sh): cancel the CI runs of the other queued pull requests (they are re-run after their own update, so until then they only take runners from the head), bring the
branch up to date (gh update-branch; a pull request that touches .github/workflows is updated by a local merge pushed over ssh, because the gh token lacks the workflow scope), label it `queued`
(heavy CI steps run only with the label, scripts/ci/heavy_gate.py), wait for the checks while cancelling the others' runs, rerun failed checks once, merge with a merge commit. Outcomes: MERGED, CONFLICT
(text conflict with main, found before or while updating: back to the author), HOLD (checks still red after the rerun, CI timeout, an update that could not be pushed, or the attempts ran out: reported to the coordinator). A pull request whose only green checks are the "(light)" ones
is never merged: the label is removed and added again. Light CI is not proof.

Queue file: one pull request number per line, anything else is kept as it is. Every change goes through flock on <queue>.lock and an atomic replace (temporary file, then rename), so an enqueue can no longer be
lost to the runner removing a finished entry (a raw `echo n >> merge.queue` or `sed -i` outside this script is not safe). Log: one line per event,
"<ISO time> <EVENT> pr=#<n> run=<ci run id or -> elapsed=<seconds since the pull request was taken>s <detail>", appended to <queue dir>/merge.log and echoed.
"""
import argparse
import contextlib
import datetime
import fcntl
import importlib.util
import json
import os
import re
import subprocess
import sys
import tempfile
import time

HERE = os.path.dirname(os.path.abspath(__file__))
HEAVY = re.compile(r"^(backend|frontend) verify")
OK = ("pass", "skipping")
LINE = re.compile(r"^\s*(\d+)\s*$")


def sh(args, cwd=None, check=False):
    r = subprocess.run(args, cwd=cwd, capture_output=True, text=True)
    if check and r.returncode:
        raise SystemExit(f"{' '.join(args[:3])} failed ({r.returncode}): {(r.stderr or r.stdout).strip()[:300]}")
    return r


class Log:
    def __init__(self, path=None, echo=True, now=None):
        self.path, self.echo = path, echo
        self.now = now or (lambda: datetime.datetime.now().astimezone())
        self.lines = []

    def event(self, ev, pr=None, run=None, since=None, detail=""):
        started = since if since is not None else time.monotonic()
        line = f"{self.now().isoformat(timespec='seconds')} {ev} pr=#{pr if pr is not None else '-'} run={run if run else '-'} elapsed={max(0, int(time.monotonic() - started))}s"
        line += f" {detail}" if detail else ""
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
    def locked(self):
        with open(self.path + ".lock", "a") as lock:
            fcntl.flock(lock, fcntl.LOCK_EX)
            try:
                yield
            finally:
                fcntl.flock(lock, fcntl.LOCK_UN)

    def _read(self):
        try:
            return open(self.path, encoding="utf-8").read().splitlines()
        except FileNotFoundError:
            return []

    def _write(self, lines):
        fd, tmp = tempfile.mkstemp(dir=os.path.dirname(os.path.abspath(self.path)), prefix=".queue-")
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            f.write("".join(l + "\n" for l in lines))
            f.flush()
            os.fsync(f.fileno())
        os.replace(tmp, self.path)

    def numbers(self):
        with self.locked():
            return [int(m.group(1)) for l in self._read() if (m := LINE.match(l))]

    def head(self):
        nums = self.numbers()
        return nums[0] if nums else None

    def enqueue(self, *prs):
        with self.locked():
            lines = self._read()
            for n in prs:
                if not any((m := LINE.match(l)) and int(m.group(1)) == n for l in lines):
                    lines.append(str(n))
            self._write(lines)

    def remove(self, *prs):
        with self.locked():
            self._write([l for l in self._read() if not ((m := LINE.match(l)) and int(m.group(1)) in prs)])

    def before(self, pr, other):
        with self.locked():
            lines = [l for l in self._read() if not ((m := LINE.match(l)) and int(m.group(1)) == pr)]
            at = next((i for i, l in enumerate(lines) if (m := LINE.match(l)) and int(m.group(1)) == other), len(lines))
            lines.insert(at, str(pr))
            self._write(lines)


class Gh:
    """The GitHub side; the self-test replaces it."""

    def view(self, n):
        r = sh(["gh", "pr", "view", str(n), "--json", "state,mergeStateStatus,labels,headRefName"], check=True)
        d = json.loads(r.stdout)
        return {"state": d["state"], "merge": d["mergeStateStatus"], "labels": [l["name"] for l in d["labels"]], "branch": d["headRefName"]}

    def checks(self, n):
        r = sh(["gh", "pr", "checks", str(n), "--json", "name,bucket,link"])
        try:
            return json.loads(r.stdout)
        except ValueError:
            return []

    def update_branch(self, n):
        return sh(["gh", "pr", "update-branch", str(n)]).returncode == 0

    def label(self, n, name, add):
        return sh(["gh", "pr", "edit", str(n), "--add-label" if add else "--remove-label", name]).returncode == 0

    def active_runs(self, branch):
        r = sh(["gh", "run", "list", "--branch", branch, "--workflow", "ci", "--json", "databaseId,status"])
        try:
            return [x["databaseId"] for x in json.loads(r.stdout) if x["status"] != "completed"]
        except ValueError:
            return []

    def cancel_run(self, run):
        return sh(["gh", "run", "cancel", str(run)]).returncode == 0

    def rerun(self, run):
        return sh(["gh", "run", "rerun", str(run), "--failed"]).returncode == 0 or sh(["gh", "run", "rerun", str(run)]).returncode == 0

    def merge(self, n):
        return sh(["gh", "pr", "merge", str(n), "--merge"]).returncode == 0


def update_over_ssh(repo, branch, wt):
    """Local merge of origin/main into the pull request branch, pushed over ssh (git, not the gh token): 'ok' | 'conflict' | 'error'."""
    sh(["git", "-C", repo, "fetch", "-q", "origin"])
    sh(["git", "-C", repo, "worktree", "remove", "-f", wt])
    if sh(["git", "-C", repo, "worktree", "add", "-q", "--detach", wt, f"origin/{branch}"]).returncode:
        return "error"
    try:
        if sh(["git", "-C", wt, "merge", "-q", "--no-edit", "origin/main"]).returncode:
            sh(["git", "-C", wt, "merge", "--abort"])
            return "conflict"
        return "ok" if sh(["git", "-C", wt, "push", "-q", "origin", f"HEAD:{branch}"]).returncode == 0 else "error"
    finally:
        sh(["git", "-C", repo, "worktree", "remove", "-f", wt])


def run_id(link):
    m = re.search(r"runs/(\d+)", link or "")
    return m.group(1) if m else None


class Processor:
    def __init__(self, gh, queue, log, repo, sleep=time.sleep, updater=update_over_ssh, ci_timeout=90 * 60, wt=None):
        self.gh, self.queue, self.log, self.repo, self.sleep = gh, queue, log, repo, sleep
        self.updater, self.ci_timeout = updater, ci_timeout
        self.wt = wt or os.path.join(os.path.dirname(os.path.abspath(queue.path)), "mq-wt")

    def cancel_others(self, head, since):
        for other in self.queue.numbers():
            if other == head:
                continue
            try:
                branch = self.gh.view(other)["branch"]
            except (SystemExit, KeyError, ValueError):
                continue
            for run in self.gh.active_runs(branch):
                if self.gh.cancel_run(run):
                    self.log.event("CANCEL-RUN", other, run, since, f"waiting behind #{head}")

    def update(self, n, branch, since):
        if self.gh.update_branch(n):
            self.log.event("UPDATE", n, None, since, "gh update-branch")
            return "ok"
        res = self.updater(self.repo, branch, self.wt)
        self.log.event("SSH-UPDATE" if res == "ok" else f"SSH-UPDATE-{res.upper()}", n, None, since, "local merge pushed over ssh")
        return res

    def process(self, n):
        since = time.monotonic()
        self.log.event("START", n, None, since)
        self.cancel_others(n, since)
        for attempt in range(1, 5):
            v = self.gh.view(n)
            if v["state"] == "MERGED":
                self.log.event("MERGED", n, None, since, "already merged")
                return "MERGED"
            if v["merge"] == "DIRTY":
                self.log.event("CONFLICT", n, None, since, "back to the author (text conflict)")
                return "CONFLICT"
            if v["merge"] == "BEHIND":
                res = self.update(n, v["branch"], since)
                if res != "ok":
                    self.log.event("CONFLICT" if res == "conflict" else "HOLD", n, None, since, "back to the author (text conflict with main)" if res == "conflict" else "the update could not be pushed")
                    return "CONFLICT" if res == "conflict" else "HOLD"
                self.sleep(30)
            if "queued" not in self.gh.view(n)["labels"]:
                if self.gh.label(n, "queued", True):
                    self.log.event("LABEL-QUEUED", n, None, since)
                self.sleep(20)
            for _ in range(20):
                if self.gh.checks(n):
                    break
                self.sleep(15)
            waited = 0
            while any(c["bucket"] == "pending" for c in self.gh.checks(n)):
                if waited >= self.ci_timeout:
                    self.log.event("HOLD", n, None, since, f"CI still pending after {waited // 60} minutes")
                    return "HOLD"
                self.cancel_others(n, since)
                self.sleep(30)
                waited += 30
            checks = self.gh.checks(n)
            red = [c for c in checks if c["bucket"] not in OK]
            if red:
                runs = sorted({r for c in red if (r := run_id(c.get("link")))})
                if self.gh.view(n)["merge"] == "BEHIND" and attempt < 4:
                    self.log.event("BEHIND-AFTER-FAIL", n, runs[0] if runs else None, since, "update and run again")
                    continue
                if attempt == 1:
                    for r in runs:
                        self.gh.rerun(r)
                    self.log.event("RERUN-FAILED", n, runs[0] if runs else None, since, ", ".join(f"{c['name']}={c['bucket']}" for c in red))
                    self.sleep(30)
                    continue
                self.log.event("HOLD", n, runs[0] if runs else None, since, "; ".join(f"{c['name']}={c['bucket']}" for c in red))
                return "HOLD"
            full = [c for c in checks if HEAVY.match(c["name"]) and not c["name"].endswith("(light)") and c["bucket"] in OK]
            if len(full) < 2:
                self.log.event("LIGHT-ONLY", n, None, since, "relabel")
                self.gh.label(n, "queued", False)
                self.sleep(10)
                continue
            if self.gh.merge(n):
                self.log.event("MERGED", n, run_id(full[0].get("link")), since)
                return "MERGED"
            self.log.event("RETRY-MERGE", n, None, since, f"attempt {attempt}")
            self.sleep(20)
        self.log.event("HOLD", n, None, since, "attempts exhausted")
        return "HOLD"


def run_loop(proc, idle=60, stop=lambda: False):
    while not stop():
        n = proc.queue.head()
        if n is None:
            proc.sleep(idle)
            continue
        proc.process(n)
        proc.queue.remove(n)
        proc.log.event("DEQUEUE", n)


def train_module():
    spec = importlib.util.spec_from_file_location("merge_train", os.path.join(HERE, "..", "queue", "merge_train.py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def repo_root():
    r = sh(["git", "rev-parse", "--show-toplevel"])
    if r.returncode:
        raise SystemExit("start mq.py inside the repository (gh needs the repository context)")
    return r.stdout.strip()


def default_queue():
    common = sh(["git", "rev-parse", "--path-format=absolute", "--git-common-dir"])
    if common.returncode:
        raise SystemExit("start mq.py inside the repository, or give --queue")
    return os.path.join(common.stdout.strip(), "merge-queue", "merge.queue")


def main(argv=None):
    argv = sys.argv[1:] if argv is None else argv
    if argv == ["--self-test"]:
        sys.path.insert(0, HERE)
        import mq_selftest
        with contextlib.redirect_stdout(open(os.devnull, "w")):
            mq_selftest.run(sys.modules[__name__])
        print("mq self-test: ok")
        return
    if argv[:1] == ["train"]:
        sys.argv = ["merge_train.py"] + argv[1:]
        return train_module().main()
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--queue")
    p.add_argument("--log")
    p.add_argument("--ci-timeout", type=int, default=90, help="minutes a pull request's CI may stay pending before HOLD")
    sub = p.add_subparsers(dest="cmd", required=True)
    sub.add_parser("run"); sub.add_parser("list")
    sub.add_parser("once").add_argument("pr", type=int)
    for name in ("enqueue", "remove"):
        sub.add_parser(name).add_argument("prs", type=int, nargs="+")
    b = sub.add_parser("before"); b.add_argument("pr", type=int); b.add_argument("other", type=int)
    a = p.parse_args(argv)
    q = Queue(a.queue or default_queue())
    if a.cmd == "list":
        print("\n".join(map(str, q.numbers())))
    elif a.cmd == "enqueue":
        q.enqueue(*a.prs)
    elif a.cmd == "remove":
        q.remove(*a.prs)
    elif a.cmd == "before":
        q.before(a.pr, a.other)
    else:
        log = Log(a.log or os.path.join(os.path.dirname(os.path.abspath(q.path)), "merge.log"))
        proc = Processor(Gh(), q, log, repo_root(), ci_timeout=a.ci_timeout * 60)
        if a.cmd == "once":
            proc.process(a.pr)
        else:
            run_loop(proc)


if __name__ == "__main__":
    main()
